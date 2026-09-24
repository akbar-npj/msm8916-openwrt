# 190 — Android DOES fatal; the RPM-rate differential is falsified; the RPM rate is an AP-idle meter

**Date:** 2026-09-23
**Device:** HMU05 `c2b9103c` (Android 4.4.4 HiMI vendor ROM, `HIMI_U01_MODEM_V1.0`)
**Status:** the A0 control arm of Doc 188 **produced a spontaneous modem fatal**; the traffic-matched
RPM-rate test that Doc 189 pre-registered has been **run and it falsifies Doc 189's rate claim**.

---

## 1. Headline

Three results, in order of importance:

1. **The A0 control arm — no manipulation, nothing but an idle instrument — caught a real modem
   fatal** at modem-uptime **31 517.5 s**, reason `ps_icmp6_msg.c:938`. Android's modem is **not
   fatal-immune**. Every prior "Android: 0 fatals" observation was taken in a window **shorter than
   a tenth** of the observed time-to-fatal, so none of them was ever evidence of immunity.
2. **The traffic-matched RPM rate test is run and it FALSIFIES Doc 189's "Android's RPM is ~1.9×
   busier."** At matched (idle) conditions Android is **226.8 ± 10.0 rec/s** against OpenWrt's
   archived **224.2 rec/s** — a ratio of **1.012**. The 1.92× was an uncontrolled-window artefact.
3. **New, and it invalidates the metric itself:** the RPM record rate is **not a platform
   constant — it is a meter of the AP's idle-entry rate.** Loading the AP's CPUs drops it **3.91×**
   (226.8 → 57.9 rec/s), reversibly (back to 227.9). No single-window RPM-rate comparison between
   two platforms is meaningful until this is controlled.

A fourth, separate observation: **Android's cellular data path is dead while the telephony layer
reports `CONNECTED/CONNECTED`.** Symptom-comparable to the OpenWrt stall; not the same defect and
not the same recovery. §6.

---

## 2. Anatomy of the A0 fatal

The A0 window opened at `ap_uptime` **5 298.59** (wall **2026-09-22T21:00:28Z**) with `fatal=0`,
`modem_rst=1`, `subsys2=ONLINE`, `/system` ro, telnet removed, and **no manipulation** (Doc 188 §14).

```
TRANSITION at ap_uptime=31525.35  modem_up=31518.74  fatal=1  rst=1  subsys2=OFFLINE
TRANSITION at ap_uptime=31530.82  modem_up=4.40      fatal=1  rst=2  subsys2=ONLINE
```

The captured dmesg (`evidence/188_android_side_repro/a0_window/dmesg_fatal_1_uptime_31525.35.txt`,
170 228 B, md5 `544a3f98c13bde363d9f21dcda787e0c`) holds **the whole boot** — first line
`[ 0.000000] Booting Linux on physical CPU 0x0`, last `[31525.375037]`. So this is a complete
record, not a wrapped ring, and the fatal count of **1** is exact.

```
[31524.147021] SMSM: Modem SMSM state changed to SMSM_RESET.
[31524.147075] Fatal error on the modem.
[31524.147089] modem subsystem failure reason: ps_icmp6_msg.c:938:.
[31524.147099] subsys-restart: subsystem_restart_dev(): Restart sequence requested for modem, restart_level = RELATED.
[31524.147609] subsys-restart: subsystem_shutdown(): [c1816900]: Shutting down modem
[31524.247659] pil-q6v5-mss 4080000.qcom,mss: Port e0b38000 halt timeout
[31524.247959] memshare: Modem Restart has happened
[31525.277465] smd_pkt_read notifying reset for smd_pkt_dev id:5
   ... ids 0,7,1,6,4,3,2 ...
[31525.374904] pil: MBA boot done
[31525.375025] Ramdump(ramdump_modem): No consumers. Aborting..
[31525.375037] Unable to dump modem fw memory (rc = -32).
```

| quantity | value |
| :-- | :-- |
| modem brought out of reset | `[ 6.610988]` |
| fatal detected | `[31524.147021]` = wall **2026-09-23T04:17:34Z** |
| **modem uptime at the fatal** | **31 517.536 s = 8 h 45 min** |
| time from window open to fatal | **26 226 s** |
| detection → `subsys2=ONLINE` | **≈ 5.5 s** |
| window so far (still running) | **31 177 s**, 1 fatal, **0 gaps**, `himi_conn=0` throughout |

**Reason string.** `modem subsystem failure reason: ps_icmp6_msg.c:938:`. This string occurs
**exactly once in the entire corpus** — in the file this document describes. Every OpenWrt fatal
reason ever recorded here is a **different** site:

| OpenWrt reason | count |
| :-- | --: |
| `a2_power.c:1189` | 130 |
| `lte_ml1_sleepmgr_stm.c:4054` | 80 (+8 variants) |
| `lte_ml1_common_timer.c:390` | 70 |
| `a2_task.c:3179`, `a2_power.c:2949`, `mmoc.c:*`, `a2_taskq.c:759`, … | ≤ 18 each |

**`ps_icmp6_msg.c` has no OpenWrt counterpart in 300+ recorded fatals.** The reason string is a
mutable global "last fatal" record and is a weak classifier (`reference_err_fatal_file_line_decode`),
so this is **suggestive, not proof**, that the two platforms crash in different code.

**Beat arithmetic (n = 1 — reported, not concluded).** Against the established always-on clock:

```
31 517.536 / 902.470 = 34.9236   (nearest int 35, residual −68.9 s)
31 517.536 / 903.675 = 34.8771   (nearest int 35, residual −111.1 s)
31 517.536 / 900.5   = 35.0000   (residual ±0 s, but 900.5 is not an established clock value)
```

The fatal sits near **beat 35** of a ~900 s clock, but **not** on either measured period — it is
**69–111 s early** for beat 35. With n = 1 this neither confirms nor refutes the clock; it is
recorded so that a second Android fatal can be scored against it. Doc 179's latched offset is the
natural explanation if the clock does apply.

---

## 3. The power correction: every prior "Android never crashes" was underpowered

This is the most important methodological consequence, and it is a correction to the record.

| Android observation | window | fatals | verdict at the time |
| :-- | --: | --: | :-- |
| control boot (Doc 189 §6) | 2 423 s | 0 | "no fatal" |
| second boot (Doc 188 §12.4) | 4 256.79 s | 0 | "no fatal" |
| 594 K-frame soak | — | 0 | "no fatal" |
| **A0, this document** | **31 177 s** | **1** | — |

The observed time-to-fatal is **31 517.5 s of modem uptime**. Every earlier window was **≤ 13.5 %**
of that. A 2 423 s window has, at this rate, a **~7.4 %** chance of containing a fatal — so
observing zero was the *expected* outcome and carried almost no information.

**The correct statement is not "Android never crashes." It is "Android's modem fatal rate is roughly
1 per 31 500 s, against OpenWrt's 1 per ~900 s — a ~35× difference."** That is still a large and
real differential, and it is still the thing to explain — but it is a **rate**, and the corpus must
stop treating it as an existence claim. (This is the same statistical-power error the AP-hang work
already earned: `feedback_statistical_power_pre_register_n`.)

**A second correction falls out of the same table.** The "no fatal" runs were also the runs that
established "the fatal does not correlate with AP power collapse" (§12.4). With a rate of 1 per
31 500 s, those runs could not have detected a correlation either. That claim is **withdrawn**, not
because it is wrong, but because it was never testable at those window lengths.

---

## 4. The traffic-matched RPM rate test — run, and it FALSIFIES Doc 189's rate claim

### 4.1 What Doc 189 claimed, and why it needed this test

Doc 189 measured Android's RPM log at **429.9 rec/s** against OpenWrt's **224.2 rec/s** (Doc 150's
archived 314.3 s idle baseline, 70 469 records) and reported **1.92×**. It explicitly flagged the
number as *"suggestive, not a result"* because the two sides were **not traffic-matched and were
different boots**, and pre-registered this test as the cheap decisive check.

The test's purpose is to separate two explanations for a rate gap:

* **(i) structural** — Android's AP is an RPM client (17 live `msm_bus` clients, Doc 189) and its
  votes add load the RPM never sees on OpenWrt. Then the gap survives traffic matching.
* **(ii) traffic** — the gap is just modem-side activity. Then matching traffic collapses it.

### 4.2 Pre-registration (frozen before measurement)

* **P-R1** — if the AP's votes are a material RPM load, Android's rate at **idle** must exceed
  OpenWrt's archived idle rate by a clear margin (≥ 1.3×).
* **Falsifier** — Android idle ≤ 224.2 rec/s, or within noise of it.
* **P-R2** — the rate should be **insensitive to AP CPU load**, because bandwidth votes track CPU
  *frequency*, not CPU *occupancy*.
* **Method** — read the RPM ring byte-write counter at `0x29dc38` (`rpmring`'s header `+0x38`; the
  counter advances in exact multiples of 32, so records = Δ/32) twice, N seconds apart. Three arms,
  same boot, same instrument: **A = idle**, **B = AP CPU load (4 × `busybox yes >/dev/null`)**,
  **A′ = load removed**. Reversibility is the control.

### 4.3 Results

| arm | samples (rec/s) | n | mean | sd |
| :-- | :-- | --: | --: | --: |
| **A idle** | 237.4, 239.1, 217.1, 213.3, 227.0 | 5 | **226.8** | 10.0 |
| **B AP CPU load** | 58.8, 55.0, 60.0 | 3 | **57.9** | 2.6 |
| **A′ recovered** | 235.0, 214.2, 234.6 | 3 | **227.9** | 11.9 |

* **B vs A: 3.91× drop, Welch t = 31.2.** Fully reversible (A′ = A to within noise).
* **A vs OpenWrt's 224.2: ratio 1.012.** Indistinguishable.

### 4.4 Verdict

* **P-R1 FALSIFIED.** Android idle 226.8 rec/s vs OpenWrt idle 224.2 rec/s. The AP's 17 voting
  clients add **no measurable RPM record load**. The "RPM is busier under Android" reading of
  Doc 189 is **retracted**.
* **Doc 189's 429.9 rec/s is retracted as an uncontrolled-window artefact.** The same instrument on
  the same device, measured under control, gives 226.8. The 429.9 reading was taken while the
  operator was actively probing the device; it is ~1.9× the controlled value, and the load arm shows
  this instrument is extremely sensitive to AP state.
* **P-R2 FALSIFIED, and in the opposite direction to the prediction** — the rate is *strongly*
  load-dependent (§5). This is a bigger result than the test itself.

**What survives from Doc 189.** The *structural* finding is untouched and still stands on its own
evidence: Android's AP has `MSM_BUS_SCALING=y` + `MSM_DEVFREQ_DEVBW=y` + 107
`msm_bus_scale_client_update_request` call sites + **17 live `/d/msm-bus-dbg` clients with non-zero
`ab`/`ib`**, and OpenWrt has none of it. What is now dead is the claim that this shows up as **RPM
traffic volume**. Any surviving mechanism must be about the **content/level** of the votes (a
bandwidth floor that keeps a resource out of a low-power state), not their count. That is P-B1, and
it needs a build.

---

## 5. New: the RPM record rate is an AP-idle meter, not a platform constant

The load arm was run as a *control* for the test above. It turned out to be the finding.

**AP CPU load cuts the RPM record rate 3.91×, reversibly.** The mechanism is not CPU frequency — the
load and recovery arms both read `scaling_cur_freq = 800000` on all four CPUs. The variable that
changed is **whether the AP's CPUs enter idle at all**: four spinning `yes` processes mean no idle
entries.

**Interpretation (stated as a hypothesis, not a result):** the RPM's log volume tracks the AP's
**idle-entry rate** — each idle transition generates RPM transactions (sleep/XO/SPM votes), so an
AP that never sleeps stops generating them. That is consistent with everything measured here: idle
226.8, busy 57.9, and OpenWrt idle 224.2 landing on the same number from the other direction.

**Three consequences, in order of severity:**

1. **The metric is invalid as a cross-platform discriminator without a load control.** A single
   30 s window can differ by 4× from another on the *same* device. Any future RPM-rate comparison
   must hold AP idle state fixed and report it.
2. **Doc 150's attribution needs re-examination.** Doc 150 concluded the steady-state RPM traffic on
   OpenWrt "is not the AP's — it is the modem's", on the evidence that only client 1 appeared in the
   idle baseline, with client 1 identified as the modem by its sequence counter restarting at an SSR.
   If the AP's idle votes are the bulk of the rate, that reading is incomplete. **This is testable on
   OpenWrt in ten minutes and it is now a top-priority item for the reflash:**
   `rpmring` idle vs 4× CPU-loaded, the same A/B/A′ design.
   * OpenWrt shows the same ~4× drop ⇒ the rate is AP-idle-driven on both platforms and Doc 150's
     modem attribution must be revised.
   * OpenWrt stays flat under load ⇒ **a genuine platform differential**, and a new lead.
3. **It supplies a second, independent reason the A0 fatal could not have been caused by the
   instrument.** `watch.sh` reads only `/proc`, sysfs and `dmesg` — it generates no RPM traffic and
   cannot hold the AP out of idle.

---

## 6. The data path: is this the OpenWrt data stall?

Asked directly by the operator: *"is it the same kind of data stall we have faced in openwrt"*.

**Symptom: the same. Layer and recovery: not the same.**

Measured 4 058 s after the fatal, and held for the 180 s of the test:

```
t=35600s  rmnet1 rx=429730  tx=710012
t=35660s  rmnet1 rx=429730  tx=710612
t=35720s  rmnet1 rx=429730  tx=711212
t=35780s  rmnet1 rx=429730  tx=711712      <-- rx frozen, tx +600/60 s
```

```
NetworkStateTracker for MOBILE:
  NetworkInfo: type: mobile[LTE], state: CONNECTED/CONNECTED, reason: linkPropertiesChanged, extra: jionet
  {InterfaceName: rmnet1
   LinkAddresses: [10.156.131.85/30, 2409:4071:d4f:ea49:416f:ecbc:6b63:e6f3/64]
   Routes: [0.0.0.0/0 -> 10.156.131.86, ::/0 -> fe80::3130:7e2f:e850:7c47]
   DnsAddresses: [49.45.0.1, 2405:200:800::1]}
  Mobile data state, sub0: CONNECTED
  Data enabled sub0: user=true
```

`rmnet1` is the **only** rmnet interface that has ever carried a byte — every other `rmnet*` and
`rmnet_data*` reads `rx=0 tx=0`. `wlan0` (74 MB rx / 298 MB tx) and `bridge1` (70 MB / 289 MB) are
busy, so the LAN side is fine.

| dimension | OpenWrt stall | Android now |
| :-- | :-- | :-- |
| interface up, addresses + routes present | yes | **yes** |
| control plane reports connected | yes | **yes** (`CONNECTED/CONNECTED`) |
| data does not pass | yes | **yes** (rx frozen) |
| **duration** | **14–17 s** per fatal (one 25 s) | **≥ 4 058 s and counting** |
| **self-recovers** | **yes** — one netifd `ifdown`/`ifup` per fatal | **no** |
| **does a retry work?** | **yes** — the first-packet stall eats exactly one packet and the retry always works | **no** — 8 pings, a DNS query, and an IPv6 ping all fail |
| layer | userspace: ModemManager settle timers + netifd | telephony/RIL (Android has no ModemManager) |

**Answer: no — not the same defect.** OpenWrt's stall is a **14–17 s userspace bearer rebuild** with a
watchdog that repairs it; Android's is a **persistent** data-plane failure with no recovery and no
working retry. What they share is the *symptom class* — control plane says connected, data plane is
dead — which is exactly the symptom that makes a stall hard to notice.

**And that is the uncomfortable reframing.** The operator's premise has been that Android "prevents"
the data stall. On this evidence **Android does not prevent it — it hides it.** The telephony layer
reports `CONNECTED/CONNECTED` with full link properties while nothing passes; there is no ModemManager
to bounce the bearer and no equivalent of `modem-bearer-watchdog`. Android's own stall detector is
`DcTracker`, and the corpus already established that Android **disables the data-stall alarm while
dormant** (`project_android_side_android_parity_quiesce`) — which is precisely the state this device
is in. So a stall that OpenWrt would detect and repair in ~16 s can persist indefinitely on Android
while every UI reads "connected".

**What is NOT established — do not over-read this:**

* **I cannot date the onset.** `/proc/net/dev` carries no timestamps. I know the WAN carried **430 KB**
  total and that `rmnet1` holds a **`scope global dynamic`** IPv6 address, which requires a Router
  Advertisement to have arrived — so the path **did** work at some point this boot. I do **not** know
  whether it died at the fatal or earlier, and therefore **cannot attribute it to the fatal.**
* **I cannot exclude a carrier/plan cause.** Jio may simply have dropped the bearer; `CONNECTED` is
  the phone's belief, not the network's.
* **This is one observation, n = 1, not a rate.**

**The decisive test, not yet run (it perturbs the running window, so it needs a decision):** bounce
the bearer and see whether it recovers. `svc data disable && sleep 5 && svc data enable`, then
re-check `rx_bytes`. Recovers ⇒ a bearer-state problem of the same family as OpenWrt's, merely never
auto-repaired. Does not recover ⇒ the data plane is broken at a different layer, and the fatal becomes
a candidate cause.

---

## 7. The ICMPv6 lead

The fatal's reason names an **ICMPv6 message handler**, and this device has **live dual-stack
cellular**:

```
rmnet1  inet6 2409:4071:d4f:ea49:416f:ecbc:6b63:e6f3/64 scope global dynamic
        inet6 fe80::416f:ecbc:6b63:e6f3/64 scope link
        inet  10.156.131.85/30
default via 10.156.131.86 dev rmnet1
default via fe80::3130:7e2f:e850:7c47 dev rmnet1
```

`/proc/net/snmp6`, whole boot (34 664 s):

| counter | value | direction |
| :-- | --: | :-- |
| `Icmp6InMsgs` | 86 | in |
| `Icmp6InRouterSolicits` (133) | 38 | in |
| `Icmp6InNeighborSolicits` (135) | 45 | in |
| `Icmp6InRouterAdvertisements` (134) | 3 | in |
| `Icmp6OutMsgs` | 81 | out |
| `Icmp6OutNeighborAdvertisements` (136) | 43 | out |
| **`Icmp6OutMLDv2Reports` (143)** | **20** | **out** |
| `Icmp6OutRouterSolicits` (133) | 11 | out |
| `Icmp6OutNeighborSolicits` (135) | 7 | out |

The **out**-direction messages are the ones that traverse the modem: Router Solicitations, Neighbor
Solicitations, and **MLDv2 Reports** are sent to multicast addresses (`ff02::2`, `ff02::1`,
solicited-node multicast) **out through `rmnet1` into the modem's data path**.

**Why this is worth a test.** It is the first reason string in this investigation that names a
**data-path** site rather than a power/sleep state machine. It is consistent with the corpus's
*activity-correlated* fatal finding (`project_a2power_periodic_905s`: under 1 Hz traffic the fatal
moves), and it offers a positive test rather than a wait — **if ICMPv6 is the trigger, generating
ICMPv6 traffic should raise the Android fatal rate**, which at 1 per 8.75 h is otherwise untestable
in reasonable time. Two candidate levers, cheapest first: force MLDv2/RS/NS traffic from the AP
(`ping6` to a multicast group, or flap the `rmnet1` IPv6 address to force DAD/RS), or suppress IPv6
on `rmnet1` entirely and look for a rate change.

**Caveat, stated plainly:** the reason string is a mutable global "last fatal" record and the corpus
explicitly warns against classifying a fatal by its `file:line`. Treat this as a **lead with a test**,
not a mechanism.

---

## 8. What this changes, and what to do next

**Changes:**

1. **Doc 188's ladder is void as designed.** §7 said: *"If A0 itself shows a modem restart, the
   platform is not quiet and every treatment arm is uninterpretable — stop and report that."* A0
   showed a modem restart. **The ladder is stopped and reported**, exactly as pre-registered. It is
   not a failure — it is the control doing its job and catching the premise being false.
2. **Doc 189's rate claim is retracted** (§4.4). Its structural claim stands.
3. **"Android never crashes" is replaced by "Android crashes at ~1 per 31 500 s"** (§3), and the
   earlier "fatal does not correlate with AP power collapse" claim is withdrawn as untestable at
   those window lengths.
4. **The RPM-rate metric is demoted** to something that must be measured with a load control (§5).

**Next, cheapest first:**

| # | test | cost | what it decides |
| :-- | :-- | :-- | :-- |
| 1 | **Bounce the bearer**, re-check `rx_bytes` | 1 min | whether the data failure is recoverable (§6) |
| 2 | **Let A0 keep running** for a second fatal | ~9 h, free (instrument already supervised, 0 gaps) | whether 31 517 s is a beat multiple or an event (§2) |
| 3 | **Generate ICMPv6** and watch the fatal rate | ~1 h | the §7 lead |
| 4 | **On the OpenWrt reflash: `rpmring` idle vs 4× CPU load** | 10 min | whether Doc 150's modem attribution survives (§5) — and whether the rate axis is a real differential |
| 5 | P-B1: give OpenWrt an interconnect consumer | build | the surviving half of Doc 189 |

**On the pre-registration discipline:** the A0 abort rule and the P-R1 falsifier were both frozen
before their data existed, and both fired. That is the mechanism working. The §3 correction is a
*statistical-power* correction to prior claims, not a re-tuning of a cut.

---

## 9. SOP compliance

**SOP steps observed.**

* **Comparative protocol** — every claim above is read from the live device (dmesg, `/proc/net/*`,
  sysfs, `dumpsys`, `devmem`), the tracked kernel tree, or a frozen capture with a recorded md5. No
  claim rests on a document's narrative. Where a document's number is contradicted, the document is
  corrected rather than reconciled (§4.4 retracts Doc 189's own number).
* **Pre-registration before measurement** — P-R1/P-R2 and their falsifiers were frozen before any
  sample was taken (§4.2), as was the A0 abort condition (Doc 188 §14.3). Both fired; neither was
  re-tuned afterwards.
* **Deployment/instrument verification** — the RPM counter address is the `rpmring`-documented header
  `+0x38`; the counter's advance was verified in multiples of 32 before use; the A0 instrument's md5
  was matched host↔device and its *shape* asserted on a real capture before the window opened
  (hygiene rule 9).
* **Measurement hygiene** — the A0 window is scored on coverage: **31 177 s, 0 gaps > 7 s** in
  `ap_uptime` (rule 10); the fatal capture was pulled to the host and hashed *before* analysis
  (rule 1); the A/B/A′ design supplies its own control and reversibility; the load arm's manipulation
  was verified (`loadavg`, `scaling_cur_freq`, and the recovery sample) rather than assumed.
* **"Verify the manipulation took"** — the load arm's effect was verified both by the manipulated
  quantity (CPU occupancy) and by its reversibility; a load arm whose load had not taken would have
  been VOID, not a negative.
* **Statistical power** — §3 applies the pre-register-n rule to *prior* claims and withdraws two of
  them as untestable rather than treating short windows as negatives.

**SOP steps deliberately not taken.**

* **No modem firmware was read, patched or written.** Nothing in this document makes a claim about
  the baseband image, so the dual-firmware byte-comparison step is not applicable.
* **No credential guessing** against the vendor admin UI.
* **The bearer was not bounced** (§6) — it is an intervention on a running measurement window and
  needs an operator decision.
* **`ps_icmp6_msg.c:938` was not resolved to a function.** The corpus warns that the reason string is
  a mutable global record; asserting a mechanism from it without a test would repeat an error this
  project has already made.
