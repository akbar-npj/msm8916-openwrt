# Engineering Report 103: Patch 31 - Active WTR1605 RF Transceiver Hardware Tuning

**Date:** 2026-09-14  
**Target Hardware:** Melbon HMU05 4G Dongle (MSM8916 + WTR1605 transceiver + QFE2320 RF360 front-end)  
**Base Firmware:** UFI001B MPSS.DPM.2.0.2.C1 (donor baseband)  
**Carrier:** Reliance Jio 4G (MCC 405 MNC 861, Bands 3/5)  
**Status:** Patch 31 Deployed, MBA Hash Verified (20 MATCH, 8 ZERO/BSS, 0 MISMATCH), Cold Reboot Executed  

---

## 1. Executive Summary & Problem Analysis

In **Patch 30**, we achieved 100% modem power stability and bypassed the card initialization failure caused by `0x261d4` returning 0. However, the modem remained in state `searching` and reported `NoNetworkFound`.

Investigation into the baseband disassembly and runtime execution revealed the exact reason:
1. **Transceiver Stubs Return 0**:
   In `static_wtr_vtable` (`0xe8040`) and `subdev_vtable` (`0xe8140`), slots 2 and 3 were stubbed with `RET0_STUB` (`{ r0 = #0 ; jumpr r31 }`). When Layer 1 RF driver queried the device for the transceiver tuning tables (frequency translation, PLL dividers, LO control, and RFFE commands), it received `0`, so no actual RF synthesizer commands were sent to the physical WTR1605 chip.
2. **Broken Relocation Delta in Residual Code**:
   In earlier experiments (Patch 21), the authentic 700-byte LTE method from HMU05 (`0xc118a9dc`) had been relocated using an erroneous delta (`+0x10574690`) based on a fictitious virtual address base (`0xd2312000`). Consequently, all 26 table pointers in the code pointed to non-existent memory (`0xd23f6...`).
3. **Card Vtable Desynchronization**:
   The LTE Card vtable at `0x280f8` still retained pointers to unmapped addresses (`0xd23f9300` and `0xd23f9308`).

---

## 2. Technical Solution & Implementation (Patch 31)

### 2.1 Genuine Relocation Delta Calculation
The authentic WTR1605 RFC configuration tables reside in `modem.b26` at file offset `0xe0000..0xe7000`:
- **Transplanted Target in `modem.b26`**: File offset `0xe0000`, true virtual address:
  $$\text{VA}_{\text{target}} = 0xc3e0f000 + 0xe0000 = 0xc3eef000$$
- **Source in HMU05 `modem.b19`**: File offset `0x241970`, source virtual address:
  $$\text{VA}_{\text{source}} = 0xc1c3c000 + 0x241970 = 0xc1e7d970$$
- **Exact Relocation Delta**:
  $$\Delta = \text{VA}_{\text{target}} - \text{VA}_{\text{source}} = 0xc3eef000 - 0xc1e7d970 = \mathbf{+0x02071690}$$

Verification:
- HMU05 pointer `0xc1e81b68` + `0x02071690` = `0xc3ef31f8` (file offset `0xe41f8` in `b26`).
- Physical inspection of `b26` at offset `0xe41f8` confirmed an exact 100% byte-for-byte match with HMU05 `modem.b19` at offset `0x245b68` (`dd 02 29 02` = WTR1605 chip ID `0x022902dd`).
- Highest pointer `0xc1e829cc` + `0x02071690` = `0xc3ef405c` (file offset `0xe505c`), matching HMU05 `b19` at `0x2469cc`.

### 2.2 Relocated Authentic LTE Method (`0xe7000`)
- Extracted 700 bytes from HMU05 `modem.b16` at offset `0xf039dc` (`rfc_wtr1605_chile_rf360_lte_ag::get_band_info`).
- Relocated all 26 Hexagon `immext` table pointers by $+0x02071690$.
- Injected the relocated function into `modem.b26` at offset `0xe7000` (VA `0xc3ef6000`).

### 2.3 Compound Mode Thunks (`0xe7300`)
Constructed compact Hexagon instruction packets:
- `thunk_mode0` (`0xe7300` / VA `0xc3ef6300`):
  `{ r3 = #0 ; jump 0xe7000 }` (bytes: `80 c0 23 16`)
- `thunk_mode1` (`0xe7304` / VA `0xc3ef6304`):
  `{ r3 = #1 ; jump 0xe7000 }` (bytes: `7e c1 23 16`)
- `thunk_mode2` (`0xe7308` / VA `0xc3ef6308`):
  Safe return-0 stub (`{ p0 = cmp.eq(r2,#0) ; jumpr r31 ; r0 = #0 ; if (!p0.new) memw(r2+#0) = #0 }`, bytes: `00 40 02 75 00 40 9f 52 00 40 00 78`)

### 2.4 Repointing Vtables
1. **LTE Card Vtable (`0x280f8`)**:
   - `vtable[2]` (`0x28100`): `0xc3ef6300` (`thunk_mode0`)
   - `vtable[3]` (`0x28104`): `0xc3ef6304` (`thunk_mode1`)
   - `vtable[4]` (`0x28108`): `0xc3ef6308` (`thunk_mode2`)
2. **`static_wtr_vtable` (`0xe8040`)**:
   - Slot 2 (`+0x08`): `0xc3ef6300` (`thunk_mode0`)
   - Slot 3 (`+0x0c`): `0xc3ef6304` (`thunk_mode1`)
   - Slot 4 (`+0x10`): `0xc3ef7080` (`METH_VA`: `static_get_device_method`)
   - Slot 7 (`+0x1c`): `0xc3ef7188` (`RET1_VA`: tech/band config query returns 1)
   - Slot 12 (`+0x30`): `0xc3ef7188` (`RET1_VA`: RF path operation check returns 1)
   - Other slots: `0xc3ef7180` (`RET0_VA`)
3. **`subdev_vtable` (`0xe8140`)**:
   - Slot 2 (`+0x08`): `0xc3ef6300` (`thunk_mode0`)
   - Slot 3 (`+0x0c`): `0xc3ef6304` (`thunk_mode1`)
   - Slot 4 (`+0x10`): `0xc3ef6304` (`thunk_mode1`)
   - Slot 7 (`+0x1c`): `0xc3ef7188` (`RET1_VA`)
   - Slot 12 (`+0x30`): `0xc3ef7188` (`RET1_VA`)
   - Other slots: `0xc3ef7180` (`RET0_VA`)

### 2.5 Legacy Direct Entrypoints
- `0x26cc0`: `{ r3 = #0 } ; { jump 0xe7000 }` (bytes: `03 c0 00 78 9e c1 18 58`)
- `0x27f58`: `{ r3 = #1 } ; { jump 0xe7000 }` (bytes: `23 c0 00 78 52 f8 17 58`)
- `0x27f60`: `{ r3 = #1 } ; { jump 0xe7000 }` (bytes: `23 c0 00 78 4e f8 17 58`)

---

## 3. Verification & Integrity Results

1. **Segment 26 SHA256**: `54f62b4fe78ee07647b1c61f8ad2cbb4d3e58dfff1d16772a76f2dec925de579`
2. **MBA Segment Verification (`ufi001b_hash_tool.py`)**:
   - `20 MATCH, 8 ZERO/BSS, 0 MISSING, 0 MISMATCH` (`Overall: PASS ✓`)
3. **Deployment**:
   - Deployed to `/lib/firmware/modem.*` and `/lib/firmware/ufi001b_patched/`.
   - Performed cold reboot (`sync && reboot`).
