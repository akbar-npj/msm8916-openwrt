# Doc 168 — The AP freeze is the *asynchronous* `ssr_teardown_work`; and `echo stop` is now a reliable forcing function

**Date:** 2026-09-21
**Device:** HMU05 4G dongle (MSM8916 + WTR1605 + QFE2320), OpenWrt 25.12.5 / Linux 6.12.94
**Baseband:** stock HMU05 (verified clean; `modem.mdt` unchanged — Doc 153)
**Patches under test:** 820 (`qcom_bam_dmux.ko` md5 `e441491317f09e34a3e72dc15cdacd3c`) and
821 (`qcom_bam_dmux.ko` md5 `364376867224df54d47b9b7a06528db3`, 241232 B, srcversion
`2B42063A8FDB7C7BA1C6294`)
**Patch written this round:** 821 (flush the teardown work in the SSR notifier)

**Status: 821 IS SCORED (see §1, §2.3, §8 Question A).** It **does** what it was written to
do — the flush returns, the teardown completes and the modem powers down — but the AP then
dies from a **different, newly-exposed defect** (a wait-queue list corruption in a userspace
`poll()`). And the T4→T5 block it was meant to remove is **intermittent**, not gone.

---

## §0 SOP compliance statement

**Dual-Firmware Comparative SOP — steps taken:**

* **Ground truth read before any patch was written.** The post-T4 region of
  `bam_dmux_ssr_teardown_work_func()` was read in full from the *live* kernel tree
  (`.../linux-6.12.94/drivers/net/wwan/qcom_bam_dmux.c:2301-2330`), together with every
  callee that can block: `bam_dmux_ssr_teardown()` (`:1914`), `bam_dmux_power_off()`
  (`:1561`), `bam_dma_terminate_all()` (`drivers/dma/qcom/bam_dma.c`), and the remoteproc
  stop path (`rproc_stop()` in `drivers/remoteproc/remoteproc_core.c`, `ssr_notify_stop()`
  in `drivers/remoteproc/qcom_common.c:457`).
* **A lock/wait census was taken, not guessed.** Every `state_lock` acquisition and every
  unbounded wait in the file was enumerated with `grep -n` before reasoning about ordering.
* **The mechanism was checked against the kernel actually being run** (the lesson of Doc
  166 §5.5): the callee chain `flush_work` → `system_wq` and `srcu_notifier_call_chain`
  context were read, not assumed.
* **No blind patching.** Patch 821 changes **one** behaviour (the teardown is run to
  completion inside the notifier). It does not touch the cancels that 820 introduced, so
  the two changes can be scored independently.
* **Pre-registration (§8) is written before the build, not after.**
* **Falsification is reported as such.** Doc 167 §7.1's prediction that 820 would make
  `echo stop` *safe* is **falsified** — see §4. And §2.2's own H1 ranking, written earlier in
  this same document, is **retracted** in §5/§7 once 821 was scored — see §2.3.
* **The instrument was measured before it was trusted** (Doc 166 §9.1's rule). §3 quantifies
  the `dev_err` console cost; §5 records that run B's 1.9 s sampler was *too slow* to observe a
  ~2 s failure, and that its silence is therefore not evidence of a stall.
* **A corpus that structurally cannot contain a line is not evidence the line's cause is new**
  (§7 item 9). The `list_del corruption` appears nowhere in the corpus because every earlier
  boot died before the teardown completed; that is a limit of the corpus, not a fact about the
  bug.

**Steps not taken, and why:** the `echo stop` test was run before the first healthy
post-820 fatal was scored (Doc 167 §10 advised scoring the fatal first). This is a
deliberate deviation: the two post-820 boots had already produced two hangs, so the
"healthy post-820 SSR" the sequence was waiting for did not exist to wait for. The
deviation is recorded here rather than hidden.

---

## §1 Headline

**Patch 820 got the teardown work to run and to acquire `state_lock`; patch 821 got it to
*finish*.** But the freeze did not go away — it moved again, to a different defect, and the
block 821 was meant to remove turned out to be **intermittent**.

Pre-820, the SSR teardown work died **before its first print** (Doc 166: the last line
ever was the notifier's `SSR before shutdown: scheduling teardown work`). With 820
deployed, the work **runs**, gets past both `cancel_*` calls, and acquires `state_lock`
(T0 → T1 → T2 → T3 → **T4**).

What 821 then showed, in two `echo stop` runs on the same module:

| | run A (earlier) | run B (§2.3) |
| :--- | :--- | :--- |
| T4 | printed | printed |
| **T5 `rx released`** | **never** | **printed** |
| T6 / T7 / T8 | never | printed |
| **T9 `flush returned`** | never | **printed** |
| `q6v5-trace 01..09` (power-down) | **absent** | **printed** |
| `stopped remote processor` | absent | **printed** |
| outcome | AP hung → reset | SSR **completed**, then a wait-queue corruption → reset |

**So 821 works, and the T4→T5 block is a race, not a deterministic bug** — the same code
path completed in run B and blocked in run A.

**§2.2's H1 is falsified.** H1 said the block was the teardown work losing an ordering race
to `q6v5_stop()` gating the modem. Run A blocks **with the modem still powered** (no
`q6v5-trace` at all), so the power-down cannot be what stops it; and run B, where the
ordering was correct, still kills the AP — from a *different* defect. The timing table in
§2.2 was a real correlation over n = 3, but it was a correlation between the *power-down
timing* and the *outcome*, not the mechanism.

The freeze is still **reproducible on demand**, which is what makes all of this cheap:

```
echo stop > /sys/class/remoteproc/remoteproc0/state     # ~2 s to a reset, every time
```

**Every `echo stop` on 820 and 821 reset the device** — 2/2 on 820 (§4) and 2/2 on 821.
That collapses the Doc 167 §7 "n ≥ 60 SSRs, ~18 h" iteration loop to **about two seconds
per test**.

---

## §2 The two post-820 boots

Both boots ran the same firmware and the same module (`e4414913…`). Both hung.

| | boot 820-#1 | boot 820-#2 |
| :--- | :--- | :--- |
| fatal | AP **276.111919**, `a2_power.c:1189` | AP **951.663368**, `a2_power.c:1189` |
| `crash detected` → `recovering` | 276.111983 → 276.127055 | 951.663564 → 951.678361 |
| T0 scheduled | 276.133805 (+21.9 ms) | 951.685278 (+21.9 ms) |
| T1 entry | 276.140110 | 951.691510 |
| T2 tx_retry cancelled | 276.148171 | 951.699261 |
| T3 tx_wakeup cancelled | 276.156851 | 951.707643 |
| **q6v5 trace 01..09** (power-down) | **276.156528-276.156671** (between T2 and T3) | **ABSENT** |
| **T4 state_lock acquired** | **276.164748** | **951.719173** |
| next console line | **none** | **none** |
| coredump created | **no** | **no** |
| outcome | silent reset | silent reset |

Both boots' pstore console records end on T4 and nothing else. `console-size = 0x40000`
(256 KiB, `805-arm64-dts-qcom-add-msm8916-generic-hmu05.patch`) and the records are only
~24 KiB starting at `[0.000000]`, so **the console region did not wrap — T4 really is the
last line the kernel emitted.**

**No coredump ⇒ `rproc_stop()` never returned.** (`rproc_coredump()` runs only after
`rproc_stop()` returns — Doc 165 §4.1.) And because the console records carry **no**
`stopped remote processor 4080000.remoteproc`, which is `dev_info` and therefore suppressed
at `console_loglevel = 6`, that line is not evidence either way.

### §2.1 What the two boots disagree about — and why that matters

In boot #1 the power-down (`q6v5-trace 01..09`) had **already completed** 8 ms before T4.
In boot #2 the power-down had **not started** when T4 printed. Both hung.

**So the freeze does not require the modem to be powered down first.** Any theory that
depends on "the BAM was already unpowered" is *insufficient* to explain boot #2 and the
`echo stop` runs — it may still be the mechanism in boot #1, but it cannot be the whole
story. This is recorded here because it is the single most important constraint on the
mechanism, and it was discovered only by comparing the two boots.

---

### §2.2 The THIRD post-820 SSR — the clean one, and the measurement that explains everything

A third boot on the same 820 module **survived** a natural fatal at AP **943.555967**
(`a2_power.c:1189`) and recovered completely — coredump created, modem back up, `rproc` state
`running`, `ssr_ledger.csv` line `951.90,1,a2_power.c:1189,1,1,1,1,1,1,1,4`. So 820 is
**1 recovery / 2 hangs**, not a deterministic failure.

Its timeline also contains the `q6v5-trace 01..09` power-down, which the two hanging boots do
not. That gives the measurement this round was missing:

| case | T4 − fatal | trace 01 − fatal | trace 09 − fatal | T4 vs the power-down | outcome |
| :--- | ---: | ---: | ---: | :--- | :--- |
| **clean (820)** | **+43.3 ms** | +62.1 ms | +62.2 ms | **T4 18.9 ms BEFORE** | **RECOVERED** |
| **hang #1 (820)** | **+52.8 ms** | +44.6 ms | +44.8 ms | **T4 8.1 ms AFTER** | **AP FROZE** |
| **hang #2 (820)** | **+55.8 ms** | — | — | freeze preceded the trace | **AP FROZE** |
| *pre-820 boot C* | *≈ +3.3 ms* (the `executing…` print) | — | *≈ +45 ms* | *far before* | *RECOVERED* |

**The correlation is the mechanism: the teardown work must finish its DMA teardown *before*
`q6v5_stop()` gates the modem.** When it does (clean, +43.3 ms with the power-down at +62.1 ms),
the SSR completes. When the power-down overtakes it (hang #1, +44.8 vs +52.8), the AP freezes.
Hang #2 is the same thing seen from the other side: the freeze began at ~+56 ms, *before*
`q6v5_stop` could reach its trace at ~+62 ms, so the trace is missing rather than reordered —
which is exactly why "no `q6v5-trace` at all" (§2.1) looked like counter-evidence and is not.

**And it explains the pre-820/post-820 difference exactly.** Pre-820 the work reached its first
print at **+3.3 ms** — an order of magnitude before the power-down — so this race could never
fire, which is why boot C's two fatals both recovered. 820's five `dev_err` probes add ≈ 40 ms
(§3) and push the work to **+43…+56 ms**, i.e. *into* the window where a ~+45…+62 ms power-down
can overtake it. **So the probes did not create a bug; they widened an existing race from
effectively 0 % to roughly 2 in 3.**

**This re-ranks the mechanism list in §5: H1 (the ordering race) explains all three post-820
SSRs and the pre-820 control, and is now the leading explanation. H2 is no longer needed.**
n = 3, so it is a strong correlation rather than a proof — but it is a *quantitative*
discriminator that patch 821 tests directly, and it predicts the exact observable
(`T9 flush returned` before `q6v5-trace 01`).

**Two corrections to §7 that this boot forces:**

* **`dev_info` IS in `dmesg`** — this boot's `dmesg` contains `wwan0at0 disconnected`,
  `executing serialized asynchronous SSR teardown` and `stopped remote processor 4080000.remoteproc`,
  all of which are `dev_info`. The filtering is on the **console/`console-ramoops` sink**, not on
  the ring buffer. So the correct statement is: *`wwan0at0 disconnected` is invisible in
  `console-ramoops`*, not "invisible". It is still useless for a hang, because a hang ends in a
  reboot and the ring buffer does not survive it — only pstore does, and pstore's console sink is
  the filtered one.
* **`T1` can print before `T0`.** In this boot `T1 entry` is at 943.578229 and `T0 scheduled` at
  943.581580 — the work ran before the notifier finished writing its own probe. Doc 166 §9's
  decode table treats "T0 without T1" as diagnostic of workqueue starvation; that direction is
  still valid, but the *reverse* (T1 before T0) is normal and must not be read as corruption.

---

### §2.3 Patch 821 scored: the teardown COMPLETES, then a *different* defect kills the AP

Deployed module = 821 (`qcom_bam_dmux.ko` md5 `364376867224df54d47b9b7a06528db3`). Trigger =
one `echo stop`. Evidence:
`evidence/168_teardown_races_powerdown/821_echostop2_teardown_completed_then_wq_corruption.txt`.

The whole SSR, from `console-ramoops-0`:

```
[  309.301329] SSR teardown T1 entry
[  309.301391] SSR teardown T2 tx_retry cancelled
[  309.307960] SSR teardown T0 scheduled
[  309.316443] SSR teardown T3 tx_wakeup cancelled
[  309.324357] SSR teardown T4 state_lock acquired
[  309.333033] bam-dmux T5 rx released
[  309.344838] bam-dmux T6 tx released
[  309.349416] bam-dmux T7 power_off returned
[  309.363183] bam-dmux T8 work done
[  309.363296] bam-dmux T9 flush returned
[  309.375474] q6v5-trace: 01 disable_qchannel mdm      <-- the power-down
        ... 02..09 ...
[  309.375823] remoteproc remoteproc0: stopped remote processor 4080000.remoteproc
[  309.376143] ------------[ cut here ]------------
[  309.376211] list_del corruption. prev->next should be ffff800083533c88, but was c40df08e5a393833. (prev=ffff6ee6c4a07f60)
[  309.380847] WARNING: CPU: 3 PID: 4833 at lib/list_debug.c:62 __list_del_entry_valid_or_report+0xd0/0xfc
[  309.446747] CPU: 3 UID: 0 PID: 4833 Comm: qmi-proxy
        Call trace: __list_del_entry_valid_or_report -> remove_wait_queue
                    -> poll_freewait -> do_sys_poll -> __arm64_sys_ppoll
[  310.381983] bam-dmux: bam_dmux: modem pc-ack timeout during resume
```

**Three separate results, and they must not be conflated:**

1. **821 works.** `T9 flush returned` at 309.363296 proves the `flush_work()` in the notifier
   returned, i.e. the teardown work reached the end of `bam_dmux_ssr_teardown_work_func()`.
   `rproc_stop()` then proceeded to `q6v5_stop()` (trace 01..09 at 309.375474) and the modem
   went down (`stopped remote processor`, 309.375823). **This is the first time the SSR
   teardown has ever been observed to complete.** Question A's *structural* test therefore
   **passes**, and the predicted observable (`T9` before `q6v5-trace 01`) is exactly what
   happened.
2. **The T4→T5 block is intermittent, not deterministic.** Run A's record ends on T4 with no
   T5 and no trace at all; run B's T4→T5 gap is **8.7 ms** — an ordinary `dev_err` cost, no
   block. Same module, same trigger, opposite result. **So `bam_dmux_power_off()` is not
   deterministically broken; something in it races.**
3. **A different defect now kills the AP, 0.14 ms after the teardown succeeds.** A
   wait-queue list corruption in `qmi-proxy`'s `poll()` path
   (`poll_freewait` → `remove_wait_queue`). It is a `WARNING`, not a panic, and the device
   resets ~1 s later (after `modem pc-ack timeout during resume`).

**Reading the corruption.** `prev->next should be ffff800083533c88` — and `ffff800083533c88`
lies in the same page as `sp = ffff800083533910`, so the entry being removed is a
**`poll_table_entry` on qmi-proxy's own kernel stack**. `prev` (`ffff6ee6c4a07f60`, with
`x20 = ffff6ee6c4a07f58` just below it) is the wait-queue **head** — the head's `next`
pointer had been overwritten with garbage by the time `poll_freewait()` ran.

The candidate heads, from a `poll_wait()` census of the running tree, are only three:

| site | wait queue | owner |
| :--- | :--- | :--- |
| `drivers/net/wwan/wwan_core.c:776` | `port->waitqueue` | `wwan_port_fops_poll` |
| `drivers/rpmsg/rpmsg_char.c:307` | `eptdev->readq` | `rpmsg_eptdev_poll` |
| `drivers/rpmsg/qcom_smd.c:998` | `channel->fblockread_event` | `qcom_smd_poll` |

`Modules linked in` lists `rpmsg_wwan_ctrl` and `wwan` (and **not** `cdc_wdm`), so the most
likely target is `port->waitqueue` — the QMI port `/dev/wwan0qmi0` that `rpmsg_wwan_ctrl`
creates. `wwan_remove_port()` (`wwan_core.c:511`) **wakes that queue and then unregisters the
port** in the same breath:

```c
	port->ops = NULL;
	mutex_unlock(&port->ops_lock);
	wake_up_interruptible(&port->waitqueue);      /* :524  wake the poller ... */
	skb_queue_purge(&port->rxq);
	dev_info(..., "port %s disconnected\n", ...); /* :529 */
	device_unregister(&port->dev);                /* :530  ... then tear it down */
```

`rpmsg_wwan_ctrl_remove()` (`:145`) calls exactly that during the SSR. **This is a
hypothesis, not a measurement** — it needs the poller's identity confirmed on the device
(`/proc/<qmi-proxy pid>/fd`) before it is claimed. What *is* measured is that the corruption
is detected **0.14 ms after `stopped remote processor`**, i.e. inside the port-teardown
window, and that no boot in the entire evidence corpus has ever shown this line before —
because every earlier boot died *before* the teardown could complete.

**Consequence for the plan.** 821 is a **net win** (it fixes the block it targeted) but it is
**not deployable as a fix**: it converts a ~50 % hang into a ~100 % reset. The next work is
the wait-queue lifetime, not more work on `bam_dmux_power_off()`.

---

### §2.4 The corruption's source-level mechanism — a userspace `poll()` left registered on a freed SMD channel

**This section is a source reading, not a measurement.** It is written down because it
predicts two cheap experiments that would settle it, and because it narrows three candidate
wait-queue heads to one.

`wwan_port_fops_poll()` (`drivers/net/wwan/wwan_core.c:772`) registers the caller's
`poll_table_entry` in **two** wait queues, not one:

```c
static __poll_t wwan_port_fops_poll(struct file *filp, poll_table *wait)
{
	struct wwan_port *port = filp->private_data;
	__poll_t mask = 0;

	poll_wait(filp, &port->waitqueue, wait);          /* head #1 */
	mutex_lock(&port->ops_lock);
	if (port->ops && port->ops->tx_poll)
		mask |= port->ops->tx_poll(port, filp, wait);  /* -> head #2 */
	...
```

For the QMI port, `port->ops->tx_poll` is `rpmsg_wwan_ctrl_tx_poll()` and it chains straight
through to the SMD channel (`drivers/net/wwan/rpmsg_wwan_ctrl.c:83`):

```
wwan_port_fops_poll
  -> poll_wait(&port->waitqueue)                          # head #1  (wwan_core.c:776)
  -> rpmsg_wwan_ctrl_tx_poll -> rpmsg_poll(ept)
       -> ept->ops->poll = qcom_smd_poll
            -> poll_wait(&channel->fblockread_event)      # head #2  (qcom_smd.c:998)
```

`CONFIG_RPMSG_QCOM_SMD=y` and **both** GLINK options are `#ifdef`-off in the running config, so
the rpmsg backend here is SMD and this chain is the only one that can run.

**The two heads have different lifetimes, and that is the whole point:**

| head | owner | freed when |
| :--- | :--- | :--- |
| `port->waitqueue` | `struct wwan_port` (kzalloc) | `wwan_port_destroy()` → `kfree(port)` (`wwan_core.c:361`) |
| `channel->fblockread_event` | `struct qcom_smd_channel` | **`qcom_smd_edge_release()` → `kfree(channel)` (`qcom_smd.c:1448`)** |

**`port->waitqueue` cannot be the freed one.** `wwan_port_fops_open()` (`:663`) calls
`wwan_port_get_by_minor()` → `class_find_device()` (`:380`), which takes a device reference,
and `wwan_port_fops_release()` (`:683`) drops it with `put_device()`. So while qmi-proxy holds
the fd open the port — and its wait queue — stay allocated.

**`channel->fblockread_event` can be, and it is freed exactly when the corruption fires.** The
SMD channel is allocated by `qcom_smd_create_channel()` (`:1130`) and freed only in
`qcom_smd_edge_release()` (`:1440-1449`), which the source's own locking note says runs
*after* the state worker is killed — i.e. during the SMD edge teardown, which is part of the
SSR. And the corruption is detected **0.14 ms after `stopped remote processor`**, inside that
window.

**The use-after-free, stated plainly.** `poll()` registers `entry->wait` on
`&channel->fblockread_event` and then **sleeps** (`do_sys_poll` → `poll_schedule_timeout`) with
the entry still linked; it is unlinked only by `poll_freewait()` at the end of the syscall. So
the registration window is the *entire blocking poll*, not an instant. If the SSR frees the
channel inside that window, `remove_wait_queue()` walks freed memory — and because the freed
`wait_queue_head`'s `next` is overwritten by whatever reuses the slab, the report is exactly
`prev->next should be <the stack entry>, but was <garbage>` with `prev` = the head. That is
the observed message, and `prev = ffff6ee6c4a07f60` is a linear-map heap address, consistent
with a kzalloc'd `struct qcom_smd_channel`.

**Two cheap experiments that would settle it, neither needing a build:**

1. **Identify the poller.** `ls -l /proc/$(pidof qmi-proxy)/fd` says whether qmi-proxy holds
   `/dev/wwan0qmi0` (→ the SMD chain above) or `/dev/rpmsg*` (→ `rpmsg_eptdev_poll` →
   `eptdev->readq`, the *other* heap head with the same free-on-SSR lifetime, and the file
   patch 819 already had to guard). `scratch/check_poller.sh` does this.
2. **Remove the poller and re-run `echo stop`.** If the corruption disappears when qmi-proxy
   is stopped, it is a poller-lifetime bug and head #2 (or `eptdev->readq`) is confirmed; if it
   persists, the hypothesis is wrong and the corruption is something else entirely.

**A candidate minimal fix, if experiment 1 lands on the SMD chain:** drop `tx_poll` from
`rpmsg_wwan_pops` (`rpmsg_wwan_ctrl.c:96`). `wwan_port_fops_poll` falls back to
`!is_write_blocked(port)` for `EPOLLOUT` when `tx_poll` is absent, so the only loss is
`qcom_smd_get_tx_avail(channel) > 20` as an extra `EPOLLOUT` condition — a small functional
regression in exchange for removing a use-after-free. **Do not write this patch until
experiment 1 confirms the device**, because it fixes a different driver than the one the
evidence currently points at.

---

## §3 The measurement that explains the difference from boot C

Pre-820 **boot C survived two consecutive fatals** (Doc 167 §1). The 820 module differs in
two ways: (a) the cancels are non-blocking, and (b) five `dev_err` probes were added.

The inter-probe gaps isolate (b):

| gap | boot 820-#1 | boot 820-#2 |
| :--- | :--- | :--- |
| T0 → T1 | 6.305 ms | 6.232 ms |
| T1 → T2 | **8.061 ms** | **7.751 ms** |
| T2 → T3 | **8.680 ms** | **8.382 ms** |
| T3 → T4 | **7.897 ms** | **11.530 ms** |

The work between those probes is `cancel_delayed_work()`, `cancel_work()` and
`mutex_lock()` — all sub-microsecond. The gaps are therefore almost entirely the
**synchronous serial console write of the preceding `dev_err`**, measured elsewhere at
**1.03 ms + 0.0904 ms/char** (a 45-char line ≈ **5.1 ms**, and the observed gaps run
higher because the console is contended). **Patch 820's own probes added ≈ 40 ms to the
teardown path.**

That is exactly the magnitude that moves T4 across the power-down in boot #1:
`q6v5-trace 09` at **+44.75 ms**, T4 at **+52.83 ms**.

**This is a self-inflicted confound and must be stated plainly: the probes are a
plausible cause of the *first* boot's hang, and Doc 167 §10 already required "re-run 820
without the `dev_err` probes" for precisely this reason.** Boot #2 and the `echo stop`
runs are the counter-evidence that the confound is not the whole story — in neither did
the power-down precede T4.

---

## §4 `echo stop` is now a reliable forcing function — and Doc 167 §7.1's prediction is falsified

Doc 167 §7.1 predicted that, because the `echo stop` hang was the teardown work blocking
in the cancels, **820 might make `echo stop` safe**, giving an SSR forcing function.

**Measured, 2/2:**

| attempt | AP time | result | last console line |
| :--- | :--- | :--- | :--- |
| 1 | 137 s | `echo stop` returned **0**, then the AP froze and reset | T4 @ 138.112650 |
| 2 | 165 s | `echo stop` returned **0**, then the AP froze and reset | T4 @ 165.997713 |

**The prediction is half right and half wrong, and the split is the useful part:**

* **Right:** 820 *does* get the teardown work past the cancels. T2 and T3 print, which
  they never did in a pre-820 hang. The `echo stop` hang is therefore **no longer the
  cancel deadlock**.
* **Wrong:** `echo stop` still freezes the AP. It is **not** a safe operation, and the
  forcing function it promised does not exist in the form Doc 167 §7.1 imagined.
* **But it is still a forcing function — a better one.** It reproduces the freeze
  **deterministically and in ~2 s**, versus ~15 min for a natural fatal. That is what
  makes the next experiment cheap.

Note the asymmetry with §2: in **both** 820 `echo stop` runs the power-down
(`q6v5-trace 01..09`) **never printed**, yet the AP still froze. See §2.1.

> **Corrected by §2.3.** That asymmetry was read here as "the `echo stop` path differs from the
> fatal path". It does not. Run B on 821 shows the `echo stop` path **does** reach
> `q6v5-trace 01..09` when the teardown work completes — so the missing trace in the 820 runs
> was a **consequence of the block**, not a property of the `echo stop` path. §7 item 8.

### §4.1 The `echo stop` return code is not a success signal

`echo stop` returned **0** in both runs, and the AP froze anyway. So:

* **`RC=0` from `echo stop` does NOT mean the stop succeeded.** `rproc_stop()` schedules
  the teardown work and does **not** wait for it, so the sysfs write can return while the
  teardown is still running and about to freeze the machine.
* The last console line, not the return code, is the signal.
* A `write()` to `/sys/class/remoteproc/remoteproc0/state` completing is therefore
  **never** evidence that the SSR teardown completed. **Do not use it as a health check.**

---

## §5 The mechanism — what is proven and what is still a hypothesis

**Proven:**

1. The teardown work runs to `state_lock` (T4) and *sometimes* the AP then stops printing.
   **Corrected by §2.3: this is intermittent.** In run B the work ran all the way through
   (T5→T9) and the modem powered down. The block is a race, not a fixed stop.
2. No coredump is created ⇒ `rproc_stop()` did not return.
3. `rproc_stop()` order is fixed: `rproc_stop_subdevices()` (fires the notifier, T0) →
   `rproc_reset_rsc_table_on_stop()` → `rproc->ops->stop()` (`q6v5_stop`, trace 01..09) →
   `rproc_unprepare_subdevices()` → return. Read from source.
4. **The teardown work is asynchronous with respect to all of that.** The notifier only
   `schedule_work()`s it (`qcom_bam_dmux.c:2434`); nothing in `rproc_stop()` waits for it.
   `bam_dmux_ssr_powerup_work_func()` does `flush_work(&dmux->ssr_teardown_work)` (`:2340`)
   — but that runs on `QCOM_SSR_AFTER_POWERUP`, i.e. **after** the whole recovery, far too
   late.
5. The post-T4 region holds `state_lock` across six blocking operations:
   `cancel_delayed_work_sync(rx_rearm_work)` (`:1591`),
   `wait_event(rx_submit_wait)` (`:1595`), `dmaengine_terminate_sync(rx)` (`:1600`),
   `wait_event(rx_callback_wait)` (`:1608`), `dmaengine_terminate_sync(tx)` (`:1613`),
   `cancel_delayed_work_sync(tx_retry_work)` (`:1636`).
6. **A redundant synchronous cancel survives inside the post-T4 region:** `:1636` calls
   `cancel_delayed_work_sync(&dmux->tx_retry_work)` even though the work function already
   called the non-blocking `cancel_delayed_work()` on the same work item at `:2296` (T2).
   `bam_dmux_tx_retry_work()` (`:845`) is a leaf — it takes no `state_lock` and only calls
   `queue_work()` — so this one is **not** a lock cycle, but it is dead code that 820 left
   behind, and it is listed so it is not mistaken for a suspect.

**Hypotheses, re-ranked after 821 was scored (§2.3):**

* **H1 — ordering race against the modem power-down. FALSIFIED.** H1 said the teardown work
  loses a race to `q6v5_stop()` gating the modem's power domain, so a BAM register access
  stalls. Run A blocks **with the modem still powered** — no `q6v5-trace` at all — so the
  power-down cannot be what stops it. And run B had the ordering 821 was built to guarantee
  (`T9` before `q6v5-trace 01`) and the AP still died, from an unrelated corruption. §2.2's
  timing table was a genuine n = 3 correlation between power-down timing and outcome, but a
  correlation with the *outcome* is not the mechanism. **Retired.**
* **H3 — a wait inside the `state_lock` section never completes. NOW THE LEADING
  EXPLANATION.** The post-T4 region holds `state_lock` across three blocking operations
  before T5 (`cancel_delayed_work_sync(rx_rearm_work)` `:1591`, `wait_event(rx_submit_wait)`
  `:1595`, `dmaengine_terminate_sync(rx)` `:1600`). §2.3 shows the block is **intermittent**
  and happens **with the modem powered**, which is exactly what a wait that depends on
  another thread's progress looks like — and not what a power-domain access stall looks like.
  The discriminator that settles it is already available without a build: `rx_telemetry`
  exposes `rx_active_submitters` / `rx_active_callbacks`, so if the block is the `wait_event`
  those counters are non-zero for the whole hang. **Not yet read during a hang — the next
  experiment (§9).**
* **H2 — the BAM's *own* runtime PM. RE-OPENED as a residual.** `bam_dma_terminate_all()`
  (`drivers/dma/qcom/bam_dma.c`) takes **no** `pm_runtime_get_sync()`, unlike every other
  hardware-touching BAM entry point (`:576`, `:779`, `:805`, `:914`, `:1032`). This was
  "retired" in §2.2 on the strength of H1; with H1 falsified the reason is gone. It predicts
  exactly what run A shows — a stall with the modem powered, on the `dmaengine_terminate_sync`
  line — so it is now a live second candidate beside H3.
* **H4 — NEW: the wait-queue corruption (§2.3) is its own defect, independent of the block.**
  It is the only failure that is *certainly* reproducible (run B), it has a named site, and it
  may well be a long-standing latent bug in the rpmsg/wwan port lifetime that every earlier
  boot was hiding behind the hang. It needs the poller identity confirmed before it is claimed.


**The freeze signature is consistent with a stalled CPU, not a wedged console:** in the
820 `echo stop` runs the shell still received `RC=0` over **SSH (USB, `usb0`)** while the
**serial console** had already stopped at T4. A bus stall on the CPU performing the
console write explains both; a printk/console-path bug does not (and Doc 166 §5.5 already
retracted that family of explanation).

**A caution added after run B, so this is not over-read.** In run B a sampler that was
reading `/proc/uptime` and `/proc/<pid>/stack` in a loop simply **did not complete its next
sample** before the reset — which looked like a stall but is also what a 2 s reset looks like
against a 1.9 s sample period. The 12 liveness probes that reported `NO ANSWER` started ~6 s
*after* the device had already begun rebooting. **Neither observation is evidence of a
stalled CPU**, and the honest statement is: *run A is consistent with a stall; run B is
consistent with a plain reset after the corruption.* A liveness probe is only evidence if it
is known to have been running *during* the window — that is what §9's experiment fixes.

---

## §6 The fix — patch 821

`821-bam-dmux-ssr-teardown-flush-before-powerdown.patch` (67 lines, md5
`29307724173b492af888087e2fb69fba`), applied to both patch trees.

**One behavioural change:** in `bam_dmux_ssr_notifier_cb()`'s `QCOM_SSR_BEFORE_SHUTDOWN`
case, after `schedule_work(&dmux->ssr_teardown_work)` and T0:

```c
flush_work(&dmux->ssr_teardown_work);
dev_err(dmux->dev, "bam-dmux T9 flush returned\n");
```

**Why this is the right place.** §2.2 measures the failure as *the teardown work not finishing
before `q6v5_stop()` gates the modem*, so the remedy is to make that ordering a guarantee rather
than a race. `rproc_stop_subdevices()` runs the notifier chain in the **recovery thread's process
context**, so the notifier may sleep. Flushing there makes the ordering deterministic: the BAM is
terminated and released **before** `rproc->ops->stop()` powers the modem down. It also removes the
race entirely rather than narrowing it — which is what a fix has to do, because "the work usually
wins" is exactly the ~3-5 % failure Doc 167 measured, and it is also why **removing the probes
alone would not be a fix** (§7 item 5): that only narrows the window back, it does not close it.

**It is deliberately paired with four localisation probes** (`T5` after the RX DMA release,
`T6` after the TX DMA release, `T7` after `bam_dmux_power_off()` returns, `T8` at the end of
the work). Rationale: if 821 fixes the freeze, T9 proves the flush returned and T5–T8 prove
the work completed. If it does **not** fix it, the last T-line localises the stall inside
`bam_dmux_power_off()` for free, without a second build. With the flush in place the probes'
~40 ms is off the critical race path, so keeping them costs nothing that matters.

**Risk, stated:** if the teardown work blocks for any reason, the notifier now blocks with
it, so `rproc_stop()` blocks — and the `echo stop` write would then **hang instead of
returning 0**. That is a distinguishable failure mode (§4.1), and it is the correct
trade: a deterministic block is debuggable, a race is not.

---

## §7 What this round corrects

1. **Doc 167 §7.1's prediction is falsified** (§4). `echo stop` is not made safe by 820.
2. **Doc 167 §7's "the two hangs have different suspects" survives, but the discriminator
   changes again.** Doc 167 used *which* line is missing. This round shows that
   `wwan0at0 disconnected` is `dev_info` (`drivers/net/wwan/wwan_core.c:529`) and is
   therefore **suppressed on the console at `console_loglevel = 6`** — it can only appear
   in `pmsg`, which is userspace-fed and block-buffered. **So its absence from
   `console-ramoops` is not evidence at all**, and the boot-B-vs-`echo stop` discriminator
   in Doc 167 §3 was resting on a sink that cannot see the line. The discriminator that
   *does* survive is the one used here: **whether `q6v5-trace 01..09` appears**, because
   those are `dev_err` and do reach `console-ramoops`.
3. **Doc 166 §9.1's "≈ 9.6 ms of console time on `rproc_stop()`'s critical path" is
   confirmed and extended** — five probes cost ~40 ms, not one probe ~9.6 ms.
4. **Doc 167 §8's "boot C is the pre-820 control" is now load-bearing**, and it is
   incomplete: boot C is the control for *820-without-probes*. There is no pre-820 control
   *with* the probes, so the probes' contribution cannot be isolated from 820's by
   comparing boot C alone. §2.2 closes most of that gap by using the *clean* post-820 SSR as
   an in-module control — same module, same probes, opposite outcome.
5. **"Re-run 820 without the probes" (Doc 167 §10 item 4) is necessary but is NOT the fix.**
   §2.2 shows the probes only *widened* an existing race; removing them would narrow it back
   to ~0 % while leaving the race in place, so the next unlucky schedule — a slower workqueue,
   a busier console, a different fatal — would hang again. The probes' removal is the right
   *control experiment* (Question C) and the wrong *remedy*.
6. **Doc 166 §9's decode table needs one addition:** `T1` before `T0` is **normal** (§2.2), so
   only "T0 without T1" is diagnostic of workqueue starvation.
7. **§2.2's H1 ranking is retracted** (§2.3, §5). The power-down-timing table was a real
   correlation over n = 3 but it is a correlation with the *outcome*, not the mechanism; run A
   blocks with the modem powered and run B has the "correct" order and still dies.
8. **§4's asymmetry argument is retracted.** §4 argued that because the `echo stop` runs showed
   no `q6v5-trace`, the power-down was not involved. Run B shows the `echo stop` path *does*
   reach `q6v5-trace` when the work completes, so "no trace in the `echo stop` runs" was a
   consequence of the *block*, not a property of the `echo stop` path. The two hangs do not
   have "different suspects" for the reason §4 gave.
9. **The evidence corpus had never seen this corruption before — because it could not.** No
   file in `Docs/Modem Stability/` contains `list_del corruption` or `poll_freewait` except the
   new one. That is not evidence the bug is new; it is evidence that every earlier boot died
   before the teardown completed. **Absence of a line in a corpus that structurally cannot
   reach that line is not absence of the bug.**
10. **A liveness probe is only evidence if it was running during the window** (§5). Run B's
    `NO ANSWER` probes started ~6 s after the reset had begun, and the sampler's next sample
    was simply never due. Neither is a stall measurement.

---

## §8 Pre-registration for patch 821

**Question A — does the flush make the teardown complete?**
*Prediction:* yes. *Test:* `echo stop`, **n = 3** attempts. *Pass:* the AP does **not**
reset; `bam-dmux T5 rx released`, `T6 tx released`, `T7 power_off returned`, `T8 work done`
and `T9 flush returned` all appear in dmesg, **and `T9 flush returned` precedes
`q6v5-trace: 01 disable_qchannel mdm`** — that ordering is the direct test of §2.2's
mechanism, and it is a stronger signal than "it did not crash" because it is the causal
claim rather than the outcome. *Fail:* the AP resets and/or the last T-line is T4/T5/T6/T7 —
which localises the stall, and a missing `T9` with `T8` present would mean the *flush itself*
is the problem.

> **SCORED (2026-09-21, n = 2 usable attempts).** **The structural half PASSES, the outcome
> half FAILS, and the split is the finding.**
>
> * Run B: `T5`→`T9` **all** printed, `T9` at 309.363296, `q6v5-trace 01` at 309.375474 —
>   the predicted order exactly, and `stopped remote processor` followed. **The flush
>   returned and the teardown completed.** §2.3.
> * Run A: the record ends on `T4` with no `T5` — the work blocked **inside**
>   `bam_dmux_power_off()`, before the flush could return.
> * Both runs reset the AP, so *Pass*'s "the AP does not reset" was **not** met — but for a
>   reason the pre-registration did not anticipate: a **wait-queue list corruption** in
>   `qmi-proxy`'s `poll()`, 0.14 ms after the teardown succeeded.
>
> **The pre-registration's own words are the honest verdict: "a missing `T9` with `T8`
> present would mean the flush itself is the problem" — that did not happen. The flush is
> not the problem, and the block it was written to remove is a race that 821 does not close.**
> n = 2 is also below the pre-registered n = 3; a third attempt was lost to the device
> becoming unreachable and is not counted.

**Question B — does the natural ~900 s fatal now recover?**
*Prediction:* yes. *Test:* leave the device soaking; score every fatal. *Pass:* coredumps
are created again (a coredump requires `rproc_stop()` to return), and the AP does not reset.

**Question C — is 820's cancel change actually safe, or did the probes cause boot #1's
hang?**
*Deliberately not answered by this build.* It requires a 820-without-probes variant and is
the next build after 821 is scored.

**Explicitly NOT claimed:** that n = 3 `echo stop` passes prove the fix. A single clean run
proves nothing here — Doc 167 §7 established the failure is a race at ~3-5 %, and
`echo stop` is *suspected* of being 100 % but is only measured 2/2. Question A is a **cheap
structural question** ("does the path now complete?"), and the expensive question ("does
the ~900 s failure still happen?") still needs **n ≥ 60 SSRs** per Doc 167 §7.

---

## §9 Next

**Priority 1 — identify the poller in the wait-queue corruption (§2.3, §2.4).** It is the only
failure that is now *certainly* reproducible, and §2.4 has narrowed it to **one heap wait-queue
head that is freed during the SSR** — `channel->fblockread_event`, reached through
`wwan_port_fops_poll` → `rpmsg_wwan_ctrl_tx_poll` → `rpmsg_poll` → `qcom_smd_poll`. Two cheap
steps, no build:

1. `ls -l /proc/$(pidof qmi-proxy)/fd` (`scratch/check_poller.sh`) — settles whether the
   poller is `/dev/wwan0qmi0` (the SMD chain in §2.4) or `/dev/rpmsg*`
   (`rpmsg_eptdev_poll` → `eptdev->readq`, the other same-lifetime heap head, already the
   subject of patch 819).
2. **Stop qmi-proxy and re-run `echo stop`.** If the corruption disappears the poller
   hypothesis is confirmed; if it persists, §2.4 is wrong and the corruption is something
   else. This is the single most informative free experiment available.

**Priority 2 — settle H3 vs H2 with `rx_telemetry`, no build.** Run `echo stop` with a
sampler that reads **only** `rx_telemetry` in a tight loop (~10 ms period, far faster than the
1.9 s full-sweep sampler that run B used) plus a `/proc/uptime` write. If the block is
`wait_event(rx_submit_wait)` then `rx_active_submitters` is non-zero for the whole hang; if it
is `dmaengine_terminate_sync` (H2) the counters stay zero and the stall is a bus access. That
single reading separates the two remaining candidates.

**Priority 3 — the sampling rate must be part of the instrument.** Run B's sampler had a 1.9 s
period against a ~2 s failure and therefore captured *nothing* of the interesting window. Any
future on-device sampler must state its period and be at least 10× faster than the phenomenon
— the same "measure the instrument before trusting it" rule as Doc 166 §9.1.

**Priority 4 — do not build 821's control yet.** Question C (820-without-probes) is now the
wrong next build: the probes' confound only mattered for H1, which is falsified. Build the
control only if H3/H2 turn out to depend on the probes.

**Priority 5 — recover the device.** It became unreachable after run B and did not return
within ~4 minutes (`usb0` gone on the host, `ping` fail on `192.168.8.0/24`). Establish
whether it needs a power cycle before scheduling anything else, and check `pstore` for a
record that the reboot produced.

Unchanged from Doc 167: score the fatal signature hypothesis (n = 2 per cell) on every fatal
for free; decode the remaining dumps for the `B` cross-boot excess; and the expensive question
("does the ~900 s failure still happen?") still needs **n ≥ 60 SSRs**.

---

## Evidence

`Docs/Modem Stability/evidence/168_teardown_races_powerdown/`

| File | What it is |
| :--- | :--- |
| `console_ramoops_echostop2.txt` | pstore console of the 2nd 820 `echo stop` freeze: T0..T4 @ 165.97, then nothing |
| `pmsg_ramoops_echostop2.txt` | the same boot's userspace-fed pmsg mirror |
| `821_echostop2_teardown_completed_then_wq_corruption.txt` | **run B (821): the whole SSR, T1→T9, the power-down, `stopped remote processor`, and the `list_del corruption` 0.14 ms later** |
| `console_ramoops_821_echostop1.txt` | run A (821): the record that ends on T4 with no T5 — the intermittent block |

Referenced but not duplicated here: `evidence/167_hang_not_deterministic/` (boot C, the
pre-820 control) and `evidence/165_hang_1840s/deploy_820.txt` (the 820 deployment record).
