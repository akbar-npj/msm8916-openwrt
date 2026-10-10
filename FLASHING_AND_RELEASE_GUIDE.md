# Comprehensive Guide: Building, Flashing, and Releasing OpenWrt & Kmod Packages for MSM8916

This guide documents the complete end-to-end workflow used for **building target firmware**, **compiling extra kernel modules (kmods) & CUPS**, **deploying online APK repositories to GitHub Pages**, **uploading release binaries to GitHub Releases**, and **flashing/sysupgrading** Qualcomm Snapdragon 410 (MSM8916) 4G USB dongles.

---

## Release Notes

> The body of every GitHub release is generated from this file (`body_path: FLASHING_AND_RELEASE_GUIDE.md` in the CI workflow). The latest entry is at the top.

### v25.12.5-r4 (2026-10-10) — built from `main`

*A maintenance release on top of r3: adds failsafe mode for headless dongles.*

**Why this release** — The headline is **failsafe mode** for headless MSM8916 dongles (no screen, no serial console), plus the HiMI_OK guard extended to **UF02**. Every firmware fix from r3 is included.

**🆘 Failsafe mode (new)** — Hold the button during boot to enter failsafe (7 s window; the LED flashes red→green): the device brings up a USB network gadget (`ncm.usb0` + `acm.GS0`) at **192.168.10.1** with a `ttyGS0` console, so a bricked or misconfigured unit can be recovered without opening it. A longer (5 s) hold performs a **factory reset**. Verified live on HMU05; enabled on all boards, with the button polarity corrected to active-high/pull-down (uz801 falls back to a green LED).

**🛡️ Modem stability** — The HiMI_OK guard is now gated for **UF02** as well (the third confirmed board), and the guard's init ships as an rc.d symlink so it survives `sysupgrade`.

**🧪 Tested only on HMU05 — your testing is needed** — This build has been validated **only on the HMU05**. The failsafe mode was verified live on HMU05; the other boards (`ufi001b`, `uz801` v3, `uf02`, `mf800b`) compile from the same tree but have **not** been tested on this release. If you run one of them — or any HMU05 setup different from the default — please flash it and **report back**: which board, what you exercised (boot, Wi-Fi, Bluetooth, modem attach + data, failsafe), and the result. Positive confirmations are as useful as failures — please open an issue so we can fold findings into the next release.

**⬆️ Upgrading** — From r3: `sysupgrade` (keep config) is fine and preserves the overlay.

**⚠️ Known issues** — See r3 below (overlay module shadowing; `sysupgrade -n` wipes the modem firmware; STA+AP Wi-Fi crashes the WCNSS firmware).

---

### v25.12.5-r3 (2026-10-09) — built from `main` @ `e66d566`

*A clean rebuild of r3 after the CI clone fix.*

**Why this release** — A modem-stability and feature release on top of r2. The headline: the software modem-restart (SSR) lever now actually works (it was silently dead in r2), paired with AP-side fixes that eliminate the recurring ~900 s baseband watchdog crash on HMU05. It also folds in Bluetooth, WPA3, thermal/CPR, and a safer two-in-one flasher.

**🛡️ Modem stability (most important)**
- **SSR restart lever fixed (kernel patch 826).** In r2, writing to `/sys/kernel/debug/msm_subsys/modem` to restart a wedged modem did nothing — a stray duplicate `start` inflated the remoteproc power refcount so `rproc_shutdown` short-circuited. Now `atomic_set(&rproc->power, 1)` is forced before shutdown, so the restart really cycles the modem and the refcount self-heals.
- **bam-dmux re-attach on already-open (kernel patch 850).** After an SSR, the BAM-DMUX data channel re-attaches correctly even when already open, preventing a "Channel already open" / QMI-port-loss stall.
- **A2 power pin moved into the kernel driver (patches 848/849).** The A2 modem-power pin is now held by `qcom_bam_dmux` from `netdev_open` instead of a userspace script. This removes a class of `a2_power.c:1189` crashes and makes the pin reliable across suspend/resume and SSR. (The old `modem-a2-hold` userspace scripts are removed.)
- **HiMI_OK guard hardened (GitHub issue #5).** The root-cause fix for the ~900 s baseband deadline: it rewrites the `HiMI_OK` handshake after every modem boot, gated to HiMI baseband boards, and only writes when NV item 2500 reads all-zeros. DIAG rpmsg access is now serialized and the diag-bind-watch no longer rewrites the status file every 2 s.
- **Corrected A2-parity claim.** Earlier docs described Android parity as `a2_pin=0`; measured behaviour shows Android actually holds the A2 ON (`a2_pin=1`). HMU05 now ships `a2_pin=1` via device-tree, matching Android.

**🔵 Bluetooth (new)** — BlueZ userspace shipped in the image; `kmod-btqca` + `kmod-btqcomsmd` packaged. `bt-ssp-guard` fixes SSP pairing by gating on the NVM LMP-feature bit; bond persists across reboot (verified). `bt-spp` adds an SPP serial server (default-on) + on-demand client with a uci bridge.

**📶 Wi-Fi (new)** — **WPA3-SAE / 802.11w (MFP)** enabled on wcn36xx (firmware already supported PMF/IGTK; only the driver needed patching). **AP-only interface combinations** allow multiple SSIDs (up to 2 APs) on one radio — do NOT enable STA+AP concurrent mode (it crashes the WCNSS firmware). Wi-Fi thermal mitigation wired up (wcn36xx HAL thermal command + cooling device).

**🌡️ Thermal & power (new)** — **Mainline CPR** CPU-rail voltage scaling ported: the CPU rail now tracks frequency and matches Android's voltage (≈1.15 V → 1.0625 V at load), closing the last meaningful voltage gap. Top CPU OPP capped at 800 MHz; PMIC thermal mitigation added.

**🔧 Flashing & recovery** — **Two-in-one `flash.sh`** (Migrate + Update) with a validated safety backup of all radio/calibration partitions (`fsc fsg modemst1 modemst2 modem persist sec`) — the script aborts before writing if any backup is missing or short. Fixed two bugs that left the modem partition zeroed after a migrate flash (IMEI read as all-zeros → modem attaches but stays detached). Radio/calibration restored *before* boot/rootfs. Wi-Fi fatals no longer miscounted as modem fatals. CI release fixed (the runner's `actions/cache` pre-creating `openwrt/` broke the clone) — this r3 is a clean rebuild from `main`.

**📦 Package feeds & kmods** — **Curated kmod feed**: a hand-picked set of ~109 `=m` kernel modules (USB/network, VPN/IPsec, crypto, filesystems, BT transports) is now compiled into the published feed / offline kmods bundle, so you can `apk add` them without rebuilding. **APK feed signing-key pinning + self-heal**: the stable `public-key.pem` is embedded and `apk-key-refresh` self-heals a stale key at boot/firstboot. `coreutils-stty` and `xxd` ship on every board. Removed the non-working `reboot-fastboot` alias (only `reboot-bootloader` works on this platform).

**📟 Supported devices** — `hmu05`, `ufi001b`, `uz801` (v3), `uf02`, and `mf800b`.

**⬆️ Upgrading** — From r2 (or any OpenWrt): `sysupgrade` (keep config) is fine and preserves the overlay. The old userspace `modem-a2-hold` is gone — the driver handles the A2 pin now; no action needed. After upgrading, verify the loaded `qcom_bam_dmux.ko` is the new one.

**⚠️ Known issues / caveats** — Overlay shadowing: a stale `/overlay/upper/lib/modules/*.ko` survives `sysupgrade` and shadows the new image module (hash the *loaded* file, not `/rom`). `sysupgrade -n` wipes the modem firmware (re-dumped on first boot); keep config unless you intend a clean slate. STA+AP Wi-Fi mode crashes the WCNSS firmware.

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
# Build all boards in diffconfigs/ (hmu05, ufi001b, uz801, uf02, mf800b)
./build.sh build all

# Or build specific boards individually:
./build.sh build hmu05
./build.sh build ufi001b
./build.sh build uz801
./build.sh build uf02
./build.sh build mf800b

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
  openwrt-msm89xx-msm8916-*-squashfs-gpt_both0.bin \
  openwrt-msm89xx-msm8916-*-flash.sh \
  kmods-msm8916-25.12.5.tar.gz \
  sha256sums \
  --clobber
```

> **Every release MUST include `*-squashfs-gpt_both0.bin` and `*-flash.sh` for each board.**
> They are the two artifacts required to flash a device **from stock Android** (Scenario A).
> Without them the release is unusable for first-time migration.

---

## 6. Flashing Firmware to Device

The generated `openwrt-msm89xx-msm8916-<board>-flash.sh` is **two-in-one**: it offers an interactive menu with **Migrate** (stock Android → OpenWrt, Scenario A) and **Update** (existing OpenWrt → OpenWrt, Scenario B, Option 1). Run `./openwrt-msm89xx-msm8916-<board>-flash.sh --help` for the non-interactive flags.

### Scenario A: Migrating from Stock Android to OpenWrt (Mandatory First-Time Flash Script)

> [!CAUTION]
> **Do NOT directly flash individual `boot` and `rootfs` partitions when migrating from stock Android.**
> Stock Android devices have a completely different partition table (GPT) layout, different bootloader/firmware partitions, and critical radio/calibration data (`fsc`, `fsg`, `modemst1`, `modemst2`, `modem`, `persist`, `sec`) that must be preserved. Directly flashing OpenWrt partitions over stock Android will cause bootloops, soft bricks, or permanent loss of IMEI, MAC addresses, and RF calibration.

To migrate from stock Android to OpenWrt safely, **you MUST use the automated flash script** generated during compilation in `openwrt/bin/targets/msm89xx/msm8916/`:

- `openwrt-msm89xx-msm8916-<board>-flash.sh` — the flasher (mandatory)
- `openwrt-msm89xx-msm8916-<board>-squashfs-gpt_both0.bin` — the OpenWrt GPT partition table
- `openwrt-msm89xx-msm8916-<board>-squashfs-boot.img` — the kernel/boot image
- `openwrt-msm89xx-msm8916-<board>-squashfs-system.img` — the rootfs image
- `openwrt-msm89xx-msm8916-<board>-firmware.zip` — `aboot/hyp/rpm/sbl1/tz` blobs flashed by the script

All five are published as GitHub Release assets for every board (see §5). Put them in the
same directory and run the script from there.

#### What the Script Automatically Handles:
1. **Safety Backup**: Backs up all critical device-unique radio/calibration partitions (`fsc`, `fsg`, `modemst1`, `modemst2`, `modem`, `persist`, `sec`) into a local `saved/` directory. Every read is validated; if any backup fails (error, or an empty/short file) the script **aborts before writing anything**, leaving the device untouched.
2. **GPT Repartitioning**: Flashes the OpenWrt partition table (`*-squashfs-gpt_both0.bin`) via raw sector writes (`primary.bin`, `backup_entries.bin`, `backup_header.bin`) to repartition the eMMC safely.
3. **Firmware Extraction & Flashing**: Extracts `aboot.mbn`, `hyp.mbn`, `rpm.mbn`, `sbl1.mbn`, and `tz.mbn` from the board's `*-firmware.zip` and flashes them to the newly repartitioned layout.
4. **OpenWrt Installation**: Flashes the OpenWrt kernel/boot image (`*-squashfs-boot.img`), the rootfs system image (`*-squashfs-system.img`), and wipes `rootfs_data` (fast 2 MiB superblock wipe, reformatted on next boot).
5. **Partition Restoration**: Restores all previously backed-up calibration and radio partitions back to the device.
6. **Automatic Reboot**: Reboots the device straight into OpenWrt (`edl reset`).

#### Step-by-Step Migration Instructions:

1. **Enter Qualcomm Emergency Download (EDL) Mode (9008)**:
   - **Method 1 (Hardware Test Points)**: Short the board's hardware EDL test pad / button to ground while inserting the USB dongle into your computer.
   - **Method 2 (via ADB on Stock Android)**: If ADB is enabled in stock firmware, run:
     ```bash
     adb reboot edl
     ```
2. **Verify EDL Connection**:
   Ensure the host recognizes the Qualcomm 9008 USB device:
   ```bash
   lsusb | grep 05c6:9008
   ```
   *Expected output: `Bus XXX Device YYY: ID 05c6:9008 Qualcomm, Inc. Gobi Wireless Modem (QDL mode)`*

3. **Run the Automated Flash Script**:
   Navigate to the build target directory and execute the script corresponding to your target board:
   ```bash
   cd openwrt/bin/targets/msm89xx/msm8916

   # Make script executable
   chmod +x openwrt-msm89xx-msm8916-<board>-flash.sh

   # Run the flasher (two-in-one: migrate or update)
   ./openwrt-msm89xx-msm8916-<board>-flash.sh
   ```
   *(Replace `<board>` with your target device board name: `generic-hmu05`, `generic-ufi001b`, `yiming-uz801v3`, or `generic-uf02`)*

   The script shows an interactive menu. For a first-time migration choose **`1` (Migrate from stock Android to OpenWrt)**:
   ```
   === OpenWrt MSM8916 EDL Flash Script ===

     1) Migrate from stock Android to OpenWrt
        (DESTRUCTIVE: repartitions the eMMC, flashes the bootloader,
         wipes Android)
     2) Update an existing OpenWrt install
        (flashes boot + rootfs only; partition table left untouched)

   Choice [1/2]:
   ```
   *(For unattended use, `--mode migrate --yes` selects the same path.)*

4. **Confirm the Prompts**:
   The script will verify the required `.img` and `.zip` files, print a loud **destructive-action warning** (type `yes` to proceed), execute the backup, flash all partitions, restore the radio data, and reset the device into OpenWrt.

---

### Scenario B: Flashing / Upgrading a Device Already Running OpenWrt

If your device is **already running OpenWrt**, the OpenWrt GPT layout and bootloader/firmware partitions are already configured. You can use direct EDL flashing or standard Sysupgrade:

#### Option 1: Fast Direct Flash via Qualcomm EDL (9008)
When the device is already partitioned for OpenWrt, the **same two-in-one `*-flash.sh`** handles the update: it flashes only `boot` + `rootfs` and leaves the partition table, bootloader and radio/calibration partitions untouched.

1. Put the device into EDL mode:
   - Run `reboot-edl` from SSH terminal on the device, **OR**
   - Short the board's EDL test pad while plugging into USB.
2. Verify EDL mode:
   ```bash
   lsusb | grep 05c6:9008
   ```
3. Run the flasher and choose **`2` (Update an existing OpenWrt install)**:
   ```bash
   cd openwrt/bin/targets/msm89xx/msm8916
   chmod +x openwrt-msm89xx-msm8916-<board>-flash.sh
   ./openwrt-msm89xx-msm8916-<board>-flash.sh
   ```
   It first confirms the device already has an OpenWrt GPT (`edl printgpt` must list `boot` + `rootfs`); if not, it refuses and points you at Migrate — update mode never writes a partition table.

   It then asks whether to keep or wipe the config overlay:
   ```
   Config overlay (rootfs_data):
     y) KEEP  - preserve /etc, packages and the dumped modem/Wi-Fi firmware
     n) ERASE - wipe the overlay for a factory-clean first boot
                (fast: 2 MiB superblock wipe, reformatted on boot;
                 modem firmware is re-dumped automatically)
   Keep configuration? [Y/n]:
   ```
   - **KEEP** is the default and matches a normal `sysupgrade` (config, packages and `/lib/firmware` are preserved).
   - **ERASE** gives a factory-clean first boot. It is fast: instead of erasing the whole multi-GiB overlay it wipes just the primary ext4 superblock (2 MiB); the boot hook `79-check-rootfs-data` reformats it on the next boot and the modem/Wi-Fi firmware is re-dumped automatically. (The wipe is a raw zero-write — see the manual alternative below for why `edl e`/`edl ep` cannot be used.)

   *(Unattended: `--mode update --keep-config --yes` or `--mode update --erase-config --yes`.)*

4. The script flashes `boot` + `rootfs` (and wipes the overlay if chosen) and reboots with `edl reset`.

> **Prefer `sysupgrade` for routine upgrades** (Option 2/3 below): it is safer and preserves state. Reserve the EDL Update path for recovery or an unbootable device. A KEEP update leaves the entire overlay in place, so stale overlay files can shadow the new rootfs (a known `/lib/modules/*.ko` shadowing incident) — ERASE avoids that.

**Manual alternative** (equivalent to update mode with KEEP):
```bash
edl w boot  openwrt/bin/targets/msm89xx/msm8916/openwrt-msm89xx-msm8916-<board>-squashfs-boot.img
edl w rootfs openwrt/bin/targets/msm89xx/msm8916/openwrt-msm89xx-msm8916-<board>-squashfs-system.img
# optional: force a factory-clean overlay on next boot.
# NOTE: `edl e`/`edl ep` do NOT work with this device's firehose loader
# (target replies "No storage drive number", nothing is written, yet edl
# still exits 0) — wipe the superblock with a raw write instead:
START=$(( $(edl printgpt | sed -n 's/^rootfs_data:.*Offset 0x\([0-9a-f]*\),.*/\1/p') / 512 ))
dd if=/dev/zero of=/tmp/zeros.bin bs=512 count=4096
edl ws "$START" /tmp/zeros.bin
edl reset
```

#### Option 2: Non-Destructive Sysupgrade (SSH Command Line)
Upgrade live over Wi-Fi or USB ethernet while preserving configurations and network settings:

```bash
# Transfer sysupgrade image to device
scp openwrt/bin/targets/msm89xx/msm8916/openwrt-msm89xx-msm8916-<board>-squashfs-sysupgrade.bin root@192.168.8.1:/tmp/sysupgrade.bin

# Perform sysupgrade
ssh root@192.168.8.1 "sysupgrade -v /tmp/sysupgrade.bin"
```

#### Option 3: Non-Destructive Sysupgrade (LuCI Web GUI)
1. Open [http://192.168.8.1](http://192.168.8.1) in your browser.
2. Navigate to **System -> Backup / Flash Firmware -> Flash image...**.
3. Upload `openwrt-msm89xx-msm8916-<board>-squashfs-sysupgrade.bin` and click **Continue**.
4. Review the verification screen and click **Flash**. The device will write the new firmware and reboot cleanly.

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
