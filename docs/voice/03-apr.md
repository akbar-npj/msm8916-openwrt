# Asynchronous Packet Router (APR)

## Overview

The Asynchronous Packet Router (APR) is Qualcomm's inter-processor messaging protocol used for communication between the Linux application processor and the Qualcomm Digital Signal Processors (DSPs).

For MSM8916, APR is the primary communication mechanism used by the Linux kernel to control audio processing on the QDSP6 Audio DSP (ADSP).

Every significant audio operation—including playback, capture, voice calls, routing, calibration, and device configuration—is performed by sending APR messages to services running on the DSP.

APR therefore forms the communication layer between Linux kernel drivers and Qualcomm's DSP firmware.

---

# Position in the Audio Stack

APR sits between the Linux audio drivers and the Qualcomm DSP.

```text
                Userspace

                   │

                 ALSA

                   │

             ASoC Drivers

                   │

       q6voice / q6asm / q6adm / q6afe

                   │

              APR Framework

                   │

           Shared Memory / IPC

                   │

             Qualcomm ADSP

                   │

          DSP Audio Services
```

Unlike ALSA, APR is not an audio framework.

It is simply a messaging protocol.

---

# Why APR Exists

The Audio DSP executes its own firmware independently of Linux.

Linux cannot directly call DSP functions.

Instead, Linux sends commands using APR.

Typical commands include:

- Open a playback stream
- Open a voice session
- Configure an audio device
- Route audio
- Start streaming
- Stop streaming
- Close a session
- Query DSP capabilities

The DSP receives the command, processes it, and returns a response.

---

# High-Level Communication Flow

Every audio operation follows roughly the same sequence.

```text
Application

      │

   ALSA API

      │

ASoC Driver

      │

QDSP6 Driver

      │

 APR Message

      │

 Shared Memory

      │

 Qualcomm DSP

      │

 DSP Service

      │

 APR Response

      │

 Linux Driver

      │

 Application
```

APR provides reliable request/response communication between Linux and the DSP.

---

# DSP Services

Multiple services run inside the Qualcomm DSP.

Each service performs a different function.

Examples include:

| Service | Purpose |
|----------|---------|
| AFE | Audio Front End |
| ASM | Audio Stream Manager |
| ADM | Audio Device Manager |
| Voice | Voice call processing |
| Calibration | Audio calibration |
| Routing | Internal audio routing |

Linux drivers communicate with these services using APR messages.

---

# Kernel Architecture

The Linux kernel contains a generic APR framework.

Specialized QDSP6 drivers are layered above it.

```text
          Linux Kernel

                │

        ASoC Machine Driver

                │

      +---------+---------+
      |         |         |
      ▼         ▼         ▼

    q6afe    q6asm    q6voice

      │         │         │

      +---------+---------+

                │

             qcom-apr

                │

             Shared Memory

                │

              ADSP
```

Each driver builds APR packets specific to its DSP service.

---

# APR Packet Structure

Every APR transaction consists of a packet.

Conceptually, an APR packet contains:

```text
+----------------------+
| APR Header           |
+----------------------+
| Source Port          |
+----------------------+
| Destination Port     |
+----------------------+
| Opcode               |
+----------------------+
| Payload Length       |
+----------------------+
| Payload              |
+----------------------+
```

The payload depends on the DSP service being addressed.

For example:

- Voice commands
- Audio routing commands
- Stream configuration
- Device configuration

Each service defines its own payload formats.

---

# Request / Response Model

APR is asynchronous.

Linux sends a request.

```text
Linux

   │

Request

   │

   ▼

 DSP
```

The DSP processes the command.

Later, it returns either:

```text
Response
```

or

```text
Event Notification
```

The Linux driver receives the response and wakes the waiting subsystem.

---

# Voice Call Example

During a voice call, a simplified sequence is:

```text
q6voiced

     │

Open Voice PCM

     │

q6voice

     │

APR

     │

Voice Service

     │

QDSP6

     │

Voice Path Created
```

Once the DSP acknowledges the request, the ALSA PCM becomes active.

---

# Relationship with QMI Voice

APR and QMI serve completely different purposes.

QMI controls the modem.

```text
Application

     │

ModemManager

     │

QMI Voice

     │

Modem
```

APR controls the DSP.

```text
Application

     │

ALSA

     │

q6voice

     │

APR

     │

QDSP6
```

Both are required for cellular voice calls.

---

# Relationship with q6voice

The q6voice driver translates ALSA operations into APR messages.

```text
ALSA

   │

q6voice

   │

APR Packet

   │

Voice DSP
```

The driver hides APR details from userspace.

Applications never communicate with APR directly.

---

# Relationship with q6afe

The Audio Front End (AFE) driver configures physical audio interfaces.

Examples include:

- PCM interfaces
- I²S
- SlimBus
- Bluetooth audio interfaces

Configuration is performed through APR commands sent to the AFE service.

---

# Relationship with q6asm

ASM manages audio streams.

Typical APR operations include:

- Open stream
- Configure format
- Start playback
- Stop playback
- Close stream

Every playback or capture stream is ultimately managed through APR.

---

# Relationship with q6adm

ADM manages routing inside the DSP.

Typical routing examples include:

```text
Microphone

      │

 Voice Encoder

      │

 Cellular Network
```

or

```text
Bluetooth SCO

      │

 Voice Decoder

      │

 Speaker
```

ADM creates and destroys these routes using APR messages.

---

# Debugging APR

Useful kernel components include:

- qcom-apr
- q6voice
- q6afe
- q6asm
- q6adm

Useful tools include:

- dmesg
- dynamic debug
- tracepoints
- debugfs

When debugging voice support, APR failures often indicate:

- Missing DSP firmware
- Incorrect Device Tree
- Missing DSP service
- Invalid routing configuration
- Unsupported DSP command

---

# Current MSM8916 Status

Verified:

- Upstream Linux contains the generic APR framework.
- MSM8916 uses APR for QDSP6 communication.
- Upstream QDSP6 audio drivers depend on APR.

Under Investigation:

- ADSP firmware loading
- Voice service availability
- Voice session creation
- DSP routing configuration
- MSM8916-specific APR initialization

---

# Key Observations

APR is not an audio subsystem.

It is the transport mechanism used by Linux to communicate with Qualcomm DSP firmware.

Every significant DSP operation—including voice call setup, audio routing, playback, and capture—is ultimately implemented as one or more APR transactions.

Understanding APR is essential for debugging Qualcomm audio systems because almost every higher-level audio component depends on successful APR communication.

---

# References

- Linux qcom-apr framework
- Qualcomm QDSP6 Audio Framework
- Qualcomm APR protocol
- Linux ASoC subsystem
- MSM8916 upstream audio drivers
