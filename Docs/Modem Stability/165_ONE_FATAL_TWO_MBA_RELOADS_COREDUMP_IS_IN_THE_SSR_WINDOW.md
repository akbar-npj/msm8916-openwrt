# 165 — One fatal, two MBA reloads: the coredump reader is *inside* the SSR window

**Date:** 2026-09-21
**Subject:** AP-side kernel diagnostic. **No modem firmware, no WCNSS firmware, no DTS and no
bootloader byte was changed.** The only build input is Doc 164's temporary patch
`msm89xx/patches/817-q6v5-ssr-window-trace.patch`, deployed as the loadable module
`kmod-qcom-rproc-modem` (no kernel flash).
**Result:** the first instrumented fatal landed at AP 937.786 s and produced **46 trace lines — four
half-cycles, two full `01..23` cycles, not one.** The extra cycle is not a retry: it is the
**remoteproc coredump reader**, which `rproc_boot_recovery()` calls *between* `rproc_stop()` and
`rproc_start()`, and which forces a complete extra MBA reclaim + reload plus an **85 398 475 B copy
that stalls the recovery path for 1.064 s**. And the Doc 159 window turns out to be **86.2 % a single
*untraced* `q6v5_rmb_mba_wait()`** — all 23 instrumented steps together are 5.888 ms, **13.4 %** of
the window they were built to observe.

---

## 0. SOP compliance

| SOP step | Status |
|---|---|
| No baseband change; no firmware write | **Honoured.** Zero firmware bytes touched. The only device-side writes were: kill four leaked/stuck probe processes (§9.2), and read. Nothing was deployed this round. |
| Dual-firmware comparative protocol | **Not applicable, and deliberately so** — same reasoning as Doc 164 §0. This is AP-side kernel instrumentation; there is no Android counterpart to compare against, and no baseband hypothesis is advanced. The comparative protocol exists to stop blind *baseband* patching. |
| Verify against ground truth before acting | **Honoured, and it is what produced this document.** The call chain in §3.1 was read from `remoteproc_core.c` and `qcom_q6v5_mss.c` in the **pristine** tree (`GitIgnore/compare/…`), not inferred. The "read the definition, not the name" rule (memory `feedback_read_the_definition_not_the_name`) is exactly what caught that `q6v5_mba_load()` has **two** callers — the finding would have been impossible by name-reading. |
| Hash the artifact, verify it in situ | **Honoured.** The resident module is Doc 164's `79e7a858d41b6c9f7edf8b26b90c77f8`; the trace it produced is internally consistent with that patch (46 lines = 2 × 23, §5). |
| Never classify a fatal by its `file:line` | **Honoured in the Doc 163 form** — the string names *which site tripped*, not the root cause. This fatal's `a2_power.c:1189` is used only as an identity label. |
| Do not treat corpus docs as fact | **Honoured — and this round is the strongest case yet.** Doc 164 §4's own figure ("that span contains all 23 trace points plus the MBA firmware load") was a reasonable inference from a capture *without* the instrument. The instrument falsified it (§4). Doc 164 §9's pre-registration is left standing as written, with its coverage gap recorded rather than patched (§4.1). |
| Record what was done, the result, and what is next | This document, plus §10. |

---

## 1. What Doc 164 left open

Doc 164 §12 item 1: *"Watch the first fatal→recovery cycle and confirm all 23 trace steps appear in
the ring, in order, with the console suppressed. This validates the instrument before the hang can
test it."*

That confirmation happened. It produced a surprise instead of a validation.

Doc 164 §2 modelled the window as **one** pass:

> the tail of `q6v5_mba_reclaim()` (called from `q6v5_stop()`, `:1615`) followed by the head of
> `q6v5_mba_load()` (called from `q6v5_start()`, `:1589`)

and §4 measured it as a single 43.726–46.944 ms span. Both are true *on a boot with no coredump*.
They are not what happens on a fatal.

---

## 2. The fatal

```
[  936.260028] bam-dmux 4080000.remoteproc:bam-dmux: bam_dmux: modem pc-ack timeout during resume
[  937.786016] qcom-q6v5-mss 4080000.remoteproc: fatal error received: a2_power.c:1189:
[  937.786149] remoteproc remoteproc0: crash detected in 4080000.remoteproc: type fatal error
[  937.793771] remoteproc remoteproc0: handling crash #1 in 4080000.remoteproc
[  937.801171] remoteproc remoteproc0: recovering 4080000.remoteproc
```

* **Signature `a2_power.c:1189`** — the "long" fatal of Doc 164 §6's 8/8 table. This boot's first
  fatal is at AP 937.786 s, in the predicted ≈940 s band.
* **`handling crash #1`** — first fatal of this boot, so the sequence is not contaminated by an
  earlier recovery.
* **`pc-ack timeout during resume` 1.5 s earlier is a symptom, not a precursor** (§8.1).

**The recovery, end to end:**

| AP uptime | event |
|---|---|
| 937.786016 | `fatal error received: a2_power.c:1189:` |
| 937.808624 | `bam_dmux: SSR before shutdown: scheduling teardown work` |
| 937.810877 / 937.813998 / 937.814998 | `wwan0at0` / `wwan0at1` / `wwan0qmi0` disconnected |
| 937.821257 | `stopped remote processor 4080000.remoteproc` |
| 938.919368 | **`port failed halt`** |
| 938.963336 | `MBA booted without debug policy, loading mpss` |
| 939.667333 | `remote processor 4080000.remoteproc is now up` |
| 940.294019–940.301932 | `wwan0at0` / `wwan0at1` / `wwan0qmi0` **attached** |
| 940.313325 | `SSR powerup: modem pc_state=1 (waited 580 ms)` |
| 940.314932 | `SSR powerup: successfully reinitialized BAM channels and rings` |
| 940.315123–… | `received CMD_OPEN (1) on channel 0..7` |

**Outcome: a clean recovery.** Total outage ≈ 2.5 s (fatal → rproc up). Patch 814's powerup poll
succeeded on the first attempt (`waited 580 ms`, **`retries: 0`** — the retry path is *still*
unvalidated on a natural trigger, Doc 162 §8's open item). Data plane verified afterwards:
`10.93.59.183/28` on `wwan0`, default route present, **3/3 ICMP, 0 % loss**.

The coredump watcher captured the dump: `devcd1` at 939.34 s → **85 398 475 B**, md5
`b8d30ffbbfa04f0e751fb0508e362c26`.

---

## 3. The finding: a fatal produces **two** `01..23` cycles

### 3.1 The call chain, read from source

`rproc_trigger_recovery()` → `rproc_boot_recovery()` (`remoteproc_core.c:1791`):

```c
static int rproc_boot_recovery(struct rproc *rproc)
{
	ret = rproc_stop(rproc, true);
	if (ret)
		return ret;

	/* generate coredump */
	rproc->ops->coredump(rproc);          /* <-- BETWEEN stop and start */

	/* load firmware */
	ret = request_firmware(&firmware_p, rproc->firmware, dev);
	...
	ret = rproc_start(rproc, firmware_p); /* <-- the real restart */
```

`rproc->ops->coredump` defaults to `rproc_coredump()` (`remoteproc_core.c:2427`), which walks every
registered dump segment and calls `rproc_copy_segment()` → **`qcom_q6v5_dump_segment()`**.

And `qcom_q6v5_dump_segment()` calls `q6v5_reload_mba()` on its **first** segment and
`q6v5_mba_reclaim()` on its **last**. Meanwhile `q6v5_mba_load()` — the function patch 817 traces —
has **two** callers:

```
$ grep -n "q6v5_mba_load(qproc)\|q6v5_reload_mba(rproc)" qcom_q6v5_mss.c
1323:	ret = q6v5_mba_load(qproc);        <- q6v5_reload_mba()
1585:	ret = q6v5_mba_load(qproc);        <- q6v5_start()
1544:		ret = q6v5_reload_mba(rproc);  <- qcom_q6v5_dump_segment()
```

So the execution order for **one fatal** is:

```
rproc_stop()                    -> q6v5_stop()  -> q6v5_mba_reclaim()   [01-09]
rproc->ops->coredump()          -> rproc_coredump()
    -> rproc_copy_segment()     -> qcom_q6v5_dump_segment()
        first segment           -> q6v5_reload_mba() -> q6v5_mba_load()  [10-23]
        ... ~85 MB copied here (synchronous, RPROC_COREDUMP_ENABLED) ...
        last segment            -> q6v5_mba_reclaim()                    [01-09]
request_firmware()              (cached by now)
rproc_start()                   -> q6v5_start() -> q6v5_mba_load()       [10-23]
                                   ... then "MBA booted"
```

**The half-cycle sequence is `01-09, 10-23, 01-09, 10-23` — not `01-09, 10-23`.** The `dump_mba_loaded`
flag is what forces the extra pair: `q6v5_mba_reclaim()` clears it (`:1246`), the dump's first
segment therefore sees it clear and calls `q6v5_reload_mba()`, and `q6v5_mba_load()` sets it again
(`:1189`) so the dump's last segment knows to release the MBA.

### 3.2 The trace, attributed

`evidence/164_q6v5_ssr_window_trace/q6trace_analyse.py` splits the flat trace into half-cycles by
step number and attributes each to its caller. Output in `window_analysis.txt`:

| half | steps | span | caller |
|---|---|---|---|
| 1 | 01–09 | **0.264 ms** | `rproc_stop` → `q6v5_stop` → `q6v5_mba_reclaim` |
| — | 09→10 | **33.071 ms** | `stopped remote processor`; `rproc_coredump`'s `vmalloc(85 MB)`; cold `request_firmware` + `q6v5_load` |
| 2 | 10–23 | **2.283 ms** | **`coredump#1` → `q6v5_reload_mba` → `q6v5_mba_load`** |
| — | 23→01 | **1064.362 ms** | **the 85 MB segment copy** (§3.4) |
| 3 | 01–09 | **0.610 ms** | **`coredump#last` → `q6v5_mba_reclaim`** |
| — | 09→10 | **2.931 ms** | `rproc_coredump` returns; `dev_coredumpv`; `request_firmware` (cached); `rproc_start` |
| 4 | 10–23 | **2.347 ms** | **`rproc_start` → `q6v5_start` → `q6v5_mba_load`** — the real restart |

Two things follow immediately.

1. **Half 2 is not a recovery.** It runs *while the modem is still down*, purely so the coredump can
   read its memory. Anyone reading the trace without this document would count it as a recovery
   attempt — and would then be unable to explain why `MBA booted` appears only once.
2. **`port failed halt` belongs to half 3, not to half 4.** It is printed by
   `q6v5proc_halt_axi_port()` at the **top** of `q6v5_mba_reclaim()` — i.e. *before* step 01 — and the
   measured gap `port failed halt` → step 01 is **0.180 ms**. So the message Doc 159 anchored on is
   emitted by the **coredump's** reclaim, not by the restart's.

### 3.3 Why the coredump forces a reload at all

`qcom_q6v5_dump_segment()` must read modem memory that TrustZone currently assigns to the modem
(Doc 158). To do that it has to boot the MBA, take ownership of mpss back, and then give it back:

```c
	if (!qproc->dump_mba_loaded) {
		ret = q6v5_reload_mba(rproc);                    /* boots the MBA */
		if (!ret)
			ret = q6v5_xfer_mem_ownership(qproc, &qproc->mpss_perm,
						      true, false, ...);  /* HLOS takes mpss */
	}
	...
	if (qproc->current_dump_size == qproc->total_dump_size) {
		if (qproc->dump_mba_loaded) {
			q6v5_xfer_mem_ownership(qproc, &qproc->mpss_perm, false, true, ...);
			q6v5_mba_reclaim(qproc);                 /* gives it back */
		}
	}
```

`total_dump_size` is the sum of the mpss ELF `p_memsz` values, computed once in
`qcom_q6v5_register_dump_segments()` (`.parse_fw`), which is why every dump is exactly 85 398 475 B
(confirmed across two boots: the previous boot's five dumps and this one's).

### 3.4 The 1.064 s stall — and it is on the recovery critical path

`rproc->dump_conf` is `RPROC_COREDUMP_ENABLED` here, so `rproc_coredump()` copies **all** segments
into a `vmalloc`'d buffer *before* handing it to `dev_coredumpv()`:

```c
	if (dump_conf == RPROC_COREDUMP_ENABLED)
		rproc_copy_segment(rproc, data + offset, segment, 0, segment->size);
	...
	if (dump_conf == RPROC_COREDUMP_ENABLED) {
		dev_coredumpv(&rproc->dev, data, data_size, GFP_KERNEL);
		return;
	}
```

The measured cost, step 23 of half 2 → step 01 of half 3:

```
  938.919368 - 937.855186 = 1.064182 s   for 85 398 475 B  =  80.2 MB/s
```

corroborated by the watcher (`captured 85398475 bytes from devcd1`) and by `devcd1` appearing at
939.34 s, i.e. *after* the copy, not during it.

**That last observation is also the disproof of the other mode.** `rproc_coredump()` has two paths:

| mode | where the 85 MB is copied | when `devcd` appears |
|---|---|---|
| `RPROC_COREDUMP_ENABLED` (buffered) | **synchronously inside `rproc_coredump()`**, via `rproc_copy_segment()` | **after** the copies (`dev_coredumpv`) |
| `RPROC_COREDUMP_INLINE` | lazily, by `rproc_coredump_read()`, as userspace reads | **before**, and `rproc_coredump()` then blocks in `wait_for_completion()` until the reader finishes |

INLINE is ruled out by two independent numbers: `devcd1` appeared at **939.34 s** (after the gap,
not at ~937.86 s), and `MBA booted` at **938.96 s** — which would be *before* the userspace reader
finished (its 85 MB write to `/overlay` took 945.24 − 939.35 = **5.89 s**, 14.5 MB/s). Had the mode
been INLINE, the modem could not have restarted until ~945 s. **The mode is buffered, and the stall
is in-kernel.**

**This is the part worth carrying forward.** Doc 153 established "the coredump is created only after
`rproc_stop()` returns", and Doc 152/153 treated that as a *limitation* (a fatal that hangs inside
`rproc_stop()` is uncapturable). It is also a **perturbation**: the coredump is not an observer of
the recovery, it is a **1.06 s step inside it**, executed while the modem is held down, and it adds a
complete extra MBA boot/reclaim pair to every single fatal. Doc 159's hang has only ever been
observed *with the watcher running*; §9.1 records the open question.

There is a second-order consequence, visible in the same capture: **the stall is long enough for the
AP's own stack to notice.**

```
[  937.959319] bam-dmux ...: bam_dmux: refusing to queue command while modem is collapsed
```

That line falls inside the 1.064 s gap — a transmit attempt during the dump copy.

---

## 4. The window, re-decomposed: 13.4 % traced, 86.2 % one bounded wait

The Doc 159 window is defined as `port failed halt` → `MBA booted`. Decomposed:

| segment | ms | share | traced? |
|---|---|---|---|
| `port failed halt` → step 01 (the tail of the `halt_axi_port` calls) | **0.180** | 0.4 % | partly (the message is the anchor) |
| step 01 → step 23 — **all 23 instrumented steps** | **5.888** | **13.4 %** | **yes** |
| step 23 → `MBA booted` — `q6v5_rmb_mba_wait(qproc, 0, 5000)` | **37.900** | **86.2 %** | **no** |
| **total** | **43.968** | 100 % | |

Of the 5.888 traced ms, **2.931 ms is the `09→10` remoteproc-core hop**, not driver work — so the
actual register/SCM/regulator/clock work patch 817 instruments totals **≈ 3.0 ms**.

**Doc 164 §4's sentence "that span contains all 23 trace points plus the MBA firmware load" is
literally true but materially misleading.** The MBA *wait* is **6.4×** the trace it brackets. The
instrument's blind spot is seven times larger than its coverage.

### 4.1 What this does to Doc 164 §9's pre-registration

Doc 164 §9 pre-registered, before the soak:

> the **last `q6v5-trace:` line** will be **08, 20 or 21** — the three `q6v5_xfer_mem_ownership()`
> TrustZone transfers. […] If the last line is instead a regulator or clock step, the SCM hypothesis
> is **falsified**.

The measurement adds a **third outcome the prediction did not enumerate**: a hang inside the
untraced `q6v5_rmb_mba_wait()` would leave the last line at **step 23** (`q6v5proc_reset`), which is
neither 08/20/21 nor a regulator/clock step.

**The prediction is left standing exactly as written** — it was registered before the data, and
rewriting it now would destroy the only property that makes it worth anything. What is recorded here
is its **coverage gap**: it enumerated sites within the traced 13.4 % and did not state what a hang
in the other 86.2 % would look like. When the hang lands, the last line should be read against the
full candidate list below, not only against §9's three.

### 4.2 Is `q6v5_rmb_mba_wait()` hang-capable?

Read from source, it is **bounded**:

```c
	timeout = jiffies + msecs_to_jiffies(ms);      /* ms = 5000 */
	for (;;) {
		val = readl(qproc->rmb_base + RMB_MBA_STATUS_REG);
		if (val < 0) break;
		if (!status && val) break;
		...
		if (time_after(jiffies, timeout)) return -ETIMEDOUT;
		msleep(1);
	}
```

so it cannot hang *indefinitely*: it returns `-ETIMEDOUT` and `q6v5_mba_load()` prints
**`MBA boot timed out`** before going to `halt_axi_ports`. Two consequences:

* **The 37.900 ms is real modem boot time** (~38 × `msleep(1)` iterations), not AP overhead. The
  window Doc 159 measured is *mostly the modem*, which is consistent with the leading hypothesis
  being about a *handoff* rather than about AP driver work.
* **If a future hang ever shows `MBA boot timed out` followed by a dead console, the site is the
  `readl()` of `RMB_MBA_STATUS_REG`** — an untimed AXI read of RPM shared memory. That is a new,
  **unproven** candidate, and it is *outside* the traced region.

### 4.3 Coverage audit: which untimed calls are traced, and which are not

The SCM hypothesis rests on "synchronous, untimed, other-VMID-owner". `q6v5_xfer_mem_ownership()`
(`qcom_q6v5_mss.c:422-450`) → `qcom_scm_assign_mem()` is called from **14 sites** in the file.
Patch 817 traces **three** of them:

| line | caller | traced? |
|---|---|---|
| 1294 | `q6v5_mba_reclaim` — mba_perm back to HLOS | **yes — step 08** |
| 1149 | `q6v5_mba_load` — mpss_perm to modem | **yes — step 20** |
| 1157 | `q6v5_mba_load` — mba_perm to modem | **yes — step 21** |
| **1547** | **`qcom_q6v5_dump_segment` — mpss_perm to HLOS** | **no** |
| **1570** | **`qcom_q6v5_dump_segment` — mpss_perm back to modem** | **no** |
| 1027 / 1046 | `q6v5_mpss_init_image` — metadata perm | no (after `MBA booted`) |
| 1403 / 1407 / 1508 | `q6v5_mpss_load` — mpss perm | no (after `MBA booted`) |
| 605 | `q6v5_dump_mba_logs` | no (error path) |
| 1203 | `q6v5_mba_load` — `reclaim_mba` error path | no (error path) |
| 1602 | `q6v5_start` — mba_perm back to HLOS, after `wait_for_start` | no (after `MBA booted`) |

So **two** mpss ownership transfers sit inside the dump path, one of them **immediately before**
`port failed halt` (1547 → the reclaim at 1567). Neither is traced. A hang there would also leave
the last line at step 23 of half 2 — again the un-enumerated outcome.

---

## 5. The instrument, validated

Doc 164 §12 item 1 asked for exactly this, and it passed:

* **46 trace lines**, in order, `01..23` twice, with no gaps and no duplicates.
* **Every inter-step delta is 0.021–0.244 ms** (half 1: 0.021–0.060; half 2: 0.021–1.909, the 1.909
  being `pds_enable proxy`; half 3: 0.041–0.244; half 4: 0.041–1.685). That is the ~0.1 ms/line
  predicted for `console_loglevel = 6`, against the ~8.3 ms/line it would have been at level 7 —
  **direct confirmation of Doc 164 §5.3's fix on live data**, not just in a synthetic probe.
* `printk` at the time: **`6 4 1 7`** — the rc.local persistence held across the boot.
* All 23 strings present in the resident `.ko` (Doc 164 §3's hash).

The one number that does *not* match Doc 164's expectation is the window's internal shape (§4) —
and that is a finding about the window, not about the instrument.

---

## 6. Telemetry — patches 810/812/814 holding

`/overlay/q6trace.csv`, at AP 1353.58 s:

```
oops fatal ssr traces defer_q defer_sub defer_keep defer_wipe_live guard_hits pc_irq pc_resync pc_state rx_mapped cmd_open tx_pkts rx_pkts
   0     1   1     46     178       178        163             0            0    327        0        0/1        32       16    6231    6186
```

* **`defer_q 178 == defer_sub 178`** — every deferred TX packet was submitted. `defer_keep 163`,
  **`defer_wipe_live 0`** — patch 812's preserve path is holding across a real fatal (Doc 156's
  pre-fix figure was 27 deferred / 27 destroyed).
* **`guard_hits 0`, `oops 0`** — patch 810's guards never had to fire.
* **`pc_resync 0`** — the resync path was not needed; the powerup poll succeeded (patch 814).
* `pc_irq 327` in 1353.6 s = **14.5 edges/min**; at 2 edges per collapse that is **≈ 7.3 collapses/min,
  one per ~8.3 s**. (The Android reference is ~17.0 edges/min.)

## 7. Scoring Doc 164 §6's prediction — partially, and what the partial result says

Doc 164 §6 pre-registered: *"every ~901 s fatal will be `lte_ml1_sleepmgr_stm.c:4054` (or
`lte_ml1_common_timer.c:390`), and every ~940–948 s fatal will be `a2_power.c:1189`."*

**The signature half is consistent; the interval half cannot be scored from this boot, and the
attempt to score it changed the picture.** The bands are in *modem* uptime, and modem uptime is
`AP time of fatal − AP time of the previous modem-up` (verified against Doc 164 §6's own table:
`2757.618 − 1857.1 = 900.5`, `3699.367 − 2759.1 = 940.3`, `4647.902 − 3700.9 = 947.0`). **This boot's
ring had already wrapped past the modem's initial boot** (the capture begins at AP 405.46 s), so the
offset has to be borrowed from the previous boot, where it was `912.915 − 900.5 = 12.4 s`. That gives
**≈ 925.4 s of modem uptime** for this fatal — which is in **neither** band.

Two readings, and the honest one is the second:

* If the 940.2–947.0 s band were a real property, this fatal should not exist. It does.
* **More likely: the 940.2–947.0 s band was a four-sample artefact.** Doc 162's own taxonomy already
  says `a2_power.c:1189` is **n=7 spanning 68.5–941.3 s and is *not* a clock** (activity-correlated);
  Doc 164 §6's tight "long" band was drawn from four points and does not survive a fifth.
  `lte_ml1_sleepmgr_stm.c:4054`'s ≈900.5–901.0 s band (spread 0.46 s) is the one with the clock
  signature, and this boot simply did not produce it.

**What is robust and does not depend on the offset at all:** the *first* fatal of this boot
(`a2_power.c:1189`, AP 937.786 s) differs in **both** signature and AP time from the *first* fatal of
the previous boot (`lte_ml1_sleepmgr_stm.c:4054`, AP 912.915 s). That is exactly what "two mechanisms
with different periods competing inside one boot" predicts, and it reproduces Doc 162 §3's independent
observation that on 2 of 5 boots the deterministic ≈901 s fatal **did not fire at all**. **The
prediction's directional claim survives; its numeric band does not.** Recorded rather than
rationalised — a band that only holds when you have four samples is not a band.

---

## 8. Corrections, and what is *not* a finding

### 8.1 `modem pc-ack timeout during resume` is a symptom, not a precursor

It appears **1× in this boot** (936.260028, 1.5 s before the fatal) and **6× in the previous boot** —
where its position relative to the fatal is *inconsistent*: before it at 1854.87 s, **after** it at
2757.99 s, before at 4648.27 s, and twice (4990.73, 5331.55) with no fatal at all. It is another
manifestation of a modem that is already wedged, not a predictor. **Do not build a trigger on it.**

### 8.2 Doc 164 §2/§4 — the window model

Corrected by §3: on a fatal the window is a **four-half** sequence with the coredump in the middle,
and `port failed halt` is emitted by the **coredump's** reclaim, not the restart's. The 43.968 ms
figure itself reproduces Doc 164 §4's n=5 range (43.726–46.944 ms) — the *measurement* was right, the
*model* of what it contained was not.

### 8.3 Doc 153 §4 — sharpen, not retract

"The coredump is created only after `rproc_stop()` returns" is correct. It should read: **created
between `rproc_stop()` and `rproc_start()`, inside `rproc_boot_recovery()`, and therefore on the
recovery critical path** — costing 1.064 s and one extra MBA boot/reclaim pair per fatal.

### 8.4 A capture-hygiene trap found while reading this one

`first_fatal_full_dmesg.txt` is 1700 lines, but **1322 of them are our own instrument's leftovers**
from an earlier probe in the same boot: 720 lines of 120 × `X` and counters `SHORT79`…`SHORT85`,
spanning AP 405.46–496.01 s (the ring had wrapped, so the capture *begins* mid-probe). **A capture
that starts inside your own instrument's output is easy to misread as device behaviour.** Trim from
the last known-good anchor before analysing.

---

## 9. Harness

### 9.1 A new open question the instrument itself raises

Doc 159's hang was observed with the coredump watcher running (it is the watcher's log that shows 20
recoveries and one hang). §3.4 shows the watcher's reader forces a **1.064 s in-path stall and an
extra MBA boot/reclaim** on every fatal. **Is the hang rate the same without the watcher?** Unknown,
and it is now cheap to test: the watcher is a single script and the coredump is not needed to score
the trace (the trace goes to `/overlay/q6trace_trace.log` and `/dev/pmsg0`). Recorded as a
confound, not acted on — disabling the watcher would trade the hang for the loss of the coredump.

### 9.2 Two leaked-process traps, found and cleaned this round

`ps w` showed processes that had been stuck since AP 112 s and AP 410 s:

* **`cat /dev/kmsg | grep PAT | head -N` never terminates on this busybox.** `grep` block-buffers,
  so `head -2` never receives 2 lines, never exits, never closes the pipe, and `grep` never gets
  `SIGPIPE` — leaving a **permanent 3-process pipeline holding a `/dev/kmsg` fd**. The first
  `RINGCHECK` probe (the one with the dropped `> /dev/kmsg` redirection, Doc 164 §8.2) was still
  stuck 940 s later. **Use `grep -m N`** (busybox 1.37 supports it — verified) or redirect to a file.
  Note `dmesg | grep -c` is *safe*: busybox `dmesg` reads via `syslog(2)` and does return EOF.
* **Killing the soak by pattern orphans its `cat /dev/kmsg` child.** One such orphan (PID 9357,
  ppid 1, since AP 112 s) still held an fd to `/overlay/q6trace_stream.log (deleted)` — proof that
  the earlier "kill the old soak" cleanup was incomplete and that the orphan had been silently
  draining the ring into a deleted inode ever since. **Kill the children too**, and re-check with
  `ps w | grep -c "[q]6trace_soak.sh"` (must be 5).

Both were killed. The soak is now 5 shells + 2 `cat /dev/kmsg` children and nothing else.

---

## 10. Status

* **The instrument is validated and live.** Resident module md5 `79e7a858d41b6c9f7edf8b26b90c77f8`,
  `srcversion 3F0B3E31…`, 23 trace strings; `printk = 6 4 1 7`; the soak is 5 processes; the
  coredump watcher is armed; the host watcher is running.
* **One fatal so far this boot** (AP 937.786 s, `a2_power.c:1189`), recovered in 43.968 ms of
  window / ≈ 2.5 s of outage, data plane verified (3/3 ICMP, 0 % loss). `retries: 0`.
* **Doc 164 §6's pre-registration is partially scored (§7):** the signature follows the interval
  direction, but this fatal's ≈ 925.4 s of modem uptime falls in neither band — so the
  **940.2–947.0 s "long" band was a four-sample artefact** and should not be cited as a band.
* `/overlay` at **7 %** (229.5 MB used, 2.8 GB free) — one 85 MB dump written.
* **No firmware, DTS or bootloader byte has been changed.** The only device-side writes were
  killing the four leaked processes in §9.2.
* Nothing was deployed this round; Doc 164's build is unchanged.

## 11. Next

1. **Keep soaking for the hang.** This fatal is one of ~21 per hang. **Doc 164 §9's** pre-registration
   is still fully open (last trace line ∈ {08, 20, 21}); **Doc 164 §6's** is now **partially scored**
   — its directional claim survives, its 940.2–947.0 s band does not (§7) — and the **next boot's first
   fatal is the cleanest way to test the band**, since it needs no borrowed offset. To make that
   measurable, the soak should record the AP uptime at which the modem comes up on each boot
   (see §11 item 6).
2. **On a hang, read the last trace line against three cases, not one:**
   * step **08 / 20 / 21** → the SCM hypothesis (as registered);
   * step **23 of half 2** → a hang in the dump path (the two untraced mpss transfers at `:1547`/`:1570`),
     or in the untraced `q6v5_rmb_mba_wait()`;
   * step **23 of half 4** → the restart's own `rmb_mba_wait`; look for `MBA boot timed out`.
3. **Score the window's 86.2 %.** If a hang lands with step 23 last, the next instrument is a single
   `dev_info()` inside `q6v5_rmb_mba_wait()`'s loop — but **only after** the current one has scored,
   because changing the instrument now would invalidate Doc 164 §9.
4. **Task #82 — the ~902 s timer.** Unchanged from Doc 164 §12 item 4; the mcfg/NV lead is
   investigated and closed as a negative (neither kernel requests `mcfg`; the active file is the
   Reliance Jio profile, and `/lib/firmware` is entirely overlay-provided). Remaining AP-side modem
   actors worth auditing: `qcom-carrier-autocfg`, `ModemManager`, `qmi-proxy`,
   `modem-bearer-watchdog`.
5. **Open, not acted on:** whether the coredump watcher's 1.064 s in-path stall changes the hang
   probability (§9.1).
6. **Small harness gap worth closing before the next boot:** nothing records **the AP uptime at which
   the modem comes up**, which is exactly the offset needed to convert a fatal's AP time into modem
   uptime (and therefore to score Doc 164 §6's bands without borrowing). It is one line in the soak's
   startup: read `/sys/class/remoteproc/remoteproc0/state` until it first reports `running`, and log
   the `/proc/uptime` at that moment. Not done this round, because changing the harness mid-soak would
   leave the current run's provenance ambiguous.
