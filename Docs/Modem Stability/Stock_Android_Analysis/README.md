# Stock Android Forensic Analysis & Reverse Engineering Index

**Target Device:** Qualcomm MSM8916 Snapdragon 410 (Generic HMU05 / UFI)  
**Baseband Firmware:** `HIMI_U01_MODEM_V1.0` (`MPSS.DPM.1.0.C7`)  
**Firmware OS:** Stock Android 4.4.4 (Kernel 3.10.28)  
**Recorded Continuous Uptime:** **>33 Minutes (2,010+ seconds)** with 0% packet loss and 0 modem errors.  

---

## 1. Directory Contents & Reports

| Document / Asset | Description |
| :--- | :--- |
| [`STOCK_ANDROID_GROUND_TRUTH_REPORT.md`](file:///home/shaanair/Projects/msm8916-openwrt-clean/Docs/Modem%20Stability/Stock_Android_Analysis/STOCK_ANDROID_GROUND_TRUTH_REPORT.md) | Comprehensive master report detailing hardware GPIOs, partition layouts, and DMA architecture. |
| [`17_WHY_STOCK_ANDROID_DOES_NOT_CRASH.md`](file:///home/shaanair/Projects/msm8916-openwrt-clean/Docs/Modem%20Stability/Stock_Android_Analysis/17_WHY_STOCK_ANDROID_DOES_NOT_CRASH.md) | Deep comparative breakdown of the 4 stability mechanisms that prevent the 15-minute crash. |
| [`23_MODEM_COMMUNICATION_PROTOCOL_TRACE.md`](file:///home/shaanair/Projects/msm8916-openwrt-clean/Docs/Modem%20Stability/Stock_Android_Analysis/23_MODEM_COMMUNICATION_PROTOCOL_TRACE.md) | Dynamic `strace`, `lsof`, and `/proc/PID/fd/` system call trace and QMI packet breakdown. |
| [`GHIDRA_ANALYSIS_TARGETS.md`](file:///home/shaanair/Projects/msm8916-openwrt-clean/Docs/Modem%20Stability/Stock_Android_Analysis/GHIDRA_ANALYSIS_TARGETS.md) | Reverse engineering target list for Ghidra/IDA (`time_daemon`, `libril-qc-qmi-1.so`, `libqmiservices.so`). |
| [`binaries/`](file:///home/shaanair/Projects/msm8916-openwrt-clean/Docs/Modem%20Stability/Stock_Android_Analysis/binaries/) | Extracted vendor executables (`time_daemon`, `qmuxd`, `netmgrd`, `rild`, `rmt_storage`, `thermal-engine`). |
| [`vendor_libs/`](file:///home/shaanair/Projects/msm8916-openwrt-clean/Docs/Modem%20Stability/Stock_Android_Analysis/vendor_libs/) | Complete Qualcomm QMI, RIL, and Time shared libraries (`.so`). |

---

## 2. Key Diagnostic Discoveries

1. **Hardware GPIO Mapping**:
   * **GPIO 119 (`esim1_en`)**: `HIGH` (255) $\rightarrow$ Powers primary SIM.
   * **GPIO 114 (`sim_hotplug`)**: `LOW` (0) $\rightarrow$ SIM presence detect.
   * **GPIO 71 (`4g_1`)**: `HIGH` (1) $\rightarrow$ 4G RF path & status LED.
2. **Time Synchronization**:
   * `time_daemon` sets RTC offset once at boot, then waits in `poll()` for asynchronous NITZ tower indications.
3. **BAM-DMUX DMA Transport**:
   * Operates in Raw-IP mode without 1000ms autosuspend flapping; transitions to `DORMANT` (`active=2`) at protocol layer while keeping DMA descriptors active.
4. **EFS Storage**:
   * `rmt_storage` has read-write access to `/dev/block/bootdevice/by-name/` for 900-second NV sync flushes.
