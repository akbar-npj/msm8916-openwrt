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

## Attempt 1 RESULT — STA+AP WORKS (2026-10-24)

Against a normal AP (`androidap`, Android hotspot, ch6) both interfaces run at
the same time on the single wcn36xx radio:

| | evidence |
| --- | --- |
| AP serving a client | `iw dev phy0-ap0 station dump` -> `Station 1a:73:65:b1:ee:83`, -54 dBm, rx 200 565 B |
| STA associated | `iw dev sta0 link` -> `Connected to 8e:aa:47:8a:a1:6c (androidap)`, -52 dBm, rx 905 886 B |
| same channel | both `channel 6 (2437 MHz), width 20 MHz` |
| data path | DHCP 10.162.21.33, gw ping 10.3 ms avg, 8.8.8.8 44.9 ms |
| 90 s soak | 6/6 checks: STA connected + gateway ping OK + AP up; no wcn36xx/firmware error; `remoteproc1` running |

A phone associated to `Hmu05-White` *while* the device stayed associated to
`androidap` — the concurrency case, end to end.

### What actually mattered

1. **452** (combination `{AP}<=1,{managed}<=1`) — without it mac80211 refuses the
   second interface outright.
2. **A distinct MAC on the second vif.** mac80211 clones the AP vif's address
   for a new interface, and `ip link set sta0 up` then fails with a bogus
   "No error information". `iw phy phy0 interface add sta0 type managed addr ...`
   avoids the window entirely.

This also **retires the "STA+AP crashes the WCNSS firmware" finding**: that
observation was almost certainly the duplicate-MAC configuration, not
concurrency. With distinct MACs the firmware never faltered — 454-457 were in
the image but nothing in the log suggests they were exercised.

### Caveats / what is still needed for a feature

* **Single channel only** (`num_different_channels = 1`). The AP must sit on the
  STA's channel; if it does not, mac80211 refuses to move the beaconing AP and
  the association fails. OpenWrt has to keep `wireless.radio0.channel` in sync
  with the upstream AP (or the STA must follow the AP's channel).
* The STA vif was created by hand. `wifi reload` tears the phy down and takes
  `sta0` with it, so it is not persistent. A real feature means a second
  `wifi-iface` (`mode=sta`) in `/etc/config/wireless`, letting netifd own both.
* The STA became the default route (`default via 10.162.21.74 dev sta0`) and
  displaced the modem's path. Needs policy routing (metric / mwan3) so the
  modem stays primary.
* STA tx bitrate reported 1-2 Mbit/s against a -52 dBm AP — worth checking rate
  control / powersave once the feature is config-driven.
* `hidden1` (3e:77:2f:9e:02:15) still disassociates with reason 5 in *plain* STA
  mode. That is AP-specific and unrelated to concurrency, but it means not every
  AP will work for testing.

## netifd-managed AP+STA CRASHES the WCNSS firmware (2026-10-24)

Config: AP (`default_radio0`, default MAC) + STA (`wifinet1`, `mode=sta`,
`macaddr='random'`, SSID `hidden1`), `radio0.channel='6'`.

Boot log:

```
[  29.965] qcom-wcnss-pil a204000.remoteproc: fatal error received:
           boot_exception_c_handlers.c:108:Data Abort Exception at 0x8b6b301c
[  29.977] remoteproc remoteproc1: handling crash #1
[  79.665] ... same signature ... handling crash #2
[  89.566] wcn36xx: ERROR Timeout! No SMD response to req 24 in 10000ms
[  89.566] wcn36xx: ERROR Sending hal_set_bsskey failed
[  99.806] wcn36xx: ERROR Timeout! No SMD response to req 127 (delete_sta_self)
[ 109.832] wcn36xx: ERROR Timeout! No SMD response to req 2 (hal_stop)
```

Result: `remoteproc1` = `crashed`, no usable wiphy, load average 10.6, LuCI
unresponsive. Recovery required a reboot with the STA iface disabled.

Two cfg80211 WARNs accompany it (`cfg80211_register_netdevice` during hostapd's
interface creation and `cfg80211_scan_done` at scan.c:1193 from
`ieee80211_sched_scan_stopped`) - the scan bookkeeping across two vifs is
already wrong before the firmware dies.

### Why the manual test passed

The successful run created the AP first (netifd), then added `sta0` by hand and
let wpa_supplicant associate. Here netifd creates **both** interfaces and starts
hostapd and wpa_supplicant concurrently, and wpa_supplicant immediately scans
for `hidden1` while hostapd is configuring the AP BSS. The single wcn36xx scan
engine plus a BSS bring-up in flight is the new ingredient.

### Candidate workarounds (untested)

1. `option bssid='<upstream BSSID>'` on the STA iface - wpa_supplicant then
   skips scanning and goes straight to auth, removing the concurrent-scan window.
2. Bring the STA up only after the AP is established (init/hotplug script doing
   `ifup` on the STA network a few seconds after `phy0-ap0` appears).
3. Firmware-level: decode PC `0x8b6b301c` (load base `0x8b600000`, offset
   `0xB301C`) in `wcnss.mdt` - the first hard fault address we have for the
   concurrency path.

Until one of these is proven, **ship AP-only**; AP+STA stays a manual/experimental
configuration.

## E1/E2 (2026-10-24) — the crash is the PHY RESTART, not the STA vif

### E1: retested with a known-good upstream and fixed MACs

Config: AP `Hmu05-White` (ch6, default MAC) + STA `wifinet1` -> `androidap`
(psk2, `macaddr='00:11:22:33:44:77'`, **no** `random`). This removes both
confounds of the first attempt (the broken `hidden1` upstream and
`macaddr=random`).

Result: **crash, byte-identical signature** —

```
[ 29.260] wcn36xx: firmware WLAN version '...' / API 1.5.1.2
[ 29.301] phy0-ap0 created, br-lan blocking/disabled, allmulti+promisc
[ 29.374] phy0-ap0 left allmulti/promisc        <- vif DELETED
[ 29.513] phy0-ap0 created again                <- vif RE-ADDED
[ 29.977] Data Abort Exception at 0x8b6b301c    <- 0.46 s later
```

⇒ `hidden1` and `macaddr=random` are **exonerated**. The retry-storm
hypothesis is dead.

### AP-only control

AP-only boot is clean (**0 crashes**) and still logs the same two
`command failed: Not supported (-95)` from `radio0` plus
`hostapd: Restart interface for phy phy0`. So the restart path runs on every
boot and is harmless **without** a managed iface in the phy config.

### E2: runtime `wifi reload` with the STA configured

`uci set wireless.wifinet1.disabled=0; wifi reload` →

```
[ 78.291] phy0-ap0 left allmulti/promisc        <- AP vif deleted
[ 78.869] phy0-ap0 created again
[ 79.168] Data Abort Exception at 0x8b6b301c    <- 0.30 s later
```

**No STA vif was ever created in this run** (`dmesg` has no `sta0`/managed vif
at all). The only difference from the clean AP-only reload is that
wpa_supplicant is running with a managed BSS in the phy config; it restarts at
the same instant (`supp-global-ctrl-iface-init ... bind(PF_UNIX): Address in
use`) and its scan lands while the AP BSS is being re-created.

**Conclusion:** the firmware dies on a *phy restart* (hostapd
`Restart interface`) whenever a wpa_supplicant-managed iface is configured on
that phy. The manual AP+STA test never restarted the phy — it only did
`iw phy phy0 interface add` against an already-beaconing AP — which is exactly
why it worked.

⇒ Any design that routes the STA through `/etc/config/wireless` will hit this,
because netifd's wifi script restarts the whole phy whenever the wireless config
changes (including at every boot). **AP+STA must not use a `wifi-iface`.**

### Secondary: kernel oops 30 s after the firmware crash

When netifd retried `ip link set phy0-ap0 up` on the dead firmware:

```
[ 109.276] Unable to handle kernel paging request at ffff800080cf5004
           ESR 0x96000007, FSC 0x07 (level 3 translation fault), WnR = 0
[ 109.441] pc : __memcpy+0x110/0x260
[ 109.447] lr : wcn36xx_smd_load_nv+0x94/0x170 [wcn36xx]
[ 109.537] drv_start+0x30/0x50 [mac80211]
           ieee80211_do_open+0x258 -> __dev_open -> dev_change_flags
```

`wcn36xx_smd_load_nv()` does
`memcpy(wcn->hal_buf + sizeof(msg_body), &nv_d->table + fm_offset, 3072)`
with `nv_d = wcn->nv->data`; here `x1` (source) is `ffff800080cf5004` — not a
firmware-loader allocation at all. The cached `wcn->nv` is only released in
`wcn36xx_remove()` and is **not** set to NULL, and `wcn36xx_start()` trusts it.
Most consistent reading: after the SSR the driver is re-entered on a `wcn`
whose firmware blob has already been released. **Not yet proven** — needs a
`wcn->nv` pointer print or a KASAN build. Consequence regardless: a crashed
WCNSS makes the kernel oops on every netifd retry instead of failing cleanly.


## E4 (2026-10-24) — late-STA service: SHIPPED, boot-verified

The E1/E2/P1 results reduce the bug to one sentence: **a phy restart while a
managed iface is configured on that phy kills the WCNSS firmware**.  netifd's
wifi script restarts the phy on every wireless-config change (including every
boot), so an AP+STA setup expressed as two `wifi-iface` stanzas can never be
safe.  The verified-safe path is the manual one: let the AP come up first, then
add the STA vif with `iw` and let wpa_supplicant associate.

`/etc/init.d/wifi-sta-late` automates exactly that, as two procd instances:

| instance | command | does |
|---|---|---|
| `sta` | `wifi-sta-up sta` | polls `hostapd.<ap>.get_status` until `ENABLED`, then `iw phy <phy> interface add <phy>-sta0 type managed addr <mac>`, writes the wpa config, and `exec`s `wpa_supplicant` in the foreground |
| `dhcp` | `wifi-sta-up dhcp` | waits for `iw dev <sta> link` to report `Connected`, then `exec`s `udhcpc -f` with `wifi-sta-dhcp.sh` |

Both are `procd_set_param respawn 3600 5 0`, so either one dying is repaired.
`wpa_supplicant -B` is deliberately **not** used: it daemonises to PPID 1 and
procd can no longer supervise it (observed: only udhcpc was supervised).

Files:

- `/etc/config/wifista` — `enabled` (default **0**), `macaddr`, `ssid`, `key`,
  `dhcp` (default 1), `wait_ap_timeout` (default 60).
- `/usr/sbin/wifi-sta-up` — both modes.
- `/usr/sbin/wifi-sta-dhcp.sh` — minimal udhcpc handler: address + connected
  route only, **never a default route** (the LTE link must stay the default).
- `/etc/uci-defaults/95-wifi-sta-late` — enables the service.

### Why not netifd for the STA netdev

`option device 'phy0-sta0'` on a netifd interface fails with
`errors: [{subsystem: interface, code: NO_DEVICE}]` — netifd has no device
object for a vif it did not create, and `ubus list` shows no
`network.device.phy0-sta0`.  udhcpc is therefore run directly.  The trade-off is
that the lease is invisible to LuCI/ubus (it is a plain `ip addr`), which is
acceptable for a concurrent-AP+STA feature.

### Verification (HMU05, image `c24dd181`)

Boot, no manual step:

```
wifi-sta: phy0-sta0 created on phy0 (phy0-ap0 ENABLED after 0s); ssid 'androidap'
wifi-sta: phy0-sta0 associated after 2s
wifi-sta: dhcp: 10.162.21.33/24 on phy0-sta0
dmesg | grep -c "handling crash"  ->  0
phy0-sta0  UP  10.162.21.33/24      phy0-ap0  ssid Hmu05-White  ch6
ping -I 10.162.21.33 8.8.8.8 -> 0% loss, 56-96 ms
default route still via wwan0 (LTE)
```

### Caveats

1. **`wifi reload` / LuCI wireless save while the STA is up restarts the phy
   with `phy0-sta0` present → same firmware crash.**  Stop the service first:
   `/etc/init.d/wifi-sta-late stop; wifi reload; /etc/init.d/wifi-sta-late start`.
   A wrapper around `/sbin/wifi` would remove this footgun and is not written.
2. **A fast stop/start cycle can log one `SME: Authentication request to the
   driver failed`** and put the BSSID on wpa_supplicant's 10 s ignore list; it
   recovers by itself (observed during `restart`, not at boot).
3. The STA gets **no default route by design**.  To use it as an uplink, add a
   route/policy rule yourself.
4. `enabled` ships **0**; this is opt-in.


5. **`wifi reload` briefly bounces `br-lan`** (the USB gadget is a bridge
   port), so an SSH session over the gadget link is dropped mid-command.  The
   verified sequence is: `/etc/init.d/wifi-sta-late stop` -> `wifi reload` ->
   `/etc/init.d/wifi-sta-late start`; measured 0 firmware crashes, and the STA
   comes back with its lease.


## Channel is shared: the AP follows the STA (2026-10-24)

The station has no channel of its own — it goes where the upstream is.  With one
radio there is only one channel at a time, so a mismatch is fatal to the STA:

```
AP forced to channel 1, upstream on channel 6:
phy0-sta0: Trying to authenticate with 2a:2a:8a:9d:f5:2c (SSID='androidap' freq=2437 MHz)
phy0-sta0: SME: Authentication request to the driver failed
phy0-sta0: CTRL-EVENT-SSID-TEMP-DISABLED ... auth_failures=2 duration=28
```

**Measured:** no firmware crash, no lease, just a growing back-off loop — the
driver refuses rather than dragging the AP off-channel.

So `wifi-sta-up sta` now syncs the channel itself, **before** the STA connects:

1. AP is ENABLED (any channel).
2. Create the STA vif and `ip link set up` it — a down netdev cannot scan
   (`iw scan` returns ENETDOWN), which is what the first implementation missed.
3. `iw dev <sta> scan` and read the target SSID's channel.  Parsing gotcha:
   `iw` prints the channel as `\t\t * primary channel: N` under HT operation, not
   as a bare `primary channel:` line — the first regex silently matched nothing.
4. If it differs from the AP's channel: **delete the STA vif**, `uci set
   wireless.<radio>.channel`, `wifi reload`, wait for the AP to come back
   ENABLED on the new channel, then re-create the STA vif.
5. Connect.

Step 4's reload is only safe because the STA vif is gone first — the same
invariant as everywhere else in this document.

Live result (AP deliberately set to ch1, upstream on ch6):

```
wifi-sta: upstream 'androidap' is on channel 6, AP is on 1; moving the AP
wifi-sta: AP moved to channel 6; re-creating phy0-sta0
wifi-sta: phy0-sta0 created on phy0 (phy0-ap0 ENABLED after 0s); ssid 'androidap'
wifi-sta: dhcp: phy0-sta0 associated after 4s; 10.162.21.33/24
```

`option radio 'radio0'` in `/etc/config/wifista` selects which radio's channel is
moved.  Caveats: this runs at bring-up only — if the upstream later moves channel
(CSA/reboot), the STA drops and stays down until the service is restarted; and
moving the AP channel briefly disconnects its own clients (normal for a channel
change, no CSA is sent).

