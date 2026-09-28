# 205 — The F3 log-descriptor table (a new instrument), the class-invariant CM death measured over 1 584 s, and the complete op-mode refusal

**Date:** 2026-09-26
**Device:** HMU05 4G modem stick (`Melbon HMU05`), OpenWrt 25.12.5, kernel 6.12.94 aarch64, `192.168.8.1`, SSH root.
**Firmware under test:** UZ801 v3.2 "-21" baseband, deployed as **v19** — `modem.b16` md5 `c71d53464a1e88f2072cbbb6f7f87475`, `modem.mdt` md5 `9f39ce579114bcf1383bbdce62a8d284`; `--dms-get-revision` = `UZ801_V3.0_21_V01R01B10 1 [Sep 07 2015]`. **No firmware was built, patched or deployed in this session.**
**Status:** RESULTS. Three new results, one new instrument, and one **method-validated negative** that retires a whole line of static analysis.
**Ledger:** see `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md`; its §3/§7/§8 are updated in the same session as this doc.
**Predecessors:** `201` (the `0x1000001` gate is dead / the pivot is `cmmsc_auto`), `202` (the boot gate retracted / the CM boot-path divergence), `203` (the capture-class confound / the instrument fix), **`204`** (the `u=1` race attacked — it controls `evt=0`, not `online`).

---

## 1. SOP compliance statement

| SOP step | Taken? | Detail |
| :-- | :-- | :-- |
| Verify against stock HMU05 ground truth | **Yes** | Every differential in §5–§8 is computed in the same session against **two** stock arms: `scratch/hmu05_stock_boot.bin` (the 94 623 391 B boot capture, 30 958 F3 records) and the **stock HMU05 ELF** `GitIgnore/MelbonWhiteStock_Dump/modem_extracted/image/modem.elf` (50 049 024 B, 27 phdrs). The log-descriptor tables of **both** firmwares are extracted and tabulated side by side (§3, §8). |
| Byte-level / hash verification before attributing | **Yes** | `scratch/online_battery.bin` md5 **`07391dfc81381cd36e796abd7c1e4491`** (5 472 107 B) is the file the device itself wrote (`/root/online_battery.bin`, `ls -la` 5 472 107 B). The running firmware is re-hashed on the device before the run: `modem.b16` = `c71d53464a1e88f2072cbbb6f7f87475`, `modem.mdt` = `9f39ce579114bcf1383bbdce62a8d284`. |
| Backup / reversibility | **Yes** | The only device writes are `/root/battery.sh`, `/root/battery.log`, `/root/online_battery.bin` and a DIAG re-bind + `cntl-enable`. **No firmware, NV, guard or partition was touched.** The guard reported `boot_count` 0 throughout (no revert). No reboot was performed. |
| No blind patching of the baseband | **Yes** | **Nothing was patched.** §11 names the next target and marks every patch **UNBUILT**. |
| Reboot discipline / instrument-not-perturbing | **Yes** | **No reboot.** The instrument is the Doc 203 async procd job (`d385dad0…`) plus a one-shot `diag_bind.sh` + `cntl-enable` + `capture 90` run, all inside one SSH session. The battery's commands are the measurement. |
| Mandatory ledger update in the same session | **Yes** | Doc 197 §3/§7/§8. |

---

## 2. Achieved vs Expected

| Intent | Expected | Achieved | Status |
| :-- | :-- | :-- | :-- |
| Explain why the `cmmsc_auto.c` / `mmocmmgsdi.c` format strings look **unreferenced** in the ELF | a mechanism | **MET (new instrument).** The F3 strings live in a **separate string segment (seg25)** and are reached through an **8-byte-stride descriptor table in seg18**, sorted by `(line, level)`. Code never embeds the string address, which is exactly why a raw-u32 or `immext` scan returns 0 for **strings the modem demonstrably emits** — the trap that produced `200 §4`'s "instrument defect" and `201 §5.3`'s "no pointer-table reference" (§3) | **MET** |
| Derive and validate the Hexagon `immext` immediate encoding | a formula | **MET.** `immword(A) = (A[31:28]<<24) | ((A>>20)&0xFF)<<16 | 0x4000 | ((A>>6)&0x3FFF)`, **validated on six independently-known constants** and cross-checked against `llvm-objdump -d --triple=hexagon` (§4) | **MET** |
| Decide whether `FUN_c06928a8` (`cmmsc_auto`) really has no static caller | a yes/no | **MET — it has none, and the method is now trustworthy.** The same scan that returns **0** for `FUN_c06928a8` correctly returns the two in-function references to `FUN_c0691d9c` (`0xc0691b6c`, `0xc0692958`), one to `FUN_c0692794` (`0xc0692a1c`) and one to `FUN_c069282c` (`0xc0692a64`) — all inside `FUN_c06928a8` itself, matching the decompile exactly (§7) | **MET** (negative, method-validated) |
| Quantify how dead the UZ801 CM service layer is over a long uptime | a count | **MET.** Over a capture whose CM records reach **modem uptime `u=1584` (26 min)**: `cmlog.c` / `cmss.c` / `cmregprx.c` / `cmsoa.c` / `cmcall.c` / `cmcc.c` / `cmmsc_auto.c` / `mmoc.c` / `mmocdbg.c` / `sdss.c` / `sdcmd.c` = **0**, while `cmdbg.c` = 20 (alloc/free only) and `cmph.c` = 1 (§5) | **MET** |
| Establish the full set of op-mode levers the modem will accept | a table | **MET — none work.** Every QMI `--dms-set-operating-mode` value (`low-power`, `persistent-low-power`, `reset`, `shutting-down`, `online`) reports **success** and the mode stays **`offline`**; `AT+CFUN=0` and `AT+CFUN=4` → **`+CME ERROR: operation not supported`**; `AT+CFUN=1` → **`+CME ERROR: phone failure`**; `AT+COPS=0`, `AT+COPS=?`, `AT+CGATT=1` → **`ERROR`**; QMI NAS `set-system-selection-preference=lte` → **`QMI protocol error (3): 'Internal'`** (§6) | **MET** |
| Find what the UZ801 `cmmsc_auto.c` source revision actually is | a diff | **MET — it is a NEWER revision than stock's.** All 13 shared log lines match stock **except** a uniform **+9 line shift** from line 2849/3830 upward, and the UZ801 build carries **three log statements stock does not have at all** (`lte data call call:%d ss;%d` @5076, `Updating ss for call_id:%d to HYBR2` @5080, `… to MAIN` @5087). The 9-line shift begins **between line 2686 and line 2849** (§8) | **MET** (UNEXPECTED) |
| Characterise a live `cntl-enable` capture's time window | a check | **NOT MET — a new instrument trap found.** The 90 s `capture` produced records whose F3 `ts` spans **1 348.7 s** (`cfm_cpu_monitor.c` alone spans 976 s at ~50 ms spacing, 19 521 distinct `ts`). A `cntl-enable` **flushes the modem's buffered F3 history**, so a "live" capture is **not** a clean time window (§5.3) | **NOT MET** (confound declared) |
| Not make the ~900 s fatal worse | no new fatal mechanism | unchanged; **no reboot, no SSR, no fatal** during the session | **MET** |

**The one-line truth:** the F3 log strings are **not** addressed by the code at all — they are reached through a
sorted descriptor table — which means the two-year-old "the `cmmsc_auto` strings are unreferenced, therefore
something is wrong with the build" line of reasoning is **an artifact of the addressing scheme, not evidence**;
what *is* evidence is that `cmmsc_auto`'s registration function `FUN_c06928a8` has **no static caller**
(method-validated), the entire CM service layer is **still dead at modem uptime 1 584 s**, and the modem
**accepts every op-mode request and changes nothing**.

---

## 3. ★ The F3 log-descriptor table — how the modem's log strings are actually addressed

> **⚠ CORRECTION 2026-09-26 (Doc 207 §3) — read this before using any count from this section.**
> The entry layout `{ u32 packed ; u32 word1 }` with `packed = (line<<16)|level` is **correct**, but the
> second word is **not always a string pointer**: it is a seg25 `strptr` for **only ~8 % of sites**, and a
> **32-bit hash** (`< 0xc0000000`, not a VA at all) for the other **~92 %**. `scratch/desc_table.py entries`
> filters on `word1 ∈ seg25`, so **it silently keeps only ~8 % of the table** — every count in this section
> is a **lower bound of unknown size**, not a count. Example (`cmcc.c` block, UZ801):
> `0xc165c398` L3027 → hash `0xa18d6925`; `0xc165c3b8` L3081 → strptr `0xc4561700`
> `"cmcc.c:=CM= MMGSDI EPS mmgsdi_status=%d"`.
> The hash is **build-independent** (10 039 of UZ801's 11 200 hash keys, **89.6 %**, are shared with HMU05),
> so it is a valid cross-build statement key. `desc_table.py` also has a **crash-to-empty-output** bug: an
> unguarded `off2va` returning `None` raises inside a `%08x` format, so under `2>/dev/null` a **wide** VA
> range prints `0` while a **narrow** range inside it prints `149`. **Re-take any wide-`entries` count.**
> The instrument to use now is `scratch/logsite.py` (Doc 207 §4), which maps a function's log calls to
> `file:line` by reading `packed` at each **literal descriptor VA** — 69 775 UZ801 sites, 68 186 stock sites.

**The problem this solves.** `cmmsc_auto.c`'s format strings sit in ELF segment 25 (`0xc4558000–0xc4614cec`
in UZ801; `0xc449a000–0xc44e6000` in stock). A raw little-endian u32 search for those VAs returns **0 hits**,
and an `immext`-based scan returns **0 hits** — in *both* firmwares. That was read (correctly, at the time) as
"no code references them", and produced two wrong turns: `200 §4`'s "instrument defect" declaration and
`201 §5.3`'s "no pointer-table reference ⇒ runtime-registered handler".

**The control that exposes the artifact.** Run the same scan on a string the modem **demonstrably emits at
runtime**:

| string (UZ801) | segment | emitted in captures? | `immext` refs | raw-u32 refs |
| :-- | :-- | :-- | --: | --: |
| `=CM= CMD alloc u=%d, tsk=%s` | **18** (`0xc18f8dcf`) | yes | **2** (`0xc06951fc`, `0xc06952d0`) | 0 |
| `=CM= mode_pref %d, pref_term %d` | **25** (`0xc456205f`) | **yes** | **0** | 0 |
| `=MMOC= mmocmmgsdi_card_status_cb…` | **25** (`0xc456639d`) | **yes** | **0** | 0 |
| `=CM= RAT_DISABLED_MASK: sys_mode %d` | **25** (`0xc4561bcb`) | **yes** | **0** | 0 |
| `=CM= MSC_AUTO: 0x%x & 0x%x = 0x%x` | 25 (`0xc4563fdd`) | no | 0 | 0 |

⇒ **segment 25 strings are unreferenced by construction.** The "0 refs" result carries no information about
whether the code runs.

**What the code actually references.** Scanning the whole image for u32 values that point into the
`cmmsc_auto.c` string block finds a cluster of holders in **segment 18** at a clean **8-byte stride**:

```
0xc165fdf0: 09fe000b c4563d04   'cmmsc_auto.c:=CM= unblock_set_flags: …'
0xc165fdf8: 0a05000b c4563d50   'cmmsc_auto.c:=CM= set send_unblock flag to TRUE'
0xc165fe00: 0a5b000b c4563d80   'cmmsc_auto.c:=CM= CMMSC_AUTO: updating op_mode, …'
0xc165fe08: 0a7e000a c4563de0   'cmmsc_auto.c:=CM= old_op_mode:%d new_op_mode:%d ue_mode:%d'
0xc165fe28: 0b2a000b c4563e1c   'cmmsc_auto.c:=CM= CMMSC_AUTO:is_ue_mode_csfb=%d …'
…
0xc165ff00: 0cb2000b c4563fd0   'cmmsc_auto.c:=CM= MSC_AUTO: 0x%x & 0x%x = 0x%x'
```

**The entry layout is `{ u32 packed; u32 strptr; }` where `packed = (line << 16) | level`:**

* `packed >> 16` is **exactly the source line** the F3 record reports. Verified on the one line where both
  builds agree on the same statement: `CMMSC_AUTO: updating op_mode` → `packed = 0x0a5b000b` → line
  **2651**, and the stock capture's F3 record is `cmmsc_auto.c L2651`. ✔
* `packed & 0xFFFF` is `0x0b` (11) for most entries and `0x0a` (10) for a minority — a **log level**, not an
  argument count (entries with 3 `%d`s carry both values).
* `strptr` points at the **`file.c:` prefix** of the combined `"<file>.c:<format>"` string. The F3 wire
  encoder splits that combined string at the first `:` to produce the record's `fmt` and `file` fields —
  which is why `f3parse2` recovers them correctly and why the ELF shows the two glued together.
* The table is **sorted by `packed`** (i.e. by line within a file): the `MSC_AUTO` entry (line 3250) sits at
  `0xc165ff00`, *before* the `Top ptr is NULL for HYBR1 stack` entries (lines 3839–4001) at
  `0xc165fe58…`. A sorted table implies a **binary search** at log time.

**A second consequence — the "hash" entries.** In the same table, entries whose `strptr` does **not** point
into a loaded segment carry a random-looking 32-bit word instead (e.g. `0xc165feb0: 1259000b 54da4c7a`).
Those are log statements whose **format string was not retained** in this build; the second word is not an
address. Any tool that assumes "second word = pointer" must filter by segment membership, not by shape.

**Instrument:** the extraction is one loop — walk the image 4 bytes at a time, keep `{j, w0, w1}` where `w1`
maps to a VA inside the target string block, then sort by `j`. §13 has the code.

---

## 4. The Hexagon `immext` encoding, derived and validated

Hexagon loads a 32-bit constant with a **pair**: `immext(#BASE)` followed by `rX = ##…`. `llvm-objdump`
resolves the pair, so `grep`ing its output works, but a **raw byte scan needs the encoding**. Derived
empirically from six `immext` instructions whose resolved values `llvm-objdump` prints:

```
immword(A) = ((A >> 28) & 0xF) << 24
           | ((A >> 20) & 0xFF) << 16
           | 0x4000
           | ((A >> 6) & 0x3FFF)
```

| target `A` | derived word | bytes seen in the image | match |
| :-- | :-- | :-- | :-- |
| `0xc165c380` | `0x0c16570e` | `0e 57 16 0c` | ✔ |
| `0xc18f8d80` | `0x0c187e36` | `36 7e 18 0c` | ✔ |
| `0xc18f8c00` | `0x0c187e30` | `30 7e 18 0c` | ✔ |
| `0xc18f8d00` | `0x0c187e34` | `34 7e 18 0c` | ✔ |
| `0xc0691d80` | `0x0c066476` | `76 64 06 0c` | ✔ |
| `0xc165c3c0` | `0x0c16570f` | `0f 57 16 0c` | ✔ |

**Caution (a real trap, now measured):** a scan for `immword(A)` must require a **4-byte-aligned** file
offset *and* check that the following instruction's low 6 bits equal `A & 0x3F`. Without the alignment test
the search returns false positives — the first pass in this session "found" the `=CM= CMD alloc` string at
`0xc06961fc` and the bytes there are `01 40 40 89`, not `37 7e 18 0c`; the real site is `0xc06951fc`
(the mismatch was a **hex-arithmetic slip in the VA→offset map**, caught by round-tripping a known byte run
through `objdump`).

---

## 5. Result 1 — the UZ801 CM service layer is still dead at modem uptime 1 584 s

### 5.1 The live capture

`/root/battery.sh` (device-side) binds the DIAG bridge, `cntl-enable`s, starts
`diag_logtool capture 90 /root/online_battery.bin`, and then issues the op-mode battery of §6 inside that
window. The device reported `7114 messages, 5443651 bytes, 84 LOG_F, 0 MSG_F`; the delimiter-aware parser
recovers **22 951 F3 records** (all with distinct offsets and distinct `ts`).

### 5.2 The CM census — over 26 minutes of modem uptime

```
  cfm_cpu_monitor.c            19521     <- dominant; see §5.3
  DalVAdc.c                     1427
  tle_log.c                      913
  qmi_nas.c                      525
  qmi_mmode_task.c               346
  mc_msg.c                       135
  a2_sio.c                        31
  cmdbg.c                         20     <- alloc/free ONLY
  wms.c                           14
  mmgsdi_refresh.c                 7
  wmscfg.c                         7
  gstk_proactive_cmd.c             2
  gstklib.c                        1
  gstkutil.c                       1
  cmph.c                           1     <- CM_PH_CMD_GET_NETWORKS only
```

**`cmlog.c` / `cmss.c` / `cmregprx.c` / `cmsoa.c` / `cmcall.c` / `cmcc.c` / `cmmsc_auto.c` / `mmoc.c` /
`mmocdbg.c` / `mmocmmgsdi.c` / `sdss.c` / `sdcmd.c` = 0.**

The CM records that *do* exist are only the allocator and a single `cmph` line:

```
idx 21183 ts=1282388328 =CM= CMD alloc u=1528, tsk=ds
idx 21184 ts=1282388480 =CM= CMD free  u=1528, tsk=ds, ftsk=cm      <- 152 ticks = 0.74 ms
idx 21834 ts=1290389292 =CM= CMD alloc u=1567, tsk=ds
idx 21835 ts=1290389316 cmph.c L13584 [0] =CM= CM_PH_CMD_GET_NETWORKS sys_mode %d
idx 21836 ts=1290389372 =CM= CMD free  u=1567, tsk=ds, ftsk=cm
idx 21973 ts=1292036972 =CM= CMD alloc u=1575, tsk=dcc
idx 21974 ts=1292037044 =CM= CMD free  u=1575, tsk=dcc, ftsk=cm      <- 72 ticks = 0.35 ms
idx 22140 ts=1292870300 =CM= CMD alloc u=1579, tsk=qmi_mmode
idx 22143 ts=1292870324 =CM= CMD free  u=1579, tsk=qmi_mmode, ftsk=cm <- 24 ticks = 0.12 ms
```

`u` is the modem's uptime in seconds (Doc 201 §3.1). The last record is **`u=1584` = 26.4 minutes**, and
across that whole span **not one** CM serving-system, MMOC, call-control or logging record appears. This is
the same class-invariant gate as Doc 204 §6, now measured over an interval 17× longer than the boot captures.

Note the *one* CM command that does work: `CM_PH_CMD_GET_NETWORKS` (the `AT+COPS=?` handler) **ran** and
reported `sys_mode = 0`. So the CM dispatcher is alive and *some* cases are reachable — which is what makes
the `dcc` drop (0.35 ms, no handler action) a **case-specific** failure, not a dead task.

### 5.3 ★ New instrument trap — a `cntl-enable` capture is NOT a clean time window

The 90 s capture contains records whose `ts` spans **1 348.7 s**:

```
cfm_cpu_monitor.c: n=19521, ts min 1024201048, max 1300412804, span 1348.69 s, 19521 DISTINCT ts,
                   median inter-record spacing ≈ 10272 ticks = 50.1 ms
```

`cfm_cpu_monitor` cannot be logging at 20 Hz for 976 s inside a 90 s window. **`cntl-enable` flushes the
modem's buffered F3 history**, so the capture contains pre-existing records plus the live ones. Consequences:

1. **A live capture may be used for *presence* claims** (does this file ever appear?) — which is what §5.2
   does, and the flushed history only *strengthens* a zero.
2. **A live capture may NOT be used for *rate*, *order* or *timing* claims** without first separating the
   flushed history from the live tail (e.g. by the `u` field, which is a real 1 Hz uptime).
3. This is a second, independent reason `200 §4`'s live-`mmocdbg.c` sub-evidence was unsound, beyond the
   mask-width reason already declared there.

---

## 6. Result 2 — the modem accepts every op-mode request and changes nothing

One SSH session, modem already `offline`, `--dms-get-operating-mode` re-read after each step:

| lever | API result | mode after |
| :-- | :-- | :-- |
| `--dms-set-operating-mode=low-power` | `Operating mode set successfully` | **`offline`** |
| `--dms-set-operating-mode=persistent-low-power` | `Operating mode set successfully` | **`offline`** |
| `--dms-set-operating-mode=reset` | `Operating mode set successfully` | **`offline`** |
| `--dms-set-operating-mode=shutting-down` | `Operating mode set successfully` | **`offline`** |
| `--dms-set-operating-mode=online` | `Operating mode set successfully` | **`offline`** |
| `AT+CFUN=0` | `+CME ERROR: operation not supported` | `+CFUN: 7` |
| `AT+CFUN=4` | `+CME ERROR: operation not supported` | `+CFUN: 7` |
| `AT+CFUN=1` | `+CME ERROR: phone failure` | `+CFUN: 7` |
| `AT+COPS=0` | `ERROR` | — |
| `AT+COPS=?` | `ERROR` | — |
| `AT+CGATT=1` | `ERROR` | — |
| `--nas-set-system-selection-preference=lte` | `QMI protocol error (3): 'Internal'` | `not-registered` |

**`low-power` is not a way out on this build.** On the *stock* HMU05 firmware, `low-power` is reversible and
`offline` is the one-way trap (see `project_pmos_a16_result.md`). Here the modem starts `offline` and
**every** transition, including the one that is a no-op by definition, is either refused or ignored. Combined
with §5.2, the shape is unambiguous: **the CM op-mode path never executes** — the DMS request is turned into
a CM command, the command is allocated and freed in 0.35 ms, and no handler action is logged.

The SIM is not implicated: `--uim-get-card-status` reports `usim (2) state 'ready'`, `Personalization state:
'ready'`, `PIN1 state: 'disabled'`, and `isim (5)` detected; `--nas-get-serving-system` reports
`Registration restriction: 'unrestricted'`, `SIM reject info: 'unavailable'`.

---

## 7. Result 3 — `FUN_c06928a8` (`cmmsc_auto`) has no static caller, and the method is now trustworthy

> ⚠ **RETRACTED 2026-09-26 by Doc 206 §3.** `FUN_c06928a8` is **not** `cmmsc_auto.c` — it is **`cmcc.c`**.
> Every log descriptor it passes resolves to a `cmcc.c:` string (`=CM= MMGSDI EPS mmgsdi_status=%d` at
> UZ801 L3081, `… srv_available=%d, cm_mmgsdi_acl_availability=%d` at L3115), and its own embedded symbols
> are `s_cmcc_service_available_cb` / `s_cmcc_call_control_processing_lte`. The **no-static-caller
> measurement below stands**; the label and the inference drawn from it do not — stock **runs `cmcc.c`**
> while that function has no static caller in the stock image either (Doc 206 §4.2). Read this section for
> the *method*, not for a statement about `cmmsc_auto`.

Doc 201 §5.3 and Doc 202 §7 both reported "no direct caller and no pointer-table reference". That claim was
made with a method that §3 now shows is **blind to segment-25 strings** — so it needed re-testing with a
method that is *demonstrably* able to find code references.

**Calibration first.** Scan for `immext` references to four targets whose references are known from the
decompile of `FUN_c06928a8` (`FUN_c091d940`… / `thunk_EXT_FUN_d0587538(…,0x456,FUN_c0691d9c,iVar2)` and
`…,0x422,FUN_c0692794,…`, and the terminal `FUN_c06917f0`):

| target | refs found | where |
| :-- | :-- | :-- |
| `FUN_c0691d9c` | **2** | `0xc0691b6c`, `0xc0692958` (the latter is the `0x456` registration inside `FUN_c06928a8`) |
| `FUN_c0692794` | **1** | `0xc0692a1c` (the `0x422` registration inside `FUN_c06928a8`) |
| `FUN_c069282c` | **1** | `0xc0692a64` (inside `FUN_c06928a8`) |
| **`FUN_c06928a8`** | **0** | — |
| `FUN_c0691d70` (thunk) | **0** | — |

The scan is therefore **capable of finding intra-CM code references** and returns exactly the four the
decompile predicts. Against that calibration, **`FUN_c06928a8` has no reference of any kind** — not a call,
not a `jump` thunk, not a raw u32 pointer, not an `immext` immediate.

**⇒ What this does and does not mean.**

* It **confirms** that `cmmsc_auto` is reached through a **runtime-registered external service**
  (`thunk_EXT_FUN_d0587538`), consistent with `201 §4`'s finding that the DMS path forwards to the external
  `thunk_EXT_FUN_d03d3420(0xc,0x1a,0x30f)` (the `0xd0…` region maps to no `PT_LOAD`).
* It **retires** the "the build is missing the caller" hypothesis: the caller was never in the image for
  *either* firmware — stock's `cmmsc_auto` also runs at boot, so the registration mechanism is external in
  both. **This is a property of the platform, not a UZ801 build defect.**
* It means **the static route to "what invokes `cmmsc_auto`" is closed** and must be replaced by a
  behavioural probe (§11).

---

## 8. Result 4 (UNEXPECTED) — the UZ801 `cmmsc_auto.c` is a **newer** source revision than stock's

The descriptor table (§3) makes every log statement in a file enumerable, **with its source line**, in both
builds. For `cmmsc_auto.c`:

| statement | UZ801 line | stock line | Δ |
| :-- | --: | --: | --: |
| `Top ptr is NULL for MAIN stack` | 1693 | 1693 | 0 |
| `MSC: HICPS: cm_mode_pref: %d, ph obj mode_pref: %d, ph_ptr mode_pref: %d` | 1872 | 1872 | 0 |
| `subsfmode, srlte:%d sxlte:%d svlte:%d` | 2503 | 2503 | 0 |
| `local_fplmn_list.length %d asubs_id:%d` | 2504 | 2504 | 0 |
| `SRLTE: UNBLOCK_PLMNs: …` | 2522 | 2522 | 0 |
| `is_1xsrlte_mcc:%d` | 2523 | 2523 | 0 |
| `SVLTE: UNBLOCK_PLMNs: …` | 2549 | 2549 | 0 |
| `unblock_set_flags: …` | 2558 | 2558 | 0 |
| `set send_unblock flag to TRUE` | 2565 | 2565 | 0 |
| `CMMSC_AUTO: updating op_mode, …` | 2651 | 2651 | 0 |
| `old_op_mode:%d new_op_mode:%d ue_mode:%d` | 2686 | 2686 | 0 |
| `CMMSC_AUTO:is_ue_mode_csfb=…` | **2858** | 2849 | **+9** |
| `Top ptr is NULL for HYBR1 stack` | **3839** | 3830 | **+9** |
| `Top_ptr is NULL for MAIN stack` | **3899** | 3890 | **+9** |
| `Top ptr is NULL for HYBR1 stack` | **3967** | 3958 | **+9** |
| `Top ptr is NULL for HYBR1 stack` | **4001** | 3992 | **+9** |
| `Top ptr is NULL for MAIN stack` | **4104** | 4095 | **+9** |
| `Top_ptr NULL for stack %d` | **4275** | 4266 | **+9** |
| `hybr2 stack sync-up %d %d` | **4563** | 4554 | **+9** |
| `MSC_AUTO: 0x%x & 0x%x = 0x%x` | **3250** | 3241 | **+9** |
| `lte data call call:%d ss;%d` | **5076** | — | **absent in stock** |
| `Updating ss for call_id:%d to HYBR2` | **5080** | — | **absent in stock** |
| `Updating ss for call_id:%d to MAIN` | **5087** | — | **absent in stock** |

**Reading.** Between source lines 2686 and 2849 the UZ801 build inserts **exactly 9 lines**; everything below
shifts by +9 uniformly. After line 4563 the UZ801 build adds **three log statements stock does not have**
(lines 5076/5080/5087 — an LTE-data-call / per-call SS-update path). So the UZ801 `cmmsc_auto.c` is a
**later revision with additional functionality**, not a stripped one.

**Consequence for the campaign.** This **kills** the last version of the "the build is missing `cmmsc_auto`"
hypothesis in all its forms. `cmmsc_auto` exists, is *larger*, and is unreferenced by construction in both
builds. The gate is **not** a missing function.

**Methodological value.** This table is the first *source-line-level* differential between the two firmwares
that does not depend on the F3 capture at all — it is derivable offline from the two ELFs. It is the tool
that should have been built before `v1`.

---

## 9. The complete `file:line` differential (stock boot capture vs UZ801 BLIND boot)

For the record, the whole capture-level differential, restricted to distinct `(file, line)` pairs:

* **UZ801-only: 49 pairs.** `qmi_nas.c` L15861 (×27) / L15909 (×37) are the SIM's preferred-network-list
  read (`plmn(%d), mcc:%d, mnc%d`, MCC 405/310/334) — benign. The rest are line-number shifts of shared
  code (`cmdbg.c` L2186 vs L2181, `cmph.c` L18094/L18103 vs L17829/L17838, `mmocmmgsdi.c` L1694/L1718/L1757
  vs L1637/L23933, `mmgsdi_refresh.c` L9732 vs L9730, `mmgsdiutil.c` L8267/L8285 vs L8242/L8260) plus
  `cmph.c` L15013 (`=CM= vs_id %x`, value `0x10C00000`) and `rcinit_rex.c` L150
  (`task ends group 8 task_hash efb72ed task_name memshare_qm`).
* **stock-only: 179 pairs**, dominated by `a2_power.c`, `mcpm_npa.c`, `mcpm_saw.c`, `pgi_msgr.c`,
  `rflte_core_rxctl.c` — all **online-gated** (Doc 202 §4.2) — plus the boot-path CM set
  (`cmlog.c`, `cmss.c`, `cmregprx.c`, `cmsoa.c`, `cmcall.c`, `cmcc.c`, `cmmsc_auto.c`) that §5.2/§8 explain.

No new causal claim is drawn from this table beyond §8; it is recorded so the next session does not have to
re-derive it.

---

## 10. What this changes

1. **Two corpus claims are retracted as method artifacts.** `200 §4`'s "instrument defect" framing and
   `201 §5.3`'s "no pointer-table reference" inference both rested on a scan that §3 shows cannot see
   segment-25 strings. The *conclusions* (the live mask is narrower; `cmmsc_auto` is runtime-registered)
   survive on independent grounds; the *reasoning* does not.
2. **The "missing caller" hypothesis is dead.** §7 (no reference, method-validated) and §8 (the function is
   a *newer*, *larger* revision) close it from both ends.
3. **The gate is now bounded on both sides.** Below it: the CM dispatcher is alive and at least one case
   (`CM_PH_CMD_GET_NETWORKS`) runs. Above it: `cmmsc_auto`, `cmss`, `cmregprx`, `cmsoa`, `cmlog` never run,
   at modem uptime 1 584 s. The drop is **case-specific**, not a dead task.
4. **A new instrument exists.** The descriptor table turns "which source lines does this build contain, and
   at what level" into an offline, exact, capture-free measurement — usable for any module, in either
   firmware.
5. **A new trap is on the record.** A `cntl-enable` capture is not a clean time window (§5.3).
6. **The op-mode refusal is total and enumerated** (§6) — there is no AP-side lever, and the table is now
   complete enough that no future session needs to re-probe it.

---

## 11. Named next step

**Replace the closed static route with a behavioural one.** In order of cost:

1. **★ Probe `cmmsc_auto`'s invocation directly.** `FUN_c06928a8` is registered with the external service
   dispatcher `thunk_EXT_FUN_d0587538(…, 0x456, FUN_c0691d9c, …)` / `(…, 0x422, FUN_c0692794, …)`. Those two
   **message IDs (`0x456`, `0x422`)** are the only concrete handles the image gives. The question is *which
   peer sends `0x456`/`0x422`* and *whether that peer is initialised on this boot*. This is a
   **cross-reference question** (find other users of `0x456`/`0x422` in the image), not a caller question,
   and it is the one static lead §7 leaves open.
2. **★ Time-box one more live differential.** §5.2's live capture is the first one long enough to ask a
   *negative over time*: at `u=1584` the layer is still dead. The complementary measurement is a **stock**
   capture of comparable length — if stock's `cmss.c`/`cmmsc_auto.c` traffic is *also* zero after boot, then
   the "dead layer" is the *normal* steady state and the gate is purely the **boot** instant; if stock keeps
   emitting, the UZ801 layer is not merely un-triggered but **torn down**. Either answer re-scopes the target.
   This needs a stock-firmware boot + long soak — a deployment, so it is the expensive item.
3. **Do not** re-open the race (`204`), the `evt=0` patch (`204`), the `0x1000001` whitelist (`201`), or the
   `cm_state+0x39e8` byte (`200`/`201`). All four are scored and negative.

**Explicitly NOT the next step:** any further search for a *caller* of `FUN_c06928a8`. §7 closes it.

---

## 12. Evidence inventory

| artifact | md5 / value | what it is |
| :-- | :-- | :-- |
| `scratch/online_battery.bin` | **`07391dfc81381cd36e796abd7c1e4491`** | the live 90 s op-mode-battery capture (5 472 107 B); equals the device's `/root/online_battery.bin`; 22 951 parsed F3 records; CM records reach `u=1584` |
| `/root/battery.sh`, `/root/battery.log` (device) | — | the device-side battery script and its log (the `set -x` transcript of §6) |
| `scratch/uz801_fw/modem.elf` | (rejoined UZ801-21) | the UZ801 ELF used for all static work; 27 phdrs, no sections |
| `GitIgnore/MelbonWhiteStock_Dump/modem_extracted/image/modem.elf` | 50 049 024 B | the **stock HMU05 ELF** — the ground-truth control for §3/§7/§8 |
| `scratch/hmu05_stock_boot.bin` | 94 623 391 B | the stock boot capture (30 958 F3 records) |
| `scratch/race_boot_4.bin` | `a7f55a973108e325f6e5a20312b7f6e1` | the UZ801 BLIND boot capture used for §9's differential |
| `scratch/hmu05_stock/` | — | the stock HMU05 firmware set (modem.b00…b25, mba.mbn, modem.mdt) |

---

## 13. Reproduce

**The descriptor table (§3), for any module, in either firmware:**

```python
import struct
def load(p):
    d=open(p,'rb').read()
    e_phoff=struct.unpack_from('<I',d,0x1c)[0]; e_sz=struct.unpack_from('<H',d,0x2a)[0]
    e_n=struct.unpack_from('<H',d,0x2c)[0]
    segs=[]
    for i in range(e_n):
        o=e_phoff+i*e_sz
        t,off,va,pa,fs,mz,fl,al=struct.unpack_from('<IIIIIIII',d,o)
        segs.append((i,t,off,va,fs,fl))
    return d,segs
def off2va(segs,off):
    for i,t,o,va,fs,fl in segs:
        if t==1 and o<=off<o+fs: return va+(off-o)
def va2off(segs,va):
    for i,t,o,v,fs,fl in segs:
        if t==1 and v<=va<v+fs: return o+(va-v)
def entries(p, lo, hi):
    d,segs=load(p); out=[]
    for j in range(0,len(d)-8,4):
        w0,w1=struct.unpack_from('<II',d,j)
        if lo<=w1<hi:
            o2=va2off(segs,w1)
            if o2 is None: continue
            e=d.find(b'\x00',o2)
            out.append((off2va(segs,j), w0, d[o2:e].decode('latin1','replace')))
    return sorted(out)                      # sorted by packed == sorted by line

# UZ801 cmmsc_auto.c block:  entries('scratch/uz801_fw/modem.elf', 0xc4563000, 0xc4564200)
# STOCK cmmsc_auto.c block:  entries('.../MelbonWhiteStock_Dump/modem_extracted/image/modem.elf',
#                                    0xc44a3300, 0xc44a3900)
#   line = packed >> 16 ; level = packed & 0xFFFF
```

**The `immext` scan (§4) — with the two mandatory guards:**

```python
def immword(A):
    return ((A>>28)&0xF)<<24 | ((A>>20)&0xFF)<<16 | 0x4000 | ((A>>6)&0x3FFF)
def refs(d, segs, A):
    pat=struct.pack('<I', immword(A & ~0x3F)); hits=[]; pos=0
    while True:
        k=d.find(pat,pos)
        if k<0: break
        if k % 4 == 0:                       # GUARD 1: 4-byte alignment
            lo6=struct.unpack_from('<I',d,k+4)[0] & 0x3F
            if lo6 == (A & 0x3F):            # GUARD 2: next instruction's low 6 bits
                hits.append(hex(off2va(segs,k)))
        pos=k+1
    return hits
# calibration: refs(...,0xc0691d9c) -> 2 ; refs(...,0xc0692794) -> 1 ; refs(...,0xc06928a8) -> 0
```

**The op-mode battery (§6):**

```sh
ssh root@192.168.8.1 '/root/diag_bind.sh; /root/diag_logtool cntl-enable
  /root/diag_logtool capture 90 /root/online_battery.bin &
  for m in low-power persistent-low-power reset shutting-down online; do
    qmicli -d /dev/wwan0qmi0 --dms-set-operating-mode=$m
    sleep 3; qmicli -d /dev/wwan0qmi0 --dms-get-operating-mode
  done
  /root/at_tool "AT+CFUN?" /dev/wwan0at0; /root/at_tool "AT+CFUN=1" /dev/wwan0at0
  wait'
```

**The capture-window trap (§5.3):**

```python
d,ms = parse('scratch/online_battery.bin')
ts=[m['ts'] for m in ms if m['file']=='cfm_cpu_monitor.c']
print(len(ts), (max(ts)-min(ts))/204800)     # 19521, 1348.7  -> NOT a 90 s window
```
