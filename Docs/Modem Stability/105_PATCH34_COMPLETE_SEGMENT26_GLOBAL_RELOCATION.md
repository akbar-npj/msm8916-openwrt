# Engineering Report 105: Patch 34 - Complete Segment 26 Global Relocation & Multi-RAT Scan Protection

**Date:** 2026-09-14  
**Target Hardware:** Melbon HMU05 4G Dongle (MSM8916 + WTR1605 transceiver + QFE2320 RF360 front-end)  
**Base Firmware:** UFI001B MPSS.DPM.2.0.2.C1 (donor baseband)  
**Carrier:** Reliance Jio 4G (MCC 405 MNC 861, Bands 3/5/40)  
**Status:** Patch 34 Applied, MBA Hash Verified (20 MATCH, 8 ZERO/BSS, 0 MISMATCH), Deployed & Verified Live  

---

## 1. Discovery & Problem Analysis

During testing of manual 3GPP network scans (`mmcli -m 0 --3gpp-scan`) under Patch 33:
- The Hexagon DSP suffered an SSR exception at $t = 142$ s:
  ```
  [  142.475824] qcom-q6v5-mss 4080000.remoteproc: fatal error received:     :Excep  :0:Exception detected
  [  142.475929] remoteproc remoteproc0: crash detected in 4080000.remoteproc: type fatal error
  ```
- Forensic disassembly of the non-LTE RAT drivers revealed the exact cause:
  1. In **Patch 25**, the virtual method slots (`get_band_info`) of GSM (`0x26c2c`), WCDMA (`0x28e14`), TDSCDMA (`0x28634`), and CDMA (`0x294e4`) had been assigned to `0xd2338dd8`.
  2. Not only was `0xd2338dd8` an offset calculation error (the Qualcomm safe stub actually resides at offset `0x28dd8`, which under the old link base would have been `0xd233add8`), but `0xd23...` is completely unmapped in the QDSP6 MMU.
  3. Whenever ModemManager requested a full 3GPP scan across 2G, 3G, and 4G, Layer 1 queried the GSM/WCDMA card vtables, jumped to unmapped address `0xd2338dd8`, and immediately took an instruction fetch TLB miss exception.

### 1.1 The Comprehensive Segment 26 Scan
We performed an automated binary scan across all 1,048,576 bytes of Segment 26 (`modem.b26`) to identify every remaining unmapped pointer.
The results were definitive:
- **Raw 32-bit pointers**: Exactly **1,304** pointers in `modem.b26` still pointed to `0xd2312000..0xd2412000`.
- **Hexagon `immext` constant loads**: Exactly **258** instruction packets loaded constants in `0xd2312000..0xd2412000`.
- **Bounds Check**: 100% of these 1,562 pointers were within `modem.b26` (0 to 1,048,576 bytes). Exactly zero pointed outside the segment.

This proved that Qualcomm compiled `modem.b26` as a relocatable shared library linked at base `0xd2312000`, while the ELF header maps it to `0xc3e0f000`.

---

## 2. Technical Solution (Patch 34)

### 2.1 Non-LTE RAT Neutralization to `0xc3e37dd8`
The authentic 12-byte Qualcomm safe stub at offset `0x28dd8`:
```hexagon
{
    p0 = cmp.eq(r2, #0)
    jumpr r31
    r0 = #0
    if (!p0.new) memw(r2+#0) = #0
}
```
resides at genuine virtual address:
$$\text{VA}_{\text{stub}} = 0xc3e0f000 + 0x28dd8 = \mathbf{0xc3e37dd8}$$

All 12 virtual method slots in the 4 non-LTE RAT vtables were updated:
- **GSM (`0x26c2c`)**: Slots 3, 4, 5 $\rightarrow$ `0xc3e37dd8`
- **WCDMA (`0x28e14`)**: Slots 3, 4, 5 $\rightarrow$ `0xc3e37dd8`
- **TDSCDMA (`0x28634`)**: Slots 3, 4, 5 $\rightarrow$ `0xc3e37dd8`
- **CDMA (`0x294e4`)**: Slots 3, 4, 5 $\rightarrow$ `0xc3e37dd8`

### 2.2 Global Relocation of Segment 26
Using the exact relocation delta:
$$\Delta = \text{VA}_{\text{new}} - \text{VA}_{\text{old}} = 0xc3e0f000 - 0xd2312000 = \mathbf{-0x0e503000}$$
1. **Raw 32-bit Pointers**:
   Relocated all 1,304 raw 32-bit pointers in the range `0x00000..0xe7000` (including residual table pointers at `0xe0b84` and `0xe69fc`).
2. **Hexagon `immext` Constant Loads**:
   Relocated all 258 `immext` constant loads, preserving instruction packet parse bits.
3. **Verification**:
   An exhaustive scan confirmed that **EXACTLY ZERO** `0xd23...` pointers remain in `modem.b26`.

---

## 3. MBA Cryptographic Verification

- **Segment 26 New SHA256**: `50a51076ffa7874a4de392d2184c5e4a28b49976fbe0fa8deebd1a8f3c687bf5`
- **MBA Verification (`ufi001b_hash_tool.py verify`)**:
  - `20 MATCH, 8 ZERO/BSS, 0 MISSING, 0 MISMATCH`
  - `Overall: PASS ✓`
