# OpenWrt Build System Guide

This guide details the architecture, operational workflow, script lifecycle, and configuration mechanics of the build orchestration system in the `msm8916-openwrt` repository.

---

## 1. Overview & Architectural Principles

The build system automates OpenWrt firmware generation for Qualcomm Snapdragon 410/412 (MSM8916/MSM8916v2) based 4G LTE USB dongles, mobile routers, and development boards (such as `hmu05`, `uf02`, `ufi001b`, `uz801`).

### Three-Tier Script Architecture

The system divides responsibilities across three dedicated scripts:

```
                  ┌────────────────────────┐
                  │        User / CI       │
                  └───────────┬────────────┘
                              │
                              ▼
                      ┌───────────────┐
                      │   build.sh    │  (Top-Level Orchestrator)
                      └───┬───────┬───┘
                          │       │
          ┌───────────────┘       └────────────────┐
          ▼                                        ▼
┌────────────────────┐                   ┌────────────────────┐
│ openwrt-version.sh │                   │ openwrt-prepare.sh │
│ (Version Tracking) │                   │ (Tree Preparation) │
└────────────────────┘                   └────────────────────┘
```

1. **`build.sh` (Top-Level Orchestrator)**
   - Manages the complete lifecycle: Docker builder setup, OpenWrt git cloning/checkout, configuration deployment, compiling, and board image generation.
   - Runs on the host system and interfaces with the container via Docker Compose.
2. **`scripts/openwrt-version.sh` (Version Manager)**
   - Tracks and updates the desired upstream OpenWrt release or development branch in `devenv/.env`.
   - Discovers available upstream release tags via `git ls-remote`.
   - Never directly clones repositories, prepares trees, or interacts with Docker.
3. **`scripts/openwrt-prepare.sh` (Tree Preparation & Synchronization)**
   - Executes inside the Docker build container.
   - Injects project-specific targets (`msm89xx`), packages (`packages/`), kernel/package patches, overlays (`openwrt-overlay/`), and feeds into the raw OpenWrt checkout.
   - Never clones OpenWrt, switches branches, or manages Docker containers.

---

## 2. Source-of-Truth Model vs. Generated Tree

To maintain clean separation between upstream OpenWrt and custom Qualcomm hardware support:

| Component | Source of Truth (Git Tracked) | Destination in OpenWrt Build Tree |
|---|---|---|
| **Linux Target / BSP** | `msm89xx/` | `openwrt/target/linux/msm89xx/` |
| **Project Packages** | `packages/` | `openwrt/package/msm8916/` |
| **Package Patches** | `packages/<pkg>/patches/*.patch` | `openwrt/package/system/<pkg>/patches/` |
| **Filesystem Overlay** | `openwrt-overlay/` | `openwrt/` (merged into rootfs) |
| **Board Configurations** | `diffconfigs/<board>` | `openwrt/.config` (expanded via `make defconfig`) |
| **OpenWrt Version** | `devenv/.env` (`OPENWRT_VERSION`) | Checked out in `openwrt/` |

> [!IMPORTANT]
> Files under `openwrt/target/linux/msm89xx/` or `openwrt/package/msm8916/` are ephemeral copies. Any manual changes made directly inside `openwrt/` will be overwritten when `build.sh` synchronizes the BSP. Always make target changes in the root `msm89xx/` and `packages/` directories.

---

## 3. Containerized Build Environment

All toolchain generation, package compiling, and kernel building occur inside a reproducible Docker container:

- **Compose Configuration**: `devenv/docker-compose.yml`
- **Container Name**: `devenv-builder` (service: `builder`)
- **Repository Mount**: Root workspace mounted at `/repo` inside the container.
- **User Permissions**: Preserves host ownership by passing `HOST_UID=$(id -u)` and `HOST_GID=$(id -g)` into the container. Build artifacts created inside the container remain owned by your host user.

---

## 4. Operational Lifecycle & Execution Flow

### High-Level Build Workflow

When you run `./build.sh build <board>`:

```text
1. check_requirements
   └── Docker installed & running? Git available? Compose file present?

2. ensure_builder
   ├── Check if builder image exists (builds if missing)
   └── Starts 'builder' container via docker compose up -d

3. ensure_prepared
   ├── Reads configured version via scripts/openwrt-version.sh --current
   ├── Clones upstream OpenWrt if 'openwrt/.git' is missing
   ├── Checks out the requested version/tag (e.g. v25.12.5 or main)
   │
   ├── If .builder-state version matches current version:
   │   └── sync_bsp: Fast sync of msm89xx/, packages/, and patches into openwrt/
   │
   └── If .builder-state does not match or is missing:
       └── prepare_tree: Runs scripts/openwrt-prepare.sh in container
           ├── Copies msm89xx target into target/linux/
           ├── Strips out OpenWrt mac80211 patch from kernel patch dir
           ├── Copies project packages into package/msm8916
           ├── Applies package patches to package/system/<pkg>
           ├── Merges openwrt-overlay/
           ├── Applies mac80211 OpenWrt patch (package/kernel/mac80211/ath.mk)
           ├── Updates and installs OpenWrt package feeds
           ├── Applies ModemManager / LuCI compatibility fixes
           └── Records OPENWRT_VERSION and timestamp in .builder-state

4. prepare_config <board>
   ├── Copies diffconfigs/<board> to openwrt/.config
   └── Runs 'make defconfig V=sc' inside container to expand full configuration

5. Compilation
   └── Runs 'make -j$(($(nproc)+1)) V=sc' inside container
```

---

## 5. Command Reference

### `build.sh`

```bash
./build.sh <command> [arguments]
```

| Command | Arguments | Description |
|---|---|---|
| `help` | (none) | Displays usage instructions and available commands. |
| `list` | (none) | Lists all supported boards found in `diffconfigs/`. |
| `version` | `[args]` | Invokes `scripts/openwrt-version.sh` to view or switch OpenWrt versions. |
| `image` | (none) | Rebuilds the Docker builder container image. |
| `prepare` | `[--force]` | Clones and prepares the OpenWrt source tree. Use `--force` to disregard `.builder-state`. |
| `shell` | (none) | Drops directly into an interactive bash shell inside the builder container. |
| `build` | `<board... \| all>` | Validates boards and compiles firmware. Multiple boards or `all` can be specified. |
| `rebuild` | `<board... \| all>` | Executes `make clean` before compiling firmware for specified boards. |
| `menuconfig` | `<board>` | Loads `<board>` configuration into `.config` and launches the interactive ncurses menu. |
| `saveconfig` | `<board>` | Extracts minimal config diff via `./scripts/diffconfig.sh` and prompts to update `diffconfigs/<board>`. |
| `clean` | (none) | Runs `make clean` inside the OpenWrt tree (cleans target objects). |
| `dirclean` | (none) | Runs `make dirclean` (cleans targets, toolchain, and staging directories). |
| `distclean` | (none) | Runs `make distclean` (purges all build artifacts, downloads, and feeds). |

---

### `scripts/openwrt-version.sh`

```bash
# Print the currently configured version
./scripts/openwrt-version.sh --current

# View the current version and list the latest 20 upstream release tags
./scripts/openwrt-version.sh

# Change the target version (updates devenv/.env)
./scripts/openwrt-version.sh v25.12.5
./scripts/openwrt-version.sh main
```

---

### `scripts/openwrt-prepare.sh`

Primarily called by `build.sh` inside Docker, but can also be invoked manually inside a container:

```bash
./scripts/openwrt-prepare.sh [options] [openwrt-directory]
```

- `--version <version>`: Records version in `openwrt/.builder-state`.
- `--refresh-feeds`: Forces full purge of `tmp/` and re-runs `./scripts/feeds update -a && ./scripts/feeds install -a`.

---

## 6. Board Configuration Workflow

### Creating or Customizing a Board Configuration

1. **Launch `menuconfig`**:
   ```bash
   ./build.sh menuconfig <board>
   ```
   *Example:* `./build.sh menuconfig hmu05`

2. **Make Changes**:
   Select desired kernel modules, packages, or drivers in the interactive ncurses UI and select `< Save >` -> `< Exit >`.

3. **Save Changes to Diffconfig**:
   ```bash
   ./build.sh saveconfig <board>
   ```
   - `build.sh` runs `diffconfig.sh` in the container to extract only non-default options.
   - Compares with the existing `diffconfigs/<board>`.
   - Prompts for confirmation before overwriting: `Overwrite diffconfigs/<board>? [y/N]`

4. **Verify Build**:
   ```bash
   ./build.sh build <board>
   ```

---

## 7. Fast Incremental Synchronization (`sync_bsp`)

When developing kernel drivers, device trees, or packages:
- You do **not** need to re-run a full `prepare` (which would re-download and re-install feeds).
- `build.sh` checks `.builder-state`. If the version matches, it runs `sync_bsp()` automatically before building:
  1. Refreshes `openwrt/target/linux/msm89xx/` from `msm89xx/`.
  2. Refreshes `openwrt/package/msm8916/` from `packages/`.
  3. Updates patches under `openwrt/package/system/<pkg>/patches/`.
  4. Re-applies runtime fixes (e.g. ModemManager init script fix).

---

## 8. Troubleshooting & Common Pitfalls

### New Kernel Kconfig Options Prompted During `defconfig`
If the Linux kernel version changes or new options are introduced:
- OpenWrt may prompt interactively during `make defconfig`.
- Resolve the prompts, save the kernel configuration, and update `msm89xx/config-6.12`.

### Docker Permission Issues
If builds fail due to file permissions, ensure `HOST_UID` and `HOST_GID` match your local user:
```bash
HOST_UID=$(id -u) HOST_GID=$(id -g) ./build.sh image
```

### Resetting OpenWrt Source State
If the source tree enters an inconsistent state:
```bash
./build.sh prepare --force
```
Or for a complete clean slate:
```bash
./build.sh distclean
```
