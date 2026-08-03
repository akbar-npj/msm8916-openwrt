# OpenWrt for Qualcomm MSM8916 Devices

A complete OpenWrt build environment for Qualcomm MSM8916-based LTE routers and USB modem sticks.

This repository provides everything required to build reproducible firmware images using Docker, including the MSM8916 target, device-specific packages, overlays, firmware generation scripts, and GitHub Actions.

---

# Table of Contents

* Features
* Supported Devices
* Repository Layout
* Requirements
* Quick Start
* Build System
* OpenWrt Version Management
* Building Firmware
* Menuconfig
* Flashing
* Carrier Configuration
* GitHub Actions
* Roadmap
* Development
* Credits

---

# Features

## Reproducible Builds

* Docker-based build environment
* Persistent OpenWrt source tree
* Automatic source tree preparation
* Automatic feed installation
* Version-aware build workflow

## Networking

* Fully functional LTE modem
* Wi-Fi support
* USB Gadget support

  * ECM
  * NCM
  * RNDIS
  * ACM Shell
  * Mass Storage

## System

* SquashFS root filesystem
* OverlayFS
* Automatic overlay formatting
* Factory reset support

## Connectivity

* WireGuard
* Tailscale
* ModemManager
* QRTR
* RMTFS

## Utilities

* LuCI
* USB Gadget LuCI application
* LED management
* Firmware dumper

---

# Supported Devices

All supported devices use:

* Qualcomm MSM8916
* 384 MB RAM
* 4 GB eMMC

## Currently Supported

* UZ801v3
* UF02
* UFI001B
* HMU05

## Reference Devices

Development work for these devices has been moved to `TBR/`.

* MF68E
* M9S

---

# Repository Layout

```text
.
├── build.sh                 Build manager
├── scripts/
│   ├── openwrt-version.sh
│   ├── openwrt-prepare.sh
│   └── patch_atheros.sh
├── devenv/
│   ├── Dockerfile
│   ├── docker-compose.yml
│   └── dependencies.sh
├── msm89xx/                 MSM8916 target
├── packages/                Project packages
├── openwrt-overlay/         Repository overlay
├── diffconfig_*             Board configurations
├── TBR/                     Experimental work
└── openwrt/                 OpenWrt source tree (created automatically)
```

---

# Requirements

* Linux
* Docker
* Docker Compose
* Git

For flashing:

* EDL Tool

---

# Quick Start

Clone the repository.

Build the Docker image.

```bash
./build.sh image
```

Build firmware.

```bash
./build.sh build ufi001b
```

The build system automatically:

* clones OpenWrt if required
* checks out the configured version
* prepares the source tree
* updates feeds
* builds the firmware

No manual preparation is required.

---

# Build System

The build system is divided into three independent components.

## build.sh

Repository orchestrator.

Responsibilities:

* Docker management
* OpenWrt checkout
* Build orchestration
* Board selection

## scripts/openwrt-version.sh

Version management.

Responsibilities:

* Show current version
* Change OpenWrt version
* Machine-readable `--current` output

Examples:

```bash
./scripts/openwrt-version.sh
./scripts/openwrt-version.sh --current
./scripts/openwrt-version.sh main
./scripts/openwrt-version.sh v25.12.5
```

## scripts/openwrt-prepare.sh

Prepares an existing OpenWrt source tree.

Responsibilities:

* Install MSM8916 target
* Install project packages
* Apply repository overlay
* Apply project patches
* Update feeds
* Record preparation state

This script never:

* clones OpenWrt
* changes Git branches or tags
* invokes Docker

---

# OpenWrt Version Management

Show the configured version.

```bash
./build.sh version
```

Show only the configured version.

```bash
./build.sh version --current
```

Switch to a release.

```bash
./build.sh version v25.12.5
```

Switch to the development branch.

```bash
./build.sh version main
```

After changing the version:

```bash
./build.sh prepare --force
```

or simply build again:

```bash
./build.sh build ufi001b
```

The build system automatically detects when the source tree must be re-prepared.

---

# Building Firmware

List supported boards.

```bash
./build.sh list
```

Build firmware.

```bash
./build.sh build ufi001b
```

Rebuild from a clean tree.

```bash
./build.sh rebuild ufi001b
```

Open a shell inside the builder.

```bash
./build.sh shell
```

Run menuconfig.

```bash
./build.sh menuconfig ufi001b
```

Clean the build.

```bash
./build.sh clean
```

Deep clean.

```bash
./build.sh dirclean
```

Complete clean.

```bash
./build.sh distclean
```

---

# Flashing

Install the EDL tool.

https://github.com/bkerler/edl

Backup the original firmware.

```bash
edl rf backup.bin
```

Flash OpenWrt.

```bash
./openwrt-msm89xx-msm8916-*-flash.sh
```

The flashing script:

* backs up radio partitions
* writes GPT
* flashes firmware
* restores radio partitions

---

# Carrier Configuration

Some carriers require a device-specific MCFG configuration.

Extract the MCFG file from the original firmware.

Copy it to the router.

```bash
scp -O mcfg_sw.mbn \
root@192.168.1.1:/lib/firmware/MCFG_SW.MBN
```

Reboot the device.

---

# GitHub Actions

Two workflows are provided.

## Firmware Builds

Build firmware images for selected boards.

## Package Builds

Build project packages as APK and IPK.

---

# Roadmap

* Package repository for MSM8916
* eSIM (LPAC) support
* Additional device support
* Further build automation

---

# Development

The recommended workflow is:

```bash
./build.sh image
./build.sh build ufi001b
```

The build manager automatically:

* creates the Docker environment
* clones OpenWrt
* prepares the source tree
* builds firmware

---

# Credits

* **@ghosthgy** — Initial project foundation
* **@lkiuyu** — MSM8916 support, patches, and OpenStick feeds
* **@Mio-sha512** — USB gadget and firmware loader concepts
* **@AlienWolfX** — Carrier policy troubleshooting
* **@gw826943555** and **@asvow** — Tailscale LuCI application
