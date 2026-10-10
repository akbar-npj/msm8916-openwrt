#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-2.0-only
#
# Hardware-free tests for himi-ok-guard and himi-ok.sh (no root, no modem, no OpenWrt).
#
#   tests/himi-ok-guard/run.sh [--self-check]
#
# The guard is copied to a scratch dir with its device/binary/lock paths redirected, and run
# against stubs for diag_nv, uci, logger and flock. The stub diag_nv prints the same output
# shape the guard parses ("status=0x0000", "OOOO  xx xx ... |ascii|", "VERIFY: MATCH") and
# keeps item 2500 in a file, so the tests assert on what ends up in the item and on every
# call the guard made.
#
# --self-check also applies 6 mutants, each removing one property; the matching check must go red.
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
SRC=${SRC:-$(cd "$HERE/../../msm89xx/base-files" && pwd)}
GUARD_SRC=$SRC/usr/sbin/himi-ok-guard
BOARD_SRC=$SRC/lib/himi-ok.sh

PASS=0; FAIL=0
ROOT=$(mktemp -d "${TMPDIR:-/tmp}/himi-ok-test.XXXXXX"); trap 'rm -rf "$ROOT"' EXIT
check() { local n=$1; shift
    if "$@" >/dev/null 2>&1; then PASS=$((PASS + 1)); printf '  ok   %s\n' "$n"
    else FAIL=$((FAIL + 1)); printf '  FAIL %s\n' "$n"; fi; }

# --- stubs ---------------------------------------------------------------------------------
BIN=$ROOT/bin; mkdir -p "$BIN"
cat > "$BIN/diag_nv" <<'EOS'
#!/bin/sh
# usage: diag_nv read 2500 | diag_nv write 2500 <hex>
echo "$1" >> "$T/nv.calls"
case "$1" in
read)
	# a failed read still returns an (all-zero) buffer - the status is the only signal
	if [ "${NV_MODE:-ok}" = fail ]; then echo "status=0x0007"; : > "$T/item"; else echo "status=0x0000"; fi
	# 128-byte payload in 8 lines of 16 bytes, "OFFS  xx .. xx |ascii|"
	python3 - "$T/item" <<'PY'
import sys
d = open(sys.argv[1], "rb").read()[:128].ljust(128, b"\0")
for o in range(0, 128, 16):
    c = d[o:o+16]
    print("%04x  %s |%s|" % (o, " ".join("%02x" % b for b in c), "".join(chr(b) if 32 <= b < 127 else "." for b in c)))
PY
	;;
write)
	printf '%s' "$3" | python3 -c "import sys; sys.stdout.buffer.write(bytes.fromhex(sys.stdin.read()))" > "$T/item"
	echo "VERIFY: MATCH"
	;;
esac
EOS
cat > "$BIN/uci" <<'EOS'
#!/bin/sh
# uci -q get <key>: value from $T/uci/<key>, else exit 1
[ "$1" = -q ] && shift
[ "$1" = get ] || exit 1
[ -f "$T/uci/$2" ] && { cat "$T/uci/$2"; exit 0; }
exit 1
EOS
cat > "$BIN/logger" <<'EOS'
#!/bin/sh
[ "$1" = -t ] && shift 2
echo "$*" >> "$T/log"
EOS
cat > "$BIN/flock" <<'EOS'
#!/bin/sh
echo "$*" >> "$T/flock.calls"
EOS
chmod +x "$BIN"/*

# --- a scenario: fresh scratch state, guard copy with redirected paths ----------------------
# scenario NAME ITEM_PYEXPR [GUARD_FILE]
scenario() {
    T=$ROOT/$1; export T; rm -rf "$T"; mkdir -p "$T/uci"; : > "$T/nv.calls"; : > "$T/log"; : > "$T/flock.calls"
    python3 -c "import sys; sys.stdout.buffer.write($2)" > "$T/item"
    : > "$T/rpmsg0"
    sed -e "s#^DEV=.*#DEV=$T/rpmsg0#" -e "s#^NV=.*#NV=$BIN/diag_nv#" \
        -e "s#^DIAG_LOCK=\([^ ]*\)#DIAG_LOCK=$T/lock #" \
        -e 's#\[ -c "\$DEV" \]#[ -e "$DEV" ]#' "${3:-$GUARD_SRC}" > "$T/guard"
}
# run one poll cycle: the loop's first iteration runs at once; stop it before the sleep elapses
run_once() {
    PATH="$BIN:$PATH" sh "$T/guard" & local p=$!
    sleep 1.5; kill "$p" 2>/dev/null; wait "$p" 2>/dev/null
}
item_is_token() { [ "$(head -c 7 "$T/item")" = "HiMI_OK" ]; }
nwrites() { grep -c '^write' "$T/nv.calls"; }
nreads()  { grep -c '^read'  "$T/nv.calls"; }

run_all() {
    # all-zero item -> exactly one write, and it is the token
    scenario zeros 'bytes(128)'; run_once
    check "all-zero item: token written"                       item_is_token
    check "all-zero item: exactly one write"                   [ "$(nwrites)" = 1 ]
    check "all-zero item: the write is logged"                 grep -q 'wrote HiMI_OK' "$T/log"

    # token already present -> read only
    scenario token 'b"HiMI_OK"'; run_once
    check "token present: no write"                            [ "$(nwrites)" = 0 ]
    check "token present: item still reads"                    [ "$(nreads)" -ge 1 ]

    # somebody else's data -> never overwritten, warned once
    scenario other 'b"REAL FACTORY DATA 123"'; run_once
    check "foreign data: never overwritten"                    [ "$(nwrites)" = 0 ]
    check "foreign data: left intact"                          [ "$(head -c 21 "$T/item")" = "REAL FACTORY DATA 123" ]
    check "foreign data: warned"                               grep -q 'non-zero data' "$T/log"

    # data that is zero in the FIRST bytes only must still count as non-zero
    scenario late 'bytes(127) + b"\x01"'; run_once
    check "non-zero only in the last byte: not overwritten"    [ "$(nwrites)" = 0 ]

    # read fails (SP-locked / absent item) -> no write
    scenario readfail 'bytes(128)'; NV_MODE=fail run_once; unset NV_MODE
    check "failed read (status != 0): no write"                [ "$(nwrites)" = 0 ]
    export NV_MODE=; unset NV_MODE

    # every cycle runs under the shared DIAG lock
    scenario lock 'bytes(128)'; run_once
    check "cycle takes the lock (flock -x) and releases it (-u)" bash -c "grep -q -- '-x' '$T/flock.calls' && grep -q -- '-u' '$T/flock.calls'"

    # uci switches
    scenario off 'bytes(128)'; echo 0 > "$T/uci/modem-watchdog.recovery.himi_ok_enabled"
    PATH="$BIN:$PATH" sh "$T/guard"; rc=$?
    check "himi_ok_enabled=0: exits 0 and touches nothing"     bash -c "[ $rc = 0 ] && [ ! -s '$T/nv.calls' ]"

    scenario noelf 'bytes(128)'; sed -i.bak "s#^NV=.*#NV=$T/missing#" "$T/guard"
    PATH="$BIN:$PATH" sh "$T/guard"; rc=$?
    check "diag_nv missing: exit 1, no crash loop"             [ "$rc" = 1 ]

    # a poll interval below 5 s, or junk, is clamped
    scenario clamp 'bytes(128)'; echo 1 > "$T/uci/modem-watchdog.recovery.himi_ok_interval"; run_once
    check "interval 1 is clamped to 5"                         grep -q 'interval=5s' "$T/log"
    scenario junk 'bytes(128)'; echo abc > "$T/uci/modem-watchdog.recovery.himi_ok_interval"; run_once
    check "non-numeric interval falls back to 20"              grep -q 'interval=20s' "$T/log"

    # recovery after a modem restart clears the item again (interval 5 s so a second cycle fits)
    scenario restart 'bytes(128)'; echo 5 > "$T/uci/modem-watchdog.recovery.himi_ok_interval"
    PATH="$BIN:$PATH" sh "$T/guard" & p=$!
    sleep 1.5; head -c 128 /dev/zero > "$T/item"          # modem restarted: item cleared
    sleep 5; kill "$p" 2>/dev/null; wait "$p" 2>/dev/null
    check "item cleared mid-run (modem restart): written again" [ "$(nwrites)" = 2 ]

    # --- the board gate (himi-ok.sh) ---
    not() { ! "$@"; }
    gate() { # board [uci_list]
        local s=$ROOT/gate; rm -rf "$s"; mkdir -p "$s/uci"
        [ -z "${2:-}" ] || echo "$2" > "$s/uci/modem-watchdog.recovery.himi_ok_boards"
        T=$s PATH="$BIN:$PATH" sh -c ". '$BOARD_SRC'; himi_ok_board_match \"\$1\"" _ "$1"
    }
    check "gate: hmu05 matches"                                gate "generic,hmu05"
    check "gate: yiming,uz801-v3 matches"                      gate "yiming,uz801-v3"
    check "gate: uf02 matches"                                 gate "generic,uf02"
    check "gate: ufi001b does NOT match"                       not gate "ufi001b"
    check "gate: empty board name does not match"              not gate ""
    check "gate: uci list overrides the default"               gate "vendor,newboard" "*newboard*"
    check "gate: ...and a board outside the override fails"    not gate "yiming,uz801-v3" "*newboard*"
    # a file named like a pattern in the CWD must not replace the glob
    mkdir -p "$ROOT/cwd"; : > "$ROOT/cwd/hmu05_fsg_extracted"
    check "gate: CWD files do not glob-expand the list"        bash -c 'cd "$1" && T="$2" PATH="$3:$PATH" sh -c ". \"$4\"; himi_ok_board_match generic,hmu05"' _ "$ROOT/cwd" "$ROOT/cwd-uci" "$BIN" "$BOARD_SRC"
}

MUT_FAIL=0
mutant() { # name old new
    local m="$ROOT/mut-$1"; rm -rf "$m"; mkdir -p "$m/usr/sbin" "$m/lib"
    cp "$GUARD_SRC" "$m/usr/sbin/"; cp "$BOARD_SRC" "$m/lib/"
    local f=$m/$4
    if ! python3 - "$f" "$2" "$3" <<'PY'
import sys
p, old, new = sys.argv[1:4]
s = open(p).read()
if old not in s: sys.exit(1)
open(p, "w").write(s.replace(old, new))
PY
    then echo "  FAIL mutant $1: could not be applied"; MUT_FAIL=1; return; fi
    if SRC="$m" bash "$HERE/run.sh" >/dev/null 2>&1; then echo "  FAIL mutant $1 SURVIVED"; MUT_FAIL=1
    else echo "  ok   mutant $1 caught"; fi
}

echo "himi-ok-guard tests"
run_all
echo "passed $PASS, failed $FAIL"
[ "$FAIL" = 0 ] || exit 1

if [ "${1:-}" = --self-check ]; then
    echo; echo "self-check: each mutant removes one property; its check must go red"
    G=usr/sbin/himi-ok-guard
    mutant no-zero-gate       'if ! payload_all_zero "$out"; then' 'if false; then' $G
    mutant writes-on-failed-read 'if ! printf '"'"'%s'"'"' "$out" | grep -q "status=0x0000"; then' 'if false; then' $G
    mutant no-lock            'flock -x 200' ':' $G
    mutant no-unlock          'flock -u 200' ':' $G
    mutant first-byte-only    "case \"\$hex\" in
		*[!0]*) return 1 ;;
	esac" 'case "$hex" in
		0*) ;; *) return 1 ;;
	esac' $G
    mutant gate-glob-expands  'set -f' ':' lib/himi-ok.sh
    [ "$MUT_FAIL" = 0 ] || exit 1
    echo "self-check passed"
fi
