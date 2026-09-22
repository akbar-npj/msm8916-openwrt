# 179 — THE FATAL OFFSET IS A CARRIED STATE, AND THE PRECURSOR IS SIGNATURE-SPECIFIC

**Date:** 2026-09-22
**Boot:** `eafa19f6` (OpenWrt, kernel 6.12.94), the boot of Docs 177/178
**Evidence:** `evidence/178_a2power_stall_delay/` — `P3_PREREG.md` (written before the capture
finished), `p3_score.py`, `rpm_track_p3_boot_eafa19f6.txt.gz` (the 861 s live capture),
`rpm_track_fatal10.txt.gz`, `rpm_control_rpm11.txt.gz`, `analyze_stall.py`

---

## 1. SOP compliance

| SOP step | done? |
| :-- | :-- |
| Comparative protocol against the stock-HMU05 ground truth | **n/a** — this doc changes no baseband and no driver; it is a measurement of the live AP/module state |
| Pre-register the prediction **before** the data exists | **YES** — `P3_PREREG.md`, written at capture time `t=9196.6 s` while the capture was still running and before fatal #11 existed |
| Freeze the capture, hash it, score the frozen copy | **YES** — capture completed (`TRACK done polls=17000 overrun=46 records=152715`), copied to `/tmp/p3_raw.txt`, gzipped into the repo |
| Keep a control | **YES** — three: the 1451 s `rpm_control_rpm11` capture, the pre-collapse baseline inside the fatal-#10 capture, and the 670 s fatal-free prefix of the P3′ capture |
| Verify the instrument before resting a claim on it | **YES, and it changed a conclusion** — §7.3 (the ring is lossy) and §7.4 (the cross-clock comparison is safe) |
| State what is *not* established | **YES** — §8 |

---

## 2. The pre-registration and its score

`P3_PREREG.md` fixed, from the **five consecutive `common_timer` intervals only**
(spread 7.505 ms), a period

    P = 903.675206 s        (identical to Doc 177's cross-boot constant to 1e-6 s)

anchored on fatal #2 = 1427.099153, and pre-registered three predictions for the
next fatal:

| prediction | pre-registered value | observed | verdict |
| :-- | --: | --: | :-- |
| **(a)** offset reverts to zero | 9560.18 | no fatal at 9560.176 (device reached 9609 with count still 10) | **FALSIFIED** |
| **(b)** offset reverts to +3.84 | 9564.01 | — | **FALSIFIED** |
| **(c)** offset persists / clock re-arms | 9622.696 | **9622.891300** (`lte_ml1_common_timer.c:390`) | **CONFIRMED** (+0.195 s) |
| **P-STALL** — if the next fatal is `common_timer`, H1 predicts **no** stall | no stall | **no >2 s stall** (max gap 1.6 s, which brackets the fatal) | **CONFIRMED** |
| **P3′** — an idle 50 ms-polled window must not reproduce the 60.3 s / 26.0 s-gap behaviour | no stall | max gap **2.527 s**; 3 gaps >2 s totalling 7.6 s (0.9 %); 29 bins, min **137 rec/s**, **0** bins <20 | **CONFIRMED** |

---

## 3. Result 1 — the fatal offset is a **carried state**

All eleven fatals of this boot against the single free-running clock:

| #  | dmesg ts | signature | beat k | **residual** |
|----|----------|-----------|--------|-------------:|
| 1  | 524.211653  | a2_power.c:1189            | −1 | **+0.787706** |
| 2  | 1427.099153 | lte_ml1_common_timer.c:390 |  0 | +0.000000 |
| 3  | 2330.773740 | lte_ml1_common_timer.c:390 |  1 | −0.000619 |
| 4  | 3234.448768 | lte_ml1_common_timer.c:390 |  2 | −0.000797 |
| 5  | 4138.119715 | lte_ml1_common_timer.c:390 |  3 | −0.005056 |
| 6  | 5041.798167 | lte_ml1_common_timer.c:390 |  4 | −0.001810 |
| 7  | 5949.210659 | a2_power.c:1189            |  5 | **+3.735476** |
| 8  | 6852.985676 | lte_ml1_common_timer.c:390 |  6 | +3.835287 |
| 9  | 7756.662692 | lte_ml1_common_timer.c:390 |  7 | +3.837097 |
| 10 | 8719.020382 | a2_power.c:1189            |  8 | **+62.519581** |
| 11 | 9622.891300 | lte_ml1_common_timer.c:390 |  9 | **+62.715293** |

Three things follow, and the third is new.

1. **The offset is a step function, not a per-fatal delay.** It sits at ≈0 for fatals 2–6,
   jumps to **+3.74** at #7, holds at **+3.84** through #8 and #9, jumps to **+62.52** at #10,
   and holds at **+62.72** at #11. Between jumps it drifts by only 0.002–0.2 s.

2. **The signature does NOT determine lateness.** Fatal #11 is a `common_timer` fatal and it
   is **62.7 s late**. Doc 178's implicit reading ("a2_power is the late one") is wrong; what
   makes a fatal late is the offset *state*, and the offset happens to be set by the two
   `a2_power` events.

3. **Both offset-setting events are `a2_power` fatals (#7, #10), and the only one we captured
   (#10) was preceded by a 60.3 s RPM-log collapse.** So the working model is:

   > the ~903.675 s clock is free-running; an `a2_power` event both **fires late** and
   > **permanently shifts the phase** by approximately the collapse duration; every later
   > fatal then inherits the shifted phase.

   The shift is `60.314 s` (collapse) vs `62.52 s` (offset at #10) and `+62.72 s` (at #11) —
   i.e. the shift is the collapse **plus ~2.2 s**, consistently at both #10 and #11. That
   2.2 s is not explained.

**This is a correction to Doc 178, not a confirmation of it.** Doc 178's H1 ("dmesg time =
beat + stall") is right about *when the assert surfaces*, but wrong to treat the delay as a
per-fatal quantity: it is a latched phase shift, which is why the very next fatal — with no
stall at all — was just as late.

---

## 4. Result 2 — the precursor is **signature-specific**, and the collapse is real

The P3′ capture is the instrument control Doc 178 could not get. Both captures used the same
tool, the same 50 ms poll, the same boot, and comparable idle traffic:

| | fatal-#10 capture | P3′ capture (861 s, contains fatal #11) |
| :-- | --: | --: |
| max NEW-gap | **25.997 s** | **2.527 s** |
| gaps >2 s | 7, total 76.9 s (**19.0 %** of span) | 3, total 7.6 s (**0.9 %**) |
| worst 30 s bin | **22 rec/s** | **137 rec/s** |
| bins <20 rec/s | 1 | **0** |

So a 50 ms-polled idle window does **not** spontaneously produce the collapse, and the one
fatal inside it (`common_timer`, #11) produced none either. **The 60.3 s collapse at #10 is a
real event, and it is associated with the `a2_power` signature — n = 1 vs n = 1.**

---

## 5. Result 3 — the collapse is the **full-resource-set batches stopping**

The RPM log's transactions come in three sizes, and the split is the whole story
(0x0144 opens a transaction; counts are over the frozen captures):

| window | span | txns/s | 9-record (client req) | ≥50-record (full resource set) |
| :-- | --: | --: | --: | --: |
| baseline 8410–8590 | 180.0 s | 3.18 | 1.60 /s | **1.58 /s** |
| **COLLAPSE 8658.9–8719.2** | 60.3 s | 0.35 | 0.35 /s | **0.00 /s** ← exactly zero |
| post-SSR 8720–8815 | 95.0 s | 11.15 | 8.37 /s | 2.46 /s |
| P3′ baseline 9580–9620 | 40.0 s | 3.35 | 1.75 /s | **1.60 /s** |
| fatal-#11 window 9620–9630 | 10.0 s | 46.50 | 44.90 /s | 1.20 /s |

During the collapse the RPM still receives the modem's 9-record client-1 requests
(20 × 9-record + 1 × 10-record in 60.3 s, sequence counter incrementing 0x152d→0x1533), and
the RPM's own 19.2 MHz clock runs normally throughout. **What stops is only the ≥50-record
batch that walks the entire resource set** (`clk0`, `clk1`, `clk2`, `clka`, `ldoa`, `smpa`,
`bslv`, `bmas`) — i.e. the RPM's resource **re-evaluation**, which is what a power-collapse
sleep/wake transition produces.

**This is the `a2_power.c:1189` root cause observed directly.** The corpus root cause is
"the sleep count is not incrementing" (`FUN_c0ce7fe0`). A sleep count that is not incrementing
is exactly "no sleep/wake re-evaluation is happening", and the RPM log shows that state as
*zero full-resource-set batches for 60 s*. The collapse is therefore not an unrelated RPM
fault that delays a report — **it is the failure itself, seen from the RPM side**, beginning
1.4 s before the clock beat (`stall_start − beat = −1.409 s`).

---

## 6. What the sequence looks like end to end

```
 8595 ─ 8658.9   RPM activity ELEVATED 7–19 txn/s (2–6× baseline)
 8658.929        ← beat 8660.338 arrives; full-resource-set batches STOP (0.00/s)
 8658.9 ─ 8719.2 the collapse: only the modem's 9-record client-1 requests, 0.35/s
 8719.020382     fatal #10  a2_power.c:1189   (residual +62.52 s)
 8719.24         collapse ends; 8720 bin = 101.8 txn/s (SSR + firmware reload)
 8719 ─ 9620     baseline restored (3.17 txn/s, 175 records/s, rock steady)
 9622.891300     fatal #11  lte_ml1_common_timer.c:390  (residual +62.72 s)
                 NO collapse before it; a 137 txn/s burst follows it
```

Note the asymmetry: fatal #10 has a **60 s precursor and no burst**, fatal #11 has **no
precursor and a 137 txn/s burst**. Both are late by the same carried offset.

---

## 7. Instrument corrections (four, all measured this session)

**7.1 — record word 2 is NOT a type; it is the high word of a 64-bit timestamp.**
The corpus (and Doc 150) calls `word[2]` "a small integer that changes rarely (`0x24`→`0x25`→
`0x26`), not decoded". It is the **high 32 bits of a 64-bit 19.2 MHz counter**. Verified 4/4:
`word[2]` increments exactly when `word[1]` wraps `0xFFFFFFFF`→`0` (`fed026f3`→`00408315`,
`fffe80e7`→`00063400`, `ffd946d1`→`014a560b`, `febb95b5`→`002bece3`). The 223.7 s wrap of
`word[1]` alone is therefore not a limit — **always read `word[2]<<32 | word[1]`.** Any earlier
analysis that treated `word[2]` as a record class is void.

**7.2 — the RPM's log clock runs 601 ppm off the AP's.**
The 64-bit counter advanced 1,070,624,559 ticks over 55.728 s of `CLOCK_MONOTONIC`
= **19,211,535 Hz** against a nominal 19.2 MHz. So the RPM's tick is not the AP's tick; do not
convert between them with a factor of 19.2e6.

**7.3 — the RPM log ring is intrinsically lossy at the current rate.**
The ring holds 256 records (`ring length 0x2000`, mask `0x1FFF`), but the RPM's bursts reach
**~300 and ~560 records**, and its peak instantaneous rate is ~9,200 rec/s (562 records in
61 ms). Measured on the P3′ capture: **46 overruns, 152,715 records emitted of 176,432 counted
= 13.4 % lost.** The loss is *not* a tool defect — `delta > 0x2000` is the ring overflowing.
Any absolute count from this ring is a lower bound. (The rate measure and the *identity* of
the surviving records are unaffected; the collapse claim in §4/§5 rests on the counter, which
advanced only 7,072 bytes in 60.3 s and had **no overrun** in that window.)

**7.4 — the cross-clock comparison in Doc 178 is SAFE.**
Doc 178 compares `rpmring`'s `CLOCK_MONOTONIC` stamps with dmesg printk stamps. Measured
directly by writing three `/dev/kmsg` markers and reading `CLOCK_MONOTONIC` immediately after:
the offset is **+5.814 / +5.719 / +5.923 ms** at 15 s spacing — constant to 0.2 ms over 30 s,
i.e. **drift < 7 ppm**. There is no NTP slew separating the two timescales, so the 1.4 s and
0.2 s agreements in Doc 178 are not clock artefacts.

---

## 8. What this establishes, and what it does not

**Established:**
* The fatal clock is free-running with `P = 903.675206 s`, reproduced within-boot from five
  consecutive intervals (spread 7.5 ms) and matching the cross-boot value to 1e-6 s.
* The fatal offset is a **latched state** that shifts at `a2_power` events and is inherited by
  later `common_timer` fatals. Pre-registered prediction (c) hit to +0.195 s.
* The 60.3 s RPM-log collapse before fatal #10 is **real** (instrument cleared) and is
  **specifically the cessation of full-resource-set batches**, i.e. of power-collapse
  re-evaluation — the `a2_power` root cause seen from the RPM side.
* The precursor is **signature-specific** at n = 1 vs 1: `a2_power` → collapse,
  `common_timer` → none.

**NOT established:**
* **Why the offset latches, and what the residual ~2.2 s is** (collapse 60.314 s vs offset
  62.52 s). No mechanism is offered.
* **n = 1 for the a2_power→collapse association.** One more captured `a2_power` fatal decides it.
* **The offset at #7 (+3.74 s) has no capture** — the prediction "an `a2_power` fatal is always
  preceded by a collapse of ≈ its offset" is untested for #7 and for #1 (+0.79 s).
* **Why #1's offset (+0.788) did not persist** while #7's and #10's did. The first fatal of a
  boot may be special; this is not established.
* **The ~903.675 s clock itself is still unidentified.** §5 links the *consequence* to a
  known root cause; it does not name the timer.
* The elevated 8595–8658.9 window (2–6× baseline) is **not** a precursor: the P3′ capture shows
  the same elevation at 9220–9310 (to 7.53 txn/s) that resolved with no collapse and no fatal.

---

## 9. Pre-registration for the next round

**P4 (the a2_power→collapse rule).** For the next `a2_power.c:1189` fatal, `rpmring -t` must
show ≥50-record transactions falling to **0.00/s** for a contiguous window beginning within
±2 s of the clock beat and ending within ±2 s of the fatal. **One counter-example kills it.**

**P5 (the offset latch).** `offset_{n+1} ≈ offset_n + (collapse duration of the a2_power event)`
within ±3 s. At #10→#11 this predicted 62.52 + 0 = 62.52 vs observed 62.72 (+0.20 s), so the
tolerance is met but the ~2.2 s excess at #10 itself is still unexplained — the next
`a2_power` event is the test.

**P6 (the timer).** The next `common_timer` fatal with no intervening `a2_power` must land at
`beat_k + offset` with |error| < 0.5 s, for every k. This is now a *stronger* form of Doc 177
P1: the clock plus a latched constant, not the clock alone.

**P7 (instrument).** `rpmring -t` at `-i 20` (not 50) should reduce the 13.4 % loss to ≈0
without changing the rate profile. If it does not, the ring is genuinely undersized and the
loss is a floor.

---

## 10. Next actions

1. **Capture every future `a2_power` fatal with `rpmring -t -i 20`** and score P4/P5.
2. **Re-run the same capture over fatal #7's window on a future boot** (offset +3.74 s is the
   cheap case: a ~4 s collapse is easy to see and hard to fake).
3. **Re-arm the coredump watcher** — it was last re-armed before this boot; fatal #11 is
   dumpable and would give the modem's own A2 power block at a *carried-offset* fatal, which is
   a different state from #10.
4. Do **not** re-open the FastDormancyService lead (Doc 176) or the "RPM is wedged" arm
   (Doc 150 §7) on the strength of §5 — the RPM is provably alive and clocking through the
   collapse; what stops is a *class of transaction*, not the RPM.
