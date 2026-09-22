# 181 — THE PC-ACK TIMEOUT IS `a2_power`-SPECIFIC, AND THE ACKS ARE ABSENT RATHER THAN LATE

**Date:** 2026-09-22
**Boot:** `eafa19f6-5a01-4ea8-b783-52e9f16bf862` (OpenWrt, kernel 6.12.94) — the boot of Docs 177/178/179
**Patch under test:** `msm89xx/patches/808-bam-dmux-stats.patch`, commit **`998ed35`** — the resume
pc-ack wait widened `msecs_to_jiffies(250)` → `msecs_to_jiffies(2000)`
**Deployed module:** `qcom_bam_dmux.ko` md5 **`982b633e2a682e21ad69b6e85d273941`** on the device —
**byte-identical** to the module the tracked patch builds (the patch → deployed-module chain is closed)
**Evidence:** `evidence/98_pc_ack_ab/` — `pc_ack_ab_frozen_20260922_170554.csv` (md5
`745b6a0820077eac59e8e73539232759`, 1143 samples, uptime 108.4 – 11584.3 s), `score_pc_ack_ab.py`,
`score_output.txt`

---

## 1. SOP compliance

| SOP step | done? |
| :-- | :-- |
| Comparative protocol against the stock-HMU05 ground truth | **YES** — the arm's constant is Android's own `UL_WAKEUP_TIMEOUT_MS = 2000` at `GitIgnore/android_kernel_zte_msm8916/drivers/soc/qcom/bam_dmux.c:237`. No baseband was touched. |
| Pre-register the prediction **before** the data exists | **YES, and it was Doc 170 §8.21.1's own** — "a 2000 ms window (or re-reading the ack state on timeout) would cover this class entirely. Prediction: after the fix, `pc_timeout_count` should stop rising by ~1 per fatal". Written 2026-09-22 before this boot existed. This doc scores it. |
| Freeze the capture, hash it, score the frozen copy | **YES** — the live `/tmp/pc_ack_ab.csv` is a tmpfs file with a live writer, so it was frozen at host 17:05:54, hashed, and scored as a copy. The scorer is `.gz`/live-writer agnostic and reads only the frozen file. |
| Keep a control | **YES, three** — (a) the 250 ms arm's own 6-member class (Doc 170 §8.21.1); (b) the 10 `lte_ml1_common_timer.c:390` fatals *inside this same boot*, which are the natural control for the signature claim; (c) the 13 fatal-free `stopped → is now up` intervals in the same boot. |
| Verify the instrument before resting a claim on it | **YES, and it changed a conclusion** — §6: `pc_ack_irq_count` is incremented **unconditionally** at the top of `bam_dmux_pc_ack_irq()` (patch line 1348), so a *late* ack is still counted; and `pc_vote_tx_count` increments **before** the SMEM write (patch line 174), which opens the alternative reading in §7. |
| State what is *not* established | **YES** — §8 |

---

## 2. What was tested

Doc 170 §8.16 found the AP-side differential: Android's bam_dmux waits **2000 ms** for the modem's
power-collapse ack where the OpenWrt port waited **250 ms**, alongside `UL_TIMEOUT_DELAY = 1000`. Doc 170
§8.21.1 then produced a mechanism and a falsifiable prediction:

> The modem **cannot ack a power-collapse vote while its firmware is being reloaded**, so the 250 ms wait
> **must** expire. … a 2000 ms window … would **cover this class entirely**. Prediction: after the fix,
> `pc_timeout_count` should stop rising by ~1 per fatal.

Patch 808 was therefore changed to 2000 ms and deployed. This boot is the result.

**The boot is the same one Docs 177/178/179 instrumented**, so the 13 fatals match Doc 179's table
exactly, including #7 at 5949.210659 and #10 at 8719.020382.

---

## 3. Result 1 — the wait's **start offset is invariant**, so the timeouts ran the full 2000 ms

The measured event is the `dev_warn` printed when the wait **expires**, so
`wait_start = timeout_line_time − wait_duration`. If the widening merely moved the expiry, the implied
start offset must be unchanged.

| arm | deltas (timeout − fatal) | implied wait start |
| :-- | :-- | :-- |
| 250 ms (Doc 170 §8.21.1, n = 6) | 0.431112 / 0.581419 / 0.435022 / 0.445107 / 0.439598 / 0.440271 | **0.181 … 0.331 s** |
| 2000 ms (this boot, n = 3) | 2.228364 / 2.216787 / 2.310981 | **0.217 … 0.311 s** |

The two bands **overlap**. The wait is entered at the same point in the SSR sequence in both arms, and
the extra 1750 ms is spent **entirely inside the wait**. So the three timeouts in this boot ran the full
2000 ms — the ack did not arrive within 2 s. That part of the design worked.

---

## 4. Result 2 — the down-window is **0.70 s**, so the expiry is *not* a down-window artifact

This is the measurement that decides the question, and it was not in the frozen capture — it comes from
the boot's `dmesg`, anchored on the fatal rather than on `MBA booted` (§8.21.1's own correction).

`stopped remote processor` → `remote processor … is now up`, all 13 frozen fatals:

```
n=13   down-window 0.698266 .. 0.705928 s   (spread 7.662 ms)
```

| fatal | signature | down-window | timeout | Δ from fatal | **Δ from `is now up`** |
| :-- | :-- | --: | --: | --: | --: |
| #1  | `a2_power.c:1189` | 0.701875 | 526.440017 | +2.228364 | **+1.328927** |
| #7  | `a2_power.c:1189` | 0.705928 | 5951.427446 | +2.216787 | **+1.312539** |
| #10 | `a2_power.c:1189` | 0.698266 | 8721.331363 | +2.310981 | **+1.414817** |
| #14 *(post-freeze)* | `a2_power.c:1189` | 0.704651 | 12364.614338 | +2.352912 | **+1.450558** |

**The wait (2000 ms) covers the outage 2.83× over, and every timeout expires 1.31 – 1.45 s *after* the
modem is already back up.** For fatal #1 the `SSR powerup: successfully reinitialized BAM channels and
rings` line lands at 525.323701 — also *before* the timeout at 526.440017.

**⇒ Doc 170 §8.21.1's mechanism is FALSIFIED.** The expiry is not caused by the modem being stopped; by
the time the wait expires the modem has been running for ~1.3 s. **And its pre-registered prediction
FAILED**: `pc_timeout_count` rose by exactly 3 over the frozen window (and by 4 by the post-freeze
read), one per `a2_power` fatal — precisely what the prediction said would stop.

A tight side-invariant, recorded but not explained: the down-window is remarkably constant —
**0.698266 … 0.705928 s across 13 recoveries, spread 7.662 ms** — tighter than Doc 170's two samples
(0.601 s, 1.297 s), which were taken during an active `lost edge` storm. This boot has
`pc_resync_count: 0` and zero `lost edge` lines.

---

## 5. Result 3 — the acks are **absent**, not late

`rx_telemetry` at the end of the frozen window and at the post-freeze read:

| read | votes | unvotes | sent | acks | deficit | timeouts | 2 × timeouts |
| :-- | --: | --: | --: | --: | --: | --: | --: |
| frozen (to 11584 s) | 217 | 217 | 434 | 428 | **6** | 3 | **6** |
| post-freeze (to 13176 s) | 254 | 254 | 508 | 500 | **8** | 4 | **8** |

The deficit equals **exactly 2 × the timeout count** in both reads. `pm_resume_attempts` equals
`pc_vote_tx_count` (217, then 254) — one vote per resume.

Why this bounds the reading: `bam_dmux_pc_ack_irq()` increments `pc_ack_irq_count` as its **first
statement**, before any state test (patch line 1348). A late ack therefore still increments the counter.
So the 8 missing acks are not late acks that arrived after the wait — **they never arrived at all**.

---

## 6. Result 4 — the residual class is `a2_power`-specific

| | fatals | with a pc-ack timeout |
| :-- | --: | --: |
| `a2_power.c:1189` | 4 | **4** |
| `lte_ml1_common_timer.c:390` | 10 | **0** |

Fisher exact on 4/4 vs 0/10: one-sided **p = 0.003497**. (On the frozen window alone, 3/3 vs 0/10, the
same test gives p = 0.003497.) The signature is the discriminator.

**Instrument check.** In this boot all 13 frozen fatals have ≥1 `pm_resume_attempts` increment inside
their own 10 s sample bucket (`pm_resume_attempts` from the CSV). The base rate is 217 resumes /
12235 s ≈ 0.177 per 10 s bucket, so `P(≥1 in a random bucket) ≈ 0.16` and 13 of 13 by chance is
≈ 5 × 10⁻¹¹. The bucket instrument is measuring what it claims. (Resumes cluster, and an SSR
mechanically attracts one, so this is a sanity check rather than an independent result.)

### 6.1 Correction to Doc 170's denominator framing

Doc 170 §8.13.7 says "the class is now **6 of 13**". That is the **all-fatal** denominator.
§8.21.1's own table has a control row — "#4, #5, #6, #8, #10, #11 — **no resume attempted**" — so the
class is **6 of the 6 fatals that attracted a resume**. The right comparison for a *timeout* metric is
timeouts per resume-attracting fatal, not per fatal.

### 6.2 The one decomposition that survives, as a hypothesis

Doc 170 §8.21.1's class members and their signatures:

| fatal | signature | in Doc 170's class? |
| :-- | :-- | :-- |
| #1, #2, #3 | `lte_ml1_sleepmgr_stm.c:4054` | **yes** (timeout at 250 ms) |
| #7, #12 | `lte_ml1_sleepmgr_stm.c:4054` | **yes** |
| #9 | `a2_power.c:1189` | **yes** |
| #4, #5, #6, #8, #10, #11 | — | no resume attempted |

So in the 250 ms arm the class contained **5 sleepmgr + 1 a2_power**, and in this boot the timeouts are
**4 a2_power and 0 of the 10 non-a2_power**. The coherent reading is that **the widening removed the
timeouts for the non-`a2_power` signatures and left the `a2_power` ones** — i.e. the 250 ms window was
genuinely too short for the former, and the latter is a different fault that no window length fixes.

**This is a hypothesis, not a result**, because the two arms ran **different signature names**:
`sleepmgr_stm.c:4054` in Doc 170's boot, `common_timer.c:390` in this one. The behavioural contrast
(timeout at 250 ms, none at 2000 ms) is real; the assumption that the two signature strings name the
same underlying path is not established. Do not pool them.

---

## 7. The honest alternative reading of §5

`bam_dmux_pc_vote()` increments its counter **before** the SMEM write (patch line 174), and the ack comes
from the **modem**, not from the write. So an unacked vote has (at least) two readings:

* **(a)** the modem received the vote and did not ack — its power-collapse path is broken;
* **(b)** the AP's write produced **no edge** at the modem (the SMEM bit was already in the target
  state), so the modem had nothing to ack. After an SSR that died inside the power-collapse module, a
  stale bit is a natural way to get this.

A third reading — a lost ack IRQ — is weakened but not excluded by `pc_resync_count: 0` and zero
`lost edge` lines (a lost *ack* IRQ is not a lost *pc* edge).

**Discriminator:** log the pre-write SMEM bit value inside `bam_dmux_pc_vote()`, or sample
`pc_line_level`/`pc_state` at a resolution finer than the 10 s monitor. Reading (a) and (b) predict the
same counters and are separated only by that bit.

---

## 8. What is NOT established

* **The cause of the missing ack** — §7's (a) vs (b).
* **The 6 → 3 halving is not significant.** Fisher on 6/13 vs 3/13: one-sided **p = 0.2055**, two-sided
  **p = 0.4110**. The arms also ran different signature mixes (§6.2), so they are not cleanly
  comparable. **This is not a win and must not be reported as one.**
* **Whether the ack is absent or merely absent *so far*.** The counters are cumulative; an ack arriving
  after the post-freeze read would raise `pc_ack_irq_count` and close the deficit.
* **Nothing about the ~903.675 s clock or the latched offset** (Doc 179) is touched by this doc. The
  fatal still fires on every beat: this boot delivered **14 fatals / 13 intervals**, one per interval,
  unbroken.
* **No causal chain** from the `a2_power` fault to the missing ack. The association is strong
  (p = 0.0035) and mechanistically natural — `a2_power` *is* the modem's power-collapse module and the
  pc-ack *is* the power-collapse ack — but co-occurrence is all that is measured.
* **`port failed halt` count is 0 and the coredump attribute is `disabled`** in this boot, so none of
  Doc 170 §8.13's capture-ON/OFF band analysis applies here.

### 8.1 A side observation that does not reproduce Doc 170

Doc 170 §8.13 built its A/B bar on `SSR before shutdown → MBA booted` = **0.119170 – 0.130237 s**
(capture-OFF) vs **0.824902 – 0.831559 s** (capture-ON). In this boot, also capture-OFF, the same pair
of markers gives **0.325234 – 0.331689 s** (n = 13, spread 6.455 ms) — **in neither band**.

Recorded, not concluded: the boot differs from Doc 170's in its module build (patch 808 at 2000 ms) and
in having `pc_resync_count: 0`, and the interval is tight within this boot. Whether the shift is a build
effect or a regime effect is untested. It does mean **Doc 170 §8.13's bands must not be applied to this
boot.**

---

## 9. What this does to the candidate list

| candidate | status after this doc |
| :-- | :-- |
| bam_dmux 250 vs 2000 ms | **FALSIFIED as the cause of this class.** The wait now covers the outage 2.83× over and the acks are still absent. |
| the SSR down-window being too short | **FALSIFIED for this class** — the down-window is 0.70 s. |
| the idle-traffic policy (2 s keepalive, 60 s QMI write) | already falsified 2026-09-20 — the fatal persisted after both were removed. |
| the AP's power-collapse **vote path** (`a2_power` ⇄ pc-ack) | **PROMOTED** — it is now the one interface where the fault and the missing acks are the same subsystem. §7's discriminator is the next measurement. |
| AP-side boot firmware (`hyp = qhypstub`, `tz = TZ.BF.3.0-00714`) | untouched by this doc; still the leading *structural* candidate. |
| the userspace bearer rebuild (netifd + ModemManager) | untouched; it is the **recovery cost**, and Task #111 tests whether it is also causal. |

---

## 10. Next actions

1. **Discriminate §7 (a) vs (b)** — log the pre-write SMEM bit in `bam_dmux_pc_vote()`. This is the
   single cheapest measurement that turns "the ack is missing" into a mechanism, and it is a loadable
   module (no flash).
2. **Task #111** — the OpenWrt userspace subtraction (stop ModemManager + qcom-time-daemon, `ifdown
   modem`, keep netifd/usb0 up so SSH survives), observing ≥3 epochs, with the manipulation asserted
   present (`pm_resume_attempts` flat). Pre-registration written before the manipulation.
3. **The AP-side boot-firmware swap** — the leading structural candidate, and the one experiment the
   userspace work cannot reach.
4. **Do not re-open the pc-ack window.** 2000 ms is Android parity and the defect it was aimed at is
   falsified here; leave it deployed.
