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

This mechanism was proven causally by a single-constant A/B (§112.162): changing the wait
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
- **Lost on firmware restore — but re-appliable.** `sysupgrade -n` restores the baseband from
  the 64 MB `modem` partition via `msm-firmware-dumper`, silently reverting the patch. It can
  be made to **survive** `sysupgrade` by re-applying it automatically — see **§3**.
- **Not board-gated in software by itself.** As a raw byte change it is a property of the
  flashed image; the §3 hook adds a board gate.

---

## 3. Making the modem binary patch survive `sysupgrade` (Option A, if you choose it)

> This section exists so that a user who deliberately wants the **binary-patch** route has the
> wiring to make it survive a firmware reflash. The **shipped** choice is still Option B (§4).
> The retired implementation is recoverable from git — `git show 57024f2^:packages/msm-firmware-dumper/src/hmu05-patch-modem.c`.

**The problem.** `/lib/firmware` is **not** in the sysupgrade image — it lives on the writable
**overlay**. On the HMU05 the modem blobs are *dumped from the device's own `modem` partition*
by `msm-firmware-dumper` on first boot. A `sysupgrade -n` (no keep-config) wipes the overlay,
so `/lib/firmware` is re-created from the stock `modem` partition on the next boot. Any bytes
you patched by hand are therefore **lost** — unless the patch is **re-applied automatically**
each time the firmware is (re)provisioned.

**The mechanism the project used before** (retired in `57024f2`). Instead of trying to make
the patched bytes persist, the old `hmu05-patch-modem` patcher was **re-run on every
provisioning**:

1. A small HMU05-gated C program installed at **`/usr/sbin/hmu05-patch-modem`**, built by
   `packages/msm-firmware-dumper/Makefile` (`Build/Compile` →
   `$(TARGET_CC) … -o hmu05-patch-modem hmu05-patch-modem.c`; installed in `Package/…/install`).
2. **Hook 1 — the dumper.** `packages/msm-firmware-dumper/files/msm-firmware-dumper.sh` calls
   it right after it finishes copying the blobs into `/lib/firmware` and just before `sync`:
   ```sh
   if [ -x /usr/sbin/hmu05-patch-modem ]; then
     /usr/sbin/hmu05-patch-modem "$FW" && log "HMU05 modem patch check completed"
   fi
   ```
3. **Hook 2 — first boot.** `msm89xx/base-files/etc/uci-defaults/99-msm89xx-firstboot` calls
   it as belt-and-braces:
   ```sh
   if [ -x /usr/sbin/hmu05-patch-modem ]; then
       /usr/sbin/hmu05-patch-modem /lib/firmware
   fi
   ```

Because the dumper is a **one-shot guarded by the `/lib/firmware/DUMPED` marker**, the sequence
after a `sysupgrade -n` is: overlay wiped → marker gone → dumper re-dumps stock blobs from the
`modem` partition → **patcher re-applies the patch + re-signs** → marker set → reboot → the
modem loads the patched image. The patch "survives" because it is **re-derived** on every
provisioning, not because the bytes persist.

**What the patcher did — the structure to copy:**

1. **Gate on the board** — `is_hmu05_board()` checks `/tmp/sysinfo/board_name`,
   `/proc/device-tree/model`, `/proc/device-tree/compatible`; exit 0 otherwise.
2. **Locate** `modem.b16`, `modem.mdt`, `modem.b01` under the firmware dir (`argv[1]`, default
   `/lib/firmware`); exit 0 if any is absent.
3. **Idempotency** — if the patch site already holds the patched bytes, exit 0 (so re-runs are
   free and the two hooks can both fire).
4. **Patch `modem.b16` in place.**
5. **Re-sign** — recompute SHA-256 of the patched `modem.b16` and write it into the segment
   hash table (below).
6. Return; the caller does `sync`.

**Adapting it for Option A.** The retired patcher wrote the *retracted* No-Sleep bytes at
`modem.b16` file offset `0x001117e0`. For the imei-wait deferral you change **step 4** only,
and keep **step 5**:

| | Retired (No-Sleep) | **Option A (imei wait)** |
|---|---|---|
| `modem.b16` file offset | `0x001117e0` | `0xc0d5f160 − 0xc0287000 = 0x00AD8160` |
| stock bytes | — | `9f 64 00 00 00 c0 00 78` (`r0 = ##0x927c0` = 600000 ms) |
| patched bytes | `00 c4 00 78 00 c0 9f 52` | `ff 7f ff 07 e0 c7 00 78` (`r0 = ##0x7fffffff` = 0x7fffffff ms) |

**Re-signing (identical for both).** The firmware uses a per-segment SHA-256 table.
`modem.mdt = modem.b00 ‖ modem.b01` and `len(modem.b00) = 916 (0x394)`. Segment *n*'s hash
lives at `modem.b01 + 0x28 + 32·n`, i.e. `modem.mdt + 0x3bc + 32·n`. Since Option A changes
only segment **16** (`modem.b16`):

- `modem.b01` @ `0x0228` (`0x28 + 32·16`) ← SHA-256(patched `modem.b16`)
- `modem.mdt` @ `0x05bc` (`0x394 + 0x228`) ← the same 32 bytes

(The retired patcher wrote exactly these two offsets; the current Option-A builder
`scratch/build_imei_wait.py` writes `b01 @ 0x228` and rebuilds `modem.mdt = b00 ‖ b01` — the
same table. It additionally re-hashes segment **5** because the deployed test base image also
carried a `modem.b05` change; on a **clean stock base only segment 16 changes**.)

**Caveats.**

- **MPSS auth.** `modem.b01` carries an RSA signature + certificate chain after the hash table.
  A wrong hash — or editing a segment you did not re-hash — makes the modem die with
  `MPSS authentication failed: -19`. Always re-hash **every** segment you changed.
- **Keep the idempotency check in sync** with the patch bytes, or a re-run double-applies.
- **The retired code is recoverable**: `git show 57024f2^:packages/msm-firmware-dumper/src/hmu05-patch-modem.c`,
  and the `Makefile` / `msm-firmware-dumper.sh` / `99-msm89xx-firstboot` wiring in the `57024f2`
  diff. Re-adding it means restoring the `Makefile` build+install and the two hooks above.
- **Do not ship it on a device-agnostic branch.** The reason it was removed (`57024f2`) is that
  `staging-main`/`main` are device-agnostic and must not carry a baseband patcher; if you go this
  route, keep it HMU05-gated (the patcher already gates itself).

---

## 4. Option B — pure-software `himi-ok-guard` (CHOSEN)

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

## 5. Comparison

| Dimension | **A** — firmware binary patch | **B** — `himi-ok-guard` (chosen) |
|---|---|---|
| Nature | **Deferral** (deadline → ~24.9 days) | **Root-cause** (deadline never arms) |
| Modifies baseband? | Yes (`modem.b16` + re-sign `b01`/`mdt`) | **No** |
| MPSS-auth risk | Yes | **No** |
| Survives `sysupgrade -n`? | Not by itself — **only with the §3 dumper/firstboot re-apply hook** | **Yes** (in the image; nothing to re-apply) |
| Board scope | Flashed image (per-device) | **HMU05-gated init** (others untouched) |
| Reversible? | Re-flash stock firmware | `uci set …himi_ok_enabled=0` |
| Verified by | §112.162 A/B + §112.171 hash | §112.169 + §112.170 A/B |
| Shipped in the tree | No (experiment only) | **Yes** |

---

## 6. Decision and rationale

**Chosen: Option B — the pure-software `himi-ok-guard`.**

1. **It fixes the cause, not the clock.** Option A leaves the faulty `memcmp` and merely
   moves the deadline 24.9 days out; B makes the comparison succeed so the deadline never
   arms.
2. **No baseband modification ⇒ no MPSS-auth exposure.** The project's standing rule is to
   never blind-patch a baseband. B needs no re-signing and cannot trip the
   `MPSS authentication failed: -19` trap.
3. **It survives `sysupgrade` inherently.** B lives in `base-files` inside the image and is
   re-enabled by `97-himi-ok`, with nothing to re-apply. A is reverted by any `sysupgrade -n`
   / firmware restore and only survives if you also re-add the §3 re-apply hook — which itself
   re-introduces a baseband patcher into a device-agnostic branch.
4. **Gated and reversible.** The init is `board_name`-gated to `*hmu05*`, so no other board
   is affected, and it can be disabled with a single UCI key.
5. **Android parity.** With the guard, `a2_pin=0` is safe, matching the stock Android
   configuration.

**Role of Option A going forward:** it is retained only as the *decisive experiment* that
proved the mechanism (§112.162) and as a fallback diagnostic. It is **not** part of the
shipped fix, and no baseband patcher (`hmu05-patch-modem`) remains in the tree.

---

## 7. Operational notes

- The guard's deadline-relevant work happens in the window before the RF task reads item
  2500 (modem-up ~299 s on the test image); the default 20 s interval leaves ample margin.
- If the modem bearer is torn down by the watchdog, the guard keeps running (it is an
  independent procd service) and rewrites the item after the next modem boot.
- `sysupgrade -n` restores the baseband from the `modem` partition; with the guard in the
  image, no re-patching is required afterwards.

---

## 8. SOP compliance statement

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
- **Retired patcher:** the §3 re-apply mechanism is documented from the git history of the
  removed `hmu05-patch-modem` (`57024f2`); it was **not** re-added to the tree.

---

## 9. Achieved vs Expected

| Goal | Expected | Achieved |
|---|---|---|
| Stop the ~900 s fatal | no fatal past 900 s modem-up | ✅ guard ON: 0 crashes through modem-up 902 s (§112.169); cold A/B 0 crashes (§112.170) |
| No baseband modification | shipped fix touches no firmware | ✅ AP-side userspace only |
| Survive `sysupgrade` | fix persists across a clean reflash | ✅ in `base-files` + `97-himi-ok` |
| Board isolation | HMU05 only | ✅ `board_name`-gated init |
| Causality proven | move the fatal by one constant | ✅ §112.162: `600000→60000 ms` moved the fatal to 360.687 s |
| Reversibility | disable without reflashing | ✅ `uci set modem-watchdog.recovery.himi_ok_enabled=0` |

---

## 10. Evidence pointers

- **§112.162** — decisive A/B: the one-constant change that moved the fatal.
- **§112.165–§112.168** — the `memcmp` / NV-item-2500 root cause.
- **§112.169** — guard ON vs no-write control on the 60000 ms image.
- **§112.170** — cold-boot A/B; `a2_pin` (not cold-vs-warm) is the gate; Android parity.
- **§112.171** — promotion of the guard from test-only into the image.
- **Artifacts:** `msm89xx/base-files/usr/sbin/{himi-ok-guard,diag_nv}`,
  `msm89xx/base-files/etc/init.d/himi-ok`,
  `msm89xx/base-files/etc/uci-defaults/97-himi-ok`;
  Option A tooling `scratch/build_imei_wait.py`, `scratch/patch_imei_max/`.
- **Retired Option-A wiring (survive-`sysupgrade` hook, §3):** commit `57024f2` (removal) and
  its parent `57024f2^` (the `hmu05-patch-modem.c` source, `Makefile`, `msm-firmware-dumper.sh`
  hook, `99-msm89xx-firstboot` call).
