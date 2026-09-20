# DOC-140 — BAM-DMUX: The Two Runtime-PM Defects That Made A2 Power Collapse Impossible

**Status:** 🔴 **PARTIAL — mechanism fixed and verified, but the decisive test FAILED.**
The runtime-PM defects are real and are now fixed (E1–E6, E8, E9 all pass), **but the firmware
still faults at 912 s (E7 FAIL)**. The fix changes the *consequence* (automatic recovery instead
of a 174 s data outage) but not the *trigger*. See §10.3 and §14.
**Related:** Doc 139 (patch 808 Android parity audit), Doc 132 (15-min stall progress log),
Doc 81/85/87 (earlier 15-min crash parity specs), `Stock_Android_Analysis/62` (kernel drivers RE —
**its autosuspend conclusion is wrong, see §6**), `Stock_Android_Live/03` (BAM-DMUX differential)
**Patch:** `msm89xx/patches/808-bam-dmux-stats.patch` (29 hunks, md5 `b4942b29bf2ddf2369d713b022cbd922`)
**Module:** `qcom_bam_dmux.ko` sha256 `a75fef2297f541d210c403846d25084f460ffa54c053887c3ec415bf19826b60`,
srcversion `92F15578020994438860CE5`, `.text` = 0x3380 (13184)
**Date:** 2026-09-20

---

> **UPDATE 2026-09-20 (Doc 146) — one correction, one addition.**
> - **§10.5 ("The fault lands exactly 900 s after modem power-up") is RETRACTED.** Measured
>   `a2_power.c:1189` fatal times are 172.353 / 918.195 / 1823.753 / 2729.267 s, and one boot
>   produced a single fatal at 172.353 s with none through 1156.95 s. There is no fixed timer.
>   Treat the fault as a **rate**, not a period. Everything else in this document stands.
> - **The RTNL-leak oops in Doc 144 §8 is now root-caused and fixed.** `bam_dmux_send_cmd()`
>   reserved a `tx_skbs[]` slot *before* `pm_runtime_get_sync()`; the resume path
>   (`bam_dmux_pc_irq` → `bam_dmux_pm_restart`) then freed that slot, so
>   `bam_dmux_skb_dma_map()` dereferenced `skb_dma->skb == NULL`. Fixed by taking the PM
>   reference first (both `send_cmd` and `start_xmit`); verified 8/8 `wwan0` down/up cycles clean.
>   See Doc 146 §5.

## 1. Purpose

The user's question was: *"Android runs without any firmware modification — what do we lack on
the AP side that makes it data stall at 15 minutes?"* and then *"an SSR will delay the data
reception for 5–15 seconds, that's unacceptable."*

This document records the answer, the two code defects that were the actual cause, the exact
changes made, and — most importantly — the **falsifiable expected result** so that the fix can
be judged on evidence rather than on a plausible story.

---

## 2. Android's actual mechanism (ground truth)

The Android 4.4.4 ZTE msm8916 source is at
`GitIgnore/android_kernel_zte_msm8916/drivers/soc/qcom/bam_dmux.c`. It has **zero runtime PM**
(`grep -c pm_runtime` = 0). Instead it drives the A2 power-collapse handshake explicitly from
the data path:

| Element | Location | Behaviour |
| :--- | :--- | :--- |
| `UL_TIMEOUT_DELAY` | `:235` | **1000 ms** |
| `ul_timeout()` | `:1776` | delayed work; if no TX packet was written and there is no on-demand vote → `ul_powerdown()` |
| `ul_powerdown()` | `:1692` | `wait_for_ack = 1; power_vote(0); bam_is_connected = 0` |
| `power_vote()` | `:1672` | `vote=1` → **sets** `SMSM_A2_POWER_CONTROL`; `vote=0` → **clears** it |
| `ul_wakeup()` | `:1840` | `power_vote(1)`, then a 3-stage ack wait at `UL_WAKEUP_TIMEOUT_MS` = 2000 ms (`:237`) |
| `ssrestart_check()` | `:1819` | each ack timeout escalates to `subsystem_restart("modem")` — called from **10 sites** |
| `ul_timeout_work` | `:2708` | `INIT_DELAYED_WORK` |

**Net effect:** while the bearer is up, Android clears `SMSM_A2_POWER_CONTROL` one second after
the last uplink packet, permitting the modem to power collapse; the next TX sets it again.

**Measured on the live stock Android device:** `rpm_master_stats` → `MPSS numshutdowns: 0x396`
= **918 modem power-collapse shutdowns in 53.8 min** (~17/min). This is the continuous behaviour
that OpenWrt was not reproducing. (See `Stock_Android_Live` and memory note
"Android has NO 15-min timer — the 900s protection is continuous".)

OpenWrt's driver *appeared* to implement the same thing — `bam_dmux_runtime_suspend()` calls
`bam_dmux_pc_vote(dmux, false)`, which clears the same SMSM bit — with a 1000 ms autosuspend
delay. The code comment at `bam_dmux_netdev_open()` even claimed so. **It never ran.**

---

## 3. Defect 1 — `bam_dmux_netdev_open()` pinned the device active forever

```c
	ret = pm_runtime_resume_and_get(bndev->dmux->dev);   /* +1 usage ref */
	...
	netif_start_queue(netdev);
	/* pm_runtime_get in open keeps the device active; autosuspend will
	 * properly vote power off after 1s idle, matching Android ul_timeout. */
	return 0;                                            /* ref never released */
```

`pm_runtime_resume_and_get()` takes a **usage reference**. Nothing released it until
`bam_dmux_netdev_stop()`, so for the entire lifetime of the bearer `pm_usage_count` was pinned
at 1 and the autosuspend timer could never expire.

**Live proof (pre-fix, 26 minutes uptime):**

```
pm_suspend_attempts: 1        <-- one, ever
pm_suspend_completions: 1
pm_usage_count: 1             <-- pinned
runtime_status: active
```

So `bam_dmux_runtime_suspend()` → `bam_dmux_pc_vote(dmux, false)` →
`qcom_smem_state_update_bits(dmux->pc, dmux->pc_mask, 0)` **never executed**. The AP never once
cleared `SMSM_A2_POWER_CONTROL`, i.e. never permitted the modem to power collapse, for the whole
session.

Note the comment and the code contradict each other: the author's *intent* was Android parity,
the *implementation* was the opposite.

---

## 4. Defect 2 — freed TX skbs leaked their runtime-PM reference

Releasing the `open()` reference was necessary but **not sufficient**. Sampling the telemetry
every second from boot caught the next failure:

```
uptime 48 s  u=0  st=suspended  att=0  done=0  res=0  txp=0    <-- working
uptime 52 s  u=0  st=active     att=0  done=0  res=1  txp=0
uptime 56 s  u=8  st=active     att=1  done=1  res=2  txp=12   <-- pinned, never recovers
```

`pm_usage_count` jumped **0 → 8** and stayed there for the rest of the boot (verified: still
exactly 2, then 8, after 60 s of completely quiet traffic).

**Cause.** Every skb sitting in `tx_skbs[]` holds exactly one runtime-PM reference, taken by
`bam_dmux_start_xmit()` (`pm_runtime_get`) or `bam_dmux_send_cmd()` (`pm_runtime_get_sync`),
and normally released by `bam_dmux_tx_done()` (`:278-279`) from the DMA completion callback.

`bam_dmux_free_skbs()` dropped those skbs **without releasing the references**:

```c
		if (skb_dma->skb) {
			dev_kfree_skb(skb_dma->skb);
			skb_dma->skb = NULL;      /* reference still held */
		}
```

It is called with `dmux->tx_skbs` from **both** `bam_dmux_power_off()` (`:1264`) and
`bam_dmux_pm_restart()` (`:1435`) — i.e. **once per power-collapse cycle**. Because
`dmaengine_terminate_sync()` frees in-flight descriptors *without invoking their callbacks*
(the pre-existing defect documented in Doc 139), `bam_dmux_tx_done()` never runs for them, so
the reference never comes back. Eight queued TX skbs → eight leaked references → autosuspend
stops forever after the first collapse.

**This is why fixing Defect 1 alone only bought two suspend cycles.** The two defects are
compounding: Defect 1 prevented collapse, and once collapse started working, Defect 2 killed it
again.

---

## 5. Why three earlier "fixes" appeared to work

`git log` on the two runtime-PM scripts shows an oscillation, each commit claiming to resolve
the *same* 15-minute freeze with a **different, mutually contradictory** setting:

| Commit | Setting | Claim |
| :--- | :--- | :--- |
| `2542425` | `control=on`, `autosuspend=-1` | "lock BAM-DMUX runtime PM to active" |
| `c2da52e` | `control=auto`, `delay=30000` | "proven 3-hour stability configuration" |
| `88bcac6` | `control=auto`, `delay=1000` | "validated … 21+ minutes, 0 SSRs" |

All three were tuning a mechanism that was **inert regardless of the value**, because
`pm_usage_count` was pinned (§3). The oscillation itself is the evidence: when a value is
irrelevant, any value "works" until it doesn't.

A subsequent soak on `88bcac6` measured an SSR at uptime **802 s**, contradicting its "0 SSRs"
claim — consistent with the earlier runs being lucky rather than fixed.

**Consequence for the shipped configuration:** `control=auto` + `1000 ms` is in fact the
*Android-correct* setting (it matches `UL_TIMEOUT_DELAY`). The two scripts that enforce it
(`etc/init.d/hmu05-modem-pm`, `usr/sbin/modem-bearer-watchdog` §"Ensure BAM-DMUX maintains
1000ms autosuspend") were **right and Doc 62 was wrong**. They needed no change — they needed
the reference leaks fixed so the value could take effect.

---

## 6. Correction to Doc 62

`Stock_Android_Analysis/62_STOCK_ANDROID_KERNEL_DRIVERS_RE_REPORT.md` §5.3 and its conclusion
state that mainline `qcom_bam_dmux` "had the 1000ms autosuspend bug, which our Tier 2 fix
resolved by forcing `control=on` and `autosuspend_delay_ms=-1`, fully replicating stock Android
stability".

**This is backwards.** Android implements a 1000 ms *idle powerdown*; `control=on` (never
suspend) is the opposite of Android. Do not treat Doc 62's autosuspend row as authoritative.

---

## 7. Hypotheses investigated and disproven

Recording these so they are not re-chased.

**7.1 DFAB / XO clock voting — DISPROVEN.** Android's driver does
`clk_get(&pdev->dev, "bus_clk")` / `"xo"` (`bam_dmux.c:2663` / `:2658`) and `vote_dfab()`
enables both, which looked like a real AP-side difference (OpenWrt has zero clock handling).
But the Android DTS node `qcom,bam_dmux@4044000`
(`arch/arm/boot/dts/qcom/msm8916.dtsi:761`) declares **no `clocks` property**, and no
board-level ZTE DTS overrides it. Both `clk_get()` calls therefore fail, are stored as NULL,
and `vote_dfab()` / `unvote_dfab()` are **no-ops on Android too**. Not a difference.

**7.2 The `IFF_UP` guard was not the blocker — CORRECTED.** Patch 808 removed
`if (dmux->netdevs[i]->flags & IFF_UP) return -EBUSY` from `runtime_suspend`, which did not
make autosuspend work. The blocker was the usage reference (§3). (The guard's removal was still
correct — Android's `ul_timeout` powers down regardless of interface state.)

**7.3 Autosuspend value tuning (`on` / `30000` / `1000`) — MOOT.** See §5.

---

## 8. The changes made

Two edits in `qcom_bam_dmux.c`, both in patch 808.

### 8.1 `bam_dmux_netdev_open()` / `bam_dmux_netdev_stop()`

```diff
 	netif_start_queue(netdev);
-	/* pm_runtime_get in open keeps the device active; autosuspend will
-	 * properly vote power off after 1s idle, matching Android ul_timeout. */
+	/*
+	 * Release the open-time reference immediately so the 1000 ms autosuspend
+	 * timer can actually run.  Holding it here pinned pm_usage_count at 1 for
+	 * as long as the interface stayed up, so bam_dmux_runtime_suspend() never
+	 * executed and SMSM_A2_POWER_CONTROL was never cleared: the AP never
+	 * permitted the modem to power collapse.
+	 *
+	 * Android does the equivalent in ul_timeout(): 1000 ms after the last TX
+	 * it calls ul_powerdown() -> power_vote(0), clearing SMSM_A2_POWER_CONTROL
+	 * so the A2 can collapse, and ul_wakeup() -> power_vote(1) re-arms it on
+	 * demand.  Here, RX/TX activity re-arms the timer through
+	 * pm_runtime_mark_last_busy(), and the TX paths re-acquire with
+	 * pm_runtime_get*() (balanced by bam_dmux_tx_done()).
+	 */
+	pm_runtime_mark_last_busy(bndev->dmux->dev);
+	pm_runtime_put_autosuspend(bndev->dmux->dev);
 	return 0;
 }
```

and, because `open()` no longer holds a reference across the interface lifetime, the matching
put in `stop()` had to be removed (it would otherwise underflow):

```diff
 	netif_stop_queue(netdev);
 	bam_dmux_send_cmd(bndev, BAM_DMUX_CMD_CLOSE);
+	/*
+	 * open() no longer holds a reference across the interface lifetime, so
+	 * there is nothing to put here.  Just restart the autosuspend timer;
+	 * the reference taken by bam_dmux_send_cmd() above is released by
+	 * bam_dmux_tx_done() when the CMD_CLOSE descriptor completes.
+	 */
 	pm_runtime_mark_last_busy(bndev->dmux->dev);
-	pm_runtime_put_autosuspend(bndev->dmux->dev);
 	return 0;
 }
```

### 8.2 `bam_dmux_free_skbs()`

```diff
 		if (skb_dma->skb) {
 			dev_kfree_skb(skb_dma->skb);
 			skb_dma->skb = NULL;
+			/*
+			 * Every skb sitting in tx_skbs[] holds exactly one
+			 * runtime-PM reference, taken by bam_dmux_start_xmit()
+			 * or bam_dmux_send_cmd() and normally released by
+			 * bam_dmux_tx_done() from the DMA completion callback.
+			 * Dropping the skb here means that callback will never
+			 * run for it, so release the reference explicitly.
+			 * Without this the count leaks one per dropped skb and
+			 * pins pm_usage_count above zero, which stops
+			 * autosuspend -- and with it the SMSM_A2_POWER_CONTROL
+			 * release -- permanently.
+			 */
+			if (dir == DMA_TO_DEVICE) {
+				pm_runtime_mark_last_busy(skb_dma->dmux->dev);
+				pm_runtime_put_autosuspend(skb_dma->dmux->dev);
+			}
 		}
```

**Nothing else was changed.** In particular `control=auto` / `autosuspend_delay_ms=1000` was
left as-is (§5), and `bam_dmux_pm_restart()`'s earlier channel-rebuild fix was left intact.

---

## 9. Expected result (falsifiable)

If the diagnosis is correct, the following must hold. Each is directly measurable.

| # | Prediction | Instrument |
| :--- | :--- | :--- |
| E1 | `pm_suspend_attempts` climbs continuously (order **tens per few minutes**), not ~1 per 26 min | `rx_telemetry` |
| E2 | `pm_usage_count` returns to and stays at **0** | `rx_telemetry` |
| E3 | `pc_vote_tx_count` ≈ `pc_unvote_tx_count`, both climbing | `rx_telemetry` |
| E4 | `pc_timeout_count` stays **0** — every A2 handshake completes | `rx_telemetry` |
| E5 | `pc_irq_count` == `pc_ack_irq_count` — every modem PC interrupt is acked | `rx_telemetry` |
| E6 | Collapse rate is within an order of magnitude of Android's ~17/min | derived |
| E7 | **No `a2_power.c:1189` fatal error past ~1100 s** (previous crashes: 802 s, and ~910 s RX-stop → 1084 s fatal) | `dmesg` |
| E8 | Continuous ping success through and beyond the 15-minute boundary | `ping -I wwan0` |
| E9 | No `pc-ack timeout` / `pc_state wait timeout` warnings | `dmesg` |

**The decisive one is E7.** E1–E6, E8, E9 establish that the mechanism now runs; E7 establishes
that it satisfies the firmware's requirement. A pass on E1–E6 with a fail on E7 means the
mechanism is right but insufficient.

**Actual outcome: exactly that.** E1–E6 ✅, E9 ✅, E8 partial (see §10.3), **E7 ❌**. The
"mechanism right but insufficient" branch is the one that materialised — see §10.4 and §14.

**Explicitly *not* claimed:** that the modem no longer power-collapses. Android collapses it
918×/54 min. The claim is that OpenWrt now collapses it at a comparable rate with every
handshake completing, which is what the firmware appears to require.

---

## 10. Results

### 10.1 Code / build verification — ✅ PASS

| Check | Result |
| :--- | :--- |
| Kernel module builds clean | ✅ `CC [M] qcom_bam_dmux.o` … `LD [M] qcom_bam_dmux.ko` |
| Patch 808 regenerated from pristine tarball | ✅ 29 hunks, md5 `b4942b29bf2ddf2369d713b022cbd922` |
| Patch reproduces build tree **byte-exactly** | ✅ md5 `4c4c83d6e0cfc0855f3c3fabd4ea1ec9` both sides |
| Deployed module hash | ✅ sha256 `a75fef22…`, srcversion `92F15578…` |

### 10.2 Runtime-PM behaviour — ✅ PASS (early, uptime ~2.5 min)

| Counter | Pre-fix (26 min uptime) | Post-fix (2.5 min uptime) | Prediction |
| :--- | :--- | :--- | :--- |
| `pm_suspend_attempts` | **1** | **26** | E1 ✅ |
| `pm_suspend_completions` | 1 | 25 | E1 ✅ |
| `pm_resume_attempts` | — | 27 | E1 ✅ |
| `pm_usage_count` | **1** (pinned) | **0** | E2 ✅ |
| `pc_vote_tx_count` | 2 | **27** | E3 ✅ |
| `pc_unvote_tx_count` | 1 | **26** | E3 ✅ |
| `pc_timeout_count` | 0 | **0** | E4 ✅ |
| `pc_irq_count` / `pc_ack_irq_count` | 5 / 1 | **53 / 53** | E5 ✅ |
| dmesg `pc-ack timeout` / `pc_state wait timeout` / fatal | — | **0** | E9 ✅ |
| `a2_pc_disabled` | 0 | 0 | — |

Per-second boot sampling shows continuous suspend/resume (≈25 cycles in 2 minutes). Collapse
rate ≈ **12/min** vs. Android's ~17/min — same regime, where before it was effectively **0/min**.
→ **E6 ✅**

### 10.3 Decisive soak (past the 800–1100 s crash window) — ❌ **E7 FAILED**

| Soak elapsed | Uptime | rx/tx pkts | ping | pcTO | susp | vote/unvote | dmesg fatal |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 0 s | 132 s | 25 / 95 | OK | 0 | 29 | 29/29 | none |
| 200 s | 354 s | 60 / 253 | OK | 0 | 93 | 94/93 | none |
| 400 s | 618 s | 94 / 434 | OK | 0 | 165 | 166/165 | none |
| 680 s | 886 s | 125 / 611 | OK | 0 | 248 | 248/248 | none |
| **700 s** | **909 s** | 127 / 626 | **FAIL** | 0 | 255 | 256/255 | — |
| **720 s** | **932 s** | 127 / 629 | **FAIL** | **1** | 256 | 256/256 | **YES @ 912.6 s** |
| 740 s | 952 s | 127 / 637 | OK | 1 | 261 | 261/261 | (recovered) |
| 800 s | 1022 s | 163 / 699 | OK | 1 | 274 | 275/274 | — |
| 920 s | 1153 s | 186 / 792 | OK | 1 | 313 | 313/313 | — |

**Verdict: E7 FAILED.** The firmware faulted again, on almost exactly the same schedule as
before:

```
[  912.412534] bam-dmux 4080000.remoteproc:bam-dmux: bam_dmux: modem pc-ack timeout during resume
[  912.560598] qcom-q6v5-mss 4080000.remoteproc: fatal error received: a2_power.c:1189:
[  912.560793] remoteproc remoteproc0: crash detected in 4080000.remoteproc: type fatal error
[  912.582559] bam-dmux 4080000.remoteproc:bam-dmux: bam_dmux: SSR before shutdown: scheduling teardown work
[  913.219758] bam-dmux 4080000.remoteproc:bam-dmux: bam_dmux: SSR after powerup: scheduling powerup work
[  913.428568] bam-dmux 4080000.remoteproc:bam-dmux: bam_dmux: SSR powerup: successfully reinitialized BAM channels and rings
```

Final counters at the end of the run: `pc_vote_tx_count: 311` / `pc_unvote_tx_count: 311`
(balanced), `pm_suspend_attempts: 311` / `completions: 303`, `pc_timeout_count: 1`,
`pm_usage_count: 0`, `a2_pc_disabled: 0`.

**What improved:** the failure now self-heals in ~1 s of *modem* time (SSR complete at 913.9 s,
`CMD_OPEN` on all 8 channels) rather than the previous mode where RX stayed dead for ~174 s and
then faulted at 1084 s. The user-visible data outage shrank from ~174 s to ~20–40 s (ping still
FAILing at 932 s while the bearer re-established). **This is a mitigation, not the fix the user
asked for.**

### 10.4 The most important finding: the trigger is independent of the AP's voting

| Run | AP collapse votes by ~910 s | Fault time | Mode |
| :--- | :--- | :--- | :--- |
| Pre-fix | **~1** (never voted) | RX dead @ ~910 s, fatal @ 1084 s | stall → fatal |
| Post-fix (this run) | **311** | fatal @ **912.6 s** | fault → auto-SSR |

The fault lands at essentially the **same time (~910 s) whether the AP votes ~1 time or 311
times.** Therefore the AP's `SMSM_A2_POWER_CONTROL` voting is **not** the trigger, and driving it
at Android-like rates does not satisfy whatever the firmware is actually waiting for.

A strong candidate for the ~912 s clock, from the existing firmware RE
(`hmu05/900S_CRASH_LFIRST...` / `900S_CRASH_LPR_FRAMEWORK_RE.md`): `FUN_c0ce7fe0` declares a Q6
PC voting failure when the sleep count fails to increment after **~400 DRX cycles**.
400 × ~2.28 s ≈ **912 s** — a close match. If correct, the firmware is counting something other
than the AP's vote (likely the modem's *own* deep-sleep transitions), and the AP-side vote is
necessary but not sufficient.

**New lead:** the `pc-ack timeout` fires **148 ms before** the fatal error, i.e. the AP's own
resume handshake was already failing at the moment the firmware gave up. Note the tolerance
asymmetry — OpenWrt's `bam_dmux_runtime_resume()` waits **250 ms** for the ack
(`qcom_bam_dmux.c:1615-1616`), Android's `ul_wakeup()` waits **2000 ms**
(`UL_WAKEUP_TIMEOUT_MS`).

### 10.5 The fault lands exactly 900 s after modem power-up

Measured on this run:

```
[   10.935731] remoteproc remoteproc0: powering up 4080000.remoteproc
[  912.560598] qcom-q6v5-mss 4080000.remoteproc: fatal error received: a2_power.c:1189:
```

**912.560 − 10.936 = 901.6 s.** The fault is anchored to *modem* boot, not AP boot.

This matches a **900 s timer already documented in the tree.** `/etc/init.d/rmtfs` carries:

```
# NOTE: Do NOT use -r (read-only mode). The modem firmware runs a
# periodic EFS sync timer every 900 seconds (15 minutes). If rmtfs
# rejects the write, the modem panics with a fatal SSR crash. Stock
# Android's rmt_storage always runs with full read-write access.
```

**rmtfs read/write state — checked 2026-09-20, it is READ-WRITE.** The daemon runs as
`/usr/sbin/rmtfs -P -s` (no `-r`), and `/proc/<pid>/fdinfo/*` confirms `O_RDWR` on every
backing device:

| fd | Target | `flags:` | Access mode |
| :--- | :--- | :--- | :--- |
| 6 | `.../remoteproc0/state` | `0400001` | **O_RDONLY** (control file — expected) |
| 8 | `/dev/mmcblk0p4` (modemst1) | `0400002` | **O_RDWR** |
| 9 | `/dev/mmcblk0p5` (modemst2) | `0400002` | **O_RDWR** |
| 10 | `/dev/mmcblk0p2` (fsg) | `0400002` | **O_RDWR** |
| 11 | `/dev/mmcblk0p1` (fsc) | `0400002` | **O_RDWR** |
| 5 | `/dev/qcom_rmtfs_mem1` | `0400002` | **O_RDWR** |

All four EFS partitions (`modemst1`, `modemst2`, `fsg`, `fsc`) plus the rmtfs shared-memory
region are open read-write. `logread` contains **zero** rmtfs messages — no
`request for unknown partition … rejecting` and no `failed to open …` — so rmtfs is neither
in read-only mode nor logging a rejection.

**So the "rmtfs is read-only" variant of the hypothesis is DISPROVEN.** What remains open:

- The fault is an **`a2_power.c` (power-collapse) error**, not an EFS error, which argues
  against the EFS-sync reading and in favour of the sleep-manager reading of the same 900 s.
- Two independent ~900 s explanations now exist and are **not yet distinguished**: the modem
  EFS-sync timer, and the firmware RE's "sleep count not incrementing after ~400 DRX cycles"
  (400 × ~2.25 s ≈ 900 s). Both land on the same instant.
- rmtfs is write-*capable* but that does not prove the modem's EFS writes **succeed** — a
  write can still fail below rmtfs (eMMC write-protect, partition mismatch). Distinguishing
  this needs request-level tracing, which rmtfs does not currently emit.

### 10.6 `a2_power.c:1189` investigated — an A2-power assert, and the "EFS sync" reading has no independent provenance

*(Added 2026-09-20.)*

**A. How the firmware reports the location (verified).**

`FUN_c0879150` @ `0xc0879150` is the universal `ERR_FATAL` handler — it has **17,708 direct
`call 0xc0879150` sites** in `modem.asm`.

The AP-visible text is then formatted by the code at **`0xc08794e0`**, which reads a record where

* the **line is a 16-bit field at `+0x00`** — `r3 = zxth(memw(r18+#0x0))`, and
* the **filename is an inline NUL-terminated string at `+0x14`** — `r4 = add(r18,#0x14)`,

and renders them with `"%s:%d:"` (`0xc18491f9`; its only code reference in the whole image is
`0xc087950c`). That trailing colon is exactly what appears in
`fatal error received: a2_power.c:1189:`.

> ⚠️ **The descriptor → file/line link is NOT decoded.** The descriptor pointers passed to
> `FUN_c0879150` (`0xc3c68290`, `0xc3c69940`, `0xc164e490`, …) read as **high-entropy, obfuscated
> data** even inside rodata, so the pairing cannot be resolved statically without the firmware's
> decryption routine. Do **not** claim a verified file↔line pairing from static bytes.

**B. What *is* verifiable — the BSS line tables, and a real control.**

The firmware keeps **`{u32 line; u32 flag}`, stride-8 tables in BSS**, initialised by per-file
functions that zero the `flag` half of every slot and then fill the `line` half:

```
c0e98748: { r11 = #0xb85 ; r16 = #0xb86
c0e98750:   memw(r0+#0x368) = r11 ; memw(r0+#0x370) = r16 }
```

The **control that validates these tables** is that they contain lines appearing verbatim in real
AP kernel logs:

| table slot | raw | line | observed in a real log? |
| :--- | :--- | ---: | :--- |
| `DAT_c3a45908` | `0xfd6` | **4054** | ✅ `lte_ml1_sleepmgr_stm.c:4054:` — many captures |
| (same family) | `0xfae` | **4014** | ✅ `lte_ml1_rfmgr_trm.c:4014:` (Doc 91) |
| `DAT_c3a44fa0` | `0x4a5` | **1189** | ✅ `a2_power.c:1189:` — this soak, 912.6 s and 1084.5 s |

So the tables do carry reported crash lines. They are **partial**, though — several other observed
lines (`lte_ml1_common_timer.c:390`, `mmoc.c:2192`, `lte_ml1_sm_conn_inter_freq_stm.c:712`,
`lte_LL1_gap_rf_tune.c:351`) are absent from this family, so it is one of several mechanisms, not
the whole story.

> ⚠️ **Correction (2026-09-20).** An earlier revision of this section used **`a2_power.c:2949` as
> the control, calling it "independently observed". That was wrong.** `a2_power.c:2949` appears in
> **no log anywhere in this repository**. It originates in
> `78_DECOMPILED_MODEM_RE_AND_OPENWRT_GAP_ANALYSIS.md` §2.4 (commit `831b55a`, 2026-09-12), which
> cites `0xc0e98748` — the *initializer instruction* that writes `0xb85` into the line table. That
> is an inference from the table, not an observation, so it cannot serve as a control. The
> 4054/4014 entries above replace it.
>
> Likewise, the "16-byte-strided filename table, 329 entries" framing used in the first revision
> was an artifact: the filenames are **packed variable-length string literals** (stride 7 for
> `mmoc.c`, 24 for `lte_ml1_sleepmgr_stm.c`, 16 for `a2_power.c`), not fixed-size records. What is
> real is that the literals are **grouped by source file**.

**C. The A2 module grouping (verified), and what it does / does not settle.**

`a2_power.c` is an **A2 power-collapse module**, not EFS code. The image holds **76 consecutive
copies of the `a2_power.c` string literal**, bracketed by runs of `a2_ul_per.c` (before) and
`a2_sio.c` (after); the same neighbourhood holds `a2_task.c`, `a2_ipfilter.c`, `a2_diag.c`,
`a2_dbg.c`, `a2_dl_tlp.c`, `a2_dl_phy_hspa.c`. EFS modules exist elsewhere in the image
(`fs_db.c` ×28, `fs_device.c` ×12, `ds_mppm_efs_parser.c` ×1) and do not appear in this group.

* This **does** support the reading that the fatal is raised by A2-power code rather than EFS
  code — A2 is the HSIC / application-processor power-collapse engine, the same subsystem whose
  `SMSM_A2_POWER_CONTROL` handshake `bam_dmux` drives.
* It **does not** prove the 900 s timer is the DRX-cycle counter. `file:line` names *where the
  firmware gave up*, which can be downstream of whatever stalled. An EFS sync that blocked the
  sleep chain would still surface as an `a2_power.c` assert.
* The strongest evidence for the location is simply that **the modem itself printed
  `a2_power.c:1189:`** — an observation that needs no table decoding at all.

**D. The "EFS sync timer" reading has no independent provenance.**

The only source for the 900 s EFS-sync timer is the comment in **this project's own** rmtfs init
script:

```
# NOTE: Do NOT use -r (read-only mode). The modem firmware runs a
# periodic EFS sync timer every 900 seconds (15 minutes). ...
```

`git log -S "periodic EFS sync timer"` traces it to commit **`2f8dc7c` (2026-09-02)**, authored by
this project ("feat(modem): implement definitive 4-tier stability architecture…"). It is **not**
upstream OpenWrt/rmtfs text and **not** Qualcomm documentation. As evidence for the 900 s
mechanism it is therefore **circular** — a hypothesis the project wrote into a comment and later
re-read as if it were a finding.

**Verdict.** With (i) the fatal being an A2-**power** assert, (ii) the firmware RE's independent
~400-DRX × ~2.25 s ≈ 900 s sleep-count check, and (iii) the fault time being invariant to the AP's
vote count, the **DRX/sleep reading now carries substantially more evidence than the EFS reading**.
The EFS reading is not formally dead — an EFS write could still be the upstream trigger — but its
only citation has been removed.

---

## 11. Rollback

Module rollback chain on the device (no reboot-free reload; `reboot` to apply):

| File | Contents |
| :--- | :--- |
| `/root/qcom_bam_dmux.ko.pmrestart-backup` | pm_restart-only module, sha256 `ad2008bdd8f378aa52dc668c3eb7d6fb624e987332b91281360f37b327110235` |
| `/root/qcom_bam_dmux.ko.prefix-backup` | stock module, sha256 `5bc1fa5640aef65568838563eef7b48d8cff7ced2b865d477aa7b61df1486476` |

`/lib/modules/6.12.94/qcom_bam_dmux.ko` lives on the **overlay** (`/overlay/upper/…`), so a
sysupgrade will not restore it — copy a backup back and reboot.

---

## 12. Reproduction / verification method

**Build the module alone** (fast; the kernel tree is already configured):

```sh
cd openwrt
export PATH="$PWD/staging_dir/toolchain-aarch64_generic_gcc-14.3.0_musl/bin:$PATH"
export STAGING_DIR="$PWD/staging_dir"
K=build_dir/target-aarch64_generic_musl/linux-msm89xx_msm8916/linux-6.12.94
make -C "$K" M=drivers/net/wwan ARCH=arm64 CROSS_COMPILE=aarch64-openwrt-linux-musl- modules
aarch64-openwrt-linux-musl-objcopy --strip-debug \
  "$K/drivers/net/wwan/qcom_bam_dmux.ko" /tmp/deploy.ko
```

**Regenerate patch 808** — must diff against a **freshly extracted** pristine file, never a
directory a patch has already been applied to:

```sh
mkdir -p /tmp/pristine && tar -xJf openwrt/dl/linux-6.12.94.tar.xz -C /tmp/pristine \
  --wildcards 'linux-6.12.94/drivers/net/wwan/qcom_bam_dmux.c'
# pristine md5 must be c817bd8fe2b1907ff470d056de0423db
diff -u /tmp/pristine/linux-6.12.94/drivers/net/wwan/qcom_bam_dmux.c <build-tree-file> ...
```

> ⚠️ **Trap encountered:** regenerating against a directory that already had patch 808 applied
> silently produced a 1-hunk diff and overwrote the real patch. Always verify the pristine
> md5 (`c817bd8f…`) and the hunk count (**29**) before installing.

**Live telemetry** (the counters used throughout this document):

```sh
T=/sys/bus/platform/devices/4080000.remoteproc:bam-dmux/rx_telemetry
cat "$T"
D=/sys/bus/platform/devices/4080000.remoteproc:bam-dmux
cat "$D/power/control" "$D/power/autosuspend_delay_ms" "$D/power/runtime_status"
```

`pm_usage_count` is read from `atomic_read(&dev->power.usage_count)` and has no side effects.

**Catching a reference leak**: sample `pm_usage_count` once per second from boot. A jump that
never returns to 0 localises the leak to the second it happened (this is how Defect 2 was found).

---

## 13. Open items

1. **Patch not committed.** The runtime-PM fixes are correct in their own right and are
   independently verifiable (§10.1/§10.2), but they do **not** deliver the user's goal. Decide
   whether to commit them as a mitigation while the real trigger is pursued.
2. **The ~912 s trigger is still unidentified** — and §10.4 shows it is *not* the AP's collapse
   voting. This is now the primary question. §10.6 narrows it: the fatal is an assertion inside
   `a2_power.c` (the A2 power-collapse module), **not** EFS code, and the only citation for the
   900 s EFS-sync timer turns out to be a comment this project wrote itself.
3. **Escalation gap.** OpenWrt's `bam_dmux_runtime_resume()` only `dev_warn`s on handshake
   timeout (`qcom_bam_dmux.c:1617-1619`); Android's `ssrestart_check()` restarts the modem
   subsystem. The user has rejected SSR as an *end state*, so this is a last resort only.
4. **The two uncommitted watchdog files** (`msm89xx/base-files/etc/config/modem-watchdog`,
   `msm89xx/base-files/usr/sbin/modem-bearer-watchdog`) implement the option-2 SSR recovery
   described in §13.3 — deployed on the device, rejected by the user as an end state, still
   uncommitted.

---

## 14. Next steps (post-E7-failure)

Ordered by (evidence strength ÷ cost). Nothing here is claimed to work yet.

**N1 — Anchor on the 900 s modem-boot timer and distinguish the two readings.** §10.5 shows the
fault is 901.6 s after *modem* power-up. rmtfs is confirmed read-write, so the "read-only rmtfs"
branch is closed; §10.6 then removed the EFS reading's only citation and showed the fatal is an
`a2_power.c` assert. The readings are therefore no longer evenly weighted — but the EFS write can
still be the *upstream* trigger, so keep testing it:
  - **Highest value:** confirm the anchor is modem-boot and not AP-boot by rebooting and checking
    that a modem SSR mid-run **resets** the 900 s window (next fault ≈ SSR time + 900 s). This
    alone separates "free-running modem timer" from "AP-anchored event".
  - For the EFS reading: trace rmtfs requests around t+900 s (it emits no request logging today —
    add it, or watch the eMMC for writes to `modemst*`).
  - For the sleep reading: check `/sys/kernel/debug/rpm_master_stats` (`MPSS numshutdowns`,
    compare against Android's ~17/min) and whether the **AP itself** ever enters system suspend
    (`/sys/power/state`, `pm_genpd_summary`). If Android's AP suspends and OpenWrt's never does,
    that is a new and testable divergence.
  - **New from §10.6:** the image holds 76 consecutive `a2_power.c` string literals, i.e. that
    many distinct assert sites in the file. Naming *which* one is line 1189 requires the
    firmware's descriptor decryption routine (the descriptors are obfuscated — §10.6 A), so this
    is currently a static-analysis dead end; the practical route is a DIAG capture that includes
    the `a2_power.c` error text around the fault.

**N2 — Instrument the 912 s boundary.** Add fine-grained logging (timestamped) around
`bam_dmux_pc_irq` / `bam_dmux_pc_ack` / `runtime_resume` in the ~60 s before the fault, to see
whether the handshake sequence desynchronises (e.g. a missed `pc_irq` leaving `pc_ack_state`
out of phase) rather than merely being late.

**N3 — Ack-tolerance parity.** Raise the resume ack wait from 250 ms → 2000 ms
(`UL_WAKEUP_TIMEOUT_MS`). Cheap and Android-faithful, but note the timeout fired only **148 ms**
before the fatal error, so a longer wait alone probably does **not** prevent the fault — it may
only change which side reports it first. Low expected value; do it for parity, not as a fix.

**N4 — Re-examine whether the fault is actually harmful.** If the fault is unavoidable at the
firmware level, the engineering question becomes how to make recovery *invisible* (the user's
real requirement is no interruption, not no fault). Current recovery is ~20–40 s because the
bearer re-establishes; shortening that is a different problem from preventing the fault.

**N5 — Revisit the firmware RE** on `FUN_c0ce7fe0` and the `rpm.sync` stall
(`hmu05/900S_CRASH_LPR_FRAMEWORK_RE.md`), now with the added fact that the fault time is
invariant to AP voting behaviour. The previously recorded blocker — `rpm.sync`
(`0xc08bebd0`) flush loops with no retry counter and no timeout — is still the deepest lead.
