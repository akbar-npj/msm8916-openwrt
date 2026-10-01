#!/bin/bash
# watch_v7_traffic.sh — v7 ring, TRAFFIC regime (the 168x-gap discriminator).
# Same instrument as the idle event run; the ONLY change is sustained ping traffic.
# If the ring's count drops to ~4 => the gap is REGIME. If it stays ~674 => v6's
# counter was unreliable.
#
# ⚠ The reader and the traffic loop are armed over SSH, NOT adb: `adb shell` kills
# the whole process group when the shell exits, so a detached `( ... & )` dies.
# (Observed in-session: the first attempt armed neither.) sshd reparents it to init.
cd /home/shaanair/Projects/msm8916-openwrt-clean || exit 1
LOG=scratch/android_dump/v7_traffic_watch.log
DMESG=scratch/android_dump/v7_traffic_dmesg.txt
: > "$LOG"
DEV=root@192.168.100.1
SSH="ssh -o ConnectTimeout=8 -o StrictHostKeyChecking=no -o BatchMode=yes"
BOOT=67650.588566
FATAL_BASE=48
FALLBACK=68650.6
DUMP=/data/local/tmp/v7_cap3.elf
FULL=85443284
IF=rmnet6
LIST='ps -A -o pid,args 2>/dev/null | grep "[c]at /dev/ramdump_modem"'

echo "watcher start $(date) boot=$BOOT fatal_base=$FATAL_BASE if=$IF" >> "$LOG"

# --- 1) arm exactly ONE reader (device-local; ssh-detached) ---
N=$($SSH "$DEV" "$LIST | wc -l" 2>/dev/null | tr -d '\r')
if [ "${N:-0}" != "0" ]; then
  echo "!! $N reader(s) already armed" >> "$LOG"; $SSH "$DEV" "$LIST" >> "$LOG" 2>&1; exit 1
fi
$SSH "$DEV" "rm -f $DUMP; (cat /dev/ramdump_modem > $DUMP 2>/dev/null &); sleep 1; $LIST" >> "$LOG" 2>&1
$SSH "$DEV" "ls -l $DUMP" >> "$LOG" 2>&1

# --- 2) start sustained traffic (device-side loop; ssh-detached) ---
$SSH "$DEV" "rm -f /data/local/tmp/v7_ping.log; (while true; do ping -c 1 -W 2 8.8.8.8 >/dev/null 2>&1; echo \$? >> /data/local/tmp/v7_ping.log; sleep 4; done &)"
sleep 12
echo "traffic: $(  $SSH "$DEV" 'ps -A -o pid,args 2>/dev/null | grep "[p]ing" | head -1; echo -n "pinglog="; wc -l < /data/local/tmp/v7_ping.log 2>/dev/null' | tr -d '\r' )" >> "$LOG"

FAILS=0
for i in $(seq 1 220); do
  LINE=$($SSH "$DEV" "U=\$(cut -d' ' -f1 /proc/uptime); F=\$(dmesg | grep -c 'modem subsystem failure reason'); R=\$(cat /sys/class/net/$IF/statistics/rx_bytes 2>/dev/null); T=\$(cat /sys/class/net/$IF/statistics/tx_bytes 2>/dev/null); S=\$(stat -c %s $DUMP 2>/dev/null); P=\$(tail -1 /data/local/tmp/v7_ping.log 2>/dev/null); echo \"\$U \$F \$R \$T \$S \$P\"" 2>/dev/null | tr -d '\r')
  UP=$(echo "$LINE" | awk '{print $1}')
  if [ -z "$UP" ]; then echo "$(date +%H:%M:%S) <ssh-fail>" >> "$LOG"; sleep 5; continue; fi
  echo "$(date +%H:%M:%S) $LINE" >> "$LOG"
  FAT=$(echo "$LINE" | awk '{print $2}')
  SZ=$(echo "$LINE" | awk '{print $5}')
  P=$(echo "$LINE" | awk '{print $6}')
  if [ "$FAT" -gt "$FATAL_BASE" ] 2>/dev/null; then
    echo "=== FATAL uptime=$UP fatal=$FAT (boot+$(awk "BEGIN{printf \"%.2f\", $UP-$BOOT}")) ===" >> "$LOG"; break; fi
  if [ "$SZ" -gt 0 ] 2>/dev/null; then
    echo "=== DUMP GROWING uptime=$UP size=$SZ ===" >> "$LOG"; break; fi
  if [ "$P" != "0" ] && [ -n "$P" ]; then FAILS=$((FAILS+1)); else FAILS=0; fi
  if [ "$FAILS" -ge 3 ]; then
    echo "=== WEDGE (ping failed ${FAILS}x) uptime=$UP (boot+$(awk "BEGIN{printf \"%.2f\", $UP-$BOOT}")) -> restart to dump ===" >> "$LOG"
    $SSH "$DEV" "echo restart > /sys/kernel/debug/msm_subsys/modem" 2>/dev/null; break; fi
  if awk "BEGIN{exit !($UP > $FALLBACK)}"; then
    echo "=== FALLBACK uptime=$UP -> restart to dump ===" >> "$LOG"
    $SSH "$DEV" "echo restart > /sys/kernel/debug/msm_subsys/modem" 2>/dev/null; break; fi
  sleep 10
done

$SSH "$DEV" 'dmesg | tail -140' > "$DMESG" 2>/dev/null
echo "--- waiting for dump ---" >> "$LOG"
for j in $(seq 1 90); do
  SZ=$($SSH "$DEV" "stat -c %s $DUMP 2>/dev/null" 2>/dev/null | tr -d '\r')
  echo "$(date +%H:%M:%S) dump=$SZ" >> "$LOG"
  [ "$SZ" = "$FULL" ] && { echo "=== DUMP COMPLETE ===" >> "$LOG"; break; }
  sleep 5
done
echo "watcher done $(date)" >> "$LOG"
