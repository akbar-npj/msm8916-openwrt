#!/bin/bash
# analyze_hang.sh - read the reset-durable instruments and say WHERE the AP stopped.
#
# WHY
#   Doc 170 §8.11 established that the AP does not panic: it STOPS and the PM8916 PON
#   watchdog (30 s) resets it, so pstore is empty and there is no fault record.  The
#   only evidence that survives is what was written to /overlay BEFORE the stall:
#     * beacon_a.txt (writes, then sync)  -> durable, but blocks if writeback wedges
#     * beacon_b.txt (writes, never sync) -> cannot block, but its tail may be lost
#     * dmesg_roll_<boot_id>.txt          -> the kernel's last lines, per boot
#
#   The two beacons are the discriminator:
#     A and B both stop at ~the same uptime  => a GLOBAL stall (kernel/CPU gone)
#     A stops, B keeps going                 => only the FS/writeback path wedged
#   and their last uptime is when the stall began, ~30 s before the watchdog fires.
#
# usage: analyze_hang.sh [boots_back]      default 1 = the PREVIOUS boot (the one that hung)

set -uo pipefail
DEV=192.168.8.1
BACK=${1:-1}
SSH=(ssh -o BatchMode=yes -o StrictHostKeyChecking=no -o ConnectTimeout=8 "root@$DEV")

echo "=== analyze_hang.sh  $(date -Is)  boots_back=$BACK ==="

# Pull the three instruments plus a little context.
tmp=$(mktemp -d)
for f in beacon_a.txt beacon_b.txt beacon.log ssr_ledger.csv; do
    timeout 30 "${SSH[@]}" "cat /overlay/$f" > "$tmp/$f" 2>/dev/null || echo "(no $f)"
done
timeout 30 "${SSH[@]}" 'ls -t /overlay/dmesg_roll_*.txt' > "$tmp/roll_list" 2>/dev/null || true
# ls -t = newest first; index BACK counts back from the newest.
# (A plain `ls` would sort by boot_id hex, which is uncorrelated with time -- the
#  wrong file would be picked as soon as a second boot's log exists.)
roll=$(sed -n "$((BACK + 1))p" "$tmp/roll_list" | tr -d '\r')
[ -n "$roll" ] && timeout 30 "${SSH[@]}" "cat $roll" > "$tmp/roll.txt" 2>/dev/null

python3 - "$tmp" "$BACK" <<'PY'
import sys, os, re
tmp, back = sys.argv[1], int(sys.argv[2])

def read(n):
    p = os.path.join(tmp, n)
    try:
        return open(p, errors="replace").read().splitlines()
    except OSError:
        return []

def boot_segments(lines):
    """Split a beacon file into [(marker, [entries])] on BOOT markers."""
    segs, cur, mark = [], [], None
    for ln in lines:
        if ln.startswith("BOOT "):
            if mark is not None or cur:
                segs.append((mark, cur))
            mark, cur = ln, []
        elif ln.strip():
            cur.append(ln)
    if mark is not None or cur:
        segs.append((mark, cur))
    return segs

for name in ("beacon_a.txt", "beacon_b.txt"):
    lines = read(name)
    segs = boot_segments(lines)
    print(f"\n--- {name}: {len(segs)} boot segment(s) ---")
    if not segs:
        print("   (empty)")
        continue
    idx = len(segs) - 1 - back
    if idx < 0:
        print(f"   only {len(segs)} segment(s); cannot go back {back}")
        idx = 0
    mark, ent = segs[idx]
    print(f"   segment {idx} of {len(segs)-1}   marker: {mark}")
    print(f"   entries: {len(ent)}")
    for e in ent[-4:]:
        print(f"     {e}")
    if ent:
        try:
            first = float(ent[0].split()[0]); last = float(ent[-1].split()[0])
            print(f"   advanced {first:.2f} -> {last:.2f} s  ({last-first:.1f} s, {len(ent)} samples)")
        except (ValueError, IndexError):
            pass

# The discriminator, computed across the SAME segment index.
def last_uptime(name, back):
    segs = boot_segments(read(name))
    if not segs:
        return None
    idx = max(0, len(segs) - 1 - back)
    ent = segs[idx][1]
    if not ent:
        return None
    try:
        return float(ent[-1].split()[0])
    except (ValueError, IndexError):
        return None

a, b = last_uptime("beacon_a.txt", back), last_uptime("beacon_b.txt", back)
print("\n--- DISCRIMINATOR ---")
if a is None or b is None:
    print("   cannot compute (missing segment)")
else:
    d = b - a
    is_current = (back == 0)
    print(f"   A last = {a:.2f} s   B last = {b:.2f} s   B-A = {d:+.2f} s")
    if is_current:
        print("   (this is the CURRENT boot -- both advancing together just means it is")
        print("    HEALTHY.  The discriminator only means something for a segment that ENDED.)")
    elif abs(d) <= 4:
        print("   => the segment ENDED with A and B together: GLOBAL stall (kernel/CPU gone).")
    elif d > 0:
        print("   => the segment ENDED with A stopped and B still going:")
        print("      the FS/WRITEBACK path wedged BEFORE the global stall.")
    else:
        print("   => A outlasted B (unexpected): B's tail was probably lost on the reset.")

print("\n--- ssr_ledger.csv (last 6) ---")
for ln in read("ssr_ledger.csv")[-6:]:
    print("   " + ln)

print("\n--- dmesg_roll (last 25 lines of the selected boot) ---")
roll = read("roll.txt")
if not roll:
    print("   (no dmesg_roll file pulled)")
else:
    for ln in roll[-25:]:
        print("   " + ln[:160])
PY

echo
echo "raw copies kept in $tmp"
