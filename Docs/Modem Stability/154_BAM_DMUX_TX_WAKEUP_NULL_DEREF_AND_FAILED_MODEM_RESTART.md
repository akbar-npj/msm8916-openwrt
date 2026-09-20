# 154 — A SECOND, DISTINCT `bam_dmux` NULL-DEREF: `tx_wakeup_work` SUBMITS A SLOT WHOSE SKB WAS FREED

**Date:** 2026-09-21. Times are **AP `/proc/uptime` seconds** (the device RTC is unreliable).

**Status:** analysis only — **no source, patch, DT, firmware or userspace file was modified.** The
only device action was a reboot to restore the modem (§6), which was already dead.

> **CORRECTION 2026-09-21 (same day):** §5.3 as first written claimed that `state_lock` does not
> protect against the sweep and that the in-tree comment is therefore false. **That claim was wrong**
> and is replaced by the corrected §5.3 below. Re-reading every call site shows the sweep is
> *always* under `state_lock`; the real hole is that `bam_dmux_netdev_start_xmit()` mutates the slot
> and the deferred bitmap *without* it. The faulting address, the decode and the distinction from
> Doc 147 are unchanged. The fix derived from this is **Doc 155**.

---

## 1. Why this doc exists

While verifying Doc 153, the device produced a **kernel NULL-pointer oops** and then **failed to
restart the modem** after the next fatal. Both are AP-side and both are new:

1. A **NULL dereference in `bam_dmux_skb_dma_submit_tx()`**, reached from
   `bam_dmux_tx_wakeup_work()` — a **different call site** from the `bam_dmux_send_cmd()` oops
   solved in Doc 147 (§4, §5).
2. The modem **never came back** after fatal #15's SSR: `remoteproc0/state` stayed `offline` with
   the log ending at `MBA booted without debug policy, loading mpss`. Only a reboot recovered it
   (§6).

Plus a bonus: the Doc 153 `/etc/rc.local` autostart hook is now **verified at a real boot** (§7).

## 2. SOP compliance

| SOP element | this session |
| :-- | :-- |
| Dual-firmware comparative protocol | **N/A** — no firmware built or transplanted. The deployed baseband is verified clean stock HMU05 (Doc 153 §6b). |
| Ground truth from source, not narrative | **Applied.** §5 is read from the 6.12.94 tree; the struct offset in §4 is **BTF-verified** with `pahole` against the built `vmlinux`, not guessed. |
| Verify before trusting a premise | **Applied.** The in-tree comment at `qcom_bam_dmux.c:399-407` claims `state_lock` protects against `power_off` freeing slots; §5.3 shows that is **false** for `power_off`/`pm_restart`. |
| Distinguish this from the known bug | **Applied.** §5.4 contrasts this oops with Doc 147's by function, fault offset and call path. |
| Record what was done, the result, and what is next | §3–§7 results, §8 status, §9 next. |
| Minimal, reversible device changes | Yes — one reboot, to a device whose modem was already offline. |

## 3. Timeline (one boot)

| AP uptime | event |
| :-- | :-- |
| 12.42 s | modem up, 8 BAM channels opened |
| 914.042 s | fatal #14 (`lte_ml1_common_timer.c:390`); SSR completes at 916.135 s |
| 916.13 s | modem re-provisioned, `CMD_OPEN` on channels 0–7 |
| **1167.938 s** | **kernel oops: NULL pointer dereference at 0x70, `bam_dmux_skb_dma_submit_tx`** |
| 1817.722 s | fatal #15; `rproc_stop` returns at 1817.774 s |
| 1818.590 s | `MBA booted without debug policy, loading mpss` — **and then nothing** |
| 2059 s | `remoteproc0/state = offline`, all 8 `wwan*` DOWN, `ping` → *Network unreachable* |
| 2089 s | reboot (modem dead) |

Note the oops is **252 s after** the modem came back from fatal #14's SSR and **650 s before**
fatal #15 — it is not part of the fatal, it is a runtime TX-path defect.

## 4. The oops, decoded

```
[ 1167.938548] Unable to handle kernel NULL pointer dereference at virtual address 0000000000000070
[ 1167.994312] Internal error: Oops: 0000000096000004 [#1] PREEMPT SMP
[ 1168.053345] CPU: 1 UID: 0 PID: 69 Comm: kworker/1:2 Tainted: G   M       O       6.12.94 #0
[ 1168.088952] Workqueue: pm bam_dmux_tx_wakeup_work [qcom_bam_dmux]
[ 1168.094075] pstate: 80000005 (Nzcv daif -PAN -UAO -TCO -DIT -SSBS BTYPE=--)
[ 1168.100065] pc : bam_dmux_skb_dma_submit_tx+0x2c/0xe0 [qcom_bam_dmux]
[ 1168.106836] lr : bam_dmux_tx_wakeup_work+0xc8/0x258 [qcom_bam_dmux]
[ 1168.113431] sp : ffff8000807a3ce0
[ 1168.119502] x29: ffff8000807a3ce0 x28: 0000000000008003 x27: 0000000000000000
[ 1168.122982] x26: 0000000000000001 x25: 0000000000000002 x24: 0000000000000020
[ 1168.130100] x23: 0000000000000000 x22: 000000000000000f x21: ffff7350019e3080
[ 1168.137219] x20: ffff7350019e36d8 x19: ffff73500104b6e0 x18: 0000010ee3a00dbc
...
[ 1168.219593] Code: f9001bf7 a9400a95 f9400a97 f94036b3 (b9407056)
[ 1168.223163] ---[ end trace 0000000000000000 ]---
```

The faulting register triple is `x0`/`x1`/`x2` (further down the same dump, not quoted in full):
**`x2 = 0000000000000000`**, `x1 = 0000000000000001`, `x0 = ffff8000807a3d20`.

Decoding the parenthesised (faulting) instruction `b9407056`:

* `LDR` (immediate, unsigned offset), 32-bit → opcode `0xB9400000`
* `imm12 = (0x7056 >> 10) & 0xFFF = 28` → byte offset `28 × 4 = 0x70`
* `Rn = (0x7056 >> 5) & 0x1F = 2` → base `x2`, which is **0**
* `Rt = 0x7056 & 0x1F = 22` → destination `w22`

So: **`ldr w22, [x2, #0x70]` with `x2 = NULL`.**

Now `struct sk_buff` on this build, from `pahole -C sk_buff vmlinux`:

```
	unsigned int               len;                  /*   112     4 */
```

**112 = 0x70.** And in `bam_dmux_skb_dma_submit_tx()` the only 32-bit load through a pointer is

```c
	desc = dmaengine_prep_slave_single(dmux->tx, skb_dma->addr,
					   skb_dma->skb->len, DMA_MEM_TO_DEV, ...);
```

⇒ **`skb_dma->skb == NULL`.** The caller's cached copy agrees: `x23 = 0`, and the caller is

```c
		struct bam_dmux_skb_dma *skb_dma = &dmux->tx_skbs[i];
		struct sk_buff *skb = skb_dma->skb;          /* x23 == 0 */
		if (!bam_dmux_skb_dma_submit_tx(skb_dma)) {
```

This is a **byte-level identification, not an inference from the trace alone.**

## 5. The defect

### 5.1 The work does not check the slot

`drivers/net/wwan/qcom_bam_dmux.c`, `bam_dmux_tx_wakeup_work()`:

```c
	pending = atomic_long_xchg(&dmux->tx_deferred_skb, 0);
	if (!pending) { mutex_unlock(&dmux->state_lock); goto out; }

	for_each_set_bit(i, &pending, BAM_DMUX_NUM_SKB) {
		struct bam_dmux_skb_dma *skb_dma = &dmux->tx_skbs[i];
		struct sk_buff *skb = skb_dma->skb;

		if (!bam_dmux_skb_dma_submit_tx(skb_dma)) {   /* derefs skb_dma->skb->len */
```

`skb` is read but **never tested for NULL**, and `bam_dmux_skb_dma_submit_tx()` dereferences it
immediately. A single set bit for a slot with no skb is therefore a NULL dereference.

### 5.2 How a slot with no skb gets a set bit

`bam_dmux_free_skbs()` (`:1349`) is the only place that NULLs TX slots:

```c
		if (skb_dma->skb) {
			dev_kfree_skb(skb_dma->skb);
			skb_dma->skb = NULL;
			...
		}
```

It is called for TX from exactly two places, and **both clear the bitmap and cancel only the
*delayed* retry work — neither cancels the non-delayed `tx_wakeup_work`:**

| caller | line | what it does |
| :-- | :-- | :-- |
| `bam_dmux_power_off()` | 1441–1445 | `cancel_delayed_work_sync(&dmux->tx_retry_work)`; `atomic_long_set(&dmux->tx_deferred_skb, 0)`; `bam_dmux_free_skbs(tx_skbs, …)`; `tx_next_skb = 0` |
| `bam_dmux_pm_restart()` | 1624–1627 | same three lines, same order |

`tx_wakeup_work` is a plain `work_struct` queued with `queue_pm_work(&dmux->tx_wakeup_work)`
(`:602` in `bam_dmux_start_xmit()`, `:1720`) and with `queue_work(system_wq, …)` at `:740` — so
`cancel_work_sync(&dmux->tx_wakeup_work)` appears only at `:1997`, `:2234` and `:2277` (the
SSR-teardown / remove paths).

### 5.3 CORRECTED — `state_lock` *is* held across the sweep; the hole is on the `start_xmit` side

> **This subsection replaces an earlier, wrong claim.** The first version of this doc asserted that
> "neither `bam_dmux_power_off()` nor `bam_dmux_pm_restart()` takes `state_lock` … the guard the
> comment promises does not exist." **That is false**, and it was disproved by re-reading every call
> site rather than only the two function bodies.

Neither function takes the lock *itself*, but **every caller holds it**. Verified call sites:

| caller | line | `state_lock` held? |
| :-- | :-- | :-- |
| `bam_dmux_pc_irq()` (wake, `pm_restart`) | `:1715` | **yes** — `mutex_lock` `:1701` … `mutex_unlock` `:1737` |
| `bam_dmux_pc_irq()` (SSR recovery / teardown, `power_off`) | `:1709`, `:1732` | **yes** — same region |
| `bam_dmux_power_on()` → `power_off` (3 error paths) | `:1304`, `:1328`, `:1335` | **yes** — `power_on` is called at `:1707` (pc_irq), `:2067`, `:2215`, all under the lock |
| `bam_dmux_rx_watchdog_func()` lost-edge resync → `pm_restart` | `:1205` | **yes** — `mutex_lock` `:1191` … `mutex_unlock` `:1212` |
| `bam_dmux_ssr_teardown()` → `power_off` | `:1681` | **yes** — called at `:2074`, `:2220` (under the lock) and `:2293` (`mutex_lock` `:2292`) |

So the `:654` comment — "hold `state_lock` … to protect against concurrent SSR teardown or
`power_off` freeing `dmux->tx` or TX slots" — is **substantially true**. `tx_wakeup_work` and the
sweep really are mutually exclusive, and that is exactly why the consumer-side test in §5.5 is
race-free.

**The actual hole is the other side of the race.** `bam_dmux_netdev_start_xmit()` mutates the TX
slot *and* the deferred bitmap **without `state_lock`** — it is `ndo_start_xmit`, so it runs in
atomic context and cannot take a mutex. It takes only `tx_lock` inside `bam_dmux_tx_queue()`
(`:356`), and it sets the deferred bit at `:599` with **no lock at all**.

The winning interleaving is therefore the *reverse* of the one originally described:

| step | CPU A — `bam_dmux_netdev_start_xmit()` | CPU B — `bam_dmux_pc_irq()` (wake) |
| :-- | :-- | :-- |
| 1 | `tx_queue()` → slot *i* now holds our skb (`:583`) | |
| 2 | `skb_dma_map()` (`:594`) | |
| 3 | reads `pc_state` → **false** (modem still collapsed), so it will take the defer branch (`:597`) | |
| 4 | | `state_lock`; `pc_state = true` (`:1704`); `pm_restart()` → `free_skbs()` **NULLs slot *i*** and `atomic_long_set(tx_deferred_skb, 0)` (`:1625`); unlock |
| 5 | `atomic_long_fetch_or(BIT(i), …)` → **sets bit *i* on the now-empty bitmap** (`:599`) and queues `tx_wakeup_work` (`:602`) | |
| 6 | | `tx_wakeup_work` runs: `pc_state` true, `pending = BIT(i)`, `skb_dma->skb == NULL` → **the crash** |

The sweep clears the bitmap *before* `start_xmit` re-arms it, so the "cleared bitmap" is not a
guard at all. This is the same *shape* as Doc 147's bug — a slot reserved before a PM transition
overtakes it — but on the deferred-bitmap side instead of the map side.

### 5.4 The same race can also fault in `start_xmit` itself

If `start_xmit` reads `pc_state` as **true** at `:597` (the wake has already set it at `:1704`,
before `pm_restart()` swept), it skips the defer branch and calls
`bam_dmux_skb_dma_submit_tx()` at `:609` — the same function, the same `skb_dma->skb->len` load,
the same NULL. `bam_dmux_skb_dma_map()` at `:594` has the identical exposure (`skb_dma->skb->data`
at `0xc8` — literally Doc 147's faulting load). The observed crash was the `tx_wakeup_work` variant;
the `start_xmit` variants are the same defect reached a few instructions earlier.

### 5.5 The fix must not add `cancel_work_sync()` to the two sweeps

Because the sweep always runs **under `state_lock`** (§5.3) and `tx_wakeup_work` also takes
`state_lock` (`:658`), adding `cancel_work_sync(&dmux->tx_wakeup_work)` to `power_off()` or
`pm_restart()` would **deadlock** whenever the work is already running and waiting for the lock the
canceller holds. The fix has to be tolerant on the consumer side instead — see Doc 155.

### 5.6 This is a different bug from Doc 147's

| | Doc 147 (fixed) | this one |
| :-- | :-- | :-- |
| faulting function | `bam_dmux_skb_dma_map+0x20` | `bam_dmux_skb_dma_submit_tx+0x2c` |
| caller | `bam_dmux_send_cmd+0x98` | `bam_dmux_tx_wakeup_work+0xc8` |
| faulting load | `ldr x20, [x1, #0xc8]` (`skb->data`) | `ldr w22, [x2, #0x70]` (`skb->len`) |
| trigger | `send_cmd` reserved a slot **before** `pm_runtime_get_sync`, then `pm_restart` freed it | a **deferred** slot is freed by `power_off`/`pm_restart` while `tx_wakeup_work` is queued |
| fix applied | resume-first ordering in `send_cmd` | **none — this path is still open** |

The Doc 147 fix removed one way to reach a NULL `skb_dma->skb`. It did **not** add a NULL check on
the `tx_wakeup_work` path, so the same underlying hazard remains reachable there.

## 6. Consequence: the modem failed to restart after fatal #15

```
[ 1817.773739] remoteproc remoteproc0: stopped remote processor 4080000.remoteproc
[ 1818.547496] qcom-q6v5-mss 4080000.remoteproc: port failed halt
[ 1818.590009] qcom-q6v5-mss 4080000.remoteproc: MBA booted without debug policy, loading mpss
        <nothing further>
```

`remoteproc0/state` = `offline`; all eight `wwan*` interfaces DOWN; `ping 8.8.8.8` →
*Network unreachable*; the last RX callback was 272.9 s earlier. The modem did not recover on its
own in the following 240 s, and **a reboot was required**.

`bam_dmux` telemetry at that moment (verbatim, selected):

```
pc_state: 0                pc_ack_state: 1          pc_timeout_count: 2
pm_suspend_attempts: 185   pm_resume_attempts: 186  pm_last_suspend_ms: 828
cmd_open: 16               cmd_open_no_a2_pc: 0     a2_pc_disabled: 0
runtime_status: active     pm_usage_count: 1
rx_last_callback_ms_ago: 272851
```

**Whether the earlier oops caused the failed restart is not established.** They are 650 s apart and
the oops only killed one `kworker`. The honest reading: this boot exhibited *two* independent
AP-side failures — a TX-path NULL deref, and a modem PIL boot that stalled at `loading mpss`.
Both need separate follow-up (§9 items 1 and 4).

### 6.1 Refinement (2026-09-21): the stall is inside `q6v5_mpss_load()`, and it is a *hang*

Source-verified against `drivers/remoteproc/qcom_q6v5_mss.c`:

* `port failed halt` is `q6v5proc_halt_axi_port()` (`:974`) — the modem's AXI port **did not go
  idle within `HALT_ACK_TIMEOUT_US` = 100 ms** (`:82`). It is only a warning: the halt request is
  cleared and the port is left halted until reset.
* `MBA booted without debug policy, loading mpss` is printed by `q6v5_start()` (`:1589`)
  immediately **before** `q6v5_mpss_load()` (`:1592`).
* Everything after that point in `q6v5_start()` logs on failure — `q6v5_mpss_load()`'s own
  `dev_err`s, the `q6v5_rmb_mba_wait(..., 10000)` "MPSS authentication timed out" (`:189`, a
  **bounded** 10 s wait), and `qcom_q6v5_wait_for_start(..., 5000)` "start timed out" (`:1598`, a
  **bounded** 5 s wait).

**None of those messages appear**, and the log simply stops for the remaining ~240 s until the
reboot. So the boot did not fail and return an error — it **hung inside `q6v5_mpss_load()`, in a
path with no timeout**, and the bounded waits after it were never reached.

The prime suspect is the interaction with the failed AXI halt: `q6v5_mpss_load()` copies segments
into modem memory and calls `q6v5_xfer_mem_ownership()`, which transfers ownership via an SCM call.
`q6v5_xfer_mem_ownership()` contains **no wait loop at all** (verified), so if the SCM call itself
blocks — plausible when the modem's AXI port is stuck after a failed halt — the boot blocks
forever with nothing to log. **Hypothesis, not proof**: no capture exists from the hung state, and
`remoteproc0/state` was already `offline` when it was read.

## 7. Bonus: the `/etc/rc.local` autostart is verified at a real boot

Doc 153 §6 deployed the hook but could only verify it by hand. This reboot exercised it:

```
uptime 71.93 s   watcher instances: 1     pidfile: 3275
/sys/class/remoteproc/remoteproc0/coredump: enabled
syslog: Sun Sep 20 21:35:08 2026 user.notice coredump-watch: watcher autostarted from rc.local
```

and the watcher's own log shows the expected cold-start line, including the confirmation that the
arming does **not** survive a reboot:

```
[16.28s] === coredump_watch start pid=3275 out=/overlay/coredump_live armed=disabled ===
```

(`armed=disabled` is the state *read* before the loop's first re-arm; it reads `enabled` afterwards.)
So the capture pipeline is now **fully automatic across reboots**: boot → hook → watcher → arm →
capture → release.

## 8. Established vs not established

**Established (measured / source-verified):**

1. A NULL-pointer oops at `0x70` in `bam_dmux_skb_dma_submit_tx+0x2c`, from
   `bam_dmux_tx_wakeup_work+0xc8`, on the `pm` workqueue (§4).
2. The faulting instruction is `ldr w22, [x2, #0x70]` with `x2 = 0`, and `skb->len` is at offset
   **0x70** on this build (`pahole -C sk_buff vmlinux` → `len /* 112 4 */`). The dereferenced
   pointer is `skb_dma->skb` (§4).
3. `bam_dmux_tx_wakeup_work()` does not NULL-check `skb_dma->skb` before submitting (§5.1).
4. `bam_dmux_free_skbs()` NULLs TX slots from exactly two callers, `bam_dmux_power_off()` and
   `bam_dmux_pm_restart()`; both clear `tx_deferred_skb` and cancel only `tx_retry_work`, and
   **neither cancels `tx_wakeup_work`** (§5.2).
5. **Every call to `bam_dmux_power_off()` and `bam_dmux_pm_restart()` is made with `state_lock`
   held** — `pc_irq` `:1701`, the rx-watchdog resync `:1191`, the SSR teardown work `:2059`, the
   SSR powerup work `:2213`, and remove `:2292`. The `:654` comment is therefore **substantially
   true**, and it is why the consumer-side test is race-free (§5.3, corrected).
6. **The hole is on the producer side:** `bam_dmux_netdev_start_xmit()` sets the deferred bit at
   `:599` with no `state_lock` (it is atomic context), so it can re-arm a bit for a slot the sweep
   has already freed — the interleaving table in §5.3. The same race can also fault in
   `start_xmit`'s own `bam_dmux_skb_dma_map()`/`bam_dmux_skb_dma_submit_tx()` calls (§5.4).
7. **`cancel_work_sync(&dmux->tx_wakeup_work)` must NOT be added to the two sweeps** — both run
   under `state_lock` and the work takes it, so that would deadlock (§5.5).
8. The modem failed to restart after fatal #15 and required a reboot (§6).
9. The `/etc/rc.local` autostart works at a real boot (§7).

**Not established:**

* Which exact interleaving won on this occasion (the oops proves the state existed; it does not
  prove the sequence that produced it). §5.3's table is the only interleaving that is *consistent*
  with every fact, not an observed trace.
* Whether the oops caused the failed modem restart (§6).
* Whether the oops is reproducible — **1 occurrence in this boot, 0 in the boot after the reboot**
  (uptime 76 s, `dmesg | grep -c "Unable to handle"` = 0).

## 9. Next experiments, in priority order

1. **Fix the TX-sweep race — DONE, see Doc 155.** The shape is: make the *consumers* of a swept
   slot tolerant, because the producer (`start_xmit`) cannot take `state_lock`. Specifically
   (a) drop set bits whose `skb_dma->skb` is NULL in `tx_wakeup_work`'s loop; (b) NULL-guard
   `bam_dmux_skb_dma_map()` and `bam_dmux_skb_dma_submit_tx()`; (c) stop `start_xmit`'s `drop:`
   path from double-freeing / double-putting when the sweep already took ownership.
   **Do NOT add `cancel_work_sync(&dmux->tx_wakeup_work)` to `power_off()`/`pm_restart()`** —
   both run under `state_lock` and the work takes it, so that deadlocks (§5.5).
   **Any change must land in the tracked driver source (`msm89xx/patches`), not
   `openwrt/target/linux/msm89xx`** — see `project_driver_patch_reproducibility`.
2. **Determine whether the oops is reproducible** — it appeared once in ~1170 s of a boot that
   also had two fatals and heavy A2 power-collapse activity (`pc_vote_tx_count: 186`,
   `pm_suspend_attempts: 185`). Instrument the path or force the interleaving.
3. **Investigate the failed modem restart** (§6) — `port failed halt` then a stall at
   `loading mpss`, with `state` left `offline`. Is it PIL, the MBA, or the A2 handshake?
4. **Build the mpss reader** (Doc 153 §9 item 1) — still the top instrument; unchanged.
5. **Keep the watcher running** — it is now verified to autostart, so every clean fatal is captured
   without intervention.

## 10. Artifacts

| what | where |
| :-- | :-- |
| full dmesg of the oops boot (408 lines) | `scratch/f16_oops/dmesg_full.txt` |
| `bam_dmux` telemetry at the dead-modem moment | `scratch/f16_oops/telemetry_after_oops.txt` |
| oops decode + `pahole` offset proof | §4 of this doc |
| driver source (read-only) | `openwrt/build_dir/…/linux-6.12.94/drivers/net/wwan/qcom_bam_dmux.c` |
| tracked driver source of truth | `msm89xx/patches` (NOT `openwrt/target/linux/msm89xx`) |
| **the fix** | `msm89xx/patches/810-bam-dmux-tx-sweep-race.patch` — see **Doc 155** |

## 11. One line

A second, **distinct** `bam_dmux` NULL dereference — `bam_dmux_tx_wakeup_work` submitting a TX slot
whose `skb_dma->skb` was NULLed by `bam_dmux_free_skbs()` — proved byte-for-byte by decoding
`ldr w22, [x2, #0x70]` with `x2 = 0` against the BTF-verified `skb->len` offset; the sweep is always
under `state_lock` (**correcting this doc's first version**), so the race is that
`bam_dmux_netdev_start_xmit()` re-arms the deferred bit without it, after the sweep already cleared
it; and, separately, **the modem failed to restart after fatal #15**, stalling at `loading mpss`
with `remoteproc0/state = offline` until a reboot — while the Doc 153 `/etc/rc.local` autostart was
verified working at a real boot. Fix: **Doc 155** / patch 810.
