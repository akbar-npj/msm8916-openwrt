# OpenWrt for Qualcomm Snapdragon 410 (MSM8916) 4G LTE USB Sticks & Modems

[![OpenWrt Version](https://img.shields.io/badge/OpenWrt-25.12.5-blue.svg)](https://openwrt.org/)
[![Kernel](https://img.shields.io/badge/Linux_Kernel-6.12-green.svg)](https://kernel.org/)
[![Architecture](https://img.shields.io/badge/Arch-aarch64-orange.svg)](https://en.wikipedia.org/wiki/AArch64)
[![License](https://img.shields.io/badge/License-GPL--2.0-lightgrey.svg)](LICENSE)

A production-ready, fully open-source OpenWrt port for Qualcomm Snapdragon 410 (MSM8916 / MSM8939) based 4G LTE USB modems, dongles, and pocket routers.

Features a modern **Linux 6.12 mainline kernel**, **ModemManager 1.24**, **Qualcomm WCN36xx Wi-Fi with WPA3-SAE**, **Bluetooth (BlueZ 5.83 + SPP)**, **USB ConfigFS CDC NCM/ACM**, **mainline CPR CPU-rail scaling**, **true persistent eMMC EXT4 overlay storage**, and working **reboot-to-EDL and reboot-to-Fastboot recovery paths**.

> 📚 **Docs:** [Wiki](https://github.com/akbar-npj/msm8916-openwrt/wiki) · [Build & Flashing Reference](https://github.com/akbar-npj/msm8916-openwrt/wiki/Build-and-Flashing-Reference) · [Release notes](RELEASE_NOTES.md)

---

## 🆕 Release Highlights

Recent additions on top of the base port:

* **🆘 Failsafe mode** — a single tap of the **Restart button** during the first 7 s of boot drops a headless dongle into a rescue shell over USB (`192.168.10.1` or `/dev/ttyACM0`); a **5-second hold** performs a factory reset.
* **🔵 Bluetooth** — the WCNSS BT core (SMD) with BlueZ 5.83, an SSP pairing-repair guard, and a Bluetooth **Serial Port Profile** server/client (a serial console or TCP bridge over Bluetooth).
* **🔐 WPA3-SAE Wi-Fi** — PMF/SAE support in the wcn36xx driver, plus **multi-SSID** (two APs on the one radio).
* **🌡️ Thermal & power** — the CPU rail is now managed by **mainline CPR** (matching Android's rail voltage), the top CPU OPP is capped at 800 MHz, and Wi-Fi + PMIC thermal mitigation is wired up on the HMU05.
* **🛡️ Modem stability** — the A2 power rail is held on in the driver (`a2_pin=1`), the modem NV `HiMI_OK` marker is rewritten after every boot, and a bearer watchdog performs pre-emptive SSR recovery.
* **🔑 Signed package feed with self-heal** — the OTA `apk` feed is signed with a pinned key, and every device re-syncs that key from a stable URL at boot.
* **📡 Carrier auto-provisioning** — automatic SIM/carrier MBN deployment with the QMI time daemon anchoring the modem clock before LTE attach.

---

## 🚀 Key Features

* **⚡ Plug-and-Play USB Networking**: High-speed **CDC NCM Ethernet** automatically bound to `br-lan` at `192.168.8.1/24` with a built-in DHCP server (avoids `192.168.1.x` subnet collisions with upstream home routers).
* **📟 Built-in USB Serial Console**: Instant root shell on `/dev/ttyACM0` (115200 baud) over USB via CDC ACM for zero-setup terminal access, debugging, and recovery.
* **📶 First-Boot Wi-Fi Auto-Start**: Automatically extracts Qualcomm WCNSS blobs, starts the remoteproc in-place, binds the physical radio path, and broadcasts an open `OpenWrt` 2.4 GHz AP (Channel 1, 2.412 GHz) on clean first boot.
* **🔐 WPA3-SAE and Multi-SSID**: The wcn36xx driver advertises WPA3-SAE (PMF/IGTK), and the radio can host **two AP interfaces** at once. See [Wi-Fi](#-wi-fi).
* **🔵 Bluetooth + Serial Port Profile**: WCNSS Bluetooth over SMD with BlueZ 5.83, an SSP pairing-repair guard, and an SPP server/client for a Bluetooth serial console or TCP bridge. See [Bluetooth](#-bluetooth).
* **🌡️ Thermal & Power Management**: Mainline **CPR** CPU-rail scaling (Android-parity voltage), an 800 MHz CPU OPP cap, and Wi-Fi/PMIC thermal mitigation on the HMU05. See [Thermal & Power Management](#-thermal--power-management).
* **🛡️ Modem Stability Guard**: The A2 power rail is pinned on (`a2_pin=1`), the modem's `HiMI_OK` NV marker is rewritten after every modem boot, and `modem-bearer-watchdog` performs pre-emptive SSR recovery. See [Modem Stability](#-modem-stability).
* **🌐 4G LTE Cellular Data & Carrier Auto-Provisioning**: Native **ModemManager** integration with automatic SIM carrier detection (`qcom-carrier-autocfg`), dynamic APN and Qualcomm Carrier MBN deployment, safe empty PLMN home operator attachment, and continuous self-healing daemon monitoring (`modem-led-monitor`).
* **💾 Permanent eMMC Storage**: Automated `/dev/mmcblk0p15` (`rootfs_data`) EXT4 formatting and mounting, with preinit filesystem checking and automatic safe repair using `e2fsck -p`, providing persistent overlay storage without unnecessarily formatting an existing filesystem.
* **💡 Intuitive Hardware Status LEDs**:

  * 🟢 **Green LED** (`green:wlan`): Wi-Fi AP state and wireless client transmission.
  * 🔵 **Blue LED** (`blue:wan`): 4G LTE registration, data bearer, and internet activity.
  * 🔴 **Red LED** (`red:power`): Modem processor and subsystem health indicator.
* **🔄 Bulletproof Sysupgrade**: Graceful pre-upgrade service teardown (`platform_pre_upgrade`) eliminates kernel linked-list panics during LuCI web and CLI firmware upgrades, backed by step-by-step diagnostic logging to stdout and `/dev/kmsg`.
* **🆘 Failsafe Mode & Factory Reset**: A single Restart-button tap during the first 7 s of boot starts OpenWrt's pre-init rescue shell over a self-contained USB gadget (NCM + ACM) at `192.168.10.1` (or `/dev/ttyACM0`), so a broken config is recoverable on a screen-less dongle without opening it; a **5-second hold** wipes the overlay. See [Recovery and Reboot Modes](#-recovery-and-reboot-modes).
* **🔑 Signed OTA Package Feed**: Pre-compiled `kmod-*` and application packages served over GitHub Pages, signed with a pinned key and self-healing on-device. See [Package Repository](#-official-package--kernel-driver-repository).
* **🚑 Reboot to Qualcomm EDL**: `reboot-edl` cleanly triggers Qualcomm Emergency Download (EDL / USB `05c6:9008`) mode without requiring hardware test-point access.
* **⚙️ Reboot to Bootloader/Fastboot**: `reboot-bootloader` switches the device into Qualcomm Fastboot mode for bootloader-level recovery and flashing.
* **🔧 Recovery Without Physical Access**: EDL and Fastboot reboot targets provide software-triggered recovery paths directly from a running OpenWrt system.

---

## 📟 Supported Devices

| Board Target  | Profile Name      | Device Model               | SoC     | RAM    | Storage   | LED mapping             |
| :------------ | :---------------- | :------------------------- | :------ | :----- | :-------- | :---------------------- |
| **`hmu05`**   | `generic-hmu05`   | Generic HMU05 (250605 V0S) | MSM8916 | 512 MB | 4 GB eMMC | Green / Blue / Red      |
| **`ufi001b`** | `generic-ufi001b` | Generic UFI001B 4G Stick   | MSM8916 | 512 MB | 4 GB eMMC | Green / Blue / Red      |
| **`uz801`**   | `yiming-uz801v3`  | YiMing UZ801 v3 Dongle     | MSM8916 | 512 MB | 4 GB eMMC | Swapped (WLAN/WAN)      |
| **`uf02`**    | `generic-uf02`    | Generic UF02 / UF2 Stick   | MSM8916 | 512 MB | 4 GB eMMC | Green / Blue / Red      |
| **`mf800b`**  | `generic-mf800b`  | Generic MF800B 4G MiFi     | MSM8916 | 512 MB | 4 GB eMMC | Bi-colour WLAN/WAN      |

### Per-board feature matrix

| Board     | Failsafe mode | Bluetooth | WPA3-SAE / Multi-SSID | CPU CPR + 800 MHz cap | Wi-Fi/PMIC cooling maps | `a2_pin` modem hold | `qcom-time-daemon` |
| :-------- | :-----------: | :-------: | :-------------------: | :-------------------: | :---------------------: | :-----------------: | :----------------: |
| `hmu05`   |      ✅       |    ✅     |          ✅           |          ✅           |           ✅            |         ✅          |         ✅         |
| `ufi001b` |      ✅       |    ✅     |          ✅           |          ✅           |           —             |         —           |         —          |
| `uz801`   |      ✅       |    ✅     |          ✅           |          ✅           |           —             |         —           |         —          |
| `uf02`    |      ✅       |    ✅     |          ✅           |          ✅           |           —             |         —           |         —          |
| `mf800b`  |      ✅       |    ✅     |          ✅           |          ✅           |           —             |         —           |         —          |

> The Wi-Fi/PMIC cooling maps and the `a2_pin` modem-rail hold are **HMU05-only** (device-tree gated). Everything else applies to every board. Failsafe mode is enabled on **every board** (LED colour varies by board); it has been **live-verified on the HMU05** only.

---

## 🔄 Recovery and Reboot Modes

OpenWrt provides both an in-band rescue shell (**failsafe mode**) and
software-triggered reboot paths for Qualcomm recovery modes.

### Failsafe mode (recover a broken config)

These dongles have no Ethernet and no serial header, so failsafe mode brings up
its own USB gadget. To enter it:

1. Power on (or reboot) the dongle.
2. **Press the Restart button once within the first 7 seconds.** A single tap is
   enough — no hold needed. The **LED fast-flashes** (~12 Hz; red where the board
   has one, else green, else any) when failsafe is active.
3. Reach the device at **`192.168.10.1`** (USB NIC) or **`/dev/ttyACM0`**
   (USB serial, 115200):

   ```bash
   ssh root@192.168.10.1
   screen /dev/ttyACM0 115200
   ```

Inside, run `mount_root` to mount the overlay, repair `/etc/config/*`, or
`firstboot && reboot` to reset to defaults. Full guide:
[`Docs/Failsafe/`](Docs/Failsafe/README.md).

> A **5-second hold** of the Restart button on a *running* system performs a
> **factory reset + reboot**; a short press is ignored.

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
reboot-bootloader
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

The script is **two-in-one** and shows an interactive menu — choose **`1` (Migrate from stock Android to OpenWrt)** for a first-time migration:

```
=== OpenWrt MSM8916 EDL Flash Script ===

  1) Migrate from stock Android to OpenWrt
     (DESTRUCTIVE: repartitions the eMMC, flashes the bootloader, wipes Android)
  2) Update an existing OpenWrt install
     (flashes boot + rootfs only; partition table left untouched)

Choice [1/2]:
```

*(For unattended use, `--mode migrate --yes` selects the same path; `./...flash.sh --help` lists all flags.)*

#### What the Script Automatically Handles:

* **Safety Backup**: Backs up all critical device-unique radio/calibration partitions (`fsc`, `fsg`, `modemst1`, `modemst2`, `modem`, `persist`, `sec`) into a local `saved/` directory. Every read is validated; if any backup fails (error, or an empty/short file) the script **aborts before writing anything**, leaving the device untouched.
* **GPT Repartitioning**: Flashes the OpenWrt partition table (`*-squashfs-gpt_both0.bin`) via raw sector writes (`primary.bin`, `backup_entries.bin`, `backup_header.bin`) to repartition the eMMC safely.
* **Firmware Extraction & Flashing**: Extracts `aboot.mbn`, `hyp.mbn`, `rpm.mbn`, `sbl1.mbn`, and `tz.mbn` from the board's `*-firmware.zip` and flashes them to the newly repartitioned layout.
* **OpenWrt Installation**: Flashes the OpenWrt kernel/boot image (`*-squashfs-boot.img`), the rootfs system image (`*-squashfs-system.img`), and wipes `rootfs_data` (fast 2 MiB superblock wipe, reformatted on next boot).
* **Partition Restoration**: Restores all previously backed-up calibration and radio partitions back to the device.
* **Automatic Reboot**: Reboots the device straight into OpenWrt (`edl reset`).

---

### Scenario B: Updating or Re-Flashing an Existing OpenWrt Device

If your device is already running OpenWrt and has already been repartitioned to the OpenWrt GPT layout:

* **Recommended (Sysupgrade)**: Use the standard sysupgrade path to preserve configuration (see [Sysupgrade](#-sysupgrade)).
* **Clean Re-flash via EDL**: Use the **same two-in-one `*-flash.sh`** and choose **`2` (Update an existing OpenWrt install)**. It flashes only `boot` + `rootfs` and never touches the partition table, bootloader or radio/calibration partitions:

```bash
cd openwrt/bin/targets/msm89xx/msm8916/
./openwrt-msm89xx-msm8916-<board>-flash.sh
```

  Update mode first confirms the device has an OpenWrt GPT (`edl printgpt` lists `boot` + `rootfs`), then asks whether to **KEEP** or **ERASE** the config overlay:

```
Config overlay (rootfs_data):
  y) KEEP  - preserve /etc, packages and the dumped modem/Wi-Fi firmware
  n) ERASE - wipe the overlay for a factory-clean first boot
             (fast: 2 MiB superblock wipe, reformatted on boot;
              modem firmware is re-dumped automatically)
Keep configuration? [Y/n]:
```

  * **KEEP** (default) matches a normal `sysupgrade`.
  * **ERASE** gives a factory-clean first boot via a fast 2 MiB superblock wipe.

  *(Unattended: `--mode update --keep-config --yes` or `--mode update --erase-config --yes`.)*

* **Manual EDL alternative** (equivalent to update mode with KEEP):

```bash
# Flash kernel boot and rootfs partitions
edl w boot openwrt/bin/targets/msm89xx/msm8916/openwrt-msm89xx-msm8916-<board>-squashfs-boot.img
edl w rootfs openwrt/bin/targets/msm89xx/msm8916/openwrt-msm89xx-msm8916-<board>-squashfs-system.img

# Optional: wipe the persistent overlay superblock to start clean on next boot
# (reformatted automatically on boot). NOTE: `edl e` / `edl ep` do NOT work with
# this device's firehose loader (they silently no-op), so wipe with a raw write:
START=$(( $(edl printgpt | sed -n 's/^rootfs_data:.*Offset 0x\([0-9a-f]*\),.*/\1/p') / 512 ))
dd if=/dev/zero of=/tmp/zeros.bin bs=512 count=4096
edl ws "$START" /tmp/zeros.bin

# Reboot the device
edl reset
```

---

## 🔧 Fastboot Recovery

If the device is already running OpenWrt and supports the software Fastboot reboot:

```bash
reboot-bootloader
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

## 📶 Wi-Fi

The WCN36xx 2.4 GHz radio comes up automatically on first boot and broadcasts an **open** `OpenWrt` AP on channel 1. It is driven by the mainline `wcn36xx` driver plus a small set of mac80211 backport patches carried in this tree.

* **WPA3-SAE (Personal)** is supported. Configure it from LuCI (*Network → Wireless → Edit → Wireless Security*) or UCI; a WPA3 client will negotiate SAE + PMF, and WPA2 clients continue to work unchanged.
* **Multi-SSID** is supported: up to **two AP interfaces** on the same radio (for example a main SSID and a guest SSID).

> [!WARNING]
> **Concurrent STA + AP is not supported.** The WCNSS firmware crashes if the radio is asked to run a station and an AP at the same time — even on the same channel — which reboots the access point and takes the modem down with it. The driver advertises an AP-only interface combination so `wpa_supplicant`/`hostapd` refuse the combination up front. Use a second radio or a wired uplink if you need to bridge a Wi-Fi client.

---

## 🔵 Bluetooth

The device's Bluetooth controller is the WCNSS core reached over the SMD transport (`kmod-btqcomsmd` + `kmod-btqca`), with **BlueZ 5.83** userspace (`bluetoothd`, `bluetoothctl`, `hcitool`, `btmgmt`, `rfcomm`, `sdptool`, `bt-agent`).

Bring up the controller and pair from the console:

```bash
bluetoothctl
[bluetooth]# power on
[bluetooth]# scan on
[bluetooth]# pair <BD_ADDR>
```

### SSP pairing guard (`bt-ssp-guard`)

The controller's Secure Simple Pairing capability is gated by a single LMP-feature bit stored in Bluetooth NVM tag 6. If that bit is ever cleared, BR/EDR pairing falls back to legacy PIN pairing and fails. `bt-ssp-guard` checks the bit at boot and repairs it if needed:

```bash
bt-ssp-guard status    # report the feature bit / SSP mode
bt-ssp-guard repair    # check and repair; add --wait to poll for hci0 first
```

In the normal case it is a silent no-op — it exists as insurance against a cleared feature bit.

### Serial Port Profile (`bt-spp`)

`bt-spp` provides a Bluetooth **SPP** endpoint, so a paired phone or PC can reach the device as a serial port:

* A **server** (enabled at boot) listens on RFCOMM channel 1 and waits for an incoming connection.
* A **client** is run on demand against a peer address.

Both use a configurable **bridge** — either a login shell on a pty (a serial console over Bluetooth) or a local TCP socket:

```bash
uci show bt-spp           # current configuration
uci set bt-spp.server.bridge='tcp'
uci set bt-spp.server.tcp_port='5000'
uci commit bt-spp
/etc/init.d/bt-spp restart
```

> **Note:** `bluetoothd` runs with `--compat` so the local SDP Unix socket exists (required by `sdptool`/`rfcomm`). A `br-connection-profile-unavailable` error means the *profile* is missing, not that authentication failed.

---

## 🌡️ Thermal & Power Management

These devices run hot in a small enclosure, and the modem's behaviour is sensitive to the power rails. This port brings the CPU rail and thermal policy in line with stock Android.

* **CPU rail (mainline CPR)** — the CPU voltage is managed by mainline **CPR** (Core Power Reduction) rather than a fixed rail, matching Android's voltage for the silicon's fused speed bin. This lowers idle and mid-load CPU voltage versus a static setting.
* **CPU OPP cap** — the top 998.4 MHz operating point is removed, so the CPUs run at up to **800 MHz** (matching Android's cap on these parts).
* **Wi-Fi + PMIC thermal mitigation (HMU05)** — the `wcn36xx` driver exposes a cooling device that throttles the Wi-Fi transmit path, and the PMIC's thermal zone is bound to the CPU cooling device. Trips are triggered from the CPU cluster sensor, mirroring Android's `thermal-engine` "wlan" action.

```bash
# Inspect the thermal zones and their cooling devices
for z in /sys/class/thermal/thermal_zone*; do
    echo "$z: $(cat $z/type) = $(cat $z/temp)"
done
```

The full investigation — the OpenWrt-vs-Android comparison, the root-cause
analysis, and the CPR port — is in **[`Docs/Thermal/`](Docs/Thermal/README.md)**.

---

## 🛡️ Modem Stability

The Qualcomm baseband on these sticks can hit a **~900 s deadline** after a warm modem restart, and it can wedge when the A2 (AP↔modem) power handshake fails under a data bearer. This port ships several **AP-side** mitigations that run automatically — no user action is required:

| Component | What it does |
| :--- | :--- |
| **`a2_pin` (HMU05)** | Holds the A2 power rail **on** in the `qcom_bam_dmux` driver so the modem is not power-collapsed mid-handshake. Enabled via device tree. |
| **`himi-ok-guard`** | Rewrites the modem NV item `HiMI_OK` after **every** modem boot (cold and each SSR), so the RF task's identity check passes and the ~900 s deadline never arms. Enabled on the HMU05, UZ801 v3 and UF02 boards; writes only when the item reads all-zero (factory data is never overwritten). |
| **`modem-bearer-watchdog`** | Watches the modem and data bearer; performs a pre-emptive subsystem restart and recovers a zombie bearer/ModemManager without a reboot. |
| **`qcom-time-daemon`** | Anchors the modem's time base (ATS) before LTE attach so the QMI time sync is valid. |

Verify the guard is active:

```bash
logread | grep -i himi-ok        # guard activity
ubus call network.interface.modem status   # data bearer state
```

> An **optional baseband binary patch** is also available for users who prefer it over the AP-side guard. The binary-patch code is deliberately **not shipped** in this firmware — see **[`Docs/Modem Stability/HMU05_900S_FATAL_FIX_USER_GUIDE.md`](Docs/Modem%20Stability/HMU05_900S_FATAL_FIX_USER_GUIDE.md)** ("Choose Your Fix") for the trade-offs and the manual procedure.

---

## 🔌 Default Device Access

| Service                  | Access Details                  | Default Credentials              |
| :----------------------- | :------------------------------ | :------------------------------- |
| **Web Interface (LuCI)** | `http://192.168.8.1`            | No password (set on first login) |
| **Modem Watchdog**       | Automatic (`modem-bearer-watchdog`) | Pre-emptive SSR + zombie recovery |
| **SMS Management**       | LuCI: **Services $\to$ SMS**    | View / Send SMS via Web UI       |
| **SSH Terminal**         | `ssh root@192.168.8.1`          | No password required             |
| **USB Serial Console**   | `screen /dev/ttyACM0 115200`    | Direct root shell                |
| **Wi-Fi Access Point**   | SSID: `OpenWrt` (2.4 GHz, Ch 1) | Open (WPA3-SAE configurable)     |
| **Bluetooth**            | `bluetoothctl` / `bt-spp`       | SPP server on RFCOMM channel 1   |
| **Failsafe Mode**        | Button tap ≤7 s → `ssh root@192.168.10.1` | Rescue shell (LED flash) |
| **Factory Reset**        | Hold Restart button 5 s         | Wipes overlay, then reboots      |
| **EDL Recovery**         | `reboot-edl`                    | Qualcomm USB `05c6:9008`         |
| **Fastboot Recovery**    | `reboot-bootloader`             | `fastboot devices`               |

---

## 📡 SIM Detection, Carrier Auto-Provisioning & Reboot Behavior

When you plug in the modem stick with a SIM card inserted (or after swapping to a different cellular carrier), the device **connects automatically — with no reboot**. Carrier detection, APN/network configuration, band selection, radio-cache flush, and LTE bearer bring-up are all performed live by the `qcom-carrier-autocfg` daemon.

> [!NOTE]
> **Earlier builds rebooted once after ~10–15 seconds to reload a carrier MBN. That behavior has been removed.** The device no longer uses `mcfg.mbn` / `MCFG_SW.MBN`, and no provisioning step reboots the system.

### Why Is There No Reboot Anymore?

1. **The AP no longer applies carrier MCFG.** The engine used to load and activate a per-carrier **Carrier MBN** (`mcfg_sw.mbn`) through the modem's PDC service (QMI), with a legacy fallback that copied the file over `/lib/firmware/MCFG_SW.MBN`. Both paths are gone.
2. **Activating an MCFG forces the modem to self-reset.** The firmware's own `mcfg_utils.c:186` descriptor logs `"MCFG:Modem Initiated Reset. This crash is expected!!!"`. For some carriers the activation never even commits (the config stays Inactive while the already-Active config keeps serving — HMU05/Jio: `ROW_Generic_3GPP`). The AP then re-attempted it on **every** boot, and each attempt is a modem restart → SSR → A2-handshake desync (`a2_power.c:1189`) → crash loop.
3. **The modem already serves the carrier on the Active MCFG config**, so the dynamic load bought nothing. It has therefore been disabled: `provision_carrier_mbn()` is now a log-only no-op (`return 1`).

### What `qcom-carrier-autocfg` Still Does (All Live, No Reboot)

* **SIM detection & matching**: reads the SIM's IMSI and MCC-MNC operator code via ModemManager, then matches the carrier against its APN database (see the custom-DB path below).
* **APN / network config**: writes the carrier's APN and IP stack (IPv4/IPv6) into `/etc/config/network`.
* **Band selection**: applies the carrier-appropriate band set via QMI.
* **Radio-cache flush**: refreshes baseband registration state through ModemManager QMI DMS (`set-power-state-low` / `-on`). Raw `AT+CFUN=0/1` is **disabled on purpose** — on pristine stock firmware it triggers a fatal baseband assertion (`lte_ml1_common_dump.c:213`).
* **Bearer bring-up**: verifies clock sync with the Qualcomm QMI Time Daemon (`qcom-time-daemon`) and commands ModemManager to connect the 4G LTE bearer. The blue WAN LED lights up to indicate active cellular internet.

### SIM Hot-Swapping Behavior

Inserting a different SIM is handled **live and without reboot** for every carrier: the daemon flushes the baseband radio cache and reconnects the network bearer with the new carrier's APN. The former "one-time reboot on carrier-family change" no longer exists.

### Custom SIM Carrier Installation

If your carrier is missing or mis-detected, add it to the user override database at **`/etc/qcom-carrier-autocfg/custom-apns.tsv`** — it takes priority over the built-in `/usr/share/qcom-carrier-autocfg/apns.tsv` and is **preserved across OpenWrt sysupgrades**. Entries are tab-separated:

```text
MCC_MNC<TAB>Operator_Name<TAB>APN<TAB>IP_Type<TAB>Mode<TAB>MBN_Path
```

For example, to add Reliance Jio (MCC-MNC `405861`):

```text
405861	Reliance Jio	jionet	ipv4v6	4g	generic/apac/reliance/commerci/mcfg_sw.mbn
```

> [!NOTE]
> The trailing `MBN_Path` column is **retained for reference only** — carrier MCFG is no longer applied (see above), so the value is logged but never loaded.

### Monitoring Auto-Provisioning in Real Time

You can observe carrier detection, profile matching, and bearer bring-up live via SSH or USB serial console (`/dev/ttyACM0`):

```bash
logread -f -e carrier-autocfg
```

**Example Log Output on Initial SIM Detection:**

```text
[carrier-autocfg] Started MSM8916 SIM Carrier Auto-Provisioning Engine
[carrier-autocfg] Matched carrier in global APN database for MCC-MNC 405861
[carrier-autocfg] Carrier MCFG dynamic loading is disabled; the modem uses the MCFG config that is already Active (requested: generic/apac/reliance/commerci/mcfg_sw.mbn).
[carrier-autocfg] Boot-time carrier provisioning completed successfully. No reboot required.
[carrier-autocfg] [QMI-TIME] Modem ATS_USER time sync verified before LTE attach.
[carrier-autocfg] Requesting ModemManager bearer connection for APN 'jionet' (ipv4v6)...
```

**Example Log Output on SIM Hot-Swap:**

```text
[carrier-autocfg] Matched carrier in global APN database for MCC-MNC 40445
[carrier-autocfg] HOT-SWAP: Live APN and baseband caches flushed.
[carrier-autocfg] Hot-swap handled live without reboot. Connection restored.
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

* **Landing Page**: https://akbar-npj.github.io/msm8916-openwrt/

The feed is **signed with a pinned key**. Every image ships the matching public key and a boot service (`apk-key-refresh`) that re-syncs it from a stable URL, so a device flashed with an older image can never be left rejecting the feed with `UNTRUSTED signature`.

### Enable Custom Feeds on Device

```bash
cat << 'EOF' > /etc/apk/repositories.d/customfeeds.list
https://akbar-npj.github.io/msm8916-openwrt/releases/25.12.5/targets/msm89xx/msm8916/packages/packages.adb
https://akbar-npj.github.io/msm8916-openwrt/releases/25.12.5/packages/aarch64_generic/base/packages.adb
https://akbar-npj.github.io/msm8916-openwrt/releases/25.12.5/packages/aarch64_generic/packages/packages.adb
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

The packages authored and vendored in this repo (DIAG tools, carrier autoconfig,
Bluetooth SPP, LuCI apps, …) are documented in
**[`Docs/Custom Packages/`](Docs/Custom%20Packages/README.md)**.

---

## 📜 License

This project is licensed under the **GNU General Public License v2.0 (GPL-2.0)**.

Qualcomm firmware dumper components are licensed under the BSD-3-Clause License.
