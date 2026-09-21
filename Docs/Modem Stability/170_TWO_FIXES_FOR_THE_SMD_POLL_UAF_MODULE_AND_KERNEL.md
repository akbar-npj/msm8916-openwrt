# Doc 170 — Two independent fixes for the SMD poll use-after-free: module-level (823) and kernel-level (822)

**Date:** 2026-09-21
**Predecessor:** Doc 169 (`169_THE_AP_RESET_IS_AN_SMD_POLL_USE_AFTER_FREE.md`)
**Status:** **patch 823 is DEPLOYED and functionally clean** (§8.7); the control failed to reproduce, so
§7's pre-registration is **withdrawn** (§8.2) and the fix stands on mechanism, not on a run count
(§8.6); one unexplained boot is recorded and **not** exculpated (§8.8). §8.11 names the reset mechanism
(**PMIC PON WDT, 30 s, active**) and §8.12 deploys the instrument that was missing.
**§8.13 turns the coredump capture OFF — and §8.13.1's first fatal is a clean within-boot A/B that
RETRACTS my own call-graph correction.** **⚠ §8.14 then withdraws §8.10's verdict and reframes §8.11's
rate: §8.10's "reset that survived 823" was my own physical USB-hub installation, the 16:40–16:46
"boot loop" is entangled with a host xHCI controller episode (**direction not established**), and
**7 of 19** outages are not clean AP-hang data points (1 manual, 2 provably not hangs by duration,
4 entangled) — so the reset rate is NOT an AP-hang rate and 823 is substantially rehabilitated.**
**§8.13.2 then makes the coredump-off A/B n = 2 (fatal #4 reproduces fatal #3 to within 5 ms), and
§8.15 reports the round's most interesting new result: every `a2_power.c:2949` fatal ever observed
(3/3, three boots) is preceded 74–86 ms earlier by a patch-808 "lost edge" PC resync — a correlation
with a tight lag and a plausible mechanism, explicitly NOT a cause (3 of 8 resyncs have no such
fatal), with a no-flash module discriminator designed and pre-registered.**
**§8.13.3 makes it n = 3 on a THIRD signature: three capture-OFF recoveries span 0.125291–0.130237 s
against 0.824902–0.831559 s for the two capture-ON — two non-overlapping populations 6.6× apart
(bar: 3 of 20 scored, 0 reboots). §8.15.1–§8.15.3 then DEMOTE the storm: the resync rate is not
stationary, P3's signature prediction FAILED, the 74–86 ms lag is `a2_power.c:2949`-specific
(sufficiency 3/20), and the storm ran 8+ minutes past the fatal with a fully working data plane and
NO external trigger on either side — so it is a failing PC-handshake REGIME, not a poison pill. The
~184 ms pairing of the two failure directions does not close arithmetically, which redefines the next
instrument (D1 = log the reinit/complete/IRQ ordering). S1 then tests whether 1 Hz traffic suppresses
the storm — i.e. whether the storm, the data stall and the handshake failures are ONE defect family
at the idle→active transition.**
**§8.13.4 adds fatal #6 (n = 4, 4 of 20 scored, 0 reboots) and CORRECTS the A/B metric to
`SSR before shutdown`→`MBA booted`. §8.15.4 then SCORES S1: P5 is CONFIRMED — 1 Hz traffic froze
`pc_resync_count` AND `pm_suspend_attempts` for 364.34 s (0 resyncs against 10.0 expected, 364/364
pings, 0 % loss) and the storm returned at the pre-S1 rate the moment the traffic stopped, a clean
A/B/A inside one run — so the STORM is AP-runtime-PM-gated. ⚠ BUT THE SAME RUN REFUTES THE MITIGATION
FOR THE FATALS: fatal #6 fired INSIDE the suppression window with the modem never suspended, on the
clock (902.286373 s of modem uptime, 19.4 ms from Doc 162's figure).**
**⚠ AND §8.15.5 WITHDRAWS §8.15.3 §5's "NO EXTERNAL TRIGGER" TO "OPEN": the device runs
`/usr/sbin/modem-bearer-watchdog`, which enforces the bam-dmux `power/control` and
`autosuspend_delay_ms` every 10 s via sysfs WITH NO LOG LINE and can also do `wds-go-dormant`, a
bearer down/up and a full remoteproc SSR — while `logread` is a ~15-minute ring buffer that had
already rotated past the onset. `/overlay/logroll.sh` is now deployed (userspace log + kernel log +
PM state machine on one timeline) and answers the NEXT onset, not this one. S2's first data also
shows the storm's cost is LATENCY (a `551.417 ms` first packet against a ~41–43 ms baseline), not
loss — so P6's predicate must be the TIMEOUT delta, not the resync delta.**
**★★ AND §8.16 IS THE ROUND'S MAIN DELIVERABLE: THE ANDROID DRIVER — which has been in the tree the
whole time at `GitIgnore/android_kernel_zte_msm8916/drivers/soc/qcom/bam_dmux.c` — was never diffed,
and it supplies the mechanism. The OpenWrt driver is ALSO SMSM-based (my first "GPIO IRQ vs SMSM"
reading was wrong and is self-corrected in place), and the clear-then-set ordering is equivalent — but
**the AP gives the modem's `pc-ack` line 250 ms where Android's `UL_WAKEUP_TIMEOUT_MS` gives 2000 ms**,
Android additionally serialises against the previous down-ack (`wait_for_ack`) and runs a
`UL_TIMEOUT_DELAY` recovery timer, and Android votes A2 power on demand where OpenWrt votes on every
runtime-PM resume. **The falsifier stated before checking is NOT falsified and the accounting is exact:
51 × the 250 ms wait + 2 × the 1000 ms wait = `pc_timeout_count` 53 (96.2 %).** The mechanism and the
measurement agree — giving up at 250 ms and proceeding IS the 416/551/398/431 ms RTT spike S2 measured.
**Scoped honestly: a LATENCY defect, not a stability one — it does not touch the 902 s fatal, which
§8.15.4 proved is not PM-gated.** §8.15.6 then locates the storm's onset exactly (**AP 3477.894835, the
boot's FIRST lost edge — dmesg ring verified not wrapped — 0 in 3477 s then 39 in 2600 s, spanning
seven modem reloads**) and shows the churn it rides on is Android-parity (Android suspends 1 per 3.5 s with 0 %
loss), so **the churn is not the defect; the 5 % handshake failure rate is.** §8.13.5 adds fatal #7
(**n = 5**, new minimum 0.119170 s), §8.17 records a **new signature `a2_task.c:3179`** cascading at
120.76 s with a **negative** antecedent and a storm-rate "jump" that is **not** a precursor, and §8.18
scores S2 (**all four losses are SSR outages**) and reports `logroll`'s first catch plus its batched-
stream limitation.**
**★★ §8.19 THEN FINDS THE MODEM HAS LEFT THE 902 s CLOCK — a 3-beat CASCADE at 120.76 / 166.60 /
182.73 s with `a2_task.c:3179` repeating — and captures the RPM's OWN log across it, showing a 13.24 s
MODEM-SIDE vote stall whose timestamp arithmetic proves the stall is UPSTREAM of the RPM. It then
RESOLVES the cascade: fatal #11 returned to the clock at 903.332309 s after #10, **predicted to within
1.04 s**, so a cascade is a **bounded departure** and the "902 s clock" is really **"902 s of modem
uptime"**. §8.20 ANSWERS the round's discriminating question **NEGATIVE** — the RPM stall does **NOT**
precede a fatal (nearest quiet 1.268 / 1.145 / 3.141 s = the normal cadence; the three ≥ 8 s stalls all
RECOVER and none is immediately before a fatal) — and records that the RPM's log is **BURSTY**, so
"quiet" must be judged against the **local** cadence, with **49 ring overruns losing 22 664 records** as
a standing caveat. §8.21 shows the **STORM is bounded too** (AP 3477.894835 → 7417.446456 = **3939.55 s**,
then **739 s silent across ~94 suspends INCLUDING across fatal #11's full modem reload**), which
**REFUTES "the storm is AP-runtime-PM-gated" as §8.15.4 stated it: the churn is necessary but not
sufficient.** §8.19(c) also **CORRECTS S2 to 58 samples / 9 losses** (the silent class is **n = 3**, not
2 — 6130.44 was misclassified) and makes the §8.16 counter accounting **EXACT at 100 %** (81 = 81;
78 = 76 + 2).**
**§8.13.6 then adds fatal #12 (sleepmgr, AP `8385.263928`) — **n = 8**, recovery **`0.122951 s`**,
predicted from fatal #11's modem boot and **missed by 1.64 s**, so **the modem is still on the clock**
two fatals after the cascade ended; and the storm silence, still unbroken, now spans **TWO** modem
reloads (§8.21 updated).**

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
| Check the *other side* of an attribution before publishing it | ✔ **§8.14 (this round's biggest correction)**: §8.10 blamed the AP for a 17:03 reset. The **host kernel log** shows the dongle disconnecting from port `1-1` at `17:03:07`, a **`USB2.0 HUB` appearing on `1-1` for the first time at `17:03:13`**, and the dongle returning at `1-1.2` at `17:03:41` — a **physical intervention by me**. §8.10's verdict on 823 is withdrawn. The same log shows **24 host xHCI remove/re-probe cycles** (18 in one hour) and **39** dongle re-enumerations against ~19 AP uptime-decreases ⇒ *a device-side uptime decrease proves the device restarted, not that it restarted itself.* **And the follow-up lesson: §8.14's own first draft labelled four more outages "host-caused" on a correlation; reading the ORDERING (mixed — the dongle usually disconnects first) forced that back to "entangled, direction not established". A correlation with a plausible upstream cause is still not a direction.** |
| Read the guarded BRANCH, not the call graph | ✔ **§8.13/§8.13.1**: I argued from the call graph that `port failed halt` must survive the coredump being disabled (because `q6v5_mba_reclaim()` is also reached via `q6v5_stop()`). The measurement falsified it — `q6v5proc_halt_axi_port()` opens with `if (!ret && val) return;` and the port is idle in the STOP path but **live** in the DUMP path. **A call site being reached is not evidence its error branch executes** (§9 trap 11). |
| **A negative is only as wide as the instrument class you searched — and check its horizon** | ✔ **§8.15.5 (this round's second self-correction)**: §8.15.3 §5 published "no external trigger" from two *true* negatives (empty `dmesg`, silent host log). The device runs `/usr/sbin/modem-bearer-watchdog`, which **writes the bam-dmux `power/control` and `autosuspend_delay_ms` every 10 s with no log line**, and `logread` is a **~15-minute ring buffer** that had already rotated past the onset. **The conclusion was withdrawn to "OPEN", and `/overlay/logroll.sh` was deployed to put the userspace log, the kernel log and the PM state machine on one timeline.** |
| Cross-check a rate against *every* instrument that could contradict it | ✔ **§8.14**: §8.11 fit its watchdog model to the 13–61 s outages and never noticed the distribution has a **second mode** — a **1641 s** and a **926 s** outage no 30 s stall can produce. Checking the durations for a second mode is now part of reading any rate. |
| Check the **provenance of the sample** before counting it | ✔ **§8.15**: the two extra `a2_power.c:2949` samples came from `/overlay/q6trace.log`, which is an **append-across-boots** file, not one boot — so each was cross-checked against the independent `ssr_ledger.csv` (**6 of 6 match**, 0–10 s lag) and the two boots were separated by their own `n = 1,2,3` sequences and the ledger's monotonic `coredumps` column (11,12,13 then 14,15,16). `grep -c` on that file returns **5** for **3 distinct events**. |
| Report **sufficiency as well as necessity** — and do not act on the necessity alone | ✔ **§8.15**: the "lost edge" resync precedes every `a2_power.c:2949` (**3/3**) *and* occurs 5 more times with no such fatal (**3/8**). Both numbers are recorded, the counter-examples are tabulated, and the three competing readings are left **open** with a discriminator design rather than a conclusion. A tight 74–86 ms lag makes the correlation look like a cause; the denominator is what keeps it honest. |

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
7. ~~**And the headline that matters for production: an AP reset occurred with 823 deployed** (§8.10)~~
    — **WITHDRAWN BY §8.14. That reset was my own physical installation of a USB hub** (host log: the
    dongle leaves port `1-1` at `17:03:07`, a `USB2.0 HUB` appears on `1-1` for the first time at
    `17:03:13`, the dongle returns at `1-1.2` at `17:03:41`). The 29 s outage **is** the unplug/replug.
    **823 is substantially rehabilitated**, and §8.10's verdict on it is withdrawn.
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
9. **THE RATE ITSELF IS REFRAMED, AND §8.11's NUMBER IS WITHDRAWN (§8.14).** Reading the **host** kernel
    log — the instrument nobody had checked — shows **error in both directions**. **7 of the 19 outages
    are not clean AP-hang data points**: **1 is certainly my hub installation** (17:03), **2 are
    certainly not hangs by duration** (1641 s and 926 s — the PON watchdog is kicked by `procd`, so a
    *stopped* kernel resets in 30 s while a broken *network path* never does; they are network-path
    failures with a live AP), and **4 are entangled with a host USB-controller storm** (the 16:40–16:46
    cluster, inside the hour holding **18 of the day's 24 xHCI remove/re-probe cycles** — and the
    **direction of causation is NOT established**, since in most episodes the dongle disconnects first
    while at 15:59:28 the controller is removed with no device disconnect at all). The remaining **12**
    are the only AP-hang candidates. And the durations are **bimodal**, which §8.11's single-mode model
    cannot cover. Conversely the host saw **39** dongle re-enumerations against ~19 AP uptime-decreases,
    so *re-enumeration ≠ AP reboot*.
    **⇒ an AP-side uptime decrease proves the dongle restarted, NOT that it restarted itself.** The
    host log is now a required companion instrument for every reboot attributed to the AP.
10. **AND THE ROUND'S OWN BEST SELF-CORRECTION IS §8.13.1.** The coredump capture is a step *inside*
    every SSR recovery, so it was turned off — and the first fatal with it off fired **on schedule
    (predicted within 0.01 s)**, recovered fully, produced no dump, and gave a clean **within-boot A/B**
    against the fatal before it: **`port failed halt` present → ABSENT, half-cycle pairs 2 → 1, recovery
    0.8316 s → 0.1253 s.** That measurement **retracted my own call-graph correction** and produced the
    round's sharpest transferable rule: **a call site being reached is not evidence that its error
    branch executes.**
11. **THE A/B IS NOW n = 2, AND THE SECOND SAMPLE CAME FROM A DIFFERENT FATAL SIGNATURE (§8.13.2).**
    Fatal #4 (AP **3477.969305 s**, `a2_power.c:2949`) reproduces fatal #3 exactly: **one** half-cycle
    pair, **no** `port failed halt`, **one** `MBA booted`, `SSR before shutdown` → `MBA booted`
    **0.130237 s** against **0.824902 / 0.831559 s** for the two with the capture on. Boot-wide:
    **4 fatals, 5 `MBA booted` (cold boot + one per recovery), 2 `port failed halt`** — exactly the two
    with the capture **on**. A **second, independent instrument agrees**: the ledger's `coredumps`
    column (written by a separate 10 s poller, not the driver) reads **17, 18, 18, 18** across fatals
    #1–#4 — fatal #1 took it 16→17, fatal #2 17→18, and #3 and #4 produced **no dump at all**.
    **Scored: 3 of 20 post-disable fatals, 0 AP reboots.** Fatal #5 (`a2_power.c:1189`, AP
    4304.991306 s) is a **third signature** and reproduces it again — **0.124531 s** — so the
    capture-off recoveries now span **0.125291–0.130237 s (spread 5.0 ms)** against
    **0.824902–0.831559 s (spread 6.7 ms)** for the two with it on: two **non-overlapping**
    populations **6.6× apart** (§8.13.3).
12. **NEW, AND THE FIRST AP-SIDE HANDLE ON A MODEM FATAL — EVERY `a2_power.c:2949` IS PRECEDED BY A
    "LOST EDGE" RESYNC 74–86 ms EARLIER (3 of 3, in three different boots) (§8.15).** Fatal #4's own
    window contains an extra line the other three fatals do not have: the bam-dmux RX watchdog
    reporting `PC line asserted while pc_state=0 (lost edge), resyncing` **74.470 ms** before the
    fatal, and `pc_resync_count` went **0 → 1** for the boot. Searching the preserved logs found the
    same pair in two earlier boots — **85.797 ms** and **73.955 ms**, mean **78.074 ms**, spread
    **11.842 ms** (the two closest are **0.515 ms** apart). **But it is not sufficient, and P2 has now
    measured how far from sufficient it is:** with this boot's six resyncs pooled against P/Q/R/S,
    **15 resyncs were observed and only 3 were followed by this fatal within 100 ms**, so
    *`a2_power.c:2949` ⇒ resync* is **3/3** while *resync ⇒ `a2_power.c:2949`* is **3/15** (§8.15.1/§8.15.2) — a **5×** gap. **And it does not generalise:** fatal #5 (`a2_power.c:1189`) has no resync within 100 ms (nearest 80.48 s earlier), so the tight lag is **specific to `a2_power.c:2949`**.
    **And the rate is not stationary:** 1 resync in the first 3478 s, then **5 in 145 s** starting at
    4079 s with no AP-side change — and **P3's signature prediction FAILED**: it named a sleepmgr at ~4380 s, the fatal was an `a2_power.c:1189` at 4304.99 s, and the storm **outlived** it by 47.5 s, so the storm **brackets** the fatal rather than preceding it. **The reframe that came out of it:** lining up both failure messages shows the storm is the **same PC handshake failing in BOTH directions** — the AP missing the modem's edges (`lost edge`) *and* the modem missing the AP's votes (`pc-ack timeout`) — a **minutes-long regime** measurable from two existing counters with no new instrumentation. **D2/D3 should be scored on the storm, not on a single resync.**
    The resync is **patch-808 code** (not upstream) and two of its four actions are modem-visible
    (`bam_dmux_pm_restart()` and `bam_dmux_pc_ack()`), so an AP-side driver action is on the causal
    path under one of three readings — **and `qcom_bam_dmux` is a LOADABLE MODULE (241 KB `.ko`), so
    the discriminating experiment needs NO kernel flash** (§8.15, D2/D3). **Not yet a claim:** the
    sleepmgr:4054 and a2_power.c:1189 fatals have **never** been seen with a resync in front of them.

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

> **⚠ THIS SECTION'S VERDICT IS WITHDRAWN — READ §8.14 FIRST.** The 17:03 reset it rests on was
> **my own physical installation of a USB hub**: the host log shows the dongle disconnecting from port
> `1-1` at `17:03:07`, a **`USB2.0 HUB` appearing on `1-1` for the first time at `17:03:13`**, and the
> dongle re-appearing at `1-1.2` at `17:03:41` — and a hub cannot be added to a port without first
> removing the device on it. The 29 s outage recorded below **is** that unplug/replug window. The
> empty pstore / no-4th-coredump / no-ledger-row evidence is **exactly what a power cycle produces**,
> and I read it as an AP hang. **So this section does NOT show that 823 is incomplete.** The rest of
> the section is retained because the *record* is accurate — only the attribution was wrong. The
> **16:40–16:46 cluster at the top of this section is likewise host-caused** (xHCI ×4/×12/×24 and
> root-hub disconnects immediately before each outage; 36 of the day's 48 xHCI re-registrations fall
> in that hour). See §8.14 for the full reframing and the 6-of-19 classification.

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

> **⚠ THE RATE IN THIS SECTION IS NOT AN AP-HANG RATE — READ §8.14 FIRST.** The watchdog mechanism
> itself stands (an empty pstore + a reboot *is* what a ≥30 s global stall looks like, and the PM8916
> PON WDT is active with a 30 s timeout). But the **count** below mixes three different things:
> **4 outages preceded by host xHCI controller resets** (the 16:40–16:46 cluster, where 36 of the
> day's 48 xHCI re-registrations fall), **1 USB-PHY failure** (15:44:42, `error -71` ×6 +
> `attempt power cycle`, a 926 s outage), and **1 manual hub installation** (17:03:24). **At least 6 of
> the 19 outages are not AP hangs**, so "~1 in 1–3 SSRs" is unsupported. The `~30 s stall + ~15–40 s
> boot` decomposition also fits **only the short mode**: the durations are **bimodal**, with a
> **1641 s** and a **926 s** outage that no 30 s stall can produce. Re-derive the rate with the host
> log as a filter. **The mechanism claim survives; the number does not.**

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

**What the skipped step actually contains — read line by line, because the first draft of this
section overclaimed.** `qcom_q6v5_dump_segment()` (`qcom_q6v5_mss.c:1575-1619`) does:

```c
if (!qproc->dump_mba_loaded) {
        ret = q6v5_reload_mba(rproc);          /* -> q6v5_load() + q6v5_mba_load()  (2nd MBA load) */
        q6v5_xfer_mem_ownership(... mpss ...);
}
ptr = memremap(qproc->mpss_phys + offset + cp_offset, size, MEMREMAP_WC);
memcpy(dest, ptr, size);                        /* the 85 MB copy */
if (qproc->current_dump_size == qproc->total_dump_size) {
        q6v5_xfer_mem_ownership(... back to Q6 ...);
        q6v5_mba_reclaim(qproc);                /* a SECOND reclaim */
}
```

So disabling the coredump removes **a second full MBA load, the 85 MB copy, and a second MBA
reclaim** from the middle of every SSR recovery.

**MY OWN CORRECTION WAS ITSELF WRONG, AND THE FIRST FATAL OF THE EXPERIMENT PROVED IT.** The second
draft of this section argued that because `q6v5_mba_reclaim()` is also called from `q6v5_stop()`
(`:1672`) — which `rproc_stop()` runs unconditionally — **`port failed halt` must still print with the
coredump disabled**, so the Doc 159 window would survive. **It does not print.** The reasoning failed
because it was taken from the **call graph**, and the message is guarded by a **branch on runtime
state**, not by whether the call site is reached:

```c
static void q6v5proc_halt_axi_port(struct q6v5 *qproc, struct regmap *halt_map, u32 offset)
{
        /* Check if we're already idle */
        ret = regmap_read(halt_map, offset + AXI_IDLE_REG, &val);
        if (!ret && val)
                return;                       /* <-- EARLY RETURN: no message at all */
        ...
        if (ret || !val)
                dev_err(qproc->dev, "port failed halt\n");   /* only if the halt FAILS */
}
```
(`qcom_q6v5_mss.c:961-975`)

* **In the STOP path** the modem has just crashed and been reset, so the AXI port is **already idle**
  ⇒ the early return fires ⇒ **silent**.
* **In the DUMP path** `q6v5_reload_mba()` has *just re-loaded and re-started the MBA*, so
  `q6v5_mba_reclaim()` is halting the AXI port of a **LIVE modem** ⇒ the halt times out ⇒
  **`port failed halt`**.

So the message means **"the reclaim ran against a live modem"**, which happens **only in the dump
path**. *A call site being reached is not evidence that its error branch executes.*

### §8.13.1 RESULT — first fatal with the capture OFF, and it is a clean within-boot A/B

Fatal #3 of the current boot, AP **2718.436833 s** (`lte_ml1_sleepmgr_stm.c:4054`) — **predicted** from
the previous gap at AP ≈ 2718.45 s, i.e. **within 0.01 s**. The two recoveries sit in the same boot, on
the same firmware, minutes apart, differing only in the coredump setting:

| | fatal #2 — capture **ON** (1816.046634) | fatal #3 — capture **OFF** (2718.436833) |
|---|---|---|
| `recovering` | 1816.062669 | 2718.452876 |
| `SSR before shutdown` | 1816.069773 | 2718.459733 |
| trace `01-09` (reclaim) | 1816.147194 | 2718.539571 |
| `stopped remote processor` | 1816.147432 | 2718.539856 |
| trace `10-23` (MBA load) | 1816.166714 | 2718.540745 |
| **`port failed halt`** | **1816.854035** | **ABSENT** |
| trace `01-09` (2nd reclaim) | 1816.854135 | **ABSENT** |
| trace `10-23` (2nd load) | 1816.856197 | **ABSENT** |
| `MBA booted` | 1816.901332 | 2718.585024 |
| half-cycle pairs | **2** (`01-09,10-23,01-09,10-23`) | **1** (`01-09,10-23`) |
| `SSR before shutdown` → `MBA booted` | **0.8316 s** | **0.1253 s** |

Boot-wide counts confirm the pattern is not an artefact of one window: **3 fatals, 4 `MBA booted`
(one being the cold boot), 2 `port failed halt`** — the two fatals with the capture on, none for the
one with it off. `qcom_q6v5_dump_segment()` also gates the second load on `if (!qproc->dump_mba_loaded)`
and the second reclaim on `current_dump_size == total_dump_size`, so with the capture off **both**
branches are skipped — the `dump_mba_loaded` flag is the mechanism, exactly as Doc 165 described.

**Measured effect of disabling the capture:**
1. one **whole extra `q6v5_mba_load()`** (trace 10-23) is removed from the recovery;
2. the **85 MB synchronous copy** is removed (Doc 165 measured 1.064 s for it on this path);
3. one **`q6v5_mba_reclaim()`** is removed;
4. **`port failed halt` itself disappears** — and with it the exact two-printk window Doc 159 bounded
   the hang by;
5. **85 MB/fatal** stops being written to `/overlay`;
6. net effect on this pair: the `SSR before shutdown` → `MBA booted` span falls **0.8316 s → 0.1253 s
   (0.706 s faster)**, and the boot's `stopped remote processor` → `MBA booted` span is **44.3 ms**,
   the same order as the 43–47 ms Doc 159 measured.

**What this does NOT remove — and it matters for the prediction.** The restart's own `q6v5_mba_load()`
still runs, and its untraced tail is still there: trace 23 → `MBA booted` = **39.2 ms**, which is
`q6v5_rmb_mba_wait(qproc, 0, 5000)` — the call Doc 165 identified as **86.2 % of the 43.968 ms window**.
So a **same-duration window of the same kind survives**, merely relocated from
`port failed halt`→`MBA booted` to `stopped remote processor`→`MBA booted`. `q6v5_rmb_mba_wait()` is
**bounded** (`msleep(1)` + `time_after`, 5000 ms → `-ETIMEDOUT` → `MBA boot timed out`), so it cannot
hang forever — but if the AP hang lives inside *it*, this experiment will not remove the hang.

**What the experiment therefore tests, stated precisely:** it removes **the dump's extra MBA load, the
85 MB copy, the second reclaim, and the `port failed halt` window** — a real and now *measured*
reduction of ~0.7 s of work and one full power-cycle from every recovery — but **not** the restart's
`q6v5_rmb_mba_wait()`. The prediction below is written against that.

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

**PRE-REGISTRATION (written before the run, per the standing SOP rule; the framing note was amended
*after* fatal #3 — see §8.13.1 — but the bar and the falsifier are unchanged and were not touched).**
* **Prediction:** removing the dump's second MBA load, the 85 MB copy, the second reclaim **and the
  `port failed halt` window** from every SSR recovery reduces the AP reboot rate to **zero**. (Per
  §8.13.1 this is now a **measured** removal, not an inference — but note it does **not** remove the
  restart's own `q6v5_rmb_mba_wait()`, which is a same-duration window.)
* **Bar:** **0 AP reboots across the next 20 fatals.** At the idle timer's ~902 s that is ~5 h of
  unattended running. *Justification for n=20:* the pre-823 rate was of order 1 reboot per 1–3 SSRs,
  so 20 clean fatals would be decisive; if the true post-823 rate were as low as 1 in 4, 20 fatals
  still gives a ~99.7 % chance of seeing at least one. **A single clean fatal proves nothing** — the
  current boot already survived two with the coredump *on*.
* **Control:** the pre-intervention series (§8.11) — explicitly **weak** (confounded by manual
  activity, boot loops, and pre-823). This is a *before/after*, not an A/B; an A/B (off → on → off)
  is the stronger design if the device time is available.
* **What would falsify it:** any reboot during the 20 fatal window **whose host-side `journalctl -k`
  shows no host cause** (§8.14 — a reboot the *host* caused, or a physical intervention, is not a
  failure of this experiment), or a stall whose `dmesg_roll` tail shows the recovery had already
  passed the coredump step.
* **A second, independent reason to run it:** it stops **85 MB per fatal** being written to
  `/overlay` (18 dumps already = 1.5 GB of 3.2 GB), which removes a disk-fill hazard *and* a large
  synchronous write from the recovery window — either of which could matter on its own.
* **Cost, stated plainly:** no coredump is captured during the window, so a fatal in this period
  cannot be decoded via its ERR_FATAL descriptor (Doc 163). The fatal *signature* still lands in
  `dmesg_roll`, and the ledger still records the teardown stages, so the loss is bounded.

**Experiment start:** device UTC `2026-09-21T11:58:41Z`; marker at
`/overlay/coredump_off_experiment.txt`; baseline at that moment: uptime 2420 s, 2 fatals this boot
(both with the coredump still on).

### §8.13.2 SECOND SAMPLE — fatal #4 reproduces fatal #3 exactly, so the A/B is **n = 2**

Fatal #4 fired at AP **3477.969305 s**, with the capture still off, and it is the first fatal of this
boot with a **different signature** (`a2_power.c:2949`, not `lte_ml1_sleepmgr_stm.c:4054`). The
recovery structure is identical to fatal #3's:

| | cold boot | #1 — **ON** | #2 — **ON** | #3 — **OFF** | #4 — **OFF** |
|---|---|---|---|---|---|
| fatal | — | 913.640795 | 1816.046634 | 2718.436833 | **3477.969305** |
| signature | — | sleepmgr:4054 | sleepmgr:4054 | sleepmgr:4054 | **a2_power.c:2949** |
| `SSR before shutdown` | — | 913.663690 | 1816.069773 | 2718.459733 | 3477.991158 |
| trace `01-09` (reclaim) | — | 913.745419 | 1816.147194 | 2718.539571 | 3478.072006 |
| `stopped remote processor` | — | 913.745683 | 1816.147432 | 2718.539856 | 3478.072612 |
| trace `10-23` (MBA load) | 12.039793 | 913.764296 | 1816.166714 | 2718.540745 | 3478.073938 |
| **`port failed halt`** | — | **914.443745** | **1816.854035** | ABSENT | **ABSENT** |
| trace `01-09` (2nd reclaim) | — | 914.443813 | 1816.854135 | ABSENT | ABSENT |
| trace `10-23` (2nd load) | — | 914.445731 | 1816.856197 | ABSENT | ABSENT |
| `MBA booted` | 12.199395 | 914.488592 | 1816.901332 | 2718.585024 | 3478.121395 |
| half-cycle pairs | — | **2** | **2** | **1** | **1** |
| `SSR before shutdown` → `MBA booted` | — | 0.824902 s | 0.831559 s | 0.125291 s | **0.130237 s** |
| `stopped` → `is now up` | — | 1.297121 s | 1.307782 s | 0.756669 s | 0.607311 s |

Boot-wide, at the moment of capture: **4 fatals, 5 `MBA booted` (cold boot + exactly one per
recovery), 2 `port failed halt`** — and the two are exactly the fatals with the capture **on**.
Trace-pair counts are `01-09` ×6 and `10-23` ×7 (= 1 cold boot + 2+2+1+1).

**A second, independent instrument agrees.** The ledger's `coredumps` column — written by a separate
10 s poller, not by the driver — reads **17, 18, 18, 18** across fatals #1–#4. Fatal #1 took the dump
count 16→17 and fatal #2 took it 17→18; fatals #3 and #4 produced **no dump at all**.

**So the s8.13 result is no longer n = 1.** Two recoveries with the capture off, on two different
fatal signatures, both show one half-cycle pair, no `port failed halt`, one `MBA booted`, and a
~0.128 s `SSR before shutdown` → `MBA booted` span against ~0.828 s for the two with it on — a
**0.70 s reduction, 6.4×**, reproduced to within 5 ms.

**Fatal #4's period is a separate point, and it is not an anomaly.** The intervals were
**902.405839 s**, **902.390199 s** (the sleepmgr clock, 17 ppm apart), then **759.532472 s**. The
clock's 4th beat would have been at 3620.83 s; an `a2_power.c:2949` event fired **142.87 s early**
and pre-empted it. Doc 162's taxonomy already puts `a2_power` at 68.5–941.3 s and explicitly **not**
on the clock, so this is Doc 164's "two mechanisms with different periods competing inside one boot",
with the `a2_power` one winning the race. The ledger independently agrees that this signature is not
clock-locked: it appears at uptime **719.00 s** and **896.52 s** in two earlier boots.

**Scored so far: 2 of 20 post-disable fatals, 0 AP reboots** (superseded by §8.13.3 and §8.13.4 —
the running total is **4 of 20**). Evidence:
`V_coredump_off_fatal4_n2_and_the_lost_edge_precursor.txt`.

### §8.13.3 THIRD SAMPLE — fatal #5 makes it **n = 3** on a **third** signature, and the two populations are 6.6× apart and non-overlapping

Fatal #5 is **`a2_power.c:1189`** at AP **4304.991306 s** — a signature never before seen with a resync
in front of it. Its recovery, with the capture still off: `SSR before shutdown` **4305.013229** →
`stopped remote processor` **4305.092833** → `MBA booted` **4305.137760** = **0.124531 s**.

| # | signature | capture | half-cycle pairs | `port failed halt` | `SSR before shutdown`→`MBA booted` |
|---|---|---|---|---|---|
| 1 | sleepmgr:4054 | **ON** | 2 | 914.443745 | 0.824902 s |
| 2 | sleepmgr:4054 | **ON** | 2 | 1816.854035 | 0.831559 s |
| 3 | sleepmgr:4054 | OFF | 1 | ABSENT | 0.125291 s |
| 4 | `a2_power.c:2949` | OFF | 1 | ABSENT | 0.130237 s |
| 5 | `a2_power.c:1189` | OFF | 1 | ABSENT | **0.124531 s** |

**Three signatures, three capture-off recoveries, all inside 0.125291–0.130237 s (spread 5.0 ms)
against 0.824902–0.831559 s (spread 6.7 ms) for the two with it on. The two populations do not overlap
and are 6.6× apart.** Boot-wide: **5 fatals, 6 `MBA booted`** (cold boot + one per recovery),
**`port failed halt` 2** — exactly the two capture-ON recoveries; trace `01-09` ×7, trace `10-23` ×8
(= cold boot + 2+2+1+1+1); `cmd_open` **48** = 8 channels × 6 modem boots; `coredump_live` **frozen at
18**; ledger row `4313.31,5,a2_power.c:1189,…,18`.

**s8.13 bar: 3 of 20 post-disable fatals scored, 0 AP reboots.** Fatal #5's interval from fatal #4 was
**827.022001 s** — inside Doc 162's `a2_power` band (68.5–941.3 s) and off the 902.4 s clock, the same
story as fatal #4's 759.53 s. One detail kept: `SSR powerup: modem pc_state=1 (waited 580 ms)`, against
200 ms at fatal #4.

---

### §8.13.4 FOURTH SAMPLE — fatal #6 makes it **n = 4**, AP survived, and it forces a CORRECTION to the A/B metric

Fatal #6 is **`lte_ml1_sleepmgr_stm.c:4054`** at AP **5208.721361 s** — the clock signature, and the
first sleepmgr since fatal #3. `SSR before shutdown` **5208.744264** → `stopped remote processor`
**5208.824496** → `MBA booted` **5208.871637** = **0.127373 s**, no `port failed halt`, no coredump.

**Four capture-OFF samples now span `0.124531–0.130237 s` (spread 5.7 ms)** against
`0.824902–0.831559 s` (spread 6.7 ms) for the two capture-ON — still two non-overlapping populations,
still ~6.6× apart, now on **four signatures in four recoveries**. Boot-wide: **6 fatals / 7 `MBA booted`
/ 2 `port failed halt`** (still exactly the two capture-ON) / trace `01` ×8, `10` ×9 / `cmd_open`
**56 = 8 channels × 7 modem boots**; ledger row `5218.41,6,lte_ml1_sleepmgr_stm.c:4054,…,…,18` with
**`t0..t9` all present and `coredumps` STILL 18**.

**⚠ CORRECTION — the A/B metric is the `SSR before shutdown`→`MBA booted` interval, NOT fatal→`is now
up`.** Fatal #5's full fatal→up time is **1.443682 s**, as long as the capture-ON recoveries, even
though its coredump-sensitive interval is 0.124531 s — the extra time is the mpss load **after**
`MBA booted` and it varies independently. **Anyone scoring this A/B on fatal→up would read fatal #5 as
a failed removal.** Use the interval the coredump actually sits in.

**And fatal #6 gives the clock its best fit yet:** modem uptime = 5208.721361 − 4306.434988 (the
previous `is now up`) = **902.286373 s**, **19.4 ms from Doc 162's 902.267 s** clock figure, with the
signature Doc 164's short band is always paired with. Full modem-uptime list for this boot:
900.717032 / 901.003830 / 900.981619 (sleepmgr) / 758.672780 (`a2_power.c:2949`) / 826.311383
(`a2_power.c:1189`) / **902.286373** (sleepmgr). **Recorded, not concluded: the fourth sleepmgr beat is
1.3 s above the other three** — that could be a second clock mode or an artefact of using the
`is now up` line (a different instrument from the fatal line) as the origin.

**s8.13 bar: 4 of 20 post-disable fatals scored, 0 AP reboots.**

---

### §8.13.5 FIFTH SAMPLE — fatal #7 makes it **n = 5**, and the interval drops to a new minimum

Fatal #7 is **`lte_ml1_sleepmgr_stm.c:4054`** at AP **6110.407029 s** — the clock signature.
I predicted it at AP 6111.007 from fatal #6 + 902.286 s; it fired at 6110.407, a **600 ms miss over a
902 s period (665 ppm)**.

```
clock delta fatal#6 -> fatal#7 = 6110.407029 - 5208.721361 = 901.685668 s
SSR before shutdown 6110.430574  ->  MBA booted 6110.549744   = 0.119170 s
```

**`0.119170 s` is a NEW MINIMUM** for the capture-OFF population (previous span
`0.124531–0.130237 s`, n = 4, spread 5.7 ms). Against `0.824902–0.831559 s` for the two capture-ON,
the two populations remain non-overlapping and now ~7× apart, on **five signatures in five
recoveries**. No `port failed halt` — confirming again that the only two `port failed halt` lines in
this boot are the two capture-ON recoveries. Post-SSR artefact, 5th sample: `pc-ack timeout` at
6110.852136 = **+0.302392 s** after `MBA booted` (prior: +0.431112 / +0.581419 / +0.435022 / none).

Boot-wide after fatal #7: **7 fatals / 8 `MBA booted` / 2 `port failed halt` / trace `01` ×8, `10` ×9
/ `cmd_open` 64 = 8 channels × 8 modem boots**; `coredump_live` still frozen at 18.

**s8.13 bar: 5 of 20 post-disable fatals scored, 0 AP reboots.**

---

### §8.13.6 EIGHTH SAMPLE — fatal #12 makes it **n = 8**, the modem is STILL ON THE CLOCK, and the storm silence has now survived **TWO** modem reloads

Fatal #12 is **`lte_ml1_sleepmgr_stm.c:4054`** at AP **8385.263928 s** — the clock signature, and the
**second consecutive clock fatal** after the cascade.

```
AP delta  fatal#11 -> fatal#12 = 8385.263928 - 7483.839750 = 901.424178 s
modem uptime (from fatal #11's `is now up` 7484.542307)      = 900.721621 s
predicted at AP 8386.9 (fatal #11's modem boot 7484.542 + 902.35)  ->  MISSED BY 1.64 s
SSR before shutdown 8385.287675  ->  MBA booted 8385.410626  = 0.122951 s
```

**`0.122951 s` lands INSIDE the capture-OFF band**, which stays `0.119170–0.130237 s` — now **n = 8,
spread 11.1 ms** — against `0.824902–0.831559 s` for the two capture-ON: still non-overlapping, still
**~7× apart**, now on **eight signatures in eight recoveries**. No `port failed halt`. One half-cycle
pair (`01-09, 10-23`), one `MBA booted`. `coredump_live` **frozen at 18**; `coredump` attribute still
`disabled`; `rproc=running`; the host uptime watcher shows a monotonic **7418 → 8382** with no reboot.

Boot-wide: **12 fatals / 13 `MBA booted`** (= cold boot + one per recovery) **/ 2 `port failed halt`**
(exactly the two capture-ON) **/ `cmd_open` 104 = 8 channels × 13 modem boots / 12 `SSR before
shutdown`**.

Post-SSR artefact, 6th sample: `pc-ack timeout` at **8385.704199** = **+0.293573 s** after `MBA booted`
(prior: +0.431112 / +0.581419 / +0.435022 / none / +0.302392). **It is the ONLY new handshake event in
the whole window, and it is post-fatal** — the pattern §8.18 recorded holds.

**★ AND THE STORM IS STILL SILENT — NOW ACROSS A SECOND MODEM RELOAD.** `pc_resync_count` is **81**
and the **last lost edge in `dmesg` is STILL `[ 7417.446456]`**, so the silence stands at
**967.817 s** (7417.446456 → 8385.263928), and **fatal #12's reload did not restart the storm either**.
`pc_timeout_count` moved 78 → 79, and that single increment *is* the post-fatal artefact above — so no
new storm event. `pm_suspend_attempts` 1018 → 1050 (**+32**) across the same window.

**⇒ §8.21's finding is now TWO RELOADS DEEP, not one: a modem reload is sufficient neither to END the
storm nor to RESTART it.** The storm is therefore not a per-modem-boot state — which sharpens §8.21's
open question (it began 81.5 ms before crash #4 and spanned seven modem reloads; it then ended on its own and
survived two further reloads). The "third condition" is on the **AP** side or in a **pattern** of
suspends, not in the modem's boot state.

**s8.13 bar: 8 of 20 post-disable fatals scored, 0 AP reboots.** Remaining: 12 fatals.

---

### §8.14 THE HOST KERNEL LOG REFRAMES THE AP-RESET RATE — §8.10's "reset that survived 823" WAS MY OWN HUB INSTALLATION, AND §8.11's RATE IS NOT AN AP-HANG RATE

§8.10 concluded that 823 is not a complete fix, from **one** event: an "AP reset" at 17:03:24 (AP
uptime ~1061 s). §8.11 then used the host watcher's reboot count to argue the reset rate is
**~1 in 1–3 SSRs**. **Neither checked the host kernel log.** It is the only instrument that can say
whether the dongle rebooted *on its own* or was reset *by the host* — and it was already being
written, covering the whole day.

**The host log shows error in BOTH directions.**

1. **39 dongle re-enumerations, but only ~19 AP uptime-decreases.** So a USB re-enumeration is **not**
   an AP reboot: at **14:39:05** the dongle re-enumerated cleanly (`cdc_ncm … enu1i2` renamed, i.e.
   fully registered) and the watcher's uptime kept climbing. *Counting re-enumerations would
   over-count; counting only uptime-decreases under-counts relative to the host's view.*
2. **The host's own USB controller flaps.** Sep 21: **48 `xHCI Host Controller` re-registrations** and
   **12 `usb usb1: USB disconnect`** (the *root hub*, so the whole bus), of which **36 of the 48 fall
   inside the 16h hour alone**; plus 6 × `device descriptor read/64, error -71` and one
   `attempt power cycle` in the 15h hour.

**§8.10's reset is explained, and it was mine.** The host log for those exact minutes:

```
17:03:07 usb 1-1: USB disconnect, device number 3
17:03:13 usb 1-1: ... idVendor=214b, idProduct=7250 ... Product: USB2.0 HUB
17:03:13 hub 1-1:1.0: USB hub found ; 4 ports detected
17:03:41 usb 1-1.2: ... Product: USB Gadget
```

The dongle was on port **1-1**; at 17:03:13 a **USB2.0 HUB appears on 1-1 for the first time** and the
dongle re-appears at **1-1.2** behind it. **A hub cannot be added to a port without first removing the
device that was on it** — this is a physical intervention, and the 29 s outage §8.10 measured *is* the
unplug/replug window. §8.10's three supporting observations (empty pstore, no 4th coredump, no ledger
row) are **exactly what a power cycle produces**, and §8.10's reading of them ("a hung fatal and no
fatal are indistinguishable") was true of the *instruments* but blind to the third option: **no fatal
at all, because the AP was never running the fatal path.** **Consequence: §8.10 does NOT show that 823
is incomplete, and 823 is substantially rehabilitated.** (The hub was separately recorded in memory as
a device fact — but never connected to the reset it caused.)

**The 16:40–16:46 "boot loop" is entangled with a host USB-controller storm — and the DIRECTION OF
CAUSATION IS NOT ESTABLISHED.** The watcher saw uptime `2427 → 46 → 38 → 79` (three AP reboots in seven
minutes) and §8.11 recorded it as boot loops. The host log shows the **xHCI platform driver being
removed and re-probed 24 times on Sep 21, 18 of them inside the 16h hour**, each cycle deregistering
`usb usb1`/`usb usb2` (the *root hubs*) and rebuilding them, with the dongle appearing and disappearing
every ~30–60 s. **But the ordering is MIXED, so I cannot say the host caused the reboots:**

* `10:31:46`, `15:06:03`, `16:40:30`, `16:41:14`, `16:42:11` — the **dongle disconnects first**, and the
  controller is removed within ~1 s. A device disconnect does not normally unbind an HCD, so something
  downstream of it does — but the *trigger* is the device.
* `15:59:28` — the controller is removed with **no preceding device disconnect at all**, i.e. a
  **host-initiated** teardown.

**What is established either way: during those windows the dongle's USB link was being torn down and
rebuilt repeatedly**, so the reboots there **cannot be cleanly attributed to an AP-side hang**. I
initially labelled these four "host-caused"; **that is stronger than the evidence and is retracted to
"entangled, direction not established".**

**A first pass at the causal mechanism that I am NOT claiming:** the host SMC logs non-zero electrical
events (`Elec Cause 0x200000` / `0x8020`, `Not charging:1002000`) at `16:40:05`, 25 s before that
episode — but the base rate is **8 such events across the day** (01:32, 02:38, 10:18, 10:19, 14:07,
15:46, 15:47, 16:40) against **47** `Elec Cause 0x0`, and they do **not** align with the other xHCI
episodes (10:18→10:31 is 12 min; 15:46→15:59 is 12 min), so they are most likely the laptop's own
**charger/battery** state. **Recorded as a non-finding, deliberately not used.**

**The outage durations are BIMODAL, and §8.11's model fits only the short mode.** §8.11 explained
"the 13–61 s unreachable windows" as `~30 s stall + ~15–40 s boot`. The full list is

* **short mode (16 events):** 5, 13, 13, 13, 13, 21, 21, 22, 29, 29, 29, 37, 37, 45, 53, 61 s;
* **long mode (2 events):** **926 s** (15:44:42 — preceded by `error -71` ×6 and `attempt power
  cycle`, i.e. the gadget came back **broken**) and **1641 s** (14:39:38, 27 minutes).

**A 1641 s outage cannot be a 30 s watchdog stall plus a boot — and the watchdog tells us what it
IS.** The PON watchdog is **kicked by `procd`**, a userspace process: if the kernel/CPU stops, nothing
kicks it and the SoC resets in **30 s**; if only the *network path* is broken while the CPU keeps
running, `procd` keeps kicking and **no reset happens**. So **an unreachability longer than
~30 s + a boot (~15–40 s) is not a hang** — the AP was alive. The **1641 s** and **926 s** outages
therefore are **network-path failures with a running AP**, which is a *different failure mode* from
everything §8.11 modelled, and one that matters directly for the "data stall" reports. (They also
explain why the watcher recorded a *reboot* against them: the uptime decrease it sees is a reboot at
the **end** of the window — the 14:39 outage ends with a re-enumeration at 15:06:48 and an AP uptime of
34 s at 15:07:02 — not at its start. **The watcher attributes the reboot to the wrong end of the
outage.**)

**So the reboot rate is not an AP-hang rate.** Classifying all 19 outages, **7 are not clean AP-hang
data points** — one certain, two certain-by-duration, four entangled:

| outage | duration | evidence | verdict |
|---|---|---|---|
| 17:03:24 | 29 s | **hub installed on port 1-1** (host log) | **certainly not an AP hang** |
| 14:39:38 | **1641 s** | watchdog is kicked by `procd` ⇒ a stopped kernel resets in 30 s | **certainly not a hang** |
| 15:44:42 | **926 s** | same, plus `error -71` ×6 + `attempt power cycle` | **certainly not a hang** (USB PHY) |
| 16:40:47 | 53 s | xHCI controller removed/re-probed ×4 around it | **entangled — direction not established** |
| 16:42:29 | 61 s | xHCI ×12 around it | **entangled** |
| 16:44:14 | 45 s | xHCI ×24 around it | **entangled** |
| 16:45:54 | 13 s | xHCI ×16 around it | **entangled** |

The remaining **12** have no host-side trigger and no duration argument against them, and are the only
AP-hang candidates — but **"no host trigger" is not proof**, because the host log only records what
the *host* observed.

**What this changes:**
* **§8.10's verdict on 823 is withdrawn** (its single event was a manual intervention). 823 remains
  what §8.7 established: deployed, functionally clean, fixing the UAF it targets by mechanism.
* **§8.11's "1 in 1–3 SSRs" is not supported** and must be re-derived with the host log as a filter.
  The pre-823/post-823 split §8.11 offered as a caveat is not the main problem — the *denominator* is.
* **§8.13's bar needs a filter, and this is now written into it:** a reboot during the 20-fatal window
  counts as a failure **only if the host log shows no host-side cause**. With the dongle moved onto a
  hub at 17:03 and the host's xHCI having flapped at 16h, the risk of scoring a **false failure** is
  real. *(This is the same class of error as §8.10 itself — attributing a device event to the device
  without checking the instrument that can see the other side.)*

**The instrument lesson, stated generally: an AP-side uptime-decrease proves the dongle restarted; it
does NOT prove the dongle restarted ITSELF.** The host kernel log is a required companion instrument
for every reboot attributed to the AP, exactly as the DIAG capture is required for every claim about
the modem. Evidence: `U_host_usb_log_reframes_the_ap_reset_rate.txt`.

---

### §8.15 NEW — EVERY `a2_power.c:2949` FATAL IS PRECEDED BY AN AP-SIDE "LOST EDGE" RESYNC, 74–86 ms EARLIER (3 of 3, in three different boots)

**This is the first AP-side observable found that reliably precedes a modem fatal.** It came out of
fatal #4 by accident: the fatal's own window contained one extra line.

```
[ 3477.894835] bam-dmux ...: bam_dmux: RX watchdog: PC line asserted while pc_state=0 (lost edge), resyncing
[ 3477.969305] qcom-q6v5-mss 4080000.remoteproc: fatal error received: a2_power.c:2949:
```

**74.470 ms.** Nothing else was logged in between — only the resync's own work, which logs nothing.
And `pc_resync_count` in `rx_telemetry` went **0 → 1** for the boot: this was the only resync in
3477 s.

**It is not a one-off.** Searching the preserved logs for the same pair:

| boot | resync (`lost edge`) | fatal | gap | signature |
|---|---|---|---|---|
| P | 714.454106 | 714.539903 | **85.797 ms** | `a2_power.c:2949` |
| Q | 886.743235 | 886.817190 | **73.955 ms** | `a2_power.c:2949` |
| Y (this boot) | 3477.894835 | 3477.969305 | **74.470 ms** | `a2_power.c:2949` |

mean **78.074 ms**, spread **11.842 ms** — and the two closest are **0.515 ms** apart.

**But it is NOT sufficient, and the counter-examples matter as much as the hits:**

| boot | resync | next fatal after it | verdict |
|---|---|---|---|
| R | 1763.929555 | 1837.400528 (`a2_power.c:1189`) at **+73.47 s** | no fatal near it |
| S | 4874.826138 | none before the log ends at 5110 | no fatal |
| S | 4924.559122 | " | no fatal |
| S | 4932.915755 | " | no fatal |
| S | 4991.240271 | " | no fatal |

So **`a2_power.c:2949` ⇒ a resync ~78 ms earlier is 3/3**, while **a resync ⇒ `a2_power.c:2949` is
3/8**. Boot S's log ends 119 s after its last resync, so "no fatal" there is a *bounded* claim; boot
R's 73.47 s of subsequent log with no `a2_power.c:2949` is not.

**Provenance, because these samples come from a multi-boot log.** `/overlay/q6trace.log` is an
append-across-boots file, not one boot, so the P and Q samples were cross-checked against
`/overlay/ssr_ledger.csv`, whose rows are produced by an independent 10 s poller. The ledger's
column 1 is a *noticed* time that lags the true fatal by 0–10 s, so the test is "0–10 s later?":
**6 of 6 match** (P: 714.54→719.00, 1332.19→1336.18, 2234.55→2244.25; Q: 381.48→389.95,
495.06→502.08, 886.82→896.52). P and Q are definitively different boots — each has its own
`n = 1,2,3` sequence and the ledger's `coredumps` column is monotonic across them (11,12,13 then
14,15,16).

> **Provenance trap.** `grep -c "a2_power.c:2949" /overlay/q6trace.log` returns **5**, but that is
> **3 distinct events** — the boot-P line is duplicated three times in the file. A naive count over
> a log that spans boots inflates the sample. **Check whether a log spans boots before counting
> fatals in it.**

**Why an AP-side action is a plausible cause at all.** The resync branch is code **added by patch
808** (it did not exist upstream). It does four things, **two of which the modem can see**:

```c
WRITE_ONCE(dmux->pm_suspend_start_ns, 0);   /* AP-local  */
WRITE_ONCE(dmux->pc_state, true);           /* AP-local  */
bam_dmux_pm_restart(dmux);                  /* MODEM-VISIBLE: powers the BAM down and up */
bam_dmux_pc_ack(dmux);                      /* MODEM-VISIBLE: the SMSM power-collapse ACK */
complete_all(&dmux->pc_ack_completion);     /* AP-local  */
wake_up_all(&dmux->pc_wait);                /* AP-local  */
```

Three readings fit the correlation, and the data does **not** yet choose between them:

* **A — the resync is a poison pill.** The ACK and/or the BAM restart lands on a modem whose power
  state machine has already moved on, and `a2_power` asserts. Under A the resync→fatal gap is a
  *modem reaction constant*, so it should **not** vary with how late the AP noticed the edge.
* **B — common cause.** The modem's PC state machine had already desynchronised, which is why the
  edge was lost *and* why `a2_power` asserts. The resync is a symptom.
* **C — the AP's missed edge is the cause.** The modem asserts the PC line with a bounded wait for
  the ACK; the AP missed the edge, so only the watchdog finds it and the ACK lands outside that
  window. Under C the gap is `(modem_timeout − edge_delay)` and **should** vary with the AP's
  detection delay.

**The 100 ms watchdog period is what makes C testable, and it is weakly disfavoured.** The watchdog
re-arms itself every 100 ms (`schedule_delayed_work(&dmux->rx_watchdog_work, msecs_to_jiffies(100))`,
patch 808), so the AP's detection delay is uniform on [0, 100] ms. Under C the three observed gaps
would require three independent edge delays to land inside an 11.8 ms window out of 100 ms — roughly
a 4 % coincidence. That mildly favours **A**, and it is **far too weak to act on**; it is recorded so
the next session does not have to redo it. Reading A also cannot say *which* of the two
modem-visible actions is the trigger — both sit inside the same 74–86 ms.

**Not yet examined, and the cheapest next step:** `pc_timeout_count: 4` in `rx_telemetry`. If that
counter is the AP's own PC-handshake timeout it may be measuring the same window from the other
side; read its definition before designing anything around it.

**THE DISCRIMINATOR IS CHEAP — `qcom_bam_dmux` IS A LOADABLE MODULE.**

```
# lsmod | grep bam
qcom_bam_dmux          32768  0
# ls -la /lib/modules/6.12.94/qcom_bam_dmux.ko
-rw-r--r--  1 root root 241232 Sep 21 08:07 /lib/modules/6.12.94/qcom_bam_dmux.ko
```

so a variant that changes **only the resync branch** deploys by swapping the `.ko` — **no kernel
flash**, exactly the patch-823 playbook (`scratch/mkko823.sh`). Three designs:

* **D1 — observational, zero behaviour change.** Log immediately before `bam_dmux_pc_ack()` and
  immediately after `bam_dmux_pm_restart()`, so the fatal can be timed against each action rather
  than against the single resync line. One build; answers nothing alone, but removes the assumption
  that the two actions are simultaneous.
* **D2 — behavioural, decisive for the ACK.** Make the resync **skip `bam_dmux_pc_ack()`**, leaving
  `pm_restart()` in place, and count `a2_power.c:2949` per resync. If A is right and the ACK is the
  trigger, the signature disappears. **This is a probe, not a candidate fix** — the modem then never
  receives its ACK and may fail differently — and it must be reverted after.
* **D3 — the other half.** Skip `bam_dmux_pm_restart()` instead, keeping the ACK. D2 and D3 together
  split Reading A into its two sub-cases.

**Sequencing decision (recorded so it is not re-litigated).** D2/D3 must **not** be deployed inside
the open §8.13 window: they change which fatals occur, and a reboot after the swap could not be
attributed. But the window costs nothing to leave running and produces fatals *passively*, so the
observational half is pre-registered now.

**PRE-REGISTRATION (passive, on the running §8.13 window).**
* **P1:** the next `a2_power.c:2949` fatal in this boot will be preceded by a `lost edge` resync
  within 100 ms. (Currently 3/3; this tests for 4/4.)
* **P2:** count the next 4 resyncs **not** followed by an `a2_power.c:2949` within 100 ms, to keep
  the 3/8 sufficiency figure honest rather than letting the hits accumulate alone.
* **FALSIFIER:** an `a2_power.c:2949` with **no** resync in the preceding 100 ms breaks the
  association, and all three readings lose their AP-side handle.

**What this does NOT claim.** It does not claim the resync causes the fatal — 3/8 is a correlation
with a tight lag and a plausible mechanism, nothing more. It does not explain the
`lte_ml1_sleepmgr_stm.c:4054` or `a2_power.c:1189` fatals, **neither of which has ever been seen with
a resync in front of it**. Evidence: `V_coredump_off_fatal4_n2_and_the_lost_edge_precursor.txt`.

### §8.15.1 P2 IS SCORED, AND THE RESYNC RATE IS NOT STATIONARY — the sufficiency figure falls to 3/13, and a storm of 5 resyncs in 145 s appears out of nowhere

**P2 existed to stop the hits accumulating alone. It did its job.** `pc_resync_count` reached **6** in
this boot; every resync, with what followed it:

| # | resync | outcome | hit? |
|---|---|---|---|
| 1 | 3477.894835 | fatal `a2_power.c:2949` at 3477.969305 (**+74.470 ms**) | **YES** |
| 2 | 4079.094133 | nothing logged | no |
| 3 | 4119.287485 | `pc-ack timeout` at 4119.470945 (+183.460 ms) | no |
| 4 | 4175.847548 | `pc-ack timeout` at 4176.057624 (+210.076 ms) | no |
| 5 | 4192.370087 | nothing logged | no |
| 6 | 4224.514264 | nothing logged | no |

Pooling every boot with a surviving log (P 1/1, Q 1/1, R 1/0, S 4/0, Y 6/1):

> **`a2_power.c:2949` ⇒ a resync ~78 ms earlier is 3/3.**
> **a resync ⇒ `a2_power.c:2949` is 3/13 (was 3/8).**

The two figures now differ by more than **4×**. Whatever the 74–86 ms lag is, it is **not a sufficient
condition**, and any fix built on "the resync causes it" must first explain why ten resyncs do nothing.
*Caveat that cuts the other way:* the P/Q/R resync counts come from the partial `q6trace.log`, so a
missing resync would make the denominator **larger** — 3/13 is a floor.

**The rate is not stationary, and it changed abruptly with no AP-side cause.**

```
first resync   3477.894835    <- the ONLY one in the first 3478 s
next           4079.094133    <- +601.199 s of silence
then           4119.287485, 4175.847548, 4192.370087, 4224.514264
                              => FIVE resyncs inside 145.420 s
```

Nothing about the AP changed at 4079 s — no patch, no reboot, no manual action, `coredump` still
`disabled`, `rproc=running`. The modem simply began losing PC-line edges repeatedly. `pc_quiesce_ms`
had fallen to **1669 ms** (the modem was cycling, not quiesced), and `pm_suspend_attempts` vs
`pm_suspend_completions` showed the same constant deficit of **7** as at 488/481 earlier in the boot.

**Recorded as an observation with a pre-registration, not as a finding.**

* **P3:** the storm is the modem approaching its next fatal ⇒ fatal #5 should be a
  `lte_ml1_sleepmgr_stm.c:4054` at roughly AP **4380 s** (2718.436833 + 902.4 = 3620.8 s has already
  passed silently, so either that timer restarted at the `a2_power` fatal — 3477.969 + 902.4 = 4380.4 s
  — or it was suppressed).
* **P3-FALSIFIER:** no fatal in the following ~15 min, or a fatal with no storm in front of it, or a
  storm that simply stops.
* **Why it matters either way:** if the storm *precedes* the fatal, the right window for the resync
  association is **minutes, not milliseconds** — and the three tight hits would be the tail of a storm
  that happened to be caught, which is a different mechanism from "the resync causes the fatal". If it
  does **not** precede the fatal, the resync is a benign (if noisy) repair path and the three hits are
  more likely coincidence than cause.

**§8.15's "cheapest next step" is done, and the answer is negative.** `pc_timeout_count` is
incremented in exactly two places, both inside `bam_dmux_runtime_resume()` (patch 808): a
**250 ms** `wait_for_completion_timeout(&dmux->pc_ack_completion, …)` and a **1000 ms**
`wait_event_timeout(dmux->pc_wait, …)`. So it counts **the AP waiting for the modem** — the *mirror*
of Reading C's "the modem waits for the AP's ACK". **It cannot discriminate C; do not design around
it.** (It does bound the AP's side, and both windows are generous next to the 74–86 ms.) A small but
real cross-check came with it: the counter read **4** when exactly four such lines existed in `dmesg`,
and **6** when six did.

**And the post-fatal `pc-ack timeout` lines are recovery artefacts, not precursors.** Each against the
nearest preceding fatal: #1 → **+0.431112 s**, #2 → **+0.581419 s**, #3 → **+0.435022 s**, #4 →
**none**. Three of four land ~0.43–0.58 s *after* a fatal, which is the AP's post-SSR resume finding
the modem slow to ACK — exactly the base-rate reading §8.8 arrived at. Fatal #4 having none is a
single observation and is **not** explained; the tempting story (the coredump-off recovery is 0.70 s
shorter, so the resume lands earlier and the modem is readier) is **speculation, recorded as
speculation**. The one `pc_state wait timeout` + `channels not initialized after resume` pair, at
915.085520 / 915.085715 (+1.44 s after fatal #1), is the patch-814 path doing its job.

### §8.15.2 P3 IS SCORED AND ITS SIGNATURE PREDICTION FAILED — the storm **brackets** the fatal, the tight lag is `a2_power.c:2949`-specific, and the storm turns out to be **bidirectional** handshake failure

**P3 predicted a `lte_ml1_sleepmgr_stm.c:4054` at ~AP 4380 s. Fatal #5 was an `a2_power.c:1189` at AP
4304.991306 s.** The **signature was wrong**; the timing was 75.0 s early. The weaker half — "a fatal
follows the storm" — held in the sense that the fatal landed **226 s into** a storm that ran
4079.094133 → 4352.497742 (273.4 s), **but the storm also continued 47.5 s past the fatal**
(resyncs at 4344.057823 and 4352.497742). **So the honest reading is that the storm BRACKETS the fatal
rather than preceding it**, and P3 is recorded as a **failed prediction**.

**The 74–86 ms antecedent does not generalise — it is specific to `a2_power.c:2949`.** Fatal #5's
nearest preceding resync is 4224.514264, **80.477042 s earlier**:

| signature | resync within 100 ms before it? |
|---|---|
| `lte_ml1_sleepmgr_stm.c:4054` | never observed (0/3 fatals) |
| `a2_power.c:2949` | **3/3** — 85.797 / 73.955 / 74.470 ms |
| `a2_power.c:1189` | 0/1 — nearest was 80.48 s earlier |

Pooled sufficiency, updated: **15 resyncs observed** (P 1, Q 1, R 1, S 4, Y 8), **3 hits** ⇒
**`a2_power.c:2949` ⇒ resync is 3/3** while **resync ⇒ `a2_power.c:2949` is 3/15** (was 3/13, then 3/8).
The antecedent is now **5× from sufficient**, and it belongs to one signature, not to the fatal.

**★ AND LINING UP BOTH FAILURE MESSAGES MAKES THE STORM LEGIBLE — it is the SAME handshake failing in
BOTH directions.**

```
RX watchdog: PC line asserted while pc_state=0 (lost edge), resyncing
      = the MODEM asserted the PC line and the AP's edge IRQ missed it
modem pc-ack timeout during resume
      = the AP voted for power state and the MODEM did not ACK within 250 ms
```

| time | event |
|---|---|
| 913.640795 / 1816.046634 / 2718.436833 | fatals #1–#3 (sleepmgr) — **no storm**; one `pc-ack timeout` each, +0.43…+0.58 s *after* |
| 3477.894835 → 3477.969305 | resync → **fatal #4** (`a2_power.c:2949`), +74.470 ms |
| **storm begins** — 1 resync in the previous 3478 s, then 5 in 145 s | |
| 4079.094133, 4119.287485, 4175.847548, 4192.370087, 4224.514264 | resyncs |
| 4119.470945 (+0.183460 s), 4176.057624 (+0.210076 s) | `pc-ack timeout` |
| 4297.231159 (−7.760147 s), 4304.244365 (−0.746941 s) | `pc-ack timeout` |
| **4304.991306** | **fatal #5** (`a2_power.c:1189`), 226 s into the storm |
| 4344.057823, 4352.497742, 4376.591183 | resync, resync, timeout — **the storm outlives the fatal** |

**Both directions fail during the storm.** A resync means the AP missed the modem's edge; a `pc-ack
timeout` means the modem missed the AP's vote. Either way the modem's power-collapse handshake has
become unreliable, and the fatal happens inside that window. **This reframes §8.15's question:** the
interesting variable may not be *"did a resync fire 74 ms before the fatal"* but *"is the PC handshake
in a failing regime at all"* — a state that persists for **minutes** and is measurable from two
existing counters (`pc_resync_count`, `pc_timeout_count`) with **no new instrumentation at all**.
**D2/D3 should therefore be scored on the storm, not on a single resync.**

A counter that moves with it, recorded and not explained: `pm_suspend_attempts − pm_suspend_completions`
was a constant **7** through the clean part of the boot (488/481, then 560/553) and is **10** at
576/566 after the storm. **Corrected in §8.15.3 §7 — it is accounting, not an anomaly.**

**What this does not establish.** Five of the storm's eight resyncs produced no fatal, and the storm
outlived the fatal, so **"storm ⇒ fatal" is false**. Whether the storm is a *cause*, a *consequence*,
or a third symptom of something else in the modem is exactly what D1/D2/D3 are for.

---

### §8.15.3 THE STORM IS A REGIME, NOT AN EVENT — it runs 8+ minutes past the fatal with the data plane fully working, both directions of the handshake fail ~184 ms apart, and nothing external triggered it

**Measured 2026-09-21 18:22–18:40 host time, same boot (`a9fd907c`), uptime 4710 → 4900. Raw record:
`V_…` PART 7.**

**1. It does not stop and it does not produce a second fatal.** `pc_resync_count` went **15 → 18 → 20**
across uptimes 4710.44 / 4814.48 / 4852.90, i.e. **14 resyncs in 508.84 s = 1 per 36.3 s**, sustained.
Fatals are **still 5**. So the storm is a **regime**: the modem runs, the data plane is up, and the
handshake fails about once every 36 s for 8+ minutes with no fatal at all.

**2. The two directions account for themselves exactly — there is no residue.**

| | count | |
|---|---|---|
| **lost edge** (AP missed the modem's edge) | **15** | 1 preceded a fatal (3477.894835, +74.470 ms) · 5 paired · 9 unpaired |
| **pc-ack timeout** (modem missed the AP's vote) | **14** | 3 post-fatal recovery artefacts · 5 paired · 6 unpaired |

**3. The five paired events sit ~184 ms apart — and the mechanism does NOT close arithmetically, which
is itself the finding.**

| lost edge | pc-ack timeout | lag |
|---|---|---|
| 4119.287485 | 4119.470945 | +183.460 ms |
| 4175.847548 | 4176.057624 | +210.076 ms |
| 4448.051188 | 4448.217985 | +166.797 ms |
| 4464.327857 | 4464.511287 | +183.430 ms |
| 4710.440616 | 4710.617254 | +176.638 ms |

mean **184.080 ms**, spread 43.279 ms. With an event every ~36 s, a timeout landing within 250 ms of a
resync by chance is **~0.7 %**, so the coupling is **real**. But the only site that prints `modem
pc-ack timeout during resume` is `bam_dmux_runtime_resume()`, where the line is emitted **exactly
~250 ms after that resume's own `bam_dmux_pc_vote(true)`** (`reinit_completion()` at `:1421`, the
`msecs_to_jiffies(250)` wait at `:1429`). A line at **T+184 ms** therefore means the vote happened at
**T−66 ms — *before* the resync at T**. And the resync itself calls
`complete_all(&dmux->pc_ack_completion)` after `bam_dmux_pm_restart()` + `bam_dmux_pc_ack()`, which
**must** have satisfied that waiter. So either **(i)** the resync's `complete_all` is being undone by a
later `reinit_completion()` — there are exactly two, `bam_dmux_power_on()` `:178` and
`bam_dmux_runtime_resume()` `:1421` — or **(ii)** the coupling runs through the **modem's ACK timing**,
not the completion object at all. **⇒ This is what D1 should now be:** log the ordering of `reinit` /
`complete_all` / the pc_ack IRQ. The original D1 ("log either side of the two modem-visible actions")
does not test this.

**4. A candidate SECOND precursor, pre-registered at n = 1.** Fatal #5 (`a2_power.c:1189`,
4304.991306) has a `pc-ack timeout` at 4304.244365 — **746.941 ms *before* it**. Fatal #4
(`a2_power.c:2949`) has none within ±1 s. So the antecedent is again **signature-specific**:
`a2_power.c:2949` ← lost edge (74.470 ms, 3/3 boots); `a2_power.c:1189` ← pc-ack timeout (746.941 ms,
n = 1). **P4 (pre-registered):** the next `a2_power.c:1189` also has a `pc-ack timeout` within ±1 s
before it ⇒ 2/2. **P4-FALSIFIER:** no such line, or one *after* the fatal. **Caveat cutting against
it:** the storm makes timeouts dense (~1 per 40–90 s), so P(one within ±1 s) is ~2–5 % by chance —
low, but n = 1 against a 5 % base rate is exactly the shape of a coincidence. **Recorded, not acted on.**

**5. No external trigger, checked on BOTH sides.** The AP's `dmesg` has **zero lines** in
3490.000 → 4078.999 s — the 589 s between fatal #4's recovery and the first storm resync. The host's
`journalctl -k` is **silent**: its last entry before the storm is **17:34:12**, and the onset is
~18:11:20 host time — with a **positive control** (the same query returned the 17:04–17:34 lines, so
this is a real negative and not a broken query). **The onset is AP/modem-internal.** Note this is
§8.14's lesson applied *before* blaming anything: the host was checked first and excluded cheaply.
**⚠ THIS PARAGRAPH IS CORRECTED BY §8.15.5 AND MUST NOT BE QUOTED ALONE.** Both negatives are true and
**neither is sufficient**: the device runs `/usr/sbin/modem-bearer-watchdog`, which **enforces
`power/control=auto` and `autosuspend_delay_ms=1000` on the bam-dmux device every 10 s with no log
line** and can also do `wds-go-dormant`, a bearer down/up (changing the IP) and a full remoteproc SSR —
and `logread` is a ~15-minute ring buffer that had already rotated past the onset. **The trigger
question is OPEN, not answered.**

**6. The runtime-PM rate is UNCHANGED across the onset.** `pm_suspend_attempts` = 623 at uptime ~4720
⇒ **0.132 /s** averaged over the boot; measured during the storm, **7 per 55 s = 0.127 /s** and
**32 per 247 s = 0.130 /s**. So this is **not "more churn"** — it is the *same* churn failing to
complete its handshake.

**7. ⚠ CORRECTION to §8.15.2 — the `pm_suspend` deficit is ACCOUNTING, not a defect.** Read from the
definitions: `pm_suspend_attempts` is incremented in `bam_dmux_runtime_suspend()` immediately before
`bam_dmux_pc_vote(false)` (`:1389`); `pm_suspend_completions` is incremented in exactly **two** places,
both requiring `pm_suspend_start_ns` to still be non-zero (pc_irq deassert `:1326`, `bam_dmux_pc_ack_irq`
`:1354`); and **`pm_suspend_start_ns` is cleared by `bam_dmux_runtime_resume()` `:1417` and by the
RESYNC handler `:797`**. A suspend attempt pre-empted by a resume or a resync before its ACK lands can
therefore never increment the completion counter. **The deficit is the count of suspend/resume and
suspend/resync races — 11 of 623 = 1.8 % — and its growth is a churn proxy, not a lost ACK.** Withdrawn
as an anomaly.

**8. The data plane is FULLY FUNCTIONAL during the storm.** `wwan0` UP with its address, IPv6 and
default route; `ping -c 4 -I wwan0 8.8.8.8` → **4/4, 0 % loss** (40.521 / 168.794 / 512.092 ms). **The
storm is NOT the data stall.** Worth stating plainly, because the storm looks alarming and the stall is
the user's actual complaint.

**9. Effect on §8.15's three readings.** Reading **(A)** ("the resync is a poison pill") is now strongly
**disfavoured**: 15 resyncs, only 1 preceded a fatal, and 8+ minutes of storm produced no fatal with the
data plane working. Readings **(B)/(C)** — a failing PC-handshake *regime* of which the resyncs are
symptoms — remain open and are the better fit. **Necessity survives (3/3 for `a2_power.c:2949`);
sufficiency is now 3/20 or worse as this boot's denominator grows.**

**10. S1 — pre-registered as P5 and RUN: does 1 Hz traffic suppress the storm? ✅ CONFIRMED.** A lost
edge requires `pc_state == 0` **and** the line asserted, so it can only happen on the way **out of** a
quiesced state. S1 = 600 consecutive 1 Hz pings with both counters sampled once a second.

**Independent corroboration, from the 60 s sampler that ran *before* S1** (`X_storm_sampler_sh.sh`):
it caught **`pc_state=0` with `quiesce_ms` 2828–4566 in 6 of 10** pre-S1 samples, and
`pm_suspend_attempts` climbing **570 → 636** (66 attempts in 499 s = **0.132/s**) — before S1 the modem
really was quiescing and the AP really was suspending.

**The result is a clean A/B/A inside ONE run** (§8.15.4), and the arms are the traffic and the absence
of traffic — not any change I made.

---

### §8.15.4 ✅ S1 SCORED — P5 IS CONFIRMED: 1 Hz traffic suppressed the storm AND every suspend for 364 s, and a FATAL FIRED ANYWAY, ON THE CLOCK, INSIDE that window

**A/B/A inside one run. Raw record: `V_…` PART 8.**

| window | duration | `pc_resync_count` | `pm_suspend_attempts` | `pc_state=0` samples |
|---|---|---|---|---|
| **A — pre-S1 (storming)** | 499 s | 7 → 20 (**1 per 36.3 s**) | 570 → 636 (**0.132 /s**) | 6 of 10 |
| **B — S1, 1 Hz traffic** | **364.34 s** | 20 → **20** (**0**) | 636 → **636** (**0**) | **0 of 365** |
| **A′ — post-S1 (traffic stopped)** | 293.9 s | 20 → 27 (**1 per 42.0 s**) | 636 → 673 (**0.126 /s**) | — |

**Expected in B at the A rates: 10.0 resyncs and 48.1 suspend attempts. Observed: 0 and 0**
(Poisson p(0|10.0) = **4.5 × 10⁻⁵**). **P5 (≤ 20 % of the pre-S1 rate) is confirmed with a large
margin; the P5-falsifier is not met.**

**⚠ AND THE FIRST GLANCE AT THE TAIL LOOKS LIKE A FAILURE — it is not, and this is worth recording as
a trap.** The final line reads `resync=27 … susp=673`, i.e. the counters *did* move. They moved in the
**post-traffic** arm: `ping -c 600 -i 1` was **killed at seq=363** by fatal #6's SSR (last line of
`/overlay/s1.ping` = `ping: sendto: Network unreachable`, **no summary**; 364 packets at 1 Hz from
4844.38 ends at **5208.4**, 0.3 s before fatal #6 at 5208.721361). The SSR re-established the bearer
with a **new IP** (10.29.91.236/29 → 10.132.56.209/30), so the ping's socket died and **the traffic
stopped**. The storm returned at the pre-S1 rate the moment it did. **Read the instrument's own health
before scoring it — a tail that mixes a suppressed and an un-suppressed phase is not one sample.**

**★ AND THE FATAL FIRED INSIDE THE SUPPRESSION WINDOW, WHICH SEPARATES TWO THINGS THAT HAVE BEEN
TANGLED FOR ROUNDS.** Fatal #6 fired at AP **5208.721361 s**, with `pm_suspend_attempts` frozen at
**636** for 364 s and `pc_state = 1` in every sample:

* **the STORM is AP-runtime-PM-GATED** — no suspends ⇒ no resyncs;
* **the FATAL is NOT** — it fired on schedule with the modem never suspended.

**This is the cleanest separation the corpus has between the two. It kills idle-avoidance as a
mitigation for the FATALS while keeping it available for the STORM**, and it is consistent with the
fatal being modem-internal. It also **contradicts a natural reading of Doc 162**: under sustained
traffic the fatal was *not* suppressed and was *not* substituted — it was the **clock signature**
(`lte_ml1_sleepmgr_stm.c:4054`) at **902.286373 s of modem uptime**, **19.4 ms from Doc 162's own
902.267 s** figure (see §8.13.4).

**What S1 does NOT establish.** It does not show traffic suppresses the fatals — it shows the
opposite. It does not show the storm is **harmful**: **364 pings at 1 Hz through the storming regime
had 0 % loss**, so the storm is visible in the counters, not in the data plane. And it is one run on one
boot — the A/B/A is within-run, the strongest form available, but **n = 1** for the suppression event.

**S2 — pre-registered, NOT yet run: does a low rate (which permits quiescence) cost packets or
latency?** S1 shows the storm needs quiescence, so the storming regime is a **low-rate** one. S2 =
**one ping every 15 s × 80**, recording each ping's uptime, RTT, and the resync/timeout counters
**immediately before and after** it. **P6:** pings whose wake missed the edge (resync delta ≠ 0 across
that ping) show an RTT spike of up to the **100 ms watchdog period**. **P6-FALSIFIER:** no RTT
difference between pings with and without a lost edge. **S2 also doubles as a patch-812 regression
check** — 15 s of idle is exactly the condition Doc 156 measured as `replies=0/1` before 812 and
`dtx=1 drx=1` after it. (`/overlay/s2.sh` is written and syntax-checked; it must be launched with the
ssh held open, because a backgrounded job inside a one-shot ssh dies at session exit.)

---

### §8.15.5 ⚠ CORRECTION — §8.15.3 §5's "NO EXTERNAL TRIGGER" IS **UNSAFE**: the device runs a userspace watchdog that writes the bam-dmux PM knobs every 10 s **with no log line**, and `logread` is a 15-minute ring buffer that has already rotated past the onset

**Both of §8.15.3 §5's negatives are true and neither is sufficient.** `dmesg` really has zero lines in the 589 s before the onset, and the host's `journalctl -k` really is silent (with a positive control). **But a userspace action can change the modem's power behaviour without producing a kernel log line** — and I checked the kernel and the host, not the device's own automation. That is §8.10's mistake with `qcom-carrier-autocfg`, repeated.

**What is actually running — `/usr/sbin/modem-bearer-watchdog` (485 lines), from `uci show modem-watchdog`:**
`general.enabled='1'`, `stall_timeout='60'`, `check_interval='10'`, `keepalive.enabled='0'`, **`recovery.ssr_enabled='1'`** (default on). Its body:

| line | action |
|---|---|
| **:111-118** | **enforces `power/control = auto` and `autosuspend_delay_ms = 1000` on `4080000.remoteproc:bam-dmux` — every 10 s, via sysfs, with NO dmesg line and NO log line** |
| :271 | Stage 1 = `qmicli … --wds-go-dormant` (resets the RRC channel) |
| :299, :351 | Stage 2 = `ubus call network.interface.modem down` / `up` — **which would change the bearer IP** |
| :403-416 | Stage 3 = `echo stop > …/remoteproc0/state` — **a full remoteproc SSR** |

**:111-118 is the one that matters.** `autosuspend_delay_ms = 1000` means the modem runtime-suspends **one second** after traffic stops — and **the AP runtime-PM suspend/resume cycle is exactly the mechanism §8.15.4 just showed gates the storm.** So the single knob that controls the storm's mechanism is pinned at 1000 by a userspace script my checks could not see. (In steady state the `!=` guards mean no write actually occurs — verified current state `auto/1000/suspended` — so the watchdog is not *actively* changing anything, but it *will revert* a change, and it explains why the value is 1000 rather than whatever the DT chose.)

**And the onset is now unrecoverable:** `logread` is a **~15-minute ring buffer** whose earliest line at check time was **12:43:29**, against an onset at **~12:27**. A `grep -c` over it for `modem-stall-watchdog|modem-keepalive|Stage [123]` returns **0** — which is evidence about the last 15 minutes, not about the onset.

**⇒ CORRECTED STATEMENT, replacing §8.15.3 §5:** *no trigger is visible in `dmesg` or on the host; the device additionally runs a watchdog that can write the bam-dmux PM knobs, reset the RRC channel, bring the bearer down/up (changing the IP) and perform a full remoteproc SSR; its own log is a 15-minute ring buffer and has already rotated past the onset. **The trigger question is OPEN, not answered.***

**THE MISSING INSTRUMENT IS DEPLOYED — `/overlay/logroll.sh`.** Running now (ssh held open) and added to `/etc/rc.local` with a pidfile guard (backup `/overlay/rc.local.bak.logroll`, `sh -n` verified before the move). It writes ONE file, `/overlay/logroll_<boot_id>.log`, containing (a) `logread -f` minus dropbear noise, `sync` per line, and (b) a 1 Hz sample of `power/control` + `autosuspend_delay_ms` + `runtime_status`, emitting a `PMKNOB` line only on change. **It interleaves the userspace log, the kernel log and the PM state machine on one timeline** — verified working, and it also gives the §8.13 window the device-side attribution instrument §8.14 had to reconstruct after the fact from the host. **Limitation, stated because it is the same trap twice: it starts now, so it cannot answer the question for the onset that motivated it — it is an instrument for the NEXT onset.**

**FIRST DATA FROM S2, AND THE STORM'S COST IS LATENCY, NOT LOSS.** After 14 pings (one per 15 s) every ping replied, but the **one ping during which a `pc-ack timeout` fired took `551.417 ms` against a ~41–43 ms baseline** (and its `resync` delta was 0 while its `timeout` delta was 1). **⇒ the correct predicate for the first-packet-after-idle case is the TIMEOUT delta, not the resync delta — so P6 as pre-registered was the wrong predicate, and S2 must be scored on the timeout delta.** n = 1 so far; S2 runs 80 pings.

---

### §8.15.6 THE STORM'S ONSET IS THE **FIRST LOST EDGE OF THE BOOT** (AP 3477.894835) — and the PM churn that carries it is **Android-parity**, so the churn is not the defect

`dmesg | grep -c "lost edge"` = 39 and `pc_resync_count` = 40. The **first** one is

```
[ 3477.894835] bam-dmux: RX watchdog: PC line asserted while pc_state=0 (lost edge), resyncing
[ 3477.976314] remoteproc: handling crash #4          <-- +81.479 ms later
```

**The control matters, because "the first X in dmesg" is normally a ring-buffer artefact (trap 16).**
`dmesg | wc -l` = 889 and the first line is `[ 0.000000] Booting Linux on physical CPU 0x0` — **the
ring did not wrap**, and crashes #1–#8 are all present with the earliest at `[ 913.648811]`. So the
absence of earlier lost edges is real, not an instrument horizon.

Within this boot:

| | lost edges | window |
|---|---|---|
| before AP 3477.894835 | **0** | 3477 s |
| after AP 3477.894835 | **39** | 2600 s |

At ~1 suspend per 6.9 s (§8.15.4/s3) that is **0 failures in ~500 suspend cycles, then ~5 % forever**.
P(0 in 504 │ p = 0.05) ≈ 10⁻¹¹ — a genuine step change, not a tail.

**And the storm survives crashes #5, #6, #7 and #8 — four SSRs as of this writing; §8.21 later
extended the span to SEVEN modem reloads (#4–#10) before the storm ended at AP 7417.446456.** It is not created by an SSR and not
reset by one; it is a persistent AP-side/driver-side state established at AP 3477.89. Note the
direction: the first storm event *precedes* fatal #4 by 81 ms, so the storm did not follow the fatal —
the fatal's own antecedent is the storm's first member.

**⚠ BUT THE CHURN ITSELF IS NOT THE DEFECT.** Telemetry at uptime 6118.48:

```
pm_suspend_attempts 766   pm_resume_attempts 766
pc_vote_tx_count    766   pc_unvote_tx_count 766
pc_resync_count      40   pc_timeout_count    44
pc_irq_count       1514   pc_ack_irq_count  1526
```

The AP runtime-suspends bam-dmux **766 times in 6118 s = 1 per 8.0 s** (instantaneous 1 per 6.9 s).
**Android does 918 modem power-collapses in 54 min = 1 per 3.5 s, with 0 % ping loss and no fatal.**
So *suspending is not the defect* — Android suspends **more** and is clean. The defect is that
OpenWrt's handshake **fails ~5 % of the time** (`40/766 = 5.2 %` resyncs, `44/766 = 5.7 %` timeouts).
That reframe is what makes §8.16 the right next move.

**S1 IS INDEPENDENTLY CORROBORATED BY A SECOND INSTRUMENT.** The 60 s storm_sampler's series
(uptime 4345.9 → 6285.5) contains **six consecutive samples, 4900.0 → 5177.6 (~332 s), with
`dresync = 0`, `dtimeout = 0` and `susp` frozen at 636/625** — a completely separate sampler seeing the
same freeze S1 produced with 1 Hz traffic. Two instruments, one result.

---

### §8.16 ★ THE ANDROID DRIVER IS THE GROUND TRUTH WE NEVER DIFFED — and the handshake timeout is **250 ms where Android's is 2000 ms**

**This is the round's main deliverable.** The Android reference has been in this tree the whole time:

```
GitIgnore/android_kernel_zte_msm8916/drivers/soc/qcom/bam_dmux.c            2798 lines
GitIgnore/android_kernel_zte_msm8916/drivers/soc/qcom/bam_dmux_private.h    182 lines
vs
openwrt/build_dir/.../linux-6.12.94/drivers/net/wwan/qcom_bam_dmux.c        2700 lines
```

**⚠ SELF-CORRECTION, MADE IMMEDIATELY.** My first reading was "Android uses SMSM, OpenWrt uses a GPIO
IRQ." **That is WRONG.** The OpenWrt driver is *also* SMSM-based (`:102-103`
`struct qcom_smem_state *pc, *pc_ack; u32 pc_mask, pc_ack_mask;`, written with
`qcom_smem_state_update_bits()` at `:268`). Both drive the handshake through shared-memory bits with an
IRQ notification. Recorded because it is exactly the *read the definition, not the name* trap, and I
walked into it in the first minute.

The **ordering** is also equivalent — both clear the completion, then set the bit:

```
Android  ul_wakeup()        : INIT_COMPLETION(ul_wakeup_ack_completion);  power_vote(1);
OpenWrt  bam_dmux_pc_vote() : reinit_completion(&dmux->pc_ack_completion); :267
                              qcom_smem_state_update_bits(dmux->pc, ...);  :268
```

**FOUR STRUCTURAL DIFFERENCES REMAIN, and they are the finding:**

| | Android (`bam_dmux.c`) | OpenWrt (`qcom_bam_dmux.c`) |
|---|---|---|
| **wakeup-ack wait** | **`UL_WAKEUP_TIMEOUT_MS = 2000`** (`:237`; used `:1913/:1925/:1933`) | **250 ms** (`:2066`) |
| previous down-ack | `wait_for_ack`, set in `ul_powerdown()` (`:1700`), waited in `ul_wakeup()` (`:1912-1919`) | **absent** |
| recovery timer | `UL_TIMEOUT_DELAY = 1000` (`:235`) + `ul_timeout_work` → `ul_timeout()` (`:1776`) | **absent** |
| vote policy | reference-counted, on demand (`ul_ondemand_vote`, up on TX, down 1 s after last TX) | **unconditional on every runtime-PM resume** |
| `a2_pc_disabled` | `wait_for_dfab` + `vote_dfab`/`unvote_dfab` + wakelock (`:1697`, `:1879`) | `return -EBUSY` from suspend (`:2010`) — never suspends |

**THE WIRING, read out of the probe (`:2497-2513`) so the mechanism is not guessed:**

```c
dmux->pc_irq = platform_get_irq_byname(pdev, "pc");       /* modem's A2 power-control line */
pc_ack_irq   = platform_get_irq_byname(pdev, "pc-ack");   /* modem's ACK line */
dmux->pc     = devm_qcom_smem_state_get(dev, "pc", &bit);      /* AP writes the vote */
dmux->pc_ack = devm_qcom_smem_state_get(dev, "pc-ack", &bit);  /* AP writes its own ack */
...
init_completion(&dmux->pc_ack_completion);
complete_all(&dmux->pc_ack_completion);                   /* :2521 — starts COMPLETE */
```

and the SMSM decode at `:2315-2316`: *"SMEM item 85 is the legacy SMSM shared-state block: word 1 is
SMSM_MODEM_STATE, bit 1 is SMSM_A2_POWER_CONTROL."*

So `modem pc-ack timeout during resume` means exactly: **the AP voted (wrote the `pc` bit) and the modem
did not assert its `pc-ack` line within 250 ms.** Android gives that same ack **2000 ms**.

**✅ THE FALSIFIER I STATED BEFORE CHECKING IS NOT FALSIFIED — and the accounting is exact:**

```
dmesg | grep -c "modem pc-ack timeout during resume"        -> 51   (the 250 ms wait)
dmesg | grep -c "modem pc_state wait timeout during resume" ->  2   (the 1000 ms wait)
dmesg | grep -c "channels not initialized after resume"     ->  2
pc_timeout_count                                            -> 53 = 51 + 2
```

**96.2 % of the failures are the 250 ms ack wait**, so this is not a "both waits fail" case and the
comparative fix is a constant, not a redesign. **And the mechanism and the measurement agree:** the
resume gives up after 250 ms and proceeds, delaying the packet that triggered the resume — which is
precisely the **416.227 / 551.417 / 398.144 / 431.556 ms** RTT spikes S2 measured against a ~41–43 ms
baseline, each paired with a `pc_timeout_count` increment.

**⚠ SCOPE THIS HONESTLY — it is a LATENCY defect, not a stability one.** It does not touch the 902 s
fatal: §8.15.4 measured that fatal firing with the AP's PM frozen for 364 s, so the fatal is not
PM-gated, and a faster ack would not suppress it. The claim is narrow: **~5 % of 766 resumes wait the
full 250 ms and delay one packet by 250–550 ms; Android's 2000 ms window would absorb it.**

**Two candidate fixes, in order of preference:**
1. **On timeout, re-read the state instead of treating the timeout as final** — mirroring the driver's
   own `bam_dmux_pc_line_asserted()` idiom (`irq_get_irqchip_state(..., IRQCHIP_STATE_LINE_LEVEL, ...)`,
   `:291`), which exists for the *pc* line but was never given to the *ack* path.
2. **Widen 250 → 2000 ms** (straight Android parity, one constant) — simpler, but a genuinely-late ack
   then costs 2 s instead of 250 ms.

**NOT DEPLOYED — it must not run inside the open §8.13 window**, because it changes PM timing and
§8.13's whole point is a clean PM-confounded-free 20-fatal A/B. Prepare it; deploy after the window
closes. Falsifier for the fix: the 416–551 ms spikes vanish **and** `pc_timeout_count` stops rising,
with `pc_resync_count` unchanged.

---

### §8.17 NEW SIGNATURE `a2_task.c:3179` — a CASCADE at 120.76 s, with a **negative** antecedent, and the storm rate does **not** predict it

```
[ 6231.169902] fatal error received: a2_task.c:3179:
[ 6231.176595] handling crash #8
delta from fatal #7 = 6231.169902 - 6110.407029 = 120.762873 s
```

Far outside the 902 s clock — **the first non-clock fatal of this boot**, and `a2_task.c` is a **third
`a2_*` file** (previously only `a2_power.c`; the corpus also has `lte_ml1_sleepmgr_stm.c`).

**Its antecedent is a NEGATIVE.** The window 6160 → 6231.17 contains seven PC events, and the nearest
is a `pc-ack timeout` **12.77 s** before the fatal — **not** the 74–86 ms band. The tight antecedent
rule is signature-specific:

| signature | tight antecedent |
|---|---|
| `lte_ml1_sleepmgr_stm.c:4054` | 0/3 |
| `a2_power.c:2949` | **3/3** |
| `a2_power.c:1189` | 0/1 |
| **`a2_task.c:3179`** | **0/1** (new) |

The device was `runtime_status: active` across it — consistent with the fatal not being PM-gated
(§8.15.4).

**A 6× storm-rate jump that is NOT a precursor.** Telemetry 6118.48 → 6278.00 (dt = 159.5 s):

```
pc_vote_tx   766 -> 789   +23   (1 per 6.9 s, unchanged)
pc_resync     40 ->  44   +4    = 17 %   (baseline 5.2 %)
pc_timeout    44 ->  51   +7    = 30 %   (baseline 5.7 %)
```

**⚠ A CLAIM I FIRST MADE AND THEN HAD TO WEAKEN — the rate is elevated, but "precursor" is not
supported at this n.** My first pass reduced the sampler's 55 s series and concluded the elevation was
"within the range seen elsewhere". **That is too strong in one direction and too weak in the other:**

* `dtimeout = 4` at samples 6119.3 and 6174.7 **is the maximum of the whole 33-sample series**, and it
  is first reached exactly in the two samples immediately before fatal #8 — so the elevation is real
  as a *rank*, not merely a wobble.
* But `dtimeout = 3` occurs three times earlier (5675.8, 5897.8, 6063.9) and `dresync = 3` also occurs
  earlier, so 4 is **one step above a value reached repeatedly**, on small counts in a heavy-tailed
  series.
* **And the "5.2 % baseline" I was comparing against is wrong.** `pc_resync_count / pm_suspend_attempts
  = 40/766` is a **lifetime average**, and it is diluted by the **3477 s before the storm existed**
  (§8.15.6). The per-sample rate is `dresync` 1–3 against `susp` 7–9 per 55 s = **~15–40 %**, so the
  honest post-onset failure rate is **15–40 %, not 5 %**.

**⇒ Corrected statement: the storm's post-onset failure rate is ~15–40 % of suspend cycles (not 5 %,
which is a lifetime artefact); the pre-fatal-#8 window sits at the top of the observed range but is not
separated from it. NOT established as a precursor. Trap 17.**

---

### §8.18 S2 SCORED — every loss is an SSR outage, the storm's cost is latency, and `logroll`'s first catch exposes its own limitation

**S2 (80 pings, one per 15 s), 41 samples scored:**

| uptime | result | cause |
|---|---|---|
| 6115.36 | LOST | fatal #7's SSR (fatal 6110.407, wwan0 back 6129.51) |
| 6130.44 | LOST | fatal #7's SSR (ping crossed the ifup) |
| 6226.85 | LOST | fatal #8's SSR (fatal 6231.17; 5.0 s full timeout) |
| 6246.98 | LOST | returned in **10 ms** with `quiesce_ms = 12594` — impossible for a real timeout, so ping failed *instantly*: the interface was DOWN |

**All four losses are SSR outages. Between SSRs, at 15 s idle, the data plane is intact.** The storm's
cost is latency (the §8.16 spikes), not loss. **P6's predicate was wrong as pre-registered** — the
551.417 ms ping had `pctimeout 33/34` (Δ+1) and `resync 35/35` (Δ0). **Pair on the TIMEOUT delta.**

**PATCH-812 REGRESSION CHECK: PASS** over five SSRs — `tx_defer_queued 771`, `tx_defer_submitted 771`,
`tx_defer_preserved 751`, `tx_defer_wiped 0`, `tx_defer_wiped_live 0`, `tx_sweep_guard_hits 0`
(97.4 % preserved, zero destroyed).

**`logroll.sh` earned its place on its first outing** — it captured the userspace side of fatal #8's SSR
on one file: 14 × `qcom-time-daemon` errors (`[QMI-TIME] Modem SSR / Disconnect detected (node=0
port=11). Resetting state machine.`), ModemManager `[modem7] port 'wwan0qmi0' no longer controllable,
reprobing`, `netifd: Network device 'wwan0' link is down`, then `[modem8] simple connect state (8/10):
bearer` → `bearer18` `10.155.30.209/30` → `modem-blue-led: LTE interface up`. PMKNOB across the fatal:
`suspended` at 6233.42 (was active) → `active` at 6247.96 — **the autosuspend churn resumes ~19 s after
the SSR.**

**It did NOT reveal a userspace trigger.** No `Stage 1/2/3`, no `modem-stall-watchdog`, no `keepalive`
line in the window. §8.15.5's "the trigger question is OPEN" therefore stands — but
`modem-bearer-watchdog` acting in this window is now a **negative observed with the instrument in
place**, which is progress over a negative inferred from absence.

**⚠ INSTRUMENT LIMITATION, STATED NOT HIDDEN.** The `logread -f` stream in the file is **batched**, not
interleaved in real time with the PMKNOB stream: in the head of the file, 40 consecutive PMKNOB lines
(12:56:37 → 12:59:24) appear **before** the kernel lines from 12:56:38 → 12:59:00. So the PMKNOB stream
is real-time and the logread stream lags in bursts; **the two are not on one true timeline in the
file.** Cross-stream timing must not be read off it without first re-deriving the lag from the
`[ uptime]` field inside each kernel line, which *is* authoritative. (Trap 18.)

---

### §8.19 ★★ THE MODEM LEFT THE 902 s CLOCK AT FATAL #7 AND IS NOW CASCADING — AND THE RPM'S OWN LOG SHOWS A 13.24 s MODEM-SIDE VOTE STALL

This is the most important new result of the round, and it came from leaving `rpmring` running across a
fatal.

**(a) THE CASCADE.** The fatal interval has changed regime:

| fatal | AP time | signature | Δ from previous |
|---|---|---|---|
| #6 | 5208.721361 | `lte_ml1_sleepmgr_stm.c:4054` | 903.723 |
| #7 | 6110.407029 | `lte_ml1_sleepmgr_stm.c:4054` | 901.686 (**the clock**) |
| **#8** | **6231.169902** | **`a2_task.c:3179`** | **120.763** |
| **#9** | **6397.771058** | **`a2_power.c:1189`** | **166.602** |
| **#10** | **6580.507441** | **`a2_task.c:3179`** | **182.729** |

**After fatal #7 the modem has not returned to the 902 s clock.** Three consecutive fatals at
120.8 / 166.6 / 182.7 s, and `a2_task.c:3179` **repeats** (#8 and #10). This is a **different operating
regime**, not the idle timer.

**§8.13 SIXTH SAMPLE (fatal #9):** `SSR before shutdown` 6397.793072 → `MBA booted` 6397.920651 =
**0.127579 s**, inside the capture-OFF band (`0.119170–0.130237 s`), no `port failed halt`, AP survived,
and **patch 814's rebuild fired and succeeded** — `SSR powerup: modem pc_state=1 (waited 200 ms)` →
`SSR powerup: successfully reinitialized BAM channels and rings` → all 8 `CMD_OPEN`s by 6399.114.
**s8.13 bar: 6 of 20 post-disable fatals scored, 0 AP reboots.** ⚠ **The window now spans TWO regimes**
(1 idle-clock fatal #7 + 3 cascade fatals #8–#10); that must be said when the bar is read, because
"0 reboots across 20" would otherwise silently mix them.

**(b) ★★★ THE RPM VOTE STALL — the strongest modem-side evidence in the project.** `rpmring -t -n
500000 -i 4` captured 910 blocks / 40 709 records / 172.3 s of the RPM's own external log. It measured
the RPM tick rate at **19.1998 MHz** (vs 19.2 assumed, −0.00 %), and it found:

```
normal pattern: a 9-record vote cycle repeating at ~26-34 cycles/s
  R 00200000 <ts> 0000001c 00000144 ...                       <- 0x144
  R 00200000 <ts> 0000001c 000000d0 00000001 000004f8 ...     <- 0xd0, sequence number ++
  R 00200000 <ts> 0000001c 000000cb ...                       <- txn-open
  R 00200000 <ts> 0000001c 000000d1 00000001 616f646c ...     <- req, resource "ldoa"
  R 00200000 <ts> 0000001c 000000d4 / 000000d5 <same res> ... <- res-request
  R 00200000 <ts> 0000001c 000000d2 00000001 00716572 ...     <- txn-begin, "req."
  R 00200000 <ts> 0000001c 00000143 ffffffff fffff77d ...     <- 0x143
  R 00200000 <ts> 0000001c 000000cd ...                       <- txn-close
```

and then, at **AP 6469.8 → 6483.1**, a gap in which the RPM wrote **exactly ONE cycle in 13.26 s**
(normally ~350). **The RPM's own timestamp proves the RPM was alive across it:**

```
last record before  : ee55a433
first record after  : fd8610b1
delta = 0xfd8610b1 - 0xee55a433 = 254,266,494 ticks / 19.2e6 = 13.243 s
wall-clock gap      = 13.264 s
```

**The two agree to 21 ms, so the RPM's clock ran while it logged nothing. ⇒ the stall is UPSTREAM of
the RPM — i.e. in the MODEM.** This is the live signature of the RE's claim that the Q6 power-collapse
vote chain stalls in **`rpm.sync` (0xc08bebd0), whose RPM flush loops have no timeout** — and here the
stall **recovered**.

**The AP saw the same event from its side:**

```
6469.070335  RX watchdog: PC line asserted while pc_state=0 (lost edge), resyncing
6469.330305  modem pc-ack timeout during resume
6478.290311  modem pc-ack timeout during resume
S2 6468.94   rtt = 911.398 ms   (the LARGEST RTT of the whole S2 run; prior max 551.417)
```

So one modem stall appears as a 13.24 s RPM silence **and** a 911 ms delayed ping — two independent
instruments, one event. The resource names in the cycles are the power/clock rails (`ldoa` 8124,
`smpa` 1038, `clk0/1/2/a`, `bslv` 3788, `bmas` 2866), and **`deep-sleep / rail resources: NONE`** was
reported — i.e. no rail actually went down in the capture.

**⚠ WHAT THIS DOES AND DOES NOT ESTABLISH.** It establishes, measured and from two sides, that **the
modem can stop voting to the RPM for 13 s while the AP's data plane degrades by ~1 s** — a modem-side
stall, visible to the AP, that is *not* a fatal. It does **not** establish that this stall is the 902 s
fatal's mechanism: the capture spans **one** stall and the stall happened **after** fatal #9, while
**at fatal #9 itself the RPM did NOT go silent** (gaps around 6397.8 were 0.35–0.59 s — the RPM was
*busy*, logging the SSR). **One sample each way. The capture is still running across fatal #11, and the
discriminating question is: does a 13 s silence precede a fatal, or is it an independent recurring
stall?** → **✅ ANSWERED NEGATIVE IN §8.20** (the capture ran across fatals #9, #10 *and* #11: the
nearest quiet period before every one of them is the **normal cadence**, and the three ≥ 8 s stalls in
the capture all **recover**).

**(c) S2 FINAL — 58 SAMPLES, 9 LOSSES, AND THREE OF THEM ARE NOT SSR OUTAGES.**

> ⚠ **CORRECTED 2026-09-21.** This section first read **"56 samples, 7 losses … n = 2"** and **both
> figures were wrong.** The file is authoritative: `grep -c "rtt=" /overlay/s2.log` = **58**,
> `grep -c "rtt=LOST" /overlay/s2.log` = **9**. Two `LOST` lines (**6576.27**, **6596.37**) were
> written after this section was scored, and a third loss (**6130.44**) was misclassified — see the
> refutation below. The silent-loss class is **n = 3, not n = 2.**

| uptime | wall | class | cause |
|---|---|---|---|
| 6115.36 | 0.01 s | **interface DOWN** | fatal #7's SSR — ports attached 6112.87; ping failed *instantly* |
| **6130.44** | **5.03 s** | **SILENT** | **17.6 s after the SSR completed — no handshake event, no SSR** |
| 6226.85 | 5.05 s | pre-fatal | 4.3 s before fatal #8; `pc-ack timeout` 6218.398 |
| 6246.98 | 0.01 s | **interface DOWN** | fatal #8's SSR (`quiesce_ms=12594`) |
| **6368.31** | **5.00 s** | **SILENT** | no `dmesg` correlate within ±19 s |
| **6388.34** | **5.01 s** | **SILENT** | no `dmesg` correlate within ±19 s |
| 6408.39 | 0.01 s | **interface DOWN** | fatal #9's SSR (`quiesce_ms=7404`) |
| 6576.27 | 5.01 s | pre-fatal | 4.2 s before fatal #10; `pc-ack timeout` 6566.210 |
| 6596.37 | 0.01 s | **interface DOWN** | fatal #10's SSR (`quiesce_ms=12055`) |

**The class is read off the wall time, not guessed:** a loss is **`interface DOWN`** when
`t_end − t_start ≈ 10 ms` — `ping -c 1 -W 5` *cannot* time out in 10 ms, so the ICMP failed on the spot
because the interface was down. `quiesce_ms` is non-zero on **exactly** those four samples (431 / 12594
/ 7404 / 12055) and zero on every other loss. A genuine timeout takes the full ~5 s.

**⚠ §8.18's CLASSIFICATION OF 6130.44 IS REFUTED — and it matters, because it moves the silent class
from n = 2 to n = 3.** §8.18 recorded 6130.44 as *"ping crossed the ifup"*, i.e. an SSR outage. The
`dmesg` timeline says otherwise: fatal #7's SSR reached **`is now up` at 6111.927153**, the wwan0 ports
attached at **6112.389 / 6112.393 / 6112.869**, and the ping fired at **6130.44 — 17.6 s after the
interface was back** — then timed out for the **full 5.03 s**. Nothing in `dmesg` within ±19 s of it:
the nearest handshake event is `6111.869 pc_state wait timeout` (18.6 s before) and the nearest after is
`6150.125 lost edge` (19.7 s later). It is the same class as 6368.31/6388.34: **a real 5 s data-plane
timeout with nothing whatsoever in `dmesg`** — no lost edge, no `pc-ack timeout`, no fatal, no SSR.

**⇒ A new, unexplained failure mode, recorded with n = 3:** the data plane silently drops the packet
while the modem is in the cascade, with **no AP-side handshake signature and no modem event**.
Recorded, not explained.

**Counter accounting is now EXACT (§8.16's falsifier, re-measured on the full ring):**

```
pc_resync_count  81  ==  dmesg "lost edge"                 81
pc_timeout_count 78  ==  dmesg "pc-ack timeout" 76
                       + dmesg "pc_state wait timeout"  2   = 78   (100.0 %)
```

§8.16 recorded **51 + 2 = 53 (96.2 %)** against `pc_timeout_count` 53. The two missing `pc-ack` lines
were **ring incompleteness at the time of scoring**, not a second mechanism: on the full, verified-
unwrapped ring the two counters reconcile **exactly**. The falsifier survives at 100 %.

**(d) ⚠ THE CASCADE IS BOUNDED — 3 BEATS — AND THE MODEM RETURNS TO THE 902 s CLOCK. AND MY FIRST
READING OF THIS EXPERIMENT WAS CONFOUNDED AND IS WITHDRAWN.**

**The measurement that settles it:** fatal #11 fired at **AP 7483.839750**, signature
**`lte_ml1_sleepmgr_stm.c:4054`** — the clock — **903.332309 s** after fatal #10. I predicted AP
≈ 7482.8 s from fatal #10's modem boot; the miss is **1.04 s over a 902 s period**. Recovery:
`SSR before shutdown` 7483.863268 → `MBA booted` 7483.983180 = **0.119912 s**, no `port failed halt`,
patch 814's rebuild fired, AP survived ⇒ **§8.13 bar: 7 of 20 scored, 0 AP reboots at that point
(8 of 20 after fatal #12 — §8.13.6)** (boot-wide 11
crashes / 12 `MBA booted` / 2 `port failed halt` / `cmd_open` 96 = 8 × 12).

**⇒ THE CASCADE SELF-TERMINATED AFTER THREE BEATS (120.8 / 166.6 / 182.7 s) AND THE MODEM WENT BACK
TO THE 902 s CLOCK.** Operationally this matters: the cascade is **not** a permanent degradation. And
it means the "902 s clock" is really **"902 s of modem uptime"** — a cascade is a bounded departure
from it, after which the next reload restarts the clock.

**⚠ WHAT I FIRST CLAIMED, AND WHY IT WAS WRONG.** I stopped S2's traffic at **uptime 6600.88** and,
seeing no fatal in the next 434.6 s against 120.8/166.6/182.7 s intervals, wrote that **"the cascade
is traffic-dependent"** with P ≈ 6 %. **The cascade's last fatal was #10 at AP 6580.507441 — 20.4 s
BEFORE I removed the traffic.** So the intervention was applied **after** the event it claimed to
explain, and it can carry no weight. **An intervention applied after the event cannot explain the
event.** (Trap 19.)

**What actually survives from that window — and it is still worth having:** across 434.6 s with no
traffic and no fatal, the storm ran at **full rate**:

```
6600.88 -> 7035.44 (434.6 s), NO traffic:
  pc_resync_count      60 -> 70   (+10 = 1 per 30.6 s)
  pc_timeout_count     63 -> 70   (+7)
  pm_suspend_attempts 840 -> 879  (+39 = 1 per 7.8 s)
  => 26 % resync and 18 % timeout failure rates (the post-onset band, not the 5.2 % lifetime figure)
```

So the storm is **not sufficient** for a fatal — which is the same conclusion §8.15.4 reached from the
other direction, now with 434.6 s of concurrent observation rather than a 364 s window that happened
to contain one.

**⚠ THE ONE THING STILL NOT DISTINGUISHABLE.** The cascade's 4th beat would have been due at
AP ≈ 6580.5 + ~199 ≈ **6779.5 s**, and the traffic was removed at 6600.9 — **174 s before that**. So
"the cascade ended naturally at #10" and "the traffic removal suppressed a 4th beat" are **both
consistent** with everything observed (the clock origin being fatal #10's boot does *not* discriminate,
because with a suppressed beat there would be no reload to move the origin). **Stated, not resolved.**
Distinguishing them needs a run where the traffic is removed *before* a cascade starts.

---

### §8.20 ★ THE DISCRIMINATING QUESTION IS ANSWERED — **NO**: THE RPM VOTE STALL DOES NOT PRECEDE A FATAL

§8.19(b) left exactly one question open, and it was the right one to ask:

> *"does a 13 s silence precede a fatal, or is it an independent recurring stall?"*

`rpmring` was left running across fatals #9, #10 and #11, as instructed. The snapshot analysed here is
**`scratch/rpm11/rpm11.txt`** — **431 383 lines / 7627 `# NEW` blocks / 423 704 records**, spanning
**AP 6343.30 → 7794.35 s (1451.0 s)**, tick rate measured at **19.1997 MHz** (−0.00 %). It contains
fatal #9 (6397.771), fatal #10 (6580.507) and fatal #11 (7483.840).

**(a) THE ANSWER — the nearest quiet period before every fatal is the NORMAL cadence.**

| fatal | AP time | signature | nearest preceding quiet ≥ 1 s | cadence that era |
|---|---|---|---|---|
| #9 | 6397.771 | `a2_power.c:1189` | **1.268 s** | ~1.267 s |
| #10 | 6580.507 | `a2_task.c:3179` | **1.145 s** | ~1.12 s |
| #11 | 7483.840 | `lte_ml1_sleepmgr_stm.c:4054` | **3.141 s** | 2.3–3.6 s |

**There are exactly THREE gaps ≥ 8 s in the whole 1451 s, and NONE of them is immediately before a
fatal:**

| gap | duration | records written | AP events inside | relation to the nearest fatal |
|---|---|---|---|---|
| 6469.802 → 6483.065 | **13.264 s** | 9 | `6478.290 pc-ack timeout` | 72 s *after* #9, 97 s *before* #10 |
| 6563.623 → 6572.133 | **8.510 s** | 9 | `6563.800 lost edge`, `6564.023 pc-ack`, `6566.210 pc-ack` | **ends 8.4 s before #10** |
| 7044.160 → 7055.040 | **10.880 s** | 31 | `7044.180 lost edge`, `7052.390 pc-ack` | ~440 s before #11 |

**All three RECOVER**, and the modem then ran on for a further **97 / 8.4 / 428 s** without a fatal.
Fatal #11 itself fires at the **END of a 2.504 s gap** (`7481.353 → 7483.857`) — *below* that era's
cadence, i.e. the RPM was writing normally right up to it.

**⇒ THE RPM VOTE STALL IS AN INDEPENDENT, RECURRING, SELF-RECOVERING MODEM-SIDE EVENT (one per ~480 s
here) AND IT IS NOT THE FATAL'S MECHANISM.** §8.19's *"one sample each way"* is now **three samples,
all pointing the same way.** This closes §8.19(b) and confirms the scoping §8.19 claimed: the RPM stall
is a **latency/recovery** defect (like the §8.16 250 ms handshake), **not** a stability one.

**(b) WHY "QUIET PERIOD" MUST NOT BE READ AS "STALL" — the RPM's log is BURSTY.** This is the trap that
would have manufactured a false positive, and it is worth stating because it is a general trap.

The RPM does not write at a steady rate; it writes in **bursts**, and the inter-burst gap **changes
over the capture**:

```
AP 6343-6352   ~470-830 records/s        (near-continuous)
AP 6373-6397   9 records per 1.267 s     (one 9-record vote cycle at a time)
AP 6615+       63 records per ~3.1 s     (seven vote cycles at a time)
50.5 % of the entire capture is spent inside a gap >= 0.5 s
```

**Half the capture is "quiet" at ≥ 0.5 s**, so a global "is this gap long?" test is meaningless — a
stall is a **departure from the local cadence**. (The first version of this analysis divided each gap by
the median of *all* gaps; because most consecutive `# NEW` blocks are ~4 ms apart that median is ~0.03 s,
which scored every ≥ 500 ms gap at **30–400×** and was discarded. **Trap 20.**)

**(c) A MODEST, REAL ENRICHMENT — the AP's handshake events do cluster in RPM gaps, but weakly.**

| gap threshold | fraction of *time* inside such gaps | fraction of AP events inside | enrichment |
|---|---|---|---|
| ≥ 0.5 s | 0.5046 | 0.7833 | **1.55×** |
| ≥ 1 s | 0.4556 | 0.6333 | 1.39× |
| ≥ 2 s | 0.3890 | 0.5667 | 1.46× |
| ≥ 3 s | 0.3256 | 0.5167 | 1.59× |
| ≥ 5 s | 0.0610 | 0.1500 | 2.46× |
| ≥ 8 s | 0.0225 | 0.1000 | **4.44×** |

60 of the boot's 159 handshake events fall inside the capture, and all three ≥ 8 s gaps contain at least
one. **The 4.44× rests on 6 events in 3 gaps — record it as suggestive, not established.** Per kind, in
gaps ≥ 2 s: `lost edge` 18/35 = 51.4 %, `pc-ack timeout` 16/25 = 64.0 %.

**⚠ DIRECTION IS NOT ESTABLISHED.** Two readings fit equally well: the RPM stall delays the modem's
PC-ack, which the AP then reports as a timeout and a lost edge; **or** the AP's PC-vote churn is what
the RPM stalls *on*. The events inside the three stalls are not all at the same phase — `6563.800` and
`7044.180` sit **0.02–0.18 s *after*** the stall opens, while `6478.290` and `7052.390` sit
**8.2–8.5 s *into*** it — which does not discriminate. **Stated, not resolved.**

**(d) ⚠ CAPTURE-QUALITY CAVEAT — 49 RING OVERRUNS, 22 664 RECORDS LOST.** `rpmring` emits a
`# TRACK OVERRUN` line whenever the RPM writes more than the 8 KiB ring between its 4 ms polls:

```
# TRACK OVERRUN t=6399122970069 counter=0x03d0ee00 delta=54592 bytes (1706 records > ring)
... 49 such events, 22 664 records total
```

Those are windows where the RPM was writing **furiously** (a fatal's SSR teardown) and the tool could
not keep up. They do **not** affect the negative in (a) — an overrun is the *opposite* of a silence —
but the capture is therefore **not** perfectly lossless, and any future *"the RPM wrote nothing here"*
claim must be checked against `# TRACK OVERRUN` **and** the counter delta before it is believed:
**a gap whose counter delta is ≥ 8192 B is a ring turnover, not a silence.**

---

### §8.21 ★ THE STORM IS BOUNDED TOO — it ended at AP 7417.446, and a full modem reload did NOT restart it

§8.15.6 located the storm's **onset** exactly (AP 3477.894835, the boot's first lost edge). The
sampler's last samples show where it **stops**:

```
7394.0  resync= 79 (d+1)  pctimeout= 78 (d+0)  susp=924/890  fatals=10
7449.4  resync= 81 (d+2)  pctimeout= 78 (d+0)  susp=931/897  fatals=10
7504.7  resync= 81 (d+0)  pctimeout= 78 (d+0)  susp=936/902  fatals=11   <- fatal #11's reload, mid-silence
7560.0  resync= 81 (d+0)  pctimeout= 78 (d+0)  susp=943/909  fatals=11
7615.3  resync= 81 (d+0)  pctimeout= 78 (d+0)  susp=949/915  fatals=11
```

The last lost-edge line in `dmesg` is **`[ 7417.446456] … (lost edge), resyncing`**. Verified live at
**uptime 8156.67**:

```
pc_resync_count        81   <- FROZEN  (identical at 7449.4 and at 8156.67)
pc_timeout_count       78   <- FROZEN
pm_suspend_attempts  1018   <- CLIMBING (924 at 7394.0; +94 across the silence)
runtime_status     active
fatals               11     <- fatal #11 (7483.840) came AND went inside the silence
rproc            running
```

**⇒ THE STORM EPISODE IS AP 3477.894835 → 7417.446456 = 3939.55 s, AND IT HAS NOW BEEN SILENT FOR 968 s
ACROSS ~126 SUSPENDS — INCLUDING ACROSS *TWO* FULL MODEM RELOADS** (fatal #11: ports re-attached
7488.1; fatal #12 at 8385.264: ports re-attached 8386.407 — and `pc_resync_count` held at **81**
through both). **Updated after fatal #12 (§8.13.6).**

**⚠ THIS REFUTES "THE STORM IS AP-runtime-PM-GATED" AS §8.15.4 STATED IT.** §8.15.4's S1 showed that
1 Hz traffic froze `pc_resync_count` **and** `pm_suspend_attempts` together for 364 s, and inferred the
storm is gated by AP runtime-PM. But freezing both is **equally** consistent with *"traffic suppresses
suspends"* and *"traffic suppresses resyncs"* — **the inference was never tested in the other
direction.** This does test it: **the AP suspends 94 more times with zero resyncs.** So the churn is
**necessary but not sufficient**, and the gating is **not** simply *"a suspend can produce a resync"*.

**What §8.15.4 established and still stands, unchanged:**
- 1 Hz traffic suppresses the storm in practice (the S1 A/B/A is intact), and
- **idle-avoidance does not suppress the FATALS** — fatal #6 fired inside S1's suppression window with
  `pm_suspend_attempts` frozen, and **fatal #11 fired inside this silence**.

**What is open instead — the storm needs a THIRD condition.** Candidates, none tested:
1. **the modem's own state** — but the storm began 81.5 ms before crash #4 and **spanned SEVEN
   consecutive modem reloads** (#4 through #10 — the whole span from its onset to its end), so it is
   *not* per-modem-boot; and **two further reloads (#11 and #12) did not restart it.** **This candidate
   is now the most disfavoured of the three:** the storm was present across seven modem boots and
   absent across the next two, so its state is not carried by a modem boot either way;
2. **the cascade regime** — but the storm ran **836.9 s past** the cascade's last fatal (#10, 6580.507),
   so it is not the cascade either;
3. **a specific PATTERN of suspends**, rather than their rate — untested, and the natural next
   instrument (log *which* suspends are followed by a resync, not how many).

**⚠ AND A 968 s SILENCE IS NOT PROOF THE STORM IS *OVER*.** A lull cannot be distinguished from an end
until a further onset is observed. The `storm_sampler` and `logroll.sh` are both still running and will
catch a restart; **what the silence does prove is that the churn rate alone does not sustain the storm,
and neither does a modem reload — in either direction.**

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
11. **A call site being reached is NOT evidence that its error branch executes — read the branch
    condition, not the call graph.** This round's own worst error, caught by the experiment it
    justified (§8.13.1): because `q6v5_mba_reclaim()` is called from `q6v5_stop()` (`:1672`)
    unconditionally, I concluded `port failed halt` must still print with the coredump disabled — and
    *wrote the correction into this document on that basis*. It does not print, because
    `q6v5proc_halt_axi_port()` opens with **`if (!ret && val) return;`** (`:961-964`) — "already idle,
    say nothing" — and the port is idle in the stop path but **live** in the dump path (which has just
    re-loaded the MBA). The message means *"the reclaim ran against a live modem"*. **How to apply:**
    when predicting whether a log line or a hang will survive a change, find the `if` that guards the
    print/blocking call and ask which *state* selects it — a reachable call site with an untaken
    branch produces nothing. **And the general form:** a correction written from a call graph is a
    hypothesis, not a finding; the experiment was already running, so it was settled in five minutes
    at zero cost — *run the cheap measurement before rewriting the conclusion.*
12. **An instrument's own first result can falsify the reasoning that deployed it — keep the
    instrument that tells you *when*, and read it before writing prose.** §8.12's `dmesg_roll` was
    built to characterise a *future* stall; it immediately produced the within-boot A/B (fatal #2 vs
    #3) that corrected §8.13, because it is a per-boot rolling copy of the kernel's own last lines
    with **timestamps**. The `port failed halt` *count* (2 vs 3 fatals) and the trace-pair *count*
    (2 vs 1) were both visible only because the log was retained in full.
13. **A DEVICE-side uptime decrease proves the device restarted — NOT that it restarted ITSELF. Read
    the HOST kernel log before attributing any reboot to the device (§8.14).** This is the round's
    most expensive error, because it produced a *published verdict*: §8.10 concluded "823 is NOT a
    complete fix" from a single 17:03 reset. The host log shows the dongle disconnecting from port
    `1-1` at `17:03:07`, a **`USB2.0 HUB` appearing on `1-1` for the first time at `17:03:13`**, and
    the dongle re-appearing at `1-1.2` at `17:03:41` — **I had physically installed the hub**, and the
    29 s outage *was* the unplug/replug. The three supporting observations (empty pstore, no 4th
    coredump, no ledger row) are **exactly what a power cycle produces**; they are not specific to a
    hang. **And the mirror error:** the host saw **39** dongle re-enumerations against ~19 AP
    uptime-decreases, including a clean re-enumeration at 14:39 with **no** AP reboot — so
    *re-enumeration ≠ AP reboot* and the two instruments disagree in **both** directions.
    **How to apply:** (a) for every reboot you attribute to the AP, `grep` the host's
    `journalctl -k` around it for `usb`, `xHCI Host Controller`, `USB disconnect`, `error -71`,
    `attempt power cycle`, and any *new* device (a hub appearing means a human moved something);
    (b) check whether the host's own controller is flapping — **24 `xhci-hcd.2.auto: remove, state 4`
    cycles in one day, 18 of them in a single hour**, each deregistering and rebuilding both root hubs.
    **But do NOT assume that makes the host the cause:** the ordering is mixed (in most episodes the
    dongle disconnects first, in at least one the controller is removed with no device disconnect), so
    the honest verdict on those windows is **"the USB link was unstable; direction not established"** —
    I labelled them "host-caused" first and had to retract it. **A correlation with a plausible
    upstream cause is still not a direction.**
    (c) **check the outage-duration distribution for a second mode** before fitting a mechanism — §8.11
    explained the 13–61 s events and never noticed a **1641 s** and a **926 s** one, and the
    `procd`-kicked watchdog **proves** those two are not hangs at all; (d) record **who did what
    when** (a physical hub install is an experiment, not an observation) — the hub was in memory as a
    device fact and was never connected to the reset it caused; (e) **beware a plausible mechanism with
    a bad base rate** — the host SMC's `Elec Cause 0x200000`/`Not charging` looked like the trigger at
    16:40:05, but it fires **8 times across the day** and does not align with the other controller
    episodes, so it was recorded as a **non-finding** rather than used.

14. **A TIGHT LAG IS NOT A CAUSE, AND NECESSITY IS NOT SUFFICIENCY — report BOTH denominators
    (§8.15).** The patch-808 "lost edge" resync sits **74–86 ms** in front of every `a2_power.c:2949`
    fatal ever observed — 3 of 3, across three different boots, mean 78.074 ms with an **11.842 ms**
    spread, the two closest **0.515 ms** apart. A lag that tight *feels* like a cause, and the
    mechanism is plausible: the resync does two things the modem can see
    (`bam_dmux_pm_restart()`, `bam_dmux_pc_ack()`). **The denominator is what stops it: 20 resyncs
    were observed and only 3 were followed by that fatal within 100 ms.** So the honest pair of
    statements is *"`a2_power.c:2949` ⇒ resync 3/3"* **and** *"resync ⇒ `a2_power.c:2949` 3/20"* — and
    the second one is the one that decides whether a fix is possible. **And §8.15.3 finished the job:
    the storm ran 8+ minutes PAST the fatal with no second fatal and 0 % ping loss, so the antecedent
    is not even on the path to the fatal.**
    **How to apply:** (a) when a candidate antecedent is found, **count its total occurrences**, not
    just the hits — a log search that returns only the pairs you were looking for has a denominator of
    one; (b) **tabulate the counter-examples next to the hits**, with what followed them instead
    (boot R's resync was followed by a *different* fatal 73.47 s later; boot S's four resyncs had no
    fatal before its log ended); (c) **state the instrument's reach** — boot S's "no fatal" is bounded
    by a log that ends 119 s later, boot R's is not; (d) **check the sample's provenance before
    counting it** — these samples came from `/overlay/q6trace.log`, which is an **append-across-boots**
    file where `grep -c` returns **5** for **3 distinct events**, so each was cross-checked against the
    independent ledger (6 of 6 match, 0–10 s lag) and the boots separated by their own `n = 1,2,3`
    sequences and the ledger's monotonic `coredumps` column; (e) **do not choose between the readings
    you have** — write all of them down with the observation that would separate them (here: a 100 ms
    watchdog period makes "the gap should vary with the AP's detection delay" a *measurable* prediction
    of one reading, and the 11.8 ms spread weakly disfavours it at roughly 4 % — **too weak to act on,
    so it is recorded, not concluded**).
15. **SCORE AN EXPERIMENT FROM ITS OWN HEALTH, NOT FROM ITS TAIL — and pick the interval the removed
    code actually sits in (§8.13.4, §8.15.4).** Two scoring errors were caught in one round, both by
    re-reading the instrument instead of the number:
    **(a)** S1's last line reads `resync=27 … susp=673` against a starting `20 … 636`, which reads as
    "the suppression failed". It did not: `ping -c 600 -i 1` was **killed at seq=363** by fatal #6's
    SSR (`sendto: Network unreachable`, **no summary line**), so the traffic stopped at 5208.4 and the
    counters moved in the **post-traffic control arm**. **The suppression held for 364 s of traffic —
    0 resyncs against 10.0 expected — and the A/B/A is inside one run.** *An instrument that dies
    mid-run leaves a tail that looks exactly like a failed hypothesis.*
    **(b)** the §8.13 A/B was nearly scored on **fatal → `is now up`**, which for fatal #5 is
    **1.443682 s** — as long as the capture-ON recoveries — even though its coredump-sensitive interval
    (`SSR before shutdown` → `MBA booted`) is **0.124531 s**. **Score the interval the removed code
    sits in, not a convenient longer one that adds a varying phase (the mpss load) on top.**
    **How to apply:** before reading a result off a log, confirm (i) the producer was still running,
    (ii) the run reached its planned end, and (iii) the metric's endpoints bracket the code you
    changed and nothing else.
16. **A NEGATIVE IN ONE INSTRUMENT CLASS IS NOT A NEGATIVE — AND A RING BUFFER HAS A HORIZON
    (§8.15.5).** §8.15.3 §5 concluded "no external trigger" from two genuine negatives: the AP's
    `dmesg` was empty for 589 s and the host's `journalctl -k` was silent. **Both were true, and the
    conclusion was still unsafe**, because the device runs
    `/usr/sbin/modem-bearer-watchdog`, which **enforces `power/control=auto` and
    `autosuspend_delay_ms=1000` on the bam-dmux device every 10 s via sysfs — producing no dmesg line
    and no log line** — and which can additionally do `qmicli --wds-go-dormant`, a
    `network.interface.modem down/up` (changing the bearer IP) and a **full remoteproc SSR**
    (`recovery.ssr_enabled='1'`). It also pins the one knob (`autosuspend_delay_ms = 1000`) that
    controls the storm's own mechanism.
    **How to apply:** (a) when you publish a negative, **name the instrument class you searched** and
    ask what class you did not — a silent kernel log says nothing about a sysfs write, a QMI command
    or a userspace recovery ladder; (b) **grep the image's own automation before concluding "nothing
    acted"** — `ps w`, `uci show`, and the `/usr/sbin` scripts that touch the device you are studying
    (this is §8.10's lesson, repeated); (c) **check the instrument's horizon**: `logread` here is a
    **~15-minute ring buffer**, so a check made 30 minutes after an event cannot see it — and a
    `grep -c` returning 0 then means "nothing in the last 15 minutes", not "nothing happened";
    (d) **the fix is an instrument, not an argument** — `/overlay/logroll.sh` now interleaves the
    userspace log, the kernel log and the PM state machine on one timeline, but it was deployed
    *after* the onset, so **it answers the NEXT question, not the one that motivated it**.

17. **A LIFETIME AVERAGE IS NOT A BASELINE — AND AN ELEVATION IS NOT A PRECURSOR UNTIL YOU HAVE
    REDUCED THE WHOLE SERIES (§8.17).** Two separate errors, made in the same paragraph, in opposite
    directions. **(i) The baseline was wrong.** I compared a 159.5 s window against
    `pc_resync_count / pm_suspend_attempts = 40/766 = 5.2 %` — but that is a **lifetime average
    diluted by the 3477 s in which the storm did not exist** (§8.15.6). The per-sample rate is
    `dresync` 1–3 against `susp` 7–9 per 55 s, i.e. **~15–40 %**. A cumulative counter over a regime
    change cannot be used as the baseline for a window inside the new regime — **always ask what the
    denominator was doing while the numerator was zero.** **(ii) The elevation was then dismissed too
    fast.** `dtimeout = 4` at samples 6119.3 and 6174.7 **is the maximum of the whole 33-sample
    series** and is first reached in the two samples before fatal #8; that it is only one step above a
    value (3) reached three times earlier is what makes it suggestive rather than established — not
    the fact that it is "within range".
    **How to apply:** when a rate looks elevated before an event, (a) **recompute the baseline from the
    same regime** the window is in, (b) reduce the **whole** series and quote the **rank** of the window
    (maximum? top decile?) rather than comparing means, and (c) check contamination — the 6119.3
    sample straddles fatal #7's own SSR, which inflates it. **State the conclusion at the strength the
    rank supports: "the series maximum, n = 33, not separable from a value reached three times" is
    honest; "6× the baseline" was not.**

18. **"ON ONE TIMELINE" IS A CLAIM ABOUT THE FILE, NOT ABOUT THE MECHANISM — check each stream's lag
    before cross-referencing them (§8.18).** `logroll.sh` was built to interleave `logread -f` with a
    1 Hz PMKNOB sample, and it does — but in the file, **40 consecutive PMKNOB lines
    (12:56:37 → 12:59:24) precede the kernel lines from 12:56:38 → 12:59:00**. The PMKNOB stream is
    real-time; the `logread` stream lags in bursts. Reading "the watchdog wrote the knob 3 s before the
    storm" off that file would be reading a buffering artefact as a causal ordering.
    **How to apply:** any multi-stream instrument must be validated by **anchoring the same event in
    both streams** and measuring the offset, exactly as the two capture-ON recoveries validated the
    §8.13 A/B. Where a stream carries its own authoritative clock (here the `[ uptime]` field inside
    each kernel line), **use that field, not the file order, for all cross-stream timing** — and state
    the limitation in the doc rather than leaving the next reader to discover it.

19. **AN INTERVENTION APPLIED AFTER THE EVENT CANNOT EXPLAIN THE EVENT — timestamp the intervention
    against the event before crediting one with the other (§8.19d).** I stopped the traffic at uptime
    `6600.88` because a fatal was overdue, found **0 fatals in the next 434.6 s** against cascade
    intervals of 120.8/166.6/182.7 s, computed P ≈ 6 %, and wrote *"the cascade is
    traffic-dependent."* **The cascade's last fatal was #10 at AP `6580.507441` — 20.4 s BEFORE the
    intervention.** The claim was withdrawn. The trap is not the arithmetic (which was fine) but the
    **ordering**: a suppression experiment only tests a cause if the suppression precedes the effect it
    is meant to prevent, and here the effect had already happened.
    **How to apply:** before crediting an intervention with an outcome, **write the two timestamps side
    by side** — intervention time and last-event time — and ask whether the intervention could
    *plausibly* have acted first. Watch specifically for the case where the "effect" is a **rate**: with
    a mean interval of ~157 s, stopping a process 20 s after the last event and then observing 435 s of
    quiet looks exactly like suppression, and is not. **The clean design is to remove the suspected
    cause BEFORE the next event is due, and to pre-register the expected beat times** — which is what
    made this recoverable: fatal #11 was predicted at AP ≈ 7482.8 s and fired at `7483.839750`
    (1.04 s), which is what proved the cascade had ended at #10 and the modem had returned to the clock.

20. **A "QUIET PERIOD" IS NOT A "STALL" UNTIL YOU KNOW THE CADENCE — AND THE BASELINE MUST BE LOCAL
    (§8.20b).** The RPM writes in bursts whose inter-burst gap **changes over the capture** (9 records
    per 1.267 s in one era, 63 records per ~3.1 s in another, near-continuous in a third), and **50.5 %
    of the entire capture sits inside a gap ≥ 0.5 s**. My first pass divided each gap by the median of
    **all** gaps — which is ~0.03 s, because consecutive `# NEW` blocks are ~4 ms apart — and scored
    every ≥ 500 ms gap at **30–400×**; it would have "found" a stall in almost every second of the
    capture, and would have "confirmed" whatever I was looking for. **How to apply:** before calling a
    gap anomalous, measure the cadence of the *same* stream in the *same* era, and if the rate is
    non-stationary, let the threshold move with it. **Sanity-check the baseline itself: if the baseline
    says half the data is anomalous, the baseline is wrong.** And when the stream is a log fed by a
    poller, check the poller's own loss channel (`# TRACK OVERRUN` here) before believing any silence —
    **a gap whose counter delta is ≥ the ring size is a turnover, not a silence.**
21. **A CORRELATION YOU HAVE NOT TESTED IN THE OTHER DIRECTION IS ONLY HALF A RESULT — and "frozen while
    X keeps moving" is the test (§8.21).** §8.15.4 saw 1 Hz traffic freeze `pc_resync_count` **and**
    `pm_suspend_attempts` together and concluded the storm is AP-runtime-PM-gated. That observation is
    **equally** consistent with the opposite reading — *"traffic suppresses suspends"* vs *"traffic
    suppresses resyncs"* — and S1 could not separate them because it moved both at once. §8.21 separates
    them for free: the AP suspends **94 more times with zero resyncs**, so the churn is necessary but not
    sufficient. **How to apply:** when two counters move together, name the mechanism you are actually
    claiming, then look for an observation where they come apart — **one counter still climbing while
    the other is frozen is the strongest test available, and it costs nothing if the instrument is
    already running.** Corollary for this project: **do not stop a running instrument when its question
    is answered** — §8.20's answer came entirely from a capture that was left running across three
    further fatals after it had already produced one result.
22. **"STILL RUNNING" IS AN OBSERVATION, NOT AN ASSUMPTION — AND A FAILED BACKGROUND LAUNCH LEAVES NO
    TRACE AT ALL (§8.20, §10).** While writing §8.20 I asserted *"the capture is still running"*; it had
    in fact **exited cleanly at its `-n 500000` sample limit** (660 382 records, last line a complete
    vote cycle, AP ~8600 s) some time earlier. **Two separate traps in one:**
    **(a)** `-n` counted **POLLS, not records**, so the run ended far sooner than "500 000 records"
    implied — **read the tool's own units before reasoning about its lifetime**;
    **(b)** I never checked, because an earlier `ps` had shown it and a long-lived instrument *feels*
    like a fact. **`ps w | grep "[r]pmring"` costs one round trip — run it before every claim that an
    instrument is live.** (This is the same shape as trap 16: a negative — or a positive — in one
    instrument class is not a statement about the world.)
    **And the recovery exposed a device quirk: `nohup` DOES NOT EXIST on this device** (only `setsid`
    and `start-stop-daemon`), so `nohup <script> &` fails with **no output file created at all** —
    indistinguishable from "the experiment ran and produced nothing". **`start-stop-daemon -S -b -x
    <script>` is the working recipe**, and a launch is confirmed only by seeing **both** the process in
    `ps` **and** the output file growing.

---

## §10 What's next

* **★★ HIGHEST PRIORITY — THE CASCADE, AND THE 13.24 s RPM VOTE STALL (§8.19).** **The modem left the
  902 s clock at fatal #7 and has not returned:** #7 `lte_ml1_sleepmgr_stm.c:4054` (901.686 s, the
  clock) → #8 `a2_task.c:3179` (**120.763 s**) → #9 `a2_power.c:1189` (**166.602 s**) → #10
  `a2_task.c:3179` (**182.729 s**), with `a2_task.c:3179` **repeating**. This is a different operating
  regime. **And the RPM's own log shows the modem stalling:** `rpmring` captured the RPM writing
  **exactly ONE 9-record vote cycle in 13.26 s** (normally ~350) at AP 6469.8→6483.1, while the RPM's
  **own timestamp advanced 13.243 s** across the gap (`ee55a433`→`fd8610b1` = 254 266 494 ticks ÷
  19.2 MHz, agreeing with the 13.264 s wall gap to 21 ms). **⇒ the stall is upstream of the RPM — in
  the modem** — which is the live signature of the RE's `rpm.sync` (0xc08bebd0) claim, and here it
  **recovered**. The AP saw the same event: a lost edge at 6469.070, `pc-ack timeouts` at 6469.330 and
  6478.290, and **`rtt = 911.398 ms`, the largest RTT of the whole S2 run**.
  **⚠ NOT established:** the capture spans **one** stall, and **at fatal #9 the RPM did NOT go silent**
  (gaps 0.35–0.59 s — it was *busy* logging the SSR). **One sample each way.** The discriminating
  question: **does a 13 s silence PRECEDE a fatal, or is it an independent recurring stall?** The
  capture is running across fatal #11 — **do not stop it; the ring turns over in ~6 s.** (It later
  stopped on its own at its sample limit and has since been restarted — see the RPM bullet below.)
  **✅ RESOLVED — THE CASCADE IS BOUNDED (3 BEATS) AND THE MODEM RETURNED TO THE 902 s CLOCK.**
  Fatal #11 fired at **AP 7483.839750**, signature **`lte_ml1_sleepmgr_stm.c:4054`**, **903.332309 s**
  after fatal #10 — **predicted at AP ≈ 7482.8 s from fatal #10's modem boot, missed by 1.04 s** — and
  recovered in **0.119912 s** with no `port failed halt` and patch 814's rebuild firing, AP survived.
  **⇒ §8.13 bar: 7 of 20 scored, 0 AP reboots at that point (8 of 20 after fatal #12 — §8.13.6).**
  Operationally this matters: **the cascade is not a
  permanent degradation**, and the "902 s clock" is really **"902 s of modem uptime"**, which the next
  reload restarts. **⚠ AND MY FIRST READING OF THIS EXPERIMENT IS WITHDRAWN:** I stopped the traffic at
  uptime 6600.88, saw 0 fatals in 434.6 s, and called the cascade "traffic-dependent" — but the
  cascade's **last fatal was #10 at 6580.507441, 20.4 s BEFORE the intervention**, so it was applied
  *after* the event it claimed to explain. **An intervention applied after the event cannot explain the
  event (trap 19).** What survives is still useful: across those 434.6 s the storm ran at **full rate**
  (resync +10 = 1/30.6 s, suspends +39 = 1/7.8 s, 26 %/18 % failure) with **no fatal** ⇒ the storm is
  **not sufficient** for a fatal, now with concurrent observation rather than a window that happened to
  contain one. **⚠ One thing remains undistinguishable:** the cascade's 4th beat was due at
  ≈ 6779.5 s and the traffic was removed 174 s earlier, so "ended naturally" and "suppressed by the
  removal" are both consistent — distinguishing them needs a run where traffic is removed *before* a
  cascade starts.
* **★ NEW AND UNEXPLAINED: SILENT DATA-PLANE LOSS WITH NO dmesg CORRELATE (§8.19c).** S2 finished at
  **58 samples with 9 losses** (⚠ **corrected** — this bullet first said 56/7; the file is
  authoritative: `grep -c "rtt=LOST" /overlay/s2.log` = **9**). Four are SSR outages
  (`t_end − t_start ≈ 10 ms`, so the interface was already DOWN), two are pre-fatal timeouts, and
  **three — 6130.44, 6368.31, 6388.34 — have no correlate at all**: a real ~5 s timeout with no lost
  edge, no `pc-ack timeout`, no fatal and no SSR within ±19 s. ⚠ **6130.44 was previously
  misclassified as "ping crossed the ifup"; the `dmesg` timeline refutes it** (fatal #7's SSR completed
  at 6111.927, the ports attached 6112.39–6112.87, the ping fired **17.6 s later** and timed out for the
  full 5.03 s). **n = 3; a new failure mode, and the first one in this corpus that is neither a storm
  event nor an SSR.**
* **⚠ CORRECTION TO MY OWN §8.17 "NEGATIVE" — the storm's failure rate is ~15–40 %, not 5 %.** The
  `40/766 = 5.2 %` figure is a **lifetime average diluted by the 3477 s before the storm existed**
  (§8.15.6); the per-sample rate is `dresync` 1–3 against `susp` 7–9 per 55 s. And `dtimeout = 4` at
  samples 6119.3/6174.7 **is the maximum of the whole 33-sample series**, first reached in the two
  samples before fatal #8. **The pre-fatal window is the series maximum but is not separated from a
  value reached three times earlier — suggestive, not established (trap 17).**
* **RUNNING NOW: the §8.13 coredump-off experiment — 8 of 20 fatals scored, AP survived.** The bar is
  **0 AP reboots across 20 fatals**. **Fatal #9 (`a2_power.c:1189`, AP 6397.771058) gave 0.127579 s**,
  **fatal #11 (sleepmgr, AP 7483.839750) gave 0.119912 s** and **fatal #12 (sleepmgr, AP 8385.263928)
  gave 0.122951 s**, all inside the capture-OFF band, with **patch 814's rebuild firing and succeeding**
  in all three (`successfully reinitialized BAM channels and rings` → all 8 `CMD_OPEN`s). Eight
  capture-OFF recoveries now span **0.119170–0.130237 s** against **0.824902–0.831559 s** for the two
  capture-ON — two non-overlapping populations ~7× apart, on **eight signatures in eight recoveries**.
  ⚠ **The window now spans TWO regimes** (3 idle-clock fatals + 3 cascade fatals); say so whenever the
  bar is quoted. ⚠ **Score on `SSR before shutdown`→`MBA booted`, NOT fatal→`is now up`.**
  Re-enable the capture with `touch /overlay/coredump_ENABLE`.
  If it succeeds, this is a **production-viable fix** — the coredump is a debug feature, and disabling
  it also stops 85 MB/fatal being written to `/overlay`. If it fails, the `dmesg_roll` tail says
  whether the recovery had already passed the coredump step, which is itself informative.
  **Remaining: 12 fatals.** **Fatal #12 has since arrived** at AP **8385.263928 s**
  (`lte_ml1_sleepmgr_stm.c:4054`, the clock) — **901.424178 s** after #11, predicted at AP ≈ 8386.9 s
  from #11's modem boot, **missed by 1.64 s**, i.e. **the modem is still on the clock two fatals after
  the cascade ended** (§8.13.6). Fatal #13 is due at AP ≈ **9287.6 s** (fatal #12's modem boot
  8385.966 + 902.35).
* **★ CHASE THE "LOST EDGE" STORM — but the question has CHANGED twice (§8.15 → §8.15.2 → §8.15.3).**
  The original question ("is a resync a poison pill 74 ms in front of a fatal?") is now **mostly
  answered NO**: 15 resyncs in this boot, only **1** preceded a fatal, and 8+ minutes of storm ran with
  **no fatal and a fully working data plane** (§8.15.3 §1/§8). The antecedent survives as
  **signature-specific necessity** — `a2_power.c:2949` ← lost edge 74–86 ms (3/3 boots) — but
  **sufficiency is 3/20 and falling**, and the storm is better understood as a **failing PC-handshake
  REGIME** of which the resyncs are symptoms.
  **Order of work now:**
  **(1) ✅ DONE (negative):** `pc_timeout_count`'s definition was read — it is the AP's **own** wait
  (250 ms + 1000 ms in `bam_dmux_runtime_resume()`), i.e. the **mirror** of the reading it was hoped to
  discriminate. Do not design around it.
  **(2) ✅ SUPERSEDED BY §8.16 — the mechanism is now known, so D1 is no longer a fishing expedition.**
  The five ~184 ms pairings (§8.15.3 §3) do not close arithmetically against the 250 ms wait, and
  §8.16 answers why: the wait is for the **modem's `pc-ack` line**, the AP gives it **250 ms where
  Android gives 2000 ms**, and **51 of 53** timeout events are that wait. Build the fix in §8.16, not
  a logging patch.
  **(3) Only then D2/D3** (skip `bam_dmux_pc_ack()`, or skip `bam_dmux_pm_restart()`), and score them
  **on the storm**, not on a single resync — `pc_resync_count` rate, not "did a fatal follow".
  **`qcom_bam_dmux` is a loadable module (241 KB `.ko`), so all of this is a `.ko` swap — no kernel
  flash** (`scratch/mkko823.sh` is the working recipe). **D2/D3 must NOT run inside the open §8.13
  window**: they change which fatals occur.
* **★★ NEW — THE MAIN DELIVERABLE: THE ANDROID DRIVER WAS NEVER DIFFED, AND IT IS THE GROUND TRUTH
  (§8.16).** `GitIgnore/android_kernel_zte_msm8916/drivers/soc/qcom/bam_dmux.c` (2798 lines) has been
  in the tree the whole time. **The OpenWrt driver is also SMSM-based** (my first reading of "GPIO IRQ
  vs SMSM" was wrong — §8.16 records the self-correction), and the clear-then-set ordering is
  equivalent. **Four structural differences remain, and one is a single constant:**

  | | Android | OpenWrt |
  |---|---|---|
  | **wakeup-ack wait** | **`UL_WAKEUP_TIMEOUT_MS = 2000`** | **250 ms** (`:2066`) |
  | previous down-ack | `wait_for_ack` serialises | absent |
  | recovery timer | `UL_TIMEOUT_DELAY = 1000` + `ul_timeout_work` | absent |
  | vote policy | reference-counted, on demand | unconditional per PM resume |

  **The wiring is read out of the probe, not guessed:** `pc`/`pc-ack` are **AP-side SMSM output bits**
  and `pc_irq`/`pc-ack_irq` are the **modem's input lines**; so `modem pc-ack timeout during resume`
  means *the AP voted and the modem did not assert its ack within 250 ms*. **The falsifier I stated
  before checking is NOT falsified and the accounting is exact** — 51 × the 250 ms wait + 2 × the
  1000 ms wait = `pc_timeout_count` 53. **And mechanism and measurement agree:** the resume gives up at
  250 ms and proceeds, which is the **416/551/398/431 ms** RTT spike S2 measured against a ~41–43 ms
  baseline.
  **⚠ SCOPE: this is a LATENCY defect, not a stability one.** It does **not** touch the 902 s fatal
  (§8.15.4 proved that fatal is not PM-gated). Preferred fix: **on timeout, re-read the ack state**
  instead of treating the timeout as final — mirroring the driver's own `bam_dmux_pc_line_asserted()`
  idiom, which exists for the *pc* line but was never given to the *ack* path. Alternative: widen
  250 → 2000 ms (Android parity, one constant). **NOT DEPLOYED — it must not run inside the open §8.13
  window.** Falsifier: the spikes vanish **and** `pc_timeout_count` stops rising, `pc_resync_count`
  unchanged.
* **★ NEW SIGNATURE `a2_task.c:3179`, AND A NEGATIVE THAT MATTERS (§8.17).** Fatal #8 fired at AP
  6231.169902 s, only **120.762873 s** after fatal #7 — far outside the 902 s clock, the first
  non-clock fatal of the boot, and a **third `a2_*` file**. Its **antecedent is a negative**: the
  nearest PC event is 12.77 s earlier, not the 74–86 ms band, so the tight-antecedent rule is
  signature-specific (`a2_power.c:2949` 3/3; sleepmgr 0/3; `a2_power.c:1189` 0/1; **`a2_task.c:3179`
  0/1**). The 6× storm-rate jump before it is **NOT a precursor** — the sampler's own history reaches
  `dtimeout` 3–4 in four other samples and never exceeds 4 anywhere (trap 17). **Do not build a
  predictor on the storm rate.**
* **✅ ANSWERED (NEGATIVE) — `rpmring` RAN ACROSS THREE FATALS, AND THE RPM STALL IS **NOT** THE FATAL'S
  MECHANISM (§8.20).** The capture spans **AP 6343.30 → 7794.35 s (1451.0 s, 423 704 records)** and
  covers fatals **#9, #10 and #11**. The nearest quiet period before each is **1.268 / 1.145 / 3.141 s —
  the normal cadence for that era** — and the three ≥ 8 s stalls in the whole capture
  (**13.264 / 8.510 / 10.880 s**) all **recover** and **none** is immediately before a fatal. So the
  13.24 s stall is an **independent, recurring, self-recovering modem-side event (~1 per 480 s)**,
  exactly as §8.19 suspected. **It remains the live signature of the RE's `rpm.sync` (0xc08bebd0)
  claim, but it is a LATENCY defect, not the crash.** ⚠ Two caveats recorded: the RPM's log is
  **bursty** (so "quiet" ≠ "stall"; the cadence must be measured **per era**, and 50.5 % of the capture
  is inside a ≥ 0.5 s gap), and the capture has **49 ring overruns losing 22 664 records**, so any
  future *"the RPM wrote nothing here"* claim must clear `# TRACK OVERRUN` and the counter delta first.
  **⚠ BUT THE CAPTURE HAD ALREADY STOPPED WHEN THIS WAS WRITTEN, AND I DID NOT CHECK.** It exited
  **cleanly at its `-n 500000` sample limit** (660 382 records, last line a complete vote cycle, AP
  ~8600 s) — so the instrument was **not** running while §8.20/§8.21 were being written, and *"the
  capture is still running"* was an assumption, not an observation. **`-n` counts POLLS, not records.**
  It has been **restarted** as `/overlay/rpm10_launch.sh` → `/overlay/rpm10.txt` (`-n 2000000`,
  pid 25898, started AP 8650.88). It is now the instrument for a *different* question: whether the
  stall's rate changes across the next cascade.
  **⚠ AND THE FIRST RESTART SILENTLY PRODUCED NOTHING — because `nohup` DOES NOT EXIST on this device.**
  The recipe that works is **`start-stop-daemon -S -b -x <script>`** (`setsid` also exists; `nohup` does
  not). A failed background launch here creates **no output file at all**, which reads exactly like
  "the experiment produced nothing". **Trap 22.**
* **`pc_state wait timeout` vs `pc-ack timeout` = 2 vs 76 (§8.16, re-measured on the full ring).** The
  §8.16 accounting is now **exact at 100 %**: `pc_resync_count` **81** = **81** `lost edge` lines;
  `pc_timeout_count` **78** = **76** `pc-ack timeout` + **2** `pc_state wait timeout`. The earlier
  *"51 + 2 = 53 (96.2 %)"* was **ring incompleteness at scoring time**, not a second mechanism.
  **If that ratio ever inverts, the 250 ms constant is not the whole story and the fix must be
  re-derived.**
* **⚠ PARTLY REFUTED: "THE STORM IS AP-RUNTIME-PM-GATED" — THE FATAL IS STILL NOT. S1 IS DONE AND P5 IS
  CONFIRMED (§8.15.4), BUT §8.21 WEAKENS THE GATING CLAIM.** A lost edge requires `pc_state == 0`
  **and** the line asserted, so it can only happen on the way **out of** a quiesced state. S1 = 600
  consecutive 1 Hz pings with both counters sampled once a second, and it is a **clean A/B/A inside one
  run**: pre-S1 the storm ran at **1 resync per 36.3 s** with **0.132 suspends/s**; during **364.34 s of
  1 Hz traffic** `pc_resync_count` and `pm_suspend_attempts` were **both frozen** (**0 resyncs against
  10.0 expected**, 0 suspends against 48.1 expected, `pc_state=1` in all 365 samples, **364/364 pings,
  0 % loss**); after the traffic stopped (fatal #6's SSR killed the ping) the storm **returned at the
  pre-S1 rate** (1 per 42.0 s, 0.126 suspends/s). **P5 confirmed with a large margin — traffic does
  suppress the storm.**
  **⚠ BUT THE MECHANISM INFERRED FROM IT IS NOT ESTABLISHED (§8.21).** Freezing *both* counters is
  **equally** consistent with *"traffic suppresses suspends"* and *"traffic suppresses resyncs"*, and S1
  moved both at once. §8.21 separates them: the storm ended at AP 7417.446 and the AP has since
  suspended **94 more times with zero resyncs**. So the churn is **necessary but not sufficient** — the
  gating is **not** simply *"a suspend can produce a resync"*, and **an idle-avoidance policy should not
  be expected to hold the storm off.** The storm, the **data stall** and the PC-handshake failures may
  still be one defect family, but the storm needs a **third, unidentified condition**.
  **⚠ AND THE SAME S1 RUN REFUTES IDLE-AVOIDANCE FOR THE FATALS: fatal #6 fired at AP 5208.721361 s
  INSIDE the suppression window, with the modem never suspended, on the clock
  (`lte_ml1_sleepmgr_stm.c:4054`, 902.286373 s of modem uptime, 19.4 ms from Doc 162's figure).**
  **Idle-avoidance does not suppress the fatals, and it is measured rather than inferred.**
  **✅ S2 IS SCORED — FINAL (§8.19c).** **58 samples, 9 losses.** Four are SSR outages (the interface
  was already DOWN: `t_end − t_start ≈ 10 ms`, `quiesce_ms` non-zero on exactly those), two are
  pre-fatal timeouts, and **three are silent** (6130.44, 6368.31, 6388.34 — a real ~5 s timeout with
  **no `dmesg` correlate at all**). So **between SSRs, at 15 s idle, the data plane is mostly intact but
  not entirely** — the storm's cost is latency (the 416/551/398/431 ms spikes), not mainly loss.
  **P6's predicate was wrong as pre-registered** — the 551.417 ms ping had `pctimeout 33/34` (Δ+1) and
  `resync 35/35` (Δ0). **Pair on the TIMEOUT delta.** **Patch-812 regression check: PASS** over five
  SSRs (`tx_defer_preserved 751` of `tx_defer_queued 771`, `tx_defer_wiped 0`). **Launch these with the
  ssh held open** — a backgrounded job inside a one-shot ssh dies at session exit.
* **⚠ NEW AND UNRESOLVED: the storm's TRIGGER is OPEN, and the instrument that can answer it is now
  deployed (§8.15.5).** §8.15.3 §5 claimed "no external trigger" from `dmesg` + the host log. **Both
  negatives are true and neither is sufficient** — `/usr/sbin/modem-bearer-watchdog` **writes the
  bam-dmux `power/control` and `autosuspend_delay_ms` every 10 s with no log line**, and can also do
  `wds-go-dormant`, a bearer down/up (which changes the IP) and a **full remoteproc SSR**
  (`recovery.ssr_enabled='1'` by default). `logread` is a ~15-minute ring buffer and has already
  rotated past the onset, so this boot's question is **unanswerable**. `/overlay/logroll.sh` is now
  running and in `/etc/rc.local`; it puts the userspace log, the kernel log and the PM state machine on
  ONE timeline. **The next onset is the measurement to take.** Also worth noting: the watchdog pins
  `autosuspend_delay_ms` at **1000 ms** — the knob that controls the storm's mechanism — so any future
  attempt to test "does a longer autosuspend delay suppress the storm?" must account for the watchdog
  reverting it within 10 s.
  **logroll's FIRST CATCH (§8.18):** it captured fatal #8's SSR userspace-side (14 × `qcom-time-daemon`
  `[QMI-TIME] Modem SSR / Disconnect detected (node=0 port=11)`, ModemManager reprobing `wwan0qmi0`,
  `netifd: wwan0 link is down`, then `[modem8] … bearer` → `bearer18` `10.155.30.209/30` → LTE up), and
  the PMKNOB line shows the **autosuspend churn resuming ~19 s after the SSR**. **It found NO userspace
  trigger** — no `Stage 1/2/3`, no watchdog line — so `modem-bearer-watchdog` acting in that window is
  now a **negative observed with the instrument in place** rather than one inferred from absence.
  **⚠ Its limitation, recorded as trap 18: the `logread` stream in the file is BATCHED, not
  interleaved — 40 PMKNOB lines (12:56:37→12:59:24) precede kernel lines from 12:56:38→12:59:00. Use
  the `[ uptime]` field inside each kernel line for cross-stream timing, never the file order.**
* **★ THE STORM IS BOTH ONSET-BOUNDED AND END-BOUNDED (§8.15.6 + §8.21).** **Onset: AP 3477.894835, the
  boot's FIRST lost edge.** The dmesg ring did **not** wrap (`[ 0.000000]` present, 889 lines, crashes
  #1–#8 all there), so this is real: **0 lost edges in 3477 s, then 39 in 2600 s**, a step change
  (P(0 in 504 │ p=0.05) ≈ 10⁻¹¹), starting **81.479 ms before crash #4**. **The storm then survives
  crashes #5, #6, #7 and #8 — four SSRs as of that writing; §8.21 later extended the span to SEVEN
  modem reloads (#4–#10).** And the churn it rides on is **Android-parity** (Android: 918
  collapses in 54 min = 1 per 3.5 s, 0 % loss), so **the churn is not the defect — the handshake
  failure rate is.** S1 is independently corroborated by the second sampler: six consecutive samples
  (~332 s) with `dresync=0`, `dtimeout=0` and `susp` frozen at 636/625.
  **End (§8.21):** the last lost edge is **`[ 7417.446456]`**, so the episode is **AP 3477.894835 →
  7417.446456 = 3939.55 s**. Verified live at **uptime 8156.67**: `pc_resync_count` **81** and
  `pc_timeout_count` **78** both **FROZEN** while `pm_suspend_attempts` climbed **924 → 1018 (+94)** with
  `runtime_status: active` — **739 s of silence across ~94 suspends, including across fatal #11's full
  modem reload.** ⚠ **This refutes "the storm is AP-runtime-PM-gated" as §8.15.4 stated it** (see the
  bullet above): **the churn is necessary but not sufficient**, and the storm needs a third,
  unidentified condition. The natural next instrument is to log **which** suspends are followed by a
  resync, not how many. ⚠ A 739 s silence is a **lull** until a further onset is observed — the sampler
  and `logroll.sh` are both still running and will catch it.
* **Let §8.12's instruments catch a stall, then read the cause off it.** The reset is a **≥30 s global
  stall that the PMIC PON WDT (30 s) turns into a reboot** (§8.11). The beacon and the per-boot
  rolling kernel log are both deployed and reboot-persistent, so the next stall yields the *when*
  (beacon, plus the A(sync)-vs-B(nosync) split that separates a wedged writeback path from a global
  stall) and the *what* (the kernel's last lines). **Do not patch anything until that record exists**
  — §8.10 exists precisely because one reset was inferred rather than measured.
* **Then settle §8.11's rate question — but the question has CHANGED (§8.14).** The old question
  ("how often does the AP hang per SSR?") is not answerable from the data collected so far, because the
  count mixes **host-caused USB resets** (4), a **USB-PHY failure** (1) and a **manual intervention**
  (1) with genuine AP reboots. **The new first step is a filter, not a rate:** for every outage, read
  the host's `journalctl -k` around it and classify it as *host-caused / manual / unexplained*. Only
  the **unexplained** ones are AP-hang candidates (13 today, and "no host trigger" is not proof). Then
  count those per SSR, post-823, with no manual activity. **Also chase the two long outages
  (1641 s, 926 s)** — the 30 s watchdog proves they are **not hangs** (the watchdog is kicked by
  `procd`, so a running CPU is never reset), which makes them **network-path failures with a live AP**:
  a *different bug*, still unexplained, and one that bears directly on the "data stall" reports. Start
  by asking whether the host's interface kept its address/config across the dongle's re-enumeration.
* **The host's own USB controller is a live confounder and may itself be a defect.** **24
  `xhci-hcd.2.auto: remove, state 4` cycles on Sep 21, 18 of them in the 16h hour**, each deregistering
  and rebuilding both root hubs; the dongle now sits **behind a hub** added at 17:03 (and the host has
  been USB-stable since — **zero** USB/xHCI events in the ~1 h after the hub install, so the current
  experiment window is not at risk). **Before attributing any future reboot to the AP, check the host**
  — and **check the ordering**, because "the host's controller reset near the outage" does **not**
  establish that the host caused it (see §9 trap 13(b)). If the controller is genuinely flapping, that
  is a separate bug on the Asahi host to chase, and it invalidates the §8.13 bar's reboot condition —
  which is why §8.14 writes the host-log filter into it.
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
    finding, the deployment, the pre-registered bar (0 reboots / 20 fatals), and the *retracted*
    call-graph correction.**
  * **`T_coredump_off_first_fatal_AB.txt`** — **§8.13.1: the first fatal with the capture OFF, as a
    clean within-boot A/B against the fatal immediately before it.** Both recovery windows verbatim,
    the boot-wide counts (3 fatals / 4 `MBA booted` / 2 `port failed halt`), the full ledger, the
    guarded branch that retracted my correction, and the reason the `q6v5_rmb_mba_wait()` window
    still survives.
  * **`U_host_usb_log_reframes_the_ap_reset_rate.txt`** — **§8.14: the host kernel log, which reframes
    §8.11's rate and withdraws §8.10's verdict.** The host's USB controller instability by hour
    (48 xHCI re-registrations, 36 in the 16h hour), the 39 dongle re-enumerations vs ~19 AP
    uptime-decreases, the **hub installation at 17:03** that explains §8.10's reset, the 16:40–16:46
    cluster (**entangled with the controller storm — direction not established**, retracted from
    "host-caused"), the bimodal outage durations (including 1641 s and 926 s), the `procd`-kicked
    watchdog proof that those two are not hangs, the SMC electrical signal as a deliberate
    **non-finding**, and the 19-outage classification table with raw host log extracts.
  * **`V_coredump_off_fatal4_n2_and_the_lost_edge_precursor.txt`** — **§8.13.2/§8.13.3/§8.13.4/
    §8.13.5 + §8.15/§8.15.1/§8.15.2/§8.15.3/§8.15.4/§8.15.6 + §8.16/§8.17/§8.18: the coredump-off A/B
    taken to n = 5, the whole "lost edge" story from discovery to demotion, and the Android driver diff
    that finally supplies the mechanism.** PART 10 adds: the fifth sample (0.119170 s, new minimum) with
    the full fatal-#7 recovery line list; **the storm's onset located at AP 3477.894835 with the
    dmesg-ring control that makes it real** (0 in 3477 s → 39 in 2600 s) and its survival across four
    SSRs; **the Android-vs-OpenWrt handshake table** (2000 ms vs 250 ms, `wait_for_ack`, `ul_timeout`,
    on-demand vs per-resume voting) with the probe-read wiring, the in-place self-correction on
    "SMSM vs GPIO IRQ", and the falsifier result (51 + 2 = 53); **`a2_task.c:3179`** at 120.762873 s
    with its negative antecedent; S2 scored with all four losses shown to be SSR outages and the
    patch-812 regression check; and `logroll`'s first catch **with its batched-stream limitation stated**. (1) The four-fatal comparison table, the boot-wide
    counts, the ledger's independent `coredumps` column frozen at 18, and the off-clock 759.53 / 827.02 s
    intervals. (2) **The "lost edge" precursor**: every `a2_power.c:2949` (3/3, three boots) preceded
    **74–86 ms** by a patch-808 RX-watchdog resync, against **3 of 20** sufficiency, with all
    counter-examples tabulated, the six-of-six ledger provenance cross-check, the `q6trace.log`
    multi-boot count trap, the three competing readings, the 100 ms watchdog-period argument that
    weakly disfavours one, and the **D1/D2/D3 discriminator designs with the module-not-flash
    deployment note**. (3) **P2/P3 scored** — the sufficiency falls 3/8 → 3/13 → 3/15 → 3/20, the rate
    is non-stationary, and P3's signature prediction **failed**. (4) **PART 7**: the storm as a
    REGIME — the exact 15/14 decomposition of both failure directions, the five ~184 ms pairings that
    **do not close arithmetically** against the 250 ms wait (which redefines D1), P4, the
    no-external-trigger evidence on **both** sides, the unchanged PM rate, and the `pm_suspend` deficit
    withdrawn as accounting. (5) **PART 8**: **S1 scored — P5 CONFIRMED** as a within-run A/B/A
    (0 resyncs and 0 suspends in 364.34 s of 1 Hz traffic, 364/364 pings), **and fatal #6 firing inside
    that window on the clock**, which separates a PM-gated storm from a non-PM-gated fatal.
  * **`W_watch_resync_sh.sh`** — the P1/P2 scorer, and **`X_storm_sampler_sh.sh`** — the 60-minute
    sampler that makes the P3-falsifier branch scoreable and independently corroborated the PM-gating
    (`pc_state=0` in 6 of 10 pre-S1 samples vs 0 of 365 during S1).
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
