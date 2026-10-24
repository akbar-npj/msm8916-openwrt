# UFI003 MB V02

This page records the OpenWrt profile and hardware validation for the UFI003
MB V02 MSM8916 modem stick. The profile uses a separate board DTS and follows
the UFI001B feature/configuration baseline. The profile has been validated on
the physical UFI003 MB V02.

## Build identity and storage

- Build alias: `ufi003`
- OpenWrt device profile: `generic-ufi003-mb-v02`
- DTS: `msm8916-generic-ufi003_mb_v02`
- `SUPPORTED_DEVICES`: `ufi003,mb-v02`
- eMMC: 7,634,944 sectors × 512 bytes
- GPT total sectors: 7,634,944

The extracted stock DTS reports a generic MSM8916 MTP model/compatible at its
root. Its live GPIO labels and values match the UFI003 stock GPIO dump; the
OpenWrt board DTS therefore uses a UFI003-specific model and compatible string.

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
ramoops reservation inside System RAM, adjacent to the reserved region beginning
at `0x8dc00000`. The kernel log confirms ramoops and pstore registration.

## Profile feature selection

The profile follows the UFI001B baseline:

- failsafe, using UFI003's active-high GPIO37;
- Bluetooth;
- WPA3-SAE and multi-SSID;
- CPU CPR and the 800 MHz cap.

It leaves the `a2_pin` modem hold and `qcom-time-daemon` outside the UFI003
profile; the Wi-Fi/PMIC cooling maps come from the shared `msm8916-ufi.dtsi`
(patch 861) and are included. These optional board-specific
features are separate from the validated button, LED, and SIM behavior.

The current UFI common DTS enables the `pm8916_usbin` USB extcon; the PM8916
charger node is disabled by default. No battery/BMS override is included in
this profile. Add one only if a physical UFI003 battery is confirmed.

## Build and hardware validation

```sh
./build.sh list
./build.sh build ufi003
```

The UFI003 MB V02 has been verified with this profile on hardware. The board
boots, the active-high/pull-down button responds correctly, the LEDs use the
documented roles, and the SIM operates as expected.
