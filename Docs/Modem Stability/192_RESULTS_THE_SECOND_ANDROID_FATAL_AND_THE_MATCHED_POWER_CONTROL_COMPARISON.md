# 192 — RESULTS: the second Android fatal, and the matched power-control comparison

**Date:** 2026-09-23
**Device:** HMUF02-v5 ("hmu05"), stock Android `msm8916_32_512-userdebug 4.4.4 KTU84P`, kernel 3.10.28,
serial `c2b9103c` — and the HMU05 OpenWrt 6.12.94 figures it is compared against.
**Status:** RESULTS. Scores the predictions frozen in **Doc 191**. **Doc 191 is not edited** (its own
§header forbids it); every correction is recorded here.
**Companions:** `191_PREREGISTRATION_THE_SECOND_ANDROID_FATAL_AND_THE_MATCHED_POWER_CONTROL_COMPARISON.md`
(the frozen predictions), `188_PREREGISTRATION_REPRODUCE_THE_900S_FATAL_FROM_THE_ANDROID_SIDE.md`
(the A0 arm and the scorer `score_fatal2.py`), `190_ANDROID_DOES_FATAL_AND_THE_RPM_RATE_DIFFERENTIAL_IS_FALSIFIED.md`
(fatal #1).

---

## 1. SOP compliance

| SOP step | Status |
| :-- | :-- |
| **Pre-registration before measurement** | **Yes** — Doc 191 §5/§6 were written and committed before fatal #2 and before the OpenWrt half. Nothing in §5/§6 of Doc 191 was edited after the event. |
| **Scored by a tool validated BEFORE the event** | **Yes** — `score_fatal2.py`, validated against 7 fixtures and regressed on fatal #1 (Doc 191 §8). It reproduced fatal #1's recorded values exactly in this run (§3.3). |
| **Comparative protocol** | **Yes** — no baseband hypothesis was reopened; no modem firmware was read, patched, or written. Every quantity below is AP-side, or AP↔modem interaction, or a modem *counter* read through an AP debugfs node. |
| **Measurement frozen before scoring** | **Yes** — the live sampler CSV, both fatal-edge dmesg dumps and the supervisor log were pulled to `evidence/191_matched_power_control/a0_fatal2/` before any statistic was computed. |
| **Coverage asserted before any rate** | **Yes** — §3.1. The scorer prints it first and refuses a gapped window. |
| **Instrument verified before use** | **Yes** — and this is where the round's main correction came from: §6.1 reads each counter's **definition** in the tracked kernel tree. The pre-registered Android↔OpenWrt mapping was **not** what its label said. |
| **Control verified to have TAKEN, not merely issued** | **Yes** — P-A13d (§6.2) is the control, and it is scored *before* P-A13a–c. It confirms the RPM instrument was working; therefore the null on P-A13b is a result and not a broken instrument. |
| **Denominator justified** | **Yes** — the fatal's modem uptime is taken from the dmesg timestamps, not the sampler's (which reads up to one 5.24 s interval late). Both are printed; the offset is +1.20 s (fatal #1) and +0.19 s (fatal #2). |
| **Negative controls** | **Yes** — §5's MTTF revision is bracketed by its n=2 Poisson interval rather than quoted as a point; §6.4 reports a ≥99× within-platform spread on the counter it is scoring. |
| **Skipped / not applicable** | No dual-firmware byte comparison — this doc makes no claim about the baseband image. No credential guessing against the vendor login. |

**Errors caught while writing this doc — all three were caught by reading a definition or a header,
not by reading a result:**

1. **A raw-arithmetic error in the first pass at the RPM ring delta.** The hand subtraction gave
   `0x271D7080 − 0x27101860 = 0xD580` (1 708 records, 14.2 rec/s). The true difference is **`0xD5820`**
   = 874 528 B = 27 329 records = **227.5 rec/s** — the first pass dropped a hex digit. The wrong value
   was 16× low and would have read as *"the AP is heavily loaded, so the whole comparison is
   confounded"*. Re-deriving it in code (§8) is what caught it.
2. **Doc 191 §6.2's Android label is wrong.** It maps *"AP power-collapse votes"* → Android's
   `a2 ack out cnt`. The tracked driver shows `a2 ack out cnt` is the AP's **ACK** toggle
   (`toggle_apps_ack()` → `SMSM_APPS_STATE & SMSM_A2_POWER_CONTROL_ACK`), and the AP's actual vote
   (`power_vote()` → `SMSM_APPS_STATE & SMSM_A2_POWER_CONTROL`) is **not counted by any exposed
   counter**. See §6.1 — this changes what P-A13a can mean, and it is reported rather than silently
   substituted.
3. **The harness and the doc disagree on a prediction's name.** `score_fatal2.py` prints the
   latched-offset test as **"P-A7b"**; Doc 191 §5 names it **P-A8**. Same test, two names. Mapped
   explicitly in §4.2 so a future reader does not think a prediction was skipped.

---

## 2. Headline

**Fatal #2 arrived while this session was running — 3 h 10 min into the modem's second life, not the
~5 h the n=1 MTTF implied.** It was caught by the pre-registered instrument and scored by the
pre-validated harness. Three results:

1. **★ The 903.675 s clock does not govern Android's fatals.** Fatal #1's residual was −111.1 s;
   fatal #2's is **−345.9 s**. P-A8 (latched offset, ±60 s) and P-A9 (±150 s of a beat) are **both
   FALSIFIED**. Fatal #1's fit was the coincidence P-A9 itself pre-warned about (p ≈ 0.11 at n = 2).
2. **★ Android's two fatals land in two different classes.** Fatal #1 `ps_icmp6_msg.c:938` is in
   **no** OpenWrt fatal in the corpus; fatal #2 `lte_ml1_common_dump.c:217` is in OpenWrt's
   **dominant family** (`lte_ml1_*`, **153 of the 415** `fatal error received` lines in the
   non-quarantined corpus). So "Android fatals at the same sites as OpenWrt" is half-supported at
   n = 2 — and the rate question is unchanged.
3. **★ The power-control comparison is not scorable as pre-registered, and the reason is the
   instrument, not the platforms.** The positive control passes (§6.2, 1.0147× vs the established
   1.012×), but the OpenWrt vote counter turns out to be a **traffic meter with a ≥99×
   within-platform spread** (§6.1), and the Android window is a **known degraded regime** with a
   wedged data path (§6.6). P-A13b is FALSIFIED (∞ ratio), P-A13a is FALSIFIED on all three committed
   OpenWrt window series, and P-A13c is UNSCOREABLE. **None of those three is a platform statement.**

**The "~35×" survives as a round number but its point estimate moves to ~25×** (§5), with a 95 %
interval of ~7×–205×. With n = 2 that revision is inside the noise.

---

## 3. Fatal #2

### 3.1 Coverage (checked before any rate)

`evidence/191_matched_power_control/a0_fatal2/samples.csv` — the **live** A0 sampler, pulled while it
was still running:

| property | value |
| :-- | :-- |
| rows | 7 517 |
| coverage | `ap_uptime` **5 298.6 → 44 688.7 s** = **10.94 h** |
| gaps > 12 s | **none** — coverage CONTINUOUS, scorable |
| observed mean interval | **5.241 s** (vs 5.24 nominal) |
| actual/expected rows | 0.9998 |
| `supervisor.log` | **one line** (start at up 42 206.92) — no STALE restart fired all window |

The supervisor was deployed mid-session (Doc 191 §8's ssh→`/init` pattern) and left the healthy
sampler untouched: the sampler's own `ap_uptime` column advanced across it, and the log carries no
`SAMPLER STALE` line.

### 3.2 The event

Full dmesg census on the live device (`dmesg | grep`), complete and unwrapped:

```
[    6.610988] pil-q6v5-mss 4080000.qcom,mss: modem: Brought out of reset      <- boot
[31524.147021] SMSM: Modem SMSM state changed to SMSM_RESET.
[31524.147075] Fatal error on the modem.
[31524.147089] modem subsystem failure reason: ps_icmp6_msg.c:938:.            <- FATAL #1
[31524.147099] subsys-restart: ... Restart sequence requested for modem, restart_level = RELATED.
[31526.419651] pil-q6v5-mss 4080000.qcom,mss: modem: Brought out of reset
[42928.256422] SMSM: Modem SMSM state changed to SMSM_RESET.
[42928.256472] Fatal error on the modem.
[42928.256487] modem subsystem failure reason: lte_ml1_common_dump.c:217:.    <- FATAL #2
[42928.256497] subsys-restart: ... Restart sequence requested for modem, restart_level = RELATED.
[42930.791823] pil-q6v5-mss 4080000.qcom,mss: modem: Brought out of reset
```

| quantity | fatal #1 | **fatal #2** |
| :-- | :-- | :-- |
| detected (dmesg, AP) | `[31524.147021]` | **`[42928.256422]`** |
| wall clock | 2026-09-23T04:17:35Z | **2026-09-23T07:27:38Z** |
| signature | `ps_icmp6_msg.c:938:` | **`lte_ml1_common_dump.c:217:`** |
| **modem uptime at the fatal** | 31 517.536 s | **11 401.837 s** = 3 h 10 min |
| CSV minus dmesg | +1.20 s | +0.19 s |
| detection → `subsys2=ONLINE` | ≈ 5.5 s | **≈ 5.4 s** |
| beat vs `P = 903.675206 s` | beat 35, residual **−111.1 s** | beat 13, residual **−345.9 s** |
| beat vs `P = 902.353 s` | beat 35, residual −64.8 s | beat 13, residual −328.8 s |

The sampler's own edge rows confirm it caught the transition, not a later state:

```
1790148453,42923.19,1,2,ONLINE,11396.77,...   <- last healthy sample
1790148458,42928.45,2,2,OFFLINE,11402.03,...  <- the fatal: subsys2 OFFLINE, modem_up 11402.03
1790148464,42933.85,2,3,ONLINE,3.06,...       <- modem back
```

### 3.3 The harness reproduced fatal #1 before scoring fatal #2

The scorer, run once over the whole window, printed fatal #1 as modem_up **31 517.536 s**,
signature `ps_icmp6_msg.c:938`, beat 35 residual **−111.1 s** — i.e. **exactly** the values Doc 191
§2 recorded from the earlier partial window. That is the regression that licenses using it on fatal #2.

**Precursor check.** The 20 dmesg lines immediately before the fatal #2 edge contain no modem
message: they are two unrelated `sshd (…) undefined instruction: pc=000d0f0x` blocks at
`[42159.2]` and `[42206.7]` — the operator's ssh/telnet additions hitting an illegal instruction on
this 32-bit ARMv7 kernel, ~720 s before the fatal and with no modem interaction. There is **no**
`a2_power`/RPM/bam_dmux precursor in the AP log, consistent with fatal #1 and with the corpus's
finding that the AP log is blind to the run-up.

---

## 4. Scoring the fatal-#2 pre-registration

Every prediction below was frozen in Doc 191 §5. Verdicts are stated as CONFIRMED / FALSIFIED /
UNSCOREABLE, and a falsification is not softened.

### 4.1 P-A7 — signature class (the pre-committed rule)

The rule, quoted: *Class **S** = a site in OpenWrt's corpus family (`a2_power.c:*`, `lte_ml1_*`,
`a2_task.c:*`, `mmoc.c:*`). Class **D** = anything else (incl. a `ps_*` data-path site). The class is
assigned by this rule and may not be re-classified after the fact.*

| fatal | site | matches | **class** |
| :-- | :-- | :-- | :-- |
| #1 | `ps_icmp6_msg.c:938` | no family prefix | **D** |
| #2 | `lte_ml1_common_dump.c:217` | `lte_ml1_*` | **S** |

**Scored as written: fatal #2 = S.** The rule is mechanical and is applied mechanically.

**But the supporting evidence is weaker than the classification, and that must be said.** A repo-wide
search for `lte_ml1_common_dump` finds it as an **OpenWrt** fatal only in two places:

* `_QUARANTINE/85_DEFINITIVE_15MIN_CRASH_FOUR_EXPERIMENT_PROOF…md` — *"Crash #1:
  `lte_ml1_common_dump.c:213`"* — a **quarantined** document (Doc 146's trust index applies), and
* `packages/qcom-carrier-autocfg/files/qcom-carrier-autocfg.sh:262` — an operational warning comment
  in the **shipped** OpenWrt package: *"raw AT+CFUN=0/1 triggers a fatal baseband assertion
  (`lte_ml1_common_dump.c:213`)"*.

There is **no captured OpenWrt dmesg in the archive** carrying `lte_ml1_common_dump`. So:

> **Fatal #2 is in OpenWrt's dominant `lte_ml1_*` family** — 153 of the 415 `fatal error received`
> lines in the non-quarantined corpus (`lte_ml1_sleepmgr_stm` 84, `lte_ml1_common_timer` 55,
> `lte_ml1_sm_conn_inter_freq_stm` 8, `lte_ml1_coex` 4, `lte_ml1_rfmgr_trm` 2; against `a2_power`
> 124, `a2_task` 15, `mmoc` 9). That line count includes the same dmesg quoted in several docs, so it
> is a corpus-size measure, not a distinct-event census. **But "the same site" is asserted at FAMILY
> level only.** The exact file `lte_ml1_common_dump.c` is attested on OpenWrt by a quarantined doc and
> a package comment — **not by a captured dmesg**.

**P-A7-CONSEQUENCE.** The pre-registration gave a per-class branch. Fatal #2 = **S** ⇒ *"Android fatals
at the same sites as OpenWrt, just ~35× less often ⇒ the rate model stands and the search stays on
the AP↔modem/RPM handshake."* Fatal #1 = **D** ⇒ *"Android has a different failure mode."*

**Both branches fired, one per fatal.** At n = 2 the honest reading is neither of the pre-registered
sentence-final conclusions: **Android produces both an OpenWrt-family site and a site with no OpenWrt
counterpart.** That is consistent with a shared baseband plus a rate difference (the rate model
stands) *and* leaves fatal #1's `ps_icmp6_msg.c` class unexplained. It does **not** license
"Android has a different failure mode" as a general statement, and it does not license "same sites"
either. The frame that survives is the rate frame; the D-class site is an open item.

### 4.2 P-A8 — the latched offset (**FALSIFIED**)

Doc 191 §5: fatal #2's residual against `P = 903.675206 s` should be within **±60 s** of fatal #1's
−111.1 s, i.e. in **[−171.1, −51.1]**.

| fatal | residual | |
| :-- | --: | :-- |
| #1 | **−111.1 s** | `ps_icmp6_msg.c:938:` |
| #2 | **−345.9 s** | `lte_ml1_common_dump.c:217:` |
| | **spread 234.8 s** | tolerance 60 s ⇒ **FALSIFIED** |

*(The harness prints this as "P-A7b" — same test, Doc 191 §5's name is P-A8. See §1 error 3.)*

Doc 191 attached a reading rule to this prediction: *"P-A7b is falsifiable by a legitimate `a2_power`
offset jump (Doc 179), so a FALSIFIED verdict must be read against the signature."* Neither Android
fatal is an `a2_power` site, so no such jump is available to excuse the spread — the falsification
stands as a falsification.

### 4.3 P-A9 — on the clock (**FALSIFIED**)

Doc 191 §5: *both* fatals land within **±150 s** of an integer multiple of `P = 903.675206 s`.

* fatal #1: residual −111.1 s ⇒ |111.1| ≤ 150 ⇒ passes
* fatal #2: residual **−345.9 s** ⇒ |345.9| > 150 ⇒ **FAILS**

⇒ **P-A9 FALSIFIED.** The same test against the secondary period `P = 902.353 s` also fails
(−328.8 s). The same test against **AP** uptime rather than modem uptime also fails (−448.2 s), so
this is not an anchoring artefact — see §4.6.

**Consequence.** Doc 191 §5 pre-warned that *"a random landing satisfies P-A9 with p ≈ 0.11 at
n = 2"*, and that P-A8 was the sharp test. Both are now falsified. **The 903.675 s free-running clock
is not the timing law of Android's modem fatals.** Fatal #1's −111.1 s fit was a single draw.

This is a negative result about **Android's** fatals only. It does not falsify the clock's role on
OpenWrt, where it was derived from five consecutive `common_timer` intervals and where the fatal
recurs every ~900 s by construction of the measurement.

### 4.4 P-A10 — no daemon death precedes the fatal (**CONFIRMED**)

Over the 1 800 s before fatal #2, all five modem daemons are present in every sample:

| daemon | min | max | verdict |
| :-- | --: | --: | :-- |
| `rild` | 1 | 1 | OK |
| `qmuxd` | 1 | 1 | OK |
| `netmgrd` | 1 | 1 | OK |
| `rmt_storage` | 1 | 1 | OK |
| `time_daemon` | 1 | 1 | OK |

⇒ **P-A10 CONFIRMED**, and identical to fatal #1. The fatal is not preceded by a userspace daemon
death on either Android fatal. (Note: `sshd` *did* die at `[42159.2]` and `[42206.7]`, ~720 s before —
but `sshd` is not one of the five, it is an operator addition, and its failure is an AP-side illegal
instruction, not a modem daemon event.)

### 4.5 P-A11 — no admin-UI poller (**CONFIRMED**)

`himi_conn` is **0 in every sample** of the 1 800 s before fatal #2 (`min=0 max=0`). ⇒ **P-A11
CONFIRMED.** The §13.3 confounder is absent, as it was for fatal #1.

### 4.6 P-A12 — the pre-fatal `state2` ramp (descriptive only; **did not reproduce**)

Doc 191 §5 marked this descriptive and pre-committed *"Do not cite it as a precursor."* Scored anyway,
because non-reproduction is information:

| fatal | pre-fatal 900 s | preceding 900 s | ratio |
| :-- | --: | --: | --: |
| #1 | 18.13 /s | 1.67 /s | **10.87×** |
| #2 | 63.68 /s | 68.22 /s | **0.93×** |

The 10.87× "ramp" **does not reproduce**. P-A12's own caveat named the reason in advance (the
reference window drives the statistic), and fatal #2 removes the last reason to cite it. **The
pre-fatal `state2` ramp is dead as a precursor.**

Incidentally, `state2` ran at **63.68 /s** in the 900 s before fatal #2 — against 18.13 /s before
fatal #1 and 12.09 /s for the window mean. That is an AP-side cpuidle quantity and is recorded, not
explained.

### 4.7 Scorecard

| id | prediction | verdict |
| :-- | :-- | :-- |
| **P-A7** | signature class, by the frozen rule | fatal #2 = **S**; fatal #1 = D *(classification; the falsifier is the rule itself)* |
| **P-A7-CONSEQUENCE** | S ⇒ same sites / D ⇒ different mode | **both branches fired (one per fatal)** — rate frame stands; the D-class site is an open item |
| **P-A8** | latched offset within ±60 s | **FALSIFIED** (spread 234.8 s) |
| **P-A9** | both fatals within ±150 s of a beat | **FALSIFIED** (fatal #2 −345.9 s) |
| **P-A10** | no daemon death | **CONFIRMED** |
| **P-A11** | no admin-UI poller | **CONFIRMED** |
| **P-A12** | pre-fatal `state2` ramp *(descriptive)* | **did not reproduce** (0.93× vs 10.87×) |

**Three of five scored predictions failed, and that is the value of the exercise.** Every failure is
a claim that would otherwise have been inherited.

---

## 5. What fatal #2 does to the "~35×"

Doc 190 measured Android at ~1 fatal / 31 500 s from a **single** interval. Doc 191 §7 explicitly said
fatal #2 **cannot test a rate** (n = 1 event) — so no rate claim is made from it. What it *can* do is
replace the n = 1 point estimate with n = 2:

| interval | span |
| :-- | --: |
| boot → fatal #1 | 31 517.536 s |
| bring-up → fatal #2 | 11 401.837 s |
| bring-up → last sample (censored, `ap_uptime` 44 688.68) | 1 757.89 s |
| **total modem exposure** | **44 677.26 s** |
| **events** | **2** |
| **MTTF (MLE)** | **22 339 s = 6.21 h** |

Against the standing OpenWrt figure of ~900 s, that is **~24.8×**, not ~35×. But:

* **the 95 % Poisson interval for 2 events in 44 677 s is MTTF ∈ [6 184, 184 464] s ⇒ ~6.9×–205×.**
  "35×" sits comfortably inside it. **The revision is within the noise of n = 2 and should not be
  reported as a change in the differential.**
* The two intervals differ by **2.8×** and the two fatals are in **different signature classes**
  (§4.1). Pooling them as one homogeneous process is an assumption, not a measurement.

**And the pre-registered expectation was wrong by 3.6×.** Doc 191 §1 said *"the modem is ~11 000 s
into its second life against a ~31 500 s MTTF, so the event is ~5 h out"*. It arrived at
**11 402 s = 3.2 h**. An MTTF is not a timer; the interval to the next fatal was the *shorter* of the
two observed intervals, not the mean.

**Net:** the honest headline stays "Android ≈ 1 fatal per ~20 000–30 000 s vs OpenWrt ≈ 1 per ~900 s,
a differential of order 10–30×". The corpus's "~35×" is a defensible round number inside the
interval; it is not a measured value at n = 2.

---

## 6. The matched AP↔modem power-control comparison (P-A13a–d)

### 6.1 ★ The instruments, corrected from the tracked source

Doc 191 §6.2 pre-registered this mapping:

| quantity | Android | OpenWrt |
| :-- | :-- | :-- |
| AP power-collapse votes | `a2 ack out cnt` | `pc_vote_tx_count` |

**Reading the definitions shows the Android label is wrong, and the two platforms' handshakes run in
opposite directions.** From the tracked trees:

**Android** (`GitIgnore/android_kernel_zte_msm8916/drivers/soc/qcom/bam_dmux.c`):

| counter | incremented in | what it actually is |
| :-- | :-- | :-- |
| `a2 pwr cntl in` | `bam_dmux_smsm_cb()` `:2436` — the `SMSM_MODEM_STATE` callback for `SMSM_A2_POWER_CONTROL` | the **modem** announcing a power-control state change |
| `a2 ack out` | `toggle_apps_ack()` `:2427` — writes `SMSM_APPS_STATE & SMSM_A2_POWER_CONTROL_ACK` | the **AP's ACK** of that change |
| `a2 ack in` | `bam_dmux_smsm_ack_cb()` `:2471` | the **modem's ACK** (it completes `ul_wakeup_ack_completion`) |

`toggle_apps_ack()` is called from `bam_init()` `:2375`, `reconnect_to_bam()` `:1990` and
`disconnect_to_bam()` `:2055` — i.e. **once per BAM connect/disconnect transition**. So `a2 ack out`
*is* a per-power-cycle AP-side counter, but it is an **ack**, not a vote. The AP's actual vote is:

```c
static void power_vote(int vote)                       /* :1672 */
{
        bam_dmux_uplink_vote = vote;
        if (vote) bam_ops->smsm_change_state_ptr(SMSM_APPS_STATE, 0, SMSM_A2_POWER_CONTROL);
        else      bam_ops->smsm_change_state_ptr(SMSM_APPS_STATE, SMSM_A2_POWER_CONTROL, 0);
}
```

called from `ul_powerdown()` `:1704`, `kickoff_ul_wakeup` `:1922` and the SSR cleanup `:2182`.
**`power_vote()` increments no counter** — Android does not expose the AP's vote at all.

**OpenWrt** (`scratch/orig_kernel/drivers/net/wwan/qcom_bam_dmux.c`, counters from patch 808):

| counter | incremented in | what it actually is |
| :-- | :-- | :-- |
| `pc_vote_tx_count` | `bam_dmux_pc_vote(dmux, true)` ← **`bam_dmux_runtime_resume()` `:2079`** | the **AP asserting** the PC bit |
| `pc_unvote_tx_count` | `bam_dmux_pc_vote(dmux, false)` ← **`bam_dmux_runtime_suspend()` `:2052`** | the **AP clearing** it |
| `pc_ack_irq_count` | the pc irq handler | the modem's ack |

**A source-independent cross-check of what `pc_vote_tx_count` counts.** In
`scratch/soak814/final/run8_final.txt` (an OpenWrt soak, module `eca269f10a685a83…`, 5 fatals /
5 SSRs, last heartbeat `uptime 5032.50 s`) the final telemetry dump reads:

```
pc_vote_tx_count: 661      pc_unvote_tx_count: 661
pm_suspend_attempts: 661   pm_resume_attempts: 661
pc_irq_count: 1278         pc_timeout_count: 9
```

**All four are equal**, and `pc_irq_count ≈ 2 × 661` (the assert + deassert edge per cycle). So one
"vote" is exactly one **PM runtime suspend/resume cycle** — which is precisely what the source reading
above predicts, and it is why the rate tracks traffic.

**Consequences, and they are load-bearing:**

1. **The A2 handshake is AP-driven on OpenWrt and modem-driven on Android.** OpenWrt's AP votes on
   runtime resume/suspend; Android's modem asserts and the AP acks. This is a genuine structural
   difference between the downstream 3.10 SMSM driver and the upstream 6.12 SMEM-state driver, and it
   is a **new candidate axis** — it was invisible while the counters were compared by name.
2. **`pc_vote_tx_count` is a traffic meter, not a power-control meter.** It counts *runtime resumes*,
   which the AP performs when it wants to send. Its committed range bears this out (§6.4):
   **0.0019 – 0.1883 /s, a 99.5× spread.**
3. Therefore a **±2× tolerance cannot discriminate a platform effect on this counter.** Doc 191 §6.3
   pre-warned that the test was underpowered below ~2×; the measurement shows it is underpowered
   below ~100×.

The comparison is still run and scored exactly as pre-registered, with the corrected reading stated
alongside. **No counter is substituted for another.**

### 6.2 P-A13d — the positive control (**CONFIRMED** — the method is not broken)

Scored **first**, because a broken method would make P-A13a–c VOID.

| quantity | value |
| :-- | --: |
| Android RPM record rate, measured | **227.5 rec/s** (27 329 records / 120.13 s) |
| Android idle reference (Doc 190 §4) | 226.8 rec/s → **1.0031×** |
| OpenWrt idle reference (Doc 150) | 224.2 rec/s → **1.0147×** |
| established ratio (Doc 190 §4) | 1.012× → off by **+0.27 %** |

⇒ **P-A13d CONFIRMED** (tolerance 2×). The RPM instrument was working and the AP was in its idle
regime, so the window is not AP-load-suppressed. **P-A13a–c are therefore not VOID.**

*(This is also where the §1 arithmetic error was caught: the first hand pass gave 14.2 rec/s, which
would have read as "the AP was 16× busier than idle, so nothing here is comparable". The correct
value 227.5 rec/s says the opposite.)*

### 6.3 P-A13b — modem PC-collapse rate within 2× (**FALSIFIED**)

| platform | source | rate |
| :-- | :-- | --: |
| **Android** (pre-registered window, 120.13 s) | `MPSS numshutdowns` 11 918 → 11 918, **Δ 0** | **0.0000 /s** |
| OpenWrt capture 1 (602 s) | `rms_out_capture1.txt` | 1.6793 /s |
| OpenWrt capture 3 (602 s) | `rms_out_capture3.txt` | 1.5547 /s |

⇒ ratios are **∞** ⇒ **P-A13b FALSIFIED** on both.

The counter is the same field on both platforms — Doc 175 §2 states it explicitly: *"Upstream prints
`Shutdown count:`; Android prints `numshutdowns:` — same field (`struct rpm_master_stats.num_shutdowns`),
different label."* So this is not a name mismatch.

**And the window is confirmed flat, not sampled flat.** The 600 s extension samples every 30 s over
**571.5 s** (20 samples, `ap_uptime` 44 611.5 → 45 183.0) and holds **`0x2e8e` at every one of the 19
samples that parsed** — **Δ = 0, 0.0000 /s** — while the AP's own handshake counters advance steadily
(`a2 ack out` +38 = **0.0665 /s**, reproducing the pre-registered window's 0.0666 /s) and the RPM
record rate reads **232.5 rec/s**, i.e. the AP was still idle. So the modem genuinely did not
power-collapse once in 11.6 minutes.

**Three facts that must travel with this number:**

1. **★ The counter does NOT reset on a modem SSR.** At `ap_uptime` 44 146.60 — only **1 215.8 s**
   after fatal #2's bring-up — it already read 11 918. A reset at the bring-up would require
   **9.8 collapses/s** in that interval, which contradicts the measured 0.00 /s. Therefore the RPM
   master stats survive a modem restart and the counter is **cumulative from AP boot**. (A full device
   *reboot* does reset it — Doc 174 §7.) That is a new instrument fact.
2. **Android's own boot-average is 11 918 / 44 652 s = 0.267 /s** — **5.2× below** Doc 174's early-boot
   Android baselines (1.3831 and 1.5137 /s) on the *same device and the same firmware*. So Android's
   within-platform spread already covers most of the OpenWrt gap, and this measurement does not
   isolate a platform effect.
3. **The window sits in a known degraded regime** — see §6.6. `rmnet1` rx is effectively wedged.

### 6.4 P-A13a — vote rate within 2× (**FALSIFIED** on all committed sources; **not interpretable**)

Android, pre-registered window: `a2 ack out cnt` 6 057 → 6 065, Δ 8 over 120.13 s = **0.0666 /s**.
(`a2 ack in cnt` and `a2 pwr cntl in` each also advanced exactly 8 — the three counters move together,
which is itself the signature of §6.1's reading: one modem transition, one AP ack, one modem ack.)

Every committed OpenWrt `pc_vote_tx_count` series:

| source | window | votes | rate |
| :-- | --: | --: | --: |
| `phase5_soak_1800s_results_deadlock_run.csv` | 547.6 → 1 721.3 s (1 174 s) | +221 | **0.1883 /s** |
| `phase5_soak_1800s_results.csv` | 1 140.2 → 1 198.0 s (58 s) | +8 | **0.1384 /s** |
| `evidence/111_userspace_subtraction/usub_samples.csv` | 14 626.6 → 17 267.9 s (2 641 s) | +5 | **0.0019 /s** |
| *(boot-average, NOT a window rate)* `evidence/148_fatal_periodicity/a2_cadence_20260920.txt` | 0 → ~3 322 s | 202 | 0.0608 /s |
| *(boot-average, NOT a window rate)* `evidence/148_fatal_periodicity/live_capture_20260920.txt` | 0 → 3 535.1 s | 224 | 0.0634 /s |

Against Android's 0.0666 /s: **2.827×**, **2.078×**, **0.028×** ⇒ **FALSIFIED on all three window rates**.

**Note the two boot-averages: 0.0608 and 0.0634 /s, i.e. 1.05–1.09× Android's 0.0666 /s — inside the
tolerance.** They are a *different statistic* (cumulative ÷ uptime, including boot transients) and are
not the pre-registered comparison, but the contrast is the point: the same counter agrees with Android
to ~7 % as a boot average and disagrees by 2.8× as a window rate. **The quantity is set by the traffic
in the window, not by the platform.** (A third boot-average, `scratch/soak814/final/run8_final.txt`,
reads 661 / 5 033 s = 0.1313 /s — but that run had **5 fatals and 5 SSRs**, so it is an SSR-churn
window, not an idle one, and is recorded rather than tabulated.)

**★ But the within-platform spread is 0.0019–0.1883 /s = 99.5×**, which is ~50× the tolerance. So:

> **P-A13a's verdict is a statement about the instrument, not about the platforms.** A counter whose
> own range spans 99.5× cannot resolve a 2× cross-platform difference. The pre-registered test is
> **UNSCOREABLE in substance** while it is FALSIFIED in form, and the two must not be confused.

Two further provenance caveats, recorded so they are not silently inherited:

* The two `phase5_*` CSVs are **orphaned** — no document in the corpus references them, the
  `phase5` spec is in `_QUARANTINE/`, and they date from 2026-09-09. The `usub` file is properly
  archived under `evidence/111_userspace_subtraction/`.
* **None of the three is a matched window.** They differ from the Android window in build, date,
  uptime, and traffic condition. Doc 191 §6.3 required *"one comparable window per platform, with the
  AP load recorded"* — that condition is **not met** by any committed OpenWrt series. **P-A13a cannot
  be properly scored until a matched OpenWrt window is taken** (§9).

### 6.5 P-A13c — the ratio `AP_votes / modem_collapses` (**UNSCOREABLE**)

| platform | votes /s | collapses /s | ratio |
| :-- | --: | --: | --: |
| Android | 0.0666 | **0.0000** | **UNDEFINED** |
| OpenWrt (deadlock_run / capture 3) | 0.1883 | 1.5547 | 0.1211 |
| OpenWrt (usub / capture 3) | 0.0019 | 1.5547 | 0.0012 |

The Android denominator is zero, so the ratio is undefined ⇒ **P-A13c UNSCOREABLE**, and the OpenWrt
values span **0.0012–0.1211 = 100×** for the same reason as §6.4.

**P-A13c was the one genuinely new quantity in Doc 191 §6** — *"a large difference would mean the
AP's share of the modem's PC cycles is itself a differential, which is a new axis."* It is not
measured. The AP's share on OpenWrt is **~0.1–12 %** depending on the window; on Android it cannot be
computed. **The axis is neither opened nor closed.**

### 6.6 Confounds — recorded, as Doc 191 §6.3 required

| confound | value | why it matters |
| :-- | :-- | :-- |
| **★ Wedged data path** | `rmnet1` **rx advanced 72 B in 571.5 s** (9 637 B held for 120.3 s, then 9 709 B held for 451.2 s) = **0.13 B/s**; tx trickling 9.6 B/s | This is **Doc 190 §6's regime**: `dumpsys` reports `CONNECTED/CONNECTED` with full addresses/routes/DNS while the bearer carries nothing. The modem is being held out of power collapse by a **broken bearer**, not by a healthy idle condition. This is the single most important reason P-A13b's ∞ is not a platform finding. **And it survives an SSR + bearer rebuild:** Doc 190 §6 measured `rx=429 730` at `ap_uptime` ~35 600, while this window reads **9 637** — `rx_bytes` is zeroed on `net_device` creation, so the interface was recreated at least once in between (fatal #2's SSR is a certain such event: its dmesg carries `rmnet_config_notify_cb(): Kernel is trying to unregister rmnet0`). The fresh instance is stuck too. |
| **Active LTE data call** | `rmnet1` UP/LOWER_UP, default route via `10.158.130.33`, `mServiceState=…home JIO 4G…LTE`, `mDataConnectionState=2` | The pre-registered Android window is *not* an idle modem. |
| **AP load** | `load1` 3.17–3.54, but the RPM record rate (227.5 rec/s in the window, 232.5 rec/s over the extension) equals the idle reference | The load is **D-state**, consistent with the wedged path, not CPU load. The RPM instrument is therefore *not* suppressed — which is what P-A13d confirms. |
| **Uptime / post-SSR** | window at `ap_uptime` 44 146–44 266 s, **1 218–1 338 s after fatal #2**; modem instance only ~1 200 s old | A freshly-restarted modem is not a steady-state subject. |
| **Android boot-average rate** | 0.267 /s vs Doc 174's 1.38–1.51 /s on the same device | Within-platform spread ≥ 5×. |

---

## 7. What this does NOT establish

1. **No platform difference in the modem PC-collapse rate.** P-A13b is falsified as pre-registered, but
   the Android window is a degraded regime and Android's own rate spans 0.00–1.51 /s. **The 35×
   differential is not explained, and this measurement does not narrow it.**
2. **No new rate.** Fatal #2 is n = 1 event (Doc 191 §7). The MTTF revision in §5 is a point estimate
   with a ~30× interval.
3. **No claim that the clock model is wrong on OpenWrt.** §4.3 falsifies it for **Android's** fatals.
4. **No claim that Android fatals "at the same sites".** One of two fatals does (§4.1), at family
   level, on weak OpenWrt attestation.
5. **P-A13a/P-A13c are not evidence of anything about the platforms** — §6.4/§6.5.
6. **Nothing about the baseband.** No modem firmware was read, patched, or written; every quantity
   here is AP-side or a counter read through an AP debugfs node.
7. **No causal link between the power-control counters and the fatal.** The fatal's proximate cause
   remains the modem's `rpm.sync` stall (`project_900s_fatal_anatomy` §3.4); nothing here touches it.

---

## 8. Artifacts

`Docs/Modem Stability/evidence/191_matched_power_control/`

| file | what |
| :-- | :-- |
| `android_raw_T0T1.txt` | **the pre-registered 120 s window** — T0/T1 for `numshutdowns`, the three A2 counters, and the RPM ring counter |
| `android_pc_series_600s.txt` | the 600 s extension: 20 samples at 30 s, also carrying `mpss_active`, `rmnet1` rx/tx bytes and `load1` |
| `score_pa13.py` | replays **every** P-A13 number from the frozen files (and re-derives the ring delta in code — the §1 error 1 fix) |
| `score_pa13_output.txt` | its output |
| `a0_fatal2/samples.csv` | the **live** A0 window, 7 517 rows, up 5 298.6 → 44 688.7 s, continuous — the window that caught fatal #2 |
| `a0_fatal2/dumps/dmesg_fatal_2_uptime_42928.45.txt` | the fatal-edge dmesg dump (the reason string is consumed on read) |
| `a0_fatal2/dumps/dmesg_fatal_1_uptime_31525.35.txt` | fatal #1's edge dump, for the regression |
| `a0_fatal2/dumps/samples_at_fatal_{1,2}.csv` | the sampler's own copy at each fatal edge |
| `a0_fatal2/score_output.txt` | `score_fatal2.py` over the live window — fatal #1 regression + fatal #2 + P-A8 |
| `a0_fatal2/supervisor.log` | one line — the supervisor never had to restart the sampler |

`scratch/android_capture/pc_series.sh` — the device-side series sampler.

**Reproduce:**

```sh
# the fatal-#2 scorecard (P-A7 .. P-A12)
python3 "Docs/Modem Stability/evidence/188_android_side_repro/score_fatal2.py" \
    "Docs/Modem Stability/evidence/191_matched_power_control/a0_fatal2/samples.csv" \
    --dumps "Docs/Modem Stability/evidence/191_matched_power_control/a0_fatal2/dumps" --expect 2

# the power-control comparison (P-A13a .. P-A13d)
python3 "Docs/Modem Stability/evidence/191_matched_power_control/score_pa13.py"
```

**Read the counter definitions before quoting any A2 number:**
`GitIgnore/android_kernel_zte_msm8916/drivers/soc/qcom/bam_dmux.c:1672` (`power_vote`),
`:2412` (`toggle_apps_ack`), `:2430` (`bam_dmux_smsm_cb`), `:2468` (`bam_dmux_smsm_ack_cb`);
and `msm89xx/patches/808-bam-dmux-stats.patch:174` (`pc_vote_tx_count` ← `bam_dmux_runtime_resume`).

---

## 9. Next actions, in order of value

1. **A matched OpenWrt `pc_vote_tx_count` window** — the only way to score P-A13a/P-A13c properly.
   Requires the reflash. Per Doc 174 **O6**, the traffic condition must be **recorded and verified from
   the traffic log**, and per **O5** at least four windows are needed, not one. Without this, §6.4/§6.5
   stay unscored.
2. **An Android window in a HEALTHY regime.** P-A13b's ∞ was taken with `rmnet1` wedged. The decisive
   version is a window where rx is advancing, so the modem is free to collapse. This is cheap
   (a counter read every 30 s) and it is the difference between "Android's modem does not collapse"
   and "this Android modem, in this degraded state, does not collapse".
3. **Fatal #3 for n = 3.** The A0 window is continuous and instrumented; the interval to fatal #3 is
   the cheapest reduction of the §5 interval.
4. **The new axis from §6.1** — the A2 handshake direction (AP-driven on OpenWrt, modem-driven on
   Android). Worth a targeted comparison of the ack path, where the 250 ms vs 2000 ms timeout
   differential is already established.
5. **(Deferred, unchanged)** P-A6 with the corrected by-name cpuidle mapping; the `rpmring`
   idle-vs-4×-CPU-load A/B/A′; P-B1 (the interconnect consumer); the ICMPv6 test; the OpenWrt soak
   (WS1/WS3).

**SOP compliance.** Steps observed: the pre-registration rule (Doc 191 §5/§6 frozen and unedited; this
doc records every correction instead of rewriting them); the comparative protocol (no baseband
hypothesis reopened, no firmware read/patched/written, every quantity labelled with its file);
measurement hygiene rules 1, 4, 5, 9 and 10 (capture frozen and hashed before scoring; coverage
asserted first; the fatal's denominator taken from the more precise instrument with the discrepancy
printed; the instrument's *shape* verified by a positive control before the null was believed;
pre-registered `n` respected — fatal #2 is reported as a mechanism test at n = 1 and no rate claim is
made from it); and the deployment-verification rule (the scorer was validated against fatal #1's
recorded values inside this very run, before fatal #2 was scored). Steps deliberately not taken: no
modem firmware was read, patched or written; no credential guessing against the vendor login; the
SOP's dual-firmware byte-comparison step is not applicable — this doc makes no claim about the
baseband image.
