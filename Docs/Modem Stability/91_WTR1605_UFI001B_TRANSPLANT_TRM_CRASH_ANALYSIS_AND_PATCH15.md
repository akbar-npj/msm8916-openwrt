# Patch Session Log 91 — TRM Assertion Crash Analysis and Patch 15 Specification

**Date:** 2026-09-14  
**Subject:** MSM8916 WTR1605 RF360 Transplant to UFI001B MPSS.DPM.2.0  
**Status:** Patches 1–14 Active; Patch 15 (TRM Grant Override) Designed and Staged for Deployment  

---

## 1. Executive Summary & Progress Milestone

We have made substantial progress in porting the stable UFI001B Modem OS v2.0 (MPSS.DPM.2.0) to the HMU05 hardware (equipped with the WTR1605 transceiver instead of WTR4905):

1. **Patches 1–13** established hardware initialization, RFC card loading, device ID translation, OEM security bypass, and forced `rfc_card_init` / `rfc_init` to return success (1).
2. **Patch 14 (Revised)** bypassed the per-tech RF inits that previously hung on RFFE bus timeouts, forcing `rfm_init()` (`FUN_c0d77d94`) to return 1.
3. **Major Milestone Reached:** The modem successfully passed the initial `offline` state! QMI DMS mode transitioned to `shutting-down` / online negotiation, all 8 BAM-DMUX channels opened, and the USIM remained ready (`+CPIN: READY`, IMSI `405861183291040`).
4. **Current Blocker Identified:** Once Call Manager initiated the LTE Layer 1 stack, the modem crashed in an SSR loop every ~26 seconds with the exact error:
   ```
   qcom-q6v5-mss 4080000.remoteproc: fatal error received: lte_ml1_rfmgr_trm.c:4014:Assert event.grant != TRM_DENIED failed:
   ```
5. **Stock Firmware Sanity Check:** Tested the stock HMU05 firmware to verify hardware and SIM status. Stock firmware booted in 12 seconds, attached to Reliance Jio 4G LTE (MCC 405 MNC 861, Band 5, -60 dBm RSSI, 9.0 dB SINR), and entered packet service `connected`. However, as per user requirement, stock firmware suffers from the known 15-minute sleep/dormancy crash, confirming that porting UFI001B v2.0 remains the necessary path.

---

## 2. Root Cause Analysis of TRM Crash (`lte_ml1_rfmgr_trm.c:4014`)

### The Transceiver Resource Manager (TRM) Flow

In Qualcomm MPSS:
```
Call Manager (CM) starts LTE
  → LTE ML1 (Modem Layer 1) RF Manager initializes
  → LTE ML1 requests RF transceiver grant from TRM
  → TRM evaluates resource availability
  → TRM invokes callback FUN_c0323190(grant_event)
  → FUN_c0323190 packages event.grant into message 0x04290430
  → Message dispatched to LTE ML1 State Machine via stm_process_event()
  → State handler executes: ASSERT(event.grant != TRM_DENIED) [line 4014]
  → Because grant == 0 (TRM_DENIED), ASSERT triggers ERR_FATAL
  → Remoteproc SSR crash loop
```

### Why TRM Denies the Grant
Because Patch 14 jumped over the per-tech RF initialization calls to avoid RFFE timeouts on WTR4905 registers, the WTR1605 RF driver never registered its transceiver resources with TRM. Consequently, TRM has no registered transceiver available to grant to LTE ML1, and returns `TRM_DENIED` (`0`).

### The Vulnerable Code Site

From static analysis of `modem.b15`:
- `FUN_c0323190` is located at VA `0xc0323190` (file offset `0x61194`).
- It is registered as a TRM callback in `FUN_c0323150` via `thunk_FUN_c0b3d320(..., 0, FUN_c0323190)`.
- Incoming `r0` is the grant code (`0 = TRM_DENIED`, `1 = TRM_GRANT`, `2 = TRM_GRANT_NO_CHANGE`).
- The function preserves `r0` into callee-saved register `r16`:
  ```asm
  0xc0323190: { call 0xffd41f7c ; r16 = r0 ; memd(r29+#-16) = r17:16 ; allocframe(#32) }
  ```
- Later, at offset `+0x40` (VA `0xc03231d0`), it writes `r16` into the state machine message payload:
  ```asm
  0xc03231d0: { call 0x44d8 ; r0 = r17 ; memw(r4+#16) = r16 }
  ```
  Here `r4` points to the 24-byte message buffer. Offset 0..15 is the MSGR header; offset 16 is `event.grant`!

---

## 3. Patch 15 Specification (TRM Grant Override)

By modifying `FUN_c0323190` to load `r16 = #1` instead of `r16 = r0`, any response from TRM is transformed into `TRM_GRANT (1)` before reaching the LTE ML1 State Machine.

### Patch 15-A: Primary TRM Callback Grant Override

| Parameter | Value |
|---|---|
| Target File | `modem.b15` |
| Function | `FUN_c0323190` |
| Virtual Address | `0xc0323194` |
| File Offset | `0x00061194` |
| Original Hex | `10 40 60 70` (`{ r16 = r0 }`) |
| Replacement Hex | `30 40 00 78` (`{ r16 = #1 }`) |
| Parse Bits | `parse = 1` (continuation in 4-word packet preserved) |
| Effect | Injects `event.grant = TRM_GRANT (1)` into message payload |

### Patch 15-B: Secondary TRM Callback Grant Override

| Parameter | Value |
|---|---|
| Target File | `modem.b15` |
| Function | `UNK_c03256d8` |
| Virtual Address | `0xc03256dc` |
| File Offset | `0x000636dc` |
| Original Hex | `10 40 60 70` (`{ r16 = r0 }`) |
| Replacement Hex | `30 40 00 78` (`{ r16 = #1 }`) |
| Parse Bits | `parse = 1` |
| Effect | Ensures secondary antenna/client grant callback also returns `1` |

---

## 4. Cumulative Patch Inventory (P1–P15)

| Patch ID | Target File | Offset / VA | Purpose | Status |
|---|---|---|---|---|
| P1 | `modem.b15` | VA `0xc100c618` | WTR transceiver dispatch jump | ACTIVE |
| P2 | `modem.b15` | VA `0xc123de7c` | RFC Card HWID Chile constructor | ACTIVE |
| P6 | `modem.b26` | Global | WTR4905→WTR1605 Device IDs | ACTIVE |
| P7 | `modem.b15` | VA `0xc0e27ed8` | OEM security bypass | ACTIVE |
| P8 | `modem.b15` | VA `0xc0e22f38` | Operating mode lock bypass | ACTIVE |
| P10 | `modem.b15` | VA `0xc0e1f67c` | DMS mode callback force success | ACTIVE |
| P11 | `modem.b26` | VA `0xd23860e8` | RFFE QFE2320 ASM/PA IDs | ACTIVE |
| P12 | `modem.b26` | VA `0xd23f2000` | HMU05 DevCfg transplant (1280 B) | ACTIVE |
| P13-T1 | `modem.b15` | VA `0xc0d7c6d0` | `FUN_c0d7c6d0` return 1 | ACTIVE |
| P13-T2 | `modem.b15` | VA `0xc1003da0` | `rfc_card_init` failure bypass | ACTIVE |
| P14-A | `modem.b15` | VA `0xc0d77dfc` | `rfm_init`: `r16 = #1` | ACTIVE |
| P14-B | `modem.b15` | VA `0xc0d77e00` | `rfm_init`: `jump #0x50` to epilogue | ACTIVE |
| **P15-A** | `modem.b15` | VA `0xc0323194` | `FUN_c0323190`: `r16 = #1` (TRM_GRANT) | **STAGED** |
| **P15-B** | `modem.b15` | VA `0xc03256dc` | `UNK_c03256d8`: `r16 = #1` (TRM_GRANT) | **STAGED** |

---

## 5. Verification & Test Plan

1. Apply Patch 15-A and 15-B to `modem.b15`.
2. Update segment 15 hash in `modem.b01` and rebuild `modem.mdt` via `ufi001b_hash_tool.py`.
3. Verify all 28 ELF segments pass MBA verification.
4. Deploy to `/lib/firmware/ufi001b_patched/` on physical dongle at `192.168.8.1`.
5. Execute `switch_modem_fw.sh patched && sync && reboot`.
6. Monitor cold boot telemetry:
   - Check if `lte_ml1_rfmgr_trm.c:4014` assertion is eliminated.
   - Verify if modem transitions from `shutting-down` to `online`.
   - Inspect AT responses on `/dev/wwan0at1` (`AT+CFUN?`, `AT+CSQ`, `AT+CREG?`).
   - Query QMI NAS serving system (`--nas-get-serving-system`) for Jio 4G LTE attach.
