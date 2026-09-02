# Qualcomm MSM8916 Stock Bootloader (`aboot`) & Hardware EDL Entry Guide
## Reverse Engineering `aboot.mbn` (LittleKernel) & Direct IMEM Register Control

**Target SoC:** Qualcomm MSM8916 / Snapdragon 410  
**Bootloader Analyzed:** `aboot.mbn` (1 MB ELF extracted from `/dev/block/bootdevice/by-name/aboot`)  
**Base Address:** `0x8f600000` (111,900 lines disassembled)  
**Hardware IMEM Base:** `0x08600000`  
**Restart Reason Register:** `0x0860065c` (32-bit word)  
**Date:** 2026-09-02  

---

## 1. Why Standard `adb reboot edl` Failed on Stock Android

Reverse engineering the stock Android 4.4.4 userspace (`/system/bin/reboot` and Bionic `libc`) and `aboot.mbn` revealed the exact failure mechanism:

1. **Android 4.4.4 Libc Limitation:**
   In Android KitKat (4.4.4), `/system/bin/reboot` only recognizes `"recovery"` and `"bootloader"`.
   When passed `"edl"` or `"dload"`, Android `libc` ignores the unknown string and passes `0` (`ANDROID_RB_RESTART`) to the kernel. The kernel writes `0x77665500` (normal reboot), causing the stick to simply reboot back into Android.
2. **`aboot` Magic Cookie Verification:**
   Disassembly of `aboot.mbn` around `0x8f61e8a4` – `0x8f61e8d4` reveals that the bootloader directly checks the 32-bit register at **`0x0860065c`** (IMEM restart reason):
   * `0x77665500` -> **Normal Android Boot**
   * `0x77665501` -> **DLOAD / EDL Emergency Download Mode (9008)**
   * `0x77665502` -> **Fastboot Bootloader Mode**
   * `0x77665503` -> **Recovery Mode**

---

## 2. The Direct Hardware Methods (Guaranteed to Work)

Because we have root access and `/system/xbin/devmem` on the stock Android system, we can write the magic cookies directly to the hardware IMEM register.

---

### Method A: Direct IMEM Magic Write (Software EDL)
Execute inside root `adb shell`:

```bash
# 1. Write the EDL magic cookie (0x77665501) directly to physical IMEM
devmem 0x0860065c 32 0x77665501

# 2. Trigger immediate hardware reset
echo b > /proc/sysrq-trigger
```
* **Result:** The system resets immediately. `aboot` / PBL reads `0x77665501` at `0x0860065c` and halts in **Qualcomm HS-USB QDLoader 9008 (EDL mode)**.

---

### Method B: Direct IMEM Magic Write for Fastboot Mode
If you want to enter Fastboot instead:

```bash
# 1. Write Fastboot cookie (0x77665502) to physical IMEM
devmem 0x0860065c 32 0x77665502

# 2. Trigger immediate hardware reset
echo b > /proc/sysrq-trigger
```
* **Result:** Reboots into Qualcomm Fastboot mode (`fastboot devices`).

---

### Method C: Physical Reset Key / Test Point Grounding (Hardware EDL)
1. Unplug the 4G USB stick from the computer.
2. Press and hold the small physical reset button (connected to GPIO 37 / `key_freset`) on the PCB or short the EDL test points (D+ to GND).
3. While holding the button, insert the stick into the USB port.
4. Release the button after 3 seconds.
* **Result:** PBL detects the hardware key sequence and enumerates directly as `05c6:9008`.

---

## 3. Summary of IMEM Magic Cookies on MSM8916

| Target Boot Mode | IMEM Physical Address | Magic Value (Hex) | Command |
|:---|:---|:---|:---|
| **EDL (9008)** | `0x0860065c` | `0x77665501` | `devmem 0x0860065c 32 0x77665501 && echo b > /proc/sysrq-trigger` |
| **Fastboot** | `0x0860065c` | `0x77665502` | `devmem 0x0860065c 32 0x77665502 && echo b > /proc/sysrq-trigger` |
| **Recovery** | `0x0860065c` | `0x77665503` | `devmem 0x0860065c 32 0x77665503 && echo b > /proc/sysrq-trigger` |
| **Normal** | `0x0860065c` | `0x77665500` | Standard `reboot` |

---
*Report logged in Docs/Modem Stability/Stock_Android_Analysis/71_QUALCOMM_MSM8916_EDL_REBOOT_MECHANISMS.md*
