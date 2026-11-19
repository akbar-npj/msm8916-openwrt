#!/usr/bin/env bash
# Tests for packages/luci-app-clients/root/usr/sbin/sta-list.
#
# The helper shells out to `iw` and reads mac80211 debugfs.  Both are
# overridable (STA_LIST_IW, STA_LIST_DEBUGFS), so the suite drives it against a
# stub iw and a fake debugfs tree.  No device, no root, no wlan.
#
#   bash tests/sta-list/run.sh [--self-check]
#
# --self-check mutates the helper and requires the suite to notice.

set -u

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
STA="${STA_LIST:-$ROOT/packages/luci-app-clients/root/usr/sbin/sta-list}"

PASS=0
FAIL=0

ok()  { PASS=$((PASS + 1)); printf '  ok   %s\n' "$1"; return 0; }
bad() { FAIL=$((FAIL + 1)); printf '  FAIL %s\n' "$1"; [ $# -gt 1 ] && printf '       %s\n' "$2"; return 0; }

# --- fixture -----------------------------------------------------------------

setup() {
    TMP="$(mktemp -d)"
    trap 'rm -rf "$TMP"' EXIT
    mkdir -p "$TMP/bin" "$TMP/world" "$TMP/dbg"
    export WORLD="$TMP/world"

    cat > "$TMP/bin/iw" <<'EOF'
#!/bin/sh
case "$1" in
dev)
	if [ $# -eq 1 ]; then
		while read -r name type; do
			printf '\tInterface %s\n' "$name"
		done < "$WORLD/ifaces"
		exit 0
	fi
	case "$3" in
	info)
		type=$(awk -v n="$2" '$1 == n { print $2 }' "$WORLD/ifaces")
		printf '\ttype %s\n' "${type:-managed}"
		exit 0
		;;
	station)
		[ "$4" = "dump" ] || exit 1
		[ -f "$WORLD/$2.dump" ] && cat "$WORLD/$2.dump"
		exit 0
		;;
	esac
	;;
esac
exit 0
EOF
    chmod +x "$TMP/bin/iw"
    : > "$TMP/world/ifaces"
}

# iface <name> <type>
iface() { printf '%s %s\n' "$1" "$2" >> "$TMP/world/ifaces"; }

# Every test starts from an empty world, so one test's interfaces cannot leak
# into the next.
reset_world() {
    : > "$TMP/world/ifaces"
    rm -f "$TMP"/world/*.dump
    rm -rf "$TMP/dbg"
    mkdir -p "$TMP/dbg"
}

# dump <name>  (station dump text on stdin)
dump() { cat > "$TMP/world/$1.dump"; }

# flags <iface> <mac> <flag...>
flags() {
    local iface="$1" mac="$2" d
    shift 2
    d="$TMP/dbg/phy6/netdev:$iface/stations/$mac"
    mkdir -p "$d"
    printf '%s\n' "$@" > "$d/flags"
}

run() { STA_LIST_IW="$TMP/bin/iw" STA_LIST_DEBUGFS="$TMP/dbg" "$STA" "$@"; }

# jq_py <expr over d> - read the JSON on stdin and print expr
jq_py() {
    python3 -c '
import json, sys
d = json.load(sys.stdin)
print(eval(sys.argv[1], {"d": d}))
' "$1"
}

# --- a one-station world -----------------------------------------------------

one_station() {
    iface phy6-ap0 AP
    dump phy6-ap0 <<'EOF'
Station aa:bb:cc:dd:ee:01 (on phy6-ap0)
	inactive time:	120 ms
	rx bytes:	1000
	signal:  	-45 [-46, -50] dBm
	signal avg:	-46 [-47, -51] dBm
	tx bitrate:	72.2 MBit/s MCS 7 short GI
	rx bitrate:	65.0 MBit/s MCS 6
	connected time:	125 seconds
EOF
    flags phy6-ap0 aa:bb:cc:dd:ee:01 AUTH ASSOC AUTHORIZED
}

# --- tests -------------------------------------------------------------------

t_empty() {
    iface phy6-ap0 AP
    : > "$TMP/world/phy6-ap0.dump"
    local out
    out=$(run)
    [ "$out" = '{"stations":[]}' ] && ok "no stations prints an empty array" ||
        bad "no stations prints an empty array" "got: $out"
}

t_fields() {
    one_station
    local out
    out=$(run)
    [ "$(printf '%s' "$out" | jq_py 'd["stations"][0]["mac"]')" = "aa:bb:cc:dd:ee:01" ] &&
        ok "mac is reported" || bad "mac is reported" "got: $out"
    [ "$(printf '%s' "$out" | jq_py 'd["stations"][0]["iface"]')" = "phy6-ap0" ] &&
        ok "iface comes from the (on ...) marker" || bad "iface comes from the (on ...) marker"
    [ "$(printf '%s' "$out" | jq_py 'd["stations"][0]["signal"]')" = "-45" ] &&
        ok "signal is the first number of the bracket list" || bad "signal is the first number"
    [ "$(printf '%s' "$out" | jq_py 'd["stations"][0]["signal_avg"]')" = "-46" ] &&
        ok "signal_avg is separate from signal" || bad "signal_avg is separate from signal"
    [ "$(printf '%s' "$out" | jq_py 'd["stations"][0]["connected"]')" = "125" ] &&
        ok "connected time is an integer" || bad "connected time is an integer"
    [ "$(printf '%s' "$out" | jq_py 'd["stations"][0]["inactive"]')" = "120" ] &&
        ok "inactive time is an integer" || bad "inactive time is an integer"
    [ "$(printf '%s' "$out" | jq_py 'd["stations"][0]["tx_rate"]')" = "72.2 MBit/s MCS 7 short GI" ] &&
        ok "tx_rate keeps the whole rate string" || bad "tx_rate keeps the whole rate string"
}

t_ps_states() {
    local out
    one_station
    flags phy6-ap0 aa:bb:cc:dd:ee:01 AUTH ASSOC AUTHORIZED PS_STA PSPOLL
    out=$(run)
    [ "$(printf '%s' "$out" | jq_py 'd["stations"][0]["ps"]')" = "legacy" ] &&
        ok "PS_STA + PSPOLL is legacy power save" || bad "PS_STA + PSPOLL is legacy" "got: $out"

    flags phy6-ap0 aa:bb:cc:dd:ee:01 AUTH ASSOC AUTHORIZED PS_STA UAPSD
    out=$(run)
    [ "$(printf '%s' "$out" | jq_py 'd["stations"][0]["ps"]')" = "wmm" ] &&
        ok "PS_STA + UAPSD is wmm power save" || bad "PS_STA + UAPSD is wmm"

    flags phy6-ap0 aa:bb:cc:dd:ee:01 AUTH ASSOC AUTHORIZED PS_STA
    out=$(run)
    [ "$(printf '%s' "$out" | jq_py 'd["stations"][0]["ps"]')" = "on" ] &&
        ok "PS_STA alone is on" || bad "PS_STA alone is on"

    flags phy6-ap0 aa:bb:cc:dd:ee:01 AUTH ASSOC AUTHORIZED UAPSD
    out=$(run)
    [ "$(printf '%s' "$out" | jq_py 'd["stations"][0]["ps"]')" = "off" ] &&
        ok "UAPSD without PS_STA is not dozing, so off" || bad "UAPSD without PS_STA is off" "got: $out"

    rm -rf "$TMP/dbg"
    out=$(run)
    [ "$(printf '%s' "$out" | jq_py 'd["stations"][0]["ps"]')" = "unknown" ] &&
        ok "no debugfs is unknown, not off" || bad "no debugfs is unknown"
}

t_skips_non_ap() {
    one_station
    iface phy0-sta0 managed
    dump phy0-sta0 <<'EOF'
Station 00:11:22:33:44:77 (on phy0-sta0)
	signal:  	-70 dBm
	connected time:	10 seconds
EOF
    local out
    out=$(run)
    [ "$(printf '%s' "$out" | jq_py 'len(d["stations"])')" = "1" ] &&
        ok "a managed interface is not dumped" || bad "a managed interface is not dumped" "got: $out"
    [ "$(printf '%s' "$out" | jq_py 'd["stations"][0]["iface"]')" = "phy6-ap0" ] &&
        ok "only the AP interface contributes" || bad "only the AP interface contributes"
}

t_two_aps() {
    iface phy6-ap0 AP
    iface phy7-ap0 AP
    dump phy6-ap0 <<'EOF'
Station aa:bb:cc:dd:ee:01 (on phy6-ap0)
	signal:  	-45 dBm
	connected time:	125 seconds
EOF
    dump phy7-ap0 <<'EOF'
Station aa:bb:cc:dd:ee:02 (on phy7-ap0)
	signal:  	-61 dBm
	connected time:	60 seconds
EOF
    flags phy6-ap0 aa:bb:cc:dd:ee:01 AUTH
    flags phy7-ap0 aa:bb:cc:dd:ee:02 AUTH PS_STA PSPOLL
    local out
    out=$(run)
    [ "$(printf '%s' "$out" | jq_py 'len(d["stations"])')" = "2" ] &&
        ok "stations from two AP interfaces are both listed" || bad "two AP interfaces" "got: $out"
    [ "$(printf '%s' "$out" | jq_py 'd["stations"][1]["ps"]')" = "legacy" ] &&
        ok "each station gets its own ps state" || bad "each station gets its own ps state"
}

t_missing_fields() {
    iface phy6-ap0 AP
    dump phy6-ap0 <<'EOF'
Station aa:bb:cc:dd:ee:01 (on phy6-ap0)
	inactive time:	5 ms
EOF
    local out
    out=$(run)
    [ "$(printf '%s' "$out" | jq_py 'd["stations"][0]["signal"]')" = "None" ] &&
        ok "a missing signal is null, not an empty string" || bad "missing signal is null" "got: $out"
    [ "$(printf '%s' "$out" | jq_py 'd["stations"][0]["connected"]')" = "None" ] &&
        ok "a missing connected time is null" || bad "missing connected time is null"
}

t_escapes() {
    iface phy6-ap0 AP
    dump phy6-ap0 <<'EOF'
Station aa:bb:cc:dd:ee:01 (on phy6-ap0)
	tx bitrate:	1.0 MBit/s "odd" \ rate
	signal:  	-45 dBm
EOF
    local out
    out=$(run)
    [ "$(printf '%s' "$out" | jq_py 'd["stations"][0]["tx_rate"]')" = '1.0 MBit/s "odd" \ rate' ] &&
        ok "quotes and backslashes survive as JSON escapes" || bad "quotes and backslashes escape" "got: $out"
}

t_cli() {
    run --help >/dev/null 2>&1 && ok "--help exits 0" || bad "--help exits 0"
    run --bogus >/dev/null 2>&1 && bad "an unknown argument exits non-zero" ||
        ok "an unknown argument exits non-zero"
}

# --- self-check --------------------------------------------------------------

self_check() {
    local mutant failed=0
    mutant="$(mktemp)"

    # 1. Treat PSPOLL/UAPSD as power save without PS_STA.  A station that only
    #    negotiated UAPSD is awake, and would be reported as wmm.
    cp "$STA" "$mutant"
    sed -i 's/^\t\t\*PS_STA\*)$/\t\t*PS_STA*|*UAPSD*)/' "$mutant"
    if STA_LIST="$mutant" bash "$0" >/dev/null 2>&1; then
        echo "  FAIL mutant 'UAPSD counts as dozing' was not caught"; failed=1
    else
        echo "  ok   mutant 'UAPSD counts as dozing' caught"
    fi

    # 2. Drop the AP-mode filter, so phy0-sta0 gets dumped too.
    cp "$STA" "$mutant"
    sed -i "/grep -q 'type AP'/d" "$mutant"
    if STA_LIST="$mutant" bash "$0" >/dev/null 2>&1; then
        echo "  FAIL mutant 'no AP-mode filter' was not caught"; failed=1
    else
        echo "  ok   mutant 'no AP-mode filter' caught"
    fi

    # 3. Emit an empty number instead of null, which is invalid JSON.
    cp "$STA" "$mutant"
    sed -i 's|function num(v) { return (v == "") ? "null" : v }|function num(v) { return v }|' "$mutant"
    if STA_LIST="$mutant" bash "$0" >/dev/null 2>&1; then
        echo "  FAIL mutant 'empty number instead of null' was not caught"; failed=1
    else
        echo "  ok   mutant 'empty number instead of null' caught"
    fi

    # 4. Do not extract the interface from the (on ...) marker.
    cp "$STA" "$mutant"
    sed -i 's|^\t\tiface = substr(\$0, RSTART + 4, RLENGTH - 5)$|\t\tiface = ""|' "$mutant"
    if STA_LIST="$mutant" bash "$0" >/dev/null 2>&1; then
        echo "  FAIL mutant 'no iface extraction' was not caught"; failed=1
    else
        echo "  ok   mutant 'no iface extraction' caught"
    fi

    rm -f "$mutant"
    [ "$failed" -eq 0 ] && echo "self-check: every mutant was caught" ||
        echo "self-check: at least one mutant survived"
    return "$failed"
}

# --- main --------------------------------------------------------------------

if [ "${1:-}" = "--self-check" ]; then
    self_check
    exit $?
fi

printf 'sta-list\n'
setup

for t in t_empty t_fields t_ps_states t_skips_non_ap t_two_aps t_missing_fields t_escapes t_cli; do
    printf '%s\n' "${t#t_}"
    reset_world
    "$t"
done

printf '\n%d passed, %d failed\n' "$PASS" "$FAIL"
[ "$FAIL" -eq 0 ]
