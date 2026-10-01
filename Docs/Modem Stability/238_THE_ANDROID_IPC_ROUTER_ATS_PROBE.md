# 238 — THE ANDROID-SIDE ATS PROBE: the modem's own uptime, read directly over the IPC Router

Date: 2026-09-30
Ledger: this doc is item 56 of `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md`
Device: Android arm, `root@192.168.100.1`, `uname -m` = `armv7l`
Question (user, verbatim): **"Deploy ats-probe and collect fatals"** → then **"i have only device , so while
android is running , i cannot run openwrt"**

---

## 1. Why this doc

`ats-probe` (Doc 237 / ledger item 55) is the standalone read-only QRTR/QMI TIME client built for the
**OpenWrt** arm. Its whole purpose is `R_fatal` — the modem's **own** uptime at a fatal, with no
borrowed offset — plus the `ρ = Δrtc/Δap` SCLK-drift test and the reset/condition discriminator.

The OpenWrt arm is **not available**: the user has one dongle and it is currently running Android.
`ats-probe` **cannot run on Android at all** — Android has no `CONFIG_QRTR` (`/proc/net/qrtr` absent;
`ipc_router_core` + `ipc_router_smd_xprt` are present instead).

This doc records the **Android-side equivalent that was built and worked**: a freestanding ARM binary
that speaks the modem's QMI TIME service **directly over the Android IPC Router**, and the `R_fatal`
measurement it produced.

## 2. The Android transport — no QRTR, and the daemon does not expose the RTC

Two independent facts had to be established first.

### 2.1 `time_daemon` is the modem's QMI TIME owner, and it is reachable

Android runs `time_daemon` (`/init.target.rc:153`, `class late_start`, **no arguments**, root). It owns
the modem's QMI TIME link and exposes genoff values to other userspace over an **abstract AF_UNIX
socket** named `time_genoff` (verified in `/proc/net/unix`: `... 8355 @time_genoff`).

The client protocol was recovered from `/system/vendor/lib/libtime_genoff.so`
(md5 `699033249d496ffaf0914239254856f8`, **Thumb-2**, symbol `time_genoff_operation` @ `0x62c`) and then
**verified byte-for-byte against a live `strace`**:

```
request/response record, 32 bytes, one connection per request, abstract socket "time_genoff"
  +0x00  int32   base        (0..14)
  +0x04  int32   const 1     (response: 1 on success, 0 on error)
  +0x08  int32   op          (0 = SET, 1 = GET, 2 = DISABLE)
  +0x0c  int32   (unused)
  +0x10  u64     ts_val
  +0x18  int32   result      (request: -1; response: 0 == ok)
sockaddr_un: sun_family = AF_UNIX, sun_path = "\0time_genoff", addrlen = 14
```

Live confirmation (strace of `time_daemon`, request for base 0 / base 1):

```
recvfrom(19, "\0\0\0\0\1\0\0\0\1\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\377\377\377\377\0\0\0\0", 32) = 32
sendto  (19, "\0\0\0\0\0\0\0\0\1\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\352\377\377\377\0\0\0\0", 32) = 32   <- result -22
```

### 2.2 ★ NEGATIVE: the daemon refuses base 0 (ATS_RTC)

`genoff_get(base 0)` returns `result = -22` (**EINVAL**) **without touching the modem** — the reply is
emitted immediately and the modem is never queried. Measured **0 successes in 591 samples at 4 Hz**
(`ats_all_arm`).

Sweeping every base 0..14 through the daemon:

| bases | daemon result | meaning |
|---|---|---|
| **0, 4, 5, 6, 9, 14** | **-22 (EINVAL)** | not served |
| 1, 2, 13 | 0 | wall clock, **ms since epoch** |
| 3, 7, 8, 10, 11, 12 | 0 | wall clock, constant offset from base 1 |

All successful bases advance at **exactly the same rate as each other** (ratio **1.000000** over
155.9 s) — they are all the same wall clock. The daemon also **caches at ~1 Hz** (157 distinct values
over 155.9 s), so it can never resolve better than 1 s.

⇒ **No Android daemon base carries the modem's uptime.** The daemon route is a dead end for `R_fatal`.

## 3. ★★★ The modem IS reachable directly — over the Android IPC Router

`time_daemon` was restarted under `strace` (`setprop ctl.stop time_daemon`, run
`/system/bin/time_daemon` under strace, then `setprop ctl.start time_daemon`). That exposed its QMI
transport and a real QMI TIME exchange:

```
socket(AF_IB, SOCK_DGRAM, 0)                       = 13
sendto(13, "\0\1\0 \0\22\0\1\4\0\2\0\0\0\2\10\0\351u\305\\W\1\0\0", 25, MSG_DONTWAIT,
       {sa_family=AF_IB, sa_data="\313\270\2\332\255\276\0\0\0\0\v\0\0\0A\34\360\266"}, 20) = 25
recvfrom(13, "\2\1\0 \0\7\0\2\4\0\0\0\0\0", 32, ...) = 14
```

* **`AF_IB` here is `AF_MSM_IPC` (family 27)** — the Android IPC Router. `strace` simply renders 27 as
  `AF_IB`. The daemon's attempt to use the qmuxd path fails first:
  `bind(13, {sun_path="/dev/socket/qmux_radio/qmux_client_socket  <pid>"}, 50) = -1 EACCES`.
* The payload is a **plain QMI message** — `type(1) txn(2) msg_id(2) len(2)` then TLVs.
  The observed one is `msg_id = 0x0020` (**GENOFF_SET**), TLV `01 04 00 02 00 00 00` (base 2) +
  TLV `02 08 00 E9 75 C5 5C 57 01 00 00` (ts_val). Reply `02 01 00 20 00 07 00 02 04 00 00 00 00 00`
  = result 0. **The daemon *writes* time to the modem; it never reads ATS_RTC.**
* The destination address (20 bytes) was copied verbatim from the strace. Port **11** is visible at
  offset 10, matching the recorded "QMI TIME svc 22, node 0 port 11".

### 3.1 The probe

`ipc_probe.c` — freestanding ARMv7, **raw syscalls only**, no libc, no dynamic linker, no NDK:

```
socket(27, SOCK_DGRAM, 0)
setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, {3,0})
sendto(fd, qmi_genoff_get(base), 14, 0, &dest_addr, 20)
recvfrom(fd, resp, 128, 0, 0, 0)
```

The QMI request is **`msg_id = 0x0021` (GENOFF_GET), 14 bytes** —
`00 01 00 21 00 07 00 | 01 04 00 <base> 00 00 00`. **READ ONLY: `0x0020` (SET) is never built.**

Build (host `clang 22.1.8`, which can emit ARM natively):

```sh
clang --target=armv7a-linux-gnueabi -mcpu=cortex-a7 -mthumb \
      -nostdlib -static -ffreestanding -fno-builtin -fno-stack-protector \
      -O2 -Wl,-e,_start -o ipc_probe ipc_probe.c
```

**First run — it works immediately:**

```
IPC-GET base=0 ap_boot_ms=8575621 sendto=14 recv=32
  hex=02010021001900 |02040000000000|03040000000000|040800 4EB90700 00000000  msg_id=33 result=0
```

`msg_id = 0x0021`, `result = 0`, and TLV type `0x04` carries the **value**.

## 4. ★★★ RESULTS

### 4.1 base 0 = the modem's own uptime, in milliseconds

| check | result |
|---|---|
| rate vs `CLOCK_BOOTTIME` | **ratio = 1.000000** (441 samples over 129.641 s, **0 anomalous steps**) |
| Δ across a fatal | resets to ~0 |
| modem epoch = AP − uptime | **AP 8974.366 s** (from `8975.410 − 1.044`); previous cycle AP **8069.432 s** (from `8971.762 − 902.330`) |

### 4.2 `R_fatal`, measured directly

One boot, AP-uptime fatal ledger (n = 8), site `lte_ml1_common_timer.c:390` for #2..#8:

```
2637.391036  3542.348641  4447.300237  5352.250832  6257.213077
7162.168625  8067.118791  8972.078057
```

AP fatal→fatal (n = 7): **mean 904.955289 s, spread 0.012079 s** (min 904.950166, max 904.962245).
This **independently confirms** Doc 234 §2's `904.954383 ± 0.002920` (n = 15).

**The fatal at 8972.078057, captured at 5 Hz:**

| sample | ap_boot_ms | base 0 (ms) |
|---|---|---|
| last before | 8 971 762 | **902 330** |
| *3648 ms gap — the modem is unresponsive* | | |
| first after | 8 975 410 | 1 044 |

* The probe normally samples every ~290 ms. It produced **zero responses for 3648 ms** across the
  fatal ⇒ the modem was **dead/resetting**, so the counter **froze at death**.
* The modem's **new epoch = AP 8974.366 s** (8975.410 − 1.044).
* ⇒ modem uptime at the AP fatal line (8972.078) = **902.646 s**.

**`R_fatal = 902.646 s` — measured with no borrowed offset.** Consistent with the recorded
`902.722930 ± 0.155438` (n = 16, Doc 234 §2), which was derived from the AP's *"Brought out of reset"*
marker. The two methods now agree.

### 4.3 ★ The reset/condition discriminator

**base 0 RESETS at the modem restart.** It is therefore a **modem-local counter**, not an always-on
clock. **base 1 (ATS_TOD) ALSO resets** — last value `1 474 794 077 725` ns before, `843` ns after — so
it too is modem-local, **not** an always-on source. (This is the §47 discriminator: *reset ⇒ condition*.)

### 4.4 The reset latency

| cycle | AP fatal line | modem new epoch | latency |
|---|---|---|---|
| #7 → #8 | 8067.118791 | 8069.432 | **2.313 s** |
| #8 → #9 | 8972.078057 | 8974.366 | **2.288 s** |

⇒ `AP fatal→fatal = R_fatal + reset latency` (902.646 + 2.288 = 904.934 ≈ 904.955 measured). ✓

### 4.5 The ρ SCLK-drift test

`ρ = Δrtc/Δap = 1.000000` over 129.641 s. **No measurable SCLK drift** at 1 ms / 130 s resolution
(bound ≈ 8×10⁻⁶). This does **not** support a drift-based explanation of the ~902.7 s clock.

### 4.6 Every base, read directly from the modem

The **modem** answers **all** bases 0..14 with `result = 0` — the daemon's base-0 EINVAL is a
**daemon-level** restriction, not a modem one.

| bases | value at modem uptime 653 s | reading |
|---|---|---|
| 0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 14 | 653 195 … 654 149 ms | **the same modem-uptime counter** (increments by the ~60–80 ms round-trip spacing) |
| 1, 12 | 1 474 793 829 450 … 856 | a second, ns-scaled value that also resets at a restart |

⚠ Open question: the modem appears to alias bases 2..11/13/14 to base 0. Whether the base TLV is
honoured for those is **not established** — only base 0 (uptime) and base 1/12 (ns-scale) are clearly
distinct.

## 5. A second, independent instrument: the ats files are a modem-restart beacon

`/data/time/ats_{1,2,13}` (8 bytes each) are rewritten **a few seconds after each modem fatal**:

| fatal | ats_1 mtime | write uptime | delay |
|---|---|---|---|
| 5352.250832 | 08:01:06 | 5360.03 | **+7.75 s** |
| 7162.168625 | 08:31:17 | ~7171.0 | **+8.80 s** |

The *values* are wall clocks (base 1/2/13), so they carry no modem uptime — but the **write event** is
a userspace confirmation of a modem restart. The daemon's `#time_genoff` **response latency also
spikes** to 9–10 ms (vs 1–3 ms) ~1.5–3.3 s after a fatal.

## 6. Achieved vs Expected

| Intent | Expected | Achieved | Status |
|---|---|---|---|
| Run `ats-probe` on the live arm | a QRTR/QMI TIME client producing `R_fatal` | `ats-probe` **cannot run** (no `CONFIG_QRTR`); built `ipc_probe` instead | ⚠ substituted |
| Reach the modem's QMI TIME from the AP | QRTR node 0 port 11 | **IPC Router (family 27), port 11** | ✅ |
| Read ATS_RTC (base 0) | `result = 0`, a monotone counter | **`result = 0`; ratio 1.000000 vs `CLOCK_BOOTTIME`** | ✅ |
| `R_fatal` with no borrowed offset | ~902.7 s | **902.646 s** | ✅ |
| ρ = Δrtc/Δap (SCLK drift) | ≠ 1 if drift is the mechanism | **1.000000** (bound ≈ 8×10⁻⁶) | ✅ (drift excluded) |
| condition vs always-on | reset ⇒ condition | **base 0 AND base 1 both reset** | ✅ condition |
| Read ATS_RTC via `time_daemon` | a client read | **EINVAL, 0/591** | ❌ negative (daemon-level) |
| Modem answers all bases | — | **0..14 all `result = 0`** | ✅ new |

## 7. What is VERIFIED and what is NOT

**VERIFIED (measured this session):**
* The `time_genoff` client protocol — RE'd from `libtime_genoff.so` and **matched byte-for-byte** by strace.
* The IPC Router transport, family **27**, `SOCK_DGRAM`, destination address captured verbatim.
* The QMI TIME wire format on Android: `0x0020` = SET, **`0x0021` = GET**; GET = 14 B.
* **base 0 = modem uptime in ms**, ratio **1.000000**, resets at a modem restart.
* **`R_fatal = 902.646 s`** for the 8972.078 fatal; reset latency 2.288 s.
* The daemon returns **EINVAL for base 0** (0/591) and caches at 1 Hz.
* The modem answers **all** bases 0..14.
* The ats-file write is a **modem-restart beacon** (+7.75 s / +8.80 s).

**NOT verified / open:**
* Whether the base TLV is honoured for bases 2..11/13/14 (they alias base 0).
* The meaning of the ns-scaled base 1 / base 12 value (≈1474.79 s at modem uptime 653 s).
* `R_fatal` at **n = 1** on this instrument. A second cycle was still pending at write time.
* Nothing here has been run on the **OpenWrt** arm; `deploy_and_collect.sh` (Doc 237) is written and
  ready but has never executed against a device.

## 8. Traps

1. **`ats-probe` is OpenWrt-only.** Android has no QRTR. Do not expect the OpenWrt binary to run there.
2. **`strace` exists on the Android arm** (`/system/xbin/strace`) — this is what made the transport
   recoverable. Use it before reverse-engineering a protocol.
3. **`pkill -f <pattern>` over `ssh` matches its own shell** — killing my own session. Use `pidof` +
   `kill`.
4. **A daemon-level error is not a modem-level error.** The daemon's base-0 EINVAL says nothing about
   the modem; the modem answers base 0 fine.
5. **Do not use the daemon for timing.** It caches genoff at ~1 Hz and returns a *wall clock*, never the
   modem's uptime.
6. **The daemon's genoff values are not raw modem values** — the daemon applies its own offset
   (base 1: daemon `1790755265890` ms vs modem `1474794077725` ns-scale).
7. **The AP fatal line is not the modem death instant.** The modem's epoch is ~2.3 s *after* the line.
8. **A gap in a sampled series is data.** The 3648 ms silence is what proves the modem was dead.
9. **`CLOCK_BOOTTIME` = 7** on this kernel; the probe's `ap_boot_ms` is a real, monotone stamp.
10. **Hand-decoding hex is a trap** — I mis-read `0x83C49` by hand and got a false 0.13× rate. Parse in
    code, never mentally.

## 9. SOP-compliance statement

* **Ground-truth first.** Every claim is anchored to a live measurement: strace bytes, `/proc/net/unix`,
  `dmesg` timestamps, and the probe's own output. No value is borrowed from a previous doc without
  saying so, and the one comparison (Doc 234's 902.7229 / 904.9544) is labelled as such.
* **Reverse-engineered before built.** The `time_genoff` struct and the QMI message ids were decoded
  from `libtime_genoff.so` (Thumb-2, `--triple=thumbv7-none-eabi`) **and then verified against a live
  strace** before any code was written.
* **Read-only.** The only QMI message ever emitted is `0x0021` (GENOFF_GET). `0x0020` (SET) is never
  built. The `time_genoff` client likewise only ever sends `op = 1` (GET).
* **Reversible.** The only state changed on the device was stopping/starting `time_daemon`
  (`setprop ctl.stop/start`), which init restores; verified back to `running`.
* **No destructive step taken.** Nothing was written to the modem, no partition touched, no
  `/dev/smdcntl*` write attempted.
* **Negative results recorded.** The daemon base-0 EINVAL and the `ats-probe`-cannot-run fact are
  reported as findings, not hidden.
* **Ledger + memory updated in the same session** (item 56).

## 10. Deployment

```sh
# build
clang --target=armv7a-linux-gnueabi -mcpu=cortex-a7 -mthumb \
      -nostdlib -static -ffreestanding -fno-builtin -fno-stack-protector \
      -O2 -Wl,-e,_start -o ipc_probe ipc_probe.c
# push
scp ipc_probe root@192.168.100.1:/data/local/tmp/ipc_probe
# run (5 Hz, base 0 only) — device-local capture
ssh root@192.168.100.1 'chmod 755 /data/local/tmp/ipc_probe;
  nohup /data/local/tmp/ipc_probe > /data/local/tmp/ipc.log 2>&1 &'
# fatal timestamps
ssh root@192.168.100.1 "dmesg | grep 'modem subsystem failure'"
```

## 11. Artifacts

`evidence/238_android_ipc_qmi_time/`
* `ipc_probe.c` — the direct IPC-Router QMI TIME probe (base 0 + base 1).
* `ipc_probe_fast.c` — 5 Hz, base 0 only (the fatal capture).
* `ipc_sweep.c` — one-shot base 0..14 sweep.
* `ats_rtc_arm.c`, `ats_all_arm.c` — the `time_genoff` daemon clients (the first instrument).
* `libtime_genoff.so` — the RE'd client library (md5 `699033249d496ffaf0914239254856f8`).
* `td_boot.strace` — the live strace of `time_daemon`'s startup (the QMI TIME exchange + AF_IB addressing).
