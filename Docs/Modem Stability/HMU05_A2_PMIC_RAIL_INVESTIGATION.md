# HMU05 — #367 "PMIC lead": does a rail/PMIC difference make Android immune to `a2_power.c:1189`?

**Task:** parked **#367** — *why is Android structurally immune to the `a2_power.c:1189`
fatal?* The thermal work (`../Thermal/HMU05_ANDROID_THERMAL_CONTROL_PORT.md`) pointed at "system-level
power (PMIC rails / DDR-fabric)" and tied it to #367, so this doc chases the **PMIC/rail**
angle specifically.

**Verdict: the PMIC/rail axis is NOT the cause of the A2 fatal.** The modem's power rails
(CX/MX) *are* voted differently by the two stacks — but only during the **boot window**;
both release the vote at handover, and the fatal happens thousands of A2 cycles *after*
boot. Ruling this out (with the three earlier handshake negatives) confirms the fatal is
**internal to the modem's collapse↔bring-up handshake** — the only remaining action is the
baseband client-word trace (`A2_QUIESCE_ASYMMETRY.md` §6).

---

## 1. What "the PMIC lead" was

Android's modem node (`qcom,mss@4080000`) carries explicit **power-rail** votes that the
OpenWrt node does not obviously have:

| Android (`stock_dts_0.dts` `qcom,mss@4080000`) | resolves to | value |
|---|---|---|
| `vdd_cx-supply = <0x67>` | `8916_s1_corner` (RPM **SMPA id 1**, `use-voltage-corner`) | `RPM_REGULATOR_CORNER_SUPER_TURBO` |
| `vdd_mx-supply = <0x113>` | `8916_l3` (RPM **LDOA id 3**) | `vdd_mx-uV = 0x100590` = **1.05 V** |
| `vdd_pll-supply = <0xda>` | `8916_l7` | 1.8 V |

Applied by the PIL driver:
* **CX** — `pil-q6v5.c:86`:
  ```c
  regulator_set_voltage(drv->vreg_cx,
      RPM_REGULATOR_CORNER_SUPER_TURBO, RPM_REGULATOR_CORNER_SUPER_TURBO);
  ```
* **MX** — `pil-msa.c:258`: `regulator_set_voltage(drv->vreg_mx, uv /*=1.05V*/, INT_MAX);`

The downstream corner enum (`include/linux/regulator/rpm-smd-regulator.h`):
`NONE=1, RETENTION=2, SVS_KRAIT=3, SVS_SOC=4, NORMAL=5, TURBO=6, SUPER_TURBO=7`
⇒ Android requests the **absolute maximum corner (7)** for CX.

OpenWrt's equivalent lives in the DTS, not a driver:
* `msm8916.dtsi:1989` (the `mpss` remoteproc node):
  ```dts
  power-domains = <&rpmpd MSM8916_VDDCX>,
                  <&rpmpd MSM8916_VDDMX>;
  ```
* `qcom_q6v5_mss.c:399` (`q6v5_pds_enable`):
  ```c
  dev_pm_genpd_set_performance_state(pds[i], INT_MAX);
  ```
* `rpmpd.c:43`: `#define MAX_CORNER_RPMPD_STATE 6` — so `INT_MAX` clamps to **corner 6**.

The rpmpd domains are the **same RPM resources** as Android's: `cx_s1a_corner`
(`RPMPD_SMPA`, res_id 1, `KEY_CORNER`) ≡ `8916_s1_corner`; `mx_l3a_corner` (`RPMPD_LDOA`,
res_id 3) ≡ `8916_l3`. So this is an apples-to-apples comparison:

| | CX (SMPA id 1) | MX (LDOA id 3) |
|---|---|---|
| **Android** | `RPM_REGULATOR_CORNER_SUPER_TURBO` (downstream enum value 7) | 1.05 V (fixed) |
| **OpenWrt** | rpmpd `opp-level = <6>` = `super_turbo` | rpmpd `opp-level = <6>` = `super_turbo` |

**★ These are the SAME corner — the two numbering schemes are offset by one.** Mainline's
rpmpd OPP table (`arch/arm64/boot/dts/qcom/msm8916.dtsi`, added by commit `2709436`
*"arm64: dts: qcom: msm8916: Add RPM power domains"*, Stephan Gerhold, 2020-10) names its
levels `1=retention, 2=svs_krait, 3=svs_soc, 4=nominal, 5=turbo, 6=super_turbo` — there is
**no `NONE` level**. The downstream enum counts `NONE=1 … TURBO=6, SUPER_TURBO=7`. So
mainline `opp-level=6` = **super_turbo** = downstream `SUPER_TURBO`. ⇒ OpenWrt's
`INT_MAX` clamp to `MAX_CORNER_RPMPD_STATE=6` reaches the **same maximum corner Android
requests** — there is **no corner cap**, and the CX rail is at the same level on both stacks.
(That commit also confirms the design history: mainline *deliberately* replaced Android-style
raw-voltage regulator voting with corner voting — the two are meant to be equivalent.)

> **Remaining caveat (unverified offline):** Android's `8916_s1_corner` node is
> `status="disabled"` (as are `s1_corner_ao`, `s1_floor_corner`, `s2`), yet it is the modem's
> `vdd_cx` supply — so whether that request is a real RPM vote or a dummy no-op isn't
> provable from the DTS alone. It does not change the conclusion (boot-window-only either way).

**★ Series context (confirms the mechanism is deliberate).** The rpmpd node came in
*[PATCH 00/10] Convert MSM8916 to use rpmpd/power domains* (Stephan Gerhold, 2020-09-16,
lore `20200916104135.25085`). The parts that matter here:
* `06 remoteproc: qcom_q6v5_mss: Allow replacing regulators with power domains`
* `09 arm64: dts: qcom: msm8916: Use power domains for MSS/WCNSS remoteprocs`
* `10 arm64: dts: qcom: msm8916-pm8916: Stop using s1/l3 as regulators`
⇒ mainline **deliberately** moved the modem's CX/MX vote from raw-voltage regulators
(Android's model) to rpmpd corners, at the **same maximum level** (`super_turbo`). The
`power-domains` on the `mpss` node are the intended replacement for Android's
`vdd_cx`/`vdd_mx` supplies — not a missing vote.

**★ Authoritative quote (patch 02 of the series):** *"Older SoCs like MSM8916, MSM8939,
MSM8974, MSM8996, … use 'voltage corners' instead of 'voltage levels'. It seems like they
all use exactly the same set of corner values, **a value from 0-6 where 6 is the maximum
corner (super turbo)**."* — i.e. `MAX_CORNER_RPMPD_STATE = 6` **is** super-turbo, so
OpenWrt's `INT_MAX` clamp reaches the same maximum corner Android requests. (Patch 09 also
notes the *old* regulator path had a latent bug — the `super_turbo` flag was unused, so it
only enabled the rail and never actually voted the max corner — which is part of why the
switch to power domains was made.)

## 2. Why it does NOT matter — the vote is boot-window-only

Both stacks treat these as **proxy votes** — held only while the AP boots the modem, then
released, because the **modem firmware votes its own rails** once it is running (it is a
separate RPM client).

* OpenWrt — enabled in the MBA-load path `q6v5_mba_load` → `q6v5_pds_enable`
  (`qcom_q6v5_mss.c:1100`) and **released at handover**:
  ```c
  static void qcom_msa_handover(struct qcom_q6v5 *q6v5) {   /* qcom_q6v5_mss.c:1709 */
      ...
      q6v5_pds_disable(qproc, qproc->proxy_pds, qproc->proxy_pd_count);   /* :1719 */
  }
  ```
* Android — `pil_mss_make_proxy_votes` (boot) / `pil_mss_remove_proxy_votes`
  (`pil-msa.c:280`, after boot).

**Live confirmation** (HMU05 @ 192.168.8.1, modem `running`):
```
cx                              off-0                           0
    genpd:0:4080000.remoteproc      suspended                   0           SW
mx                              off-0                           0
    genpd:1:4080000.remoteproc      suspended                   0           SW
```
The modem's CX/MX proxy children are **suspended** with **performance state 0** — i.e. the
AP's vote has been released (as designed), and the modem is running on its own rail votes
(invisible to the AP's rpmpd).

⇒ The only difference is the **corner value during the few-hundred-ms boot window**
(corner 6 vs 7 for CX; corner vs 1.05 V for MX). The `a2_power.c:1189` fatal fires
thousands of A2 cycles *after* boot, when the AP is no longer voting these rails at all.
A boot-window corner delta cannot cause it.

## 3. What this means for #367

With this, **every AP-side input to the modem** is now accounted for:

| AP→modem interface | tested / status |
|---|---|
| SMSM A2 vote + pc/pc-ack handshake | patch 833 + 834 (down-ack serialisation) — **negative** |
| A2 trigger model (demand-driven port) | patch 834-new — **negative** |
| CX/MX rail proxy vote | **this doc — boot-only, not the cause** |
| boot clocks / regulators / pll | proxy votes, released at handover (same as Android) |

⇒ #367 stands as stated: the fatal is **internal to the modem's collapse↔bring-up quiesce**
(`FUN_c0505308` collapses without waiting on the five client words `0xec320b{ba4,ba8,bac,bd8,be0}`;
`FUN_c0504fc8` then spins on them against a shared budget). The **only** way to prove it is a
per-edge trace of the five client words across a collapse — a **baseband-internal ring** —
which is the open item in `A2_QUIESCE_ASYMMETRY.md` §6.

## 4. Empirical falsification — DONE, NEGATIVE (2026-10-08)

The cheap test was run: **persist the AP's CX/MX vote past handover** by removing the
`q6v5_pds_disable()` call from `qcom_msa_handover` (patch `839`, now retired to
`scratch/a2rail/rejected/`). A **matched pair** of modules was built from the same tree
(±839 only — `q6v5_probe`/`q6v5_remove` verified instruction-identical); the patched module
(`68c40822…`) holds the vote live:

```
cx   on   2147483647   <- INT_MAX -> rpmpd clamps to corner 6 = super_turbo, held for the whole runtime
    genpd:0:4080000.remoteproc  active  2147483647  SW
mx   on   2147483647
    genpd:1:4080000.remoteproc  active  2147483647  SW
```

Both arms ran with `control=auto`, traffic, and **the same `a2_power.c:1189`-reproducing
state**. (The control module's readback is `perf=0`, i.e. the vote *is* released as designed.)

| arm | A2 cycles | `a2_power.c:1189` fatals | rate |
| :-- | --: | --: | --: |
| control (unpatched, vote released, `perf=0`) | 100 | 7 | **7.0 %** |
| **test (vote held at max, `perf=INT_MAX`)** | 111 | 10 | **9.0 %** |

Difference **+2.0 pp** (SE 3.8 pp) ⇒ **no significant change**. Holding the rail vote at the
same maximum corner Android uses does **not** reduce the fatal — it is marginally *higher*.
H3 (vote really held) PASSED on the test arm. Full evidence, harness, and the enforcer traps:
`scratch/a2rail/AB_RESULT.md`. ⇒ the PMIC/rail axis is **closed empirically as well as
analytically**.

## 5. Secondary observation (heat, not #367)

Android's RPM regulator set is finer-grained than mainline's: it defines **AO/SO** variants
(`8916_l7_ao`/`l7_so`, `l3_corner_ao`/`so`, `s1_corner_ao`, `s2_corner_ao`) and marks several
nodes `status="disabled"` (`s1_corner`, `s1_corner_ao`, `s1_floor_corner`, `s2`). Mainline's
`msm8916-pm8916.dtsi` collapses this to a handful of always-on rails (`l2/l5/l7`, `s3/s4`).
This is the more plausible *heat* lead (idle/system power), but it is a **separate** question
from #367 and needs a rail-level power measurement, not a code read.

---

**SOP statement.** Static code/DTS read of the tracked trees
(`GitIgnore/android_kernel_zte_msm8916/`, `openwrt/build_dir/.../linux-6.12.94/`,
`Docs/Modem Stability/Stock_Android_Analysis/dtb/stock_dts_0.dts`) + one read-only live
`pm_genpd_summary` dump. No baseband written, no device state changed. The corner enum and
the RPM resource identity (SMPA id 1, LDOA id 3) were matched across both stacks by
`res_type`/`res_id` and the `qcom,resource-name`/`resource-id` DT properties.
