# 185 — THE AP-SIDE RECOVERY STALL IS TWO MODEMANAGER SETTLE TIMERS, NOT A RETRY BACKOFF — AND THE NETIFD TEARDOWN WAS NEVER THE COST

**Date:** 2026-09-22
**Measurement boot:** `a9fd907c` (the Doc 170 capture — **not** the boot Doc 182/183/184 score)
**Change under test:** `0005-plugin-manager-halve-port-probe-settle-timers.patch` (new, ModemManager)
**Deployed with:** patch 825 (kernel) and the WS2 netifd proto-handler change
**Author:** continuing the standing 2026-09-20 grant of autonomy

---

## Evidence list (frozen and hashed)

| file | md5 | bytes | content |
|---|---|---|---|
| `evidence/170_two_fixes_for_the_smd_poll_uaf/bootA_a9fd907c_logroll.log.gz` | `fa4d67a47809ebc804f342418d93fb75` | 98 196 | 13 168 lines, unified `logread -f` + PMKNOB timeline, 11 SSRs |
| `evidence/170_two_fixes_for_the_smd_poll_uaf/V_coredump_off_fatal4_n2_and_the_lost_edge_precursor.txt` | `a12bcd3b37880596046d76ca70c8543f` | 321 318 | Doc 170's own analysis (source of the "15–26 s" headline this doc decomposes) |
| `openwrt-overlay/feeds/packages/net/modemmanager/patches/0005-plugin-manager-halve-port-probe-settle-timers.patch` | `9f9e7ab6f16e317f6291d3851756ed9e` | 2 906 | the change under test |
| `openwrt-overlay/.../files/etc/init.d/modemmanager` | `9714fb2f6a5adbfb53f73a24d49a1bb7` | 1 788 | file-gated `LOG_LEVEL` (new; §6) |
| `openwrt-overlay/.../files/lib/netifd/proto/modemmanager.sh` | `76bcb52f732c5371ed80849b208c09f5` | — | WS2: bounded `mmcli` timeouts + bounded `modempath` poll |
| `msm89xx/patches/825-bam-dmux-pc-state-reconcile.patch` | `b93987eae79eb2ed395a0bc03027dc47` | 2 562 | WS1 (Part A of the pre-registration) |
| `evidence/112_bam_reinit_ab/preregistration_2026-09-22_build.md` | — | 10 694 | the frozen thresholds for both parts |

**No new capture was taken for this doc.** The measurement was already in the archive; this doc
extracts it. That is the point of §1.

---

## 1. SOP compliance statement

| SOP step | status |
|---|---|
| Ask whether the measurement already exists before instrumenting | **YES — this is the doc's origin.** WS3 was planned as "add a hotplug logger line + enable DEBUG, build, flash, soak, then read one SSR". The archive already contained **11 SSRs at INFO level** with every needed marker. The build/flash/soak was avoided and the DEBUG logger line was **not** added (§8). |
| Pre-registration of the thresholds and the control, BEFORE the change is deployed | **YES** — `preregistration_2026-09-22_build.md` Part B, written before the build that carries patch 0005. |
| Capture frozen and hashed | **YES** — both source files md5'd above; the `.gz` is byte-frozen in the repo and was not modified by this doc. |
| Read the DEFINITION, not the name (feedback trap 1) | **YES** — and it paid: the constants at `mm-plugin-manager.c:717-732` are **2000/4000/4000**, while the comments in `device_context_complete()` (`:878`, `:887`) say **"2500ms"** and **"1500ms"**. The comments are stale; a reader who trusted them would have predicted the wrong numbers and the fit would have looked wrong. |
| Verify the mechanism exists in the artefact under test (feedback trap 5) | **YES** — `-Dudev=false` verified at `feeds/packages/net/modemmanager/Makefile:76`, which selects the `EXTRA_PROBING_TIME_MSECS 4000` branch; the `G_STATIC_ASSERT` at `:735` verified so the new values compile. |
| Distinguish a correlate from a mechanism (feedback trap 8) | **YES** — the constants are not inferred from a pattern. `device_context_complete()` **provably returns early** while either timer is pending, and all three timers are armed at the port add, so the two measured offsets (+2 s, +4 s) are the constants themselves, not a coincidence. |
| Instrument coverage matched to the claim (feedback trap 10) | **YES** — the log's timestamps are **1-second granularity** and Doc 170 §5 records the stream as **batched**, so the two `+3 s` and `+5 s` rows are read as *the same 2000/4000 ms* rounded up, not as a second mechanism. The doc does not claim sub-second precision. |
| "What is NOT established" stated explicitly | **YES** — §6. |
| Report `n` with every number | **YES** — n = 11 SSRs throughout. |

---

## 2. What was asked, and what the measurement says

The request that produced this workstream was two AP-side levers:

> 1. Stop netifd from tearing modem down on link-loss — or make the tear idempotent so the next
>    probe finds the port already back.
> 2. Shorten ModemManager's probe retry backoff — or probe the QRTR node before the WDS probe so
>    the failure path returns fast.

**Both premises are wrong, and the measurement says so plainly.**

**(1) The teardown is not the cost, and it is not avoidable.** In all 11 SSRs the sequence is
`port released by device 'qcom-soc'` → `netifd: Interface 'modem' is now down` **inside the same
second**. There is no timeout being hit, no backoff, nothing to make idempotent. The teardown is
also *correct*: the modem's WDS session dies with the SSR, so the old bearer is genuinely
invalid and there is no code path that could resume it. WS2's `--timeout 10` bounds and the
bounded `modempath` poll are **defensive hygiene** — they protect against an `mmcli` call that
hangs — not a latency win, and this doc does not claim one for them.

**(2) There is no retry backoff to shorten.** ModemManager's recovery is **event-driven**:
`mm-base-manager.c:496-499` re-runs the support check on the next port event, and that event
arrives on its own ~3 s later. The failure string

```
could not recreate modem: Unsupported device: at least a QMI port is required
```

(`src/plugins/qcom-soc/mm-plugin-qcom-soc.c:52`) appears **in 10 of 10 SSRs** in the raw capture
(and 11 of 11 counting the tail), so it is not an exception — it is the **normal** first pass, and
it is *concurrent with* the port probe rather than in front of it. Patching the failure path to
"return fast" would change nothing: it already returns in milliseconds.

**What the measurement did find is a third thing neither request named: two fixed, unconditional
settle timers inside ModemManager, worth 4 seconds of every recovery.**

---

## 3. The measurement — 11 consecutive SSRs, one boot, no new capture

Every SSR produces the same sequence. Times are offsets from the `wwan0qmi0` hotplug *add*.

| SSR | `wwan0qmi0` add | `probe step: start` | modem object created | Interface up |
| :-- | --: | --: | --: | --: |
| 1 | 13:00:14 | +2 s | +4 s | +16 s |
| 2 | 13:02:14 | +2 s | +4 s | +15 s |
| 3 | 13:05:00 | +3 s | +5 s | +15 s |
| 4 | 13:08:04 | +2 s | +4 s | +16 s |
| 5 | 13:23:06 | +2 s | +4 s | +17 s |
| 6 | 13:38:08 | +2 s | +4 s | **+25 s** |
| 7 | 13:53:09 | +2 s | +4 s | +14 s |
| 8 | 14:08:33 | +3 s | +5 s | +17 s |
| 9 | 14:23:37 | +2 s | +4 s | +15 s |
| 10 | 14:36:42 | +2 s | +4 s | +14 s |
| 11 | 14:52:27 | +2 s | +4 s | +17 s |

**`add → modem created` is 4 s in 9 of 11 and 5 s in 2 of 11.** The probe itself is not the cost:

```
13:38:10  [wwan0qmi0/probe] probe step: start
13:38:10  [wwan0qmi0/probe] probe step: QMI
13:38:10  [wwan0qmi0/probe] probe step: done
```

— start, QMI and done **inside one second**. So 3 of those 4 seconds are spent waiting on a timer.

### The full phase budget (median over 11 SSRs)

| phase | median | range | what it is |
| :-- | --: | :-- | :-- |
| port release → hotplug add | ~1 s | 0–1 s | the kernel re-enumerating the ports |
| **add → modem object created** | **4 s** | 4–5 s | **MM's settle timers (§4)** |
| **created → SIM ready** | **5 s** | 5–6 s | SIM re-read; **mechanism unidentified (§6)** |
| SIM ready → state `disabled` | 2 s | 1–2 s | device-id / location init (11 s in SSR 6) |
| state `disabled` → Interface up | 5 s | 3–5 s | enable → register → bearer → address |
| **add → Interface up** | **16 s** | 14–17 s | 10 of 11; SSR 6 is 25 s |

Two things follow that are not obvious:

- **The MM phases are serial and on the critical path.** The radio does **not** register
  autonomously while MM is still probing: in every SSR the `3GPP registration state changed
  (unknown -> searching -> registering -> home)` sequence begins *at MM's `enable`* and completes
  ~1 s later (e.g. SSR 3: enable 13:05:12 → `home` 13:05:13 → bearer 13:05:14). So time saved in
  an earlier MM phase is time off the outage — with the caveat in §6.
- **The single 25 s case is not the timers.** SSR 6 carries an extra **11 s** between
  `loaded GID2` and `state changed (unknown -> disabled)`, and it is the **only one of 11** with

  ```
  couldn't load supported assistance data types: Failed to receive indication with the predicted orbits data source
  ```

  — a GNSS/AGPS init that waits for an indication the just-restarted modem never sends. One
  occurrence in the whole 13 168-line log. **Correlation, n = 1: a hypothesis, not a finding.**

---

## 4. The mechanism

`src/mm-plugin-manager.c:717-732`:

```c
/* Time to wait for ports to appear before starting to probe the first one */
#define MIN_WAIT_TIME_MSECS 2000

/* Time to wait for other ports to appear once the first port is exposed
 * (needs to be > MIN_WAIT_TIME_MSECS!!) */
#define MIN_PROBING_TIME_MSECS 4000

#if defined WITH_UDEV
# define EXTRA_PROBING_TIME_MSECS 2000
#else
# define EXTRA_PROBING_TIME_MSECS 4000
#endif
```

All three are armed when the device context is created — i.e. **at the port add** — so they expire
at +2 s, +4 s and +4 s: exactly the two measured columns. `+2 s` is `MIN_WAIT_TIME_MSECS` (the
delay before *any* port is probed) and `+4 s` is `MIN_PROBING_TIME_MSECS` (a floor on the whole
probing phase).

And `device_context_complete()` (`:871`) **refuses to finish** while either of the last two is
still pending:

```c
	/* If the context is completed before the 2500ms minimum probing time, we need to wait
	 * until that happens, so that we give enough time to udev/hotplug to report the
	 * new port additions. */
	if (device_context->min_probing_time_id) {
		mm_obj_dbg (self, "task %s: all port probings completed, but not reached min probing time yet", ...);
		return;
	}

	if (device_context->extra_probing_time_id) {
		mm_obj_dbg (self, "task %s: all port probings completed, but not reached extra probing time yet", ...);
		return;
	}
```

**So even though every port probe has finished in under a second, the support check cannot
complete until 4 seconds have elapsed.** That is the whole of the 4-second block.

Two details that matter:

- **OpenWrt gets the doubled branch.** `feeds/packages/net/modemmanager/Makefile:76` passes
  `-Dudev=false`, so `WITH_UDEV` is undefined and `EXTRA_PROBING_TIME_MSECS` is **4000**, not
  2000. The source comment states the reason plainly: without udev MM relies on
  `mmcli --report-kernel-event` from the hotplug scripts, so it waits longer.
- **The comments are stale and would have misled.** `device_context_complete()`'s own comments say
  "2500ms" and "1500ms" while the constants are 4000 and 4000. Read the constant, not the comment.

### Why the timers are not doing any work here

They exist so that a port which appears *late* is not missed. That case is handled independently:
`device_context_port_added()` (`:1270`) is called for every port that arrives after the check
began, and it **restarts** the extra probing timer (`:1295-1300`) before running that port's probe.
So the late-port safety net does not depend on the length of `MIN_WAIT_TIME_MSECS`.

On this device all 9 ports arrive in one burst (the remoteproc re-probe re-creates them together —
at the first `create_modem` attempt, one second in, MM already lists **9 ports**). The wait is
therefore pure latency. **This is also the patch's risk: in SSR 1 the first attempt listed only 8
of 9 ports, so a port set can be incomplete at that moment.** That is what P-MM2 measures.

---

## 5. The change

`0005-plugin-manager-halve-port-probe-settle-timers.patch` — four lines, `src/mm-plugin-manager.c`
only:

| constant | before | after |
| :-- | --: | --: |
| `MIN_WAIT_TIME_MSECS` | 2000 | **1000** |
| `MIN_PROBING_TIME_MSECS` | 4000 | **2000** |
| `EXTRA_PROBING_TIME_MSECS` (`WITH_UDEV`) | 2000 | **1000** |
| `EXTRA_PROBING_TIME_MSECS` (no udev) | 4000 | **2000** |

The design constraint is that **every existing relationship is preserved**: `MIN_PROBING` stays
strictly above `MIN_WAIT` (the `G_STATIC_ASSERT` at `:735` still holds, 1000 < 2000) and the
non-udev extra wait stays exactly twice the udev one. This is a scale change, not a semantic one.

**Expected effect:** `add → modem created` falls from 4 s to ~2 s. That is the whole claim.

Verification performed before the build: the patch was generated with `git diff` so it carries
real blob hashes, `patch -p1 --dry-run` succeeds against the pristine extracted source, the applied
result is **byte-identical** to an independently edited copy, and the resulting constants are
1000/2000/1000/2000 with the static assert satisfied.

### Frozen predictions

Reproduced from `preregistration_2026-09-22_build.md` Part B — not edited after the build:

| id | prediction | falsified if |
| :-- | :-- | :-- |
| **P-MM1** | `add → modem created` falls from **4.0 s** (9/11 exactly 4, 2/11 exactly 5) to **≤ 2.0 s** | median > 2.5 s |
| **P-MM2** (control) | every post-fix SSR still yields **all 9 ports** (8 × `net/wwanN` + 1 × `wwan/wwan0qmi0`) and no `missing net port` | any SSR with < 9 ports, or any `missing net port` |
| **P-MM3** (secondary) | `add → Interface up` falls by **≥ 1.5 s** against a pre-fix median of **16 s** | the median falls by < 1.0 s |

**P-MM2 is the control that matters**, for the reason in §4. **P-MM3 is secondary on purpose**:
the serial-phase argument in §3 says the saving should propagate, but the radio's own
registration has never been measured *without* MM gating it, so it is possible that part of the
2 s is absorbed. A P-MM3 miss with P-MM1 and P-MM2 hits is a finding, not a refutation.

---

## 6. What is NOT established

1. **The 5-second SIM block — the larger remaining AP-side cost.** `running setup for device`
   → `SIM hot swap setup succeeded` is **5 s in 9 of 11 SSRs and 6 s in 2 of 11**; it is the most
   consistent single block in the budget and it is *not* addressed here. Its mechanism is
   unidentified. The lead is `src/mm-shared-qmi.c:4108` (`setup_sim_hot_swap_step`), a chain of
   QMI UIM requests each issued with a **10-second** timeout
   (`qmi_client_uim_register_events`, `qmi_client_uim_get_slot_status`,
   `qmi_client_uim_refresh_register_all`), any of which could be expiring or being retried.    This
   is a lead, not a mechanism — the step-level logging is at `mm_obj_dbg`, which INFO suppresses.
   The build therefore adds a **file-gated `LOG_LEVEL`** to the init script
   (`/etc/modemmanager-log-level`, default INFO) so DEBUG can be enabled on the device
   **without a rebuild**. `/var` is a symlink to `/tmp` (verified on the device), so the DEBUG
   log file is tmpfs-backed and cannot fill the overlay.

   **⚠ DEBUG must NOT be enabled during the timing soak.** ModemManager's logging is not free,
   and enabling it would perturb the very intervals P-MM1 and P-MM3 measure — the test would
   then differ from its control in two ways at once. The ordering is therefore: **soak at INFO
   first** (matching the pre-fix control exactly, so the timing comparison has no confound), and
   only afterwards enable DEBUG for one **bounded window** to catch the `setup_sim_hot_swap`
   steps. That second run is a separate measurement and must be labelled as one; it does not
   contribute to P-MM1/P-MM2/P-MM3.
2. **The SSR 6 GNSS stall.** One occurrence in 11, correlating exactly with an 11-second gap. Not
   a mechanism at n = 1, and it is the sole reason the pre-fix `add → Interface up` distribution
   has a 25 s tail. Do not generalise it.
3. **Whether the 2 s saving propagates end-to-end.** See P-MM3.
4. **Anything about the 903 s fatal.** Patch 0005 is userspace and does not touch it. Neither does
   patch 825 claim to — see the pre-registration's Part A.
5. **That the user-visible stall is solved.** Even if every prediction holds, this removes ~2 s of
   a ~16 s recovery. The remaining ~14 s is: the 5 s SIM block (§6.1), the ~5 s
   enable/register/bearer phase (radio-driven), and the ~2 s device-id phase. **The honest headline
   is a 12 % improvement to the recovery, not a fix.**
6. **Cross-boot comparability, in one direction only.** The control is boot `a9fd907c` while the
   test will be a different boot. This is legitimate for the timers (pure userspace, same
   ModemManager 1.24.0, unpatched upstream in both) and the probe-start column confirms the kernel
   side behaved identically. It would **not** be legitimate for any kernel-side claim.

---

## 7. Why this changes the plan

- **WS3's planned instrument is cancelled.** The `logger -t mm-probe` hotplug line and the DEBUG
  level were to answer four questions; the archive answers all four at INFO: (a) a `wwan` hotplug
  fires for `wwan0qmi0` **+1 s** after the port release, every time; (b) the port is enumerated
  (9 ports) but **unprobed** when the first `create_modem` runs; (c) the failure-to-success gap is
  **3–4 s**; (d) recovery comes from the **probe-completion event**, not a netifd restart — and the
  `additional_port` "will retry" line belongs to the **failed** attempt, not the successful one.
  The instrument would have measured what is already measured. Only the DEBUG level survives, and
  for a *different* question (§6.1).
- **WS2 is demoted to hygiene.** It stays in the build because bounded timeouts are correct, but
  it must not be credited with a latency improvement.
- **The next AP-side target is the 5 s SIM block**, which is 2.5× larger than the block just
  patched. That is where the next measurement round should go.

---

## 8. Next actions

1. Build and flash with patch 825 + patch 0005 + WS2 + the gated `LOG_LEVEL`.
2. **Verify the patch actually landed** before flashing — read the built
   `build_dir/.../modemmanager-1.24.0/src/mm-plugin-manager.c` and confirm the four constants.
   A package patch that silently does not apply is the failure mode this corpus has hit before.
3. Soak **at the default INFO level** — so the test differs from its control in exactly one way —
   then score **P-MM1/P-MM2/P-MM3** with
   `evidence/112_bam_reinit_ab/score_mm_phases.py` at **n ≥ 11 SSRs** (P-MM2 needs the full count;
   P-MM1 is informative at 5) and **P-W1/P-W2/P-W3** from the sampler at the pre-registered n.
   Report the two parts separately. The scorer is already calibrated: run against the **pre-fix**
   capture it reproduces every frozen number (medians 4/5/2/5/16 s, the SSR-6 11 s and 25 s
   outliers, 9/9 ports) and returns P-MM1 FALSIFIED / P-MM2 CONFIRMED / P-MM3 FALSIFIED, which is
   the correct verdict set for an unpatched boot.
4. **Only then**, in a separate bounded window, enable DEBUG (`/etc/modemmanager-log-level`) to
   identify the 5-second `setup_sim_hot_swap` step from the step-numbered `mm_obj_dbg` lines, and
   decide whether it is a second patch or a redesign. Do not mix this window into step 3.
