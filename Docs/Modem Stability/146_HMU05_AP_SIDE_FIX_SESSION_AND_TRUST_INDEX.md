# Doc 146 — HMU05 AP-side session: RTNL oops root cause + fix, corpus trust index, and the Android-parity experiment

**Date:** 2026-09-20
**Device:** Melbon HMU05 4G dongle — MSM8916 + WTR1605 + QFE2320, OpenWrt 25.12.5 / Linux 6.12.94
**Modem firmware:** stock HMU05, `modem.mdt` md5 `1a6f9507e03d4ddbbf1977af81ecdbd7` (verified)
**Session type:** AP-side (driver + userspace). No baseband modification.

---

## 1. Purpose and scope

The owner's directive for this session:

> if port is not feasible, then we should work on hmu05 AP side … first replace the firmware with clean
> hmu05 modem firmware and wifi firmware and then fix AP side, there must be something either in driver
> or user space at AP side which prevents the crash or freeze … dont treat each one of them as facts,
> some findings are misleading, delete misleading docs if confirmed, follow SOP so that in next session,
> we would know, what we did, what was the result, whats next.

The RF-port line (Doc 145) is closed. This document records the AP-side work.

---

## 2. SOP compliance statement

| SOP step | Status |
| :--- | :--- |
| **Ground-truth firmware identity by hash** | **DONE.** `switch_modem_fw.sh verify` → `OK: active firmware set matches profile 'stock'`. Session began with the device in a mixed state (HMU05 modem + UFI001B `wcnss`/`cmnlib`/`keymaste`/`mba`) and ends fully stock. |
| **Dual-firmware comparative discipline** | **DONE (adapted).** The baseband is byte-identical Android vs OpenWrt, so the comparison performed is Android-downstream-source ↔ OpenWrt-driver, plus Android-decompiled-userspace ↔ OpenWrt-userspace. Both are recorded with file:line evidence. |
| **Verify at byte/instruction level before asserting** | **DONE.** The oops was resolved by disassembling the *deployed* `.ko` and reading the oops register dump against it, and by confirming struct offsets with `pahole -C sk_buff vmlinux`. Two previously-recorded claims were found wrong this way (§5.2, §6). |
| **Do not patch the baseband** | **HONOURED.** No `modem.*` file was modified this session. |
| **Measure, do not infer, where measurement is possible** | **DONE.** Fatal times, vote counts, suspend counts and the oops reproduction are all measured on the live device. |
| **Leave the next session able to continue** | **DONE.** This document; `README.md` trust index; `_QUARANTINE/`; updated memory records; the coredump watcher left armed; explicit "what's next" in §11. |

Steps **not** taken, and why: no QXDM/DIAG trace capture was attempted (the DIAG bridge is known to die on
every SSR); no NV/mcfg comparison was attempted (out of scope for the AP-side question).

---

## 3. Executive summary

1. **The `wwan0`-down oops is fixed and verified.** Root cause: `bam_dmux_send_cmd()` reserved a
   `tx_skbs[]` slot **before** calling `pm_runtime_get_sync()`; the resume path
   (`bam_dmux_pc_irq` → `bam_dmux_pm_restart`) then freed that slot, so `bam_dmux_skb_dma_map()`
   dereferenced `skb_dma->skb == NULL`. 8/8 down/up cycles now clean, RTNL intact, data recovered.
2. **The "~905 s periodic fatal" premise is retracted.** Fatal times vary (172.353 / 918.195 /
   1823.753 / 2729.267 s); one boot produced exactly one fatal at 172.353 s and none through 1156.95 s.
   It is a rate, not a period.
3. **The corpus was audited and 29 misleading documents quarantined**, with a new `README.md` trust index.
   The largest corrections: the backwards-autosuspend conclusion and the fixed-900 s-timer law.
4. **An Android userspace survey found the OpenWrt stack does the opposite of Android's idle behaviour.**
   Android *quiesces* AP-side polling while the bearer is dormant; OpenWrt added a 2 s DNS keepalive and
   a 60 s QMI time write. Both have now been removed as the next controlled experiment.
5. **A modem coredump is now armed.** The dump includes BSS (81.4 MB of `p_memsz` across 25 PT_LOAD
   segments), which is where the `a2_power` state lives. This is the ground truth the project has lacked.

---

## 4. What changed — exact artifacts

| Artifact | Before | After |
| :--- | :--- | :--- |
| `drivers/net/wwan/qcom_bam_dmux.c` | reserve-then-resume in `bam_dmux_send_cmd` and `bam_dmux_netdev_start_xmit` | **resume-then-reserve** in both; `pm_runtime_put_noidle()` on failed-get paths; `pc_state` guard in `send_cmd` |
| `qcom_bam_dmux.ko` | `da69a97d7ddf8e0932dde5ff041d82b6` | **`3b693188c3ba27deedeb7d1d1e53f76`** (on-device backup: `/root/qcom_bam_dmux.ko.orig-20260920T172948Z`) |
| `/etc/config/modem-watchdog` | `stall_timeout=15`, `check_interval=2`, `keepalive.enabled=1`, `keepalive.interval=2` | `stall_timeout=60`, `check_interval=10`, `keepalive.enabled=0`, `keepalive.interval=60` |
| `/etc/init.d/qcom-time-daemon` | `-r 60` (QMI `0x0020` write every 60 s) | **`-r 0`** (Android behaviour: no periodic write) |
| `/sys/class/remoteproc/remoteproc0/coredump` | `disabled` | **`enabled`** |
| `Docs/Modem Stability/README.md` | corpus index pointing at fabricated reports | **trust index** naming the retracted premises and the trustworthy core |
| `Docs/Modem Stability/_QUARANTINE/` | — | **29 misleading docs** + a README listing each false claim and its counter-evidence |

Repo copies of the watchdog config and the time-daemon init were updated too
(`openwrt/target/linux/msm89xx/base-files/etc/config/modem-watchdog`,
`msm89xx/base-files/etc/config/modem-watchdog`, and both `qcom-time-daemon.init` copies).

---

## 5. The `wwan0`-down oops: root cause, fix, verification

### 5.1 The race

`bam_dmux_send_cmd()` (drivers/net/wwan/qcom_bam_dmux.c:374) as deployed:

```c
skb_dma = bam_dmux_tx_queue(dmux, skb);        /* reserves tx_skbs[i].skb = skb */
ret = pm_runtime_get_sync(dmux->dev);          /* <-- sleeps; runs bam_dmux_runtime_resume() */
if (!bam_dmux_skb_dma_map(skb_dma, DMA_TO_DEVICE))   /* dereferences skb_dma->skb */
```

`pm_runtime_get_sync()` → `bam_dmux_runtime_resume()` → `bam_dmux_pc_vote(dmux, true)`. The modem then
asserts the A2 power-control line; `bam_dmux_pc_irq()` takes the wake transition and calls
`bam_dmux_pm_restart()`, which does:

```c
cancel_delayed_work_sync(&dmux->tx_retry_work);
atomic_long_set(&dmux->tx_deferred_skb, 0);
bam_dmux_free_skbs(dmux->tx_skbs, DMA_TO_DEVICE);   /* frees the skb just queued */
dmux->tx_next_skb = 0;                              /* why skb_dma == &tx_skbs[0] in the oops */
```

`bam_dmux_pm_quiesce()` terminates the TX DMA (line 1427) without freeing the skbs, precisely because
the terminated descriptors never invoke their completion callbacks — so `pm_restart()` has to free them.
But it also frees a slot that a concurrent sender had merely *reserved*. `bam_dmux_tx_queue()` guards
only `skb_dma->skb`; it never validates `dmux`.

`bam_dmux_netdev_start_xmit()` had the identical ordering bug with the asynchronous `pm_runtime_get()`.

### 5.2 Correcting the earlier decode

The earlier record said the NULL was `skb_dma->dmux` and that `0xc8` was `offsetof(struct bam_dmux, dev)`.
**Both were wrong.** Disassembling the deployed module:

```
bam_dmux_skb_dma_map:
 664: ldp x0, x1, [x0]      ; x0 = skb_dma->dmux, x1 = skb_dma->skb
 668: ldr x20, [x1, #200]   ; <-- FAULTING INSTRUCTION
 66c: ldr x22, [x0]         ; dmux->dev
 674: ldr w23, [x1, #112]   ; skb->len
 678: bl  is_vmalloc_addr
```

`pahole -C sk_buff vmlinux` in this tree: `len` @112, `head` @192, **`data` @200**, size 224.
So `0xc8` = `offsetof(struct sk_buff, data)`, and with `x1 = 0` the NULL is **`skb_dma->skb`**.
The oops register dump agrees: `x0 = ffff33704432d080` (a valid `dmux`), `x1 = 0`.

### 5.3 The fix

1. `bam_dmux_send_cmd()`: `pm_runtime_get_sync()` first, then `bam_dmux_tx_queue()`. Also refuses to
   queue when `pc_state` is false (the resume can return 0 after its handshake timed out, leaving the
   modem collapsed — the next wake transition would then free the slot anyway).
2. `bam_dmux_netdev_start_xmit()`: `pm_runtime_get()` first, then `bam_dmux_tx_queue()`.
3. Error paths call `pm_runtime_put_noidle()`. This is required: `__pm_runtime_resume()`
   (drivers/base/power/runtime.c) does `atomic_inc(&dev->power.usage_count)` **unconditionally**, before
   it can fail — so a bare `return` would leak a reference, pin `pm_usage_count` above zero, and
   permanently stop the `SMSM_A2_POWER_CONTROL` release.

Once the reference is held the device cannot be suspended, so `pm_restart()` cannot run again until the
slot is submitted. Verified in the rebuilt object: `__pm_runtime_resume` now precedes
`bam_dmux_tx_queue` in both functions.

### 5.4 Verification

The exact reproducer is `ubus call network.interface.modem down` (netifd → `__dev_change_flags` →
`bam_dmux_netdev_stop` → `send_cmd(CLOSE)`, all under RTNL).

| run | cycles | oopses | RTNL wedged | IP recovered | ping |
| :--- | ---: | ---: | :--- | :--- | :--- |
| before fix | 1 | 1 (`bam_dmux_skb_dma_map+0x20`) | yes, permanent | no | 100 % loss |
| after fix | 8 (6 of them under traffic) | **0** | **no** | **8/8** | **0 % loss** |

---

## 6. The "~905 s periodic fatal" premise is retracted

Recorded fatal times, all `a2_power.c:1189`:

| boot | times (AP uptime, s) |
| :--- | :--- |
| A | 918.195, 1823.753, 2729.267 (intervals 905.558 / 905.514) |
| B | ~706 |
| C (clean HMU05 firmware) | **172.353 only** — none at 1077.8 s, none through 1156.95 s |

Boot C is decisive: the 905.5 s model predicts a second fatal at ~1077.8 s and none occurred. The fault
time also moved 5× (918 → 172) between boots. There is no fixed firmware timer.

**Related measurement:** the AP's A2 power-collapse cadence is already Android-like —
`pc_vote_tx_count` ≈ 0.24/s on OpenWrt vs Android's measured 918 collapses / 3240 s = 0.283/s.
Collapse *churn* is therefore not the differentiator, and prior "drive the vote cadence to Android
rates" work was targeting a non-problem.

---

## 7. Corpus trust index and quarantine

A full audit classified all 105 files. **29 were moved to `_QUARANTINE/`** (moved, not deleted — several
were never committed to git, so deletion would have been irreversible). `_QUARANTINE/README.md` lists,
per file, the false claim and the measurement that contradicts it.

Three retracted premises dominate the misleading material:

1. **A fixed ~900 s timer** (Docs 78, 80, 81, 85, 88, `PHASE_5_…`, `MSM8916_912S_…`,
   `QUALCOMM_MSM8916_MODEM_TIME_SERVICE_REPORT.md`). Contradicted by §6.
2. **The 1000 ms autosuspend is the bug** (Doc 62 and the `Stock_Android_Analysis/17/39/40` chain,
   plus six top-level reports). Backwards — Doc 140 §6.
3. **Missing `QMI_WDS_GO_DORMANT` is the cause** (Docs 73, 84, 87). Falsified by Doc 75.

Also quarantined: the fabricated `MODEM_FIRMWARE_NO_SLEEP_PATCH_GUIDE.md`; false "deployed and verified"
claims (Docs 82, 83, `FINAL_…`, `MSM8916_Modem_Stability_Complete_…`, `MSM8916_Modem_15Minute_…`,
`MSM8916_HYBRID_…`, `Modem_Stability_Gap_…`); and the no-RF attach claims (Docs 126, 133).

The audit also produced **18 corpus self-contradictions**; the most damaging are Doc 77 (the time daemon
*fixes* the crash) vs Doc 86 (the same daemon *causes* it), and Doc 62 (autosuspend is a bug) vs
Doc 140 (autosuspend is the parity mechanism).

The trustworthy core is small and recent and is listed in `Docs/Modem Stability/README.md`.

---

## 8. What Android's userspace does that OpenWrt's does not

Findings from decompiled stock userspace plus the live Android device:

**Ruled out (important — these are the seductive answers):**

- **`dpmd` / `libdpmfdmgr` / `xt_HARDIDLETIMER` are NOT running on this device.**
  `persist.dpm.feature=0` ⇒ `[init.svc.dpmd]: [stopped]`, and `dpmd` is absent from the process list.
  The most attractive "missing mechanism" is a non-participant.
- **`time_daemon` is event-driven on Android**, not periodic: one `0x20` SET at boot, then
  `poll(…, -1)` (infinite) and a `0x21` GET only on a NITZ/tod indication. Confirmed in the decompile
  and by live strace. OpenWrt's `-r 60` was a **non-Android addition**, not a parity gap.

**Genuine differences (OpenWrt does the opposite):**

| Android | OpenWrt (before this session) |
| :--- | :--- |
| No periodic DNS keepalive; relies on modem DRX/paging | DNS query on `wwan0` every **2 s** while idle (`modem-bearer-watchdog`) |
| **Quiesces** AP polling while the bearer is DORMANT: `stopNetStatPoll()` **and** `stopDataStallAlarm()` | Never quiesces; 2 s counter poll + 2 s keepalive continue |
| Data-stall alarm 60 s aggressive / 180 s non-aggressive | `stall_timeout=15`, `check_interval=2` |
| `time_daemon` sends **no** periodic QMI write | `qcom-time-daemon -r 60` writes QMI `0x0020` every 60 s |
| Signal-strength poll (20 s) is **suppressed** on unsolicited indications (`mDontPollSignalStrength`) | `mmcli -m any --signal-setup=0` disables the poll outright |
| Registers a QMI WDS dormancy event report; the modem pushes ACTIVE↔DORMANT transitions | ModemManager's `event_report_indication_cb` is an empty stub (`mm-bearer-qmi.c:1434-1439`) |

**Counter-evidence the survey flagged honestly:** Doc 75 falsified dormancy as the cause of the 869 s
stall (the modem was `traffic-channel-active(2)` with `uplink_fc=FLOWING`). The dormancy gap may be
irrelevant. The survey rated its own "missing mechanism" pick at only ~35–45 % confidence.

---

## 9. The experiment now running

**Hypothesis under test:** the modem's `a2_power` assert is provoked by AP-initiated traffic/QMI during
idle periods. Android stays silent while the link is idle; OpenWrt did not.

**Change deployed (one coherent userspace change):**

- `modem-watchdog.keepalive.enabled = 0` — no more 2 s DNS keepalive.
- `modem-watchdog.general.stall_timeout = 60`, `check_interval = 10` — Android's DcTracker defaults.
- `qcom-time-daemon -r 0` — no 60 s QMI write.

**Why this is the right first experiment:** it is the one axis where OpenWrt is *qualitatively* the
opposite of Android, it is a userspace-only change, and it is trivially revertible.

**How it will be judged:** by fatal *rate* over a long soak, never by "the fatal landed at t = X".
Baseline for comparison: boot C had 1 fatal in 1157 s; earlier boots had 2–3 per hour.

**Simultaneously armed:** the coredump watcher, so if a fatal does occur we get the modem's memory
(§10) rather than another hypothesis.

---

## 10. Coredump capability (new)

`/sys/class/remoteproc/remoteproc0/coredump` is now `enabled`, and a watcher streams the ELF off-device
the moment a dump appears (devcoredump discards its buffer after ~5 minutes).

Feasibility checked before enabling:

- `q6v5_ops.parse_fw = qcom_q6v5_register_dump_segments` (qcom_q6v5_mss.c:1635) calls
  `rproc_coredump_add_custom_segment(…, phdr->p_memsz, qcom_q6v5_dump_segment, NULL)` — it uses
  **`p_memsz`, not `p_filesz`**, so BSS **is** included.
- The HMU05 `modem.mdt` has 25 PT_LOAD segments totalling **81.4 MB** of `p_memsz`
  (including the three zero-`filesz` BSS segments, idx 20/21/26).
- Device has ~236 MB free RAM and 2.9 GB free on `/overlay`.

This is the first time in the project that the modem's own state at the moment of the assert will be
observable rather than inferred.

---

## 11. What's next

**Immediate (next session):**

1. **Read the soak result** for the §9 experiment. Fatal count and times are in `dmesg`; the running
   monitor writes to `/tmp/hmu05_soak_fixed.log`. Compare fatal *rate* against the baseline.
2. **If a coredump was captured**, analyse it (`scratch/coredump_live/modem_coredump_up*.elf`). Priorities:
   the `a2_power` state block, the SMSM APPS word (item 85 word 1), the A2 client vote list, and the
   DRX/sleep counters around `FUN_c0ce7fe0`. Tooling already exists in `scratch/firmware/`.
3. **If the fault rate is unchanged**, the userspace hypothesis is dead and the next axis is the kernel
   driver's A2 handshake, not userspace.

**Ranked remaining AP-side candidates (none yet ruled out by measurement):**

| # | Candidate | Evidence | Risk |
| --- | :--- | :--- | :--- |
| 1 | `SMSM_A2_POWER_CONTROL_ACK` toggle pattern. Android toggles once per BAM connect/disconnect (`toggle_apps_ack()` at bam_dmux.c:1990/2055/2375); OpenWrt toggles on **every power-collapse edge** (pc_irq lines 1709/1727) — ~2× per cycle, ~2000/hour. The fatal is in the module that owns this handshake. | qualitative, from source | **high** — the OpenWrt resume waits on the ACK IRQ, so changing it can break the handshake |
| 2 | Resume handshake tolerance: OpenWrt waits **250 ms** for the pc-ack and proceeds on timeout; Android waits **2000 ms** and calls `ssrestart_check()`. | differential P2 | medium — lengthening the timeout is low-risk; adding restart-on-timeout is not |
| 3 | OPEN-signal negotiation: OpenWrt sends `signal = 0` and ignores the modem's OPEN signal; Android advertises DYNAMIC_MTU + DL pool + MTU and switches RX buffers 2 K↔4 K. | differential P6 | medium |
| 4 | RX drain model: Android disables the RX pipe IRQ on EOT and polls `sps_get_iovec` every ~3 ms; OpenWrt uses per-completion resubmit + a 200 ms rearm work + a 100 ms watchdog. | differential P5 | medium |
| 5 | Re-enable the watchdog's *detection* without its 2 s probing, and add an idle→dormant path (`QMI_WDS_GO_DORMANT`) as Android's FastDormancyService does at 3 s screen-off / 15 s screen-on. | §8 | low, but the payoff is unproven (Doc 75) |

**Explicitly closed — do not restart:**

- The WTR1605→UFI001B RF transplant (Docs 142/143/145).
- Any "fixed 900 s timer" model (§6).
- Disabling the bam-dmux autosuspend (§7).
- `MODEM_FIRMWARE_NO_SLEEP_PATCH_GUIDE.md` and the fabricated patch family.

---

## 12. Artifacts and evidence

| Path | Contents |
| :--- | :--- |
| `/tmp/hmu05_dmesg_401s.txt` | dmesg of boot C (single fatal at 172.353 s) |
| `/tmp/hmu05_soak.log` | boot C telemetry, 30 s cadence, to 1156.95 s |
| `/tmp/hmu05_soak_fixed.log` | post-fix soak telemetry (running) |
| `/tmp/coredump_capture.log` | coredump watcher log |
| `scratch/coredump_live/` | captured modem coredumps (empty so far) |
| `/tmp/new_bam.asm` | disassembly of the patched module |
| `scratch/ufi_rf_check/dmesg_stock_boot_rtnl_oops.txt` | the original oops capture |
| `Docs/Modem Stability/README.md` | trust index |
| `Docs/Modem Stability/_QUARANTINE/README.md` | per-file false claims and counter-evidence |

On-device rollback points:
`/root/qcom_bam_dmux.ko.orig-20260920T172948Z`,
`/root/modem-watchdog.bak-*`,
`/root/qcom-time-daemon.init.bak-*`.
