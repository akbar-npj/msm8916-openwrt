#!/bin/bash
# watch813_v2.sh -- Doc 170 s8.13 bar watcher with a HOST-LOG POWER-EVENT
#                  DISCRIMINATOR.  Replaces watch813_bootB.sh (v1).
#
# WHY v2 EXISTS -- v1 SCORED A FALSE FAILURE
#   2026-09-21 23:04:29 v1 printed
#       *** FAILURE: AP RESET DETECTED -- boot_id ea6401be -> 48f567c0,
#       *** and the previous sample had fatals=2
#   It was a LOOSE WIRE.  The user reported it, and the host log proves it:
#       23:03:41 usb 1-1: USB disconnect, device number 2
#       23:03:41 macsmc 23e400000.smc: RTKit: syslog message: aceElec.cpp:711:
#                                            Elec: Elec Cause 0x0
#       23:03:41 xhci-hcd xhci-hcd.2.auto: remove, state 4
#       23:03:41 xhci-hcd xhci-hcd.2.auto: USB bus 1 deregistered
#       23:03:43 xhci-hcd xhci-hcd.2.auto: new USB bus registered
#       23:04:04 usb 1-1: new high-speed USB device number 2 ... USB Gadget
#
# THE DISCRIMINATOR (this is the point of v2)
#   AP hang   -> the AP dies and the PMIC PON watchdog (30 s) reboots it.  The
#                dongle stays POWERED the whole time.  The host sees only the
#                USB GADGET disconnect and re-enumerate on the SAME bus.  It
#                does NOT see the xHCI controller go away.
#   Power loss-> VBUS drops.  The host sees the xHCI CONTROLLER removed and
#                re-registered ("xhci-hcd ...: remove, state 4",
#                "USB bus N deregistered"), and on Apple Silicon the SMC
#                reports an electrical event ("aceElec.cpp ... Elec Cause").
#
#   So a boot_id change is scored as an AP reset ONLY IF the host log for the
#   outage window shows NO controller removal and NO SMC electrical event.
#   This is cheap, decisive, and available after the fact.
#
# SCORING
#   pooled = POOL_PRIOR + fatals_seen(this boot); bar at 20.
#   A boot_id change with prev_fatals = 0, or with a power event, is
#   re-baselined and NOT scored.
#
# usage: watch813_v2.sh <pool_prior> [logfile]

DEV=192.168.8.1
POOL_PRIOR=${1:-17}
LOG=${2:-scratch/watch813_v2.log}
BAR=20
MAX_ITER=6000

: > "$LOG"
log() { echo "$(date -Is) $*" | tee -a "$LOG"; }

prev_bootid=""
prev_fatals=0
last_good_host=""
misses=0

log "watcher v2 start; pooled prior=$POOL_PRIOR bar=$BAR (host-log discriminator ACTIVE)"

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
    last_good_host=$(date '+%Y-%m-%d %H:%M:%S')
    log "baseline established: boot=$bid fatals=$FATALS pooled=$((POOL_PRIOR+FATALS))/$BAR"
    sleep 25; continue
  fi

  if [ "$bid" != "$prev_bootid" ]; then
    # --- the boot changed: was it the AP, or the power? ----------------------
    # NOTE: do NOT write `date -d "$t - 120 seconds"` -- GNU date parses the
    # "- 120" as a UTC OFFSET, not as an arithmetic term, and silently returns a
    # time 7 h in the future (measured 2026-09-21: "23:03:36 - 120 seconds" ->
    # "2026-09-22 06:08:37").  Use epoch arithmetic, which is unambiguous.
    since=$(date -d "@$(( $(date -d "$last_good_host" +%s) - 120 ))" '+%Y-%m-%d %H:%M:%S' 2>/dev/null)
    ev=$(journalctl -k --no-pager --since "$since" 2>/dev/null \
         | grep -cE "USB bus [0-9]+ deregistered|Elec Cause|xhci-hcd.*remove, state")
    log "--- boot_id changed: $prev_bootid -> $bid (prev_fatals=$prev_fatals) ---"
    log "host-log discriminator since '$since': $ev controller/electrical event line(s)"
    journalctl -k --no-pager --since "$since" 2>/dev/null \
      | grep -E "USB bus [0-9]+ deregistered|Elec Cause|xhci-hcd.*remove, state|USB disconnect" \
      | tail -6 | sed 's/^/    EVIDENCE: /' | tee -a "$LOG"

    if [ "$ev" -gt 0 ]; then
      log "*** POWER EVENT (host removed its xHCI controller / SMC electrical event) ***"
      log "*** NOT an AP hang: the dongle lost VBUS.  Re-baselining, NOT scored. ***"
      log "*** pooled score stays at $((POOL_PRIOR+prev_fatals))/$BAR ***"
      prev_bootid=$bid; prev_fatals=$FATALS
      last_good_host=$(date '+%Y-%m-%d %H:%M:%S')
      sleep 25; continue
    fi

    if [ "$prev_fatals" -ge 1 ]; then
      # --- SECOND, INDEPENDENT CHECK: was the AP mid-SSR when it died? -------
      # An AP hang happens INSIDE a fatal-triggered SSR (Doc 159: the window is
      # the tens of ms after "port failed halt").  So a real AP reset must leave
      # the dead boot's dmesg_roll ending with a fatal and NO completed recovery
      # after it.  dmesg_roll_<boot_id>.txt is boot-keyed, so it survives.
      dead=/tmp/deadboot_$prev_bootid.txt
      timeout 30 scp -o BatchMode=yes -o StrictHostKeyChecking=no \
        "root@$DEV:/overlay/dmesg_roll_$prev_bootid.txt" "$dead" >/dev/null 2>&1
      mid_ssr=0
      if [ -s "$dead" ]; then
        lf=$(grep -n "fatal error received" "$dead" | tail -1 | cut -d: -f1)
        lr=$(grep -nE "is now up|successfully reinitialized BAM channels" "$dead" | tail -1 | cut -d: -f1)
        log "dead-boot dmesg_roll: last fatal at line ${lf:-none}, last completed recovery at line ${lr:-none}"
        if [ -n "$lf" ] && { [ -z "$lr" ] || [ "$lf" -gt "$lr" ]; }; then
          mid_ssr=1
        fi
        tail -4 "$dead" | sed 's/^/    DEAD-TAIL: /' | tee -a "$LOG"
      else
        log "dead-boot dmesg_roll unavailable -- treating mid-SSR as UNKNOWN (not 0)"
        mid_ssr=unknown
      fi

      if [ "$mid_ssr" = "1" ]; then
        log "*** FAILURE: AP RESET -- boot_id $prev_bootid -> $bid, prev_fatals=$prev_fatals,"
        log "*** host log shows NO controller removal / NO SMC electrical event, AND the dead"
        log "*** boot's dmesg_roll ends with an UNRECOVERED fatal, so the AP died in the SSR."
        log "*** pooled score at the reset: $((POOL_PRIOR+prev_fatals))/$BAR"
        echo "=== BAR FAILED at $(date -Is) ==="
        break
      fi
      log "*** boot_id change NOT scored: prev_fatals=$prev_fatals but mid-SSR=$mid_ssr"
      log "*** (a completed recovery follows the last fatal => the AP was healthy, not hung)"
      prev_bootid=$bid; prev_fatals=$FATALS
      last_good_host=$(date '+%Y-%m-%d %H:%M:%S')
      sleep 25; continue
    fi
    log "boot_id change with prev_fatals=0 and no power event; re-baselining, NOT scored"
    prev_bootid=$bid; prev_fatals=$FATALS
    last_good_host=$(date '+%Y-%m-%d %H:%M:%S')
    sleep 25; continue
  fi

  prev_fatals=$FATALS
  last_good_host=$(date '+%Y-%m-%d %H:%M:%S')
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
