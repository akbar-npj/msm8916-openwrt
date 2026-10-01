#!/bin/sh
# Doc 237 -- deploy ats-probe to an OpenWrt HMU05 dongle and collect across fatals.
#
# Usage:  sh deploy_and_collect.sh [host] [duration_s] [interval_ms]
#         defaults: 192.168.8.1  1800  1000
#
# What it does, in order:
#   1. builds the probe static for aarch64 (same toolchain/flags as the package)
#   2. preflights the target: aarch64, OpenWrt, QRTR available
#   3. copies the binary over and verifies its md5 ON THE DEVICE (never trust scp)
#   4. starts it with start-stop-daemon, writing to a DEVICE-LOCAL file
#   5. polls dmesg for `fatal error received` and prints each one as it lands
#   6. stops it, pulls the log, verifies size + md5, and prints the sample count
#
# The capture is device-local on purpose: an ssh pipe silently truncates
# (the Android ramdump lesson).  See Doc 237 sec.7.

set -u

HOST=${1:-192.168.8.1}
DURATION=${2:-1800}
INTERVAL=${3:-1000}

HERE=$(cd "$(dirname "$0")" && pwd)
REPO=$(cd "$HERE/../../../.." && pwd)
OUT=${OUT:-/tmp/ats_probe_deploy}
DEV_BIN=/usr/sbin/ats-probe
DEV_LOG=/root/ats_probe.log
DEV_PID=/var/run/ats-probe.pid

SSH="ssh -o BatchMode=yes -o StrictHostKeyChecking=no -o ConnectTimeout=6 root@$HOST"
SCP="scp -q -o BatchMode=yes -o StrictHostKeyChecking=no -o ConnectTimeout=6"

die() { printf 'FATAL: %s\n' "$*" >&2; exit 1; }

# ---------------------------------------------------------------- 1. build
TC=$REPO/openwrt/staging_dir/toolchain-aarch64_generic_gcc-14.3.0_musl/bin/aarch64-openwrt-linux-musl-gcc
QR=$(ls -d "$REPO"/openwrt/build_dir/target-aarch64_generic_musl/qrtr-* 2>/dev/null | head -1)
SRC=$REPO/packages/ats-probe/src

[ -x "$TC" ] || die "cross toolchain not found: $TC"
[ -f "$QR/lib/qmi.c" ] || die "libqrtr sources not found under $QR"

mkdir -p "$OUT"
echo "== building (static, aarch64) =="
STAGING_DIR="$REPO/openwrt/staging_dir" "$TC" -static -O2 -Wall -Wextra \
	-I"$QR/include" -I"$SRC" \
	-o "$OUT/ats-probe" \
	"$SRC/ats-probe.c" "$SRC/qmi_time.c" \
	"$QR/lib/qmi.c" "$QR/lib/qrtr.c" "$QR/lib/logging.c" \
	|| die "build failed"

LOCAL_MD5=$(md5sum "$OUT/ats-probe" | cut -d' ' -f1)
echo "   local md5: $LOCAL_MD5"
case "$(ldd "$OUT/ats-probe" 2>&1)" in
	*"not a dynamic executable"*) : ;;
	*) die "binary is NOT static -- scp deployment is unsafe" ;;
esac

# ------------------------------------------------------------- 2. preflight
echo "== preflight $HOST =="
$SSH 'true' 2>/dev/null || die "cannot ssh to $HOST (is the OpenWrt dongle attached and on this subnet?)"

ARCH=$($SSH 'uname -m' 2>/dev/null)
[ "$ARCH" = "aarch64" ] || die "target is '$ARCH', not aarch64 -- wrong device?"

if ! $SSH 'test -f /etc/openwrt_release' 2>/dev/null; then
	echo "WARN: /etc/openwrt_release absent -- not obviously OpenWrt; continuing" >&2
fi

# The probe needs AF_QIPCRTR.  On Android this is absent (no CONFIG_QRTR), which
# is exactly why ats-probe is OpenWrt-only; fail loudly rather than silently
# produce an empty log.
if ! $SSH 'test -d /sys/module/qrtr || test -e /proc/net/qrtr || lsmod 2>/dev/null | grep -q qrtr' 2>/dev/null; then
	echo "WARN: no QRTR module/entry visible; the probe may fail to open a socket" >&2
fi

DEV_UPTIME0=$($SSH 'cut -d" " -f1 /proc/uptime' 2>/dev/null)
echo "   device aarch64, ap_boot=${DEV_UPTIME0}s"

# --------------------------------------------------------------- 3. deploy
echo "== deploying =="
$SCP "$OUT/ats-probe" "root@$HOST:/tmp/ats-probe.new" || die "scp failed"
REMOTE_MD5=$($SSH "md5sum /tmp/ats-probe.new" 2>/dev/null | cut -d' ' -f1)
[ "$REMOTE_MD5" = "$LOCAL_MD5" ] || die "md5 mismatch after copy: local=$LOCAL_MD5 remote=$REMOTE_MD5"
$SSH "mv /tmp/ats-probe.new $DEV_BIN && chmod 755 $DEV_BIN" || die "install failed"
echo "   installed $DEV_BIN (md5 verified on device)"

# ---------------------------------------------------------------- 4. start
echo "== starting: interval=${INTERVAL}ms, output=$DEV_LOG =="
$SSH "rm -f $DEV_LOG $DEV_PID; \
      start-stop-daemon -S -b -m -p $DEV_PID -x $DEV_BIN -- -i $INTERVAL -o $DEV_LOG -q" \
	|| die "start-stop-daemon failed"

sleep 3
if ! $SSH "grep -q '^# ATS-PROBE v1' $DEV_LOG" 2>/dev/null; then
	echo "--- first 20 lines of the device log ---" >&2
	$SSH "head -20 $DEV_LOG" >&2
	die "probe did not start (no header in $DEV_LOG)"
fi
echo "   header OK; sampling"

# ------------------------------------------------- 5. watch for fatals
echo "== watching dmesg for fatals for ${DURATION}s =="
ELAPSED=0
FATALS=0
STEP=15
while [ "$ELAPSED" -lt "$DURATION" ]; do
	sleep "$STEP"
	ELAPSED=$((ELAPSED + STEP))

	# Count fatal lines.  NOTE: a bare `grep -c` on a ring that may wrap is
	# meaningless -- read the last line's own timestamp instead, as item 53 did.
	LAST=$($SSH "dmesg | grep -F 'fatal error received' | tail -1" 2>/dev/null)
	if [ -n "$LAST" ] && [ "$LAST" != "${PREV_LAST:-}" ]; then
		FATALS=$((FATALS + 1))
		PREV_LAST=$LAST
		echo "   [${ELAPSED}s] FATAL #$FATALS: $LAST"
		# The sample nearest the fatal, straight off the device:
		$SSH "tail -2 $DEV_LOG" 2>/dev/null | sed 's/^/      /'
	fi
done

# ----------------------------------------------------------------- 6. stop
echo "== stopping =="
$SSH "start-stop-daemon -K -p $DEV_PID -x $DEV_BIN 2>/dev/null; sleep 1; \
      grep -c '^ATS-PROBE ' $DEV_LOG 2>/dev/null" > "$OUT/remote_count.txt" 2>/dev/null
REMOTE_SAMPLES=$(cat "$OUT/remote_count.txt" 2>/dev/null)

# ---------------------------------------------------------------- 7. pull
echo "== pulling $DEV_LOG =="
REMOTE_SIZE=$($SSH "wc -c < $DEV_LOG" 2>/dev/null)
REMOTE_SUM=$($SSH "md5sum $DEV_LOG" 2>/dev/null | cut -d' ' -f1)
$SCP "root@$HOST:$DEV_LOG" "$OUT/ats_probe.log" || die "pull failed"
LOCAL_SIZE=$(wc -c < "$OUT/ats_probe.log")
LOCAL_SUM=$(md5sum "$OUT/ats_probe.log" | cut -d' ' -f1)

[ "$REMOTE_SIZE" = "$LOCAL_SIZE" ] || die "size mismatch: remote=$REMOTE_SIZE local=$LOCAL_SIZE"
[ "$REMOTE_SUM" = "$LOCAL_SUM" ] || die "md5 mismatch: remote=$REMOTE_SUM local=$LOCAL_SUM"
echo "   verified: $LOCAL_SIZE B, md5 $LOCAL_SUM"

# -------------------------------------------------------------- 8. summary
echo
echo "==================== SUMMARY ===================="
echo "samples        : ${REMOTE_SAMPLES:-?}"
echo "fatals seen    : $FATALS"
echo "log            : $OUT/ats_probe.log"
echo
echo "SSR events:"
grep 'ATS-PROBE-EVENT what=ssr' "$OUT/ats_probe.log" | tail -10 | sed 's/^/  /'
echo
echo "First and last good samples:"
grep '^ATS-PROBE ' "$OUT/ats_probe.log" | head -1 | sed 's/^/  /'
grep '^ATS-PROBE ' "$OUT/ats_probe.log" | tail -1 | sed 's/^/  /'
echo
echo "Next: correlate each fatal's dmesg stamp with the nearest sample and apply"
echo "Doc 237 sec.4 -- R_fatal constant => CONDITION; tracks recovery => always-on"
echo "CLOCK; monotone drift/wrap => COUNTER.  The rate ratio rho = d(rtc_ms)/d(ap_boot_ms)"
echo "over the whole log is the direct SCLK-drift test."
