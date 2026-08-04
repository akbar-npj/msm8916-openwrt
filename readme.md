# OpenWrt for Qualcomm MSM8916 Devices

A complete OpenWrt build environment for Qualcomm MSM8916-based LTE routers and USB modem sticks.

This repository provides everything required to build reproducible firmware images using Docker, including the MSM8916 target, device-specific packages, overlays, firmware generation scripts, and GitHub Actions.


# README Update

## Supported Devices

| Device | Status | Notes |
|---------|--------|-------|
| Generic UFI001B | ✅ Supported | Tested |
| Generic HMU05 | ✅ Supported | Tested |
| Generic UF02 | ✅ Supported | Tested |
| YiMing UZ801V3 | ✅ Supported | Tested |

---

## Features

- OpenWrt stable branch 25.12.x based
- Qualcomm MSM8916 platform support
- LuCI Web Interface
- ModemManager support
- SMS Manager (LuCI)
- USB Gadget support
- WireGuard VPN
- ZeroTier
- Collectd monitoring
- LuCI Statistics
- CPU Performance Manager
- Automatic firmware package generation
- Cached firmware downloads
- Interactive build helper

---

## Repository Layout

```
.
├── build.sh                  Interactive build script
├── scripts/                  Build helper scripts
├── msm89xx/                  MSM8916 target additions
├── packages/                 Local packages and git submodules
├── openwrt-overlay/          Files copied into OpenWrt
├── diffconfig_*              Device configurations
└── readme.md
```

---

## Quick Start

Clone the repository together with all submodules.

```bash
git clone --recurse-submodules https://github.com/akbar-npj/msm8916-openwrt.git
cd msm8916-openwrt
```

If you already cloned the repository:

```bash
git submodule update --init --recursive
```

---

## Build

Run the interactive build helper.

```bash
./build.sh
```

The build helper can:

- Download OpenWrt
- Prepare the build environment
- Launch Docker
- Configure builds
- Save device configurations
- Build firmware
- Clean build trees

---

## Device Configurations

Current device profiles:

- HMU05
- UFI001B
- UF02
- UZ801V3

Device configurations are stored as:

```
diffconfig_hmu05
diffconfig_ufi001b
diffconfig_uf02
diffconfig_uz801
```

---

## Build Output

Generated firmware is placed under:

```
openwrt/bin/targets/msm89xx/msm8916/
```

Artifacts include:

- firmware.zip
- flash.sh
- squashfs-gpt_both0.bin
- sysupgrade images
- factory images (where applicable)

---

## Firmware Generation

Firmware generation automatically:

- Downloads required Qualcomm bootloader files
- Builds qhypstub
- Builds lk2nd
- Signs images using qtestsign
- Generates firmware.zip

Downloaded repositories and firmware archives are cached under:

```
openwrt/dl/msm8916-firmware/
```

This avoids downloading the same repositories and firmware on every build.

---

## Local Packages

Additional packages included by this project:

- luci-app-sms-manager
- luci-app-cpu-perf

Some packages are maintained as Git submodules.

After cloning, initialize submodules:

```bash
git submodule update --init --recursive
```

---

## Build Helper Scripts

### build.sh

Interactive build manager.

Features:

- Docker support
- Device selection
- menuconfig
- saveconfig
- clean
- rebuild
- firmware generation

---

### openwrt-prepare.sh

Automatically prepares the OpenWrt tree.

Responsibilities:

- Install MSM8916 target
- Install local packages
- Apply repository overlay
- Apply project patches
- Configure feeds
- Apply compatibility fixes

Refresh package feeds when required:

```bash
./scripts/openwrt-prepare.sh --refresh-feeds
```

---

## Included LuCI Applications

- CPU Performance
- SMS Manager
- Statistics
- File Manager
- Wake-on-LAN
- Package Manager
- USB Gadget

---

## Networking

Included services:

- ModemManager
- QMI
- MBIM
- QRTR
- WireGuard
- ZeroTier

---

## Monitoring

Included monitoring packages:

- collectd
- collectd-mod-cpu
- collectd-mod-interface
- collectd-mod-memory
- collectd-mod-load
- collectd-mod-network
- collectd-mod-iwinfo
- collectd-mod-sensors
- collectd-mod-thermal
- LuCI Statistics

---

## Development Notes

The project uses a persistent firmware download cache.

Repositories are downloaded only once and reused on subsequent builds.

To force feed updates:

```bash
./scripts/openwrt-prepare.sh --refresh-feeds
```

---

## Roadmap

- [x] HMU05 support
- [x] UFI001B support
- [x] UF02 support
- [x] UZ801V3 support
- [x] Firmware generation
- [x] Download caching
- [x] Interactive build helper
- [ ] GitHub Actions
- [ ] Automatic release builds
- [ ] Additional MSM8916 devices

---

## Credits

    @ghosthgy - Initial project foundation
    @lkiuyu - MSM8916 support, patches, and OpenStick feeds
    @Mio-sha512 - USB gadget and firmware loader concepts
    @AlienWolfX - Carrier policy troubleshooting guide
    @gw826943555 & @asvow - Tailscale LuCI application
    @hkfuertes - For his work on bringing these devices to latest snapshot

Special thanks to all OpenWrt and MSM8916 developers.


This project builds upon the work of:

- OpenWrt
- msm8916-mainline
- lk2nd
- qhypstub
- qtestsign

