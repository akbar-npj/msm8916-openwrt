# Bluetooth Hands-Free Profile (HFP)

## Overview

The Bluetooth Hands-Free Profile (HFP) allows a Bluetooth headset to function as a remote microphone, speaker and call control device.

For the MSM8916 OpenWrt voice project, HFP provides the interface between the cellular modem and an external Bluetooth headset.

Unlike A2DP, which is designed for high-quality music playback, HFP is specifically designed for bidirectional, low-latency voice communication.

---

# Project Goal

The long-term goal of this project is to make the UFI001B operate as a Bluetooth Audio Gateway (AG).

The Bluetooth headset acts as the Hands-Free (HF) device.

```text
               Cellular Network
                      │
                      ▼
               Qualcomm Modem
                      │
                      ▼
                ModemManager
                      │
                      ▼
                 PipeWire AG
                      │
                 Bluetooth HFP
                      │
                      ▼
              Bluetooth Headset
```

---

# HFP Roles

Bluetooth HFP defines two devices.

## Audio Gateway (AG)

Normally:

- Mobile phone
- Car infotainment system

Responsibilities:

- Controls cellular calls
- Maintains call state
- Provides audio
- Reports signal strength
- Reports network status
- Reports battery level (optional)

Our OpenWrt router will implement the Audio Gateway.

---

## Hands-Free (HF)

Normally:

- Bluetooth headset
- Earbuds
- Speakerphone

Responsibilities:

- Capture microphone audio
- Play received audio
- Answer calls
- Reject calls
- Hang up
- Dial redial
- Adjust volume

Galaxy Buds2 Pro operates as the Hands-Free device.

---

# HFP Architecture

The Bluetooth HFP stack is significantly more complex than ordinary Bluetooth audio.

```text
                 Cellular Network
                        │
                        ▼
                 Qualcomm Modem
                        │
                        ▼
                 ModemManager
                        │
                        ▼
            PipeWire Telephony Service
                        │
                        ▼
          PipeWire Bluetooth Backend
                        │
              RFCOMM Control Channel
                        │
                 Bluetooth Headset
                        │
               SCO Audio Connection
                        │
                 Voice Microphone
                 Voice Speaker
```

Two completely separate channels exist.

---

# RFCOMM

RFCOMM transports call control.

Examples include:

- Answer
- Hangup
- Dial
- Call status
- Signal strength
- Battery level

RFCOMM carries AT commands.

Example:

```text
AT+CIND?

AT+CLCC

ATA

AT+CHUP

ATD<number>;
```

These commands do **not** transport audio.

---

# SCO

Voice audio uses SCO (Synchronous Connection-Oriented) links.

```text
Microphone

↓

Bluetooth SCO

↓

PipeWire

↓

ALSA

↓

Voice DSP
```

SCO provides:

- Low latency
- Bidirectional audio
- Constant bandwidth

Unlike A2DP, SCO is optimized for voice communication.

---

# Voice Codecs

HFP supports multiple codecs.

## CVSD

Mandatory.

Characteristics:

- Narrowband speech
- 8 kHz sampling
- Universally supported

---

## mSBC

Optional.

Characteristics:

- Wideband speech
- 16 kHz sampling
- Better voice quality

Both PipeWire and BlueZ support codec negotiation.

---

# PipeWire Implementation

One of the most significant discoveries during this project is that PipeWire already implements a complete Bluetooth Audio Gateway.

Important components include:

```text
backend-native.c

telephony.c

modemmanager.c
```

These components eliminate the need to implement HFP from scratch.

---

# backend-native.c

Responsibilities:

- Bluetooth Audio Gateway
- RFCOMM handling
- SCO handling
- Codec negotiation
- Headset state machine
- Audio transport

This file manages the Bluetooth connection itself.

---

# telephony.c

Provides:

```text
org.pipewire.Telephony
```

D-Bus service.

Responsibilities:

- Call objects
- Audio Gateway objects
- D-Bus methods
- HFP call state

It exposes telephony services to the Bluetooth backend.

---

# modemmanager.c

Bridges PipeWire and ModemManager.

Responsibilities:

- Monitor call state
- Monitor network registration
- Monitor signal strength
- Monitor roaming
- Monitor active calls

Converts ModemManager events into Bluetooth HFP indicators.

---

# q6voiced

q6voiced is **not** part of Bluetooth.

Instead it monitors ModemManager.

```text
ModemManager

↓

Call Active

↓

Open Voice PCM
```

When the call ends:

```text
Close Voice PCM
```

Its only purpose is to activate the Qualcomm voice ALSA device.

---

# WirePlumber

WirePlumber configures PipeWire.

The MSM8916 configuration enables the native modem backend.

Example:

```ini
monitor.bluez.properties = {
    bluez5.hfphsp-backend-native-modem = "any"
}
```

This instructs PipeWire to use ModemManager as the source of call information.

---

# Complete Audio Flow

The expected audio path is:

```text
        Headset Microphone
                │
                ▼
           Bluetooth SCO
                │
                ▼
            PipeWire
                │
                ▼
          ALSA Voice PCM
                │
                ▼
            q6voice
                │
                ▼
               APR
                │
                ▼
           Qualcomm DSP
                │
                ▼
              Modem
                │
                ▼
        Cellular Network
```

Receive audio follows the reverse path.

---

# Call Control Flow

Call signalling follows a different path.

```text
Headset

↓

RFCOMM

↓

PipeWire

↓

ModemManager

↓

QMI Voice

↓

Modem
```

No voice samples are transported through this path.

Only call-control messages.

---

# Relationship with Bluetooth

BlueZ manages:

- Pairing
- Bonding
- Discovery
- HCI
- Bluetooth profiles

PipeWire manages:

- HFP Audio Gateway
- SCO audio
- RFCOMM telephony
- Codec negotiation

The two projects work together.

---

# Current MSM8916 Status

Verified:

✓ Bluetooth controller

✓ WCNSS firmware

✓ btqcomsmd

✓ btqca

✓ HCI controller

✓ Device discovery

✓ Pairing support

✓ PipeWire HFP implementation identified

✓ q6voiced identified

✓ ModemManager integration identified

Under Investigation:

- ALSA voice PCM

- q6voice bring-up

- SCO audio

- Bluetooth call audio

- Wideband speech

---

# Key Observations

The original assumption was that Bluetooth call support would require a custom implementation.

Investigation of postmarketOS and PipeWire demonstrated that this is not necessary.

Modern Linux already provides an upstream implementation consisting of:

- ModemManager
- q6voiced
- PipeWire
- WirePlumber
- BlueZ

The remaining work is to expose a functioning Qualcomm voice PCM device and integrate these existing components within OpenWrt.

---

# References

- Bluetooth Hands-Free Profile Specification
- BlueZ
- PipeWire
- WirePlumber
- q6voiced
- ModemManager
- postmarketOS
