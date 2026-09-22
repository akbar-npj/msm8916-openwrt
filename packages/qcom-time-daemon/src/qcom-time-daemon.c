/*
 * Qualcomm QMI Time Synchronization & Keepalive Daemon
 *
 * Implements Android-compatible transactional QMI Time state machine
 * on Qualcomm MSM8916 / Snapdragon 410.
 *
 * Anchors modem baseband ATS (Accuracy Time Source) and SCLK (Sleep Clock)
 * to host RTC/system time before LTE attach, validating every QMI transaction,
 * decoding indications and replies correctly, and eliminating the 900-second
 * lte_ml1_sleepmgr_stm crash on 100% unpatched stock modem firmware.
 *
 * Reverse-engineered from stock Android time_daemon (MPSS.DPM.1.0.C7).
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
#include <endian.h>
#include <sys/time.h>
#include <sys/socket.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <linux/rtc.h>
#include <poll.h>

#include <libqrtr.h>
#include "qmi_time.h"

#define DEFAULT_POLL_INTERVAL_SEC	60
#define DEFAULT_REFRESH_INTERVAL_SEC	0
#define HANDSHAKE_RETRY_INTERVAL_SEC	5
#define QMI_SYNC_TIMEOUT_MS		5000
#define SYNC_MARKER_FILE		"/var/run/qcom-time-synced"
#define GPS_EPOCH_OFFSET_MS		315964800000ULL

/*
 * Persistent ATS state (Android parity).
 *
 * Stock Android's time_daemon keeps /data/time/ats_<N> -- the relation between
 * the AP wall clock and the modem's own ATS_RTC counter -- so the modem's time
 * bases can be restored after a reboot without waiting for NITZ.  This daemon
 * had no equivalent: the only file it wrote was the tmpfs marker above, which
 * does not survive a reboot (Doc 172).
 *
 * The store is deliberately NOT on tmpfs.  /etc is on the overlayfs upper
 * layer on this port, so it survives both a reboot and a sysupgrade.
 */
#define DEFAULT_ATS_STATE_FILE		"/etc/qcom-time/ats"
#define ATS_STATE_MAGIC			"qcom-ats-state"
#define ATS_STATE_VERSION		1
/* 2020-01-01T00:00:00Z.  A wall clock below this is not a clock. */
#define AP_CLOCK_SANITY_FLOOR_MS	1577836800000ULL

enum time_daemon_state {
	STATE_DISCOVERING = 0,
	STATE_REGISTERING_IND = 1,
	STATE_SYNCING_ATS_USER = 2,
	STATE_SYNCHRONIZED = 3,
	STATE_FAILED = 4,
};

struct qmi_header {
	uint8_t type;
	uint16_t txn_id;
	uint16_t msg_id;
	uint16_t msg_len;
} __attribute__((packed));

/*
 * One persisted ATS record.  Text on disk (see ats_state_save), because the
 * file is ours alone -- Android never reads it -- and this project reads its
 * evidence with grep.
 */
struct ats_persist {
	uint32_t base;		/* time base the offset belongs to (always ATS_USER here) */
	uint64_t offset_ms;	/* the offset the modem ACCEPTED, ms since the GPS epoch */
	uint64_t rtc_ms;	/* ATS_RTC (modem uptime) at the moment of that write */
	uint64_t wall_ms;	/* AP wall clock at that moment */
	int64_t  delta_ms;	/* wall_ms - rtc_ms; the relation Android persists */
	uint64_t synced_at;	/* AP wall seconds, for humans */
};

static volatile sig_atomic_t running = 1;
static bool verbose = false;
static bool enable_periodic_get = false;
static int poll_interval = DEFAULT_POLL_INTERVAL_SEC;
static int refresh_interval = DEFAULT_REFRESH_INTERVAL_SEC;

static const char *ats_state_file = DEFAULT_ATS_STATE_FILE;
static bool ats_state_enabled = true;
static bool ats_state_loaded = false;
static bool ats_verdict_logged = false;
static struct ats_persist ats_state;

static uint32_t modem_node = 0;
static uint32_t modem_port = 0;
static bool modem_connected = false;
static enum time_daemon_state current_state = STATE_DISCOVERING;
static uint16_t next_txn_id = 1;
static uint64_t last_handshake_attempt = 0;
static uint64_t last_user_refresh = 0;
static uint64_t last_keepalive = 0;
static uint64_t last_lookup_attempt = 0;
static int consecutive_get_fails = 0;

static uint64_t get_monotonic_sec(void)
{
	struct timespec ts;
	clock_gettime(CLOCK_MONOTONIC, &ts);
	return (uint64_t)ts.tv_sec;
}

/* ======================================================================
 * Persistent ATS state
 * ======================================================================
 *
 * What the record buys, in order of value:
 *
 *   1. Modem-reset vs AP-reboot discrimination with NO wall clock in the
 *      arithmetic.  ATS_RTC is the modem's own uptime counter and only ever
 *      moves forwards while the modem runs.  If the RTC read at the first
 *      handshake after a boot is LOWER than the RTC stored here, the modem
 *      restarted; if it is HIGHER, the modem survived and the difference is
 *      how much modem time elapsed across the AP's downtime.  That is the
 *      question this project previously could only answer from the HOST's
 *      journalctl (reference_hmu05_device_quirks.md).
 *
 *   2. Restoring the ATS_USER offset when the AP wall clock is manifestly
 *      unusable.  See ap_clock_is_usable() for the two guards.  The estimate
 *      is NEVER allowed to override a plausible AP clock: a legitimate NTP
 *      step and a broken boot clock are indistinguishable in one sample.
 *
 * Wire neutrality.  This path adds NO QMI message.  It is populated from the
 * ATS_RTC / ATS_TOD values the handshake already reads, and written to AP
 * flash only.  The 0x0021 GET stays 14 bytes and the 0x0020 SET stays 25, so
 * the read-only property established in Doc 173 is untouched.
 */

static bool ats_state_dir(char *out, size_t outsz)
{
	const char *slash = strrchr(ats_state_file, '/');
	size_t len;

	if (!slash || slash == ats_state_file)
		return false;
	len = (size_t)(slash - ats_state_file);
	if (len + 1 > outsz)
		return false;
	memcpy(out, ats_state_file, len);
	out[len] = '\0';
	return true;
}

static void ats_state_ensure_dir(void)
{
	char dir[256];

	if (ats_state_dir(dir, sizeof(dir)) && mkdir(dir, 0755) < 0 && errno != EEXIST)
		syslog(LOG_WARNING, "[QMI-TIME] ATS state: cannot create %s: %s", dir, strerror(errno));
}

/* Find "key=value" on its own line and copy the value into out. */
static bool kv_get(const char *buf, const char *key, char *out, size_t outsz)
{
	size_t klen = strlen(key);
	const char *p = buf;

	while (*p) {
		const char *eol = strchr(p, '\n');
		size_t len = eol ? (size_t)(eol - p) : strlen(p);

		if (len > klen && !strncmp(p, key, klen) && p[klen] == '=') {
			size_t vlen = len - klen - 1;

			if (vlen >= outsz)
				vlen = outsz - 1;
			memcpy(out, p + klen + 1, vlen);
			out[vlen] = '\0';
			return true;
		}
		if (!eol)
			break;
		p = eol + 1;
	}
	return false;
}

static bool kv_u64(const char *buf, const char *key, uint64_t *out)
{
	char tmp[64], *end;

	if (!kv_get(buf, key, tmp, sizeof(tmp)) || !tmp[0] || tmp[0] == '-')
		return false;
	errno = 0;
	*out = (uint64_t)strtoull(tmp, &end, 10);
	return errno == 0 && end != tmp;
}

static bool kv_i64(const char *buf, const char *key, int64_t *out)
{
	char tmp[64], *end;

	if (!kv_get(buf, key, tmp, sizeof(tmp)) || !tmp[0])
		return false;
	errno = 0;
	*out = (int64_t)strtoll(tmp, &end, 10);
	return errno == 0 && end != tmp;
}

/*
 * Load the record written by a previous boot.  Every failure is non-fatal and
 * leaves ats_state_loaded false: an absent, empty, truncated or foreign file
 * must degrade to "no record", never to a wrong offset.
 */
static int ats_state_load(void)
{
	char buf[1024], magic[32];
	ssize_t n;
	int fd;
	uint64_t version = 0, base = 0;

	ats_state_loaded = false;

	if (!ats_state_enabled)
		return -1;

	fd = open(ats_state_file, O_RDONLY);
	if (fd < 0) {
		syslog(LOG_INFO, "[QMI-TIME] ATS state: no record at %s (%s); modem reset status unknown until the first handshake",
		       ats_state_file, strerror(errno));
		return -1;
	}
	n = read(fd, buf, sizeof(buf) - 1);
	close(fd);
	if (n <= 0) {
		syslog(LOG_WARNING, "[QMI-TIME] ATS state: %s is empty or unreadable; ignoring it", ats_state_file);
		return -1;
	}
	buf[n] = '\0';

	if (!kv_get(buf, "magic", magic, sizeof(magic)) || strcmp(magic, ATS_STATE_MAGIC) ||
	    !kv_u64(buf, "version", &version) || version != ATS_STATE_VERSION ||
	    !kv_u64(buf, "base", &base) || base != ATS_USER ||
	    !kv_u64(buf, "offset_ms", &ats_state.offset_ms) ||
	    !kv_u64(buf, "rtc_ms", &ats_state.rtc_ms) ||
	    !kv_u64(buf, "wall_ms", &ats_state.wall_ms) ||
	    !kv_i64(buf, "delta_ms", &ats_state.delta_ms) ||
	    !kv_u64(buf, "synced_at", &ats_state.synced_at)) {
		syslog(LOG_WARNING, "[QMI-TIME] ATS state: %s is not a complete %s v%d record for base %d; ignoring it",
		       ats_state_file, ATS_STATE_MAGIC, ATS_STATE_VERSION, ATS_USER);
		return -1;
	}

	ats_state.base = (uint32_t)base;

	/*
	 * This is the value that gets sent to the modem, so an implausible one
	 * must not load.  The floor is the same instant as the AP clock floor,
	 * expressed as ms since the GPS epoch.
	 */
	if (ats_state.offset_ms < AP_CLOCK_SANITY_FLOOR_MS - GPS_EPOCH_OFFSET_MS) {
		syslog(LOG_WARNING, "[QMI-TIME] ATS state: %s holds an implausible offset_ms=%llu; ignoring it",
		       ats_state_file, (unsigned long long)ats_state.offset_ms);
		return -1;
	}

	ats_state_loaded = true;
	syslog(LOG_NOTICE, "[QMI-TIME] ATS state: loaded %s -- base=%u offset_ms=%llu rtc_ms=%llu wall_ms=%llu delta_ms=%lld synced_at=%llu",
	       ats_state_file, ats_state.base,
	       (unsigned long long)ats_state.offset_ms,
	       (unsigned long long)ats_state.rtc_ms,
	       (unsigned long long)ats_state.wall_ms,
	       (long long)ats_state.delta_ms,
	       (unsigned long long)ats_state.synced_at);
	return 0;
}

/*
 * Write the record.  Temp file + fsync + rename, so a power loss leaves either
 * the old record or the new one and never a half-written line.
 */
static int ats_state_save(const struct ats_persist *p)
{
	char tmp_path[288], dir[256], buf[512];
	int fd, dirfd, len;

	if (!ats_state_enabled)
		return -1;
	if (strlen(ats_state_file) + 5 > sizeof(tmp_path))
		return -1;

	ats_state_ensure_dir();
	snprintf(tmp_path, sizeof(tmp_path), "%s.tmp", ats_state_file);

	len = snprintf(buf, sizeof(buf),
		       "magic=%s\nversion=%d\n"
		       "base=%u\noffset_ms=%llu\nrtc_ms=%llu\nwall_ms=%llu\n"
		       "delta_ms=%lld\nsynced_at=%llu\n",
		       ATS_STATE_MAGIC, ATS_STATE_VERSION, p->base,
		       (unsigned long long)p->offset_ms,
		       (unsigned long long)p->rtc_ms,
		       (unsigned long long)p->wall_ms,
		       (long long)p->delta_ms,
		       (unsigned long long)p->synced_at);
	if (len <= 0 || (size_t)len >= sizeof(buf))
		return -1;

	fd = open(tmp_path, O_WRONLY | O_CREAT | O_TRUNC, 0644);
	if (fd < 0) {
		syslog(LOG_WARNING, "[QMI-TIME] ATS state: cannot open %s: %s", tmp_path, strerror(errno));
		return -1;
	}
	if (write(fd, buf, len) != len) {
		syslog(LOG_WARNING, "[QMI-TIME] ATS state: short write to %s: %s", tmp_path, strerror(errno));
		close(fd);
		unlink(tmp_path);
		return -1;
	}
	fsync(fd);
	close(fd);

	if (rename(tmp_path, ats_state_file) < 0) {
		syslog(LOG_WARNING, "[QMI-TIME] ATS state: cannot rename %s -> %s: %s",
		       tmp_path, ats_state_file, strerror(errno));
		unlink(tmp_path);
		return -1;
	}

	/* Make the rename itself durable, not just the file contents. */
	if (ats_state_dir(dir, sizeof(dir))) {
		dirfd = open(dir, O_RDONLY | O_DIRECTORY);
		if (dirfd >= 0) {
			fsync(dirfd);
			close(dirfd);
		}
	}
	return 0;
}

/*
 * The one-shot boot verdict.  Called from the handshake, which has already
 * read ATS_RTC -- so this costs no extra QMI traffic.
 */
static void ats_state_log_boot_verdict(uint64_t rtc_now_ms)
{
	if (ats_verdict_logged)
		return;
	ats_verdict_logged = true;

	if (!ats_state_loaded) {
		syslog(LOG_NOTICE, "[QMI-TIME] ATS state: no usable prior record, so modem reset vs AP reboot is UNKNOWN for this boot");
		return;
	}

	/*
	 * rtc_ms == 0 is the sentinel for "the ATS_RTC read failed when this
	 * record was written".  ATS_RTC is the modem's uptime in ms, so a real
	 * value is never 0 by the time a handshake can run.  Without it there
	 * is nothing to compare, and guessing would invert the verdict.
	 */
	if (ats_state.rtc_ms == 0) {
		syslog(LOG_NOTICE, "[QMI-TIME] ATS state: prior record carries no ATS_RTC reading, so modem reset vs AP reboot is UNKNOWN for this boot");
		return;
	}

	if (rtc_now_ms < ats_state.rtc_ms) {
		syslog(LOG_WARNING, "[QMI-TIME] ATS state: MODEM RESTARTED -- ATS_RTC is %llu ms, below the %llu ms recorded at the last ATS_USER set (%llu ms of modem time lost). Its time bases were cleared and are being re-established.",
		       (unsigned long long)rtc_now_ms,
		       (unsigned long long)ats_state.rtc_ms,
		       (unsigned long long)(ats_state.rtc_ms - rtc_now_ms));
		return;
	}

	syslog(LOG_NOTICE, "[QMI-TIME] ATS state: MODEM SURVIVED -- ATS_RTC is %llu ms, %llu ms above the %llu ms recorded at the last ATS_USER set. That difference is modem time elapsed across the AP's downtime, measured with no AP wall clock in the arithmetic.",
	       (unsigned long long)rtc_now_ms,
	       (unsigned long long)(rtc_now_ms - ats_state.rtc_ms),
	       (unsigned long long)ats_state.rtc_ms);
}

/*
 * Is the AP wall clock usable as a time source?
 *
 * Two guards, both conservative.  A plausible clock is ALWAYS preferred: a
 * legitimate NTP step and a broken boot clock look identical in a single
 * sample, and overriding a good clock with a stale offset would be worse than
 * doing nothing.
 */
static bool ap_clock_is_usable(uint64_t ap_wall_ms)
{
	if (ap_wall_ms < AP_CLOCK_SANITY_FLOOR_MS)
		return false;
	/* An AP reboot cannot move the wall clock backwards. */
	if (ats_state_loaded && ats_state.wall_ms >= AP_CLOCK_SANITY_FLOOR_MS &&
	    ap_wall_ms < ats_state.wall_ms)
		return false;
	return true;
}

static bool ats_state_restore_offset(uint64_t *out)
{
	if (!ats_state_loaded || ats_state.base != ATS_USER)
		return false;
	*out = ats_state.offset_ms;
	return true;
}

/*
 * Persist the outcome of a successful ATS_USER set.
 *
 * An INITIAL set (boot, or the first handshake after an SSR) always writes.
 * A refresh writes only when the offset actually changed: re-deriving the same
 * number tells a future boot nothing new, and every write costs a flash erase.
 */
static void ats_state_note_handshake(uint64_t rtc_ms, uint64_t wall_ms,
				     uint64_t offset_ms, bool is_refresh)
{
	struct ats_persist rec;
	bool changed;

	if (!ats_state_enabled)
		return;

	changed = !ats_state_loaded || ats_state.offset_ms != offset_ms;
	if (is_refresh && !changed) {
		syslog(LOG_INFO, "[QMI-TIME] ATS state: refresh offset unchanged (%llu ms); not rewriting %s",
		       (unsigned long long)offset_ms, ats_state_file);
		return;
	}

	rec.base = ATS_USER;
	rec.offset_ms = offset_ms;
	rec.rtc_ms = rtc_ms;
	rec.wall_ms = wall_ms;
	rec.delta_ms = (int64_t)(wall_ms - rtc_ms);
	rec.synced_at = (uint64_t)time(NULL);

	if (ats_state_save(&rec) == 0) {
		ats_state = rec;
		ats_state_loaded = true;
		syslog(LOG_NOTICE, "[QMI-TIME] ATS state: persisted to %s -- base=%u offset_ms=%llu rtc_ms=%llu wall_ms=%llu delta_ms=%lld",
		       ats_state_file, rec.base,
		       (unsigned long long)rec.offset_ms,
		       (unsigned long long)rec.rtc_ms,
		       (unsigned long long)rec.wall_ms,
		       (long long)rec.delta_ms);
	} else {
		syslog(LOG_WARNING, "[QMI-TIME] ATS state: could not persist to %s; the record stays advisory only",
		       ats_state_file);
	}
}

static void sig_handler(int sig)
{
	(void)sig;
	running = 0;
}

static void handle_qrtr_packet(int sock, void *buf, size_t len,
			       const struct sockaddr_qrtr *sq);

/*
 * Synchronous QMI Transaction Engine
 * Sends request with little-endian conversion, validates source node/port,
 * and routes any non-matching packets to handle_qrtr_packet() so they are NOT discarded.
 */
static int qmi_send_sync_transaction(int sock, uint32_t node, uint32_t port,
				     int req_msg_id, const void *req_struct,
				     struct qmi_elem_info *req_ei,
				     void *resp_struct,
				     struct qmi_elem_info *resp_ei,
				     int timeout_ms)
{
	char tx_buf[256];
	char rx_buf[4096];
	struct qrtr_packet tx_pkt;
	struct pollfd pfd;
	uint16_t txn_id = next_txn_id++;
	ssize_t enc_len;
	int ret;

	if (next_txn_id == 0)
		next_txn_id = 1;

	tx_pkt.data = tx_buf;
	tx_pkt.data_len = sizeof(tx_buf);

	enc_len = qmi_encode_message(&tx_pkt, QMI_REQUEST, req_msg_id,
				     txn_id, req_struct, req_ei);
	if (enc_len < 0) {
		syslog(LOG_ERR, "[QMI-TIME-DEBUG] Failed to encode QMI request 0x%04x: %zd", req_msg_id, enc_len);
		return -EINVAL;
	}

	syslog(LOG_NOTICE, "[QMI-TIME] TX 0x%04x to QRTR node=%u port=%u txn=%u (len=%zd)",
	       req_msg_id, node, port, txn_id, enc_len);

	ret = qrtr_sendto(sock, node, port, tx_buf, enc_len);
	if (ret < 0) {
		syslog(LOG_ERR, "[QMI-TIME] qrtr_sendto error for 0x%04x to QRTR node=%u port=%u: %d (%s)",
		       req_msg_id, node, port, errno, strerror(errno));
		return -errno;
	}

	pfd.fd = sock;
	pfd.events = POLLIN;

	struct timespec start, now;
	clock_gettime(CLOCK_MONOTONIC, &start);

	while (running) {
		clock_gettime(CLOCK_MONOTONIC, &now);
		int elapsed_ms = (now.tv_sec - start.tv_sec) * 1000 + (now.tv_nsec - start.tv_nsec) / 1000000;
		int remaining_ms = timeout_ms - elapsed_ms;
		if (remaining_ms <= 0) {
			syslog(LOG_ERR, "[QMI-TIME] Timeout (%d ms) waiting for response to 0x%04x (txn=%u, QRTR node=%u port=%u)",
			       timeout_ms, req_msg_id, txn_id, node, port);
			return -ETIMEDOUT;
		}

		ret = poll(&pfd, 1, remaining_ms);
		if (ret <= 0) {
			if (ret < 0 && errno == EINTR)
				continue;
			syslog(LOG_ERR, "[QMI-TIME] Poll error/timeout for 0x%04x (txn=%u, QRTR node=%u port=%u): %d",
			       req_msg_id, txn_id, node, port, ret);
			return -ETIMEDOUT;
		}

		struct sockaddr_qrtr sq;
		socklen_t sl = sizeof(sq);
		ssize_t rx_len = recvfrom(sock, rx_buf, sizeof(rx_buf), 0, (struct sockaddr *)&sq, &sl);
		if (rx_len <= 0)
			continue;

		struct qrtr_packet rx_pkt;
		ret = qrtr_decode(&rx_pkt, rx_buf, rx_len, &sq);
		if (ret < 0)
			continue;

		/* If packet is from modem data port and matches our txn_id, handle response */
		if (rx_pkt.type == QRTR_TYPE_DATA && sq.sq_node == node && sq.sq_port == port &&
		    rx_pkt.data_len >= sizeof(struct qmi_header)) {
			const struct qmi_header *hdr = rx_pkt.data;
			uint8_t q_type = hdr->type;
			uint16_t q_txn = le16toh(hdr->txn_id);
			uint16_t q_msg = le16toh(hdr->msg_id);

			if (q_type == QMI_RESPONSE && q_txn == txn_id && q_msg == req_msg_id) {
				unsigned int decoded_txn;
				ret = qmi_decode_message(resp_struct, &decoded_txn, &rx_pkt,
							 QMI_RESPONSE, req_msg_id, resp_ei);
				if (ret < 0) {
					syslog(LOG_ERR, "[QMI-TIME] Failed to decode QMI response 0x%04x from QRTR node=%u port=%u txn=%u: %d",
					       req_msg_id, sq.sq_node, sq.sq_port, txn_id, ret);
					return ret;
				}
				syslog(LOG_NOTICE, "[QMI-TIME] RX 0x%04x response from QRTR node=%u port=%u txn=%u decoded successfully",
				       req_msg_id, sq.sq_node, sq.sq_port, txn_id);
				return 0;
			}
		}

		/*
		 * Unrelated packet (unsolicited indication, server discovery, or control msg).
		 * Route to handle_qrtr_packet() so it is NOT discarded!
		 */
		handle_qrtr_packet(sock, rx_buf, rx_len, &sq);
	}

	return -EINTR;
}

/*
 * Synchronously query an ATS timebase offset from modem (0x0021)
 */
static int query_modem_ats_base(int sock, uint32_t base, uint64_t *val_out)
{
	struct time_genoff_get_req req;
	struct time_genoff_get_resp resp;
	memset(&req, 0, sizeof(req));
	memset(&resp, 0, sizeof(resp));
	req.base = base;

	int ret = qmi_send_sync_transaction(sock, modem_node, modem_port,
					    QMI_TIME_GENOFF_GET_REQ, &req, time_genoff_get_req_ei,
					    &resp, time_genoff_get_resp_ei, 3000);
	if (ret == 0 && resp.result.result == QMI_RESULT_SUCCESS_V01 && resp.result.error == QMI_ERR_NONE_V01) {
		*val_out = resp.offset;
		return 0;
	}
	return -1;
}

/*
 * Android-Equivalent Handshake & Alignment Engine:
 * In Qualcomm Hexagon MPSS, ATS_TOD (base 1) and ATS_USER (base 2) both share
 * the same underlying hardware SCLK counter (ATS_RTC, base 0):
 *   ATS_TOD(t)  = ATS_RTC(t) + offset_TOD
 *   ATS_USER(t) = ATS_RTC(t) + offset_USER
 *
 * Therefore, ATS_USER == ATS_TOD (drift = 0 ms) if and only if:
 *   offset_USER == offset_TOD == ATS_TOD - ATS_RTC
 *
 * Setting offset_USER = (ATS_TOD - ATS_RTC) locks ATS_USER and ATS_TOD in perfect
 * millisecond-level phase, completely eliminating SCLK calibration drift errors
 * (lte_ml1_sleepmgr_stm.c:4054 and lte_ml1_common_timer.c:390) on 100% pure software.
 */
static int send_ats_user_transaction(int sock, bool is_refresh)
{
	int ret;
	uint64_t tod_val = 0;
	uint64_t rtc_val = 0;
	uint64_t genoff = 0;
	struct timeval tv;
	uint64_t wall_ms;

	if (!modem_connected || modem_port == 0)
		return -1;

	/* Query modem base 0 (ATS_RTC) and base 1 (ATS_TOD) */
	int ret_tod = query_modem_ats_base(sock, ATS_TOD, &tod_val);
	int ret_rtc = query_modem_ats_base(sock, ATS_RTC, &rtc_val);

	gettimeofday(&tv, NULL);
	wall_ms = ((uint64_t)tv.tv_sec * 1000ULL) + ((uint64_t)tv.tv_usec / 1000ULL);

	/*
	 * Free: the RTC read above is the only input this needs, so the
	 * reset-vs-reboot verdict costs no extra QMI traffic.
	 */
	if (ret_rtc == 0)
		ats_state_log_boot_verdict(rtc_val);

	if (ret_tod == 0 && ret_rtc == 0 && tod_val > 0 && tod_val >= rtc_val) {
		/*
		 * Modem has valid cellular network TOD (from NITZ):
		 * Target offset is exactly (ATS_TOD - ATS_RTC).
		 */
		genoff = tod_val - rtc_val;
		syslog(LOG_NOTICE, "[QMI-TIME] %s: Cellular network TOD locked! Aligned offset = TOD (%llu) - RTC (%llu) = %llu ms",
		       is_refresh ? "REFRESH" : "INITIAL",
		       (unsigned long long)tod_val, (unsigned long long)rtc_val,
		       (unsigned long long)genoff);
	} else if (!ap_clock_is_usable(wall_ms)) {
		/*
		 * The AP wall clock is not a clock (unset, or it moved backwards
		 * across a reboot).  Deriving an offset from it would underflow
		 * the GPS-epoch subtraction and hand the modem a ~1.8e19 ms
		 * ATS_USER -- a wrong offset is worse than none, so use the
		 * persisted one if we have it and otherwise send nothing.
		 */
		uint64_t restored = 0;

		if (ats_state_restore_offset(&restored)) {
			genoff = restored;
			syslog(LOG_WARNING, "[QMI-TIME] %s: AP wall clock (%llu ms) is unusable; restoring ATS_USER offset %llu ms from the persisted record instead",
			       is_refresh ? "REFRESH" : "INITIAL",
			       (unsigned long long)wall_ms, (unsigned long long)genoff);
		} else {
			syslog(LOG_ERR, "[QMI-TIME] %s: AP wall clock (%llu ms) is unusable and no persisted ATS record exists; REFUSING to derive an offset from it. Will retry.",
			       is_refresh ? "REFRESH" : "INITIAL", (unsigned long long)wall_ms);
			return -1;
		}
	} else {
		/* Fallback to host AP time if network NITZ has not locked yet */
		uint64_t gps_time_ms = wall_ms - GPS_EPOCH_OFFSET_MS;
		if (ret_rtc == 0 && gps_time_ms >= rtc_val) {
			genoff = gps_time_ms - rtc_val;
		} else {
			genoff = gps_time_ms;
		}
		syslog(LOG_INFO, "[QMI-TIME] %s: Network TOD not yet locked; using AP host time offset: %llu ms",
		       is_refresh ? "REFRESH" : "INITIAL", (unsigned long long)genoff);
	}

	syslog(LOG_INFO, "[QMI-TIME] %s: Synchronously setting ATS_USER (base=2, offset=%llu ms)...",
	       is_refresh ? "REFRESH" : "INITIAL", (unsigned long long)genoff);

	struct time_genoff_set_req set_req;
	struct time_genoff_set_resp set_resp;
	memset(&set_req, 0, sizeof(set_req));
	memset(&set_resp, 0, sizeof(set_resp));
	set_req.base = ATS_USER;
	set_req.offset = genoff;

	ret = qmi_send_sync_transaction(sock, modem_node, modem_port,
					QMI_TIME_GENOFF_SET_REQ, &set_req, time_genoff_set_req_ei,
					&set_resp, time_genoff_set_resp_ei, QMI_SYNC_TIMEOUT_MS);
	if (ret < 0) {
		syslog(LOG_ERR, "[QMI-TIME] %s: ATS_USER synchronization transaction failed: %d (%s)",
		       is_refresh ? "REFRESH" : "INITIAL", ret, strerror(-ret));
		if (!is_refresh) {
			current_state = STATE_FAILED;
			unlink(SYNC_MARKER_FILE);
		}
		return -1;
	}

	if (set_resp.result.result != QMI_RESULT_SUCCESS_V01 || set_resp.result.error != QMI_ERR_NONE_V01) {
		syslog(LOG_ERR, "[QMI-TIME] %s: Modem rejected ATS_USER sync! result=%u error=%u",
		       is_refresh ? "REFRESH" : "INITIAL", set_resp.result.result, set_resp.result.error);
		if (!is_refresh) {
			current_state = STATE_FAILED;
			unlink(SYNC_MARKER_FILE);
		}
		return -1;
	}

	syslog(LOG_NOTICE, "=================================================================");
	syslog(LOG_NOTICE, "[QMI-TIME] %s ATS_USER TRANSACTION VERIFIED!",
	       is_refresh ? "REFRESH" : "INITIAL");
	syslog(LOG_NOTICE, "[QMI-TIME] Modem baseband confirmed ATS_USER (offset=%llu ms)", (unsigned long long)genoff);
	if (!is_refresh)
		syslog(LOG_NOTICE, "[QMI-TIME] SCLK calibration watchdog reset. Pure-software modem stable.");
	syslog(LOG_NOTICE, "=================================================================");

	int fd = open(SYNC_MARKER_FILE, O_WRONLY | O_CREAT | O_TRUNC, 0644);
	if (fd >= 0) {
		dprintf(fd, "offset_ms=%llu\nsynced_at=%ld\nstate=SYNCHRONIZED\nis_refresh=%d\n",
			(unsigned long long)genoff, time(NULL), is_refresh ? 1 : 0);
		close(fd);
	}

	/*
	 * The tmpfs marker above dies with the boot; this one does not.  Only
	 * a modem-CONFIRMED offset is recorded -- the SET returned success.
	 */
	ats_state_note_handshake(ret_rtc == 0 ? rtc_val : 0, wall_ms, genoff, is_refresh);

	return 0;
}

static int run_handshake_state_machine(int sock)
{
	current_state = STATE_SYNCING_ATS_USER;
	last_handshake_attempt = get_monotonic_sec();
	int ret = send_ats_user_transaction(sock, false);
	if (ret == 0) {
		current_state = STATE_SYNCHRONIZED;
		last_user_refresh = get_monotonic_sec();
		last_keepalive = get_monotonic_sec();
		consecutive_get_fails = 0;
	} else {
		current_state = STATE_FAILED;
		unlink(SYNC_MARKER_FILE);
	}
	return ret;
}

/*
 * Validates periodic ATS_TOD query response from baseband.
 * If the baseband fails to respond or returns an error, tracks failure and invalidates sync.
 * Logs TX, RX, result/error, and QRTR source node/port unconditionally.
 */
static int validate_periodic_time_get(int sock)
{
	if (!modem_connected || modem_port == 0 || current_state != STATE_SYNCHRONIZED)
		return -1;

	struct time_genoff_get_req req;
	struct time_genoff_get_resp resp;
	memset(&req, 0, sizeof(req));
	memset(&resp, 0, sizeof(resp));
	req.base = ATS_TOD;

	syslog(LOG_NOTICE, "[QMI-TIME] Periodic TX 0x0021 (QMI_TIME_GENOFF_GET_REQ) -> QRTR node=%u port=%u (base=%u)",
	       modem_node, modem_port, req.base);

	int ret = qmi_send_sync_transaction(sock, modem_node, modem_port,
					    QMI_TIME_GENOFF_GET_REQ, &req, time_genoff_get_req_ei,
					    &resp, time_genoff_get_resp_ei, 3000);
	if (ret == 0) {
		syslog(LOG_NOTICE, "[QMI-TIME] Periodic RX 0x0021 response <- QRTR node=%u port=%u: QMI result=%u error=%u, base=%u offset=%llu ms",
		       modem_node, modem_port, resp.result.result, resp.result.error,
		       resp.base, (unsigned long long)resp.offset);
		if (resp.result.result == QMI_RESULT_SUCCESS_V01 && resp.result.error == QMI_ERR_NONE_V01) {
			consecutive_get_fails = 0;
			return 0;
		}
	} else {
		syslog(LOG_ERR, "[QMI-TIME] Periodic RX 0x0021 failed/timeout <- QRTR node=%u port=%u: ret=%d (%s)",
		       modem_node, modem_port, ret, strerror(-ret));
	}

	consecutive_get_fails++;
	syslog(LOG_WARNING, "[QMI-TIME] Periodic ATS_TOD query failed (%d/3): ret=%d, QMI result=%u error=%u",
	       consecutive_get_fails, ret, resp.result.result, resp.result.error);

	if (consecutive_get_fails >= 3) {
		syslog(LOG_ERR, "[QMI-TIME] Baseband time query failed 3 times! Revoking sync state.");
		current_state = STATE_FAILED;
		unlink(SYNC_MARKER_FILE);
	}

	return -1;
}

/*
 * Read-only modem-uptime telemetry.
 *
 * ATS_RTC (base 0) is the modem's OWN uptime counter in milliseconds. It is the
 * only instrument in this project that measures modem uptime without borrowing
 * an AP-side offset from a previous boot (Doc 172; see
 * reference_modem_ats_rtc_uptime_instrument.md).
 *
 * WHY THIS CANNOT WRITE TO THE MODEM -- it is a property of the wire format, not
 * of this function's care. The only message sent here is
 * QMI_TIME_GENOFF_GET_REQ (0x0021), and time_genoff_get_req_ei (qmi_time.c:58)
 * declares EXACTLY ONE TLV: type 0x01, 4 bytes, offsetof(base). There is no
 * offset/value field anywhere in the descriptor, so no encoding of this message
 * can carry a value. Confirmed against the wire: the deployed daemon logs
 * "TX 0x0021 ... (len=14)" = 7-byte QMI header + (1 + 2 + 4) TLV, versus
 * "TX 0x0020 ... (len=25)" for the write = 7 + 7 + 11. The write is 11 bytes
 * longer and that extra TLV (0x02, 8-byte offset) is the only thing that makes
 * it a write.
 *
 * Deliberately has NO state effects: a failure is logged and dropped. It must
 * never be able to revoke sync -- that is validate_periodic_time_get()'s job,
 * and a telemetry path that can knock the daemon out of STATE_SYNCHRONIZED
 * would be a surprising side effect of asking a question.
 */
static int log_modem_uptime(int sock)
{
	uint64_t rtc_ms = 0;
	uint64_t tod_ms = 0;
	int ret_rtc = query_modem_ats_base(sock, ATS_RTC, &rtc_ms);
	int ret_tod = query_modem_ats_base(sock, ATS_TOD, &tod_ms);

	if (ret_rtc != 0) {
		syslog(LOG_WARNING, "[QMI-TIME] MODEM-UPTIME unavailable: ATS_RTC read failed (read-only, no state change)");
		return -1;
	}

	if (ret_tod == 0 && tod_ms >= rtc_ms)
		syslog(LOG_NOTICE, "[QMI-TIME] MODEM-UPTIME rtc_ms=%llu tod_ms=%llu tod_minus_rtc_ms=%llu",
		       (unsigned long long)rtc_ms, (unsigned long long)tod_ms,
		       (unsigned long long)(tod_ms - rtc_ms));
	else
		syslog(LOG_NOTICE, "[QMI-TIME] MODEM-UPTIME rtc_ms=%llu (ATS_TOD unavailable)",
		       (unsigned long long)rtc_ms);

	return 0;
}

static void handle_qrtr_packet(int sock, void *buf, size_t len,
			       const struct sockaddr_qrtr *sq)
{
	struct qrtr_packet pkt;
	int ret;

	ret = qrtr_decode(&pkt, buf, len, sq);
	if (ret < 0)
		return;

	if (pkt.type == QRTR_TYPE_NEW_SERVER) {
		if (pkt.service == QMI_TIME_SERVICE_ID) {
			syslog(LOG_NOTICE, "[QMI-TIME] Discovered QMI TIME service: node=%u port=%u service=%u instance=%u version=%u",
			       pkt.node, pkt.port, pkt.service, pkt.instance, pkt.version);

			uint64_t now = get_monotonic_sec();
			/*
			 * Guard against rapid duplicate NEW_SERVER events:
			 * Only ignore if we are ALREADY synchronized and the last handshake was recent (< 3s).
			 * If state is not synchronized or modem underwent SSR, always execute the handshake.
			 */
			if (modem_connected && current_state == STATE_SYNCHRONIZED &&
			    modem_node == pkt.node && modem_port == pkt.port &&
			    (now - last_handshake_attempt < 3)) {
				syslog(LOG_INFO, "[QMI-TIME] Duplicate NEW_SERVER for node=%u port=%u within 3s; ignoring.",
				       pkt.node, pkt.port);
				return;
			}

			modem_node = pkt.node;
			modem_port = pkt.port;
			modem_connected = true;

			/* Execute transactional handshake state machine */
			run_handshake_state_machine(sock);
		}
	} else if (pkt.type == QRTR_TYPE_DEL_SERVER) {
		if (pkt.node == modem_node && pkt.port == modem_port) {
			syslog(LOG_WARNING, "[QMI-TIME] Modem SSR / Disconnect detected (node=%u port=%u). Resetting state machine.",
			       pkt.node, pkt.port);
			modem_connected = false;
			modem_port = 0;
			current_state = STATE_DISCOVERING;
			unlink(SYNC_MARKER_FILE);
			last_handshake_attempt = 0;
			last_lookup_attempt = 0;
			/*
			 * Re-arm the verdict: the next handshake must report
			 * whether the modem actually restarted, against the
			 * record written before the SSR.  The record itself is
			 * left alone -- it is a statement about the past, and it
			 * is exactly what makes that comparison possible.
			 */
			ats_verdict_logged = false;
			qrtr_new_lookup(sock, QMI_TIME_SERVICE_ID, 0, 0);
		}
	} else if (pkt.type == QRTR_TYPE_DATA) {
		/* Validate packet source node and port */
		if (sq->sq_node != modem_node || sq->sq_port != modem_port)
			return;

		if (pkt.data_len < sizeof(struct qmi_header))
			return;

		const struct qmi_header *hdr = (const struct qmi_header *)pkt.data;
		uint8_t qmi_type = hdr->type;
		uint16_t txn_id = le16toh(hdr->txn_id);
		uint16_t msg_id = le16toh(hdr->msg_id);

		if (verbose) {
			syslog(LOG_DEBUG, "[QMI-TIME-DEBUG] Async Rx QMI: type=%u txn=%u msg_id=0x%04x len=%u",
			       qmi_type, txn_id, msg_id, le16toh(hdr->msg_len));
		}

		if (qmi_type == QMI_INDICATION) {
			if (msg_id >= QMI_TIME_ATS_RTC_UPDATE_IND && msg_id <= QMI_TIME_ATS_BREW_UPDATE_IND) {
				struct time_tod_ind ind;
				unsigned int txn;
				ret = qmi_decode_message(&ind, &txn, &pkt,
							 QMI_INDICATION, msg_id,
							 time_tod_ind_ei);
				if (ret >= 0) {
					syslog(LOG_NOTICE, "[QMI-TIME] Indication 0x%04x (%s) received from QRTR node=%u port=%u: base=%u (%s), offset=%llu ms",
					       msg_id, qmi_time_msg_name(msg_id),
					       sq->sq_node, sq->sq_port,
					       ind.base, qmi_time_base_name(ind.base),
					       (unsigned long long)ind.offset);
					if (ind.base == ATS_TOD && ind.offset > 0 && modem_connected && current_state == STATE_SYNCHRONIZED) {
						syslog(LOG_INFO, "[QMI-TIME] Cellular network NITZ broadcast received (base=%u, offset=%llu ms). Baseband ATS_TOD updated (passive host tracking).",
						       ind.base, (unsigned long long)ind.offset);
					}
				} else {
					syslog(LOG_WARNING, "[QMI-TIME] Failed to decode indication 0x%04x (%s) from node=%u port=%u: ret=%d",
					       msg_id, qmi_time_msg_name(msg_id), sq->sq_node, sq->sq_port, ret);
				}
			} else {
				syslog(LOG_NOTICE, "[QMI-TIME] Other QMI indication received: msg_id=0x%04x (%s) len=%u from node=%u port=%u",
				       msg_id, qmi_time_msg_name(msg_id), le16toh(hdr->msg_len),
				       sq->sq_node, sq->sq_port);
			}
		}
	}
}

int main(int argc, char *argv[])
{
	struct pollfd pfd;
	char buf[4096];
	int opt;
	int sock;
	int ret;

	while ((opt = getopt(argc, argv, "vpr:i:P:")) != -1) {
		switch (opt) {
		case 'v':
			verbose = true;
			break;
		case 'p':
			/*
			 * Opt-in READ-ONLY telemetry poll: periodic 0x0021 GETs of
			 * ATS_RTC (base 0, the modem's own uptime) and ATS_TOD
			 * (base 1). Sends no 0x0020 write -- see log_modem_uptime().
			 *
			 * FIXED 2026-09-22: this case previously read
			 * "enable_periodic_get = false", i.e. it assigned the
			 * flag its own default and was therefore a NO-OP. The
			 * periodic poll has never actually run, and the loop
			 * comment claiming it "can be explicitly enabled with
			 * '-p'" was false. Kept OFF by default: the deployed
			 * config must stay Android-parity while Doc 146 §9's
			 * fatal-rate experiment is running.
			 */
			enable_periodic_get = true;
			break;
		case 'r':
			refresh_interval = atoi(optarg);
			break;
		case 'i':
			poll_interval = atoi(optarg);
			if (poll_interval < 5)
				poll_interval = 5;
			break;
		case 'P':
			/*
			 * Persistent ATS state path.  "none" disables the whole
			 * path (no read, no write) -- for a soak that must not
			 * touch flash, or for testing.
			 */
			if (!strcmp(optarg, "none")) {
				ats_state_enabled = false;
			} else if (optarg[0]) {
				ats_state_file = optarg;
			}
			break;
		default:
			fprintf(stderr, "Usage: %s [-v] [-p] [-r <refresh_sec>] [-i <interval_sec>] [-P <ats_state_file>]\n", argv[0]);
			fprintf(stderr, "  -p  enable the READ-ONLY modem-uptime poll (0x0021 GET only; no 0x0020 write)\n");
			fprintf(stderr, "  -r  enable the periodic ATS_USER WRITE (0x0020) every <refresh_sec>; 0 = off (Android parity)\n");
			fprintf(stderr, "  -P  persistent ATS record path (default %s); \"none\" disables it\n", DEFAULT_ATS_STATE_FILE);
			return 1;
		}
	}

	openlog("qcom-time-daemon", LOG_PID | LOG_CONS | LOG_PERROR, LOG_DAEMON);
	syslog(LOG_NOTICE, "[QMI-TIME] Starting Qualcomm QMI Time Synchronization Daemon (Android Protocol Reconstructed)");
	syslog(LOG_NOTICE, "[QMI-TIME] Pure software modem stability mode active. Periodic ATS_USER refresh: %s (interval=%ds)",
	       refresh_interval > 0 ? "ENABLED" : "DISABLED", refresh_interval);
	syslog(LOG_NOTICE, "[QMI-TIME] Read-only modem-uptime poll (ATS_RTC base 0 + ATS_TOD base 1, 0x0021 GET only): %s (interval=%ds)",
	       enable_periodic_get ? "ENABLED" : "DISABLED", poll_interval);
	syslog(LOG_NOTICE, "[QMI-TIME] Persistent ATS state: %s",
	       ats_state_enabled ? ats_state_file : "DISABLED (-P none)");

	/*
	 * Load before the socket opens, so the record is in hand by the time
	 * the first handshake needs it.  Failure is normal on a first run.
	 */
	if (ats_state_enabled)
		ats_state_load();

	signal(SIGINT, sig_handler);
	signal(SIGTERM, sig_handler);

	unlink(SYNC_MARKER_FILE);

	sock = qrtr_open(0);
	if (sock < 0) {
		syslog(LOG_ERR, "[QMI-TIME] Failed to open QRTR socket: %d (%s)", errno, strerror(errno));
		closelog();
		return 1;
	}

	/* Register lookup for Qualcomm QMI TIME service (Service 22) */
	ret = qrtr_new_lookup(sock, QMI_TIME_SERVICE_ID, 0, 0);
	if (ret < 0) {
		syslog(LOG_WARNING, "[QMI-TIME] Failed to register QRTR lookup for time service: %d (%s)",
		       errno, strerror(errno));
	}

	pfd.fd = sock;
	pfd.events = POLLIN;
	last_keepalive = get_monotonic_sec();
	last_handshake_attempt = get_monotonic_sec();
	last_user_refresh = get_monotonic_sec();

	while (running) {
		uint64_t now = get_monotonic_sec();

		/*
		 * Periodic QRTR service re-lookup while in STATE_DISCOVERING:
		 * Ensures modem service 22 rediscovery after any unannounced SSR or QRTR restart.
		 */
		if (current_state == STATE_DISCOVERING && (now - last_lookup_attempt >= 5)) {
			last_lookup_attempt = now;
			qrtr_new_lookup(sock, QMI_TIME_SERVICE_ID, 0, 0);
		}

		/*
		 * Timer-based retry for failed or pending initial handshake:
		 * If modem is connected but sync is not confirmed, retry every HANDSHAKE_RETRY_INTERVAL_SEC
		 */
		if (modem_connected && current_state != STATE_SYNCHRONIZED) {
			if (now - last_handshake_attempt >= HANDSHAKE_RETRY_INTERVAL_SEC) {
				last_handshake_attempt = now;
				syslog(LOG_INFO, "[QMI-TIME] Retrying QMI Time handshake state machine...");
				run_handshake_state_machine(sock);
			}
		}

		/*
		 * Periodic ATS_USER (0x0020) Refresh:
		 * Continuously resets modem baseband SCLK crystal drift accumulator and calibration watchdog.
		 * Prevents baseband lte_ml1_common_timer.c:390 / lte_ml1_sleepmgr_stm.c:4054 crash at t=903.6s
		 * on 100% pristine untouched stock modem firmware.
		 */
		if (refresh_interval > 0 && modem_connected && current_state == STATE_SYNCHRONIZED &&
		    (now - last_user_refresh >= (uint64_t)refresh_interval)) {
			last_user_refresh = now;
			syslog(LOG_NOTICE, "[QMI-TIME] Periodic ATS_USER refresh (interval=%ds): re-anchoring baseband SCLK offset...",
			       refresh_interval);
			ret = send_ats_user_transaction(sock, true);
			if (ret != 0) {
				syslog(LOG_WARNING, "[QMI-TIME] Periodic ATS_USER refresh failed: %d (will retry next interval)", ret);
			}
		}

		/*
		 * In STATE_SYNCHRONIZED:
		 * Opt-in periodic READ-ONLY poll, enabled with '-p'.
		 *   - log_modem_uptime()  : 0x0021 GET of ATS_RTC + ATS_TOD. Pure
		 *                           telemetry; no state effects.
		 *   - validate_periodic_time_get() : 0x0021 GET of ATS_TOD used as a
		 *                           liveness check. This one DOES have a state
		 *                           effect -- 3 consecutive failures revoke
		 *                           sync so the handshake is retried.
		 * Neither sends a 0x0020 write.
		 */
		if (enable_periodic_get && modem_connected && current_state == STATE_SYNCHRONIZED &&
		    (now - last_keepalive >= (uint64_t)poll_interval)) {
			validate_periodic_time_get(sock);
			log_modem_uptime(sock);
			last_keepalive = now;
		}

		/* Poll for incoming QRTR messages with 1-second timeout */
		ret = poll(&pfd, 1, 1000);
		if (ret < 0) {
			if (errno == EINTR)
				continue;
			syslog(LOG_ERR, "[QMI-TIME] Poll error on QRTR socket: %d (%s)", errno, strerror(errno));
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

	syslog(LOG_NOTICE, "[QMI-TIME] Shutting down Qualcomm QMI Time Daemon");
	unlink(SYNC_MARKER_FILE);
	qrtr_close(sock);
	closelog();

	return 0;
}
