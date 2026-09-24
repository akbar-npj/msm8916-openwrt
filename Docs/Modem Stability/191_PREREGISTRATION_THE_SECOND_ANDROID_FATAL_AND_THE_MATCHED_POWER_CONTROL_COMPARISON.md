# 191 — PRE-REGISTRATION: the second Android modem fatal, and a matched AP↔modem power-control comparison

**Date:** 2026-09-23
**Status:** pre-registration — written **before** fatal #2 and **before** the OpenWrt half of
the power-control comparison is measured. Must not be edited after either is scored.
**Companions:** `188_PREREGISTRATION_REPRODUCE_THE_900S_FATAL_FROM_THE_ANDROID_SIDE.md`
(the A0 arm this extends), `evidence/188_android_side_repro/` (the instrument, the A0
window, and the validated scorer `score_fatal2.py`).

---

## 1. SOP compliance

- **Comparative protocol (the standing rule).** No baseband hypothesis is reopened and no
  modem firmware is read, patched, or written. The corpus already proves the baseband is
  byte-identical across the two stacks (`project_modem_firmware_identical_proof`, all 21
  segments hashed), so every quantity below is AP-side or AP↔modem-interaction.
- **Pre-registration before measurement.** Every prediction in §5 and §6 is frozen now.
  The fatal-#2 predictions are written before the event (the modem is ~11 000 s into its
  second life against a ~31 500 s MTTF, so the event is ~5 h out); the power-control
  prediction is written before the OpenWrt half is measured. This is the Doc 167 / 183 /
  184 discipline.
- **Measurement hygiene.** The instrument's coverage is checked and reported *before* any
  statistic (§7); the scorer is validated against a known event before the live one (§8);
  and each counter is named with its source, because the two platforms expose the same
  quantity under different names and **two different counters exist for the same concept**
  (`sysfs cpuidle usage` vs `/d/lpm_stats success` — Doc 188 §12.4).
- **Errors caught while writing this doc:**
  1. **The archive number I first reached for was misattributed.** A grep for `numshutdowns`
     returned `1.310 / 1.523 / 1.711 /s` from
     `evidence/170_two_fixes_for_the_smd_poll_uaf/V_*.txt`, in a file whose header reads
     *"Captured with: dmesg / the ssr_ledger / rx_telemetry, over ssh"* — i.e. an **OpenWrt**
     capture. Its §4 explicitly labels those figures *"MEASURED on Android"*. Reading the
     header instead of the section would have put OpenWrt's own numbers on Android's side of
     the comparison. **Every number below is therefore either measured by me on the live
     device or labelled with the file it came from.**
  2. **Doc 146 §166 compares two different quantities.** It reads
     *"`pc_vote_tx_count` ≈ 0.24/s on OpenWrt vs Android's measured 918 collapses / 3240 s =
     0.283/s"* — OpenWrt's **vote** rate against Android's **collapse** rate. Those are not
     the same quantity, so that sentence is **not** a valid prior for §6 and is not used as
     one. It is recorded here so it is not silently inherited.
  3. `busybox pkill -l` means "list signal names", not a dry run (see §8).

---

## 2. What fatal #1 established (all measured; this is the baseline, not a prediction)

The Doc 188 **A0 control arm** caught a spontaneous Android modem fatal. From
`evidence/188_android_side_repro/a0_window/`:

| quantity | value | source |
| :-- | :-- | :-- |
| modem brought out of reset | `[ 6.610988]` | dmesg |
| fatal detected | `[31524.147021]` = wall 2026-09-23T04:17:34Z | dmesg |
| **modem uptime at the fatal** | **31 517.536 s** = 8 h 45 min | `31524.147021 − 6.610988` |
| **signature** | **`ps_icmp6_msg.c:938:`** | `modem subsystem failure reason:` |
| beat vs `P = 903.675206 s` | beat 35, residual **−111.1 s** | computed |
| beat vs `P = 902.353 s` | beat 35, residual **−64.8 s** | computed |
| detection → `subsys2=ONLINE` | ≈ 5.5 s | dmesg |

**The window did not stop at the fatal.** As of the last snapshot it is **continuous from
`ap_uptime` 5 298.6 to 42 260.1 = 10.27 h with 0 gaps > 12 s** (7 056 samples; effective
cadence **5.239 s**), still **1 fatal**, with the sampler running under a supervisor
reparented to `/init` so it survives an `adbd` restart.

**Android's cpuidle baseline, measured from that same window** (cpu0, over 29 262.5 s):

| state | name / desc | rate |
| :-- | :-- | --: |
| state0 | `C0` / wfi | **105.8 /s** |
| state1 | `C1` / standalone_pc | **0.209 /s** |
| state2 | `C2` / pc | **12.09 /s** |

and the window's **first ~700 s were wfi-only with `state2` exactly 0.00 /s**.

**★ Correction this forces to the corpus.** The P-A6 instruction in
`188_…REPRODUCE_THE_900S_FATAL…` §11.4 says to read `state1` and treat *"`usage` in the
millions"* as "power-collapses like Android's". **Android's own `state1` is 0.209 /s** — the
test would return a **false negative on Android itself**. The deep state Android uses is
`state2`/`C2`/`pc`. Compare by `name`/`desc`, never by index (the two kernels name the states
differently), and report which counter.

---

## 3. ★ Android DOES report the signature — Doc 188 §4 is wrong

Doc 188 §4 states that Android *"does not report that label — `modem_err_fatal_intr_handler()`
is an IRQ handler that only knows that the modem asserted ERR_FATAL, not which site"*, and
concludes that a reproduction *"cannot claim the same signature"*.

**That is falsified by the A0 capture itself**, which contains
`modem subsystem failure reason: ps_icmp6_msg.c:938:.`

Read from the tracked kernel tree, `drivers/soc/qcom/pil-q6v5-mss.c`:

```c
static void log_modem_sfr(void)                       /* :46 */
{
    smem_reason = smem_get_entry_no_rlock(SMEM_SSR_REASON_MSS0, ...);   /* :51 */
    ...
    pr_err("modem subsystem failure reason: %s.\n", reason);            /* :63 */
    smem_reason[0] = '\0';                                              /* :65  CLEARS it */
    wmb();
}

static irqreturn_t modem_err_fatal_intr_handler(...)  /* :76 */
{
    pr_err("Fatal error on the modem.\n");            /* :84 */
    subsys_set_crash_status(drv->subsys, true);
    restart_modem(drv);                               /* :86 -> log_modem_sfr() */
    ...
}
```

So the IRQ handler is right that it does not know the site — **but it calls `restart_modem()`,
which calls `log_modem_sfr()`, which reads `SMEM_SSR_REASON_MSS0` and prints it.** OpenWrt's
equivalent is `drivers/remoteproc/qcom_q6v5.c:126` (`"fatal error received: %s\n"`) reading the
same modem-side descriptor.

**Consequences, and they are load-bearing:**
1. **Cross-platform signature comparison IS possible** — the "same class only" limitation in
   Doc 188 §4 and §9 is withdrawn. Fatal #2's site can be compared with OpenWrt's ~130 samples.
2. **The reason string is CONSUMED on read** (`smem_reason[0] = '\0'`). It is a one-shot:
   capture it from dmesg at the fatal edge, or lose it. This is why the sampler dumps the full
   dmesg on the fatal edge.
3. It is still one mutable global "last fatal" record — do not classify a fatal by a `file:line`
   read at a *later* time.

---

## 4. The question, restated

Android ≈ 1 fatal / 31 500 s; OpenWrt ≈ 1 / 900 s — a **~35× rate difference** on
byte-identical firmware. The fatal requires the coincidence of (a) ~400 accumulated sleep
cycles and (b) the modem's **≥50-record full-resource-set RPM batch collapsing to 0.00/s**
(`project_900s_fatal_anatomy` §3.4 — the proximate cause, starting ~1.4 s before the beat).

Two questions follow, and this doc pre-registers one test for each:

* **§5 — what does fatal #2 look like?** (signature class, beat, precursor, confounders)
* **§6 — does the AP's power-control *interaction* differ between the platforms?**

**An answer that would be wrong to assume:** Doc 188 §15 proposed that Android's protection is
`ssrestart_check()` resetting the modem on an A2 handshake failure. **That path never fired.**
Censused on the live device: **2 modem bring-ups in 41 190 s** (`[6.610988]`, `[31526.419651]`)
and **1 `Restart sequence requested`** — so Android ran **31 517.5 s of modem uptime with ZERO
non-fatal SSRs**. A reset-based protection that never fires is not the protection. The
protection is **upstream**: the handshake does not fail (2000 ms timeout vs a 3.160 ms worst
healthy ack, a ~630× margin).

---

## 5. Frozen predictions — fatal #2

Recorded now; the scorer in §8 prints every one of these automatically.

| id | prediction | falsified if |
| :-- | :-- | :-- |
| **P-A7** | **Signature class, pre-committed decision rule.** Class **S** = a site in OpenWrt's corpus family (`a2_power.c:*`, `lte_ml1_*`, `a2_task.c:*`, `mmoc.c:*`). Class **D** = anything else (incl. a `ps_*` data-path site). The class is assigned by this rule and **may not be re-classified after the fact.** | *(a classification — the falsifier is the rule itself)* |
| **P-A7-CONSEQUENCE** | **S** ⇒ Android fatals at the *same* sites as OpenWrt, just ~35× less often ⇒ the rate model stands and the search stays on the AP↔modem/RPM handshake. **D** ⇒ Android has a *different* failure mode ⇒ "35× rate" is the wrong frame; it is two defects and the differential must be re-split. | — |
| **P-A8** | **Latched offset.** Fatal #2's residual against `P = 903.675206 s` is within **±60 s** of fatal #1's **−111.1 s** (i.e. in [−171.1, −51.1]). Rationale: Doc 179's offset is a carried state, and #1 was *not* an `a2_power` event, so no jump is expected. | the residual differs by > 60 s |
| **P-A9** | **On the clock.** *Both* fatal #1 and fatal #2 land within **±150 s** of an integer multiple of `P = 903.675206 s`. (Stated as the necessary condition; weak alone — a random landing satisfies it with p ≈ 0.11 at n = 2.) | either fatal is further than 150 s from every beat |
| **P-A10** | **No daemon death precedes the fatal.** In the 1 800 s before fatal #2, all five of `rild`, `qmuxd`, `netmgrd`, `rmt_storage`, `time_daemon` are present (min ≥ 1 per sample). True for fatal #1. | any daemon count reaches 0 in that window |
| **P-A11** | **No admin-UI poller.** `himi_conn` = 0 in the 1 800 s before fatal #2 (the §13.3 confounder). True for fatal #1. | any non-zero `himi_conn` sample in that window |
| **P-A12** | *(descriptive, NOT scored as a test)* **Pre-fatal `state2` ramp.** For fatal #1 the 900 s before the fatal ran at **18.13 /s** against **1.67 /s** in the preceding 900 s (**10.87×**) — but the whole-window rate is 21.44 /s, so the reference window drives the statistic and the confound (overnight idle) is obvious. **Recorded, not concluded.** Do not cite it as a precursor. | — |

**A note on P-A8 vs P-A9.** P-A8 is the sharp test (a latched offset *repeats*); P-A9 is the
weak necessary condition. If P-A8 is FALSIFIED but P-A9 CONFIRMED, the reading is *"on the
clock, but the offset moved"* — which is itself informative (an `a2_power` offset jump,
Doc 179) and must be reported as that, not as "not on the clock".

---

## 6. Frozen prediction — the matched AP↔modem power-control comparison

### 6.1 Why this comparison, and what it is not

The fatal's proximate cause is a **power-collapse vote failure** whose stall is in the modem's
`rpm.sync`. The AP participates through the A2 power-control handshake. **This is the one
interaction the corpus has never compared quantitatively across the two platforms.**

It is deliberately **not** a "capture all AP↔modem traffic" experiment, for three reasons
established before writing this:

1. **The instrumentation is asymmetric and OpenWrt cannot match it.** Android exposes 11 live
   `ipc_logging` channels (`smd`, `smd_pkt`, `smd_tty`, `ipc_router`, `ipc_rtr_ind`,
   `ipc_rtr_req_resp`, `bam_dmux`, `smsm`, `smp2p`, `smem`, `msm_serial_hs`). The OpenWrt tree
   has **no `ipc_logging` framework at all** (`find -iname "*ipc_log*"` → empty; zero references
   in `drivers/`; `qcom_bam_dmux.c` exposes only `DEVICE_ATTR_RO(rx_telemetry)`; `qcom_smd.c`
   and `smp2p.c` have no debugfs). A matched capture would need a build.
2. **Most of that bus is already excluded.** The host sends **no** pre-collapse QMI commands
   (`project_900s_fatal_anatomy` §13); the userspace subtraction left the **fatal rate
   unchanged** (axis A7); the only periodic AP→modem send is QMI WDS `0x0024` at 30 s (A9);
   and the fault time is **invariant to AP voting** (1 vote → 910 s; 311 votes → 912.6 s, §5).
3. **The leading surviving hypothesis is on a different bus.** Axis A5 — Android's AP as an
   RPM bandwidth-voting client (107 call sites, 17 live `/d/msm-bus-dbg` clients) — is the only
   axis where Android has a whole *mechanism* OpenWrt lacks, and it is on the **RPM** bus. A
   complete AP↔modem capture would sail past it. So the RPM is included below.

### 6.2 The quantities, and where each lives

| quantity | Android | OpenWrt |
| :-- | :-- | :-- |
| AP power-collapse votes | `a2 ack out cnt` in `/d/bam_dmux/stats` | `pc_vote_tx_count` in `rx_telemetry` |
| AP acks received | `a2 ack in cnt` | `pc_ack_irq_count` |
| AP vote timeouts | *(not exposed)* | `pc_timeout_count` |
| lost PC edges / resync | *(not exposed)* | `pc_irq_count`, `pc_resync_count` |
| handshake state | *(not exposed)* | `pc_state`, `pc_ack_state` |
| modem PC cycles | `MPSS numshutdowns` in `/d/rpm_master_stats` | same file |
| RPM record rate | `devmem 0x29dc38` ÷ 32 | `rpmring` |

### 6.3 The predictions

**Primary derived quantity — the AP's SHARE:** `ratio = AP_votes / modem_PC_collapses`, i.e.
what fraction of the modem's power-collapse cycles the AP is actually driving.

| id | prediction | falsified if |
| :-- | :-- | :-- |
| **P-A13a** | OpenWrt's **vote rate** is within **2×** of Android's measured vote rate. | > 2× apart |
| **P-A13b** | OpenWrt's **modem PC-collapse rate** is within **2×** of Android's measured rate. | > 2× apart |
| **P-A13c** | The **ratio** `AP_votes / modem_collapses` is within **2×** between the platforms. This is the new quantity: a large difference would mean the AP's *share* of the modem's PC cycles is itself a differential, which is a **new axis**. | > 2× apart |
| **P-A13d** | *(positive control)* The **RPM record rate** reproduces the already-established **1.012×** match (Doc 190 §4). If the method cannot reproduce a quantity already known to match, **the method is broken and P-A13a–c are VOID.** | the control misses by > 2× |

**Stated in advance, so it cannot be invoked later:** the ±2× tolerance is wide **because
within-platform variance is wide** — OpenWrt's `pc_vote_tx_count` deltas between consecutive
fatals were `29 / 27 / 114 / 158 / 131` (a **5.9×** spread), and Android's own collapse rate
varies by more than that between windows. **This test is underpowered for differences smaller
than ~2× and that is a property of the counters, not of the result.**

**Explicitly NOT used as a prior:** Doc 146 §166's *"`pc_vote_tx_count` ≈ 0.24/s on OpenWrt vs
Android's measured 918 collapses / 3240 s = 0.283/s"* compares a vote rate to a collapse rate.
It is a category error and is **not** a prediction basis (§1, error 2).

**Confound to record with every sample:** the RPM record rate is an **AP-idle meter**
(4× CPU load → 3.91× drop, reversibly — Doc 190 §4), and the AP's idle regime is itself axis
A2. A heavy capture changes the quantity it measures. Both halves must be taken **at
comparable AP load**, and the load must be recorded.

---

## 7. Required n, and the coverage precondition

- **Fatal #2 is n = 1 for the event.** It cannot test a *rate* — one interval from one MTTF
  estimate is not a rate measurement. P-A7…P-A11 are **mechanism** tests at n = 1 and are
  reported as such; no rate claim is made from fatal #2.
- **P-A8 at n = 2** is the minimum for the latched-offset test, and it is scored only if
  fatal #2 is captured with **continuous coverage** (§7.1).
- **P-A13** needs **one comparable window per platform**, with the AP load recorded. It is
  scored only if both halves are taken with the same method (T0/T1 counter deltas) over a
  window of ≥ 120 s.
- **§7.1 — Coverage is a precondition, checked before the result.** A window whose
  `ap_uptime` column shows a gap cannot be scored for a rate. This is Doc 188 §12.3's rule and
  it is why the sampler carries an `ap_uptime` column at all.

---

## 8. The scoring harness (written and validated BEFORE the event)

`evidence/188_android_side_repro/score_fatal2.py` — reads the sampler CSV **by column name**
(the schema already changed once, v1→v2), and prints every prediction in §5 with its verdict.

Three design decisions worth stating:

1. **The fatal's modem uptime comes from the dmesg timestamps when a dump exists**, because
   the CSV's `modem_up` at the fatal edge is sampled up to one interval (5.239 s) *after* the
   fatal and reads high. On fatal #1: dmesg **31 517.536 s** vs CSV **31 518.74 s** — the
   **+1.20 s** offset is printed, not hidden.
2. **`himi_conn` is a connection COUNT, not a liveness flag.** 0 is the healthy value. Scoring
   it with the daemons' `≥ 1` rule inverts its meaning. *(This was a real bug, found by the
   tests below and fixed.)*
3. **The beat uses the precise dmesg value.** An earlier revision printed "USE THIS" for the
   dmesg figure but computed the beat from the coarse CSV one — a 1.2 s inconsistency.
   *(Also found by testing, not by the live event.)*

**Validation** (`scratch/fatal2_test/test_scorer.py`, 7 fixtures, all passing) pins each branch
that would otherwise execute for the first time on the real event:

| fixture | asserts |
| :-- | :-- |
| single fatal | detects it; reports beat 35; `himi_conn` scored as a counter; coverage continuous; P-A7b deferred at n = 1 |
| two fatals, same residual | P-A7b **CONFIRMED** |
| two fatals, far residuals | P-A7b **FALSIFIED** |
| injected 400 s hole | coverage gap detected and the window refused |
| zero fatals | handled, no traceback |
| `himi_conn` = 1 | flagged as the **CONFOUNDER** |
| wrong schema | refused loudly ("missing columns") |

**Regression against the real fatal #1:** dmesg-derived `modem_up` = **31 517.536 s**, signature
`ps_icmp6_msg.c:938`, beat 35 residual **−111.1 s** (dmesg) — i.e. the harness reproduces the
recorded values exactly.

*Trap met while building this:* `busybox pkill -l` means **"list signal names"**, not a dry
run — there is no dry-run flag, so a supposedly harmless `pkill -f <pattern> -l` is a real
signal send. The live sampler's `/proc/<pid>/cmdline` was confirmed as a match **before**
invoking anything, and the sampler was verified alive afterwards (7 040 → 7 043 rows).

---

## 9. What this does NOT establish

- **It does not identify the differential.** §6 measures whether the AP's power-control
  *interaction* differs; a match (P-A13 CONFIRMED) closes this axis without naming the cause,
  and a mismatch opens a new one without explaining the 35×.
- **Fatal #2 cannot test a rate.** n = 1 event.
- **P-A12 is not a precursor claim.** It is confounded and is recorded as descriptive only.
- **The Android half of §6 is taken on a *running* A0 window.** Adding a counter read every
  120 s is cheap, but the window is not a controlled platform and the load is not clamped.
- **It does not touch the modem firmware.** Nothing here reads, patches, or writes the baseband.
- **A signature match (P-A7 = S) would not prove a shared mechanism** — it would show the two
  platforms fatal at the same *sites*, which is consistent with a rate difference but does not
  by itself locate it.

**SOP compliance.** SOP steps observed: the comparative protocol (every quantity above is read
from the live device or the tracked kernel tree, and each is labelled with its file); the
pre-registration rule (§5 and §6 are frozen before either is scored, and the tolerance in §6.3
is stated before the OpenWrt half is taken); the "verify the manipulation took" rule (the
sampler's liveness is proven by its own `ap_uptime` column advancing, not by `ps` — which
renders it as bare `sh`); the deployment-verification rule (the scorer is validated against a
known event, and the instrument's *shape* asserted on a real capture, before the live event);
and measurement-hygiene rules 4, 9 and 10. Steps deliberately not taken: no modem firmware was
read, patched or written; no credential guessing against the vendor login; the SOP's
dual-firmware byte-comparison step is not applicable — this doc makes no claim about the
baseband image.
