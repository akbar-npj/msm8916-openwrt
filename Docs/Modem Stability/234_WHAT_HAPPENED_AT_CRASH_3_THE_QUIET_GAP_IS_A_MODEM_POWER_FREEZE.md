# 234 — WHAT HAPPENED AT CRASH #3: the "an SSR arms the clock" model is FALSIFIED as stated; the quiet gap is a MODEM POWER-MANAGEMENT FREEZE

Date: 2026-09-29
Ledger: this doc is item 45 of `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md`
Device: Android arm, `root@192.168.100.1`, boot_id `65458aa3-a141-477a-a4f3-f52a8c94c442`
Question (user, verbatim): **"what happened at crash #3 that made it self-sustaining"**

---

## 1. Why this doc

Ledger item 43 §43.4 concluded: *"The 902.7 s deadline … is ARMED by an SSR (or by whatever state an
SSR leaves behind) rather than present from a cold boot."* That conclusion rested on **two** clean
beat hits (fatals 4 and 5). The boot has since produced **19** fatals, which is enough to test the
model properly. **It does not hold.** This doc replaces §43.4's mechanism claim with the measured
structure and states what remains open.

## 2. The complete single-boot fatal ledger (n = 19)

One boot, one AP power-on epoch. `reset` = the `Brought out of reset` line that precedes the fatal
(the modem's own power-on epoch); `reset->fatal` is therefore the **modem's own runtime**.

| # | fatal (AP uptime) | site | reset | **reset -> fatal (s)** | /902.700 | AP fatal->fatal |
|---|---|---|---|---|---|---|
| 1 | 41 471.242830 | `ps_icmp6_msg.c:938` | *(cold boot)* | — | — | *(41 471 s clean)* |
| 2 | 42 402.107361 | `lte_ml1_sleepmgr_stm.c:4054` | 41 473.160112 | **928.947** | 1.0291 | 930.865 |
| 3 | 53 806.352775 | `lte_ml1_common_dump.c:217` | 42 404.534696 | **11 401.818** | 12.6308 | 11 404.245 |
| 4 | 54 711.272308 | `lte_ml1_common_timer.c:390` | 53 808.737279 | **902.535** | 0.9998 | 904.920 |
| 5 | 55 616.226544 | `lte_ml1_common_timer.c:390` | 54 713.440505 | 902.786 | 1.0001 | 904.954 |
| 6 | 56 521.185680 | `lte_ml1_common_timer.c:390` | 55 618.397538 | 902.788 | 1.0001 | 904.959 |
| 7 | 57 426.139361 | `lte_ml1_common_timer.c:390` | 56 523.751865 | 902.387 | 0.9997 | 904.954 |
| 8 | 58 331.094401 | `lte_ml1_common_timer.c:390` | 57 428.155349 | 902.939 | 1.0003 | 904.955 |
| 9 | 59 236.043803 | `lte_ml1_common_timer.c:390` | 58 333.428920 | 902.615 | 0.9999 | 904.949 |
| 10 | 60 141.002776 | `lte_ml1_common_timer.c:390` | 59 238.106466 | 902.896 | 1.0002 | 904.959 |
| 11 | 61 045.957081 | `lte_ml1_common_timer.c:390` | 60 143.210153 | 902.747 | 1.0001 | 904.954 |
| 12 | 61 950.911170 | `lte_ml1_common_timer.c:390` | 61 048.413457 | 902.498 | 0.9998 | 904.954 |
| 13 | 62 855.865712 | `lte_ml1_common_timer.c:390` | 61 953.125914 | 902.740 | 1.0000 | 904.955 |
| 14 | 63 760.820029 | `lte_ml1_common_timer.c:390` | 62 857.990836 | 902.829 | 1.0001 | 904.954 |
| 15 | 64 665.774134 | `lte_ml1_common_timer.c:390` | 63 762.964537 | 902.810 | 1.0001 | 904.954 |
| 16 | 65 570.723875 | `lte_ml1_common_timer.c:390` | 64 668.149113 | 902.575 | 0.9999 | 904.950 |
| 17 | 66 475.682751 | `lte_ml1_common_timer.c:390` | 65 572.837775 | 902.845 | 1.0002 | 904.959 |
| 18 | 67 380.636400 | `lte_ml1_common_timer.c:390` | 66 477.880211 | 902.756 | 1.0001 | 904.954 |
| 19 | 68 285.588046 | `lte_ml1_common_timer.c:390` | 67 382.767275 | 902.821 | 1.0001 | 904.952 |

**Aggregates.**
* **Post-#3 modem-uptime intervals (n = 16): mean 902.722930 s, sd 0.155438 s, min 902.387, max 902.939, spread 0.5516 s.**
* Post-#3 AP fatal->fatal (n = 15): mean **904.954383 s, sd 0.002920 s, spread 0.009734 s (9.7 ms)**.
* Prediction check: fatal 19 was pre-registered as AP **68 285.59**; measured **68 285.588046**.

## 3. ★★ THE RESULT — only crash #3's SSR armed the clock

If an SSR armed the deadline, **every** post-SSR interval would be ~902.7 s of modem runtime. Measured:

* after **SSR #1** -> **928.947 s** (1.0291 beats; **+26.2 s**)
* after **SSR #2** -> **11 401.818 s** (12.6308 beats; **+10 499 s**)
* after **SSR #3** -> **902.535 s** (0.9998 beats) **<- first clean hit**
* after SSR #4..#18 -> 902.72 +/- 0.16 s, sixteen in a row.

⇒ **SSRs #1 and #2 did NOT arm the deadline. The arming happened at crash #3.** Ledger §43.4's
"an SSR arms it" is therefore **FALSIFIED as stated**: it was inferred from the two hits that happen to
fall *after* the transition, and it does not describe the two intervals *before* it.

## 4. ★★ WHAT CRASH #3 ACTUALLY IS — the quiet gap is a MODEM POWER-MANAGEMENT FREEZE

The interval #2->#3 (11 401.8 s) is not "a long beat". It is a period in which the modem **stopped
power-collapsing entirely**.

`apmon.csv` samples `/d/rpm_master_stats` (the RPM master-stats block) every 15 s and records the MPSS
and APSS `numshutdowns` — the RPM-assisted power-collapse counters.

| window | non-empty MPSS reads | distinct MPSS values | MPSS counter |
|---|---|---|---|
| 38 000 – 43 300 (pre-gap) | 634 | 543 | **advancing** |
| **43 400 – 53 700 (quiet gap)** | **773** | **1 (`0x9d6a`)** | **FROZEN ~10 300 s** |
| 53 850 – 68 000 (post-#3) | 1 507 | 1 439 | **advancing** |

* Last MPSS change: AP 43 316 (`0x9d6a`). Next change: AP 53 831 (`0x9d94`). **Resumption coincides
  with crash #3 (AP 53 806) + its SSR.**
* **This is not a read artifact.** 773 of 1 004 reads in the window were non-empty and *all 773 agreed
  on the same value*; and the **APSS** counter in the same samples kept advancing throughout
  (`0x59ac2`, `0x59a5d`, ... hundreds of distinct values). A broken read path would have frozen both.
* The AP<->modem data path froze on the same schedule: `skb read cnt` frozen from AP ~43 359,
  `skb write cnt` frozen from AP ~49 548, `a2 ack out` frozen from ~50 000. All resumed at crash #3.
* The `rpm_master_stats` *read success rate* also collapsed — 97-99 % non-empty through AP 46 000,
  84 % at 48 000, **19.2 % / 19.0 % at 50 000 / 52 000**, 50.7 % at 54 000, back to 98 % from 56 000.

⇒ **The quiet gap is a modem-side power-management freeze: the modem never enters RPM-assisted power
collapse, its data path is dead, and its RPM interface is degraded. It ends at crash #3.**

## 5. What is NOT the difference (all checked and ruled out)

1. **The AP's SSR path is byte-identical at #1, #2, #3, #4.** The full dmesg sequence
   (`subsystem_restart_dev` -> `subsystem_shutdown` -> `Port e0b38000 halt timeout` -> `memshare:
   Modem Restart has happened` -> `rmnet_config_notify_cb` -> smd_pkt resets -> MBA boot -> `loading
   from 0x86800000 to 0x8ba00000` -> `Brought out of reset`) is the same, line for line, with only
   timings differing. So the arming is **not** in the AP's recovery code.
2. **The modem boot is identical** (same load addresses, same `core_get_license_status`, same
   `wcd_program_btn_threshold` block, same restart_level = RELATED).
3. **The `[%p]` in `subsystem_shutdown` is noise.** Source: `subsystem_restart.c:463`
   `pr_info("[%p]: Shutting down %s\n", current, name);` — the pointer is **`current`**, the
   `subsystem_restart` workqueue worker's `task_struct`. The value changes at fatals #8, #10 and #17
   **while the clock stays locked**, which is the control that kills it as a signal.
   (Observed: `c1db1500` #1-#2, `c1db0000` #3-#7, `d2718a80` #8-#9, `d8603f00` #10-#16, `d4491500` #17-#19.)
4. **The crash site is not a clean predictor.** Sites walk PS -> ML1_sleepmgr -> ML1_common_dump ->
   ML1_common_timer and then stick; but #2 and #3 are *both* `lte_ml1_*` and behaved oppositely.
5. **A framework restart is an EFFECT, not the armer.** `system_server` 817 -> 31710 and
   `mediaserver` 200 -> 31441 between AP 54 352 and 54 983 — i.e. at **fatal #4**, one interval
   *after* the transition.

## 6. The competing-outcome hypothesis, and its falsifier

Working model: *the fatal and the power-management freeze are competing outcomes; while the modem is
frozen the watchdog is not evaluated, so no fatal fires.* This fits the quiet gap. **It does NOT fit the
locked regime**: after fatal #4 the data path stalls again (`skb read cnt` frozen 54 799 -> 55 485)
**and the fatal still fires on time**. What distinguishes the two is that in the locked regime
`mpss_sd` keeps advancing (41 143 -> 42 293 across the stall) whereas in the quiet gap it was frozen.
So the discriminator is *power-collapse activity*, not the data path.

**And it does not fit the cold boot either**: the cold period had `mpss_sd` advancing normally
(~0.8 /s, 543 distinct values) and produced **no fatal for 41 471 s**. ⇒ "the modem is power-collapsing"
is necessary-looking but **not sufficient**; the arming is a separate variable.

## 7. ★ The controlled experiment — a CLEAN forced SSR (pre-registered)

`echo restart > /sys/kernel/debug/msm_subsys/modem` calls `subsystem_restart_dev(subsys)`
(`subsystem_restart.c:941-943`) — **the same function the crash path calls**. This gives a *clean* SSR
with no modem crash.

* Fired 2026-09-29T13:46:33Z at AP 68 344. `subsystem_shutdown` AP 68 344.495934, `Brought out of
  reset` **AP 68 347.111452**. **No `modem subsystem failure reason` line => CLEAN.**
* Pre-registration: `scratch/forced_ssr/PRE_REGISTRATION.md`.
  * H_runtime (any restart re-arms): next fatal at AP **69 252.06 +/- 1.0**.
  * H_crash (only a crash arms it): next fatal at the old beat AP **69 190.54**, or none in 3 beats.
  * Separation **61.5 s** — far outside the dispersion (modem sd 0.155 s, AP dt sd 0.003 s).
* Result: **see §11 (appended when the fatal lands).**

## 8. Traps recorded (new this doc)

1. **`/sys/bus/msm_subsys/devices/subsys1` is `wcnss`, not the modem.** The modem is **`subsys2`**.
   `android_soak.sh` reads `subsys1/state` and labels it `modem=` — so the `modem=ONLINE` column in
   `soak.log` is **WCNSS's** state. The values happened to agree (both ONLINE), but any future
   reading of that column must use `subsys2`.
2. **`subsys*/crash_count` counts RESTARTS, not crashes.** Source: `subsystem_restart.c:467`
   `dev->crash_count++;` sits inside `subsystem_shutdown()`, so a *clean* restart increments it too
   (observed 19 -> 20 on the forced clean SSR). It is a reliable restart counter; it is not a crash
   counter. Use `dmesg | grep -c "modem subsystem failure reason"` for crashes.
3. **The `resets=` column of `soak.log` is a `grep -c` on the wrapping dmesg ring** and is therefore
   meaningless as a counter (it read 2 -> 1 -> 0 early in the boot as lines were evicted). The fatal
   *timestamps* are valid because the ring covered the whole fatal history; the *counts* are not.
4. **`/d/rpm_master_stats` reads fail intermittently by design** (seq_file; empty on some opens). The
   read-success *rate* is itself a usable health signal (see §4), but a single empty read means nothing.

## 9. Achieved vs Expected

| Expected going in | Achieved | Verdict |
|---|---|---|
| The user's question "what made it self-sustaining at crash #3" | Located the transition exactly (crash #3) and identified the state that ends there (a modem power-collapse freeze) | **PARTIAL — the *what* is measured; the *why #3 and not #1/#2* is OPEN** |
| Ledger §43.4 "an SSR arms the deadline" | **Falsified as stated** — SSRs #1 and #2 did not arm it | **MODEL CORRECTED** |
| A stable beat statistic on Android | Post-#3 modem-uptime interval **902.7229 +/- 0.1554 s (n=16)**; AP dt **904.9544 +/- 0.0029 s (n=15)** | **MET** |
| Find an AP-side cause for the transition | AP SSR path byte-identical; framework restart is an *effect* at #4 | **NOT FOUND (negative)** |
| A controlled test of the arming | Clean forced SSR pre-registered and fired; result in §11 | **RUNNING** |
| Root cause of the fatal | Not identified (item 39 stands) | **NOT MET** |

## 10. SOP compliance

* Ground truth first: every claim here is a measurement on the device or a read of the in-tree kernel
  source (`subsystem_restart.c`), not an inference from a name.
* Pre-registration: the forced-SSR test was written to `scratch/forced_ssr/PRE_REGISTRATION.md`
  **before** the outcome, with explicit predictions, a falsifier, and the discriminator's size.
* Controls asserted: the forced SSR was verified CLEAN (no failure-reason line); the `[%p]` hypothesis
  was killed by a within-boot control (it changes while the clock is locked); the `mpss_sd` freeze was
  distinguished from a read artifact by the APSS counter advancing in the same samples.
* Reversibility: the forced SSR uses the standard, already-exercised restart path (it ran 19 times
  naturally in this boot); no firmware, overlay or NV was modified.
* Honesty: ledger §43.4 is explicitly retracted here rather than quietly superseded.
* Ledger + memory updated in the same session (item 45; `project_900s_fatal_anatomy.md` §34).

## 11. Result of the forced clean SSR (appended after the fact)

**Next fatal: AP 69 249.425531**, site `lte_ml1_common_timer.c:390` (the same locked site). Following
reset AP 69 251.546971; `crash_count` 20 -> 21.

| anchor | predicted AP | measured − predicted |
| :-- | --: | --: |
| **H_runtime**, as pre-registered (reset + 904.954) | 69 252.065 | **−2.640 s** |
| **H_crash** (old beat 68 285.588 + 904.954) | 69 190.542 | **+58.883 s** |
| H_runtime with the **correct** anchor (reset + 902.7229, the modem-uptime interval) | 69 249.834 | **−0.409 s** |

**Measured reset → fatal = 902.314079 s.**

* **H_crash is DECISIVELY FALSIFIED** (+58.9 s; the surviving alternative is 22x closer). **A *clean* SSR
  with no modem crash re-anchors the deadline.** ⇒ the re-anchoring is a property of the **modem's
  power-on epoch**, not of the crash.
* **H_runtime is confirmed in substance, but the pre-registered POINT was missed by 2.64 s** — outside
  the stated ±1.0 s band. **This is a derivation error in the pre-registration, stated plainly:** I
  anchored on 904.954 s, which is the *fatal → fatal* interval and therefore **includes the ~2.2 s AP
  recovery**; the correct anchor for a `reset → fatal` interval is the **modem-uptime** interval
  (902.7229 s). With the correct anchor the residual is **−0.409 s** (2.6 sd, just outside the n=16
  family's observed min of 902.387 s).
* 902.314079 s is the **lowest value in the whole family**. Recorded as *possibly slightly shorter for a
  clean restart*; **n = 1, not a result.**

### 11.1 Refined model after this test

> **M2.** The deadline is **armed by the modem's first spontaneous crash** (crash #1, after 41 471 s of
> cold operation). Once armed, **any** modem restart re-anchors it to the new power-on epoch
> (demonstrated here with a *clean* restart). It fires at ~902.7 s of the modem's own runtime, **except
> while the modem is in the power-management freeze** (§4), which suspends it — that is the quiet gap.

M2 fits: the cold period (disarmed, 41 471 s clean); crash #1 arming; the quiet gap as a freeze; and
the forced clean SSR re-anchoring. It has **one untested leg**: that a clean restart does **not** arm a
*cold* modem. That is the only remaining discriminator and it needs a cold boot — §11.2.

### 11.2 The remaining discriminator (next experiment)

Cold boot → force a clean SSR → watch ≥3 beats.

* **M2 predicts: NO fatal.** (A clean restart re-anchors only once armed; the armer is a crash.)
* **"Any SSR arms it" predicts: a fatal at reset + ~902.7 s.**

The two outcomes are unambiguous and mutually exclusive, and the run costs ~15 min after boot.

## 12. Falsifiers for this doc

* If a **cold** boot is shown to produce a 902.7 s fatal with no prior crash, §3's "the clock is
  armed by a *crash* (or by something a crash leaves behind)" is wrong.
* If the forced **clean** SSR is followed by a fatal at AP 69 252.06 +/- 1.0, then a clean SSR re-arms
  an already-armed modem (H_runtime) and the arming variable is *not* the crash — the cold-boot test
  then becomes the only remaining discriminator.
* If `mpss_sd` is shown to be frozen during a period that nevertheless contains a fatal, §6's
  "power-collapse activity is the discriminator" is wrong.
