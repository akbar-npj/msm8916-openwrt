# Experimental IMS and VoLTE on MSM8916

The optional `qcom-ims` package provides an IMS settings helper and starts
`81voltd`. The daemon handles the modem's QRTR IMS Data requests through
ModemManager. Both the package and its UCI service are opt-in.

On JZ0145 v33 with Beeline Russia (`250-99`), the tested setup registered IMS
and established ordinary LTE Internet at startup. An answered call to 0611
stayed on LTE and produced a 22-second downlink recording. The modem trace
also confirmed an outgoing SMS over IMS. Uplink call audio remains untested.

## Install and verify

Select `qcom-ims` in the OpenWrt Network package menu. This selects
`81voltd`. Build and install the packages on a matching OpenWrt image. Use
an image with the updated ModemManager netifd handler if both IMS and Internet
are required. The handler creates and tracks a dedicated Internet bearer for
each `qcom-soc` interface and preserves the IMS bearer during an Internet
interface restart. The handler uses `dbus-send` from `dbus-utils`, which
`msm89xx-scripts` now installs. Reboot after upgrading from the earlier VoLTE
handler so ModemManager clears the previous bearers and netifd records the
new ones.

Read the current modem settings and carrier profile before enabling the service:

```sh
qcom-ims-settings status
qmicli -p -d /dev/wwan0qmi0 --pdc-list-configs=software
```

Enable startup and check the result. The registration helper retries QRTR
discovery if the modem or IMS data service is still starting.

```sh
uci set qcom-ims.main.enabled='1'
uci commit qcom-ims
/etc/init.d/qcom-ims enable
/etc/init.d/qcom-ims start
qmicli -p -d /dev/wwan0qmi0 --imsa-get-ims-registration-status
qmicli -p -d /dev/wwan0qmi0 --imsa-get-ims-services-status
mmcli -m qcom-soc -K
ip route show default
```

Expect IMSA to report `registered` with the relevant service `available`.
Check that ModemManager shows separate connected IMS and Internet bearers
and that the normal default route uses the Internet bearer. To verify an
individual call or message, check the call's radio and IMS state or trace the
SMS transaction in the modem. IMSA service availability describes readiness.

The helper enables legacy IMS provisioning flags. A value of `-1` in its
status output means the modem omitted that field. To stop the service and
disable startup:

```sh
/etc/init.d/qcom-ims stop
/etc/init.d/qcom-ims disable
uci set qcom-ims.main.enabled='0'
uci commit qcom-ims
```

Modem settings may persist. If an IMS bearer remains after the daemon stops,
identify it by APN and disconnect and delete that specific bearer through
ModemManager.

## IPv4 IMS provisioning on JZ0145

The service uses the active carrier profile and existing WDS settings. The
tested modem was provisioned separately with all three of these settings:

| Component | Tested setting |
| --- | --- |
| Carrier policy | Modified ROW PDC profile ID `6ba2738bf0860cddbae96c1a57126f90232ee1a5`, with IMS address type 1 |
| WDS profile 2 | APN `ims`, IPv4 PDP type, P-CSCF Address Using PCO enabled |
| Host | `qcom-ims` enabled and netifd configured for ordinary Internet through ModemManager |

On this firmware, stock ROW requests IMS address type 2. The modem accepts
the IPv4v6 IMS profile under that policy, but Beeline rejects its activation
with 3GPP cause 50, IPv4 only allowed. The modem rejects an IPv4-only profile
during matching under the same policy. The tested ROW variant changes the IMS
policy byte from `0x40` to `0x20`, which selects address type 1 and lets the
modem use the IPv4 profile.

The IPv4 P-CSCF request uses WDS Modify Profile (`0x0028`), TLV `0x1f`.
With that flag enabled, the LTE NAS capture showed the modem ask for an IPv4
P-CSCF and Beeline return two. SIP registration completed after a `401`
authentication challenge and `200 OK`. With the request disabled, the
modem used the preconfigured ROW proxy and timed out. Carrier files are
specific to the modem firmware and configuration.

### Load and activate the tested profile

Use this procedure on JZ0145 v33 with a Beeline Russia SIM. Install the
current `qcom-ims` package for `qcom-ims-ipv4-pco`. Use a USB or local
management connection because carrier profile activation can interrupt LTE.
If `qcom-ims` is already running, stop it before changing modem profiles.

Install the current `qcom-carrier-autocfg` package from the OpenWrt Network →
WWAN menu. It includes the patched MBN for manual use.
On the router, check the file and record the active software PDC ID and
existing WDS profiles before making changes:

```sh
qmi=/dev/wwan0qmi0
mbn=/usr/share/qcom-carrier-autocfg/mcfg-ipv4/row-ims-ipv4.mbn
sha256sum "$mbn"
qmicli -p -d "$qmi" --pdc-list-configs=software
qmicli -p -d "$qmi" --wds-get-profile-list=3gpp
```

The MBN SHA-256 is
`6784f0b7e5f94da2ebcf1edc8b1a882fd9988fbf9238bf18d7db0e809b70af13`.
It was generated from the 22,360-byte ROW MBN with SHA-256
`2f6447c85cbcfec8d1ed53c53d9ea76477e99e61ffddb602b4ae63acb4725202`
by changing the byte at `0x47b8` and updating the ELF segment SHA-256
at `0x1068` for the byte range `[0x2000, 0x5758)`.

On the tested factory setup only profile 1 was present. If only profile 1
is present, create the IMS profile and confirm that the modem assigns it
index 2. If profile 2 already matches the IMS settings below, reuse it and record its
current P-CSCF setting with `qcom-ims-ipv4-pco "$qmi" status`.
If profile 2 is occupied by a different configuration, stop and review it
before proceeding.

```sh
qmicli -p -d "$qmi" --wds-create-profile=3gpp,name=ims,apn=ims,pdp-type=IP,context-num=2,auth=NONE
qmicli -p -d "$qmi" --wds-get-profile-list=3gpp
```

Set and verify IPv4 P-CSCF discovery on the IMS profile:

```sh
qcom-ims-ipv4-pco "$qmi" enable
```

Load the MBN if its ID is absent from the PDC list. If it is already listed,
skip the load. Activate it if it is inactive, then check the active ID:

```sh
qmicli -p -d "$qmi" --pdc-load-config="$mbn"
qmicli -p -d "$qmi" --pdc-activate-config=software,6ba2738bf0860cddbae96c1a57126f90232ee1a5
qmicli -p -d "$qmi" --pdc-list-configs=software
```

Confirm the patched ID is active and
`qcom-ims-ipv4-pco "$qmi" status` reports `P-CSCF-over-PCO=enabled`.
Then follow [Install and verify](#install-and-verify) to start IMS and check
that LTE Internet and IMS are both up.

### Restore the previous carrier configuration

Stop `qcom-ims` and disable startup as described above. Reactivate the
original software PDC ID you recorded. On the tested unit it was
`0e31571c865178688fd65d806a2ef2e4cb74ebe9`.

```sh
# Replace this example with the original ID recorded on your modem.
original_id=0e31571c865178688fd65d806a2ef2e4cb74ebe9
qmicli -p -d "$qmi" --pdc-activate-config="software,$original_id"
qmicli -p -d "$qmi" --pdc-list-configs=software
```

If you created profile 2 for this procedure, remove it with
`qmicli -p -d "$qmi" --wds-delete-profile=3gpp,2`. If you reused an existing
IMS profile with P-CSCF over PCO disabled, restore that setting with
`qcom-ims-ipv4-pco "$qmi" disable`.

## JZ0145 call audio

Select `kmod-sound-msm8916-voice` and install the matching kernel package.
The JZ0145 device tree enables the QDSP6 voice card. Install `alsa-utils`
to inspect it and record audio. On the tested image, the `msm8916-voice`
card exposed MultiMedia1 as PCM 0 and the hostless CS-Voice link as PCM 2.
Use `arecord -l` and `aplay -l` to confirm the device numbers.

For downlink capture, select the `VoLTE` voice session, route the hostless
voice input and output through the internal SCO mixers, enable
`MultiMedia1 Mixer VOC_REC_DL`, and select `Downlink` for `Voice Record`.
Keep the capture and playback sides of the hostless PCM open while recording
MultiMedia1. The test used 8 kHz, mono, signed 16-bit little-endian audio.
Turn `Voice Record` to `Off` after capture.
