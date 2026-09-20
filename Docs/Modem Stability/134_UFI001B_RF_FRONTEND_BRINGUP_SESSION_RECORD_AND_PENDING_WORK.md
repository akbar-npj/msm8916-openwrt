# Engineering Report 134: UFI001B RF Front-End Bring-Up — Session Record, LTE Reception Verification, and Pending Work

**Document ID:** `134_UFI001B_RF_FRONTEND_BRINGUP_SESSION_RECORD_AND_PENDING_WORK.md`
**Date:** September 19, 2026
**Status:** LTE reception **NOT ACHIEVED**. Mode-preference root cause confirmed and a working QMI tool built. A reproducible QMI-stack hang on LTE bring-up is the current blocker. This document is the **authoritative session record** and supersedes Doc 133 §8 for "where we are / what is pending".
**Predecessor:** `133_UFI001B_MPSS2_ATTACH_DIAGNOSTIC_AND_PURE_EPS_RESOLUTION.md`
**Target Hardware:** Melbon HMU05 4G USB Dongle (Qualcomm MSM8916 + WTR1605 Transceiver + QFE2320 Front-End)
**Host Environment:** OpenWrt 25.12.5 (Linux 6.12.94 aarch64), router `192.168.8.1`
**Active Baseband:** UFI001B MPSS 2.0 Port (`MPSS.DPM.2.0.2.c1-00054`, `M8936FAAAANUZM-1`, Hexagon V5)
**Comparative Baseline:** Melbon HMU05 Stock Ground Truth (`HIMI_U01_MODEM_V1.0`, `MPSS.DPM.1.0.c7`)

---

## 1. Purpose and Scope

This document records one work session dedicated to the **RF front-end bring-up** of the UFI001B → HMU05 baseband transplant, and answers three questions for the next engineer:

1. **What was done** — every action taken, with the exact commands and observed output.
2. **Where we are** — the verified current state of the port.
3. **What is pending** — the prioritised next actions.

It deliberately separates **verified facts** from **hypotheses**. Nothing is asserted here that was not observed on the live device during this session, except where explicitly labelled as a hypothesis.

---

## 2. SOP Compliance Statement

Per the mandatory protocol in Doc 133 §1, this session followed the dual-firmware comparative workflow:

- **Backup / version control** — the live image was identified by MD5 and matched to a local build before any analysis (§4.1).
- **Ground-truth verification** — all VA-level claims were re-verified against the running image by direct hexdump at computed offsets, and cross-checked against stock HMU05 / UFI001B decompilation artifacts and Patch 94's own header (§4.4, §5.3).
- **No blind patching** — **no firmware was modified in this session.** All work was measurement, tooling, and documentation.
- **Cryptographic integrity** — no re-signing was required, since no segment was altered.

> [!IMPORTANT]
> **Two errors made during this session were caught and corrected, and are recorded here rather than hidden:**
> 1. An offset arithmetic error (`0xaeb83c` instead of `0xaea83c`) briefly produced a false "transceiver validator is unpatched" conclusion (§5.3).
> 2. An initial claim that Doc 133 §2.4's telemetry was "not reproducible" was itself wrong — it *is* reproducible, but only with LTE in the mode preference (§5.4).
>
> This is exactly the class of mistake the comparative protocol exists to catch.

---

## 3. Executive Summary

The port's inability to register was previously described as "RF-deaf". This session established that the **immediate** cause is a **configuration** problem, not a hardware fault:

- The modem boots with `Mode preference: 'umts'` (WCDMA-only). Reliance Jio is pure-LTE with no UMTS carrier, so the modem can never acquire service.
- Sending `mode_pref = 0x0018` (UMTS | LTE) via QMI NAS `0x0033` **succeeds** and changes the mode to `'umts, lte'`. This confirms Doc 133 §4 Factor 2.
- **However**, once LTE is in the mode preference, the modem **never activates a radio interface** (`Radio interfaces: [0]: 'none'`), a network scan never completes, and the QMI stack reproducibly hangs (`endpoint hangup`).

**Therefore LTE reception could NOT be verified.** The `WTR4905 → WTR1605` adaptation is *structurally* patched (every transceiver patch from `patch41`–`patch53` is present and byte-verified), but it is **functionally incomplete**: the LTE radio never comes up.

Additionally, this session **disproved** the evidence Doc 133 §2.4 used to claim the receiver works — see §5.4. The `IO: -106 dBm` reading is a **static default**, not a live measurement.

---

## 4. What Was Done

### 4.1 Firmware identity established (no changes made)

| Item | Value |
| :--- | :--- |
| Active image | `/lib/firmware/modem.b15` |
| MD5 | `c69ad8003476146f1263021638dff356` |
| Local match | `GitIgnore/compare/modem_ufi001b_patched/image/modem.b15` (identical MD5) |
| Active profile | UFI001B (`switch_modem_fw.sh status` → "Active firmware: UFI001B (b26 present)") |
| Deployed mcfg | `/lib/firmware/mcfg_sw.mbn`, MD5 `d23a30603aea2b1129ddb15440efb944`; NV10 @ `0x21e0` = `15 00` (= 21 = `CM_MODE_PREF_LTE_ONLY`) |
| Local baselines | `modem_ufi001b_pre_master_backup/modem.b15` MD5 `cd28df7d9abe95978ab55cdbccd47432`; `modem_ufi001b_patch77_backup/modem.b15` MD5 `bf9fcedacc757bdf0e9948ad53a040e5` |

### 4.2 Boot-state measurement

Live QMI query (`qmicli -d /dev/wwan0qmi0 --nas-get-system-selection-preference`):

```text
Mode preference: 'umts'
Service domain preference: 'ps-only'
GSM/WCDMA acquisition order preference: 'wcdma'
Acquisition order preference: 'lte, cdma-1x, gsm, umts, cdma-1xevdo, td-scdma'
```

```text
Registration state: 'not-registered-searching'
Selected network: 'unknown'
Radio interfaces: '1'
  [0]: 'none'
```

**Interpretation:** the port is not RF-deaf — it is scanning **WCDMA only**. This fully explains `--nas-get-signal-info` → `InformationUnavailable (74)` and `AT+CSQ` → `99,99` without invoking any RF hardware fault.

### 4.3 Tooling defect found and fixed

`GitIgnore/compare/set_pure_lte.c` is commented `mode_pref: UMTS+LTE` but actually encodes:

```c
0x11, 0x02, 0x00, 0x10, 0x00, // mode_pref: UMTS+LTE   <-- value is 0x0010 (LTE only)
```

`0x0010` is precisely the value Doc 133 §4 Factor 2 documents as **rejected** with `DeviceUnsupported (0x19)`. This fully explains the previously observed `COMBINED SET SSP: FAILURE error 0x19` — the tool's QMI transport was always correct; only the payload constant was wrong.

**New tool created:** `GitIgnore/compare/set_umts_lte.c` (raw-syscall nostdlib, statically linked aarch64). Payload:

- TLV `0x17` Change Duration = `0x01` (permanent)
- TLV `0x11` Mode Preference = `0x0018` (UMTS | LTE)

### 4.4 Transceiver patch audit

All RF/transceiver patches from `patch41`–`patch53` were verified **present** in the running image by direct hexdump at correctly-computed offsets — see §5.3.

### 4.5 LTE reception verification attempt

Mode preference was set to `'umts, lte'` and the receiver was exercised. **Result: not achieved** — see §6.

### 4.6 Device restored

Mode preference was reverted to `'umts'` and `remoteproc0` confirmed `running`. No firmware was left modified.

---

## 5. Verified Findings

### 5.1 `mode_pref = 0x0018` succeeds (Doc 133 Factor 2 confirmed)

```text
Sending NAS Set SSP (mode_pref=0x0018): 01 15 00 00 03 02 00 01 00 33 00 09 00 17 01 00 01 11 02 00 18 00
Response: 01 13 00 80 03 02 02 01 00 33 00 07 00 02 04 00 00 00 00 00
===> SET SSP mode_pref=0x0018: SUCCESS

Mode preference: 'umts, lte'
```

**Conclusion:** Doc 133 §4 Factor 2 is empirically correct. `0x0018` is accepted where `0x0010` is rejected.

### 5.2 `qmicli` 1.36.0 cannot set a multi-RAT mode preference

Documented syntax:

```text
--nas-set-system-selection-preference=[cdma-1x|cdma-1xevdo|gsm|umts|lte|td-scdma][,[automatic|manual=MCCMNC]]
```

The parser takes token[0] as the **mode preference** and token[1] as the **network-selection preference**:

- `=umts,lte` → `invalid net selection preference value given: 'lte'`
- `=lte,umts` → `invalid net selection preference value given: 'umts'`

**Conclusion:** `qmicli` can express only a single RAT. Multi-RAT requires the raw QMI tool from §4.3.

### 5.3 RF transceiver patches — ALL PRESENT

| VA | Expected | Observed on live `/lib/firmware/modem.b15` | Meaning |
| :--- | :--- | :--- | :--- |
| `0xc0dac83c` | `c03f1048` | `c0 3f 10 48` ✓ | transceiver class validator bypass `{ r0 = #1; jumpr r31 }` |
| `0xc0dacb98` | `c03f1048` | `c0 3f 10 48` ✓ | second validator bypass |
| `0xc0dacb54` | `c07b3e0c…` | `c0 7b 3e 0c 00 c0 00 78 a2 6c 29 0c 34 c0 80 48 …` ✓ | PRX/DRX pointer populator |
| `0xc0325c38` | `96cb005a` | `96 cb 00 5a` ✓ | `{ call 0xc0327364 }` |
| `0xc0327364` | `00400375…` | `00 40 03 75 00 d8 5f 53 00 c0 83 52` ✓ | `safe_callr_r3` trampoline |

**Conclusion:** the port is **not** failing at the chip-ID validation gate. The WTR4905 → WTR1605 adaptation is structurally in place.

> **Correction of a session error:** an earlier measurement reported `0xc0dac83c` as unpatched. That was an arithmetic error — the offset had been computed as `0xaeb83c`; the correct offset is `0xc0dac83c − 0xc02c2000 = 0xaea83c`. Re-verified at the correct offset, the bypass **is** present.

### 5.4 The `IO: -106 dBm` reading is a STATIC DEFAULT — Doc 133 §2.4 is disproved

Doc 133 §2.4 claims the RF receiver is proven working because `--nas-get-signal-strength` reported `IO: -106 dBm`, `SINR (8): 9.0 dB`.

This session measured the same command in **two different mode configurations**:

| Mode preference | `IO` | `SINR (8)` | `ECIO` | `RSSI` |
| :--- | :--- | :--- | :--- | :--- |
| `'umts'` (WCDMA only) | `-106 dBm` | `9.0 dB` | `-2.5 dBm` | `-128 dBm` |
| `'umts, lte'` | `-106 dBm` | `9.0 dB` | `-2.5 dBm` | `-128 dBm` |

**The values are bit-identical across both configurations.** A live receiver measurement must depend on the active RAT, band, and bandwidth — it cannot be invariant. Additionally, in the LTE configuration the value was sampled **six times over 18 s and never changed**.

**Conclusion:** `IO: -106 dBm` / `SINR 9.0 dB` is a **static/default value**, not evidence of received LTE energy. Doc 133 §2.4's inference ("Physical RF receiver and front-end switches are receiving real LTE downlink carrier energy") is **not supported** and must not be used as a basis for further work. Note also that `RSSI` is the invalid sentinel `-128 dBm`.

### 5.5 QMI-over-`@qmi-proxy` framing decoded (tooling reference)

- **QMUX length field = (total frame bytes) − 1** for this proxy transport (not the usual `−3`).
- **Allocate CID (`0x0022`) response TLVs:**
  - TLV `0x02` = Result (4 bytes)
  - TLV `0x01` = **Allocated CID** (2 bytes: `service`, `client_id`)

  Verified: `... 02 04 00 00 00 00 00 | 01 02 00 03 02` → result SUCCESS, `service = 0x03` (NAS), `cid = 0x02`.

### 5.6 `clrbit` semantics — Doc 133 §2.1 is incorrect

Doc 133 §2.1 annotates the Patch-75 loopback as:

```assembly
21c9d08c    r1 = clrbit(r16, #9)   // Preserves bit 9 (0x200 = WL1_CMD_Q_SIG)
```

The annotation is **self-contradictory**: `clrbit` *clears* bit 9.

`build_patch94_cphy_stop_restore.py` independently states the same fact in its header:

> "Signal bit 9 (0x200, WL1_CMD_Q_SIG) was wiped out before reaching the command queue dispatcher… Because bit 9 was wiped out, `wcdma_l1_task` never dequeued `CPHY_STOP_REQ`… MMOC waited 30 seconds for deactivation confirmation, timed out, and asserted: `fatal error received: mmoc.c:2326`."

**Verified on the live image** — `modem.b15` at `0xc08dc840` (offset `0x61a840`):

```text
0061a840  20 db 0a 5a 21 c9 d0 8c f4 e5 f7 5b 6c c0 00 58
```

`21c9d08c` = `r1 = clrbit(r16, #9)` is **present**. The running "master" build therefore **re-introduces** the exact defect Patch 94 was written to remove — consistent with the known defect in `build_ufi001b_master_pure_lte.py`, which rebases on the stale `modem_ufi001b_patch77_backup` and discards Patches 94/96/97/98/99.

---

## 6. LTE Reception Verification — RESULT: NOT ACHIEVED

### 6.1 Method

1. Set mode preference to `'umts, lte'` via `set_umts_lte` (confirmed `SUCCESS`).
2. Poll `--nas-get-serving-system` for registration state and active radio interface.
3. Attempt `--nas-network-scan` (bounded to 120 s).
4. Cross-check via the AT path (independent of QMI): `AT+CSQ`, `AT+CEREG?`, `AT+COPS?`.

### 6.2 Results

**Serving system — no RAT ever activates:**

```text
Registration state: 'not-registered-searching'
Radio interfaces: '1'
  [0]: 'none'
```

`Radio interfaces` reports one slot, always `'none'`. The LTE radio interface is **never brought up**.

**Network scan — never completes:**

```text
$ qmicli -d /dev/wwan0qmi0 --nas-network-scan
cancelling the operation...
```

Cancelled after 120 s with **zero networks found**. (In UMTS-only mode the same scan returned `QMI protocol error (4): 'Aborted'` after ~5 min.)

**AT path — no signal, no operator:**

```text
AT+CSQ     -->  '+CSQ: 99,99'      (invalid / no signal)
AT+CEREG?  -->  ''                 (no response)
AT+COPS?   -->  '+COPS: 0'         (no operator)
```

**QMI stack hangs — reproducible:**

```text
error: couldn't create client for the 'nas' service: CID allocation failed in the CTL client: endpoint hangup
```

Occurrences of the hang, all correlated with the LTE radio being exercised:

| # | Trigger | Outcome |
| :--- | :--- | :--- |
| 1 | `mode_pref = 0x0018` | success; mode → `'umts, lte'`; router rebooted ~25–60 s later (no pstore panic record) |
| 2 | `mode_pref = 0x0018` | QMI endpoint hung (`endpoint hangup`) |
| 3 | `--nas-network-scan` with mode `'umts, lte'` | QMI endpoint hung again |
| 4 | `mode_pref = 0x0018`, then tight polling | intermittent hangs; `[0]: 'none'` throughout |
| 5 | `--nas-network-scan` with mode `'umts, lte'` | cancelled after 120 s, no results |

A **modem SSR** (subsystem restart) was also observed during LTE testing (`bam-dmux … SSR powerup: modem pc_state=1` at `t ≈ 3291 s`).

By contrast, with mode preference `'umts'` (WCDMA-only), repeated `qmicli` queries were stable and a 90 s probe ran to completion with uptime increasing monotonically (181 s → 310 s).

### 6.3 Mode-preference persistence is INCONSISTENT

| Event | Mode after event |
| :--- | :--- |
| After first router reboot | `'umts'` (reverted) |
| After a later router reboot | `'umts, lte'` (persisted) |
| After modem restart #1 | `'umts'` (reverted) |
| After modem restart #2 | `'umts, lte'` (persisted) |

**Conclusion:** the QMI "permanent" change duration is **not reliably persistent**. Most likely the NV/EFS write-back is asynchronous and is lost if the modem restarts before it completes. A boot-time fix (PDC load or init service) is still required.

### 6.4 Verdict

> **LTE reception is NOT verified and there is no evidence that the port receives LTE.**
> The LTE radio interface never activates, network scans never complete, `AT+CSQ` reports `99,99`, and `AT+COPS` reports `0`.
> The blocker is **not** the chip-ID validation gate (§5.3 shows every transceiver patch is present) and **not** the QMI transport (§5.5). It is that **bringing up the LTE radio hangs the modem's QMI/LTE stack**.

**Hypothesis (UNVERIFIED):** the remaining WTR4905 → WTR1605 incompatibility lives in the LTE RF bring-up path downstream of the (already-bypassed) chip-ID validator. This must be confirmed by tracing, not by further patching.

---

## 7. Corrections to Doc 133

| Doc 133 location | Original claim | Status |
| :--- | :--- | :--- |
| §2.1 | `r1 = clrbit(r16, #9)   // Preserves bit 9` | **Incorrect** — `clrbit` clears bit 9 (§5.6) |
| §2.4 | `IO: -106 dBm` proves the RF receiver is receiving LTE energy | **Disproved** — it is a static default, invariant across modes (§5.4) |
| §2.4 | "Physical RF receiver and front-end switches are receiving real LTE downlink carrier energy" | **Not supported** (§5.4) |
| §4 Factor 2 | `0x0018` accepted where `0x0010` is rejected | **Confirmed** (§5.1) |
| §4 Factor 1 | NV 10 out-of-range → CM defaults to WCDMA_ONLY | **Consistent with observation** (§4.2) |
| §6 Action Plan | Ordered NV10 → PDC → CM hard-lock → init.d → EPS bearer | **Superseded** — cannot proceed while the LTE radio will not come up (§8) |

---

## 8. Pending Work

Ordered by priority. **Item 1 is a prerequisite for everything else.**

| # | Action | Rationale |
| :--- | :--- | :--- |
| 1 | **Trace the LTE bring-up hang — DIAGNOSTIC, NOT CORRECTIVE** | Capture a modem-side trace (serial console strongly preferred) while the LTE radio is brought up, to identify the failing subsystem. Do **not** add further patches until the failure point is known. **Execution plan: Doc 135** (`135_UFI001B_LTE_BRINGUP_DIAGNOSTIC_TRACE_PLAN.md`). |
| 2 | **Fix `build_ufi001b_master_pure_lte.py`** | Rebase on `modem_ufi001b_pre_master_backup` (MD5 `cd28df7d9abe95978ab55cdbccd47432`), preserve Patches 94/96/97/98/99, and **drop** the Patch-75 `clrbit` loopback (restore stock `20db0a5a…` at `0xc08dc840`). |
| 3 | **Make mode preference survive boot reliably** | Either (a) load `mcfg_reliance_perfect.mbn` via QMI PDC with `LOAD_CONFIG_CHUNK_SIZE = 0x100` (per Doc 128 — qmicli's default `0x400` overflows the SMD FIFO and yields `InvalidQosId`), or (b) add an OpenWrt init service running `set_umts_lte` after ModemManager starts. |
| 4 | **Re-run LTE reception verification** | Only after item 1 yields a diagnosis. Reuse the §9 procedure. |
| 5 | **Correct Doc 133 §2.1 and §2.4 in place** | Both are actively misleading to the next reader. |
| 6 | **Investigate the router reboot** | One full router reboot followed a `mode_pref = 0x0018` change. `pstore`/ramoops held no panic record; a hardware watchdog exists (`/dev/watchdog0`, `[watchdogd]`). Not yet characterised. |

---

## 9. Reproduction / Command Reference

### 9.1 Set mode preference to UMTS+LTE

```sh
# Tool source: GitIgnore/compare/set_umts_lte.c
# Build:
#   aarch64-openwrt-linux-musl-gcc -nostdlib -static -Os -fno-stack-protector \
#       -fno-builtin -o set_umts_lte set_umts_lte.c
# Deploy, then on the router:
/tmp/set_umts_lte
qmicli -d /dev/wwan0qmi0 --nas-get-system-selection-preference | grep -i "mode preference"
# expect: Mode preference: 'umts, lte'
```

### 9.2 Verify LTE reception

```sh
qmicli -d /dev/wwan0qmi0 --nas-get-serving-system      # watch "Radio interfaces [0]"
qmicli -d /dev/wwan0qmi0 --nas-network-scan            # bounded; expect no results
mmcli -m <id> --command="AT+CSQ"                       # expect 99,99
mmcli -m <id> --command="AT+COPS?"                     # expect 0
```

### 9.3 Recovery if the QMI stack hangs

```sh
# 1. Restart the modem to clear the hang
echo stop  > /sys/class/remoteproc/remoteproc0/state
echo start > /sys/class/remoteproc/remoteproc0/state

# 2. Revert mode preference to a single RAT (qmicli CAN set a single RAT).
#    Retry — the endpoint is intermittently responsive while hung.
qmicli -d /dev/wwan0qmi0 --nas-set-system-selection-preference=umts

# 3. Verify
qmicli -d /dev/wwan0qmi0 --nas-get-system-selection-preference | grep -i "mode preference"
# expect: Mode preference: 'umts'
```

### 9.4 Switch baseband profiles

```sh
/usr/bin/switch_modem_fw.sh status
/usr/bin/switch_modem_fw.sh stock | hmu05_patched | ufi001b_patched
```

---

## 10. Artifact Inventory

| Artifact | Path | Status |
| :--- | :--- | :--- |
| This document | `Docs/Modem Stability/134_UFI001B_RF_FRONTEND_BRINGUP_SESSION_RECORD_AND_PENDING_WORK.md` | Primary session record |
| Predecessor | `Docs/Modem Stability/133_UFI001B_MPSS2_ATTACH_DIAGNOSTIC_AND_PURE_EPS_RESOLUTION.md` | See §8 there and §7 here |
| Corrected QMI SSP tool (source) | `GitIgnore/compare/set_umts_lte.c` | New; verified working |
| Corrected QMI SSP tool (aarch64 static) | `GitIgnore/compare/set_umts_lte` | New; deployed to router `/root/` and `/tmp/` |
| Defective-payload tool (reference) | `GitIgnore/compare/set_pure_lte.c` | Encodes `0x0010`, not `0x0018` |
| Patch 94 (clrbit evidence) | `GitIgnore/compare/build_patch94_cphy_stop_restore.py` | Header documents the bit-9 defect |
| Master build (defective baseline) | `GitIgnore/compare/build_ufi001b_master_pure_lte.py` | Rebases on stale `patch77_backup` |
| Router probe helper | `/root/rf_probe.sh` (on device) | Diagnostic only |

---

## 11. Device State at End of Session

| Item | Value |
| :--- | :--- |
| Mode preference | `'umts'` (reverted for stability) |
| `remoteproc0` | `running` |
| Uptime | Stable (monotonically increasing across final checks) |
| Firmware modified | **No** — measurement, tooling, and documentation only |
| Tools left on device | `/root/set_umts_lte`, `/tmp/set_umts_lte` |

---

## 12. Risk Notes for the Next Session

1. **Setting LTE mode can reboot the whole router.** Do not run it unattended; have physical/serial access if possible.
2. **The QMI stack degrades under LTE mode.** Prefer the AT path (`mmcli -m <id> --command=…`) for state queries while LTE is active.
3. **Do not trust `--nas-get-signal-strength` for RF health.** Its values are static defaults (§5.4). Use serving-system RAT activation, network scan results, or `AT+CSQ` instead.
4. **Do not add patches before tracing.** The failure point of the LTE bring-up is currently unknown; patching blind would violate the SOP and is unlikely to converge.
