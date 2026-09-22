# 182 — THE SSR POWERUP OUTCOME IS DETERMINED BY THE FATAL SIGNATURE, AND THE PC-ACK TIMEOUT IS A CONSEQUENCE OF IT

**Date:** 2026-09-22
**Boots:** `eafa19f6-5a01-4ea8-b783-52e9f16bf862` (retrospective, 17 fatals) and
`59d9c272-d87c-486a-9d6c-4fd93e02fc29` (live, 3 fatals)
**Patch under test:** `msm89xx/patches/808-bam-dmux-stats.patch` (2000 ms pc-ack wait arm),
plus the patch-814 SSR-powerup retry that produces the A/B outcome line
**Deployed module:** `qcom_bam_dmux.ko` md5 `982b633e2a682e21ad69b6e85d273941`
**Evidence:** `evidence/112_bam_reinit_ab/`
* `console_ramoops_prev.txt` — md5 `c6253e4bf582b889f73829c278a0c0fc`, 1655 lines / 146379 B
* `pcfine_boot59d9c272.txt` — md5 `2c0bc63754bad0bda2bde42962ae62d5`, 45 records
* `dmesg_boot59d9c272_upto2590s.txt` — md5 `768b7c10de3fa8e0c544919c5b92f295`, 545 lines
* `score_bam_reinit.py`, `score_output.txt`

---

## 1. SOP compliance

| SOP step | done? |
| :-- | :-- |
| Comparative protocol against the stock-HMU05 ground truth | **YES** — every claim here is about the **OpenWrt port's own driver** (`qcom_bam_dmux.c`) and the modem's *reported* fatal site. No baseband was touched, and no firmware claim is made. |
| Pre-register the prediction **before** the data exists | **PARTIAL, and the gap is stated** — §3's shortcut invalidated the pre-registration I had intended: the A/B-vs-signature association was read out of a console that already existed. §9 pre-registers the *next* prediction (H3) properly, before the data for it exists. |
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

The live boot adds a fifth: fatal #1 (`a2_power`, 532.081043) → A at 533.185843 → timeout at
534.360805, **Δ = 1.174962 s**. Across both boots, 5 A cases give Δ ∈ [1.109, 1.247] s.

**Reading.** A precedes the timeout by ~1.17 s in every case, and the timeout count equals the A
count exactly. So the timeout is **not** an independent `a2_power` signature property; it is a
**downstream consequence of the A path**. §4.3 supplies the candidate mechanism: the A-branch
does not `complete_all(&dmux->pc_ack_completion)`.

**Doc 181 §6 is therefore re-attributed, not withdrawn.** The measurement stands; the driver it
was attributed to was the wrong one. The chain is
`a2_power.c:1189` → **A** → pc-ack timeout, and `a2_power` correlates with A perfectly, which is
why the two were indistinguishable from a single boot.

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
falsify it, and n = 3–4 is needed before P1/P3 can be called supported.** The sampler is
running and needs no further intervention.

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

1. **What rebuilt `rx` in the B case is measured; what failed to rebuild it in the A case is
   not.** §8 measures the B mechanism. There is no A case on the live boot yet.
2. **The pc-ack timeout's mechanism is a candidate, not a proof.** §4.3's missing
   `complete_all()` is consistent with §6's timing, but the timeout could equally be caused by
   the modem not acking (Doc 181 §6's own alternative reading: "the modem did not ack" vs "the
   vote produced no edge"). Nothing here separates those.
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
* **`a2_power.c:1189` is a structurally distinct failure.** It is the only signature that leaves
  the pc-line handshake without a fresh edge, and the only one that produces a pc-ack timeout.
  That is a concrete, mechanistic difference between the two fatals, and it is the first
  non-statistical distinction found between them.
* **The instrument lesson.** Two of this doc's three corrections (§3, §7.3) are cases of
  *looking in the wrong place for the right quantity*: the console was in pstore, and the
  watchdog is a quiesce instrument. Both were cheap to check and both had been assumed.

---

## 13. Next actions

1. Let `scratch/pcfine.sh` run on boot `59d9c272` until **≥2 A cases** (§9's minimum n). It is
   already deployed and needs no intervention; fatal #4 is due ≈ 3242 s.
2. Score §9's P1–P4 against the sampler the moment an A case appears.
3. Pull the full `dmesg` to the host **before** any reboot (memory RULE 6), and read
   `/sys/fs/pstore/` before concluding a ring is gone (§3).
4. Not started, and untouched by this doc: the ~903.675 s always-on clock and its latched
   offset; the userspace bearer rebuild (fatal #2 on this boot cost **27 s**, of which an
   **18 s** ModemManager gap at 13:30:15 → 13:30:33 is avoidable, against a 0.70 s modem SSR);
   and the AP-side boot-firmware (`hyp`/`tz`) swap.
