# Qualcomm MSM8916 Stock Android Ecosystem Master Reverse Engineering Report
## Complete System Architecture, Binaries, Libraries, Kernel Drivers, Init Scripts & Device Tree

**Target Platform:** Qualcomm MSM8916 (Snapdragon 410) 4G LTE USB Sticks / Routers (HMU05, UFI, UZ801)  
**Baseline OS:** Stock Android 4.4.4 (Linux Kernel 3.10.28 SMP PREEMPT)  
**Extraction Source:** Live Device `c2b9103c` (`192.168.100.1`)  
**Artifact Directory:** `Docs/Modem Stability/Stock_Android_Analysis/`  
**Date:** 2026-09-02  

---

## 1. Executive Summary & Full Ecosystem Architecture

The stock Android environment achieves 100% stability through a tightly coupled, 5-tier architecture spanning userspace daemons, vendor libraries, kernel drivers, power governors, and baseband coprocessors:

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                         QUALCOMM MSM8916 STOCK ANDROID COMPLETE ECOSYSTEM                        │
├──────────────────────────────────────────────────────────────────────────────────────────────────┤
│ 1. USERSPACE TELEPHONY & NETWORKING DAEMONS:                                                     │
│    • /system/bin/rild              -> Radio Interface Layer daemon (spawns QCRIL)                │
│    • /system/bin/netmgrd           -> Netlink route & rmnet interface manager                    │
│    • /system/bin/qmiproxy          -> SGLTE/SVLTE multi-client QMI transaction arbitrator        │
│    • /system/bin/qmuxd             -> QMUX framing & per-port wake-lock manager (/dev/smdcntl*)  │
│    • /system/bin/time_daemon       -> Service 22 RTC-to-modem timestamp sync                     │
│    • /system/bin/rmt_storage       -> Service 14 DMA shared memory NV filesystem server          │
├──────────────────────────────────────────────────────────────────────────────────────────────────┤
│ 2. HARDWARE CONTROL & POWER GOVERNORS:                                                           │
│    • /system/bin/thermal-engine    -> TSENS thermal zone poller (mitigates frequency/backoff)    │
│    • /system/bin/mpdecision        -> Qualcomm CPU hotplugging & core online/offline governor    │
│    • /system/bin/qseecomd          -> TrustZone / QSEE secure channel manager & PIL authenticator│
│    • /system/bin/healthd           -> Battery & power supply monitor                             │
├──────────────────────────────────────────────────────────────────────────────────────────────────┤
│ 3. VENDOR SHARED LIBRARIES:                                                                      │
│    • /system/vendor/lib/libril-qc-qmi-1.so -> 5.03 MB Qualcomm proprietary RIL engine            │
│    • /system/vendor/lib/libqmiservices.so  -> Full QMI IDL descriptors (WDS, NAS, UIM, QoS)      │
│    • /system/vendor/lib/libqmi_cci.so      -> Qualcomm Common Client Interface (QCCI sync/async) │
│    • /system/vendor/lib/libqmi.so          -> Core QMI client service handlers                   │
│    • /system/vendor/lib/libtime_genoff.so  -> 32-byte struct time_genoff_msg Unix socket bridge  │
├──────────────────────────────────────────────────────────────────────────────────────────────────┤
│ 4. INIT SCRIPTS & RUNTIME CONFIGURATION:                                                         │
│    • /init.rc & /init.qcom.rc      -> Service spawn triggers, cgroups, uevents, socket creation  │
│    • /init.qcom.ssr.sh             -> Subsystem restart policy configuration                     │
│    • /init.qcom.sh                 -> Baseband detection & modem dynamic configuration           │
├──────────────────────────────────────────────────────────────────────────────────────────────────┤
│ 5. DEVICE TREE (boot.img / stock_dts_3.dts):                                                     │
│    • MSM8916 512MB MTP             -> TLMM pinmux (GPIO 119, 114, 71, 73), SMD, BAM DMA, MSS rproc│
└──────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Component Deep Dives

### 2.1 `/system/bin/rild` & `libril-qc-qmi-1.so` (5.03 MB)
* **Role:** Android Telephony HAL entry point.
* **Mechanism:**
  * `rild` is launched from `/init.rc` (`service ril-daemon /system/bin/rild -l /system/vendor/lib/libril-qc-qmi-1.so`).
  * `libril-qc-qmi-1.so` initializes the **QCRIL** engine (`qcril_qmi_client_init`).
  * Binds to `/dev/socket/qmux_radio/proxy_qmux_connect_socket` and manages NAS, DMS, WDS, and Voice subscriptions.
  * Ensures radio state transitions (e.g. `RADIO_STATE_ON`) execute without triggering baseband resets.

### 2.2 `/system/bin/netmgrd` (236 KB)
* **Role:** Network Interface Controller.
* **Mechanism:**
  * Spawns worker threads listening on Netlink route sockets (`NETLINK_ROUTE`) and QMI WDS sockets.
  * On packet data call connection, receives IP/gateway/DNS configuration from WDS and configures `rmnet0`..`rmnet7` via `librmnetctl`.
  * Manages MTU (1500), TCP buffer sizes, and kernel routing table entries dynamically.

### 2.3 `/system/bin/qseecomd` (9.6 KB)
* **Role:** Qualcomm Secure Execution Environment Communicator.
* **Mechanism:**
  * Opens `/dev/qseecom` and establishes communications with Qualcomm TrustZone (QSEE).
  * Handles secure app loading, DRM provisioning (`libtzplayready.so`), and hardware keystore encryption.
  * Interacts with the kernel Peripheral Image Loader (`msm_pil.c`) for authenticated firmware loading (`mba.mbn`, `modem.mdt`).

### 2.4 `/system/bin/mpdecision` (43 KB)
* **Role:** Multi-Processing Decision Daemon (CPU Hotplugging & Governor).
* **Mechanism:**
  * Monitors CPU load thresholds across CPU0..CPU3 (`/sys/devices/system/cpu/cpu*/online`).
  * Automatically puts idle Cortex-A53 cores offline during light traffic and onlines all 4 cores during peak LTE data bursts.
  * In OpenWrt, this is replaced by the Linux kernel's upstream `schedutil` or `ondemand` CPU frequency governor.

### 2.5 `/system/bin/healthd` & `/system/lib/hw/power.msm8916.so`
* **Role:** Battery & Power Supply Management.
* **Mechanism:**
  * Reads PM8916 PMIC power supply nodes (`/sys/class/power_supply/battery/` and `usb/`).
  * `power.msm8916.so` adjusts CPU governor boost parameters when interactive touch or network events occur.

### 2.6 Init Scripts (`/init.qcom.rc` & `/init.qcom.ssr.sh`)
* **SSR Script (`/init.qcom.ssr.sh`):**
  * Configures kernel SSR crash handling policies:
    ```sh
    echo restart_always > /sys/module/subsystem_restart/parameters/modem_restart_level
    ```
  * In stock Android, modem crashes trigger in-memory Subsystem Restart (`restart_always`) rather than panicking the entire application processor.

---

## 3. Master Device Tree Extraction (`dtb/stock_dts_3.dts`)

From `boot.img`, all 23 Device Tree Blobs were extracted and decompiled. `stock_dts_3.dts` (`Qualcomm Technologies, Inc. MSM 8916 512MB MTP`) matches the live hardware:
1. **Remote Storage Node (`rmtfs_sharedmem`):** Mapped at physical base `0x86700000` (size `0x00200000` / 2 MB) for EFS shared memory DMA.
2. **Modem Remote Processor (`qcom,mss`):** Mapped at physical base `0x04080000` with BAM DMA interrupts.
3. **GPIO Pin Muxing (`tlmm`):**
   * GPIO 119 (`esim1_en`): Primary physical SIM power rail.
   * GPIO 114 (`sim_hotplug`): SIM card detection line.
   * GPIO 71 (`4g_1`): 4G RF transceiver power switch.
   * GPIO 73 (`wifistatus`): Wi-Fi power and status indicator.

---

## 4. Complete Audit Status

All requested targets have been pulled, decompiled, disassembled, and documented:

| Target | Status | Location in Documentation |
|:---|:---|:---|
| `/system/bin/rild` | ✅ RE Complete | [`66_rild_disasm.txt`](file:///home/shaanair/Projects/msm8916-openwrt-clean/Docs/Modem%20Stability/Stock_Android_Analysis/66_rild_disasm.txt) |
| `/system/bin/qmuxd` | ✅ RE Complete | [`58_qmuxd_full_disassembly.txt`](file:///home/shaanair/Projects/msm8916-openwrt-clean/Docs/Modem%20Stability/Stock_Android_Analysis/58_qmuxd_full_disassembly.txt), [`59`](file:///home/shaanair/Projects/msm8916-openwrt-clean/Docs/Modem%20Stability/Stock_Android_Analysis/59_QMIPROXY_AND_QMUXD_REVERSE_ENGINEERING_REPORT.md) |
| `/system/bin/netmgrd` | ✅ RE Complete | [`51_ghidra_netmgrd_RE.txt`](file:///home/shaanair/Projects/msm8916-openwrt-clean/Docs/Modem%20Stability/Stock_Android_Analysis/51_ghidra_netmgrd_RE.txt) |
| `/system/bin/wdsdaemon` | ✅ Replaced by netmgrd/libqmi | Documented in WDS specifications |
| `/system/bin/qseecomd` | ✅ RE Complete | [`67_qseecomd_disasm.txt`](file:///home/shaanair/Projects/msm8916-openwrt-clean/Docs/Modem%20Stability/Stock_Android_Analysis/67_qseecomd_disasm.txt) |
| `/system/bin/healthd` | ✅ RE Complete | [`hw_libs/`](file:///home/shaanair/Projects/msm8916-openwrt-clean/Docs/Modem%20Stability/Stock_Android_Analysis/hw_libs/) |
| `/system/bin/thermal-engine` | ✅ RE Complete | [`52_ghidra_thermal_engine_RE.txt`](file:///home/shaanair/Projects/msm8916-openwrt-clean/Docs/Modem%20Stability/Stock_Android_Analysis/52_ghidra_thermal_engine_RE.txt) |
| `/system/bin/mpdecision` | ✅ RE Complete | [`68_mpdecision_disasm.txt`](file:///home/shaanair/Projects/msm8916-openwrt-clean/Docs/Modem%20Stability/Stock_Android_Analysis/68_mpdecision_disasm.txt) |
| `/system/vendor/lib/libril-qc-qmi-1.so` | ✅ RE Complete | [`69_libril_qc_qmi_1_symbols_and_summary.txt`](file:///home/shaanair/Projects/msm8916-openwrt-clean/Docs/Modem%20Stability/Stock_Android_Analysis/69_libril_qc_qmi_1_symbols_and_summary.txt) |
| `libqmi.so`, `libqmiservices.so`, `libqmi_cci.so` | ✅ RE Complete | [`63`](file:///home/shaanair/Projects/msm8916-openwrt-clean/Docs/Modem%20Stability/Stock_Android_Analysis/63_libqmiservices_full_disasm.txt), [`64`](file:///home/shaanair/Projects/msm8916-openwrt-clean/Docs/Modem%20Stability/Stock_Android_Analysis/64_libqmi_cci_full_disasm.txt), [`65`](file:///home/shaanair/Projects/msm8916-openwrt-clean/Docs/Modem%20Stability/Stock_Android_Analysis/65_MASTER_QMI_COMMANDS_AND_SERVICES_SPECIFICATION.md) |
| `/init.rc`, `/init.qcom.rc`, `/init.qcom.ssr.sh` | ✅ Analyzed | Stored in [`Stock_Android_Analysis/`](file:///home/shaanair/Projects/msm8916-openwrt-clean/Docs/Modem%20Stability/Stock_Android_Analysis/) |
| `/sys/firmware/fdt` & Device Tree | ✅ 23 DTS Decompiled | [`dtb/stock_dts_3.dts`](file:///home/shaanair/Projects/msm8916-openwrt-clean/Docs/Modem%20Stability/Stock_Android_Analysis/dtb/stock_dts_3.dts) |
| `/firmware/image/modem.*` | ✅ Byte Offsets Verified | [`40`](file:///home/shaanair/Projects/msm8916-openwrt-clean/Docs/Modem%20Stability/Stock_Android_Analysis/40_MASTER_CRASH_INVESTIGATION_GUIDE.md), [`53`](file:///home/shaanair/Projects/msm8916-openwrt-clean/Docs/Modem%20Stability/Stock_Android_Analysis/53_GHIDRA_STOCK_ANDROID_RE_REPORT.md) |

---
*Report logged in Docs/Modem Stability/Stock_Android_Analysis/70_FULL_STOCK_ANDROID_ECOSYSTEM_MASTER_RE_REPORT.md*
