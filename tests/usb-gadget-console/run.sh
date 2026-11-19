#!/usr/bin/env bash
# Tests for the USB gadget's serial console (packages/uci-usb-gadget).
#
# The console is a supervisor loop rather than an inittab entry, because procd
# parses /etc/inittab once before this script runs and ignores SIGHUP (#131).
# These tests drive the loop against a real pty, so "the shell starts" is
# observed rather than assumed.
#
#   bash tests/usb-gadget-console/run.sh [--self-check]
#
# --self-check mutates the script and requires the suite to notice.

set -u

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
GADGET="${GADGET:-$ROOT/packages/uci-usb-gadget/files/usb-gadget.sh}"

PASS=0
FAIL=0

ok()  { PASS=$((PASS + 1)); printf '  ok   %s\n' "$1"; return 0; }
bad() { FAIL=$((FAIL + 1)); printf '  FAIL %s\n' "$1"; [ $# -gt 1 ] && printf '       %s\n' "$2"; return 0; }

# The script is a program, not a library: it sources /lib/functions.sh and runs
# a command dispatcher at the end.  Cut both, and point the tty at a pty of
# ours so the loop has a character device to open.
make_lib() {
    sed -e '/^\. \/lib\/functions\.sh$/d' \
        -e "s#/dev/ttyGS0#$TMP/ttyGS0#g" \
        -e 's#/usr/libexec/login.sh#/bin/sh#' \
        -e '/^# Main Entry Point/,$d' \
        "$GADGET" > "$TMP/lib.sh"
}

# Hold a pty open for the length of one test and link it where the script
# expects the gadget's tty.
pty_up() {
    python3 - "$TMP/ttyGS0" 30 <<'PY' >/dev/null 2>&1 &
import os, pty, sys, time
m, s = pty.openpty()
if os.path.lexists(sys.argv[1]):
    os.unlink(sys.argv[1])
os.symlink(os.ttyname(s), sys.argv[1])
time.sleep(30)
PY
    PTY_PID=$!
    local i=0
    while [ ! -c "$TMP/ttyGS0" ] && [ $i -lt 50 ]; do
        i=$((i + 1)); sleep 0.1
    done
}

pty_down() {
    [ -n "${PTY_PID:-}" ] && kill "$PTY_PID" 2>/dev/null
    wait "$PTY_PID" 2>/dev/null
    rm -f "$TMP/ttyGS0"
    PTY_PID=""
}

alive() { kill -0 "$1" 2>/dev/null; }

# Run a snippet with the library loaded and the pidfiles in $TMP.
with_lib() {
    (
        set -u
        # shellcheck disable=SC1090
        . "$TMP/lib.sh"
        ACM_CONSOLE_PIDFILE="$TMP/console.pid"
        ACM_SHELL_PIDFILE="$TMP/shell.pid"
        "$@"
    ) 2>&1
}

console_start() { with_lib acm_console_start; }
console_stop()  { with_lib acm_console_stop; }
console_running() { with_lib acm_console_running; }
shell_pid() { cat "$TMP/shell.pid" 2>/dev/null; }
super_pid() { cat "$TMP/console.pid" 2>/dev/null; }

reset() {
    [ -f "$TMP/console.pid" ] && kill -9 "$(cat "$TMP/console.pid")" 2>/dev/null
    [ -f "$TMP/shell.pid" ] && kill -9 "$(cat "$TMP/shell.pid")" 2>/dev/null
    rm -f "$TMP/console.pid" "$TMP/shell.pid"
    pty_down
}

setup() {
    TMP="$(mktemp -d)"
    trap 'reset; rm -rf "$TMP"' EXIT
    make_lib
    PTY_PID=""
}

# --- the supervisor ---------------------------------------------------------

t_waits_for_node() {
    console_start
    sleep 1
    local sup
    sup="$(super_pid)"
    alive "$sup" \
        && ok "the supervisor starts without the tty" \
        || bad "the supervisor starts without the tty" "supervisor='$sup'"
    [ -z "$(shell_pid)" ] \
        && ok "no shell is spawned while the tty is absent" \
        || bad "no shell is spawned while the tty is absent" "shell='$(shell_pid)'"
}

t_starts_when_node_appears() {
    pty_up
    local i=0 pid
    while [ $i -lt 40 ]; do
        pid="$(shell_pid)"
        [ -n "$pid" ] && alive "$pid" && break
        i=$((i + 1)); sleep 0.1
    done
    [ -n "${pid:-}" ] && alive "$pid" \
        && ok "the shell starts once the tty appears" \
        || bad "the shell starts once the tty appears" "pid='${pid:-}'"
    case "$(ps -o tty= -p "${pid:-0}" 2>/dev/null)" in
        pts/*) ok "the shell has the gadget tty as its terminal" ;;
        *)     bad "the shell has the gadget tty as its terminal" \
                   "tty='$(ps -o tty= -p "${pid:-0}" 2>/dev/null)'" ;;
    esac
}

t_respawns() {
    local first second i=0
    first="$(shell_pid)"
    kill -9 "$first" 2>/dev/null
    while [ $i -lt 60 ]; do
        second="$(shell_pid)"
        [ -n "$second" ] && [ "$second" != "$first" ] && alive "$second" && break
        i=$((i + 1)); sleep 0.1
    done
    [ -n "${second:-}" ] && [ "$second" != "$first" ] && alive "$second" \
        && ok "the shell is reopened after it exits" \
        || bad "the shell is reopened after it exits" "first=$first second='${second:-}'"
}

t_stop_kills_both() {
    local sup sh
    sup="$(super_pid)"; sh="$(shell_pid)"
    console_stop
    sleep 1
    alive "$sup" \
        && bad "stop ends the supervisor" "still alive: $sup" \
        || ok "stop ends the supervisor"
    alive "$sh" \
        && bad "stop ends the shell" "still alive: $sh" \
        || ok "stop ends the shell"
    [ ! -f "$TMP/console.pid" ] && [ ! -f "$TMP/shell.pid" ] \
        && ok "stop removes both pidfiles" \
        || bad "stop removes both pidfiles"
}

t_running() {
    console_running \
        && bad "the console is reported down after stop" \
        || ok "the console is reported down after stop"
    console_start
    sleep 1
    console_running \
        && ok "the console is reported up after start" \
        || bad "the console is reported up after start"
}

# --- the tree ---------------------------------------------------------------

t_tree() {
    grep -q 'ttyGS0::askfirst' "$GADGET" \
        && bad "the inittab append is gone" \
        || ok "the inittab append is gone"
    grep -q '^[^#]*kill -HUP 1' "$GADGET" \
        && bad "the no-op SIGHUP is gone" \
        || ok "the no-op SIGHUP is gone"
    grep -q '^        acm_console_start$' "$GADGET" \
        && ok "the console is started from setup" \
        || bad "the console is started from setup" "$(grep -n 'acm_console_start' "$GADGET" | tr '\n' '|')"
    grep -q 'exec /usr/libexec/login.sh' "$GADGET" \
        && ok "the console runs login.sh, not a bare shell" \
        || bad "the console runs login.sh, not a bare shell"
    grep -q 'acm_console_stop' <(sed -n '/^teardown_gadget()/,/^}/p' "$GADGET") \
        && ok "teardown stops the console" \
        || bad "teardown stops the console"
}

run_suite() {
    setup
    echo "== usb-gadget-console =="
    t_waits_for_node
    t_starts_when_node_appears
    t_respawns
    t_stop_kills_both
    t_running
    t_tree
    echo
    printf '%d passed, %d failed\n' "$PASS" "$FAIL"
    [ "$FAIL" -eq 0 ]
}

# --- self-check -------------------------------------------------------------

self_check() {
    local mutant failed=0
    mutant="$(mktemp)"

    cp "$GADGET" "$mutant"
    sed -i 's/^        set +e$/        :/' "$mutant"
    if GADGET="$mutant" bash "$0" >/dev/null 2>&1; then
        echo "  FAIL mutant 'the loop dies with the first shell' was not caught"
        failed=$((failed + 1))
    else
        echo "  ok   mutant 'the loop dies with the first shell' caught"
    fi

    cp "$GADGET" "$mutant"
    sed -i 's#^    for f in "\$ACM_CONSOLE_PIDFILE" "\$ACM_SHELL_PIDFILE"; do$#    for f in "$ACM_CONSOLE_PIDFILE"; do#' "$mutant"
    if GADGET="$mutant" bash "$0" >/dev/null 2>&1; then
        echo "  FAIL mutant 'the shell outlives stop' was not caught"
        failed=$((failed + 1))
    else
        echo "  ok   mutant 'the shell outlives stop' caught"
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
