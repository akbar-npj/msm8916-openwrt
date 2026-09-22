# 183 — P-R1 CONFIRMED IN DIRECTION, P-R2 AND P-R3 FALSIFIED, AND THE A/B PREDICTORS SCORE 18 OF 19

**Date:** 2026-09-22
**Boot:** `59d9c272-d87c-486a-9d6c-4fd93e02fc29` (live, extended — the same boot Doc 182 measured,
pulled again 4 h 37 min later)
**Patch under test:** `msm89xx/patches/808-bam-dmux-stats.patch` (2000 ms pc-ack wait arm), plus the
patch-814 SSR-powerup retry that produces the A/B outcome line
**Deployed module:** `qcom_bam_dmux.ko` md5 `982b633e2a682e21ad69b6e85d273941` (unchanged — **no
code was changed between Doc 182 and this doc**; this is a longer look at the same run)
**Evidence:** `evidence/112_bam_reinit_ab/`
* `pcfine_f10_boot59d9c272.txt` — md5 `193af39d93cd93b75d2acf28216a4ccb`, **60 859 records**,
  3 427 383 B, uptime **3157.20 .. 11391.62 s** (8234 s). The extended sampler capture. It strictly
  contains Doc 182's `pcfine_f9_boot59d9c272.txt` (38 548 records, to 6978 s).
* `dmesg_boot59d9c272_late.txt` — md5 `6aeebb38c96e671b2de3ffca8990189e`, 1418 lines. Pulled
  2026-09-22 22:01. **21 fatals**, 26 `pc-ack timeout during resume` lines, 22 outcome lines. The
  ring starts at 1434.99 s, so the boot's first two fatals (533.19 s and one before 1435 s) are
  gone; the file's first fatal is the boot's **#3**.
* `score_pr.py` — md5 `358f24c934fa5d38c7426ea035607d62`. Scores Doc 182 §13's P-R1/R2/R3, the
  outcome table, and the A/B → timeout correlation. Reproduces §4–§7 below.
* `score_p1p3.py` — md5 `9b0c220894c6aecd38790270e851b194`. **NEW.** Scores §9's P1/P2/P3/P4 over
  *every* fatal in a capture rather than one at a time. Reproduces §8.
* `resume_trigger.py` — md5 `796e956683096fcddfc4faed0d933302`. Doc 182 §8.9's census, re-run on
  the longer capture.
* `census_pc.py`, `score_h3.py`, `pcfine.sh` — as in Doc 182.

---

## 1. SOP compliance

| SOP step | done? |
| :-- | :-- |
| Comparative protocol against the stock-HMU05 ground truth | **N/A, and that is stated** — every claim here is about the **OpenWrt port's own driver** (`qcom_bam_dmux.c`) and the *host* kernel's log. No baseband was touched, no firmware claim is made, and no patch is proposed. The one firmware-facing fact used is the modem's **reported** fatal site, which is read from dmesg as a string and is used only as a label. |
| Pre-register the prediction **before** the data exists | **YES — this is the cleanest instance in the corpus, and it is the entire point of the doc.** Doc 182 §13 wrote P-R1, P-R2 and P-R3 down, with their thresholds and their bars, **before this capture was scored**; §5–§7 score them as written, including the two that fail. The one prediction that is **not** pre-registered is flagged as exploratory at every point it appears: the `+14..+25 s` widening in §7, and the 1.25 s cut itself was data-chosen in Doc 182 §8.9 (which is exactly why P-R1 fixed it before scoring). |
| Freeze the capture, hash it, score the frozen copy | **YES** — both files were pulled to the host and hashed before scoring, and every scorer reads only the frozen copy. The device copy keeps growing; the pull is the artifact. |
| Keep a control | **YES** — (a) the **pre-registered band's own negative**: P-R3's bar is ≥ 90 % and it scored 58 %, so the control is built into the test rather than added after; (b) the **692 clean resumes** are the control for the 25 timeouts in P-R1/R2; (c) the **out-of-capture fatals** (#3 `common_timer` → B at 2338.609206, and #4 `a2_power` → A at 2975.685045) are scored from the *v1* capture and dmesg but excluded from the sampler-based tables, which is why those tables say 19 and the dmesg says 21; (d) §10 uses the 716 ordinary inter-resume gaps as the control for the 15 post-fatal ones. |
| Verify the instrument before resting a claim on it | **YES, and it changed a conclusion.** §8's first pass scored **P3 as MOVED on every A case** and would have "falsified" H4 outright. Two instrument facts Doc 182 had already established were the cause: the sampler's `<UP>` label is **~73 ms early** (§8.5.1), so the naive "last record before the fatal" is the driver's *first reaction* to it; and P3's window **ends at the outcome line**, not at a fixed offset (§8.1/§8.2), so a `[fatal, fatal+14 s]` window counts the *trailing deassert*. Both corrections are stated in the scorer's docstring. **This is the doc's own version of Doc 182 §7.3's lesson.** |
| State what is *not* established | **YES** — §11 |

---

## 2. What was asked

Doc 182 §13 pre-registered three predictions and asked for them to be scored against the next
capture. The session task was literally "wait for fatal #10 and score P-R1/R2/R3". Fatal #10
arrived, then eleven more; the sampler was still running, and the capture was pulled at uptime
**11 391.62 s** — 4413 s past Doc 182's last pull, and 3871 s past the fatal #10 that §13 was
waiting for.

**Answers, up front:**

| prediction | bar | result | verdict |
| :-- | :-- | :-- | :-- |
| **P-R1** — a resume within 1.25 s of a suspend is more likely to time out | direction | **19/25 (76.0 %) vs 6/25 (24.0 %)**, p = 0.000280 | **CONFIRMED in direction**; the strong form ("the band is the separator") is **FALSIFIED** — 6 of 25 timeouts are outside it |
| **P-R2** — the two classes (teardown vs race) partition the timeouts | partition | **8 of 25 timeouts fall outside both classes** | **FALSIFIED**, and §13 called this "the more likely of the two to be falsified" |
| **P-R3** — the ~15–17 s recovery resume is present after ≥ 90 % of A fatals | ≥ 90 % | **11/19 = 58 %** | **FALSIFIED as pre-registered** (post-hoc widening to `+14..+25 s` gives 19/19 — exploratory) |

And two things §13 did **not** ask for, which the longer capture made scoreable for the first time:

| finding | result |
| :-- | :-- |
| **The outcome table's perfect split collapses** | `a2_power` → **11 A / 1 B**; `common_timer` → 0 A / **1 B**; **Fisher p = 0.153846** (was 0.00042). Two new B signatures: `a2_task.c:3179` → **B 3/3**, and `a2_power.c:1189` → **B once**. |
| **P1/P3 scored as a table over 19 fatals** | **both 18/19** — and each fails on a *different* case. `pc_state` was **1 before all 19** fatals, so P2's `pc_state = 0` clause is unreachable in this capture. |

---

## 3. The capture

`pcfine_f10_boot59d9c272.txt`: 60 859 records, uptime 3157.20 .. 11391.62 s. `pc_timeout_count`
27, `pm_resume_attempts` 821, `pc_irq_count` 1381, `pc_resync_count` **83** at the end of the
device-side run.

**It is the same boot and the same module as Doc 182.** No patch was regenerated, no module was
rebuilt, no firmware was touched. The only change is the length of the observation, which is what
makes this a clean confirmatory run for predictions written down in advance.

**The dmesg file is not the sampler file and the two have different coverage.** The ring starts at
1434.99 s, so the boot's fatal #1 (533.185843) and #2 are lost; the file holds fatals **#3 through
fatal #23**. The sampler starts at 3157.20 s, so it excludes #3 (2338.609206) and #4 (2975.685045). The
sampler-based tables therefore score **19** fatals and the dmesg-based tables score **21**. This is
stated here because it is the kind of off-by-two that turns into a phantom discrepancy later.

Mapping (uptime → the boot's fatal number → signature → outcome), for the 19 in-capture cases:

| # | fatal UP | signature | outcome | first resume after it |
| --: | --: | :-- | :-- | --: |
| 5 | 3884.610521 | `a2_power.c:1189` | A | +0.1 s, then +14.8 s |
| 6 | 4808.139745 | `a2_power.c:1189` | A | +0.2 s, then +16.3 s |
| 7 | 5709.425429 | `lte_ml1_sm_conn_inter_freq_stm.c:712` | A | +17.1 s |
| 8 | 6611.040190 | `lte_ml1_sm_conn_inter_freq_stm.c:712` | A | +17.0 s |
| 9 | 6692.990034 | `a2_taskq.c:759` | A | +17.5 s |
| 10 | 7432.437788 | `a2_power.c:1189` | A | +18.5 s |
| 11 | 7464.237591 | `a2_power.c:1189` | A | +15.6 s |
| 12 | 8365.552204 | `lte_ml1_sm_conn_inter_freq_stm.c:712` | A | +18.0 s |
| 13 | 8544.742694 | `a2_power.c:1189` | A | +18.2 s |
| 14 | 9459.688387 | `a2_power.c:1189` | A | +0.2 s, then +18.5 s |
| 15 | 9952.239692 | `a2_power.c:1189` | A | +19.3 s |
| 16 | 10021.503034 | **`a2_task.c:3179`** | **B** | +18.7 s |
| 17 | 10096.452080 | `a2_power.c:1189` | A | +15.9 s |
| 18 | 10171.958531 | **`a2_task.c:3179`** | **B** | +17.2 s |
| 19 | 10464.453587 | `a2_power.c:1189` | A | +0.3 s, then +19.7 s |
| 20 | 10551.277511 | `a2_power.c:1189` | A | +16.1 s |
| 21 | 10637.116822 | **`a2_task.c:3179`** | **B** | +14.8 s |
| 22 | 10721.067093 | `a2_taskq.c:759` | A | +16.9 s |
| 23 | 11276.393334 | `a2_power.c:1189` | **B** | +18.4 s |

---

## 4. The outcome table — the perfect split collapses

Doc 182 §5's table was `a2_power` → A 4/4 and `common_timer` → B 13/13, Fisher
**p = 0.00042017**. Doc 182 §5 itself warned that this was "a property of THIS boot's signature
mix, not of the outcome rule". The longer run confirms the warning and then some:

| signature | A | B | C |
| :-- | --: | --: | --: |
| `a2_power.c:1189` | **11** | **1** | 0 |
| `lte_ml1_sm_conn_inter_freq_stm.c:712` | 3 | 0 | 0 |
| **`a2_task.c:3179`** (NEW) | 0 | **3** | 0 |
| `a2_taskq.c:759` | 2 | 0 | 0 |
| `lte_ml1_common_timer.c:390` | 0 | 1 | 0 |
| **TOTAL** | **16** | **5** | **0** |

The original 2×2, re-scored with all 21 fatals:

```
                    A    B
a2_power.c:1189    11    1
common_timer:390    0    1
```

**Fisher exact, one-sided p = 0.153846** — down from 0.00042017. The split is no longer
significant, and the reason is specific: **`a2_power.c:1189` produced a B outcome** (fatal #23,
11276.393334), and a **new signature produced three B's** (`a2_task.c:3179`, 3/3).

**The most important consequence is a negative one: §5's association was never a rule.** Doc 182
§5 already said so in prose; this capture supplies the counter-example that prose was missing. Any
surviving statement of the form "signature X means outcome Y" is now dead for `a2_power`, and
`a2_task.c:3179` is a *second* signature that goes B. `common_timer.c:390` remains the only
signature with no A case — at **n = 1** in this boot (and 13 B / 0 A in the retrospective boot).

### 4.1 The A/B → timeout correlation weakens too

Doc 182 §6's correlation, re-scored on the same 21 fatals, with a timeout attributed to a fatal if
a `pc-ack timeout during resume` line falls in `[t, t + 3.0 s]` (a timeout lands ~1.1–2.4 s after
its fatal, so this window captures it and excludes the next fatal's):

```
A: timeout 11   none 5
B: timeout  0   none 5
Fisher one-sided p = 0.012384
```

**p = 0.012384**, down from the 0.00000796 of the earlier reading. The direction survives — no B
fatal has ever produced a timeout, in either boot — but the association is now a **weak
correlation**, not the near-perfect one Doc 181 §6 and Doc 182 §6 originally reported. This is
consistent with Doc 182 §6.1/§6.2, which had already withdrawn the *causal* reading; this capture
weakens the *statistical* one as well.

---

## 5. P-R1 — direction confirmed, the strong form falsified

> *P-R1. With the cut fixed at 1.25 s, a resume with `since_susp ≤ 1.25 s` is more likely to time
> out than one with `since_susp > 1.25 s`. Score every resume in the capture, not just the fatal
> windows.* (Doc 182 §13)

Scored on all 717 resumes in the capture:

```
<= 1.25 s : timeout  19 of 25 ( 76.0 %)   clean 272 of 692  -> rate   6.5 %
>  1.25 s : timeout   6 of 25 ( 24.0 %)   clean 420 of 692  -> rate   1.4 %
Fisher one-sided p = 0.000280
```

**Direction CONFIRMED.** A resume that closely follows a suspend times out at **6.5 %** against
**1.4 %** for one that does not — a **4.6×** rate ratio, p = 0.000280. This is the replication
Doc 182 §13 asked for, and it is the first time the `since_susp` cut has been tested against a
threshold fixed in advance.

**But the strong form is falsified.** §8.9's phrasing implied the band *is* the separator. It is
not: **6 of the 25 timeouts have `since_susp > 1.25 s`** — and one of them is at **5.42 s**, which
is not a near-miss on 1.25 s. The correct statement is a **rate**, not a rule:

> A resume within ~1 s of a suspend is **more likely** to time out; it is not **required**.

The `since_susp` distribution makes the overlap explicit (from `resume_trigger.py`):

| cut | timeouts ≤ cut | clean ≤ cut |
| --: | --: | --: |
| 0.50 s | 13/25 | 134/692 |
| 1.00 s | 16/25 | 235/692 |
| **1.25 s** | **19/25** | **272/692** |
| 1.50 s | 21/25 | 301/692 |
| 2.00 s | 24/25 | 345/692 |
| 10.00 s | 25/25 | 618/692 |

There is no cut that separates cleanly — at 1.25 s, 272 of 692 clean resumes share the band, and 6
timeouts sit outside it. **The band is a risk factor with a large overlap, not a boundary.**

The informative half of P-R1 was the negative, and the honest reading of it is mixed. The
outside-band rate is now **measured** rather than bounded: **6 of 426 late resumes timed out =
1.41 %**, with a Wilson 95 % CI of **[0.65 %, 3.04 %]**. Doc 182 §11.11 could only bound it —
0 of 137, rule of three, upper limit **2.19 %** — and the new upper limit is **looser**, because
this run observed six events where the earlier one observed none. What has improved is not the
bound but the fact that the rate is no longer an extrapolation from zero: **the band is where
76 % of the timeouts are, and the rest of the resume population still times out at ~1.4 %.**

---

## 6. P-R2 — falsified

> *P-R2. Report the two classes separately, because §8.9's four events are not one mechanism:
> `pl = 0` / `td = 1` (the teardown window) versus `pl = 1` / `td = 0` (the suspend race). A band
> that spans both is a coincidence until they are separated. **P-R2 is the more likely of the two
> to be falsified**, and it should be scored first.* (Doc 182 §13)

Scored as written:

```
teardown (pl=0, td=1): timeout   4 of 25   clean  22 of 692   rate  15.4 %   p = 0.010243
race     (pl=1, td=0): timeout  13 of 25   clean 524 of 692   rate   2.4 %   p = 0.997023
NEITHER class: 8 of 25 timeouts
```

**FALSIFIED.** Two separate failures, and the second is the more interesting one:

1. **The two classes do not partition the timeouts.** **8 of the 25 fall outside both.** The
   teardown class covers 4, the race class covers 13, and the remaining 8 are neither.
2. **The race class carries no signal at all** — p = **0.997023**. 524 of 692 *clean* resumes have
   `pl = 1, td = 0`, so "the modem is healthy" describes the overwhelming majority of ordinary
   resumes as well as 13 timeouts. The class is not a discriminator; it is the background.

The teardown class *does* separate (15.4 % vs the 3.5 % base rate, p = 0.010243) but it accounts
for only 4 of 25 events. So the honest summary is: **Doc 182 §8.9's two-mechanism story is wrong in
its arithmetic** — the four events it was built from were indeed heterogeneous, but heterogeneity
does not extend to the 21 new ones, and the classification scheme that described the original four
does not describe the set.

**§13's own call was correct.** It wrote "P-R2 is the more likely of the two to be falsified, and
it should be scored first." It was, and it was.

---

## 7. P-R3 and the ~17 s quiet period

> *P-R3. The ~15–17 s recovery resume is present after all five A fatals here (+14.85 … +17.46 s)
> and never times out (0/5). Test it on the retrospective boot's 17 fatals and on every new A
> fatal: **it should be present in ≥ 90 % of them**.* (Doc 182 §13)

**FALSIFIED as pre-registered:**

```
pre-registered band +14..+18 s : 11/19 = 58 %   (bar >= 90 %)
POST-HOC widened band +14..+25 s     : 19/19 = 100 %   <- exploratory, NOT pre-registered
```

**11 of 19 = 58 %**, against a bar of 90 %. **Eight fatals missed**, and the reason is visible in
the per-fatal list of §3 — the late resumes run **+14.75 .. +19.34 s**, so six of the eight
(#10, #12, #13, #15, #16, #23) have their first resume at **+18.0 … +19.3 s**, just past the +18 s
edge. The other two are a different failure: #14 and #19 have their **first** resume in the
teardown window (+0.2 s and +0.3 s), so the pre-registered test — which looks only at the *first*
resume in `+14..+18 s` — never sees their recovery resume at +18.5 s and +19.7 s.

The post-hoc widening to `+14..+25 s` gives **19/19**, but that band was chosen after seeing the
data and is **exploratory**; it must not be quoted as the result of P-R3. What the misses show is
that P-R3's **band was mis-specified in two independent ways** — too narrow at the top, and
formulated on "the first resume" when two fatals have a teardown resume first.

**What is real, and it is stronger than the pre-registered test.** The first resume after a fatal
is **bimodal**, with nothing in between:

| | n | value |
| :-- | --: | :-- |
| **early** (the teardown) | 4 | +0.1, +0.2, +0.2, +0.3 s — **all A** |
| **late** (the recovery) | 15 | min **14.75**, max **19.34**, median **17.16**, sd **1.232** s |
| in between | **0** | — |

Measured against the capture's **716 ordinary inter-resume gaps**:

| | value |
| :-- | --: |
| median gap | 5.25 s |
| 75th percentile | 8.70 s |
| mean | 11.13 s |
| fraction > 14 s | **9.22 %** |
| fraction inside `[14.8, 19.3]` | **3.77 %** (27 of 716) |

So a **~15 s quiet period after a fatal is real and tight**: the 15 late first-resumes land inside
a band that contains only 3.77 % of ordinary gaps, and **P(all 15 land in it) = 4.43 × 10⁻²²**.
The sd of 1.232 s over a 4.6 s span is what makes it look scheduled rather than incidental.

**What is *not* established is the trigger.** §13 hoped P-R3 would let "the ~17 s event" be called
a resume with the resync trailing it. The resume is real; the *trigger* is not identified. There is
**no dmesg line and no `cmd_open` increment coinciding with the late resume** — the hypothesis in
Doc 182 §8.9 (the channel-rebuild churn's `pm_runtime_mark_last_busy()` at `:1078` holds the device
active through the post-SSR window, then the autosuspend at ~17.2 s rearms the ring, which asserts
the line) remains **unproven**. §10 below removes one of its supports.

---

## 8. P1/P2/P3/P4 scored as a table — the new result

Doc 182 §13 asked only for P-R1/R2/R3. But the longer capture also makes Doc 182 **§9's** four
predictions scoreable over **19** cases instead of the six A cases they were confirmed on. That is
worth doing, because §11.11 warns that a six-case confirmation is a single-digit count.

`score_p1p3.py` scores, for every in-capture fatal: the last **genuinely pre-fatal** sampler record
(telemetry time = `UP + 0.073 s`, §8.5.1) and the `pc_irq_count` at the **outcome line**.

```
    fatal UP  signature                          out   pl  ps     P1  iq pre->post      P3
  ----------------------------------------------------------------------------------------
     3884.61  a2_power.c:1189                      A    1   1   HIGH     269->269     FLAT
     4808.14  a2_power.c:1189                      A    1   1   HIGH     317->317     FLAT
     5709.43  lte_ml1_sm_conn_inter_freq_stm.c:712   A    1   1   HIGH     491->491     FLAT
     6611.04  lte_ml1_sm_conn_inter_freq_stm.c:712   A    1   1   HIGH     507->507     FLAT
     6692.99  a2_taskq.c:759                       A    1   1   HIGH     523->523     FLAT
     7432.44  a2_power.c:1189                      A    1   1   HIGH     585->585     FLAT
     7464.24  a2_power.c:1189                      A    1   1   HIGH     589->589     FLAT
     8365.55  lte_ml1_sm_conn_inter_freq_stm.c:712   A    1   1   HIGH     649->649     FLAT
     8544.74  a2_power.c:1189                      A    1   1   HIGH     667->667     FLAT
     9459.69  a2_power.c:1189                      A    1   1   HIGH     911->911     FLAT
     9952.24  a2_power.c:1189                      A    1   1   HIGH    1055->1055    FLAT
    10021.50  a2_task.c:3179                       B    0   1    low    1068->1069   MOVED
    10096.45  a2_power.c:1189                      A    1   1   HIGH    1085->1085    FLAT
    10171.96  a2_task.c:3179                       B    0   1    low    1090->1091   MOVED
    10464.45  a2_power.c:1189                      A    1   1   HIGH    1185->1185    FLAT
    10551.28  a2_power.c:1189                      A    1   1   HIGH    1201->1201    FLAT
    10637.12  a2_task.c:3179                       B    0   1    low    1218->1218    FLAT
    10721.07  a2_taskq.c:759                       A    1   1   HIGH    1235->1235    FLAT
    11276.39  a2_power.c:1189                      B    1   1   HIGH    1361->1363   MOVED
```

| prediction | result |
| :-- | :-- |
| **P1** — last pre-fatal record `pc_state = 1`, `pc_line = 1` for an A fatal | **18/19** — one violation, fatal #23 |
| **P2** — last pre-fatal record `pc_state = 0`, `pc_line = 0` for a B fatal | **0/4 in-capture** — `pc_state` was **1 before all 19** fatals, so the clause is unreachable; `pc_line = 0` alone holds **3/4** |
| **P3** — `pc_irq_count` does not increment between the teardown and the A outcome line | **15/15 A cases FLAT** — perfect on the A side |
| **P4** — in a B case `pc_irq_count` increments exactly once at the assert | **2/4** — deltas 1, 1, **0**, **2** |

Both predictors induce the *same* 2×2, and it is 18/19:

```
P1  (line HIGH)        True: A 15 B  1   False: A  0 B  3
P3  (counter FLAT)     True: A 15 B  1   False: A  0 B  3
```

### 8.1 The one thing this capture adds to H4, and the one thing it takes away

**Adds:** P3 — the load-bearing half of H4, the half Doc 182 §11.1 called "the quantity the
outcome depends on" — is **15/15 on the A side**, and the counter **moved in 3 of the 4 B cases**.
The A/B contrast Doc 182 built on six A cases now has 15 A and 4 B behind it, and the mechanism
survives at **18/19**. That is a genuine strengthening, and it is the answer to §11.8's "the B case
has never been measured with the fixed-key sampler": it now has been, **four times**.

**Takes away:** **P2's `pc_state = 0` clause never holds.** `pc_state` was **1 in the last pre-fatal
record of all 19 fatals** — A and B alike. So:

* P1's `pc_state = 1` clause and its `pc_line = 1` clause are **redundant** in this capture; the
  discriminator is the **line**, not the driver's cached state.
* P2 as written is **not merely falsified but unfalsifiable here**: no in-capture B fatal could
  satisfy `pc_state = 0`.

The only P2 hit is Doc 182's own confirming case, the boot's **fatal #3** (`common_timer.c:390` →
B at 2338.609206), scored from the **v1** capture `pcfine_v1_boot59d9c272.txt`: the last record
before it is **2331.34, `pc_state = 0`, `pc_line = 0`** — a **7.269 s** lead, and the v1 key cannot
see `ps`/`pl` moves between counter changes (§11.8's caveat, unchanged). So P2's status is: **1 hit
on a stale v1 record, 4 misses on fixed-key records.** The `pc_line = 0` half is the half that
survives (3 of 4 B cases; the exception is fatal #23).

### 8.2 The two violations are different cases, and that is the honest shape of it

* **P1's violation — fatal #23, 11276.393334, `a2_power.c:1189` → B.** The last pre-fatal record
  (11276.21, telemetry 11276.28, i.e. 0.11 s before the fatal) is **`pc_state = 1, pc_line = 1`** —
  the line was **HIGH** — and the outcome is **B**. This is the case Doc 182 §9's B-side did not
  predict, and it is the *second* way `a2_power.c:1189` has broken a rule: it was the signature the
  A-side was built on (§5), and here it produces a B from a high line.
* **P3's violation — fatal #21, 10637.116822, `a2_task.c:3179` → B.** The counter is **flat**
  (1218 → 1218) across the teardown and the B line, i.e. no thread ran — the A-side signature — yet
  the outcome is **B**. So a flat counter is **not sufficient** for A either.

Neither violation is a measurement error; both were re-checked against the raw records, and the
sampler's key includes both `ps` and `pl`, so the transitions are logged. What they establish is
that **the two predictors are correlates at 18/19, not mechanisms.** That is a weaker claim than
Doc 182 §9 made, and it is the claim the data supports.

**And P4 is now the weakest of the four: 2/4.** The two misses are informative — fatal #21 has
**delta 0** (no increment at all, which is why it is P3's violation) and fatal #23 has **delta 2**.
So "the B path consumes exactly one edge" is not reliable either.

---

## 9. What the sampler clock costs, and why §8 needed two corrections

This is worth recording because the first pass of `score_p1p3.py` produced a **wrong and much more
dramatic** answer: it scored P3 as **MOVED on every one of the 19 fatals**, which would have
"falsified" H4 outright. Two instrument facts, both already in Doc 182, were the cause:

1. **The sampler's `<UP>` label is ~73 ms early** (Doc 182 §8.5.1). The naive "last record with
   `UP <= fatal`" therefore returns a record whose telemetry was read *after* the fatal — the
   driver's **first reaction** to it, which is exactly §8.2's misread `3884.57` row
   (`pc_state = 0`). With the lag applied, the same case resolves to the genuinely pre-fatal
   `3883.53` record (`pc_state = 1`), which is what P1 predicted.
2. **P3's window ends at the outcome line, not at a fixed offset.** Doc 182 §8.1/§8.2 are explicit:
   the single increment is the **trailing deassert**, which lands ~2 s after the A line (fatal #4:
   flat 185 through the A line at 2976.80, then 186 at 2979.23; fatal #5: flat 269 through the A
   line at 3885.73, then 270 at 3888.06). A `[fatal, fatal + 14 s]` window counts that deassert and
   turns a FLAT into a MOVED on every A case.

Both are now in `score_p1p3.py`'s docstring, and the fix is one line each. The lesson is Doc 182
§7.3's, one level up: **an instrument misalignment does not produce noise, it produces a
confident wrong answer** — and here it produced one that pointed the opposite way from the truth.

---

## 10. The resync does *not* follow the recovery resume

Doc 182 §11.10 said the resync "trails [the ~17 s event] by 0.2–0.7 s" and called the ~17 s offset
"a property of whatever schedules the recovery resume". With 83 resyncs now in the capture
(was 2), that can be tested directly. For each in-capture fatal, the late resume and the **next**
`pc_resync_count` increment:

| fatal | late resume | next resync | delay |
| --: | --: | --: | --: |
| 5709.43 | +17.1 s | 5726.67 | **+0.19 s** |
| 9952.24 | +19.3 s | 9971.69 | **+0.11 s** |
| 6611.04 | +17.0 s | 6628.71 | +0.70 s |
| 8365.55 | +18.0 s | 8406.96 | +23.40 s |
| 8544.74 | +18.2 s | 8594.73 | +31.79 s |
| 10021.50 | +18.7 s | 10052.61 | +12.43 s |
| 10096.45 | +15.9 s | 10148.56 | +36.17 s |
| 10171.96 | +17.2 s | 10262.82 | +73.70 s |
| 10551.28 | +16.1 s | 10589.98 | +22.57 s |
| 10637.12 | +14.8 s | 10669.56 | +17.69 s |
| 10721.07 | +16.9 s | 10832.28 | +94.34 s |
| 11276.39 | +18.4 s | 11343.42 | +48.64 s |
| *(7 more: #5, #6, #9, #10, #11, #14, #19)* | +14.8 … +19.7 s | | +106 … +1827 s |

**Only 2 of 19 have a resync within 0.2 s of the recovery resume.** The other 17 are 12 s to
1827 s later — i.e. the resync runs on its own watchdog schedule and is *not* a consequence of the
resume. Doc 182 §8.6.2's 0.10 s coincidence was one case; at n = 19 it does not generalise.

This **removes one of the two supports** for the §7 hypothesis (that the ~17 s autosuspend rearms
the ring, which asserts the line, which the resync then reacts to). The resume and the ~15 s quiet
period remain real; the causal chain to the resync does not. `pc_resync_count` is now **83** (it
was 2 when Doc 182 §8.6.2 saw its first), which at ~1 resync per 99 s over the capture is a
cadence of its own — it does not pair one-to-one with the 19 recovery resumes, and §8.6.2's
0.10 s pairing was a coincidence rather than the rule.

---

## 11. What is NOT established

1. **P-R1's band is a risk factor, not a boundary, and the cut is still data-chosen.** The
   replication is real (19/25 at 76.0 % vs 6/25 at 24.0 %, p = 0.000280) and the threshold was
   fixed in advance, but 1.25 s was itself selected from Doc 182 §8.9's four events. **6 of 25
   timeouts are outside the band, one at 5.42 s.** Nothing here says the band *causes* the timeout,
   and no cut separates the two populations.
2. **The mechanism behind the band is still not identified.** §8.9's story ("too fast for the modem
   to ack") is a plausible reading of the 4-event set, but §6 shows the two classes it was built
   from do not describe the 25-event set. The `since_susp` correlation may be proxying for
   something else entirely — a channel-state or traffic-state variable that happens to correlate
   with short suspends.
3. **The ~15 s quiet period's trigger is unknown.** It is statistically tight (sd 1.232 s,
   P = 4.43 × 10⁻²² against ordinary gaps) and it is bimodal, but no dmesg line, no `cmd_open`
   increment and — per §10 — no resync explains it. The `pm_runtime_mark_last_busy()` hypothesis
   has lost one of its two supports and is **unproven**.
4. **The A/B predictors are correlates at 18/19, not mechanisms.** P1 and P3 each fail on one case,
   and they fail on *different* cases (§8.2). P3 — the load-bearing half of H4 — is perfect on the
   A side (15/15) but is **not sufficient** for A (fatal #21 is B with a flat counter). P4 is 2/4.
   The mechanism may still be right; it is not *proved* by these predictors, and the two violations
   are the reason to say so.
5. **`pc_state` is not a usable pre-fatal discriminator on this device.** It was 1 before all 19
   fatals, so P2's `pc_state = 0` clause is unreachable and P1's `pc_state = 1` clause is
   redundant. Why the driver's cached state sits at 1 across a period in which the wire toggles
   (it is 0 with `pc_line = 1` in some windows, and 1 with `pc_line = 0` in others) is **not
   explained here** and is worth a source read.
6. **The outcome table's new B signatures are unexplained.** `a2_task.c:3179` → B 3/3 is a *second*
   signature that goes B, and `a2_power.c:1189` → B once kills the A association. §5's "the
   signature is only a correlate" is now demonstrated rather than asserted — but *what* distinguishes
   the A-class signatures from the B-class ones is no closer, and `common_timer.c:390`'s uniqueness
   rests on **n = 1** in this boot.
7. **The dmesg ring does not cover the whole boot** (§3), so the boot's fatal #1 and #2 are lost and
   the two clocks' tables score different n. No claim here depends on the missing two, but the
   asymmetry is a standing hazard.
8. **P-R3's post-hoc 19/19 is exploratory and must not be quoted as the test result.** The
   pre-registered bar was ≥ 90 % in `+14..+18 s` and it scored 58 %. The widened band was chosen
   after seeing the **eight** misses.
9. **The sampler is still v2.** Doc 182 §13 item 3's clock fix (`scratch/pcfine.sh`, which brackets
   the `/proc/uptime` read) is written but **not deployed** — deploying it requires restarting the
   sampler and losing the in-flight capture. Every timing in §8 therefore carries the ~73 ms
   systematic error, corrected by a constant rather than measured per record.
10. **Untouched by this doc:** the ~903.675 s always-on clock and its latched offset; the userspace
    bearer rebuild (the user-visible data stall); the AP-side `hyp`/`tz` swap; and the Android leg.

---

## 12. Why this matters

* **The pre-registration worked, and it is the reason to trust the two falsifications.** Doc 182
  §13 wrote three predictions with thresholds and bars, and two of them failed. A doc that had
  scored them after the fact could have reported the `+14..+25 s` band (19/19), or picked a cut
  other than 1.25 s, and looked much better. **The value here is that the failures are on the
  record.** §13 even called which one would fail first, and it did.
* **P-R1 is the first replicated result in this line of work.** The `since_susp` effect is real at
  4.6× and p = 0.000280 against a threshold fixed in advance, on a different capture length than
  the one that suggested it. It is now the strongest surviving lead on *which* resume times out —
  but §5 and §6 together say it is a **rate**, not a rule, and that the two-mechanism story built
  on the original four events does not extend.
* **H4's load-bearing prediction survives at 18/19 and its strongest form does not.** The A/B
  contrast now has 15 A cases and 4 B cases with the fixed-key sampler (§11.8's gap is closed), and
  P3 holds 15/15 on the A side. But the mechanism is a correlate: two different cases violate it,
  one in each direction. That is the correct confidence level, and it is lower than Doc 182 §9's
  "CONFIRMED".
* **The outcome table is dead as a rule, which was the right thing to find.** `a2_power.c:1189` →
  B once, and a new signature → B 3/3, take the association from p = 0.00042 to p = 0.154. Any
  downstream reasoning that assumed "signature ⇒ outcome" must be revisited; the timeout
  correlation falls with it (p = 0.012384).
* **Two instrument traps produced a confident wrong answer, and both were already documented.**
  §9 is the reason to keep Doc 182 §8.5.1 and §8.1 in front of you whenever a new scorer is
  written. The first pass of `score_p1p3.py` inverted the conclusion.
* **The user-visible failure is unchanged.** None of this touches the data stall. The kernel's SSR
  recovery is 0.70 s; the userspace bearer rebuild is 15–26 s, and that remains the lever for the
  symptom the user sees.

---

## 13. Next actions

1. **P-R1's band is the surviving lead, and the next test must not re-tune the cut.** With the cut
   fixed at 1.25 s, the question is now *why* 6 of 25 timeouts are outside it. Two candidate
   separations to pre-register before the next capture: (a) `since_susp` as a **continuous**
   predictor rather than a threshold — report the timeout rate per 0.5 s bucket; (b) a
   channel-state variable (`rx_callbacks` rate, `cmd_open` delta, `vt`/`vu` pairing) measured at
   each resume, to test whether `since_susp` is a proxy. **Pre-register both, and state the n
   required**: at 25 timeouts per 8234 s, another 8000 s doubles the event count.
2. **Do not re-score P-R2.** The two-class scheme is falsified as a partition and the race class has
   no signal (p = 0.997). Any future classification must be derived from the data, not from §8.9's
   four events.
3. **Close the `pc_state` question from source.** `pc_state` was 1 before all 19 fatals while the
   wire toggled in both directions. Read `bam_dmux_pc_irq()` and the notifier to determine when
   `pc_state` is written and whether it is ever cleared outside the SSR path. This is a source
   read, not an experiment, and it would say whether P2 can be salvaged.
4. **Deploy the sampler v3** (`scratch/pcfine.sh`) at the next natural break — it removes the ~73 ms
   constant and the ambiguity in "the last record before X" (§9). Requires restarting the sampler.
5. **Pull the full `dmesg` before any reboot** (memory RULE 6) and read `/sys/fs/pstore/` before
   concluding a ring is gone. The ring in this capture starts at 1434.99 s and has already lost two
   fatals.
6. **Not started, and untouched by this doc:** the ~903.675 s always-on clock and its latched
   offset; the userspace bearer rebuild; the AP-side `hyp`/`tz` swap; the Android leg.
