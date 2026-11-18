#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-2.0-only
#
# Hardware-free tests for scripts/new-board.py, the board scaffolder.
#
# The tool reads a vendor DTB and writes a board DTS plus the five places that
# register a board.  It is the one thing that touches all of them at once, so a
# silent mistake there produces a board that builds and does not boot.  The
# suite runs it against the checked-in UFI003 vendor DTB in a throwaway tree:
# read-only by default, one full --write, and the refusals that keep it from
# clobbering an existing board.
#
# Needs dtc, for the .dts input path only: a .dtb is parsed in-process, which
# is also why one of the checks asserts that fdtget is never invoked.  Asking
# fdtget for one property at a time was 28 000 processes and 11 s per run.
# --self-check mutates the tool and requires a targeted check to go red for
# each mutant.

set -uo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
TOOL_SRC="$ROOT/scripts/new-board.py"
DTB="$ROOT/Docs/Boards/UFI003_MB_V02/dtb02.dtb"
SELF_CHECK=0
[ "${1:-}" = "--self-check" ] && SELF_CHECK=1

pass=0
fail=0
ok() { pass=$((pass + 1)); printf '  ok   %s\n' "$*"; }
bad() { fail=$((fail + 1)); printf '  FAIL %s\n' "$*"; }

if ! command -v dtc >/dev/null 2>&1; then
	printf 'new-board-tool: dtc missing (install device-tree-compiler)\n' >&2
	exit 2
fi
[ -f "$DTB" ] || {
	printf 'new-board-tool: missing fixture %s\n' "$DTB" >&2
	exit 2
}

TREE=$(mktemp -d /tmp/new-board-tool.XXXXXX)
trap 'rm -rf "$TREE"' EXIT

# scratch -- a throwaway tree holding what the tool reads, plus the tool itself:
# ROOT is derived from the script's own path, so the copy has to live inside the
# tree it is meant to work on.
scratch() {
	rm -rf "$T"
	mkdir -p "$T"
	cp -a "$ROOT/diffconfigs" "$ROOT/version" "$T/"
	mkdir -p "$T/msm89xx" "$T/.github" "$T/scripts" "$T/tests"
	cp -a "$ROOT/msm89xx/image" "$ROOT/msm89xx/patches" "$T/msm89xx/"
	cp -a "$ROOT/.github/workflows" "$T/.github/"
	cp -a "$ROOT/tests/board-profile" "$T/tests/"
	cp "$TOOL" "$T/scripts/new-board.py"
}

# run_tool <args...> -- sets RC, LAST
run_tool() {
	LAST=$(cd "$T" && python3 "$T/scripts/new-board.py" "$@" 2>&1)
	RC=$?
}

has() {
	if printf '%s' "$LAST" | grep -qF -- "$2"; then ok "$1"; else bad "$1 (no '$2' in the output)"; fi
}
lacks() {
	if printf '%s' "$LAST" | grep -qF -- "$2"; then bad "$1 (found '$2')"; else ok "$1"; fi
}
expect() {
	if [ "$RC" -eq "$2" ]; then ok "$1"; else bad "$1 (exit $RC, want $2)"; fi
}
exists() {
	if [ -e "$T/$2" ]; then ok "$1"; else bad "$1"; fi
}
contains() { # contains <label> <file> <needle>
	if grep -qF -- "$3" "$T/$2" 2>/dev/null; then ok "$1"; else bad "$1"; fi
}

# ---- the checks -----------------------------------------------------------

t_inspect() {
	scratch
	run_tool --dtb "$DTB" --name probe --inspect
	expect "inspect exits 0" 0
	has "inspect names the board" "board     probe"
	has "inspect reports the compatible" "qcom,msm8916-mtp"
	if [ -e "$T/diffconfigs/probe" ]; then
		bad "inspect writes nothing"
	else
		ok "inspect writes nothing"
	fi
}

t_nomode() {
	scratch
	run_tool --dtb "$DTB" --name probe
	expect "no mode exits 0" 0
	has "no mode says nothing was written" "nothing written"
	if [ -e "$T/diffconfigs/probe" ]; then
		bad "no mode writes nothing"
	else
		ok "no mode writes nothing"
	fi
}

t_nameless() {
	scratch
	run_tool --dtb "$DTB" --inspect
	expect "a nameless DTB is refused" 1
	has "the refusal explains --name" "pass --name"
}

t_badref() {
	scratch
	run_tool --dtb "$DTB" --name probe --reference nosuchboard --write --no-branch
	expect "an unknown reference is refused" 1
	has "the refusal names the missing diffconfig" "no diffconfigs/nosuchboard"
	lacks "the refusal is not a traceback" "Traceback"
}

t_dts() {
	scratch
	dtc -I dtb -O dts "$DTB" -o "$TREE/probe.dts" 2>/dev/null
	run_tool --dtb "$TREE/probe.dts" --name probe --inspect
	expect "a .dts input works" 0
	has "the .dts input reports the same compatible" "qcom,msm8916-mtp"
	has "the .dts input reports the battery" "battery   yes"
}

# The tree is read in-process.  If a per-property fdtget call ever comes back,
# the tool still works -- it just takes eleven seconds -- so nothing else here
# would notice.  This does.
t_no_fdtget() {
	scratch
	mkdir -p "$TREE/shim"
	cat >"$TREE/shim/fdtget" <<EOF
#!/bin/sh
echo "\$*" >>"$TREE/shim/calls"
exit 1
EOF
	chmod +x "$TREE/shim/fdtget"
	PATH="$TREE/shim:$PATH" run_tool --dtb "$DTB" --name probe --inspect
	expect "inspect still exits 0 without fdtget" 0
	if [ -s "$TREE/shim/calls" ]; then
		bad "the tool never calls fdtget"
	else
		ok "the tool never calls fdtget"
	fi
}

t_write() {
	scratch
	run_tool --dtb "$DTB" --name probe --reference mifib-v12 --write --no-branch --no-validate
	expect "a write exits 0" 0
	has "the write reports five files" "wrote 5 files"

	exists "wrote the diffconfig" diffconfigs/probe
	exists "wrote the DTS patch" \
		msm89xx/patches/817-arm64-dts-qcom-add-msm8916-generic-probe.patch
	contains "the 801 patch gains the dtb line" \
		msm89xx/patches/801-arm64-dts-qcom-add-devices-makefile.patch \
		msm8916-generic-probe.dtb
	contains "msm8916.mk gains the Device block" \
		msm89xx/image/msm8916.mk "define Device/generic-probe"
	contains "build.yml gains the board" .github/workflows/build.yml probe
	contains "the generated DTS keeps its TODOs" \
		msm89xx/patches/817-arm64-dts-qcom-add-msm8916-generic-probe.patch TODO
	contains "the diffconfig selects the new device" diffconfigs/probe \
		CONFIG_TARGET_msm89xx_msm8916_DEVICE_generic-probe=y

	# The generated board has to satisfy the harness the tool itself runs.
	if bash "$T/tests/board-profile/run.sh" --root "$T" >/dev/null 2>&1; then
		ok "the harness accepts the generated board"
	else
		bad "the harness accepts the generated board"
	fi
}

t_twice() {
	scratch
	run_tool --dtb "$DTB" --name probe --reference mifib-v12 --write --no-branch --no-validate
	run_tool --dtb "$DTB" --name probe --reference mifib-v12 --write --no-branch --no-validate
	expect "a second write is refused" 1
	has "the refusal says the diffconfig exists" "diffconfigs/probe already exists"
}

suite() {
	TOOL=$1
	T=$2
	t_inspect
	t_nomode
	t_nameless
	t_badref
	t_dts
	t_no_fdtget
	t_write
	t_twice
}

printf '== new-board tool ==\n'
suite "$TOOL_SRC" "$TREE/work"

if [ "$fail" -ne 0 ]; then
	printf '\n%d passed, %d failed\n' "$pass" "$fail"
	exit 1
fi

# --------------------------------------------------------------------------
# Self-check: each mutant must turn its targeted check red.
# --------------------------------------------------------------------------
if [ "$SELF_CHECK" -eq 1 ]; then
	printf '\n== self-check (mutants must be caught) ==\n'
	mutant() { # mutant <name> <check> <sed expression>
		local name=$1 check=$2 expr=$3 copy="$TREE/mutant.py"
		sed "$expr" "$TOOL_SRC" >"$copy"
		if [ "$(grep -c . "$copy")" -lt 100 ]; then
			bad "$name (sed produced a truncated script)"
			return
		fi
		if ( TOOL="$copy"; T="$TREE/m"; pass=0; fail=0; "$check" >/dev/null 2>&1
		  [ "$fail" -eq 0 ] ); then
			bad "$name (mutant survived)"
		else
			ok "$name caught"
		fi
	}

	mutant "--write is no longer required" t_nomode \
		's/^    if not args.write:$/    if False:/'
	mutant "a missing reference raises instead of dying" t_badref \
		's/^    if not os.path.exists(ref_path):$/    if False:/'
	mutant "the diffconfig is not written" t_write \
		's/^    open(diffconfig, "w").write(conf)$/    pass/'
	mutant "the DTS patch is empty" t_write \
		's/^def make_patch(dts_name, body):$/def make_patch(dts_name, body):\n    return ""/'
fi

printf '\n%d passed, %d failed\n' "$pass" "$fail"
[ "$fail" -eq 0 ]
