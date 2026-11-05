# UFI003 MB V02 — board profile and stock device-tree reference

This page documents the OpenWrt profile and hardware validation for the
UFI003 MB V02 MSM8916 modem stick, and records the corresponding stock
Qualcomm device-tree entry and its provenance.

## OpenWrt profile

- Build alias: `ufi003`
- OpenWrt device profile: `generic-ufi003-mb-v02`
- DTS: `msm8916-generic-ufi003_mb_v02`
- `SUPPORTED_DEVICES`: `ufi003,mb-v02`
- eMMC: 7,634,944 sectors × 512 bytes
- GPT total sectors: 7,634,944

The profile uses a separate board DTS and follows the UFI001B feature and
configuration baseline. It has been validated on the physical UFI003 MB V02.

## Stock device-tree identity and match

The stock root reports a generic MSM8916 MTP model/compatible, not the
UFI003 product name. Entry 02 identifies itself as **Qualcomm Technologies,
Inc. MSM 8916 MTP**, with `qcom,board-id = <0x08 0x00>`. The extracted stock
DTS reports the same generic root identity. Its live GPIO labels and values
match the UFI003 stock GPIO dump; the OpenWrt board DTS therefore uses a
UFI003-specific model and compatible string.

Entry 02 matches the SMEM board identity read from the running UFI003 stick
on 2026-11-05: soc_id 206, revision 1.0, hardware_platform 8, subtype 0,
platform_version 65536, PM8916. Among the 33 extracted QCDT entries it is the
only match for MSM8916/soc 206 and board-id <0x08 0x00>; stock LK selects a
DTB using this match. There is still no captured stock
`/proc/device-tree` dump.

## Stock QCDT files and entries

The directory keeps the raw DTB and decompiled DTS for entry 02. Indexes in
the table use the extracted filenames `dtb00` through `dtb32`.

| File | Description |
|---|---|
| `dtb02.dtb` | Raw entry 02 from the 33-entry QCDT table extracted from the stock boot image |
| `dtb02.dts` | Decompiled source for that raw DTB |

| Index | Model | Board ID |
|---:|---|---|
| 00 | MSM 8916 CDP | `0x01/0x00` |
| 01 | MSM 8916 SMB1360 CDP | `0x01/0x01` |
| 02 | MSM 8916 MTP | `0x08/0x00` |
| 03 | MSM 8916 SMB1360 MTP | `0x08/0x01` |
| 04 | MSM 8916 512MB MTP | `0x08/0x100` |
| 05 | MSM 8916 512MB SMB1360 MTP | `0x08/0x101` |
| 06 | MSM8916 RUMI | `0x0f/0x00` |
| 07 | MSM8916 Simulator | `0x10/0x00` |
| 08 | MSM 8916 QRD SKUH | multiple QRD IDs |
| 09 | MSM 8916 QRD SKUI | multiple QRD IDs |
| 10 | MSM 8916 512MB QRD SKUH | multiple QRD IDs |
| 11 | MSM 8916 512MB QRD SKUI | multiple QRD IDs |
| 12 | MSM 8916 QRD SKUHF | multiple QRD IDs |
| 13 | MSM 8916 QRD SKUIC | `0x11010b/0x105` |
| 14 | MSM 8916 QRD SKUID | multiple QRD IDs |
| 15 | MSM 8916 512MB QRD SKUID | multiple QRD IDs |
| 16 | MSM 8936 CDP | `0x01/0x00` |
| 17 | MSM 8936 SMB1360 CDP | `0x01/0x01` |
| 18 | MSM 8936 SMB1360 MTP | `0x08/0x00` |
| 19 | MSM 8936 MTP | `0x08/0x01` |
| 20 | MSM 8936 WCD9306 MTP | `0x08/0x02` |
| 21 | MSM8939 QRD SKUK | `0x1000b/0x05` |
| 22 | MSM 8939 CDP | `0x01/0x00` |
| 23 | MSM 8939 SMB1360 CDP | `0x01/0x01` |
| 24 | MSM 8939 SMB1360 MTP | `0x08/0x00` |
| 25 | MSM 8939 MTP | `0x08/0x01` |
| 26 | MSM 8939 WCD9306 MTP | `0x08/0x02` |
| 27 | MSM8939 RUMI | `0x0f/0x00` |
| 28 | MSM8939 Simulator | `0x10/0x00` |
| 29 | MSM8939 QRD SKUK | multiple QRD IDs |
| 30 | APQ 8016 CDP | `0x01/0x00` |
| 31 | APQ 8016 MTP | `0x08/0x00` |
| 32 | APQ 8016 SMB1360 MTP | `0x08/0x01` |

## GPIO states

| TLMM offset | Stock label | Initial state | OpenWrt role |
|---:|---|---|---|
| 0 | `ESIM_SEL` | output low | SIM selector GPIO |
| 1 | `SIM_EN` | output low | SIM enable GPIO |
| 2 | `SIM_SEL` | output high (physical SIM selected) | SIM selector GPIO |
| 3 | `ESIM2_SEL` | output low | eSIM selector GPIO |
| 20 | `WIFI_S` | output low | `blue:wlan` |
| 21 | `4G_L2` | output low | `green:wan` |
| 22 | `4G_S` | output high | `red:power` |
| 37 | `key_freset` | input, active-high, pull-down | Restart / failsafe button |
| 68 | `4G_L3` | output low | Auxiliary LTE GPIO |

The stock DTS declares GPIO37 as an active-high key and applies a pull-down;
the live GPIO dump showed it idle low. The button polarity and response, LED
behavior, and SIM operation have been verified on the physical board.

## Memory reservation

The captured OpenWrt memory map places `0x8db00000–0x8dbfffff` as a 1 MiB
ramoops reservation inside System RAM, adjacent to the reserved region
beginning at `0x8dc00000`. The kernel log confirms ramoops and pstore
registration.

## Profile feature selection

The profile follows the UFI001B baseline:

- failsafe, using UFI003’s active-high GPIO37;
- Bluetooth;
- WPA3-SAE and multi-SSID;
- CPU CPR and the 800 MHz cap.

It leaves the `a2_pin` modem hold and `qcom-time-daemon` outside the UFI003
profile; the Wi-Fi/PMIC cooling maps come from the shared `msm8916-ufi.dtsi`
(patch 861) and are included. These optional board-specific features are
separate from the validated button, LED, and SIM behavior.

The UFI common DTS enables the `pm8916_usbin` USB extcon; the PM8916 charger
node is disabled by default. No battery/BMS override is included in this
profile. Add one only if a physical UFI003 battery is confirmed.

## Build and hardware validation

```sh
./build.sh list
./build.sh build ufi003
```

The UFI003 MB V02 has been verified with this profile on hardware. The board
boots, the active-high/pull-down button responds correctly, the LEDs use the
documented roles, and the SIM operates as expected.

## OpenWrt DTS comparison

The OpenWrt board DTS is added by
[`msm89xx/patches/852-arm64-dts-qcom-add-msm8916-generic-ufi003.patch`](../../msm89xx/patches/852-arm64-dts-qcom-add-msm8916-generic-ufi003.patch);
its upstream path is
`arch/arm64/boot/dts/qcom/msm8916-generic-ufi003_mb_v02.dts`.

## Provenance

The raw DTB and decompiled DTS came from the stock boot-image QCDT extraction
on the old build host. The raw DTB is authoritative; decompilation can vary
between dtc versions.
