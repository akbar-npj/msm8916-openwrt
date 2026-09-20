/*
 * rpmring -- coherent, timestamped reader for the RPM external log ring.
 *
 * Why this exists
 * ---------------
 * The RPM log ring lives in RPM SRAM, visible to the AP at physical
 * 0x29dc58 (8 KiB = 256 records x 8 words).  It is *live*: the RPM writes
 * roughly 40 records/s, so the ring turns over in ~6 s.
 *
 * Reading it with busybox `devmem` costs ~2.9 ms per word (fork+exec), i.e.
 * 2048 words = 5.94 s for one pass -- a full ring period.  The result is not
 * a snapshot: slots are overwritten while you are still walking them, the
 * sequence aliases, and the apparent "span" is an artefact.  (Measured
 * 2026-09-21: two consecutive devmem dumps 6.0 s apart shared 0 records,
 * yet their index-0 timestamps differed by exactly one ring period.)
 *
 * `/dev/mem` read() is unusable here: dd returns EFAULT ("Bad address").
 * mmap() of /dev/mem works (that is how devmem does it), so this tool
 * mmaps once and memcpy()s the ring out in ~100 us -- ~60000x faster.
 *
 * It also brackets each copy with CLOCK_MONOTONIC, which gives the RPM
 * counter a wall-clock reference and therefore measures the tick rate.
 *
 * Build (OpenWrt cross toolchain in this tree):
 *   openwrt/staging_dir/toolchain-aarch64_generic_gcc-14.3.0_musl/bin/\
 *       aarch64-openwrt-linux-musl-gcc -O2 -static -o rpmring rpmring.c
 *
 * Ring write pointer
 * ------------------
 * Header word +0x38 (index 14) is a 32-bit *byte* counter over the ring; it
 * advances in exact multiples of 32 (one record).  Therefore
 *
 *     write_pos_records = (counter >> 5) & 0xFF
 *     newest_record_idx = (write_pos_records - 1) & 0xFF
 *
 * Verified 2026-09-21: the counter's low 13 bits / 32 predicted the ring's
 * single backward discontinuity index exactly (counter low13 = 0x1aa0 ->
 * record 213, and the discontinuity was at 212), and its deltas matched the
 * mod-256 advance of that index for two independent 400 ms intervals
 * (6464 B = 202 records, 5376 B = 168 records).
 *
 * Writes are extremely bursty: observed 202 records inside one 400 ms
 * interval, followed by 400 ms with zero records.  So a ring sample is only
 * coherent if no burst is in flight; -t re-reads the header around each ring
 * copy and discards samples whose counter moved.
 *
 * Usage:
 *   rpmring [-a 0x29dc58] [-w 2048] [-H 0x29dc00 -h 22] [-n 1] [-i 0]
 *           [-r] [-q] [-t]
 *     -a  ring base physical address (default 0x29dc58)
 *     -w  words per ring sample       (default 2048)
 *     -H  header physical address     (default 0x29dc00)
 *     -h  header words                (default 22)
 *     -n  number of samples           (default 1)
 *     -i  interval between samples in ms (default 0)
 *     -r  ring mode: print every sample (default: only the first + changes)
 *     -q  quiet: header lines only, no word dumps
 *     -t  track mode: follow the write counter and print every record the
 *         RPM writes, once, in order, with its CLOCK_MONOTONIC read time.
 *         Output is lossless as long as fewer than 256 records are written
 *         between two samples (at -i 20 that is a ~12800 rec/s budget).
 */
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <inttypes.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
#include <sys/mman.h>

#define PAGE 4096UL
#define REC_WORDS 8	/* 32-byte log record */

static uint64_t now_ns(void)
{
	struct timespec ts;
	clock_gettime(CLOCK_MONOTONIC, &ts);
	return (uint64_t)ts.tv_sec * 1000000000ULL + (uint64_t)ts.tv_nsec;
}

struct region {
	volatile uint32_t *base;	/* page-aligned mapping */
	uint32_t *buf;
	size_t words;
	unsigned long paddr;		/* original (unaligned) address */
	size_t off;			/* paddr - page base */
};

static int map_region(int fd, unsigned long paddr, size_t words, struct region *r)
{
	unsigned long page = paddr & ~(PAGE - 1);
	size_t off = paddr - page;
	size_t bytes = off + words * 4;

	void *m = mmap(NULL, bytes, PROT_READ, MAP_SHARED, fd, (off_t)page);
	if (m == MAP_FAILED)
		return -1;

	r->base = (volatile uint32_t *)m;
	r->off = off;
	r->words = words;
	r->paddr = paddr;
	r->buf = calloc(words, 4);
	return r->buf ? 0 : -1;
}

/* Copy the region out as fast as possible; return the two bracket times. */
static void sample(const struct region *r, uint32_t *dst, uint64_t *t0, uint64_t *t1)
{
	*t0 = now_ns();
	memcpy(dst, (const void *)(r->base + r->off / 4), r->words * 4);
	*t1 = now_ns();
}

static void dump_words(const uint32_t *w, size_t n)
{
	for (size_t i = 0; i < n; i += 8) {
		printf("%04zx", i);
		for (size_t j = 0; j < 8 && i + j < n; j++)
			printf(" 0x%08" PRIx32, w[i + j]);
		putchar('\n');
	}
}

int main(int argc, char **argv)
{
	unsigned long raddr = 0x29dc58, haddr = 0x29dc00;
	size_t rwords = 2048, hwords = 22;
	int nsamp = 1, interval_ms = 0, ring_mode = 0, quiet = 0, track = 0;
	int c;

	while ((c = getopt(argc, argv, "a:w:H:h:n:i:rqt")) != -1) {
		switch (c) {
		case 'a': raddr = strtoul(optarg, NULL, 0); break;
		case 'w': rwords = strtoul(optarg, NULL, 0); break;
		case 'H': haddr = strtoul(optarg, NULL, 0); break;
		case 'h': hwords = strtoul(optarg, NULL, 0); break;
		case 'n': nsamp = atoi(optarg); break;
		case 'i': interval_ms = atoi(optarg); break;
		case 'r': ring_mode = 1; break;
		case 'q': quiet = 1; break;
		case 't': track = 1; break;
		default:
			fprintf(stderr, "bad option\n");
			return 2;
		}
	}

	int fd = open("/dev/mem", O_RDONLY | O_SYNC);
	if (fd < 0) {
		fprintf(stderr, "open /dev/mem: %s\n", strerror(errno));
		return 1;
	}

	struct region ring, hdr;
	if (map_region(fd, raddr, rwords, &ring) < 0) {
		fprintf(stderr, "mmap ring %#lx: %s\n", raddr, strerror(errno));
		return 1;
	}
	int have_hdr = map_region(fd, haddr, hwords, &hdr) == 0;

	uint32_t *prev = calloc(rwords, 4);
	uint32_t *cur = calloc(rwords, 4);
	int have_prev = 0;

	printf("# rpmring ring=%#lx words=%zu hdr=%#lx hwords=%zu samples=%d interval=%dms\n",
	       raddr, rwords, haddr, hwords, nsamp, interval_ms);

	if (track) {
		if (!have_hdr) {
			fprintf(stderr, "track mode needs a readable header\n");
			return 1;
		}
		uint32_t prev_ctr = 0;
		int have_ctr = 0, clean = 0, dirty = 0, lost = 0;
		uint64_t emitted = 0;

		for (int s = 0; s < nsamp; s++) {
			uint64_t a0, a1, b0, b1, c0, c1;
			/* header, ring, header -- only accept if the counter is stable */
			sample(&hdr, hdr.buf, &a0, &a1);
			uint32_t c_a = hdr.buf[14];
			sample(&ring, cur, &b0, &b1);
			sample(&hdr, hdr.buf, &c0, &c1);
			uint32_t c_b = hdr.buf[14];

			if (c_a != c_b) {
				dirty++;
				goto next;
			}
			clean++;

			if (!have_ctr) {
				prev_ctr = c_a;
				have_ctr = 1;
				printf("# TRACK start t=%" PRIu64 " counter=%#010" PRIx32
				       " pos=%u\n", b0, c_a, (c_a >> 5) & 0xFF);
				goto next;
			}
			if (c_a == prev_ctr)
				goto next;

			uint32_t delta = c_a - prev_ctr;	/* unsigned, handles wrap */
			if (delta > 0x2000) {
				lost++;
				printf("# TRACK OVERRUN t=%" PRIu64 " counter=%#010" PRIx32
				       " delta=%u bytes (%u records > ring)\n",
				       b0, c_a, delta, delta >> 5);
				prev_ctr = c_a;
				goto next;
			}

			uint32_t start = (prev_ctr >> 5) & 0xFF;
			uint32_t nrec = delta >> 5;
			printf("# NEW t=%" PRIu64 " counter=%#010" PRIx32
			       " start=%u nrec=%u\n", b0, c_a, start, nrec);
			for (uint32_t k = 0; k < nrec; k++) {
				const uint32_t *r = &cur[((start + k) & 0xFF) * REC_WORDS];
				printf("R");
				for (int j = 0; j < REC_WORDS; j++)
					printf(" %08" PRIx32, r[j]);
				putchar('\n');
				emitted++;
			}
			prev_ctr = c_a;

next:
			if (s + 1 < nsamp && interval_ms > 0)
				usleep((useconds_t)interval_ms * 1000);
		}
		fprintf(stderr,
			"# TRACK done clean=%d dirty=%d overrun=%d records=%" PRIu64 "\n",
			clean, dirty, lost, emitted);
		return 0;
	}

	for (int s = 0; s < nsamp; s++) {
		uint64_t rt0, rt1, ht0, ht1;
		sample(&ring, cur, &rt0, &rt1);

		size_t diff = 0;
		if (have_prev)
			for (size_t i = 0; i < rwords; i++)
				if (cur[i] != prev[i])
					diff++;

		printf("# SAMPLE %d t0=%" PRIu64 " t1=%" PRIu64 " dur_ns=%" PRIu64
		       " changed_words=%zu\n", s, rt0, rt1, rt1 - rt0,
		       have_prev ? diff : 0);

		if (have_hdr) {
			sample(&hdr, hdr.buf, &ht0, &ht1);
			printf("# HDR t0=%" PRIu64 " words=", ht0);
			for (size_t i = 0; i < hwords; i++)
				printf(" 0x%08" PRIx32, hdr.buf[i]);
			putchar('\n');
		}

		if (!quiet && (s == 0 || ring_mode))
			dump_words(cur, rwords);

		memcpy(prev, cur, rwords * 4);
		have_prev = 1;

		if (s + 1 < nsamp && interval_ms > 0)
			usleep((useconds_t)interval_ms * 1000);
	}

	return 0;
}
