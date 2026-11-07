#!/bin/sh
#
# Shared LED role/pattern resolver for the msm89xx monitors.
#
# Roles are logical (wifi, wan, wan_fault, ...) and map to a physical
# /sys/class/leds label through /etc/config/leds.  A role left at 'auto' keeps
# the board default, so an untouched board behaves exactly as before this
# library existed.
#
# Sourced by wifi-led-monitor, modem-led-monitor, battery-led-monitor and the
# ieee80211/iface hotplug helpers.  Everything here is POSIX sh; no external
# tools beyond uci and cat.

# Overridable so the hardware-free test suite can point at a fake tree.
LEDS_SYSFS="${LEDS_SYSFS:-/sys/class/leds}"
BOARD_NAME_FILE="${BOARD_NAME_FILE:-/tmp/sysinfo/board_name}"

# Resolve a role to a lamp path, honouring leds.<role>.lamp.
#
# Echoes /sys/class/leds/<label> and returns 0, or returns 1 when the role has
# no lamp on this board (for example the blue 'data' lamp on HMUF02-V5).
led_role_lamp()
{
	local role="$1" v

	v="$(uci -q get "leds.${role}.lamp" 2>/dev/null)"

	case "$v" in
		""|auto)
			led_role_default "$role"
			;;
		*)
			if [ -e "$LEDS_SYSFS/$v/trigger" ]; then
				echo "$LEDS_SYSFS/$v"
			else
				# A label that is not present (yet, or on this board)
				# must never leave the role unlit: fall back to the
				# board default.
				led_role_default "$role"
			fi
			;;
	esac
}

# Board-default lamp for a role.  These are the pick_led chains the monitors
# used before the config existed.
led_role_default()
{
	case "$1" in
		wifi)
			case "$(cat "$BOARD_NAME_FILE" 2>/dev/null)" in
				*hmuf02v5*) led_pick blue:wlan blue:wan ;;
				*)          led_pick green:wlan green:wan ;;
			esac
			;;
		wifi_fault)
			led_pick red:wlan
			;;
		wan)
			case "$(cat "$BOARD_NAME_FILE" 2>/dev/null)" in
				*hmuf02v5*) led_pick green:wan green:wlan ;;
				*)          led_pick green:wan ;;
			esac
			;;
		wan_fault)
			led_pick red:wan 4g_type red:power
			;;
		data)
			case "$(cat "$BOARD_NAME_FILE" 2>/dev/null)" in
				*hmuf02v5*) return 1 ;;
				*)          led_pick blue:wan blue:wlan ;;
			esac
			;;
		bat_green)
			led_pick green:bat green:charging
			;;
		bat_red)
			led_pick red:bat red:charging
			;;
		*)
			return 1
			;;
	esac
}

# First existing lamp from a list of labels.
led_pick()
{
	local n

	for n in "$@"; do
		[ -e "$LEDS_SYSFS/$n/trigger" ] && { echo "$LEDS_SYSFS/$n"; return 0; }
	done

	return 1
}

# Configured pattern for a role+state, or $3 when the config has none.
led_role_spec()
{
	local role="$1" state="$2" def="$3" spec

	spec="$(uci -q get "leds.${role}.${state}" 2>/dev/null)"
	printf '%s\n' "${spec:-$def}"
}

# Apply a pattern spec to a lamp path.  Empty lamp = no-op.
#
#   off | on | blink:ON_MS:OFF_MS
#
# Re-writing an identical timer is skipped: writing delay_on while the timer
# runs restarts it, and the lamp visibly hiccups.
led_apply()
{
	local led="$1" spec="$2" on off trig

	[ -n "$led" ] && [ -e "$led/trigger" ] || return 0

	case "$spec" in
		""|off)
			echo none > "$led/trigger" 2>/dev/null
			echo 0 > "$led/brightness" 2>/dev/null
			;;
		on)
			echo none > "$led/trigger" 2>/dev/null
			echo 1 > "$led/brightness" 2>/dev/null
			;;
		blink:*)
			on="${spec#blink:}"
			on="${on%%:*}"
			off="${spec##*:}"
			on="${on:-500}"
			off="${off:-500}"

			trig="$(cat "$led/trigger" 2>/dev/null)"
			case "$trig" in
				*"[timer]"*)
					if [ "$(cat "$led/delay_on" 2>/dev/null)" = "$on" ] &&
					   [ "$(cat "$led/delay_off" 2>/dev/null)" = "$off" ]; then
						return 0
					fi
					;;
			esac

			echo timer > "$led/trigger" 2>/dev/null
			echo "$on" > "$led/delay_on" 2>/dev/null
			echo "$off" > "$led/delay_off" 2>/dev/null
			;;
		*)
			# Unknown spec: leave the lamp alone rather than guess.
			return 1
			;;
	esac
}
