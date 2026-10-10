# HMU05: Live OpenWrt vs. Android Ground Truth Comparison

**Date:** October 2026  
**Hardware:** HMU05 4G Modem Stick (`hmu05,250605v0s`, Qualcomm MSM8916)  
**Devices Inspected:**
* **Stock Android 4.4 / Linux 3.10.28:** Live SSH at `192.168.100.1` (Jio 4G LTE connected)
* **OpenWrt 24.10 / Linux 6.12.94:** Live SSH at `192.168.8.1` (Jio 4G LTE connected via `wwan0`, 100% Read-Only Inspection)

> **Note (2026-10-10) — the A2 pin is now in the driver, not userspace.** The
> userspace `/usr/sbin/modem-a2-hold` helper referenced below was **removed**. The A2
> pin is now held by the `qcom_bam_dmux` driver on boards whose device tree sets
> `qcom,a2-pin` (HMU05; patches 848/849); toggle it with
> `echo 0|1 > /sys/module/qcom_bam_dmux/parameters/a2_pin`. References to
> `modem-a2-hold` below describe the earlier userspace mechanism.

---

> ## ⚠ CORRECTION (2026-10-09) — read before using this document
>
> This document's **central claim is refuted.** It asserts (§1 table, §2.1, §3) that the gap is
> caused by OpenWrt pinning the modem A2 while *Android lets it collapse*. Live Android data
> (`android_idle.csv`) holds `a2_out=30`, `a2_in=28`, `a2_pwr=30` **constant — 0 transitions**
> ⇒ **Android keeps the A2 ON**, the *same* state as OpenWrt's `modem-a2-hold` pin. The pin is
> **parity, not a deviation** and cannot explain the OpenWrt−Android gap. The claimed
> **"+10–12 °C A2 offset" is not supported**; the measured pinned−unpinned **idle** delta is
> ≈ **+2.8 °C**. See the reconciliation in
> [HMU05_OPENWRT_ANDROID_THERMAL_ROOT_CAUSE.md](./HMU05_OPENWRT_ANDROID_THERMAL_ROOT_CAUSE.md).
>
> **Also superseded in this document:**
> - **§2.2 / §3 "Unscaled CPU Voltage" — FIXED.** The rail gap (1.15 V vs Android 1.05 V) is
>   closed: Qualcomm **CPR** was ported (patches 841–846) and the rail now runs at **1.0625 V**.
>   The proposed "wire `cpu-supply` + `opp-microvolt`" fix below is **wrong** — it boot-looped
>   (patch 840); the rail is CPR-managed. See
>   [HMU05_CPU_RAIL_CPR_PORT.md](./HMU05_CPU_RAIL_CPR_PORT.md).
> - **§3 "Lower the trip to 65 °C" — REFUTED as a fix** (0 °C benefit, −37 % rx, idle-throttle
>   regression).
>
> The measured sensor numbers remain useful **raw observations**; the causal story does not.

---

## 1. Executive Summary & The Ground Truth Matrix

Through controlled live hardware tests across three operating regimes, the thermal behavior between OpenWrt and Stock Android is now completely understood and proven:

```
[Regime 1: Disconnected Idle] ──► [Regime 2: Connected Idle] ──► [Regime 3: Active LTE Load]
   • BAM-DMUX: suspended             • BAM-DMUX: active (pinned)     • Continuous LTE transfer
   • Delta: 0°C (Exact Parity!)      • Delta: +10°C to +12°C         • Delta: +12°C to +14°C
```

### The Three Operating Regimes Compared

| Operating Regime | TSENS Channel | Android (`192.168.100.1`) | OpenWrt (`192.168.8.1`) | Live Delta (OWrt - Android) |
|---|---|:---:|:---:|:---:|
| **1. Disconnected Idle**<br>*(Modem suspended)* | Modem (TSENS 0)<br>CPU0–1 (TSENS 4)<br>PMIC Die | 49.0°C<br>47.0°C<br>39.2°C | **48.0°C**<br>**46.0°C**<br>**37.4°C** | **-1.0°C**<br>**-1.0°C**<br>**-1.8°C** *(Exact Parity)* |
| **2. Connected Idle**<br>*(LTE attached, 0 traffic)* | Modem (TSENS 0)<br>CPU0–1 (TSENS 4)<br>PMIC Die | 49.0°C<br>47.0°C<br>39.2°C | **59.0°C**<br>**57.0°C**<br>**54.8°C** | **+10.0°C**<br>**+10.0°C**<br>**+15.6°C** |
| **3. Active LTE Load**<br>*(10 MB Download)* | Modem (TSENS 0)<br>CPU0–1 (TSENS 4)<br>PMIC Die | 51.0°C<br>49.0°C<br>39.5°C | **63.0°C**<br>**61.0°C**<br>**59.9°C** | **+12.0°C**<br>**+12.0°C**<br>**+20.4°C** |

---

## 2. Key Insights from the Re-Run Test (Modem Connected)

### 2.1 The Critical Role of BAM-DMUX & A2 Power State
1. **When BAM-DMUX is Suspended (Regime 1):**  
   Before the cellular connection was brought up, BAM-DMUX runtime status was `suspended` with power control in `auto`. In this state, OpenWrt ran at **48.0°C modem / 46.0°C CPU / 37.4°C PMIC**, showing **complete thermal parity with Android**.
2. **When the Modem is Connected (Regimes 2 & 3):**  
   Once `wwan0` connected to Jio 4G, BAM-DMUX power status shifted to `control = on` and `runtime_status = active`.  
   Because the modem A2 domain is pinned awake 24/7 on OpenWrt to prevent the `a2_power.c:1189` crash, the modem Hexagon DSP, BAM DMA engines, and shared memory buses never enter low-power sleep between bursts. This immediately raises the idle floor from ~48°C to **~59°C** (+11°C jump).
3. **In Android:**  
   Android's downstream `bam_dmux.c` driver allows the A2 domain to collapse dynamically during idle pauses, keeping baseline temperature at **~49°C** even while registered on the LTE network.

---

### 2.2 CPU Voltage & Dynamic Power Dissipation Under Load
* **Android:** Live voltage rail `8916_s2` (VDD_APC) operates at **1,050,000 µV (1.05 V)**.
* **OpenWrt:** The mainline device tree does not bind `cpu-supply` to `PM8916 SMPS2`, leaving the CPU cores at the fixed bootloader default (~1.15 V–1.25 V).
* Under active download load (processing TCP/IP packets at 800 MHz):
  $$\frac{(1.20\text{ V})^2}{(1.05\text{ V})^2} \approx 1.30 \implies \mathbf{+30\%\text{ higher dynamic power dissipation on OpenWrt}}$$
  Combined with the pinned modem, this drives die temperatures up to **63°C** immediately after a short 10 MB transfer (cooling down very slowly to ~61°C after 90 seconds).

---

### 2.3 Thermal Mitigations and Trip Points
* **Android:** In `dmesg`, in-kernel thermal mitigation is disabled at boot (`msm_thermal:set_enabled enabled = 0`), and `thermal-engine` logged 0 mitigation events. Android is **not** throttling to stay cool under 10 MB load; it stays at 51°C purely through hardware power collapse and 1.05 V core rail.
* **OpenWrt:** Mainline `msm8916.dtsi` sets passive throttling to **75°C** (`cpu0_1_alert0`). Between 55°C and 75°C, OpenWrt applies no thermal limits, allowing load heat to accumulate freely.

---

## 3. Final Conclusion & Solution Path

| Issue Identified | Impact | Recommended Fix |
|---|---|---|
| **Modem A2 Domain Pinned Awake** | **+10°C to +12°C** shift when connected | Fix the `bam-dmux` down-ack serialisation bug in `kmod-bam-dmux` to allow runtime suspend without `a2_power.c:1189` crashes, removing the need for `control=on`. |
| **Unscaled CPU Voltage (1.20V vs 1.05V)** | **+3°C to +5°C** under CPU load | Wire `cpu-supply = <&pm8916_s2>` in DTS and add `opp-microvolt = <1050000>` to the 200/400/800 MHz OPP table. |
| **High Thermal Trip Points (75°C)** | Allows heat to build up to 75°C unmitigated | Lower passive trip threshold to **65°C** in DTS to cap peak sustained load temperatures. |
| **Wi-Fi TX Power (20 dBm default)** | +1°C to +2°C ambient heat in small casing | Lower default AP TX power to 14 dBm (25 mW). |
