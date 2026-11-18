#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-2.0-only
#
# Hardware-free tests for the private-branch guard:
#
#   .githooks/pre-push          keeps private work off a public remote, and the
#                               private branch on top of main
#   scripts/branch-guard.sh     refuses a pull request into main from a private
#                               branch unless it is labelled
#
# Both are guards that fail silently when they are wrong: a hook that allows
# everything looks exactly like a hook with nothing to do.  Each rule therefore
# gets a fixture that must be refused and a near-miss that must be allowed, and
# --self-check mutates the scripts and requires the suite to notice.

set -uo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
HOOK="$ROOT/.githooks/pre-push"
BRANCH_GUARD="$ROOT/scripts/branch-guard.sh"
SELF_CHECK=0
[ "${1:-}" = "--self-check" ] && SELF_CHECK=1

pass=0
fail=0
ok() { pass=$((pass + 1)); printf '  ok   %s\n' "$*"; }
bad() { fail=$((fail + 1)); printf '  FAIL %s\n' "$*"; }

ZERO=0000000000000000000000000000000000000000

# --- fixture ---------------------------------------------------------------
#
# A public bare repo, a private bare repo, and a clone that has both.  main has
# one commit; test/volte sits on top of it with one commit that exists nowhere
# else, and is pushed to the private remote only -- the shape the real
# repository has.

T=$(mktemp -d /tmp/private-guard.XXXXXX)
trap 'rm -rf "$T"' EXIT

PUB="$T/msm8916-openwrt.git"
PRIV="$T/msm8916-openwrt-volte.git"
WORK="$T/work"

git init -q --bare --initial-branch=main "$PUB"
git init -q --bare --initial-branch=main "$PRIV"

git clone -q "$PUB" "$WORK"
git -C "$WORK" config user.email t@t
git -C "$WORK" config user.name t
git -C "$WORK" config commit.gpgsign false

printf 'base\n' >"$WORK/base.txt"
git -C "$WORK" add base.txt
git -C "$WORK" commit -qm "base"
git -C "$WORK" push -q origin main
git -C "$WORK" remote add private "$PRIV"

git -C "$WORK" checkout -q -b test/volte
printf 'private\n' >"$WORK/private.txt"
git -C "$WORK" add private.txt
git -C "$WORK" commit -qm "private work"
git -C "$WORK" push -q "$PRIV" test/volte
git -C "$WORK" fetch -q private
git -C "$WORK" checkout -q main

# a branch that is a legitimate fix: one commit on top of main
git -C "$WORK" checkout -q -b fix/clean
printf 'fix\n' >"$WORK/fix.txt"
git -C "$WORK" add fix.txt
git -C "$WORK" commit -qm "a fix"
git -C "$WORK" checkout -q main

# a branch that merged the private work: the accident the hook is for
git -C "$WORK" checkout -q -b fix/leak
git -C "$WORK" merge -q --no-edit test/volte
git -C "$WORK" checkout -q main

# run_hook <remote-name> <url> <local-ref> <local-sha> <remote-ref> <remote-sha>
run_hook() {
	local out
	out=$(printf '%s %s %s %s\n' "$3" "$4" "$5" "$6" |
		(cd "$WORK" && sh "${HOOK_ACTIVE:-$HOOK}" "$1" "$2") 2>&1)
	RC=$?
	LAST=$out
}

sha() { git -C "$WORK" rev-parse "$1"; }

expect() { # expect <label> <want> <got>
	if [ "$3" -eq "$2" ]; then
		ok "$1"
	else
		bad "$1 (exit $3, want $2)"
	fi
}

mentions() { # mentions <label> <needle>
	if printf '%s' "$LAST" | grep -qF -- "$2"; then
		ok "$1"
	else
		bad "$1 (no '$2' in: $(printf '%s' "$LAST" | head -3))"
	fi
}

# --- the hook --------------------------------------------------------------

hook_suite() {
	HOOK_ACTIVE=$1

	# 1. a test/* branch never goes to a public remote
	run_hook origin "$PUB" refs/heads/test/volte "$(sha test/volte)" \
		refs/heads/test/volte "$ZERO"
	expect "test/* is refused on a public remote" 1 "$RC"
	mentions "the refusal names the private remote" "git push private test/volte"

	# 2. ... and an unknown remote counts as public
	run_hook scratch "file://$T/elsewhere.git" refs/heads/test/volte \
		"$(sha test/volte)" refs/heads/test/volte "$ZERO"
	expect "test/* is refused on an unknown remote" 1 "$RC"

	# 3. a fix branch that carries the private commit is refused
	run_hook origin "$PUB" refs/heads/fix/leak "$(sha fix/leak)" \
		refs/heads/fix/leak "$ZERO"
	expect "a fix branch carrying private commits is refused" 1 "$RC"
	mentions "the refusal quotes the private commit" "private work"

	# 4. a fix branch of its own is allowed
	run_hook origin "$PUB" refs/heads/fix/clean "$(sha fix/clean)" \
		refs/heads/fix/clean "$ZERO"
	expect "an ordinary fix branch is allowed" 0 "$RC"

	# 5. ... including an update to an already published branch
	run_hook origin "$PUB" refs/heads/fix/clean "$(sha fix/clean)" \
		refs/heads/fix/clean "$(sha main)"
	expect "an update to a published branch is allowed" 0 "$RC"

	# 6. deleting a branch publishes no commit
	run_hook origin "$PUB" "(delete)" "$ZERO" refs/heads/fix/clean "$(sha main)"
	expect "a deletion is allowed" 0 "$RC"

	# 7. pushing test/* to the private remote is the intended path
	run_hook private "$PRIV" refs/heads/test/volte "$(sha test/volte)" \
		refs/heads/test/volte "$ZERO"
	expect "test/* is allowed on the private remote" 0 "$RC"

	# 8. ... but only while it contains main
	git -C "$WORK" checkout -q main
	printf 'next\n' >"$WORK/next.txt"
	git -C "$WORK" add next.txt
	git -C "$WORK" commit -qm "main moves on"
	git -C "$WORK" push -q origin main
	git -C "$WORK" fetch -q origin
	git -C "$WORK" checkout -q fix/clean

	run_hook private "$PRIV" refs/heads/test/volte "$(sha test/volte)" \
		refs/heads/test/volte "$ZERO"
	expect "a private branch behind main is refused" 1 "$RC"
	mentions "the refusal names the sync command" "scripts/private-sync.sh sync"

	# 9. a branch that has main is allowed again
	git -C "$WORK" checkout -q test/volte
	git -C "$WORK" merge -q --no-edit main
	git -C "$WORK" checkout -q main
	run_hook private "$PRIV" refs/heads/test/volte "$(sha test/volte)" \
		refs/heads/test/volte "$ZERO"
	expect "a private branch that has main is allowed" 0 "$RC"

	# 10. a public push of a branch with no private commits is unaffected by
	#     any of this
	git -C "$WORK" checkout -q fix/clean
	git -C "$WORK" merge -q --no-edit main
	git -C "$WORK" checkout -q main
	run_hook origin "$PUB" refs/heads/fix/clean "$(sha fix/clean)" \
		refs/heads/fix/clean "$(sha main)"
	expect "a fix branch with main merged in is allowed" 0 "$RC"
}

# --- the pull-request guard ------------------------------------------------

branch_suite() {
	local bg=$1

	bg_run() { # bg_run <head> <base> [labels]
		LAST=$(sh "$bg" "$1" "$2" "${3:-}" 2>&1)
		RC=$?
	}

	bg_run test/volte-openimsd main
	expect "a test/* pull request into main is refused" 1 "$RC"
	mentions "the refusal offers the label" "publish-private"

	bg_run test/volte-openimsd main "publish-private"
	expect "the same pull request is allowed when labelled" 0 "$RC"

	bg_run test/volte-openimsd main "bug hotfix"
	expect "an unrelated label does not grant permission" 1 "$RC"

	bg_run private/thing main
	expect "a private/* pull request is refused" 1 "$RC"

	bg_run wip/thing staging-main
	expect "a wip/* pull request into staging-main is refused" 1 "$RC"

	bg_run fix/clean main
	expect "a fix/* pull request is allowed" 0 "$RC"

	bg_run edl-msm8916 main
	expect "an ordinary branch name is allowed" 0 "$RC"

	bg_run test/volte-openimsd feat/something
	expect "a pull request that does not target main is allowed" 0 "$RC"
}

printf '== private branch guard ==\n'
hook_suite "$HOOK"
branch_suite "$BRANCH_GUARD"

if [ "$fail" -ne 0 ]; then
	printf '\n%d passed, %d failed\n' "$pass" "$fail"
	exit 1
fi

# --------------------------------------------------------------------------
# Self-check: each mutant must make the suite fail.
# --------------------------------------------------------------------------
if [ "$SELF_CHECK" -eq 1 ]; then
	printf '\n== self-check (mutants must be caught) ==\n'

	mutant() { # mutant <name> <file> <sed expression>
		local name=$1 src=$2 expr=$3 copy
		copy=$(mktemp "/tmp/private-guard-src.XXXXXX")
		sed "$expr" "$src" >"$copy"
		if [ "$(grep -c . "$copy")" -lt 10 ]; then
			bad "$name (sed produced an empty script)"
			rm -f "$copy"
			return
		fi
		if [ "$src" = "$HOOK" ]; then
			( HOOK_ACTIVE=$copy; pass=0; fail=0; hook_suite "$copy" >/dev/null 2>&1
			  [ "$fail" -eq 0 ] ) && bad "$name (mutant survived)" || ok "$name caught"
		else
			( pass=0; fail=0; branch_suite "$copy" >/dev/null 2>&1
			  [ "$fail" -eq 0 ] ) && bad "$name (mutant survived)" || ok "$name caught"
		fi
		rm -f "$copy"
	}

	# the test/* refusal
	mutant "test/* is not refused on a public remote" "$HOOK" \
		's#^\trefs/heads/test/\*)$#\trefs/heads/none/*)#'
	# the leak comparison
	mutant "the private-commit comparison finds nothing" "$HOOK" \
		's#^\tleaked=.*#\tleaked=#'
	# the sync rule
	mutant "the sync rule never fires" "$HOOK" \
		's#^\tbehind=.*#\tbehind=0#'
	# fail-closed on an unknown remote
	mutant "an unknown remote is treated as private" "$HOOK" \
		's#^\*"\$PRIVATE_URL"\*) is_private=1 ;;#*) is_private=1 ;;#'

	# the label gate
	mutant "the publish-private label is ignored" "$BRANCH_GUARD" \
		's#^\t\[ "\$label" = "publish-private" \] && exit 0#\t: #'
	# the private prefixes
	mutant "private/* is not refused" "$BRANCH_GUARD" \
		's#^test/\* | private/\* | wip/\* | scratch/\*) ;;#test/*) ;;#'
	# the base check
	mutant "staging-main is not guarded" "$BRANCH_GUARD" \
		's#^main | staging-main) ;;#main) ;;#'
fi

printf '\n%d passed, %d failed\n' "$pass" "$fail"
[ "$fail" -eq 0 ]
