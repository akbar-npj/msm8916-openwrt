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
* **🌐 4G LTE Cellular Data & Carrier Auto-Provisioning**: Native **ModemManager** integration with automatic SIM carrier detection (`qcom-carrier-autocfg`), dynamic APN and Qualcomm Carrier MBN deployment, safe empty PLMN home operator attachment, and continuous self-healing daemon monitoring (`modem-led-monitor`).
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

## ⚡ Flashing Firmware to Device

### 1. Putting the Device into Qualcomm EDL Mode (`05c6:9008`)

Put the USB modem into **Qualcomm Emergency Download (EDL) Mode** using any of the following methods:
* Short the hardware **EDL test points** while plugging the stick into a USB port.
* From Android shell (where ADB is available): `adb reboot edl`
* From OpenWrt shell: `reboot-edl`

Verify that the host detects the device in Qualcomm EDL mode:

```bash
lsusb | grep 05c6:9008
# Expected: 05c6:9008 Qualcomm HS-USB QDLoader 9008
```

---

### Scenario A: Migrating from Stock Android to OpenWrt (Mandatory First-Time Flash Script)

> [!CAUTION]
> **Do NOT directly flash individual boot and rootfs partitions when migrating from stock Android.**
> Stock Android devices have a completely different partition table (GPT) layout, different bootloader/firmware partitions, and critical radio/calibration data (`fsc`, `fsg`, `modemst1`, `modemst2`, `modem`, `persist`, `sec`) that must be preserved. Directly flashing OpenWrt partitions over stock Android will cause bootloops, soft bricks, or permanent loss of IMEI, MAC addresses, and RF calibration.

To migrate from stock Android to OpenWrt safely, you **MUST** use the automated flash script generated during compilation in `openwrt/bin/targets/msm89xx/msm8916/`:

```bash
cd openwrt/bin/targets/msm89xx/msm8916/
chmod +x openwrt-msm89xx-msm8916-<board>-flash.sh
./openwrt-msm89xx-msm8916-<board>-flash.sh
```

#### What the Script Automatically Handles:

* **Safety Backup**: Backs up all critical device-unique radio/calibration partitions (`fsc`, `fsg`, `modemst1`, `modemst2`, `modem`, `persist`, `sec`) into a local `saved/` directory.
* **GPT Repartitioning**: Flashes the OpenWrt partition table (`*-squashfs-gpt_both0.bin`) via raw sector writes (`primary.bin`, `backup_entries.bin`, `backup_header.bin`) to repartition the eMMC safely.
* **Firmware Extraction & Flashing**: Extracts `aboot.mbn`, `hyp.mbn`, `rpm.mbn`, `sbl1.mbn`, and `tz.mbn` from the board's `*-firmware.zip` and flashes them to the newly repartitioned layout.
* **OpenWrt Installation**: Flashes the OpenWrt kernel/boot image (`*-squashfs-boot.img`), the rootfs system image (`*-squashfs-system.img`), and safely erases `rootfs_data`.
* **Partition Restoration**: Restores all previously backed-up calibration and radio partitions back to the device.
* **Automatic Reboot**: Reboots the device straight into OpenWrt (`edl reset`).

---

### Scenario B: Updating or Re-Flashing an Existing OpenWrt Device

If your device is already running OpenWrt and has already been repartitioned to the OpenWrt GPT layout:

* **Recommended (Sysupgrade)**: Use the standard sysupgrade path to preserve configuration (see [Sysupgrade](#-sysupgrade)).
* **Clean Re-flash via EDL**: If you need a clean re-flash via EDL without modifying the existing OpenWrt partition table:

```bash
# Flash kernel boot and rootfs partitions
edl w boot openwrt/bin/targets/msm89xx/msm8916/openwrt-msm89xx-msm8916-<board>-squashfs-boot.img
edl w rootfs openwrt/bin/targets/msm89xx/msm8916/openwrt-msm89xx-msm8916-<board>-squashfs-system.img

# Optional: erase persistent overlay to start completely clean
edl e rootfs_data

# Reboot the device
edl reset
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
| **Connectivity Watchdog**| LuCI: **Services $\to$ Watchcat**| Configurable auto-reboot watchdog|
| **SMS Management**       | LuCI: **Services $\to$ SMS**    | View / Send SMS via Web UI       |
| **SSH Terminal**         | `ssh root@192.168.8.1`          | No password required             |
| **USB Serial Console**   | `screen /dev/ttyACM0 115200`    | Direct root shell                |
| **Wi-Fi Access Point**   | SSID: `OpenWrt` (2.4 GHz, Ch 1) | Open (No encryption by default)  |
| **EDL Recovery**         | `reboot-edl`                    | Qualcomm USB `05c6:9008`         |
| **Fastboot Recovery**    | `reboot-fastboot`               | `fastboot devices`               |

---

## 📶 SIM Detection, Carrier Auto-Provisioning & Reboot Behavior

When you plug in the modem stick with a SIM card inserted (or after swapping to a different cellular carrier), the stick will **automatically reboot once** after approximately 10–15 seconds of uptime.

> [!NOTE]
> **This one-time reboot is intentional, expected behavior—not a crash, panic, or bootloop.**

### Why Does the Stick Reboot?

1. **Qualcomm Carrier MBN (`mcfg_sw.mbn`) Architecture**:
   Qualcomm Snapdragon 410 (MSM8916) modem baseband firmware runs a universal cellular binary (`MPSS.DPM.1.0`). Network-specific parameters—such as LTE Radio Resource Control (RRC) band priority matrices, Discontinuous Reception (DRX) paging timers, IMS/VoLTE profiles, and Evolved Packet Core (EPC) attach parameters—are packaged into signed Qualcomm **Carrier MBN files** (`mcfg_sw.mbn`).
2. **Boot-Time Modem Firmware Initialization**:
   The Qualcomm Hexagon QDSP6 v5 modem processor (`remoteproc0`) reads and loads `/lib/firmware/MCFG_SW.MBN` into baseband memory only during its low-level bootloader initialization phase. Mainline Linux kernel `remoteproc` does not support hot-reloading carrier MBN profiles into the running Hexagon DSP without restarting the subsystem.
3. **Automated Provisioning (`qcom-carrier-autocfg`)**:
   Upon detecting the SIM card's IMSI and MCC-MNC operator code via ModemManager, the background `carrier-autocfg` daemon matches the carrier profile against its APN and MBN database:
   * If the currently deployed `/lib/firmware/MCFG_SW.MBN` does not match the optimal MBN profile for the detected carrier (e.g., on clean first boot or when switching between carriers such as Reliance Jio, Airtel, or ROW default), the daemon installs the matching `mcfg_sw.mbn` into `/lib/firmware/MCFG_SW.MBN`.
   * It then safely syncs filesystems to eMMC and triggers an **automatic, one-time system reboot** (with a 3-second grace countdown) to allow the Hexagon DSP to initialize with the new carrier baseband configuration.

### What Happens After the Reboot (Steady State)?

* **No Further Reboots**: On the subsequent boot, `carrier-autocfg` inspects the SIM and compares the active `/lib/firmware/MCFG_SW.MBN` against the detected carrier profile. Because the file already matches (`cmp -s`), **no reboot occurs**.
* **Automatic Data Attachment**: The daemon automatically configures `/etc/config/network` with the carrier's APN and IP stack (IPv4/IPv6), verifies clock synchronization with the Qualcomm QMI Time Daemon (`qcom-time-daemon`), and commands ModemManager to connect the 4G LTE bearer. The blue WAN LED lights up to indicate active cellular internet.

### SIM Hot-Swapping Behavior

* **Same Carrier / Same MBN Family**: If you insert a different SIM that uses the same carrier profile (or compatible ROW profile), `carrier-autocfg` flushes the baseband radio cache and network bearer dynamically—restoring data connectivity **without rebooting**.
* **Different Carrier Family**: If you swap to a SIM that requires a different carrier MBN (e.g., swapping between Reliance Jio and Airtel/ROW), the device will perform a one-time reboot to reload the new baseband profile into the Hexagon DSP.

### Monitoring Auto-Provisioning in Real Time

You can observe carrier detection, profile matching, and MBN provisioning live via SSH or USB serial console (`/dev/ttyACM0`):

```bash
logread -f -e carrier-autocfg
```

**Example Log Output on Initial SIM Detection:**

```text
[carrier-autocfg] Started MSM8916 SIM Carrier Auto-Provisioning Engine
[carrier-autocfg] Matched carrier in global APN database for MCC-MNC 405861
[carrier-autocfg] Deploying Carrier MBN 'generic/apac/reliance/commerci/mcfg_sw.mbn' into /lib/firmware/MCFG_SW.MBN...
[carrier-autocfg] Carrier MBN radio firmware updated for 'Reliance Jio'. Scheduling automatic reboot in 3 seconds to initialize Hexagon DSP...
```

**Example Log Output After Reboot (Steady State):**

```text
[carrier-autocfg] Matched carrier in global APN database for MCC-MNC 405861
[carrier-autocfg] Active Carrier MBN already matches generic/apac/reliance/commerci/mcfg_sw.mbn.
[carrier-autocfg] Boot-time carrier provisioning completed successfully. No reboot required.
[carrier-autocfg] [QMI-TIME] Modem ATS_USER time sync verified before LTE attach.
[carrier-autocfg] Requesting ModemManager bearer connection for APN 'jionet' (ipv4v6)...
```

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
