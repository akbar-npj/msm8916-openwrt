# Bluetooth Support on MSM8916

## Overview

Bluetooth provides the external audio interface for the MSM8916 OpenWrt voice project.

Unlike smartphones, the target hardware (UFI001B LTE modem) does not contain:

- Speaker
- Microphone
- Audio codec
- Headset connector

Therefore, Bluetooth is the primary mechanism for delivering voice audio to the user.

For cellular voice support, Bluetooth is expected to operate as a Hands-Free Audio Gateway (HFP AG), allowing a Bluetooth headset to function as the microphone and speaker during phone calls.

---

# MSM8916 Bluetooth Hardware

The Qualcomm WCNSS subsystem provides both Wi-Fi and Bluetooth functionality.

A simplified architecture is shown below.

```text
                MSM8916 SoC

      +---------------------------+
      |      Cortex-A53 CPU       |
      |        Linux/OpenWrt      |
      +-------------+-------------+
                    |
              Shared Memory
                    |
      +-------------+-------------+
      |                           |
      ▼                           ▼
  Modem DSP                  WCNSS Firmware
                                   │
                                   ▼
                         Bluetooth Controller
                                   │
                             HCI over SMD
                                   │
                                   ▼
                                BlueZ
```

Unlike USB Bluetooth adapters, the Bluetooth controller is integrated into the Qualcomm WCNSS subsystem and communicates with Linux using Qualcomm Shared Memory Driver (SMD).

---

# Bluetooth Stack

The Linux Bluetooth stack consists of several layers.

```text
Applications

      │

PipeWire

      │

BlueZ

      │

Bluetooth HCI

      │

btqca

      │

btqcomsmd

      │

WCNSS Firmware

      │

Bluetooth Radio
```

Each layer performs a different role.

---

# btqcomsmd

The `btqcomsmd` driver implements the transport between Linux and the Qualcomm Bluetooth controller.

Responsibilities include:

- SMD communication
- Packet transport
- Controller startup
- HCI packet delivery

This driver is specific to Qualcomm platforms using Shared Memory Driver (SMD).

---

# btqca

The `btqca` driver provides Qualcomm-specific Bluetooth support.

Responsibilities include:

- Controller initialization
- Firmware configuration
- Vendor HCI commands
- Device-specific workarounds

This driver operates above the SMD transport.

---

# HCI Layer

The Bluetooth controller is exposed to Linux as an HCI device.

Example:

```text
hci0
```

The HCI layer is responsible for:

- Device discovery
- Pairing
- Authentication
- Encryption
- ACL links
- SCO links

Higher Bluetooth profiles operate above HCI.

---

# BlueZ

BlueZ is the official Bluetooth stack for Linux.

Responsibilities include:

- Device discovery
- Pairing
- Bonding
- Security
- Profile management
- D-Bus API

BlueZ does not perform audio processing.

Instead, it exposes Bluetooth functionality to userspace applications such as PipeWire.

---

# Bluetooth Profiles

Bluetooth functionality is divided into profiles.

Examples include:

| Profile | Purpose |
|----------|---------|
| A2DP | High-quality audio streaming |
| AVRCP | Media control |
| HID | Keyboard and mouse |
| PAN | Personal Area Networking |
| HFP | Hands-Free calling |
| HSP | Headset Profile |
| PBAP | Phonebook Access |
| MAP | Message Access |

For this project, the most important profile is:

**Hands-Free Profile (HFP)**

---

# HCI Transport

Communication between Linux and the Bluetooth controller follows this path.

```text
BlueZ

   │

HCI

   │

btqca

   │

btqcomsmd

   │

WCNSS Firmware

   │

Bluetooth Controller
```

Linux communicates only with the HCI interface.

The controller firmware handles radio communication.

---

# Bluetooth Bring-up

The basic initialization sequence is:

```text
Kernel boots

↓

WCNSS firmware starts

↓

btqcomsmd probes

↓

btqca initializes controller

↓

hci0 registered

↓

bluetoothd starts

↓

Bluetooth ready
```

Successful completion of this sequence indicates a functioning Bluetooth controller.

---

# Verified Functionality

The following functionality has been verified on the UFI001B platform.

## Controller

Verified:

- Bluetooth controller detected
- hci0 created
- Qualcomm manufacturer identified
- HCI version reported correctly

---

## Discovery

Verified:

- Device scanning
- Nearby device discovery
- RSSI reporting
- Device information

Examples discovered:

- Android phones
- Bluetooth headsets
- Printers
- Other Bluetooth peripherals

---

## Pairing

Verified:

- Pairing with Android phones
- Secure Simple Pairing
- Device alias configuration
- Controller alias configuration

Current controller name:

```text
ufi001b
```

---

## Kernel Drivers

Verified:

```text
btqcomsmd
btqca
```

Both drivers are successfully loaded.

---

## WCNSS

Verified:

- WCNSS firmware loads successfully.
- Bluetooth controller initializes after firmware startup.
- Integrated Wi-Fi and Bluetooth coexist correctly.

---

# Current Limitations

The following items are still under investigation.

- Bluetooth HFP Audio Gateway
- SCO audio transport
- Wideband speech
- PipeWire integration
- Voice routing
- Bluetooth headset call audio

Although Bluetooth connectivity is functional, voice calling requires additional userspace components described in later chapters.

---

# Relationship with Voice Calling

Bluetooth itself does not manage cellular calls.

Instead, Bluetooth is responsible only for transporting audio and call-control messages between the Linux system and the headset.

The overall architecture is:

```text
Cellular Network

      │

Qualcomm Modem

      │

ModemManager

      │

PipeWire

      │

BlueZ

      │

Bluetooth HFP

      │

Bluetooth Headset
```

Bluetooth therefore represents only one layer of the complete voice stack.

---

# Debugging

Useful commands include:

```bash
hciconfig -a

bluetoothctl

btmgmt

lsmod | grep bt

dmesg | grep -i bluetooth

dmesg | grep -i wcnss
```

Useful information includes:

- HCI version
- Manufacturer
- Controller address
- Driver status
- Firmware loading
- Discovery state

---

# Bring-up Checklist

A healthy Bluetooth subsystem should satisfy the following:

- WCNSS firmware loads.
- btqcomsmd initializes.
- btqca initializes.
- hci0 appears.
- bluetoothd starts.
- Device discovery works.
- Pairing succeeds.
- Controller alias can be changed.
- Bluetooth headset is detected.

Only after these requirements are satisfied can Bluetooth Hands-Free Profile (HFP) integration be investigated.

---

# References

- BlueZ Project
- Linux Bluetooth Subsystem
- Qualcomm WCNSS
- Bluetooth Core Specification
- Linux HCI Documentation
