#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-2.0-only
#
# Hardware-free tests for the role tables and --swap in
# packages/msm89xx-scripts/root/usr/sbin/led-probe.
#
# led-probe sources /usr/share/msm89xx/leds.sh by absolute path and inspects
# /sys/class/leds, so each case runs a copy with both rewritten into a temp
# root.  uci is stubbed through PATH.  No root, no device, no OpenWrt.

set -uo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
SRC="$ROOT/packages/msm89xx-scripts/root/usr/sbin/led-probe"
LEDS_SH="$ROOT/packages/msm89xx-scripts/root/usr/share/msm89xx/leds.sh"
TEMPLATES="$ROOT/packages/msm89xx-scripts/root/usr/share/msm89xx/led-templates"
SELF_CHECK=0
[ "${1:-}" = "--self-check" ] && SELF_CHECK=1

pass=0
fail=0
ok() { pass=$((pass + 1)); printf '  ok   %s\n' "$*"; }
bad() { fail=$((fail + 1)); printf '  FAIL %s\n' "$*"; }
assert_eq() {
	if [ "$2" = "$3" ]; then ok "$1"; else bad "$1 (got '$2', want '$3')"; fi
}
assert_contains() {
	if grep -qF -- "$3" "$2" 2>/dev/null; then ok "$1"; else bad "$1 (no '$3' in $2)"; fi
}
assert_absent() {
	if grep -qF -- "$3" "$2" 2>/dev/null; then bad "$1 (found '$3')"; else ok "$1"; fi
}
assert_status() { # assert_status <name> <want> <got>
	if [ "$3" -eq "$2" ]; then ok "$1"; else bad "$1 (exit $3, want $2)"; fi
}

# --------------------------------------------------------------------------
# Fixture
# --------------------------------------------------------------------------

fixture() {
	local src="${1:-${SRC_ACTIVE:-$SRC}}"

	[ -n "${T:-}" ] && rm -rf "$T"
	T=$(mktemp -d /tmp/ledtpl.XXXXXX)
	mkdir -p "$T/bin" "$T/leds" "$T/templates"

	# The three lamps an HMUF02-V5 exposes (patch 805): green = LTE,
	# red = modem/fault, blue = Wi-Fi.
	for l in green:wan blue:wlan red:power; do
		mkdir -p "$T/leds/$l"
		: > "$T/leds/$l/trigger"
	done

	printf 'hmuf02v5\n' > "$T/board_name"
	: > "$T/uci.db"

	# Two shipped templates plus one broken fixture.
	cp "$TEMPLATES/hmuf02v5-green-wifi" "$T/templates/"
	printf '# desc: broken on purpose\nwifi\tgreen:wlan\nnonsense\tred:wlan\n' \
		> "$T/templates/broken"

	cat > "$T/bin/uci" <<-'EOF'
	#!/bin/sh
	[ "${1:-}" = "-q" ] && shift
	cmd="${1:-}"; shift || true
	case "$cmd" in
	get)
		awk -F= -v k="$1" '$1 == k { sub(/^[^=]*=/, ""); print; found = 1; exit }
			END { exit !found }' "$TEST_T/uci.db"
		;;
	set)
		k="${1%%=*}"; v="${1#*=}"
		grep -v "^${k}=" "$TEST_T/uci.db" > "$TEST_T/uci.db.new" 2>/dev/null || true
		printf '%s=%s\n' "$k" "$v" >> "$TEST_T/uci.db.new"
		mv -f "$TEST_T/uci.db.new" "$TEST_T/uci.db"
		;;
	delete)
		grep -v "^${1}=" "$TEST_T/uci.db" > "$TEST_T/uci.db.new" 2>/dev/null || true
		mv -f "$TEST_T/uci.db.new" "$TEST_T/uci.db"
		;;
	commit) : ;;
	*) exit 1 ;;
	esac
	EOF
	chmod +x "$T/bin/uci"

	# Rewrite the two absolute paths led-probe carries.
	sed -e "s#^\. /usr/share/msm89xx/leds.sh#. $LEDS_SH#" \
	    -e "s#/sys/class/leds#$T/leds#g" "$src" > "$T/led-probe"
	chmod +x "$T/led-probe"
}

run() {
	PATH="$T/bin:$PATH" TEST_T="$T" \
		LEDS_SYSFS="$T/leds" BOARD_NAME_FILE="$T/board_name" \
		LED_TEMPLATE_DIR="$T/templates" \
		sh "$T/led-probe" "$@" > "$T/out" 2> "$T/err"
}

# --------------------------------------------------------------------------
# Suite
# --------------------------------------------------------------------------

suite() {
	local script=$1
	SRC_ACTIVE="$script"

	# --- the shipped tables are listed with their description -----------
	fixture
	LED_TEMPLATE_DIR="$TEMPLATES" PATH="$T/bin:$PATH" TEST_T="$T" \
		LEDS_SYSFS="$T/leds" BOARD_NAME_FILE="$T/board_name" \
		sh "$T/led-probe" --templates > "$T/out" 2>&1
	assert_contains "lists the built-in table first" "$T/out" "default"
	assert_contains "lists the swapped table" "$T/out" "hmuf02v5-green-wifi"
	assert_contains "prints the description" "$T/out" "Wi-Fi on green, LTE on blue"

	# --- another board is not offered this board's deviation table ------
	fixture
	printf 'hmu05\n' > "$T/board_name"
	run --templates
	assert_contains "a foreign board still gets default" "$T/out" "default"
	assert_absent "a foreign board is not offered the table" "$T/out" "hmuf02v5-green-wifi"

	# --- but naming it explicitly still applies it ----------------------
	fixture
	printf 'hmu05\n' > "$T/board_name"
	run --template hmuf02v5-green-wifi --dry-run
	assert_status "an explicit foreign table still applies" 0 $?

	# --- dry run reports the change and writes nothing ------------------
	fixture
	run --template hmuf02v5-green-wifi --dry-run
	assert_status "dry run succeeds" 0 $?
	assert_contains "dry run shows the current lamp" "$T/out" "blue:wlan"
	assert_contains "dry run shows the target" "$T/out" "green:wan"
	assert_eq "dry run writes nothing" "$(cat "$T/uci.db")" ""

	# --- applying writes both roles -------------------------------------
	fixture
	run --template hmuf02v5-green-wifi
	assert_status "apply succeeds" 0 $?
	assert_contains "wifi moves to the green LED" "$T/uci.db" "leds.wifi.lamp=green:wan"
	assert_contains "wan moves to the blue LED" "$T/uci.db" "leds.wan.lamp=blue:wlan"
	assert_contains "wan_fault stays on red" "$T/uci.db" "leds.wan_fault.lamp=red:power"

	# --- the built-in table is not a file, and puts every role back ------
	fixture
	printf 'leds.wifi.lamp=green:wan\nleds.wan.lamp=blue:wlan\n' > "$T/uci.db"
	run --template default --dry-run
	assert_status "default dry run succeeds" 0 $?
	assert_contains "default dry run shows auto" "$T/out" "auto"
	assert_contains "default dry run writes nothing" "$T/uci.db" "leds.wifi.lamp=green:wan"

	fixture
	printf 'leds.wifi.lamp=green:wan\nleds.wan.lamp=blue:wlan\n' > "$T/uci.db"
	run --template default
	assert_status "default apply succeeds" 0 $?
	assert_contains "wifi goes back to auto" "$T/uci.db" "leds.wifi.lamp=auto"
	assert_contains "wan goes back to auto" "$T/uci.db" "leds.wan.lamp=auto"

	# --- an unknown template is refused ---------------------------------
	fixture
	run --template no-such-table
	assert_status "unknown template fails" 1 $?
	assert_absent "unknown template writes nothing" "$T/uci.db" "leds.wifi.lamp"

	# --- a template naming an unknown role is refused -------------------
	fixture
	run --template broken
	assert_status "broken template fails" 1 $?
	assert_absent "broken template writes nothing" "$T/uci.db" "leds.wifi.lamp"

	# --- swap exchanges the two lamps -----------------------------------
	fixture
	printf 'leds.wifi.lamp=blue:wlan\nleds.wan.lamp=green:wan\n' > "$T/uci.db"
	run --swap wifi wan
	assert_status "swap succeeds" 0 $?
	assert_contains "wifi takes the LTE lamp" "$T/uci.db" "leds.wifi.lamp=green:wan"
	assert_contains "wan takes the Wi-Fi lamp" "$T/uci.db" "leds.wan.lamp=blue:wlan"

	# --- swap refuses a role with no lamp on this board -----------------
	fixture
	run --swap data wan
	assert_status "swap without a lamp fails" 1 $?
	assert_absent "failed swap writes nothing" "$T/uci.db" "leds.wan.lamp"

	# --- swap refuses itself --------------------------------------------
	fixture
	run --swap wifi wifi
	assert_status "swap with itself fails" 1 $?
}

printf '== led-templates ==\n'
fixture
suite "$SRC"
rm -rf "$T"

if [ "$fail" -ne 0 ]; then
	printf '\n%d passed, %d failed\n' "$pass" "$fail"
	exit 1
fi

# --------------------------------------------------------------------------
# Self-check: each mutant must make the suite fail.
# --------------------------------------------------------------------------
if [ "$SELF_CHECK" -eq 1 ]; then
	printf '\n== self-check (mutants must be caught) ==\n'
	mutant() { # mutant <name> <sed expression>
		local name=$1 expr=$2 file
		file=$(mktemp /tmp/ledtpl-mutant.XXXXXX)
		sed "$expr" "$SRC" > "$file"
		if [ "$(grep -c . "$file")" -lt 40 ]; then
			bad "$name (sed produced an empty script)"
			rm -f "$file"
			return
		fi
		if ( pass=0; fail=0; suite "$file" >/dev/null 2>&1; [ "$fail" -eq 0 ] ); then
			bad "$name (mutant survived)"
		else
			ok "$name caught"
		fi
		rm -f "$file"
	}

	mutant "self-swap allowed" 's/\[ "\$a" != "\$b" \]/[ "$a" = "$b" ]/'
	mutant "dry run writes anyway" 's/\[ "\$dry" = "dry" \] && return 0/:/'
	mutant "unknown template accepted" 's/\[ -f "\$f" \] || { echo "led-probe: no template/true || { echo "led-probe: no template/'
	mutant "board gate dropped" 's/case "\$have" in \*"\$want"\*) ;; \*) continue ;; esac/:/'
fi

printf '\n%d passed, %d failed\n' "$pass" "$fail"
[ "$fail" -eq 0 ]
