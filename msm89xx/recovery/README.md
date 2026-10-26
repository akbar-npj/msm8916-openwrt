# msm89xx recovery images

Rescue boot images for the msm8916 boards: a minimal kernel + initramfs that
gives a root shell over USB (ACM console + NCM network with SSH) and a one-line
escape into Qualcomm EDL (`reboot-edl`). Built with `./build-recovery.sh <board>`
from the repo root; output lands here as `<board>.img`.

## What the image is

```
kernel   the board's PRODUCTION kernel, lifted verbatim out of
         openwrt/bin/targets/msm89xx/msm8916/
           msm8916-openwrt-msm89xx-msm8916-generic-<board>-squashfs-boot.img
         (gzipped Image with the board DTB already appended)
ramdisk  gzip -9 of a newc cpio of irfs/ — busybox (static) + dropbear +
         reboot-mode-raw + /init + the kernel modules /init insmods
cmdline  earlycon console=ttyMSM0,115200 panic=5
         (the production "root=/dev/mmcblk0p14 rootfstype=squashfs" is dropped:
          /init runs from the initramfs and never switch_roots)
```

There is **no second kernel**. The rescue image runs the exact kernel the
device boots normally, so it can never drift from the thing it is rescuing.
The earlier recipe built its own kernel from `msm89xx/config-6.12-recovery`
(devtmpfs on, wcn36xx built in) and that drift is precisely what left the first
AP+STA image with a dead USB port: the production kernel has
**`CONFIG_DEVTMPFS` off** (OpenWrt mounts a tmpfs and runs `mdev`) and builds the
wireless stack as **modules**.

Both consequences are handled, and both are easy to reintroduce by accident:

* **No devtmpfs.** `/init` mounts a tmpfs on `/dev` and runs `mdev -s` instead. Skip
  that and `/dev` stays empty: dnsmasq dies with *"failed to seed the random
  number generator"* (no `/dev/urandom`) so there is no DHCP on USB or WiFi,
  `>/dev/ttyGS0` silently creates a **regular file** so the console never
  appears, and every `2>/dev/null` creates a file called `null`.
* **Wireless is modular.** `wcn36xx`, `mac80211`, `cfg80211`, `compat` and the
  WCNSS remoteproc set are `.ko` files. `build-recovery.sh` copies them into
  the initramfs and `/init` insmods them in dependency order. Without them the
  image boots perfectly, has no `wlan0`, and no AP.

## Step by step: build, flash and run it

The whole procedure, in the order it has to happen. `<board>` is `hmuf02v5`,
`mf800b`, `mifib-v12`, `ufi003_mb_v02` or `ufi001b`.

### 0. Prerequisites

* **The board's production image must already be built.** The recovery image
  steals its kernel and its modules from there, so there is nothing to compile
  but there is something to find:

  ```sh
  ./build.sh <board>          # produces openwrt/bin/targets/msm89xx/msm8916/
                              #   ...-generic-<board>-squashfs-boot.img
                              # and build_dir/.../root-msm89xx/lib/modules/<ver>/
  ./build-recovery.sh <board> # fails loudly if either is missing
  ```

* Host tools: `python3`, `cpio` (GNU, for `--reproducible`), `gzip`. No docker
  and no kernel build are involved any more.

### 1. Fetch the board's WiFi blobs (once per board)

The WCNSS firmware, the calibrated wlan NV and `hostapd` are device-specific
and are **not** committed. Pull them from a board that already runs OpenWrt
with a working AP:

```sh
./msm89xx/recovery/fetch-device-blobs.sh <board> root@<running-box>
```

They land in `msm89xx/recovery/blobs/<board>/` (git-ignored). Skip this and
step 2 stops with *"No WiFi blobs for ..."*.

### 2. Build

```sh
./build-recovery.sh <board>              # or: ./build-recovery.sh all
DRY_RUN=1 ./build-recovery.sh <board>    # show paths + module source, write nothing
```

Expected output ends with the sizes and a sha256:

```
==> Installing '<board>' blobs (WCNSS firmware, hostapd, libs) into the initramfs
==> Installing kernel modules (6.12.112) into the initramfs
==> Packing <board>: production kernel + recovery initramfs
    kernel   6585767 bytes (from the production boot image)
    ramdisk  5401367 bytes gzip (12069376 bytes cpio)
    image    11991040 bytes (11.44 MiB)
```

11.44 MiB has to fit the 16 MiB `recovery` partition (`p17`). A future kernel
or module set that pushes it past 16 MiB would be silently truncated by the
flash below, so compare `stat -c %s <board>.img` with the partition size.

### 3. Flash it to the recovery partition

From the **running system** (production or an older recovery), over SSH:

```sh
cat msm89xx/recovery/<board>.img | ssh root@192.168.10.1 'cat > /tmp/r.img'
ssh root@192.168.10.1 'sha256sum /tmp/r.img'      # must match the build output
ssh root@192.168.10.1 'dd if=/tmp/r.img of=/dev/mmcblk0p17 bs=1M conv=fsync; sync'
```

**`conv=fsync` is not optional.** Without it `dd` returns as soon as the data is
in the page cache; `sync` only *starts* writeback, and a `reboot` issued right
after resets the controller with pages still dirty. Measured on hmuf02v5:
`dd` reported `11991040 bytes copied, 0.085 s, 134 MB/s`, the immediate readback
matched the new hash — and the next boot ran the **previous** image, because
the whole write was lost. A real write takes ~1.25 s at ~9 MB/s.

Verify against the flash, not against the cache:

```sh
ssh root@192.168.10.1 'echo 3 > /proc/sys/vm/drop_caches; \
    dd if=/dev/mmcblk0p17 bs=11991040 count=1 2>/dev/null | sha256sum'
```

That must equal `sha256sum msm89xx/recovery/<board>.img`. Reading it back
*without* dropping the cache proves nothing — it reads the page cache you just
filled.

If the running system has no `/dev/mmcblk0p17` (an older recovery image built
before the block-node fix), run `mdev -s` once first.

### 4. Boot into it

```sh
ssh root@<box> reboot-recovery -f
```

`-f` matters: the graceful path (`ifdown -a`, `killall5`, `umount -a`) hangs
when a modem bearer is up and the box just sits there. From inside recovery the
equivalent is `reboot-mode-raw recovery`.

On a gated board the power button also works: hold it ~20 s for a PMIC hard
reset, which lands in recovery (see *Entering recovery* below).

### 5. Check it came up — over USB first

1. Host `dmesg`: `1d6b:0104` *OpenWrt / OpenWrt Recovery*, a new
   `/dev/ttyACM*` and a new network interface.
2. The host should get a `192.168.10.x` lease within ~10 s. **If it does not,
   the box still answers**: put a static `192.168.10.2/24` on that interface and
   `ssh root@192.168.10.1`. DHCP is a convenience, not a dependency.
3. On the box:

   ```sh
   ls /sys/class/net                 # br0  lo  usb0  wlan0
   iw dev                            # wlan0, type AP, ssid OpenWrt-Recovery
   ls /dev | grep mmcblk             # the eMMC nodes must be there
   ps w | grep -E 'hostapd|dnsmasq|dropbear|httpd'
   ```

4. Console: the ACM port gives a root shell with no login. It is re-spawned if
   it dies, and the banner is re-emitted every 5 s because boot-time output is
   lost when nobody is reading the port yet.

### 6. If USB does not come up at all

Work down this list — every row is a real failure seen on hardware:

| Symptom | Cause | Check |
|---|---|---|
| no ACM, no NIC | gadget never bound | `ls /sys/class/udc`, `cat /sys/kernel/config/usb_gadget/g1/UDC` |
| gadget present, console dead | `/dev/ttyGS0` missing, so the shell's redirection made a *file* | `ls -l /dev/ttyGS0` |
| no DHCP, box still pings | `/dev/urandom` missing, so dnsmasq exited | `cat /tmp/dnsmasq.err` |
| `/dev` almost empty | devtmpfs mount attempted on a kernel without it | `ls /dev` — `console`/`null`/`urandom` must exist |
| no `wlan0` | modules missing from the initramfs, or WCNSS firmware absent | `cat /proc/modules`, `cat /tmp/modules.err`, `dmesg | grep -i wcn` |
| `phy0` but no `wlan0` | backports mac80211 creates no default netdev | `iw phy phy0 interface add wlan0 type __ap` |

The first four are the same root cause (no devtmpfs). The `mdev` block at the
top of `/init` is the fix and must not be "simplified" back to a bare
`mount -t devtmpfs`.

### 7. Getting back to production

Recovery is **opt-in and one-shot**: the bootloader reads the PON reason once.
So:

* **Unplug and replug the board** (or just `reboot`) — it boots production. A
  recovery that is wedged, half-configured or stuck in a bad WiFi join is
  always escaped this way. Nothing in the recovery image writes the boot
  partition.
* `reboot-mode-raw bootloader` → fastboot; `/sbin/reboot-edl` → EDL
  `05c6:9008` for a full reflash.

### 8. WiFi

* The **AP** is on by default: `OpenWrt-Recovery`, WPA2-PSK or WPA3-SAE,
  passphrase `recovery`, bridged into `br0`, so a WiFi client gets a
  `192.168.10.x` lease from the same dnsmasq as USB.
* The **station** is opt-in: web UI → *WiFi station* → *Scan* → pick → *Join*.
  It is per-session (`/etc` is the initramfs) and is not bridged into `br0` —
  see *AP and station at the same time*.

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
| `irfs/lib/modules/<ver>/*.ko` | **NOT frozen / not committed** - copied out of `build_dir/.../root-msm89xx/lib/modules/<ver>/` on every build, so they always match the packed kernel: `mdt_loader`, `qcom_common`, `qcom_pil_info`, `qcom_wcnss_pil`, `compat`, `cfg80211`, `mac80211`, `wcn36xx` (the list is `RECOVERY_MODULES` in `build-recovery.sh` and must stay in sync with the `insmod` loop in `/init`) |

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
patch **452** — and it comes for free: the recovery image ships the production
kernel's `wcn36xx.ko`/`mac80211.ko` (the backports build), which already carries
the whole six-patch series (450/451/452/453/458/459). There is nothing to apply
at recovery-build time, and the rescue image runs the *same driver* as the
system it is rescuing.

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

* **The production kernel has no devtmpfs.** `mount -t devtmpfs /dev` fails and
  leaves `/dev` empty; everything that needs a node then fails *silently*
  (dnsmasq: no `/dev/urandom`; the console: `>/dev/ttyGS0` creates a regular
  file; every `2>/dev/null` creates a file named `null`). `/init` falls back to
  `mount -t tmpfs` + `mdev -s`.
* **The eMMC is probed ~1 s after `/init` starts**, so the first `mdev -s` runs
  before `/sys/block/mmcblk0` exists and no `/dev/mmcblk0*` node is ever created.
  Consequence: the web UI's "write a partition" fails and a hand-run `dd` says
  *"can't open '/dev/mmcblk0p17': No such file or directory"*. `/init` waits for
  `/sys/block/mmcblk0` and rescans.
* **The backports mac80211 creates no default netdev.** Loading the modules
  gives you `phy0` and `iw phy` works, but `iw dev` is empty and hostapd has no
  interface. The AP interface must be made explicitly with
  `iw phy phy0 interface add wlan0 type __ap` (`__ap`, not `AP`).
* **`dd` to the recovery partition without `conv=fsync` loses the write.** It
  returns from the page cache, a readback confirms the new data, and the next
  boot still runs the old image. Always `conv=fsync`, then verify after
  `echo 3 > /proc/sys/vm/drop_caches`.

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
