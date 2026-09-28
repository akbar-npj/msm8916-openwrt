#!/bin/bash
# deploy_uz801_patched.sh — deploy a patched UZ801 modem set to the device and
# re-arm the bootloop guard for a fresh trial.
#
# Run ON THE BUILD HOST, from the repo root:
#   bash "Docs/Modem Stability/evidence/197_bootloop_guard/deploy_uz801_patched.sh" [options]
#
# Options:
#   --dir DIR      source set (default: scratch/uz801_patched)
#   --label NAME   label recorded in the manifest (default: git describe / timestamp)
#   --reboot       reboot after installing (recommended: the guard then protects it)
#   --no-reset     do NOT reset the guard counter (deploying over a live trial)
#   --dry-run      verify + show the manifest, install nothing
#
# What it deploys: the modem subsystem only — modem.* and mba.mbn. The WCNSS /
# TrustZone images are deliberately left alone (they are not what the port patches).
#
# Safety: every file is md5-verified on the device BEFORE it is installed; on any
# mismatch nothing further is changed. The guard is reset only after a fully
# verified install, so a partial deploy cannot silently weaken the safety net.
set -uo pipefail

DEV="${DEV:-192.168.8.1}"
SRC="scratch/uz801_patched"
LABEL=""
REBOOT=0
RESET=1
DRY=0

while [ $# -gt 0 ]; do
    case "$1" in
        --dir)      SRC="$2"; shift 2 ;;
        --label)    LABEL="$2"; shift 2 ;;
        --reboot)   REBOOT=1; shift ;;
        --no-reset) RESET=0; shift ;;
        --dry-run)  DRY=1; shift ;;
        *) echo "unknown option: $1" >&2; exit 2 ;;
    esac
done

[ -d "$SRC" ] || { echo "ERROR: source set $SRC not found"; exit 1; }
[ -f "$SRC/modem.mdt" ] || { echo "ERROR: $SRC/modem.mdt missing"; exit 1; }
[ -f "$SRC/modem.b16" ] || { echo "ERROR: $SRC/modem.b16 missing"; exit 1; }

SSH=(ssh -o BatchMode=yes -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o LogLevel=ERROR -o ConnectTimeout=8 "root@$DEV")
dev() { timeout 120 "${SSH[@]}" "$@" 2>&1; }

if [ -z "$LABEL" ]; then
    LABEL="$(git -C "$(git rev-parse --show-toplevel 2>/dev/null || echo .)" describe --dirty --always 2>/dev/null || true)"
    [ -n "$LABEL" ] || LABEL="manual-$(date +%Y%m%d_%H%M%S)"
fi

echo "==> source set : $SRC"
echo "==> label      : $LABEL"

# ---------------------------------------------------------------- verify locally
echo "==> verifying the segment hashes against modem.mdt"
if [ -f GitIgnore/compare/ufi001b_hash_tool.py ]; then
    if ! python3 GitIgnore/compare/ufi001b_hash_tool.py verify "$SRC" >/tmp/deploy_verify.txt 2>&1; then
        echo "ERROR: ufi001b_hash_tool verify FAILED"; cat /tmp/deploy_verify.txt; exit 1
    fi
    echo "    hash tool: PASS"
else
    echo "    WARN: GitIgnore/compare/ufi001b_hash_tool.py not found; skipping hash-tool verify"
fi

# Only the real segment files: modem.mdt, modem.b<NN>, mba.mbn. The anchored
# regex is deliberate — a bare `modem.*` would also sweep in the .bak /
# .patched / .cm_patched / .oprt_jump scratch copies sitting in the same dir.
FILES="$(cd "$SRC" && ls -1 2>/dev/null | grep -E '^(modem\.(mdt|b[0-9]+)|mba\.mbn)$')"
[ -n "$FILES" ] || { echo "ERROR: no modem.mdt / modem.b<NN> / mba.mbn in $SRC"; exit 1; }
NFILES="$(echo "$FILES" | wc -w)"
# Space-separated, single line: $FILES keeps its newlines, and embedding that in a
# double-quoted remote command makes the shell treat every line after the first as
# a *separate command* (only modem.b00 gets copied). Collapse first.
FILELIST="$(echo $FILES)"
echo "==> set contains $NFILES files"

# ---------------------------------------------------------------- manifest
MAN="$(mktemp)"
{
    echo "label=$LABEL"
    echo "deployed_at=$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
    echo "source_set=$SRC"
    echo "host=$(hostname)"
    echo "--- md5 ---"
    (cd "$SRC" && md5sum $FILES)
} >"$MAN"
echo "--- manifest ---"; cat "$MAN"; echo "----------------"

if [ "$DRY" = 1 ]; then
    echo "==> dry run: nothing installed"; rm -f "$MAN"; exit 0
fi

# ---------------------------------------------------------------- transfer
echo "==> transferring to $DEV:/tmp/modem_deploy"
dev 'rm -rf /tmp/modem_deploy && mkdir -p /tmp/modem_deploy'
(cd "$SRC" && tar cf - $FILES) | dev 'tar xf - -C /tmp/modem_deploy' >/dev/null \
    || { echo "ERROR: transfer failed"; rm -f "$MAN"; exit 1; }

# ---------------------------------------------------------------- verify remote
echo "==> verifying md5 on the device BEFORE installing"
FAIL=0
for f in $FILES; do
    LM="$(md5sum "$SRC/$f" | awk '{print $1}')"
    RM="$(dev "md5sum /tmp/modem_deploy/$f 2>/dev/null" | awk '{print $1}')"
    if [ "$RM" != "$LM" ]; then
        echo "    ERROR: $f remote=$RM local=$LM"; FAIL=1
    fi
done
[ "$FAIL" = 0 ] || { echo "ERROR: aborting, nothing installed"; rm -f "$MAN"; exit 1; }
echo "    all $NFILES files match"

# ---------------------------------------------------------------- install
echo "==> installing into /lib/firmware"
if ! dev "cd /tmp/modem_deploy && cp -f $FILELIST /lib/firmware/ && sync" >/dev/null; then
    echo "ERROR: install failed; live set may be PARTIAL — restore before rebooting:"
    echo "       ssh root@$DEV 'cp -f /overlay/fwbackup/hmu05_stock/modem.* /lib/firmware/'"
    rm -f "$MAN"; exit 1
fi

echo "==> verifying the live image"
LIVE_MDT="$(dev 'md5sum /lib/firmware/modem.mdt' | awk '{print $1}')"
WANT_MDT="$(md5sum "$SRC/modem.mdt" | awk '{print $1}')"
if [ "$LIVE_MDT" != "$WANT_MDT" ]; then
    echo "ERROR: live modem.mdt=$LIVE_MDT expected=$WANT_MDT"
    echo "       the live set may be PARTIAL (modem.mdt vs modem.bNN inconsistent)."
    echo "       restore before rebooting:"
    echo "       ssh root@$DEV 'cp -f /overlay/fwbackup/hmu05_stock/modem.* /lib/firmware/'"
    rm -f "$MAN"; exit 1
fi
echo "    live modem.mdt ok ($LIVE_MDT)"

# ---------------------------------------------------------------- arm the guard
if [ "$RESET" = 1 ]; then
    echo "==> re-arming the bootloop guard (reset counter + record manifest)"
    cat "$MAN" | dev 'mkdir -p /overlay/modem_guard && cat > /overlay/modem_guard/deployed && /root/modem_guard.sh reset' >/dev/null
    dev 'cat /overlay/modem_guard/deployed'
else
    echo "==> --no-reset: guard counter left as-is"
fi
rm -f "$MAN"

if [ "$REBOOT" = 1 ]; then
    echo "==> rebooting"
    dev 'sync; sync; reboot' >/dev/null || true
    echo "    (the guard will count this boot; if the patch bootloops it reverts"
    echo "     to stock HMU05 after >3 unproven boots)"
else
    echo "==> installed. Reboot to activate:  ssh root@$DEV 'sync; reboot'"
fi
