# Doc 169 — The AP reset is a use-after-free of `channel->fblockread_event` in the SMD poll path

**Date:** 2026-09-21
**Device:** HMU05 4G dongle (MSM8916 + WTR1605 + QFE2320), OpenWrt 25.12.5 / Linux 6.12.94
**Baseband:** stock HMU05 (verified clean; `modem.mdt` unchanged — Doc 153)
**Deployed module:** patch 821 (`qcom_bam_dmux.ko` md5 `364376867224df54d47b9b7a06528db3`, 241232 B)
**Patch written this round:** **822** (`822-rpmsg-smd-drain-pollers-before-free.patch`)
**Trigger used:** `echo stop > /sys/class/remoteproc/remoteproc0/state` (Doc 168's forcing function)

**Status: ROOT CAUSE FOUND, PROVEN, AND FIXED (fix built, not yet scored).** The AP reset that
follows a *completed* SSR teardown is a **use-after-free of `channel->fblockread_event`**:
`qcom_smd_edge_release()` `kfree()`s the SMD channel while a userspace task blocked in
`ppoll()` still holds a `poll_table_entry` linked in that wait queue. The clobbered word is
provably the **SLUB freelist pointer** at offset 96 of the kmalloc-192 object (§4).

---

## §0 SOP compliance statement

**Dual-Firmware Comparative SOP — steps taken:**

* **Ground truth read before any patch was written.** Every function in the failing path was
  read from the *live* kernel tree, not from memory or from a doc: `wwan_port_fops_poll()`
  (`drivers/net/wwan/wwan_core.c:772`), `wwan_port_fops_open/release` (`:663`/`:683`),
  `wwan_port_destroy()` (`:354`), `wwan_remove_port()` (`:514`), `rpmsg_wwan_ctrl_tx_poll()`
  and `rpmsg_wwan_ctrl_remove()` (`drivers/net/wwan/rpmsg_wwan_ctrl.c:83`/`:142`),
  `rpmsg_poll()` (`drivers/rpmsg/rpmsg_core.c:292`), `qcom_smd_poll()`
  (`drivers/rpmsg/qcom_smd.c:991`), `qcom_smd_edge_release()` (`:1440`),
  `qcom_smd_unregister_edge()` (`:1539`), `qcom_smd_create_device()` (`:1072`),
  `bam_dmux_power_off()` and `bam_dmux_rx_rearm_work_func()`
  (`drivers/net/wwan/qcom_bam_dmux.c:1561`/`:1213`), and `bam_dma_terminate_all()` /
  `bam_start_dma()` (`drivers/dma/qcom/bam_dma.c:725`/`:1015`).
* **Struct layouts were measured, not assumed.** `pahole` against the build tree's DWARF
  gave `sizeof(struct qcom_smd_channel)` = 176 and `offsetof(..., fblockread_event)` = 88;
  `sizeof(struct wwan_port)` = 912 and `offsetof(..., waitqueue)` = 792. These two numbers are
  what turn a raw fault address into an identification (§4).
* **The instrument was measured before it was trusted** (Doc 166 §9.1). The sampler's period
  was measured (≈24 ms in a dry run) before the experiment; §9 records that a *stale* pstore
  record almost became a false result, and that `console-ramoops` and `dmesg` have **different
  log-level filters** and must not be compared line-for-line.
* **A lock/wait census was taken, not guessed.** All three blocking steps between `T4` and `T5`
  were enumerated from source, and the claim in the code's own comment at
  `qcom_bam_dmux.c:1578` ("`rx_rearm_work` … does NOT take `state_lock`") was *verified by
  reading the work function* rather than believed.
* **The mechanism was checked against the kernel actually being run**: `bam_dma.c` in *this*
  tree was grepped for `pm_runtime_get_sync()` to confirm that `dma_async_issue_pending()` can
  really block (it can: `bam_start_dma()` at `:1032`), rather than trusting the comment.
* **No blind patching.** Patch 822 changes **one** thing: it waits for outstanding pollers to
  unlink before the channels are freed. It does not alter the teardown, the cancels, or the
  poll path itself.
* **Falsification is reported as such.** §5 records that the leading hypothesis carried in from
  Doc 168 §5 (H5, `cancel_delayed_work_sync(&rx_rearm_work)`) is **demoted** — the evidence
  against it is that the same call already runs ~327 times per boot inside
  `bam_dmux_pm_quiesce()` without hanging. §6 records that this defect does **not** by itself
  explain why the four natural fatals of the previous boot recovered.
* **Pre-registration (§8) is written before the fix is scored, not after.**

**Steps not taken, and why:** the fix was **not** validated against the natural-fatal path
first, because the `echo stop` reproduction is deterministic and ~3 s while the natural path
needs ~900 s per sample. The `echo stop` path is a superset of the failure (it reaches the same
`qcom_smd_unregister_edge()`); the natural-path question is deferred to §8 Question B.

---

## §1 Headline

**Doc 168 got the teardown to *complete*; the AP still reset, 57 ms later, from a completely
different defect — and that defect is now identified, proven, and fixed.**

* The SSR teardown now runs to completion (`T1`→`T9`) and the modem powers down.
* **57 ms after `T9`**, a task in `poll_freewait()` walks a wait-queue head whose memory has
  already been recycled, and the AP resets ~1.4 s later with no further console output.
* The queue is **`channel->fblockread_event`**, inside `struct qcom_smd_channel`, freed by
  `qcom_smd_edge_release()`'s bare `kfree(channel)`.
* The poller is **`qmi-proxy`**, fd 7 → `/dev/wwan0qmi0`, reached through
  `wwan_port_fops_poll` → `rpmsg_wwan_ctrl_tx_poll` → `rpmsg_poll` → `qcom_smd_poll`.
* **A/B experiment (§2):** stop `qmi-proxy` (keeping fd 7 open) and the corruption and the
  reset **both disappear**. That is the causal link, not a correlation.
* **Fix: patch 822** — drain the channels' pollers in `qcom_smd_unregister_edge()`, between the
  child-device removal (which wakes the pollers) and the `device_unregister()` that frees the
  channels.

---

## §2 The A/B experiment — the decisive result

Both runs: patch 821 deployed, modem awake (`pc_state = 1`, ring armed with 32 slots), one
`echo stop`.

| | **Run A** — `qmi-proxy` running | **Run B** — `qmi-proxy` SIGSTOPped (fd 7 still open) |
|---|---|---|
| Teardown | `T1` 397.977776 → `T9` 398.045939 (**complete**) | `T1` 232.601320 → `T9` 232.662980 (**complete**) |
| `T4`→`T5` | 21.4 ms (normal) | 9.6 ms (normal) |
| `list_del corruption` | **1** — PID 4836 `Comm: qmi-proxy` | **0** |
| AP liveness after | 6 × `NO ANSWER`, then `uptime` resets to 60.65 | `uptime` 221 → 283, monotonic, **no reset** |
| `rproc` state after | (rebooted) | `offline` — stopped and stayed stopped |

**Run B is the control that makes this causal.** `SIGSTOP` (not `SIGKILL`) was used
deliberately: it keeps fd 7 open, so the `struct wwan_port` device reference is still held and
`port->waitqueue` is *provably* not freed (§3.2). The only thing removed is the
`poll_table_entry` in the SMD queue — and the failure vanishes.

Reproduction cost: **~3 s from `echo stop` to reset**, versus ~900 s for one natural fatal.
That is what makes §8's `n` affordable.

---

## §3 The mechanism

### 3.1 The poll chain (empirically confirmed)

`qmi-proxy` (pid 4836/3658) holds exactly one wwan fd — **fd 7 → `/dev/wwan0qmi0`**, whose
backing rpmsg device is `remoteproc0:smd-edge.DATA5_CNTL` (`id_table`: `DATA5_CNTL` →
`WWAN_PORT_QMI`). Polling it walks:

```
wwan_port_fops_poll()                  wwan_core.c:772
  poll_wait(filp, &port->waitqueue, wait)                     <- entry 0
  port->ops->tx_poll(port, filp, wait)
    rpmsg_wwan_ctrl_tx_poll()          rpmsg_wwan_ctrl.c:83
      rpmsg_poll(rpwwan->ept, filp, wait)                     rpmsg_core.c:292
        qcom_smd_poll()                qcom_smd.c:991
          poll_wait(filp, &channel->fblockread_event, wait)   <- entry 1  (THE BUG)
```

`poll_freewait()` removes entry 0 first, then entry 1.

### 3.2 `port->waitqueue` cannot be the freed head — so entry 1 is

* `wwan_port_fops_open()` takes a device reference via `wwan_port_get_by_minor()` →
  `class_find_device()`; only `wwan_port_fops_release()`'s `put_device()` drops it.
* `wwan_port_destroy()` is the `dev.release` callback (`wwan_core.c:354`, registered at
  `:366`), so `struct wwan_port` — and therefore `port->waitqueue` — **cannot be freed while
  fd 7 is open**.
* Run B confirms it empirically: fd 7 open + no poller ⇒ no corruption.

### 3.3 `struct qcom_smd_channel` can be freed — and the ordering is deterministic

The only normal free of a channel is `qcom_smd_edge_release()`
(`qcom_smd.c:1440`, `kfree(channel)` at `:1448`); the `:1195` site is an error path in
`qcom_smd_create_channel()`. That callback is the edge device's `dev.release`, reached from
`device_unregister(&edge->dev)`. Because `rpdev->dev.parent = &edge->dev`
(`qcom_smd_create_device()`, `:1097`), the edge's children **are** the rpmsg devices, so
`qcom_smd_unregister_edge()` (`:1539`) runs, in one thread:

```c
	device_for_each_child(&edge->dev, NULL, qcom_smd_remove_device);  /* :1547 */
	        -> device_unregister(rpmsg dev)
	        -> rpmsg_wwan_ctrl_remove()      (rpmsg_wwan_ctrl.c:142)
	        -> wwan_remove_port()            (wwan_core.c:514)
	        -> wake_up_interruptible(&port->waitqueue)   <-- poller becomes RUNNABLE
	                                                                 (but has not run yet)
	mbox_free_channel(edge->mbox_chan);
	device_unregister(&edge->dev);                                    /* :1552 */
	        -> qcom_smd_edge_release() -> kfree(channel)   <-- SYNCHRONOUS, same thread
```

**The woken poller must be *scheduled* before it can run `poll_freewait()`; the teardown
thread frees the channel immediately, without yielding.** That is why the poller always loses
(§5).

### 3.4 The three blocking steps between `T4` and `T5` — H5 demoted

`T4`→`T5` contains exactly three blocking steps inside `bam_dmux_power_off()`:
`cancel_delayed_work_sync(&dmux->rx_rearm_work)` (`:1591`),
`wait_event(rx_submit_wait, rx_active_submitters == 0)` (`:1595`), and
`dmaengine_terminate_sync(dmux->rx)` (`:1600`).

The H5 candidate is real in principle — `bam_dmux_rx_rearm_work_func()` self-reschedules every
200 ms while `pc_state` is true and calls `dma_async_issue_pending()`, which *does* reach
`pm_runtime_get_sync()` (`bam_start_dma()`, `bam_dma.c:1032`, confirmed in this tree) — but it
is **demoted as the leading explanation** because the *same* `cancel_delayed_work_sync()`
already runs ~327 times per boot from `bam_dmux_pm_quiesce()` (`:1695`) under the same
`state_lock`, without ever hanging. The `echo stop` reproduction also **completed** `T4`→`T5`
normally in both §2 runs (21.4 ms / 9.6 ms), so the block was not even exercised.

`bam_dma_terminate_all()` (`bam_dma.c:725`) is fully bounded — spinlock, register writes,
descriptor frees, no sleeps — so H2 can only hang via a bus fault on an unclocked BAM, which
would stall the CPU. Neither is what §2 shows.

---

## §4 The forensic identification — the clobbered word is the SLUB freelist pointer

Run A's register dump and message:

```
list_del corruption. prev->next should be ffff800081723c88, but was a397a77f11b56287.
                                                       (prev=ffff1dbc83a691e0)
sp : ffff800081723910   x20: ffff1dbc83a691d8   x19: ffff800081723c70
```

`__list_del_entry_valid_or_report(entry)` reports `entry` and `entry->prev`; so
`entry = ffff800081723c88` (on the task's kernel stack) and `prev = ffff1dbc83a691e0`.

Now apply the measured layout:

```
offsetof(struct qcom_smd_channel, fblockread_event) = 88   (pahole, this build)
wait_queue_head_t = { spinlock_t lock; struct list_head head; }
  -> &fblockread_event.head = channel + 88 + 8 = channel + 96
  -> prev = channel + 96        =>  channel = ffff1dbc83a69180
  -> x20  = channel + 88        =   ffff1dbc83a691d8     == the x20 in the dump  ✓
  -> prev = channel + 96        =   ffff1dbc83a691e0     == the reported prev    ✓
```

`0x180 % 192 == 0`, and `sizeof(struct qcom_smd_channel)` = 176 ⇒ the object lives in
**kmalloc-192**. SLUB places the freelist pointer at `ALIGN(size/2, sizeof(void *))` =
`ALIGN(88, 8)` = **96** — exactly the offset of `head.next`.

**So `head.next` was overwritten by the slab allocator's freelist pointer: the channel had
already been freed and returned to the cache.** The same arithmetic holds for the earlier
Doc 168 run B (`prev = ffff6ee6c4a07f60` → `channel = ffff6ee6c4a07f00`, `0xf00 % 192 == 0`).

The same arithmetic **excludes** `port->waitqueue`: `port = prev - 792 = ffff1dbc83a68ec0`,
and `0xec0 % 1024 != 0`, so that address is not a kmalloc-1024 object boundary — while
`struct wwan_port` is 912 bytes and therefore kmalloc-1024.

| candidate | field offset | base = prev − offset | in a valid slab slot? |
|---|---|---|---|
| `qcom_smd_channel.fblockread_event` (176 → kmalloc-192) | 88 | `…9180` (run A), `…7f00` (run B) | **yes**, `% 192 == 0` in both |
| `wwan_port.waitqueue` (912 → kmalloc-1024) | 792 | `…8ec0` (run A), `…7c50` (run B) | **no**, `% 1024 != 0` in both |

---

## §5 Why the failure is deterministic, not intermittent

Doc 167 established that the *AP hang* is a ~3–5 % race. **This defect is not that race.** The
free is performed **synchronously by the teardown thread**, while the poller it just woke
needs a context switch before it can unlink. The teardown therefore wins essentially always:

* run A (this doc): corruption, reset
* run B (Doc 168): corruption, reset
* run B (this doc, control): **no poller ⇒ no corruption, no reset**

Consistent with this, the interval from `T9` to the corruption is small and repeatable:
**57.1 ms** in run A, **12.8 ms** in Doc 168's run B.

---

## §6 What this does and does not explain

**Explains:** every reset observed on the `echo stop` path with `qmi-proxy` running.

**Does not yet explain:** why the previous boot survived **four natural fatals with no hang**
(ledger: `543.77 / 1455.70 / 2449.65 / 3355.26`, four recovered coredumps, §9). Two readings
remain open, and §8 Question B is designed to separate them:

1. On a natural fatal the modem is dead and `pc_state` may already be false, so `qmi-proxy`
   may not be parked in `ppoll()` at that instant — in which case this defect is
   `echo stop`-only and the natural path is unaffected; or
2. The corruption **did** fire on those fatals and the AP survived it (a `WARNING` is not a
   panic), and the reset has a *second*, slower trigger that the 3 s `echo stop` path simply
   reaches first.

Reading 2 is the more worrying one and is not excluded by anything measured so far.

**One instrument gap is closed by this round:** `ssr_ledger.sh` recorded `t0..t4` only, so it
could not tell whether a natural fatal ran the **full blocking** `bam_dmux_power_off()` or took
the idempotent early return at `:1568`. It is now extended to `t5..t9` plus `pc_state` and
`rx_tearing_down` (a `t5` that never appears is the signature of the early return). Note that
`bam_dmux_pm_quiesce()` (`:1662`) deliberately does **not** release the channels ("Terminates
DMA on both channels but does NOT release them"), so `dmux->rx` stays non-NULL and the early
return should *not* trigger — but that is now measurable rather than argued.

---

## §7 The fix — patch 822

`822-rpmsg-smd-drain-pollers-before-free.patch` (2.9 kB, `drivers/rpmsg/qcom_smd.c`). One
behavioural change, placed exactly between the wake and the free:

```c
	ret = device_for_each_child(&edge->dev, NULL, qcom_smd_remove_device);
	if (ret)
		dev_warn(&edge->dev, "can't remove smd device: %d\n", ret);

	qcom_smd_drain_channel_pollers(edge);      /* <-- NEW */
	mbox_free_channel(edge->mbox_chan);
	device_unregister(&edge->dev);
```

`qcom_smd_drain_channel_pollers()` iterates `edge->channels`; for each channel with
`waitqueue_active(&channel->fblockread_event)` it does `wake_up_all()` (guaranteeing the parked
task runs `poll_freewait()` even if nothing else woke it) and then waits for the queue to
drain, bounded at ~5 s with a `dev_warn` if it does not.

**Why this is correct and not a heuristic:**

* It is placed **after** `device_for_each_child()`, which is precisely the call that wakes the
  poller, and **before** the only `kfree(channel)`. The ordering is structural, not timing-based.
* **No poller can re-register during the drain.** `wwan_remove_port()` sets `port->ops = NULL`
  under `ops_lock`, and `wwan_port_fops_poll()` takes that same `ops_lock` before calling
  `tx_poll`. So a poll is either fully registered before the drain starts, or never registered
  at all — there is no window in which one appears afterwards.
* The bound exists only so a pathological poller cannot hang the SSR teardown; reaching it
  emits a `dev_warn` rather than failing silently.

**Known limits, stated honestly:** the fix is a *synchronisation barrier*, not a lifetime
redesign — `qcom_smd` still frees a wait queue that userspace can reach. The same class of bug
exists in `rpmsg_char`'s `eptdev->readq` (`rpmsg_char.c:307`) and is **not** fixed here; it is
not on this device's failure path (no `rpmsg_char` consumer polls it) but it is worth a
separate look. The drain also runs on every edge unregister, but costs nothing when a channel
has no pollers (`waitqueue_active()` is false immediately).

---

## §8 Pre-registration (written before the fix is scored)

**Question A — does the fix remove the corruption and the reset?**
`echo stop` with `qmi-proxy` **running**, **n = 5**. Predict: `list_del corruption` count **0**
in all 5, and no reboot. Success criterion is *both*: zero corruptions *and* zero resets.
Report `n` and the counts; a single clean run proves nothing (the pre-fix rate was 2/2, so 1
clean run would only be 50 %-consistent with "fixed").

**Question B — is the natural-fatal path affected at all?**
Let the device run undisturbed to a natural fatal (~900 s) with the extended ledger deployed,
and read `t5..t9`. Predict: the full teardown (`t5`…`t9` present). If `t5` is absent, the
idempotent early return at `:1568` is being taken and §6 reading 1 is correct.

**Question C — is the corruption actually what resets the AP, or a bystander?**
The causal claim in §2 rests on one control. If patch 822 removes the corruption *and* the
reset, the claim is settled. If the corruption disappears but the reset remains, the reset has
a second cause and §6 reading 2 is correct — this is the outcome that would most change the
plan.

**Explicitly out of scope for this round:** re-testing 821's own claim (Doc 168 §2.3 stands),
and the `T4`→`T5` block, which neither run in §2 exercised.

---

## §9 Instrument traps found this round

1. **A stale pstore record will masquerade as a result.** `console-ramoops-0` is only rewritten
   on a crash, so after a **non-crashing** run it still contains the *previous* crash verbatim.
   The run-B (control) check initially "found" 1 `list_del corruption` — from run A's record,
   same `prev=ffff1dbc83a691e0`, same PID 4836, same uptime 398. **Always cross-check the
   uptime and PID in the record against the run you think you are reading**, and read the live
   `dmesg` for a run that did not crash.
2. **`console-ramoops` and `dmesg` have different filters.** With `console_loglevel=6`,
   `dev_info` never reaches the console, so `stopped remote processor` and
   `port wwan0qmi0 disconnected` appear in `dmesg` but are **absent** from `console-ramoops`.
   Their absence is not evidence that the teardown stopped (Doc 166 §5.5's rule, again).
3. **`rx_tearing_down = 1` is not a stall.** It is set by `bam_dmux_power_off()` and cleared by
   `bam_dmux_power_on()`; with `pc_state = 0` it is the *normal* "modem power-collapsed, ring
   released" state. Read it only together with `pc_state`. It nearly produced a false positive
   at the start of this round.
4. **`rx_telemetry` is safe to sample during the teardown.** `rx_telemetry_show()`
   (`qcom_bam_dmux.c:2119`) takes only `rx_lock`, never `state_lock` — so a sampler that blocks
   cannot be confused with a `state_lock` deadlock. Verified in source before use.
5. **A sampler must sync, or a reset eats its tail.** The first sampler synced every 100
   samples; the reboot discarded the unsynced window. Now every 10.

---

## §10 What's next

1. Build 822 (in progress at the time of writing) and flash it.
2. Score §8 Question A: `echo stop` × 5 with `qmi-proxy` running.
3. Deploy the extended ledger and score §8 Question B on the next natural fatal.
4. Re-examine `rpmsg_char`'s `eptdev->readq` for the same lifetime bug (out of scope here).
5. Still open from before: the `T4`→`T5` block (H3/H2/H5, §3.4), the failed modem restart
   (Doc 154 §6), and the `B` cross-boot trace-count excess.

---

## Evidence

| file | what it is |
|---|---|
| `evidence/169_smd_poll_use_after_free/A_run1_qmi_running_CONSOLE_ramoops.txt` | run A, `console-ramoops-0` verbatim: `T1`→`T9`, the corruption, the register dump, the `poll_freewait` trace |
| `evidence/169_smd_poll_use_after_free/A_run1_qmi_running_PMSG_ramoops.txt` | run A, `pmsg-ramoops-0` (userspace-fed sink; stops at `stopped remote processor`) |
| `evidence/169_smd_poll_use_after_free/B_run2_noqmi_LIVE_dmesg.txt` | run B (control) live `dmesg`: full teardown, `port … disconnected`, `stopped remote processor`, **0** corruptions |
| `msm89xx/patches/822-rpmsg-smd-drain-pollers-before-free.patch` | the fix |
| `scratch/hp3.sh` | the ms-period sampler (builtin-only, writes `U` before touching sysfs) |
| `scratch/run_hp3.sh`, `scratch/run_hp3b.sh` | the run drivers (trigger + liveness + result extraction) |
