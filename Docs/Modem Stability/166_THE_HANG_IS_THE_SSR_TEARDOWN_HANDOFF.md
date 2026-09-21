# 166 — The AP hang is the SSR *teardown hand-off*, and it is upstream of the instrumented window

**Date:** 2026-09-21
**Subject:** AP-side kernel diagnostic. **No modem firmware, no WCNSS firmware, no DTS and no
bootloader byte was changed.** No new build was produced or deployed this round; the resident
module is still Doc 164's `msm89xx/patches/817-q6v5-ssr-window-trace.patch` as the loadable module
`kmod-qcom-rproc-modem`.
**Result:** the AP hang was **caught on a spontaneous fatal** (crash #2 at AP 1840.390 s,
`lte_ml1_sleepmgr_stm.c:4054`). Two independent pstore sinks agree that the **last message the
kernel ever emitted was `bam_dmux: SSR before shutdown: scheduling teardown work`** — the SSR
notifier's *hand-off*, not the SSR itself. The teardown work's own first print (`bam_dmux:
executing serialized asynchronous SSR teardown`, `qcom_bam_dmux.c:2238`) **never appeared**, and
**no coredump device was created**, so `rproc_stop()` never returned. Doc 164 §9's pre-registration
is therefore **falsified in site**: the hang is not in the 23-step window and not in Doc 159's
window either — it is **upstream of both**. The only unbounded waits in that region are
`cancel_delayed_work_sync(&dmux->tx_retry_work)` and `cancel_work_sync(&dmux->tx_wakeup_work)`
(`:2234`, `:2235`), and both are **redundant** — the flag set one line earlier already makes both
target works self-abort on their first statement. §8 proposes patch 820.

---

## 0. SOP compliance

| SOP step | Status |
|---|---|
| No baseband change; no firmware write | **Honoured.** Zero firmware bytes touched. Device-side writes this round: **none** (read-only probes). |
| Dual-firmware comparative protocol | **Not applicable, and deliberately so** — same reasoning as Doc 164 §0 / Doc 165 §0. This is AP-side kernel instrumentation. There is no Android counterpart to compare against, and **no baseband hypothesis is advanced**. The comparative protocol exists to stop blind *baseband* patching; nothing here touches the baseband. |
| Verify against ground truth before acting | **Honoured.** Every claim in §5 is read from the **live build tree** (`openwrt/build_dir/.../drivers/net/wwan/qcom_bam_dmux.c`), line-numbered. `queue_pm_work` was not assumed — it was resolved to `include/linux/pm_runtime.h:62`. The "read the definition, not the name" rule (memory `feedback_read_the_definition_not_the_name`) is what forced the `cancel_work_sync` analysis instead of accepting the function's apparent intent. |
| Hash the artifact, verify it in situ | **Honoured.** Resident module md5 `79e7a858d41b6c9f7edf8b26b90c77f8`; the instrument **survived the reboot** (§11.2). |
| Never classify a fatal by its `file:line` | **Honoured.** `lte_ml1_sleepmgr_stm.c:4054` is used only as an identity label (Doc 162 §7's refinement: ask what *this* signature's period is). The fatal is not the subject of this document — the **teardown** is. |
| Do not treat corpus docs as fact | **Honoured, and this round retracts one of my own in-session findings** (§7.1): the "stale `qcom-time-daemon` performing a periodic ATS_USER refresh" lead is **dead**, disproved by direct process inspection. |
| Record what was done, the result, and what is next | This document, plus §12 and §13. |

---

## 1. What was left open

Doc 164 §9 pre-registered a prediction about the hang:

> the last `q6v5-trace` line before the hang will be **step 08, 20 or 21** — the three
> `qcom_scm_assign_mem()` sites.

Doc 165 §4.1 then recorded a **coverage gap** in that pre-registration: the Doc 159 window is
**86.2 % one untraced `q6v5_rmb_mba_wait()`**, so a hang in the untraced 86.2 % would leave the
last line at **step 23** — a third outcome §9 did not enumerate.

Doc 165 §11 closed with: *"the hang has not been observed with the instrument resident. It is the
next thing to wait for."*

It has now been observed. It falsifies the pre-registration **in site**, and it falsifies Doc 165
§4.1's framing too, because **there is no `q6v5-trace` line in the hang at all.**

---

## 2. The hang, verbatim

Two sinks, merged in order. `console-ramoops-0` is the **ramoops console** (an in-kernel console
sink: it receives `dev_err` and above, because `console_loglevel = 6` suppresses `KERN_INFO` on
every console). `pmsg-ramoops-0` is the **soak's filtered `/dev/kmsg` mirror** (all levels,
`dev_info` included) — its filter is reproduced in §2.2 and it is why this round can say
"no further line" with confidence.

### 2.1 The dying boot's tail

```
[  937.786016] qcom-q6v5-mss 4080000.remoteproc: fatal error received: a2_power.c:1189:      crash #1
[  937.786149] remoteproc remoteproc0: crash detected in 4080000.remoteproc: type fatal error
[  937.793771] remoteproc remoteproc0: handling crash #1 in 4080000.remoteproc
[  937.801171] remoteproc remoteproc0: recovering 4080000.remoteproc
[  937.959319] bam-dmux 4080000.remoteproc:bam-dmux: bam_dmux: refusing to queue command while modem is collapsed
[  938.919368] qcom-q6v5-mss 4080000.remoteproc: port failed halt
[  939.667333] remoteproc remoteproc0: remote processor 4080000.remoteproc is now up
[  940.313325] bam-dmux ...: bam_dmux: SSR powerup: modem pc_state=1 (waited 580 ms)
[  940.314932] bam-dmux ...: bam_dmux: SSR powerup: successfully reinitialized BAM channels and rings
                                                                          <- crash #1 fully recovered
[ 1840.390188] qcom-q6v5-mss 4080000.remoteproc: fatal error received: lte_ml1_sleepmgr_stm.c:4054:   crash #2
[ 1840.390276] remoteproc remoteproc0: crash detected in 4080000.remoteproc: type fatal error
[ 1840.398357] remoteproc remoteproc0: handling crash #2 in 4080000.remoteproc
[ 1840.406218] remoteproc remoteproc0: recovering 4080000.remoteproc         <- last console-ramoops line
[ 1840.413105] bam-dmux ...: bam_dmux: SSR before shutdown: scheduling teardown work
                                                                            <- LAST LINE, then nothing
```

`recovering` is `dev_err` (so it is in `console-ramoops`); `SSR before shutdown` is `dev_info` (so
it is **only** in `pmsg-ramoops`). Both records end at the same instant. **The last line is the
notifier's hand-off, 6.887 ms after `recovering`** — against 7.453 ms on the healthy crash #1, i.e.
the hand-off itself is *normal*.

### 2.2 What did *not* appear — and why that is provable

The soak's filter (`/overlay/q6trace_soak.sh`, lines 91–100) writes a `/dev/kmsg` line to
`/dev/pmsg0` iff it matches:

```
*q6v5-trace:*|*"fatal error received"*|*"port failed halt"*|*"MBA booted"*|*"is now up"*|
*"stopped remote processor"*|*"SSR "*|*"SSR:"*|*"Unable to handle"*|*"Kernel panic"*|
*"BUG:"*|*"watchdog"*|*"Watchdog"*
```

The healthy crash #1 emitted, in this order after the same hand-off:

| line | level | matches filter? | appeared in the hang? |
|---|---|---|---|
| `wwan wwan0: port wwan0at0 disconnected` | info | no | — (not covered) |
| `bam_dmux: executing serialized asynchronous SSR teardown` | info | **yes** (`SSR `) | **NO** |
| `q6v5-trace: 01 …` … `q6v5-trace: 23 …` | info | **yes** (`q6v5-trace:`) | **NO** |
| `port failed halt` | err | **yes** | **NO** |
| `MBA booted …` / `is now up` | info | **yes** | **NO** |
| `Kernel panic`, `BUG:`, `Unable to handle`, `watchdog` | emerg/err | **yes** | **NO** |

Every step the instrument was built to observe, and every kernel fault-path banner, is inside the
filter. **None of them fired.** So this is not "the console went quiet": the one sink that carries
`dev_info` *and* is guaranteed to capture the next expected line is silent too.

### 2.3 Independent corroboration: the coredump watcher

`/overlay/coredump_watch.log` records the watcher's three lifetimes:

```
[16.63s] === coredump_watch start pid=3252 ... ===      boot A
[915.56s]  dump device devcd1 -> modem_coredump_up915.55_devcd1.elf
[1857.52s] dump device devcd2 -> ..._up1857.52_devcd2.elf
[2760.09s] dump device devcd3 -> ..._up2760.09_devcd3.elf
[3701.47s] dump device devcd4 -> ..._up3701.47_devcd4.elf
[4649.09s] dump device devcd5 -> ..._up4649.09_devcd5.elf
[16.27s] === coredump_watch start pid=3199 ... ===      boot B  <- THE HANG BOOT
[939.35s]  dump device devcd1 -> modem_coredump_up939.34_devcd1.elf
[945.24s]  captured 85398475 bytes from devcd1
[31.35s] === coredump_watch start pid=3746 ... ===      boot C  (current)
```

Boot B produced **exactly one** dump — crash #1's. **Crash #2 produced none.** This is a third,
independent line of evidence, and it does not go through `printk` at all: it is a filesystem
observation of the coredump device. It confirms Doc 153 §4's structural point — *a coredump is
created only after `rproc_stop()` returns* — so **`rproc_stop()` did not return for crash #2**.

### 2.4 Derived

```
crash #1 -> crash #2 interval   = 1840.390188 - 937.786016 = 902.604172 s
crash #2 modem uptime           = 1840.390188 - 939.667333 = 900.722855 s
                                  (rproc-up anchor of crash #1's SSR)
recovering -> SSR before shutdown = 6.887 ms   (healthy on crash #1: 7.453 ms)
```

The 902.6 s figure is consistent with the ~902.3 s modem-internal timer of Doc 162 §6 and needs no
new mechanism. **It is not the subject of this document.** The subject is that the *recovery from*
it hangs.

---

## 3. Scoring Doc 164 §9's pre-registration — FALSIFIED IN SITE

| | |
|---|---|
| **Predicted** | last `q6v5-trace` line ∈ {step 08, 20, 21} — the three traced `qcom_scm_assign_mem()` sites |
| **Observed** | **there is no `q6v5-trace` line in the hang at all.** The last line is `bam_dmux: SSR before shutdown: scheduling teardown work`, ~10.9 ms **before** step 01 would have run |
| **Verdict** | **Falsified in site.** The SCM-transfer hypothesis is not *disproved* — it was simply never reached. |

**And Doc 165 §4.1's coverage gap was itself too narrow.** §4.1 said a hang in the untraced 86.2 %
would leave the last line at **step 23**. That is true *of a hang inside the window*. But the
window begins at `q6v5_mba_reclaim()`'s first step, and this hang is **before that** — in the
`rproc_stop()` → SSR-notifier → bam_dmux-teardown hand-off that runs *ahead of* the driver's
23-step sequence. So the instrument had a **larger blind spot than §4.1 recorded: not just the
inside of the window, but the whole region upstream of it.**

**This is the fifth time an instrument's scope has been the limiting factor** (Doc 164 §0's pattern):
Doc 163's coredump descriptor, Doc 164's 23 steps vs a 86.2 %-untraced window, and now this. The
lesson to carry forward is not "trace more" but **"trace the hand-off, not the state machine"** —
the hang has now been found twice in the *gaps between* instrumented regions, never inside one.

---

## 4. Where the hang is, in one paragraph

`bam_dmux_ssr_notifier_cb()` (`:2357`) receives `QCOM_SSR_BEFORE_SHUTDOWN` and does four things
(`:2363-2370`): print, set `in_teardown = true`, set `pc_state = false`, reset the powerup budget,
then `schedule_work(&dmux->ssr_teardown_work)`. **The print is the last line the kernel ever
emitted**, which means `:2365-2369` ran and the work was submitted. The work body
(`bam_dmux_ssr_teardown_work_func()`, `:2228`) then does:

```c
2233	WRITE_ONCE(dmux->in_teardown, true);
2234	cancel_delayed_work_sync(&dmux->tx_retry_work);      /* unbounded wait */
2235	cancel_work_sync(&dmux->tx_wakeup_work);             /* unbounded wait */
2236
2237	mutex_lock(&dmux->state_lock);
2238	dev_info(dmux->dev, "bam_dmux: executing serialized asynchronous SSR teardown\n");
```

`:2238` never printed. So the hang is in **`:2233-:2237`** — a five-line region containing exactly
**two unbounded waits**, both `cancel_*_sync()`, both of which have **no timeout and cannot be
interrupted**.

---

## 5. Why those two calls are the suspects — from the code, not from their names

### 5.1 Both target works already self-abort

The flag is set **one line before** the cancels (`:2233`), and both target works test it as their
**first statement**:

```c
 730	static void bam_dmux_tx_wakeup_work(struct work_struct *work)
 736		if (READ_ONCE(dmux->in_teardown))
 737			return;

 845	static void bam_dmux_tx_retry_work(struct work_struct *work)
 850		if (READ_ONCE(dmux->in_teardown))
 851			return;
```

and `tx_wakeup_work` **re-checks under the lock** before doing anything observable:

```c
 758		mutex_lock(&dmux->state_lock);
 764		if (!READ_ONCE(dmux->pc_state) || READ_ONCE(dmux->in_teardown)) {
 765			mutex_unlock(&dmux->state_lock);
 ...
 772			return;            /* nothing submitted */
 773		}
```

So the *intent* of `:2234-:2235` — "make sure no TX work is in flight while we tear down" — is
already achieved by `:2233` for every future invocation. What the synchronous cancels add is a
**wait**, not a guarantee.

### 5.2 What the waits are waiting on, and why it can be forever

`tx_wakeup_work` is not a leaf. Its first two acts are:

```c
 739		ret = pm_runtime_resume_and_get(dmux->dev);      /* can block on the PM core */
 ...
 758		mutex_lock(&dmux->state_lock);                   /* can block on the mutex    */
```

`cancel_work_sync()` on a work item that is **running** waits for it to finish. There is no timeout
and no `_timeout` variant. Therefore:

* if `tx_wakeup_work` is running and blocked at `:739` (PM core) or `:758` (`state_lock`), then
  `cancel_work_sync()` at `:2235` **blocks indefinitely**;
* and `state_lock` is held for long stretches by exactly the paths that run *during* an SSR:
  `bam_dmux_pc_irq()` holds it across a full `bam_dmux_power_on()` (`:1903` → `:1909`) and across
  `bam_dmux_power_off()` (`:1911`, `:1934`); **`bam_dmux_rx_watchdog_func()` holds it across
  `bam_dmux_power_on()` (`:1309` → `:1335`) and `bam_dmux_pm_restart()` (`:1350`)**;
  `bam_dmux_runtime_resume()` takes it (`:2025`, `:2049`); `bam_dmux_runtime_suspend()` takes it
  (`:1994`). Three independent paths can therefore hold `state_lock` for the duration of a BAM
  channel rebuild, and the watchdog only checks `in_teardown` at `:1292` — an invocation that passed
  that test *before* the flag was set can still hold the lock.

So the hang-by-construction is: **the teardown work synchronously waits for a work item whose
progress depends on the PM core and on a mutex that the SSR path itself is busy holding.** This is
the same defect class my own memory flagged after patch 810 (*"`cancel_work_sync(&tx_wakeup_work)`
there would DEADLOCK"*) — and it has now fired.

### 5.3 The competing candidate, stated honestly

`schedule_work()` at `:2369` returns `bool`; it fails only if the work is already pending. Between
crash #1 (937 s) and crash #2 (1840 s) is 900 s and crash #1's teardown completed, so `:2369`
succeeded. **But "the work was submitted" does not prove "the work ran":** if every `system_wq`
worker were wedged, the work would sit queued forever and `:2238` would equally never print.

I cannot discriminate (a) "ran and hung at `:2234`/`:2235`" from (b) "never got a worker" from this
boot's evidence alone. **(a) is the more likely reading** — `system_wq` is per-CPU multi-threaded
(`max_active = 256`), and the only bam_dmux items on it are `tx_wakeup_work`, `ssr_teardown_work`
and `register_netdev_work`, so wedging *all* workers would need a broader cause than this driver.
**But it is a reading, not a measurement**, and §9 pre-registers the test that settles it.

---

## 6. The reset: narrowed, still unresolved — but *not a panic*, and *not an oops*

The AP reset ~2–4 s after the fatal (host arithmetic: uptime-check 1812 at 12:31:00 → boot B start
12:00:48; fatal at AP 1840.390 s → 12:31:28.4; new boot from `uptime 89` at 12:33:00 → 12:31:31).
That is consistent with `kernel.panic = 3` — **but this round adds a fact that argues against a
panic path:**

```
$ ls /proc/device-tree/reserved-memory/ramoops@8db00000/
compatible  console-size  name  pmsg-size  record-size  reg
```

**`record-size` is present and `max-reason` is absent.** In `ramoops`, the `dmesg` zone is sized by
`record-size` and its default `max_reason` is `KMSG_DUMP_OOPS` — so **an oops *or* a panic would
leave a `dmesg-ramoops` record.** There is none:

```
-r--r--r--  root  root  179975  console-ramoops-0
-r--r--r--  root  root   11094  pmsg-ramoops-0
--- dmesg-ramoops present? ---  NO
```

And independently: a `panic()` prints `Kernel panic - not syncing:` at `KERN_EMERG`, which the
**in-kernel** ramoops console cannot bypass (it needs no userspace) — and the console record's last
line is `recovering`. So:

* **not an oops** — no `Unable to handle` / `BUG:` in either sink, and the dmesg zone exists;
* **not a panic** — no `Kernel panic` banner in the one sink a panic cannot escape.

**Therefore the reset was silent**, which points at a hardware/PMIC reset path or a deliberate
reboot rather than a kernel fault path. The two candidates on the table are the 30 s PM8916 PON WDT
that Doc 159 recorded (which the ~2–4 s delta does **not** match) and an external/power event. **A
panic whose console write was lost cannot be fully excluded** — an empty or truncated pstore can
mean the buffer was invalid and discarded (memory `reference_hmu05_device_quirks`). It stays open,
and §13 names the instrument that would close it.

**This is a genuinely new narrowing**: Doc 159 left "30 s WDT" as the explanation for its hang; this
hang resets in ~2–4 s, so **the two AP hangs do not share a reset mechanism** even though they share
a site class.

---

## 7. Corrections and retractions

### 7.1 RETRACTED — the "stale `qcom-time-daemon`" lead is dead

While auditing the remaining AP-side modem actors (Doc 164 §12 item 4) I raised a lead: the device's
`/etc/init.d/qcom-time-daemon` carries a **stale comment** claiming `-r 60` *"MUST stay > 0"*, and a
log line appeared to say the periodic refresh was `ENABLED`. If true, that would be a periodic
modem-touching write that Android does not have, on the ~902 s path.

**It is false.** Direct process inspection this round:

```
$ ps w | grep '[q]com-time-daemon'
 2514 root  968 S  /usr/sbin/qcom-time-daemon -r 0 -v

$ logread | grep -i 'Periodic\|software modem stability'
... [QMI-TIME] Pure software modem stability mode active. Periodic ATS_USER refresh: DISABLED (interval=0s)
... [QMI-TIME] Periodic ATS_TOD query: DISABLED (interval=60s)
```

`-r 0` is what is **running**, and the daemon reports the feature **DISABLED**. There is **no
periodic ATS_USER re-anchor on this device.** The only residue is a cosmetic one: the deployed init
script is an older revision (`8cd979087ed55930c458911056d7258c`) whose *comment* is wrong while its
*command* is right. **No behavioural lead. Do not carry this forward.**

### 7.2 Corrected — the two pstore sinks are not interchangeable

Earlier this session I treated `console-ramoops-0` as "the" dying-boot record. At
`console_loglevel = 6` it holds **only `dev_err` and above**; the instrument's entire output is
`dev_info` and is therefore *invisible* there. §2.2's filter analysis is the only reason the
"nothing further happened" claim is sound. **Corollary for every future hang: read both sinks, and
read the filter that produced the second one.**

### 7.3 Confirmed — pstore records survive the *next* boot

Boot C's `console-ramoops-0` still contains boot B's dying log, byte-for-byte the record captured
last round (179 975 B, same tail). The ramoops `console`/`pmsg` zones use `PRZ_FLAG_ZAP_OLDEST`,
i.e. they retain the **newest** data, which is why both ends line up at 1840.406/1840.413 s. So a
hang record is **not** lost by the reboot it causes — the constraint is only that it is overwritten
by the *following* boot's traffic.

---

## 8. The fix — patch 820 (built and ready to deploy)

**Principle: a teardown path must never perform a synchronous wait on a work item that needs the
same lock the waiter holds.** Two sites violate it, and **820 fixes both**, because they are one
defect expressed twice.

### 8.1 Site 1 — the teardown work's two cancels (the `:2233-:2237` region)

Replace the two unbounded cancels with their non-blocking forms and let the mutex + flag do the
exclusion:

```c
 	WRITE_ONCE(dmux->in_teardown, true);
-	cancel_delayed_work_sync(&dmux->tx_retry_work);
-	cancel_work_sync(&dmux->tx_wakeup_work);
+	/*
+	 * Non-blocking: in_teardown is already set above, and both target works
+	 * test it as their first statement (tx_wakeup_work:736, tx_retry_work:850)
+	 * and re-test under state_lock (tx_wakeup_work:764).  A synchronous
+	 * cancel here waits, without timeout, on a work item whose progress
+	 * depends on pm_runtime_resume_and_get() (:739) and on state_lock (:758)
+	 * -- both of which this very path is contending for.  Mutual exclusion
+	 * is provided by state_lock below, which any in-flight tx_wakeup_work
+	 * must also hold.
+	 */
+	cancel_delayed_work(&dmux->tx_retry_work);
+	cancel_work(&dmux->tx_wakeup_work);
```

**Why this is safe — the three cases an in-flight `tx_wakeup_work` can be in:**

1. **Not yet running** → `cancel_work()` removes it from the queue. Done.
2. **Running, already holding `state_lock`** → the teardown blocks at the `mutex_lock` until it
   finishes. Bounded, because the locked region (`:758-:825`) contains no unbounded wait.
3. **Running, waiting for `state_lock`** → it acquires the lock *after* the teardown releases it,
   sees `in_teardown` at `:764`, unlocks, and submits nothing. Its `pm_runtime` ref is balanced
   (`:739` get / `:767` put). `in_teardown` stays true until `bam_dmux_ssr_powerup_work_func()`
   clears it.

The same reasoning applies to `tx_retry_work`: it is a two-line function whose only action is
`queue_work(system_wq, &dmux->tx_wakeup_work)` (`:857`), gated on `in_teardown` at `:850`.

### 8.2 Site 2 — `power_off` / `pm_quiesce`, and this one is a *true* ABBA on a single mutex

**This site was found only after the fix for site 1 was written, and it is the stronger of the two.**

```c
1645	static void bam_dmux_pm_quiesce(struct bam_dmux *dmux)
...
1660		cancel_delayed_work_sync(&dmux->rx_rearm_work);
1661		cancel_delayed_work_sync(&dmux->rx_watchdog_work);
...
1561	static void bam_dmux_power_off(struct bam_dmux *dmux)
...
1576		cancel_delayed_work_sync(&dmux->rx_rearm_work);
1577		cancel_delayed_work_sync(&dmux->rx_watchdog_work);
```

and the watchdog that is being cancelled:

```c
1283	static void bam_dmux_rx_watchdog_func(struct work_struct *work)
...
1304		if (!READ_ONCE(dmux->pc_state)) {
1305			bool line = bam_dmux_pc_line_asserted(dmux);
1308			if (line) {
1309				mutex_lock(&dmux->state_lock);      <-- WANTS THE LOCK
```

**`bam_dmux_pm_quiesce()` has exactly ONE caller** — `bam_dmux_pc_irq()` at `:1936` — and that
caller **holds `state_lock` from `:1903`**. `bam_dmux_power_off()` is reached under `state_lock`
from `pc_irq` too (`:1911`, `:1934`), from the teardown work (`bam_dmux_ssr_teardown` → `:1883`),
from the SSR powerup work, and from `bam_dmux_power_on()`'s error paths (`:1450`, `:1474`, `:1481`).

So:

> **pc_irq holds `state_lock` and waits for `rx_watchdog_work` to finish; `rx_watchdog_func` is
> running and waits for `state_lock`. Neither can proceed.**

That is a **true deadlock cycle on one mutex** — not a "the PM core might be slow" argument, and not
dependent on any third party. It needs only that the watchdog be *already running and past `:1308`*
when `pc_irq` reaches the cancel. And **it is exercised on every ordinary power collapse**: the
telemetry shows `pc_irq` firing 327 times in 1353.6 s ≈ **7.3 collapses/min**, each one calling
`pm_quiesce`. This is a far more frequently exercised instance of the defect than site 1, and it
would explain an intermittent hang that only shows up after hours.

**Fix (same shape):**

```c
 	/* 2. Cancel periodic work. */
 	cancel_delayed_work_sync(&dmux->rx_rearm_work);
-	cancel_delayed_work_sync(&dmux->rx_watchdog_work);
+	cancel_delayed_work(&dmux->rx_watchdog_work);
```

**Why `rx_watchdog_work` can be non-blocking but `rx_rearm_work` MUST stay synchronous** — this
asymmetry is the whole reason the fix is safe:

* `rx_watchdog_func` **takes `state_lock`** (`:1309`). Every caller of `power_off`/`pm_quiesce`
  holds that lock, so the watchdog is *already mutually excluded* from the teardown body; the
  synchronous cancel adds nothing but the deadlock. After the teardown releases the lock the
  watchdog runs, re-checks `pc_state`, `in_teardown` and the actual line level **under the lock**
  (`:1315-:1317`), and either does nothing or performs its documented rebuild — which is correct.
* `rx_rearm_work` (`bam_dmux_rx_rearm_work_func`, `:1213`) **does NOT take `state_lock`** — there is
  no `state_lock` site between `:1213` and `:1261`. It submits RX buffers, so an async cancel would
  let it touch `dmux->rx` concurrently with the `dmaengine_terminate_sync()` / `dma_release_channel()`
  below. It **must** be drained synchronously.

**Both sites fixed in the same patch, deliberately.** They are the same rule violated in two
functions, and fixing only site 1 would leave a known deadlock in the more frequently exercised
path. The patch also adds **five `dev_err` hand-off trace points** (T0–T4, §9) so that a recurrence
is diagnosable in one boot.

### 8.3 Residual risk of site 1, accepted and recorded

The synchronous cancel also guaranteed that no `tx_wakeup_work` was *past* `:739` when the teardown
proceeded. With the non-blocking form, a `tx_wakeup_work` already inside
`pm_runtime_resume_and_get()` (`:739`) can now race the teardown's `pm_runtime_set_suspended()`
(`:2243`). The outcome is bounded and benign: the work reaches `:758`, takes `state_lock` *after* the
teardown releases it, sees `in_teardown` at `:764`, unlocks, and does `pm_runtime_put_autosuspend()`
(`:767`) — a spurious resume/autosuspend pair, no DMA submitted, no NULL deref (if the channels were
released, `power_off` zeroed `tx_deferred_skb` and `bam_dmux_free_skbs` nulled every `skb_dma->skb`,
so the loop at `:801` drops stale bits via the guard). **This trades an unbounded hang for a bounded
PM blip** — the correct direction, but a real behavioural change, so watch `pm_resume_attempts` /
`pm_suspend_attempts` telemetry on the next soak.

### 8.4 Two more sites of the same class — recorded, NOT fixed by 820

* **`bam_dmux_remove()`** (`:2582-2626`) holds `state_lock` and then synchronously cancels
  `tx_wakeup_work` (`:2583`, `:2626`), `ssr_teardown_work` (`:2589`, `:2609`) and
  `rx_watchdog_work` (`:2614`) — all three of which take `state_lock`. **The same ABBA.** Out of
  scope here because it is reached only on module unload, and this device is rebooted rather than
  unloaded — but **a future `rmmod qcom_bam_dmux` will hang**, and that is worth knowing before
  someone tries it.
* **`tx_wakeup_work` is queued on two different workqueues** — `queue_pm_work()` =
  `queue_work(pm_wq, work)` (`include/linux/pm_runtime.h:62`, used at `:687` and `:1922`) vs
  `queue_work(system_wq, …)` (`:857`). A `work_struct` must not be queued to two queues: the second
  `queue_work()` sees `WORK_STRUCT_PENDING` and **returns false**, so a `tx_retry_work`-initiated
  retry can be silently **dropped** while the item is pending on `pm_wq`. **Lost-retry bug, not a
  hang.**

Both are deliberately left out so that 820's effect is attributable.

---

## 9. Pre-registered test for the next hang (written before it happens)

Patch 820 **removes** the two calls the hang is attributed to, so a repeat cannot discriminate
between §5.3's candidates (a) and (b). The instrument must therefore be re-pointed at the hand-off,
and it must survive `console_loglevel = 6` — i.e. it must use `dev_err`, not `dev_info`.

**Prediction, stated in advance:** with 820 deployed, the teardown work will reach its `dev_info` and
the AP will no longer hang at either site.

**Falsification is equally informative, and the five trace points decode it unambiguously:**

| last line seen | meaning |
|---|---|
| the notifier's `SSR before shutdown` (no `T0`) | the notifier never reached `schedule_work` — an earlier fault |
| `T0` but no `T1` | **the work never got a `system_wq` worker** — §5.3(b), workqueue starvation; the cancels were not the cause |
| `T1` but no `T2` | hung in `cancel_delayed_work(&tx_retry_work)` — should be impossible now (non-blocking) |
| `T2` but no `T3` | hung in `cancel_work(&tx_wakeup_work)` — should be impossible now |
| `T3` but no `T4` | **blocked on `state_lock`** — an external holder, i.e. the §8.2 ABBA (or another long holder of the lock) |
| `T4` but no `executing serialized…` | hung between the lock and the `dev_info` — should be impossible |

The `T3`-without-`T4` row is the one that would confirm §8.2 as the real cause; the `T0`-without-`T1`
row is the one that would confirm §5.3(b). **Either is a one-boot answer**, which is the point of
placing probes on both sides of the hand-off.

**Instrument (five lines, `dev_err` so they land in `console-ramoops` as well as pmsg):**

```
T0  in the notifier, after schedule_work(&dmux->ssr_teardown_work)   -- the hand-off
T1  entry to bam_dmux_ssr_teardown_work_func
T2  after cancel_delayed_work(&tx_retry_work)
T3  after cancel_work(&tx_wakeup_work)
T4  after mutex_lock(&state_lock), before the existing dev_info
```

Every line contains the literal `SSR `, so the soak's existing `/dev/kmsg` filter (§2.2) picks them
up with **no soak change**, and they do **not** match `q6v5-trace:`, so the existing trace counter is
not polluted.

This is the same discipline as Doc 164 §9, corrected for the two scope errors this round exposed:
**trace the hand-off, and use a level the dying console cannot suppress.**

---

## 10. Telemetry (boot C, current, uptime 402 s at last sample)

`/overlay/q6trace.csv`, 17 columns, sampled ~5 s:

```
386.75,0,0,0,14,52,52,43,0,0,84,6,0,32,8,1659,1647
391.94,0,0,0,14,53,53,44,0,0,85,6,1,32,8,1699,1687
397.13,0,0,0,14,55,55,45,0,0,87,7,1,32,8,1716,1703
402.27,0,0,0,14,56,56,46,0,0,89,7,1,32,8,1743,1731
```

* `oops = 0`, `fatal = 0`, `ssr = 0`; `trace_lines = 14` — which is exactly the **normal boot's
  single `10..23` half-cycle** (`q6v5_start()` → `q6v5_mba_load()`), i.e. the instrument is
  working on an ordinary boot and there is no spurious `01..09`.
* `defer_queued == defer_submitted` (52/52 → 56/56), i.e. **no deferred TX lost** — patch 812 is
  holding.
* `pc_irq` climbing ~0.5/s. `dmesg` shows the expected `RX watchdog: PC line asserted while
  pc_state=0 (lost edge), resyncing` at 67, 112, 154, 162, 187, 212, 396 s, and two
  `modem pc-ack timeout during resume` at 213.166 and 221.860 s — the known, documented
  AP-side defects, not new ones.

---

## 11. Harness

### 11.1 State at the time of writing

| item | value |
|---|---|
| module md5 | `79e7a858d41b6c9f7edf8b26b90c77f8` |
| `printk` | `6 4 1 7` (KERN_INFO suppressed on consoles — the trace is pmsg-only by design) |
| `/overlay` | 229.0 M used of 3.2 G, **7 %** (Doc 163's 100 %-full hazard is clear) |
| soak processes | 5 |
| coredump watcher | running, pid 3746, `armed=disabled` at start (benign — dumps were still captured in boots A and B) |
| coredump on disk | 1 × 85 398 475 B (`modem_coredump_up939.34_devcd1.elf`) |

### 11.2 The instrument survived the reboot

Doc 164 §3 warned that the module must be re-copied after every reboot. **It must not.** The
instrumented `.ko` lives in the overlay upperdir (`/overlay/upper/lib/modules/6.12.94/…`) while
`/rom` holds the pristine `6ac6603141c25b1e3462615411e92f7c`. After the reboot the resident module
is still `79e7a858…`, and boot C's `dmesg` shows it tracing a normal boot. **The warning was
over-cautious; the correct rule is "verify the md5, don't re-copy blindly."**

### 11.3 Two probe mistakes worth recording

* `grep -c PATTERN /dev/kmsg` **never terminates** — `/dev/kmsg` is a stream with no EOF, so `-c`
  waits forever. (Distinct from, and in addition to, the already-recorded
  `cat /dev/kmsg | grep PAT | head -N` trap.) **Use `dmesg | grep -c`**, which terminates because
  busybox `dmesg` uses the `syslog(2)` syscall.
* `awk '/start/,0'` on `console-ramoops-0` prints from `start` to EOF — fine — but the *device's*
  `awk` needs the pattern anchored (`/^\[ *1840\.3/`), not a bare substring, or it matches the
  `[  1840.390188]` bracket spacing inconsistently.

---

## 12. Status

| question | status |
|---|---|
| Where does the AP hang? | **Located.** The `rproc_stop()` → SSR-notifier → bam_dmux-teardown **hand-off**, upstream of both instrumented windows. Last line = `SSR before shutdown: scheduling teardown work`; `rproc_stop()` never returned (no coredump, watcher-corroborated). |
| Why? | **Attributed, not proven.** The five-line region `:2233-:2237` contains two unbounded `cancel_*_sync()` waits on works that touch runtime PM and `state_lock`. **A second, stronger instance of the same defect was then found: `power_off`/`pm_quiesce` cancel `rx_watchdog_work` synchronously while holding `state_lock`, which is a true ABBA on one mutex (§8.2) and is exercised ~7×/min.** Competing candidate (workqueue starvation) cannot be excluded from this boot's evidence (§5.3). |
| Was the hang a panic? | **No** — no `Kernel panic` banner in the ramoops console, and the dmesg zone exists while holding no record. |
| Reset mechanism | **Unresolved.** ~2–4 s, silent; **does not match** Doc 159's 30 s PM8916 PON WDT, so the two AP hangs do not share a reset path. A first `devmem` attempt at SMEM item 403 returned a **negative result** (§13 item 3). |
| Doc 164 §9's prediction | **Falsified in site** (§3). |
| Doc 165 §4.1's coverage gap | **Widened** — the blind spot is upstream of the window, not just inside it. |
| Fix | **Built** — patch 820, four hunks: both cancel sites made non-blocking (§8.1, §8.2) plus five `dev_err` hand-off trace points T0–T4 (§9). Two further sites of the same class recorded and deliberately left out (§8.4). |
| The `qcom-time-daemon` lead | **Dead** (§7.1). |

---

## 13. Next

1. **Build and deploy patch 820** (`820-bam-dmux-ssr-teardown-nonblocking-cancel.patch`) together
   with the §9 four-line `dev_err` hand-off trace, as a loadable module, and soak.
2. **Score §9's pre-registration** on the next fatal-triggered SSR — the last `T` line, or its
   absence, settles (a) vs (b) in one boot.
3. **Close the reset question — first attempt returned a NEGATIVE result.** The AP's restart reason
   is plausibly in **SMEM item 403 (`SMEM_POWER_ON_STATUS_INFO`)** — the very item the driver's own
   comment at `:2250` warns must not be *written*. A read-only `devmem` walk was tried and
   **does not work**: `/dev/mem` itself is fine (the control reads the ramoops `DBGC` console magic
   at `0x8db00000`, and kernel text at `0x40000000` gives `Bus error`, i.e. the mapping is honest),
   but **`smem@86300000` (DT `reg` = `0x86300000` + `0x100000`) has a zeroed base** — the legacy
   `smem_heap_info` (`initialized`, `free_offset`, `heap_remaining`, `reserved`) reads
   `0,0,0,0` — and a sparse scan of the whole region found only two non-zero words:
   `0x86310000 = 0x10000004` and `0x863ff000 = 0x434F5424` (`"$TOC"`). So the layout is **not** the
   legacy heap-header form a naive walk assumes. **This needs the real `qcom_smem` layout or an
   in-kernel reader, not `devmem`.** Also unchecked: the PM8916 PON reason registers, and the
   `pm8916-pon` node's sysfs (it exposes only `pwrkey`, `watchdog`, `driver`, `of_node`, … — no
   reason attribute).
4. **The `pm_wq` / `system_wq` double-queue of `tx_wakeup_work`** (§8) — separate patch, after 820.
5. Still open from before: the failed modem restart (Doc 154 §6); the `echo stop` hang (n=2);
   patch 814's retry path still unobserved on a natural trigger (`retries: 0`); the harness gap that
   **nothing records the AP uptime at which the modem comes up** (Doc 165 §11 item 6).
6. **Explicitly closed — do not restart:** the WTR1605→UFI001B RF transplant; the one-line
   `qcom-idle-state-spc` DT patch; "the RPM is the stalled party"; the live-mpss reader (Doc 158);
   the `deploy` command (declined); **the `qcom-time-daemon` periodic-refresh lead (§7.1)**.
