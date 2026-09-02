# Qualcomm MSM8916 Stock Android Binary Reverse Engineering Report
## Comprehensive Ghidra Headless Decompilation & Protocol Reconstruction

**Target Hardware:** Qualcomm Snapdragon 410 / MSM8916 (Generic HMU05 / UFI-001C)  
**Baseband Version:** `HIMI_U01_MODEM_V1.0` (`MPSS.DPM.1.0.C7`)  
**Android Build:** Android 4.4.4 KTU84P (Kernel 3.10.28)  
**Ghidra Version:** 12.1.2_PUBLIC (Headless ARM 32-bit LE Decompiler)  
**Artifact Directory:** `Docs/Modem Stability/Stock_Android_Analysis/`  
**Date:** 2026-09-02  

---

## 1. Executive Summary

Using Ghidra 12.1.2 headless decompilation directly on extracted stock Android binaries (`time_daemon`, `libtime_genoff.so`, `rmt_storage`, `qmuxd`, `netmgrd`, `libqmiservices.so`, `thermal-engine`), we completely reverse-engineered the proprietary protocol layer that prevents stock Android from crashing after 15 minutes ($t = 900\text{s}$).

This report provides byte-level C decompilations, packet structures, socket paths, and state machine diagrams for replicating these behaviors on OpenWrt / Linux mainline.

```
╔══════════════════════════════════════════════════════════════════════════════════════════╗
║                         REVERSE-ENGINEERED SUBSYSTEM TOPOLOGY                           ║
╠════════════════════╦══════════════════════════════════╦══════════════════════════════════╣
║ BINARY / LIBRARY   ║ IPC INTERFACE                    ║ REVERSE-ENGINEERED ROLE          ║
╠════════════════════╬══════════════════════════════════╬══════════════════════════════════╣
║ time_daemon        ║ QMI Service 22 + /dev/rtc0       ║ Anchors Modem ATS TOD to RTC0 at ║
║                    ║ + \0time_genoff (AF_UNIX)        ║ boot via QMI 0x0020; handles NITZ║
╠════════════════════╬══════════════════════════════════╬══════════════════════════════════╣
║ libtime_genoff.so  ║ \0time_genoff (32-byte struct)   ║ Client IPC library for apps to   ║
║                    ║ (AF_UNIX abstract socket)        ║ get/set ATS time from daemon     ║
╠════════════════════╬══════════════════════════════════╬══════════════════════════════════╣
║ rmt_storage        ║ QMI CSI + /dev/uio0 (DMA)        ║ Serves Hexagon DSP EFS2 sync     ║
║                    ║ + /dev/block/bootdevice/by-name  ║ requests to modemst1/st2/fsg/fsc ║
╠════════════════════╬══════════════════════════════════╬══════════════════════════════════╣
║ qmuxd              ║ /dev/smdcntl0-7 + /dev/socket/   ║ Multiplexes QMI across SMD with  ║
║                    ║ qmux_radio/qmux_connect_socket   ║ per-packet kernel wake-locks     ║
╠════════════════════╬══════════════════════════════════╬══════════════════════════════════╣
║ netmgrd            ║ Netlink + librmnetctl + QMI WDS  ║ Manages rmnet0 BAM DMA endpoints ║
║                    ║ + /dev/diag                      ║ & IP data call lifecycle         ║
╠════════════════════╬══════════════════════════════════╬══════════════════════════════════╣
║ libqmiservices.so  ║ Qualcomm IDL Type Tables         ║ Full service descriptors for     ║
║                    ║ (CSI / CCI runtime)              ║ WDS, DMS, NAS, UIM, TIME, DSD    ║
╚════════════════════╩══════════════════════════════════╩══════════════════════════════════╝
```

---

## 2. `time_daemon` & QMI Service 22 Deep Dive

### 2.1 Complete Initialization & Sync Workflow

From decompiled `FUN_000113d0` and `FUN_00010ef4`:

```
+-------------------------------------------------------------------------------+
|                       time_daemon Boot Synchronization Flow                   |
|                                                                               |
|  [Step 1] open("/dev/rtc0", O_RDONLY)                                         |
|  [Step 2] ioctl(fd, RTC_RD_TIME, &tm)                                         |
|  [Step 3] rtc_ms = (mktime(&tm) + tm.tm_gmtoff) * 1000                        |
|  [Step 4] qmi_client_notifier_init(time_service_get_service_object_v01())     |
|  [Step 5] qmi_client_get_service_list() -> wait up to 6s for modem QMI ready  |
|  [Step 6] qmi_client_init(..., &client_handle)                                |
|  [Step 7] Build QMI_TIME_GENOFF_SET_REQ (0x0020):                             |
|           - base = 2 (ATS TOD / User Base)                                    |
|           - ts_val = rtc_ms (64-bit uint)                                     |
|           - unit = 1 (ms)                                                     |
|  [Step 8] qmi_client_send_msg_sync(handle, 0x0020, &req, 0x10, &resp, 8, 5000)|
|  [Step 9] Mark is_initialized = 1; Enter event loop (0% CPU)                  |
+-------------------------------------------------------------------------------+
```

### 2.2 RTC Reader Function (`FUN_00010ef4`)

```c
// Decompiled from /system/bin/time_daemon @ 0x00010ef4
int get_rtc_time_in_ms(uint64_t *rtc_ms_out)
{
    int fd = open("/dev/rtc0", O_RDONLY);
    if (fd < 0) {
        __android_log_print(ANDROID_LOG_ERROR, "TimeDaemon", "Failed to open /dev/rtc0");
        return -EINVAL;
    }

    struct tm rtc_tm;
    int ret = ioctl(fd, RTC_RD_TIME, &rtc_tm);
    if (ret < 0) {
        __android_log_print(ANDROID_LOG_ERROR, "TimeDaemon", "RTC_RD_TIME ioctl failed");
        close(fd);
        return -EINVAL;
    }

    time_t t = mktime(&rtc_tm);
    int64_t total_sec = (int64_t)t + rtc_tm.tm_gmtoff;
    if (total_sec >= 0) {
        *rtc_ms_out = (uint64_t)total_sec * 1000ULL;
        close(fd);
        return 0; // Success
    }

    close(fd);
    return -EINVAL;
}
```

### 2.3 Modem Asynchronous Indication Callback (`FUN_00011670`)

```c
// Decompiled from /system/bin/time_daemon @ 0x00011670
void time_qmi_ind_callback(qmi_client_type user_handle, unsigned int msg_id,
                           void *ind_buf, unsigned int ind_buf_len, void *ind_cb_data)
{
    // Filter for valid time update indications:
    // 0x29 = QMI_TIME_ATS_TOD_IND (Tower Time of Day Indication)
    // 0x2d = QMI_TIME_ATS_USER_IND (User / NITZ Indication)
    // 0x2e = QMI_TIME_ATS_SECURE_IND
    if (msg_id != 0x29 && (msg_id - 0x2d > 1)) {
        return;
    }

    uint32_t decoded_msg[4]; // local_28[0] contains base_id
    int ret = qmi_client_message_decode(user_handle, QMI_IDL_INDICATION, msg_id,
                                        ind_buf, ind_buf_len, decoded_msg, sizeof(decoded_msg));
    if (ret == QMI_NO_ERR && decoded_msg[0] < 15) {
        pthread_mutex_lock(&time_daemon_mutex);
        pending_base_id = decoded_msg[0];
        has_pending_update = 1;
        pthread_cond_signal(&time_daemon_cond); // Wake worker thread (FUN_000120d0)
        pthread_mutex_unlock(&time_daemon_mutex);
    }
}
```

### 2.4 Worker Thread Query Loop (`FUN_000120d0`)

```c
// Decompiled from /system/bin/time_daemon @ 0x000120d0
void *time_daemon_worker_thread(void *arg)
{
    while (1) {
        pthread_mutex_lock(&worker_mutex);
        while (!has_pending_update) {
            pthread_cond_wait(&worker_cond, &worker_mutex);
        }

        uint32_t base_to_query = pending_base_id;
        has_pending_update = 0;
        pthread_mutex_unlock(&worker_mutex);

        // Query updated time from baseband via QMI_TIME_GENOFF_GET_REQ (0x0021)
        uint32_t req = base_to_query;
        uint8_t resp_buf[0x18]; // 24 bytes response struct
        memset(resp_buf, 0, sizeof(resp_buf));

        int ret = qmi_client_send_msg_sync(time_client_handle, 0x0021,
                                           &req, sizeof(req),
                                           resp_buf, sizeof(resp_buf), 1000);
        if (ret == QMI_NO_ERR) {
            uint64_t modem_ts = *(uint64_t *)(resp_buf + 12);
            // Re-sync local clock state table
            update_local_base_table(base_to_query, modem_ts);
        }
    }
}
```

---

## 3. `libtime_genoff.so` IPC Client Protocol

### 3.1 Abstract Unix Socket Architecture

* **Socket Domain:** `AF_UNIX` (1)
* **Socket Type:** `SOCK_STREAM` (1)
* **Socket Address:** `\0time_genoff` (Abstract namespace: `sa_family=AF_UNIX`, `sa_data[0]='\0'`, `sa_data[1..11]="time_genoff"`)
* **Timeouts:** `SO_RCVTIMEO` = 10s, `SO_SNDTIMEO` = 10s

### 3.2 32-Byte IPC Message Structure (`time_genoff_msg`)

```c
struct time_genoff_msg {
    uint32_t base;        // Offset 0x00: Time base ID (0 = RTC, 1 = TOD, 2 = USER, ...)
    uint32_t unit;        // Offset 0x04: 1 = ms (uint64), 2 = secs, 3 = struct tm
    uint32_t operation;   // Offset 0x08: 0 = SET, 1 = GET, 2 = SET_AND_GET, 3 = DISABLE, 4 = ENABLE
    uint64_t ts_val;      // Offset 0x0C: 64-bit timestamp value in milliseconds
    int32_t  result;      // Offset 0x14: -1 = pending/error, 0 = success
    uint32_t reserved[2]; // Offset 0x18: Padding / context
}; // sizeof = 32 bytes (0x20)
```

### 3.3 Operations and Base IDs Table

| Base ID | Constant Name | Description |
|:---|:---|:---|
| **0** | `TIME_BASE_RTC` | Hardware Real Time Clock |
| **1** | `TIME_BASE_TOD` | Time of Day (Cellular Network Time) |
| **2** | `TIME_BASE_USER` | User Adjusted Clock (Used for SCLK synchronization) |
| **3** | `TIME_BASE_SECURE` | DRM / Secure Subsystem Time |
| **4** | `TIME_BASE_GPS` | GPS Timestamp Offset |
| **5** | `TIME_BASE_1X` | CDMA 1x System Time |
| **6** | `TIME_BASE_HDR` | EV-DO HDR System Time |
| **7** | `TIME_BASE_WCDMA` | WCDMA System Time |
| **8** | `TIME_BASE_LTE` | LTE SIB16 System Time |

---

## 4. `rmt_storage` Reverse Engineering & EFS2 Sync

### 4.1 QMI CSI Architecture

`rmt_storage` does NOT operate as a standard file server. It registers as a **QMI CSI (Common Service Interface) Server** for QMI Service ID `0x14` (`QMI_RMTFS_SERVICE`).

### 4.2 UIO Memory-Mapped DMA Discovery

From decompiled `rmt_storage`:
1. Scans `/sys/class/uio/` for device named `rmtfs`.
2. Reads physical DMA address from `/sys/class/uio/uio%d/maps/map0/addr`.
3. Opens `/dev/uio0` and calls `mmap(NULL, size, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0)`.
4. Shares virtual mapping pointer with Hexagon DSP via QMI response message.

### 4.3 Partition Open & I/O Loop

```c
// Partitions registered by rmt_storage:
// 1. /dev/block/bootdevice/by-name/modemst1  (EFS2 Primary)
// 2. /dev/block/bootdevice/by-name/modemst2  (EFS2 Backup)
// 3. /dev/block/bootdevice/by-name/fsg       (Golden EFS)
// 4. /dev/block/bootdevice/by-name/fsc       (EFS Cookie)

void rmt_storage_handle_rw_request(struct rmt_req *req)
{
    // Acquire kernel wakelock to prevent CPU power collapse during NV writeback
    write(wake_lock_fd, "rmt_storage_0", 13);

    int fd = open_partition(req->partition_id);
    if (req->is_write) {
        lseek64(fd, req->sector_offset * 512, SEEK_SET);
        write(fd, req->dma_shared_buf, req->num_sectors * 512);
        fsync(fd);
    } else {
        lseek64(fd, req->sector_offset * 512, SEEK_SET);
        read(fd, req->dma_shared_buf, req->num_sectors * 512);
    }

    close(fd);
    qmi_csi_send_resp(req->req_handle, QMI_RMTFS_RW_RESP, &resp, sizeof(resp));

    // Release wakelock
    write(wake_unlock_fd, "rmt_storage_0", 13);
}
```

> **CRITICAL INSIGHT FOR OPENWRT:**  
> When OpenWrt's `rmtfs` is started with `-r` (read-only), every write request at $t = 900\text{s}$ fails at `write()`. The modem baseband detects EFS2 flush failure and panics with `Excep :0:`. `rmtfs` MUST run with full read-write permissions!

---

## 5. `qmuxd` & QMI Routing Architecture

### 5.1 SMD Channel Mapping

`qmuxd` opens 8 dedicated Qualcomm Shared Memory control channels:
* `/dev/smdcntl0` $\rightarrow$ Modem Control 0 (RIL / Telephony)
* `/dev/smdcntl1` $\rightarrow$ Modem Control 1 (Data / WDS)
* `/dev/smdcntl2` $\rightarrow$ Modem Control 2 (QoS)
* `/dev/smdcntl3` $\rightarrow$ Modem Control 3 (IMS)
* `/dev/smdcntl4` $\rightarrow$ Modem Control 4 (Tethering / QCMAP)
* `/dev/smdcntl5` $\rightarrow$ Modem Control 5 (Audio)
* `/dev/smdcntl6` $\rightarrow$ Modem Control 6 (Location / GPS)
* `/dev/smdcntl7` $\rightarrow$ Modem Control 7 (Diagnostics / Test)

### 5.2 Atomic Wake-Lock Protocol

Every incoming/outgoing QMUX frame triggers:
1. `write(fd, "qmuxd_port_wl_0", 15)` to `/sys/power/wake_lock`.
2. Complete read / write across `/dev/smdcntl%d`.
3. `write(fd, "qmuxd_port_wl_0", 15)` to `/sys/power/wake_unlock`.

This guarantees the Hexagon A2 DMA bus clock is not gated while control packets are in flight.

---

## 6. `netmgrd` & BAM-DMUX Interface Management

### 6.1 `librmnetctl` Netlink Control

`netmgrd` configures the kernel `rmnet` driver via Netlink commands:
* `rmnet_set_logical_ep_config(mux_id, "rmnet0")` $\rightarrow$ Maps QMI WDS Call ID to virtual netdev.
* `rmnet_set_link_ingress_data_format(flags)` $\rightarrow$ Configures Raw-IP framing (no Ethernet MAC header).
* `rmnet_set_link_egress_data_format(flags)` $\rightarrow$ Disables MAP header multiplexing for single APN mode.

### 6.2 BAM-DMUX Channel State

Decompilation of `netmgr_kif_cb` confirms `netmgrd` treats BAM-DMUX channels `ch00`–`ch07` as **persistent static DMA pipes**. It does not perform active open/close cycles on sleep.

---

## 7. OpenWrt Implementation Blueprint

To fully match stock Android behavior without proprietary binary blobs:

```
+-------------------------------------------------------------------------------+
|                       OpenWrt Exact Replication Blueprint                     |
|                                                                               |
|  1. CLOCK DRIFT:                                                              |
|     Apply patch FUN_c03987e0 in modem.b16 -> disables 900s DRX timer          |
|     (OR run a 50-line C daemon sending QMI 0x0020 at boot)                    |
|                                                                               |
|  2. BAM DMA AUTOSUSPEND:                                                      |
|     Apply 808-bam-dmux-stats.patch -> pm_runtime_resume_and_get() on open     |
|                                                                               |
|  3. EFS2 STORAGE:                                                             |
|     Start rmtfs with '-P -s' targeting /dev/disk/by-partlabel/modemst1/2      |
|                                                                               |
|  4. GPIO PIN LATCH:                                                           |
|     DTS gpio-hog: GPIO 119 = HIGH, GPIO 114 = LOW, GPIO 71 = HIGH             |
+-------------------------------------------------------------------------------+
```

---

*Report generated directly from Ghidra headless reverse engineering of stock MSM8916 Android 4.4.4 binaries.*  
*Reference output logs: `41_ghidra_time_daemon_RE.txt`, `42_ghidra_rmt_storage_RE.txt`, `43_ghidra_qmuxd_RE.txt`, `44_ghidra_libqmiservices_RE.txt`, `45_ghidra_libtime_genoff_RE.txt`, `51_ghidra_netmgrd_RE.txt`, `52_ghidra_thermal_engine_RE.txt`.*
