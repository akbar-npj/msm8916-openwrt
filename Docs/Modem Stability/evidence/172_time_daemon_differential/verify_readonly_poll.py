#!/usr/bin/env python3
"""
Doc 172 -- VERIFY the ATS_RTC read-only poll.

Question: can a periodic ATS_RTC (QMI TIME base 0) poll be added to
qcom-time-daemon WITHOUT it being able to write to the modem?

This script answers that mechanically, from two independent sources:

  PART 1 -- the DESCRIPTOR (what the encoder is capable of emitting)
            Parses time_genoff_get_req_ei / time_genoff_set_req_ei out of
            qmi_time.c, computes the encoded byte length of each, and checks
            that the GET request declares a `base` TLV and NOTHING ELSE.

  PART 2 -- the WIRE (what the deployed daemon actually emitted)
            Parses every "TX 0x00NN ... (len=NN)" line the daemon logged on the
            OpenWrt boot of 2026-09-19 and checks that EVERY 0x0021 is exactly
            the length PART 1 predicts for a base-only message, and every
            0x0020 is exactly the length it predicts for a base+offset message.

  PART 3 -- the CODE (what the new poll function can reach)
            Checks that log_modem_uptime()'s body contains no reference to the
            write message, the write descriptor, or the write transaction
            function.

If all three pass, the read-only property is a property of the message format
and of the measured wire -- not a promise in a comment.

Usage:  python3 verify_readonly_poll.py
"""

import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
# .../Docs/Modem Stability/evidence/172_time_daemon_differential -> repo root
REPO = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
SRC = os.environ.get('QTD_SRC',
                     os.path.join(REPO, 'openwrt', 'package', 'msm8916',
                                  'qcom-time-daemon', 'src'))
QMI_C = os.path.join(SRC, 'qmi_time.c')
DAEMON_C = os.path.join(SRC, 'qcom-time-daemon.c')
TX = os.environ.get('QTD_TX', os.path.join(HERE, 'qmi_tx_lengths.txt'))

# The QMI header the daemon puts in front of every message.
#   struct qmi_header { uint8_t type; uint16_t txn_id; uint16_t msg_id;
#                       uint16_t msg_len; } __attribute__((packed));
# = 1 + 2 + 2 + 2 = 7 bytes.  See qcom-time-daemon.c.
QMI_HEADER = 7

# QMI primitive widths, from the data_type names used in qmi_time.c.
TYPE_WIDTH = {
    'QMI_UNSIGNED_1_BYTE': 1,
    'QMI_UNSIGNED_2_BYTE': 2,
    'QMI_UNSIGNED_4_BYTE': 4,
    'QMI_UNSIGNED_8_BYTE': 8,
}

fails = []


def check(ok, msg):
    print(('  PASS  ' if ok else '  FAIL  ') + msg)
    if not ok:
        fails.append(msg)
    return ok


# --------------------------------------------------------------------------
# PART 1 -- the descriptor
# --------------------------------------------------------------------------
def parse_ei(path, name):
    """Pull one `struct qmi_elem_info <name>[] = { ... };` block and return the
    list of (tlv_type, width) for its non-terminator entries."""
    src = open(path).read()
    m = re.search(r'struct qmi_elem_info\s+' + re.escape(name) + r'\s*\[\]\s*=\s*\{(.*?)\n\};',
                  src, re.S)
    if not m:
        sys.exit('could not find %s in %s' % (name, path))
    body = m.group(1)

    entries = []
    for blk in re.finditer(r'\{(.*?)\}', body, re.S):
        e = blk.group(1)
        dt = re.search(r'\.data_type\s*=\s*(\w+)', e)
        tlv = re.search(r'\.tlv_type\s*=\s*(0x[0-9a-fA-F]+|\d+)', e)
        if not dt:
            continue                      # the trailing `{}` terminator
        if dt.group(1) == 'QMI_STRUCT':
            # a nested struct; only used on the response path here
            entries.append((None, 'QMI_STRUCT', e.strip()))
            continue
        if not tlv:
            continue
        entries.append((int(tlv.group(1), 0), TYPE_WIDTH[dt.group(1)], None))
    return entries


def encoded_len(entries):
    """QMI header + sum over TLVs of (1 type + 2 length + value)."""
    total = QMI_HEADER
    for tlv, width, _ in entries:
        if width == 'QMI_STRUCT':
            continue
        total += 1 + 2 + width
    return total


print('PART 1 -- the descriptor (what the encoder can emit)')
print('  source: %s' % os.path.relpath(QMI_C, REPO))

get_req = parse_ei(QMI_C, 'time_genoff_get_req_ei')
set_req = parse_ei(QMI_C, 'time_genoff_set_req_ei')

print('  time_genoff_get_req_ei TLVs: %s' % [(hex(t), w) for t, w, _ in get_req])
print('  time_genoff_set_req_ei TLVs: %s' % [(hex(t), w) for t, w, _ in set_req])

check(len(get_req) == 1, 'GET request declares exactly ONE TLV (n=%d)' % len(get_req))
check(get_req[0][0] == 0x01 and get_req[0][1] == 4,
      'that TLV is 0x01 / 4 bytes = `base` (the base index)')
check(all(t != 0x02 for t, _, _ in get_req),
      'GET request has NO TLV 0x02 -- the 8-byte `offset` field does not exist in it')
check(len(set_req) == 2 and any(t == 0x02 for t, _, _ in set_req),
      'SET request DOES have TLV 0x02 (8-byte offset) -- this is what makes it a write')

get_len = encoded_len(get_req)
set_len = encoded_len(set_req)
print('  predicted GET length = %d + %s = %d' % (QMI_HEADER, '+'.join(
    '3+%d' % w for _, w, _ in get_req), get_len))
print('  predicted SET length = %d + %s = %d' % (QMI_HEADER, '+'.join(
    '3+%d' % w for _, w, _ in set_req), set_len))
check(set_len - get_len == 11,
      'the write is exactly 11 bytes longer (TLV hdr 3 + 8-byte offset)')

# --------------------------------------------------------------------------
# PART 2 -- the wire
# --------------------------------------------------------------------------
print()
print('PART 2 -- the wire (what the deployed daemon actually emitted)')
print('  source: %s' % os.path.relpath(TX, REPO))

seen = {}
for line in open(TX):
    if line.startswith('#'):
        continue
    m = re.search(r'TX (0x[0-9a-fA-F]+) to QRTR.*\(len=(\d+)\)', line)
    if not m:
        continue
    msg, ln = m.group(1), int(m.group(2))
    seen.setdefault(msg, set()).add(ln)

for msg, lengths in sorted(seen.items()):
    print('  %s: %d message(s), lengths=%s' % (msg, len(lengths), sorted(lengths)))

get_lines = [l for l in open(TX) if 'TX 0x0021' in l]
set_lines = [l for l in open(TX) if 'TX 0x0020' in l]
check(get_lines and set_lines, 'the wire log has both GET and SET transactions to compare')

check(seen.get('0x0021') == {get_len},
      'EVERY 0x0021 GET on the wire is exactly %d bytes (n=%d) -- none deviates, '
      'so none ever carried a value' % (get_len, len(get_lines)))
check(seen.get('0x0020') == {set_len},
      'EVERY 0x0020 SET on the wire is exactly %d bytes (n=%d)' % (set_len, len(set_lines)))

# --------------------------------------------------------------------------
# PART 3 -- the code
# --------------------------------------------------------------------------
print()
print('PART 3 -- the code (what the new poll function can reach)')
print('  source: %s' % os.path.relpath(DAEMON_C, REPO))

src = open(DAEMON_C).read()
m = re.search(r'static int log_modem_uptime\(int sock\)\s*\{(.*?)\n\}', src, re.S)
if not m:
    sys.exit('could not find log_modem_uptime() in %s' % DAEMON_C)
body = m.group(1)

for needle, why in [
    ('0x0020', 'the write message id'),
    ('GENOFF_SET', 'the write message name'),
    ('send_ats_user_transaction', 'the write transaction function'),
    ('time_genoff_set_req_ei', 'the write descriptor'),
]:
    check(needle not in body, 'log_modem_uptime() contains no reference to %s (%s)' % (needle, why))

check('query_modem_ats_base' in body and 'ATS_RTC' in body,
      'log_modem_uptime() reads via query_modem_ats_base(ATS_RTC, ...)')

# and the read helper itself only ever sends the GET
m = re.search(r'static int query_modem_ats_base\(.*?\n\{(.*?)\n\}', src, re.S)
qb = m.group(1)
check('QMI_TIME_GENOFF_GET_REQ' in qb and 'QMI_TIME_GENOFF_SET_REQ' not in qb,
      'query_modem_ats_base() sends only QMI_TIME_GENOFF_GET_REQ')

# --------------------------------------------------------------------------
print()
if fails:
    print('RESULT: %d CHECK(S) FAILED' % len(fails))
    for f in fails:
        print('  - ' + f)
    sys.exit(1)
print('RESULT: ALL CHECKS PASSED')
print()
print('The read-only property is established three ways:')
print('  * the GET descriptor has no field capable of carrying a value;')
print('  * every GET the deployed daemon ever sent was exactly that size;')
print('  * the poll function cannot reach the write path.')
