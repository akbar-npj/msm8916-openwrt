#!/bin/bash
# Wait until boot 2 has passed the pre-registered window (AP 868.9-948.9 s),
# then score ONLY the conditional statistic (Doc 170 PART 36).
#
# The bus answers the MARGINAL question ("did an outage occur"), so that is NOT
# scored.  What is scored: does an outage recur at the SAME MODEM-RELATIVE time?
set -u
cd /home/shaanair/Projects/msm8916-openwrt-clean || exit 1
OUT=scratch/android_capture/boot2
RES="$OUT/RESULT_window_scoring.txt"
WIN_LO=868.9
WIN_HI=948.9

echo "waiting for boot 2 to pass the window (AP $WIN_LO-$WIN_HI s) ..." > "$RES"
for i in $(seq 1 200); do
    UP=$(timeout 10 adb shell 'cut -d. -f1 /proc/uptime' 2>/dev/null | tr -d '\r')
    [ -z "$UP" ] && { sleep 10; continue; }
    if [ "$UP" -ge 1000 ] 2>/dev/null; then
        echo "reached uptime $UP s at $(date -u +%H:%M:%S)" >> "$RES"
        break
    fi
    sleep 10
done

# soak start = the snapshot's first uptime marker (the log_cont pre-history is
# BOOT HISTORY, not soak data -- PART 32 s5)
T0=$(grep -oE '^=== uptime [0-9]+' "$OUT/snapshot_boot2.log" 2>/dev/null | head -1 | grep -oE '[0-9]+')
T0=${T0:-0}

{
  echo
  echo "=================================================================="
  echo "BOOT-2 WINDOW SCORING (Doc 170 PART 36)"
  echo "prediction: onset of a >=30 s bam_dmux silence or a data-call"
  echo "            DISCONNECTED/CONNECTED pair inside AP $WIN_LO-$WIN_HI s"
  echo "soak start used for the gap analysis: AP $T0 s"
  echo "=================================================================="
  echo
  echo "--- 1. bam_dmux silence gaps (the primary instrument) ---"
  python3 scratch/android_capture/analyze_android_gaps.py \
      "$OUT/bam_dmux_boot2.log" --soak-start "$T0" --top 10 2>&1
  echo
  echo "--- 2. ping, BOTH variants (plain can see a route loss, -I cannot) ---"
  for f in ping_plain ping_iface; do
      echo "  [$f]"
      grep -c "packet loss" "$OUT/${f}_boot2.log" 2>/dev/null | sed 's/^/    bursts: /'
      grep -oE "[0-9]+% packet loss" "$OUT/${f}_boot2.log" 2>/dev/null | sort | uniq -c | sed 's/^/    /'
      grep -c "Network is unreachable" "$OUT/${f}_boot2.log" 2>/dev/null | sed 's/^/    ENETUNREACH: /'
  done
  echo
  echo "--- 3. logcat: mobile data-call CONNECTED events (wall + uptime) ---"
  grep -E "ConnectivityChange for mobile: (CONNECTED|DISCONNECTED)" \
      "$OUT/logcat_boot2.log" 2>/dev/null | head -40
  echo
  echo "--- 4. modem lifecycle (the differential control) ---"
  timeout 30 adb shell 'dmesg' 2>/dev/null | grep -cE "Brought out of reset" \
      | sed 's/^/    "Brought out of reset" count: /'
  timeout 30 adb shell 'dmesg' 2>/dev/null | grep -cE "subsys-restart: Resetting" \
      | sed 's/^/    "subsys-restart: Resetting" count: /'
  echo
  echo "--- 5. supervisor health ---"
  tail -5 "$OUT/supervisor.log"
} >> "$RES" 2>&1

echo "SCORING COMPLETE" >> "$RES"
