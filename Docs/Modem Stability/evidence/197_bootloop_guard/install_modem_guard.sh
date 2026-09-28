#!/bin/bash
# install_modem_guard.sh — install / refresh the bootloop safety net on the device.
#
# Run ON THE BUILD HOST:
#   bash "Docs/Modem Stability/evidence/197_bootloop_guard/install_modem_guard.sh"
#
# It pushes the two guard files, checks the stock backup is present and correct,
# and enables the service. Nothing else on the device is touched and no reboot is
# performed — the guard takes effect on the next boot.
set -uo pipefail

DEV="${DEV:-192.168.8.1}"
HERE="$(cd "$(dirname "$0")" && pwd)"
SSH=(ssh -o BatchMode=yes -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o LogLevel=ERROR -o ConnectTimeout=8 "root@$DEV")
dev() { timeout 60 "${SSH[@]}" "$@" 2>&1; }

STOCK_DIR=/overlay/fwbackup/hmu05_stock
STOCK_MDT_MD5=1a6f9507e03d4ddbbf1977af81ecdbd7

echo "==> checking the device is reachable"
dev 'cat /proc/uptime' >/dev/null || { echo "ERROR: $DEV not reachable"; exit 1; }

echo "==> pushing guard files"
cat "$HERE/modem_guard.sh" | dev 'cat > /root/modem_guard.sh' >/dev/null
cat "$HERE/modem-guard.init" | dev 'cat > /etc/init.d/modem-guard' >/dev/null
dev 'chmod 0755 /root/modem_guard.sh /etc/init.d/modem-guard; mkdir -p /overlay/modem_guard; echo installed'

echo "==> verifying the stock HMU05 backup"
got="$(dev "md5sum $STOCK_DIR/modem.mdt 2>/dev/null" | awk '{print $1}')"
if [ "$got" != "$STOCK_MDT_MD5" ]; then
    echo "ERROR: $STOCK_DIR/modem.mdt md5=$got, expected $STOCK_MDT_MD5"
    echo "       the guard would refuse to revert; fix the backup first."
    exit 1
fi
echo "    stock modem.mdt ok ($got)"

echo "==> enabling the service"
# remove any stale S*/K* links first (e.g. an older K99) so only the current
# START/STOP numbering exists
dev 'rm -f /etc/rc.d/S*modem-guard /etc/rc.d/K*modem-guard; /etc/init.d/modem-guard enable; ls -l /etc/rc.d/ | grep modem-guard'

echo "==> guard status"
dev '/root/modem_guard.sh status'

echo
echo "==> done. The guard is armed for the next boot."
echo "    disable with : ssh root@$DEV '/etc/init.d/modem-guard disable'"
echo "    force revert : ssh root@$DEV '/root/modem_guard.sh revert-now'"
echo "    reset counter: ssh root@$DEV '/root/modem_guard.sh reset'"
