# HMU05 Thermal Investigation: Reassessing the Earlier +15°C Claim

**Device:** HMU05 4G Modem Stick (`hmu05,250605v0s`, Qualcomm MSM8916 / Snapdragon 410)  
**Comparison:** Stock ZTE Android 4.4 / Linux 3.10.28 vs. OpenWrt (Mainline Linux 6.12)  
**Report Date:** October 2026  

> **Correction (2026-10-08) — read before using this report.** The raw CSVs behind this
> report do **not** support a settled **+15 °C idle offset**. Recomputing them: OpenWrt −
> Android idle means are **+3–7 °C**, and every idle capture is a cooling (or boot-warmup)
> transient, not an equilibrated baseline. The real, corroborated gap is **load-dependent**
> (~+12 °C across five TSENS sensors under LTE). The source audit also finds a TSENS
> calibration offset unlikely, and refutes the premise of root cause #1 below — Android's
> idle capture holds `a2_pwr` **constant** (A2 on), i.e. Android is in the same A2 state as
> OpenWrt's pin. The six ranked causes carry **estimated**, not measured, °C values. The live
> thermal-engine config weakens the modem/PA-throttling claim: its `fusion` mitigation is level
> 0 at 55/60 °C and level 1 only at 105 °C; the observed LTE trace instead shows GPU limiting
> at 55 °C. A current Android snapshot found `mpdecision` running with all four CPUs online,
> and non-zero CPU0 `standalone_pc` and `pc` idle counters. CPR/voltage remains an unmeasured
> source gap. See the full evidence and current recommendation in
> [Docs/Modem Stability/HMU05_OPENWRT_ANDROID_THERMAL_ROOT_CAUSE.md](Modem%20Stability/HMU05_OPENWRT_ANDROID_THERMAL_ROOT_CAUSE.md),
> which supersedes this report's idle-baseline, root-cause, and action-plan conclusions. The
> earlier CPU/A2 ideas below are retained as hypotheses, not validated solutions.

> **Live Android verification (2026-10-08 09:10 UTC; read-only SSH to `192.168.100.1`).**
> `thermal-engine` (PID 208) and `mpdecision` (PID 1201) were running. CPUs 0–3 all reported
> online; CPU0's cumulative idle counters showed entries into `standalone_pc` (3,873) and `pc`
> (105,760), as well as WFI (48,008). TSENS hardware sensors 0/1/2/4/5 read 50/46/44/47/48 °C;
> PMIC and battery zones read 39.486 °C and 33 °C. The PA VADC node returned `Result:36 Raw:7b49`.
> This is one snapshot, not a comparative load test: it disproves the categorical claim that
> Android always keeps CPUs 1–3 offline, but does not establish all-time hotplug behavior or
> thermal causality. Do not apply the hotplug, lower-trip, undervolting, or modem-throttle
> recommendations below as written; their claimed temperature benefits were never measured.

---

## Historical Executive Summary (superseded; retained for audit)

On the HMU05 modem stick, telemetry shows that under stock Android, idle and routing operating temperatures sit between **49°C and 54°C** (measured via Qualcomm TSENS hardware sensors 0–5). Under OpenWrt running mainline Linux 6.12, the device idles at **65°C to 70°C**—consistently **15°C higher**.

The TSENS mathematical conversion formula, calibration fuses, and hardware registers are **identical** across both kernels. The +15°C difference is **real physical thermal dissipation** resulting from six compounding architectural and configuration differences between Qualcomm's downstream vendor BSP and OpenWrt's upstream kernel:

| Rank | Root Cause | Android Behavior | OpenWrt Behavior | Thermal Impact |
|:---:|---|---|---|:---:|
| **1** | **Modem A2 Power Domain** | Collapses into deep sleep when idle (`ul_powerdown`) | Pinned active 24/7 via `/usr/sbin/modem-a2-hold` | **+5°C to +7°C** |
| **2** | **CPU Core Hotplugging** | `/system/bin/mpdecision` keeps CPU1–3 **offline** | All 4 cores online 100% of the time | **+3°C to +4°C** |
| **3** | **CPU Voltage & CPR** | Dynamic closed-loop undervolting via CPR (~0.90V) | Fixed high bootloader voltage (~1.15V–1.25V) | **+3°C to +5°C** |
| **4** | **Thermal Trip Points** | Active thermal mitigation starts at **60°C** | Passive trip point starts at **75°C** | **Sets +15°C ceiling** |
| **5** | **CPU Idle / SPM vs PSCI** | Hardware SAW2 SPM Standalone Power Collapse (`spc`) | Generic PSCI; falls back to shallow WFI | **+2°C to +3°C** |
| **6** | **Modem PA & Wi-Fi Power** | Userspace `thermal-engine` throttles PA; BMPS sleep | No PA thermal throttle; continuous AP beacons | **+1°C to +2°C** |

---

## Earlier Root-Cause Hypotheses (not established)

### 1. Modem A2 Power Domain Pinned Awake (`modem-a2-hold`)
* **Stock Android:** In Qualcomm's downstream `bam_dmux.c`, runtime power management (`ul_powerdown()` / `ul_wakeup()`) is fully active. When no packets are transmitted over the cellular interface, the AP releases its vote on `SMSM_A2_POWER_CONTROL`. The modem baseband (Hexagon DSP, BAM DMA engines, shared memory buses) completely powers down the **A2 clock/power domain**.
* **OpenWrt:** OpenWrt has an unresolved handshake bug with the HMU05 modem firmware where cycling A2 power down and back up causes the modem to assert `a2_power.c:1189` (A2 power-up quiesce timeout). To prevent the modem from crashing every ~60 seconds, OpenWrt added a workaround daemon:
  ```sh
  # /usr/sbin/modem-a2-hold
  echo on > /sys/devices/platform/soc@0/4080000.remoteproc/4080000.remoteproc:bam-dmux/power/control
  ```
* **Consequence:** By holding `power/control = on`, BAM-DMUX never runtime-suspends. The modem's entire A2 power domain, DSP interfaces, and DMA clocks remain **energized 24 hours a day, 7 days a week**, dissipating continuous static and dynamic heat even when no traffic is passing through.

---

### 2. CPU Hotplugging: `mpdecision` vs. 4 Always-On Cores
* **Stock Android:** Stock Android runs `/system/bin/mpdecision`. Telemetry and process dumps (`Docs/Modem Stability/Stock_Android_Analysis/18_lsof_and_sockets_dump.txt`) reveal that `mpdecision` holds file descriptors to:
  ```
  /sys/devices/system/cpu/cpu1/online
  /sys/devices/system/cpu/cpu2/online
  /sys/devices/system/cpu/cpu3/online
  ```
  Whenever the load is light (which is almost always on a dedicated router), `mpdecision` writes `0` to offload cores 1, 2, and 3. **Only CPU0 remains online.** Cores 1–3 are completely gated and severed from power.
* **OpenWrt:** OpenWrt has no CPU hotplugging service. All 4 Cortex-A53 cores remain online continuously. Each core executes idle loops, timer interrupts, scheduler ticks, and network softirqs.
* **Consequence:** Four Cortex-A53 cores consume roughly 3.5× the static leakage and idle clock power of a single active core.

---

### 3. Missing Core Power Reduction (CPR) & Dynamic Voltage Scaling
* **Stock Android:** Qualcomm implemented **Core Power Reduction (CPR)** (`qcom,cpr` in `msm8916-regulator.dtsi`). CPR reads silicon process fuses (speed-bin and corner targets) and measures ring oscillator speeds in real-time. It dynamically lowers the PMIC buck regulator (`PM8916 SMPS2` / `VDD_APC`) to the absolute lowest safe voltage (undervolting down to ~0.90V – 0.95V).
* **OpenWrt:** In mainline Linux 6.12, CPR for MSM8916 is completely unimplemented. Furthermore, in `arch/arm64/boot/dts/qcom/msm8916.dtsi`:
  ```dts
  cpu_opp_table: opp-table-cpu {
      compatible = "operating-points-v2";
      opp-shared;
      opp-200000000 { opp-hz = /bits/ 64 <200000000>; };
      opp-400000000 { opp-hz = /bits/ 64 <400000000>; };
      opp-800000000 { opp-hz = /bits/ 64 <800000000>; };
  };
  ```
  There is **no `cpu-supply` property** linked to the PMIC buck regulator, and **no `opp-microvolt` values**.
* **Consequence:** While CPU frequency steps down to 200 MHz, **the voltage never drops**. The CPU runs at the fixed bootloader default voltage (typically 1.15V to 1.25V). Because power scales quadratically ($P \propto V^2$):
  $$\frac{(1.20\text{ V})^2}{(0.95\text{ V})^2} \approx 1.60 \implies \mathbf{+60\%\text{ more dynamic power}}$$
  Additionally, semiconductor leakage current at 28nm increases exponentially with higher supply voltage and temperature.

---

### 4. Thermal Mitigation Threshold Discrepancy
* **Stock Android:** In `arch/arm/boot/dts/qcom/msm8916.dtsi`:
  ```dts
  qcom,msm-thermal {
      qcom,limit-temp = <60>;             /* Throttles frequency at 60°C */
      qcom,temp-hysteresis = <10>;
      qcom,core-limit-temp = <80>;        /* Hotplugs cores offline at 80°C */
  };
  ```
  Additionally, Android's userspace `/system/bin/thermal-engine` monitors TSENS every 250ms and enforces strict thermal throttling starting at 55°C–60°C.
* **OpenWrt:** In mainline `arch/arm64/boot/dts/qcom/msm8916.dtsi`:
  ```dts
  cpu0-1-thermal {
      trips {
          cpu0_1_alert0: trip-point0 {
              temperature = <75000>;      /* Passive throttling ONLY at 75°C! */
              hysteresis = <2000>;
              type = "passive";
          };
      };
  };
  ```
* **Consequence:** In OpenWrt, the kernel does **zero thermal throttling below 75°C**. The thermal governor allows heat to build up without resistance until 75°C is reached. In Android, the governor actively clamps the device at ~55°C–60°C.

---

### 5. CPU Idle States: Hardware SPM vs. Incomplete PSCI
* **Stock Android:** In `msm8916-pm.dtsi`, Qualcomm's `spm-v2` programs the Subsystem Power Manager (SAW2) hardware blocks (`0xb089000`, `0xb099000`, etc.) with specialized microcode for:
  * `wfi`: Wait-for-interrupt
  * `spc`: Standalone Power Collapse (gates power rails to individual cores)
  * `pc`: Cluster power collapse (collapses L2 cache and notifies RPM to enter sleep)
* **OpenWrt:** Mainline Linux 6.12 on ARM64 disables `ARM_QCOM_SPM_CPUIDLE` and relies on `arm,psci-1.0` SMC calls. However, on HMU05, the firmware is the stock 2016 32-bit Qualcomm TrustZone (`tz.mbn`). Stock TZ does not implement full PSCI cluster sleep or RPM coordination. When `CPU_SUSPEND` is invoked, it falls back to basic clock-gated `wfi` without rail collapse.

---

### 6. Modem RF Power Amplifier (PA) & Wi-Fi Power Profiles
* **Stock Android:** `/system/bin/thermal-engine` monitors modem PA temperatures and issues QMI commands to throttle PA transmit power and step down LTE uplink rates if the dongle gets warm.
* **OpenWrt:** There is no modem PA thermal daemon. The modem operates at maximum transmission power regardless of die temperature. Furthermore, OpenWrt's upstream `wcn36xx` driver runs hostapd in full AP mode without aggressive 802.11 power saving.

---

## Earlier Proposed Solutions & Action Plan (superseded; do not apply as written)

### Phase 1: Immediate User-Space & Configuration Fixes (Target: -8°C to -10°C)

These changes require no kernel recompilation and can be tested immediately on the live system:

#### 1. Implement Dynamic CPU Hotplugging (Mini-mpdecision)
Because routing 4G traffic on a dongle rarely requires more than 1–2 cores, keep cores 1–3 offline when idle:
Create an init script `/etc/init.d/cpu-hotplug`:
```sh
#!/bin/sh /etc/rc.common
START=95
USE_PROCD=1

hotplug_loop() {
    while :; do
        # Read 1-minute load average
        load=$(awk '{print int($1 * 100)}' /proc/loadavg)
        if [ "$load" -lt 50 ]; then
            # Low load: Keep only CPU0 online
            [ "$(cat /sys/devices/system/cpu/cpu1/online 2>/dev/null)" = "1" ] && echo 0 > /sys/devices/system/cpu/cpu1/online
            [ "$(cat /sys/devices/system/cpu/cpu2/online 2>/dev/null)" = "1" ] && echo 0 > /sys/devices/system/cpu/cpu2/online
            [ "$(cat /sys/devices/system/cpu/cpu3/online 2>/dev/null)" = "1" ] && echo 0 > /sys/devices/system/cpu/cpu3/online
        else
            # High load: Bring all cores online
            echo 1 > /sys/devices/system/cpu/cpu1/online 2>/dev/null
            echo 1 > /sys/devices/system/cpu/cpu2/online 2>/dev/null
            echo 1 > /sys/devices/system/cpu/cpu3/online 2>/dev/null
        fi
        sleep 5
    done
}

start_service() {
    procd_open_instance
    procd_set_param command /bin/sh -c "$(declare -f hotplug_loop); hotplug_loop"
    procd_set_param respawn
    procd_close_instance
}
```

#### 2. Cap Maximum CPU Frequency & Use `conservative` or `powersave` Governor
By default, `luci-app-cpu-perf` sets `schedutil` up to 1.2 GHz. Capping the frequency to 800 MHz eliminates high-voltage turbo states:
```sh
uci set cpu-perf.@cpu_freq_policy[0].scaling_max_freq="800000"
uci set cpu-perf.@cpu_freq_policy[0].scaling_governor="conservative"
uci commit cpu-perf
/etc/init.d/cpu-perf restart
```

#### 3. Wi-Fi Transmit Power Optimization
Lower Wi-Fi TX power from the default 20 dBm (100 mW) to 14 dBm (25 mW). In an enclosed dongle without heatsinks, Wi-Fi PA heat radiates directly into the MSM8916 die:
```sh
iw dev wlan0 set txpower fixed 1400
```

---

### Phase 2: Kernel & Device Tree Enhancements (Target: -5°C to -7°C)

#### 1. Lower DTS Thermal Trip Points to Match Stock Android
Add a patch in `msm89xx/patches/` updating `arch/arm64/boot/dts/qcom/msm8916-generic-hmu05.dts` (or overriding `msm8916.dtsi`):
```dts
&cpu0_1_alert0 {
    temperature = <55000>;  /* Lower from 75°C to 55°C, matching Android */
    hysteresis = <5000>;
};

&cpu2_3_alert0 {
    temperature = <55000>;
    hysteresis = <5000>;
};
```

#### 2. Resolve the Root Cause of `a2_power.c:1189` to Remove `modem-a2-hold`
* As documented in `Docs/Modem Stability/Modem RE/hmu05/A2_QUIESCE_ASYMMETRY.md`, `modem-a2-hold` is only a band-aid preventing the quiesce timeout assert.
* Fix the `bam-dmux` down-ack serialisation / handshake in the kernel driver so that runtime PM can be re-enabled (`control=auto`).
* Allowing the modem's A2 domain to collapse during idle intervals will yield the single largest temperature drop (~5°C to 7°C).

#### 3. Attach PM8916 SMPS2 Regulator to CPU Nodes for Voltage Scaling
Enable dynamic voltage scaling in `msm8916.dtsi` by linking the CPU nodes to `PM8916 SMPS2` and specifying `opp-microvolt` across the OPP table so that lowering frequency also reduces core voltage.
