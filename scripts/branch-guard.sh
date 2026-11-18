#!/bin/sh
# SPDX-License-Identifier: GPL-2.0-only
#
# branch-guard.sh <head-ref> <base-ref> [labels]
#
# A pull request into main may not come from a private branch unless it is
# labelled publish-private.  test/* branches hold unverified modem and voice
# work that is pushed to the private remote only; a pull request from one would
# publish it, and the merge would look exactly like any other.
#
# Labels is a space-separated list (GitHub's join() of the PR labels).  The
# escape hatch is a label rather than a commit trailer so that granting it is
# visible on the pull request and can be revoked by removing the label.
#
# Exit 0 to allow, 1 to refuse.

set -u

head=${1:-}
base=${2:-}
labels=${3:-}

case "$base" in
main | staging-main) ;;
*) exit 0 ;;
esac

case "$head" in
test/* | private/* | wip/* | scratch/*) ;;
*) exit 0 ;;
esac

for label in $labels; do
	[ "$label" = "publish-private" ] && exit 0
done

cat >&2 <<EOF
refusing to build a pull request from '$head' into '$base'.

That prefix marks a private branch: unverified work that is pushed to the
private remote only.  Publishing it is a decision, not a side effect.

    - if this is not meant to be public, close the pull request
    - if it is, add the 'publish-private' label to it
EOF

exit 1
