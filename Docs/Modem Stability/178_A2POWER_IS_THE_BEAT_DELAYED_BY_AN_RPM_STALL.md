# 178 — The `a2_power.c:1189` fatal is 58.68 s LATE, and the RPM log stalls for exactly that window — the assert may be DELAYED by the stall, not the clock

**Date:** 2026-09-22
**Device:** HMUF02-v5 ("hmu05"), OpenWrt 6.12.94, stock HMU05 modem firmware
**Answers:** Doc 177 §7 **P1** (pre-registered: the next fatal lands at
`prev_fatal + 903.675 ± 0.005 s`) — and **P1 was NOT met**.
**Status:** MEASURED, n = 1. A striking, exact correlation with a plausible mechanism,
**not** an established result: the control shows RPM-log stalls are routine, and the
instrument cannot be fully cleared of causing the regime change.

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

## 4. The control, and why this is not yet established

A second capture (`rpm11`, 2026-09-21, 1451 s, same analyser) shows **RPM-log stalls > 2 s
are routine**:

| capture | batches | span | median gap | gaps > 2 s | time in >2 s gaps | max gap |
| :-- | --: | --: | --: | --: | --: | --: |
| LIVE (fatal #10) | 819 | 404.5 s | 104.0 ms | **7** | 76.9 s (**19.0 %**) | **25 997 ms** |
| CONTROL (rpm11) | 7627 | 1451.0 s | 31.8 ms | **161** | 564.5 s (**38.9 %**) | 13 264 ms |

So a multi-second RPM quiet period is **normal**, and the control is *more* stalled overall
than the live capture. What is **not** normal in the live capture is the **concentration**:

* **78 % of the live capture's stall time sits in the single 60 s window that brackets the
  fatal** (58.3 s of 76.9 s).
* The control's largest stalls are **spread** (6469.8, 7044.2, 6563.6, 6357.1, 7143.0,
  6938.5 s — no two adjacent).
* The live's 25 997 ms gap is **2.0× the control's maximum** (13 264 ms), on 3.6× less span.

**Two things this does NOT establish, and they matter:**

1. **n = 1.** One fatal with one stall. The correlation is exact to ~1.4 s and ~0.2 s, which
   is why it is worth recording — but a single instance cannot separate "the stall causes the
   delay" from "the stall is part of the same failure".
2. **The instrument is not cleared.** The capture reads `/dev/mem` every 50 ms for 404 s, and
   the modem woke at 8543.6 — **133 s into the capture**. The wake is not at the capture's
   start, which argues against a start transient, but a sustained-polling effect is not
   excluded. **A capture that runs the identical instrument through an idle window with NO
   fatal is the control that would settle it** (the control here used a different poll
   interval, 4 ms, and a different boot).

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
