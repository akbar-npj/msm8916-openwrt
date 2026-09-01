# MSM8916 OpenWrt Project — Build Flow and Architecture

## 1. Purpose

This document records how the `msm8916-openwrt-clean` project is structured, how the project scripts interact with OpenWrt, and where Linux kernel/BSP patches enter the build.

The project uses three main scripts:

- `build.sh` — top-level build manager
- `scripts/openwrt-version.sh` — OpenWrt version manager
- `scripts/openwrt-prepare.sh` — prepares/synchronizes the OpenWrt source tree

The key architectural point is that the repository's `msm89xx/` directory is the **BSP source of truth** for the OpenWrt `target/linux/msm89xx` target.

---

## 2. Repository Layout

```text
msm8916-openwrt-clean/
├── build.sh
├── scripts/
│   ├── openwrt-version.sh
│   └── openwrt-prepare.sh
├── msm89xx/
│   ├── Makefile
│   ├── config-6.12
│   ├── image/
│   ├── modules.mk
│   ├── msm8916/
│   └── patches/
├── packages/
├── openwrt-overlay/
├── devenv/
│   ├── .env
│   └── docker-compose.yml
└── openwrt/
    └── ... OpenWrt source tree ...
```

### Source of truth vs generated tree

The maintained BSP is:

```text
msm89xx/
```

The generated OpenWrt target is:

```text
openwrt/target/linux/msm89xx/
```

`openwrt-prepare.sh` copies the former into the latter. Therefore persistent changes belong in the project repository, not only in the generated OpenWrt tree.

---

## 3. `openwrt-version.sh`

### Purpose

`openwrt-version.sh` manages the OpenWrt version selected by the project.

It does **not**:

- clone OpenWrt
- prepare OpenWrt
- invoke Docker
- build OpenWrt

It reads and modifies:

```text
devenv/.env
```

specifically:

```text
OPENWRT_VERSION=...
```

### Commands

```bash
./scripts/openwrt-version.sh --current
```

Show current version.

```bash
./scripts/openwrt-version.sh
```

Show current version and available releases.

```bash
./scripts/openwrt-version.sh main
```

Select development `main`.

```bash
./scripts/openwrt-version.sh v25.12.5
```

Select a stable release.

The script discovers stable tags using:

```bash
git ls-remote --tags https://git.openwrt.org/openwrt/openwrt.git
```

### Important rule

`openwrt-version.sh` selects the desired version. The actual checkout/version handling is orchestrated by `build.sh`.

---

## 4. `build.sh`

`build.sh` is the top-level project build manager.

Its role is to orchestrate:

1. environment validation
2. Docker builder setup
3. OpenWrt checkout/version handling
4. OpenWrt preparation
5. configuration preparation
6. OpenWrt compilation
7. image generation

Normal board builds should therefore be started with:

```bash
./build.sh build <board>
```

For example:

```bash
./build.sh build ufi001b
```

---

## 5. High-Level Build Flow

```text
User
 │
 │ ./build.sh build ufi001b
 ▼
build.sh
 │
 ├── validate environment
 ├── ensure Docker builder
 ├── select/prepare OpenWrt version
 │
 ├── openwrt-prepare.sh
 │      │
 │      ├── install msm89xx BSP
 │      ├── install project packages
 │      ├── install package patches
 │      ├── apply repository overlay
 │      ├── apply OpenWrt source patches
 │      ├── update/install feeds
 │      └── compatibility fixes
 │
 ├── prepare OpenWrt configuration
 │
 └── OpenWrt build
        │
        ▼
   OpenWrt build system
        │
        ├── target/linux/msm89xx
        ├── Linux kernel
        ├── DTS/DTB
        ├── root filesystem
        └── image generation
                │
                ▼
          final firmware
```

---

## 6. `openwrt-prepare.sh`

This script is the bridge between the project's BSP repository and the OpenWrt source tree.

Its documented responsibilities are:

- install `msm89xx` target
- install project packages
- apply repository overlay
- apply project patches
- update/install package feeds
- apply compatibility fixes
- record preparation state

It does **not**:

- clone OpenWrt
- check out branches/tags
- invoke Docker
- read `devenv/.env`

---

## 7. BSP Installation

The important operation is effectively:

```bash
TARGET_DIR="$REPO_DIR/msm89xx"

rm -rf "$OPENWRT_DIR/target/linux/msm89xx"

cp -a     "$TARGET_DIR"     "$OPENWRT_DIR/target/linux/"
```

Therefore:

```text
Project BSP
msm89xx/
    │
    │ copy
    ▼
OpenWrt target
openwrt/target/linux/msm89xx/
```

### Consequence

Every preparation can replace the generated target.

Therefore this is **not** a persistent location for project changes:

```text
openwrt/target/linux/msm89xx/
```

Persistent changes must be made in:

```text
msm89xx/
```

---

## 8. Why the 810/811 Patches Previously Disappeared

During kernel debugging, patches were initially created under:

```text
openwrt/target/linux/msm89xx/patches/
```

They worked in the manually modified kernel tree, but disappeared after:

```bash
./build.sh build ufi001b
```

because preparation removes the generated target and copies the BSP again.

The correct locations are:

```text
msm89xx/patches/
```

For the current project:

```text
msm89xx/patches/
├── 801-arm64-dts-qcom-add-devices-makefile.patch
├── 802-arm64-dts-qcom-msm8916-label-reserved-memory.patch
├── 803-arm64-dts-qcom-swap-leds-uz801.patch
├── 804-arm64-dts-qcom-add-msm8916-generic-uf02.patch
├── 805-arm64-dts-qcom-add-msm8916-generic-hmu05.patch
├── 806-arm64-dts-qcom-add-msm8916-generic-ufi001b.patch
├── 808-bam-dmux-stats.patch
├── 809-mac80211-enable-wcn36xx.patch
├── 813-msm8916-reboot-to-edl-support.patch
├── 815-qcom-sysmon-ignore-wcnss-modem-ssr.patch
└── 999-tsens-propagate-eprobe-defer.patch
```

After preparation they appear automatically under:

```text
openwrt/target/linux/msm89xx/patches/
```

This is now verified to work.

---

## 9. Current BSP Patch Organization

The current patch set includes:

```text
801-arm64-dts-qcom-add-devices-makefile.patch
802-arm64-dts-qcom-msm8916-label-reserved-memory.patch
803-arm64-dts-qcom-swap-leds-uz801.patch
804-arm64-dts-qcom-add-msm8916-generic-uf02.patch
805-arm64-dts-qcom-add-msm8916-generic-hmu05.patch
806-arm64-dts-qcom-add-msm8916-generic-ufi001b.patch
808-bam-dmux-stats.patch
809-mac80211-enable-wcn36xx.patch
813-msm8916-reboot-to-edl-support.patch
815-qcom-sysmon-ignore-wcnss-modem-ssr.patch
999-tsens-propagate-eprobe-defer.patch
```

### Special case: 809

`809-mac80211-enable-wcn36xx.patch` is stored with the BSP patches for organization, but it is an **OpenWrt source-tree patch**, not a Linux kernel patch.

`openwrt-prepare.sh` removes it from:

```text
target/linux/msm89xx/patches/
```

and applies it separately to the OpenWrt source.

Therefore:

```text
Linux kernel patches
    └── target/linux/msm89xx/patches/

OpenWrt source-tree patches
    └── applied separately by openwrt-prepare.sh
```

---

## 10. Project Packages

The project maintains packages under:

```text
packages/
```

They are installed into:

```text
openwrt/package/msm8916/
```

The generated package directory is recreated during preparation.

Therefore `packages/` is the persistent source of truth.

---

## 11. Package Patches

Package-specific patches live under:

```text
packages/<package>/patches/
```

They are copied to:

```text
openwrt/package/system/<package>/patches/
```

Persistent changes therefore belong under `packages/`, not only in the generated OpenWrt tree.

---

## 12. Repository Overlay

The project also has:

```text
openwrt-overlay/
```

When present, its contents are copied into:

```text
openwrt/
```

This is intended for files that need to be overlaid directly into the OpenWrt source tree.

---

## 13. OpenWrt Feeds

Normal preparation checks these feeds:

```text
packages
luci
routing
telephony
video
smsmanager
```

If they are already present, preparation reports that the feeds are already installed.

A full refresh can be requested with:

```bash
./scripts/openwrt-prepare.sh --refresh-feeds
```

---

## 14. Preparation State

The preparation script writes:

```text
openwrt/.builder-state
```

including information such as:

```text
OPENWRT_VERSION=...
PREPARED_AT=...
```

This records which OpenWrt version the tree was prepared for.

---

## 15. When OpenWrt Takes Over

After preparation, the resulting tree contains:

```text
openwrt/
├── target/
│   └── linux/
│       └── msm89xx/
│           ├── Makefile
│           ├── config-6.12
│           ├── image/
│           ├── modules.mk
│           ├── msm8916/
│           └── patches/
├── package/
├── feeds/
└── ...
```

At this point the standard OpenWrt build machinery takes over.

The project-specific BSP has been converted into an OpenWrt target.

---

## 16. Linux Kernel Build Flow

Conceptually:

```text
msm89xx/
    │
    ▼
openwrt-prepare.sh
    │
    ▼
openwrt/target/linux/msm89xx/
    │
    ▼
OpenWrt kernel build machinery
    │
    ▼
build_dir/target-aarch64_generic_musl/
linux-msm89xx_msm8916/
    │
    ├── Linux source
    ├── kernel configuration
    ├── kernel patches
    ├── DTS sources
    └── build artifacts
```

The current kernel source tree is:

```text
linux-6.12.94/
```

Kernel patches under:

```text
target/linux/msm89xx/patches/
```

are applied as part of the OpenWrt kernel build.

---

## 17. Verifying the Kernel Artifact

The uncompressed kernel is:

```text
build_dir/target-aarch64_generic_musl/linux-msm89xx_msm8916/linux-6.12.94/arch/arm64/boot/Image
```

For example:

```bash
strings build_dir/target-aarch64_generic_musl/linux-msm89xx_msm8916/linux-6.12.94/arch/arm64/boot/Image | grep 'MSM8916 TEST'
```

This was used to verify that the modified kernel source had actually been compiled.

---

## 18. Android Boot Image Generation

The MSM89xx image build creates an Android boot image.

The project image flow uses:

```text
kernel-bin
    │
    ▼
gzip
    │
    ▼
append-dtb
    │
    ▼
aboot-img
```

The resulting boot image can be inspected with:

```bash
file <boot-image>
```

To extract it:

```bash
unpack_bootimg     --boot_img <boot-image>     --out /tmp/boot-test
```

The extracted kernel is gzip compressed.

Decompress it:

```bash
gzip -dc /tmp/boot-test/kernel     > /tmp/boot-test/kernel.uncompressed
```

Then verify:

```bash
strings /tmp/boot-test/kernel.uncompressed     | grep 'MSM8916 TEST'
```

This verifies that the final boot image contains the modified kernel.

---

## 19. DTB and Board Selection

The MSM8916 target builds board-specific DTBs such as:

```text
image-msm8916-generic-ufi001b.dtb
image-msm8916-generic-hmu05.dtb
image-msm8916-generic-uf02.dtb
image-msm8916-yiming-uz801v3.dtb
```

Board-specific hardware changes therefore belong in the corresponding DTS or in a patch modifying that DTS.

---

## 20. Universal vs Board-Specific Debugging

### Patch 802

```text
802-arm64-dts-qcom-msm8916-label-reserved-memory.patch
```

changes:

```dts
reserved-memory {
```

to:

```dts
reserved_memory: reserved-memory {
```

This provides a label that board DTS files can reference.

This infrastructure is intended to be MSM8916-wide.

### Board Ramoops Integration (805 & 806)

In earlier iterations, ramoops logging nodes were added as separate incremental patches (811 for UFI001B, 812 for HMU05). These have been consolidated directly into the respective board DTS patches:

- `805-arm64-dts-qcom-add-msm8916-generic-hmu05.patch`
- `806-arm64-dts-qcom-add-msm8916-generic-ufi001b.patch`

Both boards allocate the verified 1MB System RAM region:

```dts
&reserved_memory {
        ramoops@8db00000 {
                compatible = "ramoops";
                reg = <0x0 0x8db00000 0x0 0x00100000>;
                record-size = <0x00040000>;
                console-size = <0x00040000>;
                pmsg-size = <0x00040000>;
        };
};
```

The region `0x8db00000 - 0x8dbfffff` is System RAM immediately below the modem reserved region at `0x8dc00000`.

### Patch 813

```text
813-msm8916-reboot-to-edl-support.patch
```

Implements hardware-level Emergency Download Mode (EDL / 9008) trigger across Qualcomm SCM, PM8916 PON warm reset configuration, IMEM download cookies (`0x322A4F99`, `0xC67E4350`, `0x77777777` at `0x08600FE0` and restart reason `0x00000001` at `0x0860065C`), and `msm-poweroff.c` restart handler invoked on `reboot edl` / `reboot dload`.

---

## 21. Ramoops / PSTORE Debugging

The current kernel configuration enables:

```text
CONFIG_PSTORE=y
CONFIG_PSTORE_COMPRESS=y
CONFIG_PSTORE_CONSOLE=y
CONFIG_PSTORE_PMSG=y
CONFIG_PSTORE_RAM=y
```

The DTB `ramoops` node provides the persistent RAM region.

Conceptually:

```text
MSM8916 RAM
    │
    ├── normal System RAM
    ├── kernel
    ├── reserved regions
    └── ramoops region
            │
            ▼
          pstore
            │
            ▼
    persistent crash/log data
```

This is intended to preserve useful kernel information across reboots and crashes.

---

## 22. Important Debugging Lesson

A modified kernel source tree does not prove that the device is running that kernel.

Verification should proceed through several layers:

```text
1. Repository source
       │
       ▼
2. Prepared OpenWrt target
       │
       ▼
3. Kernel source tree
       │
       ▼
4. Compiled Image
       │
       ▼
5. Generated boot.img
       │
       ▼
6. Extracted boot kernel
       │
       ▼
7. Running device
       │
       ▼
8. Runtime behavior
```

Useful checks include:

```bash
grep ...
strings Image ...
file boot.img
unpack_bootimg ...
strings kernel.uncompressed ...
dmesg ...
/proc/iomem
/sys/firmware/devicetree/base/
```

This prevents confusing:

```text
"the build tree contains my change"
```

with:

```text
"the device is running my change"
```

---

## 23. Normal Development Workflow

### Select OpenWrt version

```bash
./scripts/openwrt-version.sh --current
```

or:

```bash
./scripts/openwrt-version.sh v25.12.5
```

### Modify the project BSP

For kernel/DTS work, edit:

```text
msm89xx/
```

For kernel patches:

```text
msm89xx/patches/
```

For kernel configuration:

```text
msm89xx/config-6.12
```

For image generation:

```text
msm89xx/image/
```

### Prepare OpenWrt

```bash
./scripts/openwrt-prepare.sh --version v25.12.5 openwrt
```

### Build a board

```bash
./build.sh build ufi001b
```

---

## 24. Adding a New MSM8916 Board

Typical sequence:

```text
Add board DTS
      │
      ▼
Add board target definition
      │
      ▼
Add board-specific patch if needed
      │
      ▼
Store it in msm89xx/
      │
      ▼
Run openwrt-prepare.sh
      │
      ▼
Build with build.sh
      │
      ▼
Inspect kernel + DTB + image
      │
      ▼
Flash and test
```

For ramoops:

```text
Verify board RAM map first
      │
      ▼
Choose verified RAM region
      │
      ▼
Add board-specific ramoops node
```

Do not assume another MSM8916 board has the same memory layout.

---

## 25. Where Changes Belong

| Change | Repository location |
|---|---|
| MSM89xx target | `msm89xx/` |
| Linux kernel patch | `msm89xx/patches/` |
| Board DTS patch | `msm89xx/patches/` |
| Kernel configuration | `msm89xx/config-6.12` |
| Image generation | `msm89xx/image/` |
| Project package | `packages/` |
| Package patch | `packages/<pkg>/patches/` |
| OpenWrt source overlay | `openwrt-overlay/` |
| OpenWrt selected version | `devenv/.env` |
| Generated OpenWrt target | `openwrt/target/linux/msm89xx/` |
| Generated kernel source | `openwrt/build_dir/.../linux-6.12.94/` |

The last two are generated/build artifacts and should not normally be treated as the source of truth.

---

## 26. Current UFI001B Debugging State

The BSP now contains:

```text
802-arm64-dts-qcom-msm8916-label-reserved-memory.patch
806-arm64-dts-qcom-add-msm8916-generic-ufi001b.patch
```

Preparation was tested with:

```bash
./scripts/openwrt-prepare.sh --version v25.12.5 openwrt
```

and the resulting OpenWrt tree contained both patches:

```text
openwrt/target/linux/msm89xx/patches/810-...
openwrt/target/linux/msm89xx/patches/811-...
```

The generated files contained:

```text
reserved_memory: reserved-memory
```

and:

```text
ramoops@8db00000
record-size
console-size
pmsg-size
```

This confirms that the **BSP → preparation → OpenWrt target** portion of the pipeline is working.

The next stage is to run the complete:

```text
build.sh
  ↓
openwrt-prepare.sh
  ↓
OpenWrt kernel build
  ↓
DTB
  ↓
boot.img
```

pipeline and verify the resulting boot image and runtime pstore/DT state.

---

## 27. Simplified Mental Model

The whole project can be remembered as:

```text
                  PROJECT REPOSITORY
                         │
          ┌──────────────┼──────────────┐
          │              │              │
          ▼              ▼              ▼
      msm89xx/       packages/    openwrt-overlay/
          │
          │
          ▼
 openwrt-prepare.sh
          │
          ▼
     OPENWRT TREE
          │
          ▼
       build.sh
          │
          ▼
 OpenWrt build system
          │
     ┌────┼────┐
     │    │    │
     ▼    ▼    ▼
  Kernel DTB Rootfs
     │    │    │
     └────┼────┘
          ▼
   Image generation
          │
          ▼
    MSM8916 firmware
          │
          ▼
        DEVICE
```

### The most important boundary

```text
PROJECT BSP
    ↓
openwrt-prepare.sh
    ↓
OPENWRT SOURCE TREE
    ↓
OpenWrt build system
    ↓
FIRMWARE
```

Understanding this boundary is the key to maintaining the project and avoiding accidental loss of BSP changes.
