#!/bin/bash
# Doc 170 s8.15.1 -- storm sampler.
#
# The resync rate is non-stationary: 1 resync in the first 3478 s of this boot,
# then 5 in 145 s from 4079.094133.  The fatal watcher only fires on a FATAL, so
# if the storm just stops (P3-falsifier) nothing would record its shape.
#
# This samples the counters every 60 s so the storm is characterised whether or
# not a fatal follows.  It writes to the DEVICE's /overlay (survives a reboot)
# AND to a host file.
#
# Usage: bash scratch/storm_sampler.sh [minutes]   (default 60)
DEV=192.168.8.1
MIN=${1:-60}
HLOG=/tmp/storm_sampler.log
: > "$HLOG"
echo "$(date -Is) storm sampler start, ${MIN} min" | tee -a "$HLOG"

for i in $(seq 1 "$MIN"); do
  line=$(timeout 20 ssh -o BatchMode=yes -o ConnectTimeout=8 root@$DEV \
    'T=/sys/devices/platform/soc@0/4080000.remoteproc/4080000.remoteproc:bam-dmux/rx_telemetry
     up=$(cut -d" " -f1 /proc/uptime)
     f=$(dmesg 2>/dev/null | grep -c "fatal error received")
     le=$(dmesg 2>/dev/null | grep -c "lost edge")
     r=$(grep "^pc_resync_count:" "$T" | tr -d " " | cut -d: -f2)
     t=$(grep "^pc_timeout_count:" "$T" | tr -d " " | cut -d: -f2)
     q=$(grep "^pc_quiesce_ms:" "$T" | tr -d " " | cut -d: -f2)
     ps=$(grep "^pc_state:" "$T" | tr -d " " | cut -d: -f2)
     sa=$(grep "^pm_suspend_attempts:" "$T" | tr -d " " | cut -d: -f2)
     sc=$(grep "^pm_suspend_completions:" "$T" | tr -d " " | cut -d: -f2)
     echo "up=$up fatals=$f resync=$r lostedge=$le pctimeout=$t quiesce_ms=$q pc_state=$ps susp=$sa/$sc"' 2>/dev/null)
  if [ -z "$line" ]; then
    echo "$(date -Is) NO ANSWER" | tee -a "$HLOG"
  else
    echo "$(date -Is) $line" | tee -a "$HLOG"
    echo "$line" | timeout 15 ssh -o BatchMode=yes -o ConnectTimeout=8 root@$DEV \
      'read l; echo "$(date -Is) $l" >> /overlay/resync_storm.log' 2>/dev/null
  fi
  sleep 55
done
echo "$(date -Is) storm sampler ended" | tee -a "$HLOG"
