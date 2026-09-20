# DOC-137 — HMU05 DIAG Log Stream Enablement and the `DIAG_LOG_CONFIG_F` Protocol

**Status:** ✅ COMPLETE — modem log stream confirmed live
**Supersedes the "next step" in:** `136_HMU05_DIAG_PORT_AND_SERIAL_CONSOLE_DISCOVERY.md` §7 items 1–2
**Related:** Doc 135 (diagnostic trace plan), Doc 133 (SOP/spec), Doc 134 (RF bring-up record)
**Date:** 2026-09-19

---

## 1. Purpose

Doc 136 established that the HMU05 modem's Qualcomm DIAG channel can be reached by
binding the SMD `DIAG` channel to `rpmsg_chrdev` and talking to it via `/dev/rpmsg0`,
and that requests must be sent **raw** (no HDLC framing). It left one capability gap:

> *"No async traffic is received until log masks are configured. Requires implementing
> the `DIAG_LOG_CONFIG_F` / set-log-mask request."*

This document closes that gap. It records:

1. The **verified** on-wire format of `DIAG_LOG_CONFIG_F` (0x73) — established from
   ground truth, not inference.
2. The empirical behaviour of the command (which operations and payloads are accepted).
3. The reason **0x73 alone never produced logs**, and the `DIAG_CNTL` control-channel
   sequence that does.
4. The confirmed working capture path, with real measured throughput.

---

## 2. SOP Compliance

Per the standing rule for this project (*never blind-patch the baseband; verify against
ground truth*), the `DIAG_LOG_CONFIG_F` format was **not** guessed. It was taken from the
reference Qualcomm diag driver shipped inside this repository:

```
GitIgnore/android_kernel_zte_msm8916/drivers/char/diag/diag_masks.c
    -> diag_process_apps_masks()      (the AP-side decoder for 0x73)
GitIgnore/android_kernel_zte_msm8916/drivers/char/diag/diagfwd_cntl.h
    -> struct diag_ctrl_feature_mask / _log_mask / _event_mask / _msg_mask
GitIgnore/android_kernel_zte_msm8916/drivers/char/diag/diag_masks.c
    -> diag_send_*_mask_update()      (the AP-side encoders)
```

Every field offset, operation code, and control-packet id below is traceable to those
files. The wire behaviour was then **independently confirmed** by probing the live modem
(§5) — the two agree.

### 2.1 Correction to an earlier reading in this session

An earlier interpretation of the reply `14 73 01 ff ff ff ff 6e 67 7e` as
*"DIAG_LOG_CONFIG_F acknowledged"* was **wrong**. `0x14` is **`DIAG_BAD_PARM_F`** — a
rejection. This was proven by sending a deliberately unknown opcode (`0x42`), which
returned `13 42 a8 d1 7e`; `0x13` is `DIAG_BAD_CMD_F`. The two error replies share the
same shape — *error code, then the entire original request echoed* — which is what
identifies `0x14` as an error rather than an acknowledgement.

The root cause of the rejection was a **malformed request**: the log mask was being
placed at byte offset 2, whereas the modem reads `operation` from a little-endian u32 at
offset 4 and the mask from offset 16. The misalignment made the modem parse the mask
bytes as an operation code.

---

## 3. Verified `DIAG_LOG_CONFIG_F` (0x73) Wire Format

### 3.1 Request

| Offset | Size | Type | Field | Notes |
| :--- | :--- | :--- | :--- | :--- |
| 0 | 1 | u8 | command code | `0x73` |
| 1..3 | 3 | — | reserved | must be zero |
| 4..7 | 4 | u32 LE | **operation** | see §3.3 |
| 8..11 | 4 | u32 LE | **equip_id** | used by op 3 and op 4 |
| 12..15 | 4 | u32 LE | **num_items** | op 3 only — last log code for this equip_id |
| 16.. | N | u8[] | **mask** | op 3 only — length `(num_items + 7) / 8` |

This is exactly the layout the reference decoder expects:

```c
/* drivers/char/diag/diag_masks.c :: diag_process_apps_masks() */
if (*buf == 0x73 && *(int *)(buf+4) == 3) {          /* SET log masks */
        buf += 8;
        diag_update_log_mask(*(int *)buf,            /* equip_id  @ 8  */
                             buf+8,                  /* mask      @ 16 */
                             *(int *)(buf+4));       /* num_items @ 12 */
}
else if (*buf == 0x73 && *(int *)(buf+4) == 4) {     /* GET log masks */
        equip_id = *(int *)(buf + 8);
}
else if (*buf == 0x73 && *(int *)(buf+4) == 0) {     /* DISABLE log masks */
```

### 3.2 Response

| Offset | Size | Type | Field |
| :--- | :--- | :--- | :--- |
| 0..3 | 4 | — | `73 00 00 00` |
| 4..7 | 4 | u32 LE | operation (echo) |
| 8..11 | 4 | u32 LE | status |
| 12.. | N | u8[] | payload |

An error reply instead begins with `0x13` (`BAD_CMD`) or `0x14` (`BAD_PARM`) followed by
the echoed request, then CRC + `0x7e`.

### 3.3 Operation codes (empirically mapped on the live modem)

| Operation | Result | Response size | Meaning (per reference driver) |
| :--- | :--- | :--- | :--- |
| 0 | accepted | 15 B | **Disable** log masks |
| 1 | accepted | 79 B | *(not in reference driver — see §3.4)* |
| 2 | accepted | 24 B | *(not in reference driver — see §3.4)* |
| 3 | accepted | 25 B | **Set** log masks |
| 4 | accepted | 25 B | **Get** log masks |
| 5, 6, 7, 8, 255 | **rejected** | 16 B (`0x14`) | `DIAG_BAD_PARM_F` |

### 3.4 Open question — operations 1 and 2

The reference driver only implements 0, 3 and 4. The live modem additionally accepts
1 and 2 and returns non-trivial payloads for them (79 B and 24 B respectively, vs. the
fixed 15 B template for op 0). Their semantics are **not yet determined**. Operation 1
is a plausible "enable all" candidate and is exposed as `log-enable` in the client, but
it was shown **not** to start the log stream on its own (§6).

### 3.5 Length behaviour

Probing `73 01` followed by N zero bytes showed the boundary at **6 bytes total**
(N = 4). Shorter requests returned `0x14`. This is an artefact of the modem reading the
`operation` u32 at offset 4, which extends past a 6-byte message — such requests are
under-length and must not be used. **The correct minimum request is 16 bytes** (op 3/4
with a zero-length mask).

---

## 4. The Control Channel — `DIAG_CNTL`

### 4.1 Channel roles

| SMD channel | rpmsg device | Role |
| :--- | :--- | :--- |
| `DIAG` | `/dev/rpmsg0` | data — command/response and the **log/msg stream** |
| `DIAG_CMD` | (unbound) | separate command/response channel |
| `DIAG_CNTL` | `/dev/rpmsg1` | **control — feature/mask delivery** |

Mask updates are **not** sent on the data channel. The reference driver delivers them on
the control channel:

```c
/* drivers/char/diag/diag_masks.c :: diag_mask_update_fn() */
diag_send_feature_mask_update(smd_info);
diag_send_msg_mask_update(smd_info, ALL_SSID, ALL_SSID, smd_info->peripheral);
diag_send_log_mask_update(smd_info, ALL_EQUIP_ID);
diag_send_event_mask_update(smd_info, diag_event_num_bytes);
```

where `smd_info` is `driver->smd_cntl[i]` — the channel opened as **`DIAG_CNTL`**
(`diagfwd_cntl.c :: diag_smd_cntl_probe()`).

### 4.2 Binding the control channel

```sh
D=/sys/bus/rpmsg/devices/remoteproc0:smd-edge.DIAG_CNTL.-1.-1
echo rpmsg_chrdev > "$D/driver_override"
echo "remoteproc0:smd-edge.DIAG_CNTL.-1.-1" > /sys/bus/rpmsg/drivers/rpmsg_chrdev/bind
# -> /dev/rpmsg1
```

### 4.3 The enable sequence (byte-exact)

All four packets are sent on `/dev/rpmsg1`. All integers are little-endian.
`ALL_ENABLED` = `2` (`DIAG_CTRL_MASK_ALL_ENABLED`).

**1. Feature mask** — `DIAG_CTRL_MSG_FEATURE` (8), 14 bytes

```c
struct diag_ctrl_feature_mask {   /* 12-byte header */
    u32 ctrl_pkt_id;        /* 8  */
    u32 ctrl_pkt_data_len;  /* 6  = 4 + feature_mask_len */
    u32 feature_mask_len;   /* 2  */
    u8  feature_bytes[2];   /* {F_DIAG_INT_FEATURE_MASK | F_DIAG_LOG_ON_DEMAND_RSP_ON_MASTER
                                | F_DIAG_REQ_RSP_CHANNEL, F_DIAG_OVER_STM} = {0x15, 0x02} */
};
```

```
08 00 00 00 06 00 00 00 02 00 00 00 15 02
```

**2. F3 / message mask** — `DIAG_CTRL_MSG_F3_MASK` (11), 23 bytes

```c
struct diag_ctrl_msg_mask {       /* 19-byte header + 4*msg_mask_size */
    u32 cmd_type;        /* 11 */
    u32 data_len;        /* 15 = 11 + 4*msg_mask_size */
    u8  stream_id;       /* 1  */
    u8  status;          /* 2  = ALL_ENABLED */
    u8  msg_mode;        /* 0  = legacy */
    u16 ssid_first;      /* 0  */
    u16 ssid_last;       /* 0  */
    u32 msg_mask_size;   /* 1  */
    u32 msg_mask[1];     /* 0xFFFFFFFF */
};
```

```
0b 00 00 00 0f 00 00 00 01 02 00 00 00 00 00 01 00 00 00 ff ff ff ff
```

**3. Log mask** — `DIAG_CTRL_MSG_LOG_MASK` (9), 19 bytes

```c
struct diag_ctrl_log_mask {       /* 19-byte header */
    u32 cmd_type;        /* 9  */
    u32 data_len;        /* 11 = 11 + log_mask_size */
    u8  stream_id;       /* 1  */
    u8  status;          /* 2  = ALL_ENABLED */
    u8  equip_id;        /* 0  */
    u32 num_items;       /* 0  */
    u32 log_mask_size;   /* 0  */
};
```

```
09 00 00 00 0b 00 00 00 01 02 00 00 00 00 00 00 00 00 00
```

**4. Event mask** — `DIAG_CTRL_MSG_EVENT_MASK` (10), 15 bytes

```c
struct diag_ctrl_event_mask {     /* 15-byte header */
    u32 cmd_type;          /* 10 */
    u32 data_len;          /* 7  = 7 + num_bytes */
    u8  stream_id;         /* 1  */
    u8  status;            /* 2  = ALL_ENABLED */
    u8  event_config;      /* 1  */
    u32 event_mask_size;   /* 0  */
};
```

```
0a 00 00 00 07 00 00 00 01 02 01 00 00 00 00
```

### 4.4 Modem replies on the control channel

The modem answered all four packets, and additionally pushed unsolicited control data:

| Reply | Interpretation |
| :--- | :--- |
| `08 00 00 00 06 00 00 00 02 00 00 00 f7 1e` | FEATURE accepted (echo + status word) |
| `0c 00 00 00 01 00 00 00 02` | `DIAG_CTRL_MSG_NUM_PRESETS` (12) — modem reports **2 presets** |
| 348 B and 468 B messages | `DIAG_CTRL_MSG_REG` (1) carrying **log-code → handler tables** |

The 468-byte table is a list of `{start_code, end_code, address}` triples, e.g.:

```
00 00 | 00 00 | 40 3b 8b c0      -> code 0x00, handler 0xc08b3b40
01 00 | 01 00 | d8 3e 8b c0      -> code 0x01, handler 0xc08b3ed8
...
38 00 | 38 00 | 7c 98 8b c0      -> code 0x38, handler 0xc08b987c
```

These addresses are **identical in layout** to the segment VA bases already catalogued for
this firmware (`0xc02c2000`, `0xc1480000`, `0xc3407000`, `0xc0287000`), so the tables are
directly usable to locate log handlers in the decompiled `modem.bin`. This is a new,
useful RE asset — see §9.

---

## 5. Empirical Verification

### 5.1 Error-code identification

| Probe | Reply | Conclusion |
| :--- | :--- | :--- |
| `42` (unknown opcode) | `13 42 a8 d1 7e` | `0x13` = `DIAG_BAD_CMD_F`; error replies echo the request |
| `73` (truncated) | `14 73 aa bc 7e` | `0x14` = `DIAG_BAD_PARM_F` |
| `0c` | 51-byte payload | valid command (extended build id) |

### 5.2 Operation sweep (well-formed 16-byte requests)

```
op=0    RX (15 bytes):  73 00 00 00 00 00 00 00 00 00 00 00 38 8e 7e
op=1    RX (79 bytes):  73 00 00 00 01 00 00 00 ...
op=2    RX (24 bytes):  73 00 00 00 02 00 00 00 ...
op=3    RX (25 bytes):  73 00 00 00 03 00 00 00 ...
op=4    RX (25 bytes):  73 00 00 00 04 00 00 00 ...
op=5    RX (16 bytes):  14 73 00 00 00 05 00 00 00 00 00 00 00 08 fa 7e
op=255  RX (16 bytes):  14 73 00 00 00 ff 00 00 00 00 00 00 00 40 9f 7e
```

The response's u32 at offset 4 echoes the requested operation — confirming the offset-4
interpretation of the request.

### 5.3 What did **not** work

`0x73` was sent in every accepted operation (0, 1, 2, 3, 4) and followed by a 12–30 s
listen on `/dev/rpmsg0`. In **every** case the result was:

```
--- 0 messages, 0 bytes, 0 LOG_F ---
```

This was also true while the modem was driven with AT activity on `/dev/wwan0at0`.
**Conclusion: `DIAG_LOG_CONFIG_F` (0x73) does not, by itself, cause the modem to emit
its log stream.** It is a configuration query/setter for the AP-side mask table, not the
switch that enables modem-side logging.

---

## 6. Result — Log Stream Confirmed Live

Immediately after the §4.3 control-channel sequence was sent, `/dev/rpmsg0` began
delivering unsolicited traffic:

| Run | Duration | Messages | Bytes | `LOG_F` (0x10) | `MSG_F` (0x11) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| A | 30 s | 202 | 336 825 | 40 | 0 |
| B | 20 s | 116 | 188 642 | 20 | 0 |

Message first-byte histogram for run B:

| First byte | Count | Note |
| :--- | :--- | :--- |
| `0x79` (121) | 68 | F3-style message record |
| `0x92` (146) | 28 | `0x10 \| 0x80` — event-variant log record |
| `0x10` (16) | 20 | `DIAG_LOG_F` |

The stream **persisted** across subsequent tool invocations (a later `capture` run
without re-sending the sequence still produced traffic), so the enable is a one-shot
operation, not a per-capture requirement.

### 6.1 Decoded content — proof the capture is useful

Extracting printable runs from the 116-message capture yields real modem F3 diagnostics:

| Count | Text |
| :--- | :--- |
| 415 | `VADC: Both PM_MPP_2 and PM_MPP_4 is not configured as current sink: so no ReCalibration` |
| 415 | `DalVAdc.c` |
| 14 | `tle_log.c` |
| 4 | `TPC: Lat:0.000000, Lon:0.000000, Alt:0.000000, Punc:21500000.000000, AltUnc:10000.000000` |
| 2 | `TPC: Sub:0, TimeValid:0, Wk:0, msec:0` |
| 2 | `Slow Clk releasing CDMA Time Update: Tunc 0.000000 GpsRtc 0 GpsMs 0 TBias 0.000000` |
| 2 | `mc_slow_clk.c` |

These are the modem's internal F3 messages — **file name, line, and formatted text** —
which is exactly the class of message that previously only appeared on the serial console
as an assert string (`mmoc.c:2326`). The DIAG path now provides them **without** a crash.

> The current capture contains only idle-state messages (VADC calibration, location
> engine, slow clock). No LTE/RRC/attach messages appear because the modem was not
> attempting an attach during the capture. Capturing during an attach attempt is the
> remaining step — see §8.

---

## 7. Command Reference

The client is `GitIgnore/compare/diag_logtool.c`, built static for aarch64 musl and
deployed to `/root/diag_logtool`.

```sh
TC=<openwrt>/staging_dir/toolchain-aarch64_generic_gcc-14.3.0_musl/bin/aarch64-openwrt-linux-musl-gcc
$TC -static -Os -fno-stack-protector -o diag_logtool diag_logtool.c
scp diag_logtool root@<router>:/root/diag_logtool
```

| Command | Purpose |
| :--- | :--- |
| `selftest` | `DIAG_VERNO_F` (0x00) round-trip; proves the data channel |
| `raw <hex>` | send an arbitrary payload, print the reply |
| `log-config <op>` | generic `0x73` request (zeroed equip_id/num_items) |
| `log-disable` | `0x73` op 0 |
| `log-enable` | `0x73` op 1 |
| `set-mask <equip> <nitems> <maskhex>` | `0x73` op 3 |
| `get-mask <equip>` | `0x73` op 4 |
| `cntl-enable [dev]` | **send the §4.3 control-channel sequence (this is what enables logging)** |
| `cntl-disable [dev]` | stop the log stream (ALL_DISABLED masks) — see §12.4 caveat |
| `cntl-dump [dev]` | read control-channel traffic for 5 s |
| `listen <secs>` | print decoded messages for N seconds |
| `capture <secs> <file>` | write a length-prefixed binary capture |

### 7.1 End-to-end procedure

```sh
# 1. bind the data channel (Doc 136 §5)
D=/sys/bus/rpmsg/devices/remoteproc0:smd-edge.DIAG.-1.-1
echo rpmsg_chrdev > "$D/driver_override"
echo "remoteproc0:smd-edge.DIAG.-1.-1" > /sys/bus/rpmsg/drivers/rpmsg_chrdev/bind

# 2. bind the control channel (this doc §4.2)
D=/sys/bus/rpmsg/devices/remoteproc0:smd-edge.DIAG_CNTL.-1.-1
echo rpmsg_chrdev > "$D/driver_override"
echo "remoteproc0:smd-edge.DIAG_CNTL.-1.-1" > /sys/bus/rpmsg/drivers/rpmsg_chrdev/bind

# 3. verify, enable, capture
/root/diag_logtool selftest
/root/diag_logtool cntl-enable /dev/rpmsg1
/root/diag_logtool capture 30 /tmp/diag_cap.bin
```

### 7.2 Parsing a capture

Capture records are `u32 LE length` followed by the message bytes:

```python
import struct, re
d = open('diag_cap.bin','rb').read()
i, msgs = 0, []
while i + 4 <= len(d):
    n = struct.unpack('<I', d[i:i+4])[0]; i += 4
    if n <= 0 or i + n > len(d): break
    msgs.append(d[i:i+n]); i += n
for m in msgs:
    for s in re.findall(rb'[ -~]{6,}', m):
        print(s.decode())
```

### 7.3 Gotcha — `selftest` can report `UNEXPECTED reply` once the stream is live

After enabling, the data channel carries unsolicited traffic. `selftest` may therefore
read a queued log packet instead of the `VERNO` reply. This is **expected** and does not
indicate a fault; drain the channel or run `selftest` before `cntl-enable`.

---

## 8. Pending Work

| # | Item | Notes |
| :--- | :--- | :--- |
| 0 | **Build + flash with BSP patch 819** | **Blocking prerequisite for everything below.** Until the `rpmsg_char` NULL guard is in the running kernel, the DIAG bridge can panic the AP (§12). Build with `./build.sh build <board>`. |
| 1 | **Capture during an LTE attach attempt** | The single highest-value next step. The capture path is proven; it now needs to run while the modem attempts attach, so the RRC/NAS/MMOC messages that precede the hang or crash are recorded. |
| 2 | **Decode `DIAG_LOG_F` records properly** | The 0x10/0x79/0x92 record layouts are not yet mapped. F3 text is readable as-is; binary log records need the per-log-code structure. |
| 3 | **Determine operations 1 and 2** | Accepted but undocumented (§3.4). |
| 4 | **Use the §4.4 handler tables** | The log-code → address tables returned on the control channel can locate log handlers directly in `Modem RE/*/modem_full_decompiled.c`. |
| 5 | **Re-verify after modem SSR** | The rpmsg bindings and the enable are expected to be lost on a modem restart; re-run §7.1. |
| 6 | **Fold into Doc 135** | Doc 135 Stage 2/3 assumed a capture path that did not exist; it now does. |

---

## 9. Artifacts

| Artifact | Path | Status |
| :--- | :--- | :--- |
| This document | `Docs/Modem Stability/137_HMU05_DIAG_LOG_STREAM_ENABLEMENT_AND_LOG_CONFIG_F_PROTOCOL.md` | Reference |
| DIAG client source | `GitIgnore/compare/diag_logtool.c` | Working |
| DIAG client binary | `/root/diag_logtool` (on router) | Deployed |
| Reference ground truth | `GitIgnore/android_kernel_zte_msm8916/drivers/char/diag/` | Source of truth for §3/§4 |
| **Kernel fix (BSP patch)** | `msm89xx/patches/819-rpmsg-char-guard-null-eptdev.patch` | §12.5 — applies cleanly, not yet built/flashed |
| Panic records (archived) | `/root/pstore_archive/` on the router | `console-ramoops-0`, `dmesg-ramoops-0/1` |
| Sample capture | `/tmp/diag_cap.bin` (router), local copy | 116 msgs / 188 642 B |
| Superseded tool | `GitIgnore/compare/diag_frame.py` | HDLC — not applicable (Doc 136 §4.5) |

---

## 10. Device State After This Investigation

| Item | Value |
| :--- | :--- |
| `/dev/rpmsg0` | **unbound** — lost on the AP reboot of §12; re-apply §7.1 |
| `/dev/rpmsg1` | **unbound** — lost on the AP reboot of §12; re-apply §4.2 |
| Modem log stream | **disabled** (stopped by `cntl-disable`) |
| Firmware | **Unmodified** — `modem.mdt` MD5 `72ae7f0bfa910873739a409f94910cdd` |
| `remoteproc0` | `running` |
| Coredump mode | `inline` |
| pstore | `/sys/fs/pstore/console-ramoops-0` holds the §12 panic; clear after reading |
| Router address | `192.168.8.1` (USB Ethernet `enu1i2`; `192.168.1.1` no longer answers SSH) |

All changes are **host-side and non-invasive**. The modem firmware is untouched; the
`rpmsg_chrdev` bindings are reverted with the unbind commands in Doc 136 §5.4.

---

## 11. Risk Notes

| Risk | Assessment |
| :--- | :--- |
| **Sustained DIAG traffic can panic the kernel** | **HIGH — confirmed, see §12.** The raw `rpmsg_chrdev` bridge over the SMD channel is not stable under combined log+command load. Capture in short, bounded windows. |
| Enabling full logging affects modem timing | **Low–moderate.** Logging is a debug path; heavy F3/log traffic can add load. Capture in bounded windows, not permanently. |
| Malformed `0x73` requests | **Low.** Rejections return `BAD_PARM`/`BAD_CMD` and are non-fatal — verified across ~40 malformed probes with no SSR. |
| Control-channel writes | **Low.** The sequence mirrors the reference AP driver byte-for-byte. |
| Exclusive open of `/dev/rpmsg0` | **Known.** One process must own read+write; the tool does. |
| Bindings lost on SSR **or AP reboot** | **Expected.** Re-apply §7.1 after any modem restart or router reboot. |

---

## 12. Known Instability — Kernel Panic in the SMD/rpmsg Path

### 12.1 Symptom

The router **rebooted unexpectedly** during DIAG work — twice, reproducibly. The pstore
record (`/sys/fs/pstore/console-ramoops-0`) shows a **kernel panic in the Qualcomm SMD /
rpmsg interrupt path**, not a modem fault:

```
Unable to handle kernel NULL pointer dereference at virtual address 0000000000000390
EC = 0x25: DABT (current EL)   FSC = 0x06: level 2 translation fault
Internal error: Oops: 0000000096000006 [#1] PREEMPT SMP
CPU: 0 UID: 0 PID: 0 Comm: swapper/0
pc : _raw_spin_lock+0x1c/0x4c
lr : rpmsg_ept_cb+0x58/0xa0
x20: 0000000000000000          <- eptdev == NULL
x19: 0000000000000390
x0 : 0000000000000390          <- lock address passed to _raw_spin_lock
Call trace:
  _raw_spin_lock+0x1c/0x4c
  qcom_smd_channel_intr+0x15c/0x29c
  qcom_smd_edge_intr+0x50/0x13c
  ...
Kernel panic - not syncing: Oops: Fatal exception in interrupt
Rebooting in 3 seconds..
```

### 12.2 Root cause — VERIFIED

This is an **upstream Linux bug** in `drivers/rpmsg/rpmsg_char.c`, not a modem or firmware
defect. The kernel in use (`6.12.94`) is missing a NULL guard that upstream master has.

`qcom_smd_channel_recv_single()` hands the endpoint's `priv` straight to the callback:

```c
/* drivers/rpmsg/qcom_smd.c */
struct rpmsg_endpoint *ept = &channel->qsept->ept;
ret = ept->cb(ept->rpdev, ptr, len, ept->priv, RPMSG_ADDR_ANY);
```

and `rpmsg_core` creates that endpoint with **`priv = NULL`**:

```c
/* drivers/rpmsg/rpmsg_core.c :: rpmsg_dev_probe() */
ept = rpmsg_create_ept(rpdev, rpdrv->callback, NULL, chinfo);   /* priv = NULL */
...
err = rpdrv->probe(rpdev);                                      /* sets priv later */
```

Only `rpmsg_chrdev_probe()` later patches it up (`eptdev->default_ept->priv = eptdev;`).
If the SMD channel delivers data in that window — or after a rebind — the callback runs
with `priv == NULL`, and `rpmsg_ept_cb()` dereferences it immediately:

```c
/* drivers/rpmsg/rpmsg_char.c — our 6.12.94 */
static int rpmsg_ept_cb(struct rpmsg_device *rpdev, void *buf, int len,
			void *priv, u32 addr)
{
	struct rpmsg_eptdev *eptdev = priv;
	struct sk_buff *skb;

	skb = alloc_skb(len, GFP_ATOMIC);
	...
	spin_lock(&eptdev->queue_lock);      /* <-- eptdev == NULL */
```

### 12.3 Proof of the exact offset

`pahole` on the tree's own `vmlinux` gives the struct layout:

```
struct rpmsg_endpoint *    ept;          /* 896 */
struct rpmsg_endpoint *    default_ept;  /* 904 */
spinlock_t                 queue_lock;   /* 912 */
```

**`offsetof(struct rpmsg_eptdev, queue_lock) = 912 = 0x390`** — bit-exact with the faulting
address in the panic. `rpmsg_ept_cb` is defined only once in the tree
(`drivers/rpmsg/rpmsg_char.c:101`), so there is no symbol ambiguity. The root cause is
therefore established, not inferred.

### 12.4 The trigger is the bridge, not the clock

Both panics occurred with `rpmsg_chrdev` bound to SMD channels, and both happened at
~1170 s uptime — which initially suggested a time-based fault. That was **tested and
disproven**:

| Condition | Result |
| :--- | :--- |
| `rpmsg_chrdev` bound to `DIAG` / `DIAG_CNTL` | panic (×2) |
| Device left **idle, no bindings**, monitored every 30 s | **stable past 1297 s**, pstore empty |

So the ~1170 s coincidence was not causal. The panic requires the `rpmsg_chrdev` endpoint
on an SMD channel — i.e. **it is a hazard introduced by the DIAG bridge**, specifically
the window in which an endpoint exists with `priv == NULL`. Re-binding a channel
(`unbind` → `bind`) re-opens that window, which is the most likely reproducer.

> This also means the panic is **not** the cause of the original "modem stalls after
> 15 minutes" symptom, which occurs with no DIAG bridge present.

### 12.5 The fix — backported upstream guard

Upstream master contains the guard our kernel lacks:

```c
static int rpmsg_ept_cb(struct rpmsg_device *rpdev, void *buf, int len,
			void *priv, u32 addr)
{
	struct rpmsg_eptdev *eptdev = priv;
	struct sk_buff *skb;

	if (!eptdev)        /* present upstream, absent in 6.12.94 */
		return 0;
	...
```

(`rpmsg_ept_flow_cb` has the same guard upstream.)

Backported into the BSP as:

```
msm89xx/patches/819-rpmsg-char-guard-null-eptdev.patch
```

which adds the guard to both callbacks. It is a plain unified diff following the existing
`8xx` BSP convention and applies cleanly (`patch -p1 --dry-run` verified). Per the project
layout, the kernel tree itself stays clean — `build.sh` syncs `msm89xx/` into
`openwrt/target/linux/` and applies the patch at build time.

### 12.6 Operational guidance until the patch is flashed

| # | Guidance |
| :--- | :--- |
| 1 | **Treat every unexpected reboot during DIAG work as this panic** and read `/sys/fs/pstore/` before blaming the modem. |
| 2 | **Avoid `unbind`/`bind` cycles** — re-binding is the most likely way to hit the NULL window. Bind once and leave it. |
| 3 | **Do not set `F_DIAG_REQ_RSP_CHANNEL`** unless command/response on `DIAG_CMD` is actually wanted (§4.3 packet 1). |
| 4 | **Bind only the channels in use.** |
| 5 | **Keep capture windows short** (≤30 s) and clear pstore after each session. |

> [!WARNING]
> `cntl-disable` (ALL_DISABLED masks) **does** stop the log stream, but it does **not**
> restore command/response on the data channel. Verify with `selftest` before relying on
> it after an enable/disable cycle.
