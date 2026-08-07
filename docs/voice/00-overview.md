# MSM8916 Voice Support for OpenWrt

## Overview

This project aims to enable native cellular voice calling on Qualcomm MSM8916-based LTE devices running OpenWrt.

Unlike Android, OpenWrt currently uses the modem almost exclusively for packet data (LTE Internet). Although Qualcomm modem firmware already provides complete voice capabilities through the Qualcomm Voice (QMI Voice) service, there is currently no upstream OpenWrt implementation capable of routing voice audio between the modem and an external audio device.

The primary objective of this project is to bridge this gap using existing upstream Linux kernel support together with modern userspace components.

---

# Target Hardware

Current development platform:

| Item | Value |
|------|-------|
| Device | UFI001B LTE USB Modem |
| SoC | Qualcomm Snapdragon 410 (MSM8916) |
| Kernel | Linux 6.12 |
| Distribution | OpenWrt Master |
| Modem firmware | Remoteproc loaded |
| Bluetooth | Qualcomm WCNSS |
| Audio hardware | None (no speaker or microphone) |

The device exposes:

- USB LTE modem
- QMI interface
- AT command interface
- Bluetooth controller
- Wi-Fi controller

The hardware contains no:

- Speaker
- Microphone
- Earpiece
- Audio codec

Therefore all voice audio must be transported externally.

---

# Project Goal

The final objective is to make the router behave as a fully functional Bluetooth hands-free gateway.

The desired call flow is:

```text
                Cellular Network
                       │
                       ▼
              Qualcomm Modem Firmware
                       │
                Qualcomm Voice Service
                       │
                       ▼
                 ModemManager
                       │
                D-Bus Call Events
                       │
        ┌──────────────┴──────────────┐
        │                             │
        ▼                             ▼
    q6voiced                  PipeWire Telephony
        │                             │
 Opens Voice PCM          Bluetooth HFP Audio Gateway
        │                             │
        ▼                             ▼
       ALSA                     RFCOMM + SCO
        │                             │
        └──────────────┬──────────────┘
                       │
                       ▼
              Bluetooth Headset
```

The headset should be capable of:

- Making outgoing calls
- Receiving incoming calls
- Two-way audio
- Call control
- Wideband speech (where supported)

---

# Design Philosophy

One important discovery during this project is that almost every required component already exists upstream.

Rather than implementing a completely new voice stack, the objective is to integrate existing upstream Linux components.

The overall architecture consists of:

## Kernel

- APR (Asynchronous Packet Router)
- QDSP6 Audio Framework
- Qualcomm Voice driver
- MSM8916 ASoC sound card
- Bluetooth HCI driver
- Bluetooth WCNSS transport

## Userspace

- ModemManager
- q6voiced
- PipeWire
- WirePlumber
- BlueZ

The work therefore focuses primarily on integration rather than new driver development.

---

# Current Status

## Working

✓ LTE data

✓ Remoteproc modem boot

✓ Qualcomm Voice service (QMI)

✓ Bluetooth controller

✓ Bluetooth discovery

✓ Bluetooth pairing

✓ Bluetooth device naming

✓ WCNSS firmware loading

✓ Upstream Linux QDSP6 drivers identified

✓ Upstream q6voice driver identified

✓ Upstream q6voiced daemon identified

✓ PipeWire Bluetooth HFP implementation identified

---

## Under Investigation

- APR communication
- QDSP6 voice services
- ALSA voice PCM
- MSM8916 sound card registration
- PipeWire integration
- Bluetooth HFP audio
- Voice routing
- Wideband speech

---

# Software Architecture

The complete software stack is expected to be:

```text
                    Applications
                          │
                          ▼
                   ModemManager
                          │
                 D-Bus Call Events
                          │
          ┌───────────────┴──────────────┐
          │                              │
          ▼                              ▼
     q6voiced                 PipeWire Telephony
          │                              │
     Opens PCM                Bluetooth Audio Gateway
          │                              │
          └───────────────┬──────────────┘
                          ▼
                        ALSA
                          │
                          ▼
                     q6voice Driver
                          │
                          ▼
                     APR Messaging
                          │
                          ▼
                      Qualcomm DSP
                          │
                          ▼
                    Modem Firmware
                          │
                          ▼
                   Cellular Network
```

---

# Documentation Structure

| Document | Description |
|----------|-------------|
| 00-overview.md | Project overview |
| 01-qmi-voice.md | Qualcomm Voice service |
| 02-qdsp6.md | Qualcomm DSP architecture |
| 03-apr.md | APR messaging protocol |
| 04-device-tree.md | Device Tree requirements |
| 05-kernel-config.md | Kernel configuration |
| 06-bluetooth.md | Bluetooth bring-up |
| 07-bluetooth-hfp.md | Bluetooth Hands-Free Profile |
| 08-testing.md | Validation and testing |
| 09-pipewire.md | PipeWire telephony architecture |
| TODO.md | Remaining work |

---

# Project Roadmap

## Phase 1 — Platform Bring-up

- Modem boot
- LTE data
- Bluetooth support
- Kernel bring-up

Status: **Completed**

---

## Phase 2 — Voice Infrastructure

- QMI Voice
- APR
- QDSP6
- ALSA
- Voice PCM

Status: **In Progress**

---

## Phase 3 — Audio Routing

- q6voiced
- PipeWire
- Bluetooth HFP
- SCO audio

Status: **Planned**

---

## Phase 4 — Voice Calling

- Outgoing calls
- Incoming calls
- Two-way audio
- Bluetooth headset
- Wideband speech

Status: **Planned**

---

# Long-Term Goal

The long-term objective is to provide a completely upstream, reproducible implementation of cellular voice support for MSM8916-based OpenWrt devices without relying on proprietary Android userspace components.

Whenever possible, existing upstream Linux kernel drivers and open-source userspace projects will be used instead of custom implementations.
