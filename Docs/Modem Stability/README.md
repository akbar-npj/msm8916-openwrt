# Modem Stability — corpus index and TRUST INDEX

**Read this before citing anything in this directory.** The corpus grew to 105 files
across many sessions, and a full trust audit on **2026-09-20** found that a large
fraction of it rests on three premises that have since been **measured to be false**.
29 files were moved to `_QUARANTINE/` as a result (see `_QUARANTINE/README.md`).

## The three retracted premises — do not build on these

1. **"The fatal has a fixed ~900 s period."** FALSE. Measured `a2_power.c:1189` fatal
   times: **172.353 / 918.195 / 1823.753 / 2729.267 s**. One boot produced a single
   fatal at 172 s and none through 1157 s. It is a *rate*, not a period. Everything
   that models a "900 s timer" / "SCLK maintenance timer" / "`lte_ml1_common_timer.c:390`
   every 902 s" is wrong.
2. **"The 1000 ms bam-dmux autosuspend is the bug; disable it (`autosuspend_delay_ms=-1`,
   `control=on`)."** FALSE and BACKWARDS. Android clears `SMSM_A2_POWER_CONTROL` 1000 ms
   after the last TX (918 power-collapse shutdowns in 53.8 min, measured live). The
   autosuspend *is* the parity mechanism. Corrected in Doc 140 §6.
3. **"Missing `QMI_WDS_GO_DORMANT` is the cause of the stall."** FALSIFIED by Doc 75:
   at the freeze the modem reported `traffic-channel-active(2)` and `uplink_fc=FLOWING`,
   so the stall is below QMI/WDS.

## Two more retractions (Doc 147, 2026-09-20)

4. **"The RX rearm / RX-watchdog path causes the stall."** FALSE. The driver's RX
   watchdog logs a warning every time it intervenes. It logged **zero** times across
   the measured stalls — the ring was armed with buffers queued and the modem
   delivered nothing. `pc_resync_count` and `pc_timeout_count` were both 0.
5. **"Stalls start at ~900 s."** FALSE. Measured stall onsets in a clean boot were
   **~90 s** and **~180 s** after boot, and both **self-healed** within
   seconds without any recovery action. Under sustained traffic the link is perfect
   (1 Hz ping, 240 s, TX:RX ≈ 1:1, **0 % loss**; DNS to the carrier resolver, 8.8.8.8
   and 1.1.1.1 all resolving).

## The steady-state symptom, measured (Doc 147 §5.4)

**After the link has been idle, the first packet is always lost and the retry always
works.** Reproduced **5/5**: 120 s idle, then one `ping -c 1 -W 5` → `replies=0/1 took=5s`
with `dtx=1 drx=0`; the next 3-packet ping gives `2/3` and DNS resolves. That is the
browse/DNS-after-idle hang users report, and it is exactly why `modem-bearer-watchdog`
escalates — its pre-escalation gate makes a **single** DNS attempt, and one attempt is
precisely what this defect eats.

**Fixed 2026-09-20 (Doc 147 §8):** `modem-bearer-watchdog`'s gate now retries the DNS probe
(`PROBE_ATTEMPTS=3`, 2 s apart) and requires `PROBE_FAIL_MIN=2` **consecutive** failures
before escalating. Verified: a first-packet loss is absorbed with no escalation and the
bearer stays up, while a genuinely sustained failure still escalates.

Also established and not to be re-litigated:

- The modem firmware is **byte-identical** to the stock Android build (all 21 MDT
  segments hashed) — the fault is **100 % AP-side**.
- The WTR1605→UFI001B RF transplant is **not achievable by binary means** (Docs 142/143/145).
  Do not restart that line.
- `MODEM_FIRMWARE_NO_SLEEP_PATCH_GUIDE.md` is **fabricated** and now quarantined.
- The **AP-side crash is solved**. pstore held a full kernel panic proving the NULL
  `skb` dereference in `bam_dmux_send_cmd()` reached from `bam_dmux_netdev_stop`
  (i.e. `wwan0` going down). Fixed, deployed, and verified — Doc 147 §4.
- The deployed modem driver is now **reproducible from source**: patch
  `809-bam-dmux-tx-pm-ordering-and-pc-resync.patch` was added because 13 fix hunks
  existed only in `build_dir` and would have been lost on rebuild — Doc 147 §7.

## Trustworthy core — safe to cite

| File | What it establishes |
| :--- | :--- |
| `Stock_Android_Live/01_ANDROID_LIVE_SESSION_FINDINGS.md` | 21-segment byte-identical firmware proof; partition/tooling ground truth |
| `Stock_Android_Live/02_DIFFERENTIAL_DIAG_ANALYSIS.md` | The modem does **not** crash at 900 s on Android; the failure is 100 % AP-side |
| `Modem RE/hmu05/900S_CRASH_LPR_FRAMEWORK_RE.md` | Latest firmware RE; identifies the `rpm.sync` stall; disproves the QMI-time and `common_timer.c:390` theories |
| `Modem RE/hmu05/900S_CRASH_ROOT_CAUSE_FIRMWARE_RE.md` | The Q6 power-collapse-vote root cause (only its counter semantics are superseded by the LPR doc) |
| `134_UFI001B_RF_FRONTEND_BRINGUP_SESSION_RECORD_AND_PENDING_WORK.md` | Model SOP-compliance statement; disproves Doc 133's RF claims |
| `136_HMU05_DIAG_PORT_AND_SERIAL_CONSOLE_DISCOVERY.md` | DIAG/rpmsg bridge + serial console |
| `137_HMU05_DIAG_LOG_STREAM_ENABLEMENT_AND_LOG_CONFIG_F_PROTOCOL.md` | `DIAG_LOG_CONFIG_F` wire format, empirically mapped |
| `139_BAM_DMUX_PATCH_808_ANDROID_PARITY_AUDIT_AND_FIXES.md` | Byte-level patch-808 parity audit |
| `140_BAM_DMUX_RUNTIME_PM_A2_POWER_COLLAPSE_PARITY_FIX.md` | Two runtime-PM defects proven; corrects Doc 62; reports E7 FAIL honestly |
| `142_UFI001B_B26_TRANSPLANT_RESIDENCY_ROOT_CAUSE.md` | b26 residency closed with three independent evidence lines |
| `143_UFI001B_HMU05_RF_BLOCKER_DISJOINT_TRANSCEIVER_DRIVERS.md` | Disjoint transceiver driver sets |
| `144_UFI001B_RF_VERDICT_CONFIRMED_ATTRIBUTION_CORRECTION_AND_BAM_DMUX_RTNL_OOPs.md` | No-RF confirmed; corrects Doc 141; documents the RTNL-leak oops |
| `145_RF_PORT_FEASIBILITY_WTR1605_INTO_UFI001B_VERDICT.md` | Quantitative infeasibility verdict |
| `146_HMU05_AP_SIDE_FIX_SESSION_AND_TRUST_INDEX.md` | Previous session: RTNL oops root cause + fix + verification; corpus trust index; next experiments |
| `147_HMU05_PSTORE_PANIC_CAPTURE_AND_STALL_CHARACTERISATION.md` | **The pstore kernel panic** proving the oops; fix verified in situ; stall re-characterised (not 900 s, not the RX ring); patch-809 reproducibility fix |

## Sound but narrow (accurate, subordinate scope)

`Stock_Android_Analysis/`: `23`, `33`, `37`, `53`, `54`, `59`, `65`, `70`, `72`,
`GHIDRA_ANALYSIS_TARGETS.md`, `STOCK_ANDROID_GROUND_TRUTH_REPORT.md`.
`Stock_Android_Live/03_BAM_DMUX_DIFFERENTIAL.md` (accurate comparison; its "most likely
cause" pick is an explicitly-flagged unconfirmed hypothesis).
`74`, `76`, `114`, `135`, `138`.

## Historical / superseded — a log, not findings

`89`–`132` are the WTR1605→UFI001B transplant patch logs and the earlier stability
reports. The transplant objective is void (Docs 142/143/145). They are kept as a
record. **Do not treat any of them as a current finding.**
`141` carries a caution banner (its soak ran the stock baseband, corrected by Doc 144 §7).

## Quarantined — do not cite

29 files in `_QUARANTINE/`, each with the false claim and its counter-evidence listed
in `_QUARANTINE/README.md`. Categories: fabricated patch recipes; the fixed-900 s-timer
law; the backwards autosuspend conclusion; fast-dormancy-as-cause; false
"deployed and verified" claims; and false RF/attach claims on a modem with no RF driver.
