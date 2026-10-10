# Thermal & Power Management

These MSM8916 USB dongles run hot inside a small, unventilated enclosure, and the
baseband's behaviour is sensitive to the SoC power rails. Getting the thermal
policy and the CPU rail right is therefore both a **comfort/stability** concern
and (indirectly) a **modem-stability** concern.

This folder collects the HMU05 thermal investigation: the OpenWrt-vs-Android
comparison, the root-cause analysis, and the fixes that were ported.

**Hardware:** HMU05 4G LTE stick (`hmu05,250605v0s`, Qualcomm MSM8916 /
Snapdragon 410).
**Stacks compared:** stock Android 4.4 / Linux 3.10.28 vs. OpenWrt 25.12.5 /
mainline Linux 6.12.94.

---

## What we know (current conclusion)

- **The gap is load-dependent, not a fixed idle offset.** OpenWrt is genuinely
  hotter **under LTE load** (≈ **+12 °C** across five TSENS sensors), but the
  earlier "+15 °C *idle*" claim does **not** hold up: recomputed idle captures
  differ by only **+3–7 °C**, and every idle capture is a cooling/boot-warmup
  **transient**, not an equilibrated baseline.
- **The A2 "pin" is parity, not a cause.** Android keeps the A2 domain **on** at
  idle (its `a2_out/a2_in/a2_pwr` counters are constant), i.e. the *same* state as
  OpenWrt's A2 pin. So pinning the A2 is **not** a source of extra heat.
- **Android's thermal-engine does not throttle the modem/PA at observed
  temperatures.** The recovered compiled-in config's `fusion` mitigation is level
  0 at 55/60 °C and level 1 only at 105 °C; the LTE trace instead shows **GPU**
  limiting at 55 °C. This weakens the "Android throttles the modem/PA" hypothesis.
- **The one real, fixable cause found: the CPU rail.** Mainline had no dynamic
  voltage scaling (no `opp-microvolt`/`cpu-supply`, CPR unimplemented) while
  Android used `qcom,cpr-regulator`. Porting mainline **CPR** brought the rail to
  **1.0625 V — matching Android** (was ~1.15 V).

## What was shipped

| Item | Status | Where |
| :--- | :--- | :--- |
| **CPU rail (mainline CPR)** — 1.15 V → **1.0625 V**, Android parity | ✅ Done | Patches 841–846; [`HMU05_CPU_RAIL_CPR_PORT.md`](HMU05_CPU_RAIL_CPR_PORT.md) |
| **CPU OPP cap** — top 998.4 MHz OPP removed, CPUs ≤ **800 MHz** | ✅ Done | See root-cause report |
| **Wi-Fi + PMIC thermal mitigation** — `wcn36xx` cooling device + PMIC cooling map | ✅ Done | Patch 453 / 847; [README → Thermal & Power](../../README.md#-thermal--power-management) |

---

## Documents

### Start here (current)

| Doc | What it covers |
| :--- | :--- |
| [**HMU05_OPENWRT_ANDROID_THERMAL_ROOT_CAUSE.md**](HMU05_OPENWRT_ANDROID_THERMAL_ROOT_CAUSE.md) | **Authoritative.** Source-and-artifact audit of the OpenWrt 6.12.94 build tree + live Android policy/trace (160 s LTE trace + spot-checks). Refutes the +15 °C idle premise cause-by-cause and states the corrected finding. **Supersedes the baseline/root-cause claims in the earlier reports below.** |
| [**HMU05_CPU_RAIL_CPR_PORT.md**](HMU05_CPU_RAIL_CPR_PORT.md) | Porting Qualcomm **CPR** to mainline so the CPU rail scales with the silicon's fused speed bin. Result: rail at **1.0625 V == Android** (was fixed ~1.15 V). |
| [**HMU05_SOC_DATA_PATH_RESCOPE.md**](HMU05_SOC_DATA_PATH_RESCOPE.md) | Re-scope of the SoC data-path thermal work (2026-10-09): what remains, what is closed. |

### Historical (superseded in part — read with the correction banners)

| Doc | What it covers |
| :--- | :--- |
| [HMU05_ANDROID_THERMAL_CONTROL_PORT.md](HMU05_ANDROID_THERMAL_CONTROL_PORT.md) | Lever-by-lever attempt to mimic Android thermal control in OpenWrt (CPU trips, hotplug, undervolting). Its §3 / DDR-fabric inference is superseded; the **CPU-trip A/B observations remain useful**. |
| [HMU05_THERMAL_RESEARCH_AND_PROPOSAL.md](HMU05_THERMAL_RESEARCH_AND_PROPOSAL.md) | The earlier "+15 °C" investigation and its six ranked causes. Superseded on the idle-baseline and root-cause conclusions; retained for its source observations. |
| [HMU05_DEFINITIVE_THERMAL_FINDINGS.md](HMU05_DEFINITIVE_THERMAL_FINDINGS.md) | Side-by-side SSH ground-truth findings; the "+13 °C gap" claim is now qualified as **load-dependent**. |
| [HMU05_LIVE_THERMAL_FINDINGS_AND_PROPOSAL.md](HMU05_LIVE_THERMAL_FINDINGS_AND_PROPOSAL.md) | Early live Android findings and an OpenWrt mitigation proposal. |
| [HMU05_LIVE_OPENWRT_VS_ANDROID_COMPARISON.md](HMU05_LIVE_OPENWRT_VS_ANDROID_COMPARISON.md) | Early live OpenWrt-vs-Android ground-truth comparison. |

> The root-cause report supersedes the earlier idle-baseline and root-cause
> conclusions; the historical reports are kept for their raw observations and
> CPU-trip experiments, **not** for their +15 °C idle premise.

---

## Evidence & related material

- **Raw captures / samplers:** [`scratch/thermal_baseline/`](../../scratch/thermal_baseline/)
  (`android_idle.csv`, `owrt_load_control.csv`, `thermal_sample.sh`, …).
- **Android thermal-engine recovery:** the daemon runs on a **compiled-in** default
  (there is **no** `/system/etc/thermal-engine.conf`); the effective config was
  dumped with `thermal-engine -o` and reverse-engineered.
- **A2 quiesce fault (modem side):** a separate modem-stability task —
  [`Docs/Modem Stability/Modem RE/hmu05/A2_QUIESCE_ASYMMETRY.md`](../Modem%20Stability/Modem%20RE/hmu05/A2_QUIESCE_ASYMMETRY.md).
- **Main README:** [🌡️ Thermal & Power Management](../../README.md#-thermal--power-management).
