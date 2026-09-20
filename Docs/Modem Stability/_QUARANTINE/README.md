# QUARANTINE — superseded / misleading documents

These files were moved out of `Docs/Modem Stability/` on **2026-09-20** after a
full trust audit of the corpus (105 files). They are **not deleted** — several
were never committed to git, so moving is reversible where deleting would not be.

**Do not cite anything in this directory as evidence.** Each entry below states
the specific claim that is false and the specific measurement that contradicts it.

The trustworthy core of the corpus is listed in `../README.md`.

---

## 1. Fabricated / disproven at byte level

| File | False claim | Counter-evidence |
| :--- | :--- | :--- |
| `MODEM_FIRMWARE_NO_SLEEP_PATCH_GUIDE.md` | Patch `modem.b16` @ `0x001117e0` (`r0=#-1; jumpr r31`) and `0x005f2150`, then update MBA hashes at `modem.mdt:0x05bc` / `modem.b01:0x0228`; asserts a 900 s sleep-maintenance timer and that neutering global `ERR_FATAL` is safe | Byte-level disproof (5 independent checks). The offsets/opcodes do not match the verified image; applying it corrupts the baseband. |

## 2. The "fixed ~900 s timer" law — contradicted by measurement

| File | False claim | Counter-evidence |
| :--- | :--- | :--- |
| `78_DECOMPILED_MODEM_RE_AND_OPENWRT_GAP_ANALYSIS.md` | deterministic `lte_ml1_common_timer.c:390` fatal every Δt = 902.395 s; uninitialised stack byte | measured fatal times 172.353 / 918.195 / 1823.753 / 2729.267 s |
| `80_STOCK_ANDROID_DECOMPILED_RE_AND_CORRELATION_REPORT.md` | "exactly 902.5 seconds"; RRC-state causality | same; also self-inconsistent ("if (58 < 4) -> FAIL") |
| `81_DEFINITIVE_15MIN_MODEM_CRASH_ROOT_CAUSE_AND_OPENWRT_PARITY_SPEC.md` | "two distinct 900-second maintenance timers"; Timer A fixed by `qcom-time-daemon` | `Modem RE/hmu05/900S_CRASH_LPR_FRAMEWORK_RE.md` §5.1 disproves the QMI-time theory |
| `85_DEFINITIVE_15MIN_CRASH_FOUR_EXPERIMENT_PROOF_AND_FINAL_EMULATION_SPEC.md` | "hardcoded to 901.5 s (±0.7 s)"; uninitialised-stack bug | measured times; and its own Run 3 shows dormancy does **not** prevent the crash |
| `88_DEFINITIVE_DUAL_PARITY_RESOLUTION_REPORT.md` | adds the false "1000 ms autosuspend cycling power ~450 times" story | Doc 140 (autosuspend never ran); LPR root cause |
| `PHASE_5_PERSISTENT_CHANNEL_TRANSPORT_SPEC.md` | "900.00 continuous seconds" premise; power-collapse cycling fixes `4054` | Doc 140 §10.4 — the fault time is invariant to AP voting |
| `MSM8916_912S_CRASH_FORENSIC_TELEMETRY_REPORT.md` | `lte_ml1_sleepmgr_stm.c:4054` model | superseded by `900S_CRASH_LPR_FRAMEWORK_RE.md` (its counters are valid and were reused) |
| `MSM8916_MODEM_15MIN_CRASH_AND_STABILITY_RESEARCH_REPORT.md` | fix = `autosuspend_delay_ms=-1`, `control=on`; 900 s SCLK timer law | Doc 140 §6 — this is backwards |
| `QUALCOMM_MSM8916_MODEM_TIME_SERVICE_REPORT.md` | "rmtfs in read-only mode (`-r`) rejects the 900 s EFS NV write"; SCLK timer law | rmtfs is read-write; the EFS-sync-timer citation is circular (project-authored) |

## 3. The backwards autosuspend conclusion

| File | False claim | Counter-evidence |
| :--- | :--- | :--- |
| `Stock_Android_Analysis/62_STOCK_ANDROID_KERNEL_DRIVERS_RE_REPORT.md` | mainline had "the 1000 ms autosuspend bug, which our Tier 2 fix resolved by forcing `control=on` and `autosuspend_delay_ms=-1`, fully replicating stock Android stability" | Doc 140 §6 ("This is backwards"); Doc 140 §2 — Android clears `SMSM_A2_POWER_CONTROL` 1 s after the last TX (918 power-collapse shutdowns in 53.8 min measured live) |
| `Stock_Android_Analysis/17_WHY_STOCK_ANDROID_DOES_NOT_CRASH.md` | origin of the backwards autosuspend claim + the `rmtfs -r` claim | as above |
| `Stock_Android_Analysis/39_STOCK_ANDROID_VS_OPENWRT_MATRIX.md` | "The 4 Root Causes" incl. SCLK-900 s, 1000 ms autosuspend, `rmtfs -r` | as above |
| `Stock_Android_Analysis/40_MASTER_CRASH_INVESTIGATION_GUIDE.md` | "INVESTIGATION STATUS: COMPLETE — 4 root causes identified" | consolidates the same wrong model |

## 4. Fast-dormancy as the cause — experimentally falsified

| File | False claim | Counter-evidence |
| :--- | :--- | :--- |
| `73_ANDROID_RIL_FAST_DORMANCY_RE_REPORT.md` | "ModemManager never issues `QMI_WDS_GO_DORMANT` … the modem's internal sleep manager encounters an unhandled protocol state" | Doc 75 measured `traffic-channel-active(2)` and `uplink_fc=FLOWING` at the freeze — the stall is below QMI/WDS. Its observation that `event_report_indication_cb()` is an empty stub **is** accurate. |
| `84_STOCK_ANDROID_TELEPHONY_ARCHITECTURE_AND_OPENWRT_EMULATION_PROPOSAL.md` | proposes fast-dormancy emulation as the fix | Doc 75 |
| `87_DEFINITIVE_PURE_SOFTWARE_MODEM_STABILITY_PARITY_AND_VERIFICATION.md` | "RESOLVED, DEPLOYED, COMMITTED & LIVE VERIFIED" DRX-parity fix built on the dormancy premise | Doc 75 + LPR framework |
| `77_MSM8916_15MIN_BASEBAND_STABILITY_A_B_RESOLUTION_REPORT.md` | boot-time ATS sync + `qcom-time-daemon -r 0` eliminates the crash ("209/209 pings") | `900S_CRASH_LPR_FRAMEWORK_RE.md` §5.1 |
| `86_EXACT_DECOMPILED_MODEM_AND_TIMEDAEMON_PROOF_AND_UNIFIED_FIX.md` | `qcom-time-daemon`'s NITZ-triggered `0x0020` **causes** the crash | directly contradicts Doc 77 (same daemon **fixes** it); both crash models conflict with measured times |

## 5. "Deployed and verified" claims that are false

| File | False claim | Counter-evidence |
| :--- | :--- | :--- |
| `82_DEFINITIVE_HYBRID_STABILITY_RESOLUTION_REPORT.md` | "DEPLOYED & VERIFIED … zero SSR, zero panics" via firmware patch at `0x00229fd4` | Docs 132/140/145 — E7 FAIL; `a2_power.c:1189` persists |
| `83_DEFINITIVE_RF_FREEZE_ROOT_CAUSE_AND_RX_AGC_SURGICAL_FIX_REPORT.md` | 20-min RX freeze caused by skipped Rx-AGC recalibration | superseded by the Q6-PC-vote / `a2_power.c:1189` work |
| `FINAL_HMU05_MODEM_STABILITY_RESOLUTION_REPORT.md` | 4-pillar fix built on the fabricated no-sleep patch + `autosuspend_delay_ms=-1`; "45+ minutes … zero packet loss" | Docs 140/145 |
| `MSM8916_Modem_Stability_Complete_Engineering_Report.md` | Tier-1 no-sleep patch; claimed 24-hour benchmark | as above |
| `MSM8916_Modem_15Minute_Crash_and_Stall_Resolution.md` | recommends the fabricated no-sleep patch + global `ERR_FATAL` neutralisation | as above |
| `MSM8916_HYBRID_STABILITY_AND_RECOVERY_PLAN.md` | 4-tier plan incl. the fabricated patch + `autosuspend_delay_ms=-1` | as above |
| `Modem_Stability_Gap_Analysis_and_Action_Plan.md` | operational instruction to deploy `hmu05-patch-modem` | as above |
| `133_UFI001B_MPSS2_ATTACH_DIAGNOSTIC_AND_PURE_EPS_RESOLUTION.md` | §2.1 "Complete Elimination of All Baseband SSR Crashes (100% Crash-Free)"; §2.4 "receiving real LTE downlink carrier energy" (`IO: -106 dBm`) | Doc 141 §6 (crash at 706 s); Doc 144 §5 (no RF at all — the port ships no WTR1605 driver); Doc 134 §5.4 (the `-106 dBm`/`SINR 9.0` values are bit-identical static defaults) |
| `126_JIO_MCFG_RECONCILIATION_AND_EPS_ATTACH_ANALYSIS.md` | "Radio Camping on Jio Cell (`SINR 9.0 dB`, `lte-limited-srvc`)" as live reception | Doc 134 §5.4 — static defaults on a modem with no RF driver |
| `README.md` (original) | master index pointing at the fabricated no-sleep guide and the disproven 4-pillar report as authoritative | replaced by the new `../README.md` trust index |

---

## Notes on the borderline cases (left in place, bannered instead)

- `141_UFI001B_PORT_SOAK_A2POWER_CRASH_AND_BAM_DMUX_RX_REARM_DEFECT.md` — its soak
  attribution was wrong (it ran the **stock** baseband, corrected by Doc 144 §7).
  It already carries a caution banner, so it stays.
- `89`–`124` — the WTR1605→UFI001B transplant patch logs. They are superseded by
  Docs 142/143/145 (the transplant is not achievable by binary means) but they are
  a historical record rather than an active source of false conclusions. They remain
  in the main directory; treat them as a log, not as findings.
- `Stock_Android_Live/03_BAM_DMUX_DIFFERENTIAL.md` — its code comparison is
  accurate; its "most likely root cause" (Android's polling-mode RX fallback) is an
  unconfirmed hypothesis, flagged as such. Stays.
