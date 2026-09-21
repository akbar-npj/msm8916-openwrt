#!/bin/sh
# dmesg_roll.sh - persist the last kernel messages to /overlay, ONE FILE PER BOOT.
#
# WHY (Doc 170 section 8.10 follow-up)
#   The AP hangs and the PMIC PON watchdog (30 s, /sys/class/watchdog/watchdog0)
#   resets it.  pstore was EMPTY after the 17:03 reset, so the kernel log up to the
#   stall was lost.  The device's own soak was supposed to copy /dev/kmsg to /overlay
#   but its stream file is stale, so nothing currently persists the kernel log.
#
#   This is deliberately the DUMBEST possible capture: rewrite the tail of `dmesg`
#   (the ring buffer, everything since boot) to a file every INTERVAL seconds, with a
#   sync.  No /dev/kmsg, no pipe that can leak, no filter that can lag.
#
# WHY ONE FILE PER BOOT
#   A rolling file is overwritten within INTERVAL seconds of the next boot, so the
#   pre-hang tail would be destroyed by the recovery it is meant to explain -- the
#   exact mistake beacon.sh made (Doc 170 section 8.10).  Naming the file by
#   /proc/sys/kernel/random/boot_id keeps the PREVIOUS boot's file intact, so after a
#   reset the tail that ends at the stall is still readable.
#
# usage: dmesg_roll.sh [outdir] [interval_s] [lines]     default /overlay 5 250

OUT=${1:-/overlay}
INTERVAL=${2:-5}
LINES=${3:-250}

BOOTID=$(cut -c1-8 /proc/sys/kernel/random/boot_id 2>/dev/null)
[ -n "$BOOTID" ] || BOOTID=$(cut -d' ' -f1 /proc/uptime | tr -d '.')
F="$OUT/dmesg_roll_$BOOTID.txt"

# keep only the four most recent per-boot files
ls -t "$OUT"/dmesg_roll_*.txt 2>/dev/null | tail -n +5 | while IFS= read -r old; do
	rm -f "$old"
done

echo "[$(cut -d' ' -f1 /proc/uptime)s] dmesg_roll start boot_id=$BOOTID -> $F (every ${INTERVAL}s, last ${LINES} lines)" \
	>> "$OUT/dmesg_roll.log"

while true; do
	if dmesg 2>/dev/null | tail -n "$LINES" > "$F.tmp"; then
		mv -f "$F.tmp" "$F"
	fi
	sync
	sleep "$INTERVAL"
done
