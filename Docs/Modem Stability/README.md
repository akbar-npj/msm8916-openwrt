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
patch 810**, verified against the disassembly of the very module that crashed) — see the sections
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
    and the `:597` defer branch is never taken. The soak must **idle first, then burst**; with that
    pattern it produced **23 collapse/wake cycles in ~140 s** and **0 oopses**.

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
| `153_FATAL14_CAPTURED_DEVMEM_IMPOSSIBLE_AND_WATCHER_AUTOSTART.md` | **Doc 152's watcher fix confirmed by capturing fatals #14 AND #15** (each 85 398 475 B, md5 verified) — the `test -s` guard was the whole cause. **The coredump is created only after `rproc_stop()` returns**, so a hanging fatal (fatal #13) is *structurally uncapturable* — the dump is never created. **`/dev/mem` cannot read the mpss region by any method** (read()→EFAULT, mmap()→SIGBUS; source-verified), so live `devmem` observation is impossible and a `nomap`-capable kernel module is the next instrument. Establishes **`dump_va == AP physical` for mpss**; the watcher now autostarts from `/etc/rc.local` (and busybox `start-stop-daemon -S` is **not idempotent** for a script); **the deployed baseband is verified byte-identical to the stock HMU05 dump** (21 modem + 9 WCNSS segments); the `rpm` LPR `+0x18` counter spans **375–1064** (not monotonic either way) and word B **advances 11 per period at 4/4 within-boot but does not reset across a reboot** |
| `154_BAM_DMUX_TX_WAKEUP_NULL_DEREF_AND_FAILED_MODEM_RESTART.md` | **A second, distinct `bam_dmux` NULL deref** — `bam_dmux_tx_wakeup_work+0xc8` → `bam_dmux_skb_dma_submit_tx+0x2c`, faulting load `ldr w22, [x2, #0x70]` with `x2 = NULL` (= `skb_dma->skb`; `skb->len` at 0x70 BTF-verified). Different function/caller/offset from Doc 147's oops. Separately: **the modem failed to restart after fatal #15** (`port failed halt`, stall at `loading mpss`, `state = offline`, reboot required); and the Doc 153 rc.local autostart is verified at a real boot. **Its §5.3 mechanism ("neither `power_off` nor `pm_restart` takes `state_lock`") is WRONG and is corrected by Doc 155 — the sweep is always under `state_lock`; the hole is that `start_xmit` sets the deferred bit without it.** Its §4 decode is correct and stands |
| `155_TX_SWEEP_RACE_PATCH_810_AND_DOC154_CORRECTION.md` | **The TX-sweep race, corrected and FIXED.** Corrects Doc 154 §5.3: the sweep (`bam_dmux_free_skbs()` from `power_off()`/`pm_restart()`) is **always** under `state_lock` (`pc_irq` `:1701`, rx-watchdog resync `:1191`, teardown `:2059`, powerup `:2213`, remove `:2292`), so the `:654` comment is true. The real hole is the **producer**: `bam_dmux_netdev_start_xmit()` is atomic context and sets `tx_deferred_skb` at `:599` **without** the lock, so it can re-arm a bit for a slot a concurrent sweep already freed. **Patch 810** (`msm89xx/patches/810-bam-dmux-tx-sweep-race.patch`) makes the consumers tolerant: NULL-guard `bam_dmux_skb_dma_map()`/`bam_dmux_skb_dma_submit_tx()`, **drop** stale bits in the `tx_wakeup_work` loop, and stop `start_xmit`'s `drop:` from double-freeing/double-putting. **`cancel_work_sync(&tx_wakeup_work)` there would deadlock.** Verified in the crashing object (md5 `3b693188…`): `pc` `b30−b04 = 0x2c`, `lr` `13b0−12e8 = 0xc8`, faulting word `b9407056`. Soak caveat: **1 Hz traffic keeps the modem awake and does not exercise the path** — idle-then-burst gives 23 collapse cycles and 0 oops |

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
