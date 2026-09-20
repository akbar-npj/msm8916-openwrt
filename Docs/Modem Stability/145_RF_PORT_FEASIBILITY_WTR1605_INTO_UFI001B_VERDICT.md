# Engineering Report 145: RF Transceiver Port Feasibility — Why HMU05's WTR1605 Driver Cannot Be Transplanted Into UFI001B, and What the Measurements Actually Show

**Document ID:** `145_RF_PORT_FEASIBILITY_WTR1605_INTO_UFI001B_VERDICT.md`
**Date:** 2026-09-20
**Device:** Melbon HMU05 4G USB Dongle (MSM8916 + WTR1605 + QFE2320), OpenWrt 25.12.5 / Linux 6.12.94
**Router:** `192.168.8.1` via USB Ethernet `enu1i2` (gadget MAC randomised every boot)
**Status:** The directive *"port the HMU05 RF transceiver driver into UFI001B"* was pursued to a **quantitative verdict**: it is **not achievable by binary means**, and the reason is now measured rather than asserted. Two earlier beliefs are corrected: (a) the card-class names are *not* unreferenced — the pointer tables were simply missed by a 4-byte-aligned scan; (b) the correct port target is not `b18` at all but the **card-pack module** that UFI001B has and HMU05 does not.
**Predecessors:** Doc 141 (port soak), Doc 142 (b26 residency), Doc 143 (disjoint RF driver sets), Doc 144 (RF verdict confirmed; bam-dmux RTNL oops)
**Nature of work:** **Static analysis only.** No firmware, kernel, or device state was modified in producing this report.

---

## 1. Purpose and Scope

The standing directive from the project owner is unconditional:

> *"do, whatever it takes to port rf transceiver of hmu05 to ufi001b"*

with the stated motivation that UFI001B "does not have the 15 minute crash or data stall". Doc 143 had already shown that the two firmwares ship **disjoint transceiver driver sets** (HMU05: WTR1605 only; UFI001B: WTR4905 only). What was missing was a *cost* for closing that gap. Doc 144 §15 sketched a feasibility analysis and identified "relocation" as the suspected blocker, but could not size it.

This report sizes it. The work was:

1. Re-derive the reference mechanism for RFC card classes, because Doc 144 §15.5 recorded an anomalous probe result (`0/102` class names referenced by a 4-byte pointer) that would have invalidated any relocation estimate.
2. Measure the relocation cost of moving HMU05's `modem.b18` (the segment holding the WTR1605 driver) into the UFI001B image.
3. Measure the **cross-build coupling** — how many distinct addresses the transplanted code would have to resolve against a framework it was not compiled for.
4. Compare the two firmwares' *module architecture*, which turned out to be the decisive difference.

**Headline result:** moving the code is expensive but mechanical (~39,000 address fixups). Resolving what the code *calls* is not mechanical at all — **43,296 distinct outbound targets**, against a build whose main code segment shares only **5.5 %** of its 64-byte windows with the source build. The port as conceived is a firmware merge, not a patch.

---

## 2. SOP Compliance Statement

Per the mandatory dual-firmware comparative protocol (Doc 133 §1, modelled by Doc 134 §2):

| SOP step | Performed? | How |
| :--- | :--- | :--- |
| **1. Backup & version control** | **Yes** | All analysis ran against **hash-verified local extractions**, never against a live image: HMU05 `modem.mdt` = `1a6f9507e03d4ddbbf1977af81ecdbd7` (`GitIgnore/compare/modem_hmu05_extracted/image/`, 51 files); UFI001B = `56259b76da608d124a3f32b7a4bcddee` (`GitIgnore/compare/modem_ufi001b_extracted/image/`, 61 files). The alternative UFI001B tree (`72ae7f0b…`, the patched build) was identified and **not** mixed in. |
| **2. Ground-truth verification** | **Yes** | Every structural claim is a measurement over the raw segment bytes. Where a prior claim conflicted with a measurement (the `0/102` reference anomaly), the measurement won and the earlier claim was corrected (§5). |
| **3. Dual-firmware comparison** | **Yes** | Every metric was computed for **both** firmwares with the same tool and the same parameters — the segment maps (§6.1), the class inventories (§6.2), the card→framework interface size (§7.3), and the code-similarity probe (§8.2). |
| **4. No blind patching** | **Yes** | **Nothing was patched, flashed, or written to the device.** This report is static analysis plus tooling. No hypothesis was implemented before being measured. |
| **5. Error control** | **Yes** | Two prior errors were found and corrected: the `0/102` "no references" claim (§5, cause: a 4-byte-aligned scan missing 2-byte-aligned tables) and the `modem.asm`-based xref that silently returned zero because the prebuilt disassembly **stops at `PT_LOAD#16`** and never covered `b18` (§4.3). Both are recorded as traps in §9. |
| **6. Reproducibility** | **Yes** | All seven tools are committed under `scratch/firmware/` (§10) and each is a self-contained script taking an image directory and `mpss_phys` on the command line. |

---

## 3. Executive Summary

- **The port is not a relocation exercise; it is a cross-build firmware merge.** HMU05's `modem.b18` contains **43,296 distinct outbound targets** (4-byte-aligned, chance-corrected), of which **36,412 are in `modem.b16` alone**. Those are calls and data references into a *different build* of the modem framework.
- **The two builds do not share their code.** Sampling `modem.b16` (HMU05, 18.3 MB) against `modem.b15` (UFI001B, 17.7 MB): only **15.2 %** of 16-byte windows, **9.1 %** of 32-byte windows and **5.5 %** of 64-byte windows appear anywhere in the host image. Content-based rebinding across builds is therefore not viable.
- **The architectures differ in a way that is decisive, and that no earlier document noticed.** UFI001B (2021) factors its card drivers into a **separate 1 MB module — `modem.b26`** — holding all 145 `rfc_wtr4905_*` classes, which reference only **19 distinct framework addresses**. HMU05 (2015) has no such module: its 102 `rfc_wtr1605_chile_*` classes live **inside `b18` alongside the framework**, and their tables reference **1,635 distinct `b16` addresses**. A ~19-entry plugin ABI and a ~1,635-entry inline coupling are not interchangeable.
- **The earlier "0/102 references" anomaly is explained and retracted.** Card class names *are* referenced: each is immediately followed by a C++ `type_info`-style record whose `__name` field holds `name_va − 2`. These tables are **2-byte aligned**, so the 4-byte-aligned scan used in Doc 144 §15.5 skipped every entry.
- **Relocation cost, for the record:** moving `b18` to a new VA requires **31,274** 4-byte-aligned self-fixups (9.3× the chance rate) plus **7,775** confirmed inbound fixups, and a further **17,560** 2-mod-4 self-words. Inbound is dominated by `b19` (4,972) and `b22` (2,803).
- **What would actually be required:** compiling the WTR1605 card driver against UFI001B's framework, i.e. the MPSS source for both. Neither is public. §11 lists the alternatives that remain.
- **New live observation (§12.2):** the `a2_power.c:1189` fatal **recurs on a ~905 s period** (918.195 s, then 1823.753 s), and each occurrence is recovered by SSR with the data path restored. A constant period points at a counter/timer wrap. This boot's 900 s event is *recoverable*; the non-recovering symptom is the separate AP-side RTNL leak of Doc 144 §8.

> **Practical consequence.** The RF port cannot be delivered by patching binaries, and no amount of further binary analysis will change that. Effort should return to the two lines that *can* move: the **AP-side** faults (which Doc 144 §8 and the Android differential both localise), and a **premise check** on whether UFI001B really lacks the 900 s fault (§11.1).

---

## 4. Method and Tooling

### 4.1 Address model

Both firmwares use the loader model already encoded in `scratch/firmware/mdt.py`: for each valid program header, `offset = p_paddr − mpss_reloc` and the body is written to `mpss_phys + offset`. `mpss_phys = 0x86800000` for both images.

A useful property fell out of the measurement: **the two images' virtual addresses are directly comparable.** HMU05 is non-relocatable in the loader's sense (`p_vaddr ≠ p_paddr`; `p_paddr` is already the final physical address), while UFI001B is relocatable (`p_vaddr == p_paddr`, rebased by `0xc0000000`). But both express *code addresses* in the same `0xc0000000` space, so `HMU05 b18 @ 0xc1500000` can be compared to `UFI001B b17 @ 0xc1480000` without adjustment.

### 4.2 Disassembly

`modem.elf` (`GitIgnore/MelbonWhiteStock_Dump/modem_extracted/image/`) does contain `b18` — as `phdr[18]`, `vaddr=0xc1500000`, `filesz=0x73ace0`. But its **section header table stops at `PT_LOAD#16`** (`llvm-objdump -h` lists only 9 sections), so `llvm-objdump --start-address=0xc1500000` fails with *"no section overlaps the range"*.

`scratch/firmware/mkelf.py` works around this by wrapping any raw `modem.bNN` in a minimal one-section `elf32-hexagon`:

```
python3 scratch/firmware/mkelf.py <in.bin> <out.elf> <vaddr> [paddr]
llvm-objdump -d scratch/elfwrap/hmu_b18.elf
```

This is required for the whole relocation study, not just this report.

### 4.3 A trap that invalidated an earlier result

`scratch/firmware/xref.py` and `rangerefs.py` grep the prebuilt `scratch/firmware/modem.asm`. That file is **202 MB and covers only `PT_LOAD#2` … `PT_LOAD#16`** — the disassembly run stopped early. A query for the class-name VAs therefore returned `0 refs` for every target, which is *not* evidence of absence. This is recorded in §9 so it is not repeated.

---

## 5. Corrected Finding: How Card Classes Are Actually Referenced

Doc 144 §15.5 recorded that **0 of 102** `rfc_wtr1605_chile_*` names in `b18` were referenced by a 4-byte pointer. That result was an artefact of the scan, not a property of the image.

### 5.1 The actual structure

Raw bytes around `rfc_wtr1605_chile_rf360_lte_ag` in HMU05 `modem.b18`:

```
0xc1c12922  "rfc_wtr1605_chile_rf360_lte_ag\0"
0xc1c12942  b8 a7 c3 c1   -> 0xc1c3a7b8      shared RTTI vptr
0xc1c12946  20 29 c1 c1   -> 0xc1c12920      == name_va - 2
0xc1c1294a  48 cc b3 c1   -> 0xc1b3cc48
0xc1c1294e  00 00 00 00
0xc1c12952  48 2c c1 c1   -> 0xc1c12c48
0xc1c12956  24 b6 18 c1   -> 0xc118b624
```

Each `_ag` class name is immediately followed by a `type_info`-style record `{ vptr, __name, … }` and then its vtable. Three properties caused the miss:

1. The record begins at a **2-byte-aligned, not 4-byte-aligned, offset** — each name is NUL-padded to a 2-byte boundary first. A `range(0, len, 4)` scan skips every entry.
2. The self-reference is `name_va − 2`, not `name_va − 1`, `name_va`, or `name_va + 1` — the three variants Doc 144 tested.
3. `0xc1c12942` is 2 mod 4, so consecutive words alternate between 4-aligned and 2-mod-4 values; a naive 4-aligned filter discards roughly half of every table.

### 5.2 Corrected counts

`scratch/firmware/rfc_refs3.py` (2-byte granularity, `va−2` variant):

| variant (value = name ± n) | sites | owner segments |
| :--- | ---: | :--- |
| `name − 2` | **92** | `b18`: 89, `b16`: 3 |
| `name + 0` | 35 | `b18`: 35 |
| `name − 1` | 0 | — |
| `name + 1` | 0 | — |
| `name + 2` | 3 | `b15`: 1, `b16`: 2 |

The shared RTTI vptr is a single address referenced once per class: **HMU05 `0xc1c3a7b8` ×142**, **UFI001B `0xc1bd2c08` ×145**. The ×145 is exactly the card count — a clean confirmation that the record layout is one-per-card-class in both builds.

---

## 6. The Architectural Difference

### 6.1 Segment maps

| | HMU05 (2015) | | UFI001B (2021) |
| :--- | ---: | :--- | ---: |
| main code | `b16` @ `0xc0287000`, 18.34 MB | | `b15` @ `0xc02c2000`, 17.75 MB |
| **RFC subsystem** | **`b18` @ `0xc1500000`, 7.23 MB** | | **`b17` @ `0xc1480000`, 7.32 MB** |
| **card pack** | *(does not exist)* | | **`b26` @ `0xc3e0f000`, 1.00 MB** |
| other | `b15` 1.66 MB, `b19` 3.08 MB, `b24` 8.40 MB | | `b14` 1.58 MB, `b18` 2.81 MB, `b23` 9.85 MB |

Both RFC subsystems sit at almost the same VA (`0xc1500000` vs `0xc1480000`) and are almost the same size — which is exactly why "relocate `b18` into UFI001B" looked plausible. UFI001B's `b17` occupies `0xc1480000`–`0xc1bd3100`, overlapping HMU05's `b18` range `0xc1500000`–`0xc1c3ace0`.

### 6.2 Class inventories

`scratch/firmware/rfc_inventory.py`:

| | HMU05 | UFI001B |
| :--- | :--- | :--- |
| distinct `rfc_*` class names | 162 | 192 |
| card classes | 102 `rfc_wtr1605_chile_*` (all in `b18`) | 145 `rfc_wtr4905_*` (all in `b26`) |
| framework classes | 60 (all in `b18`) | 38 (all in `b17`) |
| other card families | 5 `rfc_wtr2605_3g_*` (in `b18`) | — |

**HMU05 has no card pack.** Framework and cards are compiled together into one segment. UFI001B separates them. This is the whole story.

### 6.3 What a "card pack" is

UFI001B's `b26` disassembles cleanly as Hexagon code (`llvm-objdump` via `mkelf.py`), and `scratch/firmware/dep_surface.py` gives its complete external contract:

```
b26: 19 distinct outbound targets, 521 refs
  b15 (framework)  11 distinct, 231 refs
  b17 (framework)   8 distinct, 290 refs

  0xc1bd2c08 (b17) x145   <- shared RTTI vptr, once per card class
  ... 18 more, each x16..x22
```

**Nineteen targets.** That is a plugin ABI: a card pack can be swapped without touching the framework. HMU05's equivalent measurement, restricted to the 102 WTR1605 card tables in `b18` (`scratch/firmware/card_abi.py`), gives:

```
102 'rfc_wtr1605_chile_*' entries, 168,002 bytes of records
distinct pointer-like targets: 2194
  b16: 1635 distinct (2059 refs)
  b18:  529 distinct ( 974 refs)
  b19:   12 distinct, b04: 9, b24: 3, ...
```

**1,635 distinct `b16` targets versus 19.** Two orders of magnitude. HMU05's card code is not a module; it reaches directly into framework internals and RF parameter tables.

---

## 7. Relocation Cost

`scratch/firmware/reloc_scope3.py` (2-byte granularity; a word counts as a real address only if it is 4-byte aligned, with a per-segment chance model `expected = nwords × target_size / 2³²`).

Target `b18` = 7.23 MB ⇒ `p_chance = 0.001765` per word.

### 7.1 Inbound references (other segments → `b18`)

| src | 4-aligned words | expected | observed | 4-aligned | ratio | verdict |
| :--- | ---: | ---: | ---: | ---: | ---: | :--- |
| `b16` | 4,585,348 | 8,093 | 38,390 | 16,913 | 2.09 | ambiguous |
| `b19` | 769,714 | 1,359 | 6,889 | **4,972** | 3.66 | **REAL** |
| `b22` | 18,952 | 33 | 2,878 | **2,803** | 83.79 | **REAL** |
| `b24` | 2,099,135 | 3,705 | 8,279 | 2,425 | 0.65 | chance |
| `b15` | 415,990 | 734 | 2,735 | 1,167 | 1.59 | ambiguous |
| others | — | — | — | <200 each | <1.2 | chance |

### 7.2 Self-references inside `b18`

| class | count |
| :--- | ---: |
| 4-byte aligned | **31,274** (chance 3,345 ⇒ **9.3×**) |
| 2 mod 4 | 17,560 |
| distinct self targets | 10,215 |

### 7.3 Relocation total

| | fixups |
| :--- | ---: |
| self, 4-aligned | 31,274 |
| inbound, confirmed (`b19` + `b22`) | 7,775 |
| **confirmed total** | **39,049** |
| additional if `b16`/`b15` inbound is real | up to +18,080 |
| outbound refs that must still *resolve* | **69,258** (44,536 distinct) |

Two cautions on the self-ref figure. Roughly 3,345 of the 31,274 are expected to be coincidental words that merely *look* like addresses; rebasing them would corrupt data. A blind `if lo <= w < hi: w += delta` pass would therefore corrupt on the order of 10 % of the words it touched. Any real relocation needs a validity filter (e.g. "points at a 4-byte instruction boundary") that this report does not yet have.

---

## 8. Why Relocation Is Not the Real Problem

### 8.1 The outbound contract

`b18`'s own references are the blocker, and they are large:

| destination | distinct targets | refs |
| :--- | ---: | ---: |
| `b16` (main code) | **36,412** | 55,084 |
| `b25` | 4,261 | 4,649 |
| `b15` | 1,189 | 1,725 |
| `b19` | 1,075 | 3,313 |
| `b24` | 117 | 937 |
| others | <70 each | |
| **total** | **43,296** | 66,655 |

Every one of these is an address in the *HMU05* image. In the UFI001B image the same VA is either empty or holds different code. Resolving 43,296 references against a framework you did not compile against is not a fixup — it is a rebuild.

> **Note on the two figures.** This table is from `dep_surface.py`, which scans 4-byte-aligned
> offsets only, and gives **43,296** distinct targets. `reloc_scope3.py` scans 2-byte-aligned
> offsets (required by §5) and gives **44,536** distinct targets — the extra ~1,240 are references
> stored at 2-mod-4 offsets. Both are correct for their granularity; 44,536 is the better lower
> bound.

### 8.2 The builds do not share their code

Sampling 3,000 random windows from HMU05 `modem.b16` and searching for them anywhere in UFI001B `modem.b15`:

| window | present in host |
| :--- | ---: |
| 16 B | 457 / 3000 = **15.2 %** |
| 32 B | 272 / 3000 = **9.1 %** |
| 64 B | 164 / 3000 = **5.5 %** |

By contrast the small early segments are *identical*: `b03` 100 % byte-identical at the same offset, `b04` 100 % (139,616 of 139,616 bytes), `b02` 49 % (sizes differ). So the two firmwares share a boot stage and diverge completely in the modem body.

This closes the last escape hatch. A "match functions by content across builds" strategy would have to match 43,296 references against an image that shares 5.5 % of its 64-byte windows — i.e. essentially none of the relevant code.

---

## 9. Traps Recorded (do not repeat)

1. **`scratch/firmware/modem.asm` covers only `PT_LOAD#2`…`PT_LOAD#16`.** It is the HMU05 disassembly and it **does not include `b18`** (or `b17`, `b19`, `b24`, `b25`). Any `xref.py` / `rangerefs.py` query for an address ≥ `0xc1404e10` returns zero *by construction*. Use `mkelf.py` + `llvm-objdump` for those segments.
2. **Pointer tables in modem `.rodata` are 2-byte aligned, not 4-byte aligned.** Class names are NUL-padded to 2 before their `type_info` record. A `step=4` scan finds nothing.
3. **The RTTI `__name` field points at `name_va − 2`.** Not `name_va`. Testing only `±1` and `0` yields a false "no references" result.
4. **Raw counts of "words that look like addresses" are meaningless without a chance model.** For a 7.23 MB target range the chance rate is 0.18 % per word; over a 12 M-word image that is ~21,000 coincidences. Every count in this report is accompanied by its expected value.
5. **Do not use `modem.elf`'s section headers to bound a disassembly.** They stop at `PT_LOAD#16` even though the program headers continue to 25.

---

## 10. Artifacts

| path | purpose |
| :--- | :--- |
| `scratch/firmware/mkelf.py` | wrap a raw `modem.bNN` in a one-section `elf32-hexagon` for `llvm-objdump` |
| `scratch/firmware/rfc_inventory.py` | per-segment `rfc_*` class inventory for both firmwares |
| `scratch/firmware/rfc_refs.py` | first (flawed) reference probe — kept for the record |
| `scratch/firmware/rfc_ref2.py` | variant/offset scan that isolated the failure mode |
| `scratch/firmware/rfc_refs3.py` | **corrected** probe: 2-byte granularity, `name−2` variant |
| `scratch/firmware/reloc_scope.py` | first relocation pass (4-byte aligned) |
| `scratch/firmware/reloc_scope2.py` | adds the chance model |
| `scratch/firmware/reloc_scope3.py` | **corrected** relocation measurement (2-byte granularity) |
| `scratch/firmware/dep_surface.py` | outbound dependency surface of a segment, with host-presence check |
| `scratch/firmware/card_iface.py` | pointer targets emanating from a rodata region |
| `scratch/firmware/card_abi.py` | outbound surface of one class family's `type_info`/vtable records |
| `scratch/elfwrap/hmu_b18.elf`, `ufi_b26.elf` | generated wrappers |

Source images: HMU05 `GitIgnore/compare/modem_hmu05_extracted/image/` (`1a6f9507…`); UFI001B `GitIgnore/compare/modem_ufi001b_extracted/image/` (`56259b76…`).

---

## 11. What Remains Possible

### 11.1 Verify the premise before spending more (highest value)

The port's stated justification is that UFI001B "does not have the 15 minute crash or data stall". **That premise has never been tested**, because UFI001B has no RF (Doc 143, Doc 144 §5–§7) and therefore never attaches to LTE. Doc 144 §15.5 already flagged this.

It matters because the evidence points the other way:

- The modem firmware is **byte-identical between Android and OpenWrt** for HMU05, and the modem does **not** crash at 900 s under Android while it does under OpenWrt (Doc 144, Android differential).
- The 900 s fault is a **fatal error triggered by AP-side behaviour**, with the pre-crash collapse confined to the LTE stack.

If the fault is AP-triggered, swapping basebands is not a principled fix; the trigger follows the AP. The cheapest decisive test is therefore not a port but a **targeted re-examination of what the AP does differently at ~300 DRX cycles / ~900 s**, which is already the subject of Doc 144 §8 and the DIAG captures.

### 11.2 Targeted function-level port (if the premise survives)

If a specific UFI001B module *is* more robust, the transferable unit is a **function**, not a driver. The two builds share their early segments byte-for-byte and 5.5 % of their 64-byte windows in the main code, so a function whose HMU05 and UFI001B copies are close can be identified and patched under the existing SOP (VA verification, disassembly, hash comparison against stock). The candidates already named in the project record — `a2_power.c:1189`, the MCPM watchdog path, the Q6 power-collapse vote — are the places to look.

### 11.3 AP-side fixes (unblocked, already scoped)

Independent of any baseband question, Doc 144 §8 documents a reproducible AP-side oops that permanently freezes the network stack: `wwan0` down → NULL `skb_dma->dmux` → oops under RTNL → every later `ip` hangs → reboot required. That is a real bug in this tree with a known fix shape and no dependency on the RF question.

### 11.4 Not viable

- **Binary transplant of `b18`** — §7, §8.
- **Card-pack synthesis by lifting HMU05's card code** — HMU05's card tables are coupled to 1,635 `b16` addresses; UFI001B's pack expects a 19-entry ABI. §6.3.
- **Rebuilding UFI001B with WTR1605 support from source** — requires the MSM8916 MPSS source for both builds. Not public.
- **Driving WTR1605 silicon with UFI001B's WTR4905 driver** — different transceiver generation and register programming; the driver sets are disjoint by construction (Doc 143).

### 11.5 Worth one search

If a **third** firmware exists with UFI001B's card-pack architecture (a 2020-2022 MPSS 2.0 build) *and* WTR1605 card classes, its card pack would be drop-in by construction. This is a bounded search — a handful of candidate device firmwares, each checked with `rfc_inventory.py` for a `b26`-shaped segment containing `rfc_wtr1605_*` — and it is the only path that preserves the original goal without source.

---

## 12. Device State, and a New Live Observation

### 12.1 State at time of writing

Unchanged. Stock baseband deployed (`modem.mdt` `1a6f9507e03d4ddbbf1977af81ecdbd7`), `registered`, `wwan0` up with `10.99.112.26/30`. No firmware, kernel, or configuration was modified in producing this report; all analysis was performed on local extractions.

### 12.2 The `a2_power.c:1189` fault is periodic (new)

A read-only `dmesg` check during this session found **three** modem fatals in one boot:

| t (s) | fault | recovery |
| ---: | :--- | :--- |
| 12.037 | `coex_interface.c:530` (the WCNSS mismatch, Doc 144 §15.7) | SSR |
| 918.195 | **`a2_power.c:1189`** | SSR, `bam_dmux` channels re-initialised, data restored |
| 1823.753 | **`a2_power.c:1189`** | SSR, data restored |

Interval between the two `a2_power` faults: **905.56 s** (from powerup completion at 919.078 s to the next fatal: 904.68 s). Uptime at the time of the check: 1868 s, `wwan0` up.

**Why this matters.** The fault is not a one-off corruption — it **recurs on a roughly constant ~905 s period**, and each occurrence is recovered by SSR with the data path coming back. A constant period points at a **counter or timer wrap** rather than a random state corruption, which is consistent with the existing root-cause note (Q6 power-collapse vote failing after a fixed number of DRX cycles) and with the MCPM-watchdog characterisation.

**Caveats.** n = 2 intervals, from a single boot. The period is ~905 s, not exactly 900 s, and the first interval (measured from boot, 918.2 s) differs from the second (measured from powerup, 904.7 s), so the anchor is not yet pinned. This is recorded as an observation, not a conclusion; a longer capture would settle whether the period is fixed and what it is measured from.

**Also note:** this boot's "900 s event" is *recoverable*. The non-recovering symptom the owner reports is the separate AP-side RTNL leak of Doc 144 §8, which SSR cannot fix. The two should not be conflated.

---

## 13. Open Questions

1. Is the 900 s fault reproducible on UFI001B at all? Untestable until RF works or until an equivalent AP-side stimulus without LTE is found. (§11.1)
2. Do the 16,913 `b16` → `b18` 4-aligned hits represent real inbound references (ratio 2.09) or coincidence? They do not change the verdict, but they would raise the relocation floor. (§7.1)
3. Is a relocation validity filter ("points at an instruction boundary") sufficient to bring the ~10 % self-ref false-positive rate to zero? Only matters if §11.5 succeeds.
