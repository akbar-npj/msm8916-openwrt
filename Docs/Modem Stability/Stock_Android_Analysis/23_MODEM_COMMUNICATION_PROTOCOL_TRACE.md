# Qualcomm MSM8916 Modem Communication Protocol & System Call Trace Report

**Target Board:** Generic HMU05 (Qualcomm MSM8916)  
**Kernel:** Linux 3.10.28 (Stock Android 4.4.4)  
**Tools Used:** `strace`, `lsof`, `netstat`, `/proc/PID/fd/`  
**Capture Location:** `Docs/Modem Stability/Stock_Android_Analysis/`  

---

## 1. Executive Summary

Using live kernel instrumentation and dynamic system call tracing on the physical hardware, we captured the exact byte-level protocol transactions between the host Application Processor and the Qualcomm Hexagon QDSP6 baseband.

---

## 2. Process Descriptors & IPC Topology

| Process | PID | Target Sockets / Nodes | Purpose |
| :--- | :--- | :--- | :--- |
| **`time_daemon`** | 242 | Sockets `[8413]`, `[8414]`, `[8416]`, `[8430]`, `[8431]`<br>`/dev/diag` (FD 13) | Connects to QMI Service 22 (ATS Time) over IPC Router; listens for tower NITZ indications via `poll()`. |
| **`qmuxd`** | 232 | `/dev/smdcntl0` through `/dev/smdcntl7` (FDs 15–22)<br>`/sys/power/wake_lock` (FD 8) | Multiplexes raw QMI transactions across shared-memory SMD channels to Hexagon DSP. |
| **`netmgrd`** | 293 | Sockets `[6861]`, `[6862]`, `[6863]`, `[9018]`<br>`/dev/diag` (FD 3) | Coordinates with QMI WDS (Service 1) and configures `rmnet0` packet framing. |
| **`rmt_storage`**| 246 | `/dev/uio0` (FD 5 - Shared Memory)<br>`/dev/block/mmcblk0p13` (modemst1)<br>`/dev/block/mmcblk0p14` (modemst2)<br>`/dev/block/mmcblk0p20` (fsg) | Serves EFS2 non-volatile filesystem requests from baseband via shared memory DMA. |

---

## 3. Byte-Level QMI Packet Capture Analysis

Captured from `qmuxd` reading from SMD channel `/dev/smdcntl0`:

```text
[pid 415] read(15, "\x01\x15\x00\x80\x03\x01\x04\xde\x02\x51\x00\x09\x00\x14\x06\x00\xc1\xf7\xa8\xff\xcc\x00", 5086) = 22
```

### Packet Field Decomposition:
* `\x01`: QMUX packet start delimiter.
* `\x15\x00`: Length = 21 bytes total.
* `\x80`: Control flags (Indication/Response from Hexagon baseband).
* `\x03`: **Service ID `0x03` = QMI NAS (Network Access Service)**.
* `\x01`: Client handle ID 1.
* `\x04`: SDU header control flags.
* `\xde\x02`: Transaction ID.
* `\x51\x00`: **Message ID `0x0051` = `QMI_NAS_GET_SIG_INFO_RESP`**.
* `\x09\x00`: Payload TLV length (9 bytes).
* `\x14\x06\x00\xc1\xf7\xa8\xff\xcc\x00`: TLV payload containing RSSI, RSRP, RSRQ signal strengths.

---

## 4. Wake-Lock Synchronization Lifecycle

Every QMI transaction follows an exact atomic wake-lock sequence:
1. `write(8, "qmuxd_port_wl_0", 15)` $\rightarrow$ **Acquires kernel wake-lock** before reading.
2. `read(15, buffer)` $\rightarrow$ Reads QMI packet from `/dev/smdcntl0`.
3. `sendto(socket, buffer)` $\rightarrow$ Forwards payload to RIL / userspace daemon.
4. `write(10, "qmuxd_port_wl_0", 15)` $\rightarrow$ **Releases kernel wake-lock**.

---

## 5. `time_daemon` System Call Architecture

* **No Polling**: `time_daemon` sits in a kernel `poll()` loop waiting on file descriptors 14, 15, 16 (`socket:[8416]`, `socket:[8430]`).
* **Zero Busy-Wait Overhead**: It consumes **0% CPU** and does not issue periodic `genoff_set` packets.
* **Indication-Driven**: When the cell tower broadcasts a System Information Block (SIB16) frame, the modem pushes an indication to `time_daemon` through `tod_update_ind_cb()`.
