# Pre-registration — the next fatal after #10 (boot `eafa19f6`)

**Written 2026-09-22, while the `rpmring -t -n 17000 -i 50` capture is STILL RUNNING.**
At the moment of writing the capture is at `t = 9196.6 s` and the device has **10** fatals.
No fatal has occurred since #10 at `8719.020382 s`. Nothing below is fitted to the answer.

## The clock

All ten fatals of this boot, with the residual against a free-running clock anchored on
fatal #2 and using the period obtained from the **five consecutive `common_timer` intervals
only** (spread 7.5 ms):

    P = 903.675206 s      (identical to the cross-boot constant of Doc 177, to 1e-6 s)

| #  | dmesg ts | signature | beat k | residual |
|----|----------|-----------|--------|----------|
| 1  | 524.211653  | a2_power.c:1189              | -1 | **+0.787706** |
| 2  | 1427.099153 | lte_ml1_common_timer.c:390   |  0 | +0.000000 |
| 3  | 2330.773740 | lte_ml1_common_timer.c:390   |  1 | -0.000619 |
| 4  | 3234.448768 | lte_ml1_common_timer.c:390   |  2 | -0.000797 |
| 5  | 4138.119715 | lte_ml1_common_timer.c:390   |  3 | -0.005056 |
| 6  | 5041.798167 | lte_ml1_common_timer.c:390   |  4 | -0.001810 |
| 7  | 5949.210659 | a2_power.c:1189              |  5 | **+3.735476** |
| 8  | 6852.985676 | lte_ml1_common_timer.c:390   |  6 | +3.835287 |
| 9  | 7756.662692 | lte_ml1_common_timer.c:390   |  7 | +3.837097 |
| 10 | 8719.020382 | a2_power.c:1189              |  8 | **+62.519581** |

Two facts fall out, neither of which is in Doc 177 or Doc 178:

1. **Every `a2_power.c:1189` fatal is late** (+0.79, +3.74, +62.52 s). Every
   `lte_ml1_common_timer.c:390` fatal sits on the clock to within 5 ms *or* on the
   offset that the preceding `a2_power` fatal established.
2. The offset is **carried forward**: after #7's +3.735 the offset holds at +3.836 / +3.837
   through #8 and #9, i.e. the clock does not lose the delay.

## Predictions for beat k = 9 (the next beat; the capture ends at ~9800.8 s)

    free-running beat k=9                      = 9560.176007 s
    + the offset now carried (+3.837)          = 9564.012
    + the offset set by #10      (+62.520)     = 9622.696

Note that `9622.696` is also exactly the *re-armed* prediction
(`fatal #10 + P = 8719.020382 + 903.675206`). The two models that predict a persistent
offset and a re-armed clock are therefore **not** separable here; both are separable from
"the offset reverts", which predicts **9560.2–9564.0**.

**P-NEXT.** The next fatal lands within ±1.5 s of one of:
  (a) `9560.18` — offset reverts to zero ⇒ the delay is not carried;
  (b) `9564.01` — offset reverts to +3.84 ⇒ only the last a2_power delay is transient;
  (c) `9622.70` — offset persists / clock re-arms from the last fatal.
Anything else falsifies the free-running-clock model outright.

**P-STALL.** If the next fatal is `a2_power.c:1189`, H1 (Doc 178) predicts the contiguous
RPM-log stall bracketing it is `≈ fatal − 9560.176` in length, i.e. `≈ 62.5 s` for (c),
`≈ 0–4 s` for (a)/(b). If the next fatal is `lte_ml1_common_timer.c:390`, H1 predicts
**no** stall at all — that is the within-boot differential Doc 178 could not get.

**P-INSTRUMENT (P3′).** Over the whole capture the RPM-log *rate* profile must show no
30 s bin below ~200 words/s unless the next fatal is an `a2_power` one. The control
min was 3,637 words/s; the fatal-#10 capture had one bin at 22 words/s.
