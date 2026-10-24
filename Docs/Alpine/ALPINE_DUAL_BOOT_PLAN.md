# Alpine Linux dual-boot on msm89xx dongles — DESIGN PLAN (PARKED)

Status: **PARKED (2026-10-24)** — implement once OpenWrt is stable enough on the
target boards. Nothing in this plan blocks or touches the current OpenWrt images.
Companion facts verified live on 2026-10-24; see "Verified facts" below.

## Goal

Run Alpine Linux as an alternative OS on the msm8916 dongles (512 MB RAM, 4 GB
eMMC) alongside OpenWrt, selectable per boot, without bootloader risk.

## Why it is realistic

- Alpine already runs on msm8916: postmarketOS is Alpine + mainline kernel on
  this exact SoC. The userland port is proven; only the device glue is ours.
- 512 MB RAM is plenty for Alpine; 4 GB eMMC has room (stock userdata alone is
  2.37 GB; the port re-partitions anyway).
- The bootloader is **lk2nd** (open source, built by this repo with our patches,
  `msm89xx/image/lk2nd-patches/`), NOT the locked vendor aboot. It supports the
  BCB/recovery flow, reboot reasons, fastboot, and a boot-failover counter.
- A second bootable kernel slot already exists and is wired up (see below).

## What the port already ships (verified 2026-10-24, hmuf02v5)

| piece | state |
|---|---|
| `recovery` GPT partition (p17, 16 MiB) + `misc` (p16, 1 MiB) | present on all gated boards |
| `/sbin/reboot-recovery` | works: graceful teardown -> `reboot-mode-raw recovery` -> lk2nd boots the recovery slot once |
| rescue-image builder | `msm89xx/recovery/build-recovery.sh` -> `<board>.img` (kernel + initramfs, USB ACM/NCM console, SSH, web UI, EDL escape) |
| 3-strike failover | lk2nd counts boots that never reach userspace (cookie = last 512 B of misc); after `BOOT_FAILOVER_COUNT` (3) it boots the recovery slot; `S99recovery-bootcount` clears the cookie on every healthy boot |
| EDL recovery path | proven repeatedly; full stock dump retained (`GitIgnore/MelbonWhiteStock_Dump/`) |

Vendor-aboot string check (stock dump, 2026-10-24): `boot-recovery`,
`Recovery command: %d %s`, `error in emmc_recovery_init`, `recoveryfs`,
`bootselect`, `fastboot` — the standard Qualcomm lk misc/recovery flow is
compiled in; lk2nd supersedes it on the running boards anyway.

## Design (when implemented)

- `boot` partition keeps OpenWrt. The `recovery` slot carries the Alpine boot
  image (Alpine initramfs + mainline kernel + USB-gadget console).
- Selection = one-shot: `reboot-recovery` boots Alpine once; Alpine's init MUST
  1. clear the reboot reason / BCB so the NEXT reboot returns to OpenWrt,
  2. clear the misc failover cookie (or a failing Alpine would 3-strike into
     itself),
  3. bring up the USB ACM/NCM gadget console early (headless device).
- Persistent mode (phase 2): repartition ~1 GB for an Alpine rootfs, point the
  Alpine initramfs at it, add a `boot-alpine` / `boot-openwrt` toggle command.
  The misc failover then acts as the safety net (3 failed Alpine boots ->
  OpenWrt automatically) — the wiring already exists.
- Alternative zero-flash test: lk2nd has fastboot — `fastboot boot alpine.img`
  boots once from RAM. Requires reaching fastboot on a headless unit (USB).

## Explicitly rejected

- Modifying lk/aboot itself (brick risk, needs RE of the signed bootloader).
- VM/kexec-first approach (Qualcomm peripheral state across kexec is dicey;
  revisit only as an experiment).
- Interactive boot menus (no keys/screen on the dongles; flags only).

## Alpine-side TODO checklist (for implementation day)

1. Kernel: reuse the patched OpenWrt 6.12 source or the pmOS msm8916 kernel
   config; must include the USB configfs gadget + ncm/acm for the console.
2. Initramfs: Alpine `mkinitfs` profile with USB gadget + SSH (dropbear) and
   the BCB/cookie clearing above.
3. boot.img packing: gzip Image + board DTB + mkbootimg (same as the rescue
   image recipe in `msm89xx/recovery/README.md`).
4. Rootfs layout: Alpine base (~200 MB) + a data partition; decide ext4.
5. Toggle UX: `reboot-alpine` from OpenWrt (reuses reboot-mode infra) and a
   `reboot-openwrt` from Alpine (writes the same flag the way lk2nd expects).
6. Failover interplay test: boot-fail cookie behavior with recovery = Alpine.
7. Documentation: extend `msm89xx/recovery/README.md` + this file.

## Safety envelope

Never write: sbl1, tz, hyp, rpm, aboot (or their .bak twins), modem*, modemst*,
fsg, fsc, sec, persist. Everything else (boot, recovery, misc, rootfs) is
EDL-recoverable with the retained stock dump.

## References

- `msm89xx/recovery/README.md` — recovery image builder + failover details
- `msm89xx/image/lk2nd-patches/` — lk2nd failover patches
- `/sbin/reboot-mode.sh`, `/sbin/reboot-mode-raw`, `/etc/init.d/recovery-bootcount`
  (on-device, hmuf02v5)
- Session 2026-10-24: aboot.bin/GPT string analysis, misc=p16 / recovery=p17
  verification, Android-unit misc dump (empty — no vendor BCB ground truth).
