/*
 * ats_rtc_arm.c -- freestanding ARMv7 reader for the Android `time_genoff`
 * daemon's ATS (always-on time source) values, stamped with CLOCK_BOOTTIME.
 *
 * WHY THIS EXISTS
 *   The OpenWrt arm runs `ats-probe`, a standalone QRTR/QMI TIME client that
 *   reads the modem's own ATS_RTC (base 0) and stamps it with the AP clock,
 *   giving R_fatal = modem uptime at a fatal with no borrowed offset.
 *   The Android arm has NO QRTR (CONFIG_QRTR absent), so `ats-probe` cannot
 *   run there.  But Android DOES run `time_daemon`, which owns the modem's
 *   QMI TIME link and exposes it over an abstract AF_UNIX socket
 *   ("time_genoff").  This program speaks that socket protocol directly.
 *
 * PROTOCOL (reverse-engineered from libtime_genoff.so, ARM/Thumb-2, md5
 *   4a0f4c9e... -- see evidence/238):  a 32-byte fixed record, one
 *   connection per request, on abstract socket name "time_genoff":
 *
 *     off  size  request                response
 *     0x00  4    base                   base
 *     0x04  4    const 1                const 1
 *     0x08  4    op  (1 = GET)           op
 *     0x0c  4    (unused)               (unused)
 *     0x10  8    ts_val (0 on GET)      ts_val   <-- the ATS value, ns
 *     0x18  4    result (-1 on request) result   (0 == success)
 *     0x1c  4    (unused)               (unused)
 *
 *   sockaddr_un: sun_family = AF_UNIX, sun_path = "\0time_genoff",
 *   addrlen = 2 + 1 + 11 = 14.
 *
 * READ-ONLY BY CONSTRUCTION
 *   The only operation this program ever emits is op = 1 (GET).  It never
 *   builds op = 0 (SET), 2 (DISABLE) or 3/4 (ENABLE).  The daemon's GET path
 *   does not write to the modem.
 *
 * BUILD (host clang can emit ARM; no NDK, no libc, no dynamic linker):
 *   clang --target=armv7a-linux-gnueabi -mcpu=cortex-a7 -mthumb \
 *         -nostdlib -static -ffreestanding -fno-builtin -fno-stack-protector \
 *         -O2 -Wl,-e,_start -o ats_rtc_arm ats_rtc_arm.c
 *
 * OUTPUT (one line per sample, to stdout):
 *   ATS-PROBE ap_boot_ms=<n> ap_mono_ms=<n> rtc_ns=<n> tod_ns=<n> \
 *             res_rtc=<d> res_tod=<d> rt_boot_ms=<n>
 *
 *   ap_boot_ms : CLOCK_BOOTTIME at the instant before the socket round trip
 *   ap_mono_ms : CLOCK_MONOTONIC at the same instant
 *   rtc_ns     : ATS_RTC  (base 0), nanoseconds
 *   tod_ns     : ATS_TOD  (base 1), nanoseconds
 *   res_rtc    : daemon result for base 0 (0 == ok, negative == error)
 *   res_tod    : daemon result for base 1
 *   rt_boot_ms : CLOCK_BOOTTIME after the round trip (brackets the latency)
 *
 * The period is PERIOD_MS.  Run under `timeout` to bound it.
 */

typedef unsigned char      u8;
typedef unsigned short     u16;
typedef unsigned int       u32;
typedef unsigned long long u64;
typedef int                s32;
typedef long               slong;

/* ---- raw ARM EABI syscalls -------------------------------------------- */
#define SYS_exit          1
#define SYS_read          3
#define SYS_write         4
#define SYS_close         6
#define SYS_nanosleep     162
#define SYS_clock_gettime 263
#define SYS_socket        281
#define SYS_connect       283
#define SYS_sendto        290
#define SYS_recvfrom      292

#define CLOCK_REALTIME    0
#define CLOCK_MONOTONIC   1
#define CLOCK_BOOTTIME    7

#define AF_UNIX           1
#define SOCK_STREAM       1

struct timespec { slong tv_sec; slong tv_nsec; };
struct sockaddr_un { u16 sun_family; char sun_path[108]; };

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
#define sys4(n,a,b,c,d)       sys6((n),(slong)(a),(slong)(b),(slong)(c),(slong)(d),0,0)
#define sys6n(n,a,b,c,d,e,f)  sys6((n),(slong)(a),(slong)(b),(slong)(c),(slong)(d),(slong)(e),(slong)(f))

/* clang may still synthesise these despite -fno-builtin */
void *memset(void *p, int c, unsigned long n)
{ u8 *q = p; while (n--) *q++ = (u8)c; return p; }
void *memcpy(void *d, const void *s, unsigned long n)
{ u8 *a = d; const u8 *b = s; while (n--) *a++ = *b++; return d; }

/* ---- tiny formatting -------------------------------------------------- */
static int u64_dec(char *dst, u64 v)
{
    char tmp[24];
    int i = 0, j = 0;
    if (v == 0) { dst[0] = '0'; return 1; }
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

/* append "name=" + decimal */
static int app_kv(char *o, const char *k, u64 v)
{
    int n = 0;
    while (k[n]) o[n] = k[n], n++;
    return n + u64_dec(o + n, v);
}
static int app_kvs(char *o, const char *k, s32 v)
{
    int n = 0;
    while (k[n]) o[n] = k[n], n++;
    return n + s32_dec(o + n, v);
}

/* ---- clocks ----------------------------------------------------------- */
static u64 clk_ms(int id)
{
    struct timespec ts;
    if (sys2(SYS_clock_gettime, id, &ts) != 0) return 0;
    return (u64)ts.tv_sec * 1000ull + (u64)(ts.tv_nsec / 1000000);
}

/* ---- one ATS read ----------------------------------------------------- */
/* returns the daemon's `result` field, or a negative transport error */
static s32 genoff_get(s32 base, u64 *out_ns)
{
    struct sockaddr_un sa;
    u8 req[32], resp[32];
    int i;
    slong fd, n;
    const char *nm = "time_genoff";

    fd = sys3(SYS_socket, AF_UNIX, SOCK_STREAM, 0);
    if (fd < 0) return -100;

    memset(&sa, 0, sizeof(sa));
    sa.sun_family = AF_UNIX;
    for (i = 0; i < 11; i++) sa.sun_path[1 + i] = nm[i];
    sa.sun_path[0] = 0;                       /* abstract */

    if (sys3(SYS_connect, fd, &sa, 14) != 0) { sys1(SYS_close, fd); return -101; }

    memset(req, 0, sizeof(req));
    *(u32 *)(req + 0x00) = (u32)base;
    *(u32 *)(req + 0x04) = 1u;                /* constant */
    *(u32 *)(req + 0x08) = 1u;                /* op = GET  (never SET) */
    *(u64 *)(req + 0x10) = 0ull;
    *(s32 *)(req + 0x18) = -1;

    n = sys6n(SYS_sendto, fd, req, 32, 0, 0, 0);
    if (n != 32) { sys1(SYS_close, fd); return -102; }

    n = sys6n(SYS_recvfrom, fd, resp, 32, 0, 0, 0);
    sys1(SYS_close, fd);
    if (n < 32) return -103;

    *out_ns = *(u64 *)(resp + 0x10);
    return *(s32 *)(resp + 0x18);
}

/* ---- main loop -------------------------------------------------------- */
#define PERIOD_MS 250

void _start(void)
{
    struct timespec slp;
    u64 rtc, tod, ap_boot, ap_mono, rt_boot;
    s32 res_rtc, res_tod;
    char line[256];
    int n;

    slp.tv_sec  = PERIOD_MS / 1000;
    slp.tv_nsec = (PERIOD_MS % 1000) * 1000000L;

    for (;;) {
        rtc = 0; tod = 0;
        ap_boot = clk_ms(CLOCK_BOOTTIME);
        ap_mono = clk_ms(CLOCK_MONOTONIC);
        res_rtc = genoff_get(0, &rtc);          /* base 0 = ATS_RTC */
        res_tod = genoff_get(1, &tod);          /* base 1 = ATS_TOD */
        rt_boot = clk_ms(CLOCK_BOOTTIME);

        n  = 0;
        n += 10, memcpy(line + 0, "ATS-PROBE ", 10);
        n += app_kv (line + n, "ap_boot_ms=", ap_boot);
        line[n++] = ' ';
        n += app_kv (line + n, "ap_mono_ms=", ap_mono);
        line[n++] = ' ';
        n += app_kv (line + n, "rtc_ns=", rtc);
        line[n++] = ' ';
        n += app_kv (line + n, "tod_ns=", tod);
        line[n++] = ' ';
        n += app_kvs(line + n, "res_rtc=", res_rtc);
        line[n++] = ' ';
        n += app_kvs(line + n, "res_tod=", res_tod);
        line[n++] = ' ';
        n += app_kv (line + n, "rt_boot_ms=", rt_boot);
        line[n++] = '\n';

        out(line, n);

        sys2(SYS_nanosleep, &slp, 0);
    }
}
