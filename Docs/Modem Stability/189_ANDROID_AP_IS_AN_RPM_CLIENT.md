# 189 — The Android AP is an RPM *client*; the OpenWrt AP is not

> ## ⚠ RETRACTION 2026-09-23 — the **rate** claim in this document is withdrawn. Read Doc 190 §4.
>
> This document reported Android's RPM log at **429.9 rec/s** against OpenWrt's **224.2 rec/s**
> (**1.92×**) and used it to argue the AP's votes add measurable RPM load. **The pre-registered
> traffic-matched test has now been run and it falsifies that.**
>
> * Android at idle, controlled, n = 5: **226.8 ± 10.0 rec/s**. OpenWrt idle: **224.2 rec/s**.
>   Ratio **1.012** — indistinguishable.
> * The 429.9 reading was an **uncontrolled-window artefact**.
> * Worse, the metric is not a platform constant: **AP CPU load drops it 3.91×** (226.8 → 57.9,
>   reversibly to 227.9). No single-window RPM-rate comparison is meaningful.
>
> **What still stands:** the *structural* finding — Android has `MSM_BUS_SCALING=y` +
> `MSM_DEVFREQ_DEVBW=y`, 107 `msm_bus_scale_client_update_request` call sites, and **17 live
> `/d/msm-bus-dbg` clients with non-zero `ab`/`ib`**; OpenWrt has none of it. What is dead is the
> claim that this shows up as **RPM traffic volume**. Any surviving mechanism must be about the
> **content/level** of the votes, not their count — that is P-B1, and it needs a build.
>
> Also corrected in Doc 190 §3: this document's control boot (2 423 s, 0 fatals) was **too short to
> be evidence**. Android's observed time-to-fatal is **31 517.5 s**, so that window had a ~7.4 %
> chance of containing one. "Android never crashes" becomes "Android crashes at ~1 per 31 500 s
> against OpenWrt's ~1 per 900 s — a ~35× rate difference".

**Status:** new differential found, measured on both sides, **mechanism NOT proven**. One
previously-open lead (VMIN/XOSD) is **closed as a negative**. One previously-"killed"
differential (interconnect / bandwidth voting) is **re-opened**, because the kill was
mis-scoped.

**Why this doc exists.** The operator asked: *"do a deep research on android until you find how
it prevents 15 minute crash or data stall."* This is the result of that sweep. It does not
claim a solved mechanism; it identifies the one axis where Android has an entire **mechanism**
that OpenWrt lacks entirely — rather than a config symbol, a timing tweak, or a driver detail —
and it states precisely what would settle it.

---

## 1. The prior kill on this axis was mis-scoped

The corpus previously closed the interconnect axis (`evidence/188_android_side_repro/
android_module_profile_comparison.md` §7) with this argument:

> OpenWrt has `# CONFIG_INTERCONNECT_QCOM is not set` while Android has `CONFIG_MSM_BUS_SCALING=y`
> … **Inert:** a grep for `interconnects = ` across `msm8916.dtsi` and every `msm8916*.dts*`
> returns **nothing** — zero consumers.

**That test answers the wrong question.** It establishes that *flipping one Kconfig symbol in
OpenWrt is inert*, because mainline's **new** interconnect framework only votes when a DT
consumer calls `icc_set_bw`. But Android does not use that framework. Android uses the **old**
`msm_bus` framework, whose votes are issued from **C code**, not from the DT — so a grep for
DT `interconnects = ` cannot see them and returns nothing **whether or not the votes exist**.

An independent corpus survey (2026-09-23) flagged this unprompted: *"⚠ THIS KILL IS TOO NARROW
— it is an absence-of-a-DT-declaration argument, not a code-path argument… No `icc_set_bw`
caller census was done."* The doc itself already conceded the differential is real
(*"Android's AP does vote bandwidth; mainline's does not"*) while calling it inert.

---

## 2. The differential, measured on both sides

### 2.1 Android — an active RPM client with ~17 live voters

| evidence | value |
| :-- | :-- |
| config | `CONFIG_MSM_BUS_SCALING=y`, `CONFIG_MSM_DEVFREQ_DEVBW=y`, `CONFIG_PM_DEVFREQ=y` |
| qcom bandwidth governors | `DEVFREQ_GOV_MSM_BW_HWMON=y`, `GOV_MSM_GPUBW_MON=y`, `GOV_MSM_ADRENO_TZ=y`, `GOV_CPUFREQ=y` |
| RPM backend | **`drivers/platform/msm/msm_bus/msm_bus_rpm_smd.c`** — line 119 `msm_rpm_send_request(rpm_req)` |
| call sites | **107** `msm_bus_scale_client_update_request` calls across ~40 files |
| live debugfs | **`/d/msm-bus-dbg/client-data/`** with **17 clients**, actively toggling |

The live clients, read from the device (each entry is a timestamped vote history):

```
78b9000.spi  blsp1_uart1  grp3d        mdss_mdp   mdss_reg   msm-rng-noc
qcedev-noc   qcom,cpubw.29  qseecom-noc  scm_pas   sdhc1      usb2
vdec-ddr     venc-ddr     update-request
```

Sample contents (ab = aggregate bandwidth, ib = instantaneous):

```
--- scm_pas ---        --- qcom,cpubw.29 ---      --- msm-rng-noc ---
8.252306870            5684.15967829              0.486630260
curr   : 1             curr   : 0                 curr   : 1
masters: 55            masters: 1                 masters: 1
slaves : 512           slaves : 512               slaves : 618
ab     : 393600000     ab     : 240123904         ab     : 0
ib     : 3936000000    ib     : 799014912         ib     : 800000
```

**`qcom,cpubw.29` is the CPU→DDR bandwidth voter.** It is driven by the CPU-frequency
governor: `arch/arm/mach-msm/devfreq_cpubw.c:69` `msm_bus_scale_client_update_request(bus_client, i)`,
registered at `:228`, and attached to the `msm_cpufreq` governor at `:234`
(`devfreq_add_device(dev, &cpubw_profile, "msm_cpufreq", NULL)`). Its `slaves: 512` is the DDR
slave; it toggles continuously (observed `curr 0` → `curr 1` 0.15 s apart).

### 2.2 OpenWrt — no voter at all

| evidence | value |
| :-- | :-- |
| config | `# CONFIG_INTERCONNECT_QCOM is not set` (so `INTERCONNECT_QCOM_MSM8916`, which `select`s `INTERCONNECT_QCOM_SMD_RPM`, is never built) |
| devfreq | `CONFIG_PM_DEVFREQ=y` but **no qcom devfreq driver exists**: mainline 6.12's `drivers/devfreq/` has **no `qcom/` subdirectory at all** (only exynos, imx, mtk, rk3399, sun8i, tegra) |
| DT | `msm8916.dtsi` has **no `interconnects = ` consumers** |

**⇒ Android's AP issues a continuous stream of DDR/NoC bandwidth votes to the RPM. OpenWrt's
AP issues none.** That is a whole missing *client*, not a missing symbol.

### 2.3 The RPM firmware's resource tree matches the votes

The RPM firmware's own string table (`evidence/149_rpm/rpm_strings.txt`) exposes exactly the
resources the votes target:

```
/clk/bimc        /node/clk/bimc      VDD_BIMC
/clk/snoc        /node/clk/snoc      gcc_bimc_clk
/clk/pcnoc       /node/clk/pcnoc     gcc_sys_noc_axi_clk
                                     gcc_pcnoc_ahb_clk
                                     gcc_mss_q6_bimc_axi_clk   <-- the MODEM's own BIMC AXI clock
                                     gcc_bimc_apss_axi_clk     <-- the AP's
```

So the RPM manages BIMC/SNOC/PNOC bandwidth and clocks, and holds a **separate** clock for the
MSS's Q6 BIMC AXI path and for the APSS's. The AP's votes are inputs to that arbitration.

---

## 3. Measured: Android's RPM processes ~1.9× the records

`rpmring`'s header counter (a byte counter over the ring, advancing in exact multiples of 32)
is readable on **Android** with `devmem` at `0x29dc38` — no tool needed:

```
t0  uptime=5874.32  counter=0x04DAFEE0  (= 81 460 960)
t1  uptime=5904.33  counter=0x04E14B60  (= 81 873 760)
```

Δ = 412 800 bytes = **12 900 records over 30.01 s = 429.9 rec/s**.

The tracked OpenWrt baseline (`evidence/150_rpm_track/baseline_300s.txt.gz`, Doc 150) is
**70 469 records over 314.332 s = 224.2 rec/s**.

**Ratio: 1.92×.**

**⚠ Confound, stated up front.** Doc 174/175 killed an *earlier* rate differential on this
platform (the MCPM collapse rate, "1.88/s vs 0.83/s") by showing that rate is set by
**traffic**, not by platform. The same warning applies here: this comparison is **not
traffic-matched**, the two captures are from different boots, and the Android figure carries
whatever the RIL/netmgrd background does. **Treat 1.92× as suggestive, not as a result.** It
is recorded because it is the first RPM-*log*-rate figure for Android in the corpus and it is
what a traffic-matched re-measurement must be compared against.

---

## 4. Counter-evidence: the RPM is NOT starved of bus traffic on OpenWrt

This cuts against the simplest version of the mechanism and is recorded as such.

Decoding the OpenWrt baseline with `rpm_track.py` gives its resource-name histogram:

```
resource names seen: [('ldoa', 14069), ('bslv', 7206), ('bmas', 5112), ('req.', 3302),
                      ('smpa', 2141), ('clk1', 1878), ('clk0', 1752), ('clka', 1668),
                      ('clk2', 1592), ...]
event ids: 0x00d5 x26464  0x0042 x5820  0x0044 x4372  0x00cb x3445  0x00d2 x3302
           0x00d1 x3117   0x00d4 x3117  0x0144 x2164  ...
```

The names are 4-char little-endian tags and the argument after the name is a **resource id**:

```
R 00200000 fbe3014b 00000024 000000d5 73616d62 00000055   <- 'bmas' id 0x55  (bus master)
R 00200000 fbe2e09b 00000024 000000d5 766c7362 00000072   <- 'bslv' id 0x72  (bus slave)
R 00200000 fbe2e8f3 00000024 000000d5 616f646c 00000004   <- 'ldoa' id 0x04  (AP LDO)
```

**So `bmas` (5112) and `bslv` (7206) requests DO reach the RPM on OpenWrt.** Since OpenWrt's
AP has no bus client, these are almost certainly **the MSS voting its own bus bandwidth** —
which is normal and expected.

**Consequence:** the RPM's bus resources are **not starved** on OpenWrt. So the mechanism
cannot be "the RPM has no bus votes and therefore stalls". Any surviving version must be about
the **aggregate level** the votes set (the AP's votes are far larger — `ab 240 MB/s` from
cpubw alone, `ib 3.9 GB/s` from scm_pas), not about the *presence* of bus traffic.

---

## 5. A lead CLOSED as a negative: VMIN / XOSD

Doc 150 §6.2 listed as an open lead that OpenWrt never issues a `vmin`/`xosd` request. If
Android did, that would have been a clean differential. **It does not.**

`/d/rpm_stats` on Android:

```
RPM Mode:xosd
	 count:0
time since last mode(sec):5742          <-- equal to full uptime: never entered
	 client votes: 0x00000000

RPM Mode:vmin
	 count:0
time since last mode(sec):5742
	 client votes: 0x00000000
```

**Neither mode has ever been entered on Android either, and no AP client votes for either.**
So the VMIN/XOSD gap is **not** an Android-vs-OpenWrt differential. Lead closed.

---

## 6. What this does and does not establish

**Established.**
1. Android's AP runs a complete DDR/NoC bandwidth-voting client to the RPM; OpenWrt's runs
   none. Verified independently four ways: kernel config, source census (107 call sites +
   `msm_bus_rpm_smd.c`), **live** debugfs client state (17 clients with non-zero ab/ib), and
   the RPM firmware's own resource tree.
2. The prior kill of this axis was **mis-scoped** and is retracted; the axis is re-opened.
3. **VMIN/XOSD is closed as a negative** — zero on Android too.
4. Android's MSS power-collapse is healthy: `/d/rpm_master_stats` gives MPSS
   `numshutdowns: 0x9f4` = **2548** cycles, last shutdown at AO-clock 5512.77 s with wakeup at
   5512.90 s (**~0.13 s per cycle**), decoded against the 19.2 MHz always-on tick. So the
   failure on OpenWrt is not "the MSS cannot power-collapse" but "the PC vote stalls".
5. Android's RPM log rate is **429.9 rec/s** vs OpenWrt's **224.2 rec/s** — recorded, but
   **confounded by traffic** and not a result.

**NOT established.** That the missing AP bandwidth votes *cause* the ~902 s fatal. The
counter-evidence in §4 means the naive mechanism is dead, and no measurement yet links the
votes to the `rpm.sync` stall.

---

## 7. The decisive test

**P-B1 (pre-registered).** *If the ~902 s fatal depends on the AP's bandwidth-voting client,
then giving OpenWrt's AP a bandwidth-voting client will change the fatal — either suppressing
it, or moving its beat.*

**Implementation.** Both halves are already in the OpenWrt tree; only the wiring is missing:

1. `CONFIG_INTERCONNECT_QCOM=y` — builds `drivers/interconnect/qcom/msm8916.c` + `icc-rpm.c`,
   whose compatibles (`qcom,msm8916-bimc`/`-pcnoc`/`-snoc`) already match the three provider
   nodes declared in `msm8916.dtsi`.
2. **A consumer** — the step the previous kill proved is mandatory. The natural one is the
   CPU↔DDR path that Android's `cpubw` implements: add `interconnects` phandles plus
   `opp-peak-kBps` to the CPU OPP table / CPU nodes so the OPP framework calls `icc_set_bw`
   on frequency changes. This reproduces Android's `devfreq_cpubw` behaviour with mainline
   plumbing.

**Falsifier, stated in advance.** If the fatal's beat and rate are unchanged with the votes
present — measured from the modem's own uptime (the `modem_up` column, Doc 188 §14.2), over
**≥ 2 beats ≈ 30 min** — then the AP bandwidth-voting client is **not** the differential and
this axis is closed for good.

**Why this is worth a flash.** It is cheap (config + a DT patch), it rides along with the
reflash already planned for the cpuidle test, and it is the **only** remaining axis where
Android possesses an entire mechanism that OpenWrt lacks. Every other surviving axis is a
parameter (DRX config, boot firmware) rather than a missing client.

**Cheaper pre-test, do first.** A **traffic-matched** RPM-log-rate comparison: capture
Android's `0x29dc38` rate under a controlled traffic condition, then OpenWrt's under the
*identical* condition (same ping rate, same idle period, same window length). If the rates
converge, the AP's votes are a small fraction of the RPM's load and P-B1 should be
deprioritised. If Android stays ~2× ahead at matched traffic, P-B1 is strongly motivated.
This costs one 30 s `devmem` pair per side and needs no build.

---

## 8. SOP compliance

**Steps observed.** (a) *Comparative protocol* — every claim about Android is read from the
live device (debugfs, `/proc`, `devmem`) or from the tracked kernel tree, never from a vendor
narrative; every claim about OpenWrt is read from the tracked tree, its `.config`, or committed
evidence. (b) *Verify the deployment, not the name* — the vote census was taken from **call
sites in the source** and from **live debugfs state**, not from a config symbol; the config
symbol was used only as corroboration. (c) *Check the archive before instrumenting* — the
existing Android RPM captures (`174_stationary_android_rpm_baseline/rpm_log_200k.bin.gz`) were
inspected first and found **unusable** (`/d/rpm_log` is the documented trap, and
`decode_rpm_log.py` is retracted for devmem aliasing), so the `0x29dc38` counter route was used
instead of building a new tool. (d) *Pre-registration* — P-B1 and its falsifier are stated
**before** any patch is built. (e) *Measurement hygiene* — the rate comparison's traffic
confound is stated in the same paragraph as the number, and the number is explicitly labelled
not-a-result; the counter-evidence that weakens the hypothesis is recorded in the body rather
than omitted.

**Steps deliberately not taken.** No modem firmware was read, patched, or written. No
credential guessing against the vendor admin UI. The dual-firmware byte-comparison SOP step is
not applicable — this doc makes no claim about the baseband image. No rate was pre-registered
as a threshold, because none is used as a cut in this doc.
