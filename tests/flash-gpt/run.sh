#!/usr/bin/env bash
# Tests for the GPT selection in msm89xx/image/flash.sh.
#
# flash.sh is a 1000-line flashing script with no way to run it without a
# device, so the suite sources it with FLASH_LIB_ONLY=1 (everything defined,
# main not called) and drives select_gpt / flash_gpt against a stub edl and
# hand-built GPT images.  No device, no root, no EDL.
#
#   bash tests/flash-gpt/run.sh [--self-check]
#
# --self-check mutates flash.sh and requires the suite to notice.

set -u

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
FLASH="${FLASH:-$ROOT/msm89xx/image/flash.sh}"
MK="${MK:-$ROOT/msm89xx/image/msm8916.mk}"
RELEASE="${RELEASE:-$ROOT/.github/workflows/build-and-release.yml}"

PASS=0
FAIL=0

ok()  { PASS=$((PASS + 1)); printf '  ok   %s\n' "$1"; return 0; }
bad() { FAIL=$((FAIL + 1)); printf '  FAIL %s\n' "$1"; [ $# -gt 1 ] && printf '       %s\n' "$2"; return 0; }

setup() {
    TMP="$(mktemp -d)"
    trap 'rm -rf "$TMP"' EXIT
    mkdir -p "$TMP/bin" "$TMP/img"

    export PATH="$TMP/bin:$PATH"
    export EDL_CALLS="$TMP/edl-calls"
    export EDL_DUMP="$TMP/edl-dump"
    : > "$EDL_CALLS"

    cat > "$TMP/bin/edl" <<'EOF'
#!/bin/sh
echo "$*" >> "$EDL_CALLS"
case "$1" in
    ws)       exit 0 ;;
    printgpt) cat "$EDL_DUMP" ;;
    *)        exit 0 ;;
esac
EOF
    chmod +x "$TMP/bin/edl"
}

# A GPT image is only ever read for the backup-header LBA at byte 544, so the
# fixture is a sparse file with that one field set.
make_gpt() {
    python3 - "$1" "$2" <<'PY'
import struct, sys
path, sectors = sys.argv[1], int(sys.argv[2])
blob = bytearray(70 * 512)
struct.pack_into("<Q", blob, 544, sectors - 1)
open(path, "wb").write(blob)
PY
}

# Run select_gpt in a subshell: it calls die(), which exits, and flash.sh sets
# -euo pipefail, which must not leak into the suite.
run_select() {
    local dir="$1" dump="$2" token="$3" gpt="$4"
    (
        cd "$dir" || exit 99
        FLASH_LIB_ONLY=1 . "$FLASH"
        BOARD_TOKEN="$token"
        GPT_DUMP="$dump"
        gpt_path="$gpt"
        select_gpt
        printf 'PICKED %s %s\n' "$TOT_SECTORS" "$(basename "$gpt_path")"
    ) 2>&1
}

# --- select_gpt --------------------------------------------------------------

t_exact() {
    local d="$TMP/img" out
    make_gpt "$d/ufi003-squashfs-gpt_both0.bin" 7634944
    out=$(run_select "$d" "sectors:0x748000" "ufi003" "$d/ufi003-squashfs-gpt_both0.bin")
    case "$out" in
    *"PICKED 7634944 ufi003-squashfs-gpt_both0.bin"*)
        ok "an exact match is used as is" ;;
    *)  bad "an exact match is used as is" "$out" ;;
    esac
    case "$out" in
    *"before the end of the medium"*)
        bad "an exact match does not warn about unused space" "$out" ;;
    *)  ok "an exact match does not warn about unused space" ;;
    esac
}

t_device_larger() {
    local d="$TMP/img" out
    out=$(run_select "$d" "sectors:0x7A1200" "ufi003" "$d/ufi003-squashfs-gpt_both0.bin")
    case "$out" in
    *"PICKED 7634944"*) ok "a larger device keeps the table and says so" ;;
    *) bad "a larger device keeps the table and says so" "$out" ;;
    esac
    case "$out" in
    *"before the end of the medium"*) ok "the unused tail is reported" ;;
    *) bad "the unused tail is reported" "$out" ;;
    esac
}

t_refuse() {
    local d="$TMP/img" out rc
    out=$(run_select "$d" "sectors:0x710000" "ufi003" "$d/ufi003-squashfs-gpt_both0.bin"); rc=$?
    [ "$rc" -ne 0 ] && ok "a table that does not fit is refused" \
        || bad "a table that does not fit is refused" "rc=$rc $out"
    case "$out" in
    *"7405568"*"7634944"*) ok "the refusal names both sector counts" ;;
    *) bad "the refusal names both sector counts" "$out" ;;
    esac
}

t_fallback() {
    local d="$TMP/img" out
    make_gpt "$d/ufi003-squashfs-gpt-7405568.bin" 7405568
    out=$(run_select "$d" "sectors:0x710000" "ufi003" "$d/ufi003-squashfs-gpt_both0.bin")
    case "$out" in
    *"PICKED 7405568 ufi003-squashfs-gpt-7405568.bin"*)
        ok "the smaller table in the same directory is used" ;;
    *)  bad "the smaller table in the same directory is used" "$out" ;;
    esac
}

t_largest_fits() {
    local d="$TMP/img2" out
    mkdir -p "$d"
    make_gpt "$d/ufi003-squashfs-gpt_both0.bin" 7634944
    make_gpt "$d/ufi003-squashfs-gpt-7000000.bin" 7000000
    out=$(run_select "$d" "sectors:0x7270E0" "ufi003" "$d/ufi003-squashfs-gpt_both0.bin")
    case "$out" in
    *"PICKED 7000000"*) ok "the largest table that fits wins" ;;
    *)                  bad "the largest table that fits wins" "$out" ;;
    esac
}

t_board_filter() {
    local d="$TMP/img3" out rc
    mkdir -p "$d"
    make_gpt "$d/ufi003-squashfs-gpt_both0.bin" 7634944
    make_gpt "$d/otherboard-squashfs-gpt_both0.bin" 7000000
    out=$(run_select "$d" "sectors:0x710000" "ufi003" "$d/ufi003-squashfs-gpt_both0.bin"); rc=$?
    [ "$rc" -ne 0 ] && ok "another board's table is never used" \
        || bad "another board's table is never used" "rc=$rc $out"
}

t_no_sectors() {
    local d="$TMP/img" out rc
    out=$(run_select "$d" "no sector count here" "ufi003" "$d/ufi003-squashfs-gpt_both0.bin"); rc=$?
    [ "$rc" -ne 0 ] && ok "a dump without a sector count is refused" \
        || bad "a dump without a sector count is refused" "rc=$rc $out"
    case "$out" in
    *"will not guess"*) ok "the refusal says it will not guess" ;;
    *) bad "the refusal says it will not guess" "$out" ;;
    esac
}

# --- flash_gpt ---------------------------------------------------------------

run_flash_gpt() {
    local dump="$1"
    (
        cd "$TMP/img" || exit 99
        FLASH_LIB_ONLY=1 . "$FLASH"
        gpt_path="$TMP/img/ufi003-squashfs-gpt_both0.bin"
        TOT_SECTORS=7634944
        EDL_DUMP="$dump"
        : > "$EDL_CALLS"
        flash_gpt
        echo "FLASHED"
    ) 2>&1
}

t_flash_gpt_ok() {
    local out
    printf 'sectors:0x748000\np13 boot\np14 rootfs\n' > "$TMP/dump-ok"
    out=$(run_flash_gpt "$TMP/dump-ok")
    case "$out" in
    *FLASHED*) ok "a table that lands verifies clean" ;;
    *) bad "a table that lands verifies clean" "$out" ;;
    esac
    [ "$(grep -c '^ws ' "$EDL_CALLS")" = "3" ] && ok "three raw writes are issued" \
        || bad "three raw writes are issued" "$(cat "$EDL_CALLS")"
}

t_flash_gpt_no_rootfs() {
    local out rc
    printf 'sectors:0x748000\np13 boot\n' > "$TMP/dump-norootfs"
    out=$(run_flash_gpt "$TMP/dump-norootfs"); rc=$?
    [ "$rc" -ne 0 ] && ok "a table that did not land is caught" \
        || bad "a table that did not land is caught" "rc=$rc $out"
}

t_flash_gpt_short() {
    local out rc
    printf 'sectors:0x710000\np13 boot\np14 rootfs\n' > "$TMP/dump-short"
    out=$(run_flash_gpt "$TMP/dump-short"); rc=$?
    [ "$rc" -ne 0 ] && ok "a medium shorter than the table is caught" \
        || bad "a medium shorter than the table is caught" "rc=$rc $out"
}

# --- the tree ----------------------------------------------------------------

t_tree() {
    grep -q "flash.sh reads the real size at" "$MK" \
        && bad "the uf896 comment no longer claims the size is read at flash time" \
        || ok "the uf896 comment no longer claims the size is read at flash time"
    grep -q "read_tot_sectors" "$FLASH" \
        && bad "the circular sector read is gone" \
        || ok "the circular sector read is gone"
    [ "$(grep -c '^    select_gpt$' "$FLASH")" = "2" ] \
        && ok "both image-detection paths call select_gpt" \
        || bad "both image-detection paths call select_gpt" "$(grep -n 'select_gpt' "$FLASH" | tr '\n' '|')"

    # The fallback only helps if a smaller table is shipped.  Drop this and a
    # small-eMMC unit is refused instead of flashed, so the release must carry
    # the second table for every board.
    grep -q 'ARTIFACT/squashfs-gpt-7405568.bin := generate-squashfs-gpt-size 7405568' "$MK" \
        && ok "the small-eMMC table is generated" \
        || bad "the small-eMMC table is generated" "$(grep -n 'squashfs-gpt' "$MK" | tr '\n' '|')"
    grep -q 'ARTIFACTS := .*squashfs-gpt-7405568.bin' "$MK" \
        && ok "the small-eMMC table is in ARTIFACTS" \
        || bad "the small-eMMC table is in ARTIFACTS" "$(grep -n 'ARTIFACTS' "$MK" | tr '\n' '|')"

    # An artifact that is built but never uploaded is invisible to the user:
    # flash.sh would ask for a file that is not in the release.  Check every
    # declared artifact against the release workflow rather than this one name.
    local missing="" name
    for name in $(sed -n 's/^[[:space:]]*ARTIFACT\/\([^ ]*\)[[:space:]]*:=.*/\1/p' "$MK"); do
        grep -q -- "$name" "$RELEASE" || missing="$missing $name"
    done
    [ -z "$missing" ] \
        && ok "every declared artifact is uploaded by the release" \
        || bad "every declared artifact is uploaded by the release" "missing from $(basename "$RELEASE"):$missing"
}

run_suite() {
    setup
    echo "== flash-gpt =="
    t_exact
    t_device_larger
    t_refuse
    t_fallback
    t_largest_fits
    t_board_filter
    t_no_sectors
    t_flash_gpt_ok
    t_flash_gpt_no_rootfs
    t_flash_gpt_short
    t_tree
    echo
    printf '%d passed, %d failed\n' "$PASS" "$FAIL"
    [ "$FAIL" -eq 0 ]
}

# --- self-check --------------------------------------------------------------

self_check() {
    local mutant failed=0
    mutant="$(mktemp)"

    cp "$FLASH" "$mutant"
    sed -i 's/^    if (( img <= dev )); then$/    if true; then/' "$mutant"
    if FLASH="$mutant" bash "$0" >/dev/null 2>&1; then
        echo "  FAIL mutant 'a table that does not fit is accepted' was not caught"
        failed=$((failed + 1))
    else
        echo "  ok   mutant 'a table that does not fit is accepted' caught"
    fi

    cp "$FLASH" "$mutant"
    sed -i 's/^        if \[ -n "\$BOARD_TOKEN" \] && \[\[ "\$f" != \*"\$BOARD_TOKEN"\* \]\]; then$/        if false; then/' "$mutant"
    if FLASH="$mutant" bash "$0" >/dev/null 2>&1; then
        echo "  FAIL mutant 'another board table is used' was not caught"
        failed=$((failed + 1))
    else
        echo "  ok   mutant 'another board table is used' caught"
    fi

    cp "$FLASH" "$mutant"
    sed -i 's/^    if ! grep -qw rootfs <<<"\$after"; then$/    if false; then/' "$mutant"
    if FLASH="$mutant" bash "$0" >/dev/null 2>&1; then
        echo "  FAIL mutant 'a table that did not land is trusted' was not caught"
        failed=$((failed + 1))
    else
        echo "  ok   mutant 'a table that did not land is trusted' caught"
    fi

    cp "$FLASH" "$mutant"
    sed -i 's/^    if \[ -n "\$dev_after" \] && (( dev_after < TOT_SECTORS )); then$/    if false; then/' "$mutant"
    if FLASH="$mutant" bash "$0" >/dev/null 2>&1; then
        echo "  FAIL mutant 'a short medium is accepted' was not caught"
        failed=$((failed + 1))
    else
        echo "  ok   mutant 'a short medium is accepted' caught"
    fi

    cp "$MK" "$mutant"
    sed -i '/ARTIFACT\/squashfs-gpt-7405568.bin/d' "$mutant"
    if MK="$mutant" bash "$0" >/dev/null 2>&1; then
        echo "  FAIL mutant 'the small-eMMC table is not shipped' was not caught"
        failed=$((failed + 1))
    else
        echo "  ok   mutant 'the small-eMMC table is not shipped' caught"
    fi

    cp "$RELEASE" "$mutant"
    sed -i '/squashfs-gpt-7405568.bin/d' "$mutant"
    if RELEASE="$mutant" bash "$0" >/dev/null 2>&1; then
        echo "  FAIL mutant 'a built artifact is never uploaded' was not caught"
        failed=$((failed + 1))
    else
        echo "  ok   mutant 'a built artifact is never uploaded' caught"
    fi

    rm -f "$mutant"
    return "$failed"
}

if [ "${1:-}" = "--self-check" ]; then
    echo "== self-check =="
    run_suite || exit 1
    echo
    self_check || exit 1
    echo "self-check passed"
    exit 0
fi

run_suite
