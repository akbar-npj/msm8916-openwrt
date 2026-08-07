# Linux Kernel Voice Stack (MSM8916)

## Purpose

This document describes the upstream Linux implementation of Qualcomm voice support
for MSM8916 using the QDSP6 audio framework.

Unlike ordinary PCM playback or recording, cellular voice calls are implemented
using a dedicated voice subsystem built on top of ALSA, APR and multiple DSP
services running on the Qualcomm Hexagon (QDSP6) processor.

The goal of this document is to understand how a userspace application opening a
voice PCM device eventually starts the modem voice engine.

---

# Architecture Overview

```
                    Userspace
                        │
                 ModemManager
                        │
                 D-Bus Call Events
                        │
                    q6voiced
                        │
      ┌─────────────────┴─────────────────┐
      ▼                                   ▼
snd_pcm_open(Playback)            snd_pcm_open(Capture)
      │                                   │
      └─────────────────┬─────────────────┘
                        ▼
                 q6voice-dai.c
                        │
                 ALSA DAI Startup
                        │
                        ▼
                   q6voice.c
                        │
          ┌─────────────┴─────────────┐
          ▼                           ▼
      q6mvm.c                     q6cvp.c
          │                           │
          └─────────────┬─────────────┘
                        ▼
               q6voice-common.c
                        │
                        ▼
                     APR Bus
                        │
                        ▼
               Qualcomm Voice DSP
```

The Linux kernel itself does **not** implement voice processing.

Instead, it manages voice sessions and communicates with the Qualcomm DSP using
APR (Asynchronous Packet Router) commands.

All signal processing, echo cancellation, noise suppression and modem audio
routing are implemented inside the DSP firmware.

---

# Kernel Components

The MSM8916 voice stack consists of several independent kernel modules.

| Component | Purpose |
|-----------|---------|
| q6voice-dai | ALSA Voice DAI |
| q6voice | Voice session manager |
| q6mvm | Multimode Voice Manager |
| q6cvp | Core Voice Processor |
| q6cvs | Core Voice Stream service |
| q6voice-common | Common APR transport/session layer |

Each component has a clearly defined responsibility.

---

# q6voice-dai

Location

```
sound/soc/qcom/qdsp6/q6voice-dai.c
```

Responsibilities

- Registers the CS-VOICE ALSA DAI
- Exposes playback and capture PCM streams
- Provides ALSA mixer controls
- Calls q6voice_start() during startup
- Calls q6voice_stop() during shutdown

Supported PCM format

| Property | Value |
|----------|-------|
| Sample Rate | 8000 Hz |
| Channels | 1 (Mono) |
| Format | S16_LE |

The DAI itself performs no DSP processing.

Its purpose is to expose a standard ALSA interface to userspace.

---

# q6voice

Location

```
sound/soc/qcom/qdsp6/q6voice.c
```

Purpose

Coordinates the entire voice session.

Responsibilities

- Create MVM session
- Create CVP session
- Enable voice processor
- Attach processor to MVM
- Start voice session
- Stop and destroy sessions

An important implementation detail is that voice is **not** started until both
playback and capture streams have been opened.

```
Playback Open
        │
Capture Open
        │
        ▼
q6voice_path_start()
```

This guarantees a full-duplex voice path.

---

# q6mvm

Location

```
sound/soc/qcom/qdsp6/q6mvm.c
```

Purpose

Implements the Multimode Voice Manager.

This module controls the lifecycle of a cellular voice call.

APR commands issued

| Command | Purpose |
|----------|---------|
| CREATE_PASSIVE_CONTROL_SESSION | Create MVM session |
| SET_POLICY_DUAL_CONTROL | Enable modem-controlled state machine |
| ATTACH_VOCPROC | Connect voice processor |
| START_VOICE | Begin call |
| STOP_VOICE | End call |
| DETACH_VOCPROC | Disconnect processor |

MVM is responsible for the overall voice session state.

---

# q6cvp

Location

```
sound/soc/qcom/qdsp6/q6cvp.c
```

Purpose

Implements the Core Voice Processor (VocProc).

This module configures the DSP voice processing pipeline.

Current upstream configuration

| Parameter | Value |
|-----------|-------|
| Direction | RX + TX |
| TX topology | SM_ECNS |
| RX topology | DEFAULT |
| Echo reference | Internal |
| Calibration | None |

The TX and RX AFE ports are configured using ALSA mixer controls exposed by
q6voice-dai.

APR commands

- CREATE_FULL_CONTROL_SESSION_V2
- ENABLE
- DISABLE

---

# q6cvs

Location

```
sound/soc/qcom/qdsp6/q6cvs.c
```

Purpose

Registers the Core Voice Stream APR service.

Current upstream implementation

The driver currently contains only APR service registration and callback
handling.

No additional stream-management functionality has been implemented.

---

# q6voice-common

Location

```
sound/soc/qcom/qdsp6/q6voice-common.c
```

Purpose

Provides common APR session management for all voice services.

Responsibilities

- Discover APR services
- Create DSP sessions
- Destroy DSP sessions
- Send APR commands
- Wait for synchronous replies
- Maintain DSP session handles

Every voice-related APR command eventually passes through

```
q6voice_common_send()
```

which

1. Builds the APR packet
2. Sends it using apr_send_pkt()
3. Waits for DSP acknowledgement
4. Returns success or failure

---

# Device Tree Integration

The voice frontend is described entirely in Device Tree.

```
frontend4-dai-link
```

contains

```
<&q6voicedai CS_VOICE>
```

Unlike older machine drivers, the MSM8916 implementation does not manually
create DAI links.

Instead,

```
qcom_snd_parse_of()
```

parses the Device Tree and dynamically creates the ALSA DAI links.

---

# Voice Startup Sequence

The complete startup sequence is

```
q6voiced

↓

snd_pcm_open()

↓

q6voice_dai_startup()

↓

q6voice_start()

↓

q6mvm_session_create()

↓

CREATE_PASSIVE_CONTROL_SESSION

↓

SET_POLICY_DUAL_CONTROL

↓

q6cvp_session_create()

↓

CREATE_FULL_CONTROL_SESSION_V2

↓

ENABLE

↓

ATTACH_VOCPROC

↓

START_VOICE

↓

Voice DSP Active
```

---

# Voice Shutdown Sequence

```
STOP_VOICE

↓

DETACH_VOCPROC

↓

DISABLE

↓

DESTROY_SESSION
```

---

# Relationship with q6voiced

The userspace daemon **q6voiced** performs almost no audio processing.

Its responsibilities are limited to

- waiting for ModemManager call notifications
- opening the playback PCM
- opening the capture PCM
- closing both devices when the call ends

Opening the PCM devices automatically activates the kernel voice stack.

---

# Key Observations

The upstream Linux implementation is intentionally minimal.

Linux is responsible only for

- ALSA integration
- voice session management
- APR communication
- DSP routing

All voice processing remains inside Qualcomm's proprietary DSP firmware.

This design keeps the Linux implementation relatively small while relying on
the modem firmware for telephony-specific functionality.

---

# Next Investigation

Having understood the upstream kernel implementation, the next step is to
compare this architecture with the OpenWrt MSM8916 kernel.

The comparison will verify

- Kernel configuration
- Device Tree
- APR services
- QDSP6 driver support
- ALSA sound card registration
- Runtime initialization

The goal is to identify the remaining work required before native Bluetooth HFP
cellular calling can function on OpenWrt.
