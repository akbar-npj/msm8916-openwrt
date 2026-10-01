/*
 * ats_all_arm.c -- freestanding ARMv7 reader that samples EVERY genoff base
 * 0..14 from the Android `time_genoff` daemon, stamped with CLOCK_BOOTTIME.
 *
 * Purpose: find which base (if any) carries modem-restart state, by watching
 * for a discontinuity at a modem fatal.  The daemon's base-0 (ATS_RTC) GET
 * returns EINVAL on this build, so we sweep the whole space.
 *
 * Output, one line per sample:
 *   ATS-ALL ap_boot_ms=<n> rt_boot_ms=<n> b0=<val>/<res> b1=<val>/<res> ...
 *
 * READ-ONLY: only op = 1 (GET) is ever emitted.  See ats_rtc_arm.c for the
 * protocol derivation.
 *
 * BUILD:
 *   clang --target=armv7a-linux-gnueabi -mcpu=cortex-a7 -mthumb \
 *         -nostdlib -static -ffreestanding -fno-builtin -fno-stack-protector \
 *         -O2 -Wl,-e,_start -o ats_all_arm ats_all_arm.c
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
#define SYS_connect       283
#define SYS_sendto        290
#define SYS_recvfrom      292

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

static u64 clk_ms(int id)
{
    struct timespec ts;
    if (sys2(SYS_clock_gettime, id, &ts) != 0) return 0;
    return (u64)ts.tv_sec * 1000ull + (u64)(ts.tv_nsec / 1000000);
}

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
    sa.sun_path[0] = 0;

    if (sys3(SYS_connect, fd, &sa, 14) != 0) { sys1(SYS_close, fd); return -101; }

    memset(req, 0, sizeof(req));
    *(u32 *)(req + 0x00) = (u32)base;
    *(u32 *)(req + 0x04) = 1u;
    *(u32 *)(req + 0x08) = 1u;                 /* GET */
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

#define PERIOD_MS 250

void _start(void)
{
    struct timespec slp;
    char line[1024];
    int n, b;

    slp.tv_sec  = PERIOD_MS / 1000;
    slp.tv_nsec = (PERIOD_MS % 1000) * 1000000L;

    for (;;) {
        u64 ap_boot, rt_boot;
        u64 v[15];
        s32 r[15];

        ap_boot = clk_ms(CLOCK_BOOTTIME);
        for (b = 0; b <= 14; b++) { v[b] = 0; r[b] = genoff_get(b, &v[b]); }
        rt_boot = clk_ms(CLOCK_BOOTTIME);

        n = 0;
        memcpy(line, "ATS-ALL ap_boot_ms=", 19); n = 19;
        n += u64_dec(line + n, ap_boot);
        memcpy(line + n, " rt_boot_ms=", 12); n += 12;
        n += u64_dec(line + n, rt_boot);
        for (b = 0; b <= 14; b++) {
            line[n++] = ' '; line[n++] = 'b';
            n += u64_dec(line + n, (u64)b);
            line[n++] = '=';
            n += u64_dec(line + n, v[b]);
            line[n++] = '/';
            n += s32_dec(line + n, r[b]);
        }
        line[n++] = '\n';
        out(line, n);

        sys2(SYS_nanosleep, &slp, 0);
    }
}
