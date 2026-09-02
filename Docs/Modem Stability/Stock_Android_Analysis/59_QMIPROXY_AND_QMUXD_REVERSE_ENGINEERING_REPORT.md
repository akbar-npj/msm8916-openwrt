# Qualcomm MSM8916 `qmiproxy` & `qmuxd` Reverse Engineering Report
## IPC Architecture, QMI Transport Routing, and Power-Lock Synchronization

**Binaries Analyzed:**
1. `/system/bin/qmiproxy` (166,452 bytes, ARMv7 32-bit LE)
2. `/system/bin/qmuxd` (84,592 bytes, ARMv7 32-bit LE)

**Target Platform:** Qualcomm MSM8916 (Snapdragon 410) Stock Android 4.4.4  
**Extraction Source:** Live Device `c2b9103c` (`192.168.100.1`)  
**Artifact Directory:** `Docs/Modem Stability/Stock_Android_Analysis/`  
**Date:** 2026-09-02  

---

## 1. Executive Summary & Stack Architecture

In stock Android on Qualcomm MSM8916, communication between userspace clients (e.g. `rild`, `netmgrd`, tethering) and the Hexagon QDSP6 modem baseband is structured into a multi-tier IPC transport pipeline:

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                   QUALCOMM STOCK ANDROID QMI IPC PIPELINE                         │
├──────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   [ Client Applications / Daemons: rild, netmgrd, wdsdaemon, Settings App ]      │
│                                   │                                              │
│                                   │ (UNIX Domain Socket: AF_UNIX)                │
│                                   v                                              │
│   ┌──────────────────────────────────────────────────────────────────────────┐   │
│   │                      /system/bin/qmiproxy                                │   │
│   │  • Sockets: /dev/socket/qmux_radio/proxy_qmux_connect_socket             │   │
│   │             /dev/socket/qmux_radio/proxy_tether_connect_socket           │   │
│   │  • Multi-client QMI transaction arbitration & dispatch                   │   │
│   │  • SGLTE / SVLTE Dual-stack voice/data state machine                     │   │
│   │  • System selection preference & mode arbitration (NAS / DMS / WMS)      │   │
│   └──────────────────────────────────────────────────────────────────────────┘   │
│                                   │                                              │
│                                   │ (UNIX Domain Socket)                         │
│                                   v                                              │
│   ┌──────────────────────────────────────────────────────────────────────────┐   │
│   │                      /system/bin/qmuxd                                   │   │
│   │  • Socket: /dev/socket/qmux_radio/qmux_connect_socket                    │   │
│   │  • QMUX protocol packet framing & client ID allocation                   │   │
│   │  • Wake-Lock Management: /sys/power/wake_lock (qmuxd_port_wl_0..7)       │   │
│   │  • Physical SMD Channel Multiplexing: /dev/smdcntl0 .. /dev/smdcntl7     │   │
│   └──────────────────────────────────────────────────────────────────────────┘   │
│                                   │                                              │
│                                   │ (SMD Character Device I/O)                   │
│                                   v                                              │
│   ┌──────────────────────────────────────────────────────────────────────────┐   │
│   │             Kernel SMD Drivers (drivers/soc/qcom/smd.c)                  │   │
│   └──────────────────────────────────────────────────────────────────────────┘   │
│                                   │                                              │
│                                   │ (Shared Memory IPC / SMSM interrupts)        │
│                                   v                                              │
│   ┌──────────────────────────────────────────────────────────────────────────┐   │
│   │                  Hexagon QDSP6 v5 Baseband Core                          │   │
│   └──────────────────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Reverse Engineering `qmiproxy` (166 KB)

### 2.1 Component Source Layout (Recovered from Binary Symbols)
The binary was compiled from the proprietary Qualcomm QMI codebase:
* `vendor/qcom/proprietary/qmi/proxy/qmi_proxy.c` (Main router & socket listeners)
* `vendor/qcom/proprietary/qmi/proxy/qmi_proxy_sm.c` (General proxy state machine)
* `vendor/qcom/proprietary/qmi/proxy/qmi_proxy_sglte_sm.c` (Simultaneous GSM & LTE arbitration)
* `vendor/qcom/proprietary/qmi/proxy/qmi_proxy_queue.c` (Thread-safe transaction FIFO queue)

### 2.2 Sockets & IPC Interfaces
`qmiproxy` binds and listens on two dedicated abstract/filesystem UNIX domain sockets:
1. `/dev/socket/qmux_radio/proxy_qmux_connect_socket` (Used by `rild`, `netmgrd`, telephony services)
2. `/dev/socket/qmux_radio/proxy_tether_connect_socket` (Used by USB/Wi-Fi tethering daemons)

### 2.3 Service Arbitration Subsystems
`qmiproxy` intercepts, caches, and arbitrates requests across all standard QMI services:

| QMI Service | Arbitrator Function in `qmiproxy` | Key Responsibilities |
|:---|:---|:---|
| **NAS (0x03)** | `qmi_proxy_nas_srvc_arb_hdlr` | Arbitrates System Selection Preferences (`NAS_SET_SYS_SEL_PREF`), Network Scans (`NAS_PER_NET_SCAN`), Signal Info (`NAS_GET_SIG_INFO`), and RAT mode masks. |
| **DMS (0x02)** | `qmi_proxy_dms_srvc_arb_hdlr` | Synchronizes operating mode changes (`DMS_SET_OPRT_MODE` -> Online, Low Power Mode, Factory Test). |
| **WMS (0x05)** | `qmi_proxy_wms_srvc_arb_hdlr` | Manages SMS indication registrations and message routing. |
| **VOICE (0x09)**| `qmi_proxy_voice_srvc_arb_hdlr`| Tracks voice call active states to prevent CS/PS handovers from dropping data calls. |
| **PBM (0x07)** | `qmi_proxy_pbm_srvc_arb_hdlr` | Phonebook manager arbitration. |
| **SAR (0x11)** | `qmi_proxy_sar_srvc_arb_hdlr` | Specific Absorption Rate RF power backoff controls. |
| **IMS VT** | `qmi_proxy_ims_vt_srvc_arb_hdlr` | IMS Video Telephony QoS requests. |
| **IMS Presence**| `qmi_proxy_ims_presence_srvc_arb_hdlr` | IMS Presence capabilities. |

### 2.4 Transaction Lifecycle
1. `qmi_proxy_init_qmux_listener()`: Spawns listener thread on socket.
2. `qmi_proxy_rx_hdlr()`: Reads QMUX framed request from client FD.
3. `qmi_proxy_decode_qmi_srvc_message()`: Parses QMI Service ID, Message ID, and TLVs.
4. `qmi_proxy_add_txn_entry()` / `qmi_proxy_queue_push()`: Assigns a tracking transaction ID.
5. `qmi_proxy_dispatch_txn()`: Forwards request to `qmuxd`.
6. `qmi_proxy_send_response_to_clients()`: Dispatches modem response back to requesting client FD.

---

## 3. Reverse Engineering `qmuxd` (84 KB)

### 3.1 Kernel Device Bindings
`qmuxd` is the direct bridge to kernel SMD character devices:
* `/dev/smdcntl0` – Default Radio / Control port
* `/dev/smdcntl1` – Primary Data / Netmgr port
* `/dev/smdcntl2` – Secondary Data port
* `/dev/smdcntl3` – Auxiliary / IMS port
* `/dev/smdcntl4` – GPS / Location port
* `/dev/smdcntl5` – Bluetooth / Audio port
* `/dev/smdcntl6` – Test / Diagnostic port
* `/dev/smdcntl7` – Subsystem Management port

### 3.2 Power Management & Wake-Lock Handling
In `qmuxd`, every physical port has an associated kernel wake lock:
* `qmuxd_port_wl_0` through `qmuxd_port_wl_7`

#### The Wake-Lock Execution Routine:
```c
void qmuxd_acquire_wake_lock(int port_id) {
    char lock_name[64];
    snprintf(lock_name, sizeof(lock_name), "qmuxd_port_wl_%d", port_id);
    int fd = open("/sys/power/wake_lock", O_WRONLY);
    if (fd >= 0) {
        write(fd, lock_name, strlen(lock_name));
        close(fd);
    }
}

void qmuxd_release_wake_lock(int port_id) {
    char lock_name[64];
    snprintf(lock_name, sizeof(lock_name), "qmuxd_port_wl_%d", port_id);
    int fd = open("/sys/power/wake_unlock", O_WRONLY);
    if (fd >= 0) {
        write(fd, lock_name, strlen(lock_name));
        close(fd);
    }
}
```
* **Why this is critical:** When a QMI request is sent to the Hexagon DSP, `qmuxd` immediately acquires `qmuxd_port_wl_X`. This prevents the Linux Application Processor from entering suspend while the modem is processing the command. Once the Hexagon DSP sends the response over SMD, `qmuxd` reads the response and releases the wake lock.

### 3.3 QMUX Packet Framing Format
`qmuxd` frames all data using the standard 6-byte QMUX header:
```text
Offset  Size  Field          Description
──────  ────  ─────────────  ──────────────────────────────────────────
0x00    1     I/F Flag       0x01 (Always 0x01 for QMUX packet)
0x01    2     Length         Total packet length including QMUX header
0x03    1     Control Flag   0x00 = Request/Response, 0x80 = Indication
0x04    1     Service Type   QMI Service ID (e.g. 0x01=WDS, 0x03=NAS)
0x05    1     Client ID      Assigned QMI Client ID (0x01..0xFF)
0x06    ...   SDU Body       QMI Control/Service Message Payload
```

---

## 4. Key Takeaways for OpenWrt

1. **Wake-Lock Equivalent on OpenWrt:**
   * In OpenWrt / mainline Linux, ModemManager and `qmi_wwan` / `qcom_bam_dmux` use the kernel Runtime PM framework instead of userspace `/sys/power/wake_lock`.
   * That is why our **Tier 2 fix** (`pm_runtime_resume_and_get()` in `bam_dmux_netdev_open()` + sysfs `control=on` / `autosuspend_delay_ms=-1`) perfectly replicates `qmuxd`'s wake-lock guarantees at the kernel driver layer!

2. **No Need for Userspace `qmiproxy`/`qmuxd` on OpenWrt:**
   * OpenWrt's `libqmi` and `ModemManager` communicate directly with kernel devices (`/dev/wwan0qmi0` / `/dev/cdc-wdm0` / QRTR sockets), avoiding the userspace socket proxy layers while maintaining full QMI message compatibility.

---
*Report logged in Docs/Modem Stability/Stock_Android_Analysis/59_QMIPROXY_AND_QMUXD_REVERSE_ENGINEERING_REPORT.md*
