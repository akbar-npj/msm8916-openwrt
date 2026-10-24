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

## Attempt 1 (2026-10-24) — implemented, flashed, measured

Branch `test/wcn36xx-ap-sta` (from `staging-main` 70da8a6), commit 777f6d8.

### Code

* **452 (rewritten)** — two combinations instead of one:
  `{AP}<=2` (keeps the verified multi-SSID behaviour) and
  `{AP}<=1, {managed}<=1` (one channel). ADHOC/MESH stay out of the limits;
  including them makes wiphy registration fail. Live `iw phy phy0 info`:
  `* #{ AP } <= 2` and `* #{ AP } <= 1, #{ managed } <= 1`.
* **458 (new)** — `wcn36xx_pmc_enter_bmps_state()` refuses BMPS while more than
  one vif is in `wcn->vif_list`, mirroring qcacld. Note this is
  belt-and-braces only: mac80211 already forces `IEEE80211_CONF_PS` off
  whenever an AP vif is running (`ieee80211_recalc_ps()` sets `count = 0` on
  AP), so the guard mostly buys a debug breadcrumb.

### Measured on hmuf02v5 (kernel 6.12.94, mac80211 6.18.26)

| step | result |
| --- | --- |
| `iw phy phy0 interface add sta0 type managed` | OK |
| `ip link set sta0 up` | **fails** — mac80211 clones the AP vif's address, RTNETLINK returns a bogus "No error information" |
| same, after `ip link set sta0 address 00:11:22:33:44:55` | OK, interface UP |
| scan with AP beaconing | OK, 3 BSSes, no firmware error, `remoteproc0/1` = running |
| auth / assoc | OK — `RX AssocResp ... status=0`, `sta0: associated` |
| ~100 ms later | **AP sends Disassoc, reason 5 (DISASSOC_AP_BUSY)** |
| GTK install | fails `nl80211: kernel reports: key not allowed` (= `nl80211_key_allowed()`: STATION requires `wdev->connected`) — a consequence of the disassoc, not a key bug |

Controls, all reproducing the identical failure:

* AP vif **down**, plain STA only (no concurrency at all).
* `key_mgmt=WPA-PSK` and `key_mgmt=SAE` (the AP advertises `PSK SAE`, MFP-capable,
  group/pairwise CCMP).
* after a clean reboot (no stale firmware BSS state).

### Conclusion

The concurrency plumbing works: the combination is accepted, the second vif
comes up, the radio keeps beaconing, scanning and associating all succeed and
the firmware does **not** crash. The link cannot complete because of a
**STA-path / AP-interop problem that also exists in plain STA mode** — so it is
not caused by AP+STA and must be cleared before concurrency can be judged.

`hidden1` (3e:77:2f:9e:02:15, ch6) serves other clients fine (host `wld0` is
associated to it), so either this AP has a policy we do not satisfy, or the
wcn36xx STA path is broken against it. A second, known-good AP is required to
tell the two apart.

### Next steps (revised)

1. Reproduce STA against a second AP (phone hotspot / the Android unit's
   hotspot). If it connects there, the issue is AP policy; if it fails there
   too, it is the wcn36xx STA path (capture with the a204000 dump reader).
2. Only then re-test AP+STA, this time with `iw phy phy0 interface add ... addr`
   so the duplicate-MAC window never happens.
3. Keep 454-457 in the image for the crash capture.
