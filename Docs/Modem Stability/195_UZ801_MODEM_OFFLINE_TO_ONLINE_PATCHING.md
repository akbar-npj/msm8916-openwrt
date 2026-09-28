# 195 — UZ801 Modem `offline → online` Patching: DMS, CM, and Boot-Path Analysis

**Date:** 2026-09-25 / 2026-09-26  
**Device:** HMU05 4G modem stick (`Melbon HMU05`), OpenWrt, `192.168.8.1`, SSH root.  
**Firmware:** UZ801 baseband `MPSS.DPM.1.0.1.C1-00121`  
**Status:** IN PROGRESS — QMI error 52 (`DeviceNotReady`) persists after patching DMS + CM.  
**Companion:** Doc 194 (boot-log differential proving the LTE stack never initialises).  

---

## 1. Problem Statement

The UZ801 modem firmware boots into `offline` mode and stays there. Any attempt to bring it online fails:

```
$ qmicli -p -d /dev/wwan0qmi0 --dms-set-operating-mode=online
error: couldn't set operating mode: QMI protocol error (52): 'DeviceNotReady'

$ echo "AT+CFUN=1" > /dev/ttyACM0
+CME ERROR: phone failure
```

The stock HMU05 firmware boots to `online` within ~2 seconds without any external command. The UZ801 firmware has the same CM subsystem code but a **different boot path** that never triggers the LTE/RF protocol stack initialisation.

---

## 2. Root Cause Analysis (from Doc 194)

### 2.1 The Boot-Path Gap

Differential F3 log analysis (stock HMU05 vs UZ801, same AP/NV/hardware):

| Metric | Stock HMU05 | UZ801 |
|--------|-------------|-------|
| Boot F3 messages | 31,443 | 5,461 |
| F3 log size | 94.6 MB | 1.54 MB |
| Log-emitting source files | 40 | 23 |
| Boot operating mode | `online` | `offline` |
| Time to online | ~2 s | Never |

**25 subsystems run in the stock boot path that UZ801 never touches**, including:
- **LTE RF layer:** `rflte_core_rxctl.c` (×8,388 messages), `rflte_mc_meas.c`, `rfmeas_mc.c`
- **CM serving-system/call modules:** `cmss.c`, `cmcall.c`, `cmlog.c`
- **MCPM:** `mcpm.c`, `mcpm_npa.c`, `mcpm_saw.c`
- **MMOC:** `mmocdbg.c` reporting `Prot_state [MAIN] 5(ONLINE_GWL)`
- **System-determination:** `sdss.c`
- **Transceiver Resource Manager:** `trm_config_handler.c`
- **A2 IP filter:** `a2_ipfilter.c`

All 25 appear within 0.78 s of capture start (modem uptime ≈ 2.1 s), **before any AP userspace runs**.

### 2.2 Why DMS Returns Error 52

The DMS `set-operating-mode` handler (`FUN_c0e03974` at VA `0xc0e03974`) has two gate checks:

1. **Ready check** at `0xc0e03a80`: `r0 = memub(r20+#0x5a)` — reads a "ready" byte from the DMS context. Zero means "not ready". Since the LTE stack never initialised, this byte is 0 → **jumps to restricted mode filter**.

2. **Mode filter** at `0xc0e03aac`: only accepts `r1 == 1` (QMI LPM) or `r1 == 6` (persistent LPM). QMI online = `r1 == 0` → **rejected, falls through to error path at `0xc0e03b80`** → returns QMI error 52.

Even for a "ready" modem (byte `0x5a` ≠ 0), online mode (r1=0) is explicitly sent to an error logging path at `0xc0e03a98` with error code `0x53`.

> [!IMPORTANT]
> The DMS firmware **intentionally blocks** runtime `set-operating-mode=online` via QMI. The stock firmware achieves online mode through its own **internal boot path** (MMOC → `PROT_GEN_CMD`), never through QMI DMS.

---

## 3. Architecture: The Mode Transition Chain

### 3.1 Normal (Stock) Online Transition

```mermaid
flowchart LR
    A["Boot Init<br/>FUN_c0f8ae6c"] --> B["MMOC<br/>PROT_GEN_CMD(1)"]
    B --> C["CM State Machine<br/>cm_ph_cmd_oprt_mode"]
    C --> D["RF Init<br/>WTR1605 + PA"]
    D --> E["LTE Protocol Stack<br/>rflte_core, NAS, RRC"]
    E --> F["Mode: ONLINE<br/>Network Attach"]
```

### 3.2 Our Attempted Path (QMI DMS → CM)

```mermaid
flowchart LR
    Q["qmicli<br/>set-operating-mode=online"] --> D["DMS Handler<br/>FUN_c0e03974"]
    D -->|"r19=5 (online)"| M["Mode Mapper<br/>FUN_c0e000bc"]
    M --> CM["CM Command<br/>cm_ph_cmd_oprt_mode"]
    CM --> W["CM Whitelist<br/>FUN_c0683c88"]
    W --> H["CM Handler<br/>FUN_c067ef58"]
    H --> T["CM Transition<br/>FUN_c067c324"]
    T --> MMOC["MMOC Post"]
```

**Every node in this chain has a rejection gate that blocks online mode.**

### 3.3 Key Data Structures

| Structure | Location | Purpose |
|-----------|----------|---------|
| GP register | `0xc3d40000` (seg 22 base) | Global pointer for all GP-relative data |
| CM state array | `memw(gp+#0x32fc)` | Array of subscription contexts, each `0x1cf4` bytes |
| DMS context | returned by `FUN_c0e095e4` | DMS state at `0xc3b4fd30` |
| DMS ready byte | `memub(DMS_ctx + 0x5a)` | 0 = not ready, non-zero = ready |
| DMS current mode | `memb(DMS_ctx + 0x39)` | Stored operating mode index |

### 3.4 QMI → SYS_OPRT_MODE Mapping

The DMS handler uses two jumptables:

**Jumptable 1 — QMI mode to r19 (at DMS handler `0xc0e03a58`):**

| QMI Mode | QMI Value | r19 | Name |
|----------|-----------|-----|------|
| Online | 0 | 5 | `SYS_OPRT_MODE_ONLINE` |
| Low Power | 1 | 6 | `SYS_OPRT_MODE_LPM` |
| Factory Test | 2 | 1 | `SYS_OPRT_MODE_FTM` |
| Offline | 3 | 2 | `SYS_OPRT_MODE_OFFLINE` |
| Reset | 4 | 7 | `SYS_OPRT_MODE_RESET` |

**Jumptable 2 — FUN_c0e000bc mode index to mode_byte (at `gp+0xc7b8` → `0xc1ba7848`):**

| Index (r18) | Target VA | Stores mode_byte |
|-------------|-----------|-----------------|
| 0 | `0xc0e000f0` | 5 |
| 1 | `0xc0e000f8` | 2 |
| 2 | `0xc0e00100` | 3 |
| 3 | `0xc0e00100` | 3 |
| 4 | `0xc0e00100` | 3 |
| 5 | `0xc0e00108` | 0 |
| 6 | `0xc0e00110` | 1 |
| 7 | `0xc0e00118` | 4 |
| 8 | `0xc0e00120` | 8 |

---

## 4. Firmware Binary Layout

**Segment 16** (`modem.b16`): VA `0xc0287000`, file size `0x128b0f0` (19,443,952 bytes).  
Contains all CM, DMS, and RFC code. This is the ONLY segment we patch.

**Pristine MD5:** `848abe7eea4b772741eaaf3225dc5434`

> [!NOTE]
> The segment numbering in `.bXX` files is offset by 1 from the ELF program header indices. `modem.b16` corresponds to program header index 16 but contains the code described as "segment 17" in some contexts. Always use file offsets relative to `modem.b16` base VA `0xc0287000`.

**Hash tool:** `python3 GitIgnore/compare/ufi001b_hash_tool.py patch scratch/uz801_patched 16 scratch/uz801_patched/modem.b16`

---

## 5. Blocking Points Identified

### 5.1 Gate 1: DMS Ready Check + Mode Filter (`FUN_c0e03974`)

**Location:** VA `0xc0e03a80` (file offset `0xb7ca80`)

```
c0e03a80: r0 = memub(r20+#0x5a)                              ; DMS ready byte
c0e03a84: if (cmp.eq(r0.new,#0x0)) jump:nt 0xc0e03aac         ; if not ready → restricted path
c0e03a88: if (cmp.gtu(r1,#0x5)) jump:t 0xc0e03ab0             ; mode > 5 → check mode 6
c0e03a8c: if (cmp.eq(r1,#0x0)) jump:nt 0xc0e03a98             ; mode 0 (ONLINE) → ERROR!
c0e03a90: if (cmp.eq(r1,#0x1)) jump:t 0xc0e03ab4              ; mode 1 (LPM) → proceed
c0e03a94: if (!cmp.eq(r1,#0x2)) jump:t 0xc0e03b80             ; mode 2 → proceed, else reject

; Restricted path (ready byte == 0):
c0e03aac: if (cmp.eq(r1,#0x1)) jump:t 0xc0e03ab4              ; LPM only
c0e03ab0: if (!cmp.eq(r1,#0x6)) jump:t 0xc0e03b80             ; persistent LPM only, else REJECT

; Proceed path:
c0e03ab4: call FUN_c0e000bc                                    ; map mode + build CM command
```

**Key insight:** Online mode (r1=0) is rejected in BOTH paths — the "ready" path sends it to error `0xc0e03a98` with code `0x53`, and the "not ready" path at `0xc0e03aac` only accepts LPM/persistent-LPM.

### 5.2 Gate 2: CM Operating Mode Whitelist (`FUN_c0683c88`)

**Location:** VA `0xc0683c88` (file offset `0x3fcc88`)

This function checks the oprt_mode value from the CM command buffer against a whitelist:
- `0x1000002` (LPM) → accepted, return 2
- `0x1000026` (FTM) → accepted, return 2
- `0x1000007` (RESET) → accepted, return 2
- Everything else (including `0x1000001` ONLINE) → **rejected, return 0**

**Twin function for subscription 1:** `FUN_c0b57c2c` at VA `0xc0b57c2c` (offset `0x8d0c2c`), identical structure.

### 5.3 Gate 3: CM Handler Has No ONLINE Dispatch (`FUN_c067ef58`)

**Location:** VA `0xc067ef58` (file offset `0x3f7f58`)

After `FUN_c0683c88` returns 2 (accepted), the handler dispatches by mode value:

```
c067ef70: if (r1 == 0x1000026) jump FTM_handler     ; FTM → handled
c067ef7c: if (r1 == 0x1000007) jump RESET_handler   ; RESET → handled
c067ef88: immext(#0x1000000)
c067ef8c: r0 = ##0x1000002                           ; LPM constant
c067ef90: if (r0 != r1) jump ERROR_LOG               ; NOT LPM → ERROR!
; LPM handler:
c067ef94: r0 = #0
c067ef98: r1 = memw(gp+#0x32fc)
c067ef9c: memb(r1+#0x2) = #0x0
...
c067efb0: call FUN_c067c324                          ; ← CM state transition!
c067efb4: memb(r1+#0x4) = #0x1
c067efb8: jump epilog
```

**There is NO handler for `0x1000001` (ONLINE).** It falls through to the error log at `0xc067f090`.

**Twin function for subscription 1:** At VA `0xc0b50fbc` (offset `0x8c9fbc`), same structure but calls `FUN_c0b4f454` (equivalent of `FUN_c067c324`).

### 5.4 Gate 4: CM Command Processor Ready Check (`FUN_c06840e4`)

**Location:** VA `0xc06840e4` (file offset `0x3fd0e4`)

Checks `memub(cm_state + param_3*0x1cf4 + 4) == 1` (CM initialised flag). If CM is not fully initialised, the command is dropped before reaching the handler.

### 5.5 Gate 5: Readiness Check Function (VA `0xc0821380`)

**Location:** VA `0xc0821380` (file offset `0x59a380`)

Reads `memuh(##0xc36822a8)` and returns 0 if the value is 0 or > 0xFF. Used by callers to check if the modem subsystem is ready. Force-returning 1 bypasses this.

### 5.6 Gate 6: RFC Card ID

The RF front-end card identification logic needs to resolve to card ID `0x60` (WTR1605 transceiver) for proper RF initialisation.

---

## 6. Patches Applied (Current v2 — 134 diff bytes from pristine)

**Patched MD5:** `5cce2c2a8f48350d75532aa13e637a35`

### 6.1 DMS Bypass (Gate 1)

| File Offset | VA | Pristine | Patched | Purpose |
|-------------|-----|----------|---------|---------|
| `0xb7ca80` | `0xc0e03a80` | `40 4b 34 91` | `1a c0 00 58` | `jump 0xc0e03ab4` — skip ready check AND mode filter, go directly to `FUN_c0e000bc` call |
| `0xb7ca84` | `0xc0e03a84` | `16 c0 02 24` | `00 c0 00 7f` | NOP (was conditional jump, now unreachable) |

**Effect:** ALL operating modes (including online) now reach `FUN_c0e000bc` which maps the mode and posts a `cm_ph_cmd_oprt_mode` command to CM.

**Previous bug:** Earlier patch jumped to `0xc0e03aac` (bytes `16 c0 00 58`) instead of `0xc0e03ab4` (bytes `1a c0 00 58`). The `0xc0e03aac` path still only accepts LPM/persistent-LPM, rejecting online.

### 6.2 CM Whitelist Bypass (Gate 2)

| File Offset | VA | Pristine | Patched | Purpose |
|-------------|-----|----------|---------|---------|
| `0x3fcc88` | `0xc0683c88` | `c4 61 35 5b 02 c0 9d a0` | `c0 3f 20 48 00 c0 00 7f` | `{ r0 = #2; jumpr r31 }` + NOP — force return 2 (accepted) |
| `0x8d0c2c` | `0xc0b57c2c` | `64 7f 9b 5b 02 c0 9d a0` | `c0 3f 20 48 00 c0 00 7f` | Same for subscription 1 twin |

**Effect:** CM unconditionally accepts any operating mode command without checking the whitelist.

### 6.3 CM Handler Mode Check Removal (Gate 3)

**Subscription 0 (`FUN_c067ef58`):**

| File Offset | VA | Pristine | Patched | Purpose |
|-------------|-----|----------|---------|---------|
| `0x3f7f88` | `0xc067ef88` | `00 40 10 00` | `00 c0 00 7f` | NOP (was `immext(#0x1000000)`) |
| `0x3f7f8c` | `0xc067ef8c` | `40 40 00 78` | `00 c0 00 7f` | NOP (was `r0 = ##0x1000002`) |
| `0x3f7f90` | `0xc067ef90` | `84 e1 42 20` | `00 c0 00 7f` | NOP (was `if (!cmp.eq(r0.new,r1)) jump error`) |

**Subscription 1 (at `0xc0b50fbc`):**

| File Offset | VA | Pristine | Patched | Purpose |
|-------------|-----|----------|---------|---------|
| `0x8c9ff0` | `0xc0b50ff0` | `00 40 10 00` | `00 c0 00 7f` | NOP immext |
| `0x8c9ff4` | `0xc0b50ff4` | `40 40 00 78` | `00 c0 00 7f` | NOP r0 load |
| `0x8c9ff8` | `0xc0b50ff8` | `17 40 00 00` | `00 c0 00 7f` | NOP immext (jump target) |
| `0x8c9ffc` | `0xc0b50ffc` | `20 e1 42 20` | `00 c0 00 7f` | NOP conditional jump |

**Effect:** After the FTM and RESET checks, ALL remaining modes (including online) fall through to the handler that calls `FUN_c067c324(0)` → posts `SYS_OPRT_MODE` to MMOC.

### 6.4 CM Command Processor Ready Check Bypass (Gate 4)

| File Offset | VA | Pristine | Patched | Purpose |
|-------------|-----|----------|---------|---------|
| `0x3fcce4` | `0xc0683ce4` | `20 40 20 91 18 e1 02 24` | `18 c0 00 58 00 c0 00 7f` | Skip mode validation jump |
| `0x3fd131` | `0xc0684131` | `61 0a 10` | `40 00 58` | NOP ready-check conditional |
| `0x3fd1dc` | `0xc06841dc` | `56 e0 4b 10` | `00 c0 00 7f` | NOP sVar5==0 rejection |
| `0x3fd23c` | `0xc068423c` | `a1 43 31 91 ...` (16 bytes) | `00 c0 00 7f` × 4 | NOP validation block |

### 6.5 Readiness Check Override (Gate 5)

| File Offset | VA | Pristine | Patched | Purpose |
|-------------|-----|----------|---------|---------|
| `0x59a380` | `0xc0821380` | `8a 60 36 0c ...` (56 bytes) | `c0 3f 10 48` + NOPs | `{ r0 = #1; jumpr r31 }` — force "ready" |

The entire function body (14 instructions) is replaced with a single return-1 + 12 NOPs.

### 6.6 Thunk Caller NOPs

| File Offset | VA | Pristine | Patched | Purpose |
|-------------|-----|----------|---------|---------|
| `0x551560` | `0xc07d8560` | `fa` | `00` | NOP part of thunk caller check |
| `0x551563` | `0xc07d8563` | `10` | `7f` | NOP part of thunk caller check |

### 6.7 RFC Card ID

| File Offset | VA | Pristine | Patched | Purpose |
|-------------|-----|----------|---------|---------|
| `0xaddba0` | `0xc0d64ba0` | `00 40 9f 52` | `00 4c 00 78` | Force RFC card ID = `0x60` |
| `0xaddba4` | `0xc0d64ba4` | `6d 4d 1f 0c` | `00 c0 9f 52` | Return after card ID set |
| `0xaddba8` | `0xc0d64ba8` | `20 c1 20 49` | `00 c0 00 7f` | NOP remaining |
| `0xe015b8` | `0xc10885b8` | `30 60 20 73` | `30 c0 30 7c` | Second RFC card ID site |

---

## 7. Test Results

### 7.1 Current Result (v2 patches, 2026-09-26 01:18 IST)

```
$ qmicli -p -d /dev/wwan0qmi0 --dms-get-operating-mode
Mode: 'offline'
HW restricted: 'no'

$ qmicli -p -d /dev/wwan0qmi0 --dms-set-operating-mode=online
error: couldn't set operating mode: QMI protocol error (52): 'DeviceNotReady'
```

**Still fails with error 52.** The DMS bypass jumps to `0xc0e03ab4` which calls `FUN_c0e000bc(memb(r20+0x39), &mode_byte)`. The issue is now likely:

1. **`memb(r20+0x39)` contains an unexpected value** — this is the stored current mode from the DMS context, which may be uninitialised or set to a value that maps to an invalid SYS_OPRT_MODE.
2. **FUN_c0e000bc fails** to allocate the CM command or encounters an error in the downstream processing.
3. **The error 52 comes from a DIFFERENT path** — there may be additional checks in the DMS handler AFTER `FUN_c0e000bc` returns that still gate the response.

### 7.2 F3 Log Evidence (from earlier captures)

F3 logs during `--dms-set-operating-mode=online` showed:
```
=CM= CMD alloc u=158, tsk=ds
=CM= CMD free u=158, tsk=ds, ftsk=cm
```

This proves:
- The command **does** reach CM (alloc succeeded)
- CM **processes and frees** the command without acting on it
- **No `cm_ph_cmd_oprt_mode` log appears** — the mode transition function was never called

### 7.3 Previous Patch Iterations

| Version | Diff Bytes | DMS Target | CM Whitelist | CM Handler | Result |
|---------|-----------|------------|--------------|------------|--------|
| `.patched` (earliest) | 16 | Not patched | Not patched | Not patched | Error 52 |
| `.bak` / `.cm_patched` | 83 | `jump 0xaac` (wrong!) | `jump 0x3ce4` | Not patched | Error 52 |
| `.oprt_jump` | 102 | `jump 0xaac` (wrong!) | Various | Various | Error 52 |
| v2 | 134 | `jump 0xab4` | ret 2 | NOP mode check | Error 52 |
| v3 | 140 | `jump 0xab4` + NOP result checks | ret 2 | NOP mode check | Error 52 |
| v4 | 140 | `r21=#1; jump 0xc50` (direct cm_ph_cmd) | ret 2 | NOP mode check | ⚠️ BOOT LOOP |
| v5 | 134 | `jump 0xab4` + HWID `0x48` | ret 2 | NOP mode check | Error 52 (`0xab4` is LPM path!) |
| v5 | 134 | `jump 0xab4` + HWID `0x48` | ret 2 | NOP mode check | Error 52 (`0xab4` is LPM path!) |
| v6 | 134 | `jump 0xb80` + HWID `0x48` | ret 2 | NOP mode check | Error 52 (CM handler has no online dispatch!) |
| **v7** | **145** | **`call FUN_c0f8ae64` + `jump 0xc0f98240` (Auto-Online)** | **ret 2** | **NOP mode check** | **Tested (Disproved FUN_c0f8ae64 path)** |
| **v8** | **167** | **`call FUN_c0f8ae64` + sync success return (`r22=0`)** | **ret 2** | **NOP mode check** | **Error 52 eliminated! QMI returns Success in <1s; Jio SIM fully read; FUN_c0f8ae6c aborted due to uninit DAT_c33991e8** |
| **v9** | **148** | **`call FUN_c067cc2c` + stored mode 0 + sync success** | **ret 2** | **Site 2 force MMOC mode 5** | **⚠️ BOOT LOOP (c067cc78 called during early boot before RF init)** |
| **v10** | **175** | **`set cm_state+3=5` + `call FUN_c067cc2c(1)` + stored mode 0 + sync success** | **ret 2** | **NOP mode check + pristine c067cc78** | **⚠️ BOOT LOOP (`FUN_c067cc2c` crashes when called cross-task from `ds`)** |
| **v11–v12** | **~200** | **`jump 0xc0e03b80` (native CM dispatch)** | **`{ r0=#2; jumpr r31 }`** | **NOP mode check** | **Error 52 (CM whitelist skipped `cm_state+0x39e8=3` init → MMOC event handler aborts)** |
| **v13** | **261** | **`jump 0xc0e03b80`** | **44-byte blocks: set `cm_state+0x39e8/f0=3`, copy txn/sub IDs, ret 2** | **NOP mode check + `cm_state+2=1`** | **Stable boot, 5h+ uptime, mode stays `offline` (QMI DMS reaches CM but CM has no ONLINE dispatch)** |
| **v14** | **265** | **v13 + cmmsc_auto.c mode gate bypass** | **v13 (44-byte blocks)** | **v13 + bypass `if(mode==9)` at `0xc0691950`** | **IN PROGRESS — testing** |

> [!IMPORTANT]
> **Major Architectural Discovery: Qualcomm CM Has NO Userspace Online Dispatcher!**
> Exhaustive analysis of the entire 3.8-million-line Ghidra decompilation revealed that `cm_ph_cmd_oprt_mode` / `FUN_c067ef58` **DOES NOT IMPLEMENT** an online mode handler for userspace dispatch. The ONLY modes handled in `FUN_c067ef58` are:
> 1. `0x1000026` = FTM (Factory Test Mode)
> 2. `0x1000007` = RESET
> 3. `0x1000002` = LPM (Low Power Mode)
>
> When v2-v6 NOP'd the mode check in `FUN_c067ef58`, the function fell through to the LPM handler:
> ```c
> *(undefined1 *)(cm_state + 2) = 0; // Mode 0
> FUN_c067c324(0);
> ```
> And inside `FUN_c067c324`, when mode is 0:
> ```c
> *(undefined4 *)(iVar4 + 8) = 0x1000006; // MMOC command 0x1000006 = SYS_OPRT_MODE_LPM!
> thunk_EXT_FUN_d07f4cc8(iVar4);          // Posts LPM to MMOC!
> ```
> In other words, every userspace attempt to go online via CM was actually commanding MMOC to enter **Low Power Mode**!
>
> **The True Online Path: MMOC `PROT_GEN_CMD(1)` (`0x1000001`)**:
> Across the entire codebase, `0x1000001` (`SYS_OPRT_MODE_ONLINE`) is handled in only 3 places:
> 1. `FUN_c067cd2c` (MMOC event handler in CM — responds to MMOC `0x1000001` by starting the RF transceiver and LTE stack)
> 2. `FUN_c0b50308` (Subscription 1 twin of `FUN_c067cd2c`)
> 3. `FUN_c0f8ae6c` (the internal boot dispatcher that constructs `PROT_GEN_CMD(1)`)
>
> `FUN_c0f8ae64(0, 0)` invokes `FUN_c0f8ae6c(0, 0, 0)` -> `FUN_c0f8dd84(..., 0x1000001)` -> MMOC `PROT_GEN_CMD(1)`, which brings the modem online automatically!
>
> **v7 Dual-Action Architecture**:
> 1. **DMS Direct Dispatch (VA `0xc0e03a80`)**: At `0xc0e03a80`, instead of dispatching to CM, we execute `{ call 0xc0f8ae64; r1:0 = combine(#0, #0) }` followed by `{ jump 0xc0e03cac }`, triggering the genuine online transition and returning success.
> 2. **Boot Auto-Online Dispatch (VA `0xc0f98228`)**: In `FUN_c0f981f4`, the gate `if (*(sbyte *)(iVar2 + 4) != 0) call FUN_c0f92754` is patched at `0xc0f98228` from conditional jump `0e e0 42 24` to unconditional `jump 0xc0f98240` (`0c c0 00 58`). This ensures the boot sequence unconditionally invokes `FUN_c0f92754` -> `FUN_c0f8ae64(0, 0)` -> `0x1000001`, bringing the modem online at boot just like stock HMU05!


> [!NOTE]
> **MBA Error -27 Resolved (Segment Sync)**: When transitioning back to UZ801 from stock recovery, deploying only `modem.b16` caused MBA to abort at `modem.b02` with error `-27` because the hash table in `modem.mdt` (for UZ801) mismatched the stock `modem.b02` on disk. The entire 21-file UZ801 segment set (`modem.b00` through `modem.b25` + `modem.mdt`) was synchronized simultaneously. All 19 files with payload match the SHA-256 digests in `modem.mdt`.


### 7.4 v8 Findings: Synchronous QMI Success and the MMOC Discovery

The v8 patch tested direct invocation of `FUN_c0f8ae64` with a synchronous success return packet:
- **Site 1 (VA `0xc0e03a80`)**: `{ call 0xc0f8ae64; r0 = #0 }` followed by `{ r22 = #0; jump 0xc0e03cac }`.
- **Result**: `qmicli -p -d /dev/wwan0qmi0 --dms-set-operating-mode=online` returned:
  `[/dev/wwan0qmi0] Operating mode set successfully` in <1 second!
  Both QMI error 52 (`DeviceNotReady`) and `Transaction timed out` were **completely eliminated**.
- **Diagnostic Trace Analysis**:
  Live F3 capture (`scratch/diag_online_test.bin`, 560 messages) revealed that the Jio SIM card is read completely (extracting PLMN 405861, 405857, etc.), but the LTE stack did not come up.
- **Decompilation Root Cause**:
  Deep analysis of `FUN_c0f8ae64` revealed:
  1. `FUN_c0f8ae64` calls `FUN_c0f8ae6c(0, 0, 0)`.
  2. `FUN_c0f8ae6c` immediately tests `iVar3 = *(int *)(&DAT_c33991e8); if (iVar3 == 0) return;`.
  3. `DAT_c33991e8` is only initialized by `FUN_c0f980fc`, which is an exported API that is **never called internally** in UZ801!
  4. Consequently, `FUN_c0f8ae64` aborted without posting any command to MMOC.

### 7.5 v9 Implementation: Direct Call Manager MMOC Transition (`FUN_c067cc2c`)

Exhaustive search of the Call Manager (`cm_ph`) codebase identified the genuine mode transition function:
`FUN_c067cc2c(undefined1 param_1)` at VA `0xc067cc2c`:
```c
void FUN_c067cc2c(undefined1 param_1) {
    cmd = thunk_EXT_FUN_d07f519c(); // mmoc_cmd_alloc() at 0xc0664ed0
    if (cmd != 0) {
        *(undefined4 *)(cmd + 8) = 0x1000008; // MMOC_CMD_SUBS_OPRT_MODE
        *(undefined1 *)(cmd + 0x20) = *(undefined1 *)(cm_state + 3); // oprt_mode (5 = ONLINE)
        *(undefined1 *)(cmd + 0x21) = param_1;
        ...
        thunk_EXT_FUN_d07f4cc8(cmd); // mmoc_cmd_post(cmd) at 0xc0664ed8
    }
    *(undefined1 *)(cm_state + 2) = 1; // CM State 1: ONLINE!
}
```

**Key Advantages of `FUN_c067cc2c`**:
1. Requires **NO external context pointers** or uninitialized global data.
2. Directly allocates an MMOC command buffer, sets `cmd->cmd = 0x1000008`, posts it to MMOC, and sets Call Manager state to 1 (ONLINE).
3. The target operating mode in the MMOC command buffer is set at `0xc067cc78`.

**v9 Patch Specification (148 diff bytes from pristine)**:
- **Site 1 (DMS handler at `0xc0e03a80`, 16 bytes)**:
  - Pristine: `404b349116c0022414e5011106c00110`
  - Patch:    `d6480f5b20c0007820401e1680dc143c`
  - Disassembly:
    - `{ call 0xc067cc2c; r0 = #1 }` (Calls MMOC mode transition with param 1)
    - `{ r22 = #0; jump 0xc0e03cc8; memb(r20+#0x39) = #0 }` (Sets stored mode to Online, sets success code, jumps to response formatter)
- **Site 2 (`FUN_c067cc2c` target mode byte at `0xc067cc78`, 8 bytes)**:
  - Pristine: `6040009120c2b0a1` (`{ r0 = memb(r0+#3); memb(r16+#0x20) = r0.new }`)
  - Patch:    `0040007f05d0103c` (`{ nop; memb(r16+#0x20) = #0x5 }`)
  - Forces the MMOC command payload `oprt_mode` to `5` (`SYS_OPRT_MODE_ONLINE`).
- **Result**: ⚠️ BOOT LOOP!
  - **Root Cause Analysis**:
    `FUN_c067cc2c` is called during Call Manager early boot initialization (at lines 1134789 / 1134798). By hardcoding `memb(r16+#0x20) = #0x5` inside `FUN_c067cc2c`, MMOC was commanded to enter `ONLINE` mode at boot time, *before* the WTR1605 RF hardware and drivers had completed initialization. This caused an unhandled MMOC hardware assertion/panic -> modem crash -> PMIC PON watchdog reset loop.
  - **Recovery**:
    Successfully recovered by configuring static IP `192.168.8.100/24` on the host to eliminate DHCP negotiation latency, and executing a fast on-device restore (`cp /overlay/fwbackup/hmu05_stock/modem.* /lib/firmware/`) during the pre-crash SSH window.

### 7.6 v10 Implementation: Clean Runtime Mode Setting (`FUN_c067cc2c` Preserved Pristine)

To eliminate the boot loop while retaining the MMOC transition capability, **`FUN_c067cc2c` is left 100% PRISTINE**:
1. At boot time, `FUN_c067cc2c` executes with the standard boot-time parameters (`cm_state + 3` = offline). No early online transition is attempted; boot is 100% stable.
2. At runtime, when userspace calls `qmicli --dms-set-operating-mode=online`, the patched DMS handler (`0xc0e03a80`, 52 bytes) sets `cm_state + 3 = 5` and then calls `FUN_c067cc2c(1)`.
3. Pristine `FUN_c067cc2c` reads `cm_state + 3` (now 5), copies it to the MMOC command buffer, and posts `0x1000008` (mode 5 = ONLINE) to MMOC!

**v10 Assembly (`0xc0e03a80`, 52 bytes)**:
```
c0e03a80: { r0 = memw(gp+#0x32fc) }                      # load cm_state
c0e03a84: { memb(r0+#0x3) = #0x5 }                        # cm_state[0]+3 = 5 (ONLINE)
c0e03a88: { call 0xc067cc2c; r0 = #0x1 }                  # invoke MMOC mode transition
c0e03a90: { r22 = #0x0; jump 0xc0e03cc8; memb(r20+#0x39) = #0x0 } # success, stored mode=0
c0e03a98 - c0e03ab0: NOP * 7
```

- **Result**: Boot succeeded and remoteproc attached (`wwan0qmi0`), but calling `FUN_c067cc2c` from `ds` task triggered a crash/reboot loop when `set-operating-mode` was invoked.
  - **Root Cause Analysis**:
    `FUN_c067cc2c` is a private Call Manager function belonging to the `cm` task. At lines 1134542-1134547, it dereferences `*(int *)(gp + 0x4a18)` (Call Manager call table) without a NULL check. When invoked cross-task from the Data Services (`ds`) task context, this pointer was invalid/uninitialized, generating an immediate data access exception in QuRT and bringing down the modem subsystem.
  - **Architectural Lesson**:
    In Qualcomm Hexagon modem firmware, `ds` task cannot directly call internal CM/MMOC state transition functions. QMI DMS commands must interact with CM strictly via asynchronous message queues, or the operating mode must be set via the boot-path state machine inside MMOC.
  - **Recovery**:
    Recovered to stock HMU05 firmware cleanly via `/overlay/fwbackup/hmu05_stock/`. Live device is 100% operational with stock firmware (`Mode: 'online'`, WTR1605 powered).

---

## 8. Next Steps: MMOC Boot State Differential (Stock HMU05 vs UZ801)

### 8.1 The Core Insight
Stock HMU05 enters `online` mode **automatically inside MMOC** at uptime ~2.1s (`Prot_state [MAIN] 5(ONLINE_GWL)`), **before any AP userspace or QMI command runs**. UZ801 firmware has the exact same MMOC code in Segment 19 (`modem.b19`).

### 8.2 Immediate Action Items
1. **Differential Analysis of `modem.b19`**:
   - Compare `scratch/stock_hmu05_modem.b19` and `scratch/uz801_fw/image/modem.b19`.
   - Identify the MMOC policy file / NV item / default state variable that directs MMOC to start in `Prot_state 5(ONLINE_GWL)` on stock HMU05 vs `Prot_state 2(LPM)` or `3(OFFLINE)` on UZ801.
2. **Examine `mmoc_task` Initial State Assignment**:
   - Trace the function that logs `Prot_state [MAIN]` in `mmocdbg.c`.
   - Locate the branch or default value that sets the initial protocol state in `modem.b19`.
3. **Safe Boot-Path Patch in `modem.b19`**:
   - Rather than forcing CM through userspace QMI, patch MMOC's initial state determination so it defaults to `5(ONLINE_GWL)` at boot, exactly as stock HMU05 does.

---

## 9. Key Files and Tools

### 9.1 Firmware Files

| File | Location | Purpose |
|------|----------|---------|
| Pristine modem.b16 | `scratch/uz801_fw/image/modem.b16` | MD5 `848abe7eea4b772741eaaf3225dc5434` |
| Patched modem.b16 | `scratch/uz801_patched/modem.b16` | Current working binary |
| Hash header | `scratch/uz801_patched/modem.b01` | SHA-256 hashes for all segments |
| ELF header | `scratch/uz801_patched/modem.mdt` | ELF + hash copy |
| Patch backups | `scratch/uz801_patched/modem.b16.{bak,cm_patched,oprt_jump,patched}` | Previous iterations |

### 9.2 Decompiled Source

| File | Content |
|------|---------|
| `Docs/Modem Stability/Modem RE/uz801/modem_full_decompiled.c` | Full Ghidra decompilation of modem.b16 |

Key function locations in decompiled source:
- `FUN_c0683c88` — line 1,139,667 (CM operating mode whitelist)
- `FUN_c06840e4` — line 1,139,798 (CM command processor)
- `FUN_c067ef58` — line 1,135,915 (CM oprt_mode handler)
- `FUN_c067c324` — line 1,134,046 (CM state transition → MMOC)
- `FUN_c0e03974` — line 2,602,996 (DMS set-operating-mode handler)
- `FUN_c0b57c2c` — line 2,084,663 (CM whitelist twin for sub1)

### 9.3 Tools

| Tool | Usage |
|------|-------|
| `python3 GitIgnore/compare/ufi001b_hash_tool.py patch <dir> 16 <file>` | Update SHA-256 hashes after patching segment 16 |
| `python3 GitIgnore/compare/ufi001b_hash_tool.py verify <dir>` | Verify all 27 segment hashes |
| `scratch/apply_clean_patches.py` | Apply all patches from pristine (run from repo root) |
| `scratch/f3clean.py` | F3 log parser: `parse(path)` → `(raw, [dict])` |
| `llvm-objdump --arch=hexagon -d <elf>` | Disassemble Hexagon code |
| `llvm-mc -arch=hexagon -filetype=obj -o out.o` | Assemble Hexagon instructions |

### 9.4 Hexagon Assembly Helpers

**Disassemble raw bytes at a given VA:**
```python
import struct, subprocess, tempfile
def disasm(raw_bytes, vma):
    ehdr = struct.pack("<16sHHIIIIIHHHHHH",
        b"\x7fELF\x01\x01\x01" + b"\x00"*9, 2, 164, 1, vma, 52, 0, 5, 52, 32, 1, 0, 0, 0)
    phdr = struct.pack("<IIIIIIII", 1, 84, vma, vma, len(raw_bytes), len(raw_bytes), 5, 4)
    with tempfile.NamedTemporaryFile(suffix=".elf") as f:
        f.write(ehdr + phdr + raw_bytes)
        f.flush()
        return subprocess.check_output(["llvm-objdump", "--arch=hexagon", "-d", f.name], text=True)
```

**Common Hexagon encodings:**
```
{ nop }                    = 00 c0 00 7f
{ r0 = #1; jumpr r31 }    = c0 3f 10 48
{ r0 = #2; jumpr r31 }    = c0 3f 20 48
{ jump .+0x34 }            = 1a c0 00 58  (from 0xc0e03a80 to 0xc0e03ab4)
{ jump .+0x2c }            = 16 c0 00 58  (from 0xc0e03a80 to 0xc0e03aac)
```

---

## 10. Operational Notes

### 10.1 GPIO 71

GPIO 71 must remain HIGH at all times for WTR1605 RF transceiver power.

```bash
# Check: read TLMM GPIO 71 IN_OUT register
devmem 0x1047004 32    # Should be 0x3 (bit 0 = IN high, bit 1 = OUT high)

# Set: configure as output HIGH
devmem 0x1047000 32 0x200   # OE=1
devmem 0x1047004 32 0x2     # OUT=1
```

Maintained via `/etc/rc.local` polling loop on the device.

### 10.2 Safe Reboot

**NEVER** execute `echo stop > /sys/class/remoteproc/remoteproc0/state` — it triggers a PMIC PON WDT reset of the entire SoC. Use clean cold reboot only:

```bash
sync; sync; reboot
```

### 10.3 Deployment Workflow

```bash
# 1. Apply patches
python3 scratch/apply_clean_patches.py

# 2. Update hashes
python3 GitIgnore/compare/ufi001b_hash_tool.py patch scratch/uz801_patched 16 scratch/uz801_patched/modem.b16

# 3. Verify
python3 GitIgnore/compare/ufi001b_hash_tool.py verify scratch/uz801_patched

# 4. Upload
scp scratch/uz801_patched/modem.{b16,b01,mdt} root@192.168.8.1:/tmp/

# 5. Deploy
ssh root@192.168.8.1 'cp /tmp/modem.b16 /lib/firmware/; cp /tmp/modem.b01 /lib/firmware/; cp /tmp/modem.mdt /lib/firmware/'

# 6. Verify MD5
ssh root@192.168.8.1 'md5sum /lib/firmware/modem.b16'

# 7. Reboot
ssh root@192.168.8.1 'sync; sync; reboot'

# 8. Test (after ~60s)
ssh root@192.168.8.1 'qmicli -p -d /dev/wwan0qmi0 --dms-set-operating-mode=online'
```

### 10.4 QMI Commands

```bash
# Always use -p flag for proxy mode
qmicli -p -d /dev/wwan0qmi0 --dms-get-operating-mode
qmicli -p -d /dev/wwan0qmi0 --dms-set-operating-mode=online
qmicli -p -d /dev/wwan0qmi0 --nas-get-signal-strength
qmicli -p -d /dev/wwan0qmi0 --nas-get-serving-system
```

---

## 11. Patch Version 13: Live DIAG Capture & Call Manager State Machine Analysis

### 11.1 Live DIAG Capture Capability Restored
Using `/root/diag_bind.sh` and `/root/diag_logtool` on device, live F3 logging across `remoteproc0:smd-edge.DIAG` was re-established and verified. This allows real-time extraction of Qualcomm F3 debug messages during both system boot and runtime QMI/AT operations.

### 11.2 Root Cause Analysis of CM Rejection (`0x3c` / `CM_PH_CMD_ERR_OFFLINE_S`)
Decompilation and runtime tracing identified the exact causal chain between Call Manager and MMOC:

1. **`cm_state + 0x39e8` is the Call Manager protocol state gate**:
   In `FUN_c067cd2c` (the MMOC event handler in CM) at line 1134956:
   ```c
   if (iVar12 == 0) {
       if (*(sbyte *)(cm_state + 0x39e8) != 3) {
           FUN_c0287020(&DAT_c165570c);
           epilog_restore_regs_c0030080();
           return;
       }
   }
   ```
   If `cm_state + 0x39e8 != 3`, Call Manager refuses to process MMOC events and aborts protocol stack / RF transceiver bringup.

2. **The Whitelist Function (`FUN_c0683c88`) Role**:
   In pristine code, `FUN_c0683c88` writes `*(cm_state + 0x39e8) = 3` when accepting an operating mode transition. In previous versions (v11/v12), replacing `FUN_c0683c88` with `{ r0 = #2; jumpr r31 }` skipped this initialization, leaving `cm_state + 0x39e8 == 0`.

3. **v13 Patch Implementation**:
   `apply_v13_patches.py` replaced Group 2 (`FUN_c0683c88`) and Group 3 (`FUN_c0b57c2c`) with 44-byte atomic blocks:
   - Sets `cm_state + 0x39e8 = 3` (sub0) and `cm_state + 0x39f0 = 3` (sub1)
   - Copies client transaction ID from `*(cmd + 0x10)` to `cm_state + 0x39ec` / `0x39f4`
   - Copies subscription ID from `*(cmd + 0x14)` to `cm_state + 0x39e9` / `0x39f1`
   - Returns 2 (`ACCEPTED`)

### 11.3 Verification & Live Results
1. **ELF & Hash Verification**: All 19 segments matched SHA256 hashes (`ufi001b_hash_tool.py verify` -> PASS).
2. **Boot Stability**: Clean boot in 16 seconds, no remoteproc panic, GPIO 71 HIGH (`0x00000003`), `/dev/wwan0*` nodes created.
3. **Boot Trace Diagnostics (`diag_boot_v13.bin`)**:
   - `mmocdbg.c` produced 60 messages (previously 0 in UZ801).
   - `qmi_nas.c` actively reads preferred PLMN list for Jio (MCC 405, MNC 869-873).
   - `cmph.c:32761` confirms `mode_pref 38` (GWL), sys_mode 9 (LTE) and sys_mode 4 (WCDMA) enabled without restrictions.
   - Live QMI and AT commands successfully allocate and queue commands to Call Manager (`=CM= CMD alloc u=40/112, tsk=ds`).


---

## 12. Patch Version 14: `cmmsc_auto.c` Mode Gate Bypass

**Date:** 2026-09-26  
**Patch Script:** `scratch/apply_v14_patches.py`  
**Patched MD5:** `d755f42a189f3943baa7fbaebe2bec1f`  
**Diff Bytes:** 265 from pristine

### 12.1 Root Cause: The `cmmsc_auto.c` Mode Gate

Deep analysis of both stock HMU05 and UZ801 boot F3 traces (Doc 194, Section 11) revealed the exact causal chain that keeps UZ801 in `offline` mode:

#### 12.1.1 Stock HMU05 Boot Path (Working)
```
ts=361996  mmocmmgsdi.c:1637  evt=12, sess_id=1037826639
ts=361996  mmocmmgsdi.c:250   Sess type=0, ss=0
ts=362356  cmph.c:32761       CM allocates command u=1
ts=375844  cmmsc_auto.c:2651  CMMSC_AUTO: updating op_mode
ts=375856  mmocdbg.c:291      Recvd command 0(SUBSCRIPTION_CHGD)
ts=375924  mmocdbg.c:291      New transaction: 1(SUBSC_CHGD)
           → Prot_state [MAIN] 5(ONLINE_GWL)  ← MODEM IS ONLINE!
```

#### 12.1.2 UZ801 Boot Path (Broken)
```
ts=500487008  mmocmmgsdi.c:1694  evt=0, sess_id=0  ← EARLY SPURIOUS EVENT!
              mmocmmgsdi.c:250   Sess type=0x7fffffff, ss=4 (INVALID)
              → Posts command 7(DUAL_STANDBY_CHGD) to MMOC
              → MMOC transitions from Prot_state 0(NULL) to 7(OFFLINE)!

ts=later      SIM sessions open (evt=13, evt=19, evt=12) → cmmsc_auto.c runs
              FUN_c06917f0 checks psVar4[0x29c] == 9 (OFFLINE) → SKIPS ONLINE PATH!
```

#### 12.1.3 The Mode Gate in `FUN_c06917f0`

At VA `0xc069194c`, inside `cmmsc_auto.c`:
```
c069194c: { r0 = memub(r16+#0x29c)                         ; load current mode
c0691950:   if (!cmp.eq(r0.new,#0x9)) jump:t 0xc0691968 }  ; if mode != 9 → ONLINE path!
; OFFLINE path (mode == 9):
c0691954 - c0691964: logs DAT_c165c210, jumps to 0xc06919cc → SKIPS GOING ONLINE!
; ONLINE path (mode != 9):
c0691968: r0 = memub(r16+##0x1508)                         ; check subscription flag
c069197c: r2 = #0x5                                        ; mode 5 = SYS_OPRT_MODE_ONLINE
c0691980: memb(r16+#0x10) = #0x1                           ; set transition flag
c069198c: memb(r16+##0xa6a) = r2                           ; target mode = 5 (ONLINE)!
c06919a4: jump 0xc0691ac0                                  ; → callback posts PROT_GEN_CMD(1) to MMOC!
```

In decompiled C (line 1143644):
```c
if (psVar4[0x29c] != 9) {       // <-- THE MODE GATE
    if (psVar4[0x1508] != 0) {  // secondary check
        goto LAB_c06919cc;      // skip (subscription already active)
    }
    psVar4[0x10] = 1;
    psVar4[0xa6a] = 5;          // SET MODE = 5 (ONLINE)!
    ...
    goto LAB_c0691ac0;          // call callback → MMOC PROT_GEN_CMD(1) → ONLINE!
}
// mode == 9 → falls through to LAB_c06919cc → STAYS OFFLINE!
```

### 12.2 The v14 Patch

**GROUP 11:** Bypass mode gate at VA `0xc0691950` (file offset `0x40a950`):

| File Offset | VA | Pristine | Patched | Purpose |
|-------------|-----|----------|---------| --------|
| `0x40a950` | `0xc0691950` | `0e e9 42 24` | `0c c0 00 58` | `if (!cmp.eq(r0.new,#0x9)) jump:t` → `jump 0xc0691968` (unconditional) |

**Packet context** (8 bytes at `0xc069194c`):
```
Pristine: { r0 = memub(r16+#0x29c); if (!cmp.eq(r0.new,#0x9)) jump:t 0xc0691968 }
          80 53 30 93   0e e9 42 24

Patched:  { r0 = memub(r16+#0x29c); jump 0xc0691968 }
          80 53 30 93   0c c0 00 58
```

**Parse bits preserved:** Both the pristine and patched second word have parse bits `11` (end of packet), maintaining valid Hexagon VLIW packet structure. The first word's load into `r0` executes harmlessly; the unconditional jump is always taken.

### 12.3 Full v14 Patch Groups

v14 = v13 (Groups 1–10) + Group 11:

| Group | Target | Purpose | Status |
|-------|--------|---------|--------|
| 1 | DMS handler (`0xc0e03a80`) | Jump to native CM dispatch | v13 |
| 2 | CM whitelist sub0 (`0xc0683c88`) | Set `cm_state+0x39e8=3`, copy IDs, ret 2 | v13 |
| 3 | CM whitelist sub1 (`0xc0b57c2c`) | Set `cm_state+0x39f0=3`, copy IDs, ret 2 | v13 |
| 4 | CM handler sub0 (`0xc067ef88`) | NOP mode check, `cm_state+2=1` | v13 |
| 5 | CM handler sub1 (`0xc0b50ff0`) | NOP mode check, `cm_state+2=1` | v13 |
| 6 | CM cmd processor (`0xc0683ce4`) | Ready check bypass | v13 |
| 7 | Readiness check (`0xc0821380`) | Force return 1 | v13 |
| 8 | Thunk caller (`0xc07d8560`) | NOP | v13 |
| 9 | RFC card ID (`0xc0d64ba0`, `0xc10885b8`) | Force HWID `0x48` | v13 |
| 10 | DMS callback (`0xc0e001e0`) | Jump to success | v13 |
| **11** | **cmmsc_auto.c mode gate (`0xc0691950`)** | **Unconditional jump to ONLINE path** | **NEW in v14** |

### 12.4 Test Results (2026-09-26 14:30 IST)

1. **Boot:** Clean boot, remoteproc running, `/dev/wwan0*` nodes created, GPIO 71 HIGH (`0x3`). No panics.
2. **Operating Mode:** Still `offline`. The mode gate bypass (GROUP 11) alone is insufficient.
3. **F3 Trace Analysis:** Runtime DIAG capture (`diag_v14_online.bin`, 991 msgs) shows no `cmmsc_auto.c`, `mmocdbg.c`, or `cmph.c` activity — only heartbeat messages (`cfm_cpu_monitor.c`, `DalVAdc.c`, `tle_log.c`).
4. **QMI DMS:** `set-operating-mode=online` returns "set successfully" but mode stays `offline`.
   - `cmdbg.c:2186` shows `CMD alloc u=173, tsk=dcc` → `CMD free u=173, tsk=dcc, ftsk=cm` — command reaches CM but is freed without processing (no ONLINE dispatch in CM handler).

> [!WARNING]
> **Key Finding:** The `cmmsc_auto.c:FUN_c06917f0` mode gate bypass does NOT change behavior because `FUN_c06917f0` is **never called** during UZ801 boot on HMU05. The function is part of the CM Multi-Stack Controller (MSC) subscription change handler, which is only invoked when MMGSDI delivers a valid subscription event that triggers CM to reevaluate operating mode. In UZ801 on HMU05, the MMGSDI event sequence apparently either never triggers `cmmsc_auto.c`, or triggers a different code path that does not reach `FUN_c06917f0`.

### 12.5 Next Steps

1. **Capture BOOT-TIME F3 trace** with DIAG enabled from `rc.local` to confirm whether `cmmsc_auto.c:FUN_c06917f0` is called during UZ801 boot.
2. **Suppress `DUAL_STANDBY_CHGD(7)` in `mmocmmgsdi.c`**: Prevent the spurious `evt=0` from posting `command 7` to MMOC when `ss == 4` (invalid). Keep MMOC in `Prot_state 0(NULL)` so the normal subscription flow brings it to `Prot_state 5(ONLINE_GWL)`.
3. **Alternative: Directly force MMOC initial state**: Patch MMOC state initialization in segment 19 (`modem.b19`) to default to `Prot_state 5(ONLINE_GWL)`.
