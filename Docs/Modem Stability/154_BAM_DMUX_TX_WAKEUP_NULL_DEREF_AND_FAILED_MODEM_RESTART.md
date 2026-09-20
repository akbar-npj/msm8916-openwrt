# 154 — A SECOND, DISTINCT `bam_dmux` NULL-DEREF: `tx_wakeup_work` SUBMITS A SLOT WHOSE SKB WAS FREED

**Date:** 2026-09-21. Times are **AP `/proc/uptime` seconds** (the device RTC is unreliable).

**Status:** analysis only — **no source, patch, DT, firmware or userspace file was modified.** The
only device action was a reboot to restore the modem (§6), which was already dead.

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

So: **`bam_dmux_start_xmit()` can defer an skb (set a bit + queue `tx_wakeup_work`), and then
`power_off()` or `pm_restart()` frees that slot and NULLs `skb_dma->skb` while the already-queued
work is still pending.**

### 5.3 The lock comment is wrong

`tx_wakeup_work` takes `state_lock` and documents why:

```
	 * Hold state_lock across the confirmed-awake check, deferred bitmap exchange,
	 * TX submissions, and dma_async_issue_pending() to protect against concurrent
	 * SSR teardown or power_off freeing dmux->tx or TX slots.
```

**But neither `bam_dmux_power_off()` (`:1382`) nor `bam_dmux_pm_restart()` (`:1607`) takes
`state_lock` anywhere** — verified: there is no `state_lock` reference at all in lines 1400–1700.
`state_lock` therefore serialises `tx_wakeup_work` only against the **SSR teardown** (which does
take it), not against the two functions that actually free the slots. The guard the comment
promises does not exist.

### 5.4 This is a different bug from Doc 147's

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
5. Neither `bam_dmux_power_off()` nor `bam_dmux_pm_restart()` takes `state_lock`, so the
   in-tree comment claiming `state_lock` protects against `power_off` is **wrong** (§5.3).
6. The modem failed to restart after fatal #15 and required a reboot (§6).
7. The `/etc/rc.local` autostart works at a real boot (§7).

**Not established:**

* Which exact interleaving won on this occasion (the oops proves the state existed; it does not
  prove the sequence that produced it).
* Whether the oops caused the failed modem restart (§6).
* Whether the oops is reproducible — **1 occurrence in this boot, 0 in the boot after the reboot**
  (uptime 76 s, `dmesg | grep -c "Unable to handle"` = 0).

## 9. Next experiments, in priority order

1. **Fix the `tx_wakeup_work` path.** Minimal, defensive and independent of the interleaving: in
   the `for_each_set_bit` loop, **skip and drop bits whose `skb_dma->skb` is NULL** (they can never
   be submitted). Optionally also `cancel_work_sync(&dmux->tx_wakeup_work)` in `power_off()` /
   `pm_restart()`, and/or take `state_lock` in those two functions so the comment becomes true.
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

## 11. One line

A second, **distinct** `bam_dmux` NULL dereference — `bam_dmux_tx_wakeup_work` submitting a TX slot
whose `skb_dma->skb` was NULLed by `bam_dmux_free_skbs()` (called from `power_off`/`pm_restart`,
which take no `state_lock` and never cancel the non-delayed `tx_wakeup_work`, contrary to the
comment that claims they do) — proved byte-for-byte by decoding `ldr w22, [x2, #0x70]` with
`x2 = 0` against the BTF-verified `skb->len` offset; and, separately, **the modem failed to restart
after fatal #15**, stalling at `loading mpss` with `remoteproc0/state = offline` until a reboot —
while the Doc 153 `/etc/rc.local` autostart was verified working at a real boot.
