#!/bin/sh
# Doc 180 -- build and run the persistent-ATS-state test.
#
# The host is aarch64 and the target is aarch64, so everything is linked
# STATIC and runs NATIVELY.  No emulator is in the loop, which removes any
# doubt about the result (reference_host_is_aarch64_native_target_tests.md).
#
# The daemon's persistence helpers are static, so the test #includes the daemon
# source with main() renamed -- it exercises the shipped code, not a copy.
#
# Usage:  sh build_and_run_test_ats_persist.sh          (from anywhere)
#
# Exit status is the test's own: 0 = every check passed.

set -e

HERE=$(cd "$(dirname "$0")" && pwd)
REPO=$(cd "$HERE/../../../.." && pwd)
cd "$REPO"

TC=openwrt/staging_dir/toolchain-aarch64_generic_gcc-14.3.0_musl/bin/aarch64-openwrt-linux-musl-gcc
QR=$(ls -d openwrt/build_dir/target-aarch64_generic_musl/qrtr-* 2>/dev/null | head -1)
SRC=packages/qcom-time-daemon/src
OUT=${OUT:-/tmp/qtest_ats_persist}

if [ ! -x "$TC" ]; then
	echo "cross toolchain not found: $TC" >&2
	exit 2
fi
if [ -z "$QR" ] || [ ! -f "$QR/lib/qmi.c" ]; then
	echo "libqrtr sources not found under openwrt/build_dir/.../qrtr-*" >&2
	echo "(build the qrtr package first, or point QR at the sources)" >&2
	exit 2
fi

# Prefer NATIVE execution when the host is itself aarch64.
RUN="qemu-aarch64"
case "$(uname -m)" in
	aarch64|arm64) RUN="" ;;
esac
if [ -n "$RUN" ] && ! command -v qemu-aarch64 >/dev/null 2>&1; then
	echo "qemu-aarch64 not found; cannot run the aarch64 test on this host" >&2
	exit 2
fi

mkdir -p "$OUT"

echo "== building the unit test (static, aarch64) =="
STAGING_DIR="$REPO/openwrt/staging_dir" "$TC" -static -O2 -Wall -Wextra \
	-I"$QR/include" -I"$SRC" \
	-o "$OUT/test_ats_persist" \
	"$HERE/test_ats_persist.c" "$SRC/qmi_time.c" \
	"$QR/lib/qmi.c" "$QR/lib/qrtr.c" "$QR/lib/logging.c"

echo "== running ($([ -n "$RUN" ] && echo "under $RUN" || echo "natively")) =="
$RUN "$OUT/test_ats_persist"
rc=$?

# ---------------------------------------------------------------------------
# End-to-end wiring of the real binary.  main() logs the persistence config and
# loads the record BEFORE it opens the QRTR socket, so a host run that dies at
# qrtr_open still proves the flag and the load path.
# ---------------------------------------------------------------------------
echo
echo "== smoke-testing the real daemon's -P wiring =="
STAGING_DIR="$REPO/openwrt/staging_dir" "$TC" -static -O2 -Wall \
	-I"$QR/include" \
	-o "$OUT/qtd_static" \
	"$SRC/qcom-time-daemon.c" "$SRC/qmi_time.c" \
	"$QR/lib/qmi.c" "$QR/lib/qrtr.c" "$QR/lib/logging.c"

W=$(date +%s)000
OFF=$((W - 315964800000 - 5000000))
STATE=$OUT/ats_state

cat >"$STATE" <<EOF
magic=qcom-ats-state
version=1
base=2
offset_ms=$OFF
rtc_ms=5000000
wall_ms=$W
delta_ms=$((W - 5000000))
synced_at=$((W / 1000))
EOF

echo "--- default path, with a prepared record ---"
timeout 5 $RUN "$OUT/qtd_static" -P "$STATE" 2>&1 \
	| grep -E "Persistent ATS state|ATS state:" || true

echo "--- -P none (whole path DISABLED) ---"
timeout 5 $RUN "$OUT/qtd_static" -P none 2>&1 \
	| grep -E "Persistent ATS state|ATS state:" || true

echo "--- default path, no record present ---"
rm -f "$STATE"
timeout 5 $RUN "$OUT/qtd_static" -P "$STATE" 2>&1 \
	| grep -E "Persistent ATS state|ATS state:" || true

exit $rc
