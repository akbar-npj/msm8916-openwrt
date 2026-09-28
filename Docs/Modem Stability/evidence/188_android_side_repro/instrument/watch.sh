#!/system/bin/sh
# ---------------------------------------------------------------------------
# Android-side modem-fatal experiment instrument  (Doc 188)   -- v2 2026-09-23
#
# One CSV line per sample. The instrument's LIVENESS is proven by the
# ap_uptime column: if the loop dies, ap_uptime stops advancing while the
# wall clock keeps moving -- a gap that is visible in the log itself.
#
# COLUMNS
#   wall        : AP wall clock (epoch s)
#   ap_uptime   : AP uptime (s)              -- the coverage/liveness column
#   fatal       : count of "Fatal error on the modem" in dmesg
#   modem_rst   : count of MODEM "Brought out of reset" (restart detector)
#   subsys2     : modem subsystem state (ONLINE / OFFLINE)
#   modem_up    : MODEM's own uptime (s) = ap_uptime - t(last modem bring-up)
#                 -- the timebase P-A3 needs (Doc 177's ~902 s always-on clock)
#   himi_conn   : established TCP conns to :8000 (the vendor admin UI poller)
#                 -- Doc 188 s13.3: a browser on the UI DOES poll the modem
#   c0/c1/c2    : cpu0 cpuidle usage (wfi / standalone_pc / pc)
#   c1d/c2d     : cpu0 cpuidle disable flags -- the MANIPULATION VERIFIER
#   rild..td    : userspace daemon liveness (for the A-rungs)
#
# On the first change of `fatal` the full dmesg is dumped once, verbatim.
#
# TRAPS this script is written around:
#   * "Brought out of reset" ALSO matches wcnss -- the modem marker must be
#     qualified with pil-q6v5-mss, or the restart counter double-counts.
#   * dmesg on Android is a RING -- it is non-consuming here, but a dump is
#     taken on the fatal edge so the text is captured before any wrap.
#   * /proc/kmsg is NOT used: it is a CONSUMING reader and would fight logd.
#   * `ps` here is toolbox; it DOES list rild/qmuxd/netmgrd/rmt_storage/
#     time_daemon (verified 2026-09-23). No tooling artifact.
#   * sshd emits "undefined instruction" SIGILL probes into dmesg (OpenSSL
#     CPU-feature detection). Those are NOT an oops -- do not score them.
#   * A DETACHED LAUNCH IS NOT DURABLE: a `nohup ... &` from a shell session
#     died after ~25 s here. Run this from a supervising loop that restarts
#     it, and treat a gap in ap_uptime as "no coverage", never as a negative.
# ---------------------------------------------------------------------------

BB=/system/xbin/busybox
OUT=${OUT:-/data/local/tmp/exp/samples.csv}
INT=${INT:-5}
DUMPDIR=${DUMPDIR:-/data/local/tmp/exp/dumps}
TMP=/data/local/tmp/exp/.dmesg.$$

mkdir -p "$(dirname "$OUT")" "$DUMPDIR"

if [ ! -f "$OUT" ]; then
    echo "wall,ap_uptime,fatal,modem_rst,subsys2,modem_up,himi_conn,c0,c1,c2,c1_dis,c2_dis,rild,qmuxd,netmgrd,rmt_storage,time_daemon" > "$OUT"
fi

last_fatal=-1

while true; do
    wall=$($BB date +%s)
    up=$($BB cut -d' ' -f1 /proc/uptime)

    $BB dmesg > "$TMP" 2>/dev/null
    fatal=$($BB grep -c "Fatal error on the modem" "$TMP")
    modem_rst=$($BB grep -c "pil-q6v5-mss.*modem: Brought out of reset" "$TMP")
    mb=$($BB grep "pil-q6v5-mss.*modem: Brought out of reset" "$TMP" \
         | $BB tail -1 | $BB sed -n 's/^\[ *\([0-9][0-9.]*\)\].*/\1/p')
    if [ -n "$mb" ]; then
        modem_up=$($BB awk -v u="$up" -v m="$mb" 'BEGIN{printf "%.2f", u-m}')
    else
        modem_up=""
    fi

    sub2=$($BB cat /sys/bus/msm_subsys/devices/subsys2/state 2>/dev/null)
    himi=$($BB awk 'NR>1 && $2 ~ /:1F40$/ && $4=="01"' /proc/net/tcp 2>/dev/null | $BB wc -l)

    c0=$($BB cat /sys/devices/system/cpu/cpu0/cpuidle/state0/usage 2>/dev/null)
    c1=$($BB cat /sys/devices/system/cpu/cpu0/cpuidle/state1/usage 2>/dev/null)
    c2=$($BB cat /sys/devices/system/cpu/cpu0/cpuidle/state2/usage 2>/dev/null)
    c1d=$($BB cat /sys/devices/system/cpu/cpu0/cpuidle/state1/disable 2>/dev/null)
    c2d=$($BB cat /sys/devices/system/cpu/cpu0/cpuidle/state2/disable 2>/dev/null)

    pso=$($BB ps 2>/dev/null)
    rild=$(echo "$pso" | $BB grep -c "/system/bin/rild")
    qmuxd=$(echo "$pso" | $BB grep -c "/system/bin/qmuxd")
    netmgrd=$(echo "$pso" | $BB grep -c "/system/bin/netmgrd")
    rmt=$(echo "$pso" | $BB grep -c "/system/bin/rmt_storage")
    td=$(echo "$pso" | $BB grep -c "/system/bin/time_daemon")

    echo "$wall,$up,$fatal,$modem_rst,$sub2,$modem_up,$himi,$c0,$c1,$c2,$c1d,$c2d,$rild,$qmuxd,$netmgrd,$rmt,$td" >> "$OUT"

    if [ "$fatal" != "$last_fatal" ]; then
        if [ "$last_fatal" != "-1" ]; then
            $BB cp "$TMP" "$DUMPDIR/dmesg_fatal_${fatal}_uptime_${up}.txt"
            $BB cp "$OUT" "$DUMPDIR/samples_at_fatal_${fatal}.csv" 2>/dev/null
        fi
        last_fatal=$fatal
    fi

    $BB sleep "$INT"
done
