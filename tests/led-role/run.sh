#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-2.0-only
#
# Hardware-free checks for the LED role/pattern resolver.
#
#   tests/led-role/run.sh [--self-check]
#
# Builds a fake /sys/class/leds tree and a fake uci, then asserts:
#   - the board defaults (hmuf02v5 vs mifib) resolve to the right labels;
#   - a UCI override wins, and an unknown label falls back to the default;
#   - off/on/blink:ON:OFF write the expected sysfs values;
#   - an empty lamp is a no-op;
#   - a per-scenario pattern override wins over the built-in default, and the
#     built-in table reports the same values the monitors used to hardcode.
#
# --self-check mutates the library four ways and requires every mutant to be
# caught, so a check that silently stopped testing anything turns red.
set -u

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
LIB="$ROOT/packages/msm89xx-scripts/root/usr/share/msm89xx/leds.sh"

SELF_CHECK=0
while [ $# -gt 0 ]; do
	case "$1" in
		--self-check) SELF_CHECK=1 ;;
		*) echo "usage: $0 [--self-check]" >&2; exit 2 ;;
	esac
	shift
done

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
export LEDS_SYSFS="$WORK/leds"
export BOARD_NAME_FILE="$WORK/board_name"

mk_lamps()
{
	rm -rf "$LEDS_SYSFS"
	mkdir -p "$LEDS_SYSFS"
	local l
	for l in "$@"; do
		mkdir -p "$LEDS_SYSFS/$l"
		: > "$LEDS_SYSFS/$l/trigger"
		: > "$LEDS_SYSFS/$l/brightness"
		: > "$LEDS_SYSFS/$l/delay_on"
		: > "$LEDS_SYSFS/$l/delay_off"
	done
}

# uci -q get leds.<role>.<opt> -> $UCI_leds_<role>_<opt>
uci()
{
	[ "${1:-}" = "-q" ] && shift
	[ "${1:-}" = "get" ] || return 1
	local key="${2:-}" var
	var="UCI_$(printf '%s' "$key" | tr '.-' '__')"
	eval "printf '%s\n' \"\${$var-}\""
}

reset_uci()
{
	unset UCI_leds_wifi_lamp UCI_leds_wifi_client UCI_leds_wan_lamp
}

# Run every assertion against one copy of the library.  Returns 0 when all
# pass.  Runs in a subshell so function/variable state does not leak between
# the real run and the mutants.
run_checks()
{
	local lib="$1"

	(
		FAIL=0

		assert_eq()
		{
			if [ "$2" = "$3" ]; then
				echo "  ok   $1"
			else
				echo "  FAIL $1 (got '$2', want '$3')"
				FAIL=$((FAIL + 1))
			fi
		}

		. "$lib"

		# --- HMUF02-V5 defaults: blue = Wi-Fi, green = LTE, red = fault ---
		mk_lamps green:wan green:wlan blue:wan blue:wlan red:power red:wlan
		echo 'hmuf02v5,250605v0s' > "$BOARD_NAME_FILE"
		reset_uci

		assert_eq "hmuf02v5 wifi -> blue:wlan" \
			"$(led_role_lamp wifi)" "$LEDS_SYSFS/blue:wlan"
		assert_eq "hmuf02v5 wan -> green:wan" \
			"$(led_role_lamp wan)" "$LEDS_SYSFS/green:wan"
		assert_eq "hmuf02v5 wan_fault -> red:power" \
			"$(led_role_lamp wan_fault)" "$LEDS_SYSFS/red:power"

		if led_role_lamp data >/dev/null 2>&1; then
			echo "  FAIL hmuf02v5 data should be empty"
			FAIL=$((FAIL + 1))
		else
			echo "  ok   hmuf02v5 data -> empty"
		fi

		# --- mifib defaults: green = Wi-Fi, green:wan = LTE ---
		echo 'mifib-v12' > "$BOARD_NAME_FILE"

		assert_eq "mifib wifi -> green:wlan" \
			"$(led_role_lamp wifi)" "$LEDS_SYSFS/green:wlan"
		assert_eq "mifib wan -> green:wan" \
			"$(led_role_lamp wan)" "$LEDS_SYSFS/green:wan"

		# --- override wins; unknown label falls back ---
		echo 'hmuf02v5' > "$BOARD_NAME_FILE"
		UCI_leds_wifi_lamp='green:wlan'
		assert_eq "override wifi -> green:wlan" \
			"$(led_role_lamp wifi)" "$LEDS_SYSFS/green:wlan"

		UCI_leds_wifi_lamp='nosuch:led'
		assert_eq "unknown override falls back to blue:wlan" \
			"$(led_role_lamp wifi)" "$LEDS_SYSFS/blue:wlan"
		reset_uci

		# --- pattern parsing ---
		local lamp="$LEDS_SYSFS/blue:wlan"

		led_apply "$lamp" off
		assert_eq "off trigger" "$(cat "$lamp/trigger")" none
		assert_eq "off brightness" "$(cat "$lamp/brightness")" 0

		led_apply "$lamp" on
		assert_eq "on trigger" "$(cat "$lamp/trigger")" none
		assert_eq "on brightness" "$(cat "$lamp/brightness")" 1

		led_apply "$lamp" blink:250:150
		assert_eq "blink trigger" "$(cat "$lamp/trigger")" timer
		assert_eq "blink delay_on" "$(cat "$lamp/delay_on")" 250
		assert_eq "blink delay_off" "$(cat "$lamp/delay_off")" 150

		# --- empty lamp is a no-op ---
		if led_apply "" blink:250:250; then
			echo "  ok   empty lamp is a no-op"
		else
			echo "  FAIL empty lamp returned non-zero"
			FAIL=$((FAIL + 1))
		fi

		# --- built-in pattern table (what --show reports as effective) ---
		assert_eq "default wifi:ap_sta" \
			"$(led_role_spec wifi ap_sta)" 'blink:100:100'
		assert_eq "default wan:active" \
			"$(led_role_spec wan active)" 'blink:250:250'
		assert_eq "default bat_green:complete" \
			"$(led_role_spec bat_green complete)" 'on'
		assert_eq "default bat_red:fault" \
			"$(led_role_spec bat_red fault)" 'blink:150:150'
		assert_eq "unknown role:state prints nothing" \
			"$(led_role_spec nosuch state)" ''

		# --- per-scenario pattern override ---
		UCI_leds_wifi_client='blink:500:500'
		assert_eq "pattern override" \
			"$(led_role_spec wifi client)" 'blink:500:500'
		assert_eq "source is set" \
			"$(led_role_spec_source wifi client)" 'set'
		reset_uci
		assert_eq "pattern default" \
			"$(led_role_spec wifi client)" 'blink:250:250'
		assert_eq "source is default" \
			"$(led_role_spec_source wifi client)" 'default'

		# --- an explicit fallback argument still wins over the table ---
		assert_eq "explicit fallback" \
			"$(led_role_spec wifi client blink:999:999)" 'blink:999:999'

		[ "$FAIL" -eq 0 ]
	)
}

echo "== led-role: real library =="
if run_checks "$LIB"; then
	echo "led-role: PASS"
else
	echo "led-role: FAIL"
	exit 1
fi

[ "$SELF_CHECK" = "1" ] || exit 0

echo
echo "== led-role: self-check (each mutant must be caught) =="
MUT="$WORK/mut.sh"
CAUGHT=0

mutate()
{
	local desc="$1" sedexpr="$2"

	sed "$sedexpr" "$LIB" > "$MUT"
	if run_checks "$MUT" >/dev/null 2>&1; then
		echo "  FAIL mutant not caught: $desc"
	else
		echo "  ok   mutant caught: $desc"
		CAUGHT=$((CAUGHT + 1))
	fi
}

# 1. HMUF02-V5 default no longer prefers the blue lamp.
mutate "hmuf02v5 wifi default blue->green" \
	's/blue:wlan blue:wan/green:wlan green:wan/'

# 2. blink writes delay_off into delay_on.
mutate "blink delay_on/off swapped" \
	's/echo "\$on" > "\$led\/delay_on"/echo "$off" > "$led\/delay_on"/'

# 3. the role lamp override key is misspelt, so overrides are ignored.
mutate "override lookup key broken" \
	's/leds\.${role}\.lamp/leds.${role}.lampp/'

# 4. the built-in table drifts from the pattern the monitor used to hardcode.
mutate "wifi:ap_sta default 100 -> 250" \
	's/wifi:ap_sta)            echo blink:100:100/wifi:ap_sta)            echo blink:250:250/'

echo
TOTAL=4
if [ "$CAUGHT" -eq "$TOTAL" ]; then
	echo "led-role self-check: PASS ($CAUGHT/$TOTAL mutants caught)"
	exit 0
fi

echo "led-role self-check: FAIL ($CAUGHT/$TOTAL mutants caught)"
exit 1
