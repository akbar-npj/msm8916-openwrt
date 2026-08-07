# Kernel Configuration

## Overview

The Linux kernel already contains nearly all of the software components required to support cellular voice on Qualcomm MSM8916 platforms.

Unlike Android, where many audio components were historically implemented in downstream vendor kernels, modern upstream Linux includes support for:

- Qualcomm APR
- QDSP6 Audio Framework
- Remote Processor Framework
- Qualcomm ASoC Machine Drivers
- Bluetooth HCI
- Bluetooth HFP userspace integration

The remaining work is primarily enabling and integrating the correct kernel options for the target platform.

---

# Configuration Categories

For clarity, the required kernel options can be divided into several groups.

```text
Kernel Configuration

├── Core Qualcomm Support
├── Remote Processor
├── APR
├── QDSP6 Audio
├── ALSA / ASoC
├── Bluetooth
├── Networking
└── Debugging
```

---

# Core Qualcomm Support

These options provide the foundation for Qualcomm SoC support.

Typical examples include:

```text
CONFIG_ARCH_QCOM
CONFIG_QCOM_SMEM
CONFIG_QCOM_SMP2P
CONFIG_QCOM_RPROC_COMMON
```

These drivers provide communication mechanisms shared by multiple Qualcomm subsystems.

---

# Remote Processor Framework

The DSP firmware is executed using the Linux Remote Processor framework.

Typical options include:

```text
CONFIG_REMOTEPROC
CONFIG_QCOM_Q6V5_COMMON
CONFIG_QCOM_Q6V5_ADSP
CONFIG_QCOM_SYSMON
```

Responsibilities include:

- Firmware loading
- DSP startup
- Crash recovery
- Lifecycle management

Without Remoteproc the DSP firmware cannot execute.

---

# APR Framework

APR provides communication between Linux and the DSP.

Required options include:

```text
CONFIG_QCOM_APR
```

APR is required by nearly every QDSP6 audio driver.

Without APR:

- Voice services cannot be reached.
- Audio routing fails.
- DSP commands cannot be transmitted.

---

# QDSP6 Audio Framework

The Qualcomm audio subsystem is composed of several independent drivers.

Typical configuration includes:

```text
CONFIG_SND_SOC_QDSP6
CONFIG_SND_SOC_QDSP6_COMMON
CONFIG_SND_SOC_QDSP6_AFE
CONFIG_SND_SOC_QDSP6_ADM
CONFIG_SND_SOC_QDSP6_ASM
CONFIG_SND_SOC_QDSP6_ROUTING
```

These drivers provide communication with the corresponding DSP services.

---

# Voice Driver

Voice calls require the dedicated QDSP6 Voice driver.

Typical option:

```text
CONFIG_SND_SOC_QDSP6V2
```

or platform-specific variants depending on kernel version.

Responsibilities include:

- Voice session management
- Voice PCM creation
- DSP voice service interaction

This driver is responsible for exposing the ALSA voice interface used during calls.

---

# MSM8916 Machine Driver

The machine driver binds together:

- CPU DAIs
- DSP DAIs
- Audio routing
- ALSA sound card

Typical configuration:

```text
CONFIG_SND_SOC_MSM8916
```

Without the machine driver no sound card will be registered.

---

# ALSA Core

The standard ALSA subsystem is required.

Typical options include:

```text
CONFIG_SND
CONFIG_SND_PCM
CONFIG_SND_TIMER
CONFIG_SND_JACK
CONFIG_SND_SOC
```

These components provide the userspace audio interface.

Applications never communicate directly with APR or QDSP6.

Instead they access ALSA PCM devices.

---

# Bluetooth Support

Bluetooth audio requires both kernel drivers and userspace components.

Typical kernel options include:

```text
CONFIG_BT
CONFIG_BT_BREDR
CONFIG_BT_RFCOMM
CONFIG_BT_BNEP
CONFIG_BT_HIDP
```

For Qualcomm WCNSS platforms:

```text
CONFIG_BT_QCA
CONFIG_BT_QCOMSMD
```

These provide:

- Bluetooth controller support
- HCI transport over SMD
- Qualcomm initialization
- BR/EDR support

The Linux kernel provides the transport.

HFP Audio Gateway functionality is implemented in userspace by PipeWire.

---

# Bluetooth HCI

The MSM8916 Bluetooth controller appears as:

```text
hci0
```

Typical initialization sequence:

```text
Linux

↓

btqcomsmd

↓

btqca

↓

HCI Controller

↓

BlueZ
```

Kernel support ends at the HCI layer.

Higher-level Bluetooth profiles are handled by userspace.

---

# Audio Path

The complete kernel audio path is expected to be:

```text
Userspace

↓

ALSA

↓

MSM8916 Machine Driver

↓

QDSP6 Voice Driver

↓

APR

↓

QDSP6 DSP
```

The Bluetooth stack operates independently of the kernel audio drivers until audio reaches userspace.

---

# Optional Features

Some kernel options improve functionality but are not required for initial bring-up.

Examples include:

- DebugFS
- Dynamic Debug
- Tracepoints
- Additional Bluetooth profiles
- Wideband speech support
- Power management enhancements

These can be enabled after basic voice functionality has been verified.

---

# Debug Configuration

During development the following options are particularly useful.

```text
CONFIG_DEBUG_FS
CONFIG_DYNAMIC_DEBUG
CONFIG_FTRACE
CONFIG_FUNCTION_TRACER
CONFIG_KALLSYMS
CONFIG_KALLSYMS_ALL
```

These allow inspection of:

- Driver initialization
- APR messages
- Remoteproc lifecycle
- DSP failures
- ALSA registration

---

# Build Strategy

The recommended bring-up strategy is:

## Phase 1

Enable only the minimum required components:

- Remoteproc
- APR
- QDSP6
- ALSA
- Bluetooth

Verify:

- DSP boots
- Sound card appears

---

## Phase 2

Enable:

- Voice driver
- Machine driver
- Audio routing

Verify:

- Voice PCM devices
- APR communication

---

## Phase 3

Integrate userspace:

- ModemManager
- q6voiced
- PipeWire
- WirePlumber
- BlueZ

Verify:

- Bluetooth headset
- Voice call audio

---

# Current MSM8916 Status

Verified:

✓ Remoteproc support

✓ WCNSS firmware loading

✓ Bluetooth controller

✓ btqca driver

✓ btqcomsmd driver

✓ HCI controller

✓ Device discovery

✓ Pairing support

✓ Upstream QDSP6 framework

✓ Upstream APR framework

✓ Upstream MSM8916 machine driver

Under Investigation:

- ADSP firmware
- Voice driver initialization
- ALSA voice PCM registration
- DSP routing
- Voice session creation

---

# Key Observations

One of the most important discoveries during this project is that the upstream Linux kernel already contains nearly every driver required for MSM8916 voice support.

The remaining work focuses primarily on:

- Selecting the correct kernel configuration.
- Providing the required Device Tree.
- Integrating existing userspace components.
- Verifying end-to-end audio routing.

Very little completely new kernel development is expected to be required.

---

# References

- Linux Kernel Kconfig
- Linux ASoC Documentation
- Qualcomm APR Framework
- Linux Remoteproc Framework
- MSM8916 Upstream Drivers
- QDSP6 Audio Framework
