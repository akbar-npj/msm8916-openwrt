#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-2.0-only
#
# Hardware-free tests for scripts/hotfix-scope.sh: which feed a publish has to
# reach.  The failure this exists for is silent on both ends -- the page offers
# the fix, apk prints "1 error" and changes nothing -- so the rule is tested
# rather than trusted.

set -uo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
SRC="$ROOT/scripts/hotfix-scope.sh"
SELF_CHECK=0
[ "${1:-}" = "--self-check" ] && SELF_CHECK=1

pass=0
fail=0
ok() { pass=$((pass + 1)); printf '  ok   %s\n' "$*"; }
bad() { fail=$((fail + 1)); printf '  FAIL %s\n' "$*"; }

# check <label> <expect 0|1> <args...>
check() {
	local label=$1 want=$2
	shift 2
	local out rc
	out=$(sh "${SRC_ACTIVE:-$SRC}" check "$@" 2>&1)
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

	# --- an ordinary package reaches every device ----------------------
	check "an ordinary package with scope=all is allowed" 0 uci-usb-gadget all false
	check "an ordinary package with scope=revision is refused" 1 msm89xx-scripts revision false
	assert_mentions "the refusal names the remedy" "scope=all"
	assert_mentions "the refusal explains where devices read" "releases/<base>"

	# --- a pinned package stays out of the shared feed -----------------
	check "base-files with scope=revision is allowed" 0 base-files revision true
	check "base-files with scope=revision and no target flag is allowed" 0 base-files revision false
	check "kernel with scope=revision is allowed" 0 kernel revision false
	check "libc with scope=revision is allowed" 0 libc revision false
	check "base-files with scope=all is refused" 1 base-files all true
	assert_mentions "the refusal explains the pin" "version-pinned"
	check "kernel with scope=all is refused" 1 kernel all false

	# --- a target package is pinned by definition ----------------------
	check "an unknown target package with scope=revision is allowed" 0 anything revision true
	check "an unknown target package with scope=all is refused" 1 anything all true

	# --- feed maintenance needs no scope ------------------------------
	check "an empty package is always allowed" 0 "" revision false
	check "an empty package with scope=all is allowed" 0 "" all false

	# --- the helper the post-publish check calls ----------------------
	if sh "${SRC_ACTIVE:-$SRC}" feed-local-only base-files true; then
		ok "feed-local-only says base-files is revision-only"
	else
		bad "feed-local-only says base-files is revision-only"
	fi
	if sh "${SRC_ACTIVE:-$SRC}" feed-local-only msm89xx-scripts false; then
		bad "feed-local-only says an ordinary package is shared"
	else
		ok "feed-local-only says an ordinary package is shared"
	fi
	if sh "${SRC_ACTIVE:-$SRC}" feed-local-only whatever true; then
		ok "feed-local-only honours target_package"
	else
		bad "feed-local-only honours target_package"
	fi

	# --- an unknown subcommand is an error, not a silent pass ---------
	if sh "${SRC_ACTIVE:-$SRC}" nonsense >/dev/null 2>&1; then
		bad "an unknown subcommand fails"
	else
		ok "an unknown subcommand fails"
	fi
}

printf '== hotfix scope ==\n'
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
		file=$(mktemp /tmp/hotfix-scope.XXXXXX)
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

	mutant "the pinned list is empty" 's/^PINNED=.*$/PINNED=""/'
	mutant "revision accepted for an ordinary package" 's/^\t\[ "\$scope" = "all" \] ||$/true ||/'
	mutant "all accepted for a pinned package" 's/^\t\t\[ "\$scope" = "revision" \] ||$/true ||/'
	mutant "target_package ignored" 's/^\t\[ "\$target" = "true" \] && return 0$/:/'
	mutant "feed-local-only always false" 's/^\t\t\[ "\$pkg" = "\$p" \] && return 0$/\t:/'
fi

printf '\n%d passed, %d failed\n' "$pass" "$fail"
[ "$fail" -eq 0 ]
