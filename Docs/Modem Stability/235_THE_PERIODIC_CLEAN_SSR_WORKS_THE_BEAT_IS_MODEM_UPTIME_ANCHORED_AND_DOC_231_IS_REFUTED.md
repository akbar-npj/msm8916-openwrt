# 235 — THE PERIODIC CLEAN SSR WORKS: the beat is MODEM-UPTIME-anchored, and Doc 231's "absolute-AON-anchored" conclusion is REFUTED

**Date:** 2026-09-29. **Ledger item:** 46. **Supersedes:** Doc 231 §13.5 / §14 (the pre-emptive-SSR
retraction) and the ledger's AP-side row `pre-emptive modem SSR before the threshold → DISPROVED`.
**Boot:** `65458aa3-a141-477a-a4f3-f52a8c94c442` (unchanged; no AP hang).
**Artifacts:** `scratch/psr_test/` (`PRE_REGISTRATION.md`, `psr_run.sh`, `psr.log`,
`NOTE_doc231_reinterpretation.md`), `scratch/soak_preemptive.log` (Doc 231's own soak, re-read).

---

## 1. Why this document exists

The standing goal is to *fix the crashes*. Item 39 says the **root cause is unidentified**, and item 30
lists **every crash-eliminator as CLOSED** — including "pre-emptive SSR". That closure rested on a single
soak (Doc 231 §13.5, 2026-09-28) whose verdict was:

> The fatal beat lands on `914.82 + k × 903.675 s` of **AP uptime** … A remoteproc SSR re-arms the
> modem's ML1 timer to the **next absolute beat**, so restarting the modem can never advance past the
> beat. **The pre-emptive-SSR mitigation does not fix the crash.**

Doc 234 §34.1 (Android, 2026-09-29) then produced the *opposite* reading: a forced **clean** SSR put the
next fatal at **reset + 902.31 s**, i.e. **+58.9 s off** the AP lattice. The two cannot both hold, and the
difference decides whether a pure-software crash-eliminator exists.

## 2. Re-reading Doc 231's own soak log — the retraction was not supported

`scratch/soak_preemptive.log` is the authoritative 2200 s run. Read against the modem epoch:

```
21:05:27 ap_up=5773 modem_up=750 fatals=6 rproc=running ifup=true
21:06:28 ap_up=5833 modem_up=1   fatals=6 ...  <-- MODEM RESTART #1 (was up_ts=5023)
...
21:14:31 ap_up=6316 modem_up=484 fatals=6 ...
21:15:01 ap_up=6346 modem_up=18  fatals=7 ...  <-- MODEM RESTART #2 (was up_ts=5832)  *** NEW FATAL ***
```

Four facts, all derivable from the log:

1. The modem epoch before the intervention was **AP 5023** (`modem_up = ap_up − 5023`, consistent across
   the whole part-1 log: `modem_up=53` at `ap_up=5076`).
2. The pre-emptive SSR fired at **modem-uptime ≈ 800 s** (AP ≈ 5823–5832) — **≈ 100 s BEFORE** the 902.7 s
   deadline. It pre-empted correctly (`up_ts` 5023 → 5832). The pre-SSR epoch's silence is therefore
   *expected* under modem-uptime anchoring and is **not** evidence for an absolute anchor.
3. The fatal that followed (AP **6327.46**) sits at **modem-uptime 495.46 s**, not 902.7 s.
4. **Its site changed.** Every pre-SSR beat reported `lte_ml1_common_timer.c:390` (or
   `lte_ml1_sm_conn_inter_freq_stm.c:712`); the post-SSR fatal reported **`a2_power.c:1189`**.

Doc 231 §14.3 already flagged (4): *"the post-SSR fatal site rotated to `a2_power.c:1189`, which may be
**SSR-INDUCED** (the A2 handshake not re-completing after a warm restart; bam_dmux D1–D7)."*

**A fatal caused by the intervention, at a different site, is not evidence about whether the intervention
re-anchored the *natural* `common_timer` beat.** The two OpenWrt intervals that *can* be checked are
modem-uptime ones — epoch 12.37 → fatal 914.82 (Δ **902.45**), epoch 980.99 → fatal 1883.77 (Δ **902.78**)
— i.e. the modem-uptime family, not the 903.675 lattice. Doc 231's own interval set
(968.95 / 903.67 / 901.31 / 903.48 / 1735.23 s; the last = 1.92× the spacing) is already awkward for a
strict absolute lattice.

⇒ **The re-anchor was never actually tested on OpenWrt.** The retraction is unsupported.

## 3. The interventional test on Android — PSR-1 (pre-registered)

Pre-registration written and saved **before** the first intervention (`scratch/psr_test/PRE_REGISTRATION.md`).
Design: on the **armed** modem (same boot, 20 fatals, locked regime), fire a **clean SSR** —
`echo restart > /sys/kernel/debug/msm_subsys/modem`, i.e. `subsystem_restart_dev()`, **the same function
the crash path calls** — every ~700 s, so modem-uptime never reaches 902.7 s.

* **H_lattice** (Doc 231): the beat is fixed on the AP lattice; P-L1 = a fatal at **70 154.269871 ± 1.0**.
* **H_reanchor** (Doc 234 §34.1): the beat is `last modem power-on epoch + ~902.7 s`; P-R1 = **no fatal**
  during the intervention; P-R2 = after the last SSR the fatal appears at `last_reset + 902.7229 ± 2.0`.
* Discriminator: ~2 100 s between P-L1 and P-R2.

## 4. Result

| # | fired at AP | modem-uptime at fire | new epoch | deadline if left alone | fatal? |
| :-- | --: | --: | --: | --: | :-- |
| 1 | 69 953 | 702 s | **69 957.392485** | 70 860.115 | **no** (pre-empted by #2) |
| 2 | 70 664 | 707 s | **70 668.110125** | 71 570.833 | **no** (pre-empted by #3) |
| 3 | 71 357 | 689 s | **71 361.753572** | **72 264.476** | **YES — 72 265.086117** |

**Scoring against the pre-registered predictions:**

| prediction | pre-registered value | observed | verdict |
| :-- | --: | --: | :-- |
| **P-L1** (H_lattice) | **70 154.269871 ± 1.0** | **did not fire** (verified at AP 70 195.21: fatal count unchanged, last fatal still `[69249.425531]`) | **FALSIFIED** |
| **P-R1** (H_reanchor) | no fatal for 3 cycles | **no fatal for 3 cycles** (69 953 → 71 357 ≈ 1 404 s ≈ 1.55 beats) | **CONFIRMED** |
| **P-R2** (H_reanchor) | **72 264.476 ± 2.0** | **72 265.086117** — residual **+0.6096 s** | **CONFIRMED** |

**The fatal is not on the AP-absolute lattice.** Against `68 285.588046 + k × 904.954383` the observed
fatal sits at **k = 4.3975** — non-integral, unreachable by an absolute-anchor model. Against
`SSR#3 epoch + 902.7229` it sits at **+0.61 s**.

**Clean-SSR re-anchor family (n = 2, both this boot):**

| intervention | epoch | fatal | Δ (s) |
| :-- | --: | --: | --: |
| forced SSR (Doc 234 §34.1) | 68 347.111452 | 69 249.425531 | **902.314079** |
| PSR-1 SSR #3 | 71 361.753572 | 72 265.086117 | **903.332545** |

mean **902.823**, sd **0.720** — consistent with the boot's *natural* reset→fatal family
(n = 16, mean **902.7229**, sd **0.1554**). **A clean SSR re-anchors the deadline to the new modem
power-on epoch, exactly as a natural crash-recovery restart does.**

Corroboration: `subsys2/crash_count` 21 → **25** (3 clean SSRs + 1 fatal-recovery restart); `boot_id`
unchanged; modem ONLINE throughout; no `a2_power` fatal induced (unlike OpenWrt).

## 5. The corrected model

**M2′ (replaces both Doc 231's absolute anchor and the ledger's "an SSR arms it"):**

1. The fatal fires at **modem power-on epoch + ~902.7 s** (`lte_ml1_common_timer.c:390` family).
   In the *natural* regime the modem's crash-recovery restart is deterministic (epoch ≈ fatal + 2.25 s),
   which is why AP fatal→fatal is such a tight lattice (904.954383 s ± 0.0029) — the tightness is an
   artifact of the restart being internal to the modem, **not** evidence of an absolute clock.
2. **Any** modem restart — natural crash-recovery **or** a clean SSR — re-anchors the epoch, and hence
   the deadline.
3. The deadline is **suspended while the modem is in the power-collapse freeze** (Doc 234 §34.2:
   `mpss_sd` frozen at `0x9d6a`, 773/773 reads, ~10 300 s) — that is the quiet gap.
4. A fatal is still **conditional** (some beats do not fire — Doc 231's k=5), so "epoch + 902.7" is a
   *deadline*, not a guaranteed event.

⇒ **A periodic clean SSR keeps modem-uptime below 902.7 s and the fatal never fires. This is a working
pure-software crash-eliminator on the Android arm.**

## 6. Consequence for the ledger — the pre-emptive SSR must be RE-OPENED

The AP-side row `pre-emptive modem SSR before the threshold → **DISPROVED**` is **wrong**. The correct
statement is:

> On **OpenWrt**, a pre-emptive warm SSR **induced a different fatal** (`a2_power.c:1189`, at ~495 s of
> the new epoch), plausibly via the A2-handshake desync that a warm restart causes — an **AP-side
> `qcom_bam_dmux` defect** (D1–D7). Whether the SSR re-anchors the natural `common_timer` beat was
> therefore never tested on that arm.
>
> On **Android**, the same intervention produced a **clean `lte_ml1_common_timer.c:390` fatal at
> `epoch + 902.31 s`** — no `a2_power` induction — consistent with **A15 (the A2 handshake direction is
> INVERTED)**.

⇒ The OpenWrt path to the same workaround is the **already-known D1–D7 A2-SSR fixes**
(patches 808 / 810 / 812 / 814): make the warm SSR not desync the A2 handshake, then re-run the Doc 231
soak. Item 30's "pre-emptive SSR CLOSED" and the ledger's AP-side DISPROVED row are corrected by this
document.

## 7. ⚠ New trap found during the run

The runner's fatal test was `dmesg | grep -c "modem subsystem failure reason"` **> 20**. At the end the
count read **19** — the **dmesg ring evicted an older fatal line** when the new one was appended. The
detector never fired, the runner did not stop early, and its `DONE` line carries a **stale `last_reset`**.
The fatal was recovered only by reading the device directly.

⇒ **A `grep -c` *increase* test on a wrapping ring is unreliable in both directions.** Use the
**timestamp of the last fatal line** (`tail -1`), `crash_count`, or a device-side monotonic counter.
(Same trap class as the measurement-discipline memory; now demonstrated in the wild.)

## 8. What this does and does not give us

**Does:** a pure-software crash-eliminator for the Android arm — a periodic clean SSR on a cadence below
902.7 s. It is **reversible**, needs **no firmware patch**, and uses the standard restart path.

**Does not:** remove the root cause. The fatal is *postponed indefinitely* at the cost of a modem restart
plus the ~16 s bearer rebuild (Doc 185) per cycle. At a 850 s cadence that is ~1.9 % downtime. It also
does **not** yet tell us whether a *lighter* re-anchor exists (a partial / ML1-only reset) that would cost
no data outage — that is the obvious next experiment, and the highest-value one for turning this into a
shippable fix.

**Also unresolved:** the fatal is *conditional*, so "no fatal in 3 cycles" could in principle be luck.
Mitigating: the immediately preceding natural beats on this boot fired 16/16 (n=16, sd 0.155), and P-R2
landed on the re-anchored point to **+0.61 s** — a positive confirmation, not just an absence.

## 9. Achieved vs Expected

| goal | expected | achieved | status |
| :-- | :-- | :-- | :-- |
| Resolve the Doc 231 vs Doc 234 contradiction | one arm's model falsified by direct intervention | H_lattice falsified; H_reanchor confirmed (+0.61 s) | **MET** |
| A pre-registered test of "does a clean SSR re-anchor?" | pre-reg + 3 cycles + positive | P-L1 miss, P-R1, P-R2 hit | **MET** |
| A pure-software crash-eliminator | any AP-side lever that prevents the fatal | periodic clean SSR works on Android | **MET (workaround)** |
| Re-open the wrongly-closed pre-emptive SSR | ledger corrected | item 30 + AP-side row corrected by this doc | **MET** |
| Remove the root cause | patch the modem or eliminate the condition | not identified (item 39 stands) | **NOT MET** |
| A zero-outage re-anchor | a lighter reset than a full SSR | untested | **NOT MET (open)** |
| OpenWrt arm made crash-free | — | needs the D1–D7 A2-SSR fixes + a re-soak | **NOT MET (open)** |

## 10. SOP compliance

**Ground-truth-first:** the OpenWrt re-read uses the raw `scratch/soak_preemptive.log` and the ledger's
own beat table, not a summary; the Android side uses device `dmesg`, `/proc/uptime`, `crash_count` and
`boot_id` read live; the restart path is verified in-tree (`subsystem_restart.c:941-943` →
`subsystem_restart_dev()`), never inferred from a name.

**Pre-registration:** written and saved before the first intervention, with two explicit hypotheses, three
numbered predictions with numeric bands, a stated discriminator size (~2 100 s), and falsifiers for each
hypothesis. Scored as written; the one interim note (§7.1 of the pre-reg) was recorded before the run
ended.

**Controls / confounds asserted:** the regime was verified *armed* (the immediately preceding natural
reset at 69 251.546971 did produce its fatal at 69 249.425531); the SSR was verified **clean** (no
`modem subsystem failure reason` attributable to it); the AP did not hang (`boot_id` unchanged, no
pstore); the anchor is measured through the `Brought out of reset` proxy whose ~0.3 s offset cannot
affect a 2 100 s discriminator; `crash_count` independently corroborates the restart count.

**Reversibility:** the intervention is the standard, already-naturally-exercised restart path; no
firmware, NV, or overlay change. The capture (soak/apmon/procmon) was archived
(`scratch/psr_test/archive_pre_psr_20260929_195259.tar.gz`, 18.9 MB) before the intervention.

**Honesty about negatives:** Doc 231's retraction is explicitly reversed with its reason; the trap that
made the runner miss the fatal is documented rather than hidden; the result is labelled a **workaround,
not a fix**, and the conditional nature of the fatal is stated.

**Ledger + memory in the same session:** this doc; ledger item 46; memory
`project_900s_fatal_anatomy.md` §35 and the `MEMORY.md` index line.

## 11. Falsifiers

* A fatal on the **AP lattice** (`68 285.588046 + k × 904.954383`) in a future pre-registered run with a
  clean SSR at modem-uptime < 902.7 s ⇒ H_reanchor wrong, Doc 231 right.
* A fatal **during** the intervention window (modem-uptime < 902.7 s) at any site ⇒ the periodic-SSR
  workaround fails.
* A **cold** boot shown to fatal at ~902.7 s with no prior crash ⇒ M2′'s "arming" leg is wrong (still
  untested: Doc 234 §34).
* A clean SSR that induces an `a2_power.c:1189` fatal on **Android** ⇒ the Android/OpenWrt difference in
  §6 is not the A2-handshake direction.
* A zero-outage re-anchor shown impossible ⇒ the workaround's cost is irreducible.
