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
# Only the failsafe-window blink is gated on this probe: it is a boot-time cue
# on the same lamp the battery monitor owns, and on a battery board diag.sh
# already blinks the battery pair to say "booting".  The no-SIM dark state and
# the AP+STA fast blink drive the signal and Wi-Fi pairs, which have nothing to
# do with the charge indication, so they run on every board.
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
