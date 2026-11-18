#!/usr/bin/env bash
# Tests for the route logic in packages/msm89xx-scripts/root/usr/sbin/uplink-manager.
#
# The daemon is a while-true loop, so the suite sources it with
# UPLINK_MANAGER_LIB_ONLY=1 (functions only) and drives read_modem,
# normalize_modem_route, drop_defaults_except, migrate_shipped_dns and cycle
# against stub ubus/jsonfilter/ip/uci/ping/logger.  No device, no root.
#
#   bash tests/uplink-manager/run.sh [--self-check]
#
# --self-check mutates the script and requires the suite to notice.

set -u

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
UM="${UPLINK_MANAGER:-$ROOT/packages/msm89xx-scripts/root/usr/sbin/uplink-manager}"
CONF="$ROOT/packages/msm89xx-scripts/root/etc/config/uplink"
MK="$ROOT/packages/msm89xx-scripts/Makefile"

PASS=0
FAIL=0

ok()   { PASS=$((PASS + 1)); printf '  ok   %s\n' "$1"; return 0; }
bad()  { FAIL=$((FAIL + 1)); printf '  FAIL %s\n' "$1"; [ $# -gt 1 ] && printf '       %s\n' "$2"; return 0; }

# --- fixture -----------------------------------------------------------------

setup() {
    TMP="$(mktemp -d)"
    trap 'rm -rf "$TMP"' EXIT
    mkdir -p "$TMP/bin"

    export PATH="$TMP/bin:$PATH"
    export UCI_FILE="$TMP/uci"
    export UCI_CALLS="$TMP/uci-calls"
    export ROUTE_TABLE="$TMP/routes"
    export IP_CALLS="$TMP/ip-calls"
    export UBUS_JSON="$TMP/ubus.json"
    export LOG="$TMP/log"
    export PING_FAIL_DEV=""
    export STA_ADDR="0"
    : > "$UCI_FILE"
    : > "$UCI_CALLS"
    : > "$ROUTE_TABLE"
    : > "$IP_CALLS"
    : > "$LOG"

    # ubus: the interface status is whatever the test last wrote.
    cat > "$TMP/bin/ubus" <<'EOF'
#!/bin/sh
[ "$1" = "call" ] || exit 1
[ -s "$UBUS_JSON" ] || exit 1
cat "$UBUS_JSON"
EOF

    # jsonfilter: the small subset uplink-manager uses.
    cat > "$TMP/bin/jsonfilter" <<'EOF'
#!/usr/bin/env python3
import json, os, re, sys
args = sys.argv[1:]
expr = path = None
i = 0
while i < len(args):
    if args[i] == "-e":
        expr = args[i + 1]; i += 2
    elif args[i] == "-i":
        path = args[i + 1]; i += 2
    else:
        i += 1
data = json.load(open(path)) if path else json.load(sys.stdin)
m = re.match(r'^@\.([A-Za-z0-9_]+)(?:\[@\.([A-Za-z0-9_]+)="([^"]*)"\])?(?:\.([A-Za-z0-9_]+))?$', expr or "")
if not m:
    sys.exit(3)
first, fkey, fval, last = m.groups()
node = data[first]
if fkey:
    node = [e for e in node if str(e.get(fkey)) == fval]
    for e in node:
        if last in e:
            print(e[last])
else:
    v = node[last] if (last and isinstance(node, dict)) else node
    print("true" if v is True else "false" if v is False else v)
EOF

    # ip: a two-column route table (dev via metric) plus the calls it saw.
    cat > "$TMP/bin/ip" <<'EOF'
#!/usr/bin/env python3
import os, sys
args = [a for a in sys.argv[1:] if a != "-4"]
open(os.environ["IP_CALLS"], "a").write(" ".join(args) + "\n")
TBL = os.environ["ROUTE_TABLE"]

def load():
    out = []
    for line in open(TBL).read().splitlines():
        if line.strip():
            out.append(line.split())
    return out

def save(rows):
    open(TBL, "w").write("".join(" ".join(r) + "\n" for r in rows))

def sel(argv):
    d = {}
    i = 0
    while i < len(argv):
        if argv[i] in ("dev", "via", "metric") and i + 1 < len(argv):
            d[argv[i]] = argv[i + 1]; i += 2
        else:
            i += 1
    return d

verb = args[0] if args else ""
if verb == "addr":
    if os.environ.get("STA_ADDR") == "1":
        print("    inet 192.168.13.162/24 brd 192.168.13.255 scope global phy0-sta0")
    sys.exit(0)
if verb != "route" or len(args) < 2:
    sys.exit(0)
op = args[1]
rest = args[2:]
if op == "show":
    d = sel(rest)
    rows = [r for r in load() if "dev" not in d or r[0] == d["dev"]]
    for r in sorted(rows, key=lambda r: int(r[2])):
        print("default via %s dev %s metric %s" % (r[1], r[0], r[2]))
elif op == "replace":
    d = sel(rest)
    rows = [r for r in load() if not (r[0] == d.get("dev") and r[2] == d.get("metric"))]
    rows.append([d.get("dev", ""), d.get("via", ""), d.get("metric", "0")])
    save(rows)
elif op in ("del", "delete"):
    d = sel(rest)
    keep = []
    for r in load():
        hit = (("dev" not in d or r[0] == d["dev"]) and
               ("via" not in d or r[1] == d["via"]) and
               ("metric" not in d or r[2] == d["metric"]))
        if not hit:
            keep.append(r)
    save(keep)
EOF

    cat > "$TMP/bin/ping" <<'EOF'
#!/bin/sh
[ "$1" = "-I" ] && [ "$2" = "$PING_FAIL_DEV" ] && exit 1
exit 0
EOF

    cat > "$TMP/bin/uci" <<'EOF'
#!/bin/sh
F="$UCI_FILE"
cmd="$1"; shift
[ "$cmd" = "-q" ] && { cmd="$1"; shift; }
case "$cmd" in
get)     sed -n "s/^$1=//p" "$F" | head -1 ;;
set)     k="${1%%=*}"; v="${1#*=}"
         awk -v k="$k" 'index($0, k "=") != 1' "$F" > "$F.t" 2>/dev/null
         mv "$F.t" "$F"
         printf '%s=%s\n' "$k" "$v" >> "$F" ;;
delete|del) awk -v k="$1" 'index($0, k "=") != 1' "$F" > "$F.t" 2>/dev/null
         mv "$F.t" "$F" ;;
commit)  echo "commit $1" >> "$UCI_CALLS" ;;
add_list) echo "add_list $1" >> "$UCI_CALLS" ;;
esac
EOF

    cat > "$TMP/bin/logger" <<'EOF'
#!/bin/sh
shift 2>/dev/null
echo "$*" >> "$LOG"
EOF

    chmod +x "$TMP/bin"/*
}

# The OpenWrt shell library provides these; the script only needs the two.
config_load() { :; }
config_get() {
    local __v="$1" __s="$2" __o="$3" __d="$4" __val
    __val="$(uci -q get "uplink.$__s.$__o")"
    [ -n "$__val" ] || __val="$__d"
    eval "$__v=\"\$__val\""
}

# Source the daemon once per test so its globals start clean.
load_daemon() {
    UPLINK_MANAGER_LIB_ONLY=1 . "$UM"
    : > "$IP_CALLS"
    : > "$UCI_CALLS"
    JSON="$TMP/uplink.json"
    LEASE="$TMP/lease"
    PIDFILE="$TMP/pid"
}

seed_uci() {
    cat > "$UCI_FILE" <<'EOF'
uplink.general.policy=auto
uplink.general.probe_target=1.1.1.1
uplink.general.probe_interval=30
uplink.general.probe_timeout=3
uplink.general.fail_threshold=3
uplink.general.recover_threshold=2
uplink.general.override_metric=40
EOF
}

# The exact shape netifd returns, from the report in #121.
modem_up() {
    cat > "$UBUS_JSON" <<'EOF'
{"up":true,"l3_device":"wwan0","route":[{"target":"0.0.0.0","mask":0,"nexthop":"10.226.182.198","source":"10.226.182.197/32"}]}
EOF
}

modem_down() { : > "$UBUS_JSON"; }

set_routes() { printf '%s\n' "$@" > "$ROUTE_TABLE"; }

show_routes() { ip route show default 2>/dev/null | sort; }

# --- checks ------------------------------------------------------------------

t_predicate() {
    load_daemon
    modem_up
    read_modem
    [ "$modem_ok" = "1" ] && ok "a netifd default route is recognised" \
        || bad "a netifd default route is recognised" "modem_ok=$modem_ok dev=$modem_dev gw=$modem_gw"
    [ "$modem_gw" = "10.226.182.198" ] && ok "the gateway comes from the route entry" \
        || bad "the gateway comes from the route entry" "gw=$modem_gw"
}

t_no_default_route() {
    load_daemon
    cat > "$UBUS_JSON" <<'EOF'
{"up":true,"l3_device":"wwan0","route":[{"target":"10.226.182.196","mask":30,"nexthop":"0.0.0.0"}]}
EOF
    read_modem
    [ "$modem_ok" = "0" ] && ok "a link-scope route is not a default route" \
        || bad "a link-scope route is not a default route" "gw=$modem_gw"
}

t_modem_down() {
    load_daemon
    modem_down
    read_modem
    [ "$modem_ok" = "0" ] && [ -z "$modem_dev" ] && ok "a down interface is not usable" \
        || bad "a down interface is not usable" "ok=$modem_ok dev=$modem_dev"
}

t_normalize_lifts_metric() {
    load_daemon
    modem_up; read_modem
    set_routes "wwan0 10.226.182.198 10"
    modem_metric=50
    normalize_modem_route
    [ "$(show_routes)" = "default via 10.226.182.198 dev wwan0 metric 50" ] \
        && ok "netifd's metric-10 route is lifted to 50, not duplicated" \
        || bad "netifd's metric-10 route is lifted to 50, not duplicated" "$(show_routes | tr '\n' '|')"
}

t_normalize_idempotent() {
    load_daemon
    modem_up; read_modem
    set_routes "wwan0 10.226.182.198 50"
    modem_metric=50
    normalize_modem_route
    [ "$(show_routes)" = "default via 10.226.182.198 dev wwan0 metric 50" ] \
        && ok "an already-correct route is left alone" \
        || bad "an already-correct route is left alone" "$(show_routes | tr '\n' '|')"
    grep -q "route del" "$IP_CALLS" && bad "nothing is deleted when the metric already matches" \
        || ok "nothing is deleted when the metric already matches"
    grep -q "route replace" "$IP_CALLS" && bad "an already-correct route is not rewritten" \
        || ok "an already-correct route is not rewritten"
}

t_normalize_collapses_duplicates() {
    load_daemon
    modem_up; read_modem
    set_routes "wwan0 10.226.182.198 10" "wwan0 10.226.182.198 50"
    modem_metric=50
    normalize_modem_route
    [ "$(show_routes)" = "default via 10.226.182.198 dev wwan0 metric 50" ] \
        && ok "the two-defaults state collapses to one" \
        || bad "the two-defaults state collapses to one" "$(show_routes | tr '\n' '|')"
}

t_normalize_keeps_foreign_device() {
    load_daemon
    modem_up; read_modem
    set_routes "wwan0 10.226.182.198 10" "phy0-sta0 10.246.20.242 40"
    modem_metric=50
    normalize_modem_route
    [ "$(show_routes | wc -l)" = "2" ] && ok "routes on another device are untouched" \
        || bad "routes on another device are untouched" "$(show_routes | tr '\n' '|')"
    show_routes | grep -q "phy0-sta0 metric 40" \
        && ok "the STA override survives normalization" \
        || bad "the STA override survives normalization" "$(show_routes | tr '\n' '|')"
}

t_cycle_modem_first() {
    seed_uci; load_daemon; load_config
    modem_up; read_modem
    set_routes "wwan0 10.226.182.198 10"
    PING_FAIL_DEV=""
    cycle
    [ "$active" = "modem" ] && ok "a healthy modem is the active link" \
        || bad "a healthy modem is the active link" "active=$active"
    [ "$(show_routes)" = "default via 10.226.182.198 dev wwan0 metric 50" ] \
        && ok "cycle leaves exactly one modem default, at 50" \
        || bad "cycle leaves exactly one modem default, at 50" "$(show_routes | tr '\n' '|')"
    grep -q "metric 40" "$ROUTE_TABLE" && bad "no STA override while the modem is healthy" \
        || ok "no STA override while the modem is healthy"
}

t_cycle_sta_override_wins() {
    seed_uci; load_daemon; load_config
    modem_up; read_modem
    set_routes "wwan0 10.226.182.198 10"
    : > "$LEASE"
    printf 'interface=phy0-sta0\nrouter=10.246.20.242\ndns=10.246.20.1\n' > "$LEASE"
    STA_ADDR=1
    # Probe the modem three times so it crosses fail_threshold.
    PING_FAIL_DEV="wwan0"
    cycle; cycle; cycle
    [ "$active" = "sta" ] && ok "an unhealthy modem hands over to the STA" \
        || bad "an unhealthy modem hands over to the STA" "active=$active"
    show_routes | grep -q "phy0-sta0 metric 40" \
        && ok "the STA override is installed at 40" \
        || bad "the STA override is installed at 40" "$(show_routes | tr '\n' '|')"
    # 40 must beat the modem's metric, or the override is decoration.
    mod=$(show_routes | sed -n 's/.*wwan0 metric \([0-9]*\).*/\1/p')
    [ -n "$mod" ] && [ "$mod" -gt 40 ] \
        && ok "the modem default sits above the override" \
        || bad "the modem default sits above the override" "modem metric=$mod"
}

t_cycle_policy_sta() {
    seed_uci; load_daemon
    sed -i 's/^uplink.general.policy=auto$/uplink.general.policy=sta/' "$UCI_FILE"
    load_config
    modem_up; read_modem
    set_routes "wwan0 10.226.182.198 10"
    : > "$LEASE"
    printf 'interface=phy0-sta0\nrouter=10.246.20.242\ndns=10.246.20.1\n' > "$LEASE"
    STA_ADDR=1
    PING_FAIL_DEV=""
    cycle
    [ "$active" = "sta" ] && ok "policy=sta wins over a healthy modem" \
        || bad "policy=sta wins over a healthy modem" "active=$active"
}

t_dns_from_lease() {
    seed_uci; load_daemon; load_config
    modem_down
    : > "$LEASE"
    printf 'interface=phy0-sta0\nrouter=10.246.20.242\ndns=10.246.20.1 10.246.20.2\n' > "$LEASE"
    STA_ADDR=1
    read_sta
    cycle
    grep -q "add_list dhcp.@dnsmasq\[0\].server=10.246.20.1" "$UCI_CALLS" \
        && ok "the lease DNS is applied when sta_dns is unset" \
        || bad "the lease DNS is applied when sta_dns is unset" "$(cat "$UCI_CALLS")"
}

t_dns_override_still_works() {
    seed_uci; load_daemon
    echo "uplink.general.sta_dns=9.9.9.9" >> "$UCI_FILE"
    load_config
    modem_down
    : > "$LEASE"
    printf 'interface=phy0-sta0\nrouter=10.246.20.242\ndns=10.246.20.1\n' > "$LEASE"
    STA_ADDR=1
    cycle
    grep -q "add_list dhcp.@dnsmasq\[0\].server=9.9.9.9" "$UCI_CALLS" \
        && ok "an explicit sta_dns still overrides the lease" \
        || bad "an explicit sta_dns still overrides the lease" "$(cat "$UCI_CALLS")"
}

t_migrate_old_default() {
    seed_uci; load_daemon
    echo "uplink.general.sta_dns=8.8.8.8 8.8.4.4" >> "$UCI_FILE"
    migrate_shipped_dns
    [ -z "$(uci -q get uplink.general.sta_dns)" ] && ok "the shipped pair is removed once" \
        || bad "the shipped pair is removed once" "still $(uci -q get uplink.general.sta_dns)"
    grep -q "commit uplink" "$UCI_CALLS" && ok "the removal is committed" \
        || bad "the removal is committed"
}

t_migrate_custom_value() {
    seed_uci; load_daemon
    echo "uplink.general.sta_dns=9.9.9.9 1.1.1.1" >> "$UCI_FILE"
    migrate_shipped_dns
    [ "$(uci -q get uplink.general.sta_dns)" = "9.9.9.9 1.1.1.1" ] \
        && ok "an operator's own sta_dns is left alone" \
        || bad "an operator's own sta_dns is left alone" "$(uci -q get uplink.general.sta_dns)"
    [ -s "$UCI_CALLS" ] && bad "nothing is committed when there is nothing to migrate" \
        || ok "nothing is committed when there is nothing to migrate"
}

t_migrate_unset() {
    seed_uci; load_daemon
    migrate_shipped_dns
    [ -s "$UCI_CALLS" ] && bad "an unset sta_dns is not a migration" \
        || ok "an unset sta_dns is not a migration"
}

t_shipped_config() {
    grep -qE "^[[:space:]]*option[[:space:]]+sta_dns" "$CONF" && bad "the shipped config no longer sets sta_dns" \
        || ok "the shipped config no longer sets sta_dns"
    grep -q "^/etc/config/uplink$" "$MK" && ok "/etc/config/uplink is a conffile" \
        || bad "/etc/config/uplink is a conffile"
}

run_suite() {
    setup
    echo "== uplink-manager =="
    t_predicate
    t_no_default_route
    t_modem_down
    t_normalize_lifts_metric
    t_normalize_idempotent
    t_normalize_collapses_duplicates
    t_normalize_keeps_foreign_device
    t_cycle_modem_first
    t_cycle_sta_override_wins
    t_cycle_policy_sta
    t_dns_from_lease
    t_dns_override_still_works
    t_migrate_old_default
    t_migrate_custom_value
    t_migrate_unset
    t_shipped_config
    echo
    printf '%d passed, %d failed\n' "$PASS" "$FAIL"
    [ "$FAIL" -eq 0 ]
}

# --- self-check --------------------------------------------------------------

self_check() {
    local mutant failed=0
    mutant="$(mktemp)"

    cp "$UM" "$mutant"
    sed -i 's|@.route\[@.target="0.0.0.0"\].nexthop|@.route[@.target="0.0.0.0/0"].nexthop|' "$mutant"
    if UPLINK_MANAGER="$mutant" bash "$0" >/dev/null 2>&1; then
        echo "  FAIL mutant 'the old CIDR predicate' was not caught"
        failed=$((failed + 1))
    else
        echo "  ok   mutant 'the old CIDR predicate' caught"
    fi

    cp "$UM" "$mutant"
    sed -i '/^	drop_defaults_except "\$modem_dev" "\$modem_metric"$/d' "$mutant"
    if UPLINK_MANAGER="$mutant" bash "$0" >/dev/null 2>&1; then
        echo "  FAIL mutant 'normalize never drops the old route' was not caught"
        failed=$((failed + 1))
    else
        echo "  ok   mutant 'normalize never drops the old route' caught"
    fi

    cp "$UM" "$mutant"
    sed -i 's/^		\[ "\$met" = "\$keep" \] && continue$/		:/' "$mutant"
    if UPLINK_MANAGER="$mutant" bash "$0" >/dev/null 2>&1; then
        echo "  FAIL mutant 'normalize drops its own route too' was not caught"
        failed=$((failed + 1))
    else
        echo "  ok   mutant 'normalize drops its own route too' caught"
    fi

    cp "$UM" "$mutant"
    sed -i 's|\[ "\$(uci -q get uplink.general.sta_dns)" = "8.8.8.8 8.8.4.4" \] \|\| return 0|[ -n "$(uci -q get uplink.general.sta_dns)" ] \|\| return 0|' "$mutant"
    if UPLINK_MANAGER="$mutant" bash "$0" >/dev/null 2>&1; then
        echo "  FAIL mutant 'the migration eats any sta_dns' was not caught"
        failed=$((failed + 1))
    else
        echo "  ok   mutant 'the migration eats any sta_dns' caught"
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
