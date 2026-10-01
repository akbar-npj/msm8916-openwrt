# Doc 237 — `ats-probe`: reading the modem's own uptime over QMI, stamped with the AP clock

**Date:** 2026-09-30
**Device:** none — built and verified offline; **not yet run against a real modem**
**Subject:** a new standalone, read-only instrument: `packages/ats-probe/`
**Ledger:** issued under `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md` — **item 55**
**Status:** RESULTS (offline instrument built and verified; the measurement it enables is NOT yet taken)
**Answers:** the ledger §43.26 open item — *"resolving the period's numeric structure needs the modem's
own clock at the moment of the crash, i.e. the `ATS_RTC` instrument read alongside the AP stamp — not
more AP-side timestamps."*
**Continues:** Doc 172 (the instrument), Doc 173 (the read-only poll, implemented but left off), Doc 177
(the invariant is an AP-observed clock), Doc 233 (the period is not a stored constant).

---

## 1. Why this exists

The ~902.7 s fatal is a **deterministic beat of the modem's clock**: nine consecutive fatals agreed to
**9.7 ms total spread**, and the sixth was predicted to **0.316 ms** (ledger §43.25–43.28). That
determinism has to be explained by one of three mechanisms, and **every AP-side timestamp in the corpus
shares one clock**, so none of them can separate the three:

| mechanism | what it predicts |
| :-- | :-- |
| a **COUNTER** | the beat is an integer count at some rate; it wraps |
| an always-on **CLOCK** | the reference keeps running while the modem is down (Doc 177's model) |
| a **CONDITION** | the beat is met on a modem-local counter; the value at the fatal is constant |

The one instrument that sees the modem's side is `ATS_RTC` (QMI TIME service 22, base 0) — the modem's
own uptime counter in milliseconds, and the only quantity in this project that measures modem uptime
**with no borrowed AP-side offset** (Doc 172 §4). Doc 172 exploited it once, from a `-r 60` capture;
Doc 173 built a read-only poll for it but left it **off**, gated on `STATE_SYNCHRONIZED`.

That gate is the problem. The daemon's poll goes dark **exactly at the SSR** — the moment under study.
This doc adds a tool with no state machine at all.

---

## 2. What it does

`ats-probe` opens QRTR, finds QMI TIME (service 22), and every N ms sends **one 14-byte
`QMI_TIME_GENOFF_GET_REQ` (0x0021)** for base 0 (`ATS_RTC`) and one for base 1 (`ATS_TOD`), stamping
each round trip with the AP's monotonic clock:

```
# ATS-PROBE v1 interval_ms=1000 service=22 started_wall_ms=1790753399827
ATS-PROBE ap_boot_ms=83012 ap_mono_ms=83012 rtc_ms=82997 tod_ms=1473876712 \
          tod_minus_rtc_ms=1473793715 rt_rtc_ms=2 rt_tod_ms=2 wall_ms=1790753481201
# ATS-PROBE-EVENT what=ssr ap_boot_ms=84115
# ATS-PROBE-EVENT what=discovered node=0 port=11 service=22 ap_boot_ms=85640
```

* **`ap_boot_ms` is `CLOCK_BOOTTIME`** — the clock `/proc/uptime` reports, and the one Doc 177 matched
  against `dmesg` to sub-ms on this no-suspend device. **This is the AP stamp.**
* **`ap_mono_ms` is `CLOCK_MONOTONIC`**, logged as a validity check: on a device that never suspends the
  two are equal, and if they ever diverge the log shows it rather than silently corrupting the
  correlation.
* **`rt_rtc_ms` / `rt_tod_ms` bracket each round trip.** The true sample instant is in
  `[ap_boot_ms, ap_boot_ms + rt_*]`; use the midpoint to halve the error.
* **`wall_ms` is informational only.** It steps under NTP/`settimeofday` and must never be the anchor —
  it is there so a human can line the file up against `logread`.

**It cannot write to the modem.** The only message it can send is the 0x0021 GET, whose descriptor
(`qmi_time.c`: `time_genoff_get_req_ei`) declares exactly one TLV: type 0x01, 4 bytes, the base index.
There is no field capable of carrying a value. The 0x0020 SET (25 bytes = the same message plus an
8-byte offset TLV) is never built. §5 verifies this at the descriptor, the source and the encoder.

**It survives an SSR by construction.** No state machine: it watches for `QRTR_TYPE_DEL_SERVER`,
re-issues `qrtr_new_lookup`, and resumes the instant the modem's time service reappears.

---

## 3. The three measurements the paired series enables

Let sample *i* be `(A_i, R_i)` = (AP `CLOCK_BOOTTIME` ms, modem `ATS_RTC` ms). Within one modem life the
two tick 1:1 and `R_i − A_i` is constant to ~1 ms (Doc 172 §4.1).

| # | quantity | what it settles |
| :-- | :-- | :-- |
| 1 | **modem uptime at the fatal** `R_fatal = R_i + (A_fatal − A_i)` | converts a `dmesg` fatal stamp to modem uptime **with no borrowed offset** — removing the exact limitation Doc 165 had to work around |
| 2 | **rate ratio** `ρ = (R_j − R_i)/(A_j − A_i)` over a long window | the **direct SCLK-drift test** nobody has run. `ρ = 1.000000` within a few ppm ⇒ the modem's uptime clock tracks the AP (same XO/AON domain) ⇒ **not** drift; a tens-of-ppm deviation ⇒ *that is* the drift, and its magnitude tests the SCLK hypothesis |
| 3 | **epoch gap** `offset_new − offset_old` (Doc 172 §4.4) | a wall-clock-free period sample every fatal, no AP clock anywhere in the arithmetic |

## 4. The discriminator

Sample through 2–3 consecutive fatals and look at `R_fatal` across them:

| `R_fatal` behaviour | verdict |
| :-- | :-- |
| **constant** (to a few ms) | a **CONDITION** was met on a modem-local counter; the constant *is* the count to hunt, and `ρ` converts it to cycles at any candidate rate |
| **tracks the SSR recovery time** | the reference is an always-on **CLOCK** (Doc 177's model); modem uptime is the artifact |
| **monotone drift / wrap** | a **COUNTER** |

This is the measurement ledger §43.26 named as the single highest-value one available. It also directly
tests the SCLK-drift hypothesis (Doc 172's retracted "keepalive" premise): if `ρ` is 1 to within a few
ppm, the modem's uptime clock is **not** drifting against the AP's, and a pure SCLK-accumulation story
for the period is dead on the numbers rather than on argument.

---

## 5. Achieved vs Expected

| Intent | Expected | Achieved | Status |
| :-- | :-- | :-- | :-- |
| Deliver the instrument §43.26 named as highest-value | a tool that reads `ATS_RTC` over QMI and pairs it with an AP stamp | `packages/ats-probe/` — standalone, static, read-only, AP-stamped | **MET** |
| Not blind at the SSR (the daemon's `-p` is) | a reader with no `STATE_SYNCHRONIZED` gate | no state machine; re-`lookup` on `DEL_SERVER`, resumes on `NEW_SERVER` | **MET** |
| Provably read-only | descriptor + wire + encoder evidence, with controls | 3 levels asserted, negative control fires, GET 14 B / SET 25 B | **MET** |
| The verification checks are themselves sound | checks that can fail | first run produced **2 false FAILs** on comment prose; fixed by stripping comments | **UNEXPECTED** (then MET) |
| Take the measurement | `R_fatal`, `ρ`, and the wrap/condition/always-on verdict | **nothing measured** — no reachable device | **NOT MET** |
| Prove it works on a modem | a live series with `rtc_ms` advancing 1:1 with `ap_boot_ms` | never run on a modem | **NOT MET** |
| Measure the cost of a 1 s GET cadence | a power/autosuspend figure | unmeasured (Doc 173 §5.2 stands) | **NOT MET** |

## 6. What is VERIFIED, and what is NOT

**Verified (all in `evidence/237_ats_probe/verify_output.txt`, `RESULT: ALL CHECKS PASSED`):**

| check | result |
| :-- | :-- |
| builds clean under `-Wall -Wextra` | **PASS** — the only warnings are libqrtr's own `-Wsign-compare` in `qmi.c` |
| links **statically** (no dynamic deps) | **PASS** — `file`: *statically linked*; `ldd`: *not a dynamic executable*; 540 928 B |
| runs and emits the documented line shape | **PASS** — header + clean `what=exit` event on SIGTERM, with no modem present |
| descriptor file byte-identical to the daemon's | **PASS** — `qmi_time.c` `268ae392…`, `qmi_time.h` `ecd285c4…` |
| **no code path can encode a 0x0020 SET** | **PASS** — every `qmi_encode_message()` uses `QMI_TIME_GENOFF_GET_REQ`; zero references to `time_genoff_set_req_ei` / `QMI_TIME_GENOFF_SET_REQ` / `send_ats_user` / `0x0020` **in the comment-stripped source** |
| the read-only check **can fail** | **PASS** — negative control: an injected `time_genoff_set_req_ei` reference is detected |
| the linked encoder reproduces the captured wire | **PASS** — GET = **14 bytes**, SET = **25 bytes**; the 11-byte difference is exactly the `(3 + 8)` offset TLV |

⚠ **The comment-stripping check was itself a fix.** The first run of `build_and_verify.sh` reported two
false `FAIL`s because it grepped the *prose*: line 86 is a comment mentioning `qmi_encode_message()`,
and the file header contains the literal string `0x0020` in the sentence "the 0x0020 SET … is never
built here". A check that cannot tell a comment from a call is a bad check; the script now strips
comments first, and the negative control proves the stripped check still fires.

**NOT verified — read this before relying on it:**

1. **It has never run against a real modem.** The HMU05 OpenWrt arm is unreachable from this host and
   the Android arm is on a different subnet. What is proven is the wire (14-byte GET only), the build,
   and the line shape — **not** that the modem answers, and **not** that the correlation arithmetic
   holds on a live series. Doc 173 §4c's evidence (15 successful `GET(base 0)` transactions on this
   modem) is the nearest live support, and it is on the *helper*, not on this caller.
2. **The power cost of a 1 s GET cadence is unmeasured.** Doc 173 §5.2: a GET still holds the QRTR link
   awake for its round trip. It cannot reset the BAM-DMUX autosuspend the way the 0x0020 write did, but
   "smaller" is not "zero". At 1 s that is 60× the daemon's 60 s traffic.
3. **Concurrent-client behaviour is unmeasured.** The probe and `qcom-time-daemon` both address node 0
   port 11 from separate QRTR sockets. The modem replies to each sender's own port, so the responses do
   not cross — but whether the modem's QMI TIME service behaves identically under two clients is
   **reasoning, not measurement**. **Do not enable the daemon's `-p` while running the probe** (that
   would double the GET rate and confound §3's `ρ`).
4. **The dmesg↔`CLOCK_BOOTTIME` equality is assumed.** Doc 177 measured the *interval* agreement to
   sub-ms; `ap_mono_ms` is logged so a divergence would be visible, but the equality has not been
   re-measured on a boot carrying this tool.

---

## 7. Deployment

The binary is **static**, so the fast path needs no image rebuild and no `.config` change:

```sh
# from the build host (host and target are both aarch64)
scp /tmp/ats_probe_verify/ats-probe root@192.168.8.1:/usr/sbin/
ssh root@192.168.8.1 'ats-probe -i 1000 -o /root/ats.log &'
# then, across the fatal:
ssh root@192.168.8.1 'grep ATS-PROBE /root/ats.log'
```

⚠ **Capture DEVICE-LOCAL.** `-o <file>` exists for exactly this: a host-side `ssh` pipe can silently
truncate (the Android ramdump lesson, `reference_android_ramdump_instrument.md`), and a file on the
overlay is exact. Pull it afterwards with an md5 check.

To bake it into the image instead, add `ats-probe` to `DEVICE_PACKAGES` in `msm89xx/image/msm8916.mk`
(line 78, the `msm89xx` variant) and rebuild. **Deliberately not done here:** the hmu05 image is already
built and verified with patch 826 pending, and changing its package list would invalidate that
verification. The `scp` path keeps the image under test frozen — which is the SOP requirement.

⚠ **One expected consequence:** `build.sh`'s guard compares `packages/` against the live OpenWrt tree
and does `rm -rf openwrt/package/msm8916 && cp -a packages …`. Until the next build sync, the guard will
report `packages/` as out of sync because `openwrt/package/msm8916/ats-probe/` does not yet exist. That
is the new package, not drift — the sync is what creates it.

---

## 8. Traps this tool is designed around

* **The syslog wall clock is not the AP stamp.** The daemon's existing `MODEM-UPTIME` line carries
  `rtc_ms`/`tod_ms` but no monotonic stamp, so its samples cannot be correlated with a `dmesg` fatal
  without it. This tool embeds `ap_boot_ms`.
* **A `STATE_SYNCHRONIZED` gate is fatal to the measurement.** Hence standalone, not another `-p` flag.
* **Bracket the round trip.** QMI latency is ms-scale; `rt_*_ms` is logged so it is subtracted rather
  than assumed away.
* **Do not parse the base index by name.** `ATS_RTC` = 0 (uptime), `ATS_TOD` = 1 (network), `ATS_USER` = 2
  (the write). Confusing 0 with 1 silently inverts the measurement.
* **Read-only is a property of the descriptor, not of care.** Asserted at three levels in §5, with a
  negative control.

---

## 9. SOP-compliance statement

Per the standing rule that every porting/research doc names the SOP steps taken and skipped:

* **Taken:** comparative ground truth (Doc 172's live `ATS_RTC` series, Doc 173's wire capture, Doc 177's
  clock measurement — all read, none re-derived); read the *definition* rather than the name (the
  descriptor is reused byte-identical, md5-checked, rather than re-implemented); **negative controls for
  every check**, and the controls caught a real defect in the checks themselves (the comment-prose
  false-positives, §5); measurement hygiene — the wire lengths come from the **frozen** 2026-09-19
  capture, the binary was built with the **same** toolchain and flags the package uses, and it runs
  **natively** on this aarch64 host so no emulation sits between the test and its result; the image under
  test is left frozen (`scp`, not a package-list edit).
* **Skipped, with reason:** no live deployment (no reachable device on the OpenWrt arm); the daemon's
  `-p` was **not** enabled (would confound Doc 146 §9's rate experiment and §3's `ρ`); no firmware or
  kernel change (standing directive).
* **Explicitly not claimed:** that the tool has run on a modem; that it is free; that a concurrent
  second client is safe; that it changes the fatal rate; that any of §3's three measurements has been
  made — this doc delivers the *instrument*, not the result.

**Reproduce:**

```sh
cd "Docs/Modem Stability/evidence/237_ats_probe"
sh build_and_verify.sh          # builds + all 7 checks; exit 0 = all passed
```

Artifacts: `build_and_verify.sh`, `test_encode.c` (Doc 173's encoder test, reused verbatim,
md5 `e7d36ca4…`), `verify_output.txt`.

Source (tracked): `packages/ats-probe/{Makefile,src/ats-probe.c,src/qmi_time.c,src/qmi_time.h}` —
`ats-probe.c` md5 `c9e593f6…`, `Makefile` `69e922e8…`, `qmi_time.c` `268ae392…`, `qmi_time.h`
`ecd285c4…`.
