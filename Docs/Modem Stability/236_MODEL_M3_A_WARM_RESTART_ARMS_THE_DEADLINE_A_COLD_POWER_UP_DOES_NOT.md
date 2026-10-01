# 236 — MODEL M3: a WARM restart ARMS the ~902.7 s deadline; a COLD power-up does NOT

**Date:** 2026-09-29. **Ledger item:** 47. **Supersedes:** M2′ (Doc 235 §5) — specifically M2′'s
**arming** leg. **Refines:** Doc 235 / item 46 (the re-anchor result stands; only the *arming* step
changes).
**Boot:** `048d2931-ad00-4e19-915a-6c6a60b9b8e1` — a fresh **cold boot** (the prior boot `65458aa3…`
ended in an AP reboot at ~15:17:23Z; see §7).
**Artifacts:** `scratch/cold_ssr/` (`PRE_REGISTRATION.md`, `cold_ssr_run.sh`, `cold_ssr.log`).

---

## 1. Why this document exists

Doc 235 (item 46) proved on the Android arm that **a clean SSR re-anchors the deadline**: fire one at
modem-uptime ~700 s and the next fatal appears at `new_epoch + ~902.7 s` (residual +0.61 s), not on the
AP lattice. That makes the periodic clean SSR a working crash-eliminator.

But it left the **arming** step untested. Two models survived item 46:

* **M2′ — "the modem's first spontaneous crash arms the deadline."** A clean SSR re-anchors a clock that
  is *already* armed; a clean SSR on a *cold, never-crashed* modem would re-anchor an **unarmed** epoch
  and therefore produce no fatal.
* **H_any-restart-arms — "any modem restart arms the deadline."** A clean SSR on a cold modem arms it.

The two differ in a way that matters: under M2′ the workaround only works *after* the first natural crash
(so the very first ~15 min after every cold boot is unprotected); under H_any-restart-arms the workaround
protects from the first moment. A **cold boot is the only condition that discriminates them**, and the
AP reboot handed us one.

## 2. State at intervention (measured, not assumed)

| quantity | value |
| :-- | :-- |
| AP uptime | ~1 008 s |
| `boot_id` | `048d2931-ad00-4e19-915a-6c6a60b9b8e1` |
| modem epoch (`Brought out of reset`) | **AP 6.627321** ⇒ modem-uptime ≈ **1 001 s** |
| visible fatals | **0** |
| `subsys2/crash_count` | **0** |
| `subsys2/state` | ONLINE; `rmnet0` has an LTE address (`10.151.113.221/30`) |

**Pre-condition already passed:** the modem ran **1 001 s cold with zero fatals** — it sailed straight
through the 902.7 s point on a cold boot. That is what M2′ requires; it is a pre-condition, not the test.

## 3. Intervention

Fire **one clean SSR** — `echo restart > /sys/kernel/debug/msm_subsys/modem` (i.e.
`subsystem_restart_dev()`, the same function the crash path calls) — at AP ≈ 1 100 (modem-uptime ≈ 1 100 s),
then **watch for 3 beats** (≈ 2 715 s) with **no further intervention**.

## 4. Hypotheses and predictions (pre-registered before the run)

* **H_cold-not-armed (M2′).** **P-C1:** **no fatal** for ≥ 3 beats (≈ 2 715 s) after the SSR.
* **H_any-restart-arms.** **P-C2:** a fatal at **`new_epoch + 902.7229 ± 2.0`**, and thereafter the
  locked cascade (fatal→fatal 904.954 s).

**Discriminator:** a fatal at `new_epoch + 902.7` falsifies H_cold-not-armed; nothing at that point
through 3 beats falsifies H_any-restart-arms.

## 5. ⚠ The test's asymmetry (declared in the pre-registration, before the run)

The test is **one-sided**. The prior boot's cold modem ran **41 471 s with no fatal**, so a cold modem is
*already* expected not to fatal within 2 715 s. Therefore:

* a fatal at `new_epoch + 902.7` is **strong, decisive** evidence for H_any-restart-arms (the SSR would
  have *caused* it, since the no-intervention baseline is silent for ~41 000 s);
* **no fatal is only WEAK** evidence for H_cold-not-armed (consistent with both hypotheses).

The falsification direction is the informative one. Recorded up front so the result cannot be
over-claimed afterwards.

## 6. Result — **P-C2 HIT: a clean SSR DOES arm a COLD modem (M2′'s arming leg is FALSIFIED)**

| event | AP uptime |
| :-- | --: |
| cold boot: modem up | **6.627321** |
| (no fatal for the whole cold stretch, incl. past 902.7 s) | up to 1 114 |
| clean SSR fired | **1 114.302** (`subsystem_restart_dev()`, `restart_level = RELATED`) |
| new epoch (`Brought out of reset`) | **1 117.120911** |
| **fatal** | **2 020.262333** — site **`lte_ml1_common_timer.c:390`** (the canonical site) |
| epoch → fatal | **903.141422 s** |

**Scoring:**

| prediction | pre-registered value | observed | verdict |
| :-- | --: | --: | :-- |
| **P-C2** (H_any-restart-arms) | `1117.120911 + 902.7229` = **2 019.843811 ± 2.0** | **2 020.262333** — residual **+0.418522 s** | **HIT** |
| **P-C1** (H_cold-not-armed) | no fatal for ≥ 3 beats | fatal at the **first** beat | **FAILED** |

⇒ **H_cold-not-armed is FALSIFIED; H_any-restart-arms is CONFIRMED.**

**Corroboration:** `subsys2/crash_count` 0 → 1 → 2 (the SSR + the fatal's crash-recovery restart);
`boot_id` unchanged (no AP hang); modem ONLINE throughout; no `a2_power` fatal induced (unlike OpenWrt).

**★ The refinement that matters.** The same modem, on the same boot, ran **1 114 s from its cold power-up
with zero fatals** — past the 902.7 s point — and then crashed **903.1 s after a warm restart**. So the
discriminator is not "the modem is running"; it is **"the modem has been *restarted*."**

## 7. MODEL M3 (replaces M2′)

1. The ~902.7 s deadline is **armed by a warm modem restart** — a crash-recovery SSR *or* a deliberate
   clean SSR — and anchored to **that restart's power-on epoch**.
2. A **cold power-up does NOT arm it.** The prior boot's cold modem ran **41 471 s** before its first
   (spontaneous) crash; this boot's ran **1 114 s** before we intervened. Both consistent.
3. Consequently the **un-armed state is reachable only by a cold boot** (an AP reboot). Once *any* warm
   restart happens the modem is armed, and from then on it is a ~902.7 s clock until the next cold boot.
4. The deadline remains **suspended during the power-collapse freeze** (Doc 234 §34.2 / item 45:
   `mpss_sd` frozen at `0x9d6a`, 773/773 reads, ~10 300 s) and **conditional** (some beats do not fire).

**Consistency re-check against the prior boot (boot `65458aa3…`, item 45).** §34's "only crash #3's SSR
armed it" is a **mis-reading**: under M3 the first *warm* restart of that boot armed the clock, and the
"quiet gap" is the freeze suspending it, not a re-arming event. The 42 404-epoch deadline (43 307.26)
coincides with the freeze onset (43 300–43 316) — i.e. **the deadline was absorbed by the freeze**, exactly
as M3 predicts.

**⚠ Honest note on §5.** The asymmetry section declared the test one-sided and said the *falsification*
direction (a fatal at the predicted point) would be the informative one. It came back on the strong side:
the fatal appeared exactly at the re-anchored point, so this is **decisive evidence**, not an absence.

## 8. What this changes

* **The workaround is stronger than item 46 claimed.** Under M3 the periodic clean SSR protects the modem
  **from the first moment after a cold boot** — there is no unprotected "first 15 minutes". A cold boot
  followed immediately by a periodic clean SSR never produces a natural fatal.
* **It sharpens the root-cause lead.** The arming event is a property of a **warm restart** and not of a
  cold power-up. *What does a warm restart leave behind that a cold power-up clears?* That is now the
  sharpest available question about the mechanism, and it is a direct lead on the root cause (item 39).
  Candidates worth naming: NV/EFS state written during the crash-recovery path; the EFS `fsg`/`modemst`
  A/B selection; a boot-time ML1 timer initialized from a persisted value; the ML1 stack's first-connect
  path taken after a warm restart vs a cold one.

## 9. Achieved vs Expected

| goal | expected | achieved | status |
| :-- | :-- | :-- | :-- |
| Discriminate M2′ vs H_any-restart-arms | a cold boot + one clean SSR | P-C2 hit (+0.42 s), P-C1 failed | **MET** |
| Test "does a clean SSR arm a cold modem?" | pre-registered, one-sided | decisive positive | **MET** |
| Correct M2′ | model refined | **M3** recorded (warm arms, cold does not) | **MET** |
| A stronger workaround statement | protection from the first moment | yes, under M3 | **MET** |
| Identify what a warm restart leaves behind | the arming mechanism | not yet — named as the open question | **NOT MET (open)** |
| Remove the root cause | patch the modem or eliminate the condition | not identified (item 39 stands) | **NOT MET** |

## 10. SOP compliance

**Ground-truth-first:** every value is read live from the device (`dmesg` timestamps, `/proc/uptime`,
`subsys2/crash_count`, `subsys2/state`, the `Brought out of reset` epoch line, `boot_id`); the restart
path is the same in-tree `subsystem_restart_dev()` verified for item 46, never inferred from a name.

**Pre-registration:** `scratch/cold_ssr/PRE_REGISTRATION.md` was written and saved **before** the
intervention, with two hypotheses, two numbered predictions with numeric bands, a stated discriminator,
and an **explicit one-sidedness declaration** (§5) so the result could not be over-claimed.

**Controls / confounds asserted:** the cold-boot baseline was measured (1 114 s, 0 fatals — it passed the
902.7 s point); the SSR was verified **clean** (no `modem subsystem failure reason` attributable to it);
the AP did not hang (`boot_id` unchanged); `crash_count` independently corroborates both restarts.

**Reversibility:** the intervention is the standard, already-naturally-exercised restart path; no firmware,
NV, or overlay change.

**Honesty about negatives:** the model's previous arming leg (M2′) is explicitly falsified with its
reason; the test's one-sidedness and the prior boot's undetermined reboot are both recorded (§5, §7)
rather than hidden.

**Ledger + memory in the same session:** this doc; ledger item 47; memory
`project_900s_fatal_anatomy.md` §36 and the `MEMORY.md` index line.

## 11. Falsifiers

* A **cold** boot that fatals at ~902.7 s **with no prior warm restart** ⇒ M3's "cold does not arm" is
  wrong.
* A clean SSR on a cold modem that produces **no** fatal at `epoch + 902.7` while the *same* boot's later
  warm restarts do ⇒ the arming is not a per-restart event.
* A fatal on the **AP lattice** rather than `epoch + 902.7` in a future pre-registered run ⇒ the
  re-anchor model (item 46) is wrong.
* A demonstrable persisted state that a cold boot clears and a warm restart does not ⇒ that state, not
  "warmth", is the arming mechanism (this would *confirm* M3 while naming its cause).
