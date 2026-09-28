# Doc 224 — The EFS instrument is repaired, the live EFS is an HMU05 superset, and a raw DIAG write is a new AP-reset hazard

**Date:** 2026-09-28
**Ledger:** this doc MUST be read with `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md` (item 30).
**Scope:** UZ801-on-HMU05 offline gate. No baseband patch is proposed by this doc.

---

## 0. SOP compliance statement

| SOP step | Taken? | Note |
|---|---|---|
| Verify against stock HMU05 ground truth | **yes** | §4 compares the live EFS against `scratch/efs_H`; §5 compares `cmregprx.c` descriptors against `GitIgnore/MelbonWhiteStock_Dump/modem_extracted/image/modem.elf` |
| Pre-register before measuring | n/a | this doc is instrument repair + a census; no hypothesis is scored |
| Freeze/hash every capture | **yes** | live EFS listing archived at `evidence/224_efs_live/efs_live_20260928.txt` |
| Assert the control TOOK | **yes** | §2 verifies the F3 flood actually stopped/resumed by re-probing |
| Never blind-patch a baseband | **yes** | nothing was written to the modem |
| State irreversible steps first | **yes** | §3 is an incident report on an accidental AP reset |
| Update the ledger in the same session | **yes** | ledger item 30 |

---

## 1. Achieved vs Expected

| Item | Expected | Achieved | Verdict |
|---|---|---|---|
| Read the modem EFS live | working `diag_efs` | **repaired and working** (§2) | **MET** |
| Enumerate the live EFS | full listing | 1 315 paths captured and archived (§4) | **MET** |
| Live EFS vs HMU05 reference | know whether EFS differs | live is a strict **superset** of HMU05 — 0 HMU05 paths missing (§4) | **MET** |
| Resolve the offline latch | `is_gwl_subs_avail 0->1` | still `0->0` | **NOT MET** |
| Modem online | `+CFUN: 1` | still `+CFUN: 7` | **NOT MET** |

**The goal is not met.** What this doc adds is a working EFS read instrument and two
negative results that close off EFS-as-cause.

---

## 2. The EFS instrument was broken, and why — repaired

`/root/diag_efs` and `/root/diag_efs_new` both failed with `open: bad resp 79` /
`stat: bad resp 79`. Two independent causes:

1. **The DIAG channel carries a CONTINUOUS spontaneous F3 stream.** Measured with
   `scratch/diag_probe2.c`: **19 of 20 consecutive `read()`s returned an F3 (`0x79`)
   record**, not the reply. `efs_txn()` in `packages/diag-efs/src/diag_efs.c` calls
   `read_msg()` **once**, so it almost always consumed an F3 record. This is the same
   class of defect as Doc 184 §5.1's "single read" bugs.

   *Fix:* read until a wall-clock budget expires, discarding every message that is not
   `resp[0]==0x4b && resp[1]==req[1] && resp[2]==req[2]`. Shipped as
   `scratch/diag_efs_skip.c` → `/root/diag_efs_skip` (built with the in-tree
   `openwrt/staging_dir/toolchain-aarch64_generic_gcc-14.3.0_musl` toolchain, `-static`).

2. **The reply framing was misread.** A real reply is `4b 13 <subcmd> 00 | payload |
   crc(2) | 0x7e`. One observed reply carried a stray leading `0x15` byte; the STAT
   and OPENDIR replies do not. The instrument must key on the header at offset 0 and
   must **not** assume a length prefix.

`EFS_DUMP=1` now prints `REQ`/`RESP` hex, which is how the layouts in §4 were read.

**Verified working** (`/root/diag_efs_skip`):

```
stat /          -> mode=000041ff size=34
stat /nv        -> mode=000041ff size=5
list /          -> .efs_private, CGPS_ME, CGPS_PE, CGPS_SM, SUPL, Uim1Reset.Txt, ...
list /          -> 1 315 entries, rc=0
```

---

## 3. NEW HAZARD: a raw DIAG write RESET THE AP

**Incident.** `scratch/diag_probe` was run with a 1-byte raw request (`raw "00"`,
the DIAG version request) on `/dev/rpmsg0`. The SSH session died with
`Connection reset by peer`. On reconnect:

| | before | after |
|---|---|---|
| `/proc/uptime` | 27 919 s | **51 s** |
| `boot_id` | `73a08a06-eb18-4848-9915-48c77f24f9db` | **`a45f2ecd-d163-40a9-8346-c1fa6524a70c`** |

So a **single arbitrary byte written to `/dev/rpmsg0` took the whole AP down.** This is
the same class as the `echo stop` hazard (`project_ap_hang_and_capture.md`) and is a
**new hang/reboot site**, not previously recorded.

**Consequences / rules**

* Never write an arbitrary or truncated byte string to `/dev/rpmsg0`. Only send
  well-formed requests (`4b 13 <subcmd> 00 | …`), and never a bare 1-byte payload.
* The reset also **destroys the rpmsg bindings** — `/dev/rpmsg0` and `/dev/rpmsg1`
  disappeared and had to be recreated with `/root/diag_bind.sh`. `diag-bind-watch`
  did **not** restore them (it only acts when the SMD device is *unbound*, and here
  the device was re-created but the char nodes were missing).
* **The baseband was NOT damaged.** `modem.mdt` md5 `c132421f…`, `modem.b00`
  `b83653c3…`, `mba.mbn` `4869138c…` — byte-identical to the deployed UZ801 v32_x set.
  No `BOOTLOOP_REVERT.txt`. The modem came back `running` and still `offline`.
* **A boot_id change alone is not an AP hang** (standing rule) — but here the uptime
  reset proves a genuine restart.

---

## 4. The live EFS, and the EFS-as-cause hypothesis is now CLOSED

Full listing archived: `evidence/224_efs_live/efs_live_20260928.txt` (1 315 entries).

### 4.1 The live EFS is a strict SUPERSET of the HMU05 reference

Comparing the live listing against `scratch/efs_H/efs_H` (rooted at `/nv/item_files`):

| direction | count |
|---|---|
| in HMU05 reference but **not** live | **0** |
| in live but **not** HMU05 reference | 184 |

The 184 extra entries are all **runtime-created**: the GPS almanac set
(`CGPS_PE/AlmFile00…`, `CGPS_ME/CGPSCellDBFile`, `gpsoffsets.bin`), the APDU logs
(`Uim1Reset.Txt`, `Uim{1,2,3}EfsAPDULog.Txt`, `UimEfsAPDULog.Txt`), `.efs_private`.
None is subscription-related.

**⇒ The EFS is NOT missing anything the HMU05 stock modem has.** The
"the UZ801 firmware can't find its NV" family of hypotheses is closed for the
*HMU05-owned* items.

### 4.2 Paths named by the UZ801 firmware but absent from the live EFS

`scratch/doc224_nv_diff.py` extracts every `/nv/item_files/…` literal from both ELFs
(UZ801 **608**, stock HMU05 **510**) and diffs against the live set. Of the UIM family:

| path | in stock HMU05 ELF? | in live EFS? |
|---|---|---|
| `/nv/item_files/modem/uim/uimdrv/nv_active_slot_configuration` | yes | **no** |
| `/nv/item_files/modem/uim/uimdrv/feature_support_hotswap` | yes | **no** |
| `/nv/item_files/modem/uim/uimdrv/nv_pdown_uim_consecutive_techproblems` | yes | **no** |
| `/nv/item_files/modem/uim/mmgsdi/refresh_vote_ok` | yes | **no** |
| `/nv/item_files/jcdma/uim_jcdma_mode` | yes | **no** |

**These absences are NOT diagnostic.** They are absent from the *stock HMU05 device's*
EFS too (0 hits for `nv_active` in `scratch/efs_H`), and the stock HMU05 firmware runs
`online` happily without them. They are optional/created-on-demand items.

The live `uimdrv/` directory holds exactly six entries:
`me_hotswap_configuration`, `sim_response_timers_config`, `uim_busy_response_simulate_null_config`,
`uim_features_status_list`, `uim_hw_config` (+ the directory itself).

### 4.3 Only ONE subscription-ish NV path exists, in either arm

```
/nv/item_files/modem/mmode/ue_based_cw_Subscription01        (2 bytes, value 0x0000)
/nv/item_files/modem/nas/mm_backoff_remaining_info_subscription01
```

There is no `…Subscription02`. This is consistent with a **single-subscription** board
and is a weak signal that the UZ801 firmware's dual-subscription machinery
(`uimsub_manager.c`, `mcfg_uim.c`) has only one subscription to work with — but §4.1
means the EFS cannot be the *missing* input.

### 4.4 The UZ801 device's own NV is not readable (re-confirmed)

The full UZ801 device dump exists (`GitIgnore/UZ801 Modem/UZ801_Stock/dump/`):
`modemst1.bin`, `modemst2.bin`, `fsg.bin`, `fsc.bin`, `persist.bin`, `system.bin`.
**All three NV partitions contain ZERO plaintext EFS path strings** (`/nv/item_files`
0 hits, `uimdrv` 0 hits, `active_slot` 0 hits). This is a fresh, independent
confirmation of `reference_modem_efs_crypto.md` §8: the UZ801 EFS is
per-chip-encrypted and is not a usable oracle.

---

## 5. `cmregprx.c` is a DIFFERENT SOURCE VERSION in the UZ801 build

Decoding the F3 descriptor tables of both ELFs (`scratch/doc224_cmregprx_desc2.py`)
gives the complete `cmregprx.c` descriptor set — **22 entries in each build**, same
format strings, **shifted line numbers**:

| record | stock line | UZ801 line | Δ |
|---|---|---|---|
| `ph_stat_chgd: as_id=%d, is_gwl_subs_avail %d->%d` | 8099 | 8129 | +30 |
| `ph_stat_chgd: ue_mode_chgd: %d->%d` | 8105 | 8135 | +30 |
| `ph_stat_chgd: is_ue_mode_substate_srlte %d->%d` | 8109 | 8139 | +30 |
| `received fplmn_list,wait_resp:%d send_unblock:%d` | 11821 | 11860 | +39 |
| `STOP_MODE_CNF: ss=%d, state=%d, sub_state=%d` | 12493 | 12536 | +43 |
| `ph_stat_chgd: MMOC->CM: ue_mode= %d, …` | 14031 | 14079 | +48 |
| `is_wait_srv_cnf: %d, wait for srv cnf on ss %d` | 16223 | 16272 | +49 |

Δ grows monotonically 30 → 49, so **code was ADDED throughout `cmregprx.c`**, not just
at the top. The UZ801 build is a later/larger revision of the very file that computes
`is_gwl_subs_avail`.

**Not yet located:** the function containing the `is_gwl_subs_avail` site. The
descriptor is at `0xc165f420` (UZ801) / `0xc155ee20` (stock); neither is referenced as
a bare literal in the decompile (unlike e.g. `FUN_c091d840(&DAT_c1750178)` elsewhere),
and an `immext` scan for both the descriptor VA and its `packed` value (`0x1fc1000b`)
returns 0 hits — so the log site is reached by a computed address, not an embedded
pointer. **This is the open item.**

---

## 6. `is_gwl_subs_avail` — re-confirmed, window-controlled

Whole-capture counts (`scratch/doc224_counts.py`), so the length bias of Doc 223 §1
does not apply:

| capture | `is_gwl_subs_avail` args | `cmss.c` | `fplmn_list` | `PRECOND_SS` |
|---|---|---|---|---|
| UZ801 B10 canon | `[0,0,0]` → **0→0** | 1 | 0 | 0 |
| UZ801 v19 240 | `[0,0,0]` → **0→0** | 0 | 0 | 0 |
| stock 211 | `[0,0,1]` → **0→1** | 18 | 2 | 1 |
| stock 240 | `[0,0,1]` → **0→1** | 18 | 2 | 1 |
| stock ONLINE | `[0,0,1]` then `[0,1,1]` | 640 | 2 | 2 |

Format string (full): `=CMREGPRX= ph_stat_chgd: as_id=%d, is_gwl_subs_avail %d->%d`.
The surrounding `MMOC->CM: ss=%d, ue_mode=%d` is `[0,0,0]` in **both** arms, so the flag
is **not** derived from the serving system or the UE mode at that moment.

`cmss.c` (CM serving system) is effectively **silent** on the UZ801 boot (1 and 0
records) versus 18/18/640 on stock. The UZ801-only files `uimsub_manager.c` (9/6) and
`mcfg_uim.c` (9/9) fire only on UZ801, and the `nv_active_slot` read fails (1/1).

---

## 7. Refined root question

> `cmregprx.c` differs by +30…+49 lines between the two builds. **Which added block
> gates the `is_gwl_subs_avail` computation, and what input does it require that the
> UZ801 firmware cannot obtain on HMU05 hardware?**

Secondary: `cmss.c` never running implies the CM serving-system path is never entered
at all — is that a *cause* (something upstream is missing) or a *consequence* of
`is_gwl_subs_avail == 0`?

---

## 8. Artifacts produced

| artifact | purpose |
|---|---|
| `scratch/diag_efs_skip.c` → `/root/diag_efs_skip` | EFS read tool that skips the F3 flood (repaired instrument) |
| `scratch/diag_probe.c`, `scratch/diag_probe2.c` → `/root/diag_probe`, `/root/diag_probe2` | raw DIAG probe + traffic classifier |
| `scratch/doc224_nv_diff.py` | UZ801/stock NV-path sets vs the live EFS |
| `scratch/doc224_cmregprx_desc2.py` | full `cmregprx.c` descriptor table, both builds |
| `scratch/doc224_counts.py`, `scratch/doc224_span.py`, `scratch/doc224_bootwindow_diff.py` | window-controlled F3 censuses |
| `evidence/224_efs_live/efs_live_20260928.txt` | the frozen live EFS listing (1 315 entries) |

---

## 9. Status

The offline latch is **not** resolved. The EFS-as-cause hypothesis is **closed**
(§4.1). The instrument gap that blocked every EFS experiment is **repaired** (§2).
A new AP-reset hazard is **recorded** (§3). The next action is §7.
