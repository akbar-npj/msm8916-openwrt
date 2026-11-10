/* hostless: keep DSP "hostless" PCM devices open and running without moving
 * any data. On Qualcomm QDSP6 sound cards this starts the session behind the
 * front end (e.g. the CS-Voice FE starts the DSP voice call session); the
 * mixer routes decide where the audio goes. Same idea as AOSP tinyhostless,
 * written against alsa-lib.
 *
 *   hostless -D hw:0,2 [-P] [-C] [-r 8000] [-c 1] [-p 512] [-n 2]
 *
 * -P / -C open the playback / capture direction (default: both).
 * Runs until SIGINT/SIGTERM or until the sound card goes away.
 *
 * Build: cc -O2 -o hostless hostless.c -lasound
 */
#include <alsa/asoundlib.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

static volatile sig_atomic_t stop;

static void on_signal(int sig) { (void)sig; stop = 1; }

static snd_pcm_t *open_hostless(const char *dev, snd_pcm_stream_t dir,
                                unsigned rate, unsigned channels,
                                snd_pcm_uframes_t period, unsigned periods)
{
    snd_pcm_t *pcm;
    snd_pcm_hw_params_t *hw;
    snd_pcm_sw_params_t *sw;
    snd_pcm_uframes_t boundary;
    const char *name = dir == SND_PCM_STREAM_PLAYBACK ? "playback" : "capture";
    int err;

    if ((err = snd_pcm_open(&pcm, dev, dir, 0)) < 0) {
        fprintf(stderr, "%s %s: %s\n", dev, name, snd_strerror(err));
        return NULL;
    }
    snd_pcm_hw_params_alloca(&hw);
    snd_pcm_hw_params_any(pcm, hw);
    if ((err = snd_pcm_hw_params_set_access(pcm, hw, SND_PCM_ACCESS_RW_INTERLEAVED)) < 0 ||
        (err = snd_pcm_hw_params_set_format(pcm, hw, SND_PCM_FORMAT_S16_LE)) < 0 ||
        (err = snd_pcm_hw_params_set_channels(pcm, hw, channels)) < 0 ||
        (err = snd_pcm_hw_params_set_rate(pcm, hw, rate, 0)) < 0 ||
        (err = snd_pcm_hw_params_set_period_size_near(pcm, hw, &period, NULL)) < 0 ||
        (err = snd_pcm_hw_params_set_periods_near(pcm, hw, &periods, NULL)) < 0 ||
        (err = snd_pcm_hw_params(pcm, hw)) < 0) {
        fprintf(stderr, "%s %s: hw params: %s\n", dev, name, snd_strerror(err));
        snd_pcm_close(pcm);
        return NULL;
    }
    /* No data will flow: never stop the stream on an xrun. */
    snd_pcm_sw_params_alloca(&sw);
    snd_pcm_sw_params_current(pcm, sw);
    snd_pcm_sw_params_get_boundary(sw, &boundary);
    snd_pcm_sw_params_set_stop_threshold(pcm, sw, boundary);
    snd_pcm_sw_params_set_start_threshold(pcm, sw, 1);
    snd_pcm_sw_params(pcm, sw);

    if ((err = snd_pcm_prepare(pcm)) < 0 || (err = snd_pcm_start(pcm)) < 0) {
        fprintf(stderr, "%s %s: start: %s\n", dev, name, snd_strerror(err));
        snd_pcm_close(pcm);
        return NULL;
    }
    return pcm;
}

static int gone(snd_pcm_t *pcm)
{
    int st = snd_pcm_state(pcm);

    return st < 0 || st == SND_PCM_STATE_DISCONNECTED;
}

int main(int argc, char **argv)
{
    const char *dev = "hw:0,2";
    unsigned rate = 8000, channels = 1, periods = 2;
    snd_pcm_uframes_t period = 512;
    int play = 0, cap = 0, opt;
    snd_pcm_t *p = NULL, *c = NULL;

    while ((opt = getopt(argc, argv, "D:PCr:c:p:n:")) != -1) {
        switch (opt) {
        case 'D': dev = optarg; break;
        case 'P': play = 1; break;
        case 'C': cap = 1; break;
        case 'r': rate = atoi(optarg); break;
        case 'c': channels = atoi(optarg); break;
        case 'p': period = atoi(optarg); break;
        case 'n': periods = atoi(optarg); break;
        default:
            fprintf(stderr, "usage: %s -D hw:CARD,DEV [-P] [-C] [-r rate] [-c ch] [-p period] [-n periods]\n", argv[0]);
            return 2;
        }
    }
    if (!play && !cap)
        play = cap = 1;

    signal(SIGINT, on_signal);
    signal(SIGTERM, on_signal);

    if (play && !(p = open_hostless(dev, SND_PCM_STREAM_PLAYBACK, rate, channels, period, periods)))
        return 1;
    if (cap && !(c = open_hostless(dev, SND_PCM_STREAM_CAPTURE, rate, channels, period, periods))) {
        if (p)
            snd_pcm_close(p);
        return 1;
    }
    /* Exit when the card goes away (DSP crash): an open PCM would keep the
     * sound card, and with it the modem's restart, from being torn down. */
    while (!stop) {
        sleep(1);
        if ((p && gone(p)) || (c && gone(c))) {
            fprintf(stderr, "%s: disconnected\n", dev);
            break;
        }
    }

    if (c)
        snd_pcm_drop(c), snd_pcm_close(c);
    if (p)
        snd_pcm_drop(p), snd_pcm_close(p);
    return 0;
}
