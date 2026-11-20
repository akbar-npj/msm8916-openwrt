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
        -e "s#/sys/class/tty/ttyGS0/dev#$TMP/dev#g" \
        -e 's#/usr/libexec/login.sh#/bin/sh#' \
        -e '/^# Main Entry Point/,$d' \
        "$GADGET" > "$TMP/lib.sh"
}

# Hold a pty open for the length of one test and link it where the script
# expects the gadget's tty.
pty_up() {
    python3 - "$TMP/ttyGS0" "$TMP/dev" <<'PY' >/dev/null 2>&1 &
import os, pty, sys, time
m, s = pty.openpty()
name = os.ttyname(s)
# Keep only the master open.  The slave node stays valid while the master is,
# and a slave fd here would make this helper look like a stale holder to the
# reap.
os.close(s)
if os.path.lexists(sys.argv[1]):
    os.unlink(sys.argv[1])
os.symlink(name, sys.argv[1])
# What /sys/class/tty/ttyGS0/dev would say for this pty.
st = os.stat(name)
open(sys.argv[2], 'w').write("%d:%d\n" % (os.major(st.st_rdev), os.minor(st.st_rdev)))
time.sleep(60)
PY
    PTY_PID=$!
    local i=0
    while [ ! -c "$TMP/ttyGS0" ] && [ $i -lt 50 ]; do
        i=$((i + 1)); sleep 0.1
    done
}

pty_down() {
    # No wait: the helper sleeps for a minute by design and would hold the
    # suite open at exit.
    [ -n "${PTY_PID:-}" ] && kill -9 "$PTY_PID" 2>/dev/null
    PTY_PID=""
    rm -f "$TMP/ttyGS0"
}

alive() { kill -0 "$1" 2>/dev/null; }

# Run a snippet with the library loaded and the pidfile in $TMP.
with_lib() {
    (
        set -u
        # shellcheck disable=SC1090
        . "$TMP/lib.sh"
        ACM_CONSOLE_PIDFILE="$TMP/console.pid"
        "$@"
    ) 2>&1
}

console_start() { with_lib acm_console_start; }
console_stop()  { with_lib acm_console_stop; }
console_running() { with_lib acm_console_running; }
super_pid() { cat "$TMP/console.pid" 2>/dev/null; }

# The shell writes no pidfile -- it is its own session -- so find it the way the
# script does: whoever has our pty as its controlling terminal.
shell_pid() {
    local want pid tty
    want="$(readlink -f "$TMP/ttyGS0" 2>/dev/null)" || return 0
    [ -n "$want" ] || return 0
    ps -A -o pid= -o tty= 2>/dev/null | while read -r pid tty; do
        [ "/dev/$tty" = "$want" ] || continue
        echo "$pid"
        break
    done
}

reset() {
    [ -f "$TMP/console.pid" ] && kill -9 "$(cat "$TMP/console.pid")" 2>/dev/null
    local pid; pid="$(shell_pid)"
    [ -n "$pid" ] && kill -9 "$pid" 2>/dev/null
    rm -f "$TMP/console.pid"
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

t_survives_hup() {
    local sup
    sup="$(super_pid)"
    kill -HUP "$sup" 2>/dev/null
    sleep 1
    alive "$sup" \
        && ok "the supervisor survives a hangup" \
        || bad "the supervisor survives a hangup" "killed by HUP: $sup"
}

t_reaps_stale() {
    # A login left behind by procd's old inittab action, or one started by
    # hand.  Two shells on one tty interleave their prompts.  The holder writes
    # its own pid, because setsid may fork and $! would then be a dead parent.
    # The console is down first: stealing a controlling terminal from another
    # session needs CAP_SYS_ADMIN, so only root can do it on the device.
    console_stop
    sleep 1
    rm -f "$TMP/stray.pid"
    setsid -c sh -c 'echo $$ > "$1"; exec sleep 30' sh "$TMP/stray.pid" \
        <"$TMP/ttyGS0" >/dev/null 2>&1 &
    local stray="" i=0
    while [ $i -lt 40 ]; do
        stray="$(cat "$TMP/stray.pid" 2>/dev/null)"
        [ -n "$stray" ] && break
        i=$((i + 1)); sleep 0.1
    done
    [ -n "$stray" ] || { bad "the stray holder starts" "no pid"; return 0; }
    console_start
    sleep 1
    alive "$stray" \
        && bad "a stale holder is cleared before the console starts" "still alive: $stray" \
        || ok "a stale holder is cleared before the console starts"
    kill -9 "$stray" 2>/dev/null
    wait "$stray" 2>/dev/null
    local pid; pid="$(shell_pid)"
    [ -n "$pid" ] && alive "$pid" \
        && ok "the console still starts after the reap" \
        || bad "the console still starts after the reap" "shell='${pid:-}'"
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
    [ ! -f "$TMP/console.pid" ] \
        && ok "stop removes the pidfile" \
        || bad "stop removes the pidfile"
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
    grep -q 'exec setsid -c /usr/libexec/login.sh' "$GADGET" \
        && ok "the console runs login.sh, not a bare shell" \
        || bad "the console runs login.sh, not a bare shell"
    # The redirection must be exec'd in a subshell of its own.  Inline on the
    # setsid line it leaks onto the loop, and the loop then holds the tty and
    # echoes the login banner back as input.
    grep -q '^                    exec </dev/ttyGS0 >/dev/ttyGS0 2>&1$' "$GADGET" \
        && ok "the tty is opened in a subshell of its own" \
        || bad "the tty is opened in a subshell of its own"
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
    t_survives_hup
    t_reaps_stale
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
    sed -i '/^acm_console_stop()/,/^}/ s/^    acm_console_reap$/    :/' "$mutant"
    if GADGET="$mutant" bash "$0" >/dev/null 2>&1; then
        echo "  FAIL mutant 'the shell outlives stop' was not caught"
        failed=$((failed + 1))
    else
        echo "  ok   mutant 'the shell outlives stop' caught"
    fi

    cp "$GADGET" "$mutant"
    sed -i "s/^        trap '' HUP\$/        :/" "$mutant"
    if GADGET="$mutant" bash "$0" >/dev/null 2>&1; then
        echo "  FAIL mutant 'a hangup takes the console down' was not caught"
        failed=$((failed + 1))
    else
        echo "  ok   mutant 'a hangup takes the console down' caught"
    fi

    cp "$GADGET" "$mutant"
    sed -i "s/^    acm_console_reap\$/    :/" "$mutant"
    if GADGET="$mutant" bash "$0" >/dev/null 2>&1; then
        echo "  FAIL mutant 'a stale holder is left on the tty' was not caught"
        failed=$((failed + 1))
    else
        echo "  ok   mutant 'a stale holder is left on the tty' caught"
    fi

    cp "$GADGET" "$mutant"
    sed -i '\#^                    exec </dev/ttyGS0 >/dev/ttyGS0 2>&1$#d' "$mutant"
    if GADGET="$mutant" bash "$0" >/dev/null 2>&1; then
        echo "  FAIL mutant 'the shell has no controlling tty' was not caught"
        failed=$((failed + 1))
    else
        echo "  ok   mutant 'the shell has no controlling tty' caught"
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
