#!/bin/bash
# qa.sh -- score Doc 169 section 8 / Doc 170 Question A
#
# The SMD poll use-after-free is triggered by `echo stop` with qmi-proxy RUNNING.
# Pre-fix control: 2/2 runs produced `list_del corruption` in qmi-proxy's poll()
# and reset the AP (Doc 168 run B, Doc 169 run A).
#
# PRE-REGISTERED PREDICTION (Doc 170 section 7): with a fix deployed, N runs give
#   0 `list_del corruption` AND 0 AP resets.
#
# Run this ON THE BUILD HOST. Each iteration costs a modem SSR plus (on failure)
# an AP reboot, so the loop re-establishes ssh between runs.
#
# Usage: bash scratch/qa.sh [N] [label]

set -u

DEV=192.168.8.1
N="${1:-5}"
LABEL="${2:-qa}"
OUT="scratch/qa_${LABEL}_$(date +%Y%m%d_%H%M%S).log"
SSH=(ssh -o BatchMode=yes -o StrictHostKeyChecking=no -o ConnectTimeout=8 "root@$DEV")

log() { printf '%s\n' "$*" | tee -a "$OUT"; }

dev() { timeout 25 "${SSH[@]}" "$@" 2>&1; }

up() {
    timeout 10 "${SSH[@]}" 'cat /proc/uptime' 2>/dev/null | awk '{print $1}'
}

wait_up() {   # wait until the device answers again; echo uptime or ""
    local i
    for i in $(seq 1 40); do
        local u; u="$(up)"
        [ -n "$u" ] && { echo "$u"; return 0; }
        sleep 3
    done
    echo ""
    return 1
}

log "=== qa.sh N=$N label=$LABEL  $(date -Is) ==="

PASS=0; FAIL=0; SKIP=0

for i in $(seq 1 "$N"); do
    log ""
    log "---------------- run $i/$N ----------------"

    U0="$(wait_up)"
    if [ -z "$U0" ]; then log "  ABORT: device unreachable"; SKIP=$((SKIP+1)); continue; fi
    log "  pre  uptime = $U0"

    # --- preconditions -------------------------------------------------------
    # The bug needs THREE things at once: qmi-proxy alive, it holding a wwan fd,
    # and the modem up.  Check them separately -- a combined grep would let
    # "2: wwan0" from `ip link` satisfy a pid check.
    QPID="$(dev 'pidof qmi-proxy' | tr -d ' \r')"
    if [ -z "$QPID" ]; then
        log "  ABORT: qmi-proxy is not running -- this run would not test the bug"
        SKIP=$((SKIP+1)); continue
    fi
    FD="$(dev "ls -l /proc/$QPID/fd 2>/dev/null | grep -c wwan")"
    if [ "${FD:-0}" = "0" ]; then
        log "  ABORT: qmi-proxy pid=$QPID holds no wwan fd -- no poller to corrupt"
        SKIP=$((SKIP+1)); continue
    fi
    RST="$(dev 'cat /sys/class/remoteproc/remoteproc0/state' | tr -d ' \r')"
    if [ "$RST" != "running" ]; then
        log "  ABORT: remoteproc0 state is '$RST', not running"
        SKIP=$((SKIP+1)); continue
    fi
    log "  pre  qmi-proxy pid=$QPID  wwan fds=$FD  rproc=$RST"

    # Record what the poller is actually doing, so a run where it happens NOT to
    # be parked in poll() is distinguishable from a run where the fix worked.
    # /proc/<pid>/stack is readable as root on this device.
    WCHAN="$(dev "cat /proc/$QPID/wchan 2>/dev/null" | tr -d ' \r')"
    log "  pre  poller wchan=$WCHAN"
    dev "grep -E 'poll|schedule' /proc/$QPID/stack 2>/dev/null | head -4" | sed 's/^/       /' | tee -a "$OUT"

    # Wake the modem: the bug fires on the SSR teardown, which only runs when
    # the modem is up.
    dev 'ping -c 3 -W 2 -q 8.8.8.8 >/dev/null 2>&1; cat /proc/uptime' >/dev/null 2>&1

    # Snapshot the pstore record so a STALE console-ramoops cannot be mistaken
    # for this run's evidence (Doc 169 section 9 trap 1).
    CR_MD5_BEFORE="$(dev 'md5sum /sys/fs/pstore/console-ramoops-0 2>/dev/null' | awk '{print $1}')"
    log "  pre  console-ramoops md5 = ${CR_MD5_BEFORE:-none}"

    # --- trigger -------------------------------------------------------------
    log "  TRIGGER echo stop"
    T0="$(date +%s)"
    dev 'echo stop > /sys/class/remoteproc/remoteproc0/state; echo "write rc=$?"'
    log "  write returned after $(( $(date +%s) - T0 ))s"

    # --- did the AP survive? -------------------------------------------------
    sleep 8
    U1="$(wait_up)"
    RC="unknown"
    if [ -z "$U1" ]; then
        RC="unreachable"
    elif awk -v a="$U1" -v b="$U0" 'BEGIN{exit !(a < b)}'; then
        RC="RESET"
    else
        RC="survived"
    fi
    log "  post uptime = ${U1:-<none>}  =>  $RC"

    # --- corruption evidence -------------------------------------------------
    if [ "$RC" = "survived" ]; then
        # Live dmesg is the correct sink on a surviving boot.
        CORR="$(dev 'dmesg | grep -c "list_del corruption"')"
        LAST="$(dev 'dmesg | grep -m1 -o "list_del corruption.*"')"
        log "  dmesg list_del corruption count = ${CORR:-?}"
        [ -n "$LAST" ] && log "  first: $LAST"
    else
        # On a reset the record is only trustworthy if it is NEW and its own
        # embedded uptime is near this run's U0.
        CR_MD5_AFTER="$(dev 'md5sum /sys/fs/pstore/console-ramoops-0 2>/dev/null' | awk '{print $1}')"
        FRESH="stale"
        [ -n "$CR_MD5_AFTER" ] && [ "$CR_MD5_AFTER" != "$CR_MD5_BEFORE" ] && FRESH="fresh"
        log "  console-ramoops md5 = ${CR_MD5_AFTER:-none} ($FRESH)"
        if [ "$FRESH" = "fresh" ]; then
            CORR="$(dev 'grep -c "list_del corruption" /sys/fs/pstore/console-ramoops-0 2>/dev/null')"
            RECU="$(dev 'grep -o "^\[ *[0-9]*\." /sys/fs/pstore/console-ramoops-0 2>/dev/null | tail -1 | tr -dc "0-9."')"
            log "  record list_del corruption count = ${CORR:-?}   record ends at t=${RECU:-?} (pre-run uptime $U0)"
            dev 'grep -m1 -o "list_del corruption.*" /sys/fs/pstore/console-ramoops-0 2>/dev/null' | sed 's/^/  first: /' | tee -a "$OUT"
        else
            CORR="?"
            log "  no fresh crash record -- cannot attribute a corruption to this run"
        fi
    fi

    # --- teardown progress ---------------------------------------------------
    TEARDOWN="$(dev 'dmesg | grep -c "bam-dmux T9"')"
    log "  teardown T9 count = ${TEARDOWN:-?}"

    # --- verdict -------------------------------------------------------------
    if [ "$RC" = "survived" ] && [ "${CORR:-1}" = "0" ]; then
        log "  VERDICT: PASS (survived, 0 corruption)"
        PASS=$((PASS+1))
    else
        log "  VERDICT: FAIL (rc=$RC corruption=${CORR:-?})"
        FAIL=$((FAIL+1))
    fi

    # let the device settle / the modem restart before the next run
    sleep 12
    dev 'cat /proc/uptime' >/dev/null 2>&1
done

log ""
log "=== RESULT: $PASS PASS / $FAIL FAIL / $SKIP SKIP  (n=$N) ==="
log "=== log: $OUT ==="
