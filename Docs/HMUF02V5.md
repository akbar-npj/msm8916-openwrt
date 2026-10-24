# HMUF02-V5

This page records the OpenWrt profile and hardware validation for the
**HMUF02-V5** MSM8916 modem stick. It is the **reference / lead board** for this
project — the unit under test and the one every recovery and stability feature
has been verified on. Everything that is optional on the other boards is enabled
here.

## Build identity and storage

- Build alias: `hmuf02v5`
- OpenWrt device profile: `generic-hmuf02v5`
- DTS: `msm8916-generic-hmuf02v5`
- `SUPPORTED_DEVICES`: `hmuf02v5,250605v0s`
- eMMC: 7,634,944 sectors × 512 bytes
- GPT total sectors: 7,634,944
- Recovery partition: **yes** (17-partition layout, `misc` + `recovery`) — see
  [Recovery](../RECOVERY/README.md)
- Ramoops: yes (`ramoops@8db00000`, 1 MiB)

## LEDs

| GPIO | Function | Note |
| ---: | :------- | :--- |
| 71 | `wlan` (blue) | Wi-Fi AP activity |
| 72 | `power` (red) | default **on** — modem/subsystem health |
| 73 | `wan` (green) | 4G / internet activity |

## Reset / failsafe button

- GPIO **37**, **active-high, pull-down** (idle low, press = high).
- A short press inside the first 7 s → **failsafe** (verified live 2026-10-10);
  a 5 s hold → **factory reset**.
- This is the only board whose failsafe button flow is confirmed working. The
  key is that the polarity is active-high/pull-down — the opposite declaration
  makes the button completely inert. See [Failsafe](../Failsafe/README.md).

## SIM

HMUF02-V5 is the **only** board that exposes a `sim-presence` / `sim_hotplug`
node (TLMM **GPIO 114**, active-high). Physical SIM hot-swap is **implemented
and live-verified** here (~15 s end to end). See
[SIM Hot-Swap](../SIM_HOT_SWAP.md).

## Profile feature selection (the full set)

This board enables everything the project ships:

- failsafe — **verified**
- SIM hot-swap — **verified** (the `sim_hotplug` service is enabled 1 on every
  board but only meaningful where the line exists)
- **A2 power-rail hold** (patches 848 + 849) — modem `HIMI_U01_MODEM_V1.0`, the
  same HiMI baseband exposed to the `a2_power.c:1189` quiesce wedge
- **`qcom-time-daemon`** — HMUF02-V5-**only**; anchors the modem's ATS state
  (its init is a no-op on every other board)
- **Wi-Fi + PMIC thermal mitigation** (patch 861) — the maps live in the shared
  `msm8916-ufi.dtsi`, so every board gets them
- recovery partition
- Bluetooth, WPA3-SAE, multi-SSID
- CPU CPR and the 800 MHz cap
- the Himi-OK modem-OK gate

## Notes

This is the board the whole ~900 s modem-fatal investigation was solved on; the
fix is AP-side (Himi-OK gate + A2 hold), not a baseband patch. The modem firmware
itself is byte-identical to stock Android. See
[Modem Stability](Modem-Stability) in the wiki.
