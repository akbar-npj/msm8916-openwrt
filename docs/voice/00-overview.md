# MSM8916 Voice Support Overview

## Purpose

The goal of this project is to enable native voice calling on Qualcomm MSM8916-based LTE devices running OpenWrt.

Unlike normal mobile phones, OpenWrt currently uses the modem primarily for packet data (LTE internet). Although the modem firmware exposes the Qualcomm Voice (QMI Voice) service, there is currently no complete software stack to route call audio between Linux and the modem.

This project documents the investigation and implementation required to enable voice calling.

---

## Target Hardware

Current target:

- UFI001B LTE USB modem
- Qualcomm Snapdragon 410 (MSM8916)
- External modem firmware loaded using remoteproc
- Linux 6.12
- OpenWrt master

The device exposes:

- QMI interface
- AT interface
- Multiple WWAN network interfaces

Bluetooth is available.

No built-in speaker or microphone is present.

---

## Project Goal

Enable the following workflow:

Mobile Network
        │
        ▼
   Qualcomm Modem
        │
        ▼
   QMI Voice Service
        │
        ▼
   APR / QDSP6 Audio Services
        │
        ▼
   ALSA Sound Card
        │
        ▼
Bluetooth HFP
        │
        ▼
Bluetooth Headset

The objective is to make the device capable of placing and receiving normal cellular voice calls using a Bluetooth headset.

---

## Current Status

### Verified

✓ Modem firmware boots successfully.

✓ LTE data works.

✓ QMI Voice service version 2.1 is present.

✓ Upstream Linux already contains:

- APR driver
- QDSP6 audio framework
- MSM8916 QDSP6 sound card driver
- MSM8916 modem DTS include

✓ Bluetooth works.

---

## Not Yet Verified

- APR communication with modem
- ADSP audio services
- ALSA sound card registration
- Audio routing
- Voice session establishment
- Bluetooth HFP integration

---

## Expected Software Stack

Application
    ↓
ModemManager / Custom Voice Manager
    ↓
libqmi
    ↓
QMI Voice
    ↓
APR
    ↓
QDSP6
    ↓
ALSA ASoC
    ↓
Bluetooth HFP

---

## Documentation Structure

00-overview.md

High level project overview.

01-qmi-voice.md

Qualcomm Voice service.

02-qdsp6.md

Qualcomm Hexagon DSP audio architecture.

03-apr.md

APR messaging protocol.

04-device-tree.md

Device tree requirements.

05-kernel-config.md

Kernel configuration.

06-bluetooth-hfp.md

Bluetooth call audio routing.

07-testing.md

Testing procedures.

TODO.md

Remaining work.
