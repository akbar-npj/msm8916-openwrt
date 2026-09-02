/*
 * Qualcomm QMI Time Synchronization & Keepalive Daemon
 *
 * Replaces proprietary Android time_daemon on Qualcomm MSM8916 / Snapdragon 410.
 * Keeps modem baseband SCLK (Sleep Clock) and ATS (Accuracy Time Source)
 * synchronized with host AP, preventing the 900-second (15 minute)
 * lte_ml1_sleepmgr_stm DRX sleep crash without requiring firmware binary patches.
 *
 * Reverse-engineered from stock Android time_daemon (Qualcomm DPM.1.0)
 *
 * Copyright (c) 2026 OpenWrt MSM8916 Project
 * SPDX-License-Identifier: BSD-3-Clause
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <stdbool.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <signal.h>
#include <syslog.h>
#include <time.h>
#include <fcntl.h>
#include <sys/time.h>
#include <sys/socket.h>
#include <sys/ioctl.h>
#include <linux/rtc.h>
#include <poll.h>

#include <libqrtr.h>
#include "qmi_time.h"

#define DEFAULT_SYNC_INTERVAL_SEC	60
#define GPS_EPOCH_OFFSET_MS		315964800000ULL

static volatile sig_atomic_t running = 1;
static bool verbose = false;
static int sync_interval = DEFAULT_SYNC_INTERVAL_SEC;

static uint32_t modem_node = 0;
static uint32_t modem_port = 0;
static bool modem_connected = false;
static uint16_t next_txn_id = 1;

static void sig_handler(int sig)
{
	(void)sig;
	running = 0;
}

static uint64_t get_rtc_time_ms(void)
{
	struct rtc_time rt;
	struct tm t;
	int fd;
	time_t rtc_sec;

	fd = open("/dev/rtc0", O_RDONLY);
	if (fd < 0)
		return 0;

	if (ioctl(fd, RTC_RD_TIME, &rt) < 0) {
		close(fd);
		return 0;
	}
	close(fd);

	memset(&t, 0, sizeof(t));
	t.tm_sec = rt.tm_sec;
	t.tm_min = rt.tm_min;
	t.tm_hour = rt.tm_hour;
	t.tm_mday = rt.tm_mday;
	t.tm_mon = rt.tm_mon;
	t.tm_year = rt.tm_year;

	rtc_sec = timegm(&t);
	if (rtc_sec < 0)
		return 0;

	return (uint64_t)rtc_sec * 1000ULL;
}

static uint64_t get_genoff_ms(void)
{
	struct timespec ts;
	uint64_t ap_ms;
	uint64_t rtc_ms;

	clock_gettime(CLOCK_REALTIME, &ts);
	ap_ms = ((uint64_t)ts.tv_sec * 1000ULL) + ((uint64_t)ts.tv_nsec / 1000000ULL);
	rtc_ms = get_rtc_time_ms();

	/*
	 * Formula from stock Qualcomm time_daemon (FUN_000113d0):
	 * genoff = (ap_time_ms - GPS_EPOCH_OFFSET_MS) - rtc_ms
	 */
	if (ap_ms >= GPS_EPOCH_OFFSET_MS)
		return (ap_ms - GPS_EPOCH_OFFSET_MS) - rtc_ms;
	else
		return ap_ms - rtc_ms;
}

static int send_qmi_time_set(int sock, uint32_t node, uint32_t port,
			    enum time_genoff_base base, uint64_t offset_ms)
{
	struct time_genoff_set_req req;
	struct qrtr_packet pkt;
	char buf[128];
	ssize_t len;
	int ret;

	memset(&req, 0, sizeof(req));
	req.base = base;
	req.unit = TIME_UNIT_MSEC;
	req.offset = offset_ms;

	pkt.data = buf;
	pkt.data_len = sizeof(buf);

	len = qmi_encode_message(&pkt, QMI_REQUEST, QMI_TIME_GENOFF_SET_REQ,
				 next_txn_id++, &req, time_genoff_set_req_ei);
	if (len < 0) {
		syslog(LOG_ERR, "Failed to encode QMI_TIME_GENOFF_SET_REQ: %zd", len);
		return -1;
	}

	ret = qrtr_sendto(sock, node, port, buf, len);
	if (ret < 0) {
		syslog(LOG_ERR, "Failed to send QMI_TIME_GENOFF_SET_REQ to %u:%u: %d (%s)",
		       node, port, errno, strerror(errno));
		return -1;
	}

	syslog(LOG_INFO, "Sent time sync (base=%d, genoff=%llu ms) to %u:%u",
	       base, (unsigned long long)offset_ms, node, port);

	return 0;
}

static int send_qmi_time_get(int sock, uint32_t node, uint32_t port,
			    enum time_genoff_base base)
{
	struct time_genoff_get_req req;
	struct qrtr_packet pkt;
	char buf[64];
	ssize_t len;
	int ret;

	memset(&req, 0, sizeof(req));
	req.base = base;

	pkt.data = buf;
	pkt.data_len = sizeof(buf);

	len = qmi_encode_message(&pkt, QMI_REQUEST, QMI_TIME_GENOFF_GET_REQ,
				 next_txn_id++, &req, time_genoff_get_req_ei);
	if (len < 0) {
		syslog(LOG_ERR, "Failed to encode QMI_TIME_GENOFF_GET_REQ: %zd", len);
		return -1;
	}

	ret = qrtr_sendto(sock, node, port, buf, len);
	if (ret < 0) {
		syslog(LOG_ERR, "Failed to send QMI_TIME_GENOFF_GET_REQ to %u:%u: %d (%s)",
		       node, port, errno, strerror(errno));
		return -1;
	}

	if (verbose) {
		syslog(LOG_INFO, "Sent time get request (base=%d) to %u:%u", base, node, port);
	}

	return 0;
}

static int send_qmi_reg_ind(int sock, uint32_t node, uint32_t port)
{
	struct time_reg_ind_req req;
	struct qrtr_packet pkt;
	char buf[64];
	ssize_t len;
	int ret;

	memset(&req, 0, sizeof(req));
	req.register_indications = 1;

	pkt.data = buf;
	pkt.data_len = sizeof(buf);

	len = qmi_encode_message(&pkt, QMI_REQUEST, QMI_TIME_REG_IND_REQ,
				 next_txn_id++, &req, time_reg_ind_req_ei);
	if (len < 0) {
		syslog(LOG_ERR, "Failed to encode QMI_TIME_REG_IND_REQ: %zd", len);
		return -1;
	}

	ret = qrtr_sendto(sock, node, port, buf, len);
	if (ret < 0) {
		syslog(LOG_ERR, "Failed to send QMI_TIME_REG_IND_REQ to %u:%u: %d (%s)",
		       node, port, errno, strerror(errno));
		return -1;
	}

	syslog(LOG_INFO, "Registered for time indications on %u:%u", node, port);
	return 0;
}

static uint64_t last_synced_genoff = 0;

static int perform_time_sync(int sock, bool force_set)
{
	uint64_t genoff_ms;
	int ret = 0;

	if (!modem_connected || modem_port == 0)
		return -1;

	genoff_ms = get_genoff_ms();

	/*
	 * Stock Android time_daemon sets ATS_USER (base 2) offset once at init.
	 * Constantly overwriting the offset during active LTE sessions forces
	 * LTE Layer 1 SFN resync which drops packet data calls.
	 * Only update if forced or if the clock stepped by > 5 seconds.
	 */
	int64_t diff = (int64_t)genoff_ms - (int64_t)last_synced_genoff;
	if (diff < 0) diff = -diff;

	if (force_set || diff > 5000) {
		ret = send_qmi_time_set(sock, modem_node, modem_port, ATS_USER, genoff_ms);
		if (ret == 0)
			last_synced_genoff = genoff_ms;
	}

	/* Periodic keepalive: Poll ATS_TOD (base 1) get request */
	send_qmi_time_get(sock, modem_node, modem_port, ATS_TOD);

	return ret;
}

static void handle_qrtr_packet(int sock, void *buf, size_t len,
			      const struct sockaddr_qrtr *sq)
{
	struct qrtr_packet pkt;
	unsigned int msg_id;
	int ret;

	ret = qrtr_decode(&pkt, buf, len, sq);
	if (ret < 0)
		return;

	if (pkt.type == QRTR_TYPE_NEW_SERVER) {
		if (pkt.service == QMI_TIME_SERVICE_ID) {
			syslog(LOG_INFO, "Discovered QMI TIME service on node %u, port %u (instance %u)",
			       pkt.node, pkt.port, pkt.instance);
			modem_node = pkt.node;
			modem_port = pkt.port;
			modem_connected = true;

			/* Subscribe to modem indications and send initial time sync */
			send_qmi_reg_ind(sock, modem_node, modem_port);
			perform_time_sync(sock, true);
		}
	} else if (pkt.type == QRTR_TYPE_DEL_SERVER) {
		if (pkt.node == modem_node && pkt.port == modem_port) {
			syslog(LOG_WARNING, "QMI TIME service disconnected from node %u, port %u",
			       pkt.node, pkt.port);
			modem_connected = false;
			modem_port = 0;
		}
	} else if (pkt.type == QRTR_TYPE_DATA) {
		ret = qmi_decode_header(&pkt, &msg_id);
		if (ret < 0)
			return;

		if (pkt.type == QMI_RESPONSE) {
			if (msg_id == QMI_TIME_GENOFF_SET_REQ) {
				struct time_genoff_set_resp resp;
				unsigned int txn;
				ret = qmi_decode_message(&resp, &txn, &pkt,
							 QMI_RESPONSE, msg_id,
							 time_genoff_set_resp_ei);
				if (ret >= 0 && verbose) {
					syslog(LOG_DEBUG, "QMI_TIME_GENOFF_SET_RESP result=%u error=%u",
					       resp.result.result, resp.result.error);
				}
			} else if (msg_id == QMI_TIME_GENOFF_GET_REQ) {
				struct time_genoff_get_resp resp;
				unsigned int txn;
				ret = qmi_decode_message(&resp, &txn, &pkt,
							 QMI_RESPONSE, msg_id,
							 time_genoff_get_resp_ei);
				if (ret >= 0 && verbose) {
					syslog(LOG_DEBUG, "QMI_TIME_GENOFF_GET_RESP result=%u error=%u base=%u offset=%llu",
					       resp.result.result, resp.result.error,
					       resp.base, (unsigned long long)resp.offset);
				}
			}
		} else if (pkt.type == QMI_INDICATION) {
			if (msg_id == QMI_TIME_TOD_IND) {
				struct time_tod_ind ind;
				unsigned int txn;
				ret = qmi_decode_message(&ind, &txn, &pkt,
							 QMI_INDICATION, msg_id,
							 time_tod_ind_ei);
				if (ret >= 0) {
					syslog(LOG_INFO, "Received TOD update indication from modem: base=%u offset=%llu ms",
					       ind.base, (unsigned long long)ind.offset);
				}
			}
		}
	}
}

int main(int argc, char *argv[])
{
	struct pollfd pfd;
	char buf[4096];
	time_t last_sync = 0;
	int opt;
	int sock;
	int ret;

	while ((opt = getopt(argc, argv, "vi:")) != -1) {
		switch (opt) {
		case 'v':
			verbose = true;
			break;
		case 'i':
			sync_interval = atoi(optarg);
			if (sync_interval < 5)
				sync_interval = 5;
			break;
		default:
			fprintf(stderr, "Usage: %s [-v] [-i <interval_sec>]\n", argv[0]);
			return 1;
		}
	}

	openlog("qcom-time-daemon", LOG_PID | LOG_CONS, LOG_DAEMON);
	syslog(LOG_INFO, "Starting Qualcomm QMI Time Synchronization Daemon (interval=%ds)",
	       sync_interval);

	signal(SIGINT, sig_handler);
	signal(SIGTERM, sig_handler);

	sock = qrtr_open(0);
	if (sock < 0) {
		syslog(LOG_ERR, "Failed to open QRTR socket: %d (%s)", errno, strerror(errno));
		closelog();
		return 1;
	}

	/* Look up QMI TIME service on QRTR (wildcard version=0, instance=0) */
	ret = qrtr_new_lookup(sock, QMI_TIME_SERVICE_ID, 0, 0);
	if (ret < 0) {
		syslog(LOG_WARNING, "Failed to register QRTR lookup for time service: %d (%s)",
		       errno, strerror(errno));
	}

	last_sync = time(NULL);

	pfd.fd = sock;
	pfd.events = POLLIN;

	while (running) {
		time_t now = time(NULL);

		/* Check if we need to send periodic keepalive */
		if (modem_connected && (now - last_sync >= sync_interval)) {
			perform_time_sync(sock, false);
			last_sync = now;
		}

		/* Poll for incoming QRTR messages with 1-second timeout */
		ret = poll(&pfd, 1, 1000);
		if (ret < 0) {
			if (errno == EINTR)
				continue;
			syslog(LOG_ERR, "Poll error on QRTR socket: %d (%s)", errno, strerror(errno));
			break;
		}

		if (ret > 0 && (pfd.revents & POLLIN)) {
			struct sockaddr_qrtr sq;
			socklen_t sl = sizeof(sq);
			ssize_t len = recvfrom(sock, buf, sizeof(buf), 0, (struct sockaddr *)&sq, &sl);
			if (len > 0) {
				handle_qrtr_packet(sock, buf, len, &sq);
			}
		}
	}

	syslog(LOG_INFO, "Shutting down Qualcomm QMI Time Daemon");
	qrtr_close(sock);
	closelog();

	return 0;
}
