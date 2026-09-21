# Doc 170 — Two independent fixes for the SMD poll use-after-free: module-level (823) and kernel-level (822)

**Date:** 2026-09-21
**Predecessor:** Doc 169 (`169_THE_AP_RESET_IS_AN_SMD_POLL_USE_AFTER_FREE.md`)
**Status:** fixes written; **Question A pre-registered below, results appended after the runs.**

---

## §0 SOP compliance

| SOP step | Status |
|---|---|
| Read the *definition*, not the name | ✔ `wwan_port_fops_poll`, `rpmsg_poll`, `qcom_smd_poll`, `poll_wait`, `is_write_blocked`, `wwan_port_op_tx` all read in the live tree before writing anything |
| Verify against the running kernel, not against a doc | ✔ `CONFIG_RPMSG_WWAN_CTRL=m` and `CONFIG_RPMSG_QCOM_SMD=y` read from the **live** `.config`; the module's identity read from the **device** (`md5sum`, `lsmod`) |
| Grep the call sites | ✔ every caller of `rpmsg_poll` and every user of `tx_poll` enumerated |
| Instrument both sides of the hand-off | ✔ the poller is identified by **observation** (`/proc/<pid>/fd`), not inference |
| Pre-register before running | ✔ §7 |
| Comparative ground truth | ✔ the pre-fix control is Doc 168 run B + Doc 169 run A (2/2 corruption + reset) |
| One change per patch | ✔ 822 = one call in `qcom_smd.c`; 823 = one argument in `rpmsg_wwan_ctrl.c` |
| Nothing is a fact until measured | ✔ §2 is measurement; §3–§5 are mechanism, labelled as such |

---

## §1 Headline

Doc 169 proved the AP reset is a **use-after-free of `channel->fblockread_event`**, the wait queue
inside `struct qcom_smd_channel`: `qcom_smd_edge_release()` (`qcom_smd.c:1448`) `kfree()`s the
channel while a task blocked in `poll()` still holds a `poll_table_entry` linked in that queue.

**This round closes the one open item in that proof and finds a second, cheaper way to fix it.**

1. **The poller's identity is now measured, not inferred.** Doc 168 §2.4 said *"confirm the poller's
   identity (`/proc/<qmi-proxy pid>/fd`) before claiming it."* Done — see §2.
2. **The second poll registration lives in a LOADABLE MODULE**, so the reachable instance of the UAF
   can be removed **without a kernel flash** (patch 823).
3. Patch 822 (the in-kernel drain) remains the **structural** fix and is still the one that covers
   pollers other than qmi-proxy.

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

*(appended after the runs — see §8.x)*

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

---

## §10 What's next

* Score Question A (§7), then Question B via the flash.
* **Then return to the ~902 s timer** — it is the *trigger* for the production path. Fixing the reset
  makes a fatal survivable; it does not stop the fatal. The standing user directive names both.
* Still open and unchanged: the 4 clean natural fatals (Doc 169 §6), the failed modem restart
  (Doc 154 §6), `bam_dmux_remove()`'s ABBA, and the `pm_wq`/`system_wq` double-queue of
  `tx_wakeup_work`.

---

## Evidence

* `evidence/169_smd_poll_use_after_free/C_poller_identity_and_module_scope.txt` — the measured poller
  identity and the module/builtin split.
* `evidence/170_two_fixes_for_the_smd_poll_uaf/` — Question A runs.
* `msm89xx/patches/822-rpmsg-smd-drain-pollers-before-free.patch` (md5 `2781b4bc2916dee0ae48dee1e7cf789a`, 85 lines)
* `msm89xx/patches/823-rpmsg-wwan-ctrl-no-smd-poll-registration.patch` (md5 `23c6a7a3e1222a881858f5ea1b733727`)
