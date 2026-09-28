# 207 — The F3 descriptor model corrected, the descriptor→code instrument, and the death of the `cmcc.c` axis

**Date:** 2026-09-26
**Device:** HMU05 4G modem stick (`Melbon HMU05`), OpenWrt 25.12.5, kernel 6.12.94 aarch64, `192.168.8.1`, SSH root.
**Firmware under test:** UZ801 v3.2 "-21" baseband, deployed as **v19** — `modem.b16` md5 `c71d53464a1e88f2072cbbb6f7f87475`, `modem.mdt` md5 `9f39ce579114bcf1383bbdce62a8d284`; `--dms-get-revision` = `UZ801_V3.0_21_V01R01B10 1 [Sep 07 2015]`.
**Status:** METHOD (a corrected descriptor model + a working descriptor→code instrument) + **two negative results that close an axis**. No firmware was built, patched or deployed; **no reboot**; **no device writes**.
**Ledger:** see `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md`; its §3/§7/§8 are updated in the same session as this doc.
**Predecessors:** `205` (the F3 log-descriptor table / the complete op-mode refusal), **`206`** (`FUN_c06928a8` is `cmcc.c`, not `cmmsc_auto.c`; the WMS input path is intact).

---

## 1. SOP compliance statement

| SOP step | Taken? | Detail |
| :-- | :-- | :-- |
| Verify against stock HMU05 ground truth | **Yes** | Every differential claim is measured on **both** arms in the same session: the **UZ801 ELF** `scratch/uz801_fw/modem.elf` (50 912 492 B) against the **stock ELF** `GitIgnore/MelbonWhiteStock_Dump/modem_extracted/image/modem.elf` (50 049 024 B), each paired with its own Ghidra full-decompile (`Docs/Modem Stability/Modem RE/{uz801,hmu05}/modem_full_decompiled.c`). The two stock `cmcc.c` descriptors were located from **stock's own seg25 string** `cmcc.c:=CM= MMGSDI EPS mmgsdi_status=%d`, not from a UZ801-derived address. |
| Byte-level / hash verification before attributing | **Yes** | All attribution reads `packed` from the ELF at the literal descriptor VA and cross-checks the *resolved* anchors (the ~8 % of descriptors whose second word is a real seg25 string pointer) against the string text. `FUN_c06928a8`'s descriptors resolve to `cmcc.c:3027…3180` and stock's counterparts to `cmcc.c:264…3096` — matching Doc 206's independently derived line staircase. |
| Backup / reversibility | **Yes** | **Nothing was written to the device.** All writes are host-side: four new tools in `scratch/` and two generated maps. No firmware, NV, guard or partition was touched; the guard was not consulted because no reboot occurred. |
| No blind patching of the baseband | **Yes** | **Nothing was patched.** The doc *closes* a patch axis (§6) rather than opening one. |
| Reboot discipline / instrument-not-perturbing | **Yes** | **No reboot.** All new measurements are offline (two ELFs + two decompiles + already-archived captures). |
| Mandatory ledger update in the same session | **Yes** | Doc 197 §3/§7/§8. |

---

## 2. Achieved vs Expected

| Intent | Expected | Achieved | Status |
| :-- | :-- | :-- | :-- |
| Execute Doc 206 §9 item 1 — locate the real `cmmsc_auto.c` entry point | a function | **NOT MET — but the *reason* is now exact.** The `cmmsc_auto.c` descriptors are referenced by **no literal and no `immext` in either build** (0 of 69 775 UZ801 sites, 0 of 68 186 stock sites). The "locate by descriptor literal" route cannot reach that module in *either* image; the module uses a log-call form whose descriptor is never materialised as a constant (§7.1). The four descriptor VAs are recorded for both builds (§7.2) so the next attempt starts from them | **NOT MET** (route bounded) |
| Execute Doc 206 §9 item 2 — "is `cmcc.c` reachable at all before GROUP 11 can matter?" | a yes/no | **MET (negative, and it kills the axis).** `cmcc.c` is reachable in **both** builds in *exactly the same way*: 13 UZ801 functions emit `cmcc.c` logs (90 sites) vs 12 stock functions (89 sites), with a **1:1 site-count correspondence**; the **exact** `cmcc.c` line sets independently reproduce Doc 206's revision staircase (`+0 → +4 → +7 → +12 → +19` plus a UZ801-only L2535); and the function that is the sole entry to the service-available chain (`FUN_c06928a8`) is referenced **only at its own definition** in UZ801 — **and so is its stock counterpart `FUN_c066fa30`**. The no-static-caller property is therefore **not a build-to-build difference** (§5) | **MET** (negative) |
| Decide whether GROUP 11's target can ever change the outcome | a verdict | **MET — it cannot.** UZ801's `FUN_c06928a8` and stock's `FUN_c066fa30` are **structurally identical**: same guard, same `0xff` alloc, same field offsets (`0x29c`, `0xa68`, `0x1509`, `0x150a`, `0x150f`), same registration IDs (`0x456`, `0x422`), same single tail call (`FUN_c06917f0`/`FUN_c066e9a4`, `0xffffffff`), same cleanup. GROUP 11 patches `0xc0691950` *inside* that identical chain ⇒ **inert by construction**, which is exactly what v14/v19 measured (§6) | **MET** |
| Correct the F3 descriptor model where Doc 205 over-stated it | a correction | **MET — and it is a significant one.** `word1` is a seg25 **string pointer for only ~8 % of sites**; for ~92 % it is a **32-bit hash**. `desc_table.py`'s `entries` mode filters on `word1 ∈ seg25` and therefore **silently drops 92 % of the table** — the cause of the "`cmmsc_auto.c` has only 23 statements" reading and of several "unlocatable" conclusions (§3) | **MET** |
| Decide whether the hash is usable as a cross-build key | a measurement | **MET — it is.** ~90 % of the descriptor hash values are shared between the two images (10 039 of UZ801's 11 200 = **89.6 %**), so a hash is a valid build-independent statement key — which is what `xmap.py` was groping for with a mislabelled model (§3.3) | **MET** |
| Keep the record honest about method | corrections recorded | **MET.** Four method defects found and documented, **two of them in instruments used earlier in this campaign**: `desc_table.py`'s crash-to-empty-output bug (§3.2), `re_index.py --callers`'s blindness to function-pointer arguments (§8.1), `desc.py`'s wrong model (already known), and a **prefix-specific token regex in this session's own new tool** that produced a confident zero on the stock arm (§4.2) | **MET** |
| Not make the ~900 s fatal worse | no new fatal mechanism | unchanged; **no reboot, no SSR, no fatal** during the session | **MET** |

**The one-line truth:** the campaign's static route to the CM layer is now **measured, not guessed** — there is a
working descriptor→code instrument for both builds, the `cmcc.c` axis is **closed by a two-arm measurement**
rather than by an inference, and the one module that still cannot be reached statically (`cmmsc_auto.c`) is
now known to be unreachable *by that method in both builds*, so the next attack must come from the runtime or
from the `cmmsc_auto.c` descriptor VAs recorded in §7.2.

---

## 3. The corrected F3 descriptor model

### 3.1 What the model actually is

An F3 descriptor is 8 bytes:

```
{ u32 packed ; u32 word1 }        stride 8

packed = (line << 16) | level     level 0x0a/0x0b (cmcc.c), 0x2a (mmocmmgsdi.c),
                                        0x2e/0x2f (mmoc.c), 0x83/0x86 (qmi_nas.c), …
```

`packed` is valid for **every** site. `word1` is **one of two things**:

* a **string pointer into seg25** (`0xc4558000..0xc4614cec` for UZ801) — for **~8 %** of sites; or
* a **32-bit hash** (`< 0xc0000000`, i.e. not a VA at all) — for **~92 %** of sites.

Measured on the `cmcc.c` block at `0xc165c380…0xc165c3f8` (UZ801):

```
0xc165c380  packed=0x0b79000b line=2937  word1=0x52dc752d   hash
0xc165c388  packed=0x0b81000b line=2945  word1=0x0fb29ed5   hash
0xc165c390  packed=0x0b8f000b line=2959  word1=0x69ee87dd   hash
0xc165c398  packed=0x0bd3000a line=3027  word1=0xa18d6925   hash
0xc165c3a0  packed=0x0bf1000b line=3057  word1=0x45d1c103   hash
0xc165c3a8  packed=0x0bfa000a line=3066  word1=0xcfcc650f   hash
0xc165c3b0  packed=0x0bfe000a line=3070  word1=0x5ec07dda   hash
0xc165c3b8  packed=0x0c09000a line=3081  word1=0xc4561700   strptr -> "cmcc.c:=CM= MMGSDI EPS mmgsdi_status=%d"
0xc165c3c0  packed=0x0c0d000b line=3085  word1=0xdb60699a   hash
0xc165c3c8  packed=0x0c1b000b line=3099  word1=0x68d29ef9   hash
0xc165c3d0  packed=0x0c2b000b line=3115  word1=0xc4561728   strptr -> "cmcc.c:=CM= MMGSDI EPS srv_available=%d, cm_mmgsdi_acl_availability=%d"
```

**Doc 205 §3's model was therefore half right.** The `{packed, strptr}` layout is correct, and its own
docstring even warns "some entries have a second word that does NOT point into a loaded segment … the word is
a hash, not an address. **Filter by segment membership, never by shape.**" — but the *consequence* was never
drawn: filtering by segment membership keeps only ~8 % of the table, and every count taken from
`desc_table.py entries` is therefore a **lower bound of unknown size**, not a count.

### 3.2 A second defect in the same tool: a crash that presents as a zero

`desc_table.py`'s `entries`/`cmd_file` append `off2va(segs, j)` without a `None` guard. When a matched window's
own offset falls in a **file gap** (the ELF has gaps between PT_LOADs) the value is `None`, and
`cmd_entries` then does `"0x%08x" % va` → `TypeError`. With the usual `2>/dev/null` the script dies and the
output is **empty**, which reads as "no entries".

This is what produced the confusing result in this session that a **wide** VA range returned `0` while a
**narrow** range inside it returned `149`. Reproduced exactly:

```
$ python3 desc_table.py entries uz801_fw/modem.elf 0xc4558000 0xc4614cec 2>/dev/null | wc -l
0
$ python3 desc_table.py entries uz801_fw/modem.elf 0xc4565000 0xc4567000 2>/dev/null | wc -l
149
```

Fixed in `scratch/descref.py` (which skips `dva is None`). **Any earlier corpus number taken from a wide
`desc_table.py entries` call with stderr suppressed should be re-taken.**

### 3.3 The hash is build-independent — a usable cross-build key

Counting `{packed, word1}` windows with a tight level filter (`level ∈ {0x0a,0x0b,0x2a,0x2e,0x2f,0x83,0x86,…}`,
`0 < line < 200000`):

| | hash keys | strptr keys |
| :-- | --: | --: |
| UZ801 | 11 200 | 4 147 |
| HMU05 | 12 721 | 4 541 |
| **common** | **10 039 (89.6 % of UZ801's)** | 3 308 |

So a descriptor hash identifies the **same log statement** in both images. This is the mechanism `xmap.py`
was built around; its documented model (`{ u16 file_id; u16 msg_id; u32 hash }`) is a **mislabelling of
`{ packed, hash }`** — the first word is not two small fields, it is `(line<<16)|level`.

---

## 4. The instrument: a descriptor → code map

### 4.1 How it works

Log call sites pass the descriptor address as a **bare hex literal** (or as `&DAT_<va>`):

```c
FUN_c091d940(0xc165c398, param_3, param_4);          /* cmcc.c:3027 */
FUN_c091d8c0(0xc165c3b8, iVar3);                     /* cmcc.c:3081 */
FUN_c091d940(&DAT_c165c3d0, ...);                    /* cmcc.c:3115 */
```

Ghidra does **not** resolve the F3 strings, but it *does* fold `immext(base) + add(#off)` into the literal —
so the VA appears verbatim in the `.c` text. `scratch/logsite.py` therefore:

1. scans the decompile for `(?:0x|DAT_)([0-9a-fA-F]{8})` tokens;
2. reads `packed` from the ELF at each VA and rejects implausible ones (`level > 0xFF` or `line == 0`);
3. attributes the line to a file using the **resolved anchors** (the ~8 % with a real string pointer) and,
   for the rest, **table adjacency** — the table is written one file at a time, so the nearest resolved
   anchor in *address* order names the file (at `0xc165c398` the nearest anchor is `0xc165c3b8` = `cmcc.c`,
   `0x20` away).

Result:

| | literal log sites |
| :-- | --: |
| UZ801 | **69 775** |
| HMU05 | **68 186** |

Supporting tools added in the same session: `scratch/immbase.py` (histogram every `immext` immediate),
`scratch/va2func.py` (VA → enclosing function; 81 800 functions indexed for UZ801).

### 4.2 A prefix-specific token regex produced a confident zero (own defect, recorded)

The first version of the token pattern was `c16[0-9a-f]{5}`. That matches UZ801's `0xc165xxxx` descriptor
table but **silently skips**:

* stock's descriptors, which live at `0xc155xxxx` / `0xc156xxxx`; **and**
* UZ801's own `0xc18fxxxx` / `0xc17fxxxx` pointers.

The symptom was a **confident zero**: `--file=cmcc.c` returned `0` on the stock arm even though
`0xc155be10` demonstrably appears at stock decompile line 1127030. Fixed to `(?:0x|DAT_)([0-9a-fA-F]{8})`;
stock's `cmcc.c` count then went `0 → 89`. **This is the same failure shape as the corpus's earlier traps
(`feedback_source_reading_traps.md`): a search whose window was narrower than the claim.**

---

## 5. ★ The `cmcc.c` differential is NOT reachability

Doc 206 §4.2 warned that "a no-caller property cannot explain a build-to-build difference unless shown to
differ between builds" and left the stock-side census as a named open item. **It is now measured.**

### 5.1 Both builds have the same `cmcc.c` module, function for function

**The cleanest form of the result is the exact-line set** — the lines whose descriptors resolve to a real
`cmcc.c:` string, i.e. measured rather than inferred:

| | `cmcc.c` lines (exact) |
| :-- | :-- |
| **HMU05** (15) | `264 1078 1082 1088 1266 1329 1490` · `1616 1813 1816` · `2313 2341` · `2839 3062 3096` |
| **UZ801** (16) | `264 1078 1082 1088 1266 1329 1490` · `1620 1820 1823` · `2325 2353` · **`2535`** · `2858 3081 3115` |
| **shift** | `0 0 0 0 0 0 0` · `+4 +7 +7` · `+12 +12` · **UZ801-only** · `+19 +19 +19` |

**This reproduces Doc 206's staircase exactly** — `+0 → +4 → +7 → +12 → +19` — plus the UZ801-only statement
at L2535, from a completely independent instrument (the descriptor→code map) rather than from the
descriptor-string reading Doc 206 used. UZ801's `cmcc.c` is the newer, larger revision; the *statements* are
the same statements.

**Function level.** Attributing every literal log site (exact + table-adjacency) to a file, the two `cmcc.c`
modules line up:

| UZ801 function | sites | HMU05 counterpart | sites |
| :-- | --: | :-- | --: |
| `FUN_c0690994` | 2 | `FUN_c066db64` | 2 |
| `FUN_c0690a00` | 4 | `FUN_c066dbd0` | 4 |
| `FUN_c0690e40` | 4 | `FUN_c066e010` | 4 |
| `FUN_c0691778` | 2 | `FUN_c066e92c` | 2 |
| `FUN_c06917f0` | 28 | `FUN_c066e9a4` | 28 |
| `FUN_c0691d9c` | 5 | `FUN_c066ef48` | 5 |
| `FUN_c0691e54` | 4 | `FUN_c066f000` | 4 |
| `FUN_c0691f2c` | 17 | `FUN_c066f0d8` | 17 |
| `FUN_c0692508` | 1 | `FUN_c066f6b4` | 1 |
| `FUN_c0692794` | 5 | `FUN_c066f91c` | 5 |
| `FUN_c069282c` | 4 | `FUN_c066f9b4` | 4 |
| **`FUN_c06928a8`** | **13** | **`FUN_c066fa30`** | **13** |
| `FUN_c0692544` | 4 | (`FUN_c066f6f0`, attributed elsewhere) | — |
| **total** | **90** | | **89** |

The 12 pairs' site counts are identical. Cross-checked on the **exact** subset alone (which needs no
adjacency inference) the same 1:1 pairing holds for 7 pairs at counts `6 2 2 2 1 1 1` in both builds
(`FUN_c06917f0`↔`FUN_c066e9a4` = 6, `FUN_c06928a8`↔`FUN_c066fa30` = 2, …), with the 8th pair resting on one
adjacency-attributed site. **The correspondence is therefore not an artefact of the file-attribution
heuristic.**

### 5.2 The "no static caller" property is identical in both builds

Raw occurrence census (not a regex with a `(`-requirement — see §8.1):

```
UZ801 decompile, "FUN_c06928a8":
  1144714:/* ---- Function: FUN_c06928a8 @ c06928a8 ---- */
  1144716:void FUN_c06928a8(...)                       <-- definition only, nowhere else

HMU05 decompile, "FUN_c066fa30":
  1126992:/* ---- Function: FUN_c066fa30 @ c066fa30 ---- */
  1126994:void FUN_c066fa30(...)                       <-- definition only, nowhere else
```

Both are called by **nothing**. Each calls its own `cmcc.c` sibling exactly once
(`FUN_c06917f0(iVar2,0xffffffff)` / `FUN_c066e9a4(iVar2,0xffffffff)`), and that sibling has **no other caller**
in either image. **⇒ Doc 206 §9 item 2 is CLOSED, negative: `cmcc.c`'s reachability cannot be the difference,
because it is the same in the two builds.**

---

## 6. ★★ `FUN_c06928a8` ≡ stock `FUN_c066fa30` — GROUP 11 is inert by construction

Side-by-side, the two functions are the **same function**:

| element | UZ801 `FUN_c06928a8` | HMU05 `FUN_c066fa30` |
| :-- | :-- | :-- |
| guard | `if ((param_3 == 0) \|\| (!(param_4 == 0)))` | identical |
| alloc | `FUN_c0692508(0xff)` | `FUN_c066f6b4(0xff)` |
| field stores | `+0x150f`, `+0x29c`, `+0xa68` | identical offsets |
| service-handle call | `thunk_EXT_FUN_d001d2fc(uVar6, local_30)` | `thunk_EXT_FUN_d001cc5c(uVar6, local_30)` |
| **registration A** | `…, 0x456, FUN_c0691d9c, iVar2)` | `…, 0x456, FUN_c066ef48, iVar2)` |
| **registration B** | `…, 0x422, FUN_c0692794, iVar2)` | `…, 0x422, FUN_c066f91c, iVar2)` |
| third registration | `…, FUN_c069282c, iVar2)` | `…, FUN_c066f9b4, iVar2)` |
| GP slot | `unaff_GP + 0x4a50` | `unaff_GP + 0x49a0` |
| tail call | `FUN_c06917f0(iVar2, 0xffffffff)` | `FUN_c066e9a4(iVar2, 0xffffffff)` |
| cleanup | `FUN_c0690994(iVar2)` | `FUN_c066db64(iVar2)` |

The only differences are layout (descriptor VAs, log-call helpers `FUN_c091d9xx` vs `FUN_c08f15xx`, external
thunk IDs, the GP slot) and the **revision shift** already known from §5.1 — including the tracing line
argument `0xc26` (3110) vs `0xc13` (3091), a difference of exactly **+19**.

**Consequence.** GROUP 11 patches VA `0xc0691950`, which lies inside `FUN_c06917f0` — the function
`FUN_c06928a8` tail-calls. Doc 206 §4 already showed that VA is `cmcc.c`, not `cmmsc_auto.c`. §6 now shows
the enclosing chain is **behaviourally identical to stock's**. A patch that makes an already-stock-identical
function do something different cannot move the modem toward stock behaviour. **The measured inertness of
GROUP 11 in v14 and v19 is therefore not a coincidence and not an artefact — it is what the code predicts.**

---

## 7. What is now CLOSED, and what remains OPEN

### 7.1 CLOSED

* **The `cmcc.c` axis** (§5, §6). Reachability, function inventory and the service-available chain are all
  identical between builds. Do not spend another deployment on GROUP 11 or on `FUN_c06928a8`/`FUN_c06917f0`.
* **"`FUN_c06928a8` is dead, therefore UZ801 skips the CM path"** — the stock counterpart is equally dead.
* **The `desc_table.py entries` model as a count.** It keeps ~8 % of the table.
* **`re_index.py --callers` as a caller census.** It matches only `SYM(` and is blind to function-pointer
  arguments (§8.1).

### 7.2 OPEN — and now precisely bounded

**Doc 206 §9 item 1 (locate the real `cmmsc_auto.c` entry point) remains open, and the reason is now
measured rather than suspected: the `cmmsc_auto.c` descriptors are referenced by no literal and no `immext`
in *either* build.** The descriptor VAs are recorded here so the next attempt does not have to re-derive
them:

| statement | HMU05 desc | HMU05 line | UZ801 desc | UZ801 line |
| :-- | :-- | --: | :-- | --: |
| `cmmsc_auto.c:=CM= MSC: HICPS: cm_mode_pref` | `0xc155f740` | 1872 | `0xc165fd70` | 1872 |
| `cmmsc_auto.c:=CM= CMMSC_AUTO: updating op_mode` | `0xc155f7d0` | 2651 | `0xc165fe00` | 2651 |
| `cmmsc_auto.c:=CM= CMMSC_AUTO:is_ue_mode_csfb` | `0xc155f7f8` | 2849 | `0xc165fe28` | 2858 |
| `cmmsc_auto.c:=CM= MSC_AUTO: 0x%x & 0x%x = 0x%x` | `0xc155f8b8` | 3241 | `0xc165ff00` | 3250 |

The line shifts (+0 / +0 / +9 / +9) confirm Doc 205's "UZ801 `cmmsc_auto.c` is a newer revision (+9)".

Also measured, for completeness: the `immext` bases that reach the descriptor table from the CM cluster are
**all in the `cmcc.c` region only** (`0xc165c140 … 0xc165c800`, referenced from code at `0xc069xxxx`). There
are **zero** `immext` bases anywhere in `0xc165f700…0xc165ff40` (UZ801) or `0xc155f700…0xc155f900` (stock).
So the `cmmsc_auto.c`/`mmocmmgsdi.c`/`mmoc.c` log calls use a form that never materialises the descriptor
address — in both images.

---

## 8. Method corrections

### 8.1 `re_index.py --callers` is blind to function-pointer arguments

`re_index.py`'s call regex requires `SYM(`. A handler registered as an **argument** —
`thunk_EXT_FUN_d0587538(…, 0x456, FUN_c0691d9c, iVar2)` — is not matched. It reported "no callers" for
`FUN_c0691d9c` even though that function is registered twice. The conclusion drawn for `FUN_c06928a8`
happened to survive a raw `grep`, but the *method* was unsound. **For any future caller census on this
platform, use a raw symbol grep, not `re_index.py --callers`.**

### 8.2 `desc.py` (already known wrong) and `xmap.py` (mislabelled)

`desc.py`'s `{u32 ptr_or_hash; u16 file_id; u16 line}` model was retracted in Doc 206. `xmap.py`'s
`{ u16 file_id; u16 msg_id; u32 hash }` is the same family of error — the real layout is `{packed, hash}`.
`xmap.py`'s *purpose* (cross-build descriptor matching) is sound and now has measured support (§3.3).

---

## 9. Named next step

**Stop attacking the CM layer statically. The two-arm static comparison is exhausted for `cmcc.c`, and
`cmmsc_auto.c` is not statically reachable by the descriptor route in either build.**

In order of cost:

1. **★ Take the `cmmsc_auto.c` question to the runtime.** Doc 202 §6.1 established that stock's `u=1, tsk=cm`
   CM-command window contains a `cmmsc_auto.c` burst (idx 58/59/60) and UZ801's does not. That is a
   *runtime* difference on a **known** command. Instrument the **CM command dispatcher** so that the
   `u=1, tsk=cm` command's handler chain is observable — i.e. log the handler entry/exit for that command —
   and compare the two builds' handler *sequence*. The dispatcher's message-ID switch is already extracted
   (`scratch/dispatcher_c067cd2c.txt`, `scratch/dispatcher_asm.txt`); the missing piece is which case the
   `u=1` command maps to, which is a runtime question, not a static one.
2. **★ Use the descriptor hashes as the cross-build statement key.** §3.3 measured 89.6 % overlap. For each
   statement stock emits during boot but UZ801 does not (the diff is already computed — Doc 202 §6.2), find
   its hash in UZ801's table, then look for **any** reference to that descriptor VA from a *computed* base
   (scan for `immext` immediates in the same 64-byte block, not only the exact VA). This is the one static
   route left that has not been tried with the corrected model.
3. **Do not** re-open: the `cmcc.c`/GROUP 11 axis (§5, §6); the `0x456`/`0x422` cross-reference (`206 §5`);
   the `u=1` race (`204`); the `evt=0` patch (`204`); the `0x1000001` whitelist (`201`); the
   `cm_state+0x39e8` byte (`200`/`201`). **Also do not** cite `FUN_c06928a8` as `cmmsc_auto`, do not cite
   `desc.py`, and do not take a count from a wide `desc_table.py entries` call.

---

## 10. Evidence inventory

| Artifact | md5 / identity | Role |
| :-- | :-- | :-- |
| `scratch/logsite.py` | new | the descriptor → `file:line` instrument (§4) |
| `scratch/immbase.py` | new | `immext` immediate histogram (§7.2) |
| `scratch/va2func.py` | new | VA → enclosing function; 81 800 UZ801 functions indexed |
| `scratch/descref.py` | new | the earlier join attempt; carries the `off2va is None` fix (§3.2) |
| `scratch/uz801_desc.tsv` | 11 168 entries | UZ801 descriptor table, resolved-strptr subset |
| `scratch/uz801_logsites.txt` | 69 775 sites | UZ801 descriptor→code map |
| `scratch/hmu05_logsites.txt` | 68 186 sites | stock descriptor→code map |
| `scratch/uz801_fw/modem.elf` | 50 912 492 B | UZ801-21 rejoined image |
| `GitIgnore/MelbonWhiteStock_Dump/modem_extracted/image/modem.elf` | 50 049 024 B | stock HMU05 image |
| `Docs/Modem Stability/Modem RE/{uz801,hmu05}/modem_full_decompiled.c` | 110 552 970 / 105 121 709 B | Ghidra exports (both arms) |

---

## 11. Reproduce

```sh
cd scratch

# 1. descriptor table + resolved anchors (per build)
python3 logsite.py anchors uz801_fw/modem.elf | head
python3 logsite.py anchors ../GitIgnore/MelbonWhiteStock_Dump/modem_extracted/image/modem.elf | head

# 2. the descriptor -> file:line map (per build)
python3 logsite.py refs "../Docs/Modem Stability/Modem RE/uz801/modem_full_decompiled.c" \
    uz801_fw/modem.elf > uz801_logsites.txt
python3 logsite.py refs "../Docs/Modem Stability/Modem RE/hmu05/modem_full_decompiled.c" \
    ../GitIgnore/MelbonWhiteStock_Dump/modem_extracted/image/modem.elf > hmu05_logsites.txt

# 3. the cmcc.c arms
grep " cmcc.c:" uz801_logsites.txt | awk '{print $2}' | sort | uniq -c | sort -rn
grep " cmcc.c:" hmu05_logsites.txt | awk '{print $2}' | sort | uniq -c | sort -rn

# 4. the "no static caller" symmetry (raw grep, NOT re_index.py --callers)
grep -a -n "FUN_c06928a8" "Docs/Modem Stability/Modem RE/uz801/modem_full_decompiled.c"
grep -a -n "FUN_c066fa30" "Docs/Modem Stability/Modem RE/hmu05/modem_full_decompiled.c"

# 5. the descriptor-table immext bases (all in the cmcc.c region; none for cmmsc_auto.c)
python3 immbase.py uz801_fw/modem.elf 0xc165c000 0xc1668000 --top 200
python3 immbase.py uz801_fw/modem.elf 0xc165fd40 0xc165ff40 --top 40      # 0
python3 immbase.py ../GitIgnore/MelbonWhiteStock_Dump/modem_extracted/image/modem.elf \
    0xc155f700 0xc155f900 --top 40                                        # 0
```
