#!/bin/sh
# SPDX-License-Identifier: GPL-2.0-only
#
# Which feed a package publish has to reach, and why.
#
# Devices install from releases/<base>: CONFIG_VERSION_REPO is written without
# the -rN suffix, and promote-release.yml copies releases/<version> into
# releases/<base> once a release has passed.  releases/<version> is the
# staging copy -- and the only place a device reads hotfix.json from.  So a
# package published to <version> alone is offered by the page and cannot be
# installed.  Measured 2026-11-17: msm89xx-scripts 1.0-r20 and
# luci-app-led-setup 1.0-r6 were published revision-only, every device read
# releases/25.12.6 and stayed on r18/r5, and `apk upgrade` printed
# "1 error" without naming anything.
#
# The exceptions are the world-pinned packages.  Their exact version sits in
# /etc/apk/world, apk honours that pin, and promote-release.yml deliberately
# keeps them out of the shared feed -- a newer image's base-files offered to an
# older revision is an update apk refuses.  Those go to <version> only, and are
# installed with an explicit version.

set -u

PINNED="base-files kernel libc"

usage() {
	echo "usage: $0 check <package> <scope> <target_package>"
	echo "       $0 feed-local-only <package> <target_package>"
}

die() {
	printf '::error::%s\n' "$*" >&2
	exit 1
}

# True when this package must stay out of the shared feed.
feed_local_only() {
	local pkg="$1" target="${2:-false}" p

	[ "$target" = "true" ] && return 0

	for p in $PINNED; do
		[ "$pkg" = "$p" ] && return 0
	done

	return 1
}

cmd="${1:-}"

case "$cmd" in

feed-local-only)

	feed_local_only "${2:-}" "${3:-false}"
	exit $?
	;;

check)

	pkg="${2:-}"
	scope="${3:-}"
	target="${4:-false}"

	# Feed maintenance: no package, nothing to install.
	[ -n "$pkg" ] || exit 0

	if feed_local_only "$pkg" "$target"; then
		[ "$scope" = "revision" ] ||
			die "$pkg is version-pinned in /etc/apk/world. Publish it with scope=revision: in the shared feed the Software page would offer a fix apk then refuses to install."
		exit 0
	fi

	[ "$scope" = "all" ] ||
		die "$pkg must be published with scope=all. Devices install from releases/<base>; scope=revision writes only releases/<version>, so the page would offer this fix and apk would never find it."

	exit 0
	;;

*)

	usage >&2
	exit 2
	;;
esac
