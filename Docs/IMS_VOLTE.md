# IMS registration and VoLTE on MSM8916 sticks

What it takes to get a Qualcomm MSM8916 modem stick registered on IMS and
making VoLTE calls under OpenWrt, and what we learned on the way (October and
November 2026). Two boards were worked on:

| Board | Modem build | RF | Result |
| --- | --- | --- | --- |
| UFI003 MB V02 | `MPSS.DPM.2.0.2.c1-00178` | WTR4905 | **Works**: IMS registered, MO/MT VoLTE calls, call waiting, audio to a BT headset and SIP phones |
| a compatible modem profile (HiMI) | `MPSS.DPM.1.0.c7-00193` | WTR1605 | **Not working**: IMS stack present, never asks for the IMS PDN |

The rest of the call stack (audio, Bluetooth, Asterisk) is in
[UFI003_VOICE.md](UFI003_VOICE.md); the watchdog interplay is in
[Modem Stability/MODEM_BEARER_WATCHDOG.md](Modem%20Stability/MODEM_BEARER_WATCHDOG.md).
This page is about IMS and VoLTE itself.

Short version:

- The modem runs IMS itself (SIP, registration, VoLTE call manager, RTP,
  IPsec). Linux never sees SIP or RTP. Linux has to: enable IMS in the modem
  EFS, give the modem an `ims` PDN when it asks for one (hybrid mode), and
  open the DSP's VoLTE voice session for audio.
- VoLTE needs a modem whose **MCFG carrier configuration has the IMS
  items**. On a DPM.2.0.2 modem (UFI003) the `a compatible modem profile` MCFG from the modem
  partition has them; five EFS items on top are enough.
- On a DPM.1.0 modem (a compatible modem profile and other WTR1605 sticks) there is no
  matching IMS configuration; donor MBNs from DPM.2.0.2 crash it. Treat
  DPM.1.0 sticks as "no VoLTE" until someone finds what gates the PDN
  request.

## 1. Architecture

```
          operator IMS core (P-CSCF)
                 ▲ SIP / RTP over the ims PDN (IPv6)
 modem ──────────┼──────────────────────────────────────────────┐
 │  IMS stack: registration, SIP, qipcall (VoLTE calls), RTP     │
 │     │ hybrid mode: "please bring up the ims PDN"              │
 │     ▼ QMI service 770 (IMS data connection, client side)      │
 │  Q6 DSP: VoLTE voice session (vocoder) ──▶ AFE ports          │
 └─────┼────────────────────────────────────────────────────────┘
       │ QRTR
 Linux ▼
   imsdcm (serves QMI 770) ──▶ ModemManager: bearer apn=ims ipv4v6
   ModemManager voice (QMI VOICE): dial / answer / hold / waiting
   voicectl: opens the VoLTE (or CS) DSP session, mixer routes
```

- **Modem-side IMS.** The DPM firmware contains Qualcomm's full IMS client
  (`modem_proc/ims`: registration manager, SIP over UDP/TCP, P-CSCF
  discovery via PCO/DHCP/ISIM, IPsec, `qipcall`, `qvp_rtp` with AMR). It
  reads its settings from EFS items under `/nv/item_files/ims/`, normally
  provided by the active MCFG (carrier configuration).
- **Internal vs hybrid.** In internal mode the IMS stack opens the `ims` PDN
  with the modem's own data services. In hybrid mode
  (`ims_hybrid_enable` = 1) it asks an AP-side **IMS data connection
  service** (QMI service 770, "DCM") to do it. This is what Android's
  `imsdatadaemon` / postmarketOS `81voltd` serve. On the UFI003 we use hybrid
  mode with our own `imsdcm`, so the bearer goes through ModemManager and
  netifd sees it.
- **Call control** stays in ModemManager (QMI VOICE). A VoLTE call looks
  like any other call to ModemManager.
- **Audio.** VoLTE voice is decoded in the DSP, on its own **VoLTE voice
  session**. A VoLTE call has audio only on the VoLTE session (the CS
  session records silence), a CS call only on the CS session.

## 2. UFI003 recipe (DPM.2.0.2)

### 2.1 Modem profile

Use the generic ROW MCFG referenced by `volte-setup`; it must include IMS
support and the settings required by the target network. Keep network-specific
profile data device-local and out of shared configuration or documentation.

### 2.2 EFS items

| EFS item | Value | Source |
| --- | --- | --- |
| `/nv/item_files/ims/IMS_enable` | `01` | written |
| `/nv/item_files/ims/ims_operation_mode` | `02` | from a compatible modem profile |
| `/nv/item_files/ims/ims_hybrid_enable` | `01` (AP provides the PDN) | written |
| `/nv/item_files/ims/qp_ims_reg_config` | 405 bytes; APN `ims` at offset 2 | a compatible modem profile leaves the APN empty; written |
| `/nv/item_files/modem/mmode/voice_domain_pref` | `03` (IMS preferred), **exactly 1 byte** | written |

The `voice_domain_pref` size matters. The ROW MCFG leaves a 2-byte item;
with it the modem registers IMS for **SMS only** (IMS registered, voice
service unavailable, calls on 3G).

`volte-setup` (package `ufi-voice`) does all of it:

```sh
volte-setup status            # MCFG, the items, IMS registration, SMS/voice service
volte-setup apply --restart   # write what differs, sync the EFS, restart the modem
```

How it works, in case it has to be done by hand:

- EFS access goes over DIAG with `diag_efs` (package `diag-logtool`),
  always under `flock /var/lock/himi-ok-diag.lock`, the same lock as the
  NV 2500 guard. Two DIAG users at once hang `/dev/diag` writes.
- **EFS writes live in the modem's RAM copy** until the modem syncs EFS to
  `modemst1/2` through `rmt_storage`. A reboot within ~15 minutes of a write
  loses it. `volte-setup` forces a sync with the DIAG EFS2 sync request:
  `flock $LOCK diag_logtool req 4b13300001002f00 0x4b`.
- The modem reads the items when it starts: restart it afterwards
  (`echo stop`/`echo start` to the `4080000.remoteproc` remoteproc's
  `state`, or reboot).
- `qp_ims_reg_config` patch: if the item is 405 bytes and bytes 2-4 are
  zero, insert `69 6d 73` ("ims") at offset 2 and keep the rest. If the item
  is missing or has another size, the right MCFG is not active; do not
  write one from scratch.

### 2.3 Verify

```sh
qmicli -d qrtr://0 --imsa-get-ims-registration-status   # Status: 'registered'
qmicli -d qrtr://0 --imsa-get-ims-services-status       # Voice service: available
mmcli -m any --list-bearers                             # one bearer has apn ims
logread -e imsdcm                                       # start/stop requests, address
```

Registered with only the SMS service available means `voice_domain_pref` is
wrong (§2.2). Registered and voice available, but calls still go out on 3G,
means the audio/session side picked CS (§5).

Use `qmicli` on `qrtr://0` sparingly from scripts: every call takes a QMI
client id. A polling loop has exhausted client ids before
(`ClientIdsExhausted`).

## 3. imsdcm: the AP side of the IMS data connection

`/usr/sbin/imsdcm` (Python, procd service `imsdcm`, enabled by
`ufivoice.main.volte`) serves QMI service 770 version 1 instance 0 over
`AF_QIPCRTR`. The message layout follows postmarketOS OpenIMSD's `81voltd`
(`imsd.qmi`).

### 3.1 Protocol, as this modem uses it

| Message | Direction | What we do |
| --- | --- | --- |
| 0x20 start connection (APN, IP family) | modem → AP | `mmcli --create-bearer apn=ims,ip-type=ipv4v6` + connect. Response TLVs 0x10 (local handle, u8), 0x11 (connection handle), 0x12 (subscription). Then indication 0x20 with the address in TLV 0x11 `{u32 family, u8 len, string}` |
| connection down | AP → modem | indication 0x20 with result error 13 and no address |
| 0x21 stop connection | modem → AP | response TLV 0x10 = 0xfa, TLV 0x11 echoes the handle; tear the bearer down, then **report it down**: without the down indication the modem never asks again |
| 0x23 and the rest | modem → AP | plain success, no action |

- The modem's stop request carries a handle (`local=1`) that **does not
  match** the handle the AP returned. Do not map it to the active connection
  by handle.
- The P-CSCF address comes to the modem in the network's PCO; the AP does
  not have to pass it.

### 3.2 Start-up order

The order is what made the UFI003 register reliably:

1. **Wait for LTE (PS) attach.** If the service is published before the
   attach, the modem only sends 0x23 and never 0x20.
2. **Wait for the WAN interface** (`ufivoice.main.wan_iface`, default
   `modem`) to be up, plus 10 s. At boot `qcom-carrier-autocfg` deletes every
   bearer while the WAN comes up; an IMS bearer made before that vanishes
   under the modem, which then stops asking.
3. Wait for the attach again.
4. Delete leftover `ims` bearers.
5. Publish service 770.

### 3.3 Keeping it alive

- Connections are kept in `/tmp/imsdcm.state`. A new instance reports the
  old ones down to the modem first, so the modem asks again.
- A watchdog every 15 s:
  - a bearer that disappeared is reported down at once;
  - **a ModemManager modem path change** (modem restart/SSR) makes `imsdcm`
    exit; procd starts a fresh instance that goes through §3.2. After an SSR
    the modem asks for the PDN while ModemManager has no modem yet, gets an
    error and stops asking, so a restart of the service is needed;
  - idle (no connection and IMS not registered) for `ims_kick_after` seconds
    (180) → exit and restart. The limit doubles each time it does not help
    (`/tmp/imsdcm.kick`, up to 3600 s), e.g. on a network without VoLTE.
- A failed connect forgets the bearer, so the next request creates a new
  one.
- A connection that went away is dropped from the table at once. A stale
  entry made the old watchdog believe IMS had its PDN: IMS stayed down and
  calls fell back to 3G.
- The socket loop uses `select()`. A blocking `recvfrom()` holds the QRTR
  socket lock and blocks the indications sent from the watchdog thread.
- The early IMS "kick" (`--set-power-state-low` and back) made ModemManager
  disable the modem; it was replaced by the service restart above.
- Manual recovery if IMS stays down after a modem crash:
  `/etc/init.d/imsdcm restart`.

### 3.4 ModemManager and netifd

- OpenWrt's dispatcher `/usr/lib/ModemManager/connection.d/10-report-down`
  restarts the WAN interface on **any** bearer disconnect. Restarting the
  interface disconnects every bearer, IMS included, which disconnects again:
  a reconnect loop. `ufi-voice` installs an override in
  `/etc/ModemManager/connection.d/10-report-down` that ignores the `ims`
  bearer and bearers of other interfaces.
- The `ims` bearer is not a netifd interface; nothing routes over it on the
  Linux side. The modem carries SIP/RTP itself.
- The internet bearer (netifd `modem`) and the `ims` bearer are separate
  PDNs. The ims PDN works with a voice-only SIM that has no data service.

## 4. Network-specific configuration

IMS registration depends on the service provider's provisioning. Keep
network-specific APN and proxy settings in local device configuration rather
than in a shared image or source tree. The `ims` APN name used by the modem
is a generic default; do not replace it unless the network requires a
different value. Supplementary call features may also vary by service.

## 5. Calls over VoLTE

- **Session choice:** `ufivoice.main.session 'auto'` = VoLTE session while
  IMS is registered, CS otherwise. Wrong session = silence.
- **Clean uplink to SIP phones:** in `host` mode the DSP's vocproc TX input is
  the unused BT SCO port. Its noise went into VoLTE calls word by word (the
  codec's DTX gates it). `Voice Tx Mute` (host_tx_mute, default 1) mutes it;
  the in-call playback is mixed in after the mute.
- **Incoming calls** need a registered QMI VOICE client (ModemManager is
  one); an AT-only dialer never confirms the call setup.
- **Call waiting** with the same number: ModemManager merged a waiting call
  into the active one by number (patch 100 in the overlay fixes it).
  Linphone answers 486 to a second call from the same address, so the bridge
  offers it as `<number>~2`.
- With IMS unregistered (no LTE, network without VoLTE, PDN down) calls fall
  back to CSFB/3G and still work.

## 6. Modem profile compatibility

Modem firmware generations can use different profile formats and EFS layouts.
A profile or EFS item from another device should not be copied blindly: check
that it matches the modem firmware and intended service, and preserve a
recoverable backup before making device-local changes.

## 7. Safety and privacy

- **Back up EFS before writes**: `modemst1`, `modemst2` (and `fsg`) as raw
  partition images. A bad MCFG activation can crash-loop the modem before QMI
  is up; only a partition restore fixes it.
- Do not activate MBNs from another modem generation.
- Keep the NV 2500 HiMI_OK guard (HiMI firmware) and its DIAG lock; space
  DIAG requests (`sleep 1`, timeouts).
- Never flash modem/firmware partitions casually; IMS needs none of that on
  the UFI003.
- EFS dumps, MCFG state (`mcfg_last_autoselect_iccid`), QMI/IMS logs and
  Asterisk logs contain the IMEI, IMSI, ICCID, phone number, IPv6 interface
  ids and P-CSCF addresses. Keep them on the device or in private storage;
  mask them in anything shared.

## 8. References

- postmarketOS OpenIMSD (`81voltd`, `qcom-imsd`): https://www.openimsd.de/pages/architecture.html
- MCFG MBN format: fenrir-naru/mbn_utils
- ASUS MSM8916 VoLTE MBNs: TheMuppets `proprietary_vendor_asus`,
  `8916_volte_modem/modem_pr/mcfg/configs/mcfg_sw/`
- This repo: `packages/ufi-voice` (`imsdcm`, `volte-setup`, `10-report-down`),
  `packages/diag-logtool`, `msm89xx/patches/870`–`872`,
  [UFI003_VOICE.md](UFI003_VOICE.md), [UFI003_MB_V02.md](UFI003_MB_V02.md)
