# MSM8916 OpenWrt Sysupgrade & LuCI Firmware Upgrade Guide

## Overview

This directory contains comprehensive documentation on OpenWrt firmware upgrades for Qualcomm Snapdragon 410 (MSM8916) 4G USB dongles (including HMU05, UFI001B, UZ801v3, UF02, and MF800B).

## Documentation Index

- [**MSM8916 Sysupgrade & LuCI Architecture Guide**](MSM8916_Sysupgrade_and_LuCI_Architecture_Guide.md): Complete architectural deep-dive, root cause analysis of intermittent LuCI failures, technical fixes in `platform.sh`, CLI and LuCI option reference, and step-by-step procedures.

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

