#!/system/bin/sh
# ---------------------------------------------------------------------------
# /data/local/tmp/exp/supervisor.sh  --  v1  2026-09-23
#
# PURPOSE: keep the A0 sampler (watch.sh) alive. The sampler is normally a child
# of adbd, so an adbd restart (USB re-enumerate, `setprop service.adb.root`,
# a host-side adb hiccup) kills it and silently ends the window. This supervisor
# is launched from an SSH session, so it is NOT a child of adbd and survives one.
#
# DESIGN: it is a watchdog OF the watchdog. It never touches a healthy sampler.
# It acts ONLY when samples.csv has gone stale. The file's mtime is the right
# signal because it is immune to the toolbox-`ps` cmdline limitation (that `ps`
# prints the sampler as bare `sh`, so a process-name check is unreliable).
#
# TRAPS written around:
#   * On this busybox `pkill -l` means "LIST SIGNAL NAMES", not a dry run --
#     there is no dry-run flag. The -f pattern below was verified against
#     /proc/<pid>/cmdline, which reads `sh /data/local/tmp/exp/watch.sh`.
#   * The effective sampler interval is ~5.24 s (sleep 5 + the per-iteration
#     dmesg cost), so STALE must be far above it: 90 s = ~17 missed samples.
#   * A restart must NOT run concurrently with a live sampler or the two
#     interleave rows into samples.csv. Hence pkill + a 2 s settle first.
#   * Never write to samples.csv from here -- only append to supervisor.log.
# ---------------------------------------------------------------------------
BB=/system/xbin/busybox
EXP=/data/local/tmp/exp
OUT=$EXP/samples.csv
LOG=$EXP/supervisor.log
STALE=90
INTERVAL=20

say() { echo "[$($BB date -u +%Y-%m-%dT%H:%M:%SZ)] up=$($BB cut -d' ' -f1 /proc/uptime) $*" >> "$LOG"; }

say "supervisor start (pid $$) stale=${STALE}s interval=${INTERVAL}s"

while true; do
    now=$($BB date +%s)
    m=$($BB stat -c %Y "$OUT" 2>/dev/null)
    if [ -z "$m" ]; then
        say "samples.csv unreadable -- restarting sampler"
        $BB pkill -f '/data/local/tmp/exp/watch.sh' 2>/dev/null
        $BB sleep 2
        $BB nohup /system/bin/sh $EXP/watch.sh >> "$LOG" 2>&1 &
    else
        age=$((now - m))
        if [ "$age" -gt "$STALE" ]; then
            say "SAMPLER STALE (${age}s > ${STALE}s) -- restarting"
            $BB pkill -f '/data/local/tmp/exp/watch.sh' 2>/dev/null
            $BB sleep 2
            $BB nohup /system/bin/sh $EXP/watch.sh >> "$LOG" 2>&1 &
            say "sampler restart issued"
        fi
    fi
    $BB sleep "$INTERVAL"
done
