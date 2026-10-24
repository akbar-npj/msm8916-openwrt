# RPM deep dive — MEM_CLK / DDR clock policy, OpenWrt vs Android (2026-10-24)

Status: MAJOR NEW FINDING. This rewrites part of the earlier "bus voting is thermally inert /
OS-independent" closure (`HMU05_LIVE_BUS_VOTING_AB.md`): the RPM *firmware* is shared silicon,
but the *votes each OS sends* differ — and the DDR/BIMC clock the RPM actually holds differs
**4-7x** between the two OSes. This is the first mechanism found that plausibly explains the
OpenWrt unit's ~12 C idle-heat gap with equal CPU busy%.

## 1. How we got here

The equal-throughput loopback A/B (see `HMU05_EQUAL_TPUT_LOOPBACK_AB.md`) left the idle gap
(OWRT pm8916 55.4 C vs AND 43.6 C at ~7% busy, both 800 MHz) unexplained. Digging into the RPM
surface on both units produced three leads: the interconnect debugfs INT_MAX votes, the
mainline `icc_node_add()` boot-vote mechanism, and a direct measurement of the RPM-managed DDR
clock via a bandwidth benchmark (Linux cannot *read* the RPM MEM_CLK rate, but any streaming
workload measures it indirectly).

## 2. RPM surface inventory

OpenWrt 6.12 (mainline): `/sys/kernel/debug/regulator/regulator_summary` (all pm8916 rails;
spm/vdd-apc 1062 mV == Android's 1.05 V — rail parity re-confirmed),
`interconnect/interconnect_summary` + `interconnect_graph`, `clk/clk_summary`, `qcom_stats`
(empty — device never suspends). No rpm_log/rpm_stats on mainline.

Android 3.10 (downstream): `rpm_log`, `rpm_stats` (RPM never entered xosd/vmin in 25230 s),
`rpm_master_stats`, `rpm_send_msg`, `msm-bus-dbg/{commit-data,client-data,shell-client}`.
NOTE: `rpm_log`/`rpm_master_stats` reads BLOCK (do not cat them); `commit-data`/`client-data`
return "Input/output error" — the msm_bus debugfs is half-broken on this unit.

## 3. The INT_MAX mystery — explained (and mostly benign)

`interconnect_summary` shows `mas_apss` / `slv_ebi_ch0` avg=peak=2147483647 (INT_MAX).
Chain of custody:

1. `icc-rpm` registers its providers **without a `get_bw` callback**, so `icc_node_add()`
   (drivers/interconnect/core.c:957-963) initializes every node to
   `init_avg = init_peak = INT_MAX` and **commits that once** — a "boot at max for safety"
   vote that really reaches the RPM (qcom_icc_bw_aggregate with tag 0 -> both ACTIVE and SLEEP
   buckets; qcom_icc_rpm_set -> smd-rpm).
2. During boot, `aggregate_requests()` floors every re-aggregation at the init values
   (core.c:293-296) **until `icc_sync_state()` runs**.
3. `msm8916.c` is the only qcom interconnect platform WITHOUT `.sync_state = icc_sync_state`
   (8909/8937/8939/8974/8976/8996/osm-l3/qcm2290 all have it) — so the floor never clears and
   the debugfs display shows INT_MAX forever.
4. However, what `qcom_icc_set` actually SENDS per commit is the per-node bucket sums
   (`qn->sum_avg`), which `pre_bw_aggregate` zeroes and re-fills from real client requests.
   cpu0's request (patch 834: `opp-peak-kBps = 8528000` at the 800 MHz OPP; no avg) makes the
   *bandwidth* votes ~0 avg / 8528000 kBps peak.

So the INT_MAX is a real boot-time max vote plus a permanent *display* floor — NOT a live
max-bandwidth request. The interesting vote is elsewhere:

## 4. THE FINDING: OpenWrt votes MEM_CLK to max; Android holds it at ~1/4 rate

msm8916's BIMC provider has `bus_clk_desc = &bimc_clk` (MEM_CLK r_id 0; SNOC = bus_1_clk).
`qcom_icc_set` computes the bus rate as `max over all nodes of max(sum_avg, max_peak)/buswidth`
and commits it to the RPM for the ACTIVE and SLEEP sets. With cpu0's peak vote of 8528000 kBps
and buswidth 8: **8528000/8 = 1066000 kHz = 1066 MHz** — the BIMC PLL max — so OpenWrt's
MEM_CLK vote is at maximum at every CPU OPP (the OPP table only has peak values, and 3200000
at 200/400 MHz still maps to 400 MHz).

Measured consequence (the benchmark Linux cannot fake):

| Benchmark (same silicon, all 4 cores online, cpu0 pinned 800000 kHz, sampled in-flight) | OpenWrt | Android |
|---|---|---|
| A: memset via /dev/zero, 4 GiB | **3.0 GB/s** (1.42 s) | **0.66-0.69 GB/s** (6.0-6.2 s) |
| A at 200 MHz (OWRT powersave control) | 753 MB/s | — |
| C: page-cache read 64 MB x20 = 1.28 GiB | **3.4 GB/s** (0.38 s) | **0.5 GB/s** (2.55 s) |
| B: pipe copy mix 2 GiB | 6.7 s | 13.7 s |

Controls that rule out the obvious confounds:

- **Frequency**: sampled every 0.4 s DURING the Android bench — cpu0 = 800000 kHz in every
  single sample, cores 0-3 online throughout. (The Android `scaling_governor` writes return
  rc=1 EINVAL but DO take effect — readback confirms; do not trust the rc.)
- **Thermal mitigation**: `stop thermal-engine`, unit cooled 55->54 C, re-bench = identical
  688 MB/s. Not pop_mem/thermal throttling. thermal-engine restored afterwards.
- **CPU micro-arch / kernel copy path**: OpenWrt at 200 MHz gives 753 MB/s == Android at
  800 MHz (656-685 MB/s). Android behaves exactly like OpenWrt at quarter CPU rate; a pure
  ISA/uaccess difference cannot produce that alignment.
- OpenWrt bench ran under `performance` governor (restored to schedutil after).

Conclusion: **the RPM holds the BIMC/DDR clock at max under OpenWrt and at roughly a quarter
of that under Android, even under full memory load.** Android's msm_bus never votes MEM_CLK up
(its commit path is demonstrably half-broken on this unit), while OpenWrt's cpu0 opp-peak vote
pins the computed rate at the PLL max.

## 5. Thermal implication (why this matters)

LPDDR3/BIMC PHY+IO power scales with clock. OpenWrt holds the DDR subsystem at max clock
24/7 (its vote never drops: the OPP table has peak-only values and even the 200 MHz OPP maps
to 400 MHz), Android at ~1/4. A few hundred mW difference is easily 3-8 C on a passively
cooled dongle — this is the first mechanism consistent with the observed idle gap
(OWRT 55.4 C vs AND 43.6 C pm8916, with Android actually running a BUSIER AP: loadavg
3.5-5.5 vs 0.29).

It also flips the sign of the optimization: matching "Android parity" here means teaching
OpenWrt to vote LESS, not more.

## 6. Fix candidates (each = kernel rebuild + flash, none tested yet)

1. **Add real avg votes to the OPP table** (patch 834 extension): `opp-avg-kBps` values well
   below peak (e.g. downstream-style 100-500 MB/s at low OPPs) — changes qn->sum_avg, hence
   the MEM_CLK rate.
2. **Lower `opp-peak-kBps`** at the low/mid OPPs (8528000 -> e.g. 4264000 or per-OPP
   downstream-matched values) — directly caps the computed MEM_CLK rate.
3. **Add `.sync_state = icc_sync_state` to msm8916.c** (matches every other platform; clears
   the INT_MAX boot floor + display artifact; good hygiene, probably small thermal effect
   since live commits already supersede it).
4. Verify each flash with the same benchmark (expect OWRT memset to drop from 3.0 GB/s toward
   ~1-2 GB/s at idle OPPs if the fix works) + an idle-temperature A/B (the 12 C gap should
   narrow).
5. Optional: try to read Android's true vote values via `rpm_send_msg`/smem — but see the
   incident below; the msm_bus debugfs on this unit is dangerous.

## 7. INCIDENT: msm-bus shell-client wedged the Android unit

While trying to prove the mechanism by injecting a max vote from userspace
(`echo 1 > mas; echo 512 > slv; echo 2e9 > ab/ib; echo 1 > update_request` on
`/sys/kernel/debug/msm-bus-dbg/shell-client`), the `update_request` write **blocked** and the
unit **stopped answering ping AND ssh** (>3 min, no watchdog recovery observed). The debugfs
was already half-broken (`commit-data` = Input/output error). If the unit does not come back
on its own it needs a physical power cycle; nothing about the injected vote persists across a
reboot. DO NOT use shell-client on this unit again.

Also noted the same session: `echo restart > /sys/kernel/debug/msm_subsys/modem` on OpenWrt
returned EINVAL (rc=1, no restart fired, `dmesg | grep -c 'msm_subsys: restarting'` unchanged)
— the live-verified SSR lever did NOT work this time; worth a separate look. Consequently the
"who holds the vote, AP or modem?" attribution test (bench during mpss-down) is still open,
though the boot-vote + bus_clk_desc chain above makes the AP side the near-certain holder.

## 8. IDs and references

- Downstream bus ids (dt-bindings/msm/msm-bus-ids.h): MSM_BUS_MASTER_AMPSS_M0 = 1,
  MSM_BUS_SLAVE_EBI_CH0 = 512.
- icc-rpm.c: qcom_icc_rpm_set() (bw votes, skipped for ap_owned nodes), qcom_icc_set()
  (bus_clk_desc rate commit, cached in qp->bus_clk_rate), qcom_icc_bus_aggregate()/
  qcom_icc_calc_rate() (max(sum_avg, max_peak)/buswidth).
- core.c: icc_node_add() init INT_MAX (no get_bw), aggregate_requests() !synced_state floor,
  icc_sync_state() clearing (never called on msm8916).
- Patch 834 = cpu interconnects + opp-peak-kBps; patch 824 = rpm master-stats DT (debugfs
  empty in practice); patch 838 = OPP cap 800 MHz.
- Android freq table: 200/400/533/800 MHz, min 200000; OpenWrt OPPs capped at 800 MHz (838).
- OpenWrt regulators at rest: s3 1300 mV (iris-vddrfa), s4 1850 mV, l5/l6/l7 1800 mV,
  l8 2900 mV (mmc-vmmc), l9 3300 mV (iris-vddpa), l13 3300 mV (ulpi-v3p3), spm 1062 mV
  (vdd-apc) — all plausible, none obviously wrong vs Android (per-rail Android voltages not
  yet dumped; regulator_summary does not exist on 3.10, per-regulator debugfs dirs do).

## 9. Session tool traps (for whoever runs the next experiment)

- `/dev/kmsg` reads BLOCK (tail semantics) — always `dmesg | grep`.
- Android `rpm_log` / `rpm_master_stats` reads BLOCK.
- Android `msm-bus-dbg/shell-client/update_request` writes can HARD-WEDGE the unit. Never again.
- Android `scaling_governor` writes return rc=1 EINVAL but succeed (verify by readback).
- The host-side Bash tool cannot background long-lived servers; keep servers on the devices
  with all fds redirected to /dev/null, or ssh hangs until timeout.
- Android has no /tmp (use /data/local/tmp); OpenWrt busybox sleep is integer-only but usleep
  exists; `dd bs=4M` on a pipe returns one pipe-buffer (~64 KB) — use `bs=65536 count=64`.

## 10. SSR-lever EINVAL — root cause (resolved 2026-10-24)

`echo restart > /sys/kernel/debug/msm_subsys/modem` returned EINVAL on the DEPLOYED image,
while `printf restart` (no trailing whitespace) worked, and `printf ' restart'` (leading
space) also worked. Probes: trailing-newline rejected, trailing-whitespace variants
untested-but-implied, leading whitespace accepted -> the deployed store trims LEADING
whitespace only (skip_spaces + raw strcmp), not both ends.

Neither committed revision of patch 826 does that (a23256e 2026-10-02 and 505b071 both have
full `strstrip`; the v25.12.5-r6 tag is identical to HEAD). Conclusion: the flashed image's
qcom_q6v5_mss.c was patched from an OLDER, never-committed 826 revision that lived in the
untracked openwrt tree (stale quilt state at flash time; `openwrt/target` is not git-tracked
and build.sh syncs patch FILES, but an already-applied patch may not be re-applied).

Practical rule for the CURRENTLY deployed image: invoke the SSR lever as `printf restart >
/sys/kernel/debug/msm_subsys/modem` (NO trailing newline/space). The quirk self-heals with the
next flash (current tree = full strstrip).

Also noted: a dmesg restart line (t=26779 s) fired ~35 min before any deliberate SSR this
session — almost certainly the modem-bearer-watchdog recovery path (its invocation evidently
works, i.e. it does not append a trailing newline). Unconfirmed; watchdog script not located
at the expected paths (/root/modem-bearer-watchdog*).

## 11. Experiment: removing the AP opp-peak vote does NOT drop MEM_CLK (2026-10-24)

Built and flashed an image (branch test/rpm-mem-clk, commits d976793/e9e4bee/90b1bbd/21ed566)
with patches 834/838/846 edited so cpu0's `opp-peak-kBps` is 0 at every OPP (the AP never
drives mas_apss/slv_ebi_ch0 up). Post-flash verification:

- interconnect_summary: cpu0 client votes 0/0 (was peak 8,528,000). The mas_apss /
  slv_ebi_ch0 node aggregates still read 2147483647 (INT_MAX boot-init state).
- DDR benchmark UNCHANGED: memset 3.0 GB/s (x3, same 1.41-1.45 s), page-cache 1.28 GiB in
  0.37 s (~3.4 GB/s), pipe 2 GiB in 6.7 s. Pre-flash baseline on the same session was
  identical.

CONCLUSION: the live AP opp-peak vote was NOT the MEM_CLK holder. The holder is the
INT_MAX boot-init commit from icc_node_add() (init_avg/init_peak = INT_MAX when the
provider registers without get_bw), which msm8916.c never clears because it is the only
qcom ICC platform without `.sync_state = icc_sync_state`. Every subsequent re-commit
re-aggregates the INT_MAX floor, so MEM_CLK stays latched at max forever.

FIX: patch 860 adds `.sync_state = icc_sync_state` to msm8916_noc_driver. After all
consumers probe, the core clears the init state and re-commits the real aggregation
(with the 834 experiment: 0/0 -> RPM default policy, i.e. Android parity since Android's
3.10 kernel never votes BIMC at all). Same-session bench + idle-temp A/B to follow.

## 12. Patch 860 flash result — MEM_CLK CRACKED (2026-10-24)

Image built with patch 860 (`.sync_state = icc_sync_state` in msm8916_noc_driver) on top of
the 834/838/846 opp-peak=0 experiment. Flashed config-preserving (14:08 build, 635 s).

POST-FLASH VERIFICATION:

- interconnect_summary: mas_apss / slv_ebi_ch0 node aggregates dropped from 2147483647
  (INT_MAX) to 0/0 — sync_state FIRED and the init state was cleared and re-committed.
- DDR benchmark (same session, same script, governor pinned performance @ 800 MHz):

  | test            | pre-860 (boot-vote held) | post-860      | Android (prior session) |
  |-----------------|--------------------------|---------------|-------------------------|
  | dd /dev/zero    | 3.0 GB/s                 | 1.4-1.6 GB/s  | 0.66 GB/s               |
  | page-cache 20x  | 0.37 s                   | 0.44 s        | ~0.8 s-equiv            |
  | pipe 2 GiB      | 6.7 s                    | 10.8 s        | -                       |

  The AP's INT_MAX boot-init commit WAS the MEM_CLK holder. One line of driver code halved
  the DDR/BIMC clock domain. Remaining 2.3x vs Android is open (other voters? RPM default
  floor? bench path differences on 3.10?).

SANITY: wwan0 attached (10.110.5.113), internet OK, cpufreq max 800 MHz (838 edit intact).

IDLE THERMAL (30-min logger, device idle after boot+bench):

- OWRT pm8916: 56.5 -> 51.2-52.3 C plateau (still slowly descending at 30 min).
- AND pm8916 same day: 44.6-45.5 C. Gap now ~7 C (recorded baseline gap was ~11.8 C).
- CAUTION: a single session-start snapshot on the OLD image read 49.8 C, so the idle
  improvement is NOT cleanly proven by today's data alone (ambient/time-of-day confounds;
  boot residual heat). The DDR bandwidth change is DEFINITIVE; the idle-temp delta needs a
  multi-hour soak. The load-side repeat of the equal-tput loopback A/B on this image is the
  cleaner thermal test (that is where a 2-4.7x bandwidth difference should show up).

NEXT: multi-hour idle soak + equal-tput loopback A/B on this image; then decide whether
opp-peak=0 + sync_state ships as-is (closest to Android: AP never votes) or real per-OPP
avg/peak values replace the zeros.

## 13. Equal-tput loopback A/B on the 860 image — parity (2026-10-24)

Same-session, matched-rate rerun of the equal-throughput loopback A/B (method doc
`HMU05_EQUAL_TPUT_LOOPBACK_AB.md`), both legs back-to-back with one harness
(K=4 parallel 4 MB loopback fetches, rate set by client usleep, 5 s logger):

| Metric | OpenWrt (860 image) | Android |
|---|---|---|
| Throughput | 10.74 MB/s (161 iters, usleep 1.4 s) | 11.07 MB/s (166 iters, usleep 1.43 s) |
| pm8916 idle baseline | 44.4 C (settled ~2.5 h post-boot) | 43.6-44.1 C |
| pm8916 rise under load | ~+0.5 C max (44.4 -> 44.3-45.1, flat) | ~0 C (43.6-44.1 -> 43.2-44.4, flat) |
| CPU busy under load | 10.0% | 3.4% |
| post-load recovery | back to 43.6-44.2 C in 2 min | - |

- On the OLD image (prior session, same 10.8 MB/s): OWRT +3.2 C still climbing at 36% busy
  vs AND +1.9 C at 24%. On the 860 image: BOTH flat at ~44-45 C. The load-heat divergence at
  matched rate is GONE (cross-session caveat applies to the old numbers; the same-session
  flat-vs-flat result is the reliable one).
- IDLE PARITY TODAY: OWRT 43.6-44.3 C vs AND 43.2-44.4 C. The recorded ~11.8 C idle gap
  (55.4 vs 43.6) is GONE on the 860 image. Important sequencing lesson: the 30-min post-boot
  logger's '51-52 C plateau' was NOT equilibrium — the board kept draining boot/bench heat
  for ~2 h (long thermal time constant). Idle-gap claims need a settled baseline (>=2 h),
  not a 30-min logger.
- OWRT still burns ~3x the CPU per byte in the synthetic path (10.0% vs 3.4% busy) — the
  uhttpd/wget-vs-busybox harness asymmetry noted in the method doc persists — but it is
  thermally inert at this rate now.
- SURPRISE (cross-session, unexplained): per-iteration service time collapsed on BOTH units
  vs the prior session (OWRT ~2.1 s -> ~0.07 s; AND ~0.69 -> ~0.03 s). Same binaries, same
  servers. Do NOT compare absolute busy%/rates across sessions; possible state/harness
  confound (page-cache state, thermal-engine, background load). OWRT's rate at usleep
  400 ms today was ~33 MB/s.

VERDICT: with patch 860 (+ opp-peak=0), OpenWrt matches Android thermally at idle AND at
matched 10.9 MB/s loopback load. The thermal-gap goal is closed on this evidence.
