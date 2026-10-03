# Changelog

All notable changes to this project are documented in this file.

The primary source of truth for the modem-stability investigation is
`Docs/Modem Stability/197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md` (a living
document; each §-entry is a changelog entry). This file records commits that
modify that ledger, for quick reference.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Pending / next

- **Sequenced ~900 s fatal investigation (approved plan)** — user chose order
  1 → 2 → 3:
  1. **RF / interference lead** — DONE (§112.40): the RF/meas layer is the first
     LTE sub-layer to stop, but state-dependent and the F3 does not say why.
  2. **ML1 SERV-MEAS-RSP reply path** — DONE (§112.41): the reply is *downstream*
     of the ML1-wide stall — the producer (`FUN_c01bc8e0`) never runs
     (`slot[+0x01]=0` in 7/7 dumps); no new root.
  3. **New live instrument for the ML1-side counter** — DONE (§112.42): a high-rate
     RAM sampler is **impossible** (TrustZone blocks AP reads); the audit instead
     showed the meas-table flag is confined to (`d2=0`, `e02=1`) — a **new
     confounder `e02`** leaves its fatal-specificity **UNRESOLVED**. §112.41's
     phase-2 conclusion is **RETRACTED** and §105.8's "confound resolved" is
     **UNSUPPORTED**. The decisive control (`P-IDLECTRL2`) was **run but VOID**
     (the idle modem survived to 966 s but read `e02=0`).
  3b. **Positive control for the §105.4 flag (P-FLAGPOS)** — Stage 1 **DONE
     (§112.44)**: the flag is the deterministic output of the event-0x10 handler
     (which clears `d2` then arms), so `(d2=0, armed)` is forced by construction
     and the probabilistic Stage 2 is **superseded** (a snapshot cannot measure
     persistence). The deterministic firmware-ring follow-up was **approved,
     built and run** — **P-MEASRING, DONE (§112.45)**: the technique is proven
     (boot-transparent, captured the real fatal), but the literal "ARM stopped
     first" answer is **confounded** (the event-0x10 stream is early-concentrated
     and stops 814 s before the fatal). **⚠ §112.45.5 CORRECTION:** the proposed
     **v2** (hook `FUN_c02fda90` + `FUN_c02d7bd0`) is **REDUNDANT** — that is
     exactly the **v7–v11** instrument (items 79–86), and item 97 closed the
     armer's upstream statically. The only new piece (effective write/drain on
     the LL1 path) is largely pre-answered by the slot state. ⇒ **no v2 firmware
     patch is obviously warranted**; the next move is a decision, not a build.
- **Cleanup patch (carried over, DEFERRED)** — fold the `hang_probe_t4` /
  `f3cap` / `coredump-enable` blocks out of `/etc/rc.local`. Deferred by user
  decision: it fixes no crashes and removes the on-device safety net for a
  possible patch-831 regression.
- **`a2_pin` default decision (carried over, open)** — whether the shipped image
  should default `a2_pin=1` (currently opt-in via uCI set; shipped config sets
  it). A design/risk question: `a2_pin=1` suppresses the cold-boot
  `a2_power.c:1189` fatal but is a **mitigation, not a root-cause fix**
  (§112.24/§112.28); it holds the modem's A2 power-control pin across pre-emptive
  SSRs.

### Changed — modem stability ledger

- **§112.45 (P-MEASRING — the measurement-ring firmware instrument)** — a deterministic firmware ring
  (the v7/v8 cave technique) retargeting the **5 literal `call` sites** of the three ML1
  measurement-scheduler primitives (1 ARM `FUN_c01ecbf4` + 2 WRITE `FUN_c01bc8e0` + 2 DRAIN
  `FUN_c01bc934`), ring on save page `0xc1455000`, entered by `call` (r31 = packet return addr) and
  tail-jumping (`jumpr`, r31 intact) ⇒ **semantically transparent**. Offline-verified (hash PASS,
  read-back disassembly) **before any device write**; boot gate PASSED; captured the real fatal
  (`lte_ml1_common_timer.c:390`, modem ≈901.83 s, coredump `dump_devcd1_916.bin`, md5 `8912a618…`).
  Ring valid (G1–G5, count 389 239). **Literal pre-registered answer = class 1 "ARM stopped first"**
  (`last_ts[ARM]=88.0 s` vs WRITE `901.4 s` / DRAIN `901.8 s`) — **but CONFOUNDED and NOT promoted**:
  the modem stayed fully healthy for **813.8 s after the ARM stream stopped**, the ARM/event-0x10
  stream is **early-concentrated** (5 079 arms at ~58/s for 88 s, then 0 in the last 128 events;
  ARM:WRITE = 1:38), and the WRITE/DRAIN hooks fire on **every call** (incl. no-op iterations) so the
  192 k counts over-count (the armed slot's `+01` is still 0 at the fatal). ⇒ the instrument targeted
  the **wrong armer**: the fatal's armer is the **state-20 watchdog armer `FUN_c02fda90`**, not
  event-0x10. **Also fixes a real bug in the v7 `call` re-encode**: bit 24 (field sign) must be
  **set from the sign of the new field**, not preserved from the old word — validated against all 5
  stock sites **and** the LLVM toolchain's own encoder. **Rolled back to stock firmware** (md5-verified)
  + config restored (`preemptive_ssr_enabled=1`). Pre-registered (`PREREG_meas_ring.md`); a negative of
  our own is stated. Tools: `scratch/meas_ring/{build,read}_meas_ring.py`, `PREREG_meas_ring.md`.
- **§112.44 (P-FLAGPOS Stage 1 — the §105.4 flag's mechanism)** — the offline PC-STRUCT test the user's
  positive-control request called for. The **event-0x10 handler `FUN_c01ecbf4` clears `d2` and then arms
  the ring**: `FUN_c01bc724`'s `memset(carrier+0xbc, 0, 0x12c)` covers **`+0xd2`** (and `+0xbe`), and
  `FUN_c01bc7f0` then sets `carrier+0x00=1`, `slot[b9]+0x00=1`, `carrier+0xb8=(b8+1)mod3` (reads no
  `d2`, writes no `+0x08`). The template at `0xc1d7e91e` is `01 08 28 28 …` ⇒ the handler sets
  **`e02=1`**. ⇒ **`(d2=0, armed)` is reachable — indeed FORCED — by construction**; the §105.4 flag is
  the deterministic post-event-0x10 state (confirmed byte-for-byte in the 3 `d2=0` fatals:
  `up915.42/44`, `up1818.92`). The gate `FUN_c01bc934` **cannot drain** an armed slot until the writer
  `FUN_c01bc8e0` fills `+0x08`/increments `+0x01` ⇒ the fatal is the arm whose **result is never
  written** (§106's "consumer stalled", refined). **Consequence:** the pre-registered probabilistic
  **Stage 2 (n=8) is superseded and NOT run** — a snapshot cannot measure *persistence*, so the test
  cannot distinguish "transient" from "stuck"; the amendment is recorded in `PREREG_posctrl.md` before
  any run. Read-only/offline (no device access). §105.4's observation stands with its mechanism now
  named; §112.41 stays retracted; §105.8's "about to die" is directionally right but the flag is not a
  state that cannot exist healthy.
- **§112.43 (e02 reachability)** — `ifdown modem` (MM `registered`, LTE, no bearer) + a §6.2a
  capture (`dump_devcd1_3125.bin`, md5 `65dc280a…`, no crash report) reads carrier 0
  `e02=1, d2=1` (consumed slot, no flag) and **carrier 1 `e02=1, d2=0`** (empty, no flag).
  ⇒ **`(d2=0, e02=1)` IS reachable in a healthy modem** — the `e02` objection that voided
  P-IDLECTRL2 is **removed**. But the healthy instance was an **empty carrier** (no armed
  slot), so the flag's absence is **vacuous** and does not test it; the direction now
  **favours §105.4** (the flag = "armed but undrained"; healthy carriers always drain). Also
  refines `d2` to **per-carrier**. Device restored. Read-only analysis + one forced capture.
- **`b7b2ece` §112.42 (phase 3, the ML1 meas-table flag)** — a 39-dump census shows
  the §105.4 flag (`+0x00=1` on the `b9` slot **and** `b8=(b9+1)mod3` **and** data=0
  **and** `cons=0`) is confined to (`d2=0` **and** `e02=1`) dumps — so far exactly the
  **3 `d2=0` fatals**; the 2 healthy `d2=0` captures both have **`e02=0`**, which
  **mechanically disables** the flag (gate B `0≤+0x01` always passes). ⇒ the flag's
  fatal-specificity is **UNRESOLVED** (a new confounder, `e02`, was found). Two
  corrections: **§112.41 is RETRACTED** (its `slot[+0x01]=0` evidence was already
  falsified by §105.2 — it is the resting value) and **§105.8's "confound resolved" is
  UNSUPPORTED** (its four controls all read `d2=1`, so §105.5 was never executed).
  Design constraint: a high-rate RAM sampler is **impossible** (TrustZone blocks all AP
  reads of modem RAM). **P-IDLECTRL2 RAN** (`dump_devcd2_13985.bin`, md5
  `a6905ca4…`): the idle modem survived to modem-up **966 s** (`d2=0`) but read
  **`e02=0`** ⇒ **VOID by the pre-registered gate** — the gate fired as designed.
  Device restored (SSR 1500→800, reboot). Read-only analysis + one forced capture.
- **`adb9706` §112.41 (phase 2, ML1 SERV-MEAS-RSP reply path)** — ⚠ **RETRACTED by
  §112.42** (the `slot[+0x01]=0` evidence is non-discriminating). Kept for the
  record: traced the chain (`FUN_c02fda90` armer → CNF gate `FUN_c01bc934` →
  ready-writer `FUN_c01bc8e0`); its "producer never ran" claim holds only for the
  `d2=0` dumps. Read-only, offline.
- **`9d95c78` §112.40 (phase 1, RF/interference lead)** — re-confirmed item 71's RF
  collapse with a new tool `scratch/rf_timeline.py`; found the
  `rflte_core_rxctl` RX gain/freq-comp values are **always 0** (no retune storm);
  showed the collapse is **state-dependent** (876 vs 12 RF records across the two
  wedges). Verdict: **precursor**, cause unknown; the fatal-run RF-vs-assert
  ordering is not pinnable from the multi-session wrapping captures. Read-only,
  offline.
- **`be50e56`** — Add this CHANGELOG.md (Keep-a-Changelog format) documenting
  commit `03bbc17` and the prior §112.37–§112.39 series. Doc 197 remains the
  primary source of truth; this file is a quick-reference index.
- **`03bbc17`** — §112.39 sync note: findings folded back into §112.38.
  - Added a "Sync with §112.38" subsection to §112.39 recording that its
    findings were carried back into §112.38 (commit `c1ad1d9`): the MCPM row #1
    7-dump coredump confirmation (scratch zeroed 7/7, `q6pcvote` LIVE
    1060-1647), the OPEN-section `rpm.sync` RULED OUT, and the ML1-side
    SERV-MEAS-RSP ctx0 watchdog (items 84-86/100/102) listed as the surviving
    candidate.
  - Updated §112.39's Achieved/Expected table last row to cross-reference
    §112.38's OPEN section and items 84-86/100/102.
  - Net state recorded: the `rpm.sync` LPR park axis is **CLOSED**; the
    firmware-timer axis remains **exhausted** (§112.38); the one open question
    — *what stops the ML1 SERV-MEAS-RSP reply path at ~902 s* — is a
    causal-mechanism question, not a timer-limit question.

### Prior commits in this series

- **`c1ad1d9`** — §112.38 update: fold §112.39 ML1 counter findings back in.
  MCPM row #1 coredump-confirmed 7/7; OPEN section `rpm.sync` RULED OUT, ML1-side
  SERV-MEAS-RSP ctx0 watchdog added as surviving candidate; Achieved/Expected
  and SOP/Tools updated.
- **`b9275ad`** — §112.39: `rpm.sync` LPR park CLOSED — `q6pcvote` LIVE, MCPM
  scratch zeroed, not a hang. Read-only disassembly + 7-dump coredump synthesis.
- **`24e1001`** — §112.38: firmware timers/watchdogs complete inventory — three
  distinct ~902 s watchdogs (MCPM INERT, ML1 state-50ms FALSIFIED items 85/86,
  a2_power >900 EVALUATED); no untested lever.
- **`fe1653b`** — §112.37: PSCI/cpuidle axis CLOSED — §112.29 "cluster
  unreachable" retracted; cpuidle sub-lever already falsified (H-IDLE); no
  remaining reversible lever.
