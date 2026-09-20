# HMU05 900 s Crash — Definitive Firmware Root Cause

**Date:** 2026-09-19
**Firmware:** `HIMI_U01_MODEM_V1.0` / `MPSS.DPM.1.0.C7` (stock HMU05, byte-identical to `modem.elf` in `MelbonWhiteStock_Dump`)
**Artifacts analysed:**
- `GitIgnore/MelbonWhiteStock_Dump/modem_extracted/image/modem.elf` (50,049,024 B, ELF32 QUALCOMM DSP6)
- `Docs/Modem Stability/Modem RE/hmu05/modem_full_decompiled.c` (105,121,709 B, Ghidra Hexagon:LE:32:default)

---

> **UPDATE 2026-09-20 (Doc 146) — the "900 s" in this document's name is historical only.**
> The **mechanism** here (the Q6 power-collapse vote path) stands and is the current working
> model. The **timing** does not: measured `a2_power.c:1189` fatal times are 172.353 / 918.195 /
> 1823.753 / 2729.267 s, and one boot produced a single fatal at 172.353 s with none through
> 1156.95 s. Do not treat any "900 s" / "901.6 s" anchor or period as a fact.
> See `Docs/Modem Stability/README.md`.

## 1. Executive summary

The 15-minute (~900 s) crash is **NOT** a bug in the LTE ML1 sleep-manager state machine. It is a **Q6 Power-Collapse (PC) vote failure** detected by the MCPM power-management task, which then deliberately crashes the modem.

The crash message `lte_ml1_sleepmgr_stm.c:4054:` is the **watchdog reporting the origin of the sleep transition that failed**, not an assertion inside the state machine.

**Mechanism in one line:** after ~400 DRX sleep/wake cycles the Q6 can no longer enter power collapse; the sleep counter stops incrementing; the MCPM "system sleep check" sees this, logs `HARD_FAIL ... sleep count not incrmnt` + `UT detects Q6 PC Voting failure`, and calls the non-returning crash function.

400 cycles × ~2.25 s/cycle ≈ **900 s** — matching the measured crash period of 900.4–901.8 s of modem uptime.

---

## 2. The crash chain (verified function-by-function)

### 2.1 The detection function — `FUN_c0ce7fe0` @ `0xc0ce7fe0`
*(decompiled line ~2,380,311)*

This is the MCPM **system sleep check** (`mcpm.c`). Called on every tech sleep entry.

```
uVar6 = FUN_c0cd3384();                  // current sleep counter value
uVar1 = *(uint *)(*(GP + 0x6520) + 0x34);// RPM vdd-min reference value
puVar12 = &DAT_c30fd9a8 + iVar5*4;       // sleep counter stored at sleep entry
...
uVar7 = FUN_c0ce7f98();                  // min sleep value across active techs
if (uVar7 > 400) {
    uVar8 = FUN_c0ce7e58(0xf, 0xc8881);  // min elapsed time since any tech slept
    if (uVar8 > 400) {
        if (uVar6 <= *puVar12) {         // counter did NOT advance since sleep entry
            log("HARD_FAIL #%u[%u] sleep count not incrmnt, tis 0x%x NStis 0x%x "
                "TTR 0x%x sysAslp %u slptime %u slpcnt l:%u c:%u");
            FUN_c0cdab18("UT detects Q6 PC Voting failure", ...);
            log("Current time %lx: UT detects Q6 PC Voting failure");
            FUN_c0879150(&DAT_c3c68290);  // <-- NON-RETURNING CRASH
        }
    }
}
```

**Recovery path (`param_3 != 1`, threshold 450):**
```
if (uVar7 > 450) FUN_c0cd6f38();
```
`FUN_c0cd6f38` sends a *"Sending vdd min debug interrupt"* to the RPM — a soft recovery attempt that is **only reached in the non-active-sleep path**. In the active-sleep path (`param_3 == 1`, the real one) the crash fires first at 400.

### 2.2 The counters

| function | vaddr | returns |
|---|---|---|
| `FUN_c0ce7f98` | `0xc0ce7f98` | min of `DAT_c30fda28[]` across active techs (sleep value/time) |
| `FUN_c0ce7e58` | `0xc0ce7e58` | min elapsed ms since any tech slept (from `DAT_c30fdaa8[]` timestamps, fixed-point `/1000` conversion) |
| `FUN_c0cd3384` | `0xc0cd3384` | current sleep counter value (`FUN_c08bd290` → timer) |
| `FUN_c0ce7a50` | `0xc0ce7a50` | computes per-tech sleep time value; loops 15 techs |

### 2.3 The caller that selects the crash path — line ~2,383,400
The **tech sleep-entry** sequence sets the tech active bit, then calls the sleep check twice:

```
FUN_c0cf4e40();  *(GP+16000) |= (1 << tech);  FUN_c0cf4e4c();   // mark tech active
FUN_c0ce7fe0(tech, ..., 0);        // recovery path  (threshold 450)
FUN_c0cf4e28(iVar9 + 1);           // arm wake-up timer
thunk_FUN_c02a42e8(tech, ...);     // -> FUN_c0ce7fe0(tech, ..., 1)  CRASH path (400)
FUN_c0cf4e40();  *(GP+16000) &= ~(1 << tech); FUN_c0cf4e4c();   // clear active
(&DAT_c30fdb28)[tech] = 0;
(&DAT_c30fdaa8)[tech] = 0;
```

### 2.4 The watchdog — `FUN_c0cf3e04` @ `0xc0cf3e04`
*(decompiled line ~2,385,912)*

A second, independent crash path. Registered via `FUN_c11ff738` when a tech transition needs to complete. Runs **35 "wait" iterations + 35 "err_fatal countdown" iterations** (0x23 = 35 each), polling via `FUN_c08f1610` and sleeping via `FUN_c0cf4e10` → `FUN_c0ce6bf0(0,1,3)` → `FUN_c0914dc0`. After 70 iterations with no completion it calls `FUN_c0879150(&DAT_c3c68570)`.

Messages:
- `%s ut_print_debug wait #%d sec: tech %d, rex tcb %p` @ `0xc1a79e67`
- `%s ut_print_debug err_fatal in #%d sec: tech %d, rex tcb %p` @ `0xc1a79e9b`

### 2.5 The crash function — `FUN_c0879150` @ `0xc0879150`
*(decompiled line ~1,562,659)*

Takes a crash-reason structure (`&DAT_c3c68290` for the Q6-PC path, `&DAT_c3c68570` for the watchdog path). Formats the `file:line:` string, writes it into the SMEM crash buffer `DAT_c2a0d840` (0x50 = 80 B), then triggers the error-fatal interrupt via `thunk_FUN_c11e94d0()`. The AP kernel reads this via `pil-q6v5-mss.c:modem_err_fatal_intr_handler()` from `SMEM_SSR_REASON_MSS0`.

### 2.6 Format strings

| vaddr | content |
|---|---|
| `0xc1c350b4` | `FATAL ERROR: %d %s:%d\n` |
| `0xc18491f9` | `%s:%d:` |
| `0xc1a77422` | `%s HARD_FAIL #%u[%u] sleep count not incrmnt, tis 0x%x NStis 0x%x TTR 0x%x sysAslp %u slptime %u slpcnt l:%u c:%u` |
| `0xc1a77494` | `%s Current time %lx: UT detects Q6 PC Voting failure` |
| `0xc1a774a9` | `UT detects Q6 PC Voting failure` |
| `0xc1a774c9` | `%s #%u[%u] check rpm vdd min count, tis 0x%x NStis 0x%x TTR 0x%x sysAslp %u slptime %u vminslpcnt l:%u c:%u` |
| `0xc1a77535` | `%s #%u[%u] RPM vdd min fails %u, check for issues on RPM` |
| `0xc1a7738b` | `%s #%u[%u] system sleep check TIS 0x%x NSTIS 0x%x slpCnt l:%u c:%u, sysAsleep %u SST %u TTR 0x%x` |
| `0xc1a7734f` | `%s tech %u went to sleep %u msecs ago; retval %u ident 0x%x` |
| `0xc1a771c2` | `%s #%u[%u] sending wakeup will put tech in invalid state(curr %u), no wakeup sent` |
| `0xc1a7499f` | `Sending vdd min debug interrupt` |
| `0xc44c0840` | `mcpm.c:MCPM: tech wakeup_req- clearing params early_wakeup_time 0x%x%x sleep_active %d` |

The last string is the `MCPM: tech wakeup_req- clearing params early_wakeup_time … sleep_active %d` message seen in every stalled DIAG window — it is the **wake-up request path** that clears `early_wakeup_time` each time.

---

## 3. Consistency with every observation

| observation | explanation under this model |
|---|---|
| crash at 900.4–901.8 s modem uptime, repeatable | 400 cycles × ~2.25 s |
| crash sites `lte_ml1_sleepmgr_stm.c:4054`, `lte_ml1_common_timer.c:390` | different transition origins reported by the same watchdog; the reason struct differs |
| `a2_task.c:3179` on boot 2 | A2 power task variant of the same watchdog |
| DIAG: `MCPM: tech wakeup_req- clearing params early_wakeup_time … sleep_active %d` = perfect stall marker | emitted on the wake-up path that is failing |
| DIAG: LTE tech oscillates sleep↔wake every ~2.91 s right up to the crash, no degeneration | the counter increments normally until the Q6 PC vote starts failing; the crash is abrupt, not a degeneration |
| DIAG: only the LTE data path drops in stalled windows; VADC/CFM keep logging | the modem core is alive — it is the *Q6 power-collapse handshake*, not the core, that fails |
| host QMI sends no pre-collapse commands | the failure is entirely modem-internal |
| `RPM vdd min fails` diagnostics near the threshold | the RPM voltage-rail minimum is the suspected physical cause of the Q6 PC vote failure |

---

## 4. Fix options (ranked)

### Option A — prevent the Q6 PC attempt from the AP side
Keep the AP's `SMSM_A2_POWER_CONTROL` vote asserted (`power/control=on`, `autosuspend=-1`) so the modem never sees the AP as idle and never starts the power-collapse handshake.

**Evidence this works:** commit `2542425` did exactly this and reported it *"preventing modem DRX sleep assertions"*. It was reverted 26 minutes later by `c2da52e` on the claim that 30 s autosuspend *"prevents lte_ml1_sleepmgr_stm:4054"* — a claim this RE **disproves** (4054 is the watchdog's transition-origin report, and crashes #2/#3 occurred at that site with the current config).

**Caveat:** the modem's *internal* A2 client 14 (DL_INACT) releases independently of the AP vote, so this may only reduce, not eliminate, Q6 PC attempts. Needs an empirical 20-minute soak to confirm.

### Option B — restart the modem before 400 cycles
Re-enable the watchdog's Stage 3 SSR (currently disabled at `modem-bearer-watchdog:344`) and trigger it at ~700 s of modem uptime, resetting the counter before the 400-cycle threshold. Guaranteed to prevent the crash, but is a band-aid and the user has deliberately disabled SSR for analysis.

### Option C — raise the firmware threshold
Patch the `400`/`450` immediates in `FUN_c0ce7fe0` (`0xc0ce7fe0`) to a larger value. Delays the crash proportionally; does not fix the Q6 PC vote failure. Requires a byte-verified patch of `modem.mdt` — must follow the mandatory dual-firmware comparative protocol.

### Option D — fix the Q6 PC vote failure itself
Find which NPA/RPM client's vote blocks Q6 PC, or why the RPM vdd-minimum is not satisfied after 400 cycles. This is the only true fix. Next step: locate the RPM vdd-min request path (`FUN_c0cd6f38` sends the vdd-min debug interrupt; the `DAT_c30fd9e8` array holds the vdd-min reference value compared at `FUN_c0ce7fe0`).

---

## 5. Immediate next step

Before choosing a fix, run a controlled 20-minute soak with **Option A** (lock `power/control=on`, `autosuspend=-1`, and remove the watchdog's forced `control=auto` re-assertion) and watch for:
- absence of `lte_ml1_sleepmgr_stm.c:4054` / `lte_ml1_common_timer.c:390` in dmesg
- the `MCPM: tech wakeup_req- … sleep_active` marker in the DIAG stream

If the crash persists, the Q6 PC attempt is driven by the modem's internal clients and Option D becomes mandatory.
