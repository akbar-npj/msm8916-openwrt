# Doc 171 — The RPM master-stats enabler for OpenWrt (Task 100)

**Status:** PREPARED, NOT BUILT, NOT FLASHED, NOT VERIFIED ON DEVICE.
**Date:** 2026-09-22
**Device:** HMU05 (HMUF02-v5), currently flashed to STOCK ANDROID.

---

## 1. What this doc delivers

The three edits that let OpenWrt measure the **modem's own RPM power-collapse
rate**, the way stock Android already does:

| # | File | Change |
|---|------|--------|
| 1 | `msm89xx/patches/824-arm64-dts-qcom-msm8916-rpm-master-stats.patch` | NEW — adds the `master-stats` DT node and the three MSG-RAM slices |
| 2 | `msm89xx/config-6.12` | `# CONFIG_QCOM_RPM_MASTER_STATS is not set` → `CONFIG_QCOM_RPM_MASTER_STATS=m` |
| 3 | `msm89xx/modules.mk` | NEW `KernelPackage/rpm-master-stats` (deliberately **no** `AUTOLOAD`) |

Once flashed, the counters appear at
`/sys/kernel/debug/qcom_rpm_master_stats/{APSS,MPSS,PRONTO}` after
`modprobe rpm_master_stats`.

## 2. Why it matters

The RPM is the standing suspect for the ~902.3 s fatal: the Q6 power-collapse
vote chain stalls in `rpm.sync` (`0xc08bebd0`), whose RPM flush loops have no
timeout (Doc 170 / `project_q6pc_lpr_framework`). Android exposes a whole
observability layer for this that OpenWrt does not build:

- `/d/rpm_master_stats` — per-master `numshutdowns`, i.e. **the modem's own
  power-collapse rate measured directly**. Android reported MPSS at
  **1.310 / 1.523 / 1.711 per second** over three windows, with APSS and PRONTO
  flat at 0.000/s.
- `/d/rpm_log` and the decoded `rpm_smd` ftrace pair.

OpenWrt has none of it, so every OpenWrt-vs-Android statement about the modem's
collapse cadence has been indirect. This enabler closes that gap with a module
plus a DT node — **no baseband change**, which keeps it inside the standing
"no binary modem patching" directive.

## 3. Ground truth

The stock HMU05 DTS (`GitIgnore/MelbonWhiteStock_Dump/HMU05.dts:5738`) declares:

```dts
qcom,rpm-master-stats@60150 {
        compatible = "qcom,rpm-master-stats";
        reg = <0x60150 0x2030>;
        qcom,masters = "APSS", "MPSS", "PRONTO";
        qcom,master-stats-version = <0x02>;
        qcom,master-offset = <0x1000>;
};
```

So the three records are at **0x60150 / 0x61150 / 0x62150** — stride `0x1000`,
base `0x60000` (the `rpm_msg_ram` window), i.e. offsets **0x150 / 0x1150 /
0x2150**.

Two independent corroborations:

1. **The upstream msm8974 DTS uses the same APSS offset.** In
   `arch/arm/boot/dts/qcom/qcom-msm8974.dtsi:1105` the APSS slice is
   `sram@150` — offset `0x150`, identical. msm8226 likewise.
2. **Record size.** The upstream driver reads
   `sizeof(struct rpm_master_stats)`; the struct is `__packed` with
   4+4+8+8+8+8+4+4+4+4+8+8+8 = **80 bytes (0x50)**. Slices at stride `0x1000`
   do not overlap at size `0x50`.

## 4. The DT change (patch 824)

Two hunks in `arch/arm64/boot/dts/qcom/msm8916.dtsi`:

**(a)** `rpm: remoteproc` gains a `master-stats` child, matching the upstream
msm8974/msm8226 idiom exactly (same node name, same properties, same
indentation style):

```dts
master-stats {
        compatible = "qcom,rpm-master-stats";
        qcom,rpm-msg-ram = <&apss_master_stats>,
                           <&mpss_master_stats>,
                           <&pronto_master_stats>;
        qcom,master-names = "APSS",
                            "MPSS",
                            "PRONTO";
};
```

Note the property is `qcom,master-names` (upstream driver), **not**
`qcom,masters` (downstream name in the stock DTS). Using the downstream name
would make `of_property_count_strings()` fail and the probe return its error.

**(b)** `rpm_msg_ram` becomes a bus and gains the three slices:

```dts
rpm_msg_ram: sram@60000 {
        compatible = "qcom,rpm-msg-ram";
        reg = <0x00060000 0x8000>;

        #address-cells = <1>;
        #size-cells = <1>;
        ranges = <0 0x00060000 0x8000>;

        apss_master_stats: sram@150  { reg = <0x150  0x50>; };
        mpss_master_stats: sram@1150 { reg = <0x1150 0x50>; };
        pronto_master_stats: sram@2150 { reg = <0x2150 0x50>; };
};
```

`rpm_msg_ram`'s own `reg` and `compatible` are unchanged, so the existing
consumer (`smem@86300000`'s `qcom,rpm-msg-ram = <&rpm_msg_ram>`) is unaffected —
only its children become addressable.

**Deliberate deviation from upstream:** msm8974 declares the slices as `0x14`
(20 bytes) while the driver reads 80. Because `devm_ioremap()` rounds to page
granularity the over-read does not fault, so upstream gets away with it — but
`0x50` is the correct size and is what is used here.

## 5. The driver, and why the module is not autoloaded

`drivers/soc/qcom/rpm_master_stats.c` is upstream, already in the 6.12.94 tree,
and ships:

```c
/*
 * No MODULE_DEVICE_TABLE intentionally: that's a debugging module, to be
 * loaded manually only.
 */
```

so the `KernelPackage` deliberately omits `AUTOLOAD`. Load it by hand:

```sh
modprobe rpm_master_stats
ls /sys/kernel/debug/qcom_rpm_master_stats/
cat /sys/kernel/debug/qcom_rpm_master_stats/MPSS
```

A measurement loop should read `Shutdown count` (that is `num_shutdowns`) on a
fixed interval and difference it, exactly as the Android side did — giving a
**per-second collapse rate for the modem**, directly comparable to Android's
1.310–1.711/s.

## 6. What is verified, and what is NOT

**Verified:**
- Patch 824 applies cleanly to the live tree — `patch -p1 --dry-run` succeeded
  against the post-801..823 state of `msm8916.dtsi`.
- The offsets match the stock HMU05 ground truth, and independently match the
  upstream msm8974 APSS offset (`0x150`).
- The record size (0x50) equals the driver's `sizeof(struct rpm_master_stats)`.
- The driver source, the Kconfig symbol and the module filename all exist in the
  6.12.94 tree the build actually uses.

**NOT verified — do not treat any of it as working:**
- **Not compiled.** No build was run.
- **Not flashed.** The device is on stock Android (a soak is running).
- **Not confirmed that the counters are non-zero or that the region is readable
  on OpenWrt.** Reading the RPM message RAM is a read-only debugfs operation and
  upstream does exactly this on msm8974/msm8226, but it has never been exercised
  on this device from OpenWrt.
- **Not confirmed that a config change of this kind forces a full kernel
  re-prepare.** `./build.sh guard --deep` short-circuits on the tracked-vs-live
  drift this change creates, so the re-prepare question is unanswered. Expect
  the next build to re-sync (and possibly re-prepare) — run
  `./build.sh guard` afterwards and `diff -r msm89xx openwrt/target/linux/msm89xx`
  to confirm the tree is consistent, per Doc 160.

## 7. Known confound — the Android comparison is a BUS baseline

Android's 1.310–1.711/s MPSS rate was measured **while the device was on a
moving bus** (Doc 170 PART 35). The collapse cadence is driven by the
paging/DRX cycle, which mobility perturbs, so **that number is a BUS baseline,
not a stationary one.** Comparing an OpenWrt measurement against it would
inherit both platforms' environments.

The correct use of this enabler is therefore:
1. measure OpenWrt's MPSS rate;
2. re-measure Android **stationary**;
3. compare. Until (2) exists, an OpenWrt number can be compared only against
   itself over time (e.g. does the rate collapse before a fatal?).

## 8. SOP compliance

Per the standing rule that every porting research doc states which SOP steps
were taken and which were skipped.

**Taken:**
- **Ground-truth comparison (the mandatory dual-firmware protocol).** The
  offsets were NOT inferred from a pattern — they were read out of the stock
  HMU05 DTS, and then cross-checked against an independent second source (the
  upstream msm8974 DTS's `sram@150`) and against the driver's own struct size.
  This is the step the fabricated-docs incident and the Doc 166 §5.4
  "mechanism that fits and does not exist" incident both say not to skip.
- **Read the definition, not the name.** The driver's `probe()` was read in
  full rather than assumed: it needs one phandle per master resolved at
  *address index 0*, which is why three separate nodes are required instead of
  one node with a `reg` array. The `qcom,master-names` vs `qcom,masters`
  difference was found the same way.
- **Verified the mechanism exists in the kernel being run.** The driver, Kconfig
  symbol, Makefile entry and struct layout were confirmed in the 6.12.94 tree
  the build actually reads, not in a doc.
- **Patch verified by dry-run** against the live post-823 tree.
- **No baseband change**, consistent with the standing directive.

**Skipped, deliberately:**
- **No build.** The device is on Android and a soak is running; building now
  would produce an unverifiable artifact and could disturb the soak.
- **No flash, no on-device verification.** Cannot be done until the HMU05
  returns to OpenWrt.
- **No `guard --deep` re-prepare answer** — it short-circuits on the drift this
  change creates by design.

**Not claimed:** that the counters will be non-zero, that the module loads, that
the DT node binds, or that any of this changes the 902.3 s fatal.

---

## Files changed by this doc

```
msm89xx/patches/824-arm64-dts-qcom-msm8916-rpm-master-stats.patch   (new)
msm89xx/config-6.12                                                 (1 line)
msm89xx/modules.mk                                                  (+18 lines)
```
