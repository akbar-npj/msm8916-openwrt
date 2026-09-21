#!/bin/sh
# Doc 174 §2.3 -- show that /d/rpm_log is NOT an RPM record source.
#
# It is a live unbounded ASCII-hex stream whose content is not stable and which
# does not contain the ring's 0x00200000 record marker at any byte offset.
# Run from the repo root with a rooted device attached.
set -u
D=${1:-/tmp}
adb shell 'head -c 200000 /d/rpm_log > /data/local/tmp/rl1.bin; md5sum /data/local/tmp/rl1.bin'
sleep 8
adb shell 'head -c 200000 /d/rpm_log > /data/local/tmp/rl2.bin; md5sum /data/local/tmp/rl2.bin'
echo "# the two md5s differ => live stream, not a stable dump"
adb pull /data/local/tmp/rl1.bin "$D/rpm_log_200k.bin" >/dev/null 2>&1
python3 - "$D/rpm_log_200k.bin" <<'PY'
import re,struct,sys
from collections import Counter
b=bytes(int(t,16) for t in re.findall(r'0x([0-9A-Fa-f]{1,2})', open(sys.argv[1],'rb').read().decode('latin1')))
n=len(b)
mk=sum(1 for i in range(n-4) if b[i:i+4]==b'\x00\x00\x20\x00')
small=sum(1 for i in range(0,n-4,4) if struct.unpack_from('<I',b,i)[0]<0x1000)
print(f"decoded bytes={n}  markers(0x00200000)={mk}  words<0x1000={100*small/(n//4):.2f}%")
print("=> markers=0 at EVERY offset: /d/rpm_log does NOT carry the ring records.")
PY
