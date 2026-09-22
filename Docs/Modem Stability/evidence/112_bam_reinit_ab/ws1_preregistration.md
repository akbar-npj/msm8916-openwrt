# WS1 pre-registration — frozen before patch 825 was written

**Date:** 2026-09-22
**Frozen at:** boot `59d9c272`, uptime 15 884 s, before the patch-825 build.
**Purpose:** Doc 167 / Doc 183 / Doc 184 require the thresholds and the control to be written down
BEFORE the change is deployed. This file is that record. It must not be edited after the build.

## The pre-fix control (boot `59d9c272`, final capture)

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
