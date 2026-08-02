![OpenWrt logo](https://raw.githubusercontent.com/openwrt/openwrt/refs/heads/main/include/logo.png)

# OpenWrt for Qualcomm MSM8916 Devices

- Features
- Supported Devices
- Repository Layout
- Requirements
- Building
- OpenWrt Version Management
- Flashing
- Carrier Configuration
- GitHub Actions
- Roadmap
- Development
- Credits


# About OpenWrt for Qualcomm MSM8916 Devices

This repository provides a complete OpenWrt build environment for Qualcomm
MSM8916-based LTE routers and USB dongles.

Unlike a traditional OpenWrt tree, this project includes:

• Docker-based reproducible build environment
• Automated OpenWrt source preparation
• MSM8916 target
• Device packages
• Firmware generation scripts
• GitHub Actions for release builds

The project supports both stable OpenWrt releases and the latest development
branch.

## Supported Devices

Supported Devices

Qualcomm MSM8916
384 MB RAM
4 GB eMMC

Currently supported

• UZ801v3
• UF02
• UFI001B
• HMU05

Reference devices

• MF68E
• M9S

Reference work for MF68E and M9S has been moved to TBR/.

## Features

Features

Networking

• Fully functional LTE modem
• Wi-Fi
• USB Gadget
    - ECM
    - NCM
    - RNDIS
    - ACM Shell
    - Mass Storage

System

• SquashFS
• OverlayFS
• Automatic overlay formatting
• Factory reset

Connectivity

• WireGuard
• Tailscale
• ModemManager
• QRTR
• RMTFS

Utilities

• LuCI
• USB Gadget LuCI app
• LED management
• Firmware dumper

### Repository Layout
 

build.sh
    Main build manager

scripts/
    OpenWrt helper scripts

devenv/
    Docker environment

msm89xx/
    Target

packages/
    Project packages

openwrt-overlay/
    Files copied into OpenWrt

TBR/
    Experimental work

### Requirement

Docker
Docker Compose
Git
Linux host
For flashing: [edl tool](https://github.com/bkerler/edl)


### Building
./build.sh image
./build.sh shell
./build.sh build uz801
./build.sh build uf02
./build.sh menuconfig uz801
./build.sh clean

### OpenWrt Version Management
OpenWrt Version

Show current version

./build.sh version

Switch version

./build.sh version v25.12.5

Rebuild Docker image

./build.sh image

### Storage & Recovery
- **SquashFS Root**: Compressed root filesystem
- **OverlayFS**: ext4 overlay partition for user data (formatted automatically via preinit)
- **Factory Reset**: `firstboot` mechanism enabled

### Additional Packages
- **Tailscale**: LuCI app available as standalone package (APK and IPK)





### GitHub Actions (release builds)

GHA workflows automatically resolve the **latest OpenWrt 25.12.x** tag. Trigger manually from the Actions tab:

- **Build firmware**: `build.yml` — select a device (`uz801`, `uf02`, or `all`)
- **Build packages**: `build-package.yml` — builds `luci-app-tailscale`, `uci-usb-gadget`, and `luci-app-usb-gadget` in APK and IPK formats

### Local (snapshot builds)

1. Build the environment (defaults to OpenWrt `main`/snapshot):
```
cd devenv
docker compose build builder
```

2. Enter and build:
```
docker compose run --rm builder
cp /repo/diffconfig_uz801 .config
make defconfig
make -j$(nproc)
```

To build a specific release locally:
```
OPENWRT_VERSION=v24.10.2 docker compose build builder --no-cache
```

> **Supported versions:** OpenWrt 25.12.x and current snapshots (kernel 6.12). OpenWrt 24.10.x (kernel 6.6) compiles but does not boot — the Makefile supports it via `KERNEL_FOR_24` (currently commented out) if someone wants to investigate further.

## Installation

### Flashing from OEM Firmware

1. **Install EDL tool**: https://github.com/bkerler/edl
2. **Enter EDL mode**:
   - **UZ801v3**: See [PostmarketOS wiki guide](https://wiki.postmarketos.org/wiki/Zhihe_series_LTE_dongles_(generic-zhihe)#How_to_enter_flash_mode)

3. **Backup original firmware**:
   ```
   edl rf backup.bin
   ```

4. **Flash OpenWrt**:
   ```
   ./openwrt-msm89xx-msm8916-*-flash.sh
   ```

   > The script flashes entirely via EDL (no fastboot step). It automatically backs up radio partitions, writes the new GPT, firmware, boot and rootfs, and restores the backed-up partitions.

### Accessing Boot Modes

#### UZ801v3
- **Fastboot mode**: Insert device while holding the button
- **EDL mode**: Boot to fastboot first, then execute: `fastboot oem reboot-edl`

#### UF02
- **Fastboot mode**:
  - From OEM: `adb reboot bootloader`.
  - From OpenWrt: Enter `edl` and erase boot partition (`edl e boot`).
- **EDL mode**:
  - From OEM: `adb reboot bootloader`, flash `lk2nd` aboot. Reboot pressing the button.
  - From OpenWrt: Insert device while holding the button.

## Troubleshooting

### No Network / Modem Stuck at Searching

The modem requires region-specific MCFG configuration files.

#### Extract MCFG from Your Firmware

1. **Dump modem partition**:
   ```
   edl r modem modem.bin
   ```

2. **Mount and navigate**:
   ```
   # Mount modem.bin (it's a standard Linux image)
   cd image/modem_pr/mcfg/configs/mcfg_sw/generic/
   ```

3. **Select your region**:
   - `APAC` - Asia Pacific
   - `CHINA` - China
   - `COMMON` - Generic/fallback
   - `EU` - Europe
   - `NA` - North America
   - `SA` - South America
   - `SEA` - South East Asia

4. **Locate your carrier's MCFG**: Navigate to your telco's folder and find `mcfg_sw.mbn`. If your carrier isn't listed, use a generic configuration from the `common` folder.

#### Apply the Configuration

**Transfer to device** (capitalization matters!):
   ```
   scp -O mcfg_sw.mbn root@192.168.1.1:/lib/firmware/MCFG_SW.MBN
   # ... and reboot the device ...
   ```

## Roadmap

- [ ] Custom package server for msm89xx/msm8916
  - Note: Target-specific modules may require building from source via `make menuconfig`
  - The target-specific APK feed is automatically removed on first boot (msm89xx is not on downloads.openwrt.org)
- [ ] Investigate `lpac` for eSIM support
- [x] Memory expansion: `kmod-zram` + `zram-swap` enabled on all devices

## Credits

- **[@ghosthgy](https://github.com/ghosthgy/openwrt-msm8916)** - Initial project foundation
- **[@lkiuyu](https://github.com/lkiuyu/immortalwrt)** - MSM8916 support, patches, and OpenStick feeds
- **[@Mio-sha512](https://github.com/Mio-sha512/OpenStick-Builder)** - USB gadget and firmware loader concepts
- **[@AlienWolfX](https://github.com/AlienWolfX/UZ801-USB_MODEM/wiki/Troubleshooting)** - Carrier policy troubleshooting guide
- **[@gw826943555](https://github.com/gw826943555/luci-app-tailscale) & [@asvow](https://github.com/asvow)** - Tailscale LuCI application
