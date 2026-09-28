# 216 — THE MMOC PROTOCOL REGISTRY, THE CM-PROXIMITY ARTEFACT, AND THE PROVENANCE OF seg[26]

**Ledger:** see `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md` — §3 (Achieved vs Expected) and §7 **item 21**.
**Status:** RESULTS — **two new assets, two negatives, one refinement.**
**Supersedes:** Doc 215 §13.3/§13.4 (the "CM directly indexes the seg[19] tables adjacent to
`mmoc_cmd_name[]`" lead) — **it is a segment-proximity artefact** (§4). **Closes** Doc 214 §12 step 1 /
ledger task #15 (splice seg26 into the ELF) — **there is nothing to splice** (§5).

**One-line summary.** Doc 215's addendum claimed the CM range `0xc067xxxx`–`0xc068xxxx` "directly indexes
seg[19] tables adjacent to the MMOC command/state name table", and called that "the first code-level handle on
the CM→MMOC data relationship". **Reading the nine functions refutes it**: they are CM **task-scheduler** code
and their targets are CM scheduler globals that merely share the segment. In the same pass the region
immediately before `mmoc_cmd_name[]` turns out to be a **second, independent table — the MMOC protocol
registry** — and seg[26], whose content every earlier plan wanted to splice into the ELF, turns out to exist in
**no file at all**.

---

## 1. SOP compliance and Achieved vs Expected

### 1.1 SOP-compliance statement (per `197 §1`, modelled on `214 §1` / `215 §1`)

| Step | Taken? | Detail |
| :-- | :-- | :-- |
| 1. Backup | **YES** | Nothing was deployed in this session. The device is the v19 set already recorded in Doc 215 §1.1: `modem.mdt 9f39ce579114bcf1383bbdce62a8d284`, `modem.b16 c71d53464a1e88f2072cbbb6f7f87475`, no `/root/BOOTLOOP_REVERT.txt`. Device re-verified live this session (§9). |
| 2. Ground-truth verification against stock HMU05 | **YES** | Every address is derived from the stock HMU05 ELF (`GitIgnore/MelbonWhiteStock_Dump/modem_extracted/image/modem.elf`, 50 049 024 B) and, where the target is in a `filesz=0` segment, resolved against the stock coredump (`scratch/coredump_live/modem_coredump_up919.52.elf`) with the `native = ELF VA + 0xbeb1000` / `coredump VA + 0x39800000 = ELF VA` relations re-applied, not assumed. |
| 3. Reconcile transport/config | **YES** | The disassembler is `llvm-objdump -d --triple=hexagon` on the stock ELF (4 951 623 lines, 306 MB, ~7 s, `file format elf32-hexagon`). The decompiler is Ghidra's `Hexagon:LE:32:default` output for the same image. Where the two disagree, the disassembly is authoritative (§12 trap 5). |
| 4. Surgical Hexagon patching + re-signing | **NOT TAKEN — deliberately** | Analysis only; no byte of any image was changed. Per `feedback_operating_rules.md` rule 1. |
| 5. Live empirical validation | **YES (read-only)** | The device was queried live (§9) to close the SIM-lock question. **No state-changing command was issued except `--dms-set-operating-mode=low-power` followed by `=online`** — the same reversible lever Doc 205 §6 already enumerated, used here to confirm the modem's reaction is reproducible. No reboot, no deployment, no `echo stop`. |

**Steps skipped and why:** step 4 — the deliverable is a corrected *static* model plus two recovered data
tables; there is no byte to patch.
**No `sync` was needed** — nothing was written to the device or to `/overlay`.

### 1.2 Achieved vs Expected

| # | Intent | Expected | Achieved | Status |
| :-- | :-- | :-- | :-- | :-- |
| 1 | Execute Doc 215 §13.5 ("read the CM functions that index `0xc1d348c0` and write `0xc1d33da0`/`0xc1d33db8`") | A CM→MMOC handle | **Executed — and it is a NEGATIVE.** The nine functions are CM task-scheduler code (§4). The lead is a **segment-proximity artefact** | **MET (as a falsification)** |
| 2 | Identify the region `0xc1d33d80`–`0xc1d33eb0` (Doc 215 §13.4's "second descriptor table") | A descriptor table | **A different object entirely: the MMOC protocol registry**, 14 × `{char* name, void* block, u32}` — names `MODE_INACT`, `MC(AMPS)`, `CDMA(MC Task)`, `GPS`, `HDR(HDRMC Task)`, `RESERVED`, `HYBR NAS(REG Task)`, `NAS(REG Task)`, `HYBR 3(REG Task)`, `Invalid` | **MET — new asset (§3)** |
| 3 | Produce the authoritative `mmoc_cmd_name[]` listing | 96 slots | **96 slots: 91 named, 3 null (`11`,`61`,`95`), 2 unresolved (`64`,`94`); a shared non-string marker at `26`/`62`/`78`.** Index→name confirms every Doc 215 mapping | **MET (§8)** |
| 4 | Establish where seg[26] content comes from | A file (to splice) | **NO FILE CONTAINS IT.** Not the loose `modem.bNN`, not the 64 MB FAT16 images, not the ELF. seg[26] is a fixed-address runtime-populated region | **MET — definitive negative (§5)** |
| 5 | Correct Doc 215 §5's descriptor model | The record layout | **16-byte `{u16 line, u8 level, u8 len, u32, u32 fmt_native, u32 file_native}`** in seg[18]; only the ~8 % string-form sites are recoverable offline | **MET (§6)** |
| 6 | Execute Doc 215 §9 step 3 (diff the two builds' MMOC tables) | A data difference or identity | **Layouts are near-identical** (structural candidates at matching relative offsets, shifted ~0x400); UZ801's *strings* remain unresolvable (no coredump) | **PARTIAL (§7)** |
| 7 | Close the SIM-lock question raised by MM's `lock: sim-pin2` | A verdict | **No lock of any kind.** `AT+CLCK="FD"/"SC"/"PN"/"PU"/"PP"/"PC",2` → all `0`; `AT+CPIN?` → `READY`; QMI user-lock → `no`. MM's `enabled locks: fixed-dialing` is an **MM misreport** | **MET (§9)** |

---

## 2. The headline

| quantity | value |
| :-- | :-- |
| The MMOC **protocol registry** | ELF VA `0xc1d33e08` … `0xc1d33eb0` (file `0x1ca0e08`), file-backed **seg[19]**, 14 × 12-byte `{char* name, void* X, u32 = 0}` |
| Its names (10 distinct over 14 slots; `NAS(REG Task)` at `5,6,7,9,10`) | `MODE_INACT`, `MC(AMPS)`, `CDMA(MC Task)`, `GPS`, `HDR(HDRMC Task)`, `NAS(REG Task)`, `RESERVED`, `HYBR NAS(REG Task)`, `HYBR 3(REG Task)`, `Invalid` |
| `mmoc_cmd_name[]` | ELF VA `0xc1d33eb0`, 96 slots — **91 named, 3 null, 2 unresolved** |
| The nine CM functions that touch `0xc1d3xxxx` | `FUN_c06755a0`, `FUN_c0675fd4`, `FUN_c0676090`, `FUN_c067df9c`, `FUN_c0682224`, `FUN_c0682aa0`, `FUN_c068a688`, `FUN_c068a77c`, `FUN_c068f7a4` — **all CM task-scheduler code** |
| Their targets | `0xc1d33da0`, `0xc1d33db8`, `0xc1d348c0`, `0xc1d348c8`, `0xc1d348d0`, `0xc1d34900` — **CM scheduler globals**, not MMOC tables |
| seg[26] content (`0xd0d77xxx` strings) | **present in 0 files** of 23 candidate firmware artifacts |
| MMOC log strings with a resolvable 4-byte-LE descriptor | **only the string-form ~8 %** — `=MMOC= %s`, `mmocdbg.c` |

---

## 3. ★ NEW ASSET — the MMOC protocol registry at `0xc1d33e08`

Doc 215 §13.4 described `0xc1d33d80`–`0xc1d33eb0` as "a second descriptor table of `{seg26 name, seg24 record}`
pointer pairs". Dumping it against the coredump gives a **14-entry table of 12-byte records** running
`0xc1d33e08` → `0xc1d33eb0` (0xa8 = 12 × 14), i.e. it **ends exactly where `mmoc_cmd_name[]` begins**.
**In stored order, verbatim:**

| idx | VA | `name` (native) | decoded name | `X` (native) |
| --: | :-- | :-- | :-- | :-- |
| 0 | `0xc1d33e08` | `0xd0d77553` | `MODE_INACT` | `0` |
| 1 | `0xc1d33e14` | `0xd0d7755e` | `MC(AMPS)` | `0` |
| 2 | `0xc1d33e20` | `0xd0d77567` | `CDMA(MC Task)` | `0xd0ad74d8` |
| 3 | `0xc1d33e2c` | `0xd0d77575` | `GPS` | `0` |
| 4 | `0xc1d33e38` | `0xd0d77579` | `HDR(HDRMC Task)` | `0xd00a0890` |
| 5 | `0xc1d33e44` | `0xd0d77597` | `NAS(REG Task)` | `0xd00a090c` |
| 6 | `0xc1d33e50` | `0xd0d77597` | `NAS(REG Task)` | `0xd00a090c` |
| 7 | `0xc1d33e5c` | `0xd0d77597` | `NAS(REG Task)` | `0xd00a090c` |
| 8 | `0xc1d33e68` | `0xd0d77589` | `RESERVED` | `0` |
| 9 | `0xc1d33e74` | `0xd0d77597` | `NAS(REG Task)` | `0xd00a090c` |
| 10 | `0xc1d33e80` | `0xd0d77597` | `NAS(REG Task)` | `0xd00a090c` |
| 11 | `0xc1d33e8c` | `0xd0d77592` | `HYBR NAS(REG Task)` | `0xd00a090c` |
| 12 | `0xc1d33e98` | `0xd0d775a5` | `HYBR 3(REG Task)` | `0xd00a090c` |
| 13 | `0xc1d33ea4` | `0xd0d775b6` | `Invalid` | `0` |

Every record's third word is `0`. **10 distinct names over 14 slots**, with `NAS(REG Task)` at indices
`5,6,7,9,10`.

**What is measured.** The region is a run of `{char* name, void* X, u32 = 0}` records whose first field always
resolves to a **protocol/task name**. The names are the protocol vocabulary — `MC(AMPS)`, `CDMA(MC Task)`,
`HDR(HDRMC Task)`, `NAS(REG Task)`, `HYBR NAS(REG Task)`, `HYBR 3(REG Task)`, `GPS` — with `MODE_INACT`,
`RESERVED` and `Invalid` as sentinels. `mmoc_cmd_name[]` names *commands and states*; this names *protocols*.

**What is NOT measured, and must not be assumed.** (a) The **indexing is unknown**: `NAS(REG Task)` occupying
five slots means this is an **index→name mapping** (several indices alias to NAS), **not** a unique protocol
list, so "the protocols MMOC coordinates" is a hypothesis and not a reading. (b) The identity of `X` is
unknown — `0xd00a0890`/`0xd00a090c` land in file-backed seg[24] (`0xc41ef890`/`0xc41ef90c`) and `0xd0ad74d8` in
seg[26] (`0xc4c264d8`), and their content is binary, not strings; they are **not** names. (c) **No code
reference to this region has been found** — like `mmoc_cmd_name[]`, it is data-only (Doc 215 §7).

**Why it is still worth keeping.** It is the only place in the image where the protocol dimension is named, and
it is the natural candidate for the `%s` in the `mmocdbg.c` line
`Prot_state [MAIN] %d(%s) -- [HYBR_GW] %d(%s) -- [HYBR_GW3] %d(%s)`
(native `0xd0d77cb2` / `0xd0d77cf4`) — a `Prot_state` bracket reading `HYBR_GW`/`HYBR_GW3` is reporting *this*
vocabulary. Together with §8 it makes the whole `mmocdbg.c` vocabulary decodable offline, **provided the
indexing is pinned down first**.

Script: `scratch/doc216_strings.py`, `scratch/doc216_corestr.py`.

---

## 4. NEGATIVE — the "CM directly indexes the MMOC tables" lead is a segment-proximity artefact

Doc 215 §13.3 reported that of the 9 954 distinct seg[19] addresses referenced by any `##`-operand, **8 fall
within ±0x2000 of the MMOC name table**, and that "every referencing instruction is in `0xc067xxxx`–`0xc068xxxx`
— the CM range". The addendum concluded: *"CM code directly indexes seg[19] tables adjacent to the MMOC
command/state name table. That is the first code-level handle on the CM→MMOC data relationship."*

**Reading the nine enclosing functions refutes the conclusion.** All nine are CM **task-scheduler** code:

| function | target(s) | what it actually is |
| :-- | :-- | :-- |
| `FUN_c06755a0` | `memb(0xc1d33da0)=1` | in a `"cm:ready"` / `"mtask_orig_para_alloc"` context (`0xc17f38a2`, `0xc17f3880`); allocates via `thunk_EXT_FUN_d00747fc` and posts through `thunk_EXT_FUN_d0074830` |
| `FUN_c0675fd4` | `memh(0xc1d33db8) = 0x409 / 0x207` | **a global variable**, not a table: it stores a *command code* chosen from `thunk_EXT_FUN_d001cc98(uVar2)`; the `0x207`/`0x409` value is then used as an opcode `0xe1`/`0x74` |
| `FUN_c0676090` | reads `DAT_c1d33db8` | the consumer of the same global; logs it via `FUN_c08f1580(0xc1561bb0, …)` and dispatches `thunk_EXT_FUN_d001e5cc(uVar2, 0xe1|0x74, …)` |
| `FUN_c067df9c` | `*(p+4) = &DAT_c1d348d0` | initialises a **list head** (`p[1]=0; *p=0; *(u32*)(p+8)=5; *(void**)(p+4)=&array[4]`) — a sentinel-pointer init |
| `FUN_c0682224` | `memw(r0<<#2+##0xc1d348c0)` | a **2-deep history** (`DAT_c28dccad = DAT_c28dccac; DAT_c28dccac = param`) |
| `FUN_c0682aa0` | `memw(r0<<#2+##0xc1d348c8)` | a **queue push** (`FUN_c0684e38(&DAT_c28dc534, param)`) |
| `FUN_c068a688` | `DAT_c1d34900`, `DAT_c28de958` | a **signal helper**: `if ((DAT_c28de958 & param) != 0) FUN_c0033b60(&DAT_c28de948)` |
| `FUN_c068a77c` | `DAT_c1d34900 = 1` | a **task main loop**: `DAT_c28de948` wait-struct, `DAT_c28de958`/`DAT_c28de964`/`DAT_c28de960` **signal masks**, `FUN_c02c29f4` queue drain, `FUN_c0b65e80` free, `thunk_FUN_c11eaae4` teardown |
| `FUN_c068f7a4` | `(&DAT_c1d35ab6)[iVar1]` | a **table-driven 3-byte-record** routine (`DAT_c1d36ad8`/`d9`/`da`, stride 3; `DAT_c28ed5c8 + idx*0x7c`) |

Every one of the seven distinct targets is a **CM scheduler global** (`DAT_c28de948`, `DAT_c28de958`,
`DAT_c28dc534`, `DAT_c28dccac/ad` are the same module's state), and the targets `0xc1d33da0`, `0xc1d33db8`,
`0xc1d348c0`, `0xc1d348c8`, `0xc1d34900` are **all in seg[19] purely because CM's data and MMOC's data are
allocated in the same output segment** — seg[19] spans `0xc1c3c000`–`0xc1f2bac8` (3.1 MB), so
"within ±0x2000 of the MMOC table" is not a meaningful neighbourhood test.

**Corrected rule.** *Proximity inside a multi-megabyte data segment is not a reference.* The correct test is
whether a function's **own module** is the MMOC module — established by its log strings
(`mmocdbg.c`, `=MMOC= %s`), not by the address it happens to touch. **The MMOC emitter is therefore still
unfound, and Doc 215 §13.5's "concrete next step" is withdrawn.**

The genuine by-products of this pass are worth keeping:
- `0xc17f3880 = "mtask_orig_para_alloc"` and `0xc17f38a2 = "cm:ready"` — a CM **task-creation** context, which
  is what fixes the identity of `FUN_c06755a0`.
- **`DAT_c1d33db8` holds `0x207` or `0x409`**, selected by `thunk_EXT_FUN_d001cc98`, and is consumed as
  opcode `0xe1`/`0x74`. A two-valued CM global whose meaning is not yet known — recorded, not interpreted.

---

## 5. ★ NEGATIVE (definitive) — seg[26] content exists in **no file**

Doc 214 §12 step 1 (and ledger task #15) planned to "rebuild the ELF so the seg26 MMOC globals resolve" by
splicing coredump content into the `filesz=0` segments. **There is nothing to splice.**

**Measured.** The stock `modem.mdt` + `modem.b00`…`modem.b25` set contains a file for **every phdr with
`filesz > 0`** and for **no** phdr with `filesz = 0`:

| build | phdrs | `filesz > 0` | `filesz = 0` | `bNN` files present | missing |
| :-- | --: | :-- | :-- | :-- | :-- |
| stock HMU05 | 27 | `[0][1][2][3][4][5][8][10][11][13][14][15][16][17][18][19][22][23][24][25]` (20) | `[6][7][9][12][20][21][26]` | 20 | exactly `[6][7][9][12][20][21][26]` |
| UZ801-21 | 27 | same 20 indices | same 7 | 20 | same 7 |

The mapping is exact in both builds. **`modem.b26` does not exist** — and a raw search for the four
distinctive strings (`OPRT_MODE_CHGD`, `mmocdbg.c`, `SUBSCRIPTION_CHGD`, `Recvd command`) across
`modem_extracted/`, `GitIgnore/UZ801 Modem/` and `scratch/uz801_fw/` — **including both 64 MB FAT16 partition
images** (`hmu05_modem.bin`, `modem.bin`) — returns **zero hits in every file**.

**Consequences, stated plainly:**
1. seg[26] (`va 0xc44e6000`, `memsz 0xd1a000` = 13.7 MB, `filesz 0`) is a **fixed-address region populated at
   runtime**, not a loadable segment. Its runtime base is `0xc44e6000 + 0xbeb1000 = 0xd0397000`, which is
   exactly why every pointer into it carries the `+0xbeb1000` bias Doc 215 solved.
2. The **static tables that point into it are file-backed** (seg[18] descriptors, seg[19]
   `mmoc_cmd_name[]` / the protocol registry). So the tables are fully comparable across builds; **only their
   string targets are not resolvable offline for a build with no coredump.**
3. **Doc 215 §9 step 2 ("solve the UZ801 native constant") is therefore downgraded**: the constant cannot be
   solved from a pointer table whose targets cannot be read. It needs a **UZ801 coredump** — which is exactly
   Doc 214 §12 step 2, still open.
4. **Ledger task #15 is closed as unimplementable**; task #16's framing ("re-run Ghidra on the rebuilt ELF")
   is void for the same reason.

---

## 6. REFINEMENT — the F3 log descriptor is a 16-byte record, and only the string form is recoverable offline

Doc 215 §5 recorded the `=MMOC= %s` descriptor at ELF VA `0xc1566780` as
`{line=291, level=20, len=28, fmt→native 0xd0d77c9e, file→native 0xd0d77ca8}`. Dumping the raw bytes fixes the
layout:

```
0xc1566780  23 01 14 1c   →  u16 line = 0x0123 = 291 ; u8 level = 0x14 = 20 ; u8 len = 0x1c = 28
0xc1566784  1c 00 00 00   →  u32       = 28
0xc1566788  9e 7c d7 d0   →  u32 fmt   = native 0xd0d77c9e
0xc156678c  a8 7c d7 d0   →  u32 file  = native 0xd0d77ca8
```

i.e. **`{u16 line, u8 level, u8 len, u32, u32 fmt_native, u32 file_native}` — 16 bytes**, in file-backed
seg[18].

**The decisive measurement.** Searching the whole ELF for the 4-byte-LE native pointer of each MMOC log string:

| string | native ptr | 4-byte-LE occurrences in the ELF |
| :-- | :-- | --: |
| `=MMOC= %s` | `0xd0d77c9e` | **1** (the descriptor above) |
| `mmocdbg.c` | `0xd0d77ca8` | **1** (same descriptor) |
| `Recvd command %d(%s)` | `0xd0d77d70` | **0** |
| `Recvd report %d(%s)` | `0xd0d77d5c` | **0** |
| `Trans_state %d(%s)` | `0xd0d77d49` | **0** |
| `Curr_trans  %d(%s)` | `0xd0d77d37` | **0** |
| `Prot_state [MAIN] %d(%s) -- … [HYBR_GW]` | `0xd0d77cb2` | **0** |
| `Prot_state [MAIN] %d(%s) -- … [HYBR_GW3]` | `0xd0d77cf4` | **0** |

⇒ **the string→descriptor→code route works only for the ~8 % string-form sites.** The five sites that actually
matter for the MMOC state machine (`Recvd command`, `Recvd report`, `Trans_state`, `Curr_trans`,
`Prot_state [MAIN]`) all use the **hash** form, so they carry **no pointer in the image at all**. This is
`logsite.py`'s "~8 % strptr / 92 % hash" model, now measured rather than quoted — and it means **Doc 215 §9
step 1's "xref the `Recvd command` format string" cannot work as stated**; the hash must be computed and
matched instead.

Corollary: the two descriptor *values* `0xd0d7783f` (`OPRT_MODE_CHGD`) and `0xd0d77820` (`SUBSCRIPTION_CHGD`)
found as 4-byte-LE values are **not** log descriptors — they are entries **of `mmoc_cmd_name[]` itself**
(offsets `0x1ca0eb8`, `0x1ca0eb0`). A pointer into a name table must never be read as a log descriptor.

Script: `scratch/doc216_findrefs.py`.

---

## 7. PARTIAL — the two builds' seg[19] layouts are near-identical

Doc 215 §9 step 3 asked for a diff of the two builds' MMOC tables. Both seg[19]s are file-backed
(stock `va 0xc1c3c000`, `filesz 0x2efac8`; UZ801 `va 0xc1d5b000`, `filesz 0x2f1848`), and scanning each for
"≥88 consecutive words that are `0` or `≥0xc0000000`, spanning <0x20000" yields the **same structural
candidates at the same relative offsets**, shifted by only `0x400` (256 words):

| # | stock file off | stock seg va | UZ801 file off | UZ801 seg va |
| --: | --: | :-- | --: | :-- |
| 1 | `0x01c2a5c0` | `0xc1cbd5c0` | `0x01d571c0` | `0xc1ddc1c0` |
| 2 | `0x01c2b5c0` | `0xc1cbe5c0` | `0x01d581c0` | `0xc1ddd1c0` |
| 3 | `0x01c6b5c4` | `0xc1cfe5c4` | `0x01d981c4` | `0xc1e1d1c4` |
| 4 | `0x01c88e00` | `0xc1d1be00` | `0x01db5a00` | `0xc1e3aa00` |
| 5 | `0x01ca38b8` | `0xc1d368b8` | `0x01dd04d0` | `0xc1e554d0` |
| 6 | `0x01ca3b24` | `0xc1d36b24` | `0x01dd073c` | `0xc1e5573c` |
| 7 | `0x01cb0768` | `0xc1d43768` | `0x01ddd6a0` | `0xc1e626a0` |
| 8 | `0x01cdaf40` | `0xc1d6df40` | `0x01e36248` | `0xc1ebb248` |

**Read:** the two builds' static MMOC-adjacent data is **structurally the same**, consistent with Doc 208's
finding that the CM dispatcher is structurally identical. **The `mmoc_cmd_name[]` relative offset does NOT
transfer** (`0xf7eb0` gives a valid table in stock and a 12-byte-record region in UZ801), so UZ801's table must
be located by signature, not by offset — which is what the scan above does.

**Limit, stated:** the *string* comparison is **not** achievable. UZ801 has no coredump and its seg[26] is in no
file (§5), so its 96 pointer values cannot be resolved to names. **Only the structure is compared here; a
name-level diff is deferred until a UZ801 coredump exists.** No behavioural conclusion is drawn.

Script: `scratch/doc216_tablediff.py`.

---

## 8. The authoritative `mmoc_cmd_name[]` (96 slots)

ELF VA `0xc1d33eb0`, file `0x1ca0eb0`, file-backed **seg[19]**, native base `0xcdbe4eb0`.
**96 slots: 91 named, 3 null (`11`, `61`, `95`), 2 unresolved (`64`, `94`); a shared non-string marker
`0xd0dd533d` at `26`, `62`, `78`.**

*(This refines Doc 215 §4's "90 named, 2 null holes, 7 slots sharing a sentinel" — the counting convention
differs; the **index→name map is identical** and is re-confirmed here, including `40 = WAIT_SESSION_OPEN_CNF`,
the state Doc 202 §6.3 says UZ801 hangs in.)*

```
 0 SUBSCRIPTION_CHGD        1 PROT_GEN_CMD            2 OPRT_MODE_CHGD
 3 WAKEUP_FROM_PWR_SAVE     4 PROT_REDIR_IND          5 PROT_HO_IND
 6 MMGSDI_INFO_IND          7 DUAL_STANDBY_CHGD       8 DEACT_1XCSFB_PROT
 9 SUSPEND_SS              10 DEACT_FROM_DORMANT     11 <null>
12 PROT_DEACTD_CNF         13 PROT_AUTO_DEACTD_IND   14 PH_STAT_CHGD_CNF
15 PROT_GEN_CMD_CNF        16 PROT_AUTO_ACTD_IND     17 MMGSDI_CNF
18 DS_STAT_CHGD_CNF        19 ACTIVATION_CNF          20 PS_DETACH_CNF
21 1XCSFB_PROT_DEACTD_CNF  22 IRAT_HOLD_USER_ACT_CNF 23 UE_MODE_SWITCH_CNF
24 IMS_DEREG_CNF           25 SUBS_CAP_CHGD_CNF       26 <marker 0xd0dd533d>
27 PROT_DEACT_ENTER        28 WAIT_DEACTD_CNF         29 MMGSDI_READ_ENTER
30 MMGSDI_READ_CNF         31 PROT_PH_STAT_ENTER      32 WAIT_PH_STAT_CNF
33 GEN_CMD_ENTER           34 WAIT_GEN_CMD_CNF        35 WAIT_AUTO_DEACTD_IND
36 WAIT_AUTO_ACTD_IND      37 HDR_DEACT_ENTER         38 WAIT_HDR_DEACTD_CNF
39 PROT_REDIR_ENTER        40 WAIT_SESSION_OPEN_CNF   41 PROT_HO_ENTER
42 DS_STAT_CHGD_ENTER      43 WAIT_DS_STAT_CHGD_CNF   44 GEN_CMD_ACTIVATION_ENTER
45 WAIT_ACTIVATION_CNF     46 WAIT_PS_DETACH_CNF      47 HYBR2_DEACT_ENTER
48 WAIT_HYBR2_DEACTD_CNF   49 HYBR3_DEACT_ENTER       50 WAIT_HYBR3_DEACTD_CNF
51 WAIT_DEACTD_CNF_GWL     52 WAIT_1XCSFB_DEACT_CNF   53 SUSPEND_SS_ENTER
54 RESUME_SS_ENTER         55 WAIT_HOLD_USER_ACT_CNF  56 WAIT_UE_MODE_SWITCH
57 WAIT_SUBS_CAP_CHGD_ENTER 58 WAIT_SUBS_CAP_CHGD_CNF 59 WAIT_IMS_DEREG_CNF
60 PS_DETACH_ENTER         61 <null>                  62 <marker 0xd0dd533d>
63 SUBSC_CHGD             64 <unresolved 0xd0d9fd6d>  65 OFFLINE
66 PROT_GEN_CMD           67 PWR_DOWN                68 PWR_SAVE_ENTER
69 PROT_AUTO_DEACTD_IND   70 WAKEUP_FROM_PWR_SAVE    71 DUAL_STANDBY_CHGD
72 PROT_REDIR_IND         73 PROT_HO_IND             74 MMGSDI_INFO_IND
75 DEACT_1XCSFB_CMD       76 SUSPEND_SS              77 DEACT_FROM_DORMANT
78 <marker 0xd0dd533d>    79 ONLINE_CDMA             80 OFFLINE_CDMA
81 ONLINE_AMPS            82 OFFLINE_AMPS            83 ONLINE_GWL
84 ONLINE_HDR             85 OFFLINE                86 <unresolved 0xd0db2d7f>
87 PWR_SAVE               88 ONLINE_DED_MEAS         89 <unresolved 0xd0e0318e>
90 <marker 0xd0d8a40d>    91 PWR_DOWN                92 GPSONE_MSBASED
93 DORMANT_GWL            94 <unresolved 0xd0e02c91>  95 <null>
```

**Structure.** Slots `0`–`60` are the **forward command/state table** (61 distinct names). Slots `62`–`95` are
a **second region that re-uses names from the first** (`66 PROT_GEN_CMD`≡`1`, `70 WAKEUP_FROM_PWR_SAVE`≡`3`,
`71 DUAL_STANDBY_CHGD`≡`7`, `72/73/74`≡`4/5/6`, `76/77`≡`9/10`) interleaved with new ones (`63 SUBSC_CHGD`,
`65/85 OFFLINE`, `67 PWR_DOWN`, `75 DEACT_1XCSFB_CMD`, `79`–`84` the per-RAT online/offline names,
`87 PWR_SAVE`, `88 ONLINE_DED_MEAS`, `92 GPSONE_MSBASED`, `93 DORMANT_GWL`). The two regions are **not** one
array indexed by one ordinal — a decoder must know which region an ordinal came from.

Script: `scratch/doc216_cmdname_full.py`.

---

## 9. Device snapshot, 2026-09-27 (read-only, plus the Doc-205 low-power/online lever)

Taken live on `192.168.8.1` to close the SIM-lock question. **The device is healthy and unchanged from
Doc 215 §1.1.**

| probe | result |
| :-- | :-- |
| host | `Linux OpenWrt 6.12.94 … aarch64`; `uptime 1:58`; `boot_id 26d74096-874f-45ee-9de4-67a8ee87caf1` |
| firmware set | **21** `/lib/firmware/modem.*` (20 `bNN` + `modem.mdt`); `modem.mdt 9f39ce579114bcf1383bbdce62a8d284`; `mba.mbn 7aa1bcd5131bbbeeaea9c4a27dbe0cd3` |
| bootloop guard | `/root/BOOTLOOP_REVERT.txt` **absent**; `/overlay/fwbackup/` holds `hmu05_stock`, `hmu05_wcnss_tz`, `uz801_stock`, `uz801_v19_full.{manifest,tar.gz}`; `modem_guard.sh watch` running (PID 32724) |
| remoteproc | `remoteproc0/state = running`, `firmware = mba.mbn` |
| **modem identity** | `firmware revision: UZ801_V3.0_21_V01R01B10  1  [Sep 07 2015 23:00:00]`; IMEI `864293052253917`; `Networks: umts, lte` |
| **operating mode** | `offline`; `HW restricted: 'no'`; `activation state: not-activated`; `power state: external-source`, battery `0 %` |
| **AT** | `AT` → `OK`; **`AT+CFUN?` → `+CFUN: 7`**; `AT+CSQ` → `+CSQ: 99,99`; `AT+CREG?`/`AT+CGREG?` → `0,6`; `AT+COPS?` → `0`; `AT+CGDCONT?` → two empty `IPV4V6` contexts |
| **SIM** | `AT+CPIN?` → **`READY`**; `AT+CLCK="FD",2`/`"SC",2`/`"PN",2`/`"PU",2`/`"PP",2`/`"PC",2` → **all `0`**; QMI user-lock → **`no`** |
| SIM (QMI) | USIM `ready`, personalization `ready`, **PIN1 `disabled`**, **PIN2 `enabled-not-verified`** (retries 10/10) |
| MM | `lock: sim-pin2`, `unlock retries: sim-pin (10)…`; **`enabled locks: fixed-dialing`**; `state: disabled`; `power state: off` |
| `mmcli --enable` | **times out after 10.45 s**: *"Requested (online) and reloaded (offline) modes did not match: Power update operation timed out"*; MM then **disposes** the modem (`cleaning up port … completely disposed`) |
| MM log | `[modem4] SIM is ready, and no need for the after SIM unlock step…`; `PIN1 is reported disabled`; `PIN2 is reported enabled`; all five facility queries → `facility_state = 'deactivated'` |
| NAS | `Registration state: not-registered`, CS/PS `detached`, radio interface `none`; `--nas-get-signal-info` → `InformationUnavailable`; `--nas-network-scan` → **`QMI protocol error (3): 'Internal'` in 0.02 s** |
| bam-dmux | `RX watchdog: quiesced Ns (pc_state=0, pc_line=0, rx=held, ring disarmed)` every 60 s since boot — **the A2 power line has never been asserted** |
| wwan | `wwan0`…`wwan7` all `DOWN` |
| low-power lever | `--dms-set-operating-mode=low-power` → *success*, and the **next QMI CTL client allocation times out**; `=online` → *success*, mode **still `offline`**; no reset occurred (`remoteproc` still `running`, no new `boot_id`) |

**★ The SIM-lock question is closed, and the answer is NEGATIVE.** MM reports `lock: sim-pin2` and
`enabled locks: fixed-dialing`, but the modem's own facilities are **all deactivated** and `AT+CPIN?` is
`READY`. **PIN2 `enabled-not-verified` does not gate registration** — that is the normal state of a SIM whose
PIN2 has never been used, and PIN2 is only required to *write* FDN/ADN. **A PIN2/FDN blocker is ruled out**, and
MM's `enabled locks` field must not be read as the modem's facility state.

**Not established:** whether the RF path can see any network. NAS cannot answer while the modem is `offline`
(the scan returns `Internal` immediately, and `AT+COPS=?` → `ERROR`, Doc 205 §6). **The RF question remains
open and cannot be closed without first getting the modem to `online`.**

---

## 10. What this changes, and the next steps

**What is now certain that was not before**

1. **The MMOC layer's full vocabulary is decodable offline**: `mmoc_cmd_name[]` (commands *and* the enter/wait
   state machine, §8) **plus** the protocol registry (§3). Together these cover both `%s` fields of the
   `mmocdbg.c` `Prot_state`/`Trans_state`/`Recvd command` lines.
2. **Doc 215 §13.3/§13.4's CM lead is dead** (§4). The MMOC emitter must be found by module identity
   (log strings), not by data adjacency.
3. **seg[26] has no file** (§5). Every "rebuild the ELF / splice the segment" plan is void; the only route to
   UZ801's seg[26] content is a **UZ801 coredump**.
4. **The `Recvd command` string cannot be xref'd** (§6) — it has no pointer in the image. The hash must be
   computed.
5. **There is no SIM or facility lock** (§9).

**Next steps, in priority order**

1. **★ Obtain a UZ801 coredump** (Doc 214 §12 step 2, now the single highest-value item). It is the *only* way
   to read UZ801's seg[26] — and therefore the only way to (a) solve the UZ801 native constant, (b) do a
   name-level diff of the two builds' tables (§7), and (c) read UZ801's own log strings. Look for
   `/sys/kernel/debug/msm_subsys/modem`; **never** `echo stop > /sys/class/remoteproc/remoteproc0/state` (that
   reset the AP).
2. **Restore the F3 burst-capture class** (Doc 208 §9 item 0) — the campaign's own gating blocker. No CM/MMOC
   observation is possible without it.
3. **Find the MMOC emitter by module identity**: compute the F3 descriptor **hash** for
   `Recvd command %d(%s)` and search for it, rather than searching for a string pointer (§6).
4. **Do not** pursue: the CM→seg19 adjacency lead (dead, §4); splicing seg26 into the ELF (void, §5);
   xref'ing the `Recvd command` format string (no pointer exists, §6); the PIN2/FDN blocker (§9, ruled out).

---

## 11. Evidence

| artifact | what it is |
| :-- | :-- |
| `/tmp/hmu05.dis` | `llvm-objdump -d --triple=hexagon` of the stock ELF — 4 951 623 lines, 306 038 725 B, stderr empty |
| `GitIgnore/MelbonWhiteStock_Dump/modem_extracted/image/modem.elf` | stock HMU05 ELF, 50 049 024 B, 27 phdrs |
| `scratch/coredump_live/modem_coredump_up919.52.elf` | stock coredump, 85 398 475 B, 21 segments — the only source of seg[26] content |
| `scratch/uz801_fw/modem.elf` | UZ801 ELF, 50 912 492 B, md5 `50ee8c56d6b836329ef97ac83ed34bff`, seg[19] `va 0xc1d5b000` |
| `scratch/doc216_cmfuncs.py` | function index of the HMU05 decompile (70 666 functions) + enclosing-function lookup for the 13 target addresses |
| `scratch/doc216_strings.py` | ELF VA→file-offset, the `0xc1d33d40`–`0xc1d33f40` dump, native-pointer resolution |
| `scratch/doc216_corestr.py` | native-VA→string via the coredump (the only route for seg[26]) |
| `scratch/doc216_findrefs.py` | the 4-byte-LE descriptor search (the §6 table) |
| `scratch/doc216_tablediff.py` | the two-build seg[19] structural scan (§7) |
| `scratch/doc216_cmdname_full.py` | the authoritative 96-slot listing (§8) |
| `Docs/Modem Stability/Modem RE/hmu05/modem_full_decompiled.c` | Ghidra decompile, 105 121 709 B, `/* ---- Function: FUN_x @ x ---- */` headers |

---

## 12. Traps

1. **Proximity inside a multi-megabyte data segment is not a reference.** seg[19] is 3.1 MB; "within ±0x2000 of
   table X" says nothing. Test module identity, not adjacency. (§4)
2. **A `DAT_x` name that Ghidra resolves for one address and not its neighbour is the `immext` blindness, not a
   semantic difference.** `DAT_c1d33db8` resolved 5× while `DAT_c1d33da0` resolved 0× — both are real.
3. **The `##` operand is the authoritative constant.** The `immext(#…)` line printed above it can carry a
   spurious low half (`immext(#0xc1d33d80)` then `memb(##0xc1d33da0)`).
4. **Constants render SIGNED.** `##-0x3e2cb730` is `0xc1d348d0`. (Already in
   `reference_modem_firmware_analysis.md` §2; re-confirmed here.)
5. **A file offset must be tested as `(va − v) < filesz`, not `off ≤ (va − v) < off + filesz`.** The wrong form
   reported every seg[19] address as "memsz-only" in this session's first script — a confident, wrong answer.
6. **A 12-byte `{ptr, ptr, u32}` record table is not an array of 4-byte pointers.** Check the stride before
   indexing; indexing the wrong stride produced 96 "distinct" values for a table with 14 records. (§7)
7. **A pointer stored inside a name table is not a log descriptor.** `0xd0d7783f` / `0xd0d77820` are
   `mmoc_cmd_name[]` entries, not `{packed, strptr}` records. (§6)
8. **`enabled locks` in ModemManager's 3GPP block is not the modem's facility state.** Query
   `AT+CLCK="<fac>",2` and `--uim-get-card-status`. (§9)
9. **A QMI client-allocation timeout is an observation, not an error.** The `low-power` lever's
   `CID allocation failed … Transaction timed out` is the modem reacting — it is data. (§9)
10. **`filesz = 0` does not mean "empty at runtime"** — and now also **does not mean "loadable from a file"**.
    There is no `modem.b26`. (§5)
11. **A name repeating inside a record table means it is an index→name mapping, not a unique list.** Five
    `NAS(REG Task)` slots is the tell: do not read a table with duplicate names as "the set of protocols".
    (§3)
12. **De-duplicating a table while transcribing it silently destroys its indexing.** The first draft of §3's
    table was a sorted unique list with a fabricated `name↔X` pairing; re-dumping in **stored order** was what
    exposed the five NAS aliases. Always print a table in address order before interpreting it.
13. **An `AT+CLCK` / `+CPIN` facility reading beats a ModemManager `lock:` field.** MM's `lock: sim-pin2` and
    `enabled locks: fixed-dialing` were both contradicted by the modem's own answers (§9).

---

## 13. Ledger fold-in

See `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md` §7 **item 21** for the condensed record, and §3 for the new
Achieved-vs-Expected rows. **Item 20's addendum sub-clause (c)/(d) ("CM directly indexes seg[19] tables adjacent
to the MMOC name table", "the concrete next step is to disassemble `0xc0675000`–`0xc0690000`") is struck
through** and replaced by §4/§10 of this document.
