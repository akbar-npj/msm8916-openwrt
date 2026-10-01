#!/bin/bash
OUT="$1"; DUR="${2:-1800}"
: > "$OUT"
END=$(( $(date +%s) + DUR ))
while [ "$(date +%s)" -lt "$END" ]; do
  TS=$(date +%H:%M:%S)
  R=$(ssh -o BatchMode=yes -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o LogLevel=ERROR -o ConnectTimeout=8 root@192.168.8.1 '
    U=$(cut -d" " -f1 /proc/uptime)
    F=$(dmesg | grep -c "fatal error received")
    L=$(dmesg | grep "fatal error received" | tail -1 | sed "s/.*: //" | cut -c1-40)
    S=$(mmcli -m any 2>/dev/null | grep -E "state:|access tech:" | sed "s/.*: //" | tr "\n" "/")
    echo "up=${U} fatal=${F} mm=${S} last=${L}"' 2>/dev/null)
  echo "$TS $R" >> "$OUT"
  sleep 15
done
echo "WATCHER DONE" >> "$OUT"
