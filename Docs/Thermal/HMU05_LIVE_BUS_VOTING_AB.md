> **CORRECTION 2026-10-24 (LATE):** the "bus voting is thermally inert / OS-independent"
> conclusion below is PARTIALLY OVERTURNED by `Docs/Thermal/rpmfindings.md`: the shared RPM
> firmware holds the BIMC/DDR (MEM_CLK) clock at MAX under OpenWrt but at ~1/4 rate under
> Android (measured 4-7x DDR bandwidth difference at identical CPU frequency), because the
> votes each OS sends differ (OpenWrt's cpu0 opp-peak-kBps vote maps to the 1066 MHz PLL max;
> Android's msm_bus never votes MEM_CLK up). Read rpmfindings.md first for the RPM/DDR story.
>
# HMU05: Bus-Voting Audit - OpenWrt vs Android (CORRECTED 2026-10-24)

STATUS: lever CLOSED for the OpenWrt-vs-Android thermal gap (reason below).
DEVICES (both rooted, passwordless SSH from dev host):
- OpenWrt: 192.168.7.1 - kernel 6.12.94, msm8916-openwrt 25.12.5-r6
- Android: 192.168.100.1 - kernel 3.10.28, msm8916_32_512-userdebug 4.4.4

RAW CAPTURES: scratch/thermal_baseline/bus_voting_ab_2026-10-24/ (ab_openwrt.txt, ab_android.txt, ab.sh).

---

## TL;DR for next session

1. Bus voting is ALREADY wired and active in mainline msm8916.c. Real RPM IDs are present and
   bandwidth votes flow live (measured: mas_apss / slv_ebi_ch0 cpu0 peak = 8,528,000 kBps, tracks CPU freq).
2. Two earlier evidence readings that suggested bus voting is broken/inert were both ARTIFACTS:
   - the 'all mas_rpm_id = -1' grep (only matched master-side; slave-side + bridge IDs are present), and
   - reading bimc_ddr_clk_src = 800 MHz from Linux debugfs (that clock is RPM-managed with NO freq table;
     Linux cannot observe RPM-driven frequency changes, so the reading is inconclusive, not proof of no-scaling).
3. Bus voting CANNOT explain the OpenWrt-vs-Android temperature gap because the RPM firmware is shared
   silicon (not OS-dependent). Both Android (msm_bus) and OpenWrt (interconnect) send bandwidth votes to the
   SAME RPM over SMD; if RPM scales the DDR/BIMC clock it does so identically for both OSes. If anything,
   Android votes MORE bandwidth (more bandwidth-aware consumers) and would run the bus HOTTER, not cooler.
4. Do NOT port Android's msm_bus master/slave IDs into OpenWrt's interconnect driver. Wrong namespace,
   unnecessary (mainline IDs are already correct), and does not address the gap.
5. Remaining OS-dependent levers for the temp gap: CPU frequency scaling policy, thermal governor/trip points,
   and the throughput confound. See the controlled equal-throughput A/B plan at the bottom.

---

## Correction 1: 'all mas_rpm_id = -1' was a grep artifact

The interconnect node struct has TWO vote fields: mas_rpm_id (master-side) and slv_rpm_id
(slave-side). A grep for 'mas_rpm_id = -1' returns 81 of 88 nodes - but that only proves the
MASTER-side vote is -1 for most nodes. The nodes that actually carry the bandwidth vote to RPM use the
SLAVE-side and bridge IDs, which ARE populated:

Real RPM IDs present in drivers/interconnect/qcom/msm8916.c (build tree, linux-6.12.94):
- slv_ebi_ch0       slv_rpm_id = 0     (DDR/BIMC slave - the main CPU->DDR vote sink)
- bimc_snoc_mas     mas_rpm_id = 99
- bimc_snoc_slv     slv_rpm_id = 130
- snoc_bimc_mas     mas_rpm_id = 101
- snoc_bimc_slv     slv_rpm_id = 132
- pcnoc_snoc_mas    mas_rpm_id = 29
- pcnoc_snoc_slv    slv_rpm_id = 45
- slv_imem          slv_rpm_id = 26
- (plus slv_rpm_id 0/24/30 on the PNOC/IMEM slaves)

These are the CORRECT mainline RPM IDs; no Android ID port is needed.

## Correction 2: 'bimc_ddr_clk_src = 800 MHz => bus does not scale' was a Linux-view artifact

Live check on the OpenWrt device:
- bimc_ddr_clk_src/clk_possible_rates is EMPTY (no frequency table);
- it is parented to gpll0_vote and clk_enable_count = 1, rate shown as 800,000,000 Hz.

On msm8916 the BIMC/DDR clock is RPM-managed: the actual frequency is set inside the RPM firmware in
response to bandwidth votes. The Linux clock framework only holds a fixed placeholder (no freq table), so
reading clk_rate from Linux debugfs ALWAYS returns the placeholder 800 MHz regardless of what RPM is
really driving the hardware at. The prior A/B therefore proved nothing about physical scaling.

There is no RPM clock debugfs exposed to Linux (checked: no /sys/kernel/debug/rpm*, no BCM voter - msm8916
uses the legacy RPM_SMD bandwidth request, not BCMs). So the physical DDR frequency is not directly
observable from the AP.

## Live evidence (this session, 2026-10-24, OpenWrt device)

$ cat /sys/kernel/debug/interconnect/interconnect_summary
 node                 tag          avg         peak
 bimc_snoc_mas                      0            0
 mas_apss                   2147483647   2147483647
   cpu0                            0      8528000
 slv_ebi_ch0               2147483647   2147483647
   cpu0                            0      8528000
 ... (all other masters read 0) ...

$ bimc_ddr_clk_src rate=800000000  (placeholder; RPM-managed, no freq table)
$ scaling_cur_freq (cpu0) = 800000  (device was at 800 MHz when sampled)

The vote tracks CPU frequency (8.5M kBps at the high OPP), confirming the interconnect framework and the
RPM send path are alive. CPU was at 800 MHz during sampling, so 8.5M is a mid/high vote, not idle - a true
idle sample is still needed to see the low end of the vote range.

## What Android actually does (reference kernel, GitIgnore/android_kernel_zte_msm8916)

Android uses drivers/platform/msm/msm_bus/ (msm_bus_rpm_smd.c) - a COMPLETELY different driver from
mainline's drivers/interconnect/qcom/. It votes bandwidth to RPM over SMD using its own ID scheme
(msm_bus_id.c). SAME silicon, SAME RPM firmware, SAME SMD bandwidth-request protocol. So Android and
OpenWrt both drive the identical RPM bandwidth vote; the resulting physical bus frequency is decided by
RPM, identically, for either OS. => Bus voting is OS-INDEPENDENT and cannot be the source of an
OS-dependent temperature difference.

## Why the Android msm_bus ID port is NOT the fix

1. Namespace mismatch: Android msm_bus IDs and mainline interconnect RPM IDs are different enumerations.
   Copying one into the other would send WRONG IDs to RPM.
2. Unnecessary: mainline msm8916.c already carries correct RPM IDs (see Correction 1) and votes flow live.
3. Off-target: even if votes were missing, both OSes vote the same RPM, so it would not close the gap.

## Original A/B thermal gap (kept for record; still confounded)

Throughput was NOT equalised (historical OpenWrt vs Android LTE throughput differs ~2x), so absolute
degree numbers are not causally clean. Aggregate on-die TSENS:
- Idle:   OpenWrt ~53.2 C avg  vs  Android ~45 C max
- Load (10 MB pull): OpenWrt ~54.4 C avg (peak 58)  vs  Android ~45 C max
=> OpenWrt ~9-12 C hotter, but confounded by throughput + ambient + CPU-freq policy.

## Conclusion / recommended next steps

Bus voting is closed as a thermal lever for the OS gap (OS-independent, already active). The honest
remaining question is whether the gap even persists POST-CPR once OS-dependent confounders are controlled.

Required next step (replaces the 'implement bus voting' task):
1. CONTROLLED equal-throughput A/B: rate-limit both devices to the same LTE throughput (or transfer the
   same fixed payload), match ambient/band, n>=3 runs each. Decision rule:
   - gap <= ~3 C at matched throughput  => the rail fix (cause #3) + confound explain it; thermal
     workstream closes.
   - gap remains  => investigate OS-dependent levers: CPU frequency scaling policy / thermal governor /
     trip points, and modem/RF activity at matched throughput (NOT bus voting).
2. Optionally confirm the physical DDR-scaling question OUT-OF-BAND (e.g., power/thermal delta when a
   dummy max-bandwidth vote is held vs idle) - but this is irrelevant to the OS gap per the argument above.

## Implementation status (2026-10-24)
- Bus-voting code: already implemented (CONFIG_INTERCONNECT_QCOM_MSM8916=y, patch 834) and proven live.
  No new code to write for the thermal purpose.
- 'Port Android msm_bus IDs': CANCELLED - wrong namespace, unnecessary, off-target.
- Remaining action: controlled equal-throughput A/B (not a code change).
