# Evidence 228 — AP-independence of the ~902 s fatal, the RAT-lever test, and the assert-neutralisation patch

Date: 2026-09-28
Device: HMU05 (UFI001B) dongle, OpenWrt 25.12.5, kernel 6.12.94 aarch64, serial c2b9103c
Related: evidence/227_ap_side_stock_restore/ (the decisive natural-fatal capture)

## 1. The AP-independence proof (consolidated)

The ~902 s fatal is **not** an AP-stack property. Four independent lines of evidence:

1. **bam_dmux fully removed** — the evidence/227 capture fired at modem uptime **902.60 s**
   with `qcom_bam_dmux` `rmmod`'d: no A2 handshake, no pc-ack, no runtime_resume, no TX/RX,
   no bearer, no data path.
2. **pmOS (A16)** — the quietest possible AP stack reproduces it (n=9, mean 902.798 s,
   spread 0.272 s); fatal #3 needed no data path at all.
3. **RPM-vote invariance** — 1 vote → 910 s; 311 votes → 912.6 s.
4. **Mode/power levers** — `offline` (CFUN=4) suppresses for 1126 s; `low-power` does not
   (fired at 902.178 s).

⇒ **No AP-side change can prevent this fatal.**

## 2. What the fatal actually is (from the evidence/227 coredump)

* `Error in file lte_ml1_common_timer.c, line 390`, message `Assert 0 failed:`
* REX task `tmr_slave3` — i.e. a **timer callback**, not a task-thread context.
* The assert site is a `call 0xc0879150` (the `ERR_FATAL` entry, 12 286 callers) at
  **ELF VA 0xc02d7d80**, reached with `r0 = 0xc3c6c800` (= `QDSP6_GP_R16` in the dump).
  Confirmed byte-for-byte against `scratch/hmu05_stock_elf/modem_hmu05_stock.elf`.
* The surrounding code is a **switch-dispatcher case** (`jumpr` via a table at `gp+0xba64`)
  whose tail is an unconditional `ERR_FATAL`. I.e. the ML1 state machine reached an
  **invalid state** and trapped.
* **Five sites race for one ~900 s no-sleep deadline** (corpus census of 42 dumps):
  `lte_ml1_sleepmgr_stm.c:4054` ×18, `a2_power.c:1189` ×11, `lte_ml1_common_timer.c:390` ×9,
  `a2_power.c:2949` ×3, `a2_taskq.c:759` ×1.
* **The data path is healthy at the moment of the crash** (LTE connected, attached, 84 %
  signal). Only the sleep / power-collapse logic is stuck.

## 3. The RAT lever is blocked (tested)

`qmicli --dms-get-capabilities` → `Networks: 'umts, lte'`; the modem image carries
`rfc_wtr2605_3g_sku_wcdma_ag` / `rfc_wtr2605_3g_sku_gsm_ag`, and the REX dog table shows
`tds_l1` / `wcdma_l1` tasks — so the software is multi-mode.

But `--nas-get-system-selection-preference` reports **`Mode preference: 'lte'`** and the
modem **rejects** leaving LTE:

```
mmcli -m any --set-allowed-modes=3g
  → QMI protocol error (25): 'DeviceUnsupported'
qmicli -d /dev/wwan0qmi0 --nas-set-system-selection-preference="umts"
  → error: couldn't set operating mode: QMI protocol error (3): 'Internal'
```

⇒ A non-LTE lock cannot be used to remove the LTE ML1 stack.

## 4. The patch

Given (a) no AP-side lever, (b) a blocked RAT lever, and (c) a user mandate to fix it,
the three **dominant** assert sites (90 % of the corpus) were neutralised by replacing the
12-byte `{ call 0xc0879150; immext; r0= }` packet at each site with three `nop`s
(`00 c0 00 7f`), leaving the continuation intact:

| site | file offset in modem.b16 | ELF VA | continuation |
|---|---|---|---|
| `lte_ml1_common_timer.c:390` | 0x050d80 | 0xc02d7d80 | next statement (0xc02d7d8c) |
| `lte_ml1_sleepmgr_stm.c:4054` | 0x1187c4 | 0xc039f7c4 | tail call (0xc039f7dc) |
| `a2_power.c:1189` | 0x27d320 | 0xc0504320 | `return 0` (0xc050432c) |

* Hash-patched per Doc 226: `sha256(modem.b16)` written into `modem.b01[0x0228]`,
  `modem.mdt` rebuilt as `b00+b01`; `ufi001b_hash_tool.py verify` → PASS.
* Deployed md5s: `modem.b16 d08321b1…`, `modem.b01 bc4cc25e…`, `modem.mdt c13be1b8…`.
* The modem **boots and connects on LTE** with the patched image (no `-19` auth failure).
* Pristine rollback retained: `scratch/mcfg_extracted/image/` + `/tmp/modem.b1{6,01}.stock`
  and `/tmp/modem.mdt.stock` on the device.

## 5. RESULT — the patch is COUNTERPRODUCTIVE (reverted)

Soak log: `04_soak_patched.log`. Both fatals: `06_patched_both_fatals.txt`.

| # | AP uptime | modem uptime | site | in the 42-dump census? |
|---|---|---|---|---|
| 1 | 535.83 s | 535.8 s | **`a2_task.c:1184`** | **no** (new) |
| 2 | 1428.51 s | ~892 s (modem restarted 536.5 s) | **`a2_task.c:3179`** | **no** (new) |

* The hash-patch mechanism itself **works on HMU05**: the modem booted the modified image,
  authenticated, and connected on LTE — no `-19`.
* But neutralising the three dominant sites did **not** remove the fault. The ~900 s clock
  survived (~892 s on fatal #2) and simply moved to **`a2_task.c:3179`**, and the patch
  **added** an extra fatal at 535.8 s on **`a2_task.c:1184`**.
* Net effect: **2 fatals in 1457 s** vs **1 fatal in 902 s** on stock — i.e. *worse*.

⇒ **The five (now seven) sites are a CASCADE inside the A2 power / sleep state machine, not
independent triggers.** Removing a guard lets the machine run further into the invalid state
and trip a different guard. Patching individual asserts is futile, and patching them all
would let the machine proceed in an invalid state (a hang would be worse than the current
clean ~1.3 s SSR).

**The patch has been REVERTED.** Device restored to pristine stock
(`modem.b16 57fef19d…`, `modem.b01 b85b86ce…`, `modem.mdt 1a6f9507…`), rebooted, modem
running and connected.

## 6. Where this points

The locus of the fault is the **A2 power / sleep state machine** (`a2_power.c` /
`a2_task.c` — the note the two files' line numbers nearly coincide, e.g. `a2_power.c:1189`
vs `a2_task.c:1184`, suggests they are the same code region). This is the same machine
covered by memory axis **A15 — the A2 handshake direction is INVERTED between Android 3.10
and OpenWrt 6.12**. That axis is the natural next target: an *AP-side* A2-parity fix would
address the root rather than relocating a guard.

Also still open and non-invasive: the modem reports `Usage preference: 'voice-centric'` and
`Voice domain preference: 'cs-preferred'` / `Service domain: 'cs-ps'`, while HMU05 ships
voice **stubbed** (`qvp_emptyfunctions`). A CS-domain wait that can never complete is a
plausible mechanism for "the ML1 never sleeps". qmicli cannot set those two TLVs, so testing
it needs a small raw-QMI client (NAS Set System Selection Preference, TLVs 0x10 usage /
0x11 voice-domain). This is the cheapest remaining non-invasive lever.

## 7. Status

Patch experiment CLOSED — negative. Device back on stock. No AP-side or config lever
prevents the fatal; the surviving options are (a) an AP-side A2-parity fix (axis A15) or
(b) the CS/voice-domain config lever above.
