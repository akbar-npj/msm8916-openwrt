# 214 — `tsk=dcc` IS THE AP's OP-MODE CARRIER (CONFIRMED WITH A NEGATIVE CONTROL), THE UZ801 `dcc` DIES IN 0.35 ms vs STOCK's 9.18 ms, AND THE MMOC DEBUG STRINGS LIVE IN ELF SEGMENT 26 (`filesz=0`)

**Ledger:** see `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md` — §3 (Achieved vs Expected) and §7 item 19.
**Status:** RESULTS
**Supersedes nothing; corrects two Doc 213 claims and re-scopes Doc 213 §10.** Doc 213 left three things open:
§12 step 3 (the `dcc` test — only its stock arm had been run), §6.3's reading of `u`, and §10's verdict that the
MMOC decision site is statically unreachable. This doc closes step 3 **with a negative control**, retracts the
`u` reading, and replaces §10's verdict with a **specific, fixable tooling defect**.

> **⚠ CORRECTED BY DOC 215 (2026-09-27) — read this before acting on §5.5 or §12 step 1.**
> **§5.5's conclusion is wrong.** "No 4-byte little-endian reference to the seg26 strings exists anywhere in the
> ELF" is true **only in the ELF's link space**. The firmware stores those pointers in a **native address space**,
> `native VA = ELF VA + 0xbeb1000` (solved and verified 13/14 in Doc 215 §3). Re-run in that space, the same
> search finds **5 hits in the ELF and 7 in the coredump** — including `mmocdbg.c`, `OPRT_MODE_CHGD`,
> `SUBSCRIPTION_CHGD`, `=MMOC= %s` and `ONLINE_GWL` — all in **file-backed** seg[18]/seg[19] (Doc 215 §3.2, §4).
> **§12 step 1 is therefore the wrong fix:** splicing seg26 content into the ELF cannot make a `0xd0d77cxx`
> reference resolve. Doc 215 §6 supersedes it; Doc 215 §9 step 1 replaces it. **Everything else in this doc
> stands** — in particular §3 (`tsk=dcc` carrier), §3.2 (0.352 ms vs 9.180 ms), §4 (QMI-accepted, ineffective),
> §6 (`u` retraction), §7.1 (same id, different payload) and §9 (the census diff). **§11.2's "the MMOC debug
> strings do not exist / are unrecoverable" was already corrected here; Doc 215 additionally recovers the
> complete `mmoc_cmd_name[]` (96 entries, 90 named).**

---

## 1. SOP compliance and Achieved vs Expected

### 1.1 SOP-compliance statement (per `197 §1`, modelled on `194 §1` / `133 §1`)

| Step | Taken? | Detail |
| :-- | :-- | :-- |
| 1. Backup | **YES (prior session)** | No deployment happened in this session; the device is byte-identical to the v19 set verified in the prior session (`modem.mdt 9f39ce579114bcf1383bbdce62a8d284`, `modem.b16 c71d53464a1e88f2072cbbb6f7f87475`, boot_id `26d74096-874f-45ee-9de4-67a8ee87caf1`, no `BOOTLOOP_REVERT.txt`). |
| 2. Ground-truth verification against stock HMU05 | **YES** | Every claim is a matched-instrument comparison. The stock arm is `hmu05_stock_boot.bin` (94.6 MB, the long stock boot) and `stock_boot_240.bin`; the UZ801 arms are `uz801_v19_boot_240.bin`, `doc210/boot_210_canon.bin` and **`doc210/live_cap.bin`** (the live UZ801 capture). All parsed with `scratch/f3parse.py` md5 `9f9d6edf15d64faf3b47897f7838c9ac`. |
| 3. Reconcile transport/config | **YES** | The live capture's own window is used as the clock check: it is **exactly 9 216 000 ticks = 45.00 s** at 204 800 Hz (§3.1) — an independent confirmation of the F3 clock rate, not an input to it. |
| 4. Surgical Hexagon patching + re-signing | **NOT TAKEN — deliberately** | Diagnostic only. No byte of any image was changed. Per `feedback_operating_rules.md` rule 1. |
| 5. Live empirical validation | **YES** | One new **read-only** device session (device state re-verified) plus the re-analysis of the already-captured live UZ801 arm. No new capture was taken; `live_cap.bin` and its log already existed on the device. |

**Steps skipped and why:** step 4, because the finding is a *tooling* defect plus a runtime carrier identity, neither of which is a byte to patch.
**No `sync` was needed** — nothing was written to the device or to `/overlay`.

### 1.2 Achieved vs Expected

| # | Intent | Expected | Achieved | Status |
| :-- | :-- | :-- | :-- | :-- |
| 1 | Run the **UZ801 arm** of Doc 213 §12 step 3 (`dcc` hypothesis) | A `CMD alloc tsk=dcc` correlated with an AP op-mode request | **`CMD alloc u=814, tsk=dcc` fires 75.1 ms before `qmicli --dms-set-operating-mode=online`, and the one request whose QMI client failed to open (T3) has NO alloc** — the negative control | **MET — carrier confirmed** |
| 2 | Measure the `dcc` command's lifetime on both stacks | Stock 9.18 ms | UZ801 **72 ticks = 0.352 ms**, stock **1 880 ticks = 9.180 ms** — **26.1×** | **MET** |
| 3 | Test whether the AP's online request has any effect | One of the two | `qmicli --dms-set-operating-mode=online` returns **"Operating mode set successfully"**, and the mode is **still `offline`** at the post-check | **MET — accepted, no effect** |
| 4 | Verify Doc 213's structural claim independently | Verified | **VERIFIED**: the two boot bring-ups are byte-identical up to `Prot_state [MAIN] 0(NULL)`; UZ801 then emits `OPRT_MODE_CHGD`, stock does not | **MET** |
| 5 | Locate the MMOC decision site statically (Doc 213 §10) | Blocked | **RE-SCOPED**: not "the strings are absent" but "the strings are in **ELF segment 26, whose `filesz` is 0**". They **are** present in the coredumps, and the alignment is exact | **MET (as a corrected diagnosis)** |
| 6 | Correct Doc 213 §6.3's `u` = uptime reading | A correction | **RETRACTED**: `u` is a monotonic CM command id, not a time. It stays **0** for every alloc in the whole UZ801 boot capture and runs 1…238 (stock) / 131…830 (UZ801 live) | **MET** |

---

## 2. The headline table

| quantity | stock (`hmu05_stock_boot.bin`) | UZ801 live (`doc210/live_cap.bin`) |
| :-- | --: | --: |
| AP marker `WMS_CMD_CFG_GET_ROUTES` (Processing) | ts 3 039 909 984 | ts 5 397 204 84 / 6 802 126 00 / 6 814 568 96 / 6 826 765 60 |
| `CMD alloc … tsk=dcc` | ts 3 040 010 292 | ts **680 698 432** |
| `CMD free … tsk=dcc` | ts 3 040 012 172 | ts **680 698 504** |
| **`dcc` lifetime** | **1 880 ticks = 9.180 ms** | **72 ticks = 0.352 ms** |
| what happens inside the lifetime | `cmmsc_auto` run → `PROT_GEN_CMD` ×3 → **`Recvd command 2(OPRT_MODE_CHGD)`** → `New transaction : 2(ONLINE)` | *nothing logged* (`mmocdbg.c` = 0 in this capture — §11.2) |
| `Prot_state [MAIN]` after | `5(ONLINE_GWL)` at ts 3 040 013 116 | never leaves `7(OFFLINE)` (established at boot, §8) |
| **ratio** | | **26.1×** |

---

## 3. §12 step 3 — `tsk=dcc` is the AP's op-mode carrier, with a negative control

### 3.1 The anchor

`live_opmode_cap.sh` records `uptime` at every step, and it ran for `LIVE_SECS=45`. The capture's own last
45 s must therefore be exactly `45 × 204 800 = 9 216 000` ticks, which the data satisfies **exactly**
(`688 701 008 − 679 485 008 = 9 216 000`). That fixes the map and makes the comparison a **measurement, not a
window statistic**:

```
ts = 679 485 008 + (uptime − 820.11) × 204 800
```

| qmicli request | uptime | ts | result | nearest preceding `CMD alloc` | delta |
| :-- | --: | --: | :-- | :-- | --: |
| **T1** `--dms-set-operating-mode=online` | 826.11 | 680 713 808 | **"Operating mode set successfully"** | **`u=814, tsk=dcc`** @ 680 698 432 | **−15 376 ticks = −75.1 ms** |
| **T2** `--nas-set-system-selection-preference=lte` | 832.15 | 681 950 800 | QMI error (3) `Internal` | `u=820, tsk=qmi_mmode` @ 681 934 848 | −15 952 = −77.9 ms |
| **T3** `--dms-set-operating-mode=online` (again) | 838.19 | 683 187 792 | **`CID allocation failed in the CTL client: endpoint hangup`** — **the request never reached the modem** | **none** | — |
| **T4** `--nas-set-system-selection-preference=lte` | 842.23 | 684 015 184 | QMI error (3) `Internal` | `u=830, tsk=qmi_mmode` @ 683 998 756 | −16 428 = −80.2 ms |

**⇒ `tsk=dcc` carries the AP's QMI DMS `Set Operating Mode`; `tsk=qmi_mmode` carries the AP's QMI NAS
`Set System Selection Preference`.** The three deltas (75.1 / 77.9 / 80.2 ms) are consistent, and **T3 is a
clean negative control**: when the QMI client could not be created, no `CMD alloc` appeared at all. That is
what a carrier correlation looks like when it is real.

**This closes Doc 213 §6.3's "n = 1" caveat.** The link "an AP op-mode request produces a `tsk=dcc` CM
command" is now **proven on the UZ801 arm with a negative control**, not merely correlated on stock.

### 3.2 What the UZ801 `dcc` command does NOT do

On stock, the `dcc` command's 9.18 ms lifetime contains the entire online transition:

```
alloc 3040010292
  +504 ticks   cmmsc_auto.c:2651/24189   updating op_mode … old_op_mode:11 new_op_mode:11
  +76  ticks   Recvd command 1(PROT_GEN_CMD)      (×3, each with an mmoc.c:13500 addl_action)
 +1340 ticks   Recvd command 2(OPRT_MODE_CHGD)
 +1368 ticks   New transaction : 2(ONLINE)
free  3040012172
 +944 ticks   Prot_state [MAIN] 5(ONLINE_GWL)
```

On UZ801 the same command is allocated and freed **72 ticks later with nothing in between**. The AP's
request demonstrably arrives (§3.1) and demonstrably has no effect (§4).

> **Scope note, per the standing do-not-re-open rule.** This does **not** re-open "the `dcc` break as the
> cause of the offline". The offline is established at boot, ~826 s before this `dcc` (§8), so the break is a
> **consequence**. What is new is that the carrier identity is now *proven* rather than hypothesised, and its
> abort is now *measured* (0.352 ms).

---

## 4. The AP's online request is **accepted at the QMI layer** and has **no effect**

From `live_cap.log` (fetched verbatim from the device):

```
--- baseline op mode at uptime=820.07 ---
[/dev/wwan0qmi0] Operating mode retrieved:
	Mode: 'offline'
	HW restricted: 'no'
--- T1 dms-set-operating-mode=online at uptime=826.11 ---
[/dev/wwan0qmi0] Operating mode set successfully
--- T2 … error: couldn't set operating mode: QMI protocol error (3): 'Internal'
--- T3 … error: couldn't create client for the 'dms' service: CID allocation failed in the CTL client: endpoint hangup
--- T4 … error: couldn't set operating mode: QMI protocol error (3): 'Internal'
--- post op mode at uptime=865.40 ---
[/dev/wwan0qmi0] Operating mode retrieved:
	Mode: 'offline'
```

**T1 succeeds and the mode is still `offline` 39 s later.** This is the **same fact** ModemManager reports
independently on the current device:

```
error: couldn't enable the modem: '…Core.Timeout: Timed out: Requested (online) and reloaded (offline)
modes did not match: Power update operation timed out'
```

**⇒ The AP is not failing to ask. The modem is accepting the ask and not acting on it.** Combined with §8,
the state that blocks it is MMOC's `Prot_state [MAIN] = 7(OFFLINE)`, latched at boot.

---

## 5. The MMOC debug strings live in **ELF segment 26**, whose `filesz` is **0**

Doc 213 §10 concluded route (a) (static location of the MMOC decision site) was blocked because

> "the MMOC debug format strings … are **not present in `modem.elf` at all** (0 hits, while the control
> `old_op_mode` is present at VA `0xc4563df2`)"

**That observation is correct but its conclusion is wrong.** The strings are not absent — they are in a
segment the ELF does not carry.

### 5.1 `mmocdbg.c` is absent from **every** modem ELF and present in **every** coredump

| image | `mmocdbg.c` | `Recvd command` | `Trans_state` | `OPRT_MODE_CHGD` | `mmoc.c` | `old_op_mode` |
| :-- | --: | --: | --: | --: | --: | --: |
| HMU05 stock `modem.elf` | **0** | 0 | 0 | 0 | 68 | 1 |
| UFI001B `modem.elf` | **0** | 0 | 0 | 0 | 6 | 0 |
| UZ801 `modem.elf` | **0** | 0 | 0 | 0 | 68 | 1 |
| `modem_coredump_p27.elf` | **1** | 1 | 1 | 1 | — | — |
| `modem_coredump_up919.52.elf` | **1** | **2** | 1 | 1 | — | — |

(All counts are `bytes.count()` in Python. **Do not use `grep -c` for this** — §14 trap 1.)

### 5.2 The alignment is exact: **coredump VA + `0x39800000` = ELF VA**

The HMU05 ELF has 27 program headers; six of them (`[6] [7] [9] [12] [20] [21] [26]`) have `filesz = 0`
and are present only as `memsz`. The coredump has 21 segments, and **their `filesz` values reproduce the
ELF's `memsz` values exactly, in order** — all 21 of them:

```
ELF  memsz: 4468 5376 139616 81920 172200 124392 46344 99492 115252 1663960 18341392
            663520 7580896 3078856 1320488 28936256 75808 503871 8396540 309120 13737984
core filesz: 4468 5376 139616 81920 172200 124392 46344 99492 115252 1663960 18341392
            663520 7580896 3078856 1320488 28936256 75808 503871 8396540 309120 13737984
```

The offset is a single constant, `0x39800000` (`0xc44e6000 − 0x8ace6000`), confirmed on every segment.
**The coredump is the ELF with every segment expanded to `memsz`.**

```
HMU05 ELF seg[26]:  va 0xc44e6000  off 0x02fbb000  filesz 0          memsz 13737984  end 0xc5200000
core    seg[20]:    va 0x8ace6000  off 0x044573cb  filesz 13737984               end 0x8ba00000
```

### 5.3 The missing segment has no source file either

`GitIgnore/MelbonWhiteStock_Dump/modem_extracted/image/` contains `modem.b00`…`modem.b25` — and
**no `modem.b26`**. The files that are missing (`b06 b07 b09 b12 b20 b21 b26`) are exactly the
`filesz = 0` program headers. So the content is not in the extraction at all; it is either loaded from a
source outside the `.mdt`/`.bNN` set or written by the modem at runtime. **The coredump is currently the only
source of it.**

### 5.4 What the strings actually are

The seg26 region is a **`(format, file)` pair table**, NUL-separated, walked in order:

```
…DORMANT_GWL\0=MMOC= %s\0mmocdbg.c\0Prot_state [MAIN] %d(%s) -- [HYBR_GW] %d(%s) -- [HYBR_HDR] %d(%s)\0
Prot_state [MAIN] %d(%s) -- [HYBR_GW] %d(%s) -- [HYBR_GW3] %d(%s)\0Curr_trans  %d(%s)\0
Trans_state %d(%s)\0Recvd report %d(%s)\0Recvd command %d(%s)\0New transaction           : %d(%s)\0…
```

alongside the **enum name tables** the `%s` arguments point into — which are therefore the enum *ordinals*
in order:

```
SUBSCRIPTION_CHGD \0 PROT_GEN_CMD \0 OPRT_MODE_CHGD \0 WAKEUP_FROM_PWR_SAVE \0 PROT_REDIR_IND \0 PROT_HO_I…
…ONLINE_CDMA \0 OFFLINE_CDMA \0 ONLINE_AMPS \0 OFFLINE_AMPS \0 ONLINE_GWL \0 ONLINE_HDR \0 ONLINE_DED_MEAS…
…WAIT_HYBR2_DEACTD_CNF \0 HYBR3_DEACT_ENTER \0 WAIT_HYBR3_DEACTD_CNF \0 WAIT_DEACTD_CNF_GWL \0 WAIT_1XCSFB_DEACT_CNF…
…PS_DETACH_ENTER \0 SUBSC_CHGD \0 OFFLINE \0 PWR_DOWN \0 PWR_SAVE_ENTER \0 DEACT_1XCSFB_CMD…
```

**This is a reusable asset**: the coredump yields the complete `Trans_state` / `Curr_trans` / `Prot_state` /
command name tables, so an F3 ordinal can always be decoded offline.

### 5.5 Why this did **not** immediately yield the emitter

The strings' VAs are now known in ELF space (`0xc4ec6cxx` for the HMU05 build), but **no reference to them
exists as a 4-byte little-endian immediate anywhere in the ELF** — not in the executable segments, not in the
data segments. The same is true for controls that are unambiguously referenced, e.g. `old_op_mode`
(`0xc44a3606`) and `=MMOC= standby_pref` (`0xc44a4c9f`). **The modem addresses seg24/25/26 through a
PC-/GP-relative form**, which is why both decompiles materialise **zero** tokens in the `0xc45xxxxx`–`0xc51xxxxx`
range while materialising tens of thousands in `0xc15xxxxx`–`0xc18xxxxx`. Ghidra rendered the seg26 globals
`unaff_GP + 0x…`, and it could not do better **because the segment had no file content to point into**.
⇒ §10's verdict becomes: **route (a) is blocked by a fixable input defect (segment 26 has no `filesz`), not
by the absence of the data.**

---

## 6. `u` is a CM command id, **not** the modem's uptime (Doc 213 §6.3 retracted)

Doc 213 §6.3 read `CMD alloc u=17, tsk=dcc` as "allocated at modem uptime 17 s … the cleanest confirmation
yet". **That is wrong.** The `u` value is baked into the already-resolved format string, so it is read
directly, and it does not track time:

| capture | `u` sequence, ts-ordered |
| :-- | :-- |
| `hmu05_stock_boot.bin` | 1, 2, 17, 17, 17, 18, 18, 18, 26, 54, 75, 107, 140, 173, 205, 238 |
| `uz801_v19_boot_240.bin` | **0, 0, 0, … 0** (every alloc in the whole capture) |
| `doc210/live_cap.bin` | 131, 134, 814, 820, 830 |

Stock's `u=18 → u=26` spans 5 651 299 940 ticks (2 759 s) while `u=26 → u=54` spans 5 638 000 ticks (27.5 s):
**8 increments in 2 759 s and 28 in 27.5 s**. It is not a time. It is a per-boot command id (or handle),
incremented per command issued.

**Open, and flagged as such:** UZ801's **boot** capture reports `u = 0` for *every* alloc while its **live**
capture reports 131…830. Either the field is uninitialised on that build until some point, or it is a
different variable. Not resolved; recorded so it is not mistaken for uptime again.

---

## 7. Doc 213's structural claim re-verified independently — and sharpened

Re-derived from the raw captures (not from Doc 213's text):

**STOCK-240, `code=0`, `Recvd command` in `ts` order**

```
 96168   Recvd command 4(PROT_REDIR_IND)     -> New transaction :  9(DUAL_STANDBY_CHGD)
 96432   Recvd command 0(SUBSCRIPTION_CHGD)  -> New transaction :  1(SUBSC_CHGD)   <- bring-up #1
144492   Recvd command 7(DUAL_STANDBY_CHGD) -> New transaction : 12(MMGSDI_INFO_IND)
404100   Recvd command 0(SUBSCRIPTION_CHGD) -> New transaction :  1(SUBSC_CHGD)   <- bring-up #2 (EF_SPN)
```

**UZ801-v19, `code=4774`**

```
4119791756  Recvd command 4(PROT_REDIR_IND)    -> New transaction :  9(DUAL_STANDBY_CHGD)
4119792212  Recvd command 0(SUBSCRIPTION_CHGD) -> New transaction :  1(SUBSC_CHGD)  <- bring-up #1
4119807520  Recvd command 2(OPRT_MODE_CHGD)    -> Curr_trans 3(OFFLINE)            <- *** UZ801 ONLY ***
4119848028  Recvd command 7(DUAL_STANDBY_CHGD) -> New transaction : 12(MMGSDI_INFO_IND)
```

The two bring-ups are **byte-identical** up to and including `Prot_state [MAIN] 0(NULL)` — same
`cmmsc_auto` mask `3774` (`MSC_AUTO: 0x200 & 0xebe = 0x200`), same `old_op_mode:10 new_op_mode:11`, same
`CM->MCS: op_mode=11`, same `Set SxLTE simul cap`, same `max_standby_subscriptions`, same
`trm_config_handler chain_mapping_modes New: 4, Old: 0`, same `mmocmmgsdi subsc_chg = 0x2c`, same
`cmd_ptr->param.subsc_chgd.active_ss 1`, same `standby_pref 1 / active_ss 1 / device_mode 0`.
**Then stock waits for `DUAL_STANDBY_CHGD` and UZ801 emits `OPRT_MODE_CHGD` 20 ticks (98 µs) after the
`New transaction : 0(NULL)`.**

### 7.1 The sharpening Doc 213's §3.3 table misses

Doc 213 frames the differential as a difference of **command id** (`0 SUBSCRIPTION_CHGD` vs
`2 OPRT_MODE_CHGD`). That is true of the *bring-up slot*, but it is not the general form — **stock also emits
`Recvd command 2(OPRT_MODE_CHGD)`**, on the AP path, and it goes ONLINE:

| | stock, AP path (`hmu05_stock_boot.bin`) | UZ801, boot path (`uz801_v19_boot_240.bin`) |
| :-- | :-- | :-- |
| command id | **`2 (OPRT_MODE_CHGD)`** | **`2 (OPRT_MODE_CHGD)`** — *the same* |
| when | uptime 17 s, 0.49 s after `WMS_CMD_CFG_GET_ROUTES`, inside the `dcc` lifetime | +75 ms after the boot `SUBSCRIPTION_CHGD`, no `dcc` involved |
| transaction created | **`2 (ONLINE)`** | **`3 (OFFLINE)`** |
| terminal `Prot_state [MAIN]` | **`5 (ONLINE_GWL)`** | **`7 (OFFLINE)`** |
| body | `WAIT_PH_STAT_CNF → PH_STAT_CHGD_CNF ×3 → PROT_PH_STAT_ENTER → NEW 0(NULL)` | `WAIT_DEACTD_CNF_GWL → PROT_DEACTD_CNF → WAIT_PH_STAT_CNF → PH_STAT_CHGD_CNF ×3 → PROT_PH_STAT_ENTER → NEW 0(NULL)` |

**⇒ The command id is identical. What differs is the target op-mode carried in the payload and the actor that
sends it.** MMOC's `OPRT_MODE_CHGD` handler is *shared*; the variable is the value it is handed. That is a
better-posed target than "the command id", because it says the emitter to find is **CM's op-mode source**,
not two different MMOC handlers.

---

## 8. Where the offline actually is (unchanged, restated for the record)

`Prot_state [MAIN] 7(OFFLINE)` is emitted **26×** on both UZ801 captures and **0×** on every stock capture
(including the two 240 s stock boots and the 54-`ONLINE_GWL` long capture). It is first set at **ts
411 980 8544**, 1 024 ticks after `Recvd command 2(OPRT_MODE_CHGD)`, i.e. **~75 ms after the boot bring-up
began** and **~826 s before** the live capture's `dcc` command of §3. **The offline precedes every AP
request.** It is latched: `OPRT_MODE_CHGD(OFFLINE)` is a deactivate-path command and the `New transaction`
print is emitted only on the activate path, so the state becomes visible as `Curr_trans 3(OFFLINE)` only
*after* the `PROT_DEACTD_CNF` report.

---

## 9. Boot source-file census diff (new)

Counting F3 `file` fields over each capture's whole (boot-only) stream:

| capture | code | records | distinct files |
| :-- | --: | --: | --: |
| `stock_boot_240.bin` | 0 | 1 186 | 65 |
| `stock_boot_211.bin` | 0 | 1 130 | 63 |
| `uz801_v19_boot_240.bin` | 4774 | 1 703 | 70 |
| `doc210/boot_210_canon.bin` | 4773 | 1 565 | 73 |

**13 source files appear in the UZ801 boot and in neither stock boot**

`ale_proc.c` (3/3) · `gstkutil.c` (3/1) · **`mcfg_uim.c` (9/9)** · `mcpm_drv_mux.c` (3/3) ·
`mgp_pe_common.c` (1/1) · `policyman_uim.c` (1/1) · `qmi_voice_msgr_if.c` (2/2) ·
`qpCircularBuffer.c` (0/3) · `qpDcm.c` (1/1) · `qpIO.c` (0/10) · `uim.c` (1/1) · `uimgen.c` (1/1) ·
**`uimsub_manager.c` (6/9)**

**8 source files appear in both stock boots and in neither UZ801 capture**

`cmlog.c` (6/9) · `gfc_qmi_internal.c` (1/0) · `lsmp_api.c` (0/1) · `nf_msg.c` (0/1) ·
`policyman_serving_system.c` (1/1) · `rcinit_rex_task.c` (1/0) · **`rf_task.c` (2/2)** · `sdp_core.c` (0/1)

**Also large and asymmetric, though not a presence/absence:** `cmss.c` **18/18 stock vs 0/1 UZ801**;
`sdcmd.c` 10/10 vs 1/1; `sdss.c` 8/8 vs 2/2; `qmi_nas.c` 7/9 vs 84/84.

**Read:** the UZ801-only set is dominated by a **UIM / SIM-Toolkit / voice** subsystem
(`mcfg_uim.c`, `uimsub_manager.c`, `uimgen.c`, `uim.c`, `policyman_uim.c`, `gstkutil.c`,
`qmi_voice_msgr_if.c`) plus QP drivers (`qpDcm.c`, `qpIO.c`, `qpCircularBuffer.c`). This is consistent with
the standing finding that UZ801 carries a fuller UIM/STK stack. It is a **candidate set**, not a mechanism:
`uim.c:7136 "Read to NV active slot configuration unsuccessful"` and `mcfg_uim.c:318
mcfg_uim_autoselect NV=5` are the only error-shaped UZ801-only boot records, and neither is causally tied to
MMOC yet.

---

## 10. The `logsite.py` segment filter is the same bug class it warns about

`scratch/logsite.py`'s own docstring records a prior failure:

> "An earlier `c16[0-9a-f]{5}` matched UZ801's `0xc16xxxxx` table but silently skipped stock's `0xc155xxxx`
> descriptors … i.e. it produced a **confident zero**."

The same shape recurs one level down: `SEG25_LO, SEG25_HI = 0xC4558000, 0xC4614CEC` is hard-coded to the
segment that holds the **`file:line` anchor table**. The **`mmocdbg.c`** records' strings are in **seg26**,
so any scan restricted to seg25 cannot see them. **Rule: before scoring a "not present" verdict on a
segment-filtered scan, print the segment range that was searched and confirm the target's segment is inside
it.** (Also: seg26 holds no `file:line` strings at all — its table is `(fmt, file)` pairs — so `logsite.py`'s
anchor model does not apply to it directly.)

---

## 11. What is ruled out, what is not, and device state

### 11.1 Ruled out

* **"The AP never asks the modem to go online."** Falsified twice: `live_cap.log` T1 returns *"Operating mode
  set successfully"*, and ModemManager's own timeout says *"Requested (online) and reloaded (offline)"*.
* **"`tsk=dcc` is not involved / UZ801 has no `dcc` commands."** Doc 213 §2's table (`CMD alloc tsk=dcc = 0`
  for both UZ801 captures) is **capture-specific and misleading**: those are *boot* captures. The live UZ801
  capture allocates `dcc` commands (`u=131`, `u=814`) and §3 ties one to a request with a negative control.
* **"The `OPRT_MODE_CHGD` command id is what differs."** See §7.1 — the id is identical on both stacks.
* **"`u` is the modem's uptime."** Retracted, §6.
* **"The MMOC debug strings do not exist / are unrecoverable."** They exist; they are in seg26; the coredump
  supplies them; §5.2 gives the exact alignment.

### 11.2 Not established (stated as limits)

* **Whether UZ801's 0.352 ms `dcc` produces any `OPRT_MODE_CHGD` at all.** `mmocdbg.c` = **0** in
  `live_cap.bin` (the Doc 208 blind-class blocker). The live capture cannot see the MMOC layer.
* **The upstream trigger of the boot-path `OPRT_MODE_CHGD`.** Still open; §5.5 explains why the static route
  needs a fixed ELF input first.
* **The exact meaning of `u`** (§6) and of UZ801's all-zero boot values.
* **Whether `ale_proc.c` / the QP drivers matter.** Present in the census, no mechanism.

### 11.3 Device state (verified this session, unchanged)

UZ801 v19 · boot_id `26d74096-874f-45ee-9de4-67a8ee87caf1` · uptime 5 110 s · `modem.mdt`
`9f39ce579114bcf1383bbdce62a8d284` · no `BOOTLOOP_REVERT.txt` · `mmcli -m 3` firmware
`UZ801_V3.0_21_V01R01B10`, `state: disabled`, `power state: off`.

---

## 12. Next steps, in priority order

1. **Fix the ELF input, then re-locate the emitter (route (a), now unblocked in principle).** Build an ELF
   whose seg24/25/26 carry their real content (`filesz` from the coredump, offset by `0x39800000`) and re-run
   Ghidra. Then `logsite.py`/`xref` can resolve the seg26 globals and the `OPRT_MODE_CHGD` emitter becomes a
   normal xref query. **This is the highest-value action** and it is mechanical.
2. **Obtain a UZ801 coredump.** The stock coredump fixes the *stock* build only; the UZ801 seg26 is a
   different size (`memsz 11448320` at VA `0xc4615000`) and has no coredump. A coredump of the UZ801 modem —
   even one taken while it is sitting `offline` — would carry its seg26 and let step 1 be done on the port's
   own build. **Investigate a safe SSR trigger** (`/sys/kernel/debug/msm_subsys/modem` if present); **do not**
   use `echo stop > /sys/class/remoteproc/remoteproc0/state` — that reset the AP (Doc 213-era finding).
3. **Re-capture the live UZ801 arm with the MMOC class enabled.** `live_cap.bin` is blind to `mmocdbg.c`
   (86 % of its records are `cfm_cpu_monitor.c`). The §3 experiment should be repeated with a mask that
   demonstrably delivers `mmocdbg.c > 0`, so the 0.352 ms `dcc`'s contents are visible.
4. **Follow the `cmss.c` asymmetry** (18/18 stock vs 0/1 UZ801) — cheap, and it is a whole subsystem that is
   silent on the port.
5. **Correct the `boot_210_canon.bin` label** wherever it is cited as stock (Doc 213 §8 — it is UZ801).

**Do NOT re-open:** `cmcc.c`/GROUP 11, `0x456`/`0x422`, the `u=1` race, the `evt=0` patch, `0x1000001`,
`cm_state+0x39e8`, "the CM code differs", the `mode_pref` 9728-vs-38 difference, the `MSC_AUTO`
`0x220`-vs-`0xebe` difference as a code difference, the policyman RAT-mask-0 lead, the `dcc` break **as the
cause** of the offline, `PROT_GEN_CMD` as the online gate, or the `cmph rat_disabled_mask` sequence as the
emitter. **Do NOT** compare `file:line` across builds; **do NOT** score a CM-layer negative on a capture with
`rcinit_init.c` = 0 or `mmocdbg.c` = 0; **do NOT** take an `args` value from a parser older than
`9f9d6edf15d64faf3b47897f7838c9ac`.

---

## 13. Evidence

| artifact | size / md5 | use |
| :-- | :-- | :-- |
| `scratch/hmu05_stock_boot.bin` | 94 623 391 · `21506cace2e94824c21c597cb33f35a1` | stock arm; `dcc` lifetime 9.18 ms; `OPRT_MODE_CHGD`→ONLINE |
| `scratch/doc211/stock_boot_240.bin` | 1 500 441 · `0ff4e4d22c23443e2c6e82bcb24cbc83` | stock boot bring-up reference |
| `scratch/doc211/uz801_v19_boot_240.bin` | 1 240 397 · `81318b04f7e0da3e74675d2973366758` | UZ801 boot bring-up; `OPRT_MODE_CHGD(OFFLINE)` |
| `scratch/doc210/boot_210_canon.bin` | 1 163 362 · `b693faebb2bab65ca5c9eb948285658b` | second independent UZ801 boot |
| **`scratch/doc210/live_cap.bin`** | 3 397 120 | **the live UZ801 arm — §3** |
| **`scratch/doc210/live_cap.log`** | 8 550 | **the qmicli timeline and results — §3.1, §4** |
| `scratch/coredump_live/modem_coredump_up919.52.elf` | 85 398 475 | **seg26 content + `mmocdbg.c` strings — §5** |
| `GitIgnore/MelbonWhiteStock_Dump/modem_extracted/image/modem.elf` | 50 049 024 | HMU05 ELF; seg26 `filesz=0` |
| `scratch/uz801_fw/modem.elf` | 50 912 492 · `50ee8c56d6b836329ef97ac83ed34bff` | UZ801 ELF; seg26 `filesz=0` |
| `scratch/f3parse.py` | md5 `9f9d6edf15d64faf3b47897f7838c9ac` | the only admissible parser |
| `scratch/doc213_dcc_stock.py` · `doc213_window.py` · `doc213_mmoc.py` · `doc213_upstream.py` · `doc213_filediff.py` · `doc214_anchor.py` · `doc213_seg26_real.py` | — | the analyses in this doc |

---

## 14. Traps (new; Doc 213's list still stands)

1. **`grep -c` counts matching *lines*, not occurrences.** A modem ELF is largely one "line", so `grep -c`
   returns 0 or 1 and never a true count. Doc 213 §10's "0 hits" for the MMOC strings happened to be right,
   but the same method silently reports `1` for a string that occurs 45 times. **Use `bytes.count()` in
   Python for any string census on these images.**
2. **A segment-restricted scan produces a confident zero.** `logsite.py`'s `SEG25` constant is exactly the
   filter that hides seg26 (§10). Always print the searched range.
3. **`filesz = 0` does not mean "empty at runtime".** Six program headers are `filesz=0` yet hold real data
   in the coredump. A static tool that trusts the ELF sees BSS where the modem sees a string table.
4. **A capture's `ts` span is not its duration.** `live_cap.bin`'s `code=4773` stream spans 787.58 s while the
   capture ran 45 s — `cntl-enable` does **not** give a clean window. Anchor to the script's own `uptime`
   lines, and check that the derived window length equals `SECS × 204800`.
5. **`CMD alloc tsk=dcc` presence is capture-dependent.** Boot captures (240 s, cold) show 0; the live capture
   shows several. A census column is only comparable between captures of the **same kind**.
6. **A QMI client-creation failure is a perfect negative control — use it.** T3 in §3.1 is what makes the
   `dcc`↔request pairing a measurement instead of a correlation.
7. **Cross-build line numbers move** (`mmocdbg.c` 288 UZ801 vs 291 stock; `cmdbg.c` 2186 vs 2181;
   `cmph.c` 32761 vs 32055). Never diff `file:line` across builds.

---

## 15. Ledger fold-in

`197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md` gains **§7 item 19**, carrying: the `dcc` carrier confirmation
with its negative control (§3), the 0.352 ms vs 9.180 ms lifetime (§3.2), the QMI-accepted-but-ineffective
online request (§4), the seg26 / `filesz=0` discovery and the exact coredump alignment (§5), the `u`
retraction (§6), the sharpened "same id, different payload" formulation (§7.1), the census diff (§9), and the
re-prioritised next steps (§12).
