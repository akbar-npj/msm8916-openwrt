#!/bin/bash
# Doc 170 s8.15 passive pre-registration watcher.
#
# Waits for the next fatal (>= 5 this boot) and captures, at that moment, both
# halves of the pre-registration:
#   P1  the next a2_power.c:2949 will have a "lost edge" resync within 100 ms
#       in front of it.
#   P2  keep counting resyncs that have NO fatal within 100 ms after them.
#
# So the capture records EVERY "lost edge" line with its timestamp (so the gap
# to the newest fatal can be computed) and the running pc_resync_count (so the
# denominator keeps accumulating), not just the fatal.
#
# Usage: bash scratch/watch_resync.sh [target_fatal_count]   (default 5)
# Log:   /tmp/watch_resync.log
DEV=192.168.8.1
TARGET=${1:-5}
LOG=/tmp/watch_resync.log
: > "$LOG"
echo "$(date -Is) watcher start, target fatals=$TARGET" >> "$LOG"

for i in $(seq 1 120); do
  n=$(timeout 20 ssh -o BatchMode=yes -o ConnectTimeout=8 root@$DEV \
        'dmesg 2>/dev/null | grep -c "fatal error received"' 2>/dev/null | tr -d " \r")
  if [ -n "$n" ] && [ "$n" -ge "$TARGET" ] 2>/dev/null; then
    echo "=== FATAL #$n DETECTED at $(date -Is) ==="
    timeout 40 ssh -o BatchMode=yes -o ConnectTimeout=8 root@$DEV 'sh -s' <<'EOS' 2>&1
echo "uptime: $(cut -d' ' -f1 /proc/uptime)"
echo "boot_id: $(cat /proc/sys/kernel/random/boot_id)"
echo "rproc: $(cat /sys/class/remoteproc/remoteproc0/state)"
echo "attr: $(cat /sys/class/remoteproc/remoteproc0/coredump)"
echo "dumps: $(ls /overlay/coredump_live/ 2>/dev/null | wc -l)"
echo "=== ALL fatal lines ==="
dmesg | grep "fatal error received"
echo "=== ALL 'lost edge' resync lines (P1/P2 denominator) ==="
dmesg | grep "lost edge"
echo "lost-edge count: $(dmesg | grep -c 'lost edge')"
echo "=== boot-wide counts ==="
echo "port failed halt : $(dmesg | grep -c 'port failed halt')"
echo "MBA booted       : $(dmesg | grep -c 'MBA booted')"
echo "trace 01 pairs   : $(dmesg | grep -c 'q6v5-trace: 01 disable_qchannel mdm')"
echo "trace 10 pairs   : $(dmesg | grep -c 'q6v5-trace: 10 ')"
echo "=== rx_telemetry (pc_* only) ==="
T=$(find /sys/devices -name rx_telemetry 2>/dev/null | head -1)
grep -E "pc_|resync|timeout|rx_tearing_down" "$T" 2>/dev/null
echo "=== ledger tail 3 ==="
tail -3 /overlay/ssr_ledger.csv
echo "=== window: newest fatal +/- 1.2 s ==="
FT=$(dmesg | grep "fatal error received" | tail -1 | sed 's/^\[ *//; s/\].*//')
echo "newest fatal at: $FT"
dmesg | awk -v T="$FT" '{ if (match($0, /\[ *[0-9]+\.[0-9]+\]/)) { s=substr($0,RSTART+1,RLENGTH-2); gsub(/ /,"",s); t=s+0; if (t>=T-1.2 && t<=T+0.35) print } }'
EOS
    break
  fi
  echo "$(date -Is) fatals=$n (target $TARGET)" >> "$LOG"
  sleep 20
done
echo "=== watcher ended $(date -Is) ==="
