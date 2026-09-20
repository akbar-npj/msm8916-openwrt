# E1 — sustained-traffic experiment: FINAL RESULT (A/B/A)

**Run:** 2026-09-20, deployed boot, AP uptime 12 → 7262 s.
**Design:** Doc 148 §3.0.1. Phase A idle; phase B 1 Hz `ping 8.8.8.8` from AP ~5310 s; phase B
was stopped early at AP ~6401 s (after fatal #7) to obtain a clean A′ idle phase.
**Raw log:** `scratch/e1_traffic/e1_traffic.log` (gitignored) — key excerpts reproduced below.

---

## 1. The three phases

| phase | AP window | signature | modem-uptime period | n |
| :-- | :-- | :-- | :-- | :-- |
| **A** idle | 12 → ~5310 | `lte_ml1_common_timer.c:390` | 902.267 s (spread 0.101 s = 112 ppm) | 4 |
| **B** 1 Hz ping | ~5310 → ~6401 | `a2_power.c:1189` | **895.490 s**, then **932.855 s** | 2 |
| **A′** idle again | ~6401 → 7262 | `lte_ml1_sleepmgr_stm.c:4054` | **900.864 s** | 1 |

## 2. The pre-registered prediction FAILED

Recorded *before* fatal #7 (Doc 148 §3.0.1, verbatim):

| model | predicted |
| :-- | :-- |
| idle cadence resumed | 6330.005874 s |
| traffic changed it | 6323.195888 s |

**Actual fatal #7: AP 6360.554391 s.** Late by **+30.55 s** and **+37.36 s** respectively.

In modem-uptime terms: 895.490 s (fatal #6, under traffic) → 932.855 s (fatal #7, still under
traffic) → 900.864 s (fatal #8, after traffic stopped). Phase B is **not a fixed period at
all** — it spans 37.4 s.

## 3. What this establishes

1. **Traffic suppresses the deterministic idle fatal.** Phase A is exact to 112 ppm; phase B
   has no fixed period.
2. **A different failure substitutes.** Under traffic the deterministic timer does not fire;
   `a2_power.c:1189` does, at a variable time. This matches the already-recorded scope
   retraction ("`a2_power.c:1189` is not periodic; treat it as a rate").
3. **The ~900 s period returns when idle**, with a **third** signature. So the assert site is
   not stable even within one boot across two idle phases.
4. **Doc 148 §3.0.1's inference is refuted.** The single A→B transition was read as "the AP's
   activity pattern selects which of several ~900 s timers expires first". The A/B/A shows the
   narrower truth: the *idle* watchdog is reset by activity, and a *separate, non-deterministic*
   failure appears under traffic.

## 4. Raw evidence

Phase A → B transition and the failed prediction:

```
t=5412 ping_ok=1 fatals=5 last_fatal=4529.467002lte_ml1_common_timer.c:390:
t=5437 ping_ok=0 fatals=6 last_fatal=5426.331445a2_power.c:1189:
...
t=6319 ping_ok=1 fatals=6 last_fatal=5426.331445a2_power.c:1189:
t=6334 ping_ok=0 fatals=6 last_fatal=5426.331445a2_power.c:1189:
t=6364 ping_ok=0 fatals=7 last_fatal=6360.554391a2_power.c:1189:      <-- 30.6 s late
```

`dmesg` anchors for the three phases (AP uptime, seconds):

```
[  914.769287] fatal error received: lte_ml1_common_timer.c:390:      A
[ 4529.467002] fatal error received: lte_ml1_common_timer.c:390:      A (last)
[ 5426.331445] fatal error received: a2_power.c:1189:                 B (traffic on)
[ 6360.554391] fatal error received: a2_power.c:1189:                 B (traffic on)
[ 7262.819515] fatal error received: lte_ml1_sleepmgr_stm.c:4054:     A' (traffic stopped ~6401)
```

Modem boot anchors used for the modem-uptime column:

```
[   12.092460] 4080000.remoteproc is now up
[  916.175824] 4080000.remoteproc is now up
[ 1819.807165] 4080000.remoteproc is now up
[ 2723.511313] 4080000.remoteproc is now up
[ 3627.257122] 4080000.remoteproc is now up
[ 4530.841763] 4080000.remoteproc is now up
[ 5427.699310] 4080000.remoteproc is now up
[ 6361.955365] 4080000.remoteproc is now up
[ 7264.214429] 4080000.remoteproc is now up
```

## 5. Caveat

Phase B was stopped early (AP ~6401 s) rather than at its planned end (AP ~7410 s), so phase B
contains only 2 fatals and the "traffic persists" branch of the prediction was tested on
fatal #7 only. That is sufficient to falsify it (it was 37.4 s off), but phase B's *distribution*
is not characterised — 2 samples spanning 37.4 s. A longer matched-traffic run would be needed
to say anything quantitative about phase B.
