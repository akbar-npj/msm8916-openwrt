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
mechanisms with different periods competing inside one boot**) — see the sections
below.
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
