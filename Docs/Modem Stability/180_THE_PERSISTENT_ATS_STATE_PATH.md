# 180 — THE PERSISTENT ATS STATE PATH

**Date:** 2026-09-22
**Boot:** n/a — this is an implementation doc, not a measurement of a live boot
**Package:** `packages/qcom-time-daemon/` (tracked source of truth), `PKG_RELEASE 2 → 3`
**Evidence:** `evidence/180_ats_persistence/` — `test_ats_persist.c`,
`build_and_run_test_ats_persist.sh`, `verify_output.txt`

---

## 1. SOP compliance

| SOP step | done? |
| :-- | :-- |
| Comparative protocol against the stock-HMU05 ground truth | **YES, by source** — the parity target is stock Android's `time_daemon` / `/data/time/ats_<N>` persistence, established in Doc 172 from the on-device binaries. This doc changes no baseband and no kernel driver. |
| Pre-register the prediction **before** the data exists | **n/a** — no live measurement is claimed. The test's expectations are written into the test before it runs, and the harness is committed with its output. |
| Freeze the capture, hash it, score the frozen copy | **n/a** — no capture. `verify_output.txt` is the frozen output of the committed harness. |
| Keep a control | **YES** — every branch has a negative control: an absent file, a corrupt file, a truncated file, a wrong-magic file, a wrong-base file, a future-version file, an implausible offset, and `-P none`. The "writes" cases are each paired with a "does not write" case. |
| Verify the instrument before resting a claim on it | **YES** — the test drives the **shipped** source (it `#include`s `qcom-time-daemon.c` with `main()` renamed), linked **static** for aarch64 and run **natively**, so there is no emulator between the test and the code. |
| State what is *not* established | **YES** — §8 |

---

## 2. The gap this closes

Doc 172 established the differential:

| | stock Android | this port, before this change |
| :-- | :-- | :-- |
| ATS persistence | `/data/time/ats_<N>` — the relation between the AP wall clock and the modem's RTC counter, per base | **nothing** |
| Only state written | — | `/var/run/qcom-time-synced`, which is **tmpfs** and dies with the boot |

So the port had no way to answer, from the device itself, the question the project
has repeatedly had to answer from the **host**: *did the modem restart, or did only
the AP?* `reference_hmu05_device_quirks.md` records the rule — "an AP uptime
decrease proves the dongle restarted, **NOT** that it restarted itself" — and the
only instrument was `journalctl -k` on the host.

It also had a latent defect. The NITZ-not-yet-locked fallback computed

```c
uint64_t gps_time_ms = ap_time_ms - GPS_EPOCH_OFFSET_MS;   /* uint64 */
```

with no floor. With an unset AP clock (`ap_time_ms` ≈ 0) this **underflows** to
≈ **1.8446e19 ms**, and the following `gps_time_ms >= rtc_val` test is then trivially
true, so the daemon would have sent that value to the modem as the ATS_USER offset.
A wrong offset is worse than none.

---

## 3. What was added

One file on the overlay — `/etc/qcom-time/ats` by default, overridable with
`-P <path>` or disabled with `-P none`:

```
magic=qcom-ats-state
version=1
base=2
offset_ms=1444000000000      <- the offset the modem ACCEPTED, ms since the GPS epoch
rtc_ms=5000000               <- ATS_RTC (modem uptime) at that moment
wall_ms=1758542400000        <- AP wall clock at that moment
delta_ms=1758537400000       <- wall_ms - rtc_ms; the relation Android persists
synced_at=1758542400
```

Text, not Android's binary layout: the file is ours alone — Android never reads it —
and this project reads its evidence with `grep`.

It is written **atomically** (temp file + `fsync` + `rename` + directory `fsync`), so
a power cut leaves either the old record or the new one and never a half-written line.

### 3.1 Two uses, in order of value

**(a) Reset vs reboot, with no AP clock in the arithmetic.** `ATS_RTC` is the modem's
own uptime counter and only moves forwards while the modem runs. At the first
handshake after a boot the daemon already reads it, so the comparison is free:

* `rtc_now < rtc_recorded` → **the modem restarted** (and the difference is the modem
  time lost);
* `rtc_now > rtc_recorded` → **the modem survived**, and the difference is how much
  modem time elapsed across the AP's downtime.

**(b) Restoring the offset when the AP wall clock is manifestly unusable** — see §4.

### 3.2 When the record is rewritten

An **INITIAL** set (boot, or the first handshake after an SSR) always writes. A
**REFRESH** writes only when the offset actually changed: re-deriving the same number
tells a future boot nothing new, and every write costs a flash erase. With the deployed
`-r 0` that is one write per boot, plus one per SSR.

---

## 4. The restore path, and why it is conservative

Two guards, both of which must pass before the persisted offset may replace the AP
clock's:

1. `ap_wall_ms >= 2020-01-01T00:00:00Z`. A wall clock below that is not a clock.
2. `ap_wall_ms >= recorded_wall_ms`. **An AP reboot cannot move the wall clock
   backwards.**

If the AP clock fails either guard the daemon uses the persisted offset and logs a
`LOG_WARNING` naming both numbers. If it fails a guard **and** there is no record, the
daemon **refuses to send anything** and returns an error, so the handshake retries
rather than poisoning the modem.

A **plausible** clock is never overridden. A legitimate NTP step and a broken boot
clock are indistinguishable in a single sample, so preferring a stale offset over a
good clock would be a coin flip; the code does not take it. The consequence is that
the −3h44m boot-clock case recorded in `reference_hmu05_device_quirks.md` is caught
only when it is also *behind* the recorded wall time.

The offset is also validated **at load**: a value below the same 2020 floor, or a
negative one, makes the whole record be rejected. That matters because `offset_ms` is
the value that gets sent to the modem.

---

## 5. Wire neutrality

This path adds **no QMI message**. It is populated from the `ATS_RTC` / `ATS_TOD`
values `send_ats_user_transaction()` already reads, and it is written to AP flash
only. The message sizes are unchanged — `0x0021` GET at 14 bytes, `0x0020` SET at
25 — so the read-only property Doc 173 established is untouched, and Doc 146 §9's
"no AP-initiated modem traffic while idle" experiment is not confounded.

`-P` is passed **explicitly** by `/etc/init.d/qcom-time-daemon` (as `-r 0` already is)
so the path is visible in `ps`.

---

## 6. Verification

`evidence/180_ats_persistence/build_and_run_test_ats_persist.sh` builds the test and
the daemon **static for aarch64** and runs them **natively** (the host is aarch64, so
no emulation is in the loop), then smoke-tests the real binary's `-P` wiring.

```
RESULT: PASS  54 checks, 0 failed
```

Coverage, all with negative controls: save/load round-trip of all six fields; the file
is self-describing and ends with a newline; a **signed** `delta_ms` survives; **eight**
malformed-record classes degrade to "no record" rather than to a wrong offset; an
absent file; parent-directory creation; `-P none` disables read *and* write; the
verdict reports **RESTARTED** / **SURVIVED** with the right numbers and never the
wrong one; the verdict is one-shot; `rtc_ms = 0` yields **UNKNOWN** rather than a
guess; both AP-clock guards; the restore candidate; and the rewrite policy
(INITIAL always, unchanged REFRESH never, changed REFRESH yes).

The smoke test drives the shipped binary end to end and shows all three `-P` states
(config line, record loaded, record absent, `-P none`). The build is clean under
`-Wall -Wextra`; the only warnings are libqrtr's pre-existing `-Wsign-compare` ones in
`qmi.c`.

---

## 7. What this changes for the investigation

It does not touch the fatal's causal path — it is AP-local bookkeeping. What it buys is
an **instrument**: from the next boot onward, the device can state whether the modem
restarted across an AP reboot without borrowing the host's clock or the host's logs.
That is directly relevant to the standing caution that an AP uptime decrease proves
the dongle restarted but not that it restarted itself.

It also removes a latent way to hand the modem a ~1.8e19 ms offset.

---

## 8. What is NOT established

* **It has never run against a real modem.** Every check above is a pure function of a
  file on disk and two integers. The two live paths — the verdict on a real SSR, and
  the restore actually being used — are untested against hardware.
* **The reset-vs-reboot verdict has no live control.** No boot has yet produced a
  record and then been scored against an independently-known modem reset.
* **The flash-wear claim is an estimate, not a measurement.** One write per boot plus
  one per SSR is bounded and small, but it has not been counted over a long soak.
* **The `−3h44m` boot-clock case is not reliably caught** — see §4.
* **This is not a fix for the fatal.** The ~903.675 s clock and the latched offset of
  Doc 179 are untouched.

---

## 9. Next actions

1. Deploy and confirm the record appears at `/etc/qcom-time/ats` after the first boot,
   and that its `rtc_ms` matches the `-p` telemetry line.
2. At the next SSR, confirm the verdict line reports **MODEM RESTARTED** with a
   plausible lost-time figure — and cross-check it against the host's `journalctl -k`
   for that window. That is the missing control for §8's second bullet.
3. At the next **AP reboot with the modem up**, confirm **MODEM SURVIVED** and record
   the elapsed-modem-time figure; it is the first wall-clock-free measurement of the
   AP's downtime in this project.
