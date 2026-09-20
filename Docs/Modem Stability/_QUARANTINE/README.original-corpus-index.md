# MSM8916 Modem Stability, 15-Minute Crash Fix & Power Management

## Overview

This directory contains comprehensive reverse engineering reports, binary patching instructions, QMI protocol specifications, and engineering reports addressing the Qualcomm Hexagon QDSP6 v5 modem 15-minute crash and data stall issues on OpenWrt Linux 6.12.

## Master Documentation Index

- [**Final HMU05 Modem Stability Resolution Report**](FINAL_HMU05_MODEM_STABILITY_RESOLUTION_REPORT.md): Definitive 4-pillar resolution architecture, hardware-locked No-Sleep patch, BAM-DMUX PM alignment, native QMI Time Daemon, carrier auto-provisioning, and 45+ minute live benchmarks.
- [**MSM8916 Modem Stability Complete Engineering Report**](MSM8916_Modem_Stability_Complete_Engineering_Report.md): Detailed report covering Ghidra decompilation of `LTE_ML1_SLEEPMGR_STM`, 900s SCLK drift failure mechanism, multi-tier fix architecture, and live hardware test verification.
- [**Modem Firmware No-Sleep Patching Guide**](MODEM_FIRMWARE_NO_SLEEP_PATCH_GUIDE.md): Step-by-step instructions, Hexagon assembly opcodes, and Python automation script (`patch_modem_nosleep.py`) for patching `modem.b16`.
- [**Qualcomm QMI Time Service Reverse Engineering Report**](QUALCOMM_MSM8916_MODEM_TIME_SERVICE_REPORT.md): Analysis of stock Android `time_daemon`, QMI Service 22 IDL specification, and QRTR packet framing.
- [**Modem 15-Minute Crash & Stall Resolution Summary**](MSM8916_Modem_15Minute_Crash_and_Stall_Resolution.md): Compact summary of BAM-DMUX runtime power management, EFS2 NV calibration, and diagnostic commands.

---

## Quick Reference: Diagnostic Commands

```bash
# Check modem uptime and verify absence of fatal errors
uptime
dmesg | grep -i -E "fatal error|crash detected|lte_ml1|remoteproc"

# Check cellular network bearer state
mmcli -m 0
ifconfig wwan0

# Check persistent crash logs across reboots
ls -la /sys/fs/pstore/
```
