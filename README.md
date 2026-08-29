# OpenWrt for Qualcomm Snapdragon 410 (MSM8916) 4G LTE USB Sticks & Modems

[![OpenWrt Version](https://img.shields.io/badge/OpenWrt-25.12.5-blue.svg)](https://openwrt.org/)
[![Kernel](https://img.shields.io/badge/Linux_Kernel-6.12-green.svg)](https://kernel.org/)
[![Architecture](https://img.shields.io/badge/Arch-aarch64-orange.svg)](https://en.wikipedia.org/wiki/AArch64)
[![License](https://img.shields.io/badge/License-GPL--2.0-lightgrey.svg)](LICENSE)

A production-ready, fully open-source OpenWrt port for Qualcomm Snapdragon 410 (MSM8916 / MSM8939) based 4G LTE USB modems, dongles, and pocket routers.

Features modern **Linux 6.12 mainline kernel**, **ModemManager 1.24**, **Qualcomm WCN36xx Wi-Fi**, **USB ConfigFS CDC NCM/ACM**, and **true persistent eMMC EXT4 overlay storage**.

---

## 🚀 Key Features

- **⚡ Plug-and-Play USB Networking**: High-speed **CDC NCM Ethernet** automatically bound to `br-lan` at `192.168.8.1/24` with a built-in DHCP server (avoids `192.168.1.x` subnet collisions with upstream home routers).
- **📟 Built-in USB Serial Console**: Instant root shell on `/dev/ttyACM0` (115200 baud) over USB via CDC ACM for zero-setup terminal access, debugging, and recovery.
- **📶 First-Boot Wi-Fi Auto-Start**: Automatically extracts Qualcomm WCNSS blobs, starts the remoteproc in-place, binds the physical radio path, and broadcasts an open `OpenWrt` 2.4 GHz AP (Channel 1, 2.412 GHz) on clean first boot.
- **🌐 4G LTE Cellular Data**: Native **ModemManager** integration with protocol-level auto-enable, safe empty PLMN home operator attachment, and continuous self-healing daemon monitoring (`modem-led-monitor`).
- **💾 Permanent eMMC Storage**: Automated `/dev/mmcblk0p15` (`rootfs_data`) EXT4 formatting and mounting in `fstools`, providing true persistent storage without falling back to RAM `tmpfs`.
- **💡 Intuitive Hardware Status LEDs**:
  - 🟢 **Green LED** (`green:wlan`): Wi-Fi AP state and wireless client transmission.
  - 🔵 **Blue LED** (`blue:wan`): 4G LTE registration, data bearer, and internet activity.
  - 🔴 **Red LED** (`red:power`): Modem processor and subsystem health indicator.
- **🔄 Bulletproof Sysupgrade**: Graceful pre-upgrade service teardown (`platform_pre_upgrade`) eliminates kernel linked-list panics during LuCI web and CLI firmware upgrades, backed by step-by-step diagnostic logging to stdout and `/dev/kmsg`.
- **🛡️ HMU05 No-Sleep Fix**: Hardware-guarded native C patcher (`hmu05-patch-modem`) prevents Qualcomm Hexagon DSP 15-minute sleep stalls (`FUN_c03987e0` / `ERR_FATAL` bypass) with embedded SHA-256 header recalculation.

---

## 📟 Supported Devices

| Board Target | Profile Name | Device Model | SoC | RAM | Storage | Features |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`hmu05`** | `generic-hmu05` | Generic HMU05 (250605 V0S) | MSM8916 | 512 MB | 4 GB eMMC | USB NCM, ACM, Wi-Fi AP, LTE, No-Sleep Patch, Ramoops |
| **`ufi001b`** | `generic-ufi001b` | Generic UFI001B 4G Stick | MSM8916 | 512 MB | 4 GB eMMC | USB NCM, ACM, Wi-Fi AP, LTE, Reboot-to-EDL, Ramoops |
| **`uz801`** | `yiming-uz801v3` | YiMing UZ801 v3 Dongle | MSM8916 | 512 MB | 4 GB eMMC | USB NCM, ACM, Wi-Fi AP, LTE, Swapped LED mapping |
| **`uf02`** | `generic-uf02` | Generic UF02 / UF2 Stick | MSM8916 | 512 MB | 4 GB eMMC | USB NCM, ACM, Wi-Fi AP, LTE |

---

## 🛠️ Building Firmware

The build environment is completely containerized with Docker, ensuring reproducible builds across all Linux distributions.

### 1. Prerequisites
Ensure you have **Docker** and **Docker Compose** installed on your host system.

### 2. Prepare the OpenWrt Tree
Clone the repository and prepare the source tree, package feeds, and kernel patches:
```bash
git clone https://github.com/akbar-npj/msm8916-openwrt.git
cd msm8916-openwrt

# Prepare OpenWrt sources, feeds, and overlays
./build.sh prepare
```

### 3. Build for Your Target Board
Compile the complete firmware image:
```bash
# Build for HMU05
./build.sh build hmu05

# Build for UFI001B
./build.sh build ufi001b

# Build for UZ801 v3
./build.sh build uz801

# Build for UF02
./build.sh build uf02
```

### 4. Output Binaries
Generated firmware images will be placed in `openwrt/bin/targets/msm89xx/msm8916/`:
- `openwrt-msm89xx-msm8916-<board>-squashfs-sysupgrade.bin`: Unified sysupgrade archive for LuCI and CLI upgrades.
- `openwrt-msm89xx-msm8916-<board>-squashfs-boot.img`: Linux kernel image (flashed to partition 13 `boot`).
- `openwrt-msm89xx-msm8916-<board>-squashfs-system.img`: SquashFS root filesystem image (flashed to partition 14 `system` / `rootfs`).
- `openwrt-msm89xx-msm8916-<board>-firmware.zip`: Qualcomm bootloader package.

---

## ⚡ Installation & Flashing

### First-Time Flashing (Fastboot)

1. Put the USB modem into **Fastboot Mode** (hold boot button while plugging in or execute `adb reboot bootloader`).
2. Flash the kernel and rootfs partitions:
   ```bash
   fastboot flash boot openwrt/bin/targets/msm89xx/msm8916/openwrt-msm89xx-msm8916-<board>-squashfs-boot.img
   fastboot flash rootfs openwrt/bin/targets/msm89xx/msm8916/openwrt-msm89xx-msm8916-<board>-squashfs-system.img
   # If 'rootfs' partition is named 'system':
   # fastboot flash system openwrt/bin/targets/msm89xx/msm8916/openwrt-msm89xx-msm8916-<board>-squashfs-system.img

   fastboot reboot
   ```

### Subsequent Upgrades (Sysupgrade)

#### Option A: Via LuCI Web Interface
1. Open [http://192.168.8.1](http://192.168.8.1) in your browser.
2. Go to **System $\rightarrow$ Backup / Flash Firmware $\rightarrow$ Flash image...**.
3. Select `openwrt-msm89xx-msm8916-<board>-squashfs-sysupgrade.bin`.
4. Click **Upload** and proceed.

#### Option B: Via SSH Command Line
```bash
scp openwrt/bin/targets/msm89xx/msm8916/openwrt-msm89xx-msm8916-<board>-squashfs-sysupgrade.bin root@192.168.8.1:/tmp/sysupgrade.bin
ssh root@192.168.8.1 "sysupgrade -v /tmp/sysupgrade.bin"
```

---

## 🔌 Default Device Access

| Service | Access Details | Default Credentials |
| :--- | :--- | :--- |
| **Web Interface (LuCI)** | [http://192.168.8.1](http://192.168.8.1) | No password (set on first login) |
| **SSH Terminal** | `ssh root@192.168.8.1` | No password required |
| **USB Serial Console** | `screen /dev/ttyACM0 115200` | Direct root shell |
| **Wi-Fi Access Point** | SSID: `OpenWrt` (2.4 GHz, Ch 1) | Open (No encryption by default) |

---

## 📂 Partition Layout (eMMC /dev/mmcblk0)

| Partition | Label | Size | Type | Purpose |
| :--- | :--- | :--- | :--- | :--- |
| `p1` / `p3` | `modem` | ~64 MB | VFAT | Stock Qualcomm modem & WCNSS firmware blobs |
| `p6` / `p24`| `persist` | ~32 MB | EXT4 | Factory calibration and Wi-Fi NVRAM (`WCNSS_qcom_wlan_nv.bin`) |
| `p13` | `boot` | ~32 MB | Raw | OpenWrt Linux 6.12 kernel + DTB (`boot.img`) |
| `p14` | `system` / `rootfs` | ~1.5 GB | SquashFS | OpenWrt read-only root filesystem (`system.img`) |
| `p15` | `rootfs_data` | ~1.5 GB+ | EXT4 | Writable overlay storage (configurations, packages, logs) |

---

## 📦 Official Package & Kernel Driver Repository

This repository hosts a live APK feed on GitHub Pages with all pre-compiled Qualcomm MSM8916 kernel modules (`kmod-*`) and applications:

### Repository Feeds URL:
- **Landing Page**: [https://akbar-npj.github.io/msm8916-openwrt/](https://akbar-npj.github.io/msm8916-openwrt/)

### Enable Feeds on Device:
```bash
cat << 'EOF' > /etc/apk/repositories.d/customfeeds.list
https://akbar-npj.github.io/msm8916-openwrt/releases/25.12.5/targets/msm89xx/msm8916/packages/packages.adb
https://akbar-npj.github.io/msm8916-openwrt/releases/25.12.5/packages/aarch64_generic/base/packages.adb
https://akbar-npj.github.io/msm8916-openwrt/releases/25.12.5/packages/aarch64_generic/luci/packages.adb
https://akbar-npj.github.io/msm8916-openwrt/releases/25.12.5/packages/aarch64_generic/packages/packages.adb
EOF

apk update
```

### Install Extra Drivers & Packages:
```bash
# Install USB Ethernet driver
apk add kmod-usb-net-rtl8152

# Install WireGuard VPN
apk add luci-app-wireguard
```

---

## 📜 License

This project is licensed under the **GNU General Public License v2.0** (GPL-2.0).
Qualcomm firmware dumper components are licensed under the BSD-3-Clause License.
