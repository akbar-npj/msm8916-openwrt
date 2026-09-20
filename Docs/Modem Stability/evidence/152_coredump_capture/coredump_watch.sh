#!/bin/sh
# Capture modem (MPSS) coredumps as they appear.
#
# devcoredump semantics, verified against drivers/base/devcoredump.c in 6.12.94:
#
#  * The per-device attribute is ONLY `data`.  There is NO per-device `disabled`.
#    `disabled` is a CLASS-level, write-once, GLOBAL lockdown switch
#    (/sys/class/devcoredump/disabled).  NEVER write it -- it cannot be undone
#    until reboot and it kills every future dump.
#
#  * `data` is a bin_attribute with `.size = 0`, so `test -s` is FALSE even when a
#    dump is present.  Do NOT guard on `-s`; guard on the device directory existing.
#    (An earlier version of this script guarded on `-s "$d/data"` and therefore
#    captured nothing -- it silently missed fatal #11.)
#
#  * Writing to `data` calls devcd_data_write(), which cancels the 5-minute delete
#    timer and reschedules it at delay 0 -> the dump is released immediately.
#
#  * Without a release the device persists ~5 min and the SAME dump is re-captured
#    every poll (~9 s for 85 MB).  Dedupe by device name as well.
#
# The device has no nohup: launch this under a background ssh from the host.
#
# Usage:  /overlay/coredump_watch.sh [outdir] [logfile]
#
# Autostart: /etc/rc.local starts it via start-stop-daemon.  NOTE: busybox
# start-stop-daemon's -x match does NOT match a script's cmdline (/bin/sh
# /overlay/...), so -S alone is NOT idempotent -- it spawned a duplicate when
# tested.  Instead this script writes its own pidfile and the caller guards on
# that with kill -0.

OUT="${1:-/overlay/coredump_live}"
LOG="${2:-/overlay/coredump_watch.log}"
PIDFILE="${3:-/var/run/coredump_watch.pid}"
mkdir -p "$OUT"
seen=""

# Single-instance guard: refuse to run if a live watcher already holds the pidfile.
if [ -f "$PIDFILE" ]; then
    old=$(cat "$PIDFILE" 2>/dev/null)
    if [ -n "$old" ] && kill -0 "$old" 2>/dev/null; then
        echo "[$(cut -d' ' -f1 /proc/uptime)s] pidfile $PIDFILE held by live pid $old; exiting" \
            >> "$LOG"
        exit 0
    fi
fi
echo $$ > "$PIDFILE"
trap 'rm -f "$PIDFILE"' EXIT INT TERM HUP

log() { echo "[$(cut -d' ' -f1 /proc/uptime)s] $*" >> "$LOG"; }

log "=== coredump_watch start pid=$$ out=$OUT armed=$(cat /sys/class/remoteproc/remoteproc0/coredump 2>/dev/null) ==="

while :; do
    # keep it armed (the setting does not survive a reboot)
    cur=$(cat /sys/class/remoteproc/remoteproc0/coredump 2>/dev/null)
    [ "$cur" = "enabled" ] || echo enabled > /sys/class/remoteproc/remoteproc0/coredump 2>/dev/null

    for d in /sys/class/devcoredump/devcd*/; do
        [ -d "$d" ] || continue
        n=${d%/}; n=${n##*/}
        case " $seen " in *" $n "*) continue;; esac

        up=$(cut -d' ' -f1 /proc/uptime)
        f="$OUT/modem_coredump_up${up}_${n}.elf"
        log "dump device $n at uptime ${up}s -> $f"

        cat "$d/data" > "$f" 2>/dev/null
        sz=$(wc -c < "$f" 2>/dev/null)
        if [ "${sz:-0}" -gt 0 ]; then
            log "captured $sz bytes from $n"
            seen="$seen $n"
            echo 1 > "$d/data" 2>/dev/null   # release: devcd_data_write -> delete at t+0
        else
            log "empty read from $n; will retry"
            rm -f "$f"
        fi
    done

    sleep 2
done
