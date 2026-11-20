// SPDX-License-Identifier: GPL-2.0-or-later
/*
 * qmi-raw -- send an arbitrary QMI request and print the response.
 *
 * Development tool: stock libqmi/qmicli only exposes the messages libqmi
 * knows about.  Several interesting Qualcomm services (IMS settings 0x12,
 * IMS data 0x0302, ...) have messages that are not in libqmi, so this tool
 * builds the QMI payload by hand and lets qmi_device_command_full() deal
 * with QMUX framing, the client ID and the proxy.
 *
 * Usage:
 *   qmi-raw <device> <service-id> <message-id> [tlv ...]
 *
 *   <service-id>   decimal or 0x-prefixed
 *   <message-id>   decimal or 0x-prefixed
 *   tlv            <type>=<hexvalue>, e.g. 10=01 or 13=0300000000000000
 *
 * Example (Set IMS Services Enabled Setting, the pmOS/OpenIMSd sequence):
 *   qmi-raw /dev/wwan0qmi0 0x12 0x008f 10=01 12=01 13=0300000000000000 \
 *           14=01 15=01000000 18=01 19=01
 */

#include <gio/gio.h>
#include <glib.h>
#include <libqmi-glib.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static GMainLoop *loop;
static int exit_code = 1;

struct ctx {
	QmiDevice *dev;
	QmiClient *client;
	QmiService service;
	guint16 msg_id;
	guint8 *tlvs;
	gsize tlvs_len;

	/* Chained request: after the first response, resend <then_msg> on the
	 * same client with TLV 0x01 set to the value of <handle_tlv>.  Used to
	 * read WDS Get Current Settings right after Start Network. */
	int stage;
	guint16 then_msg;
	int handle_tlv;
};

static void send_request(struct ctx *c, guint8 cid);

static void
fail(const char *what, GError *err)
{
	g_printerr("%s: %s\n", what, err ? err->message : "unknown error");
	if (err)
		g_error_free(err);
	exit_code = 1;
	g_main_loop_quit(loop);
}

static void
on_release(GObject *src, GAsyncResult *res, gpointer user_data)
{
	GError *err = NULL;

	(void)user_data;
	if (!qmi_device_release_client_finish(QMI_DEVICE(src), res, &err)) {
		g_printerr("cannot release client: %s\n",
			   err ? err->message : "unknown error");
		if (err)
			g_error_free(err);
	}
	g_main_loop_quit(loop);
}

static void
on_response(GObject *src, GAsyncResult *res, gpointer user_data)
{
	struct ctx *c = user_data;
	GError *err = NULL;
	QmiMessage *resp;

	resp = qmi_device_command_full_finish(QMI_DEVICE(src), res, &err);
	if (!resp) {
		g_printerr("command failed: %s\n",
			   err ? err->message : "unknown error");
		if (err)
			g_error_free(err);
		exit_code = 1;
	} else {
		gchar *printable = qmi_message_get_printable(resp, "response");

		g_print("%s\n", printable);
		g_free(printable);

		if (c->stage == 0 && c->then_msg != 0) {
			guint16 hlen = 0;
			const guint8 *h;

			h = qmi_message_get_raw_tlv(resp, (guint8)c->handle_tlv,
						    &hlen);
			if (h != NULL && hlen > 0 && hlen <= 64) {
				guint8 *buf = g_malloc(3 + hlen);

				buf[0] = 0x01;          /* packet data handle */
				buf[1] = hlen & 0xff;
				buf[2] = (hlen >> 8) & 0xff;
				memcpy(buf + 3, h, hlen);

				c->msg_id = c->then_msg;
				c->tlvs = buf;
				c->tlvs_len = 3 + hlen;
				c->stage = 1;
				qmi_message_unref(resp);
				g_print("---- chained request 0x%04x with TLV 0x01 (%zu bytes)\n",
					c->then_msg, hlen);
				send_request(c, qmi_client_get_cid(c->client));
				return;
			}
			g_printerr("no handle in TLV 0x%02x; not chaining\n",
				   c->handle_tlv);
		}

		qmi_message_unref(resp);
		exit_code = 0;
	}

	/*
	 * Interactive testing knobs.  A VOICE client that goes away takes the
	 * call it created with it, so a one-shot dial is useless: either hold the
	 * client for a while (QMI_HOLD_SECS) or leave it allocated
	 * (QMI_NO_RELEASE, leaks one CID per run -- use sparingly).
	 */
	{
		const char *hold = getenv("QMI_HOLD_SECS");

		if (hold != NULL && atoi(hold) > 0) {
			g_print("holding client for %d s\n", atoi(hold));
			g_usleep((gulong)atoi(hold) * G_USEC_PER_SEC);
		}
	}
	if (getenv("QMI_NO_RELEASE") != NULL) {
		g_print("client %u left allocated (QMI_NO_RELEASE)\n",
			qmi_client_get_cid(c->client));
		g_main_loop_quit(loop);
		return;
	}

	/*
	 * Every run allocates a client ID on the modem.  Without this release the
	 * service eventually runs out of client slots and starts answering every
	 * request with QMI error 2 (NoMemory) -- which looks exactly like "the
	 * service is dead".  Release before exiting.
	 */
	qmi_device_release_client(c->dev, c->client,
				  QMI_DEVICE_RELEASE_CLIENT_FLAGS_RELEASE_CID,
				  10, NULL, on_release, c);
}

static void
send_request(struct ctx *c, guint8 cid)
{
	GByteArray *data;
	QmiMessage *msg;
	GError *err = NULL;
	guint8 hdr[7];

	data = g_byte_array_new();

	hdr[0] = 0x00;                                  /* request */
	hdr[1] = 0x01;                                  /* transaction, LE */
	hdr[2] = 0x00;
	hdr[3] = c->msg_id & 0xff;                      /* message id, LE */
	hdr[4] = (c->msg_id >> 8) & 0xff;
	hdr[5] = c->tlvs_len & 0xff;                    /* payload length, LE */
	hdr[6] = (c->tlvs_len >> 8) & 0xff;

	g_byte_array_append(data, hdr, sizeof(hdr));
	if (c->tlvs_len)
		g_byte_array_append(data, c->tlvs, c->tlvs_len);

	msg = qmi_message_new_from_data(c->service, cid, data, &err);
	g_byte_array_unref(data);
	if (!msg) {
		fail("cannot build message", err);
		return;
	}

	qmi_device_command_full(c->dev, msg, NULL, 10, NULL, on_response, c);
	qmi_message_unref(msg);
}

static void
on_client(GObject *src, GAsyncResult *res, gpointer user_data)
{
	struct ctx *c = user_data;
	GError *err = NULL;
	QmiClient *client;

	client = qmi_device_allocate_client_finish(QMI_DEVICE(src), res, &err);
	if (!client) {
		fail("cannot allocate client", err);
		return;
	}

	c->client = client;
	send_request(c, qmi_client_get_cid(client));
}

static void
on_open(GObject *src, GAsyncResult *res, gpointer user_data)
{
	struct ctx *c = user_data;
	GError *err = NULL;

	if (!qmi_device_open_finish(QMI_DEVICE(src), res, &err)) {
		fail("cannot open device", err);
		return;
	}

	qmi_device_allocate_client(c->dev, c->service, QMI_CID_NONE, 10,
				   NULL, on_client, c);
}

static void
on_device(GObject *src, GAsyncResult *res, gpointer user_data)
{
	struct ctx *c = user_data;
	GError *err = NULL;

	(void)src;
	c->dev = qmi_device_new_finish(res, &err);
	if (!c->dev) {
		fail("cannot create device", err);
		return;
	}

	qmi_device_open(c->dev,
			QMI_DEVICE_OPEN_FLAGS_PROXY | QMI_DEVICE_OPEN_FLAGS_AUTO,
			10, NULL, on_open, c);
}

static int
hexval(char ch)
{
	if (ch >= '0' && ch <= '9')
		return ch - '0';
	if (ch >= 'a' && ch <= 'f')
		return ch - 'a' + 10;
	if (ch >= 'A' && ch <= 'F')
		return ch - 'A' + 10;
	return -1;
}

int
main(int argc, char **argv)
{
	struct ctx c = { 0 };
	GByteArray *tlvs;
	GFile *file;
	int i;

	if (argc < 4) {
		g_printerr("usage: %s <device> <service-id> <message-id> [type=hexvalue ...]\n",
			   argv[0]);
		return 2;
	}

	c.service = (QmiService)strtol(argv[2], NULL, 0);
	c.msg_id = (guint16)strtol(argv[3], NULL, 0);

	tlvs = g_byte_array_new();
	for (i = 4; i < argc; i++) {
		char *eq = strchr(argv[i], '=');
		char *hex;
		guint8 type, len_lo, len_hi;
		gsize hexlen, j;

		if (!eq) {
			g_printerr("bad TLV '%s' (expected type=hexvalue)\n", argv[i]);
			return 2;
		}
		*eq = '\0';
		type = (guint8)strtol(argv[i], NULL, 0);
		hex = eq + 1;
		hexlen = strlen(hex);
		if (hexlen % 2) {
			g_printerr("bad TLV value '%s' (odd number of hex digits)\n", hex);
			return 2;
		}
		hexlen /= 2;
		len_lo = hexlen & 0xff;
		len_hi = (hexlen >> 8) & 0xff;
		g_byte_array_append(tlvs, &type, 1);
		g_byte_array_append(tlvs, &len_lo, 1);
		g_byte_array_append(tlvs, &len_hi, 1);
		for (j = 0; j < hexlen; j++) {
			int hi = hexval(hex[j * 2]);
			int lo = hexval(hex[j * 2 + 1]);
			guint8 byte;

			if (hi < 0 || lo < 0) {
				g_printerr("bad hex digit in '%s'\n", hex);
				return 2;
			}
			byte = (guint8)((hi << 4) | lo);
			g_byte_array_append(tlvs, &byte, 1);
		}
	}

	c.tlvs = g_memdup2(tlvs->data, tlvs->len);
	c.tlvs_len = tlvs->len;
	g_byte_array_unref(tlvs);

	{
		const char *then = g_getenv("QMI_THEN_MSG");
		const char *htlv = g_getenv("QMI_HANDLE_TLV");

		if (then != NULL && *then != '\0') {
			c.then_msg = (guint16)strtol(then, NULL, 0);
			c.handle_tlv = htlv ? (int)strtol(htlv, NULL, 0) : 0x01;
		}
	}

	g_print("service 0x%02x message 0x%04x payload %zu bytes\n",
		c.service, c.msg_id, c.tlvs_len);

	loop = g_main_loop_new(NULL, FALSE);
	file = g_file_new_for_path(argv[1]);
	qmi_device_new(file, NULL, on_device, &c);
	g_main_loop_run(loop);

	g_main_loop_unref(loop);
	g_object_unref(file);
	g_free(c.tlvs);

	return exit_code;
}
