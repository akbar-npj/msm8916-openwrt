#!/bin/sh
# SPDX-License-Identifier: GPL-2.0-only
#
# apk-flag-guard - refuse a shipped apk invocation that can delete packages.
#
# Two flags have already cost us a working system, and neither is needed by
# anything we ship:
#
#   --force-broken-world  2026-11-16, mifib-v12: four overlay kmods carried a
#                         stale vermagic, every `apk upgrade` failed with
#                         "breaks: kmod-...[kernel=...]", and this flag purged
#                         124 packages -- the kernel among them.
#
#   --prune               2026-11-18, mifib-v12: `apk upgrade --prune` purges
#                         base-files.  The shared feed deliberately does not
#                         carry it (promote-release.yml drops it so an older
#                         revision is never offered an unappliable update) and
#                         apk treats an unavailable world package as prunable.
#
# A CI grep is the right shape for this: the damage is done by a flag in a
# script, so the flag is what gets refused, before the script ever ships.
#
# Usage: scripts/apk-flag-guard.sh [dir...]      (default: packages msm89xx)
# Exit:  0 clean, 1 a flag was found, 2 bad usage.

set -u

ROOT=$(cd "$(dirname "$0")/.." && pwd)

[ "$#" -gt 0 ] || set -- packages msm89xx

# A non-comment line that carries one of the flags.  The optional group is
# what lets the flag stand on its own continuation line, and excluding '#' from
# its first character is what keeps a line that merely *documents* the flag --
# like the header above, or a shell comment in a package -- out of the result.
PATTERN='^[[:space:]]*([^#[:space:]].*)?--(prune|force-broken-world)([[:space:]]|$)'

status=0

for dir in "$@"; do
	case "$dir" in
		/*) path=$dir ;;
		*)  path=$ROOT/$dir ;;
	esac

	# A named directory that is not there is a mistake in the invocation,
	# not a clean result.
	if [ ! -d "$path" ]; then
		printf 'apk-flag-guard: no such directory: %s\n' "$path" >&2
		exit 2
	fi

	hits=$(grep -rnE -- "$PATTERN" "$path" 2>/dev/null || true)
	[ -n "$hits" ] || continue

	printf '%s\n' "$hits"
	status=1
done

if [ "$status" -ne 0 ]; then
	printf 'apk-flag-guard: a shipped script uses an apk flag that deletes packages.\n' >&2
	printf 'See the header of scripts/apk-flag-guard.sh for what each one did.\n' >&2
fi

exit "$status"
