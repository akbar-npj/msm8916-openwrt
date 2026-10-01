#!/bin/bash
# watch_v7_event.sh — monitor the v7 ring-buffer event run.
# Catches EITHER branch:
#   FATAL  -> kernel auto-writes the ramdump; the armed reader captures it.
#   WEDGE  -> no fatal; at the fallback time we issue `restart` to force a dump.
# Then waits for the dump to reach its full 85 443 284 B and dumps dmesg.
cd /home/shaanair/Projects/msm8916-openwrt-clean || exit 1
LOG=scratch/android_dump/v7_event_watch.log
DMESG=scratch/android_dump/v7_event_dmesg.txt
: > "$LOG"
FATAL_BASE=45
BOOT=64471.502727
FALLBACK=65431.5          # boot + ~960 s
DUMP=/data/local/tmp/v7_cap2.elf
FULL=85443284

echo "watcher start $(date)" >> "$LOG"
echo "boot=$BOOT fatal_base=$FATAL_BASE fallback=$FALLBACK" >> "$LOG"

for i in $(seq 1 240); do
  LINE=$(adb shell "U=\$(cut -d' ' -f1 /proc/uptime); F=\$(dmesg | grep -c 'modem subsystem failure reason'); R=\$(cat /sys/class/net/rmnet1/statistics/rx_bytes); T=\$(cat /sys/class/net/rmnet1/statistics/tx_bytes); S=\$(stat -c %s $DUMP 2>/dev/null); B=\$(dmesg | grep -c 'Brought out of reset'); echo \"\$U \$F \$R \$T \$S \$B\"" 2>/dev/null | tr -d '\r')
  UP=$(echo "$LINE" | awk '{print $1}')
  if [ -z "$UP" ]; then echo "$(date +%H:%M:%S) <adb-fail>" >> "$LOG"; sleep 5; continue; fi
  TS=$(date +%H:%M:%S)
  echo "$TS $LINE" >> "$LOG"
  FAT=$(echo "$LINE" | awk '{print $2}')
  SZ=$(echo "$LINE" | awk '{print $5}')
  if [ "$FAT" -gt "$FATAL_BASE" ] 2>/dev/null; then
    echo "=== FATAL DETECTED uptime=$UP fatal=$FAT ===" >> "$LOG"
    break
  fi
  if [ "$SZ" -gt 0 ] 2>/dev/null; then
    echo "=== DUMP GROWING uptime=$UP size=$SZ ===" >> "$LOG"
    break
  fi
  if awk "BEGIN{exit !($UP > $FALLBACK)}"; then
    echo "=== FALLBACK: no fatal by uptime=$UP (boot+$(awk "BEGIN{printf \"%.1f\", $UP-$BOOT}")), forcing dump via restart ===" >> "$LOG"
    adb shell 'echo restart > /sys/kernel/debug/msm_subsys/modem' 2>/dev/null
    break
  fi
  sleep 10
done

adb shell 'dmesg | tail -140' > "$DMESG" 2>/dev/null

echo "--- waiting for dump ---" >> "$LOG"
for j in $(seq 1 90); do
  SZ=$(adb shell "stat -c %s $DUMP 2>/dev/null" 2>/dev/null | tr -d '\r')
  echo "$(date +%H:%M:%S) dump size=$SZ" >> "$LOG"
  [ "$SZ" = "$FULL" ] && { echo "=== DUMP COMPLETE ===" >> "$LOG"; break; }
  sleep 5
done
echo "watcher done $(date)" >> "$LOG"
