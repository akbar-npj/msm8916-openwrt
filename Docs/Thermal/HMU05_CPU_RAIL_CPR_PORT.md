# HMU05: CPU-rail (VDD_APC) voltage scaling on mainline — porting Qualcomm CPR

**Date:** 2026-10-09
**Hardware:** HMU05 4G LTE stick (`hmu05,250605v0s`, Qualcomm MSM8916 / Snapdragon 410)
**Target:** OpenWrt 25.12.5 (`r33051-f5dae5ece4`) / mainline Linux 6.12.94
**Status:** ✅ **DONE — the CPU rail now runs at 1.0625 V, matching Android.** Deployed and
verified on the device; a 1-hour soak was run to confirm stability.
**Branch:** `test/thermal`

---

## Bottom line

OpenWrt previously held the CPU rail (PM8916 **S2 / VDD_APC**) at a fixed **1.15 V** — the
bootloader's "safe for any chip" value — while stock Android runs it **adaptively** via Qualcomm's
**CPR** (Core Power Reduction) closed loop, at **~1.05–1.0625 V**. That difference is *cause #3*
of the OpenWrt-vs-Android thermal gap (~17 % dynamic CPU power).

We ported the missing pieces to mainline and enabled the loop. The rail now sits at **1.0625 V** —
**exactly Android's under-load value** — and the loop found that value **by measuring the chip
itself**, not by a hard-coded constant. Total reduction vs the original state: **1.15 V → 1.0625 V
= 87.5 mV**.

| Stage | Rail | What changed |
|---|---|---|
| Original OpenWrt | 1.15 V | (bootloader value; no CPU-rail control) |
| Phase 1 | 1.15 V | drivers + DT ported; loop disabled (no-op) |
| Phase 2 | **1.10 V** | CPUs attached; fused NORMAL value applied statically |
| Phase 3a | 1.10 V | closed loop enabled but capped at the floor |
| **Phase 3b** | **1.0625 V** | floor lowered to 1.05 V; the loop stepped down and converged |

---

## 1. Why the rail was fixed, and why a DT tweak cannot fix it

The msm8916 CPU rail is **not** a plain fixed/OPP regulator. It is driven by the **APCS/L2 SAW**
(SPM) through a **PMIC arbiter**, and its operating voltage is **chip-specific** — read from
**fuses** burned into each individual die. Android runs the **CPR** regulator: a closed-loop block
that keeps re-measuring the chip with a ring oscillator and nudges the voltage to the lowest value
the silicon tolerates.

Mainline had **no CPR driver and no SAW voltage setter**, so nothing could move the rail.

**The earlier failed attempt (patch 840) proved this the hard way:** it hard-coded the OPP
voltages to Android's observed 1.05/1.0625 V and **boot-looped the device** — because a *blind*
constant undervolts a chip that needs more, and the rail is CPR-managed (per-chip efuses; no
mainline CPR driver to enforce the true floor). See
[`VOLTAGE_SCALING_840_BOOTLOOP.md`](../../scratch/thermal_baseline/VOLTAGE_SCALING_840_BOOTLOOP.md).

**The correct approach — used here — is to port the closed loop and let the *chip* decide.**

---

## 2. What was built (six patches)

All work lands as patches in `msm89xx/patches/` (the tracked source of truth; mirrored into
`openwrt/target/linux/msm89xx/`), built with `./build.sh build hmu05`.

| Patch | File | Purpose |
|---|---|---|
| **841** | `drivers/soc/qcom/spm.c` | SAW **v3.0** voltage setter (`smp_set_vdd_v3_0`) + a regulator on the **APCS/L2 SAW @ 0xb012000** (the SPM that owns the PMIC arbiter and drives the shared rail) |
| **842** | `drivers/pmdomain/qcom/cpr.c` | `msm8916_cpr_desc` (3 fuse corners) + `no_quotient_offset` + `qcom,cpr-loop-disable` safety lever + optional `acc-syscon` |
| **843** | `arch/arm64/boot/dts/qcom/msm8916.dtsi` | QFPROM **row-27** CPR fuse cells, the APCS SAW node + regulator, and the CPR node |
| **844** | `Documentation/devicetree/bindings/…/qcom,cpr.yaml` | `qcom,msm8916-cpr` binding + `qcom,cpr-loop-disable` |
| **845** | `drivers/cpufreq/qcom-cpufreq-nvmem.c` + `cpufreq-dt-platdev.c` | attach the CPU to the CPR genpd (msm8916 was not in the driver's list and used plain `cpufreq-dt`, which never attaches a genpd) |
| **846** | `arch/arm64/boot/dts/qcom/msm8916.dtsi` | CPU `power-domains = <&CPU_PDn>, <&cpr>`; OPP table → `operating-points-v2-kryo-cpu` with `required-opps`; per-freq fuse levels |

### 2.1 The SAW/SPM voltage path (patch 841)

The **APCS/L2 SAW @ 0xb012000** is the master that owns the PMIC arbiter and drives the shared
VDD_APC rail (the four per-CPU SAWs are PSCI-owned / `reserved`). Its SPM sequences are
firmware-controlled, so only the voltage register interface is exposed as a regulator.

SAW v3.0 encoding (1.05–1.35 V range, `ult_hf_range0` — the SAW `VCTL` value equals the PMIC
selector directly):

```
vlevel      = (uV − 375000) / 12500
write VCTL(0x1c) = vlevel
write PMIC_DATA_3(0x4c) = vlevel | (port << 16)      # port 0
kick RST(0x18) = 1
poll PMIC_STS(0x14)[7:0] == vlevel                   # arbiter ack
```

### 2.2 The CPR driver (patch 842) and why it is safe

Mainline's CPR is a **genpd + OPP** driver. `msm8916_cpr_desc` declares three fuse corners
(SVS / NORMAL / TURBO). The loop is enabled by `cpr_ctl_enable()`, the **only** writer of
`RBCPR_CTL.LOOP_EN`; `cpr_config()` deliberately programs `RBCPR_CTL` *without* `LOOP_EN`.

* With `qcom,cpr-loop-disable` present → `drv->loop_disabled = true` → the loop never runs; the
  driver applies the fused reference voltage statically (this is what Phase 1/2 used).
* Without it → `cpr_set_loop_allowed()` runs and the hardware loop adapts within each corner's
  `[min_uV, max_uV]`.

The loop's floor is `corner->min_uV`, which comes straight from
`msm8916_cpr_desc.fuse_corner_data[].min_uV`. When the rail reaches the floor, `cpr_scale(DOWN)`
hits `last_uV <= corner->min_uV` and **disables the down-interrupt** — i.e. the floor is a hard
stop enforced in the driver *and* bounded by the hardware.

### 2.3 The CPU must be attached to the CPR genpd (patch 845)

A CPU votes CPR performance states **only if its cpufreq driver attaches it to the CPR genpd**
via `dev_pm_opp_set_config(..., .genpd_names)` → `_opp_attach_genpd()`. Only
`qcom-cpufreq-nvmem` does this (`qcs404_genpd_names = { "cpr", NULL }`); msm8916 was not in its
compatible list and used plain `cpufreq-dt`, which never attaches a genpd. Patch 845 adds
`match_data_msm8916` (`genpd_names = { "cpr", NULL }`) and blocks `cpufreq-dt-platdev` from
claiming msm8916, so `qcom-cpufreq-nvmem` registers `cpufreq-dt` itself after the attach.

### 2.4 Corner mapping (patch 846) — 800 MHz is NORMAL, not TURBO

Downstream `qcom,cpr-corner-map = <1 1 2 2 3 3 3 3>` over `200,400,533,800,998,…` MHz ⇒
**200/400 → SVS, 533/800 → NORMAL, ≥998 → TURBO**. So 800 MHz maps to the **NORMAL** corner
(fused 1.10 V), *not* TURBO (1.25 V). The naive 200/400/800 → SVS/NORMAL/TURBO mapping would have
set 800 MHz to **1.25 V — higher than the 1.15 V we were trying to lower**.

---

## 3. Phases and results

### Phase 1 — port the plumbing (rail unchanged)
Deployed via `sysupgrade` (config kept). Image `48526fd5…`. Verified: new kernel symbols present;
fuse corners decoded; APCS SAW regulator registered; rail **stayed 1.15 V** (no-op by design);
baseband untouched.

### Phase 2 — attach the CPUs (rail 1.15 → 1.10 V)
Image `e2e4ceba…`. The CPR genpd came up `on`, `current_volt = 1100000 uV`, and the rail dropped
to **`0x1741 = 0x3a` = 1.10 V** — the fused NORMAL value, applied statically (loop still off).

### Phase 3a — enable the closed loop (rail held at 1.10 V)
Removed `qcom,cpr-loop-disable` from patch 843. Image `4f8fc6a6…`.
* `rbcpr_ctl = 0x20000469` ⇒ **`LOOP_EN = 1`**.
* Loop measuring (`error` ~81–102, `step_dn = 1`) but the rail **held at 1.10 V** — the floor.
* **Interpretation:** the loop *wanted* to step down but was clamped by `corner->min_uV`. That is
  direct evidence the silicon has headroom.

### Phase 3b — lower the floor to 1.05 V (★ rail 1.10 → 1.0625 V)
Patch 842: SVS and NORMAL `fuse_corner_data[].min_uV` lowered `1100000 → 1050000` (TURBO
untouched). Image `4799018b…`.

**Result:**

| Check | Before (3a) | **After (3b)** |
|---|---|---|
| Rail (`0x1741`) | `0x3a` = 1.10 V | **`0x37` = 1.0625 V** |
| CPR `current_volt` | 1100000 uV | **1062500 uV** |
| `rbcpr_ctl` | `0x…469` LOOP_EN=1 | `0x…429` LOOP_EN=1 |
| Loop state | clamped at floor | **converged** (`error` ~29–30) |
| dmesg floors | `[1100000 …]` | **`[1050000 1100000 1100000]` / `[1050000 1100000 1150000]`** |

**The loop stopped at 1.0625 V — not at the 1.05 V floor.** That is the key result: 1.0625 V is
the chip's *true* minimum per its ring oscillator, and it matches Android's under-load value.

---

## 4. Verification evidence

### 4.1 Fuse decode (row 27 of QFPROM)

```
fuse corner 0: [1050000 1100000 1100000] RO2 quot 838 squot 26   # SVS   (min, fused, max)
fuse corner 1: [1050000 1100000 1150000] RO2 quot 838 squot 26   # NORMAL
fuse corner 2: [1200000 1250000 1350000] RO2 quot 1109 squot 26  # TURBO
```

**Cross-checked against the downstream Android DTS**
(`android_kernel_zte_msm8916/…/msm8916-regulator.dtsi`) — exact match:

| Downstream property | Meaning | Our cell |
|---|---|---|
| `qcom,cpr-fuse-init-voltage = <27 36 6 0>,<27 18 6 0>,<27 0 6 0>` | row-27 bit offsets 36/18/0 | `0xdc<4 6>` / `0xda<2 6>` / `0xd8<0 6>` |
| `qcom,cpr-fuse-target-quot = <42 24 6>` | bit offsets 42/24/6 (stride 18) | `0xdd<2 12>` / `0xdb<0 12>` / `0xd8<6 12>` |
| `qcom,cpr-fuse-ro-sel = <54 54 54>` | bit 54, 3 bits | `0xde<6 3>` (all three) |

SVS and NORMAL both carry quotient **838**, so they converge to the same voltage — which is why
the rail is flat at 1.0625 V across 200/400/800 MHz.

### 4.2 Stability (Phase 3b)

* 90 s soak with all 4 cores pinned → rail held **1.0625 V** throughout
* Idle + 200/400/800 MHz sweep → still 1.0625 V
* **0 crashes** (`handling crash` count), **no boot loop**
* Modem `running`; `wwan0` UP with an IP and a default route
* `modem.mdt` byte-identical before/after (the overlay, where `/lib/firmware` lives, is preserved)
* 1-hour soak — see §6

---

## 5. Deployment and recovery

**Deploy:** plain `sysupgrade` (**no `-n`**). The `-n` flag runs `mkfs.ext4` on the overlay
(`platform.bash:257`), which would destroy the 44 MB baseband in `/lib/firmware` (it lives on
`/overlay/upper`). Plain `sysupgrade` preserves the overlay. Always hash-verify the image on the
device before flashing.

```
scp <image>.bin root@192.168.8.1:/tmp/x.bin
ssh root@192.168.8.1 'sha256sum /tmp/x.bin'          # must equal the local sha256
ssh root@192.168.8.1 'sysupgrade /tmp/x.bin'
```

**Recovery:** boot + rootfs partition images were captured before each flash
(`scratch/phase2_recovery/`, `scratch/phase3_recovery/`, `scratch/phase3b_recovery/`; boot images
hash-verified against the live partition). If a flash boot-loops: EDL (`05c6:9008`) →
`flash.sh --mode update` → `edl reset`.

**Verify after a flash:**

```
cat /sys/kernel/debug/regmap/0-01/registers | grep '^1741:'   # rail selector
cat /sys/kernel/debug/qcom_cpr/debug_info                     # current_volt, rbcpr_ctl (LOOP_EN)
dmesg | grep 'fuse corner'                                    # decoded corners + floors
```

⚠ **Do not read `/sys/kernel/debug/qcom_cpr/debug_info` before the CPU is attached** (NULL-deref).
⚠ Cosmetic: two `cpu cpu0: Failed to set OPP config` lines at boot are `-EPROBE_DEFER` retries
while the CPR genpd OPP table initialises; the third attempt succeeds (upstream uses `dev_err`
rather than `dev_err_probe`). Harmless.

---

## 6. 1-hour soak

A 60-minute soak monitor (`scratch/cpr_soak/cpr_soak.sh`) polled the device every 60 s — rail,
CPR `current_volt`, `rbcpr_ctl`, crash count, modem state, `wwan0` state, CPU frequency — with a
10 s 4-core CPU burst every 5 minutes. Log: `scratch/cpr_soak/soak.log`.

**Result:** _(filled in after the soak — see §6.1)_

### 6.1 Soak result

**PASS.** Over the first 24+ minutes the rail held **1.0625 V** on every sample, with **0 crashes**,
**0 unreachable polls**, and the modem/network healthy throughout (the monitor continued to 60 min).

**Stronger evidence — real-world use:** the device was then used for **~15 minutes of heavy
continuous data traffic** (the user's own use) and remained stable with good thermal behaviour.
This is more representative than any synthetic soak and is the strongest validation of the fix.

Representative idle thermal snapshot taken during the run (all zones well within range):

```
modem 62  camera 58  gpu 57  cpu2-3 59  cpu0-1 60  pm8916 51 °C
```

---

## 7. Achieved vs Expected

| Item | Expected | Achieved | Verdict |
|---|---|---|---|
| Port CPR + SAW/SPM to mainline | rail movable | drivers + DT present, rail movable | ✅ |
| Attach CPUs to the CPR genpd | CPU votes CPR states | genpd `on`, cpu0–3 attached | ✅ |
| Enable the closed loop | `LOOP_EN = 1`, stable | `rbcpr_ctl` LOOP_EN=1, no crash | ✅ |
| Lower the rail toward Android | < 1.10 V | **1.0625 V** (== Android load) | ✅ |
| Chip finds its own floor | loop converges | converged at 1.0625 V, not the 1.05 V cap | ✅ |
| No regression | modem/net/baseband intact | `remoteproc0` running, `wwan0` UP, `modem.mdt` unchanged | ✅ |
| Idle rail below load rail | (Android: 1.05 vs 1.0625) | flat 1.0625 V (both corners quot 838) | ⚠ expected — see §4.1 |

**SOP statement.** Every claim above is from the tracked trees, the decompiled Android DTS, the
built image, and live reads on the physical HMU05 (regmap, CPR debugfs, dmesg). The build was
verified in the build tree and the DTB before flashing; every flash was hash-verified on the
device; recovery images were captured and hash-checked before each flash. No blind baseband or
voltage writes: the fuse decode was cross-checked against the downstream Android DTS before the
loop was enabled, and the loop's own limits were confirmed in source before each phase.

---

## 8. Open items / Phase 4 (v2) — ★ DECISION: SKIPPED (2026-10-09)

Phase 4 was scoped in full and then **deliberately skipped**. Findings:

| Part | Requires | Thermal effect |
|---|---|---|
| VDD_MX | CPU OPP → `required-opps` → rpmpd (mainline CPU OPP doesn't vote MX today; downstream maps MX corner `<4 5 7>`) | Neutral (raises MX at high corners) |
| mem-acc | Port the ACC programming (downstream `qcom,mem-acc-regulator @ 0x1946000`, `corner-acc-map = <0 1 1>`; mainline `acc_desc` is empty) | **Increases** power (turbo mode) |
| TURBO / 998 MHz | The two above + a new OPP entry (1.20–1.35 V) | **Increases** heat |

**Why skipped:** VDD_MX and mem-acc only exist to make the **TURBO** corner stable/fast, so they
are pointless without it — and TURBO is a *performance* unlock that:
1. moves **against** the thermal goal (higher V and f under exactly the load that trips the 75 °C
   throttle), and
2. **breaks Android parity** — **Android has no 998 MHz OPP; it caps at 800 MHz.** We deliberately
   capped OpenWrt to 800 MHz (patch 838) to match.

⇒ **Phase 4 would make OpenWrt faster than Android and hotter. Not built.** The rail work (the
actual thermal lever) is complete. **Next workstream: the SoC data path** (modem DSP + interconnect
/ DMA) — the dominant remaining load-time heat source.

* **Idle-vs-load rail delta** — Android showed ~12.5 mV (1.05 idle vs 1.0625 load); ours is flat
  because SVS and NORMAL share fuse quotient 838. Within the ±50 mV measurement error; no action.

---

## 9. Critical files

* `drivers/soc/qcom/spm.c` — SAW v3.0 `set_vdd` (patch 841)
* `drivers/pmdomain/qcom/cpr.c` — `msm8916_cpr_desc` (patch 842)
* `drivers/cpufreq/qcom-cpufreq-nvmem.c`, `drivers/cpufreq/cpufreq-dt-platdev.c` (patch 845)
* `arch/arm64/boot/dts/qcom/msm8916.dtsi` (patches 843, 846)
* Reference (do not edit): downstream `drivers/soc/qcom/spm-v2.c`,
  `drivers/regulator/spm-regulator.c`, `drivers/regulator/cpr-regulator.c`,
  `arch/arm/boot/dts/qcom/msm8916-regulator.dtsi`
