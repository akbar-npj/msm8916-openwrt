#!/bin/bash
OUT="$1"; DUR="${2:-2400}"
: > "$OUT"
END=$(( $(date +%s) + DUR ))
while [ "$(date +%s)" -lt "$END" ]; do
  TS=$(date +%H:%M:%S)
  R=$(ssh -o BatchMode=yes -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o LogLevel=ERROR -o ConnectTimeout=8 root@192.168.8.1 '
    T=$(cut -d" " -f1 /proc/uptime)
    F=$(dmesg | grep -c "fatal error received")
    LAST=$(dmesg | grep "fatal error received" | tail -1 | cut -c1-55)
    MM=$(mmcli -m any 2>/dev/null | tr -d " " | grep -E "^state:|^access tech:|^registration:" | tr "\n" ",")
    echo "$T fatal=$F $MM $LAST"' 2>/dev/null)
  echo "$TS $R" >> "$OUT"
  sleep 25
done
echo "WATCHER DONE" >> "$OUT"
