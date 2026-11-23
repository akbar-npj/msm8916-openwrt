/*
 * lcd-dashboard - draw an Android-style status dashboard on the ST7735R SPI LCD.
 *
 * Reads two plain key=value files produced by /usr/sbin/lcd-dashboard-collect:
 *   /var/run/lcd-dashboard/view   slot->source mapping, theme colours
 *   /var/run/lcd-dashboard/state  current values for every source
 * and paints /dev/fb0 (128x128, 32bpp, 0x00RRGGBB) directly.  No libc font or
 * graphics library is used: a compact 5x7 bitmap font and a few rect icons are
 * embedded below.  After drawing, FBIOPAN_DISPLAY flushes the deferred-IO
 * pages to the panel.
 *
 * This program never touches the backlight - that is owned by lcd-control.
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <errno.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <time.h>
#include <linux/fb.h>

#define RUN_DIR   "/var/run/lcd-dashboard"
#define VIEW_FILE RUN_DIR "/view"
#define STATE_FILE RUN_DIR "/state"
#define NOTIFY_FILE RUN_DIR "/notify"

#define FB_PATH   "/dev/fb0"
#define W         128
#define H         128

/* ---- framebuffer -------------------------------------------------------- */

static uint32_t *fb;
static int fb_fd;
static struct fb_var_screeninfo vinfo;
static struct fb_fix_screeninfo finfo;

static int fb_open(void)
{
	fb_fd = open(FB_PATH, O_RDWR);
	if (fb_fd < 0)
		return -1;
	if (ioctl(fb_fd, FBIOGET_VSCREENINFO, &vinfo) < 0 ||
	    ioctl(fb_fd, FBIOGET_FSCREENINFO, &finfo) < 0) {
		close(fb_fd);
		return -1;
	}
	/* This dashboard is written for the 128x128 st7735r panel. */
	if (vinfo.xres < W || vinfo.yres < H || vinfo.bits_per_pixel != 32) {
		close(fb_fd);
		return -1;
	}
	fb = mmap(NULL, finfo.smem_len, PROT_READ | PROT_WRITE, MAP_SHARED, fb_fd, 0);
	if (fb == MAP_FAILED) {
		close(fb_fd);
		return -1;
	}
	return 0;
}

static void fb_close(void)
{
	if (fb && fb != MAP_FAILED)
		munmap(fb, finfo.smem_len);
	if (fb_fd >= 0)
		close(fb_fd);
}

static inline void pixel(int x, int y, uint32_t c)
{
	if (x < 0 || y < 0 || x >= (int)vinfo.xres || y >= (int)vinfo.yres)
		return;
	fb[(size_t)y * (finfo.line_length / 4) + x] = c;
}

static void rect(int x, int y, int w, int h, uint32_t c)
{
	int i, j;
	for (j = y; j < y + h; j++)
		for (i = x; i < x + w; i++)
			pixel(i, j, c);
}

static void hline(int x, int y, int w, uint32_t c)
{
	rect(x, y, w, 1, c);
}

/* ---- 5x7 font (public domain, bits 4..0 = left..right) ------------------ */

static const uint8_t FONT[][7] = {
	[' '] = {0,0,0,0,0,0,0},
	['!'] = {4,4,4,4,4,0,4},
	['"'] = {10,10,0,0,0,0,0},
	['#'] = {10,31,10,10,31,10,0},
	['$'] = {4,15,20,14,5,30,4},
	['%'] = {25,25,2,4,8,19,19},
	['&'] = {12,18,20,8,21,18,13},
	['\'']= {4,4,0,0,0,0,0},
	['('] = {2,4,8,8,8,4,2},
	[')'] = {8,4,2,2,2,4,8},
	['*'] = {0,21,14,31,14,21,0},
	['+'] = {0,4,4,31,4,4,0},
	[','] = {0,0,0,0,12,12,8},
	['-'] = {0,0,0,31,0,0,0},
	['.'] = {0,0,0,0,0,12,12},
	['/'] = {1,2,2,4,8,8,16},
	['0'] = {14,17,19,21,25,17,14},
	['1'] = {4,12,4,4,4,4,14},
	['2'] = {14,17,1,2,4,8,31},
	['3'] = {31,2,4,2,1,17,14},
	['4'] = {2,6,10,18,31,2,2},
	['5'] = {31,16,30,1,1,17,14},
	['6'] = {6,8,16,30,17,17,14},
	['7'] = {31,1,2,4,8,8,8},
	['8'] = {14,17,17,14,17,17,14},
	['9'] = {14,17,17,15,1,2,12},
	[':'] = {0,4,4,0,4,4,0},
	[';'] = {0,4,4,0,4,4,8},
	['<'] = {2,4,8,16,8,4,2},
	['='] = {0,31,0,31,0,0,0},
	['>'] = {8,4,2,1,2,4,8},
	['?'] = {14,17,1,2,4,0,4},
	['@'] = {14,17,23,21,23,16,14},
	['['] = {14,8,8,8,8,8,14},
	['\\']= {16,8,8,4,2,2,1},
	[']'] = {14,2,2,2,2,2,14},
	['^'] = {4,10,17,0,0,0,0},
	['`'] = {8,4,0,0,0,0,0},
	['A'] = {14,17,17,31,17,17,17},
	['B'] = {30,17,17,30,17,17,30},
	['C'] = {14,17,16,16,16,17,14},
	['D'] = {30,17,17,17,17,17,30},
	['E'] = {31,16,16,30,16,16,31},
	['F'] = {31,16,16,30,16,16,16},
	['G'] = {14,17,16,23,17,17,15},
	['H'] = {17,17,17,31,17,17,17},
	['I'] = {14,4,4,4,4,4,14},
	['J'] = {7,2,2,2,2,18,12},
	['K'] = {17,18,20,24,20,18,17},
	['L'] = {16,16,16,16,16,16,31},
	['M'] = {17,27,21,21,17,17,17},
	['N'] = {17,25,21,19,17,17,17},
	['O'] = {14,17,17,17,17,17,14},
	['P'] = {30,17,17,30,16,16,16},
	['Q'] = {14,17,17,17,21,18,13},
	['R'] = {30,17,17,30,20,18,17},
	['S'] = {15,16,16,14,1,1,30},
	['T'] = {31,4,4,4,4,4,4},
	['U'] = {17,17,17,17,17,17,14},
	['V'] = {17,17,17,17,17,10,4},
	['W'] = {17,17,17,21,21,27,17},
	['X'] = {17,17,10,4,10,17,17},
	['Y'] = {17,17,10,4,4,4,4},
	['Z'] = {31,1,2,4,8,16,31},
	['_'] = {0,0,0,0,0,0,31},
	/* lower-case: x-height on rows 2..6, ascenders reach row 0; the
	 * descenders of g/j/p/q/y are compressed into row 6 (7-row cell). */
	['a'] = {0,0,14,1,15,17,15},
	['b'] = {16,16,22,25,17,17,30},
	['c'] = {0,0,14,17,16,17,14},
	['d'] = {1,1,13,19,17,17,15},
	['e'] = {0,0,14,17,31,16,14},
	['f'] = {6,9,8,28,8,8,8},
	['g'] = {0,0,15,17,15,1,14},
	['h'] = {16,16,22,25,17,17,17},
	['i'] = {4,0,12,4,4,4,14},
	['j'] = {2,0,6,2,2,18,12},
	['k'] = {16,16,18,20,24,20,18},
	['l'] = {12,4,4,4,4,4,14},
	['m'] = {0,0,26,21,21,17,17},
	['n'] = {0,0,22,25,17,17,17},
	['o'] = {0,0,14,17,17,17,14},
	['p'] = {0,0,30,17,30,16,16},
	['q'] = {0,0,15,17,15,1,1},
	['r'] = {0,0,22,25,16,16,16},
	['s'] = {0,0,15,16,14,1,30},
	['t'] = {8,8,28,8,8,9,6},
	['u'] = {0,0,17,17,17,19,13},
	['v'] = {0,0,17,17,17,10,4},
	['w'] = {0,0,17,17,21,21,10},
	['x'] = {0,0,17,10,4,10,17},
	['y'] = {0,0,17,17,15,1,14},
	['z'] = {0,0,31,2,4,8,31},
	['{'] = {6,8,8,16,8,8,6},
	['|'] = {4,4,4,4,4,4,4},
	['}'] = {12,2,2,1,2,2,12},
	['~'] = {0,0,9,22,0,0,0},
};

/* unknown -> space; lower-case has its own glyphs */
static const uint8_t *glyph(char c)
{
	if ((size_t)(unsigned char)c < sizeof(FONT) / sizeof(FONT[0]))
		return FONT[(unsigned char)c];
	return FONT[' '];
}

static int text_width(const char *s, int scale)
{
	int n = 0;
	while (*s)
		n += 6 * scale, s++;
	return n ? n - scale : 0;
}

static int draw_text(int x, int y, const char *s, int scale, uint32_t c)
{
	int cx = x;
	while (*s) {
		const uint8_t *g = glyph(*s++);
		int row, col;
		for (row = 0; row < 7; row++)
			for (col = 0; col < 5; col++)
				if (g[row] & (1 << (4 - col)))
					rect(cx + col * scale, y + row * scale, scale, scale, c);
		cx += 6 * scale;
	}
	return cx;
}

static void draw_text_centred(int x, int y, int w, const char *s, int scale, uint32_t c)
{
	int tw = text_width(s, scale);
	if (tw > w)              /* fall back one scale to fit */
		scale = 1, tw = text_width(s, scale);
	draw_text(x + (w - tw) / 2, y, s, scale, c);
}

/* ---- key=value parsing -------------------------------------------------- */

struct kv {
	char key[32];
	char val[96];
};

static int load_kv(const char *path, struct kv *out, int max)
{
	static char buf[16384];
	int fd, n, count = 0;
	size_t len = 0;
	char *p, *end;

	fd = open(path, O_RDONLY);
	if (fd < 0)
		return 0;
	while ((n = read(fd, buf + len, sizeof(buf) - 1 - len)) > 0)
		len += n;
	close(fd);
	buf[len] = 0;

	p = buf;
	while (p && *p && count < max) {
		char *eol = strchr(p, '\n');
		char *eq;
		end = eol ? eol : p + strlen(p);
		*end = 0;
		if (*p && *p != '#') {
			eq = strchr(p, '=');
			if (eq) {
				size_t kl = (size_t)(eq - p);
				if (kl >= sizeof(out[count].key))
					kl = sizeof(out[count].key) - 1;
				memcpy(out[count].key, p, kl);
				out[count].key[kl] = 0;
				strncpy(out[count].val, eq + 1, sizeof(out[count].val) - 1);
				out[count].val[sizeof(out[count].val) - 1] = 0;
				count++;
			}
		}
		p = eol ? eol + 1 : NULL;
	}
	return count;
}

static const char *kv_get(struct kv *kv, int n, const char *key, const char *def)
{
	int i;
	for (i = 0; i < n; i++)
		if (!strcmp(kv[i].key, key))
			return kv[i].val;
	return def;
}

static uint32_t parse_colour(const char *s, uint32_t def)
{
	uint32_t r, g, b;
	if (!s)
		return def;
	if (*s == '#')
		s++;
	if (strlen(s) != 6 || sscanf(s, "%2x%2x%2x", &r, &g, &b) != 3)
		return def;
	return ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
}

/* ---- icons -------------------------------------------------------------- */

static void icon_signal(int x, int y, int level, uint32_t on, uint32_t off)
{
	static const int heights[] = {5, 9, 13, 17};
	int b, yy;
	for (b = 0; b < 4; b++) {
		uint32_t c = (level >= b + 1) ? on : off;
		for (yy = 0; yy < heights[b]; yy++)
			rect(x + b * 7, y + 17 - heights[b] + yy, 5, 1, c);
	}
}

static void icon_sim(int x, int y, uint32_t c)
{
	rect(x + 1, y + 1, 13, 17, c);
	rect(x + 2, y + 2, 11, 15, 0);
	/* clipped top-right corner and three contact lines */
	rect(x + 10, y + 2, 3, 3, 0);
	hline(x + 3, y + 7, 3, c);
	hline(x + 3, y + 10, 3, c);
	hline(x + 3, y + 13, 3, c);
}

static void icon_wifi(int x, int y, uint32_t c)
{
	/* three nested arcs approximated by stepped rows */
	hline(x + 4, y + 5, 6, c);
	hline(x + 2, y + 7, 10, c);
	hline(x + 0, y + 9, 14, c);
	rect(x + 6, y + 12, 2, 3, c);
	rect(x + 4, y + 14, 6, 2, c);
}

static void icon_battery(int x, int y, int pct, uint32_t c)
{
	int fill;
	rect(x + 1, y + 4, 12, 12, c);
	rect(x + 2, y + 5, 10, 10, 0);
	rect(x + 13, y + 8, 2, 4, c);
	fill = (pct * 10) / 100;
	if (fill > 10)
		fill = 10;
	rect(x + 2, y + 5 + 10 - fill, 10, fill, c);
}

static void icon_arrow(int x, int y, int up, uint32_t c)
{
	int i;
	if (up) {
		for (i = 0; i < 5; i++)
			hline(x + 4 - i, y + 2 + i, 1 + i * 2, c);
		rect(x + 6, y + 7, 2, 9, c);
	} else {
		rect(x + 6, y + 1, 2, 9, c);
		for (i = 4; i >= 0; i--)
			hline(x + 4 - i, y + 10 + (4 - i), 1 + i * 2, c);
	}
}

static void icon_sms(int x, int y, uint32_t c)
{
	rect(x, y + 3, 15, 11, c);
	rect(x + 1, y + 4, 13, 9, 0);
	hline(x + 1, y + 4, 13, c);
	hline(x + 3, y + 7, 3, c);
	hline(x + 6, y + 8, 3, c);
	hline(x + 9, y + 7, 3, c);
}

static void icon_phone(int x, int y, uint32_t c)
{
	rect(x + 2, y + 1, 10, 17, c);
	rect(x + 3, y + 2, 8, 15, 0);
	rect(x + 6, y + 3, 2, 3, c);
	rect(x + 6, y + 14, 2, 2, c);
}

/* ---- render ------------------------------------------------------------- */

static int atoi_safe(const char *s, int def)
{
	char *end;
	long v;
	if (!s || !*s)
		return def;
	errno = 0;
	v = strtol(s, &end, 10);
	if (errno || *end)
		return def;
	return (int)v;
}

int main(void)
{
	struct kv view[64], state[128];
	int nv, ns;
	const char *v;
	uint32_t top_bg, top_fg, mid_bg, bot_bg, bot_fg;
	uint32_t tile_on, tile_off, pop_bg, pop_fg;
	const char *slot[8];
	int i;

	/* Board gate: the renderer is harmless if fb0 is missing or not ours. */
	if (fb_open() < 0)
		return 0;

	nv = load_kv(VIEW_FILE, view, 64);
	ns = load_kv(STATE_FILE, state, 128);

#define V(k, d) kv_get(view, nv, (k), (d))
#define S(k, d) kv_get(state, ns, (k), (d))

	top_bg   = parse_colour(V("color_top_bg",   "#3fb34f"), 0x3fb34f);
	top_fg   = parse_colour(V("color_top_fg",   "#ffffff"), 0xffffff);
	mid_bg   = parse_colour(V("color_mid_bg",   "#101010"), 0x101010);
	bot_bg   = parse_colour(V("color_bottom_bg","#1e6fe0"), 0x1e6fe0);
	bot_fg   = parse_colour(V("color_bottom_fg","#ffffff"), 0xffffff);
	tile_on  = parse_colour(V("color_tile_on",  "#3fb34f"), 0x3fb34f);
	tile_off = parse_colour(V("color_tile_off", "#c62828"), 0xc62828);
	pop_bg   = parse_colour(V("color_popup_bg", "#b71c1c"), 0xb71c1c);
	pop_fg   = parse_colour(V("color_popup_fg", "#ffffff"), 0xffffff);

	slot[0] = V("top_left",  "signal");
	slot[1] = V("top_right", "operator");
	slot[2] = V("tile1", "sim");
	slot[3] = V("tile2", "wifi");
	slot[4] = V("tile3", "traffic");
	slot[5] = V("tile4", "battery");
	slot[6] = V("bottom1", "ssid");
	slot[7] = V("bottom2", "key");

	/* clear + three bands */
	rect(0, 0, W, H, 0);
	rect(0, 0,   W, 32, top_bg);
	rect(0, 32,  W, 48, mid_bg);
	rect(0, 80,  W, 48, bot_bg);

	/* top band: signal bars on the left, status text on the right */
	v = slot[0];
	if (!strcmp(v, "none")) {
		/* blank zone */
	} else if (!strcmp(v, "signal")) {
		int lvl = atoi_safe(S("signal", "-1"), -1);
		/* Bars only: the operator zone to the right owns the text area. */
		icon_signal(3, 5, lvl, top_fg, (top_bg >> 1) & 0x7f7f7f);
	} else {
		draw_text(3, 12, S(v, ""), 2, top_fg);
	}
	{
		const char *r = slot[1];
		char buf[96];
		const char *t = S(r, "");
		if (!strcmp(r, "operator")) {
			const char *op = S("operator", "NO SIM");
			snprintf(buf, sizeof(buf), "%s", op);
			t = buf;
		}
		draw_text_centred(64, 12, 60, t, 2, top_fg);
	}

	/* middle band: up to four tiles, 32 px wide */
	for (i = 0; i < 4; i++) {
		const char *src = slot[2 + i];
		int tx = i * 32;
		int on = 0, pct = 0;
		char label[16] = "";
		uint32_t ic;

		if (!strcmp(src, "none"))
			continue;

		if (!strcmp(src, "sim")) {
			on = strcmp(S("sim", "absent"), "absent") != 0;
			ic = on ? tile_on : tile_off;
			icon_sim(tx + 9, 34, ic);
			snprintf(label, sizeof(label), "SIM");
		} else if (!strcmp(src, "wifi")) {
			on = atoi_safe(S("clients", "0"), 0) > 0;
			ic = on ? tile_on : tile_off;
			icon_wifi(tx + 9, 34, ic);
			snprintf(label, sizeof(label), "W%d", atoi_safe(S("clients", "0"), 0));
		} else if (!strcmp(src, "traffic")) {
			int rx = atoi_safe(S("rx_active", "0"), 0);
			int tx_ = atoi_safe(S("tx_active", "0"), 0);
			ic = (rx || tx_) ? tile_on : (top_bg >> 1) & 0x7f7f7f;
			icon_arrow(tx + 5, 34, 1, rx ? tile_on : ic);
			icon_arrow(tx + 17, 34, 0, tx_ ? tile_on : ic);
			snprintf(label, sizeof(label), "DATA");
		} else if (!strcmp(src, "battery")) {
			pct = atoi_safe(S("battery", "0"), 0);
			on = atoi_safe(S("charging", "0"), 0);
			ic = (pct < 20) ? tile_off : tile_on;
			icon_battery(tx + 9, 34, pct, ic);
			snprintf(label, sizeof(label), "%d%%", pct);
		} else if (!strcmp(src, "sms")) {
			int n = atoi_safe(S("sms_unread", "-1"), -1);
			ic = (n > 0) ? tile_on : tile_off;
			icon_sms(tx + 9, 34, ic);
			snprintf(label, sizeof(label), n < 0 ? "SMS" : "S%d", n);
		} else if (!strcmp(src, "missed")) {
			int n = atoi_safe(S("missed_calls", "-1"), -1);
			int ring = atoi_safe(S("call_active", "0"), 0);
			ic = (ring || n > 0) ? tile_on : tile_off;
			icon_phone(tx + 9, 34, ic);
			if (ring)
				snprintf(label, sizeof(label), "RING");
			else
				snprintf(label, sizeof(label), n < 0 ? "CALL" : "M%d", n);
		} else {
			ic = tile_on;
			snprintf(label, sizeof(label), "%.12s", S(src, ""));
			draw_text_centred(tx + 1, 40, 30, label, 1, ic);
			continue;
		}
		draw_text_centred(tx + 1, 64, 30, label, 1, ic);
	}

	/* bottom band: two text lines */
	{
		const char *a = slot[6], *b = slot[7];
		char bufa[96], bufb[96];
		const char *ta = S(a, ""), *tb = S(b, "");
		if (!strcmp(a, "ssid")) {
			snprintf(bufa, sizeof(bufa), "SSID:%s", S("ssid", ""));
			ta = bufa;
		} else if (!strcmp(a, "key")) {
			snprintf(bufa, sizeof(bufa), "KEY:%s", S("key", ""));
			ta = bufa;
		}
		if (!strcmp(b, "ssid")) {
			snprintf(bufb, sizeof(bufb), "SSID:%s", S("ssid", ""));
			tb = bufb;
		} else if (!strcmp(b, "key")) {
			snprintf(bufb, sizeof(bufb), "KEY:%s", S("key", ""));
			tb = bufb;
		}
		draw_text_centred(0, 92, W, ta, 2, bot_fg);
		draw_text_centred(0, 112, W, tb, 2, bot_fg);
	}

	/* notification overlay: expiry<TAB>text */
	{
		static char nb[512];
		int fd = open(NOTIFY_FILE, O_RDONLY);
		if (fd >= 0) {
			ssize_t n = read(fd, nb, sizeof(nb) - 1);
			close(fd);
			if (n > 0) {
				char *tab;
				nb[n] = 0;
				tab = strchr(nb, '\t');
				if (tab) {
					long expiry = strtol(nb, NULL, 10);
					long left = expiry - (long)time(NULL);
					if (left > 0) {
						char tail[48];

						*tab = 0;
						snprintf(tail, sizeof(tail), "AUTO-HIDE %lds", left);
						rect(0, 0, W, H, pop_bg);
						draw_text_centred(0, 20, W, "NOTIFICATION", 2, pop_fg);
						draw_text_centred(0, 56, W, tab + 1, 2, pop_fg);
						draw_text_centred(0, 100, W, tail, 1, pop_fg);
					}
				}
			}
		}
	}

	ioctl(fb_fd, FBIOPAN_DISPLAY, &vinfo);
	fb_close();
	return 0;

#undef V
#undef S
}
