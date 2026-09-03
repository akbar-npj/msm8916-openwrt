# MSM8916 OpenWrt Boot Errors & Daemon Warnings Investigation and Fixes

## 1. Overview and Problem Statement

During device boot and service initialization on Qualcomm Snapdragon 410 (MSM8916) OpenWrt systems, multiple kernel errors (`kern.err`), user-space storage errors (`user.err`), and daemon warnings (`daemon.err`) were logged in `dmesg` and syslog:

```text
[Jun 29, 2026, 1:01:37 PM UTC] kern.err: [    0.844843] qcom-smsm smsm: mbox_request_channel: can't parse "mboxes" property
[Jun 29, 2026, 1:01:37 PM UTC] kern.err: [    0.844915] qcom-smsm smsm: mbox_request_channel: can't parse "mboxes" property
[Jun 29, 2026, 1:01:37 PM UTC] kern.err: [    0.851151] qcom-smsm smsm: mbox_request_channel: can't parse "mboxes" property
[Jun 29, 2026, 1:01:37 PM UTC] user.info: [    7.539555] block: attempting to load /tmp/overlay/upper/etc/config/fstab
[Jun 29, 2026, 1:01:37 PM UTC] user.err: [    7.541141] block: unable to load configuration (fstab: Entry not found)
[Jun 29, 2026, 1:01:37 PM UTC] user.info: [    7.545679] block: attempting to load /tmp/overlay/etc/config/fstab
[Jun 29, 2026, 1:01:37 PM UTC] user.err: [    7.552294] block: unable to load configuration (fstab: Entry not found)
[Jun 29, 2026, 1:01:37 PM UTC] user.info: [    7.558201] block: attempting to load /etc/config/fstab
[Jun 29, 2026, 1:01:37 PM UTC] user.err: [    7.568457] block: unable to load configuration (fstab: Entry not found)
[Jun 29, 2026, 1:01:37 PM UTC] user.err: [    7.570097] block: no usable configuration
[Jun 29, 2026, 1:01:37 PM UTC] user.info: [    7.577078] block: attempting to load /etc/config/fstab
[Jun 29, 2026, 1:01:37 PM UTC] user.err: [    7.580972] block: unable to load configuration (fstab: Entry not found)
[Jun 29, 2026, 1:01:37 PM UTC] user.err: [    7.586077] block: no usable configuration
[Jun 29, 2026, 1:01:37 PM UTC] user.info: [    7.873002] block: attempting to load /etc/config/fstab
[Jun 29, 2026, 1:01:37 PM UTC] user.err: [    7.876792] block: unable to load configuration (fstab: Entry not found)
[Jun 29, 2026, 1:01:37 PM UTC] user.err: [    7.881843] block: no usable configuration
[Jun 29, 2026, 1:01:38 PM UTC] daemon.err: modprobe: failed to find a module named qrtr-tun
[Jun 29, 2026, 1:01:41 PM UTC] daemon.err: modprobe: failed to find a module named qrtr-tun
[Jun 29, 2026, 1:01:42 PM UTC] kern.err: [   15.681075] wcn36xx: ERROR HAL_8023_MULTICAST_LIST rsp failed err=16
[Jun 29, 2026, 1:01:42 PM UTC] daemon.err: collectd[2658]: plugin_load: plugin "cpu" successfully loaded.
[Jun 29, 2026, 1:01:42 PM UTC] daemon.info: ModemManager[2462]: hotplug: ModemManager not yet available
[Jun 29, 2026, 1:01:42 PM UTC] daemon.err: collectd[2658]: plugin_load: plugin "interface" successfully loaded.
[Jun 29, 2026, 1:01:42 PM UTC] daemon.err: collectd[2658]: plugin_load: plugin "iwinfo" successfully loaded.
[Jun 29, 2026, 1:01:42 PM UTC] daemon.err: collectd[2658]: plugin_load: plugin "load" successfully loaded.
[Jun 29, 2026, 1:01:42 PM UTC] daemon.err: collectd[2658]: plugin_load: plugin "memory" successfully loaded.
[Jun 29, 2026, 1:01:42 PM UTC] daemon.notice: [2523]: <msg> ModemManager (version 1.24.0) starting in system bus...
[Jun 29, 2026, 1:01:42 PM UTC] daemon.err: collectd[2658]: plugin_load: plugin "rrdtool" successfully loaded.
[Jun 29, 2026, 1:01:42 PM UTC] daemon.err: collectd[2658]: rrdtool plugin: RRASingle = true: creating only AVERAGE RRAs
[Jun 29, 2026, 1:01:42 PM UTC] daemon.err: collectd[2658]: Initialization complete, entering read-loop.
```

This guide details the technical investigation, root cause diagnosis, and exact fix for each item.

---

## 2. Issue 1: `qcom-smsm smsm: mbox_request_channel: can't parse "mboxes" property`

### 2.1 Symptoms
```text
[Jun 29, 2026, 1:01:37 PM UTC] kern.err: [    0.844843] qcom-smsm smsm: mbox_request_channel: can't parse "mboxes" property
[Jun 29, 2026, 1:01:37 PM UTC] kern.err: [    0.844915] qcom-smsm smsm: mbox_request_channel: can't parse "mboxes" property
[Jun 29, 2026, 1:01:37 PM UTC] kern.err: [    0.851151] qcom-smsm smsm: mbox_request_channel: can't parse "mboxes" property
```

### 2.2 Root Cause Analysis
1. In `drivers/soc/qcom/smsm.c`, the `qcom_smsm_probe()` function sets up the Shared Memory State Machine for Qualcomm inter-processor communication.
2. The driver queries `smsm_get_size_info()` to determine the number of participating subsystem hosts (`smsm->num_hosts`), which is 4 on MSM8916 (Host 0: APPS, Host 1: MODEM, Host 2: AUDIO, Host 3: WCNSS).
3. The probe loop iterates over each host ID from `0` to `smsm->num_hosts - 1` and calls `smsm_parse_mbox(smsm, id)`:
   ```c
   for (id = 0; id < smsm->num_hosts; id++) {
       ret = smsm_parse_mbox(smsm, id);
       if (!ret)
           continue;
       ret = smsm_parse_ipc(smsm, id);
       if (ret < 0)
           goto out_put;
   }
   ```
4. `smsm_parse_mbox()` unconditionally called `mbox_request_channel(&smsm->mbox_client, host_id)`.
5. In Linux 6.12, `mbox_request_channel()` in `drivers/mailbox/mailbox.c` invokes `fwnode_property_get_reference_args(fwnode, "mboxes", "#mbox-cells", 0, index, &fwspec)`. Whenever this fails, `mbox_request_channel()` emits a high-priority kernel error:
   ```c
   dev_err(dev, "%s: can't parse \"%s\" property\n", __func__, "mboxes");
   ```
6. In `arch/arm64/boot/dts/qcom/msm8916.dtsi`, the `smsm` node specifies:
   ```dts
   mboxes = <0>, <&apcs 13>, <0>, <&apcs 19>;
   ```
   - **Host 0** is the local host (`APPS`). It never needs an IPC interrupt or mailbox channel to itself. Because entry 0 is `<0>` (an empty phandle), the parser returns `-ENOENT` and logs an error.
   - **Host 2** is `AUDIO`, which is unused on MSM8916 and also represented as `<0>`. The parser returns `-ENOENT` and logs an error.
   - Any remote host index out of range similarly triggers `-ENOENT` and an error.
   - Additionally, if the mailbox controller (`apcs`) had not yet probed, `mbox_request_channel()` returned `-EPROBE_DEFER`, but the SMSM driver previously ignored this error and fell back to non-existent syscon entries instead of deferring probe.

### 2.3 Solution & Implementation
Created kernel patch [`msm89xx/patches/816-qcom-smsm-validate-mbox-before-request.patch`](file:///home/shaanair/Projects/msm8916-openwrt-clean/msm89xx/patches/816-qcom-smsm-validate-mbox-before-request.patch):
- Skips requesting an outgoing mailbox channel for `smsm->local_host` (`host_id == smsm->local_host`).
- Validates that a real, non-empty phandle exists in the device tree for `host_id` using `of_parse_phandle_with_args()` *before* calling `mbox_request_channel()`. If the entry is `<0>` or missing, it silently skips the mailbox request.
- Propagates `-EPROBE_DEFER` cleanly if the mailbox provider is not yet ready.

---

## 3. Issue 2: `block: unable to load configuration (fstab: Entry not found)` and `block: no usable configuration`

### 3.1 Symptoms
```text
[Jun 29, 2026, 1:01:37 PM UTC] user.info: [    7.539555] block: attempting to load /tmp/overlay/upper/etc/config/fstab
[Jun 29, 2026, 1:01:37 PM UTC] user.err: [    7.541141] block: unable to load configuration (fstab: Entry not found)
[Jun 29, 2026, 1:01:37 PM UTC] user.info: [    7.545679] block: attempting to load /tmp/overlay/etc/config/fstab
[Jun 29, 2026, 1:01:37 PM UTC] user.err: [    7.552294] block: unable to load configuration (fstab: Entry not found)
[Jun 29, 2026, 1:01:37 PM UTC] user.info: [    7.558201] block: attempting to load /etc/config/fstab
[Jun 29, 2026, 1:01:37 PM UTC] user.err: [    7.568457] block: unable to load configuration (fstab: Entry not found)
[Jun 29, 2026, 1:01:37 PM UTC] user.err: [    7.570097] block: no usable configuration
```

### 3.2 Root Cause Analysis
1. During early OpenWrt preinit (`/lib/preinit/80_mount_root`), `mount_root` executes `mount_extroot("/tmp/overlay")` to check if rootfs or overlay should be pivoted to an external partition.
2. In OpenWrt's `fstools` package (`block.c`), `config_load(cfg)` tries to load:
   - `/tmp/overlay/upper/etc/config/fstab`
   - `/tmp/overlay/etc/config/fstab`
   - `/etc/config/fstab`
3. In stock OpenWrt, `/etc/config/fstab` is not shipped in `base-files`. Instead, it is dynamically generated on first boot by `/etc/uci-defaults/10-fstab` via `block detect > /etc/config/fstab`.
4. However, `/etc/uci-defaults/` only runs *much later* in the boot sequence when `/sbin/init` launches `/etc/init.d/boot`. During preinit (7.539s) and early kernel hotplug events for eMMC partitions (7.577s, 7.873s), `/etc/config/fstab` does not exist yet.
5. In addition, `config_try_load(ctx, path)` in `block.c` did not test `access(path, R_OK)` before calling `uci_load(ctx, file, &pkg)`. Whenever the file was missing, `uci_load` failed and `block` spammed syslog with `unable to load configuration (fstab: Entry not found)` and `no usable configuration`.

### 3.3 Solution & Implementation
1. **Pre-populate default fstab**: Created [`msm89xx/base-files/etc/config/fstab`](file:///home/shaanair/Projects/msm8916-openwrt-clean/msm89xx/base-files/etc/config/fstab) with standard OpenWrt global block-mount settings:
   ```uci
   config global
   	option anon_swap '0'
   	option anon_mount '0'
   	option auto_swap '1'
   	option auto_mount '1'
   	option delay_root '5'
   	option check_fs '0'
   ```
   This ensures `/etc/config/fstab` is present on the rootfs from the very first instant of boot, satisfying early preinit and hotplug lookups.
2. **Prevent speculative error spam in `fstools`**: Added patch [`openwrt-overlay/package/system/fstools/patches/0001-block-skip-nonexistent-fstab-in-config-try-load.patch`](file:///home/shaanair/Projects/msm8916-openwrt-clean/openwrt-overlay/package/system/fstools/patches/0001-block-skip-nonexistent-fstab-in-config-try-load.patch) to make `config_try_load()` check `if (access(path, R_OK)) return NULL;` before invoking `uci_load()`. Non-existent speculative paths (`/tmp/overlay/upper/...`) are skipped silently without logging errors.

---

## 4. Issue 3: `modprobe: failed to find a module named qrtr-tun`

### 4.1 Symptoms
```text
[Jun 29, 2026, 1:01:38 PM UTC] daemon.err: modprobe: failed to find a module named qrtr-tun
[Jun 29, 2026, 1:01:41 PM UTC] daemon.err: modprobe: failed to find a module named qrtr-tun
```

### 4.2 Root Cause Analysis
1. The Qualcomm Remote File System daemon service [`packages/rmtfs/files/rmtfs.init`](file:///home/shaanair/Projects/msm8916-openwrt-clean/packages/rmtfs/files/rmtfs.init) (START=15) and QRTR name server service [`packages/qrtr/files/qrtrns.init`](file:///home/shaanair/Projects/msm8916-openwrt-clean/packages/qrtr/files/qrtrns.init) (START=60) contained:
   ```sh
   load_modules() {
       modprobe qrtr 2>/dev/null || true
       modprobe qrtr-tun 2>/dev/null || true
   }
   ```
2. On MSM8916 platforms:
   - `CONFIG_QRTR=y` and `CONFIG_QRTR_SMD=y` are compiled directly into the kernel image.
   - QRTR communication between the APPS processor and Hexagon modem DSP runs over Shared Memory Device (**SMD**) channels (`IPCRTR`).
   - `qrtr-tun` is a TUN character device driver used only for testing or userspace tunneling over network devices; it is neither enabled (`CONFIG_QRTR_TUN` is unset) nor required on MSM8916.
3. OpenWrt's `modprobe` is provided by `kmodloader` (`ubox`). When a requested module name cannot be found in `/lib/modules/$(uname -r)/`, `kmodloader` invokes:
   ```c
   ULOG_ERR("failed to find a module named %s\n", name);
   ```
   `ULOG_ERR` writes directly to syslog with `LOG_DAEMON | LOG_ERR`. Shell stderr redirection (`2>/dev/null || true`) only suppresses console output, leaving the syslog error active.

### 4.3 Solution & Implementation
Modified [`packages/qrtr/files/qrtrns.init`](file:///home/shaanair/Projects/msm8916-openwrt-clean/packages/qrtr/files/qrtrns.init) and [`packages/rmtfs/files/rmtfs.init`](file:///home/shaanair/Projects/msm8916-openwrt-clean/packages/rmtfs/files/rmtfs.init):
- Removed the call to `modprobe qrtr-tun`.
- Replaced unconditional `modprobe qrtr` with a check against `/sys/module/qrtr`:
  ```sh
  load_modules() {
      [ -d /sys/module/qrtr ] || modprobe qrtr 2>/dev/null || true
  }
  ```
Because `CONFIG_QRTR=y` is built-in, `/sys/module/qrtr` already exists and no unnecessary modprobe invocations are made.

---

## 5. Issue 4: `wcn36xx: ERROR HAL_8023_MULTICAST_LIST rsp failed err=16`

### 5.1 Symptoms
```text
[Jun 29, 2026, 1:01:42 PM UTC] kern.err: [   15.681075] wcn36xx: ERROR HAL_8023_MULTICAST_LIST rsp failed err=16
```

### 5.2 Root Cause Analysis
1. In `drivers/net/wireless/ath/wcn36xx/main.c`, `wcn36xx_configure_filter()` handles hardware multicast filtering requests passed from `mac80211`:
   ```c
   /* FW handles MC filtering only when connected as STA */
   if (*total & FIF_ALLMULTI)
       wcn36xx_smd_set_mc_list(wcn, vif, NULL);
   else if (NL80211_IFTYPE_STATION == vif->type && tmp->sta_assoc)
       wcn36xx_smd_set_mc_list(wcn, vif, fp);
   ```
2. In Qualcomm WCN36xx hardware, the firmware *only* supports host-directed multicast list filtering when operating as an **associated station**.
3. When OpenWrt boots and configures networking:
   - The wireless interface `wlan0` is added to the LAN bridge (`br-lan`).
   - Linux bridge configuration automatically sets `IFF_ALLMULTI` / `FIF_ALLMULTI` on member ports to accept multicast traffic (mDNS, IGMP, IPv6 router discovery).
4. When `wcn36xx_configure_filter()` evaluated `*total & FIF_ALLMULTI`, it dispatched `wcn36xx_smd_set_mc_list(wcn, vif, NULL)` without checking whether the interface was in station mode, whether it was associated, or whether `bss_index` was valid (`!= 0xFF` / `WCN36XX_HAL_BSS_INVALID_IDX`).
5. Because the interface was either in AP mode (hostapd default) or an unassociated STA, the firmware rejected the HAL command with error code 16 (`eHAL_STATUS_BSS_NOT_FOUND` / `eHAL_STATUS_INVALID_PARAMETER`).
6. `wcn36xx_smd_set_mc_list()` in `smd.c` received the status check failure and logged:
   ```c
   wcn36xx_err("HAL_8023_MULTICAST_LIST rsp failed err=%d\n", ret);
   ```

### 5.3 Solution & Implementation
Created kernel patch [`msm89xx/patches/817-wcn36xx-only-send-mc-list-when-sta-associated.patch`](file:///home/shaanair/Projects/msm8916-openwrt-clean/msm89xx/patches/817-wcn36xx-only-send-mc-list-when-sta-associated.patch):
- Explicitly guards the `FIF_ALLMULTI` multicast list update behind the check:
  ```c
  /* FW handles MC filtering only when connected as STA */
  if (NL80211_IFTYPE_STATION == vif->type && tmp->sta_assoc &&
      tmp->bss_index != WCN36XX_HAL_BSS_INVALID_IDX) {
      if (*total & FIF_ALLMULTI)
          wcn36xx_smd_set_mc_list(wcn, vif, NULL);
      else
          wcn36xx_smd_set_mc_list(wcn, vif, fp);
  }
  ```
- If the interface is an AP or not yet associated, no invalid SMD command is sent to the firmware, eliminating the error. When the interface associates as a station, filters are configured normally.

---

## 6. Analysis of Collectd & ModemManager Logs

The user log snippet included several lines tagged as `daemon.err`, `daemon.info`, and `daemon.notice`:

```text
[Jun 29, 2026, 1:01:42 PM UTC] daemon.err: collectd[2658]: plugin_load: plugin "cpu" successfully loaded.
[Jun 29, 2026, 1:01:42 PM UTC] daemon.info: ModemManager[2462]: hotplug: ModemManager not yet available
[Jun 29, 2026, 1:01:42 PM UTC] daemon.err: collectd[2658]: plugin_load: plugin "interface" successfully loaded.
[Jun 29, 2026, 1:01:42 PM UTC] daemon.err: collectd[2658]: plugin_load: plugin "iwinfo" successfully loaded.
[Jun 29, 2026, 1:01:42 PM UTC] daemon.err: collectd[2658]: plugin_load: plugin "load" successfully loaded.
[Jun 29, 2026, 1:01:42 PM UTC] daemon.err: collectd[2658]: plugin_load: plugin "memory" successfully loaded.
[Jun 29, 2026, 1:01:42 PM UTC] daemon.notice: [2523]: <msg> ModemManager (version 1.24.0) starting in system bus...
[Jun 29, 2026, 1:01:42 PM UTC] daemon.err: collectd[2658]: plugin_load: plugin "rrdtool" successfully loaded.
[Jun 29, 2026, 1:01:42 PM UTC] daemon.err: collectd[2658]: rrdtool plugin: RRASingle = true: creating only AVERAGE RRAs
[Jun 29, 2026, 1:01:42 PM UTC] daemon.err: collectd[2658]: Initialization complete, entering read-loop.
```

### 6.1 Collectd Priority Classification
- **Cause**: OpenWrt's init script `/etc/init.d/collectd` runs collectd in foreground mode (`-f`) under `procd` supervision with `procd_set_param stderr 1`.
- In `procd`, standard error streams from supervised daemons are redirected to `logd` with syslog severity `LOG_DAEMON | LOG_ERR` (`daemon.err`).
- When collectd starts without the `syslog` output plugin enabled, it prints its standard startup banner and plugin load notifications to stderr.
- **Verdict**: These messages are **completely normal operational notices** (`plugin successfully loaded`, `Initialization complete, entering read-loop`), not actual errors.

### 6.2 ModemManager Hotplug Notice
- **Cause**: At 1:01:42 PM, the system hotplug subsystem detected cellular modem USB / SMD interfaces while ModemManager was in the middle of launching on the system bus (`ModemManager (version 1.24.0) starting in system bus...`).
- The hotplug script checked whether ModemManager was responding to D-Bus requests. Because it was still initializing, the hotplug script recorded an informational message (`daemon.info: hotplug: ModemManager not yet available`) and exited.
- Once ModemManager finished its bus registration, it automatically enumerated all modem ports.
- **Verdict**: Normal boot-time concurrency behavior; logged at `info` priority, not an error.

---

## 7. Summary of Changes

| Component | Target File | Nature of Fix |
| :--- | :--- | :--- |
| **Qualcomm SMSM** | [`msm89xx/patches/816-qcom-smsm-validate-mbox-before-request.patch`](file:///home/shaanair/Projects/msm8916-openwrt-clean/msm89xx/patches/816-qcom-smsm-validate-mbox-before-request.patch) | Kernel patch: skip local host and validate DT phandles before `mbox_request_channel`. |
| **FSTools / Preinit** | [`msm89xx/base-files/etc/config/fstab`](file:///home/shaanair/Projects/msm8916-openwrt-clean/msm89xx/base-files/etc/config/fstab) | Shipped default `/etc/config/fstab` for preinit and early hotplug. |
| **FSTools / Block** | [`openwrt-overlay/package/system/fstools/patches/0001-block-skip-nonexistent-fstab-in-config-try-load.patch`](file:///home/shaanair/Projects/msm8916-openwrt-clean/openwrt-overlay/package/system/fstools/patches/0001-block-skip-nonexistent-fstab-in-config-try-load.patch) | Check `access(path, R_OK)` before calling `uci_load()` in `config_try_load()`. |
| **QRTR Daemons** | [`packages/qrtr/files/qrtrns.init`](file:///home/shaanair/Projects/msm8916-openwrt-clean/packages/qrtr/files/qrtrns.init) | Remove non-existent `qrtr-tun` modprobe and check `/sys/module/qrtr`. |
| **RMTFS Daemon** | [`packages/rmtfs/files/rmtfs.init`](file:///home/shaanair/Projects/msm8916-openwrt-clean/packages/rmtfs/files/rmtfs.init) | Remove non-existent `qrtr-tun` modprobe and check `/sys/module/qrtr`. |
| **Wi-Fi WCN36xx** | [`msm89xx/patches/817-wcn36xx-only-send-mc-list-when-sta-associated.patch`](file:///home/shaanair/Projects/msm8916-openwrt-clean/msm89xx/patches/817-wcn36xx-only-send-mc-list-when-sta-associated.patch) | Kernel patch: only send `WCN36XX_HAL_8023_MULTICAST_LIST_REQ` on associated STA interfaces. |
| **Documentation** | [`Docs/Patches/README.md`](file:///home/shaanair/Projects/msm8916-openwrt-clean/Docs/Patches/README.md) | Cataloged new patches 816 and 817. |
| **Documentation** | [`Docs/BOOT_ERRORS_INVESTIGATION_AND_FIXES.md`](file:///home/shaanair/Projects/msm8916-openwrt-clean/Docs/BOOT_ERRORS_INVESTIGATION_AND_FIXES.md) | Comprehensive engineering report and root-cause analysis. |
