#!/bin/sh
# Capture modem (MPSS) coredumps as they appear.
#
# devcoredump discards the buffer ~5 minutes after the crash, so this must poll
# and stream continuously.  Run it under a background ssh from the host; the
# device has no nohup.
#
# Usage:  /tmp/coredump_watch.sh [outdir]     (default /overlay/coredump_live)

OUT="${1:-/overlay/coredump_live}"
mkdir -p "$OUT"

echo "# coredump_watch start uptime=$(cut -d' ' -f1 /proc/uptime) out=$OUT"
echo "# armed=$(cat /sys/class/remoteproc/remoteproc0/coredump 2>/dev/null)"

while :; do
    # keep it armed (the setting does not survive a reboot)
    cur=$(cat /sys/class/remoteproc/remoteproc0/coredump 2>/dev/null)
    [ "$cur" = "enabled" ] || echo enabled > /sys/class/remoteproc/remoteproc0/coredump 2>/dev/null

    for d in /sys/class/devcoredump/devcd*/; do
        [ -d "$d" ] || continue
        n=${d%/}; n=${n##*/}
        [ -s "$d/data" ] || continue
        up=$(cut -d' ' -f1 /proc/uptime)
        f="$OUT/modem_coredump_up${up}.elf"
        if [ ! -e "$f" ]; then
            echo "# dumping $n -> $f (uptime=$up)"
            cat "$d/data" > "$f" 2>/dev/null
            echo "# wrote $(wc -c < "$f" 2>/dev/null) bytes"
        fi
        echo 1 > "$d/disabled" 2>/dev/null
    done
    sleep 2
done
