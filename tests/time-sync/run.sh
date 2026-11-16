#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-2.0-only
#
# Hardware-free tests for packages/msm89xx-scripts/root/usr/sbin/time-sync.
#
# Everything the script talks to is stubbed through PATH: uci, qmicli,
# uclient-fetch, jsonfilter, logger, date, ntpd.  No root, no modem, no
# OpenWrt.  --self-check applies mutants and requires each one to be caught.

set -uo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
SCRIPT="$ROOT/packages/msm89xx-scripts/root/usr/sbin/time-sync"
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

# --------------------------------------------------------------------------
# Fixture
# --------------------------------------------------------------------------

fixture() {
	T=$(mktemp -d /tmp/time-sync.XXXXXX)
	mkdir -p "$T/bin"
	: > "$T/uci.db"
	: > "$T/log"
	: > "$T/date.set"
	echo 1970 > "$T/year"
	echo 1000 > "$T/epoch"
	: > "$T/qmi"
	: > "$T/geo"

	cat > "$T/bin/logger" <<-'EOF'
	#!/bin/sh
	shift 2 2>/dev/null
	printf '%s\n' "$*" >> "$TEST_LOG"
	EOF

	cat > "$T/bin/sleep" <<-'EOF'
	#!/bin/sh
	exit 0
	EOF

	cat > "$T/bin/timeout" <<-'EOF'
	#!/bin/sh
	shift
	exec "$@"
	EOF

	# date reads the clock from files so ntpd can "step" it in a child process.
	cat > "$T/bin/date" <<-'EOF'
	#!/bin/sh
	case "${1:-}" in
	-s) printf '%s\n' "${2#@}" > "$TEST_DATE_SET"; exit 0 ;;
	+%Y) cat "$TEST_YEAR_FILE"; exit 0 ;;
	+%s) cat "$TEST_EPOCH_FILE"; exit 0 ;;
	esac
	exec /bin/date "$@"
	EOF

	# -F would make the ^ anchor literal, and the keys contain [0]; compare
	# the key field in awk so the match is exact.
	cat > "$T/bin/uci" <<-'EOF'
	#!/bin/sh
	[ "${1:-}" = "-q" ] && shift
	cmd=${1:-}
	[ $# -gt 0 ] && shift
	case "$cmd" in
	get)
		awk -F= -v k="$1" '$1 == k { sub(/^[^=]*=/, ""); print; found = 1; exit }
			END { exit !found }' "$TEST_UCI"
		;;
	set) printf '%s\n' "$1" >> "$TEST_UCI" ;;
	commit) : ;;
	esac
	EOF

	cat > "$T/bin/qmicli" <<-'EOF'
	#!/bin/sh
	[ -s "$TEST_QMI" ] || exit 1
	cat "$TEST_QMI"
	EOF

	cat > "$T/bin/uclient-fetch" <<-'EOF'
	#!/bin/sh
	[ -s "$TEST_GEO" ] || exit 1
	cat "$TEST_GEO"
	EOF

	cat > "$T/bin/jsonfilter" <<-'EOF'
	#!/usr/bin/env python3
	import json, sys
	key = sys.argv[2].split('.')[-1].strip("'")
	print(json.load(sys.stdin).get(key, ""))
	EOF

	cat > "$T/bin/ntpd" <<-'EOF'
	#!/bin/sh
	[ "${TEST_NTP_OK:-1}" = "1" ] || exit 1
	echo 2026 > "$TEST_YEAR_FILE"
	exit 0
	EOF

	cat > "$T/system-init" <<-'EOF'
	#!/bin/sh
	echo "$*" >> "$TEST_SYSTEM_INIT_LOG"
	EOF

	chmod +x "$T/bin/"* "$T/system-init"
}

run() { # run <script> <subcommand>
	PATH="$T/bin:$PATH" \
	TEST_UCI="$T/uci.db" TEST_LOG="$T/log" TEST_DATE_SET="$T/date.set" \
	TEST_YEAR_FILE="$T/year" TEST_EPOCH_FILE="$T/epoch" \
	TEST_QMI="$T/qmi" TEST_GEO="$T/geo" \
	TEST_SYSTEM_INIT_LOG="$T/system-init.log" \
	TIME_SYNC_TIME_FILE="$T/time-last" \
	TIME_SYNC_QMI_DEV="$T/qmi.dev" \
	TIME_SYNC_GEO_URL="https://geo.invalid/json" \
	TIME_SYNC_SYSTEM_INIT="$T/system-init" \
	TEST_NTP_OK="${TEST_NTP_OK:-1}" \
	sh "$1" "$2" 2>/dev/null
}

# --------------------------------------------------------------------------
# Tests
# --------------------------------------------------------------------------

suite() {
	local script=$1

	# --- NITZ: 22 quarter-hours = +05:30, no DST -------------------------
	fixture
	cat > "$T/qmi" <<-'EOF'
	[/dev/wwan0qmi0] Time retrieved:
		Time count: '1 (x 1.25ms): 2026-11-16 20:00:00'
		Time source: 'network'
		Time zone: '22'
		Daylight saving adjustment: '0'
	EOF
	: > "$T/qmi.dev"
	run "$script" zone
	assert_contains "NITZ sets the POSIX zone" "$T/uci.db" "system.@system[0].timezone=IST-5:30"
	assert_contains "NITZ sets the label" "$T/uci.db" "system.@system[0].zonename=NITZ"
	assert_contains "NITZ reloads system" "$T/system-init.log" "reload"

	# --- NITZ with DST: -20 quarters (-05:00) + 1 h = -04:00 -------------
	fixture
	cat > "$T/qmi" <<-'EOF'
	[/dev/wwan0qmi0] Time retrieved:
		Time source: 'network'
		Time zone: '-20'
		Daylight saving adjustment: '1'
	EOF
	: > "$T/qmi.dev"
	run "$script" zone
	assert_contains "NITZ adds the DST hour" "$T/uci.db" "system.@system[0].timezone=LOC+4"

	# --- device time only (no SIM) must not be used ----------------------
	fixture
	cat > "$T/qmi" <<-'EOF'
	[/dev/wwan0qmi0] Time retrieved:
		Time source: 'device'
		System time: '5687496 (ms): 1980-01-06 01:34:47'
	EOF
	: > "$T/qmi.dev"
	run "$script" zone
	assert_eq "device time is ignored" "$(cat "$T/uci.db")" ""

	# --- geo fallback when the modem has no zone -------------------------
	fixture
	echo '{"timezone":"Asia/Kolkata","utc_offset":"+0530","country_code":"IN"}' > "$T/geo"
	run "$script" zone
	assert_contains "geo sets the POSIX zone" "$T/uci.db" "system.@system[0].timezone=IST-5:30"
	assert_contains "geo sets the IANA label" "$T/uci.db" "system.@system[0].zonename=Asia/Kolkata"

	# --- a negative offset from geo --------------------------------------
	fixture
	echo '{"timezone":"America/New_York","utc_offset":"-0400"}' > "$T/geo"
	run "$script" zone
	assert_contains "negative offset keeps its sign" "$T/uci.db" "system.@system[0].timezone=LOC+4"

	# --- a real choice is never overridden ------------------------------
	fixture
	printf 'system.@system[0].zonename=Europe/Berlin\n' > "$T/uci.db"
	echo '{"timezone":"Asia/Kolkata","utc_offset":"+0530"}' > "$T/geo"
	run "$script" zone
	assert_eq "user timezone is left alone" "$(cat "$T/uci.db")" "system.@system[0].zonename=Europe/Berlin"

	# --- restore raises a 1970 clock, never lowers a good one -----------
	fixture
	echo 1790000000 > "$T/time-last"
	run "$script" restore
	assert_eq "restore sets the saved time" "$(cat "$T/date.set")" "1790000000"

	fixture
	echo 500 > "$T/time-last"
	echo 1790000000 > "$T/epoch"
	run "$script" restore
	assert_eq "restore never lowers the clock" "$(cat "$T/date.set")" ""

	# --- save only with a plausible clock, and only after the interval --
	fixture
	echo 2026 > "$T/year"
	echo 1790003600 > "$T/epoch"
	echo 1790000000 > "$T/time-last"
	run "$script" save
	assert_eq "save writes after the interval" "$(cat "$T/time-last")" "1790003600"

	fixture
	echo 1970 > "$T/year"
	echo 1000 > "$T/epoch"
	echo 1790000000 > "$T/time-last"
	run "$script" save
	assert_eq "save refuses a 1970 clock" "$(cat "$T/time-last")" "1790000000"

	# --- step: one-shot NTP fixes an implausible clock -------------------
	fixture
	echo 1970 > "$T/year"
	run "$script" step
	assert_eq "step fixes the clock" "$(cat "$T/year")" "2026"
	assert_contains "step is logged" "$T/log" "clock stepped"

	# --- step: no reply is reported, not retried forever ----------------
	fixture
	echo 1970 > "$T/year"
	TEST_NTP_OK=0 run "$script" step
	if grep -qF "no NTP reply" "$T/log"; then ok "step reports no reply"; else bad "step reports no reply"; fi

	# --- a plausible clock is left to sysntpd ---------------------------
	fixture
	echo 2026 > "$T/year"
	run "$script" step
	if grep -qF "no NTP reply" "$T/log"; then bad "step skips a plausible clock"; else ok "step skips a plausible clock"; fi
}

printf '== time-sync ==\n'
suite "$SCRIPT"
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
		file=$(mktemp /tmp/time-sync-mutant.XXXXXX)
		sed "$expr" "$SCRIPT" > "$file"
		if [ "$(grep -c . "$file")" -lt 10 ]; then
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

	mutant "plausible inverted" 's/\[ "\$(date +%Y)" -ge "\$YEAR_FLOOR" \]/[ "$(date +%Y)" -lt "$YEAR_FLOOR" ]/'
	mutant "IST abbreviation dropped" 's/\[ "\$minutes" = "330" \] && abbr=IST/:/'
	mutant "user choice guard removed" 's/|UTC|GMT|Etc\/UTC) ;; \*) return 0 ;;/|UTC|GMT|Etc\/UTC) ;; *) ;; /'
fi

printf '\n%d passed, %d failed\n' "$pass" "$fail"
[ "$fail" -eq 0 ]
