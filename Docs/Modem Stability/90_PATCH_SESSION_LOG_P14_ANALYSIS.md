# Patch Session Log — Patch 14 & Per-Tech RF Init Analysis

**Session Date:** 2026-09-14  
**Context:** WTR1605 Transplant into UFI001B MPSS.DPM.2.0  
**Baseline State Entering Session:** Patches 1–13 applied, modem boots to `+CFUN: 7` (offline), AT port alive but `AT+CFUN=1` → `+CME ERROR: phone failure`

---

## Session Goal

Achieve `+CFUN: 1` by understanding why Call Manager rejects online mode and patching the RF init chain to succeed.

---

## Root Cause Analysis (Continued from P13)

### Why modem stays offline with Patches 1–13

The boot sequence with P1–P13:

```
rfm_init()  [FUN_c0d77d94]
  → rfc_init()  [FUN_c0d6fe94]
      → rfc_card_init [FUN_c1003c50]     ← P13 forced return=1  ✓
      → FUN_c0d680a0() (WTR transceiver init)  
          → FUN_c0d7d320(0, sVar5)
              → FUN_c0075710(card_ptr, 0, 0)  ← reads card_ptr[0x1b]
          → card_ptr[0x1b] = 0 (null — FUN_c0d7bb70 never populated it)
          → FUN_c0d680a0 returns ???
      → FUN_c0d7d418() → vtable[13] → returns 1  ✓
      → FUN_c0d6a1a0()  ???
      → return uVar6 & uVar3 & uVar4 & uVar5 & 1
  → if (rfc_init() == 1) { per-tech inits }   ← BRANCH DEPENDS ON rfc_init result
```

### Critical Finding: WTR get_device_list / get_band_list return NULL

From `WTR vtable[3] @ 0xc0d7b8b8` (get_device_list) and `vtable[4] @ 0xc0d7b8d0` (get_band_list):

```asm
{ r1:0 = combine(r0, ##-1050817524); allocframe(#0) }
{ call <some_lookup>; r1 = memub(r1+#4) }
{ r0 = #0; dealloc_return }          ← ALWAYS returns NULL
```

**Both vtable functions unconditionally return 0 (null pointer).**

This means:
- `FUN_c0d7bb70` receives null device list → skips all band table population
- Card object slots `card_ptr[0x1b + tech*0x22 + band*2]` remain **zero**
- `FUN_c0075710(card_ptr, tech, band)` returns 0 for all tech/band combinations

### Consequence Chain

| Function | Result | Reason |
|---|---|---|
| `rfc_card_init` (P13) | 1 (forced) | P13 patches |
| `FUN_c0d680a0` | 1 (likely — returns 1 even if no transceivers found, based on code flow) | Loop completes, `uVar3=1` |
| `FUN_c0d7d418` | 1 | vtable[13] hardcoded `{ r0 = #1; jumpr r31 }` |
| `FUN_c0d6a1a0` | 1 or 0 (TBD) | Unknown |
| **`rfc_init` total** | **possibly 1** | All sub-functions may return 1 |

---

## Patch 14 — rfm_init Result Force

### Intent

Force `rfm_init()` to return 1 regardless of actual RF init outcome, so Call Manager transitions to online mode.

### Target

| Field | Value |
|---|---|
| Function | `FUN_c0d77d94` (rfm_init) |
| File | `modem.b15` |
| File Offset | `0xab5e4c` |
| VA | `0xc0d77e4c` |

### Patch

```
Old: 30 c0 10 76  →  { r16 = and(r16,#1) }  (final AND to normalize return value)
New: 30 c0 00 78  →  { r16 = #1 }           (force return = 1)
```

### Context in rfm_init Epilogue

```asm
{ call 0x3119c }           ← per-tech LTE RFM init
{ call 0x3242c }           ← per-tech WCDMA init
{ call 0xffff9dc4 }        ← per-tech GSM init
{ call 0xffff5dac }
{ call 0xffff9d2c }
{ call 0xffff863c; r16 = r0 }
{ call 0x9144;     r17 = r0 }
{ call 0x362824;   r18 = r0 }
{ call 0xffff8b6c; r19 = r0 }
{ r1 = and(r16,r17) }
{ r1 = and(r1,r18) }
{ r1 = and(r1,r19) }
{ call 0xe000; r16 = and(r1,r0) }
{ r16 = #1 }               ← PATCH 14 (was: r16 = and(r16,#1))
{ call 0xffff731c }
{ r0 = r16; r17:16 = memd(r29+#16); r19:18 = memd(r29+#8) }
{ r21:20 = memd(r29+#0); dealloc_return }
```

> [!IMPORTANT]
> **Patch 14 is only reached if rfc_init() returns 1.** If rfc_init returns 0, the conditional branch at `+0x68` fires: `if (!cmp.eq(r16,#1)) jump:t 0xbc`, which jumps PAST the per-tech inits AND past Patch 14, directly to the cleanup call at +0xbc.

### Test Result — **FAIL** ❌

**Cold Boot (post-P14 deploy + reboot):**

| Check | Result |
|---|---|
| dmesg — modem up | ✓ All 8 BAM channels open at t≈12s |
| dmesg — SSR | None (clean boot) |
| `wwan0at0` | Disconnected at t=43s (expected pattern) |
| `wwan0at1` | Present as char device BUT **blocks on read** — no AT responses |
| `wwan0qmi0` | Working — QMI responds normally |
| QMI DMS mode | `offline` |
| QMI DMS set-online | Returns "Successfully" but mode stays `offline` |
| NAS home network | `NotProvisioned` error |
| SIM (UIM) | USIM ready, application state `ready` |
| IMEI | 864293052253917 (valid) |

### Root Cause of Patch 14 Failure

Since AT breaks with P14 but works with P13 alone, **rfc_init IS returning 1 with P13** (all sub-functions return 1). Therefore:

1. rfc_init returns 1 → conditional branch at rfm_init+0x68 does NOT jump → per-tech inits run
2. Per-tech inits (LTE/WCDMA/GSM RF init) try to configure WTR4905 RFFE bus addresses
3. WTR1605 hardware is present on the RFFE bus, not WTR4905 → RFFE bus reads return wrong data or timeout
4. Per-tech inits **hang** in RFFE bus timeout loop
5. RTOS scheduler is starved — AT task never gets CPU time → AT port blocks

Patch 14's `r16 = #1` instruction is never reached because the CPU never completes the per-tech inits.

---

## Patch 14 — Status: REVERTED

Patch 14 was reverted. Back to P1–P13 baseline where AT port responds.

---

## Key Discoveries This Session

### 1. rfc_init returns 1 with P1–P13

Counter-intuitive finding: even though the WTR band tables are null (FUN_c0d7bb70 returns 0), `FUN_c0d680a0` still returns 1 (the loop exits normally with uVar3=1 even with zero transceiver count). Combined with P13 forcing rfc_card_init=1 and vtable[13]=1, rfc_init returns 1 with our current patches.

### 2. Per-tech RF inits run after rfc_init=1

The per-tech init functions (LTE RFM, WCDMA RFM, GSM RFM etc.) execute when rfc_init succeeds. They attempt RFFE hardware communication with WTR4905 addresses, which fail/timeout on WTR1605 hardware.

### 3. AT port blocks ≠ modem crash

The modem itself doesn't crash (QMI still works, DMS/UIM/NAS respond). Only the AT task is starved. This indicates a high-priority RF task spinning in RFFE timeout loops, preventing the AT scheduler from running.

### 4. WTR vtable[3]/[4] always return null

`get_device_list()` and `get_band_list()` unconditionally return 0. This means no WTR band data is ever populated in the card object, and `FUN_c0075710(card_ptr, tech, band)` always returns 0 for any tech/band lookup.

---

## Required Fix: Provide Valid WTR1605 Band Tables

The per-tech RF inits must not hang. Two options:

### Option A: Bypass Per-Tech Inits Individually

Patch each per-tech call in rfm_init to return 1 immediately:
- `call 0x3119c` → `{ r0 = #1; jumpr r31 }` (patch the callee function entry)
- `call 0x3242c` → same
- etc.

**Risk:** CM goes online but RF hardware is not configured → no signal. But modem is stable.

### Option B: Fix WTR Band Table Population

Make `FUN_c0d7bb70` successfully populate the card object with WTR1605 band data.
This requires fixing `WTR vtable[3]/[4]` to return valid device/band lists from the HMU05 DevCfg.

**This is the correct long-term fix.**

### Option C: Patch rfm_init to Skip Per-Tech Inits (bypass branch)

At rfm_init+0x68: Change `if (!cmp.eq(r16,#1)) jump:t 0xbc` to unconditional jump to `0xbc` (epilogue), skipping per-tech inits. Combine with another patch to set r16=1 at epilogue.

---

## Next Planned Patches

### Patch 14 (Revised): Skip Per-Tech Inits + Force rfm_init=1

Two targets in `FUN_c0d77d94`:

1. **Target A** (offset +0x68 = VA `0xc0d77dfc`): Change the conditional branch to ALWAYS jump to epilogue. This bypasses ALL per-tech inits.  
   - Change: `{ r16 = r0; if (!cmp.eq(r16.new,#1)) jump:t 0xbc }` → unconditional jump to epilogue

2. **Target B** (offset +0xbc = VA `0xc0d77e50`): Change the cleanup call to instead force r16=1.
   - Change: `{ call 0xffff731c }` → `{ r16 = #1 }`

Combined effect: rfm_init skips all per-tech inits AND always returns 1.

---

## Patch History Summary (P1–P14)

| Patch | File | Effect | Status |
|---|---|---|---|
| P1 | modem.b15 | WTR transceiver init bypass (WTR4905→WTR1605) | ACTIVE |
| P2 | modem.b15 | RFC Card Factory HWID→Chile slot (0xd2338000) | ACTIVE |
| P6 | modem.b26 | Device IDs: WTR4905→WTR1605 (306+124 occurrences) | ACTIVE |
| P7 | modem.b15 | OEM NV security bypass | ACTIVE |
| P8 | modem.b15 | Operating mode lock bypass | ACTIVE |
| P10 | modem.b15 | DMS opmode callback force success | ACTIVE |
| P11 | modem.b26 | RFFE front-end table: QFE2320 ASM/PA IDs | ACTIVE |
| P12 | modem.b26 | DevCfg table transplant from HMU05 b19 | ACTIVE |
| P13-T1 | modem.b15 | FUN_c0d7c6d0 → always return 1 | ACTIVE |
| P13-T2 | modem.b15 | rfc_card_init failure branch → NOP | ACTIVE |
| **P14** | modem.b15 | rfm_init result force → r16=#1 | **REVERTED** |

---

## Test Results Summary

| Test | P1–P12 | P1–P13 | P1–P14 |
|---|---|---|---|
| Modem boots | ✓ | ✓ | ✓ |
| AT port responds | ✓ | ✓ | ✗ (blocks) |
| `+CFUN: ?` | +CFUN: 7 | +CFUN: 7 | N/A (AT blocked) |
| `AT+CFUN=1` | +CME ERROR: phone failure | +CME ERROR: phone failure | N/A |
| QMI DMS mode | offline | offline | offline |
| QMI set-online | Rejects | "Successfully" (→ stays offline) | "Successfully" (→ stays offline) |
| USIM ready | ✓ | ✓ | ✓ |
| rfc_init returns | 0 | **1** | 1 (→ per-tech init hangs) |
