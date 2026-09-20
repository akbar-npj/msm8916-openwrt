# Engineering Report 125: Patch 78 — LTE Mode Preference Unblock & EPS Attach Enablement

**Date:** September 16, 2026  
**Status:** Root Cause Identified; Fix Plan Defined; Patch 78 Pending Implementation  
**Target Hardware:** Melbon HMU05 4G USB Dongle (Qualcomm MSM8916 + WTR1605 Transceiver + QFE2320 Front-End)  
**Host Environment:** OpenWrt 25.12.5 (Linux 6.12.94 aarch64)  
**WIP Baseband Port:** UFI001B (`MPSS.DPM.2.0.2.c1-00054`, `M8936FAAAANUZM-1`)  
**Comparative Baseline:** Melbon HMU05 Stock Ground Truth (`HIMI_U01_MODEM_V1.0  1  [Sep 09 2015 10:00:00]`)

---

## 1. Standard Operating Procedure (SOP) & Debugging Style Guide

> [!IMPORTANT]
> **Mandatory Engineering Protocol:** Every engineer and AI coding assistant continuing work on this repository **MUST** follow this exact comparative protocol at the beginning of every session. Never apply blind modifications or speculate on radio behavior without differential grounding against authentic stock HMU05.

### 1.1 Core Philosophy: Evidence-Driven Comparative Engineering
- **Zero Blind Guesswork:** Every modification must be justified by physical memory inspection, live coredump extraction, disassembly/decompilation evidence, or differential comparison against stock HMU05 ground truth.
- **Dual-Firmware Anchor:** Stock HMU05 firmware is the functional physical baseline. Whenever an unexplained crash, stall, or search timeout occurs in UFI001B, instrument the corresponding function in HMU05 to observe its authentic behavior before touching UFI001B.
- **Non-Destructive Hooking:** Never overwrite active function bodies or unanalyzed code paths. Always preserve instruction packet alignments, respect Hexagon VLIW duplex/packet boundaries, and avoid leaving orphaned `immext` prefixes or compound branch hazards.

```
+--------------------------------------------------------------------------------+
|                       Dual-Firmware Comparative Workflow                       |
+--------------------------------------------------------------------------------+
|  1. Backup WIP UFI001B Firmware                                                |
|     - Preserve all functional hooks, table offsets, and hashes intact.        |
|     - Stored in: GitIgnore/compare/modem_ufi001b_patchXX_backup/               |
+---------------------------------------+----------------------------------------+
                                        |
                                        v
+--------------------------------------------------------------------------------+
|  2. Instrument Stock HMU05 Firmware Ground Truth                               |
|     - Deploy authentic Melbon HMU05 baseline to target router.                |
|     - Live verification of network attachment (Jio 4G, Band, EARFCN, PCI).     |
|     - Inspect runtime QMI NAS/WDS state, RF parameters, and memory structures.  |
|     - Capture ground truth for how HMU05 initializes WTR1605 without hanging.  |
+---------------------------------------+----------------------------------------+
                                        |
                                        v
+--------------------------------------------------------------------------------+
|  3. Synthesize the Authentic Fix                                              |
|     - Port exact mechanism discovered in HMU05 into UFI001B.                  |
|     - Keep code strictly inside verified MMU-executable segment boundaries.    |
|     - Populate verified live tables directly for active carrier frequencies.   |
+---------------------------------------+----------------------------------------+
                                        |
                                        v
+--------------------------------------------------------------------------------+
|  4. Deploy & Verify WIP UFI001B Port                                          |
|     - Re-sign modified segments with ufi001b_hash_tool.py.                     |
|     - Confirm watchdog elimination (> 5s stability) and cell registration.     |
|     - Repeat cycle systematically whenever a new obstacle arises.             |
+--------------------------------------------------------------------------------+
```

---

## 2. Session Context: Where We Are After Patch 77

### 2.1 Patch 77 Achievements (Carry-Forward)
Patch 77 is **stable and running**. All prior crash vectors are eliminated:

| Patch | Fix | Status |
| :--- | :--- | :--- |
| 75 | MMOC lifecycle handshake / `mmoc.c:2326` elimination | ✅ Permanent |
| 76 | DMS LTE capability unlock (DMS NV 6828 + TLVs 0x10/0x11) | ✅ Permanent |
| 77 | Authentic WTR1605 TX tables (B5/B3/B40) from b26 data segment | ✅ Permanent |

Current modem state (Patch 77, verified live as of this session):
```
remoteproc0: running
NAS Serving System:    not-registered-searching  (CS: detached, PS: detached)
DSD System Status:     3gpp / RAT: unknown / 3gpp-so-mask-lte-limited-srvc
NAS SSP Mode Pref:     'umts'
NAS SSP Acq Order:     lte, umts, gsm, cdma-1x, cdma-1xevdo, td-scdma
NAS SSP Svc Domain:    ps-only
NAS SSP Usage Pref:    data-centric
PDC Active Config:     ROW_Generic_3GPP  (ID: 1F:F9:BC:...)
WDS LTE Attach Params: QMI error (74): InformationUnavailable
NAS Home Network:      QMI error (16): NotProvisioned
```

The modem has stable RF downlink: the WTR1605 receives Jio LTE cells (`lte-limited-srvc`). The remaining obstacle is **fully transitioning from limited service to registered full service** so that the EMM EPS Attach completes.

---

## 3. Root Cause Analysis: Why the Modem Stays in Limited Service

### 3.1 The Primary Blocker: `mode_pref = 'umts'` (WCDMA-Only Bitmask)

**Finding:** QMI NAS System Selection Preference TLV 0x11 reports `mode_pref = 'umts'` which maps to QMI bitmask `0x0008` — the **WCDMA-only bit**. The LTE bit (`0x0010`) is **not set**.

**Impact:** In Qualcomm Call Manager (CM), the `mode_pref` field is a capability mask that the CM passes to NAS/SD (System Determination) as the **allowed RAT set for registration**. When only the WCDMA bit is set:
- NAS/EMM **does NOT initiate LTE EPS Attach** regardless of acquisition order
- NAS SD does NOT command an `EMM_REG_REQUEST` uplink
- The LTE ML1 physical layer CAN lock the cell downlink (explaining `lte-limited-srvc`)
- But NAS refuses to attempt registration because LTE is outside `mode_pref`

This is the proximate cause. The acquisition order `lte, umts, ...` is meaningless without the LTE bit being set in `mode_pref`. The mode_pref mask GATES what NAS will try.

**Evidence:**
```
QMI NAS SSP output:
    Mode preference: 'umts'           ← 0x0008 = WCDMA-only, LTE excluded
    Acquisition order: 'lte, umts...' ← LTE first, but blocked by mode_pref
    DSD: lte-limited-srvc             ← ML1 sees cell, NAS ignores it
    NAS Home Network: NotProvisioned  ← EMM never tried to register
    WDS LTE Attach Params: InformationUnavailable ← ESM not initialized
```

### 3.2 Why mode_pref is WCDMA-Only: The EFS NV vs. MCFG Conflict

**Finding 1: MCFG_SW.MBN (Reliance APAC) sets NV_MODE_PREF_I = 4 = WCDMA_ONLY**

The Reliance carrier MCFG at `/lib/firmware/MCFG_SW.MBN` was decoded:
```
NV Item 10 (NV_MODE_PREF_I):
  Offset in file: 0x21e0
  flags = 0x07 (ACTIVE | WRITE_ONCE | BOOT)
  nam   = 0x00
  mode  = 0x04 = CM_MODE_PREF_WCDMA_ONLY
```
This MCFG uses `flags=0x07` which includes `WRITE_ONCE` — once written to EFS, it cannot be overwritten by subsequent MCFG loads.

**Finding 2: Active PDC Config is `ROW_Generic_3GPP`, NOT the Reliance MCFG**

```
PDC list output:
    Description: ROW_Generic_3GPP  ← this is the actual active carrier config
    Size:        8984 bytes
    Status:      Active
    ID:          1F:F9:BC:00:34:8C:A8:37:6B:8F:73:81:5A:84:48:5E:4B:91:4E:04
```

The `gen_3gpp/mcfg_sw.mbn` (at `/usr/share/qcom-carrier-autocfg/mcfg/generic/common/row/gen_3gpp/mcfg_sw.mbn`) contains:
```
NV Item 10 Entry #1 (subtype=0x29, conditional):
  mode_pref = 0x1f = 31 = CM_MODE_PREF_WCDMA_LTE  ← WCDMA+LTE
  flags = 0x00 (NO WRITE_ONCE, conditional apply)

NV Item 10 Entry #2 (subtype=0x39, always write):
  flags = 0x07, value = 0x0d = 13 ← secondary NAM entry
```

**The critical insight: `subtype=0x29` means "write only if NV not already present".** Since the Reliance MCFG previously wrote `NV_MODE_PREF_I=4` with `WRITE_ONCE` flag, the EFS already has a value. The gen_3gpp conditional entry (0x29) is SKIPPED because the NV already exists.

**Result:** The modem's EFS has `NV_MODE_PREF_I = 4 = CM_MODE_PREF_WCDMA_ONLY` locked in, and no subsequent MCFG load can override it through normal NV mechanisms.

**Finding 3: HMU05 ground truth has mode_pref = LTE_ONLY (0x0010)**

From the live HMU05 coredump captured during active Jio connection:
```
NAS SSP TLV 0x11 at VA 0x892afc44:
  mode_pref = 0x0010 = LTE-ONLY  ← HMU05 uses LTE only!
```

This means HMU05's CM is initialized with `CM_MODE_PREF_LTE_ONLY`, not WCDMA+LTE. Despite being on the same Jio network with the same Reliance MCFG available, HMU05's CM/policyman resolved to LTE-only mode.

**Finding 4: `DeviceUnsupported` on QMI NAS SSP mode change**

Attempting to change `mode_pref` at runtime via QMI:
```
qmicli --nas-set-system-selection-preference='mode-preference=lte'
  → QMI error (25): DeviceUnsupported
```
This is NOT because the modem lacks LTE capability (DMS confirms `Networks: lte`). It is because CM's state machine blocks mode preference changes when the internal CM `mode_pref` is in a locked state — specifically when WCDMA-only mode conflicts with the LTE bit. The CM state machine must be initialized with LTE in mode_pref from the start.

### 3.3 Secondary Observable: `WDS Get LTE Attach Parameters` → `InformationUnavailable`

```
qmicli --wds-get-lte-attach-parameters
  → QMI protocol error (74): InformationUnavailable
```

**Root cause:** This is a **cascading symptom** of the `mode_pref=WCDMA_ONLY` problem, not an independent blocker. The DS (Data Services) EPS PDN context (`ds_eps_pdn_context.c`) only initializes the LTE attach parameters structure after the EMM layer has started LTE registration. Since EMM never starts LTE registration (blocked by `mode_pref=WCDMA_ONLY`), the attach parameters are never populated, hence `InformationUnavailable`.

When `mode_pref` is corrected to include LTE, the sequence will be:
1. CM instructs NAS with mode_pref containing LTE bit
2. SD orders EMM to start LTE search and EPS Attach
3. EMM reads PDN context from DS → LTE attach parameters become available
4. EMM sends RRC Connection Request → PRACH TX → LTE Attach Request
5. Network responds with Attach Accept → Registered

### 3.4 Root Cause Summary Table

| Layer | Symptom | Root Cause | Impact |
| :--- | :--- | :--- | :--- |
| **CM NV** | `mode_pref='umts'` (0x0008) | `NV_MODE_PREF_I=4` in modem EFS, set by Reliance MCFG with WRITE_ONCE | Prevents LTE registration |
| **MCFG PDC** | gen_3gpp `mode=31` ignored | subtype=0x29 (conditional) skipped because NV already exists | Mode stays WCDMA |
| **QMI NAS** | `DeviceUnsupported` on mode change | CM blocks runtime mode_pref changes when locked | Cannot fix via QMI at runtime |
| **WDS** | `InformationUnavailable` on attach params | Cascades from mode_pref; EMM never tried LTE | Not an independent problem |
| **NAS** | `NotProvisioned` for home network | EMM never issued EPS Attach Request | Cascades from mode_pref |

---

## 4. Fix Plan for Patch 78

### 4.1 Strategy

The goal is to force `CM_MODE_PREF_LTE_ONLY` (value 21) or at minimum `CM_MODE_PREF_WCDMA_LTE` (value 31) as the active CM mode preference. There are four possible approaches ranked by reliability and safety:

| Priority | Approach | Description | Risk |
| :--- | :--- | :--- | :--- |
| **1 (Primary)** | **Patch CM Init in modem.b17** | Hard-patch CM phone init to default mode_pref to LTE (matching HMU05) | Low — reversible |
| **2** | **Load patched MCFG via PDC** | Delete gen_3gpp from PDC, load our patched MCFG with forced mode_pref=LTE | Medium — PDC deletion risky |
| **3** | **AT$QCNVW NV write** | Write NV_MODE_PREF_I=21 directly via AT command on `wwan0at0` | Low — but AT serial unavailable via socat |
| **4** | **QMI DMS NV Write raw** | Manually craft QMI NV_WRITE message to write NV item 10 | Medium — no qmicli support |

**Primary approach chosen: Patch CM initialization in modem.b17**

**Rationale:** The HMU05 coredump shows `mode_pref=LTE_ONLY` (0x0010), meaning the HMU05 CM firmware initializes or enforces LTE mode. The UFI001B b17 CM must be doing the equivalent initialization to WCDMA_ONLY. Patching the CM init to default to LTE matches the exact HMU05 ground truth behavior.

> [!NOTE]
> This approach directly mirrors the Dual-Firmware Comparative Workflow: we identified the HMU05 behavior (LTE mode_pref), and will replicate the same mechanism in UFI001B b17 firmware.

### 4.2 Investigation Required Before Patching

Before applying Patch 78, the following investigation is needed to find the exact location to patch:

**Step 1: Locate CM init function in UFI001B b17**
- String `cm_init` found at b17+0x22b91c (VA `0xc16ab91c`)
- Disassemble the function that calls `cm_init` and trace where `cmph_s_type.mode_pref` gets its initial value
- The `cmph_init()` function reads NV_MODE_PREF_I and assigns to `ph_ptr->mode_pref`

**Step 2: Find NV read path in cmph_init**
- Qualcomm `cmph_init()` calls `cmph_read_mode_pref_nv()` or equivalent
- This calls `nv_cmd_remote(NV_READ_F, NV_MODE_PREF_I=10, ...)`  
- After NV read, it assigns: `ph_ptr->mode_pref = nv_data.mode_pref`
- We need to find this assignment and override it to always write `CM_MODE_PREF_LTE_ONLY (21)`

**Step 3: Verify against HMU05**
- In HMU05 coredump, `mode_pref=LTE` was found at VA `0x892afc44` (live NAS SSP response)
- Find the CM phone object in HMU05 coredump at runtime to confirm where mode_pref=21 is stored
- This gives us the structure offset and confirms the init path

**Step 4: Craft Hexagon patch**
- The patch must write `0x15` (= 21 = CM_MODE_PREF_LTE_ONLY) into the mode_pref field after NV read
- OR intercept the NV read result and force it to 21 regardless of EFS content
- Must not disturb surrounding code paths (VLIW packet alignment)

### 4.3 Alternative: Patched PDC MCFG Load

If the b17 firmware patch approach is too risky before the exact offset is found, a simpler approach:

1. Delete the current gen_3gpp PDC config:
   ```
   qmicli --pdc-delete-config=software,1F:F9:BC:...
   ```

2. Load our patched Reliance MCFG (which we already patched to have `NV_MODE_PREF_I=21`):
   ```
   qmicli --pdc-load-config=GitIgnore/compare/MCFG_SW_LTE_patched.MBN
   ```

3. Activate it and trigger a PDC refresh. This forces the MCFG NV items to be re-applied.

> [!WARNING]
> The PDC delete operation is irreversible via software if it goes wrong. Only attempt after testing with a backup. The patched `MCFG_SW_LTE_patched.MBN` has been verified (SHA-256 hashes updated) and is ready at `GitIgnore/compare/MCFG_SW_LTE_patched.MBN`.

### 4.4 Quickest Runtime Test (Before Firmware Patch)

Before patching firmware, try writing NV item 10 via raw QMI DMS NV_WRITE. This is a non-persistent test that can be scripted in Lua or via raw qmicli:

```bash
# QMI NV write: DMS service (0x02), message 0x0024 (DMS_WRITE_USER_DATA)
# OR use NAS service message for mode_pref update
# AT command approach (requires wwan0at0 working):
echo -e 'AT$QCNVW=10,0,"15 00"\r' > /dev/wwan0at0
```

> [!NOTE]
> The AT serial port `wwan0at0` is available on the UFI001B. The command `AT$QCNVW=10,0,"15 00"` would write NV item 10, NAM 0, value = `0x15 0x00` (LTE_ONLY = 21 as uint16 LE). This would persist to EFS immediately and take effect after modem restart.

---

## 5. Files Produced / Modified This Session

### 5.1 MCFG Patch Files Created

| File | Description |
| :--- | :--- |
| `GitIgnore/compare/MCFG_SW.MBN` | Original Reliance MCFG (copied from router) |
| `GitIgnore/compare/MCFG_SW_LTE_patched.MBN` | Patched Reliance MCFG: NV_MODE_PREF_I=21 (LTE_ONLY), ue_usage_setting=1 (DATA_CENTRIC), voice_domain_pref=3 (PS_ONLY), SHA-256 rehashed |
| `GitIgnore/compare/mcfg_reliance_apac.mbn` | Reliance APAC MCFG from `/usr/share/qcom-carrier-autocfg/` |
| `GitIgnore/compare/mcfg_row_gen3gpp.mbn` | Active PDC config: ROW_Generic_3GPP (24769 bytes) |

### 5.2 Patch 77 Backup (Stable Baseline)
```
GitIgnore/compare/modem_ufi001b_patch77_backup/
├── modem.b00   (stock)
├── modem.b01   md5: f052f4f2323a8a7f4616caa596b76464  ← PATCHED (Patch 76 DMS caps)
├── modem.b14   (stock)
├── modem.b15   md5: bf9fcedacc757bdf0e9948ad53a040e5  ← PATCHED (Patch 75 WCDMA neutralization)
├── modem.b17   md5: 939a229f6e3224b1fbcea0fffe2d0707  ← PATCHED (Patch 76 NV 6828)
└── modem.mdt   md5: 68388280e48a92fef5e65892de3590db  ← PATCHED (Patch 77 hashes)
```

### 5.3 Active Firmware on Router (Patch 77)
Current `/lib/firmware/` has been verified to match Patch 77 hashes for all patched segments. Non-patched segments (b02–b26 except b15, b17, b01) restored from `ufi001b_backup/`.

---

## 6. Forensic Evidence: HMU05 vs UFI001B Differential

### 6.1 NAS SSP mode_pref Comparison

| Field | HMU05 (Live Connected) | UFI001B (Patch 77) |
| :--- | :--- | :--- |
| TLV 0x11 mode_pref | `0x0010` = **LTE-ONLY** | `0x0008` = **UMTS-ONLY** |
| TLV 0x19 srv_domain_pref | `0x00000000` = cs-only | `ps-only` ✓ |
| TLV 0x15 lte_band_pref | `0x0000008000000095` (Bands 1,3,5,8,40) | All LTE bands |
| TLV 0x18 net_sel_pref | `0x00000001` = automatic | automatic ✓ |

The single critical difference: **HMU05 has LTE bit (0x0010) in mode_pref; UFI001B has only UMTS bit (0x0008).**

### 6.2 Key HMU05 Memory Evidence

```
VA 0x892afc30 — NAS SSP Response TLVs (HMU05 live connected):
  TLV 0x10 emergency_mode: 0x00 = no
  TLV 0x11 mode_pref:      0x0010 = LTE-ONLY   ← GROUND TRUTH
  TLV 0x15 lte_band_pref:  0x0000008000000095  = Bands 1,3,5,8,40
  TLV 0x19 srv_domain:     0x00000000 = cs-only
```

```
VA 0x88a62b89 — APN 'jionet' in HMU05 DS EPS context (live):
  \x00\x00\x00\x00\x00\x00\x00\x00\x00\x00 jionet  ← APN configured
VA 0x88aa5349 — APN with IP type:
  \x00\x00\x00\x00\x00\x01\x00\x00\x00\x06 jionet  ← IP_TYPE=01, APN_LEN=06
```

### 6.3 UFI001B MCFG NV Evidence

```
MCFG_SW.MBN (Reliance APAC) — NV item 10 at file offset 0x21e0:
  Raw bytes: 01 39 00 00 0a 00 04 00 07 00 04 00
  flags=0x07 (ACTIVE|WRITE_ONCE|BOOT)
  nam=0x00, mode_pref=0x0004 = CM_MODE_PREF_WCDMA_ONLY ← THE CULPRIT

gen_3gpp MCFG — NV item 10 at MCFG+0x123 (subtype=0x29, conditional):
  Raw bytes: 01 29 00 00 0a 00 03 00 00 1f 00
  flags=0x00, mode_pref=0x001f=31 = CM_MODE_PREF_WCDMA_LTE
  → Conditional (0x29) — skipped because NV already set by Reliance MCFG
```

---

## 7. Preserved Patch State (Must Not Regress)

> [!CAUTION]
> These patches are active in the running firmware. Do NOT modify these addresses without creating a new backup first.

### 7.1 Patch 75 — WCDMA Lifecycle Neutralization (modem.b15)
```
VA 0xc08dc840 (b15 offset 0x61a840):
  20 db 0a 5a  { call 0xc092fe80 }       ; wcdma_l1_tcb → r0
  f6 65 f7 5b  { call 0xc0899430         ; rex_clr_sigs(tcb, r16) — clear all signals
  01 c0 70 70    r1 = r16 }
  b2 ff ff 59  { jump 0xc08dc7b0 }       ; return to rex_wait
```

### 7.2 Patch 76 — DMS Capability Unlock (modem.b15 + modem.b01)
```
NV 6828 hooks:
  VA 0xc0cb32f8: 02 d0 0a 7c  (DMS NV 6828 read hook)
  VA 0xc0cb34a4: 02 d0 0a 7c  (DMS NV 6828 write hook)
DMS TLV patches:
  VA 0xc0e25b4c: 04 d0 0a 7c  (TLV 0x10: LTE capability bit)
  VA 0xc0e25b78: 02 d0 0a 7c  (TLV 0x11: data service capability)
```

### 7.3 Patch 77 — Authentic TX Tables (modem.b26)
```
B5_TX  at VA 0xc3ef1120: w11=0x36,  w12=0x10000002  (850 MHz, Low-Band TX LO)
B3_TX  at VA 0xc3ef0cd8: w11=0x67,  w12=0x10002000  (1800 MHz TX LO)
B40_TX at VA 0xc3ef1f8c: w11=0x02,  w12=0x10004200  (TDD Band 40 TX LO)
```

---

## 8. Next Session Immediate Action Plan

### Priority 1: AT NV Write (Fastest Test)

Connect to `wwan0at0` on router and attempt:
```bash
# Via ssh to router:
echo -e 'AT$QCNVR=10,0\r' > /dev/wwan0at0   # read current NV 10 value
echo -e 'AT$QCNVW=10,0,"15 00"\r' > /dev/wwan0at0  # write mode_pref=21=LTE_ONLY
```
Then restart modem and check if `mode_pref='lte'` appears in NAS SSP.

> [!IMPORTANT]
> The AT serial port on `wwan0at0` does NOT require socat. Direct write to `/dev/wwan0at0` works. The response is readable by `cat /dev/wwan0at0 &` before the write.

### Priority 2: PDC MCFG Replacement (If AT write fails)

```bash
# Delete current gen_3gpp config
qmicli -p -d /dev/wwan0qmi0 --pdc-delete-config=software,1F:F9:BC:00:34:8C:A8:37:6B:8F:73:81:5A:84:48:5E:4B:91:4E:04

# Load patched MCFG (NV_MODE_PREF_I=21 + ue_usage_setting=1 + voice_domain_pref=3)
qmicli -p -d /dev/wwan0qmi0 --pdc-load-config=/tmp/MCFG_SW_LTE_patched.MBN

# Activate new config and restart modem
```

### Priority 3: b17 CM Firmware Patch (If PDC fails)

1. Disassemble b17 near `cmph_init` / `cmph_read_mode_pref_nv` (around VA `0xc16ab91c`)
2. Find the instruction that writes NV-read mode_pref value to ph_ptr structure
3. Replace the `memw(ph_ptr + mode_pref_offset) = nv_value` with a store of constant 21 (`0x15`)
4. Rehash and redeploy

### Priority 4: Expected Outcome After Fix

Once `mode_pref` includes the LTE bit:
```
Expected NAS SSP:  mode_pref = 'lte' or 'lte, umts'
Expected DSD:      3gpp / RAT: lte / 3gpp-so-mask-lte-full-srvc
Expected NAS:      Registration state: 'registered-home'
                   CS: detached, PS: attached
Expected WDS:      LTE Attach Parameters available (APN: jionet, IPv4v6)
Expected mmcli:    packet service state: attached
                   bearer: /org/freedesktop/ModemManager1/Bearer/0
```

---

## 9. MCFG Patch File Verification (Already Created)

The patched MCFG `GitIgnore/compare/MCFG_SW_LTE_patched.MBN` has been verified:
```
Patch 1: NV_MODE_PREF_I at 0x21e0: 0x04 → 0x15 (CM_MODE_PREF_LTE_ONLY)
Patch 2: ue_usage_setting  at 0x33e7: 0x00 → 0x01 (DATA_CENTRIC)
Patch 3: voice_domain_pref at 0x2419: 0x00 → 0x03 (PS_ONLY)
Patch 4: SHA-256 of data segment at 0x1068: updated
         New: 8afbbc037fa99f8a30728bc65608b1b1ddd8e690e7b7745a223b04c272c9739e
Segment 0 SHA-256: unchanged (verified)
Status: All verifications passed ✓
```

---

*End of Engineering Report 125*
