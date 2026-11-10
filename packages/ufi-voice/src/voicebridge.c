/* voicebridge: low-latency audio between the DSP's in-call PCMs and a pipe.
 *
 *   voicebridge -D hw:CARD [-c 0] [-p 1] [-r 48000] [-j 40] [-v]
 *
 * stdout: the call downlink, captured on hw:CARD,<c> (in-call record).
 * stdin:  the call uplink, played on hw:CARD,<p> (in-call playback).
 * Both sides of the pipe are 8 kHz S16 mono (Asterisk AudioSocket slin).
 *
 * The PCMs run at -r (48 kHz by default: the q6asm FEs need at least 480
 * frames per period, which is 60 ms at 8 kHz but 10 ms at 48 kHz) and the
 * audio is resampled with a polyphase FIR. The playback buffer is kept full
 * at all times: audio from stdin goes through a small jitter buffer (-j ms)
 * and silence fills the gaps, so the PCM never underruns and the delay
 * cannot grow; a backlog beyond the jitter buffer is dropped.
 *
 * Exits on SIGTERM/SIGINT, when stdin is closed, or when the capture keeps
 * failing (the call's voice session is gone).
 *
 * Build: cc -O2 -o voicebridge voicebridge.c -lasound -lm
 */
#include <alsa/asoundlib.h>
#include <errno.h>
#include <fcntl.h>
#include <math.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

#define PIPE_RATE 8000
#define TAPS_PER_PHASE 16
#define FIFO_LEN PIPE_RATE      /* 1 s of uplink audio */
#define MAX_PERIOD 4096

static volatile sig_atomic_t stop;
static int verbose;

static void on_signal(int sig) { (void)sig; stop = 1; }

static double now(void)
{
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec + ts.tv_nsec / 1e9;
}

/* --- resampling ---------------------------------------------------------- */

static int L = 6;               /* PCM rate / PIPE_RATE */
static int ntaps;               /* L * TAPS_PER_PHASE */
static float *h;                /* low-pass at the 8 kHz Nyquist, PCM rate */

static void design_filter(void)
{
    int i;
    double fc = 0.45 / L;       /* 3.6 kHz at any rate */

    ntaps = L * TAPS_PER_PHASE;
    h = calloc(ntaps, sizeof(*h));
    for (i = 0; i < ntaps; i++) {
        double m = i - (ntaps - 1) / 2.0;
        double sinc = m == 0 ? 2 * fc : sin(2 * M_PI * fc * m) / (M_PI * m);
        double w = 0.42 - 0.5 * cos(2 * M_PI * i / (ntaps - 1))
                   + 0.08 * cos(4 * M_PI * i / (ntaps - 1));   /* Blackman */
        h[i] = sinc * w;
    }
}

static short clip(float v)
{
    return v > 32767 ? 32767 : v < -32768 ? -32768 : (short)lrintf(v);
}

/* 8 kHz -> PCM rate. hist holds the last TAPS_PER_PHASE input samples. */
static void upsample(const short *in, int n, short *out)
{
    static float hist[TAPS_PER_PHASE];
    int i, p, k;

    for (i = 0; i < n; i++) {
        memmove(hist + 1, hist, (TAPS_PER_PHASE - 1) * sizeof(*hist));
        hist[0] = in[i];
        for (p = 0; p < L; p++) {
            float acc = 0;
            for (k = 0; k < TAPS_PER_PHASE; k++)
                acc += h[p + k * L] * hist[k];
            out[i * L + p] = clip(acc * L);
        }
    }
}

/* PCM rate -> 8 kHz; returns the number of output samples. */
static int downsample(const short *in, int n, short *out)
{
    static float hist[MAX_PERIOD * 8];
    static int fill, phase;
    int i, k, o = 0;

    for (i = 0; i < n; i++) {
        hist[fill++] = in[i];
        if (++phase < L)
            continue;
        phase = 0;
        if (fill >= ntaps) {
            float acc = 0;
            for (k = 0; k < ntaps; k++)
                acc += h[k] * hist[fill - 1 - k];
            out[o++] = clip(acc);
        }
        if (fill > (int)(sizeof(hist) / sizeof(*hist)) - L) {
            memmove(hist, hist + fill - ntaps, ntaps * sizeof(*hist));
            fill = ntaps;
        }
    }
    return o;
}

/* --- PCM setup ----------------------------------------------------------- */

static snd_pcm_t *open_pcm(const char *dev, snd_pcm_stream_t dir, unsigned rate,
                           snd_pcm_uframes_t *period, unsigned periods)
{
    snd_pcm_t *pcm;
    snd_pcm_hw_params_t *hw;
    snd_pcm_sw_params_t *sw;
    snd_pcm_uframes_t buffer;
    const char *name = dir == SND_PCM_STREAM_PLAYBACK ? "playback" : "capture";
    int err;

    if ((err = snd_pcm_open(&pcm, dev, dir, SND_PCM_NONBLOCK)) < 0) {
        fprintf(stderr, "voicebridge: %s %s: %s\n", dev, name, snd_strerror(err));
        return NULL;
    }
    snd_pcm_hw_params_alloca(&hw);
    snd_pcm_hw_params_any(pcm, hw);
    if ((err = snd_pcm_hw_params_set_access(pcm, hw, SND_PCM_ACCESS_RW_INTERLEAVED)) < 0 ||
        (err = snd_pcm_hw_params_set_format(pcm, hw, SND_PCM_FORMAT_S16_LE)) < 0 ||
        (err = snd_pcm_hw_params_set_channels(pcm, hw, 1)) < 0 ||
        (err = snd_pcm_hw_params_set_rate(pcm, hw, rate, 0)) < 0 ||
        (err = snd_pcm_hw_params_set_period_size_near(pcm, hw, period, NULL)) < 0 ||
        (err = snd_pcm_hw_params_set_periods_near(pcm, hw, &periods, NULL)) < 0 ||
        (err = snd_pcm_hw_params(pcm, hw)) < 0) {
        fprintf(stderr, "voicebridge: %s %s: hw params: %s\n", dev, name, snd_strerror(err));
        snd_pcm_close(pcm);
        return NULL;
    }
    snd_pcm_hw_params_get_buffer_size(hw, &buffer);
    if (*period > MAX_PERIOD) {
        fprintf(stderr, "voicebridge: %s %s: period %lu too large\n", dev, name, *period);
        snd_pcm_close(pcm);
        return NULL;
    }
    snd_pcm_sw_params_alloca(&sw);
    snd_pcm_sw_params_current(pcm, sw);
    snd_pcm_sw_params_set_avail_min(pcm, sw, *period);
    snd_pcm_sw_params_set_start_threshold(pcm, sw,
        dir == SND_PCM_STREAM_PLAYBACK ? buffer : 1);
    snd_pcm_sw_params(pcm, sw);
    fprintf(stderr, "voicebridge: %s %s: %u Hz, period %lu, buffer %lu (%.0f ms)\n",
            dev, name, rate, *period, buffer, buffer * 1000.0 / rate);
    return pcm;
}

/* --- uplink jitter buffer (8 kHz) ---------------------------------------- */

static short fifo[FIFO_LEN];
static int fifo_r, fifo_n;

static void fifo_put(const short *s, int n)
{
    int i;
    for (i = 0; i < n; i++) {
        if (fifo_n == FIFO_LEN) {       /* full: drop the oldest */
            fifo_r = (fifo_r + 1) % FIFO_LEN;
            fifo_n--;
        }
        fifo[(fifo_r + fifo_n++) % FIFO_LEN] = s[i];
    }
}

static int fifo_get(short *s, int n)
{
    int i;
    if (n > fifo_n)
        n = fifo_n;
    for (i = 0; i < n; i++)
        s[i] = fifo[(fifo_r + i) % FIFO_LEN];
    fifo_r = (fifo_r + n) % FIFO_LEN;
    fifo_n -= n;
    return n;
}

static void fifo_drop(int n)
{
    if (n > fifo_n)
        n = fifo_n;
    fifo_r = (fifo_r + n) % FIFO_LEN;
    fifo_n -= n;
}

/* --- main loop ----------------------------------------------------------- */

struct stats {
    long cap_frames, play_frames, in_samples, out_samples;
    long gaps, dropped, cap_xruns, play_xruns, out_drops;
};

static int xrun_recover(snd_pcm_t *pcm, int err)
{
    if (err == -EPIPE || err == -ESTRPIPE)
        return snd_pcm_prepare(pcm);
    return err;
}

int main(int argc, char **argv)
{
    const char *card = "hw:0";
    int capdev = 0, playdev = 1, jitter_ms = 40, opt;
    unsigned rate = 48000;
    char dev[64];
    snd_pcm_t *cap, *play;
    snd_pcm_uframes_t cperiod, pperiod;
    int ncfd, npfd, nfds, buffering = 1, cap_fail = 0, jitter, maxfill;
    struct pollfd *fds;
    short pcmbuf[MAX_PERIOD], pipebuf[MAX_PERIOD], inbuf[1024];
    struct stats st = {0};
    double t_stats;

    while ((opt = getopt(argc, argv, "D:c:p:r:j:v")) != -1) {
        switch (opt) {
        case 'D': card = optarg; break;
        case 'c': capdev = atoi(optarg); break;
        case 'p': playdev = atoi(optarg); break;
        case 'r': rate = atoi(optarg); break;
        case 'j': jitter_ms = atoi(optarg); break;
        case 'v': verbose = 1; break;
        default:
            fprintf(stderr, "usage: %s -D hw:CARD [-c capdev] [-p playdev] [-r rate] [-j jitter_ms] [-v]\n", argv[0]);
            return 2;
        }
    }
    if (rate % PIPE_RATE || rate / PIPE_RATE > 8) {
        fprintf(stderr, "voicebridge: rate must be 8000, 16000, ... 64000\n");
        return 2;
    }
    L = rate / PIPE_RATE;
    jitter = jitter_ms * PIPE_RATE / 1000;
    maxfill = jitter + 3 * PIPE_RATE / 50;  /* +60 ms, then drop */
    design_filter();

    signal(SIGINT, on_signal);
    signal(SIGTERM, on_signal);
    signal(SIGPIPE, SIG_IGN);
    fcntl(0, F_SETFL, fcntl(0, F_GETFL) | O_NONBLOCK);
    fcntl(1, F_SETFL, fcntl(1, F_GETFL) | O_NONBLOCK);

    /* 10 ms periods where the FE allows it (480 frames at 48 kHz) */
    cperiod = pperiod = rate / 100 < 480 ? 480 : rate / 100;
    snprintf(dev, sizeof(dev), "%s,%d", card, capdev);
    if (!(cap = open_pcm(dev, SND_PCM_STREAM_CAPTURE, rate, &cperiod, 4)))
        return 1;
    snprintf(dev, sizeof(dev), "%s,%d", card, playdev);
    if (!(play = open_pcm(dev, SND_PCM_STREAM_PLAYBACK, rate, &pperiod, 3))) {
        snd_pcm_close(cap);
        return 1;
    }
    if (pperiod % L) {
        fprintf(stderr, "voicebridge: playback period %lu not a multiple of %d\n", pperiod, L);
        return 1;
    }
    snd_pcm_start(cap);

    ncfd = snd_pcm_poll_descriptors_count(cap);
    npfd = snd_pcm_poll_descriptors_count(play);
    nfds = 1 + ncfd + npfd;
    fds = calloc(nfds, sizeof(*fds));
    fds[0].fd = 0;
    fds[0].events = POLLIN;
    snd_pcm_poll_descriptors(cap, fds + 1, ncfd);
    snd_pcm_poll_descriptors(play, fds + 1 + ncfd, npfd);
    t_stats = now();

    while (!stop) {
        unsigned short rev;
        snd_pcm_sframes_t n;

        if (poll(fds, nfds, 1000) < 0 && errno != EINTR)
            break;

        /* uplink: stdin -> jitter buffer */
        if (fds[0].revents & (POLLIN | POLLHUP)) {
            ssize_t r = read(0, inbuf, sizeof(inbuf));
            if (r == 0)
                break;              /* the caller went away */
            if (r > 0) {
                fifo_put(inbuf, r / 2);
                st.in_samples += r / 2;
            }
        }

        /* downlink: capture -> stdout */
        snd_pcm_poll_descriptors_revents(cap, fds + 1, ncfd, &rev);
        if (rev & (POLLIN | POLLERR)) {
            while ((n = snd_pcm_readi(cap, pcmbuf, cperiod)) > 0) {
                int o = downsample(pcmbuf, n, pipebuf);
                st.cap_frames += n;
                cap_fail = 0;
                if (o > 0) {
                    if (write(1, pipebuf, o * 2) < 0)
                        st.out_drops += o;
                    else
                        st.out_samples += o;
                }
            }
            if (n == -EPIPE || n == -ESTRPIPE) {
                st.cap_xruns++;
                xrun_recover(cap, n);
                snd_pcm_start(cap);
            } else if (n < 0 && n != -EAGAIN) {
                if (++cap_fail > 100) {
                    fprintf(stderr, "voicebridge: capture: %s\n", snd_strerror(n));
                    break;
                }
                xrun_recover(cap, -EPIPE);
                snd_pcm_start(cap);
            }
        }

        /* uplink: jitter buffer -> playback, one period whenever there is
         * room; silence while the jitter buffer refills */
        snd_pcm_poll_descriptors_revents(play, fds + 1 + ncfd, npfd, &rev);
        if (rev & (POLLOUT | POLLERR)) {
            for (;;) {
                snd_pcm_sframes_t avail = snd_pcm_avail_update(play);
                int want = pperiod / L, got = 0;

                if (avail < 0) {
                    st.play_xruns++;
                    if (xrun_recover(play, avail) < 0)
                        break;
                    continue;
                }
                if (avail < (snd_pcm_sframes_t)pperiod)
                    break;
                if (fifo_n > maxfill) {
                    st.dropped += fifo_n - jitter;
                    fifo_drop(fifo_n - jitter);
                }
                if (buffering && fifo_n >= jitter)
                    buffering = 0;
                if (!buffering)
                    got = fifo_get(pipebuf, want);
                if (got < want) {
                    memset(pipebuf + got, 0, (want - got) * 2);
                    if (!buffering) {
                        st.gaps++;
                        buffering = 1;
                    }
                }
                upsample(pipebuf, want, pcmbuf);
                n = snd_pcm_writei(play, pcmbuf, pperiod);
                if (n < 0) {
                    st.play_xruns++;
                    if (xrun_recover(play, n) < 0)
                        break;
                } else {
                    st.play_frames += n;
                }
            }
        }

        if (now() - t_stats >= (verbose ? 5 : 60)) {
            double dt = now() - t_stats;
            if (verbose || st.gaps || st.dropped || st.cap_xruns || st.play_xruns)
                fprintf(stderr, "voicebridge: %.0fs cap %.0f/s play %.0f/s in %.0f/s out %.0f/s"
                        " jbuf %d ms gaps %ld dropped %ld ms xruns %ld/%ld outdrop %ld\n",
                        dt, st.cap_frames / dt, st.play_frames / dt, st.in_samples / dt,
                        st.out_samples / dt, fifo_n * 1000 / PIPE_RATE, st.gaps,
                        st.dropped * 1000 / PIPE_RATE, st.cap_xruns, st.play_xruns,
                        st.out_drops);
            memset(&st, 0, sizeof(st));
            t_stats = now();
        }
    }

    snd_pcm_drop(cap);
    snd_pcm_close(cap);
    snd_pcm_drop(play);
    snd_pcm_close(play);
    return 0;
}
