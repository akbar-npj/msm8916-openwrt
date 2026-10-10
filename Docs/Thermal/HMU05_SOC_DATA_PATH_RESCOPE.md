# HMU05: SoC data-path thermal work — re-scope

**Date:** 2026-10-09
**Hardware:** HMU05 4G LTE stick (`hmu05,250605v0s`, Qualcomm MSM8916)
**Branch:** `test/thermal`

---

## Why this was re-scoped

The earlier "the load gap is a SoC data-path / modem-DSP power difference" framing leaned heavily
on **one hypothesis — the A2 pin-hold** — which has since been **refuted** (see below). Separately,
the one clean *measured* cause (the CPU rail, cause #3) has now been **fixed**. So the data-path
work needs a fresh, evidence-based scope rather than the old one.

---

## What is now settled (do NOT re-litigate)

| Claim | Verdict | Evidence |
|---|---|---|
| **Android collapses the A2; OpenWrt pins it → +10–12 °C** | **REFUTED — parity** | `android_idle.csv`: `a2_out/a2_in/a2_pwr` **constant, 0 transitions** ⇒ Android keeps the A2 **ON** = OpenWrt's pin. Measured pinned−unpinned **idle** delta ≈ **+2.8 °C**. ([ROOT_CAUSE](./HMU05_OPENWRT_ANDROID_THERMAL_ROOT_CAUSE.md)) |
| **Wi-Fi is disabled on Android** | **REFUTED — parity** | Android `wlan0` UP + SoftAP bridge; both firmwares run Wi-Fi. |
| **Android's thermal-engine throttles the PA/modem** | **Unsupported** | No `thermal-engine.conf` (compiled-in default); `fusion` (modem QMI) fires only at **105 °C** ⇒ inactive at ~62 °C. |
| **Lower the trip point (75 → 60/65 °C)** | **REFUTED as a fix** | Controlled test: **0 °C benefit**, **−37 % rx**, permanent idle-throttle regression. |
| **CPU rail fixed at ~1.15 V (cause #3)** | **FIXED** | Qualcomm **CPR** ported (patches 841–846); rail now **1.0625 V** == Android. ([CPU_RAIL_CPR_PORT](./HMU05_CPU_RAIL_CPR_PORT.md)) |
| **DDR/interconnect scaling from the AP** | **CLOSED (inert)** | `bimc_ddr_clk_src` has no freq_tbl / is RPM-only; Android has no such clock. |

**Consequence:** the two "big" load-gap hypotheses (A2 pin, PA/modem throttle) are both dead, and
the one clean signal (the rail) is fixed. **We do not currently know how much of the load gap
remains.**

---

## The confound that must be controlled first

Every prior OpenWrt-vs-Android *load* comparison carried an uncontrolled difference:
**LTE throughput differed ~2×** (Android ≈ 0.59 MB/s vs OpenWrt ≈ 1.15 MB/s). More bytes/s = more
radio/DSP/CPU work = more heat. **Any load A/B that does not equalise throughput is uninterpretable.**

Other uncontrolled variables across past runs: **ambient temperature**, **LTE band/signal (RSRP/SINR)**,
and **sampling window vs. thermal steady state**.

---

## Re-scoped plan

### Step 0 — Re-measure the residual gap (post-CPR)

**Question:** now that the rail is 1.0625 V (Android parity), how big is the OpenWrt−Android load gap?

**Protocol (pre-registered):**
1. Same physical HMU05, flash Android → measure → flash OpenWrt → measure (or use the two saved dumps).
2. **Equalise throughput** — rate-limit the download to a fixed rate on both (e.g. 0.5 MB/s), or
   download the *same* fixed-size payload over the *same* window.
3. **Record and match**: ambient, LTE band + RSRP/SINR, CPU freq, all 6 TSENS, PMIC, and the CPU rail.
4. Hold until **steady state** (no ≥0.5 °C change over 60 s), then compare means.
5. n ≥ 3 runs per firmware.

**Decision rule:** if the steady-state gap at matched throughput/ambient is **≤ ~3 °C** on the SoC
sensors, the gap is effectively closed (rail was the cause) → **stop**. If a gap remains, go to Step 1.

### Step 1 — Isolate the survivors (only if Step 0 shows a real gap)

Only these candidates survive; test in this order (cheapest/cleanest first):

| Candidate | Test | Why it might matter |
|---|---|---|
| **GPU policy** | Is the Adreno GPU even active on a headless stick? Check `gpu` clock + `gpu-thermal` under load on both. Android's thermal-engine limits GPU at **55 °C**. | Only matters if the GPU runs. Likely **inert** (headless). |
| **WLAN policy** | Compare Wi-Fi TX power / rate and the `wlan` thermal path on both. Android limits WLAN at **60 °C**. | Only matters when Wi-Fi is carrying traffic. |
| **Radio/TX conditions** | At **matched throughput**, compare MCS / TX power / band / RSRP-SINR. | The old "gap" may be partly the 2× throughput difference. |
| **Modem DSP activity** | At matched throughput, compare modem-side activity counters (not the A2 domain state — that is parity). | If the DSP does more work per byte on OpenWrt, that is a real lead. |

**Not candidates anymore:** the A2 pin, Wi-Fi on/off, PA/modem thermal-engine mitigation, trip points.

---

## Honest expectation

There is a real chance that **Step 0 shows the gap is largely closed** — because:
- the one clean controlled signal (pure-CPU load ⇒ +4 °C SoC / +16 °C PMIC at equal frequency) was
  attributed to the **rail**, which is now fixed, and
- the remaining "load gap" numbers were all taken with a **2× throughput confound** and an
  uncontrolled ambient.

If that happens, the correct conclusion is **"cause #3 was the fix; the rest was measurement
artefact"** — and the thermal workstream closes. We should be willing to reach that conclusion.

---

## SOP statement

Read-only investigation and a pre-registered protocol. Every claim above is sourced from the
tracked trees, the stored CSVs, or live device reads; the eliminated hypotheses each carry the
specific counter-evidence. Step 0 **must** control throughput and ambient or it is not
interpretable — this is the trap that produced the retracted A2 claim.
