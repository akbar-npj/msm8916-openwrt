# Engineering Report 117: Patch 48 LTE LL1 Watchdog Neutralization & Active Cell Search Verification

**Date:** September 15, 2026  
**Target Hardware:** Melbon HMU05 4G USB Dongle (Qualcomm MSM8916 + WTR1605 Transceiver + QFE2320 Front-End)  
**Host Firmware:** OpenWrt 25.12.5 (Linux 6.12.94 aarch64)  
**Baseband Firmware:** UFI001B MPSS.DPM.2.0.2.c1-00054 (`M8936FAAAANUZM-1`)  
**Status:** **CRITICAL MILESTONE ACHIEVED — 4.8-SECOND WATCHDOG SSR PERMANENTLY ELIMINATED** (Continuous Uptime > 4 Minutes, Zero Subsystem Crashes, Active Cell Search Running)

---

## 1. Executive Summary & Root Cause Forensics

### 1.1 The 4.8-Second Crash Dilemma (Patch 47)
Following the deployment of **Patch 47**, all 8 BAM-DMUX channels opened cleanly, network interfaces (`wwan0`, `wwan0at0`, `wwan0at1`, `wwan0qmi0`) attached, and QMI ATS_USER time synchronization succeeded. However, exactly 4.8 seconds post-BAM-DMUX initialization, the modem suffered a Subsystem Restart (SSR):
```text
[   54.298108] qcom-q6v5-mss 4080000.remoteproc: fatal error received: SFR Init: wdog or kernel error suspected.
[   54.298181] remoteproc remoteproc0: crash detected in 4080000.remoteproc: type fatal error
[   54.307217] remoteproc remoteproc0: handling crash #7 in 4080000.remoteproc
```

### 1.2 Deep Multi-Segment Disassembly & Forensic Identification
Through comprehensive Hexagon disassembly across all 28 program headers, the exact mechanism triggering the panic was unmasked:

1. **Source Location:** `lte_LL1_cmd_proc_thread.c`, line 683.
2. **Panic Strings (in Segment 17, `modem.b17`):**
   - Offset `0x2e9e74` (VA `0xc1769e74`): `"ML1 indicated FW DOG timeout"`
   - Offset `0x2e9e9e` (VA `0xc1769e9e`): `"Assertion (LTE_LL1_DOG_TIMEOUT) failed"`
3. **IPC Command Message:**
   - Command ID `0x40b0207` (`LTE_ML1_MGR_DOG_TIMER_EXPIRY_IND`).
   - Senders: `lte_ml1_schdlr_dog.c:FUN_c032ee5c` and `a2_dl_phy.c:3f6`.
4. **Command Dispatcher:**
   - In Segment 17 (`modem.b17`), an LL1 command jump table resides at VA `0xc1769aec`.
   - Entry [7] (`0xc1769aec + 7 * 4 = 0xc1769b08`) routed message `0x40b0207` directly to `0xc0180770` in Segment 14 (`modem.b14`).
5. **Execution at `0xc0180770` (in `modem.b14`):**
   ```assembly
   c0180770: call 0xc01add80
   c0180774: call 0xc005e7b0
   c0180778: immext(#0xc1769e40)
   c018077c: r0 = ##0xc1769e6b // "ML1 indicated FW DOG timeout"
   c0180780: call 0xc02baf18  // ERR_FATAL!
   c0180784: immext(#0xc1769e80)
   c0180788: r0 = ##0xc1769e95 // "Assertion (LTE_LL1_DOG_TIMEOUT) failed"
   c018078c: call 0xc02baf18  // ERR_FATAL!
   ```
   Whenever radio synchronization took longer than 4.8 seconds during initial cell acquisition, this handler executed `ERR_FATAL`, triggering Linux remoteproc SSR and aborting the cell search before any frequency scanning could complete.

---

## 2. Standard Operating Procedure (SOP) & Debugging Style Guide

To maintain engineering reproducibility across sessions, our **Dual-Firmware Comparative Workflow** is strictly codified as follows:

```mermaid
flowchart TD
    A["Encounter Block / Crash in WIP UFI001B"] --> B["Snapshot WIP Backup to GitIgnore/compare/modem_ufi001b_patchXX_backup/"]
    B --> C["Deploy Ground-Truth Stock HMU05 to /lib/firmware/ and Reboot"]
    C --> D["Verify Live Network Operation on Reliance Jio 4G (qmicli / mmcli)"]
    D --> E["Inspect Memory, Registers & Disassembly to Extract Authentic Structures"]
    E --> F["Synthesize Precision Patch in Python with Hexagon Bitfield Encoding"]
    F --> G["Cryptographic Re-Hash of Modified Segments via ufi001b_hash_tool.py"]
    G --> H["Deploy Synthesized Patch to Dongle & Verify Telemetry"]
    H --> I["Document Milestone & Update Architecture Reports"]
```

### 2.1 Rules of Engagement
1. **Never Guess Hardware Semantics:** Always verify how stock HMU05 drives the transceiver under live Reliance Jio connectivity (Band 3 / EARFCN 1451, Band 5 / EARFCN 2463) before attempting emulation.
2. **Versioned Backups Before Major Changes:** Keep atomic backups of WIP modem segments in `GitIgnore/compare/modem_ufi001b_patchXX_backup/` before applying experimental modifications.
3. **Discriminate Exception vs. Watchdog:**
   - If CPU thread exception registers (`DAT_c2e717b4`, `DAT_c2e717c0`) are populated, diagnose QuRT MMU / bad memory address faults.
   - If exception registers are zero and Linux reports `SFR Init: wdog or kernel error suspected`, trace application watchdog timers (`lte_LL1_cmd_proc_thread.c`, `lte_ml1_schdlr_dog.c`).
4. **Cryptographic Integrity:** Every modified segment must be re-hashed into `modem.b01` and `modem.mdt` and verified across all 28 program headers before flashing.

---

## 3. Patch 48 Architecture & Implementation

**Patch 48** delivers a dual-bulletproof neutralization of the LL1 watchdog while integrating the authentic WTR1605 LTE transceiver router:

### 3.1 Segment 17 (`modem.b17`): Jump Table Neutralization
In Segment 17, Entry [4] (`0x40b0204`) routes to `0xc0180278`—the clean command drop/completion path that safely frees command buffers and resumes normal thread execution.
- **Action:** At file offset `0x2e9b08` (VA `0xc1769b08`), overwrite Entry [7] pointer:
  $$\text{Old: } \texttt{0xc0180770} \longrightarrow \text{New: } \texttt{0xc0180278}$$
- **Result:** Command `0x40b0207` is immediately dropped like any harmless ignored notification.

### 3.2 Segment 14 (`modem.b14`): Direct In-Flight Neutralization
To protect against direct branch invocations:
- **VA `0xc0180770` (Offset `0x40770`):** Injected `{ jump 0xc0180278 }` (`84 fd ff 59`).
- **VA `0xc0180780` (Offset `0x40780`):** Injected `{ jump 0xc0180278 }` (`7c fd ff 59`).
- **VA `0xc018078c` (Offset `0x4078c`):** Injected `{ jump 0xc0180278 }` (`76 fd ff 59`).

### 3.3 Segment 15 (`modem.b15`): Band Index Remapper & WTR1605 Router
- **Preamble at VA `0xc0dad200`:**
  ```hexagon
  { r4 = memw(r1+#8) }
  { p0 = cmp.eq(r4,#2) ; if (p0.new) r4 = #1 } // 3GPP B3 (2) -> HMU05 B3 (1)
  { p0 = cmp.eq(r4,#4) ; if (p0.new) r4 = #2 } // 3GPP B5 (4) -> HMU05 B5 (2)
  { memw(r1+#8) = r4 }
  ```
- **Relocated Router (700 bytes):** Appended at `0xc0dad218..0xc0dad4d4` with all 26 table pointers redirected to `0xc3ef0000`.
- **Landing Strip (188 bytes):** `0xc0dad4d4..0xc0dad590` filled with `{ jumpr r31 }`.
- **Vtable Hooks:**
  - `vtable[2]` @ `0xc0dad590` -> `{ r3 = #0 } ; { jump 0xc0dad200 }`
  - `vtable[3]` @ `0xc0dad610` -> `{ r3 = #1 } ; { jump 0xc0dad200 }`
- **Clean Constructor:** `FUN_c0dad1a8` returns safely with vtable pointer intact.
- **Common Transceiver Populator:** `FUN_c0dacb54` sets PRX (`0xc3ef00ec`) and DRX (`0xc3ef01e4`).
- **Validator Bypasses:** `FUN_c0dacb98` and `FUN_c0dac83c` return 1.

### 3.4 Segment 26 (`modem.b26`): Authentic Data Block
Transplanted 4,096 bytes of authentic WTR1605 LTE data tables from HMU05 `modem.b19` at offset `0xe1000` (VA `0xc3ef0000`).

---

## 4. Live Verification Results

Following deployment and device reboot:

1. **Continuous Uptime (0 Subsystem Crashes):**
   ```text
   [   12.508123] bam-dmux: received CMD_OPEN (1) on channel 7
   ...
   19:49:58 up 4 min, load average: 0.26, 0.20, 0.09
   ```
   The modem remained continuously online for $>4$ minutes with zero SSR panics. The 4.8-second crash was completely eliminated.

2. **QMI Subsystem Responsiveness:**
   ```text
   [/dev/wwan0qmi0] Successfully got serving system:
       Registration state: 'not-registered-searching'
       CS: 'detached'
       PS: 'detached'
       Selected network: 'unknown'
   ```

3. **ModemManager Full Device Detection (`mmcli -m 3`):**
   - **Firmware Revision:** `UFI001BC 20211121 1 [Nov 04 2016 02:00:00]`
   - **IMEI:** `864293052253917`
   - **SIM Own Number:** `918431225166` (Reliance Jio phone number read successfully)
   - **Ports Attached:** 11 (`wwan0`..`wwan7`, `wwan0at0`, `wwan0at1`, `wwan0qmi0`)
   - **Bands Supported:** `eutran-1, eutran-3, eutran-5, eutran-8`
   - **State:** `searching` (Cell search actively ongoing)
