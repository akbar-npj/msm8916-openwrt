#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-2.0-only
#
# Hardware-free tests for
# packages/msm89xx-scripts/root/etc/uci-defaults/95-tailscale-community
#
# The script addresses /etc and /tmp directly, so each case runs a copy whose
# prefixes are rewritten into a temp root.  apk and logger are stubbed through
# PATH.  No root, no modem, no OpenWrt.
#
# --self-check applies mutants and requires each one to be caught.

set -uo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
SRC="$ROOT/packages/msm89xx-scripts/root/etc/uci-defaults/95-tailscale-community"
SELF_CHECK=0
[ "${1:-}" = "--self-check" ] && SELF_CHECK=1

pass=0
fail=0
ok() { pass=$((pass + 1)); printf '  ok   %s\n' "$*"; }
bad() { fail=$((fail + 1)); printf '  FAIL %s\n' "$*"; }
assert_eq() {
	if [ "$2" = "$3" ]; then ok "$1"; else bad "$1 (got '$2', want '$3')"; fi
}
assert_gone() {
	if [ -e "$2" ] || [ -L "$2" ]; then bad "$1 (still present)"; else ok "$1"; fi
}
assert_kept() {
	if [ -e "$2" ]; then ok "$1"; else bad "$1 (was removed)"; fi
}
assert_contains() {
	if grep -qF -- "$3" "$2" 2>/dev/null; then ok "$1"; else bad "$1 (no '$3' in $2)"; fi
}
assert_absent() {
	if grep -qF -- "$3" "$2" 2>/dev/null; then bad "$1 (found '$3')"; else ok "$1"; fi
}

# --------------------------------------------------------------------------
# Fixture
# --------------------------------------------------------------------------

fixture() {
	[ -n "${T:-}" ] && rm -rf "$T"
	T=$(mktemp -d /tmp/tsmig.XXXXXX)
	mkdir -p "$T/bin" "$T/etc/apk" "$T/etc/config" "$T/etc/init.d" \
		"$T/etc/rc.d" "$T/tmp"

	: > "$T/apk.log"
	: > "$T/log"
	: > "$T/apk.calls"
	printf 'ok\n' > "$T/apk.modes"
	printf 'luci-app-tailscale\n' > "$T/etc/apk/world"
	printf 'config settings\n\toption enabled 1\n' > "$T/etc/config/tailscale"
	: > "$T/etc/init.d/tailscale.luci"
	: > "$T/etc/rc.d/S90tailscale.luci"
	: > "$T/etc/rc.d/S90tailscale"

	cat > "$T/bin/logger" <<-'EOF'
	#!/bin/sh
	shift 2 2>/dev/null
	printf '%s\n' "$*" >> "$TEST_T/log"
	EOF

	# One mode per call, in order: ok, lock, nofeed.
	cat > "$T/bin/apk" <<-'EOF'
	#!/bin/sh
	n=$(( $(cat "$TEST_T/apk.calls" 2>/dev/null || echo 0) + 1 ))
	printf '%s\n' "$n" > "$TEST_T/apk.calls"
	printf 'apk %s\n' "$*" >> "$TEST_T/apk.log"
	mode=$(sed -n "${n}p" "$TEST_T/apk.modes")
	case "$mode" in
	lock)
		echo 'ERROR: Unable to lock database: Resource temporarily unavailable' >&2
		exit 99
		;;
	nofeed)
		echo 'ERROR: unable to select packages' >&2
		exit 1
		;;
	*) exit 0 ;;
	esac
	EOF
	chmod +x "$T/bin/logger" "$T/bin/apk"
}

# The script writes to /etc and /tmp by absolute path; rewrite them into the
# fixture root so a case cannot touch the host.
install_script() {
	local src=$1
	sed -e "s#/etc/#$T/etc/#g" -e "s#/tmp/luci-#$T/tmp/luci-#g" \
		"$src" > "$T/script"
	chmod +x "$T/script"
}

run() {
	PATH="$T/bin:$PATH" TEST_T="$T" TAILSCALE_MIGRATION_RETRY_DELAY=0 \
		sh "$T/script" >/dev/null 2>&1
	# The lock path retries in a background subshell; the delay is 0 but the
	# process still has to be scheduled.
	sleep 0.3
}

# --------------------------------------------------------------------------
# Suite
# --------------------------------------------------------------------------

suite() {
	local script=$1

	# --- nothing to migrate --------------------------------------------
	fixture
	printf 'busybox\n' > "$T/etc/apk/world"
	install_script "$script"
	run
	assert_eq "no old app, apk is never called" "$(cat "$T/apk.calls")" ""
	assert_kept "no old app, the init is left alone" "$T/etc/init.d/tailscale.luci"

	# --- the normal switch ---------------------------------------------
	fixture
	install_script "$script"
	run
	assert_absent "world entry dropped" "$T/etc/apk/world" "luci-app-tailscale"
	assert_contains "community app installed" "$T/apk.log" "add luci-app-tailscale-community"
	assert_contains "old app removed" "$T/apk.log" "del luci-app-tailscale"
	assert_eq "install runs before remove" \
		"$(sed -n '1p' "$T/apk.log")" "apk add luci-app-tailscale-community"
	assert_gone "renamed init removed" "$T/etc/init.d/tailscale.luci"
	assert_gone "renamed rc.d link removed" "$T/etc/rc.d/S90tailscale.luci"
	assert_gone "stale S90tailscale removed" "$T/etc/rc.d/S90tailscale"
	assert_kept "the feed conffile survives" "$T/etc/config/tailscale"

	# --- the feed is unreachable ---------------------------------------
	fixture
	printf 'nofeed\n' > "$T/apk.modes"
	install_script "$script"
	run
	assert_contains "old app kept when the install fails" "$T/etc/apk/world" "luci-app-tailscale"
	assert_kept "old app's init kept" "$T/etc/init.d/tailscale.luci"
	assert_contains "failure is logged" "$T/log" "keeping the old app"

	# --- apk holds its lock (the hotfix path) --------------------------
	fixture
	printf 'lock\nok\nok\n' > "$T/apk.modes"
	install_script "$script"
	run
	assert_contains "lock is reported" "$T/log" "apk is locked"
	assert_absent "retry drops the world entry" "$T/etc/apk/world" "luci-app-tailscale"
	assert_contains "retry installs the community app" "$T/apk.log" "add luci-app-tailscale-community"
	assert_contains "retry removes the old app" "$T/apk.log" "del luci-app-tailscale"
	assert_gone "retry removes the renamed init" "$T/etc/init.d/tailscale.luci"

	rm -rf "$T"
	T=
}

printf '== tailscale-migration ==\n'
suite "$SRC"
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
		file=$(mktemp /tmp/tsmig-mutant.XXXXXX)
		sed "$expr" "$SRC" > "$file"
		if [ "$(grep -c . "$file")" -lt 20 ]; then
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

	mutant "lock branch removed" 's/\*\[Ll\]ock\*)/*NOMATCH)/'
	mutant "world entry kept" "s#sed -i '/^luci-app-tailscale\$/d' /etc/apk/world#:#"
fi

printf '\n%d passed, %d failed\n' "$pass" "$fail"
[ "$fail" -eq 0 ]
