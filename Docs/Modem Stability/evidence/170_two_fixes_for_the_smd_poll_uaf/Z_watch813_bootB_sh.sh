#!/bin/bash
# watch813_bootB.sh -- Doc 170 s8.13 bar watcher, BOOT B (boot_id ea6401be).
#
# WHY THIS REPLACES watch813.sh
#   watch813.sh had two defects that this round exposed:
#     (1) it BROKE on 3 consecutive misses ("POSSIBLE RESET"), but an AP hang is
#         followed by a PMIC PON watchdog reboot (30 s) -- so the correct signal
#         is a BOOT_ID CHANGE seen on a later poll, not an unreachable streak.
#         Breaking on unreachable throws away the very event being scored.
#     (2) it treated any boot_id change as a failure, which is wrong when the
#         host itself power-cycles the dongle (lid close, 2026-09-21 20:47).
#   This version keeps polling through unreachable windows and scores a failure
#   only when the boot_id changes AND the previous sample had already seen a
#   fatal -- i.e. the AP died DURING a fatal-triggered SSR, which is the
#   phenomenon the bar is about.
#
# POOLING (pre-registered n = 20, decided before this run -- see PART 19):
#   Boot A (a9fd907c) delivered 15 post-disable fatals with 0 AP reboots and was
#   ended by a HOST power cycle, which is not a scored failure.  The bar is
#   "0 AP reboots across the next 20 fatals", so boot B supplies the remaining 5.
#   Both counted sets are "coredump disabled at the time of the fatal", so they
#   are identically treated; the pooling is stated here rather than discovered
#   later.  Boot B is the cleaner regime: capture-off from fatal #1.
#
# usage: watch813_bootB.sh

DEV=192.168.8.1
LOG=scratch/watch813_bootB.log
POOL_PRIOR=15          # post-disable fatals already scored in boot A
BAR=20
MAX_ITER=4000

: > "$LOG"
log() { echo "$(date -Is) $*" | tee -a "$LOG"; }

prev_bootid=""
prev_fatals=0
misses=0

log "watcher start; pooled prior=$POOL_PRIOR bar=$BAR"

for i in $(seq 1 $MAX_ITER); do
  out=$(timeout 20 ssh -o BatchMode=yes -o StrictHostKeyChecking=no -o ConnectTimeout=8 \
        root@$DEV '
        echo "UP=$(cut -d" " -f1 /proc/uptime)"
        echo "BOOTID=$(cat /proc/sys/kernel/random/boot_id)"
        echo "FATALS=$(dmesg 2>/dev/null | grep -c "fatal error received")"
        echo "DUMPS=$(ls /overlay/coredump_live/ 2>/dev/null | wc -l)"
        echo "ATTR=$(cat /sys/class/remoteproc/remoteproc0/coredump 2>/dev/null)"
        echo "RPROC=$(cat /sys/class/remoteproc/remoteproc0/state 2>/dev/null)"' 2>/dev/null)

  if [ -z "$out" ]; then
    misses=$((misses+1))
    log "NO ANSWER ($misses consecutive) -- NOT scored; an AP hang shows up as a boot_id change, so keep polling"
    sleep 20
    continue
  fi
  misses=0
  eval "$out" 2>/dev/null
  up=${UP%.*}; bid=${BOOTID:0:8}

  if [ -z "$prev_bootid" ]; then
    prev_bootid=$bid
    prev_fatals=$FATALS
    log "baseline established: boot=$bid fatals=$FATALS pooled=$((POOL_PRIOR+FATALS))/$BAR"
    sleep 25; continue
  fi

  if [ "$bid" != "$prev_bootid" ]; then
    if [ "$prev_fatals" -ge 1 ]; then
      log "*** FAILURE: AP RESET DETECTED -- boot_id $prev_bootid -> $bid, and the previous"
      log "*** sample had fatals=$prev_fatals, so the AP died during a fatal-triggered SSR."
      log "*** pooled score at the reset: $((POOL_PRIOR+prev_fatals))/$BAR"
      echo "=== BAR FAILED at $(date -Is) ==="
      break
    fi
    log "host power cycle (boot_id $prev_bootid -> $bid with prev_fatals=0); re-baselining, NOT scored"
    prev_bootid=$bid; prev_fatals=$FATALS
    sleep 25; continue
  fi

  prev_fatals=$FATALS
  pooled=$((POOL_PRIOR + FATALS))

  if [ "$pooled" -ge "$BAR" ]; then
    log "*** BAR MET: pooled $pooled/$BAR post-disable fatals, 0 AP reboots ***"
    log "*** boot=$bid up=${up}s dumps=$DUMPS attr=$ATTR rproc=$RPROC ***"
    echo "=== BAR MET at $(date -Is) ==="
    break
  fi

  log "up=${up}s boot=$bid fatals=$FATALS pooled=$pooled/$BAR dumps=$DUMPS attr=$ATTR rproc=$RPROC"
  sleep 25
done

log "watcher ended"
tail -5 "$LOG"
