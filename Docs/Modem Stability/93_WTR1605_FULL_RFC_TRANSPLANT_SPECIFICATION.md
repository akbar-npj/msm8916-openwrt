# Patch Session Log 93 — WTR1605 Full RFC Table Transplant Specification

**Date:** 2026-09-14  
**Context:** MSM8916 WTR1605 RF360 Transplant to UFI001B MPSS.DPM.2.0  
**Status:** `+CFUN: 1` Active & Stable; Root Cause for Carrier Search Identified; Full RFC Transplant Specified  

---

## 1. Executive Summary

With **Patch 16** (NOP of line 4014 TRM assertion crash branch) and **Patch 17** (LTE RF task signal enable):
1. The modem boots cleanly, opens all 8 BAM-DMUX channels, and enters `Mode: 'online'` (`+CFUN: 1`).
2. The modem is 100% stable without any SSR crash loops (`dmesg` reports 0 fatal errors during normal operation).
3. The USIM is ready (`+CPIN: READY`), reading the Reliance Jio IMSI (`405861183291040`).
4. Network registration is currently in `not-registered-searching` (EPS `stat=6`).

---

## 2. Root Cause of Missing RF Carrier Reception

Through decompilation analysis of HMU05 stock `modem.b19` vs UFI001B `modem.b26`:

1. In Patch 12, we transplanted only the first 1,280 bytes of DevCfg (`0x241970` to `0x241e70` from HMU05 `modem.b19`) into `modem.b26` at offset `0xe0000`.
2. Static analysis reveals that the **complete WTR1605 RFC front-end script set** in HMU05 spans from `0x241970` to `0x245970` (**16,384 bytes**):
   - 89 separate WTR1605 device and band routing tables (`0x022902dd`).
   - QFE2320 PA (Power Amplifier) and ASM (Antenna Switch Module) RFFE write sequences.
   - Frequency band configuration and PLL divider tables for LTE Band 5 (850 MHz), Band 3 (1800 MHz), and Band 40 (2300 MHz TDD).
3. Because only the initial 1,280 bytes were transplanted, the RF synthesizer driver lacks the frequency conversion and RFFE register sequences needed to lock the WTR1605 PLL onto Reliance Jio carrier signals.

---

## 3. Full RFC Transplant Architecture

### Memory Budget in `modem.b26`
- Segment Size: 1,048,576 bytes (1.0 MB).
- Allocated Code / Tables: `0x00000` to `0x94000` (~592 KB).
- Transplant Offset: `0xe0000` (`0xd23f2000`).
- Available Unused Zero Space: `0xe0000` to `0x100000` (**131,072 bytes = 128 KB**).
- Required RFC Table Block: **16,384 bytes (16 KB)** from HMU05 `modem.b19` (offset `0x241970` to `0x245970`).

The 128 KB unused region in `modem.b26` provides ample space to hold the entire 16 KB stock HMU05 RFC configuration without any memory pressure or relocation conflict.

---

## 4. Implementation Plan

1. Extract the full 16,384-byte contiguous RFC table block from HMU05 stock `modem.b19` (offset `0x241970` to `0x245970`).
2. Transplant the block into UFI001B `modem.b26` starting at offset `0xe0000`.
3. Fix internal 32-bit pointers within the transplanted block:
   - Calculate VA relocation delta: $\Delta = \text{VA}_{\text{UFI001B (0xd23f2000)}} - \text{VA}_{\text{HMU05 (0xc1e7d970)}} = +0x10574690$.
   - Relocate all embedded table pointers so they point correctly within `modem.b26`.
4. Update segment 26 hash in `modem.b01`, regenerate `modem.mdt`, and verify all 28 ELF segments pass MBA authentication.
5. Deploy to `/lib/firmware/ufi001b_patched/` on HMU05 and cold reboot.
6. Verify LTE carrier detection and attach to Reliance Jio 4G.
