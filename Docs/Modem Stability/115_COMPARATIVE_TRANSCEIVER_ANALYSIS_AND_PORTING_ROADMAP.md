# Engineering Report 115: Comparative Transceiver Analysis & Roadmap to Port UFI001B to HMU05

**Date:** September 14, 2026  
**Status:** Root Cause Isolated, Comparative Forensic Analysis Complete, Patch 41 Architecture Defined  
**Target Hardware:** Melbon HMU05 4G USB Dongle (Qualcomm MSM8916 + WTR1605 Transceiver + RF360 Front-End)  
**Host Environment:** OpenWrt 25.12.5 (Linux 6.12.94 aarch64)  
**Reference Firmware:** Melbon HMU05 Stock (`HIMI_U01_MODEM_V1.0  1  [Sep 09 2015 10:00:00]`)  
**Target Firmware:** Patched UFI001B (`MPSS.DPM.2.0.2.c1-00055-M8936FAAAANUZM-1`)  
**Network Operator:** Reliance Jio Infocomm Ltd. (MCC 405 MNC 861, Band 5 / Band 3)  

---

## 1. Executive Summary

By instrumenting and running the stock Melbon HMU05 modem firmware live on the target router, we proved that the physical hardware (WTR1605 transceiver, QFE2320 front-end module, antenna switches, and SIM interface) functions perfectly, registering to Reliance Jio with 91% signal quality and establishing full dual-stack IPv4/IPv6 internet connectivity.

Comparing the disassembly and memory layout of stock HMU05 against WIP UFI001B Patch 40 revealed the exact technical reasons why UFI001B remained in `state: searching` and crashed on active network scans:
1. **Transceiver Mismatch:** UFI001B was natively compiled for WTR4905 (chip ID mask `0x00200000`), containing 4,531 references to WTR4905 and zero static tables for WTR1605.
2. **Missing EFS Tables:** UFI001B's transceiver setup (`FUN_c0dacb54`) attempted to dynamically read WTR1605 PRX and DRX structures from EFS (`/rfc/0081/lte/...`). Because these files do not exist in the onboard EFS, the pointers `DAT_c29b28b4 (PRX)` and `DAT_c29b28b8 (DRX)` remained `0x00000000` (NULL).
3. **Transceiver Class Validation Rejection:** Function `FUN_c0dac83c` strictly checks `(chip_id & 0x00ff0000) == 0x00200000`. It rejected WTR1605 (`0x00290000`), returning failure (`0`).
4. **Active Network Scan Crash:** At `0xc0325c38` in `FUN_c0325b38` (LTE ML1 signal dispatcher), the Hexagon DSP executed `callr r3` where `r3 = memw(r17+#0x120)`. Because the callback pointer was NULL, an indirect branch to `0x00000000` triggered an unhandled exception.

---

## 2. Comparative Matrix: Stock HMU05 vs. WIP UFI001B

| Component / Subsystem | Melbon HMU05 Stock | WIP UFI001B (Patch 40) | Root Cause & Resolution |
| :--- | :--- | :--- | :--- |
| **Board Target** | `wtr1605_chile_rf360` | `wtr4905_chile_sawless` | Melbon hardware uses WTR1605 + RF360 front-end. UFI001B requires WTR1605 driver tables. |
| **Transceiver Chip ID** | `0x022902dd` (WTR1605) | `0x04200394` (WTR4905) | UFI001B validation hardcoded to `0x200000`. Must update mask check to `0x290000`. |
| **PRX Table Pointer (`DAT_c29b28b4`)** | Statically defined in Segment 19 (`0xc1e81c2c`) | NULL (`0x00000000`) | Extract 248-byte PRX table from HMU05 and populate `DAT_c29b28b4`. |
| **DRX Table Pointer (`DAT_c29b28b8`)** | Statically defined in Segment 19 (`0xc1e81d24`) | NULL (`0x00000000`) | Extract 268-byte DRX table from HMU05 and populate `DAT_c29b28b8`. |
| **Transceiver Init (`FUN_c0dacb54`)** | Initializes WTR1605 registers, PLL, and LO | EFS file load fails -> leaves NULL -> calls teardown `0xc0d7b930` | Patch `FUN_c0dacb54` to assign table addresses directly and return SUCCESS (`1`). |
| **Active Scan Dispatcher (`0xc0325c38`)** | Valid callback registered or NULL checked | `callr r3` with `r3 == 0x0` -> QDSP6 exception | Install `safe_callr_r3` trampoline: if `r3 == 0`, return cleanly; else `jumpr r3`. |
| **Reliance Jio Network State** | `registered-home`, attached, dual-stack IP | `searching`, unattached | With valid PRX/DRX tables, LTE ML1 can program WTR1605 synthesizer to lock onto EARFCN 2463/1451. |

---

## 3. Disassembly Analysis of Key Sites

### 3.1 Transceiver Class Validator (`0xc0dac83c` in UFI001B)
```assembly
c0dac83c: { p0 = cmp.eq(r2, #0x0); if (p0.new) jump:nt 0xc0dac8a8 }
c0dac840: { r0 = #0x1 }
c0dac844: { immext(#0x200000)
c0dac848:   r3 = ##0x200000 }         // Expected WTR4905 class mask
c0dac84c: { immext(#0xff0000)
c0dac850:   r4 = ##0xff0000 }         // Class mask (0x00ff0000)
...
c0dac89c: { r1 = and(r1, r4) }        // r1 = chip_id & 0x00ff0000
c0dac8a0: { p0 = cmp.eq(r1, r3)
c0dac8a4:   if (!p0.new) r0 = #0x0 }  // Rejects WTR1605 (0x290000 != 0x200000)!
c0dac8a8: { jumpr r31 }
```
**Fix:** Change `r3 = ##0x200000` to `r3 = ##0x290000` (or make the function return `r0 = #0x1` unconditionally).

### 3.2 Transceiver Table Assignment (`0xc0dacb74..0xc0dacb94` in UFI001B)
```assembly
c0dacb74: r1:0 = combine(#0x7, r16)
c0dacb78: immext(#0xc29b2880)
c0dacb7c: r4 = ##0xc29b28b4; r3:2 = combine(#0, #0x0)
c0dacb80: call 0xc0dac7a0    // EFS loader for PRX -> fails!
c0dacb84: r1:0 = combine(#0x7, r16)
c0dacb88: immext(#0xc29b2880)
c0dacb8c: r4 = ##0xc29b28b8; r3:2 = combine(#1, #0x0)
c0dacb90: call 0xc0dac7a0    // EFS loader for DRX -> fails!
```
**Fix:** Replace the failed EFS loader calls with direct pointer assignments:
```assembly
memw(##0xc29b28b4) = ##&wtr1605_prx_table;
memw(##0xc29b28b8) = ##&wtr1605_drx_table;
```

### 3.3 Active Scan NULL Dereference Guard (`0xc0325c38` in UFI001B)
Original code:
```assembly
c0325c2c: { r1:0 = combine(#0x1, r16); r2 = memw(r17+#0x124); r3 = memw(r17+#0x120) }
c0325c38: { callr r3 }      // Faults when r3 == 0
c0325c3c: { r17:16 = memd(r29+#0x8); r19:18 = memd(r29+#0x0) }
c0325c40: { dealloc_return }
```
**Fix:** Replace `callr r3` with a call to a trampoline `safe_callr_r3`:
```assembly
safe_callr_r3:
  { p0 = cmp.eq(r3, #0); if (p0.new) jumpr:t r31 }
  { jumpr r3 }
```
- If `r3 == 0`: immediately returns to `0xc0325c3c`, allowing clean deallocation and return.
- If `r3 != 0`: tail-calls `r3`, returning directly to `0xc0325c3c`.

---

## 4. Porting Roadmap (Patch 41)

1. **Extract Authentic WTR1605 Tables:**
   - Read the 248-byte PRX table from HMU05 `0xc1e81c2c`.
   - Read the 268-byte DRX table from HMU05 `0xc1e81d24`.
2. **Embed Tables into UFI001B:**
   - Write tables into Segment 26 (dedicated RW memory at `0xc4000000`).
3. **Patch UFI001B Code:**
   - **Patch 41A:** Populate `DAT_c29b28b4` and `DAT_c29b28b8` in `FUN_c0dacb54`.
   - **Patch 41B:** Adjust class validator in `FUN_c0dac83c` to accept `0x290000`.
   - **Patch 41C:** Install `safe_callr_r3` trampoline at `0xc0325c38`.
4. **Re-sign & Deploy:**
   - Regenerate SHA-256 hashes and update `modem.mdt` and `modem.b01`.
   - Deploy to `/lib/firmware/` on router and test live network attach on Reliance Jio.
