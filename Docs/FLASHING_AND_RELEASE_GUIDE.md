# Comprehensive Guide: Building, Flashing, and Releasing OpenWrt & Kmod Packages for MSM8916

This guide documents the complete end-to-end workflow used for **building target firmware**, **compiling extra kernel modules (kmods) & CUPS**, **deploying online APK repositories to GitHub Pages**, **uploading release binaries to GitHub Releases**, and **flashing/sysupgrading** Qualcomm Snapdragon 410 (MSM8916) 4G USB dongles.

---

## Table of Contents
1. [Prerequisites & Build Environment](#1-prerequisites--build-environment)
2. [Building Firmware Images](#2-building-firmware-images)
3. [Building Extra Kernel Modules & CUPS](#3-building-extra-kernel-modules--cups)
4. [Setting Up & Deploying Online Feeds (GitHub Pages)](#4-setting-up--deploying-online-feeds-github-pages)
5. [Packaging & Publishing GitHub Releases](#5-packaging--publishing-github-releases)
6. [Flashing Firmware to Device](#6-flashing-firmware-to-device)
   - [Method A: First-Time Flash via Qualcomm EDL (9008)](#method-a-first-time-flash-via-qualcomm-edl-9008)
   - [Method B: Upgrading Running Device (Sysupgrade)](#method-b-upgrading-running-device-sysupgrade)
7. [Device Configuration & Testing Package Installation](#7-device-configuration--testing-package-installation)

---

## 1. Prerequisites & Build Environment

The build is orchestrated using Docker for reproducible compilation.

```bash
# Verify Docker and Git
docker info
git status

# Prepare build tree and sync BSP
./build.sh prepare
```

---

## 2. Building Firmware Images

You can build firmware for individual boards or build all supported boards concurrently:

```bash
# Build all boards in diffconfigs/ (hmu05, ufi001b, uz801, uf02)
./build.sh build all

# Or build specific boards individually:
./build.sh build hmu05
./build.sh build ufi001b
./build.sh build uz801
./build.sh build uf02

# Clean and rebuild (runs `make clean` before compiling):
./build.sh rebuild hmu05
./build.sh rebuild all
```

All compiled binaries, flash scripts, manifests, and checksums are generated in:
`openwrt/bin/targets/msm89xx/msm8916/`

---

## 3. Building Extra Kernel Modules & CUPS

When building external kernel modules or packages (like CUPS Network Print Server) to match the exact running kernel checksum (`vermagic`):

### Enable Packages in `.config`
```bash
docker compose -f devenv/docker-compose.yml exec -T builder bash -c "
cd /repo/openwrt
cat << 'EOF' >> .config
CONFIG_PACKAGE_kmod-usb-printer=m
CONFIG_PACKAGE_kmod-fs-btrfs=m
CONFIG_PACKAGE_kmod-fs-f2fs=m
CONFIG_PACKAGE_kmod-fs-exfat=m
CONFIG_PACKAGE_kmod-gre=m
CONFIG_PACKAGE_kmod-vxlan=m
CONFIG_PACKAGE_kmod-veth=m
CONFIG_PACKAGE_kmod-bonding=m
CONFIG_PACKAGE_kmod-8021q=m
CONFIG_PACKAGE_kmod-l2tp=m
CONFIG_PACKAGE_kmod-sched-cake=m
CONFIG_PACKAGE_kmod-sched-bbr=m
CONFIG_PACKAGE_kmod-ipt-tproxy=m
CONFIG_PACKAGE_kmod-nft-tproxy=m
EOF
make defconfig
"
```

### Compile Kernel Packages & Package Index
```bash
docker compose -f devenv/docker-compose.yml exec -T builder bash -c "
cd /repo/openwrt
make package/kernel/linux/compile package/index V=s
make package/msm8916/cups/compile package/index V=s
"
```

---

## 4. Setting Up & Deploying Online Feeds (GitHub Pages)

OpenWrt 25.x uses `apk` package manager with `packages.adb` binary index databases. We serve them publicly via GitHub Pages on the `gh-pages` branch.

### Sync Packages to GitHub Pages Working Tree
```bash
# Prepare a temporary workspace for gh-pages
rm -rf /tmp/gh-pages-site/releases
mkdir -p /tmp/gh-pages-site/releases/25.12.5/targets/msm89xx/msm8916 /tmp/gh-pages-site/releases/25.12.5/packages

# Copy target kmod packages and generic packages
cp -a openwrt/bin/targets/msm89xx/msm8916/packages /tmp/gh-pages-site/releases/25.12.5/targets/msm89xx/msm8916/
cp -a openwrt/bin/packages/aarch64_generic /tmp/gh-pages-site/releases/25.12.5/packages/

# Commit and push to gh-pages branch
cd /tmp/gh-pages-site
git add -A
git commit -m "feat(repo): update 25.12.5 package repositories and kmod packages"
git push origin gh-pages
```

The online repository becomes instantly available at:
- `https://akbar-npj.github.io/msm8916-openwrt/releases/25.12.5/targets/msm89xx/msm8916/packages/packages.adb`
- `https://akbar-npj.github.io/msm8916-openwrt/releases/25.12.5/packages/aarch64_generic/base/packages.adb`
- `https://akbar-npj.github.io/msm8916-openwrt/releases/25.12.5/packages/aarch64_generic/packages/packages.adb`

---

## 5. Packaging & Publishing GitHub Releases

### Create Offline Kmod Bundle & Calculate Checksums
```bash
cd openwrt/bin/targets/msm89xx/msm8916

# Package offline kmods bundle
tar -czvf kmods-msm8916-25.12.5.tar.gz packages/

# Calculate SHA256 checksums
sha256sum openwrt-msm89xx-msm8916-generic-hmu05-* \
          openwrt-msm89xx-msm8916-generic-ufi001b-* \
          openwrt-msm89xx-msm8916-yiming-uz801v3-* \
          openwrt-msm89xx-msm8916-generic-uf02-* \
          kmods-msm8916-25.12.5.tar.gz > sha256sums
```

### Upload All Assets to GitHub Release using `gh` CLI
```bash
gh release upload -R akbar-npj/msm8916-openwrt v25.12.5-r1 \
  openwrt-msm89xx-msm8916-generic-hmu05-squashfs-boot.img \
  openwrt-msm89xx-msm8916-generic-hmu05-squashfs-system.img \
  openwrt-msm89xx-msm8916-generic-hmu05-squashfs-sysupgrade.bin \
  openwrt-msm89xx-msm8916-generic-hmu05-firmware.zip \
  openwrt-msm89xx-msm8916-generic-ufi001b-squashfs-boot.img \
  openwrt-msm89xx-msm8916-generic-ufi001b-squashfs-system.img \
  openwrt-msm89xx-msm8916-generic-ufi001b-squashfs-sysupgrade.bin \
  openwrt-msm89xx-msm8916-generic-ufi001b-firmware.zip \
  openwrt-msm89xx-msm8916-yiming-uz801v3-squashfs-boot.img \
  openwrt-msm89xx-msm8916-yiming-uz801v3-squashfs-system.img \
  openwrt-msm89xx-msm8916-yiming-uz801v3-squashfs-sysupgrade.bin \
  openwrt-msm89xx-msm8916-yiming-uz801v3-firmware.zip \
  openwrt-msm89xx-msm8916-generic-uf02-squashfs-boot.img \
  openwrt-msm89xx-msm8916-generic-uf02-squashfs-system.img \
  openwrt-msm89xx-msm8916-generic-uf02-squashfs-sysupgrade.bin \
  openwrt-msm89xx-msm8916-generic-uf02-firmware.zip \
  kmods-msm8916-25.12.5.tar.gz \
  sha256sums \
  --clobber
```

---

## 6. Flashing Firmware to Device

### Method A: First-Time Flash via Qualcomm EDL (9008)

1. Put the USB modem into **Emergency Download (EDL)** mode:
   - Short the board's hardware EDL test pads while inserting into USB port, **OR**
   - Run `reboot-edl` / `adb reboot edl` from terminal.
2. Confirm the device is in EDL mode:
   ```bash
   lsusb | grep 05c6:9008
   ```
3. Flash the `boot` and `rootfs` partitions using the `edl` tool:
   ```bash
   # Flash boot (kernel + dtb) partition
   edl w boot openwrt/bin/targets/msm89xx/msm8916/openwrt-msm89xx-msm8916-<board>-squashfs-boot.img

   # Flash rootfs (system) partition
   edl w rootfs openwrt/bin/targets/msm89xx/msm8916/openwrt-msm89xx-msm8916-<board>-squashfs-system.img

   # Reboot device into OpenWrt
   edl reset
   ```

---

### Method B: Upgrading Running Device (Sysupgrade)

For a device already running OpenWrt, you can upgrade non-destructively while preserving network settings:

#### Option 1: Via SSH Command Line
```bash
# Transfer sysupgrade image to device
scp openwrt/bin/targets/msm89xx/msm8916/openwrt-msm89xx-msm8916-<board>-squashfs-sysupgrade.bin root@192.168.8.1:/tmp/sysupgrade.bin

# Perform upgrade
ssh root@192.168.8.1 "sysupgrade -v /tmp/sysupgrade.bin"
```

#### Option 2: Via LuCI Web GUI
1. Open [http://192.168.8.1](http://192.168.8.1) in your browser.
2. Navigate to **System -> Backup / Flash Firmware -> Flash image...**.
3. Upload `openwrt-msm89xx-msm8916-<board>-squashfs-sysupgrade.bin` and click **Continue**.

---

## 7. Device Configuration & Testing Package Installation

Once the device boots up at `192.168.8.1`:

### 1. Default Access Details
- **IP Address**: `192.168.8.1`
- **SSH**: `ssh root@192.168.8.1` (no password)
- **LuCI Web GUI**: `http://192.168.8.1`
- **Wi-Fi SSID**: `OpenWrt` (2.4 GHz, Open)

### 2. Verify Repository Configuration on Device
Repository configuration is automatically set in `/etc/apk/repositories.d/customfeeds.list`:
```
https://akbar-npj.github.io/msm8916-openwrt/releases/25.12.5/targets/msm89xx/msm8916/packages/packages.adb
https://akbar-npj.github.io/msm8916-openwrt/releases/25.12.5/packages/aarch64_generic/base/packages.adb
https://akbar-npj.github.io/msm8916-openwrt/releases/25.12.5/packages/aarch64_generic/packages/packages.adb
```

### 3. Update Package Index & Install Drivers / CUPS
```bash
ssh root@192.168.8.1

# Update local package cache
apk update

# Search for any driver or package
apk list 'kmod*'
apk search cups

# Install CUPS Print Server and USB Printer Kernel Driver
apk add cups-daemon cups-client kmod-usb-printer

# Start and enable CUPS service
/etc/init.d/cupsd enable
/etc/init.d/cupsd start

# Install other kernel drivers (e.g. CAKE SQM, BTRFS, etc.)
apk add kmod-sched-cake kmod-fs-btrfs
```

CUPS Web Management interface is accessible at:
👉 **[http://192.168.8.1:631](http://192.168.8.1:631)**
