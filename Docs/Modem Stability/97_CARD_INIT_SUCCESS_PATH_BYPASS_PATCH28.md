# Doc 97: Card Init Success Path Bypass — Patch 28

**Date:** 2026-09-14  
**Session:** Patch 28 design session  
**Goal:** UFI001B modem.bin → HMU05 (MSM8916/WTR1605)

---

## 1. Patch 27 Post-Mortem (crash forensics)

### 1.1 Crash Symptom
After Patch 27 (card routing + LTE band slots helper), the modem booted cleanly and
entered `searching` state. However triggering an explicit network scan crashed the modem:

```
[   91s] qcom-q6v5-mss: fatal error received: :Excep :0:Exception detected
[  135s] qcom-q6v5-mss: fatal error received: :Excep :0:Exception detected
```

QuRT exception frame (from coredump at `/tmp/modem_coredump_p27.elf`):
```
QDSP6_BADVA : 0x8b977000   ← faulting virtual address
QDSP6_SSR   : 0x0000000a   ← cause code 0xa = TLB miss / data access fault
QDSP6_SP    : 0x8a99f328   ← AMSS0 task stack (physical DDR range)
QDSP6_PC    : 0x00000000   ← not captured (context lost at exception)
TID         : 0xec (= 236)
Task name   : AMSS0
```

### 1.2 Root Cause Analysis

**Key observation:** `0xd23fa000` (our static_wtr_device written by the Patch 27 helper)
was **NOT found** anywhere in the 84 MB coredump. This proved the helper at `0xe8200`
never fired.

**Trace of card_init exit conditions:**

```
b26 @ 0x260b8:  { call 0x261d4 ; r16 = #0x2a }
b26 @ 0x260bc:  (second word of above packet — r16=#42)
b26 @ 0x260c0:  { p0 = cmp.eq(r0,#0x0); if (!p0.new) jump:t 0x260d4 ; ... }
```

- **If `0x261d4` returns 0** → `p0=true`, `!p0=false` → **no jump** → falls to 0x260cc (ERROR PATH)
- **If `0x261d4` returns non-zero** → `p0=false`, `!p0=true` → **jump to 0x260d4** (Patch 27 hook)

**Function at b26:0x261d4** reads the global init-count at `0xc340b2d4`:
- If already initialized (non-zero): return cached value immediately
- If first call (zero): call `0x38` (dealloc_return stub) then `0x26204`

**Function at b26:0x26204** → calls b26:0x268:
```
b26 @ 0x268:  { immext(#0xeec97f00) ; jump 0xeec98194 }
```

`0xeec98194` is the **WTR4905-specific firmware stub address** — a peripheral memory
range that is NOT mapped in the HMU05/WTR1605 configuration.

**Result chain:**
1. `0x261d4` calls `0x268` which jumps to unmapped `0xeec98194`
2. TLB miss → `0x261d4` crashes or returns 0
3. card_init takes **failure path** (r16=0, returns 0)
4. Patch 27 hook at `0x260d4` **is never reached**
5. `card_ptr + 0x424` band slots remain NULL (all zeros)
6. LTE Layer 1 band scan finds NULL device pointers → crash at `0x8b977000`
   when scan code dereferences `param_1+0x7e4` (NULL band frequency table)

---

## 2. Patch 28: Force card_init Success Path

### 2.1 Strategy
Replace the WTR4905-specific init call at `b26:0x260b8` with `{ r0 = #1 }`.
This forces `r0 = 1` (non-zero), making the success-path branch fire unconditionally.

### 2.2 Change

| Location | Before | After |
|----------|--------|-------|
| `modem.b26 @ 0x260b8` | `5a 00 40 8e` = `{ call 0x261d4; r16=#42 }` | `20 c0 00 78` = `{ r0 = #1 }` |

`modem.b26 @ 0x260bc` remains `7800c550` = `{ r16 = #42 }` (already correct).

### 2.3 New Execution Flow

```
0x260b8:  { r0 = #1 }          ← skip WTR4905 init; force r0=1
0x260bc:  { r16 = #42 }        ← success return code
0x260c0:  p0 = (r0==0) = false
          !p0 = true → JUMP to 0x260d4
0x260d4:  { jump 0xe8200 }     ← Patch 27 hook fires!
0xe8200:  helper:
          - loads GP+0x40b0 = card_ptr
          - if card_ptr == NULL: skip
          - fills card_ptr+0x424..+0x4b4 (17 slots) with static_wtr_device
          - dealloc_return with r0=r16=42
```

### 2.4 Dual Effect
1. **Helper fires** → `card_ptr + 0x424` populated with `static_wtr_device` (0xd23fa000)
2. **WTR4905 hardware access crash avoided** (0x261d4 never called → 0xeec98194 never accessed)

### 2.5 Segment Hash Update
- `modem.b26` SHA256: `7ce32c5a773565fa35a6ecdf4132ebe19c881355e512cc6ceb6d90f4d2bb7859`
- Hash updated in `modem.b01` at offset `0x368`
- `modem.mdt` rebuilt (8284 bytes)
- All 28 segments: **20 MATCH, 8 ZERO/BSS, 0 MISMATCH** ✓

---

## 3. Open Questions After Patch 28

### 3.1 `FUN_c0d7c224` Return Value
With card_init returning 42:
```c
iVar2 = (**(code **)(*param_1 + 0x10))(param_1, iVar1)  // = 42
if ((iVar2 == 1) && (param_1[0x109] != 0) && ...) {     // 42 != 1: SKIP
```
The `iVar2==1` check fails, so `param_1[0x16f]` (LTE CA slot) is not set.
This may be benign (carrier aggregation setup), needs monitoring.

### 3.2 Band Device Slot Check
The `FUN_c0d7bb70` band table allocation (`param_1+0x7e4`) still fails because
`get_band_list()` on the WTR transceiver returns NULL. This means:
- `param_1+0x7dc = 0` (no band-capable devices in WTR table)
- `param_1+0x7e4 = NULL` (no band frequency table)

The scan code may try to use this table. If so, it will need a separate fix.
Monitor: does modem crash or succeed with band slots populated?

### 3.3 static_wtr_device Vtable Methods
Our static device at `0xd23fa000` has:
- Slot 4 (`+0x10`): `static_get_device_method` → returns PRX/DRX sub-device
- All other slots: `STUB_VA` (safe no-op)

If scan code calls other methods (slot 5, 6...), they return 0 safely.
But if a method is expected to allocate/init a buffer and returns 0 (NULL ptr),
subsequent dereference would crash. Monitor coredump BADVA on next crash.

---

## 4. Test Results (to be updated)

| Test | Result |
|------|--------|
| Boot with Patch 28 | ⏳ Pending |
| card_ptr+0x424 populated (0xd23fa000 in coredump) | ⏳ Pending |
| No crash on `--nas-force-network-search` | ⏳ Pending |
| LTE signal: RSRP/RSRQ appears | ⏳ Pending |
| Registration: `registered-home` | ⏳ Pending |
| Data session / ping 8.8.8.8 | ⏳ Pending |

---

## 5. Patch Inventory

| Patch | File | Offset | Change | Purpose |
|-------|------|--------|--------|---------|
| 18 | b19 | various | Card routing buffer transplant | HMU05 RF routing |
| 21 | b26 | 0x260f4 | copy size 1968→1688 bytes | WTR1605 card precision |
| 23 | b26 | 0x260f4 + buf | Buffer source 0xd23f8d20→0xd23f2500 | Use HMU05 card routing |
| 25 | various | — | Multi-RAT stubbing (WCDMA/CDMA/TDS) | Jio LTE-only |
| 26 | b15 | 0xab5e0c-0xab5e28 | Unlock rfm_lte_init | Enable LTE RF init |
| 27 | b26 | 0x260d4 + 0xe8000-0xe8230 | Hook + static device + band slots | LTE band device population |
| **28** | **b26** | **0x260b8** | **r0=#1 (skip WTR4905 init)** | **Force helper to fire** |
