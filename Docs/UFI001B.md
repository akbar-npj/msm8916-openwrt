# UFI001B

This page records the OpenWrt profile and hardware validation for the
**UFI001B** MSM8916 modem stick. It is the *reference* board the other profiles
are baselined against, but its modem is a different revision (V2.0) that does
**not** need — and does not get — the A2 power-rail hold.

## Build identity and storage

- Build alias: `ufi001b`
- OpenWrt device profile: `generic-ufi001b`
- DTS: `msm8916-generic-ufi001b`
- `SUPPORTED_DEVICES`: `ufi001b,250605v0s`
- eMMC: 7,569,408 sectors × 512 bytes (4 GB class)
- GPT total sectors: 7,569,408
- Recovery partition: **no** (15-partition layout)
- Ramoops: yes (`ramoops@8db00000`, 1 MiB)

## LEDs

| GPIO | Function | Note |
| ---: | :------- | :--- |
| 22 | `power` (red) | default **on** — modem/subsystem health |
| 21 | `wan` (green) | 4G / internet activity |
| 20 | `wlan` (blue) | Wi-Fi AP activity |

## Reset / failsafe button

- GPIO **37**, **active-high, pull-down** (same wiring as HMUF02-V5).
- Short press in first 7 s → failsafe; 5 s hold → factory reset.
- Verified on hardware? **Not yet** — declared active-high/pull-down but
  unconfirmed. Serial `f` key is the fallback.

## SIM

The board patch defines SIM routing hogs:

| GPIO | Line | State |
| ---: | :--- | :--- |
| 1 | `sim_en` | low |
| 2 | `sim_sel` | high (physical SIM selected) |
| 68 | (aux LTE GPIO) | low |

Physical SIM hot-swap (`sim_hotplug`) is **untested** — no `sim-presence`
node is exposed. See [SIM Hot-Swap](../SIM_HOT_SWAP.md).

## Profile feature selection

Follows the common baseline:

- failsafe (button, unverified)
- Bluetooth
- WPA3-SAE and multi-SSID
- CPU CPR and the 800 MHz cap

Deliberately **no A2 power-rail hold**: its modem is `MPSS.DPM.2.0.2` (V2.0),
which does not manifest the `a2_power.c:1189` quiesce wedge, so it keeps the
normal runtime-suspend path. Also leaves out the HMUF02-V5 thermal maps,
`qcom-time-daemon`, and the recovery partition.

## Notes

The UFI001B shares the RF/transceiver layout with the stock MSM8916 MTP; unlike
the HMU05/HMUF02 line it is **not** an RF transplant candidate. See
[Modem Stability](Modem-Stability) in the wiki.
