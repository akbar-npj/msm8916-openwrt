# 156 — ROOT CAUSE OF THE DATA STALL: the first packet after every A2 collapse is destroyed by the wake path. Patches 811 + 812.

**Date:** 2026-09-21. Times are **AP `/proc/uptime` seconds** (the device RTC is unreliable).

**Status:** **root cause found, mechanism measured, fix written, deployed and verified by A/B.** This
is the defect behind the user-visible "browse / DNS hangs after the link has been idle" symptom that
`project_stall_first_packet_lost.md` recorded as `dtx=1 drx=0`, 5/5, and behind the watchdog
escalation that it explains.

Source changed in the **tracked** tree (`msm89xx/patches/811-*.patch`, `msm89xx/patches/812-*.patch`);
the deployed `/lib/modules/6.12.94/qcom_bam_dmux.ko` was replaced (two previous builds backed up to
`/overlay/modbackup/`).

**Line numbers below are from the fixed file**, `qcom_bam_dmux.c.fixed` in the evidence directory.
Where a number differs in the pre-fix file it is given as `(was :NNN)`.

---

## 1. Why this doc exists

Docs 154 and 155 were both about a **rare** fault: a NULL dereference when a `bam_dmux_free_skbs()`
sweep races `bam_dmux_netdev_start_xmit()`. Patch 810 made the consumers tolerant of it. The soak
that followed was clean but, as Doc 155 §8.4 said plainly, a clean soak of a rare race is **not a
proof** — `guard_hits` stayed 0, so the fix was never observed to fire.

While instrumenting that race, a much larger and completely different defect fell out of the same
code path — and unlike the race it is **not rare, not timing-dependent, and not a crash**:

> **Every packet that `bam_dmux_netdev_start_xmit()` defers is destroyed by the very wake it is
> waiting for.**

Measured: **27 packets deferred, 27 destroyed, 0 delivered** in the first minute of one boot. A
single ping sent to a collapsed modem was lost **3/3**; the identical ping sent to an awake modem was
delivered. That is the data stall, and it is 100 % AP-side driver logic.

This doc records the mechanism (§4), the measurement that proves it (§5), the fix (§6), and the A/B
that verifies it (§7).

## 2. SOP compliance

| SOP element | this session |
| :-- | :-- |
| Dual-firmware comparative protocol | **N/A** — no firmware built, patched or transplanted. The deployed baseband is verified clean stock HMU05 (Doc 153 §6b). |
| Ground truth from source, not narrative | **Applied, and it is what found the bug.** The existing corpus had the *symptom* (`project_stall_first_packet_lost.md`: `dtx=1 drx=0`, 5/5) and had already closed the RX path as the cause, but no mechanism. Reading `bam_dmux_netdev_start_xmit()` → `pm_runtime_get()` → `bam_dmux_pm_restart()` as one sequence, and checking `pm_runtime_get()`'s **actual definition** rather than assuming it blocked, produced the mechanism. |
| Do not trust a name — check the definition | **Applied, and decisive.** `pm_runtime_get()` is `__pm_runtime_resume(dev, RPM_GET_PUT \| RPM_ASYNC)` (`include/linux/pm_runtime.h:400-403`) — **asynchronous**. Assuming it was the blocking `_sync` variant is what makes the bug invisible on inspection. |
| Measure before and after, on the same device, same test | **Applied.** §5 and §7 are the same 25 s-idle-then-one-ping test on the same boot procedure, differing only in the module. |
| Instrument so the claim is falsifiable | **Applied.** Patch 811 adds six counters that make "was the packet lost?" a number, not an argument. |
| Minimal, reversible change | Yes — patch 812 is one new helper plus one function's preserve-list; two previous modules backed up on the device. |
| Record what was done, the result, and what is next | §5–§7 results, §8 scope, §9 status, §10 next. |

## 3. How it was found

Patch 810's `tx_sweep_guard_hits` counter counts a *stale bit* — a deferred slot whose skb had already
been freed. During the patch-810 soak it stayed at 0. That is consistent with two opposite worlds:

* the race is real but rare, **or**
* `bam_dmux_tx_wakeup_work()` never sees a stale bit because something else already cleared the
  bitmap and freed the skb.

The second possibility is the dangerous one, because it produces **no crash and no counter** — just a
silently missing packet. So patch 811 was written to distinguish them, by counting the defer branch,
the successful deferred submit, and the wipe, separately (§5.1). The first sample after boot settled
it: `queued 27, submitted 0, wiped_live 27`.

## 4. The mechanism

### 4.1 The ordering, as one sequence

Everything below is in the **fixed** file. All of it exists in the pre-fix file too — only the
consequences change.

| # | where | what happens |
| :-- | :-- | :-- |
| 1 | `start_xmit` `:645` | `active = pm_runtime_get(dmux->dev);` — the modem is autosuspended, so this **queues an async resume** and returns `-EINPROGRESS`. It does **not** wait. |
| 2 | `start_xmit` `:653` | `bam_dmux_tx_queue()` reserves a slot and installs the skb. |
| 3 | `start_xmit` `:664` | `bam_dmux_skb_dma_map()` maps the buffer. **No descriptor is submitted** — `bam_dmux_skb_dma_map()` (`:257`) only calls `dma_map_single()`. |
| 4 | `start_xmit` `:667` | `if (active <= 0 \|\| !READ_ONCE(dmux->pc_state))` → **true** (`-EINPROGRESS <= 0`, and `pc_state` is still false because the resume has not run yet). |
| 5 | `start_xmit` `:669-674` | Sets `BIT(slot)` in `tx_deferred_skb`, queues `tx_wakeup_work`, and returns `NETDEV_TX_OK`. **The stack now considers the packet sent.** |
| 6 | async resume | `bam_dmux_runtime_resume()` (`:1970`) votes `SMSM_A2_POWER_CONTROL` high, then waits for the ack and for `pc_state` (`:1987`, `:1996`). |
| 7 | `pc_irq` `:1863` | The modem asserts the A2 line. `pc_state = true`. |
| 8 | `pc_irq` `:1874` | **`bam_dmux_pm_restart(dmux)`.** |
| 9 | `pm_restart` `:1778-1780` | **`deferred = atomic_long_read(&dmux->tx_deferred_skb);` then `bam_dmux_free_skbs_except(..., deferred)`.** Pre-fix this was `atomic_long_set(&dmux->tx_deferred_skb, 0)` followed by `bam_dmux_free_skbs(dmux->tx_skbs, DMA_TO_DEVICE)` — i.e. it cleared the bit and freed the skb of the packet from step 5. |
| 10 | `pc_irq` `:1878` | `if (atomic_long_read(&dmux->tx_deferred_skb) && ...) queue_pm_work(&dmux->tx_wakeup_work);` — **pre-fix this was always false**, because step 9 had just zeroed the bitmap. The "drain deferred TX packets" step could never fire. |
| 11 | `tx_wakeup_work` `:716` | Pre-fix: `pending = atomic_long_xchg(&dmux->tx_deferred_skb, 0)` returns **0** → `goto out`. **The packet is gone.** Post-fix: `pending` contains the preserved bit and the slot is submitted. |

### 4.2 Why the defer branch is the *normal* case, not an edge case

`pm_runtime_get()` being async is the crux. For the **first packet after the modem has collapsed**
the device is RPM-suspended, so `__pm_runtime_resume(..., RPM_ASYNC)` returns `-EINPROGRESS` — the
resume has been *started*, not *completed*. `start_xmit` is in atomic context (`ndo_start_xmit`) and
must not sleep, which is exactly why the driver uses the async variant here. So the branch at `:667`
is taken on the first packet of every wake, deterministically.

The second packet is different: by then the resume has completed, `pm_runtime_get()` returns `1`
(already active) and `pc_state` is true, so `:680` submits directly. **That is why "the retry always
works"** — the single most distinctive fact about this stall.

### 4.3 Why nothing retransmits it

`start_xmit` returned `NETDEV_TX_OK` at `:675`. The network stack therefore believes the skb was
consumed, and the qdisc holds nothing to retry. `bam_dmux_pm_restart()` ends with
`bam_dmux_tx_wake_queues()` (`:1813`), but `netif_wake_queue()` only restarts a queue that was
previously *stopped* — it cannot resurrect a packet the driver already acknowledged.

So the loss is total for that packet. TCP recovers by RTO; a single ICMP echo, a single DNS query, or
a single UDP datagram does not. This is precisely why `modem-bearer-watchdog`'s pre-escalation gate —
which made **one** DNS attempt — ate it (`project_stall_first_packet_lost.md`).

### 4.4 The invariant the code states, and then violates

`bam_dmux_runtime_suspend()` carries an explicit guard (`:1933`):

```c
	/* Guard 1: Abort if deferred TX packets are queued */
	if (atomic_long_read(&dmux->tx_deferred_skb))
		return -EBUSY;
```

and Guard 2 (`:1937`) aborts while any `tx_skbs[i].skb` is set. Together these say: *a deferred packet
pins the device awake until it has been delivered.* `bam_dmux_pm_restart()` clearing the bitmap and
freeing the slot directly contradicts that invariant — it is the one path that throws the work away
instead of draining it. Patch 812 makes `pm_restart()` obey the invariant the suspend path already
relies on.

## 5. The measurement (patch 811, no behaviour change)

### 5.1 The counters

Patch 811 adds six `atomic64_t` counters, all exposed in `rx_telemetry`:

| counter | site | meaning |
| :-- | :-- | :-- |
| `tx_defer_queued` | `start_xmit:669` | the defer branch was taken |
| `tx_defer_submitted` | `tx_wakeup_work:803` | a deferred slot was actually submitted |
| `tx_defer_preserved` | `pm_restart:1779` | deferred bits carried across a wake (added by 812) |
| `tx_defer_wiped` | `power_off` / `pm_restart` | deferred bits discarded |
| `tx_defer_wiped_live` | same | of those, how many **still had an skb** = real packets destroyed |
| `tx_submit_ok` / `tx_complete` | `submit_tx` / `tx_callback` | direct-path submissions and completions |

`tx_defer_wiped_live` is the decisive one: it separates "a stale bit was cleaned up" from "a packet
was thrown away".

### 5.2 The result, one minute after boot

Module `26014c0e78f89455904ae7e0db7d017c`, patch 810 + 811, **no fix**:

```
tx_defer_queued: 27      <- 27 packets took the defer branch
tx_defer_submitted: 0    <- NONE was ever submitted
tx_defer_wiped: 27
tx_defer_wiped_live: 27  <- all 27 had a live skb: 27 real packets destroyed
tx_submit_ok: 18
tx_complete: 18          <- the direct path works fine
```

**27 of 45 TX packets (60 %) were silently destroyed on the AP side**, with no oops, no `fatal`, no
SSR, and `tx_sweep_guard_hits: 0`. This is invisible to every stability gate the project had.

### 5.3 The symptom, reproduced and attributed

Same module, `defertest.sh`: idle 25 s (long enough for autosuspend and the modem's own ~5.4 s
collapse), snapshot, **one** ping, snapshot. Full transcript: `ab_before_fix.txt`.

| round | state before | `tx_defer_queued` | `tx_defer_submitted` | `tx_defer_wiped_live` | ping |
| :-- | :-- | :-- | :-- | :-- | :-- |
| 1 | `suspended pc=0` | +1 | +0 | +1 | **100 % loss** |
| 2 | `suspended pc=0` | +5 | +0 | +5 | **100 % loss** |
| 3 | `active pc=1` | +0 | +0 (direct: `tx_submit_ok` +1, `tx_complete` +1) | +0 | **received** |
| 4 | `suspended pc=0` | +1 | +0 | +1 | **100 % loss** |

3/3 lost when collapsed, 1/1 delivered when awake, and the counter deltas track the outcome exactly.
Round 3 is the control that rules out "the modem is just slow to answer": the same packet on the same
link succeeds the moment it takes the direct path instead of the defer branch.

## 6. The fix (patch 812)

Two changes, both in `qcom_bam_dmux.c`:

**6.1 — `bam_dmux_free_skbs_except()` (`:1476`).** The existing `bam_dmux_free_skbs()` body, with a
`keep` bitmap parameter; slots whose bit is set are skipped entirely (not unmapped, not freed, PM
reference retained). `bam_dmux_free_skbs()` becomes a one-line wrapper passing `keep = 0`, so the RX
path and the SSR path are untouched.

**6.2 — `bam_dmux_pm_restart()` (`:1778-1780`).** Preserve instead of discard:

```c
	cancel_delayed_work_sync(&dmux->tx_retry_work);
	deferred = atomic_long_read(&dmux->tx_deferred_skb);
	atomic64_add(hweight_long(deferred), &dmux->tx_defer_preserved);
	bam_dmux_free_skbs_except(dmux->tx_skbs, DMA_TO_DEVICE, deferred);
	/*
	 * tx_next_skb is deliberately NOT reset: slots named by `deferred` are
	 * still occupied ...
	 */
```

Three details that make this correct:

* **The DMA mapping survives.** `bam_dmux_skb_dma_map()` maps for the *device*
  (`skb_dma->dmux->dev`), not for the channel, so `dmaengine_terminate_sync()` +
  `dma_release_channel()` in `bam_dmux_dma_release()` do not invalidate it. `tx_wakeup_work` rebuilds
  the descriptor on the fresh channel from the same `skb_dma->addr`.
* **`tx_next_skb` must not be reset.** The pre-fix reset to 0 was safe only because every slot had
  been freed. With slots preserved, restarting the ring at 0 would hand out an occupied slot, stop the
  TX queue and wait for a completion that has not even been submitted yet.
* **`power_off()` is unchanged and still discards.** On SSR teardown the whole BAM-DMUX state is being
  destroyed, so dropping is correct there. `tx_defer_wiped` therefore still exists and is expected to
  move on an SSR — it is the `pm_restart` case that had to change.

The drain at `pc_irq:1878` now finds a non-zero bitmap and queues `tx_wakeup_work`, which submits the
preserved slots. No change was needed there; it was correct all along and was simply unreachable.

## 7. Verification

### 7.1 Counter level, one minute after boot

Module `68dcc4ace88a1729d6081569c72c6383`, patch 810 + 811 + 812:

| counter | before fix | after fix |
| :-- | --: | --: |
| `tx_defer_queued` | 27 | 8 |
| `tx_defer_submitted` | **0** | **8** |
| `tx_defer_preserved` | — (did not exist) | 8 |
| `tx_defer_wiped` | 27 | **0** |
| `tx_defer_wiped_live` | **27** | **0** |
| `tx_submit_ok` / `tx_complete` | 18 / 18 | 33 / 33 |

`queued == preserved == submitted`, and nothing wiped. (`tx_defer_queued` also *fell* from 27 to 8:
before the fix the undelivered packets provoked retries and more deferrals; now they are delivered
first time.)

### 7.2 The same symptom test

Identical procedure. Full transcript: `ab_after_fix.txt`.

| round | state before | `tx_defer_queued` | `tx_defer_submitted` | `tx_defer_wiped_live` | ping |
| :-- | :-- | :-- | :-- | :-- | :-- |
| 1 | `suspended pc=0` | +1 | **+1** | +0 | **received** |
| 2 | `suspended pc=0` | +1 | **+1** | +0 | **received** |
| 3 | `active pc=1` | +0 | +0 (direct path) | +0 | **received** |
| 4 | `suspended pc=0` | +1 | **+1** | +0 | **received** |
| 5 | `suspended pc=0` | +1 | **+1** | +0 | **received** |

**5/5 delivered, including 4/4 from the exact condition that lost 3/3 before the fix.**

### 7.3 The user-visible symptom: DNS after idle

The original symptom was never ICMP-specific — it is the browse/DNS-after-idle hang, and
`modem-bearer-watchdog`'s pre-escalation gate made **one** DNS attempt. A DNS query is a single UDP
datagram, so it has no application-layer retransmit. Idle 30 s, then `nslookup example.com 8.8.8.8`
(a direct upstream query, so dnsmasq's cache cannot mask the result). Transcript:
`dns_after_idle_after_fix.txt`.

| round | `pc_state` before | `tx_defer_queued` | `tx_defer_submitted` | `tx_defer_wiped_live` | DNS answers |
| :-- | :-- | :-- | :-- | :-- | :-- |
| 1 | 1 | +3 | +3 | +0 | 3 |
| 2 | **0** | +3 | +3 | +0 | 3 |
| 3 | **0** | +3 | +3 | +0 | 3 |
| 4 | 1 | +1 | +1 | +0 | 3 |

**4/4 answered on the first attempt**, including the two rounds that started from `pc_state=0`.

### 7.4 Patch-chain integrity

The patches were verified to reproduce the built source exactly, anchored on the previously verified
pre-810 snapshot:

```
qcom_bam_dmux.c.prefix810  + 810  == qcom_bam_dmux.c.pre811      (ANCHOR OK)
qcom_bam_dmux.c.pre811     + 811  == qcom_bam_dmux.c.post811
qcom_bam_dmux.c.post811    + 812  == qcom_bam_dmux.c.fixed == the built file   (CHAIN VERIFIED)
```

## 8. What this does and does not explain

**Explains:**

* The `dtx=1 drx=0` stall (`project_stall_first_packet_lost.md`) — `dtx` is incremented at
  `start_xmit:675`, on the defer branch, before the packet is destroyed. It was never evidence that
  the packet reached the wire.
* "The retry always works" (§4.2).
* Why it is the *first* packet after idle and not a general packet-loss problem.
* Why only one attempt is enough to trigger the watchdog's escalation (§4.3).
* Why Android is unaffected: this is OpenWrt's `bam_dmux` driver logic, not the modem firmware — and
  the firmware was already proven byte-identical between the two (Doc 149 / the corpus' firmware
  identity proof).

**Does not explain — do not claim it does:**

* **The ~900 s fatal** (`a2_power.c:1189`, `lte_ml1_common_timer.c:390`). That is a modem-internal
  timer and nothing here touches it. Whether the packet loss *contributes* — by keeping the modem
  collapse/wake cycle churning, or by starving it of traffic — is now a testable question, because the
  loss is gone. §10 item 1.
* **The failed modem restart after fatal #15** (Doc 154 §6/§6.1). Unrelated path.
* **The TX-sweep race** (patch 810). Still real, still unobserved firing; the residual window is
  described in §9.

### 8.1 Confirmed in passing: a fatal fired with the fix deployed, and the modem restarted

At AP **424.573497 s** — during the DNS-after-idle test, with patch 812 deployed — the modem took
`fatal error received: a2_power.c:1189` (crash #1) and the AP ran a full SSR. Transcript:
`fatal_at_424s_with_patch812.txt`. Three things follow:

1. **The fix does not prevent the fatal.** Expected and stated above; the fatal is a modem-internal
   timer and patch 812 only stops the AP destroying its own deferred packets. **Do not cite patch 812
   as a stability fix.**
2. **The timing is variable again** — 424.57 s AP ≈ 413 s of modem uptime, neither the ~902 s idle
   deterministic timer nor the ~900 s family. Consistent with the corpus' "`a2_power.c:1189` is a
   *rate*, not a period" and with "traffic suppresses the deterministic idle fatal and substitutes a
   variable one" (Doc 149 §3). The traffic here was low-rate and bursty, which is neither the idle nor
   the sustained case.
3. **`port failed halt` is not sufficient to cause the restart hang.** Doc 154 §6 recorded a fatal
   after which the modem never came back: `port failed halt`, then a stall at `MBA booted without
   debug policy, loading mpss` with nothing after it, `state = offline`, reboot required. The
   **identical** warning appeared here (425.340321), followed by the same `MBA booted ... loading mpss`
   (425.385456) — and this time the load completed in ~0.56 s (`remote processor ... is now up`,
   425.949302). So the halt warning (AXI port not idle within `HALT_ACK_TIMEOUT_US`,
   `q6v5proc_halt_axi_port:974`) is **not** by itself the cause. Doc 154 §6.1's SCM-blocking
   hypothesis survives, but the hang is **intermittent**, not a deterministic consequence.

Recovery was clean: the remaining DNS rounds succeeded, `tx_defer_wiped_live` stayed 0, and
`tx_defer_queued == tx_defer_submitted` across the SSR.

## 9. Established vs not established

**Established:**

* Every deferred packet was destroyed by `bam_dmux_pm_restart()` before patch 812 — 27/27 measured,
  with the surviving-skb count to prove they were real packets and not stale bits.
* The loss is the cause of the measured single-packet-after-idle stall — same test, same device,
  3/3 lost before and 5/5 delivered after, with the discriminating counter moving in lockstep.
* The **user-visible** symptom is fixed: a DNS query after 30 s idle (one UDP datagram, no
  application-layer retransmit) is answered **4/4 on the first attempt**, including from `pc_state=0`.
* Patch 812 preserves and delivers them; `tx_defer_wiped_live` is 0 across the test.
* The fix is inert for the direct path: `tx_submit_ok`/`tx_complete` behaviour is unchanged.
* The patch chain reproduces the built source exactly.

**Not established:**

* **Whether the residual `start_xmit` window still loses packets.** A `pm_restart()` that reads
  `tx_deferred_skb` *before* `start_xmit` sets the bit will still free that slot; the bit is then
  stale and `tx_wakeup_work` drops it (`tx_sweep_guard_hits`, patch 810 hunk 3). This window is the
  `bam_dmux_free_skbs_except()` loop, order 10²–10³ µs, and it is counted — but `guard_hits` is
  still 0, so it has not been observed. It is strictly narrower than the pre-812 behaviour and does
  not regress it.
* **Long-run stability.** The verification above is minutes long, not hours. A soak is running (§11);
  the metric to watch is `tx_defer_queued − tx_defer_submitted`, which must stay 0.
* **Any protective effect on the fatal.** One fatal was observed with the fix deployed, at 424.57 s
  (§8.1) — i.e. **no protective effect was seen** — but a single observation under a low-rate bursty
  traffic pattern says nothing about the ~902 s idle case. Untested.
* **Whether the fix changes the collapse/wake rate or the fatal timing distribution.** The natural
  experiment: the same soak on the pre-812 module vs the post-812 module and compare fatal times.
  Not run.
* **Whether `tx_defer_wiped_live` can be non-zero post-fix.** Only via `power_off()` (SSR), where it
  is expected and correct.

## 10. Next experiments, in priority order

1. **Long soak with the fix** and read `tx_defer_queued − tx_defer_submitted` (§11). Then the
   **fatal A/B**: run the identical soak on the pre-812 module (`/overlay/modbackup/qcom_bam_dmux.ko.p811`)
   and on the post-812 module, and compare fatal times and the collapse/wake rate. The corpus'
   `project_fatal_periodicity_and_signatures.md` says traffic *suppresses* the deterministic idle fatal
   and substitutes a variable one — the fix changes what traffic actually reaches the modem, so the
   fatal timing distribution is now a live variable. One fatal was already observed with the fix
   deployed at 424.57 s (§8.1), which is neither the idle family nor the sustained-traffic family.
2. **Close the residual `start_xmit` window** (§9) if `tx_sweep_guard_hits` ever moves. The clean way
   is to make the slot install and the bit set atomic against the sweep — e.g. have `tx_queue()` take
   the defer bit under `tx_lock`, or have `pm_restart()` re-check after freeing.
3. **Re-examine the watchdog policy.** `modem-bearer-watchdog`'s retry logic exists to paper over this
   bug; with the bug fixed the gate's behaviour should be re-measured rather than left as a
   workaround. Do not remove it yet — §9's residual window is still open.
4. **Investigate the failed modem restart** (Doc 154 §6/§6.1).
5. **Build the mpss reader** (Doc 153 §9 item 1) — still the top *instrument*.

## 11. Artifacts

| what | where |
| :-- | :-- |
| patch 811 (telemetry), tracked | `msm89xx/patches/811-bam-dmux-deferred-tx-telemetry.patch` |
| patch 812 (the fix), tracked | `msm89xx/patches/812-bam-dmux-preserve-deferred-tx.patch` |
| pre-811 / post-811 / fixed source | `evidence/156_deferred_tx_loss/qcom_bam_dmux.c.{pre811,post811,fixed}` |
| A/B transcripts | `evidence/156_deferred_tx_loss/ab_{before,after}_fix.txt` |
| DNS-after-idle transcript | `evidence/156_deferred_tx_loss/dns_after_idle_after_fix.txt` |
| fatal at 424 s with the fix deployed | `evidence/156_deferred_tx_loss/fatal_at_424s_with_patch812.txt` |
| test harness (idle → one ping) | `evidence/156_deferred_tx_loss/defertest.sh` |
| soak harness (fix verification) | `evidence/156_deferred_tx_loss/soak812.sh` |
| instrumented, unfixed module | `26014c0e78f89455904ae7e0db7d017c`, 236128 B → `/overlay/modbackup/qcom_bam_dmux.ko.p811` |
| fixed module | `68dcc4ace88a1729d6081569c72c6383`, 237568 B → `/lib/modules/6.12.94/qcom_bam_dmux.ko` |
| pre-810 module (for reference) | `/overlay/modbackup/qcom_bam_dmux.ko.p810counter` (`c74596349219b4b22bfe32caef79bbba`) |
| soak data (on device) | `/overlay/soak812.csv`, `/overlay/soak812.log` |

## 12. One line

`pm_runtime_get()` is **async**, so the first packet after every A2 power collapse always takes
`start_xmit`'s defer branch — and `bam_dmux_pm_restart()`, running on the wake that packet was waiting
for, unconditionally cleared `tx_deferred_skb` and freed the skb, destroying it with **no crash and no
counter** (measured 27/27, 60 % of all TX in the first minute of a boot, and 3/3 single-packet pings);
**patch 812** makes the wake preserve deferred slots and their DMA mappings so the existing drain
submits them, turning 3/3 loss into **5/5 delivery**, and patch 811's `tx_defer_wiped_live` counter is
what made the invisible visible.
