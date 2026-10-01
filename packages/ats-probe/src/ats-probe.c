/*
 * ats-probe -- read the modem's OWN uptime (ATS_RTC) over QMI TIME, stamped
 * with the AP's monotonic clock, so the two can be correlated.
 *
 * WHY THIS EXISTS
 * ---------------
 * The ~902.7 s fatal is a deterministic beat of the modem's clock (9 fatals,
 * 9.7 ms total spread; ledger 197 item 43). Whether that beat is a COUNTER
 * that wraps, an always-on CLOCK, or a CONDITION met on a modem-local counter
 * cannot be decided from AP-side timestamps alone -- every AP-side quantity
 * (dmesg stamp, /proc/uptime) shares one clock. It needs the modem's clock.
 *
 * ATS_RTC (QMI TIME base 0, service 22) is the modem's own uptime counter in
 * milliseconds, and the ONLY instrument in this project that measures modem
 * uptime with no borrowed AP-side offset (Doc 172). This tool reads it.
 *
 * WHAT IT EMITS (one line per sample, machine-parseable)
 * -----------------------------------------------------
 *   ATS-PROBE ap_boot_ms=<n> ap_mono_ms=<n> rtc_ms=<n> tod_ms=<n> \
 *             tod_minus_rtc_ms=<n> rt_rtc_ms=<n> rt_tod_ms=<n> wall_ms=<n>
 *
 * ap_boot_ms is CLOCK_BOOTTIME, the clock /proc/uptime reports and the one
 * Doc 177 matched against dmesg to sub-ms on this (no-suspend) device. It is
 * the AP STAMP. ap_mono_ms is CLOCK_MONOTONIC, logged as a validity check:
 * on a device that never suspends the two are equal, and if they ever diverge
 * the log shows it rather than silently corrupting the correlation.
 *
 * wall_ms is INFORMATIONAL ONLY. It steps under NTP/settimeofday and must
 * NEVER be used as the anchor -- it is there so a human can line this file up
 * against logread output.
 *
 * rt_rtc_ms / rt_tod_ms bracket each QMI round trip. The true sample instant
 * lies in [ap_boot_ms, ap_boot_ms + rt_*_ms]; use the midpoint to halve the
 * error when converting a dmesg fatal stamp to modem uptime:
 *
 *     rtc_at_fatal = rtc_ms + (ap_fatal_boot_ms - (ap_boot_ms + rt_rtc_ms/2))
 *
 * which is valid because within one modem life ATS_RTC and the AP clock tick
 * 1:1 and their difference is constant to ~1 ms (Doc 172 4.1).
 *
 * READ-ONLY, AND WHY THAT IS A PROPERTY OF THE WIRE
 * -------------------------------------------------
 * The only message this program can send is QMI_TIME_GENOFF_GET_REQ (0x0021),
 * whose descriptor (qmi_time.c: time_genoff_get_req_ei) declares exactly one
 * TLV: type 0x01, 4 bytes, the base index. There is no field capable of
 * carrying a value, so no encoding of it can write to the modem. The 0x0020
 * SET (25 bytes = the same message plus an 8-byte offset TLV) is never built
 * here. This is the Doc 173 result; the descriptor is reused verbatim rather
 * than re-derived so it stays byte-identical to the verified one.
 *
 * SSR SURVIVAL
 * ------------
 * The daemon's own -p poll is gated on STATE_SYNCHRONIZED, so it goes dark
 * exactly at the SSR -- the moment under study. This tool has no state
 * machine: it watches for QRTR_TYPE_DEL_SERVER, re-issues qrtr_new_lookup,
 * and resumes the instant the modem's time service reappears. That is the
 * whole reason it is standalone rather than another flag on the daemon.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <stdbool.h>
#include <stdarg.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <signal.h>
#include <time.h>
#include <endian.h>
#include <poll.h>
#include <sys/socket.h>

#include <libqrtr.h>
#include "qmi_time.h"

#define QMI_TIME_SERVICE_ID	22
#define DEFAULT_INTERVAL_MS	1000
#define LOOKUP_RETRY_MS		5000
#define GET_TIMEOUT_MS		3000

/*
 * QMI header. libqrtr's qmi_encode_message() writes this layout; it is not
 * exported by libqrtr.h, so it is declared here as the daemon does.
 */
struct qmi_header {
	uint8_t type;
	uint16_t txn_id;
	uint16_t msg_id;
	uint16_t msg_len;
} __attribute__((packed));

static volatile sig_atomic_t running = 1;

static uint32_t modem_node;
static uint32_t modem_port;
static bool connected;
static uint16_t next_txn = 1;

static int interval_ms = DEFAULT_INTERVAL_MS;
static long max_samples;		/* 0 = run until signalled */
static const char *out_path;		/* NULL = stdout */
static bool quiet;
static FILE *out;

static void on_signal(int sig)
{
	(void)sig;
	running = 0;
}

/* CLOCK_BOOTTIME / CLOCK_MONOTONIC / CLOCK_REALTIME, in milliseconds. */
static uint64_t clk_ms(clockid_t id)
{
	struct timespec ts;

	if (clock_gettime(id, &ts) != 0)
		return 0;
	return (uint64_t)ts.tv_sec * 1000ULL + (uint64_t)ts.tv_nsec / 1000000ULL;
}

static void emit(const char *fmt, ...)
{
	va_list ap;

	va_start(ap, fmt);
	vfprintf(out, fmt, ap);
	va_end(ap);
	fputc('\n', out);
	fflush(out);
}

/*
 * Handle a QRTR control packet. NEW_SERVER for service 22 is how the modem's
 * QMI TIME port is learned; DEL_SERVER is how an SSR is detected. Called both
 * from the idle loop and from inside a transaction, so a restart that lands
 * mid-round-trip is still noticed.
 */
static void handle_control(int sock, void *buf, size_t len,
			   const struct sockaddr_qrtr *sq)
{
	struct qrtr_packet pkt;

	if (qrtr_decode(&pkt, buf, len, sq) < 0)
		return;

	if (pkt.type == QRTR_TYPE_NEW_SERVER) {
		if (pkt.service != QMI_TIME_SERVICE_ID)
			return;
		if (connected && pkt.node == modem_node && pkt.port == modem_port)
			return;
		modem_node = pkt.node;
		modem_port = pkt.port;
		connected = true;
		emit("# ATS-PROBE-EVENT what=discovered node=%u port=%u service=%u ap_boot_ms=%llu",
		     pkt.node, pkt.port, pkt.service, (unsigned long long)clk_ms(CLOCK_BOOTTIME));
	} else if (pkt.type == QRTR_TYPE_DEL_SERVER) {
		if (pkt.node != modem_node || pkt.port != modem_port)
			return;
		connected = false;
		modem_port = 0;
		emit("# ATS-PROBE-EVENT what=ssr ap_boot_ms=%llu",
		     (unsigned long long)clk_ms(CLOCK_BOOTTIME));
		/* Re-arm discovery; the modem will re-announce when it is back. */
		qrtr_new_lookup(sock, QMI_TIME_SERVICE_ID, 0, 0);
	}
}

/* Drain everything currently queued on the socket. */
static void drain(int sock)
{
	for (;;) {
		char buf[4096];
		struct sockaddr_qrtr sq;
		socklen_t sl = sizeof(sq);
		ssize_t n = recvfrom(sock, buf, sizeof(buf), MSG_DONTWAIT,
				     (struct sockaddr *)&sq, &sl);
		if (n <= 0)
			return;
		handle_control(sock, buf, n, &sq);
	}
}

/*
 * One synchronous 0x0021 GET. Returns 0 and sets *val_out on success.
 * Sends the base index and nothing else -- see the file header.
 */
static int qmi_get(int sock, uint32_t base, uint64_t *val_out)
{
	char tx[256];
	char rx[4096];
	struct qrtr_packet txp;
	struct time_genoff_get_req req;
	struct time_genoff_get_resp resp;
	struct pollfd pfd;
	struct timespec start, now;
	uint16_t txn = next_txn++;
	ssize_t enc;
	int ret;

	if (next_txn == 0)
		next_txn = 1;

	memset(&req, 0, sizeof(req));
	req.base = base;

	txp.data = tx;
	txp.data_len = sizeof(tx);
	enc = qmi_encode_message(&txp, QMI_REQUEST, QMI_TIME_GENOFF_GET_REQ,
				 txn, &req, time_genoff_get_req_ei);
	if (enc < 0)
		return -EINVAL;

	ret = qrtr_sendto(sock, modem_node, modem_port, tx, enc);
	if (ret < 0)
		return -errno;

	pfd.fd = sock;
	pfd.events = POLLIN;
	clock_gettime(CLOCK_MONOTONIC, &start);

	for (;;) {
		int elapsed, remaining;

		clock_gettime(CLOCK_MONOTONIC, &now);
		elapsed = (now.tv_sec - start.tv_sec) * 1000 +
			  (now.tv_nsec - start.tv_nsec) / 1000000;
		remaining = GET_TIMEOUT_MS - elapsed;
		if (remaining <= 0)
			return -ETIMEDOUT;

		ret = poll(&pfd, 1, remaining);
		if (ret <= 0) {
			if (ret < 0 && errno == EINTR)
				continue;
			return -ETIMEDOUT;
		}

		{
			struct sockaddr_qrtr sq;
			socklen_t sl = sizeof(sq);
			struct qrtr_packet rp;
			ssize_t rl = recvfrom(sock, rx, sizeof(rx), 0,
					      (struct sockaddr *)&sq, &sl);

			if (rl <= 0)
				continue;
			if (qrtr_decode(&rp, rx, rl, &sq) < 0)
				continue;

			if (rp.type == QRTR_TYPE_DATA &&
			    sq.sq_node == modem_node && sq.sq_port == modem_port &&
			    rp.data_len >= sizeof(struct qmi_header)) {
				const struct qmi_header *h = rp.data;

				if (h->type == QMI_RESPONSE &&
				    le16toh(h->txn_id) == txn &&
				    le16toh(h->msg_id) == QMI_TIME_GENOFF_GET_REQ) {
					unsigned int dtxn;

					memset(&resp, 0, sizeof(resp));
					if (qmi_decode_message(&resp, &dtxn, &rp,
							       QMI_RESPONSE,
							       QMI_TIME_GENOFF_GET_REQ,
							       time_genoff_get_resp_ei) < 0)
						return -EBADMSG;
					if (resp.result.result != QMI_RESULT_SUCCESS_V01 ||
					    resp.result.error != QMI_ERR_NONE_V01)
						return -EIO;
					*val_out = resp.offset;
					return 0;
				}
			}

			/* Not our answer: a control packet or an indication. */
			handle_control(sock, rx, rl, &sq);
			if (!connected)
				return -ENOTCONN;
		}
	}
}

static void usage(const char *argv0)
{
	fprintf(stderr,
		"Usage: %s [-i interval_ms] [-n count] [-o file] [-q]\n"
		"  -i  sampling interval in ms (default %d)\n"
		"  -n  stop after this many samples (default 0 = until signalled)\n"
		"  -o  append-safe output file (default stdout)\n"
		"  -q  suppress the human-readable startup banner\n"
		"\n"
		"Reads the modem's own ATS_RTC uptime over QMI TIME (service %d) and\n"
		"stamps each read with the AP's CLOCK_BOOTTIME. Sends only the 14-byte\n"
		"0x0021 GET; never writes to the modem.\n",
		argv0, DEFAULT_INTERVAL_MS, QMI_TIME_SERVICE_ID);
}

int main(int argc, char **argv)
{
	struct pollfd pfd;
	uint64_t last_lookup = 0;
	uint64_t next_due = 0;
	long samples = 0;
	int sock, opt;

	while ((opt = getopt(argc, argv, "i:n:o:qh")) != -1) {
		switch (opt) {
		case 'i':
			interval_ms = atoi(optarg);
			if (interval_ms < 1)
				interval_ms = 1;
			break;
		case 'n':
			max_samples = atol(optarg);
			break;
		case 'o':
			out_path = optarg;
			break;
		case 'q':
			quiet = true;
			break;
		default:
			usage(argv[0]);
			return 1;
		}
	}

	if (out_path) {
		out = fopen(out_path, "a");
		if (!out) {
			fprintf(stderr, "ats-probe: cannot open %s: %s\n",
				out_path, strerror(errno));
			return 1;
		}
	} else {
		out = stdout;
	}
	setvbuf(out, NULL, _IOLBF, 0);

	signal(SIGINT, on_signal);
	signal(SIGTERM, on_signal);
	signal(SIGPIPE, SIG_IGN);

	sock = qrtr_open(0);
	if (sock < 0) {
		fprintf(stderr, "ats-probe: qrtr_open: %s\n", strerror(errno));
		return 1;
	}

	emit("# ATS-PROBE v1 interval_ms=%d service=%d started_wall_ms=%llu",
	     interval_ms, QMI_TIME_SERVICE_ID,
	     (unsigned long long)clk_ms(CLOCK_REALTIME));

	if (!quiet)
		fprintf(stderr, "ats-probe: polling ATS_RTC every %d ms; "
			"output -> %s\n", interval_ms,
			out_path ? out_path : "stdout");

	if (qrtr_new_lookup(sock, QMI_TIME_SERVICE_ID, 0, 0) < 0)
		fprintf(stderr, "ats-probe: qrtr_new_lookup: %s\n", strerror(errno));

	pfd.fd = sock;
	pfd.events = POLLIN;

	while (running) {
		uint64_t now = clk_ms(CLOCK_BOOTTIME);
		int timeout;

		if (!connected) {
			if (now - last_lookup >= LOOKUP_RETRY_MS) {
				last_lookup = now;
				qrtr_new_lookup(sock, QMI_TIME_SERVICE_ID, 0, 0);
			}
			poll(&pfd, 1, 1000);
			if (pfd.revents & POLLIN)
				drain(sock);
			continue;
		}

		if (now >= next_due) {
			uint64_t t0 = clk_ms(CLOCK_BOOTTIME);
			uint64_t m0 = clk_ms(CLOCK_MONOTONIC);
			uint64_t rtc = 0, tod = 0;
			uint64_t t1, t2;
			int r1, r2;

			r1 = qmi_get(sock, ATS_RTC, &rtc);
			t1 = clk_ms(CLOCK_BOOTTIME);
			r2 = qmi_get(sock, ATS_TOD, &tod);
			t2 = clk_ms(CLOCK_BOOTTIME);

			if (r1 != 0) {
				emit("ATS-PROBE-ERR ap_boot_ms=%llu what=rtc ret=%d",
				     (unsigned long long)t0, r1);
			} else {
				emit("ATS-PROBE ap_boot_ms=%llu ap_mono_ms=%llu "
				     "rtc_ms=%llu tod_ms=%llu tod_minus_rtc_ms=%lld "
				     "rt_rtc_ms=%llu rt_tod_ms=%llu wall_ms=%llu",
				     (unsigned long long)t0,
				     (unsigned long long)m0,
				     (unsigned long long)rtc,
				     r2 == 0 ? (unsigned long long)tod : 0ULL,
				     r2 == 0 ? (long long)(tod - rtc) : -1LL,
				     (unsigned long long)(t1 - t0),
				     (unsigned long long)(t2 - t1),
				     (unsigned long long)clk_ms(CLOCK_REALTIME));
			}

			samples++;
			if (max_samples && samples >= max_samples)
				break;

			/*
			 * A failed GET means the link may be gone (SSR). Drop the
			 * connection so discovery re-runs; a transient timeout costs
			 * one re-lookup, which is cheap.
			 */
			if (r1 != 0 && r2 != 0) {
				connected = false;
				last_lookup = 0;
			}

			next_due = t0 + (uint64_t)interval_ms;
			now = clk_ms(CLOCK_BOOTTIME);
			if (next_due < now)
				next_due = now;
		}

		timeout = (int)(next_due - clk_ms(CLOCK_BOOTTIME));
		if (timeout < 0)
			timeout = 0;
		poll(&pfd, 1, timeout);
		if (pfd.revents & POLLIN)
			drain(sock);
	}

	emit("# ATS-PROBE-EVENT what=exit samples=%ld ap_boot_ms=%llu",
	     samples, (unsigned long long)clk_ms(CLOCK_BOOTTIME));

	if (out_path)
		fclose(out);
	return 0;
}
