# Porting Android's thermal control to OpenWrt — lever-by-lever result (HMU05)

**Question:** *"How can we mimic Android thermal control in OpenWrt?"* (prior thermal tests
had "little success").

**Answer (TL;DR):** You can port Android's CPU thermal levers almost 1:1 — but **they do not
cool the board**, and one of them makes things worse. The board is **~15 °C hotter than
Android on every sensor, uniformly, and already at idle** (before any CPU load). CPU
frequency is a *consequence* of that heat (the CPU throttles in response to it), not the
cause. Android's thermal trip points are calibrated against Android's cooler baseline; on
OpenWrt's hotter baseline the same absolute trip point throttles the CPU *permanently*,
including at idle.

**Verdict:** the CPU-thermal-control axis is **closed**. The excess heat is systemic
(baseline/idle), consistent with the still-open DDR/fabric/power-rail lead — *not* with a
missing thermal governor.

---

## 1. What Android actually configures on MSM8916

Android's `msm_thermal` (`drivers/thermal/msm_thermal.c`, 4118 lines, `CONFIG_THERMAL_MONITOR`)
polls every 250 ms and runs a battery of `do_*` functions. On MSM8916 **only three are
configured** in the DTS; the rest are compiled in but no-ops:

| Android lever | Configured on MSM8916? | What it does |
|---|---|---|
| `do_freq_control` | **yes** — `limit-temp=60`, step 2, mask `0xf` | caps CPU max freq at 60 °C |
| `do_core_control` | **yes** — `core-limit-temp=80`, mask `0xe` | offlines CPU1-3 at 80 °C |
| `do_vdd_restriction` | **yes** — cold protection (<5 °C) | raises VDD_DIG + CPU min-freq floor (COLD, not heat) |
| `do_psm` | no | regulator power-save mode |
| `do_ocr`, `do_vdd_mx`, `do_gfx_phase_cond`, `do_cx_phase_cond`, `do_therm_reset` | no | — |

OpenWrt's mainline equivalent is the DT `thermal-zones` + `step_wise` governor +
`cpufreq_cooling`. So the portable surface is small.

---

## 2. Lever-by-lever port + A/B result

All A/B runs: sustained LTE download (bounded `wget` loop), sampler
`scratch/thermal_baseline/thermal_sample.sh` (6 zones + per-core freq + cooling state +
bam-dmux telemetry), ~120 samples @ 2 s. CSVs in `scratch/thermal_baseline/`.

### Lever 1 — `do_freq_control`: lower CPU trip 75 °C → 60 °C

**Patch:** `837-arm64-dts-qcom-msm8916-cpu-thermal-trip-60c.patch`
(`cpu0-1-thermal` / `cpu2-3-thermal` `trip-point0` 75000 → 60000, hysteresis 2 °C kept).

| run | peak modem | mean modem | CPU freq dist (load) | throughput rx/tx |
|---|---|---|---|---|
| control (trip 75) | **76 °C** | 71.0 | 800M×98, 400M×15, 200M×2, 998M×5 | 1738 / 513 per s |
| Lever 1 (trip 60) | **76 °C** | 69.6 | 200M×52, 400M×53, 800M×14 | **1098 / 429 per s** |

**Result: no thermal benefit (peak identical), large throughput cost (−37 % rx, −16 % tx).**
The CPU was pinned to 200–400 MHz for 105/120 samples and the board still reached 76 °C.

**Why the peak is identical — the decisive observation.** Within *both* runs, the
**low-frequency samples are the hottest**:

```
control:  f0=800MHz  modem 70.4 °C (n=98) | f0=400MHz modem 74.9 °C (n=15) | f0=200MHz modem 76.0 °C (n=2)
Lever1:   f0=800MHz  modem 61.6 °C (n=14) | f0=400MHz modem 70.9 °C (n=53) | f0=200MHz modem 70.5 °C (n=52)
```

The CPU throttles **because** the board is hot. Frequency tracks heat as an *effect*; forcing
it down does not remove the heat.

**Second-order effect — the idle-throttle regression.** OpenWrt **idles at ~60 °C**
(`cpu0-1-thermal` 55–61 °C, `modem-thermal` 61–64 °C at true idle). A 60 °C trip with 2 °C
hysteresis therefore sits *below* the idle temperature: once the board is warm the CPU is
held at `cooling_device0 cur_state = 3` → **200 MHz even at idle**, where the control
(trip 75) sits at 800 MHz with `cur_state = 0`. Android does **not** do this because Android
idles ~15 °C cooler (see §3).

### Lever 2 — cap max CPU OPP to 800 MHz (Android max)

**Patch:** `838-arm64-dts-qcom-msm8916-cpu-opp-max-800mhz.patch` (removes `opp-998400000`).

**Result: provably inert (measured).** Android's OPP table is {200, 400, 533.333, 800} — no
998.4 — so capping is *parity*. But under load the control already ran 800 MHz for **98/120**
samples; the 5 samples at 998 MHz were the **coolest** of the whole run (69.4 °C mean vs
70.4 °C at 800). Removing them cannot lower the peak.

Measured A/B (image 834+837+**838**, `owrt_load_lever2.csv`):

| run | peak modem | mean modem | cool0 max | CPU freq dist (load) | rx/tx per s |
|---|---|---|---|---|---|
| control (837, has 998) | 76 | 69.6 | 3 | 200M×52, 400M×53, 800M×14, 998M×1 | 1098 / 429 |
| +838 (no 998) | **75** | 70.0 | 2 | 200M×59, 400M×59, 800M×2 | 1011 / 386 |

Peak 75 vs 76 °C = ≤1 °C, **within run-to-run noise** (control and Lever-1 both peaked at 76).
Confirmed live: `scaling_available_frequencies = 200000 400000 800000` (no 998400). With
Lever 1 active the CPU never reaches 800 under load anyway (throttled to ≤400 for 118/120
samples), so Lever 2 is doubly masked. **Harmless parity change, no thermal effect.**

### Lever 2b — `do_core_control` (offline CPU1-3 at 80 °C) — not in the approved plan

Android's second configured lever offlines cores 1-3 at 80 °C. OpenWrt has `CONFIG_CPU_THERMAL=y`
and `CONFIG_HOTPLUG_CPU=y`, but the **only cooling device is `cpufreq-cpu0`** — mainline has no
CPU-**hotplug** cooling device (Android's `core_control` is a downstream mechanism). Not
portable via DT; and given the CPU axis is inert (below), it would not help. **Note the peak is
76 °C, below the 80 °C trigger — it would never fire under this load anyway.**

### Lever 3 — CPU OPP voltage scaling (`required-opps` → rpmpd VDDCX)

**Result: NOT portable via DT.** Verified against the built tree:

- `arch/arm64/.../msm8916.dtsi` CPU OPP table has **no `opp-microvolt`** and there is **no
  `cpu-supply`** — the CPU rail is not modelled at all (mainline omits `pm8916_s2`; Android's
  rail is the downstream `spm-regulator` + `qcom,cpr-regulator`, neither in mainline).
- A bare `required-opps` in the CPU OPP table is a **provable no-op**:
  `drivers/opp/core.c:1117` `_set_required_opps()` returns immediately when
  `opp_table->required_devs == NULL`, and `required_devs` is only populated by
  `devm_pm_opp_attach_genpd()` / `dev_pm_domain_attach_by_name()` — **`cpufreq-dt.c` contains
  zero genpd/pm-domain references**. So the `_set_opp_level() → dev_pm_domain_set_performance_state()
  → rpmpd_set_performance()` path is never reached for the CPU.
- The CPU nodes already carry `power-domains = <&CPU_PDn>` (PSCI) with
  `power-domain-names = "psci"`; a CPU has one power domain, and DT-only cannot add rpmpd.

Making this work needs a **kernel driver change** (wire `cpufreq-dt` to rpmpd) plus a voltage
map — medium risk (bad map ⇒ CPU hang, EDL recovery). Out of scope for a thermal quick-win.

### Lever 4 — `do_psm` (regulator power-save mode)

**Result: NOT portable, and Android doesn't use it on MSM8916.** Mainline
`drivers/regulator/qcom_smd-regulator.c` exposes `.set_load` but **no `.set_mode`** op, so
there is no regulator-mode interface. Android's `do_psm` uses the downstream
`rpm_regulator_set_mode()` and is **not configured** in the MSM8916 DTS (no `psm-*`
properties) — a no-op even on Android. **Skip.**

### Lever 5 — DDR / fabric scaling (the final lever)

**Result: closed.** See the dedicated audit:
- The ICC vote *is* sent to the RPM (`bimc_clk` = `{QCOM_SMD_RPM_MEM_CLK, 0}`,
  `qcom_icc_rpm_set_bus_rate()`), but RPM does not act on it — thermally inert (C-3, Δ≤0.3 °C).
- `bimc_ddr_clk_src` is an **AP-side** RCG (GCC 0x32004, no freq table ⇒ `clk_set_rate`
  returns `-EINVAL`); patches 835/836 proved the AP cannot durably move it. Android has **no
  such clock** — DDR/BIMC is RPM-only.

The DDR/fabric-scaling-from-AP route is **closed**. The remaining systemic lead is the
PMIC/rail power difference, not a missing AP clock.

---

## 3. Root finding — the baseline, not the governor

Direct Android↔OpenWrt A/B (`scratch/thermal_baseline/`, same silicon):

| tsens sensor | OpenWrt peak | Android peak | Δ |
|---|---|---|---|
| 0 modem | 77 | 62 | **+15** |
| 1 camera | 74 | 58 | +16 |
| 2 gpu | 73 | 58 | +15 |
| 4 cpu23 | 73 | 58 | +15 |
| 5 cpu01 | 76 | 60 | +16 |
| pm8916 | 67 | 53 | +14 |

- The gap is **uniform across every sensor** and is present **at idle**
  (OpenWrt idle: modem 61–64 °C; Android idle: tsens 53–59 °C, pm8916 44–47 °C).
- A uniform offset that exists before any load is **system-level power**, not a CPU-thermal-
  policy difference. (Consistent with Android voting DDR/fabric bandwidth via `msm_bus` — 107
  clients — while OpenWrt's ICC path is inert.)

**Consequence for trip points.** Android's 60 °C CPU trip ≈ *Android's idle ceiling*. On
OpenWrt the same 60 °C sits *at/below* the idle ceiling → permanent throttle. OpenWrt's
**original 75 °C trip is already the baseline-corrected equivalent** of Android's 60 °C
(60 + Δ15 ≈ 75). Lowering it to 60 was the wrong direction.

---

## 4. Recommendation

1. **Do not ship Lever 1 as-is** (`trip = 60`): zero thermal benefit, −37 % throughput, and a
   permanent idle-throttle regression. Either **revert 837** or, if earlier throttling is
   wanted, set the trip *above* the idle baseline (≥ 70 °C) — but expect ≈0 °C benefit either
   way. **Note:** OpenWrt's *original* 75 °C trip is already the baseline-corrected equivalent
   of Android's 60 °C (60 + Δ15 ≈ 75), so the correct "port" of `do_freq_control` is **no
   change at all**.
2. **Lever 2 (cap 800 MHz)** is harmless parity; ship or drop, no thermal effect.
3. **Levers 3–5** are closed as documented above — no further CPU-side work.
4. **The real work is the baseline.** The board runs +15 °C uniformly from idle; chase the
   system-level power difference (PMIC rails / DDR-fabric power), not the thermal governor.
   Tie-in: parked **#367** (why Android is immune to `a2_power.c:1189`) and the A2-pin /
   thermal / crash chain.

**Decision (2026-10-08):** Lever 1 was **reverted** (patch 837 moved to
`scratch/thermal_baseline/rejected/` for reproducibility); the shipped image is **834 + 838**
(Android's 800 MHz OPP parity, harmless). The Lever-1 idle-throttle regression was observed
live on the parity image (at 59 °C the CPU sat at `cooling_device0 cur_state = 1` / 400 MHz,
where the unpatched control sits at `cur_state = 0` / 800 MHz), so the harmful lever was
removed. Verified live on the shipped image (boot_id `c2fd6ceb…`):
`scaling_available_frequencies = 200000 400000 800000`, `cooling_device0 max_state = 2`,
`cpu0-1-thermal / cpu2-3-thermal trip0 = 75000` (original), and at 55 °C the CPU sits at
`cur_state = 0` / 800 MHz — no idle throttle.

## 5. Artifacts

| Item | Path |
|---|---|
| Lever 1 patch | `msm89xx/patches/837-arm64-dts-qcom-msm8916-cpu-thermal-trip-60c.patch` |
| Lever 2 patch | `msm89xx/patches/838-arm64-dts-qcom-msm8916-cpu-opp-max-800mhz.patch` |
| A/B data | `scratch/thermal_baseline/owrt_load_{control,lever1,lever1b,lever2}.csv`, `android_{idle,load}.csv` |
| Sampler / driver | `scratch/thermal_baseline/{thermal_sample.sh,run_load_test.sh}` |
| Lever 3 code proof | `drivers/opp/core.c:1117`, `drivers/cpufreq/cpufreq-dt.c` (no genpd refs) |
| Lever 4 code proof | `drivers/regulator/qcom_smd-regulator.c` (`.set_load`, no `.set_mode`) |
| Lever 5 | `msm89xx/patches/835,836` (removed) — DDR AP-clock route closed |

## 6. SOP note

This is an AP-side (DTS/kernel) thermal exercise, **not a baseband port** — no modem firmware
was modified in this session, so the dual-firmware comparative SOP's Hexagon-patch/hash steps
do not apply. All claims are backed by either (a) the running kernel source in the build tree
(`drivers/opp/core.c:1117`, `drivers/cpufreq/cpufreq-dt.c`,
`drivers/regulator/qcom_smd-regulator.c`) or (b) measured A/B CSVs. The A/B runs used a fixed
sampler and a bounded load loop; the peak-vs-peak comparison is the pre-registered criterion.
Errors caught: the first Lever-1 patch (modem/pm8916 → CPU cooling maps) caused an idle
regression and was reverted before evaluation; a first-version `scaling_max_freq` runtime test
was inconclusive (board still hot) and was superseded by the within-run frequency/temperature
analysis.
