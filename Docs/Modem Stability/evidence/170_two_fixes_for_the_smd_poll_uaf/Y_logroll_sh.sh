#!/bin/sh
# logroll.sh -- DURABLE userspace-log + PM-knob recorder.
#
# WHY: logread is a ~15 min RING BUFFER, so a trigger that fires before that
# window is unrecoverable (Doc 170 s8.15.3 s5 claimed "no external trigger" from
# dmesg + the host log alone -- but /usr/sbin/modem-bearer-watchdog writes the
# bam-dmux runtime-PM knobs EVERY 10 s WITH NO LOG LINE, so neither check could
# have seen it).  This makes both the log and the knob state durable.
BID=$(cut -c1-8 /proc/sys/kernel/random/boot_id)
OUT=/overlay/logroll_$BID.log
echo "=== BOOT $BID uptime=$(cut -d" " -f1 /proc/uptime) $(date -u +%FT%TZ) ===" >> $OUT
D=/sys/devices/platform/soc@0/4080000.remoteproc/4080000.remoteproc:bam-dmux/power
last=""
# follow the log (minus dropbear noise) and sample the PM knobs once a second
logread -f 2>/dev/null | grep -vE "dropbear|authpriv" | while IFS= read -r l; do
  echo "$l" >> $OUT
  sync
done &
i=0
while [ $i -lt 86400 ]; do
  c=$(cat $D/control 2>/dev/null); a=$(cat $D/autosuspend_delay_ms 2>/dev/null); r=$(cat $D/runtime_status 2>/dev/null)
  cur="$c/$a/$r"
  if [ "$cur" != "$last" ]; then
    echo "$(date -u +%FT%TZ) uptime=$(cut -d" " -f1 /proc/uptime) PMKNOB $cur (was $last)" >> $OUT
    sync
    last="$cur"
  fi
  i=$((i+1))
  sleep 1
done
