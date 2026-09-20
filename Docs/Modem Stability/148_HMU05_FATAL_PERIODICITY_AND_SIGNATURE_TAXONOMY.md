# 148 — HMU05: the modem fatal is periodic *within* a boot, the corpus conflates ≥10 distinct fatal signatures, and the fatal→SSR→bearer-rebuild chain explains the address churn

**Status:** measured and complete for what it claims. Two corpus corrections, one closed open
item, and one new open question with a defined next experiment.
**Supersedes in part:** the wording of retraction #1 in `README.md` (the fixed-900 s-timer
retraction) — see §6.
**Depends on:** `147_HMU05_PSTORE_PANIC_CAPTURE_AND_STALL_CHARACTERISATION.md` (the AP-side
crash fix; the first-packet-loss characterisation; §8's open item about the bearer churn).

---

## 1. Why this document exists

Doc 147 §8 left three things open. One of them was:

> **The bearer is being re-established repeatedly.** The wwan0 address changed three times
> during this session (`10.32.48.33` → `10.139.191.152` → `10.89.244.25`) with no reboot.
> Something is tearing the bearer down and rebuilding it on a timer.

This document closes that item, and in doing so found that the *thing doing the tearing down*
is the modem firmware itself, on a period that is far more regular than the corpus believes.
That forced a re-read of the corpus's retraction #1 ("the fatal has a fixed ~900 s period is
FALSE"), which turned out to be **true for one signature and false as a general statement**.

Everything below is from the deployed device on 2026-09-20, boot at 17:38 UTC, plus a census of
every fatal signature already recorded anywhere in this repo.

---

## 2. SOP compliance statement

| SOP step | Status |
| :--- | :--- |
| Dual-firmware comparison before attributing anything to a firmware change | **Not run.** No firmware was changed this session; the deployed set still verifies against profile `stock` ("OK: active firmware set matches profile 'stock'"). |
| Verify the deployed driver matches its source | **Done.** `md5(/usr/sbin/modem-bearer-watchdog) = 2eb113ac623c9b2c339c096c29a43ce6`, matching the tracked `msm89xx/base-files/` copy. Driver source reproducibility is Doc 147 §7. |
| Fingerprint the build before attributing a result to it | **Done.** Kernel `6.12.94`; the telemetry interface used is `rx_telemetry`, a `DEVICE_ATTR_RO` added by `msm89xx/patches/808-bam-dmux-stats.patch`. Its presence proves patch 808 is in the deployed module. |
| Prefer direct AP-side measurement over inference from a capture | **Done, and it mattered.** The fatal times in §3 come from `dmesg`, not from a DIAG capture. The corpus's DIAG-based negative evidence is weaker than it was treated as being — §6.4. |
| Do not blind-patch the baseband | **Honoured.** No baseband patch was attempted. |
| State what was *not* established | **Done.** §7 lists every open question explicitly. |

---

## 3. The measurement: five fatals, 903.674 s apart — and the model predicts the next one

`dmesg` on the deployed boot (all timestamps are AP uptime, seconds):

```
[  914.769287] qcom-q6v5-mss 4080000.remoteproc: fatal error received: lte_ml1_common_timer.c:390:
[  914.830957] remoteproc remoteproc0: stopped remote processor 4080000.remoteproc
[  916.175733] bam-dmux ...: bam_dmux: SSR after powerup: scheduling powerup work
[  916.175824] remoteproc remoteproc0: remote processor 4080000.remoteproc is now up

[ 1818.443149] qcom-q6v5-mss 4080000.remoteproc: fatal error received: lte_ml1_common_timer.c:390:
[ 1818.502612] remoteproc remoteproc0: stopped remote processor 4080000.remoteproc
[ 1819.807073] bam-dmux ...: bam_dmux: SSR after powerup: scheduling powerup work
[ 1819.807165] remoteproc remoteproc0: remote processor 4080000.remoteproc is now up

[ 2722.117987] qcom-q6v5-mss 4080000.remoteproc: fatal error received: lte_ml1_common_timer.c:390:
[ 2722.180187] remoteproc remoteproc0: stopped remote processor 4080000.remoteproc
[ 2723.511231] bam-dmux ...: bam_dmux: SSR after powerup: scheduling powerup work
[ 2723.511313] remoteproc remoteproc0: remote processor 4080000.remoteproc is now up

[ 3625.792806] qcom-q6v5-mss 4080000.remoteproc: fatal error received: lte_ml1_common_timer.c:390:
[ 4529.467002] qcom-q6v5-mss 4080000.remoteproc: fatal error received: lte_ml1_common_timer.c:390:
```

| quantity | value |
| :--- | :--- |
| fatal times (AP uptime) | 914.769287 / 1818.443149 / 2722.117987 / 3625.792806 / **4529.467002** |
| interval #1→#2 | 903.673862 s |
| interval #2→#3 | 903.674838 s |
| interval #3→#4 | 903.674819 s |
| interval #4→#5 | 903.674196 s |
| mean interval (n=4) | **903.674429 s** |
| spread across 4 intervals | 0.000976 s = **1.08 ppm** |
| signature | `lte_ml1_common_timer.c:390`, **5/5** |
| SSR outage per fatal | ~1.3 s (fatal → modem back up) |
| AP-side oopses this boot | **0** |

Modem bring-up in this boot is at AP 12.092370 s, so on the *modem's* own uptime clock the
fatals land at ≈ **902.68 / 1806.35 / 2710.03 / 3613.70 / 4517.37 s**.

### 3.0 The model is PREDICTIVE, not merely a fit

Before the fifth fatal occurred, this document stated the next one was *due at AP ~4529.5 s*.
It landed at **4529.467002 s** — an error of **0.033 s over a 903.674 s horizon (37 ppm of the
interval, and 6 s of wall clock)**. A retrospective fit to four points would not survive that
test. This is a **deterministic ~903.674 s timer**, and the remaining question is no longer
*whether* it is periodic but **what resets it, and why the AP fails to**.

That reframes the whole line of attack: the useful question is "what does Android do, at least
once every 903.674 s, that OpenWrt does not?" — not "what is the failure rate".

### 3.0.1 The pattern BREAKS under traffic — experiment E1

A 1 Hz ping was started at AP 5310 s to test whether the AP's activity pattern matters. Fatal #6
came at **5426.331445 s** — and **two things changed at once**:

| | idle (fatals 1–5) | under traffic (fatal 6) |
| :--- | :--- | :--- |
| signature | `lte_ml1_common_timer.c:390`, 5/5 | **`a2_power.c:1189`** |
| interval | 903.674 s (1.08 ppm over 4) | **896.864443 s** (−6.81 s) |

A **falsifiable prediction** was recorded before the next fatal (E1 continued past the next
boundary): the two competing models differ by 6.81 s, which the measured 1 ppm stability
resolves easily.

| model | predicted next fatal |
| :--- | :--- |
| #6 was an anomaly; idle cadence resumed | 5426.331445 + 903.674429 = **6330.005874 s** |
| traffic changed it; new cadence persists | 5426.331445 + 896.864443 = **6323.195888 s** |

This is the first evidence that the fatal is **not one immutable timer**, and it would unify two
things the corpus found confusing: three signatures with three *different* ~900 s periods
(§6.2), and `a2_power.c:1189` appearing at 172 / 918 / 1824 / 2729 s across boots. If the AP's
activity pattern selects which of several ~900 s timers expires first, both fall out of one model
— and the AP becomes the lever. Full record:
`evidence/148_fatal_periodicity/e1_traffic_interim.md`.

The 4th fatal also produced a **4th `wwan0` address** (`10.90.201.29`), confirming the
fatal→SSR→rebuild chain in §4 a fourth time.

### 3.1 What it is *not*

* Not the AP-side oops. `dmesg | grep -ciE "Internal error|Call trace"` = **0**. Doc 147's fix
  holds across three SSR cycles.
* Not a lost PC edge. `pc_resync_count: 0`, and `pc_irq_count == pc_ack_irq_count` (400 == 400),
  so every power-collapse edge was acknowledged. The RX watchdog's *resync* branch
  (`qcom_bam_dmux.c:1202`) never fired.
* Not the RX watchdog intervening. The `RX watchdog: quiesced Ns` lines are a **status report**
  (`qcom_bam_dmux.c:1227`), emitted only when `pc_state=0` *and* the wire level agrees
  (`pc_line=0`) — i.e. the modem genuinely is collapsed. `rx_rearm_count: 0` confirms the rearm
  branch never ran.

---

## 4. The chain: fatal → SSR → bearer rebuild → first-packet loss

The §8 open item is now closed. The bearer rebuild is a *consequence* of the fatal:

| AP uptime | event |
| :--- | :--- |
| 2722.118 | fatal (`lte_ml1_common_timer.c:390`) |
| 2722.180 | `remoteproc0: stopped remote processor` |
| 2723.511 | modem back up; bam-dmux SSR powerup |
| ~2793 | `[modem3/bearer8] QMI IPv4 Settings: address: 10.89.244.25/30` (syslog `18:24:33`) |
| ~2793 | `Interface 'modem' is now up`, `Network device 'wwan0' link is up` |

Three fatals, three SSRs, and the bearer number reached **8** — `bearer8` is the bearer created
after the third fatal. Each rebuild hands out a **new** `10.x` address from the carrier, which is
exactly the `10.32.48.33` → `10.139.191.152` → `10.89.244.25` churn Doc 147 §8 flagged.

**So the churn is not an AP-side timer.** It is the modem fataling and the AP correctly
recovering. Two consequences:

1. Every ~903.674 s the link is down for ~1.3 s and the bearer is rebuilt — an unavoidable
   interruption while the fatal persists, and a window in which the first packet is lost.
2. Chasing "what tears the bearer down on a timer" on the AP side was chasing the wrong layer.
   The lever is the fatal, not the rebuild.

**The first-packet loss is still live** and was re-confirmed during this session:

```
BEFORE: rx_callbacks=560 rx_queued_buffers=0 rx_tearing_down=1 pc_state=0 pc_quiesce_ms=9439
  ping -c 3 -W 5 8.8.8.8  ->  3 transmitted, 2 received, 33% packet loss   (first lost)
AFTER:  rx_callbacks=562 rx_queued_buffers=32 rx_tearing_down=0 pc_state=1 pc_quiesce_ms=0
```

### 4.1 `rx_tearing_down: 1` while collapsed is correct behaviour, not a stuck flag

Worth recording because it looks alarming in the telemetry:

* `bam_dmux_pm_quiesce()` disarms the ring and sets `rx_tearing_down` (`qcom_bam_dmux.c:1475`);
* `bam_dmux_power_on()` clears it under `rx_lock` (`qcom_bam_dmux.c:1312`);
* the RX watchdog deliberately skips rearming while it is set (`qcom_bam_dmux.c:1235`).

Observed clearing exactly on wake, with the ring re-armed to 32/32 buffers and `rx_callbacks`
incrementing. No action needed.

---

## 5. Power-collapse behaviour, measured

`rx_telemetry` (path in §2) sampled every 20 s for 120 s while idle:

```
t=0    pc_irq=400 pc_quiesce_ms=2059   t=60   pc_irq=408 pc_quiesce_ms=9557
t=20   pc_irq=406 pc_quiesce_ms=9803   t=80   pc_irq=412 pc_quiesce_ms=10061
t=40   pc_irq=406 pc_quiesce_ms=29830  t=100  pc_irq=412 pc_quiesce_ms=30086
                                       t=120  pc_irq=424 pc_quiesce_ms=358
```

* 24 PC edges in 120 s = 12 collapse/wake cycles ≈ **10 s per cycle** in that window.
* Whole boot: `pm_suspend_attempts: 202` in ~3322 s ≈ **16.4 s per cycle**.
* Android (stock, live, prior session): 918 collapses in 54 min ≈ **3.53 s per cycle**.

So the OpenWrt modem wakes ~**4.6× less often** than the Android one. **Caveat: the two runs
had different traffic loads, so this is a candidate difference, not a causal finding.** It needs
a matched-traffic comparison. It is recorded because "the AP lets the modem stay asleep far
longer than Android does" is a plausible route to a firmware sleepmgr/`rpm.sync` stall, which is
where the firmware RE already points (`Modem RE/hmu05/900S_CRASH_LPR_FRAMEWORK_RE.md`).

### 5.1 The continuous-collapse duration grows across the boot

`pc_quiesce_ns` is cleared by `bam_dmux_power_on()` (`qcom_bam_dmux.c:1317`), so a logged
`quiesced Ns` really is N seconds of *continuous* collapse. Reconstructing the quiesce starts
from the RX-watchdog logs:

| window | continuous-collapse durations observed |
| :--- | :--- |
| between fatal #1 and #2 | 60 s, 60 s, 60 s, 60 s |
| between fatal #2 and #3 | 60 s→120 s, 60 s, 60 s, 60 s→120 s→**180 s** |

The last 180 s of continuous collapse ends immediately before fatal #3. Whether that is a
precursor or simply a consequence of the link being idle is **not established** — it is one
observation and the confound is obvious.

### 5.2 `pc_timeout_count` is an SSR artifact, NOT a handshake defect — self-correction

An earlier revision of this section claimed that `pc_timeout_count: 2` showed the A2 resume ACK
"has timed out", weakening Doc 146 §11 hypothesis #2 (which had been set aside partly on
`pc_timeout_count: 0`). **That reading was wrong.** Checking `dmesg` against the fatal times:

| fatal (s) | nearest driver warning | Δ |
| ---: | :--- | ---: |
| 914.769287 | `refusing to queue command while modem is collapsed` @914.879374 | +0.110 s |
| 1818.443149 | `modem pc-ack timeout during resume` @1818.815204 | +0.372 s |
| 3625.792806 | `modem pc-ack timeout during resume` @3626.414786 | +0.622 s |
| 4529.467002 | `modem pc-ack timeout during resume` @4529.854549 | +0.388 s |
| 5426.331445 | `modem pc-ack timeout during resume` @5426.705289 | +0.374 s |

Every `pc-ack timeout` lands **0.1–0.6 s after a fatal**, and `remoteproc0: stopped remote
processor` lands ~0.06 s after the fatal. So the sequence is: **modem dies → SSR → the AP's
in-flight resume handshake times out.** The counter measures the *consequence*, not a defect.
`pc_resync_count` remains 0 and no `lost edge` warning has ever appeared.

This **removes** the "the A2 handshake is failing" line of attack rather than opening it. It also
means `pc_timeout_count` is expected to scale with the *number of fatals* — a useful sanity check
when reading it on any future boot (this boot: 6 fatals, `pc_timeout_count: 7`).

The remaining unexplained telemetry is `pm_suspend_attempts` vs `pm_suspend_completions`
(553 vs 538 here — 15 incomplete, more than the 4 handshake timeouts). Not yet attributed.

---

## 6. Corpus corrections

### 6.1 There are at least **ten** distinct fatal signatures, not one

Census over every log in this repo (`evidence/148_fatal_periodicity/fatal_signature_census.txt`):

| count | signature |
| ---: | :--- |
| 17 | `lte_ml1_sleepmgr_stm.c:4054` |
| 17 | `lte_ml1_common_timer.c:390` |
| 6 | `a2_power.c:1189` |
| 5 | `mmoc.c:2326` |
| 2 | `mmoc.c:2192` |
| 1 each | `wl1m.c:8670`, `memheap.c:1242`, `lte_ml1_sm_conn_inter_freq_stm.c:712`, `lte_ml1_rfmgr_trm.c:4014`, `coex_interface.c:530` |

The corpus has been reasoning about "the 900 s crash" as one phenomenon. It is a *family*.
Any claim of the form "the fatal does X" must name the signature.

### 6.2 Within a boot the period is stable; across boots both the period **and** the signature change

Datable instances:

| signature | timestamps (s) | intervals (s) |
| :--- | :--- | :--- |
| `lte_ml1_common_timer.c:390` | 914.769287 / 1818.443149 / 2722.117987 | 903.6739, **903.6748** |
| `lte_ml1_sleepmgr_stm.c:4054` | 912.900514 → 1815.130800 | **902.2303** |
| `a2_power.c:1189` | 172.353 / 918.195 / 1823.753 / 2729.267 | 745.842, 905.558, 905.514 |

Three signatures, three *different* periods: **902.230 / 903.674 / 905.5 s**. Spread ≈ 3.3 s
(≈ 3600 ppm) — far too large to be crystal drift between two ~900 s windows, and far too small
to be unrelated. The honest statement is: *there is a ~900 s class of timeout; which signature
fires, and the exact period, vary per boot.*

#### 6.2.1 Can the period be expressed in timer units? **Not established — and the near-miss is a trap**

The temptation is to divide 903.674 s by a plausible LTE clock and declare a wrap counter. The
numbers:

| candidate unit | 903.674350 s ÷ unit | nearest integer | residual |
| :--- | ---: | ---: | ---: |
| 1.28 s (LTE DRX short) | 705.99559 | 706 | **−5.65 ms (−6.3 ppm)** |
| 2.56 s (LTE DRX long) | 352.99779 | 353 | −5.65 ms (−6.3 ppm) |
| 0.64 s | 1411.99117 | 1412 | −5.65 ms (−6.3 ppm) |
| 10.24 s (SFN hyperframe) | 88.24945 | 88 | +2.554 s (+2827 ppm) |
| 1 ms tick | 903674.35 | 903674 | +0.35 ms (0.4 ppm) |

`706 × 1.28 s = 903.68 s`, and the measurement sits 6.3 ppm below it — exactly the kind of
discrepancy you would expect between the AP's clock and the modem's DRX clock. **But the same
test applied to the other two signatures fails:**

| signature | period (s) | ÷ 1.28 s | residual |
| :--- | ---: | ---: | ---: |
| `lte_ml1_common_timer.c:390` | 903.6744 | 705.9956 | −0.0057 s (−6 ppm) |
| `lte_ml1_sleepmgr_stm.c:4054` | 902.2303 | 704.8674 | −0.170 s (−188 ppm) |
| `a2_power.c:1189` | 905.536 | 707.4500 | +0.576 s (+636 ppm) |

Only one of three lands on an integer. With ~900 s of run time and a free choice of unit, *some*
coarse unit will always fit one series to a few ppm — that is exactly how the corpus's "901.5 s
hardcoded timer" and "`lte_ml1_common_timer.c:390` every 902 s" claims arose. The 1 ms and
sleep-clock rows "fit" trivially because the unit is fine-grained, and carry no information.

**So: do not adopt a DRX-cycle model on this evidence.** The model-free facts are the ones to
build on — *within* a boot the interval is stable to ~1 ms, and *across* boots both the period
(±3.3 s) and the signature change. A DRX model would become testable if the network's paging
cycle were changed and the period scaled with it; that has not been done.

### 6.3 Retraction #1 must be scoped to the signature it was measured on

`README.md` currently says, of retraction #1:

> **"The fatal has a fixed ~900 s period."** FALSE. Measured `a2_power.c:1189` fatal times:
> **172.353 / 918.195 / 1823.753 / 2729.267 s**. One boot produced a single fatal at 172 s and
> none through 1157 s. It is a *rate*, not a period. Everything that models a "900 s timer" /
> "SCLK maintenance timer" / "`lte_ml1_common_timer.c:390` every 902 s" is wrong.

The evidence cited is entirely `a2_power.c:1189` — and that evidence *does* falsify a fixed
period **for that signature** (the 745.842 s outlier; the boot with no second fatal). But the
sentence then generalises, by name, to `lte_ml1_common_timer.c:390` — and §3 now measures that
signature at 903.674 s, 3/3, stable to 1 ppm. The generalisation is not supported and is now
directly contradicted.

**Corrected wording:** the retraction applies to `a2_power.c:1189`. For
`lte_ml1_common_timer.c:390` the periodicity is *observed and unexplained*, and is an open
measured question, not a falsified claim.

### 6.4 The differential doc's "no FATAL" evidence is weaker than its trust rating implies

`Stock_Android_Live/02_DIFFERENTIAL_DIAG_ANALYSIS.md` is listed in the **trustworthy core** and
concludes "**the modem does NOT crash at 900 s; the failure is 100% AP-side**". Its evidence is:

* Android DIAG capture, **~488 s** (modem 808–1288 s);
* OpenWrt DIAG capture, **~121 s** (modem 848–969 s);
* "No FATAL ERROR messages in either capture".

Two problems:

1. **DIAG dies on every SSR** (already recorded in the device-quirks memory). A fatal *causes*
   an SSR, so the capture stops at the fatal — absence of a FATAL record in a DIAG capture is
   weak negative evidence, and the OpenWrt 121 s figure is suspiciously short for a window that
   is claimed to span modem 848–969 s across a ~903 s fatal.
2. The conclusion happens to be **right**, but the *strong* evidence for it is elsewhere:
   `Stock_Android_Analysis/16_15min_barrier_test_log.txt` — Android at **uptime 921.01 s**,
   i.e. past the ~903 s mark, with `PING 1.1.1.1 → 3 transmitted, 3 received, 0% packet loss`
   and a `dmesg` whose last line is at 117.59 s, meaning **nothing was logged for ~800 s**.

**Action:** keep the conclusion, downgrade the DIAG evidence, and cite the 921 s barrier test as
the primary support. Android genuinely survives the mark; OpenWrt genuinely does not; the
firmware is byte-identical (Doc 143 / `01_ANDROID_LIVE_SESSION_FINDINGS.md`). **The fatal is
therefore AP-dependent** — which is exactly the premise the AP-side effort rests on.

---

## 7. What is still open

1. **Why does the modem fatal on OpenWrt but not Android, with byte-identical firmware?**
   This is now the single most important question, and §6.4 shows the Android side is a valid
   control. Ranked candidates:
   * the OpenWrt `qcom_bam_dmux` driver vs Qualcomm's `bam_dmux` — the A2 power-collapse
     handshake cadence differs (§5);
   * mainline `qcom_q6v5_mss` remoteproc vs Qualcomm `pil-q6v5-mss` (votes, `rpm.sync`);
   * the userspace QMI client set (`libqmi`/ModemManager vs `libqmiservices`/RIL) — note
     `--wds-go-dormant` returning error 25 on this modem;
   * an AP-side RPM/clock handshake the firmware waits on with no timeout
     (`Modem RE/hmu05/900S_CRASH_LPR_FRAMEWORK_RE.md` already identifies `rpm.sync` as the stall).
2. **Why do three signatures show three different ~900 s periods?** Shared underlying counter
   with a different reporting site, or three genuinely different timers? The 1 ppm stability
   within a boot argues for a counter; the cross-boot spread argues against a single constant.
3. **Is the growing collapse duration (§5.1) a precursor?** One observation, obvious confound.
4. **Does the A2 cadence difference (§5) matter?** Needs matched traffic on both sides.
5. Carried over from Doc 147: which mechanism loses the first packet; whether a keepalive with a
   *measured* period is worth reinstating; removing the dead `--wds-go-dormant` Stage 1.

**Next experiment (defined):** with the Android device connected, run a matched idle soak on
both sides and compare (a) `pc_irq_count`-equivalent collapse cadence and (b) whether
`lte_ml1_common_timer.c:390` appears in Android's `dmesg` past 903 s. The Android device was
**not reachable** (`adb devices` empty) when this document was written.

### 7.1 The ERR_FATAL descriptor for this assert is DECODED — from the coredump

The corpus recorded that the descriptor → file/line link "is NOT decoded" and that descriptors
"read as high-entropy obfuscated data even in rodata". **Both are wrong for this descriptor
family.** The modem coredump holds the runtime copy in plaintext, and it matches the formatter
(`0xc08794e0`: line u16 at `+0x00`, inline NUL-terminated filename at `+0x14`) exactly.

File offset `0x03524f40` (VA **0x89db1290**):

```
+0x00  86 01 00 00   u16 line = 0x0186 = 390
+0x02  00 00         pad
+0x04  62 be 78 c7   u32 A   (runtime-varying)
+0x08  12 8c 12 01   u32 B   (runtime-varying)
+0x0c  00 00 00 00
+0x10  00 00 00 00
+0x14  "lte_ml1_common_timer.c\0"
```

The descriptor is **static** — the same file offset in every coredump — yet its payload words
**change between dumps**, i.e. the firmware records live state into it:

| dump (uptime s) | A | B |
| :--- | :--- | :--- |
| 919.52 | 0xb7bb76cf | 0x01128bfc |
| 1000.86 | 0xb7bb76cf | 0x01128bfc |
| 1822.52 | 0xbffa3d53 | 0x01128c07 |
| 2723.69 | 0xc778be62 | 0x01128c12 |
| 3629.79 | 0xce3f7ed6 | 0x01128c1d |

`B` increases monotonically by 0x0b (11) per fatal period; across 2710 s it rises 33, i.e.
**one count per ~82.1 s**. It is *not* a plain tick count (that would scale 1:1 with uptime), so
it is a counter of something with a ~82 s period — a candidate for the quantity the assert
checks. `A` also varies but is not yet interpreted.

**Not resolved:** no pointer to `0x89db1290` exists anywhere in the dump, and `modem.asm` (which
covers only up to `0xc1404e0c`) has no `0x89db1290` / `-0x7624ed70` immediate, so the referencing
site is not yet located and `A`/`B` cannot be interpreted without it.

**Also tried and failed, so it is not repeated:** `lte_ml1_common_timer.c:390` is not in the BSS
line-table family that carries the verified controls (4054/4014/1189) — searching `modem.asm` for
the initialiser form (`rN = #0x186`) gives 4 hits, none a table initialiser. And
`scratch/firmware/modem.asm` contains **no string content at all** (`a2_power.c`,
`lte_ml1_sleepmgr_stm.c`, `mmoc.c`, `lte_ml1_common_timer.c` each appear 0 times), so the
filename-grouping route is unavailable from that artifact. The coredump is the working oracle.

### 7.2 Coredump reading is a disk hazard

`/sys/class/devcoredump/` exposes a **single** device (`devcd4`), and repeated reads return
*changing* data (a live dump). A naive polling loop therefore writes a fresh 85 MB file each
time: the watch used here wrote **122 dumps = 9.6 GB in ~45 min** before being stopped. Bound
the number of captures and prune; one dump per fatal era is sufficient.

---

## 8. Artifacts

| artifact | path |
|---|---|
| live capture (fatal + SSR sequence, telemetry, module info) | `evidence/148_fatal_periodicity/live_capture_20260920.txt` |
| A2 cadence samples + interpretation | `evidence/148_fatal_periodicity/a2_cadence_20260920.txt` |
| fatal signature census | `evidence/148_fatal_periodicity/fatal_signature_census.txt` |
| ERR_FATAL descriptor decode (this assert) | `evidence/148_fatal_periodicity/errfatal_descriptor_decode.txt` |
| coredump descriptor scanner (**tracked**) | `evidence/148_fatal_periodicity/coredump_descriptors.py` |
| modem coredumps (one per fatal era, gitignored) | `scratch/coredump_live/modem_coredump_up{919.52,1822.52,2723.69,3629.79}.elf` |
| Android 921 s barrier test (the real control) | `../Stock_Android_Analysis/16_15min_barrier_test_log.txt` |
| telemetry interface source | `rx_telemetry` = `DEVICE_ATTR_RO` in `msm89xx/patches/808-bam-dmux-stats.patch` |
| driver source of truth | `msm89xx/patches/808-*.patch` + `msm89xx/patches/809-bam-dmux-tx-pm-ordering.patch` |
| deployed watchdog md5 | `2eb113ac623c9b2c339c096c29a43ce6` |
| deployed kernel | `6.12.94` (aarch64, musl) |

---

## 9. One-line summary for the next session

The modem fatals every **903.674 s** this boot (`lte_ml1_common_timer.c:390`, **5/5**, four
intervals spread by 1.08 ppm) — and the fifth fatal was **predicted to ~0.03 s before it
happened**, so this is a deterministic timer, not a rate. Each fatal costs an SSR and a bearer
rebuild, which is what the `wwan0` address churn in Doc 147 §8 actually was, so stop looking for
an AP-side rebuild timer. The corpus conflates **ten** fatal signatures, and retraction #1's
"fixed 900 s is FALSE" is only true for `a2_power.c:1189`; for `lte_ml1_common_timer.c:390` the
periodicity is real. Android at 921 s uptime is healthy with 0 % loss on byte-identical firmware,
so **the fatal is AP-dependent and is the remaining lever**. The right question is now "what does
Android do at least once every 903.674 s that OpenWrt does not?" — not "what is the failure
rate". **New capability:** the ERR_FATAL descriptor for this assert is decoded from a modem
coredump (VA `0x89db1290`, line 390, inline filename, plus two runtime-varying payload words),
which the corpus had recorded as not decodable — §7.1.
