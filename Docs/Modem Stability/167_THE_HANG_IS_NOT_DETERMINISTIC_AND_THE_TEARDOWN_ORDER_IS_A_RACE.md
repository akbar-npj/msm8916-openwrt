# Doc 167 — The AP hang is NOT deterministic, and the SSR teardown order is a RACE

**Date:** 2026-09-21
**Device:** HMU05 (MSM8916 + WTR1605 + QFE2320), OpenWrt 25.12.5, Linux 6.12.94
**Module under test:** `qcom_bam_dmux.ko` md5 `eca269f10a685a83a8b679bc7be38d1d` — **pre-820**, so this
boot is the **control** for the 820 test.
**Nothing was built, deployed or changed on the device this round except one read-only instrument
added at the end (see §8).** The finding came from letting boot C keep running.
**Result:** boot C ran to **2181.14 s** through **two** fatals and **recovered from both**, while boot
B hung at a fatal **2.989 s later in AP time**. **The hang is a race, not a schedule.** Two further
results: the relative order of the bam_dmux teardown work and the rpmsg/SMD teardown **flips within a
single boot** (so Doc 166 §2.2c's ordering *argument* is wrong even though its conclusion survives),
and **Doc 165's "one fatal → two MBA reloads" is confirmed exactly** (106 = 14 + 2 × 46 trace lines).
The consequence that matters is §7: **a ~5 % race cannot be validated by one soak.**

---

## 0. SOP compliance

| SOP step | Status |
|---|---|
| No baseband change; no firmware write | **Honoured.** Zero firmware bytes touched. Device-side writes: **one** — the beacon (§8), which is read-only with respect to the modem and the kernel. |
| Dual-firmware comparative protocol | **Not applicable, and deliberately so** — same reasoning as Docs 164 §0 / 165 §0 / 166 §0. This is AP-side kernel behaviour; no baseband hypothesis is advanced, so there is nothing to compare against Android. |
| Verify against ground truth before acting | **Honoured.** Every number here is read from the **live device** (`dmesg`, `/proc/uptime`, `/overlay/q6trace.csv`, `/overlay/coredump_watch.log`) or from a coredump decoded by Doc 163's own tool. The trace structure was counted, not assumed. |
| Hash the artifact, verify it in situ | **Honoured.** `qcom_bam_dmux.ko` = `eca269f1…` (pre-820) and `qcom_q6v5_mss.ko` = `79e7a858…` (patch-817 tracer) — **named per module**, per Doc 166 §11.1's trap. |
| Never classify a fatal by its `file:line` | **Honoured — and this round shows why the *signature* still matters even though it is not the root cause.** The two signatures are used here only as **identity labels** for two populations of SSR (§6), which is Doc 162 §7's refinement applied, not violated. |
| Do not treat corpus docs as fact | **Honoured, and two corpus claims were corrected by measurement:** Doc 166 §2.2c's ordering argument (§3) and Doc 163's "B advances +11 per fatal" as a *cross-boot* statement (§5). Both were previously marked as verified. |
| Record what was done, the result, and what is next | This document, plus §9 and §10. |

---

## 1. What happened — boot C simply kept running

Boot C was left soaking after Doc 166. It had already produced one fatal (AP 916.536 s) at the time
Doc 166 was written, with **zero** hangs. It then produced a **second** fatal:

| | boot C |
|---|---|
| AP uptime at last sample | **2181.14 s** |
| fatals | **2** |
| oops / `Unable to handle` | **0** |
| hangs | **0** |
| coredumps created | **2** (both fatals) |
| `q6v5-trace` lines | **106** |
| `guard_hits` | **0** throughout |
| `defer_wipe_live` | **0** throughout |

| # | AP uptime | dmesg signature | coredump | B | modem up again at | implied modem uptime |
| --: | --: | :-- | :-- | :-- | --: | --: |
| 1 | **916.535985** | `lte_ml1_sleepmgr_stm.c:4054` | `up918.22_devcd1` | `0x01128E3D` | 917.915316 | ≈ **901.6 s** |
| 2 | **1837.400528** | `a2_power.c:1189` | `up1840.93_devcd2` | `0x01128E48` | 1840.136726 | ≈ **919.5 s** |

Both coredumps decode (Doc 163's `errfatal_descriptor.py`) to exactly the dmesg signature — a
**12th and 13th** independent confirmation of Doc 163's inline-filename claim, and the first for the
`a2_power.c:1189` signature on a boot other than run 8.

---

## 2. THE HEADLINE — the hang is a race, not a schedule

| | **boot B** (Doc 166) | **boot C** (this round) |
|---|---|---|
| fatal #1 | AP 937.786016 — `a2_power.c:1189` | AP 916.535985 — `lte_ml1_sleepmgr_stm.c:4054` |
| fatal #2 | AP **1840.390188** — `lte_ml1_sleepmgr_stm.c:4054` | AP **1837.400528** — `a2_power.c:1189` |
| fatal #2 outcome | **HANG** (no coredump; `rproc_stop()` never returned) | **RECOVERED** (coredump at +1.7 s; modem up at 1840.136726) |
| Δ(fatal2 − fatal1) | 902.604 s | 920.865 s |
| coredumps | 1 of 2 | **2 of 2** |

**The two second-fatals are 2.989 s apart in AP time and their outcomes are opposite.**

Boot B's second fatal landed at **1840.390 s**; boot C's second fatal landed at **1837.401 s**. On the
AP-uptime axis these are the *same event*, and one hung while the other did not. **Whatever hung boot
B is not a deterministic function of the fatal, of the AP time, or of "the second fatal of a boot".**

Three consequences, all of which change how the remaining work must be framed:

1. **The 900 s / 1840 s framing is a red herring for the hang.** The fatal times are highly
   reproducible (§1 — 901.6 s and 919.5 s of modem uptime, both in their documented bands), but the
   *hang* is not. The reproducibility of the trigger has been masking the randomness of the failure.
2. **Patch 820 cannot be validated by a single soak.** A race that fires once in ~20–40 SSRs needs
   many SSRs, or a forced reproduction — §7.
3. **A negative result is weak evidence here.** "No hang in this boot" was already true of boot C
   *before* 820, so it cannot be evidence for 820. Boot C is the **pre-820 control** (§8).

**Signature correlation, offered as a hypothesis and not a finding (n = 2 per cell):**

| signature | SSRs observed | hangs |
|---|--:|--:|
| `lte_ml1_sleepmgr_stm.c:4054` | 2 (boot B #2, boot C #1) | **1** |
| `a2_power.c:1189` | 2 (boot B #1, boot C #2) | **0** |

A sleep-state-machine fatal plausibly leaves the A2 power-collapse state machine in a different state
from an `a2_power` fatal, so a signature-dependent race is mechanistically credible — **but n = 2 per
cell is nothing, and this must not be treated as established.** It is recorded because it is
cheap to score: every future fatal carries its signature in dmesg for free.

---

## 3. The teardown order is a RACE — Doc 166 §2.2c's *argument* is wrong (its conclusion survives)

Doc 166 §2.2c reasoned from a single healthy sample that the order is
`SSR before shutdown` → `wwan0at0 disconnected` → `executing serialized asynchronous SSR teardown`,
and concluded that our hang (which ends on **one** line) is **one step earlier** than Doc 151/157's
`echo stop` hang (which ends on **two**).

**Boot C produced both orders, within one boot:**

```
--- fatal #1 (AP 916.536) ---
916.559262  bam_dmux: SSR before shutdown: scheduling teardown work
916.560999  wwan wwan0: port wwan0at0 disconnected                     (+1.737 ms)
916.561318  wwan wwan0: port wwan0at1 disconnected
916.562551  bam_dmux: executing serialized asynchronous SSR teardown   (+3.289 ms)
916.564196  wwan wwan0: port wwan0qmi0 disconnected
916.564625  q6v5-trace: 01 disable_qchannel mdm

--- fatal #2 (AP 1837.401) ---
1837.422750  bam_dmux: SSR before shutdown: scheduling teardown work
1837.423524  bam_dmux: executing serialized asynchronous SSR teardown  (+0.774 ms)
1837.426472  wwan wwan0: port wwan0at0 disconnected                    (+3.722 ms)
1837.429164  wwan wwan0: port wwan0at1 disconnected
1837.429724  wwan wwan0: port wwan0qmi0 disconnected
1837.434680  q6v5-trace: 01 disable_qchannel mdm
```

**The bam_dmux teardown work and the rpmsg/SMD teardown are concurrent, and which one prints first
depends on which thread is scheduled first.** `schedule_work(&dmux->ssr_teardown_work)` runs in the
notifier; the rpmsg teardown runs from the stop path. Their relative order is decided by
`system_wq` versus the stopping thread.

**So "ends on two lines vs one line" does not establish a step ordering**, and Doc 166 §2.2c's
inference is retracted as an argument. **Its conclusion nevertheless survives, for a better reason:**

* In **boot B's hang, *neither* line appeared.** Since the rpmsg teardown happens *inside* the
  `q6v5_stop()` path — i.e. **after** the notifier returns — a missing `wwan0at0 disconnected` means
  `rproc_stop()` never got past the notifier at all.
* In **the `echo stop` hang, `wwan0at0 disconnected` *did* appear.** So there `rproc_stop()` *did*
  proceed past the notifier; only bam_dmux's **teardown work** failed to print.

**The right discriminator is therefore *which* line is missing, not how many lines appeared:**

| hang | `executing serialized…` (bam_dmux work) | `wwan0at0 disconnected` (rpmsg, inside the stop) | reading |
|---|:--:|:--:|---|
| boot B's spontaneous hang | missing | missing | at or before the notifier's hand-off — `rproc_stop()` never continued |
| Doc 151/157's `echo stop` hang | missing | **present** | `rproc_stop()` continued; **bam_dmux's teardown work specifically blocked** |

That is a *cleaner* separation than the original one, and it **points the two hangs at different
suspects**: the `echo stop` hang at the teardown work (i.e. exactly §8's site 1 / site 2), and the
spontaneous hang at the notifier or something upstream of it. The earlier conclusion — our hang is
earlier — is preserved.

---

## 4. Doc 165 confirmed exactly: 106 = 14 + 2 × 46

Doc 165 established that `q6v5_mba_load()` has **two** callers — `q6v5_start()` and
`q6v5_reload_mba()`, the latter reached only from `qcom_q6v5_dump_segment()` — so **one fatal
produces TWO MBA boot/reclaim pairs**, i.e. four half-cycles `01-09, 10-23, 01-09, 10-23`.

Boot C's trace log, in order, is:

```
10 11 12 13 14 15 16 17 18 19 20 21 22 23          <- 14: the boot's own bring-up
01 02 03 04 05 06 07 08 09 10 ... 23               <- 23: fatal #1, cycle 1
01 02 03 04 05 06 07 08 09 10 ... 23               <- 23: fatal #1, cycle 2
01 02 03 04 05 06 07 08 09 10 ... 23               <- 23: fatal #2, cycle 1
01 02 03 04 05 06 07 08 09 10 ... 23               <- 23: fatal #2, cycle 2
```

**14 + 4 × 23 = 106 — exactly the measured count.** The CSV shows the same thing at its own
resolution: `traces` goes **14 → 60 → 106**, i.e. **+46 per fatal**, never +23.

This is an independent, quantitative confirmation of Doc 165 from a different boot, with **two**
fatals, using the trace counter rather than a manual log reading. **One fatal = two full 01..23
cycles. A boot with n fatals has 14 + 46n trace lines.**

---

## 5. `B` is not a fatal counter across boots (corrects Doc 163)

Doc 163 recorded that the ERR_FATAL descriptor's word `B` advances **+11/+12 per fatal** across run
8's five fatals. Boot C tests that two ways.

**Within a boot: confirmed, on a different boot and a different signature pair.**

```
up918.22    lte_ml1_sleepmgr_stm.c  4054   B = 0x01128E3D
up1840.93   a2_power.c              1189   B = 0x01128E48     dB = +11
```

**Across boots: it does not hold as a count.**

| | B |
|---|---|
| run 8, fatal #5 (AP 4647.902) | `0x01128E13` |
| boot C, fatal #1 (AP 916.536) | `0x01128E3D` |
| | **Δ = +42** |

Between those two dumps the boot history is known exactly from `/overlay/coredump_watch.log`
(run 8 = the `pid=3252` boot, then boot B = `pid=3199`, then boot C = `pid=3746`, **with no boot in
between**), and it contains **three** fatals: boot B #1, boot B #2 (**the hang**), and boot C #1.
Three fatals predict **+33 to +36**. **Observed +42 — an unexplained excess of +6 to +9.**

**The mechanism is unresolved and I am not going to guess it.** What is actionable is the rule:

> **`B` advances ≈ +11 per fatal *within* a boot, and is monotonic across boots, but its cross-boot
> increment is *larger* than the fatal count explains. Do not use `B` to count fatals, and do not use
> it to detect missed coredumps.** Doc 163's within-boot claim stands; its implicit cross-boot use
> does not.

A separate observation from the same two dumps: the RPM LPR counter (`lpr ctr`) went **1437 → 1487**,
i.e. **+50 between two consecutive fatals**, while `B` went +11. Recorded, not interpreted.

---

## 6. Telemetry: patch 812 at scale again, on a second boot

From `/overlay/q6trace.csv` (`defer_q, defer_sub, defer_keep, defer_wipe_live, guard_hits`):

| AP uptime | defer_q | defer_sub | defer_keep | **defer_wipe_live** | **guard_hits** | pc_resync | cmd_open | rx_mapped |
| --: | --: | --: | --: | --: | --: | --: | --: | --: |
| 905.67 | 126 | 126 | 115 | 0 | 0 | 8 | 8 | 32 |
| 915.89 | 127 | 127 | 116 | 0 | 0 | 8 | 8 | 32 |
| 921.00 | 127 | 127 | 116 | 0 | 0 | 8 | **16** | 32 |
| 941.97 | 128 | 128 | 117 | 0 | 0 | 8 | 16 | 32 |
| 1827.81 | 253 | 253 | 235 | 0 | 0 | **18** | 16 | 32 |
| 1838.22 | 255 | 255 | 235 | 0 | 0 | 18 | 16 | **0** |
| 1844.06 | 255 | 255 | 235 | 0 | 0 | 18 | **24** | 32 |
| 1865.56 | 256 | 256 | 236 | 0 | 0 | 18 | 24 | 32 |

* **`defer_q == defer_sub` at every sample** and **`defer_wipe_live` = 0 throughout** — **256 deferred
  packets, 256 submitted, 0 destroyed.** Doc 156's patch 812 holds at scale on a second boot, and now
  across two SSRs. `defer_keep` tracks it (115 → 236).
* **`guard_hits` = 0 throughout** — Doc 155's patch 810 guards never tripped, across two fatals.
* **`cmd_open` steps 8 → 16 → 24**, i.e. **8 more command channels opened per recovery** — the SSR
  rebuild is real and repeatable.
* **`rx_mapped` dips 32 → 0 → 32** across fatal #2 — RX slots unmapped by the teardown and re-mapped
  by the rearm. Visible because the sampler happened to land inside the window.
* **`pc_resync` steps 8 → 18** between the fatals — 10 resyncs during ordinary operation, unrelated
  to the fatal. Worth knowing so it is not misread as a precursor.

Also present in dmesg and worth recording as a **precursor that is not one**: `bam_dmux: refusing to
queue command while modem is collapsed` appears at **916.628644** (fatal #1) but **not** around fatal
#2. Doc 165 already showed this to be a *symptom*, not a precursor; this is a second data point.

---

## 7. The consequence that matters: a ~5 % race cannot be validated by one soak

Count the hang rate from the only unbiased instrument — `/overlay/coredump_watch.log`, which records
a dump per recovered fatal and is **silent** for a fatal that hangs:

* The log holds **21 watcher-start lines and 39 dumps** — 39 recovered SSRs.
* Hangs in that window: **boot B's fatal #2** (1), plus Doc 159's 20-of-21 result (1 in 21).

So the hang rate is of order **1 in 20 to 1 in 40 SSRs — roughly 3–5 %.**

**What that means for testing 820.** With a 5 % baseline rate, "zero hangs observed" only becomes
evidence at n ≈ 60 SSRs (rule of three: 3/n < 0.05 ⇒ n > 60). Boot C delivers **~2 SSRs per 35 min**.
**60 SSRs is ~18 hours of continuous soak.** And boot C already shows that 2 clean SSRs mean nothing —
it produced them *without* 820.

**Therefore, in priority order:**

1. **Do not claim 820 works on the basis of a short soak.** Pre-register the sample size *now*: a
   positive claim needs **≥ 60 SSRs with zero hangs**, or a *forced* reproduction (§7.1).
2. **Score the T0–T4 probes on every SSR, not just on hangs.** They fire on healthy SSRs too, so
   every fatal gives a free check that the hand-off behaves as modelled — and a `T3`-without-`T4`
   on a *healthy* SSR would be direct evidence of the ABBA being reachable.
3. **Separate the two questions.** "Does the hang still happen?" (needs n ≥ 60) and "does the
   teardown work now complete?" (needs n = 1, because `T1..T4` appear or they do not) are different
   questions with wildly different costs. **The cheap one can be answered in one boot.**

### 7.1 The obvious way to get power: force the SSR

The fatal rate is fixed by the modem (~1 per 900 s of modem uptime), so SSRs cannot be made cheaper
by traffic. But **an SSR does not require a fatal** — `rproc_stop()` can be driven directly. Memory
quirk 11 records that `echo stop > /sys/class/remoteproc/remoteproc0/state` **hangs the AP** on this
device, and §3 now shows that hang is a **different** one (the teardown work, not the hand-off).
That is a *separate* defect with its own value, but it is **not** a usable accelerator for this race —
**and it must not be used as a soak loop**, because a hung AP ends the run.

So the honest position is: **the race can only be sampled, not forced, with the instruments we have.**
A forcing method (e.g. a kernel-side loop that raises the notifier under controlled conditions) is
possible but is a bigger project than 820; it is recorded in §10 as an open option, not a plan.

---

## 8. Boot C is the pre-820 CONTROL — and it is the reason "no hang" is not evidence

Boot C's module is **`eca269f10a685a83a8b679bc7be38d1d` — pre-820** (verified: `strings | grep -c
"SSR teardown T"` = 0). It produced **two SSRs and zero hangs.**

So the correct framing for the upcoming 820 run is a **before/after on the same protocol**, not
"820 was deployed and the AP stopped hanging":

| | boot B | boot C | 820 run |
|---|---|---|---|
| module | pre-820 | pre-820 | **post-820** |
| fatals | 2 | 2 | ? |
| SSRs recovered | 1 of 2 | **2 of 2** | ? |
| hang | **yes** (fatal #2) | no | ? |

**A single clean 820 boot would sit exactly where boot C already sits.** That is the whole point of §7.

---

## 9. The one device change this round

`/overlay/beacon.sh` (md5 `721feffbe518ec0dbe432ddcf069fee7`), autostarted from `/etc/rc.local`
(md5 `3f58254193bc47c2c0c6f026e087b9a6`), implementing Doc 166 §13 item 3 and justified structurally
by §5.5: both pre-existing sinks are `/dev/kmsg` readers, and `devkmsg_read()` **sleeps on
`log_wait`** when the ring is quiet, so neither can distinguish "the kernel has nothing to say" from
"the AP is dead". The beacon reads **only `/proc/uptime`** and appends to a file, in two independent
processes writing two files — one calling `sync()`, one not — so "the writeback path wedged" is
separable from "the process died".

Verified running on boot C (rows advancing ~2 s apart, the syncing process lagging the other by ~1.3 s
as designed). **It has no role in this round's findings**; it is in place so that the *next* hang is
decidable.

---

## 10. Next

1. **Deploy patch 820 and pre-register the sample size** — a positive claim needs **≥ 60 SSRs with
   zero hangs** (§7). Until then, report `SSRs recovered: n` and nothing stronger.
2. **Score T0–T4 on every SSR**, healthy or not. One SSR settles "does the teardown work now
   complete?" (§7 item 3).
3. **Score the signature hypothesis of §2** for free: every future fatal's signature is in dmesg.
   Record `(signature, recovered|hung)` for each.
4. **Re-run 820 without the `dev_err` probes** before claiming the cancels fixed anything — Doc 166
   §9.1 measures T0 at **≈ 9.6 ms** of console time on `rproc_stop()`'s critical path, larger than the
   **7.453 ms** healthy hand-off it sits in. A disappearance has two candidate causes.
5. **`B`'s cross-boot excess (+42 vs +33..+36) is unexplained** (§5). Cheap to chase: decode the
   remaining dumps in `scratch/coredump_live/` and tabulate `B` against a known fatal count.
6. **The `echo stop` hang is now a *different* defect from the spontaneous one** (§3). It points at
   bam_dmux's teardown work specifically, which is where 820's fixes are — so **820 may well fix
   `echo stop` while leaving the spontaneous hang untouched**, and the two must be reported
   separately.
7. **Still open from before:** the failed modem restart (Doc 154 §6); patch 814's retry path still
   unobserved on a natural trigger (`retries: 0`); the reset mechanism (silent, ~2–4 s, does not
   match Doc 159's 30 s WDT); the harness gap that **nothing records the AP uptime at which the modem
   comes up** — §1 had to derive it from the `is now up` line by hand.
8. **Explicitly closed — do not restart:** the WTR1605→UFI001B RF transplant; the one-line
   `qcom-idle-state-spc` DT patch; "the RPM is the stalled party"; the live-mpss reader (Doc 158);
   the `deploy` command (declined); the `qcom-time-daemon` periodic-refresh lead (Doc 166 §7.1).

---

## Evidence

`evidence/167_hang_not_deterministic/`

| file | contents |
|---|---|
| `boot_c_fatals_and_order.txt` | boot C's dmesg extracts: both hand-offs in full, the 106-step trace sequence, the counts, and the recovery lines |
| `q6trace_boot_c.csv` | the soak's full telemetry CSV for boot C (the `traces` column is the 14 → 60 → 106 evidence) |
| `errfatal_boot_c.txt` | Doc 163's decoder output for both boot C coredumps (`dB = +11`) |
| `coredump_watch_log.txt` | the watcher's full log — 21 boots, 39 dumps — which is how §7 counts the hang rate |

Coredumps (not committed; 85 398 475 B each, md5-verifiable):
`modem_coredump_up918.22_devcd1.elf`, `modem_coredump_up1840.93_devcd2.elf`.
