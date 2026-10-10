# HMU05 Live Android Thermal Findings & OpenWrt Mitigation Proposal

**Date:** October 2026  
**Hardware:** HMU05 4G Modem Stick (`hmu05,250605v0s`, Qualcomm MSM8916)  
**Target Live Device:** Root SSH shell on Android 4.4 / Linux 3.10.28 (`192.168.100.1`)  
**Live OpenWrt Target:** OpenWrt 25.12.5 (`r33051-f5dae5ece4`) / Mainline Linux 6.12.94 at `192.168.8.1`  

> **Correction (2026-10-08) — read before using this proposal.** This document predates the
> reconciled evidence in
> [HMU05_OPENWRT_ANDROID_THERMAL_ROOT_CAUSE.md](./HMU05_OPENWRT_ANDROID_THERMAL_ROOT_CAUSE.md)
> and conflicts with it in several places. **Verified correct:** Android idle ≈ 44–49 °C; the
> PM8916 rail `8916_s2` reads 1,050,000 µV; CPU0 C2 (`pc`) residency ≈ 81.5 % (re-confirmed
> 1894 s / 2318 s = 81.7 %); `thermal-engine` has no `/system/etc/thermal-engine.conf`; kernel
> `msm_thermal` disabled. **Not supported / refuted:**
> - *"thermal-engine logged 0 mitigation events / is completely inactive"* (§1.1, §2.4) is a
>   **log-buffer sampling artifact** — a 160 s LTE trace captured `alarm raised 1 at 55.0 degC` →
>   `Setting GPU[0] to 200000000`. The 10 s load in §2.1 never crossed the 55 °C threshold.
> - The claim that *`modem-a2-hold` explains a 5–7 °C idle penalty* (§1.3, §3, Phase 3) is
>   **refuted for idle**: Android's capture holds A2 **on** (`a2_out/a2_in/a2_pwr` constant), the
>   same state as the older OpenWrt pinned sample; measured pinned−unpinned idle cost ≈ **2.8 °C**.
>   This does not test A2's load contribution. OpenWrt snapshots changed during inspection:
>   13:09 UTC reported `power/control=auto` / `suspended`; 13:39 UTC reported `on` / `active`.
>   No `modem-a2-hold` process was present in the latter snapshot, and no cause for the state
>   change is established.
> - *"PSCI falls back to `wfi` without power collapse"* (§3) is contradicted by source support and
>   the current runtime snapshot: CPU0 recorded about 590 s in `standalone-power-collapse`. That
>   state is not proven equivalent to Android's `pc`, and its thermal contribution is unmeasured.
> - OpenWrt CPU rail *"~1.15–1.25 V"* (§1.2, §3) is **not measured**; the ~25–30 % dynamic-power
>   and 3–5 °C figures depend on it. Android's CPU is in fact supplied via `apc_vreg_corner` (CPR)
>   → `pm8916_s2`, whose DTS ceiling/floor for corner 0 is 1.1 V.
> - Phase 1 DTS (§4) is **not applicable as written**: mainline `msm8916.dtsi` has **no
>   `pm8916_s2` node** (only msm8929/msm8939 define it), so `cpu-supply = <&pm8916_s2>` is a
>   dangling reference; `opp-peak-kBps` requires the interconnect path; and wiring `cpu-supply`
>   into the CPR path needs a driver change (see CONTROL_PORT Lever 3).
>
> **Do not apply the 8–12 °C action plan as written.** Treat it as a hypothesis list. OpenWrt
> CPU-rail voltage, comparable CPU-idle state effects, and matched radio/load/input-power remain
> open questions; measure them before selecting or implementing a thermal change.

---

## 1. Current Finding

The available evidence confirms a load-associated temperature difference in the stored captures,
but does **not** identify one root cause or establish a settled idle offset:

- The saved LTE-load comparison is about **+12 °C mean** and **+14–15 °C peak** across five
  matched TSENS channels. Those runs were not synchronized or matched for traffic rate, radio
  conditions, ambient temperature, starting temperature, or input power.
- The saved idle comparisons are about **+3–7 °C**, but the traces cool or warm during sampling;
  they are not equilibrated idle baselines.
- Android's live thermal-engine policy is compiled into the binary. A 160 s LTE trace observed
  GPU limiting from 310 to 200 MHz at CPU0–1 TSENS 55 °C. It observed no modem mitigation or
  runtime policy push; WLAN's 60 °C threshold was not reached. The PA thermistor is opened by the
  daemon but is not a monitored input in the recovered policy.
- Android CPU0's reported `pc` cpuidle residency was about 81.5% in one captured uptime window,
  and `8916_s2` read 1.05 V in a live snapshot. OpenWrt now has a CPU0 idle-state snapshot too,
  but the state lists/intervals are not matched; its CPU-rail voltage remains unmeasured.
- A separate live snapshot found `mpdecision` running with all four CPUs online. This does not
  establish its behavior at other times, but it refutes the claim that it always parks CPUs 1–3.
- A read-only OpenWrt snapshot at `192.168.8.1` found TSENS 0/1/2/4/5 at 48/45/43/44/46 °C,
  all four CPUs online at 800 MHz, and CPU0 frequently in `standalone-power-collapse`. BAM-DMUX
  changed from `auto`/`suspended` in the first check to `on`/`active` in the later check; the cause
  is unknown. The later check detected a powered modem, but it was still searching for network
  registration, with packet service detached and WWAN links down. Neither snapshot was an LTE-load
  sample. These temperatures are not paired with Android's sample and must not be treated as an
  OpenWrt-vs-Android delta.
- Two later clean OpenWrt-only downlink runs (2026-10-08) used an attached LTE bearer and an exact
  32 MiB body cap. Each showed a repeatable, transient +4–5 °C rise on the five SoC TSENS channels
  from a stable one-minute baseline; modem TSENS peaked at 62 °C. A first attempt was excluded
  because user-confirmed `yes` CPU-stress workers were running concurrently. The first Android CSV
  was discarded at the user's direction; the retained run 2 is the sole Android source of truth.
  It peaked 4–5 °C below OpenWrt across the five SoC channels, but began about 8–11 °C cooler and
  warmed +9–10 °C above its own baseline. Its payload rate was about 10% higher. This one retained
  short run does not settle the stored +14–15 °C cross-OS difference.

**Conclusion:** no thermal-policy, voltage, CPU-hotplug, or A2 change is justified as a
temperature fix yet. The next step is to repeat the Android/OpenWrt comparison with matched start
temperatures and at least three pairs, followed by reversible one-variable tests. No 8–12 °C
benefit is presently supported by measurements.

---

## 2. Live Telemetry Data from Android (`192.168.100.1`)

The following measurements were captured live from the running Android system:

### 2.1 One Android Snapshot and Short Load Sample

| Sysfs zone / HW TSENS ID | Node | Channel description | Sample A | After 10 s download | Delta |
|---|---|---|:---:|:---:|:---:|
| zone0 / ID 0 | `/sys/class/thermal/thermal_zone0` | Modem/baseband | **49°C** | **51°C** | +2°C |
| zone1 / ID 1 | `/sys/class/thermal/thermal_zone1` | Camera/top die | **45°C** | **47°C** | +2°C |
| zone2 / ID 2 | `/sys/class/thermal/thermal_zone2` | Adreno 306 GPU | **44°C** | **46°C** | +2°C |
| zone3 / ID 4 | `/sys/class/thermal/thermal_zone3` | CPU cores 2–3 | **45°C** | **47°C** | +2°C |
| zone4 / ID 5 | `/sys/class/thermal/thermal_zone4` | CPU cores 0–1 | **47°C** | **49°C** | +2°C |
| **PMIC Die** | `/sys/class/thermal/thermal_zone5` | PM8916 (`pm8916_tz`) | **39.2°C** | **39.5°C** | +0.3°C |
| **PA Thermistor** | `qpnp-vadc-de6e5000/pa_therm0` | RF Power Amplifier | **36.0°C** | **37.0°C** | +1.0°C |
| **XO Thermistor** | `qpnp-vadc-de6e5000/xo_therm` | 19.2 MHz Crystal | **40.0°C** | **40.0°C** | 0.0°C |

These are point readings around a short download, not a matched OpenWrt comparison or a
steady-state thermal test. Preserve the capture timestamp, uptime, ambient temperature, download
command/byte count, LTE band and signal data with any future reproduction. TSENS hardware IDs are
0, 1, 2, 4 and 5; the sysfs zone index is not the hardware ID.

### 2.2 Electrical & Voltage Rails

```text
8916_s1_corner: 5 uV (state=enabled)
8916_s2:        1050000 uV (1.05 V, state=enabled)   <-- Android snapshot; CPU rail mapping
vph_pwr:        3445437 uV (3.445 V)                 <-- System Power Rail
usb_in:         5125040 uV (5.125 V)                 <-- 5V USB Input
```

The `8916_s2` value is an Android readback at one operating point. It does not establish OpenWrt's
voltage, the voltage at every CPU OPP, or a temperature contribution. Measure both systems at
matched frequencies and load before making a power-scaling inference.

### 2.3 Reported CPU0 cpuidle Counters (one capture; uptime about 1,560 s)

```text
C0 (wfi):           282,724,828 us  ( 282.7 s / 18.1%)  - Shallow Clock Gating
C1 (standalone_pc):   3,990,260 us  (   3.9 s /  0.2%)  - Core Rail Dropped
C2 (pc):          1,271,011,076 us  (1271.0 s / 81.5%)  - Full Cluster Power Collapse
```

The reported counter values sum to about 1,558 s, consistent with the stated uptime, and `pc`
accounts for about 81.5% of this CPU0 capture. This supports frequent entry into the platform's
`pc` idle state on Android. It is not an OpenWrt measurement or a direct measurement of rail power
or leakage; collect comparable counter deltas on both systems before attributing a thermal effect.

### 2.4 Thermal Daemon Logs & Mitigation Evidence

* **Kernel driver:** `dmesg` reports:
  ```text
  [    7.256790] msm_thermal:set_enabled enabled = 0
  ```
  `msm_thermal` mitigation is disabled in this runtime.
* **Userspace `thermal-engine`:** no `/system/etc/thermal-engine.conf` exists; the daemon runs
  the recovered compiled-in default. A short `logcat` grep with zero matching lines is not proof
  that the daemon is inactive: the separate 160 s LTE trace recorded a GPU action at 55 °C.
* The trace did **not** record modem `fusion` mitigation or a runtime config push. The recovered
  default assigns `fusion` level 0 at 55/60 °C and level 1 only at 105 °C; `pa_therm0` is not a
  monitored input. The WLAN action at 60 °C and CPU `ss` controls at 78–85 °C were not reached in
  that trace. See the [captured policy and trace notes](../../scratch/thermal_baseline/android/README.md).

### 2.5 Read-only OpenWrt Snapshot (`192.168.8.1`)

No settings were changed and no load test or traffic generator was started. The snapshot reported
device time `2026-06-29 13:09:12 UTC`, uptime about 10 minutes, OpenWrt **25.12.5** revision
`r33051-f5dae5ece4`, kernel **6.12.94**, board `hmu05,250605v0s`. The device clock is not aligned
with the October 2026 Android/workspace captures, so use this as a runtime-state observation, not a
time-synchronized comparison.

| OpenWrt thermal zone | HW TSENS ID | Current reading | Configured trip(s) |
|---|---:|---:|---:|
| `modem-thermal` (zone0) | 0 | 48 °C | 85 °C |
| `camera-thermal` (zone1) | 1 | 45 °C | 75 °C |
| `gpu-thermal` (zone2) | 2 | 43 °C | 75 / 95 °C |
| `cpu2-3-thermal` (zone3) | 4 | 44 °C | 75 / 110 °C |
| `cpu0-1-thermal` (zone4) | 5 | 46 °C | 75 / 110 °C |
| `pm8916-thermal` (zone5) | — | 37.633 °C | 105 / 125 / 145 °C |

All six zones reported mode `enabled` and policy `step_wise`. The CPU frequency policy showed
all CPUs `0-3` online at 800 MHz (`schedutil`, 200–800 MHz); the CPU-frequency cooling device was
at state 0 of 2. These readings were below the listed CPU/GPU trip points at this snapshot.

CPU0 exposed two cpuidle states: `WFI` recorded 12.88 s / 19,564 entries, and
`standalone-power-collapse` recorded 589.99 s / 30,204 entries. This is direct evidence that the
running OpenWrt kernel is **not simply remaining in WFI**. The time counters are cumulative from
boot and the OpenWrt state names do not prove equivalence to Android's `pc` state or quantify rail
power; compare matched counter deltas and state definitions before drawing a thermal conclusion.

#### Follow-up check at 13:39 UTC: modem detected, LTE data not attached

The BAM-DMUX device initially reported `power/control=auto` and `runtime_status=suspended`; its RX
and power-collapse telemetry counters were zero. A later read-only check at device time
`2026-06-29 13:39:39 UTC` (uptime about 40 minutes) found `power/control=on`,
`runtime_status=active`, `pc_state=1`, one PC vote and no PC timeout. No `modem-a2-hold` process
was present in that later process listing. These snapshots differ; the transition's cause is
unknown, and neither reading identifies the modem's physical A2 rail state by itself.

At the later check, `mmcli` detected the modem powered on with 86% reported signal and 4G allowed,
but its state was `searching`, registration was `searching`, packet service was `detached`, and
the reported network rejection was `illegal-me`. `wwan0`–`wwan7` were all down with zero byte
counters. Logs included an initial EPS-bearer QMI `invalid-parameter-length` error and registration
timeout/rejection messages. This was not a working LTE data session, so no load was started at that
time. No modem configuration was inspected or changed. The later active-bearer tests are recorded
in §2.6. The existing
[`run_load_test.sh`](../../scratch/thermal_baseline/run_load_test.sh) was not used because it
repeatedly requests 50 MB downloads during the sample and writes its CSV to the target path; its
request loop has no practical byte cap for a high-throughput session. A separate
[live-comparison note](./HMU05_LIVE_OPENWRT_VS_ANDROID_COMPARISON.md)
reports `wwan0` idle/unattached; the later interface check above provides the exact per-link
state, but the interface snapshots should still be timestamped together when reproducing.

The regulator class exposed `s3`, `s4` and LDOs but no `s2`/CPU-rail readback; OpenWrt CPU voltage
is still unknown. No KGSL or devfreq GPU clock endpoint was found at the queried sysfs paths, so
the live GPU clock was not measured. OpenWrt's `pm8916-thermal` zone is enabled, unlike the
captured Android `pm8916_tz` zone, which was disabled; do not compare those PMIC readings as if
they were equivalent operating sensors.

For orientation only, OpenWrt's current TSENS readings (48/45/43/44/46 °C for HW IDs 0/1/2/4/5)
are near or below the earlier Android Sample A values (49/45/44/45/47 °C). They are **not a valid
temperature delta**: the snapshots were taken at different times, with different device clocks
and unmatched load, ambient and power conditions. The current OpenWrt snapshot was effectively
idle and had no visible modem interface.

### 2.6 OpenWrt active-LTE downlink retests (2026-10-08 UTC)

The earlier no-bearer finding above is historical. On 2026-10-08, the modem was registered
`home` on LTE, packet service was `attached`, Bearer 2 was connected on `wwan0`, and reported
signal quality was 91% on run 1 and 88% on run 2. The CPU-stress processes discussed below had
finished before either clean run; preflight showed 97% CPU idle on run 1 and no `yes` process on
either run. BAM-DMUX runtime status remained `active`.

Each clean run used the same read-only measurement procedure: 60 s idle baseline sampled every
5 s; one Cloudflare downlink with a 33,554,432-byte response cap, consumed at approximately
512 KiB/s; then 120 s cooldown sampled every 5 s. The complete body took 71 s on both runs
(about 3.78 Mbit/s payload rate). A per-chunk guard would stop the transfer if modem TSENS reached
80 °C or either CPU TSENS reached 73 °C; neither guard fired. The transfer was streamed and
discarded, with no test file, configuration, or runtime setting written to the device. The
OpenWrt frequency policy varied between 400 and 800 MHz during the baseline/load windows.
Run 1 ran 11:12:57–11:16:59 UTC (downlink 11:13:52–11:15:03); run 2 ran
11:22:00–11:26:02 UTC (downlink 11:22:55–11:24:06).

| OpenWrt channel | Run 1 idle range → load peak | Run 2 idle range → load peak | Peak above that run's idle maximum |
|---|---:|---:|---:|
| `modem-thermal` (zone0 / TSENS 0) | 56–58 → 62 °C | 55–57 → 62 °C | +4 / +5 °C |
| `camera-thermal` (zone1 / TSENS 1) | 53–54 → 58 °C | 53 → 58 °C | +4 / +5 °C |
| `gpu-thermal` (zone2 / TSENS 2) | 52–53 → 57 °C | 51–52 → 57 °C | +4 / +5 °C |
| `cpu2-3-thermal` (zone3 / TSENS 4) | 52–54 → 58 °C | 52–53 → 58 °C | +4 / +5 °C |
| `cpu0-1-thermal` (zone4 / TSENS 5) | 54–56 → 60 °C | 54–55 → 60 °C | +4 / +5 °C |
| `pm8916-thermal` (zone5) | 45.779–46.712 → 51.767 °C | 45.288–46.074 → 51.423 °C | +5.1 / +5.3 °C |

The two repeats agree within 1 °C on each SoC channel. By the end of cooldown, modem TSENS was
57 °C on both runs. WWAN RX counters increased by about 35.4 MB per run while the measured body
was exactly 32 MiB; the interface counter also includes transport/read-ahead and background
traffic. These are short transient tests, not a 10-minute thermal equilibrium, and no ambient
temperature, USB input power, RSRP/RSRQ/SINR, or modem transmit-power measurement was captured.
The PM8916 channel is useful for within-run trend only and is not equivalent to Android's disabled
`pm8916_tz` sensor. Raw samples are preserved in
[`openwrt_lte_retest_20261008.csv`](../../scratch/thermal_baseline/openwrt_lte_retest_20261008.csv)
and
[`openwrt_lte_retest_20261008_run2.csv`](../../scratch/thermal_baseline/openwrt_lte_retest_20261008_run2.csv).

#### Excluded first attempt: concurrent CPU stress

The first attempt is not part of the clean-run results. Its supposed idle baseline rose from
roughly 61–63 °C to 74–75 °C before downlink began; during the short observed load window, CPU
frequency fell to 200–400 MHz and temperatures reached 77 °C. A later process snapshot found four
orphaned `yes` workers consuming about 85–87% of CPU (about 5.0 load average and 87% user CPU).
The user confirmed these were intentionally started processes and asked that they be left to
finish; they were not stopped. The test's SSH observation session had been interrupted, and its
download subprocess outlived that connection briefly. I sent TERM only to the downloader
pipeline PIDs after verifying them; a subsequent check showed those test processes absent. The
user's workers were not signaled. After they exited, OpenWrt cooled to 58 °C on the modem zone and
55–56 °C on CPU zones; the clean repeats above then started. This episode cannot attribute its
18–20 °C rise to LTE-only load because CPU stress and network load overlapped.

### 2.7 Android active-LTE downlink retest — retained run 2 (2026-10-08 UTC)

**Source of truth:** [`android_lte_retest_20261008_run2.csv`](../../scratch/thermal_baseline/android_lte_retest_20261008_run2.csv).
The earlier Android CSV was discarded at the user's direction; none of its measurements are used
in the findings below.

Android was registered `home` on JIO LTE with data connected over `rmnet0`. The retained run's
telephony snapshot reported RSRP −95 dBm and RSRQ −14 dB. Preflight showed 97.7% CPU idle, no
stress worker, and CPU0 at 800 MHz in the sampled frequency field. Run 2 ran 12:57:09–13:01:17
UTC, with a 60 s idle baseline and 120 s cooldown sampled every 5 s. It used one Cloudflare
33,554,432-byte downlink capped at 512 KiB/s. `curl` verified HTTPS using the existing Android CA
certificates via an in-memory process substitution; no CA bundle or other file was written to the
device. An earlier curl invocation failed before transferring data because its compiled-in default
CA-bundle path was absent; that zero-byte attempt is excluded.

The retained transfer returned HTTP 200 and exactly 33,554,432 bytes in 64.39 s (521,088 B/s,
about 4.17 Mbit/s). The `rmnet0` RX counter rose about 35.4 MB through the final load sample; this
counter also includes protocol overhead and other traffic. The conservative guard (modem TSENS
80 °C; CPU TSENS 4/5 73 °C) did not fire. No device configuration, thermal policy, or persistent
file was changed.

| Android channel | Retained run 2 idle → peak | Rise above idle maximum | OpenWrt run 1 / run 2 peak | Android peak below OpenWrt peaks |
|---|---:|---:|---:|---:|
| TSENS 0 / modem | 47 → 57 °C | +10 °C | 62 / 62 °C | 5 °C |
| TSENS 1 / camera | 44 → 53 °C | +9 °C | 58 / 58 °C | 5 °C |
| TSENS 2 / GPU | 42–43 → 53 °C | +10 °C | 57 / 57 °C | 4 °C |
| TSENS 4 / CPU2–3 | 44 → 53 °C | +9 °C | 58 / 58 °C | 5 °C |
| TSENS 5 / CPU0–1 | 45–46 → 55 °C | +9 °C | 60 / 60 °C | 5 °C |
| Android `pm8916_tz` | 37.674–38.017 → 48.600 °C | +10.6 °C | OpenWrt zone5: 51.423 / 51.767 °C | Not directly comparable |

The OpenWrt baselines were roughly 8–11 °C warmer across the five SoC channels. Android warmed
+9–10 °C above its baseline (versus OpenWrt's +4–5 °C), while its absolute peaks were 4–5 °C
lower. The retained Android payload rate was about 10% higher than OpenWrt's ~3.78 Mbit/s. This
single retained test narrows the observed peak gap for these short runs but does not explain the
older +14–15 °C result or isolate an OS effect. The 120 s cooldown ended at 50 °C modem TSENS,
about 3 °C above its baseline; the run did not reach thermal equilibrium. Ambient temperature,
USB input power, LTE band, modem TX power, and numeric OpenWrt RSRP/RSRQ remain unavailable. PMIC
channels are retained as within-run trends only.

---

## 3. Evidence Status (Root Cause Not Yet Settled)

| Question | Evidence currently available | Assessment |
|---|---|---|
| Is there a temperature difference? | Stored LTE captures differ by about +12 °C mean and +14–15 °C peak across five TSENS sensors, but were not fully matched. Two clean OpenWrt 32 MiB downlinks rose +4–5 °C above baseline. The retained Android run 2 rose +9–10 °C; its absolute TSENS peaks were 4–5 °C below OpenWrt's. | The retained short run narrows the observed peak gap, but Android began 8–11 °C cooler and ran about 10% faster. It does not establish a controlled OS delta or explain the older result. |
| Is TSENS conversion the cause? | Android and OpenWrt source use the same MSM8916 sensor IDs, calibration fields and conversion anchors. Runtime coefficients/raw codes have not been compared. | Unlikely, but not fully closed. Do not add a temperature offset. |
| Does Android thermally throttle the modem/PA? | Recovered default does not monitor `pa_therm0`; no modem action occurred in the 160 s LTE trace; `fusion` is inactive at observed temperatures. | Not supported as the load-gap explanation. Do not port a modem-throttle policy from this evidence. |
| Does Android thermal policy differ? | The trace recorded GPU 310→200 MHz at CPU0–1 TSENS 55 °C. WLAN's 60 °C action and CPU `ss` limits were not reached. | GPU policy is a test candidate; its contribution to whole-device temperature is unknown. |
| Does CPU voltage explain the difference? | Android `8916_s2` read 1.05 V in one snapshot. OpenWrt CPU-rail voltage has not been measured at matched OPP/load. | Open question. The reported Android value does not validate a fixed OpenWrt voltage or safe OPP values. |
| Does CPU idle residency explain it? | Android CPU0 reported about 81.5% in `pc` in one capture. OpenWrt CPU0 counters showed about 590 s in `standalone-power-collapse` and 13 s in WFI over roughly 10 minutes; only two states were exposed. | The claim that OpenWrt remains in WFI is contradicted by the snapshot. State definitions/rail effects are not proven equivalent; compare matched counter deltas before attributing heat. |
| Is `modem-a2-hold` the load cause? | Android's stored idle capture held A2 on, matching an older OpenWrt pinned sample; pinned/unpinned idle difference was about 2.8 °C. OpenWrt was first `auto`/`suspended`, later `on`/`active`; at the latter check the modem was searching and WWAN links were down. | Not supported as the idle-gap cause; current/historical states differ and no LTE load comparison exists. Do not change A2/runtime-PM state as a temperature experiment. |
| Are thermal trips responsible? | Android `msm_thermal` is disabled at runtime. OpenWrt's 60 °C trip experiment did not lower peak TSENS and reduced RX throughput by about 37%. | Do not lower trips as a temperature fix without new evidence. |
| Are radio and test conditions controlled? | The retained Android run used JIO LTE on `rmnet0`, RSRP −95 dBm / RSRQ −14 dB, and 32 MiB at 4.17 Mbit/s. OpenWrt used 32 MiB at about 3.78 Mbit/s and reported signal quality 91%/88%, not comparable to numeric RSRP/RSRQ. Ambient temperature, input power, LTE band and modem TX power were not captured. | The retained run is a useful counterpart, but not a fully matched pair: starting TSENS was 8–11 °C lower, achieved rate differed, and numeric OpenWrt radio values are missing. Match these conditions before attribution. |

For source/artifact detail and the live thermal-engine trace, see the
[reconciled root-cause report](./HMU05_OPENWRT_ANDROID_THERMAL_ROOT_CAUSE.md) and
[Android policy/trace notes](../../scratch/thermal_baseline/android/README.md).

---

## 4. Evidence-First Action Plan

**Status: proposed; no thermal settings have been changed.** This plan is diagnostic-first. It
makes no promise of an 8–12 °C reduction because no intervention has demonstrated that benefit.

### Step 0 — Preserve a clean baseline

- Preserve device settings exactly as found. The latest snapshot reported BAM-DMUX
  `power/control=on` / `runtime_status=active` (an earlier snapshot reported `auto`/`suspended`);
  do not normalize or toggle it. Do not start/stop
  the A2 pin ([patch 848](../../msm89xx/patches/848-bam-dmux-a2-pin.patch), formerly `modem-a2-hold`), alter Wi-Fi, or change any
  runtime power setting for this comparison.
- Do not add fixed `opp-microvolt` values, lower the thermal trips, hotplug CPUs, add a modem QMI
  policy, or disable the A2 workaround as a temperature experiment.
- Record both OS images/kernel revisions, modem firmware, device identity, ambient temperature,
  enclosure/airflow and power-supply details with each run.

### Step 1 — Complete the matched Android/OpenWrt comparison (OpenWrt twice; Android run 2 retained)

- OpenWrt has two clean registered-LTE 32 MiB runs. Android run 2 is the sole retained Android
  dataset; its temperature rose +9–10 °C above baseline, began 8–11 °C cooler, and peaked 4–5 °C
  below OpenWrt at 4.17 Mbit/s versus OpenWrt's ~3.78 Mbit/s. The Android CSV for run 1 was
  discarded at the user's direction. The remaining comparison is not matched for start temperature
  or radio conditions; the contaminated OpenWrt attempt also remains excluded.
- Before any further run, confirm there are no unrelated CPU-stress workers; the contaminated
  attempt shows that uncontrolled CPU load invalidates an apparent idle baseline.
- Use the same HMU05, SIM, carrier/APN, USB supply, enclosure and physical setup. Reboot between
  OSes, but precondition each run to the same starting TSENS range (target within 1 °C).
- Test idle, LTE downlink and LTE uplink as separate scenarios. Use the same traffic generator,
  direction, byte count and target rate on both OSes; record achieved throughput rather than
  assuming the runs were equivalent.
- Capture at 1–2 s intervals for at least 10 minutes after the load begins. Continue until the
  rolling five-minute TSENS slope is below 0.1 °C/min, or stop at 30 minutes and label the run
  transient if it has not stabilized. Do not compare a boot warm-up or cooling transient as a
  steady-state value.
- Repeat at least three Android/OpenWrt pairs, alternating the order where practical. Keep
  ambient conditions and starting temperature comparable; log any deviations.

### Step 2 — Collect the same signals on both systems

- Read TSENS hardware IDs 0, 1, 2, 4 and 5 (and document the sysfs-zone mapping), plus an
  independent thermocouple/IR reading on the SoC and PA shield. Do not treat the disabled Android
  PMIC zone as an equivalent SoC temperature.
- Measure USB input voltage/current and calculate input power/energy over the run. Record LTE
  band, RSRP/RSRQ/SINR where available, modem TX-power/thermal state, traffic direction, bytes
  and throughput.
- Record CPU online state, current/max frequency, governor, and **deltas** of each cpuidle state's
  `time`/`usage` counters over the measurement window. Record GPU frequency/utilization and
  thermal-engine messages/actions over the same interval.
- Record A2 state/telemetry without changing `power/control`. On OpenWrt, measure the CPU rail at
  idle and each tested OPP using a supported runtime readback or safe instrumentation; do not
  infer 1.15–1.25 V from the lack of DTS `cpu-supply` alone.
- Preserve raw samples, command lines, timestamps and environment notes alongside the summary.

### Step 3 — Decide which single variable merits an experiment

- If a matched comparison shows materially lower residency in equivalent deep-idle states on
  OpenWrt, investigate PSCI/idle-state entry and firmware coordination. The current raw state
  names/counters are not directly equivalent; do not add CPU hotplugging as a substitute for
  measuring idle.
- If a repeatable CPU-rail voltage difference exists at the same OPP and workload, first verify
  regulator topology, silicon corner requirements and the mainline driver path. Any OPP/CPR change
  needs characterized safe voltage limits, build/boot validation and a rollback image; never copy
  Android's one-point 1.05 V readback to every OpenWrt OPP.
- If GPU utilization/power differs, test a temporary, reversible GPU limit resembling the
  observed Android 200 MHz action, with hysteresis and performance logging. Keep it only if paired
  runs show a repeatable thermal/power benefit without unacceptable throughput or graphics impact.
- Treat Wi-Fi TX-power tuning as relevant only if Wi-Fi was active in the test and actual radio
  power is measured. A UCI value or assumed 20 dBm default is not proof of dissipated heat.
- Treat the BAM-DMUX/A2 quiesce fix as a separate modem-stability project. After it is independently
  fixed, runtime PM can be tested under modem stress; do not remove `modem-a2-hold` beforehand or
  count an unmeasured temperature change as the expected result.

Do not change thermal trips unless a controlled test shows a safety or thermal-control need. The
existing 60 °C OpenWrt trip experiment imposed a substantial throughput penalty without lowering
the measured peak, so trip lowering is not the current temperature remedy.

### Step 4 — Gate implementation on repeatable results

Change one subsystem at a time, retain a known-good rollback image/configuration, and repeat the
same paired scenarios. Accept a change only when the temperature or input-power improvement is
repeatable beyond run-to-run variation and modem stability, throughput, CPU/GPU performance, and
thermal protections remain acceptable. Report the measured result; do not pre-assign a °C benefit.
