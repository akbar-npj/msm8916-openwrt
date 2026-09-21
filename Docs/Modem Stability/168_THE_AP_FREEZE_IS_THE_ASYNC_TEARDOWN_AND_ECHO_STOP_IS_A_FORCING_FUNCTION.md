# Doc 168 — The AP freeze is the *asynchronous* `ssr_teardown_work`; and `echo stop` is now a reliable forcing function

**Date:** 2026-09-21
**Device:** HMU05 4G dongle (MSM8916 + WTR1605 + QFE2320), OpenWrt 25.12.5 / Linux 6.12.94
**Baseband:** stock HMU05 (verified clean; `modem.mdt` unchanged — Doc 153)
**Patch under test:** 820 (`qcom_bam_dmux.ko` md5 `e441491317f09e34a3e72dc15cdacd3c`)
**Patch written this round:** 821 (flush the teardown work in the SSR notifier)

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
  `echo stop` *safe* is **falsified** — see §4.

**Steps not taken, and why:** the `echo stop` test was run before the first healthy
post-820 fatal was scored (Doc 167 §10 advised scoring the fatal first). This is a
deliberate deviation: the two post-820 boots had already produced two hangs, so the
"healthy post-820 SSR" the sequence was waiting for did not exist to wait for. The
deviation is recorded here rather than hidden.

---

## §1 Headline

**Patch 820 works — and it moved the hang.**

Pre-820, the SSR teardown work died **before its first print** (Doc 166: the last line
ever was the notifier's `SSR before shutdown: scheduling teardown work`). With 820
deployed, the work **runs**, gets past both `cancel_*` calls, and acquires `state_lock`
(T0 → T1 → T2 → T3 → **T4**). The AP then freezes **after T4**, with **no coredump**, and
silently resets.

That is a strictly later failure point, and it is now **reproducible on demand**:

```
echo stop > /sys/class/remoteproc/remoteproc0/state     # returns 0, then the AP freezes
```

**2/2 `echo stop` attempts froze the AP.** This collapses the Doc 167 §7 "n ≥ 60 SSRs,
~18 h" iteration loop to **about two seconds per test**.

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

Note the asymmetry with §2: in **both** `echo stop` runs the power-down
(`q6v5-trace 01..09`) **never printed**, yet the AP still froze. See §2.1.

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

1. The teardown work runs to `state_lock` (T4) and then the AP stops printing.
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

**Hypotheses, in order of the evidence:**

* **H1 — ordering race against the modem power-down.** The DMA release touches the
  BAM-DMUX BAM, which the driver's own comment (`:1296`) places *in the modem's power
  domain*. If the work runs after `q6v5_stop()` has gated the modem
  (`regulator_disable active`, trace 07), a BAM register access stalls. **Explains boot
  #1 and the probes' effect; does not explain boot #2 or the `echo stop` runs** (§2.1).
* **H2 — the BAM's *own* runtime PM.** `bam_dma_terminate_all()` (`drivers/dma/qcom/bam_dma.c`)
  takes **no** `pm_runtime_get_sync()`, unlike every other hardware-touching BAM entry point
  (`:576`, `:779`, `:805`, `:914`, `:1032`). If the BAM device is autosuspended
  (`BAM_DMA_AUTOSUSPEND_DELAY`, `:1368`), `bam_chan_init_hw()` inside it writes to an
  unclocked block. **This one is timing-dependent and does not require the modem to be
  down**, so it fits §2.1 — but it is a hypothesis read out of another driver, not a
  measurement.
* **H3 — a wait inside the `state_lock` section never completes** (`rx_callback_wait`,
  `rx_submit_wait`, or `cancel_delayed_work_sync(rx_rearm_work)`).

**The freeze signature is consistent with a stalled CPU, not a wedged console:** in the
`echo stop` runs the shell still received `RC=0` over **SSH (USB, `usb0`)** while the
**serial console** had already stopped at T4. A bus stall on the CPU performing the
console write explains both; a printk/console-path bug does not (and Doc 166 §5.5 already
retracted that family of explanation).

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

**Why this is the right place.** `rproc_stop_subdevices()` runs the notifier chain in the
**recovery thread's process context**, so the notifier may sleep. Flushing there makes the
ordering deterministic: the BAM is terminated and released **before**
`rproc->ops->stop()` powers the modem down. It also removes the race entirely rather than
narrowing it — which is what a fix has to do, because "the work usually wins" is exactly
the ~3-5 % failure Doc 167 measured.

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
   comparing boot C alone.

---

## §8 Pre-registration for patch 821

**Question A — does the flush make the teardown complete?**
*Prediction:* yes. *Test:* `echo stop`, **n = 3** attempts. *Pass:* the AP does **not**
reset; `bam-dmux T8 work done` and `bam-dmux T9 flush returned` both appear in dmesg.
*Fail:* the AP resets and/or the last T-line is T4/T5/T6/T7 — which localises the stall.

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

1. Deploy `qcom_bam_dmux.ko` from the 821 build; verify md5 and the T5–T9 strings in the
   binary before deploying (never trust the build).
2. Run **Question A**: `echo stop` × 3. Record the full T-line sequence each time.
3. If it passes, leave the device soaking for **Question B**, and keep the `ssr_ledger`
   and the coredump watcher armed.
4. If it fails, read the last T-line and localise inside `bam_dmux_power_off()`.
5. **Then** build the 820-without-probes control (Question C).
6. Unchanged from Doc 167: score the fatal signature hypothesis (n = 2 per cell) on every
   fatal for free; decode the remaining dumps for the `B` cross-boot excess.

---

## Evidence

`Docs/Modem Stability/evidence/168_teardown_races_powerdown/`

| File | What it is |
| :--- | :--- |
| `console_ramoops_echostop2.txt` | pstore console of the 2nd `echo stop` freeze: T0..T4 @ 165.97, then nothing |
| `pmsg_ramoops_echostop2.txt` | the same boot's userspace-fed pmsg mirror |

Referenced but not duplicated here: `evidence/167_hang_not_deterministic/` (boot C, the
pre-820 control) and `evidence/165_hang_1840s/deploy_820.txt` (the 820 deployment record).
