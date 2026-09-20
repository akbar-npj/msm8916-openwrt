# 150 — HMU05: the RPM log is live, ordered and lossless; the RPM is awake through the fatal, and its only client is the modem

**Status:** the RPM log channel opened by Doc 149 is now *trustworthy*; Doc 149 §7 experiment 1
is answered. One replication (fatal #10) was still in flight when this was written and is
flagged as such in §12 item 1.
**Depends on:** `149_HMU05_RPM_FIRMWARE_AND_LIVE_RPM_LOG.md` §5 (corrected here),
`148_HMU05_FATAL_PERIODICITY_AND_SIGNATURE_TAXONOMY.md`,
`Modem RE/hmu05/900S_CRASH_LPR_FRAMEWORK_RE.md` §6.4/§6.6 (the `rpm.sync` stall question).
**Corrects:** Doc 149 §5.2 (record word `[2]` is **not** constant), Doc 149 §5.3 (its event
histogram came from a dump that is not a snapshot and is **retracted**), and both tools in
`evidence/149_rpm/` (marked superseded).

---

## 1. Why this document exists

Doc 149 §7 set one experiment first:

> **Capture the RPM log around a collapse.** … If the RPM logs `churning`-equivalent retries
> or stops logging for the modem, that separates Q6-side from RPM-side conclusively.

It could not be run, because the only way to read the ring was `devmem` one word at a time.
This session measured how slow that is and found it is **slow enough to make the reading
meaningless** (§3). So the first half of this document is a retraction, and the second half is
what became possible once there was a real instrument.

The answer to Doc 149 §7 exp. 1 is in §8: **the RPM does not stop.** It logs 214 records
inside the fatal+SSR window and then a 1286 rec/s storm. The RPM is not the stalled party.

A second, unplanned result turned out to be more interesting than the first: the RPM log
identifies **which master** each request came from, and in this system there is effectively
only one — the modem.

---

## 2. SOP Compliance Statement

Per Doc 131 §2 (Stages 1–5), this session followed the instrumentation-first workflow:

- **Stage 1 — empirical instrumentation, live telemetry.** This session *is* Stage 1. It
  built a new instrument (`rpmring`, §3), validated it against the ring's own write pointer
  (§4), and calibrated its clock against the AP's wall clock (§5) **before** drawing any
  conclusion from it.
- **Stage 2 — decompiled code analysis.** Used only as ground truth for *names*: the RPM
  resource-name table (`ldoa`, `ldob`, `smpa`, `smpb`, `bslv0`, `bmas`, `clk0/1/2`, `clka`,
  `xosd`, `vmin`, `cxo`, `/sleep/uber`, `/xo/cxo`) was read out of the RPM firmware's own
  strings rather than guessed from the live log.
- **No blind patching.** **Nothing was modified.** No firmware segment, no DT, no kernel
  module, no userspace file. Every device interaction was a read.
- **Stage 4 — re-signing / deployment.** Not reached; not applicable.
- **Stage 5 — acceptance.** Not reached.

> [!IMPORTANT]
> **Two of this session's own tools produced wrong answers, and both are recorded here rather
> than quietly fixed:**
> 1. `rpm_timeline.py` (Doc 149) reported a `span=453.865 s` for a ring that covers ~6 s, and
>    two 223.7 s "gaps" that were only the timestamp counter's wrap period. Root cause: it
>    analysed `devmem` dumps, which are not snapshots (§3).
> 2. `rpm_track.py`'s first version classified *any* word whose four bytes were printable as a
>    resource name. That turned the sequence number `0x00003148` into the "name" `H1..`, and
>    the small integers `0x72`, `0x65`, `0x4d` into `r...`, `e...`, `M...` — roughly 40 % of
>    the "resource names" in the first census were noise. Fixed by requiring ≥3 alphabetic
>    bytes (§6.2); every census in this document is post-fix.
>
> Both are the same class of error the comparative protocol exists to catch: a plausible
> number that nobody re-derived.

---

## 3. The instrument was wrong: a `devmem` dump is not a snapshot

**Measured on the device, 2026-09-21:**

```sh
U0=7967.25 ; <dump 2048 words with devmem> ; U1=7973.19
```

A full ring dump takes **5.94 s**. (2.9 ms per word — dominated by fork+exec, not by the read.)

Now the ring's own turnover. Two dumps taken **back to back** (a ~0.1 s gap between the two ssh
sessions, and `dur_ns` ≈ 0.5 ms per sample) shared **0 of 256 records**, and the second dump's
index-0 timestamp was `s1[0] + 6.601 s` — i.e. **exactly one ring period later**. Two dumps
36 s apart likewise shared **0 records**.

So the RPM overwrites all 256 slots in roughly **0.5–3 s** (it is bursty, §4.3). A 5.94 s walk
of the ring therefore reads slots from *many different generations*: by the time the walk
reaches index *i*, the slot has usually already been overwritten. The result is not a snapshot
and not even a coherent sample — it is an aliased mixture.

**Retracted as a consequence:**

| claim | source | status |
| :-- | :-- | :-- |
| "the ring covers only ~6.5 s (~39 records/s)" | Doc 149 §5 / memory | **wrong basis**; a devmem walk is ~6 s and the ring period is 0.5–3 s, so the two were conflated |
| the event-id histogram (`0xd5 x33`, `0xd1 x31`, …) | Doc 149 §5.3 | **retracted** — counts come from a non-snapshot |
| the ASCII argument census (`"ldoa" x29`, `"bslv" x21`, …) | Doc 149 §5.3 | **retracted**, and additionally inflated by the `is_name` bug (§2) |
| `span=453.865 s`, the two 223.7 s "gaps" | `evidence/149_rpm/rpm_timeline.py` | **retracted** — artefacts of unwrapping the 19.2 MHz counter across a non-snapshot |

**What survives from Doc 149 §5:** the ring's base address and size, the header layout, the
fact that `devmem`'s mmap path works where `/dev/mem` `read()` does not, and the *qualitative*
finding that the requested resources are regulators and clocks with no `vmin`/`xosd`/`cxo`.

### 3.1 The fix — `rpmring`

`/dev/mem` `read()` is unusable here (`dd if=/dev/mem … ` returns `Bad address`; verified
again this session). `mmap()` works. So the fix is a ~200-line static binary that mmaps once
and `memcpy`s.

* source: `evidence/150_rpm_track/rpmring.c`
* built with the tree's own cross toolchain:
  `openwrt/staging_dir/toolchain-aarch64_generic_gcc-14.3.0_musl/bin/aarch64-openwrt-linux-musl-gcc -O2 -static`
* a full 8 KB ring copy takes **480–670 µs** — ~10 000× faster than the `devmem` walk
* every sample is bracketed with `CLOCK_MONOTONIC`, which is what makes §5 possible

---

## 4. Ring geometry, decoded

### 4.1 Header (AP phys `0x29dc00`, 22 words read live)

| offset | value | meaning |
| :-- | :-- | :-- |
| `+0x00` | `0x00000000` | — |
| `+0x04` | `0x00001000` | version |
| `+0x08` | `"RPM External Log"` | name (Doc 149 wrote "Exitrnal"; the live word is `0x65747845` = `"Exit"`, so **"External"** — Doc 149's typo claim is itself wrong) |
| `+0x20` | `0x00000003` | — |
| `+0x24` | `0x0009DC58` | ring base **in RPM space** (`+0x200000` = AP phys `0x29dc58`) |
| `+0x28` | `0x00002000` | ring size = 8192 B |
| `+0x2c` | `0x00001FFF` | ring mask |
| `+0x38` | byte counter | **write counter — see §4.2** |
| `+0x3c` | byte counter − `0x2020` | tracks `+0x38` exactly, offset by 8224 |
| `+0x40` | `0x00000001` | — |
| `+0x4c` | `0x00010000` | — |

### 4.2 `+0x38` is the write pointer — verified twice, independently

The counter advances in **exact multiples of 32** (one record) and

```
write_pos_records = (counter >> 5) & 0xFF
newest_record_idx = (write_pos_records - 1) & 0xFF
```

*Verification A (absolute).* A coherent snapshot's header read `counter = 0x03561aa0`.
`0x1aa0 = 6816`, `6816/32 = 213`. The snapshot contained **exactly one** backward
discontinuity in its timestamp sequence, at **index 212** — i.e. the newest record is 212 and
the next write goes to 213. Exact match.

*Verification B (differential).* Four snapshots 400 ms apart:

| sample | counter | `(counter>>5)&0xFF` | Δcounter | Δindex mod 256 |
| :-- | :-- | :-- | :-- | :-- |
| 0 | `0x035a9f80` | 252 | — | — |
| 1 | `0x035ab8c0` | 198 | 6464 B = 202 rec | 252→198 = 202 ✓ |
| 2 | `0x035acdc0` | 110 | 5376 B = 168 rec | 198→110 = 168 ✓ |
| 3 | `0x035acdc0` | 110 | 0 | no change ✓ |

This is what turns the ring from "a buffer" into "an ordered stream": knowing the exact write
range `[prev_pos, new_pos)` lets `rpmring -t` copy **only the records that were just written**,
which is both lossless and ~20× cheaper than copying the ring.

### 4.3 Writes are extremely bursty

`changed_words` between 100 ms samples, in one 3.4 s window: `0,0,…,959,0,0,0,0,756,0,…,612,59,0,0,271,0,…`.
The RPM writes ~100–200 records inside a few milliseconds and then nothing for 0.3–1.2 s.
A 20 ms poller that insists on a "clean" snapshot therefore *skips* during every burst — which
is exactly how the first `rpmring -t` build lost records. §10 quantifies that.

---

## 5. The RPM's clock is 19.2 MHz — measured, three ways

`record[1]` is a free-running 32-bit counter (wraps every 2³²/19.2e6 = **223.7 s**). It had
previously been *assumed* to be 19.2 MHz. It is now measured against `CLOCK_MONOTONIC`:

| method | baseline | result |
| :-- | :-- | :-- |
| two-point, first record of first vs last block | 147.2 s (no wrap) | **19.1965 MHz** |
| first-record-of-block over all 515 blocks | 147.2 s | **19.1973 MHz** |
| last-record-of-block over all 515 blocks | 147.2 s | **19.1960 MHz** |
| full unwrap of all 70 469 records | 314.3 s (crosses 1.41 wraps) | **19.19970 MHz** |

Spread **0.017 %**. The unwrapped 314 s series has **0 backward steps**, so the unwrap is
self-consistent and the counter is genuinely monotonic.

> A naive two-point estimate over the 314 s baseline returns 5.54 MHz. That is not a
> measurement, it is the aliasing of one and a half wraps — recorded here because it is exactly
> the kind of number that looks like a discovery.

**Conclusion: `record[1]` is a 19.2 MHz tick counter**, and the RPM timestamps are therefore
directly comparable to the AP's clock at ~50 ns resolution.

---

## 6. The record grammar

### 6.1 Layout

Each record is 8 words (32 bytes):

| word | meaning |
| :-- | :-- |
| `[0]` | constant `0x00200000` |
| `[1]` | 19.2 MHz timestamp (§5) |
| `[2]` | **not constant** — see §6.3 |
| `[3]` | event id |
| `[4..7]` | arguments; small integers, or 4-char little-endian resource names |

### 6.2 Two transaction shapes

A "burst" is one or more complete transactions. Two shapes are observed.

**Shape A — batch resource set (no client id):**

```
0x0144                                 open
0x00cb                                 begin
0x00d6 <arg>                           (also seen 0x00c2/0x00c6/0x00c7 preamble)
  { 0x00d5 <resource> <value>          set resource
    0x0042|0x0044 <value> 0 <status> } response; status 0x3200 | 0x4900002 | 0x34100002
  [ 0x00d9 <client> <res> <val> <seq>  secondary request
    0x00da <arg> 0x24 ]
  [ 0x0245 <n> [<name>] ]
  [ 0x00db <client> <res> <val> <seq> ]
0x00c8 0x1 0x24 <timestamp>            summary
0x0143 0xffffffff 0xfffff77d           close
0x00cd                                 end
```

**Shape B — client request:**

```
0x0144                                 open
0x00d0 <client> <seq>                  client id + per-client sequence number
0x00cb                                 begin
0x00d1 <client> <resource> <value>     request
0x00d4 <resource> <value>              echo
0x00d5 <resource> <value>              apply
0x00d2 <client> "req."                 commit marker
0x0143 0xffffffff 0xfffff77d           close
0x00cd                                 end
```

`0x00d0`/`0x00d1`/`0x00d2` all carry the **same** first argument: the client id. That is the
hook §7 hangs on.

**Resources actually requested** (post-`is_name`-fix census, 147 s): `ldoa`, `bslv`, `bmas`,
`smpa`, `clk0`, `clk1`, `clk2`, `clka`. **No `xosd`, no `vmin`, no `cxo`, not once** — this
independently re-confirms Doc 149 §5.4 by a completely different route.

### 6.3 Word `[2]` is a slowly-incrementing small integer, not a constant

Doc 149 §5.2 recorded it as the constant `0x00000020`. It is not. Over the 314 s baseline it
took exactly three values and changed exactly twice:

```
0x24 (n=1062)  ->  0x25 (n=55696)  ->  0x26 (n=13711)
```

Two changes in 314 s. It is not a time base and not a record type (all three values carry the
same mix of ids). **Not decoded**; recorded so nobody repeats Doc 149's claim.

---

## 7. Client identity: client 1 is the modem

Over the 147 s capture around fatal #9, `0x00d0` appears with exactly **two** client ids:

| client | transactions | sequence range | when |
| :-- | :-- | :-- | :-- |
| **1** | **1152** | `0x1 … 0x318a` | present the entire capture |
| **0** | **18** | `0x11d … 0x12e` | only in a 0.73 s window at the fatal |

**Client 1's sequence counter resets exactly once in 1152 transactions, and it resets at the
modem SSR** — from `0x318a` (12 682) to `0x1`. A counter that restarts when the modem restarts
belongs to the modem.

> **client 1 = MSS.** Its per-resource request profile is the wide one
> (`ldoa` 693, `smpa` 272, `bslv` 248, `clk2` 183, `bmas` 133, `clk1` 101, `clka` 65, `clk0` 6).

**Client 0 is the odd one.** It appears only in the fatal window, makes only 18 requests, and
only for `ldoa` (9) and `smpa` (9). Its own sequence counter sits at `0x11d` = 285 — a *low*
number for something that has been running since AP boot.

Its first request is detected at **+9.190 s**, and the AP's `dmesg` shows
`remoteproc: recovering` at **+9.191 s**. Those coincide to within the 20 ms poll granularity.

> **Working hypothesis (not proven): client 0 is the AP.** If so, the AP's entire RPM-request
> footprint in this capture is 18 transactions in 0.73 s at the moment of the modem's death —
> consistent with the corpus finding that OpenWrt's AP side votes almost nothing to the RPM.
> **Falsifier:** client 0 should reappear at fatal #10 and at no other time. That test is
> running (§11).

**What this changes.** The steady-state RPM traffic in this device is **not** the AP's. It is
the modem's, at a median inter-burst interval of **1.2531 s** (n = 69, σ = 0.129 s), i.e.
~8.6 transactions/s at idle. Any future "the AP is voting X to the RPM" claim now has to
explain why the AP's client id is essentially never seen.

---

## 8. Fatal #9 — the RPM is awake the whole time

**Fatal #9:** AP `8165.925446`, `lte_ml1_common_timer.c:390`. Modem boot anchor `7264.214429`
⇒ **modem uptime 901.711017 s** — inside the 902.267 s ± 112 ppm band of Doc 149 §2.

SSR timeline from `dmesg`:

| AP | Δ from fatal |
| :-- | :-- |
| `8165.925446` fatal error received | 0.000 |
| `8165.925516` crash detected | +0.000070 |
| `8165.941401` recovering | +0.015955 |
| `8165.985103` stopped | +0.059657 |
| `8167.325182` **is now up** | +1.399736 |

Capture spans AP `8156.750 → 8303.976` s (147.226 s), **515 write bursts, 39 069 records**.

### 8.1 RPM activity per phase

| phase | window (rel) | records | rate | resources |
| :-- | :-- | :-- | :-- | :-- |
| A pre-fatal | 0.0 – 9.0 | 1120 | 124/s | ldoa 248, bslv 117, bmas 87, smpa 44, clk0 33, clka 27, clk1 27 |
| **B fatal + SSR** | **9.0 – 10.6** | **214** | **134/s** | smpa 27, ldoa 24, clk0 9, clk1 6, clk2 3 |
| C post-SSR storm | 10.6 – 14.2 | **4629** | **1286/s** | ldoa 1410, bslv 45, clk1 42, clka 36 |
| D new cadence | 14.2 – 20.0 | 1404 | 242/s | bslv 192, bmas 168, ldoa 144, clk0 72 |
| E second storm | 20.0 – 22.0 | 1475 | 737/s | bslv 184, bmas 155, ldoa 144 |
| **— silence —** | **22.0 – 31.25** | **59** | **6/s** | — |
| F after silence | 31.3 – 40.0 | 5070 | 583/s | ldoa 816, bslv 470, bmas 328, clk2 246, smpa 236 |
| G late | 50 – 65 | 4042 | 264/s | ldoa 813, bslv 369, bmas 286 |

**The RPM logs 214 records inside the 1.6 s window that contains the fatal, the SSR teardown and
the modem restart, and then 4629 records in the 3.6 s immediately after the modem comes back.**

Client 1's transaction rate, derived from its own sequence counter:

| phase | rate |
| :-- | :-- |
| pre-fatal | 8.59 txn/s |
| post-SSR storm | **138.30 txn/s** |
| after the silence | 22.36 txn/s |
| late | 11.08 txn/s |

### 8.2 Answer to Doc 149 §7 experiment 1

> **The RPM does not stall, hang, or stop logging across the collapse.** Its log counter
> advances monotonically through the fatal; it services 214 requests during the SSR window and
> 4 629 in the 3.6 s after the modem returns; and its own clock (19.2 MHz, §5) never stops.
>
> Therefore the "RPM-side" arm of the `rpm.sync` hypothesis — that the RPM core is wedged and
> the Q6 spins forever in `rpm.sync` waiting for a reply that never comes — is **not
> supported**. Whatever the ~900 s timer is, it is not the RPM being dead.

**What this does *not* prove.** It shows the RPM is *receiving and processing* requests. It
does not show that it *replies*, and the log records no reply events distinguishable from the
`0x0042`/`0x0044` status words. A wedged reply path with a live receive path is still
logically possible, though it is now a much less attractive hypothesis.

---

## 9. The 9.33 s silence

The single largest anomaly in 147 s is not at the fatal. It is here:

```
+21.926 s   burst, 115 records,  counter 0x036a3f40
+31.255 s   burst,  59 records,  counter 0x036a46a0     <-- gap 9.329 s
```

AP `8178.676 → 8188.005` s, i.e. **11.35 s to 20.68 s of the new modem's uptime**. The counter
moved only 1888 bytes (59 records) in that whole 9.33 s, and those 59 arrived in the burst at
the end — so the RPM received **nothing** for 9.3 s. Client 1's sequence advanced by 6 in
11.4 s where the post-storm rate would have given ~80.

**This is a modem-side silence, not an RPM-side one.** The RPM core was up; nothing asked it
for anything.

**It did not recur.** In the 314 s baseline capture (all of it after the silence), there is
**no gap above 2.07 s** — and that one 2.07 s gap is a sustained-write artefact of the first
`rpmring -t` build (§4.3), not a silence. So the 9.33 s quiet period is a one-off associated
with the post-SSR recovery window, not a periodic feature. Its cause is not known.

---

## 10. Capture integrity — what was lost, and where

The first `rpmring -t` build read the whole ring every poll and *skipped* any poll during which
the write counter moved (its "dirty sample" guard). During a sustained burst every poll is
dirty, so it skipped until the burst ended — and if more than 256 records were written in the
meantime, the ring had already wrapped and those records were gone.

**Measured loss, fatal #9 capture: 12 overruns, ~10 771 records** (deltas of 259, 259, 803,
2056, 2226, 1605, 1345, 552, 408, 581, 319, 298).

**Where:** every one of them is inside a post-SSR storm (rel 3.9 s, 10.5–10.6 s, 15.4 s,
20.3–20.8 s, 35.3 s, 39.6 s, 42.0 s, 48.9 s, 69.4 s, 111 s, 141 s). **None is inside the fatal
window** (rel 9.175 ± 1 s), which is why §8's conclusion is unaffected — but the *rates* in
§8.1 phase C are lower bounds.

The fix (delta-only copy, §4.2) removes the guard entirely. Validated: a 6 s run at 2 ms
polling produced **0 overruns** and per-poll copies of 25–140 µs.

**Baseline capture (314 s, 70 469 records, 224.2 rec/s):** only client 1 present; no gap above
2.07 s; `word[2]` 0x24→0x26.

---

## 11. What is established vs what is not

**Established**

1. A `devmem` ring dump is not a snapshot (5.94 s read vs 0.5–3 s ring period). Doc 149 §5.3
   and `evidence/149_rpm/*` are retracted. (§3)
2. The RPM log header word `+0x38` is the ring's byte write counter; `(counter>>5)&0xFF` is the
   write position. Verified absolutely and differentially. (§4.2)
3. The RPM's `record[1]` timestamp is **19.2 MHz**, measured four ways to ±0.017 %. (§5)
4. The log carries a **client id** and a per-client **sequence number**. (§6.2)
5. **Client 1 is the modem** — its sequence counter restarts at the modem SSR, once in 1152
   transactions. (§7)
6. **The RPM is alive through the fatal and the SSR.** 214 records in the fatal window, then a
   1286 rec/s storm. Doc 149 §7 exp. 1 answered. (§8)
7. **No `vmin`/`xosd`/`cxo` request ever appears**, by a second, independent route. (§6.2)
8. A unique **9.33 s modem-side silence** at AP 8178.676–8188.005 s, which did not recur. (§9)

**Not established**

* Who **client 0** is. AP is the hypothesis; §7 gives the falsifier.
* What `word[2]` means.
* Whether the RPM *replies* to the modem (only that it receives and processes). §8.2.
* Why the modem stopped voting for 9.33 s.
* Anything about the ~900 s timer itself. This document constrains the *mechanism space*
  (RPM-wedged is out) and says nothing about the *trigger*.

---

## 12. Next experiments

1. **Replicate at fatal #10.** Client 0 should reappear in a sub-second window at the fatal and
   nowhere else; client 1's counter should reset again. *Running when this was written.*
2. **Client 0 identification by perturbation.** Force an AP-side RPM request (e.g. a regulator
   or bus vote from the AP) and see whether client 0's sequence moves. Cheapest decisive test
   available; do it in a quiet window, not near a predicted fatal.
3. **Long baseline, lossless.** 30+ min at 2 ms with the fixed tool, to get an honest
   distribution of the burst cadence and to see whether the 9.33 s class of silence recurs.
4. **Does the modem's vote cadence change before the fatal?** With a lossless stream, compute
   the modem's inter-transaction interval as a function of time-to-fatal. If the ~900 s timer
   is *modulated* by the LPR vote loop, this is where it will show.
5. **Decode `0x00da`.** It carries a 32-bit value that looks like a timestamp plus `0x24`;
   if it is a completion latency, it gives the RPM→client round trip and would settle §8.2.
6. **Fix `word[2]`.** Three values, two transitions in 314 s — probably a generation or mask
   field; check it across an SSR.
7. **`/node/sleep/uber`.** Still not disassembled (Doc 149 §7). The resource table at RPM
   `0x165c0` is literally `"xosdvmin"` — this is the node that gates them.

---

## 13. Artifacts

```
Docs/Modem Stability/evidence/150_rpm_track/
  rpmring.c                 instrument: mmap reader + track mode  (build recipe in §3.1)
  rpm_track.py              analyser for `rpmring -t` output
  f9_track.txt.gz           fatal #9 capture, 147.2 s, 39 069 records, 12 overruns (honest)
  baseline_300s.txt.gz      314.3 s idle baseline, 70 469 records
  coherent_snapshot.txt     one coherent 256-record snapshot with its header
Docs/Modem Stability/evidence/149_rpm/
  decode_rpm_log.py         SUPERSEDED (header says so)
  rpm_timeline.py           SUPERSEDED (header says so)
```

---

## 14. One line

**The RPM log is now a trustworthy, ordered, wall-clock-stamped stream; it shows the RPM wide
awake through the fatal and the SSR, serving almost nothing but the modem — so the RPM is not
what stalls at 900 s, and the next thing to watch is the modem's own vote cadence in the
seconds before the timer fires.**
