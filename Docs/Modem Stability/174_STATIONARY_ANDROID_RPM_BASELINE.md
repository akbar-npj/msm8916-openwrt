# 174 — The STATIONARY Android RPM baseline: the modem collapses ~1.4×/s, and the bus never confounded that

**Date:** 2026-09-22
**Device:** HMUF02-v5 ("hmu05"), stock Android `msm8916_32_512-userdebug 4.4.4 KTU84P`
**Answers:** Doc 171 §7 / Task #101 — the Android half of the RPM master-stats comparison,
measured with the platform **stationary** so the bus confound is removed.
**Status:** the Android side is now CLOSED. The OpenWrt side is still **PREPARED, NOT BUILT,
NOT FLASHED** (Doc 171 / commit `3bd279b`).
**Supersedes:** the "Android MPSS rate is a BUS baseline" caution recorded in Doc 171 and in
memory — see §3, which finds the bus did **not** move this metric.

---

## 1. SOP statement

| SOP step | Status |
| :-- | :-- |
| Stock-HMU05 comparative ground truth used | **Yes** — every Android number is compared against the OpenWrt figures already in the corpus (Doc 150 §baseline, Doc 170) rather than against a fresh OpenWrt run, which is not possible while the device is flashed to Android |
| Measurement frozen before scoring | **Yes** — all captures copied to `frozen_20260922T041958/` with `md5sum` manifest *before* any number was computed (hygiene rule 1) |
| Comparability decided from kernel state | **Yes** — segments split on `/proc/uptime` decrease (a reboot), never on operator action |
| Routing-path connectivity recorded | **Yes** — `ping` without `-I` (hygiene rule 3) |
| Physical/environmental context established first | **Yes** — and it mattered twice: the operator's "cable loose" disclosure (§7), and the stationary-vs-bus question (§3) |
| Sample size pre-registered | **Partially** — no `n` was pre-registered for this baseline; the collapse-rate windows are 324 s and 456 s (448 and 690 collapses), which is ample for a rate but was not fixed in advance |
| Instrument verified before use | **Yes** — the RPM ring layout was checked against Doc 150's grammar on the live device before any census was taken (§2.2) |
| Negative controls | **Yes** — §4 is a self-caught false positive, and §3's bus comparison is a negative control on the confound claim |

**Skipped:** nothing that applies. The 250→2000 ms pc-ack A/B (Task #98) and the
`FastDormancyService` falsifier (Task #103) remain blocked on OpenWrt and are not touched here.

---

## 2. Two capabilities this round added

### 2.1 The Android side is 32-BIT — aarch64 binaries cannot run on it

```
$ adb shell uname -a
Linux localhost 3.10.28 #1 SMP PREEMPT Thu Aug 28 15:04:41 CST 2025 armv7l GNU/Linux
$ adb shell 'ls /system/lib64'
/system/lib64: No such file or directory
$ adb shell getprop ro.product.cpu.abi
armeabi-v7a
```

Every static **aarch64** binary built from this repo is rejected:

```
/system/bin/sh: /data/local/tmp/rpmring: not executable: magic 7F45
```

That message is mksh reporting the kernel's `ENOEXEC` after reading the first four bytes.
It is **not** a permission problem: `/data` is mounted `nosuid,nodev` — **no `noexec`** — the
pushed file's `md5sum` matches the host, and a trivial `-static` hello-world built with the same
toolchain fails identically. Time was spent on this before the architecture was checked; the
build string had said `msm8916_32` all along.

> **This is a first-class differential.** Android runs a **32-bit `armv7l` kernel 3.10.28**;
> the OpenWrt port runs an **aarch64 64-bit kernel** on the same die. So every
> "Android vs OpenWrt" comparison differs in kernel **bitness and version**, not only in drivers.
> The modem firmware is byte-identical across both (Doc 155), so this is an AP-side axis only —
> but it was previously unstated, and it is a standing confound for any AP-side timing comparison.

### 2.2 The RPM log ring **is** readable on Android — via `devmem`

Doc 150 §2 established that on OpenWrt `devmem` is too slow (2.9 ms/word ⇒ 5.94 s for the ring,
longer than the ring period) and that the tracked `rpmring` tool must be used instead. **On
Android the ring is at the same AP physical address and `/system/xbin/devmem` reads it**, so no
binary and no kernel module is needed:

| address | what |
| :-- | :-- |
| `0x29dc00` | header, 22 words |
| `0x29dc38` | the ring's 32-bit **byte** write counter (header word 14) |
| `0x29dc58` | ring base — 2048 words = 256 records × 8 words (32 B/record) |

Verified live: header words 2–5 spell **`"RPM External Log"`**, word 10 = `0x2000` (ring bytes),
word 11 = `0x1fff`; every record carries `word[0] = 0x00200000`, `word[1]` a 19.2 MHz timestamp,
`word[3]` an event id and `word[4..7]` arguments — **the same grammar as Doc 150 §6**. Across the
pooled dumps, **3584 of 3584 records** carried the marker at stride 8.

**⚠ A ring walk is not a coherent snapshot, and on Android it is far worse than on OpenWrt.**
Measured Android counter rate ≈ **540–590 rec/s**, so the 256-record ring turns over in
**~0.45 s** while the walk takes ~5 s — the ring wraps ~10× mid-walk. The **event-id and
resource histograms are still valid samples** (each slot is read once), but the record
**ordering and timestamps are not usable**. A lossless Android track is **not achievable this
way**: at ~590 rec/s a 2 ms/word `devmem` loop cannot keep up.

### 2.3 `/d/rpm_log` is a trap — do not use it as an RPM record source

`/d/rpm_log` looks like the obvious answer to §2.2's aliasing problem, and memory lists it as an
available instrument. It is **not usable**, and this was established before relying on it:

* It is a **live, unbounded ASCII-hex stream** (`- 0x00, 0xCC, 0x1D, 0xB0, …`) — two 200 KB reads
  8 s apart differ (`md5` `6700a222…` vs `d7152baa…`), and a plain `cat` ran for **3.5 min
  producing 17 MB** without EOF. Throughput ≈ **130 KB/s of text ≈ 22 KB/s of data**, which is
  suspiciously close to the ring's own 540 rec/s × 32 B ≈ 17 KB/s — which is why it is tempting.
* **It does not carry the ring's records.** Decoded to bytes, **neither** sample contains the
  `0x00200000` record marker **at any byte offset** (0 hits in 33 326 bytes and 0 hits in
  2 851 541 bytes).
* **Its content is not stable.** The 200 KB read has clear structure (11.6 % of 32-bit words
  below `0x1000`, a repeated `?? ?? 1d b0` motif); the 17 MB read has **none** — **0.00 %** of
  words below `0x1000` and a flat 256-value byte histogram. Two reads of the same node returned
  two different *kinds* of data.

**Verdict: `/d/rpm_log` must not be used as an RPM record source.** Whatever it streams, it is not
the external-log ring in the ring's encoding, and it is not reproducible. The `devmem` ring read
above remains the only verified way to get RPM records on Android.

---

## 3. Result 1 — the modem's power-collapse rate, and the bus never mattered

`/d/rpm_master_stats` reports `numshutdowns` per master. On this device **only MPSS moves**;
APSS and PRONTO are flat across every segment. Two independent boots, both stationary, good RF
(RSRP −91…−94 dBm, home JIO LTE, data connected):

| boot | window (AP uptime) | span | MPSS collapses | **rate** | wall period | APSS | PRONTO |
| :-- | :-- | --: | --: | --: | --: | :-- | :-- |
| A (`rpm_stationary.csv`) | 493.8 → 817.8 s | 323.9 s | 448 | **1.3831 /s** | 723.0 ms | 5 → 5 | 9 → 9 |
| B (`rpm_stationary_boot2.csv`) | 79.3 → 535.2 s | 455.8 s | 690 | **1.5137 /s** | 660.7 ms | 0 → 0 | 21 → 21 |

50 s windows fluctuate **1.09 – 1.92 /s** on both boots with no monotonic trend, so the 9.4 %
gap between the two overall means is within the natural spread rather than a boot effect.

> **The correction.** Doc 171 recorded the Android rate as a **BUS baseline**
> (1.310 / 1.523 / 1.711 /s over three windows) and flagged the cross-platform comparison as
> blocked on a stationary run. **The stationary values (1.383, 1.514) fall inside the bus range.**
> The bus did **not** confound this metric — the collapse cadence is set by something that
> mobility does not reach at this resolution. The caution was reasonable but is now measured
> false, and Task #101's Android half is closed.

**What the number does and does not say.** It is the modem's *own* counter, so it is free of any
AP-side clock or driver. It does **not** explain the ~902.3 s fatal: 902.3 s × 1.38–1.51 /s is
1245–1365 collapses, which is not a round number and is not offered as one. Nor is the
**0.66–0.72 s** period an LTE DRX value (the shortest LTE paging cycle is 1.28 s), so the
collapse cadence is **not** the paging cycle and must not be equated with it.

---

## 4. A trap this round caught in itself: the 19.2 MHz counters WRAP

`shutdown_req` and `wakeup_ind` are 19.2 MHz counters. **A 19.2 MHz counter wraps 2³² in
223.7 s**, so any delta taken across a window longer than ~224 s is meaningless. Taken naively
across a 324 s window, `shutdown_req` appears to advance only 104.9 s of tick time — which reads
as a **"32.4 % modem duty cycle"** and a **"234 ms per-collapse period"**.

Both of those are **wrap artefacts**. Measured on the 10 s samples (192 × 10⁶ ticks per interval,
comfortably under 2³²) the ratio of observed to expected ticks is:

| boot | `shutdown_req` p50 | `wakeup_ind` p50 |
| :-- | --: | --: |
| A | 1.011 | 1.013 |
| B | 1.004 | 0.998 |

**p50 = 1.00 on both counters on both boots ⇒ the counters are free-running, not gated.** The
duty-cycle and period figures were written into a draft of this doc and retracted here before
publication; the scorer now asserts the per-interval ratios and prints the trap explicitly.

> The per-collapse **wall** period in §3 comes from `/proc/uptime`, not from these counters, and
> is unaffected. **The individual meaning of the two fields is not established** — do not compute
> a modem duty cycle from them.

This is the same failure shape the corpus already warns about: a mechanism that fits perfectly
and does not exist. The tell here was arithmetic — a 324 s window against a counter that wraps in
224 s — and it was caught only by scoring the *short-interval* deltas as a control.

---

## 5. Result 2 — the RPM log record rate: Android is higher, but the ranges OVERLAP

| source | window | records | rate |
| :-- | --: | --: | --: |
| `rpm_ctr_rate.txt` | 39.3 s | 22 382 | **568.8 /s** |
| `rpm_ctr_rate2.txt` | 58.2 s | 34 397 | **590.9 /s** |
| `rpm_ctr_long.txt` (frozen) | 230.5 s | 124 521 | **540.2 /s** |
| 14 ring dumps (pooled) | 14 × ~5 s | 11 070 | **388.4 /s** mean, 162.6 – 643.9 |

Per-interval rates are **bursty**: min 128, p50 ~525–592, max 1412 rec/s.

The OpenWrt reference is **224.2 rec/s** over a 314 s baseline (Doc 150). That is ~2.4× lower
than Android's mean — **but Doc 170 also records OpenWrt windows of 470–830 rec/s**, which
overlap the Android range directly.

> **Verdict: NOT a clean differential.** The RPM log is *busier* on Android on average, but the
> two platforms' ranges overlap and the OpenWrt reference points were not taken in a matched
> regime. This does not license the claim "OpenWrt's modem talks to the RPM less". A matched
> stationary OpenWrt capture is pre-registered in §8.

---

## 6. Result 3 — the resource census is broadly the same, with two outliers

Pooled 14 dumps = 3584 records; 1616 exact 4-char resource matches. Compared against Doc 150's
OpenWrt census (n = 1701, taken over a **147 s capture around fatal #9** — see the caveat):

| resource | Android | OpenWrt (Doc 150) | ratio |
| :-- | --: | --: | --: |
| `ldoa` | 37.9 % | 40.7 % | 0.93× |
| `bslv` | 19.7 % | 14.6 % | 1.35× |
| `bmas` | 14.4 % | 7.8 % | 1.84× |
| `clk2` | 6.9 % | 10.8 % | 0.64× |
| `smpa` | 6.0 % | 16.0 % | **0.38×** |
| `clk1` | 5.8 % | 5.9 % | 0.98× |
| `clk0` | 5.2 % | 0.4 % | **14.7×** |
| `clka` | 4.1 % | 3.8 % | 1.07× |

Event ids are dominated by `0xd5` (36.3 %), then `0xcb` 9.5 %, `0x42` 7.8 %, `0x44` 5.9 %,
`0x2bf` 5.2 %; the client-request shapes `0xd0`/`0xd1`/`0xd2` are 2.8 / 3.5 / 3.5 %.

Burst rate from the `0xd0` count: **~10.7 /s** on Android against Doc 150's **8.6 /s** at idle
(median inter-burst 1.2531 s, n = 69) — a 1.27× difference.

> **Caveat that limits this section.** Doc 150's census is from a window **around a fatal**, not a
> matched idle window, so `smpa`/`clk0` may reflect the fatal rather than the platform. `clk0`'s
> 14.7× is the kind of ratio that invites a story; **no story is offered**, because the
> comparison is not matched. The honest reading is: **the resource mix is broadly the same on
> both stacks**, and the two outliers are unresolved.

---

## 7. The cable incident — attributed from the HOST, not the device

At **04:10:48** the device vanished from `adb` mid-run. The host kernel log gives the attribution
without ambiguity:

```
04:10:46  xhci-hcd xhci-hcd.2.auto: new USB bus registered, assigned bus number 1
04:10:51  macsmc … RTKit: syslog message: aceElec.cpp:711:  Elec: Elec Cause 0x0
04:10:52  xhci-hcd xhci-hcd.2.auto: remove, state 4
04:10:52  usb usb2: USB disconnect, device number 1
04:10:52  xhci-hcd xhci-hcd.2.auto: USB bus 2 deregistered
04:10:52  xhci-hcd xhci-hcd.2.auto: USB bus 1 deregistered
04:10:54  xhci-hcd xhci-hcd.2.auto: new USB bus registered, assigned bus number 1
```

The device came back with `uptime 29.25 s` and MPSS `numshutdowns` reset to `0x35`. The operator
then disclosed **"device rebooted, my mistake, cable loose"** — confirming the reading.

> **This is the third host-side power event in the corpus** and it satisfies the standing AP-hang
> rule's disqualifying condition (A): a host `xhci … remove` / `USB bus N deregistered` /
> `Elec Cause` inside the outage window. It is **not** an AP hang, **not** a device self-reset and
> **not** a modem event. The device has still **never** been observed to reset itself.

The practical lesson for long Android captures: a dongle-powered device on a loose cable loses
uptime without warning, and the `adb` disconnect is the *symptom*, not the finding. Read
`journalctl -k` before attributing anything.

---

## 8. What this does NOT establish, and the pre-registered OpenWrt comparison

**Not established:**

1. **Any causal link to the ~902.3 s fatal.** The collapse rate is a modem-internal activity
   number; it is not a timer, and 902.3 s is not a multiple of the 0.66–0.72 s period.
2. **That OpenWrt's modem is quieter to the RPM.** §5's ranges overlap.
3. **Any meaning for `shutdown_req` / `wakeup_ind`.** §4 shows they are free-running counters;
   their semantics are unresolved.
4. **That the resource-mix outliers are real.** §6's comparison is not matched.
5. **Anything about the post-fatal regime.** Android produces **zero** fatals in every soak on
   record, so Android structurally cannot measure it.

**Pre-registered OpenWrt comparison (runs once Doc 171's enabler is flashed).** State the required
`n` before the run, per the standing rule:

| # | hypothesis | decision rule | n |
| :-- | :-- | :-- | --: |
| W1 | OpenWrt's MPSS collapse rate differs from Android's 1.38–1.51 /s | two-sided; report the rate with its 50 s window spread; **do not call a difference unless the spreads separate** | ≥ 300 s, ≥ 400 collapses |
| W2 | OpenWrt's RPM record rate differs from Android's ~540 /s | compare **only** at matched uptime and matched RF (RSRP, cell, data state); report both ranges, not just the means | ≥ 300 s |
| W3 | the resource mix differs (esp. `smpa`, `clk0`) | compare against a **matched idle** OpenWrt window, **not** Doc 150's fatal-adjacent one | ≥ 3000 records |

**Cheapest first step:** W1 is a single `cat` of three sysfs files once the module is loaded. It
is the cheapest decisive measurement in the corpus right now, and it is the one the whole
stationary exercise was for.

---

## 9. Artifacts

`Docs/Modem Stability/evidence/174_stationary_android_rpm_baseline/`

| file | what |
| :-- | :-- |
| `score_stationary_baseline.py` | replays every number in this doc from the frozen artifacts; gz-transparent |
| `verify_output.txt` | the scorer's output |
| `stationary_baseline.sh` | the sampler (one `adb` round trip per sample; records uptime, the three masters' counters, the MPSS tick fields, and the **raw** telephony lines) |
| `probe_rpm_log_node.sh` | reproduces §2.3 — the two differing `md5`s and the zero-marker result |
| `rpm_log_200k.bin.gz` | one 200 KB `/d/rpm_log` read, for the §2.3 structure check |
| `rpm_stationary.csv.gz` | boot A — uptime 493.8 → 817.8 s |
| `rpm_stationary_boot2.csv.gz` | boot B — uptime 79.3 → 535.2 s |
| `rpm_ctr_rate.txt.gz`, `rpm_ctr_rate2.txt.gz`, `rpm_ctr_long.txt.gz` | RPM write-counter series (1 s, 2 s, 5 s intervals) |
| `rpm_ring_android_multi.txt.gz` | 14 ring walks with per-walk counter brackets |
| `rpm_ring_android_t0.txt.gz` | the first ring walk, with header before and after |
| `ping_plain_stationary.txt.gz` | routing-path `ping` (no `-I`) across the baseline |
| `MANIFEST.md5` | hashes of the frozen set |

**Reproduce the ring read with nothing but the device:**

```sh
adb shell 'i=0; while [ $i -lt 2048 ]; do /system/xbin/devmem $((0x29dc58 + i*4)); i=$((i+1)); done'
adb shell '/system/xbin/devmem 0x29dc38'    # byte write counter; records = counter>>5
```

**Note on a live file:** `rpm_stationary_boot2.csv` and `rpm_ctr_long.txt` were **still growing**
when the frozen set was taken at 04:19:58. Every number above is from the **frozen** copies, and
the boot-B window is therefore a lower bound on its final length, not a fixed run.
