# 89: Qualcomm MSM8916 WTR1605 Transceiver & RFC Driver Transplant to Modem OS v2.0

**Target Hardware:** Generic HMU05 / Melbon White 4G LTE USB Dongle (Qualcomm MSM8916 / Snapdragon 410)  
**Stock Source Firmware:** `MPSS.DPM.1.0.C7` (`HIMI_U01_MODEM_V1.0`, Sep 09 2015) — Uses **WTR1605** RF Transceiver + Qualcomm RF360 Front-End  
**Donor Target Firmware:** `MPSS.DPM.2.0.2.C1` (`M8936FAAAANUZM-1`, Nov 04 2016) — Uses **WTR4905** RF Transceiver + SAW-less Front-End  
**Operating System:** OpenWrt 25.12.5 (Linux Kernel 6.12.94 / `msm89xx`)  
**Target Carrier / SIM:** Reliance Jio 4G (MCC 405 MNC 861, LTE Band 5 / Band 3 / Band 40)  
**Status:** **IN PROGRESS (PHASE 1 RE & TRANSLATION COMPLETE, HARDWARE REBOOT METHODOLOGY ESTABLISHED)**  

---

## Table of Contents
1. [Executive Summary & Motivation](#1-executive-summary--motivation)
2. [Hardware & Architecture Ground Truth](#2-hardware--architecture-ground-truth)
3. [Authentication & Segment Hash Alignment](#3-authentication--segment-hash-alignment)
4. [Master Patch Catalog (Patches 1 through 13)](#4-master-patch-catalog-patches-1-through-13)
5. [Negative Findings & Invariant Rules](#5-negative-findings--invariant-rules)
6. [Hardware Verification & Live Telemetry](#6-hardware-verification--live-telemetry)
7. [Forensic Root Cause: Offline Mode & CME Error Phone Failure](#7-forensic-root-cause-offline-mode--cme-error-phone-failure)
8. [Standardized Cold Reboot & Test Methodology](#8-standardized-cold-reboot--test-methodology)
9. [Next Implementation Milestones](#9-next-implementation-milestones)

---

## 1. Executive Summary & Motivation

On Qualcomm MSM8916 4G USB dongles running modern upstream Linux kernels (OpenWrt 25.12.5 with Linux 6.12.94), the stock modem firmware on HMU05 (`MPSS.DPM.1.0.C7`) suffers from a catastrophic Layer 1 Discontinuous Reception (DRX) sleep timer assertion failure:
* **Crash Point:** `lte_ml1_common_timer.c:390` / `lte_ml1_sleepmgr_stm.c:4054`
* **Trigger:** Exactly 15 minutes (900 seconds) of idle/connected data traffic, when the modem enters deep micro-sleep.
* **Impact:** Remoteproc Subsystem Restart (SSR) crash loop, tearing down `wwan0`, killing all TCP/UDP connections.

In contrast, Qualcomm Modem OS v2.0 from UFI001B (`MPSS.DPM.2.0.2.C1`, `M8936FAAAANUZM-1`) has a completely refactored Layer 1 sleep manager and DPM (Data Path Manager) stack that operates rock-solid without any sleep crashes on Linux 6.12.

**The Core Challenge:**
* Flashing UFI001B's `modem.bin` onto HMU05 leaves the device without cellular radio service:
  1. HMU05 hardware uses the **Qualcomm WTR1605** transceiver paired with Qualcomm **RF360** front-end chips (`rfc_wtr1605_chile_rf360`: QFE2320 PA/ASM).
  2. UFI001B's Modem OS v2.0 was compiled specifically for the **Qualcomm WTR4905** transceiver with a SAW-less front-end (`rfc_wtr4905_*`).
* **The Mission:** Reverse engineer, adapt, and transplant WTR1605 transceiver drivers, RFC (RF Card) tables, front-end bus configurations, and Call Manager states from HMU05 into UFI001B's Modem OS v2.0 so that HMU05 runs stable Modem OS v2.0 firmware without sleep crashes.

---

## 2. Hardware & Architecture Ground Truth

Through deep Hexagon QDSP6 disassembly (`llvm-mc`, `llvm-objdump -d --arch=hexagon`) and Ghidra decompilation of both firmwares, the structural differences were mapped:

```
+--------------------------------------------------------------------------------------------------+
|                                    HMU05 (Modem OS v1.0)                                         |
|  - Transceiver: Qualcomm WTR1605L                                                                |
|  - Front-End: Qualcomm RF360 (QFE2320 PA/ASM, QFE1100 Envelope Tracker)                          |
|  - RF Card Class: rfc_wtr1605_chile_rf360                                                        |
|  - ELF Layout: Monolithic (b16 code: 18.3MB, b18 rodata: 7.6MB, b19 devcfg: 2.8MB, b25: 309KB)   |
+--------------------------------------------------------------------------------------------------+
                                                vs
+--------------------------------------------------------------------------------------------------+
|                                   UFI001B (Modem OS v2.0)                                        |
|  - Transceiver: Qualcomm WTR4905                                                                 |
|  - Front-End: SAW-less discrete front-end (rfc_wtr4905_chile_asdiv_*, rfc_wtr4905_china_*, etc.)  |
|  - RF Card Class: Modular RFC classes loaded from independent segment modem.b26                  |
|  - ELF Layout: Modularized (b15 code: 17.7MB, b17 rodata: 7.7MB, b18: 2.8MB, b26 module: 1.0MB) |
+--------------------------------------------------------------------------------------------------+
```

### 2.1 The Transceiver Dispatcher: `FUN_c100c524` in `modem.b15`
In UFI001B's main code segment (`modem.b15`), `FUN_c100c524` (VA `0xc100c524`, file offset `0x00d4a524`) reads NV item 1878 (`NV_RF_HW_CONFIG_I`):
* **Discovery #1:** UFI001B still retains the low-level WTR1605 register dispatch table at `0xc100c640` (`LAB_c100c640`), configuring `"WTR1605"`, PMIC client `"/pmic/client/rf1_tech_gps"`, and core init functions (`FUN_c1010094`, `FUN_c100fed8`, `FUN_c1011444`).
* **Discovery #2:** The HWID classifier at `0xc100c618`–`0xc100c63c` prevents WTR1605 execution: HWID `0` (the default on uncalibrated HMU05 NV) falls through to `0xc100c692` and forces WTR4905 (`"/pmic/client/rf2_tech_gps"`). Only 12 legacy IDs (`0x3e`, `0x48`, `0x49`, `0x59`–`0x62`) ever reached the WTR1605 block.

### 2.2 The RFC Card Factory: `FUN_c123de54` in `modem.b15`
During RF subsystem startup, `FUN_c1003c50` (`rfc_card_init`) invokes `FUN_c123de54(HWID, &local_44, &local_48)` to locate the front-end class constructor:
* In UFI001B, `FUN_c123de54` dispatches on HWID.
* Every valid HWID case points into **`modem.b26`** (`SUB_d2312000`, `SUB_d2360000`, etc.).
* HWID `0` hits default branch `switchD_c123dec8_caseD_37`, clearing constructor pointers to `0` and returning `0`.
* `FUN_c1003c50` then prints:
  `rfc_card_factory.cpp:RFC_CARD_HWID: %d failed to load!!!`
  and aborts RF card initialization.

### 2.3 Segment `modem.b26` (The RFC Card Module)
In UFI001B, Qualcomm decoupled all RF front-end implementations into dynamic ELF segment `modem.b26`:
* **Virtual Address:** `0xc3e0f000` (File size: 1,048,576 bytes / 1.0 MB)
* **Linked Virtual Base:** `0xd2312000`
* **Vtable Compatibility:** The RFC Card C++ virtual method table has **8 methods** in both HMU05 and UFI001B:
  * `vtable[0]`: Destructor
  * `vtable[1]`: Instance Constructor / Init
  * `vtable[2]`: `get_band_config` / `get_timing_config`
  * `vtable[3]`–`vtable[7]`: Band routing and GRFC antenna switch scripts
  The C++ ABI and vtable layout between MPSS v1.0 and v2.0 are **100% binary compatible**.

---

## 3. Authentication & Segment Hash Alignment

Qualcomm MSM8916 implements secure boot via the Modem Boot Authenticator (MBA):
1. **The Hash Segment (`modem.b01`):**
   * Header: 40 bytes (`0x000`..`0x027`)
   * Hash Table: `N * 32` bytes (`0x028`..), containing SHA-256 digests for all 28 ELF segments.
   * Signature: 256-byte RSA-2048 signature (`0x028 + N * 32`).
   * Certificate Chain: 6144 bytes (3 x DER X.509 certs).
2. **The Master Descriptor (`modem.mdt`):**
   * Exact concatenation: `modem.b00` (ELF header, 948 bytes) + `modem.b01` (hash segment, 7336 bytes).
3. **MBA Enforcement:**
   * MBA boots in secure trustzone and verifies each segment against the hash table in `modem.b01` before un-resetting QDSP6.
   * **Custom Tooling:** We created `GitIgnore/compare/ufi001b_hash_tool.py`, which computes SHA-256 digests of modified `.bXX` segments and surgically patches the corresponding entry in `modem.b01` and rebuilds `modem.mdt`.
   * **Status:** All 20 loaded segments continuously report `20 MATCH, 8 ZERO/BSS, 0 MISMATCH, Overall: PASS ✓`.

---

## 4. Master Patch Catalog (Patches 1 through 13)

### Patch 1: Unconditional WTR1605 Transceiver Selection (`modem.b15`)
* **Target File:** `modem.b15`
* **Virtual Address:** `0xc100c618` | **File Offset:** `0x00d4a618`
* **Original:** `56 40 00 5c 03 08 22 e8 e0 79 e0 bf a8 f8 02 25`
* **Patched:** `03 08 22 e8 12 c0 00 58 00 c0 00 7f 00 c0 00 7f`
* **Action:** Bypasses NV 1878 HWID classifier; jumps unconditionally to `LAB_c100c640` (`WTR1605`, PMIC `/pmic/client/rf1_tech_gps`).

### Patch 2: RFC Card Factory Hook to Chile Constructor (`modem.b15`)
* **Target File:** `modem.b15`
* **Virtual Address:** `0xc123de7c`–`0xc123de88` | **File Offset:** `0x00f7be7c`
* **Original:** `d6 fd 4a 10 c6 40 00 58 80 44 23 0d 14 c0 00 78`
* **Patched:** `00 c0 00 7f c6 40 00 58 00 4e 23 0d 14 c0 00 78`
* **Action:** Bypasses `switchD_c123dec8_caseD_37` abort on HWID 0; falls through to Chile front-end constructor at `0xd2338000` in `modem.b26`.

### Patch 6: WTR1605 Device ID & Transceiver Enum Substitution (`modem.b26`)
* **Target File:** `modem.b26` (Linked VA Base: `0xd2312000`)
* **Action:**
  1. **Device ID:** Replaced all 306 occurrences of WTR4905 device ID `0x04200394` (`94 03 20 04`) with WTR1605 device ID `0x022902dd` (`dd 02 29 02`).
  2. **Transceiver Enum:** Replaced all 124 occurrences of WTR4905 enum `0x0000003e` (`3e 00 00 00`) with WTR1605 enum `0x00000003` (`03 00 00 00`).
* **Rationale:** Prevents RF driver from attempting to query WTR4905 RFFE register banks.

### Patch 7: OEM NV Security Password Verification Bypass (`modem.b15`)
* **Target File:** `modem.b15`
* **Virtual Address:** `0xc0e27ed8` | **File Offset:** `0x00b65ed8`
* **Original:** Checks OEM NV item 85/219 passwords, increments retry counter, and locks modem into `SYS_OPRT_MODE_PWROFF` on mismatch.
* **Patched:** `{ r0 = #0x0 ; jumpr r31 }` (`00 40 00 78 00 c0 9f 52`).
* **Action:** Returns 0 (success) immediately, preventing OEM lockout.

### Patch 8: Operating Mode Transition Lock Check Bypass (`modem.b15`)
* **Target File:** `modem.b15`
* **Virtual Address:** `0xc0e22f38` | **File Offset:** `0x00b60f38`
* **Action:** Bypassed conditional jump checking `memub(R20+0x5a)` to allow all operating mode requests to reach Call Manager.

### Patch 10: DMS Operating Mode Callback Success Force (`modem.b15`)
* **Target File:** `modem.b15`
* **Virtual Address:** `0xc0e1f67c` | **File Offset:** `0x00b5d67c`
* **Original:** `26 c0 00 5c` (`if (p0) jump:nt 0xc0e1f6c8`) -> returned QMI error 52 (`DeviceNotReady`) when Call Manager delayed transition.
* **Patched:** `06 c0 00 58` (`jump 0xc0e1f688`) -> jumps directly to success packet builder.
* **Action:** QMI DMS `Set Operating Mode: online` returns `SUCCESS` (`00:00:00:00`).

### Patch 11: RFFE Front-End Device Table Adaptation (`modem.b26`)
* **Target File:** `modem.b26`
* **Target Offset:** `0x740e8` (VA `0xd23860e8`)
* **Action:** Configured Entry 0 (ASM) PID `0x0093` (QFE2320 ASM), Entry 1 (PA) PID `0x0093` (QFE2320 PA), Qualcomm MFG ID `0x0198` (`00 98 01 00`).
* **Action:** Aligns RFFE slave addresses with HMU05's physical Qualcomm RF360 chip IDs.

### Patch 12: HMU05 Devices Configuration Table (DevCfg) Transplant (`modem.b26`)
* **Target File:** `modem.b26`
* **Action:**
  1. Extracted HMU05's pristine 1280-byte (`0x500`) Devices Configuration Table from HMU05 `modem.b19` at offset `0x241970` (`0xc1e7d970`).
  2. Injected into unused padding space in UFI001B `modem.b26` at file offset `0xe0000` (VA `0xd23f2000`).
  3. Repointed constructor `FUN_c3e35050` at offset `0x26060` (VA `0xd2338060`) from `immext(#0xd2385780)` (`5e 61 23 0d`) to `immext(#0xd23f2000)` (`80 7c 23 0d`).
  4. Fixed residual WTR4905 ID at `0x7dd6c` to WTR1605 `0x022902dd`.
  5. Fixed PA MFG ID at `0x7413c` to `0x0198`.
* **Action:** Supplies HMU05's actual physical pin routing, bus enumeration, and power rail mappings.

### Patch 13: RFC Card Init Validation Bypass (`modem.b15`)
* **Target File:** `modem.b15`
* **Target 1 (VA `0xc0d7c6d0`, file offset `0x00aba6d0`):**
  * Original (12 bytes): `00 40 9f 52 20 40 00 00 00 c0 20 91` (`{ jumpr r31 ; r0 = memub(r0+##2048) }`)
  * Patched: `c0 3f 10 48 00 c0 00 7f 00 c0 00 7f` (`{ r0 = #1 ; jumpr r31 } ; nop ; nop`)
* **Target 2 (VA `0xc1003da0`, file offset `0x00d41da0`):**
  * Original (4 bytes): `06 c0 00 10` (`p0 = cmp.eq(r0,#0); if (p0.new) jump:nt 0x1c`)
  * Patched: `00 c0 00 7f` (`nop`)
* **Action:**
  * `FUN_c0d7c6d0` getter unconditionally returns `1` (card init successful).
  * `FUN_c1003c50` NOPs the branch to `"RFC_CARD_HWID: %d failed to init!!!"`, falling through directly to `r18 = #1; memb(DAT_c2b06cd0) = 1; jump 0x2c`.

---

## 5. Negative Findings & Invariant Rules

1. **Hexagon VLIW Instruction Atomicity:**
   * Hexagon QDSP6 executes packets of 1 to 4 instructions bundled by end-of-packet (EOP) parse bits (`[15:14]`).
   * *Rule:* Never split or alter instructions within a packet without preserving the packet bundle boundaries. Inserting branches or NOPs that break packet structure triggers immediate processor exceptions (`Assertion failed` or PIL panic).
2. **UFI001B RMNET Data State Machine Invariant:**
   * In HMU05, `DAT_c1de7ae8 == 1` was used to iterate 20 RMNET instances.
   * In UFI001B, setting `DAT_c1de7ae8 = 1` triggers an unrecoverable assertion failure at `ds_rmnet_meta_sm.c:2562` because UFI001B was compiled with fewer instances. `DAT_c1de7ae8 == 0` is native and strictly required.

---

## 6. Hardware Verification & Live Telemetry

### 6.1 Stock HMU05 Hardware Ground Truth Baseline
To rule out hardware faults, SIM failure, or RF path damage, the physical dongle at `192.168.8.1` was switched live to stock HMU05 firmware (`/usr/bin/switch_modem_fw.sh stock`):
* `qmicli --dms-set-operating-mode=online`: Completed successfully from LPM.
* `qmicli --nas-get-serving-system`:
  ```
  Registration state: 'registered'
  CS: 'attached' | PS: 'attached'
  Selected network: '3gpp'
  Radio interfaces: '1' [0]: 'lte'
  PLMN: MCC '405' MNC '861' ('JIO 4G')
  Cell ID: '441648' | TAC: '63'
  ```
* **Verdict:** The physical WTR1605 transceiver, RF360 front-end circuitry, Reliance Jio 4G SIM card, and antenna lines are 100% operational.

### 6.2 Patched UFI001B Live Telemetry (Fresh Cold Boot)
Following a clean cold system reboot (`sync && reboot`):
* **MBA & PIL Boot:**
  ```
  [   10.978609] remoteproc remoteproc0: powering up 4080000.remoteproc
  [   10.994050] remoteproc remoteproc0: Booting fw image mba.mbn, size 230272
  [   11.036037] qcom-q6v5-mss 4080000.remoteproc: MBA booted without debug policy, loading mpss
  [   11.754597] remoteproc remoteproc0: remote processor 4080000.remoteproc is now up
  ```
* **BAM-DMUX Channels:**
  * All 8 channels (0 through 7) cleanly opened.
  * Device nodes created: `/dev/wwan0at0`, `/dev/wwan0at1`, `/dev/wwan0qmi0`.
  * **Zero QRTR client lookup limit errors, zero packet failures.**
* **USIM & ISIM Card Status (`qmicli --uim-get-card-status`):**
  ```
  Slot [1]: Card state: 'present'
  Application [1]:
      Application type:  'usim (2)'
      Application state: 'ready'
      PIN1 state: 'disabled' (10 retries remaining)
  Application [2]:
      Application type:  'isim (5)'
      Application state: 'detected'
  IMSI: '405861183291040'
  ICCID: '89918610400790502190'
  AT+CPIN?: '+CPIN: READY'
  ```
* **Operating Mode State:**
  * `qmicli --dms-get-operating-mode`: `Mode: 'offline'`, `HW restricted: 'no'`.
  * `qmicli --dms-set-operating-mode=online`: Returns `SUCCESS (00:00:00:00)`.
  * `AT+CFUN=1`: Returns `+CME ERROR: phone failure`.

---

## 7. Forensic Root Cause: Offline Mode & CME Error Phone Failure

### 7.1 The Asynchronous vs Synchronous Execution Path
* **QMI DMS Layer:** When `qmicli --dms-set-operating-mode=online` is executed, the DMS task accepts the request and forwards a `cm_ph_cmd_oprt_mode(SYS_OPRT_MODE_ONLINE)` command to Qualcomm Call Manager (CM). Thanks to Patch 10, DMS responds with `SUCCESS` immediately.
* **Call Manager & AT Layer:** When `AT+CFUN=1` is executed, `dsat` synchronously awaits Call Manager's command callback (`dsatme_cm_ph_cmd_cb`). Call Manager evaluates the radio subsystem state:
  ```
  cm_ph_cmd_oprt_mode(SYS_OPRT_MODE_ONLINE)
         │
         ▼
  cmph_client_cmd_proc()
         │
         ├── Checks current oprt_mode (currently SYS_OPRT_MODE_OFFLINE = 1)
         │
         └── If RF Subsystem reported uninitialized / hardware mismatch:
                 Command rejected with CM_PH_CMD_ERR_OFFLINE_S (0x3c = 60)
                 │
                 ▼
          Mapped by dsat to DSAT_CME_PHONE_FAILURE
                 │
                 ▼
          Response: "+CME ERROR: phone failure"
  ```

### 7.2 Why Baseband Starts in `SYS_OPRT_MODE_OFFLINE` (`+CFUN: 7`)
1. During boot, the system initializes RF via `rfm_init()` (`FUN_c0d77d94`).
2. `rfm_init()` calls `rfc_init()` (`FUN_c0d6fe94`).
3. `rfc_init()` calls `rfc_card_init()` (`FUN_c1003c50`).
4. `rfc_card_init()` calls the card constructor `FUN_c0d7b970`.
5. Inside `FUN_c0d7b970`:
   ```c
   cVar1 = FUN_c0dab440(local_24, uVar6);  // Transceiver / bus validation
   cVar2 = FUN_c0d7bb70(param_1);          // RFFE device ID interrogation
   cVar3 = FUN_c0d7c224(param_1);          // Front-end table matching
   *(char *)(param_1 + 0x200) = cVar2 & cVar1 & cVar3; // Offset 0x800 in card object
   ```
6. Because the physical board uses WTR1605 RF360 front-end wiring while UFI001B's tables at `0xd2338000` expect WTR4905 discrete SAW-less wiring, the physical RFFE registers query returns unexpected device IDs.
7. Consequently, `cVar2` evaluates to `0`, and byte offset `0x800` is written with `0`.
8. Even though Patch 13 forced `FUN_c0d7c6d0` to return `1` and prevented `RFC_CARD_HWID failed to init` logging, the underlying RFFE devices and transceiver synthesizers were not configured with HMU05's real physical register values.
9. When Call Manager attempts to bring the radio online, the RF driver fails to establish PLL lock on the physical WTR1605 transceiver, keeping the modem in `SYS_OPRT_MODE_OFFLINE`.

---

## 8. Standardized Cold Reboot & Test Methodology

### 8.1 Why Cold Rebooting is Essential
During our testing on the physical dongle, we proved that restarting remoteproc via `/sys/class/remoteproc/remoteproc0/state` (or `/usr/bin/switch_modem_fw.sh`) has severe limitations:
1. **PM8916 PMIC RF Regulators:** The physical power rails to the WTR1605 transceiver (VDD_RF1, VDD_RF2, VDD_MX) and TCXO clock synthesizers are controlled by PMIC LDOs. Remoteproc SSR does not power-cycle these physical LDOs, leaving the transceiver in an indeterminate hardware state.
2. **Linux Kernel QRTR & BAM-DMUX Leakage:** Repeated SSR cycles trigger kernel warnings:
   `QRTR client node exceeds max lookup limit!` and packet drop failures.
3. **Hardware FIFO Reset:** A cold system reboot (`sync && reboot`) guarantees that:
   * The MSM8916 SoC cold-resets all hardware FIFOs and BAM channels.
   * PM8916 PMIC completely re-sequences all RF power rails from ground state.
   * Modem EFS partitions (`modemst1`, `modemst2`, `fsg`) are cleanly re-read.
   * All QRTR and BAM-DMUX endpoints attach in pristine order without stale IPC references.

### 8.2 Standard Step-by-Step Test Workflow
For every subsequent patch or modification:
```bash
# 1. Apply binary modifications to local patched files:
#    GitIgnore/compare/modem_ufi001b_patched/image/modem.bXX

# 2. Recompute and patch MBA SHA-256 hash table:
python3 GitIgnore/compare/ufi001b_hash_tool.py patch GitIgnore/compare/modem_ufi001b_patched/image <SEG_NUM> GitIgnore/compare/modem_ufi001b_patched/image/modem.b<SEG_NUM>

# 3. Verify all 28 ELF segments:
python3 GitIgnore/compare/ufi001b_hash_tool.py verify GitIgnore/compare/modem_ufi001b_patched/image

# 4. Deploy patched segments to the target dongle:
scp GitIgnore/compare/modem_ufi001b_patched/image/modem.b<SEG_NUM> \
    GitIgnore/compare/modem_ufi001b_patched/image/modem.b01 \
    GitIgnore/compare/modem_ufi001b_patched/image/modem.mdt \
    root@192.168.8.1:/lib/firmware/

# 5. Mirror to rollback staging:
ssh root@192.168.8.1 "cp -a /lib/firmware/modem.b* /lib/firmware/modem.mdt /lib/firmware/ufi001b_patched/"

# 6. Perform a clean cold reboot:
ssh root@192.168.8.1 "sync && reboot"

# 7. Await reboot (~20-25 seconds) and run cold telemetry suite:
ssh root@192.168.8.1 '
  echo "=== Remoteproc State ==="
  cat /sys/class/remoteproc/remoteproc0/state
  echo "=== DMS Operating Mode ==="
  qmicli -p -d /dev/wwan0qmi0 --dms-get-operating-mode
  echo "=== UIM Card Status ==="
  qmicli -p -d /dev/wwan0qmi0 --uim-get-card-status
  echo "=== NAS Serving System ==="
  qmicli -p -d /dev/wwan0qmi0 --nas-get-serving-system
'
```

---

## 9. Next Implementation Milestones

1. **Transplant Full `rfc_wtr1605_chile_rf360` Card Structure into `modem.b26`:**
   * Port the complete LTE Band 5 / Band 3 / Band 40 GRFC control tables and RFFE register sequences from HMU05 `modem.b18` (`0xc1c11c48`) and `modem.b19` (`0xc1e81b40`) directly into slot `0x26000` (`0xd2338000`) of `modem.b26`.
2. **Transition Baseband to Online Mode (`+CFUN: 1`):**
   * Confirm Call Manager successfully accepts `cm_ph_cmd_oprt_mode(SYS_OPRT_MODE_ONLINE)` and powers up the physical WTR1605 transceiver.
3. **Reliance Jio 4G LTE Attachment & Data Connection:**
   * Attach to Reliance Jio LTE Band 5 (MCC 405 MNC 861).
   * Bring up network interface `wwan0` using `qmicli --wds-start-network="apn=jionet,ip-type=4"`.
4. **The 30-Minute Stability Soak Test:**
   * Run continuous high-rate ICMP ping traffic and TCP streams across `wwan0`.
   * Confirm the system surpasses 15 minutes (900 seconds) without triggering `lte_ml1_common_timer.c:390` or `lte_ml1_sleepmgr_stm.c:4054` sleep crashes.
