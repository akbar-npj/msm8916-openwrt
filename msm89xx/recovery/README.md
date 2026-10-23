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
* Network `192.168.10.1/24` on the NCM interface; a built-in `udhcpd`
  serves `192.168.10.2`-`.20` so the host gets an address automatically (no
  manual `ip addr add` needed)
* Root shell on `/dev/ttyGS0` (ACM console, no auth) and
  `ssh root@192.168.10.1` with **no password** (OpenWrt
  `600-allow-blank-root-password.patch`; `/etc/passwd` field 2 is empty)
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

After an upstream version bump, rebuild the binary from the matching source,
copy it into `irfs/`, and re-run `./build-recovery.sh <board>`.

## Traps learned on hardware

* `/init` must never use `exec` redirection — a failed redirection kills PID 1.
* The console shell needs `setsid -c` to get a controlling tty.
* `/etc/shells` must exist and contain `/bin/ash`, or dropbear denies **every**
  auth method pre-authentication (`checkusername()` validates `pw_shell`).
* dropbear reads the password hash from `/etc/passwd` field 2 (musl has no
  `getspnam()`); `/etc/shadow` is dead weight but kept for the uid/gid lookup.
* LED class names come from the `function`/`color` bindings, not the DT node
  names — the init matches `/sys/class/leds/*` by substring (`red`, `green`).

## Entering recovery on a buttonless board

All three triggers are handled by lk2nd (the aboot replacement built by
`generate_firmware.sh`):

1. **Hold the reset button at power-on.** Both gated boards wire it to TLMM
   GPIO 102 (active low, unused on every other board in this project). lk2nd
   reports it as VOLUMEUP, which its stock key handling maps to "boot the
   recovery partition". Enabled by `RECOVERY_KEY_GPIO=102` /
   `RECOVERY_KEY_ACTIVE_LOW=1`.
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
