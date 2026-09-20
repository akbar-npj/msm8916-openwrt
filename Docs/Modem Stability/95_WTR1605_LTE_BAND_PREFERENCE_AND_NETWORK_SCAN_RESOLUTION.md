# Patch Session Log 95 — WTR1605 LTE Band Preference Expansion and Network Scan Resolution

**Date:** 2026-09-14  
**Context:** MSM8916 WTR1605 RF360 Donor Transplant (UFI001B MPSS.DPM.2.0 on HMU05 Target)  
**Status:** Multi-RAT Scan Crash Root Cause Identified; Full LTE Band Preference (Bands 1, 3, 5, 8, 40) & PS-Only Domain Committed to NV; Device Cold-Rebooted  

---

## 1. Executive Summary

Following the deployment of **Patch 21** (Transplantation of the true HMU05 `rfc_wtr1605_chile_rf360_lte_ag` virtual method into `modem.b26`), the modem booted cleanly with 0 SSR panics, opened all 8 BAM-DMUX channels, and recognized the Reliance Jio USIM (`+CPIN: READY`).

Live telemetry revealed two critical findings:
1. **Active RF Front-End Energy:** QMI signal strength reported `IO: '-106 dBm'` and `SINR: '9.0 dB'`, confirming that the transplanted WTR1605 transceiver and QFE2320 front-end are physically powered and receiving RF carrier signals.
2. **Missing Band Coverage:** Despite hardware support for Bands 1, 3, 5, 8, the NV configuration had `LTE band preference: '5'` exclusively. Because Reliance Jio in urban environments primarily operates on **Band 3 (1800 MHz)** and **Band 40 (2300 MHz TDD)**, the modem was ignoring the strongest local carrier frequencies.

Furthermore, investigative testing identified why `--nas-network-scan` previously triggered an SSR exception: unpatched GSM/WCDMA drivers were attempting to communicate with non-existent WTR4905 hardware during multi-mode scans.

---

## 2. Root Cause Analysis

### A. Network Scan SSR Crash Root Cause
When a full network scan (`--nas-network-scan` or `AT+COPS=?`) is initiated, the multi-mode NAS layer sequences across all configured Radio Access Technologies (RATs):
1. **GSM (2G)** -> calls `rfc_wtr4905_chile_asdiv_prx_gsm_ag` (vtable `0x26c24`).
2. **WCDMA (3G)** -> calls `rfc_wtr4905_chile_asdiv_prx_wcdma_ag` (vtable `0x28e32`).
3. **LTE (4G)** -> calls `rfc_wtr1605_chile_rf360_lte_ag` (Patch 21, offset `0xe7000`).

While Patch 21 hooked the LTE card method to the transplanted WTR1605 driver, the GSM and WCDMA cards remained linked to WTR4905 hardware registers. When the GSM driver attempted to command the transceiver over SSBI/RFFE, invalid register access or missing hardware response triggered a Hexagon fatal exception (`:Excep :0:Exception detected`).

Verification:
- Setting `AT+WS46=28` (LTE Only) constrained the baseband to E-UTRAN.
- Subsequent `AT+COPS=?` execution exited cleanly with `ERROR` instead of crashing the Hexagon DSP (0 SSR crashes).

### B. Single-Band (Band 5 Only) Search Limitation
Querying DMS capabilities and NAS preferences:
```
[DMS] Band capabilities: Bands 'wcdma-2100, wcdma-850-us, wcdma-900', LTE bands '1, 3, 5, 8'
[NAS] LTE band preference: '5'
[NAS] Service domain preference: 'cs-ps'
[NAS] Voice domain preference: 'cs-preferred'
```
- Reliance Jio in India is a pure 4G VoLTE network with zero 2G/3G infrastructure.
- In many locations, Jio Band 5 (850 MHz) has narrow bandwidth or weak signal, with primary service delivered on Band 3 (1800 MHz) and Band 40 (2300 MHz).
- The modem was locked to Band 5 only and was actively seeking Circuit-Switched (CS) 2G/3G service for voice domain preference (`cs-preferred`), leading directly to out-of-service search timeouts (`power-save`).

---

## 3. Implementation: Custom QMI Tooling & NV Configuration

Because upstream `qmicli` does not expose CLI flags for LTE band mask or service domain selection in `--nas-set-system-selection-preference`, the OpenWrt host package `libqmi-1.36.0` was modified:

### Source Modification (`src/qmicli/qmicli-nas.c`)
In `set_system_selection_preference_input_create()`:
```c
if (rat_mode_preference & QMI_NAS_RAT_MODE_PREFERENCE_LTE) {
    QmiNasLteBandPreference lte_bands =
        QMI_NAS_LTE_BAND_PREFERENCE_EUTRAN_1 |
        QMI_NAS_LTE_BAND_PREFERENCE_EUTRAN_3 |
        QMI_NAS_LTE_BAND_PREFERENCE_EUTRAN_5 |
        QMI_NAS_LTE_BAND_PREFERENCE_EUTRAN_8 |
        QMI_NAS_LTE_BAND_PREFERENCE_EUTRAN_40;

    qmi_message_nas_set_system_selection_preference_input_set_lte_band_preference (
        input, lte_bands, &error);

    qmi_message_nas_set_system_selection_preference_input_set_service_domain_preference (
        input, QMI_NAS_SERVICE_DOMAIN_PREFERENCE_PS_ONLY, &error);

    if (!acquisition_order) {
        GArray *custom_acq = g_array_new (FALSE, FALSE, sizeof (QmiNasRadioInterface));
        QmiNasRadioInterface iface = QMI_NAS_RADIO_INTERFACE_LTE;
        g_array_append_val (custom_acq, iface);
        qmi_message_nas_set_system_selection_preference_input_set_acquisition_order_preference (
            input, custom_acq, &error);
        g_array_unref (custom_acq);
    }
}
```

### Build & Deployment
1. Built with `ninja -C openwrt-build src/qmicli/qmicli` using `aarch64-openwrt-linux-musl-gcc`.
2. Transferred to target device and replaced `/usr/bin/qmicli`.
3. Executed command:
   ```bash
   qmicli -p -d /dev/wwan0qmi0 --nas-set-system-selection-preference=lte,automatic
   ```
4. Output confirmed:
   ```
   [/dev/wwan0qmi0] System selection preference set successfully; replug your device.
   ```
5. Verification via `--nas-get-system-selection-preference`:
   - **Mode preference:** `'lte'`
   - **LTE band preference:** `'1, 3, 5, 8, 40'`
   - **Service domain preference:** `'ps-only'`
   - **Acquisition order preference:** `'lte, umts, gsm, cdma-1x, cdma-1xevdo, td-scdma'`
   - **Network selection preference:** `'automatic'`

---

## 4. Cold Reboot Verification Plan

The device was rebooted (`sync && reboot`) to verify:
1. Retention of the updated NV band configuration across cold reset.
2. Modem boot stability under multi-band LTE search.
3. Signal strength and serving cell acquisition on Band 3, Band 5, or Band 40.
4. Testing data channel connectivity via QMI WDS (`wwan0`).
