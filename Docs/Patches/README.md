# MSM8916 OpenWrt Kernel and Source Patches Reference Guide

This document is the complete catalog of every patch this project maintains, with
a plain-language explanation of what each one does and why it exists.

There are two patch sets:

| Set | Location | Count | Applied to |
| :-- | :------- | :---- | :--------- |
| **Kernel patches** | [`msm89xx/patches/`](../../msm89xx/patches/) | 43 | The Linux kernel source, via OpenWrt's `target/linux/msm89xx/patches/` |
| **OpenWrt source patches** | [`openwrt-patches/`](../../openwrt-patches/) | 2 | The OpenWrt build tree itself (package recipes and build scripts) |

> **Board support scope.** The board device trees target four MSM8916 4G modem
> sticks: **HMU05**, **UF02**, **UFI001B** and **MF800B**, plus the upstream
> **YiMing UZ801V3**.

---

## 1. Patches Overview

### 1.1 Device Tree — board enablement (801–807)

| Patch | Targets | Description |
| :---- | :------ | :---------- |
| [`801-arm64-dts-qcom-add-devices-makefile.patch`](../../msm89xx/patches/801-arm64-dts-qcom-add-devices-makefile.patch) | `arch/arm64/boot/dts/qcom/Makefile` | Registers the generic modem-stick DTBs (`hmu05`, `uf02`, `ufi001b`, `mf800b`) in the kernel build. |
| [`802-arm64-dts-qcom-msm8916-label-reserved-memory.patch`](../../msm89xx/patches/802-arm64-dts-qcom-msm8916-label-reserved-memory.patch) | `msm8916.dtsi` | Adds the `reserved_memory:` label so board DTS files can append `ramoops` regions. |
| [`803-arm64-dts-qcom-uz801-leds-and-button.patch`](../../msm89xx/patches/803-arm64-dts-qcom-uz801-leds-and-button.patch) | `msm8916-yiming-uz801v3.dts` | Gives the UZ801V3 LEDs standard `LED_FUNCTION_WLAN` / `LED_FUNCTION_WAN` roles, and sets the reset button to active-high/pull-down (matching the other boards). |
| [`804-arm64-dts-qcom-add-msm8916-generic-uf02.patch`](../../msm89xx/patches/804-arm64-dts-qcom-add-msm8916-generic-uf02.patch) | `msm8916-generic-uf02.dts` | Full board DTS for the UF02 stick (buttons, LEDs). |
| [`805-arm64-dts-qcom-add-msm8916-generic-hmu05.patch`](../../msm89xx/patches/805-arm64-dts-qcom-add-msm8916-generic-hmu05.patch) | `msm8916-generic-hmu05.dts` | Full board DTS for the HMU05 stick (SIM/eSIM GPIO hogs, ramoops, LEDs, reset button). |
| [`806-arm64-dts-qcom-add-msm8916-generic-ufi001b.patch`](../../msm89xx/patches/806-arm64-dts-qcom-add-msm8916-generic-ufi001b.patch) | `msm8916-generic-ufi001b.dts` | Full board DTS for the UFI001B stick (SIM GPIO hogs, ramoops, LEDs, reset button). |
| [`807-arm64-dts-qcom-add-msm8916-generic-mf800b.patch`](../../msm89xx/patches/807-arm64-dts-qcom-add-msm8916-generic-mf800b.patch) | `msm8916-generic-mf800b.dts` | Full board DTS for the MF800B stick (battery fuel-gauge, bi-colour LEDs, ramoops). |

### 1.2 BAM-DMUX network driver (808–812, 814, 820, 821, 825, 828, 831–833, 848, 850)

The `qcom_bam_dmux` driver bridges the modem's BAM DMA channels to Linux network
interfaces (`wwan0`, `rmnet*`). This is the largest cluster — it carries the
fixes for the AP↔modem data path across modem restarts and A2 power collapses.

| Patch | Description |
| :---- | :---------- |
| [`808-bam-dmux-stats.patch`](../../msm89xx/patches/808-bam-dmux-stats.patch) | Accurate TX/RX packet & byte statistics; frees TX skbs and clears channel bitmaps on power-off. |
| [`809-bam-dmux-tx-pm-ordering.patch`](../../msm89xx/patches/809-bam-dmux-tx-pm-ordering.patch) | Acquire the runtime-PM reference **before** reserving a TX slot, closing a NULL-deref race with the A2 power-collapse sweep. |
| [`810-bam-dmux-tx-sweep-race.patch`](../../msm89xx/patches/810-bam-dmux-tx-sweep-race.patch) | Guards `start_xmit()` against a slot being swept (freed) under it by `bam_dmux_free_skbs()`. |
| [`811-bam-dmux-deferred-tx-telemetry.patch`](../../msm89xx/patches/811-bam-dmux-deferred-tx-telemetry.patch) | Diagnostics for the deferred-TX path (did a deferred packet ever reach the BAM?). |
| [`812-bam-dmux-preserve-deferred-tx.patch`](../../msm89xx/patches/812-bam-dmux-preserve-deferred-tx.patch) | Stops the quiesce from discarding deferred packets that were only DMA-mapped, never submitted. |
| [`814-bam-dmux-ssr-powerup-retry.patch`](../../msm89xx/patches/814-bam-dmux-ssr-powerup-retry.patch) | Retries the post-SSR power-up poll instead of giving up permanently (a slow modem firmware reload used to leave `wwan0` down until reboot). |
| [`820-bam-dmux-ssr-teardown-nonblocking-cancel.patch`](../../msm89xx/patches/820-bam-dmux-ssr-teardown-nonblocking-cancel.patch) | Makes the RX watchdog cancel non-blocking during teardown to avoid an ABBA deadlock on `state_lock`. |
| [`821-bam-dmux-ssr-teardown-flush-before-powerdown.patch`](../../msm89xx/patches/821-bam-dmux-ssr-teardown-flush-before-powerdown.patch) | Runs the SSR teardown to completion **before** `rproc_stop()` powers the modem (and its BAM) down. |
| [`825-bam-dmux-pc-state-reconcile.patch`](../../msm89xx/patches/825-bam-dmux-pc-state-reconcile.patch) | Reconciles the cached `pc_state` with the wire before branching — a dropped edge used to leave it stale and drive pc-ack timeouts. |
| [`828-bam-dmux-ack-stale-deassert.patch`](../../msm89xx/patches/828-bam-dmux-ack-stale-deassert.patch) | Acknowledges a missed deassert edge so the modem's `a2_task` does not time out (`a2_task.c:3179`). |
| [`831-bam-dmux-defer-dma-release-to-powered-modem.patch`](../../msm89xx/patches/831-bam-dmux-defer-dma-release-to-powered-modem.patch) | Defers releasing DMA channels (which write BAM registers) until the modem/BAM is powered again — avoids the AP hang. |
| [`832-bam-dmux-tx-stats-uaf.patch`](../../msm89xx/patches/832-bam-dmux-tx-stats-uaf.patch) | Snapshots `skb->len` before DMA submission; reading it after the completion callback is a use-after-free. |
| [`833-bam-dmux-down-ack-serialisation.patch`](../../msm89xx/patches/833-bam-dmux-down-ack-serialisation.patch) | Android-parity: resume waits for the modem to ack the down vote before voting up. **Board-gated to HMU05.** |
| [`848-bam-dmux-a2-pin.patch`](../../msm89xx/patches/848-bam-dmux-a2-pin.patch) | Driver support for the `qcom,a2-pin` DT property — holds the modem A2 power domain alive (`pm_runtime_forbid()`). |
| [`850-bam-dmux-reattach-on-already-open.patch`](../../msm89xx/patches/850-bam-dmux-reattach-on-already-open.patch) | Re-attaches the netdev when the modem re-issues `CMD_OPEN` without a matching `CMD_CLOSE` (internal restart the AP did not see as an SSR). |

### 1.3 Remoteproc / modem subsystem (815, 826, 827)

| Patch | Description |
| :---- | :---------- |
| [`815-qcom-sysmon-ignore-wcnss-modem-ssr.patch`](../../msm89xx/patches/815-qcom-sysmon-ignore-wcnss-modem-ssr.patch) | Stops forwarding modem SSR notifications to WCNSS, whose firmware faults when told about modem shutdown. |
| [`826-remoteproc-q6v5-mss-msm-subsys-restart.patch`](../../msm89xx/patches/826-remoteproc-q6v5-mss-msm-subsys-restart.patch) | Android-parity `/sys/kernel/debug/msm_subsys/modem` restart lever; also fixes the `rproc->power` refcount so `rproc_shutdown` actually cycles the modem. |
| [`827-remoteproc-q6v5-mss-gfmux-clk-src-switch-ovr.patch`](../../msm89xx/patches/827-remoteproc-q6v5-mss-gfmux-clk-src-switch-ovr.patch) | Forces the clock on during the GFMUX source switch, matching the downstream/Android power-up path. |

### 1.4 RPMSG (819, 822, 823)

| Patch | Description |
| :---- | :---------- |
| [`819-rpmsg-char-guard-null-eptdev.patch`](../../msm89xx/patches/819-rpmsg-char-guard-null-eptdev.patch) | Guards `rpmsg_char` against a NULL endpoint device. |
| [`822-rpmsg-smd-drain-pollers-before-free.patch`](../../msm89xx/patches/822-rpmsg-smd-drain-pollers-before-free.patch) | Drains outstanding pollers before freeing SMD channels, closing a poll use-after-free that could reset the AP. |
| [`823-rpmsg-wwan-ctrl-no-smd-poll-registration.patch`](../../msm89xx/patches/823-rpmsg-wwan-ctrl-no-smd-poll-registration.patch) | Reports the endpoint TX state without registering the caller in the SMD wait queue (module-level instance of the 822 bug). |

### 1.5 Power, reset & EDL (813)

| Patch | Description |
| :---- | :---------- |
| [`813-msm8916-reboot-to-edl-support.patch`](../../msm89xx/patches/813-msm8916-reboot-to-edl-support.patch) | Kernel support for warm-reboot into Emergency Download (EDL / 9008) mode, matching the `lk2nd` reset sequence. |

### 1.6 Power rail, CPR & CPU (818, 824, 834, 838, 841–846)

| Patch | Description |
| :---- | :---------- |
| [`818-arm64-dts-qcom-msm8916-pm8916-l13-voltage-range.patch`](../../msm89xx/patches/818-arm64-dts-qcom-msm8916-pm8916-l13-voltage-range.patch) | Widens PM8916 L13 to 3.05–3.3 V so the USB HS PHY can set its 3.3 V supply. |
| [`824-arm64-dts-qcom-msm8916-rpm-master-stats.patch`](../../msm89xx/patches/824-arm64-dts-qcom-msm8916-rpm-master-stats.patch) | Adds the `qcom,rpm-master-stats` node (APSS/MPSS/PRONTO slices) for RPM power-collapse accounting. |
| [`834-arm64-dts-qcom-msm8916-cpu-interconnect.patch`](../../msm89xx/patches/834-arm64-dts-qcom-msm8916-cpu-interconnect.patch) | Declares CPU→DDR interconnect paths (`cpu-mem`) with per-OPP bandwidth votes. |
| [`838-arm64-dts-qcom-msm8916-cpu-opp-max-800mhz.patch`](../../msm89xx/patches/838-arm64-dts-qcom-msm8916-cpu-opp-max-800mhz.patch) | Removes the 998.4 MHz CPU OPP, capping the cluster at 800 MHz (Android parity, lower rail voltage). |
| [`841-soc-qcom-spm-v3.0-set-vdd.patch`](../../msm89xx/patches/841-soc-qcom-spm-v3.0-set-vdd.patch) | Adds the SPM v3.0 VCTL/PMIC_DATA voltage-register interface to the APCS/L2 SAW that drives VDD_APC. |
| [`842-pmdomain-cpr-add-msm8916.patch`](../../msm89xx/patches/842-pmdomain-cpr-add-msm8916.patch) | Adds MSM8916 support to the mainline CPR (Core Power Reduction) driver — the AP-side CPU-rail voltage scaling. |
| [`843-arm64-dts-qcom-msm8916-cpr.patch`](../../msm89xx/patches/843-arm64-dts-qcom-msm8916-cpr.patch) | Adds the CPR DT node, QFPROM fuse corners and the `cpr_opp_table` for MSM8916. |
| [`844-dt-bindings-qcom-cpr-msm8916.patch`](../../msm89xx/patches/844-dt-bindings-qcom-cpr-msm8916.patch) | Extends the `qcom,cpr.yaml` binding for the MSM8916 CPR variant. |
| [`845-cpufreq-qcom-nvmem-add-msm8916.patch`](../../msm89xx/patches/845-cpufreq-qcom-nvmem-add-msm8916.patch) | Registers MSM8916 with `qcom-cpufreq-nvmem` and wires the `cpr` power domain. |
| [`846-arm64-dts-qcom-msm8916-cpr-attach-cpus.patch`](../../msm89xx/patches/846-arm64-dts-qcom-msm8916-cpr-attach-cpus.patch) | Attaches the CPUs to the CPR genpd and sets their OPP fuse levels. |
| [`849-arm64-dts-qcom-msm8916-hmu05-a2-pin.patch`](../../msm89xx/patches/849-arm64-dts-qcom-msm8916-hmu05-a2-pin.patch) | HMU05 DTS half of the A2 pin (848): sets `qcom,a2-pin` on `&bam_dmux` so the driver holds the modem A2 powered. Only HMU05 sets it. |

### 1.7 Thermal (847, 999)

| Patch | Description |
| :---- | :---------- |
| [`847-arm64-dts-qcom-msm8916-hmu05-thermal-mitigation.patch`](../../msm89xx/patches/847-arm64-dts-qcom-msm8916-hmu05-thermal-mitigation.patch) | HMU05 thermal zones and cooling maps for Wi-Fi (65 °C) and PMIC (90 °C). |
| [`999-tsens-propagate-eprobe-defer.patch`](../../msm89xx/patches/999-tsens-propagate-eprobe-defer.patch) | Propagates `-EPROBE_DEFER` from TSENS NV calibration so the thermal sensor probes when QFPROM arrives late. |

### 1.8 Other SoC (816)

| Patch | Description |
| :---- | :---------- |
| [`816-qcom-smsm-validate-mbox-before-request.patch`](../../msm89xx/patches/816-qcom-smsm-validate-mbox-before-request.patch) | Skips requesting a mailbox for the local host and validates the `mboxes` phandle first, silencing boot error spam. |

### 1.9 OpenWrt source patches (openwrt-patches/)

| Patch | Targets | Description |
| :---- | :------ | :---------- |
| [`809-mac80211-enable-wcn36xx.patch`](../../openwrt-patches/809-mac80211-enable-wcn36xx.patch) | `package/kernel/mac80211/ath.mk` | Enables the `wcn36xx` (and `ath10k-sdio`) kernel modules on the `msm89xx` target. |
| [`810-rstrip-skip-carrier-mcfg.patch`](../../openwrt-patches/810-rstrip-skip-carrier-mcfg.patch) | `scripts/rstrip.sh` | Excludes the bundled carrier MCFG blobs from `rstrip` — they are modem firmware (`\x7fELF`), not AP executables, and stripping corrupts them. |

---

## 2. Detailed Technical Breakdown

### 2.1 Device Tree — board enablement

#### 801: Device Tree Makefile Registration
- **Target**: `arch/arm64/boot/dts/qcom/Makefile`
- **Purpose**: The upstream kernel Makefile does not list our generic 4G dongle
  targets. This adds them to `dtb-$(CONFIG_ARCH_QCOM)`:
  ```makefile
  dtb-$(CONFIG_ARCH_QCOM) += msm8916-generic-hmu05.dtb
  dtb-$(CONFIG_ARCH_QCOM) += msm8916-generic-uf02.dtb
  dtb-$(CONFIG_ARCH_QCOM) += msm8916-generic-ufi001b.dtb
  dtb-$(CONFIG_ARCH_QCOM) += msm8916-generic-mf800b.dtb
  ```

#### 802: MSM8916 Reserved Memory Node Label
- **Target**: `msm8916.dtsi`
- **Purpose**: Upstream defines `reserved-memory` anonymously. Board DTS files
  need to append `ramoops` buffers, so this adds the phandle label:
  ```dts
  -	reserved-memory {
  +	reserved_memory: reserved-memory {
  ```

#### 803: UZ801V3 LED Functions
- **Target**: `msm8916-yiming-uz801v3.dts`
- **Purpose**: Assigns `LED_FUNCTION_WLAN` (blue, GPIO 6) and
  `LED_FUNCTION_WAN` (green, GPIO 8) so OpenWrt's LED monitor and `uci`
  triggers identify the indicators consistently across board variants.

#### 804: Generic UF02 Board Device Tree
- **Target**: `msm8916-generic-uf02.dts`
- **Compatible**: `"uf02,250605v0s", "qcom,msm8916"`
- **Purpose**: Reset button on GPIO 23 (active-high, pull-down) and tri-colour
  status LEDs — red (GPIO 72), green (GPIO 71, WAN), blue (GPIO 73, WLAN).

#### 805: Generic HMU05 Board Device Tree
- **Target**: `msm8916-generic-hmu05.dts`
- **Purpose**:
  1. **Ramoops**: 1 MB at `0x8db00000` (256 KB record / 256 KB console / 256 KB pmsg).
  2. **Reset key**: GPIO 37 (`key_freset`, active-high, pull-down).
  3. **LEDs**: green GPIO 71 (WLAN), red GPIO 72 (POWER), blue GPIO 73 (WAN).
  4. **GPIO hogs** for SIM routing/power: `sim1_en` (GPIO 119, high), `sim2_en`
     (GPIO 14, low), `sim3_en` (GPIO 12, low), `sim_hotplug` (GPIO 114),
     `bat1` (GPIO 36), `ftest` (GPIO 106).

#### 806: Generic UFI001B Board Device Tree
- **Target**: `msm8916-generic-ufi001b.dts`
- **Purpose**:
  1. **Ramoops**: 1 MB at `0x8db00000`.
  2. **Reset key**: GPIO 37 (`key_freset`, active-high, pull-down).
  3. **LEDs**: red GPIO 22 (POWER), green GPIO 21 (WAN), blue GPIO 20 (WLAN).
  4. **GPIO hogs**: `sim_en` (GPIO 1), `sim_sel` (GPIO 2), `4G_L3` (GPIO 68).

#### 807: Generic MF800B Board Device Tree
- **Target**: `msm8916-generic-mf800b.dts`
- **Purpose**: Board DTS for the MF800B stick: a 2100 mAh / 4.35 V LiPo fuel
  gauge (stock `qcom,v-cutoff-uv` 3.35 V, no `ocv-capacity-table`), the same
  ramoops region at the top of the `0x8bd00000–0x8dbfffff` System RAM range,
  a reset button on GPIO 102 (active-high, pull-down), and three active-high
  bi-colour LEDs (Wi-Fi green GPIO 73 / red GPIO 84,
  Cellular green GPIO 85 / red GPIO 72).

### 2.2 BAM-DMUX network driver

The driver is `drivers/net/wwan/qcom_bam_dmux.c` (all patches in this cluster).
The fixes are interdependent and reflect a long investigation into why the
AP↔modem data path breaks across A2 power collapses and modem restarts.

#### 808: Statistics & Clean Shutdown
Upstream `bam_dmux` omitted packet/byte accounting (LuCI / `ip -s link`
reported 0). This adds `DEV_STATS_INC/ADD` for tx/rx packets and bytes, and on
power-off frees pending TX skbs and clears the channel bitmap so DMA buffers do
not leak and channel state does not corrupt.

#### 809: TX Power-Management Ordering
The runtime-PM reference must be acquired **before** a TX slot is reserved.
`pm_runtime_get_sync()` can run `bam_dmux_runtime_resume()`, which votes
`SMSM_A2_POWER_CONTROL` high; the modem then asserts A2 and
`bam_dmux_pc_irq()` → `bam_dmux_pm_restart()` frees every `tx_skbs[]` slot. If
the slot were reserved first, the skb would be freed under this function and
`bam_dmux_skb_dma_map()` would dereference a NULL `skb_dma->skb`.

#### 810: TX-Sweep Race Guards
An A2 power-collapse sweep can NULL a TX slot while `start_xmit()` is between
`bam_dmux_tx_queue()` and the map call, because `start_xmit` does not hold
`state_lock`. Since the sweep also freed the skb, the patch fails cleanly
instead of dereferencing NULL, and counts every prevented crash.

#### 811: Deferred-TX Telemetry
Diagnostics for the deferred-TX path — counts how many deferred packets were
queued, actually submitted, and discarded with a live skb. This answered, with
data, whether a packet deferred while the resume was in flight ever reached the
BAM.

#### 812: Preserve Deferred TX
A deferred slot was only DMA-mapped, never submitted, so the quiesce never had
a descriptor of its own to terminate — the mapping is still valid for the
rebuilt channel. Freeing them (the old behaviour) threw away the packet
`start_xmit()` had just deferred while waiting for the very wake that ran the
free. Measured before the fix: 27 deferred, 27 wiped with a live skb, 0
submitted.

#### 814: SSR Power-Up Retry
The first power-up attempt polls the modem's A2 line for 3200 ms — enough for a
warm restart but not a full firmware reload (~5.9 s observed). On expiry the
work returned and never ran again, leaving `wwan0` down until reboot. This adds
a bounded retry (`BAM_DMUX_SSR_POWERUP_MAX_RETRIES 20`, ~77 s extra).

#### 820: SSR Teardown Non-Blocking Cancel
`bam_dmux_power_off()` is always reached under `state_lock`; a synchronous
cancel of `rx_watchdog_work` (which wants `state_lock`) is an ABBA deadlock. The
watchdog cancel is made non-blocking (it re-checks under the lock);
`rx_rearm_work` stays synchronous because it does not take `state_lock`.

#### 821: SSR Teardown Flush Before Power-Down
`rproc_stop()` fires the `BEFORE_SHUTDOWN` notifier and then, tens of ms later,
powers the modem down. The teardown work terminates and **releases** the
BAM-DMUX DMA channels, and the BAM lives in the modem's power domain. Left
asynchronous, the release raced the power-down; this patch runs the teardown to
completion before `rproc_stop()` continues.

#### 825: Reconcile `pc_state` With the Wire
`pc_state` only tracks `pc_irq` edges, so a dropped edge leaves it stale-high
while the modem is collapsed, and the branch logic never reads the wire. This
is the highest-rate pc-ack timeout class (40 % of resumes vs 2.3 % when the
wire is high). The patch trusts the wire: it clears the cached edge, and the
resync path re-reads the line under `state_lock`.

#### 828: Acknowledge a Stale Deassert
If the deassert really happened (the wire is low) but `pc_irq()` missed the
edge, the AP's ack toggle is left one short. The modem never sees its deassert
acked, its `a2_task` waits, and it asserts ~15 s later (`a2_task.c:3179`).
Measured: 8 of 8 `a2_task.c:3179` fatals followed a stale edge by 15.0–25.7 s.
The patch re-checks under `state_lock` and acks.

#### 831: Defer DMA Release to a Powered Modem
`dma_release_channel()` → `bam_free_chan()` writes BAM registers, and the BAM
sits in the modem's power domain (`qcom,powered-remotely`). The SSR teardown
always runs with the modem collapsed, so releasing there races the BAM
power-down and a register access to the unpowered BAM wedges the AP. The
release is deferred to `bam_dmux_power_on()`.

#### 832: TX Statistics Use-After-Free
Once the DMA is submitted, the completion callback can free the skb before the
accounting runs; reading `skb->len` then is a use-after-free (observed: one
freed skb accounted ~4 GB). The patch snapshots the length while the skb is
still owned.

#### 833: Down-Ack Serialisation (board-gated to HMU05)
Android parity (`bam_dmux.c ul_wakeup()`): on the HMU05 the resume waits for the
modem to ack the down vote before voting up. A flag set by
`bam_dmux_runtime_suspend()` and cleared by `bam_dmux_runtime_resume()` gates
the wait. **Gated to HMU05** via `of_machine_is_compatible("hmu05,250605v0s")`,
because the UFI001B shares the baseband line but does not manifest the fatal.

#### 848: Board-Gated A2 Pin
Adds the `qcom,a2-pin` DT property. When set, the driver calls
`pm_runtime_forbid()` on `&bam_dmux` (holds `power/control=on` forever), keeping
the modem's A2 power domain alive. Applied from `bam_dmux_netdev_open()` once
the device is runtime-ACTIVE, so the forbid's resume is a no-op.

#### 849: HMU05 DTS A2 Pin
The device-tree half of 848: sets `qcom,a2-pin` on `&bam_dmux` for the HMU05
board only. Its modem (`HIMI_U01_MODEM_V1.0`) can wedge its internal A2 quiesce
handshake when the AP releases the A2 power vote and then asserts
`a2_power.c:1189`; holding the domain on is the workaround. The other boards
(UFI001B, UZ801, MF800B) do not set it and keep normal runtime suspend.

#### 850: Re-Attach on Already-Open
The channel can already be marked open if the modem re-issued `CMD_OPEN`
without a matching `CMD_CLOSE` (e.g. after an internal restart the AP did not
observe as an SSR). The netdev can still be detached; bailing out would leave it
detached and silently kill the data path. `netif_device_attach()` and the
netdev-registration work are idempotent, so the patch re-runs them.

### 2.3 Remoteproc / modem subsystem

#### 815: Sysmon Ignore WCNSS Modem SSR
WCNSS firmware does not implement modem SSR event handling and faults if
notified of modem shutdown. The patch skips those notifications:
```c
if (!strcmp(sysmon->name, "wcnss") && !strcmp(sysmon_event->subsys_name, "modem"))
    return NOTIFY_DONE;
```

#### 826: Android-Parity Clean-Restart Interface
Android's `subsystem_restart` framework exposes `/sys/kernel/debug/msm_subsys/<name>`;
writing `restart` shuts the subsystem down and powers it back up from a
workqueue. Mainline has no equivalent — the only lever is the synchronous
`remoteproc0/state` file, whose stop path blocks the caller inside the q6v5
force-stop handshake (and can be caught by the PMIC watchdog). This provides the
non-blocking node for the modem.
- **Refcount fix**: the lever silently no-ops when `rproc->power > 1` (a stray
  duplicate `start` inflates it, and `rproc_boot` bumps it even when
  short-circuiting). The patch does `atomic_set(&rproc->power, 1)` before
  `rproc_shutdown` so an inflated-refcount restart actually cycles the modem.

#### 827: GFMUX Clock Source Switch Override
Matches the downstream/Android q6v56 power-up path, which forces the clock on
during a source switch. The live Android `GFMUX_CTL` reads `BIT(1)|BIT(8)`; the
patch logs the bootloader value for interpretability.

### 2.4 RPMSG

#### 819: Guard NULL Endpoint Device
Returns early from `rpmsg_char` when the endpoint device is NULL.

#### 822: Drain SMD Pollers Before Free
A task blocked in `poll()`/`ppoll()` on an rpmsg endpoint holds a
`poll_table_entry` linked in `channel->fblockread_event`; it is only unlinked by
`poll_freewait()` once the woken task is scheduled again. `device_for_each_child()`
wakes it but does not wait, while the edge release callback that frees the
channels runs synchronously a few lines later — so the channel is freed while
the poller still points into it, and `poll_freewait()` walks recycled memory.
The patch adds a drain barrier.

#### 823: No SMD Poll Registration in rpmsg_wwan_ctrl
Reports the endpoint's TX state without registering the caller in the SMD wait
queue, removing a reachable instance of the 822 use-after-free without a kernel
flash (see 822 for the in-kernel drain).

### 2.5 Power, reset & EDL

#### 813: Emergency Download (EDL / 9008) Mode
Enables warm-reboot into EDL from `reboot edl`/`reboot dload`, replicating the
`lk2nd` reset sequence:
1. **IMEM cookies**: writes `0x444C4F57`/`0x12345678`/`0x56781234` at
   `0x08600FE0`.
2. **TCSR boot detect**: `qcom_scm_set_edload_mode()` sets bit 0 of
   `TCSR_BOOT_MISC_DETECT` (`0x193D100`). Note: the `QCOM_DLOAD_FULLDUMP` path
   must **not** be used — Linux encodes it into bits[5:4] (0x10), whereas
   MSM8916 PBL checks bit[0] (0x01).
3. **PM8916 PON**: warm-reset config, clears the PMIC watchdog.
4. **PMIC arbiter halt**: SCM service `0x9`, cmd `0x1` (retries cmd `0x2`).
5. **PS_HOLD assert**: writes 0 with system-off priority 130.

### 2.6 Power rail, CPR & CPU

#### 818: PM8916 L13 Voltage Range
`phy-qcom-usb-hs.c` calls `regulator_set_voltage_triplet(v3p3, 3050000, 3300000, 3300000)`.
`v3p3-supply` is PM8916 `l13`, configured fixed at 3.075 V
(`min == max`), so the regulator core rejects the request with
`l13: voltage operation not allowed`. The patch widens the constraints to
3.05–3.3 V (within the physical PLDO range).

#### 824: RPM Master Stats
Adds the `qcom,rpm-master-stats` node with APSS/MPSS/PRONTO slices. Ground
truth is the stock HMU05 DTS: `qcom,rpm-master-stats@60150` with
`qcom,master-offset = <0x1000>` and `qcom,masters = "APSS","MPSS","PRONTO"` →
`0x60150`/`0x61150`/`0x62150` (stride `0x1000`, record `0x50` =
`sizeof(struct rpm_master_stats)`).

#### 834: CPU Interconnect
Declares `interconnects = <&bimc MASTER_AMPSS_M0 &bimc SLAVE_EBI_CH0>` with
`interconnect-names = "cpu-mem"` and per-OPP `opp-peak-kBps` votes (3.2 GB/s and
8.528 GB/s). Note: the CPU→DDR fabric is RPM-only on MSM8916; from the AP this
is a vote, not a control (see the DDR investigation docs).

#### 838: Cap CPU at 800 MHz
Removes the 998.4 MHz OPP so the cluster tops out at 800 MHz, matching
Android's OPP maximum and keeping the CPU rail at a lower voltage.

#### 841: SPM v3.0 Set VDD
Adds the APCS/L2 SAW voltage-register interface. The SAW v3.0 VCTL /
PMIC_DATA_3 selector is the raw PMIC VSET value: for the ULT_HF range used by
PM8916 S2 (VDD_APC), `vlevel = (uV - 375000) / 12500` (1.15 V → 62 / `0x3e`).
The APCS/L2 SAW at `0xb012000` is the master that drives the shared VDD_APC
rail; its SPM sequence is owned by PSCI firmware, so only the voltage registers
(VCTL/PMIC_DATA_3, kicked via RST and polled through PMIC_STS) are touched.

#### 842: CPR for MSM8916
MSM8916 uses a single APC rail (PM8916 S2, SAW/SPM controlled) with three CPR
fuse corners. Per-chip voltages live in QFPROM row 27; there is no
per-corner quotient-offset fuse, so the offset is derived from the difference
between adjacent fuse-corner quotients. The ring-oscillator select fuse is 3
bits (8 step-quotient entries, all 26). This is what drops the CPU rail from
1.15 V to Android's ~1.05 V.

#### 843: CPR DT Node
Adds the CPR node, the QFPROM row-27 fuse cells (SVS/NORMAL/TURBO corners: each
a 6-bit initial voltage — MSB is a sign bit, 10 mV step — and a 12-bit target
quotient, plus a shared 3-bit ring-oscillator select) and the `cpr_opp_table`.

#### 844: CPR Binding
Extends `qcom,cpr.yaml` for the MSM8916 variant (`qcom,msm8916-cpr`), the
`qcom,cpr-loop-disable` bring-up lever, and the MSM8916 fuse-cell layout (no
per-corner quotient offset, no revision cell).

#### 845: cpufreq-nvmem for MSM8916
Registers MSM8916 with `qcom-cpufreq-nvmem` with `genpd_names = { "cpr" }`.

#### 846: Attach CPUs to CPR
Attaches the CPUs to the CPR genpd and sets their `qcom,opp-fuse-level`
(SVS/NORMAL).

### 2.7 Thermal

#### 847: HMU05 Thermal Mitigation
The WCN36XX firmware supports `WCN36XX_HAL_SET_THERMAL_MITIGATION_REQ` (level
0–4; 1–4 progressively disable TX AMPDU aggregation and reduce transmit power),
exposed as the `wcn36xx` cooling device. There is no dedicated WCNSS temperature
sensor, so — like Android's thermal-engine — the trip is driven from the CPU
cluster sensor (tsens5 / cpu0-1) at 65 °C; the PMIC trip is at 90 °C.

#### 999: TSENS Propagate `-EPROBE_DEFER`
`tsens_calibrate_nvmem()` reads QFPROM NVMEM cells; if the provider has not yet
probed it returns `-EPROBE_DEFER`, which the driver used to treat as fatal,
disabling SoC thermal monitoring. The patch propagates it so the probe defers:
```c
ret = tsens_calibrate_nvmem(priv, 3);
if (ret == -EPROBE_DEFER)
    return ret;
```

### 2.8 Other SoC

#### 816: SMSM Validate Mailbox Before Request
In Linux 6.12, `mbox_request_channel()` prints `can't parse "mboxes" property`
whenever `fwnode_property_get_reference_args()` fails. MSM8916 defines
`mboxes = <0>, <&apcs 13>, <0>, <&apcs 19>;` where host 0 is APPS
(`local-host`) and host 2 is the empty AUDIO placeholder. The driver looped
over all hosts unconditionally, spamming boot errors. The patch:
1. Skips the mailbox request for `smsm->local_host` (APPS never IPCs to itself).
2. Uses `of_parse_phandle_with_args()` to validate the phandle first.
3. Propagates `-EPROBE_DEFER` cleanly if the mailbox controller is still probing.

### 2.9 OpenWrt source patches

#### 809: mac80211 wcn36xx Support
Enables `kmod-wcn36xx` (WCN3660/3680 Wi-Fi) and `kmod-ath10k-sdio` in OpenWrt's
mac80211 package: adds the `@TARGET_msm89xx` dependency to `kmod-ath`, defines
`KernelPackage/wcn36xx` with autoload/probe, and enables `WCN36XX_DEBUGFS`.

#### 810: rstrip Skip Carrier MCFG
Every carrier MCFG (`mcfg_sw.mbn`) begins with the `\x7fELF` magic, so `file(1)`
reports it as an ELF executable, and `rstrip.sh` fed it to `sstrip -z`, which
rewrites the header and truncates the blob (29816 → 29813 bytes, SHA-1 changes).
These are modem firmware images, not AP executables. The patch excludes
`*/qcom-carrier-autocfg/mcfg/*` the same way `/lib/firmware` already is.

---

## 3. Maintenance and Patch Ingestion Workflow

When developing or updating patches:

1. **Source location**:
   - Kernel patches → [`msm89xx/patches/`](../../msm89xx/patches/)
   - OpenWrt build-tree patches → [`openwrt-patches/`](../../openwrt-patches/)
2. **Numbering scheme**: three-digit, applied in lexical order. Numbers are
   stable identifiers, not a contiguous sequence — gaps (817, 829, 830, 835–837,
   839, 840) are removed/retired patches and must not be reused casually.
   - `801–807`: device-tree board enablement
   - `808–850`: kernel drivers (BAM-DMUX, remoteproc, rpmsg, power, CPR, thermal)
   - `999`: trailing bug fixes that must sort last
3. **Format**: unified diff (`diff -u` / `git diff`) with `a/...` and `b/...`
   paths.
4. **Integration**: during a build, [`build.sh`](../../build.sh) and
   [`scripts/openwrt-prepare.sh`](../../scripts/openwrt-prepare.sh) synchronize
   [`msm89xx/patches/`](../../msm89xx/patches/) into OpenWrt's
   `target/linux/msm89xx/patches/`, and apply
   [`openwrt-patches/`](../../openwrt-patches/) to the OpenWrt source tree
   before the kernel/package build.
5. **Board-gating**: a few patches are only correct for a specific modem
   revision. Currently only **833** is gated (to HMU05 via
   `of_machine_is_compatible`). Board-specific behaviour is otherwise expressed
   through DT properties (e.g. `qcom,a2-pin` in 849), not `#ifdef`s.
