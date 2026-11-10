# UFI003 voice: VoLTE, Bluetooth headset, local SIP phones

The UFI003 image carries a call stack built on the modem's own IMS client:

| Part | What it does |
| --- | --- |
| `kmod-sound-msm8916-voice` | QDSP6 over APR on the modem edge, q6voice (MVM/CVS/CVP passive sessions), internal BT SCO AFE ports, voice record taps and in-call playback (`870-asoc-qcom-msm8916-voice.patch`, `msm8916-ufi-voice.dtsi`) |
| `imsdcm` | AP side of the modem's IMS data connection service (QMI service 770 over QRTR). The IMS stack runs in hybrid mode and asks the AP to bring up the `ims` PDN; the bearer is made through ModemManager |
| `volte-setup` | Checks / writes the modem EFS items VoLTE needs |
| `hfp-ag` | Minimal HFP audio gateway on BlueZ (Profile1 API): call control AT commands with a headset, SCO link during calls |
| `voicectl` | Starts / stops the DSP voice path: `bt` (internal BT SCO) or `host` (record tap on pcm0, in-call playback on pcm1) |
| `ufi-voiced` | Call manager: modem calls (ModemManager) to the headset and/or Asterisk |
| `ufi-voice-asterisk` | Asterisk with PJSIP phones 100/101 and AudioSocket to `ufi-voiced` |

Configuration: `/etc/config/ufivoice` (comments in the file).

## Modem configuration (EFS)

The generic ROW MCFG referenced by `volte-setup` must be active and contain
IMS settings before applying these EFS values.

| EFS item | Value |
| --- | --- |
| `/nv/item_files/ims/IMS_enable` | `01` |
| `/nv/item_files/ims/ims_operation_mode` | `02` |
| `/nv/item_files/ims/ims_hybrid_enable` | `01` (the AP provides the IMS PDN) |
| `/nv/item_files/ims/qp_ims_reg_config` | APN `ims` |
| `/nv/item_files/modem/mmode/voice_domain_pref` | `03`, exactly one byte. A 2-byte item (left by some MCFGs) registers IMS for SMS only |

`volte-setup status` shows them, `volte-setup apply --restart` writes the
missing ones, forces an EFS sync over DIAG (writes are otherwise lost on a
reboot within ~15 min) and restarts the modem.

Check: `qmicli -d qrtr://0 --imsa-get-ims-registration-status` and
`--imsa-get-ims-services-status` (Voice service `available`).

Background, the IMS data connection protocol and troubleshooting: [IMS_VOLTE.md](IMS_VOLTE.md).

## Notes on the IMS data connection

* The modem only asks for the PDN (request 0x20) after LTE attach; `imsdcm`
  waits for the attach and for the WAN interface (`wan_iface`).
* A boot-time modem configuration service deletes every bearer while the WAN comes up at boot.
  An IMS bearer made before that disappears under the modem, which then stops
  asking. `imsdcm` therefore starts after the WAN and reports a vanished
  bearer as down.
* After a modem restart (SSR) the modem asks for the PDN while ModemManager
  has no modem yet, gets an error and stops asking. `imsdcm` exits when the
  ModemManager modem object changes; procd starts a new instance, which waits
  for the attach, publishes the service again and reports the old connections
  down. A failed connect also forgets its bearer, so the next request makes a
  new one.
* As a last resort `imsdcm` restarts the same way when IMS has neither a
  connection nor a registration for `ims_kick_after` seconds; the limit
  doubles (up to 1 h) while that does not help, e.g. on a network without
  VoLTE.
* OpenWrt's ModemManager dispatcher script `10-report-down` restarts the WAN
  interface on any bearer disconnect, which also drops the IMS bearer: a
  reconnect loop. The package installs an override in
  `/etc/ModemManager/connection.d/` that ignores the `ims` bearer and other
  interfaces.
* The modem's stop request carries a handle (`local=1`) that does not match
  the one the AP gave; it must not be mapped to the active connection.
* A connection that went away (stop request, vanished bearer) is dropped from
  `imsdcm`'s table at once. Before, a stale entry made the watchdog believe
  IMS still had its PDN: no re-attach, IMS stayed down and calls fell back
  to 3G.

## Call control with mmcli

```sh
# list modems
mmcli -L
# list calls on a modem
mmcli -m any --voice-list-calls
# create an outgoing call, then start it using the returned call path
mmcli -m any --voice-create-call="number=<number>"
mmcli -o <call-path> --start
# accept an incoming call
mmcli -o <call-path> --accept
# hang up
mmcli -o <call-path> --hangup
# send DTMF
mmcli -o <call-path> --send-dtmf=123
# show call details and state
mmcli -o <call-path>
```

`ufi-voiced` polls ModemManager about every 0.5 seconds. When a tracked call becomes `active`, it starts or reroutes the selected audio path; calls placed or answered with `mmcli` follow the same call-state path.

Future work: starting audio before answer in response to ModemManager `CallAdded`/`StateChanged` events instead of polling could reduce the late-audio gap; this is not implemented.

## Proposals (not implemented)

The current branch already includes q6voice and internal BT SCO through patch 870, and documents working two-way call audio over Bluetooth and SIP. The older fork notes that those features were missing do not describe this branch.

- **LuCI dialer:** A LuCI view and rpcd/ucode backend could expose call list/status, dial, answer, hold, and DTMF controls. `ufi-voiced` already manages ModemManager calls and the Bluetooth/Asterisk routes, but this branch has no LuCI call-control interface or RPC backend.
- **USB UAC2 audio:** A USB audio gadget could expose call capture/playback to a connected PC. That needs a UAC2 gadget configuration and a bridge between gadget PCM and the DSP call PCMs; neither is implemented here, and `CONFIG_USB_CONFIGFS_F_UAC2` is disabled.
- **Codec-pin audio:** Direct audio over codec ports such as `PRI_MI2S` or `TERT_MI2S` could be useful on a board that exposes those pins. Patch 870 adds no codec-pin routes; it uses the internal SCO and DSP record/playback routes.
- **File record/playback:** WAV recording or playback from the call taps is not implemented. `voicectl` configures the DSP routes and `hostless.c` keeps the hostless PCM open; `voicebridge` forwards call audio to Asterisk over AudioSocket rather than to files.

## Audio paths

`voicectl start bt|host [cs|volte]` / `voicectl stop` / `voicectl status`.
Mixer controls (card `msm8916voice`):

* `INT_BT_SCO_RX Voice Mixer CS-Voice`, `CS-Voice Capture Mixer INT_BT_SCO_TX`:
  voice path on the internal BT SCO ports (WCNSS)
* `MultiMedia1 Mixer VOC_REC_DL` / `VOC_REC_UL`: record taps to pcm0
* `VOICE_PLAYBACK_TX Audio Mixer MultiMedia2`: pcm1 into the uplink
* `Voice Session` (CS / VoLTE), `Voice Record` (Off / Downlink / Uplink / Both),
  `Voice Playback`

* `Voice Tx Mute`: mutes the vocproc (mic) input of the uplink. In `host`
  mode the vocproc TX is the unused BT SCO port; its noise went into VoLTE
  calls word by word (the codec's DTX gates it), so `voicectl start host`
  sets it to `host_tx_mute` (default 1). Playback on pcm1 is mixed in after
  the mute and is not affected.

pcm2 (CS-Voice) is hostless; `voicectl` keeps it open with `hostless`.
`hostless` exits when the card disconnects (modem restart, SSR) and
`voicectl stop` works without a card, so a modem crash during a call does
not leave the card held and the restart stuck.

The session has to match the call: a VoLTE call has audio only on the VoLTE
session (the CS session records silence), a CS call only on the CS one.
`ufi-voiced` picks it with `session 'auto'`: VoLTE while IMS is registered,
CS otherwise.

## Bluetooth headset

Pair once (`bluetoothctl`: `scan on`, `pair <mac>`, `trust <mac>`), then
`uci set ufivoice.main.bt_headset=<mac>; uci commit ufivoice;
/etc/init.d/ufi-voiced restart`. With `bt_headset` empty the first paired
device is used. The pairing keys are in `/etc/bluetooth/keys` and survive a
sysupgrade. While the headset is not connected (off at boot, out of range)
`ufi-voiced` pages it again, first after a minute, then backing off to
`bt_reconnect_max` (default 900 s; 0 = never page, the headset connects by
itself when it is switched on), and never during a call. Bluetooth and the
Wi-Fi station share the WCN36xx 2.4 GHz radio: a page keeps it busy for
seconds and the station loses the AP's beacons and reconnects, so paging
every minute dropped the Wi-Fi uplink every minute. Answer, hang up, dial
and DTMF from the headset go to the modem; the audio uses the DSP internal
SCO port.

## SIP phones (Asterisk)

The first boot writes `pjsip.conf`, `extensions.conf` and `manager.conf`
(originals kept as `*.orig`); the phone passwords are in
`/etc/asterisk/ufi-voice-phones.txt`. Register phones as `100` / `101` at the
stick (UDP 5060). Incoming mobile calls ring `sip_ring`; any other number
dialled from a phone goes out through the modem.

Call audio goes through `voicebridge` (C): the in-call record (pcm0) and
playback (pcm1) PCMs at 48 kHz with 10 ms periods, resampled to the 8 kHz
AudioSocket stream, and a 40 ms jitter buffer (`jitter_ms`) for the phones'
audio. The playback buffer is kept full (silence fills gaps, a backlog is
dropped), so the delay stays around 60-80 ms and cannot grow. The SIP leg is
G.711 (8 kHz), so a wideband PCM rate brings nothing.

Call waiting and hold: a second incoming mobile call rings the phones while
they are in a call; answering it holds the first (hold-and-accept). Hold,
resume and swap on the phone (AMI `Hold` / `Unhold`) switch the mobile calls
the same way, and the bridge follows the active one. Hanging up one call
keeps the other.

Linphone answers `486 Busy here` to a call from an address it already has a
call with (duplicate guard). A waiting call from the same number as the
current one is therefore offered as `<number>~2` (display name: the number);
`ufi-voice-dial` strips the suffix when that entry is called back.

The first-boot script also creates the `asterisk` user when a sysupgrade kept
an older `/etc/passwd`. A sysupgrade keeps the configuration and the
passwords: the script leaves a `pjsip.conf` it wrote before alone.

## Fixes outside the package

* `msm89xx/patches/870`: the q6voice ASoC driver, including `Voice Tx Mute`
  (`VSS_IVOLUME_CMD_MUTE_V2` on the CVP session, TX direction).
* `msm89xx/patches/871`: bam-dmux power-collapse IRQ re-checks the state under
  the lock. Two edges racing could ack the A2 twice; the modem then asserted
  (SSR) right after a waiting call.
* ModemManager (`openwrt-overlay/.../modemmanager/patches/100`): a waiting
  call from the same number as the active one was matched to the active call
  by number and merged into it (the waiting call never appeared and the first
  call looked ended). Calls with different indexes no longer match by number.

Development notes: do not open the PCMs or reload audio modules during a call.
Do not `rmmod` APR clients (`q6cvp`, `q6cvs`, `q6mvm`, ...): their APR service
entry is lost and the service gets no replies until a reboot. To swap ASoC
modules, unbind the card first
(`echo 7702000.sound > /sys/bus/platform/drivers/qcom-apq8016-sbc/unbind`).

## Status

Verified on the UFI003: VoLTE registration, MO and MT VoLTE calls (SIP log),
sound card probe, Asterisk endpoints / AMI / AudioSocket loaded, and call
audio both ways on a VoLTE call with the VoLTE session: a 1 kHz beep played
into pcm1 came back in the pcm0 downlink record as the far phone's echo
(0.5 s on/off, RMS ~55-135 against ~5-25 in the gaps). The CS session
recorded silence on the same call.

Bluetooth headset (Poly V4320): pairing, HFP service level connection,
ringing, answering from the headset, eSCO link up for the call with the
voice path on the VoLTE session, SCO closed and the path stopped on hang-up.

Calls through the headset and through SIP phones have been used with audio
both ways. After a sysupgrade (settings kept) and after a power cut the stick
comes back on its own: IMS registered, Asterisk up, headset reconnected.

Also used: CS (3G) calls with audio both ways; VoLTE calls through a SIP
phone with clean uplink (`Voice Tx Mute`) and no audible delay; call waiting
from a second number with hold, swap and hang-up of either call.

Known issue: `voicectl stop` logs `q6voice: failed to stop playback: -5`
(CVS 0x11239 returns error 1 after the call ended); harmless.

## Power and heat observations on HMUF02-V05 (not UFI003)

Supply sag and heat are worth checking on MSM8916 sticks during calls with the modem and audio codec active. The measurements below are specific to an uncooled HMUF02-V05 during 3G calls; they are not UFI003 measurements.

- On a 0.55 A supply the stick reset about 50 s into a call (PMIC power-on reason SMPL, power-off reason UVLO; no kernel panic). On a stronger supply, calls of 4, 4, and 10 minutes ran without a reset, but the input still sagged:

  | Rail | Idle | Call (10 min) |
  |---|---|---|
  | `usb_in` | ~4.9–5.0 V | avg 4.82 V, min 4.45 V; ~250 mV dips every few 100 ms (TX bursts) |
  | `vph_pwr` | 3.45 V | min 3.43 V |

- A dip raised the PMIC USB-in-valid interrupt and USB re-enumerated, dropping the USB gadget link. The test notes recommend a short, thick cable and supply margin; capping the CPU at 998 MHz added some more margin.
- On this uncooled HMUF02-V05, the modem sensor (`tsens0`) and CPU sensors settled at 70–75 °C with the CPU quiet and reached 90–95 °C during CPU load (for example, right after boot).
