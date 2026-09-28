// SPDX-License-Identifier: GPL-2.0-only
/*
 * diag_efs.c - read (and, with `put`, write) the MODEM's EFS, decrypted on the
 *              fly, over the SMD DIAG channel bridged to /dev/rpmsg0.
 *
 * Part of the `diag-efs` OpenWrt package. Run `diag-bind` first (or let the
 * diag-bind service keep the bridge up) so /dev/rpmsg0 exists.
 *
 * Transport (from diag_logtool.c / Doc 136): requests are RAW (no HDLC, no
 * CRC); each read() returns exactly one DIAG message; responses carry a
 * trailing 2-byte CRC + 0x7E.
 *
 * EFS2 subsystem, method 0x13 (verified on this device: 0x3E returns 0x13
 * bad-cmd).  Sub-commands (efs_cmds):
 *   OPEN=2  CLOSE=3  READ=4  WRITE=5  OPENDIR=11  READDIR=12  STAT=15  FSTAT=17
 *
 * Request shapes (LE):
 *   OPEN    : 4b 13 02 00 | oflag(u32) mode(u32) | path\0
 *   CLOSE   : 4b 13 03 00 | fdata(u32)
 *   READ    : 4b 13 04 00 | fdata(u32) nbytes(u32) offset(u32)
 *   WRITE   : 4b 13 05 00 | fdata(u32) offset(u32) | data[...]
 *             (NOTE: no nbytes field -- the length is the message length.
 *              This differs from READ; see efs_write().)
 *   OPENDIR : 4b 13 0b 00 | path\0
 *   READDIR : 4b 13 0c 00 | dirp(u32) seqno(u32)
 *   CLOSEDIR: 4b 13 0d 00 | dirp(u32)
 *   STAT    : 4b 13 0f 00 | path\0
 *   FSTAT   : 4b 13 11 00 | fdata(u32)
 *
 * Responses: [4b 13 cc 00] then payload; OPEN/OPENDIR -> handle@4 err@8;
 * FSTAT -> err@4 mode@8 size@0xc; READ -> fdata@4 off@8 nread@0xc err@0x10
 * data@0x14; WRITE -> fdata@4 off@8 nwritten@0xc err@0x10; READDIR -> 9 u32
 * (dirp seqno err entry_type mode size atime mtime ctime) then name.
 *
 * NOTE: an earlier revision named sub-command 5 "EFS_READ5" and offered it as
 * an alternate READ opcode via $EFS_READCMD.  The protocol notes above list
 * WRITE=5, and this tool now uses 5 for WRITE.  The $EFS_READCMD override is
 * kept for probing, but defaults to 4.
 *
 * Build (OpenWrt package: see packages/diag-efs/Makefile), or standalone:
 *   aarch64-openwrt-linux-musl-gcc -static -Os -fno-stack-protector \
 *            -o diag_efs diag_efs.c
 *
 * Usage:
 *   diag_efs list [path]           recursive directory listing (default /)
 *   diag_efs read <path> <local>   read one EFS file to a local file
 *   diag_efs stat <path>           stat one EFS path
 *   diag_efs get <path>            read one EFS file to stdout (hex+ascii)
 *   diag_efs put <local> <path> [--force] [--oflag N]
 *                                  write a local file to an EFS path, then
 *                                  read it back and verify (MATCH/MISMATCH)
 *   diag_efs detect                probe which EFS2 method the modem accepts
 *
 * `put` safety: it refuses calibration paths (/nv/item_files/rfnv/,
 * /nv/item_files/mcs/) unless --force, backs up an existing target to
 * <sanitised-path>.bak.<epoch> before writing, and always verifies by
 * read-back.  The write open flag defaults to EFS_OFLAG_WRITE and can be
 * overridden with --oflag while probing a firmware.
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
#include <sys/stat.h>
#include <sys/types.h>

#define DEV_PATH "/dev/rpmsg0"
#define BUFSZ    8192
#define EFS      0x13

/* sub-commands */
#define EFS_OPEN    2
#define EFS_CLOSE   3
#define EFS_READ    4
#define EFS_WRITE   5
#define EFS_OPENDIR 11
#define EFS_CLOSEDIR 13
#define EFS_READDIR 12
#define EFS_STAT    15
#define EFS_FSTAT   17

/* Open flags for a write.  Qualcomm EFS uses the POSIX/Linux values, probed on
 * the HMU05:
 *   O_WRONLY=0x1  O_RDWR=0x2  O_CREAT=0x40  O_TRUNC=0x200  O_APPEND=0x400
 * (0x41 creates, 0x201 truncates, 0x401 appends).  The default creates the file
 * if absent and truncates it if present, so `put` replaces the target. */
#define EFS_OFLAG_WRITE 0x241   /* O_WRONLY | O_CREAT | O_TRUNC */

static int g_fd = -1;
static int g_readcmd = EFS_READ;   /* overridable with $EFS_READCMD */

static void put_le32(unsigned char *p, uint32_t v) {
    p[0]=v&0xff; p[1]=(v>>8)&0xff; p[2]=(v>>16)&0xff; p[3]=(v>>24)&0xff;
}
static uint32_t rd_le32(const unsigned char *p) {
    return (uint32_t)p[0] | ((uint32_t)p[1]<<8) | ((uint32_t)p[2]<<16) | ((uint32_t)p[3]<<24);
}

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

/* Send a request and read one response. Returns response length (payload
 * includes the trailing crc+7e), 0 on timeout, -1 on error. */
static int efs_txn(const unsigned char *req, int n, unsigned char *resp, int timeout_ms) {
    if (open_dev() < 0) return -1;
    if (write(g_fd, req, n) != n) { fprintf(stderr, "write: %s\n", strerror(errno)); return -1; }
    /* Doc 224: this channel also carries a CONTINUOUS spontaneous F3 (0x79)
     * stream -- measured at ~1 message per read, so a single read() almost
     * always returns an F3 record, never our reply.  Read until the wall-clock
     * budget is spent, discarding everything that is not the EFS2 reply whose
     * byte 2 echoes THIS request's sub-command.
     *
     * NOTE: do NOT try to silence the F3 stream with `diag_logtool cntl-disable`
     * -- that stops the flood but leaves the channel UNRESPONSIVE, so the EFS2
     * replies stop too.  The skip loop is not equivalent to disabling. */
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
        if (r >= 4 && resp[0] == 0x4b && resp[1] == req[1] && resp[2] == req[2]) {
            if (getenv("EFS_DUMP")) {
                fprintf(stderr, "REQ :"); for (int i=0;i<n && i<24;i++) fprintf(stderr," %02x",req[i]);
                fprintf(stderr, "\nRESP(%d):", r); for (int i=0;i<r && i<64;i++) fprintf(stderr," %02x",resp[i]);
                fprintf(stderr, "\n");
            }
            return r;
        }
        skipped++;
    }
    if (skipped && getenv("EFS_DEBUG"))
        fprintf(stderr, "efs_txn: gave up after skipping %d non-matching message(s)\n", skipped);
    return 0;
}

static const char *efs_err_str(uint32_t e) {
    switch (e) {
    case 0x40000001: return "Inconsistent state";
    case 0x40000002: return "Invalid seq no";
    case 0x40000003: return "Directory not open";
    case 0x40000004: return "Directory entry not found";
    case 0x40000005: return "Invalid path";
    case 0x40000006: return "Path too long";
    case 0x40000007: return "Too many open directories";
    case 0x40000008: return "Invalid directory entry";
    case 0x40000009: return "Too many open files";
    case 0x4000000a: return "Unknown filetype";
    case 0x4000000b: return "Not nand flash";
    case 0x4000000c: return "Unavailable info";
    default: return "?";
    }
}

/* ---- EFS ops ---- */

static int efs_detect(void) {
    unsigned char req[64], resp[BUFSZ];
    memset(req, 0, sizeof(req));
    req[0]=0x4b; req[1]=0x13; req[2]=0x00; req[3]=0x00;
    int r = efs_txn(req, 4+40, resp, 3000);
    if (r > 0 && resp[0]==0x4b) return 0x13;
    memset(req, 0, sizeof(req));
    req[0]=0x4b; req[1]=0x3e; req[2]=0x00; req[3]=0x00;
    r = efs_txn(req, 4+40, resp, 3000);
    if (r > 0 && resp[0]==0x4b) return 0x3e;
    return -1;
}

static int efs_opendir(const char *path, uint32_t *dirp) {
    unsigned char req[BUFSZ], resp[BUFSZ];
    int plen = strlen(path);
    memset(req, 0, sizeof(req));
    req[0]=0x4b; req[1]=EFS; req[2]=EFS_OPENDIR; req[3]=0x00;
    memcpy(req+4, path, plen); req[4+plen]=0;
    int r = efs_txn(req, 5+plen, resp, 5000);
    if (r <= 0) return -1;
    if (resp[0]!=0x4b) { fprintf(stderr, "opendir: bad resp %02x\n", resp[0]); return -1; }
    uint32_t err = rd_le32(resp+8);
    if (err) { fprintf(stderr, "opendir(%s): err 0x%08x (%s)\n", path, err, efs_err_str(err)); return -1; }
    *dirp = rd_le32(resp+4);
    return 0;
}

static int efs_closedir(uint32_t dirp) {
    unsigned char req[16], resp[BUFSZ];
    memset(req, 0, sizeof(req));
    req[0]=0x4b; req[1]=EFS; req[2]=EFS_CLOSEDIR; req[3]=0x00;
    put_le32(req+4, dirp);
    int r = efs_txn(req, 8, resp, 3000);
    return (r > 0 && resp[0]==0x4b) ? 0 : -1;
}

/* returns 1 = entry read (name filled), 0 = end-of-dir, -1 = error */
static int efs_readdir(uint32_t dirp, uint32_t seqno, char *name, int namesz,
                       uint32_t *entry_type, uint32_t *mode, uint32_t *size) {
    unsigned char req[16], resp[BUFSZ];
    memset(req, 0, sizeof(req));
    req[0]=0x4b; req[1]=EFS; req[2]=EFS_READDIR; req[3]=0x00;
    put_le32(req+4, dirp); put_le32(req+8, seqno);
    int r = efs_txn(req, 12, resp, 5000);
    if (r <= 0) return -1;
    if (resp[0]!=0x4b) { fprintf(stderr, "readdir: bad resp %02x\n", resp[0]); return -1; }
    uint32_t err = rd_le32(resp+0xc);
    if (err) { fprintf(stderr, "readdir: err 0x%08x (%s)\n", err, efs_err_str(err)); return -1; }
    uint32_t et = rd_le32(resp+0x10);
    if (entry_type) *entry_type = et;
    if (mode) *mode = rd_le32(resp+0x14);
    if (size) *size = rd_le32(resp+0x18);
    /* name starts at 0x28, ends before trailing crc(2)+0x7e(1).
     * NOTE: entry_type is 1 for directories but 0 for *regular files* too, so
     * it is NOT the end-of-directory marker.  End-of-dir is an all-zero record
     * with an EMPTY name (observed past the last entry of /nv/item_files/conf:
     * dirp/seqno echoed, err=0, type/mode/size=0, name=""). */
    int namelen = r - 0x28 - 3;
    if (namelen < 0) namelen = 0;
    if (namelen >= namesz) namelen = namesz-1;
    memcpy(name, resp+0x28, namelen); name[namelen]=0;
    if (strlen(name) == 0) return 0;       /* end of directory */
    return 1;
}

static int efs_open(const char *path, int oflag, uint32_t *fdata) {
    unsigned char req[BUFSZ], resp[BUFSZ];
    int plen = strlen(path);
    memset(req, 0, sizeof(req));
    req[0]=0x4b; req[1]=EFS; req[2]=EFS_OPEN; req[3]=0x00;
    put_le32(req+4, (uint32_t)oflag); put_le32(req+8, 0);
    memcpy(req+12, path, plen); req[12+plen]=0;
    int r = efs_txn(req, 13+plen, resp, 5000);
    if (r <= 0) return -1;
    if (resp[0]!=0x4b) { fprintf(stderr, "open: bad resp %02x\n", resp[0]); return -1; }
    uint32_t err = rd_le32(resp+8);
    if (err) { fprintf(stderr, "open(%s): err 0x%08x (%s)\n", path, err, efs_err_str(err)); return -1; }
    *fdata = rd_le32(resp+4);
    return 0;
}

static int efs_close(uint32_t fdata) {
    unsigned char req[16], resp[BUFSZ];
    memset(req, 0, sizeof(req));
    req[0]=0x4b; req[1]=EFS; req[2]=EFS_CLOSE; req[3]=0x00;
    put_le32(req+4, fdata);
    int r = efs_txn(req, 8, resp, 3000);
    return (r > 0 && resp[0]==0x4b) ? 0 : -1;
}

static int efs_fstat(uint32_t fdata, uint32_t *mode, uint32_t *size) {
    unsigned char req[16], resp[BUFSZ];
    memset(req, 0, sizeof(req));
    req[0]=0x4b; req[1]=EFS; req[2]=EFS_FSTAT; req[3]=0x00;
    put_le32(req+4, fdata);
    int r = efs_txn(req, 8, resp, 3000);
    if (r <= 0 || resp[0]!=0x4b) return -1;
    uint32_t err = rd_le32(resp+4);
    if (err) return -1;
    if (mode) *mode = rd_le32(resp+8);
    if (size) *size = rd_le32(resp+0xc);
    return 0;
}

static int efs_stat(const char *path, uint32_t *mode, uint32_t *size) {
    unsigned char req[BUFSZ], resp[BUFSZ];
    int plen = strlen(path);
    memset(req, 0, sizeof(req));
    req[0]=0x4b; req[1]=EFS; req[2]=EFS_STAT; req[3]=0x00;
    memcpy(req+4, path, plen); req[4+plen]=0;
    int r = efs_txn(req, 5+plen, resp, 5000);
    if (r <= 0) return -1;
    if (resp[0]!=0x4b) { fprintf(stderr, "stat: bad resp %02x\n", resp[0]); return -1; }
    uint32_t err = rd_le32(resp+4);
    if (err) { fprintf(stderr, "stat(%s): err 0x%08x (%s)\n", path, err, efs_err_str(err)); return -1; }
    if (mode) *mode = rd_le32(resp+8);
    if (size) *size = rd_le32(resp+0xc);
    return 0;
}

/* Read nbytes at offset. Returns bytes read (>=0) or -1. */
static int efs_read(uint32_t fdata, uint32_t nbytes, uint32_t offset,
                    unsigned char *out, int outsz) {
    unsigned char req[32], resp[BUFSZ];
    memset(req, 0, sizeof(req));
    req[0]=0x4b; req[1]=EFS; req[2]=(unsigned char)g_readcmd; req[3]=0x00;
    put_le32(req+4, fdata); put_le32(req+8, nbytes); put_le32(req+12, offset);
    int r = efs_txn(req, 16, resp, 8000);
    if (r <= 0) return -1;
    if (resp[0]!=0x4b) { fprintf(stderr, "read: bad resp %02x\n", resp[0]); return -1; }
    uint32_t nread = rd_le32(resp+0xc);
    uint32_t err = rd_le32(resp+0x10);
    if (err) { fprintf(stderr, "read: err 0x%08x (%s)\n", err, efs_err_str(err)); return -1; }
    if ((int)nread > outsz) nread = outsz;
    /* sanity: response must actually carry nread bytes after 0x14 */
    if (r < 0x14 + (int)nread) nread = r - 0x14;
    if (nread > 0) memcpy(out, resp+0x14, nread);
    return (int)nread;
}

/* Write nbytes at offset from data (<=1024 per call). Returns 0 or -1.
 *
 * IMPORTANT: the WRITE request layout is NOT the READ layout.  It is
 *     4b 13 05 00 | fdata(u32) | offset(u32) | data[...]
 * -- the byte count is implied by the message length, and there is no
 * separate nbytes field.  (Measured on the HMU05: putting nbytes at req+8
 * makes the modem treat it as the file offset.)
 *
 * Response: fdata@4 offset@8 nwritten@0xc err@0x10.  The data is only
 * committed to EFS when the handle is CLOSEd, so the caller must close before
 * reading the file back. */
static int efs_write(uint32_t fdata, const unsigned char *data, uint32_t nbytes,
                     uint32_t offset, uint32_t *nwritten) {
    unsigned char req[BUFSZ], resp[BUFSZ];
    if (nbytes > 1024) nbytes = 1024;
    memset(req, 0, sizeof(req));
    req[0]=0x4b; req[1]=EFS; req[2]=EFS_WRITE; req[3]=0x00;
    put_le32(req+4, fdata);
    put_le32(req+8, offset);
    memcpy(req+12, data, nbytes);
    int r = efs_txn(req, 12 + (int)nbytes, resp, 8000);
    if (getenv("DIAG_EFS_DEBUG")) {
        fprintf(stderr, "[dbg] WRITE req(%d): ", 12 + (int)nbytes);
        for (int i = 0; i < 12; i++) fprintf(stderr, "%02x ", req[i]);
        fprintf(stderr, "...\n[dbg] resp(%d): ", r);
        for (int i = 0; i < r && i < 24; i++) fprintf(stderr, "%02x ", resp[i]);
        fprintf(stderr, "\n");
    }
    if (r <= 0) return -1;
    if (resp[0]!=0x4b) { fprintf(stderr, "write: bad resp %02x\n", resp[0]); return -1; }
    uint32_t err = rd_le32(resp+0x10);
    if (err) { fprintf(stderr, "write: err 0x%08x (%s)\n", err, efs_err_str(err)); return -1; }
    if (nwritten) *nwritten = rd_le32(resp+0xc);
    if (getenv("DIAG_EFS_DEBUG"))
        fprintf(stderr, "[dbg] parsed: fdata=0x%08x off=%u nwritten=%u err=0x%08x\n",
                rd_le32(resp+4), rd_le32(resp+8), rd_le32(resp+0xc), rd_le32(resp+0x10));
    return 0;
}

/* ---- high-level commands ---- */

static int cmd_stat(const char *path) {
    uint32_t mode=0, size=0;
    if (efs_stat(path, &mode, &size) != 0) return 1;
    printf("%s mode=%08x size=%u\n", path, mode, size);
    return 0;
}

static int cmd_read(const char *path, const char *local, int to_stdout) {
    uint32_t fdata=0, mode=0, size=0;
    if (efs_open(path, 0, &fdata) != 0) return 1;
    if (efs_fstat(fdata, &mode, &size) != 0) { efs_close(fdata); return 1; }
    if (!to_stdout)
        fprintf(stderr, "%s mode=%08x size=%u -> %s\n", path, mode, size, local ? local : "-");
    FILE *out = stdout;
    if (!to_stdout) {
        out = fopen(local, "wb");
        if (!out) { fprintf(stderr, "fopen(%s): %s\n", local, strerror(errno)); efs_close(fdata); return 1; }
    }
    /* The modem caps one READ at 1024 bytes and returns a SHORT count for
     * larger requests, so loop until the full size is read (do NOT break on a
     * short read). */
    unsigned char buf[2048];
    uint32_t off = 0; int total = 0;
    while (off < size) {
        uint32_t want = size - off;
        if (want > 1024) want = 1024;
        int n = efs_read(fdata, want, off, buf, sizeof(buf));
        if (n <= 0) break;
        fwrite(buf, 1, n, out);
        off += n; total += n;
    }
    efs_close(fdata);
    if (!to_stdout) { fclose(out); fprintf(stderr, "%s: read %d/%u bytes\n", path, total, size); }
    return (off == size) ? 0 : 1;
}

/* Read a whole EFS file into a freshly malloc'd buffer. Returns 0 on success. */
static int efs_read_all(const char *path, unsigned char **out, uint32_t *outlen) {
    uint32_t fdata=0, mode=0, size=0;
    if (efs_open(path, 0, &fdata) != 0) return -1;
    if (efs_fstat(fdata, &mode, &size) != 0) { efs_close(fdata); return -1; }
    unsigned char *buf = malloc(size ? size : 1);
    if (!buf) { efs_close(fdata); return -1; }
    uint32_t off = 0;
    while (off < size) {
        uint32_t want = size - off;
        if (want > 1024) want = 1024;
        int n = efs_read(fdata, want, off, buf + off, (int)(size - off));
        if (n <= 0) break;
        off += n;
    }
    efs_close(fdata);
    *out = buf; *outlen = off;
    return (off == size) ? 0 : -1;
}

static void hexdump(const unsigned char *b, int n) {
    for (int i=0;i<n;i++){ printf("%02x ", b[i]); if((i&15)==15) printf("\n"); }
    if (n&15) printf("\n");
}

static int cmd_get(const char *path) {
    uint32_t fdata=0, mode=0, size=0;
    if (efs_open(path, 0, &fdata) != 0) return 1;
    if (efs_fstat(fdata, &mode, &size) != 0) { efs_close(fdata); return 1; }
    printf("== %s mode=%08x size=%u ==\n", path, mode, size);
    unsigned char buf[2048];
    uint32_t off=0;
    while (off < size) {
        uint32_t want = size-off; if (want>1024) want=1024;
        int n = efs_read(fdata, want, off, buf, sizeof(buf));
        if (n<=0) break;
        hexdump(buf, n);
        off += n;
    }
    efs_close(fdata);
    return 0;
}

/* True for the device-unique calibration trees that must not be written by
 * accident (per-unit RF/TCXO calibration). */
static int path_is_cal(const char *p) {
    return (strstr(p, "/rfnv/") != NULL) || (strstr(p, "/mcs/") != NULL);
}

/* Turn an EFS path into a safe local filename. */
static void sanitize_path(const char *in, char *out, int outsz) {
    int j = 0;
    for (int i = 0; in[i] && j < outsz - 1; i++) {
        char c = in[i];
        if (c == '/') c = '_';
        out[j++] = c;
    }
    out[j] = 0;
    if (j == 0 && outsz > 1) { out[0] = 'x'; out[1] = 0; }
}

/* Write a local file to an EFS path, with backup + read-back verification. */
static int cmd_put(const char *local, const char *efs_path, int force, uint32_t oflag) {
    if (path_is_cal(efs_path) && !force) {
        fprintf(stderr, "refusing to write calibration path %s (use --force)\n", efs_path);
        return 1;
    }

    /* load the payload */
    FILE *in = fopen(local, "rb");
    if (!in) { fprintf(stderr, "fopen(%s): %s\n", local, strerror(errno)); return 1; }
    if (fseek(in, 0, SEEK_END) != 0) { fclose(in); return 1; }
    long lsz = ftell(in);
    if (lsz < 0) { fclose(in); return 1; }
    if (fseek(in, 0, SEEK_SET) != 0) { fclose(in); return 1; }
    unsigned char *data = malloc(lsz ? (size_t)lsz : 1);
    if (!data) { fclose(in); return 1; }
    if (lsz && fread(data, 1, (size_t)lsz, in) != (size_t)lsz) {
        fprintf(stderr, "short read on %s\n", local); fclose(in); free(data); return 1;
    }
    fclose(in);

    /* pre-flight backup of an existing target */
    uint32_t omode=0, osize=0;
    if (efs_stat(efs_path, &omode, &osize) == 0) {
        char base[1024], bak[1200];
        sanitize_path(efs_path, base, sizeof(base));
        snprintf(bak, sizeof(bak), "%s.bak.%ld", base, (long)time(NULL));
        fprintf(stderr, "%s exists (%u bytes); backing up -> %s\n", efs_path, osize, bak);
        if (cmd_read(efs_path, bak, 0) != 0) {
            fprintf(stderr, "backup failed; refusing to write\n");
            free(data); return 1;
        }
    } else {
        fprintf(stderr, "%s does not exist; a create-open is required\n", efs_path);
    }

    /* open for write */
    uint32_t fdata=0;
    if (efs_open(efs_path, (int)oflag, &fdata) != 0) { free(data); return 1; }

    /* write in <=1024-byte chunks */
    uint32_t off = 0;
    int rc = 0;
    while (off < (uint32_t)lsz) {
        uint32_t want = (uint32_t)lsz - off;
        if (want > 1024) want = 1024;
        uint32_t nw = 0;
        if (efs_write(fdata, data + off, want, off, &nw) != 0) { rc = 1; break; }
        if (nw == 0) { fprintf(stderr, "write: short write (0) at offset %u\n", off); rc = 1; break; }
        off += nw;
    }
    efs_close(fdata);
    if (rc) { free(data); return 1; }

    /* verify by read-back */
    unsigned char *rb = NULL; uint32_t rblen = 0;
    if (efs_read_all(efs_path, &rb, &rblen) != 0) {
        fprintf(stderr, "VERIFY: read-back failed\n");
        free(data); return 1;
    }
    int match = (rblen == (uint32_t)lsz) && (memcmp(rb, data, (size_t)lsz) == 0);
    printf("%s: wrote %ld bytes; read-back %u bytes -> %s\n",
           efs_path, lsz, rblen, match ? "MATCH" : "MISMATCH");
    free(rb); free(data);
    return match ? 0 : 1;
}

/* recursive listing. The modem allows only a handful of simultaneously-open
 * directories, so collect the entries, CLOSE the directory, and only then
 * recurse. */
#define MAXENT 2048
static int list_rec(const char *path, int depth) {
    uint32_t dirp=0;
    if (efs_opendir(path, &dirp) != 0) return 1;

    char (*names)[512] = malloc((size_t)MAXENT*512);
    unsigned char *isdirs = malloc(MAXENT);
    if (!names || !isdirs) { free(names); free(isdirs); efs_closedir(dirp); return 1; }
    int nent = 0;
    for (uint32_t seq=1; seq<1000000 && nent<MAXENT; seq++) {
        char name[512]; uint32_t et=0, mode=0, size=0;
        int rc = efs_readdir(dirp, seq, name, sizeof(name), &et, &mode, &size);
        if (rc <= 0) break;
        int isdir = ((mode & 0xf000) == 0x4000);
        char full[1024];
        snprintf(full, sizeof(full), "%s%s%s", path,
                 (path[strlen(path)-1]=='/')?"":"/", name);
        printf("%s%s  (mode=%08x size=%u)\n", full, isdir?"/":"", mode, size);
        snprintf(names[nent], 512, "%s", name);
        isdirs[nent] = (unsigned char)isdir;
        nent++;
    }
    efs_closedir(dirp);

    if (depth < 12) {
        for (int i=0;i<nent;i++) {
            if (!isdirs[i]) continue;
            char sub[1024];
            snprintf(sub, sizeof(sub), "%s%s%s", path,
                     (path[strlen(path)-1]=='/')?"":"/", names[i]);
            list_rec(sub, depth+1);
        }
    }
    free(names); free(isdirs);
    return 0;
}

/* recursively read every file under rpath into local dir lpath */
static int dump_rec(const char *rpath, const char *lpath, int depth) {
    uint32_t dirp=0;
    if (efs_opendir(rpath, &dirp) != 0) return 1;
    char (*names)[512] = malloc((size_t)MAXENT*512);
    unsigned char *isdirs = malloc(MAXENT);
    if (!names || !isdirs) { free(names); free(isdirs); efs_closedir(dirp); return 1; }
    int nent = 0;
    for (uint32_t seq=1; seq<1000000 && nent<MAXENT; seq++) {
        char name[512]; uint32_t et=0, mode=0, size=0;
        int rc = efs_readdir(dirp, seq, name, sizeof(name), &et, &mode, &size);
        if (rc <= 0) break;
        snprintf(names[nent], 512, "%s", name);
        isdirs[nent] = (unsigned char)((mode & 0xf000) == 0x4000);
        nent++;
    }
    efs_closedir(dirp);

    if (depth > 12) { free(names); free(isdirs); return 0; }
    for (int i=0;i<nent;i++) {
        char rsub[1024], lsub[1024];
        snprintf(rsub, sizeof(rsub), "%s%s%s", rpath, (rpath[strlen(rpath)-1]=='/')?"":"/", names[i]);
        snprintf(lsub, sizeof(lsub), "%s/%s", lpath, names[i]);
        if (isdirs[i]) {
            mkdir(lsub, 0755);
            dump_rec(rsub, lsub, depth+1);
        } else {
            cmd_read(rsub, lsub, 0);
        }
    }
    free(names); free(isdirs);
    return 0;
}

int main(int argc, char **argv) {
    if (argc < 2) {
        fprintf(stderr,
            "usage:\n"
            "  %s list [path]\n"
            "  %s read <path> <local>\n"
            "  %s stat <path>\n"
            "  %s get <path>\n"
            "  %s put <local> <efs-path> [--force] [--oflag N]\n"
            "  %s detect\n", argv[0], argv[0], argv[0], argv[0], argv[0], argv[0]);
        return 2;
    }
    if (getenv("EFS_READCMD")) g_readcmd = atoi(getenv("EFS_READCMD"));

    int rc = 0;
    if (!strcmp(argv[1], "detect")) {
        int m = efs_detect();
        printf("efs method: %s\n", m<0?"none":(m==0x13?"0x13":"0x3e"));
        rc = m<0;
    } else if (!strcmp(argv[1], "list")) {
        rc = list_rec(argc>=3?argv[2]:"/", 0);
    } else if (!strcmp(argv[1], "stat") && argc>=3) {
        rc = cmd_stat(argv[2]);
    } else if (!strcmp(argv[1], "read") && argc>=4) {
        rc = cmd_read(argv[2], argv[3], 0);
    } else if (!strcmp(argv[1], "get") && argc>=3) {
        rc = cmd_get(argv[2]);
    } else if (!strcmp(argv[1], "put") && argc>=4) {
        int force = 0;
        uint32_t oflag = EFS_OFLAG_WRITE;
        for (int i=4; i<argc; i++) {
            if (!strcmp(argv[i], "--force")) force = 1;
            else if (!strcmp(argv[i], "--oflag") && i+1 < argc) oflag = (uint32_t)strtoul(argv[++i], NULL, 0);
        }
        rc = cmd_put(argv[2], argv[3], force, oflag);
    } else if (!strcmp(argv[1], "dump") && argc>=4) {
        mkdir(argv[3], 0755);
        rc = dump_rec(argv[2], argv[3], 0);
    } else {
        fprintf(stderr, "bad args\n"); rc = 2;
    }
    if (g_fd >= 0) close(g_fd);
    return rc;
}
