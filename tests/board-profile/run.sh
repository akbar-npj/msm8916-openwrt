#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-2.0-only
#
# Hardware-free checks for board support: every diffconfigs/<board> must have a
# device profile, a device tree the build actually compiles, the DTB registered,
# a matching SUPPORTED_DEVICES, sane file modes, and no change to a file that
# every board shares without a board gate.
#
#   tests/board-profile/run.sh [--self-check] [--base-ref REF] [--root DIR]
#
# --base-ref diffs the working tree against REF and additionally enforces the
# shared-file gate (CI passes the pull request's base branch).  Without it that
# one check is skipped: a plain "is the tree consistent" run has no diff.
#
# --self-check copies the tree to a scratch git repo and applies 5 mutants; each
# one must turn the matching check red, otherwise the check itself is broken.
#
# The rules are the mechanical half of Docs/ADDING_A_DEVICE_BOARD.md section 9.
set -u

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=""
BASE_REF=""
SHARED_DIFF=""
SELF_CHECK=0
while [ $# -gt 0 ]; do
    case "$1" in
        --self-check) SELF_CHECK=1 ;;
        --root)       shift; ROOT=$1 ;;
        --base-ref)   shift; BASE_REF=$1 ;;
        --shared-diff) shift; SHARED_DIFF=$1 ;;
        *) echo "usage: $0 [--self-check] [--base-ref REF] [--root DIR] [--shared-diff FILE]" >&2; exit 2 ;;
    esac
    shift
done
[ -n "$ROOT" ] || ROOT=$(cd "$HERE/../.." && pwd)

PASS=0; FAIL=0; SKIP=0
ok()   { PASS=$((PASS + 1)); printf '  ok   %s\n' "$*"; }
bad()  { FAIL=$((FAIL + 1)); printf '  FAIL %s\n' "$*"; }
skip() { SKIP=$((SKIP + 1)); printf '  skip %s\n' "$*"; }
section() { printf '\n%s\n' "$*"; }

MK=$ROOT/msm89xx/image/msm8916.mk
PATCHES=$ROOT/msm89xx/patches
BUILD_YML=$ROOT/.github/workflows/build.yml

# ---- helpers ---------------------------------------------------------------

# profile_field <device> <VAR> -> the value assigned inside "define Device/<device>"
profile_field() {
    awk -v want="$1" -v var="$2" '
        /^define Device\// { dev = $2; sub(/^Device\//, "", dev); inblk = (dev == want); next }
        /^endef/           { inblk = 0; next }
        inblk && $0 ~ "^[ \t]*" var "[ \t]*:?=" {
            line = $0
            sub(/^[^=]*=[ \t]*/, "", line)
            print line
            exit
        }
    ' "$MK"
}

# profile_devices -> every device that has a "TARGET_DEVICES +=" line
profile_devices() {
    awk '
        /^define Device\// { dev = $2; sub(/^Device\//, "", dev); next }
        /^TARGET_DEVICES[ \t]*\+=/ { if (dev != "") print dev; dev = "" }
    ' "$MK"
}

# dts_creator <dts-basename> -> patch that adds arch/arm64/boot/dts/qcom/<dts>.dts
dts_creator() {
    local want="arch/arm64/boot/dts/qcom/$1.dts" f
    for f in "$PATCHES"/*.patch; do
        awk -v want="$want" -v f="$f" '
            /^--- \/dev\/null[ \t]*$/ { newfile = 1; next }
            newfile && $0 == "+++ b/" want { print f; exit }
            { newfile = 0 }
        ' "$f"
    done
}

# dtb_registered <dts-basename> -> patch that adds the dtb- line
dtb_registered() {
    local want="$1.dtb" f
    for f in "$PATCHES"/*.patch; do
        grep -qE "^\+dtb-.*[[:space:]]$want[[:space:]]*$" "$f" && echo "$f"
    done
}

# dts_compatible <patch> -> first compatible string the patch adds
dts_compatible() {
    grep -m1 -E '^\+[[:space:]]*compatible[[:space:]]*=[[:space:]]*"' "$1" |
        sed -e 's/^[^"]*"//' -e 's/".*$//'
}

# rel <abs path> -> path relative to the repository root
rel() { printf '%s' "$1" | sed "s|^$ROOT/||"; }

# ---- 1. diffconfig <-> device profile --------------------------------------
section "diffconfig <-> device profile"
known_devices=$(profile_devices)
for cfg in "$ROOT"/diffconfigs/*; do
    board=$(basename "$cfg")
    lines=$(grep -cE '^CONFIG_TARGET_msm89xx_msm8916_DEVICE_[A-Za-z0-9_-]+=y$' "$cfg")
    if [ "$lines" -ne 1 ]; then
        bad "diffconfigs/$board selects $lines devices (want exactly 1)"
        continue
    fi
    dev=$(grep -oE '^CONFIG_TARGET_msm89xx_msm8916_DEVICE_[A-Za-z0-9_-]+=y$' "$cfg" |
          sed -e 's/^CONFIG_TARGET_msm89xx_msm8916_DEVICE_//' -e 's/=y$//')
    if ! printf '%s\n' "$known_devices" | grep -qx -- "$dev"; then
        bad "diffconfigs/$board selects '$dev', but msm8916.mk has no 'TARGET_DEVICES += $dev'"
        continue
    fi
    ok "diffconfigs/$board -> Device/$dev"
done
for dev in $known_devices; do
    hit=$(grep -lE "^CONFIG_TARGET_msm89xx_msm8916_DEVICE_$dev=y$" "$ROOT"/diffconfigs/* 2>/dev/null | head -1)
    [ -n "$hit" ] || bad "Device/$dev is built by no diffconfigs/ entry"
done

# ---- 2. device tree: created by a patch => registered as a dtb -------------
section "device tree and DTB registration"
for dev in $known_devices; do
    dts=$(profile_field "$dev" DEVICE_DTS)
    [ -n "$dts" ] || dts="msm8916-$dev"
    dts=$(printf '%s' "$dts" | sed 's/\.dts$//')
    creator=$(dts_creator "$dts")
    if [ -z "$creator" ]; then
        skip "$dev: $dts.dts is not added by a patch (upstream board)"
        continue
    fi
    reg=$(dtb_registered "$dts")
    if [ -n "$reg" ]; then
        ok "$dev: $dts.dtb registered in $(basename "$reg")"
    else
        bad "$dev: $(basename "$creator") adds $dts.dts but no patch registers $dts.dtb"
    fi
done

# ---- 3. SUPPORTED_DEVICES matches the DTS compatible -----------------------
section "SUPPORTED_DEVICES <-> DTS compatible"
for dev in $known_devices; do
    dts=$(profile_field "$dev" DEVICE_DTS)
    [ -n "$dts" ] || dts="msm8916-$dev"
    dts=$(printf '%s' "$dts" | sed 's/\.dts$//')
    creator=$(dts_creator "$dts")
    [ -n "$creator" ] || { skip "$dev: DTS not in a patch"; continue; }
    want=$(profile_field "$dev" SUPPORTED_DEVICES | awk '{print $1}')
    got=$(dts_compatible "$creator")
    if [ -n "$want" ] && [ "$want" = "$got" ]; then
        ok "$dev: $want"
    else
        bad "$dev: SUPPORTED_DEVICES starts with '$want', DTS compatible is '$got'"
    fi
done

# ---- 4. build.yml choice list matches diffconfigs/ -------------------------
section "build.yml device list"
choices=$(sed -n '/^ *options:/,/^ *[a-z_]*:/p' "$BUILD_YML" |
          grep -E '^ *- ' | sed 's/^ *- //' | grep -vx 'all' | sort)
boards=$(ls "$ROOT"/diffconfigs | sort)
if [ "$choices" = "$boards" ]; then
    ok "build.yml lists exactly $(printf '%s\n' "$boards" | wc -l) boards"
else
    bad "build.yml and diffconfigs/ disagree:"
    diff <(printf '%s\n' "$choices") <(printf '%s\n' "$boards") | sed 's/^/       /'
fi

# ---- 5. file modes and rc.d symlinks ---------------------------------------
section "file modes"
modes_bad=0
while read -r mode sha stage path; do
    [ -n "$path" ] || continue
    if [ "$mode" != "100755" ] && [ "$mode" != "120000" ]; then
        bad "$(rel "$path") is $mode (want 100755)"
        modes_bad=$((modes_bad + 1))
    fi
done <<EOF
$(git -C "$ROOT" ls-files -s msm89xx/base-files/etc/init.d msm89xx/base-files/etc/hotplug.d \
      msm89xx/base-files/etc/uci-defaults msm89xx/base-files/usr/sbin msm89xx/base-files/usr/bin 2>/dev/null)
EOF
[ "$modes_bad" -eq 0 ] && ok "every script under init.d/hotplug.d/uci-defaults/usr/sbin is 100755"

while read -r mode sha stage path; do
    [ -n "$path" ] || continue
    name=$(basename "$path")
    if [ "$mode" != "120000" ]; then
        bad "$(rel "$path") is $mode (want a 120000 symlink)"
        continue
    fi
    target=$(git -C "$ROOT" cat-file -p "$sha" 2>/dev/null)
    init=$(printf '%s' "$name" | sed -E 's/^S[0-9]+//')
    if [ "$target" = "../init.d/$init" ] && [ -f "$ROOT/msm89xx/base-files/etc/init.d/$init" ]; then
        ok "rc.d/$name -> ../init.d/$init"
    else
        bad "rc.d/$name -> '$target' (want ../init.d/$init, which must exist)"
    fi
done <<EOF
$(git -C "$ROOT" ls-files -s msm89xx/base-files/etc/rc.d 2>/dev/null)
EOF

# ---- 6. diffconfig hygiene -------------------------------------------------
section "diffconfig hygiene"
for cfg in "$ROOT"/diffconfigs/*; do
    board=$(basename "$cfg")
    repo=$(sed -n 's/^CONFIG_VERSION_REPO="\(.*\)"$/\1/p' "$cfg")
    case "$repo" in
        */packages) bad "diffconfigs/$board: CONFIG_VERSION_REPO ends in /packages (the feed generator appends it)" ;;
        "")         bad "diffconfigs/$board: no CONFIG_VERSION_REPO" ;;
        *)          ok "diffconfigs/$board: repo $repo" ;;
    esac
done

# ---- 7. patch numbering ----------------------------------------------------
section "patch series"
dups=$(ls "$PATCHES" | sed -nE 's/^([0-9]{3})-.*/\1/p' | sort | uniq -d)
if [ -z "$dups" ]; then
    ok "patch numbers are unique ($(ls "$PATCHES" | wc -l) patches)"
else
    bad "duplicate patch numbers: $(printf '%s' "$dups" | tr '\n' ' ')"
fi

# ---- 8. shared-file gate (diff-scoped) -------------------------------------
section "shared-file gate"
if [ -n "$SHARED_DIFF" ]; then
    diff_file=$SHARED_DIFF
elif [ -n "$BASE_REF" ]; then
    diff_file=$(mktemp)
    git -C "$ROOT" diff --unified=0 "$BASE_REF"...HEAD > "$diff_file" 2>/dev/null ||
        git -C "$ROOT" diff --unified=0 "$BASE_REF" > "$diff_file"
else
    diff_file=""
fi

if [ -z "$diff_file" ]; then
    skip "no --base-ref: nothing to compare against"
else
    # A file passes if ANY added line carries a gate: a comment above the
    # change may describe the board without naming it in code.
    ungated=$(awk '
        function flush() {
            if (shared && added > 0 && !gated) print file "\t" first
        }
        /^diff --git / {
            flush()
            file = $3; sub(/^a\//, "", file)
            # rc.d entries are symlinks: no place for a marker, and the mode
            # check already proves the target exists.
            shared = (file ~ /^msm89xx\/base-files\// && file !~ /\/etc\/rc\.d\//)
            added = 0; gated = 0; first = ""
            next
        }
        shared && /^\+/ && !/^\+\+\+/ {
            added++
            if ($0 ~ /board_name|is_compatible|board_match|BOARDS:/) gated = 1
            else if (first == "") {
                first = substr($0, 2)
                if (first ~ /^[ \t]*$/) first = "(blank line)"
            }
        }
        END { flush() }
    ' "$diff_file")
    if [ -z "$ungated" ]; then
        ok "every change under msm89xx/base-files/ is board-gated or marked ALL-BOARDS"
    else
        while IFS="$(printf '\t')" read -r f l; do
            bad "$f: shared change with no board gate: $(printf '%s' "$l" | cut -c1-70)"
        done <<EOF
$ungated
EOF
    fi
    [ -n "$SHARED_DIFF" ] || rm -f "$diff_file"
fi

# ---- self-check ------------------------------------------------------------
if [ "$SELF_CHECK" -eq 1 ]; then
    section "self-check (mutants must be caught)"
    TMP=$(mktemp -d /tmp/board-profile.XXXXXX)
    trap 'rm -rf "$TMP"' EXIT

    mutate() { # mutate <name> <expected-FAIL-substring> <mutation> <shared-diff>
        rm -rf "$TMP/repo"; mkdir -p "$TMP/repo/.github/workflows"
        cp -a "$ROOT/diffconfigs" "$ROOT/msm89xx" "$TMP/repo/"
        cp "$BUILD_YML" "$TMP/repo/.github/workflows/"
        ( cd "$TMP/repo" && git init -q . && git add -A &&
          git -c user.email=t@t -c user.name=t commit -qm base ) >/dev/null 2>&1
        ( cd "$TMP/repo" && eval "$3" ) || { bad "mutant '$1' could not be applied"; return; }
        out=$(bash "$HERE/run.sh" --root "$TMP/repo" --shared-diff "$4" 2>&1)
        if printf '%s' "$out" | grep -q "FAIL .*$2"; then
            ok "mutant '$1' caught"
        else
            bad "mutant '$1' NOT caught (expected a FAIL matching '$2')"
        fi
    }

    mutate "dtb line dropped"       "uf02.dtb"          "sed -i '/generic-uf02\.dtb/d' msm89xx/patches/801-*.patch" ""
    mutate "SUPPORTED_DEVICES typo" "SUPPORTED_DEVICES" "sed -i 's/SUPPORTED_DEVICES := mf800b,generic/SUPPORTED_DEVICES := mf800x,generic/' msm89xx/image/msm8916.mk" ""
    mutate "script loses +x"        "is 100644"         "chmod 644 msm89xx/base-files/etc/init.d/himi-ok && git add -A" ""
    mutate "duplicate patch number" "duplicate patch"   "cp msm89xx/patches/867-arm64-dts-qcom-add-msm8916-generic-uf896.patch msm89xx/patches/867-dup.patch" ""
    mutate "ungated shared change"  "shared change"     "true" "$HERE/fixtures/ungated.diff"
fi

printf '\n%d passed, %d failed, %d skipped\n' "$PASS" "$FAIL" "$SKIP"
[ "$FAIL" -eq 0 ]
