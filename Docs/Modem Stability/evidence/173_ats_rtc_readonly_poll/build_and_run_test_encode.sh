#!/bin/sh
# Doc 173 -- build and run the encoder verification for the ATS_RTC read-only poll.
#
# The daemon is aarch64/musl and the host is not, so the test is cross-compiled
# and run under qemu-user.  Two things make that work here:
#
#   1. qemu-user on this machine does NOT apply -L to library opens, so the
#      test is linked STATICALLY.  libqrtr ships no .a, so its sources are
#      compiled straight into the binary from the build tree.
#   2. everything is built into a scratch dir -- no artifacts are written into
#      the repository.
#
# Usage:  sh build_and_run_test_encode.sh          (from anywhere)
#
# Exit status is the test's own: 0 = lengths match the captured wire.

set -e

HERE=$(cd "$(dirname "$0")" && pwd)
REPO=$(cd "$HERE/../../../.." && pwd)
cd "$REPO"

TC=openwrt/staging_dir/toolchain-aarch64_generic_gcc-14.3.0_musl/bin/aarch64-openwrt-linux-musl-gcc
QR=$(ls -d openwrt/build_dir/target-aarch64_generic_musl/qrtr-* 2>/dev/null | head -1)
SRC=openwrt/package/msm8916/qcom-time-daemon/src
OUT=${OUT:-/tmp/qtest_ats_rtc}

if [ ! -x "$TC" ]; then
	echo "cross toolchain not found: $TC" >&2
	exit 2
fi
if [ -z "$QR" ] || [ ! -f "$QR/lib/qmi.c" ]; then
	echo "libqrtr sources not found under openwrt/build_dir/.../qrtr-*" >&2
	echo "(build the qrtr package first, or point QR at the sources)" >&2
	exit 2
fi
if ! command -v qemu-aarch64 >/dev/null 2>&1; then
	echo "qemu-aarch64 not found; cannot run the aarch64 test on this host" >&2
	exit 2
fi

# Prefer NATIVE execution when the host is itself aarch64 -- a static musl
# aarch64 binary runs directly, which removes any emulation doubt.  Fall back
# to qemu-user on other hosts.
RUN="qemu-aarch64"
case "$(uname -m)" in
	aarch64|arm64) RUN="" ;;
esac

mkdir -p "$OUT"

echo "== building (static, aarch64) =="
STAGING_DIR="$REPO/openwrt/staging_dir" "$TC" -static -O2 -Wall \
	-I"$QR/include" -I"$SRC" \
	-o "$OUT/test_encode" \
	"$HERE/test_encode.c" "$SRC/qmi_time.c" \
	"$QR/lib/qmi.c" "$QR/lib/qrtr.c" "$QR/lib/logging.c"

echo "== running ($([ -n "$RUN" ] && echo "under $RUN" || echo "natively")) =="
$RUN "$OUT/test_encode"
rc=$?

echo
echo "== also smoke-testing the daemon's -p wiring =="
STAGING_DIR="$REPO/openwrt/staging_dir" "$TC" -static -O2 -Wall \
	-I"$QR/include" \
	-o "$OUT/qtd_static" \
	"$SRC/qcom-time-daemon.c" "$SRC/qmi_time.c" \
	"$QR/lib/qmi.c" "$QR/lib/qrtr.c" "$QR/lib/logging.c"

echo "--- with -p (poll ENABLED, write must stay DISABLED) ---"
timeout 4 $RUN "$OUT/qtd_static" -p 2>&1 | grep -E "modem-uptime poll|ATS_USER refresh" || true
echo "--- without -p (deployed default) ---"
timeout 4 $RUN "$OUT/qtd_static" 2>&1 | grep -E "modem-uptime poll|ATS_USER refresh" || true

exit $rc
