# Qualcomm QMI Voice Service

## Overview

Qualcomm MSM8916 modems implement cellular voice functionality using the Qualcomm MSM Interface (QMI) Voice service.

Unlike traditional AT-command based voice control, modern Qualcomm chipsets expose voice call management through a dedicated QMI service. This service is responsible for establishing, managing and terminating voice calls while coordinating the modem's internal voice DSP.

For the MSM8916 platform, QMI Voice forms the control plane of the entire cellular calling stack.

---

# What is QMI?

QMI (Qualcomm MSM Interface) is Qualcomm's proprietary IPC protocol used for communication between the application processor and the modem subsystem.

Instead of sending AT commands directly to the modem, Linux applications communicate with QMI services.

Each modem function is implemented as an independent service.

Examples include:

| Service | Purpose |
|----------|---------|
| DMS | Device Management |
| NAS | Network Access |
| WDS | Wireless Data Service |
| UIM | SIM Card Management |
| PDS | Positioning |
| VOICE | Cellular Voice Calls |

Each service exposes its own message IDs, requests, responses and indications.

---

# QMI Voice Responsibilities

The Voice service is responsible for:

- Dialing outgoing calls
- Receiving incoming calls
- Answering calls
- Rejecting calls
- Hanging up calls
- Call waiting
- Hold and resume
- Multiparty calls
- DTMF generation
- Call state notifications

The Voice service only manages call control.

It does **not** transport audio.

Audio is handled separately through the Qualcomm audio subsystem (APR/QDSP6).

---

# Voice Control vs Voice Audio

A useful way to think about the system is:

```text
                Voice Control

Application
      │
      ▼
ModemManager
      │
      ▼
libqmi
      │
      ▼
QMI Voice Service
      │
      ▼
Qualcomm Modem
```

This path controls:

- Dial
- Answer
- Hangup
- Hold
- Call status

No audio flows through this path.

---

Voice audio uses an entirely different subsystem.

```text
              Voice Audio

Bluetooth Headset
        │
        ▼
PipeWire
        │
        ▼
ALSA Voice PCM
        │
        ▼
QDSP6 Voice Driver
        │
        ▼
APR
        │
        ▼
Voice DSP
        │
        ▼
Qualcomm Modem
```

The control plane and audio plane operate independently.

---

# ModemManager

On Linux, ModemManager provides a high-level interface to the QMI Voice service.

Applications typically interact with ModemManager instead of directly using libqmi.

Responsibilities include:

- Creating voice calls
- Receiving incoming call notifications
- Tracking call state
- Exposing D-Bus APIs
- Managing multiple simultaneous calls

During a call, ModemManager emits D-Bus signals whenever the call state changes.

These signals are used by q6voiced and PipeWire.

---

# Call Lifecycle

A typical outgoing call follows this sequence.

```text
User
 │
 ▼
Dial Number
 │
 ▼
ModemManager
 │
 ▼
QMI Voice
 │
 ▼
Modem
 │
 ▼
Network
 │
 ▼
Remote Phone
```

Typical call states include:

```text
Idle
 │
 ▼
Dialing
 │
 ▼
Alerting
 │
 ▼
Active
 │
 ▼
Disconnecting
 │
 ▼
Idle
```

Incoming calls follow a similar state machine beginning with the Ringing state.

---

# Interaction with q6voiced

One of the most important discoveries during this project is the role of q6voiced.

q6voiced does **not** control cellular calls.

Instead, it monitors ModemManager D-Bus notifications.

When a call transitions into an active state, q6voiced opens the Qualcomm voice ALSA PCM device.

When the call ends, the PCM device is closed.

This starts and stops the audio path without affecting call control.

```text
ModemManager
      │
      ▼
StateChanged Signal
      │
      ▼
q6voiced
      │
      ▼
Open Voice PCM
```

---

# Interaction with PipeWire

PipeWire also listens to ModemManager.

However, its purpose is different.

Instead of opening PCM devices, PipeWire converts ModemManager call information into Bluetooth Hands-Free Profile (HFP) events.

This allows Bluetooth headsets to:

- Display call state
- Answer calls
- Reject calls
- Hang up calls
- Exchange SCO audio

Thus, both q6voiced and PipeWire depend on ModemManager, but they perform different tasks.

```text
                ModemManager
                 │       │
                 │       │
                 ▼       ▼
            q6voiced   PipeWire
                 │       │
          Voice PCM     Bluetooth HFP
```

---

# Current MSM8916 Status

Verified:

- ✓ Modem firmware boots
- ✓ LTE data works
- ✓ QMI interfaces are operational
- ✓ QMI Voice service version 2.1 is available
- ✓ ModemManager detects the modem

Under Investigation:

- Voice session establishment
- APR voice services
- ALSA voice PCM
- End-to-end voice routing

---

# Key Observations

The QMI Voice service is responsible only for controlling voice calls.

It is **not** responsible for transporting voice audio.

Audio transport is performed through the Qualcomm QDSP6 audio subsystem, while Bluetooth integration is handled by PipeWire and BlueZ.

Understanding this separation between the control plane and audio plane is fundamental to the MSM8916 voice implementation.

---

# References

- Qualcomm MSM Interface (QMI)
- ModemManager D-Bus API
- libqmi
- Linux QMI drivers
- PipeWire Telephony
- q6voiced
