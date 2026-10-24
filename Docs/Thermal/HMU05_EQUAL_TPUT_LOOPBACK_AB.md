# Equal-throughput loopback A/B — OpenWrt vs Android (2026-10-24)

Status: EXPERIMENT COMPLETE. Companion to `HMU05_LIVE_BUS_VOTING_AB.md` (bus-voting closure)
and `HMU05_DEFINITIVE_THERMAL_FINDINGS.md`. This is the controlled-load experiment that the
bus-voting doc planned; the load generator is fixed and the rates are matched.

## Question

Does OpenWrt run hotter than Android at EQUAL throughput? Prior evidence: real-LTE fetch at
400 KB/s was thermally identical on both; bus voting is OS-independent (shared RPM silicon).
This experiment adds a matched-rate, measured-throughput comparison with an idle baseline and
CPU-utilization accounting.

## Why loopback, not LTE

Real-LTE high-rate A/B was not runnable: OpenWrt busybox wget resolves AAAA-only for common
speed-test hosts and its firewall REJECTs the v6 send (`Failed to send request: Operation not
permitted`; `uclient-fetch -4` to speedtest.tele2.net hung); Android has LTE L3 up (ping 8.8.8.8
OK) but a broken resolver (`nslookup` answers 127.0.0.1; `curl --resolve` with the literal IP
also stalled). Bulk LTE pulls would also have burned SIM quota. Loopback keeps everything
device-local, needs no external host, and the throttle lives in the CLIENT so the rate is exact.
Caveat: loopback exercises the shell/HTTP path, not the modem data path — see Caveats.

## Method

Per device, one static 4 MB file served by the device's own HTTP server, fetched by K parallel
client loops over 127.0.0.1; each iteration pulls 4 MB (busybox `dd bs=65536 count=64` — a single
`dd bs=4M` read returns only the pipe buffer, ~64 KB) then sleeps `usleep USLEEP`. A logger
samples every 5 s: all thermal zones, all `scaling_cur_freq`, and the first five `/proc/stat`
ticks (user nice sys idle iowait).

- OpenWrt server: the already-running `uhttpd` (`/www/bigfile` on port 80)
- Android server: busybox `httpd -p 8080 -h /data/local/tmp` (Android has no `/tmp`; its busybox
  `nc -l` was rejected: accept-gap races gave connection-refused under concurrency)
- Calibration: K=4 with the same 400 ms usleep gave OWRT 10.2 MB/s vs AND 16.0 MB/s. Rates were
  matched by slowing Android (`usleep 800000`): final match 10.78 vs 11.07 MB/s (2.7% apart).
- Protocol per device: idle 30 s (K=0), then load 60 s (K=4). Same physical-class units
  (HMU05 family); absolute baselines differ between units, so RISE at matched load is the metric.
- Traps fixed on the way: `pkill -f "httpd -p 8080"` matches its own remote shell's cmdline and
  kills the ssh session (use `pkill -x`); a backgrounded listener holds the ssh pipe fd open
  unless stdout/stderr/stdin are all redirected; the host-side Bash tool cannot background a
  long-lived server (every such call hung) — all servers live on the devices.

## Results (60 s load phase)

| Metric | OpenWrt (hmuf02v5) | Android |
|---|---|---|
| Throughput | 10.78 MB/s (646.7 MB) | 11.07 MB/s (664.2 MB) |
| CPU busy (proc/stat, 4 cores, 100 Hz) | ~36% | ~24% |
| pm8916 (zone5) idle baseline | 55.4 C | 43.6 C |
| pm8916 rise under load | +3.2 C (55.6 -> 58.9, still climbing) | +1.9 C (44.2 -> 46.1) |
| CPU freq under load | 800 MHz x4 | 800 MHz x4 |
| CPU freq at idle | mostly 800 MHz (schedutil, occasional dips) | pinned 800 MHz |
| modem zone at idle | 63-64 C (zone0 modem-thermal) | 52-53 C (tsens sensor0) |

Scales: OpenWrt zones are millidegrees. Android tsens zones 0-4 are centigrade-as-integer
(verified: they rose 48->60 under a CPU stress while pm8916_tz moved millidegrees);
`pm8916`/`pm8916_tz` (zone5) is millidegrees on BOTH and is the apples-to-apples sensor.
Android zone6 = battery (33 C, inert).

## Analysis

1. At matched throughput OpenWrt burns ~1.5x the CPU (36% vs 24% busy) and the board heats
   ~1.7x more (+3.2 vs +1.9 C pm8916 rise). Heat tracks CPU work per byte, not the OS per se.
2. Frequency is NOT the differentiator under load: both pin all four cores at 800 MHz.
3. Combined with the real-LTE 400 KB/s parity result (no rise on either) and the bus-voting
   closure (shared RPM silicon, votes flow live on both), the thermal gap is EXPLAINED BY
   CPU-work-per-byte differences, i.e. a load/efficiency confound — not a bus, core-count,
   or frequency-scaling defect.
4. Unit-to-unit baseline offset (55.4 vs 43.6 C idle pm8916) is why absolute temps must never
   be compared cross-device; only rises at matched load are valid. The OpenWrt unit also idles
   with its modem zone at 63-64 C, which pre-heats the whole board before any AP-side load —
   the modem-side thermal state dominates the board's starting point.

## Caveats

- The harness itself is asymmetric: uhttpd (OpenWrt) is a heavier per-request server than
  busybox httpd (Android), and busybox wget/uclient process spawn cost differs. Part of the
  measured 1.5x CPU penalty is harness, not the LTE data path (which has no HTTP server at all).
  Directionally, though, more CPU work per byte = more heat, on both OSes.
- 60 s is shorter than the board's thermal time constant; both rises were still climbing at
  cutoff. The RATIO, not the absolute rise, is the finding.
- Single trial per config. The calibration run (20 s) reproduced the same ordering
  (+1.9 vs +0.4 C at 10.07 vs 10.7 MB/s), so the direction is repeatable.

## Open questions

- Does the ~1.5x CPU-per-byte penalty survive in the REAL data path (modem -> USB -> NAT ->
  LAN)? Needs a reliable high-rate LTE load source (or a USB-host-to-USB-device iperf rig).
- OpenWrt idle: why does schedutil hold 800 MHz at idle? (Possible poller keeping the CPU
  busy-sufficient; worth a `perf`/trace look — it costs idle heat.)
- Android's broken DNS (resolves 127.0.0.1) is itself an OS-gap data point for the differential
  table, unrelated to thermal.

## Harness (reproducible)

Devices: OWRT root@192.168.7.1, AND root@192.168.100.1 (passwordless). Client loop per device:

    while running; do N=$(wget -q -O - $URL | dd bs=65536 count=64 | wc -c); B+=N; usleep $US; done

K loops in parallel + one 5 s logger (zones, freqs, /proc/stat head). Scripts were ephemeral
(/tmp); the numbers above and the trap list are the record.

## Follow-up (same session): the IDLE gap is NOT an OS gap either

After the A/B, the obvious remaining question was the 12 C idle pm8916 offset (55.4 vs 43.6).
Measured:

- cpuidle: OpenWrt 6.12 = `psci_idle` + `menu`, states WFI (lat 1) + cpu-sleep-0 (lat 280 us).
  Android 3.10 = `msm_idle` + `menu`, states C0/C1/C2 (lat 1/180/230 us). Comparable depth —
  OpenWrt DOES power-collapse; no idle-path deficit.
- idle busy%: ~7% on BOTH (from the idle-phase /proc/stat ticks).
- loadavg: OpenWrt 0.29; Android **3.5-5.5 (15-min 5.46)** — the Android unit runs chronically
  heavier AP load (framework) yet is the COOLER unit. This alone kills any 'OpenWrt idle burns
  more' theory.
- RF parity: OpenWrt RSRP **-81.00 dBm** / RSRQ -12.0 / SNR 13.0 / RSSI -52 (mmcli signal-setup
  5, then read; polling reset to 0 after). Android RSRP **-81** / RSRQ -11 / SNR 11.4
  (dumpsys telephony.registry). Same tower, same TX-power conditions — TX-driven modem heat
  ruled out.
- traffic at idle: OpenWrt wwan0 ~2 KB/s keepalives (40 conntrack entries, no real flows);
  Android ~0. Neither modem carries data, yet the OpenWrt unit's modem die (zone0) idles
  63-64 C vs the Android unit's 52-53 C.

With firmware byte-identical, A2 policy parity (a2_pin=1 both), rail parity (1.0625 V == 1.05 V,
prior session), bus voting shared, cores/freq/cpuidle/busy%/RF all at parity, the residual idle
modem gap is UNIT-LEVEL/PHYSICAL (case fit, USB-port heat coupling, antenna/cable placement),
not an OS property. To close it experimentally: swap the two units' USB ports (or locations) and
re-log idle zones for 30 min — the gap should follow the unit/port, not the OS.

## Final parity matrix (all measured)

| Axis | OpenWrt | Android | Parity |
|---|---|---|---|
| CPU cores online | 4 | 4 | yes |
| CPU freq under load | 800 MHz x4 | 800 MHz x4 | yes |
| CPU rail | 1.0625 V (CPR, prior session) | 1.05 V | yes |
| Bus voting | live, RPM (shared silicon) | live, same RPM | yes |
| cpuidle | WFI + cpu-sleep-0 @280 us | C0/C1/C2 @180/230 us | yes |
| idle busy% | ~7% | ~7% | yes |
| LTE RSRP | -81.0 dBm | -81 dBm | yes |
| idle data traffic | ~2 KB/s | ~0 | negligible |
| A2 policy | a2_pin=1 (held ON) | held ON | yes |
| modem firmware | byte-identical (proven) | same | yes |
| loadavg at idle | 0.29 | 3.5-5.5 | Android busier AND cooler |

VERDICT: no OS-controllable thermal lever remains on the evidence gathered. The gap is
(a) CPU-work-per-byte under sustained load (OpenWrt 1.5x in the synthetic path; real LTE path
unverified) and (b) unit-level physical variance at idle.
