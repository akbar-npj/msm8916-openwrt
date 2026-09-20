# BAM-DMUX Differential Analysis: Stock Android vs OpenWrt + Patch 808

## Executive Summary

A line-by-line comparison of three BAM-DMUX implementations:

1. **Android stock** (`bam_dmux.c`, 2798 lines) — QCOM kernel 3.x, uses SPS + SMSM
2. **OpenWrt vanilla** (`qcom_bam_dmux.c`, 910 lines) — Stephan Gerhold's upstream driver, uses DMA engine + SMEM state
3. **OpenWrt patched** (vanilla + patch 808, 1823 lines) — our additions: SSR, RX state machine, telemetry

**Key finding: the Android driver has a polling-mode RX fallback that OpenWrt completely lacks.** When a DMA interrupt is missed during a power-collapse transition, the Android driver recovers by polling; the OpenWrt driver stalls permanently. This is the most likely AP-side root cause of the 900-second data stall.

---

## 1. Architecture Comparison

| Feature | Android Stock | OpenWrt Vanilla | OpenWrt + Patch 808 |
|---|---|---|---|
| Transport | SPS (`sps_transfer_one`) | DMA engine (`dmaengine_prep_slave_single`) | Same as vanilla |
| Power control | SMSM (`smsm_change_state`) | SMEM state + IRQ (`qcom_smem_state_update_bits`) | Same as vanilla |
| SSR | `subsys_notif_register_notifier` + `subsystem_restart()` | **None** | `qcom_register_ssr_notifier` (reactive only) |
| RX mode | Dual: interrupt + polling fallback | Interrupt only | Interrupt only |
| TX path | Linked-list pool + SPS EOT | 32-slot ring + DMA callback | Same + retry work |
| UL timeout | 1000ms auto-powerdown | pm_runtime autosuspend | pm_runtime autosuspend (blocked when iface up) |
| Clock mgmt | DFAB + XO clock voting | **None** | **None** |
| MTU negotiation | Dynamic (2K-16K) | Fixed 2K | Fixed 2K |
| Wake locks | Yes (`wake_lock`) | No (uses pm_runtime) | No (uses pm_runtime) |

---

## 2. CRITICAL: Polling-Mode RX Fallback (Android only)

### What Android does

The Android driver operates in two RX modes:

**Interrupt mode** (default): The SPS EOT event fires `bam_mux_rx_notify()`, which **immediately switches to polling mode**:

```c
// bam_dmux.c:1467-1510
static void bam_mux_rx_notify(struct sps_event_notify *notify)
{
    case SPS_EVENT_EOT:
        if (!polling_mode) {
            // Disable interrupts, enable polling
            cur_rx_conn.options = SPS_O_AUTO_ENABLE |
                SPS_O_ACK_TRANSFERS | SPS_O_POLL;
            sps_set_config_ptr(bam_rx_pipe, &cur_rx_conn);
            grab_wakelock();
            polling_mode = 1;
            queue_work_on(0, bam_mux_rx_workqueue, &rx_timer_work);
        }
        break;
}
```

**Polling mode** (`rx_timer_work_func`): Polls `sps_get_iovec` in a loop with `usleep_range(2950, 3050)`. When no data arrives for `POLLING_INACTIVITY` (1) cycles, calls `rx_switch_to_interrupt_mode()` which re-enables interrupts and releases the wakelock.

This means: **every RX interrupt triggers a burst of polling** that drains all pending data before returning to interrupt mode. This is immune to missed interrupts.

### What OpenWrt does

Pure interrupt-driven via DMA callback:

```c
// qcom_bam_dmux.c (patched)
static void bam_dmux_rx_callback(void *data)
{
    // Process the completed buffer
    // Immediately resubmit this slot
    if (active) {
        if (bam_dmux_rx_slot_submit(skb_dma, GFP_ATOMIC, false))
            dma_async_issue_pending(dmux->rx);
    }
}
```

**No polling fallback.** If a DMA completion interrupt is lost (e.g., during a power-collapse transition when the BAM hardware is being torn down and rebuilt), the RX ring drains to empty and stays empty. The modem has no buffers to write to, and the data path stalls permanently.

### Why this explains the 900s stall

- The modem does ~1.88 MCPM sleep/wake cycles per second (916 cycles in 808-1288s)
- Each cycle is a power-collapse transition where the BAM is torn down and rebuilt
- On each transition, there is a small probability that a DMA completion interrupt is lost
- After ~900s (~1690 cycles), the cumulative probability of a missed interrupt approaches certainty
- Once a cycle misses, the RX ring stays empty, and data stalls
- The modem keeps running (it has its own buffers and continues MCPM cycles), but the AP-side DMA ring is drained

---

## 3. UL Timeout / Power-Down Behavior

### Android: Active power-down after 1s idle

```c
// bam_dmux.c:1776-1817
static void ul_timeout(struct work_struct *work)
{
    if (bam_is_connected) {
        if (ul_packet_written || atomic_read(&ul_ondemand_vote)) {
            ul_packet_written = 0;
            schedule_delayed_work(&ul_timeout_work,
                    msecs_to_jiffies(UL_TIMEOUT_DELAY)); // 1000ms
        } else {
            ul_powerdown();  // Vote A2 power off
        }
    }
}
```

The Android driver **powers down the UL path every 1 second of inactivity**, even when the network interface is open. This creates a clean sleep/wake cycle synchronized with the modem.

### OpenWrt + Patch 808: Suspend blocked when interface is up

```c
// Patch 808, bam_dmux_runtime_suspend:
/* Guard 0: Abort suspend if any network interface is active / running */
for (i = 0; i < BAM_DMUX_NUM_CH; i++) {
    if (dmux->netdevs[i] && (dmux->netdevs[i]->flags & IFF_UP))
        return -EBUSY;
}
```

**This guard is commented "Stock Android parity" but is the OPPOSITE of what Android does.** Android allows UL power-down regardless of interface state. This guard prevents pm_runtime from ever suspending while the interface is up, which means:

1. `bam_dmux_pc_vote(dmux, false)` is never called
2. The SMEM power-collapse vote is never cleared
3. The modem sees the AP always voting "power on" for A2
4. The A2 power-collapse handshake is never exercised
5. The BAM is never torn down and rebuilt during normal operation

**However**: the `pc_irq` still fires on modem-initiated power state changes, and `power_off`/`power_on` are still called from the IRQ handler. The issue is that the AP-side vote is stuck at "on", so the modem's view of the AP's power intent is incorrect.

### Fix direction

Remove the `IFF_UP` guard from `bam_dmux_runtime_suspend`. The pm_runtime autosuspend (1000ms delay) will then properly vote power off after 1s of inactivity, matching the Android `ul_timeout` behavior. The other guards (deferred TX, TX in-flight, RX callbacks) are sufficient for safety.

---

## 4. A2 Power Collapse Disabled (CMD_OPEN_NO_A2_PC)

### Android

When the modem sends `BAM_MUX_HDR_CMD_OPEN_NO_A2_PC` (cmd 4):

```c
// bam_dmux.c:698-709
case BAM_MUX_HDR_CMD_OPEN_NO_A2_PC:
    if (!a2_pc_disabled) {
        a2_pc_disabled = 1;
        ul_wakeup();  // Vote DFAB clock, keep BAM powered
    }
    handle_bam_mux_cmd_open(rx_hdr);
    break;
```

The Android driver:
1. Sets `a2_pc_disabled = 1`
2. Calls `ul_wakeup()` which votes DFAB clock (`clk_prepare_enable(dfab_clk)`) and XO clock
3. Skips the SMSM power vote/ack handshake entirely
4. The BAM stays powered by the DFAB clock, bypassing power collapse

The `ul_timeout` still fires but takes the `a2_pc_disabled` path:
```c
// bam_dmux.c:1697-1700
if (a2_pc_disabled) {
    wait_for_dfab = 1;
    release_wakelock();
}
```

### OpenWrt + Patch 808

```c
// Patch 808
case BAM_DMUX_CMD_OPEN_NO_A2_PC:
    dmux->a2_pc_disabled = true;
    bam_dmux_cmd_open(dmux, hdr);
    break;
```

The OpenWrt driver:
1. Sets `a2_pc_disabled = true`
2. Calls `bam_dmux_cmd_open` (register netdev)
3. **Does NOT vote any clock** (no DFAB, no XO)
4. The `a2_pc_disabled` flag is only checked in `runtime_suspend` where it returns `-EBUSY`

**Problem**: Without DFAB clock voting, the A2 BAM hardware may lose its clock source when the clock framework turns off unused clocks. On Android, `vote_dfab()` keeps the clock on. On OpenWrt, nothing keeps the clock on.

### Fix direction

Add DFAB/XO clock management to the OpenWrt driver. The DT binding should reference the `bus_clk` and `xo` clocks, and the driver should `clk_prepare_enable` them during `power_on` and `clk_disable_unprepare` during `power_off`. This is a significant addition but is essential for correct A2 power-collapse-disabled operation.

---

## 5. SSR Self-Healing

### Android

```c
// bam_dmux:1819-1838
static int ssrestart_check(void)
{
    in_global_reset = 1;
    ret = subsystem_restart("modem");
    if (ret == -ENODEV)
        panic("modem subsystem restart failed\n");
    return 1;
}
```

When a fatal condition is detected (timeout, hardware error), Android **triggers a modem subsystem restart**. The `restart_notifier_cb` handles cleanup (flush TX pool, disconnect BAM, clear channel states) before and after.

### OpenWrt + Patch 808

```c
// Patch 808
static int bam_dmux_ssr_notifier_cb(struct notifier_block *this,
                                    unsigned long code, void *data)
{
    switch (code) {
    case QCOM_SSR_BEFORE_SHUTDOWN:
        schedule_work(&dmux->ssr_teardown_work);
        break;
    case QCOM_SSR_AFTER_POWERUP:
        schedule_work(&dmux->ssr_powerup_work);
        break;
    }
    return NOTIFY_OK;
}
```

The OpenWrt driver **only reacts** to externally triggered SSR events. It **never triggers a restart itself**. If a fatal condition is detected (timeout in `bam_dmux_runtime_resume`), it just logs a warning:

```c
if (!ret) {
    atomic64_inc(&dmux->pc_timeout_count);
    dev_warn(dev, "bam_dmux: modem pc-ack timeout during resume\n");
}
```

No `subsystem_restart()`, no panic, no recovery. The driver continues in a potentially broken state.

### Fix direction

Consider adding a watchdog mechanism that triggers SSR after N consecutive timeout failures. This would require `qcom_ssr_notify("mpss")` or equivalent.

---

## 6. RX Buffer Replenishment Strategy

### Android

```c
// bam_dmux.c:474-482
static void queue_rx(void)
{
    __queue_rx(GFP_NOWAIT | __GFP_NOWARN);  // Hot path
}

static void __queue_rx(gfp_t alloc_flags)
{
    while (bam_connection_is_active && rx_len_cached < num_buffers) {
        // Allocate, DMA map, submit to SPS
        // On failure: schedule_work(&queue_rx_work) for GFP_KERNEL retry
    }
}
```

`queue_rx()` is called after **every** RX packet is processed. It fills the pool to `num_buffers` (32). If allocation fails (GFP_NOWAIT), it schedules `queue_rx_work` which retries with GFP_KERNEL. The pool is a **dynamically growing linked list**.

### OpenWrt + Patch 808

RX callback does immediate per-slot replenishment:
```c
if (active) {
    if (bam_dmux_rx_slot_submit(skb_dma, GFP_ATOMIC, false))
        dma_async_issue_pending(dmux->rx);
} else {
    // Set to FREE — slot sits idle until rx_rearm_work or power_on
}
```

Plus a periodic rearm work:
```c
static void bam_dmux_rx_rearm_work_func(struct work_struct *work)
{
    // Re-arm FREE slots every 2000ms
    for (i = 0; i < BAM_DMUX_NUM_SKB; i++) {
        if (skb_dma->rx_state == BAM_DMUX_RX_SLOT_FREE)
            bam_dmux_rx_slot_submit(skb_dma, GFP_ATOMIC, true);
    }
    schedule_delayed_work(&dmux->rx_rearm_work, msecs_to_jiffies(2000));
}
```

**Problem**: If `active` is false during an RX callback (e.g., `pc_state` transiently false during a power transition), the slot goes to FREE and stays there for up to 2 seconds. With 32 slots and ~1.88 power cycles/s, a burst of transitions can drain multiple slots to FREE faster than the 2-second rearm can refill them. If all 32 go FREE, the modem has no RX buffers and data stalls.

### Fix direction

1. Reduce `rx_rearm_work` interval from 2000ms to 200ms
2. Or better: add a `queue_rx_work` equivalent that retries immediately with GFP_KERNEL
3. Or best: ensure `pc_state` never goes false during normal RX callbacks (only during SSR)

---

## 7. Full Teardown Per Power Cycle

### Android

During normal power-collapse cycles (not SSR), the Android driver does **NOT** tear down the SPS connection. It only votes/unvotes power via SMSM:

```c
// ul_powerdown(): just votes power off, BAM stays connected
power_vote(0);
bam_is_connected = 0;

// ul_wakeup(): just votes power on, BAM stays connected
power_vote(1);
// Wait for ack
```

The SPS pipes, descriptor memory, and event registrations all stay alive. Only `bam_dmux_smsm_cb` (modem-initiated) calls `reconnect_to_bam()` / `disconnect_to_bam()`, and even those only disconnect/reconnect the SPS pipes, not the BAM device itself.

### OpenWrt + Patch 808

Every `power_off` call **fully tears down** the BAM connection:

```c
static void bam_dmux_power_off(struct bam_dmux *dmux)
{
    // Set rx_tearing_down
    // Cancel rearm work
    // Wait for submitters
    // Terminate and RELEASE RX DMA channel
    // Wait for RX callbacks
    // Terminate and RELEASE TX DMA channel
    // Reset all RX slot states to FREE
    // Free TX skbs
    // Clear remote channels
}
```

And `power_on` re-requests the channels:
```c
static bool bam_dmux_power_on(struct bam_dmux *dmux)
{
    dmux->rx = dma_request_chan(dev, "rx");  // Re-request!
    dmux->tx = dma_request_chan(dev, "tx");  // Re-request!
    // Re-submit all RX buffers
}
```

**Problem**: Releasing and re-requesting DMA channels on every power-collapse cycle is expensive and error-prone. If `dma_request_chan` fails on any cycle, the driver is stuck. The Android driver never releases the SPS connection during normal operation.

### Fix direction

Separate "power vote" from "channel teardown":
- `runtime_suspend`: vote power off (SMEM state), do NOT release DMA channels
- `runtime_resume`: vote power on (SMEM state), do NOT re-request DMA channels
- Only release/re-request channels during SSR (when the modem actually restarts)
- This matches the Android behavior where `ul_powerdown`/`ul_wakeup` just vote, and `disconnect_to_bam`/`reconnect_to_bam` (SSR-only) handle channel teardown

---

## 8. Summary of Differences and Fix Priorities

| # | Difference | Impact | Priority |
|---|---|---|---|
| 1 | No polling-mode RX fallback | **Root cause of 900s stall** — missed interrupt = permanent stall | **P0** |
| 2 | `IFF_UP` guard blocks runtime suspend | AP never votes power off; modem's A2 PC handshake never exercised | **P1** |
| 3 | Full DMA channel teardown per power cycle | Expensive; `dma_request_chan` failure = stuck | **P1** |
| 4 | No DFAB/XO clock management | A2 BAM may lose clock; `OPEN_NO_A2_PC` not properly handled | **P2** |
| 5 | No SSR self-healing | Fatal conditions not recovered | **P2** |
| 6 | RX rearm interval 2000ms | Slots can drain faster than rearm during power transitions | **P2** |
| 7 | No MTU negotiation | Minor — fixed 2K works but may cause fragmentation | **P3** |

---

## 9. Recommended Fix Sequence

### Phase 1: Stop the stall (P0 + P1)

1. **Remove the `IFF_UP` guard** from `bam_dmux_runtime_suspend` — allow pm_runtime to suspend and vote power off after 1s idle, matching Android's `ul_timeout` behavior.

2. **Separate power-vote from channel-teardown** in `bam_dmux_power_off`/`power_on`:
   - `runtime_suspend`: call `bam_dmux_pc_vote(dmux, false)` only, keep DMA channels
   - `runtime_resume`: call `bam_dmux_pc_vote(dmux, true)` only, keep DMA channels
   - `ssr_teardown`: full teardown (release channels) — only during SSR
   - `ssr_powerup`: full rebuild (request channels) — only during SSR

3. **Add a polling fallback** or equivalent interrupt-recovery mechanism:
   - Option A: After each `rx_callback`, check if the DMA ring is fully drained and trigger an immediate rearm
   - Option B: Reduce `rx_rearm_work` interval to 200ms and make it check for drained rings
   - Option C: Add a timer that fires 100ms after the last RX callback; if no new callback, poll the DMA ring

### Phase 2: Correctness (P2)

4. **Add DFAB/XO clock management** — request `bus_clk` and `xo` in probe, enable on `power_on`, disable on `power_off`.

5. **Add timeout-triggered SSR** — after N consecutive `pc_ack` timeouts, trigger modem restart via `qcom_ssr_notify` or equivalent.

### Phase 3: Optimization (P3)

6. **Add MTU negotiation** — parse `DYNAMIC_MTU_MASK` and `MTU_SIZE_MASK` from open commands.
7. **Add watermark flow control** — `HIGH_WATERMARK`/`LOW_WATERMARK` on TX.

---

## 10. Code Reference Map

| Component | Android Stock | OpenWrt Vanilla | Patch 808 |
|---|---|---|---|
| Driver | `GitIgnore/.../bam_dmux.c` | `scratch/orig_kernel/.../qcom_bam_dmux.c` | `msm89xx/patches/808-bam-dmux-stats.patch` |
| Private HDR | `GitIgnore/.../bam_dmux_private.h` | (inline) | (inline) |
| Public HDR | `GitIgnore/.../include/soc/qcom/bam_dmux.h` | (inline) | (inline) |
| Built | (prebuilt on device) | `openwrt/build_dir/.../qcom_bam_dmux.c` (1823 lines) | Applied at build time |
| Patch | N/A | N/A | `msm89xx/patches/808-bam-dmux-stats.patch` |
