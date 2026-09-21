# Modem Stability — corpus index and TRUST INDEX

**Read this before citing anything in this directory.** The corpus grew to 105 files
across many sessions, and a full trust audit on **2026-09-20** found that a large
fraction of it rests on three premises that have since been **measured to be false**.
29 files were moved to `_QUARANTINE/` as a result (see `_QUARANTINE/README.md`).
Four further retractions/corrections were added the same day by Docs 147 and 148, a
fifth round the next day by **Doc 149** (which corrects Doc 148's own period figure and
signature framing), a sixth by **Doc 150** (which retracts Doc 149's own RPM-log
reading method and answers its §7 experiment 1), a seventh by **Doc 151** (which
replicates client-0's fatal-only window at 2/2, names the AP as its leading candidate,
records a new AP-side hang in the modem-shutdown path, and **withdraws Doc 149 §7 item 3's
one-line `qcom,idle-state-spc` DT patch** as unworkable), an eighth by **Doc 152**
(which finds the modem-coredump watcher was **structurally incapable of capturing** — a
`test -s` guard on a size-0 attribute and a release path that does not exist — so fatals
#11 and #12 were lost; it records the corrected procedure and the coredump analysis status),
and a ninth by **Doc 153** (which **confirms Doc 152's fix by capturing fatals #14 and #15**,
withdraws Doc 152 §8 item 3(b) — **`/dev/mem` cannot read the modem region at all**, so the
proposed live `devmem` read of `0xc1d47410` is impossible — and verifies the deployed baseband is
the clean stock HMU05 set), and a tenth by **Doc 154** (which finds a **second, distinct
`bam_dmux` NULL deref** on the TX-wakeup path — `tx_wakeup_work` submits a slot whose
`skb_dma->skb` was NULLed by `power_off`/`pm_restart` — and records that **the modem failed to
restart after fatal #15**, requiring a reboot), and an eleventh by **Doc 155** (which **corrects
Doc 154 §5.3** — the sweep *is* always under `state_lock`; the real hole is that
`bam_dmux_netdev_start_xmit()` re-arms the deferred bit without it — and **fixes the race with
patch 810**, verified against the disassembly of the very module that crashed), and a twelfth by
**Doc 156** (which **root-causes the steady-state data stall**: `pm_runtime_get()` is *async*, so the
first packet after every A2 power collapse always takes `start_xmit`'s defer branch — and
`bam_dmux_pm_restart()` then cleared `tx_deferred_skb` and freed the skb on the very wake that packet
was waiting for, destroying it with **no crash and no counter**; measured **27/27** deferred packets
destroyed in the first minute of one boot and **3/3** single-packet pings lost, and **patch 812** turns
that into **5/5** delivered), and a thirteenth by **Doc 157** (which finds a **third AP-side defect**:
the SSR powerup work polled the modem's power-control line for 3.2 s and then **returned permanently**,
so a modem that took 6.4 s to boot after a fatal was left with `dmux->rx == dmux->tx == NULL` — the
interface stayed DOWN, the kernel deleted its default route, and the symptom looked like a dead modem;
**patch 814** retries instead, and also rebuilds the channels from the RX watchdog; run 3 then proved
the retry's own rebuild with the assert edge deliberately dropped, so recovery does **not** depend on
receiving another interrupt, and run 6 then showed the **watchdog** rebuild firing on a *natural*
trigger, twice), a fourteenth by **Doc 158** (a **negative result** that closes the
live-mpss-observation line: a kernel module *can* `ioremap` the `no-map` modem region, but every
**access** aborts — mpss is assigned to the modem's VMID by **TrustZone**, so the coredump is the only
instrument), and a fifteenth by **Doc 159** (which shows the AP can **hang on a spontaneous
fatal-triggered SSR** — 20 crashes survived, the 21st stopped the whole AP dead until the hardware
watchdog reset it — at a site **different** from the `echo stop` hang the corpus previously recorded,
namely the 43–47 ms between `port failed halt` and `MBA booted`, a window containing three untimed
TrustZone ownership transfers), and a sixteenth by **Doc 160** (a **build-provenance** check: the built
kernel **does** contain all 18 tracked patches — 17/17 target files byte-identical — but the **live**
target patch directory was **stale at 14**, and since the build reads the live directory, a re-prepare
would have silently reverted four fixes in one file, including **patch 812's root-cause fix for the data
stall**; now synced and re-verified), and a seventeenth by **Doc 161** (**build tooling**, no
firmware/driver change: `build.sh` now guards the patch tree — reporting drift before healing it and
asserting the copy landed on **both** install paths — answers "will the next build re-prepare?" by
**asking make** for its own `STAMP_PREPARED`, and can rebuild **just the kernel, one package, or one
kernel module**), and an eighteenth by **Doc 162** (which measures the two data-plane fixes at scale —
**549 deferred packets, 549 delivered, 0 destroyed** over four A2 collapses, against Doc 156's pre-fix
**27/27 destroyed** — confirms patch 814's recovery on 4/4 natural SSRs while noting its *retry* path is
still unvalidated, and records a **new unexplained structure**: the deterministic ~900.8 s fatal fired on
only 2 of that boot's 5 modem boots, and on the other 3 an `a2_power.c:1189` fatal fired instead at
940.2–947.2 s of modem uptime (the first four fatals alternated exactly and the fifth broke it, so
Doc 162's initial "2-cycle" reading is corrected in place); it also
**quantifies the fatal-signature taxonomy**, showing the three `file:line` values near 900 s have
measurably different distributions — so Doc 149's "must not classify a fatal by its `file:line`" is a
correct warning but too strong as stated), and a nineteenth by **Doc 163** (which reads the
ERR_FATAL record out of the modem's own coredumps and finds the **filename is plaintext and inline
at `+0x24`**, and that it names the **same `file:line` dmesg does in 11 dumps out of 11 across two
boots** — so Doc 149's "never classify a fatal by its `file:line`" is a correct warning and a wrong
conclusion, and Doc 162's suppressed-~901 s-fatal finding cannot be a stale-signature artefact; it
also preserves a 36-dump, 2.9 GB coredump corpus that was one write away from filling the overlay
partition), and a twentieth by **Doc 164** (which instruments the Doc 159 hang window with a
temporary kernel patch — **23 `dev_info()` trace points**, one before each step, so the last line
printed names the step that hung — and then **measures its own instrument before trusting it**: the
console costs **1.03 ms + 0.0904 ms/char** (a 4 % match to the 115200-baud serialisation rate), so the
23 trace lines would have added **≈ 190 ms to a 45.481 ms window**, and the window itself is
re-measured independently at **43.726–46.944 ms, n=5**; the fix is one sysctl,
`console_loglevel = 6`, which makes the trace ring-only and **32× cheaper** while leaving `dev_err`
reference points a kernel-side sink; it also records an **8/8 correlation between a fatal's interval
and its signature** — the short ≈900.7 s fatal is always `lte_ml1_sleepmgr_stm.c:4054` and the long
≈942 s fatal is always `a2_power.c:1189` — which **reframes Doc 162's "unexplained alternation" as two
mechanisms with different periods competing inside one boot**), and a twenty-first by **Doc 165**
(which catches the first instrumented fatal and finds that **one fatal makes TWO MBA reloads, not
one** — `q6v5_mba_load()` has two callers, and the second is the **remoteproc coredump reader**,
which `rproc_boot_recovery()` calls *between* `rproc_stop()` and `rproc_start()`, so the trace shows
`01-09, 10-23, 01-09, 10-23` and **`port failed halt` belongs to the coredump's reclaim, not the
restart's**; that the coredump's synchronous 85 398 475 B copy **stalls the recovery path for
1.064 s** and adds a whole extra MBA boot/reclaim pair to every fatal — sharpening Doc 153 §4 from a
limitation into an on-the-critical-path perturbation, and raising an untested confound for Doc 159's
hang; and that the Doc 159 window is **86.2 % a single *untraced* `q6v5_rmb_mba_wait()`** — all 23
trace points together are **5.888 ms, 13.4 %** of the 43.968 ms window, correcting Doc 164 §4's model
of what that span contained and recording, rather than patching, a **coverage gap in Doc 164 §9's
pre-registration**), and a twenty-second by **Doc 166** (which **catches the AP hang** — on a
*spontaneous* fatal, and in a place no instrument was looking: the last message the kernel ever
emits is the SSR notifier's **hand-off**, `bam_dmux: SSR before shutdown: scheduling teardown work`,
so the hang is in `qcom_bam_dmux.c:2233-2237` — the five-line region whose only unbounded waits are
two `cancel_*_sync()` calls on works that themselves touch `pm_runtime` and `state_lock`, and which
are **redundant** because the flag set one line earlier already makes both self-abort; it then finds
a **second, stronger site of the same defect — `power_off`/`pm_quiesce` cancel `rx_watchdog_work`
synchronously while holding `state_lock`, which the watchdog needs at `:1309`, a TRUE ABBA on one
mutex exercised ~7×/min** — and fixes both in **patch 820**, keeping `rx_rearm_work` synchronous
because it takes no lock; it corroborates the hang **without `printk` at all** via the
coredump watcher's missing dump, records that **Doc 164 §9's prediction is falsified in site** —
there is no `q6v5-trace` line in the hang, because the hang is **upstream of the entire window** —
and shows the reset is **silent**: the ramoops DT node has `record-size` but no `max-reason`, so the
dmesg zone exists and an oops or panic **would** have left a record, and none exists; also
**retracts the `qcom-time-daemon` periodic-ATS_USER lead outright** and corrects the pstore-sink
reading). **Doc 166 then corrects itself three times in place, and the third correction matters most:**
(i) the `pmsg` sink is **userspace-fed**, so "no further line appeared" is proven only for `dev_err`+;
(ii) `wwan0at0 disconnected` is an **rpmsg/SMD** line, not a bam_dmux one; and (iii) **its own §5.4
logging-path hypothesis is RETRACTED (§5.5)** — the `logbuf_lock` it invokes **does not exist in
Linux 6.12** (removed in v5.10; `printk_ringbuffer.c` is lockless; `devkmsg_read()` takes only a
per-fd mutex; `syslog_print_all()` takes `syslog_lock` only when clearing), and since a `/dev/kmsg`
reader **sleeps on `log_wait`** when the ring is quiet, its silence is *expected*, not diagnostic —
so the "three independent consumers" are really **two print sites plus a reader with nothing to
read**, and **one** blocked step in the `QCOM_SSR_BEFORE_SHUTDOWN` **blocking notifier chain**
explains both. With §5.4 gone, the `state_lock` ABBA is **again the leading explanation**. See the
sections below), and a **twenty-third by Doc 167** (which finds the hang is **not deterministic** —
boot C ran through **two** fatals with **zero** hangs, its second fatal landing **2.989 s** from the
one that hung boot B — so the ~900 s *trigger* is reproducible while the *failure* is a **race**; it
also shows the SSR **teardown order flips within a single boot**, retracting Doc 166 §2.2c's ordering
*argument* while keeping its conclusion via a better discriminator (*which* line is missing), confirms
Doc 165's "one fatal = two MBA reloads" exactly as `14 + 4 × 23 = 106` trace lines, shows `B` is not a
cross-boot fatal counter, and draws the consequence that matters: at a **3–5 % hang rate** a single
soak cannot validate 820 — **n ≥ 60 SSRs**), and a **twenty-fourth by Doc 168** (which **scores 820: it works, and it moved the
hang** — the teardown work now *runs* and reaches `state_lock` (T4) before the AP freezes, where
pre-820 it died before its first print; **`echo stop` is now a ~2-second forcing function (2/2
freeze)**, which is the real prize; the freeze is a **stalled CPU, not a wedged console**, because
`echo stop` returned **0 over SSH** while the serial console had already stopped at T4; **Doc 167
§7.1's prediction that 820 would make `echo stop` safe is FALSIFIED**; Doc 167 §3's "which line is
missing" discriminator is **retracted in part**, because `wwan0at0 disconnected` is `dev_info` and is
suppressed on the console at `console_loglevel = 6`; and **patch 821 is written and then SCORED —
it works** (the flush returns, `T5`→`T9` all print, the modem powers down, `stopped remote
processor` follows), **but the T4→T5 block it targeted turns out to be a race, not a
deterministic bug** (the same code path blocked in one run and completed in another), so **§2.2's
H1 is FALSIFIED**; and **821 exposes a different defect — a `list_del corruption` wait-queue
corruption in `qmi-proxy`'s `poll()` 0.14 ms after the teardown succeeds** (item 123)).
**Retraction 1 is scoped: read it before citing it.**

## The three retracted premises — do not build on these

1. **"The fatal has a fixed ~900 s period."** **SCOPED 2026-09-20 — read Doc 148 §6.3.**
   FALSE *for `a2_power.c:1189`*: measured times **172.353 / 918.195 / 1823.753 /
   2729.267 s** (intervals 745.842 / 905.558 / 905.514), and one boot produced a single
   fatal at 172 s and none through 1157 s. That signature is a *rate*, not a period.
   **But the generalisation was wrong.** `lte_ml1_common_timer.c:390` was measured at
   **914.769287 / 1818.443149 / 2722.117987 s** — intervals **903.6739 / 903.6748 s**,
   3/3, stable to **1.08 ppm**. So: name the signature. "Fixed 900 s" is falsified for
   `a2_power.c:1189`; for `lte_ml1_common_timer.c:390` the periodicity is **real and
   unexplained** (Doc 148 §3).
   **CORRECTED 2026-09-21 (Doc 149 §2):** the period is **~902.3 s of *modem* uptime**
   (measured 902.267 s, 112 ppm, n=4), not 903.674 s. The 903.674 s figure is the
   AP-observed interval and includes ~1.4 s of SSR downtime — which is why it reconciles
   with the modem RE's independent `400 × 2.256 s = 902.4 s`. **Also corrected (Doc 149
   §3):** the fatal is **not** paced by the AP's A2 vote cadence (that varies 5.9× while
   the period stays flat), and under traffic the deterministic idle fatal is **suppressed**
   and replaced by a variable one.
2. **"The 1000 ms bam-dmux autosuspend is the bug; disable it (`autosuspend_delay_ms=-1`,
   `control=on`)."** FALSE and BACKWARDS. Android clears `SMSM_A2_POWER_CONTROL` 1000 ms
   after the last TX (918 power-collapse shutdowns in 53.8 min, measured live). The
   autosuspend *is* the parity mechanism. Corrected in Doc 140 §6.
3. **"Missing `QMI_WDS_GO_DORMANT` is the cause of the stall."** FALSIFIED by Doc 75:
   at the freeze the modem reported `traffic-channel-active(2)` and `uplink_fc=FLOWING`,
   so the stall is below QMI/WDS.

## Two more retractions (Doc 147, 2026-09-20)

4. **"The RX rearm / RX-watchdog path causes the stall."** FALSE. The driver's RX
   watchdog logs a warning every time it intervenes. It logged **zero** times across
   the measured stalls — the ring was armed with buffers queued and the modem
   delivered nothing. `pc_resync_count` and `pc_timeout_count` were both 0.
5. **"Stalls start at ~900 s."** FALSE. Measured stall onsets in a clean boot were
   **~90 s** and **~180 s** after boot, and both **self-healed** within
   seconds without any recovery action. Under sustained traffic the link is perfect
   (1 Hz ping, 240 s, TX:RX ≈ 1:1, **0 % loss**; DNS to the carrier resolver, 8.8.8.8
   and 1.1.1.1 all resolving).

## A further correction (Doc 148, 2026-09-20)

6. **"The 900 s crash is one phenomenon."** FALSE — the corpus conflates **at least ten**
   distinct modem fatal signatures (`lte_ml1_sleepmgr_stm.c:4054`, `lte_ml1_common_timer.c:390`,
   `a2_power.c:1189`, `mmoc.c:2326`, `mmoc.c:2192`, `wl1m.c:8670`, `memheap.c:1242`,
   `lte_ml1_sm_conn_inter_freq_stm.c:712`, `lte_ml1_rfmgr_trm.c:4014`, `coex_interface.c:530`).
   Any claim of the form "the fatal does X" must **name the signature**. Three of them show
   ~900 s periods but with *different* values (902.230 / 903.674 / 905.5 s); within a boot the
   period is stable to ~1 ppm, across boots both the period and the signature change.
   **CORRECTED 2026-09-21 (Doc 149 §3.1) — the census is right, the conclusion is not.**
   In one boot **three** signatures fired at ~900 s of modem uptime, two of them in the *same*
   idle condition on different boots. That confirms the modem RE's reading (they are one root
   event — the MCPM `system_sleep_check` Q6-PC-voting failure — at different assert sites) and
   refutes this item's "distinct bugs" reading. The ERR_FATAL descriptor is a **single mutable
   global record**, so **never classify a fatal by its `file:line` string**.
7. **The differential doc's "no FATAL" evidence is weak — but its conclusion is right.**
   `Stock_Android_Live/02_DIFFERENTIAL_DIAG_ANALYSIS.md` rests on a ~488 s Android DIAG capture
   and a ~121 s OpenWrt one. DIAG **dies on every SSR**, and a fatal *causes* an SSR, so absence
   of a FATAL record there proves little. The strong control is
   `Stock_Android_Analysis/16_15min_barrier_test_log.txt`: Android at **uptime 921.01 s**,
   `3 transmitted / 3 received, 0% loss`, dmesg silent since 117.59 s. **Android really does
   survive the mark; the fatal really is AP-dependent** (byte-identical firmware). Keep the
   conclusion, downgrade the citation.

## A sixth round — Doc 150, 2026-09-21: the RPM log is now trustworthy

8. **"The RPM log ring can be read with `devmem`."** FALSE — and everything read that way is
   **retracted**. A 2048-word `devmem` walk takes **5.94 s**, while the RPM overwrites all 256
   slots in **0.5–3 s**. Two dumps taken back-to-back shared **0 of 256 records**. So Doc 149
   §5.3's event histogram and ASCII census, and `evidence/149_rpm/rpm_timeline.py`'s
   `span=453.865 s`, are artefacts and are retracted (Doc 150 §3). Both tools are marked
   superseded. The qualitative part of Doc 149 §5.3 survives: the requested resources are
   regulators and clocks, and **no `vmin`/`xosd`/`cxo` request ever appears**.
9. **"The RPM is the stalled party (the Q6 spins in `rpm.sync` waiting for a dead RPM)."**
   **NOT SUPPORTED.** With a real instrument (`rpmring`, mmap, ~500 µs/sample) the RPM's log
   counter advances monotonically **through** fatal #9 and its SSR: 214 records inside the
   1.6 s fatal window, then **1286 rec/s** for 3.6 s as the modem returns (Doc 150 §8). The RPM
   is receiving and processing. It is *not* proven that it replies (§8.2).
10. **New, and load-bearing: the RPM's only real client is the modem.** The log carries a
    client id and a per-client sequence number. Client 1's counter restarts **exactly once in
    1152 transactions — at the modem SSR** (Doc 150 §7), so client 1 = MSS. The AP's client id
    appears only in a 0.73 s window at the fatal (18 transactions), so any "the AP is voting X
    to the RPM" claim must now explain why it is essentially never seen.
    Also: the RPM's `record[1]` timestamp is a **19.2 MHz** counter, measured four ways to
    ±0.017 % (Doc 150 §5) — it had only ever been assumed.
    **Still open:** who client 0 is; whether the RPM replies; the cause of a unique 9.33 s
    modem-side RPM silence at AP 8178.7–8188.0 s that did not recur.

## A seventh round — Doc 151, 2026-09-21: client 0 is not the modem; the AP is the candidate; the SPM patch withdrawn

11. **"The AP's client id appears only in a 0.73 s window at the fatal (Doc 150 §10) — so the
    AP is essentially never an RPM client."** **REFINED, not refuted.** Fatal #10 was captured
    (331.7 s, 94 252 records, 2271 bursts) and client 0 appeared in a **1.308 s** window
    (9069.596–9070.904 s) and **nowhere else in 330.4 s** — the "fatal-only" signature holds at
    2/2 fatals. But client 0's **sequence counter is contiguous and monotonic across the SSR**
    (0x135→0x14c, no restart) while client 1's *does* restart — so **client 0 is NOT the modem**.
    The leading hypothesis is now that client 0 **is the AP**: it requests only `ldoa` (val 3)
    and `smpa` (val 1), which `qcom_smd-regulator.c:967-985` maps to exactly the AP's
    `qcom,rpm-pm8916-regulators` client names; the AP is silent in steady state (regulators
    configured once at boot) and acts at runtime only during remoteproc crash recovery — which
    is *when* client 0 appears. **Status: hypothesis with a mechanism, not proof.**
12. **New AP-side defect found in the modem-shutdown path.** `echo stop >
    /sys/class/remoteproc/remoteproc0/state` **hangs the AP** in the `bam_dmux`
    "SSR before shutdown" teardown path — pstore `console-ramoops-0` ends mid-teardown
    (`bam_dmux: SSR before shutdown: scheduling teardown work` → `port wwan0at0
    disconnected`, then silence) with **no panic/oops/BUG/backtrace**, so it is a hard hang
    followed by a watchdog reset. This is a **second, independent AP-side defect** in the
    modem-teardown path (distinct from the already-solved `wwan0-down` oops of Doc 147). One
    observation; not yet re-attempted.
13. **The SPM/VMIN/`cpuidle-qcom-spm` plan (Doc 149 §5.5/§7 item 3) is WITHDRAWN.** The SAW/ACC
    nodes are `status = "reserved"` under PSCI firmware ownership, so `qcom_spm_find_any_cpu()`
    returns false and the `qcom-spm-cpuidle` platform device is never created; the one-line
    `"qcom,idle-state-spc"` patch does nothing. The `psci ... -3` line is benign. CPR is built
    but never probes (no DT node; the `vmin` code the corpus cited is at the wrong path — in
    6.12 it is `drivers/pmdomain/qcom/cpr.c`, with no `vmin` references). The VMIN/XOSD gap is
    real (Doc 150) but is **not testable by that route**.

## An eighth round — Doc 152, 2026-09-21: the coredump watcher was silently broken

14. **"A watcher streams the ELF off-device the moment a dump appears" (Doc 146 §7) — FALSE as
    deployed.** The watcher could **never** capture, for two source-verified reasons:
    * it guarded on `[ -s "$d/data" ]`, but `data` is a `bin_attribute` with **`.size = 0`**
      (`devcoredump.c:146`; passed straight to kernfs via `fs/sysfs/file.c:343`), so `test -s`
      is false **even with a complete dump present**;
    * it released by `echo 1 > "$d/disabled"`, but the per-device attribute set is **only
      `data`** (`devcoredump.c:151`). The real `disabled` is **class-level**
      (`/sys/class/devcoredump/disabled`) and is a **global, write-once lockdown** that must
      never be written (`devcoredump.c:214`).
    The per-device release is a **write to `data`** (`devcd_data_write`, line 127).
    Consequence: **fatals #11 and #12 produced no dump.** The previous session's log
    (`/tmp/coredump_capture.log`, 29.6 KB) proves a devcd device *is* capturable — it shows
    ~34 reads of one device every ~9 s until the 5-min timeout, i.e. the earlier watcher had no
    `-s` guard and no working release (the "122 dumps = 9.6 GB in 45 min" hazard Doc 148
    already flagged). The corrected watcher is deployed detached and armed (Doc 152).
15. **New period sample:** Δ(#11→#12) = **903.674516 s**, 0.0004 s from the family mean —
    another confirmation of the deterministic period.
16. **Coredump status:** the mapping `dump_va = elf_va − 0x39800000` is re-verified; the
    ERR_FATAL record is a **structured object** at ELF `0xC35B1280` (`{3, 1, ptr 0xC3C0BF84, 3,
    line=390, A, B, "lte_ml1_common_timer.c", "Assert 0 failed: "}`); its word **B increments
    exactly 11 per fatal period** (~82.15 s/count) — still unexplained, the descriptor's only
    lead. The dumps hold the **modem's** memory, **not SMEM**, so the SMSM APPS word and the A2
    client vote list are **not** in them.

## A ninth round — Doc 153, 2026-09-21: fatal #14 captured; `devmem` cannot read the modem

17. **Doc 152's fix is CONFIRMED by measurement — twice.** The corrected watcher captured **fatal
    #14** *and* **fatal #15** (each 85 398 475 B, md5 verified on device *and* host) with no
    intervention. The `test -s` guard really was the whole reason nothing had ever been captured.
    The watcher now also **autostarts from `/etc/rc.local`** (`S95done`), because a hanging fatal
    reboots the AP. *Trap:* busybox `start-stop-daemon -S -x <script>` is **not idempotent** — its
    `-x` match is applied to `/proc/PID/cmdline` (`/bin/sh /overlay/…`), so a second `-S` spawns a
    duplicate. The hook guards on a pidfile the watcher writes itself.
18. **The coredump is created only *after* `rproc_stop()` returns** — `rproc_boot_recovery()`
    calls `rproc_stop()` and only then `rproc->ops->coredump()`, and the `bam_dmux` SSR teardown
    is *scheduled from inside* `rproc_stop()` (`ssr_notify_stop` → `QCOM_SSR_BEFORE_SHUTDOWN` →
    `qcom_bam_dmux.c:2083`). Fatal #13 never printed `stopped remote processor` and produced no
    dump; fatal #14 did, and did. So **a hanging fatal is structurally uncapturable** — the dump
    is never created, not merely expired. (Supersedes Doc 152's "the 5-min window elapsed" framing.)
19. **`/dev/mem` cannot read the mpss region — by *any* method.** Source-verified *and* measured:
    `read()` → **EFAULT** (`arch/arm64/mm/mmap.c` `valid_phys_addr_range()` requires
    `memblock_is_map_memory()`, false for `nomap`); `mmap()` → **SIGBUS**
    (`arch/arm64/mm/mmu.c:99` `phys_mem_access_prot()` forces `pgprot_noncached` for a
    non-`map` pfn → DRAM as Device-nGnRnE → external abort). `CONFIG_STRICT_DEVMEM` is **not**
    the blocker (it is unset). This is why `rpmring` works (the RPM ring is **SRAM**) and
    `devmem` on mpss cannot. **Doc 152 §8 item 3(b) — "read `0xc1d47410` live with `devmem`" —
    is WITHDRAWN.** A kernel module *can* `ioremap` a `nomap` region
    (`arch/arm64/mm/ioremap.c:27` refuses only *map* memory), so that is the next instrument.
20. **`dump_va == AP physical` for the mpss region**, established structurally: the coredump
    PT_LOAD segments span `0x86800000`–`0x8ace6000`, inside `mpss@86800000..0x8bcfffff`.
21. **The `rpm` LPR `+0x18` counter spans 375–1064 across six fatals** and is **not monotonic in
    either direction** — it *exceeds* the adjacent literal 1000s (1064) and also drops far below
    them (375), so Doc 152's "per-client maximum table" reading is at best incomplete.
    **Word B advances by exactly 11 per fatal period at 4/4 within-boot transitions across two
    boots, but does not reset across a reboot** (0x01128c1d → 0x01128c90, Δ = 115, *not* a multiple
    of 11).
22. **The deployed baseband is verified to be the clean stock HMU05 set** — all **21 modem** and
    **9 WCNSS** segments byte-identical to the stock device dump (`modem.mdt` =
    `1a6f9507e03d4ddbbf1977af81ecdbd7`), and equal to neither the UFI001B firmware nor
    `modem_hmu05_patched` nor any of the ~60 `patchNN` backups. So **no firmware-patch experiment
    can be blamed for the fatal**, and the directive's "restore clean firmware first" precondition
    is discharged.
23. **The post-fatal memory state is largely deterministic** — three independent diffs (same-boot
    ×2, different-boot ×1) all differ by **5.14–5.31 %** of bytes. Do not read much into a diff.

## A tenth round — Doc 154, 2026-09-21: a *second* `bam_dmux` NULL deref, and a modem that would not restart

24. **A second, distinct `bam_dmux` NULL dereference — this one on the TX-wakeup path.** At
    AP uptime **1167.94 s**, on the `pm` workqueue:

    ```
    pc : bam_dmux_skb_dma_submit_tx+0x2c/0xe0 [qcom_bam_dmux]
    lr : bam_dmux_tx_wakeup_work+0xc8/0x258 [qcom_bam_dmux]
    ```

    The faulting instruction decodes to **`ldr w22, [x2, #0x70]` with `x2 = NULL`**, and
    `skb->len` is at offset **0x70** on this build (`pahole -C sk_buff vmlinux` → `len /* 112 4 */`),
    so the NULL pointer is **`skb_dma->skb`**. `bam_dmux_tx_wakeup_work()` reads
    `skb_dma->skb` into a local and calls `bam_dmux_skb_dma_submit_tx()` **without a NULL check**
    (`x23 = 0` in the dump is that local). This is a **different call site** from Doc 147's
    `send_cmd` oops: different function, different caller, different faulting load
    (`skb->data` at `0xc8` vs `skb->len` at `0x70`). **Doc 147's fix closed one route to a NULL
    `skb_dma->skb`; it did not add the missing check on this route.**
25. **The in-tree comment that claims `state_lock` guards against `power_off` is FALSE.**
    `bam_dmux_free_skbs()` NULLs TX slots from exactly two callers —
    `bam_dmux_power_off()` (`:1382`) and `bam_dmux_pm_restart()` (`:1607`) — and **neither takes
    `state_lock` anywhere** (there is no `state_lock` reference in lines 1400–1700). Both clear
    `tx_deferred_skb` and `cancel_delayed_work_sync(&dmux->tx_retry_work)` but **never cancel the
    non-delayed `tx_wakeup_work`**, which is only cancelled at `:1997`, `:2234` and `:2277` (the
    SSR-teardown / remove paths). So a deferred slot can be freed while its `tx_wakeup_work` is
    still queued. The comment at `:654` documents a guarantee that does not exist.
26. **The modem failed to restart after fatal #15.** `rproc_stop` returned at 1817.774 s, then
    `port failed halt`, then a stall at `MBA booted without debug policy, loading mpss` — and
    nothing further. `remoteproc0/state` stayed **`offline`**, all eight `wwan*` DOWN, `ping` →
    *Network unreachable*, and it did not recover on its own in the following 240 s. **Only a
    reboot recovered it.** Whether the earlier oops caused this is **not established** (they are
    650 s apart and the oops only killed one `kworker`); read it as **two independent AP-side
    failures in one boot**.
27. **The Doc 153 `/etc/rc.local` autostart is now verified at a real boot** (watcher pid 3275,
    `coredump armed enabled`, syslog `watcher autostarted from rc.local`). The capture pipeline is
    fully automatic across reboots. The watcher's own log confirms arming does **not** survive a
    reboot (`armed=disabled` on the first read, `enabled` after the loop's first re-arm).

## An eleventh round — Doc 155, 2026-09-21: the TX-sweep race FIXED (patch 810), and Doc 154 §5.3 corrected

28. **Doc 154's mechanism was wrong — the sweep is *always* under `state_lock`.** Doc 154 §5.3
    claimed neither `bam_dmux_power_off()` nor `bam_dmux_pm_restart()` takes `state_lock`, so the
    in-tree comment at `:654` was false. **Re-reading every call site shows the opposite**: the
    sweep is under `state_lock` in every path — `pc_irq` `:1701`, the rx-watchdog lost-edge resync
    `:1191`, `ssr_teardown_work` `:2059`, `ssr_powerup_work` `:2213`, and remove `:2292`. The
    `:654` comment is **substantially true**. (Doc 154 §5.3/§5.4/§8/§9 rewritten in place; its
    byte-level fault decode stands.)
29. **The real defect is the *producer*.** `bam_dmux_netdev_start_xmit()` is `ndo_start_xmit`
    (atomic context — it *cannot* take the mutex) and sets `tx_deferred_skb` at `:599` with **no
    lock at all**. The window is the **`bam_dmux_free_skbs()` loop**, not an instruction gap: the
    sweep clears the bitmap (`:1694`) *before* the 32-iteration loop (`:1695`) that actually NULLs
    the slots, and `tx_next_skb` is not reset until *after* it (`:1696`). So a `tx_queue()` that
    lands inside the loop installs an skb the loop then frees — leaving a set bit on a NULL slot,
    which `tx_wakeup_work` dereferences. `active <= 0` is the natural trigger during a wake
    (`pm_runtime_get()` → `-EINPROGRESS`). **Same shape as Doc 147's bug, on the deferred-bitmap
    side.** Four NULL sites are reachable the same way, including Doc 147's own `skb->data` load.
30. **Patch 810 fixes it** — the producer cannot be locked, so the **consumers** are made tolerant:
    NULL-guard `bam_dmux_skb_dma_map()` and `bam_dmux_skb_dma_submit_tx()`; **drop** stale set bits
    in `tx_wakeup_work`'s loop (a slot with no skb can never be submitted — restoring the bit would
    retry every 20 ms forever); and stop `start_xmit`'s `drop:` from **double-freeing** and
    **double-putting** when the sweep already took ownership. The loop guard is provably race-free
    because both it and the sweep hold `state_lock`. *Trap:* `cancel_work_sync(&tx_wakeup_work)` in
    `power_off()`/`pm_restart()` would **deadlock** — both run under `state_lock` and the work takes
    it. Full report: `155_TX_SWEEP_RACE_PATCH_810_AND_DOC154_CORRECTION.md`.
31. **The fix is verified in the object that actually crashed.** The pre-fix module was recovered
    from the device backup (md5 `3b693188c3ba27de9edeb7d1d1e53f76`) and disassembled:
    `bam_dmux_skb_dma_submit_tx` starts at `b04` and the faulting `ldr w22, [x2, #112]`
    (`b9407056` — the oops `Code:` word) is at `b30`, i.e. **`+0x2c` = the oops `pc`**;
    `bam_dmux_tx_wakeup_work` starts at `12e8` and its `bl` is at `13ac`, so `lr = 13b0 − 12e8` =
    **`+0xc8` = the oops `lr`**. Both reported addresses reproduce exactly. The fixed build places
    `cbz x2` immediately before that same load.
32. **A 1 Hz ping soak does NOT test this bug** — measured: `pc_irq`/`pc_vote`/`pm_suspend` froze
    for 105 s because continuous traffic holds the modem permanently awake, so it never collapses
    and the `:597` defer branch is never taken. The soak must **idle first, then burst**.
    **CORRECTED 2026-09-21 (Doc 155 §8.5): the burst phases never actually ran.** All three tunings
    used a fractional `-i` (`0.2` / `0.15` / `0.05`) and **this device's busybox `ping` accepts
    integer `-i` only** — `ping: invalid number '0.05'`. With the burst redirected to `/dev/null`
    the failure was invisible: the generator stayed alive, slept, and transmitted nothing. So the
    "23 collapse/wake cycles in ~140 s" and the §8.3 tuning table are **withdrawn** — those edges
    were the modem's own ~5.4 s cadence plus background AP traffic. The working burst is a shell
    loop of `ping -c 1` (measured: 20 packets in 1.24 s, `tx_pkts +20`); `ping -i 0` hangs and must
    not be used (no `timeout(1)` on the device). **A silently broken traffic generator is
    indistinguishable from a clean soak — always assert TX is advancing.**

## A twelfth round — Doc 156, 2026-09-21: the data stall ROOT-CAUSED and FIXED (patches 811 + 812)

33. **`pm_runtime_get()` is asynchronous, and that is the whole bug.**
    `pm_runtime_get()` is `__pm_runtime_resume(dev, RPM_GET_PUT | RPM_ASYNC)`
    (`include/linux/pm_runtime.h:400-403`) — it **queues** the resume and returns `-EINPROGRESS`; it
    does **not** block. `start_xmit` is atomic context and cannot sleep, which is why the driver uses
    this variant. So for the **first packet after the modem has collapsed**, `active = -EINPROGRESS`,
    and the branch at `qcom_bam_dmux.c:667` (`active <= 0 || !pc_state`) is taken **deterministically**.
    Assuming `pm_runtime_get()` behaved like `_sync` is what made this invisible to inspection for
    several sessions.
34. **`bam_dmux_pm_restart()` then destroyed exactly that packet.** The wake transition is
    `pc_irq` → `pc_state = true` → **`pm_restart()`**, and `pm_restart()` did
    `atomic_long_set(&dmux->tx_deferred_skb, 0)` + `bam_dmux_free_skbs(dmux->tx_skbs, DMA_TO_DEVICE)`
    — i.e. it cleared the bit and freed the skb of the packet that `start_xmit` had deferred *while
    waiting for this very wake*. The drain at `pc_irq:1878` was therefore **dead code**: `pm_restart()`
    had already zeroed the bitmap it tests.
35. **Nothing retransmits it.** `start_xmit` returned `NETDEV_TX_OK`, so the stack believes the skb was
    consumed and the qdisc holds nothing to retry. `bam_dmux_tx_wake_queues()` cannot resurrect a
    packet the driver already acknowledged. TCP survives by RTO; **one** ICMP echo, DNS query or UDP
    datagram does not — which is exactly why `modem-bearer-watchdog`'s single-attempt gate ate it.
36. **Measured, and it is not rare.** Patch 811 adds six counters (`tx_defer_queued`,
    `tx_defer_submitted`, `tx_defer_preserved`, `tx_defer_wiped`, `tx_defer_wiped_live`,
    `tx_submit_ok`, `tx_complete`). One minute after boot, **before the fix**:
    `queued 27 / submitted 0 / wiped 27 / wiped_live 27` — **27 of 45 TX packets (60 %) silently
    destroyed**, with no oops, no fatal, no SSR and `tx_sweep_guard_hits: 0`. It is invisible to every
    stability gate this project had. `tx_defer_wiped_live` is the decisive counter: it separates "a
    stale bit was cleaned up" from "a real packet was thrown away".
37. **The symptom reproduced and attributed in one test.** `defertest.sh`: idle 25 s, snapshot, **one**
    ping, snapshot. **3/3 lost** when the snapshot showed `rs=suspended pc=0` (`deferred +1,
    submitted +0, wiped_live +1`); **1/1 delivered** when it showed `rs=active pc=1` (direct path:
    `tx_submit_ok +1, tx_complete +1`). The single discriminating variable is whether the packet took
    the defer branch — which rules out "the modem is just slow to answer".
38. **Patch 812 fixes it** by making `pm_restart()` obey the invariant the suspend path already relies
    on: `bam_dmux_runtime_suspend()` has an explicit **Guard 1** (`:1933`) that aborts while
    `tx_deferred_skb` is non-zero, i.e. *a deferred packet pins the device awake until delivered*.
    `pm_restart()` was the one path that threw the work away instead of draining it. The fix adds
    `bam_dmux_free_skbs_except(skbs, dir, keep)` and preserves the deferred slots (and their DMA
    mappings — `dma_map_single()` maps for the **device**, not the channel, so it survives the channel
    rebuild) plus their PM references. `tx_next_skb` must **not** be reset while slots are preserved,
    or the ring hands out an occupied slot and stops the TX queue forever. `power_off()` (SSR) is
    unchanged and still discards — dropping is correct there.
39. **Verified.** After the fix, one minute after boot: `queued 8 / preserved 8 / submitted 8 /
    wiped_live 0`. The same symptom test: **5/5 delivered**, including **4/4** from `rs=suspended
    pc=0` — the exact condition that lost 3/3. And the **user-visible symptom**: idle 30 s then
    `nslookup example.com 8.8.8.8` (a single UDP datagram, so no application-layer retransmit)
    answered **4/4 on the first attempt**, including both rounds that started from `pc_state=0`.
    And **the original recorded reproduction is inverted**: Doc 147 §5.4's 120 s-idle / one-ping test
    was **5/5 failed with `dtx=1 drx=0`**; it is now **3/3 delivered with `dtx=1 drx=1`**.
    Patch-chain integrity re-verified against the pre-810 snapshot: `pre810 + 810 == pre811`,
    `pre811 + 811 == post811`, `post811 + 812 == the built file`.
40. **Scope — what this does NOT explain.** It does **not** explain the ~900 s fatal
    (`a2_power.c:1189`, `lte_ml1_common_timer.c:390`) or the failed modem restart after fatal #15; those
    paths are untouched. **Established, not just unobserved:** **three** fatals fired with patch 812
    deployed, including a clean **900.811 s of modem uptime** interval (matching the corpus' own
    `lte_ml1_sleepmgr_stm.c:4054` sample, 900.864 s, to 0.053 s) — i.e. the deterministic idle timer
    fires with the fix in place — so **patch 812 is not a stability fix**; do not cite it as one. The
    intervals are themselves a result: **900.811 s idle → 77.469 s once real bursts started**, matching
    the corpus' "traffic suppresses the deterministic idle fatal and substitutes a variable one", and
    the **signature changed between fatals in one boot** (`a2_power.c:1189` /
    `lte_ml1_sleepmgr_stm.c:4054` / `a2_power.c:1189`), so never classify a fatal by its `file:line`.
    **Use the modem-uptime convention, not the AP fatal-to-fatal interval** — mixing them is what
    produced corpus retraction #1. Those same events show **`port failed halt` is not sufficient to
    cause the restart hang**: it appeared 3/3 and the modem came up 0.56–0.75 s later every time, so
    Doc 154 §6's hang is **intermittent**. The residual `start_xmit` window is still open (a
    `pm_restart()` that reads the bitmap *before* the bit is set still frees that slot; counted by
    patch 810's `tx_sweep_guard_hits`, still 0). Full report:
    `156_DEFERRED_TX_PACKET_LOSS_ROOT_CAUSE_AND_FIX.md`.
41. **A harness defect found mid-session — and it also invalidates Doc 155 §8.2/§8.3.** The first
    `soak812.sh` burst used `ping -c 40 -i 0.05`; **this device's busybox `ping` rejects a fractional
    `-i`** (`ping: invalid number '0.05'`), and with the burst redirected to `/dev/null` the failure
    was invisible — the generator stayed alive, slept, and transmitted **nothing**. None of Doc 156's
    primary evidence is affected (the A/B, DNS and 120 s-repro tests all use `ping -c N`/`nslookup`
    with no `-i`, and their counter deltas prove traffic flowed), but the soak is supporting evidence
    only, and **Doc 155's three burst tunings are retracted** (Doc 155 §8.5). The harness is now a
    shell loop of `ping -c 1` (20 packets in 1.24 s, `tx_pkts +20`) with a liveness check that logs
    `WARNING: no TX for Ns … burst generator dead?`. **Lesson: a silently broken traffic generator is
    indistinguishable from a clean soak — always assert TX is advancing.**

## A thirteenth round — Doc 157, 2026-09-21: the SSR powerup gave up permanently (patch 814)

42. **"The default route is missing" is a SYMPTOM, not the defect.** For IPv4 the kernel **deletes
    every route on a device when the device goes down** (`fib_disable_ip()`). netifd had installed the
    route successfully (`adding default IPv4 route via 10.89.144.122`) and `ifstatus modem` reported
    `"up": true` with `"updated": ["addresses","routes"]` — while the kernel had `wwan0` in
    `state DOWN`. So the real question is always **why is the interface down**, not where the route
    went. This is the third time a data-plane fault on this device has presented as a routing or
    bearer fault; check `ip link show wwan0` before `ip route`.
43. **The primary defect: the SSR powerup defer was a permanent give-up.** When the modem's A2
    power-control line was not asserted within 3.2 s, `bam_dmux_ssr_powerup_work_func()` did a bare
    `return` — **no retry, no reschedule, ever**. The comment said "deferring"; the code said
    "abandoning". Nothing re-runs that work except a *new* `QCOM_SSR_AFTER_POWERUP`.
44. **Why 3.2 s is not enough, measured.** A fatal error forces a **full modem firmware reload**. In
    the failing boot the modem took **~6.4 s** to become ready (teardown 1405.70 s → `wwan0at0
    attached` 1413.57 s), so the poll expired at 1410.92 s, **2.65 s before the modem finished
    booting**. The other two SSRs in the same boot waited only 200 ms — the budget is right for a warm
    restart and wrong for a reload. **One retry would have been enough.**
45. **The false success that turned it into a DOWN interface.**
    `bam_dmux_runtime_resume()` **returns 0** when `dmux->rx == NULL` — it logs
    `channels not initialized after resume` and then reports success. `bam_dmux_netdev_open()` calls
    the *synchronous* `pm_runtime_resume_and_get()`, gets 0, and proceeds to
    `bam_dmux_send_cmd()`, which refuses with `-EAGAIN` on `pc_state == false`
    (`refusing to queue command while modem is collapsed`). So `ndo_open` fails, the interface stays
    DOWN, and the routes go with it. **Measured:** `ip link set wwan0 up` →
    `RTNETLINK answers: Resource temporarily unavailable`.
46. **The lost-edge resync was unreachable in the state it exists for.** The RX watchdog's
    "PC line asserted while pc_state=0 (lost edge), resyncing" path was gated on
    **`dmux->rx && dmux->tx`** — i.e. it could only run when the channels were already there, which is
    precisely not the case after an SSR teardown. Measured `pc_resync_count: 0` for the whole stuck
    period. The two paths that *can* rebuild the channels both require a **rising edge** on the pc IRQ,
    so a missed edge left no recovery route at all.
47. **`pc_line_level` is not a GPIO level — it is the modem's SMEM state word.** The pc IRQ is
    `smsm 1 Edge`, and `irq_get_irqchip_state(…, IRQCHIP_STATE_LINE_LEVEL, …)` is implemented by
    `smsm_get_irqchip_state()` as `readl(entry->remote_state)` (`smsm.c:320-334`). So
    `pc_line_level: 1` with `pc_state: 0` is a genuine contradiction: **the modem said it was awake
    and the driver did not believe it.** The SMSM cascade handler is edge-based on a cached
    `last_value` (`smsm.c:219-220`), refreshed on unmask — which is why an edge can be lost.
48. **The stuck state, verbatim:** `pc_line_level 1` / `pc_state 0` / `pc_resync_count 0` /
    `rx_tearing_down 1` / `rx_slots_mapped 0` / `cmd_open` frozen at **24** / `runtime_status
    suspended` — while `remoteproc0/state` was **`running`** and the modem was registered, attached and
    at 88 % signal. **This is not Doc 154 §6**: there the modem never came up (`state = offline`, log
    stopped at `loading mpss`). Here the modem came up fine and the *driver* stopped trying.
49. **Patch 814** (three changes): retry the powerup work 500 ms apart, 20 times (~77 s total budget),
    resetting the counter on every `QCOM_SSR_BEFORE_SHUTDOWN`; let the RX watchdog rebuild the channels
    when the modem is awake and `rx`/`tx` are NULL; and arm both from `bam_dmux_runtime_resume()`.
    **`runtime_resume()` still returns 0 deliberately** — an error would set
    `dev->power.runtime_error`, and `rpm_resume()` then refuses *every* later resume
    (`if (dev->power.runtime_error) goto out;`), converting a transient condition into a permanent
    one. **The retry polls the SMEM level, so it recovers from a slow boot and from a lost edge
    alike** — it does not depend on ever receiving another interrupt.
50. **Patch 814 is NOT a stability fix.** The fatal cadence is untouched; the three fatals in the
    failing boot still happened (two different signatures in one boot — never classify a fatal by its
    `file:line`). Its chain reproduces the built source byte-exactly, and the post-fix baseline is
    healthy (`cmd_open 8`, `rx_slots_mapped 32`, default route present, 2/2 ping).
51. **The Doc 151 §5 `echo stop` hang is now n=2.** Re-observed while trying to force an SSR on
    demand: `echo stop > /sys/class/remoteproc/remoteproc0/state` hung the AP and the watchdog reset
    it, with the console ending on exactly the same two lines
    (`SSR before shutdown: scheduling teardown work` / `port wwan0at0 disconnected`) and no
    `dmesg-ramoops` record (so not a panic). Different boot (116 s vs 9355 s) and different module
    build (patch 814) ⇒ **not caused by, and not fixed by, patch 814**. An AP-initiated modem teardown
    is still not available as a test route.
52. **Memory quirk #9 is necessary but not sufficient.** `network.modem.auto` *is* set now, netifd
    *did* install the address and the route, and the interface was still DOWN. Do not stop at the
    config when this symptom appears — go to `bam_dmux` telemetry.
53. **Soak run 1: the normal SSR path is intact, and a SECOND measurement artefact of the same kind.**
    A real fatal-triggered SSR (`lte_ml1_sleepmgr_stm.c:4054` at **900.904 s of modem uptime** — the
    deterministic idle timer) recovered on the normal path: `pc_state=1 (waited 560 ms)`, channels
    reinitialised, and **`cmd_open` 8 → 16** — the modem reopened all eight data channels, which can
    only happen if the driver rebuilt them. The new retry/rebuild paths stayed silent, i.e. inert.
    But the harness also logged **`DATA PLANE DOWN (SSR #1)`** at 926.7 s, which was **false**: the
    link was fully back by 972.6 s. An SSR is followed by ModemManager re-registration and a bearer
    reconnect — **~30-45 s** — so a single probe 8 s after the SSR reports "down" for a healthy
    recovery. **This is the second artefact of exactly this kind in two sessions** (the first was
    Doc 156's `ping -i 0.05` burst that silently transmitted nothing); both would have been read as a
    device fault. The probe now polls (5 s × 12) and reports the **time to recovery**. A second,
    smaller bug in the same pass: the modem-uptime-at-fatal calculation took the *last* `is now up`
    line unconditionally, which after a recovery picks the **new** modem's start time and gives a
    **negative** uptime (run 1 logged `-1.406112 s`); it now picks the last line older than the fatal.
    **Lesson, again: when a harness reports a fault, verify the harness against a known-good recovery
    before believing it.**
54. **The retry's OWN rebuild branch is now proven — by dropping the assert edge.** The first
    observation of the retry was *not* proof: in the test build's boot the channels had already been
    rebuilt by the **pc_irq edge** (`CMD_OPEN` at 12.221 s arrived *before* the retry's success log at
    12.542 s, which then said `channels already active`). That verifies exactly the wrong half — the
    patch exists for a **lost edge**. So run 3 used a deliberately crippled build: the poll window cut
    from 3200 ms to 40 ms **and** `bam_dmux_pc_irq()` dropping the rising edge outright whenever
    `rx`/`tx` were NULL. Result: edge discarded at 12.517 s with `rx=0 tx=0`; the retry polled the
    modem's SMEM state word at 12.866 s, called `bam_dmux_power_on()` **itself**, logged
    `successfully reinitialized BAM channels and rings`, and all eight `CMD_OPEN`s followed with no
    second edge to trigger them. Data plane came back fully (4/4 ping, DNS, `rx_slots_mapped 32`,
    `rx_tearing_down 0`). **The recovery does not depend on ever receiving another interrupt.**
55. **Test builds must be reproducible and provably reverted.** The run-3 edits are saved as a patch
    against `post814`; rebuilding from that patch reproduces the deployed test module byte-exactly
    (`5e26986d…`). After reverting, the restored source `diff`s clean against `post814` and rebuilds to
    **`eca269f10a685a83a8b679bc7be38d1d`** — the production hash, **verified by rebuild, not assumed** —
    and the rebooted device shows the normal boot path again (`waited 560 ms`, no retry, no `TEST:`
    lines). A temporary diagnostic build is only acceptable if you can prove you got back to the
    production bytes.

## A fourteenth round — Doc 158, 2026-09-21: the modem's memory is unreadable from the AP (a negative result)

56. **`ioremap` succeeding is NOT permission to read.** Doc 153 named "a `nomap`-capable kernel module"
    as the next instrument, on the reasoning that `/dev/mem` fails because mpss is `no-map`. A module
    was written, built and loaded on the live device; the **mapping succeeded**, the debugfs file was
    created — and the **very first read of offset 0 aborted**:
    `Internal error: synchronous external abort: 0000000096000010`, `pc : mpss_read+0xc0`,
    `x0 = ffff800090000000` (the ioremap'd mpss base), faulting instruction `ldr w0, [x0]`. Five
    occurrences. The kernel killed the faulting `dd`, tainted itself `[M]=MACHINE_CHECK`, and **kept
    running** — no panic.
57. **The confound that nearly changed the answer — and how it was eliminated.** The first experiment
    used `ioremap()`, which on arm64 gives **Device-nGnRE**, whereas the driver reads mpss with
    `memremap(..., MEMREMAP_WC)` — **Normal-NonCacheable**. Touching Normal DRAM through a Device
    mapping is an architectural memory-type mismatch that an interconnect *may* abort, so the abort
    could have been the mapping's fault and the reader perfectly feasible with the right type. The
    module gained a `wc` parameter and the experiment was repeated **with the driver's own memory
    type**: `memremap(MEMREMAP_WC)` → `pc : __memcpy+0x24`, `lr : mpss_read+0xa4`,
    `x23 = ffffdce188260000`, faulting insn `ldp x6, x7, [x1]` — **the same abort**. Both memory types
    are refused, so page attributes, cacheability and access width are all excluded and **permission is
    the only variable left**. Do this: when a hardware refusal is the conclusion, reproduce it under the
    exact conditions the working path uses.
58. **Why: mpss is protected by TrustZone, not by the kernel.** `q6v5_xfer_mem_ownership()`
    (`qcom_q6v5_mss.c:422-450`) issues **`qcom_scm_assign_mem()`** to move the region between
    `QCOM_SCM_VMID_HLOS` (the AP) and `QCOM_SCM_VMID_MSS_MSA` (the modem). While the modem runs, the
    AP is not in the permitted VMID list and the interconnect denies the access **in hardware**. The
    driver only `memremap`s mpss *after* taking ownership back (`:1403-1404`, `:1440`, `:1547-1555`).
59. **This re-reads Doc 153's `/dev/mem` result.** `read()` → `EFAULT` and `mmap()` → `SIGBUS` are the
    **same hardware abort** by two userspace paths (SIGBUS delivered to the faulting load; EFAULT from
    the failed `copy_to_user`), **not** a `no-map` or `CONFIG_STRICT_DEVMEM` effect. There is no
    software route around a TrustZone assignment. **"`nomap`" is a kernel-MM attribute and says nothing
    about whether the bus will answer an AP access** — treating the two as the same is what made the
    instrument look feasible.
60. **Why `rpmring` works and this does not.** The RPM log ring is in **SRAM** — a normal reserved
    region, not a TrustZone-assigned one — so `/dev/mem` `mmap` succeeds and the live RPM log stays
    available. The difference is TrustZone, not `no-map`.
61. **The "watch the fatal develop live" idea is dead.** The coredump is taken *after* the driver hands
    mpss back to the AP, so it is a consistent snapshot and it is the only instrument. There is no
    better one, and the fatal's transition can only be studied post-mortem (coredump) or indirectly
    (RPM log, AP-side telemetry).
62. **A second hazard: the module could not be unloaded and debugfs wedged.** After the aborts,
    `rmmod` failed (`rc=255`), `refcnt` read **`-1`**, and any `stat()` of the debugfs file blocked in
    **`D` state** (`ls /sys/kernel/debug/` hung too). The rest of the system was unaffected — the soak
    kept running, the route stayed up, ping worked. Leading explanation (not proven): the first
    `rmmod` blocked inside `debugfs_remove()` waiting for the file's `active_users` to drain, so the
    module stayed in the going-away state; the undrained reference is probably a `dd` killed by the
    abort while holding a `debugfs_file_get()`. **Procedure: after such an abort, do not `rmmod` —
    reboot.** Both reboots were clean (verified).

## A fifteenth round — Doc 159, 2026-09-21: the AP hangs on a *natural* fatal-triggered SSR

63. **Patch 814's watchdog rebuild fires on a natural trigger — twice.** Soak run 6 (538 samples, 8
    fatals in one boot) logged `RX watchdog: modem awake (pc line asserted) but no channels, rebuilding`
    at 2907.1 s and 3888.5 s, and the data plane returned both times (`rebuilds` 0→1→2 in the CSV).
    Together with run 3 (§7.5 of Doc 157, the retry proven by dropping the assert edge) **both new
    recovery paths are now verified**. `retries` stayed **0** across all 8 SSRs — every modem was ready
    in 540–580 ms, well inside the original 3.2 s window — which is the expected "inert when unneeded"
    result. The only thing still missing is a *natural* occurrence of the **retry's** trigger.
64. **`port failed halt` is NORMAL — 21/21 in that boot.** The obvious reading of a hang that ends on
    that line is "the AXI halt port failed and hung the AP". The boot refutes it: **21** crashes, **21**
    `port failed halt`, **21** `MBA booted` (one of which is the cold boot) — so 20 of them printed the
    message and recovered 43–47 ms later. It is a `dev_err()` on a 100 ms `AXI_IDLE` poll timeout
    (`qcom_q6v5_mss.c:969-974`) and means only *the port was not idle*, which is what a just-crashed
    modem looks like. **A message is not a cause until its rate is known.**
65. **THE HANG IS REACHABLE WITHOUT `echo stop` — and that is the headline.** The corpus had this hang
    only from `echo stop` (Doc 151 §5 n=1, Doc 157 §8.2 n=2) and had drawn the narrow conclusion
    "don't use it as a test route". Run 6's boot ran 4 h 27 min, took **21** fatals, recovered from 20,
    and **hung on the 21st** with nobody touching `remoteproc0/state`. It is a **production failure**:
    the AP is gone (data, ssh, everything) until the hardware watchdog resets the SoC. Console stops
    dead — no panic banner, no `BUG:`, no `dmesg-ramoops` record for that boot.
66. **It is a DIFFERENT site from the `echo stop` hang — do not conflate them.** The `echo stop` hang
    lands *before* `executing serialized asynchronous SSR teardown` (the bam_dmux teardown had not
    started). This one got **past** the teardown, past the three port detaches, and past
    `stopped remote processor`, and died on `port failed halt`. The window is therefore bounded by two
    adjacent printk calls and is only 43–47 ms wide, which makes it enumerable: the tail of
    `q6v5_mba_reclaim()` plus the head of `q6v5_mba_load()` — **three untimed `qcom_scm_assign_mem()`
    TrustZone ownership transfers**, one reset assert/deassert pair, four regulator ops, four clock ops,
    six TCSR writes, and one SMP2P/mbox teardown+setup. Leading candidate is the SCM handoff: it is a
    **synchronous SMC** with no timeout, and the modem is the *other* VMID owner of the memory being
    handed back (Doc 158) at the exact moment it has just crashed. **Not proven.** Recommended next step
    is a temporary build with `dev_info()` at each of the 13 steps — the console is the only instrument
    that survives a hang, and it would pin the site to one call.
67. **The crash that hung came 49 s after the previous one.** Crashes #1–#20 are the ~902 s timer
    (Doc 156), spaced 902.1–902.2 s apart; crash #21 is **49.0 s** after #20, an activity-correlated
    fatal, i.e. it happened while the system was still settling from the previous recovery. **n=1 —
    hypothesis, not finding**, but the obvious thing to test: does the hang need a *second* fatal inside
    the recovery window? The soak harness does not currently record "time since previous crash".
68. **Same signature, different outcome — the `file:line` rule again.** Crash #19 (`a2_power.c:2949`)
    recovered in 47 ms; crash #21 carries the **same** signature and hung. Third independent
    confirmation that a fatal cannot be classified by its `file:line` (the record is a single mutable
    global). Also: **`a2_power.c:2949` has now been observed**, where the decode reference recorded it
    as *not* observed.
69. **Harness blind spot #3: a bounded `tail` window.** Run 6's log made SSR #5 look like it had lost
    its `SSR after powerup` line — the teardown at 3809.637 s was the last matching line and nothing
    followed. It was an artefact: the block captures `dmesg | grep … | tail -8` and was written at
    3818.16 s, **0.33 s before the modem finished coming up** at 3818.489 s. The full dmesg shows a
    completely normal cycle. **Check the raw log before believing the summary.**
70. **A "recovered" SSR that passed no data (observation, n=1, needs confirmation).** In the same run,
    SSR #5's recovery looks clean — `successfully reinitialized BAM channels and rings`, all eight
    `CMD_OPEN`s — yet **no data flowed for ~68–85 s**: `rx_pkts` advanced by **0** while `tx_pkts`
    advanced 45 over `3817.67 s → 3903.37 s`, against a healthy baseline of ~40 TX *and* ~40 RX per 10 s
    sample. It cleared only when the next fatal rebuilt the channels again. The harness probe failed all
    12 attempts over 75 s, but its `wwan0 … state DOWN` snapshot is contaminated by SSR #6's teardown.
    `probe_dataplane()` now logs a per-attempt trace (`route`/`NOroute`, `pc_state`, `pc_line_level`,
    `rx_slots_mapped`, `cmd_open`) so a bare "down" can be told apart from "route present but the modem
    is not passing packets".
71. **`pstore` is NOT reliable — a spontaneous reboot can destroy its own evidence.** The boot after
    the hang took four fatals (all coredumped: 923.71, 1827.23, 2275.74, 2389.74 s), was verified
    healthy at 2164 s, and then **rebooted on its own** after 2395 s. pstore came back **empty**, and
    the new boot says why: `ramoops: found existing invalid buffer, size 220, start 724` — the dying
    boot's console buffer was invalid and the kernel discarded it. The one instrument that survived
    Doc 159's hang produced nothing this time. **It is probably NOT a second hang**: the 4th fatal's
    coredump was created at 2395.10 s, and a coredump only exists after `rproc_stop()` returns
    (Doc 153 §4) — the Doc 159 hang is precisely a failure to get that far. **Recorded as unexplained.**
72. **Harness death #3, and the same lesson.** In the same boot the soak sampler's CSV stopped at
    1833.07 s and its log at the start lines, with **nothing saying why** — it died between the CSV
    append and the next `log` call. It was **not** a device hang: the AP answered ssh and ping at
    2164 s and the coredump watcher captured fatals at 2275.74 and 2389.74 s, i.e. the AP ran normally
    for **560 s** after the sampler stopped. `soak814.sh` now logs a **heartbeat** every 5 min and keeps
    a **rolling dmesg snapshot** (last 400 lines, every 2 min) to `/overlay/soak814_dmesg_rolling.txt`,
    precisely because pstore proved it cannot be relied on.

## A sixteenth round — Doc 160, 2026-09-21: the patch chain is verified in the kernel, and the *live* tree was stale

73. **The built kernel contains every tracked patch — 17/17, byte-identical.** Reconstructing the
    kernel from the pristine tarball (`openwrt/dl/linux-6.12.94.tar.xz`) plus all **18** patches in
    `msm89xx/patches/` yields a tree that `cmp`s clean against
    `build_dir/…/linux-6.12.94` for **all 17 target files**. Nothing in the deployed kernel came from
    anywhere else. **The answer to "did the patches land in the kernel" is yes.**
74. **But the LIVE target patch directory was stale — 14 patches, missing `810`, `811`, `812`, `814`.**
    `openwrt/target/linux/msm89xx/patches/` (gitignored, derived) had not been refreshed since those
    four were added to the tracked tree. **The build reads the LIVE directory, not the tracked one** —
    so this was a live hazard, not cosmetics.
75. **A re-prepare in that state would have silently reverted four fixes in one file.** A clean-room
    simulation (pristine + the live 14) gives a `drivers/net/wwan/qcom_bam_dmux.c` differing from the
    built kernel by **26 hunks, +303/−26 lines (2598 → 2321)** — i.e. the `tx_sweep_guard_hits`
    counter (7→0), the `defer_q`/`defer_sub` counters (6→0), and `812`'s extra `bam_dmux_pm_restart()`
    hunks (12→9) all vanish. **Patch 812 is the root-cause fix for the data stall (Doc 156), and the
    counters that would have exposed its loss would have vanished with it** — an invisible regression.
76. **The mechanism — why the live tree is a build input.** `PATCH_DIR` resolves to the **live**
    `target/linux/msm89xx/patches` (`include/kernel.mk:43`); that directory is part of
    `KERNEL_FILE_DEPENDS` (`include/kernel-build.mk:12`); and `STAMP_PREPARED` is an **md5 of those
    directories** (`:13`). So changing the live tree invalidates the stamp, and the stamp rule then
    does `-rm -rf $(KERNEL_BUILD_DIR)` and re-extracts + re-patches from the **live** tree
    (`:92-96`). The tracked `msm89xx/` is authoritative **only because `build.sh`'s `sync_bsp()` copies
    it over the live directory first** (`build.sh:336`). Any build driven another way, or a sync that
    runs before the patches are added, drifts. **Fixed** by `cp -a msm89xx/patches/. openwrt/target/linux/msm89xx/patches/`
    — both trees are now byte-identical and the reconstruction from the **live** tree re-verifies at
    17/17.
77. **Two wrong intermediate answers were caught — both are traps worth keeping.** (a) A new file is
    **not always `--- /dev/null`**: patch `813` creates `msm-poweroff.h` as `--- a/…` with hunk header
    `@@ -0,0 +1,16 @@`, so a `--- /dev/null` scan misses it and a naive "restore pristine" re-applies
    the patch onto its own output, producing a duplicated file. **Detect created files by `@@ -0,0`.**
    (b) A first-run *"804/805/806 FAILED — would create the file which already exists"* was a
    **dirty-tree artefact**: on a genuinely pristine tree all 14 apply cleanly. **Check an experiment
    for a dirty input before believing its failure** — the same lesson as Doc 159 §9's `tail -8` blind
    spot and §11's invalid ramoops, in a different medium.

## A seventeenth round — Doc 161, 2026-09-21: the build now guards the patch tree, and can build one thing

78. **`build.sh` reports and verifies BSP drift instead of healing it silently.** `bsp_drift()`
    compares the tracked trees against the live ones (`msm89xx/` → `target/linux/msm89xx/`,
    `packages/` → `package/msm8916/`) with `diff -rq`. `sync_bsp()` now **reports** any difference
    *before* its `rm -rf live && cp -a tracked live`, and **asserts** afterwards that the copy landed
    — a partial or failed copy used to mean a kernel built from the wrong patch set with no error.
    Both install paths are covered: `assert_bsp_synced()` also runs after `scripts/openwrt-prepare.sh`
    (`ensure_prepared` slow path and `force_prepare`).
79. **`./build.sh guard [--deep]`** — a standalone pre-flight that exits **non-zero on drift**. Docker
    is not required for the basic form, because the check is most useful when the build environment is
    *not* running.
80. **`guard --deep` answers "will the next build re-prepare?" exactly, by asking make.** A re-prepare
    wipes `build_dir`, so it is worth predicting. Re-implementing OpenWrt's `find_md5` would be
    **wrong** — it hashes **absolute paths**, which differ between host and container. Instead an
    `--eval`'d target prints make's own `STAMP_PREPARED`. Three things had to be measured, not assumed:
    **`make -p` prints recursive variables UNEXPANDED** (verified on a minimal makefile), so `-p`
    alone cannot work; **`TOPDIR` must be passed** (it is exported by the top-level make, not set in
    `rules.mk`); and **`TARGET_BUILD` must be exactly `1`** (`include/target.mk:389`). Cross-checked
    against ground truth: on-disk stamp `.prepared_355cb72e…` vs computed `.prepared_28a7f3ba…` →
    **"re-prepare pending"**, the correct answer after Doc 160's 14→18 sync.
81. **Selective builds: `kernel [board]`, `package <name|path> [board]`, `kmod <name> [board]`.**
    `make target/linux/compile` and `make package/<path>/compile` were always available (the toplevel
    `%::` catch-all, `include/toplevel.mk:225-238`) — they only needed a `.config`, which is what the
    new `ensure_config` supplies. Without a board it **reuses the existing `.config`** instead of
    re-running `make defconfig`, which is what makes iteration fast. Package resolution verified across
    all five paths: base-tree nested (`dnsmasq` → `package/network/services/dnsmasq`, `mac80211` →
    `package/kernel/mac80211`), feed symlink (`curl` → `package/feeds/packages/curl`), project
    (`qrtr`/`rmtfs`/`reboot-edl` → `package/msm8916/…`), and explicit path.
82. **`kmod` distinguishes the two real cases rather than guessing.** An out-of-tree kmod package is
    built directly; an **in-tree** module (`qcom_bam_dmux` → `kmod-bam-dmux`, no package directory at
    all) has **no narrower goal than the kernel target**, and the command says so instead of pretending
    otherwise. It then prints matching modules with md5 — `kmod bam-dmux` → `qcom_bam_dmux.ko`
    `eca269f1…`, the hash the device is running.
83. **`DRY_RUN=1` prints the make command instead of running it — and is fully side-effect-free.**
    It is what made the new commands validatable without a multi-minute build. Its `prepare_config`
    guard was added *because the first version clobbered `.config`*: a dry run still copied the bare
    hmu05 diffconfig and then skipped `make defconfig`, so `TARGET_DIR_NAME` evaluated to `_` and the
    deep check printed `target-_/…`. **A testing affordance that mutates state is worse than none.**
    `.config` was restored to the hmu05 board config that produced the deployed images.

## An eighteenth round — Doc 162, 2026-09-21: run 8 puts patch 812 at scale, and the fatal signature *is* informative

84. **Patch 812 holds at scale, and the number is the point.** Run 8 (70 min, 4 fatals, 4 SSRs,
    the same 6 s-idle-then-burst harness as run 6) measured **`tx_defer_queued 675` /
    `tx_defer_submitted 675` / gap 0 / `tx_defer_wiped_live 0` / `tx_sweep_guard_hits 0` /
    `tx_submit_ok 23341` = `tx_complete 23341` / 0 oopses**. Doc 156's pre-fix baseline was
    **27 deferred, 27 destroyed, 0 delivered** — 60 % of all TX in the first minute of a boot. The
    defect does not degrade under load; it is gone. `cmd_open: 48` (6 modem boots × 8 channels) is
    the modem's own confirmation that the driver rebuilt the channels every time.
85. **Patch 814 recovered the data plane on 5/5 natural SSR triggers** without a reboot (~20 / 40 /
    15 / 30 s), **and its watchdog rebuilt the channels on 4 of them — `pc_resync_count: 4`**, i.e.
    the SSR `pc` assert edge was lost on 4 of the 5 fatals. That is Doc 157's third AP-side defect
    (before patch 814, that state was a permanent `wwan0` DOWN) now observed on a **natural**
    trigger four times in one boot — which run 6 could only show twice. But **`retries: 0`**: this
    modem came ready in 540–580 ms every time, well inside the original 3.2 s budget, so the
    *retry* path's trigger is still unobserved.
86. **Run 8's ~901 s fatal fires on only some boots — the first four fatals made this look like a
    clean 2-cycle, and the fifth broke it.** Modem uptime at fatal:
    **900.965 / 941.288 / 900.662 / 940.238 / 947.161 s**, signatures
    `lte_ml1_sleepmgr_stm.c:4054` / `a2_power.c:1189` / `lte_ml1_sleepmgr_stm.c:4054` /
    `a2_power.c:1189` / `a2_power.c:1189` — i.e. **S, A, S, A, A, not an alternation**. The
    surviving claim is the useful one: **the deterministic ~900.8 s fatal fired on only 2 of the 5
    modem boots; on the other 3 an `a2_power.c:1189` fatal fired instead at 940.2–947.2 s.** On
    boots 2, 4 and 5 the ~901 s fatal did **not** fire at all (it would have landed at AP 1815.3,
    3660.1 and 4601.5 s; SSR count = fatal count = 5, so nothing went unrecorded). Run 6, under the
    *same harness and traffic*, showed none of this. **New structure; unexplained; n=5, one boot.**
    Doc 162 was written when only four fatals were in and claimed a 2-cycle; **it is corrected in
    place, with the wrong reading kept visible.**
87. **"The period" is only well-defined per signature.** Over every fatal in the corpus that can
    be paired with a signature and a modem-uptime anchor (`evidence/162_fatal_signature_taxonomy/fatal_taxonomy.py`,
    every row cited):
    `lte_ml1_common_timer.c:390` — **n=11, 902.353 s mean, spread 0.508 s, ±281 ppm**;
    `lte_ml1_sleepmgr_stm.c:4054` — **n=10, 901.230 s mean, spread 2.319 s** (±1287 ppm);
    `a2_power.c:1189` — **n=8, 68.524 … 947.161 s, spread 878.637 s**.
    The first is the deterministic timer (and matches the RE's `400 × 2.256 s`). The second sits
    **1.12 s earlier with 4.6× the spread** — a difference larger than both spreads combined, so
    likely a downstream consequence rather than the timer. The third is **not a clock at all**.
88. **Doc 149's "must not classify a fatal by its `file:line`" is right as a warning and too strong
    as stated.** Three `file:line` values do all appear near 900 s, so a `file:line` does not
    identify *the* assert — but the three distributions are measurably different, so it *does* say
    which mechanism fired. **Ask "what is this signature's period?", not "what is the fatal's
    period?"**

## A nineteenth round — Doc 163, 2026-09-21: the ERR_FATAL descriptor is inline and matches dmesg 11/11

89. **The ERR_FATAL descriptor carries a plaintext, INLINE filename — and it names the same
    `file:line` dmesg does, 11 dumps out of 11, across two boots.** The record is at ELF VA
    `0xC35B1280`: `+0x10` = line (u16), `+0x14`/`+0x18` = words A/B, and **`+0x24` = the filename,
    NUL-terminated and in the clear**. The corpus recorded the descriptor as *"obfuscated"* with
    *"no 16-byte filename table"* — that is true of the **ELF on disk**, but the runtime copy in a
    coredump is plaintext, and the earlier reader followed the unrelated `+0x08` pointer instead of
    reading `+0x24`. Run 8's five dumps decode to `lte_ml1_sleepmgr_stm.c` 4054 /
    `a2_power.c` 1189 / `lte_ml1_sleepmgr_stm.c` 4054 / `a2_power.c` 1189 / `a2_power.c` 1189 —
    exactly the five dmesg signatures. The earlier boot's six dumps all decode to
    `lte_ml1_common_timer.c` 390, matching that boot's dmesg table (Doc 149 §2). Word B advances
    **+11/+12 per fatal** (Doc 152 re-confirmed).
90. **Doc 149's "must not classify a fatal by its `file:line`" is a correct warning and a wrong
    conclusion.** The string does not name the *root cause* — several sites can trip near 900 s —
    but it does name **which site tripped**, and the firmware's own bytes confirm it. That is the
    modem RE's §5.2 exactly: one root event at different assert sites. **Doc 162's revision is now
    confirmed from the firmware's memory, not just from timing.**
91. **This closes the alternative reading of Doc 162 §3.** The suppressed-~901 s-fatal finding
    cannot be a stale/lagging signature, because the descriptor is rewritten per fatal and matches
    5/5. The live hypothesis is the other one: two competing mechanisms whose relative phase
    depends on the state the SSR recovery leaves behind.
92. **The 36-dump coredump corpus was one write away from destroying itself.** `/overlay` was at
    **100 % with 9.2 MB free**, holding 2.9 GB of dumps on a 3.2 GB partition, while the autostarting
    watcher writes a **fresh 85 MB dump on every fatal** — so the next fatal would have failed to
    write and the capture the watcher exists to make would have been lost silently. All 36 dumps
    were copied to `scratch/coredump_live_full/coredump_live/` and **verified byte-identical by md5
    (36/36)**; only 6 were in the repo before. `/overlay/coredump_live/` was then cleared —
    **9.2 MB → 2.9 GB free**. The watcher is still running.

## A twentieth round — Doc 164, 2026-09-21: the q6v5 SSR window is now observable — and the instrument was 4× the phenomenon

93. **The Doc 159 hang window is now instrumented.** Patch `817-q6v5-ssr-window-trace.patch`
    (temporary, diagnostic) puts one `dev_info()` immediately **before** each of the **23** steps in
    the window — the tail of `q6v5_mba_reclaim()` (steps 01–09) and the head of `q6v5_mba_load()`
    (steps 10–23), bracketed by `port failed halt` and `MBA booted`. The **last `q6v5-trace:` line
    printed names the step that never returned.** It is a loadable module
    (`kmod-qcom-rproc-modem`), so **no kernel image flash was needed**; the artifact is
    `79e7a858d41b6c9f7edf8b26b90c77f8`, `srcversion 3F0B3E31…`, 23 trace strings, vermagic matching
    the running kernel. Two traps: the `root-msm89xx` staging `.ko` is the **stale pristine
    baseline** (0 trace strings) — take the kernel-tree one; and the rootfs is **squashfs**, so the
    `.ko` reverts on every reboot, *including a watchdog reboot caused by the hang this soak exists
    to catch*.
94. **The window was re-measured independently, n=5: 43.726–46.944 ms, mean 45.481 ms.** Taken from
    the previous boot's `console-ramoops-0` (38 403 B, the whole boot), which Doc 159 did not use. Its
    43–47 ms figure reproduces exactly. **That span contains all 23 trace points plus the MBA
    firmware load** — the number the instrument has to fit inside.
95. **The instrument was 4.2× the phenomenon, and it was measured before it was trusted.** The console
    is `ttyMSM0,115200`. Writing to `/dev/kmsg` runs the full `printk` path (ring + console) from
    process context, and a `<N>` prefix selects the level, so the same probe measures printed and
    suppressed messages. Fitted: **cost ≈ 1.03 ms + 0.0904 ms/char**, against the 115200-baud
    serialisation rate of 0.0868 ms/char — a **4 % match**, i.e. pure serialisation. An 80-char trace
    line costs **≈ 8.3 ms**, so patch 817's 23 lines cost **≈ 190 ms against a 45.481 ms window**.
    An independent upper bound agrees: steps 10→23 plus `MBA booted` span **159.32 ms** on a cold
    boot against 45.48 ms for the same code on a real recovery (**113.8 ms ≈ 8.1 ms/line**).
    **An instrument four times the size of what it measures is not a measurement** — and the
    perturbation is not neutral, because it changes the timing of exactly the handoff the leading
    hypothesis blames.
96. **Fixed with one sysctl, verified, and persisted.** `console_loglevel = 6` suppresses KERN_INFO
    on **every** console (including `ramoops-1`) while still storing it in the ring buffer:
    **11.87 ms/line → 0.37 ms/line, 32× cheaper**, and the trace is still captured because the soak
    reads `/dev/kmsg`. Verified directly (`<6>PROBE_INFO` → `dmesg | grep -c` = 1). The two
    **reference points keep a kernel-side sink** — `fatal error received` and `port failed halt` are
    `dev_err` (3), so they still reach the serial console and the ramoops console. Applied at runtime
    **and** persisted in `/etc/rc.local` (a watchdog reboot would otherwise restore 7 and silently
    re-arm the 4× perturbation), and the soak records the level it started under. **The tradeoff,
    stated plainly:** the trace's only sink is now the userspace reader — justified because Doc 159's
    hang left **no** `console-ramoops` record anyway. **Pre-registered A/B:** if no hang lands within
    ~10 h, the *perturbed* arm is the next configuration to try, not a repeat of this one.
97. **An unplanned finding, from the same capture: the interval predicts the signature, 8/8.** The
    previous boot took **5** fatals (**not 4** — the ramoops copy has byte corruption: `received`
    appears as `rEceived`, so a plain `grep -c` undercounts; cross-check with a marker you are not
    counting — `MBA booted` = 6 and `port failed halt` = 5 agree on 5 recoveries). The cadence is
    **S, A, S, A, A — identical to run 8** (Doc 162 §3), which had to be recorded as "unexplained,
    n=4, a property of the boot". It reproduces. Combining both boots, **8/8 with no overlap**: the
    short fatal (**≈900.5–901.0 s** of modem uptime, spread **0.46 s**) is always
    `lte_ml1_sleepmgr_stm.c:4054`; the long fatal (**≈940.2–947.0 s**, spread **6.8 s**, 15× larger)
    is always `a2_power.c:1189`. **This reframes Doc 162's "alternation" as two mechanisms with
    different periods competing inside one boot, not one mechanism whose phase flips.** Testable
    prediction recorded for the next boot.
98. **`/dev/pmsg0` is a reset-surviving sink, verified empirically rather than assumed.** Three lines
    written to `/dev/pmsg0` in the previous boot came back after the reboot via
    `/sys/fs/pstore/pmsg-ramoops-0`. It has no filesystem and no page cache in the path — reserved
    RAM — which is exactly the case it exists for: the `sync` loop dying with the AP. Also
    re-confirmed rather than assumed: **netconsole is impossible here** (`usb0` is a configfs USB
    gadget, so no netpoll; `CONFIG_NETCONSOLE`, `CONFIG_DYNAMIC_DEBUG` and `CONFIG_FTRACE` are all
    off), and `console-ramoops-0` **did** survive a *clean* shutdown (the full 5-fatal boot) while
    Doc 159's hang left none.
99. **Two harness incidents worth carrying forward.** `DRY_RUN=1 ./build.sh kernel hmu05` prints
    "not executing" for the make steps but **still runs `sync_bsp()`**, so it healed a real drift —
    `DRY_RUN` guards only the make/config steps. And because **`pkill` does not exist on this image**,
    an old soak survived a restart and **three soaks ran concurrently** (14 processes, two extra
    full-ring replays); the rc.local block had also first landed **after `exit 0`** and could never
    run. **Always verify the process count after a restart** — `ps w | awk '/q6trace_soak/ && !/awk/'`
    must show exactly 5.

## A twenty-first round — Doc 165, 2026-09-21: one fatal makes TWO MBA reloads, and the coredump reader is inside the window

100. **The first instrumented fatal produced 46 trace lines — four half-cycles, two full `01..23`
     cycles, not one.** `q6v5_mba_load()` has **two** callers, and the second is not a retry path:
     `q6v5_reload_mba()` (`:1323`) is reached **only** from `qcom_q6v5_dump_segment()` (`:1544`), the
     remoteproc coredump reader, which `rproc_boot_recovery()` calls **between `rproc_stop()` and
     `rproc_start()`**. The execution order for one fatal is `stop→reclaim [01-09]`, `coredump#1 →
     reload_mba → mba_load [10-23]`, `coredump#last → reclaim [01-09]`, `rproc_start → q6v5_start →
     mba_load [10-23]`. The `dump_mba_loaded` flag is the mechanism: `mba_reclaim` clears it (`:1246`),
     so the dump's first segment boots the MBA; `mba_load` sets it (`:1189`), so the dump's last
     segment releases it. **Anyone reading the trace without this would count half 2 as a recovery
     attempt and then be unable to explain the single `MBA booted`.** Also: **`port failed halt` is
     emitted by the *coredump's* reclaim, not the restart's** — it prints at the top of
     `q6v5_mba_reclaim()` (`:1249-1253`), before step 01, measured 0.180 ms earlier.
101. **The coredump stalls the recovery path for 1.064 s and copies 85 398 475 B (80.2 MB/s).** With
     `RPROC_COREDUMP_ENABLED`, `rproc_coredump()` copies every segment into a `vmalloc`'d buffer
     *before* `dev_coredumpv()`, synchronously, while the modem is held down. Measured: step 23 of
     half 2 (`937.855186`) → step 01 of half 3 (`938.919368`) = **1.064182 s**; corroborated by the
     watcher (`captured 85398475 bytes`) and by `devcd1` appearing at 939.34 s, i.e. *after* the copy.
     **Doc 153 §4 is sharpened, not retracted:** "the coredump is created only after `rproc_stop()`
     returns" is true, but it is created **inside `rproc_boot_recovery()`, on the recovery critical
     path**, adding one extra MBA boot/reclaim pair to every fatal. Second-order consequence visible in
     the same capture: the stall is long enough for the AP's own stack to notice —
     `bam_dmux: refusing to queue command while modem is collapsed` falls *inside* the 1.064 s gap.
     **New confound, recorded not acted on:** Doc 159's hang was only ever observed *with the watcher
     running*; whether the 1.064 s in-path stall changes the hang probability is untested.
102. **The Doc 159 window is 86.2 % one *untraced* wait — the instrument covers 13.4 % of what it was
     built to observe.** Decomposed: `port failed halt`→step 01 = **0.180 ms** (0.4 %); steps 01→23 =
     **5.888 ms** (**13.4 %**), of which 2.931 ms is the `09→10` remoteproc-core hop, so the real
     register/SCM/regulator/clock work is ≈ 3.0 ms; step 23→`MBA booted` = **37.900 ms** (**86.2 %**),
     which is `q6v5_rmb_mba_wait(qproc, 0, 5000)`. Total 43.968 ms, reproducing Doc 164 §4's n=5 range.
     **Doc 164 §4's "that span contains all 23 trace points plus the MBA firmware load" is literally
     true and materially misleading** — the MBA *wait* is **6.4×** the trace. The blind spot is seven
     times the coverage.
103. **Doc 164 §9's pre-registration is left standing as written, with its coverage gap recorded
     instead of patched.** The measurement adds a **third outcome the prediction did not enumerate**:
     a hang inside the untraced `q6v5_rmb_mba_wait()` leaves the last line at **step 23**, which is
     neither 08/20/21 nor a regulator/clock step. The candidate set is larger than §9 said:
     `q6v5_xfer_mem_ownership()` has **14 call sites** in the file and patch 817 traces **three**
     (steps 08/20/21); the **two untraced mpss transfers inside `qcom_q6v5_dump_segment`**
     (`:1547`, `:1570`) sit in the dump path, one of them immediately before `port failed halt`.
104. **`q6v5_rmb_mba_wait()` is bounded, so the 37.900 ms is real modem boot time.** Source-verified:
     `msleep(1)` loop with `time_after(jiffies, timeout)` at `ms = 5000`; on expiry it returns
     `-ETIMEDOUT` and `q6v5_mba_load()` prints **`MBA boot timed out`** before `halt_axi_ports`. Two
     consequences: the window Doc 159 measured is *mostly the modem*, consistent with a handoff
     hypothesis rather than AP driver work; and a hang with `MBA boot timed out` + a dead console would
     point at the **untimed `readl()` of `RMB_MBA_STATUS_REG`** — a new, unproven candidate.
105. **The instrument is validated on live data, not just in a probe.** All 46 lines in order, `01..23`
     twice, **every inter-step delta 0.021–0.244 ms** — the ~0.1 ms/line predicted for
     `console_loglevel = 6`, against ~8.3 ms/line at level 7 (Doc 164 §5.3 confirmed on real data).
     `printk = 6 4 1 7`, so the rc.local persistence held across the boot. Telemetry: `defer_q 178 ==
     defer_sub 178`, `defer_keep 163`, **`defer_wipe_live 0`**, `guard_hits 0`, `oops 0`,
     `pc_resync 0` — patches 810/812/814 all holding across a real fatal. The recovery was clean
     (43.968 ms window, ≈ 2.5 s outage, `retries: 0`, then 3/3 ICMP 0 % loss on `10.93.59.183/28`).
     `pc_irq 327` in 1353.6 s = 14.5 edges/min ≈ **7.3 collapses/min, one per ~8.3 s**.
106. **`modem pc-ack timeout during resume` is a symptom, not a precursor — do not build a trigger on
     it.** 1× in this boot (1.5 s before the fatal), 6× in the previous boot, and its position relative
     to the fatal is *inconsistent*: before it at 1854.87 s, **after** it at 2757.99 s, and twice
     (4990.73, 5331.55) with no fatal at all.
107. **Doc 164 §6's prediction is partially scored, and the partial result matters.** Its bands are in
     *modem* uptime = `AP time of fatal − AP time of the previous modem-up` (verified against Doc 164
     §6's own table). This boot's ring had wrapped past the modem's initial boot, so the offset must be
     borrowed from the previous boot (`12.4 s`), giving **≈ 925.4 s** — in **neither** band. **The
     940.2–947.0 s "long" band was a four-sample artefact** and is contradicted by Doc 162's own
     taxonomy (`a2_power.c:1189` n=7 spanning 68.5–941.3 s, *not* a clock). **What survives without any
     offset:** this boot's first fatal differs in **both** signature and AP time from the previous
     boot's first fatal, which is what "two mechanisms competing" predicts and which reproduces Doc
     162 §3's "on 2 of 5 boots the ≈901 s fatal did not fire at all". **A band that only holds at four
     samples is not a band.** Also recorded: nothing measures the modem-up offset, so the next boot
     needs one line added to the soak (§10 item 6).
108. **Two leaked-process traps, found and cleaned.** (a) **`cat /dev/kmsg | grep PAT | head -N` never
     terminates on this busybox** — `grep` block-buffers, `head` never gets N lines, never exits, and
     `grep` never gets `SIGPIPE`, leaving a permanent 3-process pipeline holding a `/dev/kmsg` fd (the
     broken `RINGCHECK` probe was still stuck 940 s later). Use **`grep -m N`** (busybox 1.37 supports
     it — verified) or redirect to a file; `dmesg | grep -c` is safe because busybox `dmesg` reads via
     `syslog(2)` and does return EOF. (b) **Killing the soak by pattern orphans its `cat /dev/kmsg`
     child** — one such orphan still held an fd to `/overlay/q6trace_stream.log (deleted)` since AP
     112 s, silently draining the ring into a deleted inode, which proves the earlier "kill the old
     soak" cleanup was incomplete. **Kill the children too**, then re-check `ps w |
     grep -c "[q]6trace_soak.sh"` = 5. Also a capture-hygiene trap: this capture's first **1322 of 1700
     lines are our own instrument's leftovers** (720 lines of 120 × `X`, counters `SHORT79`…`SHORT85`,
     AP 405.46–496.01 s), because the ring wrapped and the capture *begins* mid-probe.

## A twenty-second round — Doc 166, 2026-09-21: the AP hang is the SSR *teardown hand-off*, upstream of the instrumented window

109. **The hang was caught — on a spontaneous fatal, and in a place no instrument was looking.**
     Crash #2 at AP 1840.390 s (`lte_ml1_sleepmgr_stm.c:4054`) printed `recovering`, then
     **`bam_dmux: SSR before shutdown: scheduling teardown work` at 1840.413105 — and that is the last
     message the kernel ever emitted.** Two independent pstore sinks agree, and the second one makes
     the claim provable rather than merely suggestive: `console-ramoops-0` is the **ramoops console**
     (`dev_err` and above only, because `console_loglevel = 6`), while `pmsg-ramoops-0` is the soak's
     **filtered `/dev/kmsg` mirror** whose filter (`*"SSR "*`, `*q6v5-trace:*`, `*"port failed halt"*`,
     `*"MBA booted"*`, `*"Kernel panic"*`, `*"BUG:"*`, `*"Unable to handle"*`, `*"watchdog"*`, …)
     **covers every expected next line and every kernel fault banner.** None of them fired. Both
     zones use `PRZ_FLAG_ZAP_OLDEST` (they retain the *newest* data), and both end at the same instant.
     So this is not "the console went quiet" — **the one sink that carries `dev_info` and is guaranteed
     to capture the next expected line is silent too.**
110. **A third, printk-independent line of evidence: the coredump watcher.** `coredump_watch.log`
     shows boot B produced **exactly one** dump (crash #1's, at 939.34 s) and **none for crash #2**.
     Since a coredump is created only after `rproc_stop()` returns (Doc 153 §4), **`rproc_stop()` did
     not return.** This is a filesystem observation of a coredump device — no `printk` involved.
111. **The hang is in the five-line region `qcom_bam_dmux.c:2233-2237`, and it is upstream of *both*
     windows.** The teardown work's own first print (`:2238`, `executing serialized asynchronous SSR
     teardown` — measured at +2.747 ms on the healthy crash #1) never appeared, so the hang is in
     `:2233` (a `WRITE_ONCE`), `:2234` `cancel_delayed_work_sync(&tx_retry_work)`, `:2235`
     `cancel_work_sync(&tx_wakeup_work)`, or `:2237` `mutex_lock(&state_lock)`. **The only unbounded
     waits there are the two `cancel_*_sync()` calls — no timeout, not interruptible.** `tx_wakeup_work`
     is not a leaf: its first two acts are `pm_runtime_resume_and_get()` (`:739`) and
     `mutex_lock(&state_lock)` (`:758`), so it can be *running yet not finished* for as long as the PM
     core or the mutex makes it wait — and `state_lock` is held across a **full `bam_dmux_power_on()`**
     by the threaded `pc_irq` handler (`:1903`→`:1909`) and across `bam_dmux_power_off()` (`:1911`,
     `:1934`), which is exactly what runs during an SSR. **Both cancels are also redundant:** `:2233`
     sets `in_teardown` one line earlier, and both target works test it as their **first statement**
     (`tx_wakeup_work:736`, `tx_retry_work:850`) and re-test under the lock (`:764`). **The cancels add
     a wait, not a guarantee.** Patch 820 replaces them with the non-blocking `cancel_work()` /
     `cancel_delayed_work()`; mutual exclusion is already provided by `state_lock` at `:2237`.
112. **Doc 164 §9's pre-registration is FALSIFIED IN SITE — and Doc 165 §4.1's coverage gap was too
     narrow.** §9 predicted the last trace line would be step **08, 20 or 21** (the traced
     `qcom_scm_assign_mem()` sites). **There is no `q6v5-trace` line in the hang at all** — the last
     line is the notifier's hand-off, ~10.9 ms *before* step 01 would have run. The SCM hypothesis is
     not disproved; it was simply never reached. And §4.1 said a hang in the untraced 86.2 % would
     leave the last line at step 23 — true *of a hang inside the window*, but this hang is **before the
     window begins**, in the `rproc_stop()` → SSR-notifier → bam_dmux-teardown **hand-off**. **The
     instrument's blind spot was therefore not just the inside of the window but the whole region
     upstream of it** — the fifth time an instrument's *scope* has been the limiting factor. The
     lesson to carry: **trace the hand-off, not the state machine** — the hang has now been found
     twice, both times in the *gaps between* instrumented regions.
113. **The reset is silent, and the two AP hangs do NOT share a reset mechanism.** The AP came back
     ~2–4 s after the fatal (host arithmetic: boot B start 12:00:48 → fatal 12:31:28.4 → new boot
     12:31:31), consistent with `kernel.panic = 3` — **but this round adds a fact that argues against a
     panic:** the ramoops DT node is
     `compatible console-size name pmsg-size record-size reg` — **`record-size` present, `max-reason`
     absent**, so the **dmesg zone exists with the default `max_reason = KMSG_DUMP_OOPS`** and an oops
     *or* a panic **would** leave a `dmesg-ramoops` record. There is none. Independently, a `panic()`
     prints `Kernel panic - not syncing:` at `KERN_EMERG` into the **in-kernel** ramoops console,
     which needs no userspace and cannot be bypassed — and that record's last line is `recovering`.
     **So: not an oops, and not a panic.** Doc 159 recorded a 30 s PM8916 PON WDT for *its* hang;
     ~2–4 s does not match, so **the two AP hangs do not share a reset path** even though they share a
     site class. A panic whose console write was lost cannot be fully excluded (an invalid pstore
     buffer is discarded silently). **New instrument proposed:** read the AP's restart reason from
     **SMEM item 403 (`SMEM_POWER_ON_STATUS_INFO`)** — the very item the driver's own comment at
     `:2250` warns must not be *written*.
114. **RETRACTED: the "stale `qcom-time-daemon` performing a periodic ATS_USER refresh" lead is dead.**
     Raised while auditing the remaining AP-side modem actors (Doc 164 §12 item 4) — the device's init
     carries a stale comment claiming `-r 60` *"MUST stay > 0"*, and a log line looked like `ENABLED`.
     Direct process inspection disproves it: **`/usr/sbin/qcom-time-daemon -r 0 -v` is what is
     running**, and the daemon logs **`Periodic ATS_USER refresh: DISABLED (interval=0s)`**. There is
     **no periodic ATS_USER re-anchor on this device.** The only residue is cosmetic — the deployed
     init (`8cd979087ed55930c458911056d7258c`) is an older revision whose *comment* is wrong while its
     *command* is right. **Do not carry this forward.**
115. **Corrected: the two pstore sinks are not interchangeable.** Earlier this session
     `console-ramoops-0` was treated as "the" dying-boot record. At `console_loglevel = 6` it holds
     **only `dev_err` and above**; the instrument's entire output is `dev_info` and is *invisible*
     there. **Corollary for every future hang: read both sinks, and read the filter that produced the
     second one.** Also confirmed: pstore records **survive the reboot they cause** (boot C's
     `console-ramoops-0` still holds boot B's dying log, same 179 975 B, same tail) — the constraint is
     only that the *following* boot's traffic overwrites them. And **the instrumented `.ko` survives a
     reboot too** (it lives in the overlay upperdir while `/rom` holds the pristine
     `6ac6603141c25b1e3462615411e92f7c`), so **Doc 164 §3's "re-copy after every reboot" was
     over-cautious — verify the md5, don't re-copy blindly.**
116. **A secondary defect found while reading, recorded but deliberately not fixed by 820:** the *same*
     work item is queued on **two different workqueues** — `queue_pm_work()` = `queue_work(pm_wq, work)`
     (`include/linux/pm_runtime.h:62`, used at `:687` and `:1922`) and `queue_work(system_wq, …)`
     (`:857`). A `work_struct` must not be queued to two queues: the second `queue_work()` sees
     `WORK_STRUCT_PENDING` and **returns false**, so a `tx_retry_work`-initiated retry can be silently
     **dropped** while the item is pending on `pm_wq`. **Lost-retry bug, not a hang** — separate patch,
     so that 820 tests exactly one change.
117. **Probe trap, in addition to round 21's:** `grep -c PATTERN /dev/kmsg` **never terminates** —
     `/dev/kmsg` is a stream with no EOF, so `-c` waits forever (it cost a 20 s ssh timeout this
     round). Use **`dmesg | grep -c`**, which returns because busybox `dmesg` reads via `syslog(2)`.
     The device's `awk` also needs an **anchored** timestamp pattern (`/^\[ *1840\.3/`), not a bare
     substring. Evidence: `evidence/165_hang_1840s/{dying_boot_tail,corroboration,source_region}.txt`.
118. **A SECOND site of the same defect, and it is a *true ABBA on one mutex* — found only after
     site 1's fix was written, and it is the stronger of the two.** `bam_dmux_pm_quiesce()` (`:1661`)
     and `bam_dmux_power_off()` (`:1577`) both do
     `cancel_delayed_work_sync(&dmux->rx_watchdog_work)` **while holding `state_lock`**:
     `pm_quiesce` has **exactly ONE caller** — `bam_dmux_pc_irq()` at `:1936` — and it holds the lock
     from `:1903`; `power_off` is reached under the lock from `pc_irq` (`:1911`, `:1934`), the
     teardown work, the SSR powerup work and `power_on`'s error paths. And
     `bam_dmux_rx_watchdog_func()` **acquires the same lock at `:1309`**. So **pc_irq waits for the
     watchdog while the watchdog waits for pc_irq's lock** — no third party, no PM-core subtlety, and
     it is exercised on **every ordinary power collapse** (`pc_irq` 327 times in 1353.6 s ≈
     **7.3 collapses/min**), i.e. far more often than site 1. **Patch 820 fixes both sites** (four
     hunks). **The asymmetry that makes it safe — do not "simplify" it away:** `rx_watchdog_work`
     TAKES `state_lock`, so it is already mutually excluded and re-checks `pc_state`/`in_teardown`/the
     line level under the lock at `:1315-:1317` ⇒ its cancel may be non-blocking; **`rx_rearm_work`
     does NOT take `state_lock`** (no `state_lock` site between `:1213` and `:1261`) and submits RX
     buffers ⇒ its cancel **MUST stay synchronous** or it will touch DMA channels concurrently with
     `dmaengine_terminate_sync()`. Two further sites of the same class recorded and **deliberately
     left out** so 820's effect stays attributable: `bam_dmux_remove()` (`:2582-2626`) has the same
     ABBA and **will hang a future `rmmod`**; and `tx_wakeup_work` is queued on **two workqueues**
     (`pm_wq` via `queue_pm_work` at `:687`/`:1922`, `system_wq` at `:857`) so a retry can be
     silently dropped (lost-retry bug, not a hang). Patch 820 also adds **five `dev_err` hand-off
     trace points** (T0 after `schedule_work` in the notifier; T1–T4 across the teardown work's
     entry, two cancels and lock) — `dev_err` so they reach `console-ramoops` at
     `console_loglevel = 6`, each containing `SSR ` so the existing soak filter picks them up with
     **no soak change**. **Decode table pre-registered (Doc 166 §9): T0-without-T1 ⇒ the work never
     got a worker (starvation, not the cancels); T3-without-T4 ⇒ an external `state_lock` holder,
     i.e. this ABBA was the real cause.** Verification before building: `patch -p1 --dry-run` OK, and
     applying 820 to the pre-820 file reproduces the live build-tree file **byte-for-byte**.
     Artifact: `msm89xx/patches/820-bam-dmux-ssr-teardown-nonblocking-cancel.patch`, md5
     `8e94dd68f6a973c50b4645a919557a35`; evidence
     `evidence/165_hang_1840s/deploy_820.txt`.
119. **TWO CORRECTIONS TO DOC 166 ITSELF, made after its first draft — and they matter more than the
     original finding. Read `166` §2.2b and §5.4 before citing it.** (a) **The `pmsg` sink is fed by
     a *userspace* reader.** `pmsg-ramoops-0` is written by the soak's `cat /dev/kmsg | while read …`
     loop, so its last line is *the last line userspace managed to copy*, not the last line `printk`
     stored. Therefore "no further line appeared" is **proven only for `dev_err` and above**
     (`console-ramoops-0` is a genuine in-kernel console sink) — the missing `dev_info` line
     `executing serialized asynchronous SSR teardown` **may simply never have been copied**, and
     `rproc_stop()` never returning is proven *only* by the coredump watcher, which is printk-free.
     (b) **`wwan0at0 disconnected` is not a bam_dmux line.** `wwan_remove_port()` (`wwan_core.c:529`)
     is called from `rpmsg_wwan_ctrl.c:145` (`rpwwan_remove`), `mhi_wwan_ctrl.c:255` and
     `wwan_hwsim.c:239` — **`qcom_bam_dmux.c` contains no `wwan_*` port calls at all** — so it is an
     **rpmsg/SMD** teardown line on a different thread. Combined, the evidence is that **three
     independent consumers stop within ~7 ms of the notifier callback**: the SMD/rpmsg teardown,
     bam_dmux's teardown work, and the userspace `/dev/kmsg` reader. **A single driver mutex cycle
     cannot explain that** — the other three CPUs would keep running — so §5.4 adds a **live
     alternative: the stall is in the logging/console path.** The console serialises synchronously at
     115200 baud (1.03 ms + 0.0904 ms/char, ~8.3 ms per 80-char line) and the last line before the
     silence, `recovering`, **is a `dev_err`**, i.e. the console was actively writing in the final
     millisecond; a wedged console/logbuf lock silences every sink and blocks every `/dev/kmsg`
     reader at once, and would also explain `rproc_stop()` never returning without any bam_dmux lock
     cycle. **Patch 820 is therefore downgraded from "the cure" to "two provable deadlocks fixed"**
     (one of them a true ABBA) — still worth shipping, and it carries the T0–T4 probes, but not
     claimed to be the cause. **Also new: this hang is one step EARLIER than Doc 151/157's
     `echo stop` hang**, which ends on *two* lines (`SSR before shutdown` **and**
     `wwan0at0 disconnected`); the region has **at least two adjacent hang points**. Plus a new
     instrument (§13 item 3): a **non-`printk` liveness beacon**, and the cheap test of booting with
     the serial console removed or `console_loglevel = 1`.
120. **§5.4 IS RETRACTED — the logging-path hypothesis, disproved by reading the kernel source
     (Doc 166 §5.5).** Item 119's premise (b) — "a wedged console/logbuf lock silences every sink and
     blocks every `/dev/kmsg` reader at once" — **is not true in Linux 6.12**, and the four claims it
     rests on were each checked against
     `openwrt/build_dir/target-aarch64_generic_musl/linux-msm89xx_msm8916/linux-6.12.94/`, the tree the
     running modules were built from: (i) `grep -rn logbuf_lock kernel/printk/ include/linux/printk.h`
     → **no matches**; the global `logbuf_lock` was **removed in v5.10**. (ii) `printk_ringbuffer.c`
     contains **zero spinlocks** and its own header states *"readers and writers to **locklessly**
     synchronize access to the data"*; `devkmsg_read()` (`printk.c:822`) takes only
     `mutex_lock_interruptible(&user->lock)` — a **per-file-descriptor** mutex — so one reader cannot
     block another. (iii) `dmesg` is `syslog(2)` `SYSLOG_ACTION_READ_ALL` → `syslog_print_all()`
     (`printk.c:1684`), which takes `syslog_lock` **only inside `if (clear)`**, i.e. only for
     `dmesg -c`; the record walk is lockless. (iv) `console_lock` gates **console output**, not the
     ring — `vprintk_store()` stores locklessly and output defers to the printk kthread. **So a wedged
     console cannot stop a `/dev/kmsg` reader.** **The corrected reading is SIMPLER than the
     hypothesis it replaces:** `devkmsg_read()` **sleeps on `log_wait`** when the ring has no new
     records (`printk.c:848`), so the pmsg filter's silence means exactly one thing — **nothing
     further was stored** — and it is *not* independent evidence of a hang, merely what a *healthy*
     reader looks like when the kernel has nothing to say. Hence the "**three independent consumers
     stop within ~7 ms**" of item 119 collapses to **two print sites that never emitted** (bam_dmux's
     `:2238`, and the rpmsg/SMD `wwan0at0 disconnected`) **plus one reader with nothing to read**, and
     **one** blocked step explains both, because `QCOM_SSR_BEFORE_SHUTDOWN` is a **blocking notifier
     chain** — if an entry after bam_dmux's does not return, nothing downstream of it runs. **§5.4's
     premise, "a single mutex cycle inside one driver cannot explain that", is simply wrong: it can.**
     **Consequences.** (a) §5.4 was the **only** reason patch 820 was downgraded, so with it retracted
     the **`state_lock` ABBA and the unbounded `cancel_*_sync` waits are again the leading explanation**
     — still unproven, but no longer competing with a hypothesis this kernel cannot support. (b) The
     console is **still** worth one cheap test (it remains the only sink `dev_err` reaches, and
     ~8.3 ms/line is measured) — but it is a *sink* failure, not a *stall* mechanism. (c) **The CSV
     discriminator survives with CORRECTED POLARITY**: CSV **continuing** past the last `pmsg` line
     means the AP is **alive** and the stall is **confined to the code path that stopped printing** —
     which is exactly what a driver lock cycle on one thread looks like, so it does **NOT** exonerate
     the driver locks (item 119 had this backwards); CSV **stopping** first means a genuine global
     stall. The CSV was confound-checked before being accepted: `rx_telemetry_show()`
     (`qcom_bam_dmux.c:2116`) takes only `dmux->rx_lock` for a bounded loop, **not** `state_lock`.
     (d) **A `/dev/kmsg`-based watcher structurally cannot serve as a liveness beacon**, which is the
     real justification for the §13 item 3 beacon — not the speculative one item 119 gave.
     **Reusable lesson:** the error was not a wrong hypothesis, it was **inferring a mechanism from a
     pattern without checking the mechanism exists in the kernel being run** — that lock had been
      deleted four years before this kernel was cut. Evidence: `evidence/165_hang_1840s/corroboration.txt`
      section 11.
121. **THE AP HANG IS NOT DETERMINISTIC — it is a race. And the SSR teardown order is a race too
     (Doc 167).** Boot C was left running after Doc 166 and reached **2181.14 s through TWO fatals,
     recovering from both** (0 oops, 2 coredumps, `guard_hits` 0, `defer_wipe_live` 0, `defer_q ==
     defer_sub` = 300). **Its second fatal landed at AP 1837.400528; boot B hung at AP 1840.390188 —
     2.989 s apart, opposite outcomes.** So the hang is **not** a function of the fatal, the AP time, or
     "the second fatal of a boot"; the *trigger* (the ~900 s fatal) is reproducible and the *failure*
     is not, which is what has been masking this. **A signature correlation is offered as a hypothesis
     only (n = 2 per cell):** both observed hangs' fatals were `lte_ml1_sleepmgr_stm.c:4054`, while
     `a2_power.c:1189` fatals recovered — mechanistically credible (a sleep-state-machine fatal may
     leave the A2 power-collapse state machine in a different state) but far too small a sample.
     **The teardown order is ALSO a race, and it flips *within one boot*:** boot C's fatal #1 printed
     `wwan0at0 disconnected` **before** `executing serialized asynchronous SSR teardown`, and its fatal
     #2 printed them in the **opposite** order. **So Doc 166 §2.2c's ordering *argument* is retracted**
     (the bam_dmux teardown work and the rpmsg/SMD teardown are concurrent, so "ends on two lines vs
     one" does not establish a step ordering) — **but its conclusion survives for a better reason: the
     right discriminator is *which* line is missing.** In boot B's hang **neither** appeared, and since
     the rpmsg teardown runs *inside* `q6v5_stop()` — after the notifier returns — that means
     `rproc_stop()` never got past the notifier. In the `echo stop` hang `wwan0at0 disconnected` **did**
     appear, so `rproc_stop()` *did* continue and only bam_dmux's **teardown work** failed to print —
     **i.e. the two hangs have *different* suspects**, and 820 may fix `echo stop` while leaving the
     spontaneous hang untouched; report them separately. **Doc 165 confirmed exactly:** boot C's trace
     log is `14 + 4 × 23 = 106` lines — the boot's bring-up plus **two full 01..23 cycles per fatal**,
     and the CSV's `traces` column steps 14 → 60 → 106, i.e. **+46 per fatal**. **`B` is NOT a fatal
     counter across boots:** within boot C it advanced **+11** (0x01128E3D → 0x01128E48, Doc 163's
     claim confirmed on a second boot), but run 8's last fatal → boot C's first is **+42 where three
     fatals explain only +33..+36** — an unexplained excess; **do not use `B` to count fatals or to
     detect missed dumps** (mechanism left open, not guessed). **THE CONSEQUENCE THAT MATTERS:** the
     hang rate is **~1 in 20–40 SSRs (3–5 %)** — 39 dumps in `coredump_watch.log` plus Doc 159's 1-in-21
     — so **"zero hangs observed" only becomes evidence at n ≥ 60 SSRs (rule of three), which is ~18 h
     of soak at ~2 SSRs per 35 min. Boot C already delivered 2 clean SSRs *without* 820, so a single
     clean 820 boot means nothing.** Pre-register the sample size; score T0–T4 on **every** SSR (they
     fire on healthy SSRs too, so one boot answers "does the teardown work now complete?"); and re-run
     820 **without** the `dev_err` probes before claiming the cancels fixed anything, because Doc 166
     §9.1 measures T0 at **≈ 9.6 ms** of console time on `rproc_stop()`'s critical path — larger than
     the 7.453 ms healthy hand-off it sits in. **Boot C is therefore the pre-820 CONTROL.** **820 was
     deployed at the end of this round** (`qcom_bam_dmux.ko` md5 `e441491317f09e34a3e72dc15cdacd3c`,
     240 544 B, srcversion `F7405AD685715AEC3C274D9`, 5 T-strings, pre-820 backed up to
     `/overlay/modbackup/qcom_bam_dmux.ko.pre820`), and the post-reboot state is clean: `printk =
     6 4 1 7`, 14 trace lines, 0 fatals, 5 soak procs + watcher + beacon. **HIGHEST-LEVERAGE NEXT
     EXPERIMENT (§7.1): the `echo stop` hang is the *teardown work* blocking, which is exactly what 820
     fixes — so 820 may make `echo stop` safe, and that would give an SSR *forcing function* and
     collapse the n ≥ 60 requirement from ~18 h to minutes.** Test it **once**, **after** the first
     healthy post-820 SSR is scored (a hang there is a datapoint, not a failure — the ledger,
     watcher and beacon all autostart). New instrument: `ssr_ledger.sh` writes one persistent line per
     fatal (`uptime,n,sig,t0..t4,exec,wwan,coredumps`) to `/overlay/ssr_ledger.csv`, because dmesg is
     lost on every reboot and an 18 h soak spans reboots; **a hang produces NO line for that fatal, so
     gaps are the signal**, cross-checked against `coredump_watch.log`. Evidence:
     `evidence/167_hang_not_deterministic/`.

## A twenty-fourth round — Doc 168, 2026-09-21: the AP freeze is the *async* teardown, and `echo stop` is now a forcing function

122. **820 WORKS — AND IT MOVED THE HANG. `echo stop` IS NOW A 2-SECOND FORCING FUNCTION, AND
     DOC 167 §7.1's PREDICTION IS FALSIFIED (Doc 168).** Both post-820 boots hung. **Boot
     820-#1:** fatal AP **276.111919** (`a2_power.c:1189`), `q6v5-trace 01..09` (the modem
     power-down) at **276.156528-276.156671**, **T4 `state_lock acquired` at 276.164748**, then
     **nothing**. **Boot 820-#2:** fatal AP **951.663368** (`a2_power.c:1189`), **NO `q6v5-trace`
     at all**, **T4 at 951.719173**, then **nothing**. Both: **no coredump ⇒ `rproc_stop()` never
     returned** (`rproc_coredump()` runs only after it returns), and both silently reset.
     `console-size = 0x40000` (256 KiB) with a ~24 KiB record starting at `[0.000000]` ⇒ **the
     console region did not wrap, so T4 really is the last line the kernel emitted.** **What 820
     fixed is real and must be reported: pre-820 the teardown work died *before its first print*
     (Doc 166); with 820 it runs, clears both cancels (T2/T3 print) and acquires `state_lock`.**
     **What it did not fix: the AP still freezes — after T4.** **A THIRD post-820 SSR RECOVERED
     (fatal AP 943.555967, coredump created, modem back up, `rproc` `running`, ledger line
     `951.90,1,a2_power.c:1189,1,1,1,1,1,1,1,4`) — so 820 is 1 recovery / 2 hangs, not
     deterministic — AND IT SUPPLIES THE MECHANISM: the teardown work must finish its DMA
     teardown BEFORE `q6v5_stop()` gates the modem.** T4−fatal = **+43.3 ms** with the power-down
     at **+62.1 ms** ⇒ RECOVERED; **+52.8 ms** with the power-down at **+44.8 ms** ⇒ FROZE;
     **+55.8 ms** with the trace never reached (the freeze preceded it) ⇒ FROZE; pre-820 boot C
     reached its first print at **≈ +3.3 ms** against a **≈ +45 ms** power-down ⇒ never hung.
     **n = 3, and it re-ranks the mechanism list: H1 (the ordering race) explains all three
     post-820 SSRs *and* the pre-820 control, so H2 (the BAM's own runtime PM) is no longer
     needed.** ⇒ **the probes did not create a bug, they widened an existing race from ~0 % to
     ~2-in-3** — and therefore **removing the probes alone is NOT a fix** (it narrows the window
     back without closing it; it is the right *control*, the wrong *remedy*). Also corrected:
     **`dev_info` IS in `dmesg`** — this boot's ring buffer contains `wwan0at0 disconnected`,
     `executing serialized asynchronous SSR teardown` and `stopped remote processor`, so the
     filtering is on the **console/`console-ramoops` sink**, not the ring; it is still useless
     for a hang, because a hang ends in a reboot and only pstore survives it. And **`T1` printing
     before `T0` is NORMAL** (this boot: T1 943.578229, T0 943.581580), so only "T0 without T1"
     diagnoses workqueue starvation. **The measurement that explains boot
     #1's difference from boot C:** the T1→T2/T2→T3/T3→T4 gaps are **8.061/8.680/7.897 ms** in
     boot #1 and **7.751/8.382/11.530 ms** in boot #2, and the work between them
     (`cancel_delayed_work`, `cancel_work`, `mutex_lock`) is sub-microsecond — so **each `dev_err`
     probe costs ~8 ms of synchronous serial-console time and patch 820's five probes add ≈ 40 ms
     to the teardown path**, which is exactly what moved T4 (+52.83 ms) past the power-down
     (+44.75 ms) in boot #1. **That is a self-inflicted confound** and Doc 167 §10 already required
     the no-probe re-run. **But it is not the whole story:** in boot #2 *and* in both `echo stop`
     runs the power-down had **not** started when T4 printed, and the AP froze anyway — so any
     "the BAM was already unpowered" theory is **insufficient**, and the honest mechanism list is
     H1 (ordering race against the power-down — explains boot #1 only), **H2 (the BAM's *own*
     runtime PM: `bam_dma_terminate_all()` takes **no** `pm_runtime_get_sync()`, unlike every other
     hardware-touching BAM entry point at `bam_dma.c:576/779/805/914/1032`, so `bam_chan_init_hw()`
     can write an unclocked block — timing-dependent and does **not** need the modem down, so it
     fits)**, and H3 (a wait inside the `state_lock` section never completes). **The freeze
     signature is a stalled CPU, not a wedged console:** in the `echo stop` runs the shell still
     received **`RC=0` over SSH (USB)** while the **serial console** had stopped at T4.
     **⇒ `RC=0` from `echo stop` is NOT a success signal and the write completing is NEVER evidence
     that the SSR teardown finished — `rproc_stop()` schedules the teardown work and does not wait
     for it. Do not use it as a health check.** **`echo stop` reproduces the freeze 2/2** (AP 137 s →
     RC=0 → freeze, last line T4 @ 138.112650; AP 165 s → RC=0 → freeze, last line T4 @ 165.997713),
     which collapses the Doc 167 §7 "n ≥ 60 SSRs ≈ 18 h" loop to **~2 s per test** — that is the
     real prize of this round. **Doc 167 §7.1's prediction is therefore HALF RIGHT: 820 does get the
     work past the cancels (so the `echo stop` hang is no longer the cancel deadlock), but `echo
     stop` is still NOT safe and the forcing function it promised does not exist in that form.**
     **Doc 167 §3's "which line is missing" discriminator is RETRACTED in part:**
     `wwan0at0 disconnected` is `dev_info` (`drivers/net/wwan/wwan_core.c:529`) and is **suppressed
     on the console at `console_loglevel = 6`** — it can only appear in the userspace-fed,
     block-buffered `pmsg` mirror, so **its absence from `console-ramoops` is not evidence at all.**
     The discriminator that survives is **whether `q6v5-trace 01..09` appears**, because those are
     `dev_err`. Also recorded: a **redundant** `cancel_delayed_work_sync(&dmux->tx_retry_work)`
     survives at `:1636`, one line after 820 made the same cancel non-blocking at T2 — it is a
     **leaf** (`bam_dmux_tx_retry_work()` takes no `state_lock`, only `queue_work()`), so it is
     dead code and **not** a lock-cycle suspect. **PATCH 821 WRITTEN (not yet scored):**
     `821-bam-dmux-ssr-teardown-flush-before-powerdown.patch` (67 lines, md5
     `29307724173b492af888087e2fb69fba`, both patch trees) adds **one** behavioural change —
     `flush_work(&dmux->ssr_teardown_work)` in the `QCOM_SSR_BEFORE_SHUTDOWN` notifier, which runs
     in the recovery thread's **process context** and may sleep — so the BAM is released **before**
     `rproc->ops->stop()` powers the modem down. It also adds four localisation probes (T5 after the
     RX DMA release, T6 after the TX DMA release, T7 after `bam_dmux_power_off()` returns, T8 at the
     end of the work) so a failure localises itself without a second build. **Pre-registered in
     Doc 168 §8:** Question A `echo stop` × **3** (cheap structural — "does the path complete?"),
     Question B the natural ~900 s fatal, Question C the 820-without-probes control (next build).
     **Explicitly NOT claimed: n = 3 proves nothing** — `echo stop` is only *suspected* to be 100 %
     (measured 2/2) and the expensive question still needs **n ≥ 60 SSRs**. Evidence:
     `evidence/168_teardown_races_powerdown/`.

123. **821 IS SCORED — IT WORKS, THE BLOCK IT TARGETED IS A RACE, AND IT EXPOSES A NEW DEFECT
     (Doc 168 §2.3).** Two `echo stop` runs on the 821 module (md5
     `364376867224df54d47b9b7a06528db3`) give **opposite** results, and that is the finding.
     **Run B (the teardown COMPLETES, for the first time ever):** `T1` 309.301329 → `T4`
     309.324357 → **`T5 rx released` 309.333033 → `T6 tx released` 309.344838 → `T7 power_off
     returned` 309.349416 → `T8 work done` 309.363183 → `T9 flush returned` 309.363296** →
     `q6v5-trace 01..09` 309.375474-309.375629 → **`stopped remote processor` 309.375823**. So the
     `flush_work()` returned, `rproc_stop()` proceeded to `q6v5_stop()`, and the modem went down —
     **Question A's structural test passes and the predicted observable (`T9` before
     `q6v5-trace 01`) is exactly what happened.** **Run A (earlier):** the record ends on `T4` with
     **no `T5`** — the work blocked inside `bam_dmux_power_off()`. ⇒ **THE T4→T5 BLOCK IS
     INTERMITTENT, NOT DETERMINISTIC** (run B's T4→T5 gap is **8.7 ms** — an ordinary `dev_err`
     cost, no block), so **§2.2's H1 (the ordering race against the power-down) is FALSIFIED**: run
     A blocks **with the modem still powered** (no `q6v5-trace` at all), so the power-down cannot be
     what stops it, and run B has the ordering 821 was built to guarantee and the AP **still dies** —
     from an unrelated defect. §2.2's timing table was a genuine n = 3 correlation, but a
     correlation with the **outcome** is not the mechanism. **THE NEW DEFECT:** **0.14 ms after
     `stopped remote processor`, a `list_del corruption` WARNING fires in `qmi-proxy`** —
     `prev->next should be ffff800083533c88, but was c40df08e5a393833 (prev=ffff6ee6c4a07f60)`,
     `WARNING: CPU: 3 PID: 4833 at lib/list_debug.c:62 __list_del_entry_valid_or_report+0xd0/0xfc`,
     call trace `__list_del_entry_valid_or_report → remove_wait_queue → poll_freewait → do_sys_poll
     → __arm64_sys_ppoll`. **`ffff800083533c88` shares a page with `sp = ffff800083533910`, so the
     entry being removed is a `poll_table_entry` on qmi-proxy's OWN kernel stack, and `prev`
     (`x20 = ffff6ee6c4a07f58`, i.e. `head + 8`) is the wait-queue HEAD — its `next` pointer had been
     overwritten.** Only three `poll_wait()` sites exist in the running tree: `wwan_core.c:776`
     (`port->waitqueue`, `wwan_port_fops_poll`), `rpmsg_char.c:307` (`eptdev->readq`), and
     `qcom_smd.c:998` (`channel->fblockread_event`); `Modules linked in` lists `rpmsg_wwan_ctrl` and
     `wwan` and **not** `cdc_wdm`, so `port->waitqueue` (the `/dev/wwan0qmi0` QMI port) is the
     leading candidate — and `wwan_remove_port()` (`wwan_core.c:511`) **wakes that queue (`:524`)
     and then unregisters the port (`:530`) in the same breath**, called from
     `rpmsg_wwan_ctrl_remove()` (`:145`) during the SSR. **That is a hypothesis, not a measurement —
     the poller's identity must be confirmed (`/proc/<qmi-proxy pid>/fd`) before it is claimed.**
     **AND §2.4 NARROWS IT FROM SOURCE: `wwan_port_fops_poll()` registers the caller's
     `poll_table_entry` in TWO queues, because it also calls `port->ops->tx_poll()` — which for
     this port is `rpmsg_wwan_ctrl_tx_poll()` → `rpmsg_poll()` → `qcom_smd_poll()` →
     `poll_wait(&channel->fblockread_event)` (`qcom_smd.c:998`); `CONFIG_RPMSG_QCOM_SMD=y` with BOTH
     GLINK options off, so that chain is the only one that can run. The two heads differ in
     lifetime: `port->waitqueue` **cannot** be the freed one, because `wwan_port_fops_open()`'s
     `wwan_port_get_by_minor()` → `class_find_device()` holds a device reference that only
     `wwan_port_fops_release()`'s `put_device()` drops — so the port stays allocated while
     qmi-proxy holds the fd. `channel->fblockread_event` **can** be: the `struct qcom_smd_channel`
     is freed ONLY in `qcom_smd_edge_release()` (`qcom_smd.c:1448`), which the source's own locking
     note places *after* the state worker is killed — i.e. during the SMD edge teardown, and the
     corruption fires **0.14 ms after `stopped remote processor`**. The registration window is the
     ENTIRE blocking poll (`do_sys_poll` sleeps with the entry still linked; only `poll_freewait()`
     unlinks it), so the SSR can free the head under a sleeping poller — a use-after-free that
     reports exactly as observed. **Two free experiments settle it: (1) `ls -l
     /proc/$(pidof qmi-proxy)/fd` to see whether the poller is `/dev/wwan0qmi0` (the SMD chain) or
     `/dev/rpmsg*` (`rpmsg_eptdev_poll` → `eptdev->readq`, the other same-lifetime heap head,
     already the subject of patch 819); (2) STOP qmi-proxy and re-run `echo stop` — if the
     corruption disappears §2.4 is confirmed, if it persists §2.4 is wrong.** A candidate minimal
     fix if it lands on the SMD chain is to drop `tx_poll` from `rpmsg_wwan_pops`
     (`rpmsg_wwan_ctrl.c:96`), which removes the second registration at the cost of one `EPOLLOUT`
     condition — **do not write it until experiment 1 confirms the device.**
     **Consequence: 821 is a net win but is NOT deployable as a fix** — it converts a ~50 % hang into
     a ~100 % reset, so the next work is the **wait-queue lifetime**, not more work on
     `bam_dmux_power_off()`. **Two traps this round records:** (a) **the evidence corpus had never
     seen `list_del corruption` — because it structurally could not**: every earlier boot died
     *before* the teardown completed, so **absence of a line from a corpus that cannot reach that
     line is not absence of the bug**; (b) **a liveness probe is only evidence if it was running
     during the window** — run B's sampler (1.9 s period vs a ~2 s failure) captured **nothing**, and
     its 12 `NO ANSWER` probes started ~6 s *after* the reset had begun, so **neither is a stall
     measurement**, and §2.2's "stalled CPU" claim is downgraded to "run A is consistent with a
     stall; run B is consistent with a plain reset". **Honest mechanism list after 821:** H1
     **FALSIFIED**; **H3 (a wait inside the `state_lock` section never completes) now leads** — it
     fits the intermittency and the powered-modem condition, and `rx_telemetry` exposes
     `rx_active_submitters`/`rx_active_callbacks` to settle it **without a build**; **H2 (the BAM's
     own runtime PM) is RE-OPENED as a live residual** because its only reason for retirement was
     H1; **H4 (new) — the wait-queue corruption is its own, independently reproducible defect.**
     Evidence: `evidence/168_teardown_races_powerdown/821_echostop2_teardown_completed_then_wq_corruption.txt`
     and `console_ramoops_821_echostop1.txt`.

## A twenty-fifth round — Doc 169, 2026-09-21: the AP reset is a use-after-free of `channel->fblockread_event`

124. **ROOT CAUSE OF THE POST-821 RESET, FOUND AND PROVEN — AND FIXED BY PATCH 822 (Doc 169).**
     The `list_del corruption` that Doc 168 §2.3 found 0.14 ms after a *completed* teardown is a
     **use-after-free of `channel->fblockread_event`**, the wait queue inside
     `struct qcom_smd_channel`. **The proof is arithmetic, not inference:** `pahole` against the
     build tree's DWARF gives `sizeof(struct qcom_smd_channel) = 176` (⇒ kmalloc-192) and
     `offsetof(..., fblockread_event) = 88`; a `wait_queue_head_t` is `{spinlock_t lock; struct
     list_head head;}`, so `&fblockread_event.head = channel + 96`. The crash reports
     `prev = ffff1dbc83a691e0`, and `x20 = ffff1dbc83a691d8 = prev − 8 = channel + 88` — so
     `channel = prev − 96 = ffff1dbc83a69180`, which is **192-aligned**. **Offset 96 is exactly
     where SLUB stores the freelist pointer for a kmalloc-192 object** (`ALIGN(176/2, 8)`), so
     the clobbered word **is** the freelist pointer: the channel had already been freed. The same
     arithmetic holds for Doc 168's run B (`channel = ffff6ee6c4a07f00`, `% 192 == 0`) and
     **excludes** `port->waitqueue` (`port = prev − 792` is not 1024-aligned, and
     `sizeof(struct wwan_port) = 912` ⇒ kmalloc-1024). **The mechanism:** `qmi-proxy` fd 7 →
     `/dev/wwan0qmi0` → `wwan_port_fops_poll` (`wwan_core.c:772`) registers entry 0 in
     `port->waitqueue` **and** entry 1 in `channel->fblockread_event` via
     `rpmsg_wwan_ctrl_tx_poll` → `rpmsg_poll` → `qcom_smd_poll` (`qcom_smd.c:998`); on an SSR,
     `qcom_smd_unregister_edge()` (`:1539`) removes the edge's child rpmsg devices
     (`rpdev->dev.parent = &edge->dev`, `:1097`), which runs `rpmsg_wwan_ctrl_remove` →
     `wwan_remove_port` → **`wake_up_interruptible()` — making the poller runnable — and then,
     in the SAME thread, `device_unregister(&edge->dev)` → `qcom_smd_edge_release()` →
     `kfree(channel)`, synchronously.** The poller needs a context switch before it can run
     `poll_freewait()`; the teardown never yields, **so the poller always loses — this defect is
     deterministic, NOT the ~3-5 % race of Doc 167**. **THE A/B EXPERIMENT SETTLES CAUSALITY:**
     `echo stop` with `qmi-proxy` running ⇒ corruption **1**, AP resets (`uptime` resets to
     60.65); the same run with `qmi-proxy` **SIGSTOPped — fd 7 deliberately kept open**, so
     `port->waitqueue` is provably still alive ⇒ corruption **0**, teardown completes
     (`T1`→`T9`), `port wwan0qmi0 disconnected`, `stopped remote processor`, and the AP
     **survives** (`uptime` 221→283 monotonic, `rproc` `offline`). **`echo stop` is now a ~3 s
     deterministic reproduction.** **FIX = PATCH 822**
     (`822-rpmsg-smd-drain-pollers-before-free.patch`, `drivers/rpmsg/qcom_smd.c`):
     `qcom_smd_drain_channel_pollers()` runs **between** the child-device removal (which wakes
     the poller) and the `device_unregister()` that frees the channels — it `wake_up_all()`s each
     channel's `fblockread_event` and waits (bounded, ~5 s, `dev_warn` on timeout) for
     `waitqueue_active()` to go false. **It is structural, not a timing heuristic: no poller can
     re-register during the drain**, because `wwan_remove_port()` sets `port->ops = NULL` under
     `ops_lock` and `wwan_port_fops_poll()` takes that same lock before calling `tx_poll`.
     **Also this round:** Doc 168 §5's H5
     (`cancel_delayed_work_sync(&rx_rearm_work)`, `qcom_bam_dmux.c:1591`) is **DEMOTED** — the
     same call already runs **~327×/boot** from `bam_dmux_pm_quiesce()` (`:1695`) under the same
     `state_lock` without ever hanging, and neither §2 run even exercised the `T4`→`T5` block
     (21.4 ms / 9.6 ms, both normal); `bam_dma_terminate_all()` (`bam_dma.c:725`) was read and is
     fully bounded (spinlock + register writes, no sleeps). **The ledger instrument gap is
     closed:** `ssr_ledger.sh` recorded `t0..t4` only, so it could not tell whether a natural
     fatal ran the **full blocking** `bam_dmux_power_off()` or took the idempotent early return
     at `:1568` — it now records **`t5..t9` plus `pc_state` and `rx_tearing_down`** (a `t5` that
     never appears is the signature of the early return), with the 5 historical rows migrated to
     `?` in the new columns (they are **not** zeros). **Open, and stated as such:** this does
     **not** explain why the previous boot survived **4 natural fatals** (ledger
     `543.77 / 1455.70 / 2449.65 / 3355.26`, 4 recovered coredumps) — either `qmi-proxy` is not
     parked in `ppoll()` when a natural fatal hits, or the corruption fired and the AP survived
     it and the reset has a second, slower trigger. Doc 169 §8 pre-registers the three questions
     that separate these. **Three instrument traps recorded:** (a) **`console-ramoops-0` is
     STALE after a non-crashing run** — it still holds the *previous* crash verbatim, and it
     nearly produced a false "1 corruption" for the control run (same `prev`, same PID 4836, same
     uptime 398 — **always cross-check the uptime and PID inside the record**); (b)
     **`console-ramoops` and `dmesg` have different filters** — at `console_loglevel=6`,
     `dev_info` never reaches the console, so `stopped remote processor` and
     `port wwan0qmi0 disconnected` appear in `dmesg` but are absent from `console-ramoops`; (c)
     **`rx_tearing_down = 1` is NOT a stall** — with `pc_state = 0` it is the normal
     "modem power-collapsed, ring released" state (`bam_dmux_power_off` sets it,
     `bam_dmux_power_on` clears it), and it nearly produced a false positive at the start of this
     round. Evidence: `evidence/169_smd_poll_use_after_free/`.

## A twenty-seventh round — Doc 170 (continued), 2026-09-21: patch 823 is DEPLOYED, and the reproduction it was to be scored against does not reproduce

126. **PATCH 823 IS BUILT, DEPLOYED AND FUNCTIONALLY CLEAN — AND ITS DISASSEMBLY DIFF IS 18 LINES IN
     ONE FUNCTION (Doc 170 §8.6, §8.7).** The no-flash fix is now **on the device**:
     `/lib/modules/6.12.94/rpmsg_wwan_ctrl.ko` = **`6553acd4eb83fcf032c1cf0821f35f4d`**, with the
     unpatched baseline preserved at `/overlay/modbackup/rpmsg_wwan_ctrl.ko.pre823` (`cd46d8f4…`) and
     `bash scratch/deploy823.sh --revert` to undo it. `sysupgrade` was **not** used, so `/overlay` is
     untouched.

     **A kernel-only build does not produce a deployable module.** `./build.sh kernel` leaves the
     `.ko` **unstripped** (56112 B); OpenWrt strips kernel modules in the **packaging** step. The rule
     was found by reading the build system — `rules.mk:383-390` → `scripts/strip-kmod.sh`
     (`objcopy -x -G __this_module --strip-unneeded -R .comment -R .note.gnu.build-id`) with
     `NO_RENAME=1` because `CONFIG_KERNEL_KALLSYMS=y`. `scratch/mkko823.sh` reproduces it **and
     validates itself by SIZE against the shipped baseline**: input `1457ad79…` 56112 B → output
     `6553acd4…` **6808 B** vs shipped `cd46d8f4…` **6808 B** — a size match, i.e. *this is the recipe
     the build uses*. Without that check the recipe is a guess.

     **Never verify a stripped module by symbol label.** `strip-kmod.sh` passes `objcopy -x`, which
     discards **local** symbols, and `rpmsg_wwan_ctrl_tx_poll` is `static` — so the stripped object has
     no label for it **and neither does the shipped baseline**. A label-based grep therefore reports a
     **false negative** (mine did, first attempt). Anchor on the `rpmsg_poll` relocation: baseline
     `30: mov x20, x2` / `3c: mov x2, x20` (save and pass the caller's table) vs 823
     `34: add x2, sp, #0x20` / `40: stp xzr, xzr, [sp,#32]` (a stack table `{0,0}`). A full
     `objdump -d` diff of baseline vs patched is **18 lines and every one is inside this single
     function** — nothing else in the module changed.

     **Non-regression is the part that matters** (823 changes what `poll()` reports, so a fix that
     removes the UAF and breaks `qmi-proxy` would look *clean* in a corruption count and be
     worthless). Measured after the deploy: module `6553acd4…`; `qmi-proxy` pid 5166 with **1** wwan
     fd, `fd/7 -> /dev/wwan0qmi0`, `wchan=do_sys_poll`; `wwan0` UP `10.104.99.100/29` + a global v6
     address; default route `via 10.104.99.101 dev wwan0`; **ping 8.8.8.8 4/4, 0 % loss, 42.2/64.3/
     81.8 ms**; `nslookup openwrt.org 8.8.8.8` resolves. And the targeted scenario —
     `echo stop` × 3 (`scratch/qa.sh 3 post823`) — gives **3 PASS / 0 FAIL / 0 SKIP**: every run
     survived, `0 list_del corruption`, the teardown `T9` count stepped **1 → 2 → 3**, and the modem
     was **restored** each time (`rproc=running`, `qmi-proxy` re-opened the port). **Read that
     honestly: 3/3 is NOT evidence 823 fixed anything** — the unpatched control was also 0/2 on the
     same trigger, and two rates of zero cannot be told apart. What it establishes is
     **non-regression on the exact path the fix touches**.

127. **THE CONTROL DID NOT REPRODUCE ⇒ Doc 169 §5's "DETERMINISTIC" CLAIM IS FALSIFIED AND §7's
     PRE-REGISTRATION IS WITHDRAWN (Doc 170 §8.1, §8.2).** Doc 169 argued the poller *always* loses:
     the wake (`wwan_remove_port`) and the free (`kfree(channel)`) are in the same thread with no
     yield. **The code reading is right; the conclusion is not.** Two controls — the second on a
     fresh boot with the precondition *explicitly forced* (`port wwan0qmi0 attached 1 /
     disconnected 0`) — both gave **0 corruption and a surviving AP**. The measured window is
     **at most 2.07 ms**:

     ```
     [ 129.213099] wwan wwan0: port wwan0qmi0 disconnected   <- the poller's wake
     [ 129.215173] remoteproc: remoteproc0: stopped remote processor  <- kfree already done
     ```

     On a 4-CPU system the woken poller **usually wins**. **The missing step in Doc 169's reasoning:**
     a *runnable* task on another CPU can reach `poll_freewait()` before the freeing thread reaches
     `kfree()` — the two are only ordered if the poller must run on the same CPU, and nothing
     establishes that. **Both of Doc 169's positives were taken with the ms-period `hp3.sh` sampler
     armed** (heavy load) — the leading, still-**untested** discriminator. Note the device also
     carries heavy instrumentation of its own (5× `q6trace_soak.sh`, 3× `beacon.sh`, `ModemManager`,
     `collectd`) and it *still* did not reproduce. **Consequence: a control that is 0/2 cannot score a
     fix** — five clean runs after deploying would be indistinguishable from five clean runs without
     it — so §7 is withdrawn and **the fix stands on mechanism, not on a run count**. The mechanism
     argument is stronger than a run count in one specific way: **it does not depend on the race rate**
     (`poll_wait()` is a no-op when `_qproc == NULL`, so no entry is ever linked).

128. **ONE BOOT WITH 823 HUNG AT t=73.93 s — RESOLVED: the known ~3–5 % SSR-path hang, NOT a 823
     regression (Doc 170 §8.8).** *Title corrected after the round closed: the cause was subsequently
     identified by measuring the base rate on a known-good run — see the RESOLUTION paragraph below.*
     The boot immediately after the module swap reached 73.93 s and the console **stopped**;
     the device rebooted. `console-ramoops-0` ends with **no panic, no oops, no Call trace, no
     `fatal error`, and no `list_del corruption`** — the console simply stops, which is what an AP
     hang looks like here:

     ```
     [72.752419] wcn36xx: ERROR hal_delete_sta_self response failed err=7
     [73.452171] qcom-q6v5-mss: timeout waiting for ssctl service
     [73.454181 .. 73.550436] bam_dmux SSR teardown T0..T9   <- ALL PRESENT, clean
     [73.932201] bam_dmux: modem pc-ack timeout during resume
     ```

     **Read out of the tree we run, not inferred.** `qcom_sysmon.c:555` is reached **only when
     `crashed == false`** (`if (crashed) return;` precedes it) ⇒ this was a **GRACEFUL `rproc_stop()`**
     — the `rproc_shutdown()` path, **not** crash recovery — and the `HZ/2` = 500 ms wait places
     `sysmon_stop`'s start at ≈72.95 s. `qcom_bam_dmux.c:2070` is `bam_dmux_runtime_resume()`'s 250 ms
     wait for the modem's power-collapse ACK (our own instrumentation). **This is NOT the Doc 169
     UAF** — that one reports `list_del corruption` inside the poller; here the teardown is **clean and
     complete** and the hang is in the power-collapse/resume handshake afterwards. It is the Doc 159
     family, but with **no `fatal error received` anywhere**, so it was not a modem fatal.
     **It did not recur:** the very next boot, **also running 823**, has none of these messages
     (`dmesg | grep -nE "ssctl|pc-ack|SSR teardown|fatal error|list_del"` → empty) and was healthy
     past 173 s. So **1 boot in 2**. "823 cannot call `rproc_shutdown`" is an argument from the
     patch's text, not a measurement — and this project was burned once this round by inferring a
     mechanism from a pattern — so the clean discriminator is a controlled boot with the unpatched
     module checking for the same sequence.

     **RESOLUTION (same round, after the `qa.sh` runs).** The boot that ran `qa.sh 3 post823`
     supplies the base rate the verdict above was missing: its own three `echo stop` runs each drove
     the **same** `ssctl → teardown → pc-ack` sequence and **recovered every time**, and a **natural
     fatal also recovered** (`t0..t9`, 0 corruption, coredump, data working). So that sequence is one
     **this device runs and survives routinely** — the t=73.93 s boot was the **known intermittent
     ~3–5 % SSR-path hang** (Doc 167), not a 823 regression. **823 is substantially exonerated, not
     absolutely** — the unpatched control boot is still the clean discriminator and has not been run.
     **The generalisable lesson: `timeout waiting for ssctl service` and `modem pc-ack timeout during
     resume` are NORMAL lines on this device, not fault markers. Establish a message's BASE RATE on a
     known-good run before treating it as a fault** — a line rare in the corpus may simply be rare in
     the corpus.

     **⚠ RESOLVED LATER THE SAME ROUND — IT IS THE KNOWN INTERMITTENT SSR-PATH HANG, NOT A 823
     REGRESSION.** The boot that ran `qa.sh 3 post823` gave the base rate this verdict was missing.
     Its full modem timeline: `211.046 timeout waiting for ssctl service` → stopped → `229.872
     powering up` → `251.143` stopped → `269.886` up → `291.053` stopped → `309.720` up → `381.485
     fatal error received: a2_power.c:1189` → recovered → `382.912` up. **The three stops are my own
     `echo stop` runs** (qa.sh pre-uptimes 207.30 / 245.89 / 285.95), and **all three ran the same
     sequence as this hang and all three recovered.** Two consequences: (a) **`timeout waiting for
     ssctl service` is the NORMAL signature of a graceful `rproc_stop()` on this device**, not an
     anomaly — it is `sysmon_stop()`'s `HZ/2` wait (`qcom_sysmon.c:555`), and it appears on only some
     stops because the wait returns immediately once `sysmon_start` has completed `ssctl_comp`, so
     **do not use its presence/absence to count graceful stops**; (b) **`modem pc-ack timeout during
     resume` also occurs normally and normally SELF-HEALS** — here at 381.900 the very next lines are
     patch 814's RX watchdog repairing it (`pc_state wait timeout` → `channels not initialized after
     resume` → `RX watchdog: modem awake (pc line asserted) but no channels, rebuilding` → data
     works). **The exact line that was the last thing this hang printed is a line the device prints
     and survives** — what failed was the recovery that normally follows it. **So §8.8 is the
     pre-existing intermittent AP hang on the SSR path** (Doc 167's ~1 in 20-40 SSRs, 3-5 %), and
     **823 is SUBSTANTIALLY EXONERATED, not absolutely** (1 hang in 4 graceful stops on this boot
     plus 1 on the previous; the §8.8 hang was on a *graceful* stop and the self-heal observed here
     was on a *fatal* recovery). **The trap this created is worth more than the verdict:** both lines
     were read as fault markers *because they are rare in the corpus* — and the corpus is dominated
     by boots that died before reaching them. That is the mirror image of Doc 168 §5's trap: **a line
     rare in the corpus may simply be rare in the corpus, not rare in the kernel. Establish a
     message's BASE RATE on a known-good run before calling it a fault** — one `dmesg | grep -n` did it.

     **AND A SECOND NATURAL-FATAL DATA POINT WITH 823 DEPLOYED, on the real production trigger.**
     The fatal at AP 381.485 (`a2_power.c:1189`) ran with `qmi-proxy` parked in `do_sys_poll`
     throughout: ledger row `389.95,1,a2_power.c:1189,4,4,4,4,4,4,4,4,4,4,4,4,0,1,14` with **`t0..t9`
     all present**, **0 `list_del corruption`**, coredump captured (85 398 475 B, 14 total), **AP
     survived**, modem recovered, `wwan0` UP, **ping 3/3**. Honest caveat: the natural-fatal control
     (§4) also gave 0 *without* 823, so this is **non-regression on the production path**, not
     discrimination. Free periodicity sample: modem up at 309.719860, fatal at 381.484577 ⇒ **71.76 s
     of modem uptime**, consistent with Doc 162's non-clock taxonomy for `a2_power.c:1189` (68.5-941.3 s).

     **Two confounders found while chasing it, both new to the corpus.** (a) **The image
     power-cycles the modem behind your back every 10 s**: `/usr/sbin/qcom-carrier-autocfg`
     (563 lines, `while … sleep 10`) drives `mmcli -m M --set-power-state-low` /
     `--set-power-state-on` / `-e` (its `reset_baseband_cache()`), `--simple-disconnect`,
     `--delete-bearer` and `--3gpp-set-initial-eps-bearer-settings`, and can **`reboot` the device
     itself** (lines ~529, ~537) on an MBN change. A 10 s QMI power-cycle loop is a live confounder
     for **any** modem-state experiment on this image. (b) The device was carrying 5 instances of my
     own `q6trace_soak.sh` plus `beacon.sh`/`ssr_ledger.sh`/`coredump_watch.sh`/`ModemManager`/
     `collectd`/`qcom-time-daemon`. **Also ruled out as the trigger:** nothing under `/overlay` writes
     remoteproc state — the scripts only *read* it (checked with the **device's** `grep`; `rg` is not
     installed there, so a host-side `rg` over ssh returns nothing and reads as "not found").

129. **TWO OPERATIONAL FINDINGS THAT CHANGE HOW THE DEVICE IS RECOVERED.** (a) **The dongle runs its
     own WiFi AP**, and it is the **only management path that survives a dead USB gadget**:
     `phy0-ap0` is UP, bridged into `br-lan` (192.168.8.1/24), SSID **`OpenWrt`, encryption `none`**,
     from `wcn36xx` on `a204000.remoteproc`. **So the `OpenWrt` SSID visible from the build host is
     this device, not a neighbour — and do NOT infer "no WiFi" from `CONFIG_QCOM_WCNSS_PIL` being
     unset** (the target sets `CONFIG_QCOM_WCNSS_CTRL=y` and leaves PIL unset, yet WiFi works, so the
     firmware path is not the PIL one). The failure mode in item 128's neighbourhood kills the *USB
     gadget* while possibly leaving the AP alive, so **attach over WiFi before concluding the AP is
     dead** — it is the only way to tell "the AP hung" from "the gadget died". (b) **"Gone from the
     USB bus" and "attached but failing to enumerate" are different observations, and only the HOST
     kernel log distinguishes them.** `lsusb` showed only root hubs, which reads as "the dongle is
     gone"; the host log said it had **re-attached at FULL speed** (working enumeration is high-speed)
     and failed setup — `device descriptor read/64, error -71`, `attempt power cycle`, `Device not
     responding to setup address`, `unable to enumerate USB device` — i.e. a **gadget/PHY that came
     back broken**, with the hub driver's own power-cycle already failed. **Recovery = a PHYSICAL COLD
     REPLUG (unplug, wait ~15 s, replug); a quick replug may not clear a stuck DWC3 PHY.** And
     **`pstore` was empty afterwards ⇒ the AP did not panic** (empty pstore is not conclusive here, but
     it does exclude a panic-with-record).

130. **A TOOLING TRAP THAT NEARLY BECAME A FINDING: `bash grep` IS NOT grep (Doc 170 §8.9).** The
     project's memory said *"the Grep tool is broken — use `bash grep`"*. **That advice is wrong and
     dangerous.** `bash grep -rn PATTERN DIR` runs **bash with `/usr/bin/grep` as the script to
     interpret** and dies with `cannot execute binary file`; with the usual `2>/dev/null` the failure
     is **invisible and indistinguishable from "no matches"**. This produced a near-miss: I
     "established" that `timeout waiting for ssctl service` and `pc-ack timeout during resume` **do not
     exist anywhere in linux-6.12.94** — they are at **`qcom_sysmon.c:555`** and
     **`qcom_bam_dmux.c:2070`**. Working on the build host (aarch64, Asahi Fedora): **`rg`** (ripgrep
     15.2.0) and **plain `grep`** (GNU 3.12, aliased). **Never `bash grep`.** Two general rules: **(a)
     a search that returns nothing is evidence only if the search tool RAN** — verify a negative with a
     positive control in the same tree (`rg -c "bam_dmux" qcom_bam_dmux.c` → 315); **(b) `2>/dev/null`
     on a search is a correctness hazard, not tidiness.** And `rg` is not installed on the device
     (busybox only), so a host-side `rg` over ssh finds nothing: a tool that is *absent* and a tool
     that is *broken* both look like "no matches".

131. **`sysupgrade` PRESERVES `/overlay` UNLESS `-n` IS PASSED** — `platform.sh:264` formats
     `rootfs_data` only when `UPGRADE_BACKUP` is empty. Critical here because `/lib/firmware`, the
     deployed modules and the coredumps all live on the overlay: **`-n` would destroy them.** And
     `rmmod` of a wwan module is impossible while `qmi-proxy` holds `/dev/wwan0qmi0`, so a module swap
     is copy + reboot, and a `/overlay/modbackup/` copy is what makes `--revert` possible.

132. **⚠ AN AP RESET SURVIVED 823 — 823 IS NOT A COMPLETE FIX (Doc 170 §8.10). — ⚠ THIS ITEM'S
     VERDICT IS WITHDRAWN: SEE ITEM 137 (Doc 170 §8.14). THE RESET WAS MY OWN PHYSICAL USB-HUB
     INSTALLATION, NOT AN AP HANG. 823 IS SUBSTANTIALLY REHABILITATED.** The item is retained as
     written because the *record* is accurate and the *reasoning error* is the lesson; read item 137
     before citing it. The host-side
     liveness watcher caught the **same boot** that produced item 127's 3/3 result resetting. Its
     complete fatal record: ledger rows 14/15/16 (`389.95 a2_power.c:1189`, `502.08 a2_power.c:1189`,
     `896.52 a2_power.c:2949`) all with **`t0..t9` present**, and coredumps `devcd1/2/3` at 382.41 /
     496.01 / 888.08 s — **three natural fatals ran their full SSR and all recovered**, and then the AP
     reset at **uptime ~1061 s, ~165 s after the third teardown had already completed**. **pstore is
     EMPTY** while ramoops is registered and working ⇒ **not a trappable kernel fault** (a `list_del
     corruption` panics and would have left a `dmesg-ramoops-0`) ⇒ consistent with a **global stall →
     PMIC watchdog reset**, the Doc 159 picture. **No fourth coredump and no ledger row 17** — both
     instruments are userspace and die with the AP, so **"no fatal" and "a fatal that hung before
     either could record it" are indistinguishable**: **the cause is UNDETERMINED, recorded rather
     than explained.** The `qmi-proxy` poller **was** the only wwan-fd holder
     (`/proc/4619/fd/7 -> /dev/wwan0qmi0`), so 823's target vector was present and the reset happened
     anyway — consistent with the reset being the **other** (coredump-reclaim) mechanism, but not
     proof. **Confounders ruled out:** `/overlay` at **48 %** (not full), and `qcom-carrier-autocfg`
     (see item 133). **Effect on the verdict: unchanged and now stated concretely — 823 is
     substantially but not absolutely exonerated, and it is not a complete fix.** Do **not** expect
     patch 822 to close this class either: 822 fixes the same UAF, and this reset points at the
     separate coredump-reclaim hang.

133. **TWO INSTRUMENT LESSONS, BOTH ABOUT THE INSTRUMENT RATHER THAN THE BUG (Doc 170 §8.10).**
     **(a) Read the BRANCH CONDITIONS of an image script before blaming it.** Memory said
     `/usr/sbin/qcom-carrier-autocfg` "power-cycles the modem through `mmcli` EVERY 10 s" — **that was
     wrong.** Its `while … sleep 10` body **only polls** in steady state; `mmcli --set-power-state-low`
     / `-on` (lines 268/270) runs only on an operator change, the initial boot, or a radio-cache
     mismatch, and `reboot` (lines 523/538) only on an MBN change. **"It runs every 10 s" is not "it
     acts every 10 s."** (b) **A liveness instrument must not erase its own record.** `/overlay/beacon.sh`
     did `: > beacon_a.txt` / `: > beacon_b.txt` at **every boot**, destroying exactly the pre-reset
     tail it exists to capture — the last advanced uptime and the A(sync)-vs-B(nosync) divergence that
     separate "the FS/writeback path wedged" from "a global stall". **The one event it was built to
     characterise was the one it could not.** Fixed and deployed: it now appends a
     `BOOT <uptime>s pid=$$ host=…` marker (`scratch/beacon.sh`; copy at
     `evidence/170_two_fixes_for_the_smd_poll_uaf/N_beacon_sh_reset_durable.sh`). **The next reset WILL
     be characterisable — do not patch anything until that record exists.**

134. **THE RESET MECHANISM IS NAMED: the PM8916 PON WATCHDOG, ACTIVE, 30 s — AND THE RATE IS NOT RARE
     (Doc 170 §8.11). — ⚠ THE *MECHANISM* STANDS; THE *RATE* IS WITHDRAWN — SEE ITEM 137.** An AP-side
     uptime decrease proves the dongle restarted, **not** that it restarted itself: at least 6 of the 19
     outages are host-caused or manual (§8.14), and the durations are **bimodal** — the `~30 s stall +
     ~15–40 s boot` decomposition fits only the 16 short ones, not the **1641 s** and **926 s** events.
     The item is retained for the mechanism and for the record. The AP does not panic; it **stops**.
     Read from the device:
     `/sys/class/watchdog/watchdog0/{state,timeout,identity}` → **`active` / `30` /
     `QCOM PM8916 PON WDT`**. Something in userspace (procd) must kick it every ≤30 s while the kernel
     is healthy, so **an empty pstore plus a reboot is exactly what a ≥30 s global stall looks like** —
     and it explains the two-component shape of every outage: the 13–61 s unreachable windows are
     `~30 s stall + ~15–40 s boot`. **The rate is the uncomfortable part:** filtering the watcher's 19
     unreachable events to those where the uptime actually *decreased* gives **15 real reboots in ~5 h**
     (uptimes before reset: 5294 · 2427 · 2275 · 2250 · 1812 · 1614 · 1139 · 1042 · 897 · 395 · 307 ·
     220 · 187 · 164 · 126). A modem on the ~902 s idle timer gives ~20 SSRs in 5 h, so that is nearer
     **1 in 1–3 than the 1 in 20–40 Doc 167 measured**. **Two readings, neither yet a fact:** **14 of
     the 15 are pre-823** (the only post-823 one is the 17:04 reset of item 132), and the pre-823 window
     was *also* the heaviest manual-activity window (echo-stops, deploys, soaks, the UAF A/B), so it is
     not a clean steady-state rate. **The current boot has since survived TWO consecutive
     `lte_ml1_sleepmgr_stm.c:4054` fatals at AP 919.62 and 1825.87 — a 906 s gap, i.e. the idle timer —
     with `t0..t9` present and 0 corruption**, so 823 may well have lowered the rate; n is too small to
     claim it. **The tempting tidy model — "the AP hangs on the SSR of the n-th idle-timer fatal", i.e.
     reboots at multiples of ~902 s — was TESTED AND REJECTED: only 2 of 15 fall within ±20 s of a
     multiple of 902, and 6 of 15 (uptimes 395 · 307 · 220 · 187 · 164 · 126) cannot be one at all
     because they are below 500 s.** Three of those six (13:31:03 @897 s → 13:33:00 @126 s → 13:36:00
     @164 s) are a **boot loop** — three reboots in five minutes — as is the 16:40–16:46 cluster, so
     **counting boot loops as steady-state hangs inflates the rate.** The reset is therefore driven by
     the **variable activity-correlated fatals** (`a2_power.c:1189`/`:2949`, which fire anywhere in
     68.5–941.3 s) or by something that is not a fatal at all. **A clean-looking mechanism that fails
     its own check is the one worth recording — this is that case.**

135. **THE INSTRUMENT THAT WAS MISSING — a per-boot rolling kernel log (Doc 170 §8.12).** §8.11 leaves
     "what does the kernel print in the last seconds before it stops?", and **nothing on the device
     answered it**: `pstore` empty, `console-ramoops` **absent** (not merely stale), and the soak's own
     `/dev/kmsg` stream file `/overlay/q6trace.log` has an **mtime of the boot** — its log path is dead
     (its sibling `q6trace.csv` *is* current, so only the log path died). Deployed
     `scratch/dmesg_roll.sh`, autostarted from `/etc/rc.local`, with three deliberate choices that are
     each a mistake this project already made once: **(a) `dmesg` (the ring buffer), NOT `/dev/kmsg`** —
     a `/dev/kmsg` reader sleeps on `log_wait` when the ring is quiet, and a leaked
     `cat /dev/kmsg | …` pipeline already cost this project a permanent fd; **(b) ONE FILE PER BOOT**
     (`/overlay/dmesg_roll_<boot_id>.txt`) — a single rolling file is overwritten seconds after the next
     boot, destroying exactly the pre-hang tail it exists to capture, **the same self-erasing-instrument
     bug the beacon had**; **(c) `sync` every write**, since nothing runs after the stall. Verified live
     (18673 B, rewritten every 5 s, `sh -n /etc/rc.local` → OK). **With the beacon, the next stall will
     be CHARACTERISED, not merely counted:** the beacon gives the *when* and the A(sync)-vs-B(nosync)
     split, the rolling log gives the *what*.

136. **EXPERIMENT DEPLOYED: the coredump capture sits INSIDE the SSR recovery, so it is turned OFF —
     and the corpus claim "there is NO per-device `disabled`" is WRONG (Doc 170 §8.13).** Reading the
     recovery path gave a mechanism-backed, no-flash, reversible intervention:
     `rproc_boot_recovery()` (`remoteproc_core.c:1792-1817`) runs
     `rproc_stop()` → **`rproc->ops->coredump(rproc)`** → `request_firmware()` → `rproc_start()` — the
     dump is **between stop and start**. For the MSS that op is the **DEFAULT `rproc_coredump`**
     (`remoteproc_core.c:2426` installs it when the driver supplies none, and `q6v5_ops`
     (`qcom_q6v5_mss.c:1728`) has **no `.coredump` member**), and it **returns immediately** when the
     per-device attribute is `disabled` (`remoteproc_coredump.c:249`). **`remoteproc_sysfs.c:73-127`
     does accept `"disabled"` — and note there are TWO different sysfs locations, which the corpus does
     not distinguish:** the note *"there is NO per-device `disabled`"* is about the **devcoredump**
     device (`/sys/class/devcoredump/devcdN/`, only `data`) and **remains true**; the **remoteproc**
     device has its **own** attribute `/sys/class/remoteproc/remoteproc0/coredump` that does accept it.
     The remoteproc one controls whether a dump is **produced**; the class-level
     `/sys/class/devcoredump/disabled` is a **write-once global lockdown** (never write it).
     **Why removing it matters:** `qcom_q6v5_dump_segment()` is the **second caller of
     `q6v5_mba_load()`** (Doc 165) — a whole extra MBA power-up mid-recovery; Doc 165
     measured the 85 MB synchronous copy at **1.064 s on this path**; and it writes **85 MB per fatal
     to `/overlay`** (18 dumps = 1.5 GB of 3.2 GB). **RESULT — fatal #3, the first with the capture
     off, is a clean within-boot A/B and it corrected my own correction.** Fatal #3 fired at AP
     **2718.436833 s** (predicted from the previous gap within **0.01 s**), recovered fully
     (`t0..t9`, `rproc=running`), produced **no coredump** (count frozen at 18), and its recovery
     differs from fatal #2's (capture **on**) exactly as the source says it should:
     **2 half-cycle pairs (`01-09,10-23,01-09,10-23`) → 1**, **`port failed halt` present → ABSENT**,
     and `SSR before shutdown`→`MBA booted` **0.8316 s → 0.1253 s (0.706 s faster)**; boot-wide,
     **3 fatals / 4 `MBA booted` / 2 `port failed halt`**. So the dump's second MBA load, the 85 MB
     copy, the second reclaim **and the `port failed halt` window all genuinely disappear**.
     **The reasoning I had to retract:** I had argued (from the call graph) that because
     `q6v5_mba_reclaim()` is *also* called by `q6v5_stop()` (`:1672`) unconditionally,
     `port failed halt` must still print. It does not — `q6v5proc_halt_axi_port()` opens with
     **`if (!ret && val) return;`** (`:961-964`, "already idle, say nothing"), and the port is idle in
     the stop path but **live** in the dump path, which has just re-loaded the MBA. **A call site being
     reached is not evidence that its error branch executes.** **What still survives:** the restart's
     own `q6v5_mba_load()`, whose untraced tail (trace 23 → `MBA booted` = **39.2 ms**) is
     `q6v5_rmb_mba_wait()` — Doc 165's 86.2 %-of-the-window call, still bounded (5000 ms). So a
     same-duration window of the same kind **remains**, relocated from `port failed halt`→`MBA booted`
     to `stopped remote processor`→`MBA booted`. **Deployed** via `/etc/rc.local`, gated on
     `/overlay/coredump_ENABLE` so it survives a reboot and re-enables with one `touch`. Verified:
     attribute `disabled`, watcher procs 0, `sh -n` OK. **PRE-REGISTERED BAR: 0 AP reboots across the
     next 20 fatals** (~5 h at the idle timer) — justified because the pre-823 rate was order 1 reboot
     per 1–3 SSRs, and even a true 1-in-4 rate gives ~99.7 % chance of seeing one in 20.
     **Progress: 10 of 20 scored, AP survived.** **Fatal #14 (AP `10210.922046`) is
     `a2_power.c:1189` — NOT the clock** — and recovers in **`0.119210 s`**. It arrived **21 s after**
     the clock predicted (`10189.8 s`) and with a **different signature**, because **the test traffic I
     generated to probe the data stall substituted the activity-correlated fatal for the idle clock**
     (E1 confirmed — a soak that generates traffic is not measuring the idle clock).
     **Fatal #13 (sleepmgr, AP `9286.716877`) recovers in
     `0.125060 s`** — predicted at AP ≈ `9288.32 s` from fatal #12's modem boot and **missed by 1.60 s**,
     so the modem was **still on the clock three fatals after the cascade ended**; and it attracted **no**
     `pc-ack timeout`, so it joins §8.21.1's control group.
     **Fatal #12 (sleepmgr, AP `8385.263928`) recovers in
     `0.122951 s`** — predicted at AP ≈ `8386.9 s` from fatal #11's modem boot and **missed by 1.64 s**,
     so the modem is **still on the clock two fatals after the cascade ended**. Fatal #11 (sleepmgr, AP `7483.839750`) recovers in
     **`0.119912 s`** and fatal #9 (AP `6397.771058`, `a2_power.c:1189`) in **`0.127579 s`**, both with
     **patch 814's rebuild firing and succeeding**
     (`successfully reinitialized BAM channels and rings` → all 8 `CMD_OPEN`s), and fatal #7 (AP
     `6110.407029 s`, sleepmgr) in **`0.119170 s`**, so the nine capture-OFF recoveries span
     **`0.119170–0.130237 s`** against **`0.824902–0.831559 s`** for the two with the capture **on** —
     two **non-overlapping** populations **~7× apart**, on nine signatures in nine recoveries.
     ⚠ **The window now spans TWO regimes** — the **idle 902 s clock** (`lte_ml1_sleepmgr_stm.c:4054`)
     and the **cascade** (`a2_power.c` / `a2_task.c`) — so say which regime a quoted bar covers.

     The fourth sample (fatal #6, AP `5208.721361 s`,
     `lte_ml1_sleepmgr_stm.c:4054`) recovers in **`0.127373 s`**. The third sample
     (fatal #5, AP `4304.991306 s`, `a2_power.c:1189`) reproduces it at `0.124531 s`. The second sample
     (fatal #4, AP `3477.969305 s`, `a2_power.c:2949`) reproduced fatal #3 to within 5 ms, so the A/B
     no longer rests on one window, and the three capture-off recoveries now span
     **`0.125291–0.130237 s` (spread 5.0 ms)** against **`0.824902–0.831559 s` (spread 6.7 ms)** for the
     two with the capture **on** — two **non-overlapping** populations **6.6× apart**:
     **1 half-cycle pair, no `port failed halt`, 1 `MBA booted`, `SSR before shutdown`→`MBA booted`
     `0.130237 s`** against `0.824902`/`0.831559 s` for the two with the capture **on**; boot-wide
     **6 fatals / 7 `MBA booted` (cold boot + one per recovery) / 2 `port failed halt`** (exactly the
     two capture-ON recoveries), trace `01-09` ×8 / `10-23` ×9, `cmd_open` 56 = 8 channels × 7 modem
     boots, `coredump_live` **frozen at 18** — and
     the ledger's **independent** `coredumps` column (a separate 10 s poller, not the driver) reads
     **17, 18, 18, 18, 18, 18** — fatal #1 took it 16→17, fatal #2 17→18, **#3, #4, #5 and #6 produced
     no dump at all**. Fatal #4's interval was **759.53 s** and fatal #5's **827.02 s**, both off the 902.4 s
     clock — which is *expected*, not an anomaly: Doc 162's taxonomy puts `a2_power` at 68.5–941.3 s
     and explicitly **not** on the clock; fatal #6 is the clock signature at **902.286373 s of modem
     uptime, 19.4 ms from Doc 162's 902.267 s**. **⚠ And score this on `SSR before shutdown`→`MBA booted`,
     NOT on fatal→`is now up`:** fatal #5's full fatal→up is **1.443682 s**, as long as the capture-ON
     recoveries, even though the interval the coredump actually sits in is 0.124531 s — anyone scoring
     on fatal→up would read that removal as a failure. **Stated
     cost:** no coredump is captured in the window, so a fatal there cannot be decoded via its
     ERR_FATAL descriptor (Doc 163) — though its *signature* still lands in `dmesg_roll`.

137. **⚠ THE HOST KERNEL LOG REFRAMES THE AP-RESET RATE: §8.10's "reset that survived 823" WAS MY OWN
     HUB INSTALLATION, AND 7 OF 19 OUTAGES ARE NOT CLEAN AP-HANG DATA POINTS (Doc 170 §8.14).** §8.10 blamed
     the AP for a 17:03 reset; §8.11 used the watcher's reboot count to claim the rate is ~1 in 1–3
     SSRs. **Neither checked the host's `journalctl -k`, which covers the whole day and can see the
     other side.** It shows error in **both** directions:

     **The 17:03 reset was mine.** `17:03:07` the dongle disconnects from port `1-1`; `17:03:13` a
     **`USB2.0 HUB` (214b:7250) appears on `1-1` for the first time**, with `4 ports detected`;
     `17:03:41` the dongle re-appears at **`1-1.2`** behind it. **A hub cannot be added to a port
     without first removing the device on it** — this is a physical intervention, and the **29 s
     outage §8.10 measured *is* the unplug/replug window**. §8.10's three observations (empty pstore,
     no 4th coredump, no ledger row) are **exactly what a power cycle produces**; §8.10's reading
     ("a hung fatal and no fatal are indistinguishable") was true of the *instruments* but blind to
     the third option — **no fatal at all, because the AP was never running the fatal path.**
     **⇒ §8.10's verdict on 823 is WITHDRAWN and 823 is substantially rehabilitated.** (The hub *was*
     recorded in memory as a device fact — but never connected to the reset it caused.)

     **The 16:40–16:46 "boot loop" is entangled with a HOST USB-controller storm — DIRECTION NOT
     ESTABLISHED.** The watcher saw uptime `2427 → 46 → 38 → 79` (three AP reboots in seven minutes);
     the host shows the **xHCI platform driver removed and re-probed 24 times on Sep 21, 18 of them in
     the 16h hour**, each cycle deregistering and rebuilding both root hubs, with the dongle appearing
     and disappearing every ~30–60 s. **But the ordering is MIXED** — at `10:31:46`, `15:06:03`,
     `16:40:30`, `16:41:14`, `16:42:11` the **dongle disconnects first** and the controller goes ~1 s
     later, while at `15:59:28` the controller is removed with **no** preceding device disconnect.
     **My first draft called these "host-caused"; that is stronger than the evidence and is retracted
     to "entangled".** A plausible upstream cause correlated with an outage is still not a direction.

     **The outage durations are BIMODAL and §8.11's model fits only one mode.** §8.11 explained "the
     13–61 s windows" as `~30 s stall + ~15–40 s boot`; the full list is **16 short** (5–61 s) plus
     **two long: 926 s** (15:44:42 — preceded by `error -71` ×6 and `attempt power cycle`, i.e. the
     gadget came back **broken**) **and 1641 s** (14:39:38, 27 min). **A 1641 s outage cannot be a 30 s
     watchdog stall plus a boot — and the watchdog tells us what it IS:** the PON watchdog is kicked by
     **`procd`, a userspace process**, so a stopped kernel is reset in 30 s while a broken *network
     path* with a running CPU is **never** reset. **An unreachability longer than ~30 s + a boot is
     therefore not a hang — the AP was alive**, making the 1641 s and 926 s events **network-path
     failures**, a failure mode §8.11 never modelled and one that bears directly on the "data stall"
     reports. (It also exposes a watcher error: it attributes the reboot to the *start* of a long
     outage, but the uptime decrease is a reboot at the **end** — the 14:39:38 outage ends with a
     re-enumeration at 15:06:48 and an AP uptime of 34 s at 15:07:02, i.e. the AP rebooted at
     ~15:06:28, so between 14:39 and 15:06 it was **up but unreachable for 27 minutes**.)

     **Classified: 7 of the 19 outages are not clean AP-hang data points** — **1 certainly not an AP
     hang** (17:03:24, my hub install), **2 certainly not hangs by duration** (1641 s and 926 s: the
     PON watchdog is kicked by `procd`, so a *stopped* kernel resets in 30 s while a broken *network
     path* never does ⇒ these are **network-path failures with a live AP**), and **4 entangled with the
     host controller storm** (16:40:47, 16:42:29, 16:44:14, 16:45:54). The remaining **12** are the only
     AP-hang candidates, but **"no host trigger" is not proof** — the host log only records what the
     *host* saw. Conversely the host saw **39** dongle re-enumerations against ~19 AP uptime-decreases
     (a clean re-enumeration at 14:39 with **no** AP reboot), so **re-enumeration ≠ AP reboot** and the
     two instruments disagree in both directions.
     *(A mechanism I deliberately did NOT use: the host SMC logs `Elec Cause 0x200000` / `Not charging`
     25 s before the 16:40 episode — but it fires **8 times across the day** against 47 `Elec Cause 0x0`
     and does not align with the other controller episodes, so it is most likely the laptop's own
     charger/battery state. Recorded as a non-finding.)*
     **⇒ THE RULE: an AP-side uptime decrease proves the dongle restarted, NOT that it restarted
     ITSELF.** The host log is now a **required companion instrument** for every reboot attributed to
     the AP — and §8.13's bar is amended so a reboot counts as a failure **only if the host log shows
     no host cause** (the dongle sits behind a hub added at 17:03 and the host's xHCI flapped at 16h,
     so a **false failure** was a real risk). Evidence:
     `evidence/170_two_fixes_for_the_smd_poll_uaf/U_host_usb_log_reframes_the_ap_reset_rate.txt`.

138. **★ THE FIRST AP-SIDE OBSERVABLE THAT PRECEDES A MODEM FATAL: every `a2_power.c:2949` is preceded
     74–86 ms earlier by the patch-808 "lost edge" PC resync — 3 of 3, in three different boots
     (Doc 170 §8.15).** Found by accident in fatal #4's own window: one extra line, `bam-dmux … RX
     watchdog: PC line asserted while pc_state=0 (lost edge), resyncing` at `3477.894835`, **74.470 ms**
     before `fatal error received: a2_power.c:2949` at `3477.969305` — and `pc_resync_count` went
     **0 → 1** for the boot. Searching the preserved logs found the same pair in two earlier boots:

     | boot | resync | fatal | gap |
     |---|---|---|---|
     | P | `714.454106` | `714.539903` | **85.797 ms** |
     | Q | `886.743235` | `886.817190` | **73.955 ms** |
     | Y | `3477.894835` | `3477.969305` | **74.470 ms** |

     mean **78.074 ms**, spread **11.842 ms**, the two closest **0.515 ms** apart.

     **⚠ AND IT IS NOT SUFFICIENT — 3 of 15, AND THE PRE-REGISTERED P2 IS WHAT MEASURED THAT.** Pooling
     every boot with a surviving log (**P 1/1, Q 1/1, R 1/0, S 4/0, Y 8/1**): **`a2_power.c:2949` ⇒
     resync is 3/3** while **resync ⇒ `a2_power.c:2949` is 3/15** — a **5×** gap, and the second is the
     one that decides whether a fix is possible. *Caveat that cuts the other way:* the P/Q/R resync
     counts come from the partial `q6trace.log`, so a missing resync would make the denominator
     **larger** — 3/15 is a floor. **A lag this tight feels like a cause; the denominator is what keeps
     it honest.** **AND IT DOES NOT GENERALISE — the tight lag is specific to `a2_power.c:2949`:** fatal
     #5 (`a2_power.c:1189`, AP `4304.991306 s`) has **no resync within 100 ms** — its nearest is
     **80.477042 s earlier**. So: sleepmgr never (0/3), `a2_power.c:2949` 3/3, `a2_power.c:1189` 0/1.
     **AND THE RATE IS NOT STATIONARY:** this boot had **1** resync in its first 3478 s, then **5 in
     145 s** starting at `4079.094133` — with no AP-side change (no patch, no reboot, no manual action,
     `coredump` still `disabled`, `rproc=running`), `pc_quiesce_ms` down to **1669 ms**. **AND P3 WAS
     SCORED AND ITS SIGNATURE PREDICTION FAILED:** it named a `lte_ml1_sleepmgr_stm.c:4054` at ~AP
     4380 s; the fatal was an `a2_power.c:1189` at **4304.99 s**, and the storm **continued 47.5 s past
     the fatal** (resyncs at `4344.057823`, `4352.497742`), so the storm **brackets** the fatal rather
     than preceding it. **★ THE REFRAME THAT CAME OUT OF IT — THE STORM IS BIDIRECTIONAL PC-HANDSHAKE
     FAILURE.** Lining up both messages for the whole boot: `RX watchdog: … (lost edge), resyncing` =
     the **modem** asserted the PC line and the **AP** missed the edge; `modem pc-ack timeout during
     resume` = the **AP** voted and the **modem** did not ACK within 250 ms. **Both directions fail
     during the storm**, and the fatal lands inside it — so the interesting variable may not be "did a
     resync fire 74 ms before the fatal" but **"is the PC handshake in a failing regime at all"**, a
     state that persists for **minutes** and is measurable from two **existing** counters
     (`pc_resync_count`, `pc_timeout_count`) with no new instrumentation. **D2/D3 should be scored on
     the storm, not on a single resync.** A counter that moves with it, recorded and unexplained:
     `pm_suspend_attempts − pm_suspend_completions` was a constant **7** through the clean part of the
     boot and is **10** after the storm. **§8.15's "cheapest next step" is done and the answer is
     NEGATIVE:** `pc_timeout_count` counts **the AP waiting for the modem** (a 250 ms
     `wait_for_completion_timeout(&dmux->pc_ack_completion)` and a 1000 ms `wait_event_timeout(dmux->pc_wait)`
     inside `bam_dmux_runtime_resume()`, patch 808) — the *mirror* of the "modem waits for the AP's ACK"
     reading, so **it cannot discriminate that reading; do not design around it**. And the post-fatal
     `pc-ack timeout` lines are **recovery artefacts, not precursors**: #1 → +0.431112 s,
     #2 → +0.581419 s, #3 → +0.435022 s, #4 → **none** (unexplained, and the tempting "shorter recovery"
     story is recorded as speculation).

     **Why an AP-side action is plausible at all:** the resync is **patch-808 code** (not upstream) and
     **two of its four actions are modem-visible** — `bam_dmux_pm_restart()` (powers the BAM down and
     up) and `bam_dmux_pc_ack()` (the SMSM power-collapse ACK). Three readings fit and the data does
     **not** choose between them: **(A)** the resync is a poison pill (gap should be a modem constant);
     **(B)** common cause — the modem's PC state machine had already desynchronised (resync is a
     symptom); **(C)** the AP's *missed edge* is the cause and the modem's ACK timeout is what asserts
     (gap = `modem_timeout − edge_delay`, so it **should** vary). **The 100 ms watchdog period makes C
     testable**: the AP's detection delay is uniform on [0,100] ms, so three gaps inside 11.8 ms is a
     ~4 % coincidence — **that weakly favours A and is far too weak to act on**, so it is recorded, not
     concluded.

     **THE DISCRIMINATOR NEEDS NO KERNEL FLASH: `qcom_bam_dmux` is a loadable module**
     (`/lib/modules/6.12.94/qcom_bam_dmux.ko`, 241232 B, `lsmod` refcount 0). **D1** = log either side
     of each action (zero behaviour change); **D2** = make the resync **skip `bam_dmux_pc_ack()`** and
     count the signature per resync; **D3** = skip `bam_dmux_pm_restart()` instead. D2/D3 are **probes,
     not fixes** (the modem then never gets its ACK) and must be reverted. **They must NOT run inside
     the open §8.13 window** — they change which fatals occur. The passive half is pre-registered:
     **P1** the next `a2_power.c:2949` will have a resync within 100 ms in front of it; **P2** keep
     counting resyncs with *no* fatal after them. **FALSIFIER:** an `a2_power.c:2949` with **no** resync
     in the preceding 100 ms breaks the association entirely.

     **★★ §8.19–§8.21 CLOSED OUT THE ROUND, AND TWO OF THE THREE RESULTS ARE NEGATIVES — BOTH OBTAINED
     BY LEAVING AN INSTRUMENT RUNNING PAST ITS FIRST RESULT.** **(a) The modem LEFT the 902 s clock at
     fatal #7 and CASCADED:** `#8 a2_task.c:3179` **120.763 s**, `#9 a2_power.c:1189` **166.602 s**,
     `#10 a2_task.c:3179` **182.729 s** — `a2_task.c:3179` being a **third `a2_*` file**, and repeating.
     **(b) The RPM's own external log was captured across it** (`rpmring -t -n 500000 -i 4`; tick
     measured **19.1997 MHz**) and shows a **13.264 s stall in which the RPM wrote exactly ONE 9-record
     vote cycle** while **its own timestamp advanced 13.243 s** — agreeing with the wall gap to **21 ms**,
     so the stall is **UPSTREAM of the RPM, i.e. in the MODEM**: the live signature of the RE's
     `rpm.sync` (0xc08bebd0) claim. The AP saw the same event as a lost edge + two `pc-ack` timeouts +
     **`rtt = 911.398 ms`**, the largest of the S2 run. **(c) THE CASCADE IS BOUNDED: fatal #11 returned
     to the clock** at **903.332309 s** after #10 — **predicted from #10's modem boot and missed by
     1.04 s** — so the "902 s clock" is really **"902 s of MODEM UPTIME"** and a cascade is a bounded
     departure the next reload restarts. **(d) ⚠ MY OWN CONFOUND, WITHDRAWN:** I stopped the traffic at
     uptime 6600.88 and called the cascade "traffic-dependent", but **the cascade's last fatal was #10 at
     6580.507441 — 20.4 s BEFORE the intervention** (trap 19).
     **(e) ★ THE DISCRIMINATING QUESTION IS ANSWERED *NO* (§8.20):** the capture ran across fatals
     **#9, #10 AND #11** (AP 6343.30→7794.35 s, 423 704 records) and the **nearest quiet period before
     each is 1.268 / 1.145 / 3.141 s — the normal cadence for that era**; the only three gaps ≥ 8 s
     (**13.264 / 8.510 / 10.880 s**) all **recover** and **none** is immediately before a fatal. So the
     RPM stall is an **independent, recurring, self-recovering modem-side event (~1 per 480 s), NOT the
     fatal's mechanism** — a **latency** defect, like the §8.16 250 ms handshake. **Two traps fell out of
     it:** the RPM's log is **BURSTY** (9 rec/1.267 s in one era, 63 rec/3.1 s in another; **50.5 % of
     the capture sits inside a ≥ 0.5 s gap**), so "quiet" must be judged against the **LOCAL** cadence —
     my first pass used the median of *all* gaps (~0.03 s, since consecutive `# NEW` blocks are ~4 ms
     apart) and scored every ≥ 500 ms gap at **30–400×**, i.e. it would have "confirmed" whatever I was
     looking for; and the capture has **49 ring overruns losing 22 664 records**, so **a gap whose counter
     delta is ≥ 8192 B is a ring turnover, not a silence.** AP handshake events *do* cluster in RPM gaps
     but only **modestly** (enrichment **1.55× at ≥ 0.5 s**, **4.44× at ≥ 8 s** on 6 events in 3 gaps —
     suggestive, not established), and **the direction is NOT established.**
     **(f) ★ THE STORM IS BOUNDED TOO (§8.21):** the episode is **AP 3477.894835 → 7417.446456 =
     3939.55 s**, after which `pc_resync_count` **81** stayed **FROZEN** while
     `pm_suspend_attempts` climbed **924 → 1176 (+252)** with `runtime_status: active` — **2042.95 s of
     silence across ~252 suspends, INCLUDING across THREE full modem reloads (fatals #11, #12 AND #13).**
     ⚠ **`pc_timeout_count` did NOT stay frozen — it moved 78 → 79, and §8.21.1 shows the single
     increment is fatal #12's own SSR-window `pc-ack timeout`, a THIRD class of handshake event that is
     neither the storm nor the silent data-plane loss. So "both counters froze" was wrong as written;
     only the resync counter froze, and the storm's silence still stands.**
     **This REFUTES "the storm is AP-runtime-PM-gated" as §8.15.4 stated it:** freezing both counters
     under traffic is *equally* consistent with "traffic suppresses suspends" and "traffic suppresses
     resyncs", and the AP now suspends 126 more times with **zero** resyncs. **The churn is necessary
     but not sufficient** — the storm needs a **third, unidentified condition**. The strongest surviving
     candidate is a specific *pattern* of suspends (the natural next instrument is to log **which**
     suspends are followed by a resync, not how many); **"the modem's own state" is now the most
     disfavoured**, because the storm was **present across SEVEN consecutive modem reloads (#4–#10)** and
     then **absent across the next three** — so a modem boot carries the state neither way. §8.15.4's
     intact parts stand: traffic does suppress the storm in practice, and **idle-avoidance does not
     suppress the fatals.** ⚠ A 2042.95 s silence is a **lull** until a further onset is observed — the
     sampler and `logroll.sh` are both still running.
     **(h) ★ NEW — A THIRD CLASS OF `pc-ack timeout`, AND IT IS INEVITABLE (§8.21.1):** **6 of the 12
     fatals** produce a `pc-ack timeout` **inside their own SSR down-window** (`stopped remote
     processor` → `is now up`), where the modem is **provably stopped** and **cannot ack a
     power-collapse vote** — so the 250 ms wait **must** expire. Δ from the fatal is a tight **150 ms**
     band (`+0.431112 / +0.581419 / +0.435022 / +0.445107 / +0.439598 / +0.440271`); measured from
     `MBA booted` the sign **changes**, so **the anchor is the fatal, not the MBA boot.** This
     **corrects §8.13.6**, which mixed those two reference points and **omitted fatal #9**. The other
     six fatals are the control (no resume attempted in their down-window). **Consequences:** a
     `pc_timeout_count` increment is **not by itself storm evidence**; and the §8.16 fix (Android's
     **2000 ms** vs the 250 ms here) gains a **falsifiable target** — the mpss load is only **0.556 s**,
     so a 2000 ms window would cover this class, and `pc_timeout_count` should then stop rising by ~1
     per fatal. **It does NOT contaminate the §8.13 A/B bar** (the timeout lands *before* `MBA booted`
     for #1/#2 and *after* it for the rest).
     **(i) ★★★ "DOES DATA STILL STALL?" — YES, BUT IT IS THE USERSPACE BEARER REBUILD, NOT THE DEFECT WE
     ROOT-CAUSED (§8.22).** **The first-packet-after-idle stall NO LONGER REPRODUCES:** 3 rounds of
     idle-then-one-ping against a device at `suspended / pc_state=0` gave **2/2 first-attempt deliveries
     from the collapsed state** (round 3 took the direct path) against the recorded **5/5 failures**
     pre-812, with `tx_defer_queued − tx_defer_submitted` **0** and `tx_defer_wiped_live` **0**
     throughout; plus **20/20 sustained pings, 0 % loss** and **DNS after 20 s idle**. **What actually
     stalls is the bearer:** `netifd`'s own log holds **one `ifdown`/`ifup` pair per fatal** with a
     **15–26 s** outage (#9 **15 s**, #10 **18 s**, #11 **18 s**, #12 **26 s**, #13 **15 s**) — against a
     **kernel SSR recovery of 0.12 s**, i.e. **100–200× longer**. The rebuild is a **ModemManager
     re-probe + modem-object recreation**, and the first probe **fails**
     (`could not recreate modem: Unsupported device: at least a QMI port is required`) before the retry
     succeeds. **All 10 watchdog stall events fall inside the cascade window and none in the ~3260 s
     since** (⚠ confound: the watchdog only fires when `TX > 0`). **The lever has moved from the
     baseband to ModemManager + netifd; do not chase the modem for this symptom.** ⚠ Measured in one
     boot, five fatals.
     **(j) ★ CONFIRMED FROM A FIELD OBSERVATION — the bearer is always NEW (§8.22.1).** The user
     reported that after a stall ModemManager tried the **new** bearer, not the old one. Measured: the
     boot references bearers **`16 18 20 22 24 26 28 30`** (index advances by 2 per recovery, never
     repeats) and creates modem objects **`modem6 … modem14`** — 9 in one boot — while `mmcli` shows a
     clean steady state (`bearers.length : 1`, only `Modem/14` live). So it is **not a leak; it is full
     re-provisioning**, and `/lib/netifd/proto/modemmanager.sh` is **stateless** — setup requires
     **exactly one** bearer (`INVALID_BEARER_LIST` if not; 0 occurrences) and teardown just
     `--simple-disconnect`s when the path is gone (`couldn't load bearer path: disconnecting anyway`).
     **There is no path that resumes an old bearer** — after an SSR the modem's WDS session is gone, so
     the old bearer is correctly invalid. **Phase breakdown (two rebuilds, same shape):** ~5–6 s
     ModemManager re-probe/modem-object creation **including a FAILED first probe**
     (`at least a QMI port is required`), ~5 s **SIM re-read**, ~4–5 s enable/register/simple-connect/
     bearer creation, ~1–2 s netifd address+route. **Two of those four stages exist only because the
     object is new**, and the **failed first probe is the most concrete avoidable cost** — the QMI port
     returns ~1 s after the fatal but the object is not created until ~5 s in, a retry-backoff/port-
     reappearance mismatch worth ~2–4 s per fatal.
     **(g) ⚠ CORRECTIONS:** S2 is **58 samples / 9 losses** (not 56/7), the **silent-loss class is n = 3**
     (not 2 — 6130.44 was misclassified as "ping crossed the ifup": fatal #7's SSR completed at
     `6111.927`, the ports attached by `6112.87`, and the ping fired **17.6 s later** then timed out for
     the full 5.03 s), and the §8.16 counter accounting is now **EXACT at 100 %** — `pc_resync_count`
     **81** = **81** `lost edge` lines; `pc_timeout_count` **78** = **76** `pc-ack timeout` + **2**
     `pc_state wait timeout` (the earlier 51 + 2 = 53 at 96.2 % was **ring incompleteness at scoring
     time**), **extended after fatal #12 to `79 = 77 + 2`, still exact (§8.21.1)**. **A new, unexplained
     failure mode: a real ~5 s data-plane timeout with NO dmesg correlate at all — no lost edge, no
     `pc-ack timeout`, no fatal, no SSR — n = 3.**

     **What it does NOT claim:** the resync does not (yet) cause the fatal, and it explains **neither**
     the `lte_ml1_sleepmgr_stm.c:4054` **nor** the `a2_power.c:1189` fatals — **neither has ever been
     seen with a resync in front of it**.

     **★ AND THEN THE STORM KEPT RUNNING, WHICH DEMOTES THE WHOLE PRECURSOR STORY (Doc 170 §8.15.3).**
     Eight-plus minutes after fatal #5 the storm was still going — `pc_resync_count` **15 → 18 → 20**
     across uptimes 4710/4814/4853, i.e. **1 resync per 36.3 s sustained** — with **fatals still 5**,
     `rproc=running`, and `ping -c 4 -I wwan0 8.8.8.8` **4/4, 0 % loss**. **15 resyncs, ONE of which
     preceded a fatal.** So the storm is a **REGIME, not an event**, and reading **(A)** ("the resync is
     a poison pill") is now strongly **disfavoured**; **(B)/(C)** — a failing handshake regime of which
     the resyncs are symptoms — fit better. **The two failure directions account for the boot exactly,
     with no residue:** 15 lost edges = 1 fatal-preceding + 5 paired + 9 unpaired; 14 `pc-ack timeout`s
     = 3 post-fatal recovery artefacts + 5 paired + 6 unpaired. **And the five pairings sit ~184 ms
     apart (166.797–210.076 ms, mean 184.080) — but the mechanism does NOT close arithmetically, which
     is itself the finding:** the only site printing `modem pc-ack timeout during resume` emits it
     **exactly ~250 ms after that resume's own vote**, so a line at **T+184 ms** means the vote was at
     **T−66 ms — *before* the resync** — and the resync's own `complete_all(&dmux->pc_ack_completion)`
     **must** then have satisfied that waiter. So either a later `reinit_completion()` (there are exactly
     two: `bam_dmux_power_on()` `:178`, `bam_dmux_runtime_resume()` `:1421`) undoes it, or the coupling
     runs through the **modem's ACK timing**. **⇒ This REDEFINES D1: it should log the ordering of
     `reinit_completion()` / `complete_all()` / the pc_ack IRQ, not "either side of the two
     modem-visible actions".** Also: **no external trigger** — the AP's `dmesg` has **zero lines** in the
     589 s before the onset, and the host's `journalctl -k` is **silent** (last entry `17:34:12` vs an
     onset of ~`18:11:20`, with a **positive control** proving the query works) — so the onset is
     AP/modem-internal, and §8.14's lesson was applied *before* blaming anything. **The runtime-PM rate
     is unchanged** across the onset (0.132/s boot average vs 0.127–0.130/s during), so this is *not*
     "more churn" — it is the same churn failing its handshake. **Correction:**
     `pm_suspend_attempts − pm_suspend_completions` (7 → 10 → 11) is **accounting, not a defect** —
     `pm_suspend_start_ns` is cleared by `bam_dmux_runtime_resume()` (`:1417`) **and by the resync
     handler** (`:797`), so a suspend pre-empted by either can never increment the completion counter;
     the deficit is the count of suspend/resume races (11 of 623 = 1.8 %), **withdrawn as an anomaly**.
     **★ AND THE STORM IS AP-RUNTIME-PM-GATED — S1 IS DONE AND P5 IS CONFIRMED, AND THE SAME RUN
     REFUTES THE MITIGATION FOR THE FATALS.** S1 = 600 consecutive 1 Hz pings with both counters
     sampled once a second, and it is a **clean A/B/A inside ONE run** (the arms are traffic vs no
     traffic, not any change I made): pre-S1 the storm ran at **1 resync per 36.3 s** with
     **0.132 suspends/s** and `pc_state=0` in 6 of 10 samples; during **364.34 s of 1 Hz traffic
     `pc_resync_count` AND `pm_suspend_attempts` were BOTH FROZEN** — **0 resyncs against 10.0
     expected** (Poisson p = 4.5 × 10⁻⁵), 0 suspends against 48.1 expected, `pc_state=1` in all 365
     samples, **364/364 pings, 0 % loss**; after the traffic stopped (fatal #6's SSR killed the ping
     at `seq=363` with `sendto: Network unreachable` and **no summary**) the storm **returned at the
     pre-S1 rate** (1 per 42.0 s, 0.126 suspends/s). **So the storm, the DATA STALL and the PC-handshake
     failures are ONE defect family — the idle→active transition — and an idle-avoidance policy is a
     candidate mitigation for the STORM.** ⚠ **But fatal #6 fired at AP `5208.721361 s` INSIDE that
     suppression window, with the modem never suspended, on the clock (`lte_ml1_sleepmgr_stm.c:4054`,
     `902.286373 s` of modem uptime, 19.4 ms from Doc 162's 902.267 s) — so the FATAL is NOT
     PM-gated, and idle-avoidance does not suppress it. That is now measured, not inferred, and it is
     the cleanest separation the corpus has between the storm and the fatal.** ⚠ **Trap worth keeping:
     the tail of `/overlay/s1.track` reads `resync=27 … susp=673` and looks like a failed hypothesis —
     it is the post-traffic CONTROL arm.** Score from the instrument's health, not its tail. **Next:
     S2** (`/overlay/s2.sh`, written and syntax-checked, **running**) — one ping every 15 s × 80,
     recording each ping's RTT and the counters either side; ⚠ **P6's predicate was WRONG: the first
     data shows the one slow ping (`551.417 ms` against a ~41–43 ms baseline) is the one whose
     `pc_timeout_count` advanced, while its `resync` delta was 0 — score S2 on the TIMEOUT delta**;
     S2 also regression-tests patch 812 at exactly the idle period Doc 156 measured as broken.

     **⚠⚠ AND A SECOND SELF-CORRECTION THIS ROUND (Doc 170 §8.15.5, and its §9 trap 16): §8.15.3 §5's
     "NO EXTERNAL TRIGGER" IS WITHDRAWN TO "OPEN".** Its two negatives are both true — `dmesg` really
     is empty for the 589 s before the onset and the host's `journalctl -k` really is silent — **and
     neither is sufficient, because a userspace action can change the modem's power behaviour without
     producing a kernel log line.** The device runs **`/usr/sbin/modem-bearer-watchdog`** (485 lines,
     `uci show modem-watchdog`: `general.enabled=1`, `check_interval=10`, `recovery.ssr_enabled=1`)
     which **enforces `power/control=auto` and `autosuspend_delay_ms=1000` on the bam-dmux device
     every 10 s via sysfs, with NO dmesg line and NO log line** — and `autosuspend_delay_ms = 1000`
     is *exactly* the knob that controls the storm's mechanism. It can also do
     `qmicli --wds-go-dormant` (RRC reset, `:271`), `ubus call network.interface.modem down/up`
     (which **would change the bearer IP**, `:299`/`:351`) and **`echo stop > …/remoteproc0/state` — a
     full remoteproc SSR** (`:403-416`). **And the onset is now unrecoverable: `logread` is a
     ~15-minute ring buffer whose earliest line was `12:43:29` against an onset at ~`12:27`.** The
     missing instrument is deployed — **`/overlay/logroll.sh`**, running and in `/etc/rc.local` (with
     a pidfile guard and a backup), writing `/overlay/logroll_<boot_id>.log` containing `logread -f`
     plus a 1 Hz `PMKNOB` sample of `power/control`/`autosuspend_delay_ms`/`runtime_status` — so the
     userspace log, the kernel log and the PM state machine are on **one timeline**. Verified working;
     **it answers the NEXT onset, not this one.** *(This is §8.10's lesson repeated: grep the image's
     own automation before concluding "nothing acted".)*
     Evidence:
     `evidence/170_two_fixes_for_the_smd_poll_uaf/V_coredump_off_fatal4_n2_and_the_lost_edge_precursor.txt`.
     *(Provenance trap, recorded because it nearly inflated the sample: the P and Q samples come from
     `/overlay/q6trace.log`, an **append-across-boots** file — `grep -c "a2_power.c:2949"` returns **5**
     for **3 distinct events**. Each sample was instead cross-checked against the independent
     `ssr_ledger.csv` (**6 of 6 match**, 0–10 s noticed-lag) and the two boots separated by their own
     `n = 1,2,3` sequences and the ledger's monotonic `coredumps` column, 11→13 then 14→16.)*

## A twenty-sixth round — Doc 170, 2026-09-21: two independent fixes for the SMD poll use-after-free (module 823, kernel 822)

125. **THE UAF IS NOW FIXABLE WITHOUT A KERNEL FLASH, THE POLLER'S IDENTITY IS MEASURED, AND THE
     NATURAL-FATAL PATH IS SCORED (Doc 170).** Doc 169 left exactly one thing as a hypothesis —
     *"confirm the poller's identity (`/proc/<qmi-proxy pid>/fd`) before claiming it"* — and one
     deployment assumption. **Both are now closed.**

     **The poller, measured.** A full `/proc/*/fd` sweep gives
     `3658 qmi-proxy /proc/3658/fd/7 -> /dev/wwan0qmi0`, and it is the **only** wwan/rpmsg fd on the
     device. Its kernel stack, read live, is
     `do_sys_poll+0x390/0x4ec <- __arm64_sys_ppoll+0x88/0xe8`; Doc 169's corruption fires in
     `remove_wait_queue <- poll_freewait <- do_sys_poll <- __arm64_sys_ppoll`. **Both ends of the
     loop are the same function, observed directly** — a task inside `do_sys_poll` is by
     construction holding its `poll_table_entry` linked. (`/proc/<pid>/stack` is readable for a
     **userspace** task here; it had only been verified for kernel threads.)

     **`CONFIG_RPMSG_WWAN_CTRL=m`.** The second registration — the one that links into
     `channel->fblockread_event` — is made by `rpmsg_wwan_ctrl_tx_poll()` in a **loadable module**.
     Doc 169 assumed a kernel flash without checking the config. **PATCH 823**
     (`823-rpmsg-wwan-ctrl-no-smd-poll-registration.patch`) therefore exists and needs **no flash**:
     `rpmsg_wwan_ctrl_tx_poll()` passes `rpmsg_poll()` a `poll_table` with **`_qproc == NULL`**, and
     `poll_wait()` is a **no-op** in that case (`include/linux/poll.h:42`) while the returned mask is
     **byte-for-byte identical** (`qcom_smd_poll()` computes `EPOLLOUT` from
     `qcom_smd_get_tx_avail() > 20` **independently of `wait`**). Verified in the built object:
     `add x2, sp, #0x20` / `stp xzr, xzr, [sp,#32]` / `bl rpmsg_poll` — the caller's `poll_table` is
     discarded. **The obvious alternative was rejected after reading the callee:** deleting
     `.tx_poll` would fall through to `else if (!is_write_blocked(port)) mask |= EPOLLOUT`, and
     `is_write_blocked()` is `test_bit(WWAN_PORT_TX_OFF, &port->flags) && port->ops` — **nothing in
     `rpmsg_wwan_ctrl.c` ever calls `wwan_port_txoff()`/`txon()`** (grepped), so EPOLLOUT would
     become **unconditional** and the `> 20` flow-control signal would be silently lost. **Honest
     limitation, recorded not hidden:** dropping the registration also drops the SMD wakeup for a
     poller blocked *purely* on EPOLLOUT; entry 0 in `port->waitqueue` is still registered and is
     woken by every RX, so a request/response client self-heals, but this is a real behavioural
     change and is why 822 stays on the list.

     **PATCH 822 REVISED — the bound is now wall clock.** The first revision looped
     `for (retries = 5000; retries--; ) msleep(1)` and called it "roughly 5 seconds". **It is not a
     time bound:** `msleep(1)` can sleep a whole tick, so 5000 iterations is tens of seconds of
     extra latency **inside the SSR teardown** — the exact path Doc 168 spent a round on. It is now
     `deadline = jiffies + msecs_to_jiffies(2000)`. Verified in the object:
     `adrp x25, jiffies` / `add x21, x21, #0x258` (= **600 = `msecs_to_jiffies(2000)` at HZ=300**) /
     `cmp x1, x21`. **That disassembly also reproduces Doc 169 §4's offset arithmetic from the
     COMPILER, not from `pahole`:** `&channel->fblockread_event = channel + 0x58` (88) and
     `&...head = channel + 0x60` (96) — the two constants the whole identification rests on.

     **QUESTION B ANSWERED, FOR FREE.** A natural fatal landed at AP 1335.05 s
     (`lte_ml1_sleepmgr_stm.c:4054`); the extended ledger row is
     `1337.83,1,lte_ml1_sleepmgr_stm.c:4054,2,2,2,2,2,2,2,2,2,2,2,2,1,0,9` — **`t0..t9` all present**,
     so `bam_dmux_power_off()` ran the **full blocking teardown** and the idempotent early return at
     `qcom_bam_dmux.c:1568` was **not** taken; **0 `list_del corruption`**; a coredump was captured
     (`8 -> 9`, so `rproc_stop()` returned); the AP survived. **The `t5..t9` columns work and answer
     exactly the question they were added for.** So the UAF does **not** reproduce on the
     natural-fatal path even though that path also frees the channels — consistent with Doc 159's
     ~1-in-21, and the two paths differ in the **context** that runs the teardown (the sysfs write
     runs `rproc_stop()` without the scheduling opportunities the recovery workqueue has).
     **Hypothesis, not measurement.**

     **And a free periodicity sample:** that fatal is **900.862 s of modem uptime**
     (`1335.049762 − 434.187561`), inside Doc 164's short band (900.5–901.0 s, spread 0.46 s), with
     the signature that band is *always* paired with — **n = 12 and the pairing holds again.**

     **OPERATIONAL — read from the target's own `platform.sh`, not assumed:**
     `sysupgrade` **preserves `/overlay` unless `-n` is passed**, because the script formats
     `rootfs_data` only when `UPGRADE_BACKUP` is empty. That is the single most important detail for
     shipping 822, since `/lib/firmware`, the deployed modules and the coredumps all live there.
     **`-n` would destroy them.** Question A (does 823 remove both the corruption *and* the reset?)
     is pre-registered in Doc 170 §7 — `echo stop` × 5 with `qmi-proxy` running, control **2/2**.
     Evidence: `evidence/170_two_fixes_for_the_smd_poll_uaf/`.


## The steady-state symptom, measured (Doc 147 §5.4)

**After the link has been idle, the first packet is always lost and the retry always
works.** Reproduced **5/5**: 120 s idle, then one `ping -c 1 -W 5` → `replies=0/1 took=5s`
with `dtx=1 drx=0`; the next 3-packet ping gives `2/3` and DNS resolves. That is the
browse/DNS-after-idle hang users report, and it is exactly why `modem-bearer-watchdog`
escalates — its pre-escalation gate makes a **single** DNS attempt, and one attempt is
precisely what this defect eats.

**Fixed 2026-09-20 (Doc 147 §8):** `modem-bearer-watchdog`'s gate now retries the DNS probe
(`PROBE_ATTEMPTS=3`, 2 s apart) and requires `PROBE_FAIL_MIN=2` **consecutive** failures
before escalating. Verified: a first-packet loss is absorbed with no escalation and the
bearer stays up, while a genuinely sustained failure still escalates.

**ROOT-CAUSED AND FIXED 2026-09-21 (Doc 156).** The watchdog change above is a **workaround**; the
defect itself was in the driver. `pm_runtime_get()` is async, so the first packet after every A2
collapse takes `start_xmit`'s defer branch, and `bam_dmux_pm_restart()` destroyed it on the wake it
was waiting for. Measured **27/27** deferred packets destroyed (60 % of TX in the first minute of a
boot) and **3/3** single-packet pings lost before the fix; **patch 812** gives **5/5** delivered
after. `dtx=1` was never evidence the packet reached the wire — it is incremented on the defer branch
*before* the packet is destroyed. The watchdog's single-attempt gate is now a **safety net, not a
fix**; do not remove it yet, because Doc 156 §9's residual `start_xmit` window is still open.

Also established and not to be re-litigated:

- The modem firmware is **byte-identical** to the stock Android build (all 21 MDT
  segments hashed) — the fault is **100 % AP-side**.
- The WTR1605→UFI001B RF transplant is **not achievable by binary means** (Docs 142/143/145).
  Do not restart that line.
- `MODEM_FIRMWARE_NO_SLEEP_PATCH_GUIDE.md` is **fabricated** and now quarantined.
- The **AP-side crash is solved**. pstore held a full kernel panic proving the NULL
  `skb` dereference in `bam_dmux_send_cmd()` reached from `bam_dmux_netdev_stop`
  (i.e. `wwan0` going down). Fixed, deployed, and verified — Doc 147 §4.
- The deployed modem driver is now **reproducible from source**: patch
  `809-bam-dmux-tx-pm-ordering.patch` was added because fix hunks existed only in
  `build_dir` and would have been lost on rebuild — Doc 147 §7.
- The `wwan0` address churn (`10.32.48.33` → `10.139.191.152` → `10.89.244.25`) is **not** an
  AP-side rebuild timer: it is the modem fataling, the AP running SSR, and the carrier handing
  out a new address on re-attach. Three fatals, three rebuilds, `bearer8` — Doc 148 §4.

## Trustworthy core — safe to cite

| File | What it establishes |
| :--- | :--- |
| `Stock_Android_Live/01_ANDROID_LIVE_SESSION_FINDINGS.md` | 21-segment byte-identical firmware proof; partition/tooling ground truth |
| `Stock_Android_Live/02_DIFFERENTIAL_DIAG_ANALYSIS.md` | The modem does **not** crash at 900 s on Android; the failure is 100 % AP-side. **Conclusion sound, evidence downgraded — cite `Stock_Android_Analysis/16_15min_barrier_test_log.txt` (Android at 921 s, 0 % loss) instead; the DIAG captures are too short and DIAG dies on SSR (Doc 148 §6.4)** |
| `Modem RE/hmu05/900S_CRASH_LPR_FRAMEWORK_RE.md` | Latest firmware RE; identifies the `rpm.sync` stall; disproves the QMI-time and `common_timer.c:390` theories |
| `Modem RE/hmu05/900S_CRASH_ROOT_CAUSE_FIRMWARE_RE.md` | The Q6 power-collapse-vote root cause (only its counter semantics are superseded by the LPR doc) |
| `134_UFI001B_RF_FRONTEND_BRINGUP_SESSION_RECORD_AND_PENDING_WORK.md` | Model SOP-compliance statement; disproves Doc 133's RF claims |
| `136_HMU05_DIAG_PORT_AND_SERIAL_CONSOLE_DISCOVERY.md` | DIAG/rpmsg bridge + serial console |
| `137_HMU05_DIAG_LOG_STREAM_ENABLEMENT_AND_LOG_CONFIG_F_PROTOCOL.md` | `DIAG_LOG_CONFIG_F` wire format, empirically mapped |
| `139_BAM_DMUX_PATCH_808_ANDROID_PARITY_AUDIT_AND_FIXES.md` | Byte-level patch-808 parity audit |
| `140_BAM_DMUX_RUNTIME_PM_A2_POWER_COLLAPSE_PARITY_FIX.md` | Two runtime-PM defects proven; corrects Doc 62; reports E7 FAIL honestly |
| `142_UFI001B_B26_TRANSPLANT_RESIDENCY_ROOT_CAUSE.md` | b26 residency closed with three independent evidence lines |
| `143_UFI001B_HMU05_RF_BLOCKER_DISJOINT_TRANSCEIVER_DRIVERS.md` | Disjoint transceiver driver sets |
| `144_UFI001B_RF_VERDICT_CONFIRMED_ATTRIBUTION_CORRECTION_AND_BAM_DMUX_RTNL_OOPs.md` | No-RF confirmed; corrects Doc 141; documents the RTNL-leak oops |
| `145_RF_PORT_FEASIBILITY_WTR1605_INTO_UFI001B_VERDICT.md` | Quantitative infeasibility verdict |
| `146_HMU05_AP_SIDE_FIX_SESSION_AND_TRUST_INDEX.md` | Previous session: RTNL oops root cause + fix + verification; corpus trust index; next experiments |
| `147_HMU05_PSTORE_PANIC_CAPTURE_AND_STALL_CHARACTERISATION.md` | **The pstore kernel panic** proving the oops; fix verified in situ; stall re-characterised (not 900 s, not the RX ring); patch-809 reproducibility fix |
| `148_HMU05_FATAL_PERIODICITY_AND_SIGNATURE_TAXONOMY.md` | **The fatal is periodic within a boot**; scopes retraction #1; explains the address churn; downgrades the DIAG-based "no FATAL" evidence. **Its period figure (903.674 s) and its ten-signature framing are corrected by Doc 149** |
| `149_HMU05_RPM_FIRMWARE_AND_LIVE_RPM_LOG.md` | **The period is ~902.3 s of *modem* uptime** (not 903.674 s AP), reconciling with the RE's `400 × 2.256 s`; the E1 A/B/A result (traffic *suppresses* the deterministic fatal; the pre-registered prediction **failed**); the assert string is a mutable global and **must not classify a fatal**; `rpm.bin` is a disassemblable ARM ELF. **Its §5.2/§5.3 log-reading method and census are RETRACTED by Doc 150**; its VMIN/XOSD and "AP never votes VMIN" findings stand; **its §5.5 SPM framing and §7-item-3 one-line DT patch are WITHDRAWN by Doc 151 §6** (SAW nodes are `reserved` under PSCI; CPR never probes) |
| `150_HMU05_RPM_LOG_IS_LIVE_AND_ITS_CLIENT_IS_THE_MODEM.md` | **The RPM log is now a trustworthy, ordered, wall-clock-stamped stream** (`rpmring`: mmap, ~500 µs/sample; `+0x38` is the ring's byte write counter, verified twice); the RPM timestamp is **19.2 MHz measured to ±0.017 %**; the log carries a **client id** — client 1 = **MSS** (its sequence counter restarts at the modem SSR, once in 1152 transactions); **the RPM is alive through the fatal and the SSR** (214 records in the fatal window, then 1286 rec/s), so the "RPM-wedged" arm of the `rpm.sync` hypothesis is **not supported**; answers Doc 149 §7 exp. 1. **Its §13's VMIN/SPM patch plan is withdrawn by Doc 151 §6; its SIGBUS boundary-straddle bug (and two-`memcpy` fix) are documented in its uncommitted addendum** |
| `151_HMU05_F10_REPLICATION_CLIENT0_AND_SPM_CORRECTION.md` | **Fatal #10 replicated** (331.7 s / 94 252 records / 2271 bursts): client 0 appears in a 1.308 s window at the fatal and **nowhere else** in 330.4 s — the "fatal-only" signature holds at 2/2 fatals; **client 0's sequence counter is contiguous across the SSR (0x135→0x14c) while client 1's restarts, so client 0 is NOT the modem**; the AP is the leading client-0 candidate (`ldoa`/`smpa` = the AP's `qcom,rpm-pm8916-regulators` names; the AP acts only during remoteproc crash recovery — the mechanism for "only at the fatal"); **a new AP-side hang** in the `bam_dmux` "SSR before shutdown" teardown path (`echo stop > .../state` → hard hang, no panic, watchdog reset); **the SPM/CPR plan withdrawn** (SAW `status="reserved"` under PSCI; `qcom_spm_find_any_cpu()` returns false; CPR never probes; the `-3` is benign) |
| `152_COREDUMP_WATCHER_REGRESSION_AND_CAPTURE_FIX.md` | **The modem-coredump watcher could never capture** — a `[ -s "$d/data" ]` guard on a `bin_attribute` with `.size = 0`, and a release that wrote a nonexistent per-device `disabled` (the real one is a **global write-once lockdown**); the per-device release is a **write to `data`**. Fatals #11 and #12 were lost; Δ(#11→#12) = **903.674516 s** is a fresh period sample. Records the corrected watcher; the mapping `dump_va = elf_va − 0x39800000` re-verified; the ERR_FATAL record's structure at ELF `0xC35B1280` and its word **B (11 per period)**; and that the dumps hold modem memory, **not SMEM**. **Its fix is CONFIRMED by Doc 153; its §8 item 3(b) (`devmem` live read) is WITHDRAWN by Doc 153 §5; its "5-min window elapsed" explanation of fatal #13 is superseded by Doc 153 §4** |
| `153_FATAL14_CAPTURED_DEVMEM_IMPOSSIBLE_AND_WATCHER_AUTOSTART.md` | **Doc 152's watcher fix confirmed by capturing fatals #14 AND #15** (each 85 398 475 B, md5 verified) — the `test -s` guard was the whole cause. **The coredump is created only after `rproc_stop()` returns**, so a hanging fatal (fatal #13) is *structurally uncapturable* — the dump is never created. **`/dev/mem` cannot read the mpss region by any method** (read()→EFAULT, mmap()→SIGBUS; source-verified), so live `devmem` observation is impossible and a `nomap`-capable kernel module is the next instrument. Establishes **`dump_va == AP physical` for mpss**; the watcher now autostarts from `/etc/rc.local` (and busybox `start-stop-daemon -S` is **not idempotent** for a script); **the deployed baseband is verified byte-identical to the stock HMU05 dump** (21 modem + 9 WCNSS segments); the `rpm` LPR `+0x18` counter spans **375–1064** (not monotonic either way) and word B **advances 11 per period at 4/4 within-boot but does not reset across a reboot**. **Its §5 inference that "a `nomap`-capable kernel module is the next instrument" is CORRECTED by Doc 158: `ioremap` succeeds but every ACCESS aborts, because mpss is TrustZone-assigned — the coredump is the only instrument** |
| `154_BAM_DMUX_TX_WAKEUP_NULL_DEREF_AND_FAILED_MODEM_RESTART.md` | **A second, distinct `bam_dmux` NULL deref** — `bam_dmux_tx_wakeup_work+0xc8` → `bam_dmux_skb_dma_submit_tx+0x2c`, faulting load `ldr w22, [x2, #0x70]` with `x2 = NULL` (= `skb_dma->skb`; `skb->len` at 0x70 BTF-verified). Different function/caller/offset from Doc 147's oops. Separately: **the modem failed to restart after fatal #15** (`port failed halt`, stall at `loading mpss`, `state = offline`, reboot required); and the Doc 153 rc.local autostart is verified at a real boot. **Its §5.3 mechanism ("neither `power_off` nor `pm_restart` takes `state_lock`") is WRONG and is corrected by Doc 155 — the sweep is always under `state_lock`; the hole is that `start_xmit` sets the deferred bit without it.** Its §4 decode is correct and stands |
| `155_TX_SWEEP_RACE_PATCH_810_AND_DOC154_CORRECTION.md` | **The TX-sweep race, corrected and FIXED.** Corrects Doc 154 §5.3: the sweep (`bam_dmux_free_skbs()` from `power_off()`/`pm_restart()`) is **always** under `state_lock` (`pc_irq` `:1701`, rx-watchdog resync `:1191`, teardown `:2059`, powerup `:2213`, remove `:2292`), so the `:654` comment is true. The real hole is the **producer**: `bam_dmux_netdev_start_xmit()` is atomic context and sets `tx_deferred_skb` at `:599` **without** the lock, so it can re-arm a bit for a slot a concurrent sweep already freed. **Patch 810** (`msm89xx/patches/810-bam-dmux-tx-sweep-race.patch`) makes the consumers tolerant: NULL-guard `bam_dmux_skb_dma_map()`/`bam_dmux_skb_dma_submit_tx()`, **drop** stale bits in the `tx_wakeup_work` loop, and stop `start_xmit`'s `drop:` from double-freeing/double-putting. **`cancel_work_sync(&tx_wakeup_work)` there would deadlock.** Verified in the crashing object (md5 `3b693188…`): `pc` `b30−b04 = 0x2c`, `lr` `13b0−12e8 = 0xc8`, faulting word `b9407056`. Soak caveat: **1 Hz traffic keeps the modem awake and does not exercise the path** — idle-then-burst gives 23 collapse cycles and 0 oops |
| `156_DEFERRED_TX_PACKET_LOSS_ROOT_CAUSE_AND_FIX.md` | **ROOT CAUSE of the steady-state data stall, and its fix.** `pm_runtime_get()` is `__pm_runtime_resume(dev, RPM_GET_PUT \| RPM_ASYNC)` (`include/linux/pm_runtime.h:400-403`) — **asynchronous**; it queues the resume and returns `-EINPROGRESS` rather than blocking. So the **first packet after every A2 power collapse** takes `start_xmit`'s defer branch (`qcom_bam_dmux.c:667`, `active <= 0`), deterministically. The wake then runs `pc_irq` → `pc_state = true` → **`bam_dmux_pm_restart()`**, which did `atomic_long_set(&dmux->tx_deferred_skb, 0)` + `bam_dmux_free_skbs(dmux->tx_skbs, DMA_TO_DEVICE)` — destroying the skb of the packet it was supposed to drain, and making the drain at `pc_irq:1878` **dead code**. `start_xmit` had already returned `NETDEV_TX_OK`, so nothing retransmits it (TCP survives by RTO; a single ICMP/DNS/UDP packet does not — this is the `dtx=1 drx=0` stall). **Measured before the fix: 27 packets deferred, 27 destroyed with a live skb, 0 delivered — 60 % of all TX in the first minute of a boot, with no oops/fatal/SSR and `tx_sweep_guard_hits: 0`.** A single ping to a collapsed modem: **3/3 lost**; the same ping to an awake modem: delivered. **Patch 811** adds the counters (`tx_defer_queued/submitted/preserved/wiped/wiped_live`, `tx_submit_ok`, `tx_complete`); **patch 812** adds `bam_dmux_free_skbs_except()` and makes `pm_restart()` **preserve** deferred slots (the `dma_map_single()` mapping is for the *device*, not the channel, so it survives the rebuild) — honouring `runtime_suspend()`'s existing **Guard 1** (`:1933`). `tx_next_skb` must **not** be reset while slots are preserved. **Verified: `queued 8 / preserved 8 / submitted 8 / wiped_live 0`, and 5/5 pings delivered including 4/4 from `rs=suspended pc=0`.** Does **not** explain the ~900 s fatal or the failed restart. Residual `start_xmit` window remains open and counted |
| `157_SSR_POWERUP_GIVEUP_AND_PATCH814.md` | **A third AP-side defect: the SSR powerup work gave up permanently.** `bam_dmux_ssr_powerup_work_func()` polled the modem's A2 power-control line for **3.2 s** and, when it was not asserted, did a bare `return` — **no retry, no reschedule**. A fatal error forces a **full modem firmware reload**, and this boot's modem took **~6.4 s** to become ready (teardown 1405.70 s → `wwan0at0 attached` 1413.57 s), so the poll expired **2.65 s before the modem finished booting** and `dmux->rx == dmux->tx == NULL` from then on. **`bam_dmux_runtime_resume()` then returned 0 with the channels missing**, so `bam_dmux_netdev_open()` (`:545`, the *synchronous* `pm_runtime_resume_and_get()`) proceeded into `bam_dmux_send_cmd()`, which refused with `-EAGAIN` on `pc_state == false` (`refusing to queue command while modem is collapsed`) — `ndo_open` failed, `wwan0` stayed **DOWN**, and the kernel **deleted its routes** (`fib_disable_ip`), which is why the symptom looked like a missing default route. **The RX-watchdog lost-edge resync was unreachable in exactly this state** (it required `dmux->rx && dmux->tx`, measured `pc_resync_count: 0`), and the only other rebuild paths need a **rising edge** that never arrived. **Stuck state, verbatim:** `pc_line_level 1` (the SMSM irqchip reads the **modem's SMEM state word** — the modem said it was awake) with `pc_state 0`, `rx_tearing_down 1`, `rx_slots_mapped 0`, `cmd_open` frozen at **24**, `runtime_status suspended` — while `remoteproc0/state` was **`running`** and the modem was registered/attached at 88 %. **This is NOT Doc 154 §6** (there `state = offline`, log stopped at `loading mpss`). **Patch 814** retries the powerup work (500 ms apart, 20×, ~77 s), lets the watchdog rebuild missing channels, and arms both from the resume path; `runtime_resume()` still returns 0 deliberately because an error sets `dev->power.runtime_error`, after which `rpm_resume()` refuses **every** later resume. **Not a stability fix** (cadence untouched); chain byte-verified; baseline healthy. **The recovery path is PROVEN** (run 3): with a deliberately crippled test build that shortened the poll to 40 ms **and dropped the pc assert edge whenever `rx`/`tx` were NULL**, the retry polled the modem's SMEM state word, called `bam_dmux_power_on()` **itself**, reinitialised all eight channels and brought the data plane back fully — so recovery does **not** depend on ever receiving another interrupt. The test patch is preserved (`run3_test_edits.patch`, reproduces md5 `5e26986d…`) and its revert was verified **by rebuild** back to the production hash `eca269f1…`. **Run 6 then showed the watchdog rebuild firing on a NATURAL trigger, twice** (2907.1 s and 3888.5 s; the data plane returned both times, `rebuilds` 0→1→2), with `retries 0` across all 8 SSRs because every modem was ready in 540–580 ms. What is still unobserved is a *natural* occurrence of the **retry's** trigger. Run 6 also produced a `recovered`-but-no-data stall (n=1, unexplained) and a third harness blind spot (a bounded `tail -8` window). Also **re-observes the Doc 151 §5 `echo stop` hang (n=2)** — but note that hang is a **different site** from Doc 159's |
| `159_THE_AP_HANGS_ON_A_NATURAL_FATAL_SSR.md` | **The AP can hang on a SPONTANEOUS fatal-triggered SSR — it is a production failure, not the `echo stop` testing hazard the corpus recorded.** A boot that ran 4 h 27 min took **21** modem fatals, recovered from **20**, and **hung on the 21st** with nobody touching `remoteproc0/state`; the console stops dead (no panic banner, no `BUG:`, no `dmesg-ramoops`) and only the hardware watchdog recovers the device. **`port failed halt` is NORMAL — 21 crashes, 21 `port failed halt`, 21 `MBA booted`** — it is a `dev_err()` on a 100 ms `AXI_IDLE` poll timeout (`qcom_q6v5_mss.c:969-974`) and 20 of those crashes recovered 43–47 ms after printing it; **a message is not a cause until its rate is known.** Because the next print normally follows in 43–47 ms, the hang window is bounded by two adjacent printk calls and is enumerable: the tail of `q6v5_mba_reclaim()` + the head of `q6v5_mba_load()` — **three untimed `qcom_scm_assign_mem()` TrustZone ownership transfers** (`:1294`, `:1149`, `:1157`), a reset assert/deassert pair, four regulator ops, four clock ops, six TCSR writes, and one SMP2P/mbox teardown+setup. **Leading candidate: the SCM handoff** — a synchronous SMC with no timeout, at the exact moment the modem (the other VMID owner of that memory; Doc 158) has just crashed. **Not proven.** The hang lands at a **different site** from Doc 151 §5 / Doc 157 §8.2 (that one stops *before* `executing serialized asynchronous SSR teardown`; this one gets past it, past the port detaches, past `stopped remote processor`). Crash #21 came **49.0 s** after #20 (an activity-correlated fatal, not the 902 s timer) — n=1 hypothesis. Crash #19 carries the **same signature** (`a2_power.c:2949`) and survived in 47 ms — third confirmation of the "never classify a fatal by its `file:line`" rule; and **`a2_power.c:2949` has now been observed**, where the decode reference said it was not. Recommended next step: `dev_info()` at each of the 13 steps in a temporary build — the console is the only instrument that survives a hang |
| `158_MPSS_UNREADABLE_FROM_AP.md` | **A measured negative result that closes the live-mpss-observation line.** A kernel module **can** `ioremap` the `no-map` mpss region (`mpss@86800000`, base `0x86800000`, size `0x5500000`), and the debugfs file is created — but the **first read of offset 0 aborts**: `Internal error: synchronous external abort`, `pc : mpss_read+0xc0`, `x0 = ffff800090000000`, faulting insn `ldr w0, [x0]`; five occurrences; the kernel kills the `dd`, taints `[M]=MACHINE_CHECK` and continues. **The memory-type confound was eliminated**: the abort reproduces identically with `memremap(MEMREMAP_WC)` (Normal-NC, the driver's own mapping), faulting in `__memcpy` — so page attributes, cacheability and access width are all excluded. **Cause: mpss is assigned by TrustZone** — `q6v5_xfer_mem_ownership()` → `qcom_scm_assign_mem()` between `QCOM_SCM_VMID_HLOS` and `QCOM_SCM_VMID_MSS_MSA` (`qcom_q6v5_mss.c:422-450`), and the AP only `memremap`s mpss after taking ownership back (`:1403-1404`, `:1440`, `:1547-1555`). **Corrects Doc 153 §5:** its `/dev/mem` `EFAULT`/`SIGBUS` are the same hardware abort by two userspace paths, not a `no-map`/`CONFIG_STRICT_DEVMEM` effect; `nomap` is a kernel-MM attribute that says nothing about bus access. Also: after the aborts `rmmod` failed (`refcnt -1`) and `stat()` of the debugfs file blocked in `D` state (a reboot clears it); `rpmring` works only because the RPM ring is SRAM. **Consequence: the coredump is the only instrument for the fatal's state.** |
| `160_PATCH_CHAIN_VERIFICATION_AND_STALE_LIVE_TREE.md` | **Build-provenance verification: the built kernel contains all 18 tracked patches (17/17 target files byte-identical to pristine + `msm89xx/patches/`), but the LIVE `openwrt/target/linux/msm89xx/patches/` was stale at 14 — missing `810`, `811`, `812`, `814`.** Because the build reads the **live** directory (`PATCH_DIR`, `include/kernel.mk:43`), and because that directory is part of the prepare stamp's md5 (`include/kernel-build.mk:12-13`), a re-prepare in that state would have `rm -rf`'d `build_dir` and rebuilt from the stale set — producing a `qcom_bam_dmux.c` **277 lines shorter** (2598→2321; 26 hunks, +303/−26) with the `tx_sweep_guard_hits`, `defer_q`/`defer_sub` counters and **patch 812's root-cause data-stall fix** all absent, invisibly. The tracked tree is authoritative only because `build.sh`'s `sync_bsp()` copies it over the live one (`build.sh:336`). **Fixed** by syncing (both trees now byte-identical; reconstruction from the live tree re-verifies 17/17). Two wrong intermediate answers are recorded: a created file expressed as `--- a/…` + `@@ -0,0 +1,N @@` (patch `813`'s `msm-poweroff.h`) is missed by a `--- /dev/null` scan, and a "patches FAILED" result that was a dirty-tree artefact. **No firmware, driver or DTS change was made.** |
| `161_BUILD_TOOLING_PATCH_GUARD_AND_SELECTIVE_BUILDS.md` | **Build tooling; no firmware/driver/DTS change.** `build.sh` now (a) **guards the patch tree**: `bsp_drift()` compares tracked vs live (`msm89xx/` → `target/linux/msm89xx/`, `packages/` → `package/msm8916/`), `sync_bsp()` **reports drift before healing and asserts afterwards**, and `assert_bsp_synced()` covers **both** install paths (`sync_bsp` *and* `scripts/openwrt-prepare.sh`, via `ensure_prepared`/`force_prepare`); (b) adds **`./build.sh guard [--deep]`**, exiting non-zero on drift and needing no Docker in its basic form; (c) `--deep` predicts a re-prepare **exactly by asking make** for its own `STAMP_PREPARED` via an `--eval`'d target, rather than re-implementing `find_md5` — which would be **wrong**, because that hash covers **absolute** paths that differ between host and container; it records that **`make -p` prints recursive variables UNEXPANDED** (so `-p` cannot work), that **`TOPDIR` must be passed** (exported by the top-level make, not set in `rules.mk`), and that **`TARGET_BUILD` must be exactly `1`**; (d) adds **`kernel [board]`, `package <name\|path> [board]`, `kmod <name> [board]`** with a board-optional `ensure_config` (reuses `.config` instead of re-running `defconfig`) and five-path package resolution (base-tree nested / feed symlink / project / explicit); (e) draws the honest line that an **in-tree** kmod (`qcom_bam_dmux`) has **no narrower goal than the kernel target**, and prints matching modules with **md5** (`kmod bam-dmux` → `qcom_bam_dmux.ko` `eca269f1…`, the hash on the device); (f) adds a **fully side-effect-free `DRY_RUN=1`**, whose `prepare_config` guard was added after a dry run **clobbered `.config`** (leaving `TARGET_DIR_NAME` = `_`; `.config` restored to the hmu05 board config). Validated by **10 checks** including a real **drift-injection cycle** (detect → report → heal → verify). **The guard does NOT cover a `make` run directly inside the container — that is what `guard` is for.** |
| `162_SOAK_RUN8_PATCH812_AT_SCALE_AND_FATAL_SIGNATURE_TAXONOMY.md` | **Run 8: the two AP-side data-plane fixes measured at scale, plus a quantified fatal-signature taxonomy.** 70 min, 4 fatals, 4 SSRs, same harness as run 6: **`tx_defer_queued 549` = `tx_defer_submitted 549`, gap 0, `tx_defer_wiped_live 0`, `tx_sweep_guard_hits 0`, 0 oopses, `cmd_open 40`** — against Doc 156's pre-fix **27/27 destroyed**; the data stall is gone at load, not just in the 3-packet repro. Patch 814 recovered the data plane on **4/4 natural SSR triggers** (~20/40/15/30 s) but **`retries: 0`**, so the *retry* path is still unvalidated on a natural trigger. **The run's four fatals alternate 900.965 / 941.288 / 900.662 / 940.238 s of modem uptime with the signature alternating `lte_ml1_sleepmgr_stm.c:4054` / `a2_power.c:1189` in lock-step** — on boots 2 and 4 the deterministic ~901 s fatal did not fire at all (SSR count 4 = fatal count 4, so nothing was missed); run 6 showed no such alternation under identical conditions, so it is a property of the boot, **unexplained, n=4**. And the taxonomy: `lte_ml1_common_timer.c:390` **n=11, 902.353 s, ±281 ppm** (the deterministic timer, matching `400 × 2.256 s`); `lte_ml1_sleepmgr_stm.c:4054` **n=10, 901.230 s, 4.6× the spread** (likely downstream); `a2_power.c:1189` **n=7, 68.5–941.3 s — not a clock**. **Refines Doc 149: the `file:line` does not identify *the* assert, but it *does* identify which mechanism fired — ask "what is this signature's period?"** |
| `163_ERRFATAL_DESCRIPTOR_IS_INLINE_AND_MATCHES_DMESG.md` | **The ERR_FATAL coredump descriptor carries a plaintext, INLINE filename, and its `file:line` matches that fatal's dmesg signature 11/11 across two boots.** Record at ELF VA `0xC35B1280`: `+0x10` line (u16), `+0x14`/`+0x18` words A/B, **`+0x24` filename, NUL-terminated, in the clear**. The corpus' *"obfuscated"* / *"no 16-byte filename table"* is true of the **ELF on disk** but not of the **runtime copy in a coredump** — the earlier reader followed the unrelated `+0x08` pointer instead of reading `+0x24`. Run 8's 5 dumps decode to `lte_ml1_sleepmgr_stm.c` 4054 / `a2_power.c` 1189 / `lte_ml1_sleepmgr_stm.c` 4054 / `a2_power.c` 1189 / `a2_power.c` 1189, exactly its 5 dmesg signatures; the earlier boot's 6 dumps all decode to `lte_ml1_common_timer.c` 390, matching Doc 149 §2's table (and covering **4** distinct fatals, not 6 — two were captured twice). Word B advances **+11/+12 per fatal**. **So Doc 149's "must not classify a fatal by its `file:line`" is a correct warning and a wrong conclusion** — the string does not name the root cause but it does name *which site tripped*, which is the modem RE's §5.2 exactly, and it closes the alternative reading of Doc 162 §3 (the signature cannot be stale). Also records that `/overlay` was at **100 % with 9.2 MB free** while the watcher writes an 85 MB dump per fatal; **all 36 dumps (2.9 GB) were copied to `scratch/coredump_live_full/` and verified byte-identical by md5 (36/36)** before `/overlay/coredump_live/` was cleared (**9.2 MB → 2.9 GB free**). Tool: `evidence/163_errfatal_descriptor/errfatal_descriptor.py`. |
| `164_Q6V5_SSR_WINDOW_IS_NOW_OBSERVABLE.md` | **The Doc 159 hang window is instrumented (patch 817, 23 trace points), and the instrument was measured before it was trusted — it was 4.2× the phenomenon, and that was fixed.** The window re-measured independently from the previous boot's `console-ramoops-0`: **43.726–46.944 ms, n=5, mean 45.481 ms** (Doc 159's 43–47 ms reproduces exactly). The console costs **1.03 ms + 0.0904 ms/char** (a **4 %** match to the 115200-baud serialisation rate of 0.0868 ms/char), so an 80-char trace line is **≈ 8.3 ms** and 23 of them are **≈ 190 ms against a 45.481 ms window**; an independent bound from the cold-boot bring-up agrees (**113.8 ms ≈ 8.1 ms/line**). **Fixed by `console_loglevel = 6`** — KERN_INFO is suppressed on every console but still stored in the ring buffer, **11.87 → 0.37 ms/line (32×)**, verified that a suppressed line still reaches the ring, with `dev_err` reference points keeping a kernel-side sink; applied at runtime and persisted in `/etc/rc.local`. Records the **8/8 interval↔signature correlation** (short ≈900.7 s ⇒ `lte_ml1_sleepmgr_stm.c:4054`; long ≈942 s ⇒ `a2_power.c:1189`) that reframes Doc 162 §3's "alternation"; that **`/dev/pmsg0` survives a reboot** (verified) and that netconsole is impossible here; the **ramoops byte-corruption trap** (`received` → `rEceived`, so a plain `grep -c` undercounted 5 fatals as 4); the squashfs/staging-`.ko` deployment traps; and both pre-registrations. Tooling in `evidence/164_q6v5_ssr_window_trace/`. |
| `165_ONE_FATAL_TWO_MBA_RELOADS_COREDUMP_IS_IN_THE_SSR_WINDOW.md` | **One fatal produces TWO `01..23` cycles, because the coredump reader is inside the recovery path.** `q6v5_mba_load()` has **two** callers: `q6v5_start()` (`:1585`) and **`q6v5_reload_mba()` (`:1323`), reached only from `qcom_q6v5_dump_segment()` (`:1544`)** — and `rproc_boot_recovery()` calls `rproc->ops->coredump()` **between `rproc_stop()` and `rproc_start()`**. The half-cycle order is therefore `01-09, 10-23, 01-09, 10-23`; the `dump_mba_loaded` flag (`mba_reclaim` clears at `:1246`, `mba_load` sets at `:1189`) is the mechanism; and **`port failed halt` is emitted by the *coredump's* reclaim, not the restart's** (it prints at the top of `q6v5_mba_reclaim()`, measured 0.180 ms before step 01). The coredump's synchronous segment copy (**85 398 475 B at 80.2 MB/s, 1.064182 s**, `RPROC_COREDUMP_ENABLED` → `vmalloc` → `dev_coredumpv`) **stalls the recovery path while the modem is held down** and adds an extra MBA boot/reclaim pair to every fatal — **Doc 153 §4 sharpened**: "created after `rproc_stop()` returns" is true, but it is *inside* `rproc_boot_recovery()`, on the critical path; `bam_dmux: refusing to queue command while modem is collapsed` falls inside the gap. **The Doc 159 window is 86.2 % one untraced wait**: 0.180 ms halt tail + **5.888 ms for all 23 steps (13.4 %)** + **37.900 ms `q6v5_rmb_mba_wait(qproc, 0, 5000)` (86.2 %)** = 43.968 ms, so **Doc 164 §4's "that span contains all 23 trace points plus the MBA firmware load" is literally true and materially misleading** (the wait is 6.4× the trace). `q6v5_rmb_mba_wait()` is **bounded** (`msleep(1)` + `time_after`, 5000 ms → `-ETIMEDOUT` → `MBA boot timed out`), so the 37.900 ms is real modem boot time; a hang showing `MBA boot timed out` would point at the untimed `readl()` of `RMB_MBA_STATUS_REG`. **Doc 164 §9's pre-registration stands as written, with its coverage gap recorded rather than patched** — a hang in the untraced 86.2 % leaves the last line at **step 23**, a third outcome §9 did not enumerate; and `q6v5_xfer_mem_ownership()` has **14 call sites, of which patch 817 traces 3**, the two untraced mpss transfers in `qcom_q6v5_dump_segment` (`:1547`, `:1570`) sitting in the dump path. Instrument validated live (**every inter-step delta 0.021–0.244 ms**, the ~0.1 ms/line predicted for `console_loglevel = 6`); telemetry holding across the fatal (`defer_q 178 == defer_sub 178`, `defer_wipe_live 0`, `guard_hits 0`, `pc_resync 0`, clean recovery `retries: 0`, 3/3 ICMP 0 % loss); `modem pc-ack timeout during resume` shown to be a **symptom, not a precursor** (inconsistent position vs the fatal in the previous boot). Two harness traps: **`cat /dev/kmsg \| grep PAT \| head -N` never terminates on this busybox** (block-buffered `grep` → no `SIGPIPE`; use `grep -m N`), and **killing the soak by pattern orphans its `cat /dev/kmsg` child** (one held a deleted log inode since AP 112 s). **Doc 164 §6's prediction is partially scored and its 940.2–947.0 s "long" band does NOT survive**: this fatal's ≈ 925.4 s of modem uptime (offset borrowed from the previous boot, since the ring had wrapped past the modem's initial boot) falls in neither band, contradicting Doc 162's own taxonomy (`a2_power.c:1189` n=7 spanning 68.5–941.3 s, *not* a clock) — what survives is the *directional* claim, since this boot's first fatal differs in both signature and AP time from the previous boot's. Tooling: `evidence/164_q6v5_ssr_window_trace/q6trace_analyse.py` + `window_analysis.txt`. |
| `166_THE_HANG_IS_THE_SSR_TEARDOWN_HANDOFF.md` | **The AP hang is located, and it is upstream of every instrument so far.** Crash #2 at AP 1840.390 s (`lte_ml1_sleepmgr_stm.c:4054`) printed `recovering`, then **`bam_dmux: SSR before shutdown: scheduling teardown work` at 1840.413105 — the last line a userspace reader copied** (see correction (a) below: the `pmsg` sink is userspace-fed, so this is not the same as "the last line `printk` stored"). Two pstore sinks agree and their **union covers every expected next line**: `console-ramoops-0` carries `dev_err`+ only (`console_loglevel = 6`), while `pmsg-ramoops-0` is the soak's **filtered `/dev/kmsg` mirror** whose filter (`*"SSR "*`, `*q6v5-trace:*`, `*"port failed halt"*`, `*"MBA booted"*`, `*"Kernel panic"*`, `*"BUG:"*`, `*"Unable to handle"*`, `*"watchdog"*`) matches the healthy path's every next step — **none fired**; both zones use `PRZ_FLAG_ZAP_OLDEST` and both end at the same instant, so this is *not* "the console went quiet". A **third, printk-independent** proof: `coredump_watch.log` shows boot B made **exactly one** dump (crash #1's) and **none for crash #2** ⇒ **`rproc_stop()` never returned** (Doc 153 §4). The hang is therefore in **`qcom_bam_dmux.c:2233-2237`** — the teardown work's own print at `:2238` (measured +2.747 ms on the healthy crash #1) never appeared, and the only unbounded waits in that five-line region are `cancel_delayed_work_sync(&tx_retry_work)` (`:2234`) and `cancel_work_sync(&tx_wakeup_work)` (`:2235`) — **no timeout, not interruptible**; `tx_wakeup_work` is not a leaf (`pm_runtime_resume_and_get()` at `:739`, `mutex_lock(&state_lock)` at `:758`) and `state_lock` is held across a full `bam_dmux_power_on()` by the threaded `pc_irq` handler (`:1903`→`:1909`), which is exactly what runs during an SSR. **Both cancels are redundant**: `:2233` sets `in_teardown` one line earlier and both works test it as their first statement (`:736`, `:850`) and re-test under the lock (`:764`) — so the cancels add a *wait*, not a guarantee; **patch 820** swaps them for the non-blocking forms (mutual exclusion is already `state_lock` below). **And a second, stronger site of the same defect: `bam_dmux_pm_quiesce()` (`:1661`, one caller — `pc_irq` at `:1936`, which holds `state_lock` from `:1903`) and `bam_dmux_power_off()` (`:1577`) both `cancel_delayed_work_sync(&dmux->rx_watchdog_work)` while holding that lock, and `bam_dmux_rx_watchdog_func()` takes it at `:1309` ⇒ a TRUE ABBA on one mutex, exercised ~7.3×/min on every ordinary power collapse.** 820 fixes both, and **deliberately keeps `rx_rearm_work` synchronous** — it takes no `state_lock` and submits RX buffers, so an async cancel would race `dmaengine_terminate_sync()`. 820 also adds **five `dev_err` hand-off probes** (T0–T4) with a **pre-registered decode table**: T0-without-T1 ⇒ workqueue starvation; **T3-without-T4 ⇒ an external `state_lock` holder, i.e. this ABBA was the real cause**. **Doc 164 §9's pre-registration is falsified in site** — there is **no `q6v5-trace` line in the hang at all**, because the hang is *before the window begins*, in the `rproc_stop()` → SSR-notifier → bam_dmux-teardown **hand-off**; Doc 165 §4.1's coverage gap was therefore **too narrow** (the blind spot is upstream of the window, not just inside it). **The reset is silent:** the ramoops DT node is `compatible console-size name pmsg-size record-size reg` — **`record-size` present, `max-reason` absent**, so the dmesg zone exists with the default `max_reason = KMSG_DUMP_OOPS` and an oops *or* panic **would** leave a record; there is none, and a panic's `KERN_EMERG` banner cannot escape the in-kernel ramoops console either ⇒ **not an oops, not a panic**; ~2–4 s does **not** match Doc 159's 30 s PM8916 PON WDT, so **the two AP hangs do not share a reset mechanism** (proposed instrument: SMEM item 403 `SMEM_POWER_ON_STATUS_INFO`, read-only). **RETRACTED: the "stale `qcom-time-daemon` / periodic ATS_USER refresh" lead is dead** — `ps` shows `/usr/sbin/qcom-time-daemon -r 0 -v` and the daemon logs `Periodic ATS_USER refresh: DISABLED (interval=0s)`; only a stale *comment* remains. Also: the **two sinks are not interchangeable** (read both, and read the second one's filter); **pstore records and the instrumented `.ko` both survive the reboot they cause** (Doc 164 §3's "re-copy after every reboot" was over-cautious — verify the md5); and a secondary defect recorded but **deliberately out of scope for 820**: the *same* work item is queued on **two workqueues** (`queue_pm_work()` = `queue_work(pm_wq, work)` at `:687`/`:1922` vs `queue_work(system_wq, …)` at `:857`), so a retry can be silently dropped while pending on `pm_wq` — a lost-retry bug, not a hang. New probe trap: **`grep -c PATTERN /dev/kmsg` never terminates** (no EOF); use `dmesg \| grep -c`. Evidence: `evidence/165_hang_1840s/{dying_boot_tail,corroboration,source_region,deploy_820}.txt`. **THREE CORRECTIONS TO THIS DOC, made after its first draft — read §2.2b and §5.5 before citing it.** (a) **`pmsg-ramoops-0` is fed by a USERSPACE reader** (the soak's `cat /dev/kmsg | while read` loop), so its last line is *the last line userspace copied*, not the last line `printk` stored ⇒ "no further line appeared" is proven **only for `dev_err`+** (`console-ramoops-0` is a real in-kernel console sink); the missing `dev_info` line may simply never have been copied, and `rproc_stop()` never returning is proven **only** by the printk-free coredump watcher. (b) **`wwan0at0 disconnected` is NOT a bam_dmux line** — `wwan_remove_port()` (`wwan_core.c:529`) is called from `rpmsg_wwan_ctrl.c:145`/`mhi_wwan_ctrl.c:255`/`wwan_hwsim.c:239` and **`qcom_bam_dmux.c` has no `wwan_*` port calls at all** — it is an **rpmsg/SMD** teardown line on another thread. So **three independent consumers stop within ~7 ms**: the SMD/rpmsg teardown, bam_dmux's teardown work, and the userspace `/dev/kmsg` reader — **which one driver mutex cycle cannot explain**, hence §5.4's **live alternative: the stall is in the logging/console path** (the console serialises synchronously at 115200 baud, 1.03 ms + 0.0904 ms/char, and the last line before the silence, `recovering`, **is a `dev_err`**; a wedged console/logbuf lock silences every sink and blocks every reader at once, and would explain `rproc_stop()` never returning with no bam_dmux lock cycle at all). **Patch 820 is therefore downgraded from "the cure" to "two provable deadlocks fixed"** (one a true ABBA) — still worth shipping, and it carries the T0–T4 probes, but not claimed to be the cause. **Also new: this hang is one step EARLIER than Doc 151/157's `echo stop` hang**, which ends on *two* lines (`SSR before shutdown` **and** `wwan0at0 disconnected`) ⇒ the region has **at least two adjacent hang points**. **THEN RETRACTED IN PLACE: §5.4 itself is withdrawn (§5.5), verified against the kernel source** — `grep -rn logbuf_lock kernel/printk/ include/linux/printk.h` has **no matches** (the global `logbuf_lock` was removed in v5.10), `printk_ringbuffer.c` contains **zero spinlocks** and its own header says *"readers and writers to locklessly synchronize access to the data"*, `devkmsg_read()` takes only a **per-file-descriptor** mutex, and `syslog_print_all()` takes `syslog_lock` **only inside `if (clear)`** (i.e. only `dmesg -c`). So a wedged console **cannot** silence a `/dev/kmsg` reader, and §5.4's mechanism does not exist in Linux 6.12. **The corrected reading is simpler:** `devkmsg_read()` **sleeps on `log_wait`** when the ring has no new records, so the pmsg filter's silence means only "nothing further was stored" — it is *not* a third witness, and the "three independent consumers" collapse to **two print sites plus one reader with nothing to read**; **one** blocked step in the `QCOM_SSR_BEFORE_SHUTDOWN` **blocking notifier chain** explains both, so §5.4's premise ("a single mutex cycle cannot explain that") is simply wrong. **Consequence: §5.4 was the only reason 820 was downgraded — with it retracted, the `state_lock` ABBA and the unbounded `cancel_*_sync` waits are again the leading explanation (still unproven).** **Zero-cost discriminator for the next hang: read `/overlay/q6trace.csv` FIRST — but with CORRECTED polarity**: CSV **continuing** past the last `pmsg` line ⇒ the AP is **alive** and the stall is **confined to the code path that stopped printing** (exactly what a driver lock cycle on one thread looks like — it does **NOT** exonerate the driver locks); CSV **stopping** first ⇒ a genuine global stall. (The CSV was confound-checked: `rx_telemetry_show()`, `qcom_bam_dmux.c:2116`, takes only `dmux->rx_lock`, **not** `state_lock`.) **And a `/dev/kmsg`-based watcher structurally cannot be a liveness beacon** — which is the real justification for the §13 item 3 beacon. |
| `167_THE_HANG_IS_NOT_DETERMINISTIC_AND_THE_TEARDOWN_ORDER_IS_A_RACE.md` | **THE AP HANG IS A RACE, NOT A SCHEDULE — and the SSR teardown order is a race too.** Boot C was left soaking after Doc 166 and reached **2181.14 s through TWO fatals, recovering from both** (0 oops, 2 coredumps, `guard_hits` 0, `defer_wipe_live` 0, `defer_q == defer_sub` = 300, 106 trace lines). **Its second fatal landed at AP 1837.400528 (`a2_power.c:1189`); boot B hung at AP 1840.390188 (`lte_ml1_sleepmgr_stm.c:4054`) — 2.989 s apart, opposite outcomes.** So the hang is **not** a function of the fatal, the AP time, or "the second fatal of a boot": the *trigger* is reproducible and the *failure* is not, which is what has been masking this. **Signature correlation is offered as a hypothesis only (n = 2 per cell)** — both observed hangs' fatals were `lte_ml1_sleepmgr_stm.c:4054`, while `a2_power.c:1189` fatals recovered; mechanistically credible (a sleep-state-machine fatal may leave the A2 power-collapse state machine in a different state) but far too small a sample; it is free to score on every future fatal. **The teardown order is ALSO a race and it flips WITHIN one boot:** boot C's fatal #1 printed `wwan0at0 disconnected` **before** `executing serialized asynchronous SSR teardown` (+1.737 vs +3.289 ms), its fatal #2 printed them in the **opposite** order (+0.774 vs +3.722 ms) — because `schedule_work(&dmux->ssr_teardown_work)` runs in the notifier while the rpmsg/SMD teardown runs from the stop path, so `system_wq` versus the stopping thread decides. **So Doc 166 §2.2c's ordering *argument* is RETRACTED** — "ends on two lines vs one" does not establish a step ordering — **but its conclusion survives for a better reason: the right discriminator is *WHICH* line is missing.** In boot B's hang **neither** appeared, and since the rpmsg teardown runs *inside* `q6v5_stop()` (after the notifier returns), a missing `wwan0at0 disconnected` means **`rproc_stop()` never got past the notifier**. In Doc 151/157's `echo stop` hang `wwan0at0 disconnected` **DID** appear, so `rproc_stop()` *did* continue and only bam_dmux's **teardown work** failed to print. **⇒ the two hangs have DIFFERENT suspects**, and **820 may fix `echo stop` while leaving the spontaneous hang untouched — report them separately.** **Doc 165 confirmed exactly:** the trace log is `14 + 4 × 23 = 106` lines (boot bring-up + **two full 01..23 cycles per fatal**), and the CSV's `traces` column steps **14 → 60 → 106**, i.e. **+46 per fatal**. **`B` is NOT a fatal counter across boots:** within boot C it advanced **+11** (`0x01128E3D` → `0x01128E48`, Doc 163's claim re-confirmed on a second boot), but run 8's last fatal → boot C's first is **+42 where three fatals explain only +33..+36** (boot history known exactly from the watcher log: run 8 = `pid=3252`, boot B = `pid=3199`, boot C = `pid=3746`, no boot between) — an unexplained excess, **mechanism left open rather than guessed**; the RPM LPR counter went 1437 → 1487 (+50) across the same pair. **THE CONSEQUENCE THAT MATTERS: the hang rate is ~1 in 20–40 SSRs (3–5 %)** — 39 dumps in `coredump_watch.log` plus Doc 159's 1-in-21 — so **"zero hangs observed" only becomes evidence at n ≥ 60 SSRs (rule of three: 3/n < 0.05), which is ~18 h of soak at ~2 SSRs per 35 min.** **Boot C already delivered 2 clean SSRs WITHOUT 820, so a single clean 820 boot means nothing.** Pre-register the sample size now; **score T0–T4 on every SSR** (they fire on healthy SSRs too, so **one boot** answers "does the teardown work now complete?"); and **re-run 820 without the `dev_err` probes** before claiming the cancels fixed anything, because Doc 166 §9.1 measures T0 at **≈ 9.6 ms** of console time on `rproc_stop()`'s critical path — larger than the **7.453 ms** healthy hand-off it sits in. **Telemetry (patch 812 at scale again, on a second boot and across two SSRs):** `defer_q == defer_sub` at every sample (256/256), `defer_wipe_live` 0, `guard_hits` 0, `cmd_open` 8 → 16 → 24 (**8 more command channels per recovery**), `rx_mapped` 32 → 0 → 32 across the teardown, `pc_resync` 8 → 18 during ordinary operation (not a precursor). Both coredumps decode to their dmesg signatures — a **12th and 13th** confirmation of Doc 163. **Boot C is therefore the pre-820 CONTROL.** **820 was deployed at the end of this round** (`qcom_bam_dmux.ko` md5 `e441491317f09e34a3e72dc15cdacd3c`, 240 544 B, srcversion `F7405AD685715AEC3C274D9`, 5 T-strings, vermagic `6.12.94 SMP preempt mod_unload aarch64`; pre-820 backed up to `/overlay/modbackup/qcom_bam_dmux.ko.pre820`), and the post-reboot state is clean: `printk = 6 4 1 7`, 14 trace lines, 0 fatals, 5 soak procs + watcher (pid 3485) + beacon, `/overlay` 392 M / 13 %. Evidence: `evidence/167_hang_not_deterministic/`. |
| `168_THE_AP_FREEZE_IS_THE_ASYNC_TEARDOWN_AND_ECHO_STOP_IS_A_FORCING_FUNCTION.md` | **820 WORKS — AND IT MOVED THE HANG. `echo stop` IS NOW A ~2-SECOND FORCING FUNCTION, AND DOC 167 §7.1 IS FALSIFIED.** Both post-820 boots hung: **#1** fatal AP **276.111919**, `q6v5-trace 01..09` at **276.156528-276.156671**, **T4 @ 276.164748**, then nothing; **#2** fatal AP **951.663368**, **no `q6v5-trace` at all**, **T4 @ 951.719173**, then nothing. Both: **no coredump ⇒ `rproc_stop()` never returned**, both silently reset. `console-size = 0x40000` with a ~24 KiB record starting at `[0.000000]` ⇒ **no wrap, so T4 really is the last line the kernel emitted.** **What 820 fixed is real:** pre-820 the teardown work died *before its first print* (Doc 166); with 820 it clears both cancels (T2/T3 print) and acquires `state_lock`. **What it did not fix: the AP still freezes, after T4.** **A THIRD post-820 SSR RECOVERED (fatal AP 943.555967, coredump created, modem back up, `rproc` `running`) AND IT SUPPLIES THE MECHANISM: the work must finish its DMA teardown BEFORE `q6v5_stop()` gates the modem — T4−fatal +43.3 ms with the power-down at +62.1 ms ⇒ RECOVERED; +52.8 ms with the power-down at +44.8 ms ⇒ FROZE; +55.8 ms with the trace never reached ⇒ FROZE; pre-820 boot C reached its first print at ≈ +3.3 ms against a ≈ +45 ms power-down ⇒ never hung (n = 3). H1 (ordering race) therefore explains all three post-820 SSRs *and* the pre-820 control, and H2 (the BAM's own runtime PM) is retired. ⇒ the probes did not create a bug, they widened an existing race from ~0 % to ~2-in-3, so removing them alone is NOT a fix (right control, wrong remedy). Also: `dev_info` IS in `dmesg` — the filtering is on the console/`console-ramoops` sink, not the ring — and `T1` before `T0` is normal.** **The confound, measured:** T1→T2/T2→T3/T3→T4 gaps are **8.061/8.680/7.897 ms** (boot #1) and **7.751/8.382/11.530 ms** (boot #2), while the work between them is sub-microsecond ⇒ **each `dev_err` probe costs ~8 ms of synchronous console time and 820's five probes add ≈ 40 ms**, which is exactly what moved T4 (+52.83 ms) past the power-down (+44.75 ms) in boot #1. **But that is not the whole story:** in boot #2 *and* both `echo stop` runs the power-down had **not** started when T4 printed, and the AP froze anyway ⇒ any "BAM was already unpowered" theory is **insufficient**. Honest mechanism list **as first written — NOTE: the third-boot measurement promoted H1 and retired H2, and then 821's scoring FALSIFIED H1 and re-opened H2; see the tail of this row for the current list**: **H1** ordering race vs. the power-down (explains boot #1 only); **H2 the BAM's *own* runtime PM — `bam_dma_terminate_all()` takes **no** `pm_runtime_get_sync()`, unlike every other hardware-touching BAM entry point (`bam_dma.c:576/779/805/914/1032`), so `bam_chan_init_hw()` can write an unclocked block (timing-dependent, does not need the modem down, so it fits)**; **H3** a wait inside the `state_lock` section never completes. **`echo stop` reproduces the freeze 2/2** (AP 137 s → RC=0 → freeze, last line T4 @ 138.112650; AP 165 s → RC=0 → freeze, last line T4 @ 165.997713). **⇒ `RC=0` from `echo stop` is NOT a success signal, and the write completing is NEVER evidence the SSR teardown finished — `rproc_stop()` schedules the teardown work and does not wait for it. Do not use it as a health check.** **Doc 167 §7.1 is HALF RIGHT:** 820 does get the work past the cancels, so the `echo stop` hang is no longer the cancel deadlock — **but `echo stop` is still not safe.** **Doc 167 §3's "which line is missing" discriminator is RETRACTED in part:** `wwan0at0 disconnected` is `dev_info` (`wwan_core.c:529`) and is **suppressed on the console at `console_loglevel = 6`** — it can only appear in the userspace-fed, block-buffered `pmsg` mirror, so **its absence from `console-ramoops` is not evidence at all**; the discriminator that survives is **whether `q6v5-trace 01..09` appears** (those are `dev_err`). Also recorded: a **redundant** `cancel_delayed_work_sync(&dmux->tx_retry_work)` survives at `:1636`, one line after 820 made the same cancel non-blocking at T2 — it is a **leaf** (`bam_dmux_tx_retry_work()` takes no `state_lock`, only `queue_work()`), so it is dead code and **not** a lock-cycle suspect. **PATCH 821 WRITTEN AND THEN SCORED — IT WORKS, BUT IT IS NOT A FIX.** `821-bam-dmux-ssr-teardown-flush-before-powerdown.patch` (67 lines, md5 `29307724173b492af888087e2fb69fba`, both patch trees) adds **one** behavioural change — `flush_work(&dmux->ssr_teardown_work)` in the `QCOM_SSR_BEFORE_SHUTDOWN` notifier (recovery-thread **process context**, may sleep) — so the BAM is released **before** `rproc->ops->stop()` powers the modem down; plus four localisation probes (T5 after the RX DMA release, T6 after the TX DMA release, T7 after `bam_dmux_power_off()` returns, T8 at the end of the work). **SCORED (n = 2, §2.3): the structural test PASSES** — run B printed `T5`→`T9` (`T9 flush returned` 309.363296) **before** `q6v5-trace 01` (309.375474), and `stopped remote processor` followed, i.e. **the SSR teardown completed for the first time ever**; **the outcome test FAILS**, because run A blocked at T4→T5 and run B died from a new defect — **0.14 ms after `stopped remote processor`, a `list_del corruption` WARNING in `qmi-proxy`'s `poll()`** (`prev->next should be ffff800083533c88, but was c40df08e5a393833`; `WARNING: CPU: 3 PID: 4833 at lib/list_debug.c:62 __list_del_entry_valid_or_report+0xd0/0xfc`; `__list_del_entry_valid_or_report → remove_wait_queue → poll_freewait → do_sys_poll → __arm64_sys_ppoll`), where `ffff800083533c88` shares a page with `sp = ffff800083533910` so the removed entry is a `poll_table_entry` on **qmi-proxy's own kernel stack** and `prev` (`x20 = ffff6ee6c4a07f58`) is the wait-queue **head**, whose `next` had been overwritten. Only three `poll_wait()` sites exist in the tree (`wwan_core.c:776` `port->waitqueue`, `rpmsg_char.c:307` `eptdev->readq`, `qcom_smd.c:998` `channel->fblockread_event`); `Modules linked in` lists `rpmsg_wwan_ctrl`+`wwan` and **not** `cdc_wdm`, so `port->waitqueue` (`/dev/wwan0qmi0`) leads — and `wwan_remove_port()` (`wwan_core.c:511`) **wakes that queue (`:524`) then unregisters the port (`:530`) in the same breath**, called from `rpmsg_wwan_ctrl_remove()` (`:145`) during the SSR. **That is a hypothesis, not a measurement** — confirm the poller's identity (`/proc/<qmi-proxy pid>/fd`) before claiming it. **⇒ 821 is a net win but is NOT deployable: it converts a ~50 % hang into a ~100 % reset, so the next work is the wait-queue lifetime, not `bam_dmux_power_off()`.** **§2.2's H1 (ordering race) is FALSIFIED**: run A blocks **with the modem still powered** (no `q6v5-trace` at all) and run B has the ordering 821 guarantees yet still dies, so the n = 3 timing table was a correlation with the **outcome**, not the mechanism. **Honest mechanism list after 821: H1 FALSIFIED; H3 (a wait inside the `state_lock` section never completes) LEADS** — it fits both the intermittency and the powered-modem condition, and `rx_telemetry`'s `rx_active_submitters`/`rx_active_callbacks` can settle it **without a build**; **H2 (the BAM's own runtime PM) RE-OPENED** as a live residual, since its only reason for retirement was H1; **H4 (new) — the wait-queue corruption is its own, independently reproducible defect.** **Two traps recorded:** the corpus had never seen `list_del corruption` **because it structurally could not** (every earlier boot died before the teardown completed) — **absence of a line from a corpus that cannot reach it is not absence of the bug**; and **a liveness probe is only evidence if it ran during the window** — run B's 1.9 s sampler captured nothing against a ~2 s failure and its `NO ANSWER` probes began ~6 s after the reset, so §2.2's "stalled CPU" is downgraded to "run A is consistent with a stall; run B with a plain reset". **Explicitly NOT claimed: n = 2 proves nothing** — the expensive question still needs **n ≥ 60 SSRs**. Evidence: `evidence/168_teardown_races_powerdown/`. |
| `169_THE_AP_RESET_IS_AN_SMD_POLL_USE_AFTER_FREE.md` | **ROOT CAUSE OF THE POST-821 RESET: a use-after-free of `channel->fblockread_event`. Fix = patch 822.** The `list_del corruption` Doc 168 found 0.14 ms after a *completed* teardown is the SMD channel being `kfree()`d while a userspace `poll_table_entry` is still linked in its wait queue. **The identification is arithmetic, not inference:** `pahole` on the build tree's DWARF gives `sizeof(struct qcom_smd_channel) = 176` (⇒ kmalloc-192) and `offsetof(..., fblockread_event) = 88`; `wait_queue_head_t` = `{spinlock_t lock; struct list_head head;}` ⇒ `&fblockread_event.head = channel + 96`. The crash reports `prev = ffff1dbc83a691e0` and `x20 = ffff1dbc83a691d8 = prev − 8 = channel + 88` ⇒ `channel = prev − 96 = ffff1dbc83a69180`, **192-aligned**; and **96 is exactly SLUB's freelist-pointer offset** for kmalloc-192 (`ALIGN(176/2, 8)`), so the clobbered word **is** the freelist pointer ⇒ the channel was already freed. Same arithmetic on Doc 168's run B (`channel = ffff6ee6c4a07f00`, `% 192 == 0`); and it **excludes** `port->waitqueue` (`prev − 792` is not 1024-aligned, and `sizeof(struct wwan_port) = 912` ⇒ kmalloc-1024). **Mechanism:** `qmi-proxy` fd 7 → `/dev/wwan0qmi0` → `wwan_port_fops_poll` (`wwan_core.c:772`) registers entry 0 in `port->waitqueue` **and** entry 1 in `channel->fblockread_event` (`rpmsg_wwan_ctrl_tx_poll` → `rpmsg_poll` → `qcom_smd_poll`, `qcom_smd.c:998`). On an SSR, `qcom_smd_unregister_edge()` (`:1539`) removes the edge's child rpmsg devices (`rpdev->dev.parent = &edge->dev`, `:1097`) ⇒ `rpmsg_wwan_ctrl_remove` → `wwan_remove_port` → **`wake_up_interruptible()` makes the poller runnable** — and then, **in the same thread**, `device_unregister(&edge->dev)` → `qcom_smd_edge_release()` → `kfree(channel)`, **synchronously**. The poller needs a context switch to reach `poll_freewait()`; the teardown never yields ⇒ **the poller always loses, so this defect is DETERMINISTIC — it is NOT Doc 167's ~3–5 % race.** **A/B, causal:** `echo stop` with `qmi-proxy` running ⇒ corruption **1**, AP resets (`uptime` → 60.65); identical run with `qmi-proxy` **SIGSTOPped, fd 7 deliberately kept open** (so `port->waitqueue` is provably alive) ⇒ corruption **0**, `T1`→`T9` completes, `port wwan0qmi0 disconnected`, `stopped remote processor`, **AP survives** (`uptime` 221→283 monotonic, `rproc` `offline`). **`echo stop` is now a ~3 s deterministic reproduction.** **Patch 822** (`822-rpmsg-smd-drain-pollers-before-free.patch`) adds `qcom_smd_drain_channel_pollers()` **between** the child removal (which wakes the poller) and the `device_unregister()` that frees the channels: `wake_up_all()` + a bounded (~5 s, `dev_warn` on timeout) wait for `waitqueue_active()` to clear. **Structural, not a timing heuristic — no poller can re-register during the drain**, since `wwan_remove_port()` sets `port->ops = NULL` under `ops_lock` and `wwan_port_fops_poll()` takes that same lock before `tx_poll`. **Doc 168 §5's H5 is DEMOTED:** `cancel_delayed_work_sync(&rx_rearm_work)` (`qcom_bam_dmux.c:1591`) already runs **~327×/boot** from `bam_dmux_pm_quiesce()` (`:1695`) under the same `state_lock` without hanging, and neither §2 run exercised `T4`→`T5` (21.4 / 9.6 ms, both normal); `bam_dma_terminate_all()` (`bam_dma.c:725`) is bounded (spinlock + register writes, no sleeps). **Instrument gap closed:** `ssr_ledger.sh` now records **`t5..t9` + `pc_state` + `rx_tearing_down`** (a `t5` that never appears is the signature of the idempotent early return at `qcom_bam_dmux.c:1568`); the 5 historical rows are migrated to `?`, **not** zeros. **Explicitly OPEN:** this does **not** explain why the previous boot survived **4 natural fatals** (ledger `543.77 / 1455.70 / 2449.65 / 3355.26`, 4 recovered coredumps) — either `qmi-proxy` is not parked in `ppoll()` when a natural fatal lands, or the corruption fired and the AP survived it and the reset has a second, slower trigger. §8 pre-registers the three questions that separate them, and **the fix is NOT yet scored**. **Three instrument traps:** (a) **`console-ramoops-0` is STALE after a non-crashing run** — it still holds the *previous* crash verbatim, and it nearly produced a false "1 corruption" for the control run (**always cross-check the uptime and PID inside the record**); (b) **`console-ramoops` and `dmesg` have different filters** — at `console_loglevel=6` `dev_info` never reaches the console, so `stopped remote processor` and `port wwan0qmi0 disconnected` are in `dmesg` but absent from `console-ramoops`; (c) **`rx_tearing_down = 1` is NOT a stall** — with `pc_state = 0` it is the normal "modem power-collapsed, ring released" state. Evidence: `evidence/169_smd_poll_use_after_free/`. |
| `170_TWO_FIXES_FOR_THE_SMD_POLL_UAF_MODULE_AND_KERNEL.md` | **TWO INDEPENDENT FIXES FOR THE DOC 169 USE-AFTER-FREE, AND THE POLLER IS NOW MEASURED.** **Doc 168 §2.4's one open hypothesis is closed:** a full `/proc/*/fd` sweep gives `3658 qmi-proxy /proc/3658/fd/7 -> /dev/wwan0qmi0` and it is the **only** wwan/rpmsg fd on the device, and its live kernel stack is `do_sys_poll+0x390/0x4ec <- __arm64_sys_ppoll+0x88/0xe8` — the same function Doc 169's corruption fires in (`remove_wait_queue <- poll_freewait <- do_sys_poll <- __arm64_sys_ppoll`), so **both ends of the loop are observed, not inferred**. **`CONFIG_RPMSG_WWAN_CTRL=m`: the second registration is in a LOADABLE MODULE**, which Doc 169 assumed away — so **PATCH 823** removes the reachable UAF **without a kernel flash**: `rpmsg_wwan_ctrl_tx_poll()` hands `rpmsg_poll()` a `poll_table` with **`_qproc == NULL`**, and `poll_wait()` is a **no-op** then (`include/linux/poll.h:42`) while the returned mask is **byte-for-byte identical** (`qcom_smd_poll()` derives `EPOLLOUT` from `qcom_smd_get_tx_avail() > 20` **independently of `wait`**). Verified in the built object (`add x2, sp, #0x20` / `stp xzr, xzr, [sp,#32]` / `bl rpmsg_poll` — the caller's table is discarded). **The obvious alternative was rejected after reading the callee:** deleting `.tx_poll` falls through to `else if (!is_write_blocked(port))`, and `is_write_blocked()` is `test_bit(WWAN_PORT_TX_OFF, …) && port->ops` while **nothing in `rpmsg_wwan_ctrl.c` calls `wwan_port_txoff()`/`txon()`** (grepped) ⇒ EPOLLOUT would become **unconditional**. **Honest limitation:** it also drops the SMD wakeup for a poller blocked purely on EPOLLOUT; `port->waitqueue` (entry 0) is still registered and RX-woken, so a request/response client self-heals, but it is a real behavioural change — which is why 822 stays on the list. **PATCH 822 REVISED: its bound was an iteration count, not a time bound.** `for (retries = 5000; retries--; ) msleep(1)` is *not* ~5 s — `msleep(1)` can sleep a whole tick, so the real worst case was tens of seconds **inside the SSR teardown**, the exact path Doc 168 spent a round on; it is now `jiffies + msecs_to_jiffies(2000)`, verified in the object as `adrp x25, jiffies` / `add x21, x21, #0x258` (**600 = `msecs_to_jiffies(2000)` at HZ=300**) / `cmp x1, x21`. **That disassembly independently reproduces Doc 169 §4's offset arithmetic from the COMPILER rather than `pahole`:** `&channel->fblockread_event = channel + 0x58` (88) and `&...head = channel + 0x60` (96). **QUESTION B ANSWERED, FOR FREE:** a natural fatal at AP 1335.05 s gave the ledger row `1337.83,1,lte_ml1_sleepmgr_stm.c:4054,2,2,2,2,2,2,2,2,2,2,2,2,1,0,9` — **`t0..t9` all present**, so `bam_dmux_power_off()` ran the **full blocking teardown** (the idempotent early return at `qcom_bam_dmux.c:1568` was **not** taken), with **0 `list_del corruption`**, a coredump captured (`8 -> 9` ⇒ `rproc_stop()` returned) and the AP surviving — **so the `t5..t9` columns answer exactly the question they were added for, and the UAF does not reproduce on the natural-fatal path** (consistent with Doc 159's ~1-in-21; the two paths differ in the **context** that runs the teardown). **Hypothesis, not measurement.** Plus a **free periodicity sample**: that fatal is **900.862 s of modem uptime** (`1335.049762 − 434.187561`), inside Doc 164's short band (900.5–901.0 s) with the signature that band is *always* paired with — **n = 12, pairing holds**. **Operational, read from the target's own `platform.sh` rather than assumed:** `sysupgrade` **preserves `/overlay` unless `-n` is passed** (the script formats `rootfs_data` only when `UPGRADE_BACKUP` is empty) — critical for shipping 822, since `/lib/firmware`, the deployed modules and the coredumps all live there. Question A (`echo stop` × 5, `qmi-proxy` running, control **2/2**) is pre-registered in Doc 170 §7. **ROUND 27 UPDATE — AND THE HONEST PART: the control DID NOT REPRODUCE.** Two controls (the second on a fresh boot with the precondition forced) gave **0 corruption and a surviving AP**, and the window is measured at **≤ 2.07 ms** (wake `129.213099` → free `129.215173`), so **Doc 169 §5's "the poller always loses" is FALSIFIED** and §7's pre-registration is **WITHDRAWN** — a control that is 0/2 cannot score a fix. The missing step in Doc 169's reasoning: a *runnable* task on another CPU can reach `poll_freewait()` before the freeing thread reaches `kfree()`; the two are only ordered if the poller must run on the same CPU, which nothing establishes. Both of Doc 169's positives were taken with the ms-period `hp3.sh` sampler armed — **load is the leading, still-untested discriminator**. **PATCH 823 IS NOW DEPLOYED** (`6553acd4eb83fcf032c1cf0821f35f4d`, 6808 B) via a module swap + reboot (no flash; backup at `/overlay/modbackup/rpmsg_wwan_ctrl.ko.pre823`; `bash scratch/deploy823.sh --revert`), **functionally clean** (ping 4/4, DNS, route, `qmi-proxy` fd intact) and **`echo stop` × 3 gives 3 PASS / 0 FAIL / 0 SKIP** with the teardown `T9` count stepping 1→2→3 and the modem restored each time — read honestly, that is **non-regression**, not proof of a fix, since the control was also 0. **A kernel-only build does NOT produce a deployable module** (the strip happens in packaging: `rules.mk:383-390` → `scripts/strip-kmod.sh`, `objcopy -x …`, `NO_RENAME=1` under `CONFIG_KERNEL_KALLSYMS=y`); `scratch/mkko823.sh` reproduces it **and validates by SIZE against the shipped baseline** (6808 B). **Never verify a stripped module by symbol label** — `objcopy -x` discards local symbols, so a `static` function has no label in *either* object; anchor on the `rpmsg_poll` relocation. The full `objdump -d` diff baseline-vs-823 is **18 lines, all inside one function**. **ONE BOOT WITH 823 HUNG AT t=73.93 s AND 823 IS NOT EXCULPATED:** a **graceful** `rproc_stop()` (`qcom_sysmon.c:555` is reached only when `crashed == false`) → clean `T0..T9` teardown → `qcom_bam_dmux.c:2070` pc-ack timeout → console stops; **no `fatal error`, no `list_del corruption`**, so it is NOT the Doc 169 UAF but the Doc 159 family; **it did not recur** (next boot, also 823, clean past 173 s) ⇒ 1 boot in 2, discriminator not yet run. **Two new device facts:** the dongle **runs its own open WiFi AP** (`phy0-ap0`, SSID `OpenWrt`, no encryption) — the only management path that survives a dead USB gadget, and **do not infer "no WiFi" from `CONFIG_QCOM_WCNSS_PIL` being unset**; and **`/usr/sbin/qcom-carrier-autocfg` power-cycles the modem through `mmcli`** (and can reboot the device), a live confounder for any modem-state experiment — **CORRECTED in §8.10: NOT "every 10 s"; in steady state its `while … sleep 10` body only polls, and the power-cycle sits inside the operator-change / initial-boot / radio-cache-mismatch branch, `reboot` only on an MBN change (read the branch conditions, not the script's reputation)**. **§8.10 THEN REPORTS THE UNCOMFORTABLE PART: an AP RESET SURVIVED 823.** The same boot that gave the 3/3 natural-fatal result reset at **AP ~1061 s, ~165 s after the third fatal's SSR had completed**; **pstore EMPTY** ⇒ not a trappable fault; **no fourth coredump and no ledger row 17** ⇒ **cause UNDETERMINED** (a hung fatal and no fatal are indistinguishable); the qmi-proxy poller *was* the only wwan-fd holder, so 823's vector was present ⇒ **823 fixes the UAF it targets but is NOT a complete fix**, and 822 will not close this class either. **And the beacon that was supposed to characterise it had been truncating its own log at every boot — `: > beacon_a.txt` — destroying exactly the pre-reset tail it existed to capture; it now appends a `BOOT <uptime>s` marker, so the next reset IS characterisable.** **§8.11 NAMES THE RESET MECHANISM AND FINDS THE RATE IS NOT RARE: the AP does not panic, it STOPS — `/sys/class/watchdog/watchdog0` is `active`, `timeout 30`, identity `QCOM PM8916 PON WDT`, so an empty pstore plus a reboot is exactly a ≥30 s global stall (`~30 s stall + ~15–40 s boot` = every 13–61 s outage). Filtering the watcher's 19 unreachable events to real reboots (uptime decreased) gives 15 in ~5 h, nearer 1 in 1–3 SSRs than Doc 167's 1 in 20–40 — but 14 of the 15 are PRE-823 and that window was also the heaviest manual-activity window, so neither reading is a fact yet; the current boot has since survived two consecutive idle-timer fatals (AP 919.62 / 1825.87, a 906 s gap) with `t0..t9` present. §8.12 then deploys the instrument that was missing: a per-boot rolling kernel log (`dmesg` ring buffer, NOT `/dev/kmsg`; ONE FILE PER BOOT so it is not overwritten by the recovery it explains; `sync` every write), because pstore was empty, `console-ramoops` absent, and the soak's own `/dev/kmsg` log path was dead.** **Plus a tooling trap that nearly became a finding: `bash grep` is NOT grep** — it runs bash on the ELF and fails, and `2>/dev/null` turns that into a **false negative** (it nearly established that `qcom_sysmon.c:555` and `qcom_bam_dmux.c:2070` do not exist); use `rg` or plain `grep`. **§8.13 TURNS THE COREDUMP CAPTURE OFF, BECAUSE THE CAPTURE IS A STEP *INSIDE* EVERY SSR RECOVERY:** `rproc_boot_recovery()` (`remoteproc_core.c:1792`) runs `rproc_stop()` → **`rproc->ops->coredump()`** → `request_firmware()` → `rproc_start()`; for the MSS that op is the **default `rproc_coredump`** (`:2426`, because `q6v5_ops` has no `.coredump`) and it returns immediately when the attribute is `disabled` — **note TWO sysfs locations**: `/sys/class/devcoredump/devcdN/` has only `data` (the corpus's "no per-device `disabled`" is about *this* and stays true) while **`/sys/class/remoteproc/remoteproc0/coredump` DOES accept `disabled`**, and `/sys/class/devcoredump/disabled` is the class-level **write-once global lockdown** (never write it). **AND §8.13.1 IS THE FIRST FATAL WITH IT OFF — A CLEAN WITHIN-BOOT A/B THAT RETRACTED MY OWN CORRECTION.** Fatal #3 fired at AP **2718.436833 s**, **predicted from the previous gap within 0.01 s**, recovered fully (`t0..t9`, `rproc=running`), produced **no coredump** (count frozen at 18). Against fatal #2 (capture ON): **`port failed halt` present → ABSENT**, **half-cycle pairs `01-09,10-23` 2 → 1**, **`SSR before shutdown`→`MBA booted` 0.8316 s → 0.1253 s (0.706 s faster)**; boot-wide **3 fatals / 4 `MBA booted` / 2 `port failed halt`** — the two with the capture on, none for the one with it off, because `qcom_q6v5_dump_segment()` gates the second load on `if (!qproc->dump_mba_loaded)` and the second reclaim on `current_dump_size == total_dump_size`. **The correction I had to retract:** I had argued *from the call graph* that `port failed halt` must survive, since `q6v5_mba_reclaim()` is also called by `q6v5_stop()` (`:1672`) unconditionally — but **`q6v5proc_halt_axi_port()` opens with `if (!ret && val) return;`** (`:961-964`, "already idle, say nothing") and only prints when the halt **fails**: the port is **idle in the STOP path** (modem just crashed and reset) but **LIVE in the DUMP path** (which has just re-loaded the MBA). **⇒ a call site being reached is NOT evidence that its error branch executes — read the guarding `if`, not the call graph** (and note this is the **opposite** of trap 2: you need both). **What still survives, so the prediction is not over-read:** the restart's own `q6v5_mba_load()`, whose untraced tail (trace 23 → `MBA booted` = **39.2 ms**) is `q6v5_rmb_mba_wait()` — Doc 165's 86.2 % of the window — so a **same-duration window of the same kind remains**, relocated from `port failed halt`→`MBA booted` to `stopped remote processor`→`MBA booted` (bounded at 5000 ms, but if the hang lives inside it this will not remove it). **Pre-registered bar: 0 AP reboots across 20 post-disable fatals; 7 of 20 scored, AP survived — §8.13.4 adds fatal #6 (n = 4 on a FOURTH signature, `lte_ml1_sleepmgr_stm.c:4054`, recovery `0.127373 s`), §8.13.5 adds fatal #7 (n = 5, sleepmgr, `0.119170 s`), fatal #9 (`a2_power.c:1189`, `0.127579 s`) makes it n = 6, and fatal #11 (sleepmgr, AP `7483.839750`, `0.119912 s`) makes it n = 7 — seven capture-OFF recoveries span `0.119170–0.130237 s` against `0.824902–0.831559 s` for the two capture-ON, ~7× apart, with the SSR ledger's independent `coredumps` column frozen at 18. ⚠ The window now spans TWO regimes (2 idle-clock fatals + 3 cascade fatals).** **★★ AND §8.19 IS THE ROUND'S BIGGEST NEW RESULT: THE MODEM LEFT THE 902 s CLOCK AT FATAL #7 AND IS CASCADING, AND THE RPM'S OWN LOG SHOWS IT STALLING.** The interval sequence is now `#6 5208.721361 (sleepmgr, +903.723)` / `#7 6110.407029 (sleepmgr, +901.686 — the clock)` / **`#8 6231.169902 (a2_task.c:3179, +120.763)`** / **`#9 6397.771058 (a2_power.c:1189, +166.602)`** / **`#10 6580.507441 (a2_task.c:3179, +182.729)`** — `a2_task.c:3179` **repeats** and the modem has **not returned to the clock**, i.e. a different operating regime. **`rpmring -t -n 500000 -i 4` (910 blocks / 40 709 records / 172.3 s, measured RPM tick 19.1998 MHz) caught the modem writing EXACTLY ONE 9-record vote cycle in 13.26 s (normally ~350) at AP 6469.8→6483.1, while the RPM's OWN TIMESTAMP advanced 13.243 s across the gap (`ee55a433`→`fd8610b1` = 254 266 494 ticks ÷ 19.2 MHz, agreeing with the 13.264 s wall gap to 21 ms) ⇒ THE STALL IS UPSTREAM OF THE RPM — IN THE MODEM** — which is the live signature of the RE's `rpm.sync` (`0xc08bebd0`, RPM flush loops with no timeout) claim, and here it **recovered**; the AP saw the same event as a lost edge at `6469.070335`, `pc-ack timeouts` at `6469.330305`/`6478.290311`, and **`rtt = 911.398 ms`, the largest RTT of the whole S2 run**. ⚠ **NOT established:** the capture spans **one** stall, and **at fatal #9 the RPM did NOT go silent** (gaps 0.35–0.59 s — it was *busy* logging the SSR), so it is not shown to be the fatal's mechanism — **one sample each way; the capture is running across fatal #11.** **★ §8.16 ALSO ANSWERS THE HANDSHAKE QUESTION COMPARATIVELY, USING THE ANDROID DRIVER THAT HAD BEEN IN THE TREE ALL ALONG (`GitIgnore/android_kernel_zte_msm8916/drivers/soc/qcom/bam_dmux.c`, 2798 lines): the OpenWrt driver is ALSO SMSM-based (a self-correction — my "GPIO IRQ vs SMSM" reading was wrong), the clear-then-set ordering is equivalent, but the AP gives the modem's `pc-ack` line `250 ms` where Android's `UL_WAKEUP_TIMEOUT_MS` gives `2000 ms`, Android additionally serialises against the previous down-ack (`wait_for_ack`) and runs a `UL_TIMEOUT_DELAY=1000` recovery timer, and Android votes A2 power on demand where OpenWrt votes on every runtime-PM resume. The falsifier stated BEFORE checking is not falsified and the accounting is exact — `51 × 250 ms wait + 2 × 1000 ms wait = pc_timeout_count 53` (96.2 %), and the probe shows `pc`/`pc-ack` are AP-side SMSM outputs while `pc_irq`/`pc-ack_irq` are the modem's input lines, so the timeout means "the AP voted and the modem did not ack in 250 ms". Mechanism and measurement agree (giving up at 250 ms IS the 416/551/398/431 ms spike S2 measured). ⚠ **Scoped honestly: a LATENCY defect, not a stability one — it does not touch the 902 s fatal, which §8.15.4 proved is not PM-gated.** **★ §8.15.6 LOCATES THE STORM'S ONSET EXACTLY — AP `3477.894835`, THE BOOT'S FIRST LOST EDGE (dmesg ring verified NOT wrapped: `[ 0.000000]` present, 889 lines, crashes #1–#8 all there), 81.479 ms before crash #4: 0 lost edges in 3477 s, then 39 in 2600 s, P(0 in 504 │ p=0.05) ≈ 10⁻¹¹ — and it survives four SSRs.** **And the churn it rides on is ANDROID-PARITY** (Android: 918 collapses in 54 min = 1 per 3.5 s, 0 % loss), so **the churn is not the defect — the handshake failure rate is.** **★ §8.18 SCORES S2: 56 samples, 7 losses — five are SSR outages but TWO (`6368.31`, `6388.34`) have NO dmesg correlate at all** (nothing in `dmesg` 6360–6397 but the fatal), i.e. **silent data-plane loss while the modem was already in the cascade — a new, unexplained failure mode, n = 2.** **⚠ AND I CORRECTED MY OWN §8.17 "NEGATIVE": the storm's post-onset failure rate is ~15–40 %, NOT 5 %** — `40/766` is a **lifetime average diluted by the 3477 s before the storm existed**; and `dtimeout = 4` at samples `6119.3`/`6174.7` **is the maximum of the whole 33-sample series**, first reached in the two samples before fatal #8 (suggestive, not separable — trap 17). **`logroll.sh` earned its place on its first outing** (captured fatal #8's SSR userspace-side: 14 × `qcom-time-daemon` `[QMI-TIME] Modem SSR / Disconnect detected`, ModemManager reprobing, `netifd` link down → `[modem8]` `bearer18` → LTE up; the autosuspend churn resumes ~19 s after the SSR) — **but it found NO userspace trigger, so `modem-bearer-watchdog` acting in that window is now a negative observed with the instrument in place**; ⚠ **and it has a limitation now recorded as trap 18: the `logread` stream in the file is BATCHED, not interleaved (40 PMKNOB lines 12:56:37→12:59:24 precede kernel lines 12:56:38→12:59:00), so use the `[ uptime]` field for cross-stream timing, never the file order.** **✅ RESOLVED — THE CASCADE IS BOUNDED (3 BEATS) AND THE MODEM RETURNED TO THE 902 s CLOCK.** Fatal #11 fired at AP `7483.839750`, signature `lte_ml1_sleepmgr_stm.c:4054`, **903.332309 s** after fatal #10 — **predicted at AP ≈ 7482.8 s from fatal #10's modem boot, missed by 1.04 s** — and recovered in `0.119912 s`, AP survived. So the "902 s clock" is really **"902 s of modem uptime"**, which the next reload restarts, and a cascade is a **bounded departure** from it, not a permanent degradation. **⚠ AND MY FIRST READING OF THIS EXPERIMENT IS WITHDRAWN:** I stopped the traffic at uptime 6600.88, saw 0 fatals in 434.6 s, and called the cascade "traffic-dependent" (P ≈ 6 %) — but the cascade's **last fatal was #10 at AP 6580.507441, 20.4 s BEFORE the intervention**, so it was applied *after* the event it claimed to explain. **An intervention applied after the event cannot explain the event (trap 19).** What survives is still useful: across those 434.6 s the storm ran at **full rate** (resync +10 = 1/30.6 s, suspends +39 = 1/7.8 s, 26 %/18 % failure) with **no fatal** ⇒ **the storm is not sufficient for a fatal**, now with concurrent observation rather than a window that happened to contain one. **⚠ One thing remains undistinguishable:** the cascade's 4th beat was due at ≈ 6779.5 s and traffic was removed 174 s earlier, so "ended naturally" and "suppressed by the removal" are both consistent — distinguishing them needs a run where traffic is removed *before* a cascade starts. (fatal #5 = `a2_power.c:1189` at AP `4304.991306 s`, recovery `0.124531 s`; the four capture-off recoveries span `0.124531–0.130237 s`, spread 5.7 ms, against `0.824902–0.831559 s`, spread 6.7 ms, for the two with it ON — two non-overlapping populations 6.6× apart). **⚠ §8.13.4 also CORRECTS the A/B metric: score `SSR before shutdown`→`MBA booted`, NOT fatal→`is now up` — fatal #5's full fatal→up is `1.443682 s`, as long as the capture-ON recoveries, because the mpss load after `MBA booted` varies independently.** **AND §8.13.2 MAKES THAT A/B n = 2 ON A SECOND, DIFFERENT SIGNATURE.** Fatal #4 (AP **3477.969305 s**, `a2_power.c:2949`) reproduces fatal #3 to within 5 ms — **1 half-cycle pair, no `port failed halt`, 1 `MBA booted`, `SSR before shutdown`→`MBA booted` `0.130237 s`** against `0.824902`/`0.831559 s` for the two with the capture **on** — with boot-wide **5 fatals / 6 `MBA booted` (cold boot + one per recovery) / 2 `port failed halt`** (exactly the two capture-ON recoveries), and the ledger's **independent** `coredumps` column (a separate 10 s poller, not the driver) reading **17, 18, 18, 18, 18** (fatal #1 took it 16→17, fatal #2 17→18, **#3, #4 and #5 produced no dump at all**). Fatal #4's interval was **759.53 s** and fatal #5's **827.02 s**, off the 902.4 s clock — **expected, not an anomaly**, since Doc 162's taxonomy puts `a2_power` at 68.5–941.3 s and explicitly **not** on the clock (the ledger independently shows this signature at uptime 719.00 s and 896.52 s in two earlier boots). Also recorded: **the SSR ledger's column 1 is a "NOTICED" time** (poll + `sleep 10`), so it lags the true fatal by 0–10 s — measured `919.62`/`1825.87` against the real dmesg lines `[913.640795]`/`[1816.046634]`, so the ledger-derived gap **906.25 s** overstated the true **902.405839 s** by 3.85 s (the difference of two lags); **take period samples from `dmesg`, never from the ledger**. **AND §8.14 WITHDRAWS §8.10's VERDICT AND REFRAMES §8.11's RATE — THE HOST KERNEL LOG SHOWS ERROR IN BOTH DIRECTIONS.** §8.10's "reset that survived 823" was **my own physical USB-hub installation**: the host log shows the dongle disconnecting from port `1-1` at `17:03:07`, a **`USB2.0 HUB` (214b:7250) appearing on `1-1` for the first time at `17:03:13`** with 4 ports detected, and the dongle re-appearing at `1-1.2` at `17:03:41` — a hub cannot be added to a port without removing the device on it, and the 29 s outage §8.10 measured **is** that unplug/replug window. §8.10's evidence (empty pstore, no 4th coredump, no ledger row) is **exactly what a power cycle produces**, so **823 is substantially rehabilitated**. The 16:40–16:46 "boot loop" (uptime `2427 → 46 → 38 → 79`) is **entangled with a host USB-controller storm** — the xHCI platform driver removed/re-probed **24 times on Sep 21, 18 of them in the 16h hour**, each deregistering and rebuilding both root hubs. **But the DIRECTION IS NOT ESTABLISHED and I retracted my own "host-caused" label:** the ordering is mixed — at `10:31:46`, `15:06:03`, `16:40:30`, `16:41:14`, `16:42:11` the **dongle disconnects first** (controller ~1 s later), while at `15:59:28` the controller is removed with **no** preceding device disconnect. The outage durations are **bimodal**: 16 short (5–61 s) plus **926 s** (15:44:42, `error -71` ×6 + `attempt power cycle` — the gadget came back broken) and **1641 s** (14:39:38) — and the **`procd`-kicked PON watchdog proves those two are not hangs at all** (a stopped kernel resets in 30 s; a broken *network path* never does), so they are **network-path failures with a live AP**. **7 of 19 outages are therefore not clean AP-hang data points** (1 certainly my hub install, 2 certainly not hangs by duration, 4 entangled with the controller storm); the other 12 are the only candidates. Conversely the host saw **39** dongle re-enumerations against ~19 AP uptime-decreases, so **re-enumeration ≠ AP reboot**. **⇒ an AP-side uptime decrease proves the dongle restarted, NOT that it restarted ITSELF — the host log is now a required companion instrument for every reboot attributed to the AP, and §8.13's bar counts a reboot as a failure only if the host log shows no host cause.** Evidence: `evidence/170_two_fixes_for_the_smd_poll_uaf/`. **★ AND §8.15 FINDS THE FIRST AP-SIDE OBSERVABLE THAT PRECEDES A MODEM FATAL: every `a2_power.c:2949` ever observed (3 of 3, in three DIFFERENT boots) is preceded 74–86 ms earlier by the patch-808 bam-dmux RX-watchdog "lost edge" PC resync.** Found by accident in fatal #4's window — one extra line, `RX watchdog: PC line asserted while pc_state=0 (lost edge), resyncing` at `3477.894835`, **74.470 ms** before the fatal at `3477.969305`, with `pc_resync_count` going **0 → 1** for the boot — and the same pair exists in two earlier boots: **85.797 ms** (boot P, `714.454106`→`714.539903`) and **73.955 ms** (boot Q, `886.743235`→`886.817190`). Mean **78.074 ms**, spread **11.842 ms**, the two closest **0.515 ms** apart. **BUT IT IS NOT SUFFICIENT — 3 of 8:** eight resyncs were observed in total and five had **no** such fatal after them (boot R's resync was followed by a *different* fatal 73.47 s later; boot S's four had none before its log ended 119 s later), so **`a2_power.c:2949` ⇒ resync is 3/3 while resync ⇒ `a2_power.c:2949` is 3/15** (§8.15.1/§8.15.2 scored the pre-registered P2: 15 resyncs pooled across five boots, 3 hits — and the P/Q/R counts come from a partial log, so 3/15 is a floor; the P/Q/R/Y counts are 1/1, 1/1, 1/0, 4/0, 8/1) — a **5×** gap, and the second number is the one that decides whether a fix is possible. **AND IT DOES NOT GENERALISE — the tight lag is specific to `a2_power.c:2949`:** fatal #5 (`a2_power.c:1189`) has **no resync within 100 ms** (nearest **80.477042 s** earlier), so sleepmgr 0/3, `a2_power.c:2949` 3/3, `a2_power.c:1189` 0/1. **AND THE RATE IS NOT STATIONARY: 1 resync in the first 3478 s, then 5 in 145 s from 4079.094133 with no AP-side change — and P3 IS SCORED WITH ITS SIGNATURE PREDICTION FAILED** (it named a sleepmgr at ~4380 s; the fatal was an `a2_power.c:1189` at 4304.99 s, and the storm **continued 47.5 s past** the fatal, so the storm **brackets** it). **★ THE REFRAME: the storm is BIDIRECTIONAL PC-handshake failure** — `lost edge` = the modem asserted and the AP missed the edge; `pc-ack timeout` = the AP voted and the modem missed it — **a minutes-long regime measurable from two EXISTING counters**, so **D2/D3 should be scored on the storm, not on a single resync**. Also: `pc_timeout_count` counts **the AP waiting for the modem** (250 ms + 1000 ms waits in `bam_dmux_runtime_resume()`), i.e. the **mirror** of the "modem waits for the AP's ACK" reading ⇒ **it cannot discriminate it; do not design around it**; the post-fatal `pc-ack timeout` lines are **recovery artefacts** (+0.431112 / +0.581419 / +0.435022 s after fatals #1/#2/#3, none after #4), not precursors. **★ AND §8.15.3 THEN DEMOTES THE PRECURSOR STORY ITSELF: the storm ran 8+ MINUTES PAST the fatal with NO second fatal and the data plane fully working** (`pc_resync_count` **15 → 18 → 20** across uptimes 4710/4814/4853 = **1 resync per 36.3 s sustained**, fatals still **5**, `ping -c 4 -I wwan0 8.8.8.8` **4/4, 0 % loss**) — **15 resyncs, ONE of which preceded a fatal** ⇒ reading **(A)** ("resync is a poison pill") is **disfavoured**; the storm is a **REGIME, not an event**, and **(B)/(C)** fit better. **The two directions account for the boot exactly, no residue:** 15 lost edges = 1 fatal-preceding + 5 paired + 9 unpaired; 14 `pc-ack timeout`s = 3 recovery artefacts + 5 paired + 6 unpaired. **And the five pairings sit ~184 ms apart (166.797–210.076 ms, mean 184.080) but the mechanism does NOT close arithmetically, which is itself the finding:** the only site printing `modem pc-ack timeout during resume` emits it **exactly ~250 ms after that resume's own vote**, so a line at **T+184 ms** implies a vote at **T−66 ms — *before* the resync** — and the resync's own `complete_all(&dmux->pc_ack_completion)` **must** then have satisfied that waiter; so either a later `reinit_completion()` (exactly two: `bam_dmux_power_on()` `:178`, `bam_dmux_runtime_resume()` `:1421`) undoes it, or the coupling runs through the **modem's ACK timing** ⇒ **D1 is REDEFINED: log the `reinit_completion()` / `complete_all()` / pc_ack-IRQ ordering**, not "either side of the two modem-visible actions". **No external trigger:** the AP's `dmesg` has **zero lines** in the 589 s before the onset and the host's `journalctl -k` is **silent** (last entry `17:34:12` vs an onset of ~`18:11:20`, with a **positive control** proving the query works) ⇒ AP/modem-internal, with §8.14's lesson applied *before* blaming anything. **The runtime-PM rate is UNCHANGED** across the onset (0.132/s boot average vs 0.127–0.130/s during) ⇒ not "more churn", just the same churn failing its handshake. **Correction: `pm_suspend_attempts − pm_suspend_completions` (7 → 10 → 11) is ACCOUNTING, not a defect** — `pm_suspend_start_ns` is cleared by `bam_dmux_runtime_resume()` (`:1417`) **and by the resync handler** (`:797`), so a suspend pre-empted by either can never increment the completion counter; it is the count of suspend/resume races (11 of 623 = 1.8 %), **withdrawn as an anomaly**. **★ AND THE STORM IS AP-RUNTIME-PM-GATED — S1 IS DONE, P5 IS CONFIRMED, AND THE SAME RUN REFUTES THE MITIGATION FOR THE FATALS.** A lost edge needs `pc_state == 0` **and** the line asserted, so it can only happen on the way **out of** a quiesced state; **S1 (600 consecutive 1 Hz pings, both counters sampled once a second) is a clean A/B/A inside ONE run** — pre-S1 **1 resync per 36.3 s** with **0.132 suspends/s** and `pc_state=0` in 6 of 10 samples; during **364.34 s of traffic `pc_resync_count` AND `pm_suspend_attempts` were BOTH FROZEN** (**0 resyncs against 10.0 expected**, p = 4.5 × 10⁻⁵; 0 suspends against 48.1 expected; `pc_state=1` in all 365 samples; **364/364 pings, 0 % loss**); after the traffic stopped (fatal #6's SSR killed the ping at `seq=363`, `sendto: Network unreachable`, **no summary**) the storm **returned at the pre-S1 rate** (1 per 42.0 s, 0.126 suspends/s). **So the storm, the DATA STALL and the PC-handshake failures are ONE defect family — the idle→active transition — and idle-avoidance is a candidate mitigation for the STORM.** ⚠ **But fatal #6 fired at AP `5208.721361 s` INSIDE the suppression window with the modem never suspended, on the clock (`lte_ml1_sleepmgr_stm.c:4054`, `902.286373 s` of modem uptime, 19.4 ms from Doc 162's 902.267 s) ⇒ the FATAL is NOT PM-gated and idle-avoidance does not suppress it — measured, not inferred.** ⚠ **And the trap that came with it: the tail of `/overlay/s1.track` reads `resync=27 … susp=673` and looks like a failed hypothesis — it is the post-traffic CONTROL arm; score from the instrument's health, not its tail.** **Next: S2** (`/overlay/s2.sh`, one ping every 15 s × 80 with the counters either side; **RUN AND SCORED, §8.18 — 56 samples, 7 losses, all five SSR-related ones accounted for and TWO with no dmesg correlate at all; patch-812 regression check PASS with `tx_defer_wiped 0` and 751/771 preserved**) — one ping every 15 s × 80 with the counters either side; **P6:** pings whose wake missed the edge show an RTT spike up to the 100 ms watchdog period; **P6-FALSIFIER:** no RTT difference; S2 also regression-tests patch 812 at the idle period Doc 156 measured as broken. **Why an AP-side action is plausible at all:** the resync is **patch-808 code** (not upstream) and **two of its four actions are modem-visible** — `bam_dmux_pm_restart()` (powers the BAM down and up) and `bam_dmux_pc_ack()` (the SMSM power-collapse ACK). **Three readings fit and the data does not choose between them:** (A) the resync is a poison pill (gap should be a modem reaction *constant*); (B) common cause — the modem's PC state machine had already desynchronised, so the resync is a symptom; (C) the AP's *missed edge* is the cause and the modem's ACK timeout is what asserts (gap = `modem_timeout − edge_delay`, so it **should** vary). **The 100 ms watchdog period makes C testable** — the AP's detection delay is uniform on [0,100] ms, so three gaps landing inside 11.8 ms is a ~4 % coincidence, which **weakly favours A and is far too weak to act on**; it is recorded, not concluded. **THE DISCRIMINATOR NEEDS NO KERNEL FLASH: `qcom_bam_dmux` is a LOADABLE MODULE** (`/lib/modules/6.12.94/qcom_bam_dmux.ko`, 241232 B, `lsmod` refcount 0) — **D1** log either side of each action (zero behaviour change); **D2** make the resync **skip `bam_dmux_pc_ack()`** and count the signature per resync; **D3** skip `bam_dmux_pm_restart()` instead; D2/D3 are **probes, not fixes**, and **must not run inside the open §8.13 window** because they change which fatals occur. Passive pre-registration on that window: **P1** the next `a2_power.c:2949` will have a resync within 100 ms in front of it; **P2** keep counting resyncs with *no* fatal after them. **FALSIFIER:** an `a2_power.c:2949` with **no** resync in the preceding 100 ms breaks the association entirely. **It explains neither the `lte_ml1_sleepmgr_stm.c:4054` nor the `a2_power.c:1189` fatals — neither has ever been seen with a resync in front of it.** *(Provenance trap, recorded because it nearly inflated the sample: the P and Q samples come from `/overlay/q6trace.log`, an **append-across-boots** file where `grep -c "a2_power.c:2949"` returns **5** for **3 distinct events**; each was instead cross-checked against the independent `ssr_ledger.csv` — **6 of 6 match**, 0–10 s noticed-lag — and the boots separated by their own `n = 1,2,3` sequences and the ledger's monotonic `coredumps` column, 11→13 then 14→16.)* **★ AND §8.13.4 + §8.15.4–§8.15.5 CLOSE THE ROUND: fatal #6 makes the coredump-off A/B n = 4 (4 of 20 scored, 0 reboots) on a FOURTH signature, and it forces a metric correction — score `SSR before shutdown`→`MBA booted`, NOT fatal→`is now up`, because fatal #5's full fatal→up is `1.443682 s` and would read as a failed removal.** **S1 is scored and P5 is CONFIRMED as a clean A/B/A inside ONE run:** 1 Hz traffic froze `pc_resync_count` AND `pm_suspend_attempts` for **364.34 s** (**0 resyncs against 10.0 expected**, 0 suspends against 48.1, `pc_state=1` in all 365 samples, **364/364 pings, 0 % loss**), and the storm returned at the pre-S1 rate the moment the traffic stopped. **So the STORM, the DATA STALL and the PC-handshake failures are one defect family — the idle→active transition — and the storm is AP-runtime-PM-gated.** **⚠ BUT THE SAME RUN REFUTES THE MITIGATION FOR THE FATALS: fatal #6 fired at AP `5208.721361 s` INSIDE the suppression window with the modem never suspended, on the clock (`lte_ml1_sleepmgr_stm.c:4054`, `902.286373 s` of modem uptime, 19.4 ms from Doc 162's figure) ⇒ the FATAL is NOT PM-gated, measured rather than inferred.** **⚠ AND §8.15.5 WITHDRAWS §8.15.3 §5's "NO EXTERNAL TRIGGER" TO "OPEN": both of its negatives are true (empty `dmesg` for 589 s, silent host log) and NEITHER IS SUFFICIENT, because `/usr/sbin/modem-bearer-watchdog` ENFORCES `power/control=auto` and `autosuspend_delay_ms=1000` on the bam-dmux device EVERY 10 s via sysfs WITH NO LOG LINE — the very knob that controls the storm's mechanism — and can also do `wds-go-dormant`, a bearer down/up (changing the IP) and a FULL REMOTEPROC SSR (`recovery.ssr_enabled=1`); and `logread` is a ~15-minute ring buffer that had already rotated past the onset.** **The missing instrument is deployed: `/overlay/logroll.sh` (running, in `/etc/rc.local`) puts the userspace log, the kernel log and the PM state machine on ONE timeline — it answers the NEXT onset, not this one.** **And S2's first data shows the storm's cost is LATENCY, not loss (a `551.417 ms` first packet against a ~41–43 ms baseline, on the ping whose `pc_timeout_count` advanced) ⇒ P6's predicate must be the TIMEOUT delta, not the resync delta.** Evidence: `evidence/170_two_fixes_for_the_smd_poll_uaf/V_coredump_off_fatal4_n2_and_the_lost_edge_precursor.txt`. |

## Sound but narrow (accurate, subordinate scope)

`Stock_Android_Analysis/`: `23`, `33`, `37`, `53`, `54`, `59`, `65`, `70`, `72`,
`GHIDRA_ANALYSIS_TARGETS.md`, `STOCK_ANDROID_GROUND_TRUTH_REPORT.md`.
`Stock_Android_Live/03_BAM_DMUX_DIFFERENTIAL.md` (accurate comparison; its "most likely
cause" pick is an explicitly-flagged unconfirmed hypothesis).
`74`, `76`, `114`, `135`, `138`.

## Historical / superseded — a log, not findings

`89`–`132` are the WTR1605→UFI001B transplant patch logs and the earlier stability
reports. The transplant objective is void (Docs 142/143/145). They are kept as a
record. **Do not treat any of them as a current finding.**
`141` carries a caution banner (its soak ran the stock baseband, corrected by Doc 144 §7).

## Quarantined — do not cite

29 files in `_QUARANTINE/`, each with the false claim and its counter-evidence listed
in `_QUARANTINE/README.md`. Categories: fabricated patch recipes; the fixed-900 s-timer
law; the backwards autosuspend conclusion; fast-dormancy-as-cause; false
"deployed and verified" claims; and false RF/attach claims on a modem with no RF driver.
