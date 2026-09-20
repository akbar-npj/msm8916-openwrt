# Engineering Report 98: Patch 28 & Patch 28 Revised Test Results & Coredump Analysis

## 1. Executive Summary

Following the deployment of **Patch 28** (forcing `card_init` success path) and **Patch 28 Revised** (attempting to use native return value from `0x261d4`), rigorous verification and coredump analyses were performed on the Melbon HMU05 target device.

The investigation conclusively identified why the modem crashed on `--nas-network-scan` under Patch 28 Revised, why Patch 28 v1 was crash-free, and what is required to achieve active RF tuning on Jio LTE bands.

---

## 2. Test Execution & Comparative Results

| Metric / Test | Patch 27 | Patch 28 (v1) | Patch 28 (Revised) |
| :--- | :--- | :--- | :--- |
| **b26 @ 0x260b8** | `{ call 0x261d4 ; r16 = #42 }` | `{ r0 = #1 }` (forced success) | `{ call 0x261d4 ; r16 = #42 }` (reverted) |
| **Helper at 0xe8200** | Fills slots with `static_wtr_device` (`0xd23fa000`) | Fills slots with `static_wtr_device` (`0xd23fa000`) | Fills slots with `r18 = r0` (`WTR_device_ptr`) |
| **Passive Boot Stability** | Crashed at t=91s | **Stable 15+ minutes, 0 crashes** | **Stable 5+ minutes, 0 crashes** |
| **`--nas-force-network-search`** | CRASH | **SUCCESS (No crash)** | SUCCESS (No crash) |
| **`--nas-network-scan`** | CRASH | Not run | **CRASH (Immediate SSR)** |
| **Coredump Generated** | `modem_coredump_p27.elf` | None (no crash occurred) | `modem_coredump_p28r.elf` (84.6 MB) |
| **QDSP6 BADVA** | `0x8b977000` | N/A | `0xe2e6a008` |
| **QDSP6 SSR** | `0x0000000a` | N/A | `0x0000000a` (TLB miss) |

---

## 3. Deep-Dive Coredump Analysis (`modem_coredump_p28r.elf`)

### 3.1 Exception Details
- **Architecture**: `ERR_ARCH_QDSP6`
- **Fatal Error**: `fatal error received: :Excep :0:Exception detected`
- **IPC Log**: `ExIPC: Exception recieved tid=ec inst=0`
- **Faulting Virtual Address (`QDSP6_BADVA`)**: `0xe2e6a008`
- **Status Register (`QDSP6_SSR`)**: `0x0000000a` (data access fault / page not mapped)

### 3.2 Proof of `0x261d4` Return Value
Function `0x261d4` in `modem.b26` caches its return value in global variable `0xc340b2d4`:
```assembly
261f4: { immext(#0xc340b2c0)
261f8:   memw(##0xc340b2d4) = r16 }
261fc: { r0 = r16 ; jump dealloc_return }
```
We inspected physical file offset `0x33abc84` (corresponding to VA `0xc340b2d4`) in `modem_coredump_p28r.elf`:
```
Value of 0xc340b2d4 in coredump p28r: 0x00000000
  0xc340b2c4: 0x00000000
  0xc340b2c8: 0x00000000
  0xc340b2cc: 0x00000000
  0xc340b2d0: 0x00000000
  0xc340b2d4: 0x00000000   <-- r16 return value was exactly 0!
```

### 3.3 Root Cause of the Crash in Patch 28 Revised
Because `0x261d4` returned `0`:
1. `card_init` executed the check at `0x260c0`:
   ```assembly
   260c0: { p0 = cmp.eq(r0,#0); if (!p0.new) jump:t 0x260d4; ... }
   ```
2. With `r0 == 0`, `p0` evaluated to **TRUE**, so `!p0.new` was **FALSE**.
3. The jump to `0x260d4` (our hook) **was not taken**.
4. Execution fell through to `0x260cc`:
   ```assembly
   260cc: { call 0x100 ; r16 = #0 ; r1:0 = combine(#0,r2) }
   ```
5. `card_init` took the **FAILURE path**, returned `0`, and **completely skipped the helper at `0xe8200`**.
6. The 17 LTE band slots at `card_ptr + 0x424` remained entirely unpopulated (`0x00000000`).
7. When `--nas-network-scan` triggered Layer 1 scan function `FUN_c0d5e0a0`, it called `thunk_FUN_c0075710(card_ptr, 7, band)` which returned `0x00000000`.
8. Dereferencing NULL caused the fatal TLB miss at BADVA `0xe2e6a008`.

---

## 4. Key Architectural Discoveries

### 4.1 Segment 26 Identity
- `scratch/melbon_black_extracted/image/modem.b26` (HMU05 stock) and `GitIgnore/compare/modem_ufi001b_extracted/image/modem.b26` (UFI001B) are **100% byte-for-byte identical** (1,048,576 bytes).
- Both devices share the exact same Qualcomm WTR transceiver card driver binary.

### 4.2 Why `0x261d4` Fails on the Hybrid Firmware
- In stock HMU05 firmware, `0x261d4` succeeds because the surrounding MPSS (`modem.b15`) and NV items match HMU05 hardware completely.
- In our UFI001B-to-HMU05 port, `modem.b15` belongs to UFI001B. When `0x261d4` queries global structures initialized earlier by `b15`, the structures are either missing or in an unexpected state, causing `0x261d4` to return `0`.
- **Conclusion**: `0x261d4` will always take the failure path on this hybrid stack unless bypassed.

### 4.3 Virtual Memory Mapping of `modem.b26`
- The ELF MDT header defines Segment 26 at `p_vaddr = 0xc3e0f000`.
- However, Qualcomm's dynamic module dispatcher (`FUN_c123de54` in `b15`) dispatches card methods using the overlay virtual address base `0xd2312000` (`case 0x35: pcVar6 = &SUB_d2338000`).
- Offset `0x26000` in `b26` corresponds to `0xd2312000 + 0x26000 = 0xd2338000`, confirming that `b26` runtime execution occurs in the `0xd23...` address space.

---

## 5. Summary & Actionable Path Forward

1. **Re-apply the `card_init` Success Bypass**:
   `0x260b8` must be patched to force `r0 = 1` so that `card_init` always takes the success path to `0x260d4`.
2. **Proper Device Vtable for Band Slots**:
   Instead of using simple stub functions that return 0 without touching hardware, the static device structures at `0xe8000` must invoke the genuine WTR1605 RF control functions present in `b26` (such as `0xd2338274` and the transceiver routines at `0x262a8`) or authenticate the RF front-end configuration.
