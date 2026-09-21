# Doc 172 — The time-daemon differential (Android vs OpenWrt), and what the modem's OWN clock says about the ~902 s fatal

**Question asked (2026-09-22):** *"anything observable for time daemon in android vs openwrt, modem timer error"*

**Answer in one line:** yes, and the differential is **not presence — both stacks run a daemon** — it is
**architecture, persistence and policy**; and the same daemon turned out to carry a *new instrument*
(the modem's own `ATS_RTC` uptime counter), which **independently confirms the ~900.5 s `sleepmgr`
band to 59 ms** and shows that **the daemon is not the lever for the fatal**.

---

## 1. Both stacks have a daemon — so the differential is not presence

| | Android (stock) | OpenWrt (deployed) |
| :-- | :-- | :-- |
| process | `/system/bin/time_daemon`, PID 243, **uid `system`** | `/usr/sbin/qcom-time-daemon`, procd, `START=65` |
| second process | `com.qualcomm.timeservice` (Java, PID 2019) | — |
| IPC | **local abstract socket `#time_genoff`** (Java ⇄ native) | — (single process) |
| modem link | QMI via `libqmi_cci` (`qmi_client_init`, `qmi_client_send_msg_sync`) | raw **QRTR** (`qrtr_open`, node 0 / port 11 / service 22) |
| also owns | `/dev/diag` | — |
| ATS bases touched | **1, 2 and 13** (per-base policy table) | **2 only** (reads 0 and 1 to compute the offset) |

Both are alive and doing work on the live device. The captured OpenWrt boot logged 15 periodic
refreshes; the Android side is pushing the wall clock continuously (§3).

**How to apply:** do not frame this as "Android has a time daemon and OpenWrt doesn't". That framing
would be wrong and would send the next investigation after a non-existent missing component.

---

## 2. Differential #1 — persistence: Android carries wall time across a reboot, OpenWrt cannot

### Measured (Android, live, 2026-09-22)

```
/data/time/ats_1   8 bytes   1790017629034      (rewritten; mtime moved 02:36 -> 02:39)
/data/time/ats_2   8 bytes   1790017629034      (identical to ats_1)
/data/time/ats_13  8 bytes   1790017629000      (= ats_1 truncated to a whole second)
```

These are **offsets**, and the quantity is the *host-side* one. Sampled three times, 4 s apart:

```
rtc=7576 sys=1790025205   ats_1 = 1790017629034 ms
     (sys - rtc) * 1000 = 1790017629000 ms   residual = 34 ms
rtc=7580 sys=1790025209   ats_1 = 1790017629034 ms
     (sys - rtc) * 1000 = 1790017629000 ms   residual = 34 ms
rtc=7584 sys=1790025213   ats_1 = 1790017629034 ms
     (sys - rtc) * 1000 = 1790017629000 ms   residual = 34 ms
```

`ats_1` is **constant**, `sys − rtc` is **constant**, and the residual is **constant at 34 ms** — a
one-time sub-second skew from the instant the file was written, not a growing error. So

> **`/data/time/ats_N` = (AP wall clock − AP RTC), in ms.**

*(Instrumentation trap: this device's `sh` does **32-bit** arithmetic and `ats_1 ≈ 1.79e12` overflows
it, silently yielding `−983733`. The first run of this probe did exactly that and reported a
meaningless residual. Use `awk`, as the committed script now does.)*

### Why that matters

The daemon's own strings show the mechanism:

```
ats_rtc_init            ats_bases_init        genoff_boot_tod_init
genoff_persistent_update                      genoff_pre_init / genoff_post_init
Daemon:%s: Time read from RTC -- MM/DD/YY HH:MM:SS%d/%d/%d %d:%d:%d
Daemon:Invalid RTC seconds = %ld
settimeofday            (imported)
```

So Android reads the free-running `qpnp_rtc` at boot, adds the persisted offset, and calls
`settimeofday()`. **That is how Android knows the approximate wall time before NITZ arrives.**

**OpenWrt has none of it.** The deployed daemon:

* never opens `/dev/rtc*`, never reads `since_epoch`, never calls `settimeofday`/`clock_settime`
  (verified by grep — zero hits across `src/*.c`);
* persists **nothing** — its only file is `/var/run/qcom-time-synced`, and `/var/run` is tmpfs,
  so it is gone on every reboot;
* the only socket it opens is `qrtr_open(0)` (`qcom-time-daemon.c:536`).

**Consequence (observable, and consistent with an existing memory):** on OpenWrt a device with no
network starts at the wrong time and stays there until NTP; Android starts approximately right
immediately. This is the `HMU05` "wall clock is wrong for the first ~60–70 s of a boot" quirk seen
from the other side.

**Not established:** the base-index → name mapping. `ats_1`/`ats_2` holding *identical* values is
consistent with base 1 = `ATS_TOD` and base 2 = `ATS_USER` being phase-locked — which is exactly what
the OpenWrt daemon independently measures on the modem side (§4: `TOD − RTC` constant to ±1 ms). But
**base 13 is outside the 0–2 set the OpenWrt daemon even has an enum for**, and I did not decode it.
Do not name it without a header or a disassembly.

---

## 3. Differential #2 — the push direction: Android pushes the AP clock, OpenWrt pushes a modem-derived offset

Android's `com.qualcomm.timeservice` reacts to the system broadcast and pushes the **AP wall clock**
into the modem:

```
09-22 02:39:51.013 D/TimeService( 2019): Received android.intent.action.TIME_SET intent. Current Time is 1790024991026
09-22 02:39:51.013 D/        ( 2019): TimeServiceNative: User Time to be set is 1790024991026
09-22 02:39:53.024 D/TimeService( 2019): Received android.intent.action.TIME_SET intent. Current Time is 1790024993034
```

The OpenWrt daemon instead computes the offset **entirely from the modem's own bases**
(`qcom-time-daemon.c:252-261`):

```c
query_modem_ats_base(sock, ATS_TOD, &tod_val);      /* base 1 */
query_modem_ats_base(sock, ATS_RTC, &rtc_val);      /* base 0 */
genoff = tod_val - rtc_val;                          /* -> ATS_USER, base 2 */
```

So the two stacks agree on *what to set* (`ATS_USER`) but derive it from different references. The
OpenWrt derivation is arguably the better one for a device with no RTC discipline — it cannot inject
a wrong host clock into the modem — but it is a **behavioural** difference, not a defect.

### The periodic-write policy — already corrected, and now falsified in the negative direction

Android is indication-driven: one `ATS_USER` SET at boot, then `poll(..., -1)`. OpenWrt's `-r N` adds
a write every N seconds. Doc 146 §9 changed the deployed init from `-r 60` to `-r 0` for parity.

The captured boot (§4) ran with **`-r 60`**, i.e. the *retracted* configuration, and it gives us the
missing negative control:

```
14 pre-fatal "Periodic ATS_USER refresh" events
14 pre-fatal TX 0x0020 (QMI_TIME_GENOFF_SET_REQ) transactions
last one: 15:54:54  -> "REFRESH ATS_USER TRANSACTION VERIFIED!  offset=1473867647648 ms"
fatal:    15:55:49   (55 s later)
```

> **The daemon wrote `ATS_USER` to the modem 14 times, the last one succeeding 55 s before the fatal,
> and the fatal fired anyway.**

That is the retracted "900 s SCLK watchdog needs the daemon's keepalive" premise, falsified from the
side it was originally argued from. It does not by itself prove `-r 0` is better (Doc 146 §9's
pre-registered criterion is fatal *rate* over a long soak, never "the fatal landed at t = X", and the
`ab_test_qcom_time_r0_telemetry.csv` boot reached AP 1168.8 s with `modem_state=connected` — **one
boot, n = 1, no evidence either way**).

---

## 4. ★ The "modem timer error" — `ATS_RTC` is the modem's own uptime counter, and it confirms the band

This is the part that was not previously exploited.

Once a minute the daemon logs a line in which **both operands are read from the modem over QMI**:

```
Aligned offset = TOD (<tod_ms>) - RTC (<rtc_ms>) = <off_ms> ms
```

`TOD` is base 1 = `ATS_TOD`; `RTC` is base 0 = `ATS_RTC`. So **`ATS_RTC` is the modem's own uptime
counter, exposed to the AP**, and the daemon was reading it every 60 s for 13 minutes. Nobody had used
it as an instrument.

### 4.1 It behaves as a counter

```
n = 14 samples, 15:41:54 -> 15:54:54
TOD - RTC  range 1473867647648 .. 1473867647649 ms   (spread 1 ms)
over 780 s of log stamp:  RTC +780028 ms,  TOD +780028 ms   (difference 0 ms)
```

`TOD` and `RTC` advance at *exactly* the same rate and the offset never moves — so `ATS_RTC` is a
valid uptime counter at sub-second resolution, and the modem is **not** disciplining `TOD` to the
network during this window (it is slaved to the modem's own clock).

**Do not quote ppm from this file.** The log stamps are integer seconds, so "780 s vs 780028 ms" is
quantization, not drift. The correct statement is: *the offset is constant to ±1 ms over 13 minutes*,
which is all the instrument is being used for.

### 4.2 The fatal fired at 900.4 s of the modem's own uptime

Two routes from `ATS_RTC` alone, using different sample pairs:

```
route A (from the NEW epoch, 15:56:53 sample - 62.922 s):  old RTC at fatal = 900.441 s
route B (from the OLD epoch, 15:41:54 sample - 65.413 s):  old RTC at fatal = 900.413 s
                                         A vs B agree to:  0.028 s
```

Cross-checked against the **AP-derived** anchor, which shares no clock with either (Doc 165: this
boot's ring had wrapped past the modem's initial boot, so the offset is borrowed from the previous
boot, `912.915 − 900.5 = 12.415 s`):

```
AP fatal 912.900514 s  -  borrowed 12.415 s  =  900.486 s of modem uptime
QMI-derived (mean of A and B)                =  900.427 s
                                     AGREEMENT:  0.059 s
```

**Two instruments that share no clock agree to 59 ms.**

Honest bound: the log stamps are integer seconds, so the interval is
**`[899.4, 901.4) s`**; the point estimates above are the mid-range values.

### 4.3 Where it lands

```
Doc 164 short band (all lte_ml1_sleepmgr_stm.c:4054, n=3):  900.5, 900.662, 900.965
this boot:  900.427 s, signature lte_ml1_sleepmgr_stm.c:4054     -> n=4
band spread before 0.465 s, now 0.538 s
```

It extends the band's lower edge by 0.073 s and keeps the Doc 164 rule intact — **the short band is
still always `sleepmgr`**. The new thing is that this sample was obtained **without borrowing an
offset from a previous boot**, which is what Doc 165 had to do and flagged as the reason the long
band could not be scored.

### 4.4 A wall-clock-free measurement: the epoch gap is exactly 902.470 s

`offset = TOD − RTC` is constant within a modem life, so `offset_old` **is** the `TOD` value at the
old modem's `RTC = 0`, and `offset_new` is the `TOD` at the new modem's `RTC = 0`. Both are read from
the modem. Their difference is therefore the elapsed time between the two modem epochs, with **no AP
clock anywhere in the arithmetic**:

```
offset_old = 1473867647648 ms   (TOD at old RTC = 0)
offset_new = 1473868550118 ms   (TOD at new RTC = 0)
epoch gap  =  902470 ms  =  902.470 s
```

Consistency: `902.470 − 900.427 = 2.043 s` of post-fatal tail, against the kernel's
`fatal 912.900514 → "is now up" 913.588120 = 0.688 s` plus the RTC's own start latency — consistent
inside the 1 s stamp quantization. (The old modem's `ATS_RTC` stops with the modem; the gap is
*lifetime-to-fatal* plus *tail*, **not** a period, so do not read 902.470 s as a new period sample.)

---

## 5. What the daemon log says about the fatal: nothing before it, a lot after it

**Before:** **zero** timeouts, failures or retries anywhere in the capture up to the fatal
(`grep -c` on the pre-fatal region = 0). The QMI TIME channel was healthy to the last exchange, 55 s
before. **The daemon shows no precursor — it is not a trigger.**

**After** — the daemon is an unusually clean SSR detector and modem-readiness probe:

```
15:55:49  fatal; daemon: "Modem SSR / Disconnect detected (node=0 port=11). Resetting state machine."
15:55:50  kernel: "remote processor 4080000.remoteproc is now up"
15:55:50  daemon: "Discovered QMI TIME service: node=0 port=11 service=22"
15:55:53  3 x "Timeout (3000 ms) waiting for response to 0x0021" (txn 49, 50, 51)
15:55:53  txn 55 answers -> INITIAL ATS_USER set succeeds (AP-host fallback, TOD not yet locked)
15:56:25  ATS_TOD_UPDATE_IND 0x0027 arrives -> re-locks to the network TOD
```

Two observations worth keeping:

* **The QRTR service is *discoverable* ~1 s after the fatal but not *responsive* for ~4 s.** That is a
  distinct readiness signal from the bam-dmux `pc_state` one, and it is the same shape as the
  "failed first probe" cost in the userspace bearer rebuild — the port exists before it answers.
* The daemon **re-synced itself** (`INITIAL ATS_USER TRANSACTION VERIFIED`) with no operator action.
  Nothing here needs a fix.

---

## 6. What this changes, and what it does not

**Changes:**

* A **new instrument** exists and is already being logged for free: `ATS_RTC` = modem uptime, read
  over QMI TIME, independent of the AP clock. It converts any fatal's AP timestamp into modem uptime
  **without** borrowing an offset — which removes the exact limitation Doc 165 had to work around.
* The **epoch gap** is measurable to the millisecond with no wall clock.
* The `-r 60` configuration is now a **negative control**: 14 modem-visible `ATS_USER` writes, and the
  fatal fired anyway.
* Android's **RTC + persisted-offset** mechanism is identified, and OpenWrt provably lacks it.

**Does not change:**

* The ~902 s fatal is still unexplained and unfixed.
* Nothing here touches the AP-hang work (Docs 169/170) or the pc-ack work (§8.21).
* The `ATS_RTC` instrument is only available **while the daemon runs with a periodic refresh**, i.e.
  with `-r > 0`. **With the deployed `-r 0` there are no periodic samples**, so this instrument goes
  dark. If it is wanted, add a read-only `GET` path (no `0x0020` write) rather than restoring `-r`.

---

## 7. Open items

1. **Make the instrument deliberate.** A read-only variant that polls `0x0021` for bases 0 and 1 every
   N s and logs `modem_uptime_s` would give every future boot the modem-uptime anchor for free, with
   no modem-visible write. Cheap; **do not** do it by restoring `-r`.
2. **Decode base 13.** One header or one disassembly of `ats_bases_init()` settles it.
3. **Confirm the RTC-carry on OpenWrt.** Is the missing `settimeofday`/RTC path a deliberate choice or
   an omission? It is a real user-visible difference but has no demonstrated link to modem stability,
   so it should not be changed under the banner of a stability fix.
4. **`-r 0` vs `-r 60`** remains judged by fatal *rate* over a long soak (Doc 146 §9), n = 1 today.

---

## 8. SOP-compliance statement

Per the standing rule that every porting/research doc names the SOP steps taken and skipped:

* **Taken:** comparative verification against stock Android ground truth (the live device, not a
  summary); read the *definition* rather than the name (`qcom-time-daemon.c:252-261` for the offset
  derivation, `:586` for the refresh gate); instrument both sides of the hand-off (the daemon's own
  log on OpenWrt, `logcat` + `strings` + `/data/time` on Android); measurement hygiene — the
  OpenWrt figures come from a **frozen** capture (`scratch/qmi900/qmi_syslog.txt`, 2026-09-19, not a
  live file), and the quantization limit is stated rather than hidden; the numbers are reproducible
  from the committed `ats_series.txt` via `score_time_daemon.py`.
* **Skipped, with reason:** no firmware or kernel change was made or proposed as a fix (the standing
  directive is no binary patching of the modem); no A/B was run (the device is currently booted into
  Android, and §5's pre-registered criterion is rate over a soak, which n = 1 cannot serve); the
  `-r 0` vs `-r 60` question was **not** re-opened.
* **Explicitly not claimed:** that the time daemon causes or prevents the fatal; that base 13 is any
  particular base; that `ats_N` is exactly `wall − RTC` rather than that quantity plus a one-time
  constant (the constant is measured at 34 ms and is stated as such).

**Reproduce:**

```sh
cd "Docs/Modem Stability/evidence/172_time_daemon_differential"
python3 score_time_daemon.py          # OpenWrt side, from the committed ats_series.txt
sh probe_android_time.sh              # Android side, against the live device (read-only)
```

Artifacts: `ats_series.txt`, `score_time_daemon.py`, `probe_android_time.sh`,
`android_probe_output.txt`.
