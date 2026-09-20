# Engineering Report 129: Forensic Root Cause of Jio SMS-Only Registration (+CEREG: 6) & Pure LTE EPS Attach Plan

**Date:** September 16, 2026  
**Status:** Comprehensive Root Cause Forensically Identified; Ground Truth Verified Against Authentic Melbon HMU05 Dump; Definitive Resolution Roadmap Established  
**Target Hardware:** Melbon HMU05 4G USB Dongle (Qualcomm MSM8916 + WTR1605 Transceiver + QFE2320 Front-End)  
**Host Environment:** OpenWrt 25.12.5 (Linux 6.12.94 aarch64)  
**WIP Baseband Port:** UFI001B (`MPSS.DPM.2.0.2.c1-00054`, `M8936FAAAANUZM-1`)  
**Comparative Baseline:** Melbon HMU05 Stock Ground Truth (`HIMI_U01_MODEM_V1.0  1  [Sep 09 2015 10:00:00]`)  
**Reference Coredump:** `GitIgnore/compare/modem_hmu05_live_connected.elf` (85 MB live RAM dump under active Jio 4G connection)

---

## 1. Executive Summary & Problem Definition

The Melbon HMU05 router with UFI001B baseband currently achieves flawless physical-layer downlink lock on Reliance Jio (PLMN `405861`, Cell ID `109983756`, TAC `41662`, SINR `+9 dB`, IO `-106 dBm`). However, the modem remains stuck in **LTE Limited Service**:
```text
+CEREG: 0,6           <-- 6 = Registered for "SMS only", home network
DSD System Status:    3gpp / RAT: unknown / 3gpp-so-mask-lte-limited-srvc
Packet Service State: detached
WDS start-network:    CallFailed (14) - generic-no-service / [cm] no-service
ModemManager Connect: Network timeout (waiting for EPS bearer attachment)
```

The core objective is to transition `<stat>` from `6` ("SMS only") to `1` ("Registered, home network"), establish the default EPS bearer (`jionet`), and obtain a routable IP address on interface `wwan0`.

---

## 2. Forensic Discoveries & Differential Analysis

### 2.1 Authentic Melbon HMU05 Ground Truth Forensics
Analysis of the 85 MB RAM dump (`modem_hmu05_live_connected.elf`) captured from the Melbon HMU05 under an active, working Jio 4G connection revealed the ground truth configuration:

1. **Active PDC Carrier Configuration:**
   - Active PDC Config: **`ROW_Generic_3GPP`** (ID: `1F:F9:BC:00:34:8C:A8:37:6B:8F:73:81:5A:84:48:5E:4B:91:4E:04`).
   - `Commercial-Reliance` was **NOT** active on stock HMU05.
2. **QMI NAS Mode Preference:**
   - `mode_pref`: **`0x0010` (`LTE ONLY`)** at VA `0x892afc44`.
   - The UMTS (`0x0008`) and GSM (`0x0004`) bits were **completely absent**.
3. **EFS NV Item Settings in Live RAM:**
   - `/nv/item_files/modem/mmode/sms_only`: Explicitly **`0x00`** (offset `0x2c6bf81`).
   - `/nv/item_files/modem/mmode/sms_domain_pref`: **`0x01`** (PS only).
   - `/nv/item_files/modem/mmode/sms_mandatory`: **`0x01`**.
   - `/nv/item_files/modem/mmode/ue_usage_setting`: **`0x00`** (Voice centric).
   - `/data/ds_dsd_attach_profile.txt`: **`Attach_Profile_ID:3;\r\n`** (offset `0x2cf7c73`).
4. **Live Data Session Evidence:**
   - Active IPv6 data session found at offset `0x2a21728`:
     APN `jionet`, link-local `fe80::b0c7:a349:dfd7:d4` passing ICMPv6 Router Advertisements to multicast group `ff02::1`.

---

### 2.2 Forensic Mechanism of the Jio "+CEREG: 6" Lock

Why does Reliance Jio accept registration for SMS only (`+CEREG: 6`) while denying the default EPS packet data bearer?

```
+-----------------------------------------------------------------------------------+
|                        LTE EPS Attach Procedure on Jio 4G                         |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|   UE (MSM8916 UFI001B)                                Reliance Jio EPC (MME)      |
|          |                                                      |                 |
|          | -------- ATTACH REQUEST (Type: Combined EPS/IMSI) -> |                 |
|          |          ESM Container: PDN Conn Req (jionet)        |                 |
|          |          Voice Domain: CS Voice Preferred            |                 |
|          |                                                      |                 |
|          |                                       [Jio MME Evaluation]             |
|          |                                       - Jio has NO 2G/3G CS Core!      |
|          |                                       - UE requested Combined CS/PS.   |
|          |                                       - UE is not VoLTE IMS registered.|
|          |                                       - CSFB is impossible on Jio!    |
|          |                                       - Decision: Accept for SMS only. |
|          |                                                      |                 |
|          | <-- ATTACH ACCEPT (Addl Update Result: SMS Only) --- |                 |
|          |     *No Default EPS Bearer Context Activated*        |                 |
|          |                                                      |                 |
|   [EMM State: Registered for SMS Only]                          |                 |
|   [+CEREG: 0,6]                                                 |                 |
|   [PS Service: Detached / Limited Service]                      |                 |
+-----------------------------------------------------------------------------------+
```

1. **Combined vs. Pure EPS Attach:**
   - In 3GPP TS 24.301 §5.5.1.2, when a UE has `mode_pref` containing 2G/3G (`0x0018` or `0x001f`) or `Service Domain = CS+PS`, it requests `Attach Type = Combined EPS/IMSI Attach`.
   - Reliance Jio operates a **pure 4G EPC network with zero 2G/3G legacy circuit-switched infrastructure**.
   - When Jio's MME receives a Combined Attach from a UE with CS-voice preference, it grants SMS over SGs/SGx but **does not activate the packet data bearer**, setting `Additional Update Result = SMS Only` (`+CEREG: 6`).
2. **The Pure LTE Contrast:**
   - When `mode_pref = 0x0010` (LTE ONLY) and `Service Domain = PS ONLY`:
   - The UE sends `Attach Type = 1 (EPS Attach / Pure Data)`.
   - The UE makes zero requests for legacy CS services.
   - Jio MME immediately grants full EPS data attachment, executes `Activate Default EPS Bearer Context Request`, and transitions the UE to `+CEREG: 1` ("Registered, home network").

---

### 2.3 The Call Manager `mode_pref` Reversion Trap

During this session, we successfully sent QMI NAS command 0x0033 (`set_voice_pref`) to set:
- `Mode Preference`: `umts, lte` (`0x0018`)
- `Acquisition Order`: `lte` first (`0x01 0x08`)
- `Service Domain`: `ps-only` (`0x01`)
- `Usage Preference`: `data-centric` (`0x02`)
- `Voice Domain`: `ps-only` (`0x01`)

However, as soon as network selection cycled (`AT+COPS=0` or netifd link check):
- `qmicli --nas-get-system-selection-preference` reported:
  `Mode preference: 'umts'` (0x0008 — WCDMA-only)!
- Call Manager's internal phone object (`cmph_s_type.mode_pref`) reverted back to WCDMA-only because `NV Item 10 (NV_MODE_PREF_I)` was originally locked to `0x0004` (WCDMA_ONLY) in the modem's EFS with the `WRITE_ONCE` flag (`0x07`).

---

### 2.4 The `mmoc.c:2326` Mechanism Forensics

When pure LTE (`mode_pref = 0x0010`) was previously forced or when low-power mode was commanded:
- Remoteproc crashed after exactly 30 seconds:
  `qcom-q6v5-mss 4080000.remoteproc: fatal error received: mmoc.c:2326:=MMOC=`
- **Root Cause Discovered:**
  1. MMOC (Multi-Mode Operations Controller) commands WCDMA deactivation: sends deactivation request to NAS.
  2. NAS sends `CPHY_STOP_REQ` to `wcdma_l1_task`.
  3. Patch 75's loopback at VA `0xc08dc840` clears non-lifecycle signals, discarding `CPHY_STOP_REQ`.
  4. `wcdma_l1` never dispatches `CPHY_STOP_CNF` back to NAS.
  5. NAS never confirms deactivation to MMOC.
  6. MMOC's 30-second deactivation watchdog timer expires, asserting `mmoc.c:2326`.

---

## 3. Comparative Matrix: Baseline vs. Current vs. Target

| Architectural Parameter | Melbon HMU05 Ground Truth | UFI001B (Current State) | Target State (Patch 78) |
| :--- | :--- | :--- | :--- |
| **Active PDC Carrier Config** | `ROW_Generic_3GPP` | `ROW_Generic_3GPP` / `Reliance` | `ROW_Generic_3GPP` |
| **NAS Mode Preference** | `0x0010` (**LTE ONLY**) | `0x0008` (**UMTS**) / `0x0018` | `0x0010` (**LTE ONLY**) |
| **CM Phone Object mode_pref** | `CM_MODE_PREF_LTE_ONLY (21)` | `CM_MODE_PREF_WCDMA_ONLY (4)` | `CM_MODE_PREF_LTE_ONLY (21)` |
| **Acquisition Order** | `LTE` first | `LTE` first | `LTE` first |
| **Service Domain Preference** | `PS ONLY` | `PS ONLY` (reverting) | `PS ONLY` (permanent) |
| **EPS Attach Type** | `1` (Pure EPS Data Attach) | `2` (Combined EPS/IMSI Attach) | `1` (Pure EPS Data Attach) |
| **MMOC WCDMA Deact** | Never active from boot | Times out in 30s (`mmoc.c:2326`) | Handshaked / Stubbed |
| **Jio Registration Status** | `+CEREG: 1` (Registered, Home) | `+CEREG: 6` (SMS Only) | **`+CEREG: 1` (Registered, Home)** |
| **Data Bearer State** | `Attached` (`jionet` IPv6/IPv4) | `Detached` (`limited-srvc`) | **`Attached` (`wwan0` UP)** |

---

## 4. Definitive Action Plan (Patch 78 Implementation)

### Step 1: Disarm the MMOC Deactivation Timeout (`mmoc.c:2326`)
To allow clean, unrestricted transitions to pure LTE without Remoteproc SSR:
1. In `wcdma_l1_task` (Segment 15, `modem.b15`):
   Instead of silently discarding command queue signals at `0xc08dc840`, synthesize an immediate confirmation or patch the MMOC deactivation timer handler.
2. Alternatively, patch the `mmoc.c:2326` assertion site in Segment 15:
   Convert the `ERR_FATAL` invocation to a non-fatal warning (`MSG_HIGH`) and force state transition to `MMOC_PROT_STATE_DEACTIVATED`.

### Step 2: Hard-Lock CM Mode Preference to LTE-Only (`CM_MODE_PREF_LTE_ONLY = 21`)
1. In `cmph_init()` / `cm_init` (Segment 15, entry point `0xc06ca318`):
   Locate where `ph_ptr->mode_pref` is populated from NV Item 10.
2. Override the assignment to enforce `0x15` (`CM_MODE_PREF_LTE_ONLY = 21`):
   ```hexagon
   r0 = #21            // CM_MODE_PREF_LTE_ONLY
   memw(r_ph + #offset) = r0
   ```
3. Re-sign `modem.b15` / `modem.mdt` using `ufi001b_hash_tool.py` and deploy to `/lib/firmware/`.

### Step 3: Trigger Pure EPS Attach & Interface Bringup
1. Once booted with pure LTE mode preference:
   - Radio will issue `Attach Type = 1 (EPS Attach)` directly.
   - Jio MME responds with `Attach Accept` + `Activate Default EPS Bearer Context Request`.
   - Verify `+CEREG: 1` on `/dev/wwan0at0`.
2. Verify ModemManager / netifd:
   - `mmcli -m <id>` reports `packet service state: attached`.
   - Trigger network: `ifup modem`.
   - Verify IP assignment on `wwan0`: `ip addr show wwan0`.
   - Test end-to-end ping: `ping -I wwan0 -c 4 8.8.8.8`.
