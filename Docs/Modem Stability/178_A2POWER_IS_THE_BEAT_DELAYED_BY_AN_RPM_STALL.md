# 178 — The `a2_power.c:1189` fatal is 58.68 s LATE, and the RPM log stalls for exactly that window — the assert may be DELAYED by the stall, not the clock

**Date:** 2026-09-22
**Device:** HMUF02-v5 ("hmu05"), OpenWrt 6.12.94, stock HMU05 modem firmware
**Answers:** Doc 177 §7 **P1** (pre-registered: the next fatal lands at
`prev_fatal + 903.675 ± 0.005 s`) — and **P1 was NOT met**.
**Status:** MEASURED, n = 1. A striking, exact correlation — and a **rate-based control that
finds nothing like it** (the control never drops below **3 637 words/s** in 1 451 s; this
capture drops to **22**). Not an established *mechanism*: n = 1, and the direction (cause vs
consequence) is undetermined. Doc 170 §8.20's contrary conclusion is addressed in §4 — it
rested on a gap-based measure that §8.20(b) itself declared invalid for this bursty log.

---

## 1. SOP statement

| SOP step | Status |
| :-- | :-- |
| Stock-HMU05 comparative ground truth used | **Yes** — stock modem firmware; `modem.mdt` unchanged; the RPM ring is read, never written |
| Measurement frozen before scoring | **Yes** — the capture was written to `/tmp/rpm_track_live.txt` on the host and gzipped into `evidence/178_a2power_stall_delay/rpm_track_fatal10.txt.gz` before scoring; the control is the committed `scratch/rpm11/rpm11.txt`, also gzipped here |
| Comparability decided from KERNEL state | **Yes** — the interval is split at the fatal line; both captures are single-kernel, single-boot |
| Routing-path connectivity recorded | **Partly** — `wwan0` held `10.84.147.181/30` before the fatal; a `ping` during the fatal window returned 100 % loss, which is the expected symptom, not a separate finding |
| Physical/environmental context established first | **Yes** — device desk-mounted, USB-tethered, stationary; no bus travel |
| Control asserted as present | **Yes, and it is the load-bearing caveat** — a second capture (`rpm11`, 2026-09-21, 1451 s) is analysed with the same code |

---

## 2. The pre-registered prediction, and what happened

Doc 177 §7 **P1**: *the next boot's consecutive `common_timer` AP intervals will be
903.675 ± 0.005 s.*

Fatal #10 landed at AP **8719.020382 s**, signature **`a2_power.c:1189`** — interval from #9
**962.357690 s**, i.e. **+58.682 s late**. **P1 is not met.**

| # | AP time | AP interval | signature |
| --: | --: | --: | :-- |
| 9 | 7756.662692 | 903.677016 | `lte_ml1_common_timer.c:390` |
| **10** | **8719.020382** | **962.357690** | **`a2_power.c:1189`** |

This is the *known* behaviour, not a new failure of the clock: `a2_power.c:1189` is the
**activity-correlated** signature (Doc 162 taxonomy — n = 7 spanning 68.5–941.3 s, "not a
clock at all"), and the regime was not idle — the RX watchdog stopped logging after
**8543.642393** (`quiesced 300s`), so the RX ring was armed (modem awake) for the last
~176 s before the fatal.

---

## 3. The RPM log stalls for the fatal's whole lateness

`rpmring -t` emits a `# NEW` line each time the RPM log ring has new records. The ring
turns over every ~100–300 ms when the RPM is busy, so a multi-second gap means the RPM
wrote **nothing** for that long. In the live capture the gap series collapses exactly at
the predicted beat and resumes at the fatal:

| quantity | value |
| :-- | --: |
| fatal #9 | 7756.662692 |
| **predicted beat** (= #9 + 903.675206) | **8660.337898** |
| fatal #10 | 8719.020382 |
| **excess (late by)** | **+58.682 s** |
| last RPM batch before the stall | 8658.929 |
| first RPM batch after the stall | 8719.243 |
| **RPM stall duration** | **60.314 s** |
| stall_start − beat | **−1.409 s** |
| fatal − stall_end | **−0.223 s** |
| stall duration − excess | 1.632 s |

Inside that window the ring advanced only **7 072 words** where the surrounding rate
(~8 800 words/s) predicts **~470 000** — the log was **96 % stalled** for 60 s.

**Reading (H1).** The ~903.675 s timer expires **on schedule** at 8660.34. The resulting
transition needs the RPM, the RPM stalls, and the `a2_power.c:1189` assert surfaces only
when the stall clears at 8719.24. So **the dmesg timestamp is `beat + stall`, not the beat.**
That reading is what the corpus already suspects from the other direction — Doc 165/Doc 149
place the stall in `rpm.sync` (`0xc08bebd0`, unbounded flush loops), i.e. on the way to the
fatal.

**What this would explain if true:** the `a2_power.c:1189` series was never "not a clock" —
it is the **same clock**, with a variable assert delay added. Doc 162's "`a2_power` spans
68.5–941.3 s" would be `903.675 + stall`, i.e. stall ∈ [−835, +38] s. The negative end does
not fit, so H1 cannot be the whole story — but the positive end does.

---

## 4. The control — and why the *rate* measure, not the *gap* measure, is the right one

**Doc 170 §8.20 ran this experiment before me and concluded the opposite:** *"the RPM stall is
an independent, recurring, self-recovering modem-side event … NOT the fatal's mechanism."*
It reached that from **gap** statistics — the nearest preceding quiet ≥ 1 s before each of its
fatals #9/#10/#11 was **1.268 / 1.145 / 3.141 s**, i.e. that era's normal cadence.

**But Doc 170 §8.20(b) itself warns that this measure is invalid:** *"the RPM's log is
**BURSTY** (so 'quiet' ≠ 'stall'; the cadence must be measured per era, and **50.5 % of the
capture is inside a ≥ 0.5 s gap**)."* A "nearest quiet period" test cannot see a *rate*
collapse, and a bursty log manufactures large gaps as a matter of course.

The burstiness-robust measure is the **write rate from the ring's own counter** (header
`+0x38`), which is authoritative and immune to ring overruns — the live capture logs
**26 `# TRACK OVERRUN` lines**, yet the counter still counts every write. Rate = Δcounter / Δt
over 30 s bins:

| capture | usable 30 s bins | median | p5 | **min** | bins < 200 words/s |
| :-- | --: | --: | --: | --: | --: |
| LIVE (fatal #10) | 14 | 5 081 | **22** | **22** | **1 / 14** |
| CONTROL (`rpm11`, 2026-09-21) | 48 | 9 513 | 6 529 | **3 637** | **0 / 48** |

**The control never drops below 3 637 words/s in 1 451 s. The live capture drops to
22 words/s — a 165× lower floor — in exactly one bin, the one containing the fatal.** On the
*gap* measure the control looks *more* stalled (38.9 % of its span in >2 s gaps, max 13 264 ms,
vs the live's 19.0 % and 25 997 ms); on the *rate* measure it is nowhere near.

**So Doc 170 §8.20's negative is not confirmed by a better version of its own test — it is
replaced by one that can see the event.** (Its fatals were also not clean clock beats: #10
landed **182.736 s** after #9, mid-cascade, so H1 predicts *no* stall for them either.)

### What still does NOT follow

1. **n = 1.** One fatal, one collapse. The rate control is decisive that the collapse is
   *anomalous*; it is **not** evidence about *direction* — cause, consequence, or common cause.
2. **The instrument is not formally cleared.** The live capture polled `/dev/mem` every 50 ms;
   the control every 4 ms — so the *less* intrusive capture is the one that collapsed, which
   argues the polling is not the cause. But they are also different boots. **P3′ (a 50 ms
   capture through a window with NO fatal) is the clean control**, and is running.
3. **A competing reading of the same numbers.** The collapse could be the *consequence* of the
   failing assert path rather than its cause. H1 does not require a direction; P2′ tests the
   *correlation* (`excess ≈ stall`), which is what the data can actually decide.

---

## 5. Pre-registration

**P2′ (the H1 test, cheap, applies to every future `a2_power.c:1189` fatal).** For each such
fatal, compute `excess = fatal − (prev_fatal + 903.675206)` and the duration of the
contiguous RPM-log stall bracketing it. **H1 predicts `stall ≈ excess` (within ~2 s).** Two
or three instances make it a mechanism; a single counter-example kills it.

**P3′ (the instrument control, one capture, no fatal required).** Run `rpmring -t` at
**50 ms** through a window containing **no** fatal. If a 50 ms-polled idle window also
produces a >25 s stall, then the live capture's stall is an instrument artifact and §3
collapses. If it does not, the instrument is cleared.

**Falsifier for §3's reading:** an `a2_power.c:1189` fatal whose excess is *small* while a
long stall precedes it, or a long stall with **no** fatal at its end.

---

## 6. What this does to Doc 177

Nothing in Doc 177 §3–§5 changes: the `common_timer` intervals are still the invariant, and
the control captures used there are unaffected. Doc 177 §7's **P1** is scored **NOT MET**,
with the confound named — the cycle was not idle, and the fatal was the activity-correlated
signature, exactly as the Doc 162 taxonomy predicts. **The next idle cycle is the clean test
of P1**, not this one.
