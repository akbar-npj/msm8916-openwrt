# Phased Implementation Plan (v2): QMI WDS Dormancy Instrumentation & Falsification Test

## 1. Objectives & Safety Commitments
- **Zero Baseband Modification:** Baseband firmware (`modem.b16`) remains **100% untouched stock**.
- **No State Mutation in Instrumentation:** Callback is strictly observational. No `GO_DORMANT`, `GO_ACTIVE`, bearer teardown, or protocol state modification.
- **Single Aggregated Log Line:** Exactly **one** structured line per WDS event indication (`[WDS-EVENT] ...`) to prevent syslog buffer flooding or GLib loop timing distortion.
- **Subscription Verification:** Callback decodes only the fields explicitly requested by ModemManager's `event_report_input_new()`. Transfer statistics are not subscribed and will not be decoded in this callback.
- **Experimental Control:** `qcom-time-daemon` will be stopped/disabled during the test to eliminate periodic `0x0020` time-sync requests from confounding the 900-second timeline.
- **Strict Approval Gate:** We will not touch or deploy anything to the live router until you have reviewed the patch diff, compilation output, and deployment plan.

---

## 2. Subscription Audit: Subscribed vs Monitored Fields
Auditing [`event_report_input_new()`](file:///home/shaanair/Projects/msm8916-openwrt-clean/openwrt/build_dir/target-aarch64_generic_musl/modemmanager-1.24.0/src/mm-bearer-qmi.c#L1506-L1526) reveals:

| QMI TLV | Parameter Name | Subscribed in `event_report_input_new()`? | Handled in Instrumented Callback? |
| :--- | :--- | :--- | :--- |
| **0x13 / 0x18** | Dormancy Status | **Yes** (`set_dormancy_status`) | **Yes** (`qmi_indication_wds_event_report_output_get_dormancy_status`) |
| **0x1b / 0x27** | Uplink Flow Control | **Yes** (`set_uplink_flow_control`) | **Yes** (`qmi_indication_wds_event_report_output_get_uplink_flow_control_enabled`) |
| **0x17 / 0x11** | Data Call Status | **Yes** (`set_data_call_status`) | **Yes** (`qmi_indication_wds_event_report_output_get_data_call_status`) |
| **0x11 / 0x12** | Data Bearer Technology | **Yes** (`set_data_bearer_technology`) | **Yes** (`qmi_indication_wds_event_report_output_get_data_bearer_technology`) |
| **0x10 / 0x1a** | Transfer Statistics | **No** (not subscribed) | **No** (polled independently every 30s via MsgID `0x0024`) |

Because transfer statistics are not subscribed in `event_report_input_new()`, omitting them from the callback prevents empty getter queries and keeps the log line concise.

---

## 3. The Single-Line Aggregated Callback Implementation

In [`src/mm-bearer-qmi.c`](file:///home/shaanair/Projects/msm8916-openwrt-clean/openwrt/build_dir/target-aarch64_generic_musl/modemmanager-1.24.0/src/mm-bearer-qmi.c#L1434-L1439), replace the empty stub with:

```c
static void
event_report_indication_cb (QmiClientWds *client,
                            QmiIndicationWdsEventReportOutput *output,
                            MMBearerQmi *self)
{
    GString *str;
    QmiWdsDormancyStatus dormancy = QMI_WDS_DORMANCY_STATUS_UNKNOWN;
    gboolean uplink_fc = FALSE;
    QmiWdsDataCallStatus call_status = QMI_WDS_DATA_CALL_STATUS_UNKNOWN;
    QmiWdsDataBearerTechnology bearer_tech = QMI_WDS_DATA_BEARER_TECHNOLOGY_UNKNOWN;
    gboolean has_field = FALSE;

    str = g_string_new ("[WDS-EVENT]");

    /* TLV 0x18: Dormancy Status (1 = Dormant, 2 = Active) */
    if (qmi_indication_wds_event_report_output_get_dormancy_status (output, &dormancy, NULL)) {
        g_string_append_printf (str, " dormancy=%s(%u)",
                                qmi_wds_dormancy_status_get_string (dormancy),
                                (guint)dormancy);
        has_field = TRUE;
    }

    /* TLV 0x27: Uplink Flow Control (0 = Unthrottled, 1 = Throttled) */
    if (qmi_indication_wds_event_report_output_get_uplink_flow_control_enabled (output, &uplink_fc, NULL)) {
        g_string_append_printf (str, " uplink_fc=%s",
                                uplink_fc ? "PAUSED" : "FLOWING");
        has_field = TRUE;
    }

    /* TLV 0x11: Data Call Status */
    if (qmi_indication_wds_event_report_output_get_data_call_status (output, &call_status, NULL)) {
        g_string_append_printf (str, " call_status=%s(%u)",
                                qmi_wds_data_call_status_get_string (call_status),
                                (guint)call_status);
        has_field = TRUE;
    }

    /* TLV 0x12: Data Bearer Technology */
    if (qmi_indication_wds_event_report_output_get_data_bearer_technology (output, &bearer_tech, NULL)) {
        g_string_append_printf (str, " bearer_tech=%s(%u)",
                                qmi_wds_data_bearer_technology_get_string (bearer_tech),
                                (guint)bearer_tech);
        has_field = TRUE;
    }

    /* Emit exactly one log line if any monitored TLV was present */
    if (has_field)
        mm_obj_msg (self, "%s", str->str);
    else
        mm_obj_dbg (self, "got QMI WDS event report (unmonitored TLVs)");

    g_string_free (str, TRUE);
}
```

### Log Output Examples
```text
[modem0/bearer1] [WDS-EVENT] dormancy=traffic-channel-dormant(1)
[modem0/bearer1] [WDS-EVENT] uplink_fc=PAUSED
[modem0/bearer1] [WDS-EVENT] dormancy=traffic-channel-active(2) uplink_fc=FLOWING
```

---

## 4. Host-Side Patch Creation & Compilation Procedure

### Step 1: Create the OpenWrt Patch
Place the patch at:
`openwrt/feeds/packages/net/modemmanager/patches/0005-wds-dormancy-instrumentation.patch`

### Step 2: Compile & Strip
Cross-compile with the existing OpenWrt musl toolchain and strip unneeded symbols:
```bash
PATH=/home/shaanair/Projects/msm8916-openwrt-clean/openwrt/staging_dir/toolchain-aarch64_generic_gcc-14.3.0_musl/bin:$PATH \
ninja -C /home/shaanair/Projects/msm8916-openwrt-clean/openwrt/build_dir/target-aarch64_generic_musl/modemmanager-1.24.0/openwrt-build src/ModemManager

/home/shaanair/Projects/msm8916-openwrt-clean/openwrt/staging_dir/toolchain-aarch64_generic_gcc-14.3.0_musl/bin/aarch64-openwrt-linux-musl-strip \
  --strip-unneeded -o /tmp/ModemManager.instrumented \
  /home/shaanair/Projects/msm8916-openwrt-clean/openwrt/build_dir/target-aarch64_generic_musl/modemmanager-1.24.0/openwrt-build/src/ModemManager
```

### Step 3: Host API & Symbol Verification
Verify with `readelf` / `nm` that the binary links cleanly against `libqmi-glib` and contains the expected symbols.

---

## 5. Controlled Deployment & Rollback Protocol (Executed Only After User Review)

1. **Disable `qcom-time-daemon`:**
   ```bash
   ssh root@192.168.8.1 "/etc/init.d/qcom-time-daemon stop"
   ```
2. **Graceful Bearer Teardown Window:**
   ```bash
   ssh root@192.168.8.1 "ifdown wan"
   ```
3. **Backup & Hash Verification:**
   ```bash
   ssh root@192.168.8.1 "cp -a /usr/sbin/ModemManager /usr/sbin/ModemManager.stock_backup && sha256sum /usr/sbin/ModemManager /usr/sbin/ModemManager.stock_backup"
   ```
4. **Atomic Replacement & Verification:**
   ```bash
   scp /tmp/ModemManager.instrumented root@192.168.8.1:/usr/sbin/ModemManager.new
   ssh root@192.168.8.1 "chmod +x /usr/sbin/ModemManager.new && mv /usr/sbin/ModemManager.new /usr/sbin/ModemManager && sha256sum /usr/sbin/ModemManager"
   ```
5. **Clean Service Start & Bearer Establishment:**
   ```bash
   ssh root@192.168.8.1 "/etc/init.d/modemmanager restart && ifup wan"
   ```
6. **Instant Rollback (if required):**
   ```bash
   ssh root@192.168.8.1 "cp -f /usr/sbin/ModemManager.stock_backup /usr/sbin/ModemManager && /etc/init.d/modemmanager restart && ifup wan"
   ```

---

## 6. 15-Minute Soak & Falsification Matrix

| Observed State at First Ping Failure ($t \approx 900\text{s}$) | Immediate Action | Engineering Interpretation |
| :--- | :--- | :--- |
| **Case A: `dormancy=traffic-channel-dormant(1)`** | Issue `qmicli -d /dev/wwan0qmi0 -p --wds-go-active`. If ping recovers, state misalignment confirmed. | Modem entered DRX/dormant state and failed to wake automatically on TX data. Next step: add stock Android RIL `qcril_data_reg_sys_ind()` subscription update. |
| **Case B: `dormancy=traffic-channel-active(2)`** | Log state and confirm RX freeze via `/sys/class/net/wwan0/statistics/rx_packets`. | **Dormancy is 100% ruled out as root cause.** Focus shifts immediately to Hexagon BAM DMA descriptor exhaustion / Hexagon sleepmgr. |
| **Case C: No `[WDS-EVENT]` received around freeze** | Check WDS polling statistics and NAS signal info. | Baseband is not emitting any WDS state change; stall is entirely sub-QMI (firmware/BAM DMA level). |
