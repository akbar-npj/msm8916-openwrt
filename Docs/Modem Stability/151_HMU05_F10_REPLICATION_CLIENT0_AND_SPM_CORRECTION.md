# 151 — HMU05: fatal #10 replication, client 0 is NOT the modem, a new AP-side hang in the modem-shutdown path, and the SPM/CPR hypothesis corrected

**Date:** 2026-09-21
**Device:** Melbon HMU05 (MSM8916 + WTR1605 + QFE2320), OpenWrt 25.12.5 / Linux 6.12.94,
`hmu05,250605v0s`, OpenWrt `r33051-f5dae5ece4`.
**Baseband:** stock HMU05 modem firmware (byte-identical to the Android build — Doc 149 /
`Stock_Android_Live/01`). Nothing was flashed, patched, or rebuilt in this session.
**Supersedes / corrects:** Doc 149's VMIN paragraph (`reference_rpm_firmware_and_live_log`
"the OpenWrt DT has everything `cpuidle-qcom-spm` needs except the compatible"), and the
carried-forward note that "the `qcom_spm` driver is bound".
**Answers:** Doc 150 §12 item 1 (replicate at fatal #10) and §12 item 2 (client 0 identity).

---

## 1. Why this document exists

Doc 150 turned the RPM's external log into a trustworthy instrument and, from a single
capture spanning fatal #9, proposed that the AP's client id appears only in a sub-second
window at the fatal. It pre-registered a falsifier:

> "Client 0 should reappear in a sub-second window at the fatal and nowhere else; client 1's
> counter should reset again." — Doc 150 §12 item 1

This document reports the replication at fatal #10, the mechanism that makes the AP the
leading candidate for client 0, a **new and unrelated AP-side hang** discovered while trying
to build a second, independent falsifier, and a **correction that invalidates a planned DT
patch** in the carried-forward VMIN/SPM plan.

## 2. SOP compliance statement

The Dual-Firmware Comparative SOP requires each porting/stability claim to name the steps
taken and the steps deliberately skipped. This session:

* **No firmware, DT, module, or userspace file was modified on the device.** The only
  device-side writes were `/tmp/rpmring` (an existing instrument, already deployed),
  `/tmp/coredump_watch.sh` (re-installed after the reboot below), and
  `/sys/class/remoteproc/remoteproc0/coredump = enabled` (re-armed after the reboot).
* **Ground truth was read from the live device, not from the corpus.** Every claim in §6
  about the live device tree, the live driver bindings and the live cpuidle driver was read
  from `/sys/firmware/devicetree/base/` and `/sys/` on the running unit this session. Where
  this contradicts the corpus, the corpus is corrected.
* **The comparative arm was not re-run.** No Android flash was performed (one device; the
  user has stated this is a last resort). The byte-identical-firmware result of Doc 149
  stands as the substitute.
* **One destructive experiment was attempted and is reported honestly** (§5). It caused a
  device reboot and did not produce the intended measurement.

## 3. Fatal #10 — replication of Doc 150 §7

### 3.1 The boot's fatal table

All ten fatals of the boot, from `dmesg`:

| # | AP time (s) | Δ (s) | signature string |
| ---: | ---: | ---: | :--- |
| 1 | 914.769287 | — | `lte_ml1_common_timer.c:390` |
| 2 | 1818.443149 | 903.673862 | `lte_ml1_common_timer.c:390` |
| 3 | 2722.117987 | 903.674838 | `lte_ml1_common_timer.c:390` |
| 4 | 3625.792806 | 903.674819 | `lte_ml1_common_timer.c:390` |
| 5 | 4529.467002 | 903.674196 | `lte_ml1_common_timer.c:390` |
| 6 | 5426.331445 | **896.864443** | `a2_power.c:1189` |
| 7 | 6360.554391 | **934.222946** | `a2_power.c:1189` |
| 8 | 7262.819515 | **902.265124** | `lte_ml1_sleepmgr_stm.c:4054` |
| 9 | 8165.925446 | 903.105931 | `lte_ml1_common_timer.c:390` |
| **10** | **9069.600322** | **903.674876** | `lte_ml1_common_timer.c:390` |

Fatal #10 is a textbook member of the deterministic family: Δ = **903.674876 s**, i.e.
within **0.2 ppm** of fatals #2–#5. This is consistent with Doc 149 §2's decomposition
(modem-uptime period 902.267 s + ~1.4 s SSR). Per Doc 148 §6.3 and Doc 149 §3.1 the
`file:line` string is a mutable global and is **not** a classifier — it is reported here
only because the *time* is the classifier.

### 3.2 The capture

`rpmring -t -n 200000 -i 2` over the fatal: **8919.412 – 9251.160 s** (331.748 s),
**2271 write bursts, 94 252 records**. The fatal sits 150.2 s into the capture and the
capture runs 181.6 s past it.

### 3.3 Client census — the falsifier is satisfied

| client | records | first (s) | last (s) | span (s) | ids seen |
| :--- | ---: | ---: | ---: | ---: | :--- |
| **0** | **72** | **9069.596** | **9070.904** | **1.308** | `0xd0`×24, `0xd1`×24, `0xd2`×24 |
| 1 | 10 751 | 8919.412 | 9251.160 | 331.748 | `0xd0`×2311, `0xd1`×4119, `0xd2`×4321 |

**Client 0 appears in exactly five bursts, all inside the fatal+SSR window, and nowhere in
the other 330.4 s of the capture.** This is the second independent fatal at which that is
true (Doc 150 §7 was the first), so the falsifier has now been *satisfied twice* rather than
merely not-yet-failed.

The five bursts, with the fatal at 9069.600322 and the modem back up at 9070.982865:

| burst (AP s) | nrec | client-0 records |
| ---: | ---: | ---: |
| 9069.596398 | 41 | 18 |
| 9070.290049 | 7 | 3 |
| 9070.292785 | 45 | 15 |
| 9070.296796 | 52 | 18 |
| 9070.904078 | 45 | 18 |

### 3.4 Client 0 is not the modem

The decisive observation is the **sequence number**, which is carried in the `0x00d0`
request record. Over the whole capture client 0's sequence runs

```
0x135, 0x136, 0x137, 0x138, 0x139, 0x13a,   (burst 9069.596, pre-fatal)
0x13b,                                       (burst 9070.290, post-SSR)
0x13c, 0x13d, 0x13e, 0x13f, 0x140,           (burst 9070.293)
0x141 … 0x146,                               (burst 9070.297)
0x147 … 0x14c                                (burst 9070.904)
```

**contiguous and monotonically increasing straight through the modem SSR.** Client 1's
counter, by contrast, restarts at the SSR (Doc 150 §7). Whatever client 0 is, it **did not
restart when the modem did**. That rules out every entity that is reset by the SSR,
including the modem itself.

### 3.5 What client 0 asks for

Every client-0 transaction is the three-record shape `0x00d0 → 0x00d1 → 0x00d2`:

```
0x00d0  <client=0> <seq>
0x00d1  <client=0> <resource> <value>
0x00d2  <client=0> "req."
```

with the resource alternating between two 4-char little-endian names:

| word[5] | ASCII | value | count |
| :--- | :--- | ---: | ---: |
| `0x616f646c` | `ldoa` | `0x3` | 12 |
| `0x61706d73` | `smpa` | `0x1` | 12 |

24 transactions over 1.308 s ≈ **18 requests/s**, in bursts, with no other resource ever
requested.

## 4. Client 0's identity — the AP is now the leading hypothesis, with a mechanism

### 4.1 The AP's RPM clients

The live device exposes four AP-side RPM consumers:

| platform device | driver |
| :--- | :--- |
| `remoteproc:smd-edge:rpm-requests:regulators` | `qcom_rpm_smd_regulator` |
| `remoteproc:smd-edge:rpm-requests:power-controller` | `qcom-rpmpd` |
| `remoteproc:smd-edge:rpm-requests:clock-controller` | `qcom-clk-smd-rpm` |
| `icc_smd_rpm` | interconnect |

### 4.2 `smpa` and `ldoa` are the AP regulator client's resource names

This is the load-bearing link. `drivers/regulator/qcom_smd-regulator.c:967-985` maps the
AP's PM8916 regulators onto RPM resources:

```c
{ "s1", QCOM_SMD_RPM_SMPA, 1, &pm8916_buck_lvo_smps, "vdd_s1" },
{ "s2", QCOM_SMD_RPM_SMPA, 2, &pm8916_buck_hvo_smps, "vdd_s2" },
{ "l1", QCOM_SMD_RPM_LDOA, 1, &pm8916_nldo, "vdd_l1" },
{ "l2", QCOM_SMD_RPM_LDOA, 2, &pm8916_nldo, "vdd_l2_l5" },
{ "l3", QCOM_SMD_RPM_LDOA, 3, &pm8916_nldo, "vdd_l3_l6_l10" },
...
```

`QCOM_SMD_RPM_SMPA` / `QCOM_SMD_RPM_LDOA` are RPM resource *classes*, and the RPM's own
resource table spells them **`smpa`** and **`ldoa`** — both strings are present in
`rpm.bin` (35 and 15 occurrences respectively; the RPM name table also contains the
`xosdvmin` run at file offset `0x195c0` that Doc 149 identified).

So the AP's regulator client is exactly the kind of client that emits `0x00d1 <client>
ldoa <n>` and `0x00d1 <client> smpa <n>` records. The `value` field is then the
**resource index**: `ldoa 3` = `l3`, `smpa 1` = `s1`.

### 4.3 Why the AP would act only at the fatal

The AP's RPM regulator client is silent in steady state — regulators are configured once at
boot and then left alone, which is why client 0 is absent from 330 s of baseline *and* from
the 11.2 s manual-capture window in §5. The one thing that makes the AP touch the PMIC's
rails at runtime is **remoteproc crash recovery**, i.e. exactly the fatal window. That is a
coherent story, and it explains the otherwise-puzzling "only at the fatal" signature.

### 4.4 Status: hypothesis, not proof

What is **established**: client 0 is fatal-correlated (2/2), is not reset by the SSR, and
requests exactly the two RPM resources the AP's regulator driver is built to request.

What is **not established**: that client 0 *is* the AP. The `smpa`/`ldoa` names are the
AP's, but they are also the *modem's* — the modem's own rail votes use the same PMIC
resource namespace, and the modem's RPM client (client 1) is separately visible. A
non-AP, non-modem client (e.g. the RPM's internal power sequencer acting on behalf of the
crash) has not been excluded.

The falsifier that would settle it — provoke a *known* AP-side RPM regulator request and
watch client 0's sequence — was attempted in §5 and could not be completed.

## 5. A new AP-side hang: `echo stop` on the modem remoteproc

### 5.1 What was attempted

To obtain an AP-initiated modem teardown without waiting for a fatal, the modem was stopped
and restarted through sysfs while an RPM capture ran:

```sh
echo stop  > /sys/class/remoteproc/remoteproc0/state
echo start > /sys/class/remoteproc/remoteproc0/state
```

### 5.2 What happened

The `stop` **never returned**. The ssh command hit its 40 s timeout with the device still
inside the write. The device then **rebooted**, coming back at uptime 41.87 s.

The RPM capture (`manual_ssr.txt`) survived 11.2 s / 2127 lines and **contains no client-0
activity at all** — only client 1 (213 records). It ends at 9355.801 s, essentially the
instant the stop was issued.

### 5.3 The reboot was a hang, not a panic

`/sys/fs/pstore/console-ramoops-0` holds the **complete console of the boot that died**
(645 lines, from `Booting Linux` at t=0 to the end). Its final lines are:

```
[ 9355.876748] bam-dmux 4080000.remoteproc:bam-dmux: bam_dmux: SSR before shutdown: scheduling teardown work
[ 9355.877908] wwan wwan0: port wwan0at0 disconnected
```

and then **nothing**. Grepping the whole 645-line record for `panic`, `oops`, `BUG`,
`deadlock`, `stall` or a backtrace returns **no match**. `kernel.panic = 3` is set, so a
panic would have been printed *and* would have rebooted cleanly; neither happened.

The console simply stops mid-teardown ⇒ **a hard hang in kernel context, terminated by a
watchdog reset.** The reset itself prints nothing to the console, which is consistent with a
hardware watchdog rather than the software `panic()` path.

### 5.4 Why this matters

1. It is a **second, independent AP-side defect in the modem teardown path**, distinct from
   the already-solved `wwan0 down` oops (Doc 144 §7 / Doc 147 §4). The console shows the
   hang landing in the `bam_dmux` "SSR before shutdown" teardown — the same code path the
   fatal path traverses (`remoteproc crash detected` → `bam_dmux: SSR before shutdown`),
   which is why it is worth pursuing rather than dismissing as an artefact of the
   experiment.
2. It **blocked the client-0 falsifier**. Any future attempt to drive an AP-side RPM
   request through a modem teardown must use a different route.
3. It is a *prerequisite* for testing any AP-side fix that involves bringing the modem down
   deliberately. A "clean modem restart" is not currently available on this build.

**One observation only.** This was attempted once. It is not claimed to be reproducible, and
no attempt was made to distinguish "always hangs" from "hangs on this occasion".

## 6. The SPM/VMIN plan is wrong as written — do not build the patch

The carried-forward plan (Doc 149, and the memory note `reference_rpm_firmware_and_live_log`)
was:

> Add `"qcom,idle-state-spc",` to `CPU_SLEEP_0`'s compatible (one line in `msm89xx/patches/`),
> rebuild, read `qcom_stats/vmin`.

**That patch would not work, and the reasoning behind it is wrong in one important place.**
This section re-derives the whole chain from the live device and the source tree.

### 6.1 `psci: failed to set PC mode: -3` is benign

The live boot prints:

```
[ 0.000000] psci: OSI mode supported.
[ 0.000000] psci: [Firmware Bug]: failed to set PC mode: -3
[ 0.258423] CPUidle PSCI: Initialized CPU PM domain topology using OSI mode
```

This is not a defect. `psci_1_0_init()` (`drivers/firmware/psci/psci.c:736-741`) detects OSI
support and then **deliberately defaults to PC mode**:

```c
if (psci_has_osi_support()) {
        pr_info("OSI mode supported.\n");
        /* Default to PC mode. */
        psci_set_osi_mode(false);
}
```

The firmware implements only OSI, so it DENIES the PC-mode request (`-3` = DENIED) and
`psci_set_osi_mode()` logs the `[Firmware Bug]` line. The cpuidle domain driver then sets OSI
explicitly and succeeds (`drivers/cpuidle/cpuidle-psci-domain.c:176`), producing the third
line. **The `-3` is the expected result of a firmware that does not implement PC mode, not
evidence of an SPM problem.**

### 6.2 The SPM cpuidle path is disabled *by upstream design*

The carried-forward note said the OpenWrt DT "has everything `cpuidle-qcom-spm` needs except
the compatible". That is true but not operative, and it misses the real reason.

`arch/arm64/boot/dts/qcom/msm8916.dtsi:2591-2636` declares the SAW/ACC blocks as
**reserved**:

```dts
cpu0_acc: power-manager@b088000 {
        compatible = "qcom,msm8916-acc";
        reg = <0x0b088000 0x1000>;
        status = "reserved"; /* Controlled by PSCI firmware */
};
cpu0_saw: power-manager@b089000 {
        compatible = "qcom,msm8916-saw2-v3.0-cpu", "qcom,saw2";
        reg = <0x0b089000 0x1000>;
        status = "reserved"; /* Controlled by PSCI firmware */
};
```

(and identically for cpu1/cpu2/cpu3).

`drivers/cpuidle/cpuidle-qcom-spm.c:160-174` gates the whole driver on an **available** SAW
node:

```c
static bool __init qcom_spm_find_any_cpu(void)
{
        for_each_of_cpu_node(cpu_node) {
                saw_node = of_parse_phandle(cpu_node, "qcom,saw", 0);
                if (of_device_is_available(saw_node)) { ... return true; }
        }
        return false;
}
...
if (!qcom_spm_find_any_cpu())
        return 0;                       /* platform device never registered */
```

`of_device_is_available()` is false for `status = "reserved"`, so `qcom_spm_find_any_cpu()`
returns false, the `qcom-spm-cpuidle` platform device is never created, and
`spm_cpuidle_drv_probe()` never runs. Separately, `drivers/soc/qcom/spm.c` cannot bind the
`qcom,msm8916-saw2-v3.0-cpu` devices either, so `dev_get_drvdata()` would return NULL even if
the probe ran (`cpuidle-qcom-spm.c:114-117`).

Live corroboration on the running unit:

| probe | result |
| :--- | :--- |
| `/sys/bus/platform/drivers/qcom_spm/` | contains only `bind`, `uevent`, `unbind` — **no bound device** |
| `/sys/devices/system/cpu/cpuidle/current_driver` | `psci_idle` |
| `cpuidle` states | `state0 = WFI`, `state1 = cpu-sleep-0` |
| live DT `cpu-sleep-0/compatible` | `arm,idle-state` (only) |

So the live DT does lack `qcom,idle-state-spc` (the carried-forward note was right about
that), but adding it alone would change nothing: the gate is the SAW's `status = "reserved"`,
which exists because **PSCI firmware owns the SAW/ACC registers**. Making those nodes
`okay` to re-enable the SPM path would put the Linux SPM driver in direct conflict with the
secure firmware over the same registers — a plausible way to manufacture a hang of exactly
the kind in §5, and not a change to make on a hypothesis.

**Corrected carried-forward claim.** The earlier note that "`qcom_spm` driver *is* bound" is
wrong: the driver is *registered*, with zero bound devices.

### 6.3 CPR/VMIN is absent, and the "no vmin code" note was imprecise

* `CONFIG_QCOM_CPR=y` and `CONFIG_QCOM_SPM=y` are both set in the built `.config`.
* In Linux 6.12 the CPR driver has moved to **`drivers/pmdomain/qcom/cpr.c`** (not
  `drivers/regulator/qcom-cpr.c`, which does not exist in this tree — the earlier note cited
  a path that is simply absent).
* That file contains **no `vmin` handling at all** (no `vmin`, `VMIN`, `vdd-mx` or
  `sleep-status` references).
* The live DT contains **no `vmin` string anywhere**, no `apc_vreg_corner`, and no
  `cpu-sleep-status` node; `dmesg` shows no CPR probe.

So CPR never probes and the AP never votes VMIN — which is what `qcom_stats/vmin` reports:

```
Count: 0
Client Votes: 0x0
```

`xosd` on the same boot reads `Count: 0`, `Client Votes: 0x7050705`. The corpus recorded
`0x5070507`; the two differ by a byte-pair swap and one of the two is a transcription
error. Flagged, not resolved.

**Net effect on the VMIN/XOSD hypothesis:** the *observation* (AP never votes VMIN; Android
does) stands and is unchanged. The *proposed test* (one-line DT compatible + rebuild) does
not, and is withdrawn. A future test of the same idea needs a different lever — most
plausibly giving the AP a genuine `apc_vreg_corner`/CPR node, which is a port of a driver
and DT subtree, not a one-liner.

## 7. Established vs not established

**Established (this session):**

1. Fatal #10 at AP **9069.600322 s**, Δ **903.674876 s** — the deterministic family.
2. **Client 0 is fatal-correlated: 72 records, all within 9069.596–9070.904 s, 2/2 fatals.**
3. **Client 0 is not the modem**: its sequence counter is contiguous across the SSR.
4. Client 0 requests only `ldoa` (value 3) and `smpa` (value 1), ~18/s for 1.3 s.
5. `smpa`/`ldoa` are the RPM resource names the **AP's** `qcom,rpm-pm8916-regulators` client
   uses (`qcom_smd-regulator.c:967-985`), and both appear in `rpm.bin`.
6. **AP gap ≈ RPM gap throughout** the 331.7 s capture ⇒ the AP was never starved of
   scheduling; the log's silences are genuine RPM idleness, not a measurement artefact.
7. **A manual `echo stop` of the modem remoteproc hangs the AP** (pstore console ends in the
   `bam_dmux` SSR teardown, no panic/oops) and the device resets.
8. **The SPM cpuidle path is disabled by upstream design** (`status = "reserved"` SAW/ACC),
   corroborated by zero bound `qcom_spm` devices and `current_driver = psci_idle`.
9. **The `psci ... failed to set PC mode: -3` line is benign.**
10. **CPR is built but never probes**; there is no `vmin` property anywhere in the live DT.

**Not established:**

* That client 0 **is** the AP. (Strong circumstantial case; no positive identification.)
* Whether the §5 hang is reproducible, and whether it is the *same* defect as anything in
  the fatal path or a separate one.
* Whether the RPM ever **replies** to the modem (unchanged from Doc 150 §8.2).
* What `word[2]` means (Doc 150 §12).
* Whether the 9.33 s modem-side RPM silence (Doc 150 §9) is a class — it did **not** recur
  in this 331.7 s capture either.

## 8. Next experiments, in priority order

1. **Read the four existing modem coredumps.** `scratch/coredump_live/` holds four
   **85 398 475-byte** dumps (`modem_coredump_up919.52.elf`, `…up1822.52.elf`,
   `…up2723.69.elf`, `…up3629.79.elf`) captured at fatals #1–#4. These are the only
   ground-truth view of the modem's own BSS at the fault and have not been analysed.
   Priorities: the `a2_power` state block, the SMSM APPS word (SMEM item 85 word 1), the A2
   client vote list, and the DRX/sleep counters near `FUN_c0ce7fe0`. The coredump has been
   re-armed on the current boot and a watcher is running (`/tmp/coredump_watch.sh`,
   writing to `/overlay/coredump_live`), so fatal #11 onwards will also be captured.
2. **Identify client 0 positively, by a route that does not stop the modem.** Candidates:
   (a) read the RPM's client table from SRAM and match ids to the AP's registered clients;
   (b) provoke an AP RPM request through `qcom-rpmpd` (power-domain transitions) rather than
   through a modem teardown.
3. **Characterise the §5 hang.** Re-attempt once to confirm reproducibility; if it
   reproduces, the console-ramoops + `sysrq`-style instrumentation should be used to find
   where the teardown blocks. This is a self-contained AP-side bug worth fixing on its own.
4. **A long, lossless RPM baseline (30+ min)** to get an honest burst-cadence distribution
   and settle whether the 9.33 s silence is a class.
5. Decode `0x00da` (possibly a completion latency — would settle whether the RPM replies).
6. Fix `word[2]`.

**Withdrawn:** the one-line `qcom,idle-state-spc` DT patch (see §6.2).

## 9. Artifacts

All under `Docs/Modem Stability/evidence/151_f10_and_spm/`:

| File | What |
| :--- | :--- |
| `f10b_final.txt.gz` | the fatal-#10 capture: 8 594 505 B raw → 764 137 B gz; 2271 bursts, 94 252 records, 8919.412–9251.160 s |
| `manual_ssr.txt.gz` | the 11.2 s capture around the manual `echo stop` (2127 lines) |
| `pstore_console_manual_stop.txt` | the complete 645-line `console-ramoops-0` of the boot that the manual stop killed |
| `an_f10.py` | the analyser (burst parser, client census, gap analysis, phase rates) |
| `coredump_watch.sh` | the re-installed coredump watcher |

Device-side scratch (gitignored): `scratch/rpm_re/` (captures, `rpmring`, analysers),
`scratch/coredump_live/` (the four 85 MB dumps).

## 10. One line

Fatal #10 replicates fatal #9 exactly — client 0 is real, is fatal-specific, is **not** the
modem, and requests precisely the two RPM resources the **AP's** regulator client owns — but
the attempt to prove that identity by driving a modem teardown instead produced a **new
AP-side hang** (a watchdog-reset with no panic, mid-`bam_dmx` SSR teardown), and re-deriving
the SPM/VMIN chain from the live device shows the planned DT patch **cannot work** because
the SAW blocks are `status = "reserved"` under PSCI firmware ownership.
