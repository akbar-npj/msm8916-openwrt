// SPDX-License-Identifier: GPL-2.0-only
/*
 * diag_nv.c - read/write a modem NV item BY ID over the SMD DIAG channel
 *             bridged to /dev/rpmsg0 (same transport as diag_efs).
 *
 * This is the DIAG NV path (not EFS2): command 0x26 = NV_READ_F, 0x27 =
 * NV_WRITE_F.  Object layout (from edlclient/Tools/qc_diag.py, nvitem_type):
 *
 *   req : 26 | item(u16 LE) | rawdata[128] | status(u16 LE)      (133 bytes)
 *   resp: 26 | item(u16 LE) | rawdata[128] | status(u16 LE)  [+ crc(2) 7e]
 *
 * The request carries a 128-byte buffer; the modem returns the item contents
 * in rawdata.  A reply whose first byte is 0x14 is "invalid parameter"; 0x42
 * (in the status word) means "nv_read/write because SP is locked".
 *
 * Usage:
 *   diag_nv read  <item>
 *   diag_nv write <item> <hexbytes>     (write, then read back + compare)
 *   diag_nv raw   <hex...>              (send arbitrary bytes, print reply)
 *
 * Build:
 *   aarch64-openwrt-linux-musl-gcc -static -Os -fno-stack-protector \
 *        -o diag_nv diag_nv.c
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <stdint.h>
#include <time.h>
#include <sys/select.h>

#define DEV_PATH "/dev/rpmsg0"
#define BUFSZ    8192
#define NV_READ  0x26
#define NV_WRITE 0x27

static int g_fd = -1;

static int open_dev(void) {
    if (g_fd >= 0) return 0;
    g_fd = open(DEV_PATH, O_RDWR);
    if (g_fd < 0) { fprintf(stderr, "open(%s): %s\n", DEV_PATH, strerror(errno)); return -1; }
    return 0;
}

static int read_msg(unsigned char *buf, int bufsz, int timeout_ms) {
    fd_set rfds; struct timeval tv;
    FD_ZERO(&rfds); FD_SET(g_fd, &rfds);
    tv.tv_sec = timeout_ms/1000; tv.tv_usec = (timeout_ms%1000)*1000;
    int r = select(g_fd+1, &rfds, NULL, NULL, &tv);
    if (r <= 0) return 0;
    ssize_t n = read(g_fd, buf, bufsz);
    if (n < 0) { fprintf(stderr, "read: %s\n", strerror(errno)); return -1; }
    return (int)n;
}

/* Send a raw request, then read until a message whose first byte == want.
 * Returns response length, 0 on timeout, -1 on error. */
static int txn(const unsigned char *req, int n, unsigned char want,
               unsigned char *resp, int timeout_ms) {
    if (open_dev() < 0) return -1;
    if (write(g_fd, req, n) != n) { fprintf(stderr, "write: %s\n", strerror(errno)); return -1; }
    struct timespec t0, t1;
    clock_gettime(CLOCK_MONOTONIC, &t0);
    long long budget_ns = (long long)timeout_ms * 1000000LL;
    int skipped = 0;
    for (;;) {
        clock_gettime(CLOCK_MONOTONIC, &t1);
        long long used = (t1.tv_sec - t0.tv_sec) * 1000000000LL + (t1.tv_nsec - t0.tv_nsec);
        long long left_ns = budget_ns - used;
        if (left_ns <= 0) break;
        int left_ms = (int)(left_ns / 1000000LL);
        if (left_ms < 1) left_ms = 1;
        if (left_ms > 250) left_ms = 250;
        int r = read_msg(resp, BUFSZ, left_ms);
        if (r < 0) return -1;
        if (r == 0) continue;
        if (r >= 1 && resp[0] == want) return r;
        skipped++;
    }
    if (skipped) fprintf(stderr, "txn: gave up after skipping %d message(s)\n", skipped);
    return 0;
}

static void hexdump(const unsigned char *b, int n) {
    for (int k = 0; k < n; k += 16) {
        printf("%04x  ", k);
        for (int j = 0; j < 16; j++) {
            if (j+k < n) printf("%02x ", b[k+j]); else printf("   ");
        }
        printf(" |");
        for (int j = 0; j < 16 && k+j < n; j++) {
            unsigned char c = b[k+j];
            putchar((32 <= c && c < 127) ? c : '.');
        }
        printf("|\n");
    }
}

static int hexval(int c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
}
static int parse_hex(const char *s, unsigned char *out, int maxn) {
    int n = 0;
    while (*s && n < maxn) {
        while (*s == ' ' || *s == ':' || *s == ',') s++;
        if (!*s) break;
        int hi = hexval(*s++); if (hi < 0) return -1;
        while (*s == ' ' || *s == ':' || *s == ',') s++;
        int lo = 0;
        if (*s) { lo = hexval(*s); if (lo >= 0) s++; else lo = 0; }
        out[n++] = (hi << 4) | lo;
    }
    return n;
}

static int nv_do(int cmd, unsigned item, const unsigned char *data128,
                 unsigned char *out128, int *status_out, int timeout_ms) {
    unsigned char req[1+2+128+2], resp[BUFSZ];
    memset(req, 0, sizeof req);
    req[0] = (unsigned char)cmd;
    req[1] = item & 0xff; req[2] = (item >> 8) & 0xff;
    if (data128) memcpy(req + 3, data128, 128);
    int r = txn(req, sizeof req, (unsigned char)cmd, resp, timeout_ms);
    if (r <= 0) return r;
    /* resp: cmd | item(2) | rawdata(128) | status(2) */
    if (r >= 3 + 128 + 2) {
        memcpy(out128, resp + 3, 128);
        *status_out = resp[3+128] | (resp[3+128+1] << 8);
    } else if (r >= 3) {
        memset(out128, 0, 128);
        memcpy(out128, resp + 3, r - 3);
        *status_out = -1;
    } else {
        *status_out = -1;
    }
    return r;
}

static int cmd_read(unsigned item) {
    unsigned char out[128]; int st = -1;
    int r = nv_do(NV_READ, item, NULL, out, &st, 4000);
    if (r == 0) { printf("nvread %u (0x%x): TIMEOUT (no 0x26 reply)\n", item, item); return 1; }
    if (r < 0)  { printf("nvread %u (0x%x): ERROR\n", item, item); return 1; }
    printf("nvread %u (0x%x): resp=%d bytes  status=0x%04x\n", item, item, r, (unsigned)st);
    if (st == 0x42) printf("  !! SP is LOCKED (0x42)\n");
    hexdump(out, 128);
    return 0;
}

static int cmd_write(unsigned item, const unsigned char *data, int dlen) {
    unsigned char buf[128], back[128]; int st = -1;
    memset(buf, 0, sizeof buf);
    if (dlen > 128) { fprintf(stderr, "data too long (%d > 128)\n", dlen); return 2; }
    memcpy(buf, data, dlen);
    int r = nv_do(NV_WRITE, item, buf, back, &st, 4000);
    if (r <= 0) { printf("nvwrite %u: no 0x27 reply (r=%d)\n", item, r); return 1; }
    printf("nvwrite %u (0x%x): resp=%d status=0x%04x\n", item, item, r, (unsigned)st);
    if (st == 0x42) { printf("  !! SP is LOCKED (0x42) -- write refused\n"); return 1; }
    int r2 = nv_do(NV_READ, item, NULL, back, &st, 4000);
    if (r2 <= 0) { printf("  read-back failed (r=%d)\n", r2); return 1; }
    printf("  read-back status=0x%04x\n", (unsigned)st);
    hexdump(back, 128);
    if (memcmp(buf, back, 128) == 0) printf("  VERIFY: MATCH\n");
    else printf("  VERIFY: MISMATCH\n");
    return 0;
}

static int cmd_raw(const char *hex) {
    unsigned char req[BUFSZ], resp[BUFSZ];
    int n = parse_hex(hex, req, sizeof req);
    if (n <= 0) { fprintf(stderr, "bad hex\n"); return 2; }
    if (open_dev() < 0) return 1;
    if (write(g_fd, req, n) != n) { fprintf(stderr, "write: %s\n", strerror(errno)); return 1; }
    printf("sent %d bytes\n", n);
    struct timespec t0, t1;
    clock_gettime(CLOCK_MONOTONIC, &t0);
    for (;;) {
        clock_gettime(CLOCK_MONOTONIC, &t1);
        long long used = (t1.tv_sec-t0.tv_sec)*1000000000LL + (t1.tv_nsec-t0.tv_nsec);
        if (used > 3000000000LL) break;
        int r = read_msg(resp, BUFSZ, 250);
        if (r < 0) return 1;
        if (r == 0) continue;
        printf("resp(%d):", r);
        for (int i = 0; i < r && i < 64; i++) printf(" %02x", resp[i]);
        printf("\n");
    }
    return 0;
}

int main(int argc, char **argv) {
    if (argc < 2) { fprintf(stderr, "usage: %s read <item> | write <item> <hex> | raw <hex>\n", argv[0]); return 2; }
    if (!strcmp(argv[1], "read") && argc >= 3) return cmd_read((unsigned)strtoul(argv[2], NULL, 0));
    if (!strcmp(argv[1], "write") && argc >= 4) {
        unsigned char d[128]; int n = parse_hex(argv[3], d, sizeof d);
        if (n < 0) { fprintf(stderr, "bad hex\n"); return 2; }
        return cmd_write((unsigned)strtoul(argv[2], NULL, 0), d, n);
    }
    if (!strcmp(argv[1], "raw") && argc >= 3) return cmd_raw(argv[2]);
    fprintf(stderr, "bad args\n");
    return 2;
}
