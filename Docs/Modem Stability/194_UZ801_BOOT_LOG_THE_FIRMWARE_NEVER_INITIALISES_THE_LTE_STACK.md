# 194 — The UZ801-on-HMU05 boot log: the firmware never initialises the LTE RF/protocol stack, and the stock baseline proves it

**Date:** 2026-09-25
**Device:** HMU05 4G modem stick (`HMU05 4G Modem Stick`, serial `c2b9103c`), OpenWrt 25.12.5,
kernel 6.12.94 aarch64, `192.168.8.1`. One physical device; the firmware *set* is what changes.
**Status:** RESULTS + a new instrument. The UZ801-21 set is deployed at the end of the session, unchanged
from how it was found. **No baseband binary was patched, edited, or re-signed in this session** — the
only firmware operation was a *swap between two stock sets*, both backed up and fully reversible.
**Companions:** `136_HMU05_DIAG_PORT_AND_SERIAL_CONSOLE_DISCOVERY.md` and
`137_HMU05_DIAG_LOG_STREAM_ENABLEMENT_AND_LOG_CONFIG_F_PROTOCOL.md` (the DIAG transport this builds on),
`189`–`192` (the Android differential), `193` (the p825/0005 soak — **its number is reserved**; the
evidence dir `evidence/193_p825_0005_soak/` already exists).
**Scope note:** this doc is about **why the UZ801-21 firmware sits in `offline` on HMU05 hardware**. It is
*not* about the ~900 s fatal, and it does not reopen the (closed) question of whether the swap can fix it.

---

## 1. SOP compliance

Per the mandatory protocol in `133_UFI001B_MPSS2_ATTACH_DIAGNOSTIC_AND_PURE_EPS_RESOLUTION.md` §1 — the
five-step workflow **backup → ground-truth verification against stock HMU05 → reconcile transport/config →
surgical Hexagon patching + cryptographic re-signing → live empirical validation**:

| SOP step | Status |
| :-- | :-- |
| **Backup / version control** | **Yes.** Before the firmware swap, the stock sets were already backed up at `/overlay/fwbackup/hmu05_stock` (mba.mbn + `modem.*`) and `/overlay/fwbackup/hmu05_wcnss_tz` (`cmnlib.*`, `keymaste.*`, `wcnss.*`, `mcfg_sw.mbn`, `MCFG_SW.MBN`, `regulatory.db`). The UZ801 boot capture was copied to `/root/diag_boot_uz801.bin` **before** the revert could overwrite it, and pulled to the host. |
| **Ground-truth verification against stock HMU05** | **Yes — this is the doc's core method.** §6–§7 are a *differential* between the two firmware sets on **the same AP, same NV, same hardware**, which is the strongest form of the comparative protocol available here. No claim about the UZ801 firmware is made without the stock arm. |
| **Reconcile transport/config** | **Yes.** The DIAG transport was re-derived and re-verified rather than assumed: the F3 wire format was reversed and validated against `%`-counts (§4), and the mask sweep was widened from the Doc-137 template's SSID 0 to 64 SSIDs (§3.3). |
| **Surgical Hexagon patching + re-signing** | **NOT APPLICABLE — deliberately.** No byte of any baseband image was modified. **No re-signing was required and none was attempted.** `ufi001b_hash_tool.py` was therefore not run; its verdict is not claimed. |
| **Live empirical validation** | **Yes.** Every finding below is from a live device: four UZ801 captures and one stock capture, all with `boot_id` recorded, plus live QMI/AT probes. |
| **No blind patching** | **Yes.** No patch is proposed here. §11 names the *open question* and explicitly declines to guess at a gate. |
| **Irreversible / high-blast-radius actions declared first** | **Yes.** The firmware revert and re-deploy were announced before running, both reboots were stated, and the revert was verified by MD5 against the expected stock values before proceeding. |

**Errors caught and corrected in this session — recorded rather than hidden** (this is the class of mistake
the comparative protocol exists to catch):

1. **A false-positive F3 parse.** The flat `79 00` scan matched byte pairs *inside* the 94 MB of binary
   `0x10` log payloads, yielding 33 039 "messages" of which only 31 443 were real, with a `ts` span of
   17 817 s for a 240 s capture. Caught by the span check; fixed with a median-`ts` cluster filter (§4.3).
   **The file-set diff was computed only after the fix.**
2. **A mis-read field.** `=CM= CMD alloc u=N` was initially read as "CM opcode N". It is a
   **monotonically increasing command handle** (§8).
3. **A retracted premise.** The claim "`/rfc_ver_data` is absent from the EFS" is **void**: the EFS
   partitions are `IMGEFS1` encrypted/compressed images, so the plaintext grep that produced it proved
   nothing (§9.2).
4. **A falsified mechanism.** Doc 89 §7.2's "CM rejects `SYS_OPRT_MODE_ONLINE` with
   `CM_PH_CMD_ERR_OFFLINE_S`" does not describe this failure — CM never receives an opmode command (§5.3).

---

## 2. Executive summary

1. **A new instrument.** The modem firmware's **own F3 boot log** can now be captured across a cold AP
   reboot, reliably and non-invasively (§3). It binds the SMD DIAG channel **0.05 s after it appears**
   (AP uptime 12.96 s) without delaying rmtfs or rcS.
2. **The UZ801 boot is clean.** In 5 568 F3 messages there are **zero** occurrences of
   `fail`/`error`/`reject`/`abort`/`invalid`/`timeout`/`not ready`. The SIM completes
   (`MS_SIM_INIT_START` → `GW_READY` → `MS_READY` → `MS_SIM_INIT_END`), **EFS reads succeed**
   (`PLMNWACT`/`OPLMWACT` `status:0`, then 67 PLMN entries), and **CM's RAT state is clean**
   (`mode_pref 0x26`, `rat_disabled_mask 0x0`, `is_rat_disabled=0`).
3. **The `online` request dies in `ds`/DMS, not in CM.** `--dms-set-operating-mode=online` →
   QMI **(52) DeviceNotReady**, and the log shows `=CM= CMD alloc u=374, tsk=ds` with **no
   `cm_ph_cmd_oprt_mode` message at all** — although UZ801's `b18` *does* contain that log tag.
   **This falsifies Doc 89 §7.2's mechanism** for this case.
4. **★ The stock baseline names the difference.** Same AP/NV/hardware, only the firmware differs: stock
   boots to **`online`** and emits **94.6 MB / 31 443 clean F3 messages / 40 log-emitting files**; UZ801
   emits **1.54 MB / 5 461 / 23**. **25 subsystems run in the stock boot path that UZ801 never touches** —
   above all the **LTE RF layer** (`rflte_core_rxctl.c` ×8 388, `rflte_mc_meas.c`, `rfmeas_mc.c`), CM's
   serving-system/call modules (`cmss.c`, `cmcall.c`, `cmlog.c`, …), **MCPM** (`mcpm.c`, `mcpm_npa.c`,
   `mcpm_saw.c`), `mmocdbg.c` reporting **`Prot_state [MAIN] 5(ONLINE_GWL)`**, the system-determination
   scripts (`sdss.c`), the **Transceiver Resource Manager** (`trm_config_handler.c`), and `a2_ipfilter.c`.
5. **All 25 appear within 0.78 s of the capture start**, i.e. at **modem uptime ≈ 2.1 s**, before
   ModemManager exists — so **none of it can be AP-driven**. It is the firmware's own boot path.
6. **Verdict:** the UZ801 firmware's boot path never initialises the LTE RF / protocol stack; the stock
   firmware's does, within ~2 s. That *explains* `DeviceNotReady` as a downstream symptom. It does **not**
   yet name the single gating step (§11).

---

## 3. The instrument: catching the modem's own boot log

### 3.1 Why a boot log was needed

The offline modem is **completely quiescent** — a 45 s live capture shows only CFM CPU-monitor, VADC,
TPC-GPS and `qmi_mmode` traffic. It never retries its init, so the failure can only be observed **once, at
boot**. An earlier attempt to force a modem restart with `echo stop > …/remoteproc0/state` **reset the AP**
(PMIC PON WDT stop, `boot_id` changed) — a known hazard, and not a usable lever. A cold AP reboot is the
only clean modem restart.

### 3.2 Deployment

| artifact | role |
| :-- | :-- |
| `scratch/uz801_diagboot_run.sh` → `/root/diagboot_run.sh` | poll → bind → enable → capture → dump post-boot state |
| `scratch/uz801_diagboot.init` → `/etc/init.d/diagboot` | procd wrapper, `USE_PROCD=1`, `START=11`, `STOP=99` |
| `/root/diag_bind.sh` | binds SMD `DIAG`→`/dev/rpmsgN`, `DIAG_CNTL`→`/dev/rpmsgM`; **prints `DIAG=`/`CNTL=`** |
| `/root/diag_logtool` | `cntl-enable <dev> <nssid>`, `capture <secs> <file>` |

Enable/disable with `/etc/init.d/diagboot enable|disable`. Output goes to `/root/diagboot.log` and
`/root/diag_boot.bin` — **on the overlay, never `/tmp`**, which the reboot being measured would wipe.

Four design rules, each learned the hard way:

1. **procd, not a blocking init script.** `start_service()` must return immediately; an inline
   poll/capture loop stalls rcS, **delays rmtfs, and perturbs the very boot being measured**.
2. **Poll for the DIAG device; never assume a fixed delay.** The modem is started by a *kernel* deferred
   probe, asynchronously with userspace.
3. **Parse the node names from `diag_bind.sh`'s stdout.** The `rpmsg<id>` index is an ida allocation, so
   `/dev/rpmsg1` is not guaranteed.
4. **The F3 mask must be re-sent every modem boot.** It lives in the modem.

### 3.3 Measured boot timeline (HZ = 100, derived empirically — `getconf` is absent)

| event | AP uptime |
| :-- | --: |
| procd (pid 1) | 0.02 s |
| rmtfs (S15) | 10.66 s |
| `remoteproc0` powering up (kernel) | 10.94 s (UZ801) / 11.13 s (stock) |
| MBA booted | 11.11 s |
| `remoteproc0` "now up" | 11.88 s |
| bam_dmux `CMD_OPEN` ch0..7 | 12.42–12.51 s |
| **SMD DIAG channel appears** | **12.91 s** |
| **bind + F3 enable** | **12.96 s — 0.05 s later** |

**rmtfs is not late** (10.66 s vs the modem's 10.94 s), which kills the "the modem's EFS reads fail because
rmtfs starts after it" hypothesis for this board.

### 3.4 Mask coverage

`cntl-enable <dev> <nssid>` emits **one single-SSID `DIAG_CTRL_MSG_F3_MASK` packet per SSID**, because the
AP reference driver (`diag_masks.c:diag_send_msg_mask_update`) sends one packet per *real* range from its
own table and **never puts `ALL_SSID` (0xFFFF) on the wire**. The Doc-137 template's `ssid 0..0` is what
made earlier "the modem logs nothing" negatives unsound. **64 SSIDs is sufficient: a 256-SSID re-sweep
produced the same file set and no new message class.**

---

## 4. The F3 wire format (reversed, so the result is auditable)

Each DIAG read is length-prefixed by `diag_logtool capture` (`u32 LE len` + bytes). Inside, an F3 message is:

```
off  0     u8   0x79            type (F3 message)
off  1     u8   0x00
off  2..3  u16  num_args
off  4     u8   0x00
off  5..8  u32  timestamp       (204800 Hz)
off  9..10 u16  0x1294          message code
off 11     u8   0x01
off 12..13 u16  line number
off 14..15 u16  file/subsystem id
off 16..19 u32  0x00000004      constant
off 20..   u32  args[num_args]
then            fmt string, NUL-terminated
then            file name (*.c), NUL-terminated
then            u16 CRC + 0x7E delimiter
```

**Parser:** `scratch/f3parse.py` → `parse(path) -> (raw, [dict(ts, code, line, args, fmt, file)])`.

**Validation:** the parsed argument count equals the format string's `%` count in **5 540 / 5 568 messages
(99.5 %)** on the UZ801 capture. The 28 mismatches are all `args=3` with 1–2 `%` (the field is rounded up,
not an arg count). Every message quoted in this doc has an exact match.

**Timestamp calibration:** `ts` ticks at **204 800 Hz**, calibrated over 230 s against the explicit
`TPC: SendPosReport: TimeTickMsec=%d` values (6 pairs spanning the capture, consistent).
**The epoch is not MPSS boot**, so `ts` is used for **relative ordering** only; absolute positions are
anchored by correlating a `TimeTickMsec` message.

### 4.1 ⚠ The false-positive trap (error #1)

The flat scan matches `79 00` pairs *inside* binary `0x10`/`0x92` payloads. On the stock capture
(94.6 MB, 24 211 `0x10` records) that produced **33 039 raw hits but only 31 443 real messages**, with a
raw `ts` span of **17 817 s for a 240 s capture**. The UZ801 capture was only ~2 % junk, which is why the
flaw went unnoticed at first.

**Fix — `scratch/f3clean.py`:** keep only messages whose `ts` is within ~40e6 ticks (≈195 s) of the
**median** `ts`. Clean spans then read 222.5 s (stock) and 235.3 s (UZ801), both consistent with the window.
**The tell: if `(max(ts) − min(ts))/204800` ≫ the capture duration, the parse is contaminated — do not
report the file list.** (Requiring a `0x7e` HDLC flag before the signature does **not** fix it; it let in a
*different* wrong set.)

---

## 5. The UZ801 boot: clean, and the `online` request never reaches Call Manager

### 5.1 Zero errors in 5 568 messages

A scan for `fail`, `error`, `reject`, `abort`, `invalid`, `timeout`, `not ready`, `deny`, `unable`,
`not support` returns **nothing**. The subsystems that matter all succeed:

| subsystem | evidence |
| :-- | :-- |
| SIM / MMGSDI | card-status events (`evt=0x13, 0x15, 0xc, 0x19`), sessions found, `Notify: 19 WMS_CFG_EVENT_MS_SIM_INIT_START` → `GW_READY` → `MS_READY` → `WMS_CFG_EVENT_MS_SIM_INIT_END` |
| EFS | `MMGSDI READ CNF cmd:0x26`; `file_enum 0xdc` (PLMNWACT) `status:0`; `file_enum 0x10b` (OPLMWACT) `status:0`; `Got file read confirmation(s)`; 67 `plmn(idx, mcc, mnc)` entries |
| CM RAT state | `=CM= mode_pref 0x26, pref_term 0x0`; `=CM= rat_disabled_mask as_id_0 0x0, as_id_1 0x0`; `RAT_DISABLED_MASK: sys_mode 9 is_rat_disabled=0`, `sys_mode 4 is_rat_disabled=0` |

### 5.2 The live signature (unchanged)

`--dms-set-operating-mode=online` → QMI **(52) DeviceNotReady**; `AT+CFUN=1` → `+CME ERROR: phone failure`;
`AT+CFUN?` → `+CFUN: 7`; `--dms-get-operating-mode` → `offline`; `--dms-get-user-lock-state` → `no`;
`--dms-get-activation-state` → `not-activated`; `AT+CPIN?` → `+CPIN: READY`.
`--dms-get-hardware-revision` → `'10000'`, `--dms-get-model` → `'0'`, `--dms-get-manufacturer` → `'1'`.
**Repeated online attempts make the QMI endpoint hang up** (`endpoint hangup`) — do not hammer it.

### 5.3 ★ The request dies in `ds`/DMS (falsifies Doc 89 §7.2)

`--dms-set-operating-mode=online`, captured concurrently with the F3 stream, produces exactly:

```
=CM= CMD alloc u=370, tsk=dcc     / free
=CM= CMD alloc u=374, tsk=ds      / free        <-- the data-services task
```

and **no `cm_ph_cmd_oprt_mode` message** — although UZ801's `b18` contains the
`cm_ph_cmd_oprt_mode(%s)` log tag at VA `0xc1ba847c`. A 256-SSID re-sweep during a second attempt added
nothing.

> **⇒ Doc 89 §7.2's mechanism — "CM rejects `SYS_OPRT_MODE_ONLINE` with `CM_PH_CMD_ERR_OFFLINE_S` (0x3c = 60),
> which `dsat` maps to `+CME ERROR: phone failure`" — is FALSIFIED for this case.** The refusal is generated
> inside `ds`/DMS *before* CM's opmode command is ever issued.

**What this settles:** the blocker is not the SIM, not EFS/rmtfs (NAS reads EFS fine), not the CM RAT mask,
and not a CM-level opmode rejection.
**What it does not settle:** *why* DMS says not-ready. **The RFC/RFFE subsystem logs nothing at all** — no
`rfc_*` file ever appears — so the log can neither confirm nor deny an RFC init failure.

---

## 6. The stock baseline: design and result

**Design.** Same AP, same NV, same hardware — **only the firmware set differs**. That makes any boot-log
difference a property of the firmware, not of the platform or the NV. `bash scratch/swap_uz21_full.sh
--revert` restored the stock set (MD5s verified against the expected values *before* the reboot:
`modem.mdt 1a6f9507…`, `wcnss.mdt 62bb56b2…`, `cmnlib.mdt 78752167…`, `mcfg_sw.mbn 0be0d361…`), one cold
reboot (`boot_id` `fb46e134…` → `10b2a9a5…`), the same `diagboot` harness and the same 240 s window.

| | STOCK `HIMI_U01_MODEM_V1.0` | UZ801-21 |
| :-- | --: | --: |
| capture size | **94 623 391 B** | 1 542 544 B |
| records | 32 050 (24 211 × `0x10`) | 1 439 (1 197 × `0x79`) |
| clean F3 messages | **31 443** | 5 461 |
| distinct log-emitting files | **40** | 23 |
| `--dms-get-operating-mode` at 253 s | **`online`** | `offline` |

---

## 7. ★ 25 subsystems run in the stock boot that UZ801 never touches

First-occurrence times are **seconds after the capture start**. The stock capture began at AP uptime
13.30 s while `remoteproc0` powered up at AP 11.13 s ⇒ **t0 ≈ modem uptime 2.1 s**. **ModemManager is S70;
wcn36xx (S19) is at 15.94 s.** So none of this can be AP-driven.

| first seen | msgs | file | what it is |
| --: | --: | :-- | :-- |
| 0.00 s | 551 | `cmss.c` | CM **serving system** (`Mapping csg_info, sys_mode:9`; `fmode:1`) |
| 0.00 s | 36 | `cmcall.c` | CM active-call |
| 0.01 s | 79 | `a2_ipfilter.c` | A2 IPv4/IPv6 filter rules installed |
| 0.01 s | 801 | `cmlog.c` | CM serving-system event log |
| 0.01 s | 30 | `qm_util.c` | GSM7→UTF16 text conversion |
| 0.29 s | 5 984 | `pgi_msgr.c` | PGI messaging |
| 0.29 s | 757 | `mcpm_saw.c` | **MCPM** `FW_WAKE-UP_Start` / `FW_SLEEP_PWRDN_FULL` |
| 0.29 s | 2 411 | `mcpm_npa.c` | **MCPM** NPA clock votes (`CLKCPU req for 384000, tech 6`) |
| **0.53 s** | **8 388** | **`rflte_core_rxctl.c`** | **LTE RF RX control** (`rx_gain_freq_comp_to_mdsp`) |
| **0.53 s** | **668** | **`rflte_mc_meas.c`** | **LTE measurement ctrl** — `IRAT LTE GRFC script GFRC Tx ant_sel_num 4 for device 0 band 4` |
| 0.78 s | 149 | `mmocdbg.c` | **`Prot_state [MAIN] 5(ONLINE_GWL)`** |
| 0.78 s | 10 | `mmoc.c` | MMOC |
| 0.79 s | 25 | `cmmsc_auto.c` | CM MSC auto |
| 1.17 s | 7 | `tlm_ptm.c` | `LTE EARFCN: (u) set to (u)` |
| 1.17 s | 18 | `a2_dl_phy.c` | A2 downlink PHY |
| 1.34 s | 22 | `gstkutil.c` | GSM stack util |
| 1.34 s | 45 | `sdss.c` | **`** Activate GWL opr script = ssscr_gw_opr_srv_info **`** |
| 1.35 s | 15 | `cmsds.c` | CM `Domsel:Update SIB-8 Status` |
| 1.35 s | 30 | `cmsoa.c` | **`=CM= main mode_pref=0x26, hybr2_operational=0`** |
| 1.66 s | 8 | `tm_umts_up_supl.c` | UMTS SUPL |
| 1.66 s | 9 | `tm_lpp_cp.c` | LPP positioning |
| 2.30 s | 6 | `a2_log.c` | A2 log |
| 6.89 s | 88 | `rfmeas_mc.c` | `rfmeas_mc_exit(): source_tech=8, target_tech=8` |
| 35.01 s | 12 | **`trm_config_handler.c`** | **Transceiver Resource Manager**: `SRLTE is enabled`, `Config Handler get srlte,dsds mode: 2` |
| 35.38 s | 106 | `mcpm.c` | `MCPM: tech wakeup_req` |

**UZ801-only files (8):** `mmocmmgsdi.c`, `wms.c`, `policyman_uim.c`, `mmgsdi_refresh.c`, `cmph.c`,
`wmssim.c`, `qmi_nas_mmgsdi.c`, `wmsmsg.c` — i.e. only the SIM/WMS/NAS/CM-core set.
**No `rflte_*`, no `cmss.c`, no `mcpm_*`, no `mmoc*`, no `trm_*`.**

Also from the stock log's first 0.01 s: `tle_log.c` reports a real LTE cell
(`MCC:405, MNC:861, CellId:4399664, PhysicalCellId:390, TAC:231`) and `mmgsdiutil.c` prints the SIM's SPN
`4a 69 6f` = **"Jio"**.

### 7.1 The confound, and why it does not apply

The stock modem is `online` from boot while UZ801 is not, so a stock-only subsystem *could* in principle be
downstream of being online. **It is not, for these 25:** they all appear within **0.78 s of the capture
start (modem uptime ≈ 2.1 s)**, before ModemManager exists and before the AP has sent the modem anything.
A fully clean separation would need the stock firmware booted with the modem prevented from going online;
that experiment was **not** run (§11).

---

## 8. What is *not* the differentiator

- **`mode_pref` is `0x26` in BOTH.** UZ801 logs it from `cmph.c` (`=CM= mode_pref 0x26, pref_term 0x0`);
  stock logs the same value from `cmsoa.c` at 1.35 s (`=CM= main mode_pref=0x26, hybr2_operational=0`).
  The mode preference is not the cause.
- **`=CM= CMD alloc u=N` — `u` is a monotonically increasing command HANDLE, not an opcode** (error #2).
  Stock: 26, 54, 75, 107, 140, 173, 205, 238 (≈ +33 each, one every ~32 s). So `u=374, tsk=ds` means
  "CM command #374, issued by the data-services task", **not** "opcode 374".
- **A real CM-debug build difference:** stock logs `=CM= CMD alloc` at `cmdbg.c:2181`, UZ801 at
  `cmdbg.c:2186`; and `cmph.c` appears **only** on UZ801. The two builds log the same CM state from
  different files/lines — a reminder not to compare log *lines* across builds.
- **Neither build ever logs an opmode / `cm_ph_cmd_oprt_mode` string** (0 hits in both), so the log cannot
  show the online transition directly.

---

## 9. Corrections and retractions issued by this session

### 9.1 Doc 89 §7.2 — FALSIFIED (see §5.3)

### 9.2 "`/rfc_ver_data` is absent from the EFS" — RETRACTED (error #3)

The claim rested on grepping `GitIgnore/MelbonWhiteStock_Dump/{modemst1,modemst2,fsg}.bin` and the live
`/dev/mmcblk0p{1,2,4,5}` for `rfc_ver_data` / `rfc_wtr1605` / `rfc_device_info` / `gnss_device_info` →
0 hits. **The EFS is an encrypted/compressed image.** Re-read with a hexdump, `mmcblk0p4` and `p2` both begin
`10 00 00 00 03 00 00 00 … 49 4d 47 45 46 53 31` (`IMGEFS1`) / `IMGEFS- SIGNED_IMAGE` followed by
high-entropy bytes. **A plaintext grep of an `IMGEFS1` partition proves nothing about which paths exist.**

The apparent contradiction this claim created — "both firmwares' reads would fail, yet HMU05 works" —
**dissolves**, and the RFC hypothesis loses its only evidence. *(The `24–40 printable strings in 1.5 MB` was
the tell that the container was encoded; it should have made the grep distrusted, not trusted.)*

### 9.3 The `echo stop` hazard — re-confirmed

`echo stop > /sys/class/remoteproc/remoteproc0/state` **reset the AP** (`boot_id` changed, `console-ramoops-0`
present, PMIC PON WDT stop — not a panic). Not a usable "restart the modem" lever. A cold reboot is the only
clean modem restart. (See `159`/`167`/`168`/`169`.)

---

## 10. Reproducibility — n = 6, parser-independent

A **raw byte-count** over **all six UZ801 captures** — three `diagboot` boot captures (`uz801_boot.bin`,
`uz801_boot_4.bin` from the re-deploy reboot, and the original) plus the earlier ad-hoc
`uz801_online256.bin` / `uz801_online_attempt.bin` / `uz801_online.bin` — returns **0** for every one of
`rflte_core_rxctl.c`, `rflte_mc_meas.c`, `rfmeas_mc.c`, `cmss.c`, `cmlog.c`, `mcpm_npa.c`, `mcpm_saw.c`,
`mcpm.c`, `mmocdbg.c`, `trm_config_handler.c`, `sdss.c`, `a2_ipfilter.c`, `pgi_msgr.c`, `qm_util.c`
**and for the literal `ONLINE_GWL`**, while `hmu05_stock_boot.bin` has all of them (`ONLINE_GWL` ×54).

Two independent UZ801 boot captures also agree on total size (1 542 544 B vs 1 650 822 B — same shape).
**No parser is involved in this check, so the result does not depend on the F3 decoding.**
(`Jio` appears once in every capture — the SIM's SPN.)

---

## 11. Verdict, and the question that remains

**Verdict.** The UZ801-21 firmware's **boot path never initialises the LTE RF / protocol stack**; the stock
HMU05 firmware's does, within ~2 s and before the AP can influence it. That is the direct explanation of the
`offline` state and of the QMI `DeviceNotReady` refusal: DMS's precondition is a device/protocol stack that
the UZ801 boot path never brings up.

**What remains open — and is deliberately not guessed at here.** *Which single gate* stops the RF/protocol
init. The leading candidate is the platform/HWID-derived device configuration — precisely the axis the
UFI001B recipe's Patches 1/2/7/8/10/13 attack (`100_...`, `89 §4`) — **but the RFC/RFFE subsystem logs
nothing at all, so it stays unproven.** Two routes would settle it:

1. **Cheap:** boot the stock firmware with the modem prevented from going online, to fully separate cause
   from consequence in §7.1. Requires an intervention before the modem auto-onlines at ~2 s.
2. **Definitive:** firmware RE of the UZ801 boot path's RF/device-config gate, differentially against the
   stock image. This is the only route that can name the gate.

**Scope reminder.** None of this reopens whether the swap can fix the ~900 s fatal. It cannot: the fault
table and the crash branch are byte-identical between the sets (`143`/`145`), and a third party reports the
same ~900 s crash on a UZ801 build. **Making this firmware reach `online` would *arm* the LTE-gated fatal
clock (P-PMOS3), i.e. it would make the device worse.**

---

## 12. Evidence inventory

| artifact | what it is |
| :-- | :-- |
| `scratch/hmu05_stock_boot.bin` (+ `.gz`) | stock boot capture, 94 623 391 B |
| `scratch/hmu05_stock_boot.log` | stock `diagboot` log (bind times, post-boot QMI, dmesg) |
| `scratch/uz801_boot.bin`, `scratch/uz801_boot.log` | UZ801 boot capture #1, 1 542 544 B |
| `scratch/uz801_boot_4.bin` | UZ801 boot capture #2 (re-deploy reboot), 1 650 822 B |
| `scratch/diag_uz801_online.bin`, `…_cfun.bin`, `…_online_attempt.bin`, `…_online256.bin` | earlier ad-hoc UZ801 captures |
| `scratch/f3parse.py` | the F3 parser |
| `scratch/f3clean.py` | the median-`ts` cluster filter (§4.1) |
| `scratch/uz801_diagboot_run.sh`, `scratch/uz801_diagboot.init` | the boot-log instrument |
| `scratch/swap_uz21_full.sh` | the firmware swap / `--revert` |
| device-side: `/root/diag_boot_uz801.bin`, `/root/diagboot_uz801.log` | preserved UZ801 artifacts |

> `scratch/` is gitignored; the artifacts live on the build host and on the device. The **stock** capture is
> the one that would be expensive to re-create (it needs a firmware revert + reboot) — do not delete it.

## 13. How to re-run

```bash
# 1. instrument is already deployed and enabled; confirm:
ssh root@192.168.8.1 '/etc/init.d/diagboot enabled && echo enabled'

# 2. cold reboot (the ONLY clean modem restart — never `echo stop`)
ssh root@192.168.8.1 'sync; sync; reboot'

# 3. wait ~260 s, then pull
scp root@192.168.8.1:/root/diag_boot.bin        scratch/reboot_boot.bin
scp root@192.168.8.1:/root/diagboot.log         scratch/reboot_boot.log

# 4. parse, and ALWAYS check the span before trusting the file list (§4.1)
python3 -c "import sys;sys.path.insert(0,'scratch');from f3clean import clean;ms=clean('scratch/reboot_boot.bin');ts=[m['ts'] for m in ms];print(len(ms),'msgs span',(max(ts)-min(ts))/204800,'s (expect <= ~240)')"
```

To swap firmware sets: `bash scratch/swap_uz21_full.sh` (to UZ801) or `… --revert` (to stock HMU05).
**Disable the instrument when done:** `/etc/init.d/diagboot disable` — it otherwise captures 240 s on every
boot and holds `/dev/rpmsg0` exclusively for that window.
