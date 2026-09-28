#!/bin/sh
# modem_guard.sh — bootloop safety net for the UZ801-on-HMU05 modem port.
#
# WHY
#   A bad baseband patch can make the modem crash during its own boot path. The
#   crash trips the PMIC PON watchdog, which resets the WHOLE SoC, so the AP
#   reboots and the bad firmware crashes again on the next boot — an AP reset
#   loop that costs physical access to break.
#
# WHAT
#   Count consecutive boots that never reach a stable state. On the boot after
#   the threshold is exceeded, restore the stock HMU05 modem firmware from
#   /overlay/fwbackup/hmu05_stock so the device comes back up and the offending
#   patch can be inspected. The live (bad) firmware is preserved verbatim under
#   /overlay/modem_guard/quarantine/<timestamp>/ before it is overwritten.
#
#   This runs from /etc/init.d/modem-guard at START=01, i.e. BEFORE the kernel
#   deferred-probes remoteproc0 (measured: rcS S01 ~= AP uptime 8.9 s, modem
#   powers up at 11.19 s — see Doc 194 §3.3). So on the revert boot the modem
#   still boots the stock image, not the bad one.
#
# SUBCOMMANDS
#   boot            called from the init script at every boot
#   watch           the procd instance: clears the counter after GOOD_AFTER s
#                   up, or immediately on the SIGTERM of a clean shutdown
#   markgood        clear the counter now (manual / test)
#   clean-shutdown  clear the counter now (manual / test)
#   status          print counter, markers and recent log
#   reset           zero the counter (also done by the deploy helper)
#   revert-now      force a revert immediately (manual rescue)
#   show-quarantine list preserved bad firmware sets
#
# STATE  (all on the overlay, so it survives the reset)
#   /overlay/modem_guard/boot_count      consecutive unproven boots
#   /overlay/modem_guard/events.log      append-only human timeline
#   /overlay/modem_guard/config          optional overrides (see below)
#   /overlay/modem_guard/deployed        manifest written by the deploy helper
#   /overlay/modem_guard/DISABLED        presence disables the guard
#   /overlay/modem_guard/quarantine/<ts> the exact bad set, before it is replaced
#   /overlay/modem_guard/REVERT_DONE     summary of the last automatic revert
#   /overlay/modem_guard/REVERT_FAILED   set if a revert could not be completed
#
# CONFIG overrides (/overlay/modem_guard/config, shell syntax):
#   MAX_BOOTLOOPS=3        revert once boot_count > MAX_BOOTLOOPS
#                          (i.e. after 3 bootloops, on the 4th boot)
#   GOOD_AFTER=180         seconds up before a boot counts as good
#   STOCK_DIR=...          source of the stock HMU05 set
#   STOCK_MDT_MD5=...      expected md5 of the restored modem.mdt

GUARD_DIR="${MODEM_GUARD_DIR:-/overlay/modem_guard}"
FW_DIR="${MODEM_GUARD_FW_DIR:-/lib/firmware}"
STOCK_DIR="${MODEM_GUARD_STOCK_DIR:-/overlay/fwbackup/hmu05_stock}"
MAX_BOOTLOOPS="${MODEM_GUARD_MAX:-3}"
GOOD_AFTER="${MODEM_GUARD_GOOD_AFTER:-180}"
STOCK_MDT_MD5="${MODEM_GUARD_STOCK_MDT_MD5:-1a6f9507e03d4ddbbf1977af81ecdbd7}"

STATE="$GUARD_DIR/boot_count"
LOG="$GUARD_DIR/events.log"
MARK_OK="$GUARD_DIR/REVERT_DONE"
MARK_FAIL="$GUARD_DIR/REVERT_FAILED"
NOTICE="${MODEM_GUARD_NOTICE:-/root/BOOTLOOP_REVERT.txt}"

# Optional overrides. Sourced AFTER the defaults so it wins.
[ -f "$GUARD_DIR/config" ] && . "$GUARD_DIR/config"

log() { printf '%s %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*" >>"$LOG" 2>/dev/null; }
kmsg() { { echo "modem-guard: $*" >/dev/kmsg; } 2>/dev/null; }

read_count() {
    n=$(cat "$STATE" 2>/dev/null)
    case "$n" in ''|*[!0-9]*) n=0 ;; esac
    echo "$n"
}

write_count() {
    echo "$1" >"$STATE" 2>/dev/null
    sync
}

mdt_md5() { md5sum "$FW_DIR/modem.mdt" 2>/dev/null | awk '{print $1}'; }

# ---------------------------------------------------------------- boot
do_boot() {
    mkdir -p "$GUARD_DIR" 2>/dev/null
    [ -f "$STATE" ] || write_count 0

    if [ -f "$GUARD_DIR/DISABLED" ]; then
        log "boot: guard DISABLED (marker present), not counting"
        return 0
    fi

    n=$(read_count)
    n=$((n + 1))
    write_count "$n"
    log "boot: consecutive_unproven=$n threshold=$MAX_BOOTLOOPS"
    kmsg "boot #$n (threshold $MAX_BOOTLOOPS)"

    [ "$n" -gt "$MAX_BOOTLOOPS" ] || return 0

    # Already stock? Then the loop is not a firmware problem — do not thrash.
    if [ "$(mdt_md5)" = "$STOCK_MDT_MD5" ]; then
        log "bootloop threshold exceeded but firmware is already stock HMU05; resetting counter"
        kmsg "bootloop ($n) but firmware already stock — not a firmware loop"
        write_count 0
        return 0
    fi

    log "BOOTLOOP: $n > $MAX_BOOTLOOPS consecutive unproven boots; reverting to stock HMU05"
    kmsg "BOOTLOOP ($n) -> reverting modem firmware to stock HMU05"
    if do_revert "$n"; then
        write_notice "$n"
        sync
        if [ -n "${MODEM_GUARD_NO_REBOOT:-}" ]; then
            log "MODEM_GUARD_NO_REBOOT set: skipping reboot (test/inspection mode)"
        else
            kmsg "revert complete; rebooting into stock firmware"
            reboot -f
        fi
    else
        log "revert did not complete; leaving system up for inspection"
        kmsg "revert FAILED — leaving system up"
    fi
}

# ---------------------------------------------------------------- revert
do_revert() {
    n="$1"
    ts=$(date '+%Y%m%d_%H%M%S')
    qdir="$GUARD_DIR/quarantine/$ts"

    if ! mkdir -p "$qdir" 2>/dev/null; then
        log "REVERT FAILED: cannot create $qdir"
        echo "cannot create $qdir" >"$MARK_FAIL"
        return 1
    fi

    # 1. preserve exactly what was live, plus its identity
    md5sum "$FW_DIR"/modem.* "$FW_DIR"/mba.mbn >"$qdir/deployed.md5" 2>/dev/null
    cp -f "$FW_DIR"/modem.* "$FW_DIR"/mba.mbn "$qdir/" 2>/dev/null
    [ -f "$GUARD_DIR/deployed" ] && cp -f "$GUARD_DIR/deployed" "$qdir/deployed.info" 2>/dev/null
    {
        echo "boot_count=$n"
        echo "reverted_at=$ts"
        echo "live_modem.mdt_md5=$(mdt_md5)"
        echo "expected_stock_modem.mdt_md5=$STOCK_MDT_MD5"
        echo "fw_dir=$FW_DIR"
        echo "stock_dir=$STOCK_DIR"
    } >"$qdir/context.txt"
    log "quarantined live firmware -> $qdir"

    # 2. the stock source must exist and be verifiable
    if [ ! -f "$STOCK_DIR/modem.mdt" ]; then
        log "REVERT FAILED: $STOCK_DIR/modem.mdt missing"
        echo "missing $STOCK_DIR/modem.mdt" >"$MARK_FAIL"
        return 1
    fi
    src_md5=$(md5sum "$STOCK_DIR/modem.mdt" 2>/dev/null | awk '{print $1}')
    if [ "$src_md5" != "$STOCK_MDT_MD5" ]; then
        log "REVERT FAILED: stock source modem.mdt=$src_md5 expected=$STOCK_MDT_MD5"
        echo "stock source modem.mdt md5=$src_md5 expected=$STOCK_MDT_MD5" >"$MARK_FAIL"
        return 1
    fi

    # 3. restore
    cp -f "$STOCK_DIR"/modem.* "$FW_DIR"/ || { log "REVERT FAILED: copy error"; echo "copy error" >"$MARK_FAIL"; return 1; }
    [ -f "$STOCK_DIR/mba.mbn" ] && cp -f "$STOCK_DIR/mba.mbn" "$FW_DIR/mba.mbn"
    sync

    # 4. verify the live image really is stock
    got=$(mdt_md5)
    if [ "$got" != "$STOCK_MDT_MD5" ]; then
        log "REVERT FAILED: live modem.mdt=$got expected=$STOCK_MDT_MD5"
        echo "live modem.mdt md5=$got expected=$STOCK_MDT_MD5" >"$MARK_FAIL"
        return 1
    fi

    rm -f "$MARK_FAIL"
    write_count 0
    {
        echo "Automatic bootloop revert"
        echo "when:                  $(date)"
        echo "consecutive bad boots: $n (threshold $MAX_BOOTLOOPS)"
        echo "restored:              stock HMU05 modem firmware from $STOCK_DIR"
        echo "live modem.mdt md5:    $got (expected $STOCK_MDT_MD5)"
        echo "offending set kept at: $qdir"
        echo "full timeline:         $LOG"
    } >"$MARK_OK"
    log "REVERT OK: stock HMU05 restored (modem.mdt=$got); counter reset"
    return 0
}

write_notice() {
    n="$1"
    reason="${2:-bootloop}"
    qdir=$(sed -n 's/^offending set kept at: //p' "$MARK_OK" 2>/dev/null)
    if [ "$reason" = manual ]; then
        headline=" MODEM FIRMWARE REVERTED TO STOCK HMU05 (manual revert-now)"
        body="The modem firmware was reverted to the stock HMU05 set on $(date)."
    else
        headline=" MODEM BOOTLOOP — FIRMWARE AUTO-REVERTED TO STOCK HMU05"
        body="The patched baseband boot-looped $n times (> $MAX_BOOTLOOPS), so it was
replaced with the stock HMU05 set on $(date)."
    fi
    {
        echo "================================================================"
        echo "$headline"
        echo "================================================================"
        echo
        echo "$body"
        echo
        echo "  bad set preserved : ${qdir:-unknown}"
        echo "  revert summary    : $MARK_OK"
        echo "  timeline          : $LOG"
        echo "  status            : /root/modem_guard.sh status"
        echo "  re-arm for a new patch: /root/modem_guard.sh reset"
        echo
        echo "The bad firmware is byte-preserved under $GUARD_DIR/quarantine/."
        echo "Diff it against scratch/uz801_patched/ to identify the offending patch."
        echo "================================================================"
    } >"$NOTICE" 2>/dev/null
    log "wrote notice $NOTICE ($reason)"
}

# ---------------------------------------------------------------- others
# Runs as the procd instance for the whole boot.
#   - survives GOOD_AFTER seconds  -> the boot is good, clear the counter
#   - gets SIGTERM first           -> a clean shutdown, clear the counter
# A PMIC PON watchdog reset gives no signal at all, which is exactly how a
# crash reboot is told apart from an intentional one.
do_watch() {
    trap 'log "clean shutdown (signal): counter $(read_count) -> 0"; write_count 0; exit 0' TERM INT HUP
    sleep "$GOOD_AFTER" &
    wait $!
    do_markgood
}

do_markgood() {
    [ -f "$GUARD_DIR/DISABLED" ] && return 0
    n=$(read_count)
    write_count 0
    log "mark good: up >= ${GOOD_AFTER}s, counter $n -> 0"
    kmsg "stable for ${GOOD_AFTER}s; boot counter cleared"
}

do_clean_shutdown() {
    n=$(read_count)
    write_count 0
    log "clean shutdown: counter $n -> 0"
}

do_status() {
    echo "guard dir    : $GUARD_DIR"
    echo "firmware dir : $FW_DIR"
    echo "stock source : $STOCK_DIR"
    echo "threshold    : > $MAX_BOOTLOOPS consecutive unproven boots"
    echo "good after   : ${GOOD_AFTER}s"
    echo "disabled     : $([ -f "$GUARD_DIR/DISABLED" ] && echo yes || echo no)"
    echo "boot_count   : $(read_count)"
    echo "live modem.mdt: $(mdt_md5)  ($([ "$(mdt_md5)" = "$STOCK_MDT_MD5" ] && echo 'STOCK HMU05' || echo 'not stock'))"
    echo "stock modem.mdt: $STOCK_MDT_MD5"
    [ -f "$MARK_OK" ] && { echo "--- last revert ---"; cat "$MARK_OK"; }
    [ -f "$MARK_FAIL" ] && { echo "--- REVERT FAILED ---"; cat "$MARK_FAIL"; }
    echo "--- last 10 events ---"
    tail -n 10 "$LOG" 2>/dev/null
}

do_reset() {
    write_count 0
    rm -f "$MARK_FAIL"
    log "manual reset: counter -> 0"
    echo "counter reset to 0"
}

do_show_quarantine() {
    ls -1 "$GUARD_DIR/quarantine" 2>/dev/null || echo "(none)"
}

case "${1:-}" in
    boot)           do_boot ;;
    watch)          do_watch ;;
    markgood)       do_markgood ;;
    clean-shutdown) do_clean_shutdown ;;
    status)         do_status ;;
    reset)          do_reset ;;
    revert-now)     n=$(read_count); do_revert "$n" && write_notice "$n" manual ;;
    show-quarantine) do_show_quarantine ;;
    *)
        echo "usage: $0 {boot|watch|markgood|clean-shutdown|status|reset|revert-now|show-quarantine}" >&2
        exit 2
        ;;
esac
