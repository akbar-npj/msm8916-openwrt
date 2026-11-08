# LED Setup — roles, colours and patterns

The LED monitors no longer decide which colour is which from `board_name`.
They read a role table from `/etc/config/leds`, so one board can have a green
Wi-Fi lamp and the next a blue one, and both still behave as designed.

## Why this exists

Two units can report the same `board_name` (`hmuf02v5`) yet have their green and
blue lamps wired the other way round. The old scripts hardcoded "HMUF02-V5 =
blue Wi-Fi", so on the swapped unit the Wi-Fi monitor drove the LTE lamp and
vice versa. There is no software-visible difference between the units, so the
mapping has to be confirmed once, by eye, on each unit.

## Roles

| Role | Drives |
|---|---|
| `wifi` | Wi-Fi status lamp (AP ready / client / AP+STA / down) |
| `wifi_fault` | red half of the Wi-Fi pair (battery boards) |
| `wan` | LTE/WAN signal lamp (idle / active / searching / no SIM) |
| `wan_fault` | red modem/fault lamp |
| `data` | blue data-activity lamp (four-lamp boards; empty on HMUF02-V5) |
| `bat_green` | green half of the battery pair |
| `bat_red` | red half of the battery pair |

Each role has `option lamp` in `/etc/config/leds`. `auto` means "use the board
default"; anything else is a label under `/sys/class/leds` — including
non-RGB colours such as `yellow:led1` or `white:status`.

## Patterns

Every role carries one pattern per scenario:

```
off                lamp off
on                 lamp solid
blink:ON:OFF       kernel timer blink, milliseconds
```

Defaults reproduce the behaviour the monitors had before this file existed, so
an untouched board is unchanged. Examples:

| Scenario | Default |
|---|---|
| `wifi.ap_sta` (repeater active) | `blink:100:100` |
| `wifi.client` (a client joined the AP) | `blink:250:250` |
| `wan.active` (registered + traffic) | `blink:250:250` |
| `wan_fault.search` | `blink:500:500` |
| `bat_green.charging` | `blink:1000:1000` |
| `bat_red.fault` | `blink:150:150` |

Retuning a board is a config change, not a rebuild — no hotfix needed.

## The CLI: `led-probe`

Run it over SSH on the unit whose lamps you want to identify.

```sh
led-probe                       # interactive wizard
led-probe --list                # indicator lamp labels
led-probe --show                # current role/pattern mapping
led-probe --blink blue:wan 3    # light one lamp for 3 s
led-probe --set wifi=green:wlan wan=blue:wan
led-probe --set wifi.client=blink:500:500
led-probe --reset               # every role lamp back to 'auto'
```

The wizard stops the LED monitors while it works (so their 2-second poll does
not overwrite the identification blink), blinks each lamp for three seconds,
and asks what it does:

```
[w]ifi [l]te/wan [d]ata [f]ault [x]wifi-fault [b]attery-green [r]ed-pair [s]kip
```

It writes `/etc/config/leds`, commits it, and restarts the monitors.

`--show` prints what is **in effect**, not just what is written down, so an
untouched board still reports a full table:

```
role    wifi       green:wlan  green:wlan     # configured / lamp in use
role    wan_fault  auto        red:power      # 'auto' resolved for this board
pattern wifi       ap_sta      blink:100:100 default
pattern wan        active      blink:250:250 set
```

The fourth column of a `role` line is the lamp the monitors actually drive
(empty when the board has no such lamp); the last column of a `pattern` line is
`set` when `/etc/config/leds` overrides it and `default` when the built-in
value is used. The built-in table lives in `leds.sh` and is the only copy —
the monitors read it through `led_role_spec`.

## Confirming a swapped unit

The units cannot be told apart in software, so identify them by eye once:

```sh
led-probe --blink blue:wan 3     # which colour lights up?
led-probe --blink green:wlan 3
```

Then record what each one does. For a unit whose **green** lamp is Wi-Fi and
whose **blue** lamp is LTE:

```sh
led-probe --set wifi=green:wlan wan=blue:wan
```

Check it by joining the unit to a repeater uplink: the green lamp must fast
blink (100/100) and the blue lamp must follow the LTE signal. `led-probe
--reset` puts every role back on the board default.

`--set` validates what it is given:

- a pattern must be `off`, `on` or `blink:ON:OFF` in milliseconds; anything
  else is rejected and nothing is written;
- a lamp label that does not exist is still saved, but a warning is printed —
  the role then falls back to the board default, so a typo cannot leave a lamp
  dark;
- an empty pattern value (`led-probe --set wifi.client=`) deletes the override
  and restores the built-in pattern.

## The LuCI page

**System → LED Setup** lists every role with a lamp dropdown and a **Blink**
button, plus a pattern dropdown per scenario. The battery pair is only offered
when the board reports a battery in `/sys/class/power_supply`, so the two
mains-only boards do not show a battery section at all (`led-probe --caps`
prints the same answer). It opens on the configuration in
effect: the `auto` entry names the board default it resolves to, and each
pattern dropdown starts with `default (…)`, showing the built-in timing that
would be used. Pick `off`, `on` or one of the blink presets to pin a timing, or
`custom…` to type `blink:ON_MS:OFF_MS` yourself; a timing that is not a preset
is kept as its own `(custom)` entry rather than being reset on save. Leave
everything alone and nothing changes; **Save & apply** only writes the fields
you edited and restarts the monitors. Selecting `default (…)` again drops the
override. **Reset lamps to auto** clears the lamp overrides.

## Checking the result

```sh
led-probe --show
logread | grep -E 'wifi-led-monitor|modem-led-monitor'
cat /sys/class/leds/<label>/delay_on /sys/class/leds/<label>/delay_off
```

On a repeater, `wifi.ap_sta` should fast-blink the lamp you assigned to `wifi`
(100 ms on / 100 ms off), not the LTE lamp.

## Files

- `/etc/config/leds` — role table and patterns (a conffile; edits survive upgrades).
- `/usr/share/msm89xx/leds.sh` — shared resolver used by the monitors.
- `/usr/sbin/led-probe` — the CLI above.
- `luci-app-led-setup` — the **System → LED Setup** page.

Every consumer goes through the resolver, including the services' `stop`
paths: stopping `wifi-led-monitor` turns off the lamp assigned to `wifi`, not a
hardcoded colour.
