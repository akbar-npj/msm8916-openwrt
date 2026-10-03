# Changelog

All notable changes to this project are documented in this file.

The primary source of truth for the modem-stability investigation is
`Docs/Modem Stability/197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md` (a living
document; each §-entry is a changelog entry). This file records commits that
modify that ledger, for quick reference.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Changed — modem stability ledger

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
