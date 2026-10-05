# Changelog

All notable changes to this project are documented in this file.

The primary source of truth for the modem-stability investigation is
`Docs/Modem Stability/197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md` (a living
document; each §-entry is a changelog entry). This file records commits that
modify that ledger, for quick reference.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### 2026-10-06 — §112.140 v56 RESULT: key-3's timer spec carries interval 0x2EE00 = 10.000 ms; (a) MATCHED — the period WAS reprogrammed

- **Run.** Natural event at modem-up **906 s**, `devcd37`, pre-emptive SSR disabled. `SAVE.seq = 128803`,
  `spec_last = 0xc2cda7e0`, `key_last = 3`. The modem **booted cleanly** on the mid-function patch (LTE up, ping OK).
- **Decisive measurement** (filtered on the ML1 key-3 context `0xc20f1068`): **123 records, one spec
  `0xc2cda7e0`, interval `0x2EE00` = 192 000 = 10.000 ms for all 123**, all in the single terminal `t`-group —
  exactly matching the 123 dispatches v54/v55 saw. Deadline-step top = `0x2EE00` ×121. **(a) MATCHED.**
- **Honesty.** H2/H4 "FAIL" are **scoring artifacts**: the engine is **generic** (non-ML1 timer contexts also carry
  the magic; `+0x38` is the ML1 key only for ML1 contexts). The second "key-3" is `ctx 0xc21837b8` / spec
  `0xc2cd8980` with a **1 s** interval — a different timer. Caveat: the ring covers only the terminal window, so
  the interval *history* is inferred from the rate (v52 census key-3 = 906 over ~900 s ≈ 1 Hz vs 100 Hz here).
- **Recorded caller** `0xc0913618` = the engine's own loop site, **not** the armer. **No** writer of `spec+0x38`
  exists in the engine (`0xc0913000..0xc0914000`) ⇒ the interval is set elsewhere.
- **Next (v57):** ring the **writer of `spec+0x38`** / the "start timer" path for key-3's spec `0xc2cda7e0`.
  Root cause **OPEN**. Rollback to stock follows.

### 2026-10-06 — §112.139 v56 PRE-REGISTRATION: the ARM-path ring — the ML1 timer RESCHEDULE interval

- **Why.** §112.138 closed the caller question (key-3 shares `0xc0916ff4`; only expired contexts fire) ⇒ key-3's
  **deadline** was set to 10 ms; §112.136 showed the handler never re-arms ⇒ the arm is in the **timer engine**.
- **Engine.** Function @`0xc0913370`: `r18` = SPEC; `+0x30` base/deadline, **`+0x38` INTERVAL (the period)**,
  `+0x40` last-fire, `+0x88` the ML1 context. At `0xc09137ec` it stores the next deadline; `r23:22` = the interval.
  `interval == 0x2EE00` ⟺ 10.00 ms.
- **Hook.** Site `0xc09137ec` → PAD `0xc02cb2c4` → cave `0xc003054c` (**136 B**), re-executes the original store
  then jumps to `0xc09137f0`. Clobbers only `r6–r13`; a **liveness scan of the whole function** confirms they are
  dead (`r0,r16,r18,r19,r24–r27,r29,r31` are the live set).
- **Ring** (1024 × 48 B): `{seq, spec, iv_lo, iv_hi, dl_lo, dl_hi, ctx, key, caller, t}`. **P-V56:** H1–H4;
  **(a)** interval IS `0x2EE00` ⇒ period reprogrammed (find the writer of `spec+0x38`); **(b)** interval not 10 ms
  but rate ~100 Hz ⇒ the driver; **NEG** no key-3 records ⇒ re-target. Image md5
  `962eb17f0f30f7317c387e5b42409d4f`, VERIFY PASS (17 checks). Rollback now restarts the modem.

### 2026-10-06 — §112.138 v55 RESULT: the invoker/caller ring — key-3 shares the SAME caller as every other key ⇒ (a) the timer period was reprogrammed

- **Run.** Natural event at modem-up **906 s**, `devcd36` (85 398 475 B), pre-emptive SSR disabled.
  `SAVE.seq = 18902`. H1–H4 **PASS** (key-3 = 123 in the terminal group; key-3 `obj = 0xc20f1068` single).
- **Caller decoded.** `f_b4 = 0xc0916ff4` (distinct=1, 1024/1024 code VAs) = **the caller**, the *same* for every
  key. `f_ac = 0xc02d7bd0` = the caller's `r17` = the **registered callback** (confirms the indirect-callback
  model; **0** direct `call ##0xc02d7bd0` in `modem.b16`). Caller site = the instruction after `callr r17`
  (`r17 = memw(r19+#0x1c)` callback, `r0 = memw(r19+#0x30)` arg) at `0xc0916ff0` — **one** dispatch loop.
- **Why (a) is decisive.** The 3 non-terminal `t`-groups (~26 records, ≈1.28 s apart) contain **no key-3**; only
  the terminal group (178 records, a single `t`) has the 123 key-3 firings ⇒ the loop dispatches **only expired**
  contexts ⇒ key-3's **deadline itself** was set to 10 ms. `w20` step median = `0x2EE00` = 10.000 ms (re-confirmed).
- **Next (v56):** the **arm path** (writer of key-3's deadline / producer of its dispatch record) is the target.
  Root cause **OPEN**. Rollback to stock follows.

### 2026-10-06 — §112.137 v55 PRE-REGISTRATION: the invoker/caller ring (is key-3's 100 Hz a timer-period change or an event source?)

- **Adds the caller of the dispatcher** to the verified v54 instrument: the frame window `r29+0xa8..0xb4` that
  `allocframe(#0xb0)` saved the caller's return address into, each load in its **own** packet (fixes the v53
  read-before-write bug). Same site `0xc02d7bec` / PAD / cave as v52–v54.
- **Ring** (1024 × 48 B): `{seq, key, handler, obj, w1c, w20, w28, f_a8, f_ac, f_b0, f_b4, t}`.
- **P-V55:** H1–H4; **(a)** caller SAME for key-3 and others ⇒ timer-period change (hunt the arm); **(b)** caller
  DIFFERS ⇒ event/message source; **NEG** no frame word is a code VA ⇒ frame offset wrong.
- **Honest note:** the literal arm function (`0xc0913370`) is only a **hypothesis** (it arms from a *global* spec)
  and its entry packet is messy; the invoker ring is the robust first step. Image md5
  `4eb4566dd2c0ad700e59f9de5815e68e`, VERIFY PASS (16 checks). Pre-emptive SSR disabled; `a2_pin=1`.

### 2026-10-06 — §112.136 STATIC MAP: the ML1 dispatcher is a thin jump table; every handler sends an ML1 message; key-20's fatal is a flag-gate check

- **Dispatcher `FUN_c02d7bd0` = 0x20 bytes**: `key = memub(obj+0x38); handler = memw(table[key]); jumpr handler`.
  No timer work in the dispatcher ⇒ the period is set by the **arm path**.
- **Every handler builds+sends an ML1 message** (`0xc02871ac` build / `0xc0287198` send / `0xc0879150` assert).
  key-0 → `0x41b041a`; key-3 → `0x4200409`, `0x43a0404`, `0x4050451`; key-20 → checks then asserts.
- **key-20 (fatal) = a flag gate**: asserts `:390` when `memub(gp+0x2d1) != 1` **and** bit 2 is clear in
  `gp+0x6584`/`gp+0x6588` — candidate root-cause flags.
- **Timer framework** (fn containing `0xc0913740`) reads a 64-bit **interval** from `r18+#0x38` and clears
  `obj+0x20`. **No 10 ms literal in `modem.b16`** ⇒ the key-3 period is **data-driven**.
- **Next (v55):** pin the arm function and ring `{key, interval, caller, t}`. Root cause **OPEN**.

### 2026-10-06 — §112.135 v54 RESULT: the key-3 storm is a LIVE context firing at a constant ~10 ms (100 Hz); the fatal key-20 dispatch is the ONLY dead-context dispatch

- **Run.** Natural event, modem-up **901 s**, devcd33. `SAVE.seq = 18854`. Device fatal at AP uptime 40083.39 s =
  `lte_ml1_common_timer.c:390`; modem auto-recovered. Image md5 `e223bd11…`.
- **H1–H4 PASS**: boots to the event; ring sane; terminal group 176 with key-3 = **123**; obj stable `0xc20f1068`.
- **★ ~100 Hz (10 ms).** `w20 = memw(obj+0x20)` is a shared monotone clock advancing by an exact **`0x2EE00` =
  192 000 ticks = 10.00 ms @ 19.2 MHz** between consecutive key-3 dispatches. 123 × 10 ms = 1.23 s ≈ the DRX
  cycle. `w28 = w20 + ~40` (dispatch entry/exit times; ~40 ticks ≈ 2 µs callback duration).
- **★ key-3 is LIVE** (`w1c = 0xc37300b0`), not poisoned. **(a) EXTERNAL is FALSIFIED** (v53 read the key/type
  id); **(b) MATCHED** — with the caveat that `+0x20/+0x28` are timestamp bookkeeping, so the decisive facts are
  the **rate** and the dead-context fatal, not the inside/outside label.
- **★ The fatal key-20 dispatch is the ONLY `0xdeaddead` dispatch in the whole 1024-record window**, and its
  fields are backwards (`w28 < w20`) — a dead, never-maintained context dispatched exactly once.
- **§112.132's "(a) EXTERNAL" retracted.** Root cause (what collapses the key-3 period to 10 ms / why key 20 is
  dead) **OPEN**.

### 2026-10-06 — §112.134 v54 PRE-REGISTRATION: corrected context-snapshot ring (packet-hazard fixed; records the key-3 timer's own fields)

- **Fixes §112.132's instrument bug.** v53's cave used `{ load r13; store r13 }` in **one** Hexagon packet, so
  each store wrote the **pre-packet** `r13` (the ring offset) and the caller field was lost. v54 splits each load
  and store into **separate packets** (`verify_v54.py` check "no load/store r13 packet hazard" = PASS).
- **Records the timer fields** `w1c=memw(obj+0x1c)`, `w20=memw(obj+0x20)`, `w28=memw(obj+0x28)` (not `w38/w3c`,
  which §112.133 showed are the key/type id).
- **P-V54:** H1–H4 as before; **(a)** fields all constant ⇒ EXTERNAL (confirms v53); **(b)** any changes ⇒
  mechanism INSIDE the context; **(d)** interval `(w28-w20)` constant ⇒ fixed re-arm, collapsing ⇒ runaway.
- **Timing:** image deployed + run started **19:54:14Z**; pre-registration written **~20:00Z**, **before** the
  ~900 s event — no v54 data seen. Image md5 `e223bd11bb54e3990498cfa5165e4aa5` (VERIFY PASS, 15 checks).
- Pre-emptive SSR **disabled** (natural event); `a2_pin=1`; rollback `scratch/deploy_v54.py --rollback`.

### 2026-10-06 — §112.133 CORRECTION: key-20 is NOT "poisoned" — a fixed set of 28 context objects; `0xdeaddead` is the normal non-live default

- **Supersedes §112.132's "structurally poisoned" claim**, which rested on an incomplete enumeration (the scanner
  matched only two magic values).
- **Fixed set of 28 objects.** Every one of **63** coredumps >40 MB contains **exactly 28** objects with
  `callback(+0x0c)==0xc02d7bd0` and `magic(+0x30) ∈ {0xfedcba9..0xfedcbae}` (keys `0..30 \ {5,16,19}`). (The
  §112.132 "9 contexts" was a filter artifact.) Object stride is **0x40**, but they are **not one key-indexed
  array** — they occupy several stable contiguous 0x40-stride runs: keys **20–28**, **6–11**, **1,13–15**, plus
  **9 singletons**; run membership is identical across dumps.
- **`+0x1c == 0xdeaddead` is the DEFAULT** — present in 20–28 of the 28 objects in every dump; only the **live**
  objects hold a real pointer. Key 20 is one of ~21 non-live entries.
- **`magic(+0x30)` is a 6-value STATE field**, written by firmware: `0xc0913830` writes `0xfedcbaa` (live,
  constructor); `0xc0913784` writes `0xfedcbab` gated on `(magic|4)==0xfedcbae`; **`0xc0913884` writes
  `0xdeaddead` at `+0x1c`**; the code range-checks `magic - 0xfedcba9 < 6`.
- **The LIVE set (`0xfedcbaa`) is the DRX-census set** `{0,1,3,12,13,14,15}` (31/63), with key 4 swapped in (9),
  both (8), neither (7) — the live slots are the actively-cycling timers. Key 20 is never live, but neither are
  keys 2, 6–11, 17, 18, 21–30.
- **Corrected conclusion:** no evidence of corruption/use-after-free; `0xdeaddead`/`0xfedcbab` are deliberate
  "not live" states. Surviving facts: 28 fixed context objects; ~7 live at a time; the fatal key-20 dispatch runs on a
  **non-live** object. **Root cause OPEN.** Tools: `scratch/scan_ctx.py` (widened to the full magic range in this
  follow-up), `scratch/_dis2.py`, `scratch/_find_magic.py`.

### 2026-10-06 — §112.132 v53 RESULT: the terminal key-3 storm is EXTERNAL (a static context re-dispatched); the key-20 fatal context is structurally POISONED (`0xdeaddead`)

- **Run.** Natural event (no SSR, no forced dump), modem-up **907 s**, `scratch/v53_capture/natural_devcd31.bin`
  (85 398 475 B). `SAVE.seq = 18617`, `key_last = 20`. Image md5 `2b0a0176…` (rolled back to stock this session).
- **H1–H4 ALL PASS:** boots to the event; ring sane (monotone seq, keys ⊆ v52 set); key-3 storm reproduced
  (terminal group **178**, key-3 = **124**); key-3 `obj` stable = `0xc20f1068`.
- **★ Exact reproduction of §112.130's terminal signature.** 34 groups by the cached timestamp: **33 regular cycles**
  (24–28 dispatches, keys `{0,1,12,13,14,15}` — no key 3, no key 20) stepping at `Δt = 24 575 864` ticks =
  **1.2800 s @ 19.2 MHz** (the LTE DRX cycle), then the **terminal group = 178** `{0:26,1:9,3:124,12:2,13:3,14:9,15:4,20:1}`.
- **★★★★★ Instrument defect found + corrected (honest negative).** The cave's three context-word stores used
  packets `{ r13 = memw(r16+#NN); memw(r12+#slot) = r13 }`; Hexagon reads a register written in the **same packet**
  as the **pre-packet** value ⇒ each store wrote the **previous** packet's r13, shifting the fields one slot. Actual
  layout: `ring+0x10` = ring offset (junk), `+0x14` = `obj+0x38`, `+0x18` = `obj+0x3c`, `+0x1c` = `t`. `memw(r29+0xb0)`
  was **never stored** ⇒ **the caller field does not exist** ((c)/(d) VOID). Reader corrected.
- **Scored decisions (corrected):** **(a) MATCHED — the context is CONSTANT ⇒ driven EXTERNALLY** (obj+0x38 = 3,
  obj+0x3c = 0 across all 124 key-3 records); (b) not matched; (c)/(d) VOID; NEG not triggered.
- **★★★ Structural finding.** One context object per key (obj ↔ key 1:1, 8 keys). All share vtable `0xc361a3f0`,
  `+0x0c = 0xc02d7bd0`, `+0x18 = 4`, `+0x30 = 0x0fedcbaa` — **except key 20**, whose `+0x18`/`+0x1c` are **`0xdeaddead`**
  and `+0x30 = 0x0fedcbab`: the fatal is dispatched through a **poisoned / never-initialised** context. Keys
  `{1,13,14,15}` sit at `0xc216bbe8 + k*0x40` (the known ML1 ctx-array stride).
- **Next:** capture the key-3 caller (own-packet load / saved r31) and/or hook the key-3 handler `0xc02d7cc4` and the
  key-20 arm path. Root cause remains **OPEN**.

### 2026-10-06 — §112.131 v53 PRE-REGISTRATION: a context-snapshot ring on the ML1 common-timer dispatcher (what drives the terminal key-3 storm?)

- **Why.** §112.130 localised the event to the terminal DRX cycle (key-3 storm → key-20 fatal). *What drives key 3*
  is unknown. v53 records, per dispatch, the **context object** (`r16`) and its header words, to discriminate an
  **external** driver (a static context re-dispatched) from an **internal** one (the context's state changing).
- **Ground truth resolved this session (static).** The dispatcher prologue is ONE 12-byte packet
  `{ call 0xc02d1140; r16 = r0; memd(r29+#-0x10)=r17:16; allocframe(#0xb0) }`; `0xc02d1140` is a `return 0` stub and
  the `call`'s r31 clobber is invisible (allocframe saves the original r31:30) ⇒ **`r16` = the incoming r0 = the
  context object** (resolves the earlier "r16 = 0" confusion — a multi-instruction-packet misread). **There is
  exactly ONE NOP sled ≥160 B in the whole image** (`b05 @ 0xc003054c`, 180 B) ⇒ the cave must be compact.
- **Instrument.** Site `0xc02d7bec` (byte-identical to v52) → PAD `0xc02cb2c4` → CAVE `0xc003054c` (**156 B**);
  clobbers only r7–r13, preserves r2/r16/r29/r31, ends `jumpr r2`. State in the v50-proven run: SAVE 16 B,
  CENSUS 256×16 B, **RING 1024 × 32 B `{seq, key, handler, obj, w38, w3c, caller, t}`**. Image md5 `2b0a0176…`.
- **P-V53 pre-registered:** H1 boots to the event; H2 ring sane (seq monotonic, keys ⊆ v52 set); H3 key-3 storm
  reproduced (≥50 terminal); H4 obj stable for key-3; **(a)** w38/w3c CONSTANT ⇒ external driver; **(b)** w38/w3c
  CHANGE ⇒ mechanism inside the context; **(c)** caller differs key-3 vs normal; **(d)** caller identical ⇒ fixed loop;
  **NEG** storm absent / obj invalid / (constant AND identical) ⇒ re-scope to the arm path. **Falsifier:** obj == 0
  for all records ⇒ the "r16 = the object" model is wrong.
- Offline **VERIFY PASS** (cave 156 ≤ 180, no `<unknown>`, 4 constants, all 8 ring store offsets, seg5/16/19
  sha256, `mdt=b00+b01`, regions zeroed). Root cause remains **OPEN**.

### 2026-10-05 — §112.130 v52 RESULT: whole-run ML1 common-timer dispatch census is SANE (H2 PASS); the ~900 s event is a terminal DRX-cycle storm (key 3) ending in the single key-20 dispatch that IS the ERR_FATAL

- **Run.** v52 built, offline-verified, deployed, run to the natural event (`scratch/v52_capture/natural_devcd29.bin`,
  devcd29, modem-up **907 s**, 85 398 475 B). `SAVE.seq = 18576` dispatch calls — the whole-run view v51 could not give.
- **H2 PASS (decisive).** The census read from the **v50-readback-proven** run is sane: 9 keys, counts 1…8831,
  **zero** ~3e9 contamination, no self-referential `last_seq`. ⇒ §112.121's fix works; the §112.121 falsifier is NOT triggered.
- **★★★ Full dispatcher map (assert table in `lte_ml1_common_timer.c`, resolved with `a2_descr.py`):**
  key 0→`:268`; 1/13/14/15→`:287`; 12→`:325`; **3→`:336/357/372`**; **20→`:390/411/417`**. Each normal case is
  *log → send → `if(status==0) clean` else `FUN_c0879150(descr)`*. **Key 20's case has NO status check — it calls
  `FUN_c0879150(0xc3c6c800 = :390)` unconditionally** ⇒ the key-20 dispatch **is** the fatal emission (why key 20 fired once in 18 576).
- **★★★★★ Terminal structure.** Ring grouped by the cached DRX-cycle timestamp: **77 regular cycles** (25–28 dispatches,
  `{0:≈13,1:4,14:4,13:2,15:2,12:1}`, step 24 575 868 ticks = **1.2800 s** = the LTE DRX cycle) then a **final cycle with
  178 dispatches** (6.8×), composition `{0:26,1:9,3:124,12:3,13:3,14:9,15:3,20:1}`, start **on time** (1.2760 s).
  **Key 3 is absent from all 77 regular cycles and fires 124× in the terminal cycle**; key 20 fires once, last (seq 18576 = `SAVE.seq`).
- **Scoring:** H1 PASS, **H2 PASS**, **H3 FAIL** (terminal storm), H4 PASS; (a) NOT MATCHED (no key genuinely stopped —
  k4 is a boot-era artifact, k13/k15 are artifacts of key-3's flood), (b) MATCHED, (c) MATCHED; **NEG NOT MATCHED**.
- **Interpretation.** The event is a **burst**, not a stopped timer: the terminal DRX cycle runs ~7× the normal rate,
  dominated by context key 3 (a working re-arm/retry loop — its own asserts at :336/357/372 did NOT fire), then the
  key-20 context unconditionally emits the fatal at `:390`. Root cause **OPEN**; next target = what drives key 3's
  terminal storm and what enters state-20 (`FUN_c02fda90`). Caveats recorded (key 3 also fired ~481× earlier, clustered).
- **Reversible:** `/lib/firmware` + modem restart only; **rolled back to stock** (`modem.mdt md5 1a6f9507…`) this session.

### 2026-10-05 — §112.129 v52 PRE-REGISTRATION: the corrected whole-run ML1 common-timer dispatch census (state moved into the v50-proven region)

- **Why.** §112.121 scored v51's per-key **census INVALID** (its region `0xc1cfe8a0` is used by the modem at
  runtime — counts ≈3.25e9, `last_handler` self-referential), and left an explicit fix: co-locate ALL state in
  the **v50-proven** run and verify by readback. v51's **ring** was valid but covered only **9 s**, so the
  whole-run per-key view was never obtained. v52 recovers it.
- **Mechanism (byte-identical hook to v51):** `FUN_c02d7bd0` dispatch `0xc02d7bec` `{ jumpr r2 }` → `{ jump PAD }`;
  PAD `0xc02cb2c4` → CAVE `0xc003054c`; clobbers only `r7–r13`, ends `jumpr r2`.
- **★ The single variable vs v51 = the state region.** ALL state now lives in the v50-**readback-proven**
  run `0xc1d4c600..0xc1d5c600` (seg19): SAVE `0xc1d4c600` (16 B), CENSUS `0xc1d4c610` (256×16 B
  `{count, first_seq, last_seq, last_t}`), RING `0xc1d4d610` (2048×16 B). End `0xc1d55610 ≤ 0xc1d5c600`.
- **P-V52 pre-registered:** H1 boot+run to the event; H2 census **sane** (no ≈3e9 / self-referential);
  H3 regular terminal cadence; H4 key 20 present + rare; **(a)** a key's `last_seq ≪ SAVE.seq` ⇒ owner stalled;
  **(b)** key 20 fires ⇒ state-20 expiry observed; **(c)** all keys active to the end; **NEG** ⇒ the ML1 timer
  layer is healthy whole-run ⇒ trigger outside it. **Falsifier:** if the census is *again* contaminated at the
  proven run, no BSS region is safe ⇒ the zero-run ring method is unsound.
- Root cause remains **OPEN**. No build/deploy yet (pre-registration only).

### 2026-10-05 — §112.128 the three alternative "live peek" instruments are all NEGATIVE (log-on-demand, QMI, coredump); the "read the modem live" avenue is CLOSED

- **1. Modem-side log-on-demand (`DIAG_CMD_LOG_ON_DMND = 0x78`) — NEGATIVE.** `0x78` is defined
  (`diagchar.h:120`) but handled **AP-side only** (`diag_masks.c:880`, `diag_dci.c:1433` — a local
  echo `78 <code:2> 01`, request `78 <log_code_lo> <log_code_hi>`, code = 4-bit equip + 12-bit item)
  and **never forwarded to the modem** (grep: `0x78` appears nowhere else in `drivers/char/diag`).
  On the live CMD channel `77`/`78`/`79` are **recognized-but-silent** (no reply for any payload
  length) while all other unknown codes → `13 BAD_CMD`. ⇒ no log-buffer/memory dump this way.
- **★ Data-channel correction.** `/dev/rpmsg0` returns a `60 10 …` **log record** to both raw and
  HDLC sends (its RX is the F3 stream, a rolling counter), **not** a command reply; the 58-byte VERNO
  is obtained on the **CMD** channel. §112.127 §4's "stray HDLC success on rpmsg0" is corrected to the
  CMD channel.
- **2. QMI — NEGATIVE.** Enumerated the 15 libqmi services (`dms dsd fox ims imsa imsp loc nas pbm qos
  uim voice wda wds wms`); `fox` = Foxconn firmware-version. Probed DMS/NAS/WDS/DSD: host-visible state
  only (online / external-source / registered LTE / RSRP −92 / SNR 19.8 / WDS connected / DRX unknown).
  **No ML1/MCPM/timer state; no memory read. IMS/IMSA → `InvalidServiceType`** (not instantiated,
  consistent with §112.99). Note: `--dms-foxconn-change-device-mode=fastboot-*` is a non-SSR
  device-mode lever (future recovery option).
- **3. Coredump / live RAM read — NEGATIVE.** Modem = `remoteproc0` (`4080000.remoteproc`); debugfs
  exposes only `coredump`/`crash`/`recovery`/`resource_table` — **`crash` is the only capture path**
  (destructive). No minidump, no restart-level. The modem carveout `mpss@86800000` is **XPU-protected:
  `devmem 0x86800000` → Bus error** (reconfirms Doc 172 §1 / Doc 239 §153: the AP cannot read modem
  RAM by any route). ⇒ no non-destructive snapshot.
- **Aggregate.** The ~900 s trigger cannot be read from a *running* modem by any of these routes; the
  productive family remains the **firmware cave/ring + crash-coredump read** (Doc 239/240, v26–v51) —
  the event must be **caught at the fatal**, not peeked at beforehand. Root cause remains **OPEN**.

### 2026-10-05 — §112.127 the DIAG COMMAND CHANNEL is solved (DIAG_CMD + raw); the DIAG dispatch decoded; NO memory-read opcode exists

- **★ The missing instrument is built.** The modem exposes a dedicated request/response SMD channel
  **`DIAG_CMD`** (`F_DIAG_REQ_RSP_CHANNEL`), separate from the DIAG data channel. `diag-bind` never
  bound it, so `/dev/rpmsg2` did not exist. Bound it: **raw DIAG command bytes written to
  `/dev/rpmsg2` are answered on `/dev/rpmsg2`**; `/dev/rpmsg0` carries only the log/F3 stream and
  never answers. Requires an active mask session (`cntl-enable-range`), and the reply queue lags one
  request (drain before send). `packages/diag-bind/files/diag-bind` now binds `DIAG_CMD` too.
- **★ Framing corrected.** The old "send RAW, HDLC is rejected" note is **FALSE**. The DATA channel
  answers an HDLC-framed VERNO (`7e 00 0f 1e 7e` → the 58-byte `00 "Nov 25 2025…EAAAANUZ:" …` reply);
  the CMD channel wants **bare** bytes. HDLC recovered from `diagchar_hdlc.c`: CRC-16/CCITT-FALSE
  (0x1021/0xFFFF, sent inverted, lo-hi), escape `7e`/`7d` as `7d ^ (b^20)`, leading+trailing `7e`.
- **`diag_logtool` extended** (`packages/diag-logtool/src/diag_logtool.c`): `req`, `hdlc`,
  `capture-send`, `DIAG_DEV`; `read_reply` accumulates reads, splits on `0x7e`, skips the
  `10/11/79/92` stream, `expect=0` = any non-stream.
- **★★★ DIAG dispatch decoded.** Top-level accepted: `00 01 0c 0f 19 1a 1b 1c 1d 1f 24 26 2c 2d 2f
  30`; everything else → `13 BAD_CMD`. **The memory/IO commands are GONE — `02/03/04 PEEKB/W/D`,
  `05/06/07 POKE*`, `08/09 OUTP*`, `0a/0b INP*` all → `0x13`.** `DIAG_SERV (0x4b 12)` valid:
  `02 03 04 05 09 0a 0b 0c 0d 0e 0f 10 11 12 29` (they are the `diagdiag` factory memory *stress*
  commands, not a read); `4b 13 01 00` (FS) returns a real payload.
- **Conclusion.** **No DIAG memory-read opcode exists on this build** — so the §112.107 hoped-for
  "peek at ~800 s vs ~910 s" is **NOT achievable via DIAG**; the decisive test needs another
  instrument. Also: the Android userspace tools (`diag_mdlog`, `libdiag.so`, `test_diag`,
  `diag_dci_sample`) are 32-bit bionic (won't run on aarch64 musl) and logging-only, and the AP
  kernel driver is a transport pipe — **so porting the Android stack was the wrong target.**
- **Intermittency explained:** a second modem fatal at ap `17:56:11` (watchdog count 17→18, site
  `:Excep :0:`) SSR'd the modem and **destroyed the DIAG_CMD binding** mid-session.

### 2026-10-05 — §112.126 the live DIAG memory-peek is NOT exposed at cmd 0x0000 (scoped negative); a live post-fatal WEDGE recovered by the watchdog's Stage-3 SSR

- **DIAG memory-peek route tested (the §112.107 "missing capability").** Transport validated first
  (`diag_logtool selftest` VERNO round-trip OK). Probing `DIAG_SUBSYS_CMD_F (0x4B)|subsys|cmd=0x0000` for
  subsys `0x00..0x20` mapped the subsystems: valid = `04,05,08,09,0a,0b,0e,0f,12,13,1b,1c` (`0x13`=EFS,
  known-good); **subsys `0x00` (the classic `DIAG_SUBSYS_DIAG_SERVICES` memory-read path) returns `0x13`
  BAD_CMD.** ⇒ **no memory-read service at cmd 0x0000.** Scope: closes only cmd 0x0000; a read behind a
  different cmd/subsys is NOT excluded. Blind-probing valid subsystems with unknown cmds was **declined**
  (SOP: no blind commands at the baseband). The route needs a decoded opcode, not a drop-in tool.
- **F3-mask hypothesis closed.** `scratch/f3cap.sh` already arms `cntl-enable-range 256` (full F3 space),
  so the ~43 msg/s of `f3_900` is the modem's **emitted** rate — the "NO F3 PRECURSOR" finding stands.
- **Live post-fatal WEDGE observed + recovered.** Device found ~1036 s into a modem boot in the §112.108
  one-way state (modem `running`, `wwan0` up on the Arm-C IP `10.139.113.132`, route present, `ping`
  100 % loss, no coredump). Pre-emptive SSR **off** (deliberate). `modem-bearer-watchdog` ran Stage 1
  (`wds-go-dormant`) → Stage 2 (bearer rebuild) → **Stage 3 SSR SUCCESS at ap 30011.2**, restoring data
  (`10.47.253.94`, ping 0 % loss) and **re-arming** the ~900 s window. ⇒ a wedge is SSR-recoverable.

### 2026-10-05 — §112.125 A/B/C Arm C (patch + 2 s keepalive) = FATAL at modem-up 915.8 s (soak false-CLEAN from a dmesg ring-wrap) ⇒ ★ the ~900 s event occurred in ALL THREE arms; ★ a quick SSR recovery makes a fatal look "clean" to a ping soak

- **Arm C actually FATALED** at modem-up **915.81 s** (`lte_ml1_common.c:324`, crash **#23**; `restart_count`
  20→21; SSR recovered in ~1.5 s; netifd rebuilt the bearer → new IPv4 `10.139.113.132`). The soak reported
  `CLEAN` — **false**, because its `grep -c` fatal count stayed 16 when the dmesg ring wrapped (new fatal
  displaced an old line). The only soak-visible trace was the IP change.
- **★★ A/B/C conclusion — the event occurred in ALL 3 arms:** A stock = FATAL @900.96 s; B patch-only = WEDGE
  @~900 s; C patch+2 s keepalive = FATAL @915.8 s. ⇒ **neither the no-sleep patch nor the 2 s keepalive prevents
  the ~900 s event.** The report's "completely resolves the crash" is **FALSE**.
- **★ Measured recovery (Arm C, wall-clock calibrated):** remoteproc up **+1.49 s**; BAM/ports +1.7/+1.9 s; netifd
  bearer setup +9.2 s; **bearer/interface up +13.2 s**; keepalive (2 s) **still failing at +16.2 s and +21.2 s**;
  soak ping OK at +25.2 s. ⇒ **modem ≈1.5 s but the data path is down ~15–25 s** (userspace bearer rebuild). The
  Arm-C miss was **primarily the ring-wrap fatal-count mask**, secondarily the 30 s ping sampling straddling the
  ~20 s outage. **A loss-only metric is insufficient; the fatal counter (`crash #N`) is the dependable detector.**
- **★ Masking mechanism (the report's likely error):** a fatal with a **quick modem recovery** is easy to miss in a
  ping/loss soak ⇒ `0.0 % loss` over 20 min is not evidence of stability. **A stability soak MUST count fatals
  (last-timestamp or `crash #N`), not just loss.**
- **Caveat:** n=1/arm; manifestation varies run-to-run ⇒ no arm-to-arm causal claim from n=1.
- **Harness fix:** `scratch/soak_dev.sh` fatal detection now compares the **last fatal line** (timestamp), not a
  count. Re-uploaded. Device rolled back to stock + keepalive OFF.

### 2026-10-05 — §112.124 A/B/C Arm B (no-sleep patch alone) = WEDGE at ~900 s (fatal gone, data path still dies ⇒ reproduces main's Run 2); soak false-CLEAN fixed; Arm C (patch + 2 s keepalive) launched

- **Arm B verdict = WEDGE, not CLEAN.** Soak printed CLEAN only because its 8-fail wedge threshold wasn't reached
  before the mu=1100 target (only 6 fails) — the link was dead and never recovered. Independent evidence: **no new
  dmesg fatal** (last `:4054` @25556.94 is before Arm B's boot @26432.65), `state=running`, `wwan0` holds
  `10.110.7.180`, **ping FAILS**, `devcd: disabled` (no coredump). Onset between mu=879 (OK) and mu=910 (FAIL).
- **Meaning:** the no-sleep patch removes the ML1 DSP **fatal** but **not** the ~900 s event — it changes
  **FATAL → WEDGE**. This **exactly reproduces `main`'s Run 2**. ⇒ the user report's "completely resolves the
  crash" is **false as a stability claim**; the modem still dies at ~15 min.
- **Harness fix:** `scratch/soak_dev.sh` `WEDGE_FAILS 8→5` + target-time link check (target reached while down =
  WEDGE). Re-uploaded.
- **Arm C launched:** patch + 2 s keepalive (config `enabled=1, interval=2, enable_recovery=0`, verified running).

### 2026-10-05 — §112.121 CORRECTED (v51 census CONTAMINATED but ring CLEAN ⇒ dispatcher NEG; `:324` is NATURAL, not instrument-induced) + §112.123 Arm-A control = FATAL at 900.96 s (`:324` reproduced on stock ⇒ 6th natural signature) + Arms B/C moved to a device-side soak

- **§112.121 correction:** the Arm-A control reproduced `lte_ml1_common.c:324` on **stock** at modem-up 900.96 s ⇒
  v51's fatal was **natural**, NOT instrument-induced (the first version's signature argument is falsified). What
  stands: the **CENSUS** region `0xc1cfe8a0` is **contaminated at runtime** (readback = self-referential pointers
  `0xc1cffXXX`, counts ≈3.25e9) ⇒ census invalid. The **RING** (`0xc1d4c600`, v50-proven) is **CLEAN** — 4096 recs,
  keys `{3:3284,0:395,14:132,1:132,15:50,13:56,12:39,4:8}`, **no key 20**, regular cadence, span 9.0 s ⇒
  **P-V51 NEG for the dispatcher's tail**. Lesson: **"zeroed in the image" ≠ "unused at runtime"**.
- **§112.123 Arm A (stock control) = FATAL reproduced.** dmesg crashes #20/#21/#22 (all stock, post-rollback) at
  modem-up **900.96 / 900.70 / 900.61 s** — 3/3 boots evented. **`:324` is a NEW natural 6th signature** (not in
  the 42-dump corpus). Base rate remains ~100 %.
- **Harness fix:** the host soak was killed at mu=867 s (33 s before the fatal) by a session/network interruption ⇒
  Arms B/C run **on the device** (`scratch/soak_dev.sh`, `setsid`-detached, `/root/soak_<label>.log`), robust to
  host/SSH loss; FATAL via dmesg count, WEDGE via 8 consecutive ping failures.
- **Arm B deployed + running:** no-sleep blob verified (`3006927c…`), daemon ON, keepalive OFF, coredump enabled.

### 2026-10-05 — §112.121: v51 (ML1 dispatch census) — see the CORRECTED entry above; §112.122: `main`-branch no-sleep patcher audit (opcodes mis-labeled `#-1` vs actual `#0x20`; target is the `ENABLE_SLEEP_REQ` handler; daemon was a crash trigger on main, not a fix; main's own Run 2 → WEDGE at 15 min) + staged A/B/C blob test pre-registered (control running, patch blob built `3006927c…`, 2 s keepalive staged but OFF)

- **v51 result:** fatal at modem-up 919 s, signature `lte_ml1_common.c:324` — **NOT in the 42-dump corpus** ⇒
  instrument-induced. `SAVE.seq=84362` (dispatcher hot, ~92 calls/s); ring cadence healthy to the end (keys
  0/1/3/4/12/13/14/15; no key 20; no collapse). The `SAVE`/`CENSUS` region `0xc1cfe8a0` is in a `filesz>0` BSS
  segment ⇒ writes corrupted live modem state. **Fix for re-run:** relocate all state into the v50-proven zeroed
  run at `0xc1d4c600`. NEG branch **NOT scored** (confound invalidates the run).
- **`main`-branch audit:** the forwarded "patch + qcom-time-daemon resolves the crash" report is the
  already-quarantined no-sleep patch. Verified: opcodes `00 c4 00 78 00 c0 9f 52` = `{ r0 = #0x20 ; jumpr r31 }`
  (returns 32, **not** `-1`); target `0xc03987e0` = the `ENABLE_SLEEP_REQ` (msg `0x42b0200`) handler; stock bytes
  are a live prologue ⇒ the 8-byte write stubs sleep-enable. `main`'s own test log shows Run 2 (patch alone) →
  WEDGE at 15 min, and the final "stable" stack = patch + 2 s keepalive + daemon **OFF**.
- **Test pre-registered (blob A/B/C, no rebuild):** A=stock control (running), B=patch-only, C=patch+2 s
  keepalive. WEDGE detection load-bearing. Patched blob built + verified (`modem.mdt` md5 `3006927c…`). 2 s
  keepalive staged on device (`/root/modem-keepalive.2s`, orig backed up), service still OFF for the control.

### 2026-10-05 — #2 ML1 pivot (offline re-scope): no literal 900 s constant and no ML1 watchdog; the ONLY boot-anchored 1 Hz field in the 58 MB BSS is an UNREFERENCED counter at `0xc28c28a0+0x320·i+0x2c`; P-V51 (ML1 common-timer dispatch census) pre-registered (ledger §112.120)

- **Offline recon only** (stock image + 35-dump corpus; no device write, no patch, no run).
- **Re-scope:** the ~900 s family is **exclusively `lte_ml1`** at modem-uptime **900–903 s** (21 local boundary
  dumps) — the **ML1 SM/timer layer**; MCPM/RF/sleepmgr are victims (per §112.118/119, v32–v34, §112.66).
- **F1:** no literal 900 s constant drives it — `#0x384`/`#0x385` sites are memory offsets, a binary-search
  threshold table (`c0680744`), and a jump-table enum (`c0e79b78`/`c0ea6b80`); no `>900` ML1 watchdog exists.
- **F2:** the tracked disassembly covers exactly the **executable** segments (`0xc0000000..0xc1404e0c`, #0–#10); the
  rest is `rw-`/`r--` data, and #15/#20 are `filesz=0` BSS ⇒ the static blind spot is **data, not code**.
- **F3 (NEW LEAD):** a cross-dump scan for `|value − modem-uptime| ≤ 3` in *every* dump finds **exactly 3** offsets —
  `0xc28c28cc/0xc28c2bec/0xc28c2f0c` (stride `0x320`, base `0xc28c28a0`) — a **1 Hz seconds-since-boot counter**
  present in **both** the `a2_power` and the `lte_ml1` families. Struct: self-referential list head (+0x00/+0x04), a
  slowly-advancing cursor (+0x0c), a table pointer (+0x14), per-instance state (+0x20/+0x24/+0x28), the counter
  (+0x2c). **⚠ It is NOT referenced by any ELF immediate and nothing points to it** (whole-dump pointer scan = 6
  self/neighbour hits) ⇒ maintained outside the ELF's static reference graph (the known "second image") — recorded
  as a **lead, not a conclusion** (a 1 Hz logging/tracing client would look identical).
- **Instrument picked + pre-registered: P-V51 = an ML1 common-timer dispatch census** — an entry ring on
  `FUN_c02d7bd0` (`lte_ml1_common_timer.c:390`), recording `{seq, key=memb(obj+0x38), obj, t}` into a per-key census
  + a 4096-entry ring, to see whether a key stops, the state-20 watchdog spikes, or the cadence collapses at ~900 s.
- Root cause **OPEN**.

### 2026-10-05 — v50 MCPM collapse-issuer entry ring: H1/H3/H4 PASS, decision (a) MATCHED — the collapse is ISSUED + ENABLED + COMPLETING throughout ⇒ the MCPM/power layer is a VICTIM; the MCPM line is CLOSED and the search PIVOTS to ML1 (ledger §112.119)

- **Built, offline-verified, deployed and ran v50** — a single-site **ENTRY** ring on `FUN_c0cf8cd0` (the COLLAPSE
  ISSUER; site `0xc0cf8cd0`, cave 148 B @ `0xc003054c`, ring `0xc1d4c600`), recording
  `{seq, tag|state<<8|f3599<<16|f3598<<24, mask, t}` (`modem.mdt md5 = 3c099919ec1b0529a548fa2d6aab33d3`;
  `verify_v50.py` PASS; sha256 read-back PASS). Forced restart (14→15).
- **Run:** NATURAL fatal `lte_ml1_sleepmgr_stm.c:4054` ("Assert stm_get_state(LTE_ML1_SLEEPMGR_STM) == SLEEP
  failed"), task `slpc`, modem-uptime **900 s**; auto-coredump `devcd14` (85 398 475 B). Victim = `slpc`/sleepmgr
  (v47's variant), **not** `tmr_slave3` (v49).
- **RESULT:** `SAVE.seq = 863` (863/863 recovered); **state=0 for all; mask=0x3ff for all; `DAT_c3973599`==1 for
  ALL 863; `DAT_c3973598`==0 at EVERY entry**. Cadence = the DRX cycle (1.28 s long / 0.32 s short); span 880.2 s.
- **HEADLINE:** the collapse issuer is called **once per DRX cycle** and on **every** call the collapse is enabled
  (`f3599=1`), the previous collapse has completed (`f3598=0`), and the fatal is an **ML1/sleepmgr** assert — **not**
  `mcpm_saw.c:424`. ⇒ **the MCPM/power layer is a VICTIM** (direct observation, not inference). With §112.118 (vote
  healthy) and §112.111 (manifestation-independent), the **MCPM line is CLOSED**.
- **PIVOT:** the failure is outside MCPM — the fatal is the **ML1** layer (`lte_ml1_sleepmgr_stm.c:4054` /
  `lte_ml1_common_timer.c:390`). Next: the ML1 sleepmgr `SLEEP→WAKEUP` trigger `FUN_c039ef80`, or the outstanding
  **RF_WAKEUP_CNF omission on the terminal cycle** (v26/v27).
- Rolled back to stock (`1a6f9507…`) and reloaded the modem.

### 2026-10-05 — v49 MCPM SAW wake-source-mask ring: H1–H4 PASS but branches (a)/(b)/(c) ALL NOT MATCHED — the wake-source vote is HEALTHY right up to the fatal ⇒ the "stuck source blocks the collapse" hypothesis is REFUTED (ledger §112.118)

- **Built, offline-verified, deployed and ran v49** — a two-site **ENTRY** ring on the MCPM SAW vote functions
  `FUN_c0cf8e70` (tag 1 = source-IDLE / collapse path) and `FUN_c0cf8fa4` (tag 2 = source-ACTIVE / wake path),
  recording `{seq, tag|state<<8, mask, t}` into the 4096 × 16 B ring `0xc1d4c600` (`SAVE.seq` @ `0xc1455000`);
  cave 160 B @ `0xc003054c` (`modem.mdt md5 = 78f9a91e04a8c30790863abbdd81989a`; `verify_v49.py` PASS; sha256
  read-back PASS). Forced restart (count 11→12).
- **Run:** NATURAL fatal `lte_ml1_common_timer.c:390`, task `tmr_slave3`, at modem-uptime **902 s** (crash report
  "Uptime 0:15:02"); auto-coredump `devcd13` (85 398 475 B). Victim = `tmr_slave3` (v48's variant), **not** `slpc`
  (v47) — the variable-victim race again.
- **RESULT — H1/H2/H3/H4 PASS, but NONE of the pre-registered branches (a)/(b)/(c):** `SAVE.seq = 2645`
  (recovered 2645/2645); tags e70(idle)=1287, fa4(wake)=1358; mask histogram `0x3ff`×1288, `0x3ef`×1355 (+ two
  STARTUP transients `0x37f`/`0x3bf`); **Σ inter-record Δt = 900.4 s @19.2 MHz ≈ the 902 s run** ⇒ the ring spans
  the **whole** run. Only **bit 4 (0x10, LTE)** ever toggles; the other 9 sources stay idle all run. The last 12
  records alternate `e70(0x3ef)/fa4(0x3ff)` cleanly through seq 2645.
- **HEADLINE — the hypothesis is REFUTED:** the mask returns to `0x3ff` (all sources idle) on **every** cycle; the
  collapse condition `state==0 && mask==0x3ff` is reachable ~1322× over the run, including at the last `e70`
  (seq 2644). **No source is stuck ACTIVE** ⇒ the MCPM/power layer is a **VICTIM**, not the cause (consistent with
  §112.111 and §112.66). §112.111's "last event is a wake with no sleep" is trivial: the crash landed just after a
  wake, before the next sleep request — **not** a skipped sleep.
- **Next target:** the MCPM-side candidate is now *inside* the collapse issuer `FUN_c0cf8cd0` (the
  `FW_SLEEP_PWRDN_FULL` completion / `mcpm_saw.c:424` timeout); but the weight is on the cause being **outside
  MCPM (upstream in ML1)**.
- Rolled back to stock (`1a6f9507…`) and reloaded the modem (count 13).

### 2026-10-05 — v48 entry ring on the generic STM engine 0xc0fe1460: NEGATIVE (H3 FAIL) — the engine recomputes the state internally, so the entry's r0 is NOT the state; v13's write-packet hook is the correct instrument (ledger §112.117)

- **Built, offline-verified, deployed and ran v48** — an entry ring on the generic STM engine `FUN_c0fe1460`
  (the same function v13 hooks at its write packet), recording `{seq, r0, r1, ts}` filtered on
  `r0 == 0xc1e158d0 || r1 == 0xc1e158d0` (`modem.mdt md5 = ca3bb2b7d4b752684b6bb9434ec5bd69`;
  `verify_v48.py` PASS; sha256 read-back PASS). Clean restart (`restart_count` 8→9).
- **Run:** natural fatal `lte_ml1_common_timer.c:390` at modem-uptime **902.0 s** (a *different* victim than
  v47's `slpc` — the variable-victim race); auto-coredump `devcd12` (85 398 475 B).
- **RESULT — H1 PASS, H2 PASS, H3 FAIL:** `SAVE.seq = 288 591` (~320 calls/s — a hot generic framework
  function); 650 records in the last 4096 calls, all with exactly one OBJ field (always `r1`); but **`r0 = 0`
  (INACTIVE) for all 650** ⇒ a flat state sequence, NOT v13's cycle.
- **Cause (decisive, static):** `0xc0fe1654: r18 = r0` — the engine **recomputes** the new state internally;
  the entry's `r0` is a scratch/default (0). The write `0xc0fe1754: memw(r16+#0x4) = r18` stores the
  recomputed value, so the entry cannot log a transition. `r16` is never reassigned ⇒ the *filter* is sound;
  only the *state field* is wrong.
- **★★ Redundancy acknowledged:** v48's H3 was already answered by **v13** (§112.57.13/14), which hooked the
  write packet and captured the sequence at both variants (wedge `3→4`, live state 4; fatal `1→2`, live state
  2). v48 adds **no new physics** — it is a negative that validates v13's approach.
- **Task #293 (slpc line):** both stated leads are spent — **v42** ringed `slpc_arm` (armer = the sleepmgr, all
  `tech==4`, 1.28 s LTE DRX re-arm) and **v13** captured the STM sequence. The failure is **downstream of the
  sleepmgr's request** (the FW stops collapsing) ⇒ the open question is *why the MCPM/FW stops re-entering
  power-collapse*.
- Rolled back to stock (`1a6f9507…`) and restarted the modem (10) at the end of the session.

### 2026-10-05 — v47 entry ring on FUN_c101311c: called exactly 10x per run (=60/6) — v46 was CORRECT; the ~360x is a regime difference, not a hook bug; the :403 line is a SYMPTOM (ledger §112.116)

- **Built, offline-verified, deployed and ran v47** — an entry ring on `FUN_c101311c` (the `rflte_core_rxctl.c:403`
  emitter), recording `{seq, caller, arg0, arg1}` before the prologue clobbers `r31`
  (`modem.mdt md5 = b0493f252fcb15f208e84298a6ffd51d`; `verify_v47.py` PASS; sha256 read-back PASS).
  Clean restart (`restart_count` 5→6); natural fatal `devcd11` (85 398 475 B) at `modem_up = 903 s`.
- **RESULT: `SAVE.seq = 10`, `count = 10`**; last `{seq=10, caller=0xc1019a88, arg0=1, arg1=2}`;
  first `{seq=1, caller=0xc1019a88, arg0=0, arg1=2}`. **All 10 calls from ONE site `0xc1019a80`** (a 2-carrier
  loop run 5×).
- **The §112.115 discrepancy is RESOLVED — option (a), no hook bug:** `10 calls × 6 = 60` = v46's `count403`
  exactly. The F3 capture's 288 `:403` records (a 5.84 s burst) are a **different (busier) regime**, not the
  idle baseline.
- **Correction to §112.115's "~360×":** the `f3_900` capture is a masked subset (25 files, ~2174 msgs / ~50 s
  ≈ 43/s vs the full ~446/s) whose `:403` records sit entirely in a 5.84 s window — so "~360×" compared a
  *within-burst* rate to a *whole-run count*.
- **Census:** `rflte`/`mcpm`/`a2_power`/`pgi_msgr` **collapse together** (up903→908) while `cfm_cpu_monitor`
  stays flat ⇒ `:403` does not stop uniquely first; the RF-freeze line is a **SYMPTOM** and is de-prioritized.
- **v47 fatal signature:** `Task = slpc`, uptime 902 s (the known per-RAT wakeup manifestation, §112.100/101).

### 2026-10-05 — v46 line-403 table on the CORRECT wrapper 0xc08f1580: H2/H3/H4 PASS, but count403=60 is ~360x below the F3 capture (ledger §112.115)

- **Built, offline-verified, deployed and ran v46** — a one-constant retarget of v45's O(1) line-403
  last-seen table onto the wrapper the `:403` message actually uses, `0xc08f1580`
  (`modem.mdt md5 = f57b0fbcc3f3a35097bf336408ea8227`; `verify_v46.py` PASS; sha256 read-back PASS).
  Clean asynchronous restart (`restart_count` 4→5); natural fatal `devcd10` (85 398 475 B) at
  `modem_up = 902 s`.
- **RESULT: `SAVE.seq = 158 471`, `count403 = 60`**; `last403 {seq=111821, desc=0xc16ea334,
  caller=0xc1013438, arg0=5}`; `first403 {seq=8929, … arg0=0}`.
- **H3/H4 PASS (decisive):** the `rflte_core_rxctl.c:403` message **is** emitted via `0xc08f1580` by
  **`FUN_c101311c`**, exactly as §112.114 pinned statically. `arg0 = 0…5` confirms the 6-index loop
  (`count403 = 60 = 10 × 6` ⇒ ~10 `FUN_c101311c` calls).
- **⚠ OPEN — a ~360× rate discrepancy:** the F3 capture (`scratch/f3_900/c000171_up00898.raw`) holds
  216 `:403` records in 4.28 s (~50/s ⇒ ~8.4 `FUN_c101311c` calls/s), vs v46's 10 calls in 902 s.
  Either the runs differ in RX activity, or the hook misses most calls. Verified NOT a filter failure
  (only one descriptor in the image points at the `:403` fmt string `0xc44dd988` → `0xc16ea334`).
- **Next decisive test (running):** v47 hooks `FUN_c101311c`'s ENTRY and reports its call count + caller
  (`modem.mdt md5 = b0493f252fcb15f208e84298a6ffd51d`; `verify_v47.py` PASS; deployed + booted cleanly).

### 2026-10-05 — v45 F3 line-403 table: count403=0 — the hook covered 1 of ≥5 log wrappers; the :403 emitter is FUN_c101311c via 0xc08f1580 (ledger §112.114)

- **Built, offline-verified, deployed and ran v45** (same `FUN_c08f1610` hook; cave now keeps an O(1)
  line-403 last-seen record at `0xc1d4c600`; `modem.mdt md5 = 493392c6…`; `verify_v45.py` PASS;
  sha256 read-back PASS). Natural fatal `devcd9` (85,398,475 B) at `modem_up = 906 s`.
- **RESULT: `SAVE.seq = 411 552` (the cave ran) but `count403 = 0`** — no line-403 descriptor at this hook.
- **WHY (the real finding):** `0xc08f1610` is ONE entry point of an F3 log-wrapper family
  (`0xc08f1480`/`0xc08f1500`/`0xc08f1580`/`0xc08f1610`/`0xc08f16a0`/`0xc08f1840`, ~55,500 call sites
  total). The hook covered only 14,227 (~26%).
- **The `:403` emitter is now pinned statically:** the fmt string lives at `0xc44dd988` (b25); exactly one
  descriptor points at it — **`0xc16ea334`** `{packed=0x01930015 → line 403, level 21; word1=0xc44dd988}`,
  loaded at `0xc1013430` inside **`FUN_c101311c`** (the per-index RX-gain compute). Its packet calls
  **`0xc08f1580`** — not `0xc08f1610`. §112.112's level-1 candidates are **superseded**.
- **Next:** re-run the same table hooked on `0xc08f1580`.

### 2026-10-05 — v44 F3 log-wrapper ring: the hook is CORRECT, but the ring is too short (~446 calls/s) (ledger §112.113)

- **Built, offline-verified, deployed and ran the v44 ring** on the F3 log wrapper `FUN_c08f1610`
  @ `0xc08f1610` (pad `0xc08f1604`, cave `0xc003054c`, ring `0xc1d4c600`, `modem.mdt md5 = 326209ee…`;
  `verify_v44.py` PASS; sha256 read-back PASS). Clean restart via `msm_subsys/modem` (`restart_count` 2→3).
- **The hook target is CORRECT (verified by disassembly, not assumed).** Every log call site loads the
  descriptor address into `r0` then `call 0xc08f1610` (e.g. `0xc05763ac`, `0xc05af634`/`0xc05af668`,
  `0xc0664000`, `0xc039e114`, `0xc0b7ac38`, `0xc031bfd0`).
- **RESULT — the ring filled (404 343 calls in 906 s ⇒ ~446 calls/s), so 4096 slots cover only the last
  ~9.2 s.** Natural fatal `devcd8` (85 398 475 B) at `modem_up = 906 s`. In-window: 12 distinct
  descriptors, a tight 3-message crash storm (`0xc1655cb8`/`0xc1617f50`/`0xc1652068`); **all three
  `:403` candidates are ABSENT** — the `:403` stream stopped >9.2 s before the fatal. **H3/H4 NOT
  supported; the negative is a window limit, not an absence.**
- **Model correction:** `level` can exceed `0xFF` (it is a level *mask*); `logsite.py`'s `<=0xFF` guard
  is a string-pointer-anchor heuristic, not the descriptor model.
- **Next instrument (v45, built):** an O(1) "last-seen" record filtered on descriptor line == 403
  (`count403`/`last403`/`first403` @ `0xc1d4c600`) — spans the whole run with ~64 B.

### 2026-10-05 — v43 RF/LTE RX-control ring: the §112.111 target `FUN_c10169d0` is NOT the RX tick (ledger §112.112)

- **Built, offline-verified, deployed and ran the v43 ring** on `FUN_c10169d0` @ `0xc10169d0`
  (cave `0xc003054c`, ring `0xc1d4c600`, `modem.mdt md5 = 7b7f1dfe…`; `verify_v43.py` PASS;
  sha256 read-back PASS). The modem was on v42 (last reload predated the deploy), so a clean
  restart was forced via patch 826's `msm_subsys/modem` node — the write value is **`"restart"`,
  not `"1"`** (`restart_count` 1→2).
- **RESULT — the ring holds only 11 entries.** `FUN_c10169d0` runs **11× in 899 s** (≈0.012 Hz).
  It is a general "process this control struct" helper, **not** the periodic RX worker. The
  §112.111 target identification is **FALSIFIED**.
- **H2 NOT supported** (two callers: `0xc1016c68` ×8 with a stack-local ctx, `0xc1018740` ×3 with
  a BSS ctx). **H3/H3′ inconclusive** (n=11). Patch bytes confirmed inside the returned dump.
- **The real tick's rate measured:** `rflte_core_rxctl.c:403` is a 6-index loop emitted ~**7–8×/s**
  (36 calls/chunk in `scratch/f3_900`), stopping between `up903+1.548 s` and `up908` — ~600× the
  v43 target's rate. Emitter descriptor ∈ {`0xc1528fe4`, `0xc152d68c`, `0xc155a338`}; candidate
  function entry `0xc05af580`; exact site ambiguous statically ⇒ identify at runtime.
- **Reader bug found and fixed:** the counter is at `@0xc1455000+0`, not `+4`; re-read gives 11
  (consistent with the ring).
- **Next:** hook the F3 log wrapper `0xc08f1610`, ring `{seq, desc, a1, a2, a3, caller}`, to name
  the `:403` emitter at runtime, then ring **that** entry. Root cause remains **OPEN**.

### 2026-10-05 — Why the wake never arrives, part 2: the MCPM cycle is DRX-driven and stops after a WAKE — a correction to §112.110 (ledger §112.111)

- **The MCPM FW sleep/wake cycle is a DRX-driven power cycle.** Repeating every **0.31–0.32 s** in
  healthy short DRX: `mcpm_npa.c:702 ldo17 freq CB` (tech 6) → `mcpm_saw.c:591 FW_WAKE-UP_Start`
  (**8–11 µs**) → `mcpm_npa.c:1322 No Imm CLKCPU req` → `mcpm_saw.c:393 FW_SLEEP_PWRDN_FULL 5` →
  `mcpm_npa.c:1286/1361 Sched CLKCPU req`. FW awake ~34 ms, asleep ~280 ms. **Trigger = the NPA
  `ldo17` (CX-rail) frequency callback, NOT the IRAT path** (corrects §112.110 Finding 2's implication).
- **DRX transition:** inter-wake interval **0.32 → 1.28 s** (LTE short→long DRX) at the boundary;
  `mcpm.c:3109 tech wakeup_req` appears **only** in the long-DRX regime and **only in the fatal**
  (the wedge holds 0.32 s with no `mcpm.c:3109`).
- **CORRECTION to §112.110.** The **last MCPM event is a completed `FW_WAKE-UP_Start` with NO
  following `FW_SLEEP_PWRDN_FULL`** — the FW is left **awake** and the cycle stops. §112.110's
  "…then the terminal `FW_SLEEP_PWRDN_FULL`" was the last *complete* cycle (`up908`), not the true
  terminal (`up913`). "The wake never arrives" is mis-stated: the wake arrived one last time; the
  cycle did not continue.
- **The terminal signature is COMMON to the fatal and the wedge:** `ldo17 CB → FW_WAKE-UP_Start →
  A2 power req (client 3) → CXM WWAN_TECH_MSG → rf_task.c:336 "get imei stoped" → heartbeat only`,
  no sleep. Manifestation-independent ⇒ the common shutdown, not the differentiator.
- **Collapse order (census):** RF/LTE RX control (`rflte_core_rxctl` + `rflte_mc_meas`) stops first,
  then the CM/SDSS/TRM IRAT reconfig (`trm_config_handler.c:1012 "SRLTE is enabled"`,
  `sdss.c:18454 "** Activate GWL opr script **"`), then MCPM/A2/CXM ~9 s later; the app-core
  heartbeat (`cfm_cpu_monitor.c:308`, 50 ms) never stops.
- **SRLTE/GWL reconfig** at the RF stop is **fatal-only in the pair** but **not time-locked** in a
  second fatal-family capture (`f3_soak/fail9584`) ⇒ causal role **UNPROVEN**.
- **Next (revised):** a live ring on the **RF/LTE RX-control task** (`FUN_c10169d0` / `FUN_c101311c`)
  — the MCPM wake-path ring of §112.110 is now lower priority (the MCPM cycle is a slave). Root **OPEN**.

### 2026-10-05 — Why the wake never arrives: the MCPM 10-source wake vote + a coredump NEGATIVE (ledger §112.110)

- **§112.110 — the wake mechanism, named.** The MCPM FW sleep/wake is a **10-source vote**:
  `DAT_c3973594` is the wake-source bitmask, **all-idle = `0x3ff`**; the FW power-collapse
  (`FUN_c0cf8e70`) is issued only when every source is idle; a wake (`FUN_c0cf8fa4`, which logs
  `mcpm_saw.c:591 FW_WAKE-UP_Start`) happens when a source **clears its bit**. Per-tech mask table
  at `0xc1e0d320` (read from a coredump): tech 0→0x01 … 4→0x04 (LTE) … 6→0x10 … 9→0x80.
- **The request path:** `FUN_c0ce3f78` (MCPM **IRAT / mode-change** handler) → `FUN_c0cd4500`
  (aggregates 14 tech entries, stride `0x17c`) → `FUN_c0ce1664` (per-tech, gated by
  `DAT_c3963d87 + tech*0x4d0`) → `FUN_c0cf8fa4`.
- **NEW assert sites (candidate, NOT the known ~900 s sites):** `mcpm_saw.c:424` (the FW-collapse
  10-try timeout in `FUN_c0cf8cd0`) and the `fws_sleep.c` SAW state machine (2→3→4→5) asserting at
  every transition (`:253/257/258`, `:407/409`, `:493/495/502`, `:542/548/549`).
- **Request-side, not service-side.** `mcpm.c:3109 tech wakeup_req` (tech 4 = LTE) appears **only at
  the boundary** (0 → 3 → 4 → 1) and its last occurrence is immediately followed by the **last**
  `FW_WAKE-UP_Start` ⇒ the last wake *did* fire; **no further request arrives**.
- **NEGATIVE (important):** all **6 coredumps** give a **byte-identical** MCPM state
  (`DAT_c3973594 = 0x3ef`, `DAT_c3973590 = 0`) ⇒ the dump is taken **after the crash handler quiesced
  the MCPM**; a coredump **cannot** answer "why the wake didn't arrive". Only a **live** wake-path
  ring can.
- **Next:** a live ring on `FUN_c0cf8fa4` / `FUN_c0cd4500` / `FUN_c0ce1664`. Root cause **OPEN**.

### 2026-10-05 — #3 "Hunt the RF freeze": the RF is NOT an independent failure; it is the first step of a staged FW wind-down (ledger §112.109)

- **§112.109 — the RF freeze, named.** The RF's first-to-stop signal is `rflte_core_rxctl.c:403`
  (`rflte_core_rxctl_update_rx_gain_freq_comp_to_mdsp: Gain_offset[%d]= %d`), emitted 6× + 1×
  `rflte_mc_meas.c:1943` per tick (~7 calls/s). In the 350 s dense capture **3444/3444** records carry
  `Gain_offset = 0` — the compensation update is a **no-op** on this board.
- **The ordering is real:** `rflte` stops at **60 %** of the up903 chunk while MCPM/power/coex continue to
  **94–97 %** (verified by both last-stream-index and last-`ts`) ⇒ rflte stops **≈1.7 s before** MCPM/power.
- **A staged wind-down:** the MCPM FW sleep/wake cycle steps **0.320 s → 1.280 s** (LTE short→long DRX,
  ×4) at the boundary, `mcpm.c:3109 tech wakeup_req` (tech 4 = LTE) appears (0 → 3/4/1), then a CM/SDSS
  burst (`SRLTE is enabled`, SDSS operator-script activations) and `rf_task.c:336 "get imei stoped"`.
  Terminal state = `mcpm_saw.c:393 FW_SLEEP_PWRDN_FULL` with **no wake**; only the 20 Hz
  `cfm_cpu_monitor` heartbeat + VADC continue ⇒ the app core is alive, the FW is dead (the §112.86
  unmatched-`SleepEntry` signature).
- **Verdict:** the RF does **not** fail independently — the RF RX task stops first *within* a staged FW
  wind-down. The RF-freeze hypothesis is **NOT SUPPORTED**; patching the RF task would not touch the root
  cause (it is downstream and a no-op). Root cause **OPEN**.
- ⚠ **Parser bug found+fixed:** the session's ad-hoc F3 parsers read `ts` at `k+3` (inside `na`) — counts
  unaffected, timestamps were garbage; switched to the proven `f3parse.py` (`ts` at `k+5`).
- ⚠ **Confound stated:** the `f3_v15` wedge capture has no LTE-RX activity at all, so the fatal-vs-wedge
  comparison is confounded and is recorded as a limit, not a result.

### 2026-10-05 — #2 config-perturbation probe: NEGATIVE (no config lever); plus the post-fatal restart leaves the modem in a one-way low-power trap (ledger §112.108)

- **§112.108 — live config probe.** Found the device in a post-fatal zombie state: fatal `lte_ml1_common_timer.c:390`
  at AP 914.5 s (modem-uptime ~902 s) → crash-recovery restart → modem returned in DMS `shutting-down`, NAS
  `not-registered-searching`, RF `none`, MM `No modems were found`, and stayed that way **4 777 s**.
- **Natural experiment (strongest evidence):** a modem with the LTE stack OFF ran **4 777 s with no fatal** —
  the longest clean modem-uptime seen on this device ⇒ the fatal is **LTE-stack-gated** (extends P-PMOS3's
  1 126 s `offline`).
- **Config levers all failed to perturb or move the anchor:** DRX is get-only (network-controlled);
  `--3gpp-set-packet-service-state` → `QMI protocol error (3) 'Internal'`; `--3gpp-register-in-operator` →
  `WrongState … while modem is connected`; `--set-current-bands=eutran-3` → accepted but a no-op (already
  band 3). Prior: `offline` suppresses but kills the data path; `low-power` does not suppress; `a2_pin` does
  not touch the 900 s fatal. ⇒ **no config keeps LTE data up and suppresses the fatal.** #2 = NEGATIVE.
- **NEW (feeds #3): the post-fatal restart's outcome is VARIABLE.** Case A (n=1): fatal `lte_ml1_common_timer.c:390`
  → restart → the modem returned in the one-way low-power trap (Doc 205 §6: DMS `shutting-down`, RF `none`, MM
  `No modems were found`) and stayed dead **4 777 s**; only a **cold reboot** restored LTE. Case B (n=1, same
  day): fatal `lte_ml1_sleepmgr_stm.c:4054` → **identical AP-side recovery sequence** → MM re-probed and LTE was
  `connected` in **~24 s**. ⇒ the difference is the MODEM's post-restart state (a firmware race), not an AP
  defect. Consequence: a reactive recovery must **detect the trapped branch and re-drive**. Candidate mechanism
  (UNTESTED): `a2_pin=1` holds the AP A2 vote SET across the restart; the normal ~16 s rebuild predates
  `a2_pin`. Next: test `a2_pin=0` × post-fatal recovery.

### 2026-10-05 — #1 boundary-state hunt: NO distinct ~900 s trigger in the corpus; the one boundary-correlated structure is the common shutdown (ledger §112.107)

- **§112.107 — offline cross-dump discriminator scan** (`scratch/_v43_discrim.py`, 36 coredumps: 21 boundary
  `lte_ml1`@900–903 s, 8 pre `a2_*`@68–575 s, 7 post `a2_*`@909–1041 s) found **370 perfect discriminators**
  but **no distinct trigger**: 88 boundary-correlated (counters + 4 simple latches), 280 failure-mode assert
  artifacts.
- **The only structured boundary-correlated object is a drained QuRT timer/scheduler pool** at `0xC3479a00`
  (256×0x10 records, pool header magic `0x0fedcba9`; the same magic the timer code writes at `c0914adc` /
  checks at `c0913748`). Uniformly released at the boundary in **both** the `lte_ml1` and `a2_power` dumps ⇒ it
  is the **common shutdown** (a symptom), not the trigger.
- **Key limitation:** the corpus is **crash-time only**; there is no pre-boundary dump of a doomed run, so the
  pre-vs-post comparison is confounded. The decisive boundary hunt needs a **live pre-boundary snapshot**
  (the on-demand capture forces a crash) — e.g. a DIAG memory-peek at ~800 s vs ~910 s.
- **900 s timer re-confirmed a non-trigger:** it fires ~25 s AFTER the fatal; no pool timer expires at the fatal.
- **Root cause remains OPEN.**

### 2026-10-05 — v42 tick rate RESOLVED: slpc = 30.72 MHz; phase-2 deadline = 1.28 s LTE DRX re-arm (ledger §112.106)

- **§112.106 — the slpc tick rate is 30.72 MHz (`0x7800` = 1 ms)** — the §112.105D "unit OPEN" caveat is closed.
- **Decisive measurement:** two consecutive sleep-timer expiries in the LTE RAT slot `0xc1da0830`
  (`+0xd8` = 17 567 500 668, `+0xe0` = 17 592 134 110) give a **period of 24 633 442 ticks @19.2 MHz =
  1.282992 s**. The ring deadline 39 247 211 is **1.277578 s @30.72 MHz (0.42 % fit)** but **2.044126 s
  @19.2 MHz (60 % off)** ⇒ the deadline is in **30.72 MHz** ticks (implied tick = 30.59 MHz).
- **The code's own conversion confirms it:** `slot+0x130 = f(tech) = 24 529 350`, `slot+0x138 = deadline =
  39 247 211`; ratio = **5/8 = 19.2/30.72**, and the converted value lands on the measured period (0.42 %).
- **Physical meaning:** phase-2 deadline = **1.2776 s ≈ the LTE 1.28 s paging/DRX cycle** — the sleepmgr
  re-arms `slpc_arm(4, ·)` once per DRX cycle. Phase-1 = 20 ms / 60 ms (boot/acquisition).
- **What it does NOT say:** the 1.28 s constant spans seq 31→581, not a 900-s-specific transition ⇒ the ring
  still does not isolate the ~900 s event. **Root cause (why STM ≠ SLEEP at ~900 s) remains OPEN.**

### 2026-10-05 — v42 deadline ANALYSIS: step object found, clock = 19.2 MHz, sleepmgr at the fatal instant (ledger §112.105)

- **§112.105 — the sleepmgr step object is `0xc20f1510`**, reached via `*(0xc1e158d0+0x14)` (the method
  `0xc03973a0` is `class 0xc1a94f70 +0x8c`). Its **`+0x58` = `0x0256dd6b` = the last ring entry (seq 581)** —
  a clean cross-check. (The STM object `0xc1e158d0` is a *different*, smaller object; its `+0x58` is a byte
  array — that is why the earlier "now" hunt failed.)
- **The clock is 19.2 MHz** (`gp+0x107b8 = 19 200 000`). The sleepmgr's absolute time fields
  (`step+0x2d0` = 17 567 592 566; `slot+0x118` = 17 567 583 228) = **914.98 s**, within **0.6 s of the fatal
  (915.57 s)** ⇒ the on-demand dump caught the sleepmgr **at the fatal instant**.
- **The slpc timer is 27-bit** (`gp+0x109e8 = 0x07ffffff`); the arm path is a two-clock conversion
  (`slot+0x8 = 30 720 000 = 0x7800 × 1000`). `0x7800`-granular phase-1 deadlines ⇒ the slpc tick is likely
  30.72 MHz (`0x7800` = 1 ms) but is **not yet independently proven (OPEN)**.
- **The deadline has TWO phases:** seq **1–30** = `20×` and `60×` 0x7800 (3 : 1 bimodal); seq **31–581** =
  near-constant **~39 247 200 (±100)**, non-monotonic. The transition is **sharp at seq ~31** (~47 s).
- **What it says / doesn't:** the sleepmgr re-arms its own LTE sleep timer ~1.5×/s with a *periodic*
  (near-constant) deadline — but that constant is present from seq 31, **not** only at ~900 s, so the ring
  does **not** isolate a 900-s transition. **Root cause (why STM ≠ SLEEP at 900 s) remains OPEN.**

### 2026-10-05 — v42 CAPTURED: the `slpc_arm` armer is the SLEEPMGR itself (ledger §112.104)

- **§112.104 — the ring was captured** by an on-demand dump at `ap_up 884 s` (the auto-dump is blocked by the
  AP hang, §112.103). 581 non-empty slots = the seq counter ⇒ the hook works.
- **RESULT — the armer is the SLEEPMGR.** All 581 arms are `tech==4` (LTE), and every caller is one of the
  two sleepmgr sites: `0xc039466c` × 489 (site `0xc0394660`) and `0xc0394640` × 92 (site `0xc0394634`). None
  of the `0xc06d`/`0xc0bb`/`0xc0cf` sites fire. **H3 answered.** Combined with §112.101 (the callback is
  registered by the sleepmgr), the LTE sleep path is **entirely self-contained inside the sleepmgr**.
- **Caller semantics corrected:** `r31` = the **next packet start** after the `call`, not `callsite+4`.
- **The deadline** (`r21:20`) is a **32-bit** value (`r3`=0 in 581/581), range `0x517c2`…`0x256ddbd`,
  dominated by a near-constant cluster **~39,247,200 (`0x256dd60`)**, plus a secondary ~`0x254e000` and many
  lower values. **H2 not supported:** 39,247,200 ticks = **2.04 s** at 19.2 MHz, not ~900 s; the unit is
  OPEN. H2′ (short deadline, fires late) is compatible. **Root cause (why STM != SLEEP at 900 s) OPEN.**

### 2026-10-05 — v42 run #1: fatal CONFIRMED at 915.57 s, but the AP hang blocks the auto-coredump (ledger §112.103)

- **§112.103 — H1 CONFIRMED.** The v42 instrument booted and ran cleanly to the ~900 s event. The device's
  `console-ramoops-0` (previous boot's console) shows `ap_up = 915.573721 s`:
  `qcom-q6v5-mss 4080000.remoteproc: fatal error received: lte_ml1_common_timer.c:390` — the `tmr_slave3`
  variant of the same ~900 s family. The hook did **not** crash-loop the modem.
- **⚠ The auto-coredump was NOT captured.** `port failed halt` at **916.41 s**, then an **AP reboot ~3 s
  later** (next boot 07:19:27). The 85 MB dump could not be written/read in that window. This is **AP hang
  site B**; **patch 831 IS deployed** (`qcom_bam_dmux.ko` md5 `98d532de…`) and the ramoops shows T4–T9
  completing, so this hang is **different** (the q6v5 halt failure), not the T4→T5 race.
- **Consequence.** The natural fatal does not reliably yield a coredump. The ring must be captured by an
  **on-demand** dump (§6.2a: `rmmod qcom_bam_dmux` first, then enable + trigger the coredump) taken
  **before** the fatal (ap_up ≈915 s). Capture scheduled at ap_up ≥880 s.

### 2026-10-05 — the `slpc_arm` FUNNEL RING (v42): built, offline-verified, DEPLOYED (ledger §112.102)

- **§112.102 — the §112.101 lead instrumented.** Ringing 15 call sites is infeasible, so v42 hooks the
  **single callee entry** `0xc0b66c00`: every site reaches it via `call 0xc0b66c00`, and at entry
  **r0 = tech**, **r2:r3 = the 64-bit deadline**, **r31 = the caller's return address (= callsite+4)** — so
  one hook captures the armer, the tech, and the deadline for all 15 sites.
- **The entry hook is packet-fragile.** `0xc0b66c00`'s first packet is 12 B and bundles `{call 0xc08371d0;
  immext; r1 = ##0xc1d9fef0; allocframe(#0x38)}`. Replacing 4 bytes would split the packet. v42 replaces the
  **whole 12-byte packet** with `jump 0xc0deb4a8; nop; nop`; the pad does `r6 = ##0xc003054c; jumpr r6`; the
  cave writes the ring, then **replicates the prologue** (`allocframe(#0x38)`, `callr 0xc08371d0`,
  `r1 = ##0xc1d9fef0`) and resumes at `0xc0b66c0c`. The frame and the saved LR are byte-identical.
- **Ring:** `0xc1d4c600`, **2048 slots × 32 B** (BSS) = `{seq, tech, deadline_lo, deadline_hi, caller}`;
  seq counter at `0xc1455000+0x4`. A first build used a 16-byte stride for a 20-byte record (the `caller`
  word would have aliased the next slot's `seq`) — caught offline and fixed before deployment.
- **Offline verification PASS** (`scratch/diag_patch_v42/verify_v42.py`): site jump target/parse, preserved
  body packet, byte-exact pad/cave, all five constants resolved, changed-byte containment, `sha256` headers,
  `mdt == b00+b01`. Built `modem.mdt` md5 `08584974f14fb8b2596edf98c6a00923` (stock
  `1a6f9507e03d4ddbbf1977af81ecdbd7`).
- **DEPLOYED** to `/lib/firmware` (sha256 read-back PASS). **Pre-emptive SSR DISABLED**
  (`preemptive_ssr_enabled=0`) because `interval=800` was resetting the modem before the ~900 s event (the
  device had run 82 min clean). `a2_pin=1` and recovery `ssr_enabled=1` unchanged. Reboot issued
  2026-10-05 07:02 UTC. Capture window **OPEN**; `scratch/read_v42_ring.py <dump>` decodes the ring.
- **P-V42 pre-registered:** H1 boot; H2 deadline itself ≈ 900 s; H2′ deadline short but fires late; H3 the
  last `tech==4` entry's caller names the armer. **Armer still OPEN** pending the next fatal's coredump.

### 2026-10-05 — the `slpc` "SystemTimer" trigger traced: a per-RAT DAL deferred callback (ledger §112.101)

- **§112.101 — the trigger mechanism is fully mapped.** `slpc` is a **2-byte `{type, tech}` message queue**
  (`0xc1200f30` on `0xc1da1200`): type 0 → the per-RAT worker `0xc0b66f20` (the fatal path), type 1 → a
  per-RAT counter. The type-0 producer is the **callback handler `0xc0b67bd0`**, stored at
  `pertech[i]+0x98`'s `+0x0c` (= `pertech[i]+0xa4`) — the object created in `slpc_init` by the DAL
  constructor `0xc0914b30`. That object **is the "SystemTimer"** (`slpc_init` looks the DAL property
  `"SystemTimer"` up at `0xc17e7d36`).
- **The arm/submit path:** `0xc0b66c00` / `0xc0b66830` (`slpc_arm(tech, deadline64)`) → `0xc0b66ae0`
  (computes `pertech+0xd8 = pertech+0x118 − pertech+0x60`) → `0xc0914e10(pertech[tech]+0x98, delay)`
  (DAL submit). On expiry the handler posts `{0,tech}`; the worker then calls `pertech[tech]+0x70`.
- **Live identity checks** (`up913.64`): `pertech[4]+0x60 = 0x21480`, `+0x118 = 0x1682da30`,
  `+0xd8 = 0x1680c5b0` and `0x1682da30 − 0x21480 = 0x1680c5b0` ✓; `0x21480 / 0x1bbc = 19.2` exactly.
- **The LTE wakeup callback is registered BY the sleepmgr** (`0xc0396fa0` writes `pertech[4]+0x70 =
  0xc039ef80`, `+0xf0 = 0xc039eed0`) ⇒ the fatal is the **sleepmgr's own scheduled wakeup running while the
  STM is not `SLEEP`** — a race (§112.67). `slpc` is the deferred-execution vehicle.
- **Corrected a first-pass under-count:** `slpc_arm` has **15** callers across `0xc039`/`0xc06d`/`0xc0bb`/
  `0xc0cf` — it is a public per-RAT API, not a sleepmgr-only path. So this item does **not** claim the
  sleepmgr arms tech 4.
- **Still OPEN:** which caller arms LTE at ~900 s, and why the sleepmgr leaves `SLEEP` early. Next instrument
  re-pointed at the 15 `slpc_arm` call sites (capture tech `r0` + the 64-bit deadline `r21:20`).

### 2026-10-05 — the plurality fatal's EXACT call chain: the `slpc` task's per-RAT LTE wakeup callback (ledger §112.100)

- **§112.100 — the fatal's caller is named.** The crash report carries a raw stack dump; walking the
  Hexagon frame chain on `modem_coredump_up913.64_devcd1.elf` (`lte_ml1_sleepmgr_stm.c:4054`, task `slpc`)
  gives **`slpc` task loop `0xc0b675e0` → per-RAT worker `0xc0b66f20` → `callr r0` (`r0 = memw(r17+0x70)`)
  → `FUN_c039ef80` → `if (stm_get_state(LTE_ML1_SLEEPMGR_STM) != 3) assert`**. So the fatal is a **wakeup
  requested while the sleepmgr is not `SLEEP`** — the *consumer* side §112.96 pointed at.
- **`FUN_c039ef80` is a registered per-RAT callback** (`grep "call 0xc039ef80"` = 0). The table is
  `0xc1d9fef0`, stride `0x250`; read from the coredump it is the modem's **RAT table** —
  `"GSM"`/`"1X"`/`"WCDMA"`/**`"LTE"`**, with **tech 4 = LTE** and `pertech[4]+0x70 = 0xc039ef80`
  (identical in all 36 archived dumps). Crash registers corroborate the index (`R21 = 4×0x250`,
  `R26 = 0xc1d9fef0`, `R20/19/18 = pertech[4]+0x04/+0x78/+0xd8`).
- **The owning module is the `slpc` task** — its two printable strings are **`"slpc"`** and
  **`"SystemTimer"`**, and it creates the task at `0xc0b6783c` (`0xc1200d40("slpc", 0x102)`).
- **Still OPEN:** the upstream "why the sleepmgr is not `SLEEP` at 900 s". Next instrument re-pointed at the
  **`slpc` receive** (`0xc1200f30`) + per-RAT dispatch entry — a cheaper, pre-registerable ring.

### 2026-10-05 — the only literal 900 s constant is the IMS Registration Manager, which is NOT instantiated ⇒ REFUTED (ledger §112.99)

- **§112.99 — the 900000 scan, and its refutation.** A full-disassembly scan for `0xdbba0` (900000)
  returns exactly **three** code sites, all in one module: `0xc12553c0`, `0xc125d338`, `0xc125fe54`,
  each storing 900000 into a timer-config field of the modem's **IMS Registration Manager**
  (`…/modem_proc/ims/regmana`: `CPDPRATHandlerVoLTE`, `CRegistrationHandlerVoLTE/WLAN`; timeout table
  `{30000,600000,60000,900000,10800,5000}` ms, consumed by the retry dispatcher `0xc1260cf0`).
- **Three independent NEGATIVE checks** ⇒ the module is **not instantiated** (HMU05 ships voice/VoLTE
  stubbed): (1) the class vtable `0xc1810c00` has **no 4-byte-aligned data hit** in 6 coredumps;
  (2) the exact 20-byte config-struct signature has **zero** hits; (3) no IMS/regmana string in any of
  the 10 `f3cap` captures. ⇒ the IMS 900 s timer is **not** the ~900 s deadline.
- **Unit caveat:** QuRT pool `idx 92` `+0x50 = 0x000dbba0` with `rem/dur = 1200` (not 19200) ⇒ the
  "900 s pool timer" identification (§112.68/69) rests on an **unverified unit**. No 900-s-at-tick
  constant exists (`0x40600000` is the float 3.5; every `#0x384` is a struct stride). Next instrument
  must read the **runtime** arm argument, not scan for a constant.

### 2026-10-05 — corpus census: the ~900 s deadline is EXCLUSIVELY `lte_ml1` at modem-uptime 900–903 s; the `a2_power.c:1189` family is separate and coredump-identifiable (ledger §112.96–§112.97)

- **§112.97 — the two-family census (n=42 dumps).** Cross-tabulating each archived coredump's embedded
  crash report (site + modem uptime, `scratch/crashlog_extract.py`) against the A2-quiesce counters
  (`scratch/a2_counters.py`) gives a **clean partition**: the `lte_ml1` family
  (`sleepmgr_stm.c:4054` ×18, `common_timer.c:390` ×9) is at modem-uptime **900/901/902/903 s ONLY**;
  the `a2_power.c:1189` family (×11) is **scattered** (68–1041 s). ⇒ the ~900 s deadline is a clean
  **900 s (15 min) on the MODEM's own clock** (the AP-side "902.7 s" includes the boot offset).
- **§112.97(B) — NEW coredump signature.** `logcnt@0xc28602fc == 0x385 (901)` **exactly** identifies an
  `a2_power.c:1189` deadlock (11/11 vs 0/31) — a one-word, coredump-only detector for the A2-quiesce
  deadlock (registers `0xec320ba4/ba8/bac/bd8/be0` never clear).
- **§112.97(C) — early `a2_power` window.** The corpus holds `a2_power.c:1189` at 68/77/177/412 s, below
  any pre-emptive interval ⇒ `a2_pin=1` is load-bearing for the early window; discrepancy vs the config
  comment's 722.7 s "earliest" recorded honestly (two readings, open).
- **§112.96 — MCPM/SAW offline recon.** Solved the F3 log-descriptor mechanism (8-byte
  `{packed, msg_ptr→"<file>: <fmt>"}`); located the MCPM/SAW step code (`0xc0cf8cd0`); the SAW state is
  byte-identical across 5 fatal + 3 wedge dumps ⇒ **not** a discriminator (do not instrument it).
  Retarget: the sleepmgr's consumption of `RF_WAKEUP_CNF`, not the SAW step.

### 2026-10-05 — v41: the 773-entry handler sequence is the BOOT sequence — the RFM-teardown line (v35–v41) is CLOSED (ledger §112.95)

- **v41 (§112.95) — P-V41 H2 PASS.** `scratch/diag_patch_v41/` records the bit-14 guard flag
  `memub(0xc3110940)` as ring A's third field. The run manifested as a **WEDGE** at modem-up ≈896–911 s
  (the canonical window; the `type watchdog` crash at AP 981.8 s was **our own §6.2a coredump trigger**).
  Ring A: **all 771 `mask==0x06` entries carry `guard==0`**, the teardown entry (seq 772) reads 0, and the
  recursion (seq 773) reads 1 ⇒ the sequence is the **BOOT** sequence; the flag flips `0→1` at the boot
  teardown itself. ⇒ **The RFM handler does NOT re-run at the ~900 s event.** This also resolves §112.94's
  "reset" puzzle (there was no reset; v38's `t` proxy was broken). The `rf_1` "new imei check" teardown is
  a boot-time, load-bearing op and is **not** the event; in the fatal path it re-enters only as a
  **symptom**. **Line closed.**

### 2026-10-05 — v39/v40 NEG: the RFM teardown is reached AT BOOT (handler-count >100); b05 code is EXECUTE-ONLY (ledger §112.94)

- **v39 (§112.94) — NEG (boot crash-loop).** `scratch/diag_patch_v39/` gates the RFM teardown call
  `0xc0d5ef70` on the handler-entry counter (*skip iff > 0x64*), keeping the boot teardown and skipping
  the ~900 s one. After a real AP reboot the modem **crash-loops** with `SFR Init: wdog or kernel error
  suspected.` (the **v36 signature**) ⇒ the **boot** teardown was skipped ⇒ the handler had already been
  entered **>100×** before it, and the teardown is **load-bearing at boot**. The v39 premise is falsified.
- **v40 (§112.94) — NEG (`:Excep`).** A **code-space** run counter at `0xc00305f8` (b05, ELF `RWX`)
  makes the modem crash-loop with `fatal error received:     :Excep  :0:` ⇒ **b05 is execute-only at
  runtime** (the ELF `W` bit is not honoured); code-space markers are unavailable on this baseband.
- **Model.** `0xc0d5ef70` is in the **`rf_1` task entry** `0xc0d5edd0`, which registers the handler
  `0xc0d5efd0` for `"RFM_INIT_COMPLETE"` (`0xc0d5eefc`) then tears down near its tail ⇒ the teardown is
  reached **at boot**. The F3 collapse tail shows the handler's bit-14 log `rf_task.c:336` **at ~908 s**
  (live heartbeat context), and v39 shows the handler also runs at boot ⇒ it runs at **both**, so the
  boot sequence was **reset** in between — but the v38 soak log shows `f=0` / `mm=connected` throughout,
  i.e. **not a modem restart**; the reset mechanism is **UNIDENTIFIED**.
- **v41 (§112.94, in flight)** records the bit-14 guard `memb(0xc3110940)` (BSS; sole writer = the
  handler's bit-14 path) as ring A's third field, to decide boot (guard 0) vs ~908 s (guard 1).

### 2026-10-05 — ★★★★★ v36 NEG: the RFM teardown is LOAD-BEARING ⇒ the ~900 s event is a RE-RUN of the RFM init; v37 names its trigger (ledger §112.88–§112.90)

- **v35 (§112.88) — the RFM-task EVENT ring.** Hooked the RFM event-bitmask handler `0xc0d5efd0`
  (registered for `"RFM_INIT_COMPLETE"`). `count = 773`; exactly **ONE** bit-14 event
  (`seq=772, mask=0x40d3` = the full RFM teardown mask), a **single** caller `r31 = 0xc0d5ef78`
  → the call site `0xc0d5ef70` inside the function `0xc0d5edd0..0xc0d5ef7c`
  (`rf_task.c:582 "RF task  new imei check !"`). ⇒ the terminal LTE death is initiated by the
  **RFM task itself**, not an external event fan-in.
- **v36 (§112.89) — NEG (crash-loop), REPRODUCED.** Replacing the 2-word teardown packet at
  `0xc0d5ef70` with `{ r0 = #0x40d3; nop }` (clean 8-byte patch; verified by `llvm-mc`) makes the
  modem **crash-loop at boot** every ~1.8 s; AP dmesg: `qcom-q6v5-mss: fatal error received:
  SFR Init: wdog or kernel error suspected.` + `handling crash #33`. The string lives in `modem.b18`
  (the modem's own watchdog message). ⇒ **the teardown call is LOAD-BEARING at boot**.
- **Model sharpened.** Doc 221 independently shows `rf_task.c:582 "new imei check !"` is logged **at
  boot**. The v35 ring missed that early entry (its cave save-area `0xc1455000` is initialised/zeroed
  after the boot check). ⇒ the ~900 s teardown is a **RE-RUN of the boot-time RFM "new imei check"**;
  the event is the RFM task **re-initialising** itself at ~900 s (and not recovering), NOT the RFM
  task killing the modem. **New target: the trigger that re-runs `0xc0d5edd0` at ~900 s.**
- **NEW offline fact.** File offset `0x174d500` is a task-descriptor table: `rf_1`(`0xc1d1c330`) →
  **`0xc0d5edd0`**, `rf_2` → `0xc0d5e980`, `rf_apps` → `0xc0d5e810`, `rf_wrsp` → `0xc0d5d1b0`,
  `rf_ic` → `0xc0d5c4f0`, `rf_fwsw` → `0xc0d5eda0`, `rf_task_init_function` → `0xc0b32fa4`.
  ⇒ `0xc0d5edd0` is the **`rf_1` task's registered entry** (its 2-word entry packet starts at
  `0xc0d5edd0`; `0xc0d5edd4` was only its 2nd instruction).
- **v37 (§112.90) — the `rf_1`-task ENTRY ring (PRE-REGISTERED, run in progress).** Hooks the entry
  `0xc0d5edd0` (site → b16 stub `0xc0deb4a8` → b05 cave `0xc003054c`), records
  `{seq, caller=r31, r0, r1}`; image md5 `fa34d8a0`. P-V37-RF1CALLER: H1 count>0; H2 ≥2 entries;
  H3 the re-run's caller names the trigger. Boots cleanly, data path up. Device recovered after the
  v36 NEG (stock fw `1a6f9507`, mitigation re-enabled, AP rebooted).

### 2026-10-05 — ★★★★ v37 RESULT: the `rf_1` task ENTRY is dispatched ONCE per boot ⇒ the ~900 s re-run is NOT a re-entry (ledger §112.91)

- **Capture.** The v37 run produced **no auto-coredump** (it wedged), so the ring was taken **on demand**
  (§6.2a): `rmmod qcom_bam_dmux` → `echo 1 > /sys/kernel/debug/remoteproc/remoteproc0/crash` →
  `cat /sys/class/devcoredump/devcd*/data` = **85 398 475 B**, md5 `80a62f3afd27c4a1049665d2c2c815bc`
  (`scratch/dumps/v37_wedge_dump.bin`).
- **RESULT — `count = 1` in BOTH captures** (wedge + the auto fatal dump `c1b18065…`): the `rf_1` entry
  `0xc0d5edd0` is reached exactly **once** per boot — `seq=1 caller=0xc087c658 r0=0 r1=0x5a`
  (caller inside `FUN_c087c500`, the boot dispatch). **P-V37-RF1CALLER: H1 PASS, H2 FAIL, H3 n/a.**
- **⇒ the ~900 s event does NOT re-enter the `rf_1` task entry.** `0xc0d5edd0` is a **task entry**
  (one dispatch, then an internal loop); the teardown at `0xc0d5ef70` is reached **inside that loop**.
  The entry-hook is **mis-targeted** ⇒ retarget the instrument to the teardown call site `0xc0d5ef70`.
- **★ Timing caveat.** Neither v37 boot reproduced the ~900 s event: the AP-327 boot fataled at
  modem-uptime **3:00:01 = 10 801 s** (crash report: Task **ML1**, PC `0xc087a804`,
  site `lte_ml1_common_dump.c:217`), and the AP-11 130 boot **wedged** at modem-uptime **~3 778 s**
  ("RF receiver frozen", `mmcli` still connected). So `count = 1` is consistent with the event never
  firing — a weak negative on H2, but a solid fact on the entry.
- **Restore (done).** v37 → stock (`modem.mdt` `1a6f9507…`); mitigation re-enabled; AP rebooted ⇒
  `ping -I wwan0` **exit 0**.

### 2026-10-05 — ★★ AP-side modem-powerup differential is a NEGATIVE; the RF-task stop is an EVENT (ledger §112.87)

- **A. CX-rail BHS lead CLOSED:** Android `pil_mss_power_up()` asserts `EXTERNAL_BHS_ON` only `if
  (drv->cxrail_bhs)`, which requires a `cxrail_bhs_reg` DTS entry — present in `msm8226/msm8610.dtsi`
  but **NOT `msm8916.dtsi`**. Android-on-HMU05 does not assert it either.
- **B. QDSP6SS reset/power-up sequence IDENTICAL** between Android `__pil_q6v5_reset` and mainline
  `q6v5proc_reset`'s else-branch (GFMUX_CTL already matched by the project patch; §112.12 no-op).
- **C. IMEM `pil@94c` image-info table** (Android writes 20 B `pil_image_info` entries; mainline has
  no writer) = AP-side crash-dump bookkeeping, not causal. ⚠ AP `devmem` of IMEM returns garbage.
- ⇒ the §112.8 "PIL powerup handshake" lead is **narrowed to a negative**: the power-up does not differ.
- **E. NEW — the F3 collapse tail's last non-heartbeat `rf_task.c:336 "get imei stoped"` is an EVENT:**
  code site `0xc0d5f030` in the handler `0xc0d5efd0` (registered for the string `"RFM_INIT_COMPLETE"`),
  gated by event bit 14 (`tstbit(r17,#0xe)`); sibling bits 6/7 log `rf_task.c:386/396/400`
  start/done/will-stop. `rf_dispatch_command` dispatches RF commands by CID from a stride-`0xc8` table.
- **F. CORRECTION to §112.86(B):** the 290 ms `FW_SLEEP_PWRDN_FULL`→`FW_WAKE-UP_Start` gap is a **normal
  deep-sleep cycle** (byte-identical ×2), not the stall. The MCPM cycle runs normally to the event.

### 2026-10-05 — ★★★ MCPM/power-collapse target, offline: last sleep never exits; F3 collapse tail; NEGATIVE on the `rpm.sync` park (ledger §112.86)

- **Offline only** (archived coredumps + archived F3 chunks; no device mutation, no patch).
- **A. LPR deep-sleep ring:** across the 5 fatal dumps `SleepEntry = SleepExit + 1` in 4 of 5; in
  `up915.44` the memory-order tail's last record is an **unmatched `SleepEntry`** ⇒ the modem entered
  its final sleep and **never exited** (coredump confirmation of §112.57.15).
- **B. F3 collapse tail:** `FW_SLEEP_PWRDN_FULL` → (290 ms) → `FW_WAKE-UP_Start` → 2× `a2_power` req →
  **`rf_task.c:336 "get imei stoped"`** → heartbeat only. Refines §112.57.15 (the last record is the RF
  task stopping, not the wakeup).
- **C. `mc_msg.c:5625 "SLOW CLOCK REQUEST"` is ROUTINE** (1/10 s, reason=0 valid=0) — not a precursor;
  re-confirms the F3 clock is 204800 Hz.
- **D. NEGATIVE:** the §6.4/§37 **`rpm.sync` software park** — the corpus's surviving root-cause
  candidate — is **not present at any captured fatal**: the churn-loop flag reads a stable pointer/zero
  across **all 42 archived coredumps** (independent of §39.2's three-count kill).
- **E/F. Revised target:** the stall is a **hardware-handshake** stall — the `cxo.shutdown` step of the
  always-selected `mode[1]`, and/or the A2 quiesce `0xec320bac & 7` (§112.20/§112.28). Next instrument
  reads the hardware register live, not the F3 rate. **Root cause still OPEN.**

### 2026-10-05 — ★★★★★ v34 RESULT: H7a — the RF wakeup transport is HEALTHY in BOTH directions; the ~900 s event has a VARIABLE victim (ledger §112.85)

- Ran v34 (`modem.mdt` `1908ec47…`), SSR off, IDLE. **FATAL at AP 913.386908 s**, site
  **`lte_ml1_sm_conn_inter_freq_stm.c:712`** (task `ML1 MGR`); dump `scratch/v34_run/dump_FATAL.bin`.
- **Raw:** `comp_count = issue_count = cb_count = 1647`.
- **★ H7a CONFIRMED:** every issued WAKEUP_REQ completed — **NO loss on the request hop**. The whole
  RF wakeup round trip (issue → RFA handler → CNF → completion → tail → RF_WAKEUP_CNF → sleepmgr) is
  control-flow-healthy in BOTH directions. **FALSIFIES the §112.83-deduced "issued-but-never-completed"**
  (measure, don't infer).
- **★★★★★ This run's fatal does NOT involve the RF path:** RFMGR state 4 (TX_TUNED) with
  `ctx+0x104 = 0` (**idle**), SLEEPMGR state 1 (**ONLINE, healthy**), assert in `ML1 MGR`.
  ⇒ the "RFMGR pending + sleepmgr state 9" seen at v33's fatal is **VICTIM-DEPENDENT**, not universal.
- **⇒ Reframing:** the ~900 s event is an **upstream ML1/MCPM trigger with a variable victim**
  (§112.57.13/14/15, §112.66). The RF-wakeup/sleepmgr "stall" is a symptom of one victim, not the
  cause. The v26→v34 RF-transport line **closes**. Root cause remains the **MCPM/power-collapse stall**
  (§112.57.15) — still OPEN.
- Device rolled back to stock (`1a6f9507…`); SSR mitigation re-enabled (1/1).

### 2026-10-05 — ★ v33 RESULT: RFMGR WAKEUP completion + RF_WAKEUP_CNF callback are HEALTHY (H5a+H6a, ledger §112.83)

- Ran v33 (`modem.mdt` `9202ebd5…`), SSR off, IDLE. **FATAL at AP 914.180 s**,
  `lte_ml1_common_timer.c:390`; dump `scratch/v33_run/dump_FATAL.bin`.
- **Raw:** `comp_count = comp_exit = cb_count = 1296`; ring uniform (all `caller 0xc0fe1654`,
  `ctx 0xc1e145a8`, `msg_obj 0xc312ba48`).
- **★ H5a+H6a CONFIRMED:** the RFMGR WAKEUP completion `0xc0315560` reaches its tail `0xc0315628` and
  invokes its callback `0xc039e570` on **every** wakeup, including the last before the fatal.
- **★★★ NEW FACT:** the callback IS the **`SLEEPMGR_RF_WAKEUP_CNF` (`0x42b0801`) sender** —
  `0xc039e570` → `0xc039e580` → funnel `0xc03927d0` (the function v26 hooked). So the RFMGR→sleepmgr
  wakeup-CNF path is healthy end-to-end ⇒ the ~900 s event is **NOT** a lost/stalled RF wakeup CNF.
  **Closes the RFMGR-wakeup-transport hypothesis family (v26→v33).**
- **⚠ OPEN (cross-run):** v27's ingress ring saw `RF_WAKEUP_CNF` 265× vs `STMR_ON_REQ` 266×; v33 shows the
  sender fires 1296×. Reconciliation (router/dispatch drop vs sleepmgr ordering race) needs a combined
  send+ingress instrument → proposed **v34**.
- **Offset correction:** §112.82's first-build offsets (`0xc1455010/14`) were the boot-looping build; the
  shipped sled build uses `+0x08 comp_exit` / `+0x0c cb_count`.

### 2026-10-05 — ★★ v33 boot-looped: the b05 ZERO regions are NOT EXECUTABLE (ledger §112.82.1)

- **Symptom:** the first v33 image (`a9def8a9…`, new caves in a zero run at `0xc003f354`) boot-looped
  `:Excep :0:` every ~11 s. Bisect: **exit-only** (`f7458322…`) also looped ⇒ the EXIT hook's *cave
  location*, not the callback.
- **Root cause (crash log):** captured the boot-loop dump (`scratch/v33_bootloop/dump_exitonly.bin`,
  85 398 475 B) → `crashlog_extract.py` gives **`PC=0xc003f354` = the cave's FIRST word**, while that
  VA in the same dump holds the cave bytes ⇒ the region is **loaded + readable but NOT EXECUTABLE**
  (runtime permission/MPU; the ELF's RWE flag does not describe the runtime mapping).
- **★ RULE:** injected code must go in a **NOP-padded** (`00 c0 00 7f`) run, never a **zero** run. b05
  has ONE usable sled: `0xc003054c..0xc0030600` (180 B).
- **Fix:** rebuilt v33 with ALL caves in the sled — comp+ring 92 B @`0xc003054c` + exit 28 B
  @`0xc00305a8` + cb 36 B @`0xc00305c4` = 156 B. `modem.mdt` md5 **`9202ebd5562c85948e1321d98594004f`**;
  `verify_v33.py` PASS; deployed → **CLEAN BOOT** (`crashes=0 excep=0` at up 88).
- Memory `reference_hmu05_platform_quirks.md` §1 updated with the executability rule.

### 2026-10-05 — v32 RESULT: the RFMGR WAKEUP completion RUNS on every wakeup (H4a) — delivery is HEALTHY

- **Ledger §112.81** — ran v32 v2 (`modem.mdt` `76b4177e…`), SSR off, to the ~900 s event. **FATAL at
  AP 914.191932 s**, `lte_ml1_common_timer.c:390`; dump `scratch/v32_run/dump_FATAL.bin`.
- **Raw:** `seq_entry = 1458`, `comp_count = 1458`, `cnf_count = 0`; completion ring = 1458 entries
  (all `caller 0xc0fe1654`, `ctx 0xc1e145a8`, `msg_obj 0xc312ba48`).
- **★ H4a CONFIRMED — `comp_count == seq_entry`:** the RFMGR WAKEUP completion `0xc0315560` ran on
  every wakeup, **1:1** with the RFA handler returns ⇒ **the RFA→RFMGR CNF DELIVERY IS HEALTHY.**
  This **FALSIFIES v31's inference** that the completion never runs.
- **Site identity:** the completion's `r16` = the RETURN of `0xc02d8e58` = the ctx `0xc216fd50` (the
  ring logs the ENTRY arg = the STM object `0xc1e145a8`). It reads `ctx+0x104` (=`0x4290203`), calls
  `ctx+0x1f0`=`0xc039e570`, clears `ctx+0x104`.
- **⚠ Caveat:** the completion ENTRY ran 1:1 but may still BAIL inside (4 assert-logger paths); at the
  fatal `ctx+0x104` is still pending. Next = the completion EXIT + the callback.
- **Refinement:** at the fatal `ctx+0x12 = 0` (the completion's early-return gate is CLEAR ⇒ it takes
  the callback path and clears `ctx+0x104`), callback slots registered ⇒ the pending REQ is a **NEW
  cycle interrupted by the fatal**, not a bail ⇒ **the RFMGR WAKEUP cycle is HEALTHY up to the fatal**
  (a WEDGE remains the load-bearing case; v32 captured a FATAL).
- **Send NEG:** `cnf_count = 0` ⇒ `0xc03165cc` is an ERROR-path block, NOT the CNF builder (re-scope).
- **H1 gap:** the v32 caves never write the marker (builder gap) — attribution via offsets + ring.

### 2026-10-05 — v32 v1 **BOOT-LOOPED the modem** (negative result): packet-boundary bug + v2 fix

- **Ledger §112.80** — the v32 v1 instrument (`modem.mdt` md5 `9b5a47de…`) **boot-looped the modem**:
  `lte_ml1_rfmgr_stm.c:3712` every ~11 s. Boot-loop coredump captured
  (`scratch/v32_run/dump_BOOTLOOP.bin`); **rolled back to stock** (`1a6f9507…`), modem recovered.
- **Root cause — a MID-PACKET continuation.** Both v32 sites are multi-instruction packets
  (comp `0xc0315560` = 3 words; cnf `0xc03165cc` = 2 words), but v1 patched only the first word and
  continued at `SITE+4` = **mid-packet**. The RFMGR `:3712` assert is a downstream symptom of the
  WAKEUP completion never finishing. Same trap class as `feedback_patch_parse_bits.md`.
- **v2 fix (built + offline-verified):** replace the first word with `{ jump cave }`, **reproduce the
  whole packet in the cave** (comp keeps `{callr; r17=r1; memd; allocframe}` as ONE packet so
  `allocframe` still saves the caller's r31), continue at the **true** boundaries
  `0xc031556c`/`0xc03165d4`; `verify_v32.py` now **asserts packet boundaries** (v1's gap).
- **v2 hash:** `modem.mdt` md5 **`76b4177e5279d2521767d9409b7fdf85`** (seg16/seg5 SHA-256 PASS; caves
  172 B of the 180 B sled). Decision rule (§112.79) **unchanged**.

### 2026-10-04 — P-V31-EPILOGUE **CONFIRMED**: the CNF is EMITTED and LOST DOWNSTREAM (RFA→RFMGR transport)

- **Ledger §112.78** — ran v31 (entry ring on `0xc1017818` **+ a counter at the return site
  `0xc10179fc`**) with SSR disabled, to the ~900 s event.
- **FATAL at AP 915.194888 s**, site `lte_ml1_common_timer.c:390`; MBA at AP 11.795971 s ⇒
  **modem-up 903.399 s**. Dump `scratch/v31_run/dump_FATAL.bin` (85 398 475 B).
- **H1 PASS** (marker `0x76323b01` + site_entry `0xc1017818`), **H2 PASS** (`seq_entry = 1428`,
  `epilogue_count = 1428`).
- **★ H3a — `epilogue_count == seq_entry` (diff 0):** every handler invocation **reached the CNF
  post AND returned** ⇒ the handler ran to normal completion on the failing request too, and the CNF
  (`0x60708aa`) was **emitted**. **H3b FALSIFIED** (no in-post block). ⇒ **the loss is DOWNSTREAM of
  the raw handler, in the RFA→RFMGR completion transport** (RFMGR completion `0xc0315560` never ran).
- **Corroboration:** RFMGR state 5 (SLEEP); SLEEPMGR state 9 (OFFLINE_WAKEUP); `ctx+0x104 = 0x4290203`.
- **Residual (honest):** "returned" proves the handler completed; "posted" is inferred (single live
  path, §112.75.1). Next instrument = the RFA-server reply path that hands `0x60708aa` to RFMGR.

### 2026-10-04 — v31 PRE-REGISTRATION (P-V31-EPILOGUE): does the handler RETURN after the CNF post?

- **Ledger §112.77** — pre-registered **before** the run. v30 showed the handler reaches its CNF-post
  block on every entry, but a post-**entry** counter can't tell whether it then **completed and
  returned**.
- **Instrument (v31):** v29/v30 entry ring on `0xc1017818` (site → tramp `0xc1048644` → cave
  `0xc003054c`) **plus a counter at the single return site `0xc10179fc`** (`jump 0xc0837c38`; site →
  tramp `0xc1048664` → cave `0xc00305bc`, 32 B). Header `0xc1455000` = `{ marker 0x76323B01,
  seq_entry, site_entry 0xc1017818, (unused), epilogue_count }`.
- **Hash:** `modem.mdt` md5 `e91031ed535ce2b648e5fc6fedc048de`; sites `0xc1017818`→`16c70658`,
  `0xc10179fc`→`34c60658`; offline disasm verify PASS.
- **Decision rule (fixed):** `epilogue_count == seq_entry` ⇒ every entry returned ⇒ the CNF was
  **EMITTED and lost in the RFA→RFMGR transport**; `== seq_entry − 1` ⇒ the last entry **BLOCKED
  INSIDE the post** (in `0xc08f1500`/`0xc093be30`); NEG ⇒ marker absent.

### 2026-10-04 — P-V30-CNFPOST SCORED: the CNF-post site is reached on every entry (stall is at/after the handler's completion)

- **Ledger §112.76** — ran v30 (entry ring on the raw WAKEUP handler `0xc1017818` **+ a counter at
  the CNF-post site `0xc10179a8`**) with SSR disabled, to the ~900 s event.
- **FATAL at AP 915.799 s**, site `lte_ml1_common_timer.c:390`. Dump
  `scratch/v30_run/dump_FATAL.bin` (85 398 475 B). Clean AP reboot first (0 real oops; LTE attached).
- **H1 PASS** (marker `0x76323a01` + site_entry `0xc1017818`), **H2 PASS** (`seq_entry = 1234`,
  `exit_count = 1234`; independent counters at `+0x04`/`+0x0c`).
- **★ H3a — `exit_count == seq_entry` (diff 0):** all **1234** handler invocations reached the
  CNF-post block ⇒ **H3b FALSIFIED** (the last entry's RF-script work did **not** block before the
  post). The loss is **at/after the handler's completion**, not in the per-carrier RF-script work.
- **Corroboration:** RFMGR state 5 (SLEEP); SLEEPMGR state 9; WAKEUP_REQ `0x4290203` at `0xc1e14530`
  (⚠ corrects the ledger's earlier "`ctx+0x104`" offset note — the value is the invariant).
- **Residual (honest):** the counter is at the post **entry**, so it cannot separate "completed the
  post and returned (CNF emitted, lost in the RFA→RFMGR transport)" from "reached the post and
  blocked inside it" ⇒ **v31 = an epilogue counter at `0xc10179fc`** (`== exit_count` ⇒ transport;
  `== exit_count − 1` ⇒ in-post block).
- **Pre-run control (§112.75.1):** the post site is on the only main path (the sole bypass is a dead
  null-object error path) ⇒ `exit_count == seq_entry` in health.

### 2026-10-04 — v30 PRE-REGISTRATION (P-V30-CNFPOST): is the CNF `0x60708aa` actually POSTED at the event?

- **Ledger §112.75** — pre-registered the v30 run **before** it: v29 proved the raw WAKEUP handler
  `0xc1017818` is **entered** 1162× at the event (stall DOWNSTREAM), but an entry-only ring cannot
  separate "last CNF posted but lost" from "last entry's RF-script work blocked".
- **Instrument (v30):** same entry ring on `0xc1017818` (site → tramp `0xc1048644` → cave
  `0xc003054c`) **plus a counter at the CNF-post site `0xc10179a8`** (site → tramp `0xc1048664` →
  cave `0xc00305bc`, 44 B). Header `0xc1455000` = `{ marker 0x76323A01, seq_entry, site_entry
  0xc1017818, exit_count, site_exit 0xc10179a8 }`. ⚠ exit probe is a **counter, not a ring** (b05
  free window = 180 B; entry cave already uses 112 B).
- **Hash:** `modem.mdt` md5 `bfe46e2bffada25a13dbb732afe63686`; seg16/seg5 SHA-256 re-verified PASS;
  site words `0xc1017818`→`16c70658`, `0xc10179a8`→`5ec60658`; offline disasm verify PASS.
- **Run config:** SSR disabled (`ssr_enabled=0`, `preemptive_ssr_enabled=0`), `coredump=enabled`,
  LTE attached. Runner `scratch/v30_run.sh`; decoder `scratch/read_v30_ring.py`.
- **Decision rule (fixed):** `exit_count == seq_entry` ⇒ CNF **POSTED but LOST** (loss downstream of
  the handler); `== seq_entry − 1` ⇒ **last entry's RF-script work BLOCKED**; `== 0` ⇒ post site never
  reached; NEG ⇒ marker absent.

### 2026-10-04 — P-V29-RAWWAKE SCORED: the raw WAKEUP handler runs at the event (stall is DOWNSTREAM)

- **Ledger §112.74** — ran v29 (ring on the entry of the raw RFA WAKEUP handler `0xc1017818`) with
  SSR disabled, to the ~900 s event.
- **FATAL at modem-uptime 903.46 s** (AP 1718.567 s − modem restart 815.107 s), site
  `lte_ml1_common_timer.c:390`. Dump `scratch/v29_run/dump_FATAL.bin` (85 398 475 B).
- **H1 PASS** (marker `0x76323901` + site `0xc1017818`), **H2 PASS** (`seq = 1162`).
- **★ H3a SUPPORTED / H3b FALSIFIED:** the raw WAKEUP handler is **entered 1162×**, all via the WAKEUP
  stub (`caller = 0xc10187a0`), regular cadence right up to the recorded end ⇒ the raw op `0x60702aa`
  **DOES fire**; the stall is **NOT upstream** of `0xc1017818`.
- **Corroboration:** RFMGR state 5 (SLEEP) + `ctx+0x104 = 0x4290203` (WAKEUP pending); SLEEPMGR state 9.
- **Residual (honest):** an entry-only ring cannot separate "every entry completed, last CNF lost"
  from "last entry's RF-script work blocked" ⇒ **v30 needs a ring at the handler exit / CNF post
  (`0xc10179a8` / `0xc0316600`)**.

### 2026-10-04 — The router flash-storage bug: overlayfs→ext4→jbd2 unlink oops wedges the overlay

- **Ledger §112.72** — documented the AP-side (OpenWrt/kernel) storage fault found while setting up
  the v29 run; **unrelated to the ~900 s modem event**.
- **Symptom:** every **overlay write hangs forever** (`/root`, `/etc`, `/lib` on `mmcblk0p15` ext4);
  **tmpfs writes work**; **reads work** (deployed v29 image `398dce8a…` intact); load ≈ 7.5.
- **Oops (deterministic, uptime 39 s):** `PID 6049 Comm: rm` → `ovl_unlink` → `ext4_unlink` →
  `ext4_orphan_add` → `jbd2_journal_get_write_access` → **`jbd2_write_access_granted+0x14`** fault
  `(f9400020)` dereferencing poison `x1 = 0xc568142b5fe526b7` (the `s_sbh` handed to jbd2).
- **Wedge mechanism (proven by kernel stacks):** the oopsed `rm` dies holding an open jbd2 handle ⇒
  `PID 187 [jbd2/mmcblk0p15]` stuck in `jbd2_journal_wait_updates`; `PID 12 [kworker/u16:0+f]` stuck
  in `wait_transaction_locked` ⇒ **no new transaction can start** ⇒ all writers block in `D`.
- **Trigger:** the diagnostic scripts launched from `/etc/rc.local` churn files on the overlay —
  `f3cap.sh:54/59` and `dumpwatch.sh:40/52` `rm -f`. Live `…/c000005_up00039.raw` ("up00039" = the
  oops uptime) pins it to `f3cap.sh`.
- **★ A reboot does NOT fix it (hypothesis FALSIFIED):** the oops recurred at the same point, same
  PID 6049 — `f3cap.sh` re-runs at boot and re-triggers. The wedge is in-memory (a dead task's
  handle), so breaking the trigger should give a healthy boot.
- **Blocked:** the v29 run setup (`uci commit` to disable the pre-emptive/Stage-3 SSR cannot persist).
- **RESOLVED 2026-10-04 (ledger §112.72.8):** edited `/etc/rc.local` **out-of-band via the raw block
  device** (journaled writes were wedged): `ls -i` → inode 170 → ext4 superblock (bs 4096, inode 256)
  → group desc 0 (`bg_inode_table_lo` @0x08 = 458) → inode offset 1,919,232 → extent `start_block`
  685158 (byte 2,806,407,168); verified md5, then wrote a **same-length 2001-byte** replacement
  (dropped the `f3cap`/`dumpwatch`/`hang_probe_t4` churn; kept `coredump-enable`) with
  `dd … seek=685158 conv=notrunc,fsync`. After a **`sysrq-b`** reboot (`reboot -f` did nothing):
  `/etc/rc.local` = `7052eb35…`, **0 oops**, `jbd2` healthy, **overlay writes OK**, firmware intact.
  ⚠ A content search (`strings -t d`) found a **stale** older copy — resolve via the INODE.
  Artifacts: `scratch/flash_bug/`.

### 2026-10-04 — Task #278: the raw radio wakeup event is RFA operation 0x60702aa

- **Ledger §112.71** — traced the raw event that must drive the RFMGR wakeup completion.
- **★ The RFMGR is an STM** (class `0xc1a8b9d0`, obj `0xc1e145a8`, 8 states × 20 messages); handler
  table `0xc1a8bb40` decoded in full. SLEEP handler[18] `LTE_ML1_RFMGR_WAKEUP_REQ` (`0x4290203`) =
  `0xc0315490` (issue); SLEEP handler[19] `RFA_RF_LTE_WAKEUP_CNF` (`0x60708aa`) = `0xc0315560`
  (completion) ⇒ the completion is a **software message** delivered to the STM.
- **★ The raw radio wakeup event = RFA operation `0x60702aa`** — one of a 1:1 family of 10 RFA ops
  (request `0x60702a1..aa` ↔ CNF `0x60708a1..aa`, offset +0x600). The RFMGR **sends** `0x60702aa`
  (absent from its received-message filter table `0xc1e14520`) and **receives** `0x60708aa`.
- **★★ gp resolved = `0xc3c09000`** (set at `0xc0000670`), unlocking every gp-relative jump table:
  the CNF builder `0xc0316080` table = `0xc1a8b940` (10 entries, idx 9 → WAKEUP → posts `0x60708aa`);
  the RFA server dispatcher `0xc1018620` table A = `0xc1b328a8` (26 entries, **idx 9 → `0xc1018798`
  → `0xc1017818` = the raw WAKEUP handler**).
- **★ Where the stall sits:** the raw op `0x60702aa` is never completed ⇒ `0x60708aa` is never
  posted ⇒ the RFMGR handler `0xc0315560` never runs. The issue side did run (pending set). **The
  resource that stops the RFA layer completing `0x60702aa` at ~900 s remains OPEN.**
- **Artifacts:** `scratch/_rfmgr_stm.py`, `scratch/_rfatbl.py`, `scratch/_find_u32.py`,
  `scratch/_dump_region.py`, `scratch/_rd_ptr.py`.

### 2026-10-04 — Task #277: the RF confirmation stall is a stuck RFMGR wakeup transaction

- **Ledger §112.70** — named the RF entity and the stall signature.
- **★ The "RF driver" is the `LTE_ML1_RFMGR` STM** (class `0xc1a8b9d0`, obj `0xc1e145a8`, 8 states
  INACTIVE/ACTIVE/RX_TUNED/TUNING/TX_TUNED/SLEEP/SCRIPT_EXEC/SCRIPT_BUILD; msgs `0x4290200..06`).
- **★★★★ FALSIFIED:** the RFMGR state is NOT a FATAL/WEDGE discriminator — the FATAL state is BOTH
  4 (TX_TUNED) and 5 (SLEEP); the WEDGE state is 5 (3/3). (The *sleepmgr* state 9/4 remains the clean
  discriminator.)
- **★★★★★ NEW signature:** the RFMGR context field `ctx+0x104` holds the in-flight request
  (`0x4290202` SLEEP / `0x4290203` WAKEUP). **Every event dump in SLEEP has `0x4290203` (9/9);
  every non-event dump in SLEEP has `0x0` (5/5)** (Fisher p ≈ 5×10⁻⁴) ⇒ the RFMGR is stuck in SLEEP
  holding an unserviced WAKEUP_REQ.
- **★★★★ Code path:** an issue/completion pair per request (WAKEUP issue `0xc0315490` / completion
  `0xc0315560`; SLEEP issue `0xc03152f0` / completion @`0xc0315420`). The completion emits the CNF
  and clears `ctx+0x104`; at the event it never runs. The callback slots (`ctx+0x1b8`/`ctx+0x1f0`)
  are **registered (non-zero)** ⇒ **not** a missing-callback bug — the **async RF completion event
  itself** does not arrive (supports hardware/resource, not a software timer). Root remains OPEN.
- **Artifacts:** `scratch/rfmgr_dump.py` (reader).

### 2026-10-04 — P-DET continuation (n=6): the race reproduces; 3 new dumps re-confirm §112.68; a leftover capture script contaminated the soak

- **Ledger §112.69** — the P-DET soak produced one more modem boot with three events under the
  **same fixed observation config**: **FATAL** `lte_ml1_sm_conn_inter_freq_stm.c:712` @ modem-up ≈901 s,
  **FATAL** `lte_ml1_sleepmgr_stm.c:4054` @ ≈900 s, **WEDGE** @ ≈900 s (data death; `crash detected …
  type watchdog` +175 s). ⇒ **FATAL, FATAL, WEDGE again — the §112.67 RACE verdict is now n = 6.**
- **★ The mitigation's ON→OFF transition is visible in one boot.** `msm_subsys: restarting` at
  812.79 s and 1623.23 s (Δ = 810.4 s ≈ the 800 s pre-emptive interval) held the event off across two
  cycles; after it was disabled the first fatal landed at **2524.5 s = 1623.2 + 901.3 s**.
- **★★★★ 3 new coredumps re-confirm §112.68** (`scratch/pdet_end/dumps/`): the 900 s timer's remaining
  is **+21.73 s** (sm_conn fatal) and **+11.55 s** (sleepmgr fatal) — i.e. it fires *after* the event —
  and is **absent** from the wedge dump (already fired ≈4363 s, not re-armed). **★ NEW: the 900 s
  timer is a per-restart one-shot**, armed ≈14–25 s after the preceding recovery.
- **⚠⚠ Measurement-discipline failure.** A leftover capture script `/root/v28_wedgecap.sh` (started by
  a prior session, never stopped) fired on the wedge commit, `rmmod`'d `qcom_bam_dmux`, and forced a
  crash → the driver never re-initialized → no `wwan0`, MM blind, data path dead ~2000 s. New RULE:
  stop prior-session capture scripts before a soak and verify the soak is *arming*, not merely running.
- **⚠ New, untriaged:** `modprobe qcom_bam_dmux` **hung** (>3 min, no dmesg, no module) after the
  forced crash — a possible reload-after-force-crash defect.
- **Recovery:** soak ended; **mitigation restored** (`preemptive_ssr=1` + `a2_pin=1`, interval 800) and
  the device rebooted clean (`wwan0..7` up, MM `Modem/0`).



- **Ledger §112.68** — tasks #272/#273 ("enumerate boot-armed timers to name the ~900 s timer").
  The modem's timer API is the QuRT timer library (create `FUN_c0914b30`; arm-with-duration
  `FUN_c0914e10`; `FUN_c0914dc0` scales by `0x24a`=586 ≈ 19.2 MHz/32768 Hz).
- **★ New instrument — the timer object pool.** `FUN_c0915020` resolves a handle to
  `&DAT_c2cd4de0 + idx*0x90`, 256 entries, in **BSS ⇒ readable from any coredump**. Field map
  (+0x1c callback, +0x30 expiry, +0x40 arm-time, +0x50 duration, +0x84 magic `0xcacacac`, +0x88
  owner). **Units proven exactly** (`expiry − arm = dur_ms × 19200` for all 57 entries): duration in
  ms, tick clock 19.2 MHz. Tools `scratch/timer_pool.py`, `timer_analyze.py`, `timer_obj.py`.
- **★ `max(+0x40)` = the modem uptime** (verified vs dump filenames 917.4/1536.0/13986.1/13518.5/302.4 s)
  ⇒ the pool gives a reliable uptime + a full live-timer census from any coredump.
- **★ The ML1 timers are directly visible** (callback `c02d7bd0`): 50/100/330/430/530/630/1000/5000 ms,
  re-armed continuously; at the fatal the expiring ones are the 50 ms (state-20 watchdog) + 100 ms.
- **★★★★★ NEGATIVE: the ~900 s trigger is NOT a QuRT software timer.** A 900,000 ms (900 s) timer
  *does* exist (callback `d051e254`, dynamically allocated, ~19/31 dumps) but it is armed at ~30–41 s
  (≈ attach) and in every boot-anchored dump its **remaining is +16…+27 s ⇒ it fires ~20 s AFTER the
  fatal**. No pool timer has an expiry at ~900 s. ⇒ redirect to a non-QuRT source (hardware / RPM /
  MCPM timer) or a counter/resource.
- The `900000` constant is an **IMS `PDPRATHandlerVoLTE.cpp`** config default (3 sites, with 30 s/60 s/
  600 s/5 s neighbours); the 900 s timer's callback `d051e254` is outside the ELF segments.
- ⚠ Tick clock: reset on cold boot, **persists across an SSR** (`mcpm_run/fatal_901` `max(now)`=2526 s
  at ATS uptime ≈900 s) ⇒ "900 s" is an ATS-uptime figure.

### 2026-10-04 — P-DET RESULT: the ~900 s manifestation is a RACE (fixed config → FATAL, FATAL, WEDGE)

- **Ledger §112.67 / Doc 245 §4** — the pre-registered determinism test (P-DET) ran autonomously:
  fixed config (stock baseband, `preemptive_ssr=0`, `a2_pin=1`, continuous traffic); the event fires at
  modem-up ≈900 s on every boot and the modem auto-recovers (fatal → crash-recovery; wedge →
  stall-watchdog Stage-3 SSR).
- **★★★★★ H0 FALSIFIED (n = 3).** Event A (=run 8) **FATAL** `lte_ml1_sm_conn_inter_freq_stm.c:712`
  @ mu 900.385 s; event B **FATAL** `lte_ml1_sleepmgr_stm.c:4054` @ mu 900.838 s; event C **WEDGE**
  @ mu ≈903.7 s. The **timing is deterministic** (spread ≈3.3 s) but the **manifestation is a race**.
  ⇒ "if it's a quiet failure it should always be a quiet failure" is **NO**.
- **★★★★ Variable victim** — events A and B (identical config, back-to-back) assert in two different
  ML1 SMs. One upstream trigger, two independent random outcomes (which SM notices first; assert vs
  silent data death).
- **★★★★ Wedge on STOCK firmware** (event C) ⇒ the wedge is **not** an artefact of the v13/v28 ring
  instruments. Telemetry: RF still reports a cell (RSRP −90 dBm, SNR 10.6 dB) while `RX+0` =
  "connected but the data path is dead" (§112.71).
- Instruments: device `/root/pdet.sh` + `/root/v13_traffic.sh`; host `scratch/pdet_collect.sh` →
  `scratch/pdet_collect.log`. Device left in the observation config (`preemptive_ssr=0`) — restore `=1`
  for the mitigation.

### 2026-10-04 — RUN 8: a FIFTH ~900 s fatal signature (`lte_ml1_sm_conn_inter_freq_stm.c:712`, ML1 MGR) with a HEALTHY sleepmgr

- **Ledger §112.66 / Doc 245 §3** — stock baseband `1a6f9507…`, `preemptive_ssr_enabled=0`, `a2_pin=1`,
  continuous traffic, cold AP boot → **FATAL** at AP uptime 2524.496 s (modem-up **≈901.3 s**).
- **New signature.** Crash report `file=lte_ml1_sm_conn_inter_freq_stm.c line=712 task=ML1 MGR`; the
  descriptor resolved offline from the coredump's assert-DB (`scratch/a2_descr.py`): `0xc3c80640` →
  message **`Assert 0 failed:`** (unconditional `ASSERT(0)`). This is the **5th distinct ~900 s fatal
  site** (after `lte_ml1_sleepmgr_stm.c:4054`, `a2_power.c:1189`, `lte_ml1_common_timer.c:390`,
  `lte_ml1_sm_idle_stm.c:2913`).
- **★★★★★ The sleepmgr was HEALTHY** (`0xc1e158d0` `+0x04 = 1` ONLINE, `+0x08 = 0x06`). ⇒ **the ~900 s
  event can fatal WITHOUT the sleepmgr RF-wakeup stall** — the sleepmgr state is a *victim's* signature,
  not the root. This reframes the model: the trigger is upstream of the ML1 SMs.
- **The crashing SM consumes RF completions.** Its assert cluster includes line **657**
  `((rfa_rf_lte_l2l_build_scripts_cnf_s *)payload)->req_result == RFA_RF_LTE_SUCCESS` (an RF-driver
  completion check) — the same "waits on the RF layer" shape as the sleepmgr's `RF_WAKEUP_CNF`. The
  *fired* assert was line 712 (`ASSERT(0)`), a default branch; the exact reaching event is unresolved
  (the function was not decompiled; disasm has decode gaps).
- **No F3 precursor** (matches §112.13): 227 normal records in the final 1.0 s (IRAT GRFC script every
  40 ms to −23 ms, MCPM cycle running, A2 power req); the last record is `rf_task.c:336 " get imei
  stoped "`. Differs from the wedge's §112.57.11 staged collapse (a fatal crashes at the event).
- **Track record updated** (Doc 245 §3): run 8 → counts now **WEDGE 4 / FATAL 4**; added the **victim
  SM** as a second uncontrolled variable alongside the MCPM phase.
- Artifacts: `scratch/mcpm_run/dump_fatal_901.bin` (md5 `f955dd1344937a9b6b497ec541df21c8`),
  `scratch/mcpm_run/f3win/`, readers `scratch/crashlog_extract.py` / `sleepmgr_dump.py` /
  `a2_descr.py` / `mcpm_run/f3tail.py`.

### 2026-10-04 — v28 RF-WAKEUP-CNF CALLBACK RING: instrument the RF wakeup path's RF-driver side

- **Ledger §112.63 / Doc 244 §6.8** — v26 (egress) and v27 (ingress) see only the sleepmgr's
  *messages*; neither sees the RF driver that produces `RF_WAKEUP_CNF`. v28 hooks the ONE function
  that emits every `RF_*_CNF`, `0xc039e580`, at its **function ENTRY** — the only clean packet
  boundary where both `r2` (the CNF selector) and the true caller `r31` are live.
- **Code map:** `0xc039e580` is entered only via four tail-call stubs that set the selector —
  `0xc039e570` r2=0 → `RF_WAKEUP_CNF 0x42b0801`; `0xc03a0b70` r2=1 → `RF_EXIT_CNF 0x42b0804`;
  `0xc03a0b40` r2=2 → `RF_SLEEP_CNF 0x42b0802`; `0xc039e700` r2=3 → `RF_ENTER_CNF 0x42b0803`
  (switch verified at `0xc039e688`). The stubs are **completion callbacks** registered in
  `0x4290203` RF-request payloads (`0xc039b95c` stores `0xc039e700`; RF dispatcher `0xc0313f1c`),
  so at `0xc039e580` entry `r31` = the **RF driver** return address.
- **v1 crash (mid-packet continuation).** v1 hooked the 4-byte `{ call 0xc02d1140 }` at
  `0xc039e584` and continued at `0xc039e588` — which is **MID-PACKET**: a stream decode shows
  `0xc039e584` is a **FUSED 16-byte packet** `{ call; r17=r2; r18=r0; memd(r29+#0x10)=r19:18 }`
  (spans `0xc039e584..0xc039e594`). The re-executed `r18 = r0` (r0=0) fired
  `Assert cnf_msg != NULL failed` at `lte_ml1_sleepmgr_stm.c:4344` (task ML1 MGR, uptime 16 s).
  v2 re-targets the ENTRY and continues at `0xc039e584`.
- **★★★★★ v2 crash → THE PARSE-BITS BUG (root-caused + fixed).** `site_packet4` built the
  `{ jump 0xc003054c }` word with base `0x58004000` → packet parse bits `[15:14]=0b01`, but a
  **standalone** 4-byte instruction needs **parse=0b11**. With parse=01 the CPU decoded the NEXT
  packet as extra instructions of this packet → **illegal-instruction `:Excep` at the site PC
  `0xc039e580`** (all GPRs 0, `BADVA=0xe2eea008`, task AMSS0, modem crash-loop ≈20 s uptime).
  Fix = base `0x5800C000`. Verified against `llvm-mc` label assembly: site→cave = **`e6cf9259`**
  (correct) vs buggy `e64f9259` (only bit 15 differs). ★ v27's stock site `0xc039d46c` was
  parse=01, so v27's parse=01 coincidentally matched (why v27 ran). STOCK firmware ran 360 s clean
  (`scratch/stock_watch.sh`) = negative control.
- **Cave (`0xc003054c`, 120 B)** reproduces the entry prologue duplex
  `{ memd(r29+#-0x10)=r17:16; allocframe(#0x20) }` byte-for-byte, then logs
  `{seq, sel=r2, caller=r31, state}` to ring `0xc1d4c600` (header/beacon `0xc1455000`, marker
  `0x76323801`), then jumps to `0xc039e584`. Cave uses only `r6`–`r11`; `r2`/`r19`/`r29` preserved.
- **Built from pristine stock** (`scratch/diag_patch_v28/build_diag_patch_v28.py`), site + cave
  byte-verified by re-disassembly, b01 seg16/seg5 hashes rebuilt, hash re-verify PASS.
  **`modem.mdt md5 = 0f57e8836314ac863f7053316979b220`** (parse-fixed).
- **Deployed** to `/lib/firmware` with sha256 read-back PASS on all 5 files
  (`scratch/deploy_v28_ring.py`); `preemptive_ssr_enabled=0`, `ssr_enabled=1`, `a2_pin=1`, dumpwatch
  running.
- **★ RESULT (ledger §112.64).** RUN 1 (cold modem) **WEDGED** — data-path death, NO fatal, no
  coredump; the stall-watchdog's Stage-3 SSR at AP 1435 s **wiped the ring**. RUN 2 (the
  crash-recovery warm restart) **FATALED** at **modem-uptime 902.77 s** (`lte_ml1_common_timer.c:390`,
  task `tmr_slave3`, PC `0xc087a804`); ring captured (`scratch/v28_run/dump_fatal_905.bin`,
  md5 `dc64876e…`): **seq 2541, strictly alternating RF_WAKEUP_CNF ×1270 / RF_SLEEP_CNF ×1271, no
  non-alternating runs** — the **last emission is a RF_SLEEP_CNF**, i.e. the final **RF wakeup
  confirmation is absent**. Sleepmgr object `0xc1e158d0` `+0x04 = 9` = **OFFLINE_WAKEUP** at the
  fatal. **NEW:** the caller resolves the RF-driver completion paths — **ONLINE** wakeup `0xc0314d48`,
  **OFFLINE** wakeup `0xc03155fc`, sleep always `0xc0315440`; the missing confirmation is an OFFLINE
  wakeup. ⇒ the stall is on the **RF-driver completion side**, not the sleepmgr; **root cause still
  OPEN** (third independent confirmation of the RF-wakeup stall). ★ New device-side instrument
  `scratch/v28_wedgecap.sh` forces an on-demand coredump at the stall-watchdog's commit point, so a
  WEDGE (no coredump) can be captured before the Stage-3 SSR wipes the ring.
- **★ RUN 3 — REPRODUCED.** The next warm cycle fataled again (`lte_ml1_common_timer.c:390` at
  modem-uptime **901.98 s**); ring **seq 2441**, identical structure (WAKEUP ×1220 / SLEEP ×1221,
  last = SLEEP, sleepmgr `+0x04 = 9` OFFLINE_WAKEUP, non-alternating runs = 0) — dump
  `scratch/v28_run/dump_fatal_903.bin` (md5 `740c0e33…`). ⇒ reproducible across consecutive warm
  restarts (n = 2). ⚠ Open hypothesis (n = 1): the only wedge (RUN 1) was the cold-boot event; both
  warm-restart events were FATALs.
- **★★★★★ WEDGE CAPTURED (ledger §112.65).** With v28 re-deployed and `preemptive_ssr_enabled=0`, a
  **warm** modem restart (`echo restart > /sys/kernel/debug/msm_subsys/modem`) wedged: data death at
  modem-uptime **≈898–928 s**, no fatal, no SSR restart. `v28_wedgecap.sh` forced the on-demand dump at
  the watchdog commit (AP 8355 s) → `scratch/wedge_run/dump_wedge_8357.bin` (85,398,475 B,
  md5 `db8b304a…`). **Sleepmgr `0xc1e158d0` `+0x04 = 4` = ONLINE_WAKEUP** ⇒ **confirms §112.58:
  FATAL ⇒ state 9, WEDGE ⇒ state 4** (n = 2 for the wedge). Ring **seq 3383** (WAKEUP ×1691 /
  SLEEP ×1692, **last = RF_SLEEP_CNF**) — the **same** missing-final-wakeup signature as the fatal ⇒
  the RF-CNF tail alone does **not** discriminate. **NEW discriminator — the REGIME:** the fatal's last
  76 emissions are all OFFLINE-regime (state 9); the wedge's last 10 are ONLINE-regime (state 4), having
  transitioned **OFFLINE→ONLINE ~3 s before capture**. ⚠ Caveat: the wedge is captured ~145–160 s after
  the data death (at the commit), so the state may be a phase; n = 2. ⚠ **§112.64's cold/warm
  hypothesis is FALSIFIED** (this wedge was a warm restart — the manifestation is a race). ⚠ LIMITATION:
  the F3 stream was silent for the whole v28 modem life (AP 7301→8359), so no F3 for this specimen.
- **Restored to steady state:** `preemptive_ssr_enabled=1` (interval 800, `a2_pin=1`), firmware rolled
  back to stock `1a6f9507…`, diagnostic watcher stopped.
- Reader `scratch/read_v28_ring.py`; monitors `scratch/v28b_wait.sh` / `scratch/stock_watch.sh`.
  Reversible: `scratch/deploy_v28_ring.py --rollback` (stock `1a6f9507…`).
- **Memory:** `project_v28_rf_cnf_ring.md`; new trap `feedback_patch_parse_bits.md` (a general
  Hexagon site-packet lesson — always verify parse bits with `llvm-mc`).

### 2026-10-04 — v27 SLEEPMGR INGRESS RING: confirms the missing RF wakeup confirmation (ingress side)

- **Ledger §112.62 / Doc 244 §6.6–6.7** — v26 logged the sleepmgr **egress**; the OFFLINE *trigger*
  is an **ingress** event. v27 hooks the ML1-dispatch entry `0xc039d460`'s call packet `0xc039d46c`
  (`{ call 0xc02ef910 }` → `{ jump 0xc003054c }`), the only point where both `r16 = r1` (msg id) and
  the true caller `r31` are live. 128 B cave in `modem.b05` @`0xc003054c` logs
  `{seq, msg_id, caller, state}` to ring `0xc1d4c600` (header/beacon `0xc1455000`, marker `0x76323701`).
- **Built from pristine stock** (`scratch/diag_patch_v27/build_diag_patch_v27.py`), site + cave
  byte-verified by disassembly (site jump target `0xc003054c`; cave constants all correct),
  b01 seg16/seg5 hashes rebuilt, hash re-verify PASS. **`modem.mdt md5 = 030f528376f38cce5a1e9fb0803075de`.**
- **Deployed** to `/lib/firmware` with sha256 read-back PASS on all 5 files
  (`scratch/deploy_v27_ring.py`); `preemptive_ssr_enabled=0`, `ssr_enabled=1`, `a2_pin=1`, coredump
  enabled, dumpwatch running; AP rebooted 2026-10-04T09:08Z, v27 modem booted clean.
- **RESULT (H1/H2/H3 PASS):** fatal `lte_ml1_common_timer.c:390` at AP dmesg `914.458760 s`; dump
  `scratch/v27_run/dump_fatal_916.bin` (md5 `4df0b22e…`); `seq=22564`, window seq 18469..22564
  (~163 s). **The hook is sleepmgr-specific** (all 23 msg-ids are sleepmgr messages ⇒ `r1` = msg id,
  resolving the §4.4a ambiguity). **★ The anomaly matches v26 from the ingress side:** `STMR_ON_REQ`
  occurs 266× and is preceded by `RF_WAKEUP_CNF` **265×** — the **sole exception is the LAST one**
  (seq 22561, state 9 `OFFLINE_WAKEUP`): the sleepmgr **armed its sleep timer without the RF-wakeup
  confirmation**, then the ML1 timer slave asserted. RF wakeup was healthy for the previous ~163 s.
  Strengthens the RF-death model; **root cause still OPEN**. Reproduce: `scratch/analyze_v27_cycles.py`.
- Reversible: `scratch/deploy_v27_ring.py --rollback` (stock `1a6f9507…`).

### 2026-10-04 — v26 funnel ring CAUGHT THE FATAL: the last sleepmgr cycle omits `RF_WAKEUP_CNF`

- **Ledger §112.61 / Doc 244 §6.5** — the v26 image (`modem.mdt f57ed422…`) hooking the sleepmgr
  ML1-message **send funnel `0xc03927d0`** (body packet `0xc03927d4` → ring
  `{seq,msg_id,caller,len|state}`) ran with `preemptive_ssr_enabled=0`; fatal at AP dmesg
  `915.260490 s` / crash-report uptime `0:15:02`, `lte_ml1_common_timer.c:390`, **Task `tmr_slave3`**,
  PC `0xc087a804`, BADVA `0`. Dump → `scratch/v26_run/dump_fatal_916.bin`.
- **P-V26-FUNNEL scored: H1/H2/H3 PASS, H4 PASS (n=1).** `seq=40111`, 4096 live entries; the egress
  is a regular ~20–21-message sleep/wake cycle (~192 repeats, **≈44 msg/s**). **The last cycle alone
  is anomalous:** `STMR_ON_REQ (0x42b0206)` is preceded by `RF_WAKEUP_CNF (0x42b0801)` **191/192
  times** — the sole exception is the **last** one (`seq 40110`), preceded by `42b0405`. Last four:
  `40a020f → 42b0405 → STMR_ON_REQ → 42b0406 → FATAL` ⇒ the sleepmgr **armed its sleep timer without
  the RF-wakeup confirmation**, then the **timer slave** fataled.
- **The ring is EGRESS** ⇒ it cannot see the *incoming* message that drives the sleepmgr into
  OFFLINE_WAKEUP (state 9, still frozen at the fatal) — the next instrument must hook the sleepmgr
  **ingress/dispatch** (Doc 244 §5, §112.61.7).
- Reusable: the recorded `caller` is `r31` = the **next packet boundary** (a fused `call` returns
  `call+12`, not `call+4`); the ring window is `[max(1,seq-N+1) .. seq]` (seq is pre-incremented).
  The earlier `0xc5000000` boot-crash (zero-filled coredump region ≠ writable runtime region) is
  recorded as §112.60.3. Reversible: `scratch/deploy_v26_ring.py --rollback` (stock `1a6f9507…`).
  Ledger + Doc 244 + memory updated in the same session.

### 2026-10-04 — v13 run-2 CAUGHT THE FATAL: the sleepmgr is FROZEN in `ONLINE_SLEEP_WAIT` (state 2)

- **Ledger §112.57.14** — the §112.57.13 next-step experiment (same v13 ring firmware
  `daa903be…`, `preemptive_ssr_enabled=0`, continuous traffic, cold boot) produced a **FATAL** this
  time: `fatal error received: lte_ml1_sleepmgr_stm.c:4054` at **AP-up 913.92 s** (modem uptime ≈900 s),
  recovery at 915.63 s (**~1.7 s** — the modem's own fast SSR). Auto-coredump pulled to
  `scratch/v13_run2/dump_run2_fatal.bin` (md5 `7cae3307…`); crash report Task=`slpc`, Uptime `0:15:00`.
- **The ring's last transition is INTO state 2** (`seq 13297: 1 -> 2`, ONLINE -> ONLINE_SLEEP_WAIT) and
  **there is no `2 -> 3`**; live `*(0xc1e158d4)=2`. ⇒ the sleep entry never completed — exactly what
  `ASSERT(stm_get_state(SLEEPMGR) == SLEEP)` rejects. Run-1's wedge had last `3 -> 4`, live state 4.
- **★★★★★ UNIFIED MODEL** — the ~900 s event is the **MCPM sleep/wake power-collapse cycle stopping**;
  the sleepmgr freezes at its current phase. Phase = **sleep entry (state 2)** ⇒ the sleep-entry
  deadline assert fires ⇒ **FATAL**; phase = **wakeup (state 4)** ⇒ **WEDGE**. This refines §112.57.13:
  the sleepmgr stall is the *phase that trips the assert*, not a separate event.
- **★★ Correction to §112.20/§112.57.12** — the `a2_power.c:1189` assert is a **SPIN-COUNT** watchdog,
  not a time watchdog: every a2-fatal dump has `0xc28602f8=45051, 0xc28602fc=901` at **variable** uptimes
  (18/17/27/28/288/576 s); every ~900 s ML1-class fatal/wedge has both counters **0**. The `901` constant
  was recovered from the missing second word of the `c0504314` packet (from the stock ELF).
- Reversible (`scratch/deploy_v13_ring.py --rollback`, stock `1a6f9507…`). Ledger + memory updated in
  the same session.

### 2026-10-04 — v13 STM-state-writer ring WORKS; the run WEDGED with a HEALTHY sleepmgr (the deadlock is a VARIANT)

- **Ledger §112.57.13** — the v13 ring firmware (`modem.mdt daa903be…`) deployed to `/lib/firmware`
  with `preemptive_ssr_enabled=0`; cold boot clean. Soak clean to AP-up 907.95 s, then at **AP-up
  925.10 s** `ping=0` **with `fatals=0 ssr=0`** — a **WEDGE** (modem `running`, `mmcli`
  `connected/lte/attached`). No fatal ⇒ no auto-coredump ⇒ captured **on demand** (§6.2a) →
  `scratch/v13_run/dump_v13_wedge.bin` (md5 `6d731dcc…`).
- **Ring** (`scratch/read_v13_ring.py`): `marker=0xc1455000` ✓, **`seq=12922`**; the 128-entry ring is
  a **continuous normal cycle** (`1->2->3->4->5->1`, `1->6->7->8->1`) to the **last entry `3->4`**;
  live `*(0xc1e158d4)=4` (`ONLINE_WAKEUP`). ⇒ the sleepmgr **never stalled** this run (≈13.5
  transitions/s, cycle ≈0.37 s). **P-V13-STM: H1 FALSIFIED** (state 11 first entered seq 683 ≈50 s,
  not boot), **H2 UNSCORED** (ring wrapped), **H3 FALSIFIED** (cycle ≈0.37 s, not 900 s).
- **F3** (`scratch/v13_run/f3/`): byte rate 3→8–9 MB/5 s at up 913–967; the **same staged shutdown**
  as §112.57.11 — power layer (`a2_power`/`mcpm_npa`/`mcpm_saw`/`pgi_msgr`) collapses at up ~913, then
  CM/NAS/QMI, leaving only `cfm_cpu_monitor`.
- **★★★★★ REFRAMING** — the sleepmgr deadlock is **one manifestation, not the common event**. The
  common feature of the ~900 s event (fatal AND wedge) is the **staged shutdown of the MCPM/power
  layer**; the sleepmgr `ONLINE_SLEEP_WAIT` stall is a **variant** that adds the `sleepmgr_stm.c:4054`
  assert (and thus the modem's fast ~12 s self-recovery).
- Reversible (`scratch/deploy_v13_ring.py --rollback`, stock `1a6f9507…`). Ledger + memory updated in
  the same session.

### 2026-10-04 — v14 FAILED (NEG): a 4th ~900 s signature; full-corpus site set; v15 = suppress all four

- **Ledger §112.57.10** — v14 (`ecdaf2c9…`) with the pre-emptive SSR off was clean through AP-up
  892.96 s, then at **AP-up 924.21 s** the modem fatally crashed and **SSR'd** (`916.816818 fatal
  error received: lte_ml1_sm_idle_stm.c:2913` → `MBA booted … loading mpss` → up at 936 s). A **4th
  signature not covered by v14**; **P-V14-SUPPRESS = NEG**.
- **The 4th signature (D)** — `lte_ml1_sm_idle_stm.c:2913`, task `ML1 MGR`, call-site packet
  `0xc03556a8` (descriptor `0xc3c793f0` → `a2_descr.py`); sibling `0xc03556c0` → `…:2923`; clean
  continuation `0xc0030098`.
- **Full-corpus tally (63 dumps, 43 classified) — exactly 4 ~900 s signatures:** A
  `lte_ml1_sleepmgr_stm.c:4054` (18), B `a2_power.c:1189` (11), C `lte_ml1_common_timer.c:390` (9),
  D `lte_ml1_sm_idle_stm.c:2913` (1). Off-900 (out of scope): `a2_power.c:2949`, `a2_taskq.c:759`.
- **Mechanism re-read** (`scratch/scan_assert_sites.py`) — `FUN_c0879150` is the generic assert/error
  **logger** (**17 708** call sites), not the halt; `0xc087a76c` is the crash-context **saver**
  (`r1 = pc` at `0xc087a804` ⇒ the reported PC is a fixed red herring); the real halt is `stop(r0)` at
  `0xc087a818`. The scanner maps each assert packet's descriptor → file:line (verified: the 8 ML1
  sites resolve to 8 distinct lines, only 390 fires).
- **v15** (`scratch/diag_patch_v15/`) — v14's 10 sites + the D pair = **12 sites**; deployed
  **2026-10-04** with SSR off; `modem.mdt 1eae18690c2ad8fe8769938be09c6cde`, `modem.b16
  717198341650743d128abb83332e5392`. All 13 jumps independently decoded. Reversible
  (`deploy_v15.py --rollback`).
- **P-V15-ALLFOUR** pre-registered: H1 = no fatal past modem-up 900 s (soak to AP-up ≥1500 s);
  H2 = data alive past 900 s (not a zombie); NEG = a 5th signature ⇒ per-site suppression abandoned,
  root cause mandatory. **Scored 2026-10-04 06:16: NEG (H1 PASS, H2 FAIL)** — see the §112.57.11
  entry below.

### 2026-10-04 — v15 FAILED (NEG): assert-suppression converts the FATAL into the WEDGE; the fatal IS the modem's fast recovery trigger

- **Ledger §112.57.11** — v15 (`1eae1869…`) with the pre-emptive SSR off was clean through AP-up
  **1383.6 s** (`fatals=0`) — **H1 PASSES** — but the data path was **dead**: direct `ping -I wwan0`
  = **100 % loss**, `wwan0` RX **frozen at 21678 B** while TX grew, `mmcli` still
  `connected/lte/83 %/attached`. **H2 FAILS ⇒ P-V15-ALLFOUR = NEG.**
- **Recovery was the userspace `modem-stall-watchdog`** (`modem-bearer-watchdog`, `stall_timeout=60`):
  Stage 1 `wds-go-dormant` → Stage 2 bearer rebuild → both **insufficient**; **Stage 3 SSR at up
  1414 s** restored RX. Telemetry at confirmation: `RSRP=-86 dBm, SNR=16.0 dB, Cell ID=4399665,
  WDS dormancy='traffic-channel-active', TX=317, RX=246` — a **healthy control plane with a frozen
  user-plane RX**. ⇒ **~504 s of dead data** (up 910→1414) vs the fatal's **~12 s** recovery.
- **F3 ground truth** (`scratch/f3_v15/`, chunks up 898–923) — a **STAGED SHUTDOWN**:
  `a2_power/mcpm_npa/mcpm_saw/pgi_msgr` die first (~up 910), then `cmlog/qmi_nas/cmss` (~915),
  leaving only the `cfm_cpu_monitor` CPU heartbeat. The modem CPU is **alive**; only the LTE stack
  is dead. Byte burst 2.5–3.0 MB/5 s vs a normal 0.39 MB/5 s at up 898–908 = the collapse flush.
- **Reframing:** the ~900 s **fatal assert IS the modem's own fast recovery trigger**
  (deadlock → RF receiver frozen → assert → `stop(r0)` → SSR → ~12 s). Suppressing it removes the
  trigger and leaves only the slow userspace fallback. **`fatal → wedge` is strictly
  counterproductive (~12 s vs ~504 s). Assert-suppression is a dead axis; the root cause (the
  boot-anchored ~900 s `LTE_ML1_SLEEPMGR_STM` sleep-entry deadlock) is now mandatory.**
- **⚠ Measurement bug found + fixed** — `grep -c "0% packet loss"` also matches `"100% packet
  loss"`, so the soak/fine `ping=` field was bogus. Fixed to use **ping's exit code**
  (`scratch/v15_soak.sh`, `scratch/v15_fine.sh`); historical `ping=` values are not interpretable.
- **Action taken** — v15 **rolled back to stock** (`1a6f9507…`, verified) and the **shipped
  mitigation re-enabled** (`preemptive_ssr_enabled=1`, interval 800 s, `a2_pin=1`); device healthy.
- **Ledger §112.57.12** — decoded `FUN_c05042c8`: a 50 Hz tick that increments `*(0xc28602f8)` and,
  once per 50 ticks, `*(0xc28602fc)++`; otherwise asserts when `*(0xc28602fc) > 900`. Every reference
  to the counter base `0xc28602c0` checked — the **1 Hz counter `0xc28602fc` is written in exactly
  ONE place (the increment) ⇒ never reset** ⇒ the a2 assert is a **deterministic boot-anchored 900 s
  timer**, not a symptom. The ~900 s fatal is a family of boot-anchored timers; suppressing them is
  dead (v14/v15 NEG); the pre-emptive SSR remains the only proven lever; a true fix needs the
  upstream modem-internal ML1/sleepmgr deadlock cause (firmware RE).

### 2026-10-04 — the ~900 s fatal is a family of THREE asserts; v14 suppression fix deployed

- **Ledger §112.57.9** — `crashlog_extract.py` over the archived ~900 s coredumps shows the fatal is
  **three** asserts, each in a different task, all at modem-uptime 0:15:00–0:15:10:
  (A) `lte_ml1_sleepmgr_stm.c:4054` task `slpc` site `FUN_c039ef80` `0xc039f7b8`;
  (B) `a2_power.c:1189` task `a2` site `FUN_c05042c8` `0xc0504320` — a **1 Hz counter `0xc28602fc`
  = 901** (>900 s, never reset) in the fatal dump;
  (C) `lte_ml1_common_timer.c:390` task `tmr_slave3` site `FUN_c02d7bd0` (8 sites).
  Every site is the same `{ call 0xc0879150 ; immext ; r0 = <desc> }` 12-byte packet; `FUN_c0879150`
  never returns, and each caller has a clean continuation elsewhere.
- **v14 fix** (`scratch/diag_patch_v14/`) — overwrite each assert packet with `{ jump <clean> ; nop ;
  nop }` (10 sites, all in `modem.b16`; b01 seg16 re-hashed, mdt rebuilt). Deployed **2026-10-04
  05:24** with the **pre-emptive SSR disabled**; cold boot clean. `modem.mdt
  ecdaf2c97d72da150d367c68d8b7cff8`, `modem.b16 ef1b02e8f5baa885a17a40ef43c92566`. Reversible
  (`deploy_v14_suppress.py --rollback`, stock `1a6f9507…`).
- **P-V14-SUPPRESS** pre-registered: H1 = no fatal past modem-up 900 s (SSR off, soak to AP-up
  ≥1500 s); H2 = data path alive past 900 s (not a zombie); NEG = a signature still fires. **Scored
  2026-10-04 05:38: NEG** — a 4th signature (`lte_ml1_sm_idle_stm.c:2913`) fired at modem-up
  ~916.8 s; see the §112.57.10 entry above.

### 2026-10-04 — v13 instrument debugged (two builder bugs) + early-crash hypothesis falsified

- **Ledger §112.57.7** — v13 deployed; it crash-looped at boot; **two builder bugs fixed**:
  (1) the Hexagon `J2_jump` immediate is **23-bit signed** (sign bit → `word[24]`), not 22-bit —
  the site mis-signed `-0x1F3B44` and jumped to `0xc15edc08`; (2) every `{ r6 = ##X; jumpr r6 }`
  trampoline is a **same-packet hazard** (`jumpr` reads the pre-packet r6) — split into two
  packets. Instrument now cold-boots clean (0 crashes). Image md5 in §112.57.7.
- **Ledger §112.57.8** — "an early-boot SSR/crash arms the ~900 s timer" **FALSIFIED**: a clean
  OpenWrt cold boot (`bootB`) had zero early crashes yet fataled at modem-uptime 900.7 s; Android
  cold boot clean through 2723 s. The arming is powerup-anchored (§112.8), not crash-driven.

### Pending / next

- **Sequenced ~900 s fatal investigation (approved plan)** — user chose order
  1 → 2 → 3:
  1. **RF / interference lead** — DONE (§112.40): the RF/meas layer is the first
     LTE sub-layer to stop, but state-dependent and the F3 does not say why.
  2. **ML1 SERV-MEAS-RSP reply path** — DONE (§112.41): the reply is *downstream*
     of the ML1-wide stall — the producer (`FUN_c01bc8e0`) never runs
     (`slot[+0x01]=0` in 7/7 dumps); no new root.
  3. **New live instrument for the ML1-side counter** — DONE (§112.42): a high-rate
     RAM sampler is **impossible** (TrustZone blocks AP reads); the audit instead
     showed the meas-table flag is confined to (`d2=0`, `e02=1`) — a **new
     confounder `e02`** leaves its fatal-specificity **UNRESOLVED**. §112.41's
     phase-2 conclusion is **RETRACTED** and §105.8's "confound resolved" is
     **UNSUPPORTED**. The decisive control (`P-IDLECTRL2`) was **run but VOID**
     (the idle modem survived to 966 s but read `e02=0`).
  3b. **Positive control for the §105.4 flag (P-FLAGPOS)** — Stage 1 **DONE
     (§112.44)**: the flag is the deterministic output of the event-0x10 handler
     (which clears `d2` then arms), so `(d2=0, armed)` is forced by construction
     and the probabilistic Stage 2 is **superseded** (a snapshot cannot measure
     persistence). The deterministic firmware-ring follow-up was **approved,
     built and run** — **P-MEASRING, DONE (§112.45)**: the technique is proven
     (boot-transparent, captured the real fatal), but the literal "ARM stopped
     first" answer is **confounded** (the event-0x10 stream is early-concentrated
     and stops 814 s before the fatal). **⚠ §112.45.5 CORRECTION:** the proposed
     **v2** (hook `FUN_c02fda90` + `FUN_c02d7bd0`) is **REDUNDANT** — that is
     exactly the **v7–v11** instrument (items 79–86), and item 97 closed the
     armer's upstream statically. The only new piece (effective write/drain on
     the LL1 path) is largely pre-answered by the slot state. ⇒ **no v2 firmware
     patch is obviously warranted**; the next move is a decision, not a build.
  3c. **The state-20 ARM-vs-CANCEL lifecycle ring (P-CANCELRATE)** — **DONE (§112.46),
     VOID/CONFOUNDED**: the run **wedged** (no fatal in 34 min; the fatal grep was
     empty from AP 159→2175), and the "CANCEL" hook site `0xc034e1f0` is a
     **shared dispatch merge point** (15+ selectors) so it counts **every ML1
     dispatch**, not the ctx0 cancel. The ARM (`FUN_c02fda90`, exactly 2 call
     sites) fired only **32×**, **corroborating §63.5's dormant finding** and
     **refuting the pre-registration's "0.63/s armer" premise** (that rate is the
     dispatcher's). **⚠ RETRACTED by §112.47.4b** — the v7 instrument (the SAME two
     sites) measured 674 calls / 902.29 s idle = 0.747 Hz, i.e. the pre-registration
     was plausibly RIGHT and this 32 is the outlier; the ARM rate is OPEN.
  3d. **Offline closure of 3c's "open identification"** — **DONE (§112.47), NEGATIVE**:
     the ctx0 arm is **genuinely only `FUN_c02fda90`** (its 2 sites are the only
     two that pass selector `0x80` to the sole pending-bit setter `FUN_c02fba64`;
     the other 13 sites arm the *other* eight contexts). There is **no hidden
     high-rate armer** ⇒ a v3 ring is **REDUNDANT**. Also corrected: `obj[+0x38]`
     is a **fixed context ID (20..28)**, not a transient state. The ARM rate is
     **contested (4 / 32 / 225 / 674) and OPEN**; the static closure is
     rate-independent. A thermal-signature search in the F3 was a **NULL**.
- **Cleanup patch (carried over, DEFERRED)** — fold the `hang_probe_t4` /
  `f3cap` / `coredump-enable` blocks out of `/etc/rc.local`. Deferred by user
  decision: it fixes no crashes and removes the on-device safety net for a
  possible patch-831 regression.
- **`a2_pin` default decision (carried over, open)** — whether the shipped image
  should default `a2_pin=1` (currently opt-in via uCI set; shipped config sets
  it). A design/risk question: `a2_pin=1` suppresses the cold-boot
  `a2_power.c:1189` fatal but is a **mitigation, not a root-cause fix**
  (§112.24/§112.28); it holds the modem's A2 power-control pin across pre-emptive
  SSRs.

### Changed — modem stability ledger

- **§112.57.6 (v13 — the corrected STM-state-writer ring, BUILT + offline-verified)** — with the
  §112.57.5 correction restoring the premise, the v12 ring is rebuilt as **v13** with the hook on
  the **whole packet at `0xc0fe174c`** (12 bytes → `{jump tramp; nop; nop}`, parse bits 01/01/11,
  hand-built because the target is out of assembler range), trampoline `0xc0dedc08` → cave
  `0xc003054c` (136 B) that filters `r16 == 0xc1e158d0`, logs, then **replays the packet exactly**
  and branches to `0xc0fe1758`/`0xc0fe1764`. Register safety **proven** (the engine uses no r6–r15).
  Save area `0xc1455000`: marker + seq + `first_seq[0..11]` (ring-wrap insurance, sentinel-guarded) +
  a 128-entry `{seq,r31,old,new}` ring. **Disassembly-verified** (`scratch/_verify_v13.py`); hash
  re-verify PASS. **NOT deployed** (dongle offline this session). Builder
  `scratch/diag_patch_v13/build_diag_patch_v13.py`; deploy = copy `modem.mdt`/`b01`/`b05`/`b16`
  into `/lib/firmware/` + reboot. See ledger §112.57.6.

- **§112.57.5 (CORRECTION to §112.57.3 — `0xc0fe1754` IS the general STM state writer)** — a
  **same-session self-correction**. §112.57.3 wrongly concluded the store `memw(r16+#0x4) = r18`
  at `0xc0fe1754` is skipped when the packet's conditional jump is taken. In Hexagon a packet
  issues as a unit — *"the instructions execute in parallel, and their results are written at the
  end of the packet. Branch decisions are also taken at packet end"* (Hex-Rays *Hexagon support*;
  Qualcomm V60/V61 PRM §3.3) — so the store **always executes**. **Confirmed by Ghidra
  decompilation** of the engine `FUN_c0fe1460`: `iVar4 = piVar9[1]; piVar9[1] = iVar6;` sits
  *outside* the `if (iVar6 == -2)` branch ⇒ unconditional. Logical proof: the engine reads
  `piVar9[1]` *after* the handler to detect a state change, so the engine (not the handler) must
  write it, and `0xc0fe1754` is the only `memw(obj+4) = …` in the whole STM cluster. ⇒ **§112.56.6
  stands; §112.57.3 retracted.** The v12 ring's *premise* is restored; only the *implementation*
  (whole-packet hook at `0xc0fe1740`, replay the packet, `jumpr` back to `0xc0fe174c`) was ever
  blocked. Offline only; no device touched. See ledger §112.57.5.

- **§112.57 (the v12 STM-state-writer ring — BLOCKED; two corrections)** — executing §112.56.9's
  recorded next step (a firmware ring on the STM state writer). The instrument was built,
  hash-verified and disassembly-verified, then **NOT deployed** (deployed once, immediately rolled
  back) because two offline checks falsified its premise. **(1) CORRECTION — the AP loads the
  baseband from `/lib/firmware` (persistent ext4 overlay), NOT from the `/dev/mmcblk0p3` `modem`
  partition.** A `fastboot flash modem` of the p3 FAT16 had **no effect** on the running baseband
  (`/lib/firmware/modem.mdt` stayed stock `1a6f9507…`); p3 is not mounted, no script copies it, and
  the DT has no `firmware-name`. To deploy a patched baseband, copy the changed `modem.*` files into
  `/lib/firmware/` and reboot. **(2) CORRECTION — the site `0xc0fe1754` is the 3rd instruction of a
  3-instruction PACKET** (`{ if(!p0.new) jump:t 0xc0fe1764; p0=cmp.eq(r18,-2); compound }`); a bare
  `jump` replacement breaks the packet parse bits (verified with `llvm-mc`, which reproduces the
  firmware bytes exactly). **(3) ⚠ CORRECTION to §112.56.6 — RETRACTED by §112.57.5** — this
  claimed the write at `0xc0fe1754` is **conditional on `r18 == -2`** and so is **NOT** the general
  state writer. **That is WRONG** (Hexagon same-packet instructions all execute); `0xc0fe1754`
  **is** the general STM state writer and §112.56.6 stands. Both `p3` and
  `/lib/firmware` were **restored to stock in the same session**; the running modem was never at
  risk. Root cause remains OPEN. See ledger §112.57.

- **§112.56 (the sleepmgr, decoded from the coredump — state names + full 31-message table
  byte-verified; the armer's ONLY caller; the generic STM/scheduler frameworks)** — continuing
  §112.55's read-only next steps 1–2. **(1)** The 12 state names are now **byte-verified from the
  coredump string pool** (each state-table `name` is a real `char *`): `INACTIVE, ONLINE,
  ONLINE_SLEEP_WAIT, SLEEP, … OFFLINE_SLEEP_WAIT`; class `0xc1a94f70` carries the literal
  `"LTE_ML1_SLEEPMGR_STM"`; instance `0xc1e158d0` has `+0x04 = state = 2`, `+0x14 = ctx
  0xc20f1680`. **(2)** The **full 31-entry message table** (`0xc1a95088`, `{name,id}`) is recovered
  (supersedes §112.55.6's partial list), incl. `LL1_SYS_SLEEP_CNF 0x040a0808`, `RF_SLEEP_CNF
  0x042b0802`, `GO_TO_SLEEP_REQ 0x042b0209`, `STMR_ON_REQ 0x042b0206`. **(3)** The armer
  `FUN_c0396fa0` has **exactly one reference in the whole firmware** (`call` @ `c0398764`, in the
  `OFFLINE_SLEEP_WAIT` handler `FUN_c03986b0`) and **zero** raw-pointer occurrences ⇒ **the
  "armed in state 11, fires in state 2" paradox is PROVEN real**. **(4)** `FUN_c0396f30` (state 2
  entry) is **short** (returns at `c0396f84`); §112.55's "big body" was a **Ghidra merge** of three
  adjacent functions (its §112.55.5 conclusion still stands). **(5)** `FUN_c0397150` is a shared
  helper called by **both** state 2 and state 11; it issues an **`LTE_LL1_SYS` request
  `0x040a0209`** (≠ `GO_TO_SLEEP_REQ`). **(6)** The **generic STM framework** is located: getter
  `stm_get_state(obj)=*(u32*)(obj+4)` @ `0xc0fe1960`; the transition writes `memw(obj+4)=newstate`
  @ `c0fe1754` (old-state exit `0xc0fe11e0`, new-state entry `0xc0fe11f0`). **(7)** The scheduler
  `0xc0b6xxxx` is a **generic** service (callers in ≥6 unrelated modules) ⇒ slot id 4 is the
  sleepmgr's own. **(8)** Context: `ctx+0x068 = 0x040a0808` (LL1_SYS_SLEEP_CNF's id); six embedded
  sub-timer structs `{base=0xc361a3f0, id=4, state=0xdeaddead}`; states 2/3 handlers are
  near-identical clones. ⇒ **the read-only path is saturated**; the remaining unknowns are
  **dynamic** ⇒ the decisive instrument stays the §112.55.7 **firmware ring on the STM state
  writer**. **Root cause still OPEN.** Offline, read-only; no device write, no patch.
- **§112.55 (THE FATAL IS A TIMER-EXPIRY CALLBACK — and the 12-state map; CORRECTS §112.54.3)** —
  continuing §112.54's read-only next steps 1–2. **(1) The sleepmgr STM has 12 states**, not 4
  (class `+0x14 = 12`; table `0xc1a94fc8` runs exactly 12×0x10 B and ends at the message table
  `0xc1a95088`): `INACTIVE, ONLINE, ONLINE_SLEEP_WAIT, SLEEP, ONLINE_WAKEUP, TTL_WAIT,
  LIGHT_SLEEP_WAIT, LIGHT_SLEEP, LIGHT_SLEEP_WAKEUP, OFFLINE_WAKEUP, OFFLINE_RECORD,
  OFFLINE_SLEEP_WAIT` (live `2 = ONLINE_SLEEP_WAIT` unchanged). **(2) Assert texts resolved**
  (`a2_descr.py`): `0xc3c81a40` = `lte_ml1_sleepmgr_stm.c:4054` `Assert stm_get_state (
  LTE_ML1_SLEEPMGR_STM ) == SLEEP failed`; `0xc3c81a50` = `…:4089` and `0xc3c81a00` = `…:10903`
  are `serv_cell != NULL`. The decompressed ERR_FATAL record in **both** coredumps reads exactly the
  4054 text. **(3) `FUN_c039ef80` is a software-TIMER EXPIRY CALLBACK, not a handler**: the fatal
  stack carries the scheduler array base `0xc1d9fef0`, the id-4 slot `0xc1da0830`, the stride
  `0x940 (=4×0x250)`, the stamp `0xe_e81861fe`, and the return address `0xc0b66fcc` — which
  disassembles to `c0b66fbc: memb(r20)=#3` (slot state → 3) … `c0b66fc0: r0 = memw(r17+0x70)` …
  `c0b66fc8: callr r0`. **(4) Registration/arm:** `FUN_c0b66ee0(id,fn)`/`FUN_c0b66f00(id,fn)` write
  `slot+0x70`/`slot+0xf0`; `FUN_c0396fa0` writes `slot+0x70 = FUN_c039ef80`, `slot+0xf0 =
  FUN_c039eed0`, then **arms** (`0xc0b663e0`). **(5) ★ CORRECTION:** `FUN_c0396fa0`'s **only** caller
  is `c0398764`, inside the **`OFFLINE_SLEEP_WAIT`** handler `0xc03986b0` — so §112.54.3's
  "`ONLINE_SLEEP_WAIT` registers `FUN_c039ef80`" is **WRONG** (a Ghidra merge artifact). **(6)**
  `FUN_c039eed0` arms the `ctx+0x4a8` watchdog and sends `STMR_ON_REQ 0x042b0206`; `FUN_c039ef80`
  cancels timer 4 (`func_0xc0b674b0(4)`). The full 31-entry sleepmgr message table is recovered.
  ⇒ **the fatal is a sleep-cycle timer expiring while the STM is mid-sleep-entry** (the §112.54
  "deadlock" framing stands, now pinned to a timer). **OPEN:** why the arm site is state 11 while the
  machine is state 2; the timer period (~0.313 s at 19.2 MHz); why `ONLINE_SLEEP_WAIT → SLEEP` never
  completed. ⚠ the older `lte_ml1_common_timer.c:390` attribution does **not** match these dumps'
  fatal record. Offline, read-only; no device write, no patch.
- **§112.53.1 (P-SCHED1 RESULT — activity ARMS, it does not GATE)** — the pre-registered early-only-traffic
  run produced a **FATAL at modem uptime `900.622162 s`** (`lte_ml1_sleepmgr_stm.c:4054`), **600 s after
  traffic stopped at mu 300 s**. `P-SCHED1-ARM` **CONFIRMED**, `P-SCHED1-GATE` **FALSIFIED**,
  `P-SCHED1-CLASS` **CONFIRMED** (a fatal, not a wedge). The coredump (`dump_devcd2_7362.bin`, md5
  `9e3cd57b…` device==host) has the **same task `slpc`, PC `0xc087a804`, and the identical fatal stack**
  (`+0x01c = 0xc039f7c4`), and the **same STM state `*(0xc1e158d4) = 2` = `ONLINE_SLEEP_WAIT`** ⇒ §112.54
  reproduced on a second independent specimen. ⇒ "ACTIVITY-GATED" is more precisely **"ACTIVITY-ARMED"**:
  early activity arms the ~900 s event; presence of traffic at the mark is irrelevant. Baseline restored
  (`preemptive_ssr_enabled=1`). n=1 (qualitative).
- **§112.54 (THE STATE NAMED — the fatal STM is in `ONLINE_SLEEP_WAIT`, not `SLEEP`)** — continuing
  §112.52's read-only next steps. **(1) The getter is proven:** `FUN_c03a0c20` →
  `c02d9f94` → `c0fe1960` where `c0fe1960: if(r0==0) assert; r0 = memw(r0+#4); return` ⇒
  **`FUN_c03a0c20()` = `stm_get_state(obj)` = `*(0xc1e158d0 + 4)`**, so `SLEEP = 3` is confirmed (compare
  is `cmp.eq(r0,#0x3)`). **(2) The state ENUM is recovered** from the class struct `0xc1a94f70`
  (`+0x04 = "LTE_ML1_SLEEPMGR_STM"`, `+0x18 = state table 0xc1a94fc8`, 0x10 B entries): idx 0 `INACTIVE`
  (h `c0396a40`), 1 `ONLINE` (h `c0396c50`), **2 `ONLINE_SLEEP_WAIT` (h `c0396f30`, f2 `c0397380`)**,
  **3 `SLEEP` (h `c03973a0`)**. Live value at the §112.49 fatal: `*(0xc1e158d4) = 2` =
  **`ONLINE_SLEEP_WAIT`**. **(3) The registration chain is proven from the coredump:** the
  `ONLINE_SLEEP_WAIT` entry handler `FUN_c0396f30` registers `FUN_c039ef80` and `FUN_c039eed0`
  (`c0396fc0: r1:0 = combine(##-0x3fc61080,#0x4); call 0xc0b663e0`); searching the coredump for the
  handler pointers finds each **exactly once** — `FUN_c0396f30`@`0xc1a94fec` (state-table +0x24),
  `FUN_c039ef80`@`0xc1da08a0`, `FUN_c039eed0`@`0xc1da0920`. ⇒ **the fatal function is entered as a
  handler only while the STM is in `ONLINE_SLEEP_WAIT` — the state it then asserts it is not in.**
  ⚠ **CORRECTED by §112.55.5:** the `c0396fa0` registration is a *separate* function whose only caller
  is the `OFFLINE_SLEEP_WAIT` handler; `FUN_c039ef80` is a **timer callback**, not a state handler.
  **Mechanism:** the sleep manager is **deadlocked mid-sleep-entry** — it left `ONLINE`, entered
  `ONLINE_SLEEP_WAIT` (a `GO_TO_SLEEP_REQ` issued, completion watchdog armed), and the sleep **never
  landed** (`SLEEP` never reached); a later event dispatches `FUN_c039ef80`, which asserts `state ==
  SLEEP` ⇒ ERR_FATAL. **§112.54.6:** the completion watchdog (`ctx+0x4a8`, callback `FUN_c03967f0`, a
  message-sender) is **DISARMED** (`+0x1c = 0xdeaddead`) at the fatal, so it did not fire the crash; and
  the line-4089 `serv_cell` assert is the *different* function `FUN_c0396850` — so `0xc3c81a40`/`50` are
  **two functions' asserts**, correcting §112.52's "one shared block" phrasing. Sleep-path message table
  (`0xc1a95088`) recovered (`GO_TO_SLEEP_REQ` `0x042b0209`, `RF_SLEEP_CNF` `0x042b0802`,
  `LL1_SYS_SLEEP_CNF` `0x040a0808`, …). **Does NOT add:** *why* the transition never lands (lost
  `*_CNF`? out-of-order wakeup? watchdog?) — still OPEN. Offline, read-only; no device write, no patch.
- **§112.52 (the assert SITE located — `FUN_c039ef80`, `lte_ml1_sleepmgr_stm.c`)** — continuing §112.51's
  read-only next step. Resolving the ERR/assert descriptor for line 4054 in the zlib descriptor DB gives
  **`0xc3c81a40`** (`lte_ml1_sleepmgr_stm.c`), and searching the stock disassembly for that exact
  constant finds its **unique** code load site: `c039f7c0` (`r0 = ##-0x3c37e5c0`), assert call
  `c039f7b8`/`c039f7c4`. The fatal task's stack (SP `0x8ae992d8` → modem VA `0xc46992d8`) carries the
  return address **`c039f7c4`** ⇒ the assert is issued from **`FUN_c039ef80`** (`c039ef80:
  {call 0xc0030000; allocframe(#0x88)}`), a sleepmgr handler registered at `c0396fc0`. The block is
  **shared** (packet `c039f7b8` → line 4054; packet `c039f7c4` → line 4089) and is reached from two
  in-function branches: `c039efec` (`if (FUN_c03a0c20(...) != 3)`) and `c039f098`
  (`if (FUN_c037121c(...) == 0)`). The return address `c039f7c4` pins the fired call to the **line-4054**
  one, so the fatal condition is **`FUN_c03a0c20(...) != 3`** = (hypothesis) `stm_get_state(
  LTE_ML1_SLEEPMGR_STM) != SLEEP` with `SLEEP = 3`. The stack also spills the live ctx
  (`table 0xc312db4c → 0xc20f1680`). **Adds:** the fatal is a **state-machine precondition** in a named
  sleepmgr handler (NOT the MCPM count guard of §112.38) — site, function, conditions, call all exact.
  **Does NOT add:** the root cause — why the STM was not in SLEEP is still OPEN, and `FUN_c03a0c20`'s
  identity / `FUN_c039ef80`'s role remain inferred. New tools `scratch/descr_scan.py`,
  `scratch/read_stack.py`. Offline, read-only; no device write, no patch.
- **§112.51 (the fatal assert, NAMED)** — resolving the hard-coded ERR_FATAL descriptor `0xc35b1384` in
  the §112.49 coredump gives `{line = 4054, msg_ptr → "Assert stm_get_state ( LTE_ML1_SLEEPMGR_STM ) ==
  SLEEP failed: ", file → "lte_ml1_sleepmgr_stm.c"}` ⇒ the fatal is **`ASSERT(stm_get_state(
  LTE_ML1_SLEEPMGR_STM) == SLEEP)`** — a sleep-manager **state** check, **not** the MCPM count guard
  `FUN_c0ce7fe0` that §112.38 found INERT. ⇒ §112.38's "the `sleepmgr:4054` guard is INERT" does **not**
  dispose of this fatal; the sleepmgr lead is **re-opened**. (Corrects my own §112.49.1 over-claim that
  the label was a generic "shared descriptor".)
- **§112.50 (mitigation hardening — fail-safe fallback anchor + fatal observer)** — audit of
  `modem-bearer-watchdog` found two real gaps, both fixed and deployed. **(1)** When `get_modem_uptime()`
  could not read the kernel's "is now up" line (dmesg evicted + no saved anchor), the old loop only
  *warned* and left the modem **unprotected** — the ~902.7 s deadline would simply expire. It now falls
  back to `SECS_SINCE_SSR`, a local timer since the last known restart (own SSR or observed fatal).
  **(2)** The loop never read dmesg, so a mitigation failure was **silent**; a fatal observer now counts
  `fatal error received` lines, logs each NEW site, and resets the local timer (baseline seeded at
  startup). Unit-tested with a mock dmesg; `sh -n` clean; deployed (`67136b8b…` → `cdb8c7db…` → `9a31d83`);
  backup `/root/modem-bearer-watchdog.pre-harden.bak`. Commits `fa47a85`, `9a31d83`.
- **§112.49 (PRE-REGISTRATION + RESULT — stock-firmware wedge-recoverability run)** — device run with the
  one-change discipline (`preemptive_ssr_enabled` 0→1): stock firmware (no patch), `a2_pin=1`, pre-emptive
  SSR **OFF**, continuous `ping -I wwan0` traffic, stall-watchdog fallback. The event at modem uptime
  **900.478 s** was a **FATAL** — dmesg `lte_ml1_sleepmgr_stm.c:4054`, coredump
  `/root/dumps/dump_devcd1_3334.bin` (85 398 475 B, md5 `580be11b…`) reads `Uptime 0:15:00`, task
  **`slpc`**, PC `0xc087a804`. **P-WEDGE-OUTCOME FALSIFIED** (it was not a wedge); the monitor's
  `WEDGE at up=3346` is a **false positive** (the ping failures were the fatal's SSR, not an independent
  wedge). ★ Corpus inconsistency §112.48.3 gains a clean data point: **traffic → FATAL** (supports
  §112.35, contradicts item 81; item 81's v7-instrumented firmware remains a confound). ★★ **`a2_pin`
  alone did NOT suppress this fatal** — the pin guards a *different* (cold-boot `a2_power.c:1189`) fatal;
  only the pre-emptive SSR suppresses the 900 s event. ★ The site label is the **fixed shared ERR_FATAL
  descriptor**, not the real site: reading the coredump confirms the MCPM guard `FUN_c0ce7fe0` is
  **INERT** (its snapshot arrays `DAT_c30fd9a8`/`DAT_c30fda28` are all zero; LPR `0xc1d473f8` = `"rpm"`),
  so this run does **not** revive the closed `rpm.sync` lead (§112.39) — the surviving root-cause
  candidate is still the **ML1-wide stall** (items 84–86/100/102). Pre-registration written **before** the
  event; coredump pulled device-local and md5-verified; device restored to the mitigated baseline.
- **§112.48 (WEDGE vs FATAL — the ~900 s event's two manifestations)** — offline re-parse of the two
  archived F3 series (`a2pin/f3stall/*` = the §112.34 idle run; `a2pin/f3assert/*` = the §112.35 traffic
  run). The wedge's F3 is a **staged shutdown**: RF (`rflte_*`) → 0 first, then an `a2_power` **storm**
  (53→114), then NAS/QMI decay, then only the `cfm_cpu_monitor` heartbeat; the fatal run instead has the
  RF **busy** (674→560) right up to the assert. **★★ A CORPUS INCONSISTENCY is recorded OPEN:**
  §112.34/35 say *traffic→assert, idle→no assert*; item 81 says the **exact opposite** — both cannot be
  right. Also: §112.34's "wedge" is **confounded with idle-sleep** (§104), so it is not a clean specimen;
  and the stated goal **"convert a fatal into a recoverable wedge" is likely COUNTERPRODUCTIVE** — a
  fatal recovers in ~1.7 s via the SSR (§112.35: assert 912.464 → up 914.168) while a wedge waits for the
  60 s stall watchdog. Two negatives of our own. No device write.
- **§112.47 (offline closure of §112.46.8's "open identification")** — pure offline re-derivation
  (stock `modem.asm` + the Ghidra decompilation; **no device write, no firmware build**). Establishes
  that (a) `obj[+0x38]` is a **fixed context ID `20+k`**, written once by `FUN_c02fb8b0` via
  `FUN_c02d7b80(ctx_base, 20+k)` — not a transient state; (b) `inst[+0x300]` is the pending-selector
  bitmask, **set only** by `FUN_c02fba64` (15 sites, one fixed selector each) and cleared by
  `FUN_c02fc3cc`; (c) the **`0x80` (ctx0) selector is passed at exactly 2 sites, both inside
  `FUN_c02fda90`** ⇒ there is **no hidden high-rate ctx0 armer** and a v3 ring is **REDUNDANT**
  (§112.45.5 already noted the v7–v11 rings ARE the `FUN_c02fda90` instrument). Two nulls recorded: the
  F3 `cap_fatal.bin` carries **no temperature record** (the 16 `temp` hits are the `PC_PENDING_TEMP`
  A2 client name), so a thermal/PA-aging hypothesis gets no log support; and the capture tail is a
  quiet post-SSR idle modem. **A negative of our own is stated** — the hoped-for lead is removed and a
  v3's value is downgraded to zero. Device left on stock firmware (md5-verified).
- **§112.46 (P-CANCELRATE — the state-20 ARM-vs-CANCEL lifecycle ring)** — a firmware ring (the v7/v8
  cave technique) retargeting 3 `call` sites: **ARM** `FUN_c02fda90` at `0xc0326874`/`0xc033c0f4` (both
  its call sites) + **CANCEL** `FUN_c02fc3cc` at the ML1 dispatcher `0xc034e1f0`. Offline-verified (hash
  PASS, read-back disassembly) before any write; boot-transparent (the retargeted dispatcher did **not**
  reproduce the §87 v12 crash-loop). **The fatal NEVER fired** — a 34-min watcher saw an empty fatal grep
  from AP 159→2175 and the modem **WEDGED** (data path dead, `mmcli` still `running/connected/lte`; the
  §71 regime, third confirmation). The coredump was **forced on-demand** (`dump_devcd1_1536.bin`, md5
  `f05204e8…`) and **carries NO filled crash report** (only format strings) — a forced watchdog crash
  cannot be classified. Ring valid (G1–G3 PASS; **G4 FAIL** — wedge, not fatal): **ARM = 32**,
  **CANCEL = 947** (last arg `0x80`), ring 128/128 CANCEL. **VOID on two independent grounds:** (a) the
  CANCEL site `0xc034e1f0` is a **shared dispatch MERGE POINT** — cases `jump` there with selectors
  `1,2,4,8,0x10,0x20,0x40,0x80,0x100,0x200,0x400,0x800,0x1000,0x2000,0x4000` — so the count is a
  **generic ML1-dispatch count, not the ctx0 cancel** (the §87 trap; the ring stores no per-entry
  selector, so the ctx0 subset is unrecoverable); (b) `FUN_c02fda90` fired only 32×, **corroborating
  §63.5** ("entered exactly 4 times in 900.6 s … essentially dormant") and **refuting the
  pre-registration's "~0.63/s armer / 565–674 arms" premise** (≈ the dispatcher rate). A second forced
  crash (`dump_devcd2_2057.bin`) reads the save page as **magic 0, count 0** ⇒ the page is **cleared at
  modem boot** (no persistence). The `0xec121000` counter **rate is unresolved** (builders say 204 800 Hz;
  `ll1_ring_dump.py` says 19.2 MHz; a cross-dump LL1 test is ambiguous because the seq counter resets at
  boot) ⇒ report raw ticks; the ordering is rate-independent. **Rolled back to stock firmware**
  (md5-verified) + config restored (`preemptive_ssr_enabled=1`). Pre-registered
  (`PREREG_cancel_ring.md`); a negative of our own is stated. Tools:
  `scratch/cancel_ring/{build,read}_cancel_ring.py`, `PREREG_cancel_ring.md`.
- **§112.45 (P-MEASRING — the measurement-ring firmware instrument)** — a deterministic firmware ring
  (the v7/v8 cave technique) retargeting the **5 literal `call` sites** of the three ML1
  measurement-scheduler primitives (1 ARM `FUN_c01ecbf4` + 2 WRITE `FUN_c01bc8e0` + 2 DRAIN
  `FUN_c01bc934`), ring on save page `0xc1455000`, entered by `call` (r31 = packet return addr) and
  tail-jumping (`jumpr`, r31 intact) ⇒ **semantically transparent**. Offline-verified (hash PASS,
  read-back disassembly) **before any device write**; boot gate PASSED; captured the real fatal
  (`lte_ml1_common_timer.c:390`, modem ≈901.83 s, coredump `dump_devcd1_916.bin`, md5 `8912a618…`).
  Ring valid (G1–G5, count 389 239). **Literal pre-registered answer = class 1 "ARM stopped first"**
  (`last_ts[ARM]=88.0 s` vs WRITE `901.4 s` / DRAIN `901.8 s`) — **but CONFOUNDED and NOT promoted**:
  the modem stayed fully healthy for **813.8 s after the ARM stream stopped**, the ARM/event-0x10
  stream is **early-concentrated** (5 079 arms at ~58/s for 88 s, then 0 in the last 128 events;
  ARM:WRITE = 1:38), and the WRITE/DRAIN hooks fire on **every call** (incl. no-op iterations) so the
  192 k counts over-count (the armed slot's `+01` is still 0 at the fatal). ⇒ the instrument targeted
  the **wrong armer**: the fatal's armer is the **state-20 watchdog armer `FUN_c02fda90`**, not
  event-0x10. **Also fixes a real bug in the v7 `call` re-encode**: bit 24 (field sign) must be
  **set from the sign of the new field**, not preserved from the old word — validated against all 5
  stock sites **and** the LLVM toolchain's own encoder. **Rolled back to stock firmware** (md5-verified)
  + config restored (`preemptive_ssr_enabled=1`). Pre-registered (`PREREG_meas_ring.md`); a negative of
  our own is stated. Tools: `scratch/meas_ring/{build,read}_meas_ring.py`, `PREREG_meas_ring.md`.
- **§112.44 (P-FLAGPOS Stage 1 — the §105.4 flag's mechanism)** — the offline PC-STRUCT test the user's
  positive-control request called for. The **event-0x10 handler `FUN_c01ecbf4` clears `d2` and then arms
  the ring**: `FUN_c01bc724`'s `memset(carrier+0xbc, 0, 0x12c)` covers **`+0xd2`** (and `+0xbe`), and
  `FUN_c01bc7f0` then sets `carrier+0x00=1`, `slot[b9]+0x00=1`, `carrier+0xb8=(b8+1)mod3` (reads no
  `d2`, writes no `+0x08`). The template at `0xc1d7e91e` is `01 08 28 28 …` ⇒ the handler sets
  **`e02=1`**. ⇒ **`(d2=0, armed)` is reachable — indeed FORCED — by construction**; the §105.4 flag is
  the deterministic post-event-0x10 state (confirmed byte-for-byte in the 3 `d2=0` fatals:
  `up915.42/44`, `up1818.92`). The gate `FUN_c01bc934` **cannot drain** an armed slot until the writer
  `FUN_c01bc8e0` fills `+0x08`/increments `+0x01` ⇒ the fatal is the arm whose **result is never
  written** (§106's "consumer stalled", refined). **Consequence:** the pre-registered probabilistic
  **Stage 2 (n=8) is superseded and NOT run** — a snapshot cannot measure *persistence*, so the test
  cannot distinguish "transient" from "stuck"; the amendment is recorded in `PREREG_posctrl.md` before
  any run. Read-only/offline (no device access). §105.4's observation stands with its mechanism now
  named; §112.41 stays retracted; §105.8's "about to die" is directionally right but the flag is not a
  state that cannot exist healthy.
- **§112.43 (e02 reachability)** — `ifdown modem` (MM `registered`, LTE, no bearer) + a §6.2a
  capture (`dump_devcd1_3125.bin`, md5 `65dc280a…`, no crash report) reads carrier 0
  `e02=1, d2=1` (consumed slot, no flag) and **carrier 1 `e02=1, d2=0`** (empty, no flag).
  ⇒ **`(d2=0, e02=1)` IS reachable in a healthy modem** — the `e02` objection that voided
  P-IDLECTRL2 is **removed**. But the healthy instance was an **empty carrier** (no armed
  slot), so the flag's absence is **vacuous** and does not test it; the direction now
  **favours §105.4** (the flag = "armed but undrained"; healthy carriers always drain). Also
  refines `d2` to **per-carrier**. Device restored. Read-only analysis + one forced capture.
- **`b7b2ece` §112.42 (phase 3, the ML1 meas-table flag)** — a 39-dump census shows
  the §105.4 flag (`+0x00=1` on the `b9` slot **and** `b8=(b9+1)mod3` **and** data=0
  **and** `cons=0`) is confined to (`d2=0` **and** `e02=1`) dumps — so far exactly the
  **3 `d2=0` fatals**; the 2 healthy `d2=0` captures both have **`e02=0`**, which
  **mechanically disables** the flag (gate B `0≤+0x01` always passes). ⇒ the flag's
  fatal-specificity is **UNRESOLVED** (a new confounder, `e02`, was found). Two
  corrections: **§112.41 is RETRACTED** (its `slot[+0x01]=0` evidence was already
  falsified by §105.2 — it is the resting value) and **§105.8's "confound resolved" is
  UNSUPPORTED** (its four controls all read `d2=1`, so §105.5 was never executed).
  Design constraint: a high-rate RAM sampler is **impossible** (TrustZone blocks all AP
  reads of modem RAM). **P-IDLECTRL2 RAN** (`dump_devcd2_13985.bin`, md5
  `a6905ca4…`): the idle modem survived to modem-up **966 s** (`d2=0`) but read
  **`e02=0`** ⇒ **VOID by the pre-registered gate** — the gate fired as designed.
  Device restored (SSR 1500→800, reboot). Read-only analysis + one forced capture.
- **`adb9706` §112.41 (phase 2, ML1 SERV-MEAS-RSP reply path)** — ⚠ **RETRACTED by
  §112.42** (the `slot[+0x01]=0` evidence is non-discriminating). Kept for the
  record: traced the chain (`FUN_c02fda90` armer → CNF gate `FUN_c01bc934` →
  ready-writer `FUN_c01bc8e0`); its "producer never ran" claim holds only for the
  `d2=0` dumps. Read-only, offline.
- **`9d95c78` §112.40 (phase 1, RF/interference lead)** — re-confirmed item 71's RF
  collapse with a new tool `scratch/rf_timeline.py`; found the
  `rflte_core_rxctl` RX gain/freq-comp values are **always 0** (no retune storm);
  showed the collapse is **state-dependent** (876 vs 12 RF records across the two
  wedges). Verdict: **precursor**, cause unknown; the fatal-run RF-vs-assert
  ordering is not pinnable from the multi-session wrapping captures. Read-only,
  offline.
- **`be50e56`** — Add this CHANGELOG.md (Keep-a-Changelog format) documenting
  commit `03bbc17` and the prior §112.37–§112.39 series. Doc 197 remains the
  primary source of truth; this file is a quick-reference index.
- **`03bbc17`** — §112.39 sync note: findings folded back into §112.38.
  - Added a "Sync with §112.38" subsection to §112.39 recording that its
    findings were carried back into §112.38 (commit `c1ad1d9`): the MCPM row #1
    7-dump coredump confirmation (scratch zeroed 7/7, `q6pcvote` LIVE
    1060-1647), the OPEN-section `rpm.sync` RULED OUT, and the ML1-side
    SERV-MEAS-RSP ctx0 watchdog (items 84-86/100/102) listed as the surviving
    candidate.
  - Updated §112.39's Achieved/Expected table last row to cross-reference
    §112.38's OPEN section and items 84-86/100/102.
  - Net state recorded: the `rpm.sync` LPR park axis is **CLOSED**; the
    firmware-timer axis remains **exhausted** (§112.38); the one open question
    — *what stops the ML1 SERV-MEAS-RSP reply path at ~902 s* — is a
    causal-mechanism question, not a timer-limit question.

### Prior commits in this series

- **`c1ad1d9`** — §112.38 update: fold §112.39 ML1 counter findings back in.
  MCPM row #1 coredump-confirmed 7/7; OPEN section `rpm.sync` RULED OUT, ML1-side
  SERV-MEAS-RSP ctx0 watchdog added as surviving candidate; Achieved/Expected
  and SOP/Tools updated.
- **`b9275ad`** — §112.39: `rpm.sync` LPR park CLOSED — `q6pcvote` LIVE, MCPM
  scratch zeroed, not a hang. Read-only disassembly + 7-dump coredump synthesis.
- **`24e1001`** — §112.38: firmware timers/watchdogs complete inventory — three
  distinct ~902 s watchdogs (MCPM INERT, ML1 state-50ms FALSIFIED items 85/86,
  a2_power >900 EVALUATED); no untested lever.
- **`fe1653b`** — §112.37: PSCI/cpuidle axis CLOSED — §112.29 "cluster
  unreachable" retracted; cpuidle sub-lever already falsified (H-IDLE); no
  remaining reversible lever.
