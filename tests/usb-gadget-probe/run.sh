#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-2.0-only
#
# Hardware-free tests for usb-gadget-probe: the NCM-then-RNDIS state machine in
# packages/uci-usb-gadget/files/usb-gadget-probe.
#
# The probe reads /sys, /sys/kernel/config and /var/run by absolute path, so
# each case runs it with those redirected into a temp root through its own
# USB_GADGET_PROBE_* overrides, with uci and logger stubbed through PATH and a
# fake usb-gadget that records the rebuilds.
#
# The loop runs forever by design, so every case is bounded by `timeout` with
# the poll and settle timers turned down to nothing.  A case therefore asserts
# on what the probe did (the rebuild log, the decision file), not on when.

set -uo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
SRC="$ROOT/packages/uci-usb-gadget/files/usb-gadget-probe"
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
	if grep -qF -- "$3" "$2" 2>/dev/null; then ok "$1"; else bad "$1 (no '$3' in $(basename "$2"))"; fi
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
	T=$(mktemp -d /tmp/usbprobe.XXXXXX)
	mkdir -p "$T/bin" "$T/sys/class/udc/ci_hdrc.0" "$T/sys/class/net/usb0" \
		"$T/cfg/usb_gadget/g1/functions/ncm.usb0" "$T/run"

	: > "$T/uci.db"
	: > "$T/calls"
	: > "$T/log"
	: > "$T/restart_states"

	printf 'usb0\n' > "$T/cfg/usb_gadget/g1/functions/ncm.usb0/ifname"

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

	cat > "$T/bin/logger" <<-'EOF'
	#!/bin/sh
	shift 2
	printf '%s\n' "$*" >> "$TEST_T/log"
	EOF
	chmod +x "$T/bin/logger"

	# The rebuild the probe asks for.  It records which transport was chosen
	# (the probe writes that to the decision file before calling), and moves
	# the UDC to the next state in the queue -- an empty queue leaves it
	# where it is, which is what a host that re-enumerates immediately looks
	# like.
	cat > "$T/bin/fake-usb-gadget" <<-'EOF'
	#!/bin/sh
	printf 'restart %s\n' "$(cat "$TEST_T/run/net_mode" 2>/dev/null)" >> "$TEST_T/calls"
	next=$(head -1 "$TEST_T/restart_states" 2>/dev/null)
	if [ -n "$next" ]; then
		sed -i '1d' "$TEST_T/restart_states"
		printf '%s\n' "$next" > "$TEST_T/sys/class/udc/ci_hdrc.0/state"
	fi
	exit 0
	EOF
	chmod +x "$T/bin/fake-usb-gadget"

	chmod +x "$src"
}

# probe_run -- run the probe against whatever the fixture currently holds.
probe_run() {
	PATH="$T/bin:$PATH" TEST_T="$T" \
	USB_GADGET_PROBE_SYSFS="$T/sys" \
	USB_GADGET_PROBE_CONFIGFS="$T/cfg" \
	USB_GADGET_PROBE_OVERRIDE="$T/run/net_mode" \
	USB_GADGET_PROBE_PROG="$T/bin/fake-usb-gadget" \
	USB_GADGET_PROBE_POLL=0.05 \
	USB_GADGET_PROBE_SETTLE=0 \
	USB_GADGET_PROBE_UNPLUG=0 \
	timeout 0.6 sh "$SRC_ACTIVE" > "$T/out" 2> "$T/err"
	return 0
}

# probe <state> [carrier] -- set the world, then run the probe for a moment.
probe() {
	local state=$1 carrier=${2:-}
	printf '%s\n' "$state" > "$T/sys/class/udc/ci_hdrc.0/state"
	if [ -n "$carrier" ]; then
		printf '%s\n' "$carrier" > "$T/sys/class/net/usb0/carrier"
	else
		rm -f "$T/sys/class/net/usb0/carrier"
	fi
	probe_run
}

uci_set() {
	printf '%s=%s\n' "$1" "$2" >> "$T/uci.db"
}

decided() { cat "$T/run/net_mode" 2>/dev/null; }
calls() { wc -l < "$T/calls" | tr -d ' '; }

# --------------------------------------------------------------------------
# Suite
# --------------------------------------------------------------------------

suite() {
	local script=$1
	SRC_ACTIVE="$script"

	# --- a host that binds NCM is left alone ----------------------------
	fixture
	uci_set usbgadget.usb.net_mode auto
	probe configured 1
	assert_eq "NCM host causes no rebuild" "$(calls)" 0
	assert_no_file "NCM host leaves no decision" "$T/run/net_mode"
	assert_contains "NCM host is reported" "$T/log" "host bound NCM"

	# --- a host that does not bind NCM is moved to RNDIS ----------------
	fixture
	uci_set usbgadget.usb.net_mode auto
	probe configured 0
	assert_eq "no NCM bind causes one rebuild" "$(calls)" 1
	assert_contains "the rebuild uses RNDIS" "$T/calls" "restart rndis"
	assert_eq "the decision is RNDIS" "$(decided)" "rndis"
	assert_contains "the fallback is reported" "$T/log" "falling back to RNDIS"

	# --- an unreadable carrier counts as unbound ------------------------
	fixture
	uci_set usbgadget.usb.net_mode auto
	probe configured
	assert_eq "a missing carrier falls back" "$(calls)" 1
	assert_contains "the rebuild uses RNDIS" "$T/calls" "restart rndis"

	# --- a gadget with no ncm function cannot bind it -------------------
	fixture
	uci_set usbgadget.usb.net_mode auto
	rm -f "$T/cfg/usb_gadget/g1/functions/ncm.usb0/ifname"
	probe configured 1
	assert_eq "no ncm ifname falls back" "$(calls)" 1
	assert_contains "the rebuild uses RNDIS" "$T/calls" "restart rndis"

	# --- nobody plugged in means no decision ----------------------------
	fixture
	uci_set usbgadget.usb.net_mode auto
	probe "not attached"
	assert_eq "an empty port causes no rebuild" "$(calls)" 0
	assert_no_file "an empty port leaves no decision" "$T/run/net_mode"

	# --- one decision per attach, never a second flip -------------------
	fixture
	uci_set usbgadget.usb.net_mode auto
	printf 'rndis\n' > "$T/run/net_mode"
	probe configured 0
	assert_eq "a standing decision is not re-made" "$(calls)" 0
	assert_eq "the decision survives" "$(decided)" "rndis"

	# --- a real unplug puts NCM back for the next host ------------------
	fixture
	uci_set usbgadget.usb.net_mode auto
	printf 'rndis\n' > "$T/run/net_mode"
	probe "not attached"
	assert_eq "an unplug causes one rebuild" "$(calls)" 1
	assert_contains "the rebuild goes back to NCM" "$T/calls" "restart ncm"
	assert_no_file "the unplug clears the decision" "$T/run/net_mode"

	# --- after the unplug the next host is probed from the top ----------
	fixture
	uci_set usbgadget.usb.net_mode auto
	printf 'rndis\n' > "$T/run/net_mode"
	printf 'configured\n' > "$T/restart_states"
	probe "not attached" 1
	assert_eq "the next host is decided once" "$(calls)" 1
	assert_contains "the reset rebuild goes back to NCM" "$T/calls" "restart ncm"
	assert_contains "the next host is read as NCM" "$T/log" "host bound NCM"

	# --- a pinned mode is never overridden ------------------------------
	fixture
	uci_set usbgadget.usb.net_mode rndis
	printf 'rndis\n' > "$T/run/net_mode"
	probe configured 0
	assert_eq "a pin causes no rebuild" "$(calls)" 0
	assert_no_file "a pin drops a stale decision" "$T/run/net_mode"
	assert_contains "the pin is reported" "$T/log" "net_mode=rndis is not auto"

	fixture
	uci_set usbgadget.usb.net_mode ncm
	probe configured 0
	assert_eq "ncm pin causes no rebuild" "$(calls)" 0
	assert_no_file "ncm pin writes no decision" "$T/run/net_mode"

	# --- an unset net_mode keeps the legacy booleans --------------------
	fixture
	probe configured 0
	assert_eq "a legacy unit is not probed" "$(calls)" 0
	assert_no_file "a legacy unit gets no decision" "$T/run/net_mode"
	assert_contains "the legacy unit is reported" "$T/log" "net_mode=unset is not auto"

	# --- no UDC at all is not a plugged-in host -------------------------
	fixture
	uci_set usbgadget.usb.net_mode auto
	rm -rf "$T/sys/class/udc"
	probe_run
	assert_eq "a missing UDC causes no rebuild" "$(calls)" 0
	assert_no_file "a missing UDC leaves no decision" "$T/run/net_mode"
}

printf '== usb-gadget probe ==\n'
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
		file=$(mktemp /tmp/usbprobe-mutant.XXXXXX)
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
	chmod +x /tmp/usbprobe-mutant.* 2>/dev/null

	mutant "carrier ignored, always NCM" 's/if \[ "\$(ncm_carrier)" = 1 \]; then/if true; then/'
	mutant "an unplug does not rebuild" 's/^                        switch_to ncm$/                        :/'
	mutant "the decision is not written" 's|printf .%s\\n. "\$mode" > "\$OVERRIDE"|: > "$OVERRIDE"|'
	mutant "a standing decision is re-made" 's/^                    if \[ -z "\$decided" \]; then$/                    if true; then/'
	mutant "pinned mode still decides" 's/^            auto)$/            auto|rndis|ncm|both)/'
fi

printf '\n%d passed, %d failed\n' "$pass" "$fail"
[ "$fail" -eq 0 ]
