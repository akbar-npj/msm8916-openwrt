# Pre-registration for the 2026-09-22 build — WS1 (patch 825) and WS3 (patch 0005)

**Date:** 2026-09-22
**Frozen at:** boot `59d9c272`, uptime 15 884 s, before the patch-825 build.
**Purpose:** Doc 167 / Doc 183 / Doc 184 require the thresholds and the control to be written down
BEFORE the change is deployed. This file is that record. It must not be edited after the build.

This file freezes **two** independent pre-registrations:

- **Part A** — WS1, the kernel pc-state reconcile (`825-bam-dmux-pc-state-reconcile.patch`).
- **Part B** — WS3, the ModemManager port-probe settle timers
  (`0005-plugin-manager-halve-port-probe-settle-timers.patch`), added later the same day from the
  Doc 185 measurement.

They are scored from different instruments and must be reported separately. Part B was written
before the build that carries it; Part A was written before the earlier (never-flashed) one.

## Part A — WS1: the kernel pc-state reconcile

### The pre-fix control (boot `59d9c272`, final capture)

Source: `pcfine_final_boot59d9c272.txt` (md5 `d616346c6dca6fe6c4052998330424cb`,
87 616 records, uptime 3157.20 .. 15 884.20 s) and `dmesg_final_boot59d9c272.txt`
(md5 `10b95f77d3bc1a6147a54cac293f934b`, 1418 lines).

**Capture totals:** 1078 resumes, 36 pc-ack timeouts (3.3 %), 21 fatals in the ring.

**The four classes** (`pl` = `pc_line_level` read live from the wire; `td` = `rx_tearing_down`):

| class | meaning | resumes | timeouts | rate |
| :-- | :-- | --: | --: | --: |
| `pl=1, td=0` | wire HIGH, driver awake — "race" | 838 | 19 | **2.3 %** |
| `pl=1, td=1` | wire HIGH while tearing down | 174 | 0 | 0.0 % |
| `pl=0, td=1` | wire LOW, driver agrees collapsed — "teardown" | 41 | 7 | **17.1 %** |
| `pl=0, td=0` | **wire LOW, driver believes AWAKE** — "STALE" | 25 | 10 | **40.0 %** |

Doc 184 §5.1 named only the first three and recorded the fourth as "NEITHER" (8 of 33 timeouts,
unexplained). It is the highest-rate class and it is the target of patch 825.

**The teardown class's timeouts cluster in a 110 ms band.** All 7:
`since_susp` = 1.220, 1.200, 1.260, 1.280, 1.200, 1.200, 1.170 — i.e. **[1.17, 1.28] s**.
That is `BAM_DMUX_AUTOSUSPEND_DELAY` (`qcom_bam_dmux.c:36`, 1000 ms) plus ~170–280 ms for the
modem's collapse to land.

## Frozen predictions

| id | prediction | falsified if |
| :-- | :-- | :-- |
| **P-W1** | the `pl=0, td=0` class timeout rate falls from **40.0 % (10/25)** to **< 5 %** | ≥ 5 % |
| **P-W2** | the overall `pc_timeout_count` rate falls from **3.3 % (36/1078)** to **< 2.0 %** | ≥ 2.0 % |
| **P-W3** (control) | the `pl=1, td=0` race class stays at **2.3 % ± 1.5 pp** (i.e. 0.8 – 3.8 %) | outside that band |

P-W3 exists because the patch must not slow the healthy path. If the race class moves, the
patch changed something it should not have, whatever P-W1 and P-W2 say.

## Required n — stated before the run

The pre-fix capture has **25** `pl=0, td=0` resumes. To distinguish 40 % from < 5 % at 80 %
power needs roughly 15 post-fix events. **Do not score P-W1 until n ≥ 20 post-fix
`pl=0, td=0` resumes.** Report `n` and the count, never "it stopped happening".

P-W3 needs the same order of magnitude on the race side (838 pre-fix, so it will accumulate
faster). P-W2 needs ≥ 500 post-fix resumes to be meaningful at the 3.3 % → < 2.0 % scale.

## What this pre-registration does NOT claim

Patch 825 fixes the **pc-ack timeout** — a latency defect that costs a ~2 s stall per event. It
is **not** claimed to fix the 903 s fatal. The pc-line timing is a lead toward the always-on
clock (Doc 177); the fatal's clock is a separate measurement and needs its own instrument. Any
statement about the fatal must be made from a fatal count, not from this file.

## Why the fix follows from the control

`bam_dmux_runtime_resume()` (`qcom_bam_dmux.c:2074`) skips the pc_state wait when `pc_state`
is already true:

```c
	if (!READ_ONCE(dmux->pc_state)) {
		ret = wait_event_timeout(&dmux->pc_wait, READ_ONCE(dmux->pc_state),
					 msecs_to_jiffies(1000));
```

`pc_state` tracks `pc_irq` *edges* only (set at `:1942`, cleared at `:1968`). The `pl=0, td=0`
class is exactly the state where it is stale-high while the wire is low — so the wait is
skipped, the vote goes to a collapsed modem, and the 2000 ms ack wait expires. The RX watchdog
has the same blind spot (its resync at `:1304` is gated on `!pc_state`, and its awake branch at
`:1381` never re-reads the wire).

Patch 825 makes `pc_state` match the wire after an ack timeout, and teaches the watchdog to
re-read the wire on its awake branch. It adds no delay and reorders nothing on the healthy path
— which is what P-W3 tests.

---

## Part B — WS3: the ModemManager port-probe settle timers

**Source of the control:** a *different* boot, `a9fd907c`, captured in Doc 170 —
`evidence/170_two_fixes_for_the_smd_poll_uaf/bootA_a9fd907c_logroll.log.gz`
(13 168 lines, 11 SSRs between 13:00:12 and 14:52:27 on 2026-09-21).

Cross-boot comparison is legitimate here because **the change is purely userspace**: the three
constants are in `src/mm-plugin-manager.c`, ModemManager is unpatched upstream 1.24.0 in both
boots, and no kernel path participates in the timers. The kernel only affects how fast the ports
re-appear, which is measured directly (see the probe-start column below).

### What the measurement showed

Every SSR produces the same sequence, and the timings are near-deterministic:

| SSR | `wwan0qmi0` add | `probe step: start` | modem object created | Interface up |
| :-- | --: | --: | --: | --: |
| 1 | 13:00:14 | +2 s | +4 s | +16 s |
| 2 | 13:02:14 | +2 s | +4 s | +15 s |
| 3 | 13:05:00 | +3 s | +5 s | +15 s |
| 4 | 13:08:04 | +2 s | +4 s | +16 s |
| 5 | 13:23:06 | +2 s | +4 s | +17 s |
| 6 | 13:38:08 | +2 s | +4 s | +25 s |
| 7 | 13:53:09 | +2 s | +4 s | +14 s |
| 8 | 14:08:33 | +3 s | +5 s | +17 s |
| 9 | 14:23:37 | +2 s | +4 s | +15 s |
| 10 | 14:36:42 | +2 s | +4 s | +14 s |
| 11 | 14:52:27 | +2 s | +4 s | +17 s |

**`add → modem created` is 4 s in 9 of 11 SSRs and 5 s in 2 of 11** — and the QMI probe itself
(`probe step: start` → `QMI` → `done`) completes **inside one second**, so 3 of those 4 seconds
are spent waiting on a timer rather than on the modem.

### The mechanism

`src/mm-plugin-manager.c:717-732`:

```c
#define MIN_WAIT_TIME_MSECS 2000
#define MIN_PROBING_TIME_MSECS 4000
#if defined WITH_UDEV
# define EXTRA_PROBING_TIME_MSECS 2000
#else
# define EXTRA_PROBING_TIME_MSECS 4000
#endif
```

`device_context_complete()` (`:871`) refuses to finish the support check while either of the
last two timers is pending:

```c
	if (device_context->min_probing_time_id) { ... return; }
	if (device_context->extra_probing_time_id) { ... return; }
```

All three timers are armed when the device context is created (i.e. at the port add) and all
three expire at +2 s / +4 s / +4 s — exactly the measured probe-start and creation times. The
`+2 s` rows are the `MIN_WAIT_TIME_MSECS` and the `+4 s` rows are `MIN_PROBING_TIME_MSECS`.

OpenWrt builds ModemManager with `-Dudev=false` (`feeds/packages/net/modemmanager/Makefile:76`),
so `WITH_UDEV` is **not** defined and the extra probing time is the doubled 4000 ms branch.

The comments in `device_context_complete()` say "2500ms" and "1500ms"; both are stale and do not
match the constants. Read the constant, not the comment.

### What the measurement FALSIFIED (recorded so it is not re-opened)

- **"Shorten ModemManager's probe retry backoff."** There is no backoff to shorten. The retry is
  event-driven: `mm-base-manager.c:496-499` re-runs the support check on the next port event, and
  that event arrives ~3 s later on its own. The first `create_modem` failure
  (`mm-plugin-qcom-soc.c:52`, "at least a QMI port is required") is a *symptom* of the port being
  unprobed, not a cost — it happens concurrently with the probe.
- **"Stop netifd from tearing modem down on link-loss."** The teardown is instantaneous (same
  second as the port release) and *correct*: the modem's WDS session dies with the SSR, so the
  bearer is genuinely invalid. The WS2 changes are bounded-timeout hygiene, not a latency win.
- **The MM recovery path is not the `additional_port` retry.** It is the timer expiry.

### Frozen predictions

| id | prediction | falsified if |
| :-- | :-- | :-- |
| **P-MM1** | the `add → modem created` phase falls from **4.0 s** (9/11 exactly 4, 2/11 exactly 5) to **≤ 2.0 s** | median > 2.5 s |
| **P-MM2** (control) | every post-fix SSR still yields a modem with **all 9 ports** (8 × `net/wwanN` + 1 × `wwan/wwan0qmi0`) and no `missing net port` error | any SSR with < 9 ports, or any `missing net port` |
| **P-MM3** (secondary) | the `add → Interface up` interval falls by **≥ 1.5 s** against a pre-fix median of **16 s** (10 of 11 in 14–17 s, one at 25 s) | the median falls by < 1.0 s |

**P-MM2 is the safety control and is the one that matters.** The three timers exist so that
late-arriving ports are not missed; halving them is only safe if the port set is still complete
when `create_modem` runs. The pre-fix control had 8 of 9 ports at the first attempt in SSR 1, so
this is a real risk, not a formality. If P-MM2 fails the patch is reverted or reduced, whatever
P-MM1 says.

**P-MM3 is labelled secondary on purpose.** The MM phases are serial (the radio registers within
~1 s of MM's `enable`, and MM's `enable` triggers that registration), so the saving *should*
propagate end-to-end — but the pre-fix outlier (SSR 6, 25 s, an 11 s GNSS/AGPS stall in
`load supported assistance data types`) shows the tail is not governed by these timers. A P-MM3
miss with P-MM1 and P-MM2 hits is a *finding*, not a refutation.

### Required n — stated before the run

The pre-fix control is **11 SSRs**. The timers are deterministic, so P-MM1 is informative at
n = 5; **P-MM2 needs the same n = 11** because it is looking for a rare incomplete-port case;
**P-MM3 needs n ≥ 10** because of the outlier. Report `n` (SSR count) with every number. Never
report "the stall is fixed" from a single recovery.

### What Part B does NOT claim

- It does **not** touch the 903 s fatal, and must not be cited for it.
- It does **not** explain the **consistent 5 s** in `running setup for device → SIM hot swap
  setup succeeded`, which is present in **11 of 11** SSRs and is therefore the *larger* remaining
  AP-side block. Its mechanism is unidentified; the lead is the QMI UIM hot-swap chain in
  `src/mm-shared-qmi.c:4108` (each request with a 10 s timeout). This build enables ModemManager
  DEBUG logging to identify it — see Doc 185 §6.
- It does **not** claim the user-visible stall is solved. At best it removes ~2 s of ~15.5 s.
