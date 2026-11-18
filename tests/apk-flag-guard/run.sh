#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-2.0-only
#
# Hardware-free tests for scripts/apk-flag-guard.sh: a shipped script must not
# pass an apk flag that can delete packages wholesale.  Both flags this refuses
# have already purged a working system once (see the guard's header), and
# neither failure announced itself as a deletion until it was too late.

set -uo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
SRC="$ROOT/scripts/apk-flag-guard.sh"
SELF_CHECK=0
[ "${1:-}" = "--self-check" ] && SELF_CHECK=1

pass=0
fail=0
ok() { pass=$((pass + 1)); printf '  ok   %s\n' "$*"; }
bad() { fail=$((fail + 1)); printf '  FAIL %s\n' "$*"; }

# fixture <body> -- writes the body to a file in a fresh temp dir and prints
# the directory.  Callers remove it.
fixture() {
	local dir
	dir=$(mktemp -d /tmp/apk-flag-guard.XXXXXX)
	printf '%s\n' "$1" > "$dir/script.sh"
	printf '%s\n' "$dir"
}

# expect <label> <want 0|1> <dir> [script]
expect() {
	local label=$1 want=$2 dir=$3 script=${4:-${SRC_ACTIVE:-$SRC}}
	local out rc
	out=$(sh "$script" "$dir" 2>&1)
	rc=$?
	if [ "$rc" -eq "$want" ]; then
		ok "$label"
	else
		bad "$label (exit $rc, want $want)"
	fi
	LAST="$out"
}

assert_mentions() {
	if printf '%s' "$LAST" | grep -qF -- "$2"; then
		ok "$1"
	else
		bad "$1 (no '$2' in: $LAST)"
	fi
}

suite() {
	local script=$1
	SRC_ACTIVE="$script"
	local d

	# --- the real trees are clean --------------------------------------
	if sh "$script" "$ROOT/packages" "$ROOT/msm89xx" >/dev/null 2>&1; then
		ok "the shipped trees carry no destructive apk flag"
	else
		bad "the shipped trees carry no destructive apk flag"
	fi

	# --- the flags are refused -----------------------------------------
	d=$(fixture 'apk upgrade --prune')
	expect "apk upgrade --prune is refused" 1 "$d"
	assert_mentions "the refusal names the file" "script.sh"
	rm -rf "$d"

	d=$(fixture 'apk del --force-broken-world foo')
	expect "apk del --force-broken-world is refused" 1 "$d"
	rm -rf "$d"

	# A flag on a continuation line has no `apk` on it; it must still count.
	d=$(fixture 'apk upgrade \

	--prune')
	expect "a flag on a continuation line is refused" 1 "$d"
	rm -rf "$d"

	# --- ordinary use is not touched ------------------------------------
	d=$(fixture 'apk upgrade')
	expect "a plain apk upgrade is allowed" 0 "$d"
	rm -rf "$d"

	d=$(fixture 'apk add --no-cache foo')
	expect "an ordinary apk add is allowed" 0 "$d"
	rm -rf "$d"

	# --- documenting the flag is not using it ---------------------------
	d=$(fixture '# never run apk upgrade --prune on these units')
	expect "a comment that names the flag is allowed" 0 "$d"
	rm -rf "$d"

	d=$(fixture '   # apk del --force-broken-world')
	expect "an indented comment that names the flag is allowed" 0 "$d"
	rm -rf "$d"

	# --- a named directory that does not exist is a usage error ---------
	if sh "$script" /nonexistent-$$ >/dev/null 2>&1; then
		bad "a missing directory is an error"
	else
		ok "a missing directory is an error"
	fi
}

printf '== apk flag guard ==\n'
suite "$SRC"

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
		file=$(mktemp /tmp/apk-flag-guard-src.XXXXXX)
		sed "$expr" "$SRC" > "$file"
		if [ "$(grep -c . "$file")" -lt 20 ]; then
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

	mutant "the pattern matches nothing" "s#^PATTERN=.*#PATTERN='^\$'#"
	mutant "comments are not excluded" "s#^PATTERN=.*#PATTERN='--(prune|force-broken-world)'#"
	mutant "a hit no longer fails the run" 's/^\tstatus=1$/\tstatus=0/'
	mutant "the exit code is ignored" 's/^exit "\$status"$/exit 0/'
fi

printf '\n%d passed, %d failed\n' "$pass" "$fail"
[ "$fail" -eq 0 ]
