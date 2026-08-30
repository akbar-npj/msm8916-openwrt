# OpenWrt for Qualcomm Snapdragon 410 (MSM8916) 4G LTE USB Sticks & Modems

[![OpenWrt Version](https://img.shields.io/badge/OpenWrt-25.12.5-blue.svg)](https://openwrt.org/)
[![Kernel](https://img.shields.io/badge/Linux_Kernel-6.12-green.svg)](https://kernel.org/)
[![Architecture](https://img.shields.io/badge/Arch-aarch64-orange.svg)](https://en.wikipedia.org/wiki/AArch64)
[![License](https://img.shields.io/badge/License-GPL--2.0-lightgrey.svg)](LICENSE)

A production-ready, fully open-source OpenWrt port for Qualcomm Snapdragon 410 (MSM8916 / MSM8939) based 4G LTE USB modems, dongles, and pocket routers.

Features modern **Linux 6.12 mainline kernel**, **ModemManager 1.24**, **Qualcomm WCN36xx Wi-Fi**, **USB ConfigFS CDC NCM/ACM**, **true persistent eMMC EXT4 overlay storage**, and working **reboot-to-EDL and reboot-to-Fastboot recovery paths**.

---

## 🚀 Key Features

* **⚡ Plug-and-Play USB Networking**: High-speed **CDC NCM Ethernet** automatically bound to `br-lan` at `192.168.8.1/24` with a built-in DHCP server (avoids `192.168.1.x` subnet collisions with upstream home routers).
* **📟 Built-in USB Serial Console**: Instant root shell on `/dev/ttyACM0` (115200 baud) over USB via CDC ACM for zero-setup terminal access, debugging, and recovery.
* **📶 First-Boot Wi-Fi Auto-Start**: Automatically extracts Qualcomm WCNSS blobs, starts the remoteproc in-place, binds the physical radio path, and broadcasts an open `OpenWrt` 2.4 GHz AP (Channel 1, 2.412 GHz) on clean first boot.
* **🌐 4G LTE Cellular Data**: Native **ModemManager** integration with protocol-level auto-enable, safe empty PLMN home operator attachment, and continuous self-healing daemon monitoring (`modem-led-monitor`).
* **💾 Permanent eMMC Storage**: Automated `/dev/mmcblk0p15` (`rootfs_data`) EXT4 formatting and mounting, with preinit filesystem checking and automatic safe repair using `e2fsck -p`, providing persistent overlay storage without unnecessarily formatting an existing filesystem.
* **💡 Intuitive Hardware Status LEDs**:

  * 🟢 **Green LED** (`green:wlan`): Wi-Fi AP state and wireless client transmission.
  * 🔵 **Blue LED** (`blue:wan`): 4G LTE registration, data bearer, and internet activity.
  * 🔴 **Red LED** (`red:power`): Modem processor and subsystem health indicator.
* **🔄 Bulletproof Sysupgrade**: Graceful pre-upgrade service teardown (`platform_pre_upgrade`) eliminates kernel linked-list panics during LuCI web and CLI firmware upgrades, backed by step-by-step diagnostic logging to stdout and `/dev/kmsg`.
* **🛡️ HMU05 No-Sleep Fix**: Hardware-guarded native C patcher (`hmu05-patch-modem`) prevents Qualcomm Hexagon DSP 15-minute sleep stalls (`FUN_c03987e0` / `ERR_FATAL` bypass) with embedded SHA-256 header recalculation.
* **🚑 Reboot to Qualcomm EDL**: `reboot-edl` cleanly triggers Qualcomm Emergency Download (EDL / USB `05c6:9008`) mode without requiring hardware test-point access.
* **⚙️ Reboot to Fastboot**: `reboot-fastboot` switches the device into Qualcomm Fastboot mode for bootloader-level recovery and flashing.
* **🔧 Recovery Without Physical Access**: EDL and Fastboot reboot targets provide software-triggered recovery paths directly from a running OpenWrt system.

---

## 📟 Supported Devices

| Board Target  | Profile Name      | Device Model               | SoC     | RAM    | Storage   | Features                                                                                |
| :------------ | :---------------- | :------------------------- | :------ | :----- | :-------- | :-------------------------------------------------------------------------------------- |
| **`hmu05`**   | `generic-hmu05`   | Generic HMU05 (250605 V0S) | MSM8916 | 512 MB | 4 GB eMMC | USB NCM, ACM, Wi-Fi AP, LTE, No-Sleep Patch, Ramoops, Reboot-to-EDL, Reboot-to-Fastboot |
| **`ufi001b`** | `generic-ufi001b` | Generic UFI001B 4G Stick   | MSM8916 | 512 MB | 4 GB eMMC | USB NCM, ACM, Wi-Fi AP, LTE, Reboot-to-EDL, Reboot-to-Fastboot, Ramoops                 |
| **`uz801`**   | `yiming-uz801v3`  | YiMing UZ801 v3 Dongle     | MSM8916 | 512 MB | 4 GB eMMC | USB NCM, ACM, Wi-Fi AP, LTE, Reboot-to-EDL, Reboot-to-Fastboot, Swapped LED mapping     |
| **`uf02`**    | `generic-uf02`    | Generic UF02 / UF2 Stick   | MSM8916 | 512 MB | 4 GB eMMC | USB NCM, ACM, Wi-Fi AP, LTE, Reboot-to-EDL, Reboot-to-Fastboot                          |

---

## 🔄 Recovery and Reboot Modes

OpenWrt provides software-triggered reboot paths for Qualcomm recovery modes.

### Reboot to EDL

From an SSH shell or USB serial console:

```bash
reboot-edl
```

The device reboots directly into **Qualcomm Emergency Download (EDL) mode**.

On the host, verify that the Qualcomm EDL USB device is detected:

```bash
lsusb | grep 05c6:9008
```

Expected USB identification:

```text
05c6:9008 Qualcomm HS-USB QDLoader 9008
```

This allows the device to be recovered or reflashed using Qualcomm EDL tools such as `edl` or `qdl`.

### Reboot to Fastboot

From OpenWrt:

```bash
reboot-fastboot
```

The device reboots into **Fastboot mode**, allowing bootloader-level operations from the host.

Verify the device from the host with:

```bash
fastboot devices
```

### Android/ADB EDL

Where ADB is available, the standard Android command can also be used:

```bash
adb reboot edl
```

The OpenWrt-specific `reboot-edl` command is useful when the device is already running OpenWrt and ADB is not present.

---

## ⚡ Installation & Flashing

### First-Time Flashing (Qualcomm EDL 9008 Mode)

1. Put the USB modem into **EDL Mode** using either:

   * the hardware EDL test points while plugging into USB,
   * OpenWrt's `reboot-edl` command, or
   * `adb reboot edl` where ADB is available.

2. Verify the device is detected in EDL mode:

```bash
lsusb | grep 05c6:9008
```

3. Flash the kernel and rootfs partitions using `edl` or `qdl`:

```bash
# Using edl:
edl w boot openwrt/bin/targets/msm89xx/msm8916/openwrt-msm89xx-msm8916-<board>-squashfs-boot.img
edl w rootfs openwrt/bin/targets/msm89xx/msm8916/openwrt-msm89xx-msm8916-<board>-squashfs-system.img
edl reset
```

Or with `qdl`:

```bash
qdl --storage emmc prog_emmc_firehose_8916.mbn rawprogram_unsparse.xml patch0.xml
```

---

## 🔧 Fastboot Recovery

If the device is already running OpenWrt and supports the software Fastboot reboot:

```bash
reboot-fastboot
```

Then verify the device:

```bash
fastboot devices
```

Fastboot can be used for bootloader-level recovery operations where supported by the device's bootloader.

---

## 🔄 Sysupgrade

The OpenWrt sysupgrade path preserves the persistent `rootfs_data` overlay.

Before upgrading, the platform code performs the required service/subsystem teardown to avoid the previously observed reboot/kernel issues.

After flashing a new sysupgrade image, the persistent `/overlay` filesystem remains available:

```bash
mount | grep overlay
```

Expected:

```text
/dev/mmcblk0p15 on /overlay type ext4 (rw,noatime)
overlayfs:/overlay on / type overlay (...)
```

The preinit filesystem check verifies the EXT filesystem before `mount_root`:

```text
rootfs_data: ext filesystem detected
rootfs_data: running e2fsck -p
rootfs_data: filesystem errors repaired
mount_root: switching to ext4 overlay
```

An existing EXT filesystem is **not reformatted merely because it requires repair**. A new EXT4 filesystem is created only when no existing EXT filesystem is detected.

---

## 🔌 Default Device Access

| Service                  | Access Details                  | Default Credentials              |
| :----------------------- | :------------------------------ | :------------------------------- |
| **Web Interface (LuCI)** | `http://192.168.8.1`            | No password (set on first login) |
| **SSH Terminal**         | `ssh root@192.168.8.1`          | No password required             |
| **USB Serial Console**   | `screen /dev/ttyACM0 115200`    | Direct root shell                |
| **Wi-Fi Access Point**   | SSID: `OpenWrt` (2.4 GHz, Ch 1) | Open (No encryption by default)  |
| **EDL Recovery**         | `reboot-edl`                    | Qualcomm USB `05c6:9008`         |
| **Fastboot Recovery**    | `reboot-fastboot`               | `fastboot devices`               |

---

## 📂 Partition Layout (eMMC /dev/mmcblk0)

| Partition    | Label               | Size     | Type     | Purpose                                                              |
| :----------- | :------------------ | :------- | :------- | :------------------------------------------------------------------- |
| `p1` / `p3`  | `modem`             | ~64 MB   | VFAT     | Stock Qualcomm modem & WCNSS firmware blobs                          |
| `p6` / `p24` | `persist`           | ~32 MB   | EXT4     | Factory calibration and Wi-Fi NVRAM (`WCNSS_qcom_wlan_nv.bin`)       |
| `p13`        | `boot`              | ~32 MB   | Raw      | OpenWrt Linux 6.12 kernel + DTB (`boot.img`)                         |
| `p14`        | `system` / `rootfs` | ~1.5 GB  | SquashFS | OpenWrt read-only root filesystem (`system.img`)                     |
| `p15`        | `rootfs_data`       | ~1.5 GB+ | EXT4     | Writable persistent overlay storage (configurations, packages, logs) |

---

## 📦 Official Package & Kernel Driver Repository

This repository hosts a live APK feed on GitHub Pages with all pre-compiled Qualcomm MSM8916 kernel modules (`kmod-*`) and applications:

### Repository Feeds URL

* **Landing Page**: https://akbar-npj.github.io/msm8916-openwrt/

### Enable Custom Feeds on Device

```bash
cat << 'EOF' > /etc/apk/repositories.d/customfeeds.list
https://akbar-npj.github.io/msm8916-openwrt/releases/25.12.5/targets/msm89xx/msm8916/packages/packages.adb
EOF

apk update
```

### Install Extra Drivers & Packages

```bash
# Install USB Ethernet driver
apk add kmod-usb-net-rtl8152

# Install WireGuard VPN
apk add luci-app-wireguard
```

---

## 📜 License

This project is licensed under the **GNU General Public License v2.0 (GPL-2.0)**.

Qualcomm firmware dumper components are licensed under the BSD-3-Clause License.
