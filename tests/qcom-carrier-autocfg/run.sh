#!/usr/bin/env bash
# Tests for provision_network() in
# packages/qcom-carrier-autocfg/files/qcom-carrier-autocfg.sh.
#
# The daemon is a while-true loop, so the suite sources it with
# QCOM_CARRIER_AUTOCFG_LIB_ONLY=1 (functions only) and drives provision_network
# against a stub uci.  No modem, no root, no OpenWrt.
#
# The case that matters is the one in issue #112: a device whose operator
# deleted network.modem.dns must not have it written back, and a device still
# carrying the pair this script used to write must lose it.
#
#   bash tests/qcom-carrier-autocfg/run.sh [--self-check]
#
# --self-check mutates the script and requires the suite to notice.

set -u

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
SCRIPT="${CARRIER_AUTOCFG:-$ROOT/packages/qcom-carrier-autocfg/files/qcom-carrier-autocfg.sh}"

PASS=0
FAIL=0

ok()  { PASS=$((PASS + 1)); printf '  ok   %s\n' "$1"; }
bad() { FAIL=$((FAIL + 1)); printf '  FAIL %s\n' "$1"; [ $# -gt 1 ] && printf '       %s\n' "$2"; }

# --- fixture -----------------------------------------------------------------

setup() {
    TMP="$(mktemp -d)"
    trap 'rm -rf "$TMP"' EXIT
    mkdir -p "$TMP/bin"

    export UCI_FILE="$TMP/uci"
    export UCI_CALLS="$TMP/ucicalls"
    export LOG="$TMP/log"
    export NETWORK_INIT="$TMP/network"
    : > "$UCI_FILE"
    : > "$UCI_CALLS"
    : > "$LOG"

    # uci stub: "conf.sec.opt=value" lines.  get/delete/commit only.
    cat > "$TMP/bin/uci" <<'EOF'
#!/bin/sh
[ "${1:-}" = "-q" ] && shift
cmd="${1:-}"
shift || true
case "$cmd" in
    get)
        line="$(grep -m1 "^${1}=" "$UCI_FILE" 2>/dev/null)" || exit 1
        [ -n "$line" ] || exit 1
        printf '%s\n' "${line#*=}"
        ;;
    set)
        key="${1%%=*}"
        val="${1#*=}"
        printf '%s\n' "$key" >> "$UCI_CALLS"
        grep -v "^${key}=" "$UCI_FILE" > "$UCI_FILE.tmp" 2>/dev/null
        mv "$UCI_FILE.tmp" "$UCI_FILE"
        printf '%s=%s\n' "$key" "$val" >> "$UCI_FILE"
        ;;
    delete)
        grep -v "^${1}=" "$UCI_FILE" > "$UCI_FILE.tmp" 2>/dev/null
        mv "$UCI_FILE.tmp" "$UCI_FILE"
        ;;
    commit)
        ;;
esac
exit 0
EOF

    # logger stub: record the message, like the log() echo.
    cat > "$TMP/bin/logger" <<'EOF'
#!/bin/sh
shift 2 2>/dev/null || true
printf '%s\n' "$*" >> "$LOG"
EOF

    # sleep stub: provision_network waits 3 s after a reload.
    cat > "$TMP/bin/sleep" <<'EOF'
#!/bin/sh
exit 0
EOF

    # network init stub, so the suite never touches the host's networking.
    cat > "$NETWORK_INIT" <<'EOF'
#!/bin/sh
printf 'network %s\n' "$*" >> "$CALLS"
EOF
    chmod +x "$TMP/bin/uci" "$TMP/bin/logger" "$TMP/bin/sleep" "$NETWORK_INIT"
    export CALLS="$TMP/calls"
    : > "$CALLS"

    PATH="$TMP/bin:$PATH"
    export PATH
}

# Load the functions into this shell.  Each test calls provision_network in a
# subshell so the uci stub's file state is the only thing carried between them.
load_script() {
    QCOM_CARRIER_AUTOCFG_LIB_ONLY=1 . "$SCRIPT"
}

# A configuration that provision_network considers already correct.
seed_good() {
    cat > "$UCI_FILE" <<'EOF'
network.modem.apn=3gnet
network.modem.iptype=ipv4v6
network.modem.proto=modemmanager
network.modem.allowedmode=3g|4g
network.modem.preferredmode=4g
network.modem.init_epsbearer=default
network.modem.peerdns=1
EOF
}

run_provision() {
    ( load_script; provision_network "$1" "$2" "$3" 0 ) >/dev/null 2>&1
}

uci_get() {
    grep -m1 "^$1=" "$UCI_FILE" 2>/dev/null | sed 's/^[^=]*=//'
}

has()  { if grep -q -- "$2" "$1" 2>/dev/null; then ok "$3"; else bad "$3" "missing: $2"; fi; }
lacks() { if grep -q -- "$2" "$1" 2>/dev/null; then bad "$3" "unexpected: $2"; else ok "$3"; fi; }

# --- tests -------------------------------------------------------------------

# A fresh device gets an interface, and no static resolver pair.
t_fresh() {
    setup
    : > "$UCI_FILE"
    run_provision 3gnet ipv4v6 4g
    [ "$(uci_get network.modem.apn)" = 3gnet ] \
        && ok "a fresh device gets its APN" || bad "a fresh device gets its APN" "$(cat "$UCI_FILE")"
    [ "$(uci_get network.modem.peerdns)" = 1 ] \
        && ok "a fresh device gets peerdns=1" || bad "a fresh device gets peerdns=1"
    [ -z "$(uci_get network.modem.dns)" ] \
        && ok "a fresh device gets no static DNS" || bad "a fresh device gets no static DNS" "dns=$(uci_get network.modem.dns)"
    lacks "$UCI_CALLS" "network.modem.dns" "the script never writes network.modem.dns"
}

# Issue #112: deleting the DNS must survive.  Before the fix, the empty value
# was itself a trigger, so every boot re-entered the block.
t_empty_dns_is_stable() {
    setup
    seed_good
    run_provision 3gnet ipv4v6 4g
    lacks "$LOG" "Configuring" "an empty dns does not force a reconfigure"
    [ -z "$(uci_get network.modem.dns)" ] \
        && ok "an empty dns stays empty" || bad "an empty dns stays empty" "dns=$(uci_get network.modem.dns)"
    lacks "$CALLS" "reload" "an empty dns does not reload netifd"
}

# A real change still reconfigures.
t_change_reconfigures() {
    setup
    seed_good
    run_provision other.apn ipv4v6 4g
    has "$LOG" "Configuring" "a changed APN reconfigures"
    [ "$(uci_get network.modem.apn)" = other.apn ] \
        && ok "a changed APN is written" || bad "a changed APN is written"
}

# The pair this script used to write is removed from units that still have it.
t_legacy_pair_removed() {
    setup
    seed_good
    printf 'network.modem.dns=8.8.8.8 1.1.1.1\n' >> "$UCI_FILE"
    run_provision 3gnet ipv4v6 4g
    [ -z "$(uci_get network.modem.dns)" ] \
        && ok "the shipped DNS pair is removed" || bad "the shipped DNS pair is removed" "dns=$(uci_get network.modem.dns)"
    has "$LOG" "Removed the fixed DNS pair" "the removal is logged"
}

# The operator's own resolvers are not the script's to remove.
t_user_dns_kept() {
    setup
    seed_good
    printf 'network.modem.dns=222.85.85.85 222.88.88.88\n' >> "$UCI_FILE"
    run_provision 3gnet ipv4v6 4g
    [ "$(uci_get network.modem.dns)" = "222.85.85.85 222.88.88.88" ] \
        && ok "an operator's own DNS is kept" || bad "an operator's own DNS is kept" "dns=$(uci_get network.modem.dns)"
    lacks "$LOG" "Removed the fixed DNS pair" "no removal is logged for a foreign DNS"
}

# With peerdns off the static list is load-bearing, even if it is our old pair.
t_peerdns_off_keeps_pair() {
    setup
    seed_good
    sed -i 's/^network\.modem\.peerdns=1$/network.modem.peerdns=0/' "$UCI_FILE"
    printf 'network.modem.dns=8.8.8.8 1.1.1.1\n' >> "$UCI_FILE"
    run_provision 3gnet ipv4v6 4g
    [ "$(uci_get network.modem.dns)" = "8.8.8.8 1.1.1.1" ] \
        && ok "the pair is kept when peerdns is off" || bad "the pair is kept when peerdns is off"
}

# --- runner ------------------------------------------------------------------

run_suite() {
    echo "== qcom-carrier-autocfg =="
    t_fresh
    t_empty_dns_is_stable
    t_change_reconfigures
    t_legacy_pair_removed
    t_user_dns_kept
    t_peerdns_off_keeps_pair
    echo
    printf '%d passed, %d failed\n' "$PASS" "$FAIL"
    [ "$FAIL" -eq 0 ]
}

self_check() {
    local mutant failed=0
    mutant="$(mktemp)"

    cp "$SCRIPT" "$mutant"
    sed -i 's/ || \[ "\$cur_eps" != "\$target_eps" \]; then/ || [ "$cur_eps" != "$target_eps" ] || [ -z "$(uci -q get network.modem.dns || echo "")" ]; then/' "$mutant"
    if CARRIER_AUTOCFG="$mutant" bash "$0" >/dev/null 2>&1; then
        echo "  FAIL mutant 'empty dns re-triggers the block' was not caught"
        failed=$((failed + 1))
    else
        echo "  ok   mutant 'empty dns re-triggers the block' caught"
    fi

    cp "$SCRIPT" "$mutant"
    sed -i 's/^\t\tuci set network.modem.peerdns=.1.$/&\n\t\tuci set network.modem.dns="8.8.8.8 1.1.1.1"/' "$mutant"
    if CARRIER_AUTOCFG="$mutant" bash "$0" >/dev/null 2>&1; then
        echo "  FAIL mutant 'the pair is written again' was not caught"
        failed=$((failed + 1))
    else
        echo "  ok   mutant 'the pair is written again' caught"
    fi

    cp "$SCRIPT" "$mutant"
    sed -i 's/^\tif \[ "\$(uci -q get network.modem.peerdns)" = "1" \] &&$/\tif [ -n "$(uci -q get network.modem.peerdns)" ] \&\&/' "$mutant"
    if CARRIER_AUTOCFG="$mutant" bash "$0" >/dev/null 2>&1; then
        echo "  FAIL mutant 'the pair is removed with peerdns off' was not caught"
        failed=$((failed + 1))
    else
        echo "  ok   mutant 'the pair is removed with peerdns off' caught"
    fi

    cp "$SCRIPT" "$mutant"
    sed -i 's/^\t   \[ "\$(uci -q get network.modem.dns)" = "8.8.8.8 1.1.1.1" \]; then$/\t   [ -n "$(uci -q get network.modem.dns)" ]; then/' "$mutant"
    if CARRIER_AUTOCFG="$mutant" bash "$0" >/dev/null 2>&1; then
        echo "  FAIL mutant 'any DNS is removed' was not caught"
        failed=$((failed + 1))
    else
        echo "  ok   mutant 'any DNS is removed' caught"
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
