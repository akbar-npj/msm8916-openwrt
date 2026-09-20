# Engineering Report 136: HMU05 DIAG Port and Serial Console — Discovery, Verification, and Usage

**Document ID:** `136_HMU05_DIAG_PORT_AND_SERIAL_CONSOLE_DISCOVERY.md`
**Date:** September 19, 2026
**Status:** **VERIFIED WORKING.** The Qualcomm DIAG port is reachable and the modem responds to DIAG commands. This supersedes the "Instrumentation Inventory" in Doc 135 §5 and answers the open question in Doc 135 §12.
**Predecessors:** `135_UFI001B_LTE_BRINGUP_DIAGNOSTIC_TRACE_PLAN.md`, `134_UFI001B_RF_FRONTEND_BRINGUP_SESSION_RECORD_AND_PENDING_WORK.md`
**Target Hardware:** Melbon HMU05 4G USB Dongle (Qualcomm MSM8916), router `192.168.8.1`

---

## 1. Purpose

Doc 135 §5 recorded "no `/dev/diag`; QXDM-style capture is not possible", and §12 asked whether a serial console exists. Both questions were investigated on the live device. **Both answers turned out better than assumed:**

- The **serial console is configured** (`console=ttyMSM0,115200`).
- The **Qualcomm DIAG port exists and works** — the modem's DIAG service is reachable and answers commands. It simply had no driver bound.

This document records how each was found, how to use them, and what they enable.

---

## 2. Summary of Findings

| Capability | Status | Detail |
| :--- | :--- | :--- |
| Serial console (`ttyMSM0`) | **Configured** | `console=tty0 console=ttyMSM0,115200`; UART @ MMIO `0x78b0000`, irq 21 |
| Physical UART access | **UNKNOWN — needs user input** | Requires test pads/header on the dongle PCB |
| Modem DIAG SMD channels | **Present** | `DIAG`, `DIAG_2`, `DIAG_2_CMD`, `DIAG_CMD`, `DIAG_CNTL` |
| `/dev/diag` char driver | **NOT built** | `CONFIG_DIAG_CHAR` absent from the kernel |
| DIAG reachable another way | **YES — VERIFIED** | Bridged via `rpmsg_chrdev` → `/dev/rpmsg0` |
| Modem responds to DIAG commands | **YES — VERIFIED** | `VERNO`, `ESN`, `EXT_BUILD_ID` all answered |
| Asynchronous modem logs | **Not yet enabled** | Requires a log-mask configuration command (next step) |
| GLINK | Not present | `CONFIG_RPMSG_QCOM_GLINK_*` unset |
| USB diag function | Not present | Gadget exposes only `acm.GS0` + `ncm.usb0` |

---

## 3. Serial Console

### 3.1 What was found

```text
/proc/cmdline:
    earlycon console=tty0 console=ttyMSM0,115200 root=/dev/mmcblk0p14 rootfstype=squashfs rootwait

dmesg:
    [0.000000] earlycon: msm_serial_dm0 at MMIO 0x00000000078b0000 (options '')
    [0.658080] msm_serial 78b0000.serial: msm_serial: detected port #0
    [0.658243] msm_serial 78b0000.serial: uartclk = 7372800
    [0.664525] 78b0000.serial: ttyMSM0 at MMIO 0x78b0000 (irq = 21, base_baud = 460800) is a MSM
    [0.669794] msm_serial: console setup on port #0

/sys/class/tty/console/active:
    tty0 ttyMSM0

/dev/ttyMSM0:  crw-rw----  243, 0  (group dialout)
```

A console login (`askfirst → login.sh`) is running.

### 3.2 Value and limitations

**Important nuance:** the MSM8916 modem is a **separate processor** (Hexagon). Its output does **not** appear on the AP's `ttyMSM0`. So the serial console does **not** give modem-side traces — for that, use the DIAG port (§4).

Its genuine value is **AP-side**:

- Capturing kernel messages across a **whole-router reboot** — one was observed during LTE bring-up (Doc 134 §8), and `dmesg` is lost across a reboot. A serial capture would preserve the final messages before reset.
- Capturing output if the kernel **hangs** (watchdog reset), where `dmesg` never reaches storage.

### 3.3 How to use

```sh
# From a host with a USB-TTL adapter on the dongle UART pads:
screen /dev/ttyUSB0 115200
# or
picocom -b 115200 /dev/ttyUSB0
```

> **Open question for the user:** are the UART test pads exposed on the HMU05 PCB? This cannot be determined remotely. If yes, a serial capture should accompany every risky run in Doc 135.

---

## 4. Qualcomm DIAG Port

### 4.1 What was found

The modem exposes DIAG over SMD. These devices exist on the rpmsg bus:

```text
remoteproc0:smd-edge.DIAG.-1.-1
remoteproc0:smd-edge.DIAG_2.-1.-1
remoteproc0:smd-edge.DIAG_2_CMD.-1.-1
remoteproc0:smd-edge.DIAG_CMD.-1.-1
remoteproc0:smd-edge.DIAG_CNTL.-1.-1
```

The `DIAG` device reports `modalias = rpmsg:DIAG`, `name = DIAG`, and had **no driver bound**.

### 4.2 Why there is no `/dev/diag`

The Qualcomm `diag` char driver (`CONFIG_DIAG_CHAR`) is **not built**:

```text
$ zcat /proc/config.gz | grep -iE "CONFIG_DIAG|DIAG_CHAR|DIAG_OVER"
(no matches — the grep hits are only network socket diagnostics: INET_DIAG, SOCK_DIAG, ...)
```

The driver is out-of-tree (Qualcomm downstream); it does not exist in the mainline 6.12 tree used here, and no OpenWrt package provides it. GLINK is also absent, and the USB gadget exposes only `acm` + `ncm`.

**Therefore the SMD DIAG channels were present but unused.**

### 4.3 How it was bridged (the key discovery)

`CONFIG_RPMSG_CHAR=y` is built, and `rpmsg_chrdev` is registered on the rpmsg bus. Binding it to the DIAG rpmsg device creates a char device backed by that channel:

```sh
D=/sys/bus/rpmsg/devices/remoteproc0:smd-edge.DIAG.-1.-1
echo rpmsg_chrdev > "$D/driver_override"
echo "remoteproc0:smd-edge.DIAG.-1.-1" > /sys/bus/rpmsg/drivers/rpmsg_chrdev/bind
# result:
#   /dev/rpmsg0 created
#   $D/driver -> .../drivers/rpmsg_chrdev
```

`/dev/rpmsg0` is a raw bidirectional pipe to the modem's DIAG service.

### 4.4 Verified working

Commands were sent and the modem answered:

| Request | Response | Interpretation |
| :--- | :--- | :--- |
| `0x00` (`DIAG_VERNO_F`) | 58 bytes, see below | **Build/version string** |
| `0x01` (`DIAG_ESN_F`) | 1 byte `01` | Recognised |
| `0x0c` (`DIAG_EXT_BUILD_ID_F`) | 51 bytes, see below | Build ID structure |
| `0x20` | 1 byte `0x13` | `DIAG_BAD_CMD_F` — correctly rejected |

**`DIAG_VERNO_F` response (58 bytes) decoded:**

```text
00 4e 6f 76 20 32 31 20 32 30 32 31 32 33 3a 34   .Nov 21 202123:4
39 3a 34 36 4e 6f 76 20 30 34 20 32 30 31 36 30   9:46Nov 04 20160
32 3a 30 30 3a 30 30 45 41 41 41 41 4e 55 5a 3a   2:00:00EAAAANUZ:
09 ff 64 00 02 07 05 b4 a5 7e                     ..d......~
```

Leading byte `0x00` is the response code; the ASCII payload carries build date strings (`Nov 21 2021 23:49:46`, `Nov 04 2016 02:00:00`) and a build identifier fragment.

**`DIAG_EXT_BUILD_ID_F` response (51 bytes):**

```text
0c 00 00 00 77 e0 eb 80 03 00 00 00 00 00 00 00
00 00 00 00 00 00 00 00 00 ff 00 00 00 00 00 00
00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
b1 a0 7e
```

### 4.5 Protocol details (important for tooling)

Two behaviours were established empirically and are easy to get wrong:

1. **The SMD transport uses RAW payload framing — no HDLC.** Sending an HDLC-framed request (`7e 00 f0 e1 7e`) produced `0x13` (`BAD_CMD`) because the modem parsed `0x7e` as the command code. Sending the bare command byte `0x00` produced a correct response. Do **not** apply HDLC escaping/CRC on this transport.
2. **Each `read()` returns exactly one DIAG message, and an undersized buffer silently truncates it.** Reading with `dd bs=1` returned only the first byte and discarded the rest of the message. Reading with `dd bs=512` returned the full 58-byte response. **Always read with a large buffer (≥512 bytes).**
3. **`/dev/rpmsg0` is exclusive-open.** A read and a write cannot be issued from two separate processes; use a single read-write descriptor (`exec 3<>/dev/rpmsg0`) or a dedicated program.

---

## 5. Usage Reference

### 5.1 Enable the DIAG bridge

```sh
D=/sys/bus/rpmsg/devices/remoteproc0:smd-edge.DIAG.-1.-1
echo rpmsg_chrdev > "$D/driver_override"
echo "remoteproc0:smd-edge.DIAG.-1.-1" > /sys/bus/rpmsg/drivers/rpmsg_chrdev/bind
ls -la /dev/rpmsg0
```

### 5.2 Send a command and read the reply

```sh
exec 3<>/dev/rpmsg0
printf "\000" >&3                              # DIAG_VERNO_F (raw, no framing)
( dd bs=512 count=1 <&3 > /root/resp.bin 2>/dev/null & P=$!; sleep 5; kill $P 2>/dev/null )
hexdump -C /root/resp.bin
exec 3<&-
```

### 5.3 Capture a window of DIAG traffic

```sh
exec 3<>/dev/rpmsg0
( dd bs=4096 count=200 <&3 > /root/diag_capture.bin 2>/dev/null & P=$!; sleep 30; kill $P 2>/dev/null )
exec 3<&-
```

> No asynchronous traffic appears until modem logging is enabled (see §7).

### 5.4 Tear down (revert to default state)

```sh
echo "remoteproc0:smd-edge.DIAG.-1.-1" > /sys/bus/rpmsg/drivers/rpmsg_chrdev/unbind
```

---

## 6. Why This Matters for the LTE Bring-Up Investigation

Doc 135 needed modem-side visibility to discriminate between:

- **H1–H3** — the LTE bring-up *asserts* (crash → SSR). The remoteproc coredump already covers this.
- **H4** — the modem *hangs* without crashing. **A coredump is useless here** — there is no crash to dump.
- **H5** — the failure is host-side (qmi-proxy / ModemManager).

The DIAG port is the missing instrument for **H4**: it provides a live view of the modem's internal state and (once logging is enabled) its internal log stream during the hang, with no crash required.

**Concretely, this raises the value of Doc 135 Stage 6:** instead of blind firmware instrumentation, the DIAG log stream may localise the hang directly.

---

## 7. Limitations and Next Steps

| # | Item | Notes |
| :--- | :--- | :--- |
| 1 | ~~**Enable modem logging over DIAG**~~ | **RESOLVED — see Doc 137.** No async traffic is received until the *control channel* is configured. `DIAG_LOG_CONFIG_F` (0x73) alone is **not sufficient**; the `DIAG_CNTL` control-channel sequence is what starts the stream. |
| 2 | ~~**Build a proper DIAG client**~~ | **RESOLVED — see Doc 137.** `GitIgnore/compare/diag_logtool.c` (single open, framed requests, `listen`/`capture`). |
| 3 | **Confirm physical UART access** | Needs user input (§3.3). |
| 4 | **Do not expect `/dev/diag`** | The Qualcomm char driver is absent; use the rpmsg bridge instead. |
| 5 | **Verify the bridge survives a modem restart** | The rpmsg device is recreated on modem SSR; the `driver_override`/bind may need re-applying. Not yet tested. |

---

## 8. Artifacts

| Artifact | Path | Status |
| :--- | :--- | :--- |
| This document | `Docs/Modem Stability/136_HMU05_DIAG_PORT_AND_SERIAL_CONSOLE_DISCOVERY.md` | Capability record |
| DIAG frame generator | `GitIgnore/compare/diag_frame.py` | **HDLC framing — NOT applicable to the SMD transport** (see §4.5). Retained for reference only. |
| Captured responses (device) | `/root/resp_msg.bin`, `/root/resp_msg2.bin`, `/root/diag_listen.bin` | Raw captures on the router |

> [!NOTE]
> `diag_frame.py` was written on the assumption of HDLC framing and is **not** the correct tool for this transport. It is retained because the negative result (a `BAD_CMD` from a framed request vs. a correct reply from a raw one) is what proved the framing is raw. A correct client must send bare payloads.

> [!IMPORTANT]
> **This document's capability gap is closed.** Doc 137 records the completion of
> items 1 and 2 above: the verified `DIAG_LOG_CONFIG_F` (0x73) wire format, the
> `DIAG_CNTL` control-channel sequence that actually starts the log stream, and
> the working client `GitIgnore/compare/diag_logtool.c`. The log stream was
> confirmed live (116–202 messages / 20–30 s, 188–336 KB).

---

## 9. Device State After This Investigation

| Item | Value |
| :--- | :--- |
| `/dev/rpmsg0` | Created and bound (`rpmsg_chrdev` → `remoteproc0:smd-edge.DIAG`) |
| Firmware | **Unmodified** (MD5 `c69ad8003476146f1263021638dff356`) |
| Mode preference | `'umts'` |
| `remoteproc0` | `running` |
| Coredump mode | `inline` (set during Doc 135 preparation) |

The `rpmsg_chrdev` binding is non-invasive — nothing else claimed the DIAG channel — and is reverted with §5.4.
