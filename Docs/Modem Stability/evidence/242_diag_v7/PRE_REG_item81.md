# PRE-REGISTRATION — item 81: the 168× gap discriminator (v7 + TRAFFIC)

Written **before** arming the reader and starting the run. Firmware: v7 (unchanged;
`modem.b05` md5 `1e6271991fe8ca9ab431141f0f6d6df0`, verified resident).

## The question

Ledger §80.5: v6 counted **4** calls to `FUN_c02fda90` over a 900.606 s window; v7 counted
**674** over 902.29 s — a **168×** gap on the **same `modem.b16`** (same two call sites).
Three candidates, none established: **(a) regime**, **(b) instrument quality**, **(c) a
different fatal signature**.

## Design (frozen before the run)

Hold the **instrument** fixed (v7, the proven ring — its count is internally corroborated by
a **no-gap** seq range) and change **only the regime**: run **sustained ping traffic**
(`ping -c 1 -W 2 8.8.8.8` every 4 s on the device, the item-71/78 pattern) through the boot
instead of idle. Arm **one** device-local `/dev/ramdump_modem` reader; capture at the event
(a fatal auto-fires the reader; a wedge is cleared with a `restart`, which also produces a
dump). Read the ring with `read_diag_ring.py`.

* v7 boot reference `Brought out of reset` at device uptime **`67196.070352`** (the 4th beat
  of the running limit cycle; fatals at 65373.79 / 66288.99 / 67193.99 — all ~902 s apart).
* Expected event ≈ `boot+902` ⇒ device uptime ≈ **`68098`**.

## Predictions (scored only after a capture)

* **P-81a (capture).** A complete 85 443 284 B dump is obtained at the event.
* **P-81b (instrument ran).** Ring `magic == 0xc0030560` and `count > 0`.
* **P-81c (★ THE DISCRIMINATOR — mutually exclusive).**
  * **count ≲ 50** ⇒ **(a) REGIME**: traffic suppresses the mobility-evaluator path
    (~0.004 Hz), so §63.5's "dormant" is correct *for the traffic regime* and the v7 idle
    reading is the outlier regime.
  * **count ≳ 300** ⇒ **(b) INSTRUMENT**: the path is active in *both* regimes ⇒ v6's
    counter under-counted (its base is the poison fill `0xdeadc0fe`; its page has a live
    writer from `+0x080`) and §63.5's "dormant" is simply wrong.
* **P-81d (branch).** Per item 78's 2×2 (v6+ping → WEDGE), the event is **expected to be a
  WEDGE**, not a fatal. If so, the ring is read at the wedge (post-restart).

## Falsifier / void conditions

* **V-81a.** If the ping log shows the data path never carried traffic (rx flat, all pings
  failing from the start), the test is **VOID** (the regime was not actually changed).
* **V-81b.** A crash-loop or a magic mismatch ⇒ the instrument is broken ⇒ revert to v6.

## Scope / honesty

* **n = 1**, one device/SIM, one regime. Exploratory.
* The ring has **no in-cave clock**, so only the **count** (not the per-call timing) is
  compared. The windows differ slightly (v6 900.606 s vs this run's actual window), so the
  comparison is of **rate**, not of raw count.
* `rmnet` index varies per boot — this boot's data interface is **`rmnet0`** (verified from
  `ip route`), not `rmnet1`.
