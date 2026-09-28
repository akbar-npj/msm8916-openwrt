# Doc 225 — The descriptor-emitter map, a corrected immext scanner, and the uimsub_manager hotswap latch that forces card status UNKNOWN

**Date:** 2026-09-28
**Ledger:** this doc MUST be read with `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md` (item 31).
**Scope:** UZ801-on-HMU05 offline gate. No baseband patch is proposed *or applied* by this doc.

---

## 0. SOP compliance statement

| SOP step | Taken? | Note |
|---|---|---|
| Verify against stock HMU05 ground truth | **yes** | every ELF fact is computed on **both** `scratch/uz801_fw/modem.elf` and `GitIgnore/MelbonWhiteStock_Dump/modem_extracted/image/modem.elf`; §6 confirms the three UZ801-only module names are **absent from the stock ELF** |
| Pre-register before measuring | **yes** | the §7 chain is stated as a **falsifiable prediction** with the exact observation that would kill it, *before* the patch that would test it is proposed |
| Freeze/hash every capture | **yes** | no new capture was taken; the five archived captures are read-only inputs |
| Assert the control TOOK | **yes** | §2 validates the corrected scanner against a **known-positive** site before trusting its negatives |
| Never blind-patch a baseband | **yes** | nothing was written to the modem or to the baseband |
| State irreversible steps first | **yes** | §8 states the firmware-patch blast radius before proposing it |
| Update the ledger in the same session | **yes** | ledger item 31 |

---

## 1. Achieved vs Expected

| Item | Expected | Achieved | Verdict |
|---|---|---|---|
| Explain why Doc 224 could not find the `cmregprx.c` log site | a locating method | the descriptor is **statically unreachable**; the method was wrong, not the search (§4) | **MET** |
| Trust the reference scanner | a working immext scanner | the shipped scanner has a **defective guard**; corrected and validated (§2) | **MET** |
| Reach the UZ801-only modules | read their code | `uimsub_manager.c` reached; **16 functions** located (§5) | **MET** |
| Explain the `is_gwl_subs_avail 0->0` gate | a mechanism | a **candidate** mechanism found and stated falsifiably (§7) | **PARTIAL** |
| Resolve the offline latch | `is_gwl_subs_avail 0->1` | still `0->0` | **NOT MET** |
| Modem online | `+CFUN: 1` | still `+CFUN: 7` (verified this session) | **NOT MET** |

**The goal is not met.** This doc contributes one instrument repair, one important
negative, and one new candidate mechanism.

---

## 2. `desc_table.py refs` is DEFECTIVE — corrected, and the fix is validated

`scratch/desc_table.py refs <elf> <va>` (Doc 205 §4) is the shipped Hexagon
`immext`-reference scanner. **Its GUARD 2 is wrong and it silently under-reports.**

Disassembly of a site whose reference is *not* in doubt — `FUN_c0685dc0 @ c0685dc0`,
which Ghidra renders as `FUN_c091d840(&DAT_c165a690)` — shows the real encoding:

```
c0685e04:  9a 56 16 0c   immext(#0xc165a680)      raw word = 0x0C16569A
c0685e08:  00 c2 00 78   r0 = ##-0x3e9a5970       raw word = 0x7800C200
```

* `immword(0xc165a680)` = `0x0C16569A` — **exactly the immext word.** The scanner's
  key computation is correct.
* `0x7800C200 & 0x3F` = `0x00`, but `A & 0x3F` = `0x10`. **GUARD 2 demands
  `next_word & 0x3F == A & 0x3F`, which is false.** The low 6 bits of the constant
  live in the following `##` instruction in a *different* encoding, so the guard
  discards every real hit.

**Measured effect:** with the shipped scanner, `refs(0xc165a690)` returns **0**;
with GUARD 2 removed, the same query returns **5** hits
(`0xc0685e04, 0xc0685e48, 0xc0685fe8, 0xc068610c, 0xc068615c`). The scanner's
negatives in Doc 205/206/224 were therefore **unsafe** in the under-reporting
direction — they could not have produced a false positive, but they could and did
produce false zeros.

**Corrected tool:** `scratch/doc225_refs2.py` (raw-ELF scan, immext key only, prints
the following word for inspection). A second tool, `scratch/doc225_str_refs.py`,
searches the **disassembly text**; note that `llvm-objdump` prints
`immext(#<decoded address>)`, i.e. the **decoded value**, not the raw encoding — a
first version of that script used the raw encoding as the key and returned a
false zero. Both tools now agree with each other and with Ghidra on the
known-positive.

---

## 3. The descriptor-emitter map — `scratch/doc225_emitter_map.py`

Every `FUN_xxx(&DAT_<va>)` call site in the decompile is a candidate F3 log
emitter, because the argument is the `{level,line,strptr}` descriptor (Doc 205 §3).
Grouping by emitter and by descriptor **page** (VA >> 12) gives:

| emitter | call sites | pages | note |
|---|---|---|---|
| `FUN_c091d840` | 17 137 | 230 | legacy MSG emitter; `if (level < *(gp+0x3970))` |
| `FUN_c08a4c90` | 12 782 | 123 | the "does not return" sink |
| `FUN_c0287020` | 4 569 | 85 | second emitter — **carries `uimsub_manager.c`** |
| `FUN_c091da60(desc, 4)` | 4 669 | many | third emitter — also carries `uimsub_manager.c` |
| `FUN_c091d8c0` | 2 638 | 215 | fourth |

This map is what makes the next section possible: a descriptor page that **no**
emitter touches is a page whose records are nonetheless on the wire.

---

## 4. IMPORTANT NEGATIVE — the `cmregprx.c` and `mcfg_uim.c` descriptors are STATICALLY UNREACHABLE

Doc 224 §5 recorded, as its open item, that the `is_gwl_subs_avail` descriptor
(`0xc165f420` UZ801 / `0xc155ee20` stock) has no bare-literal reference and that an
`immext` scan returns 0. **That observation is correct, and it is not a scanner
bug.** Four independent tests agree:

1. **Decompile substring search** for the pages `c165e`/`c165f` → **0 lines**
   (the only `165f4`/`165ec`/`165ef` hits are substrings of *other* addresses such
   as `DAT_c17165f4`, `DAT_c3165ec0`, `DAT_c3165ef0`). The stock decompile is the
   same for `c155ee2`/`c155edc`/`c155ec3`/`c155ea3` → **0**.
2. **Emitter map (§3):** `cmregprx.c` page `0xc165e` → **NO EMITTER REFERENCES
   THIS PAGE**.
3. **Full-ELF disassembly** (`llvm-objdump -d`, 5 219 227 lines) grepped for
   `immext(#0xc165[ef]` → **0**. Sanity control in the same file:
   `immext(#0xc165a680)` → **5**.
4. **Exhaustive raw-ELF scan** of every 0x40-aligned address in the whole window
   `0xc165e000`–`0xc1660000` (128 keys, `scratch/doc225_window_refs.py`) → 5 hits,
   **all 4-byte-unaligned, all inside the seg[24] rodata blob** (`va=0xc3debe47`,
   `0xc3dfb49c`, `0xc4034e90`, `0xc3e05337`, `0xc3e06625`) — chance coincidences,
   not code.

**Consequence, stated plainly:** the asset "locate a `cmregprx.c` log site by its
descriptor" **does not exist for either build**. The same is true of `mcfg_uim.c`
(pages `0xc16ad`/`0xc16ae` → 0 for the specific descriptor VAs, despite the page
aggregate showing 60 `FUN_c091d840` sites, which belong to *other* files in the
same 4 KB page). The records are nevertheless on the wire (line 8129 verified
byte-for-byte in §6), so these files' logging goes through a mechanism that does
**not** embed the descriptor address — a lookup keyed on `(file, line)`, or an
address computed at run time.

**This retires Doc 224 §5's open item as unachievable by that route.** The
`is_gwl_subs_avail` *computation* must be reached another way (§5).

---

## 5. A route that DOES work — NV path literals, and 16 `uimsub_manager.c` functions

F3 **format** strings are not embedded in code, but **NV path** literals are.
`scratch/doc225_str_refs.py` resolves each NV path literal to its VA and looks for
the `immext(#<va & ~0x3F>)` that materialises it. That produced:

| NV path literal | VA | reference sites |
|---|---|---|
| `…/uimdrv/feature_support_hotswap` | `0xc18f0e5f` | `c053f0b8`, `c05468a8` |
| `…/uimdrv/nv_active_slot_configuration` | `0xc18f0d5a` | `c053e980`, `c053f0d0`, `c055c82c` |
| `…/uimdrv/me_hotswap_configuration` | `0xc18f0d21` | `c053e8c4`, `c053e908`, `c053f06c` |
| `…/uimdrv/uim_features_status_list` | `0xc18f0ce8` | 8 sites, `0xc053c37c`–`0xc0546ba0` |

`uimsub_manager.c` **is** reachable, via `FUN_c0287020(&DAT_c161b9xx)` and
`FUN_c091da60(&DAT_c161b9xx, 4)`. **16 functions** were located:

| function | source lines it logs |
|---|---|
| `FUN_c055f1c0` | 139 |
| `FUN_c055f55c` | 343, 363, 369, 387 |
| `FUN_c055c348` | 423 (`None of the slots are active!!!`) |
| `FUN_c055f854` | 462 |
| `FUN_c055f7ec` | 515 |
| `FUN_c055dea4` | 660 |
| `FUN_c055c68c` | 698, **711**, 735 |
| `FUN_c055dfe8` | 714 |
| `FUN_c055e0f4` | 747, 783, 819 |
| `FUN_c055d104` | 788 |
| `FUN_c055e344` | 860 |
| `FUN_c055d154` | 879 |
| `FUN_c055e458` | 900 |
| `FUN_c055c7d4` | 910, 925 (+ 930/933/949 via `FUN_c091da60`) |
| `FUN_c055bfd8` | 771, 805, 809, 812, 814, 824 |
| `FUN_c055d0e4` | 765 |

---

## 6. `uimsub_manager.c` / `mcfg_uim.c` / `qpCircularBuffer.c` are UZ801-ONLY — reconfirmed on the ELF

Byte counts of the file-name literals:

| name | UZ801 ELF | stock HMU05 ELF |
|---|---|---|
| `uimsub_manager.c` | **35** | **0** |
| `mcfg_uim.c` | **32** | **0** |
| `qpCircularBuffer.c` | **2** | **0** |

And the live modem **runs** them (the stock arm does not):

| capture | `uimsub_manager.c` records | `mcfg_uim.c` records |
|---|---|---|
| `doc210/boot_210_canon.bin` | 9 | 9 |
| `doc211/uz801_v19_boot_240.bin` | 6 | 9 |
| `doc211/stock_boot_211.bin` | **0** | **0** |
| `doc211/stock_boot_240.bin` | **0** | **0** |
| `hmu05_stock_boot.bin` | **0** | **0** |

The UZ801 records observed at boot are, in order:
`L643 Registering QMI sub manager callback pointer with UIMDRV` →
`L708 copying instance 0x%x data to sm global` (instances 0 and 1) →
`L711 sub_mag Setting card status as unknown as hotswap is disabled` (**×2**) →
`L735 QMI syncing latest slot information` → `L809 sub_mgr slot : iccid 0x%x = 0x%x` (×3);
plus, from `mcfg_uim.c`, `L318 mcfg_uim_autoselect NV=5` then `=0`, `=0`,
`L1287 ICCID 89918610400794228644`, `L1555 Successfully open card ext sessions with 1 session(s)`.

**⚠ Ring-buffer correction to an earlier reading of this session.** The captures are
**multi-boot ring dumps**, so *absence of a string is not evidence of a code path
that stops*. The strings `Reporting QMI latest physical`, `card-status`,
`activity status` and `logical slot` are absent from the raw captures, yet the
`L809` record is present — and `L771`, `L805`, `L809`, `L812`, `L814` are all emitted
by the **same** function `FUN_c055bfd8` (verified: each of those descriptors has
exactly **one** reference in the whole image, and all of them are inside
`FUN_c055bfd8`). Eviction from the ring, not a halted path, is the consistent
explanation. Any "string X never appears ⇒ branch never taken" claim made from
these captures is unsafe.

---

## 7. The candidate mechanism — a hardcoded hotswap flag forces card status UNKNOWN

Read from the UZ801 decompile (`Docs/Modem Stability/Modem RE/uz801/modem_full_decompiled.c`).

### 7.1 The flag is hardcoded to 0

`FUN_c055f1c0` (`uimsub_manager.c` L139) initialises a 4-entry per-slot table with
stride `0x1d`:

```c
do {
    (&DAT_c34b120b)[iVar2] = 0;      /* <-- the hotswap flag, HARDCODED */
    (&DAT_c34b120c)[iVar2] = 1;
    ...
    iVar2 = iVar2 + 0x1d;
} while (iVar12 != 4);
```

`DAT_c34b120b` has **exactly two references in the entire image**: this write
(= 0) and the read in §7.2. There is no NV-driven writer. The `hotswap` NV items
are read *elsewhere* (`FUN_c0546888`), but their result never reaches this flag.

### 7.2 It is copied, then read

```c
/* FUN_c055f8f8 @ 0xc055f8f8 */
uVar2 = (&DAT_c34b120b)[iVar6];          /* iVar6 = slot * 0x1d */
(&DAT_c34b15bc)[iVar7] = uVar2;          /* iVar7 = slot * 0x14 */
```

```c
/* FUN_c055c68c @ 0xc055c68c — logs L735 "QMI syncing latest slot information" */
if ((&DAT_c34b15bc)[(uint)*pcVar2 * 0x14] == 0) {
    FUN_c0287020(&DAT_c161b940);         /* L711 "…Setting card status as unknown as hotswap is disabled" */
    *pcVar7 = '\x03';                    /* card status := 3 (UNKNOWN) */
} else {
    *pcVar7 = pcVar2[0x3a27];            /* the real status */
}
```

```c
/* FUN_c055bfd8 @ 0xc055bfd8 — logs L771 then the L805/L809/L812/L814 loop */
if ((&DAT_c34b15bc)[uVar2 * 0x14] == 0) {
    (&DAT_c29a90ed)[uVar2 * 0xe] = 3;                    /* UNKNOWN */
} else {
    (&DAT_c29a90ed)[uVar2 * 0xe] = pcVar1[0x3a27];
}
```

### 7.3 The NV side, measured live (read-only, with the repaired tool)

| path | live result |
|---|---|
| `…/uimdrv/feature_support_hotswap` | **err 0x02 — ABSENT** |
| `…/uimdrv/nv_active_slot_configuration` | **err 0x02 — ABSENT** |
| `…/uimdrv/me_hotswap_configuration` | present, 8 B, `14 00 01 00 0a 00 00 00` |
| `…/uimdrv/uim_features_status_list` | present, 128 B |
| `…/uimdrv/uim_hw_config` | present, 256 B |
| `…/mmgsdi/features_status_list` | present, 64 B |

`FUN_c0546888` reads the hotswap feature as `uim_features_status_list` **then**
`feature_support_hotswap`, returning 0/1/2 — so the NV *is* consulted, but only
downstream of the hardcoded flag.

### 7.4 The stated chain, and its falsifier

> `FUN_c055f1c0` hardcodes the hotswap flag 0 → `FUN_c055f8f8` copies it →
> `FUN_c055c68c` / `FUN_c055bfd8` set the slot card status to **3 (UNKNOWN)** for
> every slot → **the subscription is never "available"** → `cmregprx.c` computes
> `is_gwl_subs_avail = 0` → MMOC stays in `3(OFFLINE)` → `+CFUN: 7`.

**The last two links are NOT yet proven.** What *is* proven is links 1–3 (code,
above) and the runtime correlate: "hotswap is disabled" is logged **exactly twice**
per boot, matching the two reported instances.

**Falsifier, stated before any patch:** patch the single byte in `FUN_c055f1c0` so
the flag is written as **1**, deploy, reboot, and read
`=CMREGPRX= ph_stat_chgd: as_id=%d, is_gwl_subs_avail %d->%d` from a fresh F3
capture. **If it still reads `0->0`, the chain is FALSIFIED** and card status is
not the input. If it reads `0->1` (and `AT+CFUN?` becomes 1), the chain is
confirmed and this is the port's first real root cause.

### 7.5 Side-by-side at the flag (why the gate is downstream, not here)

| arm | MMOC transaction | WMS client event | flag |
|---|---|---|---|
| stock (all three) | `Curr_trans 1(SUBSC_CHGD)` | `WMS_CMD_CM_SUBS_EVENT_CB` | **`0->1`** |
| UZ801 (both) | `Curr_trans 3(OFFLINE)` | `WMS_CMD_CM_PH_EVENT_CB` | **`0->0`** |

Both arms emit the identical `ph_stat_chgd` triple
(`cmregprx.c L14079`/`L14182` then `L8129`/`L8135`/`L8139`), so the difference is
in the *transaction state*, i.e. upstream of `cmregprx.c`.

---

## 8. Blast radius of the proposed test (stated before acting)

Patching `FUN_c055f1c0` means **modifying the baseband image** (`modem.b16` /
`modem.mdt`), which is exactly the class of change the ledger SOP requires to be
announced. Concretely:

* The deployed set is the guard-backed UZ801 v3.2 set; `modem-guard` reverts after
  > 3 bootloops, and `/overlay/fwbackup/hmu05_stock/` holds the stock set.
* A wrong byte can bootloop the modem; the guard bounds the damage but a partial
  deploy has happened before (item 30's newline-in-`$FILES` bug).
* The stock HMU05 set must be re-deployed to revert to a working (if offline) state.
* **Nothing in this doc has been applied.** The patch is proposed, not executed.

---

## 9. Artifacts

| artifact | what it is |
|---|---|
| `scratch/doc225_find_ref.py` | decompile substring search for descriptor renderings |
| `scratch/doc225_table_map.py` | PT_LOAD/segment map + descriptor-page histogram |
| `scratch/doc225_window_dump.py` | every `{packed,strptr}` entry in a VA window, with the file name |
| `scratch/doc225_refs2.py` | **the corrected** raw-ELF immext scanner |
| `scratch/doc225_window_refs.py` | exhaustive per-0x40 immext scan of a VA window |
| `scratch/doc225_desc_hits.py` | per-descriptor immext key check against a disassembly |
| `scratch/doc225_str_refs.py` | **NV-literal → code-address** finder (the route that works) |
| `scratch/doc225_emitter_map.py` | emitter → descriptor-page map |
| `scratch/doc225_file_desc.py` | all descriptors for a set of file prefixes |
| `scratch/doc225_immext_hits.txt` | the (empty) result of the full-ELF page grep |
| `/tmp/uz801_dis.txt` | the full-ELF Hexagon disassembly (5 219 227 lines), regenerable |

---

## 10. Status

* The offline gate is **unchanged**: `+CFUN: 7`, `is_gwl_subs_avail 0->0`.
* **New, proven:** the shipped immext scanner is defective (§2); the `cmregprx.c`
  and `mcfg_uim.c` descriptors are statically unreachable (§4); `uimsub_manager.c`
  is reachable and its 16 functions are located (§5); the hotswap flag is
  hardcoded 0 and forces card status UNKNOWN (§7.1–7.3).
* **Open:** whether card-status-UNKNOWN actually drives `is_gwl_subs_avail`
  (§7.4's falsifier), and the `cmregprx.c` +30…+49 line delta (Doc 224 §5), which
  is now known to be reachable only by a non-descriptor route.
