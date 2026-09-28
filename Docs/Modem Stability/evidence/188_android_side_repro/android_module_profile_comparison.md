# Android vs OpenWrt kernel module profile — builtin vs loadable

**Question (operator, 2026-09-22):** *"also check inbuilt and loadble kernel modules, they also may be what
protects 15 minute crash."*

**Answer, in one line:** builtin-vs-loadable is **not itself a mechanism** — the same C code runs either way —
and the census shows **no protection module exists on the Android side**; the only genuine module-level gap on
the modem path is `rpm_master_stats`, which is an *observation* driver and is not loaded on OpenWrt. The
comparison did, however, kill two plausible hypotheses and produce one new control measurement.

Device: Android serial `c2b9103c` (same physical unit as the OpenWrt target — see `README.md` §7).
Captured 2026-09-22/23.

---

## 1. Why builtin vs loadable cannot be the mechanism

`CONFIG_FOO=y` links `foo.o` into `vmlinux`; `CONFIG_FOO=m` links it into `foo.ko`. **The object file is the
same source compiled by the same compiler with the same options.** The runtime behaviour of the driver is
identical. Only three things change:

1. **when** the initcall runs (link order at boot vs. `modprobe` from userspace),
2. **whether** it can be `rmmod`/`insmod`-ed afterwards,
3. **whether it is present at all** — a module that is never loaded is *absent*.

So the operator's hypothesis reduces to a single answerable question: **is a driver that is present on Android
absent (or never loaded) on OpenWrt?** §5 answers it. §6–§7 dispose of the two differences that looked
promising before they were checked.

---

## 2. Android: the authoritative census

| evidence | result |
|---|---|
| `/proc/modules` | **`wlan 3802905 0 - Live 0x00000000 (O)`** — exactly one module |
| `/sys/module/*` | 137 entries: **136 builtin, 1 loadable (`wlan`, `initstate: live`)** |
| `CONFIG_MODULES` | `=y`, `CONFIG_MODULE_UNLOAD=y`, `CONFIG_MODULE_FORCE_UNLOAD=y` |
| `=m` symbols in the config | **15** — and every one is a test/dev driver (table below) |
| `/system/lib/modules/` | 21 `.ko` on disk, none loaded except `wlan` |

The 15 `=m` symbols, with what they are:

```
CONFIG_OPROFILE=m              profiler (test)
CONFIG_IOSCHED_TEST=m          block test
CONFIG_MSM_DMA_TEST=m          DMA test
CONFIG_MSM_BUSPM_DEV=m         bus-performance-monitor mmap helper (dev)
CONFIG_INPUT_EVBUG=m           input event debug
CONFIG_INPUT_GPIO=m            gpio input (dev board)
CONFIG_SPI_SPIDEV=m            userspace SPI
CONFIG_RADIO_IRIS_TRANSPORT=m  FM radio transport
CONFIG_MMC_TEST=m / CONFIG_MMC_BLOCK_TEST=m   MMC test
CONFIG_MOBICORE_SUPPORT=m / CONFIG_MOBICORE_API=m   MobiCore (unused here)
CONFIG_CRYPTO_RNG=m / CONFIG_CRYPTO_ANSI_CPRNG=m / CONFIG_CRYPTO_DEV_QCRYPTO=m  crypto
```

**None of the 15 is on the modem, RPM, SMD, PIL, or power path.** The `=m` set is a stock Qualcomm
`msm_defconfig` artifact; it is not a design choice that protects anything.

`/system/lib/modules/` additionally holds `edl_tcsr.ko` (our own EDL-reboot module, added 2026-09-20) and
`pronto/pronto_wlan.ko` (the `wlan` target).

**Therefore: on Android the entire modem stack is builtin by configuration, and there is no loadable
"protection" module to find.** If a protection existed as a module it would have to appear in this list; it
does not.

---

## 3. OpenWrt: the modem stack is 100 % loadable

`/lib/modules/6.12.94/` in the built rootfs holds 75 `.ko`. The modem/RPM/WWAN path is **10 of them**
(`qcom_bam_dmux`, `qcom_common`, `mdt_loader`, `qcom_pil_info`, `qcom_q6v5`, `qcom_q6v5_mss`,
`qcom_wcnss_pil`, `wcnss_ctrl`, `rpmsg_wwan_ctrl`, `wwan`), all loaded from `/etc/modules.d` at boot:

| `/etc/modules.d` file | modules it loads |
|---|---|
| `bam-dmux` | `qcom_bam_dmux`, `qcom_common` |
| `qcom-rproc` | `mdt_loader`, `qcom_common`, `qcom_pil_info` |
| `qcom-rproc-modem` | `qcom_q6v5`, `qcom_q6v5_mss` |
| `qcom-rproc-wcnss` | `qcom_wcnss_pil`, `wcnss_ctrl` |
| `rpmsg-wwan-ctrl` | `rpmsg_wwan_ctrl` |
| `wwan` | `wwan` |

`modules.d` is processed in **alphabetical** order, so the load order is
`bam-dmux` → `qcom-rproc` → `qcom-rproc-modem` → `qcom-rproc-wcnss` → `rpmsg-wwan-ctrl` → `wwan`.
i.e. **`qcom_bam_dmux` loads before `qcom_q6v5_mss`.** The Android link order is the same relative order
(`drivers/soc/qcom/Makefile:11 bam_dmux.o` … `:45 pil-q6v5-mss`), so **the ordering is not a difference.**

### 3.1 The one module that is built but never loaded

`rpm_master_stats.ko` is installed in the rootfs (`kmod-rpm-master-stats`) and the DT has a matching node
(`rpm: remoteproc { master-stats { compatible = "qcom,rpm-master-stats"; … } }`, added by
`target/linux/msm89xx/patches/824-arm64-dts-qcom-msm8916-rpm-master-stats.patch`) — **but there is no
`/etc/modules.d` entry for it**, and `modinfo` shows **no `alias:` lines**:

```
$ modinfo rpm_master_stats.ko
license:    GPL
srcversion: FF81DBB47D28D17A740317D
depends:
name:       rpm_master_stats
```

A platform driver with no modalias is never autoloaded by the uevent/hotplug path, so unless something
`insmod`s it by hand the node sits unbound. (Doc 175's "OpenWrt enabler" is exactly such a manual load.)

It is an **observation** driver — it reads the RPM's per-master counters and exposes them; it makes no
requests and changes no RPM state. It cannot be a protection.

---

## 4. The mapping — every modem-path function, both sides

Symbol names differ completely between 3.10-downstream (`MSM_*`) and 6.12-mainline (`QCOM_*`), so a naive
symbol diff is misleading. The functional mapping:

| function | Android 3.10 (all **builtin**) | OpenWrt 6.12 | form on OpenWrt |
|---|---|---|---|
| modem remoteproc / PIL | `pil-q6v5-mss` + `pil-q6v5` + `pil-msa` + `peripheral-loader` + `subsys-pil-tz` | `qcom_q6v5_mss` + `qcom_q6v5` + `mdt_loader` + `qcom_pil_info` + `qcom_common` | **modules**, auto-loaded |
| modem data channel | `bam_dmux` + `msm_rmnet_bam` + `rmnet_data` | `qcom_bam_dmux` + `rpmsg_wwan_ctrl` + `wwan` | **modules**, auto-loaded |
| SMD | `smd` | `rpmsg_qcom_smd` (`CONFIG_RPMSG_QCOM_SMD=y`) | builtin |
| SMEM | `smem` | `qcom_smem` (`=y`) | builtin |
| SMP2P | `smp2p` | `qcom_smp2p` (`=y`) | builtin |
| QMI transport | `ipc_router_core` + `ipc_router_smd_xprt` | `qrtr` + `qrtr_smd` + `qcom_qmi_helpers` (`=y`) | builtin |
| RPM transport | `rpm_smd` | `qcom_smd_rpm` (`=y`) | builtin |
| SPM | `spm_v2` | `qcom_spm` (`=y`) | builtin |
| MPM | `mpm_of` | `qcom_mpm` (`=y`) | builtin |
| SSR / subsystem restart | `subsystem_restart` + `subsystem_notif` | `remoteproc` (`=y`) + `qcom_common` (`qcom_add_ssr_subdev`) | builtin + module |
| watchdog | `msm_watchdog_v2` | `qcom_wdt` (`=y`) | builtin |
| thermal | `msm_thermal` + `thermal_monitor` + `tsens8974` + `step_wise` | `qcom_tsens` + `thermal_of` + `step_wise` (`=y`) | builtin |
| RTC | `qpnp_rtc` | `rtc-pm8xxx` (`CONFIG_RTC_DRV_PM8XXX=y`) | builtin |
| RPM stats | `rpm_stats` + `rpm_master_stat` | `rpm_master_stats` (`=m`) | **module, NOT loaded** |
| RPM log | `rpm_log` | — (ring read via `devmem`/`rpmring`) | **absent** |
| DIAG | `diagchar` | — | **absent** |
| TZ log | `tz_log` | — | **absent** |
| SMD packet | `msm_smd_pkt` | — | **absent** |
| resume-IRQ debug | `msm_show_resume_irq` | — | **absent** (debug) |
| Android alarm | `alarm_dev` (`CONFIG_ANDROID_INTF_ALARM_DEV`) | — | **absent** |
| wakelocks / autosleep | `CONFIG_PM_WAKELOCKS=y`, `PM_AUTOSLEEP=y`, `SUSPEND=y` | `# CONFIG_SUSPEND is not set` | **absent** |
| bus scaling | `msm_bus_core` (`CONFIG_MSM_BUS_SCALING=y`) | `# CONFIG_INTERCONNECT_QCOM is not set` | **absent** |
| AP LPM state machine | `lpm_levels` (`CONFIG_MSM_PM=y`) | PSCI cpuidle (`ARM_PSCI_CPUIDLE_DOMAIN=y`) | replaced |

**Every modem-path function has a counterpart on both sides.** The framework names differ; the code exists.

---

## 5. The genuine gaps — and why none of them is a protection

Of the "absent" rows in §4, none sits on the modem's own control path:

* `diagchar`, `tz_log`, `msm_show_resume_irq`, `rpm_log`, `rpm_master_stats` — **observation / debug**. The
  modem cannot tell whether they are present, and their absence cannot cause an assert.
* `msm_smd_pkt`, `msm_rmnet_bam`/`rmnet_data` — an **alternative data path**. OpenWrt carries data over
  `qcom_bam_dmux` + `rpmsg_wwan_ctrl` + `wwan` instead; the corpus already established the port passes
  traffic, so this is a substitution, not a gap.
* `alarm_dev` — the Android `/dev/alarm` interface. It is an interface to timers, not a timer that guards
  anything, and there is no 900 000 ms alarm anywhere on Android (established earlier).
* `lpm_levels`, `CONFIG_SUSPEND`, `msm_bus_core` — **AP-side power policy**; §6 and §7 test them.

`CONFIG_MSM_PM_TIMEOUT_HALT=y` on Android deserves a note because its Kconfig help text reads exactly like our
failure mode:

```
choice
	prompt "Power management timeout action"
	default MSM_PM_TIMEOUT_HALT
	help
	  Selects the Application Processor's action when Power Management
	  times out waiting for Modem's handshake.

	config MSM_PM_TIMEOUT_HALT       bool "Halt the Application Processor"
	config MSM_PM_TIMEOUT_RESET_MODEM bool "Reset the Modem Processor"
	config MSM_PM_TIMEOUT_RESET_CHIP  bool "Reset the entire chip"
```

**But it is dead config.** A whole-tree grep in the reference tree
(`GitIgnore/android_kernel_zte_msm8916/`, 46 096 files) finds `MSM_PM_TIMEOUT` **only** in
`arch/arm/mach-msm/Kconfig` and in the defconfig — **no `.c` or `.h` reads it**. The same is true of the
adjacent `CONFIG_MSM_IDLE_WAIT_ON_MODEM`. These are vestigial msm8960-era symbols. Do not build a hypothesis
on the help text.

---

## 6. Hypothesis killed: "Android suspends the AP and OpenWrt cannot"

**The config difference is real and large:**

| | Android | OpenWrt |
|---|---|---|
| `CONFIG_SUSPEND` | `=y` | **`# CONFIG_SUSPEND is not set`** |
| `CONFIG_PM_SLEEP` | `=y` | unset |
| `CONFIG_PM_AUTOSLEEP` | `=y` | unset |
| `CONFIG_HAS_WAKELOCK` / `CONFIG_WAKELOCK` / `CONFIG_PM_WAKELOCKS` | `=y` | unset |

On OpenWrt the AP **has no system-suspend path at all**. If the ~902 s protection were "the AP periodically
suspends and runs the modem handshake", this would be the answer.

**It is not.** Measured on the live Android device:

```
$ cat /proc/uptime ; uptime
2423.52 9281.29
up time: 00:40:23, idle time: 02:34:41, sleep time: 00:00:00      <-- never slept

$ cat /d/lpm_stats/suspend
[system] suspend:
  success count:       0
  total success time: 0.000000000

$ dmesg | grep -c "PM: suspend entry"
0

$ for d in /sys/bus/msm_subsys/devices/*; do echo "$(cat $d/name): $(cat $d/state)"; done
modem: state=ONLINE

$ dmesg | grep -c "Fatal error on the modem"
0
```

**Android ran 2 423 s — 2.69 × the 902 s clock — with the modem ONLINE the whole time, ZERO fatals, and ZERO
system suspends.** The `[system] suspend` counter is 0 across all ten duration buckets.

The suspend hypothesis is dead. (Caveat, stated honestly: this boot had `adb` attached and
`PowerManagerService.Display` held, so it is a "device kept awake" boot. It therefore proves *"an awake
Android AP does not crash"*, which is exactly the differential — it does not prove that a stock idle dongle
never suspends. `autosleep` was `off`.)

---

## 7. Hypothesis killed: the interconnect (bus-bandwidth) driver

This looked like the strongest lead of the whole comparison:

* Android: **`CONFIG_MSM_BUS_SCALING=y`** → `msm_bus_core` builtin — the downstream bus-scaling framework that
  votes DDR/NoC bandwidth to the **RPM** on behalf of every bus client.
* OpenWrt: **`# CONFIG_INTERCONNECT_QCOM is not set`** → the parent of
  `INTERCONNECT_QCOM_MSM8916` ("Qualcomm MSM8916 interconnect driver", `select INTERCONNECT_QCOM_SMD_RPM`)
  is off, so **no interconnect provider is built**.

The kernel even *has* the driver (`drivers/interconnect/qcom/msm8916.c`, compatibles `qcom,msm8916-bimc`,
`qcom,msm8916-pcnoc`, `qcom,msm8916-snoc`, `#include "icc-rpm.h"` → votes over SMD RPM), and the DT
**declares the providers** (`bimc: interconnect@400000`, `pcnoc: interconnect@500000`,
`snoc: interconnect@580000`, all `#interconnect-cells = <1>`).

**But the DT has zero consumers.** A grep for `interconnects = ` across `msm8916.dtsi` and every
`msm8916*.dts*` returns **nothing** — the only 7 hits for the string `interconnect` in those files are the
`#include` of the bindings header and the three provider nodes themselves.

`icc-rpm` votes only when a consumer calls `icc_set_bw`. **With no consumers, enabling
`CONFIG_INTERCONNECT_QCOM_MSM8916` would register three providers that receive no requests and issue no RPM
votes.** The difference is real (Android's AP does vote bandwidth; mainline's does not) but it is a
**DT/framework gap, not a Kconfig gap**, and flipping the symbol would not close it. Not a mechanism.

---

## 8. What the AP actually does in low power — both sides

Because §6 and §7 both landed on "AP power state", the AP's real low-power behaviour was measured directly.

**Android** (`/d/lpm_stats`, uptime 2 423 s):

```
[cpu0] wfi:             success 407 033
[cpu0] pc:              success  39 664   failed 8 866     <-- power collapse, 18.3 % fail
[cpu0] standalone_pc:   success     654   failed   147
[system] l2-gdhs:       success  51 555                     <-- L2 collapsed
[system] l2-pc:         success       0
[system] suspend:       success       0
```

So the Android AP **enters power collapse (`pc`) heavily** — 39 664 times, with an 18.3 % failure rate — and
**never suspends**.

**OpenWrt's DT declares the same state hierarchy** (this was checked, and an earlier reading of "no idle
states" was wrong):

```
idle-states {
	entry-method = "psci";
	CPU_SLEEP_0: cpu-sleep-0 {
		compatible = "arm,idle-state";
		idle-state-name = "standalone-power-collapse";
		arm,psci-suspend-param = <0x40000002>;
		entry-latency-us = <130>; exit-latency-us = <150>; min-residency-us = <2000>;
		local-timer-stop;
	};
};
domain-idle-states {
	CLUSTER_RET:  cluster-retention  { arm,psci-suspend-param = <0x41000012>; … };
	CLUSTER_PWRDN: cluster-gdhs     { arm,psci-suspend-param = <0x41000032>; … };
};
```

wired through `psci { CPU_PD0..3 → domain-idle-states = <&CPU_SLEEP_0>; CLUSTER_PD →
domain-idle-states = <&CLUSTER_RET>, <&CLUSTER_PWRDN>; }`, with `ARM_PSCI_CPUIDLE_DOMAIN=y`,
`DT_IDLE_STATES=y`, `DT_IDLE_GENPD=y`.

**So the state *declaration* is equivalent. What is NOT yet known is whether OpenWrt's AP actually enters
them** — the suspend param `0x40000002` must be implemented by the PSCI firmware, and the port replaced the
AP-side boot firmware (`hyp = qhypstub`, `tz = TZ.BF.3.0-00714`). If `qhypstub` does not implement PC, the
kernel would fall back to WFI and `state1` would read `usage 0`.

**This is the cheapest open measurement, and it is one command** — when OpenWrt is back:

```
for s in /sys/devices/system/cpu/cpu0/cpuidle/state*/; do
    echo "$(cat $s/name): usage=$(cat $s/usage) time=$(cat $s/time)"
done
```

* `state1 (standalone-power-collapse) usage` in the millions ⇒ the AP power-collapses, same as Android ⇒
  the AP power path is **not** the differential, and this whole axis closes.
* `usage == 0` ⇒ **the OpenWrt AP never power-collapses while Android does 39 664 times in 40 min** ⇒ a
  concrete, mechanical AP-side difference on the exact axis the `a2_power.c` assert lives on.

---

## 9. The image is identified (answers the operator's *"That doesn't look like a stock phone image"*)

```
ro.build.fingerprint : qcom/msm8916_32_512/msm8916_32_512:4.4.4/KTU84P/eng.edwin.20250828:userdebug/test-keys
ro.build.description : msm8916_32_512-userdebug 4.4.4 KTU84P eng.edwin.20250828 test-keys
ro.product.brand     : HiMI          ro.product.manufacturer : HiMI
ro.product.model     : UFI           ro.product.name         : UFI
ro.build.host        : android       ro.build.user           : android
ro.build.date        : 2025-08-28
gsm.version.baseband : HIMI_U01_MODEM_V1.0
ro.board.platform    : msm8916        ro.boot.baseband        : msm
persist.sys.ssr.restart_level : modem
rild.libpath         : /system/vendor/lib/libril-qc-qmi-1.so
ro.sys.usb.default.config : diag,serial_smd,rmnet_bam,adb
```

It is a **vendor engineering build** (`eng.`, `userdebug`, `test-keys`, built 2025-08-28 by user `edwin` on
host `android`), not a stock carrier/ZTE image. That is what the operator saw.

### 9.1 Correction: the modem daemons are RUNNING — the earlier "all absent" reading was wrong

The earlier reading was that `rild`, `qmuxd`, `netmgrd`, `rmt_storage`, `time_daemon` are "all absent".
**They are all running.** Verified three independent ways:

```
init.svc:  ril-daemon running   qmuxd running   netmgrd running
           rmt_storage running  time_daemon running   qcomsysd running
           (ril-daemon1/2, qmiproxy, ssr_setup, ssr_diag, bridgemgrd, dpmd: unset/stopped)

ps:        radio  196  /system/bin/rild
           system 243  /system/bin/time_daemon
           radio  247  /system/bin/qmuxd
           nobody 249  /system/bin/rmt_storage
           radio  274  /system/bin/netmgrd

/proc scan: 196 rild, 243 time_daemon, 247 qmuxd, 249 rmt_storage, 274 netmgrd
```

`ps` here is `toolbox` (`/system/bin/ps -> toolbox`) and it **does** list all five; `toolbox ps` and
`busybox ps` both return 214 lines. There is **no tooling artifact** — the earlier conclusion was simply
wrong. No mechanism should be built on it.

This matters for the R5b ladder: **rung A2 ("stop rild") is not already the state** — it is a real
manipulation with a real control.

---

## 10. What this establishes, and what it does not

**Establishes**

1. **Builtin-vs-loadable is not a protection mechanism.** Android's modem stack is 100 % builtin; OpenWrt's is
   100 % loadable; both run the same functions. Android's 15 `=m` symbols are all test/dev drivers; Android's
   only loadable module is `wlan`.
2. **Every modem-path function exists on both sides** (§4). There is no missing modem driver.
3. **The only built-but-not-loaded module on the modem path is `rpm_master_stats`** — an observation driver
   with no modalias and no `modules.d` entry.
4. **Two hypotheses killed:** the Android/OpenWrt suspend difference (§6, refuted by a 2 423 s
   zero-suspend zero-fatal control), and the interconnect/bus-scaling difference (§7, inert — the DT has zero
   consumers).
5. **`CONFIG_MSM_PM_TIMEOUT_HALT` is dead config** in the reference tree; its help text must not be read as a
   live mechanism.
6. **New control measurement:** Android, modem ONLINE, **0 fatals in 2 423 s = 2.69 × the 902 s clock, with
   `suspend success count = 0`** and the AP entering `pc` 39 664 times.
7. **The Android image is a HiMI vendor engineering build** (`eng.edwin.20250828`), and the modem daemons are
   running (corrects an earlier error).

**Does not establish**

* Why Android does not crash. The comparison narrowed the axis to **AP-side low-power behaviour** and
  produced exactly one cheap decisive test (§8), but no mechanism is proven.
* Whether OpenWrt's AP actually enters power collapse. That is the §8 command, not yet run.
* Anything about the ~902 s clock itself — it remains an always-on time base (Doc 177).

**Next action, in order**

1. **Run the §8 cpuidle command on OpenWrt.** One command; it either closes the AP-power axis or opens the
   first concrete mechanical difference found on it.
2. **Then** the R5b ladder (Doc 188 §10), which now has a corrected baseline: the daemons are running, so
   every rung is a genuine manipulation.
3. Consider adding a **rung A5: hold a wakelock** (`echo test > /sys/power/wake_lock`) — but note §6 already
   shows a held-wakelock, never-suspending Android AP does **not** crash, so this rung is expected to be a
   negative control and is low priority.

---

## 11. SOP compliance (standing rule: every porting-research doc carries one)

| SOP step | done? | how |
|---|---|---|
| Dual-firmware comparative — measure both sides, never blind-patch | **yes** | Android `/proc/config.gz` (md5 `223d98c6f967910afa7883af29a2cac2`) vs the OpenWrt build's `linux-6.12.94/.config` (md5 `4577bc775dc5b6a380e6f5742e2a9f99`); both preserved |
| Ground truth is the reference kernel tree, not memory | **yes** | every Android-only symbol resolved against `GitIgnore/android_kernel_zte_msm8916/` (46 096 files) |
| Check the archive before building an instrument | **yes** | the OpenWrt module list came from the build tree, not a new device run; the suspend question was answered from an existing live device, not a new soak |
| Verify an instrument's SHAPE, not just its presence | **yes** | the `dmesg | grep -c` fatal count was re-run precisely after the loose pattern matched boot-time `subsys_register()` lines (12 → 0) |
| Do not read a name as a definition | **yes** | `CONFIG_MSM_PM_TIMEOUT_HALT`'s help text *looks* like our failure mode; a whole-tree grep proved it dead |
| Read the definition, not the name | **yes** | `INTERCONNECT_QCOM_MSM8916` exists and the DT declares the nodes — but the DT's **zero consumers** makes it inert |
| Re-derive before believing a fresh reading | **yes** | two self-corrections this session: "no idle-states in the DT" (wrong — `sed` range stopped early) and "toolbox `ps` hides the daemons" (wrong — `ps` lists all five) |
| Never rest a claim on a single window | **yes** | the 2 423 s Android control is labelled a *this-boot* measurement, with the adb-attached caveat stated |
| Pre-register before measuring | **n/a** | this is a census/comparison, not a thresholded test; no threshold was chosen after seeing data |
| Do not binary-patch the modem firmware | **yes** | nothing was written to the modem; all work was read-only on both devices |

**Self-corrections on the record** (§6/§7/§9.1): three readings were revised within the session —
"no DT idle states", "toolbox `ps` hides the daemons", and "`MSM_PM_TIMEOUT_HALT` is the protection". All
three are recorded above rather than deleted.
