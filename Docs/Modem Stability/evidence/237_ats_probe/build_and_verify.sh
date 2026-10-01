#!/bin/sh
# Doc 237 -- build and verify the standalone ATS_RTC probe (ats-probe).
#
# Builds the probe static for aarch64 (the host IS aarch64, so it also runs
# natively -- no emulation sits between the test and its result), then checks
# the four properties that matter:
#
#   1. it builds clean under -Wall -Wextra
#   2. it runs and emits the documented line shape without a modem
#   3. its QMI TIME descriptor file is byte-identical to the verified daemon's
#   4. it contains NO path that can encode a 0x0020 SET  (with a negative
#      control proving that check can fail)
#   5. the encoder it actually links reproduces the captured wire lengths
#      (GET = 14 bytes, SET = 25)
#
# Usage:  sh build_and_verify.sh
# Exit status 0 == every check passed.

set -e

HERE=$(cd "$(dirname "$0")" && pwd)
REPO=$(cd "$HERE/../../../.." && pwd)
cd "$REPO"

TC=openwrt/staging_dir/toolchain-aarch64_generic_gcc-14.3.0_musl/bin/aarch64-openwrt-linux-musl-gcc
QR=$(ls -d openwrt/build_dir/target-aarch64_generic_musl/qrtr-* 2>/dev/null | head -1)
SRC=packages/ats-probe/src
DAEMON_SRC=packages/qcom-time-daemon/src
OUT=${OUT:-/tmp/ats_probe_verify}

fail=0
note() { printf '%s\n' "$*"; }
ok()   { printf 'PASS  %s\n' "$*"; }
bad()  { printf 'FAIL  %s\n' "$*"; fail=1; }

[ -x "$TC" ] || { echo "cross toolchain not found: $TC" >&2; exit 2; }
[ -f "$QR/lib/qmi.c" ] || { echo "libqrtr sources not found under $QR" >&2; exit 2; }

RUN="qemu-aarch64"
case "$(uname -m)" in
	aarch64|arm64) RUN="" ;;
esac

mkdir -p "$OUT"

note "== 1. build (static, aarch64, -Wall -Wextra) =="
if STAGING_DIR="$REPO/openwrt/staging_dir" "$TC" -static -O2 -Wall -Wextra \
	-I"$QR/include" -I"$SRC" \
	-o "$OUT/ats-probe" \
	"$SRC/ats-probe.c" "$SRC/qmi_time.c" \
	"$QR/lib/qmi.c" "$QR/lib/qrtr.c" "$QR/lib/logging.c" \
	2>"$OUT/build.log"; then
	# Warnings from libqrtr's own qmi.c are expected; warnings from OUR
	# sources are not.  Check for the latter specifically.
	if grep -E "$SRC/(ats-probe|qmi_time)\.c.*warning" "$OUT/build.log"; then
		bad "our sources produced warnings"
	else
		ok "built clean (libqrtr's own -Wsign-compare warnings only)"
	fi
else
	bad "build failed"; sed -n '1,40p' "$OUT/build.log"
fi

note
note "== 2. shape: runs and emits the documented line without a modem =="
note "--- file(1) ---"
file "$OUT/ats-probe" 2>/dev/null || note "(file(1) unavailable)"
note "--- usage (-h) ---"
$RUN "$OUT/ats-probe" -h 2>&1 | sed -n '1,12p' || true
note "--- 5 s run, no modem present (SIGTERM ends it) ---"
timeout 5 $RUN "$OUT/ats-probe" -i 200 -q >"$OUT/smoke.txt" 2>&1 || true
cat "$OUT/smoke.txt"
if head -1 "$OUT/smoke.txt" | grep -q '^# ATS-PROBE v1 interval_ms=200 service=22'; then
	ok "startup header has the documented shape"
else
	bad "startup header missing or malformed"
fi
if grep -q 'ATS-PROBE-EVENT what=exit' "$OUT/smoke.txt"; then
	ok "exits cleanly on signal (exit event emitted)"
else
	bad "no clean exit event"
fi

note
note "== 3. provenance: descriptor file is byte-identical to the daemon's =="
for f in qmi_time.c qmi_time.h; do
	a=$(md5sum "$SRC/$f" | cut -d' ' -f1)
	b=$(md5sum "$DAEMON_SRC/$f" | cut -d' ' -f1)
	if [ "$a" = "$b" ]; then
		ok "$f identical ($a)"
	else
		bad "$f DIFFERS: probe=$a daemon=$b"
	fi
done

note
note "== 4. read-only: no path in ats-probe.c can encode a 0x0020 SET =="
# Grep the CODE, not the prose.  A check that cannot tell a comment from a
# call is a bad check -- the first run of this script flagged its own
# explanatory comments ("The 0x0020 SET ... is never built here").
strip_comments() {
	python3 - "$1" <<'PY'
import re, sys
src = open(sys.argv[1]).read()
src = re.sub(r'/\*.*?\*/', '', src, flags=re.S)   # block comments
src = re.sub(r'//[^\n]*', '', src)                # line comments
sys.stdout.write(src)
PY
}
strip_comments "$SRC/ats-probe.c" > "$OUT/ats-probe.nocomment.c"
CODE="$OUT/ats-probe.nocomment.c"

if grep -nE 'qmi_encode_message' "$CODE" | grep -v 'QMI_TIME_GENOFF_GET_REQ'; then
	bad "an encode call does not use the GET message id"
else
	ok "every qmi_encode_message() call uses QMI_TIME_GENOFF_GET_REQ"
fi
for pat in 'time_genoff_set_req_ei' 'QMI_TIME_GENOFF_SET_REQ' 'send_ats_user' '0x0020'; do
	if grep -q -- "$pat" "$CODE"; then
		bad "ats-probe.c code references a write path: $pat"
	else
		ok "no reference to write path in code: $pat"
	fi
done
note "--- negative control: the check above must be able to FAIL ---"
cp "$SRC/ats-probe.c" "$OUT/ats-probe.injected.c"
printf '\n/* injected control */\nstruct qmi_elem_info *x = time_genoff_set_req_ei;\n' \
	>> "$OUT/ats-probe.injected.c"
strip_comments "$OUT/ats-probe.injected.c" > "$OUT/ats-probe.injected.nocomment.c"
if grep -q -- 'time_genoff_set_req_ei' "$OUT/ats-probe.injected.nocomment.c"; then
	ok "negative control fired (injection detected in code)"
else
	bad "negative control did NOT fire -- the check is vacuous"
fi

note
note "== 5. encoder: the linked descriptor reproduces the captured wire =="
STAGING_DIR="$REPO/openwrt/staging_dir" "$TC" -static -O2 -Wall \
	-I"$QR/include" -I"$SRC" \
	-o "$OUT/test_encode" \
	"$HERE/test_encode.c" "$SRC/qmi_time.c" \
	"$QR/lib/qmi.c" "$QR/lib/qrtr.c" "$QR/lib/logging.c"
$RUN "$OUT/test_encode"
enc_rc=$?
if [ "$enc_rc" -eq 0 ]; then
	ok "GET=14 bytes, SET=25 bytes -- the GET has no room for a value"
else
	bad "encoder length check failed (rc=$enc_rc)"
fi

note
if [ "$fail" -eq 0 ]; then
	note "RESULT: ALL CHECKS PASSED"
else
	note "RESULT: ONE OR MORE CHECKS FAILED"
fi
exit "$fail"
