# Doc 245 — The ~900 s modem event: CONFIG + RUN TRACK RECORD

**Status:** living record. Created 2026-10-04. Companion to the living ledger
`197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md` (this doc is referenced from its §112.66).

**Purpose.** The boot-anchored ~900 s modem event can manifest as a **FATAL** (assert → auto-coredump →
the modem's own ~12 s self-recovery) or a **WEDGE** (data-path death, NO coredump; recovery only via the
userspace stall-watchdog Stage-3 SSR, ~500 s later). This doc records, per run, the **exact device
configuration** (baseband firmware build, watchdog settings, activity, boot type) alongside the observed
**manifestation**, so the hypothesis *"a given config always produces the same manifestation"* can be
tested against data rather than memory.

---

## 1. Device configuration — baseline / steady state (2026-10-04)

**Platform.** OpenWrt 25.12.5, kernel `6.12.94` (aarch64, target `msm89xx/msm8916`), SMP PREEMPT.

**Baseband firmware** — `/lib/firmware` (on the **overlay**, NOT in the sysupgrade image; the modem
re-loads these on **every** powerup, so an AP reboot or an SSR activates the current files):

| file | md5 (current = STOCK) |
| :-- | :-- |
| `modem.mdt` | `1a6f9507e03d4ddbbf1977af81ecdbd7` |
| `modem.b00` | `d8e7e61274655ae9410190dad757bd9b` |
| `modem.b01` | `b85b86cec95bc250d753fe3eca016dc9` |
| `modem.b05` | `332f000baa2522e8bc01f3240115d316` |
| `modem.b16` | `57fef19de7178fb732c8b2edc40bc9dc` |

**Status: STOCK.** All diagnostic baseband patches are rolled back. Backups:
`/root/fw_stock_hmu05/` (stock), `scratch/deploy_v28_ring.py --rollback`.

**Loaded kernel modules (AP-side driver state):**

| module | md5 |
| :-- | :-- |
| `qcom_bam_dmux.ko` | `98d532def3b3c7efbcf1c241725ed999` (patch 831) |
| `qcom_q6v5_mss.ko` | `f786067f0a0a91c553f525f67de8732a` |
| `qcom_common.ko` | `5abd2ea850bde4a9acf10f000734375a` |

**`modem-watchdog` settings** (`uci show modem-watchdog`) — steady state:

```
general.enabled=1  stall_timeout=60  check_interval=10  cooldown_seconds=60
keepalive.enabled=0
recovery.ssr_enabled=1  preemptive_ssr_enabled=1  preemptive_ssr_interval=800  a2_pin=1
```

**`network.modem`:** proto=`modemmanager`, device=`qcom-soc`, apn=`jionet`, allowedmode=`4g`,
iptype=`ipv4v6`, dns `8.8.8.8 1.1.1.1`, metric 10, ifname `wwan0`.

**Boot instrumentation** (`/etc/rc.local`, all TEMPORARY diagnostic blocks): `hang_probe_t4`,
`f3cap` (+ `dmesgtap` + `dumpwatch`), `coredump-enable`.

**Packages (modem-related):** `diag-bind`, `diag-efs`, `diag-logtool`, `kmod-bam-dmux`,
`kmod-qcom-rproc`, `kmod-qcom-rproc-modem`, `kmod-qcom-rproc-wcnss`, `libqmi`, `libqrtr`,
`libqrtr-glib`, `modemmanager`, `qcom-carrier-autocfg`, `qcom-time-daemon`, `qmi-utils`,
`qrtr-utils`, `rmtfs` (286 packages installed total).

---

## 2. Applied patches

### 2a. AP-side (kernel / driver) — `msm89xx/patches/`, applied at build time

These are compiled into the running kernel/modules. Numbered 801–831:

```
801  arm64 dts qcom add-devices-makefile          818  dts pm8916 l13 voltage range
802  arm64 dts label reserved-memory               819  rpmsg-char guard null eptdev
803  arm64 dts swap leds uz801                     820  bam-dmux ssr teardown nonblocking cancel
804  arm64 dts msm8916-generic-uf02                821  bam-dmux ssr teardown flush before powerdown
805  arm64 dts msm8916-generic-hmu05               822  rpmsg-smd drain pollers before free
806  arm64 dts msm8916-generic-ufi001b             823  rpmsg-wwan-ctrl no smd poll registration
808  bam-dmux stats                               824  dts rpm master stats
809  bam-dmux tx-pm ordering                      825  bam-dmux pc-state reconcile
810  bam-dmux tx sweep race                       826  remoteproc q6v5-mss msm-subsys restart
811  bam-dmux deferred tx telemetry               827  remoteproc q6v5-mss gfmux clk src switch ovr
812  bam-dmux preserve deferred tx                828  bam-dmux ack stale deassert
813  msm8916 reboot-to-edl support                830  bam-dmux teardown substage probe
814  bam-dmux ssr powerup retry                   831  bam-dmux defer dma release to powered modem
815  qcom-sysmon ignore wcnss/modem ssr           999  tsens propagate eprobe defer
816  qcom-smsm validate mbox before request
```

### 2b. Modem-side (baseband) — built separately, deployed to `/lib/firmware` ONLY for diagnostic runs

`scratch/diag_patch_v*/` (v13 STM ring, v26/v27 sleepmgr rings, v28 RF-CNF ring). **Currently NONE
deployed (stock).** These do NOT affect the AP side and are rolled back after each run.

---

## 3. Run track record (config × manifestation)

| # | fw build | preempt SSR | a2_pin | traffic | boot | **manifestation** | event at | evidence |
| :- | :-- | :-- | :-- | :-- | :-- | :-- | :-- | :-- |
| 1 | v15 assert-suppress `1eae1869` | off | ? | no (soak) | cold | **WEDGE** (forced by suppression) | data dead ~AP 910 s | §112.57.11 |
| 2 | v13 ring `daa903be` | off | ? | yes | cold | **WEDGE** | AP 925.10 s | §112.57.13 |
| 3 | v13 ring `daa903be` | off | ? | yes | cold ⚠ | **FATAL** | AP 913.92 s | §112.57.14 |
| 4 | v28 ring `0f57e883` | off | 1 | yes | cold | **WEDGE** | data dead ~AP 970 s, SSR 1435 | §112.64 |
| 5 | v28 ring `0f57e883` | off | 1 | ? | warm | **FATAL** | modem-up 902.77 s | §112.64 |
| 6 | v28 ring `0f57e883` | off | 1 | ? | warm | **FATAL** | modem-up 901.98 s | §112.64 |
| 7 | v28 ring `0f57e883` | off | 1 | yes | warm | **WEDGE** | modem-up ~898–928 s | §112.65 |
| 8 | **stock** `1a6f9507` | off | 1 | yes | cold | **FATAL** | modem-up ~901.3 s | §112.66 |

⚠ run 3's *"cold boot"* label is **suspect** — run 2's wedge was followed by an SSR, so run 3 may in
fact have been a **warm** modem restart. The ledger text says cold; the sequence says warm. This is
exactly the ambiguity the track record exists to remove.

**Counts so far:** WEDGE 4 (runs 1,2,4,7) / FATAL 4 (runs 3,5,6,8). Event uptime: 898–928 s in every
case (the trigger is boot-anchored and stable).

**Victim signature (added with run 8, §112.66).** The *fatal* itself has ≥5 distinct sites —
`lte_ml1_sleepmgr_stm.c:4054`, `a2_power.c:1189`, `lte_ml1_common_timer.c:390`,
`lte_ml1_sm_idle_stm.c:2913` (§112.57.10), and now `lte_ml1_sm_conn_inter_freq_stm.c:712` (run 8).
Run 8's sleepmgr was **HEALTHY (state 1)** ⇒ the sleepmgr stall is a *victim*, not the trigger. This
adds a second untracked variable: the **victim** (which SM asserts first).

---

## 4. Analysis — is the manifestation deterministic?

**Answer: the data does NOT yet support determinism, but it also does not cleanly refute it, because
one variable is not controlled.** Three readings:

1. **Same documented config, different outcome.** Runs 2 and 3 are both *v13 ring, preempt SSR off,
   traffic on, cold boot* — yet one wedged and one fataled. Taken at face value this **refutes**
   determinism. **But** if run 3 was actually a warm restart (see the ⚠), it does not.
2. **Same boot type, different outcome.** Runs 5/6 (warm → FATAL) vs run 7 (warm → WEDGE): the same
   boot type produced both. This **refutes "boot type determines the manifestation"** outright.
3. **The one controlled pair.** Runs 4 (cold → WEDGE) and 5/6 (warm → FATAL) are consistent with a
   cold/warm split — but run 7 (warm → WEDGE) breaks it.

**Conclusion.** With the variables currently tracked (fw, SSR, a2_pin, traffic, boot), the manifestation
is **not** uniquely determined. At least one untracked variable exists. The strongest candidate is the
**phase of the MCPM/power-collapse cycle at the instant the ~900 s trigger lands** (§112.57.15): the
cycle runs ~3×/s, so which side of the PWRDN/WAKE-UP pair the stall lands on is, on the face of it, a
**race**. The manifestation then follows: freeze on the PWRDN side ⇒ the sleep-entry deadline assert
fires ⇒ **FATAL** (state 9); freeze on the WAKE-UP side ⇒ no assert ⇒ **WEDGE** (state 4).

**Candidate hidden variables to track from now on:** (a) the modem **restart mechanism** (cold boot /
crash-recovery / Stage-3 SSR / explicit `msm_subsys restart`); (b) **activity** (traffic on/off);
(c) the **MCPM/power phase** at the trigger; (d) AP/modem uptime at the event; (e) temperature.

### Pre-registration — P-DET (the determinism test)

> **Fixed config:** stock baseband, `preemptive_ssr=0`, `a2_pin=1`, continuous traffic, cold AP boot.
> **Run N = 10 events.** Record each manifestation.
> **H0 (deterministic, the user's hypothesis):** the split is 10:0 or 0:10.
> **H1 (race):** the split is mixed.
> **Falsification:** H0 is falsified by a single mixed pair; H1 is falsified only by 10 identical.
> **Power:** at a 50 % rate, n = 10 gives a 95 % CI of ~[0.19, 0.81]; 10 identical under H1 has
> probability 2·0.5¹⁰ ≈ 0.2 %. ⇒ n = 10 is enough to reject H1 if H0 holds, and to see a split if it
> is roughly balanced.

⚠ **Instrument caveat.** A wedge's Stage-3 SSR makes the *next* run warm, so a naive "cold boot every
time" loop silently becomes warm after the first wedge. The loop must force a **full AP reboot** (or at
minimum record the actual boot type) before each event.

**▶ SOAK LAUNCHED (2026-10-04 13:48 UTC).** The event fires at modem-up ≈900 s on **every** modem boot
(with `preemptive_ssr=0` + traffic), and the modem auto-recovers (a fatal self-recovers; a wedge is
recovered by the stall-watchdog Stage-3 SSR) — so the series runs **automatically** without a manual
restart loop. Live instruments:
- device `/root/pdet.sh` (recorder, `start-stop-daemon`, log `/root/pdet.log`) — logs every
  `FATAL <sig>` (from `dmesg "fatal error received: <file>:<line>:"`), every `WEDGE` (stall-watchdog
  `treating as a genuine stall`), and every modem restart, with the AP uptime;
- device `/root/v13_traffic.sh` (continuous `ping -I wwan0 8.8.8.1`/s) — the §112.49 "activity arms the
  event" regime;
- host `scratch/pdet_collect.sh` → `scratch/pdet_collect.log` (mirrors the device log every 60 s; exits
  at 10 events or 3.2 h).
⚠ Boot type after a **fatal recovery** (a remoteproc crash-recovery, not an SSR) is a *third* restart
class; record it, since cold/warm is already falsified (§112.65) but the restart mechanism is a tracked
variable.

**▶ P-DET live events (2026-10-04):**
| event | restart class before it | modem-up at event | manifestation | signature |
| :-- | :-- | :-- | :-- | :-- |
| 1 | fatal-recovery (after run 8) | ≈922 s | **FATAL** | `lte_ml1_sleepmgr_stm.c:4054` |

★ **Immediate live result:** run 8 (`lte_ml1_sm_conn_inter_freq_stm.c:712`) and P-DET event 1
(`lte_ml1_sleepmgr_stm.c:4054`) are the **same config, back-to-back** — the **victim SM varies within a
fixed config**, confirming §112.66's "variable victim" directly. (Both are FATAL, so this pair does not
yet speak to the fatal-vs-wedge split.)

---

## 5. Achieved vs Expected

| item | expected | achieved |
| :-- | :-- | :-- |
| device config captured (fw + modules + settings + packages) | complete | **YES** — §1 |
| patch inventory | complete | **YES** — §2 (801–831 AP-side; modem-side diagnostics separate) |
| run track record | every known run, with config | **YES** — §3 (8 runs) |
| determinism question answered | yes/no | **PARTIAL** — not determined by the tracked variables; ≥2 uncontrolled variables (MCPM phase, victim SM); P-DET pre-registered |
| root cause of the missing wake-up | identified | **OPEN** — see ledger §112.66 |

## 6. SOP statement

* **Ground truth first:** every md5, setting and package name in §1–§2 was read from the running device
  in this session; the run outcomes in §3 are transcribed from the ledger §112.57.11/13/14/64/65.
* **No blind patch:** nothing was written to the baseband for this doc; the device is on **stock**
  `modem.mdt 1a6f9507…`.
* **Reversibility:** all diagnostic changes are reversible (`deploy_v28_ring.py --rollback`;
  `uci set …preemptive_ssr_enabled=1`).
* **Honesty:** the manifestation is **not** shown to be deterministic; the one uncontrolled variable
  (MCPM phase) and the suspect cold/warm label are stated rather than smoothed over.
* **Ledger:** referenced from `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md` §112.66; memory updated in
  the same session.
