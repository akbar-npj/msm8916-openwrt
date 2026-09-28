# MSM8916 DIAG Bridge and Logtool Reference

## 1. Overview and Architecture

Qualcomm Snapdragon 410 / MSM8916 devices communicate with the integrated Hexagon Modem DSP (QDSP6 / MPSS) over Shared Memory Driver (SMD) edges. Among these SMD channels, two are dedicated to diagnostics:

1. **`remoteproc0:smd-edge.DIAG.-1.-1`**: The primary data channel for raw DIAG packets (command requests, unsolicited event reports, and F3 debug message streams).
2. **`remoteproc0:smd-edge.DIAG_CNTL.-1.-1`**: The control channel for configuration masks, subsystem SSID enablement, and flow control.

In mainline Linux and modern OpenWrt kernels (e.g. 6.6 / 6.12), the kernel `qcom_smd` driver exposes SMD channels as `rpmsg` bus devices. By binding these devices to the `rpmsg_chrdev` driver, userspace character devices (`/dev/rpmsg0` and `/dev/rpmsg1`) are created, allowing standard userspace tools to directly interact with the Qualcomm baseband diagnostic subsystem without proprietary Android daemons (`diag-router`, `diag_mdlog`).

```
+-------------------------------------------------------------------------+
|                              Userspace                                  |
|   diag_logtool (C)  /  f3clean.py (Python)  /  diag_bind.sh (Shell)     |
+------------------------------------+------------------------------------+
                                     |
               /dev/rpmsg0 (Data)    |    /dev/rpmsg1 (Control)
+------------------------------------+------------------------------------+
|                         Kernel rpmsg_chrdev                             |
|  (/sys/bus/rpmsg/drivers/rpmsg_chrdev/bind -> rpmsg character devices)  |
+------------------------------------+------------------------------------+
|                         Kernel qcom_smd                                 |
|             remoteproc0:smd-edge.DIAG / DIAG_CNTL                       |
+------------------------------------+------------------------------------+
                                     | Shared Memory (SMD)
+------------------------------------+------------------------------------+
|                   Hexagon DSP (Modem Baseband)                          |
|         DIAG Subsystem, F3 Logger, Call Manager, MMOC, RFC, LTE         |
+-------------------------------------------------------------------------+
```

---

## 2. Kernel Prerequisites and Safety Guards

1. **Kernel Config Options**:
   - `CONFIG_RPMSG=y` or `m`
   - `CONFIG_RPMSG_CHAR=y` or `m`
   - `CONFIG_QCOM_SMD=y` or `m`
   - `CONFIG_QCOM_Q6V5_MSS=y` or `m`

2. **Subsystem Restart (SSR) and UAF Guard**:
   When the modem crashes or restarts (SSR), `qcom_smd` destroys and re-creates all rpmsg device objects. Re-binding `rpmsg_chrdev` during this window can trigger a NULL pointer dereference in `rpmsg_ept_cb` if `priv == NULL`. Ensure the kernel contains the guard patch:
   ```c
   /* in drivers/rpmsg/rpmsg_char.c */
   static int rpmsg_ept_cb(struct rpmsg_device *rpdev, void *buf, int len,
                           void *priv, u32 addr)
   {
       struct rpmsg_eptdev *eptdev = priv;
       if (!eptdev)
           return 0; /* Guard against early/stale callbacks */
       ...
   }
   ```

---

## 3. The DIAG Bridge Binding Script (`diag_bind.sh`)

Save this script as `/root/diag_bind.sh` (mode `0755`). It dynamically binds the SMD endpoints, waits for device nodes to be registered, and ensures `/dev/rpmsg0` and `/dev/rpmsg1` exist with correct major/minor numbers.

```sh
#!/bin/sh
# diag_bind.sh - Establish the Qualcomm SMD DIAG <-> rpmsg_chrdev bridge.
# Universal for all MSM8916 Linux / OpenWrt devices.

set -u
BUS=/sys/bus/rpmsg
DRV=rpmsg_chrdev
DIAG=remoteproc0:smd-edge.DIAG.-1.-1
CNTL=remoteproc0:smd-edge.DIAG_CNTL.-1.-1

bind_one() {
    dev=$1
    d=$BUS/devices/$dev
    if [ ! -d "$d" ]; then
        echo "ERROR: no such rpmsg device: $dev" >&2
        return 1
    fi
    cur=$(basename "$(readlink "$d/driver" 2>/dev/null)" 2>/dev/null)
    if [ "$cur" = "$DRV" ]; then
        return 0
    fi
    if [ -n "$cur" ]; then
        echo "$dev" > "$BUS/drivers/$cur/unbind" 2>/dev/null
    fi
    echo "$DRV" > "$d/driver_override" 2>/dev/null || return 1
    echo "$dev" > "$BUS/drivers/$DRV/bind" 2>/dev/null || return 1
    return 0
}

node_of() {
    d=$BUS/devices/$1
    for p in "$d"/rpmsg/rpmsg*; do
        [ -e "$p" ] || continue
        basename "$p"
        return 0
    done
    return 1
}

ensure_dev() {
    name=$1
    [ -n "$name" ] || return 1
    [ -c "/dev/$name" ] && return 0
    devt=$(cat "/sys/class/rpmsg/$name/dev" 2>/dev/null)
    [ -n "$devt" ] || return 1
    mknod "/dev/$name" c "${devt%:*}" "${devt#*:}" 2>/dev/null || return 1
    chmod 600 "/dev/$name"
    return 0
}

bind_one "$DIAG" || exit 1
bind_one "$CNTL" || exit 1

i=0
while [ "$i" -lt 10 ]; do
    D0=$(node_of "$DIAG"); D1=$(node_of "$CNTL")
    [ -n "${D0:-}" ] && [ -n "${D1:-}" ] && break
    sleep 1
    i=$((i+1))
done

D0=$(node_of "$DIAG"); D1=$(node_of "$CNTL")
if [ -z "${D0:-}" ]; then echo "ERROR: DIAG bound but produced no rpmsg node" >&2; exit 1; fi
if [ -z "${D1:-}" ]; then echo "ERROR: DIAG_CNTL bound but produced no rpmsg node" >&2; exit 1; fi

ensure_dev "$D0" || { echo "ERROR: cannot create /dev/$D0" >&2; exit 1; }
ensure_dev "$D1" || { echo "ERROR: cannot create /dev/$D1" >&2; exit 1; }

# Verify exclusive read-write open
if ! ( exec 3<>"/dev/$D0" ) 2>/dev/null; then
    echo "ERROR: /dev/$D0 will not open read-write" >&2
    exit 1
fi
if ! ( exec 3<>"/dev/$D1" ) 2>/dev/null; then
    echo "ERROR: /dev/$D1 will not open read-write" >&2
    exit 1
fi

echo "DIAG=/dev/$D0"
echo "CNTL=/dev/$D1"
```

---

## 4. The Diagnostic Tool (`diag_logtool`)

### 4.1 Transport Rules
1. **Raw Requests**: Outgoing requests to `/dev/rpmsg0` must be sent RAW (no HDLC framing, no trailing CRC). HDLC framing will be rejected by the Hexagon firmware.
2. **Framed Responses**: Incoming messages from `/dev/rpmsg0` include trailing CRC16 + `0x7E` delimiter.
3. **Atomic Reads**: Each `read()` call returns exactly **one** DIAG packet. Reading with a buffer smaller than the message causes silent truncation. Always use a 4096-byte buffer.
4. **Exclusive Access**: `/dev/rpmsg0` is exclusive-open per process. The same process must handle writes and reads.

### 4.2 C Source Code (`diag_logtool.c`)

Compile statically for OpenWrt target:
```bash
aarch64-openwrt-linux-musl-gcc -static -Os -fno-stack-protector -o diag_logtool diag_logtool.c
```

Full source located in repository at [`GitIgnore/compare/diag_logtool.c`](file:///home/shaanair/Projects/msm8916-openwrt-clean/GitIgnore/compare/diag_logtool.c).

Key commands supported by `diag_logtool`:
- `diag_logtool selftest`: Sends `DIAG_VERNO_F` (0x00) and prints firmware build timestamp.
- `diag_logtool cntl-enable [dev] [nssid]`: Sweeps subsystem IDs (SSIDs) on the control channel (`/dev/rpmsg1`) and instructs the baseband to stream all F3 log messages.
- `diag_logtool log-enable`: Issues `DIAG_LOG_CONFIG_F` (0x73, op 1) on data channel.
- `diag_logtool capture <seconds> <output_file>`: Captures raw incoming stream directly to disk without console overhead.
- `diag_logtool listen <seconds>`: Live hex/text dump to stdout.

---

## 5. Standard Operating Procedures for Diagnostics

### 5.1 Real-Time Capture on Device
To capture modem activity while triggering an operation (e.g. QMI mode change, AT command, network scan):

```sh
# 1. Bind the interfaces
/root/diag_bind.sh

# 2. Enable F3 log streaming on control channel
/root/diag_logtool cntl-enable /dev/rpmsg1
/root/diag_logtool log-enable

# 3. Launch background capture and execute trigger command
(
    /root/diag_logtool capture 10 /tmp/diag_capture.bin &
    PID=$!
    sleep 1
    # Trigger command here
    qmicli -p -d /dev/wwan0qmi0 --dms-set-operating-mode=online
    wait $PID
)
```

### 5.2 Boot Capture Service (`/etc/init.d/diagboot`)
To capture early boot messages (before userspace network management begins):

```sh
#!/bin/sh /etc/rc.common
START=11
USE_PROCD=1

start_service() {
    procd_open_instance
    procd_set_param command /bin/sh /root/diagboot_run.sh
    procd_set_param respawn 0 0 0
    procd_close_instance
}
```

Where `/root/diagboot_run.sh`:
```sh
#!/bin/sh
DEV=/sys/bus/rpmsg/devices/remoteproc0:smd-edge.DIAG.-1.-1
i=0
while [ ! -d "$DEV" ] && [ "$i" -lt 60 ]; do
    sleep 1
    i=$((i+1))
done

/root/diag_bind.sh > /root/diagboot.log 2>&1
/root/diag_logtool cntl-enable /dev/rpmsg1 >> /root/diagboot.log 2>&1
/root/diag_logtool capture 30 /root/diag_boot.bin >> /root/diagboot.log 2>&1
```

---

## 6. Parsing Diagnostic Logs

To extract human-readable logs from the captured `.bin` file, use [`scratch/f3clean.py`](file:///home/shaanair/Projects/msm8916-openwrt-clean/scratch/f3clean.py):

```python
import sys
sys.path.insert(0, 'scratch')
import f3clean

raw, msgs = f3clean.parse('diag_boot.bin')
for m in msgs:
    fmt = m.get('fmt', '')
    args = tuple(m.get('args', []))
    try:
        msg = fmt % args
    except Exception:
        msg = fmt
    print(f"{m.get('ts', 0):.3f} [{m.get('file', '')}:{m.get('line', 0)}] {msg}")
```

This decoder unpacks `DIAG_MSG_F` (0x79) frames containing native ASCII format strings, source file paths, and argument vectors generated by `MSG_LOW`, `MSG_MED`, `MSG_HIGH`, and `MSG_ERR` inside the Qualcomm baseband firmware.
