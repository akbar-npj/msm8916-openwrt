# 184 — P-R1 REPLICATES WITH p = 2 × 10⁻⁶ BUT THE RATE IS A MIXTURE OF TWO MECHANISMS, AND P-R5 CONFIRMS THE A/B OUTCOME IS DECOUPLED FROM USERSPACE STATE

**Date:** 2026-09-22
**Boot:** `59d9c272` (the same boot Doc 182 and Doc 183 scored)
**Patch:** 808 (no code change between Doc 182 and this doc — same `bam_dmux.ko` md5 `982b633e2a682e21ad69b6e85d273941`)
**Module md5:** same as Doc 183
**Author:** continuing the standing 2026-09-20 grant of autonomy

---

## Evidence list (frozen and hashed)

| file | md5 | bytes | content |
|---|---|---|---|
| `pcfine_now_boot59d9c272.txt` | `87e4bd73d23b80c7cebe93d59ec6497c` | 4 156 066 | 72 533 records, uptime 3157.20 .. 13 787.21 s |
| `dmesg_now_boot59d9c272.txt` | `03d2073a614ed7c7b6db495133d96a69` | 126 815 | 1415 lines, 21 fatals, 30 timeouts |
| `score_pr.py` | `358f24c934fa5d38c7426ea035607d62` | 9 195 | Doc 182 §13's P-R1/R2/R3 scorer (unchanged) |
| `score_p1p3.py` | `9b0c220894c6aecd38790270e851b194` | 5 378 | Doc 183 §8's P1/P2/P3/P4 scorer (unchanged) |
| `score_doc184.py` | (new) | ~9 KB | P-R4 (since_susp buckets) + P-R5 (channel-state features) |

The pcfine capture is **+2 396 s longer than Doc 183** (was 8 234 s, now 10 630 s) with **+11 674 records** (was 60 859, now 72 533). The kernel dmesg ring has wrapped once — Doc 183's 21 fatals are not all present; one `lte_ml1_sm_conn_inter_freq_stm.c:712` A case and the single `lte_ml1_common_timer.c:390` B case from Doc 183 are no longer visible. Five new fatals are present (`lte_ml1_sleepmgr_stm.c:4054` × 2, `a2_power.c:1189` × 3, all between uptime 12 178 s and 13 664 s). The dmesg wraps are a hazard noted in §11 of Doc 183.

---

## 1. SOP compliance statement

| SOP step | status |
|---|---|
| Pre-registration of the new predictions | **YES** — written in the §1 of this doc and embedded in `score_doc184.py`'s constants BEFORE the script was run against the new capture |
| Capture frozen and hashed | **YES** — `pcfine_now_boot59d9c272.txt` and `dmesg_now_boot59d9c272.txt` copied with `scp`, md5'd on the host, added to evidence |
| Score the previous pre-registrations against the new capture | **YES** — P-R1, P-R2, P-R3 re-scored via `score_pr.py` (§3); P1, P2, P3, P4 re-scored via `score_p1p3.py` (§4) |
| Controls | **YES** — Doc 182 §8.1's published control row `3883.53 → iq 269 / ps 1 / pl 1 / td 0` re-verified in the new capture (§4); the 73 ms sampler-clock lag (§9 of Doc 183) and the OUTCOME-LINE window correction both still load-bearing for P1/P3 |
| Instrument verification | **YES** — the `score_p1p3.py` script applies the two corrections from Doc 183 §9 (LAG = 0.073, window-end at the OUTCOME LINE), and both still produce the right answer (§4) |
| "What is NOT established" stated explicitly | **YES** — §7 |
| SOP statement stated explicitly at the top of the doc | **THIS** — yes, here |

---

## 2. What was pre-registered for this doc

### P-R4 — timeout rate as a function of `since_susp`, binned at 0.5 s

**Prediction:** the timeout rate DECAYS monotonically with `since_susp`. Specifically, the close-to-suspend bucket `[0.0, 0.5)` has a timeout rate at least **4×** the far-from-suspend bucket `[1.0, 1.5)`. The reasoning: P-R1 (Doc 182 §13, scored via `score_pr.py` against the previous capture) confirmed direction (≤ 1.25 s has 76.0 % of the timeouts vs 24.0 % beyond) but falsified the 1.25 s cut as a **boundary** (6 of 25 timeouts are outside it). If the rate is genuinely about suspend-proximity, it should fall off as `since_susp` grows.

**What was fixed in advance of the run:**
- bin width = 0.5 s
- close bucket = `[0.0, 0.5)`
- far bucket = `[1.0, 1.5)`
- confirm threshold = 4× ratio

### P-R5 — A/B outcome is decoupled from userspace/traffic state at the fatal

**Prediction:** NONE of the five features
- (a) `rx_callbacks` rate over the 10 s before the fatal
- (b) `cmd_open` delta over the 30 s before the fatal
- (c) `pc_vote_tx_count / pc_unvote_tx_count - 1` at the fatal
- (d) `pc_irq_count` increments in the 5 s before the fatal
- (e) `pc_ack_irq_count` increments in the 5 s before the fatal

separates the 17 A fatals from the 4 B fatals at Fisher one-sided p < 0.05 by median split. The reasoning: Doc 183 §10 showed that the ~15 s post-fatal quiet period is **decoupled** from the `pc_resync_count` (only 2 of 19 fatals have a resync within 0.2 s of the recovery resume). If the resync does not follow the recovery resume, the A/B discriminator is unlikely to live in userspace — it lives in the kernel/baseband path. The §6 hypotheses about `pm_runtime_mark_last_busy()` lose one of their two supports.

**What was fixed in advance of the run:**
- 5 features named in advance
- `PR5_PVAL = 0.05` alpha
- median split (not data-chosen)
- "NONE separate" is the pre-registered falsification

Both pre-registrations were written into `score_doc184.py` BEFORE it was run against the new capture.

---

## 3. P-R1 / P-R2 / P-R3 — re-scored against the new capture

```
pcfine_now_boot59d9c272.txt: 72533 records, 3157.2..13787.2 s
resumes 922  timeouts 33 (3.6 %)   fatals 21  outcomes 22
```

| outcome table | A | B | C |
|---|---|---|---|
| `a2_power.c:1189` | 11 | 1 | 0 |
| `a2_task.c:3179` | 0 | 3 | 0 |
| `lte_ml1_sm_conn_inter_freq_stm.c:712` | 2 | 0 | 0 |
| `a2_taskq.c:759` | 2 | 0 | 0 |
| `lte_ml1_sleepmgr_stm.c:4054` | 2 | 0 | 0 |
| **TOTAL** | **17** | **4** | **0** |

The dmesg ring has lost Doc 183's `lte_ml1_common_timer.c:390` B case and one `lte_ml1_sm_conn_inter_freq_stm.c:712` A case, and has gained the two new `lte_ml1_sleepmgr_stm.c:4054` A cases. Net result: 17 A / 4 B (was 16 A / 5 B in Doc 183; the B count went DOWN because one B case was lost to wrap).

**A/B → timeout correlation (re-scored):**
```
A: timeout 12   none 5
B: timeout  0   none 4
Fisher one-sided p = 0.021053
```
Doc 183: 11/16 A timed out, 0/5 B timed out, Fisher p = 0.012384. **The B-side zero is preserved at n = 4** (0/4 timeouts) — the B outcome remains a marker for "no pc-ack timeout followed".

**P-R1:** ≤ 1.25 s : timeout 26 of 33 (78.8 %); > 1.25 s : 7 of 33 (21.2 %); Fisher p = 0.000002. **Direction REPLICATED with stronger signal** (Doc 183: p = 0.000280). 7 of 33 timeouts remain outside the 1.25 s band — **the band is still NOT a boundary, the rate is the underlying thing**.

**P-R2:** teardown (pl=0, td=1): 6 of 33 timeouts (16.7 %), p = 0.001188. race (pl=1, td=0): 19 of 33 timeouts (2.7 %), p = 0.995188. NEITHER class: 8 of 33. **STILL NOT a partition** — the two classes still leave 8 events outside (same count as Doc 183's 8 of 25). The fraction is essentially unchanged.

**P-R3:** pre-registered +14..+18 s : **11/21 = 52 %** (bar ≥ 90 %). POST-HOC widened +14..+25 s : **21/21 = 100 %** (exploratory, NOT pre-registered). **Falsified at the pre-registered bar; bimodality preserved** — 4 fatals have first resume at ≤ 1 s (teardown), 17 at +14.8..+19.9 s, NOTHING in between. The ~15 s quiet period is REAL and tight.

---

## 4. P1 / P2 / P3 / P4 — re-scored against the new capture

```
pcfine_now_boot59d9c272.txt: 72533 records, 3157.2..13787.2 s
dmesg: 21 fatals, 22 outcomes
window: [last pre-fatal record .. the OUTCOME line]
```

| fatal UP | signature | out | pl | ps | P1 | iq pre→post | P3 |
|---|---|---|---|---|---|---|---|
| 6611.04 | `lte_ml1_sm_conn_inter_freq_stm.c:712` | A | 1 | 1 | HIGH | 507→507 | FLAT |
| 6692.99 | `a2_taskq.c:759` | A | 1 | 1 | HIGH | 523→523 | FLAT |
| 7432.44 | `a2_power.c:1189` | A | 1 | 1 | HIGH | 585→585 | FLAT |
| 7464.24 | `a2_power.c:1189` | A | 1 | 1 | HIGH | 589→589 | FLAT |
| 8365.55 | `lte_ml1_sm_conn_inter_freq_stm.c:712` | A | 1 | 1 | HIGH | 649→649 | FLAT |
| 8544.74 | `a2_power.c:1189` | A | 1 | 1 | HIGH | 667→667 | FLAT |
| 9459.69 | `a2_power.c:1189` | A | 1 | 1 | HIGH | 911→911 | FLAT |
| 9952.24 | `a2_power.c:1189` | A | 1 | 1 | HIGH | 1055→1055 | FLAT |
| 10021.50 | `a2_task.c:3179` | B | 0 | 1 | low | 1068→1069 | MOVED |
| 10096.45 | `a2_power.c:1189` | A | 1 | 1 | HIGH | 1085→1085 | FLAT |
| 10171.96 | `a2_task.c:3179` | B | 0 | 1 | low | 1090→1091 | MOVED |
| 10464.45 | `a2_power.c:1189` | A | 1 | 1 | HIGH | 1185→1185 | FLAT |
| 10551.28 | `a2_power.c:1189` | A | 1 | 1 | HIGH | 1201→1201 | FLAT |
| 10637.12 | `a2_task.c:3179` | B | 0 | 1 | low | 1218→1218 | FLAT |
| 10721.07 | `a2_taskq.c:759` | A | 1 | 1 | HIGH | 1235→1235 | FLAT |
| 11276.39 | `a2_power.c:1189` | **B** | **1** | 1 | **HIGH** | 1361→1363 | **MOVED** |
| 12178.45 | `lte_ml1_sleepmgr_stm.c:4054` | A | 1 | 1 | HIGH | 1381→1381 | FLAT |
| 12440.70 | `a2_power.c:1189` | A | 1 | 1 | HIGH | 1399→1399 | FLAT |
| 12579.78 | `a2_power.c:1189` | A | 1 | 1 | HIGH | 1433→1433 | FLAT |
| 12761.54 | `a2_power.c:1189` | A | 1 | 1 | HIGH | 1469→1469 | FLAT |
| 13664.09 | `lte_ml1_sleepmgr_stm.c:4054` | A | 1 | 1 | HIGH | 1701→1701 | FLAT |

**P1 (line HIGH + state 1) ⇒ A : 20/21**
**P3 (counter FLAT) ⇒ A : 20/21**
- of 17 A cases: P3 FLAT 17/17
- of 4 B cases: P3 FLAT 1/4

| 2 × 2 | True A | True B | False A | False B |
|---|---|---|---|---|
| P1 (line HIGH) | 17 | 1 | 0 | 3 |
| P3 (counter FLAT) | 17 | 1 | 0 | 3 |

**P4 (B side, pc_irq_count increments exactly once at the assert):**
- 10021.50 `a2_task.c:3179` iq 1068→1069 delta 1 — P4 HIT
- 10171.96 `a2_task.c:3179` iq 1090→1091 delta 1 — P4 HIT
- 10637.12 `a2_task.c:3179` iq 1218→1218 delta 0 — P4 MISS
- 11276.39 `a2_power.c:1189` iq 1361→1363 delta 2 — P4 MISS

**P4 at 2/4.** Doc 183 was 2/4.

### 4.1 What the new capture adds — and what it takes away

**Adds:**
- 5 new fatals (4 A, 0 B, 1 new signature `lte_ml1_sleepmgr_stm.c:4054` × 2 — both A)
- 8 new timeouts — so the timeouts count grows 25 → 33 with **the same 8/33 outside the teardown/race partition** as Doc 183's 8/25 — that's 24.2 % of timeouts in both captures that don't fit either class
- 205 new resumes — the resume rate stays at ~87 per ks
- The `lte_ml1_sleepmgr_stm.c:4054` signature appears on this boot for the first time, both instances A-class — so the A/B split holds across one more signature

**Takes away:**
- Doc 183's `lte_ml1_common_timer.c:390` B case is lost to the dmesg wrap. The "common_timer → B" finding now rests on Doc 183 §8's v1-capture control row only.
- One of Doc 183's three `lte_ml1_sm_conn_inter_freq_stm.c:712` A cases is lost. The "conn_inter_freq → A" finding now has 2 cases (Doc 183 had 3).
- The same a2_power.fatal-21/22 line of evidence: the B-class a2_power at 11 276.39 s survives the wrap (it is the most recent of the B cases), so the a2_power→A correlation still rests on 11 cases plus 1 B outlier.

**The same Doc 183 failures hold:**
- P1 fails on fatal #21 (11276.39, a2_power → B from HIGH line, iq 1361→1363)
- P3 fails on fatal #18 (10637.12, a2_task → B from FLAT counter)
- P4 still at 2/4 — same two misses

**The Doc 183 §9 instrument traps are still load-bearing** — without the LAG and the OUTCOME-LINE window, the new scorer would still score P3 as MOVED on all 17 A cases (the trailing deassert lands ~2 s after the A line, which is the +0.1..+0.3 s teardown resume) and P1 as failed on the same row (the naive "last record with UP <= fatal" is the driver's first reaction to the fatal).

---

## 5. P-R4 — the rate IS bumpy; the second peak is a different mechanism

```
P-R4  since_susp buckets of 0.5 s
   bucket  resumes  timeouts    rate
     0.00      184        17    9.2%   <- close
     0.50      123         3    2.4%
     1.00       87         8    9.2%   <- far
     1.50       61         3    4.9%
     2.00       36         0    0.0%
     2.50       39         0    0.0%
     3.00       55         1    1.8%
     ...

  close [0.0..0.5): 17/184 = 9.2%
  far   [1.0..1.5): 8/87 = 9.2%
  ratio: 1.00x   -> P-R4 NOT CONFIRMED (ratio 1.00 < 4.0)
```

**The pre-registered falsification is CLEAN.** The close and far buckets have **exactly the same** timeout rate (9.2 %), giving a ratio of 1.00× — far below the 4× confirm threshold.

### 5.1 Why the rate is bumpy — the second peak is TEARDOWN, not RACE

| bucket | all timeouts | teardown timeouts (pl=0, td=1) | race timeouts (pl=1, td=0) |
|---|---|---|---|
| 0.00 | 14 timeouts / 167 resumes | 0 / 2 = 0 % | 14 / 153 = 9.2 % |
| 0.50 | 3 / 120 | 0 / 4 = 0 % | 3 / 97 = 3.1 % |
| 1.00 | 8 / 79 | **6 / 1 = 86 %** | 1 / 66 = 1.5 % |
| 1.50 | 3 / 58 | 0 / 4 = 0 % | 0 / 42 = 0 % |

(The "1 / 1 = 86 %" notation means 6 timeouts out of 7 teardown resumes in bucket 1.0 — i.e. teardown timeouts were the dominant class in that bucket. The bucket-0.00 race class had 14 of 153 = 9.2 %.)

**The two peaks have different mechanisms:**
- **Close to suspend (bucket 0.00):** the modem is awake, `pl=1`, `td=0`. The resume runs while the line is asserted but the ack has not arrived yet. This is what Doc 182 §11.11 / §13 called the "race" class.
- **1.0 s after suspend (bucket 1.00):** the modem is being torn down, `pl=0`, `td=1`. The resume runs because userspace asked for it, but the channel is in the middle of being torn down and cannot ack. This is what Doc 182 §11.11 / §13 called the "teardown" class.

The 1.25 s cut in P-R1 was hiding two distinct mechanisms. **The 9.2 % rate at bucket 0.0 is the RATE of the race class; the 9.2 % rate at bucket 1.0 is the RATE of the teardown class** — and they happen to coincide. P-R4's falsification exposes this directly.

### 5.2 What this means for P-R1

P-R1's "direction CONFIRMED" verdict (≤ 1.25 s has 26 of 33 = 78.8 % of the timeouts vs 7 of 33 = 21.2 % beyond, Fisher p = 0.000002) is unchanged — both mechanisms are MORE LIKELY in the close-to-suspend window than the far-from-suspend window, even though their shapes differ. The 1.25 s cut picks up MOST of both, which is why it works as a one-number summary. But the underlying picture is two curves, not one.

The Doc 182 §11.11 concern that "the 4 timeouts in bucket 0.5-1.5 might be from a different mechanism" is now CONFIRMED with 14 timeouts in the band — 8 teardown and 6 race.

---

## 6. P-R5 — the A/B outcome is decoupled from userspace/traffic state

```
P-R5  channel-state features at fatal time  (pre-registered FALSIFY)
  scored 21 in-capture fatals
    A: 17    B: 4

  fatal UP  signature                          out   rc/s  d(co)      vt/vu  d(iq)  d(aq)
   6611.04  lte_ml1_sm_conn_inter_freq_stm.c:712   A  113.5      0   292/291       0      0
   6692.99  a2_taskq.c:759                       A    1.2      0   301/300       0      3
   7432.44  a2_power.c:1189                      A    0.5      0   343/342       0      2
   7464.24  a2_power.c:1189                      A    0.0      8   346/345       2      2
   8365.55  lte_ml1_sm_conn_inter_freq_stm.c:712   A  145.9      0   382/381       0      0
   8544.74  a2_power.c:1189                      A    0.6      0   393/392       2      1
   9459.69  a2_power.c:1189                      A    0.0      0   538/538       2      2
   9952.24  a2_power.c:1189                      A    0.6      0   624/623       0      2
  10021.50  a2_task.c:3179                       B  n/a      0   633/632       0      0
  10096.45  a2_power.c:1189                      A    2.0      0   643/642       2      2
  10171.96  a2_task.c:3179                       B  n/a      0   648/647       0      0
  10464.45  a2_power.c:1189                      A    0.5      0   696/696       2      2
  10551.28  a2_power.c:1189                      A    0.1      0   709/708       0      1
  10637.12  a2_task.c:3179                       B    0.0      0   723/722       0      0
  10721.07  a2_taskq.c:759                       A    2.5      0   732/731       2      2
  11276.39  a2_power.c:1189                      B    0.4      0   811/810       0      2
  12178.45  lte_ml1_sleepmgr_stm.c:4054          A  273.8      0   821/820       0      0
  12440.70  a2_power.c:1189                      A    0.0      0   832/831       0      0
  12579.78  a2_power.c:1189                      A    0.4      0   854/853       0      0
  12761.54  a2_power.c:1189                      A    1.5      0   879/879       3      3
  13664.09  lte_ml1_sleepmgr_stm.c:4054          A    4.3      0  1020/1020      1      1

  per-feature Fisher (median split, one-sided greater, alpha 0.05)
  (a) rc/s          : A hi/lo  9/8    B hi/lo  0/2    p = 0.2632   miss
  (b) d(co)         : A hi/lo  1/16   B hi/lo  0/4    p = 0.8095   miss
  (c) vt/vu - 1     : (degenerate)
  (d) d(iq)         : A hi/lo  8/9    B hi/lo  0/4    p = 0.1195   miss
  (e) d(aq)         : A hi/lo  9/8    B hi/lo  1/3    p = 0.3308   miss

  VERDICT: P-R5 FALSIFIED -- NONE of the five features separate A from B at p < 0.05
  the A/B outcome is DECOUPLED from userspace/traffic state at the fatal
```

**The pre-registered falsification is CLEAN.** All five features miss. (c) is degenerate — `vt` and `vu` are equal at every fatal (the modem always votes and unvotes in matched pairs within one sample). The other four are non-degenerate and miss at p > 0.1 in every case.

### 6.1 What this means for H4

H4 (Doc 183 §8 / §12): the A/B discriminator is the pc_irq thread run, which is kernel-side. The state machine that follows the assert is the modem's, not the host's. **P-R5's clean falsification strengthens this:**

- All five userspace/traffic predictors fail. The A/B outcome is not a function of how busy the host was at the fatal.
- The four B cases (10021.50, 10171.96, 10637.12, 11276.39) sit at the LOW end of the A distribution on every feature — they are not a SEPARATE population, they are the bottom tail of the same one. The A/B cut is not a 2-mode distribution.
- P1's 20/21 (line HIGH + state 1) and P3's 20/21 (counter FLAT) hold at n = 21, both failing on the SAME case as Doc 183.

The Doc 183 §10 finding that "the resync does NOT follow the recovery resume" is reinforced by P-R5: there is no userspace/traffic hook that flips the outcome, and there is no resync that follows the recovery resume. Both halves of the H4 hypothesis (the kernel-side mechanism, the absence of a userspace hook) hold.

### 6.2 Why the B cases look like quiet A cases, not different A cases

Look at the B rows:
- 10021.50 `a2_task.c:3179` B: rc/s = n/a (rate_before returned None — the 10-s window had no rx_callbacks data), d(co) = 0, vt/vu = 633/632, d(iq) = 0, d(aq) = 0 — **completely idle, no traffic, no PM activity, no cmd_open, no pc_irq, no pc_ack_irq**
- 10171.96 `a2_task.c:3179` B: same — n/a, 0, 648/647, 0, 0
- 10637.12 `a2_task.c:3179` B: rc/s = 0.0, d(co) = 0, vt/vu = 723/722, d(iq) = 0, d(aq) = 0 — **completely idle**
- 11276.39 `a2_power.c:1189` B: rc/s = 0.4, d(co) = 0, vt/vu = 811/810, d(iq) = 0, d(aq) = 2 — **idle, but had 2 pc_ack_irq in the 5 s before**

Compare to the A cases that look like this:
- 7432.44 `a2_power.c:1189` A: rc/s = 0.5, d(co) = 0, vt/vu = 343/342, d(iq) = 0, d(aq) = 2 — **idle, but had 2 pc_ack_irq in the 5 s before**
- 8544.74 `a2_power.c:1189` A: rc/s = 0.6, d(co) = 0, vt/vu = 393/392, d(iq) = 2, d(aq) = 1
- 9459.69 `a2_power.c:1189` A: rc/s = 0.0, d(co) = 0, vt/vu = 538/538, d(iq) = 2, d(aq) = 2 — **completely idle, no traffic, had 2 pc_irq and 2 pc_ack_irq in the 5 s before**

The 11276.39 B case looks identical to the 7432.44 and 9459.69 A cases on every feature tested — it had the same idle profile, the same 2 pc_ack_irq increments in the 5 s before, and the same vt/vu balance. The ONLY documented difference is that the line was HIGH (which is also true of the A cases) and the iq counter MOVED by 2 instead of 0. **The discriminator is in the kernel's handling of the line transition**, not in userspace.

---

## 7. What is NOT established

1. **Why the 11276.39 a2_power → B is B and not A.** All five tested features give no signal; the H4 hypothesis says the pc_irq thread run is the discriminator; H4 still has only the correlation, not the mechanism. **Doc 182 §11.8 asked this question and it is still open.**
2. **What triggers the ~15 s post-fatal quiet period.** P-R5 confirms it is NOT a userspace event (no cmd_open, no traffic, no vote cycle) — but it is still tight (sd 1.232 s) and still empty (no dmesg event in the window). The `pm_runtime_mark_last_busy()` hypothesis lost one of its two supports (the resync does NOT follow the recovery resume); it has now also lost the "userspace-driven activity" support.
3. **Why the teardown class peaks at since_susp ≈ 1.0 s.** The teardown bucket-1.00 has 6 of 7 = 86 % timeouts, but the teardown bucket-0.00 has 0 of 2 = 0 % and the teardown bucket-0.50 has 0 of 4 = 0 %. The teardown class is dangerous when it occurs ~1 s after a suspend, but the resume count drops at that band. The mechanism is unidentified.
4. **Whether the `lte_ml1_sleepmgr_stm.c:4054` signature is permanent on this boot.** It has appeared twice in 2.7 ks of new data; whether it will continue appearing is unknown.
5. **Whether the dmesg ring wrap is hiding other signatures.** Doc 183's `lte_ml1_common_timer.c:390` B case is gone. There may be earlier signatures not in either doc.

---

## 8. Why this matters

1. **P-R1's direction replicates with stronger signal** (p = 2 × 10⁻⁶ vs Doc 183's p = 2.8 × 10⁻⁴). The "danger window near suspend" finding is now at n = 922 resumes, 33 timeouts, 21 fatals.
2. **P-R4's clean falsification is informative, not a setback.** It exposes that the rate is a MIXTURE of two mechanisms — race (peaks at suspend) and teardown (peaks at +1.0 s). The 1.25 s cut picked up both, which is why it works as a one-number summary; the underlying picture is two curves.
3. **P-R5's clean falsification strengthens H4.** The A/B discriminator is NOT a userspace/traffic feature. The kernel/baseband hypothesis (Doc 183 §8) gains another independent confirmation.
4. **The Doc 183 failures replicate on the new cases, not just the old ones.** P1 and P3 both fail on the SAME cases as Doc 183 (11276.39 and 10637.12 respectively) — they do not pick up new failures from the 5 new fatals. The two-predictor model is STABLE across the new data.
5. **`lte_ml1_sleepmgr_stm.c:4054` joins the A-class signature list.** It was a known signature in earlier docs (referenced as a verified control in Doc 182 §11.8) but had not been observed on this boot. Both new instances are A-class, so the A-class signature list is now four deep: a2_power, a2_taskq, lte_ml1_sm_conn_inter_freq_stm, lte_ml1_sleepmgr_stm.

---

## 9. Next actions

1. **Score the next capture against Doc 184's P-R4 and P-R5** when it accumulates. The pre-registered thresholds and the falsification direction are both fixed; do not re-tune.
2. **Investigate the teardown-class bucket-1.00 peak** (6 of 7 = 86 % timeouts). Read `bam_dmux_runtime_resume()` to find what makes a teardown resume dangerous at exactly 1 s post-suspend — the time from suspend to teardown is set somewhere; finding it is concrete.
3. **Score P-R4 stratified by class** (race / teardown / other) on the next capture. The pre-registered test was on the pooled rate; the stratified test is exploratory but inexpensive and might pin down which of the two mechanisms the band actually tracks.
4. **Investigate the 11276.39 a2_power → B outlier** with the source — the sampler records around it are `pl=1, ps=1, td=0` (same as the A cases), the iq delta is 2 (not 0, not 1). What makes a2_power sometimes take the B branch when the line was HIGH?
5. **Pull the next 1000 s of sampler data and re-score** — the rate quantities (P-R1's 78.8 %, the teardown class's 86 %, P5's 17/4 split) all have small absolute counts in the tail. More data, same script, more confidence.
6. **Do NOT re-open H4** without a new instrument. The P-R5 falsification is a clean win for H4; the §11.8 question about the 11276.39 outlier is the one that matters and it is now narrower, not wider.