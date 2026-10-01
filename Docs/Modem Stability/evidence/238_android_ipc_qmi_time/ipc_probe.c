/*
 * ipc_probe.c -- freestanding ARMv7 QMI TIME client over the Android IPC
 * Router (AF_MSM_IPC / family 27), the transport `time_daemon` itself uses.
 *
 * WHY
 *   `ats-probe` (OpenWrt) uses QRTR, which Android does not have
 *   (CONFIG_QRTR absent).  Android's `time_daemon` reaches the modem's QMI
 *   TIME service over the IPC Router (AF_IB == 27, SOCK_DGRAM), captured
 *   live with strace:
 *
 *     sendto(13, "\0\1\0 \0\22\0\1\4\0\2\0\0\0\2\10\0\351u\305\\W\1\0\0",
 *            25, MSG_DONTWAIT,
 *            {sa_family=AF_IB, sa_data="\313\270\2\332\255\276\0\0\0\0\v\0\0\0A\34\360\266"},
 *            20)
 *     recvfrom(13, "\2\1\0 \0\7\0\2\4\0\0\0\0\0", 32, ...) = 14
 *
 *   The payload is a plain QMI message:  type(1) txn(2) msg_id(2) len(2) TLVs.
 *   The observed one is msg_id 0x0020 (GENOFF_SET), base 2.
 *   This program sends msg_id 0x0021 (GENOFF_GET) -- READ ONLY.
 *
 * DESTINATION ADDRESS (20 bytes, copied verbatim from the strace):
 *   1B 00 | CB B8 02 DA AD BE 00 00 00 00 0B 00 00 00 41 1C F0 B6
 *   (family 27; port 11 is visible at offset 10 -> matches the recorded
 *    "QMI TIME svc 22, node 0 port 11")
 *
 * BUILD:
 *   clang --target=armv7a-linux-gnueabi -mcpu=cortex-a7 -mthumb \
 *         -nostdlib -static -ffreestanding -fno-builtin -fno-stack-protector \
 *         -O2 -Wl,-e,_start -o ipc_probe ipc_probe.c
 */

typedef unsigned char      u8;
typedef unsigned short     u16;
typedef unsigned int       u32;
typedef unsigned long long u64;
typedef int                s32;
typedef long               slong;

#define SYS_exit          1
#define SYS_write         4
#define SYS_close         6
#define SYS_nanosleep     162
#define SYS_clock_gettime 263
#define SYS_socket        281
#define SYS_sendto        290
#define SYS_recvfrom      292
#define SYS_setsockopt    294

#define CLOCK_BOOTTIME    7
#define AF_MSM_IPC        27
#define SOCK_DGRAM        2
#define SOL_SOCKET        1
#define SO_RCVTIMEO       20

struct timespec { slong tv_sec; slong tv_nsec; };
struct timeval  { slong tv_sec; slong tv_usec; };

static slong sys6(slong n, slong a, slong b, slong c,
                  slong d, slong e, slong f)
{
    register slong r7 asm("r7") = n;
    register slong r0 asm("r0") = a;
    register slong r1 asm("r1") = b;
    register slong r2 asm("r2") = c;
    register slong r3 asm("r3") = d;
    register slong r4 asm("r4") = e;
    register slong r5 asm("r5") = f;
    asm volatile("svc #0" : "+r"(r0)
                 : "r"(r1), "r"(r2), "r"(r3), "r"(r4), "r"(r5), "r"(r7)
                 : "memory");
    return r0;
}
#define sys1(n,a)             sys6((n),(slong)(a),0,0,0,0,0)
#define sys2(n,a,b)           sys6((n),(slong)(a),(slong)(b),0,0,0,0)
#define sys3(n,a,b,c)         sys6((n),(slong)(a),(slong)(b),(slong)(c),0,0,0)
#define sys5(n,a,b,c,d,e)     sys6((n),(slong)(a),(slong)(b),(slong)(c),(slong)(d),(slong)(e),0)
#define sys6n(n,a,b,c,d,e,f)  sys6((n),(slong)(a),(slong)(b),(slong)(c),(slong)(d),(slong)(e),(slong)(f))

void *memset(void *p, int c, unsigned long n)
{ u8 *q = p; while (n--) *q++ = (u8)c; return p; }
void *memcpy(void *d, const void *s, unsigned long n)
{ u8 *a = d; const u8 *b = s; while (n--) *a++ = *b++; return d; }

static int u64_dec(char *dst, u64 v)
{
    char tmp[24]; int i = 0, j = 0;
    if (!v) { dst[0] = '0'; return 1; }
    while (v) { tmp[i++] = (char)('0' + (int)(v % 10)); v /= 10; }
    while (i) dst[j++] = tmp[--i];
    return j;
}
static int s32_dec(char *dst, s32 v)
{
    if (v < 0) { dst[0] = '-'; return 1 + u64_dec(dst + 1, (u64)(-(slong)v)); }
    return u64_dec(dst, (u64)v);
}
static void out(const char *s, int n) { sys3(SYS_write, 1, s, n); }
static void outs(const char *s) { int n = 0; while (s[n]) n++; out(s, n); }

static int hex2(char *o, u8 b)
{
    const char *h = "0123456789abcdef";
    o[0] = h[b >> 4]; o[1] = h[b & 15]; return 2;
}

/* the modem TIME service address, verbatim from the strace */
static const u8 dest_addr[20] = {
    0x1B, 0x00,
    0xCB, 0xB8, 0x02, 0xDA, 0xAD, 0xBE, 0x00, 0x00,
    0x00, 0x00, 0x0B, 0x00, 0x00, 0x00, 0x41, 0x1C,
    0xF0, 0xB6
};

static u64 clk_ms(int id)
{
    struct timespec ts;
    if (sys2(SYS_clock_gettime, id, &ts) != 0) return 0;
    return (u64)ts.tv_sec * 1000ull + (u64)(ts.tv_nsec / 1000000);
}

/* issue one GENOFF_GET (0x0021) for `base`; print the raw response */
static void genoff_get(s32 base, u64 ap_boot)
{
    u8 req[14], resp[128];
    char line[512];
    struct timeval tv;
    slong fd, n;
    int i, k;

    fd = sys3(SYS_socket, AF_MSM_IPC, SOCK_DGRAM, 0);
    if (fd < 0) { outs("IPC-SOCKET-FAIL\n"); return; }

    tv.tv_sec = 3; tv.tv_usec = 0;
    sys5(SYS_setsockopt, fd, SOL_SOCKET, SO_RCVTIMEO, (slong)&tv, sizeof(tv));

    /* QMI header: type=0(req) txn=1 msg_id=0x0021 len=7
     * TLV: type=0x01 len=4 value=base  */
    memset(req, 0, sizeof(req));
    req[0] = 0x00;                 /* request          */
    req[1] = 0x01; req[2] = 0x00;  /* txn id 1         */
    req[3] = 0x21; req[4] = 0x00;  /* msg_id 0x0021 GET*/
    req[5] = 0x07; req[6] = 0x00;  /* msg_len 7        */
    req[7] = 0x01; req[8] = 0x04; req[9] = 0x00;  /* TLV len 4 */
    req[10] = (u8)base; req[11] = 0; req[12] = 0; req[13] = 0;

    n = sys6n(SYS_sendto, fd, req, 14, 0, (slong)dest_addr, 20);
    k = 0;
    k += 9, memcpy(line, "IPC-GET base=", 13), k = 13;
    k += s32_dec(line + k, base);
    memcpy(line + k, " ap_boot_ms=", 12); k += 12;
    k += u64_dec(line + k, ap_boot);
    memcpy(line + k, " sendto=", 8); k += 8;
    k += s32_dec(line + k, (s32)n);

    if (n != 14) {
        memcpy(line + k, " ERR-SEND\n", 10); out(line, k + 10);
        sys1(SYS_close, fd); return;
    }

    n = sys6n(SYS_recvfrom, fd, resp, sizeof(resp), 0, 0, 0);
    sys1(SYS_close, fd);

    memcpy(line + k, " recv=", 6); k += 6;
    k += s32_dec(line + k, (s32)n);
    memcpy(line + k, " hex=", 5); k += 5;
    if (n > 0) {
        int lim = (n > 64) ? 64 : (int)n;
        for (i = 0; i < lim; i++) k += hex2(line + k, resp[i]);
        /* decode: msg_id at [3..4], len at [5..6], result TLV 0x02 */
        if (n >= 14) {
            u32 mid = (u32)resp[3] | ((u32)resp[4] << 8);
            memcpy(line + k, " msg_id=", 8); k += 8;
            k += u64_dec(line + k, mid);
            /* scan TLVs for type 2 (result) */
            {
                int off = 7;
                while (off + 3 <= (int)n) {
                    u8 t = resp[off];
                    u32 l = (u32)resp[off+1] | ((u32)resp[off+2] << 8);
                    if (off + 3 + (int)l > (int)n) break;
                    if (t == 0x02 && l >= 4) {
                        s32 r = (s32)((u32)resp[off+3] | ((u32)resp[off+4] << 8)
                                | ((u32)resp[off+5] << 16) | ((u32)resp[off+6] << 24));
                        memcpy(line + k, " result=", 8); k += 8;
                        k += s32_dec(line + k, r);
                    }
                    off += 3 + (int)l;
                }
            }
        }
    }
    line[k++] = '\n';
    out(line, k);
}

void _start(void)
{
    struct timespec slp;
    int b;
    slp.tv_sec = 1; slp.tv_nsec = 0;

    for (;;) {
        u64 t0 = clk_ms(CLOCK_BOOTTIME);
        /* base 0 = ATS_RTC (the target); base 1 = ATS_TOD (control) */
        for (b = 0; b <= 1; b++) genoff_get(b, t0);
        sys2(SYS_nanosleep, &slp, 0);
    }
}
