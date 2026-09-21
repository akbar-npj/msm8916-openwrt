# 166 — The AP hang is the SSR *teardown hand-off*, and it is upstream of the instrumented window

**Date:** 2026-09-21
**Subject:** AP-side kernel diagnostic. **No modem firmware, no WCNSS firmware, no DTS and no
bootloader byte was changed.** No new build was produced or deployed this round; the resident
module is still Doc 164's `msm89xx/patches/817-q6v5-ssr-window-trace.patch` as the loadable module
`kmod-qcom-rproc-modem`.
**Result:** the AP hang was **caught on a spontaneous fatal** (crash #2 at AP 1840.390 s,
`lte_ml1_sleepmgr_stm.c:4054`). The **last line a userspace reader copied** was the SSR notifier's
*hand-off*, `bam_dmux: SSR before shutdown: scheduling teardown work` — and **the teardown work's
own first print (`qcom_bam_dmux.c:2238`) never appeared**, and **no coredump device was created**, so
`rproc_stop()` never returned. Doc 164 §9's pre-registration is therefore **falsified in site**: the
hang is not in the 23-step window and not in Doc 159's window either — it is **upstream of both**.

**Three corrections were made to this document after its first draft, and they matter more than the
original finding. Read §2.2b and §5.5 before citing anything here.** (i) The `pmsg` sink is fed by a
**userspace** reader, so "no further line appeared" is *proven* only for `dev_err`-and-above — the
missing `dev_info` line may simply never have been copied. (ii) `wwan0at0 disconnected` is an
**rpmsg/SMD** line, not a bam_dmux one. (iii) **§5.4 — which proposed that the stall is in the
logging/console path — is RETRACTED in §5.5**, because its mechanism was checked against this
kernel's source and **does not exist in Linux 6.12**: the global `logbuf_lock` was removed in v5.10,
`printk_ringbuffer.c` is explicitly lockless, and neither `/dev/kmsg` nor `dmesg` takes a global
lock. A `/dev/kmsg` reader *sleeps on `log_wait`* when the ring is quiet, so its silence is expected,
not diagnostic. §8 nonetheless fixes **two provable deadlocks** — including a **true ABBA on one
mutex** — and ships five `dev_err` hand-off probes with a pre-registered decode table; it is
justified on its own merits, and with §5.4 gone it is **again the leading explanation** for the hang,
though still not proven to be it.

---

## 0. SOP compliance

| SOP step | Status |
|---|---|
| No baseband change; no firmware write | **Honoured.** Zero firmware bytes touched. Device-side writes this round: **none** (read-only probes). |
| Dual-firmware comparative protocol | **Not applicable, and deliberately so** — same reasoning as Doc 164 §0 / Doc 165 §0. This is AP-side kernel instrumentation. There is no Android counterpart to compare against, and **no baseband hypothesis is advanced**. The comparative protocol exists to stop blind *baseband* patching; nothing here touches the baseband. |
| Verify against ground truth before acting | **Honoured — and §5.5 is the strongest instance.** Every claim in §5 is read from the **live build tree** (`openwrt/build_dir/.../drivers/net/wwan/qcom_bam_dmux.c`), line-numbered. `queue_pm_work` was not assumed — it was resolved to `include/linux/pm_runtime.h:62`. The "read the definition, not the name" rule (memory `feedback_read_the_definition_not_the_name`) is what forced the `cancel_work_sync` analysis instead of accepting the function's apparent intent. And the §5.4 mechanism was not argued but **grep'd out of the kernel source** (`kernel/printk/printk.c`, `printk_ringbuffer.c`, `include/linux/printk.h`) in the same tree the module was built from — and found to be absent. `rx_telemetry_show` was likewise checked to take only `rx_lock`, not `state_lock`, before the CSV was accepted as a liveness witness. |
| Hash the artifact, verify it in situ | **Honoured.** Resident **tracer** `qcom_q6v5_mss.ko` md5 `79e7a858d41b6c9f7edf8b26b90c77f8`; the instrument **survived the reboot** (§11.2). The **bam_dmux** module is a different artifact (`eca269f1…`, §11.1) — see the trap note there. |
| Never classify a fatal by its `file:line` | **Honoured.** `lte_ml1_sleepmgr_stm.c:4054` is used only as an identity label (Doc 162 §7's refinement: ask what *this* signature's period is). The fatal is not the subject of this document — the **teardown** is. |
| Do not treat corpus docs as fact | **Honoured, and this round retracts three of my own in-session findings.** (§7.1) the "stale `qcom-time-daemon` performing a periodic ATS_USER refresh" lead is **dead**, disproved by direct process inspection. (§2.2b, §5.4) this document's own first-draft claim that "no further line appeared" was **overstated** — the `pmsg` sink is userspace-fed — and the "five-line region" attribution is therefore **downgraded from a cause to a candidate**. (§5.5) **the §5.4 logging-path hypothesis is itself retracted**, after checking its mechanism against the kernel source: `logbuf_lock` does not exist in Linux 6.12. All three corrections are recorded in place rather than silently rewritten. |
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
filter. **None of them fired.**

### 2.2b — but that is NOT the proof it looks like: the `pmsg` sink needs a *userspace* reader

**This correction was made after the first draft of this document and it materially weakens §2.2's
claim. Read it before citing §2.2.**

`pmsg-ramoops-0` is not an in-kernel sink. It is written by the soak, in userspace, from a shell loop:

```sh
cat /dev/kmsg | while IFS= read -r line; do
	case "$line" in *q6v5-trace:*|*"SSR "*|...) printf '%s\n' "$line" >> /dev/pmsg0 ;;
	esac
done
```

So `pmsg-ramoops-0`'s last line is **the last line a userspace process managed to copy**, not
necessarily the last line `printk` stored. If userspace stops — or if it blocks reading `/dev/kmsg`,
which needs the **logbuf lock** — the mirror stops even though the kernel is still printing.

**What is therefore actually proven, and what is not:**

| claim | status |
|---|---|
| No `dev_err`-or-higher message was emitted after `recovering` (1840.406218) | **Proven.** `console-ramoops-0` is an in-kernel `struct console` sink and carries level ≤ 6; it ends there. |
| A userspace process was still running and still copying at 1840.413105 | **Proven.** It copied that line. |
| **No `dev_info` message was emitted after `SSR before shutdown`** | **NOT proven.** `executing serialized asynchronous SSR teardown` is `dev_info`; it may have been printed and simply never copied. |
| No `Kernel panic` / `BUG:` / oops banner | **Proven.** Those are `KERN_EMERG`/`KERN_ERR` and would have landed in `console-ramoops`. |
| `rproc_stop()` never returned | **Proven, independently of `printk`** — by the coredump watcher (§2.3). |

**And the cessation of *both* sinks at once, together with the absence of the coredump, is itself
evidence about the shape of the stall.** A single mutex cycle in one driver thread would leave
userspace running on the other three CPUs and would let the other SSR consumers proceed. Instead
**three independent things all stop within ~7 ms of the notifier callback**: the SMD/rpmsg teardown
thread, bam_dmux's teardown work, and the userspace `/dev/kmsg` reader. §5.4 adds the competing
hypothesis this implies.

> **CORRECTED — see §5.5.** "Three independent things" is **one too many**. A `/dev/kmsg` reader
> (`devkmsg_read()`, `printk.c:822`) **sleeps on `log_wait`** whenever the ring has no new records,
> so its silence is not a third witness — it is the expected appearance of a healthy reader with
> nothing to read. There are **two** print sites that never emitted, and **one** blocked step in the
> `QCOM_SSR_BEFORE_SHUTDOWN` blocking-notifier chain explains both. §5.4's own mechanism is also
> retracted there. The paragraph above is left as first written so the correction is auditable.

### 2.2c — `wwan0at0 disconnected` is not bam_dmux's line

> **PARTIALLY SUPERSEDED BY DOC 167 §3.** The *attribution* below is correct and stands: the line is
> `wwan_remove_port()` from the rpmsg/SMD teardown, not bam_dmux. But the **ordering argument** at the
> end of this section is **retracted** — Doc 167 measured the two lines' relative order **flipping
> within a single boot**, because the bam_dmux teardown work and the rpmsg/SMD teardown are
> concurrent. "Ends on two lines vs one" therefore does **not** establish a step ordering. The
> conclusion (this hang is earlier) survives for a better reason: **the discriminator is *which* line
> is missing** — `wwan0at0 disconnected` runs *inside* `q6v5_stop()`, after the notifier returns, so
> its absence means `rproc_stop()` never got past the notifier; in the `echo stop` hang it *did*
> appear, so there only the teardown **work** failed. See Doc 167 §3.

The healthy sequence's second line is easy to misattribute:

```
938.808624  bam_dmux: SSR before shutdown: scheduling teardown work
938.810877  wwan wwan0: port wwan0at0 disconnected        (+2.253 ms)
938.811371  bam_dmux: executing serialized asynchronous SSR teardown   (+2.747 ms)
```

`wwan_remove_port()` — the function that prints `port %s disconnected` (`wwan_core.c:529`) — is
called from **`rpmsg_wwan_ctrl.c:145`** (`rpwwan_remove`), `mhi_wwan_ctrl.c:255` and
`wwan_hwsim.c:239`. **`qcom_bam_dmux.c` contains no `wwan_*` port calls at all.** So that line is
emitted by the **rpmsg/SMD** teardown when the modem's SMD channels are unregistered — a *different
component* from the bam_dmux teardown work, running on a *different thread*.

That matters twice:

1. Its absence in this hang is evidence about the **SMD/rpmsg** path, not about bam_dmux — and it
   was missing too, so the stall is not confined to one driver.
2. **It fixes the ordering between this hang and Doc 151/157's `echo stop` hang.** Memory quirk 11
   records that the `echo stop` hang ends on **two** lines — `SSR before shutdown` **and**
   `wwan0at0 disconnected`. **Our spontaneous hang ends on one.** So the `echo stop` hang is one step
   *later* than this one: this region contains **at least two adjacent hang points**, and ours is the
   earlier. Any fix must be scored against the right one.

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

### 5.4 A third candidate: the stall is in the *logging* path — **RETRACTED in §5.5**

> **This section is RETRACTED.** Its mechanism was checked against this kernel's source and is
> **false for Linux 6.12**: there is no global log-buffer lock, so no stalled printk can silence a
> `/dev/kmsg` reader or `dmesg`. §5.5 gives the verification and the corrected reading of the same
> evidence. The section is kept verbatim below because the *reasoning error* — inferring a
> mechanism from a pattern without checking that the mechanism exists in this kernel — is the
> reusable lesson, and because §5.5's correction depends on knowing exactly what was claimed.

§2.2b showed that `pmsg-ramoops-0` is fed by a userspace reader, and that three independent things —
the SMD/rpmsg teardown, bam_dmux's teardown work, and that reader — all stop within ~7 ms of the
notifier callback. **A single mutex cycle inside one driver cannot explain that**, because the other
three CPUs would keep running and the other SSR consumers would keep printing.

**What can explain it:** a stall in the kernel's logging/console path.

* `printk` stores into the ring under the **logbuf lock**, and `/dev/kmsg` readers take the same
  lock. A thread that dies (or spins) holding it silences *every* sink and blocks every reader —
  exactly the observed pattern.
* This device's console is `ttyMSM0,115200` and it **serialises synchronously**: measured
  **1.03 ms + 0.0904 ms/char**, i.e. ~8.3 ms for an 80-character line (memory
  `reference_hmu05_console_cost_and_trace_sinks`). `console_loglevel = 6` removed `KERN_INFO` from
  the console but **`dev_err` still goes there** — and the last line before the silence,
  `recovering`, *is* a `dev_err`. So the console was actively writing in the final millisecond.
* If the serial console's write blocks (UART not draining, TX interrupt lost), the console lock is
  held indefinitely and every subsequent `printk` that targets the console blocks with it.

**Under this hypothesis the driver-level deadlocks in §8 are real but incidental**, and the reason
`rproc_stop()` never returned is that it tried to log while the log path was wedged — which also
explains why the coredump never appeared without needing any bam_dmux lock cycle.

**I do not claim this hypothesis is true.** I claim §2.2's original "nothing further happened" was
overstated, and that this is a live alternative that the evidence cannot separate from §5.3(a).

**A zero-cost discriminator already exists and should be read first on the next hang.** The soak
writes `/overlay/q6trace.csv` from a userspace shell loop every ~5 s. Compare its **last timestamp**
against the last `pmsg` line:

| CSV vs pmsg | meaning |
|---|---|
| CSV stops *before* the pmsg line | userspace died first → a global/scheduling stall |
| CSV continues *past* it | userspace was alive while the log stopped → **the logging path is the stall**, and the driver locks are innocent |

That single comparison separates §5.4 from everything else, and it needs no new instrument — only
the discipline of reading the CSV on a hang. **Add a second, independent beacon anyway** (§13
item 3): a heartbeat that does not touch `printk` or `/dev/kmsg` at all, so "alive but silent" is
distinguishable from "dead".

### 5.5 §5.4 is RETRACTED — verified against this kernel's own source

The §5.4 mechanism was checked by reading `kernel/printk/` **in the live build tree**
(`openwrt/build_dir/target-aarch64_generic_musl/linux-msm89xx_msm8916/linux-6.12.94/`), the same tree
the running module was built from. **The mechanism does not exist in Linux 6.12.**

| §5.4 claim | verification | verdict |
|---|---|---|
| "`printk` stores into the ring under the **logbuf lock**" | `grep -rn logbuf_lock kernel/printk/ include/linux/printk.h` → **no matches.** The global `logbuf_lock` was removed in v5.10, replaced by John Ogness's lockless ring. | **FALSE** |
| "`/dev/kmsg` readers take the same lock" | `printk_ringbuffer.c` contains **no spinlock at all** — its own header says *"readers and writers to **locklessly** synchronize access to the data"*. `devkmsg_read()` (`printk.c:822`) takes only `mutex_lock_interruptible(&user->lock)`, a **per-file-descriptor** mutex. | **FALSE** |
| "`dmesg` takes the same lock" | busybox `dmesg` = `syslog(2)` `SYSLOG_ACTION_READ_ALL` → `syslog_print_all()` (`printk.c:1684`). It takes `syslog_lock` **only inside `if (clear)`** — i.e. only for `dmesg -c`. The record walk is lockless. | **FALSE** |
| "a wedged console lock blocks every subsequent printk" | `console_lock` gates **console output**, not the ring. `vprintk_store()` stores locklessly; the console flush falls back to deferral/kthread when `console_trylock()` fails. Stores continue. | **FALSE** |

**What is still true:** the console (`ttyMSM0,115200`, ~8.3 ms per `dev_err` line) is a real
single point of failure *for the console sink*, and it is still worth testing (§13 item 4). But a
wedged console **cannot** stop a `/dev/kmsg` reader — so it cannot explain the pmsg mirror stopping.

**The corrected reading of the evidence — and it is simpler, not more complicated.** `devkmsg_read()`
does not poll: it **sleeps on `log_wait`** when the ring holds no new records
(`wait_event_interruptible(log_wait, printk_get_next_message(...))`, `printk.c:848`). Therefore:

* The pmsg filter going silent means **exactly one thing: nothing further was stored in the ring.**
  It is *not* a stalled reader, and its silence is **not** independent evidence of a hang — it is
  what a healthy `/dev/kmsg` reader *always* looks like when the kernel has nothing more to say.
* So the "**three independent consumers stop within ~7 ms**" of §2.2b collapses to **two print sites
  that never emitted** — bam_dmux's `:2238` and the rpmsg/SMD `wwan0at0 disconnected` — plus one
  reader that simply had nothing to read. **Three witnesses were counted where there were two.**
* And **one** blocked step in the SSR hand-off explains both missing lines, because the
  `QCOM_SSR_BEFORE_SHUTDOWN` chain is a **blocking notifier chain**: if an entry after bam_dmux's
  does not return, nothing downstream of it — including the SMD teardown that prints
  `wwan0at0 disconnected` and `rproc_stop()`'s own trace steps — ever runs. **§5.4's premise, "a
  single mutex cycle inside one driver cannot explain that", is therefore wrong: it can.**

**Consequence for the rest of this document.** §5.4 was the only reason patch 820 was downgraded, and
it is now gone. The three candidates in §5.1/§5.2/§5.3 stand as written, and **the `state_lock` ABBA
(§8.2) and the unbounded `cancel_*_sync` waits (§8.1) are back to being the leading explanation** —
not proven, but no longer competing with a hypothesis that this kernel cannot support.

**Consequence for the discriminator — the CSV survives, with corrected semantics.** §5.4's table is
right about *what to do* and wrong about *what it means*. `/overlay/q6trace.csv` is written by a loop
that reads `/proc/uptime`, `dmesg`, `$TRACE` and the bam_dmux telemetry sysfs — **none of which takes
`state_lock`** (`rx_telemetry_show()`, `qcom_bam_dmux.c:2116`, takes only `dmux->rx_lock` for a
bounded loop). So the CSV is a valid **liveness** witness:

| CSV vs last pmsg line | corrected meaning |
|---|---|
| CSV **continues** past it | the kernel and its userspace are **alive**; only the printing stopped ⇒ the stall is confined to the code path that stopped printing (a subsystem deadlock, **not** a global stall, and **not** a log-path lock) |
| CSV **stops** at/before it | the AP genuinely stalled ⇒ global stall, and a watchdog reset is expected |

Note this is the *opposite polarity* from §5.4's table for the "continues" row — §5.4 read "CSV alive"
as exonerating the driver locks. It does not: it says the stall is **localised**, which is exactly
what a driver-level deadlock on one thread looks like.

**The beacon is still worth adding, for a different reason than §5.4 gave** (§13 item 3): the CSV
depends on `dmesg`, sysfs and a shell loop; a heartbeat that touches **only `/proc/uptime` and a
file** cannot be confounded by any of them. And a `/dev/kmsg`-based watcher — which is what both
existing sinks are — **structurally cannot** serve as a liveness beacon, because it sleeps on
`log_wait` precisely when the kernel stops printing.

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

## 8. Patch 820 — two *provable* deadlocks fixed (but §5.4 says this is not the cure)

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

### 9.1 The instrument's own cost — measured before it was trusted (Doc 164 §5's rule, applied again)

Doc 164 §5's rule is that an instrument must be measured against the phenomenon before it is
trusted. **These five probes are `dev_err`, so unlike the `q6v5` trace they are *not* free at
`console_loglevel = 6`** — `suppress_message_printing()` only suppresses level ≥ 6, and `dev_err`
is level 3. Each one therefore takes the serial console for a full synchronous write:

| line | chars (with `[ts] qcom-q6v5-mss 4080000.remoteproc:bam-dmux: ` prefix) | cost @ 1.03 + 0.0904 ms/char |
|---|---|---|
| `bam_dmux: SSR teardown T0 scheduled` | ~95 | **≈ 9.6 ms** |
| `… T1 entry` / `… T2 …` / `… T3 …` / `… T4 …` | ~92–105 | ≈ 9.4–10.5 ms each |

**T0 is the one that matters, because it is on `rproc_stop()`'s critical path** — it sits in the SSR
notifier callback, which runs synchronously inside the stop. For comparison, the *entire* healthy
hand-off measured **7.453 ms** from `recovering` to the notifier line. So T0 adds a delay **larger
than the interval it is placed in.**

**Why it is nonetheless acceptable, stated precisely:**

* T0 is placed **after** `schedule_work(&dmux->ssr_teardown_work)` (verified in the re-prepared tree,
  `qcom_bam_dmux.c:2434` → probe at `:2441`). So the ~9.6 ms is spent *after* the work is queued —
  **the probe cannot prevent the teardown work from starting**, which is the one thing T0 exists to
  prove. It delays `rproc_stop()`'s continuation only.
* T1–T4 run in the **teardown work**, which is asynchronous — off `rproc_stop()`'s critical path.
* Only `dev_err` takes the console here; the suppressed `dev_info` lines (`:2303`, `:2444`) do not,
  so the probes are not queuing behind a stream of invisible writers.

**The ambiguity this creates, pre-registered rather than discovered later:** if the hang disappears
with 820 deployed, **the improvement cannot be attributed to the non-blocking cancels alone** — the
~9.6 ms T0 delay, inserted exactly at the hand-off, is a confound. Distinguishing them needs one more
boot: deploy 820's **two cancel changes without the probes** and soak. If the hang returns, the
cancels are the cause; if it does not, the delay was.

**This is a strictly better position than Doc 164 was in**, where the 23-line trace cost up to
**190 ms against a 45.481 ms window (4.2×)** and was only noticed afterwards. Here the cost is known
in advance, is confined to one line on the critical path, and the confound it creates is written down
before the run rather than after it.

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
| **tracer** module md5 | `qcom_q6v5_mss.ko` = `79e7a858d41b6c9f7edf8b26b90c77f8` (Doc 164's patch 817) |
| **bam_dmux** module md5 | `qcom_bam_dmux.ko` = `eca269f10a685a83a8b679bc7be38d1d` (239 464 B, **pre-820** — `strings \| grep -c "SSR teardown T"` = 0) |
| `printk` | `6 4 1 7` (KERN_INFO suppressed on consoles — the trace is pmsg-only by design) |
| `/overlay` | 229.0 M used of 3.2 G, **7 %** (Doc 163's 100 %-full hazard is clear) |
| soak processes | 5 |
| coredump watcher | running, pid 3746, `armed=disabled` at start (benign — dumps were still captured in boots A and B) |
| coredump on disk | 1 × 85 398 475 B (`modem_coredump_up939.34_devcd1.elf`) |

> **Trap fixed 2026-09-21.** This table originally carried a single unlabelled `module md5` row with
> `79e7a858…`, and §11.2 said "the resident module is still `79e7a858…`". **That md5 is
> `qcom_q6v5_mss.ko`, not `qcom_bam_dmux.ko`** — two different modules were conflated. §11.2's claim
> (the instrument survives a reboot) is about the *tracer* and is correct as re-worded; but anyone
> reading the old table as "bam_dmux is `79e7a858…`" would have deployed 820 against the wrong
> baseline. **Always name the module alongside the hash.** Verified on boot C by
> `md5sum /lib/modules/6.12.94/*.ko`: the bam_dmux module is `eca269f1…`, and `/rom`'s pristine copy
> is `6ac66031…`.

### 11.2 The instrument survived the reboot

Doc 164 §3 warned that the module must be re-copied after every reboot. **It must not.** The
instrumented `.ko` lives in the overlay upperdir (`/overlay/upper/lib/modules/6.12.94/…`) while
`/rom` holds the pristine copy. After the reboot the resident **tracer** (`qcom_q6v5_mss.ko`) is
still `79e7a858…`, and boot C's `dmesg` shows it tracing a normal boot. **The warning was
over-cautious; the correct rule is "verify the md5, don't re-copy blindly."** The same holds for
`qcom_bam_dmux.ko` — the rule is generic, but the hash must be checked **per module** (§11.1's trap).

### 11.3 The §5.4 discriminator now has a **healthy control**

§5.4 proposes reading `/overlay/q6trace.csv`'s last timestamp against the last `pmsg` line on the
next hang. That comparison is only meaningful if the CSV's first column really is AP uptime — so it
was checked on a boot that did **not** hang.

Boot C, probed at AP uptime **1518.90 s** (i.e. **~602 s after its fatal**, which it survived):

| observation | value |
|---|---|
| `/overlay/q6trace.csv` | 304 rows, last row timestamp **1591.80** — and AP uptime at the *next* probe was ~1590 s |
| `q6trace.csv` column 1 vs `/proc/uptime` | **equal to within the 5 s sampling period** ⇒ column 1 **is** AP uptime |
| CSV liveness | **alive**, continuously written straight through and long past the fatal |
| `/overlay/coredump_live/` | **2** dumps, `…up918.22_devcd1.elf` (boot C's fatal, +1.7 s) and `…up939.34_devcd1.elf` (boot B's crash #1) |
| `/overlay` | 310.5 M of 3.2 G, **10 %** — the 163 MB of dumps account for the growth since §11.1 |

**So the discriminator's *mechanics* are confirmed**: a healthy boot's CSV tracks AP uptime and keeps
advancing across an SSR. Its **interpretation is corrected by §5.5** — §5.4's polarity was backwards
for the "CSV continues" row. The corrected table:

| CSV vs last `pmsg` line | meaning |
|---|---|
| CSV **continues** past it | kernel + userspace **alive**; only printing stopped ⇒ stall is **confined to the code path that stopped printing** — a subsystem deadlock, which is exactly what a driver-level lock cycle on one thread looks like. **Does NOT exonerate §8.** |
| CSV **stops** at/before it | the AP genuinely stalled ⇒ global stall, watchdog reset expected |

Also note boot C's fatal **did** produce a coredump, +1.7 s after the fatal time — the normal
outcome, and the exact contrast with boot B's crash #2, which produced none (§2.3).

**And the beacon's justification is now structural rather than speculative.** Both existing sinks are
`/dev/kmsg` readers, and `devkmsg_read()` **sleeps on `log_wait`** whenever the ring is quiet
(`printk.c:848`) — so *by construction* they cannot distinguish "the kernel has nothing to say" from
"the AP is dead". A beacon built on `/proc/uptime` + a file can, and it also removes the CSV's
dependencies on `dmesg`, on the bam_dmux telemetry sysfs and on a shell loop. **That is §13 item 3.**

### 11.4 Two probe mistakes worth recording

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
| Where does the AP hang? | **Located to a region, not a line.** The stall is at or immediately after the `QCOM_SSR_BEFORE_SHUTDOWN` **hand-off** (`bam_dmux: SSR before shutdown`, `qcom_bam_dmux.c:2364` — the last line userspace copied) and is **upstream of both instrumented windows**. It blocks **three independent consumers at once**: bam_dmux's teardown work (`:2238` never copied), the **rpmsg/SMD** teardown (`wwan0at0 disconnected` — a `rpmsg_wwan_ctrl.c:145` line, not a bam_dmux one — never copied), and the userspace `/dev/kmsg` reader. `rproc_stop()` never returned (no coredump, watcher-corroborated). |
| Why? | **Two candidates, and one retracted.** (1) The teardown work hung in `:2233-:2237` — two unbounded `cancel_*_sync()` on works that touch runtime PM and `state_lock` (§5.2). (2) The work never got a `system_wq` worker (§5.3). ~~(3) The stall is in the logging/console path (§5.4)~~ — **RETRACTED in §5.5, verified against this kernel's source**: there is no `logbuf_lock` in Linux 6.12, `printk_ringbuffer.c` is lockless, `devkmsg_read()` takes only a per-fd mutex, and `syslog_print_all()` takes `syslog_lock` only when clearing. A wedged console cannot stop a `/dev/kmsg` reader. The "three independent consumers" of §2.2b were **two** print sites plus one reader that had nothing to read. **§2.2's original "nothing further happened" was still overstated** (§2.2b). |
| Was the hang a panic? | **No** — no `Kernel panic` banner in the ramoops console, and the dmesg zone exists while holding no record. |
| Reset mechanism | **Unresolved.** ~2–4 s, silent; **does not match** Doc 159's 30 s PM8916 PON WDT, so the two AP hangs do not share a reset path. A first `devmem` attempt at SMEM item 403 returned a **negative result** (§13 item 5). |
| Doc 164 §9's prediction | **Falsified in site** (§3). |
| Doc 165 §4.1's coverage gap | **Widened** — the blind spot is upstream of the window, not just inside it. |
| Relation to Doc 151/157's `echo stop` hang | **This is one step EARLIER.** The `echo stop` hang ends on two lines (`SSR before shutdown` **and** `wwan0at0 disconnected`); this one ends on one. The region has **at least two adjacent hang points** (§2.2c). |
| Fix | **Patch 820 built** — four hunks: both cancel sites made non-blocking (§8.1, §8.2) plus five `dev_err` hand-off probes T0–T4 (§9). It fixes **two provable deadlocks** (one of them a true ABBA, §8.2), and with §5.4 retracted (§5.5) it is **again the leading explanation** for the hang — though still not proven. Two further sites of the same class recorded and deliberately left out (§8.4). |
| The instrument's own cost | **Measured before deploying, not after (§9.1).** The five `dev_err` probes are **not free at `console_loglevel = 6`** (`dev_err` is level 3), so each costs a full synchronous console write: **T0 ≈ 9.6 ms**, T1–T4 ≈ 9.4–10.5 ms each, against a healthy hand-off of **7.453 ms**. T0 is on `rproc_stop()`'s critical path but sits **after** `schedule_work()` (`:2434` → `:2441`), so it cannot prevent the work starting. **Pre-registered ambiguity: if the hang disappears, the cancels and the T0 delay are both candidate causes** — resolving it needs one boot with the cancels and no probes (§13 item 7). |
| The `qcom-time-daemon` lead | **Dead** (§7.1). |

---

## 13. Next

1. **Deploy patch 820** (`820-bam-dmux-ssr-teardown-nonblocking-cancel.patch`) together with the
   §9 five-point `dev_err` hand-off trace, as a loadable module, and soak. It removes a **provable**
   ABBA (§8.2) and a provable unbounded wait (§8.1), and it carries the T0–T4 probes. With §5.4
   retracted (§5.5) it is **again the leading explanation** for the hang — but it is not proven.
2. **On the next hang, read `/overlay/q6trace.csv` BEFORE anything else**, and read it with §5.5's
   corrected polarity: **CSV alive + `pmsg` silent ⇒ the AP is alive and the stall is localised**
   (which is what a driver deadlock looks like, so §8 stays in play); **CSV stopped ⇒ global stall**.
   Then score §9's T0–T4 decode table.
3. **Add a non-`printk` liveness beacon** — now justified **structurally**, not speculatively (§5.5):
   both existing sinks are `/dev/kmsg` readers, and `devkmsg_read()` **sleeps on `log_wait`** when the
   ring is quiet, so they *cannot* distinguish "nothing to say" from "dead". A small process that
   appends `/proc/uptime` to a file on `/overlay` every 2 s — **touching no `dmesg`, no `/dev/kmsg`,
   no sysfs, no shell builtin beyond the loop itself** — is immune to every confound identified so
   far. Two such processes writing to two separate files, so a single file's failure is not silent.
4. **The logging hypothesis is retracted as a *mechanism* (§5.5), but the console is still worth one
   cheap test.** It cannot silence a `/dev/kmsg` reader, but it remains the one component whose cost
   is measured (1.03 ms + 0.0904 ms/char) and it is still the only sink that `dev_err` reaches.
   Booting once with the serial console removed from the kernel command line (or
   `console_loglevel = 1`) costs one boot and definitively closes it.
5. **Close the reset question — first attempt returned a NEGATIVE result.** The AP's restart reason
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
   reason attribute). **debugfs IS mounted**, so an in-kernel reader is available.
6. **The `pm_wq` / `system_wq` double-queue of `tx_wakeup_work`** (§8.4) — separate patch, after 820.
7. **If the hang DISAPPEARS with 820, re-run without the probes before claiming the cancels fixed
   it.** §9.1 measures T0 at **≈ 9.6 ms** of synchronous console time inside the SSR notifier — on
   `rproc_stop()`'s critical path, and larger than the 7.453 ms healthy hand-off it sits in. So a
   disappearance has two candidate causes. One extra boot with the two cancel changes and **no**
   `dev_err` probes separates them. **Do not skip this; it is the difference between "fixed" and
   "masked".**
8. Still open from before: the failed modem restart (Doc 154 §6); the `echo stop` hang (n=2, and now
   known to be **one step later** than this one, §2.2c); patch 814's retry path still unobserved on a
   natural trigger (`retries: 0`); the harness gap that **nothing records the AP uptime at which the
   modem comes up** (Doc 165 §11 item 6).
9. **Explicitly closed — do not restart:** the WTR1605→UFI001B RF transplant; the one-line
   `qcom-idle-state-spc` DT patch; "the RPM is the stalled party"; the live-mpss reader (Doc 158);
   the `deploy` command (declined); **the `qcom-time-daemon` periodic-refresh lead (§7.1)**.
