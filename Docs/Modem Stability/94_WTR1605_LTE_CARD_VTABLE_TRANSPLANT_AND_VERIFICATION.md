# Patch Session Log 94 — WTR1605 True LTE Card Method Transplant & Cold Boot Verification

**Date:** 2026-09-14  
**Target Hardware:** Melbon HMU05 4G Dongle (MSM8916 + WTR1605 transceiver + QFE2320 RF360 front-end)  
**Base Firmware:** UFI001B MPSS.DPM.2.0.2.C1 (donor)  
**Status:** Patch 21 Deployed & Verified with Cold Hardware Reset  

---

## 1. Breakthrough Discovery: GSM vs LTE Virtual Methods

During verification of Patch 20:
1. When requesting `--nas-network-scan`, the Hexagon DSP raised an exception (`fatal error received: :Excep :0:Exception detected`), recovering through SSR.
2. In-depth decompilation of HMU05 `modem.b18` vtables uncovered the architectural root cause:
   - Vtable at `0xc1c12188` (offset `0x712188` in `modem.b18`): belongs to `rfc_wtr1605_chile_rf360_gsm_ag` (**GSM**)!
     - Its method `FUN_c118794c` (2,996 bytes) only contained GSM 2G frequency bands and lacked LTE Band 5, Band 3, and Band 40.
   - Vtable at `0xc1c12950` (offset `0x712950` in `modem.b18`): belongs to `rfc_wtr1605_chile_rf360_lte_ag` (**LTE**)!
     - Virtual method at `vtable[4]` (Slot 2) points to `0xc118a9d0` (mode 0 thunk calling `0xc118a9dc`).
     - Virtual method at `vtable[5]` (Slot 3) points to `0xc118ac98` (mode 1 thunk calling `0xc118a9dc`).
     - Core LTE method: `0xc118a9dc` (offset `0xf039dc` in HMU05 `modem.b16`).

---

## 2. Analysis of Authentic HMU05 LTE Method (`0xc118a9dc`)

1. **Self-Contained Architecture:**
   - Contiguous 700 bytes (`0x2bc`) from `0xf039dc` to `0xf03c98`.
   - **Zero external calls:** 100% leaf function. All branches are internal PC-relative jumps.
   - Operates on Qualcomm LTE band config structure (`r1+#4` for PRX/DRX path, `r1+#12` for band class, `r1+#8` for band number).
2. **Table Pointers:**
   - Contains exactly 26 table pointers (`immext`) in the range `0xc1e81b68` to `0xc1e829cc`.
   - All 26 pointers point directly into the transplanted 28 KB WTR1605 RF table block at `modem.b26` offset `0xe0000`.
   - Relocation formula: $\text{VA}_{\text{new}} = \text{VA}_{\text{old}} + 0x10574690$ (range `0xd23f61f8` to `0xd23f705c`).
3. **Mode Switching:**
   - Parameter `r3`:
     - `r3 == 0`: Mode 0 table pointer.
     - `r3 == 1`: Mode 1 table pointer.

---

## 3. Implementation (Patch 21)

1. **Extraction & Relocation:**
   - Extracted 700 bytes from HMU05 `modem.b16` at offset `0xf039dc`.
   - Relocated all 26 table pointers by $+0x10574690$ using exact Hexagon `immext` bitfield re-encoding.
   - Placed at `modem.b26` offset `0xe7000` (VA `0xd23f9000`).
2. **Thunk Construction:**
   - Placed at `modem.b26` offset `0xe7300`:
     - `thunk_mode0` (`0xe7300` / VA `0xd23f9300`): `{ r3 = #0 } ; { jump 0xe7000 }`
     - `thunk_mode1` (`0xe7308` / VA `0xd23f9308`): `{ r3 = #1 } ; { jump 0xe7000 }`
3. **Vtable Repointing (`0x280f8` in `modem.b26`):**
   - `vtable[2]` (offset `0x28100`, Slot 2): `0xd23f9300` (thunk_mode0)
   - `vtable[3]` (offset `0x28104`, Slot 3): `0xd23f9308` (thunk_mode1)
   - `vtable[4]` (offset `0x28108`, Slot 4): `0xd23f9308` (thunk_mode1)
   - `vtable[5]` (offset `0x2810c`, Slot 5): `0xd2339f78` (original pristine TDD method)
   - `vtable[6]` (offset `0x28110`, Slot 6): `0xd2339f68` (original pristine stub)
4. **Legacy Direct Jump Overrides:**
   - `0x26cc0`: `{ r3 = #0 } ; { jump 0xe7000 }`
   - `0x27f58`: `{ r3 = #1 } ; { jump 0xe7000 }`
   - `0x27f60`: `{ r3 = #1 } ; { jump 0xe7000 }`
   - `0x27f78`: pristine uncorrupted bytes (`98 40 02 10 00 c0 00 78`)
5. **MBA Authentication & Verification:**
   - Segment 26 hash re-calculated and written to `modem.b01` and `modem.mdt`.
   - Verification of all 28 ELF segments: 20 MATCH, 8 ZERO/BSS, 0 MISMATCH (PASS).
6. **Deployment & Cold Reboot:**
   - Synced to `/lib/firmware/ufi001b_patched/` and `/lib/firmware/`.
   - Executed cold SoC reset (`sync && reboot`).
