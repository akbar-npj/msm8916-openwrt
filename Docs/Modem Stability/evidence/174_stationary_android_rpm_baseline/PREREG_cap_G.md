# Pre-registration — capture G (7th MCPM window)

Written **before** the capture ran, 2026-09-22, device on stock Android,
`/proc/uptime` ≈ 6986 s (≈ 116 min) at the time of writing.

## Why

Doc 174 §9.8 currently says the MCPM rate "drifts ~30 % across a 65-minute
session (1.63 → 2.17 /s)". Laying the six existing windows against AP uptime
does **not** support "drift":

| cap | AP uptime | trimmed rate |
| :-- | --: | --: |
| A | 2244–2545 s (37–42 min) | 1.629 /s |
| B | 3730–3851 s (62–64 min) | 2.165 /s |
| C | 4800–5040 s (80–84 min) | 2.075 /s |
| D | 5092–5333 s (85–89 min) | 2.071 /s |
| E | 5385–5625 s (90–94 min) | 2.100 /s |
| F | 5678–5918 s (95–99 min) | 2.125 /s |

Five windows spanning 62–99 min agree to **2.6 %**; A is a single low outlier at
37–42 min. The shape is **a rise before ~60 min, then a plateau** — not a
monotonic drift. The distinction matters because "the rate drifts" is the
load-bearing reason §9.8 gives for the withdrawal of the corpus's
"Android 1.88 /s vs OpenWrt 0.83 /s" claim.

## Design

One 240 s capture, `traffic=base`, same driver and same recipe as C–F
(modem clock, trimmed to a common final 240 s, interval distribution).
Uptime ≈ 116 min, i.e. **well past** the 62–99 min plateau band.

## Predictions

* **G1 (plateau reading).** G lands in **[2.05, 2.15] /s** — the B–F band. Then
  the plateau holds over **62–116 min** (a 54-minute span, 5× the longest
  same-condition span measured so far) and A is a **lone low outlier**, so §9.8
  must be rewritten: the rate is *stable once past a startup transient*, and the
  withdrawal's reason becomes "both sides are single windows" **only** — the
  "drift" clause is deleted.
* **G2 (drift reading).** G ≥ 2.2 /s — the rate is still climbing at 116 min and
  "drift" survives as a description.
* **Falsifier for G1.** G < 1.9 /s. Then the plateau is not stable at 116 min
  either, and §9.8's "drift" wording stands (with the honest note that the
  low windows are not monotonic in time).

## Limits, stated in advance

* **n = 1.** One window can falsify G1 but cannot establish a plateau on its
  own; if G1 holds it *strengthens* an already-5-window band rather than
  creating one.
* The device has been up 116 min with unknown operator/radio activity between
  captures; only the kernel state is known to be unchanged (no reboot), so
  pooling against B–F is legitimate for a rate but **not** for cadence.
* A's own condition is unknown (it predates the traffic control, §9.7 P4 VOID),
  so G cannot settle *why* A is low — only whether A is isolated.
