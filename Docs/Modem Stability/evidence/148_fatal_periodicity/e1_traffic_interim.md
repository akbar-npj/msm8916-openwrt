# E1 — sustained-traffic experiment (interim)

**Question:** does continuous traffic change the deterministic 903.674 s fatal?
**Method:** 1 Hz `ping` to 8.8.8.8 across the boundary at AP uptime 5433.141 s. One variable,
one period. Baseline (idle) is Doc 148 §3.
**Started:** 2026-09-20T19:07:18Z, AP uptime 5309.93 s. Duration 2100 s.
**Log:** `scratch/e1_traffic/e1_traffic.log` (gitignored)

## Result so far — the pattern broke

| # | AP uptime (s) | interval (s) | signature |
| ---: | ---: | ---: | :--- |
| 1 | 914.769287 | — | `lte_ml1_common_timer.c:390` |
| 2 | 1818.443149 | 903.673862 | `lte_ml1_common_timer.c:390` |
| 3 | 2722.117987 | 903.674838 | `lte_ml1_common_timer.c:390` |
| 4 | 3625.792806 | 903.674819 | `lte_ml1_common_timer.c:390` |
| 5 | 4529.467002 | 903.674196 | `lte_ml1_common_timer.c:390` |
| **6** | **5426.331445** | **896.864443** | **`a2_power.c:1189`** |

Traffic began at AP ~5310 s; fatal #6 came 116 s later.

**Two things changed at once, under traffic:**
1. the **signature** — `lte_ml1_common_timer.c:390` (5/5 idle) became `a2_power.c:1189`;
2. the **period** — 903.674 s (spread 1.08 ppm over four intervals) became **896.864 s**, i.e.
   **6.81 s short**.

SSR recovery was normal: fatal 5426.331445 -> stopped 5426.391585 -> up 5427.699310 (~1.37 s),
and a **5th** `wwan0` address appeared (`10.97.92.60`).

## The falsifiable prediction (recorded BEFORE the next fatal)

E1 continues to AP ~7410 s, so it covers the next boundary. Two competing models give
**different, 1-ms-resolvable** predictions from fatal #6 at 5426.331445:

| model | predicted next fatal |
| :--- | :--- |
| traffic did NOT change it; #6 was an anomaly and idle cadence resumed | 5426.331445 + 903.674429 = **6330.005874 s** |
| traffic DID change it; the new cadence persists under traffic | 5426.331445 + 896.864443 = **6323.195888 s** |

The two differ by **6.81 s** — comfortably resolvable given the measured 1 ppm stability.
**Whichever lands decides whether the AP's traffic pattern selects the fatal mechanism.**

## Why this matters

This is the first evidence that the fatal is **not one immutable timer**. It unifies two things
the corpus recorded as confusing:
* three signatures with three *different* ~900 s periods (902.230 / 903.674 / 905.5 s, Doc 148 §6.2);
* the same signature appearing at wildly different times across boots (`a2_power.c:1189` at
  172 / 918 / 1824 / 2729 s).

If the AP's activity pattern selects which of several ~900 s timers expires first, both
observations fall out of one model — and it makes the AP the lever, which is what the
Android-vs-OpenWrt evidence already implies.

## Caveat

n = 1 for the traffic case. A single early fatal with a different signature could also be a
natural rotation. The prediction above is what distinguishes them.
