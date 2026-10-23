# Battery, charge limit and battery LEDs

Two boards carry a Li-ion pack: **mifib-v12** (4.2 V / 2150 mAh) and **mf800b**
(4.35 V / 2100 mAh).  Everything battery-related ships in one package,
[`packages/luci-app-battery`](../packages/luci-app-battery/), which only those
two boards install — the other five have no cell, so they get no LuCI page and
no services.

---

## What is in the package

| File | Role |
| :--- | :--- |
| `etc/config/battery` | the charge-limit settings (`enabled`, `limit`, `resume`) |
| `usr/sbin/battery-charge-limit` | the charge-limit monitor (procd service) |
| `usr/sbin/battery-led-monitor` | drives the green/red battery LED pair |
| `usr/sbin/battery-session` | records how long the unit has run on battery |
| `usr/sbin/battery-ctl` | CLI: show status, toggle charging, edit the limit |
| `luci/controller/battery.lua` + `view/battery/status.htm` | the **System → Battery** page |

Both init scripts are enabled automatically when the package is installed
(`default_postinst` enables everything under `/etc/init.d/`), so there is no
uci-defaults of its own.

---

## The LuCI page

**System → Battery** shows capacity, voltage, temperature, charge status and
health, the charge-limit state, and the current battery session (how long the
unit has been unplugged and how much capacity that cost).  The page is read-only
apart from the charge-limit switches; use `battery-ctl` or uci for the rest.

---

## Charge limit

Default policy: **stop charging at 85 %, resume at 80 %.**  Li-ion cells age
fastest when held at full charge, and these sticks are usually plugged in for
weeks at a time.

How it works — the PM8916 linear charger has no "stop charging but keep the
system powered" bit upstream, so patch **857** adds one:

* writing `0` to `charge_enabled` clears `CHG_EN` while `PSTG_EN` stays set,
  so the stick keeps running from VBUS and the pack is merely **held**;
* the monitor also floors `constant_charge_current` at its 90 mA minimum as a
  belt-and-braces measure;
* when capacity falls to `resume`, charging is re-enabled.

Config (`/etc/config/battery`):

```
config charge
	option enabled '1'     # master switch for the limit
	option limit   '85'    # stop charging at this %
	option resume  '80'    # resume at this % (hysteresis)
```

CLI:

```sh
battery-ctl                  # live status + current policy
battery-ctl limit 90         # change the stop level
battery-ctl resume 85        # change the resume level
battery-ctl enable|disable   # turn the monitor on/off
battery-ctl charge on|off    # one-shot nudge (the monitor re-applies its policy)
```

---

## Charge status tells the truth

Upstream reported `Charging` whenever VBUS was online, so a stick held at 85 %
showed "Charging" forever.  Patch **859** makes the fuel gauge query the
charger's `CHARGE_ENABLED` and report:

| State | Meaning |
| :---- | :------ |
| `Charging` | VBUS present **and** charging enabled |
| `Not charging` | VBUS present, pack held at the limit |
| `Discharging` | running from the pack |

---

## LEDs

`battery-led-monitor` polls every few seconds and drives `green:bat` /
`red:bat`:

| Pattern | Meaning |
| :------ | :------ |
| green solid | pack present, charge ≥ 15 % |
| red solid | charge < 15 %, discharging |
| red blink | charge < 15 %, charging |
| red fast blink | gauge present but capacity unreadable (driver/thermistor fault) |
| both off | no battery device — a board without a pack |

"No battery" and "battery broken" are deliberately different states: the first
is silent, the second blinks fast.

---

## sysfs and the kernel side

```sh
/sys/class/power_supply/pm8916-bms-vm/     # fuel gauge
    capacity  voltage_now  temp  status  health
/sys/class/power_supply/pm8916-lbc-chgr/   # charger
    online  charge_enabled  constant_charge_current
```

Those attributes exist because of the PM8916 power-supply series — see
[`Docs/Patches/README.md`](Patches/README.md) §1.8 (patches 854–859).  The
two that matter most in practice:

* **854** derives `capacity` from voltage (the VM-BMS has no coulomb counter),
  preferring the open-circuit voltage captured while suspended and falling
  back to the live voltage, so the number errs low rather than high;
* **858** fixes the temperature read — `iio_read_channel_processed()` returns
  `IIO_VAL_INT` (1) on success, and the original non-zero test discarded every
  good sample, leaving the gauge stuck at 25 °C.

---

## Troubleshooting

```sh
battery-ctl
logread | grep -E "battery-charge-limit|battery-led-monitor"
cat /sys/class/power_supply/pm8916-bms-vm/capacity
cat /sys/class/power_supply/pm8916-lbc-chgr/charge_enabled
```

| Symptom | Cause |
| :------ | :---- |
| no Battery page in LuCI | the package is not installed — only mifib-v12 and mf800b ship it |
| capacity jumps around | expected: it is derived from voltage, not a coulomb counter |
| temperature always 25 °C | patch 858 missing, or the board has no `batt-therm` channel |
| status says `Charging` at 85 % | patch 859 missing |
| red fast blink | the gauge is present but unreadable — check `dmesg` for BMS/VADC probe errors |