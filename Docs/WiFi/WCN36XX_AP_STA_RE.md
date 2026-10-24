# WCN36xx AP+STA — reverse-engineering survey and reference map (2026-10-24)

Status: survey complete; implementation not started. Companion to the WPA3 work
(patches 451-453) and the iface-combination crash recorded earlier this year.

## Goal and prior state

Goal: concurrent AP+STA on the WCN3620 (wcn36xx driver, backports 6.18.26).
Prior state: patch 452 advertises an AP-only combo (`{AP} <= 2`) because the
crash was `remoteproc1 a204000` fatal (WCNSS firmware) even with matched
channels; mac80211 rejects STA+AP unless the driver advertises it. Trap on
record: an `ADHOC & max>1` combo makes probe fail -22 (no wiphy).

## Survey findings

### 1. Upstream (torvalds tree, drivers/net/wireless/ath/wcn36xx)

- NO concurrency or multi-vif work exists upstream — never implemented.
  There is no free win from updating the driver alone.
- Four post-6.18.26 stability fixes were backported by this survey (staged as
  mac80211 patches 454-457, commit f27adef):
    - 88a240d86d: HAL response bounds check (heap overflow, firmware-controlled)
    - df2187acfc: PRINT_REG_INFO indication OOB read
    - b5e6f21923: TRIGGER_BA response short-read
    - d9be5e7553: TX-ack timer teardown UAF
  Context note: 454 needed adaptation — patch 453's SET_THERMAL_MITIGATION_RSP
  case sits in the same switch.

### 2. Firmware feature bitmap (live dump, firmware_feat_caps debugfs)

The WCNSS blob advertises: **MCC**, **P2P**, **P2P_GO_NOA_DECOUPLE_INIT_SCAN**,
**SAP32STA**, plus beacon/scan/powersave offloads, TDLS, WOW, CH144 etc.
P2P GO *is* AP-with-STA concurrency; MCC is multi-channel concurrency. The
blob is concurrency-capable by its own admission — treat the crash as a
DRIVER sequencing problem until proven otherwise.

### 3. Firmware image layout (wcnss.mdt, parsed 2026-10-24)

ARM ELF (e_machine 40 — not Hexagon), load base 0x8b600000, 12 segments:
main firmware = ph6 / wcnss.b06 (3.4 MB file, 5.2 MB mem — 1.8 MB zeroed RAM,
same runtime-patchable pattern as the modem b19); b10 = 640 KB data; b11 = 39 KB.
Same PIL signing as MPSS (hash segment + RSA in b01) — the MPSS auth-trap rules
apply if code patching is ever needed. NV bin (WCNSS_qcom_wlan_nv.bin, 29.8 KB)
is UNSIGNED — freely patchable config lever.

## Reference implementation (qcacld-2.0 / prima)

Source: Fairphone 2 kernel (msm8916 + WCN3620 — identical silicon),
`ubports/android_kernel_fairphone_fp2` branch `ubp-5.1`, `drivers/staging/prima`.
Copies archived in `Docs/WiFi/reference/` (limSendMessages.c, wlan_hdd_hostapd.c,
wlan_hdd_cfg80211.c, wlan_hdd_main.c).

What the downstream driver does that wcn36xx does not:

1. **Persona model with distinct MACs.** `VOS_MAX_CONCURRENCY_PERSONA` MACs are
   read from NV (`vos_nv_readMultiMacAddress`) and derived per persona (serialno
   stepping) — every interface gets its own hardware address. wcn36xx uses one
   MAC for everything.
2. **Concurrency mode bookkeeping.** `wlan_hdd_set_concurrency_mode()` /
   `vos_concurrent_open_sessions_running()`; SCC-vs-MCC decided by channel
   equality (`apChannel==staChannel ? SCC : MCC`).
3. **BMPS/IMPS disabled during concurrency.** "Disable BMPS incase of
   Concurrency" (wlan_hdd_main.c:6505) — powersave is turned OFF while two
   personas are active. wcn36xx has pmc.c (IMPS) and does NOT disable it for a
   second interface.
4. **Session model.** Each persona is a separate session with its own id; SAP
   start (`wlan_hdd_cfg80211_start_ap` -> `wlan_hdd_cfg80211_start_bss` ->
   eSAP_START_BSS_EVENT) runs while the STA session stays associated.
5. HAL message set per BSS: ADD_BSS + ADD_STA(self) + START_BSS + beacon
   template. wcn36xx instead sends the combined CONFIG_BSS_REQ and one
   ADD_STA_SELF per interface.

## Ranked hypotheses for the STA+AP crash

1. IMPS/BMPS powersave engaging while the AP beacons (qcacld disables it
   explicitly; wcn36xx does not). Cheapest experiment: patch pmc.c to refuse
   IMPS while an AP vif is up, retry STA+AP.
2. MAC duplication across vifs (one NV MAC for both) — derive per-vif MACs like
   qcacld (NV multi-MAC or base+step).
3. CONFIG_BSS single-message model not handling a second BSS — split into
   ADD_BSS + ADD_STA + START_BSS ordering per qcacld.
4. Channel/policy gap (least likely — crash happened with channels matched).

## Next steps

1. Finish the 400 MHz thermal soak (running; separate thread).
2. Crash-capture instrument: arm a WCNSS dump reader at crash time (a204000),
   reproduce STA+AP once, identify faulting PC/assert text.
3. Implement hypothesis 1 (pmc guard) behind a build flag; retry STA+AP with
   the 454-457 robustness fixes in place.
4. Sequence diff against `Docs/WiFi/reference/` for hypotheses 2-3.
5. Firmware blob changes only if 1-4 fail (see layout above).
