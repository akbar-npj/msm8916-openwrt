# USB Gadget Manager - UCI Configuration Guide

Universal USB Gadget Manager for OpenWrt using configfs interface.

## Quick Start

```bash
# View current configuration
uci show usbgadget

# Enable/start the service
/etc/init.d/usb-gadget enable
/etc/init.d/usb-gadget start

# Check status
/etc/init.d/usb-gadget status
```

## Configuration File

Location: `/etc/config/usbgadget`

## Upgrading the package

Installing a new version rebuilds the gadget, because the script is what the
gadget is made of — there is no way to change it under a host that is already
bound. The USB link drops for a few seconds and comes back with the new code.
If you are installing over that link, expect to be disconnected once.

`prerm` deliberately does not stop the gadget: it runs before the new files are
unpacked, so stopping there would drop the link with nothing to bring it back
until a reboot.

A unit that predates `net_mode` is moved to `net_mode='auto'` on upgrade, but
only when it is on the shipped NCM-only default. If `rndis` or `ecm` was
selected, that was a deliberate choice and it is left alone.

## Common Tasks

### Enable/Disable USB Gadget Mode

```bash
# Disable gadget (enable host mode - for USB storage, etc.)
uci set usbgadget.usb.enabled='0'
uci commit usbgadget
/etc/init.d/usb-gadget restart

# Enable gadget mode
uci set usbgadget.usb.enabled='1'
uci commit usbgadget
/etc/init.d/usb-gadget restart
```

### Network Functions

#### Choosing the transport: `net_mode`

```bash
uci set usbgadget.usb.net_mode='auto'   # auto | ncm | rndis | both
uci commit usbgadget
/etc/init.d/usb-gadget restart
```

| Value | Exposes | For |
| :---- | :------ | :-- |
| `auto` (default) | NCM, and RNDIS only if the host does not bind NCM | everything; NCM is the better transport wherever it is supported |
| `ncm` | NCM only | a known-good modern host |
| `rndis` | RNDIS only | Windows before 11, and Android hosts |
| `both` | both at once | testing; gives the host two adapters on the same subnet |

`net_mode` overrides the `ncm.enabled` / `rndis.enabled` booleans below. With
it unset, those booleans stand — which is what a unit that predates the option
keeps, so an upgrade never changes the transport under a user.

`auto` is decided per attach by `usb-gadget-probe`, and the decision is what
the host can actually bind rather than what its OS claims to be:

- The gadget is built with NCM.
- When the host has enumerated (`udc/state` is `configured`), the probe waits
  five seconds and reads the NCM netdev's `carrier`. `f_ncm` only raises
  carrier once the host sets the data interface to altsetting 1, which it
  cannot do without a driver.
- Carrier up: the host speaks NCM, nothing more happens.
- Carrier still down: the gadget is rebuilt with RNDIS, and the host sees one
  disconnect and reconnect about two seconds later.

One decision per attach. A real unplug clears it and the next host is probed
from NCM again, so a unit is never left stuck in RNDIS because of where it was
last plugged. The decision is a runtime file (`/var/run/usb-gadget/net_mode`),
not uci — `net_mode` stays `auto`, which is what "decide per attach" means.
`/etc/init.d/usb-gadget-probe` runs the probe, and only in `auto` mode.

A host with no NCM driver takes about seven seconds to get an address the
first time it is plugged in, and never again until it is unplugged. A host
that binds NCM sees no difference at all. If a host would have bound NCM but
took longer than five seconds to do it, the probe picks RNDIS — which Linux,
macOS and Windows all drive, so a wrong guess costs throughput, not
connectivity. Set `net_mode='ncm'` or `'rndis'` to skip the probe entirely.

Measured on hardware: **Windows 11 binds NCM directly** — the probe logs
`host bound NCM; keeping it` and nothing is rebuilt, so there is no delay at
all and no second adapter. The fallback exists for Windows 10 and Android
hosts, which have no NCM driver; a Windows 10 LTSC host was measured binding
the RNDIS function once it declares the `ef`/`04`/`01` association.

NCM has a driver in Linux, macOS and recent Windows; RNDIS is the fallback for
hosts without one. Enabling both is the only way to be certain a host finds
something it can bind, but a host that can bind both then shows two adapters
on one subnet, so it is not the default.

##### A Windows host that falls back to 169.254.x.x on the first plug

`auto` has one host-side cost, measured on a Windows 10 machine. The host binds
the first (NCM) enumeration, the probe rebuilds to RNDIS five seconds later, and
Windows keeps the cached adapter for the dead instance: its media state does not
refresh, it stops retrying DHCP after a handful of DISCOVERs and falls back to
APIPA. The device looks healthy throughout — `brif` already contains `usb0`,
`br-lan` already has its address, and the DHCP counters stay at zero — which is
what makes it look like a bridge problem.

Disabling and re-enabling the adapter fixes it instantly, because that hands the
stack a fresh media event. `net_mode='rndis'` avoids it entirely: one
enumeration, no rebuild. Use `rndis` on a host that behaves this way, and clear
the cached lease before re-testing, or the counters will show a renewal
(`discover=0 request=3 ack=3`) instead of a first-time attach. `auto` remains
the right default everywhere else, because it keeps NCM for Linux, macOS and
Windows 11.

#### RNDIS (Windows Ethernet)

```bash
# Enable RNDIS
uci set usbgadget.usb.net_mode='rndis'
uci commit usbgadget
/etc/init.d/usb-gadget restart
```

**Compatible with:** Windows (all versions, plug-and-play)

The RNDIS function declares the Microsoft interface association (`ef` / `04` /
`01`), so Windows matches `USB\Class_ef&SubClass_04&Prot_01` with its inbox
driver and needs no WCID negotiation. Those three attributes are parsed as at
most two hex digits — `0xef` stores 0 and `239` stores `0x23`, which silently
breaks the association. Write `ef`, `04`, `01` and nothing else.

#### The USB serial, and the MACs derived from it

The host keys the device on its serial. It is derived from `/etc/machine-id`,
falling back to the eMMC CID, and generated once into `/etc/machine-id` if
neither exists — never from the clock. A serial that changes between boots
makes Windows treat each plug as new hardware: the adapter walks up
`Ethernet 3` → `4` → `5` and asks to set up a network every time.

#### ECM (macOS/Linux Ethernet)

```bash
# Enable ECM
uci set usbgadget.ecm.enabled='1'
uci set usbgadget.rndis.enabled='0'  # Disable RNDIS
uci commit usbgadget
/etc/init.d/usb-gadget restart
```

**Compatible with:** macOS ≤10.14, Linux (all)

#### NCM (Modern High-Performance Ethernet)

```bash
# Enable NCM (best performance)
uci set usbgadget.usb.net_mode='ncm'
uci commit usbgadget
/etc/init.d/usb-gadget restart
```

**Compatible with:** Windows 11+, macOS ≥10.15, Linux (all)

**Note:** Network interfaces are automatically added to the LAN bridge.

### Serial Console (ACM)

#### Enable Serial Console with Login Shell

```bash
# Enable ACM with shell access
uci set usbgadget.acm.enabled='1'
uci set usbgadget.acm.shell='1'
uci commit usbgadget
/etc/init.d/usb-gadget restart

# Access from host:
# Linux/macOS: screen /dev/ttyACM0 115200
# Windows: Use PuTTY or similar on COMx port
```

The shell is supervised by `usb-gadget` itself: it starts once the UDC is
bound, comes back when the host disconnects, and is cleared by `stop`, so
`usb-gadget status` tells you whether a login is actually running rather than
just whether the node exists.

Use a terminal program that puts the port in raw mode (`screen`, `picocom`,
`minicom`). A plain reader such as `cat /dev/ttyACM0` leaves the host's line
discipline in its default canonical/echo state, and the host then echoes the
device's output straight back into it -- the console appears to eat its own
banner.

#### Enable Raw TTY (No Shell)

```bash
# Enable ACM without shell (raw serial port)
uci set usbgadget.acm.enabled='1'
uci set usbgadget.acm.shell='0'
uci commit usbgadget
/etc/init.d/usb-gadget restart
```

### Mass Storage (UMS)

```bash
# Enable USB mass storage
uci set usbgadget.ums.enabled='1'
uci set usbgadget.ums.image_path='/var/lib/usb-gadget/storage.img'
uci set usbgadget.ums.image_size='1G'
uci set usbgadget.ums.readonly='0'  # 0=read-write, 1=read-only
uci commit usbgadget
/etc/init.d/usb-gadget restart
```

**Note:** Image file is created automatically on first use.

### Device Information

```bash
# Change USB vendor/product name
uci set usbgadget.usb.manufacturer='MyCompany'
uci set usbgadget.usb.product='My USB Device'
uci commit usbgadget
/etc/init.d/usb-gadget restart

# Change USB IDs (advanced)
uci set usbgadget.usb.vendor_id='0x1234'
uci set usbgadget.usb.product_id='0x5678'
uci commit usbgadget
/etc/init.d/usb-gadget restart
```

### Custom Gadget Name

```bash
# Change internal gadget name (optional)
uci set usbgadget.usb.gadget_name='my-device'
uci commit usbgadget
/etc/init.d/usb-gadget restart
```

## Common Scenarios

### Scenario 1: Quick Network Access (Windows PC)

```bash
uci set usbgadget.usb.enabled='1'
uci set usbgadget.rndis.enabled='1'
uci commit usbgadget
/etc/init.d/usb-gadget restart
```

Connect USB cable. Windows will detect "Remote NDIS Compatible Device" and get DHCP from OpenWrt.

### Scenario 2: Maximum Performance (Modern OS)

```bash
uci set usbgadget.usb.enabled='1'
uci set usbgadget.ncm.enabled='1'
uci set usbgadget.rndis.enabled='0'
uci commit usbgadget
/etc/init.d/usb-gadget restart
```

### Scenario 3: Serial Console + Network

```bash
uci set usbgadget.usb.enabled='1'
uci set usbgadget.rndis.enabled='1'
uci set usbgadget.acm.enabled='1'
uci set usbgadget.acm.shell='1'
uci commit usbgadget
/etc/init.d/usb-gadget restart
```

Provides both network access and serial console on the same USB connection.

### Scenario 4: USB Storage + Network

```bash
uci set usbgadget.usb.enabled='1'
uci set usbgadget.rndis.enabled='1'
uci set usbgadget.ums.enabled='1'
uci set usbgadget.ums.image_size='2G'
uci commit usbgadget
/etc/init.d/usb-gadget restart
```

### Scenario 5: Switch to USB Host Mode

```bash
# Disable gadget to use USB port for storage, etc.
uci set usbgadget.usb.enabled='0'
uci commit usbgadget
/etc/init.d/usb-gadget restart
```

Now you can connect USB storage devices to the OpenWrt device.

## Service Management

```bash
# Start
/etc/init.d/usb-gadget start

# Stop
/etc/init.d/usb-gadget stop

# Restart (applies new configuration)
/etc/init.d/usb-gadget restart

# Status
/etc/init.d/usb-gadget status

# Enable on boot
/etc/init.d/usb-gadget enable

# Disable on boot
/etc/init.d/usb-gadget disable
```

## Viewing Configuration

```bash
# View all settings
uci show usbgadget

# View specific section
uci show usbgadget.usb
uci show usbgadget.rndis
uci show usbgadget.acm

# Export to file
uci export usbgadget > /tmp/usbgadget-backup.conf

# Import from file
uci import usbgadget < /tmp/usbgadget-backup.conf
```

## Troubleshooting

### Check if gadget is active

```bash
/etc/init.d/usb-gadget status
```

### View logs

```bash
logread | grep usb-gadget
```

### Check USB controller

```bash
ls /sys/class/udc/
# Should show: ci_hdrc.0 (MSM8916), 20980000.usb (RPi), etc.
```

### Verify network interface

```bash
ip link show
# Look for: usb0, usb1, etc.

# Check if added to bridge
uci show network.@device[0].ports
```

### The LAN bridge on a USB-only board

On these sticks `usb0` is the **only** port of `br-lan`, and the gadget is
built at `S99` while `network` runs at `S20`. netifd therefore builds the
bridge before `usb0` exists, and a bridge whose port list resolves to nothing
is not marked present at all — `br-lan` never appears:

```bash
ifstatus lan
# "up": false, "errors": [ { "subsystem": "interface", "code": "NO_DEVICE" } ]
```

The symptom is not "the USB NIC is missing from the LAN", it is **the LAN has
no device**: the host gets no DHCP and the AP, which shares the bridge, gives
none either. `usb-gadget` now sets `bridge_empty=1` on the bridge's device
section, which keeps the bridge present with no members, and after the reload
it re-attaches every enabled gadget interface — retrying with
`ubus call network.interface.lan up` if the bridge still has not appeared.

Check it with:

```bash
uci get network.@device[0].bridge_empty   # 1
ls /sys/class/net/br-lan/brif/            # usb0
```

There are three layers, in order, so no single one is load-bearing:

1. `bridge_empty=1` — the bridge exists from S20, with or without ports.
2. `ensure_bridge` — after each rebuild, re-attach and ask netifd for the LAN.
3. `/etc/hotplug.d/net/30-usb-gadget-bridge` — re-attach whenever `usb0`
   appears at any other moment (a manual restart, a probe rebuild, netifd
   recreating the bridge). It only attaches; it never calls `ifup`, so it
   cannot race the reload `usb-gadget` has just issued.

### Reset to defaults

```bash
rm /etc/config/usbgadget
/etc/init.d/usb-gadget restart
```

This will recreate the config with default values.

### Common Issues

**"No UDC device found"**
- USB gadget kernel support not enabled
- Install: 
  - `opkg install kmod-usb-gadget kmod-usb-configfs`
  - `apk add kmod-usb-gadget kmod-usb-configfs`

**"ttyGS0 not found"**
- ACM function takes ~10 seconds to initialize
- Check: `ls -l /dev/ttyGS0`

**Console prints its own banner back, prompts garbled**
- The port was opened without raw mode (`cat /dev/ttyACM0`, a script, an
  application that does not set termios). Use `screen`/`picocom`/`minicom`, or
  `stty -F /dev/ttyACM0 raw -echo` first.
- Two logins on one tty do the same thing. `usb-gadget stop` clears the tty,
  and `usb-gadget status` shows whether a shell is running.

**Network not working**
- Verify function is enabled: `uci show usbgadget.rndis.enabled`
- Check if interface exists: `ip link show usb0`
- Verify bridge: `brctl show br-lan`

## Hardware Compatibility

**Auto-detects USB controller on:**
- Raspberry Pi (all models with USB OTG)
- Qualcomm MSM8916/MSM89xx devices
- Orange Pi, Banana Pi
- Any device with USB gadget kernel support

**No hardware-specific configuration needed!**

## Advanced: Manual UDC Selection

If auto-detection fails:

```bash
# Check available UDC
ls /sys/class/udc/

# Set manually
uci set usbgadget.usb.udc_device='ci_hdrc.0'  # Example
uci commit usbgadget
/etc/init.d/usb-gadget restart
```

## Configuration Reference

### Device Section (`config device 'usb'`)

| Option | Default | Description |
|--------|---------|-------------|
| `enabled` | `1` | Enable/disable gadget (0=host mode, 1=device mode) |
| `gadget_name` | `g1` | Internal gadget name (any string) |
| `vendor_id` | `0x1d6b` | USB Vendor ID (Linux Foundation) |
| `product_id` | `0x0104` | USB Product ID (Multifunction Composite) |
| `device_version` | `0x0100` | Device version (BCD format) |
| `manufacturer` | `OpenWrt` | Manufacturer string shown to host |
| `product` | `USB Gadget` | Product string shown to host |
| `udc_device` | _(empty)_ | UDC device name (auto-detect if empty) |
| `net_mode` | `auto` | `auto`, `ncm`, `rndis` or `both` — which ethernet function to expose; overrides the `ncm`/`rndis` booleans |

### Function Sections

Each function has:
- `enabled`: `0` or `1`
- `description`: Human-readable description (not used by system)

#### RNDIS Function (`config function 'rndis'`)
No additional options.

#### ECM Function (`config function 'ecm'`)
No additional options.

#### NCM Function (`config function 'ncm'`)
No additional options.

#### ACM Function (`config function 'acm'`)
- `shell`: `0` or `1` - Enable login shell on serial console

#### UMS Function (`config function 'ums'`)
- `image_path`: Path to image file
- `image_size`: Size when creating (e.g., `512M`, `1G`, `2G`)
- `readonly`: `0` (read-write) or `1` (read-only)

## Tips

- **Multiple functions:** You can enable multiple functions simultaneously (e.g., RNDIS + ACM)
- **Network priority:** use `usb.net_mode` to pick one ethernet function (RNDIS, ECM or NCM). `both` is available but gives the host two adapters on one subnet.
- **Serial number:** from `/etc/machine-id`, else the eMMC CID, else generated once into `/etc/machine-id` — never from the clock
- **MAC addresses:** Auto-generated deterministically (same device = same MACs)
- **Storage persistence:** UMS image persists across reboots (in `/var` which is `/tmp`)

## Learn More

- OpenWrt USB Gadget: https://openwrt.org/docs/guide-user/hardware/usb_gadget
- Linux USB Gadget API: https://www.kernel.org/doc/html/latest/usb/gadget_configfs.html
- UCI Documentation: https://openwrt.org/docs/guide-user/base-system/uci
