# 215 — THE MODEM'S DATA POINTERS LIVE IN A **NATIVE ADDRESS SPACE** (`ELF VA + 0xbeb1000`): Doc 214 §5.5's "no reference exists" searched the WRONG SPACE, and the MMOC COMMAND-NAME TABLE IS IN THE IMAGE

**Ledger:** see `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md` — §3 (Achieved vs Expected) and §7 item 20.
**Status:** RESULTS
**Corrects Doc 214 §5.5 and re-scopes Doc 214 §12 step 1.** Doc 214 §5.5 concluded that "no 4-byte
little-endian reference to the seg26 strings exists anywhere in the ELF" and inferred that the modem addresses
them PC-/GP-relatively. **The observation is true only in the ELF's link space.** The pointers are stored in a
**native address space** that differs from it by a constant, `0xbeb1000`. Searching that space finds the
references immediately, and it puts the **complete MMOC command/state name table inside the file-backed image**
— a reusable offline decoder and the first concrete handle on the MMOC layer.

---

## 1. SOP compliance and Achieved vs Expected

### 1.1 SOP-compliance statement (per `197 §1`, modelled on `194 §1` / `214 §1`)

| Step | Taken? | Detail |
| :-- | :-- | :-- |
| 1. Backup | **YES (prior session)** | No deployment in this session. The device is byte-identical to the v19 set verified in the prior session (`modem.mdt 9f39ce579114bcf1383bbdce62a8d284`, boot_id `26d74096-874f-45ee-9de4-67a8ee87caf1`, no `BOOTLOOP_REVERT.txt`). |
| 2. Ground-truth verification against stock HMU05 | **YES** | Every new address is derived from the stock HMU05 ELF (`GitIgnore/MelbonWhiteStock_Dump/modem_extracted/image/modem.elf`, 50 049 024 B) and cross-checked against the stock coredump (`scratch/coredump_live/modem_coredump_up919.52.elf`). The alignment `coredump VA + 0x39800000 = ELF VA` was re-derived here, 21/21 segments, before being used. |
| 3. Reconcile transport/config | **YES** | The native constant is not assumed: it is **solved** from a table whose targets are known strings (§3), then used. The disassembler (`llvm-objdump --triple=hexagon`) reports `file format elf32-hexagon`, i.e. the same architecture as the target. |
| 4. Surgical Hexagon patching + re-signing | **NOT TAKEN — deliberately** | Analysis only. No byte of any image was changed. Per `feedback_operating_rules.md` rule 1. |
| 5. Live empirical validation | **NOT APPLICABLE** | The finding is static (a firmware address space and a data table). Nothing was deployed, so there is no device-side claim to validate. The one device-side claim inherited from Doc 214 (v19 offline) is unchanged. |

**Steps skipped and why:** step 4, because the deliverable is a corrected *static* model plus a recovered data
table; there is no byte to patch.
**No `sync` was needed** — nothing was written to the device or to `/overlay`.

### 1.2 Achieved vs Expected

| # | Intent | Expected | Achieved | Status |
| :-- | :-- | :-- | :-- | :-- |
| 1 | Explain Doc 214 §5.5's "no reference to the seg26 strings exists" | A defect in the search or a real architectural fact | **A defect: the search was in the wrong address space.** In native space the same search finds **5 hits in the ELF and 7 in the coredump** | **MET — corrected** |
| 2 | Solve the native address space | A constant `native = ELF VA + K` | **`K = 0xbeb1000`**, solved from a 14-entry table and verified **13/14** against real NUL-terminated strings | **MET** |
| 3 | Locate the MMOC command/state name table | A table in the image | **`mmoc_cmd_name[]` at ELF VA `0xc1d33eb0`**, file-backed seg[19], **96 entries, 90 resolved**, index→name matching every observed F3 ordinal | **MET** |
| 4 | Understand the addressing mechanism | PC- or GP-relative | **Confirmed**: `llvm-objdump` shows a PC-relative base (`r25 = pc`-derived) plus `immext`-extended 32-bit constants; Ghidra renders the base `unaff_GP` and materialises **no** absolute token for these tables | **MET** |
| 5 | Test whether Doc 214 §12 step 1 (rebuild the ELF with seg26 content) is the right fix | The fix | **NO — it is the wrong fix.** The blocker is the *address space*, not the missing bytes: the references are to `0xd0d77cxx`, not `0xc4ec6cxx` (§6) | **MET (as a corrected diagnosis)** |
| 6 | Find the `OPRT_MODE_CHGD` emitter | The emitter | **NOT ACHIEVED** — the name table is data-only; no instruction references it by absolute constant (§7). The emitter is still open | **NOT MET — stated as a limit** |

---

## 2. The headline

| quantity | value |
| :-- | :-- |
| native address space | **`native VA = ELF VA + 0xbeb1000`** (stock HMU05 build) |
| same relation in coredump space | `native VA = coredump VA + 0x456b1000` |
| how it was solved | the 14-entry MMOC protocol-state name table at ELF `0xc4ec67e8`; **13/14** entries land exactly on real NUL-terminated strings |
| 4-byte-LE hits for the MMOC strings, **ELF link space** (Doc 214 §5.5's search) | **0** |
| 4-byte-LE hits for the MMOC strings, **native space** | **5 in the ELF, 7 in the coredump** |
| the MMOC command/state name table | **ELF VA `0xc1d33eb0`**, file-backed **seg[19]**, native `0xcdbe4eb0`, **96 entries / 90 resolved** |
| the `=MMOC= %s` F3 descriptor | ELF VA `0xc1566780` in file-backed **seg[18]**: `{line=291, level=20, len=28, fmt→native 0xd0d77c9e, file→native 0xd0d77ca8}` |

---

## 3. How the native constant was solved

### 3.1 The instrument: a table whose targets are known strings

`scratch/doc215_blob.py` dumped the seg26 string blob from the coredump. Immediately before the enum name
tables it found a run of 14 four-byte values that are neither ELF addresses (`0xc0…`–`0xc5…`) nor coredump
addresses (`0x86…`–`0x8b…`):

```
ELF 0xc4ec67e8: 0xd0d7775c 0xd0d7776c 0xd0d7777d 0xd0d7778d 0xd0d777ac
                0xd0da9a91 0xd0d777c9 0xd0d777b5 0xd0d777c9 0xd0d7779e
                0xd0d777c9 0xd0d777c9 0xd0d777c9 0xd0d777bd
```

Their low bytes track the offsets of the *protocol-state names* that sit just above them
(`MC(CDMA-online)`, `MC(CDMA-offline)`, … `GPS-MSBASED`). Fitting a single constant to the whole run
(`scratch/doc215_addr.py`) gives **`C = 0xbeb1000`** and matches **13/14** entries onto real strings:

```
0xd0d7775c -> 0xc4ec675c  'MC(CDMA-online)'
0xd0d7776c -> 0xc4ec676c  'MC(CDMA-offline)'
0xd0d7777d -> 0xc4ec677d  'MC(AMPS-online)'
0xd0d7778d -> 0xc4ec678d  'MC(AMPS-offline)'
0xd0d777ac -> 0xc4ec67ac  'REG(NAS)'
0xd0d777c9 -> 0xc4ec67c9  'No active protocols'   (x5 — a shared sentinel)
0xd0d777b5 -> 0xc4ec67b5  'MC(FTM)'
0xd0d7779e -> 0xc4ec679e  'MC(DED. MEAS)'
0xd0d777bd -> 0xc4ec67bd  'GPS-MSBASED'
```

The one miss, `0xd0da9a91`, is a sentinel (`0xc4ef8a91`); every other entry is exact. **The relation is
`native VA = ELF VA + 0xbeb1000`.**

### 3.2 The search that Doc 214 ran, re-run in the right space

`scratch/doc215_native.py` searched both images for the native address of each distinctive MMOC string:

| string | ELF VA | native VA | in ELF | in coredump |
| :-- | --: | --: | --: | --: |
| `mmocdbg.c` | 0xc4ec6ca8 | 0xd0d77ca8 | **1** | **1** |
| `OPRT_MODE_CHGD` | 0xc4ec683f | 0xd0d7783f | **1** | **1** |
| `SUBSCRIPTION_CHGD` | 0xc4ec6820 | 0xd0d77820 | **1** | **1** |
| `=MMOC= %s` | 0xc4ec6c9e | 0xd0d77c9e | **1** | **1** |
| `ONLINE_GWL` | 0xc4ec6c5d | 0xd0d77c5d | **1** | **2** |
| `Prot_state [MAIN]` fmt B | 0xc4ec6cf4 | 0xd0d77cf4 | 0 | **1** |
| `Recvd command %d(%s)` | 0xc4ec6d70 | 0xd0d77d70 | 0 | 0 |
| `Trans_state %d(%s)` | 0xc4ec6d49 | 0xd0d77d49 | 0 | 0 |
| **TOTAL** | | | **5** | **7** |

**Zero** in ELF space (Doc 214 §5.5, re-confirmed); **twelve** in native space. The four strings with no
reference are reached by index (see §4) rather than by a stored pointer.

---

## 4. The MMOC command/state name table — `mmoc_cmd_name[]`

All five ELF hits land in **file-backed data segments**:

| string | native VA | file offset | segment | segment VA |
| :-- | --: | --: | --: | --: |
| `=MMOC= %s` | 0xd0d77c9e | 0x014d4788 | **seg[18]** | 0xc1500000 |
| `mmocdbg.c` | 0xd0d77ca8 | 0x014d478c | **seg[18]** | 0xc1500000 |
| `SUBSCRIPTION_CHGD` | 0xd0d77820 | 0x01ca0eb0 | **seg[19]** | 0xc1c3c000 |
| `OPRT_MODE_CHGD` | 0xd0d7783f | 0x01ca0eb8 | **seg[19]** | 0xc1c3c000 |
| `ONLINE_GWL` | 0xd0d77c5d | 0x01ca0ffc | **seg[19]** | 0xc1c3c000 |

At `0xc1d33eb0` (file `0x1ca0eb0`) they are a **contiguous array of native pointers**, one per ordinal.
`scratch/doc215_cmdname.py` resolves the whole array against the coredump: **96 slots, 90 named**, 2 null
holes, and 7 unresolved slots that all point at a shared sentinel (`0xc4f2433d` etc.).

The recovered table (abridged; the script prints all 96):

```
idx  name                        idx  name                        idx  name
  0  SUBSCRIPTION_CHGD            33  GEN_CMD_ENTER               65  OFFLINE
  1  PROT_GEN_CMD                 34  WAIT_GEN_CMD_CNF            66  PROT_GEN_CMD
  2  OPRT_MODE_CHGD               35  WAIT_AUTO_DEACTD_IND        67  PWR_DOWN
  3  WAKEUP_FROM_PWR_SAVE         36  WAIT_AUTO_ACTD_IND          68  PWR_SAVE_ENTER
  4  PROT_REDIR_IND               37  HDR_DEACT_ENTER             69  PROT_AUTO_DEACTD_IND
  5  PROT_HO_IND                  38  WAIT_HDR_DEACTD_CNF         70  WAKEUP_FROM_PWR_SAVE
  6  MMGSDI_INFO_IND              39  PROT_REDIR_ENTER            71  DUAL_STANDBY_CHGD
  7  DUAL_STANDBY_CHGD            40  WAIT_SESSION_OPEN_CNF       72  PROT_REDIR_IND
  8  DEACT_1XCSFB_PROT            41  PROT_HO_ENTER               73  PROT_HO_IND
  9  SUSPEND_SS                   42  DS_STAT_CHGD_ENTER          74  MMGSDI_INFO_IND
 10  DEACT_FROM_DORMANT           43  WAIT_DS_STAT_CHGD_CNF       75  DEACT_1XCSFB_CMD
 11  (null)                       44  GEN_CMD_ACTIVATION_ENTER    76  SUSPEND_SS
 12  PROT_DEACTD_CNF              45  WAIT_ACTIVATION_CNF         77  DEACT_FROM_DORMANT
 13  PROT_AUTO_DEACTD_IND         46  WAIT_PS_DETACH_CNF          78  (sentinel)
 14  PH_STAT_CHGD_CNF             47  HYBR2_DEACT_ENTER           79  ONLINE_CDMA
 15  PROT_GEN_CMD_CNF             48  WAIT_HYBR2_DEACTD_CNF       80  OFFLINE_CDMA
 16  PROT_AUTO_ACTD_IND           49  HYBR3_DEACT_ENTER           81  ONLINE_AMPS
 17  MMGSDI_CNF                   50  WAIT_HYBR3_DEACTD_CNF       82  OFFLINE_AMPS
 18  DS_STAT_CHGD_CNF             51  WAIT_DEACTD_CNF_GWL         83  ONLINE_GWL
 19  ACTIVATION_CNF               52  WAIT_1XCSFB_DEACT_CNF       84  ONLINE_HDR
 20  PS_DETACH_CNF                53  SUSPEND_SS_ENTER            85  OFFLINE
 21  1XCSFB_PROT_DEACTD_CNF       54  RESUME_SS_ENTER             86  (sentinel)
 22  IRAT_HOLD_USER_ACT_CNF       55  WAIT_HOLD_USER_ACT_CNF      87  PWR_SAVE
 23  UE_MODE_SWITCH_CNF           56  WAIT_UE_MODE_SWITCH         88  ONLINE_DED_MEAS
 24  IMS_DEREG_CNF                57  WAIT_SUBS_CAP_CHGD_ENTER    89  (sentinel)
 25  SUBS_CAP_CHGD_CNF            58  WAIT_SUBS_CAP_CHGD_CNF      90  (sentinel)
 26  (sentinel)                   59  WAIT_IMS_DEREG_CNF          91  PWR_DOWN
 27  PROT_DEACT_ENTER             60  PS_DETACH_ENTER             92  GPSONE_MSBASED
 28  WAIT_DEACTD_CNF              61  (null)                      93  DORMANT_GWL
 29  MMGSDI_READ_ENTER            62  (sentinel)                  94  (sentinel)
 30  MMGSDI_READ_CNF              63  SUBSC_CHGD                  95  (null)
 31  PROT_PH_STAT_ENTER           64  (sentinel)
 32  WAIT_PH_STAT_CNF
```

**Validation against observed behaviour.** Every ordinal in the F3 captures maps correctly through this
table: `Recvd command 0(SUBSCRIPTION_CHGD)`, `1(PROT_GEN_CMD)`, `2(OPRT_MODE_CHGD)`,
`4(PROT_REDIR_IND)`, `7(DUAL_STANDBY_CHGD)`, and `Prot_state [MAIN] 5(ONLINE_GWL)` / `7(OFFLINE)`.
**This is a reusable asset**: any MMOC ordinal can now be decoded offline without the modem.

---

## 5. The `=MMOC= %s` descriptor, and `logsite.py`'s model confirmed

At ELF VA `0xc1566780` (seg[18], file `0x14d4780`) sits a 16-byte record:

```
line=291  level=20  len=28  fmt -> native 0xd0d77c9e ("=MMOC= %s")  file -> native 0xd0d77ca8 ("mmocdbg.c")
```

`line=291` is the **stock** `mmocdbg.c` line for that log site — Doc 214 §7.1 recorded 291 stock / 288 UZ801.
Immediately after it, the table switches to an **8-byte** record form (`line=43 level=360 …`), i.e. the
string-bearing 16-byte entries are the minority. That is exactly `scratch/logsite.py`'s descriptor model —
"`word1` is a string pointer for ~8 % of sites, a hash otherwise" — **and it now has a mechanism: the ~8 %
string-pointer entries hold NATIVE pointers.**

---

## 6. Why Doc 214 §12 step 1 is the WRONG fix

Doc 214 §12 step 1 proposed rebuilding the ELF so seg24/25/26 carry their real content (`filesz` from the
coredump, offset by `0x39800000`), expecting the seg26 globals to resolve and the `OPRT_MODE_CHGD` emitter to
become an ordinary xref query.

**That cannot work, for a reason that is now measured.** The missing segment content is not what blocks
resolution — the **address-space mismatch** is. Adding bytes at ELF VA `0xc4ec6cxx` does nothing for a
reference that is a `0xd0d77cxx` value. The correct fix is to teach the analyser the native base:

* either a **Ghidra memory block at the native VAs** (a duplicate of the image translated by `+0xbeb1000`), so
  native references resolve to real data;
* or the **GP / PC-relative base value**, so the `unaff_GP + off` form resolves in place.

Both are cheap; neither is what Doc 214 proposed. **Doc 214 §12 step 1 is superseded by §9 step 1 below.**

---

## 7. The addressing mechanism, from the disassembly

`llvm-objdump -d --triple=hexagon` on the stock ELF (4 951 623 lines, 7 s) shows the idiom directly:

```
c0000440:  r24 = pc
c0000444:  immext(#0xc3eaf000)
c0000448:  r27 = ##0xc3eaf000          <- an absolute 32-bit constant, link space
c0000450:  r24 = and(r24,r0)
c0000454:  r25 = sub(r24,r26)          <- r25 becomes a PC-relative base
c0000458:  immext(#0xc1c3d000)
c000045c:  r0 = memw(r25+##-0x3e3c2fc8)
```

So the code uses a **PC-relative base register plus `immext`-extended 32-bit constants**. Ghidra models the
base as an unknown register and renders it `unaff_GP` (90 699 occurrences in the HMU05 decompile, e.g.
`*(int *)(unaff_GP + 0x3128)`). Its offsets are small and clustered — 9 797 distinct values in
`0x10…0x12814` — so the base addresses a data window of roughly 76 KB.

**Consequence for the emitter search.** `scratch/doc215_desc.py` and the disassembly grep show that **no
instruction references the MMOC name table (`0xc1d33eb0`) or its page (`0xc1d33e`/`0xc1d33f`) by any absolute
constant** — 0 hits. The table is reached through the PC-relative base, so recovering the emitter requires
resolving that base first. **The emitter is therefore still open**, and this doc states that as a limit rather
than claiming it.

---

## 8. What is now possible

1. **Decode any MMOC command/state ordinal offline** — the table is complete and validated (§4).
2. **Recover the F3 descriptor for any MMOC log site** — `{line, level, len, fmt→native, file→native}` records
   are in file-backed seg[18] (§5), so a site can be identified without a capture.
3. **Search for data references correctly** — any future "reference to string X" search must be run in
   **both** spaces, and the native one is `ELF VA + 0xbeb1000` on the stock build.
4. **Re-attempt the emitter** with the base resolved (§9 step 1), which is now a bounded, mechanical task.

---

## 9. Next steps, in priority order

1. **★ Resolve the PC-relative base, then find the `OPRT_MODE_CHGD` emitter.** Build a Ghidra program with a
   second memory block holding the image translated by `+0xbeb1000` (so native references resolve), or set the
   GP/PC-relative base value, then re-run the decompile and xref the name table and the `Recvd command` format
   string. **This supersedes Doc 214 §12 step 1.**
2. **Solve the UZ801 native constant.** The constant `0xbeb1000` is verified on the **stock** build only. The
   UZ801 build links seg26 at a different VA (`0xc4615000`, `memsz 11448320`), so its constant must be solved
   the same way — by finding one of its own pointer tables and fitting it. Without it, no UZ801 data reference
   can be resolved.
3. **Diff the two builds' `mmoc_cmd_name[]` and descriptor tables.** They are file-backed and now locatable in
   both builds. If the code is structurally identical (Doc 208 for CM), a behavioural difference has to live in
   data — these tables are the first place to look.
4. **Still worth doing from Doc 214 §12:** obtain a **UZ801 coredump** (its seg26 is a different size and has
   no dump); re-capture the live UZ801 arm with `mmocdbg.c` demonstrably > 0; follow the `cmss.c` 18-vs-0
   asymmetry.

**Do NOT re-open:** `cmcc.c`/GROUP 11, `0x456`/`0x422`, the `u=1` race, the `evt=0` patch, `0x1000001`,
`cm_state+0x39e8`, "the CM code differs", the `mode_pref` 9728-vs-38 difference, the `MSC_AUTO`
`0x220`-vs-`0xebe` difference as a code difference, the policyman RAT-mask-0 lead, the `dcc` break **as the
cause** of the offline, `PROT_GEN_CMD` as the online gate, or the `cmph rat_disabled_mask` sequence as the
emitter. **Do NOT** compare `file:line` across builds; **do NOT** score a CM-layer negative on a capture with
`rcinit_init.c` = 0 or `mmocdbg.c` = 0; **do NOT** take an `args` value from a parser older than
`9f9d6edf15d64faf3b47897f7838c9ac`; **do NOT** use `grep -c` for a string census on these images; and now also
**do NOT** conclude "no reference exists" from a search that ran in only one address space.

---

## 10. Evidence

| artifact | size / md5 | use |
| :-- | :-- | :-- |
| `GitIgnore/MelbonWhiteStock_Dump/modem_extracted/image/modem.elf` | 50 049 024 | the stock build; all native-space hits and both tables |
| `scratch/coredump_live/modem_coredump_up919.52.elf` | 85 398 475 | seg26 string content; name resolution; alignment re-derived 21/21 |
| `scratch/doc215_phdr.py` | — | phdr dumps + the 21/21 memsz↔filesz correspondence |
| `scratch/doc215_map.py` | — | the segment correspondence and `va-diff = 0x39800000` on all 21 |
| `scratch/doc215_ptr.py` | — | string VAs + the pointer hunt that found nothing in ELF space |
| `scratch/doc215_blob.py` | — | the seg26 blob dump that exposed the pointer tables |
| `scratch/doc215_addr.py` | — | **the constant solve — `C = 0xbeb1000`, 13/14** |
| `scratch/doc215_native.py` | — | the native-space reference census (5 ELF / 7 core) |
| `scratch/doc215_desc.py` | — | the file-backed descriptor/name-table neighbourhoods |
| `scratch/doc215_cmdname.py` | — | **the complete `mmoc_cmd_name[]`, 96 entries / 90 resolved** |
| `scratch/doc215_consolidate.py` | — | the consolidation + seg[18] descriptor record |
| `/tmp/hmu05.dis` | 4 951 623 lines | the full Hexagon disassembly used for §6/§7 |

---

## 11. Traps (new; Doc 214's list still stands)

1. **A reference search in one address space produces a confident zero.** Doc 214 §5.5's "no reference exists
   anywhere in the ELF" was true of ELF space and false overall — the pointers are native. **Before scoring a
   "no reference" verdict, say which address space was searched and why it is the right one.**
2. **The native constant is per-build, not universal.** `0xbeb1000` is solved for the stock HMU05 build. The
   UZ801 build links seg26 elsewhere (`0xc4615000`), so its constant must be solved independently.
3. **A pointer table's absence from the decompile is not evidence of absence.** The tables are pure data; no
   instruction references them by absolute constant (§7). Data-only tables never appear as decompile tokens.
4. **`unaff_GP` is a symptom, not a name.** Ghidra uses it for a PC-relative base it cannot resolve; the
   offsets are small (`0x10…0x12814`), so the base points at a ~76 KB window, not at the whole image.
5. **Doc 214 §12 step 1's premise is falsified.** Splicing seg26 content into the ELF does not make the
   references resolve, because the references were never in ELF space.
6. **A "sentinel" pointer is not a name.** Seven slots in `mmoc_cmd_name[]` share one value
   (`0xc4f2433d`); treating it as a string yields nonsense. Resolve against the coredump, not by guessing.
7. **`llvm-objdump` needs `--triple=hexagon`.** Without it the file is reported as an unknown format; with it
   the 50 MB image disassembles in seconds and the `immext` constants are visible.

---

## 12. Ledger fold-in

`197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md` gains **§7 item 20**, carrying: the native address space and the
solved constant (§3), the corrected Doc 214 §5.5 verdict (§2, §3.2), the complete `mmoc_cmd_name[]` table and
its validation (§4), the `=MMOC= %s` descriptor (§5), the falsified Doc 214 §12 step 1 (§6), the addressing
mechanism (§7), the re-prioritised next steps (§9), and the new traps (§11).

---

## 13. ADDENDUM — the working instrument, and the first CM→seg19 references

Written in the same session, after §1–§12. This is §9 step 1 begun.

### 13.1 ★ Ghidra does **not** materialise the `immext` constants — llvm-objdump does

§7 established that the code reaches its data through a PC-relative base. It turns out the failure is
**narrower and more specific than that**: the code *also* uses absolute 32-bit constants, and **Ghidra is not
emitting them**. `llvm-objdump` shows, for example,

```
c11ebb7c:  immext(#0xc1c3c8c0)
c11ebb80:  r29 = ##-0x3e3c3740        ; == 0xc1c3c8c0
c11ebb8c:  r29 = addasl(r29,r1,#0x5)  ; an array-of-structs base, 32-byte stride
```

yet the HMU05 decompile contains **0** occurrences of `c1c3c8c0`, and **0** of `c1d33eb0` (the MMOC name
table) and `c1566780` (the F3 descriptor). Two nearby ones do appear, once or twice each, by coincidence.
**⇒ Doc 214 §5.5's "the decompile materialises zero `0xc45xxxxx` tokens" is a Ghidra artefact, not evidence
about the firmware.** The reliable instrument is the `llvm-objdump --triple=hexagon` disassembly.

### 13.2 The instrument: bucket every absolute constant by segment

`scratch/doc215_consts.py` parses `/tmp/hmu05.dis` (4 951 623 lines), normalises every `##`-operand to a u32,
and buckets it by the segment that contains it. Result:

| segment | base | code references |
| --: | --: | --: |
| 18 | 0xc1500000 | **167 764** |
| 19 | 0xc1c3c000 | **20 298** |
| 23 | 0xc3c1c000 | 17 823 |
| 16 | 0xc0287000 | 6 412 |
| 22 | 0xc3c09000 | 6 146 |
| 24 | 0xc3c98000 | 4 456 |
| … | | |

So the code addresses seg[18] and seg[19] **absolutely**, at 141 989 and 9 954 distinct addresses
respectively. The tables of §4/§5 are in exactly those two segments.

### 13.3 ★ The first code references near the MMOC name table — and they are in **CM**

Of the 9 954 distinct seg[19] addresses, **8 fall within ±0x2000 of the MMOC name table (`0xc1d33eb0`)**:

| referenced address | referencing instruction | access |
| --: | --: | :-- |
| 0xc1d33da0 | `c0675640` | `memb(##0xc1d33da0) = r17` |
| 0xc1d33db8 | `c0676038` / `c0676048` | `memh(##0xc1d33db8) = r0.new` (values `0x207`, `0x409`) |
| 0xc1d33db8 | `c06760a8` | `r2 = memuh(r19=##0xc1d33db8)` |
| 0xc1d348c0 | `c067dfac`, `c0682250`, `c0682258` | `r2 = memw(r0<<#0x2+##0xc1d348c0)` — **a word-indexed table** |
| 0xc1d348c8 | `c0682ab8` | `r1 = memw(##0xc1d348c8)` |
| 0xc1d34900 | `c068a6b8`, `c068a814` | `r0 = memub(##0xc1d34900)` / `memb(##0xc1d34900) = r16` |
| 0xc1d35ab6 | `c068f800` | `r2 = memub(r1<<#0x0+##0xc1d35ab6)` |

**Every referencing instruction sits in `0xc067xxxx`–`0xc068xxxx`, i.e. seg[16]** — and that is the **CM**
range (`FUN_c0659ef0` is stock's CM dispatcher, `FUN_c066fa30` the `cmcc.c` function, `FUN_c06951b0` the CM
allocator; Doc 208, Doc 207). **CM code directly indexes seg[19] tables adjacent to the MMOC command/state
name table.** That is the first code-level handle on the CM→MMOC data relationship, and it is where §9 step 1
resumes.

### 13.4 The region immediately before the name table is itself a table

`0xc1d33d80`–`0xc1d33eb0` is a run of records each ending in a **native string pointer**, and the MMOC
name table begins immediately after it at `0xc1d33eb0`. Two recurring targets appear:

```
0xc1d33dd8 -> 0xc4ec4d08   0xc1d33dec -> 0xc4ec4c50   0xc1d33e00 -> 0xc4ec4be8
0xc1d33e08 -> 0xc4ec6553   0xc1d33e14 -> 0xc4ec655e   0xc1d33e20 -> 0xc4ec6567
0xc1d33e2c -> 0xc4ec6575   0xc1d33e38 -> 0xc4ec6579   0xc1d33e44 -> 0xc4ec6597
0xc1d33e48 -> 0xc41ef90c   0xc1d33e50 -> 0xc41ef90c   0xc1d33e58 -> 0xc41ef90c
```

The `0xc4ec65xx` targets are seg26 strings; the repeated `0xc41ef90c` / `0xc41ef890` targets are in **seg[24]**
(a file-backed segment). The `{seg26 name, seg24 record}` pairing recurs — **this is a second descriptor-style
table, and it is reachable from CM.**

### 13.5 What this addendum does and does not establish

**Establishes:** a working, reusable instrument (constant bucketing on the disassembly); the fact that Ghidra's
decompiler is blind to these constants; that **CM code at `0xc067xxxx`–`0xc068xxxx` indexes seg[19] tables
adjacent to the MMOC name table**; and the layout of the table immediately preceding it.

**Does NOT establish:** the `OPRT_MODE_CHGD` emitter. No instruction references the name table's **exact base**
(`0xc1d33eb0`); the references found are to neighbours, so the emitter is reached through them, not directly.
**Still open, and now the concrete next step: disassemble `0xc0675000`–`0xc0690000` and read the functions that
index `0xc1d348c0` and write `0xc1d33da0`/`0xc1d33db8` — those are CM's, and CM is where the differential
lives.**

