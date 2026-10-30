# MSM8916 OpenWrt Sysupgrade & LuCI Firmware Upgrade Guide

## Overview

This directory contains comprehensive documentation on OpenWrt firmware upgrades for Qualcomm Snapdragon 410 (MSM8916) 4G USB dongles (including HMU05, UFI001B, UZ801v3, UF02, and MF800B).

## Documentation Index

- [**MSM8916 Sysupgrade & LuCI Architecture Guide**](MSM8916_Sysupgrade_and_LuCI_Architecture_Guide.md): Complete architectural deep-dive, root cause analysis of intermittent LuCI failures, technical fixes in `platform.sh`, CLI and LuCI option reference, and step-by-step procedures.
- [**First boot after a clean install: the firmware dump (and the 45 s)**](FIRST_BOOT_FIRMWARE_DUMP.md): Why `sysupgrade -n` leaves the box with *no* firmware until the first boot finishes extracting it from the overlay — the failed `-2` loads, the measured 39 s vs 17 s timeline, and why it is not a regression.

---

## Quick Reference: Flashing Methods

### 1. Command Line Sysupgrade (CLI)

```bash
# Transfer image to device
scp openwrt-msm89xx-msm8916-<board>-squashfs-sysupgrade.bin root@192.168.8.1:/tmp/sysupgrade.bin

# Upgrade while preserving configuration
ssh root@192.168.8.1 "sysupgrade -v /tmp/sysupgrade.bin"

# Clean upgrade (wipe configuration and reset overlay)
ssh root@192.168.8.1 "sysupgrade -n -v /tmp/sysupgrade.bin"

# Force upgrade (override board metadata check)
ssh root@192.168.8.1 "sysupgrade -F -v /tmp/sysupgrade.bin"
```

### 2. LuCI Web Interface Upgrade

1. Open Web Browser and navigate to `http://192.168.8.1/cgi-bin/luci/admin/system/flashops`.
2. Under **Flash new firmware image**, click **Browse** and select `openwrt-msm89xx-msm8916-<board>-squashfs-sysupgrade.bin`.
3. Click **Upload...**.
4. LuCI verifies the image checksum and board compatibility metadata.
5. Choose whether to check **Keep settings and retain the current configuration**:
   - **Checked** (equivalent to `sysupgrade`): Preserves `/etc/config/*` and restored overlay.
   - **Unchecked** (equivalent to `sysupgrade -n`): Reformats `rootfs_data` partition as a clean EXT4 filesystem.
6. Click **Continue** / **Flash**. The device enters Stage 2 ramfs, writes the kernel and rootfs to eMMC, and automatically reboots.

---

### 3. Built-in upgrade page (r3 and later)

From **r3** the device can find, verify and install a new release on its own —
no `scp` and no manual download:

* **LuCI** — *System → Firmware Upgrade* (`/cgi-bin/luci/admin/system/firmware-upgrade`).
  It shows the running version, the published version for this board, the image
  size and the release notes, then downloads, checks the SHA-256 and flashes.
  A **Keep settings** box is ticked by default; untick it for `sysupgrade -n`.
* **CLI** —

  ```bash
  firmware-upgrade check          # JSON: available, version, url, sha256, size
  firmware-upgrade status
  firmware-upgrade download       # fetch + sha256 verify into /tmp
  firmware-upgrade apply [--no-keep]
  firmware-upgrade upgrade [--no-keep]   # check + download + apply
  ```

The client reads `firmware.json`, the manifest the release pipeline publishes
next to the images, and compares it with this device's `DISTRIB_RELEASE`. It
honours a `min_version` floor, so a device that is too old to jump is told to
flash once by hand instead of being handed an image it cannot apply.

> **Devices on r1/r2 have no client.** They must be flashed to r3 manually; from
> r3 on, every release updates in place. See
> [How Releases and Hotfixes Work](https://github.com/akbar-npj/msm8916-openwrt/wiki/How-Releases-and-Hotfixes-Work).

The engine ships as `msm89xx-firmware-upgrade` and the page as
`luci-app-firmware-upgrade` (in `DEFAULT_PACKAGES`, so every board has it).

---

## Recovery if the device becomes unreachable

These dongles have no Ethernet and no serial header, so if an upgrade leaves the
network stack broken, use **failsafe mode**:

1. Power on (or reboot) the dongle.
2. **Press the Restart button once within the first 7 seconds.** A single tap is
   enough. The **LED fast-flashes** (red, else green) when failsafe is active.
3. Reach the device at **`192.168.10.1`** (USB NIC) or **`/dev/ttyACM0`**
   (USB serial, 115200):

   ```bash
   ssh root@192.168.10.1
   screen /dev/ttyACM0 115200
   ```

4. Inside, run `mount_root` to mount the overlay, repair `/etc/config/*`, or
   `firstboot && reboot` to reset to defaults. If a sysupgrade image is staged in
   `/tmp`, failsafe runs the upgrade automatically instead of dropping to a shell.

Full guide: [`Docs/Failsafe/`](../Failsafe/README.md). As a last resort, reflash
via EDL/Fastboot ([`Docs/EDL/`](../EDL/)).

