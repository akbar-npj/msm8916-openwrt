#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-2.0-only
#
# Hardware-free tests for usb-gadget.sh: the net_mode selector and the stable
# serial in packages/uci-usb-gadget/files/usb-gadget.sh.
#
# The script sources /lib/functions.sh and reads /etc/machine-id and
# /sys/block/mmcblk0/device/cid by absolute path, so each case runs a copy with
# those rewritten into a temp root, with uci and logger stubbed through PATH.
# No root, no device, no configfs.

set -uo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
SRC="$ROOT/packages/uci-usb-gadget/files/usb-gadget.sh"
SELF_CHECK=0
[ "${1:-}" = "--self-check" ] && SELF_CHECK=1

pass=0
fail=0
ok() { pass=$((pass + 1)); printf '  ok   %s\n' "$*"; }
bad() { fail=$((fail + 1)); printf '  FAIL %s\n' "$*"; }
assert_contains() {
	if grep -qF -- "$3" "$2" 2>/dev/null; then ok "$1"; else bad "$1 (no '$3' in $(basename "$2"))"; fi
}
assert_absent() {
	if grep -qF -- "$3" "$2" 2>/dev/null; then bad "$1 (found '$3')"; else ok "$1"; fi
}
assert_eq() {
	if [ "$2" = "$3" ]; then ok "$1"; else bad "$1 (got '$2', want '$3')"; fi
}
assert_no_file() {
	if [ -e "$2" ]; then bad "$1 (found $(basename "$2"))"; else ok "$1"; fi
}

# --------------------------------------------------------------------------
# Fixture
# --------------------------------------------------------------------------

fixture() {
	local src="${1:-${SRC_ACTIVE:-$SRC}}"

	[ -n "${T:-}" ] && rm -rf "$T"
	T=$(mktemp -d /tmp/usbnet.XXXXXX)
	mkdir -p "$T/bin" "$T/etc" "$T/sys"

	# A UCI database the stub reads: one key=value per line.
	: > "$T/uci.db"

	cat > "$T/functions.sh" <<-'EOF'
	# Stub of /lib/functions.sh -- the two calls usb-gadget.sh makes.
	config_load() { :; }
	config_get() {
		local __var="$1" __sec="$2" __opt="$3" __def="$4" __v
		__v=$(awk -F= -v k="${__sec}.${__opt}" '$1 == k { sub(/^[^=]*=/, ""); print; exit }' "$TEST_T/uci.db")
		eval "$__var=\"\${__v:-$__def}\""
	}
	config_get_bool() { config_get "$@"; }
	EOF

	cat > "$T/bin/uci" <<-'EOF'
	#!/bin/sh
	[ "${1:-}" = "-q" ] && shift
	case "${1:-}" in
	get)
		awk -F= -v k="$2" '$1 == k { sub(/^[^=]*=/, ""); print; found = 1; exit }
			END { exit !found }' "$TEST_T/uci.db"
		;;
	*) exit 1 ;;
	esac
	EOF
	chmod +x "$T/bin/uci"

	printf '#!/bin/sh\nexit 0\n' > "$T/bin/logger"
	chmod +x "$T/bin/logger"

	# Rewrite the absolute paths the script carries.
	sed -e "s#^\. /lib/functions.sh#. $T/functions.sh#" \
	    -e "s#/etc/machine-id#$T/etc/machine-id#g" \
	    -e "s#/sys/block/mmcblk0/device/cid#$T/sys/cid#g" \
	    -e "s#/var/run/usb-gadget/net_mode#$T/run/net_mode#g" \
	    "$src" > "$T/usb-gadget"
	chmod +x "$T/usb-gadget"
}

run() {
	PATH="$T/bin:$PATH" TEST_T="$T" sh "$T/usb-gadget" "$@" \
		> "$T/out" 2> "$T/err"
}

uci_set() {
	printf '%s=%s\n' "$1" "$2" >> "$T/uci.db"
}

# --------------------------------------------------------------------------
# Suite
# --------------------------------------------------------------------------

suite() {
	local script=$1 first second
	SRC_ACTIVE="$script"

	# --- no net_mode: the per-function booleans stand --------------------
	fixture
	uci_set usb.enabled 1
	uci_set ncm.enabled 1
	uci_set rndis.enabled 0
	run status
	assert_contains "legacy config keeps NCM" "$T/out" "Network mode: legacy (ncm=1 rndis=0"

	fixture
	uci_set usb.enabled 1
	uci_set ncm.enabled 0
	uci_set rndis.enabled 1
	run status
	assert_contains "legacy config keeps RNDIS" "$T/out" "Network mode: legacy (ncm=0 rndis=1"

	# --- net_mode=ncm forces NCM even if rndis was enabled ---------------
	fixture
	uci_set usb.enabled 1
	uci_set usb.net_mode ncm
	uci_set ncm.enabled 0
	uci_set rndis.enabled 1
	run status
	assert_contains "ncm mode wins over the booleans" "$T/out" "Network mode: ncm (ncm=1 rndis=0"

	# --- net_mode=rndis forces RNDIS even if ncm was enabled -------------
	fixture
	uci_set usb.enabled 1
	uci_set usb.net_mode rndis
	uci_set ncm.enabled 1
	uci_set rndis.enabled 0
	run status
	assert_contains "rndis mode wins over the booleans" "$T/out" "Network mode: rndis (ncm=0 rndis=1"

	# --- net_mode=both keeps both, as an escape hatch --------------------
	fixture
	uci_set usb.enabled 1
	uci_set usb.net_mode both
	run status
	assert_contains "both mode enables both" "$T/out" "Network mode: both (ncm=1 rndis=1"

	# --- an unknown mode falls back to the booleans, and says so ---------
	fixture
	uci_set usb.enabled 1
	uci_set usb.net_mode rndsi
	uci_set ncm.enabled 1
	uci_set rndis.enabled 0
	run status
	assert_contains "a typo keeps the booleans" "$T/out" "Network mode: legacy (ncm=1 rndis=0"
	assert_contains "a typo is reported" "$T/err" "is not auto, ncm, rndis or both"

	# --- auto is NCM today ----------------------------------------------
	fixture
	uci_set usb.enabled 1
	uci_set usb.net_mode auto
	uci_set rndis.enabled 1
	run status
	assert_contains "auto resolves to NCM" "$T/out" "Network mode: auto (ncm=1 rndis=0"

	# --- the probe's decision is applied, and shown ---------------------
	fixture
	uci_set usb.enabled 1
	uci_set usb.net_mode auto
	mkdir -p "$T/run"
	printf 'rndis\n' > "$T/run/net_mode"
	run status
	assert_contains "the decision moves auto to RNDIS" "$T/out" "Network mode: auto (ncm=0 rndis=1"
	assert_contains "the decision is shown" "$T/out" "Probe decision: rndis"

	# --- a pinned mode ignores a leftover decision ----------------------
	fixture
	uci_set usb.enabled 1
	uci_set usb.net_mode ncm
	mkdir -p "$T/run"
	printf 'rndis\n' > "$T/run/net_mode"
	run status
	assert_contains "a pin outranks the decision" "$T/out" "Network mode: ncm (ncm=1 rndis=0"
	assert_absent "a pinned unit shows no decision" "$T/out" "Probe decision:"

	# --- a legacy unit ignores it too -----------------------------------
	fixture
	uci_set usb.enabled 1
	uci_set ncm.enabled 1
	uci_set rndis.enabled 0
	mkdir -p "$T/run"
	printf 'rndis\n' > "$T/run/net_mode"
	run status
	assert_contains "legacy booleans outrank the decision" "$T/out" "Network mode: legacy (ncm=1 rndis=0"
	assert_absent "a legacy unit shows no decision" "$T/out" "Probe decision:"

	# --- the serial is stable across runs -------------------------------
	fixture
	uci_set usb.enabled 1
	printf '1abe6880-ufi003\n' > "$T/etc/machine-id"
	run status
	first=$(sed -n 's/^Serial: //p' "$T/out")
	run status
	second=$(sed -n 's/^Serial: //p' "$T/out")
	assert_eq "the serial survives a restart" "$first" "$second"
	assert_eq "the serial is 16 hex characters" "${#first}" 16

	# --- a different machine-id gives a different serial ----------------
	printf 'a-different-unit\n' > "$T/etc/machine-id"
	run status
	assert_absent "two units do not share a serial" "$T/out" "Serial: $first"

	# --- without a machine-id the eMMC CID is used, and is stable -------
	fixture
	uci_set usb.enabled 1
	rm -f "$T/etc/machine-id"
	printf '150100384754463452067d2b7f3b2a00\n' > "$T/sys/cid"
	run status
	first=$(sed -n 's/^Serial: //p' "$T/out")
	run status
	second=$(sed -n 's/^Serial: //p' "$T/out")
	assert_eq "the CID serial is stable" "$first" "$second"
	assert_no_file "the CID path leaves machine-id alone" "$T/etc/machine-id"
}

printf '== usb-gadget net ==\n'
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
		file=$(mktemp /tmp/usbnet-mutant.XXXXXX)
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

	mutant "net_mode ignored" 's/^    case "\$CFG_NET_MODE" in$/    CFG_NET_MODE=""; case "$CFG_NET_MODE" in/'
	mutant "rndis mode keeps ncm" 's/^            CFG_NCM=0$/            CFG_NCM=1/'
	mutant "the probe decision is ignored" 's/^    if \[ "\$CFG_NET_MODE" = "auto" \] && \[ -r "\$NETMODE_OVERRIDE" \]; then$/    if false; then/'
	mutant "serial from the clock" 's/^    printf .%s. "\$id" | sha256sum/    id="$(date +%s)-$$"; printf "%s" "$id" | sha256sum/'
fi

printf '\n%d passed, %d failed\n' "$pass" "$fail"
[ "$fail" -eq 0 ]
