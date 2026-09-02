# Qualcomm MSM8916: Stock Android vs. OpenWrt Stability Difference Matrix

**Target Device:** Qualcomm Snapdragon 410 / MSM8916 (Generic HMU05 / UFI)  
**Baseline Firmware:** Stock Android 4.4.4 (Kernel 3.10.28) — **>5.5 Hours Continuous Uptime**  
**Comparison Target:** OpenWrt 25.12.5 (Kernel 6.12.94 `msm89xx`)  
**Artifact Path:** `Docs/Modem Stability/Stock_Android_Analysis/39_STOCK_ANDROID_VS_OPENWRT_MATRIX.md`  

---

## 1. Executive Summary

By instrumenting the running stock Android system continuously across 0–15–30–45–300+ minutes, we mapped **every single subsystem difference** between stock Android (which runs indefinitely without crashing) and vanilla OpenWrt (which crashes at $t = 900\text{s}$).

---

## 2. Comprehensive Subsystem Comparison Matrix

```
========================================================================================================================
SUBSYSTEM               STOCK ANDROID GROUND-TRUTH               OPENWRT VANILLA                DIFFERENCE & IMPACT
========================================================================================================================
1. SCLK Clock /         time_daemon starts at boot;              No time daemon running;        CRITICAL: At T=900s,
   DRX Sleep FSM        anchors baseband to RTC (/dev/rtc0);     baseband SCLK timer            SCLK drift causes fatal
                        registers tod_update_ind_cb (0x0025).    drifts uncorrected.            assertion in lte_ml1.
────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
2. BAM-DMUX DMA         Runtime autosuspend unsupported;         Upstream Linux enables         CRITICAL: 1000ms idle
   Power Management     all 8 channels (ch00-ch07) remain        1000ms autosuspend             triggers SMSM drop;
                        persistently open & ready in SPS/BAM.    (BAM_DMUX_AUTOSUSPEND_DELAY).  fails resume -> drops RX.
────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
3. Remote Storage       rmt_storage runs full read-write         OpenWrt rmtfs default          CRITICAL: 900s EFS2
   (EFS2 NV Sync)       over /dev/uio0 shared memory DMA to      sometimes ran with -r          NV sync rejected ->
                        modemst1, modemst2, fsg, fsc.            (read-only).                   panics with :Excep :0:.
────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
4. Primary SIM Power    GPIO 119 (esim1_en) driven HIGH (255)   DTS may leave floating         HIGH: SIM power unpowered
   & Select Line        continuously at boot.                    or misconfigured.              or drops out on dormancy.
────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
5. SIM Hotplug Detect   GPIO 114 (sim_hotplug) driven            DTS may leave unmapped         MEDIUM: Baseband drops
   Detection Line       LOW (0) continuously.                    or pull-up active.             SIM card session.
────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
6. 4G RF Path Power     GPIO 71 (4g_1) driven HIGH (1)           DTS may treat only as          MEDIUM: Baseband RF
   & Front-End Latch    continuously.                            passive LED.                   path unlatched.
────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
7. PM8916 Regulators    L5 (1.8V), L6 (1.8V), L7 (1.8V MSS PLL), Set in DTS rpm-regulator;     LOW: Matches standard
                        L13 (3.075V USB/Audio), L17 (2.85V SPI), managed by RPM firmware.       Qualcomm power tree.
                        S4 (1.8V PMIC Buck).
────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
8. Hardware Watchdog    MSM hardware WDT (b017000.qcom,wdt)      OpenWrt kernel uses same       NONE: In-kernel timer
                        fed in-kernel by scheduler core.         qcom_wdt driver.               feeding works properly.
────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
9. Data Dormancy &      QCRIL manages wake-locks (qcril,         ModemManager uses QMI WDS;     MEDIUM: OpenWrt must
   Wake-Lock Lifecycle  radio-interface) on every QMI event;     network stack has no           maintain DMA active
                        transitions to DORMANT (active=2).       wake-lock semantics.           while netdev is UP.
────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
10. Sysmon SSR          Sysmon isolates WCNSS Wi-Fi from         Vanilla Linux broadcasts       CRITICAL: Modem reset
    Handling            Modem SSR restarts.                      SSR to WCNSS -> panics Wi-Fi.  causes local Wi-Fi crash.
========================================================================================================================
```

---

## 3. The 4 Root Causes of OpenWrt Instability vs. Stock Android

From our forensic analysis, the 4 specific mechanisms causing failure on OpenWrt are:

### Root Cause 1: SCLK Clock Drift at $T = 900\text{s}$
* **Android Solution**: `time_daemon` provides initial baseband ATS time anchor at boot.
* **OpenWrt Resolution**: Binary patch `FUN_c03987e0` (`lte_ml1_sleepmgr_cfg`) to return `-1` (bypasses the 900s DRX timer entirely) OR implement a lightweight QMI Service 22 daemon.

### Root Cause 2: BAM-DMUX 1-Second Autosuspend Flapping
* **Android Solution**: BAM DMA descriptors remain permanently active; link dormancy is handled at protocol layer.
* **OpenWrt Resolution**: In-kernel PM lock ([`808-bam-dmux-stats.patch`](file:///home/shaanair/Projects/msm8916-openwrt-clean/msm89xx/patches/808-bam-dmux-stats.patch)) holding `pm_runtime_resume_and_get()` while `wwan0` is UP.

### Root Cause 3: RMTFS NV Sync Rejection
* **Android Solution**: Direct read-write access to eMMC partitions.
* **OpenWrt Resolution**: `packages/rmtfs` running `-P -s` with `/dev/disk/by-partlabel/` symlinks.

### Root Cause 4: Hardware GPIO Pin Assertions
* **Android Solution**: Explicitly drives GPIO 119 HIGH, GPIO 114 LOW, GPIO 71 HIGH.
* **OpenWrt Resolution**: Configure `msm8916-generic-hmu05.dts` and `99-msm89xx-firstboot` to latch these exact pin states.
