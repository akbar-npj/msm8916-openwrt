# Linux Userspace Voice Architecture

## Overview

The Linux kernel provides the low-level infrastructure required for Qualcomm voice support, including Remoteproc, APR, QDSP6 audio drivers, ALSA, and Bluetooth HCI drivers.

Userspace is responsible for coordinating these kernel components into a complete cellular voice solution.

Unlike Android, where proprietary daemons implement much of the telephony stack, modern Linux systems use open-source components including ModemManager, PipeWire, WirePlumber, BlueZ, and q6voiced.

One of the primary goals of this project is to integrate these existing upstream components into OpenWrt rather than developing a custom voice framework.

---

# Overall Architecture

The complete userspace architecture is shown below.

```text
                    Cellular Network
                           │
                           ▼
                    Qualcomm Modem
                           │
                     QMI Voice Service
                           │
                           ▼
                     ModemManager
                           │
                  D-Bus Call Events
                           │
        ┌──────────────────┴──────────────────┐
        │                                     │
        ▼                                     ▼
    q6voiced                         PipeWire Telephony
        │                                     │
 Opens Voice PCM                  Bluetooth Audio Gateway
        │                                     │
        ▼                                     ▼
       ALSA                          BlueZ Bluetooth Stack
        │                                     │
        └──────────────────┬──────────────────┘
                           ▼
                    Bluetooth Headset
```

Each component has a well-defined responsibility.

---

# Component Responsibilities

## ModemManager

ModemManager provides the high-level interface to the cellular modem.

Responsibilities include:

- Modem detection
- SIM management
- Network registration
- Cellular data
- SMS
- Voice calls
- D-Bus API

Voice calls are controlled through the Qualcomm QMI Voice service.

Applications communicate with ModemManager rather than directly with libqmi.

---

## q6voiced

q6voiced is a lightweight daemon used by postmarketOS and other Linux mobile systems.

Its purpose is frequently misunderstood.

q6voiced does **not**:

- manage Bluetooth
- control calls
- route audio
- communicate with the headset

Instead, it simply monitors ModemManager D-Bus signals.

When a call becomes active:

```text
ModemManager

↓

Call Active

↓

q6voiced

↓

Open ALSA Voice PCM
```

When the call ends:

```text
Close ALSA Voice PCM
```

Opening the PCM device activates the Qualcomm voice path inside the kernel.

---

## PipeWire

PipeWire is responsible for multimedia routing.

For telephony it provides:

- Bluetooth Audio Gateway
- HFP implementation
- SCO audio routing
- Codec negotiation
- Telephony D-Bus interfaces

PipeWire already contains an upstream implementation of Bluetooth Hands-Free Profile.

No custom implementation is required.

---

## WirePlumber

WirePlumber is PipeWire's session manager.

Responsibilities include:

- Device policy
- Audio routing
- Backend selection
- ALSA configuration
- Bluetooth configuration

For MSM8916, WirePlumber enables the native modem backend.

Example:

```ini
monitor.bluez.properties = {
    bluez5.hfphsp-backend-native-modem = "any"
}
```

This instructs PipeWire to obtain call information from ModemManager.

---

## BlueZ

BlueZ provides the Linux Bluetooth stack.

Responsibilities include:

- Pairing
- Discovery
- Bonding
- Security
- Bluetooth profiles
- D-Bus interfaces

BlueZ does not implement the complete Bluetooth Hands-Free Audio Gateway.

Instead, PipeWire integrates with BlueZ to provide HFP functionality.

---

# PipeWire Internal Architecture

During this project, several important PipeWire components were identified.

## backend-native.c

Responsibilities:

- Bluetooth Audio Gateway
- RFCOMM
- SCO
- Codec negotiation
- Headset state machine

---

## telephony.c

Provides:

```text
org.pipewire.Telephony
```

D-Bus interface.

Responsibilities include:

- Call objects
- Audio Gateway objects
- HFP state management

---

## modemmanager.c

Connects PipeWire to ModemManager.

Responsibilities include:

- Monitor call state
- Monitor registration
- Signal strength
- Roaming
- Active calls

Converts ModemManager information into Bluetooth HFP indicators.

---

# Call Control Flow

Cellular call signalling follows the path below.

```text
Bluetooth Headset

↓

RFCOMM

↓

PipeWire

↓

ModemManager

↓

QMI Voice

↓

Qualcomm Modem

↓

Cellular Network
```

Only signalling messages travel through this path.

Examples include:

- Dial
- Answer
- Reject
- Hang up
- Call waiting
- DTMF

---

# Voice Audio Flow

Voice samples use a completely different path.

Transmit audio:

```text
Headset Microphone

↓

Bluetooth SCO

↓

PipeWire

↓

ALSA Voice PCM

↓

q6voice

↓

APR

↓

QDSP6

↓

Qualcomm Modem
```

Receive audio follows the reverse direction.

This separation between signalling and audio is fundamental to the architecture.

---

# D-Bus Interfaces

Several D-Bus interfaces cooperate to provide voice functionality.

Examples include:

## ModemManager

```text
org.freedesktop.ModemManager1
```

Provides:

- Modem information
- Voice call objects
- Call state

---

## PipeWire

```text
org.pipewire.Telephony
```

Provides:

- Audio Gateway
- Call objects
- Telephony control

---

## BlueZ

```text
org.bluez
```

Provides:

- Bluetooth devices
- Pairing
- Profiles
- Controller management

---

# Relationship with the Kernel

Userspace sits above the Linux audio framework.

```text
Applications

↓

ModemManager

↓

PipeWire

↓

ALSA

↓

MSM8916 Machine Driver

↓

QDSP6

↓

APR

↓

DSP
```

Userspace never communicates directly with the DSP.

Instead, it accesses ALSA PCM devices exposed by the kernel.

---

# Current MSM8916 Status

Verified:

✓ ModemManager

✓ QMI Voice service

✓ Bluetooth controller

✓ BlueZ

✓ PipeWire HFP implementation identified

✓ q6voiced identified

✓ WirePlumber configuration identified

✓ Native modem backend identified

Under Investigation:

- ALSA voice PCM registration

- q6voice initialization

- SCO audio

- Bluetooth call audio

- Wideband speech

---

# Key Observations

One of the most significant discoveries during this project is that upstream Linux already provides a complete userspace architecture for Bluetooth cellular voice.

The major components already exist:

- ModemManager
- q6voiced
- PipeWire
- WirePlumber
- BlueZ

The remaining challenge is not implementing these components, but integrating them with the MSM8916 kernel audio stack and exposing a functional Qualcomm voice PCM device.

---

# References

- ModemManager
- PipeWire
- WirePlumber
- BlueZ
- q6voiced
- Linux ALSA
- postmarketOS
