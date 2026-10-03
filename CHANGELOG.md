# Changelog

All notable changes to this project are documented in this file.

The primary source of truth for the modem-stability investigation is
`Docs/Modem Stability/197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md` (a living
document; each §-entry is a changelog entry). This file records commits that
modify that ledger, for quick reference.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Pending / next

- **Cleanup patch (carried over)** — fold the `hang_probe_t4` / `f3cap` /
  `coredump-enable` blocks out of `/etc/rc.local` into a removal patch; the
  instruments are no longer needed post-§112.31 (P-REARM confirmed) and
  §112.18.13 (patch 831 soak passed 30/30 SSRs).
- **`a2_pin` default decision (carried over, open)** — whether the shipped image
  should default `a2_pin=1` (currently opt-in via uCI set; shipped config sets
  it). A design/risk question: `a2_pin=1` suppresses the cold-boot
  `a2_power.c:1189` fatal but is a **mitigation, not a root-cause fix**
  (§112.24/§112.28); it holds the modem's A2 power-control pin across pre-emptive
  SSRs.
- **The one open mechanism question** — *what stops the ML1 SERV-MEAS-RSP reply
  path at ~902 s* (§112.38 OPEN, §112.39 surviving candidate). This is a
  causal-mechanism question, not a timer-limit question; the firmware-timer axis
  is exhausted.

### Changed — modem stability ledger

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
