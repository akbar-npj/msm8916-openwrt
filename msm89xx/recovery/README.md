# msm89xx recovery images

Rescue boot images for the msm8916 boards: a minimal kernel + initramfs that
gives a root shell over USB (ACM console + NCM network with SSH) and a one-line
escape into Qualcomm EDL (`reboot-edl`). Built with `./build-recovery.sh <board>`
from the repo root; output lands here as `<board>.img`.

## What the image is

```
kernel   Linux from the patched OpenWrt source, config msm89xx/config-6.12-recovery
         (devtmpfs, configfs, libcomposite, usb_f_ncm, usb_f_acm built in —
         the initramfs carries no modules), with the initramfs below embedded
         via CONFIG_INITRAMFS_SOURCE
initramfs irfs/ — busybox (static) + dropbear + reboot-mode-raw + /init
pack     gzip -9 Image | append board DTB | mkbootimg --ramdisk /dev/null
cmdline  empty — /init does everything
```

## What /init provides

* USB ConfigFS gadget `1d6b:0104` with `ncm.usb0` + `acm.GS0`
* Network: `br0` bridges the USB NCM link and the WiFi AP, carrying
  `192.168.10.1/24`; one `dnsmasq` serves `192.168.10.2`-`.20` on the bridge
  (DHCP + DNS) so USB *and* WiFi clients get an address automatically — the
  same shape as OpenWrt's `br-lan` + dnsmasq
* WiFi AP `OpenWrt-Recovery`, WPA2-PSK **and** WPA3-SAE, passphrase
  `recovery`, bridged into `br0` (best-effort: a missing driver/firmware
  never blocks the USB/SSH path)
* WiFi **station** on a second vif (`wlan1`), opt-in: join an existing
  network from the web UI and the box also answers on *your* LAN, without a
  USB cable and without giving up the AP.  Not bridged into `br0` — it takes
  its own lease (see *AP and station at the same time*)
* Root shell on `/dev/ttyGS0` (ACM console, no auth) and
  `ssh root@192.168.10.1` with **no password** (OpenWrt
  `600-allow-blank-root-password.patch`; `/etc/passwd` field 2 is empty)
* Web UI on <http://192.168.10.1/> (busybox `httpd` + CGI in `/www`):
  upload a `*-sysupgrade.bin` for a fresh install (`sysupgrade -n` semantics),
  write a raw image to a single partition, reboot, or reboot into EDL
* Status banner re-emitted every 5 s (boot-time output is lost when the host
  is not reading the ACM port yet)
* Green LED blink = alive; `/sbin/reboot-edl` → EDL `05c6:9008`

Nothing secret is baked in: the dropbear host key is generated at first boot.

## Flashing

* Boards with `GPT_RECOVERY=1` in `msm89xx/image/msm8916.mk` (`mf800b`,
  `mifib-v12`) have a dedicated 16 MiB `recovery` GPT partition (plus 1 MiB
  `misc`, matching the stock Android layout) — flash `<board>.img` there.
* Other boards: one-shot boot over fastboot (`fastboot boot <board>.img`).

## Enabling recovery on an install that already runs OpenWrt

`flash.sh --mode repartition` rewrites the GPT, adds `misc` + `recovery`,
installs the rescue image and wipes the overlay. It deliberately leaves
`boot`, `rootfs`, the bootloader and the radio partitions untouched.

That is enough for `reboot recovery` (aboot's stock BCB handling), but **not
enough for the 3-strike failover**, which also needs:

1. **`aboot.mbn` built with the failover code** (`BOOT_FAILOVER_COUNT`, see
   `msm89xx/image/lk2nd-patches/001-…`). A board flashed before that patch has
   an aboot that never touches the counter, so the failover silently does
   nothing. Symptom: the last 512-byte block of `misc` stays all-zero across
   reboots.
2. **`S99recovery-bootcount` from `reboot-edl` r4 in the *running* rootfs.**
   aboot increments the counter on every boot; only userspace clears it. With
   the hook missing the counter never resets and a perfectly healthy board
   drops into recovery every 3rd boot.

So after repartitioning, run `flash.sh --mode update` once (boot + rootfs from
the same build), and flash `aboot.mbn` if the board predates the failover
patch. Verify from the running system:

```sh
ls -l /etc/rc.d/S99recovery-bootcount           # must exist
dd if=/dev/mmcblk0p16 bs=512 skip=2047 count=1 2>/dev/null | od -An -tx1 | head -1
#  43 46 4b 4c NN  -> counter NN, cleared to 00 by the hook after a healthy boot
```

`misc` is p16 and `recovery` p17 on every gated board, so these offsets are
the same on `mf800b`, `mifib-v12` and `hmuf02v5`.

## Frozen binaries and how to rebuild them

| File | Provenance |
|---|---|
| `irfs/bin/busybox` | busybox built with `busybox.config` (`CONFIG_STATIC=y`) |
| `irfs/usr/sbin/dropbear` | bit-identical to the OpenWrt dropbear package build (`build_dir/.../root-msm89xx/usr/sbin/dropbear`) |
| `irfs/lib/ld-musl-aarch64.so.1`, `libc.so`, `libgcc_s.so.1` | bit-identical to the toolchain rootfs copies (`build_dir/.../root-msm89xx/lib/`) |
| `irfs/sbin/reboot-mode-raw` | built from `msm89xx/package/reboot-edl` sources |
| `irfs/usr/sbin/dnsmasq` | bit-identical to the OpenWrt dnsmasq package build (`build_dir/.../root-msm89xx/usr/sbin/dnsmasq`); needs `libubox`/`libubus` from `irfs/lib` |
| `irfs/usr/sbin/hostapd`, `irfs/usr/sbin/iw`, `irfs/usr/sbin/wpa_supplicant` + their `.so` libs (`irfs/lib/libnl*`, `libwolfssl*`/`libmbed*`) | **NOT frozen / not committed** - pulled per board from a live device by `fetch-device-blobs.sh` (a SAE-capable build; the board already serves WPA3 with it). `wpa_supplicant` is a **symlink to `hostapd`**: OpenWrt ships one multicall `wpad` behind both names, and the fetch script checks that on the device before falling back to a second binary. See the note below. |
| `irfs/etc/hostapd.conf` | WPA2 + WPA3 (SAE) AP `OpenWrt-Recovery`, passphrase `recovery` |

### WiFi firmware and hostapd are fetched per board, not committed

The recovery image brings up a **WiFi AP** (`wcn36xx`) so the box is reachable
without a USB cable. Because the WCNSS processor firmware (`wcnss.mdt` +
segments), the board-specific WiFi NV (`wlan/prima/WCNSS_qcom_wlan_nv.bin`),
and the `hostapd` binary + its shared libs are **device-specific and must not
be committed**, they are copied from a live device before building:

```sh
./msm89xx/recovery/fetch-device-blobs.sh <board> <user@device>
./build-recovery.sh <board>
```

The blobs are stored per board under `blobs/<board>/` (git-ignored) and
`build-recovery.sh` copies **only that board's** directory into `irfs/`
before the build, deleting the previous board's firmware and libs first.
`wcnss.mdt` and the calibrated wlan NV are not interchangeable
(hmuf02v5 `62bb56b2…` vs mifib-v12 `41d715d8…`), so a mixed tree would ship
the wrong firmware silently.

The AP is **best-effort**: `init` runs it in a detached subshell, so a missing
driver/firmware never blocks the USB/SSH rescue path. Clients join
`OpenWrt-Recovery` with passphrase `recovery` over WPA2 or WPA3 and get a
DHCP lease on `192.168.10.x`.

After an upstream version bump, rebuild the binary from the matching source,
copy it into `irfs/`, and re-run `./build-recovery.sh <board>`.

## AP and station at the same time

The image can run the AP **and** join an upstream network. Both live on the
same radio: `wlan0` is the AP (hostapd → `br0`), `wlan1` is a managed vif that
`wpa_supplicant` drives. That needs wcn36xx interface-combination support, i.e.
patch **452** — so the recovery kernel carries the whole six-patch series
(450/451/452/453/458/459) and runs the *same driver* as the system it is
rescuing. `build-recovery.sh` applies them to the in-tree 6.12 source and
aborts the build if one does not apply.

Why the station is worth having: a stick in recovery that only runs an AP is
reachable from whatever can see `OpenWrt-Recovery`; with the station joined it
is also on the network you already use, so you can rescue it from your desk
without reconfiguring your laptop.

### How it is wired

* The station is **not** bridged into `br0`. `br0` is `192.168.10.1/24` with its
  own DHCP server; bridging an upstream LAN into it would answer DHCP on
  somebody else's network. `wlan1` takes its own lease instead, so the box ends
  up with a second address on the upstream link.
* The profile is `/etc/wifi-sta.conf` (wpa_supplicant syntax). **It does not
  survive a reboot**: `/etc` is the initramfs, rebuilt from scratch every boot.
  Joining is a per-session action.
* With no profile the station costs nothing — `/init` polls for the file and
  never creates `wlan1`.

### What /init does

1. Wait for `wlan0`, then 3 s for the wcn36xx firmware to settle. Starting a
   scan inside the bring-up window is what loses the driver's scan state, after
   which every scan answers `-EBUSY` until the next boot — the same failure the
   production `wifi-sta-up` guards against.
2. `iw phy phy0 interface add wlan1 type managed`, `ip link set wlan1 up`.
3. `wpa_supplicant -D nl80211 -i wlan1 -c /etc/wifi-sta.conf` in the
   foreground under a restart loop. The loop re-arms when the profile is
   rewritten (join again) or deleted (leave).
4. Once `iw dev wlan1 link` reports `Connected to`, run
   `udhcpc -i wlan1`. The hook is `irfs/usr/share/udhcpc/default.script` —
   busybox ships none, and without one udhcpc gets a lease and installs nothing.
5. Leaving flushes the address and deletes `wlan1`.

### Configuring it

Web UI → **WiFi station**: *Scan*, pick a network (or type the SSID of a hidden
one), enter the passphrase, *Join*. *Leave* removes the profile.

The browser builds the `wpa_supplicant` config, not the CGI: the initramfs has
no URL decoder and no `wpa_passphrase`, and building it client-side lets the
SSID go over the wire as a **hex string** (`ssid=4d79204e6574…`, a native
wpa_supplicant form) so no quoting is involved. Only the passphrase needs
escaping (`"` and `\`). The CGI writes the file and `killall wpa_supplicant`
makes the supervisor re-read it.

From a shell the same thing by hand — `wpa_passphrase` is not in the initramfs
either, so it is a heredoc:

```sh
cat > /etc/wifi-sta.conf <<'EOF'
network={
	ssid=4d79204e6574776f726b
	psk="secret"
}
EOF
killall wpa_supplicant        # /init restarts it with the new profile
```

`ssid=` also accepts a quoted plain string; the hex form is what the UI uses
because it cannot be broken by a quote in the network name.

### Traps

* **A UI scan needs `wlan1` too.** The CGI creates the vif if no profile
  exists yet and leaves it behind. Harmless — the supervisor deletes and re-adds
  it when it starts.
* **A scan can still wedge the driver** (`-EBUSY` forever) if its completion
  event is lost. The AP keeps working — it is on a fixed channel and never runs
  ACS — but no further scan succeeds until a reboot. The CGI caps the scan at
  20 s so the page cannot hang on it.
* **`-EBUSY` is also what breaks a `channel=auto` AP** on this driver: hostapd
  runs an ACS scan at every start, so a lost scan leaves the AP with no channel
  at all. `hostapd.conf` therefore pins `channel=6`; do not switch it to `auto`.
* The station shares the radio with the AP, so throughput on both is halved.
  Fine for SSH and the web UI, which is all this link is for.

## Web UI (flash from a browser)

`busybox httpd` serves `/www` on port 80 and runs the CGIs in
`/www/cgi-bin`:

| Endpoint | Purpose |
|---|---|
| `GET /` | page: status, sysupgrade, single-partition flash, power buttons |
| `GET /cgi-bin/info` | JSON: kernel, uptime, free RAM, partition list |
| `POST /cgi-bin/flash?mode=sysupgrade` | body = `*-sysupgrade.bin` (tar) |
| `POST /cgi-bin/flash?mode=partition&target=<name>` | body = raw image |
| `POST /cgi-bin/power?action=reboot\|edl` | reset, or reset into EDL 9008 |
| `GET /cgi-bin/wifi?action=status` | station state: `state \t ssid \t signal \t ip` |
| `GET /cgi-bin/wifi?action=scan` | one BSS per line: `ssid \t bssid \t signal \t freq \t security` |
| `POST /cgi-bin/wifi?action=join` | body = wpa_supplicant config → `/etc/wifi-sta.conf` |
| `POST /cgi-bin/wifi?action=leave` | delete the profile, drop the link |

The upload body is the **raw request body** (`fetch`/`XHR` send the File
directly), not multipart, so the CGI is a plain `cat > /tmp/upload.img` -
busybox httpd pipes the POST body to the CGI's stdin and sets
`CONTENT_LENGTH`.

Safety rules in `flash`:

* the body is buffered in `/tmp` (tmpfs) and the size is checked against
  `Content-Length` before anything is written - a short upload never touches
  the flash;
* the image must fit the target partition (size from
  `/sys/class/block/*/size`);
* `mode=sysupgrade` requires `sysupgrade-<board>/{kernel,root}` in the tar and
  writes them to the `boot`/`rootfs` partitions, then zeroes the head of
  `rootfs_data` so fstools formats a fresh overlay (that is what `-n` means);
* partitions are resolved by `PARTNAME=` in `/sys/class/block/*/uevent`, so
  the code needs no udev and no hard-coded partition numbers.

There is **no authentication** - same trust model as the passwordless SSH
root shell. Keep the recovery box on a network you control.

## Traps learned on hardware

* `/init` must never use `exec` redirection — a failed redirection kills PID 1.
* The console shell needs `setsid -c` to get a controlling tty.
* `/etc/shells` must exist and contain `/bin/ash`, or dropbear denies **every**
  auth method pre-authentication (`checkusername()` validates `pw_shell`).
* dropbear reads the password hash from `/etc/passwd` field 2 (musl has no
  `getspnam()`); `/etc/shadow` is dead weight but kept for the uid/gid lookup.
* LED class names come from the `function`/`color` bindings, not the DT node
  names — the init matches `/sys/class/leds/*` by substring (`red`, `green`).
* **wcn36xx AP mode drops group-addressed (broadcast/multicast) frames when
  the BSS is encrypted.** Measured on hmuf02v5 with WPA2-PSK: 20 unicast
  frames sent to the station arrived 20/20, while ~10 broadcast DHCP OFFERs
  arrived 0 times. Consequence: busybox `udhcpd` (which always *broadcasts*
  its reply) can never hand a lease to a WiFi client, and a client that sets
  the DHCP broadcast flag still cannot get one. That is why the image runs
  `dnsmasq`, which unicasts the reply to the client's MAC — DHCP then works
  over WPA2/WPA3. Anything else that needs broadcast over WiFi (IPv6 RA,
  mDNS) will not work; SSH/TCP/ARP-replies are unicast and do.
* `hostapd` must be started **in the foreground under a restart loop**, not
  with `-B`: `-B` calls `daemon()`, which closes stderr, so a startup failure
  leaves a zero-byte log. Also wait ~3 s after `wlan0` appears — starting
  hostapd in wcn36xx's firmware-bringup window enables the AP and tears it
  down again ~200 ms later.

## Entering recovery

### mifib-v12: the power button is enough (verified on hardware)

Holding the **power button** (the PMIC `pm8941_pwrkey`) for ~20 s drives the
PMIC into a KPDPWR hard reset. The board then comes up in the recovery image -
verified on mifib-v12: 20 s hold from the running system, then recovery at
`192.168.10.1` with USB **and** WiFi working and `reboot-edl` available.

The entry therefore comes from the bootloader's hard-reboot-reason handling
(the PON reason of a power-key reset), not from the recovery-key patch: the
shipped aboot contains the failover code but **no** `recovery key pressed`
string, i.e. `RECOVERY_KEY_GPIO` was never compiled into it. That is also why
the GPIO102 key is **not needed** on mifib-v12 - it stays in the tree for
mf800b and for anyone who wants a shorter hold than 20 s (enabling it means
reflashing `aboot`, the one write that can brick the boot chain).

Note the side effect on a tethered board: the same hard reset fires on a long
hold while USB is plugged in, and VBUS re-powers the SoC, so a "power off"
hold reads as a reboot into recovery rather than an off state.

### The three software triggers

All three are handled by lk2nd (the aboot replacement built by
`generate_firmware.sh`):

1. **Hold the reset button at power-on** (only if aboot was built with the
   key - check with `strings /dev/disk/by-partlabel/aboot | grep 'recovery key'`).
   Both gated boards wire the reset button to TLMM GPIO 102 (active low,
   unused on every other board in this project). lk2nd reports it as
   VOLUMEUP, which its stock key handling maps to "boot the recovery
   partition". Enabled by `RECOVERY_KEY_GPIO=102` /
   `RECOVERY_KEY_ACTIVE_LOW=1`. Not present in the aboot shipped on the
   mifib-v12 test unit.
2. **`reboot-recovery` from a running system.** Same infrastructure as
   `reboot-edl`: it sets the PON reboot reason to recovery, which lk2nd's
   `check_reboot_mode()` honours on the next boot. Works whenever OpenWrt is
   alive enough to run a command (SSH, serial, failsafe shell).
3. **Automatic boot failover.** lk2nd counts consecutive boots that never
   reach userspace in the last 512-byte block of the `misc` partition and
   boots the recovery partition once the count reaches
   `BOOT_FAILOVER_COUNT` (3). OpenWrt clears the cookie in
   `S99recovery-bootcount` (reboot-edl package) as soon as userspace is up,
   so routine reboots of a healthy system never trip it. This is the only
   trigger that rescues a board whose boot partition is broken.

Bootloader patches live in `msm89xx/image/lk2nd-patches/` and are applied by
`generate_firmware.sh`, which also prepends the `DEFINES` to
`project/lk1st-msm8916.mk` - they must precede the project include or they are
silently ignored.

### Boot-failover cookie layout

Last 512-byte block of `misc` (1 MiB): `magic` (0x4C4B4643) + `count`.
Page 0 of `misc` holds the FFBM cookie and page 1 the recovery BCB, so the
tail is free. Writing from Linux is a single 512-byte `dd` at `seek=2047` of
`/dev/mmcblk0p16`.
