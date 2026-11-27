# WPA3 (PMF) versus aggregation on the WCN3620 — what is broken and what to try next

Status: **driver work exhausted 2026-11-27**. Root cause localised to the
firmware's *transmit* path for management frames to a keyed peer: not one of
eight descriptor/channel variants gets an ADDBA Request onto the air. Two
shippable mitigations remain (section 8).

Related: patches 451 (`wcn36xx-mfp-wpa3`), 464/465/466 (RA and debugfs
diagnostics), 468 (A-MPDU latch), 469 (generic cfg writer). Companion doc for
the driver/AP+STA work: [`WCN36XX_AP_STA_RE.md`](WCN36XX_AP_STA_RE.md).

## 1. The symptom

Same client, same channel, same driver, only the security mode differs:

| AP config | downlink | A-MPDU | AP TX rate |
|---|---|---|---|
| `sae-mixed`, `ieee80211w=1` | **6.6 Mbit/s** | none (`TX=0`) | MCS 0-1 |
| `psk2`, `ieee80211w=0` | **31.8 Mbit/s** | `TX=1 RX=1` | MCS 7 |

Live confirmation on a mifib-v12 with one client at **-24 dBm**:

```
MFP: yes
tx bitrate: 6.5 MBit/s MCS 0        <- AP -> client
rx bitrate: 65.0 MBit/s MCS 7       <- client -> AP
expected throughput: 5.742 Mbps
tx retries: 0
```

So it is not a bad link and not retransmission. The AP simply never leaves MCS 0
once PMF is on.

## 2. Why a missing Block Ack costs 5x, not 20 %

mac80211 does not pick the AP's data rate here: `wcn36xx_set_tx_data()` sets
`bd->bd_rate = WCN36XX_BD_RATE_DATA` (`txrx.c:555`), which tells the **firmware**
to run its own rate adaptation. That RA has no per-frame feedback to work with
unless a Block Ack session exists, so without BA it stays at the lowest MCS.

That is why the loss is a factor of five rather than a few percent: no BA means
no A-MPDU *and* no rate ramp. Any fix has to restore the BA session; raising the
rate without it is not a lever the AP has.

## 3. What actually fails — the ADDBA handshake

Chain, verified with `WCN36XX_DBG_TX` plus an over-the-air capture from the AP's
own monitor interface:

1. traffic starts, `wcn36xx_tx_start_ampdu()` fires after
   `WCN36XX_AMPDU_START_THRESH` (20) frames;
2. `ampdu_action(TX_START)` → `wcn36xx_smd_add_ba_session()` succeeds, the
   firmware returns an `fw-ssn`;
3. mac80211 builds an ADDBA Request and hands it to the driver. The TX log shows
   `tx skb len 49 fc 40d0 high ucast` — 33 bytes of frame + 8 CCMP header +
   8 MIC, i.e. **already encrypted by mac80211 in software** (patch 451 sets
   `IEEE80211_KEY_FLAG_SW_MGMT_TX`), with the Protected bit set;
4. the frame never appears on air. The capture shows the AP's beacons, auth and
   assoc responses, and **zero** AP action frames, while the client sent 24
   protected action frames that the AP never answered;
5. `ADDBA_RESP_INTERVAL` (1 s) expires → `ampdu_action(TX_STOP_CONT)` → the
   latch in `wcn36xx_tx_start_ampdu()` never clears.

So the AP can receive protected action frames, associate a PMF client, and pass
data — but it cannot transmit a single protected management frame.

## 4. New evidence (2026-11-27): the firmware *receives* them fine

`WCN36XX_DBG_RX|WCN36XX_DBG_TX` (mask `0x450`) on a live AP while a client
pushed traffic:

```
fc 4188  x972   protected QoS data
fc 4988  x132   protected QoS data
fc 40d0  x1     protected Action frame, delivered to mac80211
```

`0x40d0` is a **protected action frame that the firmware decrypted with the PTK
and passed up**. That matters: it rules out "the firmware has no 802.11w
support" and points at a gate in the TX descriptor path instead. The client's
ADDBA Request arrives, mac80211 answers it, and the answer dies on the way out.

Note the asymmetry: RX needs only a DPU lookup for an existing key, TX needs the
DPU to *encrypt and own the PN* — a different code path, and the one that fails.

## 5. The five variants already tried (all reproduce the drop)

| # | change | result |
|---|---|---|
| A | `dpu_ne = !protected` | plaintext 33 B on air |
| B | peer `sta_index` + `dpu_desc_idx`, `dpu_ne = 0` | plaintext 30 B on air |
| C | B + `dpu_sign = ucast_dpu_sign` | still plaintext 30 B |
| D | drop `SW_MGMT_TX` so the firmware should encrypt | frame dropped |
| E | route protected mgmt through the data DXE path (`is_low = true`) | frame dropped |

A-C left mac80211 doing the CCMP, so the firmware received an already-encrypted
frame and either dropped it (`dpu_ne=1` → "protected but not encrypted") or
refused to encrypt twice. D and E removed the SW encryption but kept the mgmt
path's `dpu_ne=1`, so the firmware still had nothing to encrypt. **No attempt
ever combined plaintext input with `dpu_ne=0` and a full peer DPU on the data
path** — see below.

## 6. The full data-frame recipe — built and tested, still fails

The "untested variant" from the first pass was implemented behind a
runtime-switchable module parameter `mgmt_dpu` (default **0** = original
behaviour, so a normal boot is unaffected):

```c
/* wcn36xx_start_tx(): robust mgmt frame to a keyed peer */
bd->sta_index    = peer->sta_index;        /* peer, not self */
bd->dpu_desc_idx = peer->dpu_desc_index;
bd->dpu_sign     = peer->ucast_dpu_sign;
bd->dpu_ne       = 0;                      /* firmware encrypts */
```

Supporting changes: `set_key()` only sets `SW_MGMT_TX` in variant 0; the peer
is found with `ieee80211_find_sta_by_ifaddr(wcn->hw, hdr->addr1, hdr->addr2)`
under RCU because `control->sta` is **NULL** for management frames (this was
the reason the first build of the experiment fired zero times); the gate is
`!is_low && mgmt_dpu && !bcast && ieee80211_has_protected() &&
ieee80211_is_robust_mgmt_frame() && peer->is_data_encrypted`.

Two traps found while testing, both worth recording:

- `dhd` client profiles: NetworkManager's `802-11-wireless-security.pmf` uses
  **0 = default, 1 = disable, 2 = optional, 3 = require**. Setting 3 to
  "disable" actually *forces* PMF. The AP's BSSID also flips between
  `02:00:59:8a:3a:21` and `06:00:59:8a:3a:21` on each `wifi` restart, so the
  client's pinned BSSID has to be refreshed or it silently joins a neighbour.
- `dmesg` on the unit can lag a freshly-written log line by one shell round
  trip; re-read before concluding a probe did not fire.

Results, all under `sae-mixed` + `ieee80211w=1`, client `MFP: yes`, confirmed by
an over-the-air capture from the AP's own monitor interface:

| variant | BD / channel | AP action frames on air | BA session |
|---|---|---|---|
| 1 | peer DPU, `dpu_ne=0`, high (mgmt) DXE, `sign 0` | **0** | none |
| 2 | same through low (data) DXE, `sign 1` | **0** | none |
| 3 | clear the Protected bit, `dpu_ne=1`, self DPU | **0** | none |

The driver log proves each path ran (`fw-crypt mgmt fc 40d0 peer 2 dpu 3 sign
1 low`, `pmf-plain mgmt fc 00d0 len 33`), the ADDBA Request was handed to
`wcn36xx_dxe_tx_frame()`, and the client sent its own protected action frames
throughout — but the AP emitted **no Action frame at all**. Variant 3 rules out
the Protected bit itself as the drop trigger: even a plain, unprotected ADDBA
on the mgmt channel disappears. Together with the earlier five variants this
is a firmware-level refusal to transmit AP Action frames to a keyed peer under
PMF, not a descriptor-field mistake.

Control run on the same module with `mgmt_dpu=0`, `psk2`, `ieee80211w=0`:
BA sessions open on TID 0 and TID 3 immediately, 29 MB in 20 s, `MFP: no`.
So the client and the driver are fine; only the PMF case is broken.

## 7. Dead ends — do not redo these

- **Patching the WCNSS firmware.** `wcnss.b01` is a hash table plus an RSA
  signature over the segments and WCNSS is brought up through
  `qcom_scm_pas_auth_and_reset()`; modifying a segment fails PIL authentication
  (`MPSS/WCNSS authentication failed: -19`), exactly like the modem. There is no
  known unhashed code cave. Locating the drop check inside a symbol-less 2.86 MB
  ARM32 blob would be a large RE project with no guarantee.
- **Disabling PMF under SAE.** hostapd rejects `ieee80211w=0` with an SAE AKM
  ("SAE: PMF is required"), and every WPA3 client sets MFPC=1, so the ADDBA is
  always protected. `sae-mixed` does not help: the AP still advertises MFPC=1.
- **`bmps_switcher`.** STA-only, no-op on an AP (see the #122 reply).
- **Firmware config knobs** (226, 208, 55, 56, 79). These affect power save, not
  the mgmt TX path; the stall in #122 is a different problem from this one.
- **`recal` / RA poke alone.** Refreshing the existing table value does not move
  the rate. Writing a *different* value does — see section 8.

## 8. What can actually be shipped

Two mitigations, one performance-only and one a product decision:

1. **Raise the firmware RA start rate (safe, no security trade-off).**
   Patch 464 already exposes `ra_start_rate` (module parameter) and the generic
   WCN3620 table sends `ENABLE_DYNAMIC_RA_START_RATE = 133` (MCS 5). With the
   `cfgwrite` node from patch 469, writing **210 135** (MCS 7) at runtime moved
   the AP from MCS 1 / 8.8 Mbit/s to MCS 4 / **12.3 Mbit/s** with PMF still on.
   This does not restore aggregation, but it is a real ~40 % gain and can ship
   as a table value or `/etc/modprobe.d/wcn36xx.conf` option for WCN3620 only.
   A fixed rate (`cfg 18`) is *not* better: it stalls at 54 Mbps legacy and was
   worse than the RA tweak.
   `cfg 210` is volatile — it must be set at MAC start, not once per boot by
   hand; `ra_start_rate=135` is the durable form.

   **WPA2 regression check (2026-11-27).** Because the durable form is a global
   module parameter, the same value also applies when PMF is off. An A-B-A-B
   run on the mifib-v12 in `psk2` / `ieee80211w=0` (client at -20 dBm, BSSID
   pinned to `02:00:59:8a:3a:21`, 8 s warm-up + 22 s SSH `/dev/zero` downlink,
   client-interface byte counters) found no regression:

   | arm | downlink |
   |---|---|
   | A1 — `cfg 210 135` | 34.8 Mbit/s |
   | B1 — `cfg 210 133` | 33.9 Mbit/s |
   | A2 — `cfg 210 135` | 35.4 Mbit/s |
   | B2 — `cfg 210 133` | 34.3 Mbit/s |

   Mean 35.1 (MCS 7) vs 34.1 Mbit/s — about 3 %, within run-to-run noise.
   A-MPDU was up in every arm (`tx retries: 0`). So `ra_start_rate=135` is
   safe to ship as the WCN3620 default: it gains ~40 % under PMF and costs
   nothing measurable under WPA2. Caveat: the bench client is very close; a
   weak-signal client will start at MCS 7 and fall back, so this should get one
   attenuation sanity check before it becomes a fleet-wide default.

   Note: the earlier cold-start (`rmmod`/`insmod ra_start_rate=...`) attempts
   were interrupted by an OOM pause and wrote to the wrong debugfs PHY
   (`phy0` instead of `phy3`), so their numbers are not used here. The runtime
   `cfgwrite` A/B exercises the same firmware cfg field that the module
   parameter sets at MAC start.
2. **Default to `psk2` / `ieee80211w=0`, keep WPA3 opt-in.** This is the only way
   to get A-MPDU and MCS 7 (measured 31.8 Mbit/s). It is a security-vs-speed
   decision for the product, so it has not been changed in the tree yet.
   If WPA3 is kept as an option, LuCI should warn that it costs roughly 5x
   downlink on the WCN3620: the firmware will not transmit any AP Action frame
   to a keyed peer, so ADDBA never completes and the firmware RA never leaves
   the lowest MCS.

The warning matters more than the default: a user who turns WPA3 on and then
sees 6 Mbit/s will otherwise conclude the port is broken.

## 9. Bench state (2026-11-27)

The experimental module is **not** in any patch: all edits live only in
`openwrt/build_dir/.../wcn36xx/{txrx.c,main.c,wcn36xx.h}` behind `mgmt_dpu=0`
(default), so a clean rebuild reverts them. Patches 451/464/465/466/468/469
are the only committed driver changes.

Restoring the AP from the WPA2 test config back to `sae-mixed` + `ieee80211w=1`
hung the unit on `wifi up` (same trap as the earlier WPA3 reload) and needed a
power cycle. As of the 2026-11-27 WPA2 A/B the unit is deliberately back in
`psk2` / `ieee80211w=0`; the pre-A/B WPA3 config was saved on the unit as
`/tmp/wireless.wpa3.ab` and the live `/etc/config/wireless` is WPA2. The
`ra_start_rate=135` seen in `/sys/module/wcn36xx/parameters` came from a manual
`insmod` and is **not** persistent — a reboot returns to the default `-1`
(table value 133). `cfg 210` was left at 133 by the last A/B arm.
