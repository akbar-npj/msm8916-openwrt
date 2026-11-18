#!/bin/sh
# SPDX-License-Identifier: GPL-2.0-only
#
# private-sync.sh [status|sync] [branch]
#
# The private branch carries work that is not ready to publish, and it has to
# stay on top of main: a branch that drifts for a week cannot be merged back
# without conflicts whose reasons nobody remembers.  This reports the gap and
# closes it.
#
#   status   how far the branch is behind main, and whether the private remote
#            has the local tip.  Exits 1 when it is behind, so it can be used
#            as a check.
#   sync     merge origin/main into the branch.  Does not push: publishing is a
#            separate decision, and .githooks/pre-push refuses the push anyway
#            while the branch is behind.
#
# "Behind" counts non-merge commits on origin/main that the branch lacks.  A
# merge commit on main whose parents are both present is not content, so it
# does not count -- otherwise every merged pull request would look like drift.

set -eu

root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root"

cmd=${1:-status}
branch=${2:-}

if [ -z "$branch" ]; then
	branch=$(git rev-parse --abbrev-ref --symbolic-full-name '@{u}' 2>/dev/null |
		sed 's|^private/||' || true)
fi
if [ -z "$branch" ]; then
	branch=test/volte-openimsd
fi

git rev-parse --verify -q "refs/heads/$branch" >/dev/null || {
	printf 'private-sync: no such branch: %s\n' "$branch" >&2
	exit 1
}

fetch() {
	git fetch --quiet origin main 2>/dev/null ||
		printf 'private-sync: could not fetch origin (using the refs already here)\n' >&2
	git fetch --quiet private 2>/dev/null ||
		printf 'private-sync: could not fetch private (using the refs already here)\n' >&2
}

behind() {
	git rev-list --count --no-merges origin/main --not "refs/heads/$branch"
}

ahead() {
	git rev-list --count --no-merges "refs/heads/$branch" --not origin/main
}

report() {
	local_tip=$(git rev-parse --short "refs/heads/$branch")
	remote_tip=$(git rev-parse --short "refs/remotes/private/$branch" 2>/dev/null || echo "-")

	printf 'branch          %s\n' "$branch"
	printf 'local tip       %s\n' "$local_tip"
	printf 'private remote  %s\n' "$remote_tip"
	printf 'behind main     %s\n' "$(behind)"
	printf 'ahead of main   %s\n' "$(ahead)"
}

fetch

case "$cmd" in
status)
	report
	n=$(behind)
	if [ "$n" -gt 0 ]; then
		printf '\n%s commit(s) behind main.  Run: scripts/private-sync.sh sync\n' "$n"
		exit 1
	fi
	;;
sync)
	[ "$(git rev-parse --abbrev-ref HEAD)" = "$branch" ] || {
		printf 'private-sync: check out %s first (it is not the current branch)\n' "$branch" >&2
		exit 1
	}
	git diff --quiet && git diff --cached --quiet || {
		printf 'private-sync: the working tree is not clean; commit or stash first\n' >&2
		exit 1
	}
	if [ "$(behind)" -eq 0 ]; then
		printf '%s is already up to date with origin/main\n' "$branch"
		report
		exit 0
	fi
	git merge --no-edit origin/main
	report
	printf '\nPush it with: git push private %s\n' "$branch"
	;;
*)
	printf 'usage: %s [status|sync] [branch]\n' "$0" >&2
	exit 2
	;;
esac
