# 221 — VoLTE on the mifib-v12: complete guide

**Date:** 2026-11-13
**Device:** HM-MIFIB-V12 (`mifib-v12`), Qualcomm MSM8916, OpenWrt
**Baseband:** `MPSS.DPM.2.0.2.C1-00178`, Build ID `HM02_MODEM_V1.2`
**Status:** working — IMS registers, VoLTE calls connect, and call audio flows both ways.

This is the practical guide: what was broken, how the fix works, how to build it, how to verify it, and
what to do when it does not come up. The investigation history (every failed attempt, with evidence)
lives in `220_VOLTE_ENABLEMENT_SOP.md`; this document assumes you do not want to read that first.

---

## 1. Status at a glance

| Capability | State | Notes |
|---|---|---|
| IMS registration | **works** | automatic on boot, no shell needed |
| VoLTE call (voice over LTE) | **works** | `type=voice-ip`, no CS fallback |
| Call audio, downlink | **works** | caller's voice on `pcm0` |
| Call audio, uplink | **works** | audio on `pcm1` reaches the far end |
| Voice path starts by itself | **works** | `ufi-voiced` starts it on answer, stops it on hangup |
| Bluetooth headset | **wired, untested** | `hfp-ag` runs; needs a paired headset and its MAC in uci |
| SIP phones via Asterisk | **wired, needs a client** | Asterisk runs on `:5060`; register a softphone as user 100 |

---

## 2. The 60-second version

VoLTE needs four things, in this order:

1. **A WDS profile for the IMS APN that asks for a P-CSCF.** The carrier config file (MCFG) declares
   the `ims` PDN as `Override_Type:API`, so the modem never materialises a profile for it. Without a
   profile that sets `IMCN=1` and `PCSCF-via-PCO=1`, the network delivers no P-CSCF and the modem's IMS
   stack has nowhere to send a SIP REGISTER.
2. **That profile must exist when the modem boots.** The IMS stack reads the profile table **once**,
   early. A profile created afterwards is invisible. So the AP creates it the moment the modem's QMI
   port appears (`/etc/hotplug.d/wwan/20-ims-profile`).
3. **An AP-side IMS data service.** In hybrid IMS mode the modem asks the AP to bring the IMS PDN up
   over QMI service 770. `81voltd` answers that, creates the bearer through ModemManager, and reports
   the address back. Then the modem registers.
4. **An audio path.** The modem's DSP does not have a speaker on this board. `q6voice` exposes the
   in-call audio as PCM taps: the downlink can be captured on `pcm0`, and whatever you write to `pcm1`
   is played into the call. `ufi-voiced` starts that path when a call is answered.

Steps 1–3 give you a call that connects but is silent. Step 4 gives you a phone.

---

## 3. Why it did not work

### 3.1 The carrier config declares the IMS PDN as API-owned

`mcfg_sw.mbn` for this carrier contains a text block:

```
PDN_APN_String:jionet; Override_Type:NONE; Supported_RAT_Priority_List:WWAN, IWLAN;
PDN_APN_String:ims;    Override_Type:API;  Supported_RAT_Priority_List:WWAN, IWLAN;
```

`Override_Type:API` means "the application processor will own this PDN". The modem therefore does not
create a WDS profile for `ims` at boot, and it refuses an AP-initiated `Start Network` on it with
`QMI protocol error (79): PolicyMismatch`.

### 3.2 No profile ⇒ no P-CSCF

The IMS PDN only returns a P-CSCF if the profile asks for one:

| Profile flags | `PCSCF Server Address List` |
|---|---|
| neither | empty (`length = 1, value = 00`) |
| `IMCN=1` + `PCSCF-via-PCO=1` | populated (`02:0C:8B:F0:38:30:82:38:0A` → 56.240.139.12, 10.56.130.48) |

No P-CSCF, no SIP REGISTER, no VoLTE. This is the gate.

### 3.3 The profile has to be there at boot

The modem's IMS stack reads the WDS profile table once, early in its own boot. A profile created later
(even correctly flagged) is ignored — the modem does not re-read it and does not retry. That is why an
`init.d` script at `START=94` is too late: the modem booted at ~10 s.

The fix is a **hotplug hook on the wwan port**: `/dev/wwan0qmi0` appears the moment the modem's QMI
service is up, which is the earliest point the AP can talk to it — and the same point the manual
"restart the modem, create the profile within 2 s" recipe uses.

### 3.4 Registration still needs an AP-side data service

In hybrid IMS mode (`ims_hybrid_enable=1`) the modem does not bring the IMS PDN up itself. It sends a
QMI IMSD request (`0x20`, service 770) to the AP and waits for the address. `81voltd` implements that
service: it creates the bearer through ModemManager, reads the assigned address, and sends the
indication back. Only then does the modem register with the IMS core.

---

## 4. The chain, end to end

```
 carrier MCFG ──> modem boots ──> reads WDS profile table (once)
                                        │
        /dev/wwan0qmi0 appears ─────────┤  hotplug: create+flag the `ims` profile
                                        │
 modem IMS stack ──QMI 770 (IMSD)──> 81voltd ──mmcli──> ModemManager ──> bearer up
        ▲                               │
        └──── indication 0x20 (address) ┘
                                        │
                                  IMSA: registered  ──> SIP REGISTER ──> VoLTE call
                                        │
                          QDSP6 voice session (q6voice)
                                        │
                    ┌───────────────────┴───────────────────┐
              pcm0 capture (VOC_REC_DL)            pcm1 playback (VOICE_PLAYBACK_TX)
                    │                                       │
              voicebridge / ufi-voiced  ────────>  Asterisk / BT / your app
```

---

## 5. What is installed

| Package | Role |
|---|---|
| `81voltd` | QMI service 770 (IMSD) + `ims-profile-setup` + the wwan hotplug hook |
| `ufi-voice` | `ufi-voiced`, `imsdcm`, `voicectl`, `voicebridge`, `hostless`, `hfp-ag`, `volte-setup` |
| `ufi-voice-asterisk` | one-shot Asterisk config (SIP phones 100/101, AudioSocket bridge) |
| `kmod-sound-msm8916-voice` | `q6voice`, `q6mvm`, `q6cvs`, `q6cvp`, `q6afe` ports, the `msm8916-voice` card |
| `alsa-utils` | `amixer`, `aplay`, `arecord` |

The kernel side is three patches in `msm89xx/patches/`:

| Patch | What it does |
|---|---|
| `870-asoc-qcom-msm8916-voice.patch` | the QDSP6 voice stack, BT SCO + record/playback ports, the card |
| `871-bam-dmux-pc-irq-recheck-state-under-lock.patch` | fixes an A2 assertion seen during a voice call on UFI003 |
| `872-arm64-dts-qcom-mifib-v12-enable-voice.patch` | includes the voice dtsi for the mifib-v12 |

There is **no ADSP partition and no extra firmware**: APR rides the modem's existing `smd-edge`, and
the voice services are already in the modem firmware.

---

## 6. Build and flash

```
./build.sh build mifib-v12
```

The image lands in `openwrt/bin/targets/msm89xx/msm8916/…-generic-mifib-v12-squashfs-sysupgrade.bin`.

Before flashing, confirm the packages actually made it into the image:

```
grep -E "ufi-voice|sound-msm8916-voice|alsa-utils" \
  openwrt/bin/targets/msm89xx/msm8916/*.manifest
```

If `kmod-sound-msm8916-voice` is missing, the kernel package scan cache is stale (it does not track
`target/linux/*/modules.mk`). Fix:

```
rm -f openwrt/tmp/info/.packageinfo-kernel_linux \
      openwrt/tmp/.config-package.in openwrt/tmp/.config-target.in openwrt/tmp/.config
```

Flash with a config-preserving `sysupgrade` (no `-n`: that would also wipe the modem firmware).

---

## 7. Bring-up

### 7.1 Normal boot (nothing to do)

On boot, in order:

1. The modem starts; `/dev/wwan0qmi0` appears (~10 s).
2. `20-ims-profile` runs from the wwan hotplug and creates the `ims` profile with
   `IMCN=1` + `PCSCF-via-PCO=1`.
3. `81voltd` starts (`START=96`) and binds QMI service 770.
4. ModemManager brings up the IMS bearer when the modem asks.
5. The modem registers with the IMS core.

Check it:

```
qmicli -d qrtr://0 --imsa-get-ims-registration-status   # Status: 'registered'
mmcli -m 0 | grep -E "state:|registration:"
```

The modem takes ~60 s to reach `registered: home` after a boot. Give it that time before concluding
anything.

### 7.2 Manual bring-up (when registration does not happen)

The script on the unit does the whole sequence:

```
/root/volte_flow.sh
```

What it does, step by step:

```
# 1. restart the modem and create the profile as early as possible
echo restart > /sys/kernel/debug/msm_subsys/modem
#    wait for /dev/wwan0qmi0, then:
qmicli -d /dev/wwan0qmi0 --device-open-proxy \
       --wds-create-profile=3gpp,apn=ims,pdp-type=IPV4V6
#    set PCSCF-via-PCO (0x1f) and IMCN (0x22) on it
qmi-raw /dev/wwan0qmi0 1 0x0028 0x01=0002 0x1f=01 0x22=01

# 2. ModemManager loses its 3GPP view after a modem restart
mmcli -m 0 --disable; sleep 4; mmcli -m 0 --enable; sleep 12

# 3. re-trigger the modem's IMS stack
/etc/init.d/81voltd restart

# 4. poll until registered (3-5 s)
qmi-raw /dev/wwan0qmi0 0x21 0x0021     # TLV 0x10 = 02:00:00:00
```

**Warning:** step 1 restarts the modem. Never do that on a unit you cannot power-cycle — a failed cold
boot leaves the modem offline with no in-band recovery (only an AP reboot brings it back).

---

## 8. Verification

### 8.1 Signalling

```
# modem on the network
mmcli -m 0 | grep -E "state:|registration:|signal"

# IMS registered
qmicli -d qrtr://0 --imsa-get-ims-registration-status

# place a call (numbers are ASCII hex, not BCD!)
QMI_HOLD_SECS=30 qmi-raw /dev/wwan0qmi0 0x09 0x0020 \
    0x01=38343331323235313636 0x02=38343331323235313636

# what the modem thinks is happening
qmi-raw /dev/wwan0qmi0 0x09 0x002f
```

A working call reports `type = 'voice-ip'` and `state = 'conversation'`. `voice-ip` is the proof that
it is VoLTE and not a circuit-switched fallback.

The dialling client must stay allocated for the call's lifetime — that is what `QMI_HOLD_SECS` does.

### 8.2 Audio

```
cat /proc/asound/cards          # 0 [msm8916voice]: msm8916-voice
voicectl status                 # running: mode=host session=volte card=0

# downlink: the caller's voice
arecord -D hw:0,0 -f S16_LE -r 8000 -c 1 -d 10 /tmp/dl.wav

# uplink: play something into the call
aplay -D hw:0,1 /tmp/tone.wav
```

A silent `arecord` with a live call almost always means the wrong voice session — see §9.

---

## 9. The audio path

### 9.1 The card

`qcom,msm8916-qdsp6-sndcard`, model `msm8916-voice`, with three PCMs:

| PCM | Direction | Use |
|---|---|---|
| `pcm0` MultiMedia1 | capture | `VOC_REC_DL` (downlink, what the caller says) or `VOC_REC_UL` |
| `pcm1` MultiMedia2 | playback | `VOICE_PLAYBACK_TX` — audio played here goes **into** the call |
| `pcm2` CS-Voice | both | hostless; opening it starts the DSP voice path |

`voicectl` opens `pcm2` with the `hostless` helper and sets the mixer routes.

### 9.2 The session selector (the trap that cost us an hour)

The DSP has two voice sessions, and the mixer control `Voice Session` chooses between them:

| Call type | Session | Wrong session looks like |
|---|---|---|
| `voice-ip` (VoLTE) | `volte` | call connects, capture is **all zeros** |
| CS (2G/3G) | `cs` | same, the other way round |

`voicectl start host` defaults to **cs**. On a VoLTE call you must say `volte`, or let `ufi-voiced` do
it: its `session=auto` picks `volte` while IMS is registered.

```
voicectl start host volte
voicectl status
voicectl stop
```

### 9.3 Mixer routes (for reference)

| Control | Meaning |
|---|---|
| `INT_BT_SCO_RX Voice Mixer CS-Voice` | downlink → BT SCO port |
| `CS-Voice Capture Mixer INT_BT_SCO_TX` | uplink ← BT SCO port |
| `MultiMedia1 Mixer VOC_REC_DL` | downlink → `pcm0` capture |
| `VOICE_PLAYBACK_TX Audio Mixer MultiMedia2` | `pcm1` playback → uplink |
| `Voice Record` (`Downlink`/`Off`) | enables the record tap |
| `Voice Playback` (`on`/`off`) | enables the in-call playback |
| `Voice Tx Mute` | mutes the DSP mic so only `pcm1` goes out |

---

## 10. Automation: what happens on a call

`ufi-voiced` polls ModemManager every 0.5 s and drives everything:

| Event | Action |
|---|---|
| call becomes `active` | `voicectl start <mode> <session>`; start `voicebridge` |
| call ends | `voicectl stop`, reap `hostless` and `voicebridge` |
| incoming call | ring the SIP phones (AMI) and/or the headset (HFP) |
| call waiting | offer it to the phones, hold/swap through AMI |

Configuration is `/etc/config/ufivoice`:

| Option | Default | Meaning |
|---|---|---|
| `volte` | `1` | run the AP-side IMS data service |
| `mode` | `both` | `bt` \| `asterisk` \| `both` \| `none` — where calls go |
| `session` | `auto` | `cs` \| `volte` \| `auto` |
| `bt_headset` | *(empty)* | MAC of the paired headset |
| `sip_ring` | `PJSIP/100&PJSIP/101` | phones rung for an incoming call |
| `autoanswer` | `0` | answer incoming calls after N s (0 = never) |
| `host_tx_mute` | `1` | mute the DSP mic in host mode |
| `ami_secret` | generated | AMI password (must match `/etc/asterisk/manager.conf`) |

**Routing rule:** a connected headset owns the call; otherwise the host taps are used. The host taps do
not need a SIP phone — the voice path runs and the audio is available on `pcm0`/`pcm1` regardless.

---

## 11. Bluetooth calling

`hfp-ag` registers the Hands-Free Profile Audio Gateway on the BlueZ `Profile1` interface, and the DSP
routes the call to WCNSS's internal SCO link.

1. Pair the headset with the device:

   ```
   bluetoothctl
   > power on
   > scan on
   > pair <MAC>
   > trust <MAC>
   > connect <MAC>
   ```

2. Tell `ufi-voiced` which headset is the call audio device:

   ```
   uci set ufivoice.main.bt_headset='<MAC>'
   uci commit ufivoice
   /etc/init.d/hfp-ag restart
   /etc/init.d/ufi-voiced restart
   ```

3. Place or receive a call. With the headset connected the route becomes `bt`, `voicectl` sets the SCO
   mixers, and `hfp-ag` drives the HFP state machine (ring, answer, hangup, volume).

`bt_reconnect_max` (default 900 s) pages a disconnected headset with a backoff. Each page briefly
blinds the 2.4 GHz station, so it does not page during a call.

---

## 12. Asterisk: SIP phones as extensions

`ufi-voice-asterisk` writes the config once and enables the service. Asterisk listens on **UDP 5060**
and the AMI on 127.0.0.1:5038.

| File | Purpose |
|---|---|
| `/etc/asterisk/pjsip.conf` | transport + endpoints 100/101 |
| `/etc/asterisk/extensions.conf` | `[ufi-phones]` (dial 100/101, everything else goes out via LTE) and `[ufi-incoming]` |
| `/etc/asterisk/ufi-voice-phones.txt` | the generated SIP usernames/passwords — read them from here |

### 12.1 Register a softphone

Any SIP client works (Linphone, Zoiper, a desk phone). Use:

| Setting | Value |
|---|---|
| Server / domain | the device's LAN address (`192.168.8.1` on the bench) |
| Port | 5060, UDP |
| Username | `100` (or `101`) |
| Password | from `/etc/asterisk/ufi-voice-phones.txt` |
| Transport | UDP |

### 12.2 What works once a phone is registered

- **Incoming mobile call** → the phone rings; answering it starts the voice path and bridges the audio.
- **Outgoing from the phone** → dial any number; the dialplan calls `ufi-voice-dial`, which asks
  `ufi-voiced` to create the mobile call, then bridges with AudioSocket.
- **Phone to phone** → dial 100 or 101.
- **Call waiting / hold** → the modem supports concurrent calls; `ufi-voiced` offers the second call to
  the phones and uses AMI hold/swap.

Audio never touches the host CPU as a "sound card" — `voicebridge` moves 8 kHz PCM between the DSP taps
and the AudioSocket.

---

## 13. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `IMSA: not-registered` right after boot | checked too early | the modem needs ~60 s to reach `registered: home` |
| `not-registered` and the profile is missing | hotplug did not run | check `logread \| grep ims-profile`; run `/root/volte_flow.sh` |
| `not-registered` with the profile present | ModemManager lost its 3GPP view (common after a modem restart) | `mmcli -m 0 --disable; sleep 4; mmcli -m 0 --enable` |
| Dial returns SUCCESS but nothing rings | number sent as BCD | the VOICE number TLVs are **ASCII** (`0x02=3834…`), not packed BCD |
| Call drops immediately after dialling | the QMI client was released | keep it alive: `QMI_HOLD_SECS=30` |
| Call connects, no audio, capture all zeros | wrong voice session | `voicectl start host volte` on a VoLTE call |
| `aplay -D hw:0,1` → `Resource busy` | `voicebridge` owns `pcm1` | inject audio through Asterisk/AudioSocket, or stop the bridge first |
| `voicectl: the voice path did not start` | no active call, or the DSP session is already held | start it while a call is up; `voicectl stop` first |
| No sound card at all | kernel package missing from the image | check the manifest; clear the stale kernel package scan cache (§6) |
| IMS bearer appears and disappears every few seconds | 81voltd and `imsdcm` fighting over QMI service 770 | `/etc/init.d/imsdcm disable` — run one IMS data service, not two |
| Two calls to the same number | a second dial while one is active holds the first (normal call waiting) | not a bug; hang up the held call or use hold/swap |

---

## 14. Known limits

1. **The profile is RAM-only.** A modem firmware reflash or an NV reset loses it; the hotplug hook
   recreates it on the next boot, but a device that has never had one needs the manual flow once.
2. **The first boot after a flash is slower to register** — the modem re-provisions its profile table
   from the MCFG and only then accepts the AP's profile.
3. **One IMS data service.** `81voltd` and `imsdcm` both implement QMI 770. `imsdcm` is the newer
   implementation (it also handles the bearer-at-boot and stale-id cases); switching to it is a
   deliberate change, not something to run side by side.
4. **Multi-call works but has no policy.** The modem will happily hold a call and dial another; nothing
   currently decides which one gets the audio.
5. **Bluetooth and Asterisk are wired but not yet exercised end to end** on this unit.

---

## 15. Reference

### 15.1 QMI messages used

| Service | Message | Meaning |
|---|---|---|
| WDS `0x01` | `0x0027` / `0x0028` | Create / Modify Profile (`0x01` = `{type, index}`, type 0 = 3GPP; `0x1f` PCSCF-PCO, `0x22` IMCN) |
| WDS `0x01` | `0x0020` / `0x002d` | Start Network / Get Current Settings |
| IMSA `0x21` | `0x0020` / `0x0021` | Get IMS Services Enabled / Registration Status (`0x10` = `02` registered) |
| VOICE `0x09` | `0x0020` | Dial Call (`0x01` calling, `0x02` called — ASCII strings) |
| VOICE `0x09` | `0x0021` / `0x0022` / `0x002f` | End / Answer / Get All Call Info |
| IMSD `0x302` | `0x20` / `0x23` | Start Connection request / connection-changed indication |

### 15.2 Files and services

| Path | What it is |
|---|---|
| `/etc/hotplug.d/wwan/20-ims-profile` | creates the `ims` profile when the QMI port appears |
| `/usr/sbin/ims-profile-setup` | the profile creation + flagging helper |
| `/etc/init.d/ims-profile` | fallback (START=94), idempotent |
| `/etc/init.d/81voltd` | the IMS data service (START=96) |
| `/etc/init.d/ufi-voiced` | the call manager (START=99) |
| `/etc/config/ufivoice` | automation settings |
| `/etc/asterisk/` | Asterisk config, including `ufi-voice-phones.txt` |
| `/proc/asound/cards` | the voice card |
| `/root/volte_flow.sh` | the manual bring-up |

### 15.3 Logs worth grepping

```
logread | grep -i ims-profile        # profile creation at boot
logread | grep 81voltd               # IMSD service, bearer create/remove
logread | grep ufi-voiced            # call state machine, voice path start/stop
logread | grep voicectl              # "voice path started (host, volte)"
dmesg | grep -iE "q6voice|asoc|apr"  # DSP plumbing
```

---

## 16. Changelog

| Date | Change |
|---|---|
| 2026-11-13 | First version. Written after the first fully working build: IMS on boot, VoLTE call, two-way audio, automatic voice path. |
