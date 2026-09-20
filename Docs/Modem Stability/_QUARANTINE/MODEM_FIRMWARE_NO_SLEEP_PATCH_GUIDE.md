# Qualcomm MSM8916 Modem Firmware: 15-Minute Crash Resolution & No-Sleep Patching Guide

---

## 1. Executive Summary

On Qualcomm MSM8916 4G LTE modems (such as **Generic HMU05**, Melbon White, HiMI UFI, and other USB dongles running baseband firmware `MPSS.DPM.1.0.C7`), the cellular data connection consistently crashed or stalled after **14.5 to 15.2 minutes ($t \approx 900\text{s} - 914\text{s}$)** of uptime.

By reverse engineering the Qualcomm Hexagon QDSP6 v5 modem firmware in Ghidra, we identified the exact function responsible: **`FUN_c03987e0` (`lte_ml1_sleepmgr_cfg`)**. 

This guide details:
1. **The Root Cause Analysis** inside the Hexagon DSP binary.
2. **The Exact Assembly Opcodes** patched to disable the 900-second DRX sleep timer.
3. **The MBA SHA256 Hash Alignment** required to allow MBA to authenticate and boot the modified firmware.
4. **The Complete Python Script and Shell Commands** to patch stock firmware with a single command.

---

## 2. Technical Root Cause in Hexagon DSP Firmware

### The 900-Second Sleep Maintenance Timer
In `MPSS.DPM.1.0.C7` (`modem.b16`), the LTE Layer 1 Sleep Manager (`lte_ml1_sleepmgr_stm.c`) implements a 12-state machine that coordinates Discontinuous Reception (DRX) power collapse. Every 900 seconds (15 minutes), a background maintenance timer fires to recalibrate the Sleep Clock (SCLK) drift against the cellular tower.

```text
+-------------------------------------------------------------------------------+
|                       Hexagon QDSP6 Baseband Execution                        |
|                                                                               |
|  [Boot: t = 0s]  ---> lte_ml1_sleepmgr_cfg()                                  |
|                             |                                                 |
|                             v                                                 |
|                      Initialize DRX State Machine                             |
|                      Schedule 900s Calibration Timer                          |
|                             |                                                 |
|                             v                                                 |
|  [t = 900s - 914s] -> Timer Expiry: SCLK Recalibration                        |
|                             |                                                 |
|            +----------------+----------------+                                |
|            |                                 |                                |
|     (On Stock Android)                 (On Linux / OpenWrt)                   |
|     RIL TOD/ATS keepalives reset       No proprietary keepalive               |
|     timer state -> SCLK syncs OK       -> State assertion or RF freeze        |
|                                        -> "lte_ml1_sleepmgr_stm.c:4054"       |
+-------------------------------------------------------------------------------+
```

On Linux/OpenWrt without Android proprietary RIL keepalives, this timer expiry caused:
1. **Physical Synthesizer Freeze**: The modem receiver entered DRX sleep and never woke up, dropping all downlink (`RX`) traffic.
2. **Kernel Fatal Crash**: The state machine hit an unhandled state transition and asserted:
   `qcom-q6v5-mss 4080000.remoteproc: fatal error received: lte_ml1_sleepmgr_stm.c:4054:`

---

## 3. The Binary Modifications

### Patch 1: Neutralize the Sleep Manager Configuration (`FUN_c03987e0`)

* **Target Binary**: `modem.b16` (ELF Segment 16)
* **Virtual Address (VA)**: `0xc03987e0`
* **Segment Base Address**: `0xc0287000`
* **File Offset**: `0x001117e0` (`0xc03987e0 - 0xc0287000 = 0x1117e0`)
* **Original Assembly**:
  ```hexagon
  allocframe(#0x18)           // 08 c0 9d a0
  r17:16 = combine(r1, r0)    // 0c c0 9f a0
  ```
* **Patched Assembly**:
  ```hexagon
  r0 = #-1                    // 00 c4 00 78  (Immediate return value -1)
  { jumpr r31 }               // 00 c0 9f 52  (Return to caller)
  ```
* **Hex Replacement**:
  ```text
  Offset 0x001117e0: 00 c4 00 78 00 c0 9f 52
  ```
* **Effect**: When `lte_ml1` initializes sleep configuration, the function immediately returns `-1` (*"Sleep not configured in mode %s"*). The 900-second DRX sleep timer is **never scheduled**, and the RF receiver synthesizers **never power down into sleep state**.

---

### Patch 2: Neutralize Global `ERR_FATAL` Assertion (`0xc0879150`)

* **Target Binary**: `modem.b16` (ELF Segment 16)
* **Virtual Address (VA)**: `0xc0879150`
* **File Offset**: `0x005f2150` (`0xc0879150 - 0xc0287000 = 0x5f2150`)
* **Original Assembly**:
  ```hexagon
  allocframe(#0x18)           // 08 c0 9d a0
  ```
* **Patched Assembly**:
  ```hexagon
  { jumpr r31 }               // 00 c0 9f 52  (Return to caller)
  ```
* **Hex Replacement**:
  ```text
  Offset 0x005f2150: 00 c0 9f 52
  ```
* **Effect**: If any unexpected baseband assertion occurs, the Hexagon DSP returns immediately to normal execution rather than halting the processor or triggering a Subsystem Restart (SSR).

---

### Patch 3: SHA256 Segment Hash Alignment (MBA Authentication)

The Qualcomm Modem Boot Authenticator (`mba.mbn`) verifies the SHA256 digest of each segment during boot. When `modem.b16` is modified:
1. Calculate the SHA256 digest of the new `modem.b16` binary.
2. Update the Segment 16 digest in **`modem.mdt`** at offset **`0x05bc`** (32 bytes).
3. Update the Segment 16 digest in **`modem.b01`** at offset **`0x0228`** (32 bytes).

---

## 4. Automated Patching Script: `patch_modem_nosleep.py`

Below is the complete standalone Python script to patch stock modem firmware files:

```python
#!/usr/bin/env python3
"""
Qualcomm MSM8916 Modem Firmware No-Sleep & Stability Patch
Targets: modem.b16, modem.mdt, modem.b01
"""

import sys
import os
import hashlib

def patch_modem_firmware(firmware_dir):
    b16_path = os.path.join(firmware_dir, "modem.b16")
    mdt_path = os.path.join(firmware_dir, "modem.mdt")
    b01_path = os.path.join(firmware_dir, "modem.b01")

    if not os.path.exists(b16_path):
        print(f"[-] Error: {b16_path} not found!")
        sys.exit(1)

    print(f"[+] Loading {b16_path}...")
    with open(b16_path, "rb") as f:
        b16_data = bytearray(f.read())

    # 1. Patch FUN_c03987e0 (lte_ml1_sleepmgr_cfg) to return -1
    # Hexagon: r0 = #-1; { jumpr r31 } -> 00 c4 00 78 00 c0 9f 52
    offset_sleepmgr = 0x001117e0
    patch_sleepmgr = bytes([0x00, 0xc4, 0x00, 0x78, 0x00, 0xc0, 0x9f, 0x52])
    print(f"[+] Patching lte_ml1_sleepmgr_cfg at offset 0x{offset_sleepmgr:08x}...")
    b16_data[offset_sleepmgr:offset_sleepmgr+len(patch_sleepmgr)] = patch_sleepmgr

    # 2. Patch global ERR_FATAL to { jumpr r31 } -> 00 c0 9f 52
    offset_err_fatal = 0x005f2150
    patch_err_fatal = bytes([0x00, 0xc0, 0x9f, 0x52])
    print(f"[+] Patching ERR_FATAL at offset 0x{offset_err_fatal:08x}...")
    b16_data[offset_err_fatal:offset_err_fatal+len(patch_err_fatal)] = patch_err_fatal

    # Save patched modem.b16
    with open(b16_path, "wb") as f:
        f.write(b16_data)
    print(f"[+] Successfully wrote patched {b16_path}")

    # 3. Calculate new SHA256 of modem.b16
    new_sha256 = hashlib.sha256(b16_data).digest()
    print(f"[+] New modem.b16 SHA256: {new_sha256.hex()}")

    # 4. Update SHA256 in modem.mdt (offset 0x05bc)
    if os.path.exists(mdt_path):
        with open(mdt_path, "rb") as f:
            mdt_data = bytearray(f.read())
        mdt_offset = 0x05bc
        print(f"[+] Updating modem.mdt hash at offset 0x{mdt_offset:04x}...")
        mdt_data[mdt_offset:mdt_offset+32] = new_sha256
        with open(mdt_path, "wb") as f:
            f.write(mdt_data)
        print(f"[+] Successfully updated {mdt_path}")

    # 5. Update SHA256 in modem.b01 (offset 0x0228)
    if os.path.exists(b01_path):
        with open(b01_path, "rb") as f:
            b01_data = bytearray(f.read())
        b01_offset = 0x0228
        print(f"[+] Updating modem.b01 hash at offset 0x{b01_offset:04x}...")
        b01_data[b01_offset:b01_offset+32] = new_sha256
        with open(b01_path, "wb") as f:
            f.write(b01_data)
        print(f"[+] Successfully updated {b01_path}")

    print("\n[✓] Modem firmware patching complete!")

if __name__ == "__main__":
    target_dir = sys.argv[1] if len(sys.argv) > 1 else "."
    patch_modem_firmware(target_dir)
```

---

## 5. How to Apply the Patch on a Live OpenWrt Device

Execute the following one-line command sequence in SSH on the device:

```bash
python3 -c '
import os, hashlib

b16_path = "/lib/firmware/modem.b16"
mdt_path = "/lib/firmware/modem.mdt"
b01_path = "/lib/firmware/modem.b01"

with open(b16_path, "rb") as f:
    b16 = bytearray(f.read())

# Patch lte_ml1_sleepmgr_cfg (r0 = #-1; { jumpr r31 })
b16[0x001117e0:0x001117e0+8] = bytes([0x00, 0xc4, 0x00, 0x78, 0x00, 0xc0, 0x9f, 0x52])

# Patch ERR_FATAL ({ jumpr r31 })
b16[0x005f2150:0x005f2150+4] = bytes([0x00, 0xc0, 0x9f, 0x52])

with open(b16_path, "wb") as f:
    f.write(b16)

digest = hashlib.sha256(b16).digest()

with open(mdt_path, "rb") as f:
    mdt = bytearray(f.read())
mdt[0x05bc:0x05bc+32] = digest
with open(mdt_path, "wb") as f:
    f.write(mdt)

with open(b01_path, "rb") as f:
    b01 = bytearray(f.read())
b01[0x0228:0x0228+32] = digest
with open(b01_path, "wb") as f:
    f.write(b01)

print("Modem firmware successfully patched!")
'

# Reboot device to boot into patched firmware
sync && reboot
```

---

## 6. Verification and Performance

Once patched, verify the live system:

1. **MBA Authentication Check (`dmesg`)**:
   ```text
   [   11.147400] qcom-q6v5-mss 4080000.remoteproc: MBA booted without debug policy, loading mpss
   [   11.862134] remoteproc remoteproc0: remote processor 4080000.remoteproc is now up
   ```
2. **Continuous Stability**:
   * Uptime reaches **1.5+ hours (95+ minutes)** without drop.
   * Total ping delivery rate exceeds **98.3%** under continuous test.
   * Thermals remain at a steady, cool **61°C–62°C**.
