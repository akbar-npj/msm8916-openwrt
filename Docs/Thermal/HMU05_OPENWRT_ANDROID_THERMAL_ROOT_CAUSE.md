# HMU05: OpenWrt vs Android thermal difference — source audit, live Android policy, and findings

**Date:** 2026-10-08
**Scope:** Compare the stored HMU05 OpenWrt and stock-Android measurements, the Android kernel named by the user, and the OpenWrt 6.12.94 build-tree thermal/DT implementation, and record live Android policy/trace evidence gathered on 2026-10-08. This began as a source-and-artifact audit; it now also includes read-only live device observations (a 160 s LTE load trace and point-in-time spot-checks).

**Source revisions:** Android kernel branch cm-11.0 at c5dd03a3; OpenWrt build-tree kernel release 6.12.94.

> **Note (2026-10-10) — the A2 pin is now in the driver, not userspace.** The
> userspace `/usr/sbin/modem-a2-hold` helper referenced below was **removed**. The A2
> pin is now held by the `qcom_bam_dmux` driver on boards whose device tree sets
> `qcom,a2-pin` (HMU05; patches 848/849); toggle it with
> `echo 0|1 > /sys/module/qcom_bam_dmux/parameters/a2_pin`. References to
> `modem-a2-hold` below describe the earlier userspace mechanism.

## Bottom line

The source comparison does **not** support a 15 °C error in OpenWrt's TSENS conversion. Android and OpenWrt use the same MSM8916 TSENS hardware sensor IDs, the same QFPROM calibration fields, the same 30/120 °C anchors, and effectively the same slope/intercept conversion. Unit normalization and integer rounding are far too small to explain a 15 °C delta.

The stored measurements show a **load-dependent** difference: comparing `android_load.csv` with `owrt_load_control.csv` (each ~4 min of LTE load), OpenWrt averages about **12 °C hotter** across the five matched TSENS sensors, with peak differences of **14–15 °C**. The saved idle runs instead differ by roughly **3–7 °C** depending on which OpenWrt capture is used; all saved idle captures cool over their sampling windows. Therefore the earlier report's claim of a settled, uniform +15 °C idle offset is not borne out by its raw CSVs.

The leading explanation to test was a **real power/thermal-management difference under modem load**, especially Android's Qualcomm thermal-engine path. That path has now been **partially resolved**: the device has **no `thermal-engine.conf`** — the daemon runs on a **compiled-in default** whose only modem mitigation (`fusion`, over QMI) fires at **105 °C**, i.e. it is inactive at the observed ~62 °C; its active actions at realistic temperatures are GPU (55 °C), WLAN (60 °C) and a CPU `ss` control (78–85 °C). So the modem-power-mitigation lead is **weakened**; the remaining load-gap candidates are the CPU/GPU/WLAN policy difference and radio/TX conditions. LTE signal/TX conditions and exact load rate were also not logged side-by-side.

**Recommended disposition:** do not add a blanket −15 °C offset, do not change TSENS calibration, and do not lower OpenWrt thermal trips as a temperature fix. The recovered Android policy and the LTE trace **rule out a modem/PA thermal-mitigation difference** as the load-gap cause (no `thermal-engine.conf`; `fusion` inactive below 105 °C; no runtime config push; no modem action observed), so do **not** build a modem-throttle equivalent. Run a controlled, steady-state paired A/B to isolate the remaining candidates — GPU/CPU policy, radio/TX conditions, and rail/power input — before implementing any change.

## What the stored data actually says

The comparison below uses the means and maxima of the five TSENS channels in android_load.csv (120 rows, uptime 578–829 s) and owrt_load_control.csv (120 rows, uptime 2045–2300 s). Sampling is nominally every two seconds, so each window is about 4.2 minutes. The capture windows have similar duration, but were not synchronized and are not proven to have identical LTE throughput, radio conditions, ambient temperature, or starting temperature.

| Hardware sensor / sampler name | Android mean / max | OpenWrt mean / max | Mean delta | Peak delta |
|---|---:|---:|---:|---:|
| 0 / modem | 59.25 / 62 °C | 71.03 / 76 °C | +11.78 °C | +14 °C |
| 1 / camera | 55.70 / 58 °C | 67.98 / 73 °C | +12.28 °C | +15 °C |
| 2 / GPU | 55.08 / 58 °C | 67.11 / 72 °C | +12.03 °C | +14 °C |
| 4 / CPU2–3 | 55.03 / 58 °C | 66.88 / 72 °C | +11.85 °C | +14 °C |
| 5 / CPU0–1 | 56.88 / 60 °C | 69.40 / 75 °C | +12.52 °C | +15 °C |

The near-uniform rise across all five die sensors points to a broad difference in heat generation or operating conditions, rather than one incorrectly labelled hot sensor. These are on-die TSENS readings, however—not a measurement of enclosure or skin temperature. No independent thermocouple/IR reference is in the captured artifacts.

Idle evidence is materially different:

| Capture | Sensor 0 mean | Sensor 5 mean | PMIC-channel mean | Notes |
|---|---:|---:|---:|---|
| android_idle.csv, 30 rows | 55.7 °C | 54.1 °C | 45.5 °C | Uptime 516–578 s; temperatures fall during capture |
| owrt_idle_unpinned.csv, 60 rows | 58.9 °C | 57.1 °C | 48.5 °C | Uptime 4600–4725 s; temperatures fall during capture |
| owrt_idle_pinned.csv, 30 rows | 61.7 °C | 59.8 °C | 51.9 °C | Uptime 4008–4070 s; not a paired run |

The Android-versus-unpinned OpenWrt means are about +2.7 to +3.6 °C across TSENS; Android versus the other OpenWrt idle file is about +5.2 to +7.0 °C. These are short cooling transients with no ambient/start-state log, not equilibrated idle baselines. They cannot substantiate +15 °C at idle.

**Measurement comparability caveats:** OpenWrt's run_load_test.sh documents its bounded Cloudflare download loop. android_thermal_sample2.sh is only a sampler; the collected files do not include an equivalent Android load runner, throughput, LTE band, RSRP/RSRQ, modem transmit power, or input power. The Android load CSV has a cumulative skb counter, but that is not a matched throughput record. The OpenWrt control also allows samples at 998.4 MHz, while the Android sample reports 800 MHz; an 800 MHz OpenWrt cap was later tested, but there is no paired Android run from the same test session. These differences prevent attributing the full thermal gap to one software policy.

The PMIC numbers should not be treated as an apples-to-apples temperature comparison. Android reports pm8916_tz with mode=disabled in the captured zone dump. The inspected OpenWrt MSM8916 DTS has no pm8916-thermal zone, although the OpenWrt sampler searches for one. The source/mapping of the CSV's pm8916 column therefore needs to be verified on the exact runtime image before using it as corroboration.

## TSENS source comparison

| Area | Android source | OpenWrt build source | Assessment |
|---|---|---|---|
| Sensor map | msm8916.dtsi declares five TSENS channels, slope 3200, IDs 0, 1, 2, 4, 5 | tsens-v0_1.c has the MSM8916 hardware-ID table {0,1,2,4,5}; board DTS supplies NVMEM cells for mode/base and the same five sensor calibration point pairs | Same physical channels and fuse data layout |
| Calibration | msm8974-tsens.c reads the MSM8916 QFPROM calibration words and extracts per-sensor point 1/2; anchor constants are 30 and 120 °C and scale factor is 1000 | tsens-v0_1.c calls tsens_calibrate_nvmem(priv, 3); tsens.c decodes the same MSM8916 bit fields and computes the same two-point slope/intercept | No source-level 15 °C offset identified |
| Temperature conversion | Converts ADC code with the per-sensor slope/intercept and rounds to integer °C | Uses the same integer °C conversion then returns millidegrees to thermal core | Unit/rounding can account for at most sub-degree/integer-degree differences, not 15 °C |
| Device-tree identity | Android TSENS compatible qcom,msm8916-tsens; sensor IDs are explicit | OpenWrt compatible qcom,msm8916-tsens plus qcom,tsens-v0_1; NVMEM cells are named per hardware sensor ID | Both DTs describe MSM8916 TSENS rather than different sensors |

This is a comparison of source and built DT descriptions. It does **not** yet prove that both runtime kernels selected the expected HMU05 DTB, successfully read the same actual fuse values, or report the same live effective coefficients. A runtime capture of the selected DTB, TSENS raw ADC values, and effective slope/intercept (or equivalent driver debug output) is the final calibration sanity check. Still, a calibration defect is now a lower-probability hypothesis than a real difference in board power or thermal policy.

Useful line anchors for review: Android MSM8916 TSENS DT is msm8916.dtsi:258–266; Android calibration is msm8974-tsens.c:1196–1316 and code-to-temperature conversion is at :590–608. The OpenWrt MSM8916 fuse bit layout is tsens-v0_1.c:26–45 and calibration entry point is :86–116; shared slope/intercept and conversion are tsens.c:259–348. OpenWrt's hardware sensor-ID table is tsens-v0_1.c:353–370, fuse cells/TSENS NVMEM mapping are msm8916.dtsi:480–578, and thermal zones are at :2640–2750.

The OpenWrt tree inspected is the built Linux 6.12.94 tree at GitIgnore/compare/openwrt/build_dir/target-aarch64_generic_musl/linux-msm89xx_msm8916/linux-6.12.94. Its MSM8916 DTS includes PSCI CPU idle and cluster idle states (msm8916.dtsi:202–228), and its build configuration enables CPU_IDLE and ARM_PSCI_CPUIDLE (.config:547, 558–559). Thus “OpenWrt has no deep CPU idle support” is not a valid source-level explanation. Actual residency and rail state still need measurement. Android uses its downstream msm_idle/power-collapse implementation; source support on either side does not prove the state was entered in the test.

## Thermal-policy difference and strongest lead

The Android DT's msm-thermal node polls every 250 ms and configures CPU frequency restriction on hardware sensor 5 at 60 °C, plus a core limit at 80 °C. The OpenWrt mainline DT maps CPU sensor 5 and sensor 4 to passive CPU cooling trips at 75 °C; the modem sensor 0 zone is a hot trip at 85 °C and has no modem cooling action. This is a real policy difference, but the saved Android load sample shows CPU current/max frequency at 800 MHz and does not record a clear downclock event. Earlier OpenWrt lever tests lowered the CPU trip to 60 °C and forced substantial CPU throttling; the modem peak remained 76 °C while RX throughput fell roughly 37%. That makes CPU trip tuning alone an unconvincing solution to the multi-sensor gap.

Android has an additional vendor path for which no equivalent was found in the inspected OpenWrt source artifacts:

- init.target.rc starts /system/bin/thermal-engine, and the captured Android properties say it was running.
- The captured lsof dump shows thermal-engine holding /sys/devices/soc.0/qpnp-vadc-de70d000/pa_therm0 open (the live device's node is `qpnp-vadc-de6e5000` — same PA channel, build-specific address; confirmed open on the live device as fd 29). Android DTS defines that PA thermistor as a PM8916 VADC channel.
- The local reverse-engineering notes for the stock thermal-engine binary contain the /system/etc/thermal-engine.conf path, TSENS/PMIC/pa_therm0 sensor names, QMI thermal service references, and log strings for modem mitigation success/failure.

**★ Config recovered (2026-10-08, live device).** There is **no `thermal-engine.conf` file** on
the device (nor in the stock `system.bin`) — `/system/etc/thermal-engine.conf` is absent, and the
daemon logs `open: No such file or directory` before starting on its **compiled-in default**. The
effective config was dumped with the binary's own `-o` (see
[scratch/thermal_baseline/android/](../../scratch/thermal_baseline/android/README.md)):

```
[SS-POPMEM]  ss  sensor pop_mem  set 78/clr 73 °C          -> cpu
[SS-CPU2-3]  ss  sensor cpu2-3   set 85/clr 55 °C          -> cpu
[SS-CPU0-1]  ss  sensor cpu0-1   set 85/clr 55 °C          -> cpu
[CX_MITIGATION_MONITOR_TSENS4] monitor sensor cpu0-1
             thresholds 55/60/105 °C  clr 50/55/101 °C
             actions fusion+gpu / fusion+gpu+wlan / gpu+wlan+fusion
             action_info 0+200000000 / 0+200000000+0 / 200000000+0+1
[CPU2-3_MONITOR] monitor cpu2-3  116/108 °C -> shutdown
[CPU0-1_MONITOR] monitor cpu0-1  116/108 °C -> shutdown
```

This materially changes the lead: the `fusion` action **is** modem thermal mitigation over QMI
(`ACTION: FUSION - Fusion modem mitigation succeeded for level %d`), but in this config its level
is **0 at 55/60 °C and 1 only at 105 °C** ⇒ **modem/PA mitigation is effectively inactive at the
observed board temperatures** (Android peak ≈ 62 °C). The config sets **GPU frequency limiting
(→200 MHz at 55 °C)** and a **WLAN action at 60 °C**; its CPU/pop_mem `ss` controls target
78–85 °C. Notably `pa_therm0` is opened by the daemon but is **not a monitored sensor** in the
active config, and the modem sensor `tsens_tz_sensor0` is not monitored at all.

So the "Android throttles the modem/PA from the PA thermistor" hypothesis is **not supported by the
recovered config**. A **160 s live trace under LTE load** (2026-10-08) confirms it: CPU0–1 peaked at
only 58 °C, so the **only action that fired was GPU limiting (310→200 MHz at 55 °C)** — WLAN, CPU
`ss`, and modem `fusion` never triggered. The trace also shows **no runtime config push** (no
`Config set` activity, no client on the thermal client sockets), so the compiled-in default is what
runs. Radio/TX conditions and power-input/rail-power differences remain unmeasured alternatives.

**Additional live spot-check (2026-10-08 09:10 UTC, read-only SSH to `192.168.100.1`).** Both
`thermal-engine` (PID 208) and `mpdecision` (PID 1201) were running, while `/sys/devices/system/cpu/online`
reported `0-3` and each CPU's `online` node reported `1`. CPU0's cumulative cpuidle counters were
non-zero for `standalone_pc` (state 1: 3,873 entries) and `pc` (state 2: 105,760 entries), as well
as WFI (state 0: 48,008 entries). This is a single snapshot plus cumulative counters: it shows all
four cores online at that moment and proves those idle states have been entered, but does not
establish their current residency or behavior under the stored OpenWrt comparison workload.

At the same snapshot, Android TSENS hardware sensors 0/1/2/4/5 read 50/46/44/47/48 °C;
`pm8916_tz` was 39.486 °C and battery 33 °C. The PA VADC
sysfs node `/sys/devices/soc.0/qpnp-vadc-de6e5000/pa_therm0` returned `Result:36 Raw:7b49`.
The active thermal policy still does not list this PA channel as a monitored input. The current
log buffer contained no `ThermalEngine` lines, so this spot-check adds no new event evidence; the
separate 160 s LTE trace remains the useful action trace. Together, the runtime check directly
disproves the proposal's categorical claim that `mpdecision` keeps CPUs 1–3 offline, but not the
possibility that it hotplugs them in other periods.

Evidence anchors: Android starts thermal-engine in init.target.rc:148 and the captured properties show it running at 05_subsystems_and_properties.txt:77. The lsof capture lists pa_therm0 at 18_lsof_and_sockets_dump.txt:496; the Ghidra notes include the config path at :7450, PA/PMIC sensor names at :7648–7649, and modem mitigation success/failure strings at :7947–7949. The Android PA VADC channel is msm8916.dtsi:1803; the PM8916 thermal-alarm DT node is msm-pm8916.dtsi:175–185.

| Finding / hypothesis | Confidence | Why |
|---|---|---|
| The saved OpenWrt load run is hotter than the saved Android load run | High for these captures | All five TSENS channels show about +12 °C mean and +14–15 °C peak; conditions are not fully matched |
| OpenWrt has a systematic +15 °C idle offset | Rejected | Raw idle means are +3–7 °C, and all idle traces cool during sampling |
| TSENS conversion/calibration code creates the +15 °C delta | Low likelihood | Same sensor IDs, fuse bit layout, calibration anchors, and conversion math; live coefficients/raw codes still need confirmation |
| Android thermal policy explains much of the load delta | **GPU action observed; causal effect unknown** | Recovered default and LTE trace show GPU limited 310→200 MHz at CPU0–1 TSENS 55 °C. WLAN's 60 °C action and CPU `ss` controls at 78–85 °C were not reached; `fusion` is level 0 at 55/60 °C and level 1 only at 105 °C. No modem mitigation or runtime config push occurred; these observations do not quantify the GPU action's temperature effect |
| CPU trip-point difference alone explains the delta | Unlikely as sole cause | OpenWrt's 60 °C-trip test did not lower the modem peak and cost throughput; all sensor channels move together |
| Missing CPU deep-idle support is the idle explanation | Not supported by source | OpenWrt has PSCI CPU/cluster idle states and CPU idle driver enabled; actual residency remains unmeasured |

## Proposed solution and validation

### Solution design — evidence-first; proposed, not implemented

Do **not** implement an OpenWrt modem/PA thermal manager from the current evidence. The recovered
Android default does not monitor `pa_therm0`; its `fusion` mitigation level is 0 at the 55/60 °C
thresholds and level 1 only at 105 °C, while the LTE trace peaked near 62 °C on the board sensor.
The trace also showed no runtime config push or modem action. Therefore copying this policy as a
QMI modem-throttle solution would be unsupported. Do not change TSENS calibration, add speculative
CPU hotplugging, lower thermal trips, alter voltage tables, or clear the A2 pin
(`echo 0 > /sys/module/qcom_bam_dmux/parameters/a2_pin`; formerly `modem-a2-hold`) as a
temperature fix on this evidence.

The current recommendation is a **controlled, paired A/B before implementation**. The stored LTE
captures show a real, broad on-die temperature difference, but their radio/load/ambient conditions
are not sufficiently matched to assign its cause. The Android trace establishes a narrower
candidate worth isolating: GPU frequency falls from 310 to 200 MHz when CPU0–1 TSENS reaches 55 °C;
WLAN's 60 °C action did not fire in that trace, and no modem mitigation fired. This is evidence of
policy behavior, not evidence that the GPU action explains the multi-sensor temperature gap.

Recommended sequence:

- Run repeated paired Android/OpenWrt tests on the same device, power supply, SIM/network, and controlled traffic pattern. Match starting/ambient temperature, band and signal as closely as practical; record the same byte count and throughput, and allow a steady-state interval.
- Log matched TSENS channels, USB input voltage/current, LTE band and signal metrics, traffic direction/rate, CPU online/frequency and idle-state counters, GPU clock, and relevant thermal-engine logs on both systems. Keep the existing OpenWrt thermal policy and modem A2 workaround unchanged during this baseline.
- If the gap persists, use reversible one-variable-at-a-time tests to isolate GPU, CPU/idle, radio TX, and rail-power contributions. Only then design a narrowly scoped policy change, with its temperature source, thresholds, action semantics, hysteresis, and rollback behavior verified first.
- Preserve firmware and modem thermal protections. Do not infer PA mitigation from an open file descriptor alone, or infer a runtime policy from strings embedded in the binary.

Keep OpenWrt's existing 75 °C CPU passive trip, current OPP settings, and the A2 pin
(`/sys/module/qcom_bam_dmux/parameters/a2_pin`; formerly `modem-a2-hold`)
unchanged during the baseline. The saved 60 °C CPU-trip experiment did not lower peak TSENS
meaningfully and imposed a large throughput penalty. The A2 workaround is a modem-stability
measure; remove it only after the separate BAM-DMUX quiesce fault is fixed and validated.

### Validation before implementation

1. **Run a genuinely paired A/B.** Use the same HMU05 board, power supply, SIM/APN, modem firmware and carrier profile; keep ambient temperature and airflow fixed; start each run from a matched board temperature; use the same LTE band/signal if practical and transfer the same byte count at a controlled rate. Wait for equilibrium and record at least 10 minutes. Record both TSENS sets, an independent thermocouple/IR point on the SoC and PA shield, USB input voltage/current or total power, LTE throughput, RSRP/RSRQ/band, CPU frequency and cpuidle residency.

2. **Capture Android's runtime policy evidence.** The active config has already been recovered from the running binary's `-o` dumper; `/system/etc/thermal-engine.conf` is absent. Capture logs and state through a full matched test, including GPU/WLAN actions and any modem mitigation level. The key test is which policy actions actually occur and whether any correlate with reduced input power or temperature.

3. **Verify OpenWrt runtime calibration and sensor provenance.** Confirm the exact booted HMU05 DTB and TSENS probe/calibration success. Capture raw TSENS codes plus effective slopes/intercepts on both OSes if possible. Identify the runtime provider for the sampler's pm8916-thermal zone; until then, exclude that series from cross-OS conclusions.

4. **Gate each change on evidence.** If Android shows no modem-mitigation request during the relevant load—as in the captured 160 s trace—do not implement modem mitigation as the explanation. Use the paired measurements to choose which single subsystem to test next.

5. **Evaluate any patched OpenWrt build against the same control.** Require a repeatable reduction in the load-temperature gap beyond run-to-run noise, while checking throughput and modem stability/SSR events. Keep the CPU trip and OPP unchanged so the result isolates the single variable under test.

## Corrected finding

The best evidence-backed answer today is: **OpenWrt is genuinely running hotter in the stored LTE-load capture, but the alleged +15 °C idle offset was a misreading of transient data. A TSENS conversion/calibration bug is unlikely. The Android modem/PA-mitigation hypothesis is not supported by the recovered compiled-in config or the 160 s LTE trace: `fusion` is inactive at the observed temperatures, and no runtime config push occurred. The trace did show GPU limiting at 55 °C, while WLAN did not reach its 60 °C trigger. A current point-in-time snapshot also found all four Android CPUs online with `mpdecision` running and non-zero `standalone_pc`/`pc` idle counters. CPU/GPU policy and radio/TX or power-input differences remain candidates, not proven causes; the next step is a controlled paired A/B, not a speculative policy port.**

This supersedes the baseline claim in HMU05_ANDROID_THERMAL_CONTROL_PORT.md §3 and its inference that a uniform +15 °C idle rise proves DDR/fabric power is the cause. It likewise supersedes the settled-+15 °C-idle premise and the ranked-magnitude table in [HMU05_THERMAL_RESEARCH_AND_PROPOSAL.md](HMU05_THERMAL_RESEARCH_AND_PROPOSAL.md) (see the reconciliation above). Both earlier reports remain useful for their CPU-trip experiments, but not for their idle-baseline/root-cause conclusions.

## Reconciliation with the earlier proposal (HMU05_THERMAL_RESEARCH_AND_PROPOSAL.md)

The earlier report [HMU05_THERMAL_RESEARCH_AND_PROPOSAL.md](HMU05_THERMAL_RESEARCH_AND_PROPOSAL.md)
concludes that OpenWrt idles a settled **+15 °C** hotter than Android and ranks six compounding root
causes. Re-reading its own raw CSVs (recomputed here, mean °C over each capture window) does not
support that conclusion. This section adjudicates the report cause-by-cause so the two documents can
be read together.

### Verified measurement matrix (mean °C; TSENS channel → sampler label)

| Capture | window (s) | modem/0 | camera/1 | gpu/2 | cpu23/4 | cpu01/5 |
|---|---|---:|---:|---:|---:|---:|
| android_idle.csv | 516–578 | 55.70 | 52.30 | 50.70 | 52.60 | 54.10 |
| android_load.csv | 578–829 | 59.25 | 55.70 | 55.08 | 55.02 | 56.88 |
| owrt_idle_unpinned.csv | 4600–4725 | 58.90 | 55.78 | 54.28 | 55.28 | 57.07 |
| owrt_idle_pinned.csv | 4008–4070 | 61.73 | 58.70 | 57.67 | 57.83 | 59.80 |
| owrt_idle_c1.csv | **159–221** | 69.47 | 66.13 | 66.00 | 64.70 | 67.17 |
| owrt_load_control.csv | 2045–2300 | 71.03 | 67.97 | 67.11 | 66.88 | 69.40 |
| owrt_load_c1.csv | **221–484** | 75.66 | 72.33 | 71.88 | 70.89 | 73.62 |
| owrt_load_pinned.csv | 4080–4336 | 72.33 | 69.29 | 68.42 | 68.12 | 70.73 |

OpenWrt − Android deltas (same columns): **load control +11.8/+12.3/+12.0/+11.9/+12.5**;
load pinned +13.1/+13.6/+13.3/+13.1/+13.9; **idle unpinned +3.2/+3.5/+3.6/+2.7/+3.0**;
idle pinned +6.0/+6.4/+7.0/+5.2/+5.7.

Two capture families are **not valid baselines** and must be excluded from any "+15 °C" claim:

- The `_c1` captures sit at uptime **159–221 s (idle)** and **221–484 s (load)** — i.e. within the
  first few minutes after a fresh boot, while the board is still warming. `owrt_idle_c1` rises
  67→71 °C across its window (a boot warm-up), and its "+12–15 °C vs Android idle" is that warm-up,
  not an idle offset.
- Every other idle capture **cools** during its window (android_idle 59→52 °C; owrt_idle_unpinned
  61→56 °C; owrt_idle_pinned 64→61 °C). A falling trace is a transient, not an equilibrated idle
  baseline, so it cannot establish a settled offset either.

The report's Executive-Summary figure ("OpenWrt idles at 65–70 °C") matches the boot warm-up
transient, not the stored steady-ish idle captures (pinned 61.7 °C, unpinned 58.9 °C).

### Cause-by-cause adjudication

| Report rank | Claim | Verdict | Evidence |
|---|---|---|---|
| 1 | Android collapses the A2 into deep sleep when idle; OpenWrt pins it → **+5–7 °C** | **Premise refuted; magnitude overstated; not a gap cause** | android_idle.csv holds `a2_out=30`, `a2_in=28`, `a2_pwr=30` **constant** (0 transitions over the whole window) ⇒ Android keeps the A2 **on** at idle — the same state as OpenWrt's `modem-a2-hold` pin. Measured pinned−unpinned idle delta is ≈ **+2.8 °C** (modem channel), not +5–7. Because Android is in the pinned state, the pin is *parity*, not a deviation, and cannot explain OpenWrt−Android. |
| 2 | `mpdecision` keeps CPU1–3 offline → **+3–4 °C** | **Contradicted for recorded periods; not established for all time** | android_idle.csv and android_load.csv report `f0..f3 = 800000`; the live 2026-10-08 snapshot likewise found `mpdecision` running with CPUs 0–3 online. This does not rule out hotplugging in other periods. No evidence supports the claimed thermal impact. Also not DT-portable: mainline has no CPU-hotplug cooling device (see CONTROL_PORT Lever 2b). |
| 3 | No CPR/dynamic voltage scaling → **+3–5 °C** | **Open, unquantified** | Source claim is correct: mainline CPU OPP table has no `opp-microvolt`/`cpu-supply`, CPR unimplemented; Android uses `qcom,cpr-regulator`. But **no rail-voltage telemetry exists in the artifacts**, so the °C value is an estimate. `required-opps`→rpmpd is a provable no-op (`drivers/opp/core.c:1117`); a real fix needs a kernel driver change (CONTROL_PORT Lever 3). |
| 4 | Android trip 60 °C vs OpenWrt 75 °C → **"sets +15 °C ceiling"** | **Refuted as a cause** | Trips are a *response*, not a source. CONTROL_PORT Lever 1 lowered the OpenWrt trip to 60 °C: **0 °C benefit** (peak modem 76 °C both), **−37 % rx throughput**, and a permanent idle-throttle regression. The "60 °C = Android idle ceiling" framing is itself downstream of the rejected +15 °C idle baseline; OpenWrt's 75 °C is arguably already the baseline-corrected analogue. |
| 5 | No SPM `spc` / PSCI falls back to WFI → **+2–3 °C** | **Not supported by source; Android state entry observed, OpenWrt residency unmeasured** | OpenWrt 6.12.94 has PSCI CPU/cluster idle states (msm8916.dtsi:202–228) and `CONFIG_CPU_IDLE`/`ARM_PSCI_CPUIDLE` enabled. The live Android CPU0 counters show entries to both `standalone_pc` and `pc`; this does not establish their comparative residency or rail savings. |
| 6 | No PA/Wi-Fi thermal-engine throttle → **+1–2 °C** | **PA/modem claim unsupported; GPU/WLAN policy remains a test candidate** | The recovered default does not monitor `pa_therm0`; `fusion` is inactive at observed temperatures, and the LTE trace has no modem action. It does show GPU limiting at 55 °C. WLAN's 60 °C threshold was not reached. No °C contribution is measured. |

**Net:** none of the six earlier claims establishes a root cause or a measured temperature
contribution. The PA/modem part of cause 6 is contradicted by the recovered policy/trace; its GPU
and WLAN policy portion is a candidate for a controlled test, not a confirmed explanation. Cause 3
(CPR/voltage) remains an unmeasured source gap. The reported "+5–7", "+3–4", etc. figures are
estimates, not results.

**What to keep from the earlier report:** its source observations can inform experiments, but the
CPU hotplug, thermal-trip, undervolting, and modem-throttle changes are not validated temperature
solutions. Fixing the A2 quiesce fault remains a separate modem-stability task
(`Docs/Modem Stability/Modem RE/hmu05/A2_QUIESCE_ASYMMETRY.md`), not a demonstrated thermal fix.
Do not carry forward the settled-+15 °C-idle premise or ranked magnitude table.

## Source and artifact index

- Android TSENS source: GitIgnore/android_kernel_zte_msm8916/drivers/thermal/msm8974-tsens.c (MSM8916 fuse decode, 30/120 °C conversion) and arch/arm/boot/dts/qcom/msm8916.dtsi (TSENS IDs/slopes, msm-thermal, PA VADC channel).
- OpenWrt TSENS source: GitIgnore/compare/openwrt/build_dir/target-aarch64_generic_musl/linux-msm89xx_msm8916/linux-6.12.94/drivers/thermal/qcom/tsens-v0_1.c and tsens.c; matching DT/calibration cells and zones are in that tree's arch/arm64/boot/dts/qcom/msm8916.dtsi.
- Captured data and samplers: [thermal_baseline directory](../../scratch/thermal_baseline/), especially android_idle.csv, android_load.csv, owrt_idle_unpinned.csv, owrt_idle_pinned.csv, owrt_load_control.csv, thermal_sample.sh, android_thermal_sample2.sh and run_load_test.sh.
- Android policy/trace artifacts (2026-10-08): [thermal_baseline/android/](../../scratch/thermal_baseline/android/README.md) — `thermal-engine-active.conf` (recovered via `thermal-engine -o`), `thermal-engine.bin` (md5 d8084a1c342c82ca796a91f27033b59e), `thermal_trace.log` + `thermal_samples.log` + `trace_thermal.sh` (160 s LTE trace), and `init.target.rc.thermal-engine.txt`.
- Android runtime evidence: [thermal-engine reverse-engineering notes](../Modem%20Stability/Stock_Android_Analysis/52_ghidra_thermal_engine_RE.txt), [init service configuration](../Modem%20Stability/Stock_Android_Analysis/init.target.rc), [runtime thermal zones](../Modem%20Stability/Stock_Android_Analysis/05_subsystems_and_properties.txt), and [file-descriptor capture](../Modem%20Stability/Stock_Android_Analysis/18_lsof_and_sockets_dump.txt).
- Earlier CPU lever data/review: [Android thermal-control port report](./HMU05_ANDROID_THERMAL_CONTROL_PORT.md).
- Upstream cross-checks: [Linux Qualcomm TSENS conversion/calibration](https://github.com/torvalds/linux/blob/master/drivers/thermal/qcom/tsens.c), [TSENS v0.1/MSM8916 data](https://github.com/torvalds/linux/blob/master/drivers/thermal/qcom/tsens-v0_1.c), and [upstream MSM8916 device tree](https://github.com/torvalds/linux/blob/master/arch/arm64/boot/dts/qcom/msm8916.dtsi).
