/*
 * Doc 173 -- encoder-level verification of the ATS_RTC read-only poll.
 *
 * WHY THIS IS A REAL VERIFICATION AND NOT A RE-STATEMENT OF THE CODE
 * ------------------------------------------------------------------
 * qcom-time-daemon does not hand-roll its QMI messages.  It calls libqrtr's
 * qmi_encode_message() and passes the RETURN VALUE straight to qrtr_sendto(),
 * logging it as "(len=%zd)"  (qcom-time-daemon.c, qmi_send_sync_transaction):
 *
 *     enc_len = qmi_encode_message(&tx_pkt, QMI_REQUEST, req_msg_id,
 *                                  txn_id, req_struct, req_ei);
 *     syslog(..., "TX 0x%04x ... (len=%zd)", req_msg_id, ..., enc_len);
 *     ret = qrtr_sendto(sock, node, port, tx_buf, enc_len);
 *
 * So the "len=" field in the captured syslog IS this function's return value.
 * Calling the same function, with the same descriptor tables and the same
 * message ids, therefore reproduces the deployed wire format exactly -- and if
 * it yields 14 for the GET and 25 for the SET, then the GET message has no room
 * for a value, on the wire, by construction.
 *
 * Build (cross, same flags the package uses):
 *   aarch64-openwrt-linux-musl-gcc -O2 -Wall -Wextra \
 *       -I<staging>/usr/include -L<staging>/usr/lib \
 *       -o test_encode test_encode.c <daemon-src>/qmi_time.c -lqrtr
 *
 * Run (the binary is aarch64; the host is not):
 *   qemu-aarch64 -L <staging>/root-msm89xx ./test_encode
 *
 * Exit status 0 == both lengths match the captured wire lengths.
 */

#include <stdio.h>
#include <string.h>
#include <stdint.h>
#include <libqrtr.h>
#include "qmi_time.h"

/* Lengths the deployed daemon actually logged, every time, in the 2026-09-19
 * capture: 38 x "TX 0x0021 ... (len=14)" and 19 x "TX 0x0020 ... (len=25)". */
#define WIRE_GET_LEN 14
#define WIRE_SET_LEN 25

static void dump_tlvs(const char *label, const unsigned char *p, size_t n)
{
	size_t i = 0;

	printf("  %-4s (%zu bytes):", label, n);
	for (i = 0; i < n; i++)
		printf(" %02x", p[i]);
	printf("\n");

	/* QMI-over-QRTR header is 7 bytes: u8 type, u16 txn, u16 msg, u16 len. */
	printf("         header: type=0x%02x txn=%u msg=0x%04x msg_len=%u\n",
	       p[0],
	       (unsigned)(p[1] | (p[2] << 8)),
	       (unsigned)(p[3] | (p[4] << 8)),
	       (unsigned)(p[5] | (p[6] << 8)));

	for (i = 7; i + 3 <= n; ) {
		unsigned type = p[i];
		unsigned len = p[i + 1] | (p[i + 2] << 8);
		printf("         TLV type=0x%02x len=%u", type, len);
		if (type == 0x01 && len == 4)
			printf("  <- `base` (u32) = %u", (unsigned)(p[i + 3] |
			       (p[i + 4] << 8) | (p[i + 5] << 16) | (p[i + 6] << 24)));
		if (type == 0x02 && len == 8)
			printf("  <- `offset` (u64)  *** THIS IS THE WRITE ***");
		printf("\n");
		i += 3 + len;
	}
}

int main(void)
{
	DEFINE_QRTR_PACKET(gpkt, 512);
	DEFINE_QRTR_PACKET(spkt, 512);

	struct time_genoff_get_req greq;
	struct time_genoff_set_req sreq;
	ssize_t glen, slen;
	int ok;

	memset(&greq, 0, sizeof(greq));
	greq.base = ATS_RTC;		/* base 0 -- the modem's own uptime */

	memset(&sreq, 0, sizeof(sreq));
	sreq.base = ATS_USER;		/* base 2 */
	sreq.offset = 1473867647648ULL;	/* the value the -r 60 capture wrote */

	glen = qmi_encode_message(&gpkt, QMI_REQUEST, QMI_TIME_GENOFF_GET_REQ,
				  47, &greq, time_genoff_get_req_ei);
	slen = qmi_encode_message(&spkt, QMI_REQUEST, QMI_TIME_GENOFF_SET_REQ,
				  48, &sreq, time_genoff_set_req_ei);

	if (glen < 0 || slen < 0) {
		printf("FAIL: qmi_encode_message returned %zd / %zd\n", glen, slen);
		return 2;
	}

	printf("== what the READ-ONLY poll sends (ATS_RTC, base 0) ==\n");
	dump_tlvs("GET", gpkt.data, (size_t)glen);
	printf("  qmi_encode_message() -> %zd   [captured wire length: %d]\n\n",
	       glen, WIRE_GET_LEN);

	printf("== what the periodic WRITE sends (ATS_USER, base 2) ==\n");
	dump_tlvs("SET", spkt.data, (size_t)slen);
	printf("  qmi_encode_message() -> %zd   [captured wire length: %d]\n\n",
	       slen, WIRE_SET_LEN);

	printf("difference = %zd bytes = TLV header (3) + u64 offset (8)\n\n",
	       slen - glen);

	ok = (glen == WIRE_GET_LEN) && (slen == WIRE_SET_LEN);
	printf("RESULT: %s\n", ok ? "PASS -- encoder reproduces the captured wire lengths"
				  : "FAIL -- lengths do not match the capture");
	return ok ? 0 : 1;
}
