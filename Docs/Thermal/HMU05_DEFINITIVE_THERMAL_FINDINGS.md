# HMU05: Definitive Thermal Findings & Empirical Ground Truth

**Date:** October 8, 2026  
**Hardware Platform:** HMU05 4G LTE USB Dongle (`hmu05,250605v0s`, Qualcomm MSM8916 / Snapdragon 410)  
**Cellular Operator:** Jio 4G (India, MCC/MNC `405/861`, Band 3/5/40)  
**Test Protocol:** Live side-by-side SSH inspection of physical device running:
1. **Stock ZTE Android 4.4 / Kernel 3.10.28** at `192.168.100.1`
2. **OpenWrt 24.10 / Kernel 6.12.94** at `192.168.8.1` (100% Read-Only)

---

> ## ⚠ CORRECTION (2026-10-09) — read before using this document
>
> Two headline claims below are **refuted** by the reconciled evidence in
> [HMU05_OPENWRT_ANDROID_THERMAL_ROOT_CAUSE.md](./HMU05_OPENWRT_ANDROID_THERMAL_ROOT_CAUSE.md):
>
> 1. **"Android collapses the A2; OpenWrt pins it" (§1 table, §2.1, §3.2, §4 Cause 1) —
>    REFUTED.** Live Android data (`android_idle.csv`) holds `a2_out=30`, `a2_in=28`,
>    `a2_pwr=30` **constant — 0 transitions** over the window ⇒ **Android keeps the A2 ON**,
>    the *same* state as OpenWrt's `modem-a2-hold` pin. The pin is therefore **parity, not a
>    deviation**, and cannot explain the OpenWrt−Android gap. The claimed "+10–12 °C A2 offset"
>    is **not supported**; the measured pinned−unpinned **idle** delta is ≈ **+2.8 °C**.
> 2. **"Lower the thermal trip to 60/65 °C" (§4, and the related report) — REFUTED as a fix.**
>    A controlled test (trip 75→60 °C) gave **0 °C benefit**, **−37 % rx throughput**, and a
>    permanent idle-throttle regression.
>
> **Also superseded:** the CPU-rail voltage gap described below (OpenWrt ~1.15 V vs Android
> 1.05 V) has since been **FIXED** — Qualcomm **CPR** was ported to mainline (patches 841–846);
> the rail now runs at **1.0625 V**, matching Android. See
> [HMU05_CPU_RAIL_CPR_PORT.md](./HMU05_CPU_RAIL_CPR_PORT.md).
>
> The sensor tables and load/cooldown profiles below remain useful **raw observations**; the
> *causal* attributions (especially the A2 pin) do not.

---

## 1. Executive Summary: The Three Operating Regimes

Live hardware measurements conclusively resolve the true thermal behavior of OpenWrt versus Stock Android on the HMU05. Rather than a flat, constant temperature offset, the thermal delta depends entirely on **which of the three operating regimes** the device is in:

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                             THE THREE THERMAL REGIMES                            │
├──────────────────────┬─────────────────────────────┬─────────────────────────────┤
│ 1. Disconnected Idle │ 2. Connected Idle (No Data) │ 3. Active LTE Data Load     │
│  • BAM-DMUX: Suspended│  • BAM-DMUX: Pinned Active  │  • 10 MB HTTP LTE Download  │
│  • Modem: Sleep      │  • Modem: Pinned Awake      │  • Continuous Packet DMA    │
│  • OWrt: 48°C / 46°C │  • OWrt: 59°C / 57°C        │  • OWrt: 63°C / 61°C        │
│  • Andr: 49°C / 47°C │  • Andr: 49°C / 47°C        │  • Andr: 51°C / 49°C        │
│  • Delta: 0°C PARITY │  • Delta: +10°C OWrt Shift  │  • Delta: +12°C OWrt Shift  │
└──────────────────────┴─────────────────────────────┴─────────────────────────────┘
```

---

## 2. Empirical Data Matrix

All readings were taken directly from the Qualcomm TSENS on-die thermal sensors and PM8916 PMIC hardware registers:

### 2.1 Complete Live Sensor Comparison Table

| Sensor Node | Monitored Silicon Domain | Regime 1: Disconnected Idle | Regime 2: Connected Idle (LTE Up) | Regime 3: Active LTE Load (10 MB Download) |
|---|---|:---:|:---:|:---:|
| **TSENS 0** | Modem Hexagon DSP / Baseband | **Android:** 49.0°C<br>**OpenWrt:** 48.0°C<br>*(Delta: -1.0°C)* | **Android:** 49.0°C<br>**OpenWrt:** 59.0°C<br>*(Delta: +10.0°C)* | **Android:** 51.0°C<br>**OpenWrt:** 63.0°C<br>*(Delta: +12.0°C)* |
| **TSENS 1** | Camera Interface / Die Top | **Android:** 45.0°C<br>**OpenWrt:** 45.0°C<br>*(Delta: 0.0°C)* | **Android:** 45.0°C<br>**OpenWrt:** 56.0°C<br>*(Delta: +11.0°C)* | **Android:** 47.0°C<br>**OpenWrt:** 60.0°C<br>*(Delta: +13.0°C)* |
| **TSENS 2** | Adreno 306 GPU | **Android:** 44.0°C<br>**OpenWrt:** 43.0°C<br>*(Delta: -1.0°C)* | **Android:** 44.0°C<br>**OpenWrt:** 55.0°C<br>*(Delta: +11.0°C)* | **Android:** 46.0°C<br>**OpenWrt:** 60.0°C<br>*(Delta: +14.0°C)* |
| **TSENS 3** | Cortex-A53 Cores 2 & 3 | **Android:** 45.0°C<br>**OpenWrt:** 44.0°C<br>*(Delta: -1.0°C)* | **Android:** 45.0°C<br>**OpenWrt:** 55.0°C<br>*(Delta: +10.0°C)* | **Android:** 47.0°C<br>**OpenWrt:** 59.0°C<br>*(Delta: +12.0°C)* |
| **TSENS 4** | Cortex-A53 Cores 0 & 1 | **Android:** 47.0°C<br>**OpenWrt:** 46.0°C<br>*(Delta: -1.0°C)* | **Android:** 47.0°C<br>**OpenWrt:** 57.0°C<br>*(Delta: +10.0°C)* | **Android:** 49.0°C<br>**OpenWrt:** 61.0°C<br>*(Delta: +12.0°C)* |
| **PMIC** | PM8916 Die Thermistor | **Android:** 39.2°C<br>**OpenWrt:** 37.4°C<br>*(Delta: -1.8°C)* | **Android:** 39.2°C<br>**OpenWrt:** 54.8°C<br>*(Delta: +15.6°C)* | **Android:** 39.5°C<br>**OpenWrt:** 59.9°C<br>*(Delta: +20.4°C)* |
| **PA Therm** | RF Power Amplifier (VADC) | **Android:** 36.0°C<br>**OpenWrt:** *(unexposed)* | **Android:** 36.0°C<br>**OpenWrt:** *(unexposed)* | **Android:** 37.0°C<br>**OpenWrt:** *(unexposed)* |

---

## 3. Cooldown Dynamics: Android vs OpenWrt Under Identical 10 MB LTE Load

Under the exact same 10 MB download over Jio 4G LTE (`speedtest.tele2.net`), we profiled the post-load dissipation curve and internal silicon states on both operating systems:

### 3.1 Empirical Side-by-Side Cooldown Profile

| Elapsed Time | Android Modem (TSENS 0) | Android CPU (TSENS 5) | Android PMIC | OpenWrt Modem (TSENS 0) | OpenWrt CPU (TSENS 4) | OpenWrt PMIC | Thermal Delta (Modem / CPU) |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **Pre-Test Baseline** | 49.0°C | 48.0°C | 39.9°C | 59.0°C | 57.0°C | 54.8°C | **+10.0°C / +9.0°C (OpenWrt)** |
| **T = 0s (Peak Load)** | **52.0°C** | **50.0°C** | **43.5°C** | **63.0°C** | **61.0°C** | **59.9°C** | **+11.0°C / +11.0°C (OpenWrt)** |
| **T = 30s Post-Load** | **49.0°C** *(-3°C)* | **47.0°C** *(-3°C)* | **40.0°C** *(-3.5°C)* | **62.0°C** *(-1°C)* | **59.0°C** *(-2°C)* | **56.3°C** *(-3.6°C)* | **+13.0°C / +12.0°C (OpenWrt)** |
| **T = 60s Post-Load** | **48.0°C** *(-4°C)* | **46.0°C** *(-4°C)* | **39.2°C** *(-4.3°C)* | **61.5°C** *(-1.5°C)* | **59.5°C** *(-1.5°C)* | **56.4°C** *(-3.5°C)* | **+13.5°C / +13.5°C (OpenWrt)** |
| **T = 90s Post-Load** | **48.0°C** *(Baseline)* | **46.0°C** *(Baseline)* | **38.7°C** *(Baseline)* | **61.0°C** *(FLOOR)* | **60.0°C** *(FLOOR)* | **56.5°C** *(FLOOR)* | **+13.0°C / +14.0°C (OpenWrt)** |

### 3.2 The Mechanistic Reason for the Divergence

1. **On Android:**
   * **Instant A2 Collapse:** The moment data packets stop flowing, `bam_dmux` executes the SMSM handshake (`a2 ack out cnt` advanced from 66 to 68; `a2 pwr cntl in` advanced to 68).
   * **Hexagon DSP Sleep:** `/sys/kernel/debug/rpm_master_stats` confirms the Modem Processor Subsystem (MPSS) transitioned to `active_cores: 0x0` within milliseconds, logging **+98 sleep shutdowns** during the 90s cooldown window.
   * **Clock Gating:** The Hexagon AXI bus clock (`gcc_mss_q6_bimc_axi_clk`) immediately drops to `rate = 0 Hz`.
   * **Result:** Total PCB power collapses immediately, restoring the cool **48.0°C baseline in under 60 seconds**.

2. **On OpenWrt:**
   * **Pinned DMA & Baseband:** the A2 pin ([patch 848](../../msm89xx/patches/848-bam-dmux-a2-pin.patch)) forces `control = on` on the BAM-DMUX remoteproc device (this replaced the older userspace `modem-a2-hold` helper).
   * **Hexagon DSP Pinned Awake:** The modem baseband is prevented from dropping `SMSM_A2_POWER_CONTROL`. The Hexagon DSP and BAM DMA engines run continuously at full power.
   * **Result:** Heat generated during the 10 MB download cannot fully dissipate because the modem is actively generating baseline power. The device halts cooling at **~59°C–61°C**, creating a permanent **+13°C gap** compared to Android.

---

## 4. Architectural Root Cause Analysis

### Cause 1: Modem A2 Domain Pinning (`modem-a2-hold`) — (+10°C Baseline Shift)
* **On Android:** Qualcomm's downstream `bam_dmux.c` uses runtime power management (`ul_powerdown()` / `ul_wakeup()`). When network traffic pauses, the AP clears its vote on `SMSM_A2_POWER_CONTROL`. The modem baseband (Hexagon DSP, BAM DMA engines, shared memory buses) collapses the A2 clock/power domain into low-power sleep.
* **On OpenWrt:** Cycling A2 power down and up triggers the `a2_power.c:1189` assertion crash in stock modem firmware. OpenWrt uses `/usr/sbin/modem-a2-hold` to force:
  ```sh
  echo on > /sys/devices/platform/soc@0/4080000.remoteproc/4080000.remoteproc:bam-dmux/power/control
  ```
* **Impact:** The modem baseband and its shared DMA engines run at full power 24/7, generating a continuous **~10°C thermal offset** across the entire PCB whenever a cellular connection is open.

### Cause 2: Missing CPU Core Voltage Scaling (CPR / VDD_APC) — (+3°C to +5°C Under Load)
* **On Android:** 
  * Qualcomm's Core Power Reduction (CPR) hardware module actively regulates `VDD_APC` supplied by PM8916 SMPS2 (`8916_s2`).
  * Live registers from `/sys/kernel/debug/cpr-regulator/apc_corner/debug_info` on this exact HMU05 hardware unit:
    ```
    corner = 4 (800 MHz), current_volt = 1050000 uV (1.05 V)
    fuse_corner = 2, rbcpr_gcnt_target = 0x13346
    pvs voltage: [1050000 1100000 1250000] uV
    ceiling voltage: [1050000 1150000 1350000] uV
    floor voltage:   [1050000 1050000 1162500] uV
    ```
  * For all operating frequencies up to 800 MHz (corners 1–4: 200, 400, 533, 800 MHz), the CPU core voltage is binned by Qualcomm silicon efuses (QFPROM rows 0 and 27) to run strictly at **1.05 V**.
* **On OpenWrt:** 
  * In mainline `arch/arm64/boot/dts/qcom/msm8916.dtsi`, there is no `cpu-supply` property attached to the CPU nodes, and no `opp-microvolt` values in `opp-table-cpu`.
  * The CPU frequency governor switches frequencies dynamically, but the PM8916 SMPS2 buck converter remains locked at the default bootloader output voltage (**~1.20 V–1.25 V**).
* **Impact:** Dynamic switching power dissipation scales quadratically with voltage ($P \propto V^2$):
  $$\frac{(1.20\text{ V})^2}{(1.05\text{ V})^2} \approx 1.306 \implies \mathbf{+30.6\%\text{ higher dynamic power dissipation on OpenWrt}}$$
  When processing network packets at 800 MHz, OpenWrt produces substantially more heat per packet.

### Cause 3: Disparate Thermal Mitigation Trip Points
* **On Android:** Kernel DTS sets passive throttling at **60°C** (`qcom,limit-temp = <60>`). Userspace `thermal-engine` monitors GPU at 55°C and WLAN at 60°C.
* **On OpenWrt:** Mainline DTS sets passive throttling at **75°C** (`cpu0_1_alert0`).
* **Impact:** Between 55°C and 75°C, OpenWrt applies **zero thermal limits**, allowing heat to accumulate freely during sustained network transfers until 75°C is reached.

---

## 5. What Was Proven FALSE (Busted Myths)

1. **Myth 1: "OpenWrt is always 15°C hotter, even at idle."**
   * **Busted:** At disconnected idle, OpenWrt is **48.0°C vs. Android's 49.0°C** (exact parity).
2. **Myth 2: "OpenWrt has no CPU idle and only does WFI."**
   * **Busted:** Live telemetry shows OpenWrt spends **97.9% of idle time in `cpu-sleep-0` (standalone-power-collapse)** via PSCI OSI mode.
3. **Myth 3: "Android uses aggressive thermal-engine throttling to stay cold."**
   * **Busted:** In Android, `msm_thermal` is disabled at boot (`enabled = 0`), and `thermal-engine` recorded 0 mitigation events. Android stays cool naturally due to 1.05 V core voltage and A2 power collapse.
4. **Myth 4: "TSENS calibration is broken on mainline Linux."**
   * **Busted:** Both OSes read identical QFPROM fuses and compute matching slopes and offsets.

---

## 6. Technical Remediation Plan

To permanently bring OpenWrt's operating temperatures down to Android levels:

### Step 1: Resolve the `a2_power.c:1189` Quiesce Bug (Target: -10°C)
* **Goal:** Enable BAM-DMUX runtime suspend (`control=auto`) so the modem baseband sleeps between packet bursts.
* **Implementation:** Fix the down-ack serialisation handshake in `kmod-bam-dmux` (tracked in `Docs/Modem Stability/Modem RE/hmu05/A2_QUIESCE_ASYMMETRY.md`), allowing removal of `/usr/sbin/modem-a2-hold`.

### Step 2: Wire the 1.05 V SMPS2 Regulator in Device Tree (Target: -3°C to -5°C Under Load)
* **Goal:** Lower CPU core voltage from bootloader default (~1.20 V) to 1.05 V.
* **Implementation:** In `msm89xx/patches/805-arm64-dts-qcom-add-msm8916-generic-hmu05.patch`, bind `cpu-supply = <&pm8916_s2>` and add `opp-microvolt = <1050000>` across the 200/400/800 MHz OPP table.

### Step 3: Lower Passive Thermal Trip Point to 65°C
* **Goal:** Prevent long sustained LTE downloads from pushing die temperatures into the 70°C+ range.
* **Implementation:** Override `cpu0_1_alert0` in `msm8916-generic-hmu05.dts` to set `temperature = <65000>`.
