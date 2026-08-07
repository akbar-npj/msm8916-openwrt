# Device Tree Requirements

## Overview

The Linux Device Tree (DT) describes the hardware present on an MSM8916 platform and provides the information required for the kernel to initialize and connect the Qualcomm audio subsystem.

Unlike traditional PC hardware, Qualcomm SoCs rely heavily on the Device Tree to describe DSPs, remote processors, audio interfaces, routing information, clocks, interrupts, shared memory regions, and interconnects.

For voice support, simply enabling kernel drivers is not sufficient. The required Device Tree nodes must also exist and correctly describe the hardware.

---

# Why the Device Tree Matters

The Qualcomm audio stack is highly modular.

Most kernel drivers do not contain board-specific information.

Instead, they obtain configuration from the Device Tree.

Without the required DT nodes:

- DSP firmware cannot be loaded.
- APR cannot initialize.
- ASoC sound cards are not created.
- Audio interfaces remain unavailable.
- Voice routing cannot be established.

---

# High-Level Architecture

A simplified view of the relevant Device Tree hierarchy is shown below.

```text
Root
 │
 ├── reserved-memory
 │
 ├── remoteproc
 │     │
 │     └── ADSP
 │
 ├── apr
 │
 ├── sound
 │
 ├── q6afe
 │
 ├── q6asm
 │
 ├── q6adm
 │
 └── q6voice
```

Not every platform exposes every node directly.

Some drivers instantiate child devices dynamically after the DSP boots.

---

# Reserved Memory

Qualcomm DSPs communicate with Linux through shared memory.

The Device Tree reserves memory regions that must not be used by Linux.

Typical purposes include:

- DSP firmware
- IPC buffers
- Shared memory
- Crash dumps

Example:

```text
reserved-memory
```

Without these regions the DSP cannot communicate reliably.

---

# Remote Processor

The ADSP is treated as a remote processor.

The Device Tree typically contains a node describing:

- firmware image
- memory regions
- interrupts
- clocks
- power domains

Linux uses the remoteproc framework to:

- load firmware
- start the DSP
- recover after crashes

Typical firmware:

```text
adsp.mdt
```

or platform-specific variants.

---

# APR Node

The APR node establishes communication between Linux and the DSP.

Responsibilities include:

- APR transport initialization
- service discovery
- endpoint registration

All higher-level QDSP6 drivers depend on a functioning APR connection.

---

# Sound Card

The ASoC machine driver creates the ALSA sound card.

The Device Tree describes:

- CPU DAIs
- DSP DAIs
- codec DAIs
- routing
- audio widgets

Once initialized, Linux registers one or more ALSA sound cards.

Applications interact only with ALSA.

---

# Digital Audio Interfaces (DAIs)

DAIs define the logical audio links between components.

Examples include:

```text
CPU DAI

↓

DSP DAI

↓

Codec DAI
```

Voice support often introduces dedicated voice DAIs in addition to normal playback and capture DAIs.

These links determine how audio flows through the system.

---

# Audio Routing

Routing describes how internal audio paths are connected.

Examples:

```text
Microphone

↓

Voice Encoder

↓

Modem
```

or

```text
Bluetooth SCO

↓

Voice Decoder

↓

Speaker
```

Routing tables are provided by the machine driver and Device Tree.

Incorrect routing results in silent audio despite successful call establishment.

---

# Audio Widgets

Widgets represent functional blocks within the audio graph.

Typical widgets include:

- Microphone
- Speaker
- Headphone
- Line In
- Line Out
- Bluetooth
- Voice

Widgets are connected through routing definitions.

---

# Bluetooth Audio

For the UFI001B platform there is no integrated audio codec.

Instead, the expected audio endpoint is Bluetooth.

The long-term goal is:

```text
Voice DSP

↓

ALSA

↓

PipeWire

↓

Bluetooth SCO

↓

Bluetooth Headset
```

Unlike smartphones, there are no physical speaker or microphone widgets to configure.

---

# MSM8916 Device Tree Includes

Upstream Linux already contains MSM8916 Device Tree include files that define much of the common hardware.

Examples include:

- MSM8916 SoC description
- Modem support
- QDSP6 support
- Audio framework

Board-specific Device Trees extend these common definitions with hardware-specific configuration.

---

# Device Tree and Driver Relationship

The relationship between Device Tree nodes and Linux drivers can be summarized as:

```text
Device Tree

      │

remoteproc

      │

ADSP Firmware

      │

APR

      │

QDSP6 Drivers

      │

ASoC Machine Driver

      │

ALSA
```

If any stage fails to initialize, subsequent components cannot function.

---

# Voice Bring-Up Sequence

A simplified initialization sequence is:

```text
Boot Linux

↓

Parse Device Tree

↓

Reserve DSP Memory

↓

Start Remote Processor

↓

Load ADSP Firmware

↓

Initialize APR

↓

Register DSP Services

↓

Create Sound Card

↓

Register ALSA PCM Devices
```

Only after this sequence completes can userspace applications access voice audio.

---

# Current MSM8916 Status

Verified:

- Remoteproc modem support is operational.
- WCNSS remote processor boots successfully.
- Upstream Linux contains MSM8916 QDSP6 support.
- Upstream Linux contains MSM8916 audio machine drivers.
- Upstream Linux contains MSM8916 modem Device Tree includes.

Under Investigation:

- ADSP firmware loading.
- APR Device Tree configuration.
- Voice DAI registration.
- ALSA sound card creation.
- Voice PCM registration.
- Bluetooth voice routing.

---

# Common Failure Modes

Typical Device Tree related failures include:

- Missing firmware node.
- Incorrect memory reservations.
- Missing APR node.
- Invalid DAI links.
- Missing routing definitions.
- Missing sound card node.
- Incorrect clock configuration.
- Incorrect interrupt definitions.

Symptoms often include:

- No ALSA devices.
- DSP failing to boot.
- APR probe failures.
- Missing voice PCM devices.
- Silent audio during calls.

---

# Verification Checklist

When bringing up a new MSM8916 device, verify the following:

- Reserved memory is present.
- Remote processor probes successfully.
- DSP firmware loads.
- APR initializes.
- QDSP6 drivers probe successfully.
- Sound card is registered.
- ALSA PCM devices appear.
- Voice DAIs are created.
- Audio routing is valid.

---

# References

- Linux Device Tree Specification
- Linux Remoteproc Framework
- Qualcomm MSM8916 Device Trees
- Linux ASoC Machine Drivers
- Linux QDSP6 Audio Framework
- Qualcomm APR Framework
