# Stock Android Deep Dynamic Tracing & Investigation Report

**Target Board:** Qualcomm MSM8916 (Generic HMU05 / UFI)  
**Recorded Continuous Uptime:** **5 Hours 28 Minutes 59 Seconds (`19,739.07` seconds)** with **0% packet loss** and **0 modem errors**.  
**Tools Executed:** `strace`, `inotifywait`, `lsof`, `dumpsys`, `tcpdump`, `cat /d/pinctrl/`  

---

## 1. Watchdog & Keep-Alive Tracing

* **Watchdog Node:** `/sys/devices/soc.0/b017000.qcom,wdt`
* **Device Node:** No `/dev/watchdog` character device exists on this platform.
* **Kernel Mechanism:** Managed via high-priority kernel workqueues.
* **`healthd` Strace:** `healthd` listens on Android Binder (`/dev/binder`) and a `timerfd`. It does not feed an external hardware watchdog character device; hardware pet/bark/bite is fed at the Linux kernel scheduler layer.

---

## 2. `rild` & QCRIL Live Syscall Traces

Tracing `rild` (PID 197, 16 threads) showed:
```text
[pid  640] recvfrom(34, "\0\0\0\0\16\0\0\0... \1\0\0\0$\200\0\0\4\24\3\1\0\4\0\30\1\0\1", 43) = 43
[pid  381] write(20, "qcril", 5) = 5  (Acquires /sys/power/wake_lock "qcril")
[pid  555] recvfrom(28, "3\0\0\0\1\200\0\0\4\36\32\215\0\t\0\20\6\0d\0\0d\0\0", 48) = 48
[pid  381] write(20, "radio-interface", 15) = 15  (Acquires /sys/power/wake_lock "radio-interface")
```

### Key Finding:
* `rild` manages power wake locks (`qcril`, `radio-interface`) dynamically on every QMI event, preventing the CPU and BAM DMA paths from entering deep sleep while unhandled radio indications are in flight.

---

## 3. Filesystem Access Patterns (`inotifywait`)

* Monitored `/persist/` and `/data/misc/radio/` with `inotifywait`.
* **Zero unsolicited file accesses** during steady-state data transfer.
* The modem baseband does **not** read or write loose configuration files in `/persist/` or `/system/etc/` periodically. All non-volatile calibration access is channeled directly to **raw eMMC partitions (`modemst1`, `modemst2`, `fsg`) via `rmt_storage` over `/dev/uio0` shared memory DMA**.

---

## 4. Ground-Truth Summary for OpenWrt Porting

```
+---------------------------------------------------------------------------------------------------+
| COMPONENT         | GROUND-TRUTH PARAMETER                   | OPENWRT IMPLEMENTATION             |
+---------------------------------------------------------------------------------------------------+
| MPSS Mem Base     | 0x86800000, Size: 85 MiB (0x5500000)     | Set in DTS / reserved-memory       |
| Primary SIM GPIO  | GPIO 119 (esim1_en) = HIGH (255)         | Assert HIGH in DTS / firstboot     |
| SIM Detect GPIO   | GPIO 114 (sim_hotplug) = LOW (0)         | Assert LOW in DTS / firstboot      |
| 4G RF Power GPIO  | GPIO 71 (4g_1) = HIGH (1)                | Assert HIGH in DTS / firstboot     |
| DMA Transport     | Persistent BAM DMA (No 1s autosuspend)   | Patch 808 PM lock in kernel        |
| Time Sync         | QMI Service 22 (One-time boot sync + IND)| Baseband No-Sleep / Service 22     |
| NV Storage        | Read-Write rmt_storage to modemst1/st2   | rmtfs running -P -s without -r     |
+---------------------------------------------------------------------------------------------------+
```
