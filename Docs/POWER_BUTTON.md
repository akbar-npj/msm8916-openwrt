# The power button

Only **two** boards in this project have a power button:

| Board | Power key | Reset button |
| :---- | :-------- | :----------- |
| `mifib-v12` | `pm8941_pwrkey` (PMIC PON) | GPIO 102, also the recovery key |
| `mf800b` | `pm8941_pwrkey` (PMIC PON) | GPIO 102, also the recovery key |
| `hmuf02v5`, `uf02`, `ufi001b`, `ufi003`, `uz801` | none | GPIO 37 / GPIO 23 — plain reset, no power key |

On the buttonless boards `pwrkey-monitor` is not started and the handler is
never invoked; nothing else changes.

---

## What each press does

| Press | Result |
| :---- | :----- |
| short press (≤ 2 s) | nothing — anti-accidental |
| **two** short presses within 800 ms | clean `sync; reboot` |
| hold ≥ 4 s, on battery | LED blinks 6×, then `poweroff` |
| hold ≥ 4 s, on VBUS | clean `sync; reboot` (see below) |
| hold ≥ ~10–15 s | the PMIC hard-resets on its own — software cannot prevent it |

### Why a long press reboots when plugged in

A software power-off does not stay off while the stick is tethered: the PMIC
re-applies system power as soon as VBUS is present, and a key held long enough
reaches the PMIC hard reset.  Rather than racing the PMIC (and leaving users
with a stick that appears dead but is really rebooting), the handler checks
every `/sys/class/power_supply/*/online` and does a clean reboot when any reads
`1`.  On battery it powers off for real.

---

## How it works

```
pm8941_pwrkey  (evdev, /dev/input/eventN)
        │
        │  /sbin/pwrkey-monitor   (procd, START=94)
        ▼
/etc/rc.button/power            ACTION=released SEEN=<seconds> BUTTON=power
        │
        ├─ SEEN ≤ 2  → double-press window (800 ms) → reboot
        ├─ SEEN ≥ 4  → VBUS? reboot : blink + poweroff
        └─ otherwise → nothing
```

`pwrkey-monitor` exists because procd only dispatches `/etc/rc.button/*` for
**GPIO** keys reported by `kmod-gpio-button-hotplug`.  The PMIC power key is a
plain evdev device and never produces those uevents, so the monitor decodes it
and supplies the same `ACTION`/`SEEN`/`BUTTON` environment.

Implementation notes (all learned the hard way):

* the key is **`0x74`** (`KEY_POWER` = 116) inside an `EV_KEY` record; do not
  trust the `capabilities/key` bitmap on this kernel.
* `input_event` is 16 bytes here, but the reader takes 32-byte chunks and scans
  a sliding window, so a 24-byte struct would also work.
* it waits up to 60 s for the `pm8941_pwrkey` input node before giving up.  A
  bare `exit 1` at start-up makes procd respawn it in a tight loop, because the
  driver can probe after the service starts.

---

## Tuning

Thresholds are constants at the top of
[`etc/rc.button/power`](../msm89xx/base-files/etc/rc.button/power):

| Variable | Default | Meaning |
| :------- | :------ | :------ |
| `SHORT_MAX` | 2 | longest press still counted as "short" (seconds) |
| `LONG_HOLD` | 4 | shortest press that powers off / reboots (seconds) |
| `DBL_WINDOW` | 800 | double-press window (milliseconds) |

Keep `LONG_HOLD` below ~10 s: past that the PMIC resets the board itself and
the script never sees the release.

The LED used for the goodbye blink is the first existing of `red:wan`,
`green:wan`, `red:bat`, `green:bat`, `green:wlan` — so the same script works on
both button boards without a board check.

---

## Troubleshooting

```sh
logread | grep -E "pwrkey-monitor|power-button"
cat /sys/class/input/input*/name          # expect pm8941_pwrkey
/etc/init.d/pwrkey-monitor running && echo ok
```

| Symptom | Cause |
| :------ | :---- |
| nothing happens on any press | `pwrkey-monitor` not running, or no `pm8941_pwrkey` node (board has no power key) |
| long press powers off instead of rebooting | running on battery, not VBUS |
| board reboots ~10 s into a long press | PMIC hard reset, not the script |
| double press does not reboot | the two presses were more than 800 ms apart |