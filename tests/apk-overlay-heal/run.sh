#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-2.0-only
#
# Hardware-free checks for msm89xx/base-files/usr/sbin/apk-overlay-heal.
#
#   bash tests/apk-overlay-heal/run.sh [--self-check]
#
# Builds a fake /rom + /overlay pair (the script takes both roots from
# APK_HEAL_ROM / APK_HEAL_OVERLAY) and asserts what a flash must leave behind:
#   - a package built for the PREVIOUS kernel is dropped from the database and
#     its files are removed, so apk can solve again;
#   - it stays in /etc/apk/world, so apk reinstalls it from the feed;
#   - a package built for THIS kernel, and a package with no kernel dependency
#     at all, are left alone;
#   - a package the image ships wins the merge, and its overlay copy goes;
#   - a package the overlay holds at a higher version survives, files included;
#   - the merge is recorded once, so a second boot does nothing.
#
# --self-check mutates the script five ways and requires every mutant to be
# caught, so a check that silently stopped testing anything turns red.
set -u

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
HEAL="$ROOT/msm89xx/base-files/usr/sbin/apk-overlay-heal"

SELF_CHECK=0
while [ $# -gt 0 ]; do
	case "$1" in
		--self-check) SELF_CHECK=1 ;;
		*) echo "usage: $0 [--self-check]" >&2; exit 2 ;;
	esac
	shift
done

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

OLD=6.12.112~0a9f7302deadbeef-r1
NEW=6.12.112~bdcc5351c39ae3e26f28507f59afede0-r1

PASS=0
FAIL=0

ok()   { PASS=$((PASS + 1)); printf '  ok   %s\n' "$1"; }
bad()  { FAIL=$((FAIL + 1)); printf '  FAIL %s\n' "$1"; [ $# -gt 1 ] && printf '       %s\n' "$2"; }

assert_eq() {
	if [ "$2" = "$3" ]; then ok "$1"; else bad "$1" "expected '$3', got '$2'"; fi
}
assert_gone() {
	if [ -e "$2" ] || [ -L "$2" ]; then bad "$1" "$2 still exists"; else ok "$1"; fi
}
assert_here() {
	if [ -e "$2" ] || [ -L "$2" ]; then ok "$1"; else bad "$1" "$2 is missing"; fi
}

# --- fixture -----------------------------------------------------------------

# A record is the blank-line separated block apk writes; only the lines the
# script looks at are here, plus a C: line so the "unparseable" guard passes.
rec() {
	local name="$1" ver="$2" dep="$3"
	shift 3
	printf 'C:Q1%s=\nP:%s\nV:%s\nA:aarch64_generic\n' "$name" "$name" "$ver"
	[ -n "$dep" ] && printf 'D:%s\n' "$dep"
	printf 'f:S\nF:lib\n'
	local f
	for f in "$@"; do
		printf 'F:lib/modules/6.12.112\nR:%s\n' "$f"
	done
	printf '\n'
}

mk_image_db() {
	rec kernel "$NEW" libc
	rec base-files 1719-r1 libc
	rec kmod-shared 6.12.112.1.0-r1 "kernel=$NEW" shared.ko
	rec kmod-newer 6.12.112.2.0-r1 "kernel=$NEW" newer.ko
} > "$ROM/lib/apk/db/installed"

mk_overlay_db() {
	rec kernel "$OLD" libc
	rec base-files 1718-r1 libc
	rec kmod-stale 6.12.112.9.9-r1 "kernel=$OLD" stale.ko
	rec kmod-shared 6.12.112.1.0-r1 "kernel=$OLD" shared.ko
	rec kmod-newer 6.12.112.2.0-r2 "kernel=$NEW" newer.ko
	rec kmod-fresh 6.12.112.3.0-r1 "kernel=$NEW" fresh.ko
	rec my-app 1.0-r1 "libc"
} > "$OVL/upper/lib/apk/db/installed"

run_checks() {
	local script="$1" rc

	rm -rf "$WORK/fx" "$WORK/log"
	ROM="$WORK/fx/rom"
	OVL="$WORK/fx/overlay"
	UPPER="$OVL/upper"
	export APK_HEAL_ROM="$ROM" APK_HEAL_OVERLAY="$OVL" APK_HEAL_MERGED="$UPPER"

	mkdir -p "$ROM/lib/apk/db" "$ROM/etc/apk" "$ROM/lib/modules/6.12.112" \
		 "$UPPER/lib/apk/db" "$UPPER/etc/apk" "$UPPER/lib/modules/6.12.112" \
		 "$WORK/bin"

	# logger stub: the script's only channel out.
	cat > "$WORK/bin/logger" <<'EOF'
#!/bin/sh
shift 2 2>/dev/null
printf '%s\n' "$*" >> "$LOGFILE"
EOF
	chmod +x "$WORK/bin/logger"
	export LOGFILE="$WORK/log"
	: > "$LOGFILE"
	PATH="$WORK/bin:$PATH"

	mk_image_db
	mk_overlay_db
	printf 'base-files\nkmod-shared\nkmod-newer\n' > "$ROM/etc/apk/world"
	printf 'base-files\nkmod-shared\nkmod-newer\nkmod-stale\nkmod-fresh\nmy-app\n' \
		> "$UPPER/etc/apk/world"

	# Files.  shared.ko and newer.ko exist on BOTH sides with different
	# content; stale.ko and fresh.ko only on the overlay.
	echo image > "$ROM/lib/modules/6.12.112/shared.ko"
	echo image > "$ROM/lib/modules/6.12.112/newer.ko"
	echo overlay > "$UPPER/lib/modules/6.12.112/shared.ko"
	echo overlay > "$UPPER/lib/modules/6.12.112/newer.ko"
	echo overlay > "$UPPER/lib/modules/6.12.112/stale.ko"
	echo overlay > "$UPPER/lib/modules/6.12.112/fresh.ko"

	# A whiteout the image ships is resurrected.  A char device needs root,
	# so the check is skipped where the fixture cannot make one.
	WHITEOUT=0
	echo image > "$ROM/lib/modules/6.12.112/ghost"
	if mknod "$UPPER/lib/modules/6.12.112/ghost" c 0 0 2>/dev/null; then
		WHITEOUT=1
	fi

	sh "$script" --boot
	rc=$?
	assert_eq "boot run exits 0" "$rc" 0

	DB="$UPPER/lib/apk/db/installed"

	# --- the kernel-mismatched package is gone, files and all -------------
	if grep -qx 'P:kmod-stale' "$DB"; then
		bad "stale record dropped" "kmod-stale is still in the database"
	else
		ok "stale record dropped"
	fi
	assert_gone "stale module file removed" "$UPPER/lib/modules/6.12.112/stale.ko"
	assert_here "stale package still in world" "$UPPER/etc/apk/world"
	grep -qx 'kmod-stale' "$UPPER/etc/apk/world" &&
		ok "world keeps the intent to reinstall" ||
		bad "world keeps the intent to reinstall"
	grep -q 'dropped 1 package(s) built for another kernel (kmod-stale)' "$LOGFILE" &&
		ok "drop is logged" ||
		bad "drop is logged" "$(cat "$LOGFILE")"

	# --- packages that still match this kernel are untouched ---------------
	grep -qx 'P:kmod-fresh' "$DB" && ok "matching kernel-only package kept" ||
		bad "matching kernel-only package kept"
	assert_here "matching module file kept" "$UPPER/lib/modules/6.12.112/fresh.ko"
	grep -qx 'P:my-app' "$DB" && ok "package without a kernel dep kept" ||
		bad "package without a kernel dep kept"

	# --- the image wins for what it ships ----------------------------------
	assert_eq "image record wins over the overlay" \
		"$(awk 'BEGIN { RS = "" } /(^|\n)P:kmod-shared(\n|$)/ { print }' "$DB" |
		   sed -n 's/^D:kernel=\([^ ]*\).*/\1/p')" "$NEW"
	assert_gone "overlay copy of an image file removed" \
		"$UPPER/lib/modules/6.12.112/shared.ko"

	# --- a newer feed package survives, with its files ---------------------
	assert_eq "newer package record kept" \
		"$(awk 'BEGIN { RS = "" } /(^|\n)P:kmod-newer(\n|$)/ { print }' "$DB" |
		   sed -n 's/^V://p')" '6.12.112.2.0-r2'
	assert_here "newer package file kept" "$UPPER/lib/modules/6.12.112/newer.ko"

	# --- housekeeping ------------------------------------------------------
	if [ "$WHITEOUT" = "1" ]; then
		if [ -c "$UPPER/lib/modules/6.12.112/ghost" ]; then
			bad "whiteout for an image file removed"
		else
			ok "whiteout for an image file removed"
		fi
	else
		echo "  skip whiteout (cannot mknod without root)"
	fi
	if grep -q 'WARNING' "$LOGFILE"; then
		bad "no warnings" "$(cat "$LOGFILE")"
	else
		ok "no warnings"
	fi

	# --- once per image change ---------------------------------------------
	before=$(cat "$DB")
	: > "$LOGFILE"
	sh "$script" --boot
	assert_eq "second boot is a no-op" "$(cat "$DB")" "$before"
	assert_eq "second boot logs nothing" "$(cat "$LOGFILE")" ""

	# --- the manual mode exists and is a no-op once healed -----------------
	# It is documented in --help, so an option the case list forgot to accept
	# must be caught here rather than on a device.
	assert_eq "--stale-kmods is accepted" "$(sh "$script" --stale-kmods 2>&1)" ""

	[ "$FAIL" -eq 0 ]
}

echo "== apk-overlay-heal: real script =="
if run_checks "$HEAL"; then
	echo "apk-overlay-heal: PASS"
else
	echo "apk-overlay-heal: FAIL"
	exit 1
fi

[ "$SELF_CHECK" = "1" ] || exit 0

echo
echo "== apk-overlay-heal: self-check (each mutant must be caught) =="
MUT="$WORK/mut.sh"
CAUGHT=0

mutate() {
	local desc="$1" expr="$2"

	sed "$expr" "$HEAL" > "$MUT"
	if run_checks "$MUT" >/dev/null 2>&1; then
		echo "  FAIL mutant not caught: $desc"
	else
		echo "  ok   mutant caught: $desc"
		CAUGHT=$((CAUGHT + 1))
	fi
}

# 1. the kernel dependency is never compared, so nothing looks stale.
mutate "kernel dependency comparison broken" \
	's/substr(dep\[i\], 8) != kver/substr(dep[i], 8) == "never"/'

# 2. stale detection works but the merge keeps the record anyway.
mutate "merge keeps the stale record" \
	's/} else if (substr(name, 3) in drop) {/} else if (substr(name, 3) == "never") {/'

# 3. the record is dropped but the files are left behind.
mutate "stale files not removed" \
	's/rm -f "\$UPPER\$p" && removed=\$((removed + 1))/: \&\& removed=$((removed + 1))/'

# 4. the record count is checked without the dropped packages, so every boot
#    after a flash would refuse to write the merged database.
mutate "record count ignores the drops" \
	's/else if (!(n in seen) \&\& !(substr(n, 3) in drop)) c++/else if (!(n in seen)) c++/'

# 5. the pass is never called from the boot path.
mutate "stale pass not wired into --boot" \
	's/^\theal_stale_kernel$/\t:/'

echo
TOTAL=5
if [ "$CAUGHT" -eq "$TOTAL" ]; then
	echo "apk-overlay-heal self-check: PASS ($CAUGHT/$TOTAL mutants caught)"
	exit 0
fi

echo "apk-overlay-heal self-check: FAIL ($CAUGHT/$TOTAL mutants caught)"
exit 1
