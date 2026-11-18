/* SPDX-License-Identifier: BSD-3-Clause */
#include <alsa/asoundlib.h>
#include <stdio.h>

#include <unistd.h>

static int prepare(snd_pcm_t **pcm, const char *name, snd_pcm_stream_t stream)
{
    snd_pcm_hw_params_t *params;
    snd_pcm_uframes_t period = 1024;
    snd_pcm_uframes_t buffer = 2048;
    int err;

    snd_pcm_hw_params_alloca(&params);
    if ((err = snd_pcm_open(pcm, name, stream, SND_PCM_NONBLOCK)) < 0 ||
        (err = snd_pcm_hw_params_any(*pcm, params)) < 0 ||
        (err = snd_pcm_hw_params_set_access(*pcm, params, SND_PCM_ACCESS_RW_INTERLEAVED)) < 0 ||
        (err = snd_pcm_hw_params_set_format(*pcm, params, SND_PCM_FORMAT_S16_LE)) < 0 ||
        (err = snd_pcm_hw_params_set_channels(*pcm, params, 1)) < 0 ||
        (err = snd_pcm_hw_params_set_rate(*pcm, params, 8000, 0)) < 0 ||
        (err = snd_pcm_hw_params_set_period_size_near(*pcm, params, &period, NULL)) < 0 ||
        (err = snd_pcm_hw_params_set_buffer_size_near(*pcm, params, &buffer)) < 0 ||
        (err = snd_pcm_hw_params(*pcm, params)) < 0 ||
        (err = snd_pcm_prepare(*pcm)) < 0) {
        fprintf(stderr, "%s: %s\n", stream == SND_PCM_STREAM_CAPTURE ? "capture" : "playback",
                snd_strerror(err));
        return 1;
    }
    return 0;
}

int main(int argc, char **argv)
{
    snd_pcm_t *capture;
    snd_pcm_t *playback;
    if (argc != 2)
        return 2;
    if (prepare(&capture, argv[1], SND_PCM_STREAM_CAPTURE) ||
        prepare(&playback, argv[1], SND_PCM_STREAM_PLAYBACK))
        return 1;
    puts("Hostless voice PCM prepared in both directions");
    fflush(stdout);
    for (;;)
        pause();
}
