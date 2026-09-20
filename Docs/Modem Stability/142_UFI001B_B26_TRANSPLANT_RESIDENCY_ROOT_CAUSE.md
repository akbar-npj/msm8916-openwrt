# Engineering Report 142: UFI001B `modem.b26` Transplant Residency — Root Cause Closed

**Device:** Melbon HMU05 (MSM8916 + WTR1605 + QFE2320), OpenWrt 25.12.5 / Linux 6.12.94
**Router:** `192.168.8.1` (USB Ethernet `enu1i2`)
**Date:** 2026-09-20
**Subject:** Why RF table-transplant patches 41–53 produced zero LTE — definitively resolved
**Supersedes / closes:** `project_b26_transplant_not_resident.md` §"Open question"

---

## 1. Purpose and Scope

Report 118 §"uninitialized BSS" and the working memory note both flagged an unresolved
question: **is `modem.b26`'s content actually present at its MDT-declared address at runtime?**

Two hypotheses were live and not separated:

- **(A)** coredump addresses are *virtual*, and segment 26 is simply **not resident** where the
  MDT declares it — the region is runtime-owned and gets overwritten;
- **(B)** coredump addresses are *physical*, VA ≠ PA for segment 26 only, and the transplant
  **is** reachable — the LTE failure lies elsewhere.

This report closes that question with three independent lines of evidence. **Hypothesis (A) is
confirmed. The transplant never reaches the modem.**

Scope: read-only measurement of the live device plus offline analysis of the kernel source,
the MDT images, and the eight archived coredumps. One live QMI mode-preference change was made
(§9) to reproduce the known LTE bring-up hang; no firmware was modified.

---

## 2. SOP Compliance Statement

Per the mandatory dual-firmware comparative protocol (Doc 133 §1, modelled by Doc 134 §2):

- **Backup / version control** — the live firmware was identified by MD5 against
  `GitIgnore/compare/modem_ufi001b_patched/image/` **before** any analysis, and matched 4/4
  (`modem.b26` = `258d8da88bad78180ebbbb62e1cd25eb`, `modem.mdt` = `72ae7f0bfa910873739a409f94910cdd`,
  `modem.b15` = `c69ad8003476146f1263021638dff356`, `modem.b17` = `d0c62e87a15e47c4e0d2a7f5af9ead06`).
  No image was rebuilt, re-signed, or written.
- **Ground-truth verification** — every VA-level claim was re-derived from the kernel source
  (§3) *and* re-measured on the live device with an independent method (`/dev/mem` + `devmem`,
  §5), not inferred from a single artifact.
- **Dual-firmware comparison** — every measurement was paired against the stock HMU05 working
  state (`modem_hmu05_live_connected.elf`, §7), which is the required control.
- **No blind patching** — no firmware was modified in this session. No new transplant was attempted.
- **Error control** — a defect in the first version of the new analysis tool was found and fixed
  (§10.1), and a version-skew methodology error from the previous session was corrected (§10.2).

> [!IMPORTANT]
> **One process deviation, recorded rather than hidden:** the previous session's approach of
> comparing a *patch-49 coredump* against a *patch-52+ deployed image* was invalid. Re-running
> with matched pairs (§6) changed the headline number from "602/8340" to a clean result. The
> corrected pairing is what is reported here.

---

## 3. The Address Model — Proven From Kernel Source

This is the foundation. Both the loader and the coredump writer use the **same** formula.

**Definitions** (`include/linux/soc/qcom/mdt_loader.h:9`):

```c
#define QCOM_MDT_RELOCATABLE	BIT(27)
```

**Loader** (`drivers/remoteproc/qcom_q6v5_mss.c`):

| Line | Code | Meaning |
| :--- | :--- | :--- |
| `:1380-1381` | `if (phdr->p_flags & QCOM_MDT_RELOCATABLE) relocate = true;` | any valid phdr sets relocate |
| `:1383-1384` | `if (phdr->p_paddr < min_addr) min_addr = phdr->p_paddr;` | `min_addr` = lowest **`p_paddr`** |
| `:1416` | `mpss_reloc = relocate ? min_addr : qproc->mpss_phys;` | relocation base |
| `:1425` | `offset = phdr->p_paddr - mpss_reloc;` | **`p_paddr`**, never `p_vaddr` |
| `:1440` | `ptr = memremap(qproc->mpss_phys + offset, phdr->p_memsz, MEMREMAP_WC);` | where the body is written |

**Coredump writer** (`drivers/remoteproc/qcom_q6v5_mss.c` + `remoteproc_coredump.c`):

| Line | Code | Meaning |
| :--- | :--- | :--- |
| `qcom_q6v5_mss.c:1665` | `rproc_coredump_add_custom_segment(rproc, phdr->p_paddr, phdr->p_memsz, …)` | the dump's `da` **is** `p_paddr` |
| `qcom_q6v5_mss.c:1539` | `int offset = segment->da - qproc->mpss_reloc;` | same subtraction as the loader |
| `qcom_q6v5_mss.c:1555` | `ptr = memremap(qproc->mpss_phys + offset + cp_offset, size, MEMREMAP_WC);` | `+ cp_offset` |
| `remoteproc_coredump.c:160` | `segment->dump(rproc, segment, dest, offset, size);` | `cp_offset` ← this `offset` |
| `remoteproc_coredump.c:212` | `rproc_copy_segment(rproc, buffer, seg, seg->size - seg_data, copy_sz);` | `offset` = **within-segment read position** |

> **The `cp_offset` term is not an address bias.** It is the position of the chunk being read
> inside the segment, supplied by the streaming coredump reader. The dump therefore maps modem
> address `da` → physical `mpss_phys + (da − mpss_reloc)` — **byte-for-byte the same expression
> the loader used to write it.**

**Conclusion:** the coredump is a faithful read-back. Hypothesis (B) is eliminated on first
principles. Whatever the loader wrote, the dump reads back.

---

## 4. Numeric Instantiation

### 4.1 UFI001B

`modem.mdt` (8284 B = `modem.b00` 948 + `modem.b01` 7336), ELF32, 28 phdrs, **22 valid**
(`q6v5_phdr_valid`: `PT_LOAD`, not `QCOM_MDT_TYPE_HASH`, `p_memsz != 0`).

- Every valid phdr carries `BIT(27)` (`p_flags` = `0x8000005`/`…6`/`…7`/`…4`) ⇒ **`relocate = true`**.
- `min_addr = 0xc0000000` (phdr 2, `modem.b02`).
- `mpss_phys = 0x86800000` — DT `reserved-memory/mpss@86800000`, `no-map`; size `0x5500000`
  (85 MB). Corroborated by `/proc/iomem`, which shows the hole
  `86000000–8bcfffff` between `System RAM 80000000–85ffffff` and `8bd00000–8dbfffff`.

$$\text{phys}(da) = 0x86800000 + (da - 0xc0000000)$$

Segment 26 (`modem.b26`): `p_vaddr = p_paddr = 0xc3e0f000`, `p_filesz = p_memsz = 0x100000`
⇒ loader writes to **`0x8a60f000`**.

### 4.2 HMU05 (control)

`modem.mdt` (8220 B), ELF32, 27 phdrs, **21 valid**. **`p_vaddr ≠ p_paddr`** — HMU05's MDT
carries explicit physical addresses (`phdr 2`: va `0xc0000000`, pa `0x86800000`).
`min_addr = 0x86800000 = mpss_phys`, so `mpss_reloc = 0x86800000` and `phys = p_paddr`.

**HMU05 has no `modem.b26` file at all** — its phdr 26 is a `0xd1a000`-byte BSS segment at
`0xc44e6000`. The b26 phenomenon is therefore **UFI001B-specific by construction**, and the
HMU05 control must be run segment-by-segment (§7), not file-by-file.

---

## 5. Live Measurement (`/dev/mem` + `devmem`) — Independent of the Coredump

Taken on the deployed image, modem up 9 min, all four MD5s matched. `devmem <phys> 32`.

| declared VA | physical | live read (8 words) | expected | verdict |
| :--- | :--- | :--- | :--- | :--- |
| `0xc3e0f000` | `0x8a60f000` | `210032BE 00000070 C0DAC2FC 00000034 C199A0F0 00000051 00000000 6366722F` | `b26[0:32]` = `70604011 EBF41C10 …` | **MISMATCH** |
| `0xc4764000` | `0x8AF64000` | `70604011 EBF41C10 78004180 0C3442C2 49804590 2442E012 5A00C010 5A004012` | `b26[0:32]` | **MATCH** |
| `0xc3eef000` | `0x8a6ef000` | `00000000 ×8` | transplanted table `DD 02 29 02 …` | **ABSENT** |
| `0xc4844000` | `0x8B044000` | `00000000 ×8` | transplanted table (relocated home) | **ABSENT** |
| `0xc3eef468` | `0x8a6f2468` | `00000000 ×8` | table `[0x3468]` = `DD 02 29 02 …` | **ABSENT** |
| `0xc02c2000` | `0x86ac2000` | `28102802 7F004000 0C296E0E AB81C0A4 …` | `b15[0:4]` = `28102802` | **MATCH** (control) |

**Reading of the row 1 structure.** It is not code. It is a runtime descriptor table whose
fields are `{0x210032BE, 0x70, 0xC0DAC2FC (b15 code ptr), 0x34, 0xC199A0F0 (b18 data ptr), 0x51,
0x0, "/rfc/0081/tdscdma/tx5_tdscdma_05_device_info.dat"}`, followed by further
`{id, flags, code-ptr, 0x34, data-ptr}` records and a run of `FFFFFFFF 00000000` pairs.
The firmware is **building its own RF-config registry in the b26 region at runtime.**

The last row is the decisive control: **b15's code *is* at its declared address**, so the
router patch itself landed correctly. Only the data host is missing.

---

## 6. Coredump Statistics — Matched Pairs Only

Comparing a dump against the image that produced it is mandatory. Pairs used:
`p49↔patch49_backup`, `p53↔patch53_backup`.

### 6.1 The headline number was a zero-matching artifact

At the declared VA `0xc3e0f000`, `modem.b26` vs dump:

| metric | value |
| :--- | :--- |
| file zero bytes | 725,869 / 1,048,576 (69.2 %) |
| file **non-zero** bytes | 322,707 |
| of those, **matching** in the dump | **675** |
| of those, dump byte is `0x00` | **289,368** |
| other mismatch | 32,664 |
| naive byte match | 680,024 / 1,048,576 (65 %) |

The 65 % "match" is **almost entirely zero-versus-zero**. Only 675 of 322,707 meaningful bytes
survive. **Controls at their declared VAs, same dump, same method:**

| segment | permission | non-zero bytes matching |
| :--- | :--- | :--- |
| `modem.b22` | R only | **434,108 / 434,108 (100 %)** |
| `modem.b17` | R only | **6,253,700 / 6,253,700 (100 %)** |

### 6.2 Every segment except b26 lands where declared

Searching the dump for each segment's first 64 bytes:

- **21 of 22** segments are found **exactly at their declared VA**.
- **`modem.b26` is the sole exception** — found at **`0xc4764000`**.

### 6.3 Deterministic across all eight coredumps

| coredump | `b26[0:64]` found at | transplanted table found |
| :--- | :--- | :--- |
| p27, p28r, p29, p49, p49b, p53, p55, p56 | **`0xc4764000` (all eight)** | **NOWHERE (all eight)** |

Because this holds in `p27` — captured **before** any table transplant — the displacement is
**inherent UFI001B firmware behaviour**, not a side effect of patching.

### 6.4 Only 40 KB survives

Byte-map of the dump at `0xc4764000` versus the file, per 64 KB:

| block | non-zero bytes matching |
| :--- | ---: |
| `0x00000` | **34,538 / 38,979** (first divergence at `0xa000`) |
| `0x10000` … `0xfffff` | ≤ 235 each (15 blocks) |

**`b26[0:0xA000]` is resident at `0xc4764000`; `b26[0xA000:]` is not present anywhere.**

Note `0xc4764000` lies **inside segment 27's declared BSS range** (`0xc3f10000 + 0x15f0000 =
0xc5500000`). The firmware has placed code/data into a region the MDT describes as BSS.

---

## 7. HMU05 Control — The Working Firmware

| segment | declared PA | non-zero match |
| :--- | :--- | ---: |
| `modem.b02` | `0x86800000` | 2,860 / 2,860 |
| `modem.b05` | `0x86830000` | 42,835 / 42,835 |
| `modem.b08` | `0x86844000` | 117,430 / 117,430 |
| `modem.b10` | `0x86874000` | 24,531 / 24,531 |
| `modem.b11` | `0x86898000` | 28,345 / 28,345 |
| `modem.b13` | `0x868a8000` | 58,790 / 58,790 |
| `modem.b14` | `0x868c8000` | 105,581 / 105,581 |
| `modem.b15` | `0x868f0000` | 1,516,742 / 1,516,742 |
| `modem.b16` | `0x86a87000` | 16,637,437 / 16,637,437 |
| `modem.b18` | `0x87d00000` | 6,086,542 / 6,086,542 |
| `modem.b19` | `0x8843c000` | 1,229,975 / 1,286,249 (95.6 % — RW data) |
| `modem.b22` | `0x8a409000` | 43,481 / 43,554 (99.8 % — RW data) |
| `modem.b23` | `0x8a41c000` | 502,770 / 502,770 |
| `modem.b24` | `0x8a498000` | 8,257,222 / 8,257,222 |
| `modem.b25` | `0x8ac9a000` | 295,685 / 295,685 |

**Every file-backed HMU05 segment sits exactly at its declared physical address.** The only
shortfalls are two read-write *data* segments, which is expected. There is no b26-analogue
displacement anywhere in the working firmware.

---

## 8. Verdict

> **The `modem.b26` region (VA `0xc3e0f000`, 1 MB) is runtime-owned BSS/data. The UFI001B
> firmware builds its own structures there after boot. Only the first 40 KB of `modem.b26`
> survives, at VA `0xc4764000`.**
>
> **Consequently, anything written into b26's declared region — including the transplanted HMU05
> LTE RF tables at `b26+0xe0000` (VA `0xc3eef000`) — is destroyed before it can be read.**
>
> **Patches 41–53 were structurally incapable of producing LTE.** The code patches (b15/b17) do
> execute — b15 is verified present at its declared VA (§5) — but the pointers they compute
> resolve into a region the firmware has repurposed. This is a complete, sufficient explanation
> for the observed "zero LTE progress" from ~13 consecutive table-transplant patches.

This is consistent with Doc 118's own warning that these tables are *"uninitialized BSS,
dynamically populated from EFS at boot"*. The measurement now proves it.

---

## 9. Current Live State (recorded for continuity)

Immediately after the residency measurement, the modem was exercised to re-establish the
baseline:

| item | value |
| :--- | :--- |
| SIM | present, USIM `ready`, PIN1 disabled |
| `mode preference` (before) | `'umts'` (WCDMA-only) → `not-registered-searching`, radio `[0]: 'none'` |
| action | `set_umts_lte` → `SET SSP mode_pref=0x0018: SUCCESS` |
| `mode preference` (after) | unchanged behaviour; LTE radio never activates |
| result | `CID allocation failed in the CTL client: endpoint hangup` |

**This reproduces Doc 134 §6.2 exactly** (5/5 recorded occurrences). The LTE bring-up hang is
live and reproducible on the current image. Per Doc 134 §8 item 1, the correct next step is
**diagnosis, not further patching**.

---

## 10. Corrections

### 10.1 Tool defect found and fixed this session

The first version of the new analysis library (`scratch/firmware/mdt.py`) used
`QCOM_MDT_RELOCATABLE = 0x04000000`. The correct value is `BIT(27) = 0x08000000`
(`include/linux/soc/qcom/mdt_loader.h:9`). This produced a wrong `mpss_reloc`
(`0x86800000` instead of `0xc0000000`) and, with it, a wrong predicted physical address for
every UFI001B segment. Corrected before any conclusion was drawn. **The segment-comparison
results were unaffected**, because those index the coredump by its own `p_vaddr` (which is the
MDT's `p_paddr`) and do not depend on `mpss_reloc`.

### 10.2 Version-skew methodology error (previous session)

The previous session compared the **patch-49 coredump** against the **patch-52+ deployed image**,
yielding a spurious "602 / 0 / 1483" table comparison and a misleading headline. Re-running with
matched pairs (§6) gives a clean, reproducible result. All numbers in this report use matched pairs.

### 10.3 Hypothesis (B) formally retired

Hypothesis (B) — "coredump addresses are physical and VA ≠ PA for segment 26 only" — is
**disproven** by §3 (the dump uses the loader's own expression, `cp_offset` is a read position,
not a bias) and by §5 (an independent live `devmem` read shows the same displacement). It should
not be revisited.

---

## 11. Pending Work

| # | Action | Rationale |
| :--- | :--- | :--- |
| 1 | **Stop writing table transplants into `modem.b26` / VA `0xc3eef000`.** | Proven futile (§8). Any further patch of this class is wasted effort. |
| 2 | **Trace the LTE bring-up hang (diagnostic, not corrective).** | Doc 134 §8 item 1; plan is Doc 135. `diag_logtool` is **no longer on the device** and must be re-deployed from `GitIgnore/compare/diag_logtool`. |
| 3 | **Establish which RF card the firmware selects, and whether it matches HMU05.** | The runtime registry at `0xc3e0f000` carries card field **`0x51`** and path `/rfc/0081/…`. If `0081` is HMU05's WTR1605 card, RF card selection is already correct and the fault is downstream; if it is UFI001B's own card, the WTR4905→WTR1605 adaptation is incomplete. **This is the highest-value open question and is cheap to answer.** |
| 4 | **If a static data host is ever needed again, use a runtime-stable segment.** | Proven 100 % stable at declared VA: **`b17`, `b22`, `b23`** (read-only), plus `b05`, `b06`, `b08`, `b09`, `b12`, `b13`, `b14`, `b15`, `b25`. Never `b26`. |
| 5 | **Correct Doc 118 / memory if either claims b26 is a valid table host.** | It is not. |

---

## 12. Reproduction Reference

```sh
# ---- 0. Firmware identity (must match before trusting anything) ----
md5sum /lib/firmware/modem.b26 /lib/firmware/modem.mdt \
       /lib/firmware/modem.b15 /lib/firmware/modem.b17
# expect: 258d8da88bad78180ebbbb62e1cd25eb, 72ae7f0bfa910873739a409f94910cdd,
#         c69ad8003476146f1263021638dff356, d0c62e87a15e47c4e0d2a7f5af9ead06

# ---- 1. Live physical reads (read-only; phys = 0x86800000 + (va - 0xc0000000)) ----
read32() { for i in 0 1 2 3 4 5 6 7; do \
  devmem $(printf "0x%x" $(( $1 + i*4 ))) 32; done; echo; }
read32 0x8a60f000   # declared b26   -> /rfc descriptor table  (NOT b26)
read32 0x8AF64000   # relocated b26  -> 70604011 EBF41C10 ...  (b26[0:32])
read32 0x8a6ef000   # declared table -> all zero              (transplant lost)
read32 0x8B044000   # relocated table-> all zero              (transplant lost)
read32 0x86ac2000   # b15 control    -> 28102802 ...          (code patch present)

# ---- 2. Offline analysis (uses scratch/firmware/mdt.py) ----
python3 - <<'EOF'
import sys; sys.path.insert(0,'scratch/firmware')
from mdt import DumpView, mdt_plan, seg_filename, search
import os
IMG='GitIgnore/compare/modem_ufi001b_patch53_backup/image'
dump=DumpView('GitIgnore/compare/modem_coredump_p53.elf')
b26=open(IMG+'/modem.b26','rb').read()
print('b26[0:64] at', [hex(h) for h in search(dump, b26[0:64])])
print('table     at', [hex(h) for h in search(dump, b26[0xe0000:0xe0000+64])] or 'NONE')
live=dump.read(0xc3e0f000, 0x100000)
print('non-zero bytes matching at declared VA:',
      sum(1 for a,b in zip(b26,live) if a and a==b), '/', sum(1 for a in b26 if a))
EOF
# expect: b26[0:64] at ['0xc4764000']; table at NONE; 675 / 322707
```

**Device quirks that cost time — do not re-learn these:**

- `dd if=/dev/mem` returns **nothing** (blocked). Use `devmem` word-by-word in a device-side loop.
- `devmem 0x86800000` → **Bus error** (region base is XPU-guarded); other offsets read fine.
- `echo 1 > /sys/kernel/debug/remoteproc/remoteproc0/crash` **reboots the whole router**
  (hardware reset during modem recovery, no panic). `echo stop > …/state` does the same
  (Doc 141 §9.1). **Do not use either to obtain a dump.**
- `coredump_mode` resets to `disabled` on every reboot (Doc 141 §8.2).
- `timeout(1)` is absent from this BusyBox image.

---

## 13. Artifacts

| artifact | role |
| :--- | :--- |
| `scratch/firmware/mdt.py` | **new** — MDT/coredump inspection library (ELF32/64 detection, `q6v5_phdr_valid`, loader placement, dump search) |
| `GitIgnore/compare/modem_ufi001b_patched/image/` | deployed UFI001B set (MD5-matched to device) |
| `GitIgnore/compare/modem_hmu05_extracted/image/` | stock HMU05 set (control) |
| `GitIgnore/compare/modem_hmu05_live_connected.elf` | stock HMU05 working-state dump (control) |
| `GitIgnore/compare/modem_coredump_p{27,28r,29,49,49b,53,55,56}.elf` | the eight UFI001B dumps |
| `GitIgnore/compare/modem_ufi001b_patch{49,53}_backup/image/` | matched-pair reference images |

---

## 14. Device State at End of Session

- Router up; modem `running`; UFI001B patched set deployed and MD5-verified.
- `mode preference` left at **`umts, lte`** (set during §9). The QMI NAS client allocation is
  hung as a result. **Recovery requires a clean router reboot** — `echo stop > …/state` must
  **not** be used on this device (Doc 141 §9.1).
- No firmware was modified. `scratch/firmware/mdt.py` was added.
