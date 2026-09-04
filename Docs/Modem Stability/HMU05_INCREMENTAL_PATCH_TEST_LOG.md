# HMU05 Incremental Patch & Stability Test Log

**Date:** September 4, 2026  
**Target Device:** Generic HMU05 (Qualcomm MSM8916 / Snapdragon 410)  
**Test Branch:** `test/hmu05-modem-2026-09-04`  
**Base Firmware:** OpenWrt 25.12.5 (Kernel Linux 6.12.94)  

---

## 1. Test Run 1: Baseline Build (Patches 1 to 4)

### 1.1 Patches Applied
* **Patch 1 (`ae2394d` / `f914327`):** `feat(hmu05): add automatic device-specific No-Sleep modem firmware patcher`
  * Added `packages/msm-firmware-dumper/src/hmu05-patch-modem.c`
  * Hexagon binary patch at `0x001117e0` returning `-1` + SHA-256 recalculation in `modem.mdt` and `modem.b01`.
* **Patch 2 (`03b342e` / `624b40c`):** `docs(modem): add comprehensive modem stability, 15-minute crash fix, and test report`
* **Patch 3 (`972b7fe` / `37d917e`):** `docs: add standardized README indexes for Docs/EDL and Docs/Modem Stability`
* **Patch 4 (`dbe9393` / `2f8dc7c`):** `feat(modem): implement modem-watchdog supervisor and rmtfs boot synchronization (parts A and B)`
  * Added `packages/modem-watchdog` (periodic ping check + progressive soft/hard recovery).
  * Synchronized `msm-firmware-dumper` boot sequence (`START=19` before `rmtfs` `START=20`).

### 1.2 Test Outcome: FAILED / DEVICE CRASH
* **Result:** Device crashed / lost network connection during live operation.
* **Root Cause Analysis:**
  1. **Premature Watchdog Recovery Loop:**
     `modem-watchdog` was running an aggressive detection loop (every 15s, 3 ping failures). When normal cellular packet latency or an unpatched BAM-DMUX idle sleep occurred, `modem-watchdog` triggered an un-synchronized remoteproc reset:
     ```sh
     echo stop > /sys/class/remoteproc/remoteproc0/state
     sleep 2
     /etc/init.d/rmtfs restart
     sleep 1
     echo start > /sys/class/remoteproc/remoteproc0/state
     ```
     Stopping and restarting `remoteproc0` while Linux network sockets were open locked the kernel DMA bus or triggered a kernel panic/watchdog reboot.
  2. **Unpatched Kernel BAM-DMUX Driver:**
     Without the BAM-DMUX runtime PM fix (`808-bam-dmux-stats.patch`), idle channels autosuspended into `RPM_ERROR` (`-EINVAL`), permanently stalling RX/TX.
  3. **Missing QMI Time Synchronization:**
     Without `qcom-time-daemon`, SCLK drifted against the TCXO oscillator, causing modem DSP instability.

---

## 2. Test Run 2: Stability Upgrade (Patches 5 to 13)

### 2.1 Applied Patches
To resolve the failure in Test Run 1, the following patches were incrementally applied:

| # | Commit | Summary | Role in Fix |
| :- | :--- | :--- | :--- |
| **5** | `d6fcb15` | `fix(watchdog): integrate mmcli direct connect and carrier timing resync in recovery pipeline` | Improved watchdog stall recovery logic. |
| **6** | `7f6276a` | `docs: add Qualcomm MCFG carrier MBN and QMI PDC architecture report` | Documentation of carrier MBN structure. |
| **7** | `c9a3a01` | `feat(carrier): introduce qcom-carrier-autocfg auto-provisioning engine for MSM8916` | Dynamic SIM carrier detection and automated APN configuration. |
| **8** | `fe2f629` | `feat(carrier): integrate global carrier MCFG MBN database & dynamic provisioning` | Carrier profile database across major global networks. |
| **9** | `4d1fdde` | `feat(carrier): prioritize device-native modem_pr carrier tree before falling back to bundled database` | Ensures hardware calibration from internal eMMC is prioritized. |
| **10** | `3711f39` | `fix(bam-dmux): fix PM runtime state synchronization and remove invalid pm_runtime_resume_and_get in netdev_open` | **CRITICAL:** Kernel patch `808-bam-dmux-stats.patch` calling `pm_runtime_set_active` on modem power-on and `pm_runtime_set_suspended` on power-off, eliminating `-ETIMEDOUT` and `RPM_ERROR`. |
| **11** | `5a6d403` | `feat(time-daemon): integrate native Qualcomm QMI Time Synchronization daemon (Service 22) for modem ATS/SCLK stability` | **CRITICAL:** Native C daemon syncing `ATS_USER` / `ATS_TOD` over QRTR to eliminate SCLK drift without SFN glitches. |
| **12** | `2e34779` | `build(target): bundle qcom-carrier-autocfg and qcom-time-daemon by default across all MSM8916 targets` | Included daemons in build targets. |
| **13** | `2f62d52` | `feat(hmu05): refine modem stability fix for generic-hmu05` | **CRITICAL CLEANUP:**<br>• **Purged `modem-watchdog` completely** from the tree.<br>• Scoped `qcom-time-daemon` strictly to `generic-hmu05`.<br>• Eliminated premature QRTR send in `qcom-time-daemon.c`. |

### 2.2 Key Architectural Shift in Test Run 2
* **Zero Watchdog Interference:** `modem-watchdog` is 100% removed; the firmware relies on native prevention rather than violent resets.
* **Proactive SCLK Calibration:** `qcom-time-daemon` silently maintains clock alignment over QRTR.
* **Synchronized DMA Channels:** The kernel driver `qcom_bam_dmux` no longer desynchronizes from Hexagon power states.

### 2.3 Build Artifacts Generated
* **Timestamp:** 2026-09-04 17:17
* **Binaries:**
  - `openwrt-msm89xx-msm8916-generic-hmu05-squashfs-boot.img` (6.2 MB)
  - `openwrt-msm89xx-msm8916-generic-hmu05-squashfs-system.img` (12 MB)
  - `openwrt-msm89xx-msm8916-generic-hmu05-squashfs-sysupgrade.bin` (19 MB)
* **Status:** Flashed and monitored for 20 minutes on live hardware.

### 2.4 Test Run 2 Outcome: NO KERNEL/DSP CRASH, BUT DATA STALL AT 15.0 MINUTES
* **Test Duration:** 20 continuous minutes (device uptime reached 25+ minutes).
* **Crash Status:** **ZERO CRASHES / ZERO KERNEL PANICS.**
  - Hexagon DSP remained running throughout the test.
  - No `lte_ml1_sleepmgr_stm.c:4054` assertion (Patch 1 No-Sleep successfully disabled the sleep timer).
  - No remoteproc bus panic (Patch 13 removed `modem-watchdog`'s uncoordinated remoteproc resets).
  - ModemManager and QMI control socket (`/dev/wwan0qmi0`) remained fully operational and attached.
* **Connectivity Finding:** **DATA FROZE AT EXACTLY 15 MINUTES (Sample #22 -> #23).**
  - Samples #01–#22 (0m–10.6m into test, device uptime 4m–15m): Ping latency 35ms–150ms, 0% packet loss, RX/TX continuously incrementing.
  - Sample #23 (11.1m into test, device uptime 15m): Ping packets stopped receiving responses. RX bytes froze at `9,927 bytes`. TX bytes continued incrementing from 10,600 to 17,296 bytes.
* **Live Hardware Diagnostic Findings:**
  1. `/sys/devices/platform/soc@0/4080000.remoteproc/4080000.remoteproc:bam-dmux/power/runtime_status` was found in `suspended` state (`control: auto`, `autosuspend_delay_ms: 1000`).
  2. The modem firmware still has multi-mode enabled (`utran-1, utran-5, utran-8` along with `eutran-1, eutran-3, eutran-5, eutran-8`). On Reliance Jio (a pure LTE carrier), the modem triggers periodic IRAT measurement gaps searching for 3G cells, breaking the LTE bearer session.
  3. No keepalive ping mechanism was running to maintain eNodeB RRC_CONNECTED state, allowing carrier radio link timeout.

---

## 3. Test Run 3: Upgrade through Patch 17 (Runtime PM Lock + Global APN Engine)

### 3.1 Applied Patches
To eliminate the BAM-DMUX DMA autosuspend and EFS2 913-second Hexagon exception, Patches 14 to 17 were applied:

| # | Commit | Summary | Role in Fix |
| :- | :--- | :--- | :--- |
| **14** | `539227a` / `aab6d0f` | `docs(modem): add definitive HMU05 modem stability and connectivity resolution report` | Full architecture documentation of 4-pillar resolution. |
| **15** | `bf85a3b` / `33d5edd` | `feat(carrier): introduce hybrid global and custom APN auto-provisioning engine` | Adds global TSV APN database (`/usr/share/qcom-carrier-autocfg/apns.tsv`) and `/etc/qcom-carrier-autocfg/custom-apns.tsv` sysupgrade overrides. |
| **16** | `051f0ed` / `8a7a46b` | `docs(modem): document empirical proof of No-Sleep patch requirement and hybrid APN engine` | Empirical analysis of No-Sleep requirement and carrier engine. |
| **17** | `e8fe65c` / `58f3b2f` | `fix(hmu05): lock modem and bam-dmux runtime power management to prevent 15-min crash` | **CRITICAL RUNTIME PM LOCK:**<br>• Adds `/etc/init.d/hmu05-modem-pm` (`START=96`). Locks `control=on` and `autosuspend_delay_ms=-1` on all `4080000.remoteproc` and `bam-dmux` nodes.<br>• Integrates PM lock enforcement into `modem-led-monitor` and `99-msm89xx-firstboot`. |

### 3.2 Target Scope & Build Artifacts Generated
* **Timestamp:** 2026-09-04 17:50
* **Binaries Generated:**
  - `openwrt/bin/targets/msm89xx/msm8916/openwrt-msm89xx-msm8916-generic-hmu05-squashfs-boot.img` (6.2 MB)
  - `openwrt/bin/targets/msm89xx/msm8916/openwrt-msm89xx-msm8916-generic-hmu05-squashfs-system.img` (12 MB)
  - `openwrt/bin/targets/msm89xx/msm8916/openwrt-msm89xx-msm8916-generic-hmu05-squashfs-sysupgrade.bin` (19 MB)
* **Status:** Flashed on hardware.

### 3.3 Test Run 3 Outcome: DATA STALL PERSISTED
* **Observation:** The cellular connection still experienced a data freeze at ~15 minutes.
* **Root Causes Identified:**
  1. `/etc/init.d/hmu05-modem-pm` lacked a `boot()` handler in Patch 17, meaning `start()` was not called during OpenWrt non-procd boot.
  2. Carrier radio connection timed out into `RRC_IDLE` due to lack of an active keepalive ping heartbeat.
  3. No non-destructive netifd reconnect handler was in place to automatically re-establish the data bearer.

---

## 4. Test Run 4: Upgrade through Patch 22 (Keepalive Heartbeat + Synchronized Safe Recovery)

### 4.1 Applied Patches
Patches 18 to 22 were applied to provide the missing boot hook, continuous 45s keepalive heartbeat, and synchronized netifd reconnect:

| # | Commit | Summary | Role in Fix |
| :- | :--- | :--- | :--- |
| **18** | `47e84ef` / `97f03d7` | `fix(hmu05): refine init script boot/start handler in hmu05-modem-pm` | Adds `boot() { start; }` so runtime PM locks run reliably on initial boot. |
| **19** | `3e2709a` / `8a1e181` | `docs(hmu05): document 913s remoteproc fatal error and runtime PM lock fix` | Documentation of 913s EFS2 sync and PM locks. |
| **20** | `5b2c7a8` / `a33012d` | `feat(modem): implement two-pronged safe keepalive and synchronized recovery` | **CRITICAL:**<br>• **Keepalive:** 45s ICMP ping heartbeat over `wwan0` keeping carrier link in `RRC_CONNECTED`.<br>• **Safe Reconnect:** Synchronous `ifdown modem` -> netifd wait -> 2s settling gap -> `ifup modem` when stalled. |
| **21** | `2856a4f` / `c41f651` | `fix(modem-monitor): properly scope local variables in run_watchdog_cycle function` | Fixes shell variable scoping in monitor cycle. |
| **22** | `6fedfc2` / `f71fd50` | `fix(modem-monitor): accelerate retry loop to 5s intervals when FAIL_COUNT > 0` | Accelerates health checks upon packet loss for rapid recovery. |

### 4.2 Build Artifacts Generated
* **Timestamp:** 2026-09-04 18:14
* **Binaries Generated:**
  - `openwrt/bin/targets/msm89xx/msm8916/openwrt-msm89xx-msm8916-generic-hmu05-squashfs-boot.img` (6.2 MB)
  - `openwrt/bin/targets/msm89xx/msm8916/openwrt-msm89xx-msm8916-generic-hmu05-squashfs-system.img` (12 MB)
  - `openwrt/bin/targets/msm89xx/msm8916/openwrt-msm89xx-msm8916-generic-hmu05-squashfs-sysupgrade.bin` (19 MB)
* **Status:** Flashed on hardware.

### 4.3 Test Run 4 Outcome: BOOT TIME RECOVERY LOOP
* **Observation:** The cellular connection failed to route traffic on initial boot.
* **Root Cause:**
  1. Patch 22's accelerated retry (every 5s on failure) pinged before `wwan0` finished initial DHCP/routing setup.
  2. Because the modem was still in multi-mode (`utran-1, utran-5, utran-8`), Jio's lack of 3G triggered IRAT scanning stalls during initial attachment.
  3. Safe recovery triggered prematurely, entered a 90-second cooldown, leaving `wwan0` down.

---

## 5. Test Run 5: Final Comprehensive Stability Stack (Patches 23 to 26)

### 5.1 Applied Patches
Patches 23 to 26 were applied to eliminate 2G/3G IRAT measurement gap crashes, lock LTE bands on Jio, and isolate watchdog execution strictly to HMU05:

| # | Commit | Summary | Role in Fix |
| :- | :--- | :--- | :--- |
| **23** | `52d2b26` / `994993a` | `feat(modem): eliminate IRAT measurement gap crashes via software LTE band masking` | Restricts modem bands to LTE-only (`eutran-1\|3\|5\|8`) on pure 4G networks, eliminating radio-stall IRAT scans. |
| **24** | `0425bba` / `2a7d19f` | `fix(modem): use word boundary in band regex to prevent eutran from matching utran` | Fixes regex boundary (`\<utran`) so `eutran` is not falsely matched. |
| **25** | `0094009` / `87058a6` | `fix(modem): strictly isolate keepalive watchdog and band locks to HMU05 only` | Safeguard ensuring band locks and keepalive only execute on HMU05 hardware. |
| **26** | `7ca1961` / `81ddb63` | `feat(modem): restrict LTE-only band lock strictly to Jio carrier on HMU05` | Applies band restrictions **only to Reliance Jio** (`4058xx` / `IN Loop`), preserving 2G/3G multi-mode for Airtel, BSNL, Ncell, NTC. |

### 5.2 Build Artifacts Generated
* **Timestamp:** 2026-09-04 18:28
* **Binaries Generated:**
  - `openwrt/bin/targets/msm89xx/msm8916/openwrt-msm89xx-msm8916-generic-hmu05-squashfs-boot.img` (6.2 MB)
  - `openwrt/bin/targets/msm89xx/msm8916/openwrt-msm89xx-msm8916-generic-hmu05-squashfs-system.img` (12 MB)
  - `openwrt/bin/targets/msm89xx/msm8916/openwrt-msm89xx-msm8916-generic-hmu05-squashfs-sysupgrade.bin` (19 MB)
* **Status:** Flashed on hardware.

### 5.3 Test Run 5 Outcome: "NETWORK UNREACHABLE" ON BOOT
* **Observation:** The device established cellular bearer with Jio (`10.31.90.109`), but ping failed immediately with `sendto: Network unreachable` and `ip route show` had zero default route.
* **Kernel Root Cause Analysis:**
  1. `ip link show` showed `wwan0` stuck in `state DOWN`.
  2. Manual `ip link set wwan0 up` threw `RTNETLINK answers: I/O error`.
  3. Kernel dmesg showed: `bam-dmux 4080000.remoteproc:bam-dmux: Failed to prepare TX DMA buffer`.
  4. In `drivers/net/wwan/qcom_bam_dmux.c`, `dmux->tx` was requested exclusively inside `bam_dmux_runtime_resume()`. But because `pm_runtime_set_active()` was called on power-on and PM was locked to `control=on`, `runtime_resume` was never triggered. `dmux->tx` remained `NULL` forever!
  5. Any attempt to transmit command/data packets called `dmaengine_prep_slave_single(dmux->tx, ...)`, which returned `NULL` because `!dmux->tx`, failing netdev open with `-EIO`.

---

## 6. Test Run 6: The Definitive DMA Fix (TX Channel Allocation on Power-On + 30s Grace Period)

### 6.1 Applied Fixes
1. **Kernel Driver Patch ([`msm89xx/patches/808-bam-dmux-stats.patch`](file:///home/shaanair/Projects/msm8916-openwrt-clean/msm89xx/patches/808-bam-dmux-stats.patch)):**
   - In `bam_dmux_power_on()`, immediately requests `dmux->tx = dma_request_chan(dev, "tx")` alongside `rx`.
   - Ensures that as soon as the modem powers on, both DMA pipes are fully allocated and ready, completely resolving `Failed to prepare TX DMA buffer`.
2. **Userspace Grace Period ([`msm89xx/base-files/usr/sbin/modem-led-monitor`](file:///home/shaanair/Projects/msm8916-openwrt-clean/msm89xx/base-files/usr/sbin/modem-led-monitor)):**
   - Added a 30-second settling grace period (`[ "${iface_uptime:-0}" -lt 30 ]`) after `modem` interface reaches `up=true` before keepalive pings start, preventing boot-time recovery loops.
### 6.2 Build & Flash Verification
* **Image:** `openwrt-msm89xx-msm8916-generic-hmu05-squashfs-sysupgrade.bin`
* **Kernel Hash on Device (`/dev/mmcblk0p13`):** `d14f8d718011663730a721c6e8b942d3084a2d7bd63d86040efeb2cc0296a8c5`
* **Status:** Flashed and verified live on hardware.

### 6.3 Test Run 6 Outcome: INTERNET FULLY OPERATIONAL & ZERO TX DMA ERRORS
* **Modem State:** `connected` (LTE, Band 3 `eutran-3`, Signal 83–88%, Operator: `IN Loop` / Jio).
* **Interface State:** `wwan0: <POINTOPOINT,NOARP,UP,LOWER_UP>` (mtu 1500, state UP).
* **Routing Table:** `default via 10.20.188.160 dev wwan0 proto static src 10.20.188.159 metric 10` installed automatically by netifd.
* **Kernel DMA Check:** `dmesg | grep -i "Failed to prepare TX DMA buffer"` -> **0 errors** (completely eliminated).
* **Network Verification:**
  - `ping -c 4 1.1.1.1`: **0% packet loss**, avg 43.6 ms.
  - `ping -c 4 8.8.8.8`: **0% packet loss**, avg 44.1 ms.
  - `nslookup openwrt.org`: **IPv4 (64.226.122.113) and IPv6 (2a03:b0c0:3:d0::1a51:c001) successfully resolved**.
  - `ip -s link show dev wwan0`: RX and TX packets and bytes incrementing cleanly in real time.

---

## 7. Test Run 7: 20-Minute Soak Test & 915s Reboot Forensics Resolution

### 7.1 Soak Test & 15-Minute Baseband Analysis
* **Initial Observation:** During a 10s keepalive test, the modem ran with 0% packet loss up to 15.0m, but then stalled when left completely idle (`UE In Idle: 'yes'`). When active continuous traffic was sent, the session survived past 24 minutes without stall.
* **Option A Implementation ([`msm89xx/base-files/usr/sbin/modem-led-monitor`](file:///home/shaanair/Projects/msm8916-openwrt-clean/msm89xx/base-files/usr/sbin/modem-led-monitor)):**
  - Configured active 2-second heartbeat (`CHECK_INTERVAL=2`, `KEEPALIVE_INTERVAL=2`).
  - Added traffic-aware passive check (`rx_packets` delta > 0 skips artificial pings).
  - Throttled heavy `mmcli` DBus queries to once every 30s to keep CPU load average at ~0.30.

### 7.2 The 915-Second Reboot: Forensics & Root Cause
* **Symptom:** During live testing, the device suddenly rebooted at exactly $t = 915.21\text{s}$ ($15\text{m } 15\text{s}$).
* **Crash Dump Analysis (`/sys/fs/pstore/console-ramoops`):**
  ```text
  [  915.212002] qcom-q6v5-mss 4080000.remoteproc: fatal error received:     :Excep  :0:
  [  915.212204] remoteproc remoteproc0: crash detected in 4080000.remoteproc: type fatal error
  [  915.227603] remoteproc remoteproc0: recovering 4080000.remoteproc
  [  915.298518] qcom-q6v5-mss 4080000.remoteproc: MBA booted without debug policy, loading mpss
  ```
* **Correlated System Log (`logread`):**
  ```text
  Fri Sep  4 15:21:51 2026 daemon.info qcom-time-daemon[2505]: Sent time sync (base=2, genoff=1472505674532 ms) to 0:11
  Fri Sep  4 15:22:07 2026 daemon.err collectd[2877]: Sleeping only 2s because the next interval is 881.522 seconds in the past!
  ```
* **Root Cause:**
  1. `ntpd` stepped system clock forward after establishing NTP sync.
  2. `qcom-time-daemon` detected `diff > 5000ms` and sent `QMI_TIME_GENOFF_SET_REQ` (base 2) to the modem.
  3. Dynamically overwriting `genoff` during an active LTE session forces Hexagon Layer 1 SFN resynchronization, immediately crashing the DSP with hardware exception `:Excep :0:`.
  4. Linux remoteproc attempted to reload `mpss` at runtime, which violated TrustZone security on MSM8916, triggering a hardware Watchdog Bite (WDT) reset that rebooted the router.

### 7.3 Resolution & Final Soak Test Results
1. **Patched `packages/qcom-time-daemon/src/qcom-time-daemon.c`:**
   - Restricted `send_qmi_time_set` to one-time boot initialization (`force_set && last_synced_genoff == 0`).
   - Prevented dynamic mid-session time overwrites from ever being sent to the baseband.
2. **Stopped & Disabled `qcom-time-daemon` on Hardware:**
   - Eliminated the crash trigger completely.
3. **20-Minute Soak Test Milestone PASSED:**
   - **Total Uptime:** **20+ minutes continuous ($t = 1212\text{s}$)**.
   - **Packet Loss:** **0.0%**.
   - **Latency:** min/avg/max = 34.6 / 35.6 / 36.4 ms.
   - **Radio State:** Maintained continuously in `RRC_CONNECTED` (`UE In Idle: 'no'`).
   - **System Load:** 0.66 (CPU idle > 90%).
   - **Commit:** `f073415` on `test/hmu05-modem-2026-09-04`.


