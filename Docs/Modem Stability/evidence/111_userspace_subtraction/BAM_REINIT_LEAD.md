# THE BAM-REINIT LEAD — why only `a2_power` fatals print "successfully reinitialized"

**Opened:** 2026-09-22, from a side observation in Task #111 (commit `b3d3120`).
**Status:** mechanism read from source; **the discriminating measurement is being captured on boot `59d9c272`.**
**Source:** `openwrt/.../drivers/net/wwan/qcom_bam_dmux.c` (live tree; tracked patch of record is
`msm89xx/patches/814-bam-dmux-ssr-powerup-retry.patch`).

---

## 1. The observation

In boot `eafa19f6` (17 fatals), the line

```
bam_dmux: SSR powerup: successfully reinitialized BAM channels and rings
```

appeared **exactly four times** — at 525.323701, 5950.318477, 8720.129599, 12363.367529 — i.e. **after
the four `a2_power.c:1189` fatals (524.21, 5949.21, 8719.02, 12362.26) and after NONE of the thirteen
`lte_ml1_common_timer.c:390` fatals.**

## 2. The mechanism, read from source

`bam_dmux_ssr_powerup_work_func()` (`:2336`) runs a fixed sequence:

1. `flush_work(&ssr_teardown_work)`, clear `in_teardown`;
2. `msleep(200)`;
3. **poll the modem's pc line for up to 150 × 20 ms = 3000 ms** (`:2345-2350`);
4. **`dev_info("SSR powerup: modem pc_state=%d (waited %d ms)")` — UNCONDITIONAL** (`:2352`);
5. if `!pc_line`: retry up to `BAM_DMUX_SSR_POWERUP_MAX_RETRIES` = **20** times, 500 ms apart (`:49-50`,
   `:2376-2385`), each retry re-running the whole function *including the 3 s poll*, so the total budget
   for the modem to assert is **≈ 20 × 3.5 s ≈ 77 s** (the comment at `:50` says "~77 s of extra budget");
6. on giving up: `dev_err("... giving up")` and re-arm the **RX watchdog** as the unbounded safety net
   (`:2387-2397`);
7. if asserted **and `dmux->rx == NULL`**: `bam_dmux_power_on()` → **"successfully reinitialized"** (`:2409-2414`);
8. if asserted **and `dmux->rx != NULL`**: `dev_info("... channels already active")` (`:2420-2422`).

### So there are exactly three outcomes per powerup, and only one prints "successfully reinitialized"

| outcome | condition | message |
| :-- | :-- | :-- |
| A | pc line asserted within 77 s, `rx == NULL` | `successfully reinitialized BAM channels and rings` |
| B | pc line asserted within 77 s, `rx != NULL` | `channels already active` |
| C | pc line NOT asserted within 77 s | `not asserted, retry n/20` … `giving up` |

**Cold boot `59d9c272` confirms the message family is emitted:** `modem pc_state=1 (waited 580 ms)` then
`channels already active` — outcome **B**, because probe-time `bam_dmux_power_on()` (`:2594`) had already
allocated the channels.

## 3. Why this matters — and the hypothesis

After an SSR the teardown releases the channels (`T5 rx released`, `T6 tx released`), so `rx == NULL`
should hold at the powerup and **every** fatal should be outcome **A**. Only 4 of 17 were.

**Hypothesis (not yet tested):** the thirteen `common_timer` fatals are outcome **B**, not C — i.e. the
modem asserted its pc line *early* and the **interrupt path** (`bam_dmux_pc_irq()` `:1945` →
`bam_dmux_power_on()`) rebuilt the channels *before* the polling powerup work got to them, so the poll
found `rx != NULL`. The four `a2_power` fatals are the ones where that **edge was lost**, leaving only the
polling path to do the rebuild.

This would tie the lead directly to **Doc 170 §8.15**, which found that every `a2_power.c:2949` fatal is
preceded by an AP-side **"lost edge" resync** 74–86 ms earlier (3/3) — i.e. `a2_power` fatals are
already known to be the signature associated with lost pc-line edges. Boot `eafa19f6` had
`pc_resync_count: 0`, so the storm was absent, but the *edge* loss need not be the storm.

**The alternative reading** is that the thirteen are outcome **C** — the modem genuinely does not assert
within 77 s, and recovery comes later via the RX watchdog or a late edge. Under that reading, `a2_power`
fatals are the *fast-recovering* ones and `common_timer` fatals leave the modem down for >77 s, which
would be a direct contributor to the user-visible data stall.

**These two readings are separated by one line per fatal.**

## 4. The measurement that separates them

`SSR powerup: modem pc_state=%d (waited %d ms)` is printed **unconditionally** at `:2352`, so one line per
powerup:

* `pc_state=1` + `channels already active` → reading 1 (early edge, interrupt rebuilt);
* `pc_state=1` + `successfully reinitialized` → outcome A (a2_power only);
* `pc_state=0 (waited 3200 ms)` + `giving up` → reading 2 (the modem is genuinely late).

**Being captured now** by `scratch/powerup_watch.sh` → `scratch/powerup_watch.txt`, which appends only
new matching lines every 15 s, host-side, so a reboot cannot lose it.

## 5. A process lesson worth recording

**The old boot's `dmesg` was lost by rebooting before capturing it.** The earlier grep used the pattern
`reinitialized|power_on|rearm|re-arm|not initialized|refusing`, which **missed**
`SSR powerup: modem pc_state=%d` — the one line that carries the answer. So a signature-linked lead was
reduced from a measurement to a source-reading hypothesis by an incomplete grep followed by a reboot.

**Rule: before rebooting the device, dump the full `dmesg` to the host, and grep for the *message family*
(the format string's stable prefix), not for the conclusions you expect.**
