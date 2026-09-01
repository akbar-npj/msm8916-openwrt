#!/bin/sh
# SPDX-License-Identifier: GPL-2.0-only
#
# reboot-mode.sh - Safe shutdown and reboot-to-mode utility for Qualcomm MSM8916
#
# Supports: edl (9008), dload (9006), bootloader/fastboot, recovery, poweroff

set -e

PROG="$(basename "$0")"
FORCE=0
MODE=""

print_usage() {
	echo "Qualcomm MSM Reboot Utility"
	echo "Usage: $PROG [options] [mode]"
	echo ""
	echo "Available modes:"
	echo "  edl        - Qualcomm 9008 Emergency Download Mode (PBL)"
	echo "  dload      - Qualcomm 9006 Mass Storage / Dump Mode (SBL1)"
	echo "  bootloader - Fastboot Mode (aboot)"
	echo "  fastboot   - Fastboot Mode (aboot)"
	echo "  recovery   - Recovery Mode (aboot)"
	echo ""
	echo "Options:"
	echo "  -f, --force - Skip graceful shutdown and trigger reboot immediately"
	echo "  -h, --help  - Show this help message"
}

# Determine default mode based on symlink / binary name
case "$PROG" in
	*edl*|*9008*)   MODE="edl" ;;
	*dload*|*9006*) MODE="dload" ;;
	*bootloader*)  MODE="bootloader" ;;
	*fastboot*)    MODE="fastboot" ;;
	*recovery*)    MODE="recovery" ;;
	*)             MODE="edl" ;;
esac

# Parse arguments
while [ $# -gt 0 ]; do
	case "$1" in
		-f|--force)
			FORCE=1
			;;
		-h|--help)
			print_usage
			exit 0
			;;
		-*)
			echo "Unknown option: $1"
			print_usage
			exit 1
			;;
		*)
			MODE="$1"
			;;
	esac
	shift
done

[ -z "$MODE" ] && MODE="edl"

echo "[*] Rebooting into '$MODE' mode..."

if [ "$FORCE" -eq 1 ]; then
	echo "[!] Force mode requested: skipping graceful teardown"
	sync
	exec /sbin/reboot-mode-raw "$MODE"
fi

# 1. Graceful network interface shutdown
if command -v ifdown >/dev/null 2>&1; then
	echo "[*] Bringing down network interfaces..."
	ifdown -a 2>/dev/null || true
fi

# 2. Stop running services gracefully
if [ -d /etc/rc.d ]; then
	echo "[*] Stopping system services..."
	/etc/init.d/rcS K shutdown >/dev/null 2>&1 || true
fi

# 3. Terminate remaining daemons
echo "[*] Terminating processes (SIGTERM)..."
killall5 -15 2>/dev/null || true
sync
sleep 1

echo "[*] Killing remaining processes (SIGKILL)..."
killall5 -9 2>/dev/null || true
sync
sleep 1

# 4. Flush page caches and sync buffers
echo 3 > /proc/sys/vm/drop_caches 2>/dev/null || true
sync

# 5. Remount storage and overlay read-only
echo "[*] Remounting filesystems read-only..."
if grep -q " /overlay " /proc/mounts 2>/dev/null; then
	mount -o noatime,remount,ro /overlay 2>/dev/null || true
fi
mount -o remount,ro / 2>/dev/null || true
umount -a -r 2>/dev/null || true

# 6. Kernel-level emergency sync & remount-ro
if [ -w /proc/sysrq-trigger ]; then
	echo s > /proc/sysrq-trigger 2>/dev/null || true
	echo u > /proc/sysrq-trigger 2>/dev/null || true
fi

echo "[*] Dispatching reboot to hardware ($MODE)..."
exec /sbin/reboot-mode-raw "$MODE"
