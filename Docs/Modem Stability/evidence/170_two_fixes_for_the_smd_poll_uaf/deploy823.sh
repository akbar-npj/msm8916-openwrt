#!/bin/bash
# deploy823.sh -- install the patch-823 rpmsg_wwan_ctrl.ko on the device.
#
# Patch 823 removes the SMD poll registration made by rpmsg_wwan_ctrl_tx_poll(),
# which is the reachable instance of the channel->fblockread_event use-after-free
# (Doc 169 / Doc 170).  It is a MODULE, so this is a copy + reboot -- no flash.
#
# Run ON THE BUILD HOST.
#
# Usage: bash scratch/deploy823.sh [--revert]

set -uo pipefail

DEV=192.168.8.1
KO=openwrt/build_dir/target-aarch64_generic_musl/linux-msm89xx_msm8916/linux-6.12.94/drivers/net/wwan/rpmsg_wwan_ctrl.ko
DEST=/lib/modules/6.12.94/rpmsg_wwan_ctrl.ko
BAK=/overlay/modbackup/rpmsg_wwan_ctrl.ko.pre823
SSH=(ssh -o BatchMode=yes -o StrictHostKeyChecking=no -o ConnectTimeout=8 "root@$DEV")

dev() { timeout 30 "${SSH[@]}" "$@" 2>&1; }

if [ "${1:-}" = "--revert" ]; then
    echo "==> reverting to $BAK"
    dev "test -f $BAK && cp $BAK $DEST && md5sum $DEST && sync" || { echo "no backup found"; exit 1; }
    echo "==> rebooting"
    dev 'reboot' || true
    exit 0
fi

[ -f "$KO" ] || { echo "ERROR: $KO not built yet"; exit 1; }

LOCAL_MD5="$(md5sum "$KO" | awk '{print $1}')"
echo "==> local  $KO"
echo "    md5   $LOCAL_MD5  ($(stat -c%s "$KO") B)"

echo "==> back up the current module on the device"
dev "mkdir -p /overlay/modbackup && cp -n $DEST $BAK; md5sum $BAK $DEST"

echo "==> copy the patched module"
cat "$KO" | dev "cat > /tmp/rpmsg_wwan_ctrl.ko.new && md5sum /tmp/rpmsg_wwan_ctrl.ko.new"

# Verify the transfer BEFORE installing it (Doc 168 trap 12: an empty/partial
# file is indistinguishable from a working one until it is too late).
REMOTE_MD5="$(dev 'md5sum /tmp/rpmsg_wwan_ctrl.ko.new' | awk '{print $1}')"
if [ "$REMOTE_MD5" != "$LOCAL_MD5" ]; then
    echo "ERROR: md5 mismatch -- remote $REMOTE_MD5 != local $LOCAL_MD5"
    echo "       NOT installing."
    exit 1
fi
echo "    md5 verified on the device"

echo "==> install (rmmod is impossible: qmi-proxy holds /dev/wwan0qmi0 -- reboot instead)"
dev "cp /tmp/rpmsg_wwan_ctrl.ko.new $DEST && rm -f /tmp/rpmsg_wwan_ctrl.ko.new && md5sum $DEST && sync"

echo "==> rebooting to load it"
dev 'reboot' || true

echo "==> waiting for the device to come back"
UP=""
for i in $(seq 1 40); do
    UP="$(dev 'cat /proc/uptime' | awk '{print $1}')"
    if [ -n "$UP" ]; then
        echo "    up, uptime=$UP"
        break
    fi
    sleep 3
done
if [ -z "$UP" ]; then
    echo "ERROR: device did not come back after 120 s -- needs a physical power cycle"
    exit 1
fi

echo "==> post-boot verification"
dev 'md5sum /lib/modules/6.12.94/rpmsg_wwan_ctrl.ko; lsmod | grep -E "rpmsg_wwan_ctrl|wwan"; cat /proc/uptime'
echo "    expected md5: $LOCAL_MD5"

# Functional check: patch 823 changes what poll() reports for the wwan port, so
# the QMI path must still work end to end.  A fix that removes the UAF but breaks
# qmi-proxy would look "clean" in the corruption count and be worthless.
echo "==> functional check (the QMI path must still work)"
dev 'sleep 20; pidof qmi-proxy; ip -br link show wwan0; ip route show default; mmcli -m 0 2>/dev/null | head -12'
echo "==> data check"
dev 'ping -c 4 -W 3 -q 8.8.8.8 2>&1 | tail -3'
echo "==> the poller must still be attached"
QPID="$(dev 'pidof qmi-proxy' | tr -d ' \r')"
[ -n "$QPID" ] && dev "ls -l /proc/$QPID/fd | grep wwan"
echo "==> done"
