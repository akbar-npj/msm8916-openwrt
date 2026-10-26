#!/bin/sh
#
# Does this board have a battery pack?
#
# The status LEDs mean different things on the two families:
#
#   battery boards (mifib-v12, mf800b)  the lamps are a charge/status pair
#                                       (green:bat / red:bat) next to the
#                                       signal and Wi-Fi pairs, and
#                                       luci-app-battery owns the indication
#   batteryless dongles (HMU05, UZ801,  the lamps are the only feedback a
#   UF02, UFI001B, UFI003, ...)         headless box has
#
# The cues written for the dongles -- the failsafe-window blink, the no-SIM
# dark state and the AP+STA fast blink -- therefore only apply when this probe
# says "no battery": on a battery board they would fight the charge
# indication the user is watching.
#
# Two probes, because the two answers become available at different times: the
# luci-app-battery marker is on disk from preinit onwards, while the fuel gauge
# only shows up once its driver has probed.
#
is_battery_board()
{
	local p

	[ -x /usr/sbin/battery-led-monitor ] && return 0

	for p in /sys/class/power_supply/*/type; do
		[ -r "$p" ] || continue
		[ "$(cat "$p" 2>/dev/null)" = "Battery" ] && return 0
	done

	return 1
}
