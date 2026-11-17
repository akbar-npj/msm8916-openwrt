#!/usr/bin/env bash
# Tests for the voice-liveness logic in msm89xx/base-files/usr/sbin/modem-bearer-watchdog.
#
# The daemon itself is a while-true loop, so the suite sources the script with
# MODEM_WATCHDOG_LIB_ONLY=1 (functions only) and drives the functions against
# stub uci/mmcli/ip/logger binaries. No modem, no root, no OpenWrt.
#
#   bash tests/modem-watchdog/run.sh [--self-check]
#
# --self-check mutates the script and requires the suite to notice.

set -u

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
WATCHDOG="${WATCHDOG:-$ROOT/msm89xx/base-files/usr/sbin/modem-bearer-watchdog}"

PASS=0
FAIL=0
SKIP=0

ok()   { PASS=$((PASS + 1)); printf '  ok   %s\n' "$1"; }
bad()  { FAIL=$((FAIL + 1)); printf '  FAIL %s\n' "$1"; [ $# -gt 1 ] && printf '       %s\n' "$2"; }
skip() { SKIP=$((SKIP + 1)); printf '  skip %s\n' "$1"; }

# --- fixture -----------------------------------------------------------------

setup() {
    TMP="$(mktemp -d)"
    trap 'rm -rf "$TMP"' EXIT
    mkdir -p "$TMP/bin"

    export UCI_FILE="$TMP/uci"
    export CALLS="$TMP/calls"
    export SIM_ICCID=""
    export CALL_STATE=""
    export REG_STATE="home"
    export CELL_MOVING=0
    export ROUTES=""
    export WWAN_ROUTE=""
    : > "$UCI_FILE"
    : > "$CALLS"

    cat > "$TMP/bin/uci" <<'EOF'
#!/bin/sh
[ "${1:-}" = "-q" ] && shift
[ "${1:-}" = "get" ] && shift
key="${1:-}"
line="$(grep -m1 "^${key}=" "$UCI_FILE" 2>/dev/null)" || exit 1
[ -n "$line" ] || exit 1
printf '%s\n' "${line#*=}"
EOF

    cat > "$TMP/bin/mmcli" <<'EOF'
#!/bin/sh
echo "mmcli $*" >> "$CALLS"
case "$*" in
    *"-i any -K"*)
        [ -n "$SIM_ICCID" ] && echo "sim.properties.iccid : $SIM_ICCID"
        ;;
    *"--voice-list-calls"*)
        [ -n "$CALL_STATE" ] && echo "/org/freedesktop/ModemManager1/Call/1 ($CALL_STATE)"
        ;;
    *"-m any -K"*)
        echo "modem.3gpp.registration-state : ${REG_STATE:-home}"
        ;;
    *"--get-cell-info"*)
        n=$(( $(cat "$TMP/cc" 2>/dev/null || echo 0) + 1 ))
        echo "$n" > "$TMP/cc"
        if [ "$CELL_MOVING" = "1" ]; then
            echo "serving: yes, rsrp: -$((80 + n)) dBm"
        else
            echo "serving: yes, rsrp: -85 dBm"
        fi
        ;;
esac
exit 0
EOF

    # Not an IMS-capable modem: the qmicli branch must fall through to
    # radio_alive(), which is what the 3G/2G and no-IMS boards do.
    cat > "$TMP/bin/qmicli" <<'EOF'
#!/bin/sh
exit 1
EOF

    cat > "$TMP/bin/ip" <<'EOF'
#!/bin/sh
echo "ip $*" >> "$CALLS"
case "$*" in
    *"route show default"*)
        case "$*" in
            *"dev wwan0"*) [ -n "$WWAN_ROUTE" ] && echo "$WWAN_ROUTE" ;;
            *) [ -n "$ROUTES" ] && printf '%s\n' "$ROUTES" ;;
        esac
        ;;
esac
exit 0
EOF

    printf '#!/bin/sh\nexit 0\n' > "$TMP/bin/sleep"
    printf '#!/bin/sh\nexit 0\n' > "$TMP/bin/logger"
    chmod +x "$TMP/bin/"*

    export TMP
    PATH="$TMP/bin:$PATH"
    export PATH
}

# Source the daemon's functions (no loop, no side effects).
load() {
    # shellcheck disable=SC1090
    MODEM_WATCHDOG_LIB_ONLY=1 . "$WATCHDOG"
    RX_SEEN_FLAG="$TMP/rx_seen"
    DROPPED_ROUTES="$TMP/dropped-routes"
    RX_SEEN_SIM=""
    OTHER_UPLINK_AT=0
    NOW=1000
    rm -f "$RX_SEEN_FLAG" "$DROPPED_ROUTES"
}

uci_set() { printf '%s=%s\n' "$1" "$2" >> "$UCI_FILE"; }
uci_clear() { : > "$UCI_FILE"; }
calls() { grep -c "$1" "$CALLS" 2>/dev/null || true; }

# --- tests -------------------------------------------------------------------

t_defaults_are_inert() {
    uci_clear
    if voice_alive >/dev/null; then
        bad "default voice_alive=0 holds the ladder"
    else
        ok "default voice_alive=0 holds the ladder"
    fi
    if [ "$(calls mmcli)" -eq 0 ]; then
        ok "default voice_alive=0 asks ModemManager nothing"
    else
        bad "default voice_alive=0 asks ModemManager nothing" "calls: $(tr '\n' ';' < "$CALLS")"
    fi
}

t_rx_tracking_gate() {
    if ! declare -F rx_tracking_enabled >/dev/null; then
        bad "rx_tracking_enabled exists" "the loop calls it unguarded without the helper"
        return
    fi
    uci_clear
    if rx_tracking_enabled; then
        bad "rx_tracking_enabled is off by default"
    else
        ok "rx_tracking_enabled is off by default"
    fi
    uci_set modem-watchdog.recovery.voice_alive 1
    if rx_tracking_enabled; then
        ok "rx_tracking_enabled follows voice_alive=1"
    else
        bad "rx_tracking_enabled follows voice_alive=1"
    fi
}

t_data_plan_data_sim() {
    uci_clear
    uci_set modem-watchdog.recovery.data_plan 1
    if no_data_service; then
        bad "data_plan=1 is never a voice-only SIM"
    else
        ok "data_plan=1 is never a voice-only SIM"
    fi
}

t_data_plan_forced_voice() {
    uci_clear
    uci_set modem-watchdog.recovery.data_plan 0
    if no_data_service && [ "$NO_DATA_WHY" = "data_plan is 0" ]; then
        ok "data_plan=0 is a voice-only SIM"
    else
        bad "data_plan=0 is a voice-only SIM" "why=$NO_DATA_WHY"
    fi
}

t_data_plan_auto() {
    uci_clear
    uci_set modem-watchdog.recovery.data_plan auto
    export SIM_ICCID="89012345678901234567"

    if no_data_service; then
        ok "data_plan=auto, no downlink yet -> voice-only"
    else
        bad "data_plan=auto, no downlink yet -> voice-only"
    fi

    cur="$(sim_id)"
    printf '%s\n' "$cur" > "$RX_SEEN_FLAG"
    if no_data_service; then
        bad "data_plan=auto, downlink seen with this SIM -> data SIM"
    else
        ok "data_plan=auto, downlink seen with this SIM -> data SIM"
    fi

    printf '%s\n' "0000000000000000" > "$RX_SEEN_FLAG"
    if no_data_service; then
        ok "data_plan=auto, downlink seen with another SIM -> voice-only"
    else
        bad "data_plan=auto, downlink seen with another SIM -> voice-only"
    fi
}

t_radio_alive() {
    uci_clear
    REG_STATE=home CELL_MOVING=1
    if radio_alive; then
        ok "radio_alive: registered with moving measurements"
    else
        bad "radio_alive: registered with moving measurements"
    fi

    CELL_MOVING=0
    if radio_alive; then
        bad "radio_alive: registered but frozen measurements -> not alive"
    else
        ok "radio_alive: registered but frozen measurements -> not alive"
    fi

    : > "$CALLS"
    REG_STATE=idle CELL_MOVING=1
    if radio_alive; then
        bad "radio_alive: not registered -> not alive"
    else
        ok "radio_alive: not registered -> not alive"
    fi
    if [ "$(calls get-cell-info)" -eq 0 ]; then
        ok "radio_alive: unregistered modem is not polled for cells"
    else
        bad "radio_alive: unregistered modem is not polled for cells"
    fi
    REG_STATE=home
}

t_call_in_progress() {
    uci_clear
    uci_set modem-watchdog.recovery.voice_alive 1
    uci_set modem-watchdog.recovery.data_plan 1
    CALL_STATE="active"
    out="$(voice_alive)" && rc=0 || rc=$?
    if [ "$rc" -eq 0 ] && [ "$out" = "a call is in progress" ]; then
        ok "voice_alive: a call holds the ladder on a data SIM"
    else
        bad "voice_alive: a call holds the ladder on a data SIM" "rc=$rc out=$out"
    fi
    CALL_STATE=""
}

t_voice_only_hold() {
    uci_clear
    uci_set modem-watchdog.recovery.voice_alive 1
    uci_set modem-watchdog.recovery.data_plan 0
    REG_STATE=home CELL_MOVING=1
    out="$(voice_alive)" && rc=0 || rc=$?
    if [ "$rc" -eq 0 ] && printf '%s' "$out" | grep -q "live cell measurements"; then
        ok "voice_alive: voice-only SIM with a live cell holds the ladder"
    else
        bad "voice_alive: voice-only SIM with a live cell holds the ladder" "rc=$rc out=$out"
    fi

    CELL_MOVING=0
    if voice_alive >/dev/null; then
        bad "voice_alive: voice-only SIM with a dead modem releases the ladder"
    else
        ok "voice_alive: voice-only SIM with a dead modem releases the ladder"
    fi
}

t_other_uplink() {
    ROUTES="" WWAN_ROUTE="default via 10.0.0.1 dev wwan0 proto static"
    OTHER_UPLINK_AT=0
    if other_uplink; then
        bad "other_uplink: modem-only route is not another uplink"
    else
        ok "other_uplink: modem-only route is not another uplink"
    fi

    ROUTES="default via 192.168.8.1 dev wlan0 proto static"
    if other_uplink; then
        ok "other_uplink: a wlan0 default is another uplink"
    else
        bad "other_uplink: a wlan0 default is another uplink"
    fi

    ROUTES="" OTHER_UPLINK_AT=$((NOW - 600))
    if other_uplink; then
        bad "other_uplink: grace window expires"
    else
        ok "other_uplink: grace window expires"
    fi
    OTHER_UPLINK_AT=0
}

t_route_round_trip() {
    WWAN_ROUTE="default via 10.0.0.1 dev wwan0 proto static"
    ROUTES="$WWAN_ROUTE"
    : > "$CALLS"
    drop_modem_routes
    if [ -f "$DROPPED_ROUTES" ] && grep -q "route del default via 10.0.0.1" "$CALLS"; then
        ok "drop_modem_routes records and deletes the wwan0 default"
    else
        bad "drop_modem_routes records and deletes the wwan0 default" "$(cat "$DROPPED_ROUTES" 2>/dev/null)"
    fi

    : > "$CALLS"
    restore_modem_routes
    if [ ! -f "$DROPPED_ROUTES" ] && grep -q "route add default via 10.0.0.1" "$CALLS"; then
        ok "restore_modem_routes puts it back and clears the record"
    else
        bad "restore_modem_routes puts it back and clears the record"
    fi
}

run_suite() {
    setup
    load
    t_defaults_are_inert
    t_rx_tracking_gate
    t_data_plan_data_sim
    t_data_plan_forced_voice
    t_data_plan_auto
    t_radio_alive
    t_call_in_progress
    t_voice_only_hold
    t_other_uplink
    t_route_round_trip
    printf '\n%d passed, %d failed, %d skipped\n' "$PASS" "$FAIL" "$SKIP"
    [ "$FAIL" -eq 0 ]
}

# --- self-check: the suite must catch a broken gate and a broken default -----

self_check() {
    local mutant
    mutant="$(mktemp)"
    local failed=0

    cp "$WATCHDOG" "$mutant"
    sed -i 's/^    \[ "\$(uci -q get modem-watchdog.recovery.voice_alive || echo 0)" = "1" \]$/    return 0/' "$mutant"
    if WATCHDOG="$mutant" bash "$0" >/dev/null 2>&1; then
        echo "  FAIL mutant 'rx gate always on' was not caught"
        failed=$((failed + 1))
    else
        echo "  ok   mutant 'rx gate always on' caught"
    fi

    cp "$WATCHDOG" "$mutant"
    sed -i 's/^        1) return 1 ;;$/        1) return 0 ;;/' "$mutant"
    if WATCHDOG="$mutant" bash "$0" >/dev/null 2>&1; then
        echo "  FAIL mutant 'data_plan=1 is voice-only' was not caught"
        failed=$((failed + 1))
    else
        echo "  ok   mutant 'data_plan=1 is voice-only' caught"
    fi

    cp "$WATCHDOG" "$mutant"
    sed -i 's/^    \[ "\$seen" = unknown \] && return 1$/    return 0/' "$mutant"
    if WATCHDOG="$mutant" bash "$0" >/dev/null 2>&1; then
        echo "  FAIL mutant 'unknown SIM is voice-only' was not caught"
        failed=$((failed + 1))
    else
        echo "  ok   mutant 'unknown SIM is voice-only' caught"
    fi

    rm -f "$mutant"
    return "$failed"
}

if [ "${1:-}" = "--self-check" ]; then
    echo "== self-check =="
    run_suite || exit 1
    echo
    self_check || exit 1
    echo "self-check passed"
    exit 0
fi

run_suite
