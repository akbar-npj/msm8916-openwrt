# Doc 229 — The stock fatal captured, the RAT lever blocked, and the assert-neutralisation patch FAILED

**Date:** 2026-09-28
**Device:** HMU05 (UFI001B) dongle · OpenWrt 25.12.5 · kernel 6.12.94 aarch64 · serial `c2b9103c`
**Evidence:** `evidence/227_ap_side_stock_restore/`, `evidence/228_ap_independence_and_mode/`
**Ledger:** this document is issued under `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md` (see item 33).

---

## 1. Goal and method

Goal: fix the ~15-minute modem crash on **stock** HMU05 firmware, SOP-compliantly. The user granted
full autonomy for the fix with the constraint "maintain SOP" — i.e. never guess; instrument and
differentially ground every change; keep atomic backups; re-hash modified segments.

Method: (a) restore pristine stock and capture the natural fatal **with the AP-side data path removed**;
(b) test the cheapest non-invasive lever (RAT mode); (c) if blocked, attempt a **verified** baseband
patch and score it empirically.

## 2. The capture — the 5th and strongest AP-independence proof

Stock HMU05 was restored and verified (51/51 files byte-identical to the pristine set), then
**`qcom_bam_dmux` was fully `rmmod`'d** — no A2 handshake, no pc-ack, no `runtime_resume`, no TX/RX,
no bearer, no data path at all.

The fatal **still fired at modem uptime 902.60 s** (AP 913.565 s). The SSR recovered cleanly in 1.30 s
with **no AP hang**, independently re-confirming Doc 217 (the hang lives in the bam_dmux SSR-teardown
notifier, not the remoteproc core).

This joins three earlier proofs — pmOS (n=9, mean 902.798 s), RPM-vote invariance (1 vote → 910 s;
311 → 912.6 s), and the mode/power levers — to close the question:

> **No AP-side change can prevent this fatal.**

## 3. What the fatal is

From the coredump (`scratch/coredump_stock_227/modem_coredump_stock_227.elf`, 85 398 475 B,
md5 `786c8b8492960c46eb8f4c7007c9454a`):

* `Error in file lte_ml1_common_timer.c, line 390`, message **`Assert 0 failed:`**, REX task
  **`tmr_slave3`** — a **timer callback**, not a task-thread context. Build `HIMI_U01_MODEM_V1.0`.
* **★ Coredump↔ELF bias: `elf_va = dump_va + 0x39800000`**, content-verified.
* The assert site is the packet at **ELF VA `0xc02d7d80`** =
  `{ call 0xc0879150; immext(#0xc3c6c800); r0= }`. `0xc0879150` is the `ERR_FATAL` entry
  (**12 286 callers**); `r0 = 0xc3c6c800` equals `QDSP6_GP_R16` in the dump; the packet's return
  address `0xc02d7d8c` matches the dump's stack. Verified byte-for-byte in
  `scratch/hmu05_stock_elf/modem_hmu05_stock.elf`.
* **★ `QDSP6_PC` is a hardcoded placeholder** — `FUN_c087a76c` writes the literal `0xc087a804` into
  the saved-PC slot. Use LR and the stack; never the PC.
* The site sits in a **switch dispatcher** (`jumpr` via a table at `gp+0xba64`) whose tail is an
  unconditional `ERR_FATAL` ⇒ the ML1 state machine reached an **invalid state**.
* **Five sites race for one ~900 s deadline** (census of all 42 coredumps):
  `lte_ml1_sleepmgr_stm.c:4054` ×18 · `a2_power.c:1189` ×11 · `lte_ml1_common_timer.c:390` ×9 ·
  `a2_power.c:2949` ×3 · `a2_taskq.c:759` ×1.
* **The data path is healthy at the moment of the crash** (LTE connected, attached, 84 % signal).
  Only the sleep / power-collapse logic is stuck.

## 4. The RAT lever is BLOCKED (tested, negative)

`qmicli --dms-get-capabilities` → `Networks: 'umts, lte'`; the image carries
`rfc_wtr2605_3g_sku_wcdma_ag` / `_gsm_ag`; the REX dog table shows `tds_l1` / `wcdma_l1` tasks — the
software is multi-mode. **But** `--nas-get-system-selection-preference` reports
`Mode preference: 'lte'`, and the modem **refuses to leave LTE**:

```
mmcli -m any --set-allowed-modes=3g
  → QMI protocol error (25): 'DeviceUnsupported'
qmicli -d /dev/wwan0qmi0 --nas-set-system-selection-preference="umts"
  → error: couldn't set operating mode: QMI protocol error (3): 'Internal'
```

⇒ the LTE ML1 stack cannot be removed by RAT locking, so the "LTE-stack-gated" escape hatch is closed.

## 5. The patch — and why it FAILED

Three dominant sites (90 % of the corpus) were neutralised by replacing the 12-byte
`{ call 0xc0879150; immext; r0= }` packet at each with three `nop`s (`00 c0 00 7f`), leaving the
continuation intact:

| site | `modem.b16` offset | ELF VA | continuation |
|---|---|---|---|
| `lte_ml1_common_timer.c:390` | 0x050d80 | 0xc02d7d80 | next statement (0xc02d7d8c) |
| `lte_ml1_sleepmgr_stm.c:4054` | 0x1187c4 | 0xc039f7c4 | tail call (0xc039f7dc) |
| `a2_power.c:1189` | 0x27d320 | 0xc0504320 | `return 0` (0xc050432c) |

Hash-patched per Doc 226 (`sha256(modem.b16)` → `modem.b01[0x228]`, `modem.mdt = b00+b01`);
`ufi001b_hash_tool.py verify` → PASS. Deployed md5s `b16 d08321b1…`, `b01 bc4cc25e…`, `mdt c13be1b8…`.

**★ The mechanism works on HMU05:** the modem **booted the modified image, authenticated, and
connected on LTE** — no `-19`. This confirms Doc 226's hash-patch recipe generalises.

**★ But the patch is counterproductive:**

| # | AP uptime | modem uptime | site | in the 42-dump census? |
|---|---|---|---|---|
| 1 | 535.83 s | 535.8 s | **`a2_task.c:1184`** | **no** |
| 2 | 1428.51 s | ~892 s | **`a2_task.c:3179`** | **no** |

**2 fatals in 1457 s vs 1 fatal in 902 s on stock — worse.** The ~900 s clock survived; the patch
merely moved the site and **added** an early fatal.

> **Conclusion: the assert sites are a CASCADE inside the A2 power / sleep state machine, not
> independent triggers.** Removing a guard lets the machine run further into the invalid state and
> trip a different guard. Patching individual asserts is futile; patching them all would leave the
> machine proceeding in an invalid state, and a hang would be worse than the current clean ~1.3 s SSR.

The patch was **reverted**; the device is back on pristine stock (`b16 57fef19d…`, `b01 b85b86ce…`,
`mdt 1a6f9507…`) and connected.

## 6. Where this points — the two surviving leads

1. **Axis A15 — the A2 handshake direction is INVERTED between Android 3.10 and OpenWrt 6.12**
   (`reference_a2_handshake_semantics.md`). The cascade lands in `a2_power.c` / `a2_task.c` (their
   line numbers nearly coincide, e.g. 1189 vs 1184 ⇒ the same code region). An **AP-side A2-parity
   fix** addresses the root instead of relocating a guard.
2. **The CS / voice-domain config** (cheap, non-invasive, UNTESTED). The modem reports
   `Usage preference: 'voice-centric'`, `Voice domain preference: 'cs-preferred'`,
   `Service domain: 'cs-ps'`, while HMU05 ships voice **stubbed** (`qvp_emptyfunctions`). A CS-domain
   wait that can never complete is a plausible "never sleeps" mechanism — and it would explain the
   Android/OpenWrt rate differential (Android's carrier config sets this; ModemManager does not).
   `qmicli` cannot set NAS TLVs 0x10/0x11, so testing it needs a small raw-QMI client.

## 7. Achieved vs Expected

| # | expected | achieved | verdict |
|---|---|---|---|
| 1 | capture the natural stock fatal | captured at 902.60 s modem uptime; coredump + crash log + full register/stack/dog dump | **MET** |
| 2 | prove AP-independence | 5th proof with the entire AP data path removed | **MET** |
| 3 | test a non-invasive lever | RAT lock tested → **blocked** by the modem (`DeviceUnsupported`) | **MET (negative)** |
| 4 | apply a verified baseband patch | 3 sites neutralised, hash-patched, **booted** | **MET** |
| 5 | the patch fixes the crash | **FAILED** — relocated to new `a2_task.c` sites and *increased* the rate | **NOT MET** |
| 6 | leave the device in a known-good state | reverted to pristine stock, verified, connected | **MET** |
| 7 | fix the 15-minute crash | **not achieved** — no AP-side or config lever; the two surviving leads are A15 and the CS/voice config | **NOT MET** |

## 8. SOP-compliance statement

* **Ground truth first:** every VA and byte was verified against the rebuilt stock HMU05 ELF
  (`scratch/hmu05_stock_elf/modem_hmu05_stock.elf`, md5 `954f2be5…`) and against the *deployed* file
  md5s before any write. No patch was derived from the decompile alone (the Ghidra boundaries were
  found to merge functions, so the **disassembly** was authoritative).
* **Atomic backups + rollback:** the pristine set was retained on the host
  (`scratch/mcfg_extracted/image/`) and the exact pre-patch device files were pushed back and
  md5-verified; the device was rebooted and re-verified (modem running, LTE connected).
* **Hashes re-computed:** the modified segment's SHA-256 was written into `modem.b01` and `modem.mdt`
  rebuilt, per Doc 226; `verify` → PASS.
* **Pre-registered success criterion, scored honestly:** the criterion was "zero fatals for > 1200 s".
  It was **not met**; the negative result is recorded in full rather than reframed.
* **Ledger updated in the same session** (item 33) and the memory files updated
  (`project_900s_fatal_anatomy.md` §19).
* **Warning stated before the high-blast-radius step:** the baseband modification was announced as
  reversible-but-risky before deployment.
* **⚠ Outstanding:** `modem-guard` is still **absent** from the device (Doc 197 item 32). Any further
  baseband work must re-install it first.
