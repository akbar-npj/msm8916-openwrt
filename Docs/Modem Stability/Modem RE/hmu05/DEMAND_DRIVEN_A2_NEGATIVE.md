# Demand-driven A2 power (Android `ul_wakeup` parity) — TESTED, NEGATIVE

**Date:** 2026-10-07 · **Board:** HMU05 (`hmu05,250605v0s`) · **Branch:** `copy-main`
**Ledger:** `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md` (on `test/pure-software-modem`)

## Why this document exists

The `a2_power.c:1189` fatal ("A2 Assertion Failed" — the modem's power-**up** quiesce
times out) is suppressed only by the `control=on` pin-hold (`modem-a2-hold`), which
freezes the A2 so it never collapses. That is a workaround: the modem is held powered
and never gets to idle. The hypothesis tested here was that OpenWrt's **trigger model**
— not the wire protocol — is the cause, so porting Android's demand-driven model would
let the A2 collapse naturally without the fatal, and the pin could be retired.

## The hypothesis (from `reference_a2_handshake_semantics.md`)

Both stacks vote the A2 the same way (AP writes `SMSM_APPS_STATE & SMSM_A2_POWER_CONTROL`;
the modem acks via `SMSM_MODEM_STATE`). The real difference is **who decides when**:

| | Android `bam_dmux.c` | OpenWrt `qcom_bam_dmux.c` |
| :-- | :-- | :-- |
| power-up | `ul_wakeup()` from the TX/RX data path | `bam_dmux_runtime_resume()` on the runtime-PM autosuspend timer |
| power-down | `ul_powerdown()` after `verify_tx_queue_is_empty()` | `bam_dmux_runtime_suspend()` on the timer, queue not checked |
| RX holds A2 up | yes (`ul_packet_written=1` on RX) | no (RX only `pm_runtime_mark_last_busy()`, holds no PM ref) |

⇒ The claim was: a timer-scheduled resume can land while a modem A2 client's quiesce
word is still `& 7 != 0`, and the modem spins to its 45 000-spin assert budget.

## The implementation (patch `834-bam-dmux-demand-driven-a2-power.patch`)

- Driver-owned idle timer `power_timeout_work` (1000 ms, `BAM_DMUX_POWER_IDLE_MS`) —
  Android's `ul_timeout_work`.
- TX **and RX** mark activity (`pkt_written`, Android's `ul_packet_written`).
- `bam_dmux_power_wakeup()` ≡ `ul_wakeup()`; `bam_dmux_power_collapse()` ≡ `ul_powerdown()`.
- `runtime_suspend` returns `-EBUSY`, `runtime_resume` returns 0 — the PM core can never
  collapse the A2; the driver owns it.
- A `demand_driven` bool module param (default 1) so the two models are A/B-testable on
  one image.

The series 001–834 replayed from a wiped `build_dir` reproduced the edited source
byte-for-byte (`33d8a658…`), then `700aec30…` after the `runtime_auto` fix below.

## The result — FATAL

Soak `scratch/a2dd_soak.sh armB 1 3600` — `control=auto`, `modem-a2-hold` and the bearer
watchdog stopped, ~1 A2 cycle / 6 s (matching the baseline cycle rate):

| arm | first fatal | A2 cycles before it | per-cycle rate |
| :-- | :-- | --: | --: |
| **demand_driven=1, `control=auto`** | `a2_power.c:1189` @ modem-uptime **52 s** | **~9** (`pc_vote` 10 / `pc_unvote` 9) | **~11 %** |
| runtime-PM baseline (prior) | ~95 s | ~10–12 | ~8.2 % |

After the first fatal the modem **crash-looped** every ~55–70 s (7 fatals / 553 s). The
demand-driven trigger does **not** move the fatal. This is the **third** independent
AP-side mechanism to fail (patch 833 down-ack serialisation; patch 834-old down-ack
collapse-wait; patch 834-new demand-driven) ⇒ the quiesce failure is **internal to the
modem** and merely requires the A2 to collapse *at all*. **Line closed.**

Raw evidence: `scratch/a2dd_armB_evidence/armB.log`.

## ★ Reusable finding — a driver-owned collapse must not defeat `control=on`

The driver's idle timer **bypasses the PM core**. The first 834 build therefore
collapsed the A2 *while `control=on`*: `pc_unvote_tx_count` reached 3 in 47 s (the 833
module keeps it at 0), silently removing the proven mitigation.

Fix: `bam_dmux_power_collapse()` returns early when `!dmux->dev->power.runtime_auto`
(`pm_runtime_forbid()` clears it; `control=auto` leaves it true). Verified: `control=on`
+ `demand_driven=1` → `pc_unvote` stayed 0 across 25 s of traffic. **Any future
in-driver collapse path must carry this gate**, or the pin is not a valid override.

## Disposition

- Patch 834 **reverted** from both patch trees; series ends at 833. Kept as
  `scratch/patch834/834-bam-dmux-demand-driven-a2-power.patch.negative`.
- Device module rolled back to the 833 control (`d9274204…`); `modem-a2-hold` +
  bearer watchdog re-enabled → `control=on`, A2 frozen, 0 fatals.
- **`control=on` pin-hold is KEPT** as the shipped stability measure.

## Achieved vs Expected

| Intent | Expected | Achieved | Status |
| :-- | :-- | :-- | :-- |
| Demand-driven A2 fixes `a2_power.c:1189` | 0 fatals over 3600 s | fatal at mu 52 s (~9 cycles) + crash-loop | **NOT MET** |
| Per-cycle fatal rate falls vs 8.2 % | < 8.2 % | ~11 % (unchanged) | **NOT MET** |
| `control=on` remains a valid rollback | pin unaffected | first build defeated the pin; fixed via `runtime_auto` gate, then verified | **MET (after fix)** |
| Device left stable | 0 fatals, A2 frozen | 833 + `control=on`, 0 fatals, `pc_unvote` 0 | **MET** |

## SOP

Ground truth first: the Android trigger model was read from the tracked driver
(`GitIgnore/android_kernel_zte_msm8916/drivers/soc/qcom/bam_dmux.c`), not inferred; the
OpenWrt side from `qcom_bam_dmux.c`. One variable per arm: the only difference between
the arms is `demand_driven`. The patch was validated by replaying the whole series from a
wiped `build_dir` and comparing byte-for-byte before it was soaked. The soak used the
**same cycle rate** as the baseline so the per-cycle rate is comparable. **Negative result
recorded, not buried**: the demand-driven line is closed; the only AP-side mitigation is
the `control=on` pin-hold, and a true fix would be a baseband change (make
`FUN_c0505308` wait on the five `0xec320b80` client words, or reset the assert budget at
bring-up — see `A2_QUIESCE_ASYMMETRY.md`).
