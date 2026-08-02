
# OpenWrt for Qualcomm MSM8916 Devices

A complete OpenWrt build environment for Qualcomm **MSM8916-based LTE routers and USB dongles**.

## Features

- Docker-based reproducible build environment
- Automated OpenWrt source preparation
- Custom MSM8916 target
- Project-specific packages
- GitHub Actions for release builds
- Support for stable OpenWrt releases and the latest development branch

---

# Table of Contents

- About
- Features
- Supported Devices
- Repository Layout
- Requirements
- Quick Start
- Building
- OpenWrt Version Management
- GitHub Actions
- Installation
- Accessing Boot Modes
- Troubleshooting
- Roadmap
- Development
- Credits

---

# About

Unlike a traditional OpenWrt tree, this repository provides a complete development environment. The OpenWrt source tree is automatically prepared with the MSM8916 target, project packages, repository overlay, patches, and feeds.

The build system is centered around Docker and the `build.sh` helper.

---

# Supported Devices

Hardware:

- Qualcomm MSM8916
- 384 MB RAM
- 4 GB eMMC

Currently supported:

- UZ801v3
- UF02
- UFI001B
- HMU05

Reference devices:

- MF68E
- M9S

Reference work for MF68E and M9S has been moved to the `TBR/` directory.

---

# Repository Layout

| Path | Description |
|------|-------------|
| `build.sh` | Main build manager |
| `scripts/` | Helper scripts |
| `devenv/` | Docker environment |
| `msm89xx/` | MSM8916 target |
| `packages/` | Project packages |
| `openwrt-overlay/` | Repository overlay |
| `TBR/` | Experimental work |

---

# Requirements

- Linux
- Docker
- Docker Compose
- Git

For flashing:

- edl

---

# Quick Start

```bash
./build.sh image
./build.sh list
./build.sh build ufi001b
./build.sh shell
./build.sh menuconfig ufi001b
```

---

# Building

Build firmware:

```bash
./build.sh build hmu05
./build.sh build uf02
./build.sh build ufi001b
./build.sh build uz801
```

Cleaning:

```bash
./build.sh clean
./build.sh dirclean
./build.sh distclean
```

---

# OpenWrt Version Management

```bash
./build.sh version
./build.sh version main
./build.sh version v25.12.5
./build.sh image
```

---

# GitHub Actions

- Firmware builds
- Package builds
- Automatic OpenWrt version resolution

---

# Installation

Firmware is flashed through EDL using the generated flash script.

---

# Accessing Boot Modes

See the device-specific documentation for UZ801v3 and UF02.

---

# Troubleshooting

If the modem remains in the searching state, install the appropriate carrier MCFG file as `MCFG_SW.MBN` under `/lib/firmware/` and reboot.

---

# Roadmap

- Package repository
- Additional MSM8916 devices
- eSIM support
- ZRAM improvements
- CI enhancements

---

# Development

Main scripts:

- `build.sh`
- `scripts/openwrt.sh`
- `scripts/prepare_openwrt.sh`
- `scripts/patch_atheros.sh`

---

# Credits

- @ghosthgy
- @lkiuyu
- @Mio-sha512
- @AlienWolfX
- @gw826943555
- @asvow
