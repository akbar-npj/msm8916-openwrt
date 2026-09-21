# Doc 162 — Soak run 8: patch 812 at scale, patch 814 on natural triggers, and the fatal-signature taxonomy

**Date:** 2026-09-21
**Device:** Melbon HMU05 (MSM8916 + WTR1605 + QFE2320), OpenWrt 25.12.5, Linux 6.12.94
**Deployed driver:** `qcom_bam_dmux.ko` md5 `eca269f10a685a83a8b679bc7be38d1d` (patches 810 + 811 + 812 + 814)
**Harness:** `/overlay/soak814.sh` on the device; repo copies at
`evidence/157_ssr_powerup_giveup/soak814.sh` and `scratch/patch811/soak812.sh` (the same script
under its earlier name).
**Status when written:** soak still running, 4206 s, healthy.

This document is a **measurement report**. It makes no firmware, driver or DTS change. Its one
analytical claim that goes beyond the run itself is a **refinement of the fatal-signature rule**
(§5), and that claim is derived from data already in the corpus plus this run.

---

## 1. What was run

Run 8 is the long soak of the two AP-side fixes that were built the same day:

* **patch 812** — `bam_dmux_pm_restart()` must **preserve** deferred TX slots instead of freeing
  them (Doc 156's root-caused data stall);
* **patch 814** — the SSR powerup work must **retry** instead of returning permanently after
  3.2 s (Doc 157's third AP-side defect).

Traffic pattern: **6 s idle, then 40 × `ping -c 1` to 8.8.8.8**, repeating. The burst is a shell
loop and not `ping -i 0.05`, because this device's busybox `ping` accepts only integer `-i`
values — the trap that silently invalidated an earlier run (Doc 156 §6, Doc 157 §7).

Sampling: one CSV row every ~10 s into `/overlay/soak814.csv`, plus an event log at
`/overlay/soak814.log`. Data-plane probes poll (12 × 5 s) rather than probing once, because an
SSR is followed by ~30–45 s of ModemManager re-registration (the measurement artefact recorded in
Doc 157 §7.1).

---

## 2. Result: both patches hold at scale

Measured at device uptime **4206 s** (70 min), 4 modem fatals, 4 SSRs:

| metric | value | what it means |
| :--- | :--- | :--- |
| `tx_defer_queued` | **549** | packets that took `start_xmit`'s defer branch |
| `tx_defer_submitted` | **549** | … of which were eventually submitted |
| **gap (queued − submitted)** | **0** | **no packet was lost** |
| `tx_defer_preserved` | 521 | preserved across a `pm_restart()` |
| `tx_defer_wiped_live` | **0** | nothing destroyed by the wake path |
| `tx_sweep_guard_hits` | **0** | patch 810's guards never had to fire |
| `pc_resync_count` | 0 | the watchdog never had to rebuild |
| `retries` | 0 | patch 814's retry never had to fire |
| `oops` | **0** | no AP-side fault |
| `cmd_open` | 40 | 5 modem boots × 8 channels — every reopen succeeded |
| `rx_slots_mapped` | 32 | full RX ring |

**The headline is the gap.** Doc 156 measured the pre-812 defect as **27 deferred packets, 27
destroyed, 0 delivered** — 60 % of all TX in the first minute of a boot. Run 8 measures **549
deferred, 549 submitted, 0 destroyed** over 70 minutes that included **four** A2 collapses and
four full SSR recoveries. The defect does not scale; it is gone.

`cmd_open: 40` is an independent confirmation of the same thing from the modem's side: the modem
accepted a fresh `CMD_OPEN` for all eight data channels on each of the five modem boots (one cold
boot + four SSRs). That can only happen if the driver rebuilt the channels each time.

**Patch 814 recovered the data plane on 4/4 natural SSR triggers** without a reboot — the
`DATA PLANE OK` probes report ~20 s / ~40 s / ~15 s / ~30 s to recovery. The *retry* branch
(`retries 0`) still has not fired on a natural trigger, because this modem came ready in
540–580 ms every time — well inside the original 3.2 s budget. Run 6 already showed the
*watchdog* rebuild firing naturally (Doc 157 §7.5); what remains unobserved is specifically the
**retry** path's trigger.

---

## 3. The fatal cadence in run 8 — a clean 2-cycle

Raw `dmesg` anchors, AP uptime seconds:

| # | modem boot (`… is now up`) | fatal | **modem uptime** | signature |
| :-- | :-- | :-- | :-- | :-- |
| 1 | 11.950 | 912.915 | **900.965** | `lte_ml1_sleepmgr_stm.c:4054` |
| 2 | 914.305 | 1855.593 | **941.288** | `a2_power.c:1189` |
| 3 | 1856.956 | 2757.618 | **900.662** | `lte_ml1_sleepmgr_stm.c:4054` |
| 4 | 2759.129 | 3699.367 | **940.238** | `a2_power.c:1189` |

Four consecutive fatals, alternating **900.965 / 941.288 / 900.662 / 940.238 s of modem uptime**,
with the signature alternating in lock-step. On boots 2 and 4 the deterministic ~901 s fatal
**did not fire at all**: the timer would have landed at AP 1815.3 s and 3660.1 s, and there is no
fatal there (`ssr` count is 4, matching the 4 fatals — no fatal went unrecorded).

This is **new structure**. Run 6, under the *same* harness and the same burst pattern, showed no
such alternation: all four of its sleepmgr fatals sat at 900.699 / 901.324 / 901.965 / 902.981 s,
and its `a2_power` fatals were scattered at 68.5 / 178.0 / 909.1 s (Doc 157 evidence). So the
2-cycle is a property of *this* boot, not of the harness.

**Not claimed:** why. Four samples is a pattern, not a mechanism. The two candidate readings are
(a) two competing mechanisms whose relative phase depends on the state the SSR recovery leaves
behind, and (b) one mechanism whose *reported* signature lags or leads by one event. §5's
taxonomy is evidence for (a) over (b), but does not settle it.

**What it does establish:** the ~40 s offset of run 8's `a2_power` fatals (941.288, 940.238) is
**not** a new period. It is the deterministic timer's neighbourhood plus ~40 s, and the ~40 s
coincides with the data-plane recovery time this harness measures after an SSR (~20–40 s). That
coincidence is worth testing, not asserting.

---

## 4. The period is not one number

Across the corpus the fatal is described as "~900 s", "~901.6 s", "~902.3 s", and "903.674 s"
(AP-side). Doc 149 corrected the last of these to **902.267 s of modem uptime (n=4, 112 ppm)**.
Run 8's four samples (900.965, 941.288, 900.662, 940.238) are **not** consistent with a single
period, and this is the second independent boot to say so — Doc 149's own E1 experiment already
produced 895.490 s then 932.855 s under traffic.

The reconciliation is §5: **"the period" is only well-defined per signature.**

---

## 5. The fatal-signature taxonomy (the one analytical claim)

Every fatal in the corpus that can be paired with its dmesg signature and its modem-uptime
anchor, grouped by signature. Script:
`evidence/162_fatal_signature_taxonomy/fatal_taxonomy.py` (re-runnable; every row names its
source).

| signature | n | min (s) | max (s) | mean (s) | spread (s) | ±ppm |
| :--- | --: | --: | --: | --: | --: | --: |
| `lte_ml1_common_timer.c:390` | 11 | 902.169 | 902.677 | **902.353** | **0.508** | **281** |
| `lte_ml1_sleepmgr_stm.c:4054` | 10 | 900.662 | 902.981 | **901.230** | 2.319 | 1287 |
| `a2_power.c:1189` | 7 | 68.524 | 941.288 | 695.068 | **872.764** | 627826 |

Read it as three different things:

1. **`lte_ml1_common_timer.c:390` is the deterministic timer.** n=11, **±281 ppm**, and the
   cluster is tight enough that the spread (0.508 s) is dominated by the anchor error on a cold
   boot. This is the signature that matches the firmware RE's `400 × 2.256 s = 902.4 s`.
2. **`lte_ml1_sleepmgr_stm.c:4054` is in the same neighbourhood but is not the same clock.**
   Its mean is **1.12 s earlier** and its spread is **4.6× wider**. The difference (1.12 s)
   exceeds the two spreads combined, so it is likely real, not sampling noise. It behaves like a
   *downstream* consequence of the timer rather than the timer itself.
3. **`a2_power.c:1189` is not a timer at all.** 68.5 s to 941.3 s, a spread of 872 s. Treating it
   as a period is the error Doc 148's retraction #1 was scoped to avoid.

**This refines Doc 149's rule.** Doc 149 says the assert string is a mutable global and **must
not classify a fatal**. That is correct as a warning — three different `file:line` values all
appear near 900 s, so a `file:line` does **not** identify *the* assert. But it is **too strong as
stated**: the three signatures have measurably different distributions, so the `file:line` *does*
carry information about which mechanism fired. The operational rule is:

> Do not ask "what is the fatal's period?" — ask "what is **this signature's** period?"
> Two of the three signatures are clocks; the third is an event.

This is consistent with, and quantifies, the existing scoped entry that `a2_power.c:1189` "is NOT
periodic, but the idle deterministic timer IS".

---

## 6. What is NOT claimed

* **Not** that the 15-minute crash is fixed. It is not. Four fatals fired in this 70-minute run
  with both patches deployed. Each costs a 15–40 s data outage. Patches 810/811/812/814 fix the
  **data plane**; they do not touch the modem's timer.
* **Not** that the 2-cycle is deterministic. n=4, one boot.
* **Not** that `lte_ml1_sleepmgr_stm.c:4054` is a separate timer from
  `lte_ml1_common_timer.c:390`. It may be the same timer observed at a different point; the
  data show different *distributions*, not different *sources*.
* **Not** that `cmd_open: 40` proves the modem itself re-provisioned. It proves the driver issued
  and completed the reopen; the modem accepted it.
* **Not** that the retry path of patch 814 is validated by this run. It is not — `retries: 0`.

---

## 7. Next steps

1. **Instrument the SSR window** (`dev_info()` at each of the 13 steps Doc 159 enumerated) to
   catch the 1-in-21 AP hang. Built as patch 817; see Doc 163.
2. **Push on the timer.** The firmware segments are proven byte-identical Android vs OpenWrt, but
   **NV/mcfg have never been compared**, and `qcom-carrier-autocfg` is an AP-side package that
   could provision them differently. That is the leading unexplored lead.
3. **Collect more boots** to test the 2-cycle: the harness should record, per fatal, both the
   signature and the modem uptime — it already does — so a longer run settles §3 for free.
4. Re-check the pre-812 vs post-812 fatal *timing* A/B. Patch 812 is not a stability fix, but
   nothing has tested whether removing the packet-destruction changes the timer's exposure.

---

## 8. Artifacts

| path | contents |
| :--- | :--- |
| `evidence/157_ssr_powerup_giveup/run8_soak814.log` | the run-8 event log (59 lines, pulled at 4460 s) |
| `evidence/157_ssr_powerup_giveup/run8_soak814.csv` | the run-8 10-s sampler, 396 rows (pulled at 4460 s) |
| `evidence/157_ssr_powerup_giveup/soak814.sh` | the harness itself (unchanged from run 6) |
| `evidence/157_ssr_powerup_giveup/run6_soak814.{log,csv}` | the run-6 comparison used in §3 |
| `evidence/162_fatal_signature_taxonomy/fatal_taxonomy.py` | the §5 script, with a source citation per sample |
| `evidence/162_fatal_signature_taxonomy/dmesg_at_4165s.txt` | full `dmesg` at 4165 s (451 lines) — the source of §3's anchors |
| `evidence/162_fatal_signature_taxonomy/soak814_evidence.txt` | the log + full CSV + CSV header, as pulled at 4165 s |

---

## 9. One line

**Patch 812 held at scale — 549 deferred packets, 549 delivered, 0 destroyed, over four A2
collapses — patch 814 recovered the data plane on 4/4 natural SSRs, and the run's four fatals
alternated 900.965 / 941.288 / 900.662 / 940.238 s of modem uptime with the signature alternating
in lock-step, which is new structure the corpus does not explain.**

---

## 10. Provenance and SOP compliance

Per the standing rule (every porting/research doc carries an explicit SOP statement; Doc 134 §2
is the model).

**SOP steps taken:**

* **Ground truth identified before interpretation.** The deployed `qcom_bam_dmux.ko` was hashed on
  the device (`eca269f10a685a83a8b679bc7be38d1d`) and matched against the repo's built module
  *before* any soak result was attributed to patches 812/814 — the check Doc 144 §7 exists to
  enforce.
* **Comparative protocol.** §5 compares against the corpus' own previously-published samples
  (Doc 149's table, the `Modem RE/hmu05/` RE docs, Doc 122, Doc 140, README §377/§468) rather
  than against a single boot. Every sample is cited to its source file.
* **A message is not a cause until its rate is known** (Doc 159's rule). §3 counts the SSRs (4) and
  the fatals (4) and checks that no fatal went unrecorded, rather than reasoning from a single
  line.
* **Measurement artefacts are treated as first-class.** The harness polls the data plane instead
  of probing once, and uses a `ping -c 1` loop instead of `-i 0.05`; both were prior artefacts
  (Doc 156 §6, Doc 157 §7.1). §2 reports the *gap* rather than a spot value, so a dead generator
  cannot masquerade as a pass.
* **Negative and unproven results are labelled as such** (§6), including the one thing the run
  did **not** validate (the retry path).

**SOP steps skipped, and why:**

* **No A/B against the stock Android firmware.** The one-device constraint means Android
  comparison costs a reflash; it is held in reserve. Not needed here — the claim under test is
  AP-side driver behaviour, and the run has a pre-fix baseline (Doc 156's 27/27) to compare
  against.
* **No coredump.** These fatals are the ordinary ~900 s kind, not a hang; Doc 153 establishes
  that a coredump is created only after `rproc_stop()` returns, and the fatals here all recovered
  normally.
* **No live modem-memory read.** Doc 158 measured that it is impossible (TrustZone).
