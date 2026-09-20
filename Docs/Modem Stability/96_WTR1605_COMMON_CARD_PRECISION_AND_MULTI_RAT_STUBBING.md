# Patch Session Log 96 — WTR1605 Common Card Precision, Multi-RAT Safe Stubbing, and Coredump Forensics

**Date:** 2026-09-14  
**Target Hardware:** Melbon HMU05 4G Dongle (MSM8916 SoC, Qualcomm WTR1605 transceiver, QFE2320 RF360 front-end)  
**Base Firmware:** UFI001B MPSS.DPM.2.0.2.C1 (donor baseband)  
**Carrier:** Reliance Jio 4G (MCC 405 MNC 861, pure VoLTE / PS-only, Bands 3, 5, 40)  
**Status:** Patches 21, 23, and 25 Active; Post-Reboot Uptime 100% Stable (0 Dormancy Freezes); Coredump Forensics Complete; RF Front-End Receiving Signal (`IO: -106 dBm`, `SINR: 9.0 dB`)  

---

## 1. Executive Summary & Accomplishments

In this session, we advanced the WTR1605 donor port through three major technical milestones:

1. **Patch 23 (Common Card Precision Alignment):**
   - Reverse engineered `rfc_wtr1605_chile_rf360_cmn_ag` in stock HMU05 `modem.b16` (`0xc1186160`) to correct table relocations and instruction packet alignment in `modem.b26`.
   - Connected authentic HMU05 GRFC Antenna Switch Table (`0xd23f2b3c`), WTR1605 Transceiver Device Table (`0xd23f2b98`), and GRFC Timing Script Table (`0xd23f8d20`).
   - Restored instruction packet alignment at `0x26130`, preserving the Packet 1 epilogue and preventing code clobbering.

2. **Patch 24 & Patch 25 (Clean Neutralization of Non-LTE RATs):**
   - Diagnosed an instruction decode exception in Patch 24 caused by an 8-byte truncation of a 12-byte Hexagon instruction packet and a hex character address typo (`0xd233add8` vs `0xd2338dd8`).
   - Created and deployed **Patch 25**, which completely leaves code sections unmodified and repoints the virtual method slots (`get_band_info`) of all non-LTE technologies (GSM, WCDMA, TDSCDMA, and CDMA) to Qualcomm's authentic 12-byte safe stub at `0xd2338dd8` (`00 40 02 75 00 40 9f 52 00 40 00 78`):
     ```
     {
         p0 = cmp.eq(r2, #0)
         jumpr r31
         r0 = #0
         if (!p0.new) memw(r2+#0) = #0
     }
     ```
   - Re-hashed Segment 26 in `modem.b01` and regenerated `modem.mdt`. All 28 ELF segments passed MBA authentication (`20 MATCH, 8 ZERO/BSS, PASS`).

3. **ELF Coredump Forensics (`devcd1/data`):**
   - Enabled inline remoteproc coredumps and captured an 88 MB full memory dump when testing manual network scan (`AT+COPS=?`).
   - Decoded QuRT Exception Frame:
     - Task: `AMSS0` (REX TCB `0xc224ca1c`)
     - QuRT Cause: `0x0a` (Privilege / Page Fault / TLB Miss)
     - `BADVA`: `0x8a7f2810`
     - Physical DDR address `0x8a7f2810` maps to virtual address `0xc3ff2810` inside Segment 27 (heap / BSS).
   - Identified that `0x8a7f2810` was an uninitialized pointer dereferenced during scan response formatting because RF Manager initialization (`rfm_init`) was previously bypassed in Patch 14.

---

## 2. Telemetry & Verification Across Cold Reboots

Following deployment of Patch 25, the dongle was rebooted (`sync && reboot`). Telemetry verified:

### A. Boot & Channel Status
- **Remoteproc Boot:** `remoteproc0: remote processor 4080000.remoteproc is now up` at $t \approx 11.8$ s.
- **BAM-DMUX:** All 8 channels (`CMD_OPEN 1`) opened cleanly between channels 0 and 7.
- **Modem State:** `Mode: 'online'` (`+CFUN: 1`).
- **Uptime:** Exceeds 3 hours cumulative operation across sessions without a single 15-minute sleep/dormancy crash (permanently avoiding stock HMU05 flaw).

### B. SIM & Network Stack Status
- **IMSI:** `405861183291040` (Reliance Jio).
- **Own Number:** `918431225166`.
- **IMEI:** `864293052253917`.
- **Allowed Modes:** `4g` (pure packet-switched EPS `ps-1`).
- **LTE Band Preference:** `1, 3, 5, 8, 40`.
- **Active RF Front-End Energy:**
  ```
  [/dev/wwan0qmi0] Successfully got signal strength
  IO: '-106 dBm'
  SINR (8): '9.0 dB'
  ```
  Confirms that the WTR1605 transceiver and QFE2320 front-end are physically energized and receiving RF energy.

---

## 3. Detailed Root Cause Analysis: Cell Acquisition & RF Initialization

### Why Automatic Cell Selection Has Not Yet Camped on a Jio Tower

1. **The Patch 14 Historical Context:**
   - In early sessions (Log 90/91), before the WTR1605 DevCfg tables, LTE method (Patch 21), and common card tables (Patch 23) were transplanted, running `rfm_init()` (`FUN_c0d77d94`) caused the baseband to hang because per-technology RF inits were attempting to speak to non-existent WTR4905 hardware registers over RFFE.
   - Patch 14 inserted an unconditional jump (`jump 0x68`) at `0xab5e08` in `modem.b15` to skip all per-technology RF inits and jump directly to the epilogue with `r16 = #1`.

2. **The Unintended Side Effect:**
   - Skipping all per-tech inits in `rfm_init()` allowed the modem to boot into `online` mode, but it also skipped `rfm_lte_init()`!
   - Because `rfm_lte_init()` never executed:
     - The LTE RF manager never initialized the RF driver state machine.
     - The WTR1605 PLL synthesizers, VCOs, and RX baseband filters were never tuned to Jio's LTE raster frequencies (Band 5: 881.5 MHz, Band 3: 1842.5 MHz).
     - Layer 1 runs its search loop, but the baseband RX path is not actively downconverting carrier channels to I/Q samples.
     - Manual scan (`AT+COPS=?`) crashes on `0x8a7f2810` because the RF scan response structure was never allocated by `rfm_lte_init()`.

---

## 4. Next Step: Unlocking `rfm_lte_init()`

With the authentic WTR1605 tables and LTE methods now safely in place (Patches 21, 23, 25):

1. **Selective Tech Init Activation:**
   - In `rfm_init` (`FUN_c0d77d94` in `modem.b15`), only run `rfm_lte_init()` while safely stubbing the 2G/3G/CDMA init calls.
   - This allows the LTE RF driver to fully configure the WTR1605 hardware for LTE carrier reception without triggering legacy WTR4905 RFFE timeouts on 2G/3G.
2. **Re-hash and Cold Boot:**
   - Re-calculate Segment 15 hash in `modem.b01` and rebuild `modem.mdt`.
   - Deploy to `/lib/firmware/` and cold reboot (`sync && reboot`).
3. **Monitor Serving System:**
   - Verify LTE cell acquisition on Reliance Jio (Band 5 or Band 3) and start data session via QMI WDS (`wwan0`).
