# Failsafe Mode, Factory Reset, and Recovery

These MSM8916 USB dongles have **no Ethernet port and no external serial
header**. The only ways to talk to them are the USB gadget they present to the
host (a CDC-NCM network interface plus a CDC-ACM serial console) and the
Wi-Fi/LTE radios. That makes recovery hard: if a bad config takes the network
down, a normal boot is unreachable.

This document covers the recovery paths built into the firmware:

1. **Failsafe mode** — a minimal, pre-init shell reachable at `192.168.10.1`
   (USB NIC) or `/dev/ttyACM0` (USB serial), used to repair a broken config.
2. **Factory reset** — from a running system, a **5-second hold** of the
   Restart button wipes the overlay.
3. **EDL / Fastboot** — last-resort reflashing from the host.

---

## 1. Failsafe mode

Failsafe mode is OpenWrt's standard pre-init rescue shell. It boots the kernel
and a minimal userland from the read-only squashfs, **before** any config is
applied, and lets you fix things (reset the network config, mount the overlay,
run `firstboot`, etc.). It is the same mechanism OpenWrt routers use — this
firmware only adds the trigger and the reachability for a headless dongle.

### How to enter it

**Option A — Restart button (recommended).** Power the dongle (or reboot it) and
**press the Restart button once, any time during the first 7 seconds** of boot.

- A **single short press is enough** — no hold required. The press only has to
  land inside the 7-second window.
- Watch the **LED**: once failsafe is entered it **fast-flashes** (~12 Hz). It
  uses the **red** LED where the board has one, otherwise the **green** LED
  (UZ801 v3), otherwise any LED.

> **UZ801 status:** GPIO23 responds to the button and was observed high while
> idle; its DTS now uses active-low/pull-up. The complete failsafe flow is not
> working yet on UZ801, so do not rely on the button for recovery there.
**Option B — serial console.** If you have a UART connected to the kernel
console (`ttyMSM0`, 115200), press **`f`** then Enter during the same window.

> **Boot delay:** the failsafe window is 7 s, so every boot waits ~3 s longer
> than the stock 4 s before continuing. This is the trade-off for a usable
> button trigger.

### How to reach it

Failsafe brings up a minimal USB gadget by itself (NCM + ACM), so the host sees:

- a **USB network interface** (CDC-NCM) configured at **`192.168.10.1/24`**, and
- a **USB serial port** — `/dev/ttyACM0` on Linux, 115200 baud.

**SSH** (no password):

```bash
ssh root@192.168.10.1
```

**Serial console:**

```bash
screen /dev/ttyACM0 115200      # or: picocom -b 115200 /dev/ttyACM0
```

> The failsafe address is deliberately `192.168.10.1`, different from the normal
> LAN address `192.168.8.1`, so a half-broken network config cannot collide with
> it. Failsafe runs a DHCP server on the gadget link (dnsmasq, pool
> `192.168.10.10`-`.50`), so the host normally picks up an address by itself. If
> it does not, run `ip addr add 192.168.10.2/24 dev <usb-nic>`.

> **No WiFi in failsafe — by design.** Failsafe is a *pre-init* environment:
> it runs from the read-only squashfs **before** the wireless config is applied,
> and wcn36xx needs its firmware (which lives on the overlay) plus a
> `hostapd`/`wpa_supplicant` daemon to be useful.  The **recovery image** is
> the one that offers WiFi — an AP *and* an optional upstream station, so a
> rescued box can be driven from your own LAN.  Keeping failsafe USB-only also
> keeps its 7 s window and its "can never wipe the overlay" property intact.
> See [`Docs/RECOVERY/README.md`](../RECOVERY/README.md) §5.

### What to do once inside

The root filesystem is mounted read-only and the overlay is **not** mounted yet.
Typical recovery steps:

```bash
# Mount the writable overlay so your edits persist:
mount_root

# Inspect / repair the config, e.g.:
vi /etc/config/network
vi /etc/config/wireless

# Reset to defaults (wipes the overlay):
firstboot && reboot

# Or just reboot out of failsafe when done:
reboot
```

Notes:

- Without `mount_root`, edits live in RAM and are lost on reboot.
- `firstboot` removes the overlay contents; the read-only squashfs (the firmware
  itself) is untouched.
- If a `sysupgrade` image is staged in `/tmp`, failsafe detects it and proceeds
  with the upgrade automatically instead of dropping to the shell.

### If failsafe does not come up

- **No new USB device on the host?** Try a different USB cable/port; the NCM
  gadget needs USB data lines, not a charge-only cable.
- **Button did nothing?** The 7-second window starts during preinit, not
  at power-on. HMU05's button-driven failsafe flow is verified.
  UZ801's button response and idle level are verified, but its failsafe flow is
  not working yet. UFI001B, UFI003, UF02 and MF800B buttons are declared active-high/pull-down but not yet confirmed on hardware —
  if yours does nothing, use the serial `f` key instead and report it.
- **Everything is dead?** Fall back to EDL (section 3).

---

## 2. Factory reset (from a running system)

Once the system is up, a **5-second hold** of the Restart button performs a
factory reset and reboots:

1. Hold the Restart button for **5 seconds**.
2. Release. The device runs `factoryreset` and reboots with a clean overlay.

A **short press does nothing** — the button does not reboot the device on a
quick tap (unlike stock OpenWrt), precisely because the button is the only
physical control and an accidental reboot on a headless box is costly.

> The factory reset only wipes the **overlay** (config and packages you
> installed). It never touches the modem firmware, the NV partitions, or the
> bootloader.

---

## 3. EDL / Fastboot (last resort)

If the system does not boot and failsafe is unreachable, reflash from the host.

From a running OpenWrt (if you can still reach it):

```bash
reboot-edl            # Qualcomm EDL  (host sees 05c6:9008)
reboot-bootloader     # Qualcomm Fastboot (host: fastboot devices)
```

Then use the flashing tooling in [`Docs/EDL/`](../EDL/) and the project
[`flash.sh`](../../msm89xx/image/flash.sh). See the main
[README → Recovery](../../README.md#-recovery-and-reboot-modes) for details.

> ⚠ **A bad migrate flash can corrupt the modem NV** (`modemst1`/`modemst2`),
> leaving the IMEI at `000000000000000`. If you use EDL, restore the seven
> essential partitions (`fsc fsg modem modemst1 modemst2 persist sec`) from a
> known-good dump.

---

## Reference — board specifics

| Board   | Restart button GPIO | Polarity / bias        | Status LED(s)                    | Failsafe LED |
|---------|---------------------|------------------------|----------------------------------|--------------|
| HMU05   | `tlmm 37`           | active-high, pull-down | `red:power`                      | `red:power`  |
| UFI001B | `tlmm 37`           | active-high, pull-down | `red:power`                      | `red:power`  |
| UFI003  | `tlmm 37`           | active-high, pull-down | `red:power`                      | `red:power`  |
| UF02    | `tlmm 23`           | active-high, pull-down | `red`                            | `red`        |
| UZ801   | `tlmm 23`           | active-low, pull-up    | `green:wlan` (no red on some)    | `green:wlan` |
| MF800B  | `tlmm 102`          | active-high, pull-down | `red:wlan`, `red:wan`, `red:charging` | all red LEDs |

> ⚠ **The button's polarity and bias must match the hardware, or the button is
> completely inert** (no edge ever reaches the driver, so no uevent, so no
> failsafe). The verified HMU05 behaviour is **`GPIO_ACTIVE_HIGH` +
> `bias-pull-down`** (idle low, press = high): its pinhole Restart button pulls
> GPIO37 **up to VCC** when pressed, and the stock HMU05 Android DTS agrees
> (`key_freset`, flags `0x00`). The earlier active-low/pull-up declaration made
> GPIO37 idle high, so a press produced no edge.
>
> **Only HMU05 is verified live (2026-10-10).** UFI001B, UFI003, UF02 and
> MF800B are declared active-high/pull-down but unverified. **UZ801 is the
> exception — it is active-low/pull-up**: GPIO23 idles high, so an active-high
> declaration made the driver read the button as permanently pressed and a
> short press triggered a factory reset; reverted 2026-10-16 (PR #11). If a
> board's button turns out to be wired the other way, the fix is to revert that
> board's patch (`GPIO_ACTIVE_HIGH` ↔ `GPIO_ACTIVE_LOW`, `bias-pull-down` ↔
> `bias-pull-up`) and report it. If a button does nothing, check the `bias` and
> polarity against the board's stock DTS.

> The failsafe LED fast-flashes on **red** where available, otherwise on
> **green** (UZ801 v3 units without a red LED), otherwise on any LED — so the
> indication works on every board.

The button is the DT `button_restart` node (`KEY_RESTART` → button name
`reset`). It is driven by `kmod-gpio-button-hotplug`, which emits button
uevents; the preinit hotplug daemon turns a press during the boot window into
`/tmp/failsafe_button`, and the running system runs `/etc/rc.button/reset` for
the factory-reset hold.

## Implementation notes

- Failsafe core (`lib/preinit/30_failsafe_wait`, `40_run_failsafe_hook`,
  `99_10_failsafe_login`) is stock OpenWrt and compiled in.
- `CONFIG_TARGET_PREINIT_TIMEOUT=7` sets the window; `CONFIG_TARGET_PREINIT_IP`
  etc. set the failsafe address (per-board `diffconfigs/`). These symbols are
  **gated**: `CONFIG_IMAGEOPT=y` makes `CONFIG_PREINITOPT` visible, and
  `CONFIG_PREINITOPT=y` makes the `TARGET_PREINIT_*` symbols visible. Both must
  be set, or `make defconfig` resets them to the defaults (4 s /
  `192.168.1.1`). Verify in the built image's `lib/preinit/00_preinit.conf`.
- `msm89xx/base-files/lib/preinit/35_failsafe_usb` brings up the preinit USB
  gadget (configfs `g1`: `ncm.usb0` + `acm.GS0`), assigns the address, blinks
  the LED (red → green → any fallback), and spawns the `ttyGS0` login shell. It
  runs on the `failsafe` hook only.
- `msm89xx/base-files/etc/rc.button/reset` is the post-boot factory-reset
  handler (5 s hold; short press ignored).
- The in-kernel `gpio_keys` driver (`CONFIG_KEYBOARD_GPIO`) is **disabled** so
  `kmod-gpio-button-hotplug` can bind the `gpio-keys` DT node; otherwise the
  built-in driver claims it first and the button produces no hotplug uevents.

### Post-mortem: why a 5 s hold wipes the overlay, and why failsafe cannot

Two independent button paths exist, and they look identical from the outside:

| boot path | what runs | overlay mounted? | can it wipe? |
|---|---|---|---|
| press inside the 7 s window | `30_failsafe_wait` sets `FAILSAFE` -> `40_run_failsafe_hook` **blocks** | **no** — preinit never reaches `80_mount_root` | **never** |
| press missed the window (normal boot) | `/etc/rc.button/reset` via `button-hotplug` | **yes** (mounted ~1-2 s in) | **yes, on release** |

`40_run_failsafe_hook` parks in `while [ ! -e /tmp/sysupgrade ]; do lock -w /tmp/.failsafe; done`,
which is *before* `80_mount_root` in the preinit chain.  So inside failsafe
`/overlay` is genuinely unmounted, `rc.button/reset` sees an empty
`OVERLAY` variable and refuses to act.  A press that reaches failsafe is safe.

The trap is the other row.  `rc.button/reset` evaluates `released`
**independently** of `pressed`:

```sh
released)
	if [ "$SEEN" -ge "$HOLD_SECONDS" ] 2>/dev/null && [ -n "$OVERLAY" ]; then
		factoryreset -y && reboot &
	fi
```

There is no state carried from the `pressed` event, so a press that started
*before* `/overlay` existed still wipes the box if it is released at >= 5 s
after `button-hotplug` probed.  Nothing prompts, nothing prints on the console,
and the reboot happens in the background — on a loaded box (e.g. while the WCNSS
firmware is crash-looping) it can take several seconds, so the unit looks like it
"did not restart".

**Observed consequence (2026-10-24):** a 5 s hold during boot that missed the
7 s window factory-reset the unit.  The overlay came back as defaults (45 MB,
`.fs_state -> 2`), `/etc/config/wireless` was gone, the radio came up as
`OpenWrt` on channel 1, and the box was reachable only after re-creating the
Wi-Fi config by hand.

**Rule of thumb:** on these dongles the Restart button has *two* destructive
meanings that differ only by timing.  Tap it once (< 1 s) inside the first 7 s
for failsafe.  Never hold it during boot unless you intend a factory reset.


> **Serial console node:** preinit's `/dev` is a 512k tmpfs with no devtmpfs
> population, so the kernel creates no node for the ACM function even though the
> host enumerates `/dev/ttyACM0` normally. `35_failsafe_usb` therefore creates
> it itself (`mknod /dev/ttyGS0 c <major> <minor>` from
> `/sys/class/tty/ttyGS0/dev`) before spawning the login shell. Verified
> 2026-10-24: node removed, `fs_ensure_tty` recreates it (242:0), the host reads
> the BusyBox banner off `/dev/ttyACM0`.

