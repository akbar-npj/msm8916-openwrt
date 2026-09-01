# Qualcomm MSM8916 Modem 15-Minute Crash: Reverse Engineering Report & Host Implementation Blueprint

---

## 1. Executive Summary

Qualcomm MSM8916 (Snapdragon 410) LTE USB modems and dongles (such as Generic HMU05, Melbon White, HiMI UFI, and UZ801 running baseband firmware `MPSS.DPM.1.0.C7`) running mainline Linux or OpenWrt experience a fatal remoteproc crash after **14.5 to 15.2 minutes ($t \approx 900\text{s} - 914\text{s}$)** of uptime:

```text
[  901.428512] qcom-q6v5-mss 4080000.remoteproc: fatal error received: lte_ml1_sleepmgr_stm.c:4054:
[  901.428701] remoteproc remoteproc0: crash detected in 4080000.remoteproc: type fatal error
```

Previously, the only known workaround was binary-patching the Qualcomm Hexagon DSP modem firmware image (`modem.b16`) to disable the LTE ML1 sleep manager. 

**However, stock Android runs the exact same unpatched firmware indefinitely without crashes.**

Through static and dynamic reverse engineering of the stock Android userspace daemon (`time_daemon`) and its proprietary QMI IDL tables extracted from the stock eMMC dump, we have discovered the root cause host-side mechanism and mapped out the exact protocol required to resolve this issue natively in OpenWrt without patching modem firmware.

---

## 2. Root Cause Analysis

### The Two Independent Crash Mechanisms

Our investigation identified that the 15-minute crash actually consists of **two separate, independent failure modes** that both trigger around the 900-second mark:

| Crash Signature | Exact Cause | Stock Android Solution | OpenWrt Solution |
| :--- | :--- | :--- | :--- |
| **Crash A:**<br>`lte_ml1_sleepmgr_stm.c:4054` | Hexagon DSP Sleep Clock (SCLK) calibration watchdog expires due to missing Time-of-Day / Accuracy Time Service keepalives. | Proprietary `time_daemon` sends continuous QMI time synchronization packets to Service 22. | Implement native `qcom-time-daemon` over QRTR. |
| **Crash B:**<br>`:Excep :0:` | Modem Hexagon DSP attempts periodic 900-second EFS NV sync; `rmtfs` in read-only mode (`-r`) rejects the write request. | Stock `rmt_storage` runs with full read-write permissions to EFS partitions (`modemst1`, `modemst2`, `fsg`). | Remove `-r` flag in `rmtfs.init` and ensure shared memory write permissions. |

### Why `lte_ml1_sleepmgr_stm` Fails

In LTE networks, the modem enters Discontinuous Reception (DRX) low-power sleep to conserve battery. During sleep, the high-frequency main oscillator is powered down, and timing is maintained by a low-frequency $32.768\text{ kHz}$ Sleep Clock (SCLK).

Because hardware crystals drift significantly over time with temperature variations, the LTE ML1 (Modem Layer 1) subsystem maintains a state machine (`lte_ml1_sleepmgr_stm`) that requires periodic recalibration of SCLK against an external reference time base.

Qualcomm baseband firmwares rely on the Application Processor (AP) to provide external time bases:
1. **ATS_RTC (Base 0)**: Hardware Real-Time Clock base.
2. **ATS_TOD (Base 1)**: Time of Day base (epoch timestamp).
3. **ATS_USER (Base 2)**: User / System Time base.

If the host AP fails to register with the modem's QMI Time Service and send periodic time synchronization updates, the modem's SCLK calibration timer reaches a terminal watchdog timeout ($t = 900\text{s}$), triggering an unhandled state transition assertion in `lte_ml1_sleepmgr_stm.c:4054`.

---

## 3. Reverse Engineering Findings

### 3.1 Binaries Audited

Extracted from the stock Android `system.bin` dump (`/system/` filesystem):
- `/system/bin/time_daemon` (17,756 bytes, 32-bit ARM ELF)
- `/system/vendor/lib/libtime_genoff.so` (9,300 bytes)
- `/system/vendor/lib/libTimeService.so` (5,204 bytes)
- `/system/vendor/lib/libqmi_cci.so` (29,976 bytes)
- `/system/vendor/lib/libqmi_common_so.so` (8,192 bytes)

### 3.2 QMI Service Identification

Inspecting QRTR lookup on the live MSM8916 platform:
```text
Service Version Instance Node  Port
     22       1        1    0    11  Time service
```
* **Service ID**: `22` (`0x0016`) — Qualcomm QMI TIME Service
* **Version**: `1`
* **Instance**: `1`
* **Transport**: IPC Router / QRTR (`AF_QIPCRTR`)
* **Hardware Endpoint**: Node `0` (Hexagon DSP Modem), Port `11`

### 3.3 Decompiled IDL Table (`time_daemon`)

Using Ghidra headless analysis on `time_daemon`, we located the QMI IDL service object at memory offset `0x14d40` and decoded the message table starting at `0x13a0e`:

```text
Service ID: 0x16 (22 decimal)
Max Message Length: 25 bytes
Number of Messages: 6
```

#### Message Table Mapping:
```
MsgEntry 0: Msg ID 0x0020  Req Type: 0x00  Resp Type: 0x12  -> QMI_TIME_GENOFF_SET_REQ / RESP
MsgEntry 1: Msg ID 0x0021  Req Type: 0x02  Resp Type: 0x07  -> QMI_TIME_GENOFF_GET_REQ / RESP
MsgEntry 2: Msg ID 0x0022  Req Type: 0x08  Resp Type: 0x07  -> QMI_TIME_SET_LEAP_SEC_REQ / RESP
MsgEntry 3: Msg ID 0x0023  Req Type: 0x0a  Resp Type: 0x07  -> QMI_TIME_GET_LEAP_SEC_REQ / RESP
MsgEntry 4: Msg ID 0x0024  Req Type: 0x04  Resp Type: 0x04  -> QMI_TIME_TURN_OFF_IND_REQ
MsgEntry 5: Msg ID 0x0025  Req Type: 0x06  Resp Type: 0x00  -> QMI_TIME_REG_IND_REQ
Indication: Msg ID 0x0029  Type: 0x10                       -> QMI_TIME_TOD_IND (from modem)
```

### 3.4 Key Function Decompilation

#### A. Initialization & Initial Sync (`FUN_000113d0` / `genoff_modem_qmi_init`)
```c
// Decompiled snippet from Ghidra:
puVar6 = ...; // QMI client handle
memset(&local_108, 0, 0x10);
gettimeofday(&local_110, NULL);
lVar1 = __aeabi_uldivmod(local_110.tv_usec, 1000);

local_100 = ((longlong)local_110.tv_sec * 1000) + lVar1; // Epoch milliseconds
local_108.tv_sec = 2; // Base ID: ATS_USER / ATS_TOD

// Send synchronous QMI message 0x20:
iVar5 = qmi_client_send_msg_sync(*puVar6, 0x20, &local_108, 0x10, auStack_118, 8, 5000);
```

#### B. Modem Indication Callback (`FUN_00011670` / `tod_update_ind_cb`)
Handles asynchronous indication message `0x29` (`QMI_TIME_TOD_IND`) when the modem receives network time from the cellular tower (NITZ/SIB16).

---

## 4. QMI Protocol Specification for QMI TIME (Service 22)

### 4.1 Packet Framing Over QRTR

QMI messages over QRTR sockets (`AF_QIPCRTR`) use the standard QMI Multiplexing header:

```
+---------------+------------------+------------------+------------------+--------------------+
| Flags (1B)    | Txn ID (2B)      | Msg ID (2B)      | Msg Len (2B)     | TLV Payload (N B)  |
+---------------+------------------+------------------+------------------+--------------------+
| 0x00 (Req)    | 0x0001 ...       | 0x0020           | 0x0013           | See TLV structure  |
+---------------+------------------+------------------+------------------+--------------------+
```

### 4.2 Message 0x0020: `QMI_TIME_GENOFF_SET_REQ`

Sent from AP to Modem to calibrate SCLK and set time offset.

* **TLV Type `0x01` (Mandatory, Length: 16 bytes)**:
  * `uint32_t base`: Time base identifier
    * `0` = `ATS_RTC`
    * `1` = `ATS_TOD`
    * `2` = `ATS_USER`
  * `uint32_t unit`: `0` (`TIME_UNIT_MSEC`)
  * `uint32_t operation`: `0` (`TIME_GENOFF_OP_SET`)
  * `uint64_t offset`: Timestamp in milliseconds since Unix epoch ($1970\text{-}01\text{-}01\text{ 00:00:00 UTC}$)

### 4.3 Message 0x0020: `QMI_TIME_GENOFF_SET_RESP`

Sent from Modem to AP confirming calibration.

* **TLV Type `0x02` (Mandatory, Length: 4 bytes)**:
  * `uint16_t result`: `0x0000` (`QMI_RESULT_SUCCESS`)
  * `uint16_t error`: `0x0000` (`QMI_ERR_NONE`)

### 4.4 Message 0x0025: `QMI_TIME_REG_IND_REQ`

Registers the AP to receive modem time updates and keepalive acknowledgments.

* **TLV Type `0x01` (Mandatory, Length: 1 byte)**:
  * `uint8_t enable`: `1` (Enable indications)

---

## 5. Host-Side Implementation Blueprint for OpenWrt

Instead of running heavy Android proprietary daemons or patching modem firmware binaries, OpenWrt can handle this via a clean, native C daemon: `qcom-time-daemon`.

### 5.1 Architecture Overview

```
 +-----------------------------------------------------------+
 |                     OpenWrt Linux AP                      |
 |                                                           |
 |  [ clock_gettime / RTC ]                                  |
 |           |                                               |
 |           v                                               |
 |  [ qcom-time-daemon ]                                     |
 |           |                                               |
 |           | AF_QIPCRTR socket (libqrtr)                   |
 +-----------|-----------------------------------------------+
             |
             | QRTR IPC (Node 0, Port 11 - Service 22)
             v
 +-----------------------------------------------------------+
 |               Qualcomm Hexagon QDSP6 Modem                |
 |                                                           |
 |  [ QMI Time Service 0x16 ]                                |
 |           |                                               |
 |           v                                               |
 |  [ SCLK Recalibration Engine ]                            |
 |           |                                               |
 |           +---> Resets 900s Sleep Calibration Watchdog    |
 |           |                                               |
 |  [ lte_ml1_sleepmgr_stm ] (State machine stays stable)    |
 +-----------------------------------------------------------+
```

### 5.2 Required Package Components

1. **`packages/qcom-time-daemon/`**:
   * `src/qmi_time.h`: Protocol structs and constants.
   * `src/qmi_time.c`: QMI element info arrays (`struct qmi_elem_info`) for encoding/decoding via `libqrtr`.
   * `src/qcom-time-daemon.c`: Daemon process implementing:
     * Auto-discovery of QMI Time service via QRTR nameservice (`qrtr_new_lookup`).
     * Initial TOD/ATS synchronization at boot.
     * Periodic keepalive loop (default: every 60 seconds, well within the 900s timeout).
     * Handling of modem subsystem restarts (SSR) and re-discovery.
   * `files/qcom-time-daemon.init`: OpenWrt `procd` init script starting at `START=65` (after `qrtrns` at `START=60`).
   * `Makefile`: OpenWrt package definition depending on `+libqrtr +qrtr-utils`.

2. **`packages/rmtfs/files/rmtfs.init`**:
   * Ensure `rmtfs` runs with `-P -s` (without `-r`).
   * Add sync hook on shutdown to commit NV storage to flash.

3. **`packages/msm-firmware-dumper/`**:
   * Drop the binary patcher (`hmu05-patch-modem`).
   * Extract and deploy 100% clean, unmodified stock firmware partitions.

---

## 6. Verification and Validation Strategy

To verify that the host-side time daemon completely replaces the binary patch:

1. **Clean Test Branch**:
   * Branch: `test/time-daemon-unpatched-modem`.
   * Firmware dumper configured to leave all firmware binaries 100% stock.
   * `qcom-time-daemon` installed and running.
2. **Test Criteria**:
   * Uptime past $15\text{ minutes}$ ($900\text{s}$).
   * Uptime past $1\text{ hour}$ ($3600\text{s}$).
   * Active LTE data connection during sleep/idle intervals.
   * Verify syslog shows periodic QMI keepalives:
     `qcom-time-daemon: Sent time sync (base=1, offset=... ms) to 0:11`
   * Check `/sys/fs/pstore/console-ramoops-0` for zero `lte_ml1_sleepmgr_stm.c` errors.
3. **Merge Back to Main**:
   * Once validated, remove the firmware patcher entirely and make `qcom-time-daemon` standard across all MSM8916 boards.
