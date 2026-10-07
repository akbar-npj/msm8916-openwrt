# Arm A — the no-833 bam_dmux driver still fatals `a2_power.c:1189`

**Date:** 2026-10-07 · **Device:** HMU05 `hmu05,250605v0s` @ 192.168.8.1 · **Ledger:** `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md`

## The question

`test/pure-software-modem` runs `a2_pin=0` and (per ledger §112.170) "does not crash",
while `staging-main` with `control=auto` fatals `a2_power.c:1189` every ~65-70 s once a
data bearer is up. The **only behavioural driver difference** between the two branches
is patch **833** (`bam-dmux-down-ack-serialisation`, HMU05-gated; the test branch does
not have it — its extra patch 830 is pure `dev_err` logging). Is 833 implicated?

## Method (single variable: the driver)

- Driver swapped to the **no-833** module: `/overlay/modbackup/qcom_bam_dmux.ko.pre833`,
  md5 `323075c0fa6ec41def32f82988e65461` (45528 B), verified free of the 833 strings
  (`bam_dmux: modem down-ack timeout before resume`, `pc_down_ack_*_count`). It carries
  the 825 ("stale edge … reconciling") and 831 ("release deferred") markers ⇒ it is the
  staging-main series **minus 833** (its 820/821/831 predate commit `365eba1`, which only
  stripped inert `dev_err` probes).
- Everything else unchanged: staging-main system + HiMI_OK guard.
- Neutralised the `control=on` pin (`modem-a2-hold` stopped+disabled, bearer watchdog
  stopped), forced `power/control=auto`, restarted the modem to anchor U0, drove bursty
  traffic (2 pings / ~8 s period), watched `dmesg` for `fatal error received`.

Tooling: `scratch/armA/deploy_armA.sh`, `scratch/armA/armA_soak.sh`.

## Result

| arm | driver | first fatal | A2 cycles before it | rate |
| :-- | :-- | --: | --: | --: |
| control (833) | `d9274204…` | uptime ~30–96 s | 15 | 8.2 % (16/195) |
| **Arm A (no 833)** | **`323075c0…`** | **modem-uptime 74 s** | **15** | **~6.7 % (1/15)** |

Raw: `scratch/armA/armA_A.log`.

```
[up52s] modem up; U0=52 state=running
[up52s] traffic started (2 pings / ~8 s period)
[up127s mu74] *** FATAL at mu=74s: qcom-q6v5-mss ...: fatal error received: a2_power.c:1189: ***
[up127s mu74] telemetry at fatal: pc_state: 1 pc_vote_tx_count: 16 pc_unvote_tx_count: 15 pc_timeout_count: 1 pm_suspend_attempts: 15 pm_suspend_completions: 13 pm_resume_attempts: 16
```

## Conclusion

**Patch 833 is NOT implicated.** The no-833 driver fataled `a2_power.c:1189` **22 s
after the modem came up**, after ~15 A2 cycles — the same ~7-8 %/cycle rate as the 833
control. The `a2_power` fatal is intrinsic to the OpenWrt A2 power-down/up cycle under
traffic and is independent of the AP's down-ack serialisation.

**Corollary — the test-branch "`a2_pin=0` is safe" was a no-traffic artifact.** §112.170's
Arm B ran with an LTE IP but no sustained data; the fatal only arms once the A2 churns
*under traffic*. Removing 833 does not reproduce the test branch's immunity. Any branch
comparison must drive traffic.

## Implication for the "why is Android immune?" question

Android power-collapses its modem **~1/3.5 s idle** (`MPSS numshutdowns` 918 / 53.8 min)
with **0** `a2_power` fatals ⇒ Android is **structurally** immune, not merely rarer. The
OpenWrt AP-side handshake (with or without 833) is **not** the axis. The remaining
difference is inside the modem's collapse↔bring-up **client quiesce**
(`A2_QUIESCE_ASYMMETRY.md` §6) or the AP-side client-release timing — both **OPEN**.

## Achieved vs Expected

| Intent | Expected | Achieved | Status |
| :-- | :-- | :-- | :-- |
| No-833 driver under traffic | if 833 is the cause → no/few fatals | `a2_power.c:1189` @ mu 74 s (~7 %) | **MET (negative for the 833 hypothesis)** |
| Single variable (only 833 removed) | driver-only diff | `pre833` = series minus 833 (825/831 markers verified) | **MET** |
| Device restored | 833 module + `control=on` + 0 fatals | `d9274204`, control=on, 0 fatals, bearer up | **MET** |

## SOP compliance statement

Follows the Dual-Firmware Comparative Workflow (Doc 133 §1): **backup** (the 833 module
was copied to `/overlay/modbackup/qcom_bam_dmux.ko.armA_prev.*` before the swap) →
**ground-truth verification** (the no-833 module was md5-pinned and string-verified to
lack the 833 markers, and its 825/831 markers confirmed against the tracked patches) →
**one change at a time** (only the driver module differed from the known 833 baseline) →
**live empirical validation** (on-device soak). **No baseband (`modem.bNN`) was written.**
No cryptographic re-signing was involved. **Errors caught:** the first `md5sum` echo in
the restore step mis-quoted (`cut -d\" \"` inside a single-quoted SSH command); the module
was nonetheless restored and the md5 re-verified in a follow-up step.
