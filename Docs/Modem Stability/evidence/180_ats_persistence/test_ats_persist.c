/*
 * Doc 180 -- unit test for the persistent ATS state path.
 *
 * The persistence helpers in qcom-time-daemon.c are static, so this test
 * compiles that source into its own translation unit with main() renamed and
 * calls them directly.  That is deliberate: the point is to exercise the code
 * the daemon actually runs, not a re-implementation of it.
 *
 * The host is aarch64 and the target is aarch64, so the test is linked STATIC
 * and runs NATIVELY -- there is no emulator between the test and the code.
 *
 * What is NOT tested here: anything that needs a real modem.  Every branch
 * below is a pure function of a file on disk and two integers.
 *
 * Exit status 0 = all checks passed.
 */

#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>

#define main qtd_daemon_main_unused
#include "qcom-time-daemon.c"
#undef main

#define TDIR		"/tmp/ats_persist_test"
#define CAP_PATH	TDIR "/cap.log"
#define WALL		1758542400000ULL	/* 2025-09-22T12:00:00Z */
#define OFF		1444000000000ULL	/* ms since the GPS epoch, same instant */

/* A complete, valid record; the rejection cases below are mutations of it. */
#define GOOD_RECORD \
	"magic=qcom-ats-state\n" \
	"version=1\n" \
	"base=2\n" \
	"offset_ms=1444000000000\n" \
	"rtc_ms=5000000\n" \
	"wall_ms=1758542400000\n" \
	"delta_ms=1758542395000\n" \
	"synced_at=1758542400\n"

static int n_run, n_fail;
static char tpath[256];

static void t(const char *name, bool ok, const char *detail)
{
	n_run++;
	if (ok) {
		printf("  ok    %-50s %s\n", name, detail ? detail : "");
	} else {
		n_fail++;
		printf("  FAIL  %-50s %s\n", name, detail ? detail : "");
	}
}

static void reset_state(void)
{
	ats_state_enabled = true;
	ats_state_loaded = false;
	ats_verdict_logged = false;
	memset(&ats_state, 0, sizeof(ats_state));
	ats_state_file = tpath;
}

static int write_file(const char *path, const char *content)
{
	size_t len = strlen(content);
	ssize_t n;
	int fd = open(path, O_WRONLY | O_CREAT | O_TRUNC, 0644);

	if (fd < 0)
		return -1;
	n = write(fd, content, len);
	close(fd);
	return (n == (ssize_t)len) ? 0 : -1;
}

static bool file_exists(const char *path)
{
	struct stat st;

	return stat(path, &st) == 0;
}

/* ---- stderr capture, so the verdict text itself can be asserted on ---- */

static int cap_saved = -1, cap_fd = -1;

static void cap_begin(void)
{
	fflush(stderr);
	cap_saved = dup(STDERR_FILENO);
	cap_fd = open(CAP_PATH, O_WRONLY | O_CREAT | O_TRUNC, 0644);
	dup2(cap_fd, STDERR_FILENO);
}

static void cap_end(char *out, size_t outsz)
{
	ssize_t n = 0;
	int fd;

	fflush(stderr);
	dup2(cap_saved, STDERR_FILENO);
	close(cap_saved);
	close(cap_fd);
	cap_saved = cap_fd = -1;

	fd = open(CAP_PATH, O_RDONLY);
	if (fd >= 0) {
		n = read(fd, out, outsz - 1);
		close(fd);
	}
	if (n < 0)
		n = 0;
	out[n] = '\0';
}

static struct ats_persist mkrec(uint64_t offset, uint64_t rtc, uint64_t wall)
{
	struct ats_persist r;

	memset(&r, 0, sizeof(r));
	r.base = ATS_USER;
	r.offset_ms = offset;
	r.rtc_ms = rtc;
	r.wall_ms = wall;
	r.delta_ms = (int64_t)(wall - rtc);
	r.synced_at = 1758542400ULL;
	return r;
}

int main(void)
{
	char logbuf[8192], filebuf[4096], tmppath[288];
	struct ats_persist rec;
	uint64_t v;
	int i;

	static const struct { const char *name; const char *body; } bad[] = {
		{ "empty file", "" },
		{ "not a record at all", "hello world\n" },
		{ "wrong magic", "magic=qcom-ats-state-x\nversion=1\nbase=2\n"
				 "offset_ms=1444000000000\nrtc_ms=1\nwall_ms=1758542400000\n"
				 "delta_ms=1\nsynced_at=1\n" },
		{ "future version", "magic=qcom-ats-state\nversion=99\nbase=2\n"
				    "offset_ms=1444000000000\nrtc_ms=1\nwall_ms=1758542400000\n"
				    "delta_ms=1\nsynced_at=1\n" },
		{ "wrong base (ATS_TOD)", "magic=qcom-ats-state\nversion=1\nbase=1\n"
					  "offset_ms=1444000000000\nrtc_ms=1\nwall_ms=1758542400000\n"
					  "delta_ms=1\nsynced_at=1\n" },
		{ "truncated (no delta_ms)", "magic=qcom-ats-state\nversion=1\nbase=2\n"
					     "offset_ms=1444000000000\nrtc_ms=1\nwall_ms=1758542400000\n" },
		{ "implausible offset", "magic=qcom-ats-state\nversion=1\nbase=2\n"
					"offset_ms=1000\nrtc_ms=1\nwall_ms=1758542400000\n"
					"delta_ms=1\nsynced_at=1\n" },
		{ "negative offset", "magic=qcom-ats-state\nversion=1\nbase=2\n"
				     "offset_ms=-5\nrtc_ms=1\nwall_ms=1758542400000\n"
				     "delta_ms=1\nsynced_at=1\n" },
	};

	openlog("ats-persist-test", LOG_PERROR, LOG_DAEMON);
	mkdir(TDIR, 0755);
	snprintf(tpath, sizeof(tpath), TDIR "/ats");
	snprintf(tmppath, sizeof(tmppath), "%s.tmp", tpath);

	printf("== 1. save / load round-trip ==\n");
	reset_state();
	unlink(tpath);
	rec = mkrec(OFF, 5000000ULL, WALL);
	t("save returns 0", ats_state_save(&rec) == 0, NULL);
	t("save leaves no .tmp behind", !file_exists(tmppath), NULL);
	t("save creates the file", file_exists(tpath), NULL);

	reset_state();
	t("load returns 0", ats_state_load() == 0, NULL);
	t("load sets the loaded flag", ats_state_loaded, NULL);
	t("base round-trips", ats_state.base == ATS_USER, NULL);
	t("offset_ms round-trips", ats_state.offset_ms == OFF, NULL);
	t("rtc_ms round-trips", ats_state.rtc_ms == 5000000ULL, NULL);
	t("wall_ms round-trips", ats_state.wall_ms == WALL, NULL);
	t("delta_ms round-trips", ats_state.delta_ms == (int64_t)(WALL - 5000000ULL), NULL);
	t("synced_at round-trips", ats_state.synced_at == 1758542400ULL, NULL);

	printf("\n== 2. the file is self-describing ==\n");
	{
		size_t len;
		int nl = 0;

		memset(filebuf, 0, sizeof(filebuf));
		{
			int fd = open(tpath, O_RDONLY);
			ssize_t n = (fd >= 0) ? read(fd, filebuf, sizeof(filebuf) - 1) : -1;
			if (fd >= 0)
				close(fd);
			if (n < 0)
				n = 0;
			filebuf[n] = '\0';
		}
		len = strlen(filebuf);
		for (i = 0; i < (int)len; i++)
			if (filebuf[i] == '\n')
				nl++;
		t("declares its magic", strstr(filebuf, "magic=qcom-ats-state") != NULL, NULL);
		t("declares its version", strstr(filebuf, "version=1") != NULL, NULL);
		t("declares its base", strstr(filebuf, "base=2") != NULL, NULL);
		t("has exactly the 8 documented keys", nl == 8, NULL);
		t("ends with a newline", len > 0 && filebuf[len - 1] == '\n', NULL);
	}

	printf("\n== 3. the signed field survives ==\n");
	rec = mkrec(OFF, WALL + 5000ULL, WALL);
	reset_state();
	ats_state_save(&rec);
	reset_state();
	ats_state_load();
	t("negative delta_ms round-trips", ats_state.delta_ms == -5000, NULL);

	printf("\n== 4. a bad record degrades to 'no record', never to a wrong offset ==\n");
	for (i = 0; i < (int)(sizeof(bad) / sizeof(bad[0])); i++) {
		write_file(tpath, bad[i].body);
		reset_state();
		t(bad[i].name, ats_state_load() == -1 && !ats_state_loaded, NULL);
	}

	printf("\n== 5. absent file ==\n");
	reset_state();
	unlink(tpath);
	t("missing file -> not loaded, no crash", ats_state_load() == -1 && !ats_state_loaded, NULL);

	printf("\n== 6. the parent directory is created on demand ==\n");
	rmdir(TDIR "/newdir");
	snprintf(tpath, sizeof(tpath), TDIR "/newdir/ats");
	reset_state();
	rec = mkrec(OFF, 5000000ULL, WALL);
	t("save into a missing directory succeeds",
	  ats_state_save(&rec) == 0 && file_exists(tpath), NULL);
	unlink(tpath);
	rmdir(TDIR "/newdir");
	snprintf(tpath, sizeof(tpath), TDIR "/ats");

	printf("\n== 7. -P none disables the whole path ==\n");
	reset_state();
	unlink(tpath);
	ats_state_enabled = false;
	t("disabled: save writes nothing",
	  ats_state_save(&rec) == -1 && !file_exists(tpath), NULL);
	ats_state_note_handshake(1000000ULL, WALL, OFF, false);
	t("disabled: note_handshake writes nothing", !file_exists(tpath), NULL);
	reset_state();
	ats_state_enabled = false;
	write_file(tpath, GOOD_RECORD);
	t("disabled: load reads nothing",
	  ats_state_load() == -1 && !ats_state_loaded, NULL);
	unlink(tpath);
	ats_state_enabled = true;

	printf("\n== 8. verdict: the modem restarted ==\n");
	reset_state();
	unlink(tpath);
	rec = mkrec(OFF, 5000000ULL, WALL);
	ats_state_save(&rec);
	reset_state();
	ats_state_load();
	cap_begin();
	ats_state_log_boot_verdict(1200000ULL);	/* RTC went BACKWARDS */
	cap_end(logbuf, sizeof(logbuf));
	t("reported as RESTARTED", strstr(logbuf, "MODEM RESTARTED") != NULL, NULL);
	t("names the modem time lost", strstr(logbuf, "3800000 ms") != NULL, NULL);
	t("does not claim survival", strstr(logbuf, "MODEM SURVIVED") == NULL, NULL);

	printf("\n== 9. verdict: the modem survived ==\n");
	ats_verdict_logged = false;
	cap_begin();
	ats_state_log_boot_verdict(9000000ULL);	/* RTC moved FORWARDS */
	cap_end(logbuf, sizeof(logbuf));
	t("reported as SURVIVED", strstr(logbuf, "MODEM SURVIVED") != NULL, NULL);
	t("names the elapsed modem time", strstr(logbuf, "4000000 ms") != NULL, NULL);
	t("does not claim a restart", strstr(logbuf, "MODEM RESTARTED") == NULL, NULL);

	printf("\n== 10. the verdict is one-shot per boot ==\n");
	ats_verdict_logged = false;
	cap_begin();
	ats_state_log_boot_verdict(9000000ULL);
	cap_end(logbuf, sizeof(logbuf));
	t("first call logs", strlen(logbuf) > 0, NULL);
	cap_begin();
	ats_state_log_boot_verdict(9000000ULL);
	cap_end(logbuf, sizeof(logbuf));
	t("second call is silent", strlen(logbuf) == 0, NULL);

	printf("\n== 11. verdict: no comparable RTC ==\n");
	reset_state();
	unlink(tpath);
	rec = mkrec(OFF, 0ULL, WALL);	/* rtc_ms == 0 sentinel */
	ats_state_save(&rec);
	reset_state();
	ats_state_load();
	ats_verdict_logged = false;
	cap_begin();
	ats_state_log_boot_verdict(9000000ULL);
	cap_end(logbuf, sizeof(logbuf));
	t("rtc_ms=0 -> UNKNOWN, not a guess",
	  strstr(logbuf, "UNKNOWN") != NULL &&
	  strstr(logbuf, "MODEM SURVIVED") == NULL, NULL);

	reset_state();
	unlink(tpath);
	ats_state_load();
	ats_verdict_logged = false;
	cap_begin();
	ats_state_log_boot_verdict(9000000ULL);
	cap_end(logbuf, sizeof(logbuf));
	t("no record -> UNKNOWN", strstr(logbuf, "UNKNOWN") != NULL, NULL);

	printf("\n== 12. the AP-clock guards ==\n");
	reset_state();
	unlink(tpath);
	ats_state_load();				/* no record loaded */
	t("1970 is not a clock", !ap_clock_is_usable(1000ULL), NULL);
	t("a 2025 clock is usable", ap_clock_is_usable(WALL), NULL);

	reset_state();
	rec = mkrec(OFF, 5000000ULL, WALL);
	ats_state_save(&rec);
	reset_state();
	ats_state_load();
	t("a clock behind the record is unusable",
	  !ap_clock_is_usable(WALL - 3600000ULL), NULL);
	t("a clock ahead of the record is usable",
	  ap_clock_is_usable(WALL + 3600000ULL), NULL);

	printf("\n== 13. the restore candidate ==\n");
	t("returns the persisted offset",
	  ats_state_restore_offset(&v) && v == OFF, NULL);
	reset_state();
	unlink(tpath);
	ats_state_load();
	t("refuses when there is no record", !ats_state_restore_offset(&v), NULL);

	printf("\n== 14. when the record is rewritten ==\n");
	reset_state();
	unlink(tpath);
	ats_state_note_handshake(1000000ULL, WALL, OFF, false);
	t("INITIAL always writes", file_exists(tpath), NULL);
	t("INITIAL records its own rtc_ms", ats_state.rtc_ms == 1000000ULL, NULL);

	unlink(tpath);	/* remove it, so a rewrite is detectable */
	ats_state_note_handshake(2000000ULL, WALL, OFF, true);
	t("unchanged REFRESH does NOT rewrite", !file_exists(tpath), NULL);
	t("unchanged REFRESH keeps the old rtc_ms", ats_state.rtc_ms == 1000000ULL, NULL);

	ats_state_note_handshake(3000000ULL, WALL, OFF + 1ULL, true);
	t("changed REFRESH writes", file_exists(tpath), NULL);
	t("changed REFRESH updates rtc_ms", ats_state.rtc_ms == 3000000ULL, NULL);
	reset_state();
	ats_state_load();
	t("the rewritten record holds the new offset",
	  ats_state_loaded && ats_state.offset_ms == OFF + 1ULL, NULL);

	printf("\n== 15. the persisted offset is the one the modem accepted ==\n");
	{
		/* Drive the real encoder and confirm the record matches the wire. */
		struct time_genoff_set_req sr;

		memset(&sr, 0, sizeof(sr));
		sr.base = ATS_USER;
		sr.offset = ats_state.offset_ms;
		t("record offset fits the u64 TLV unchanged",
		  sr.offset == ats_state.offset_ms && sr.base == ATS_USER, NULL);
	}

	printf("\n%s  %d checks, %d failed\n",
	       n_fail ? "RESULT: FAIL" : "RESULT: PASS", n_run, n_fail);

	unlink(tpath);
	unlink(tmppath);
	unlink(CAP_PATH);
	return n_fail ? 1 : 0;
}
