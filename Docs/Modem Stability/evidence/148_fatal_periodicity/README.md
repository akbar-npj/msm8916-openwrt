# Evidence — Doc 148: fatal periodicity and signature taxonomy

Raw captures backing `../../148_HMU05_FATAL_PERIODICITY_AND_SIGNATURE_TAXONOMY.md`.
Captured from the deployed HMU05 on 2026-09-20 (boot 17:38 UTC, kernel 6.12.94).

| file | what it is |
| :--- | :--- |
| `live_capture_20260920.txt` | `dmesg` fatal + SSR sequence, RX-watchdog quiesce log, full `rx_telemetry`, module/kernel info, deployed watchdog md5 |
| `a2_cadence_20260920.txt` | 120 s `rx_telemetry` sampling (A2 power-collapse cadence), the interpretation, the collapse-duration trend, and the live re-confirmation of the first-packet loss |
| `fatal_signature_census.txt` | every `fatal error received: <file>:<line>` string found anywhere in this repo, with counts, plus the datable instances |

## Headline numbers

* `lte_ml1_common_timer.c:390` at AP uptime **914.769287 / 1818.443149 / 2722.117987 s**
  → intervals **903.673862 / 903.674838 s** (1.08 ppm apart), 3/3.
* Each fatal → `remoteproc0: stopped remote processor` → SSR powerup, modem back in ~1.3 s.
* AP-side oopses this boot: **0**.
* `pc_resync_count: 0`; `pc_irq_count == pc_ack_irq_count` (400 == 400) — no lost PC edge.
* `pc_timeout_count: 2`; `pm_suspend_attempts: 202` vs `pm_suspend_completions: 199`.
* 10 distinct fatal signatures in the corpus.

## Reproducing the capture

```sh
T=/sys/devices/platform/soc@0/4080000.remoteproc/4080000.remoteproc:bam-dmux/rx_telemetry
ssh root@192.168.8.1 "dmesg | grep -E 'fatal error received|remoteproc0|RX watchdog'"
ssh root@192.168.8.1 "cat $T"
```

The telemetry file exists only when `msm89xx/patches/808-bam-dmux-stats.patch` is in the
deployed module — its presence is itself a build fingerprint.
