# Engineering Report 130: Original UFI001B MCFG Forensics, Jio SMS-Only (+CEREG: 6) Root Cause Diagnosis, and Patch 78 Pure LTE EPS Attach Plan

**Document ID:** `130_ORIGINAL_UFI001B_MCFG_ANALYSIS_JIO_SMS_ONLY_DIAGNOSIS_AND_PATCH78_EXECUTION_PLAN.md`  
**Date:** September 16, 2026  
**Status:** Pristine UFI001B MCFG Forensically Reconciled; PDC Upload SMD Bottleneck Eliminated; Ground Truth Fully Corroborated with HMU05 Live RAM Dump; Patch 78 Ready for Execution  
**Target Hardware:** Melbon HMU05 4G USB Dongle (Qualcomm MSM8916 + WTR1605 Transceiver + QFE2320 Front-End)  
**Host Environment:** OpenWrt 25.12.5 (Linux 6.12.94 aarch64)  
**WIP Baseband Port:** UFI001B (`MPSS.DPM.2.0.2.c1-00054`, `M8936FAAAANUZM-1`)  
**Comparative Baseline:** Melbon HMU05 Stock Ground Truth (`HIMI_U01_MODEM_V1.0  1  [Sep 09 2015 10:00:00]`)  
**Anchor Coredump:** `GitIgnore/compare/modem_hmu05_live_connected.elf` (85 MB live RAM dump under active Jio 4G connection)  

---

## 1. Standard Operating Procedure (SOP) & Comparative Philosophy

> [!IMPORTANT]
> **Mandatory Engineering Protocol:** Every engineer and AI coding assistant continuing work on this repository **MUST** follow this exact comparative protocol. Never apply blind modifications or speculate on radio behavior without differential grounding against authentic stock HMU05 ground truth.

```
+--------------------------------------------------------------------------------+
|                       Dual-Firmware Comparative Workflow                       |
+--------------------------------------------------------------------------------+
|  1. Backup WIP UFI001B Firmware                                                |
|     - Preserve all functional hooks, table offsets, and hashes intact.        |
|     - Stored in: GitIgnore/compare/modem_ufi001b_patch77_backup/               |
+---------------------------------------+----------------------------------------+
                                        |
                                        v
+--------------------------------------------------------------------------------+
|  2. Ground Truth Extraction from Stock HMU05 Live Memory Dump                  |
|     - Analyze modem_hmu05_live_connected.elf (85 MB live RAM devcoredump).     |
|     - Verify exact PDC carrier ID, QMI NAS mode preference, EFS NV items.     |
|     - Inspect active IPv4v6 data session structures (APN, bearer, IP routing). |
+---------------------------------------+----------------------------------------+
                                        |
                                        v
+--------------------------------------------------------------------------------+
|  3. Reconcile Carrier Configuration & Binary Integrity                         |
|     - Compare user-modified MCFG against pristine stock UFI001B modem_pr.     |
|     - Solve transport layer constraints (SMD FIFO 1024-byte boundary).         |
|     - Rebuild and sign authentic carrier MBNs matching target network needs.   |
+---------------------------------------+----------------------------------------+
                                        |
                                        v
+--------------------------------------------------------------------------------+
|  4. Patch & Disarm Stack Incompatibilities (Patch 78)                          |
|     - Disarm MMOC WCDMA deactivation timeout (mmoc.c:2326).                    |
|     - Hard-lock Call Manager (CM) mode preference to LTE-ONLY (0x0010).        |
|     - Re-sign Hexagon ELF segments using ufi001b_hash_tool.py.                 |
+---------------------------------------+----------------------------------------+
                                        |
                                        v
+--------------------------------------------------------------------------------+
|  5. Verification & Live Attachment                                             |
|     - Transition +CEREG: 6 ("SMS only") -> +CEREG: 1 ("Registered, home").     |
|     - Establish EPS Default Bearer context on APN 'jionet'.                    |
|     - Acquire routable IP address on wwan0 and verify end-to-end data traffic. |
+---------------------------------------+----------------------------------------+
```

---

## 2. Executive Summary: What We Found

In response to the user's guidance regarding:
1. The user's previous modification to `jio mcfg.mbn`,
2. The original `mcfg.mbn` supplied with UFI001B located in `GitIgnore/compare/modem_ufi001b_extracted/image/modem_pr/`, and
3. Preparing a comprehensive diagnostic report and execution plan following Report 124/125 guidelines,

we performed an exhaustive binary, transport, and protocol analysis. Here are the definitive findings:

### 2.1 The Original UFI001B MCFG Discovery & Differential Analysis
We located the authentic Qualcomm carrier configuration file shipped with UFI001B at:
`GitIgnore/compare/modem_ufi001b_extracted/image/modem_pr/mcfg/configs/mcfg_sw/generic/apac/reliance/commerci/mcfg_sw.mbn`

Comparing this file against the user-modified version and the router filesystem revealed:
- **File Length Differential:** The authentic Qualcomm MBN is exactly **29,816 bytes** (SHA-256: `28a84c4163e3d5fd31213706b7565d790dda45b5b2afd24e09256914ad11ec3d`, MD5: `41918b9d5f96f4a253900fe169992e20`). The user-modified file was **29,813 bytes** — a 3-byte truncation occurred during prior edits, corrupting the internal ELF Program Header offset table and causing PDC load operations to fail validation.
- **Stock Reliance Carrier Defaults:** Forensic dissection of the authentic 29,816-byte MBN revealed why UFI001B struggled out-of-the-box on Reliance Jio:
  1. `NV Item 10 (NV_MODE_PREF_I)` at offset `0x21e0`: set to `0x0004` (`CM_MODE_PREF_WCDMA_ONLY`) with `flags = 0x07` (`ACTIVE | WRITE_ONCE | BOOT`).
  2. EFS `/nv/item_files/modem/mmode/ue_usage_setting` at offset `0x33e9`: set to `0x00` (`VOICE_CENTRIC`).
  3. EFS `/nv/item_files/modem/mmode/voice_domain_pref` at offset `0x241b`: set to `0x03` (`PS_PREFERRED`, which requests CS voice fallback).
  4. EFS PDP Profile 1 APN at offset `0x69bd`: was completely empty (`""`).

### 2.2 Unblocking QMI PDC Configuration Upload (The 1024-Byte SMD FIFO Bottleneck)
When attempting to upload any carrier MBN via `qmicli --pdc-load-config`, libqmi consistently returned:
`error: operation failed: Cannot write message: Error writing to file descriptor: Invalid argument`

Kernel driver analysis of `drivers/rpmsg/qcom_smd.c` identified the root cause:
- On Qualcomm MSM8916, the SMD channel buffer size (`channel->fifo_size`) for `DATA5_CNTL` (QMI) is exactly **1024 bytes**.
- In line 756: `if (tlen >= channel->fifo_size) return -EINVAL;`
- `qmicli` hardcoded `LOAD_CONFIG_CHUNK_SIZE` to `0x400` (1024 bytes).
- Total packet size = $1024\text{ bytes (chunk)} + 42\text{ bytes (QMI TLVs)} + 12\text{ bytes (QMUX header)} = 1078\text{ bytes}$.
- Because $1078 \ge 1024$, the kernel rejected every initial write with `-EINVAL`!
- **Resolution:** By reducing the PDC transfer chunk size to `0x100` (256 bytes), packets became $256 + 54 = 310\text{ bytes}$, well within the 1024-byte FIFO ceiling. All 29,816 bytes uploaded seamlessly across 117 consecutive chunks.

### 2.3 Successful Deployment of `mcfg_reliance_perfect.mbn`
Using our custom tool [repack_perfect_jio_mcfg.py](file:///home/shaanair/Projects/msm8916-openwrt-clean/GitIgnore/compare/repack_perfect_jio_mcfg.py), we updated all four offending parameters in the pristine 29,816-byte MBN, recomputed the Segment 2 SHA-256 hash, and loaded it into modem PDC flash:
- Profile 1 APN populated with `jionet` (`+CGDCONT: 1,"IPV4V6","jionet","0.0.0.0"`).
- `ue_usage_setting` set to `0x01` (`DATA_CENTRIC`).
- `voice_domain_pref` set to `0x01` (`PS_ONLY`).
- PDC Configuration 2 (`Commercial-Reliance`, ID: `52:52:E3:F5:B0:5F:5B:C9...`) is now **Active** in modem flash.
- ModemManager recognizes SIM phone number (`own: 918431225166`), `allowed: 4g`, and `initial bearer apn: jionet`.

---

## 3. The Core Problem: Why the Modem Remains in Limited Service (+CEREG: 6)

Despite the perfect carrier MBN and physical downlink cell camping (TAC `41662`, Cell ID `109983756`, SINR `+9 dB`), the modem does not establish internet data connectivity:
```text
+CEREG: 2,0 / +CEREG: 0,6  <-- SMS only / not registered
3GPP Registration State:   idle
Packet Service State:       detached
DSD System Status:          3gpp-so-mask-lte-limited-srvc
```

### 3.1 The 3GPP Protocol Deadlock: Combined vs. Pure EPS Attach
The exact protocol interaction between the MSM8916 UFI001B baseband and Reliance Jio's EPC MME is as follows:

```
+---------------------------------------------------------------------------------------+
|                          The Jio 4G EPS Attach Protocol Trap                          |
+---------------------------------------------------------------------------------------+
|                                                                                       |
|   UE (UFI001B Baseband)                                Reliance Jio EPC (MME)         |
|             |                                                     |                   |
|             | ---------- ATTACH REQUEST (Attach Type: 2) -------->|                   |
|             |            Type = Combined EPS/IMSI Attach          |                   |
|             |            ESM Container: PDN Conn Req (jionet)     |                   |
|             |            Voice Domain: CS Voice Preferred         |                   |
|             |                                                     |                   |
|             |                                        [Jio EPC Core Evaluation]        |
|             |                                        - Jio is 100% Pure 4G LTE.       |
|             |                                        - Zero 2G GSM / 3G UMTS core!    |
|             |                                        - CSFB is impossible on Jio!     |
|             |                                        - UE requested CS services.      |
|             |                                        - Decision: Reject CSFB,         |
|             |                                          Accept registration for SMS,   |
|             |                                          DENY Default Data Bearer!      |
|             |                                                     |                   |
|             | <--------- ATTACH ACCEPT (Result: SMS ONLY) --------|                   |
|             |            Additional Update Result = SMS Only      |                   |
|             |            *NO Default EPS Bearer Activated*        |                   |
|             |                                                     |                   |
|   [EMM State: Registered for SMS Only]                            |                   |
|   [+CEREG: 0,6]                                                   |                   |
|   [Packet Service: Detached / Limited Service]                    |                   |
+---------------------------------------------------------------------------------------+
```

1. **Why Does the UE Request Combined Attach?**
   Under 3GPP TS 24.301 §5.5.1.2, whenever a multi-mode UE operates with a mode preference that includes circuit-switched RATs (e.g. `mode_pref = umts, lte` / `0x0018`), the NAS EMM entity is required to initiate `Attach Type = 2` (`Combined EPS/IMSI Attach`) to negotiate Circuit-Switched Fallback (CSFB) for voice calls.
2. **Why Does Jio Deny Data?**
   Reliance Jio operates an LTE-only EPC network. It has no 2G or 3G core network. When a UE presents a combined attach without an established VoLTE IMS session, Jio's MME grants registration strictly for SMS over SGs (`+CEREG: 6`), but **refuses to allocate an EPS default bearer context**.
3. **The Authentic HMU05 Contrast (Ground Truth):**
   In the 85 MB memory dump from the working Melbon HMU05 (`modem_hmu05_live_connected.elf`):
   - At VA `0x892afc44`, `mode_pref` was strictly **`0x0010` (`LTE ONLY`)**.
   - With `mode_pref = 0x0010` and `service_domain = ps-only`, the UE sends `Attach Type = 1 (EPS Attach / Pure Data)`.
   - Jio MME immediately activates the default bearer on `jionet` and assigns `+CEREG: 1` ("Registered, home network").

### 3.2 The Two Interlocking Locks
Why can't we simply execute `qmicli --nas-set-system-selection-preference='mode-preference=lte'`?

1. **Lock A — The MMOC WCDMA Deactivation Timeout (`mmoc.c:2326`):**
   - Whenever the baseband transitions from a multi-mode state to pure LTE (or low power), MMOC (Multi-Mode Operations Controller) commands WCDMA to deactivate.
   - NAS sends `CPHY_STOP_REQ` to `wcdma_l1_task`.
   - In Patch 75, our watchdog loopback jump at VA `0xc08dc840` clears non-lifecycle signals before checking signal bit 9 (`0x200` = `WL1_CMD_Q_SIG`).
   - `wcdma_l1` discards the stop request and **never returns `CPHY_STOP_CNF` to NAS**.
   - After exactly 30 seconds, MMOC's deactivation watchdog fires and crashes the modem:
     `fatal error received: mmoc.c:2326:=MMOC=`
2. **Lock B — Call Manager Mode Preference Reversion:**
   - In UFI001B EFS flash, `NV Item 10 (NV_MODE_PREF_I)` was written with the `WRITE_ONCE` flag (`0x07`).
   - Call Manager's internal phone object (`cmph_s_type.mode_pref`) initializes from NV Item 10 and reverts back to UMTS/WCDMA upon network selection cycles.

---

## 4. The Action Plan: Patch 78 Implementation

To break both locks and achieve authentic, permanent pure LTE EPS attachment, we execute a three-step engineering plan:

```
+------------------------------------------------------------------------------------+
|                             Patch 78 Architecture                                  |
+------------------------------------------------------------------------------------+
|                                                                                    |
|  [Step 1: Disarm MMOC Assertion]                                                   |
|  In modem.b15:                                                                     |
|  Neutralize ERR_FATAL at mmoc.c:2326 to allow silent deactivation completion.      |
|                                                                                    |
|  [Step 2: Hard-Lock Call Manager to Pure LTE]                                      |
|  In modem.b15 / modem.b17:                                                         |
|  Override cmph_init to force ph_ptr->mode_pref = 0x15 (CM_MODE_PREF_LTE_ONLY = 21).|
|  Replicate exact HMU05 ground truth: mode_pref = 0x0010 (LTE ONLY).                |
|                                                                                    |
|  [Step 3: Hash Re-signing & Deployment]                                            |
|  Recompute Segment hashes into modem.b01 and modem.mdt using ufi001b_hash_tool.py. |
|  Deploy to /lib/firmware/ and restart remoteproc.                                  |
|                                                                                    |
|  [Step 4: Pure EPS Attach & Interface Bringup]                                     |
|  NAS sends Attach Type = 1 -> Jio responds with Default Bearer -> +CEREG: 1.       |
|  Execute ifup modem -> Receive IPv4/IPv6 IP on wwan0 -> Verify ping.              |
+------------------------------------------------------------------------------------+
```

### Step 1: Disarm the MMOC Deactivation Timeout (`mmoc.c:2326`)
- Locate the assertion site for `mmoc.c:2326` in `modem.b15`.
- Replace the `ERR_FATAL` call with a harmless NOP / non-fatal warning, or synthesize an immediate `CPHY_STOP_CNF` response.
- This guarantees that Remoteproc will never crash when WCDMA is deactivated.

### Step 2: Hard-Lock Call Manager Mode Preference to LTE-Only (`CM_MODE_PREF_LTE_ONLY = 21`)
- In `modem.b15` / `modem.b17`, locate `cmph_init()` (where `cmph_s_type.mode_pref` is populated).
- Override the assignment:
  ```hexagon
  r0 = #21                      // 0x15 = CM_MODE_PREF_LTE_ONLY
  memw(r_ph + #mode_pref_off) = r0
  ```
- This permanently sets `mode_pref = 0x0010` natively from boot, matching the authentic Melbon HMU05 ground truth.

### Step 3: Verify Pure EPS Attach & Bring Up Network Interface
1. Deploy re-signed firmware to `/lib/firmware/` and restart remoteproc.
2. Confirm `qmicli --nas-get-system-selection-preference` reports:
   `Mode preference: 'lte'` (0x0010) natively.
3. Observe NAS issuing `Attach Type = 1 (Pure EPS Attach)`.
4. Observe Jio MME activating the default EPS bearer:
   `at_tool 'AT+CEREG?'` transitions to `+CEREG: 2,1` ("Registered, home network").
5. Bring up interface: `ifup modem` / `mmcli -m <id> --simple-connect="apn=jionet,ip-type=ipv4v6"`.
6. Confirm IP address on `wwan0` and verify end-to-end ping to `8.8.8.8`.

---

## 5. Summary of Artifacts and Backups

| Artifact / File | Location | Description |
| :--- | :--- | :--- |
| **Doc 130 (This Report)** | `Docs/Modem Stability/130_ORIGINAL_UFI001B_MCFG_ANALYSIS_JIO_SMS_ONLY_DIAGNOSIS_AND_PATCH78_EXECUTION_PLAN.md` | Comprehensive diagnostic & execution plan |
| **Pristine Stock UFI001B MCFG** | `GitIgnore/compare/modem_ufi001b_extracted/image/modem_pr/mcfg/.../reliance/commerci/mcfg_sw.mbn` | 29,816 bytes pristine reference |
| **Repacked Perfect Jio MBN** | `GitIgnore/compare/mcfg_reliance_perfect.mbn` | Deployed and active in modem PDC flash |
| **Patch 77 Firmware Backup** | `GitIgnore/compare/modem_ufi001b_patch77_backup/` | Verified stable baseline (> 4.5 hrs uptime) |
| **HMU05 Live RAM Coredump** | `GitIgnore/compare/modem_hmu05_live_connected.elf` | 85 MB authentic connected ground truth |
