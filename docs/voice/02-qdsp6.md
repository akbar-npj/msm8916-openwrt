# Qualcomm QDSP6 Audio Architecture

## Overview

Qualcomm MSM8916 devices contain one or more dedicated Digital Signal Processors (DSPs) that perform real-time processing independently of the main ARM CPU.

For audio and voice applications, the primary DSP is the Qualcomm Hexagon QDSP6 Audio DSP (ADSP).

Instead of processing audio directly on the Cortex-A53 CPUs, Linux offloads most audio operations to the ADSP. This reduces CPU utilization, lowers power consumption, and provides deterministic real-time processing required for telephony.

The Linux kernel communicates with the ADSP using Qualcomm's APR (Asynchronous Packet Router) protocol.

---

# Qualcomm Processor Architecture

A simplified MSM8916 architecture is shown below.

```text
                 Qualcomm MSM8916 SoC

          +----------------------------+
          |        Cortex-A53          |
          |      Linux / OpenWrt       |
          +-------------+--------------+
                        |
                        |
                 Shared Memory
                        |
        +---------------+---------------+
        |                               |
        ▼                               ▼
 +---------------+             +----------------+
 |     Modem     |             |      ADSP      |
 |   Baseband    |             |   Hexagon DSP  |
 +---------------+             +----------------+
                                      |
                                      |
                               Audio Processing
```

Linux runs entirely on the ARM processor.

Audio processing executes inside the Hexagon DSP.

---

# Why Use a DSP?

Voice calls require extremely low latency.

Typical processing performed by the DSP includes:

- Audio mixing
- Echo cancellation
- Noise suppression
- Automatic gain control (AGC)
- Voice codec processing
- Sample-rate conversion
- Audio routing
- Voice synchronization

Executing these workloads on dedicated hardware provides better performance and significantly lower power consumption than running them on the application processor.

---

# QDSP6 Components

The Linux kernel divides the DSP interface into several logical services.

```text
             Linux ALSA

                 │
                 ▼
             ASoC Drivers
                 │
                 ▼
          Qualcomm DSP Drivers
                 │
        ┌────────┼────────┐
        │        │        │
        ▼        ▼        ▼
      AFE      ASM      ADM
        │        │        │
        └────────┼────────┘
                 ▼
               APR
                 ▼
            Hexagon DSP
```

Each service performs a different role.

---

# AFE (Audio Front End)

AFE provides the interface between the DSP and physical audio hardware.

Responsibilities include:

- PCM interfaces
- I²S
- SlimBus
- TDM
- Bluetooth audio interfaces
- Sample-rate configuration
- Clock management

AFE represents the hardware endpoints of the audio system.

---

# ASM (Audio Stream Manager)

ASM manages audio streams.

Responsibilities include:

- Playback streams
- Capture streams
- Audio buffers
- Stream state
- Stream synchronization

Applications typically interact with ASM indirectly through ALSA.

---

# ADM (Audio Device Manager)

ADM controls audio routing inside the DSP.

Examples include routing audio between:

- Microphone → Voice encoder
- Voice decoder → Speaker
- Bluetooth SCO → Voice processor
- PCM interface → DSP

ADM determines where audio flows inside the DSP.

---

# Voice Services

Voice calls use dedicated DSP services separate from ordinary media playback.

These services handle:

- Circuit-switched voice
- VoLTE voice processing
- Voice encoder
- Voice decoder
- Uplink processing
- Downlink processing

Unlike music playback, voice processing follows dedicated routing paths optimized for low latency.

---

# Linux Driver Stack

The Linux kernel exposes several QDSP6 drivers.

Typical call path:

```text
Userspace
     │
     ▼
ALSA
     │
     ▼
MSM8916 Sound Card
     │
     ▼
q6voice
     │
     ▼
APR
     │
     ▼
Voice DSP
```

Additional kernel drivers provide supporting functionality.

```text
Application

   │

ASoC

   │

q6voice

   │

q6asm

   │

q6adm

   │

q6afe

   │

APR

   │

QDSP6
```

Each driver exposes a specific DSP service.

---

# Relationship with QMI Voice

One of the most important concepts in the MSM8916 voice architecture is the separation between call control and audio transport.

Call control uses the modem.

```text
Application
      │
      ▼
ModemManager
      │
      ▼
QMI Voice
      │
      ▼
Modem
```

Audio uses the DSP.

```text
Bluetooth Headset
       │
       ▼
PipeWire
       │
       ▼
ALSA
       │
       ▼
q6voice
       │
       ▼
APR
       │
       ▼
QDSP6
```

The modem and DSP cooperate during a call, but they are distinct subsystems.

---

# Interaction with q6voiced

The userspace daemon **q6voiced** does not communicate directly with the DSP.

Instead, it opens the ALSA voice PCM device.

Opening the PCM causes the Linux ASoC driver to establish the required voice path through the QDSP6 drivers.

```text
ModemManager
      │
Call Active
      │
      ▼
q6voiced
      │
Open ALSA Voice PCM
      │
      ▼
q6voice
      │
      ▼
APR
      │
      ▼
QDSP6
```

---

# Current MSM8916 Status

Verified:

- Upstream Linux contains the QDSP6 framework.
- MSM8916 ASoC drivers exist upstream.
- q6voice driver exists upstream.
- APR framework exists upstream.

Under Investigation:

- ADSP firmware loading
- Voice service registration
- ALSA voice PCM creation
- Audio routing
- Voice session establishment

---

# Key Observations

The Qualcomm DSP is responsible for processing voice audio.

It is not responsible for call control.

Call control is managed through QMI Voice, while audio processing is performed by the QDSP6 audio subsystem.

This separation allows Linux userspace to control calls independently of the audio routing implemented inside the DSP.

---

# References

- Linux QDSP6 Audio Framework
- Qualcomm Hexagon DSP Architecture
- ASoC (ALSA System-on-Chip)
- APR Messaging Protocol
- MSM8916 Upstream Audio Drivers
