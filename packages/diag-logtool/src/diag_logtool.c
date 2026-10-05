// SPDX-License-Identifier: GPL-2.0-only
/*
 * diag_logtool.c - Qualcomm DIAG client for the HMU05 SMD DIAG channel
 *                  bridged to /dev/rpmsg0 (see Doc 136/137).
 *
 * Part of the `diag-logtool` OpenWrt package. Run `diag-bind` first (or install
 * the diag-bind service) so /dev/rpmsg0 and /dev/rpmsg1 exist.
 *
 * Transport facts established empirically (Doc 136):
 *   - Requests are sent RAW (no HDLC framing, no CRC). HDLC-framed requests
 *     are rejected by the modem.
 *   - Responses carry a trailing CRC + 0x7E delimiter.
 *   - Each read() returns exactly ONE DIAG message; an undersized buffer
 *     silently truncates it. This tool always reads with a 4096-byte buffer.
 *   - /dev/rpmsg0 is exclusive-open; one process must do both read and write.
 *
 * DIAG_LOG_CONFIG_F (0x73) layout - VERIFIED against the reference Qualcomm
 * diag driver, drivers/char/diag/diag_masks.c:diag_process_apps_masks()
 * (GitIgnore/android_kernel_zte_msm8916). The AP-side decoder is:
 *
 *     if (*buf == 0x73 && *(int *)(buf+4) == 3) {        // SET log masks
 *         buf += 8;
 *         diag_update_log_mask(*(int *)buf,             // equip_id  @ 8
 *                              buf+8,                   // mask      @ 16
 *                              *(int *)(buf+4));        // num_items @ 12
 *     }
 *     else if (*buf == 0x73 && *(int *)(buf+4) == 4) {   // GET log masks
 *         equip_id = *(int *)(buf + 8);
 *     }
 *     else if (*buf == 0x73 && *(int *)(buf+4) == 0) {   // DISABLE log masks
 *
 * so the on-wire request is:
 *
 *     off 0     : u8  0x73
 *     off 1..3  : 3 reserved bytes (zero)
 *     off 4..7  : u32 LE operation   (0=disable, 1,2 accepted, 3=set, 4=get)
 *     off 8..11 : u32 LE equip_id    (op 3/4)
 *     off 12..15: u32 LE num_items   (op 3; last log code for this equip_id)
 *     off 16..  : mask bytes, length = (num_items + 7) / 8
 *
 * The response is:
 *
 *     off 0..3  : 73 00 00 00
 *     off 4..7  : u32 LE operation (echo)
 *     off 8..11 : u32 LE status
 *     off 12..  : payload
 *
 * An ERROR reply instead starts with 0x14 (DIAG_BAD_PARM_F) followed by the
 * echoed request. 0x13 is DIAG_BAD_CMD_F (verified with an unknown opcode).
 *
 * The mask is delivered to the modem over the *control* channel DIAG_CNTL,
 * not the data channel, using DIAG_CTRL_MSG_* packets (see `cntl-enable`).
 *
 * Build (OpenWrt aarch64 musl):
 *   aarch64-openwrt-linux-musl-gcc -static -Os -fno-stack-protector \
 *       -o diag_logtool diag_logtool.c
 *
 * Usage:
 *   diag_logtool selftest                       VERNO (0x00) round-trip
 *   diag_logtool raw <hexbytes>                 send a raw payload
 *   diag_logtool listen <seconds>               capture + decode packets
 *   diag_logtool log-config <op>                generic 0x73 request
 *   diag_logtool log-disable                    op 0
 *   diag_logtool log-enable                     op 1
 *   diag_logtool set-mask <equip> <nitems> <maskhex>   op 3
 *   diag_logtool get-mask <equip>               op 4
 *   diag_logtool cntl-enable [dev] [nssid]      control-channel enable sequence
 *   diag_logtool cntl-dump [dev]                read control-channel replies
 *
 * nssid (default 1) = number of single-SSID F3 ranges to sweep.  The stock
 * template only enables SSID 0; pass a larger value (e.g. 64) to cover the
 * modem's full F3 subsystem space.
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <time.h>
#include <sys/select.h>
#include <stdint.h>

#define DEV_PATH  "/dev/rpmsg0"
#define CNTL_PATH "/dev/rpmsg1"
#define BUFSZ     4096

/* DIAG command codes */
#define DIAG_VERNO_F          0x00
#define DIAG_LOG_F            0x10
#define DIAG_MSG_F            0x11
#define DIAG_BAD_CMD_F        0x13
#define DIAG_BAD_PARM_F       0x14
#define DIAG_LOG_CONFIG_F     0x73

/* DIAG_LOG_CONFIG_F operations */
#define LOGCFG_DISABLE        0
#define LOGCFG_ENABLE         1
#define LOGCFG_OP2            2
#define LOGCFG_SET_MASK       3
#define LOGCFG_GET_MASK       4

/* DIAG control-channel packet ids (drivers/char/diag/diagfwd_cntl.h) */
#define DIAG_CTRL_MSG_FEATURE     8
#define DIAG_CTRL_MSG_LOG_MASK    9
#define DIAG_CTRL_MSG_EVENT_MASK  10
#define DIAG_CTRL_MSG_F3_MASK     11

/* mask status (drivers/char/diag/diag_masks.c) */
#define DIAG_CTRL_MASK_ALL_DISABLED 1
#define DIAG_CTRL_MASK_ALL_ENABLED  2
#define DIAG_CTRL_MASK_VALID        3

/* feature bits (drivers/char/diag/diagfwd_cntl.h) */
#define F_DIAG_INT_FEATURE_MASK              0x01
#define F_DIAG_OVER_STM                      0x02
#define F_DIAG_LOG_ON_DEMAND_RSP_ON_MASTER   0x04
#define F_DIAG_REQ_RSP_CHANNEL               0x10
#define F_DIAG_HDLC_ENCODE_IN_APPS_MASK      0x40

#define LOG_ITEMS_TO_SIZE(n)  (((n) + 7) / 8)

static int g_fd = -1;

static int open_dev(const char *path) {
    g_fd = open(path, O_RDWR);
    if (g_fd < 0) {
        fprintf(stderr, "open(%s): %s\n", path, strerror(errno));
        return -1;
    }
    return 0;
}

static int hex2bin(const char *hex, unsigned char *out, int maxlen) {
    int n = 0;
    while (hex[0] && hex[1] && n < maxlen) {
        unsigned int v;
        if (sscanf(hex, "%2x", &v) != 1) return -1;
        out[n++] = (unsigned char)v;
        hex += 2;
    }
    return n;
}

static void hexdump(const unsigned char *b, int n) {
    for (int i = 0; i < n; i++) {
        printf("%02x ", b[i]);
        if ((i & 15) == 15) printf("\n");
    }
    if (n & 15) printf("\n");
}

/* Print bytes, rendering printable ASCII for readability */
static void hexdump_ascii(const unsigned char *b, int n) {
    for (int i = 0; i < n; i++) {
        printf("%02x ", b[i]);
        if ((i & 15) == 15) {
            printf(" |");
            for (int j = i - 15; j <= i; j++)
                putchar((b[j] >= 32 && b[j] < 127) ? b[j] : '.');
            printf("|\n");
        }
    }
    if (n & 15) {
        int pad = 16 - (n & 15);
        for (int j = 0; j < pad; j++) printf("   ");
        printf(" |");
        for (int j = n - (n & 15); j < n; j++)
            putchar((b[j] >= 32 && b[j] < 127) ? b[j] : '.');
        printf("|\n");
    }
}

static int send_payload(const unsigned char *p, int n) {
    ssize_t w = write(g_fd, p, n);
    if (w != n) {
        fprintf(stderr, "write: %zd (%s)\n", w, strerror(errno));
        return -1;
    }
    return 0;
}

/* Read one message with a timeout. Returns bytes read, 0 on timeout, -1 error. */
static int read_msg(unsigned char *buf, int bufsz, int timeout_ms) {
    fd_set rfds;
    struct timeval tv;
    FD_ZERO(&rfds);
    FD_SET(g_fd, &rfds);
    tv.tv_sec = timeout_ms / 1000;
    tv.tv_usec = (timeout_ms % 1000) * 1000;

    int r = select(g_fd + 1, &rfds, NULL, NULL, &tv);
    if (r <= 0) return 0;            /* timeout */
    ssize_t n = read(g_fd, buf, bufsz);
    if (n < 0) {
        fprintf(stderr, "read: %s\n", strerror(errno));
        return -1;
    }
    return (int)n;
}

static double now_s(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec + ts.tv_nsec / 1e9;
}

/* CRC-16/CCITT (poly 0x1021, seed 0xFFFF) as used by diagchar_hdlc.c
 * (CRC_16_L_SEED / crc_ccitt_byte). */
static uint16_t crc16_ccitt(uint16_t crc, const unsigned char *p, int n) {
    for (int i = 0; i < n; i++) {
        crc ^= (uint16_t)(p[i] << 8);
        for (int b = 0; b < 8; b++)
            crc = (crc & 0x8000) ? (uint16_t)((crc << 1) ^ 0x1021)
                                 : (uint16_t)(crc << 1);
    }
    return crc;
}

/*
 * HDLC-frame a DIAG payload exactly as diagchar_hdlc.c does:
 *   [0x7e] <escaped payload> <escaped ~crc_lo> <escaped ~crc_hi> 0x7e
 * Escape 0x7e and 0x7d as 0x7d ^ (byte ^ 0x20).  The leading flag is optional
 * (the driver only emits the trailing one); pass lead=1 for a fresh frame.
 */
static int hdlc_frame(const unsigned char *in, int n, unsigned char *out,
                      int outsz, int lead) {
    unsigned char raw[BUFSZ];
    int rl = 0, o = 0;
    uint16_t crc;

    if (n < 0 || n > (int)sizeof(raw) - 3) return -1;
    memcpy(raw, in, n);
    rl = n;
    crc = ~crc16_ccitt(0xFFFF, in, n);
    raw[rl++] = (unsigned char)(crc & 0xFF);
    raw[rl++] = (unsigned char)((crc >> 8) & 0xFF);

    if (lead) {
        if (o >= outsz) return -1;
        out[o++] = 0x7e;
    }
    for (int i = 0; i < rl; i++) {
        unsigned char b = raw[i];
        if (b == 0x7e || b == 0x7d) {
            if (o + 2 > outsz) return -1;
            out[o++] = 0x7d;
            out[o++] = (unsigned char)(b ^ 0x20);
        } else {
            if (o + 1 > outsz) return -1;
            out[o++] = b;
        }
    }
    if (o >= outsz) return -1;
    out[o++] = 0x7e;
    return o;
}

/*
 * Read until a message whose first byte is `code` appears, then return it.
 *
 * The modem interleaves a continuous stream (LOG_F 0x10, MSG_F 0x11, and the
 * 0x79 / 0x92 control messages) with command replies.  Each message is
 * terminated by a 2-byte CRC followed by 0x7e, and a single read() may coalesce
 * many messages, so a reply can be preceded/followed by stream traffic and may
 * even be split across two reads.  We therefore accumulate reads and look for
 * a message start -- either at buffer offset 0 or immediately after a 0x7e --
 * whose first byte matches `code`, terminated by the next 0x7e.
 *
 * Returns the message length, 0 on timeout, -1 on error.
 */
static int read_reply(unsigned char code, unsigned char *out, int outsz,
                      int timeout_ms) {
    static unsigned char acc[1 << 20];
    int acclen = 0;
    double t0 = now_s();

    for (;;) {
        int i;
        for (i = 0; i < acclen; i++) {
            int match;
            if (code == 0)
                /* any message that is not part of the continuous stream */
                match = !(acc[i] == 0x10 || acc[i] == 0x11 ||
                          acc[i] == 0x79 || acc[i] == 0x92);
            else
                match = (acc[i] == code);
            if (!match) continue;
            if (i != 0 && acc[i - 1] != 0x7e) continue;   /* must be a msg start */
            /* first candidate: look for its delimiter */
            for (int j = i + 1; j < acclen; j++) {
                if (acc[j] == 0x7e) {
                    int len = j - i + 1;
                    if (len > outsz) len = outsz;
                    memcpy(out, acc + i, len);
                    return len;
                }
            }
            break;      /* candidate present but incomplete -> read more */
        }

        int remain = timeout_ms - (int)((now_s() - t0) * 1000.0);
        if (remain <= 0) return 0;

        if (acclen > (int)sizeof(acc) - BUFSZ) {   /* keep tail for split msgs */
            int keep = 4096;
            memmove(acc, acc + acclen - keep, keep);
            acclen = keep;
        }

        int r = read_msg(acc + acclen, sizeof(acc) - acclen, remain);
        if (r < 0) return -1;
        if (r == 0) continue;
        acclen += r;
    }
}

static void put_le32(unsigned char *p, uint32_t v) {
    p[0] = (unsigned char)(v & 0xFF);
    p[1] = (unsigned char)((v >> 8) & 0xFF);
    p[2] = (unsigned char)((v >> 16) & 0xFF);
    p[3] = (unsigned char)((v >> 24) & 0xFF);
}

/* ------------------------------------------------------------------ */

static int cmd_raw(const char *hex) {
    unsigned char req[BUFSZ], resp[BUFSZ];
    int n = hex2bin(hex, req, sizeof(req));
    if (n <= 0) { fprintf(stderr, "bad hex\n"); return 1; }

    printf("TX (%d bytes): ", n);
    hexdump(req, n);

    if (send_payload(req, n) < 0) return 1;

    int r = read_msg(resp, sizeof(resp), 5000);
    if (r == 0) { printf("RX: <timeout, no response>\n"); return 0; }
    if (r < 0) return 1;

    printf("RX (%d bytes):\n", r);
    hexdump_ascii(resp, r);
    return 0;
}

/*
 * Build and send a DIAG_LOG_CONFIG_F request.
 *   operation : 0..4
 *   equip_id  : used by op 3 and 4
 *   num_items : used by op 3 (last log code for this equip_id)
 *   mask      : used by op 3, length = LOG_ITEMS_TO_SIZE(num_items)
 */
static int log_config(uint32_t operation, uint32_t equip_id,
                      uint32_t num_items, const unsigned char *mask) {
    unsigned char req[BUFSZ], resp[BUFSZ];
    int mask_len = (operation == LOGCFG_SET_MASK) ? LOG_ITEMS_TO_SIZE(num_items) : 0;
    int n = 16 + mask_len;

    memset(req, 0, sizeof(req));
    req[0] = DIAG_LOG_CONFIG_F;
    /* req[1..3] stay zero: reserved */
    put_le32(req + 4, operation);
    put_le32(req + 8, equip_id);
    put_le32(req + 12, num_items);
    if (mask_len && mask)
        memcpy(req + 16, mask, mask_len);

    printf("TX DIAG_LOG_CONFIG_F: operation=%u equip_id=%u num_items=%u "
           "mask_len=%d\n", operation, equip_id, num_items, mask_len);
    printf("TX (%d bytes): ", n);
    hexdump(req, n);

    if (send_payload(req, n) < 0) return 1;

    int r = read_msg(resp, sizeof(resp), 5000);
    if (r == 0) { printf("RX: <timeout>\n"); return 0; }
    if (r < 0) return 1;

    printf("RX (%d bytes):\n", r);
    hexdump_ascii(resp, r);

    if (r >= 1 && resp[0] == DIAG_BAD_PARM_F) {
        printf("=> REJECTED: DIAG_BAD_PARM_F (0x14)\n");
        return 0;
    }
    if (r >= 1 && resp[0] == DIAG_BAD_CMD_F) {
        printf("=> REJECTED: DIAG_BAD_CMD_F (0x13)\n");
        return 0;
    }
    if (r >= 8 && resp[0] == DIAG_LOG_CONFIG_F) {
        uint32_t op_echo = resp[4] | (resp[5] << 8) | (resp[6] << 16) | ((uint32_t)resp[7] << 24);
        uint32_t status  = (r >= 12)
            ? (resp[8] | (resp[9] << 8) | (resp[10] << 16) | ((uint32_t)resp[11] << 24))
            : 0;
        printf("=> ACCEPTED: op_echo=%u status=%u\n", op_echo, status);
        return 0;
    }
    printf("=> unexpected reply shape\n");
    return 0;
}

/* Decode a DIAG_LOG_F / DIAG_MSG_F packet header */
static void decode_log(const unsigned char *b, int n) {
    if (n < 3) return;
    if (b[0] == DIAG_LOG_F) {
        int len = b[1] | (b[2] << 8);
        printf("    LOG_F: declared_len=%d actual=%d\n", len, n - 3);
    } else if (b[0] == DIAG_MSG_F) {
        printf("    MSG_F (F3): %d bytes\n", n);
        /* F3 payloads usually carry ASCII around offset 12+ */
        printf("      text: ");
        for (int i = 0; i < n; i++)
            putchar((b[i] >= 32 && b[i] < 127) ? b[i] : '.');
        printf("\n");
    }
}

static int cmd_listen(int seconds) {
    unsigned char buf[BUFSZ];
    double t0 = now_s();
    long total = 0, msgs = 0, logs = 0, msgsf3 = 0;

    printf("Listening on %s for %d s (one message per read)...\n", DEV_PATH, seconds);

    while ((now_s() - t0) < seconds) {
        int r = read_msg(buf, sizeof(buf), 500);
        if (r < 0) break;            /* device gone (EPIPE after an SSR) */
        if (r == 0) continue;        /* timeout */
        total += r;
        msgs++;
        printf("[%8.3f] RX %d bytes: ", now_s() - t0, r);
        hexdump_ascii(buf, r);
        if (buf[0] == DIAG_LOG_F) { logs++; decode_log(buf, r); }
        else if (buf[0] == DIAG_MSG_F) { msgsf3++; decode_log(buf, r); }
    }

    printf("--- %ld messages, %ld bytes, %ld LOG_F, %ld MSG_F ---\n",
           msgs, total, logs, msgsf3);
    return 0;
}

/*
 * Capture raw DIAG messages to a file as a length-prefixed stream so the
 * message boundaries survive (each read() returns exactly one message).
 * Record format: u32 LE length, then the message bytes.
 */
static int cmd_capture_send(int seconds, const char *path, const char *hex);

static int cmd_capture(int seconds, const char *path) {
    return cmd_capture_send(seconds, path, NULL);
}

/*
 * Like cmd_capture, but send a raw payload first.  Records every read() as a
 * length-prefixed blob so the request/response relationship can be analysed
 * offline (the modem's reply is interleaved with the continuous stream).
 */
static int cmd_capture_send(int seconds, const char *path, const char *hex) {
    unsigned char buf[BUFSZ];
    double t0 = now_s();
    long total = 0, msgs = 0, logs = 0, msgsf3 = 0;
    int fd = open(path, O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (fd < 0) { fprintf(stderr, "open(%s): %s\n", path, strerror(errno)); return 1; }

    if (hex && hex[0]) {
        unsigned char req[BUFSZ];
        int n = hex2bin(hex, req, sizeof(req));
        if (n <= 0) { fprintf(stderr, "bad hex\n"); close(fd); return 1; }
        printf("TX (%d bytes): ", n);
        hexdump(req, n);
        if (send_payload(req, n) < 0) { close(fd); return 1; }
        t0 = now_s();               /* start the capture window after the TX */
    }

    printf("Capturing %s for %d s -> %s\n", DEV_PATH, seconds, path);

    while ((now_s() - t0) < seconds) {
        int r = read_msg(buf, sizeof(buf), 500);
        if (r < 0) break;            /* device gone (EPIPE after an SSR) */
        if (r == 0) continue;        /* timeout */
        unsigned char hdr[4] = {
            (unsigned char)(r & 0xFF), (unsigned char)((r >> 8) & 0xFF),
            (unsigned char)((r >> 16) & 0xFF), (unsigned char)((r >> 24) & 0xFF)
        };
        if (write(fd, hdr, 4) != 4 || write(fd, buf, r) != r) {
            fprintf(stderr, "write(%s): %s\n", path, strerror(errno));
            break;
        }
        total += r;
        msgs++;
        if (buf[0] == DIAG_LOG_F) logs++;
        else if (buf[0] == DIAG_MSG_F) msgsf3++;
    }

    close(fd);
    printf("--- %ld messages, %ld bytes, %ld LOG_F, %ld MSG_F -> %s ---\n",
           msgs, total, logs, msgsf3, path);
    return 0;
}

static int cmd_selftest(void) {
    unsigned char req[1] = { DIAG_VERNO_F };
    unsigned char resp[BUFSZ];

    printf("selftest: sending DIAG_VERNO_F (0x00)\n");
    if (send_payload(req, 1) < 0) return 1;

    int r = read_reply(DIAG_VERNO_F, resp, sizeof(resp), 5000);
    if (r <= 0) { printf("selftest: FAIL (no response)\n"); return 1; }

    printf("selftest: got %d bytes\n", r);
    hexdump_ascii(resp, r);

    if (resp[0] == DIAG_VERNO_F && r > 8) {
        printf("selftest: PASS\n");
        return 0;
    }
    printf("selftest: UNEXPECTED reply\n");
    return 1;
}

/* Discard any already-queued messages (e.g. a spontaneous reply left over from
 * the previous request) so the next read returns OUR reply, not a stale one. */
static void drain_quiet(int ms) {
    unsigned char b[BUFSZ];
    while (read_msg(b, sizeof(b), ms) > 0) { /* discard */ }
}

/*
 * Send a raw payload and wait for a reply whose first byte equals `expect`,
 * skipping the F3/log stream.  This is the instrument `raw` should have been:
 * it isolates the modem's actual answer from the continuous stream.
 */
static int cmd_req(const char *hex, unsigned int expect) {
    unsigned char req[BUFSZ], resp[BUFSZ];
    int n = hex2bin(hex, req, sizeof(req));
    if (n <= 0) { fprintf(stderr, "bad hex\n"); return 1; }

    printf("TX (%d bytes): ", n);
    hexdump(req, n);

    drain_quiet(150);           /* flush stale replies before our request */
    if (send_payload(req, n) < 0) return 1;

    int r = read_reply((unsigned char)expect, resp, sizeof(resp), 5000);
    if (r == 0) {
        printf("RX: <timeout, no reply with code 0x%02x>\n", expect);
        return 0;
    }
    if (r < 0) return 1;

    printf("RX (%d bytes):\n", r);
    hexdump_ascii(resp, r);
    return 0;
}

/*
 * HDLC-frame a payload and send it, then wait for a reply.  Required on the
 * DIAG_CMD (request/response) channel, whose endpoint has encode_hdlc set.
 * `lead` selects whether a leading 0x7e flag is emitted.
 */
static int cmd_hdlc(const char *hex, unsigned int expect, int lead) {
    unsigned char req[BUFSZ], frame[BUFSZ], resp[BUFSZ];
    int n = hex2bin(hex, req, sizeof(req));
    if (n <= 0) { fprintf(stderr, "bad hex\n"); return 1; }

    int fl = hdlc_frame(req, n, frame, sizeof(frame), lead);
    if (fl < 0) { fprintf(stderr, "hdlc_frame failed\n"); return 1; }

    printf("TX payload (%d bytes) -> HDLC frame (%d bytes): ", n, fl);
    hexdump(frame, fl);

    drain_quiet(150);
    if (send_payload(frame, fl) < 0) return 1;

    int r = read_reply((unsigned char)expect, resp, sizeof(resp), 5000);
    if (r == 0) {
        printf("RX: <timeout, no reply with code 0x%02x>\n", expect);
        return 0;
    }
    if (r < 0) return 1;

    printf("RX (%d bytes):\n", r);
    hexdump_ascii(resp, r);
    return 0;
}

/* ------------------------------------------------------------------ */
/* Control channel (DIAG_CNTL) - mask delivery to the modem            */
/* ------------------------------------------------------------------ */

static int send_ctrl(const char *what, const unsigned char *p, int n) {
    printf("CNTL TX %-14s (%2d bytes): ", what, n);
    hexdump(p, n);
    if (send_payload(p, n) < 0) return -1;
    return 0;
}

/*
 * Replicates the AP-side sequence from diag_masks.c:diag_mask_update_fn():
 * feature mask, msg/F3 mask, log mask, event mask - all in ALL_ENABLED form.
 */
static int cmd_cntl_enable_n(const char *path, int nssid, int span) {
    unsigned char pkt[64];

    if (open_dev(path) < 0) return 1;

    /* 1. DIAG_CTRL_MSG_FEATURE - advertise logging support to the modem */
    memset(pkt, 0, sizeof(pkt));
    put_le32(pkt + 0, DIAG_CTRL_MSG_FEATURE);
    put_le32(pkt + 4, 4 + 2);                 /* ctrl_pkt_data_len */
    put_le32(pkt + 8, 2);                     /* feature_mask_len  */
    pkt[12] = F_DIAG_INT_FEATURE_MASK
            | F_DIAG_LOG_ON_DEMAND_RSP_ON_MASTER
            | F_DIAG_REQ_RSP_CHANNEL;
    pkt[13] = F_DIAG_OVER_STM;
    if (send_ctrl("FEATURE", pkt, 14) < 0) return 1;

    /* 2. DIAG_CTRL_MSG_F3_MASK - one packet per SSID range
     * (struct diag_ctrl_msg_mask).  The AP reference driver
     * (diag_masks.c:diag_send_msg_mask_update) sets ssid_first/ssid_last to the
     * REAL range taken from its own table - one packet per range - and never
     * puts ALL_SSID (0xFFFF) on the wire.
     *
     * `span` is how many SSIDs one packet covers.  span == 1 reproduces the
     * original ssid s..s packet byte for byte.  span > 1 matters because the
     * enable is a RACE against the modem's boot-time F3 history: 256 single-SSID
     * packets take long enough that the boot chunk can be overwritten before it
     * is flushed, which is what puts a capture in Doc 203's "blind" class.  One
     * packet covering 0..255 (span 256) enables the same set in one shot. */
    if (span < 1) span = 1;
    for (int s = 0; s < nssid; s += span) {
        int f = s, l = s + span - 1;
        if (l >= nssid) l = nssid - 1;
        memset(pkt, 0, sizeof(pkt));
        put_le32(pkt + 0, DIAG_CTRL_MSG_F3_MASK);
        put_le32(pkt + 4, 11 + 4);            /* data_len = 11 + 4*size */
        pkt[8]  = 1;                          /* stream_id             */
        pkt[9]  = DIAG_CTRL_MASK_ALL_ENABLED; /* status                */
        pkt[10] = 0;                          /* msg_mode = legacy     */
        pkt[11] = f & 0xff; pkt[12] = (f >> 8) & 0xff;   /* ssid_first */
        pkt[13] = l & 0xff; pkt[14] = (l >> 8) & 0xff;   /* ssid_last  */
        put_le32(pkt + 15, 1);                /* msg_mask_size         */
        put_le32(pkt + 19, 0xFFFFFFFFu);      /* msg_mask[0]           */
        if (send_ctrl("F3_MASK", pkt, 23) < 0) return 1;
    }

    /* 3. DIAG_CTRL_MSG_LOG_MASK - enable all equipment ids (struct diag_ctrl_log_mask) */
    memset(pkt, 0, sizeof(pkt));
    put_le32(pkt + 0, DIAG_CTRL_MSG_LOG_MASK);
    put_le32(pkt + 4, 11);                    /* data_len = 11 + log_mask_size */
    pkt[8]  = 1;                              /* stream_id     */
    pkt[9]  = DIAG_CTRL_MASK_ALL_ENABLED;     /* status        */
    pkt[10] = 0;                              /* equip_id      */
    put_le32(pkt + 11, 0);                    /* num_items     */
    put_le32(pkt + 15, 0);                    /* log_mask_size */
    if (send_ctrl("LOG_MASK", pkt, 19) < 0) return 1;

    /* 4. DIAG_CTRL_MSG_EVENT_MASK - enable all events (struct diag_ctrl_event_mask) */
    memset(pkt, 0, sizeof(pkt));
    put_le32(pkt + 0, DIAG_CTRL_MSG_EVENT_MASK);
    put_le32(pkt + 4, 7);                     /* data_len = 7 + num_bytes */
    pkt[8]  = 1;                              /* stream_id                */
    pkt[9]  = DIAG_CTRL_MASK_ALL_ENABLED;     /* status                   */
    pkt[10] = 1;                              /* event_config             */
    put_le32(pkt + 11, 0);                    /* event_mask_size          */
    if (send_ctrl("EVENT_MASK", pkt, 15) < 0) return 1;

    printf("=> control-channel enable sequence sent on %s\n", path);

    /* Drain any replies */
    unsigned char resp[BUFSZ];
    for (int i = 0; i < 8; i++) {
        int r = read_msg(resp, sizeof(resp), 300);
        if (r <= 0) break;
        printf("CNTL RX (%d bytes): ", r);
        hexdump_ascii(resp, r);
    }
    return 0;
}

/*
 * Disable the stream: same three mask packets with ALL_DISABLED status.
 * Used to return the channel to a quiet state after a capture window.
 */
static int cmd_cntl_disable(const char *path) {
    unsigned char pkt[64];

    if (open_dev(path) < 0) return 1;

    /* F3 / message mask - ALL_DISABLED */
    memset(pkt, 0, sizeof(pkt));
    put_le32(pkt + 0, DIAG_CTRL_MSG_F3_MASK);
    put_le32(pkt + 4, 11);                    /* data_len = 11 + 4*0 */
    pkt[8]  = 1;
    pkt[9]  = DIAG_CTRL_MASK_ALL_DISABLED;
    pkt[10] = 0;
    put_le32(pkt + 15, 0);                    /* msg_mask_size = 0 */
    if (send_ctrl("F3_MASK", pkt, 19) < 0) return 1;

    /* Log mask - ALL_DISABLED */
    memset(pkt, 0, sizeof(pkt));
    put_le32(pkt + 0, DIAG_CTRL_MSG_LOG_MASK);
    put_le32(pkt + 4, 11);
    pkt[8]  = 1;
    pkt[9]  = DIAG_CTRL_MASK_ALL_DISABLED;
    pkt[10] = 0;
    put_le32(pkt + 11, 0);
    put_le32(pkt + 15, 0);
    if (send_ctrl("LOG_MASK", pkt, 19) < 0) return 1;

    /* Event mask - ALL_DISABLED */
    memset(pkt, 0, sizeof(pkt));
    put_le32(pkt + 0, DIAG_CTRL_MSG_EVENT_MASK);
    put_le32(pkt + 4, 7);
    pkt[8]  = 1;
    pkt[9]  = DIAG_CTRL_MASK_ALL_DISABLED;
    pkt[10] = 0;                              /* event_config = 0 */
    put_le32(pkt + 11, 0);
    if (send_ctrl("EVENT_MASK", pkt, 15) < 0) return 1;

    printf("=> control-channel disable sequence sent on %s\n", path);
    return 0;
}

static int cmd_cntl_dump(const char *path) {
    unsigned char resp[BUFSZ];
    if (open_dev(path) < 0) return 1;
    printf("Reading %s for 5 s...\n", path);
    double t0 = now_s();
    long msgs = 0;
    while ((now_s() - t0) < 5.0) {
        int r = read_msg(resp, sizeof(resp), 500);
        if (r < 0) break;            /* device gone */
        if (r == 0) continue;        /* timeout */
        msgs++;
        printf("CNTL RX (%d bytes): ", r);
        hexdump_ascii(resp, r);
    }
    printf("--- %ld messages ---\n", msgs);
    return 0;
}

int main(int argc, char **argv) {
    if (argc < 2) {
        fprintf(stderr,
            "usage:\n"
            "  %s selftest\n"
            "  %s raw <hexbytes>\n"
            "  %s req <hexbytes> <expect_code>   (reply-matching; skips F3 stream)\n"
            "  %s hdlc <hexbytes> <expect_code> [lead]   (HDLC-framed, for DIAG_CMD)\n"
            "  %s listen <seconds>\n"
            "  %s capture <seconds> <file>\n"
            "  %s capture-send <seconds> <file> <hex>   (TX first, then capture)\n"
            "  %s log-config <op>\n"
            "  %s log-disable\n"
            "  %s log-enable\n"
            "  %s set-mask <equip> <nitems> <maskhex>\n"
            "  %s get-mask <equip>\n"
            "  %s cntl-enable [dev] [nssid]\n"
            "  %s cntl-enable-range [dev] [nssid]   (one F3_MASK packet per 256 SSIDs)\n"
            "  %s cntl-disable [dev]\n"
            "  %s cntl-dump [dev]\n",
            argv[0], argv[0], argv[0], argv[0], argv[0], argv[0],
            argv[0], argv[0], argv[0], argv[0], argv[0], argv[0], argv[0],
            argv[0], argv[0], argv[0]);
        return 2;
    }

    /* cntl-* open their own device (DIAG_CNTL); everything else uses DIAG. */
    if (!strcmp(argv[1], "cntl-enable"))
        return cmd_cntl_enable_n(argc >= 3 ? argv[2] : CNTL_PATH,
                                 argc >= 4 ? atoi(argv[3]) : 1, 1);
    if (!strcmp(argv[1], "cntl-enable-range"))
        return cmd_cntl_enable_n(argc >= 3 ? argv[2] : CNTL_PATH,
                                 argc >= 4 ? atoi(argv[3]) : 256,
                                 argc >= 5 ? atoi(argv[4]) : 256);
    if (!strcmp(argv[1], "cntl-disable"))
        return cmd_cntl_disable(argc >= 3 ? argv[2] : CNTL_PATH);
    if (!strcmp(argv[1], "cntl-dump"))
        return cmd_cntl_dump(argc >= 3 ? argv[2] : CNTL_PATH);

    /* The DIAG data/log channel is /dev/rpmsg0; the request/response channel
     * is a separate endpoint (DIAG_CMD).  DIAG_DEV overrides for testing. */
    const char *dev_path = getenv("DIAG_DEV");
    if (!dev_path || !dev_path[0]) dev_path = DEV_PATH;
    if (open_dev(dev_path) < 0) return 1;

    int rc = 2;
    if (!strcmp(argv[1], "raw") && argc >= 3) {
        rc = cmd_raw(argv[2]);
    } else if (!strcmp(argv[1], "req") && argc >= 3) {
        rc = cmd_req(argv[2], argc >= 4 ? (unsigned int)strtoul(argv[3], NULL, 0) : 0);
    } else if (!strcmp(argv[1], "hdlc") && argc >= 3) {
        rc = cmd_hdlc(argv[2], argc >= 4 ? (unsigned int)strtoul(argv[3], NULL, 0) : 0,
                      argc >= 5 ? atoi(argv[4]) : 1);
    } else if (!strcmp(argv[1], "listen") && argc >= 3) {
        rc = cmd_listen(atoi(argv[2]));
    } else if (!strcmp(argv[1], "capture") && argc >= 4) {
        rc = cmd_capture(atoi(argv[2]), argv[3]);
    } else if (!strcmp(argv[1], "capture-send") && argc >= 5) {
        rc = cmd_capture_send(atoi(argv[2]), argv[3], argv[4]);
    } else if (!strcmp(argv[1], "selftest")) {
        rc = cmd_selftest();
    } else if (!strcmp(argv[1], "log-config") && argc >= 3) {
        rc = log_config((uint32_t)strtoul(argv[2], NULL, 0), 0, 0, NULL);
    } else if (!strcmp(argv[1], "log-disable")) {
        rc = log_config(LOGCFG_DISABLE, 0, 0, NULL);
    } else if (!strcmp(argv[1], "log-enable")) {
        rc = log_config(LOGCFG_ENABLE, 0, 0, NULL);
    } else if (!strcmp(argv[1], "set-mask") && argc >= 5) {
        uint32_t equip = (uint32_t)strtoul(argv[2], NULL, 0);
        uint32_t nitems = (uint32_t)strtoul(argv[3], NULL, 0);
        unsigned char mask[BUFSZ];
        int mlen = hex2bin(argv[4], mask, sizeof(mask));
        if (mlen < 0) { fprintf(stderr, "bad mask hex\n"); close(g_fd); return 1; }
        rc = log_config(LOGCFG_SET_MASK, equip, nitems, mask);
    } else if (!strcmp(argv[1], "get-mask") && argc >= 3) {
        rc = log_config(LOGCFG_GET_MASK, (uint32_t)strtoul(argv[2], NULL, 0), 0, NULL);
    } else {
        fprintf(stderr, "unknown command\n");
    }

    close(g_fd);
    return rc;
}
