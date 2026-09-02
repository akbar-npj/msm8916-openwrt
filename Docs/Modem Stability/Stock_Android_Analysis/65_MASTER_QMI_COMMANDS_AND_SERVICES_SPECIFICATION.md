# Qualcomm MSM8916 QMI Services & Commands Master Reverse Engineering Specification
## Complete Protocol IDL, Message Catalog, Client APIs (`libqmiservices`, `libqmi_cci`, `qmuxd`, `qmiproxy`)

**Target Platform:** Qualcomm MSM8916 (Snapdragon 410) Stock Android 4.4.4  
**Extracted Binaries:**
- `libqmiservices.so` (87 KB, ARMv7 32-bit LE)
- `libqmi.so` (190 KB, ARMv7 32-bit LE)
- `libqmi_cci.so` (30 KB, ARMv7 32-bit LE)
- `libqmi_client_qmux.so` (42 KB, ARMv7 32-bit LE)
- `qmiproxy` (166 KB, ARMv7 32-bit LE)
- `qmuxd` (84 KB, ARMv7 32-bit LE)

**Artifact Directory:** `Docs/Modem Stability/Stock_Android_Analysis/`  
**Date:** 2026-09-02  

---

## 1. Executive Summary & QMI Service Architecture

Qualcomm MSM8916 utilizes the Qualcomm MSM Interface (QMI) protocol for all baseband configuration, network connection setup, power management, and SIM card management.

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                         QUALCOMM QMI PROTOCOL LAYER STACK                        │
├──────────────────────────────────────────────────────────────────────────────────┤
│ Application Layer: rild / netmgrd / ModemManager / libqmi                        │
├──────────────────────────────────────────────────────────────────────────────────┤
│ QMI Service Layer: WDS (0x01), DMS (0x02), NAS (0x03), QoS (0x04), WMS (0x05),   │
│                    UIM (0x0B), TIME (0x16), DSD (0x2A), DPM (0x2F)               │
├──────────────────────────────────────────────────────────────────────────────────┤
│ Client API Layer: QCCI (libqmi_cci.so) / Legacy QMI (libqmiservices.so)          │
├──────────────────────────────────────────────────────────────────────────────────┤
│ Framing Layer: QMUX Framing (6-Byte Header) or QRTR AF_QIPCRTR                   │
├──────────────────────────────────────────────────────────────────────────────────┤
│ Physical Transport: SMD (/dev/smdcntl0..7) or BAM-DMUX DMA (wwan0)               │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Complete QMI Service Catalog & Extracted Commands

### 2.1 WDS (0x01) — Wireless Data Service
*Manages cellular packet data sessions, APN profiles, LTE attach parameters, and data stats.*

| Message ID | Command / API Function | Description |
|:---|:---|:---|
| `0x0020` | `qmi_wds_start_nw_if` | Initiates LTE/3G packet data call with specified APN, IP type, and profile ID. |
| `0x0021` | `qmi_wds_stop_nw_if` | Disconnects active cellular packet data bearer. |
| `0x0022` | `qmi_wds_get_pkt_srvc_status` | Queries current packet connection state (Disconnected, Connecting, Connected, Suspended). |
| `0x0024` | `qmi_wds_get_current_channel_rate` | Returns instantaneous UL/DL physical channel data rates in bps. |
| `0x0025` | `qmi_wds_get_pkt_statistics` | Reads total TX/RX bytes, packets, and error drop counters. |
| `0x0026` | `qmi_wds_reset_pkt_statistics` | Resets session packet counters. |
| `0x0027` | `qmi_wds_create_profile` | Creates a new PDP/APN profile in modem NVRAM. |
| `0x0028` | `qmi_wds_modify_profile` | Modifies APN, authentication (PAP/CHAP), PDP type (IPv4/IPv6). |
| `0x0029` | `qmi_wds_delete_profile` | Deletes a stored APN profile. |
| `0x002A` | `qmi_wds_get_profile_list` | Enumerates all APN profiles stored on modem and USIM. |
| `0x002B` | `qmi_wds_get_default_settings` | Reads default carrier APN parameters. |
| `0x0030` | `qmi_wds_get_current_bearer_tech` | Queries active radio bearer technology (LTE, HSPA+, WCDMA, EVDO). |
| `0x0085` | `qmi_wds_set_lte_attach_pdn_list` | Sets the carrier default LTE initial attach APN list. |
| `0x0086` | `qmi_wds_get_lte_attach_pdn_list` | Queries the active LTE initial attach APN list. |
| `0x0088` | `qmi_wds_set_lte_data_retry` | Configures LTE attach retry intervals. |
| `0x009E` | `qmi_wds_bind_mux_data_port` | Binds WDS client to specific BAM-DMUX DMA / RmNet channel. |

---

### 2.2 DMS (0x02) — Device Management Service
*Device hardware control, operating mode, IMEI/MEID serials, and baseband reboot.*

| Message ID | Command / API Function | Description |
|:---|:---|:---|
| `0x0020` | `qmi_dms_reset` | Resets DMS service state variables. |
| `0x0022` | `qmi_dms_get_device_caps` | Queries maximum radio capabilities, supported bands, and revision. |
| `0x0023` | `qmi_dms_get_device_serial_numbers` | Reads IMEI, MEID, and ESN numbers from baseband. |
| `0x002D` | `qmi_dms_get_operating_mode` | Reads power mode: `0`=Online, `1`=Low Power Mode (LPM), `2`=Factory Test, `3`=Offline. |
| `0x002E` | `qmi_dms_set_operating_mode` | Switches modem power mode (e.g. puts modem into Online or Airplane/LPM). |
| `0x002F` | `qmi_dms_get_time` | Queries modem internal real-time timestamp. |
| `0x0036` | `qmi_dms_get_device_rev_id` | Reads baseband firmware version string (e.g. `HIMI_U01_MODEM_V1.0`). |

---

### 2.3 NAS (0x03) — Network Access Service
*Cellular network search, registration status, signal quality, and radio technology preference.*

| Message ID | Command / API Function | Description |
|:---|:---|:---|
| `0x0020` | `qmi_nas_set_event_report_state` | Subscribes to signal strength (RSSI, RSRP, RSRQ, SINR) change indications. |
| `0x0022` | `qmi_nas_get_serving_system` | Queries current registration state, roaming status, MCC/MNC, and cell ID. |
| `0x0024` | `qmi_nas_initiate_ps_attach_detach`| Triggers LTE Packet Switched (PS) domain attach or detach. |
| `0x0033` | `qmi_nas_set_sys_sel_pref` | Sets Radio Access Technology (RAT) preference (e.g. 4G LTE only, Auto). |
| `0x0034` | `qmi_nas_get_sys_sel_pref` | Reads active RAT preference, acquisition order, and band preferences. |
| `0x004F` | `qmi_nas_get_sig_info` | Reads detailed 5-band LTE signal metrics (RSRP, RSRQ, RSSI, SINR). |
| `0x0051` | `qmi_nas_get_sys_info` | Comprehensive serving/neighbor cell information report. |

---

### 2.4 UIM (0x0B) — User Identity Module Service
*SIM card presence, PIN verification, ICCID/IMSI reads, and APDU channel transport.*

| Message ID | Command / API Function | Description |
|:---|:---|:---|
| `0x0020` | `qmi_uim_read_transparent` | Reads transparent binary elementary files (EF) from SIM (e.g. EF_ICCID, EF_IMSI). |
| `0x0021` | `qmi_uim_read_record` | Reads linear fixed / cyclic records from SIM filesystem. |
| `0x0022` | `qmi_uim_write_transparent` | Writes binary data to SIM EF. |
| `0x0023` | `qmi_uim_write_record` | Updates record in SIM EF. |
| `0x0025` | `qmi_uim_set_pin_protection` | Enables/disables SIM PIN1/PIN2 locks. |
| `0x0026` | `qmi_uim_verify_pin` | Submits PIN1/PIN2 code to unlock SIM. |
| `0x0027` | `qmi_uim_unblock_pin` | Submits PUK1/PUK2 code to unblock locked SIM. |
| `0x0028` | `qmi_uim_change_pin` | Modifies existing PIN code. |
| `0x002B` | `qmi_uim_get_card_status` | Queries physical SIM presence, card state (Ready, Pin Required, Error), and slots. |
| `0x0030` | `qmi_uim_power_down` / `power_up` | Cuts or restores electrical power to physical SIM slot (GPIO 119). |
| `0x003B` | `qmi_uim_send_apdu` | Transmits raw ISO-7816-4 APDU command to SIM card and reads response. |

---

### 2.5 TIME (0x16 / 22) — Time Synchronization Service
*Synchronizes Linux Application Processor RTC clock with Hexagon baseband.*

| Message ID | Message Type | Description |
|:---|:---|:---|
| `0x0020` | `QMI_TIME_GENOFF_SET_REQ` | Writes GPS/Unix epoch time difference to baseband (`ATS_USER` / `ATS_TOD`). |
| `0x0021` | `QMI_TIME_GENOFF_GET_REQ` | Reads current baseband time offset. |
| `0x0025` | `QMI_TIME_REG_IND_REQ` | Registers host to receive asynchronous tower NITZ / SIB16 broadcast indications. |
| `0x0029` | `QMI_TIME_TOD_IND` | Baseband broadcasts tower network time to host AP. |

---

## 3. Qualcomm Common Client Interface (`libqmi_cci.so`)

`libqmi_cci.so` provides the unified asynchronous/synchronous QMI transaction API used across all Qualcomm vendor daemons:

### 3.1 Exported API Functions
```c
/* 1. Client Initialization & Binding */
qmi_client_error_type qmi_client_init_instance(
    qmi_idl_service_object_type service_obj,
    qmi_service_instance_instance_id instance_id,
    qmi_client_ind_cb ind_cb,
    void *ind_cb_data,
    qmi_client_os_params *os_params,
    qmi_client_type *user_handle);

/* 2. Synchronous Command Dispatch */
qmi_client_error_type qmi_client_send_msg_sync(
    qmi_client_type user_handle,
    unsigned int msg_id,
    void *req_c_struct,
    int req_c_struct_len,
    void *resp_c_struct,
    int resp_c_struct_len,
    int timeout_msecs);

/* 3. Asynchronous Command Dispatch */
qmi_client_error_type qmi_client_send_msg_async(
    qmi_client_type user_handle,
    unsigned int msg_id,
    void *req_c_struct,
    int req_c_struct_len,
    void *resp_c_struct,
    int resp_c_struct_len,
    qmi_client_recv_msg_async_cb resp_cb,
    void *resp_cb_data,
    qmi_txn_handle *txn_handle);

/* 4. Release Handle */
qmi_client_error_type qmi_client_release(qmi_client_type user_handle);
```

### 3.2 Dual Transport Operations
`libqmi_cci.so` dynamically detects and selects the transport backend:
* **`qcci_ipc_router_ops`**: Uses AF_QIPCRTR sockets over kernel shared memory (low overhead).
* **`qmuxd_ops`**: Uses UNIX domain sockets connecting to `/dev/socket/qmux_radio/` and `/dev/smdcntl0..7`.

---

## 4. OpenWrt Implementation & Command Mapping

On OpenWrt, the standard open-source tools (`libqmi`, `qmicli`, `ModemManager`) utilize the exact same QMI protocol semantics:

```bash
# Query Device Capabilities (DMS 0x0022)
qmicli -d /dev/cdc-wdm0 --dms-get-capabilities

# Query Operating Mode (DMS 0x002D)
qmicli -d /dev/cdc-wdm0 --dms-get-operating-mode

# Set Operating Mode to Online (DMS 0x002E)
qmicli -d /dev/cdc-wdm0 --dms-set-operating-mode=online

# Query Serving System & Network Registration (NAS 0x0022)
qmicli -d /dev/cdc-wdm0 --nas-get-serving-system

# Query Signal Quality (NAS 0x004F)
qmicli -d /dev/cdc-wdm0 --nas-get-signal-info

# Query SIM Card Status (UIM 0x002B)
qmicli -d /dev/cdc-wdm0 --uim-get-card-status

# Start Data Session (WDS 0x0020)
qmicli -d /dev/cdc-wdm0 --wds-start-network="apn=jionet,ip-type=4" --client-no-release-cid
```

---
*Report logged in Docs/Modem Stability/Stock_Android_Analysis/65_MASTER_QMI_COMMANDS_AND_SERVICES_SPECIFICATION.md*
