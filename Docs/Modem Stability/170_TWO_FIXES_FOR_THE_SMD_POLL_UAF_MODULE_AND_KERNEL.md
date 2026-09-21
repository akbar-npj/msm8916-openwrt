# Doc 170 — Two independent fixes for the SMD poll use-after-free: module-level (823) and kernel-level (822)

**Date:** 2026-09-21
**Predecessor:** Doc 169 (`169_THE_AP_RESET_IS_AN_SMD_POLL_USE_AFTER_FREE.md`)
**Status:** **patch 823 is DEPLOYED and functionally clean** (§8.7); the control failed to reproduce, so
§7's pre-registration is **withdrawn** (§8.2) and the fix stands on mechanism, not on a run count
(§8.6); one unexplained boot is recorded and **not** exculpated (§8.8); **and §8.10 records an AP
reset that survived 823 — so 823 is a real fix for the UAF it targets but is NOT a complete fix for
the production reset.** §8.11 names the reset mechanism (**PMIC PON WDT, 30 s, active**) and reports a
reboot rate the "1 in 20–40" model does not predict; §8.12 deploys the instrument that was missing.

---

## §0 SOP compliance

| SOP step | Status |
|---|---|
| Read the *definition*, not the name | ✔ `wwan_port_fops_poll`, `rpmsg_poll`, `qcom_smd_poll`, `poll_wait`, `is_write_blocked`, `wwan_port_op_tx` all read in the live tree before writing anything; **round 27 adds `sysmon_stop` (`qcom_sysmon.c:555`) and `bam_dmux_runtime_resume` (`qcom_bam_dmux.c:2070`), read to place the §8.8 hang rather than name it** |
| Verify against the running kernel, not against a doc | ✔ `CONFIG_RPMSG_WWAN_CTRL=m` and `CONFIG_RPMSG_QCOM_SMD=y` read from the **live** `.config`; the module's identity read from the **device** (`md5sum`, `lsmod`) **before and after** the swap |
| Grep the call sites | ✔ every caller of `rpmsg_poll` and every user of `tx_poll` enumerated; **and §8.8 greps every `/overlay` script for remoteproc writes to rule the watchers out** |
| Instrument both sides of the hand-off | ✔ the poller is identified by **observation** (`/proc/<pid>/fd`), not inference |
| Pre-register before running | ✔ §7 — **and then withdrawn in §8.2 when the control invalidated it.** Registering is not the same as being entitled to the prediction |
| Comparative ground truth | ✔ the pre-fix control is Doc 168 run B + Doc 169 run A (2/2 corruption + reset) — **and §8.1 records that it did NOT reproduce here, which is the honest headline of this round** |
| One change per patch | ✔ 822 = one call in `qcom_smd.c`; 823 = one argument in `rpmsg_wwan_ctrl.c`, verified as an **18-line disassembly diff confined to one function** (§8.6) |
| Nothing is a fact until measured | ✔ §2 is measurement; §3–§5 are mechanism, labelled as such; **§8.6's strip recipe is validated against the shipped baseline by size, and §8.9 records a case where a *tool failure* was nearly written up as a finding** |
| Read the *branch conditions*, not the script's reputation | ✔ **§8.10**: `qcom-carrier-autocfg` was recorded in memory as "power-cycles the modem every 10 s"; reading its `while … sleep 10` body showed the power-cycle is inside the *operator-change / cache-mismatch* branch only. The script is exonerated as the 17:03 reset's cause, and the earlier claim is corrected in §9 trap 8 |
| Make the instrument survive the failure it measures | ✔ **§8.10**: the beacon truncated its own files at every boot, destroying exactly the pre-reset tail it exists to capture. The reset-durable `BOOT`-marker form is now deployed. The gap was *in the instrument*, and only re-reading it against the failure it missed exposed that |
| Read the code that runs, not the code you remember | ✔ **§8.13**: the corpus said "there is NO per-device `disabled`" for the remoteproc coredump. `remoteproc_sysfs.c:73-127` accepts `"disabled"`, and `remoteproc_core.c:2426` installs the **default** `rproc_coredump` for the MSS because `q6v5_ops` has no `.coredump` member — so a one-line sysfs write removes a whole MBA load from the middle of every SSR recovery. **Both facts were only visible by grepping the running kernel's own source.** |
| Pre-register before running | ✔ §7 — **and then withdrawn in §8.2 when the control invalidated it.** Registering is not the same as being entitled to the prediction. **§8.13 re-registers a bar (0 reboots / 20 fatals) for the coredump-off experiment *before* it runs.** |

---

## §1 Headline

Doc 169 proved the AP reset is a **use-after-free of `channel->fblockread_event`**, the wait queue
inside `struct qcom_smd_channel`: `qcom_smd_edge_release()` (`qcom_smd.c:1448`) `kfree()`s the
channel while a task blocked in `poll()` still holds a `poll_table_entry` linked in that queue.

**This round closes the one open item in that proof, finds a second, cheaper way to fix it, ships it,
and then has to report that the reproduction it was going to be scored against does not reproduce.**

1. **The poller's identity is now measured, not inferred.** Doc 168 §2.4 said *"confirm the poller's
   identity (`/proc/<qmi-proxy pid>/fd`) before claiming it."* Done — see §2.
2. **The second poll registration lives in a LOADABLE MODULE**, so the reachable instance of the UAF
   can be removed **without a kernel flash** (patch 823) — and it now **is** removed on the device
   (§8.7).
3. Patch 822 (the in-kernel drain) remains the **structural** fix and is still the one that covers
   pollers other than qmi-proxy.
4. **The control did not reproduce** (§8.1), so the "deterministic" claim in Doc 169 §5 is
   **falsified**, §7's pre-registration is **withdrawn** (§8.2), and the fix is justified by
   **mechanism** — an argument that does not depend on the race rate (§8.6).
5. **One boot with 823 hung at t=73.93 s — RESOLVED as the known intermittent SSR-path hang, not a
    regression** (§8.8). The same `ssctl → teardown → pc-ack` sequence ran three more times in the
    same round and recovered every time, and a **natural fatal also recovered with 823 deployed**
    (`t0..t9`, 0 corruption, coredump captured, data working). 823 is **substantially exonerated**, not
    absolutely — the unpatched control boot is still the clean discriminator and has not been run.
6. **A trap worth more than the verdict:** both `ssctl` and `pc-ack` lines were read as fault markers
    *because they are rare in the corpus* — and the corpus is dominated by boots that died before
    reaching them. **Establish a message's BASE RATE on a known-good run before calling it a fault.**
7. **And the headline that matters for production: an AP reset occurred with 823 deployed** (§8.10) —
    165 s after three natural fatals had each run a *complete* SSR and recovered. pstore was empty,
    so it was not a trappable fault; no fourth coredump and no ledger row exist, so it is *not*
    attributable to a fatal from this data. 823 fixes the UAF it was written for; it does **not** close
    the coredump-reclaim hang that Doc 159 located. **This is why the round does not end with "fixed".**
8. **The reset mechanism is now named, and the tidy explanation is REJECTED** (§8.11): the AP does not
    panic, it **stops**, and the **PM8916 PON watchdog (active, 30 s timeout)** resets the SoC — which is
    exactly "empty pstore + reboot" and explains the `~30 s stall + ~15–40 s boot` shape of every
    outage. The watcher's 15 real reboots in ~5 h *look* like 1 in 1–3 SSRs rather than 1 in 20–40, but
    **14 of the 15 are pre-823, that window was also the heaviest manual-activity window, and six are
    boot-loop reboots below 500 s uptime**. The tempting model — "the AP hangs on the SSR of the n-th
    idle-timer fatal", i.e. reboots at multiples of ~902 s — **was tested and fails: only 2 of 15 fit,
    and 6 of 15 cannot fit at all.** So the reset is driven by the variable activity-correlated fatals
    or by something that is not a fatal. **The instrument that was missing is now deployed** (§8.12):
    a reset-durable beacon plus a per-boot rolling kernel log, both reboot-persistent, so **the next
    stall will be characterised rather than merely counted.**

---

## §2 NEW MEASUREMENT — the poller, and the module scope

**2.1 The poller.** On the device (boot uptime 973 s, qmi-proxy running):

```
$ for p in /proc/[0-9]*; do for f in $p/fd/*; do l=$(readlink $f); case "$l" in *wwan*|*rpmsg*) …;; esac; done; done
3658 qmi-proxy /proc/3658/fd/7 -> /dev/wwan0qmi0
```

**qmi-proxy fd 7 on `/dev/wwan0qmi0` is the only wwan/rpmsg fd on the entire device.** No other
process holds one. So Doc 168 §2.4's hypothesis is now an observation, and the *reachable* poller set
on this device is a set of one.

**2.2 The module scope — the finding that changes the deployment plan.**

```
CONFIG_RPMSG_WWAN_CTRL=m    ->  rpmsg_wwan_ctrl.ko, 6808 B, md5 cd46d8f45a74e6a1f77c0a136a86249
CONFIG_WWAN=m               ->  wwan.ko, 28600 B
CONFIG_RPMSG_QCOM_SMD=y     ->  builtin (patch 822 needs a kernel flash)
CONFIG_QCOM_BAM_DMUX=m      ->  qcom_bam_dmux.ko
```

The **second** poll registration — the one that links into `channel->fblockread_event` — is made by
`rpmsg_wwan_ctrl_tx_poll()`, which is in **`rpmsg_wwan_ctrl.ko`**. That is the whole reason a
no-flash fix exists.

**2.3 The chain, with the exact cut point.**

```
qmi-proxy ppoll(/dev/wwan0qmi0)                      [fd 7, measured]
  -> wwan_port_fops_poll()                  wwan_core.c:772
       poll_wait(filp, &port->waitqueue, wait)       entry 0  — SAFE (device ref held by open())
       mutex_lock(&port->ops_lock)
       port->ops->tx_poll(port, filp, wait)          <-- CUT HERE (patch 823)
         -> rpmsg_wwan_ctrl_tx_poll()       rpmsg_wwan_ctrl.c:83
              -> rpmsg_poll()                rpmsg_core.c:292
                   -> qcom_smd_poll()        qcom_smd.c:991
                        poll_wait(filp, &channel->fblockread_event, wait)   entry 1  — UNSAFE
                        if (qcom_smd_get_tx_avail(channel) > 20) mask |= EPOLLOUT|EPOLLWRNORM;
```

`rpmsg_poll()` uses `wait` for **nothing** but forwarding it; `qcom_smd_poll()` uses it for **nothing**
but that one `poll_wait()`. That is what makes the cut clean.

---

## §3 Patch 823 — remove the reachable instance, keep the mask

```c
static __poll_t rpmsg_wwan_ctrl_tx_poll(struct wwan_port *port,
					struct file *filp, poll_table *wait)
{
	struct rpmsg_wwan_dev *rpwwan = wwan_port_get_drvdata(port);
	struct poll_table_struct no_wait = { };   /* _qproc == NULL */

	(void)wait;

	return rpmsg_poll(rpwwan->ept, filp, &no_wait);
}
```

**Why this is correct and not a hack — from the definitions, not the names.**

* `poll_wait()` is
  ```c
  if (p && p->_qproc && wait_address) { p->_qproc(filp, wait_address, p); smp_mb(); }
  ```
  (`include/linux/poll.h:42`). With `_qproc == NULL` it is **a no-op** — no `poll_table_entry` is ever
  linked into `channel->fblockread_event`, so there is no dangling entry for
  `remove_wait_queue()` to walk. **The UAF becomes unreachable.**
* **The returned mask is byte-for-byte identical.** `qcom_smd_poll()` computes
  `EPOLLOUT|EPOLLWRNORM` from `qcom_smd_get_tx_avail(channel) > 20` **independently of `wait`**, and
  `rpmsg_poll()` returns it unchanged. So TX flow-control reporting is preserved.
* The alternative (deleting `.tx_poll` from `rpmsg_wan_pops`) was **rejected**: it would fall through
  to `else if (!is_write_blocked(port)) mask |= EPOLLOUT`, and `is_write_blocked()` is
  `test_bit(WWAN_PORT_TX_OFF, &port->flags) && port->ops` — and **nothing in `rpmsg_wwan_ctrl.c`
  ever calls `wwan_port_txoff()`/`txon()`** (grepped: no matches). EPOLLOUT would therefore become
  **unconditionally set**, silently discarding the `> 20` flow-control signal. The `no_wait` form
  keeps it.

**§3.1 The honest limitation.** Dropping the registration also drops the *wakeup* that the SMD queue
would deliver when TX space frees. A poller blocked **purely** on EPOLLOUT is no longer woken by that
event. Mitigations, all real: entry 0 in `port->waitqueue` is still registered and is woken by every
RX (`wwan_port_rx()`), so a request/response client self-heals; QMI clients run timeouts; and this is
strictly better than resetting the AP. **It is a real, if small, behavioural change and it is recorded
as such rather than hidden.** Patch 822 has no such change, which is exactly why 822 stays on the list.

---

## §4 Patch 822 — the structural fix (kernel, needs a flash)

`qcom_smd_drain_channel_pollers(edge)` runs **between** the child-device removal (which wakes the
poller) and the `device_unregister()` that frees the channels: `wake_up_all()` plus a wait for
`waitqueue_active()` to clear, **bounded by wall time** (`jiffies + msecs_to_jiffies(2000)`,
`dev_warn` on timeout).

**§4.1 Why the bound is a wall-clock deadline and not a retry counter.** The first revision looped
`for (retries = 5000; retries--; ) msleep(1)` and called that "roughly 5 seconds". It is not:
`msleep(1)` can sleep for a whole tick or longer, so 5000 iterations is a bound on *iterations*, not on
*time* — on a slow tick that is tens of seconds of extra latency **inside the SSR teardown**, which is
precisely the path whose blocking behaviour Doc 168 spent a round chasing. The deadline form is
bounded at 2 s regardless of sleep granularity, and 2 s is ~3 orders of magnitude more than a woken
poller needs to reach `poll_freewait()`. **A bounded wait whose bound is not in the units it claims is
not a bound.**

**Why it is structural rather than a timing heuristic:** `wwan_remove_port()` sets `port->ops = NULL`
under `ops_lock`, and `wwan_port_fops_poll()` takes that **same** lock before calling `tx_poll`
(`wwan_core.c:777-779`) ⇒ **no poller can re-register during the drain.** The drain therefore cannot
lose a race with a new registration; it only has to outlast the ones already linked.

**822 covers what 823 cannot:** any poller on any SMD channel — e.g. a raw `/dev/rpmsgN` client
(`rpmsg_char`), or a future client. On *this* device there are none (§2.1), but that is a property of
today's process list, not of the kernel.

---

## §5 Deployment

| fix | artefact | deployment | risk |
|---|---|---|---|
| **823** | `rpmsg_wwan_ctrl.ko` (module) | copy to `/lib/modules/6.12.94/`, `rmmod`+`modprobe` (or reboot) | low, instantly reversible |
| **822** | kernel `Image` (builtin) | `sysupgrade` of the target image | needs a flash; **`/overlay` is preserved** (see §5.1) |

**§5.1 The flash is not as destructive as it looks — verified in the target's own upgrade script.**
`openwrt/target/linux/msm89xx/base-files/lib/upgrade/platform.sh:264`:

```sh
if [ -z "$UPGRADE_BACKUP" ]; then
        mkfs.ext4 -q -F -L rootfs_data "$data_part"     # wipes /overlay
else
        log_upgrade "Upgrade with configuration preservation complete"
fi
```

`UPGRADE_BACKUP` is set by `sysupgrade` **unless `-n` is passed**. So a plain
`sysupgrade <image>` **writes only `boot` and `rootfs` and does not format `rootfs_data`** — the
overlay (deployed modules, coredumps, soak scripts, and the modem firmware under `/lib/firmware`)
survives. **`-n` would destroy it.** This is the single most important operational detail of this
round and is the reason a kernel flash is acceptable at all.

---

## §6 Order of testing, and why

1. **823 first.** ~30 s, no flash, instantly reversible, and it is a *direct test of the mechanism*:
   if removing the SMD poll registration removes the corruption **and** the reset, then
   `channel->fblockread_event` is confirmed as the corrupted head by *causation*, not only by the
   arithmetic of Doc 169 §4.
2. **822 second** (after reverting 823), via a flash. It must then reproduce the same clean result
   *with the SMD registration restored* — otherwise 823 would be masking the result.

---

## §7 PRE-REGISTRATION (written before any run)

> **⚠ WITHDRAWN — see §8.1/§8.2.** The control did **not** reproduce (0/2, the second run with the
> precondition explicitly forced), so the "control 2/2" this pre-registration rests on does not hold
> under today's conditions, and a 0/5 result after the fix would be **indistinguishable from no fix
> at all**. The pre-registration is kept verbatim below because it was written before the runs and
> rewriting it afterwards would destroy the record. **The corrected protocol is in §8.2.**

**Question A — does 823 remove the corruption and the reset?**
`echo stop` with **qmi-proxy running**, **n = 5**.
**Predict: `list_del corruption` count 0 in all 5, AND the AP survives (uptime monotonic) in all 5.**
Success requires **both**; a run that survives but still logs the corruption is a **FAIL** (it would
mean the corruption has a second source).
**Pre-fix control: 2/2 corruption + reset** (Doc 168 run B, Doc 169 run A).
**Instrument for the stale-sink trap:** every run records the `console-ramoops-0` md5 *before* the
trigger and only accepts the record as evidence if the md5 changed **and** the record's own embedded
uptime is near that run's pre-trigger uptime. On a surviving boot, `dmesg` (not pstore) is the sink.

**Question B — does 822 (kernel) do the same with the SMD registration restored?**
Revert 823, flash the 822 kernel, repeat Question A. Predict 0/0 again.

**Question C — is the corruption the reset's only cause?** If a fix removes the corruption but the AP
still resets, Doc 169 §6 reading 2 holds and the reset has a second cause.

**Stated in advance so it cannot be spun later: n = 5 on a deterministic reproduction is strong, but
it is not the n ≥ 60 needed for the ~3–5 % *spontaneous* hang (Doc 167 §5). Question A tests the
**deterministic** `echo stop` path; it says nothing new about the spontaneous path.**

---

## §8 RESULTS

### §8.1 QUESTION A — BLOCKED, BECAUSE THE CONTROL FAILED TO REPRODUCE. **The "deterministic" claim is FALSIFIED.**

**Run the control first, and it did not behave as pre-registered.** Two consecutive
`echo stop` runs with the **unpatched** `rpmsg_wwan_ctrl.ko` — the exact configuration that
produced Doc 169's corruption — produced **no corruption and no reset**:

| | control 1 (`control`) | control 2 (`control_freshboot`) |
|---|---|---|
| AP uptime at trigger | 1578.35 | 124.46 |
| `port wwan0qmi0 attached / disconnected` in boot | 4 / 3 | **1 / 0** |
| poller | fd 7 → `/dev/wwan0qmi0`, wchan `do_sys_poll` | same |
| `echo stop` write | rc=0, 0 s | rc=0, 1 s |
| outcome | **survived** | **survived** |
| `list_del corruption` | **0** | **0** |
| teardown | T9 present (count 3) | T9 present (count 1), full T0..T9 |

Control 2 was run **after a deliberate reboot** specifically to force the precondition control 1
could not guarantee: with `attached = 1` and `disconnected = 0`, the SMD edge had never been
unregistered in that boot, so qmi-proxy's open of `/dev/wwan0qmi0` is **necessarily** on the
original, live port. **The stale-port objection is therefore eliminated, and the corruption still
did not fire.**

**What this falsifies.** Doc 169 §5 claimed the defect is **deterministic** — *"the teardown never
yields, so the poller always loses."* It does not always lose. **Two clean controls, the second
with the precondition forced, both show the poller winning.**

**What replaces it — measured, not argued.** Control 2's teardown is complete in `dmesg`, and it
puts a number on the window:

```
[  129.186015] bam-dmux T9 flush returned
[  129.197672] wwan wwan0: port wwan0at0 disconnected
[  129.198364] wwan wwan0: port wwan0at1 disconnected
[  129.213099] wwan wwan0: port wwan0qmi0 disconnected   <-- the poller's WAKE
[  129.215173] remoteproc remoteproc0: stopped remote processor ...   <-- kfree() already done
```

The poller is woken at `129.213099` and the channel is freed before `129.215173` — **a window of at
most 2.07 ms** in which the woken poller must reach `poll_freewait()` and unlink. On a 4-CPU system
it usually can. **This is a race with a ~2 ms window, not a guaranteed loss.**

**And it re-reads Doc 169's two positive observations correctly:** both were taken with the
ms-period sampler `hp3.sh` armed — i.e. **under heavy load**, which is exactly the condition that
would delay the woken poller past 2 ms. **That is a hypothesis and is directly testable** (re-run
`echo stop` with `hp3.sh` armed); it was not tested here because the device left the USB bus first
(§8.3).

### §8.2 CONSEQUENCE — §7's pre-registration is INVALID AND IS WITHDRAWN

§7 pre-registered *"n = 5, predict 0 corruptions and 0 resets, control 2/2."* **A control that is
2/2 in the corpus but 0/2 here cannot support that.** Five clean runs after deploying a fix would be
**indistinguishable from five clean runs without it.** Stating this rather than quietly running the
five is the whole point of pre-registering.

**The corrected protocol, in order:**

1. **Measure the control rate** — `echo stop` × n with the unpatched module, under (a) idle and
   (b) `hp3.sh` armed, to find whether load is the discriminator. **A forcing function that does not
   reproduce reliably cannot score anything.**
2. Only then deploy 823 and repeat under the same condition.
3. Report the two rates and n, or report that no forcing function was found.

**Not affected by this correction:** Doc 169 §4's arithmetic identification of the corrupted object
(re-derived independently from the compiled code in §3 above), the poll chain, and the fact that the
object freed is `channel->fblockread_event`. The A/B experiment with qmi-proxy SIGSTOPped removed
the corruption 1/1 — **also now weaker than it looked**, since the control is not 2/2 here.

### §8.3 THE DEVICE LEFT THE USB BUS DURING THIS ROUND

About 60 s after control 2's `echo stop` — **with the teardown complete and the modem successfully
re-attached** — the device vanished:

```
15:43:01  ok (uptime-check: 127)
15:44:01  ok (uptime-check: 187)
15:44:42  *** AP UNREACHABLE *** (3 consecutive failures; last ok 15:44:21)
```

`lsusb` on the host then showed **only the two root hubs**. This is **not a plain reset**: a reset
returned in **37 s** at 15:41:45 and re-enumerated.

> **§8.3 ADDENDUM (2026-09-21, after the device was recovered by a physical cold replug).** The host
> kernel log was read afterwards and it sharpens this materially — the dongle was **not** "gone from
> the USB bus", it was **attached but failing to enumerate**:
>
> ```
> 15:41:38  usb 1-1: new high-speed USB device number 4   idVendor=1d6b idProduct=0104
>           Product: USB Gadget / Manufacturer: Linux        <- it HAD come back
>           cdc_acm 1-1:1.0: ttyACM0 / cdc_ncm 1-1:1.2 usb0: register 'cdc_ncm'
> 15:44:25  usb 1-1: USB disconnect, device number 4
> 15:44:25  usb 1-1: new FULL-speed USB device number 5
>           device descriptor read/64, error -71   (x2)
> 15:44:26  usb usb1-port1: attempt power cycle
>           Device not responding to setup address.
>           device not accepting address 7, error -71
>           WARN: invalid context state for evaluate context command.
>           usb usb1-port1: unable to enumerate USB device
> ```
>
> Two corrections follow. (a) The device re-attaches at **full speed** (the working enumeration was
> high-speed) and cannot complete setup — the signature of a USB **gadget/PHY that came back broken**,
> not of a device that is off the bus. The hub driver already tried `attempt power cycle` and failed,
> so a host-side reset is not available; recovery needed a **physical cold replug** (a quick replug
> may not clear a stuck DWC3 PHY — unplug, wait ~15 s, replug). (b) **pstore was EMPTY after the
> replug** (`/sys/fs/pstore/` contained nothing), so **the AP did not panic**. An empty pstore is not
> conclusive on this device (it can mean an invalid buffer), but combined with "no USB, no ssh" it
> means the observable is *AP unreachable with a dead gadget*, and the cause remains unresolved.

Same presentation as Doc 168 §13f recorded after the 821 run B reset. **Recorded as a fact with an
unresolved cause, not as a finding.**

> **§8.3.1 A SECOND MANAGEMENT PATH EXISTS — the dongle runs its own WiFi AP, and it is now SET UP
> AND VERIFIED.** `phy0-ap0` is UP, bridged into `br-lan` (192.168.8.1/24), SSID **`OpenWrt`, open
> (no encryption)**, from `wcn36xx` on `a204000.remoteproc`. So the "OpenWrt" SSID visible from the
> build host is this device, not a neighbour. `CONFIG_QCOM_WCNSS_PIL` is unset in
> `target/linux/msm89xx/config-6.12` yet WiFi works, so the WCNSS firmware path is not the PIL one —
> **do not conclude "no WiFi" from that config symbol again.**
> **Why this matters:** the failure mode above kills the *USB gadget* while leaving the AP possibly
> alive. Every time the dongle "disappears", the WiFi AP is the one remaining way in — and it is the
> only way to distinguish "the AP hung" from "the gadget died". (Honest limit: if the AP *itself*
> hangs — the failure being chased — the WiFi AP dies with it. This is a **discriminator**, not a
> guaranteed rescue.)
>
> **Now configured and verified (2026-09-21).** A second adapter — **RTL8188FTV on `rtl8xxxu`**, a
> mainline in-kernel driver — shares the host's single USB port with the dongle via a 4-port hub:
> ```
> Bus 001 Port 001 -> Hub (4p)
>     |-- Port 001 -> RTL8188FTV (rtl8xxxu)          -> wlu1u1
>     |-- Port 002 -> HMU05 gadget (cdc_acm+cdc_ncm) -> enu1u2i2
> Bus 002 (10000M) -> free
> ```
> ```
> nmcli con add type wifi ifname wlu1u1 con-name hmu05-fallback ssid OpenWrt
> nmcli con modify hmu05-fallback ipv4.never-default yes ipv6.never-default yes \
>     ipv4.route-metric 5000 ipv6.route-metric 5000
> nmcli con up hmu05-fallback          # may need a second `up` for the DHCPv4 lease
> ```
> **`never-default yes` is the critical setting** — without it this AP's DHCP gateway steals the
> host's default route and the host loses its internet. **Verified both ways:**
> `ssh -b 192.168.8.102 root@192.168.8.1` → `IPV4-WIFI-PATH-OK`, and `ssh root@fd85:138e:7d31::1`
> (the dongle's IPv6 ULA) → `WIFI-PATH-OK`. Route metrics give **automatic failover with no script**:
> `192.168.8.0/24 dev enu1u2i2 metric 2000` (preferred) and `dev wlu1u1 metric 5000` (fallback) — when
> the USB interface disappears its route goes with it and `192.168.8.1` becomes reachable over WiFi.
> `autoconnect: yes`, so it survives host reboots.
>
> **⚠ The dongle's host interface was RENAMED `enu1i2` → `enu1u2i2`** by moving it behind the hub. Any
> script hardcoding the old name breaks; **address the dongle as `192.168.8.1`, never by interface
> name.** Two smaller gotchas: the first `nmcli con up` brought up **IPv6 only** (a second `up` got
> the DHCPv4 lease `192.168.8.102`), and the dongle's DHCP server also leases to `enu1u2i2`
> (`192.168.8.132`), so both interfaces sit on 192.168.8.0/24 — harmless, and the metric ordering
> above is what keeps the USB path preferred.

### §8.4 WHAT IS STILL SOLID AFTER THIS ROUND

* The **poller is measured** (§2.1) — `qmi-proxy` fd 7 → `/dev/wwan0qmi0`, the only wwan/rpmsg fd on
  the device, parked in `do_sys_poll`, the same function the corruption fires in.
* **`CONFIG_RPMSG_WWAN_CTRL=m`** and therefore a no-flash fix exists (§2.2).
* **Patch 823** is correct **as a fix for the UAF**: it removes the registration that creates the
  dangling entry, so the corruption becomes **unreachable** regardless of the race's probability.
  **This is a stronger argument than "5 clean runs"** — and it is worth stating that the fix does not
  depend on the race rate being high.
* **Patch 822** is verified in the compiled kernel (§3) and its bound is a real time bound.
* **Question B is answered** (§4 above / evidence `B_...txt`): the natural-fatal path runs the full
  teardown with 0 corruption.

### §8.5 THE HONEST SUMMARY

**The fix is justified by mechanism, not by a reproduction.** The reproduction rate on `echo stop`
is not what Doc 169 claimed; it is somewhere between 2/4 (the corpus) and 0/2 (here), and until that
rate is measured under a stated condition, **no run count can score it.** The right next step is to
find the forcing function — with **load** as the leading candidate — not to run five more and declare
victory.

---

### §8.6 THE DEPLOYABLE ARTIFACT — `./build.sh kernel` does NOT produce it

`./build.sh kernel` leaves `rpmsg_wwan_ctrl.ko` **unstripped** (56112 B). OpenWrt strips kernel
modules in the **packaging** step, not the kernel build, so a kernel-only build never yields the object
the image would contain — and "is what I deployed the same artifact the image ships?" is unanswerable.

The strip rule was found by **reading the build system**, not guessed:

```
rules.mk:383-390   RSTRIP= ... STRIP_KMOD="$(SCRIPT_DIR)/strip-kmod.sh" ...
scripts/strip-kmod.sh   objcopy -x -G __this_module --strip-unneeded \
                               -R .comment -R .note.gnu.build-id ...
```
with `NO_RENAME=1` set when `CONFIG_KERNEL_KALLSYMS=y` (it is), which skips the symbol-renaming pass.
`scratch/mkko823.sh` reproduces it and **validates itself against the shipped baseline**:

| object | md5 | size |
|---|---|---|
| input (unstripped, from `build_dir`) | `1457ad79afaeeb8c7d451ea895b10da3` | 56112 B |
| **output (stripped, deployable)** | **`6553acd4eb83fcf032c1cf0821f35f4d`** | **6808 B** |
| shipped baseline (the module that was on the device) | `cd46d8f45a74e6a1f77c0a136a862c49` | 6808 B |

**Size match ⇒ this is the recipe the build uses.** Without that check the recipe would be a guess.

**Verification is by CONTENT, not by symbol label.** `strip-kmod.sh` passes `objcopy -x`, which
discards **local** symbols, and `rpmsg_wwan_ctrl_tx_poll` is `static` — so the stripped object has no
label for it, **and neither does the shipped baseline**. A label-based grep therefore reports a
*false negative* (mine did, on the first attempt). Anchor on the `rpmsg_poll` relocation instead:

```
BASELINE  (cd46d8f4)           PATCH 823 (6553acd4)
  30: mov  x20, x2   <-save      34: add  x2, sp, #0x20    <- x2 = &no_wait
  3c: mov  x2, x20   <-pass      40: stp  xzr, xzr, [sp,#32] <- no_wait = {0,0}
  44: bl   <rpmsg_poll>          44: bl   <rpmsg_poll>
```

A full `objdump -d` diff of baseline vs patched is **18 lines and every one is inside this single
function** — nothing else in the module changed. Why that is *sufficient*: `rpmsg_poll()` hands its
`poll_table` to `poll_wait()`, which is a **no-op when `p->_qproc == NULL`**
(`include/linux/poll.h:42`), and a stack `poll_table` initialised to `{0,0}` has `_qproc == NULL`.
**No `poll_table_entry` is ever linked into `channel->fblockread_event` by this path**, so
`qcom_smd_edge_release()`'s bare `kfree(channel)` can no longer free a queue a userspace poller is
linked in. **The UAF is removed structurally, not narrowed.**

### §8.7 PATCH 823 IS DEPLOYED AND FUNCTIONALLY CLEAN (no kernel flash)

Deployed by module swap + reboot (`scratch/deploy823.sh`; `rmmod` is impossible because `qmi-proxy`
holds `/dev/wwan0qmi0`). Backup kept at `/overlay/modbackup/rpmsg_wwan_ctrl.ko.pre823`; revert with
`bash scratch/deploy823.sh --revert`. **`sysupgrade` was not used, so `/overlay` is untouched.**
`lsmod` → `rpmsg_wwan_ctrl 12288 0`.

Non-regression is the part that matters — 823 changes what `poll()` reports, so a fix that removes the
UAF and breaks `qmi-proxy` would look *clean* in a corruption count and be worthless. Measured on the
boot after the deploy:

| check | result |
|---|---|
| module on device | `6553acd4eb83fcf032c1cf0821f35f4d` (patched) |
| `qmi-proxy` | pid 5166, **1** wwan fd, `fd/7 -> /dev/wwan0qmi0`, `wchan=do_sys_poll` |
| `wwan0` | UP, `10.104.99.100/29` + a global v6 address |
| default route | `via 10.104.99.101 dev wwan0 proto static metric 10` |
| ping 8.8.8.8 | 4/4, 0% loss, 42.2/64.3/81.8 ms |
| DNS | `nslookup openwrt.org 8.8.8.8` → `2a03:b0c0:3:d0::1a51:c001` |
| **`echo stop` × 3** | **3 PASS / 0 FAIL / 0 SKIP** |
| **a NATURAL fatal** (`a2_power.c:1189` ×2 at AP 381.485 / 502.08; `a2_power.c:2949` at 896.52) | **3/3: `t0..t9` all present, 0 corruption, coredumps 14→16, AP survived, modem recovered, ping 2–3/3** |

The `echo stop` runs (`bash scratch/qa.sh 3 post823`) are the *targeted scenario* — the trigger that
produces the UAF — and they also exercise the one thing 823 could plausibly have broken: the poller's
ability to notice the teardown at all. Each run:

* **survived** (uptime monotonic, no reset);
* **0 `list_del corruption`**;
* teardown `T9` count stepped **1 → 2 → 3**, i.e. the full teardown ran on every run;
* **restored**: `rproc=running`, `qmi-proxy=5166` re-opened `/dev/wwan0qmi0` with **1** wwan fd.

**Read this honestly: 3/3 is NOT evidence that 823 fixed anything**, because the *unpatched* control was
also 0/2 on the same trigger (§8.1). Two rates of zero cannot be told apart. What 3/3 *does* establish
is **non-regression on the exact path the fix touches**, which is the thing that could have gone wrong.

**Honest limitation, recorded rather than hidden:** 823 removes entry 1 (the SMD flow-control queue)
but entry 0 (`port->waitqueue`, registered by `wwan_port_fops_poll`) remains, and `wwan_port_rx()`
wakes that queue, so **EPOLLIN is unaffected**. EPOLLOUT is unaffected in *value* because
`rpmsg_poll()` still **returns** the same mask — only the wakeup *source* is gone. The "obvious" fix
(deleting `.tx_poll` so the caller falls through to `else if (!is_write_blocked(port)) mask |=
EPOLLOUT`) would have been **wrong**: `is_write_blocked()` is dead code for this driver, so EPOLLOUT
would have become **unconditional**.

### §8.8 THE FIRST BOOT WITH 823 HUNG AT t=73.93 s — **RESOLVED: it is the known intermittent SSR-path hang, not a 823 regression**

> **RESOLUTION (same round, after the qa.sh runs — see `L_8_8_RESOLVED_…txt`).** The boot that ran
> `qa.sh 3 post823` supplies the base rate the verdict below was missing. Its complete modem timeline:
>
> ```
>  11.275 powering up          12.149 up        <- normal boot
> 211.046 timeout waiting for ssctl service   <- GRACEFUL stop  [qa.sh run 1]
> 211.130 stopped remote processor
> 229.872 powering up         230.470 up       <- qa.sh's restore
> 251.143 stopped remote processor            <- GRACEFUL stop  [qa.sh run 2]
> 269.886 powering up         270.490 up
> 291.053 stopped remote processor            <- GRACEFUL stop  [qa.sh run 3]
> 309.720 powering up         310.325 up
> 381.485 fatal error received: a2_power.c:1189   <- NATURAL FATAL
> 381.500 crash detected / recovering   381.588 stopped   382.912 up
> (485.58 still running, modem up, data working)
> ```
>
> The three stops are **my own `echo stop` runs** (qa.sh pre-uptimes 207.30 / 245.89 / 285.95).
> **All three ran the same sequence as this hang and all three recovered.** Two consequences:
>
> * **`timeout waiting for ssctl service` is the NORMAL signature of a graceful `rproc_stop()` on this
>   device**, not an anomaly. (It appeared on only one of the three stops — the `HZ/2` wait returns
>   immediately once `sysmon_start` has completed `ssctl_comp` — so **do not use its presence or
>   absence to count graceful stops.**)
> * **`modem pc-ack timeout during resume` also occurs normally and normally SELF-HEALS**, via patch
>   814's RX watchdog: `382.913 pc_state wait timeout` → `382.913 channels not initialized after
>   resume` → `383.027 RX watchdog: modem awake (pc line asserted) but no channels, rebuilding` →
>   recovered, data works. **The exact line that was the last thing §8.8 printed before its console
>   stopped is a line this device prints and survives.** What failed in §8.8 was not the pc-ack
>   timeout but the recovery that normally follows it.
>
> **So §8.8 is the pre-existing intermittent AP hang on the SSR path** — the failure Doc 167
> quantified at **~1 in 20-40 SSRs (3-5 %)**, which Doc 169/170 located at the teardown and Doc 159 at
> a later site. A rare race landing on that boot is far more parsimonious than a regression from an
> 18-line change that cannot call `rproc_shutdown`.
>
> **Revised verdict: 823 is SUBSTANTIALLY EXONERATED, not absolutely.** 1 hang in 4 graceful stops on
> this boot plus 1 on the previous. "Substantially" rather than "absolutely" because the §8.8 hang was
> on a *graceful* stop and the self-heal observed here was on a *fatal* recovery; the controlled boot
> with the unpatched module (`bash scratch/deploy823.sh --revert`) remains the clean discriminator and
> is **still not run**.
>
> **The trap this created, worth more than the verdict.** Both messages were read as fault markers
> *because they are rare in the corpus* — and the corpus is dominated by boots that died before
> reaching them. That is the mirror image of Doc 168 §5's trap: **a line that is rare in the corpus may
> simply be rare in the corpus, not rare in the kernel. Before treating a kernel message as a fault
> marker, establish its BASE RATE on a known-good run.** One `dmesg | grep -n` on a healthy boot is
> what produced the table above.

The boot immediately after the module swap reached **t=73.93 s** and the console **stopped**; the
device rebooted. Evidence: `console-ramoops-0` (24155 B) in
`evidence/.../H_first_823_boot_anomaly.txt`. The tail:

```
[72.752419] wcn36xx: ERROR hal_delete_sta_self response failed err=7
[73.452171] qcom-q6v5-mss: timeout waiting for ssctl service
[73.454181] bam_dmux: SSR teardown T1 entry        ... T5/T6/T7/T8/T9 all present
[73.932201] bam_dmux: modem pc-ack timeout during resume
<-- record ENDS.  No panic, no oops, no Call trace, no fatal error, no list_del
    corruption.  The console just stops: an AP hang, then the watchdog reboots.
```

**Read out of the tree we run** (not inferred):

* `qcom_sysmon.c:555` — `sysmon_stop()`, and the message is reached **only when `crashed == false`**
  (`if (crashed) return;` precedes it). So this was a **graceful `rproc_stop()`**, i.e. the
  `rproc_shutdown()` path — **not** crash recovery. The 500 ms (`HZ/2`) wait puts `sysmon_stop`'s
  start at ≈72.95 s.
* `qcom_bam_dmux.c:2070` — `bam_dmux_runtime_resume()`, a 250 ms wait for the modem's power-collapse
  ACK (our own instrumentation, patches 808–821).

**This is NOT the Doc 169/170 UAF**: that one reports `list_del corruption` and kills the AP inside the
poller's `poll_freewait()`. Here the teardown is **clean and complete** and the hang is in the
power-collapse/resume handshake afterwards — the Doc 159 family (AP hangs on an SSR), but with **no
`fatal error received` anywhere**, so it was not a modem fatal.

**It did not recur.** The very next boot — **also running 823** — has *none* of these messages
(`dmesg | grep -nE "ssctl|pc-ack|SSR teardown|fatal error|list_del"` → empty) and was healthy at
uptime 173 s, well past 73.93 s. So: **1 boot in 2 with 823.**

**823 is not excluded, and no mechanism has been established either way.** "823 cannot call
`rproc_shutdown`" is an argument from the patch's text, not a measurement — and this project was
burned once this round by inferring a mechanism from a pattern (Doc 166 §5.4). The clean
discriminator is a controlled comparison: boot with the unpatched module
(`bash scratch/deploy823.sh --revert`) and check whether the same
`ssctl → teardown → pc-ack` sequence appears. **That has not been run.**

**Two confounders found on this device, recorded for whoever runs it:**

1. **A device-specific script power-cycles the modem every 10 s.**
   `/usr/sbin/qcom-carrier-autocfg` (563 lines, `while … sleep 10`) drives the modem through
   ModemManager — `mmcli -m M --set-power-state-low` / `--set-power-state-on` / `-e`
   (`reset_baseband_cache()`), `--simple-disconnect`, `--delete-bearer`,
   `--3gpp-set-initial-eps-bearer-settings` — and can `reboot` the device itself (lines ~529, ~537)
   when it detects an MBN change. **A 10 s loop that power-cycles the modem through QMI is a live
   confounder for ANY modem-state experiment on this image, and it had not been catalogued before.**
2. **The device was carrying my own heavy instrumentation:** 5 instances of `q6trace_soak.sh`,
   3 of `beacon.sh`, `ssr_ledger.sh`, `coredump_watch.sh`, `ModemManager`, `mmcli -M`, `collectd`,
   `qcom-time-daemon`. Under §8's load hypothesis that makes the UAF *more* likely, not less — and it
   still did not reproduce in the two controls.

Also checked and **ruled out as the trigger**: nothing under `/overlay` writes remoteproc state. The
scripts only *read* it (`coredump_watch.sh:52,56,57` reads `.../remoteproc0/coredump`;
`ssr_ledger.sh:59` and `q6trace_soak.sh:61` read the bam-dmux `rx_telemetry`). Note this check was
done with the **device's** `grep` — `rg` is not installed there, so a host-side `rg` over ssh returns
nothing and reads as "not found".

### §8.9 TOOLING TRAP — `bash grep` IS NOT grep, AND `2>/dev/null` HIDES IT

The project's memory says *"the Grep tool is broken — use `bash grep`"*. **That advice is wrong and
dangerous.** `bash grep -rn PATTERN DIR` does not run grep: it runs **bash with `/usr/bin/grep` as the
script to interpret**, and fails with `cannot execute binary file`. With the usual `2>/dev/null` the
failure is *invisible* and indistinguishable from "no matches".

This bit me directly: I "established" that `timeout waiting for ssctl service` and
`pc-ack timeout during resume` **do not exist anywhere in linux-6.12.94**, and was one step from
writing that up as a finding. They are at **`qcom_sysmon.c:555`** and **`qcom_bam_dmux.c:2070`**.

Working on this host (aarch64, Asahi Fedora): **`rg`** (ripgrep 15.2.0) and **plain `grep`** (GNU 3.12,
aliased to `grep --color=auto`). **Never `bash grep`.** Two generalisable rules:

* **A search that returns nothing is evidence only if the search tool ran.** Verify a negative with a
  positive control in the same tree — `rg -c "bam_dmux" qcom_bam_dmux.c -> 315` takes two seconds and
  would have caught this immediately.
* **`2>/dev/null` on a search is a correctness hazard, not tidiness**: it makes "the tool could not
  run" produce the same output as "no matches", and those demand opposite conclusions.

---

### §8.10 AN UNEXPLAINED AP RESET WITH 823 DEPLOYED (2026-09-21 17:03) — 823 IS NOT A COMPLETE FIX

While this round was being written up, the host-side liveness watcher
(`evidence/164_q6v5_ssr_window_trace/host_hang_watch.log`) captured the boot that ran the §8.7
`qa.sh` runs through to its end:

```
16:40:47 *** AP UNREACHABLE ***  -> 16:41:43 AP-BACK after 53s
16:42:29 *** AP UNREACHABLE ***  -> 16:43:33 AP-BACK after 61s
16:44:14 *** AP UNREACHABLE ***  -> 16:45:02 AP-BACK after 45s
16:45:54 *** AP UNREACHABLE ***  -> 16:46:10 AP-BACK after 13s     <- deploy823.sh reboot + 3 more
16:47 .. 17:03  stable (uptime 79 -> 1042)                         <- qa.sh 3x PASS + 3 natural fatals
17:03:24 *** AP UNREACHABLE ***  -> 17:03:56 AP-BACK after 29s     <- AP uptime ~1061 s
```

The boot that reset at 17:03:24 is the **same boot** that produced §8.7's results. Its complete
fatal record, from the two independent persistent instruments, is unambiguous:

| instrument | record on that boot |
|---|---|
| `ssr_ledger.csv` rows 14/15/16 | `389.95 a2_power.c:1189` · `502.08 a2_power.c:1189` · `896.52 a2_power.c:2949` — **all with `t0..t9` present**, `0` corruption, `core=1` |
| `coredump_watch.log` | `devcd1` @382.41 s · `devcd2` @496.01 s · `devcd3` @888.08 s — **three dumps, 85 398 475 B each**, then the next line is the *new* boot's watcher start at 17.90 s |

So **three natural fatals ran their full SSR and all three recovered**, and then the AP reset
**~165 s after the third fatal's teardown had already completed**.

**What the evidence says — and what it does not.**

1. **pstore is EMPTY** (`/sys/fs/pstore/` has no `dmesg-ramoops-0`), while `ramoops` is registered and
   functional on this boot. A `list_del corruption` panics and *would* have left a record. Therefore
   this was **not a trappable kernel fault** — consistent with a **global stall → PMIC watchdog
   reset**, which is the Doc 159 picture ("stopped the whole AP"), not a fault the kernel could catch.
2. **No fourth coredump, no ledger row 17.** Both instruments are userspace and die with the AP, so
   this is *expected* if the AP hung during a fourth fatal's SSR — but it also means **the reset
   cannot be attributed to a fourth fatal from this data**; "no fatal" and "a fatal that hung before
   either instrument could record it" look identical here.
3. **823 does not cover this path.** 823 removes the `qmi-proxy` poller's `poll_table_entry` from
   `channel->fblockread_event`. The Doc 159 hang site is the **coredump-reclaim / `q6v5_rmb_mba_wait()`
   / TrustZone `qcom_scm_assign_mem()`** window, which 823 does not touch. The qmi-proxy poller was
   the **only** holder of a wwan fd at the time (`/proc/<pid>/fd/7 -> /dev/wwan0qmi0`, re-checked), so
   823's target vector *was* present and the reset happened anyway — **consistent with the reset being
   the other mechanism, but not proof of it.**

**Why the beacon could not characterise it, and the fix.** `beacon.sh` ran `: > beacon_a.txt` at every
boot, **destroying the previous boot's tail** — so the last advanced uptime before the reset, and the
A-vs-B divergence that separates "the FS/writeback path wedged" from "a global stall", were lost.
**Fixed this round:** `beacon.sh` now appends a `BOOT <uptime>s pid=… host=…` marker instead of
truncating (`scratch/beacon.sh`), and the running instance was replaced on the device. The pre-reset
tail will survive the next reset. The coredump watcher was **not** the gap — it is armed
(`/sys/class/remoteproc/remoteproc0/coredump` = `enabled`) and captured all three dumps.

**Confounders ruled out this round.**

* `/overlay` was at **48 %** (1.6 G free) with 16 coredumps (1.3 G) — not full, so no write-failure path.
* **`qcom-carrier-autocfg` did NOT reset the AP.** §9 trap #8's "power-cycles the modem every 10 s"
  is an **overstatement and is corrected here**: its `while … sleep 10` loop only *polls* `mmcli -L`
  in steady state. `mmcli --set-power-state-low/-on` (lines 268/270) runs only on an operator change,
  the initial boot, or a **radio-cache mismatch**; `reboot` (lines 523/538) runs only on an **MBN
  change**. At 17:03 there was no SIM swap and the boot-time provisioning had logged
  "No reboot required" at 11:01:36.

**Effect on the verdict.** Unchanged, and now stated with the caveat made concrete: **823 is
substantially but not absolutely exonerated, and it is not a complete fix.** The four-reset cluster
at 16:40–16:46 (only one of which the deploy script explains) plus the 17:03 reset show an
**AP-reset path that survives 823**. That path is the production problem, and patch 822 — or a fix
for the coredump-reclaim hang — is still required.

---

### §8.11 THE RESET MECHANISM IS THE PMIC PON WATCHDOG (30 s) — AND THE RATE IS NOT RARE

Chasing §8.10's undetermined cause produced two hard facts, both read from the device rather than
inferred, plus one measurement that is uncomfortable for the standing model.

**(1) The reset mechanism, named.** The AP does not panic — it **stops running**, and hardware resets it:

```
# cat /sys/class/watchdog/watchdog0/state    -> active
# cat /sys/class/watchdog/watchdog0/timeout  -> 30
# cat /sys/class/watchdog/watchdog0/identity -> QCOM PM8916 PON WDT
```

The PM8916 PON watchdog is **active with a 30 s timeout**, and something in userspace (procd) must
kick it every ≤30 s while the kernel is healthy. So **an empty pstore plus a reboot is exactly what a
≥30 s global stall looks like**: the kernel stops, the kick stops, the PMIC resets the SoC. This is the
mechanism §8.10 could only describe as "a global stall → PMIC watchdog reset" — and it also explains
the **shape** of every observed outage: the short ones (13–61 s) are `~30 s stall + ~15–40 s boot`,
two components, exactly as the timings in §8.10 show.

**(2) The rate.** The host watcher has logged 19 unreachable events; keeping only those where the AP's
uptime actually *decreased* (i.e. it really rebooted, rather than the USB gadget dropping) gives
**15 reboots in ~5 h**:

| uptime before the reset (s) | 5294 · 2427 · 2275 · 2250 · 1812 · 1614 · 1139 · 1042 · 897 · 395 · 307 · 220 · 187 · 164 · 126 |

A modem that fatals on its ~902 s idle timer produces roughly 20 SSRs in 5 h, so **taken at face value**
15 reboots in ~20 SSRs is nearer 1 in 1–3 than the 1 in 20–40 Doc 167 measured. **It should not be
taken at face value**, for three reasons — and the third kills the tidy explanation:

* **The pre/post-823 split.** **14 of the 15 reboots are pre-823** (12:02–16:42); the only post-823 one
  is 17:04 (§8.10). The current boot has since run **two consecutive `lte_ml1_sleepmgr_stm.c:4054`
  fatals at AP 919.62 and 1825.87 — a 906 s gap, i.e. the idle timer — and survived both**, `t0..t9`
  present, 0 corruption, coredumps 17/18. **So 823 may well have lowered the rate; n is far too small
  to claim it.**
* **The pre-823 window was also the heaviest manual-activity window** (echo-stop runs, deploys, soaks,
  the UAF A/B), so its rate is not a clean steady-state figure either.
* **Six of the fifteen reboots happened at an uptime below 500 s** (395 · 307 · 220 · 187 · 164 · 126),
  i.e. **before the modem could possibly have reached its first ~902 s idle-timer fatal**. Three of
  them (13:31:03 @897 s → 13:33:00 @126 s → 13:36:00 @164 s) are a **boot loop** — three reboots in
  five minutes — as is the 16:40–16:46 cluster. **A boot loop is not a steady-state hang, and counting
  it as one inflates the rate.**

**The tidy hypothesis is TESTED AND REJECTED.** If the reboot were "the AP hangs on the SSR of the n-th
idle-timer fatal", the reboot uptimes would sit at multiples of ~902 s. They do not:

| uptime before reset | 5294 | 2427 | 2275 | 2250 | 1812 | 1614 | 1139 | 1042 | 897 | 395 | 307 | 220 | 187 | 164 | 126 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| nearest n×902 | 5412 | 2706 | 2706 | 1804 | 1804 | 1804 | 902 | 902 | 902 | — | — | — | — | — | — |
| delta | −118 | −279 | −431 | +446 | **+8** | −190 | +237 | +140 | **−5** | — | — | — | — | — | — |

**Only 2 of 15 fall within ±20 s of a multiple of 902, and 6 of 15 cannot be one at all.** So the
reset is **not** simply "the n-th idle-timer fatal"; it is driven by the **variable, activity-correlated
fatals** (`a2_power.c:1189`/`:2949`, which fire anywhere in 68.5–941.3 s) or by something that is not a
fatal at all. **This is exactly the kind of clean-looking mechanism that has to be checked against the
data before it is written down** — and it failed the check.

**What is now true regardless.** The reset is a **≥30 s global stall**; the instruments that can name
the stall are deployed and **reboot-persistent** (a reset-durable beacon, §8.10, and a per-boot rolling
kernel log, §8.12); and the **next** stall is therefore the measurement that matters. Everything above
is rate *bookkeeping* — the instrument is what will actually identify the cause.

---

### §8.12 THE INSTRUMENT THAT WAS MISSING — a per-boot rolling kernel log

§8.11 leaves one question: **what does the kernel print in the last seconds before it stops?** Nothing
on the device answered it. `pstore` was empty (§8.10); `console-ramoops` is absent, not merely stale;
and the soak's own `/dev/kmsg` stream file (`/overlay/q6trace.log`) has an mtime of the boot, i.e. it
is **not being written** — its sibling `q6trace.csv` is current, so only the log path is dead.

Deployed instead (`scratch/dmesg_roll.sh`, autostarted from `/etc/rc.local`):

```sh
BOOTID=$(cut -c1-8 /proc/sys/kernel/random/boot_id)
F="$OUT/dmesg_roll_$BOOTID.txt"
ls -t "$OUT"/dmesg_roll_*.txt | tail -n +5 | while read -r old; do rm -f "$old"; done
while true; do
    dmesg | tail -n "$LINES" > "$F.tmp" && mv -f "$F.tmp" "$F"
    sync
    sleep "$INTERVAL"
done
```

Three deliberate choices, each of which is a mistake already made once in this project:

* **`dmesg` (the ring buffer), not `/dev/kmsg`.** A `/dev/kmsg` reader sleeps on `log_wait` when the
  ring is quiet, and a leaked `cat /dev/kmsg | …` pipeline has already cost this project a permanent
  fd. `dmesg` is a bounded read that always returns.
* **One file per BOOT, not one rolling file.** A single rolling file is overwritten within `INTERVAL`
  seconds of the next boot — so it destroys exactly the pre-hang tail it exists to capture. **That is
  the same self-erasing-instrument bug the beacon had (§8.10), and it is why the beacon's fix and this
  design share the `BOOT`-marker idea.**
* **`sync` on every write.** The tail must be durable before the stall, since nothing runs after it.

Verified live: `/overlay/dmesg_roll_a9fd907c.txt` (18673 B, rewritten every 5 s) ending at the SSR
powerup lines of the 919.62 s fatal. `rc.local` passes `sh -n` and the block is present.

**Together with §8.10's beacon, the next stall will be characterised rather than merely counted** —
the beacon gives the *when* and the A(sync)-vs-B(nosync) split, the rolling log gives the *what*.

---

### §8.13 EXPERIMENT DEPLOYED — the coredump capture sits INSIDE the SSR recovery, so it is turned OFF

While waiting for §8.12's instruments to catch a stall, reading the recovery path produced a
**mechanism-backed, no-flash, reversible intervention**.

**The code, read out of the live tree:**

```c
/* remoteproc_core.c:1792 */
static int rproc_boot_recovery(struct rproc *rproc)
{
        ret = rproc_stop(rproc, true);          /* 1. stop   */
        if (ret) return ret;
        rproc->ops->coredump(rproc);            /* 2. DUMP   <-- between stop and start */
        ret = request_firmware(...);
        ret = rproc_start(rproc, firmware_p);   /* 3. start  */
```

* For the MSS, **`ops->coredump` is the DEFAULT `rproc_coredump`** — `remoteproc_core.c:2426`
  installs it when the driver supplies none, and `q6v5_ops` (`qcom_q6v5_mss.c:1728`) has **no
  `.coredump` member**. (Grep first: the driver does *not* define one, so the generic one runs.)
* And `rproc_coredump()` **returns immediately** when the per-device attribute says disabled:
  ```c
  /* remoteproc_coredump.c:249 */
  if (list_empty(&rproc->dump_segments) || dump_conf == RPROC_COREDUMP_DISABLED)
          return;
  ```
* The per-device attribute **does accept `disabled`** (`remoteproc_sysfs.c:73-127`, `"disabled"` /
  `"enabled"` / `"inline"`; it refuses only while `state == RPROC_CRASHED`).
  **Two different sysfs locations — conflating them is easy and the corpus does not distinguish them.**
  The corpus note *"there is NO per-device `disabled`"* is about the **devcoredump** device
  (`/sys/class/devcoredump/devcdN/`, whose only per-device attribute is `data`) — **that remains
  true**. The **remoteproc** device has its **own, different** attribute,
  `/sys/class/remoteproc/remoteproc0/coredump`, which does accept `disabled`. They select different
  things: the remoteproc one says whether a dump is **produced at all**; the class-level
  `/sys/class/devcoredump/disabled` is a **write-once global lockdown** that kills every future dump
  and must never be written.

**Why this is the right thing to remove.** The step being skipped is not a passive copy:

* `qcom_q6v5_dump_segment()` is the **second caller of `q6v5_mba_load()`** (Doc 165) — i.e. a whole
  extra MBA power-up/handshake per fatal, in the middle of recovery;
* the dump reclaim is what emits **`port failed halt`**, and Doc 159 put the AP hang **43–47 ms after
  that line**; Doc 165 measured the 85 398 475 B synchronous copy at **1.064 s, on this path**;
* and it writes **85 MB per fatal to `/overlay`** — 18 dumps is already 1.5 GB of a 3.2 GB partition.

**Deployed** (`/etc/rc.local`, gated so it survives a reboot and is trivially reversible):

```sh
if [ -x /overlay/coredump_watch.sh ] && [ -f /overlay/coredump_ENABLE ]; then
        ... start the watcher (which re-arms the attribute every 2 s) ...
else
        echo disabled > /sys/class/remoteproc/remoteproc0/coredump 2>/dev/null
fi
```

Verified: `cat /sys/class/remoteproc/remoteproc0/coredump` → **`disabled`**; watcher procs **0**;
`sh -n /etc/rc.local` → OK; **re-enable with `touch /overlay/coredump_ENABLE`** (plus a reboot, or
just start the watcher — it re-arms within 2 s).

**PRE-REGISTRATION (written before the run, per the standing SOP rule).**
* **Prediction:** if the coredump path is the hang, the AP reboot rate falls to **zero**.
* **Bar:** **0 AP reboots across the next 20 fatals.** At the idle timer's ~902 s that is ~5 h of
  unattended running. *Justification for n=20:* the pre-823 rate was of order 1 reboot per 1–3 SSRs,
  so 20 clean fatals would be decisive; if the true post-823 rate were as low as 1 in 4, 20 fatals
  still gives a ~99.7 % chance of seeing at least one. **A single clean fatal proves nothing** — the
  current boot already survived two with the coredump *on*.
* **Control:** the pre-intervention series (§8.11) — explicitly **weak** (confounded by manual
  activity, boot loops, and pre-823). This is a *before/after*, not an A/B; an A/B (off → on → off)
  is the stronger design if the device time is available.
* **What would falsify it:** any reboot during the 20 fatal window, or a stall whose `dmesg_roll`
  tail shows the recovery had already passed the coredump step.
* **Cost, stated plainly:** no coredump is captured during the window, so a fatal in this period
  cannot be decoded via its ERR_FATAL descriptor (Doc 163). The fatal *signature* still lands in
  `dmesg_roll`, and the ledger still records the teardown stages, so the loss is bounded.

**Experiment start:** device UTC `2026-09-21T11:58:41Z`; marker at
`/overlay/coredump_off_experiment.txt`; baseline at that moment: uptime 2420 s, 2 fatals this boot
(both with the coredump still on).

---

## §9 Traps recorded this round

1. **A patch that "cannot need a flash" is a property of the config, not of the bug.** The first
   framing of the Doc 169 fix assumed a kernel flash. `CONFIG_RPMSG_WWAN_CTRL=m` was sitting in the
   live `.config` the whole time. **Grep the config before planning a deployment.**
2. **`sysupgrade` without `-n` preserves the overlay on this target; with `-n` it does not.**
   Read from the target's own `platform.sh`, not assumed.
3. **`is_write_blocked()` is dead code for this driver** — nothing calls `wwan_port_txoff()`/
   `txon()`, so the `else if` fallback would have silently made EPOLLOUT unconditional. The
   "obvious" fix (delete `.tx_poll`) would have been the wrong one, and only reading the callee
   showed it.
4. Carried from Doc 169 and still live: `console-ramoops-0` is **stale** after a non-crashing run;
   `console-ramoops` and `dmesg` have different loglevel filters.
5. **A kernel-only build does not produce a deployable kernel module.** The strip happens in the
   *packaging* step; `./build.sh kernel` leaves the `.ko` unstripped. Read `RSTRIP`/`strip-kmod.sh`
   and **validate the recipe against the shipped baseline by size** — otherwise "this is the artifact
   the image would contain" is an assumption (§8.6).
6. **Never verify a stripped object by symbol label.** `objcopy -x` discards local symbols, so a
   `static` function has no label in *either* the new object or the shipped one. Anchor on a
   **relocation** and diff the disassembly (§8.6).
7. **`bash grep` is not grep, and `2>/dev/null` turns its failure into a false negative.** A search
   that returns nothing is evidence only if the search tool ran — check a positive control (§8.9).
8. **A device-specific script can power-cycle the modem behind your back — but read the loop before
   believing it does so constantly.** `/usr/sbin/qcom-carrier-autocfg` runs `while … sleep 10` and can
   `reboot` the device (lines 523/538) and `mmcli --set-power-state-low/-on` (lines 268/270). **But it
   only does either on an operator change, the initial boot, a radio-cache mismatch, or an MBN change
   — in steady state it merely polls `mmcli -L`.** Catalogue the image's own automation before
   attributing modem state changes to your experiment, *and read the branch conditions* before
   concluding the automation is the cause (§8.10 corrects an earlier overstatement of this).
9. **"Gone from the USB bus" and "attached but failing to enumerate" are different observations, and
   only the host kernel log distinguishes them.** `lsusb` shows neither. Read `dmesg`/`journalctl -k`
   on the host before writing "the device is gone" (§8.3).
10. **Do not conclude "no WiFi" from `CONFIG_QCOM_WCNSS_PIL` being unset.** WiFi works on this device
    via a different WCNSS path, and its AP is the only management route that survives a dead USB
    gadget (§8.3.1).

---

## §10 What's next

* **RUNNING NOW: the §8.13 coredump-off experiment.** The bar is **0 AP reboots across 20 fatals**
  (~5 h at the idle timer). Re-enable the capture with `touch /overlay/coredump_ENABLE`. If it
  succeeds, this is a **production-viable fix** — the coredump is a debug feature, and disabling it
  also stops 85 MB/fatal being written to `/overlay`. If it fails, the `dmesg_roll` tail says whether
  the recovery had already passed the coredump step, which is itself informative.
* **Let §8.12's instruments catch a stall, then read the cause off it.** The reset is a **≥30 s global
  stall that the PMIC PON WDT (30 s) turns into a reboot** (§8.11). The beacon and the per-boot
  rolling kernel log are both deployed and reboot-persistent, so the next stall yields the *when*
  (beacon, plus the A(sync)-vs-B(nosync) split that separates a wedged writeback path from a global
  stall) and the *what* (the kernel's last lines). **Do not patch anything until that record exists**
  — §8.10 exists precisely because one reset was inferred rather than measured.
* **Then settle §8.11's remaining rate question — but only after the stall is characterised.** The
  "reboot at the n-th idle-timer fatal" model is already **rejected** (2/15 fit, 6/15 impossible), so
  the reset is **not** a simple function of the idle timer. What is still unmeasured is the clean
  **post-823** per-SSR hang rate, and the pre-823 number is unusable for it (boot loops + manual
  activity). Count it from the ledger's max-`n`-per-boot once a few more boots have run *without*
  manual intervention.
* **Re-check the boot loops.** Six of the fifteen reboots are at uptime <500 s, three of them a
  three-reboots-in-five-minutes loop (13:31–13:36); 16:40–16:46 is another. A boot loop is a different
  failure from a steady-state hang and deserves its own instrumentation window.
* **Finish scoring 823.** It is deployed and functionally clean (§8.7) and passed the `echo stop`
  scenario (§8.8). What is still missing is the **control rate**: `echo stop` × n with the
  **unpatched** module (`bash scratch/deploy823.sh --revert`) under a *stated* condition, to see
  whether the corruption is reachable at all here. Two controls gave 0/2; a third and fourth at 0/2
  do not change the conclusion, so the useful experiment is a **forcing function**, not more samples.
  Prime candidates, in order: (a) `echo stop` with `hp3.sh` armed (both of Doc 169's positives were
  taken under that load); (b) `echo stop` with several extra processes blocked in `ppoll()` on
  `/dev/wwan0qmi0`, which multiplies the entries that must unlink inside the ≤2.07 ms window.
* **§8.8 is resolved as far as it can be without the control** — the sequence that hung is one this
  device runs and survives routinely (§8.8 resolution box). The one remaining action is a controlled
  boot with the unpatched module to close it properly.
* **Patch 822 via a flash** (`sysupgrade`, which preserves `/overlay`) — it is the only fix that
  covers SMD pollers other than `qmi-proxy`. **Note §8.10 lowers its expected value**: 822 fixes the
  *UAF*, and §8.10's evidence points at a *different* (coredump-reclaim) hang. Flash 822 for the UAF;
  do not expect it to stop the 17:03 class of reset on its own.
* **Question C** — is the corruption the reset's *only* cause? **§8.10 has now answered "no" for at
  least one reset**, which is the strongest reason to stop treating 823/822 as the whole fix.
* **Then return to the ~902 s timer** — it is the *trigger* for the production path. Fixing the reset
  makes a fatal survivable; it does not stop the fatal. The standing user directive names both. Note
  the natural fatal in §8.8 was `a2_power.c:1189` at **71.76 s of modem uptime**, consistent with Doc
  162's non-clock taxonomy for that signature — the modem *does* fatal early, not only at ~902 s.
* Still open and unchanged: the 4 clean natural fatals (Doc 169 §6), the failed modem restart
  (Doc 154 §6), `bam_dmux_remove()`'s ABBA, and the `pm_wq`/`system_wq` double-queue of
  `tx_wakeup_work`.

---

## Evidence

* `evidence/169_smd_poll_use_after_free/C_poller_identity_and_module_scope.txt` — the measured poller
  identity and the module/builtin split.
* `evidence/170_two_fixes_for_the_smd_poll_uaf/`
  * `A_poller_in_do_sys_poll_measured.txt` — the live poller stack.
  * `B_question_b_natural_fatal_full_teardown.txt` — the natural fatal runs the complete teardown.
  * `D_patch_verification.txt` — both patches verified in the compiled objects.
  * `E_period_sample_900862s.txt` — the free periodicity sample.
  * `F_THE_CORRUPTION_IS_NOT_DETERMINISTIC_control_x2.txt` — the falsification.
  * **`G_patch823_deployed_and_validated.txt`** — the strip recipe, the 18-line disassembly diff, the
    deploy, and the non-regression measurements (§8.6, §8.7).
  * **`H_first_823_boot_anomaly.txt`** — the t=73.93 s hang, its two source sites, and what is and is
    not ruled out (§8.8); **`H2_console-ramoops-0_823_first_boot_raw.txt`** — the raw 24155 B record.
  * **`I_tooling_trap_bash_grep.txt`** — `bash grep` is not grep (§8.9).
  * **`J_qa_post823_3runs.log`** — `echo stop` × 3 with 823: 3 PASS / 0 FAIL / 0 SKIP.
  * **`K_qcom-carrier-autocfg_10s_modem_loop.sh`** — the image's own 10 s polling loop. **Its name is
    misleading and §8.10 corrects it**: the loop only *polls* in steady state; it power-cycles the
    modem only on an operator change / initial boot / radio-cache mismatch, and reboots only on an MBN
    change.
  * **`L_8_8_RESOLVED_and_natural_fatal_with_823.txt`** — the base-rate measurement that resolves §8.8,
    plus the second natural-fatal data point with 823 deployed.
  * **`M_post823_apreset_1703.txt`** — **the 17:03 AP reset with 823 deployed (§8.10): the full reset
    record, the three recovered fatals that preceded it, the empty pstore, the two confounders ruled
    out, the poller-identity re-check, and the beacon fix.**
  * **`O_watchdog_reset_mechanism_and_rate.txt`** — **§8.11/§8.12: the reset mechanism (PMIC PON WDT,
    30 s, active), the 15-reboot-in-5 h rate with its pre/post-823 split, the two consecutive
    idle-timer fatals the current boot survived, and the rolling-kernel-log instrument.**
  * **`S_coredump_off_experiment.txt`** — **§8.13: the recovery-path code, the per-device `disabled`
    finding, the deployment, and the pre-registered bar (0 reboots / 20 fatals).**
  * `R_analyze_hang_sh.sh`, `P_dmesg_roll_sh_per_boot_kernel_log.sh`,
    `Q_dev_state_mon_sh_host_sampler.sh` — the §8.12/§8.13 instruments.
  * `qa.sh`, `deploy823.sh`, `ssr_ledger_v2.sh`, **`mkko823.sh`** — the harnesses.
* `scratch/beacon.sh` — the **reset-durable** liveness beacon (§8.10): appends a `BOOT <uptime>s` marker
  instead of truncating, so the pre-reset tail survives the next reset. Copy kept at
  `evidence/170_two_fixes_for_the_smd_poll_uaf/N_beacon_sh_reset_durable.sh`.
* `scratch/dmesg_roll.sh` — the **per-boot rolling kernel log** (§8.12), autostarted from
  `/etc/rc.local`; `scratch/dev_state_mon.sh` — the host-side device-state sampler (§8.12). Copies kept
  at `evidence/170_two_fixes_for_the_smd_poll_uaf/P_…` and `Q_…`.
* `scratch/mkko823.sh` — produces and self-validates the deployable stripped module.
* `msm89xx/patches/822-rpmsg-smd-drain-pollers-before-free.patch` (md5 `2781b4bc2916dee0ae48dee1e7cf789a`, 85 lines)
* `msm89xx/patches/823-rpmsg-wwan-ctrl-no-smd-poll-registration.patch` (md5 `23c6a7a3e1222a881858f5ea1b733727`)
