# HMU05 900 s Crash — LPR Framework Analysis and Exact Crash Branch

**Date:** 2026-09-19
**Firmware:** `HIMI_U01_MODEM_V1.0` / `MPSS.DPM.1.0.C7` (stock HMU05)
**Supersedes (in part):** `900S_CRASH_ROOT_CAUSE_FIRMWARE_RE.md` — that report's *detection
function* is correct, but its identification of the two counters was wrong. This document
replaces §2.1's counter semantics and adds the exact instruction-level crash branch.

> **Headline (2026-09-20).** The blocking step is identified: the chain stalls inside
> **`rpm.sync`** (`enter = 0xc08bebd0`), the `rpm` Low Power Resource's sync step. Its two RPM
> flush loops (`0xc08b96f4` and `0xc08b988c`) have **no retry counter and no timeout**, so a stalled
> RPM parks the Q6 forever, `q6pcvote` (`lpr_get("rpm")->+0x18`) never advances, and MCPM's
> `system_sleep_check` fires the `sleep count not incrmnt` / `Q6 PC Voting failure` fatal at
> `0xc0ce84d0`. The chain order and every step's enter/exit are enumerated in **§6**.
> Root cause of the stall (Q6-side bookkeeping vs RPM-side service) is still open — §6.6.
>
> **Correction (2026-09-20, later).** An earlier revision of §6.6 claimed the `"synth"` mode table
> was probably dead build-time metadata. **That was wrong** — the table is registered at runtime via
> `FUN_c1280640(manager, &synth_provider)` and `FUN_c1281800` parses all 8 records with a 12-byte
> stride (§6.1). The error came from a raw-u32 pointer search, which cannot see code references or
> `gp`-relative references; §1.5 now documents the rule. The Ghidra decompilation was cross-checked
> in §6.7 and agrees with §2/§3.1/§6.1 exactly.
>
> **DIAG evidence (2026-09-20, later still) — see §9.** Re-analysis of the two existing captures
> overturns the premise of the "enable the F3 mask" plan: the mask was **already all-enabled**, and
> the captures **do** contain ~330 MCPM records. The captures store **format strings, not resolved
> text** (§9.2). Two methodology traps are now documented: byte offset is **not** a time axis
> (linear fit residual 24.6 s — §9.4), so timelines must use the `timetick` field that
> `a2_power.c` embeds inline. On that clock the LTE path collapses to ~20–30 % in **two episodes**
> (t≈30–60 s and t≈70–100 s), confirming `project_diag_900s_capture_finding`; an earlier revision
> of §9.5 reported a spurious single "pause at modem ~910.9 s" derived from byte offsets.

**Artifacts analysed:**

| Artifact | Size | Notes |
|---|---|---|
| `GitIgnore/MelbonWhiteStock_Dump/modem_extracted/image/modem.elf` | 50,049,024 B | ELF32 `elf32-hexagon`, 27 program headers, **0 section headers** |
| `scratch/firmware/modem.asm` | 202,061,320 B | `llvm-objdump -d --triple=hexagon` output, 4.95 M instructions |
| `Docs/Modem Stability/Modem RE/hmu05/modem_full_decompiled.c` | 105,121,709 B | Ghidra Hexagon:LE:32:default (incomplete — see §1.1) |

---

## 1. Methodology change: proper Hexagon disassembly

### 1.1 Why Ghidra alone was insufficient

Ghidra's decompilation did not resolve the string and table references in this image. Searching
the decompiled source for any string vaddr (e.g. `c1a7361f`, the ubiquitous `"%s %u"` format,
which has **337 code references**) returns **0 hits**. The same is true of the `.c` file-name
strings and the MSG tables. Any conclusion drawn from "Ghidra didn't reference X" was therefore
worthless.

### 1.2 The tool that works

`llvm-objdump` in this environment has the `hexagon` target registered:

```bash
llvm-objdump -d --triple=hexagon --no-show-raw-insn \
  GitIgnore/MelbonWhiteStock_Dump/modem_extracted/image/modem.elf > scratch/firmware/modem.asm
# 9 seconds, 202 MB, 4,951,623 instructions
```

### 1.3 The constant-encoding trap

Hexagon loads a full 32-bit constant as an `immext(#hi26)` + `Rd = ##<imm>` pair. **llvm-objdump
prints the resolved constant as a SIGNED hex.** So `0xc1a7361f` appears as `##-0x3e58c9e1`, and a
raw little-endian u32 search of the ELF finds nothing. Conversion:

```
signed = -(0x100000000 - vaddr)      # for vaddr >= 0x80000000
```

The operand shown on the `immext` line is the constant rounded **down to a 64-byte boundary**
(bits [31:6]); the full value is only in the `##` field of the following instruction. Example:

```
c0cd3018: { 	call 0xc08bd220
c0cd301c:   	immext(#0xc1854fc0)
c0cd3020:   	r0 = ##-0x3e7ab00b }        ; -> 0xc1854ff5
```

### 1.4 Helper scripts produced

| Script | Purpose |
|---|---|
| `scratch/firmware/xref.py <vaddr>…` | all code references to a vaddr (signed-form aware) |
| `scratch/firmware/ctx.py <vaddr>` | ±6 lines of disassembly around each reference |
| `scratch/firmware/rangerefs.py <lo> <hi>` | every code-referenced constant inside a range |
| `scratch/firmware/range.py <lo> <hi>` | disassembly lines whose address falls in `[lo,hi)` — **line format is `^([0-9a-f]{8}): `** (colon-space, *not* a tab) |
| `scratch/firmware/core.py` | read virtual addresses out of `scratch/coredump_p36.elf` |
| `scratch/firmware/a2_table2.py` | a2_power.c MSG table with corrected layout |
| `scratch/firmware/hex_immext.py` | immext-pair decoder (superseded by `xref.py`) |

### 1.5 Two ways to get a false negative (both have bitten this investigation)

**Never search the image for a raw little-endian u32 to decide whether an address is referenced.**

1. **Code references are not raw u32s.** `Rd = ##<imm32>` is encoded as `immext(#hi)` + a 16-bit
   immediate, and the *resolved* value only exists in llvm's output as a signed `##` operand. A
   `struct.pack('<I', vaddr)` search of `modem.elf` therefore finds data pointers but **never** code
   references. This is exactly the §1.3 trap, applied to reference-hunting rather than to reading
   constants. Use `xref.py` (signed-form aware).
2. **`gp`-relative references leave no pointer anywhere.** The framework stores its objects in
   `gp`-relative slots — `gp+0x3824` (the sleep-mode manager), `gp+0x3d44` (`lpr_get("cpu_vdd")`),
   `gp+0x3d48` (`lpr_get("rpm")`), `gp+0x3e68` (`lpr_get("mcpm_lpr")`), `gp+0x4e88`/`gp+0x4e94`
   (log handle / RPM lock). Their targets have no absolute pointer in the image at all.

**Do not grep the Ghidra `.c` for raw hex addresses either.** Ghidra assigns symbolic names from the
address of whichever field it first resolved, so the name→LPR table at `0xc1d464b0` appears as
`PTR_DAT_c1d464b4` — a `grep c1d464b0` returns 0 while the table is in fact used by two functions.
Search for symbols (`grep -o "[A-Za-z_][A-Za-z0-9_]*c1d46[0-9a-f]*"`), not for addresses.

**Ghidra's silence is not evidence of absence.** Because the LPRM callbacks are reached only through
the `+0x14`/`+0x18` fields of the LPRM descriptors, Ghidra never created functions at them:
`FUN_c08bebd0`, `FUN_c08bec60`, `FUN_c08beaf0`, `FUN_c08be940`, `FUN_c08b9690`, `FUN_c127ed60` and
`FUN_c12812c0` are **all absent** from `modem_full_decompiled.c`, yet every one of them is live. The
same tool does emit `FUN_c0ce7fe0`, `FUN_c08bd220`, `FUN_c08be030`, `FUN_c1280640` and
`FUN_c1281800`, which is what makes it useful for cross-checking (§6.7) but useless for coverage.

---

## 2. The exact crash branch

`FUN_c0ce7fe0` @ `0xc0ce7fe0` is the MCPM `system_sleep_check`. The fatal decision is a
two-instruction packet:

```
0xc0ce81d0   02 40 99 91    r2 = memw(r25+#0x0)
0xc0ce81d4   74 f3 02 22    if (cmp.gtu(r19,r2.new)) jump:t 0xc0ce82b8
```

* `r25` was advanced at `0xc0ce8178` by `r25 = addasl(r25,r16,#0x2)` from base `0xc30fd9a8`,
  so `r2 = DAT_c30fd9a8[tech]` — the **q6pcvote value stored when this tech last entered sleep**.
* `r19` = current `q6pcvote`, captured at `0xc0ce7fe8` (`call 0xc0cd3384` / `r19 = r0`).
* `cmp.gtu` = unsigned greater-than. **If the counter advanced, the branch is taken and the fatal
  block at `0xc0ce81d8`–`0xc0ce82b4` is skipped.**

Falling through executes, in order:

```
0xc0ce8250  r2 = ##-0x3e588bde            ; 0xc1a77422 "HARD_FAIL #%u[%u] sleep count not incrmnt, ..."
0xc0ce8288  r1:0 = combine(#0x0, 0xc1a774a9)  ; "UT detects Q6 PC Voting failure"
0xc0ce8294  call 0xc0cdab18
0xc0ce8298  memd(gp+#0x122e0) = r5:4
0xc0ce82ac  r0 = memub(r22+#0x6)
0xc0ce82b0  p0 = !tstbit(r0,#0x2)
0xc0ce82b4  if (!p0.new) jump:nt 0xc0ce844c      ; -> FUN_c0879150(&DAT_c3c68290), non-returning
```

**File offsets for patching:**

| Image | Offset |
|---|---|
| `modem.elf` | `0xcaf1d0` (packet), `0xcaf1d4` (branch) |
| `modem.b16` (VA base `0xc0287000`) | `0xa611d0` / `0xa611d4` |

### 2.1 Guards that must all hold before this packet is reached

1. `(bool)(-bVar2 & 1)` — i.e. `bVar2 == 0`. From the disassembly at `0xc0ce8004`–`0xc0ce804c`:
   * if `(DAT_c3968656 & 8) == 0`: `bVar2 = ((gp+0x3e80 | gp+0x3e84) == gp+0x3e70)`
   * else: `bVar2 = ((gp+0x3e70 & gp+0x3e84) != 0)`
2. `FUN_c0ce7f98() > 400` — min over the 15 active "techs" of `DAT_c30fda28[tech]`
3. `FUN_c0ce7e58(0xf, 0xc8881) > 400` — min elapsed since any active tech last slept
4. `param_3 == 1` — the **active-sleep** path. The `param_3 != 1` path only fires the soft
   recovery `FUN_c0cd6f38()` ("Sending vdd min debug interrupt") at threshold `0x1c2` = 450.

The 15 "tech" slots are exactly the 15 A2 power clients, indexed 0..14, and the active mask is
`(gp+0x3e80 | gp+0x3e84)`:

```
(0,1)=INT  (2,4,7,9)=L/W/TD/DO UL  (3,5,8,10)=L/W/TD/DO DL
(11)=APPS  (12,13)=A2PER  (14)=DL_INACT
```

---

## 3. What `q6pcvote` and `rpmvmin` actually are

The previous report treated these as opaque counters. They are **not**. `FUN_c0cd3384()` is:

```
c0cd3384: { allocframe(#0x20) }
c0cd3388: { r1 = add(r29,#0x0) ; r16 = add(r29,#0x0)
c0cd3390:   r0 = memw(gp+#0x3d48)
c0cd3394:   memd(r29+#0x18) = r17:16 }
c0cd3398: { call 0xc08bd290 }              ; buf+0x10 = *(u32*)(handle+0x18)
c0cd339c: { jump 0xc08372d8                ; tail-call the decoder
c0cd33a0:   r0 = memw(r16+#0x10) }
```

`FUN_c0cd3364()` is identical but uses `gp+0x3d44`.

### 3.1 The handles are LPR descriptors

`gp+0x3d44` and `gp+0x3d48` are filled in the MCPM init:

```
c0cd3018  call 0xc08bd220
c0cd301c  immext(#0xc1854fc0)
c0cd3020  r0 = ##-0x3e7ab00b        ; 0xc1854ff5 = "cpu_vdd"
c0cd3024  memw(gp+#0x3d44) = r0

c0cd3030  call 0xc08bd220
c0cd3034  immext(#0xc1848040)
c0cd3038  r0 = ##-0x3e7b7fa8        ; 0xc1848058 = "rpm"
c0cd303c  memw(gp+#0x3d48) = r0

c0cf45e0  call 0xc08bd220
c0cf45e4  immext(#0xc18554c0)
c0cf45e8  r0 = ##-0x3e7aab28        ; 0xc18554d8 = "mcpm_lpr"
c0cf45ec  memw(gp+#0x3e68) = r0
```

`FUN_c08bd220(name)` walks a **9-entry `{char *name; void *descriptor;}` table at `0xc1d464b0`**
(8 bytes per entry) and returns the descriptor pointer. Therefore:

> **`q6pcvote` = `*(u32*)(0xc1d473f8 + 0x18)` — the `rpm` Low Power Resource descriptor's +0x18 field.**

`rpmvmin` = `*(u32*)(*(gp+0x6520) + 0x34)` — a **different** struct. No writer for `gp+0x6520`
exists anywhere in the disassembly (it is read at `0xc0ce7ff8`, `0xc0ce8938`, `0xc0cec218`,
`0xc0cee618`, `0xc0cf1efc` and written nowhere), so it is initialised through a computed pointer.
**Still unresolved.**

### 3.2 The 9 registered LPRs (Low Power Resources)

The `/node/sleep/lpr` framework registers these, in this order:

| # | LPR name | descriptor vaddr | +0x10 callback | LPRM step(s) |
|---|---|---|---|---|
| 0 | `CLM` | `0xc1d46c68` | — | `disable` |
| 1 | `npa_scheduler` | `0xc1d46d18` | — | `fork` |
| 2 | `cpu_vdd` | `0xc1d46f18` | `0xc08be8c0` | `pc_l2_tcm_noret`, `pc_l2_noret`, `pc_l2_tcm_ret` |
| 3 | `l2` | `0xc1d47078` | — | `noret`, `ret` |
| 4 | `tcm` | `0xc1d471d8` | — | `noret`, `ret` |
| 5 | `cxo` | `0xc1d47298` | `0xc08bece0` | `shutdown` |
| 6 | **`rpm`** | `0xc1d473f8` | `0xc08bebc0` (`jumpr r31`, no-op) | **`sync`, `sync_only`** |
| 7 | `crypto_nav` | `0xc1d474b8` | — | `bcr_hm` |
| 8 | `mcpm_lpr` | `0xc1d47578` | set at runtime | `power_debug` |

Full enter/exit addresses per LPRM: §6.2.

Descriptor layout (from the raw image):

```
+0x00  char   *name
+0x04  u32     num_lprms
+0x08  LPRM   *lprm_list       ; array of num_lprms INLINE LPRM descriptors, stride 0x70
+0x0C  u32     flags
+0x10  int   (*fn)(void)
+0x14  u32     0 in the static image
+0x18  u32     the counter read as q6pcvote   <-- 0 statically, runtime-maintained
+0x1C  u32     0 in the static image
+0x20..0x4C    latency table (usec), e.g. cpu_vdd 0x19c8/0x17b2/0x1700
+0x50..        tail; descriptor stride is not uniform — see §6.2
```

(Verified in full in §6.2. Note that `+0x08` is the **LPRM step list**, not a parent pointer — an
earlier reading of this field as "parent" was wrong.)

Related framework strings:

| vaddr | string |
|---|---|
| `0xc185505a` | `/node/sleep/lpr` |
| `0xc1855080` | `ERROR (message: "Sleep LPR is already registered") (LPR Name: "%s")` |
| `0xc1a735cc` | `mcpm_sleep_lpr_client` |
| `0xc185501b` | `WARNING (message: "Sleep LPR not found") (LPR Name: "%s")` |
| `0xc18554d8` | `mcpm_lpr` |
| `0xc1a73626` | `mcpm_drv.c` |
| `0xc1a73631` | `%s MCPMDRV_State_Translation() failure !! ` |

### 3.3 The resulting mechanism

The `rpm` LPR is at index 6 of 9. If the chain stalls **at or before** `rpm`, `q6pcvote` never
increments and the check at `0xc0ce81d4` fires after 400 cycles.

**400 × 2.256 s = 902.4 s**, matching the measured periods across many boots
(902.169 / 902.394 / 902.395 / 902.395 / 902.394 / 902.395 s).

The chain and the stalling step are resolved in **§6** — the chain is
`npa_scheduler → CLM → l2 → tcm → cxo → rpm → mcpm_lpr → cpu_vdd`, and the stall is inside
`rpm.sync` itself (`0xc08bebd0`), not upstream of it.

---

## 4. The MCVS resource verifier (`mcpm_drv.c`) and its bypass flag

Region `0xc0cdb000`–`0xc0ce2000` is the MCPM driver's MCVS (multi-core voltage scaling)
resource verifier. It compares software votes against hardware register state and can HARD_FAIL.

| vaddr | string |
|---|---|
| `0xc1a7603e` | `%s #%u[%u] Mcvs bypass is ON ignoring mcvs checks, mcvsTechActive %u` |
| `0xc1a76083` | `%s HARD_FAIL #%u[%u] hwr:%x mcvs req value %u mismatched with hw Value %u` |
| `0xc1a7610b` | `%s RES #%u[%u] hwr:%x hwV:%u rsrcRqmt(vpe: 3 %d 8 %d clk 3 %d 8 %d q6:%d)` |
| `0xc1a761f9` | `%s IRES_FAIL #%u[%u] hwr:%x hwV:%u rsrcRqmt(vpe: 3 %d 8 %d clk 3 %d 8 %d q6:%d)` |
| `0xc1a7628d` | `%s HARD_FAIL #%u[%u] hwr:%x clkp:%u hwV:%u ux:%u ix:%u lc:%u chk:%u` |
| `0xc1a765e3` | `%s Current time %lx: UT detects Modem BHS mis-configuration. votes %x, software status %x, hw status %x. ` |
| `0xc1a7664d` | `%s #%u[%u] time to read hw vals: %lu usec, numTechs:%u, st tick %lx end tick %lx PC count q6 %u: rpm %u: chkType %x` |
| `0xc1a76976` | `%s HARD_FAIL #%u[%u] mcvs vpe req is Active but MEMSS block %u or Modem %u is off!` |
| `0xc1a769c9` | `%s Current time %lx: UT detects mss/memss mcvs vpe misconfiguration!` |

The **bypass flag** is a single byte bit:

```
0xc0cdbd20   r1 = memub(r22+#0x178)        ; MCPM driver struct, byte +0x178
0xc0cdbd28   r2 = and(r1,#0x1)
...
0xc0cdbd7c   p0 = tstbit(r0,#0); if (p0.new) jump:t 0xc0cdbd90   ; bypass set -> skip checks
```

The resource requirements voted on are `vpe`, `clk`, and `q6`.

---

## 5. Hypotheses disproven

### 5.1 The QMI time-service theory — DISPROVEN

`Docs/Modem Stability/QUALCOMM_MSM8916_MODEM_TIME_SERVICE_REPORT.md` claims the crash is caused
by the AP failing to send QMI time keepalives, starving an SCLK calibration watchdog.

`scratch/qmi900/qmi_syslog.txt` (2026-09-19) shows the opposite:

```
15:54:54  qcom-time-daemon[2486]: [QMI-TIME] REFRESH ATS_USER TRANSACTION VERIFIED!
15:54:54  qcom-time-daemon[2486]: [QMI-TIME] Modem baseband confirmed ATS_USER (offset=1473867647648 ms)
15:55:49  kernel: [ 912.900514] qcom-q6v5-mss: fatal error received: lte_ml1_sleepmgr_stm.c:4054:
```

The time daemon was succeeding **55 seconds before** the crash. The theory is dead.

### 5.2 `lte_ml1_common_timer.c:390` is a second crash — DISPROVEN

Older logs (`Docs/Modem Stability/78_DECOMPILED_MODEM_RE_AND_OPENWRT_GAP_ANALYSIS.md`) show
`lte_ml1_common_timer.c:390:` with deltas of 902.169 / 902.395 / 902.394 / 902.395 / 902.394 s —
**the same period to within 0.03 %**. A separate uninitialised-stack bug would not reproduce the
identical 902.4 s cadence. This is almost certainly the same root event surfacing at a different
assert site (or a different build's assert target).

**Consequence:** `GitIgnore/compare/build_hmu05_stock_patched.py` patches
`FUN_c04af058`/`FUN_c04aff24` for the `lte_ml1_common_timer.c:390` signature. Its structural
claims check out (`FUN_c04af058` does `allocframe(#0x680)`, sets `memb(r1+0)=0x1a` and
`memw(r2+0)=0x3a`, then `call FUN_c02a3c64`) — but it addresses a *different assert site*, and
the `lte_ml1_sleepmgr_stm.c:4054` crash persists afterwards.

### 5.3 "Which A2 power client blocks" — superseded

The earlier working hypothesis (`PC_PENDING_TEMP`, or A2 client 14 = `DL_INACT`) is **not** the
mechanism. The two counters read by the check are LPR descriptors, not A2 client slots. The A2
client mask only gates *whether the check runs at all* (`bVar2`), not the counter.

---

## 6. The LPR chain walker — the blocking step is `rpm.sync`

### 6.1 The chains are data, not code

The framework's mode definitions live at **`0xc1d464f8`**, immediately after the 9-entry
name→descriptor table at `0xc1d464b0` (9 × 8 = 0x48 bytes). Each record is **12 bytes**:

```
+0x00  char  *name          ; a human-readable dump of the chain, e.g. "CLM.disable + l2.ret + ..."
+0x04  u32    count         ; number of steps
+0x08  u32   *lprm_array    ; count pointers to LPRM descriptors, in execution order
```

The eight records:

| mode | `name` | count | `lprm_array` |
|---|---|---|---|
| 0 | `npa_scheduler.fork + CLM.disable + l2.ret + tcm.ret + cxo.shutdown + rpm.sync + mcpm_lpr.power_debug + cpu_vdd.pc_l2_tcm_ret` | 8 | `0xc1d46a98` |
| 1 | `npa_scheduler.fork + CLM.disable + l2.ret + tcm.ret + cxo.shutdown + rpm.sync + cpu_vdd.pc_l2_tcm_ret` | 7 | `0xc1d46ab8` |
| 2 | `CLM.disable + l2.ret + tcm.ret + cxo.shutdown + rpm.sync + mcpm_lpr.power_debug + cpu_vdd.pc_l2_tcm_ret` | 7 | `0xc1d46ad8` |
| 3 | `CLM.disable + l2.ret + tcm.ret + cxo.shutdown + rpm.sync + cpu_vdd.pc_l2_tcm_ret` | 6 | `0xc1d46af8` |
| 4 | `CLM.disable + l2.ret + tcm.ret + rpm.sync_only + mcpm_lpr.power_debug + cpu_vdd.pc_l2_tcm_ret` | 6 | `0xc1d46b10` |
| 5 | `CLM.disable + l2.ret + tcm.ret + rpm.sync_only + cpu_vdd.pc_l2_tcm_ret` | 5 | `0xc1d46b28` |
| 6 | `CLM.disable + l2.ret + tcm.ret + mcpm_lpr.power_debug + cpu_vdd.pc_l2_tcm_ret` | 5 | `0xc1d46b40` |
| 7 | `CLM.disable + l2.ret + tcm.ret + cpu_vdd.pc_l2_tcm_ret` | 4 | `0xc1d46b58` |

The `name` field is not decoration — resolving each `lprm_array` entry reproduces the same sequence
verbatim. **This closes old open question 3: the chain order *is* the registration/name order.**

**Execution order (all modes):** `npa_scheduler → CLM → l2 → tcm → cxo → rpm → mcpm_lpr → cpu_vdd`,
entered left-to-right and unwound right-to-left by `FUN_c12812c0` / `FUN_c1281440`.
`cpu_vdd` is **last**, not early. Modes 6 and 7 omit `cxo` *and* `rpm` entirely.

**The mode table is live — the `"synth"` provider is registered at runtime.** The mode table is
held by a provider descriptor at `0xc1d46558`:

```
0xc1d46558  char *name     = 0xc18554e1 "synth"
0xc1d4655c  u32   nmode    = 8
0xc1d46560  void *modes    = 0xc1d464f8
0xc1d46564  u32            = 1          ; power of two (validated by bitsclr)
0xc1d46568  u32            = 0x0005efb0
0xc1d46574..0xc1d4658c     = 1,2,3,4,5,6,7
```

and it **is** consumed:

```
0xc08bd094  immext(#0xc1d46540)
0xc08bd098  r0 = ##-0x3e2b9aa8        ; r0 = 0xc1d46558
0xc08bd090  jump 0xc08be0d0           ; delay slot sets r0
0xc08be0d0  r2 = memw(gp+#0x3824)     ; r2 = the sleep-mode manager object
0xc08be0d4  jump 0xc08be0e0 ; r1:0 = combine(r0,r2)
                                      ; combine(Rs,Rt) -> r1 = OLD r0, r0 = r2
                                      ; so: r0 = manager, r1 = 0xc1d46558
0xc08be0e0  jump 0xc1280640           ; FUN_c1280640(manager, &synth_provider)
```

`FUN_c1280640` @ `0xc1280640` is the provider registration. It logs

```
0xc1280678  r2 = ##0xc185537a
   "Registering pre-synthesized LPR (Name: \"%s\") (Sharing Cores: 0x%08x)"
```

with `r3 = memw(r17+0x00)` = the provider name = **`"synth"`**, validates `memw(r17+0x0C) == 1` as a
power of two (`bitsclr`), then calls `FUN_c1281800(provider)`.

`FUN_c1281800` @ `0xc1281800` is the core, and it confirms the record layout by code:

```
c128180c  r0 = memw(r16+#0x4)          ; nmode
c1281810  if (r0 != 8) jump 0xc1281884 ; MUST be exactly 8, else fatal
c1281814  r0 = #0x30 ; call 0xc1221780 ; malloc(0x30) — the runtime mode object
c1281848  r0 = memw(r16+#0x8)          ; the mode table pointer
c128184c  call 0xc1280eb0 ; r0 = add(r0,r21)
c128185c  r21 = add(r21,#0xc)          ; <-- 12-byte stride, CONFIRMED by code
c1281864  memw(r20++#0x4) = r18        ; store the parsed mode
c1281868  r0 = memw(r16+#0x4)
c128186c  if (r0 > r19) jump:t 0xc1281848   ; loop nmode times
```

So all 8 modes are parsed and stored. The mode names are therefore **runtime chain definitions**,
not documentation — which *strengthens* §6.1 rather than weakening it.

### 6.2 Corrected descriptor layouts

**LPR descriptor** (e.g. `rpm` @ `0xc1d473f8`):

```
+0x00  char   *name            ; 0xc1848058 "rpm"
+0x04  u32     num_lprms       ; 2 for rpm
+0x08  LPRM   *lprm_list       ; 0xc1d47318 — array of num_lprms INLINE descriptors, stride 0x70
+0x0C  u32     flags           ; 1
+0x10  int   (*cb)(void)       ; 0xc08bebc0 for rpm (a bare `jumpr r31` — a no-op stub)
+0x14  u32     (0)
+0x18  u32     the counter read as q6pcvote      <-- 0 statically, runtime-maintained
+0x1C  u32     (0)
+0x20..0x4C    latency table (usec): rpm 0x3e8/0x3e8/0x3e8, cpu_vdd 0x19c8/0x17b2/0x1700
+0x50..       beyond the fields used here; descriptor stride is not uniform
              (CLM→npa_scheduler = 0xB0, npa_scheduler→cpu_vdd = 0x200), so the tail is
              variable-length. An earlier reading called it an embedded "child LPR" — unverified.
```

The **`+0x08` field is a pointer to an *inline* LPRM array (stride `0x70`), not an array of
pointers.** (The earlier "array of pointers" reading produced garbage names such as `0x65676173`.)

**LPRM descriptor** (e.g. `rpm.sync` @ `0xc1d47318`):

```
+0x00  char   *name     ; 0xc184b795 "sync"
+0x04  u32     (1)
+0x08  void   *         ; 0xc1d472b8
+0x0C  u32     (1)
+0x10  void   *         ; 0xc1d472c8
+0x14  int   (*enter)(void)  ; 0xc08bebd0
+0x18  int   (*exit)(void)   ; 0xc08bec60
+0x1C  u32     (1)
+0x20  void   *         ; 0xc1d472d8
+0x24  u32     flags
```

Complete LPRM inventory, straight from the image:

| LPR | n | LPRM | descriptor | `enter` | `exit` |
|---|---|---|---|---|---|
| `CLM` | 1 | `disable` | `0xc1d46bf8` | `0xc127ed60` | `0xc127f050` |
| `npa_scheduler` | 1 | `fork` | `0xc1d46ca8` | `0xc08b8c60` | `0xc08b8d70` |
| `cpu_vdd` | 3 | `pc_l2_tcm_noret` | `0xc1d46dc8` | `0xc08beac0` → `0xc08be940` (r3=1) | `0xc08beae0` |
| | | `pc_l2_noret` | `0xc1d46e38` | `0xc08be930` → `0xc08be940` (r3=2) | `0xc08beae0` |
| | | `pc_l2_tcm_ret` | `0xc1d46ea8` | `0xc08bead0` → `0xc08be940` (r3=0) | `0xc08beae0` |
| `l2` | 2 | `noret` | `0xc1d46f98` | `0xc08beb20` | `0xc08bebb0` |
| | | `ret` | `0xc1d47008` | `0xc08beaf0` | `0xc08bebb0` |
| `tcm` | 2 | `noret` | `0xc1d470f8` | `0xc08beb80` | `0xc08bebb0` |
| | | `ret` | `0xc1d47168` | `0xc08beb50` | `0xc08bebb0` |
| `cxo` | 1 | `shutdown` | `0xc1d47228` | `0xc08bed60` | `0xc08bed80` |
| **`rpm`** | 2 | **`sync`** | `0xc1d47318` | **`0xc08bebd0`** | **`0xc08bec60`** |
| | | **`sync_only`** | `0xc1d47388` | **`0xc08bebd0`** | **`0xc08bec60`** |
| `crypto_nav` | 1 | `bcr_hm` | `0xc1d47448` | `0xc12875e0` | `0xc12875c0` |
| `mcpm_lpr` | 1 | `power_debug` | `0xc1d47508` | `0xc0cd2e20` | `0xc0cd2e40` |

### 6.3 Blocking analysis of every step

Every `enter` was disassembled and scanned for backward branches (loops):

| step | `enter` | body | blocks? |
|---|---|---|---|
| `npa_scheduler.fork` | `0xc08b8c60` | bookkeeping, timestamp math, one log; **no backward branch** | no |
| `CLM.disable` | `0xc127ed60` | timing math + four linked-list walks (`r16 = memw(r16+#0x60)`, `r2 = memw(r2+#0x60)`) and one count-bounded loop (`while (memw(r19+#0x6c) > r18)`). All terminate on a null next-pointer / fixed count. | no |
| `l2.ret` | `0xc08beaf0` | straight-line read-modify-write of `0xEC107030` | no |
| `tcm.ret` | `0xc08beb50` | straight-line read-modify-write of `0xEC107030` | no |
| `cxo.shutdown` | `0xc08bed60` | `FUN_c08b9110()` then tail-call to `0xc0298cb0` (clock vote release) | no |
| **`rpm.sync`** | **`0xc08bebd0`** | **`FUN_c08b9690(1)` then `FUN_c08b9690(2)` — RPM force-sync** | **YES — unbounded** |
| `mcpm_lpr.power_debug` | `0xc0cd2e20` | one conditional call to `0xc0ce3790` | no |
| `cpu_vdd.pc_l2_tcm_ret` | `0xc08bead0` → `0xc08be940` | HW poll of `0xEC107010` bit 0, **200 iterations**, then `FUN_c0879150` | no (bounded) |

The `cpu_vdd` poll, exactly:

```
c08be96c  r0 = #-0xc8                  ; -200
c08be970  memw(r21+#0x0) = #0x1        ; r21 = 0xEC10700C — kick the collapse
c08be974  r1 = memw(0xEC107010)        ; <-- poll
c08be97c  p0 = tstbit(r1,#0); if (p0) jump 0xc08be994    ; bit 0 set -> done
c08be980  r0 = add(r0,#0x1)
c08be984  if (r0 != 0) jump:t 0xc08be974                 ; bounded loop
c08be988  call 0xc0879150              ; timeout -> fatal, with its OWN message
```

Because this step is **timeout-bounded and self-reporting**, it cannot produce the
`sleep count not incrmnt` signature. The same argument rules out every other step.

### 6.4 The blocker: `rpm.sync` (enter `0xc08bebd0`)

```
c08bebd0  r17:16 = combine(r1,r0) ; r0 = #0x1 ; allocframe(#0x10)
c08bebdc  call 0xc08bd140
c08bebe0  call 0xc08bedf0 ; r1:0 = combine(#0x0,#0x0)
c08bebe8  call 0xc0f9a600 ; r1:0 = combine(r17,r16)
c08bebf0  call 0xc08b9690 ; r0 = #0x1        <-- RPM force-sync, set 1
          ...
c08bec10  call 0xc0880240                    ; log " Sleep set sent (wakeup time requested: 0x%llx)"
c08bec18  r2 = ##-0x3e7aa7e0                 ; 0xc1855820
c08bec20  call 0xc08bcbc0
c08bec28  call 0xc08b9690 ; r0 = #0x2        <-- RPM force-sync, set 2
c08bec40  call 0xc089de40
c08bec44  if (r0 != 0) jump 0xc08bec4c       ; -> call 0xc0879150 (fatal)
c08bec48  return
c08bec4c  call 0xc0879150
```

`FUN_c08b9690(set)` @ `0xc08b9690` is the RPM force-sync / flush engine. It operates on the static
RPM driver struct at **`0xC2C65FC8`** (`r21`):

```
+0x04  0xC2C65FCC   the pending/"dirty" count polled by BOTH loops
+0x08  0xC2C65FD0   entry count (loop bound at 0xc08b9780)
+0x0C  0xC2C65FD4   array of entry pointers
+0x10  0xC2C65FD8
+0x14  0xC2C65FDC   handle passed to FUN_c08b9b70
+0x1C/+0x20/+0x24   per-set lists, indexed by set (r16 = 0/1/2) at 0xc08b96f8
```

It contains **two waits, neither of which has a counter or a time check:**

**Wait A — drain the dirty set** (`0xc08b96e8`–`0xc08b96f4`):

```
c08b96e8  call 0xc08b9820            ; churn-drain one entry
c08b96ec  call 0xc08ba950 ; r0 = memw(r21+#0x4)
c08b96f4  p0 = cmp.eq(r0,#0x0); if (!p0) jump:t 0xc08b96e8     ; LOOP, unbounded
```

**Wait B — the RPM churn queue**, inside `FUN_c08b9820` @ `0xc08b9820`:

```
c08b9820  r1 = memw(gp+#0x4e94) ; allocframe(#0x10)   ; RPM lock
c08b9828  call 0xc0691730                             ; spin_lock
c08b9838  r2 = ##-0x3e7abf45                          ; 0xc18540bb
c08b9844  call 0xc087785c                             ; log "rpm_churn_queue (msg_id: 0x%08x)"
c08b9850  jump 0xc08b9884 ; r17 = ##0xc2c65fcc        ; r17 = 0xC2C65FCC
c08b9884  call 0xc08baec0 ; r1 = r16                   ; FUN_c08baec0(*(u32*)0xC2C65FCC, msg_id)
c08b988c  p0 = cmp.eq(r0,#0x0); if (!p0) jump:t 0xc08b9860   ; LOOP, unbounded
c08b9860  log "churning (msg_id: 0x%08x)"             ; 0xc18540dc
c08b9878  call 0xc08b9b70 ; r1 = #0x2
c08b9890  log "rpm_flushed (set: %d) (msg_id: 0x%08x)" ; 0xc1854094
c08b98a8  unlock; return
```

`FUN_c08baec0(driver, msg_id)` is a *still-pending* predicate: it walks the driver's three queues
(via `FUN_c08baf00` → `FUN_c08bb060`/`FUN_c08bb220`/`FUN_c08bb070`) looking for an entry matching
`msg_id`, returning 1 while any copy is still queued. The loop body `FUN_c08b9b70` (0xc08b9b70 –
0xc08b9c9c) contains **no backward branch** — it is a bounded kick/log helper. So the churn loop's
only exit is the predicate clearing. **Nothing decrements a retry counter; nothing compares against
a timestamp.** If the RPM message RAM is not serviced, the Q6 spins here forever.

RPM log strings recovered from the image:

| vaddr | string |
|---|---|
| `0xc185403b` | `rpm_force_sync (set: %d) (dirty: %d,%d,%d)` |
| `0xc1854066` | `\trpm_flushing (resource: 0x%08x) (id: 0x%08x)` |
| `0xc1854094` | `rpm_flushed (set: %d) (msg_id: 0x%08x)` |
| `0xc18540bb` | `rpm_churn_queue (msg_id: 0x%08x)` |
| `0xc18540dc` | `\tchurning (msg_id: 0x%08x)` |

The only other exit from `FUN_c08b9690` is the fatal `FUN_c0879150` at `0xc08b9804`
(`memw(r21+#0x8) == 0`) and `0xc08b9810` (`FUN_c089d170` returned `< 0`) — i.e. the engine either
completes, or **spins**, or aborts with a *different* message. It never returns "did not advance".

### 6.5 Why this reproduces the observed crash exactly

```
rpm.sync enter 0xc08bebd0
  -> FUN_c08b9690(1) -> FUN_c08b9820 -> while (still-pending) log("churning")   <-- parks here
  -> (never reached) FUN_c08b9690(2)
  -> (never reached) cpu_vdd.pc_l2_tcm_ret  0xc08be940
  -> (never reached) walker exit, counter bump
```

`q6pcvote` is `lpr_get("rpm")->+0x18` (`0xc1d47410`), read by `FUN_c0cd3384` via
`gp+0x3d48` (cached `lpr_get("rpm")` from `0xc0cd3038`). The snapshot `DAT_c30fd9a8[tech]` is
refreshed with the *current* counter value at each sleep entry (`0xc0cee620`, next to the sleep-cycle
counter bump at `0xc0cee604`), so the test means **"did `q6pcvote` advance since this tech last
entered sleep?"**. The chain never completes, so it does not, and `FUN_c0ce7fe0` reaches the packet
at `0xc0ce81d4` with `r19 <= r2` (Ghidra: `uVar6 <= *puVar12`), the `cmp.gtu` is false, the branch is
not taken, and control falls into
`HARD_FAIL #%u[%u] sleep count not incrmnt` (`0xc0ce8254`, string `0xc1a77422`). Guard 4's config
bit (`0xc396865c` bit 2, tested at `0xc0ce82ac`–`0xc0ce82b4`) then sends it to `0xc0ce844c`, which
logs `UT detects Q6 PC Voting failure` (`0xc0ce84a8`, string `0xc1a77494`) and calls the
non-returning `FUN_c0879150` at **`0xc0ce84d0`** with message `0xC3C68290`. That is the
`qcom-q6v5-mss: fatal error received: lte_ml1_sleepmgr_stm.c:4054` on the AP console.

**Answer to old open question 1: the blocking LPR is `rpm`.** It is *not* one of
`CLM / npa_scheduler / cpu_vdd / l2 / tcm / cxo` — those six are all either straight-line register
twiddles or bounded list walks, and `cpu_vdd` (which is *last* in the chain, not first) has its own
200-iteration timeout that reports a different fatal message.

### 6.6 What is *not* yet proven

**1. Which mode is actually selected at runtime.** Modes 0–5 include `rpm.sync`; modes 6–7 exclude
it. If the framework were selecting mode 6 or 7, the `rpm` counter could never advance *by
construction* and the 400-cycle check would fire on every boot regardless of any stall. Static
analysis now proves that **all 8 modes are registered** (§6.1: `FUN_c1281800` requires `nmode == 8`
and parses all of them), so the choice is made dynamically — it is the *policy* that is unknown, not
the existence of the modes. It is cheap to settle: enable the F3 message group that emits
`Mode entering (lpr: %s) (lprm: %s) (Enter Time 0x%llx)` (`0xc1855461`, logged at `0xc12813b8` by
the walker `FUN_c12812c0`) and read the mode from the first sleep cycle.

**Note on reaching the walker.** `FUN_c12812c0` / `FUN_c1281440` have **no direct code references** —
they are reached through a jump table at `0xc08bc060`–`0xc08bc0b4` (a series of `immext` + `jump`
trampolines: `0xc08bc08c → FUN_c12812c0`, `0xc08bc09c → FUN_c1281440`, `0xc08bc07c → FUN_c1280a70`,
`0xc08bc084 → FUN_c1280960`). The table holds no stored pointers, so the switch is computed, not
loaded. This is why both the decompiler and naive pointer searches lose the chain entirely.

**2. Q6-side vs RPM-side root cause.** The chain **stalls** in `rpm.sync`; whether the *cause* is
Q6-side or RPM-side is still open:

* **(a) Q6-side** — the churn predicate keeps finding a matching `msg_id`, e.g. because the RPM
  driver's queue bookkeeping is corrupt or a duplicate entry is never retired.
* **(b) RPM-side** — the RPM (the separate processor inside PM8916 that owns the message RAM) stops
  servicing the queue, so nothing is ever consumed. The Q6 then spins in `rpm_churn_queue`.

Note that the modem firmware is byte-identical between stock Android and OpenWrt (it lives on the
`/lib/firmware` overlay and sysupgrade does not touch it — see
`project_modem_firmware_deployment`), yet stock Android does not crash. Whatever differs must be
**AP-side**, which is only consistent with (b) or with a mode-selection difference (caveat 1) — the
Q6-side code path and its inputs are the same in both cases. This is the strongest available
argument and it points at the AP's own RPM/SMD traffic as the trigger.

**3. ~~Whether the `synth` mode table is live.~~ RESOLVED — it is live.** An earlier version of this
document claimed the mode table was probably dead build-time metadata because "nothing references
`0xc1d46558`". **That claim was wrong**, and the error is instructive: it rested on a *raw
little-endian u32* search of the image, which cannot find a code reference (encoded as an
`immext`+`##signed` pair, never as a bare u32) and cannot find a `gp`-relative reference at all.
The signed-form-aware `xref.py` finds the reference immediately: **`0xc08bd098`**,
`r0 = ##-0x3e2b9aa8`. See §6.1 for the full registration path, and §1.5 for the general rule.

Both DIAG captures taken so far (`scratch/diag900/diag_cap_900.bin`,
`scratch/qmi900/diag_cap_900.bin`) contain **zero** records from the LPR framework or the RPM
driver — `Mode entering`, `Late sleep exit`, `lte_ml1_sleepmgr` and `rpm_force_sync` all return 0
hits as plain byte strings, so the sink was not enabled. (Searching those captures by *string
vaddr* returns 0 for everything: the DIAG capture stores **resolved literal text**, not vaddrs.)
The only coredump available (`scratch/coredump_p36.elf`) is a **UFI001BC** build, not HMU05, so it
cannot supply HMU05 runtime state.

---

### 6.7 Independent cross-check against the Ghidra decompilation

`Docs/Modem Stability/Modem RE/hmu05/modem_full_decompiled.c` (105 MB, Hexagon:LE:32:default) was
used as an independent source. Where Ghidra *did* emit the function, it agrees with the
instruction-level reading exactly:

**`FUN_c0ce7fe0` (the crash function)** — decompiles to:

```c
uVar6 = FUN_c0cd3384();                                   /* current q6pcvote            */
uVar1 = *(uint *)(*(int *)(unaff_GP + 0x6520) + 0x34);    /* rpmvmin                     */
...
puVar12 = (uint *)(&DAT_c30fd9a8 + iVar5 * 4);            /* snapshot, indexed by tech   */
...
if (400 < uVar7) {                                        /* guard: sleep-cycle counter  */
  uVar8 = FUN_c0ce7e58(0xf,0xc8881);
  if (400 < uVar8) {                                      /* guard: elapsed              */
    if (uVar6 <= *puVar12) {                              /* <-- THE FATAL CONDITION     */
      ...
      FUN_c1284180(..., s__s_HARD_FAIL___u__u__sleep_count_c1a77422);
      FUN_c0cdab18(&DAT_c1a774a9, ...);                   /* "UT detects Q6 PC Voting failure" */
      if (((DAT_c1e0a5dc._3_1_ & 0x02) != 0) &&
          (!(bool)(-((DAT_c396865c & 0x04) == 0) & 1))) {
        FUN_c1284180(..., 5, &DAT_c1a77494);
        /* WARNING: Subroutine does not return */
        FUN_c0879150(&DAT_c3c68290);
      }
    }
```

Every element of §2 / §2.1 / §6.5 is reproduced: the `<=` comparison, the 400 thresholds, the
`param_3 != 1` soft path at `0x1c2` (= 450), the `0x1c2`-and-`rpmvmin` second branch
(`check rpm vdd min count`, `RPM vdd min fails %u, check for issues on RPM`), and the fatal call
marked *"Subroutine does not return"*. The config gate is `DAT_c396865c & 4` — bit 2, as stated.

**`FUN_c08bd220` (`lpr_get`)** — decompiles to:

```c
ppuVar5 = &PTR_DAT_c1d464b4;
uVar6 = 0;
while (true) {
  puVar1 = ppuVar5[-1];                       /* entry +0x00 = name       */
  iVar3  = thunk_FUN_c1204760(puVar1);        /* strlen                   */
  iVar4  = thunk_FUN_c1206600(iVar2,puVar1,iVar3);   /* strncmp           */
  if (iVar4 == 0 && *(sbyte *)(iVar2 + iVar3) == 0) break;
  ppuVar5 = ppuVar5 + 2;                      /* +8 bytes per entry       */
  uVar6   = uVar6 + 1;
  if (8 < uVar6) { FUN_c0837778(0); return; } /* 9 entries, 0..8          */
}
FUN_c0837778(*ppuVar5);                       /* entry +0x04 = descriptor */
```

This independently confirms the **8-byte stride, the 9 entries, and the
`{char *name; void *descriptor}` layout of the table at `0xc1d464b0`** — the load-bearing
foundation for §3.1, §6.1 and §6.2.

**`FUN_c08be030` (`lpr_register`)** is the *second* user of `PTR_DAT_c1d464b4` and walks the same
table with the same stride, confirming the table has two live consumers.

**`FUN_c1280640` / `FUN_c1281800`** confirm the mode-table registration and the 12-byte record
stride (§6.1).

**What the decompilation cannot do.** It never emits the LPRM callbacks, the RPM flush engine, the
chain walker, or `CLM.disable` (see §1.5), so it cannot speak to §6.3 or §6.4 at all. Its role here
is corroboration of the *static data structures and the crash function*, not of the blocking step.

---

## 7. Remaining open questions

1. **Which sleep mode is selected at runtime, and does it contain `rpm.sync`?** Modes 0–5 do;
   modes 6–7 do not. Settle it by enabling the F3 group for
   `Mode entering (lpr: %s) (lprm: %s) (Enter Time 0x%llx)` (`0xc1855461` @ `0xc12813b8`) and
   `Mode exiting ...` (`0xc1855498` @ `0xc12814b8`).
2. **Why does the RPM churn queue stop draining after ~400 cycles?** Q6-side bookkeeping vs
   RPM-side service stall — see §6.6. Same F3 mask plus the `rpm_force_sync` (`0xc185403b`),
   `rpm_churn_queue` (`0xc18540bb`), `churning` (`0xc18540dc`) and `rpm_flushed` (`0xc1854094`)
   groups.
3. **What `gp+0x6520` points at** (the `rpmvmin` source, compared against at `0xc0ce82c8`).
   No static writer found; initialised through a computed pointer.
4. **Which firmware was deployed at the 912.9 s capture** — stock `modem.mdt`
   sha256 `3d23e145637689d79f44f72349b24b61` or `GitIgnore/compare/modem_hmu05_patched/`
   sha256 `68fb4a5a21c03ffbb41145fe70b73369` (built 2026-09-19 04:31). Undetermined; no hash log
   exists in `scratch/`.

---

## 8. Candidate interventions (not yet implemented)

Ordered by cost, cheapest and least invasive first.

| # | Intervention | Site | Risk |
|---|---|---|---|
| 0 | **Enable the F3/DIAG mask** for the `/node/sleep` and `rpm` message groups and re-run a soak. Answers §7.1 and §7.2 with no firmware change at all. | DIAG only | none — this is the next step, not a patch |
| 1 | Make the fatal branch always take the "counter advanced" path | `0xc0ce81d4` | Suppresses a genuine hardware-state check. Modem stops crashing but may never power-collapse. |
| 2 | Raise the 400 threshold | `0xc0ce81cc` / immediate `0x190` | Buys time, does not fix. |
| 3 | Set the MCVS bypass flag bit | MCPM driver struct `+0x178` bit 0 | Suppresses all MCVS checks. |
| 4 | **Bound the `rpm.sync` waits** — add a retry/timeout to the churn loops at `0xc08b96f4` and `0xc08b988c` so a stalled RPM returns an error instead of parking the Q6. | `0xc08b96f4`, `0xc08b988c` | Correct-shaped fix, but the loops have no free register for a counter and the enclosing frame is 0x10/0x18 bytes — needs a real code cave. |
| 5 | Force a mode without `rpm.sync` | mode table `0xc1d464f8` (`count` / `lprm_array`) | Removes the Q6 PC vote entirely — the modem would stop power-collapsing on that rail. Also blocked on the §6.1 caveat: the mode table may not be live. Untested. |
| 6 | **Suppress the AP-side RPM traffic that starves the RPM** (if §6.6 branch (b) is confirmed). | AP kernel/userspace | Needs the culprit identified first; see `project_root_cause_q6pc_vote_failure`. |

Per the project's **dual-firmware comparative protocol**, none of the patches may be flashed until
the patch bytes are verified against stock ground truth and the two firmware images are compared.

---

## 9. DIAG capture evidence — two LTE-path collapse episodes, and why the mask plan is void

Re-analysis of the two captures already on disk. This section **overturns the premise of
intervention 0** in §8 ("enable the F3/DIAG mask"): there was nothing left to enable.

### 9.1 The mask was already all-enabled

`scratch/diag900/run_capture.sh:50` enables the stream with
`/root/diag_logtool cntl-enable /dev/rpmsg1`. Reading `GitIgnore/compare/diag_logtool.c`:

* `cmd_cntl_enable()` sends `DIAG_CTRL_MSG_F3_MASK` with `msg_mask_size = 1` and
  `msg_mask[0] = 0xFFFFFFFF` (lines 413–424), and
* `DIAG_CTRL_MSG_LOG_MASK` with `status = DIAG_CTRL_MASK_ALL_ENABLED` and `log_mask_size = 0`
  (lines 426–435).

That is **byte-for-byte what the reference driver emits** for `ALL_ENABLED`:
`diag_masks.c:375-380` sets `equip_id = 0`, `num_items = 0`, `log_mask_size = 0`,
`send_once = 1` — i.e. "enable everything", with no per-equipment mask bytes by design
(contrast `DIAG_CTRL_MASK_VALID` at `diag_masks.c:381-386`, which does carry bytes).
**Conclusion: the modem was already told to emit every SSID and every log code.**

### 9.2 The captures store format strings, not resolved text

An earlier note claimed the captures store resolved literal text. They do not. The stored payload
is the **format string**, with `%`-specifiers intact and the arguments in separate binary fields:

```
MCPM: tech wakeup_req- clearing params early_wakeup_time 0x%x%x sleep_active %d
MCPM 2 step FW PC FW_SLEEP_PWRDN_FULL %d
WWAN_TECH_MSG from CXM: Tech %u Band %d Chan %d FreqKHz %d Dir %d BW %d
```

This does **not** change the earlier "zero LPR/RPM records" result — searching for
`Mode entering`, `rpm_force_sync`, `churning`, `rpm_flushed`, `rpm_churn_queue`, `Late sleep exit`,
`sleep count not incrmnt`, `Q6 PC Voting failure`, `HARD_FAIL` as plain byte strings returns **0**
in both captures, and those substrings are prefix-clean (no `%` before the matched text). But the
*reason* is now stated correctly, and any future search must use the format-string form.

### 9.3 What the captures DO contain

The captures are rich in MCPM. Capture 1 (`scratch/diag900/diag_cap_900.bin`, 14,058,851 B,
modem 848→969 s):

| Message | Count |
|---|---|
| `MCPM 2 step FW PC FW_SLEEP_PWRDN_FULL %d` | 100 |
| `MCPM FW_WAKE-UP_Start ...` | 101 |
| `MCPM_NPA: No Imm CLKCPU req of 384000 ...` | 199 |
| `MCPM_NPA: ldo17 freq CB notified with %d tech %d` | 101 |
| `MCPM: tech wakeup_req- clearing params early_wakeup_time 0x%x%x sleep_active %d` | 29 |
| `A2 power req from client=3[...]` | 402 |
| `CFM using CLM: CPU percentage utilization is %d` | 1059 |

So the `sleep_active` state *is* observable, and the MCPM power-collapse (PC) cadence is directly
measurable — the sink was never the problem.

### 9.4 Byte offset is NOT a valid time axis — use the `timetick` clock

An earlier revision of this section built its timeline on byte offset, having "validated" it with a
size-matched record-count test (a normal window held 237 records, median 3710 B; the gap window
held 240, median 3738 B). **That test was too weak, and the resulting timeline was wrong.**

The correct clock is the `timetick=0x%08x` field that `a2_power.c` embeds **resolved inline** in its
message text (note: this message is *fully formatted*, unlike the MCPM messages of §9.2 — the
firmware pre-formats some messages and not others). It is a free-running 32-bit counter at
**8,263,680 Hz** (wrap 519.7 s). Over this capture it yields:

* **599 samples, span 120.00 s** (the wall window is 121 s — essentially exact)
* **0 non-monotonic samples**

A linear fit of time against byte offset leaves a **maximum residual of 24.6 s (rms 5.65 s)** — so
byte offset is not merely noisy, it is unusable. All times below are on the `timetick` clock.

### 9.5 The two collapse episodes (capture 1, `timetick` clock)

Counts per 10 s bin (`t = 0` at capture start; capture start ≈ modem 848 s):

```
series                  t=0   10   20   30   40   50   60   70   80   90  100  110
VADC (poller)            89   86   89   82   88   88   86   56   25   42  230  107
CFM (poller)             87   87   87   82   87   87   86   55   25   43  228  105
A2 cl3 power req         56   52   52   36   16   22   54   34   16   16   34   14
WWAN_TECH_MSG CXM        84   78   79   44   16   34   81   51   24   24   34   14
MCPM PC powerdown        14   13   13    9    4    5   14    8    4    4    9    3
MCPM wake-up             14   13   13    9    4    6   13    9    4    4    8    4
MCPM CLKCPU req          30   26   25   15    5    8   35   21   10    9   11    4
sleep_active msg          0    0    0    9    4    3    0    0    0    1    8    4
rflte_core_rxctl          0    0    0  144    0    0    0    0    0    0    0    0
```

This **confirms the earlier capture finding** (`project_diag_900s_capture_finding`): the LTE data
path — `A2 power req`, `WWAN_TECH_MSG from CXM`, `MCPM FW_WAKE-UP_Start`,
`MCPM 2 step FW PC FW_SLEEP_PWRDN_FULL`, `MCPM_NPA` CLKCPU — drops to **~20–30 % of its healthy
rate** in **two** episodes, t≈30–60 s and t≈70–100 s, separated by a healthy window at t≈60–70 s.
The pollers (VADC, CFM) hold at 82–89 through t≈70 s, then also dip (56, 25) in episode 2 before
bursting to 230/228 at t≈100–110 s — the recovery backlog.

`rflte_core_rxctl_update_rx_gain_freq_comp_to_mdsp` is **exclusive to episode 1**: 144 occurrences,
all inside t=30–40 s, zero elsewhere. The LTE RX chain retunes and never locks.

**Correction.** An earlier revision of §9.5 reported a single "691,580-byte MCPM pause at modem
~910.9 s" matching the predicted stall onset. **That was an artifact** of the byte→time mapping.
On the real clock the largest MCPM PC inter-event gap is only **6.63 s, starting at t = 86.08 s**
(inside episode 2). The stall is a *rate collapse across two episodes*, not a discrete pause.

### 9.6 What this changes

* **§8 intervention 0 is void.** The mask is already all-enabled (§9.1); re-running `cntl-enable`
  cannot add records.
* The open question is no longer "enable the sink" but **"why do `HARD_FAIL`,
  `sleep count not incrmnt` and `Q6 PC Voting failure` never appear, when the same logger
  (`FUN_c1284180`, 451 call sites) is plainly working for other MCPM messages?"** Candidates, none
  tested: the fatal path suppresses normal MSG output before `FUN_c0879150`; no crash occurred
  inside capture 1's window; the message goes to a stream the bridge drops.
* **Any future timeline from these captures must use `timetick`, not byte offset.** The MCPM
  messages of §9.2 do not carry it, but the `a2_power.c` messages interleave densely (599 samples
  over 120 s) and can be used to interpolate.
* **New lead (unchanged).** `MCPM: tech wakeup_req- clearing params early_wakeup_time 0x%x%x
  sleep_active %d` is a clean binary marker of the collapse: **0 occurrences in every healthy bin,
  present in every episode** (t=30–60 s and t=70–110 s). Its binary arguments carry the runtime
  `sleep_active` state at the moment of collapse. Decoding them is task #26.


