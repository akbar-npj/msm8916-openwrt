/* hfp-ag: minimal Bluetooth Hands-Free Audio Gateway for BlueZ.
 *
 * Registers the HFP AG profile with bluetoothd (Profile1 API), runs the
 * service level connection (AT commands) with a headset and opens the SCO
 * link during calls. The audio itself does not pass through the host: on
 * MSM8916 the WCNSS controller routes SCO to the DSP (internal BT SCO port),
 * see voicectl.
 *
 * Call control comes from ufi-voiced over a UNIX socket, one command per line:
 *   incoming <number>   ringing (RING/+CLIP every 3 s)
 *   outgoing | alerting call setup states of an outgoing call
 *   active              call up: indicators + SCO
 *   end                 call gone: indicators, SCO closed
 *   sco on|off          open/close SCO by hand
 *   connect <mac>       connect to a paired headset
 *   status
 * Events sent back to every client:
 *   connected <mac> | disconnected | answer | hangup | dial <number> |
 *   redial | dtmf <digit>
 *
 *   hfp-ag [-s /var/run/hfp-ag.sock] [-v]
 *
 * Build: cc -O2 -o hfp-ag hfp-ag.c $(pkg-config --cflags --libs dbus-1)
 */
#include <dbus/dbus.h>
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <sys/un.h>
#include <syslog.h>
#include <time.h>
#include <unistd.h>

/* Bluetooth socket definitions (no libbluetooth needed) */
#ifndef AF_BLUETOOTH
#define AF_BLUETOOTH 31
#endif
#define BTPROTO_SCO 2
#define SOL_BLUETOOTH 274
#define BT_VOICE 11
#define BT_VOICE_CVSD_16BIT 0x0060

typedef struct { uint8_t b[6]; } __attribute__((packed)) bdaddr_t;
struct sockaddr_sco {
	sa_family_t sco_family;
	bdaddr_t sco_bdaddr;
};
struct bt_voice {
	uint16_t setting;
};

#define HFP_AG_UUID "0000111f-0000-1000-8000-00805f9b34fb"
#define HFP_HS_UUID "0000111e-0000-1000-8000-00805f9b34fb"
#define PROFILE_PATH "/org/ufivoice/hfpag"
#define MAX_CLIENTS 8

/* AG features: reject call (bit 5) */
#define AG_FEATURES 0x0020

enum { IND_SERVICE = 1, IND_CALL, IND_CALLSETUP, IND_CALLHELD, IND_SIGNAL,
       IND_ROAM, IND_BATTCHG };

static int verbose;
static DBusConnection *bus;
static int ctl_fd = -1, clients[MAX_CLIENTS];
static int rfcomm = -1, sco = -1;
static char dev_path[128], dev_mac[18];
static char rxbuf[512];
static size_t rxlen;
static int slc;			/* service level connection up */
static int call, callsetup;	/* indicator values */
static int cmer;		/* indicator events enabled */
static int clip;
static char ring_number[64];
static time_t next_ring;
static volatile sig_atomic_t quit;

static void logmsg(int prio, const char *fmt, ...)
{
	va_list ap;

	if (prio == LOG_DEBUG && !verbose)
		return;
	va_start(ap, fmt);
	vsyslog(prio, fmt, ap);
	va_end(ap);
	if (verbose) {
		va_start(ap, fmt);
		vfprintf(stderr, fmt, ap);
		fputc('\n', stderr);
		va_end(ap);
	}
}

static void event(const char *fmt, ...)
{
	char buf[160];
	va_list ap;
	int n, i;

	va_start(ap, fmt);
	n = vsnprintf(buf, sizeof(buf) - 1, fmt, ap);
	va_end(ap);
	if (n < 0)
		return;
	if (n > (int)sizeof(buf) - 2)
		n = sizeof(buf) - 2;
	buf[n++] = '\n';
	for (i = 0; i < MAX_CLIENTS; i++)
		if (clients[i] >= 0 && write(clients[i], buf, n) < 0) {
			close(clients[i]);
			clients[i] = -1;
		}
	logmsg(LOG_DEBUG, "event: %.*s", n - 1, buf);
}

static void at_send(const char *fmt, ...)
{
	char buf[256];
	va_list ap;
	int n;

	if (rfcomm < 0)
		return;
	buf[0] = '\r';
	buf[1] = '\n';
	va_start(ap, fmt);
	n = vsnprintf(buf + 2, sizeof(buf) - 4, fmt, ap);
	va_end(ap);
	if (n < 0)
		return;
	if (n > (int)sizeof(buf) - 5)
		n = sizeof(buf) - 5;
	n += 2;
	buf[n++] = '\r';
	buf[n++] = '\n';
	logmsg(LOG_DEBUG, "AG> %.*s", n - 4, buf + 2);
	if (write(rfcomm, buf, n) < 0)
		logmsg(LOG_WARNING, "rfcomm write: %s", strerror(errno));
}

static void ciev(int ind, int val)
{
	if (slc && cmer)
		at_send("+CIEV: %d,%d", ind, val);
}

static int str2ba(const char *s, bdaddr_t *ba)
{
	unsigned int b[6];
	int i;

	if (sscanf(s, "%2x:%2x:%2x:%2x:%2x:%2x",
		   &b[5], &b[4], &b[3], &b[2], &b[1], &b[0]) != 6)
		return -1;
	for (i = 0; i < 6; i++)
		ba->b[i] = b[i];
	return 0;
}

static void sco_close(void)
{
	if (sco < 0)
		return;
	close(sco);
	sco = -1;
	logmsg(LOG_INFO, "SCO closed");
}

static void sco_open(void)
{
	struct sockaddr_sco addr = { .sco_family = AF_BLUETOOTH };
	struct bt_voice voice = { .setting = BT_VOICE_CVSD_16BIT };
	struct timeval tv = { .tv_sec = 5 };

	if (sco >= 0 || !slc)
		return;
	sco = socket(AF_BLUETOOTH, SOCK_SEQPACKET, BTPROTO_SCO);
	if (sco < 0) {
		logmsg(LOG_ERR, "SCO socket: %s", strerror(errno));
		return;
	}
	if (bind(sco, (struct sockaddr *)&addr, sizeof(addr)) < 0)
		logmsg(LOG_WARNING, "SCO bind: %s", strerror(errno));
	setsockopt(sco, SOL_BLUETOOTH, BT_VOICE, &voice, sizeof(voice));
	setsockopt(sco, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv));
	str2ba(dev_mac, &addr.sco_bdaddr);
	if (connect(sco, (struct sockaddr *)&addr, sizeof(addr)) < 0) {
		logmsg(LOG_ERR, "SCO connect %s: %s", dev_mac, strerror(errno));
		close(sco);
		sco = -1;
		return;
	}
	logmsg(LOG_INFO, "SCO open to %s", dev_mac);
}

static void rfcomm_close(void)
{
	sco_close();
	if (rfcomm >= 0) {
		close(rfcomm);
		rfcomm = -1;
		event("disconnected");
		logmsg(LOG_INFO, "headset %s disconnected", dev_mac);
	}
	slc = cmer = clip = 0;
	rxlen = 0;
}

static void ring(void)
{
	if (!slc)
		return;
	at_send("RING");
	if (clip && ring_number[0])
		at_send("+CLIP: \"%s\",%d", ring_number,
			ring_number[0] == '+' ? 145 : 129);
	next_ring = time(NULL) + 3;
}

/* --- AT command handling (headset -> AG) -------------------------------- */

static void at_command(char *cmd)
{
	logmsg(LOG_DEBUG, "HF> %s", cmd);

	if (!strncmp(cmd, "AT+BRSF=", 8)) {
		at_send("+BRSF: %d", AG_FEATURES);
	} else if (!strcmp(cmd, "AT+CIND=?")) {
		at_send("+CIND: (\"service\",(0,1)),(\"call\",(0,1)),"
			"(\"callsetup\",(0-3)),(\"callheld\",(0-2)),"
			"(\"signal\",(0-5)),(\"roam\",(0,1)),(\"battchg\",(0-5))");
	} else if (!strcmp(cmd, "AT+CIND?")) {
		at_send("+CIND: 1,%d,%d,0,5,0,5", call, callsetup);
	} else if (!strncmp(cmd, "AT+CMER=", 8)) {
		int a, b, c, d = 0;

		if (sscanf(cmd + 8, "%d,%d,%d,%d", &a, &b, &c, &d) >= 1)
			cmer = d;
		at_send("OK");
		if (!slc) {
			slc = 1;
			event("connected %s", dev_mac);
			logmsg(LOG_INFO, "service level connection with %s", dev_mac);
		}
		return;
	} else if (!strcmp(cmd, "AT+CHLD=?")) {
		at_send("+CHLD: (0,1)");
	} else if (!strncmp(cmd, "AT+CLIP=", 8)) {
		clip = atoi(cmd + 8);
	} else if (!strcmp(cmd, "ATA")) {
		event("answer");
	} else if (!strcmp(cmd, "AT+CHUP")) {
		event("hangup");
	} else if (!strncmp(cmd, "ATD", 3) && cmd[3] != '>') {
		char *end = strchr(cmd, ';');

		if (end)
			*end = 0;
		event("dial %s", cmd + 3);
	} else if (!strcmp(cmd, "AT+BLDN")) {
		event("redial");
	} else if (!strncmp(cmd, "AT+VTS=", 7)) {
		event("dtmf %c", cmd[7]);
	} else if (!strcmp(cmd, "AT+COPS?")) {
		at_send("+COPS: 0,0,\"UFI\"");
	} else if (!strcmp(cmd, "AT+CLCC")) {
		if (call || callsetup)
			at_send("+CLCC: 1,%d,%d,0,0", callsetup == 1 ? 1 : 0,
				call ? 0 : callsetup == 1 ? 4 : callsetup == 3 ? 3 : 2);
	} else if (!strcmp(cmd, "AT+CNUM")) {
		/* no subscriber number */
	} else if (!strncmp(cmd, "AT+BAC=", 7) || !strncmp(cmd, "AT+COPS=", 8) ||
		   !strncmp(cmd, "AT+CCWA=", 8) || !strncmp(cmd, "AT+CMEE=", 8) ||
		   !strncmp(cmd, "AT+NREC=", 8) || !strncmp(cmd, "AT+VGS=", 7) ||
		   !strncmp(cmd, "AT+VGM=", 7) || !strncmp(cmd, "AT+BIA=", 7) ||
		   !strncmp(cmd, "AT+XAPL=", 8) || !strncmp(cmd, "AT+IPHONEACCEV=", 15) ||
		   !strncmp(cmd, "AT+CSRSF=", 9) || !strncmp(cmd, "AT+BTRH", 7) ||
		   !strncmp(cmd, "AT+XEVENT=", 10)) {
		/* accepted, nothing to do */
	} else {
		at_send("ERROR");
		return;
	}
	at_send("OK");
}

static void rfcomm_read(void)
{
	ssize_t n = read(rfcomm, rxbuf + rxlen, sizeof(rxbuf) - 1 - rxlen);
	char *p, *start;

	if (n <= 0) {
		rfcomm_close();
		return;
	}
	rxlen += n;
	rxbuf[rxlen] = 0;
	start = rxbuf;
	while ((p = strpbrk(start, "\r\n"))) {
		*p = 0;
		if (*start)
			at_command(start);
		start = p + 1;
	}
	rxlen = strlen(start);
	memmove(rxbuf, start, rxlen + 1);
	if (rxlen >= sizeof(rxbuf) - 1)
		rxlen = 0;
}

/* --- control socket (ufi-voiced -> AG) ---------------------------------- */

static void ctl_command(int fd, char *line)
{
	char reply[160];

	logmsg(LOG_DEBUG, "ctl: %s", line);
	if (!strncmp(line, "incoming", 8)) {
		snprintf(ring_number, sizeof(ring_number), "%s",
			 line[8] == ' ' ? line + 9 : "");
		callsetup = 1;
		ciev(IND_CALLSETUP, 1);
		ring();
	} else if (!strcmp(line, "outgoing")) {
		callsetup = 2;
		ciev(IND_CALLSETUP, 2);
	} else if (!strcmp(line, "alerting")) {
		callsetup = 3;
		ciev(IND_CALLSETUP, 3);
	} else if (!strcmp(line, "active")) {
		ring_number[0] = 0;
		if (!call) {
			call = 1;
			ciev(IND_CALL, 1);
		}
		if (callsetup) {
			callsetup = 0;
			ciev(IND_CALLSETUP, 0);
		}
		sco_open();
	} else if (!strcmp(line, "end")) {
		ring_number[0] = 0;
		sco_close();
		if (callsetup) {
			callsetup = 0;
			ciev(IND_CALLSETUP, 0);
		}
		if (call) {
			call = 0;
			ciev(IND_CALL, 0);
		}
	} else if (!strcmp(line, "sco on")) {
		sco_open();
	} else if (!strcmp(line, "sco off")) {
		sco_close();
	} else if (!strncmp(line, "connect ", 8)) {
		char path[64], *q;
		DBusMessage *r;

		snprintf(path, sizeof(path), "/org/bluez/hci0/dev_%s", line + 8);
		for (q = path; *q; q++)
			if (*q == ':')
				*q = '_';
		/* Asynchronous: bluetoothd calls our NewConnection before it
		 * answers ConnectProfile, a blocking call would deadlock. */
		r = dbus_message_new_method_call("org.bluez", path,
						 "org.bluez.Device1", "ConnectProfile");
		if (r) {
			const char *uuid = HFP_HS_UUID;

			dbus_message_append_args(r, DBUS_TYPE_STRING, &uuid,
						 DBUS_TYPE_INVALID);
			dbus_connection_send(bus, r, NULL);
			dbus_message_unref(r);
		}
	} else if (!strcmp(line, "status")) {
		snprintf(reply, sizeof(reply), "status %s slc=%d call=%d callsetup=%d sco=%d\n",
			 rfcomm >= 0 ? dev_mac : "-", slc, call, callsetup, sco >= 0);
		if (write(fd, reply, strlen(reply)) < 0)
			return;
	}
}

static void ctl_read(int i)
{
	char buf[256], *p, *start;
	ssize_t n = read(clients[i], buf, sizeof(buf) - 1);

	if (n <= 0) {
		close(clients[i]);
		clients[i] = -1;
		return;
	}
	buf[n] = 0;
	start = buf;
	while ((p = strchr(start, '\n'))) {
		*p = 0;
		if (p > start && p[-1] == '\r')
			p[-1] = 0;
		ctl_command(clients[i], start);
		start = p + 1;
	}
}

static int ctl_listen(const char *path)
{
	struct sockaddr_un sa = { .sun_family = AF_UNIX };
	int fd = socket(AF_UNIX, SOCK_STREAM, 0);

	if (fd < 0)
		return -1;
	snprintf(sa.sun_path, sizeof(sa.sun_path), "%s", path);
	unlink(path);
	if (bind(fd, (struct sockaddr *)&sa, sizeof(sa)) < 0 || listen(fd, 4) < 0) {
		close(fd);
		return -1;
	}
	return fd;
}

/* --- D-Bus: Profile1 object and ProfileManager1 registration ------------ */

static void dict_entry_str(DBusMessageIter *dict, const char *key, const char *val)
{
	DBusMessageIter e, v;

	dbus_message_iter_open_container(dict, DBUS_TYPE_DICT_ENTRY, NULL, &e);
	dbus_message_iter_append_basic(&e, DBUS_TYPE_STRING, &key);
	dbus_message_iter_open_container(&e, DBUS_TYPE_VARIANT, "s", &v);
	dbus_message_iter_append_basic(&v, DBUS_TYPE_STRING, &val);
	dbus_message_iter_close_container(&e, &v);
	dbus_message_iter_close_container(dict, &e);
}

static void dict_entry_bool(DBusMessageIter *dict, const char *key, dbus_bool_t val)
{
	DBusMessageIter e, v;

	dbus_message_iter_open_container(dict, DBUS_TYPE_DICT_ENTRY, NULL, &e);
	dbus_message_iter_append_basic(&e, DBUS_TYPE_STRING, &key);
	dbus_message_iter_open_container(&e, DBUS_TYPE_VARIANT, "b", &v);
	dbus_message_iter_append_basic(&v, DBUS_TYPE_BOOLEAN, &val);
	dbus_message_iter_close_container(&e, &v);
	dbus_message_iter_close_container(dict, &e);
}

static int register_profile(void)
{
	DBusMessage *m, *r;
	DBusMessageIter it, dict;
	DBusError err;
	const char *path = PROFILE_PATH, *uuid = HFP_AG_UUID;

	m = dbus_message_new_method_call("org.bluez", "/org/bluez",
					 "org.bluez.ProfileManager1", "RegisterProfile");
	dbus_message_iter_init_append(m, &it);
	dbus_message_iter_append_basic(&it, DBUS_TYPE_OBJECT_PATH, &path);
	dbus_message_iter_append_basic(&it, DBUS_TYPE_STRING, &uuid);
	dbus_message_iter_open_container(&it, DBUS_TYPE_ARRAY, "{sv}", &dict);
	dict_entry_str(&dict, "Name", "Hands-Free Voice gateway");
	dict_entry_bool(&dict, "AutoConnect", TRUE);
	dbus_message_iter_close_container(&it, &dict);

	dbus_error_init(&err);
	r = dbus_connection_send_with_reply_and_block(bus, m, 10000, &err);
	dbus_message_unref(m);
	if (!r) {
		logmsg(LOG_ERR, "RegisterProfile: %s", err.message);
		dbus_error_free(&err);
		return -1;
	}
	dbus_message_unref(r);
	logmsg(LOG_INFO, "HFP AG profile registered");
	return 0;
}

static DBusHandlerResult profile_msg(DBusConnection *c, DBusMessage *m, void *data)
{
	DBusMessage *reply = NULL;
	(void)data;

	if (dbus_message_is_method_call(m, "org.bluez.Profile1", "NewConnection")) {
		const char *path;
		int fd;
		DBusMessageIter it;

		dbus_message_iter_init(m, &it);
		dbus_message_iter_get_basic(&it, &path);
		dbus_message_iter_next(&it);
		dbus_message_iter_get_basic(&it, &fd);
		if (rfcomm >= 0) {
			logmsg(LOG_WARNING, "already connected, rejecting %s", path);
			close(fd);
			reply = dbus_message_new_error(m, "org.bluez.Error.Rejected", "busy");
		} else {
			const char *d = strstr(path, "dev_");
			int i;

			rfcomm = fd;
			fcntl(fd, F_SETFL, fcntl(fd, F_GETFL) | O_NONBLOCK);
			snprintf(dev_path, sizeof(dev_path), "%s", path);
			snprintf(dev_mac, sizeof(dev_mac), "%s", d ? d + 4 : "?");
			for (i = 0; dev_mac[i]; i++)
				if (dev_mac[i] == '_')
					dev_mac[i] = ':';
			logmsg(LOG_INFO, "headset %s connected (RFCOMM)", dev_mac);
			reply = dbus_message_new_method_return(m);
		}
	} else if (dbus_message_is_method_call(m, "org.bluez.Profile1", "RequestDisconnection")) {
		rfcomm_close();
		reply = dbus_message_new_method_return(m);
	} else if (dbus_message_is_method_call(m, "org.bluez.Profile1", "Release")) {
		reply = dbus_message_new_method_return(m);
	} else {
		return DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
	}
	if (reply) {
		dbus_connection_send(c, reply, NULL);
		dbus_message_unref(reply);
	}
	return DBUS_HANDLER_RESULT_HANDLED;
}

/* Re-register when bluetoothd restarts */
static DBusHandlerResult bus_filter(DBusConnection *c, DBusMessage *m, void *data)
{
	const char *name, *old, *new;
	(void)c; (void)data;

	if (dbus_message_is_signal(m, "org.freedesktop.DBus", "NameOwnerChanged") &&
	    dbus_message_get_args(m, NULL, DBUS_TYPE_STRING, &name, DBUS_TYPE_STRING, &old,
				  DBUS_TYPE_STRING, &new, DBUS_TYPE_INVALID) &&
	    !strcmp(name, "org.bluez")) {
		if (*new) {
			sleep(1);
			register_profile();
		} else {
			rfcomm_close();
		}
	}
	return DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
}

static void on_signal(int sig) { (void)sig; quit = 1; }

int main(int argc, char **argv)
{
	static const DBusObjectPathVTable vtable = { .message_function = profile_msg };
	const char *sock_path = "/var/run/hfp-ag.sock";
	DBusError err;
	int opt, i, bus_fd;

	while ((opt = getopt(argc, argv, "s:v")) != -1) {
		switch (opt) {
		case 's': sock_path = optarg; break;
		case 'v': verbose = 1; break;
		default:
			fprintf(stderr, "usage: %s [-s socket] [-v]\n", argv[0]);
			return 2;
		}
	}
	openlog("hfp-ag", LOG_PID, LOG_DAEMON);
	signal(SIGINT, on_signal);
	signal(SIGTERM, on_signal);
	signal(SIGPIPE, SIG_IGN);
	for (i = 0; i < MAX_CLIENTS; i++)
		clients[i] = -1;

	dbus_error_init(&err);
	bus = dbus_bus_get(DBUS_BUS_SYSTEM, &err);
	if (!bus) {
		logmsg(LOG_ERR, "system bus: %s", err.message);
		return 1;
	}
	dbus_connection_register_object_path(bus, PROFILE_PATH, &vtable, NULL);
	dbus_bus_add_match(bus, "type='signal',sender='org.freedesktop.DBus',"
			   "member='NameOwnerChanged',arg0='org.bluez'", NULL);
	dbus_connection_add_filter(bus, bus_filter, NULL, NULL);
	register_profile();

	ctl_fd = ctl_listen(sock_path);
	if (ctl_fd < 0) {
		logmsg(LOG_ERR, "control socket %s: %s", sock_path, strerror(errno));
		return 1;
	}
	if (!dbus_connection_get_unix_fd(bus, &bus_fd)) {
		logmsg(LOG_ERR, "no D-Bus fd");
		return 1;
	}

	while (!quit) {
		struct pollfd pfd[3 + MAX_CLIENTS];
		int n = 0, timeout = 1000;

		pfd[n++] = (struct pollfd){ .fd = bus_fd, .events = POLLIN };
		pfd[n++] = (struct pollfd){ .fd = ctl_fd, .events = POLLIN };
		pfd[n++] = (struct pollfd){ .fd = rfcomm, .events = POLLIN };
		for (i = 0; i < MAX_CLIENTS; i++)
			pfd[n++] = (struct pollfd){ .fd = clients[i], .events = POLLIN };

		/* Flush pending D-Bus work before sleeping */
		while (dbus_connection_dispatch(bus) == DBUS_DISPATCH_DATA_REMAINS)
			;
		dbus_connection_flush(bus);

		if (poll(pfd, n, timeout) < 0 && errno != EINTR)
			break;

		if (pfd[0].revents)
			dbus_connection_read_write(bus, 0);
		if (pfd[1].revents & POLLIN) {
			int c = accept(ctl_fd, NULL, NULL);

			for (i = 0; c >= 0 && i < MAX_CLIENTS; i++)
				if (clients[i] < 0) {
					clients[i] = c;
					c = -1;
				}
			if (c >= 0)
				close(c);
		}
		if (rfcomm >= 0 && pfd[2].revents)
			rfcomm_read();
		for (i = 0; i < MAX_CLIENTS; i++)
			if (clients[i] >= 0 && pfd[3 + i].revents)
				ctl_read(i);

		if (ring_number[0] || callsetup == 1)
			if (callsetup == 1 && time(NULL) >= next_ring)
				ring();
	}

	rfcomm_close();
	unlink(sock_path);
	return 0;
}
