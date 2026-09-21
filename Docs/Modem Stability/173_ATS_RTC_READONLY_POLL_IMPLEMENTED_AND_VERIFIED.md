# Doc 173 — The ATS_RTC read-only poll: implemented and verified

**Asked (2026-09-22):** *"Verify ATS_RTC read-only poll"* — i.e. Doc 172 §7 item 1: give OpenWrt a
periodic **read-only** poll so the modem's own uptime counter (`ATS_RTC`, QMI TIME base 0) stops going
dark under the deployed `-r 0`.

**Result:** implemented, and verified three independent ways with three negative controls.
**It is left DISABLED by default on purpose** (§6). **Not verified: it has never run against the real
modem** — the HMU05 is currently booted into Android 4.4.4, so no live deployment was possible. §5
states exactly what that leaves open.

---

## 1. What was already there — and a dead flag

The daemon already had a periodic-read path, `validate_periodic_time_get()` (`:354`), and a flag for it:

```c
static bool enable_periodic_get = false;      /* :64  */
...
case 'p':
        /* periodic get disabled for stability */
        enable_periodic_get = false;          /* :507  <-- assigns the flag its own default */
        break;
```

**`-p` was a no-op.** It assigned the flag the value it already had, so `validate_periodic_time_get()`
was **unreachable in every build**, and the comment in the main loop — *"Can be explicitly enabled with
'-p' flag for diagnostic testing"* — was false. There was no way to turn the read path on.

So this was not a "add a feature" task. It was: **fix the flag, point it at base 0, and prove the
thing it turns on cannot write.**

---

## 2. The read-only property, stated precisely

The claim to verify is *not* "we were careful". It is:

> The only message the poll can send is `QMI_TIME_GENOFF_GET_REQ` (0x0021), whose wire format carries a
> base index and **nothing else**. There is no field in it capable of carrying a value.

That is a claim about a byte layout, so it is checkable three ways — the descriptor, the wire, and the
code — and each is checked below with a negative control.

---

## 3. The change (4 edits, one file + one init)

| where | what |
| :-- | :-- |
| `qcom-time-daemon.c` `case 'p'` | `enable_periodic_get = false` → **`true`**, with a comment recording that it was a no-op |
| `qcom-time-daemon.c` | **new** `log_modem_uptime()` — `GET` of `ATS_RTC` (base 0) + `ATS_TOD` (base 1), one log line, **no state effects** |
| `qcom-time-daemon.c` main loop | the `-p` branch now calls `validate_periodic_time_get()` **and** `log_modem_uptime()` |
| `qcom-time-daemon.c` | startup line + usage text made accurate |
| `qcom-time-daemon.init` | a **commented-out** `-p` with the rationale and the log line to grep for |

`log_modem_uptime()` deliberately **cannot revoke sync**. `validate_periodic_time_get()` can (3
consecutive failures ⇒ `STATE_FAILED`), and that is a defensible liveness watchdog — but a *telemetry*
path that can knock the daemon out of `STATE_SYNCHRONIZED` would be a surprising side effect of asking
a question, so the two are kept separate.

The log line:

```
[QMI-TIME] MODEM-UPTIME rtc_ms=<n> tod_ms=<n> tod_minus_rtc_ms=<n>
```

`rtc_ms` is directly the modem's uptime in milliseconds — the Doc 172 instrument.

---

## 4. The verification

Artifacts: `Docs/Modem Stability/evidence/173_ats_rtc_readonly_poll/`.
Reproduce: `sh build_and_run_test_encode.sh` (also `verify_readonly_poll.py` in the Doc 172 dir for
parts 1 and 2 below).

### 4a. The encoder reproduces the captured wire — **run, not argued**

The daemon does not hand-roll QMI. It calls libqrtr's `qmi_encode_message()` and passes the **return
value** to `qrtr_sendto()`, logging it as `(len=%zd)`. So the `len=` in the captured syslog *is* that
function's return value — which means calling the same function with the same descriptors reproduces
the deployed format exactly. It was compiled (static aarch64, libqrtr built in from source) and run:

```
== what the READ-ONLY poll sends (ATS_RTC, base 0) ==
  GET  (14 bytes): 00 2f 00 21 00 07 00 01 04 00 00 00 00 00
         header: type=0x00 txn=47 msg=0x0021 msg_len=7
         TLV type=0x01 len=4  <- `base` (u32) = 0
  qmi_encode_message() -> 14   [captured wire length: 14]

== what the periodic WRITE sends (ATS_USER, base 2) ==
  SET  (25 bytes): 00 30 00 20 00 12 00 01 04 00 02 00 00 00 02 08 00 a0 8a 5b 29 57 01 00 00
         header: type=0x00 txn=48 msg=0x0020 msg_len=18
         TLV type=0x01 len=4  <- `base` (u32) = 2
         TLV type=0x02 len=8  <- `offset` (u64)  *** THIS IS THE WRITE ***
  qmi_encode_message() -> 25   [captured wire length: 25]

difference = 11 bytes = TLV header (3) + u64 offset (8)
RESULT: PASS -- encoder reproduces the captured wire lengths
```

**The GET message is 14 bytes and its last 7 are `01 04 00 00 00 00 00`. There is no room in it for a
value.** The write is the same message plus one TLV.

*(The 8 offset bytes decode as `a0 8a 5b 29 57 01 00 00` → `0x00000157295b8aa0` = `1473867647648`,
the exact value the `-r 60` capture wrote — so the test is encoding the real message, not a lookalike.)*

### 4b. The wire agrees, across every message ever sent

From the 2026-09-19 capture, every transmitted QMI TIME message:

| message | count | lengths observed |
| :-- | --: | :-- |
| `0x0021` GET | 38 | **`{14}` — one distinct length** |
| `0x0020` SET | 19 | **`{25}` — one distinct length** |

`verify_readonly_poll.py` derives 14 and 25 from the descriptor tables and asserts the wire matches.
**No GET ever deviated**, so no GET ever carried a value.

### 4c. The base-0 read path is already proven against this modem

`log_modem_uptime()` is a *new caller* of an **old, exercised** helper. `query_modem_ats_base(sock,
ATS_RTC, …)` is exactly what `send_ats_user_transaction()` calls, and it only writes `*val_out` when
`ret == 0 && result == SUCCESS && error == NONE`. The capture printed an `RTC (<n>)` value **15 times,
every one non-zero, and `RTC (0)` zero times** — so **15 successful `GET(base 0)` transactions are on
record**, plus 38 GET TX / 35 RX / 0 failures overall.

The code path to the modem is not new. Only the caller is.

### 4d. The daemon runs, and `-p` toggles only the read

```
--- with -p (poll ENABLED, write must stay DISABLED) ---
[QMI-TIME] Pure software modem stability mode active. Periodic ATS_USER refresh: DISABLED (interval=0s)
[QMI-TIME] Read-only modem-uptime poll (ATS_RTC base 0 + ATS_TOD base 1, 0x0021 GET only): ENABLED (interval=60s)
--- without -p (deployed default) ---
[QMI-TIME] Pure software modem stability mode active. Periodic ATS_USER refresh: DISABLED (interval=0s)
[QMI-TIME] Read-only modem-uptime poll (ATS_RTC base 0 + ATS_TOD base 1, 0x0021 GET only): DISABLED (interval=60s)
```

**`-p` turns the read poll on and leaves the write `DISABLED`.** The binary was run on an aarch64 host
**natively** (static musl, no emulation); the script falls back to `qemu-aarch64` elsewhere.

Also verified: `gcc -O2 -Wall -Wextra` clean, both objects and the full link, exit 0, no warnings.

### 4e. Negative controls — the checks can fail

| control | injection | outcome |
| :-- | :-- | :-- |
| **A** | add a `send_ats_user_transaction()` call inside `log_modem_uptime()` | **FAIL** — "contains no reference to the write transaction function" |
| **B** | rewrite one `TX 0x0021` line to `len=25` in the wire log | **FAIL** — "EVERY 0x0021 GET on the wire is exactly 14 bytes" |
| **C** | give `time_genoff_get_req_ei` a second TLV (the 8-byte offset) | **FAIL** — encoder returns 25, "lengths do not match the capture" |

Control C is the important one: it shows the encoder test is measuring the *descriptor*, not
re-asserting a constant. Each check was confirmed to pass on the real source and fail under its
injection.

---

## 5. What is NOT verified — read this before relying on it

1. **`log_modem_uptime()` has never executed against a real modem.** The device is on Android 4.4.4.
   What is proven is that *when* it runs it can only send a 14-byte base-indexed GET, and that the same
   helper + base already produced 15 successful reads on this modem. The new **caller** is unexercised.
2. **The 60 s cadence's effect on power/autosuspend is unmeasured.** A GET still holds the QRTR link
   awake for its round trip. It cannot *reset* the BAM-DMUX autosuspend timer the way the `0x0020`
   write did, but "smaller" is not "zero" and has not been measured.
3. **No live OpenWrt boot was tested**, so the init script's commented line is untested as a
   procd invocation.
4. **`validate_periodic_time_get()`'s sync-revoking behaviour is unchanged and also now reachable** for
   the first time. Its 3-failure threshold at a 60 s interval should tolerate a ~4 s SSR, but that is
   reasoning, not a measurement.
5. The Android AP-side differential from Doc 172 is untouched — this doc is OpenWrt-only.

---

## 6. Why it stays OFF by default

**Doc 146 §9's experiment is still running.** It is judged by fatal **rate** over a long soak, and its
deployed configuration is deliberately *no AP-initiated modem traffic while idle* — that is what
`-r 0` and the keepalive removal are for. Switching on a new periodic AP→modem exchange mid-experiment
would confound the very thing being measured.

So the poll is opt-in, and the init script carries the rationale next to a commented-out line. To use
it for a soak:

```sh
sed -i 's|^#procd_append_param command -p|procd_append_param command -p|' \
    /etc/init.d/qcom-time-daemon
/etc/init.d/qcom-time-daemon restart
logread -e MODEM-UPTIME
```

---

## 7. SOP-compliance statement

* **Taken:** read the *definition* rather than the name (found `-p` was a no-op only by reading the
  assignment, not the comment); verified the **mechanism** rather than the call graph — the encoder
  test runs the real libqrtr function and compares against the deployed wire, and the base-0 path is
  evidenced by 15 non-zero values in a frozen capture, not by "the function looks right"; **negative
  controls for every check** (A/B/C), because a verifier that cannot fail is worthless; measurement
  hygiene — the wire counts come from the **frozen** 2026-09-19 capture, and the static binary was run
  **natively** so no emulation sits between the test and its result.
* **Skipped, with reason:** no live deployment (device on Android 4.4.4); the poll was **not** enabled
  in the deployed init (would confound Doc 146 §9); no firmware or kernel change (standing directive).
* **Explicitly not claimed:** that the poll has run on a modem; that it is free; that
  `validate_periodic_time_get()`'s new reachability is safe; that enabling it changes the fatal rate.

**Reproduce:**

```sh
cd "Docs/Modem Stability/evidence/173_ats_rtc_readonly_poll"
sh build_and_run_test_encode.sh            # encoder test + daemon -p smoke test
cd ../172_time_daemon_differential
python3 verify_readonly_poll.py            # descriptor + wire invariants
```

Artifacts: `test_encode.c`, `build_and_run_test_encode.sh`, `verify_output.txt`; plus
`../172_time_daemon_differential/{verify_readonly_poll.py,qmi_tx_lengths.txt}`.
