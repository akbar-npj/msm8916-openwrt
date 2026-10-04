# Doc 244 — The LTE_ML1_SLEEPMGR wakeup path: complete code map

**Date:** 2026-10-04
**Scope:** `LTE_ML1_SLEEPMGR_STM` (the ML1 sleep manager state machine) — object,
class, 12-state table, the full 31-message interface, the wakeup-state entry
handlers, the fatal-assert callback, and the sleep/wake timer-callback pair.
**Status:** static RE complete for the wakeup path; the ~900 s root cause is
**narrowed, not yet fixed**.
**Method:** stock ELF `scratch/hmu05_stock_elf/modem_hmu05_stock.elf`
(md5 `1a6f9507e03d4ddbbf1977af81ecdbd7`), flat b16 disassembly
`scratch/hmu05_stock_elf/disasm_b16.txt`, and the v25 fatal coredump
`scratch/v24_dumps/dump_devcd1_916.bin` (modem uptime 0:15:02, Task `tmr_slave3`).

---

## 1. Summary (what this doc establishes)

1. The sleepmgr is an STM of class **`LTE_ML1_SLEEPMGR_STM`** (class struct
   `0xc1a94f70`), with **12 states** and a state table at `0xc1a94fc8`.
2. Its live object is **`0xc1e158d0`** (slot 0); the getter reads the state at
   **`obj+0x4`**. At the ~900 s fatal the state is **9 = OFFLINE_WAKEUP**
   (re-confirmed, 6 dumps — §112.58).
3. The sleepmgr's **complete message interface is 31 messages**, in a
   `{name_ptr, msg_id}` registry at **`0xc1a95088`** (31 × 8 B = 0xF8, ending at
   `0xc1a95180`). This is the first time the full set has been enumerated.
4. The **fatal assert site is `0xc039ef80`** — the **SLEEP→WAKEUP routine**:
   it asserts `stm_get_state(sleepmgr) == SLEEP(3)` and then **sends
   `LTE_ML1_SLEEPMGR_WAKEUP_REQ` (0x42b0205)**. It is not a generic assert; it
   is the modem's periodic "time to wake up" callback.
5. `0xc039ef80` is one half of a **sleep/wake callback pair**
   (`0xc039eec0` = sleep, `0xc039ef80` = wakeup), registered by **`0xc0396fa0`**
   from the sleep-entry region (`0xc0398764`), tag `4`, via `0xc0b66f00` /
   `0xc0b663e0`.
6. **Incoming dispatch:** the ML1 message router `0xc043b0xx` (binary-search on
   msg id) routes `LTE_LL1_SYS_SLEEP_CNF` (0x40a0808),
   `LTE_LL1_SYS_RUN_RXLM_RF_SCRIPT_CNF` (0x40a080e) and
   `LTE_LL1_SYS_LIGHT_SLEEP_MOD_CTRL_CNF` (0x40a0814) to the sleepmgr handler
   **`0xc039d460`**.
7. The **OFFLINE trigger** is `LTE_ML1_SM_OFFLINE_GOTO_SLEEP_IND` (0x4020425),
   sent by `0xc036b3d4` (ML1-SM module) — this is the message that drives the
   sleepmgr into the OFFLINE path whose terminal state is OFFLINE_WAKEUP (9).

---

## 2. The object, the class, and the state table

Read directly from the fatal coredump (`scratch/sleepmgr_dump.py`):

```
sleepmgr slot[0] 0xc1e158d0:
  +0x00 = 0xc1a94f70   class ptr
  +0x04 = 0x00000009   STATE  (stm_get_state reads memw(obj+4))
  +0x08 = 0x0000001d
  +0x14 = 0xc20f1510   CONTEXT ptr (get_context reads memw(obj+0x14))

class 0xc1a94f70:
  +0x10 = 0x00000001
  +0x14 = 0x0000000c   num_states = 12
  +0x18 = 0xc1a94fc8   state table
  +0x20 = 0xc1a95088   message registry (31 entries)
  +0x24 = 0xc1a95180   handler table (8 entries, 5 default)
  +0x28 = 0xc0396440   vtable: init
  +0x2c = 0xc0396a00   vtable
  +0x30 = 0xc0396400   vtable: F3 log
  +0x34 = 0xc0396310   vtable: F3 log-dispatch (6-way, gp+0xbc24)
  +0x3c = "LTE_ML1_SLEEPMGR_STM\0"
```

### 2.1 State table `0xc1a94fc8` (12 × 0x10 = {name, entry, f2, f3})

| # | name | entry | f2 | f3 |
|---|------|-------|----|----|
| 0 | INACTIVE | `0xc0396a40` | – | – |
| 1 | ONLINE | `0xc0396c50` | – | – |
| 2 | ONLINE_SLEEP_WAIT | `0xc0396f30` | `0xc0397380` | – |
| 3 | SLEEP | `0xc03973a0` | – | – |
| 4 | ONLINE_WAKEUP | `0xc0397f30` | `0xc03982b0` | – |
| 5 | TTL_WAIT | `0xc0398410` | `0xc0398590` | – |
| 6 | LIGHT_SLEEP_WAIT | `0xc0397a30` | – | – |
| 7 | LIGHT_SLEEP | `0xc0397d10` | `0xc0397dd0` | – |
| 8 | LIGHT_SLEEP_WAKEUP | `0xc0397ea0` | `0xc0397ee0` | – |
| **9** | **OFFLINE_WAKEUP** | **`0xc03985f0`** | – | – |
| 10 | OFFLINE_RECORD | `0xc0398670` | – | – |
| 11 | OFFLINE_SLEEP_WAIT | `0xc03986b0` | – | – |

State-name strings verified byte-for-byte from the coredump
(`scratch/read_cstr.py`).

---

## 3. The complete 31-message interface

Registry `0xc1a95088` (16 entries) continued at `0xc1a95108` (15 entries).
Names read from the coredump.

| msg id | name |
|--------|------|
| `0x42b0200` | `LTE_ML1_SLEEPMGR_ENABLE_SLEEP_REQ` |
| `0x42b0201` | `LTE_ML1_SLEEPMGR_DISABLE_SLEEP_REQ` |
| `0x42b0202` | `LTE_ML1_SLEEPMGR_OBJ_START_REQ` |
| `0x42b0204` | `LTE_ML1_SLEEPMGR_OBJ_ABORT_REQ` |
| `0x42b0205` | **`LTE_ML1_SLEEPMGR_WAKEUP_REQ`** |
| `0x42b0206` | `LTE_ML1_SLEEPMGR_STMR_ON_REQ` |
| `0x42b0208` | `LTE_ML1_SLEEPMGR_UPDATE_SCLK_ERR_REQ` |
| `0x42b0209` | `LTE_ML1_SLEEPMGR_GO_TO_SLEEP_REQ` |
| `0x42b020c` | `LTE_ML1_SLEEPMGR_LIGHT_SLEEP_WAKEUP_REQ` |
| `0x42b020d` | `LTE_ML1_SLEEPMGR_LIGHT_SLEEP_ENABLE_FW_REQ` |
| `0x42b020e` | **`LTE_ML1_SLEEPMGR_RF_LESS_WAKEUP_REQ`** |
| `0x42b020f` | **`LTE_ML1_SLEEPMGR_OFFLINE_ENABLE_REQ`** |
| `0x42b0408` | `LTE_ML1_SLEEPMGR_LIGHT_SLEEP_OBJ_END_IND` |
| `0x42b0409` | `LTE_ML1_SLEEPMGR_DELAYED_CFG_APP_IND` |
| `0x42b040a` | `LTE_ML1_SLEEPMGR_DLS_EXIT_IND` |
| `0x42b040b` | `LTE_ML1_SLEEPMGR_WMGR_RESULT_IND` |
| `0x42b040c` | `LTE_ML1_SLEEPMGR_WMGR_TIMER_EXPIRY_IND` |
| `0x42b040d` | `LTE_ML1_SLEEPMGR_TRM_GRANT_CB_IND` |
| `0x42b0801` | **`LTE_ML1_SLEEPMGR_RF_WAKEUP_CNF`** |
| `0x42b0802` | **`LTE_ML1_SLEEPMGR_RF_SLEEP_CNF`** |
| `0x42b0803` | **`LTE_ML1_SLEEPMGR_RF_ENTER_CNF`** |
| `0x42b0804` | **`LTE_ML1_SLEEPMGR_RF_EXIT_CNF`** |
| `0x4030841` | `LTE_ML1_DLM_RX_CFG_CNF` |
| `0x4030462` | `LTE_ML1_DLM_LIGHT_SLEEP_ISSUE_IND` |
| `0x4090804` | `LTE_LL1_UL_TX_LM_CONFIG_CNF` |
| `0x40a0808` | `LTE_LL1_SYS_SLEEP_CNF` |
| `0x40a080e` | `LTE_LL1_SYS_RUN_RXLM_RF_SCRIPT_CNF` |
| `0x40a0814` | `LTE_LL1_SYS_LIGHT_SLEEP_MOD_CTRL_CNF` |
| `0x40a041b` | `LTE_LL1_SYS_SAMPLE_REC_DONE_IND` |
| `0x40b0806` | `LTE_LL1_ASYNC_WAKEUP_CNF` |
| `0x4020425` | `LTE_ML1_SM_OFFLINE_GOTO_SLEEP_IND` |

---

## 4. The wakeup path — code

### 4.1 The fatal assert site `0xc039ef80` = SLEEP→WAKEUP

```
c039ef80  call 0xc0030000                 ; stack guard
c039ef84  allocframe(#0x88)
c039ef88  call 0xc02d1140                 ; get ML1 instance
...
c039ef98  r1 = memub(gp+#0x2d1)           ; f3_toggle (DIAG gate)
c039efbc  r0 = memw(gp+#0x6584)           ; f3_mask
...
c039efe4  call 0xc03a0c20                 ; = stm_get_state(0xc1e158d0)
c039efec  p0 = cmp.eq(r0,#0x3); if (!p0.new) jump:nt 0xc039f7b8
          ;   ^ ASSERT(stm_get_state(LTE_ML1_SLEEPMGR_STM) == SLEEP)
...
c039f7a0  r0 = r24                        ; the sleepmgr object
c039f7a8  r3:2 = combine(##0x42b0205,#0x18)   ; WAKEUP_REQ, payload 0x18
c039f7b0  call 0xc03927d0                 ; ML1 message send
c039f7b4  jump 0xc0030080                 ; return
c039f7b8  call 0xc0879150 ; ... ; call 0xc0879150   ; the assert logger ×2
```

`0xc03a0c20` is the sleepmgr's `stm_get_state` wrapper — a 4-byte thunk
`jump 0xc02d9f94`, which is itself a thunk `jump 0xc0fe1960` (the engine getter
`r0 = memw(r0+0x4)`). Confirmed by the caller `0xc039d460` which loads
`r0 = ##0xc1e158d0` immediately before the call.

**Interpretation:** the function is the modem's periodic *wake-up trigger*. It
requires that the sleepmgr be in SLEEP (3) — you can only wake from sleep — then
sends `WAKEUP_REQ`. At ~900 s it is invoked while the sleepmgr sits in
**OFFLINE_WAKEUP (9)**, so the assert fires.

### 4.2 The sleep/wake callback pair and its registration

`0xc039eec0` (sleep) and `0xc039ef80` (wakeup) are registered together by
`0xc0396fa0`:

```
c0396fa0  ...
c0396fac  r1:0 = combine(##0xc039eec0, #0x4)   ; callback, tag 4
c0396fb4  call 0xc0b66f00
c0396fc4  r1:0 = combine(##0xc039ef80, #0x4)   ; callback, tag 4
c0396fc8  call 0xc0b663e0
```

`0xc0396fa0` has exactly **one** caller: `0xc0398764`, inside the sleep-entry /
OFFLINE_SLEEP_WAIT region (`0xc03986b0`). `0xc0b663e0(cb, slot)` indexes a
connection table at `0xc1d9fec0 + slot*0x250`.

### 4.3 The wakeup-state entry handlers

* **state 4 ONLINE_WAKEUP** `0xc0397f30`: `get_context` → ctx; reads
  `ctx+0x394`; large body (`0xc02d2610`, RF retune); f2 `0xc03982b0` is the
  F3-gated completion logger (`0xc0396bf0` → `0xc0319550` → `0xc02a5538`).
* **state 8 LIGHT_SLEEP_WAKEUP** `0xc0397ea0`: builds `r3:2 =
  combine(0x42b020d, 0x10)` → `0xc03927d0` (sends `LIGHT_SLEEP_ENABLE_FW_REQ`);
  sets `ctx+0x2de = 1`. f2 `0xc0397ee0` clears `ctx+0x2df`.
* **state 9 OFFLINE_WAKEUP** `0xc03985f0` (the fatal state): `get_context` →
  ctx; writes `ctx+0x4=0x43`, `ctx+0x14=2`, `ctx+0x1c=2`, `ctx+0x18=0xc039e800`
  (a callback fn ptr), `ctx+0x8=0xffff`; calls `0xc0392ac0` (re-inits the 8-entry
  online mask); then `jump 0xc0318f90` (builds/dispatches a 0x2c-byte context
  block, code `0x321`). **f2 = 0 — state 9 has NO completion sub-handler**, which
  is consistent with it being a state the design never expects to dwell in.

### 4.4 The incoming dispatch (LL1 CNFs → sleepmgr)

The ML1 router `0xc043b0xx` binary-searches `r1` (msg id). For the range
`[0x40a0808 .. 0x40a0808+0xc]` with the accept-mask `0x1041` (bits 0, 6, 12) it
calls the sleepmgr handler `0xc039d460`:

```
c043b108  r0 = add(r1, ##-0x40a0808)
c043b110  if (cmp.gtu(r0,#0xc)) jump default
c043b114  r0 = lsl(#1, r0)
c043b11c  r0 = and(r0, ##0x1041)      ; accept 0x40a0808 / 0x40a080e / 0x40a0814
c043b124  call 0xc039d460
```

`0xc039d460` = the sleepmgr's LL1-CNF handler. It loads the sleepmgr
(`r0 = 0xc1e158d0`), calls `stm_get_state`, then logs.

> **⚠ CORRECTION (2026-10-04, verified against the stock ELF — see §4.4a).** The
> first sentence is right, the second is imprecise: `0xc039d460` is **not** the
> sleepmgr's per-message handler. It is a *directly-called, generic* router→object
> forwarder (it forwards to the vtable dispatch `0xc02ef910 → 0xc0fe13c0`). The
> sleepmgr's real per-message handlers are the class table `0xc1a95180`.

### 4.4a Verification of `0xc039d460` against the stock ELF (2026-10-04)

Requested check. Findings (all from `scratch/hmu05_stock_elf/modem_hmu05_stock.elf`):

* **It is a genuine function** — entry `0xc039d460`, prologue
  `r4=r2; r16=r1; memd(r29+#-0x10)=r17:16; allocframe(#0x8)`; the preceding
  function ends `dealloc_return` at `0xc039d454` + nop padding.
* **Called from 3 sites**: `0xc043b124` (the msg-id dispatcher), `0xc039e7ec`
  (sleepmgr region), `0xc03a0c44` (tail-call).
* **It forwards** to `0xc02ef910` → `0xc0fe13c0`, which does
  `r4 = memw(memw(memw(obj))+0x20); … callr r4` — a **vtable-style indirect
  dispatch** (`0xc02ef910` has **106 callers** ⇒ a generic helper, not
  sleepmgr-specific). It also references the constant `0xC1E158D0` (sleepmgr
  object) and, in the not-taken branch, `0xc03c3450` with `0xC1655Cxx` records
  (F3/trace descriptors — **not** strings).
* **★ The sleepmgr's real ingress handlers live in the class table `0xc1a95180`**
  (index-aligned with the registry `0xc1a95088`), and those functions have
  **zero direct call sites** (grep count 0) ⇒ they are dispatched *indirectly*:

  | registry msg | name | handler |
  |---|---|---|
  | `0x42b0200` | ENABLE_SLEEP_REQ | `0xc03987e0` |
  | `0x42b0201` | DISABLE_SLEEP_REQ | `0xc0398c80` |
  | `0x42b020f` | **OFFLINE_ENABLE_REQ** | **`0xc039d150`** |
  | `0x42b0202` / `0x42b0204` / `0x04030841` / `0x040a080e` / `0x04090804` | (mixed) | `0xc03987c0` |

* The class `0xc1a94f70` was re-confirmed from the fatal dump: `+0x14 = 12`
  states, `+0x1c = 31` messages, `+0x20 = 0xc1a95088` (registry), `+0x24 =
  0xc1a95180` (handler table), name string `"LTE_ML1_SLEEPMGR_STM"`, state-9
  entry `0xc03985f0`. All 31 registry names read out and match.
* **`0xc043b124` is a shared/fallback target**, reached from several msg-id
  ranges — not only the LL1-CNF mask (`0x1041`). I also traced a path that sends
  `0x42b0200..0x42b020f` to `0xc043b124`.

⇒ **Conclusion:** `0xc039d460` *is* on the sleepmgr ingress path (router →
`0xc039d460` → generic object dispatch), but it is a **shared forwarder**, not
the per-message handler. For a v27 ingress instrument the *specific* handlers are
`0xc1a95180`'s entries (`0xc039d150` for OFFLINE_ENABLE_REQ); hooking the class
handler-table dispatch (filtered to `obj == 0xc1e158d0`) captures ingress
cleanly. Residual uncertainty: `combine(##imm,#0x0)` renders ambiguously, so
whether the `0xC1E158D0` constant lands in `r0` (trace branch live) or `r1`
(function is a thin forwarder) is not fully resolved.

### 4.5 The OFFLINE trigger

`LTE_ML1_SM_OFFLINE_GOTO_SLEEP_IND` (0x4020425) is sent by `0xc036b3d4` in the
ML1-SM module; the payload's `+0x10` byte is 1 or 2 depending on an argument.
This is the message that drives the sleepmgr along the OFFLINE path
(OFFLINE_SLEEP_WAIT → OFFLINE_RECORD → OFFLINE_WAKEUP).

---

## 5. What this means for the ~900 s fatal

* The fatal is **not** a generic assert: it is the sleepmgr's **wake-up trigger
  (`0xc039ef80`) firing while the sleepmgr is in OFFLINE_WAKEUP (9)**.
* The sleepmgr reached the OFFLINE path — which is driven by
  `LTE_ML1_SM_OFFLINE_GOTO_SLEEP_IND` from the ML1-SM — instead of completing
  the normal `ONLINE → ONLINE_SLEEP_WAIT → SLEEP` cycle.
* This is consistent with the established §112.57.x model: the ML1/RF layer
  dies first (RF `rflte_*` 353→0; MCPM power-collapse cycle stops at the event),
  the stack goes offline, and the periodic wake-up trigger then asserts. The
  assert is the modem's own **fast recovery trigger** (~12 s), not the root
  cause.
* The state-9 handler has **no f2 completion sub-handler** and arms a callback
  at `ctx+0x18`; the `9→1` completion (RF_EXIT_CNF path) is the transition that
  never lands.

### Next steps (candidate)
1. Instrument the **sleepmgr message ingress** — hook `0xc03927d0` (the single
   send funnel) filtered to `0x42b0xxx`/`0x40a08xx`, logging `{ts, msg_id, state}`
   in a ring, to capture the exact message that first drives the sleepmgr into
   the OFFLINE path at ~900 s. This is a *receiver-side* history (the opposite of
   the v7–v11 sender rings). **→ BUILT as v26; see §6.**
2. Instrument `0xc043b124` / `0xc039d460` to log the LL1 CNFs (esp.
   `LTE_LL1_SYS_SLEEP_CNF`) and their arrival time relative to the wake-up
   trigger.
3. Determine whether the OFFLINE transition is *caused by* the RF death or is
   itself the trigger — by logging `0xc036b3d4` (OFFLINE_GOTO_SLEEP sender) and
   correlating with the `rflte_*` F3 decay.

---

## 6. The v26 instrument — the sleepmgr message-funnel ring

Built and deployed 2026-10-04 in response to the request *"build v26 receiver
ring"*; this implements §5 next-step 1.

### 6.1 The hook site

`0xc03927d0` is the sleepmgr's ML1-message **send funnel**. Its caller convention
is uniform (verified at the sleepmgr call sites `0xc039f7a0`, `0xc0397ea0`,
`0xc03986b0` and the ML1-core thunks `0xc02a5eb0/ec0/ed0`):

| reg | meaning |
|-----|---------|
| `r0` | destination object (the sleepmgr `0xc1e158d0` for sleepmgr-originated posts) |
| `r1` | payload pointer |
| `r2` | payload length |
| `r3` | ML1 message id |

The funnel is reached by **41 `call` sites** in `0xc0392xxx..0xc039fxxx` plus 3
tail-`jump` thunks in the ML1 core that post `0x408021a`, `0x42b0204`
(OBJ_ABORT_REQ) and `0x42b040d` (TRM_GRANT_CB_IND). A ring here is the
sleepmgr's message history — the counterpart of the v7–v11 ML1-timer *sender*
rings.

The prologue packet `{ memd(r29+#-0x10) = r17:16; allocframe(#0x10) }` must not
be disturbed (v24: hooking a prologue packet crash-loops the modem). v26
therefore hooks the **first body packet `0xc03927d4`** — a 3-instruction,
12-byte packet:

```
c03927d4: { r19:18 = combine(r2,r1)          ; r18 = payload, r19 = len
c03927d8:   r17:16 = combine(r0,r3)          ; r16 = msg_id, r17 = obj
c03927dc:   memd(r29+#0x0) = r19:18 }        ; save the CALLER's r19:18 (intra-packet
                                              read sees the OLD value)
```

which is replaced by `{ jump CAVE; nop; nop }` (parse bits `01,01,11`). The cave
reproduces the packet **byte-for-byte** (llvm-mc emits the exact stock bytes
`124102f5104300f500d2dda1`) so the callee-saved r19:18 is still preserved.

Note the site is only `0x362288` = 3.5 MB from the b05 cave, inside the `j2_jump`
±8 MB range, so **no trampoline is needed** (unlike v25, whose b16 site needed
one).

### 6.2 The cave (`0xc003054c`, 144 B)

```
r8  = 0xc1455000              ; header/beacon
[r8+0] = 0x76323601 (marker); [r8+8] = 0xc03927d0 (site VA)
r9  = [r8+4] + 1; [r8+4] = r9 ; global seq
r11 = 0xc1d4c600              ; ring base
r10 = ((r9 & 0xfff) << 4) + r11
[r10+0]=r9 ; [r10+4]=r3 (msg_id) ; [r10+8]=r31 (caller / return addr)
p0 = (r0 == 0xc1e158d0)       ; sleepmgr?
if p0: r6 = [r0+4]  else r6 = -1    ; PREDICATED load -> no fault for other callers
[r10+0xc] = (r2<<16) | (r6 & 0xffff) ; len | state
<reproduce the 3-instruction packet>
jump 0xc03927e0               ; packet fall-through
```

Scratch registers r6–r11 are safe: the funnel body `0xc03927e0..0xc039284c`
uses only r0–r3, r16–r19, r29–r31 and gp — never r6–r15.

### 6.3 Save area — and the 0xc5000000 lesson

The first v26 build placed the header+ring at `0xc5000000` (the tail of the
topmost segment [20]). That region is byte-verified all-zero in all 68 HMU05
coredumps, but the modem took an exception **on the cave's first write**:

```
Uptime 0:00:16  Task AMSS0  PC 0xc003055c (inside the cave)  BADVA 0xc5000000
```

⇒ **a zero-filled coredump region is NOT necessarily a writable runtime
region.** The final v26 uses:

* header/beacon `0xc1455000` — the free-BSS window proven writable by the
  v13/v20/v25 instruments;
* ring `0xc1d4c600` — a 100 KB region byte-verified all-zero in **all 69 HMU05
  coredumps** (uptimes 25 s…13656 s), in the **middle** of the normal RW BSS
  segment [13] (not a segment tail).

### 6.4 Build & deploy

* builder `scratch/diag_patch_v26/build_diag_patch_v26.py`
  (`modem.mdt` md5 `f57ed422b23405228f9e5689e17aa06c`);
* deployer `scratch/deploy_v26_ring.py` (sha256 read-back verification);
* reader `scratch/read_v26_ring.py`.

**Pre-registration (P-V26-FUNNEL):** H1 marker `0x76323601` at `0xc1455000` and
site `0xc03927d0` at `0xc1455008`; H2 `seq>0` and non-zero ring entries; H3
entries carry ML1-family ids (`0x42b0xxx`/`0x4080xxx`/`0x4320xxx`); H4 the last
entries before the fatal differ from the steady-state sequence; NEG marker absent
⇒ instrument did not run.

### 6.5 Result — the ring captured the fatal (2026-10-04)

Run with pre-emptive SSR **disabled** so the ~900 s event is reachable. Fatal at
AP dmesg `915.260490 s`, crash report uptime `0:15:02` (modem ≈902 s):
`lte_ml1_common_timer.c:390`, **Task `tmr_slave3`**, PC `0xc087a804` (the assert
logger), BADVA `0`. Coredump `dump_devcd1_916.bin` → `scratch/v26_run/dump_fatal_916.bin`.

**Scoring — P-V26-FUNNEL:**

| # | hypothesis | verdict |
|---|-----------|---------|
| H1 | marker `0x76323601` @`0xc1455000`, site @+8 | **PASS** — marker OK; site field = `0xc03927d4` (the *hook packet*, not the `0xc03927d0` entry — the cave stores `SITE_VA`) |
| H2 | `seq>0` + non-zero ring | **PASS** — `seq = 40111`; 4096 live entries |
| H3 | ML1-family msg ids | **PASS** — `0x42b0xxx`, `0x40a0xxx`, `0x40b0xxx`, `0x4290xxx`, `0x4320xxx`, `0x4080xxx` |
| H4 | last entries differ from steady state | **PASS (n=1)** — the final cycle omits `RF_WAKEUP_CNF`; see below |
| NEG | marker absent | not triggered |

**What the ring shows.** The sleepmgr's egress is a highly regular ~20–21-message
sleep/wake cycle, repeated ~192 times across the last 4096 messages
(`seq 36016..40111`; total `40111` since modem boot ⇒ ≈44 msg/s). A full cycle:

```
42b0801 RF_WAKEUP_CNF   42b0206 STMR_ON_REQ   42b0406   4320220   4290202
40a0209  42b0802 RF_SLEEP_CNF   42b0208 UPDATE_SCLK_ERR_REQ   42b0403
42b0209 GO_TO_SLEEP_REQ   42b040b WMGR_RESULT_IND ×2   42b0404
42b0205 WAKEUP_REQ   40a0400 ×3   40b0206   4290203   40a020f   42b0405 → …
```

**The fatal cut the cycle mid-stream** — the last four entries are:

```
40108  0x40a020f  56  caller 0xc039bc04
40109  0x42b0405  32  caller 0xc039bd1c
40110  0x42b0206  16  caller 0xc039ef78   SLEEPMGR_STMR_ON_REQ
40111  0x42b0406  24  caller 0xc0395e80
       ── FATAL ──
```

**The H4 anomaly (the single discriminating fact).** Across the whole window,
`STMR_ON_REQ (0x42b0206)` is preceded by `RF_WAKEUP_CNF (0x42b0801)` **191 of 192
times**; the *only* exception is the **last** `STMR_ON_REQ` (seq 40110), which is
preceded by `42b0405`. So the final cycle went `42b0405 → STMR_ON_REQ → 42b0406`
**without** the `RF_WAKEUP_CNF` that every other cycle emits — i.e. the sleepmgr
armed its sleep timer (`STMR_ON_REQ`) *without first emitting the RF-wakeup
confirmation*, then died. This is consistent with the RF layer failing to wake,
and with the fatal task being the **timer slave `tmr_slave3`** (the `STMR_ON_REQ`
arms a timer whose callback asserts at `lte_ml1_common_timer.c:390`).

The sleepmgr object (`0xc1e158d0`) is frozen in **state 9 `OFFLINE_WAKEUP`**
(class `0xc1a94f70`, ctx `0xc20f1510`) — unchanged from prior runs. The ring is
**egress**, so it never shows the *incoming* message that drives the sleepmgr into
the OFFLINE path; the object carries a 20-entry msg-id table at `+0x20`
(`0x0409xxxx`/`0x041bxxxx`/`0x0404xxxx`/`0x040dxxxx` …) but no state-history
buffer.

**Caller-field decoding.** The recorded `caller` is `r31` = the address of the
**next packet** after the packet containing the `call` (Hexagon sets `r31` to the
packet boundary). Three sites are standalone `{ call … }` ⇒ `call+4`
(`0xc039f7b4`/`0xc039e6f0`/`0xc0395e80`, all matched exactly); the `STMR_ON_REQ`
sender `0xc039ef6c` is fused into a 3-instruction packet ⇒ `r31 = 0xc039ef6c+12 =
0xc039ef78`. So all recorded callers are correct.

**Reader notes.** `scratch/read_v26_ring.py` — the ring index is `seq % 4096` and
`seq` is **pre-incremented**, so the first message lands at index 1 and the window
must be `[max(1,seq-N+1) .. seq]` (an earlier `seq-N` window read a wrap-ghost and
mislabelled it).

### 6.6 The v27 instrument — the sleepmgr INGRESS ring (built + deployed 2026-10-04, RESULT PENDING)

The v26 ring is **egress**; it cannot see the incoming message that drives the
sleepmgr into OFFLINE_WAKEUP. v27 logs the **dispatch** side.

**Hook site.** `0xc039d460`'s call packet `0xc039d46c` (`{ call 0xc02ef910 }`, word
`0x5bea5252`) → `{ jump 0xc003054c }` (word `0x59925870`; target verified `0xc003054c`).
Chosen because it is the only point where both `r16 = r1` (the ML1 message id being
dispatched) **and** the true caller `r31` are live: the next packet `0xc039d470`
(`r3:2 = combine(r16,r0)`) would give a useless `r31 = 0xc039d470`, and the prologue
packet `0xc039d460..0xc039d468` must not be disturbed (v24 lesson). The function
`0xc039d460` hard-references the sleepmgr object `0xC1E158D0`.

**Cave (`0xc003054c`, 128 B).** Append `{seq, msg_id=r16, caller=r31, state}` to ring
`0xc1d4c600` (header/beacon `0xc1455000`, marker `0x76323701`, site VA `0xc039d46c`);
`state = memw(0xc1e158d0+0x4) & 0xffff`; then reproduce the hooked call
(`r6 = ##0xc02ef910; callr r6`) and continue (`r6 = ##0xc039d470; jumpr r6`). The
`callr`/`jumpr` idiom (not `call`/`jump`) is required: llvm-mc rejects a numeric-literal
operand for `call`/`jump`. Registers: cave uses `r6`–`r11` only; `r0`–`r3`, `r16`, `r17`
preserved; the cave's `r31` clobber is harmless (frame-saved by `allocframe(#0x8)`,
restored by `dealloc_return` at `0xc039d49c`; `0xc039d470..0xc039d49c` never reads `r31`).

**Open caveat.** §4.4a found `0xc039d460` is a **shared forwarder** to the generic
dispatcher `0xc02ef910 → 0xc0fe13c0`; and re-reading `r1:0 = combine(##-0x3e1ea730,#0x0)`
with `combine(high,low)` semantics puts `0xC1E158D0` in **r1**, `r0 = 0`, so the following
`cmpb.eq(r0,#0)` is ambiguous. Whether the ring is sleepmgr-only ingress or a broader ML1
dispatch is therefore settled **empirically** by the msg-id histogram.

**Image / deploy.** `modem.mdt md5 = 030f528376f38cce5a1e9fb0803075de`; built from pristine
stock (`scratch/diag_patch_v27/build_diag_patch_v27.py`), hash re-verify PASS; deployed with
sha256 read-back PASS (`scratch/deploy_v27_ring.py`). Reader: `scratch/read_v27_ring.py`.

### 6.7 v27 RESULT (2026-10-04) — the ingress ring confirms the missing RF wakeup confirmation

Fatal: `lte_ml1_common_timer.c:390`, AP dmesg `914.458760 s`, dump `scratch/v27_run/dump_fatal_916.bin`
(md5 `4df0b22e3d7893ad2683881793503c39`). Marker OK; `seq = 22564`; ring window seq 18469..22564
(last ~163 s). **The hook is sleepmgr-specific** — every one of the 23 distinct msg-ids is a
sleepmgr message (`0x42b02xx`, `0x40a08xx`, `0x4020425`, …), i.e. `0xc039d460` is entered with
`r1 = the ML1 msg id`. Callers: `0xc043b130` (= the router's fused call at `0xc043b124`) and
`0xc039e7fc` (= the sleepmgr's own `OBJ_START_REQ` self-post at `0xc039e7ec`) — both consistent
with the `r31` = next-packet-boundary rule.

**The ingress is a repeating ~13–15-message cycle** (308 cycles; state trajectory passes through
`SLEEP(3)`, `OFFLINE_WAKEUP(9)`, `OFFLINE_RECORD(10)`, `OFFLINE_SLEEP_WAIT(11)` and the ONLINE
states). The canonical cycle:

```
OBJ_START(0x42b0202) → GOTO_SLEEP(0x42b0209) → WMGR_RESULT×2(0x42b040b) → WAKEUP_REQ(0x42b0205)
 → OFFLINE_GOTO_SLEEP(0x4020425) → RF_WAKEUP_CNF(0x42b0801) → RXLM_RF_SCRIPT(0x40a080e)
 → ASYNC_WAKEUP(0x40b0806) → STMR_ON(0x42b0206) → SAMPLE_DONE(0x40a041b)
 → RF_SLEEP_CNF(0x42b0802) → LL1_SYS_SLEEP_CNF(0x40a0808) → OFFLINE_GOTO_SLEEP → UPD_SCLK(0x42b0208)
```

**★ The anomaly — identical to v26, now from the ingress side.** `STMR_ON_REQ` occurs **266**
times; it is preceded by `RF_WAKEUP_CNF` in **265** of them. The **sole exception is the LAST one**
(seq `22561`, state 9 `OFFLINE_WAKEUP`): `… WAKEUP_REQ(22557) → OFFLINE_GOTO_SLEEP(22558) →
RXLM_RF_SCRIPT(22559) → ASYNC_WAKEUP(22560) → **STMR_ON(22561, no RF_WAKEUP_CNF)** →
SAMPLE_DONE(22562) → OFFLINE_GOTO_SLEEP(22563) → OFFLINE_ENABLE_REQ(22564) → FATAL`. So the
sleepmgr **armed its sleep timer without the RF-wakeup confirmation**, and the timer slave then
fataled — exactly the v26 egress result (§6.5), independently reproduced. The RF wakeup
confirmation was present for the previous ~163 s and failed only in the final cycle.

**Interpretation.** This strengthens the §112.57.x model: the **RF wakeup fails** (no
`RF_WAKEUP_CNF`), the sleepmgr nonetheless arms the sleep timer, and the ML1 timer slave asserts.
It does **not** reveal *why* the RF wakeup fails — the root cause remains OPEN. Reproduce with
`scratch/analyze_v27_cycles.py`.

### 6.8 The v28 instrument — the RF-CNF callback ring (built + deployed + RESULT 2026-10-04)

v26/v27 see only the sleepmgr's *messages*; neither can see the **RF driver** side that produces
`RF_WAKEUP_CNF`. v28 hooks the ONE function that emits every `RF_*_CNF` and records its **caller**.

**The RF-confirmation sender `0xc039e580`.** It is entered ONLY through four 8-byte tail-call
stubs, each setting `r2` = a selector then `jump 0xc039e580`:

| stub | r2 | message | id |
|---|---|---|---|
| `0xc039e570` | 0 | `RF_WAKEUP_CNF` | `0x42b0801` |
| `0xc03a0b70` | 1 | `RF_EXIT_CNF` | `0x42b0804` |
| `0xc03a0b40` | 2 | `RF_SLEEP_CNF` | `0x42b0802` |
| `0xc039e700` | 3 | `RF_ENTER_CNF` | `0x42b0803` |

Verified from the switch at `0xc039e688` (`0xc039e698 r3=##0x42b0801` for r17==0/else;
`0xc039e6b0`/`0xc039e6c8`/`0xc039e6e0` for r17==1/2/3). The stubs are **completion callbacks**:
`0xc039b95c` stores `0xc039e700` into a `0x4290203` RF-request payload (built at `0xc039b93c` /
`0xc039baf4`); the RF-driver dispatcher range-checking `0x4290203` is `0xc0313f1c`. So when the RF
driver completes the request it calls the callback → stub → `0xc039e580`, and at `0xc039e580`
**entry** `r31` = the RF driver's return address. Hooking the sender thus captures the **RF driver
code path** that produced each confirmation — the piece v26/v27 could not see.

**Hook site — the function ENTRY `0xc039e580`.** Its first packet is the clean 4-byte duplex
`{ memd(r29+#-0x10)=r17:16; allocframe(#0x20) }` (word `0x401cf4eb`). The NEXT packet `0xc039e584`
is a **FUSED 16-byte packet** `{ call 0xc02d1140; r17 = r2; r18 = r0; memd(r29+#0x10) = r19:18 }`
(spans `0xc039e584..0xc039e594`; `0xc02d1140` is a leaf stub `{ r0 = #0x0; jumpr r31 }`). The entry
is the only clean packet boundary where BOTH `r2` (selector) and the true caller `r31` are live.

**v1 crash (mid-packet continuation).** v1 hooked the 4-byte `{ call }` at `0xc039e584` and
continued at `0xc039e588` — **MID-PACKET** — so the fused packet's `r18 = r0` re-executed with the
cave's r0=0 → `Assert cnf_msg != NULL failed` at `lte_ml1_sleepmgr_stm.c:4344` (task ML1 MGR,
uptime 16 s). v2 re-targets the ENTRY and continues at `0xc039e584`.

**★★★★★ The site-packet parse-bits bug (v2 crash cause, fixed).** The `{ jump 0xc003054c }` word
must be a **standalone** 4-byte instruction with packet parse bits `[15:14] = 0b11`. The builder's
base `0x58004000` gave `0b01`, so the CPU decoded the following packet's words as extra
instructions of THIS packet → **illegal-instruction `:Excep` at the site PC `0xc039e580`** (all
GPRs 0, `BADVA=0xe2eea008`, task AMSS0, ≈20 s modem-uptime crash-loop). Fix = base **`0x5800C000`**;
verified against `llvm-mc` label assembly: site→cave = **`e6cf9259`** (correct) vs `e64f9259`
(buggy; only bit 15 differs). ★ The parse bits must match the replaced packet's structure: the
stock site `0x401cf4eb` is a **duplex (parse=00)** → parse=11; v27's stock site `0xc039d46c` was
parse=01, so v27's parse=01 coincided (why v27 ran).

**Cave (`0xc003054c`, 120 B).** Reproduce the entry prologue duplex byte-for-byte (it saves r31
into the new frame, so the fused packet's `call` may clobber r31; the cave logs r31 BEFORE it), then
append `{seq, sel=r2, caller=r31, state}` to ring `0xc1d4c600` (header `0xc1455000`, marker
`0x76323801`, site VA `0xc039e580`; `state = memw(0xc1e158d0+0x4) & 0xffff`), then
`r6 = ##0xc039e584; jumpr r6`. Registers: cave uses `r6`–`r11` only; `r2`/`r19`/`r29` preserved.

**Image / deploy.** `modem.mdt md5 = 0f57e8836314ac863f7053316979b220` (parse-fixed; buggy v2 was
`ea7cc5a8…`); built from pristine stock (`scratch/diag_patch_v28/build_diag_patch_v28.py`), cave
re-disassembled and every constant verified, hash re-verify PASS; deployed with sha256 read-back
PASS (`scratch/deploy_v28_ring.py`). Reader: `scratch/read_v28_ring.py` (sel → message table; sel,
caller and state histograms); monitors `scratch/v28b_wait.sh` / `scratch/stock_watch.sh` (STOCK ran
360 s clean = negative control). AP rebooted 2026-10-04 with `preemptive_ssr_enabled=0`.

**★ RESULT (2026-10-04; full write-up in ledger §112.64).** RUN 1 (cold modem) WEDGED (data-path
death, no coredump; the Stage-3 SSR at AP 1435 s wiped the ring). RUN 2 (the crash-recovery warm
restart) FATALED at **modem-uptime 902.77 s** (`lte_ml1_common_timer.c:390`, task `tmr_slave3`); the
ring was captured: `scratch/v28_run/dump_fatal_905.bin` (md5 `dc64876e…`).
- Ring **seq = 2541**, strictly alternating: **RF_WAKEUP_CNF ×1270 / RF_SLEEP_CNF ×1271**, no
  non-alternating runs. The **last emission is a RF_SLEEP_CNF** ⇒ the final **RF wakeup confirmation
  is absent**.
- Sleepmgr object `0xc1e158d0`: `+0x04 = 9` = **OFFLINE_WAKEUP** at the fatal — waiting for the RF
  wakeup that never came. (Matches v27's ingress-side finding.)
- **NEW:** the RF-driver caller resolves the two wakeup paths — **ONLINE** wakeup `0xc0314d48`,
  **OFFLINE** wakeup `0xc03155fc`; sleep always `0xc0315440`. The missing confirmation is an
  OFFLINE wakeup.
- ⇒ the stall is on the **RF-driver completion side**, not the sleepmgr. **Root cause still OPEN.**
- ★ New device-side instrument `scratch/v28_wedgecap.sh` forces an on-demand coredump at the
  stall-watchdog's commit point, to capture a **WEDGE** (which produces no coredump) before the
  Stage-3 SSR wipes the ring.

**★★★★★ WEDGE RESULT (2026-10-04; full write-up in ledger §112.65).** A **WEDGE** was captured: data
death at modem-uptime **≈898–928 s**, no fatal and no SSR restart; `v28_wedgecap.sh` forced the dump at
the watchdog commit (AP 8355 s) → `scratch/wedge_run/dump_wedge_8357.bin` (85,398,475 B, md5
`db8b304a…`).
- Sleepmgr `0xc1e158d0` `+0x04 = 4` = **ONLINE_WAKEUP** ⇒ **confirms §112.58: FATAL ⇒ state 9,
  WEDGE ⇒ state 4** (n = 2 for the wedge).
- Ring **seq = 3383**, RF_WAKEUP_CNF ×1691 / RF_SLEEP_CNF ×1692, **last = RF_SLEEP_CNF** — the SAME
  missing-final-wakeup signature as the fatal ⇒ the RF-CNF tail alone does **not** discriminate.
- **NEW discriminator — the REGIME:** the fatal's last 76 emissions are all OFFLINE-regime (state 9);
  the wedge's last 10 are ONLINE-regime (state 4), having transitioned **OFFLINE→ONLINE ~3 s before
  capture** (seq 3374).
- ⚠ The wedge is captured ~145–160 s after the data death (at the watchdog commit, not the death), and
  the ring shows the sleepmgr still emitting RF-CNFs, so the state may be a phase; n = 2.
- ⚠ **§112.64's cold/warm hypothesis is FALSIFIED** — this wedge was a **warm** modem restart
  (`echo restart`), not a cold boot. The manifestation is a race.

---

## 7. SOP statement

* **Ground truth first:** every address/name in this doc was read from the stock
  ELF disassembly and/or the v25 fatal coredump; no claim is from a name alone.
  State names, message names, class name and the object field offsets were read
  byte-for-byte from the coredump.
* **No blind patch:** nothing was written to the baseband in this session. The
  v24/v25 diagnostic images from the prior session were rolled back; the device
  is on stock `modem.mdt` md5 `1a6f9507e03d4ddbbf1977af81ecdbd7` with the
  pre-emptive-SSR mitigation enabled.
* **Reversibility:** no irreversible operation was performed.
* **Honesty:** the ~900 s root cause is **not** fixed; this doc narrows the
  wakeup path and proposes the next instrument.
* **Ledger:** §112.59 of `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md` references
  this doc; memory updated in the same session.
