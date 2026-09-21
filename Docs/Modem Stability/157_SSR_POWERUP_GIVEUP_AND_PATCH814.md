# 157 — THIRD AP-SIDE DEFECT: the SSR powerup work gave up permanently, so a modem that was awake was never re-opened. Patch 814.

**Date:** 2026-09-21. Times are **AP `/proc/uptime` seconds** (the device RTC is unreliable). Wall-clock
syslog lines are converted to kernel time where both exist, and the conversion is stated.

**Status:** **root cause found, mechanism source-verified and measured on the live device, fix written,
built, deployed and running, and both recovery paths proven.** Soak run 1 confirms the **normal** SSR
path is intact with the patch deployed (§7.4); run 3 proves the retry rebuilds the channels **with the
assert edge deliberately dropped**, i.e. in exactly the failure mode the patch exists for (§7.5); and
run 6 shows the **watchdog rebuild firing naturally, twice** (§7.6). The only thing still missing is a
*natural* occurrence of the **retry's** trigger (a modem slower than 3.2 s) — every modem in run 6 was
ready in 540–580 ms. Soak run 7 is watching for one.

Source changed in the **tracked** tree (`msm89xx/patches/814-bam-dmux-ssr-powerup-retry.patch`); the
deployed `/lib/modules/6.12.94/qcom_bam_dmux.ko` was replaced (md5 `eca269f1…`, previous build backed
up to `/overlay/modbackup/qcom_bam_dmux.ko.p812`).

**Line numbers below are from the fixed file**, `qcom_bam_dmux.c.post814` in the evidence directory.
Where a number differs in the pre-fix file it is given as `(was :NNN)`.

---

## 1. Why this doc exists

The user-visible symptom that started this was **"the link is up, the modem is registered, but there is
no default route and `ping` says Network unreachable"**. That reads like a modem or bearer problem. It
is not. It is the third independent defect found in the AP-side `qcom_bam_dmux` driver, and it is the
one that explains the end state Doc 154 §6 recorded but could not explain.

Doc 154 §6 found the same *end state* — `wwan*` interfaces DOWN, `ping` → Network unreachable, a reboot
required — and attributed it to the modem PIL boot hanging inside `q6v5_mpss_load()`, because the
console stopped right after `MBA booted without debug policy, loading mpss` and `remoteproc0/state`
read `offline`. **That is a different failure.** In the incident documented here the modem booted
cleanly, `remoteproc0/state` was `running`, the modem was registered, attached and had 88 % signal —
and the data plane was still dead, because the *driver* had stopped trying.

## 2. SOP compliance

| SOP element | this session |
| :-- | :-- |
| Dual-firmware comparative protocol | **N/A** — no firmware built, patched or transplanted. The deployed baseband is verified clean stock HMU05 (Doc 153 §6b); the module md5 was checked before and after deployment. |
| Ground truth from source, not narrative | **Applied, and it is what produced the answer.** The corpus had the *symptom* (memory quirk #9: "`wwan0` gets an IP but no default route … will be misread as a modem data-plane failure") but attributed it to `network.modem.auto` being unset. `auto` is set now and the symptom returned, so the corpus explanation was insufficient and the driver source had to be read. |
| Read the definition, not the name | **Applied.** Three separate traps in this one chain: (a) `bam_dmux_runtime_resume()` **returns 0** on the missing-channels path, so it *looks* like a successful resume; (b) the message "RX watchdog: … (lost edge), resyncing" is reached only when `dmux->rx && dmux->tx` — the one state it cannot help in; (c) `irq_get_irqchip_state(IRQCHIP_STATE_LINE_LEVEL)` on the SMSM irqchip reads the **remote SMEM state word**, not a GPIO level (`smsm.c:330-331`). |
| Check the call sites, not just the body | **Applied.** `bam_dmux_netdev_open()` (`:545`) calls the **synchronous** `pm_runtime_resume_and_get()` and then `bam_dmux_send_cmd()`; that pair is what turns the driver's silent success into a failed `ndo_open` and a DOWN interface. |
| Measure on the live device, before and after | **Applied.** The stuck state was measured exhaustively (§5); the fix is deployed and the post-fix baseline verified (§7.2); the normal path survived a real fatal-triggered SSR (§7.4); and the new retry path was driven on the live device by a deliberately crippled test build (§7.5). What has *not* happened is a natural occurrence of the trigger — this doc says so rather than implying otherwise. |
| Minimal, reversible change | Yes — patch 814 is one retry loop, one widened condition, and two schedule calls. The previous module is on the device as `qcom_bam_dmux.ko.p812`. The run-3 test build was temporary, is preserved as a patch for reproducibility, and was reverted to a byte-identical production module (§7.5). |
| Record what was done, the result, and what is next | §4–§7 mechanism, §5 measurement, §6 fix, §8 scope and next. |
| Never classify a fault by its `file:line` | **Applied.** See §7.3 — the three fatals in this boot carry two different signatures. |

## 3. How it was found

The soak harness had been restarted after the Doc 156 work and was reporting **`tx_pkts` frozen**.
The harness itself was alive and its burst generator was alive, so the first suspicion was the
harness. Running the traffic by hand gave the real answer:

```
# ping -c 1 -W 2 8.8.8.8
ping: sendto: Network unreachable
# ip route
192.168.8.0/24 dev br-lan proto kernel scope link src 192.168.8.1     <- no default
# ip addr show wwan0
    inet 10.89.144.121/30 brd 10.89.144.123 scope global wwan0
```

`wwan0` had an address but was **administratively DOWN**:

```
2: wwan0: <POINTOPOINT,NOARP> mtu 1500 qdisc fq_codel state DOWN ...
```

That is the whole trick of this defect. For IPv4 the kernel **deletes every route on a device when the
device goes down** (`fib_disable_ip()`), so the missing default route is a *symptom of the interface
being down*, not a routing problem. netifd had in fact installed the route successfully moments
earlier:

```
22:40:20 netifd: modem (2948): adding default IPv4 route via 10.89.144.122
22:40:21 netifd: Interface 'modem' is now up
```

and `ifstatus modem` still reported `"up": true` with `"updated": ["addresses","routes"]`. netifd
believed the interface was up; the kernel disagreed. So the question became *why is `wwan0` down*,
and the answer was in the driver, not in the network configuration.

## 4. The mechanism

### 4.1 The timeline of the failing SSR

All lines are the kernel's own timestamps except where marked.

| t (AP s) | event |
| --: | :-- |
| 1405.636209 | `fatal error received: a2_power.c:1189:` — crash #3 |
| 1405.651338 | `remoteproc0: recovering 4080000.remoteproc` |
| 1405.658167 | `bam_dmux: SSR before shutdown: scheduling teardown work` |
| 1405.669902 | `wwan wwan0: port wwan0at0 disconnected` |
| 1406.018703 | `bam_dmux: modem pc-ack timeout during resume` |
| 1407.032131 | `bam_dmux: modem pc_state wait timeout during resume` |
| 1407.032232 | `bam_dmux: channels not initialized after resume` |
| 1407.197927 | `bam_dmux: SSR after powerup: scheduling powerup work` |
| 1407.199457 | `remoteproc0: remote processor 4080000.remoteproc is now up` |
| **1410.918738** | **`bam_dmux: SSR powerup: modem pc_state=0 (waited 3200 ms)`** |
| **1410.918806** | **`bam_dmux: SSR powerup: modem pc line not asserted, deferring`** |
| 1413.566878 | `wwan wwan0: port wwan0at0 attached` ← the modem finished booting here |
| 1413.567946 | `wwan wwan0: port wwan0at1 attached` |
| 1413.763247 | `wwan wwan0: port wwan0qmi0 attached` |
| ~1437.9 (wall 22:40:20) | netifd: `adding default IPv4 route via 10.89.144.122` |
| 1437.913064 | `bam_dmux: modem pc-ack timeout during resume` |
| 1438.926599 | `bam_dmux: modem pc_state wait timeout during resume` |
| 1438.926817 | `bam_dmux: channels not initialized after resume` |
| ~1438.9 (wall 22:40:21) | netifd: `Interface 'modem' is now up` — but the kernel left `wwan0` DOWN |
| 1731.331143 | (my own `ip link set wwan0 up`) `pc_state wait timeout` + `channels not initialized` — **still stuck, 5 minutes later** |

**The modem took ~6.4 s to become ready** (teardown 1405.70 → ports attached 1413.57), because a fatal
error forces a full firmware reload. The powerup work's poll budget is **3.2 s**, so it expired while
the modem was still booting — 2.65 s before the modem attached its ports.

The wall↔kernel mapping above is anchored on the two pairs that share a timestamp: the kernel messages
at 1437.913/1438.926 and the netifd messages at 22:40:20/22:40:21, which the dropbear log then
confirms (my first ssh connection, wall 22:42:33, is kernel ~1571 — matching `/proc/uptime` 1571.11).

### 4.2 Defect 1 (the primary one): the defer is a permanent give-up

`bam_dmux_ssr_powerup_work_func()` (was `:2228-2231`):

```c
	if (!pc_line) {
		dev_warn(dmux->dev, "bam_dmux: SSR powerup: modem pc line not asserted, deferring\n");
		return;                    /* <- no retry, no reschedule, ever */
	}
```

That `return` is the whole bug. The name says "deferring"; the code says "abandoning". Nothing in the
driver re-runs this work except a *new* `QCOM_SSR_AFTER_POWERUP`, and a new SSR is exactly what the
system is trying not to have.

### 4.3 Defect 2: `bam_dmux_runtime_resume()` reports success with no channels

`bam_dmux_runtime_resume()` (`:2005-2012`):

```c
	/* Verify channels were initialized under state_lock */
	mutex_lock(&dmux->state_lock);
	if (!dmux->rx || !dmux->tx) {
		dev_warn(dev, "bam_dmux: channels not initialized after resume\n");
		mutex_unlock(&dmux->state_lock);
		bam_dmux_record_resume_time(dmux, t_start_ns);
		return 0;                  /* <- success, with dmux->rx == NULL */
	}
```

It logs a warning and then **returns success**. The consequence is one call away —
`bam_dmux_netdev_open()` (`:545-553`):

```c
	ret = pm_runtime_resume_and_get(bndev->dmux->dev);   /* succeeds */
	if (ret < 0)
		return ret;
	ret = bam_dmux_send_cmd(bndev, BAM_DMUX_CMD_OPEN);   /* fails */
	if (ret) {
		pm_runtime_put(bndev->dmux->dev);
		return ret;                                       /* ndo_open fails */
	}
```

and `bam_dmux_send_cmd()` refuses when the modem is collapsed (`:500-505`):

```c
	if (!READ_ONCE(dmux->pc_state)) {
		dev_warn_once(dmux->dev, "bam_dmux: refusing to queue command while modem is collapsed\n");
		ret = -EAGAIN;
		goto put_skb;
	}
```

So `ndo_open` returns `-EAGAIN`, the kernel leaves the interface DOWN, and deletes its routes.

**Measured directly** on the stuck device:

```
# ip link set wwan0 up
RTNETLINK answers: Resource temporarily unavailable      (rc=2, EAGAIN)
# ip link show wwan0
2: wwan0: <POINTOPOINT,NOARP> mtu 1500 ... state DOWN
```

### 4.4 Defect 3: the lost-edge resync cannot fire in the state it exists for

The RX watchdog already contained a resync for exactly the "the wire says awake, the driver says
collapsed" case — but it was gated on the channels existing:

```c
			if (!READ_ONCE(dmux->pc_state) &&
			    !READ_ONCE(dmux->in_teardown) &&
			    dmux->rx && dmux->tx &&              /* <- was :1302 */
			    bam_dmux_pc_line_asserted(dmux)) {
				... bam_dmux_pm_restart(dmux); ...
```

`bam_dmux_pm_restart()` re-requests the DMA channels, so it *looks* like it can rebuild — but it is
only reachable when the channels are already there. In the post-SSR state (`rx == tx == NULL`) the
condition is false, `resynced` stays false, and the function falls through to the quiesce logger and
reschedules. Measured: `pc_resync_count: 0` for the entire stuck period.

The two other paths that can rebuild the channels both require a **rising edge** on the pc IRQ
(`bam_dmux_pc_irq()` `:1864-1871`). So if the assert edge is ever missed, the driver is stuck with no
recovery route at all.

## 5. The measured stuck state

Captured verbatim in `evidence/157_ssr_powerup_giveup/stuck_state_capture.txt`. The load-bearing
lines:

```
pc_state: 0
pc_line_level: 1          <- the SMSM irqchip reads the MODEM's state word: it says it is AWAKE
pc_irq_count: 177
pc_resync_count: 0        <- the lost-edge resync never ran
rx_tearing_down: 1
rx_slots_mapped: 0        <- no BAM ring allocated
rx_slots_free: 32
cmd_open: 24              <- frozen; the modem never reopened a data channel
runtime_status: suspended
pm_usage_count: 0
```

and, from the same moment:

```
# cat /sys/class/remoteproc/remoteproc0/state
running                              <- the modem is NOT hung (contrast Doc 154 §6)

50:  177  smsm   1 Edge  4080000.remoteproc:bam-dmux     <- pc IRQ,  == pc_irq_count
51:  182  smsm  11 Edge  4080000.remoteproc:bam-dmux     <- pc-ack IRQ
34:  333  GIC-0 58 Edge  smsm                            <- the SMSM parent
```

**What is proven by this:** the modem was awake (`pc_line_level: 1`), the driver believed it was
collapsed (`pc_state: 0`), the channels were gone (`rx_slots_mapped: 0`), and nothing had retried
(`pc_resync_count: 0`, `cmd_open` frozen at 24). The modem was registered, attached and connected
throughout — only the AP-side BAM data plane was dead.

### 5.1 Why the assert edge was missed — an open question, with a candidate

`pc_line_level` is produced by `bam_dmux_pc_line_asserted()` → `irq_get_irqchip_state(…,
IRQCHIP_STATE_LINE_LEVEL, …)`, and the SMSM irqchip implements that as a read of the **remote
processor's SMEM state word** (`drivers/soc/qcom/smsm.c:330-331`). So `pc_line_level: 1` is a real
level read, not a latched pending bit — the modem genuinely had A2_POWER_CONTROL asserted.

The pc IRQ is delivered by SMSM, which is an **edge** notification whose cascade handler compares the
remote word against a cached `last_value` (`smsm.c:219-220`):

```c
	val = readl(entry->remote_state);
	changed = val ^ xchg(&entry->last_value, val);
```

so an interrupt is only generated for a bit that *changed since the cache was last refreshed*. The
cache is refreshed on `smsm_unmask_irq()` (`smsm.c:280-283`), which runs at the end of every
`handle_level_irq` for that child — including the `disable_irq()`/`enable_irq()` pair at the end of
`bam_dmux_ssr_teardown_work_func()`.

**A candidate desynchronisation exists and is our own code.** `bam_dmux_ssr_teardown_work_func()`
(`:2189-2191`) writes the modem's SMEM state word **directly**, bypassing SMSM entirely:

```c
	states = qcom_smem_get(QCOM_SMEM_HOST_ANY, 85, NULL);
	if (!IS_ERR_OR_NULL(states))
		states[1] &= ~BIT(1);
```

SMSM's `last_value` is not updated by that write. The design relies on the following
`disable_irq()`/`enable_irq()` to refresh it, which does happen (1407.03, before the modem asserted at
~1411-1413) — so **this does not explain the observed incident**, and the honest position is that the
missed edge is unexplained.

Two things follow, and they are why the fix is shaped the way it is:

1. The retry loop **polls the SMEM level** instead of waiting for an interrupt, so it recovers from a
   slow boot and from a lost edge alike, and does not depend on the answer to this question.
2. The direct SMEM write is a real latent hazard worth a targeted test (§8.3), not a proven cause.

## 6. The fix — patch 814

Three changes, in `msm89xx/patches/814-bam-dmux-ssr-powerup-retry.patch`.

### 6.1 Retry the powerup work (the primary fix)

`ssr_powerup_work` becomes a `struct delayed_work`, and the give-up becomes a bounded retry:

```c
	if (!pc_line) {
		if (dmux->ssr_powerup_retries++ < BAM_DMUX_SSR_POWERUP_MAX_RETRIES) {
			dev_warn(dmux->dev,
				 "bam_dmux: SSR powerup: modem pc line not asserted, retry %u/%u in %d ms\n",
				 dmux->ssr_powerup_retries, BAM_DMUX_SSR_POWERUP_MAX_RETRIES,
				 BAM_DMUX_SSR_POWERUP_RETRY_MS);
			schedule_delayed_work(&dmux->ssr_powerup_work,
					      msecs_to_jiffies(BAM_DMUX_SSR_POWERUP_RETRY_MS));
			return;
		}
		dev_err(dmux->dev, "bam_dmux: SSR powerup: modem pc line not asserted after %u retries, giving up\n",
			dmux->ssr_powerup_retries);
		schedule_delayed_work(&dmux->rx_watchdog_work, msecs_to_jiffies(100));
		return;
	}
	dmux->ssr_powerup_retries = 0;
```

with

```c
#define BAM_DMUX_SSR_POWERUP_RETRY_MS		500
#define BAM_DMUX_SSR_POWERUP_MAX_RETRIES	20	/* ~77 s of extra budget */
```

Each retry re-runs the whole function, so each costs 200 ms + up to 3000 ms of polling + 500 ms of
delay; the total budget is ≈ 3.2 s + 20 × 3.7 s ≈ **77 s**. The counter is reset on
`QCOM_SSR_BEFORE_SHUTDOWN` (`:2367`) so every SSR gets a fresh budget, and on a successful powerup.

In the failing boot a **single retry** would have been enough: the first attempt expired at 1410.92
and the modem's ports attached at 1413.57.

### 6.2 Rebuild the channels from the watchdog when they are missing

The resync condition no longer requires `dmux->rx && dmux->tx`; instead the branch splits:

```c
			if (!READ_ONCE(dmux->pc_state) &&
			    !READ_ONCE(dmux->in_teardown) &&
			    bam_dmux_pc_line_asserted(dmux)) {
				if (!dmux->rx || !dmux->tx) {
					/* modem awake but channels absent: rebuild */
					WRITE_ONCE(dmux->pc_state, true);
					if (bam_dmux_power_on(dmux)) { ... resynced = true; }
					else WRITE_ONCE(dmux->pc_state, false);
				} else {
					/* existing lost-edge path, unchanged */
					bam_dmux_pm_restart(dmux); ...
				}
			}
```

This needs a forward declaration of `bam_dmux_power_on()` next to the existing one for
`bam_dmux_pm_restart()` (`:1262`), because the watchdog is defined above it.

### 6.3 Arm the recovery paths from the resume path

`bam_dmux_runtime_resume()` now schedules both recovery mechanisms when it finds the channels missing
(`:2054-2071`). It deliberately **still returns 0**:

> returning an error would set `dev->power.runtime_error`, and `rpm_resume()` refuses every later
> resume while that is set (`if (dev->power.runtime_error) goto out;`). That would convert a transient
> condition into a permanent one, and nothing on this path calls `pm_runtime_set_active()` to clear
> it.

### 6.4 What the fix deliberately does not do

* It does **not** make `bam_dmux_runtime_resume()` return an error — see §6.3.
* It does **not** touch `bam_dmux_pm_restart()` or the patch-812 deferred-TX logic.
* It does **not** change the fatal cadence; patch 814 is **not** a stability fix (§7.3).
* It does **not** remove the direct SMEM write in the teardown work; that needs its own test (§8.3).

## 7. Verification

### 7.1 Patch chain

```
pre814 + 814 == the built source
md5 a8a3811f88d4cb19c0117d5ee188c4a8   (both sides)
```

`pre814` is `evidence/156_deferred_tx_loss/qcom_bam_dmux.c.fixed`, i.e. the post-812 file; the diff was
inspected hunk by hunk (11 hunks, all intended) before the patch was written. The module built with no
new warnings and was deployed as `eca269f10a685a83a8b679bc7be38d1d`.

### 7.2 Post-fix baseline (verified)

```
uptime 94.58 s   module eca269f10a685a83a8b679bc7be38d1d
default via 10.136.237.101 dev wwan0 proto static src 10.136.237.100 metric 10
wwan0: <POINTOPOINT,NOARP,UP,LOWER_UP> ... state UNKNOWN
2 packets transmitted, 2 received, 0% packet loss
rx_slots_mapped: 32   cmd_open: 8   pc_timeout_count: 0   pc_resync_count: 0
```

Boot-time powerup log:

```
[   11.549508] SSR after powerup: scheduling powerup work
[   12.199525] SSR powerup: modem pc_state=1 (waited 560 ms)     <- no retry needed, correct
[   12.240040] SSR powerup: channels already active
[   12.240109] received CMD_OPEN (1) on channel 0 ... through channel 7
```

### 7.3 The fatal cadence is unchanged (patch 814 is not a stability fix)

Three fatals in the failing boot, two different signatures — which is why a fatal must never be
classified by its `file:line`:

| # | AP s | signature |
| --: | --: | :-- |
| 1 | 424.573497 | `a2_power.c:1189` |
| 2 | 1326.760468 | `lte_ml1_sleepmgr_stm.c:4054` |
| 3 | 1405.636209 | `a2_power.c:1189` |

### 7.4 Soak run 1 — an SSR survived, and a harness defect that would have been misread

`soak814.sh` runs the corrected idle-then-burst traffic generator and, unlike its predecessors,
**probes the data plane after every SSR**. Run 1 (`evidence/…/run1_soak814.csv`, `.log`) reached a real
fatal-triggered SSR and the driver recovered:

```
[  912.723006] fatal error received: lte_ml1_sleepmgr_stm.c:4054:      <- 900.904 s of modem uptime
[  912.746032] SSR before shutdown: scheduling teardown work
[  914.129025] SSR after powerup: scheduling powerup work
[  914.761965] SSR powerup: modem pc_state=1 (waited 560 ms)          <- no retry needed, correct
[  914.764299] SSR powerup: successfully reinitialized BAM channels and rings
```

and, in the CSV, **`cmd_open` 8 → 16** — the modem reopened all eight data channels, which can only
happen if the driver rebuilt the BAM channels. `defer_q == defer_sub` throughout (129/129), `wiped_live 0`,
`guard_hits 0`, `pc_timeout_count 0`, `pc_resync_count 0`. So the **normal** SSR path is intact with
patch 814 deployed, and the new retry/rebuild paths stayed silent — they are inert unless needed.

**The harness defect.** Run 1 also logged

```
[926.71s] DATA PLANE DOWN (SSR #1): NO DEFAULT ROUTE; wwan0: ... state DOWN
```

which is **false**. By 972.6 s the link was fully back (`default via 10.103.224.185 dev wwan0`,
`2 packets transmitted, 2 received, 0% packet loss`). An SSR is followed by ModemManager
re-registration, a bearer reconnect, and netifd re-installing the address and the route — which
legitimately takes **~30 s** (here ~45 s: SSR at 914.8 s, route back by ~960 s). A single probe 8 s
after the SSR therefore reports "down" for a perfectly healthy recovery.

This is the **second** measurement artefact of exactly this kind in two sessions (the first was the
`ping -i 0.05` burst that silently transmitted nothing, Doc 156 §8.2/§8.5). Both would have been read as
a device fault. The probe now **polls** (5 s × 12 = 60 s) and reports the **time to recovery**, so
"down" means it never came back inside the window. Run 1's numbers are kept as-is rather than
reinterpreted.

A second, smaller harness defect was found and fixed in the same pass: the modem-uptime-at-fatal
calculation took the **last** `is now up` line unconditionally, which after a recovery picks the *new*
modem's start time and yields a **negative** modem uptime (run 1 logged `-1.406112s`). It now selects
the last line older than the fatal's timestamp, which for fatal #1 gives the correct **900.904 s** —
the deterministic idle timer, consistent with Doc 156's 900.811 s.

Run 2 (`run2_soak814.csv`, `.log`) was started on the production module at uptime 1013.9 s and
abandoned ~2 s later when the test build below was deployed. It carries no measurement.

### 7.5 Run 3 — the retry's *own* rebuild branch, proven by dropping the assert edge

§8.4 item 1 left the decisive question open: the retry was known to **fire**, but in the only
observation of it (the `i < 2` test build's boot, §8.4) the channels had already been rebuilt by the
**pc_irq edge** — `CMD_OPEN` at 12.221 s arrived *before* the retry's success log at 12.542 s, and the
retry reported `channels already active`. That leaves exactly the wrong half verified. The failure this
patch exists for is a **lost assert edge** (§5.1): if the edge is what rebuilds the channels, then
patch 814 does nothing for the case it was written for.

So run 3 forced it. Two temporary edits were applied to the working source — **neither is part of
patch 814** — and saved as `evidence/…/run3_test_edits.patch` (49 lines) against `post814`:

1. the poll window `for (i = 0; i < 150; i++)` → `i < 2`, i.e. 40 ms instead of 3200 ms, so every SSR
   must take the retry path; and
2. in `bam_dmux_pc_irq()`, the rising edge is **dropped outright** when `dmux->rx` or `dmux->tx` is
   NULL — nothing is written, nothing is acked, no `bam_dmux_power_on()` — which is a faithful
   simulation of the edge never reaching SMSM. The pc IRQ is edge-triggered (`smsm 1 Edge`) and SMSM's
   cascade handler is edge-based on a cached value (`smsm.c:219-220`), so dropping it cannot storm.

The reconstructed test source rebuilds to `5e26986d4697ac992489cc849f29c899`, byte-identical to the
module that was deployed — so the saved patch reproduces the test exactly.

Result (full capture: `evidence/…/run3_lost_edge_retry_rebuild.txt`):

```
[   11.866793] SSR after powerup: scheduling powerup work
[   12.126163] SSR powerup: modem pc line not asserted, retry 1/20 in 500 ms
[   12.516987] TEST: pc assert edge DROPPED (rx=0000000000000000 tx=0000000000000000) -- only the poll path can recover now
[   12.866073] SSR powerup: modem pc_state=1 (waited 200 ms)
[   12.869878] SSR powerup: successfully reinitialized BAM channels and rings   <- the RETRY did this
[   12.874720] received CMD_OPEN (1) on channel 0   ... through channel 7
```

The edge at 12.517 s was discarded with `rx == tx == NULL`; the retry polled again at 12.866 s, read
the modem's true SMEM state word (not an interrupt), found `!dmux->rx`, and called
`bam_dmux_power_on()` itself. The eight `CMD_OPEN` messages follow from the driver's own open sequence
— there was no second edge to trigger them. The resulting data plane is fully functional:

```
wwan0: <POINTOPOINT,NOARP,UP,LOWER_UP> ... state UNKNOWN
inet 10.101.20.157/30   default via 10.101.20.158 dev wwan0 proto static src 10.101.20.157 metric 10
4 packets transmitted, 4 received, 0% packet loss
rx_slots_mapped: 32   rx_tearing_down: 0   pc_state: 1   pc_line_level: 1   pc_resync_count: 0
nslookup openwrt.org -> 2a03:b0c0:3:d0::1a51:c001
```

`pc_irq_count` reached 11 over the boot, so the handler resumed working normally once the channels
existed — the suppression was confined to the missing-channels state, as intended.

Both temporary edits were then reverted. The restored source is byte-identical to `post814`
(`diff` clean) and rebuilds to **`eca269f10a685a83a8b679bc7be38d1d`**, the production module hash —
verified by rebuild, not by assumption. It was redeployed and the device rebooted: the boot log is the
normal path again (`SSR powerup: modem pc_state=1 (waited 560 ms)`, `channels already active`, no
`TEST:` lines, no `retry`), with `5/5` and `5/5` ping and `cmd_open: 8`.

**This closes the last verification gap on patch 814.** The recovery does not depend on ever receiving
another interrupt.

### 7.6 Run 6 — the watchdog rebuild fires **naturally**, twice, and a new stall is found

Run 6 (`run6_soak814.csv`, `.log`, `run6_dmesg_final.txt`; 538 samples, 8 fatals in one boot) is the
longest soak on the production module and it settles the remaining question in the other direction:

```
[2907.109701] RX watchdog: modem awake (pc line asserted) but no channels, rebuilding
[3888.527634] RX watchdog: modem awake (pc line asserted) but no channels, rebuilding
```

**The §6.2 recovery path fired twice on its own**, with no test build, and both times the data plane
came back (`DATA PLANE OK (SSR #4): recovered after ~25s`, `(SSR #6): recovered after ~10s`). The CSV's
`rebuilds` column tracks it (`0` → `1` at SSR #4 → `2` at SSR #6). So **both** new recovery paths are
now verified: the retry by the run-3 test build (§7.5), and the watchdog rebuild by a natural trigger
here.

`retries` stayed **0** for all 8 SSRs. Every modem in this run was ready within 540–580 ms, far inside
the original 3.2 s window, so the retry had nothing to do — which is the expected result and confirms
the patch is inert when it is not needed (§7.4, §8.4 item 1).

**A new observation, and it is NOT explained.** SSR #5's recovery looks clean in dmesg —
`successfully reinitialized BAM channels and rings` at `3819.105`, then all eight `CMD_OPEN`s at
`3819.110` — but **no data flowed for ~68–85 s**. The harness probe made 12 attempts over 75 s and
failed every one, and the telemetry agrees independently: across `3817.67 s → 3903.37 s` **`rx_pkts`
advanced by 0 while `tx_pkts` advanced by 45**, against a healthy baseline of ~40 TX *and* ~40 RX per
10 s sample. It recovered only when the *next* fatal's SSR (#6) tore the channels down and rebuilt
them. This is a "recovered" SSR that did not pass data — a different failure from the one this doc is
about (there, `wwan0` was DOWN and `cmd_open` was frozen; here the driver reported success).

It is recorded as an **observation needing confirmation, not a finding**: n=1, and the probe's own
`wwan0 … state DOWN` snapshot at 3893 s is contaminated by SSR #6's teardown at 3887 s. The harness has
been changed to make the next occurrence decisive — `probe_dataplane()` now logs a per-attempt trace
(`route`/`NOroute`, `pc_state`, `pc_line_level`, `rx_slots_mapped`, `cmd_open`) so a bare "down" can be
told apart from "route present but the modem is not passing packets".

**Harness lesson, for the third time in this line of work.** The same run's log made SSR #5 *look* like
it had lost its `SSR after powerup` line — the teardown at `3809.637` was the last matching line and
nothing followed it. It was not a defect: the block captures `dmesg | grep … | tail -8`, and it was
written at 3818.16 s, i.e. **0.33 s before the modem finished coming up at 3818.489 s**. The full dmesg
(`run6_dmesg_final.txt`) shows the cycle was completely normal. A bounded `tail` window is a
measurement instrument with a blind spot, and the fix is always the same: check the raw log before
believing the summary.

**Also in this run, and it is a production failure, not a harness one:** the boot continued past the
soak's 8-fatal limit, took 21 crashes in total, and **hung on the 21st**. That is a different defect at
a different site and it has its own doc — **Doc 159**.


## 8. Scope, what this is not, and next

### 8.1 What this defect is not

* **Not the ~900 s fatal.** Patch 814 changes no timing constant on the fatal path; the crash cadence
  is untouched (§7.3). The three crashes still happen.
* **Not the Doc 156 deferred-TX packet loss.** That is patch 812 and it remains fixed (§7.4).
* **Not the Doc 154 §6 modem boot hang.** There the modem never came up (`remoteproc0/state` =
  `offline`); here it came up normally and the *driver* stopped.
* **Not a `network.modem.auto` problem.** Memory quirk #9 says the interface does not autostart; `auto`
  is now `1`, netifd installed the address *and* the route, and the interface was still down. The
  corpus explanation is necessary but not sufficient — quirk #9 should be amended to say so.

### 8.2 A second, independent confirmation of the Doc 151 §5 hang

Doc 151 §5 recorded that `echo stop` on the modem remoteproc hangs the AP and ends in a watchdog
reset, and explicitly declined to claim reproducibility ("One observation only"). While trying to
exercise the patch-814 recovery paths on demand, it happened again:

```
[  116.534504] bam-dmux ...: SSR before shutdown: scheduling teardown work
[  116.535604] wwan wwan0: port wwan0at0 disconnected
   <console ends>
```

No `dmesg-ramoops` record (so not a panic; `kernel.panic = 3` is set), `watchdog0` `state=active` with
a 30 s timeout, and the device came back at uptime 32 s. **n=2**, on a different boot (uptime 116 s vs
9355 s) and a different module build (patch 814 vs its predecessor). It is therefore not caused by
patch 814, and patch 814 does not fix it. Evidence:
`evidence/157_ssr_powerup_giveup/echo_stop_hang_second_observation.txt`.

**Consequence:** an AP-initiated modem teardown is still not available as a test route. The recovery
paths were therefore exercised by a **deliberately crippled test build** instead of by forcing an SSR —
which turned out to be the better experiment anyway, because it isolates the poll path from the
interrupt path (§7.5).

**Update, same day — this is NOT the same hang as Doc 159.** Run 6's boot later hung on a *spontaneous*
fatal (crash #21) and it landed at a **different site**: past `executing serialized asynchronous SSR
teardown` and past `stopped remote processor`, on the line `port failed halt`. The `echo stop` hang
lands *before* the teardown work even starts. Two distinct hangs, one a test-route hazard and one a
production failure — see **Doc 159**. The practical consequence above is unchanged (still do not use
`echo stop`), but the reason to care about this area is now much stronger than "a test route is
unavailable".

### 8.3 Open questions

1. **Why was the assert edge missed?** §5.1. The candidate is the direct write to the modem's SMEM
   state word in `bam_dmux_ssr_teardown_work_func()` (`:2189-2191`), which SMSM's `last_value` cache
   does not observe. It does not explain this incident (the `enable_irq()` refresh lands before the
   modem asserts) but it is a real hazard. A test: instrument `smsm_intr()` to log `val`, `changed` and
   `last_value` for the pc bit, then force an SSR. **Lower priority since §7.5:** the driver now
   recovers from a wholly missed edge, so this is now a question about the *mechanism*, not about
   whether the symptom returns. It would still be worth knowing whether an edge is ever missed in the
   field at all — if it never is, the real trigger is only the slow-boot case.
2. **Why did this modem boot take 6.4 s when the other two took 0.4 s?** Crash #3 was the third fatal
   in ~980 s. Back-to-back crashes may load the firmware more slowly.

### 8.4 Next, in priority order

1. **Keep `soak814.sh` running** (run 7 is live on the production module `eca269f1…`) and watch for a
   **natural** `retry N/20` line followed by `DATA PLANE OK (SSR #n)`. The retry's own rebuild branch is
   now proven by construction (§7.5) and the *watchdog* rebuild has since fired naturally twice (§7.6),
   so this is no longer a correctness question — it is a confirmation that the *retry's* trigger (a
   modem slower than 3.2 s) occurs in the field at all. Run 6 produced 8 SSRs and **no** retry, every
   modem being ready in 540–580 ms. A run in which no retry fires is **inconclusive, not negative** —
   say so, do not upgrade it.
2. **Amend memory quirk #9** — `auto` is not the whole story; see §8.1.
3. **Instrument SMSM** for the missed-edge question (§8.3 item 1), if a third occurrence appears.
4. **Do not** retry `echo stop` as a test route (§8.2).

## 9. Established vs not established

**Established (measured / source-verified):**

1. `bam_dmux_ssr_powerup_work_func()` returned permanently when the modem's PC line was not asserted
   within 3.2 s; there was no retry and no reschedule (`:2228-2231`, was). §4.2.
2. The modem in this incident took ~6.4 s to become ready and was still booting when the poll expired
   (ports attached 2.65 s *after* the give-up). §4.1.
3. `bam_dmux_runtime_resume()` returns 0 with `dmux->rx == NULL` (`:2005-2012`), and
   `bam_dmux_netdev_open()` therefore proceeds into `bam_dmux_send_cmd()`, which refuses with
   `-EAGAIN` on `pc_state == false` (`:500-505`). §4.3.
4. `ip link set wwan0 up` on the stuck device fails with `RTNETLINK answers: Resource temporarily
   unavailable` and leaves the interface DOWN. §4.3.
5. The kernel deletes a device's IPv4 routes when the device goes down, so the "missing default route"
   is a consequence of the interface being down. §3.
6. The stuck state was `pc_line_level 1` / `pc_state 0` / `rx_slots_mapped 0` / `cmd_open 24` /
   `pc_resync_count 0`, with the modem `running`, registered and attached. §5.
7. The RX-watchdog lost-edge resync was unreachable with `rx == tx == NULL` (`:1302`, was). §4.4.
8. `pc_line_level` is a read of the remote SMEM state word, not a latched pending bit
   (`smsm.c:320-334`). §5.1.
9. The SMSM cascade handler is edge-based on a cached `last_value` (`smsm.c:219-220`), refreshed on
   unmask (`smsm.c:280-283`). §5.1.
10. The Doc 151 §5 `echo stop` hang reproduced — **n=2**. §8.2.
11. Patch 814's chain reproduces the built source byte-exactly, and the post-fix baseline is healthy.
    §7.1, §7.2.
12. **Patch 814 does not disturb the normal SSR path.** Soak run 1 survived a real fatal-triggered SSR:
    the powerup work found the modem in 560 ms, reinitialised the channels, and the modem reopened all
    eight of them (`cmd_open` 8 → 16). The new retry and rebuild paths stayed silent (`retries 0`,
    `rebuilds 0`, `pc_resync_count 0`, `pc_timeout_count 0`) — they are inert unless needed. §7.4.
13. The fatal cadence is unaffected by patch 814: run 1's fatal #1 was `lte_ml1_sleepmgr_stm.c:4054` at
    **900.904 s of modem uptime** — the deterministic idle timer. §7.4.
14. **The retry path rebuilds the channels on its own, with no help from the pc_irq edge.** With the
    edge deliberately dropped while `rx == tx == NULL`, the retry polled the modem's SMEM state word,
    called `bam_dmux_power_on()` itself, logged `successfully reinitialized BAM channels and rings`,
    the modem opened all eight channels, and the data plane came back fully (4/4 ping, DNS,
    `rx_slots_mapped 32`, `rx_tearing_down 0`). §7.5.
15. The run-3 test build is reproducible from the saved patch (`run3_test_edits.patch` → md5
    `5e26986d…`), and reverting it reproduces the production module byte-exactly
    (`eca269f10a685a83a8b679bc7be38d1d`), verified by rebuild. §7.5.
16. **The §6.2 watchdog rebuild fires on a natural trigger.** In run 6 the modem asserted the pc line
    while `pc_state` was 0 with no channels, twice (2907.1 s, 3888.5 s), the watchdog rebuilt, and the
    data plane returned both times (`rebuilds` 0→1→2 in the CSV). §7.6.
17. **Patch 814 is inert when unneeded, over 8 consecutive real SSRs.** Run 6: `retries 0` for all
    eight, every modem ready in 540–580 ms, `oops 0`. §7.6.

**Observed but NOT explained (n=1, needs confirmation):**

* **SSR #5 in run 6 recovered the channels but passed no data for ~68–85 s.** The driver logged
  `successfully reinitialized BAM channels and rings` and all eight `CMD_OPEN`s, yet `rx_pkts` advanced
  by **0** while `tx_pkts` advanced 45 over `3817.67 s → 3903.37 s` (healthy baseline: ~40 TX and ~40 RX
  per 10 s). It cleared only when the next fatal's SSR rebuilt the channels again. The harness probe
  failed all 12 attempts over 75 s, but its `wwan0 … state DOWN` snapshot is contaminated by SSR #6's
  teardown. §7.6 — recorded as an observation, not a finding.

**Not established:**

* **That a retry fires in the field on the production build.** The path is proven by construction
  (§7.5) and inert when unneeded (§7.4, §7.6), but no *natural* occurrence has been observed — the
  trigger is a modem slower than 3.2 s. Run 6 produced **8** real SSRs and none of them triggered it
  (every modem ready in 540–580 ms); run 7 is watching. This is now a question about the trigger, not
  about the fix.
* **Why the assert edge was missed.** §5.1. The SMEM-write hazard is a candidate that does not fit the
  observed timing.
* **Whether the 3.2 s budget is the only reason the first two SSRs succeeded** (they waited 200 ms;
  run 1's recovery waited 560 ms).
* **Whether `bam_dmux_rx_slot_submit()` failing inside `bam_dmux_power_on()` is silent** — it is the
  one failure path in `power_on()` with no `dev_err`, and it was not observed.
* **Whether the ~30-45 s post-SSR blackout is itself reducible.** It is normal ModemManager
  re-provisioning, but it is long enough to be user-visible.

## 10. Artifacts

`Docs/Modem Stability/evidence/157_ssr_powerup_giveup/`

| file | what |
| :-- | :-- |
| `814-bam-dmux-ssr-powerup-retry.patch` | the fix (tracked copy is `msm89xx/patches/`) |
| `qcom_bam_dmux.c.pre814` / `.post814` | the two source states |
| `stuck_state_capture.txt` | the live stuck device, verbatim |
| `echo_stop_hang_second_observation.txt` | the n=2 confirmation of Doc 151 §5 |
| `soak814.sh` | the soak with per-SSR data-plane probes |
| `run1_soak814.csv` / `.log` | run 1 — the normal SSR path is intact |
| `run2_soak814.csv` / `.log` | run 2 — abandoned after 2 s, no measurement |
| `run3_test_edits.patch` | **not** part of patch 814: the two temporary test edits |
| `qcom_bam_dmux.c.run3_testbuild` | the run-3 test source (rebuilds to `5e26986d…`) |
| `run3_lost_edge_retry_rebuild.txt` | run 3 — the retry's own rebuild, with the edge dropped |
| `run4_soak814.csv` / `.log` | run 4 — 58 clean samples, abandoned when the device was rebooted |
| `run6_soak814.csv` / `.log` / `run6_dmesg_final.txt` | **run 6** — 538 samples, 8 fatals, the watchdog rebuild firing twice, the SSR #5 stall (§7.6). The full dmesg is the artifact that settled the `tail -8` ambiguity. |

## 11. One line

**A modem that booted in 6.4 s was declared collapsed by a driver that only waited 3.2 s and then never
tried again — so the interface was left down, the kernel deleted its default route, and the symptom
looked like a dead modem.**
