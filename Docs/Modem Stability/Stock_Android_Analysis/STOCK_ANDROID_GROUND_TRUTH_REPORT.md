# Qualcomm MSM8916 Stock Android Ground-Truth Analysis Report

**Target Board:** Generic HMU05 / UFI (Snapdragon 410 / MSM8916)  
**Baseband Firmware:** `HIMI_U01_MODEM_V1.0` (`MPSS.DPM.1.0.C7`)  
**Android Version:** Android 4.4.4 KTU84P (Kernel 3.10.28)  
**Capture Date:** 2026-09-02  

---

## 1. Executive Summary

By instrumenting the rooted stock Android firmware on the physical HMU05 hardware, we captured the exact ground-truth configuration of:
1. **GPIO Pin Configuration & SIM Control**
2. **Partition Layout & Storage Nodes**
3. **BAM-DMUX & SPS DMA Configuration**
4. **Daemon Architecture (`rild`, `qmuxd`, `netmgrd`, `time_daemon`, `rmt_storage`)**
5. **Data Call Lifecycle (Active vs. Dormant)**

---

## 2. Hardware GPIO & Pinmux Mapping

From `/sys/kernel/debug/gpio` (Base 902 for `msm_tlmm_v4_gpio`):

| Signal Name | Kernel GPIO ID | TLMM Physical GPIO | Direction | Stock State | Purpose |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`esim1_en`** | `gpio-1021` | **GPIO 119** | `out` | **`HIGH` (255)** | **Primary SIM / eSIM 1 Power & Select** |
| **`sim_hotplug`**| `gpio-1016` | **GPIO 114** | `out` | **`LOW` (0)** | **SIM Hotplug Detect Line** |
| **`4g_1`** | `gpio-973` | **GPIO 71** | `out` | **`HIGH` (1)** | **4G LTE RF Power / LTE Status LED** |
| **`4g_type`** | `gpio-974` | **GPIO 72** | `out` | `LOW` (0) | 4G Network Mode Indicator |
| **`wifistatus`** | `gpio-975` | **GPIO 73** | `out` | **`HIGH` (1)** | Wi-Fi Power / WLAN Status LED |
| **`key_freset`** | `gpio-939` | **GPIO 37** | `in` | `LOW` (0) | Factory Reset Pushbutton |
| **`esim2_en`** | `gpio-916` | **GPIO 14** | `out` | `LOW` (0) | Secondary eSIM 2 Select |
| **`esim3_en`** | `gpio-914` | **GPIO 12** | `out` | `LOW` (0) | Tertiary eSIM 3 Select |
| **`USB_ID_GPIO`** | `gpio-1012`| **GPIO 110** | `in` | `HIGH` (1) | USB ID / OTG Detect |

> **Critical Discovery:** On OpenWrt DTS, GPIO 119 (`esim1_en`) must be asserted HIGH and GPIO 114 (`sim_hotplug`) LOW to ensure the physical SIM slot remains energized and detected by the baseband.

---

## 3. Stock Partition Table Layout

From `/proc/partitions` and `/dev/block/bootdevice/by-name/`:

* `mmcblk0p1`: **`modem`** (64 MB vfat, mounted at `/firmware`)
* `mmcblk0p13`: **`modemst1`** (1.5 MB raw, EFS2 NV Primary)
* `mmcblk0p14`: **`modemst2`** (1.5 MB raw, EFS2 NV Backup)
* `mmcblk0p16`: **`fsc`** (1 KB raw, EFS Cookie)
* `mmcblk0p20`: **`fsg`** (1.5 MB raw, Factory Golden EFS)
* `mmcblk0p24`: **`persist`** (32 MB ext4, mounted at `/persist`)
* `mmcblk0p22`: **`boot`** (16 MB raw, Kernel boot image)
* `mmcblk0p23`: **`system`** (800 MB ext4, Android root/system)
* `mmcblk0p28`: **`userdata`** (2.3 GB ext4, Data partition)

---

## 4. BAM-DMUX DMA Architecture

From `/d/bam_dmux/tbl` and `/d/bam_dmux/stats`:

```text
ch00 local open=Y remote open=Y
ch01 local open=Y remote open=Y
ch02 local open=Y remote open=Y
ch03 local open=Y remote open=Y
ch04 local open=Y remote open=Y
ch05 local open=Y remote open=Y
ch06 local open=Y remote open=Y
ch07 local open=Y remote open=Y
```

* **No Inactivity Autosuspend Flapping**: `runtime_status` is `unsupported`. Stock Android does NOT power-collapse BAM-DMUX every 1000 ms.
* **Persistent DMA Channels**: All 8 data channels (`ch00`–`ch07`) remain open and synchronized with Hexagon A2 DMA engine.
* **Dormancy vs. Suspension**: When traffic pauses, the modem informs the host via `UNSOL_DATA_CALL_LIST_CHANGED` with `active=2` (`DORMANT`), but the DMA ring buffers stay intact.

---

## 5. Daemons & Synchronization Flow

1. **`time_daemon` (`/system/bin/time_daemon`)**:
   * Reads RTC from `/dev/rtc0`.
   * Initializes modem base offset at boot via `genoff_boot_tod_init`.
   * Registers `tod_update_ind_cb` to receive asynchronous NITZ tower time indications from the modem.
   * Does **not** poll or spam `genoff_set` in a busy loop.
2. **`rmt_storage` (`/system/bin/rmt_storage`)**:
   * Runs in full read-write mode with `-P -s` targeting `/dev/block/bootdevice/by-name/` nodes.
   * Handles 900-second periodic EFS2 sync flushes without rejection.
3. **`qmuxd` & `netmgrd`**:
   * Mediate QMI messaging across SMD channels `DATA40_CNTL` and IPCRTR.
