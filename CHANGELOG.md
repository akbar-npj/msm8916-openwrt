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
  3c. **The state-20 ARM-vs-CANCEL lifecycle ring (P-CANCELRATE)** — **DONE (§112.46),
     VOID/CONFOUNDED**: the run **wedged** (no fatal in 34 min; the fatal grep was
     empty from AP 159→2175), and the "CANCEL" hook site `0xc034e1f0` is a
     **shared dispatch merge point** (15+ selectors) so it counts **every ML1
     dispatch**, not the ctx0 cancel. The ARM (`FUN_c02fda90`, exactly 2 call
     sites) fired only **32×**, **corroborating §63.5's dormant finding** and
     **refuting the pre-registration's "0.63/s armer" premise** (that rate is the
     dispatcher's). **⚠ RETRACTED by §112.47.4b** — the v7 instrument (the SAME two
     sites) measured 674 calls / 902.29 s idle = 0.747 Hz, i.e. the pre-registration
     was plausibly RIGHT and this 32 is the outlier; the ARM rate is OPEN.
  3d. **Offline closure of 3c's "open identification"** — **DONE (§112.47), NEGATIVE**:
     the ctx0 arm is **genuinely only `FUN_c02fda90`** (its 2 sites are the only
     two that pass selector `0x80` to the sole pending-bit setter `FUN_c02fba64`;
     the other 13 sites arm the *other* eight contexts). There is **no hidden
     high-rate armer** ⇒ a v3 ring is **REDUNDANT**. Also corrected: `obj[+0x38]`
     is a **fixed context ID (20..28)**, not a transient state. The ARM rate is
     **contested (4 / 32 / 225 / 674) and OPEN**; the static closure is
     rate-independent. A thermal-signature search in the F3 was a **NULL**.
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

- **§112.54 (THE STATE NAMED — the fatal STM is in `ONLINE_SLEEP_WAIT`, not `SLEEP`)** — continuing
  §112.52's read-only next steps. **(1) The getter is proven:** `FUN_c03a0c20` →
  `c02d9f94` → `c0fe1960` where `c0fe1960: if(r0==0) assert; r0 = memw(r0+#4); return` ⇒
  **`FUN_c03a0c20()` = `stm_get_state(obj)` = `*(0xc1e158d0 + 4)`**, so `SLEEP = 3` is confirmed (compare
  is `cmp.eq(r0,#0x3)`). **(2) The state ENUM is recovered** from the class struct `0xc1a94f70`
  (`+0x04 = "LTE_ML1_SLEEPMGR_STM"`, `+0x18 = state table 0xc1a94fc8`, 0x10 B entries): idx 0 `INACTIVE`
  (h `c0396a40`), 1 `ONLINE` (h `c0396c50`), **2 `ONLINE_SLEEP_WAIT` (h `c0396f30`, f2 `c0397380`)**,
  **3 `SLEEP` (h `c03973a0`)**. Live value at the §112.49 fatal: `*(0xc1e158d4) = 2` =
  **`ONLINE_SLEEP_WAIT`**. **(3) The registration chain is proven from the coredump:** the
  `ONLINE_SLEEP_WAIT` entry handler `FUN_c0396f30` registers `FUN_c039ef80` and `FUN_c039eed0`
  (`c0396fc0: r1:0 = combine(##-0x3fc61080,#0x4); call 0xc0b663e0`); searching the coredump for the
  handler pointers finds each **exactly once** — `FUN_c0396f30`@`0xc1a94fec` (state-table +0x24),
  `FUN_c039ef80`@`0xc1da08a0`, `FUN_c039eed0`@`0xc1da0920`. ⇒ **the fatal function is entered as a
  handler only while the STM is in `ONLINE_SLEEP_WAIT` — the state it then asserts it is not in.**
  **Mechanism:** the sleep manager is **deadlocked mid-sleep-entry** — it left `ONLINE`, entered
  `ONLINE_SLEEP_WAIT` (a `GO_TO_SLEEP_REQ` issued, completion watchdog armed), and the sleep **never
  landed** (`SLEEP` never reached); a later event dispatches `FUN_c039ef80`, which asserts `state ==
  SLEEP` ⇒ ERR_FATAL. **§112.54.6:** the completion watchdog (`ctx+0x4a8`, callback `FUN_c03967f0`, a
  message-sender) is **DISARMED** (`+0x1c = 0xdeaddead`) at the fatal, so it did not fire the crash; and
  the line-4089 `serv_cell` assert is the *different* function `FUN_c0396850` — so `0xc3c81a40`/`50` are
  **two functions' asserts**, correcting §112.52's "one shared block" phrasing. Sleep-path message table
  (`0xc1a95088`) recovered (`GO_TO_SLEEP_REQ` `0x042b0209`, `RF_SLEEP_CNF` `0x042b0802`,
  `LL1_SYS_SLEEP_CNF` `0x040a0808`, …). **Does NOT add:** *why* the transition never lands (lost
  `*_CNF`? out-of-order wakeup? watchdog?) — still OPEN. Offline, read-only; no device write, no patch.
- **§112.52 (the assert SITE located — `FUN_c039ef80`, `lte_ml1_sleepmgr_stm.c`)** — continuing §112.51's
  read-only next step. Resolving the ERR/assert descriptor for line 4054 in the zlib descriptor DB gives
  **`0xc3c81a40`** (`lte_ml1_sleepmgr_stm.c`), and searching the stock disassembly for that exact
  constant finds its **unique** code load site: `c039f7c0` (`r0 = ##-0x3c37e5c0`), assert call
  `c039f7b8`/`c039f7c4`. The fatal task's stack (SP `0x8ae992d8` → modem VA `0xc46992d8`) carries the
  return address **`c039f7c4`** ⇒ the assert is issued from **`FUN_c039ef80`** (`c039ef80:
  {call 0xc0030000; allocframe(#0x88)}`), a sleepmgr handler registered at `c0396fc0`. The block is
  **shared** (packet `c039f7b8` → line 4054; packet `c039f7c4` → line 4089) and is reached from two
  in-function branches: `c039efec` (`if (FUN_c03a0c20(...) != 3)`) and `c039f098`
  (`if (FUN_c037121c(...) == 0)`). The return address `c039f7c4` pins the fired call to the **line-4054**
  one, so the fatal condition is **`FUN_c03a0c20(...) != 3`** = (hypothesis) `stm_get_state(
  LTE_ML1_SLEEPMGR_STM) != SLEEP` with `SLEEP = 3`. The stack also spills the live ctx
  (`table 0xc312db4c → 0xc20f1680`). **Adds:** the fatal is a **state-machine precondition** in a named
  sleepmgr handler (NOT the MCPM count guard of §112.38) — site, function, conditions, call all exact.
  **Does NOT add:** the root cause — why the STM was not in SLEEP is still OPEN, and `FUN_c03a0c20`'s
  identity / `FUN_c039ef80`'s role remain inferred. New tools `scratch/descr_scan.py`,
  `scratch/read_stack.py`. Offline, read-only; no device write, no patch.
- **§112.51 (the fatal assert, NAMED)** — resolving the hard-coded ERR_FATAL descriptor `0xc35b1384` in
  the §112.49 coredump gives `{line = 4054, msg_ptr → "Assert stm_get_state ( LTE_ML1_SLEEPMGR_STM ) ==
  SLEEP failed: ", file → "lte_ml1_sleepmgr_stm.c"}` ⇒ the fatal is **`ASSERT(stm_get_state(
  LTE_ML1_SLEEPMGR_STM) == SLEEP)`** — a sleep-manager **state** check, **not** the MCPM count guard
  `FUN_c0ce7fe0` that §112.38 found INERT. ⇒ §112.38's "the `sleepmgr:4054` guard is INERT" does **not**
  dispose of this fatal; the sleepmgr lead is **re-opened**. (Corrects my own §112.49.1 over-claim that
  the label was a generic "shared descriptor".)
- **§112.50 (mitigation hardening — fail-safe fallback anchor + fatal observer)** — audit of
  `modem-bearer-watchdog` found two real gaps, both fixed and deployed. **(1)** When `get_modem_uptime()`
  could not read the kernel's "is now up" line (dmesg evicted + no saved anchor), the old loop only
  *warned* and left the modem **unprotected** — the ~902.7 s deadline would simply expire. It now falls
  back to `SECS_SINCE_SSR`, a local timer since the last known restart (own SSR or observed fatal).
  **(2)** The loop never read dmesg, so a mitigation failure was **silent**; a fatal observer now counts
  `fatal error received` lines, logs each NEW site, and resets the local timer (baseline seeded at
  startup). Unit-tested with a mock dmesg; `sh -n` clean; deployed (`67136b8b…` → `cdb8c7db…` → `9a31d83`);
  backup `/root/modem-bearer-watchdog.pre-harden.bak`. Commits `fa47a85`, `9a31d83`.
- **§112.49 (PRE-REGISTRATION + RESULT — stock-firmware wedge-recoverability run)** — device run with the
  one-change discipline (`preemptive_ssr_enabled` 0→1): stock firmware (no patch), `a2_pin=1`, pre-emptive
  SSR **OFF**, continuous `ping -I wwan0` traffic, stall-watchdog fallback. The event at modem uptime
  **900.478 s** was a **FATAL** — dmesg `lte_ml1_sleepmgr_stm.c:4054`, coredump
  `/root/dumps/dump_devcd1_3334.bin` (85 398 475 B, md5 `580be11b…`) reads `Uptime 0:15:00`, task
  **`slpc`**, PC `0xc087a804`. **P-WEDGE-OUTCOME FALSIFIED** (it was not a wedge); the monitor's
  `WEDGE at up=3346` is a **false positive** (the ping failures were the fatal's SSR, not an independent
  wedge). ★ Corpus inconsistency §112.48.3 gains a clean data point: **traffic → FATAL** (supports
  §112.35, contradicts item 81; item 81's v7-instrumented firmware remains a confound). ★★ **`a2_pin`
  alone did NOT suppress this fatal** — the pin guards a *different* (cold-boot `a2_power.c:1189`) fatal;
  only the pre-emptive SSR suppresses the 900 s event. ★ The site label is the **fixed shared ERR_FATAL
  descriptor**, not the real site: reading the coredump confirms the MCPM guard `FUN_c0ce7fe0` is
  **INERT** (its snapshot arrays `DAT_c30fd9a8`/`DAT_c30fda28` are all zero; LPR `0xc1d473f8` = `"rpm"`),
  so this run does **not** revive the closed `rpm.sync` lead (§112.39) — the surviving root-cause
  candidate is still the **ML1-wide stall** (items 84–86/100/102). Pre-registration written **before** the
  event; coredump pulled device-local and md5-verified; device restored to the mitigated baseline.
- **§112.48 (WEDGE vs FATAL — the ~900 s event's two manifestations)** — offline re-parse of the two
  archived F3 series (`a2pin/f3stall/*` = the §112.34 idle run; `a2pin/f3assert/*` = the §112.35 traffic
  run). The wedge's F3 is a **staged shutdown**: RF (`rflte_*`) → 0 first, then an `a2_power` **storm**
  (53→114), then NAS/QMI decay, then only the `cfm_cpu_monitor` heartbeat; the fatal run instead has the
  RF **busy** (674→560) right up to the assert. **★★ A CORPUS INCONSISTENCY is recorded OPEN:**
  §112.34/35 say *traffic→assert, idle→no assert*; item 81 says the **exact opposite** — both cannot be
  right. Also: §112.34's "wedge" is **confounded with idle-sleep** (§104), so it is not a clean specimen;
  and the stated goal **"convert a fatal into a recoverable wedge" is likely COUNTERPRODUCTIVE** — a
  fatal recovers in ~1.7 s via the SSR (§112.35: assert 912.464 → up 914.168) while a wedge waits for the
  60 s stall watchdog. Two negatives of our own. No device write.
- **§112.47 (offline closure of §112.46.8's "open identification")** — pure offline re-derivation
  (stock `modem.asm` + the Ghidra decompilation; **no device write, no firmware build**). Establishes
  that (a) `obj[+0x38]` is a **fixed context ID `20+k`**, written once by `FUN_c02fb8b0` via
  `FUN_c02d7b80(ctx_base, 20+k)` — not a transient state; (b) `inst[+0x300]` is the pending-selector
  bitmask, **set only** by `FUN_c02fba64` (15 sites, one fixed selector each) and cleared by
  `FUN_c02fc3cc`; (c) the **`0x80` (ctx0) selector is passed at exactly 2 sites, both inside
  `FUN_c02fda90`** ⇒ there is **no hidden high-rate ctx0 armer** and a v3 ring is **REDUNDANT**
  (§112.45.5 already noted the v7–v11 rings ARE the `FUN_c02fda90` instrument). Two nulls recorded: the
  F3 `cap_fatal.bin` carries **no temperature record** (the 16 `temp` hits are the `PC_PENDING_TEMP`
  A2 client name), so a thermal/PA-aging hypothesis gets no log support; and the capture tail is a
  quiet post-SSR idle modem. **A negative of our own is stated** — the hoped-for lead is removed and a
  v3's value is downgraded to zero. Device left on stock firmware (md5-verified).
- **§112.46 (P-CANCELRATE — the state-20 ARM-vs-CANCEL lifecycle ring)** — a firmware ring (the v7/v8
  cave technique) retargeting 3 `call` sites: **ARM** `FUN_c02fda90` at `0xc0326874`/`0xc033c0f4` (both
  its call sites) + **CANCEL** `FUN_c02fc3cc` at the ML1 dispatcher `0xc034e1f0`. Offline-verified (hash
  PASS, read-back disassembly) before any write; boot-transparent (the retargeted dispatcher did **not**
  reproduce the §87 v12 crash-loop). **The fatal NEVER fired** — a 34-min watcher saw an empty fatal grep
  from AP 159→2175 and the modem **WEDGED** (data path dead, `mmcli` still `running/connected/lte`; the
  §71 regime, third confirmation). The coredump was **forced on-demand** (`dump_devcd1_1536.bin`, md5
  `f05204e8…`) and **carries NO filled crash report** (only format strings) — a forced watchdog crash
  cannot be classified. Ring valid (G1–G3 PASS; **G4 FAIL** — wedge, not fatal): **ARM = 32**,
  **CANCEL = 947** (last arg `0x80`), ring 128/128 CANCEL. **VOID on two independent grounds:** (a) the
  CANCEL site `0xc034e1f0` is a **shared dispatch MERGE POINT** — cases `jump` there with selectors
  `1,2,4,8,0x10,0x20,0x40,0x80,0x100,0x200,0x400,0x800,0x1000,0x2000,0x4000` — so the count is a
  **generic ML1-dispatch count, not the ctx0 cancel** (the §87 trap; the ring stores no per-entry
  selector, so the ctx0 subset is unrecoverable); (b) `FUN_c02fda90` fired only 32×, **corroborating
  §63.5** ("entered exactly 4 times in 900.6 s … essentially dormant") and **refuting the
  pre-registration's "~0.63/s armer / 565–674 arms" premise** (≈ the dispatcher rate). A second forced
  crash (`dump_devcd2_2057.bin`) reads the save page as **magic 0, count 0** ⇒ the page is **cleared at
  modem boot** (no persistence). The `0xec121000` counter **rate is unresolved** (builders say 204 800 Hz;
  `ll1_ring_dump.py` says 19.2 MHz; a cross-dump LL1 test is ambiguous because the seq counter resets at
  boot) ⇒ report raw ticks; the ordering is rate-independent. **Rolled back to stock firmware**
  (md5-verified) + config restored (`preemptive_ssr_enabled=1`). Pre-registered
  (`PREREG_cancel_ring.md`); a negative of our own is stated. Tools:
  `scratch/cancel_ring/{build,read}_cancel_ring.py`, `PREREG_cancel_ring.md`.
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
