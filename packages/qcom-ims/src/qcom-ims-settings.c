/* SPDX-License-Identifier: BSD-3-Clause */
/* Legacy IMS Settings v1 (MSM8916). Protocol field references:
 * https://github.com/os-vector/wire-os/blob/master/qmi/services/ip_multimedia_subsystem_settings_v01.c
 * This requests registration; it does not implement IMS authentication or SIP.
 */
#include <endian.h>
#include <errno.h>
#include <libqrtr.h>
#include <poll.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

static uint16_t transaction;
static uint8_t reply[8192];

static uint16_t get16(const uint8_t *p)
{
	return p[0] | ((uint16_t)p[1] << 8);
}

static int64_t milliseconds(void)
{
	struct timespec t;
	clock_gettime(CLOCK_MONOTONIC, &t);
	return (int64_t)t.tv_sec * 1000 + t.tv_nsec / 1000000;
}

static int wait_readable(int fd, int64_t deadline)
{
	struct pollfd p = { .fd = fd, .events = POLLIN };
	int rc;
	for (;;) {
		int64_t remaining = deadline - milliseconds();
		if (remaining <= 0) {
			errno = ETIMEDOUT;
			return -1;
		}
		rc = poll(&p, 1, remaining);
		if (rc > 0 && (p.revents & POLLIN))
			return 0;
		if (rc < 0 && errno == EINTR)
			continue;
		errno = rc == 0 ? ETIMEDOUT : EIO;
		return -1;
	}
}

static int find_service(int fd, uint32_t service, uint32_t *node, uint32_t *port)
{
	struct qrtr_ctrl_pkt pkt;
	uint32_t from_node, from_port;
	int64_t deadline = milliseconds() + 5000;
	if (qrtr_new_lookup(fd, service, 1, 0) < 0)
		return -1;
	while (wait_readable(fd, deadline) == 0) {
		int n = qrtr_recvfrom(fd, &pkt, sizeof(pkt), &from_node, &from_port);
		if (n < (int)sizeof(pkt) || from_port != QRTR_PORT_CTRL ||
		    le32toh(pkt.cmd) != QRTR_TYPE_NEW_SERVER ||
		    le32toh(pkt.server.service) != service ||
		    le32toh(pkt.server.instance) != 1)
			continue;
		*node = le32toh(pkt.server.node);
		*port = le32toh(pkt.server.port);
		qrtr_remove_lookup(fd, service, 1, 0);
		return 0;
	}
	return -1;
}

static const uint8_t *tlv(int length, uint8_t type, unsigned int size)
{
	int i = 7;
	while (i + 3 <= length) {
		unsigned int n = get16(reply + i + 1);
		if (n > (unsigned int)(length - i - 3))
			return NULL;
		if (reply[i] == type && n == size)
			return reply + i + 3;
		i += 3 + n;
	}
	return NULL;
}

static int request(int fd, uint32_t node, uint32_t port, uint16_t message,
		   const uint8_t *data, size_t size)
{
	uint8_t packet[64] = {0};
	uint32_t from_node, from_port;
	int64_t deadline = milliseconds() + 10000;
	const uint8_t *result;
	uint16_t txn = ++transaction;
	if (size > sizeof(packet) - 7)
		return -1;
	packet[1] = txn; packet[2] = txn >> 8;
	packet[3] = message; packet[4] = message >> 8;
	packet[5] = size; packet[6] = size >> 8;
	if (size)
		memcpy(packet + 7, data, size);
	if (qrtr_sendto(fd, node, port, packet, size + 7) < 0)
		return -1;
	while (wait_readable(fd, deadline) == 0) {
		int n = qrtr_recvfrom(fd, reply, sizeof(reply), &from_node, &from_port);
		if (n < 7 || from_node != node || from_port != port ||
		    reply[0] != QMI_RESPONSE || get16(reply + 1) != txn ||
		    get16(reply + 3) != message)
			continue;
		if (get16(reply + 5) != n - 7 || !(result = tlv(n, 2, 4))) {
			fprintf(stderr, "Malformed response to IMS 0x%04x\n", message);
			return -1;
		}
		if (get16(result)) {
			fprintf(stderr, "IMS 0x%04x: QMI error %u\n", message, get16(result + 2));
			return -1;
		}
		return n;
	}
	fprintf(stderr, "IMS 0x%04x: %s\n", message, strerror(errno));
	return -1;
}

static int show_settings(int fd, uint32_t node, uint32_t port, int verify)
{
	int n, test_mode = -1, mobile_data = -1, volte = -1, provisioned = -1;
	const uint8_t *v;
	n = request(fd, node, port, 0x26, NULL, 0);
	if (n < 0) return -1;
	if ((v = tlv(n, 0x13, 1))) test_mode = *v;
	n = request(fd, node, port, 0x37, NULL, 0);
	if (n < 0) return -1;
	if ((v = tlv(n, 0x12, 1))) mobile_data = *v;
	if ((v = tlv(n, 0x13, 1))) volte = *v;
	printf("IMS test mode: %d\nIMS mobile data enabled: %d\nVoLTE enabled: %d\n",
	       test_mode, mobile_data, volte);
	n = request(fd, node, port, 0x41, NULL, 0);
	if (n >= 0 && (v = tlv(n, 0x11, 4)))
		printf("IMS registration configuration mode: %u\n",
		       (unsigned int)v[0] | ((unsigned int)v[1] << 8) |
		       ((unsigned int)v[2] << 16) | ((unsigned int)v[3] << 24));
	n = request(fd, node, port, 0x54, NULL, 0);
	if (n >= 0 && (v = tlv(n, 0x12, 1))) {
		provisioned = *v;
		printf("Client provisioning VoLTE enabled: %u\n", *v);
	}
	n = request(fd, node, port, 0x48, NULL, 0);
	if (n >= 0 && (v = tlv(n, 0x19, 8))) {
		printf("IMS policy service mask: 0x");
		for (int i = 7; i >= 0; i--)
			printf("%02x", v[i]);
		putchar('\n');
	}
	if (verify && (test_mode != 0 || mobile_data != 1 || volte != 1 || provisioned != 1)) {
		fprintf(stderr, "Modem did not confirm all requested IMS settings\n");
		return -1;
	}
	return 0;
}

static int open_settings(int enable, uint32_t *node, uint32_t *port)
{
	static const unsigned int delays[] = {5, 10, 20, 30};

	for (size_t attempt = 0;; attempt++) {
		int fd = qrtr_open(0);

		if (fd < 0) {
			perror("qrtr_open");
		} else {
			if (enable && find_service(fd, 770, node, port) < 0)
				fprintf(stderr, "No IMS data service found; start 81voltd first\n");
			else if (find_service(fd, 18, node, port) == 0)
				return fd;
			else
				perror("Find IMS Settings v1 instance 0");
			qrtr_close(fd);
		}
		if (!enable || attempt == sizeof(delays) / sizeof(delays[0]))
			return -1;
		sleep(delays[attempt]);
	}
}

int main(int argc, char **argv)
{
	uint32_t node, port;
	int fd, rc;
	int enable = argc == 2 && !strcmp(argv[1], "enable");
	const uint8_t ip_call[] = {0x11, 1, 0, 1, 0x12, 1, 0, 1};
	const uint8_t registration[] = {0x12, 1, 0, 0};
	const uint8_t provisioning[] = {0x10, 1, 0, 1, 0x11, 1, 0, 1};
	if (argc != 2 || (!enable && strcmp(argv[1], "status"))) {
		fprintf(stderr, "Usage: %s status|enable\n"
			"status reads legacy IMS settings (-1 means absent).\n"
			"enable sets mobile data/VoLTE and requests IMS registration.\n"
			"Requires a running IMS data service and compatible carrier provisioning.\n", argv[0]);
		return argc == 2 && (!strcmp(argv[1], "--help") || !strcmp(argv[1], "-h")) ? 0 : 1;
	}
	fd = open_settings(enable, &node, &port);
	if (fd < 0)
		return 1;
	rc = show_settings(fd, node, port, 0);
	if (!rc && enable) {
		rc = request(fd, node, port, 0x36, ip_call, sizeof(ip_call));
		if (rc >= 0)
			rc = request(fd, node, port, 0x53, provisioning, sizeof(provisioning));
		if (rc >= 0)
			rc = request(fd, node, port, 0x21, registration, sizeof(registration));
		if (rc >= 0)
			rc = show_settings(fd, node, port, 1);
		if (!rc)
			puts("IMS registration requested; use qmicli --imsa-get-ims-registration-status to check registration.");
	}
	qrtr_close(fd);
	return rc < 0 ? 1 : 0;
}
