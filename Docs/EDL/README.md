# MSM8916 Qualcomm Reboot to EDL, Fastboot & System Modes

## Overview

This directory contains comprehensive documentation on hardware reboot modes (PBL 9008 EDL, SBL1 9006 DLOAD, Android Fastboot, Recovery) and safe userspace teardown for Qualcomm Snapdragon 410 (MSM8916) OpenWrt devices.

## Master Documentation Index

- [**MSM8916 Reboot-to-EDL Complete Implementation Guide**](MSM8916_Reboot_to_EDL_Complete_Implementation_Guide.md): The definitive guide covering reverse engineering of IMEM cookies, kernel restart handlers, two-tier userspace orchestrator, normal reboot `umount-overlay` clean unmount fix, and live hardware test results.

---

## Quick Reference: Hardware Mode Commands

```bash
# Reboot into Qualcomm 9008 Emergency Download Mode (PBL)
reboot-edl

# Reboot into Qualcomm 9006 Mass Storage / Dump Mode (SBL1)
reboot-dload

# Reboot into Fastboot Bootloader Mode (aboot / lk2nd)
reboot-bootloader
# or:
reboot-fastboot

# Reboot into Android Recovery Mode
reboot-recovery

# Normal Safe Reboot (cleanly unmounts EXT4 overlay)
reboot
```
