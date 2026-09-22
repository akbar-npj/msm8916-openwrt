# 182 — THE SSR POWERUP OUTCOME IS DETERMINED BY THE FATAL SIGNATURE, AND THE PC-ACK TIMEOUT IS A CONSEQUENCE OF IT

**Date:** 2026-09-22
**Boots:** `eafa19f6-5a01-4ea8-b783-52e9f16bf862` (retrospective, 17 fatals) and
`59d9c272-d87c-486a-9d6c-4fd93e02fc29` (live, 5 fatals — 3 `a2_power` → A, 2 `common_timer` → B)
**Patch under test:** `msm89xx/patches/808-bam-dmux-stats.patch` (2000 ms pc-ack wait arm),
plus the patch-814 SSR-powerup retry that produces the A/B outcome line
**Deployed module:** `qcom_bam_dmux.ko` md5 `982b633e2a682e21ad69b6e85d273941`
**Evidence:** `evidence/112_bam_reinit_ab/`
* `console_ramoops_prev.txt` — md5 `c6253e4bf582b889f73829c278a0c0fc`, 1655 lines / 146379 B
* `pcfine_v1_boot59d9c272.txt` — md5 `01168319e64bbfca417499008d99acaa`, 71 records — the
  A-case proof of §8.1 (key = `pc_irq_count` only; see §8.1)
* `pcfine_v2_boot59d9c272.txt` — md5 `ad02ab23a44e775e8b165e5f79996544`, 10893 records — the
  fixed-key capture covering §8.2 (fatal #5) and §8.4 (the orphan timeout). **Snapshot** taken at
  device uptime ≈ 4240 s while the sampler was still running; the device copy keeps growing.
* `pcfine_boot59d9c272.txt` — md5 `2c0bc63754bad0bda2bde42962ae62d5`, 45 records (earlier snapshot)
* `dmesg_boot59d9c272_upto2590s.txt` — md5 `768b7c10de3fa8e0c544919c5b92f295`, 545 lines
* `score_bam_reinit.py`, `score_h3.py`, `census_pc.py`, `pcfine.sh`, `score_output.txt`
  (`census_pc.py` reproduces §8.4's pc-line / PM census and the flatness check on any capture,
  and parses both sampler formats)

---

## 1. SOP compliance

| SOP step | done? |
| :-- | :-- |
| Comparative protocol against the stock-HMU05 ground truth | **YES** — every claim here is about the **OpenWrt port's own driver** (`qcom_bam_dmux.c`) and the modem's *reported* fatal site. No baseband was touched, and no firmware claim is made. |
| Pre-register the prediction **before** the data exists | **PARTIAL, and the gap is stated** — §3's shortcut invalidated the pre-registration I had intended for the A/B-vs-signature association: it was read out of a console that already existed. But **H3 (§9) was registered before any A case existed on the live boot and was then confirmed by the first one to arise (§8.1)** — P1–P4, all four. The association in §5 is retrospective; the mechanism in §8.1 is not. |
| Freeze the capture, hash it, score the frozen copy | **YES** — the pstore console was copied off the device and hashed before scoring; the scorer reads only the frozen copy. The live sampler was likewise pulled to a hashed file. |
| Keep a control | **YES** — (a) the 13 `lte_ml1_common_timer.c:390` fatals *inside the same boot* are the control for the 4 `a2_power.c:1189` ones; (b) the 14 B outcomes are the control for the 4 A outcomes on the timeout claim; (c) the cold-boot powerup (row 1) is a no-fatal control. |
| Verify the instrument before resting a claim on it | **YES, and it changed two conclusions** — §7.1 (the RX watchdog *has* rebuild paths, so its silence had to be checked, not assumed) and §7.2 (the modem's `CMD_OPEN` burst had to be ruled out as the rebuilder). |
| State what is *not* established | **YES** — §11 |

---

## 2. What was asked

The lead from the previous session: after an SSR, `bam_dmux_ssr_powerup_work_func()`
(`qcom_bam_dmux.c:2336`) prints one of three outcomes. The teardown releases the channels
(`T5 rx released`), so `dmux->rx == NULL` should hold and **every** fatal should end in
outcome **A**. Only 4 of 17 did. The question was whether the other 13 were outcome **B**
(the modem asserted its pc line early and the *interrupt* path rebuilt the channels first)
or outcome **C** (the modem genuinely failed to assert within the ~77 s retry budget).

**Answer: B, 13 of 13. C never occurred in either boot.**

---

## 3. The instrument — a pstore console that should not have been there

The intended method was to run a telemetry sampler until enough fatals accumulated (~4 h at
the 903.675 s beat). That was unnecessary. `/sys/fs/pstore/console-ramoops-0` held
**146379 B of the previous boot's console** — 1655 lines, including 35 `SSR powerup` lines
covering all 17 of that boot's fatals, and it survived the reboot because ramoops is the
persistent *console* writer, not the panic-only `dmesg-ramoops` record.

This is a **process correction**: in the previous session I recorded (memory RULE 6) that the
old boot's ring was lost by rebooting, and built a watcher to capture the future. The data was
never lost — it was in pstore the whole time and I did not look. **Before concluding a ring is
gone, read `/sys/fs/pstore/`.**

### 3.1 The capture is bit-corrupted, so pairing must be by ORDER

The ramoops console carries the known byte-corruption trap. Observed in this very capture:

| line | rendered | should be | damage |
| :-- | :-- | :-- | :-- |
| 549 | `[ 3234.44768]` | `3234.447680` | digit lost |
| 1318 | `[13266.8&9229]` | `13266.869229` | `&` substituted |
| **1364** | **`[13066.869437]`** | **`13266.869437`** | **bit flip in the INTEGER part** |
| 756 | `fatal error receIved` | `received` | capital `I` |
| 1487 | `Z15073.101920]` | `[15073.101920]` | bracket replaced |

Line 1364 is the dangerous one: a plausible-looking but **wrong** timestamp. A scorer that
paired events by timestamp would silently mis-pair there. `score_bam_reinit.py` therefore pairs
by **file order**, which corruption cannot reorder, and reports timestamps only as labels.
Line 756 is why the first manual grep reported fatal #7 as unreadable — it had been looking for
`received`, and `receIved` did not match. The tolerant pattern `rece\w*ved` recovers it.

---

## 4. The mechanism, read from source

### 4.1 The three outcomes (`:2402-2423`)

```c
mutex_lock(&dmux->state_lock);
if (READ_ONCE(dmux->in_teardown)) { ... return; }
if (!dmux->rx) {
        WRITE_ONCE(dmux->pc_state, true);
        if (bam_dmux_power_on(dmux)) {
                bam_dmux_pc_ack(dmux);
                pm_runtime_set_active(dmux->dev);
                dev_info(... "successfully reinitialized BAM channels and rings");   /* A */
        } else { ... }
} else {
        dev_info(... "channels already active");                                     /* B */
}
```

* **A** — `dmux->rx == NULL`: nobody rebuilt the channels, so the work did.
* **B** — `dmux->rx != NULL`: something else rebuilt them first.
* **C** — the pc line never asserted within ~77 s; the retry loop gives up.

All three are preceded by an **unconditional** line:

```c
dev_info(dmux->dev, "bam_dmux: SSR powerup: modem pc_state=%d (waited %d ms)\n",
         pc_line, 200 + i * 20);
```

**The label is a misnomer**: the argument is `pc_line` (the level read from the irqchip), *not*
`dmux->pc_state`. `waited 200` means `i == 0`, i.e. the line was already high at the first poll
after the fixed `msleep(200)`. This is the one line that separates A/B/C from C, and the earlier
grep pattern missed it — which is why the lead was stuck at a source reading.

### 4.2 Who can rebuild `rx`?

| rebuilder | logs on success | counter |
| :-- | :-- | :-- |
| `bam_dmux_pc_irq()` assert branch (`:1939`) | **nothing** (only `dev_dbg`) | `pc_irq_count` (+1 unconditionally at `:1941`) |
| `bam_dmux_rx_watchdog_func()` (`:1309`, `!rx` path) | `dev_warn "modem awake (pc line asserted) but no channels, rebuilding"` | `pc_resync_count` |
| `bam_dmux_rx_watchdog_func()` (`:1330`, lost-edge path) | `dev_warn "PC line asserted while pc_state=0 (lost edge), resyncing"` | `pc_resync_count` |
| `bam_dmux_ssr_powerup_work_func()` A-branch | `dev_info "successfully reinitialized"` | — |

The watchdog paths both `dev_warn` **and** both bump `pc_resync_count`. That makes them
checkable rather than assumable — see §7.1.

### 4.3 The asymmetry that explains the timeout

The `pc_irq` assert branch ends with:

```c
complete_all(&dmux->pc_ack_completion);
wake_up_all(&dmux->pc_wait);
```

The powerup work's **A**-branch does **not**. It calls `bam_dmux_power_on()`,
`bam_dmux_pc_ack()` and `pm_runtime_set_active()`, and returns. A `bam_dmux_runtime_resume()`
already waiting on `pc_ack_completion` is therefore never woken by the A path. That is a
source-level candidate for §6.

---

## 5. Result 1 — the outcome is predicted by the fatal signature

Paired by file order. Rows 2–18 are the 17 fatals; row 1 is the cold-boot powerup.

| # | fatal t | signature | pc_line | waited | outcome |
| --: | --: | :-- | --: | --: | :-- |
| 1 | — (cold boot) | — | 1 | 560 | B |
| 2 | 524.211653 | **`a2_power.c:1189`** | 1 | 200 | **A** |
| 3 | 1427.099153 | `common_timer.c:390` | 1 | 200 | B |
| 4 | 2330.773740 | `common_timer.c:390` | 1 | 200 | B |
| 5 | 3234.44768 * | `common_timer.c:390` | 1 | 200 | B |
| 6 | 4138.119715 | `common_timer.c:390` | 1 | 200 | B |
| 7 | 5041.798167 | `common_timer.c:390` | 1 | 200 | B |
| 8 | 5949.210659 | **`a2_power.c:1189`** | 1 | 200 | **A** |
| 9 | 6852.985676 | `common_timer.c:390` | 1 | 200 | B |
| 10 | 7756.662692 | `common_timer.c:390` | 1 | 200 | B |
| 11 | 8719.020382 | **`a2_power.c:1189`** | 1 | 200 | **A** |
| 12 | 9622.891300 | `common_timer.c:390` | 1 | 200 | B |
| 13 | 10526.566407 | `common_timer.c:390` | 1 | 200 | B |
| 14 | 11430.241652 | `common_timer.c:390` | 1 | 200 | B |
| 15 | 12362.261426 | **`a2_power.c:1189`** | 1 | 200 | **A** |
| 16 | 13265.751554 | `common_timer.c:390` | 1 | 200 | B |
| 17 | 14169.427048 | `common_timer.c:390` | 1 | 200 | B |
| 18 | 15073.101717 | `common_tiler.c:390` * | 1 | 560 | B |

\* corrupted in the capture; see §3.1.

```
                    A    B
a2_power.c:1189     4    0
common_timer:390    0   13
```

**Fisher exact, one-sided p = 0.00042017; two-sided p = 0.00042017.** (The two coincide, as
they must for a perfect table.)

**The association is perfect and the split is clean.** `a2_power.c:1189` → A, 4/4.
`lte_ml1_common_timer.c:390` → B, 13/13. **Outcome C never occurred in this boot.**

### 5.1 Two caveats

* Row 18's `waited 560` is anomalous — it is the only **fatal** SSR that did not assert at the
  first poll (16 of 17 did). Its outcome is B, so it does not disturb the split, but it shows the
  200 ms figure is not universal.
* `waited 200` is an artefact of the fixed `msleep(200)`, not a measurement: it means "already
  asserted when the poll began". The poll cannot resolve the assert time below 200 ms.

---

## 6. Result 2 — the pc-ack timeout is a consequence of A, not an `a2_power` property

Doc 181 §6 established that the `pc-ack timeout` is `a2_power`-specific at 4/4 vs 0/10
(p = 0.003497), and treated that as a property of the signature. This boot separates the two:

| | timeout | no timeout |
| :-- | --: | --: |
| **A** | **4** | 0 |
| **B** | 0 | 14 |

**Fisher exact one-sided p = 0.00032680.** And the timeout **follows** the A outcome, at a
near-constant latency:

| fatal | A ("successfully reinitialized") | pc-ack timeout | Δ |
| --: | --: | --: | --: |
| 2 | 525.323701 | 526.440017 | **1.116316 s** |
| 8 | 5950.318477 | 5951.427446 | **1.108969 s** |
| 11 | 8720.129599 | 8721.331363 | **1.201764 s** |
| 15 | 12363.367529 | 12364.614338 | **1.246809 s** |

The live boot adds three more, all `a2_power` → A:

| fatal | A ("successfully reinitialized") | pc-ack timeout | Δ |
| --: | --: | --: | --: |
| 1 | 533.185843 | 534.360805 | **1.174962 s** |
| 4 | 2976.800897 | 2978.056277 | **1.255380 s** |
| 5 | 3885.730123 | 3886.848426 | **1.118303 s** |

Across both boots, **7 A cases give Δ ∈ [1.109, 1.255] s** (range 146 ms), and **B has produced
0 timeouts in 17 outcomes** (13 fatal B + 1 cold-boot B from the retrospective boot, 2 fatal B
+ 1 cold-boot B from the live boot).

**Reading.** A precedes the timeout by ~1.17 s in every case, and the timeout count equals the A
count exactly. So the timeout is **not** an independent `a2_power` signature property; it is a
**downstream consequence of the A path**. §4.3 supplies the candidate mechanism: the A-branch
does not `complete_all(&dmux->pc_ack_completion)`.

**Doc 181 §6 is therefore re-attributed, not withdrawn.** The measurement stands; the driver it
was attributed to was the wrong one. The chain is
`a2_power.c:1189` → **A** → pc-ack timeout, and `a2_power` correlates with A perfectly, which is
why the two were indistinguishable from a single boot.

**Qualified by §8.4.** A is not the *only* way to get a timeout. While this doc was being
written the live boot produced a **fourth** timeout with **no fatal at all** — an 80 ms
suspend/resume pair whose pc line never transitioned, so neither completion source ran and the
2000 ms wait expired (§8.4). The correct reading of the table above is therefore "**A guarantees
the timeout**", not "A is the timeout's only cause"; the general condition is H4's — the
completion needs a line *transition*.

---

## 7. Result 3 — what is ruled out

### 7.1 The RX watchdog is not the rebuilder

`bam_dmux_rx_watchdog_func()` can rebuild `rx` (§4.2). It did not: this capture contains
**zero** `rebuilding` / `resyncing` / `lost edge` lines, and the live telemetry reports
`pc_resync_count: 0`. So the B-case rebuilder is `bam_dmux_pc_irq()`.

### 7.2 The modem's `CMD_OPEN` burst is not the rebuilder

142 `received CMD_OPEN` lines appear. A burst of 7–8 follows **every** fatal SSR, both A and B
(rows 2 and 3 both show 8). On the A rows it also arrives *after* the `successfully
reinitialized` line (row 2: A at 525.323701, first `CMD_OPEN` at 525.730). It cannot be the
discriminator, and it cannot be the rebuilder on the A rows.

### 7.3 The pre-fatal pc line is low in *both* signatures — and this does NOT falsify the line hypothesis

All **186** `RX watchdog: quiesced` lines in the capture read `pc_state=0, pc_line=0`, with
quiesce counters up to 2820 s. It is tempting to conclude from this that the pc line is low
before every fatal and therefore cannot explain A. **That conclusion is wrong, and I drew it
earlier in this session before checking the instrument.**

The watchdog prints **only while quiesced**, on a ~60 s cadence, and the last line before a
fatal can be tens of seconds earlier — 29.5 s before fatal #8. The live sampler (§8) shows the
line toggling every few seconds to ~100 s. A 29.5 s gap is therefore **wide enough to contain a
full assert**, and the quiesce line says nothing about the state at the fatal.

This is the same family of error as the corpus's other instrument traps: **a control that
silently did not cover the window is worse than no control**, because it turns an untested
hypothesis into a false negative. The watchdog is a *quiesce* instrument, not a *pre-fatal*
instrument.

---

## 8. Result 4 — the live replicate, and the measurement the console cannot give

A 45 Hz telemetry sampler (`scratch/pcfine.sh`) was deployed to resolve the ~1.1 s SSR window.
It logs on **change of the event fields only** (the uptime and quiesce counters change every
iteration and would otherwise defeat change-detection). Verified before use:

* `rx_telemetry_show()` takes **no `state_lock`** — only `spin_lock_irqsave(&dmux->rx_lock)`
  over 32 slots (`:2132`) — so a 45 Hz reader **cannot contend with the SSR path it measures**.
* Measured cost ~80 ticks over ~70 s ≈ **~1 % of one core**; the loop rate is 22.1 ms/iter.
* It survives SSH detachment via `setsid` (busybox `sleep` rejects fractional arguments, hence a
  fork-per-iteration loop rather than a timed one).

**Fatal #3 on boot `59d9c272`** — `lte_ml1_common_timer.c:390` at **2338.609206** → outcome **B**:

| event | time |
| :-- | --: |
| fatal | 2338.609206 |
| teardown scheduled / T4 `state_lock` | 2338.632036 / 2338.671748 |
| `stopped remote processor` | 2338.810815 |
| `SSR after powerup: scheduling powerup work` / `is now up` | 2339.518926 / 2339.519032 |
| `SSR powerup: modem pc_state=1 (waited 200 ms)` | 2339.719214 |
| **outcome B** `channels already active` | 2339.719303 |
| `received CMD_OPEN (1) on channel 0..7` | 2340.140581 … 2340.191208 |

The sampler's last record **before** the fatal:

```
162|162 0 0 1 0 0 1 1433 82 82 82 82 24 2331.34 138664
```

`pc_irq_count=162`, `pc_state=0`, `pc_line=0`, `pc_quiesce_ms=138664` — **the line was low at the
fatal**, and low for the preceding 138.7 s. The next record:

```
163|163 1 1 1 0 0 0 1433 83 82 83 82 24 2339.52 0
```

At uptime **2339.52** — within 22 ms of `is now up` (2339.519032) — `pc_irq_count` went
**162 → 163** with `pc_state 0 → 1` and `pc_line 0 → 1`, and `rx_tearing_down` went 1 → 0.

**So for the B case, measured rather than argued:**

1. the line was **low** at the fatal;
2. the modem's post-reload assert produced a **genuine rising edge**;
3. `bam_dmux_pc_irq()` **consumed it** (`pc_irq_count` +1) and rebuilt the channels;
4. the powerup work's poll then found `rx != NULL` → **B**.

Note this also means the poll's `pc_state=1 (waited 200 ms)` is *not* evidence that the line was
high early — it is evidence only that the line was high when the poll began, 200 ms after the
work started.

### 8.1 The A case (fatal #4) — H3's P1 and P3 are confirmed

**Fatal #4 at 2975.685045 is `a2_power.c:1189` → outcome A.** The sampler was running.

| event | time |
| :-- | --: |
| fatal | 2975.685045 |
| teardown scheduled / T4 `state_lock` | 2975.706874 / 2975.746448 |
| `stopped remote processor` | 2975.882878 |
| `SSR after powerup: scheduling powerup work` / `is now up` | 2976.588566 / 2976.588652 |
| `SSR powerup: modem pc_state=1 (waited 200 ms)` | 2976.799456 |
| **outcome A** `successfully reinitialized` | 2976.800897 |
| `received CMD_OPEN (1) on channel 0..7` | 2977.208468 … 2977.224677 |
| `modem pc-ack timeout during resume` | 2978.056277 |

The sampler records that bracket it:

| record | uptime | `pc_irq_count` | `pc_state` | `pc_line` | `pc_timeout_count` |
| :-- | --: | --: | --: | --: | --: |
| last **before** the fatal | 2973.58 | 185 | **1** | **1** | 1 |
| next | 2979.23 | **186** | 0 | 0 | **2** |

**Exactly one `pc_irq` thread run occurred in the whole window** (185 → 186), and the state it
produced is `pc_state = 0, pc_line = 0` — i.e. that single run is the final **1 → 0 fall**. The
line was therefore high at the fatal and **stayed** high through the teardown, the reload and the
A rebuild, which is why the poll at 2976.799456 found it high and why `bam_dmux_pc_irq()` **never
ran** between the teardown and the A outcome. (Read with §8.3: the counter counts thread runs, not
edges, so this says "the handler did not run", not "no edge arrived".)

**Why this is still rigorous despite a bug in the sampler.** The change key is not the 14-field
event tuple it was meant to be: `${cur%%|*}` on a line of the form `iq|aq ps pl …` reduces the key
to `pc_irq_count` **alone**, so the sampler only logged when `pc_irq_count` changed (this is why
`rx_tearing_down`, `rx_callbacks` and `cmd_open` transitions inside the window were not logged).
For *this* claim that is exactly the right keying: **every run of `bam_dmux_pc_irq()` is guaranteed
a record**, which is precisely the quantity P3 asserts. It is **not** sufficient to track wire
transitions — a masked edge moves the line without moving the counter — which is why §8.2 needed
the full-tuple key. The bug was fixed for the ongoing run (§13), which now keys on the full tuple.

**Prediction score (§9, registered before this case existed):**

| | prediction | result |
| :-- | :-- | :-- |
| **P1** | last pre-fatal record shows `pc_state = 1, pc_line = 1` for an A fatal | **CONFIRMED** |
| **P2** | last pre-fatal record shows `pc_state = 0, pc_line = 0` for a B fatal | **CONFIRMED** (fatal #3) |
| **P3** | `pc_irq_count` does not increment between the teardown and the A outcome | **CONFIRMED** — the only increment is the trailing deassert |
| **P4** | in a B case `pc_irq_count` increments exactly once at the assert | **CONFIRMED** (fatal #3) |

**The A/B contrast, measured on the same boot, with the same instrument:**

| fatal | signature | line at the fatal | `pc_irq` run in the window | outcome |
| --: | :-- | :-- | :-- | :-- |
| 3 | `common_timer.c:390` | **low** (low for the preceding 138.7 s) | **one, rising**, at the assert | B |
| 4 | `a2_power.c:1189` | **high** | **one, falling only**, at the very end | A |

**And the timeout follows A again**: fatal #4's A at 2976.800897 → `pc-ack timeout` at
2978.056277, **Δ = 1.255380 s**. Together with fatal #1 (Δ = 1.174962 s) that makes the
A → timeout association **6 of 6 across two boots, 0 of 14 for B** (§6).

### 8.2 Fatal #5 — H3 confirmed at n = 2, and the window exposes the real mechanism

**Fatal #5 at 3884.610521 is `a2_power.c:1189` → outcome A** (reinitialized at 3885.730123),
timeout at 3886.848426 (**Δ = 1.118303 s**). The fixed sampler key gives a lead time of
**0.001 s** on the last pre-fatal record — versus 2.105 s in the v1 run — because the full-tuple
key logs at ~17 records/s.

| record | uptime | `pc_irq_count` | `pc_state` | `pc_line` | `rx_tearing_down` |
| :-- | --: | --: | --: | --: | --: |
| **last before the fatal** | 3884.61 | 269 | **0** | **1** | 1 |
| (fatal) | 3884.610521 | | | | |
| line falls | 3884.64 | **269** | 0 | 0 | 1 |
| | 3884.75 | **269** | 0 | 0 | 1 |
| line rises | 3885.54 | **269** | 0 | 1 | 1 |
| work sets pc_state | 3885.66 | **269** | 1 | 1 | 0 |
| (`successfully reinitialized`) | 3885.730123 | | | | |
| | 3886.79 | **269** | 1 | 1 | 0 |
| thread finally runs | 3888.06 | **270** | 0 | 0 | 1 |

**P1 HIT, P3 HIT → H3 confirmed on this case.** But the window shows something the previous
cases could not: **the line moved 1 → 0 at 3884.64 and 0 → 1 at 3885.54 with `pc_irq_count`
completely flat at 269.** Two wire transitions, zero counter movement. That is only possible if
`pc_irq_count` does not count edges — and it does not (§8.3).

### 8.3 Correction — `pc_irq_count` counts THREAD RUNS, not edges

```c
ret = devm_request_threaded_irq(dev, dmux->pc_irq, NULL, bam_dmux_pc_irq,
                                IRQF_ONESHOT, NULL, dmux);          /* :2581 */
```

The **primary handler is `NULL`** and `bam_dmux_pc_irq` is the **`thread_fn`**. The kernel then
installs its *default primary handler*, which under `IRQF_ONESHOT` **masks the line and wakes
the thread**. `dmux->pc_irq_count++` is the first statement of `bam_dmux_pc_irq` — i.e. of the
**thread**. So the counter is incremented when the thread **runs**, and a thread blocked on
`state_lock` (which the SSR teardown holds from T4 to after T8) contributes nothing, while the
masked line delivers no further transitions.

**This is the actual mechanism, and it is cleaner than H3's phrasing:**

> **H4.** The pc line is sampled through a threaded `IRQF_ONESHOT` irq whose line is **masked
> from the moment an edge arrives until the thread returns**. At an **A** fatal the line is
> **already HIGH**, so a thread is already queued and the line is masked when the SSR begins;
> the teardown's `disable_irq`/`enable_irq` resync cannot manufacture an edge that the hardware
> has already consumed, so nothing rebuilds `rx` through the interrupt path and the powerup
> work's **level-poll** is the only thing that sees the high line → **A**. At a **B** fatal the
> line is **LOW**, no thread is queued and the line is not masked, so the modem's post-reload
> rise is delivered normally and the thread rebuilds → **B**.

**H3's wording was too strong and is corrected here.** H3 said "no rising edge *reaches*
`bam_dmux_pc_irq()`". The measurable fact is narrower: **the thread did not run inside the
window**. Whether an edge arrived at a masked line is *not observable* from `pc_irq_count`,
because that counter lives in the thread. So P3 is confirmed **as written** ("`pc_irq_count` does
not increment between the teardown and the A outcome") but its interpretation changes from "the
edge was lost" to "the handler did not run" — which is exactly the quantity the A/B outcome
depends on, and it is the reason a queued-but-blocked thread loses to the work's level-poll.

A second observation, offered as an observation and not a claim: in this window `pc_state`
disagreed with the wire (`pc_state = 0, pc_line = 1`) for roughly a second **before** the fatal.
The RX watchdog's resync path exists for precisely that condition, but it returns early whenever
`in_teardown` is set and it runs on a ~60 s cadence, so it did not fire here (`pc_resync_count`
stayed 0).

### 8.4 An orphan pc-ack timeout with NO fatal — H4's mechanism reproduced outside the SSR

While the sampler was still running, `pc_timeout_count` went **3 → 4** at uptime **4186.364155**
with **no fatal anywhere near it** (the boot's last fatal is #5 at 3884.610521; the console
between 3886.85 and 4186.36 holds only `CMD_OPEN`s and `port wwan0qmi0 attached`). This is a
**runtime-PM** timeout, not an SSR one, and it is the first timeout on either boot that is not
downstream of an A outcome. The frozen sampler resolves it completely:

| uptime | `pc_irq_count` | `pc_ack_irq_count` | `pc_state` | `pc_line` | `pc_timeout_count` | `pc_vote_tx` | `pc_unvote_tx` | `pm_resume` | `pm_suspend` |
| --: | --: | --: | --: | --: | --: | --: | --: | --: | --: |
| 3951.50 | 289 | 303 | 1 | 1 | 3 | 155 | 154 | 155 | 154 |
| *235 s with the tuple frozen; only `rx_callbacks` climbs, 35308 → 62185* | | | | | | | | | |
| 4184.06 | 289 | 303 | **1** | **1** | 3 | 155 | **155** | 155 | **155** |
| 4184.14 | 289 | 303 | **1** | **1** | 3 | **156** | 155 | **156** | 155 |
| 4186.32 | 289 | 303 | 1 | 1 | **4** | 156 | 155 | 156 | 155 |

Read out:

1. From 3951.50 to 4184.06 the whole tuple is **frozen for 235 s** — `pc_state = 1`,
   `pc_line = 1`, `rx_tearing_down = 0`, `pc_irq_count` flat — while `rx_callbacks` climbs at
   ~114/s. The modem was **fully awake and busy**, and the device never suspended.
2. At **4184.06** a suspend was attempted (`pm_suspend_attempts` 154 → 155, `pc_unvote_tx`
   154 → 155). The line was **still HIGH** at that instant and it **never fell** — no `pc_irq`
   run, no `rx_tearing_down`.
3. **~80 ms later**, at **4184.14**, a resume voted (`pm_resume_attempts` 155 → 156,
   `pc_vote_tx` 155 → 156). Because the line was **already HIGH**, the vote produced **no rising
   edge**, so `bam_dmux_pc_irq()` did not run (`pc_irq_count` stayed 289) and
   `bam_dmux_pc_ack_irq()` did not fire either (`pc_ack_irq_count` stayed 303).
4. Neither completion source ran, so `wait_for_completion_timeout()` in
   `bam_dmux_runtime_resume()` (`:2066`) expired after 2000 ms → the timeout at **4186.32**
   (Δ = 2.18 s from the vote, matching the reported `pm_last_resume_ms: 2178`).

**What this means for §6.** §6's claim — "the pc-ack timeout is a consequence of A" — is an
association over the fatals, and it stands (A → timeout 7/7, B → 0/17). But this event shows A is
**not the only way** to produce a timeout. The general condition is the one H4 names: **the
completion is completed only by `bam_dmux_pc_irq()` (`:1977`) or `bam_dmux_pc_ack_irq()`
(`:1997`), and both require a line *transition***. A fatal makes that condition *certain* (the
line is pinned high and masked); an 80 ms suspend/resume pair makes it *possible* without any
fatal at all. §6 should therefore be read as "A **guarantees** the timeout", not "A is the
timeout's only cause".

**What this means for H4.** This is an **independent, non-fatal confirmation of H4's core
mechanism**, and it is the cleanest case in the corpus: no SSR, no teardown, no lock contention,
no masked line — just a resume whose line did not transition, and a timeout that followed
exactly as H4 predicts. The mechanism does not need the fatal to be true; the fatal is simply
one way to guarantee the precondition.

**The second completion source is flat too, in both windows.** `bam_dmux_runtime_resume()`'s
wait can be completed by `bam_dmux_pc_irq()` (`:1977`) **or** by `bam_dmux_pc_ack_irq()`
(`:1997`), so §11.2 has kept two readings alive: "the vote produced no edge" and "the modem did
not ack". The sampler's fixed key shows that in these windows the two readings **coincide**:

| window | `pc_irq_count` | `pc_ack_irq_count` | outcome |
| :-- | :-- | :-- | :-- |
| fatal #5, 3884.57 → 3886.79 (§8.2) | **269 → 269 (flat)** | **275 → 275 (flat)** | A, then timeout at 3886.85 |
| orphan, 4184.06 → 4186.32 (§8.4) | **289 → 289 (flat)** | **303 → 303 (flat)** | timeout at 4186.32 |

Neither source ran. **This does not identify which one is "the" cause, and it should not be read
as "the ack is downstream of the pc irq".** The normal-operation census argues against that
reading: outside the two windows `pc_ack_irq_count` moves on its own — e.g. at 3406.48 it goes
203 → 204 while `pc_irq_count` is still 201 and the line is still high, 170 ms before the
`pc_irq` run at 3406.65 — which is why it runs *ahead* of the pc counter overall (303 vs 289).
`bam_dmux_pc_ack()` (the AP's ack-bit toggle, which elicits the modem's ack) is called from
`bam_dmux_pc_irq()` (`:1955`, `:1973`), from the RX watchdog (`:1336`, `:1351`) and from the SSR
powerup work (`:2412`), so the two paths are coupled but not ordered. What the measurement
supports is the weaker and sufficient statement: **in both timeout windows, neither completion
source ran, so the driver offers no case in which one of them rescued the wait.** In the A case
the thread does eventually run — `pc_irq_count` goes 269 → 270 at **3888.06**, ~3.4 s after the
fatal, and `pc_ack_irq_count` follows to 276 — but by then the 2000 ms wait has already expired
at 3886.85. In the orphan case the thread never runs at all, because the line never transitions.

**A latency cost, not a stability one.** In the orphan window the state was already correct
throughout (`pc_state = 1`, `pc_line = 1`, `rx_tearing_down = 0`, `rx_callbacks` climbing at
~114/s), and it stayed correct after the timeout — the sampler shows `pc_timeout_count = 4` with
the data plane untouched. So this event's entire cost is the **2000 ms** the resume spent in
`wait_for_completion_timeout()`, which is the same latency class the corpus already attributes to
the 250 → 2000 ms widening (memory: *"a LATENCY defect, not stability"*). It is a second,
independent demonstration that the pc-ack wait can burn its full budget on a vote that needed no
work — and, unlike the A cases, with no fault anywhere in sight.

**What is NOT established.** (a) It is **one** occurrence — the sampler caught the only orphan
timeout on this boot, and the boot has produced exactly one. (b) *Why* the modem held the line
across the 80 ms unvote is not determined here; "the modem's deassert is slower than 80 ms" and
"the unvote was coalesced with the re-vote" are both consistent with the data and are not
separated. (c) Whether the 80 ms suspend/resume pair is itself abnormal is not assessed — with
155 suspend attempts against 143 completions there is PM churn on this boot, but no baseline for
what is normal.

---

## 9. H3 — the refined hypothesis, pre-registered

The hypothesis is not the naive "the line is asserted before the fatal" (§7.3 kills that as a
*sufficient* condition, since the line is low before a B fatal). It is about **whether the
assert edge is available to the handler after the teardown**:

> **H3.** At an **A** fatal the pc line was **already asserted at the moment of the fatal** and
> **stayed** asserted across the SSR. The teardown then sets `pc_state = false` and clears the
> remote SMSM bit, but because the line never falls there is **no rising edge afterwards**, so
> `bam_dmux_pc_irq()` never rebuilds. Only the powerup work's level-poll sees the high line, and
> it rebuilds → **A**. At a **B** fatal the line was **low** at the fatal, so the modem's
> post-reload assert is a real rising edge, the handler consumes it, and the work finds the
> channels already active → **B**.

**Predictions, registered 2026-09-22 before any A case exists on boot `59d9c272`:**

* **P1** — in the sampler's last record before an **A** fatal, `pc_state = 1` and `pc_line = 1`.
* **P2** — in the sampler's last record before a **B** fatal, `pc_state = 0` and `pc_line = 0`.
  *(already satisfied by fatal #3, §8)*
* **P3** — `pc_irq_count` does **not** increment between the teardown and the A outcome line.
* **P4** — in a B case `pc_irq_count` increments exactly once at the modem's assert.
  *(already satisfied by fatal #3, §8)*

**Falsifiers.**

* An **A** case whose last pre-fatal record shows `pc_line = 0` **kills H3**. (That would leave
  "the edge was delivered but `bam_dmux_power_on()` failed in the handler" as the surviving
  reading — separable only by whether `pc_irq_count` moved.)
* An **A** case with `pc_irq_count` incremented in the window kills the *lost-edge* mechanism
  specifically, while leaving the A/B split itself intact.

**Required n.** The signature mix is 4 `a2_power` : 13 `common_timer`, so A cases arrive at
roughly **1 in 4 fatals ≈ 1 per hour** at the 903.675 s beat. The split itself is already
established at n = 17 (§5); H3 is a *within-A* claim, so **n = 2 A cases is the minimum that can
falsify it, and n = 3–4 is needed before P1/P3 can be called supported.**

**Status: P1–P4 confirmed at n = 2 A cases (§8.1 fatal #4, §8.2 fatal #5).** Both A cases to
arise after registration confirmed all four predictions, the second with a lead time of 0.001 s.
**H3's mechanism wording is superseded by H4 (§8.3)**: `pc_irq_count` counts thread runs, not
edges, so the measured fact is "the handler did not run in the window" rather than "no edge
reached the handler". **H4 has since gained an independent, non-fatal confirmation (§8.4)** — a
resume whose line did not transition timed out exactly as H4 predicts, with no fatal involved.
The sampler is still running; fatal #6 is due ≈ 4788 s.

---

## 10. A correction to my own earlier reading

Earlier in this session I wrote that H2 was "falsified" on the strength of the 186 all-low
quiesce lines. That was an overreach for the reason given in §7.3: the watchdog does not cover
the window in which the claim applies, so the observation was **silent on H2**, not against it.
The corrected statement is that H2 is **untested by the console** and is now being tested by the
sampler as H3. The error was made by treating a counter's *last reported value* as its value at
an arbitrary later time.

---

## 11. What is NOT established

1. **The A mechanism is confirmed at n = 2, but the masking is inferred, not directly
   observed.** §8.1 and §8.2 confirm P1–P4 on both A cases. The claim that the *line is masked*
   under `IRQF_ONESHOT` comes from reading the registration (§8.3) plus the flat counter while
   the wire moved — the kernel's default primary handler is not instrumented, so "the edge was
   consumed by the mask" and "the edge was never latched by the irqchip" are **not separated**.
   Both produce the same observable: the thread does not run. What the outcome depends on — that
   the interrupt path does not rebuild `rx` — is measured either way.
   Note also the **asymmetry of the test**: the pc line is high a large fraction of the time
   (the sampler shows it toggling every few seconds to ~100 s), so **a high pre-fatal line is
   weak evidence on its own; P3 — that `pc_irq_count` does not move — is the load-bearing
   prediction**, and it held on both cases.
2. **The pc-ack timeout's mechanism is a candidate, not a proof.** §4.3's missing
   `complete_all()` is consistent with §6's timing, but the timeout could equally be caused by
   the modem not acking (Doc 181 §6's own alternative reading: "the modem did not ack" vs "the
   vote produced no edge"). **§8.4 shows the two readings coincide in the windows measured** —
   in both the A window and the orphan window *both* completion sources are flat
   (`pc_irq_count` and `pc_ack_irq_count`), so there is no case here in which one ran and the
   other did not. That does not identify the mechanism so much as show that the driver's two
   completion paths are not independent enough to discriminate between the readings. The Δ is
   now 1.109–1.255 s over 7 A cases, which bounds the mechanism but does not identify it.
3. **No causal claim.** A and B both end with the channels rebuilt and the data plane restored.
   The A/B split is a **symptom-level** discriminator between two fatal signatures. Nothing here
   says the A/B outcome causes the fatal, or that the fatal is caused by the pc line.
4. **Outcome C is unobserved, not excluded.** It did not occur in 18 fatal SSRs across two boots.
   At the observed rate that bounds it below ~1 in 6 (rule of three, 95 %) — it is *rare*, not
   impossible, and the ~77 s retry budget remains untested.
5. **The 4 : 13 signature mix is not explained.** Why `a2_power.c:1189` behaves differently at
   the pc line is the open question; §9's P1 would give the first evidence about it.
6. **`waited 560` in row 18 is unexplained** (§5.1).

---

## 12. Why this matters

* **The lead is closed.** The 13 `common_timer` fatals are **B**, not C: the modem asserts
  promptly, the interrupt rebuilds the channels, and recovery is healthy. There is no hidden
  class of 77 s recovery failures. The ~77 s retry budget added by patch 814 has still never
  been exercised by a real fatal.
* **Doc 181 §6 is re-attributed.** The pc-ack timeout belongs to the **A** path, and
  `a2_power` is its proxy. Anyone re-opening the 250 → 2000 ms question should now condition on
  A/B, not on the signature.
* **…but the timeout is a *transition* defect, not a fatal defect (§8.4).** The same boot
  produced a timeout with no fatal at all, from an 80 ms suspend/resume pair whose line never
  moved. So the honest statement of the finding is that **A guarantees a pc-ack timeout**, and
  the underlying condition — the completion needs a line transition, and the vote is a no-op
  when the line is already high — is a **runtime-PM** property that the fatal merely forces.
  That matters for anyone tempted to read "A → timeout" as "the timeout is a symptom of the
  fatal": it is not, it is a symptom of a vote that changed nothing.
* **`a2_power.c:1189` is a structurally distinct failure, and the mechanism is now identified.**
  It is the only signature whose fatal leaves the pc line **already asserted**, the only one that
  produces a pc-ack timeout, and §8.3 explains *why*: the pc irq is **threaded with
  `IRQF_ONESHOT` and a NULL primary handler**, so an asserted line means a queued thread and a
  **masked line** — the interrupt-driven recovery path is structurally unavailable and only the
  powerup work's level-poll saves the data plane. That is the first **mechanistic** difference
  found between the two fatals; every previous distinction between them was statistical.
* **The masking has a cost beyond the fatal.** §8.3's observation that `pc_state` can disagree
  with the wire for ~1 s before a fatal, while the only resync that exists returns early on
  `in_teardown` and runs on a ~60 s cadence, is a latent robustness gap in the port — not a
  cause of the fatal, and not claimed as one.
* **A latent robustness point about the driver.** The port's SSR recovery depends on a
  *level* poll (`bam_dmux_ssr_powerup_work_func`) only as a fallback; the primary path is
  edge-driven. §8.1 shows a case where the edge path is structurally unavailable and the
  fallback is what recovers the data plane. That is a point in favour of patch 814's retry
  design, and it is worth remembering that the retry has still never been needed (§11.4).
* **The instrument lesson.** Two of this doc's three corrections (§3, §7.3) are cases of
  *looking in the wrong place for the right quantity*: the console was in pstore, and the
  watchdog is a quiesce instrument. Both were cheap to check and both had been assumed.

---

## 13. Next actions

1. The sampler is still running on boot `59d9c272`; fatal #6 is due ≈ 4788 s (the device was at
   ~4240 s when the capture was last pulled, and `pc_timeout_count` was 4). H3 is settled at
   n = 2 A cases, so further fatals are now **confirmatory only** — but a `common_timer` → B case
   with the fixed key would be the first B case measured at 0.001 s lead time, which would close
   the last asymmetry in the evidence (the B case in §8 rests on the v1 capture's 7.269 s lead).
   Pull `/tmp/pcfine.txt` again before any reboot; it now has the fixed key and is the only
   record of the §8.4 orphan timeout.
2. **The one thing that would sharpen H4**: instrument the *primary* handler. The kernel's
   default primary handler is not traceable, so "the edge was consumed by the `IRQF_ONESHOT`
   mask" and "the irqchip never latched the edge" remain unseparated (§11.1). A `dev_err` in a
   custom primary handler would distinguish them — but that requires a module rebuild, so it is
   only worth doing if the distinction ever becomes load-bearing. It currently is not: both
   readings predict "the thread does not run", which is what the A/B outcome depends on.
3. Pull the full `dmesg` to the host **before** any reboot (memory RULE 6), and read
   `/sys/fs/pstore/` before concluding a ring is gone (§3).
4. Not started, and untouched by this doc: the ~903.675 s always-on clock and its latched
   offset; the userspace bearer rebuild (fatal #2 on this boot cost **27 s**, of which an
   **18 s** ModemManager gap at 13:30:15 → 13:30:33 is avoidable, against a 0.70 s modem SSR);
   and the AP-side boot-firmware (`hyp`/`tz`) swap.
