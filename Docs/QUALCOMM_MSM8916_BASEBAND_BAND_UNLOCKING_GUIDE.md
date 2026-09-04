# Qualcomm MSM8916 Baseband Band Unlocking Guide

## 1. Overview & Architecture

This document provides a comprehensive, step-by-step technical guide on unlocking additional LTE and WCDMA/GSM cellular frequency bands on **Qualcomm Snapdragon 410 (MSM8916)** based devices (such as Generic HMU05, UZ801, UF896, UFI001C dongles) running OpenWrt / Linux.

### Hardware & Firmware Context
- **SoC**: Qualcomm Snapdragon 410 (MSM8916, Quad-Core Cortex-A53)
- **Modem Core**: Qualcomm Hexagon QDSP6 v5 (MSS Subsystem)
- **RF Transceiver**: Qualcomm WTR2605 / WTR1605 multi-mode RFIC
- **Baseband Firmware**: `HIMI_U01_MODEM_V1.0` (or similar AMSS/Modem OS)
- **Storage Subsystem**: eMMC partitions `modemst1` (`/dev/mmcblk0p4`), `modemst2` (`/dev/mmcblk0p5`), and `fsg` (`/dev/mmcblk0p2`) served to the DSP via the `rmtfs` userspace service.

### Default Factory Bands vs. Unlocked Bands
| Technology | Factory Enabled Bands | Unlocked Operational Bands |
| :--- | :--- | :--- |
| **LTE (FDD)** | B1 (2100), B3 (1800), B5 (850), B8 (900) | B1, B3, B5, **B7 (2600)**, B8, **B20 (800)**, **B28 (700)** |
| **LTE (TDD)** | *None* | **B38 (2600)**, **B40 (2300)**, **B41 (2500 - BSNL/Jio)** |
| **WCDMA/UMTS**| B1 (2100), B5 (850), B8 (900) | B1, B5, B8 |

---

## 2. Safety First: Partition & NVRAM Backups

Before issuing any low-level Qualcomm DIAG NV write commands, backup the modem EFS partitions to prevent permanent loss of calibration or IMEI.

Run on the device (or via SSH):
```bash
# 1. Dump active modem NVRAM partitions
dd if=/dev/disk/by-partlabel/modemst1 of=/tmp/modemst1_factory.img bs=64k
dd if=/dev/disk/by-partlabel/modemst2 of=/tmp/modemst2_factory.img bs=64k
dd if=/dev/disk/by-partlabel/fsg of=/tmp/fsg_factory.img bs=64k

# 2. Transfer backups to host machine
scp root@192.168.8.1:/tmp/*_factory.img ~/modem_backups/
```

---

## 3. Exposing the Qualcomm DIAG Interface

In Qualcomm MSM8916 OpenWrt, the Hexagon DSP communicates with the application processor (AP) via shared-memory rpmsg channels. The DIAG service is exposed on the modem endpoint `/dev/rpmsg_ctrl*`.

### Step 3.1: Create the RPMSG DIAG Endpoint
The Linux kernel `rpmsg_char` driver provides `RPMSG_CREATE_EPT_IOCTL` to create dynamic endpoints.

Identify the modem rpmsg control node (typically `/dev/rpmsg_ctrl2` or `/dev/rpmsg_ctrl3`):
```bash
ls -l /dev/rpmsg_ctrl*
```

### Step 3.2: Compile and Run the DIAG Bridge
A lightweight userspace daemon (`diag_bridge`) establishes the RPMSG `DIAG` endpoint and bridges bidirectional traffic between `/dev/rpmsg0` and the USB gadget serial interface `/dev/ttyGS0`:

```c
// diag_bridge.c snippet
int ctrl_fd = open("/dev/rpmsg_ctrl2", O_RDWR);
struct rpmsg_endpoint_info ept_info = { .name = "DIAG", .src = RPMSG_ADDR_ANY, .dst = RPMSG_ADDR_ANY };
ioctl(ctrl_fd, RPMSG_CREATE_EPT_IOCTL, &ept_info);

int rpmsg_fd = open("/dev/rpmsg0", O_RDWR | O_NONBLOCK);
int gs0_fd = open("/dev/ttyGS0", O_RDWR | O_NOCTTY | O_NONBLOCK);

// Bi-directional forwarding loop using select() between gs0_fd and rpmsg_fd
```

Start the bridge on the device:
```bash
/usr/sbin/diag_bridge &
```

When connected to the host via USB, the host will detect a CDC ACM serial port:
```bash
# On host Linux machine
ls -l /dev/ttyACM0
```

---

## 4. Qualcomm DIAG Protocol & NV Item Specifications

Qualcomm modems communicate over DIAG using HDLC-framed packets with CRC-16 CCITT.

### 4.1 HDLC Framing & CRC-16
- **Frame Delimiter**: `0x7E`
- **Escape Character**: `0x7D` (escaped byte = `byte ^ 0x20`)
- **CRC-16 Polynomial**: `0x8408` (CCITT reverse)
- **Initial CRC**: `0xFFFF`, Final XOR: `0xFFFF`

### 4.2 Packet Length Requirement (Critical)
Many custom scripts fail with `TimeoutError` or `DIAG_BAD_LEN_F` (0x15) because they omit the trailing 2-byte status field in the request:
- **`CMD_NV_READ` (0x26)**: Must be exactly **133 bytes**
  - `uint8 cmd` = `0x26` (1 byte)
  - `uint16 nv_item` (2 bytes, little-endian)
  - `uint8 data[128]` (128 bytes, zeroes in request)
  - `uint16 status` = `0x0000` (2 bytes)
  - *Total payload = 133 bytes + 2 bytes CRC + 1 byte delimiter = 136 bytes.*
- **`CMD_NV_WRITE` (0x27)**: Must be exactly **133 bytes**
  - `uint8 cmd` = `0x27` (1 byte)
  - `uint16 nv_item` (2 bytes, little-endian)
  - `uint8 data[128]` (128 bytes, target value padded with zeroes)
  - `uint16 status` = `0x0000` (2 bytes)
  - *Total payload = 133 bytes + 2 bytes CRC + 1 byte delimiter = 136 bytes.*

In response, `status == 0` indicates `NV_DONE_S` (success).

---

## 5. Band Bitmask Calculations

### 5.1 LTE Band Mask (NV 6828 - `NV_LTE_BC_CONFIG_I`)
LTE band bits are indexed as `1 << (Band - 1)` stored in a 64-bit little-endian integer.

| Byte Offset | Bits Covered | Bands Included | Hex Value | Enabled Bands |
| :--- | :--- | :--- | :--- | :--- |
| **Byte 0** | Bits 0–7 | B1–B8 | `0xD5` | B1 (`0x01`) + B3 (`0x04`) + B5 (`0x10`) + B7 (`0x40`) + B8 (`0x80`) |
| **Byte 1** | Bits 8–15 | B9–B16 | `0x00` | None |
| **Byte 2** | Bits 16–23 | B17–B24 | `0x08` | B20 (`1 << 3` = `0x08`) |
| **Byte 3** | Bits 24–31 | B25–B32 | `0x08` | B28 (`1 << 3` = `0x08`) |
| **Byte 4** | Bits 32–39 | B33–B40 | `0xA0` | B38 (`1 << 5` = `0x20`) + B40 (`1 << 7` = `0x80`) |
| **Byte 5** | Bits 40–47 | B41–B48 | `0x01` | B41 (`1 << 0` = `0x01`) |

**Complete 6-byte Hex String**:
```text
d5 00 08 08 a0 01
```

### 5.2 WCDMA & GSM Band Mask (NV 1877 - `NV_RF_BC_CONFIG_I`)
- Bit 7: GSM DCS 1800
- Bit 8: GSM EGSM 900
- Bit 9: GSM PGSM 900
- Bit 19: GSM 850
- Bit 21: GSM PCS 1900
- Bit 22: WCDMA Band I (2100)
- Bit 26: WCDMA Band V (850)
- Bit 49: WCDMA Band VIII (900)

**Complete 8-byte Hex String**:
```text
80 03 68 04 00 00 02 00
```

---

## 6. Execution: Writing the NV Items

Save and run the following Python automation script against the host's `/dev/ttyACM0` port:

```python
#!/usr/bin/env python3
import serial, struct, time

def crc16(data: bytes) -> int:
    crc = 0xFFFF
    for b in data:
        crc = (crc >> 8) ^ CRC_TABLE[(crc ^ b) & 0xFF]
    return crc ^ 0xFFFF

# Precompute CRC table
CRC_TABLE = []
for i in range(256):
    crc = i
    for _ in range(8):
        crc = (crc >> 1) ^ 0x8408 if crc & 1 else crc >> 1
    CRC_TABLE.append(crc)

def hdlc_encode(payload: bytes) -> bytes:
    crc = crc16(payload)
    raw = payload + struct.pack('<H', crc)
    out = bytearray()
    for b in raw:
        if b in (0x7E, 0x7D):
            out.append(0x7D)
            out.append(b ^ 0x20)
        else:
            out.append(b)
    out.append(0x7E)
    return bytes(out)

def write_nv(ser, nv_id: int, hex_data: str):
    data = bytes.fromhex(hex_data).ljust(128, b'\x00')
    # Exact 133-byte packet: cmd (1) + nv_id (2) + data (128) + status (2)
    payload = struct.pack('<BH', 0x27, nv_id) + data + struct.pack('<H', 0)
    frame = hdlc_encode(payload)
    ser.reset_input_buffer()
    ser.write(frame)
    ser.flush()
    time.sleep(0.5)
    resp = ser.read(ser.in_waiting or 200)
    target = bytes([0x27]) + struct.pack('<H', nv_id)
    pos = resp.find(target)
    if pos >= 0:
        status = int.from_bytes(resp[pos+131:pos+133], 'little')
        print(f"NV {nv_id} write result: status={status} (0=OK)")
    else:
        print(f"NV {nv_id} write: acknowledgment received ({len(resp)} bytes)")

ser = serial.Serial('/dev/ttyACM0', baudrate=115200, timeout=3)
ser._update_dtr_state = lambda: None
ser._update_rts_state = lambda: None

# 1. Unlock LTE Bands (B1, B3, B5, B7, B8, B20, B28, B38, B40, B41)
write_nv(ser, 6828, "d5000808a001")

# 2. Unlock GSM & WCDMA Bands
write_nv(ser, 1877, "8003680400000200")

ser.close()
```

### Step 6.1: Commit Changes to Flash
Flush write buffers through the AP to eMMC:
```bash
ssh root@192.168.8.1 "sync"
```

Restart ModemManager to trigger a clean capabilities query:
```bash
ssh root@192.168.8.1 "/etc/init.d/modemmanager restart"
```

---

## 7. Crucial Fix: Preventing Script Reversions

By default, existing carrier auto-config scripts in OpenWrt may attempt to lock bands to factory defaults when non-Jio networks are active or when an LTE search detects a nearby Jio/Loop pilot.

### 7.1 The Root Cause
In `/usr/sbin/modem-led-monitor`:
```bash
# BUGGY ORIGINAL CODE:
case "$op_name" in
    *[Jj]io*|*[Rr]eliance*|*"IN Loop"*) is_jio=1 ;;
esac
if [ "$is_jio" = "1" ]; then
    mmcli -m "$m_idx" --set-current-bands="eutran-1|eutran-3|eutran-5|eutran-8"
fi
```
1. During automatic network search, non-Jio SIMs (e.g. BSNL `40471`) overhear neighbor cell pilots from Jio (`405861` / "IN Loop").
2. The script falsely identified the carrier as Jio.
3. It executed `mmcli --set-current-bands="eutran-1|eutran-3|eutran-5|eutran-8"`.
4. ModemManager translated this call into a QMI DMS command that **clobbered NV 6828 back to factory `0x95`**.

### 7.2 The Permanent Fix
In both `msm89xx/base-files/usr/sbin/modem-led-monitor` and `openwrt/target/linux/msm89xx/base-files/usr/sbin/modem-led-monitor`:

1. Restrict `is_jio` checks strictly to operator MCC/MNC (`4058xx`), explicitly ignoring neighbor tower names when non-Jio carrier codes are present:
   ```bash
   case "$op_code" in
       4058[4-7][0-9]) is_jio=1 ;;
       404*|405*) is_jio=0 ;;
       *)
           case "$op_name" in
               *[Jj]io*|*[Rr]eliance*) is_jio=1 ;;
           esac
           ;;
   esac
   ```

2. If Jio band restriction is required to avoid IRAT 2G/3G crashes on older modem firmware, **dynamically query supported bands** rather than hardcoding factory bands:
   ```bash
   if [ "$is_jio" = "1" ]; then
       local sup_bands="$(mmcli -m "$m_idx" --output-keyvalue 2>/dev/null | awk -F': ' '/modem.generic.supported-bands.value/ {print $2}')"
       local lte_bands=""
       for b in $(echo "$sup_bands" | tr -s ', ' '\n'); do
           case "$b" in
               eutran-*) lte_bands="${lte_bands:+${lte_bands}|}$b" ;;
           esac
       done
       if [ -n "$lte_bands" ]; then
           mmcli -m "$m_idx" --set-current-bands="$lte_bands" >/dev/null 2>&1 || true
       fi
   fi
   ```

---

## 8. Verification & Operational Testing

### Step 8.1: Verify Supported and Current Bands
Query the modem status:
```bash
mmcli -m 0
```

Expected output:
```text
  ----------------------------------
  Bands    |              supported: utran-1, utran-5, utran-8, eutran-1, eutran-3, eutran-5, 
           |                         eutran-7, eutran-8, eutran-38, eutran-40, eutran-41
           |                current: utran-1, utran-5, utran-8, eutran-1, eutran-3, eutran-5, 
           |                         eutran-7, eutran-8, eutran-38, eutran-40, eutran-41
```

### Step 8.2: Verify Active RF Tuning via QMI
Verify that the modem radio is actively tuned to the expanded frequencies:
```bash
qmicli -d /dev/wwan0qmi0 --nas-get-rf-band-info
```

Expected output:
```text
[/dev/wwan0qmi0] Successfully got RF band info
Band Information:
	Radio Interface:   'lte'
	Active Band Class: 'eutran-1'
	Active Channel:    '465'
```

Check the system selection preference:
```bash
qmicli -d /dev/wwan0qmi0 --nas-get-system-selection-preference
```

Expected output:
```text
[/dev/wwan0qmi0] Successfully got system selection preference
	LTE band preference: '1, 3, 5, 7, 8, 38, 40, 41'
```

### Step 8.3: Apply Unlocked Bands to Netifd / Network
Configure `/etc/config/network` to use multi-mode with 4G preference:
```bash
uci set network.modem.allowedmode='3g|4g'
uci set network.modem.preferredmode='4g'
uci commit network
/etc/init.d/network reload
```
