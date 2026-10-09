/*
 * bt-spp -- Bluetooth Serial Port Profile (SPP) server/client for OpenWrt.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Two roles:
 *   bt-spp serve   --channel N --bridge shell|tcp [--tcp-host H --tcp-port P]
 *       Binds RFCOMM channel N, waits for an incoming SPP connection, and
 *       bridges it to a login shell (on a pty) or to a TCP host:port.
 *
 *   bt-spp connect --bdaddr XX:XX:.. --channel N --bridge shell|tcp [--tcp-port P]
 *       Connects to a remote SPP server and bridges it to a login shell, or to
 *       a local TCP listener (so a local program can talk to the BT device).
 *
 * The SDP "Serial Port" record is registered separately (sdptool add SP, which
 * requires bluetoothd --compat); this program only speaks RFCOMM.
 *
 * RFCOMM sockets are kernel-level (AF_BLUETOOTH / BTPROTO_RFCOMM); no
 * libbluetooth linkage is required, so the few constants/structs used are
 * declared here.
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <unistd.h>
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <termios.h>
#include <sys/socket.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>

#ifndef AF_BLUETOOTH
#define AF_BLUETOOTH 31
#endif
#define BTPROTO_RFCOMM 3

typedef struct { uint8_t b[6]; } bdaddr_t;

struct sockaddr_rc {
	sa_family_t rc_family;
	bdaddr_t    rc_bdaddr;
	uint8_t     rc_channel;
};

enum bridge { BRIDGE_SHELL, BRIDGE_TCP };

static void die(const char *msg)
{
	fprintf(stderr, "bt-spp: %s: %s\n", msg, strerror(errno));
	exit(1);
}

/* bluez's str2ba byte order (least-significant byte first). */
static int str2ba(const char *str, bdaddr_t *ba)
{
	int i;
	for (i = 5; i >= 0; i--) {
		unsigned int byte;
		if (sscanf(str, "%2x", &byte) != 1)
			break;
		ba->b[i] = (uint8_t)byte;
		str += 2;
		if (*str == ':')
			str++;
	}
	return (i < 0) ? 0 : -1;
}

/* Full-duplex byte pump between two fds; returns when either side closes. */
static void relay(int a, int b)
{
	char buf[4096];
	struct pollfd pfd[2];

	pfd[0].fd = a;
	pfd[1].fd = b;
	for (;;) {
		int r, i;

		pfd[0].events = pfd[1].events = POLLIN;
		pfd[0].revents = pfd[1].revents = 0;

		r = poll(pfd, 2, -1);
		if (r < 0) {
			if (errno == EINTR)
				continue;
			return;
		}
		for (i = 0; i < 2; i++) {
			ssize_t n, off;
			int out;

			if (!(pfd[i].revents & (POLLIN | POLLHUP | POLLERR)))
				continue;

			n = read(pfd[i].fd, buf, sizeof(buf));
			if (n <= 0)
				return;

			out = pfd[1 - i].fd;
			for (off = 0; off < n; ) {
				ssize_t w = write(out, buf + off, n - off);
				if (w < 0) {
					if (errno == EINTR)
						continue;
					return;
				}
				off += w;
			}
		}
	}
}

/* Bridge the BT connection to a login shell on a fresh pty. */
static void bridge_shell(int btfd)
{
	int master, sfd;
	char *slave, sl[128];
	pid_t pid;

	master = posix_openpt(O_RDWR | O_NOCTTY);
	if (master < 0) {
		perror("bt-spp: posix_openpt");
		return;
	}
	if (grantpt(master) < 0 || unlockpt(master) < 0) {
		perror("bt-spp: grantpt/unlockpt");
		close(master);
		return;
	}
	slave = ptsname(master);
	if (!slave) {
		close(master);
		return;
	}
	snprintf(sl, sizeof(sl), "%s", slave);

	pid = fork();
	if (pid < 0) {
		perror("bt-spp: fork");
		close(master);
		return;
	}
	if (pid == 0) {
		setsid();
		sfd = open(sl, O_RDWR);
		if (sfd < 0)
			_exit(1);
		ioctl(sfd, TIOCSCTTY, 0);
		dup2(sfd, 0);
		dup2(sfd, 1);
		dup2(sfd, 2);
		if (sfd > 2)
			close(sfd);
		close(master);
		execl("/bin/sh", "sh", "-i", (char *)NULL);
		_exit(127);
	}

	relay(master, btfd);
	kill(pid, SIGHUP);
	close(master);
}

static int tcp_connect(const char *host, int port)
{
	struct addrinfo hints, *res, *r;
	char ps[16];
	int fd = -1;

	memset(&hints, 0, sizeof(hints));
	hints.ai_family = AF_UNSPEC;
	hints.ai_socktype = SOCK_STREAM;
	snprintf(ps, sizeof(ps), "%d", port);

	if (getaddrinfo(host, ps, &hints, &res) != 0)
		return -1;
	for (r = res; r; r = r->ai_next) {
		fd = socket(r->ai_family, r->ai_socktype, r->ai_protocol);
		if (fd < 0)
			continue;
		if (connect(fd, r->ai_addr, r->ai_addrlen) == 0)
			break;
		close(fd);
		fd = -1;
	}
	freeaddrinfo(res);
	return fd;
}

static int tcp_listen(int port)
{
	struct sockaddr_in a;
	int fd, one = 1;

	fd = socket(AF_INET, SOCK_STREAM, 0);
	if (fd < 0)
		return -1;
	setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &one, sizeof(one));
	memset(&a, 0, sizeof(a));
	a.sin_family = AF_INET;
	a.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
	a.sin_port = htons((uint16_t)port);
	if (bind(fd, (struct sockaddr *)&a, sizeof(a)) < 0 || listen(fd, 1) < 0) {
		close(fd);
		return -1;
	}
	return fd;
}

/* Bridge the BT connection to the configured target. */
static void bridge(int btfd, enum bridge br, const char *host, int port, int server)
{
	if (br == BRIDGE_SHELL) {
		bridge_shell(btfd);
		return;
	}

	if (server) {
		int t = tcp_connect(host, port);
		if (t < 0) {
			fprintf(stderr, "bt-spp: tcp connect %s:%d failed\n", host, port);
			return;
		}
		relay(btfd, t);
		close(t);
	} else {
		int l = tcp_listen(port);
		if (l < 0) {
			fprintf(stderr, "bt-spp: tcp listen :%d failed\n", port);
			return;
		}
		for (;;) {
			int t = accept(l, NULL, NULL);
			if (t < 0) {
				if (errno == EINTR)
					continue;
				break;
			}
			relay(btfd, t);
			close(t);
		}
		close(l);
	}
}

static void usage(void)
{
	fprintf(stderr,
		"usage:\n"
		"  bt-spp serve   --channel N --bridge shell|tcp [--tcp-host H --tcp-port P]\n"
		"  bt-spp connect --bdaddr AA:BB:CC:DD:EE:FF --channel N "
		"--bridge shell|tcp [--tcp-port P]\n");
	exit(2);
}

int main(int argc, char **argv)
{
	const char *bdstr = NULL, *host = "127.0.0.1";
	int channel = 1, port = 5000;
	enum bridge br = BRIDGE_SHELL;
	int server, i;
	bdaddr_t bd;
	struct sockaddr_rc addr;
	int s;

	if (argc < 2)
		usage();

	if (!strcmp(argv[1], "serve"))
		server = 1;
	else if (!strcmp(argv[1], "connect"))
		server = 0;
	else
		usage();

	for (i = 2; i < argc; i++) {
		if (!strcmp(argv[i], "--channel") && i + 1 < argc)
			channel = atoi(argv[++i]);
		else if (!strcmp(argv[i], "--bridge") && i + 1 < argc) {
			const char *b = argv[++i];
			if (!strcmp(b, "shell"))
				br = BRIDGE_SHELL;
			else if (!strcmp(b, "tcp"))
				br = BRIDGE_TCP;
			else
				usage();
		} else if (!strcmp(argv[i], "--tcp-host") && i + 1 < argc)
			host = argv[++i];
		else if (!strcmp(argv[i], "--tcp-port") && i + 1 < argc)
			port = atoi(argv[++i]);
		else if (!strcmp(argv[i], "--bdaddr") && i + 1 < argc)
			bdstr = argv[++i];
		else
			usage();
	}

	if (channel < 1 || channel > 30) {
		fprintf(stderr, "bt-spp: channel must be 1..30\n");
		return 2;
	}
	if (!server && (!bdstr || str2ba(bdstr, &bd) < 0)) {
		fprintf(stderr, "bt-spp: connect needs a valid --bdaddr\n");
		return 2;
	}

	signal(SIGCHLD, SIG_IGN);
	signal(SIGPIPE, SIG_IGN);

	s = socket(AF_BLUETOOTH, SOCK_STREAM, BTPROTO_RFCOMM);
	if (s < 0)
		die("socket(AF_BLUETOOTH)");

	memset(&addr, 0, sizeof(addr));
	addr.rc_family = AF_BLUETOOTH;
	addr.rc_channel = (uint8_t)channel;

	if (server) {
		/* rc_bdaddr left zero == BDADDR_ANY */
		if (bind(s, (struct sockaddr *)&addr, sizeof(addr)) < 0)
			die("bind RFCOMM channel");
		if (listen(s, 1) < 0)
			die("listen RFCOMM");
		fprintf(stderr, "bt-spp: listening on RFCOMM channel %d (bridge=%s)\n",
			channel, br == BRIDGE_SHELL ? "shell" : "tcp");
		for (;;) {
			int c = accept(s, NULL, NULL);
			if (c < 0) {
				if (errno == EINTR)
					continue;
				break;
			}
			if (fork() == 0) {
				close(s);
				bridge(c, br, host, port, 1);
				close(c);
				_exit(0);
			}
			close(c);
		}
	} else {
		addr.rc_bdaddr = bd;
		if (connect(s, (struct sockaddr *)&addr, sizeof(addr)) < 0)
			die("connect RFCOMM");
		fprintf(stderr, "bt-spp: connected to %s channel %d (bridge=%s)\n",
			bdstr, channel, br == BRIDGE_SHELL ? "shell" : "tcp");
		bridge(s, br, host, port, 0);
	}

	close(s);
	return 0;
}
