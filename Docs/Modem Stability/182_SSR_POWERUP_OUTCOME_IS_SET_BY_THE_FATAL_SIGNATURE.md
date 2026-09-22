# 182 — THE SSR POWERUP OUTCOME IS SET BY THE FATAL SIGNATURE; THE PC-ACK TIMEOUT IS **NOT** A CONSEQUENCE OF IT

**Date:** 2026-09-22
**Boots:** `eafa19f6-5a01-4ea8-b783-52e9f16bf862` (retrospective, 17 fatals) and
`59d9c272-d87c-486a-9d6c-4fd93e02fc29` (live, 9 fatals — 4 `a2_power.c:1189` → A, 2
`common_timer.c:390` → B, 2 **new** signature `lte_ml1_sm_conn_inter_freq_stm.c:712` → A **with
no timeout**, and 1 **new** signature `a2_taskq.c:759` → A **with** a timeout at Δ = 18.7 s).
The two timeout-free A cases falsify §6; the 18.7 s Δ kills its latency argument — see §8.6–§8.8.
**§8.9 then replaces the explanation**: a census of **all 230 resumes** in the live capture shows
that **every A fatal does resume** (~15–17 s later, 0/5 timing out), so §8.6/§8.7's "no resume"
was a **window-length artifact**; only **4 of 230** resumes time out, and all four are within
**1.22 s** of a suspend.
**Patch under test:** `msm89xx/patches/808-bam-dmux-stats.patch` (2000 ms pc-ack wait arm),
plus the patch-814 SSR-powerup retry that produces the A/B outcome line
**Deployed module:** `qcom_bam_dmux.ko` md5 `982b633e2a682e21ad69b6e85d273941`
**Evidence:** `evidence/112_bam_reinit_ab/`
* `console_ramoops_prev.txt` — md5 `c6253e4bf582b889f73829c278a0c0fc`, 1655 lines / 146379 B
* `pcfine_v1_boot59d9c272.txt` — md5 `01168319e64bbfca417499008d99acaa`, 71 records — the
  A-case proof of §8.1 (key = `pc_irq_count` only; see §8.1)
* `pcfine_v2_boot59d9c272.txt` — md5 `e2645f89bcd01f8f4bf1c5a2240d4791`, 16775 records — the
  fixed-key capture covering §8.2 (fatal #5), §8.4 (the orphan timeout) and §8.5.3 (fatal #6).
  **A growing snapshot, not a finished capture**: the sampler was still running when it was
  pulled, and the §8.2/§8.4 analyses were scored against an earlier 10893-record copy
  (md5 `ad02ab23a44e775e8b165e5f79996544`) that this one strictly contains. Reproduce against
  whichever copy; the records are append-only.
* `pcfine_boot59d9c272.txt` — md5 `2c0bc63754bad0bda2bde42962ae62d5`, 45 records (earlier snapshot)
* `dmesg_boot59d9c272_upto2590s.txt` — md5 `768b7c10de3fa8e0c544919c5b92f295`, 545 lines
* `score_bam_reinit.py`, `score_h3.py`, `census_pc.py`, `calibrate_sampler_clock.py`,
  `score_resume_window.py`, `resume_trigger.py`, `pcfine.sh`, `score_output.txt`
  (`census_pc.py` reproduces §8.4's
  pc-line / PM census and the flatness check on any capture; `calibrate_sampler_clock.py`
  reproduces §8.5.1's sampler clock error from three independent event types;
  `score_resume_window.py` reproduces §8.6 — for each labelled window it reports the pre-event
  PM state, whether a resume ran, whether a timeout followed, and the resume→timeout delay;
  **`resume_trigger.py` reproduces §8.9 — it censuses *every* `pm_resume_attempts` increment in a
  capture, with the state at each resume, the time since the previous suspend, whether a
  completion event followed, and the exact Fisher tests.** All
  parse the sampler formats; both window scorers import `census_pc.py`'s parser)
* `dmesg_boot59d9c272_full.txt` — md5 `81903ea91f010f8644a2b1618f5b776b`, 729 lines — the whole
  boot's ring, needed by `calibrate_sampler_clock.py`
* `pcfine_f9_boot59d9c272.txt` — md5 `4aae3807d80eee91a35be48fa29d3ba9`, 38548 records — the
  later pull again, covering **fatals #8 and #9** (§8.7, §8.8). Same append-only stream as the
  f7 copy (both start at uptime 3157.20); use this one for anything at or after uptime 5713.
* `pcfine_f7_boot59d9c272.txt` — md5 `adeb90d0ffb658e6b9b6869d99a3b129`, 28548 records — the
  earlier pull of the same sampler, covering **fatal #7** (§8.6). It strictly contains
  `pcfine_v2_boot59d9c272.txt` (both start at uptime 3157.20 and log the same tuple); the v2
  copy was pulled earlier and stops inside fatal #6. Use the f9 copy for anything at or after
  uptime 5713.

---

## 1. SOP compliance

| SOP step | done? |
| :-- | :-- |
| Comparative protocol against the stock-HMU05 ground truth | **YES** — every claim here is about the **OpenWrt port's own driver** (`qcom_bam_dmux.c`) and the modem's *reported* fatal site. No baseband was touched, and no firmware claim is made. |
| Pre-register the prediction **before** the data exists | **PARTIAL, and the gaps are stated** — §3's shortcut invalidated the pre-registration I had intended for the A/B-vs-signature association: it was read out of a console that already existed. But **H3 (§9) was registered before any A case existed on the live boot and was then confirmed by the first one to arise (§8.1)** — P1–P4, all four. The association in §5 is retrospective; the mechanism in §8.1 is not. **§8.4 is EXPLORATORY, not pre-registered**: the orphan timeout was found by continuing to sample, and its mechanism was read off the capture after the fact. It is reported as a single occurrence for exactly that reason. **§8.6 is opportunistic, and that is stated where it matters**: fatal #7 arrived while the sampler was running and was scored *after* it fired, so the §6 falsification is a post-hoc observation. What makes it credible rather than a fishing expedition is that it is a **falsification of a claim already written down** (this doc's own §6), the mechanism was then read from source (`pc_timeout_count` has exactly two writers, both inside `runtime_resume`), and the PM counters were **already in the sampler's change key** — so the discriminating measurement was not chosen after seeing the answer. **§8.7 and §8.8 ARE pre-registered, and this is the one clean instance in the doc**: §8.6 wrote down the rule "no resume ⇒ no timeout" *before* fatals #8 and #9 existed, and both then confirmed it — #8 as a replication of the no-resume/no-timeout case, and #9 as a case that *did* have a resume and *did* time out, with the within-window pair (§8.8.1) isolating the variable to the individual resume. The rule predicted both outcomes before they were measured. |
| Freeze the capture, hash it, score the frozen copy | **YES** — the pstore console was copied off the device and hashed before scoring; the scorer reads only the frozen copy. The live sampler was likewise pulled to a hashed file (§8.4's file is a **snapshot** taken while the sampler was still running, and the device copy keeps growing). |
| Keep a control | **YES** — (a) the 13 `lte_ml1_common_timer.c:390` fatals *inside the same boot* are the control for the 4 `a2_power.c:1189` ones; (b) the 14 B outcomes are the control for the A outcomes on the timeout claim; (c) the cold-boot powerup (row 1) is a no-fatal control; (d) **§8.4's orphan timeout is a control of a different kind — a timeout with no fatal at all**, which is what forced the §6 qualification. **(e) fatals #7–#9 are the strongest controls of all: two A cases *without* a timeout, and one whose timeout arrives 18.7 s late — the three measurements the earlier corpus could not supply, and the ones that finally falsified §6 rather than merely qualifying it. §8.8.1's two resumes inside a single window are the strongest of the strong: a control that varies exactly one variable.** |
| Verify the instrument before resting a claim on it | **YES, and it changed four conclusions** — §7.1 (the RX watchdog *has* rebuild paths, so its silence had to be checked, not assumed), §7.2 (the modem's `CMD_OPEN` burst had to be ruled out as the rebuilder), §8.3 (the counter I had been reading as an *edge* count is a **thread-run** count), and §8.5.1 (the sampler's own timestamp is **~73 ms early**, calibrated against three independent event types — without which §8.2's table reads as a contradiction). |
| State what is *not* established | **YES** — §11 |

---

## 2. What was asked

The lead from the previous session: after an SSR, `bam_dmux_ssr_powerup_work_func()`
(`qcom_bam_dmux.c:2336`) prints one of three outcomes. The teardown releases the channels
(`T5 rx released`), so `dmux->rx == NULL` should hold and **every** fatal should end in
outcome **A**. Only 4 of 17 did. The question was whether the other 13 were outcome **B**
(the modem asserted its pc line early and the *interrupt* path rebuilt the channels first)
or outcome **C** (the modem genuinely failed to assert within the ~77 s retry budget).

**Answer: B, 13 of 13. C never occurred in either boot.**

---

## 3. The instrument — a pstore console that should not have been there

The intended method was to run a telemetry sampler until enough fatals accumulated (~4 h at
the 903.675 s beat). That was unnecessary. `/sys/fs/pstore/console-ramoops-0` held
**146379 B of the previous boot's console** — 1655 lines, including 35 `SSR powerup` lines
covering all 17 of that boot's fatals, and it survived the reboot because ramoops is the
persistent *console* writer, not the panic-only `dmesg-ramoops` record.

This is a **process correction**: in the previous session I recorded (memory RULE 6) that the
old boot's ring was lost by rebooting, and built a watcher to capture the future. The data was
never lost — it was in pstore the whole time and I did not look. **Before concluding a ring is
gone, read `/sys/fs/pstore/`.**

### 3.1 The capture is bit-corrupted, so pairing must be by ORDER

The ramoops console carries the known byte-corruption trap. Observed in this very capture:

| line | rendered | should be | damage |
| :-- | :-- | :-- | :-- |
| 549 | `[ 3234.44768]` | `3234.447680` | digit lost |
| 1318 | `[13266.8&9229]` | `13266.869229` | `&` substituted |
| **1364** | **`[13066.869437]`** | **`13266.869437`** | **bit flip in the INTEGER part** |
| 756 | `fatal error receIved` | `received` | capital `I` |
| 1487 | `Z15073.101920]` | `[15073.101920]` | bracket replaced |

Line 1364 is the dangerous one: a plausible-looking but **wrong** timestamp. A scorer that
paired events by timestamp would silently mis-pair there. `score_bam_reinit.py` therefore pairs
by **file order**, which corruption cannot reorder, and reports timestamps only as labels.
Line 756 is why the first manual grep reported fatal #7 as unreadable — it had been looking for
`received`, and `receIved` did not match. The tolerant pattern `rece\w*ved` recovers it.

---

## 4. The mechanism, read from source

### 4.1 The three outcomes (`:2402-2423`)

```c
mutex_lock(&dmux->state_lock);
if (READ_ONCE(dmux->in_teardown)) { ... return; }
if (!dmux->rx) {
        WRITE_ONCE(dmux->pc_state, true);
        if (bam_dmux_power_on(dmux)) {
                bam_dmux_pc_ack(dmux);
                pm_runtime_set_active(dmux->dev);
                dev_info(... "successfully reinitialized BAM channels and rings");   /* A */
        } else { ... }
} else {
        dev_info(... "channels already active");                                     /* B */
}
```

* **A** — `dmux->rx == NULL`: nobody rebuilt the channels, so the work did.
* **B** — `dmux->rx != NULL`: something else rebuilt them first.
* **C** — the pc line never asserted within ~77 s; the retry loop gives up.

All three are preceded by an **unconditional** line:

```c
dev_info(dmux->dev, "bam_dmux: SSR powerup: modem pc_state=%d (waited %d ms)\n",
         pc_line, 200 + i * 20);
```

**The label is a misnomer**: the argument is `pc_line` (the level read from the irqchip), *not*
`dmux->pc_state`. `waited 200` means `i == 0`, i.e. the line was already high at the first poll
after the fixed `msleep(200)`. This is the one line that separates A/B/C from C, and the earlier
grep pattern missed it — which is why the lead was stuck at a source reading.

### 4.2 Who can rebuild `rx`?

| rebuilder | logs on success | counter |
| :-- | :-- | :-- |
| `bam_dmux_pc_irq()` assert branch (`:1939`) | **nothing** (only `dev_dbg`) | `pc_irq_count` (+1 unconditionally at `:1941`) |
| `bam_dmux_rx_watchdog_func()` (`:1309`, `!rx` path) | `dev_warn "modem awake (pc line asserted) but no channels, rebuilding"` | `pc_resync_count` |
| `bam_dmux_rx_watchdog_func()` (`:1330`, lost-edge path) | `dev_warn "PC line asserted while pc_state=0 (lost edge), resyncing"` | `pc_resync_count` |
| `bam_dmux_ssr_powerup_work_func()` A-branch | `dev_info "successfully reinitialized"` | — |

The watchdog paths both `dev_warn` **and** both bump `pc_resync_count`. That makes them
checkable rather than assumable — see §7.1.

### 4.3 The asymmetry that explains the timeout

The `pc_irq` assert branch ends with:

```c
complete_all(&dmux->pc_ack_completion);
wake_up_all(&dmux->pc_wait);
```

The powerup work's **A**-branch does **not**. It calls `bam_dmux_power_on()`,
`bam_dmux_pc_ack()` and `pm_runtime_set_active()`, and returns. A `bam_dmux_runtime_resume()`
already waiting on `pc_ack_completion` is therefore never woken by the A path. That is a
source-level candidate for §6.

---

## 5. Result 1 — the outcome is predicted by the fatal signature

Paired by file order. Rows 2–18 are the 17 fatals; row 1 is the cold-boot powerup.

| # | fatal t | signature | pc_line | waited | outcome |
| --: | --: | :-- | --: | --: | :-- |
| 1 | — (cold boot) | — | 1 | 560 | B |
| 2 | 524.211653 | **`a2_power.c:1189`** | 1 | 200 | **A** |
| 3 | 1427.099153 | `common_timer.c:390` | 1 | 200 | B |
| 4 | 2330.773740 | `common_timer.c:390` | 1 | 200 | B |
| 5 | 3234.44768 * | `common_timer.c:390` | 1 | 200 | B |
| 6 | 4138.119715 | `common_timer.c:390` | 1 | 200 | B |
| 7 | 5041.798167 | `common_timer.c:390` | 1 | 200 | B |
| 8 | 5949.210659 | **`a2_power.c:1189`** | 1 | 200 | **A** |
| 9 | 6852.985676 | `common_timer.c:390` | 1 | 200 | B |
| 10 | 7756.662692 | `common_timer.c:390` | 1 | 200 | B |
| 11 | 8719.020382 | **`a2_power.c:1189`** | 1 | 200 | **A** |
| 12 | 9622.891300 | `common_timer.c:390` | 1 | 200 | B |
| 13 | 10526.566407 | `common_timer.c:390` | 1 | 200 | B |
| 14 | 11430.241652 | `common_timer.c:390` | 1 | 200 | B |
| 15 | 12362.261426 | **`a2_power.c:1189`** | 1 | 200 | **A** |
| 16 | 13265.751554 | `common_timer.c:390` | 1 | 200 | B |
| 17 | 14169.427048 | `common_timer.c:390` | 1 | 200 | B |
| 18 | 15073.101717 | `common_tiler.c:390` * | 1 | 560 | B |

\* corrupted in the capture; see §3.1.

```
                    A    B
a2_power.c:1189     4    0
common_timer:390    0   13
```

**Fisher exact, one-sided p = 0.00042017; two-sided p = 0.00042017.** (The two coincide, as
they must for a perfect table.)

**The association is perfect and the split is clean.** `a2_power.c:1189` → A, 4/4.
`lte_ml1_common_timer.c:390` → B, 13/13. **Outcome C never occurred in this boot.**

**⚠ The two-way split is a property of THIS boot's signature mix, not of the outcome rule.** The
live boot has since produced **two more** signatures — `lte_ml1_sm_conn_inter_freq_stm.c:712`
(fatals #7 and #8, both at 5709.425429 / 6611.040190) and `a2_taskq.c:759` (fatal #9 at
6692.990034) — and **all three go to A**, not B. So the correct statement of Result 1 is *not*
"`a2_power` means A and `common_timer` means B"; it is that **A and B are both reachable and the
signature is only a correlate**. With fatals #7–#9 the live boot's mix is 4 `a2_power` → A,
2 `common_timer` → B, 2 `lte_ml1_sm_conn_inter_freq_stm` → A, 1 `a2_taskq` → A. The next signature
that arises could fall either way, and §8.6–§8.8 show the *timeout* half of §6 was over-read from
a corpus in which the two signatures happened never to cross over.

### 5.1 Two caveats

* Row 18's `waited 560` is anomalous — it is the only **fatal** SSR that did not assert at the
  first poll (16 of 17 did). Its outcome is B, so it does not disturb the split, but it shows the
  200 ms figure is not universal.
* `waited 200` is an artefact of the fixed `msleep(200)`, not a measurement: it means "already
  asserted when the poll began". The poll cannot resolve the assert time below 200 ms.

---

## 6. Result 2 — the pc-ack timeout correlates with A, but is **not** caused by it (fatal #7)

Doc 181 §6 established that the `pc-ack timeout` is `a2_power`-specific at 4/4 vs 0/10
(p = 0.003497), and treated that as a property of the signature. This boot appeared to separate
the two — and then fatal #7 separated them a third way and falsified the separation. The section
is kept in its original order so the falsification is visible as a correction rather than
retrofitted.

*The reading as first written (retrospective boot only), **before fatal #7 existed**:*

| | timeout | no timeout |
| :-- | --: | --: |
| **A** | **4** | 0 |
| **B** | 0 | 14 |

**Fisher exact one-sided p = 0.00032680.** And the timeout **follows** the A outcome, at a
near-constant latency:

| fatal | A ("successfully reinitialized") | pc-ack timeout | Δ |
| --: | --: | --: | --: |
| 2 | 525.323701 | 526.440017 | **1.116316 s** |
| 8 | 5950.318477 | 5951.427446 | **1.108969 s** |
| 11 | 8720.129599 | 8721.331363 | **1.201764 s** |
| 15 | 12363.367529 | 12364.614338 | **1.246809 s** |

The live boot adds four more, all `a2_power` → A:

| fatal | A ("successfully reinitialized") | pc-ack timeout | Δ |
| --: | --: | --: | --: |
| 1 | 533.185843 | 534.360805 | **1.174962 s** |
| 4 | 2976.800897 | 2978.056277 | **1.255380 s** |
| 5 | 3885.730123 | 3886.848426 | **1.118303 s** |
| 6 | 4809.243040 | 4810.361343 | **1.118303 s** |

Across both boots, **8 A cases give Δ ∈ [1.109, 1.255] s** (range 146 ms), and **B has produced
0 timeouts in 17 outcomes** (13 fatal B + 1 cold-boot B from the retrospective boot, 2 fatal B
+ 1 cold-boot B from the live boot). Fatals #5 and #6 agree to **1 µs**; see §8.5.3.

### 6.1 Fatals #7–#9 — the claim as originally written is FALSIFIED

The live boot then produced **two A cases with NO timeout** and **one with a timeout 16× later
than the "constant" Δ**:

| fatal | signature | outcome | timeout | Δ(A → timeout) |
| --: | :-- | :-- | :-- | --: |
| 7 | `lte_ml1_sm_conn_inter_freq_stm.c:712` | A @ 5710.863153 | **none** | — |
| 8 | `lte_ml1_sm_conn_inter_freq_stm.c:712` | A @ 6612.174387 | **none** | — |
| 9 | `a2_taskq.c:759` | A @ 6695.432008 | yes @ 6714.134806 | **18.702798 s** |

§8.6–§8.8 give the measurements and the mechanism. So the table above is **incomplete**, and the
corrected version is:

| | timeout | no timeout |
| :-- | --: | --: |
| **A** | **9** | **2** |
| **B** | 0 | 17 |

Fisher exact one-sided p = **0.00000796** (it was 0.00000092 for the perfect table). The
association survives — it is still very strong — but **"A guarantees the timeout" is dead**, and
with it the re-attribution below. **Doc 181 §6's original `a2_power` attribution and this
section's A-attribution were both reading a correlate**: the true antecedent is a
`bam_dmux_runtime_resume()` whose wait is never completed (§8.6), and §8.8.1 demonstrates it
*within a single window*.

### 6.2 What survives, and what does not

**Withdrawn:** "the pc-ack timeout is a **consequence** of the A path", and "A **guarantees** the
timeout". Fatal #7 is A and produced none.

**Survives, as a correlate only:** A and B differ sharply in timeout rate (9/11 vs 0/17). The
reason is now known and is not causal: an A fatal pins the pc line high, so **if** a resume
occurs its vote cannot produce a transition. B pins it low, so the resume's own vote *is* the
transition. A raises the *probability* of a timeout; it does not produce one.

**Also withdrawn:** §4.3's "the A-branch does not `complete_all()`" as *the* explanation. It is
still true and still a real asymmetry, but it cannot be the cause of the A→timeout correlation,
because fatal #7 and fatal #8 took the A branch with no wait pending for it to fail to complete,
and — decisively — **§8.8.1's resume #302 completed inside the very same A window as the resume
that timed out.** The missing `complete_all()` is a **latent** defect (it would matter if a
resume were ever waiting *through* the A rebuild), not the measured one.

**Reading.** A precedes the timeout by ~1.17 s in the two cases where the resume happened before
the A line (#5, #6) — **and by 18.7 s in the one case where it happened after (#9)**, which is
why the Δ is not a property of A at all (§8.8.2). In the two A cases with no timeout, **no resume
occurred anywhere in the window** (§8.6, §8.7). That is the whole difference.

---

## 7. Result 3 — what is ruled out

### 7.1 The RX watchdog is not the rebuilder

`bam_dmux_rx_watchdog_func()` can rebuild `rx` (§4.2). It did not: this capture contains
**zero** `rebuilding` / `resyncing` / `lost edge` lines, and the live telemetry reports
`pc_resync_count: 0`. So the B-case rebuilder is `bam_dmux_pc_irq()`.

**⚠ Corrected by §8.6.2.** The `pc_resync_count: 0` half of that sentence was true of *this
capture* and is now stale: the live boot's watchdog fired the lost-edge path at **5726.582280**
(`pc_resync_count` 0 → 1), 17.2 s after fatal #7. The conclusion of this section is unaffected —
that resync is not the rebuilder for any of the 17 fatals scored here — but the *evidence* is a
statement about a window, not about the path.

### 7.2 The modem's `CMD_OPEN` burst is not the rebuilder

142 `received CMD_OPEN` lines appear. A burst of 7–8 follows **every** fatal SSR, both A and B
(rows 2 and 3 both show 8). On the A rows it also arrives *after* the `successfully
reinitialized` line (row 2: A at 525.323701, first `CMD_OPEN` at 525.730). It cannot be the
discriminator, and it cannot be the rebuilder on the A rows.

### 7.3 The pre-fatal pc line is low in *both* signatures — and this does NOT falsify the line hypothesis

All **186** `RX watchdog: quiesced` lines in the capture read `pc_state=0, pc_line=0`, with
quiesce counters up to 2820 s. It is tempting to conclude from this that the pc line is low
before every fatal and therefore cannot explain A. **That conclusion is wrong, and I drew it
earlier in this session before checking the instrument.**

The watchdog prints **only while quiesced**, on a ~60 s cadence, and the last line before a
fatal can be tens of seconds earlier — 29.5 s before fatal #8. The live sampler (§8) shows the
line toggling every few seconds to ~100 s. A 29.5 s gap is therefore **wide enough to contain a
full assert**, and the quiesce line says nothing about the state at the fatal.

This is the same family of error as the corpus's other instrument traps: **a control that
silently did not cover the window is worse than no control**, because it turns an untested
hypothesis into a false negative. The watchdog is a *quiesce* instrument, not a *pre-fatal*
instrument.

---

## 8. Result 4 — the live replicate, and the measurement the console cannot give

A 45 Hz telemetry sampler (`scratch/pcfine.sh`) was deployed to resolve the ~1.1 s SSR window.
It logs on **change of the event fields only** (the uptime and quiesce counters change every
iteration and would otherwise defeat change-detection). Verified before use:

* `rx_telemetry_show()` takes **no `state_lock`** — only `spin_lock_irqsave(&dmux->rx_lock)`
  over 32 slots (`:2132`) — so a 45 Hz reader **cannot contend with the SSR path it measures**.
* Measured cost ~80 ticks over ~70 s ≈ **~1 % of one core**; the loop rate is 22.1 ms/iter.
* It survives SSH detachment via `setsid` (busybox `sleep` rejects fractional arguments, hence a
  fork-per-iteration loop rather than a timed one).

**Fatal #3 on boot `59d9c272`** — `lte_ml1_common_timer.c:390` at **2338.609206** → outcome **B**:

| event | time |
| :-- | --: |
| fatal | 2338.609206 |
| teardown scheduled / T4 `state_lock` | 2338.632036 / 2338.671748 |
| `stopped remote processor` | 2338.810815 |
| `SSR after powerup: scheduling powerup work` / `is now up` | 2339.518926 / 2339.519032 |
| `SSR powerup: modem pc_state=1 (waited 200 ms)` | 2339.719214 |
| **outcome B** `channels already active` | 2339.719303 |
| `received CMD_OPEN (1) on channel 0..7` | 2340.140581 … 2340.191208 |

The sampler's last record **before** the fatal:

```
162|162 0 0 1 0 0 1 1433 82 82 82 82 24 2331.34 138664
```

`pc_irq_count=162`, `pc_state=0`, `pc_line=0`, `pc_quiesce_ms=138664` — **the line was low at the
fatal**, and low for the preceding 138.7 s. The next record:

```
163|163 1 1 1 0 0 0 1433 83 82 83 82 24 2339.52 0
```

At uptime **2339.52** — within 22 ms of `is now up` (2339.519032) — `pc_irq_count` went
**162 → 163** with `pc_state 0 → 1` and `pc_line 0 → 1`, and `rx_tearing_down` went 1 → 0.

**So for the B case, measured rather than argued:**

1. the line was **low** at the fatal;
2. the modem's post-reload assert produced a **genuine rising edge**;
3. `bam_dmux_pc_irq()` **consumed it** (`pc_irq_count` +1) and rebuilt the channels;
4. the powerup work's poll then found `rx != NULL` → **B**.

Note this also means the poll's `pc_state=1 (waited 200 ms)` is *not* evidence that the line was
high early — it is evidence only that the line was high when the poll began, 200 ms after the
work started.

### 8.1 The A case (fatal #4) — H3's P1 and P3 are confirmed

**Fatal #4 at 2975.685045 is `a2_power.c:1189` → outcome A.** The sampler was running.

| event | time |
| :-- | --: |
| fatal | 2975.685045 |
| teardown scheduled / T4 `state_lock` | 2975.706874 / 2975.746448 |
| `stopped remote processor` | 2975.882878 |
| `SSR after powerup: scheduling powerup work` / `is now up` | 2976.588566 / 2976.588652 |
| `SSR powerup: modem pc_state=1 (waited 200 ms)` | 2976.799456 |
| **outcome A** `successfully reinitialized` | 2976.800897 |
| `received CMD_OPEN (1) on channel 0..7` | 2977.208468 … 2977.224677 |
| `modem pc-ack timeout during resume` | 2978.056277 |

The sampler records that bracket it:

| record | uptime | `pc_irq_count` | `pc_state` | `pc_line` | `pc_timeout_count` |
| :-- | --: | --: | --: | --: | --: |
| last **before** the fatal | 2973.58 | 185 | **1** | **1** | 1 |
| next | 2979.23 | **186** | 0 | 0 | **2** |

**Exactly one `pc_irq` thread run occurred in the whole window** (185 → 186), and the state it
produced is `pc_state = 0, pc_line = 0` — i.e. that single run is the final **1 → 0 fall**. The
line was therefore high at the fatal and **stayed** high through the teardown, the reload and the
A rebuild, which is why the poll at 2976.799456 found it high and why `bam_dmux_pc_irq()` **never
ran** between the teardown and the A outcome. (Read with §8.3: the counter counts thread runs, not
edges, so this says "the handler did not run", not "no edge arrived".)

**Why this is still rigorous despite a bug in the sampler.** The change key is not the 14-field
event tuple it was meant to be: `${cur%%|*}` on a line of the form `iq|aq ps pl …` reduces the key
to `pc_irq_count` **alone**, so the sampler only logged when `pc_irq_count` changed (this is why
`rx_tearing_down`, `rx_callbacks` and `cmd_open` transitions inside the window were not logged).
For *this* claim that is exactly the right keying: **every run of `bam_dmux_pc_irq()` is guaranteed
a record**, which is precisely the quantity P3 asserts. It is **not** sufficient to track wire
transitions — a masked edge moves the line without moving the counter — which is why §8.2 needed
the full-tuple key. The bug was fixed for the ongoing run (§13), which now keys on the full tuple.

**Prediction score (§9, registered before this case existed):**

| | prediction | result |
| :-- | :-- | :-- |
| **P1** | last pre-fatal record shows `pc_state = 1, pc_line = 1` for an A fatal | **CONFIRMED** |
| **P2** | last pre-fatal record shows `pc_state = 0, pc_line = 0` for a B fatal | **CONFIRMED** (fatal #3) |
| **P3** | `pc_irq_count` does not increment between the teardown and the A outcome | **CONFIRMED** — the only increment is the trailing deassert |
| **P4** | in a B case `pc_irq_count` increments exactly once at the assert | **CONFIRMED** (fatal #3) |

**The A/B contrast, measured on the same boot, with the same instrument:**

| fatal | signature | line at the fatal | `pc_irq` run in the window | outcome |
| --: | :-- | :-- | :-- | :-- |
| 3 | `common_timer.c:390` | **low** (low for the preceding 138.7 s) | **one, rising**, at the assert | B |
| 4 | `a2_power.c:1189` | **high** | **one, falling only**, at the very end | A |

**And the timeout follows A again**: fatal #4's A at 2976.800897 → `pc-ack timeout` at
2978.056277, **Δ = 1.255380 s**. Together with the other A cases that made the A → timeout
association **8 of 8 across two boots, 0 of 17 for B** at the time this was written. **It is now
8 of 9: fatal #7 is the counter-example (§8.6), so read this paragraph as the correlation, not
the rule (§6.2).**

### 8.2 Fatal #5 — H3 confirmed at n = 2, and the window exposes the real mechanism

**Fatal #5 at 3884.610521 is `a2_power.c:1189` → outcome A** (reinitialized at 3885.730123),
timeout at 3886.848426 (**Δ = 1.118303 s**).

| record | uptime | `pc_irq_count` | `pc_state` | `pc_line` | `rx_tearing_down` |
| :-- | --: | --: | --: | --: | --: |
| **last pre-fatal record** | 3883.53 | 269 | **1** | **1** | 0 |
| (fatal) | 3884.610521 | | | | |
| notifier `pc_state = false` (`:2435`) | 3884.57 | 269 | **0** | **1** | 0 |
| T5 `rx_tearing_down = true` (`:1572`) | 3884.61 | 269 | 0 | 1 | **1** |
| line falls | 3884.64 | **269** | 0 | 0 | 1 |
| | 3884.75 | **269** | 0 | 0 | 1 |
| line rises | 3885.54 | **269** | 0 | 1 | 1 |
| work sets pc_state | 3885.66 | **269** | 1 | 1 | 0 |
| (`successfully reinitialized`) | 3885.730123 | | | | |
| | 3886.79 | **269** | 1 | 1 | 0 |
| thread finally runs | 3888.06 | **270** | 0 | 0 | 1 |

**P1 HIT, P3 HIT → H3 confirmed on this case.** But the window shows something the previous
cases could not: **the line moved 1 → 0 at 3884.64 and 0 → 1 at 3885.54 with `pc_irq_count`
completely flat at 269.** Two wire transitions, zero counter movement. That is only possible if
`pc_irq_count` does not count edges — and it does not (§8.3).

**Two corrections to how this table was first read** (both found on fatal #6, §8.5):

* The record at **3884.57 is NOT pre-fatal.** The sampler's `<UP>` label is **~73 ms earlier
  than the telemetry it carries** (§8.5.1), so that record's values were read at ≈3884.64 —
  after the fatal — and they are the *first driver reaction* to it: `bam_dmux_ssr_notifier_cb()`
  sets `WRITE_ONCE(dmux->pc_state, false)` at **`:2435`**, in the `QCOM_SSR_BEFORE_SHUTDOWN`
  case, ~22 ms after the fatal and ~57 ms before the teardown work sets `rx_tearing_down`. The
  earlier reading of this row as "the last pre-fatal record, with `pc_state = 0`" was therefore
  wrong, and it was the reason P1 looked inconsistent with the row above it. **The last
  genuinely pre-fatal record is 3883.53, where `pc_state = 1` and `pc_line = 1`** — which is
  what P1 predicted. The "lead time" of this case is therefore **≈0.92 s** (3884.610521 −
  3883.53), not 0.001 s.
* `pc_state = 0` **before** `rx_tearing_down = 1` is not an anomaly and not a driver bug: it is
  the designed order, because the notifier reacts to the SSR announcement while
  `rx_tearing_down` is only set later, inside `bam_dmux_power_off()` on the teardown work's
  thread (`:1918` → `:1572`).

### 8.3 Correction — `pc_irq_count` counts THREAD RUNS, not edges

```c
ret = devm_request_threaded_irq(dev, dmux->pc_irq, NULL, bam_dmux_pc_irq,
                                IRQF_ONESHOT, NULL, dmux);          /* :2581 */
```

The **primary handler is `NULL`** and `bam_dmux_pc_irq` is the **`thread_fn`**. The kernel then
installs its *default primary handler*, which under `IRQF_ONESHOT` **masks the line and wakes
the thread**. `dmux->pc_irq_count++` is the first statement of `bam_dmux_pc_irq` — i.e. of the
**thread**. So the counter is incremented when the thread **runs**, and a thread blocked on
`state_lock` (which the SSR teardown holds from T4 to after T8) contributes nothing, while the
masked line delivers no further transitions.

**This is the actual mechanism, and it is cleaner than H3's phrasing:**

> **H4.** The pc line is sampled through a threaded `IRQF_ONESHOT` irq whose line is **masked
> from the moment an edge arrives until the thread returns**. At an **A** fatal the line is
> **already HIGH**, so a thread is already queued and the line is masked when the SSR begins;
> the teardown's `disable_irq`/`enable_irq` resync cannot manufacture an edge that the hardware
> has already consumed, so nothing rebuilds `rx` through the interrupt path and the powerup
> work's **level-poll** is the only thing that sees the high line → **A**. At a **B** fatal the
> line is **LOW**, no thread is queued and the line is not masked, so the modem's post-reload
> rise is delivered normally and the thread rebuilds → **B**.

**H3's wording was too strong and is corrected here.** H3 said "no rising edge *reaches*
`bam_dmux_pc_irq()`". The measurable fact is narrower: **the thread did not run inside the
window**. Whether an edge arrived at a masked line is *not observable* from `pc_irq_count`,
because that counter lives in the thread. So P3 is confirmed **as written** ("`pc_irq_count` does
not increment between the teardown and the A outcome") but its interpretation changes from "the
edge was lost" to "the handler did not run" — which is exactly the quantity the A/B outcome
depends on, and it is the reason a queued-but-blocked thread loses to the work's level-poll.

A second observation, offered as an observation and not a claim: in this window `pc_state`
disagreed with the wire (`pc_state = 0, pc_line = 1`) for roughly a second **before** the fatal.
The RX watchdog's resync path exists for precisely that condition, but it returns early whenever
`in_teardown` is set and it runs on a ~60 s cadence, so it did not fire here (`pc_resync_count`
stayed 0).

### 8.4 An orphan pc-ack timeout with NO fatal — H4's mechanism reproduced outside the SSR

While the sampler was still running, `pc_timeout_count` went **3 → 4** at uptime **4186.364155**
with **no fatal anywhere near it** (the boot's last fatal is #5 at 3884.610521; the console
between 3886.85 and 4186.36 holds only `CMD_OPEN`s and `port wwan0qmi0 attached`). This is a
**runtime-PM** timeout, not an SSR one, and it is the first timeout on either boot that is not
downstream of an A outcome. The frozen sampler resolves it completely:

| uptime | `pc_irq_count` | `pc_ack_irq_count` | `pc_state` | `pc_line` | `pc_timeout_count` | `pc_vote_tx` | `pc_unvote_tx` | `pm_resume` | `pm_suspend` |
| --: | --: | --: | --: | --: | --: | --: | --: | --: | --: |
| 3951.50 | 289 | 303 | 1 | 1 | 3 | 155 | 154 | 155 | 154 |
| *235 s with the tuple frozen; only `rx_callbacks` climbs, 35308 → 62185* | | | | | | | | | |
| 4184.06 | 289 | 303 | **1** | **1** | 3 | 155 | **155** | 155 | **155** |
| 4184.14 | 289 | 303 | **1** | **1** | 3 | **156** | 155 | **156** | 155 |
| 4186.32 | 289 | 303 | 1 | 1 | **4** | 156 | 155 | 156 | 155 |

Read out:

1. From 3951.50 to 4184.06 the whole tuple is **frozen for 235 s** — `pc_state = 1`,
   `pc_line = 1`, `rx_tearing_down = 0`, `pc_irq_count` flat — while `rx_callbacks` climbs at
   ~114/s. The modem was **fully awake and busy**, and the device never suspended.
2. At **4184.06** a suspend was attempted (`pm_suspend_attempts` 154 → 155, `pc_unvote_tx`
   154 → 155). The line was **still HIGH** at that instant and it **never fell** — no `pc_irq`
   run, no `rx_tearing_down`.
3. **~80 ms later**, at **4184.14**, a resume voted (`pm_resume_attempts` 155 → 156,
   `pc_vote_tx` 155 → 156). Because the line was **already HIGH**, the vote produced **no rising
   edge**, so `bam_dmux_pc_irq()` did not run (`pc_irq_count` stayed 289) and
   `bam_dmux_pc_ack_irq()` did not fire either (`pc_ack_irq_count` stayed 303).
4. Neither completion source ran, so `wait_for_completion_timeout()` in
   `bam_dmux_runtime_resume()` (`:2066`) expired after 2000 ms → the timeout at **4186.32**
   (Δ = 2.18 s from the vote, matching the reported `pm_last_resume_ms: 2178`).

**What this means for §6.** §6's claim — "the pc-ack timeout is a consequence of A" — is an
association over the fatals, and it stood at the time this was written (A → timeout 7/7 at that
count, B → 0/17). But this event already showed A is **not the only way** to produce a timeout.
The general condition is the one H4 names: **the completion is completed only by
`bam_dmux_pc_irq()` (`:1977`) or `bam_dmux_pc_ack_irq()` (`:1997`), and both require a line
*transition***. A fatal makes that condition *certain* (the line is pinned high and masked); an
80 ms suspend/resume pair makes it *possible* without any fatal at all. The wording chosen here
was "A **guarantees** the timeout" — **and §8.6 then killed even that**, because fatal #7 is A
with no resume in its window and therefore no timeout. The correct statement, superseding both
this paragraph and §6, is that **the timeout requires a `bam_dmux_runtime_resume()` and is
completed only by a line transition; a fatal supplies the second half but not the first.**

**This section is therefore also the first place the real antecedent was visible** — the orphan
timeout has no fatal, so it was already proof that A was not necessary. What was still missing
was proof that A was not *sufficient*, and fatal #7 supplied it.

**What this means for H4.** This is an **independent, non-fatal confirmation of H4's core
mechanism**, and it is the cleanest case in the corpus: no SSR, no teardown, no lock contention,
no masked line — just a resume whose line did not transition, and a timeout that followed
exactly as H4 predicts. The mechanism does not need the fatal to be true; the fatal is simply
one way to guarantee the precondition.

**The second completion source is flat too, in both windows.** `bam_dmux_runtime_resume()`'s
wait can be completed by `bam_dmux_pc_irq()` (`:1977`) **or** by `bam_dmux_pc_ack_irq()`
(`:1997`), so §11.2 has kept two readings alive: "the vote produced no edge" and "the modem did
not ack". The sampler's fixed key shows that in these windows the two readings **coincide**:

| window | `pc_irq_count` | `pc_ack_irq_count` | outcome |
| :-- | :-- | :-- | :-- |
| fatal #5, 3884.57 → 3886.79 (§8.2) | **269 → 269 (flat)** | **275 → 275 (flat)** | A, then timeout at 3886.85 |
| orphan, 4184.06 → 4186.32 (§8.4) | **289 → 289 (flat)** | **303 → 303 (flat)** | timeout at 4186.32 |

Neither source ran. **This does not identify which one is "the" cause, and it should not be read
as "the ack is downstream of the pc irq".** The normal-operation census argues against that
reading: outside the two windows `pc_ack_irq_count` moves on its own — e.g. at 3406.48 it goes
203 → 204 while `pc_irq_count` is still 201 and the line is still high, 170 ms before the
`pc_irq` run at 3406.65 — which is why it runs *ahead* of the pc counter overall (303 vs 289).
`bam_dmux_pc_ack()` (the AP's ack-bit toggle, which elicits the modem's ack) is called from
`bam_dmux_pc_irq()` (`:1955`, `:1973`), from the RX watchdog (`:1336`, `:1351`) and from the SSR
powerup work (`:2412`), so the two paths are coupled but not ordered. What the measurement
supports is the weaker and sufficient statement: **in both timeout windows, neither completion
source ran, so the driver offers no case in which one of them rescued the wait.** In the A case
the thread does eventually run — `pc_irq_count` goes 269 → 270 at **3888.06**, ~3.4 s after the
fatal, and `pc_ack_irq_count` follows to 276 — but by then the 2000 ms wait has already expired
at 3886.85. In the orphan case the thread never runs at all, because the line never transitions.

**A latency cost, not a stability one.** In the orphan window the state was already correct
throughout (`pc_state = 1`, `pc_line = 1`, `rx_tearing_down = 0`, `rx_callbacks` climbing at
~114/s), and it stayed correct after the timeout — the sampler shows `pc_timeout_count = 4` with
the data plane untouched. So this event's entire cost is the **2000 ms** the resume spent in
`wait_for_completion_timeout()`, which is the same latency class the corpus already attributes to
the 250 → 2000 ms widening (memory: *"a LATENCY defect, not stability"*). It is a second,
independent demonstration that the pc-ack wait can burn its full budget on a vote that needed no
work — and, unlike the A cases, with no fault anywhere in sight.

**What is NOT established.** (a) It is **one** occurrence — the sampler caught the only orphan
timeout on this boot, and the boot has produced exactly one. (b) *Why* the modem held the line
across the 80 ms unvote is not determined here; "the modem's deassert is slower than 80 ms" and
"the unvote was coalesced with the re-vote" are both consistent with the data and are not
separated. (c) Whether the 80 ms suspend/resume pair is itself abnormal is not assessed — with
155 suspend attempts against 143 completions there is PM churn on this boot, but no baseline for
what is normal.

### 8.5 Fatal #6, the sampler's clock error, and the `pc_state = 0 / rx_tearing_down = 0` puzzle

Fatal #6 at **4808.139745** is `a2_power.c:1189` → **A** (reinitialized at 4809.243040), timeout
at 4810.361343 (**Δ = 1.118303 s** — *identical to fatal #5 to the microsecond*). Scoring it
required resolving a state the source said was impossible, and that turned up a clock error in
the instrument.

#### 8.5.1 The sampler's `<UP>` label is ~73 ms EARLIER than the telemetry it carries

`pcfine.sh` reads `/proc/uptime` in awk's `BEGIN` block and **then** reads the telemetry file, so
the `<UP>` it prints is a **lower bound** on when the telemetry values were actually sampled.
The magnitude is measured, not guessed, using **three independent co-observed event types**
(`calibrate_sampler_clock.py`):

| pairing | how the two sides are identified | pairs | skew (dmesg − sampler `<UP>`) |
| :-- | :-- | --: | --: |
| `cmd_open` counter ↔ `received CMD_OPEN (1) on channel N` | the counter and the print are 1:1 | 6 | 0.0681 – 0.0806 s |
| `pc_state` 1→0 ↔ `SSR before shutdown` | `bam_dmux_ssr_notifier_cb()` sets it at **`:2435`**, on that print | 2 | 0.0624 – 0.0716 s |
| `rx_tearing_down` 0→1 ↔ `T5 rx released` | `bam_dmux_ssr_teardown()` → `bam_dmux_power_off()` sets it at **`:1572`**, before T5 prints | 2 | 0.0685 – 0.0793 s |

**All 10 pairs: 0.0624 – 0.0806 s, mean 0.0729 s.** The three event types are independent, and
they agree, so this is a property of the sampler and not of one code path.

**Consequences, and they are the reason this matters:**

* **Relative ordering inside the sampler is unaffected.** `<UP>` is monotonic across records, so
  "A came before B" claims between two sampler records still stand — including §8.4's whole
  analysis, which never compares a sampler timestamp to a dmesg one.
* **Absolute alignment to dmesg is not good to better than ~±60 ms**, and every claim of the form
  "the last record *before* the fatal" is ambiguous at that scale. §8.2's table was read wrongly
  for exactly this reason (§8.5.3).
* The direction is structural, not a fluke: the `BEGIN`-block read guarantees the label is early.
  The magnitude varies with loop cost under load, so the correct statement is a **range**, not a
  constant.

#### 8.5.2 The `pc_state = 0 / rx_tearing_down = 0` state is real, and it is the notifier

The sampler shows a record with `pc_state = 0` while `rx_tearing_down = 0`, then the next with
`pc_state = 0` and `rx_tearing_down = 1` — in **both** fatal #5 and fatal #6. Reading only the
teardown work (`bam_dmux_ssr_teardown_work_func()`, `:2264-2334`) that state looks impossible:
the work sets `rx_tearing_down` via `bam_dmux_power_off()` (`:1918` → `:1572`) *before* it sets
`pc_state = false` (`:2308`).

**It is not impossible — `pc_state = false` is written earlier, in the SSR *notifier*:**

```c
case QCOM_SSR_BEFORE_SHUTDOWN:
        dev_info(dmux->dev, "bam_dmux: SSR before shutdown: scheduling teardown work\n");
        WRITE_ONCE(dmux->in_teardown, true);
        WRITE_ONCE(dmux->pc_state, false);          /* :2435 */
        dmux->ssr_powerup_retries = 0;
        schedule_work(&dmux->ssr_teardown_work);
```

So the order is `pc_state = false` (notifier, on the modem's crash notification) → *then* the
teardown work runs → *then* `rx_tearing_down = true`. The measured offsets are extremely stable
across **all nine** fatals on this boot:

| fatal | `fatal error received` | `SSR before shutdown` (ps=false) | `T5 rx released` (td=true) | Δ notifier | Δ T5 |
| --: | --: | --: | --: | --: | --: |
| 1 | 532.081043 | 532.102979 | 532.159879 | +21.9 ms | +78.8 ms |
| 2 | 1434.938927 | 1434.961891 | 1435.018706 | +23.0 ms | +79.8 ms |
| 3 | 2338.609206 | 2338.632036 | 2338.688922 | +22.8 ms | +79.7 ms |
| 4 | 2975.685045 | 2975.706874 | 2975.763879 | +21.8 ms | +78.8 ms |
| 5 | 3884.610521 | 3884.632369 | 3884.689285 | +21.8 ms | +78.8 ms |
| 6 | 4808.139745 | 4808.161574 | 4808.218511 | +21.8 ms | +78.8 ms |
| 7 | 5709.425429 | 5709.449091 | 5709.505999 | +23.7 ms | +80.6 ms |
| 8 | 6611.040190 | 6611.064034 | 6611.120840 | +23.8 ms | +80.7 ms |
| 9 | 6692.990034 | 6693.011776 | 6693.068722 | +21.7 ms | +78.7 ms |

**So `ps = 0` with `td = 0` is a designed 57 ms window**, and the sampler resolves it because the
two transitions are 57 ms apart — comfortably wider than the ~22 ms loop. Across all nine fatals
the notifier offset spans **+21.7 … +23.8 ms** and the T5 offset **+78.7 … +80.7 ms** — a spread
of 2.1 ms over 6 160 s of uptime, i.e. the crash-notification path is not accumulating any drift.

#### 8.5.3 Fatal #6 — a third A case, and P1/P3 hold

| record | uptime | `pc_irq_count` | `pc_state` | `pc_line` | `rx_tearing_down` |
| :-- | --: | --: | --: | --: | --: |
| **last pre-fatal record** | 4807.09 | 317 | **1** | **1** | 0 |
| (fatal) | 4808.139745 | | | | |
| notifier `pc_state = false` (`:2435`) | 4808.09 | 317 | **0** | **1** | 0 |
| T5 `rx_tearing_down = true` (`:1572`) | 4808.15 | 317 | 0 | 1 | **1** |
| line falls | 4808.17 | **317** | 0 | 0 | 1 |
| | 4808.29 | **317** | 0 | 0 | 1 |
| line rises | 4809.08 | **317** | 0 | 1 | 1 |
| work sets pc_state (`:2410`) | 4809.17 | **317** | 1 | 1 | 0 |
| (`successfully reinitialized`) | 4809.243040 | | | | |
| (timeout) | 4810.361343 | | | | |

**P1 HIT** (last pre-fatal record: `pc_state = 1`, `pc_line = 1`), **P3 HIT** (`pc_irq_count` flat
at 317 from 4805.91 through the A outcome). This is the **third** A case and the second scored
with the fixed key, and it reproduces §8.3 exactly: the line moved **1 → 0 at 4808.17 and 0 → 1
at 4809.08 with the counter flat at 317** — two wire transitions, zero counter movement.

**The identical Δ is worth one line.** Fatal #5 and fatal #6 have A→timeout intervals that agree
to **1 µs** (1.118303 s) while their fatal→notifier and fatal→T5 offsets agree only to ~1 ms and
their absolute SSR durations differ by ~16 ms. That is a strong hint the interval is set by a
deterministic quantized pair (the 2000 ms `wait_for_completion_timeout` against a fixed poll
offset) rather than by the SSR's own timing — but **two cases is not enough to claim that**, and
it is recorded as an observation, not a result.

**And the §8.2 misreading is corrected above**: the record I had labelled "last before the fatal"
is the notifier's own reaction, ~73 ms after the fact in telemetry time. The true pre-fatal
record is `pc_state = 1, pc_line = 1`, so P1 was hit there too — it was my table's label that was
wrong, not the prediction.

### 8.6 Fatal #7 — a THIRD signature, outcome A, and **NO** timeout: §6 is falsified

**Fatal #7 at 5709.425429 is a signature new to this boot** —
`lte_ml1_sm_conn_inter_freq_stm.c:712` — and it is outcome **A** (`successfully reinitialized BAM
channels and rings` at **5710.863153**). **It produced no pc-ack timeout.** The device reports
`pc_timeout_count: 5` at uptime 6034.40, and the last `pc-ack timeout` line in the ring is the
one belonging to fatal **#6** at 4810.361343 — **899 s earlier**. All five timeout lines on this
boot are of the `pc-ack timeout during resume` kind; the second writer,
`pc_state wait timeout during resume` (`:2078`), has never fired.

| event | time |
| :-- | --: |
| fatal `lte_ml1_sm_conn_inter_freq_stm.c:712` | 5709.425429 |
| `SSR before shutdown` (notifier, `pc_state=false`) | 5709.449091 (+23.7 ms) |
| `T5 rx released` (`rx_tearing_down=true`) | 5709.505999 (+80.6 ms) |
| `stopped remote processor` | 5709.625912 |
| `SSR after powerup: scheduling powerup work` / `is now up` | 5710.643429 / 5710.643566 |
| `SSR powerup: modem pc_state=1 (waited 200 ms)` | 5710.859068 |
| **outcome A** `successfully reinitialized` | **5710.863153** |
| *(no `pc-ack timeout`)* | — |

The sampler window (`pcfine_f7_boot59d9c272.txt`, format
`iq aq ps pl to rs ra td rc vt vu mra msa co|UP qm`; `<UP>` is ~73 ms early, §8.5.1):

| record | `<UP>` | `pc_irq_count` | `pc_state` | `pc_line` | **`pc_timeout_count`** | **`pc_vote_tx`** | **`pc_unvote_tx`** | **`pm_resume`** | **`pm_suspend`** |
| --: | --: | --: | --: | --: | --: | --: | --: | --: | --: |
| last pre-fatal | 5709.11 | 491 | **1** | **1** | **5** | **282** | **281** | **282** | **281** |
| notifier `pc_state=false` | 5709.39 | 491 | 0 | 1 | 5 | 282 | 281 | 282 | 281 |
| T5 `rx_tearing_down=true` | 5709.45 | 491 | 0 | 1 | 5 | 282 | 281 | 282 | 281 |
| line falls | 5709.47 | **491** | 0 | 0 | 5 | 282 | 281 | 282 | 281 |
| line rises | 5710.72 | **491** | 0 | 1 | 5 | 282 | 281 | 282 | 281 |
| work sets `pc_state` (A) | 5710.80 | **491** | **1** | 1 | **5** | 282 | 281 | 282 | 281 |
| **suspend** | 5710.84 | 491 | 1 | 1 | **5** | 282 | **282** | 282 | **282** |
| thread finally runs | 5710.88 | **492** | 0 | 0 | **5** | 282 | 282 | 282 | 282 |
| | 5711.21 | 493 | 1 | 1 | **5** | 282 | 282 | 282 | 282 |
| `CMD_OPEN` burst (co 56 → 64) | 5711.30 | 493 | 1 | 1 | **5** | 282 | 282 | 282 | 282 |
| | 5713.22 | 494 | 0 | 0 | **5** | 282 | 282 | 282 | 282 |

**P1 and P3 hold** — the last pre-fatal record is `pc_state = 1, pc_line = 1`, and
`pc_irq_count` is flat at **491** from 5709.11 through the A outcome at 5710.863153 while the
line moves 1 → 0 at 5709.47 and 0 → 1 at 5710.72. That is the fourth A case and the third
measured with the fixed key, and it is the same signature as §8.2/§8.5.3: **two wire
transitions, zero counter movement.** It is also the **first A case whose fatal is not
`a2_power.c:1189`**.

#### 8.6.1 The load-bearing column is the PM pair, not the outcome

**`pc_timeout_count` has exactly two writers, and both are inside `bam_dmux_runtime_resume()`**
— `:2069` (the 2000 ms `wait_for_completion_timeout`, logged as `pc-ack timeout during resume`)
and `:2078` (the 1000 ms `wait_event_timeout`, logged as `pc_state wait timeout during resume`).
There is no third writer anywhere in the driver. So:

> **No `bam_dmux_runtime_resume()` ⇒ no possible timeout.**

> **⚠ CORRECTION (2026-09-22) — this paragraph's headline is a WINDOW-LENGTH ARTIFACT.**
> As first written it read "in fatal #7's window **no resume ran**", and §8.7 repeated the claim.
> The window used here is 5709.4 – 5713.5, i.e. **4.1 s**. With a 25 s window a resume **does**
> run, at **5726.48 (+17.08 s)**; fatal #8 is the same (+17.01 s). The full census is §8.9. The
> load-bearing fact is therefore **not** "no resume" but **"the resume that ran did not time
> out"** — and §8.9 identifies what makes the difference. Everything else in this paragraph (the
> PM pair, the suspend at 5710.84, the change-key argument that a resume cannot be missed *inside
> a window*) is unaffected, and the change-key argument is what makes §8.9's census exact.

Within the 4.1 s window, **no resume ran**. The evidence is the vote/resume pair: `pc_vote_tx` is
**282** and `pm_resume_attempts` is **282** at 5709.11 and *still* 282 at 5711.30 — i.e. the
device was **already runtime-ACTIVE** when the fatal arrived (`vt = 282 > vu = 281`,
`mra = 282 > msa = 281`; the outstanding resume is the one from **5407.84**, 301.6 s earlier).
The only PM transition in the window is a **suspend** at 5710.84 (`msa` 281 → 282, `vu`
281 → 282), which is the autosuspend that follows the A branch's `pm_runtime_set_active()`
(`:2413`) — not a resume. The tuple's PM fields are part of the sampler's change key, so a resume
**cannot** have been missed: any `vt`/`mra` increment would have forced a record.

Now the same read-out for every timeout the corpus has, plus fatal #7:

| case | signature | outcome | PM pair at the last pre-event record | resume inside the window | Δ resume → timeout | timeout |
| :-- | :-- | :-- | :-- | :-- | --: | :-- |
| fatal #3 | `common_timer.c:390` | B | 82 / 82 — **suspended** | **yes**, 2339.52, line **low → high** | — | **no** |
| fatal #4 | `a2_power.c:1189` | A | 94 / 93 — active | **yes** (`mra` 94 → 95; ≈2976.06 by the Δ) | — | yes @2978.06 |
| fatal #5 | `a2_power.c:1189` | A | 140 / 140 — suspended | **yes**, 3884.75 | **2.04 s** | yes @3886.85 |
| fatal #6 | `a2_power.c:1189` | A | 170 / 170 — suspended | **yes**, 4808.29 | **2.01 s** | yes @4810.36 |
| orphan (§8.4) | *(no fatal)* | — | 155 / 155 — suspended | **yes**, 4184.14 | **2.18 s** | yes @4186.36 |
| **fatal #7** | **`lte_ml1_sm_conn_inter_freq_stm.c:712`** | **A** | **282 / 281 — ACTIVE** | **NONE within 4.1 s**; **+17.08 s** with a 25 s window (§8.9) | — | **NO** |

`Δ resume → timeout` is ~2000 ms in all three cases where it can be measured, which is exactly
the `msecs_to_jiffies(2000)` at `:2067`. (Fatal #4's resume time is bracketed only by the coarse
v1 capture — `mra` is 94 at 2973.58 and 95 at 2979.23 — so it is inferred from the timeout, not
observed; flagged as such.)

**The corrected condition.** The pc-ack timeout is neither an `a2_power` property (Doc 181 §6)
nor a consequence of A (this doc §6). It is a property of **a `bam_dmux_runtime_resume()` whose
wait is not completed**, and the wait is completed only by a **pc-line transition**
(`bam_dmux_pc_irq` `:1977`) or by the modem's ack (`bam_dmux_pc_ack_irq` `:1997`). A fatal
supplies the *precondition* — it pins the line — but **not the trigger**: a resume must still
happen, and it must fail to be completed. §8.4 shows a timeout with no fatal, and therefore no A.

**This is the cleanest statement the corpus supports:**

> **timeout ⟺ a `bam_dmux_runtime_resume()` runs while the pc line is already asserted.**

* A fatal makes that *likely* (the line is pinned high for the whole SSR), which is why A
  correlates 9/11 with timeouts.
* A **B** fatal makes it *unlikely*, because the line is low and the resume's own vote is the
  rising edge that completes the wait (fatal #3) — 0/17.
* **A resume is nonetheless not *sufficient*** — only **4 of 230** resumes in this boot timed
  out (1.7 %), and §8.9 shows the extra condition. The three bullets above are necessary
  conditions, not a mechanism.

**What is NOT established, and one thing that is now WITHDRAWN.** The original text read: *"Why
fatal #7 had no resume in its window is not determined … the one thing that is established is the
negative: no resume, so no timeout."* The second half is **withdrawn** — the "no resume" premise
was a window artifact (correction above), so it supports nothing. The pre-registered reading
("the resume is triggered by the SSR's own traffic/PM churn and its occurrence depends on load")
is **falsified in its mechanism**: §8.9 shows that **all five A fatals get a resume** at
+14.85 … +17.46 s, busy or idle. Load did not suppress the resume; it only changed *which* resume
was the dangerous one.

#### 8.6.2 An addendum — the lost-edge resync is not dead, and it fired 17 s later

§7.1 concluded that the RX watchdog was not the B-case rebuilder, partly on the evidence that
`pc_resync_count` was 0. **That is no longer true on this boot.** At **5726.582280**, 17.2 s
after fatal #7's A outcome, the watchdog fired the *lost-edge* path (`:1330`):

```
[ 5726.582280] bam-dmux 4080000.remoteproc:bam-dmux: bam_dmux: RX watchdog: PC line asserted while pc_state=0 (lost edge), resyncing
```

`pc_resync_count` went 0 → 1 (`rs` transitions at the record labelled 5726.67). It fired ~0.2 s
after a **resume** at 5726.48 (`vt` 282 → 283) — i.e. a vote raised the line while `pc_state` was
still 0, and the thread had not yet run. **This paragraph is what refutes §8.6.1's headline**: the
"resume at 5726.48" written here *is* fatal #7's +17.08 s resume, and it was already in the
capture when §8.6.1 said no resume ran. The two statements sat 60 lines apart and contradicted
each other; the resolution is the §8.6.1 correction and §8.9. **This is exactly the condition §8.3's second
observation predicted would exist** ("`pc_state` can disagree with the wire … while the only
resync that exists returns early on `in_teardown` and runs on a ~60 s cadence"), and it is the
first observed instance of that path in the corpus. It does not change §7.1's conclusion — the
resync was not the rebuilder for any of the retrospective boot's 17 fatals — but §7.1's
supporting sentence ("`pc_resync_count: 0`") is a statement about *that capture*, not about the
path being unreachable. **A resync path that never fires and a resync path that has never been
given the chance look identical from a counter.**

### 8.7 Fatal #8 — the §8.6 rule replicates, and the A count reaches 6

**Fatal #8 at 6611.040190 is the SAME signature as fatal #7** —
`lte_ml1_sm_conn_inter_freq_stm.c:712`, its second occurrence — and it is outcome **A**
(reinitialized at 6612.174387) **with no timeout**: `pc_timeout_count` stayed **5** across the
whole SSR.

| record | `<UP>` | `pc_irq_count` | `pc_state` | `pc_line` | `pc_timeout_count` | `pc_vote_tx` | `pc_unvote_tx` | `pm_resume` | `pm_suspend` |
| --: | --: | --: | --: | --: | --: | --: | --: | --: | --: |
| last pre-fatal | 6610.88 | 507 | **1** | **1** | 5 | **292** | **291** | **292** | **291** |
| notifier `pc_state=false` | 6611.02 | 507 | 0 | 1 | 5 | 292 | 291 | 292 | 291 |
| T5 `rx_tearing_down=true` | 6611.06 | 507 | 0 | 1 | 5 | 292 | 291 | 292 | 291 |
| line falls | 6611.10 | **507** | 0 | 0 | 5 | 292 | 291 | 292 | 291 |
| line rises | 6612.03 | **507** | 0 | 1 | 5 | 292 | 291 | 292 | 291 |
| work sets `pc_state` (**A**) | 6612.12 | **507** | **1** | 1 | 5 | 292 | 291 | 292 | 291 |
| thread runs + suspend | 6612.52 | **508** | 0 | 0 | 5 | 292 | **292** | 292 | **292** |
| | 6612.56 | 509 | 1 | 1 | 5 | 292 | 292 | 292 | 292 |

**P1 and P3 hold again** (pre-fatal `pc_state = 1, pc_line = 1`; counter flat at 507 across both
wire transitions), and the device was **ACTIVE** at the fatal (`vt = 292 > vu = 291`,
`mra = 292 > msa = 291`) with `pm_resume_attempts` flat at 292 for the 2.5 s window. **This is the
rule's first replication**: two independent A cases (§8.6, §8.7), two different fatals, same
signature, both ACTIVE-at-fatal, both timeout-free.

> **⚠ CORRECTION (2026-09-22).** The original text continued "…flat at 292 for the whole window —
> no resume, so no timeout, exactly as §8.6 predicts." **Both halves are wrong.** A resume *does*
> run, at **6628.01 (+17.01 s)** — 2.5 s windows cannot see it (the table above stops at 6612.56).
> The replication that survives is **"same signature, same outcome, and timeout-free"**; the
> explanation is §8.9, not "no resume". The same correction applies to §8.6.1's table row for
> fatal #7 and to §8.8.2's closing sentence.

A second lost-edge resync followed at **6628.743817** (`pc_resync_count` 1 → 2) — the same
pattern as §8.6.2, again ~17 s after the A outcome.

### 8.8 Fatal #9 — a FOURTH signature, and the cleanest evidence in this doc

**Fatal #9 at 6692.990034 is `a2_taskq.c:759`** — a signature that has not appeared before — and
it is outcome **A** (reinitialized at 6695.432008, and note `waited 560`, not 200) **followed by
a timeout at 6714.134806, Δ(A → timeout) = 18.702798 s.** That Δ is **16× the "near-constant"
1.118 s** of §6, and it is the observation that finishes §6 off.

#### 8.8.1 Two resumes in one window — a within-window control

The window (format `iq aq ps pl to rs ra td rc vt vu mra msa co|UP`):

| record | `<UP>` | `iq` | `aq` | `ps` | `pl` | `to` | `td` | `vt` | `vu` | `mra` | `msa` |
| --: | --: | --: | --: | --: | --: | --: | --: | --: | --: | --: | --: |
| last pre-fatal | 6690.99 | **523** | 591 | **1** | **1** | 5 | 0 | 301 | 300 | 301 | 300 |
| notifier | 6692.96 | 523 | 591 | 0 | 1 | 5 | 0 | 301 | 300 | 301 | 300 |
| T5 | 6693.01 | 523 | 591 | 0 | 1 | 5 | **1** | 301 | 300 | 301 | 300 |
| line falls | 6693.03 | **523** | 591 | 0 | 0 | 5 | 1 | 301 | 300 | 301 | 300 |
| suspend | 6694.40 | 523 | 591 | 0 | 0 | 5 | 1 | 301 | **301** | 301 | **301** |
| work sets `pc_state` (**A**) | 6695.38 | **523** | 591 | **1** | 1 | 5 | 0 | 301 | 301 | 301 | 301 |
| thread runs | 6697.39 | **524** | 591 | 0 | 0 | 5 | 1 | 301 | 301 | 301 | 301 |
| **resume #302** | 6710.46 | **525** | 591 | 1 | 1 | 5 | 0 | **302** | 301 | **302** | 301 |
| suspend | 6711.70 | 525 | 591 | 1 | 1 | 5 | 0 | 302 | **302** | 302 | **302** |
| **resume #303** | 6712.11 | **525** | **591** | 1 | 1 | 5 | 0 | **303** | 302 | **303** | 302 |
| **TIMEOUT** | 6714.14 | 525 | 591 | 1 | 1 | **6** | 0 | 303 | 302 | 303 | 302 |
| | 6715.33 | 525 | **592** | 1 | 1 | 6 | 0 | 303 | **303** | 303 | **303** |

**Two resumes ran, 1.65 s apart, and they had opposite outcomes:**

* **Resume #302** at 6710.46 — `pc_irq_count` went **524 → 525**. A completion source ran, the
  wait was satisfied, **no timeout**.
* **Resume #303** at 6712.11 — `pc_irq_count` **flat at 525** and `pc_ack_irq_count` **flat at
  591**. Neither completion source ran, so the 2000 ms wait expired → the timeout at **6714.14**,
  **resume → timeout = 2.03 s**, matching `msecs_to_jiffies(2000)` at `:2067`.

> **⚠ HONEST BOUND ON #302's EVIDENCE (2026-09-22).** The `524 → 525` transition is **not a
> separate record**: `iq` is 524 at 6697.39 and 525 at 6710.46, in the *same* record that carries
> `mra` 301 → 302. The sampler therefore **cannot order** the pc_irq run against the resume. What
> *is* observed, and is enough for the conclusion, is the **outcome**: `to` is flat at 5 through
> 6712.11, so #302's deadline (6712.46) passed without an increment, while #303's (6714.11)
> produced one recorded at 6714.14. #302 did not time out and #303 did. **The mechanism attributed
> to #302 — "a completion source ran" — is an inference from the fact that it did not time out,
> not an independent observation of the ordering.** (A timeout increments the counter *at* its
> deadline, so an increment first seen at 6714.14 cannot belong to a deadline of 6712.46 — that
> part is sound.)

**This is the strongest single measurement in this document.** It is a *within-window* control:
the fatal, the signature, the outcome (A), the pc-line state, and the device's PM state are all
**identical** for the two resumes, and they differ only in whether a completion source ran. No
cross-case comparison, no boot-to-boot confound, no signature confound. **The timeout is a
property of the individual `bam_dmux_runtime_resume()` call, and of nothing else in this table.**

It also disposes of the last alternative reading. §4.3's mechanism candidate was "the A-branch
does not `complete_all()`", which would make the timeout a property of the *outcome*. Resume #302
ran inside the same A window and completed. So the A-branch's missing `complete_all()` is not
what decides it — what decides it is whether the vote moved the line (or the modem acked).

#### 8.8.2 The "Δ = 1.109–1.255 s" observation is dead, and it explains why §6 felt solid

| fatal | A outcome | pc-ack timeout | Δ(A → timeout) | resume that timed out |
| --: | --: | --: | --: | --: |
| 5 | 3885.730123 | 3886.848426 | **1.118303 s** | 3884.75 — **0.98 s *before* the A** |
| 6 | 4809.243040 | 4810.361343 | **1.118303 s** | 4808.29 — **0.95 s *before* the A** |
| **9** | 6695.432008 | 6714.134806 | **18.702798 s** | 6712.11 — **16.7 s *after* the A** |

The Δ was never a property of the A outcome. **It is the delay from the resume**, and the resume
happens whenever the runtime-PM state machine next needs the device — which in fatals #5 and #6
was ~1 s *before* the A line and in fatal #9 was 15 s *after* it. The near-constancy that made §6
look mechanistic (and the 1 µs agreement between #5 and #6, §8.5.3) was a coincidence of *when
the resumes happened*, not a property of the A path.

**This is the retrospective explanation of the whole error chain.** Doc 181 saw `a2_power` → A →
timeout; this doc saw the same data and moved the attribution one link down the chain. Both were
reading the *resume's* signature through two layers of proxy: the resume's timing is set by the
runtime-PM state machine, and in that corpus it happened to land just before the A line every
time. **A perfect correlation with a stable latency looked exactly like a mechanism.** One fatal
with a 15 s resume delay broke it.

**And the corrected table, with #8 and #9:**

| | timeout | no timeout |
| :-- | --: | --: |
| **A** (fatal-linked) | **9** | **2** |
| **B** | 0 | 17 |

Fisher exact one-sided p = **0.00000796**. The correlation is still strong — A really does make a
timeout likely — but the two counter-examples were described here as "both A cases with **no
resume in the window**". **That description is withdrawn**: both do resume, at +17.08 s and
+17.01 s (§8.9). The variable that actually decides it is §8.9's, not the absence of a resume.

### 8.9 What triggers a resume — and the extra condition that makes one time out

§8.6–§8.8 read the resume out of **five hand-picked windows**. That is the wrong instrument for
this question, and it produced the window-length artifact corrected above. The right one is a
**census of every resume in the capture**, which the sampler supports exactly because
`pm_resume_attempts` (`mra`) is part of its change key: any `mra` increment forces a record.

**First, the source — the SSR path never triggers a resume.** `bam_dmux_ssr_powerup_work_func()`
calls only `pm_runtime_set_active()` (`:2413`) and `bam_dmux_ssr_teardown_work_func()` only
`pm_runtime_set_suspended()` (`:2311`); neither invokes the resume callback. Every actual resume
comes from outside the SSR path:

| site | caller | trigger |
| :-- | :-- | :-- |
| `:501` | `bam_dmux_send_cmd()` | `CMD_OPEN` / `CMD_CLOSE` — netdev open/stop |
| `:559` | `bam_dmux_netdev_open()` | userspace bringing `wwan0` up |
| `:667` | `bam_dmux_netdev_start_xmit()` | an AP TX packet |
| `:739` | `bam_dmux_tx_wakeup_work()` | draining deferred TX |

All four are **traffic or userspace** events. So the resume is not an SSR mechanism at all: the
fatal supplies the *precondition* (it pins the pc line), and an unrelated, later packet supplies
the *trigger*.

**The census** (`resume_trigger.py`, capture `pcfine_f9_boot59d9c272.txt`):

| | |
| :-- | --: |
| capture span | 3157.2 – 6978.2 s (3821 s) |
| resumes (`mra++`) | **230** — one per **16.6 s** |
| suspends (`msa++`) | 230 |
| timeouts (`to++`) | **4** = **1.7 %** of resumes |

230 resume/suspend pairs in 3821 s is an ordinary **autosuspend duty cycle**: idle → autosuspend,
a packet arrives → resume. Nothing about the SSR is needed to explain the rate.

All four timeouts are the **2000 ms** wait. The ring names them, and **none** is the second site:

```
[ 3886.848426] bam-dmux …: modem pc-ack timeout during resume      <- :2069
[ 4186.364155] bam-dmux …: modem pc-ack timeout during resume
[ 4810.361343] bam-dmux …: modem pc-ack timeout during resume
```

`pc_state wait timeout during resume` (`:2078`) **never fires on this boot**. So the second wait is
ruled out as a confound, and every timeout is the pc-ack wait at `:2069`.

**The four, with the state at the resume:**

| resume `<UP>` | `pl` | `ps` | `td` | `rx`/s (−10 s) | s since last suspend | completion event | delay |
| --: | --: | --: | --: | --: | --: | :-- | --: |
| 3884.750 (fatal #5) | **0** | **0** | **1** | 0.7 | 1.220 | NONE | +2.040 s |
| 4184.140 (orphan §8.4) | 1 | 1 | 0 | **116.3** | **0.080** | NONE | +2.180 s |
| 4808.290 (fatal #6) | **0** | **0** | **1** | 0.0 | 1.200 | NONE | +2.010 s |
| 6712.110 (fatal #9, #303) | 1 | 1 | 0 | 0.0 | **0.410** | NONE | +2.030 s |

Every delay is ~2000 ms, i.e. `msecs_to_jiffies(2000)` at `:2067`. **None of the four had a
completion event** (a `pc_irq` or `pc-ack irq` increment) at or within 3 s; **all 226 clean
resumes had one** — 226/226 vs 0/4. That 2×2 is *not* a discovery, though: `to` increments
exactly when the wait expires, so it restates the code's own logic. It is a **consistency check on
the instrument**, and it passes, which is what licenses the rest.

**★ The correction: every A fatal DOES resume, ~15–17 s later — and that resume never times out.**

| fatal | resumes in a 25 s window (`*` = timed out) |
| :-- | :-- |
| #5 | +0.15 s\*, **+14.85 s** |
| #6 | +0.19 s\*, **+16.32 s** |
| #7 | **+17.08 s** |
| #8 | **+17.01 s** |
| #9 | **+17.46 s**, +19.11 s\*, +23.17 s |

**All five A fatals have a resume in the +14.85 … +17.46 s band, and 0 of 5 timed out.** That is
the recovery resume (it lands 0.2–0.7 s before the lost-edge resync of §8.6.2/§8.7, and it is
what §8.6.2 had already recorded at 5726.48 without noticing it contradicted §8.6.1). So the
correct reading of fatal #7 and fatal #8 is **not** "no resume, so no timeout" but **"the resume
that ran was not the dangerous kind"**.

**What distinguishes the dangerous resumes? Time since the last suspend.** All four timeouts
resumed within **1.22 s** of a suspend; **0 of the 137 resumes later than 1.25 s after a suspend
timed out** (95 % upper bound ≈ 2.2 % by the rule of three). Inside the band it is 4/93 = 4.3 %:

| `since_susp` | timeouts | clean |
| :-- | --: | --: |
| ≤ 1.25 s | **4 / 4** | 89 / 226 |
| > 1.25 s | **0 / 4** | 137 / 226 |

Fisher exact one-sided **p = 0.0257**. **The 1.25 s cut is data-chosen, so this is exploratory,
not a pre-registered result** — it is a lead, and §13 pre-registers the confirmatory run.

`pl = 0` is the only other near-miss, and it is **not** a substitute: 2/4 of the timeouts had
`pl = 0` but so do 11 clean resumes, and the split is not clean in either direction —

| | `pl = 0` | `pl = 1` |
| :-- | --: | --: |
| `since_susp ≤ 1.25 s` | **2 T / 2 C** | **2 T / 87 C** |
| `since_susp > 1.25 s` | 0 T / 9 C | 0 T / 128 C |

Both fast cells contain timeouts, and both slow cells contain none. **The band, not `pl`, is the
separator.**

Two mechanisms are visible in the four, and they are not the same:

1. **Teardown-window** (fatal #5 @3884.750, fatal #6 @4808.290): `td = 1, ps = 0, pl = 0` — the
   resume runs while the modem is still being reset, so the pc line is **low** and there is
   nothing to ack. These are the early resumes, +0.15/+0.19 s after the fatal.
2. **Suspend/resume race** (orphan @4184.140, fatal #9 #303 @6712.110): `td = 0, ps = 1, pl = 1` —
   the modem is **healthy**, and the resume simply follows a suspend too closely (80 ms, 410 ms).
   The orphan is the cleanest case in the corpus: at 4184.06 the device autosuspends
   (`vu` 154 → 155, `msa` 154 → 155); `rx_callbacks` advances 62172 → 62183 across that suspend,
   i.e. **a packet arrived**; the resume follows at 4184.14, 80 ms later; the vote-off/vote-on
   pair is too fast for the modem to ack, and the wait expires at 4186.32.

**The pre-registered prediction is FALSIFIED in its mechanism.** §11.9 predicted that *"an A
fatal arriving in a BUSY window should be LESS likely to time out than one arriving in an IDLE
window"*, on the reasoning that a busy device is already runtime-ACTIVE and needs no resume. The
census kills the reasoning: **all five A fatals resume**, busy or idle. And at the resume level
the busy cut points the **wrong way** — the single busy resume that timed out is the orphan
(1/4 vs 0/226, p = 0.017, i.e. *busy* is associated with the timeout, not protected from it).
The prediction's *conclusion* (idle fatals #5/#6/#9 timed out, busy #7/#8 did not) holds in this
sample, but for a different reason and at n = 5 it is not evidence of anything.

**What is not established.** *Why* a resume within ~1.25 s of a suspend fails to be acked is not
determined. The driver-side reading — the vote-off and the vote-on reach the RPM too close
together to produce two distinct modem responses — is consistent with the two healthy cases (80 ms
and 410 ms) but is **not** measured; the modem's side of the handshake is not observable from the
AP. The 1.25 s cut is also unexplained: 80 ms and 410 ms look like a race, 1.20 s does not, and
the two 1.2 s cases are the teardown-window class, which has a different explanation. The band may
be covering two mechanisms rather than one.

---

## 9. H3 — the refined hypothesis, pre-registered

The hypothesis is not the naive "the line is asserted before the fatal" (§7.3 kills that as a
*sufficient* condition, since the line is low before a B fatal). It is about **whether the
assert edge is available to the handler after the teardown**:

> **H3.** At an **A** fatal the pc line was **already asserted at the moment of the fatal** and
> **stayed** asserted across the SSR. The teardown then sets `pc_state = false` and clears the
> remote SMSM bit, but because the line never falls there is **no rising edge afterwards**, so
> `bam_dmux_pc_irq()` never rebuilds. Only the powerup work's level-poll sees the high line, and
> it rebuilds → **A**. At a **B** fatal the line was **low** at the fatal, so the modem's
> post-reload assert is a real rising edge, the handler consumes it, and the work finds the
> channels already active → **B**.

**Predictions, registered 2026-09-22 before any A case exists on boot `59d9c272`:**

* **P1** — in the sampler's last record before an **A** fatal, `pc_state = 1` and `pc_line = 1`.
* **P2** — in the sampler's last record before a **B** fatal, `pc_state = 0` and `pc_line = 0`.
  *(already satisfied by fatal #3, §8)*
* **P3** — `pc_irq_count` does **not** increment between the teardown and the A outcome line.
* **P4** — in a B case `pc_irq_count` increments exactly once at the modem's assert.
  *(already satisfied by fatal #3, §8)*

**Falsifiers.**

* An **A** case whose last pre-fatal record shows `pc_line = 0` **kills H3**. (That would leave
  "the edge was delivered but `bam_dmux_power_on()` failed in the handler" as the surviving
  reading — separable only by whether `pc_irq_count` moved.)
* An **A** case with `pc_irq_count` incremented in the window kills the *lost-edge* mechanism
  specifically, while leaving the A/B split itself intact.

**Required n.** The signature mix is 4 `a2_power` : 13 `common_timer`, so A cases arrive at
roughly **1 in 4 fatals ≈ 1 per hour** at the 903.675 s beat. The split itself is already
established at n = 17 (§5); H3 is a *within-A* claim, so **n = 2 A cases is the minimum that can
falsify it, and n = 3–4 is needed before P1/P3 can be called supported.**

**Status: P1–P4 confirmed at n = 6 live-boot A cases** (§8.1 fatal #4, §8.2 fatal #5, §8.5.3
fatal #6, §8.6 fatal #7, §8.7 fatal #8, §8.8 fatal #9). Every A case to arise after registration
confirmed all four predictions, and the last three are the important ones: they have **three
different signatures** between them (`a2_power.c:1189`, `lte_ml1_sm_conn_inter_freq_stm.c:712`,
`a2_taskq.c:759`), so the A mechanism is not signature-bound — the one generalisation the first
three could not make. **H3's mechanism wording is superseded by H4 (§8.3)**: `pc_irq_count`
counts thread runs, not edges, so the measured fact is "the handler did not run in the window"
rather than "no edge reached the handler". **H4 has since gained an independent, non-fatal
confirmation (§8.4)** — a resume whose line did not transition timed out exactly as H4 predicts,
with no fatal involved — **two A cases with no resume and therefore no timeout (§8.6, §8.7) as
the converse control, and §8.8.1's within-window pair as the decisive one.**

**§9's falsifier list is untouched by fatals #7–#9.** Neither falsifier fired: P1 held
(`pc_line = 1` pre-fatal in all three) and P3 held (`pc_irq_count` flat at 491 / 507 / 523 across
each A outcome). Their contribution is to §6, not to H3.

**The B side is still the weak one**: it rests on the v1 capture's 7.269 s lead (§8), which was
taken with a key that could not see `ps`/`pl` moves between counter changes, and **six
consecutive fatals have now been A rather than the B that would have closed it**. The sampler is
still running; fatal #10 is due ≈ 7519 s.

---

## 10. A correction to my own earlier reading

Earlier in this session I wrote that H2 was "falsified" on the strength of the 186 all-low
quiesce lines. That was an overreach for the reason given in §7.3: the watchdog does not cover
the window in which the claim applies, so the observation was **silent on H2**, not against it.
The corrected statement is that H2 is **untested by the console** and is now being tested by the
sampler as H3. The error was made by treating a counter's *last reported value* as its value at
an arbitrary later time.

---

## 11. What is NOT established

1. **The A mechanism is confirmed at n = 6, but the masking is inferred, not directly
   observed.** §8.1, §8.2, §8.5.3, §8.6, §8.7 and §8.8 confirm P1–P4 on all six live-boot A
   cases, and the last three carry **three different signatures** between them, so the mechanism
   is not signature-bound.
   The claim that the *line is masked* under `IRQF_ONESHOT` comes from reading the registration
   (§8.3) plus the flat counter while the wire moved — the kernel's default primary handler is
   not instrumented, so "the edge was consumed by the mask" and "the edge was never latched by
   the irqchip" are **not separated**. Both produce the same observable: the thread does not run.
   What the outcome depends on — that the interrupt path does not rebuild `rx` — is measured
   either way. Note also the **asymmetry of the test**: the pc line is high a large fraction of
   the time (the sampler shows it toggling every few seconds to ~100 s), so **a high pre-fatal
   line is weak evidence on its own; P3 — that `pc_irq_count` does not move — is the load-bearing
   prediction**, and it held on all six cases.
2. **The sampler's `<UP>` label carries a ~73 ms systematic error (§8.5.1).** Measured on 10
   co-observed pairs of three independent event types: 0.0624 – 0.0806 s, mean 0.0729 s. So
   sampler timestamps may **not** be compared to dmesg timestamps at better than ~±60 ms, and
   any "the last record before X" claim is ambiguous at that scale — which is exactly how §8.2's
   table came to be misread. Relative ordering *within* the sampler is unaffected, which is why
   §8.4's analysis (which never crosses the two clocks) stands as written. A future version
   should read `/proc/uptime` **after** the telemetry, or read it twice and bracket.
3. **The pc-ack timeout's *antecedent* is established; what makes the antecedent dangerous is a
   lead, not a finding.** §8.6 shows the timeout cannot occur without a `bam_dmux_runtime_resume()`
   (both writers of `pc_timeout_count` are inside it) and that every measured timeout follows its
   resume by ~2000 ms — the `msecs_to_jiffies(2000)` at `:2067` (2.04 / 2.01 / 2.18 / **2.03** s).
   §8.9 adds that **`pc_state wait timeout` (`:2078`) never fires**, so the second wait is ruled
   out, and that `pc_ack_completion` has **three** completers — the pc-irq handler (`:1977`), the
   pc-ack irq handler (`:1997`, the only one that bumps `pc_ack_irq_count`), and the watchdog
   resync (`:1361`) — so `aq` alone is **not** a valid completion instrument (47 clean resumes had
   no `aq` increment). What is **not** identified is *which* of the three failed: §4.3's missing
   `complete_all()` in the A-branch is consistent, but so is Doc 181 §6's alternative ("the modem
   did not ack"). **§8.8.1 narrows it but does not close it** — resume #302 completed while #303
   did not, with the fatal, signature, outcome and PM state identical, and §8.8.1 now carries an
   explicit bound: the `524 → 525` transition is co-recorded with the resume, so the *ordering* is
   inferred from #302's non-timeout, not observed. What §8.8.1 *does* establish is that the
   deciding variable is a property of the individual resume call, not of the fatal, the signature
   or the outcome. §8.9's `since_susp` band is the best candidate for that property and is
   **exploratory**.
4. **No causal claim, in either direction.** A and B both end with the channels rebuilt and the
   data plane restored. The A/B split is a **symptom-level** discriminator between fatal
   signatures. Nothing here says the A/B outcome causes the fatal, or that the fatal is caused by
   the pc line — and §8.6–§8.8 remove the last place where a causal reading of the timeout could
   have survived, since A now demonstrably occurs without one.
5. **Outcome C is unobserved, not excluded.** It did not occur in **26** fatal SSRs across two
   boots (17 retrospective + 9 live). At the observed rate that bounds it below ~1 in 9 (rule of
   three, 95 %) — it is *rare*, not impossible, and the ~77 s retry budget remains untested.
6. **The signature mix is still not explained, and fatals #7–#9 make it worse.** Why
   `a2_power.c:1189` leaves the pc line asserted while `common_timer.c:390` does not was the open
   question; **three** further signatures (`lte_ml1_sm_conn_inter_freq_stm.c:712` ×2,
   `a2_taskq.c:759`) also leave it asserted, so the property is not unique to `a2_power` and the
   mix is at least **4-way**. What the A-class signatures have in common — and why
   `common_timer.c:390` alone is different — is not known.
7. **`waited 560` in row 18 is unexplained** (§5.1).
8. **The B case has never been measured with the fixed-key sampler.** Every A case now has a
   ~0.9–2.1 s lead; the single B case rests on a 7.269 s lead from a capture that could not see
   `ps`/`pl` moves between counter changes (§8). A `common_timer` → B with the fixed key is the
   one remaining measurement that would close the asymmetry, and it has not arrived in **six**
   consecutive A fatals.
9. **~~What triggers the resume is still not established.~~ RESOLVED by §8.9, and the
   pre-registered busy/idle test is FALSIFIED in its mechanism.** The census answers the question
   and corrects this item's premise: **all five A fatals resume** (at +14.85 … +17.46 s, 0/5
   timing out), so "fatal #7's and fatal #8's windows contained none" was a window-length
   artifact, and the count "two produced no resume" was wrong. The trigger is not an SSR
   mechanism at all — the four call sites are `send_cmd`/`netdev_open`/`start_xmit`/
   `tx_wakeup_work`, i.e. traffic and userspace. The busy/idle prediction's *reasoning* ("busy ⇒
   already ACTIVE ⇒ no resume") is dead, and at the resume level busy points the wrong way
   (1/4 vs 0/226). What replaces it is **`since_susp ≤ 1.25 s`**, which is 4/4 vs 0/137 but is a
   **data-chosen cut and therefore exploratory** — it needs the confirmatory run §13 pre-registers
   before it may be called a finding.
10. **The lost-edge resync has now fired twice, and what it reacts to is still not fully read
    out.** §8.6.2 records the first at 5726.582280 and §8.7 the second at 6628.743817
    (`pc_resync_count` 0 → 1 → 2), both ~17 s after an A outcome and shortly after a resume. The
    sequence is consistent with the masked-line condition §8.3 describes, and the **repetition at
    the same ~17 s offset after two different fatals makes it look systematic rather than
    incidental** — but it is two occurrences, both outside their fatal's window, and neither was
    pre-registered. The ~17 s offset is a hypothesis to test, not a finding.
    **§8.9 sharpens this**: the resync follows a resume that §8.9 now shows is present after
    **all five** A fatals at +14.85 … +17.46 s, which makes "the ~17 s event" a *resume*, with the
    resync trailing it by 0.2–0.7 s. The offset is therefore a property of whatever schedules the
    recovery resume, not of the resync.
11. **The resume census is one boot, and its central number is a single-digit count.** §8.9's
    230 resumes and 4 timeouts are from **one** capture (boot `59d9c272`, 3821 s); the 4/4 vs
    0/137 split rests on **four** events, and the 1.25 s cut was chosen *after* seeing them. Two
    of the four are explained by a different mechanism (the teardown window) than the other two
    (the suspend race), so the band may be covering two things rather than one. A 4-event
    separation with a data-chosen threshold is a **lead**, and the honest bound is the negative
    half: **0 of 137 late resumes timed out, i.e. the rate outside the band is < 2.2 % (95 %,
    rule of three)**. Nothing here says the band *causes* the timeout.

---

## 12. Why this matters

* **The lead is closed.** The 13 `common_timer` fatals are **B**, not C: the modem asserts
  promptly, the interrupt rebuilds the channels, and recovery is healthy. There is no hidden
  class of 77 s recovery failures. The ~77 s retry budget added by patch 814 has still never
  been exercised by a real fatal.
* **Doc 181 §6 is re-attributed — and this doc's own re-attribution is then withdrawn.**
  The pc-ack timeout is **not** an `a2_power` property (Doc 181 §6) and **not** a consequence of
  the A path (this doc §6): fatal #7 is A with no timeout, and the orphan timeout of §8.4 has no
  fatal at all. The timeout's real antecedent is a **`bam_dmux_runtime_resume()` whose wait is
  never completed**, and the wait is completed only by a pc-line transition. Anyone re-opening
  the 250 → 2000 ms question should condition on **whether a resume ran and whether the line
  transitioned**, not on the fatal signature and not on A/B. **This is a two-step correction of
  the same error**: both earlier readings took a perfect correlation in a corpus where the two
  signatures never crossed over and read it as a mechanism. Fatals #7–#9 are the crossover, and
  fatal #9 also removes the supporting argument — **the Δ(A → timeout) is 1.118 s, 1.118 s and
  18.703 s, because it is the *resume's* delay, not A's** (§8.8.2).
* **The timeout is a *transition* defect, not a fatal defect (§8.4 + §8.9).** The same boot
  produced a timeout with **no fatal at all** (the §8.4 orphan: an 80 ms suspend/resume pair
  whose line never moved) and A fatals with **no timeout** (fatal #7 and #8, whose only resume is
  the ordinary ~17 s recovery resume). So the underlying condition — the completion needs a
  **line transition or an ack**, and the vote is a no-op when the line is already high — is a
  **runtime-PM** property that the fatal can *force* but does not *create*. That matters for
  anyone tempted to read "A → timeout" as "the timeout is a symptom of the fatal": it is not.
  **§8.9 puts a number on it: 4 of 230 resumes time out (1.7 %).**
* **`a2_power.c:1189` is a structurally distinct failure, but it is not alone.** It is **a**
  signature whose fatal leaves the pc line **already asserted** — and §8.6–§8.8 show it is **not
  the only one**, because `lte_ml1_sm_conn_inter_freq_stm.c:712` (×2) and `a2_taskq.c:759` do too.
  §8.3 explains *why* that matters: the pc irq is **threaded with `IRQF_ONESHOT` and a NULL
  primary handler**, so an asserted line means a queued thread and a **masked line** — the
  interrupt-driven recovery path is structurally unavailable and only the powerup work's
  level-poll saves the data plane. That is still the first **mechanistic** difference found
  between the A and B classes; what fatals #7–#9 remove is the claim that the difference is a
  property of one signature. **They also invert the frequency picture**: `a2_power.c:1189` is no
  longer the A class's most common member, and `common_timer.c:390` is the *only* B signature
  seen — so the interesting question is now what makes `common_timer` different, not what makes
  `a2_power` special.
* **The masking has a cost beyond the fatal.** §8.3's observation that `pc_state` can disagree
  with the wire for ~1 s before a fatal, while the only resync that exists returns early on
  `in_teardown` and runs on a ~60 s cadence, is a latent robustness gap in the port — not a
  cause of the fatal, and not claimed as one.
* **A latent robustness point about the driver.** The port's SSR recovery depends on a
  *level* poll (`bam_dmux_ssr_powerup_work_func`) only as a fallback; the primary path is
  edge-driven. §8.1 shows a case where the edge path is structurally unavailable and the
  fallback is what recovers the data plane. That is a point in favour of patch 814's retry
  design, and it is worth remembering that the retry has still never been needed (§11.4).
* **The instrument lessons — seven of this doc's corrections are reasoning errors, and all seven
  were cheap to check.** §3 and §7.3 are cases of *looking in the wrong place for the right
  quantity*: the console was in pstore, and the watchdog is a quiesce instrument. §8.3 is a case
  of *reading a counter's name instead of its definition*: `pc_irq_count` counts thread runs, so
  a flat counter is not a statement about edges. §8.4 is a case of *writing a causal gloss for an
  anomaly without checking it against the baseline*: "the ack path is downstream of the pc irq"
  explained both timeout windows and is refuted by ordinary operation. §8.5.1 is a case of
  *trusting a timestamp without calibrating the instrument that produced it*: the sampler's
  `<UP>` is read before the telemetry it labels, so it is ~73 ms early, and that error is what
  made §8.2's table look like it contradicted P1. **§8.6 is the same error as §6, one level up:
  *reading a perfect correlation as a mechanism*.** `a2_power` → A → timeout was perfect in the
  retrospective boot, so both Doc 181 and this doc attributed the timeout to whichever variable
  came first in their own table. A perfect 2×2 in a corpus where the two predictors never cross
  over cannot separate them, and **the fix is not more statistics on the same corpus — it is one
  case where they do cross**. Fatals #7–#9 were that case, and it took *three more signatures* to
  produce them. **§8.8.2 adds the matching lesson about latency**: the "Δ = 1.118 s" that made
  §6's attribution feel mechanistic was itself an artefact — the resume's timing, not A's — and
  it took one fatal with a 15 s resume delay to expose it. **A stable latency is not evidence of
  a causal link; it is a second correlation, and it can be even more misleading than the first
  because it *feels* like a mechanism.** **★ §8.9 adds the seventh, and it is the cheapest of
  all: *a negative result is only as wide as the window you looked in*.** "No resume in the
  window" was true of a 2.5 s window and false of a 25 s one, and the two statements sat in the
  same document 60 lines apart (§8.6.1 vs §8.6.2) without anyone noticing. **The window is part
  of the claim, and a claim about absence must state its window — and then be re-run at a window
  chosen for the mechanism's expected timescale, not for convenience.** The fix here was not a
  better reading of the same records; it was a **census** — count every event in the capture
  instead of inspecting five hand-picked intervals. **When the question is "how often", do not
  answer it from windows.** The first two lessons are in the corpus's existing trap
  list; the rest were added to it (memory: `feedback_read_the_definition_not_the_name.md` §7–§8,
  and the coverage/alignment rule in `feedback_scoring_live_captures.md`).

---

## 13. Next actions

1. **The sampler is still running on boot `59d9c272`; fatal #10 is due ≈ 7519 s** (the device was
   at 6964.89 s and `pc_timeout_count` 6 when this was written). Pull `/tmp/pcfine.txt` again
   before any reboot; it holds §8.4's orphan timeout, §8.5's calibration pairs, §8.6–§8.8's
   fatals #7–#9 **and §8.9's 230-resume census**. H3 is settled at n = 6 live-boot A cases, so
   further A fatals are **confirmatory only** — what is still missing is a `common_timer` → **B**
   case measured with the fixed key (§11.8).
2. **★ §8.9's `since_susp` band is the new highest-value question, and it must be CONFIRMED, not
   re-tuned.** §8.6–§8.8 reduced the timeout to "a resume whose wait is not completed"; §8.9 then
   measured *which* resumes those are — 4 of 230 — and the only clean separator found is the time
   since the previous suspend: **4/4 timeouts at ≤ 1.22 s, 0/137 above 1.25 s**. That cut was
   chosen **from this boot's data**, so it is exploratory and must be tested against a fixed
   threshold. Pre-registration, before the next capture is scored:

   * **P-R1.** With the cut **fixed at 1.25 s**, a resume with `since_susp ≤ 1.25 s` is more
     likely to time out than one with `since_susp > 1.25 s`. Score every resume in the capture,
     not just the fatal windows. The informative half is the **negative**: at ~1 resume / 16.6 s,
     **300 late resumes needs ~5000 s (~83 min)**, which is what it takes to bound the
     outside-band rate below 1 % (rule of three). One more boot of this length should supply it.
   * **P-R2.** Report the two classes **separately**, because §8.9's four events are not one
     mechanism: `pl = 0` / `td = 1` (the teardown window — the line is low, so nothing can ack)
     versus `pl = 1` / `td = 0` (the suspend race — the modem is healthy). A band that spans both
     is a coincidence until they are separated. **P-R2 is the more likely of the two to be
     falsified**, and it should be scored first.
   * **P-R3.** The **~15–17 s recovery resume** is present after all five A fatals here
     (+14.85 … +17.46 s) and never times out (0/5). Test it on the retrospective boot's 17
     fatals and on every new A fatal: **it should be present in ≥ 90 % of them**. If it is, then
     "the ~17 s event" of §8.6.2/§8.7 is a **resume**, and the resync trailing it is downstream.

   The prediction this **replaces** — the busy/idle cut of §11.9 — is already falsified in its
   mechanism and should not be scored again as if it were live.
3. **Fix the sampler's clock before the next long run** (§8.5.1): read `/proc/uptime` *after* the
   telemetry file, or read it both before and after and record the bracket. That turns a ~73 ms
   systematic error into a ~22 ms bounded one and removes the ambiguity in "the last record
   before X". The v3 script is written (`scratch/pcfine.sh`, which brackets the read with a third
   field) but **the device is still running v2** — deploying it requires restarting the sampler,
   which loses the in-flight capture, so it should be done at the next natural break.
4. **The one thing that would sharpen H4**: instrument the *primary* handler. The kernel's
   default primary handler is not traceable, so "the edge was consumed by the `IRQF_ONESHOT`
   mask" and "the irqchip never latched the edge" remain unseparated (§11.1). A `dev_err` in a
   custom primary handler would distinguish them — but that requires a module rebuild, so it is
   only worth doing if the distinction ever becomes load-bearing. It currently is not: both
   readings predict "the thread does not run", which is what the A/B outcome depends on.
5. Pull the full `dmesg` to the host **before** any reboot (memory RULE 6), and read
   `/sys/fs/pstore/` before concluding a ring is gone (§3).
6. Not started, and untouched by this doc: the ~903.675 s always-on clock and its latched
   offset; the userspace bearer rebuild (fatal #2 on this boot cost **27 s**, of which an
   **18 s** ModemManager gap at 13:30:15 → 13:30:33 is avoidable, against a 0.70 s modem SSR);
   and the AP-side boot-firmware (`hyp`/`tz`) swap.
