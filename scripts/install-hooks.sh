#!/bin/sh
# SPDX-License-Identifier: GPL-2.0-only
#
# install-hooks.sh -- point git at the hooks this repository tracks.
#
# The hooks live in .githooks so that they are versioned and reviewed like any
# other file.  Git only reads them after core.hooksPath says so, which is a
# per-clone setting, so this has to be run once per clone.  ./build.sh guard
# reports when it has not been.

set -eu

root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root"

[ -d .githooks ] || {
	printf 'install-hooks: no .githooks directory\n' >&2
	exit 1
}

chmod +x .githooks/* 2>/dev/null || true
git config core.hooksPath .githooks

printf 'core.hooksPath = %s\n' "$(git config core.hooksPath)"
ls .githooks | sed 's/^/  /'
