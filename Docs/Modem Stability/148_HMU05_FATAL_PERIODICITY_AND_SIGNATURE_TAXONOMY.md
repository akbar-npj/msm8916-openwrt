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

## 3. The measurement: three fatals, 903.674 s apart

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
```

| quantity | value |
| :--- | :--- |
| interval #1→#2 | **903.673862 s** |
| interval #2→#3 | **903.674838 s** |
| difference between the two intervals | 0.000976 s = **1.08 ppm** |
| signature | `lte_ml1_common_timer.c:390`, **3/3** |
| SSR outage per fatal | ~1.3 s (fatal → modem back up) |
| AP-side oopses this boot | **0** |

Modem bring-up in this boot is at AP 12.092370 s, so on the *modem's* own uptime clock the
fatals land at ≈ **902.68 / 1806.35 / 2710.03 s**.

**A period stable to 1 ppm across two intervals is not a coincidence.** Whatever produces this
is a counter or timer, not a random failure rate. That is the opposite of what the corpus
currently asserts for this family — §6.

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

### 5.2 Two small telemetry facts that weaken an earlier claim

* **`pc_timeout_count: 2`** — the A2 resume ACK *has* timed out twice. Doc 146 §11 hypothesis #2
  was set aside partly on `pc_timeout_count: 0`. That specific ground no longer holds.
* **`pm_suspend_attempts: 202` vs `pm_suspend_completions: 199`** — three suspend attempts never
  completed. Small, but nonzero, and consistent with the two ACK timeouts.

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

---

## 8. Artifacts

| artifact | path |
|---|---|
| live capture (fatal + SSR sequence, telemetry, module info) | `evidence/148_fatal_periodicity/live_capture_20260920.txt` |
| A2 cadence samples + interpretation | `evidence/148_fatal_periodicity/a2_cadence_20260920.txt` |
| fatal signature census | `evidence/148_fatal_periodicity/fatal_signature_census.txt` |
| Android 921 s barrier test (the real control) | `../Stock_Android_Analysis/16_15min_barrier_test_log.txt` |
| telemetry interface source | `rx_telemetry` = `DEVICE_ATTR_RO` in `msm89xx/patches/808-bam-dmux-stats.patch` |
| driver source of truth | `msm89xx/patches/808-*.patch` + `msm89xx/patches/809-bam-dmux-tx-pm-ordering.patch` |
| deployed watchdog md5 | `2eb113ac623c9b2c339c096c29a43ce6` |
| deployed kernel | `6.12.94` (aarch64, musl) |

---

## 9. One-line summary for the next session

The modem fatals every **903.674 s** this boot (`lte_ml1_common_timer.c:390`, 3/3, stable to
1 ppm), each fatal costs an SSR and a bearer rebuild — which is what the `wwan0` address churn
in Doc 147 §8 actually was, so stop looking for an AP-side rebuild timer. The corpus conflates
**ten** fatal signatures, and retraction #1's "fixed 900 s is FALSE" is only true for
`a2_power.c:1189`; for `lte_ml1_common_timer.c:390` the periodicity is real and unexplained.
Android at 921 s uptime is healthy with 0 % loss on byte-identical firmware, so **the fatal is
AP-dependent and is the remaining lever** — with the A2 collapse cadence differing 4.6× as the
leading, unproven candidate.
