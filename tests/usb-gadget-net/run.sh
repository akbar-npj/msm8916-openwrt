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
	printf '%s\n' "$*" >> "$TEST_T/uci.log"
	[ "${1:-}" = "-q" ] && shift
	case "${1:-}" in
	get)
		awk -F= -v k="$2" '$1 == k { sub(/^[^=]*=/, ""); print; found = 1; exit }
			END { exit !found }' "$TEST_T/uci.db"
		;;
	set)
		k="${2%%=*}"; v="${2#*=}"
		grep -v "^${k}=" "$TEST_T/uci.db" > "$TEST_T/uci.db.new" 2>/dev/null
		printf '%s=%s\n' "$k" "$v" >> "$TEST_T/uci.db.new"
		mv "$TEST_T/uci.db.new" "$TEST_T/uci.db"
		;;
	show)
		# Only the `.name=` lines add_to_bridge greps for.
		awk -F= '$1 ~ /\.name$/ { print $1 "=" $2 }' "$TEST_T/uci.db"
		;;
	*) exit 0 ;;
	esac
	EOF
	chmod +x "$T/bin/uci"

	printf '#!/bin/sh\nexit 0\n' > "$T/bin/logger"
	chmod +x "$T/bin/logger"

	# ip/brctl/ubus/ifup/sleep are only reached by the bridge code; stubs
	# keep the suite fast and let a case make an attach fail on purpose.
	# A successful `ip link set <iface> master <bridge>` creates the brif
	# entry, which is what the real kernel does and what bridge_attach
	# reads back to decide whether the attach worked.
	cat > "$T/bin/ip" <<-'EOF'
	#!/bin/sh
	echo "ip $*" >> "$TEST_T/net.log"
	if [ "${4:-}" = "master" ]; then
		[ "${NET_STUB_RC:-0}" = 0 ] || exit "${NET_STUB_RC}"
		mkdir -p "$TEST_T/sys/class/net/$5/brif/$3"
		exit 0
	fi
	exit "${NET_STUB_RC:-0}"
	EOF
	chmod +x "$T/bin/ip"

	for c in brctl ubus ifup ifconfig; do
		printf '#!/bin/sh\necho "%s $*" >> "$TEST_T/net.log"\nexit ${NET_STUB_RC:-0}\n' "$c" \
			> "$T/bin/$c"
		chmod +x "$T/bin/$c"
	done
	printf '#!/bin/sh\nexit 0\n' > "$T/bin/sleep"
	chmod +x "$T/bin/sleep"

	# Rewrite the absolute paths the script carries.
	sed -e "s#^\. /lib/functions.sh#. $T/functions.sh#" \
	    -e "s#/etc/machine-id#$T/etc/machine-id#g" \
	    -e "s#/sys/block/mmcblk0/device/cid#$T/sys/cid#g" \
	    -e "s#/var/run/usb-gadget/net_mode#$T/run/net_mode#g" \
	    -e "s#/sys/class/net#$T/sys/class/net#g" \
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

# Pull one function out of the rewritten copy and evaluate it in this shell.
# The bridge code only touches uci/ip/brctl/ubus and rewritten paths, so it
# can be exercised without configfs or a kernel bridge.
load_fn() {
	sed -n "/^$1() {/,/^}/p" "$T/usb-gadget"
}

# --------------------------------------------------------------------------
# Suite
# --------------------------------------------------------------------------

suite() {
	local script=$1 first second
	SRC_ACTIVE="$script"

	# --- no bare `cond && cmd` can abort the script under set -e --------
	# usb-gadget.sh runs with `set -e`.  A trailing `[ ... ] && cmd` whose
	# test fails exits the whole script with 1.  That was every RNDIS
	# build, where the last guard in setup_network is
	# `[ "$CFG_NCM" = "1" ] && bring_up_iface ...` -- the gadget was built
	# correctly and the script still reported failure.
	fixture
	local bare
	bare=$(grep -nE '^[[:space:]]*\[ .* \] &&' "$script" | grep -v '|| true' | head -1)
	if [ -n "$bare" ]; then
		bad "a bare guard aborts under set -e ($bare)"
	else
		ok "no bare guard can abort under set -e"
	fi

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

	# --- the LAN bridge ------------------------------------------------
	# netifd runs at S20 and the gadget at S99, and usb0 is the bridge's
	# only port.  A bridge with no present members is never marked present
	# (netifd bridge.c), so br-lan does not exist at all and the port
	# attach has nothing to join.  bridge_empty is what keeps it alive.
	bridge_setup() {
		fixture
		mkdir -p "$T/sys/class/net" "$T/cfg/functions/ncm.usb0"
		printf 'usb0\n' > "$T/cfg/functions/ncm.usb0/ifname"
		export PATH="$T/bin:$PATH" TEST_T="$T"
		CFG_FUNCTIONS_PATH="$T/cfg/functions"
		CFG_RNDIS=0; CFG_ECM=0; CFG_NCM=1
		eval "$(load_fn log)"
		eval "$(load_fn bridge_attach)"
		eval "$(load_fn add_to_bridge)"
		eval "$(load_fn ensure_bridge)"
	}

	# The device section that carries the port list must also carry
	# bridge_empty, or the reload produces no bridge at all.
	bridge_setup
	uci_set network.@device[0].name br-lan
	add_to_bridge "$T/cfg/functions/ncm.usb0/ifname" 2>>"$T/err"
	if grep -qx 'network.@device\[0\].bridge_empty=1' "$T/uci.db"; then
		ok "the br-lan device section gets bridge_empty"
	else
		bad "the br-lan device section gets bridge_empty"
	fi

	# No device section names br-lan: the same option goes on the one the
	# firstboot writes, so the fallback section is covered too.
	bridge_setup
	add_to_bridge "$T/cfg/functions/ncm.usb0/ifname" 2>>"$T/err"
	if grep -qx 'network.@device\[0\].bridge_empty=1' "$T/uci.db"; then
		ok "the fallback device section gets bridge_empty"
	else
		bad "the fallback device section gets bridge_empty"
	fi

	# A bridge that exists and refuses the port is the one failure that
	# used to be invisible behind `|| true`.
	bridge_setup
	mkdir -p "$T/sys/class/net/br-lan" "$T/sys/class/net/usb0"
	NET_STUB_RC=1 bridge_attach usb0 2>>"$T/err"
	assert_contains "a refused attach is logged" "$T/err" "could not attach usb0 to br-lan"

	# No bridge yet is normal on a cold boot, and must not be noise.
	bridge_setup
	mkdir -p "$T/sys/class/net/usb0"
	bridge_attach usb0 2>>"$T/err"
	assert_absent "a missing bridge is not an error" "$T/err" "could not attach"

	# netifd is asked to bring the LAN up when the bridge is still absent,
	# and says so when it never appears.
	bridge_setup
	ensure_bridge 2>>"$T/err"
	assert_contains "ensure_bridge asks netifd for the LAN" "$T/net.log" \
		"ubus call network.interface.lan up"
	assert_contains "ensure_bridge reports a bridge that never came" "$T/err" \
		"br-lan does not exist"

	# And it re-enslaves the port if netifd built the bridge without it.
	bridge_setup
	mkdir -p "$T/sys/class/net/br-lan" "$T/sys/class/net/usb0"
	ensure_bridge 2>>"$T/err"
	assert_contains "ensure_bridge re-attaches the port" "$T/net.log" \
		"ip link set usb0 master br-lan"
	if [ -d "$T/sys/class/net/br-lan/brif/usb0" ]; then
		ok "the port ends up in brif"
	else
		bad "the port ends up in brif"
	fi
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
	mutant "a bare guard returns" 's/^    if \[ "\$CFG_NCM" = "1" \]; then bring_up_iface .*$/    [ "$CFG_NCM" = "1" ] \&\& bring_up_iface "${CFG_FUNCTIONS_PATH}\/ncm.usb0\/ifname"/'
	mutant "serial from the clock" 's/^    printf .%s. "\$id" | sha256sum/    id="$(date +%s)-$$"; printf "%s" "$id" | sha256sum/'
	mutant "bridge_empty dropped" 's/bridge_empty=1/bridge_empty=0/'
	mutant "a refused attach is silent" 's/^    log "could not attach \$iface to br-lan"$/    :/'
	mutant "ensure_bridge does not retry" 's/^    while \[ "\$i" -lt 5 \]; do$/    while [ "$i" -lt 0 ]; do/'
	mutant "ensure_bridge does not report" 's/^        log "br-lan does not exist; the LAN interface has no device"$/        :/'
fi

printf '\n%d passed, %d failed\n' "$pass" "$fail"
[ "$fail" -eq 0 ]
