# 218 — THE UZ801 MMOC NAME TABLE IS NOT IN THE IMAGE (stock's is): three independent tests, one negative

**Date:** 2026-09-27
**Ledger:** `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md` (item 23; §3 and §7/§8 updated in the same session)
**Device:** HMU05 4G modem stick, OpenWrt 25.12.5, kernel 6.12.94 aarch64, `192.168.8.1`, SSH root.
**Firmware under test:** UZ801 v3.2 "-21" baseband, deployed set md5 `modem.mdt = 9f39ce579114bcf1383bbdce62a8d284`.
**Status:** Doc 217 §8.1 opened "locate the UZ801 `mmoc_cmd_name[]` / protocol tables" as **priority #1**. This
document **executes the two candidate tests it named** — and both are **negative**. The UZ801 command-name
strings have **no pointer table and no offset table anywhere in the captured image**, while the **stock** build
verifiably has a 96-entry native-pointer table. The lookup mechanism is therefore **not a static pointer array**;
the evidence points to a **runtime-populated table** (an adjacent pointer array's targets are zeroed in the dump)
and/or a **hash/ID** lookup, consistent with the F3 descriptor hash finding.

---

## 1. SOP compliance and Achieved vs Expected

**SOP compliance statement (ledger `197` §1).** This work is **read-only** against the already-captured,
md5-verified UZ801 coredump (`scratch/coredump_uz801/uz801_coredump.elf`, md5
`59855a4cfac73e7dd2a4b54032ff5c42`) and the two ground-truth firmware artifacts (the joined UZ801 ELF
`scratch/uz801_fw/modem.elf`, the stock ELF under `GitIgnore/MelbonWhiteStock_Dump/`, and the stock coredump
`scratch/coredump_live/modem_coredump_up919.52.elf`). **No device access, no firmware write, no deployment** was
performed in this session's analysis. The native-address relation used (`native = ELF VA + 0x0beb1000`,
`ELF VA = coredump VA + 0x39800000`) is the one **content-verified** by Doc 217 §6 on all 21 segments — it is not
re-derived here. All conclusions are stated with the **chance model** that could have produced them, and the
negative is scored against a **positive control** (the stock build, same method, same encoding).

| Intent | Expected | Achieved | Status |
| :-- | :-- | :-- | :-- |
| Test Doc 217 §8.1 candidate (a) — is the table **outside the 21 captured segments**? | a yes/no | **NO.** The UZ801 ELF has 27 phdrs; the one extra region (`phdr[1]`, `type 0`, pa `0x8b900000`, `filesz 0x1c88`) is the **MBN attestation header** (X.509 certs, `SW_ID`/`HW_ID`/`SHA256`), **not** a table. Every other PT_LOAD is among the 21 dumped segments | **MET** (negative) |
| Test Doc 217 §8.1 candidate (b) — is the table **offset-encoded**? | a yes/no | **NO.** A whole-image scan for any run of ≥8 **distinct, strictly increasing** u32 words equal to the pool's string offsets returns **0 runs**. A base-independent test (search for the pool's consecutive **offset-difference** pattern) also returns **0** | **MET** (negative) |
| Direct test — does **any** word point into the command-name range? | a count | **3**, all landing **mid-string**, at the **chance level** for the range size. Not a table | **MET** (negative) |
| Positive control — does **stock** have such a table? | a yes/no | **YES.** `mmoc_cmd_name[]` at stock ELF `0xc1d33eb0` holds **96 native pointers** (`0xd0d77820`…) that resolve **exactly** to `SUBSCRIPTION_CHGD`, `PROT_GEN_CMD`, `OPRT_MODE_CHGD`, … via the stock coredump | **MET** |
| Establish that the negative is not an **encoding** artefact | a check | **PASSED.** 878 aligned words in a single 1 MB window of seg[26]'s native range are native pointers into seg[26] (99 resolve to strings) ⇒ the native encoding **is** used and searched. The 0 is a real absence | **MET** |
| Characterise the pool's runtime structure | a layout | **MET.** `[struct-pointer array][protocol names][14-ptr array][command names][log formats]`; the **14-pointer array's targets are ZEROED** in the dump | **MET** |
| Name the mechanism (if not a pointer table) | a hypothesis | **STATED, not proven:** a **runtime-populated** table (never built because the modem was `offline`) and/or a **hash/ID** lookup (as the F3 descriptors use) | **STATED** |
| Make no change to the device or firmware | none | none made | **MET** |

---

## 2. Headline

| # | finding | one line |
| :-- | :-- | :-- |
| 1 | **The UZ801 command-name strings have no pointer table** | a whole-image search in **three encodings** (native `cd+0x456b1000`, ELF `cd+0x39800000`, raw `cd`) for pointers to 12 target names returns **0** |
| 2 | **…and no offset table** | 0 strictly-increasing distinct-offset runs; 0 runs matching the pool's offset-difference pattern |
| 3 | **…and essentially nothing points into the name range** | **3** words total, all mid-string, at chance level |
| 4 | **Stock is the positive control** | `mmoc_cmd_name[]` @ ELF `0xc1d33eb0` = **96 native pointers** resolving to the exact command names |
| 5 | **The negative is not an encoding artefact** | **878** native seg[26] pointers exist in one 1 MB window alone (99 resolve to strings) |
| 6 | **The table is not "outside the captured segments"** | the only un-dumped ELF region is the **MBN attestation header** |
| 7 | **The pool has a runtime structure** | `[ptr array][protocol names][14-ptr array][command names][log formats]` |
| 8 | **The 14-pointer array's targets are ZERO** | consistent with a table that is **populated at runtime** and was unpopulated in this dump |
| 9 | **Mechanism** | most consistent with a **runtime-built table** and/or **hash/ID** lookup — **not** a static array |
| 10 | **Consequence for the campaign** | the "decode an MMOC ordinal offline" asset is **stock-only**; UZ801 ordinals cannot be decoded from the image alone |

---

## 3. The methods and their results

All addresses below are **coredump VAs** unless labelled `ELF`/`native`. The pool window is
`cd 0x8b7f8230 .. 0x8b7f8b5c` (ELF `0xc4ff8230..0xc4ff8b5c`), holding **135 printable strings** (length ≥ 3,
NUL-terminated). **⚠ A NUL-to-NUL scan reports only 116** — the pointer array immediately before the
command-name block contains **no zero bytes**, so a NUL-to-NUL scan swallows `SUBSCRIPTION_CHGD` and the whole
block after it (§7.4). Doc 217 §8's "119 NUL-terminated strings" is the same artefact. The **command-name**
sub-range used for the decisive count is `native 0xd0ea9508 .. 0xd0ea9b60`
(`cd 0x8b7f8508..0x8b7f8b60`), starting at `SUBSCRIPTION_CHGD`.

### 3.1 Test 1 — exact-address pointer search (three encodings)

For each of `MODE_INACT`, `WAIT_SESSION_OPEN_CNF`, `ONLINE_GWL`, `OPRT_MODE_CHGD`, `DUAL_STANDBY_CHGD`,
`SUBSCRIPTION_CHGD`, `MMOC:ready`, `Recvd command` (and, in the wider pass, every copy of `GPS`, `Invalid`,
`RESERVED`, `PROT_GEN_CMD`), the script computed the string's native, ELF-space and raw addresses and searched
**all 21 segments** for a 4-byte LE word equal to each, testing **VA** alignment (`(p_vaddr + i) % 4 == 0` —
seg[26] has `off % 4 = 2`). **Result: 0 hits for every target in every encoding.**

> **This is the same test that, on the stock build, finds the table.** The stock pointers were read directly from
> stock ELF `0xc1d33eb0` and **resolve** (§4). Same method, opposite outcome.

### 3.2 Test 2 — offset-encoded table

Two forms were tested, both over **every aligned u32 in all 21 segments**:

* **Absolute-offset form** — is there a run of ≥8 words that are **distinct, strictly increasing**, and each
  equal to a pool string's offset from the pool base? **0 runs.**
* **Base-independent form** — take the pool's **consecutive offset differences** for a 24-name stretch
  (`SUBSCRIPTION_CHGD` onward) and search for any run whose consecutive differences match that pattern exactly.
  **0 runs.**

### 3.3 Test 3 — the decisive count

Count **every** aligned u32 word, anywhere in the image, whose value lies in the command-name range
`[0xd0ea9508, 0xd0ea9b60)` (1 624 bytes):

| word | location | resolves to |
| :-- | :-- | :-- |
| `0xd0ea9816` | `cd 0x8a6a2408` (phdr 18) | `…CTD_CNF_GWL` — **8 bytes into** `WAIT_DEACTD_CNF_GWL` |
| `0xd0ea977c` | `cd 0x8a78a494` (phdr 18) | `F` — mid-string |
| `0xd0ea9568` | `cd 0x8ab2aa9c` (phdr 18) | `…GSDI_INFO_IND` — **8 bytes into** `MMGSDI_INFO_IND` |

**3 words, all mid-string.** The chance expectation: seg[26] pointers are common (878 in a single 1 MB window,
§3.4), so a uniform pointer distribution puts `878 × 1624/1048576 ≈ 1.4` words in the range by chance alone.
**3 is chance.** None of the three lands on a string start, and none is part of a run.

### 3.4 The control that makes the negative interpretable

Searching the 1 MB window `[0xd0e00000, 0xd0f00000)` (native) for aligned u32 words yields **878** hits, of
which **99** resolve to printable strings — i.e. **native pointers into seg[26] are abundant and the native
encoding is genuinely in use**. So Test 1's zero is **not** "the image uses some other encoding"; it is a real
absence of pointers to these particular strings.

### 3.5 Test 4 — the "outside the captured segments" hypothesis

The UZ801 ELF has **27 phdrs**, the coredump **21**. The six extra are `type 0` (`phdr[0]`, `phdr[1]`) and the
four `filesz = memsz = 0` placeholders (`phdr[6][7][9][12]`). Of the `type 0` pair, `phdr[1]`
(`va 0x8b900000`, `pa 0x8b900000`, `filesz 0x1c88`) carries **file content**, and dumping it shows the **MBN
attestation header**: `Generated Test Root CA`, `General Use Test Key (for testing only)`, `SW_ID`,
`HW_ID`, `OEM_ID`, `MODEL_ID`, `SHA256`, `http://crl.qdst.com/crls/qctdevattest.crl`. **It is not a table.**
⇒ the table is not hidden outside the dumped segments.

---

## 4. The positive control — stock **does** have the table

The stock `mmoc_cmd_name[]` at **ELF `0xc1d33eb0`** (file offset `0x1ca0eb0`, file-backed seg[19]) is a 96-slot
array of **native** pointers. Read from the stock ELF and resolved against the **stock coredump**
(`scratch/coredump_live/modem_coredump_up919.52.elf`, 85 398 475 B, native bias `0x456b1000`):

| slot | stored value | native target | resolves to |
| --: | :-- | :-- | :-- |
| 0 | `0xd0d77820` | `cd 0x8b6c6820` | `SUBSCRIPTION_CHGD` |
| 1 | `0xd0d77832` | `cd 0x8b6c6832` | `PROT_GEN_CMD` |
| 2 | `0xd0d7783f` | `cd 0x8b6c683f` | `OPRT_MODE_CHGD` |
| 3 | `0xd0d7784e` | `cd 0x8b6c684e` | `WAKEUP_FROM_PWR_SAVE` |
| 4 | `0xd0d77863` | `cd 0x8b6c6863` | `PROT_REDIR_IND` |
| 5 | `0xd0d77872` | `cd 0x8b6c6872` | `PROT_HO_IND` |
| 6 | `0xd0d7787e` | `cd 0x8b6c687e` | `MMGSDI_INFO_IND` |
| 7 | `0xd0d7788e` | `cd 0x8b6c688e` | `DUAL_STANDBY_CHGD` |
| 17 | `0xd0d77925` | `cd 0x8b6c6925` | `1XCSFB_PROT_DEACTD_CNF` |
| 18 | `0xd0d77a85` | `cd 0x8b6c6a85` | `DS_STAT_CHGD_CNF` |

This is the **same construct** the campaign has been hunting in UZ801, and it is **absent there**. The two pools
also sit at different places in seg[26] (stock `cd 0x8b6c6820`; UZ801 `cd 0x8b7f8508`) — consistent with two
different runtime allocators, not with a shared layout.

---

## 5. What the UZ801 pool actually looks like at runtime

Reading `cd 0x8b7f8100 .. 0x8b7f8b60` in address order:

| range | content |
| :-- | :-- |
| `0x8b7f8100..0x8b7f822c` | a dense array of **native pointers to structures** in the low part of seg[26] (`0xd009xxxx`/`0xd00axxxx` → `cd 0x8a9exxxx`); the targets are **not** printable strings |
| `0x8b7f8230..0x8b7f84cf` | **protocol names + short messages**: `MMOC:ready`, `MODE_INACT`, `MC(AMPS)`, `CDMA(MC Task)`, `GPS`, `HDR(HDRMC Task)`, `RESERVED`, `HYBR NAS(REG Task)`, `HYBR 3(REG Task)`, `Invalid`, the `MC(CDMA-online)`…`GPS-MSBASED` RAT names, the `No active protocols` / `…request sent to…` messages |
| `0x8b7f84d0..0x8b7f8504` | a **14-entry** native-pointer array (`0xd0cb9444`, `0xd0cb9454`, `0xd0cb9465`, `0xd0cb9475`, `0xd0cb9494`, `0xd0ce8de9`, `0xd0cb94b1`, `0xd0cb949d`, `0xd0cb94b1`, `0xd0cb9486`, `0xd0cb94b1`, `0xd0cb94b1`, `0xd0cb94b1`, `0xd0cb94a5`) — **14 is exactly the size of stock's protocol-name table** |
| `0x8b7f8508..0x8b7f8b5c` | **command names**: `SUBSCRIPTION_CHGD`, `PROT_GEN_CMD`, `OPRT_MODE_CHGD`, … `SUBSC_CHGD`, `ONLINE`, `OFFLINE`, `ONLINE_GWL`, `GPSONE_MSBASED`, `DORMANT_GWL`; then the log formats (`=MMOC= %s`, `mmocdbg.c`, `Prot_state [MAIN] %d(%s) …`, `Curr_trans  %d(%s)`, `Trans_state %d(%s)`, `Recvd report %d(%s)`, `Recvd command %d(%s)`, `New transaction           : %d(%s)`) and three NV item paths |

**★ The 14-pointer array's targets are all `0x00000000` in the dump** (`cd 0x8b608430..0x8b6084cc` is entirely
zero). A pointer array whose targets are zeroed is the signature of a table that is **allocated/registered but
not yet populated**.

---

## 6. Interpretation

The MMOC command-name strings exist in the UZ801 runtime (seg[26], a runtime-populated region), and the firmware
clearly uses them — `Recvd command %d(%s)` prints them. Yet **nothing in the image points at them**. Two
mechanisms remain, and they are not mutually exclusive:

1. **A runtime-populated table.** The 14-pointer array immediately before the command names has **zeroed
   targets**, and the modem was captured in `offline` — i.e. with the MMOC protocol stack never brought up.
   If the command-name table is built when MMOC initialises (as stock's presumably is), a dump taken `offline`
   would show **exactly this**: strings present (they are in the logging pool) and table entries absent/zero.
2. **A hash/ID lookup.** The F3 log descriptors are ~92 % **hash** form with no pointer in the image (Doc 216
   §6); an MMOC that resolves names by a compiled-in hash would likewise leave **no pointer** to the strings.

**What this rules out** is the campaign's working assumption — inherited from the stock finding — that a
`mmoc_cmd_name[]`-style pointer array can be located in the UZ801 image by its references. **It cannot.**

**Consequence, stated plainly:** the asset "any MMOC ordinal can be decoded offline" (Doc 215 §4 / ledger item
20) is **stock-only**. For UZ801 the ordinal→name map must come from either (a) a coredump taken with the modem
**further through bring-up** (impossible while the modem cannot leave `offline`), or (b) a **runtime** read —
which is the same wall the campaign keeps hitting.

---

## 7. Traps (all cost real time in this session)

1. **A 32-bit value in a data segment is not a pointer until you show the value distribution.** The 3 hits are
   mid-string; the range is 1 624 bytes and seg[26] pointers are common. **Always state the chance level.**
2. **`off % 4 == 0` is the wrong alignment test for a coredump.** Coredump phdrs are page-aligned but seg[26] has
   `off % 4 = 2`; test **`(p_vaddr + i) % 4`**.
3. **The ELF header is easy to mis-unpack.** `e_phoff` @28, `e_phentsize` @42, `e_phnum` @44. Reading @28 as
   `e_phentsize` silently yields "0 phdrs" and a confident "string not found".
4. **A `split(b'\x00')` / NUL-to-NUL scan is not a presence test — and it also under-counts.** The pool contains
   a binary gap (the pointer array before the command-name block has **no zero bytes**); the first draft
   concluded `SUBSCRIPTION_CHGD` was absent, and the count came out **116 instead of 135**. Extract **maximal
   printable runs terminated by NUL**. Decide presence/absence by **exact NUL-terminated token search over the
   whole image**.
5. **`type 0` phdrs can carry file content.** The "extra" ELF region is the MBN attestation header, not a table —
   check before concluding "outside the captured segments".
6. **A pointer array with zeroed targets is a signal, not noise.** Do not discard it: it dates the dump to a
   state in which the table was never populated.
7. **Memory pressure.** This host had ~1.3 GB free with swap nearly full; `struct.unpack_from('<{n}I', blob)`
   over 21 M words, or materialising per-candidate windows, **OOM-kills** the process. Use `mmap` + `array.array`
   and never store the candidate windows.
8. **Do not compare a stock artifact to a UZ801 artifact without the same control.** The stock table resolves
   only against the **stock coredump** (its targets are in the stock's `filesz = 0` seg[26]); the UZ801 table
   would resolve only against the **UZ801 coredump**.

---

## 8. Ledger fold-in

* **Item 23** added to `197` §7: the three tests, their results, the stock control, the pool structure, the
  zeroed 14-pointer array, and the mechanism hypothesis.
* **§3 rows added:** test candidate (a) outside-the-segments (**MET, negative**); test candidate (b)
  offset-encoding (**MET, negative**); direct count (**MET, negative**); stock positive control (**MET**);
  encoding-artefact check (**MET**); pool structure (**MET**); mechanism hypothesis (**STATED**).
* **§3 row superseded:** Doc 217 §8.1's "locate the UZ801 `mmoc_cmd_name[]` / protocol tables (**NOT MET, new
  priority #1**)" — the **route is now exhausted**; the row is re-scored as a **bounded negative**.
* **§8 evidence rows added:** `tblhunt.py` (this doc's instrument), the three one-off probes, and the stock
  control read.

---

## 9. Evidence

| artifact | what it is |
| :-- | :-- |
| `scratch/coredump_uz801/tblhunt.py` | **new** — the segment map, all-copy string finder, and the three-encoding pointer search (Test 1) |
| `scratch/coredump_uz801/mmocptr.py` | the earlier targeted pointer probe (the original 0-hit result) |
| `scratch/coredump_uz801/stocktbl.py` | reads the stock `mmoc_cmd_name[]` (ELF `0xc1d33eb0`); the positive control |
| `scratch/coredump_uz801/xlat.py` | the coredump↔ELF↔native translator (`NATIVE_BIAS 0x456b1000`, `ELF_BIAS 0x39800000`) |
| `scratch/coredump_uz801/uz801_coredump.elf` | the UZ801 coredump (84 407 190 B, md5 `59855a4cfac73e7dd2a4b54032ff5c42`) — the subject |
| `scratch/coredump_live/modem_coredump_up919.52.elf` | the **stock** coredump (85 398 475 B) — the control's resolver |
| `scratch/uz801_fw/modem.elf` | the joined UZ801 ELF (50 912 492 B, 27 phdrs) — the segment-map source |
| `GitIgnore/MelbonWhiteStock_Dump/modem_extracted/image/modem.elf` | the stock ELF — the table source |
| `Docs/Modem Stability/217_…SEG26_CONTENTS.md` | the capture and seg[26] read that this document builds on |
