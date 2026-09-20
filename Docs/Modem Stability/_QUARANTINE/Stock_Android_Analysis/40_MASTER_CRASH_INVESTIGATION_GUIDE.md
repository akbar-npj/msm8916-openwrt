# MSM8916 Modem Crash Investigation Guide
## Android Stock Analysis for OpenWrt Port — Master Reference

**Device:** UFI-001C / Xinxun Brand & Similar MSM8916-Based Modem Sticks  
**Baseband Firmware:** `HIMI_U01_MODEM_V1.0` (`MPSS.DPM.1.0.C7`, Sep 09 2015)  
**Android Version:** Android 4.4.4 KTU84P (Kernel 3.10.28)  
**OpenWrt Target:** 25.12.5 (Kernel 6.12.94 `msm89xx`)  
**Ghidra Install:** `/home/shaanair/Projects/Ghidra/ghidra_12.1.2_PUBLIC/support/`  
**Artifact Path:** `Docs/Modem Stability/Stock_Android_Analysis/`  
**Last Updated:** 2026-09-02

---

> **INVESTIGATION STATUS: COMPLETE**  
> The 4 root causes of the 15-minute crash have been forensically identified via live Android instrumentation (5h 28m 59s continuous uptime confirmed). This document consolidates all findings from files `01` through `39` into a single authoritative reference.

---

## Table of Contents

1. [Root Cause Summary (READ THIS FIRST)](#1-root-cause-summary)
2. [Temporary Root Access Procedure](#2-temporary-root-access-procedure)
3. [Hardware Ground Truth](#3-hardware-ground-truth)
4. [Watchdog Subsystem Analysis](#4-watchdog-subsystem-analysis)
5. [Modem Process Architecture](#5-modem-process-architecture)
6. [QMI Communication Protocol](#6-qmi-communication-protocol)
7. [Power Management & BAM-DMUX](#7-power-management--bam-dmux)
8. [EFS2 Remote Storage (RMTFS)](#8-efs2-remote-storage-rmtfs)
9. [Time Daemon & Clock Drift — THE CRASH CAUSE](#9-time-daemon--clock-drift)
10. [GPIO Pin Configuration](#10-gpio-pin-configuration)
11. [Memory Region Allocation](#11-memory-region-allocation)
12. [Firmware Loading & Patching](#12-firmware-loading--patching)
13. [USB Gadget Configuration](#13-usb-gadget-configuration)
14. [Init Services & Startup Sequence](#14-init-services--startup-sequence)
15. [Kernel Configuration & Modules](#15-kernel-configuration--modules)
16. [Android vs OpenWrt Delta Matrix](#16-android-vs-openwrt-delta-matrix)
17. [OpenWrt Implementation Checklist](#17-openwrt-implementation-checklist)
18. [Ghidra Reverse Engineering Guide](#18-ghidra-reverse-engineering-guide)
19. [ADB Investigation Commands](#19-adb-investigation-commands)
20. [File Index](#20-file-index)

---

## 1. Root Cause Summary

> **CRITICAL:** The 15-minute crash is NOT caused by a Linux hardware watchdog. It is caused by 4 concurrent software/configuration failures. All 4 must be fixed simultaneously.

The modem binary `MPSS.DPM.1.0.C7` runs an internal 900-second (15-minute) DRX sleep maintenance timer. When it fires, it expects the host AP to have provided certain services. Android provides them; vanilla OpenWrt does not.

```
╔══════════════════════════════════════════════════════════════════════════════════╗
║                   4 ROOT CAUSES OF THE 15-MINUTE CRASH                         ║
╠══╦═══════════════════════════╦══════════════════════╦══════════════════════════╣
║P ║ ROOT CAUSE                ║ ANDROID BEHAVIOR     ║ OPENWRT FAILURE MODE     ║
╠══╬═══════════════════════════╬══════════════════════╬══════════════════════════╣
║0 ║ SCLK Clock Drift          ║ time_daemon anchors  ║ No daemon → SCLK drifts  ║
║  ║ (lte_ml1_sleepmgr_stm)    ║ ATS to RTC at boot,  ║ → ERR_FATAL at t=900s    ║
║  ║                            ║ registers NITZ IND   ║ lte_ml1_sleepmgr_stm.c  ║
║  ║                            ║ callback (0x0025)    ║ :4054                    ║
╠══╬═══════════════════════════╬══════════════════════╬══════════════════════════╣
║0 ║ BAM-DMUX 1s Autosuspend   ║ Autosuspend marked   ║ qcom_bam_dmux.c hard-    ║
║  ║ Flapping                   ║ "unsupported"; all   ║ codes 1000ms delay →     ║
║  ║                            ║ 8 channels           ║ ~60-100 power collapses  ║
║  ║                            ║ persistently open    ║ → SMSM ACK timeout (-22) ║
╠══╬═══════════════════════════╬══════════════════════╬══════════════════════════╣
║0 ║ RMTFS NV Sync Rejection   ║ rmt_storage runs     ║ If rmtfs runs with -r    ║
║  ║ (900s EFS2 writeback)      ║ full R/W over        ║ (read-only), 900s NV     ║
║  ║                            ║ /dev/uio0 to         ║ sync is rejected →       ║
║  ║                            ║ modemst1/st2/fsg/fsc ║ modem panics :Excep :0:  ║
╠══╬═══════════════════════════╬══════════════════════╬══════════════════════════╣
║1 ║ GPIO SIM Power Miscfg     ║ GPIO 119 HIGH (255)  ║ DTS may leave pin        ║
║  ║ (Hardware)                 ║ GPIO 114 LOW  (0)    ║ floating or misconfigured║
║  ║                            ║ GPIO  71 HIGH (1)    ║ → SIM drops after dormcy ║
╚══╩═══════════════════════════╩══════════════════════╩══════════════════════════╝
```

**Fixes Applied / Available:**

| Fix | Implementation | Status |
|-----|---------------|--------|
| Firmware no-sleep patch | `MODEM_FIRMWARE_NO_SLEEP_PATCH_GUIDE.md` | ✅ Documented |
| BAM-DMUX PM lock | `msm89xx/patches/808-bam-dmux-stats.patch` | ✅ Patch exists |
| RMTFS R/W mode | `packages/rmtfs/files/rmtfs.init` with `-P -s` | ✅ Configured |
| GPIO DTS alignment | `msm8916-generic-hmu05.dts` GPIO 119/114/71 | ⚠️ Verify |

---

## 2. Temporary Root Access Procedure

> **WARNING:** Root is **temporary** — lost on every reboot. Must re-apply before every investigation session. ADB disconnects during the process; this is expected.

```bash
# Step 1: Connect
adb shell

# Step 2: Trigger root escalation
setprop service.adb.root 1
busybox killall adbd

# Step 3: Wait 5-10 seconds (ADB will disconnect — this is normal)
# Step 4: Reconnect
adb shell
# Prompt should now show: #  (root)

# Verify with:
id
# Expected: uid=0(root) gid=0(root)
```

> Works only on devices with debug firmware. The UFI-001C/Xinxun units have debug firmware enabled by default.

---

## 3. Hardware Ground Truth

### 3.1 Confirmed GPIO States (from `/sys/kernel/debug/gpio`)

Base: **902** for `msm_tlmm_v4_gpio`

| Signal Name | Kernel GPIO ID | TLMM GPIO | Direction | Stock State | Purpose |
|:---|:---|:---|:---|:---|:---|
| **`esim1_en`** | `gpio-1021` | **GPIO 119** | `out` | **`HIGH` (255)** | **Primary SIM Card Power — CRITICAL** |
| **`sim_hotplug`** | `gpio-1016` | **GPIO 114** | `out` | **`LOW` (0)** | **SIM Presence Detect — CRITICAL** |
| **`4g_1`** | `gpio-973` | **GPIO 71** | `out` | **`HIGH` (1)** | **4G RF Path Power** |
| `4g_type` | `gpio-974` | GPIO 72 | `out` | `LOW` (0) | 4G Network Mode Indicator |
| `wifistatus` | `gpio-975` | GPIO 73 | `out` | `HIGH` (1) | Wi-Fi Power / Status LED |
| `key_freset` | `gpio-939` | GPIO 37 | `in` | `LOW` (0) | Factory Reset Button |
| `esim2_en` | `gpio-916` | GPIO 14 | `out` | `LOW` (0) | Secondary SIM (Disabled) |
| `esim3_en` | `gpio-914` | GPIO 12 | `out` | `LOW` (0) | Tertiary SIM (Disabled) |
| `disp_rst_n` | `gpio-1019` | GPIO 117 | `in` | `HIGH` (1) | Display Reset |
| `USB_ID_GPIO` | `gpio-1012` | GPIO 110 | `in` | `HIGH` (1) | USB OTG ID Detect |

**Source files:** `02_gpio_dump.txt`, `30_gpio_full_pinmap.txt`, `06_init_gpio_config.txt`

### 3.2 Partition Layout

From `/proc/partitions` and `/dev/block/bootdevice/by-name/`:

| Partition Name | Block Device | Size | FS | Mount | Purpose |
|:---|:---|:---|:---|:---|:---|
| **`modem`** | `mmcblk0p1` | 64 MB | vfat | `/firmware` | Modem firmware blobs |
| **`modemst1`** | `mmcblk0p13` | 1.5 MB | raw | — | EFS2 NV Primary |
| **`modemst2`** | `mmcblk0p14` | 1.5 MB | raw | — | EFS2 NV Backup |
| `fsc` | `mmcblk0p16` | 1 KB | raw | — | EFS Cookie |
| `fsg` | `mmcblk0p20` | 1.5 MB | raw | — | Factory Golden EFS |
| `persist` | `mmcblk0p24` | 32 MB | ext4 | `/persist` | Persistent settings |
| `boot` | `mmcblk0p22` | 16 MB | raw | — | Android kernel image |
| `system` | `mmcblk0p23` | 800 MB | ext4 | `/system` | Android root |
| `userdata` | `mmcblk0p28` | 2.3 GB | ext4 | `/data` | User data |

**Source:** `09_partitions_and_mounts.txt`

### 3.3 AT Command Identity

- **AT Port:** `/dev/smd11`
- **Manufacturer:** `QUALCOMM INCORPORATED`
- **Model:** `4094`
- **Revision:** `HIMI_U01_MODEM_V1.0  1  [Sep 09 2015 10:00:00]`
- **IMEI:** `864293052253917`

```text
AT+CPIN? → +CPIN: READY
AT+COPS? → +COPS: 0,0,"JIO 4G Jio",7   (LTE E-UTRAN)
AT+CSQ   → +CSQ: 28,99               (~-57 dBm, excellent)
```

**Source:** `31_at_command_test.txt`, `32_at_identification.txt`

---

## 4. Watchdog Subsystem Analysis

> **NOTE:** The hardware watchdog is NOT the crash cause. It is managed entirely in-kernel and operates independently of the 15-minute modem crash.

### Confirmed Findings

| Parameter | Value |
|:---|:---|
| **Hardware WDT Driver** | `msm_watchdog` |
| **Sysfs Path** | `/sys/devices/soc.0/b017000.qcom,wdt` |
| **`/dev/watchdog` device** | **Does NOT exist** (no userspace feeder) |
| **Bark timeout** | 10 seconds |
| **Bite timeout** | 11 seconds |
| **Kernel log** | `[0.060760] msm_watchdog b017000.qcom,wdt: MSM Watchdog Initialized` |
| **Status** | `disable=0` (Active) |
| **Feeder** | In-kernel scheduler workqueue — no userspace process required |

### Watchdog-Adjacent Processes (not the WDT feeder)

- **`/sbin/healthd` (PID 187)** — monitors Android framework via Binder (`/dev/binder`). Does NOT feed `/dev/watchdog`.
- **`VosWDThread` (PID 1286)** — monitors WCNSS Wi-Fi subsystem. Not related to modem.

**ADB commands to verify:**
```bash
adb shell ls /dev/watchdog*          # Expected: nothing (no device node)
adb shell cat /sys/devices/soc.0/b017000.qcom,wdt/disable
adb shell dmesg | grep watchdog
```

**Source:** `28_watchdog_investigation.txt`, `33_RECOMMENDED_INVESTIGATION_SUMMARY.md`

---

## 5. Modem Process Architecture

### 5.1 Running Processes & PIDs (from live Android at t=19,739s uptime)

```text
rild        PID 197   16 threads  /system/bin/rild
qmuxd       PID 232   6 threads   /system/bin/qmuxd
netmgrd     PID 293               /system/bin/netmgrd
time_daemon PID 242               /system/bin/time_daemon
rmt_storage PID 246               /system/bin/rmt_storage
healthd     PID 187               /sbin/healthd
```

### 5.2 Process IPC Topology

| Process | Key FDs | IPC Target | Purpose |
|:---|:---|:---|:---|
| `time_daemon` | FD 13: `/dev/diag`; Sockets: `[8413]`, `[8414]`, `[8416]`, `[8430]`, `[8431]` | QMI Service 22 over IPC Router | Clock drift prevention |
| `qmuxd` | FD 8: `/sys/power/wake_lock`; FDs 15–22: `/dev/smdcntl0`–`/dev/smdcntl7` | SMD channels to Hexagon | QMI multiplexing |
| `netmgrd` | FD 3: `/dev/diag`; Sockets: `[6861]`–`[9018]` | QMI WDS (Service 1) | Data call & rmnet0 mgmt |
| `rmt_storage` | FD 5: `/dev/uio0` (DMA); modemst1, modemst2, fsg | eMMC raw partitions | EFS2 NV sync |

**Source:** `03_process_dump.txt`, `19_process_file_descriptors.txt`, `23_MODEM_COMMUNICATION_PROTOCOL_TRACE.md`

---

## 6. QMI Communication Protocol

### 6.1 Physical Transport Layer

| Channel | Device Node | Opened By | Content |
|:---|:---|:---|:---|
| SMD DATA40_CNTL | `/dev/smdcntl0`–`/dev/smdcntl7` | `qmuxd` | Raw QMI packets |
| IPC Router | `AF_QIPCRTR` socket | `time_daemon` | QMI Service 22 (ATS) |
| DIAG | `/dev/diag` | `time_daemon`, `netmgrd` | Diagnostic protocol |
| AT Commands | `/dev/smd11` | userspace | AT command interface |

### 6.2 QMI Packet Decomposition (Live Capture)

```text
Captured from qmuxd reading /dev/smdcntl0:
[pid 415] read(15, "\x01\x15\x00\x80\x03\x01\x04\xde\x02\x51\x00\x09\x00\x14\x06\x00...", 5086) = 22

Field breakdown:
  0x01        → QMUX start delimiter
  0x15 0x00   → Length: 21 bytes total
  0x80        → Control flags (Indication from Hexagon)
  0x03        → Service ID 0x03 = QMI NAS (Network Access Service)
  0x01        → Client handle ID 1
  0x04        → SDU header control flags
  0xde 0x02   → Transaction ID
  0x51 0x00   → Message ID 0x0051 = QMI_NAS_GET_SIG_INFO_RESP
  0x09 0x00   → TLV payload length 9
  <9 bytes>   → RSSI/RSRP/RSRQ signal strength TLV
```

### 6.3 Wake-Lock Protocol (Per QMI Transaction)

```text
Every qmuxd QMI transaction:
  1. write(8, "qmuxd_port_wl_0", 15)    → Acquire kernel wake-lock
  2. read(15, buffer)                    → Read QMI packet from /dev/smdcntl0
  3. sendto(socket, buffer)              → Forward to RIL daemon
  4. write(10, "qmuxd_port_wl_0", 15)   → Release kernel wake-lock

rild (QCRIL) additional wake-locks:
  write(20, "qcril", 5)            → Acquire "qcril" wake-lock
  write(20, "radio-interface", 15) → Acquire "radio-interface" wake-lock
```

**Source:** `21_strace_qmuxd.txt`, `35_rild_and_healthd_strace.txt`

---

## 7. Power Management & BAM-DMUX

### 7.1 Stock Android BAM-DMUX State (Confirmed at t=921s and t=19,739s)

```text
/d/bam_dmux/tbl:
  ch00  local open=Y  remote open=Y
  ch01  local open=Y  remote open=Y
  ch02  local open=Y  remote open=Y
  ch03  local open=Y  remote open=Y
  ch04  local open=Y  remote open=Y
  ch05  local open=Y  remote open=Y
  ch06  local open=Y  remote open=Y
  ch07  local open=Y  remote open=Y   ← All 8 data channels persistently open
  ch08–ch20: local open=N  (channels 8+ not used for data)

power/runtime_status = "unsupported"  ← Autosuspend NOT active in Android
```

### 7.2 The Vanilla Linux Failure Mode

```
Upstream qcom_bam_dmux.c:
  #define BAM_DMUX_AUTOSUSPEND_DELAY 1000   ← 1 second idle timeout

Over 15 minutes of LTE data:
  - ~60–100 forced power collapses via SMSM handshake
  - Each collapse: SMSM_A2_POWER_CNTL sequence + resume
  - Eventually: "Failed to resume: -22" (EBUSY timeout)
  - Result: crash at a2_task.c:3179
```

### 7.3 Data Dormancy (Correct vs. Crash)

When no packets flow, Android transitions to `DORMANT` (`active=2`) at protocol layer:
```text
UNSOL_DATA_CALL_LIST_CHANGED → active=2 (DORMANT)   ← Normal, NOT a crash
```
The underlying kernel DMA descriptors **remain intact**. This is CORRECT behavior.

### 7.4 Fix: BAM-DMUX PM Lock Kernel Patch

**File:** `msm89xx/patches/808-bam-dmux-stats.patch`

```c
// In bam_dmux_netdev_open():
+   ret = pm_runtime_resume_and_get(bndev->dmux->dev);  // Hold PM ref while wwan0 UP
+   if (ret < 0) return ret;

// In bam_dmux_netdev_stop():
+   pm_runtime_put(bndev->dmux->dev);  // Release PM ref when wwan0 DOWN
```

**Source:** `04_network_and_bam_dump.txt`, `15_debugfs_bam_smd_smsm.txt`, `16_15min_barrier_test_log.txt`

---

## 8. EFS2 Remote Storage (RMTFS)

### 8.1 Stock Android Implementation

- **Binary:** `/system/bin/rmt_storage`
- **Mode:** Full **read-write** access via `-P -s` flags
- **IPC:** `/dev/uio0` (shared memory DMA with Hexagon DSP)
- **Target Partitions:** modemst1, modemst2, fsg, fsc via `/dev/block/bootdevice/by-name/`
- **900s NV Sync:** Write completes in ~2ms

### 8.2 The Read-Only Failure Mode

```text
If rmtfs is run with -r (read-only):
  T=900s: modem issues EFS2 writeback request
  rmt_storage rejects the write (read-only mode)
  Modem receives error response
  Modem panics with:  Excep :0:  (EFS2 write failure exception)
  → Subsystem restart / crash
```

### 8.3 OpenWrt Fix

```bash
# packages/rmtfs/files/rmtfs.init — must use these flags:
rmtfs -P -s
# With partition symlinks at /dev/disk/by-partlabel/modemst1 etc.
# DO NOT use: -r  (read-only)
```

**Source:** `10_firmware_and_persist_contents.txt`

---

## 9. Time Daemon & Clock Drift

### 9.1 THE PRIMARY CRASH CAUSE — What Happens at t=900s

```
Baseband internal timer fires every 900 seconds:
  lte_ml1_sleepmgr_stm.c → maintenance timer expiry

The LTE Sleep Manager calculates:
  Drift = (actual_sclk_ticks - expected_sclk_ticks) × 0x7800

WITH Android (time_daemon running):
  → ATS timebase anchored at boot → drift ≈ 0 → timer exits cleanly

WITHOUT Android (no time_daemon):
  → ATS timebase never anchored → drift is large
  → Assertion at lte_ml1_sleepmgr_stm.c:4054
  → ERR_FATAL → Subsystem Restart → crash
```

### 9.2 time_daemon Behavior (Confirmed via strace, PID 242)

```text
Boot sequence:
  1. open("/dev/rtc0")                       → Access hardware RTC
  2. genoff_boot_tod_init()                  → Set modem ATS timebase = RTC time
  3. genoff_modem_qmi_init()                 → Connect to QMI Service 22
                                                (AF_QIPCRTR, Port 11, Service 22)
  4. QMI_TIME_GENOFF_SET_REQ (0x0020)        → One-time boot time sync
  5. QMI_TIME_REG_IND_REQ (0x0025)          → Register NITZ tower IND callback

Steady state (observed at t=19,739s):
  poll([{fd=14}, {fd=15}, {fd=16}], 3, -1)  → BLOCKS INDEFINITELY (0% CPU)
  
  When cell tower broadcasts SIB16 NITZ frame:
    → tod_update_ind_cb() is called
    → Modem ATS is updated asynchronously
    → Drift is corrected
    → Back to poll() → 0% CPU
```

**Key fact:** `time_daemon` is event-driven. It does NOT poll or spam. Zero CPU overhead at steady state.

### 9.3 OpenWrt Fix Option A — Firmware No-Sleep Patch (Recommended)

Patch `modem.b16` to make `lte_ml1_sleepmgr_cfg` return `-1` immediately.  
This prevents the 900s DRX timer from ever being scheduled.

**Binary:** `modem.b16` (ELF Segment 16)

```
Patch 1: lte_ml1_sleepmgr_cfg (FUN_c03987e0)
  Virtual Address: 0xc03987e0
  File Offset:     0x001117e0

  ORIGINAL bytes: 08 c0 9d a0  0c c0 9f a0
  ORIGINAL asm:   allocframe(#0x18)
                  r17:16 = combine(r1, r0)

  PATCHED bytes:  00 c4 00 78  00 c0 9f 52
  PATCHED asm:    r0 = #-1           ← Return -1 immediately
                  { jumpr r31 }      ← Return to caller

Patch 2: ERR_FATAL neutralization (0xc0879150)
  File Offset: 0x005f2150

  ORIGINAL bytes: 08 c0 9d a0
  PATCHED bytes:  00 c0 9f 52        ← jumpr r31 (return immediately)

SHA256 update required:
  modem.mdt  at offset 0x05bc  (32 bytes) ← Segment 16 hash
  modem.b01  at offset 0x0228  (32 bytes) ← Segment 16 hash
```

**Full guide:** `MODEM_FIRMWARE_NO_SLEEP_PATCH_GUIDE.md`

### 9.4 OpenWrt Fix Option B — QMI Service 22 Daemon

Implement a Linux daemon that:
1. Connects to `AF_QIPCRTR` socket, Service 22
2. Sends `QMI_TIME_GENOFF_SET_REQ` (message 0x0020) with current time
3. Registers `QMI_TIME_REG_IND_REQ` (message 0x0025) for NITZ callbacks
4. Sits in `poll()` forever handling callbacks

Reference implementation: reverse engineer `binaries/time_daemon` + `binaries/libtime_genoff.so` in Ghidra.

**Source:** `11_time_daemon_analysis.txt`, `20_strace_time_daemon.txt`, `MODEM_FIRMWARE_NO_SLEEP_PATCH_GUIDE.md`

---

## 10. GPIO Pin Configuration

### 10.1 Required States Before Modem Boot

| GPIO | Signal | Required State | Consequence if Wrong |
|:---|:---|:---|:---|
| **119** | `esim1_en` | **HIGH (255)** | SIM card unpowered → no SIM detection |
| **114** | `sim_hotplug` | **LOW (0)** | Modem treats SIM as absent → no registration |
| **71** | `4g_1` | **HIGH (1)** | 4G RF path unpowered → no LTE |
| 14 | `esim2_en` | LOW (0) | (Secondary SIM disabled) |
| 12 | `esim3_en` | LOW (0) | (Tertiary SIM disabled) |

### 10.2 OpenWrt DTS Configuration

```dts
/* In msm8916-generic-hmu05.dts */
regulators {
    /* GPIO 119: Primary SIM power — MUST be HIGH */
    esim1_en-hog {
        gpio-hog;
        gpios = <119 GPIO_ACTIVE_HIGH>;
        output-high;
        line-name = "esim1-en";
    };

    /* GPIO 114: SIM hotplug detect — MUST be LOW */
    sim_hotplug-hog {
        gpio-hog;
        gpios = <114 GPIO_ACTIVE_HIGH>;
        output-low;
        line-name = "sim-hotplug";
    };

    /* GPIO 71: 4G RF path power — MUST be HIGH */
    4g_rf-hog {
        gpio-hog;
        gpios = <71 GPIO_ACTIVE_HIGH>;
        output-high;
        line-name = "4g-rf-power";
    };
};
```

### 10.3 Alternative: firstboot Script

```bash
#!/bin/sh
# /etc/uci-defaults/99-msm89xx-firstboot

# GPIO 119: SIM power ON
echo 119 > /sys/class/gpio/export
echo out  > /sys/class/gpio/gpio119/direction
echo 1    > /sys/class/gpio/gpio119/value

# GPIO 114: SIM detect LOW
echo 114 > /sys/class/gpio/export
echo out  > /sys/class/gpio/gpio114/direction
echo 0    > /sys/class/gpio/gpio114/value

# GPIO 71: 4G RF power ON
echo 71  > /sys/class/gpio/export
echo out  > /sys/class/gpio/gpio71/direction
echo 1    > /sys/class/gpio/gpio71/value
```

**Source:** `02_gpio_dump.txt`, `06_init_gpio_config.txt`, `06_sim_and_gpio_scripts.txt`, `07_leds_and_sim_gpio_nodes.txt`, `30_gpio_full_pinmap.txt`

---

## 11. Memory Region Allocation

### 11.1 Confirmed Memory Map (from live dmesg)

| Region | Base Address | Size | End Address | Purpose |
|:---|:---|:---|:---|:---|
| **`modem_adsp_mem` (mpss_mem)** | **`0x86800000`** | **85 MiB (`0x5500000`)** | `0x8BD00000` | **Hexagon QDSP6 Modem Firmware** |
| `external_image_mem` | `0x86000000` | 8 MiB (`0x800000`) | `0x86800000` | MBA & TrustZone |
| `peripheral_mem` | `0x8BD00000` | 6 MiB (`0x600000`) | `0x8C300000` | WCNSS (Pronto Wi-Fi) |
| `venus_qseecom_mem` | `0x8F800000` | 8 MiB (`0x800000`) | `0x90000000` | Video Core & QSEE |

### 11.2 PIL Loading Sequence (Confirmed from dmesg)

```text
[6.002955] pil-q6v5-mss 4080000.qcom,mss: modem: loading from 0x86800000 to 0x8ba00000
[6.048943] pil: MBA boot done
[6.677921] pil-q6v5-mss 4080000.qcom,mss: modem: Brought out of reset
```

### 11.3 Required DTS Reserved Memory

```dts
reserved-memory {
    #address-cells = <1>;
    #size-cells = <1>;
    ranges;

    mba_mem: mba@86000000 {
        reg = <0x86000000 0x800000>;   /* 8 MiB MBA region */
        no-map;
    };

    mpss_mem: mpss@86800000 {
        reg = <0x86800000 0x5500000>;  /* 85 MiB MPSS region */
        no-map;
    };
};
```

**Source:** `29_modem_memory_regions.txt`, `33_RECOMMENDED_INVESTIGATION_SUMMARY.md`, `14_full_dmesg.txt`

---

## 12. Firmware Loading & Patching

### 12.1 Firmware File Layout in `/firmware/image/`

```text
/firmware/image/  (mounted from mmcblk0p1, vfat, 64MB)
├── modem.mdt    ← ELF header + segment hash table
│                   Segment 16 SHA256 at offset 0x05bc
├── modem.b00    ← ELF segment 0
├── modem.b01    ← ELF segment 1
│                   Segment 16 SHA256 at offset 0x0228
├── modem.b02 .. modem.b15
├── modem.b16    ← PRIMARY TARGET: contains lte_ml1_sleepmgr_cfg
│                   Patch offset 0x001117e0 (sleep manager)
│                   Patch offset 0x005f2150 (ERR_FATAL)
├── modem.b17 .. modem.bXX
├── mba.mbn      ← MBA: verifies SHA256 of all segments at boot
└── *.mbn        ← Other firmware components
```

### 12.2 Patch Application

```bash
# Extract from device
adb pull /firmware/image/ ./firmware_backup/

# Apply no-sleep patch
python3 patch_modem_nosleep.py ./firmware_backup/

# Verify patches
python3 -c "
with open('firmware_backup/modem.b16', 'rb') as f:
    data = f.read()
print('Sleep patch:', data[0x1117e0:0x1117e8].hex())
# Expected: 00c40078 00c09f52
print('ErrFatal patch:', data[0x5f2150:0x5f2154].hex())
# Expected: 00c09f52
"

# Push patched files to OpenWrt device
scp firmware_backup/modem.b16 root@<device>:/lib/firmware/
scp firmware_backup/modem.mdt root@<device>:/lib/firmware/
scp firmware_backup/modem.b01 root@<device>:/lib/firmware/

# After reboot, verify in dmesg:
# "MBA booted without debug policy, loading mpss"  ← Success
```

**Source:** `10_firmware_and_persist_contents.txt`, `MODEM_FIRMWARE_NO_SLEEP_PATCH_GUIDE.md`

---

## 13. USB Gadget Configuration

### 13.1 Stock Android USB

- **Vendor ID:** `0x05c6` (Qualcomm)
- **Functions:** `rndis,diag,serial,rmnet`
- **Data transport:** Native BAM-DMUX DMA via `rmnet0` — NOT USB CDC-ECM/NCM

### 13.2 Architecture Clarification

This is a Snapdragon 410 SoC. The AP (Application Processor) and modem (Hexagon QDSP6) are on the **same die**. They communicate via:
- **BAM-DMUX DMA** — for IP data (`rmnet0`)
- **SMD (Shared Memory Driver)** — for QMI control (`/dev/smdcntl*`)
- **SMSM** — for power state negotiation

The USB port is used for external connectivity (RNDIS tethering to PC/host). ModemManager on OpenWrt is irrelevant here — the data path is internal.

**Source:** `25_rmnetcli_and_telephony_dump.txt`, `26_rmnet_data_format.txt`, `27_tcpdump_rmnet0.txt`

---

## 14. Init Services & Startup Sequence

### 14.1 Required Service Startup Order

```
1. Kernel → Reserved memory allocated (mpss_mem, mba_mem)
2. init → Property service starts
3. rmt_storage starts (-P -s R/W mode) ← MUST be before modem boot
4. qmuxd starts
5. PIL loads modem firmware (qcom-q6v5-mss remoteproc)
6. Modem announces ready via SMSM
7. rild starts (connects to modem via qmuxd)
8. time_daemon starts (line 153 of init.target.rc) ← CRITICAL: anchors ATS clock
9. netmgrd starts
10. Data call established via QMI WDS → rmnet0 gets IP
```

### 14.2 Service Configuration Details

**`time_daemon`** (from `init.target.rc`):
```text
service time_daemon /system/bin/time_daemon
    class main
    user root
    group radio net_raw
    oneshot   ← Runs once at boot, sits in poll() forever
```

**`rmt_storage`:**
```text
service rmt_storage /system/bin/rmt_storage
    class core
    user root
    # MUST have: -P -s (POSIX shared mem, storage mode)
    # NOT -r (read-only)
```

**`qmuxd`:**
```text
service qmuxd /system/bin/qmuxd
    class main
    user radio
    group radio audio bluetooth
    # Opens /dev/smdcntl0-7 and /sys/power/wake_lock
```

**Source:** `init.qcom.rc`, `init.target.rc`, `init.rc`, `05_subsystems_and_properties.txt`

---

## 15. Kernel Configuration & Modules

### 15.1 Required Kernel Features

| Feature | Kconfig Option | Required For |
|:---|:---|:---|
| QMI WWAN | `CONFIG_USB_NET_QMI_WWAN` | wwan0 data interface |
| BAM-DMUX | `CONFIG_QCOM_BAM_DMUX` | IP data transport (+ patch 808) |
| QRTR IPC Router | `CONFIG_QRTR` | QMI Service 22, time sync |
| QRTR SMD | `CONFIG_QRTR_SMD` | SMD transport for QRTR |
| SMD | `CONFIG_QCOM_SMD` | Control channel transport |
| MSS remoteproc | `CONFIG_QCOM_Q6V5_MSS` | Modem firmware loading |
| SMSM | `CONFIG_QCOM_SMSM` | Power state negotiation |
| UIO | `CONFIG_UIO` | rmt_storage /dev/uio0 |
| UIO QMSS | `CONFIG_UIO_QCOM` | Qualcomm UIO for RMTFS |

### 15.2 Key Android Kernel Parameters (for reference)

```text
lpm_levels.sleep_disabled=1   ← Android DISABLES deep sleep
msm_rtb.filter=0x237
ehci-hcd.park=3
```

**Source:** `34_full_config_and_init_search.txt`, `08_radio_and_dmesg.txt`

---

## 16. Android vs OpenWrt Delta Matrix

*Full matrix: `39_STOCK_ANDROID_VS_OPENWRT_MATRIX.md`*

| # | Subsystem | Android Ground-Truth | OpenWrt Vanilla | Impact | Fix |
|:--|:---|:---|:---|:---|:---|
| 1 | SCLK Clock / DRX | `time_daemon` anchors ATS at boot | No time daemon | **CRITICAL** crash t=900s | Firmware patch OR Service 22 daemon |
| 2 | BAM-DMUX DMA PM | `runtime_status=unsupported`, 8 ch open | 1000ms autosuspend | **CRITICAL** SMSM drop | Patch 808 (PM lock) |
| 3 | RMTFS NV Sync | `rmt_storage -P -s` R/W | `-r` read-only | **CRITICAL** 900s sync rejected | Run `rmtfs -P -s` |
| 4 | SIM Power GPIO | GPIO 119 HIGH | May be floating | **HIGH** SIM drops | DTS + firstboot |
| 5 | SIM Hotplug | GPIO 114 LOW | May be pull-up | **MEDIUM** SIM loss | DTS configuration |
| 6 | 4G RF Power | GPIO 71 HIGH | Passive LED only | **MEDIUM** RF unlatched | DTS configuration |
| 7 | PM8916 Regulators | Standard Qualcomm power tree | Same in DTS | LOW (matches) | Already configured |
| 8 | Hardware Watchdog | In-kernel, `msm_watchdog` | Same `qcom_wdt` | **NONE** | No change needed |
| 9 | Data Wake-lock | QCRIL manages per-event | No wake-lock | **MEDIUM** DMA may sleep | Patch 808 covers this |
| 10 | Sysmon SSR | WCNSS isolated from Modem SSR | Broadcasts to WCNSS | **CRITICAL** Wi-Fi crash | Kernel SSR isolation |

---

## 17. OpenWrt Implementation Checklist

### Phase 1 — Immediate Crash Fix (P0)

- [ ] **Firmware No-Sleep Patch:** Apply `patch_modem_nosleep.py` to `modem.b16`, update SHA256 in `modem.mdt` (offset `0x05bc`) and `modem.b01` (offset `0x0228`)
- [ ] **BAM-DMUX PM Lock:** Verify `808-bam-dmux-stats.patch` is included in kernel build and applied
- [ ] **RMTFS R/W:** Verify `rmtfs` runs with `-P -s`, NOT `-r`; targeting `/dev/disk/by-partlabel/modemst1` etc.
- [ ] **GPIO Assertion:** Verify GPIO 119=HIGH, 114=LOW, 71=HIGH before modem boot (DTS or firstboot script)

### Phase 2 — Communication Setup (P1)

- [ ] **QRTR/IPC Router:** `CONFIG_QRTR` and `CONFIG_QRTR_SMD` enabled
- [ ] **SMD Channels:** `/dev/smdcntl0`–`/dev/smdcntl7` appear after modem boot
- [ ] **Memory Regions:** DTS `mpss_mem` at `0x86800000`, 85 MiB; `mba_mem` at `0x86000000`, 8 MiB
- [ ] **Data Call:** `wwan0` establishes IP via QMI WDS (libqmi/ModemManager)

### Phase 3 — Long-term Stability (P2)

- [ ] **SSR Isolation:** Modem SSR doesn't cascade to WCNSS
- [ ] **Thermal:** Thermal engine not aggressively throttling modem power
- [ ] **RMTFS fsg/fsc:** `fsg` and `fsc` partitions also accessible by rmtfs

### Success Criteria

- [ ] Modem does **NOT** crash at t=900s
- [ ] Modem operational for **>24 hours** continuous
- [ ] `wwan0` establishes IP and maintains connectivity
- [ ] `AT+CPIN?` → `+CPIN: READY`
- [ ] dmesg: NO `lte_ml1_sleepmgr_stm.c:4054`
- [ ] dmesg: NO `Failed to resume: -22`
- [ ] `/d/bam_dmux/tbl`: All ch00–ch07 `local open=Y remote open=Y`

---

## 18. Ghidra Reverse Engineering Guide

### 18.1 Setup

```bash
# Headless Ghidra
GHIDRA=/home/shaanair/Projects/Ghidra/ghidra_12.1.2_PUBLIC/support/analyzeHeadless

# Projects
PROJ=/home/shaanair/Projects/msm8916-openwrt-clean/ghidra_proj

# Scripts  
SCRIPTS=/home/shaanair/Projects/msm8916-openwrt-clean/ghidra_scripts

# Extracted binaries
BINS="/home/shaanair/Projects/msm8916-openwrt-clean/Docs/Modem Stability/Stock_Android_Analysis/binaries"

# Existing projects:
#   $PROJ/ModemCrashProj.gpr    ← modem.b16 analysis
#   $PROJ/time_daemon_proj.gpr  ← time_daemon analysis
```

### 18.2 Priority Analysis Targets

| Priority | Binary | Size | Key Functions | Purpose |
|:--|:---|:---|:---|:---|
| **P0** | `modem.b16` | ~50MB | `FUN_c03987e0` (`lte_ml1_sleepmgr_cfg`) | THE crash target — patch here |
| **P0** | `time_daemon` | 17,756B | `genoff_boot_tod_init()`, `tod_update_ind_cb()`, `genoff_modem_qmi_init()` | QMI Service 22 reference impl |
| **P0** | `libtime_genoff.so` | 9,300B | `time_genoff_operation()` — formats msg 0x0020, 0x0025 | QMI message TLV formats |
| **P1** | `rmt_storage` | 23,504B | Main loop, UIO DMA handling | RMTFS reference |
| **P1** | `libqmiservices.so` | 88,652B | `time_service_get_service_object_v01()`, `wds_get_service_object_v01()` | QMI IDL type tables |
| **P1** | `libril-qc-qmi-1.so` | 5,032,528B | `qmi_ril_data_call_list_changed()`, `netmgr_kif_cb()` | Dormancy & BAM link state |
| **P2** | `qmuxd` | 84,592B | SMD read/write, wake-lock pattern | QMI multiplexer reference |
| **P2** | `netmgrd` | 236,088B | `netmgr_kif_cb()`, rmnet0 configuration | Network mgmt reference |

### 18.3 Headless Ghidra Commands

```bash
# Import and analyze time_daemon (ARM 32-bit LE)
$GHIDRA "$PROJ" time_daemon_proj \
    -import "$BINS/time_daemon" \
    -processor ARM:LE:32:v7 \
    -cspec default \
    -analysis \
    -postScript "$SCRIPTS/FindMain.java" \
    -postScript "$SCRIPTS/DecompileKey.java"

# Analyze existing modem.b16 project with crash analysis script
$GHIDRA "$PROJ" ModemCrashProj \
    -process "modem.b16" \
    -postScript "$SCRIPTS/AnalyzeModemCrash.java"

# Import modem.b16 fresh (Hexagon QDSP6v5)
$GHIDRA "$PROJ" ModemCrashProj \
    -import /path/to/modem.b16 \
    -processor Hexagon:LE:32:default \
    -analysis \
    -postScript "$SCRIPTS/DecompileOffset.java" "c03987e0"

# Targeted decompile at specific virtual address
$GHIDRA "$PROJ" ModemCrashProj \
    -process "modem.b16" \
    -postScript "$SCRIPTS/DecompileOffset.java" "c03987e0"

# Parse QMI IDL tables from libqmiservices.so
$GHIDRA "$PROJ" ModemCrashProj \
    -import "$BINS/libqmiservices.so" \
    -processor ARM:LE:32:v7 \
    -analysis \
    -postScript "$SCRIPTS/ParseQmiIdl.java" \
    -postScript "$SCRIPTS/DumpIDL.java"
```

### 18.4 Available Ghidra Scripts

| Script | Purpose |
|:---|:---|
| `AnalyzeModemCrash.java` | Full modem crash analysis — locates sleep manager and ERR_FATAL |
| `DecompileKey.java` | Decompile key functions by name |
| `DecompileOffset.java` | Decompile function at specific VA (hex arg) |
| `FindMain.java` | Locate `main()` entry point |
| `ParseQmiIdl.java` | Parse QMI IDL type tables from libqmiservices |
| `DumpIDL.java` | Dump QMI IDL message structures |
| `DumpTypeTable.java` | Dump QMI type tables |
| `DecompileAll.java` | Decompile all functions in binary |
| `TargetedDecompile.java` | Targeted function decompilation by list |
| `FindFuncs.java` | Find functions by pattern |
| `CheckConstants.java` | Find hardcoded constants (e.g., 900, 0x384) |

---

## 19. ADB Investigation Commands

### 19.1 Root Access (Required First)

```bash
adb shell setprop service.adb.root 1
adb shell busybox killall adbd
sleep 8
adb shell   # Reconnect as root
id          # Verify: uid=0(root)
```

### 19.2 Complete Snapshot Script

```bash
#!/bin/bash
# Run this on Android to capture full system state

echo "=== UPTIME ===" && adb shell uptime
echo "=== GPIO STATE ===" && adb shell cat /sys/kernel/debug/gpio
echo "=== PROCESSES ===" && adb shell ps
echo "=== BAM-DMUX TABLE ===" && adb shell cat /d/bam_dmux/tbl
echo "=== BAM-DMUX STATS ===" && adb shell cat /d/bam_dmux/stats
echo "=== BAM-DMUX RUNTIME STATUS ===" && adb shell cat /sys/bus/platform/drivers/bam_dmux/*/power/runtime_status
echo "=== WATCHDOG DEVICES ===" && adb shell ls /dev/watchdog* 2>&1
echo "=== WATCHDOG SYSFS ===" && adb shell cat /sys/devices/soc.0/b017000.qcom,wdt/disable
echo "=== MEMORY REGIONS ===" && adb shell cat /proc/iomem
echo "=== SMD CHANNELS ===" && adb shell ls -la /dev/smdcntl*
echo "=== FIRMWARE FILES ===" && adb shell ls -la /firmware/image/
echo "=== WAKE LOCKS ===" && adb shell cat /sys/power/wake_lock
echo "=== NETWORK CONFIG ===" && adb shell netcfg
echo "=== PING TEST ===" && adb shell ping -c 5 1.1.1.1
echo "=== RADIO LOGCAT ===" && adb shell logcat -b radio -d -t 50
echo "=== DMESG (last 50 lines) ===" && adb shell dmesg | tail -50
```

### 19.3 15-Minute Barrier Test

```bash
# Capture state at t=0
adb shell "uptime; cat /d/bam_dmux/tbl; ping -c 3 1.1.1.1"

# Wait >16 minutes

# Capture state at t>900s
adb shell "uptime; cat /d/bam_dmux/tbl; ping -c 3 1.1.1.1; dmesg | tail -30"

# Look for:
# - ping packet loss (any loss = failure)
# - bam_dmux ch00-ch07 closed (crash)
# - dmesg: lte_ml1_sleepmgr_stm.c:4054  (SCLK crash)
# - dmesg: Failed to resume: -22          (BAM crash)
```

### 19.4 Live Strace Tracing

```bash
# time_daemon — verify poll() behavior
adb shell "strace -p \$(pidof time_daemon) -e trace=poll,recvmsg 2>&1 | head -20"

# qmuxd — verify wake-lock pattern
adb shell "strace -f -p \$(pidof qmuxd) -e trace=write -s 32 2>&1 | grep wake"

# rild — verify QMI receive and wake-lock acquisition
adb shell "strace -f -p \$(pidof rild) -e write -s 32 2>&1 | grep -E 'qcril|radio-interface'"

# rmt_storage — verify it's doing writes (not read-only)
adb shell "strace -p \$(pidof rmt_storage) -e trace=write 2>&1 | head -20"
```

### 19.5 GPIO Investigation

```bash
# Read all GPIO states
adb shell cat /sys/kernel/debug/gpio

# Check specific critical GPIOs
adb shell cat /sys/class/gpio/gpio119/value   # Should be: 1
adb shell cat /sys/class/gpio/gpio114/value   # Should be: 0
adb shell cat /sys/class/gpio/gpio71/value    # Should be: 1

# Full pinctrl state
adb shell cat /d/pinctrl/1000000.pinctrl/pinconf-pins 2>/dev/null
adb shell cat /d/pinctrl/1000000.pinctrl/pins 2>/dev/null
```

### 19.6 QMI Channel Investigation

```bash
# Check SMD control channels
adb shell ls -la /dev/smdcntl*

# Check QMI proxy sockets
adb shell netstat -x | grep -E "qmi|smd"

# Check AT command port
adb shell ls -la /dev/smd11

# Send AT command test
adb shell "echo -e 'ATI\r' > /dev/smd11; sleep 1; cat /dev/smd11"
```

### 19.7 inotify Filesystem Monitor

```bash
# Verify modem does NOT read loose config files (uses raw eMMC only)
adb shell inotifywait -m -r /persist /data/misc/radio 2>&1 &
sleep 120  # Wait 2 minutes
# Expected: zero output (confirmed by 36_inotify_filesystem_log.txt)
```

---

## 20. File Index

### Research Data Files (Chronological — all in this directory)

| # | File | Size | Content | Key Finding |
|:--|:---|:---|:---|:---|
| 01 | `01_system_overview.txt` | 328B | System info | Platform: MSM8916, Android 4.4.4 |
| 02 | `02_gpio_dump.txt` | 3.9KB | Full GPIO state | **GPIO 119=HIGH, 114=LOW, 71=HIGH** |
| 03 | `03_process_dump.txt` | 16KB | All running processes | PIDs, threads, cmdlines |
| 04 | `04_network_and_bam_dump.txt` | 26KB | BAM-DMUX, network state | **All 8 ch open, autosuspend=unsupported** |
| 05 | `05_subsystems_and_properties.txt` | 11KB | Android properties | RIL, radio, QMI property dump |
| 06 | `06_init_gpio_config.txt` | 1.8KB | GPIO init script | init.qcom.rc GPIO section |
| 06b | `06_sim_and_gpio_scripts.txt` | 2.9KB | SIM GPIO scripts | SIM control shell scripts |
| 07 | `07_leds_and_sim_gpio_nodes.txt` | 3.8KB | LED/SIM GPIO nodes | Sysfs nodes for SIM control |
| 08 | `08_radio_and_dmesg.txt` | 21KB | Radio logcat + dmesg | Boot-time modem init sequence |
| 09 | `09_partitions_and_mounts.txt` | 5.2KB | Partition table | eMMC layout and mount points |
| 10 | `10_firmware_and_persist_contents.txt` | 3.8KB | Firmware files | modem.b* list, /persist contents |
| 11 | `11_time_daemon_analysis.txt` | 190B | time_daemon initial analysis | Entry point |
| 12 | `12_system_libraries_list.txt` | 1.2KB | Library inventory | All .so files in /system/lib |
| 13 | `13_modem_boot_dmesg.txt` | — | Boot dmesg | Modem PIL loading sequence |
| 14 | `14_full_dmesg.txt` | 78KB | Complete kernel log | **Full dmesg — all boot messages** |
| 15 | `15_debugfs_bam_smd_smsm.txt` | 16KB | BAM/SMD/SMSM debugfs | IPC subsystem internal state |
| 16 | `16_15min_barrier_test_log.txt` | 12KB | **15-min test result** | **0% packet loss at t=921s** |
| 17 | `17_WHY_STOCK_ANDROID_DOES_NOT_CRASH.md` | 5.8KB | **Root cause analysis** | **4 stability mechanisms** |
| 18 | `18_lsof_and_sockets_dump.txt` | 1.4MB | Full lsof output | All open files — complete IPC map |
| 19 | `19_process_file_descriptors.txt` | 9.9KB | Per-process FDs | IPC topology per daemon |
| 20 | `20_strace_time_daemon.txt` | 586B | time_daemon strace | poll() blocking — 0% CPU confirmed |
| 21 | `21_strace_qmuxd.txt` | 5.9KB | qmuxd strace | Wake-lock + SMD read pattern |
| 22 | `22_strace_netmgrd.txt` | 603B | netmgrd strace | QMI WDS socket patterns |
| 23 | `23_MODEM_COMMUNICATION_PROTOCOL_TRACE.md` | 3.3KB | **QMI protocol trace** | **Byte-level QMI packet analysis** |
| 24 | `24_available_debug_tools.txt` | 38KB | Tool inventory | All binaries in /system/bin, /system/xbin |
| 25 | `25_rmnetcli_and_telephony_dump.txt` | 9.3KB | rmnet config | rmnet0 interface configuration |
| 26 | `26_rmnet_data_format.txt` | 345B | Data format | Raw-IP mode confirmed |
| 27 | `27_tcpdump_rmnet0.txt` | 1.3KB | Packet capture | rmnet0 live traffic |
| 28 | `28_watchdog_investigation.txt` | 1.4KB | **Watchdog analysis** | **No /dev/watchdog — in-kernel only** |
| 29 | `29_modem_memory_regions.txt` | 11KB | **Memory map** | **mpss_mem=0x86800000, 85MiB** |
| 30 | `30_gpio_full_pinmap.txt` | 4.0KB | Full pin map | Complete TLMM GPIO map |
| 31 | `31_at_command_test.txt` | 2.5KB | AT commands | Network status, signal strength |
| 32 | `32_at_identification.txt` | 776B | AT modem ID | HIMI_U01_MODEM_V1.0 confirmed |
| 33 | `33_RECOMMENDED_INVESTIGATION_SUMMARY.md` | 4.2KB | **Investigation summary** | **All ground-truth parameters** |
| 34 | `34_full_config_and_init_search.txt` | 2.9KB | Config search | rc file search results |
| 35 | `35_rild_and_healthd_strace.txt` | 6.9KB | **rild + healthd strace** | **Wake-lock at t=19,739s** |
| 36 | `36_inotify_filesystem_log.txt` | 150B | Filesystem events | Zero polling — raw eMMC only |
| 37 | `37_COMPREHENSIVE_STOCK_TRACE_ANALYSIS.md` | 3.2KB | Trace analysis | **5h 28m 59s stable uptime** |
| 38 | `38_deep_system_telemetry.txt` | 11KB | System telemetry | CPU, memory, thermal at steady state |
| 39 | `39_STOCK_ANDROID_VS_OPENWRT_MATRIX.md` | 8.8KB | **Delta matrix** | **10-row subsystem comparison** |
| **40** | **`40_MASTER_CRASH_INVESTIGATION_GUIDE.md`** | **— (this file)** | **Master reference** | **All findings consolidated** |

### Key Reports

| Document | Location | Purpose |
|:---|:---|:---|
| `STOCK_ANDROID_GROUND_TRUTH_REPORT.md` | This dir | Master hardware ground-truth |
| `GHIDRA_ANALYSIS_TARGETS.md` | This dir | RE targets for Ghidra |
| `MODEM_FIRMWARE_NO_SLEEP_PATCH_GUIDE.md` | Repo root | Firmware patch — complete guide |
| `808-bam-dmux-stats.patch` | `msm89xx/patches/` | BAM-DMUX PM lock kernel patch |
| `init.qcom.rc` | This dir | Android init (Qualcomm services) |
| `init.target.rc` | This dir | Android target-specific init |
| `init.rc` | This dir | Android main init |
| `init.qcom.sh` | This dir | Android Qualcomm init shell script |

### Extracted Binaries for Ghidra Analysis

Directory: `binaries/` (all ARM 32-bit LE unless noted)

| Binary | Size | Architecture | Priority |
|:---|:---|:---|:---|
| `time_daemon` | 17,756B | ARM 32-bit | **P0** |
| `libtime_genoff.so` | 9,300B | ARM 32-bit | **P0** |
| `rmt_storage` | 23,504B | ARM 32-bit | **P0** |
| `libqmiservices.so` | 88,652B | ARM 32-bit | P1 |
| `libril-qc-qmi-1.so` | 5,032,528B | ARM 32-bit | P1 |
| `libqmi_client_qmux.so` | 42,232B | ARM 32-bit | P1 |
| `qmuxd` | 84,592B | ARM 32-bit | P2 |
| `netmgrd` | 236,088B | ARM 32-bit | P2 |
| `rild` | 9,556B | ARM 32-bit | P2 |
| `qseecomd` | 9,640B | ARM 32-bit | P3 |
| `thermal-engine` | 222,776B | ARM 32-bit | P3 |
| `mpdecision` | 43,448B | ARM 32-bit | P3 |
| `qmiproxy` | 166,452B | ARM 32-bit | P3 |
| `rfs_access` | 18,564B | ARM 32-bit | P3 |

Also: `vendor_libs/` — additional Qualcomm shared libraries (libqmi.so, libqmi_cci.so, etc.)

---

*This document consolidates all 39 prior research files into a single authoritative reference.*  
*All findings derived from live instrumentation of Android 4.4.4 on physical UFI-001C hardware.*  
*Confirmed continuous stable uptime: 5 hours 28 minutes 59 seconds (19,739 seconds) with 0% packet loss.*
