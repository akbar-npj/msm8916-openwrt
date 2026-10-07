# HMU05 ~900 s Modem Fatal — Fix Options and the Decision

**Status:** ✅ CURRENT — this is the authoritative record of *how* the famous ~900 s
modem fatal is fixed and *why* this fix was chosen over the alternative.
**Date:** 2026-10-07
**Board:** HMU05 (`hmu05,250605v0s`) — MSM8916 / Snapdragon 410
**Branch:** `main` (staging: `staging-main`)
**Ledger:** `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md` §112.162, §112.165–§112.171

> **In one line.** There are two ways to stop the ~900 s fatal: **(A)** patch the modem
> firmware to push the deadline ~24.9 days out, or **(B)** rewrite one NV item from the
> AP after every modem boot so the deadline never arms. **We chose (B) — the pure-software
> `himi-ok-guard`** — because it fixes the root cause, touches no baseband, and survives
> `sysupgrade`. Option (A) was used as the decisive *experiment* that proved the mechanism,
> then retired.

---

## 1. The root cause (what the ~900 s fatal actually is)

The modem's RF task (`rf_task`, handler `0xc0d5efd0`) reads **NV item 2500**
(`NV_FACTORY_DATA_4_I`) into the buffer at `0xc310dcb0` and `memcmp()`s it against the
literal **`"HiMI_OK"`**:

- **Match** → the task proceeds normally.
- **Mismatch** → the task takes its *"get imei will stop"* branch and waits
  (stock **600000 ms = 600 s**) before the ML1 fatal that the ~900 s deadline is built on
  (`lte_ml1_common_timer.c:390`).

Two facts make this a *recurring* failure rather than a one-off:

1. **Item 2500 is cleared at every modem boot.** Its status is `0x0000` (PRESENT) but its
   128 bytes are all zeros. An AP that never rewrites it leaves the modem to fatal ~900 s
   after **any warm restart** — a crash-recovery SSR or a deliberate clean one.
2. **The item is not in EFS.** It is reachable only via **raw DIAG NV** (`0x26` read /
   `0x27` write over `/dev/rpmsg0`, tool `diag_nv`). The EFS `/nv` tree (1 155 entries,
   rfnv ids ≥ 20086) does not carry it; `"HiMI_OK"` is a firmware literal in `modem.b18`.

The ~900 s figure decomposes as **~300 s anchor + 600 s wait ≈ 900 s** (modem clock).

This mechanism was proven causally by a single-constant A/B (see §3): changing the wait
from `600000` ms to `60000` ms moved *both* the "stoped" message *and* the fatal from
~900 s to **360.687 s**.

---

## 2. Option A — modem-firmware binary patch (extend the wait to ~24.9 days)

Change the RF task's event-wait timeout constant so the deadline lands ~24.9 days out
instead of ~15 minutes.

| | |
|---|---|
| **Site** | `0xc0d5f160` in `modem.b16` (VA base `0xc0287000`; file offset `0xc0d5f160 − 0xc0287000`) |
| **Stock bytes** | `9f 64 00 00 00 c0 00 78` = `r0 = ##0x927c0` = **600000 ms** |
| **Patched bytes** | `ff 7f ff 07 e0 c7 00 78` = `r0 = ##0x7fffffff` = **2 147 483 647 ms ≈ 24.855 days** |
| **Re-sign** | re-hash segments 16 and 5 into `modem.b01` (hash header `0x28`, stride 32), rebuild `modem.mdt = modem.b00 ‖ modem.b01` |
| **Tooling** | `scratch/build_imei_wait.py` |
| **Deployed** | 2026-10-06; device hash-verified (`modem.mdt d78d1f2f…`); AP up 31 min / modem ≈1860 s clean ⇒ new deadline ≈24.9 d |

**What it is:** a **deferral**, not a fix. The deadline still arms; it just fires ~24.9 days
later. It also carries every cost of touching the baseband:

- **MPSS authentication risk.** `modem.b01` carries an RSA signature + certificate chain
  after the hash table; a wrong segment hash makes the modem die with
  `MPSS authentication failed: -19`. (Related trap: editing some `modem.bNN` *files* breaks
  auth even when `b01` hashes verify.)
- **Lost on firmware restore.** `sysupgrade -n` restores the baseband from the 64 MB `modem`
  partition via `msm-firmware-dumper`, silently reverting the patch.
- **Not board-gated in software.** It is a property of the flashed firmware image, not of
  the OS build.

---

## 3. Option B — pure-software `himi-ok-guard` (CHOSEN)

Rewrite NV item 2500 to `"HiMI_OK"` from the AP shortly after every modem boot. The RF
task's `memcmp` then passes and **the deadline never arms** — the root cause is removed,
not postponed.

| | |
|---|---|
| **Guard** | `msm89xx/base-files/usr/sbin/himi-ok-guard` — polls `/dev/rpmsg0` via `diag_nv`, reads item 2500, writes `"HiMI_OK"` (`48694d495f4f4b`) only when it is not already set |
| **NV tool** | `msm89xx/base-files/usr/sbin/diag_nv` — raw DIAG NV `0x26`/`0x27` over `/dev/rpmsg0` |
| **Service** | `msm89xx/base-files/etc/init.d/himi-ok` — procd, `board_name`-gated to `*hmu05*`, `START=97` |
| **Enable** | `msm89xx/base-files/etc/uci-defaults/97-himi-ok` (enables on HMU05 first boot) |
| **UCI** | `modem-watchdog.recovery.himi_ok_enabled` (default **1**), `modem-watchdog.recovery.himi_ok_interval` (default **20 s**) |

**Why poll rather than hook a boot event:** continuous re-checking covers cold boot *and*
every SSR/crash restart with no dependency on a restart-detection signal, and self-heals if
the modem clears the item again. Steady-state cost is one NV read per cycle.

**Verification:**

- **§112.169** — on the 60000 ms test image, writing `HiMI_OK` every 15 s through the
  RF-task read (modem-up ~299 s) gave **0 restarts / no fatal through modem-up 902 s** with
  LTE fully up; the no-write control **fataled**.
- **§112.170** — cold-boot A/B, both at `a2_pin=0`: no guard → `lte_ml1_common_timer.c:390`
  at modem ~363 s; guard ON → **0 crashes**. Android runs `a2_pin=0`, so
  **`a2_pin=0` + guard = Android parity**.
- **§112.171** — the guard was initially test-only; it is now in the image and restored by
  `sysupgrade` (a plain copy-main flash had regressed it, which is what prompted the
  promotion to `main`).

---

## 4. Comparison

| Dimension | **A** — firmware binary patch | **B** — `himi-ok-guard` (chosen) |
|---|---|---|
| Nature | **Deferral** (deadline → ~24.9 days) | **Root-cause** (deadline never arms) |
| Modifies baseband? | Yes (`modem.b16` + re-sign `b01`/`mdt`) | **No** |
| MPSS-auth risk | Yes | **No** |
| Survives `sysupgrade -n`? | No (firmware restored) | **Yes** (in the image) |
| Board scope | Flashed image (per-device) | **HMU05-gated init** (others untouched) |
| Reversible? | Re-flash stock firmware | `uci set …himi_ok_enabled=0` |
| Verified by | §112.162 A/B + §112.171 hash | §112.169 + §112.170 A/B |
| Shipped in the tree | No (experiment only) | **Yes** |

---

## 5. Decision and rationale

**Chosen: Option B — the pure-software `himi-ok-guard`.**

1. **It fixes the cause, not the clock.** Option A leaves the faulty `memcmp` and merely
   moves the deadline 24.9 days out; B makes the comparison succeed so the deadline never
   arms.
2. **No baseband modification ⇒ no MPSS-auth exposure.** The project's standing rule is to
   never blind-patch a baseband. B needs no re-signing and cannot trip the
   `MPSS authentication failed: -19` trap.
3. **It survives `sysupgrade`.** B lives in `base-files` inside the image and is re-enabled
   by `97-himi-ok`; A is silently reverted by any `sysupgrade -n` / firmware restore.
4. **Gated and reversible.** The init is `board_name`-gated to `*hmu05*`, so no other board
   is affected, and it can be disabled with a single UCI key.
5. **Android parity.** With the guard, `a2_pin=0` is safe, matching the stock Android
   configuration.

**Role of Option A going forward:** it is retained only as the *decisive experiment* that
proved the mechanism (§112.162) and as a fallback diagnostic. It is **not** part of the
shipped fix, and no baseband patcher (`hmu05-patch-modem`) remains in the tree.

---

## 6. Operational notes

- The guard's deadline-relevant work happens in the window before the RF task reads item
  2500 (modem-up ~299 s on the test image); the default 20 s interval leaves ample margin.
- If the modem bearer is torn down by the watchdog, the guard keeps running (it is an
  independent procd service) and rewrites the item after the next modem boot.
- `sysupgrade -n` restores the baseband from the `modem` partition; with the guard in the
  image, no re-patching is required afterwards.

---

## 7. SOP compliance statement

- **Firmware modification:** none in the shipped fix. The baseband was **not** modified for
  Option B; the guard is AP-side userspace only.
- **Dual-firmware comparative grounding:** the mechanism was grounded against the stock
  HMU05 ground truth (live connected coredump + `modem_full_decompiled.c`) — the `memcmp`
  site, the `"HiMI_OK"` literal, and NV item 2500's presence/zero-fill were read from the
  firmware, not guessed.
- **Option A experiment:** the test firmware image was built with the tracked tooling
  (`scratch/build_imei_wait.py`), hash-verified on-device, and rolled back after the run.
  The A/B is recorded in §112.162.
- **Re-signing / hash tooling:** `b01` segment hashes were recomputed with the same scheme
  used by the verification tool; verdict recorded as device-side md5 match
  (`modem.mdt d78d1f2f…`).
- **Errors caught:** the guard was initially test-only and a plain flash regressed it
  (§112.171) — recorded and corrected by promoting it into the image; and an earlier
  cold-vs-warm reading of the gate was corrected to `a2_pin` (§112.170).

---

## 8. Achieved vs Expected

| Goal | Expected | Achieved |
|---|---|---|
| Stop the ~900 s fatal | no fatal past 900 s modem-up | ✅ guard ON: 0 crashes through modem-up 902 s (§112.169); cold A/B 0 crashes (§112.170) |
| No baseband modification | shipped fix touches no firmware | ✅ AP-side userspace only |
| Survive `sysupgrade` | fix persists across a clean reflash | ✅ in `base-files` + `97-himi-ok` |
| Board isolation | HMU05 only | ✅ `board_name`-gated init |
| Causality proven | move the fatal by one constant | ✅ §112.162: `600000→60000 ms` moved the fatal to 360.687 s |
| Reversibility | disable without reflashing | ✅ `uci set modem-watchdog.recovery.himi_ok_enabled=0` |

---

## 9. Evidence pointers

- **§112.162** — decisive A/B: the one-constant change that moved the fatal.
- **§112.165–§112.168** — the `memcmp` / NV-item-2500 root cause.
- **§112.169** — guard ON vs no-write control on the 60000 ms image.
- **§112.170** — cold-boot A/B; `a2_pin` (not cold-vs-warm) is the gate; Android parity.
- **§112.171** — promotion of the guard from test-only into the image.
- **Artifacts:** `msm89xx/base-files/usr/sbin/{himi-ok-guard,diag_nv}`,
  `msm89xx/base-files/etc/init.d/himi-ok`,
  `msm89xx/base-files/etc/uci-defaults/97-himi-ok`;
  Option A tooling `scratch/build_imei_wait.py`, `scratch/patch_imei_max/`.
