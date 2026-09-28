# 197 — UZ801-on-HMU05 port: LIVING LEDGER, the bootloop fallback, and the MANDATORY SOP

**Date opened:** 2026-09-26
**Device:** HMU05 4G modem stick (`Melbon HMU05`), OpenWrt 25.12.5, kernel 6.12.94 aarch64, `192.168.8.1`, SSH root.
**Firmware under test:** UZ801 v3.2 "-21" baseband `MPSS.DPM.1.0.1.C1-00121`, patched in `modem.b16`.
**Status:** **LIVING DOCUMENT — append to it, do not fork it.** This file is the single source of truth for
what the port has done, what it achieved against what it expected, and how it is kept safe.

> **Ledger:** this doc. Every document written from 197 onward MUST reference it (see §1).

**Companions:** `194` (the UZ801 boot log proves the LTE stack never initialises), `195` (the
`offline → online` patching campaign, v1–v14), `196` (the DIAG bridge + `diag_logtool` reference),
`198` (the recovered v15/v16 outcomes and the invariant MMOC boot), `199` (v18/GROUP 14 tested negative,
the "v17" mis-build corrected, and the named stock-vs-UZ801 `DUAL_STANDBY_CHGD` divergence — **§4 of
which `200` corrects**), **`200`** (the online gate re-derived: stock does not auto-online either, the
CM code is **identical**, and the break is the `dcc`-task CM command being dropped before `cmmsc_auto`),
**`201`** (**corrects `200` §6.2/§8**: `cmd+0x14` is the modem's **uptime in seconds**, so the
`0x1000001` whitelist gate is effectively dead and the planned v20 patch is **withdrawn**; the
`0xc1ba7894` table is a code switch; the pivot is `cmmsc_auto`; **and the stock-vs-UZ801 subsystem
differential is re-opened as mask-confounded**),
**`202`** (**retracts `194` §7**: stock does **not** auto-online — its boot chunk is all `Prot_state
0(NULL)`; `scratch/f3clean.py`'s median-`ts` window **deleted two real clusters** (the boot chunk and the
online transition), which is where Doc 194's "0.78 s / modem uptime 2.1 s" came from; the LTE stack is
**online-gated**, not boot-gated; the real divergence is that the **`u=1, tsk=cm` CM command's completion
path skips `cmmsc_auto`** on UZ801; and the mask control in `201` §6 was run on a **coverage-blind**
capture),
**`203`** (**corrects `201` §6's delta and `202` §6's "same `u=1, tsk=cm`" claim** — the 64→256 delta is
`nvruim.c` *lost*, not `rcinit_rex.c` gained, and the first CM command's `tsk` is **capture-class
dependent** (`mmgsdi_1` in the burst class, `cm` in the blind class); **proves** the 256-sweep provenance
from the live log (256 `F3_MASK` packets) and the device mtimes; **★ and names a new, 16/16-correlated
precursor of `Prot_state 7(OFFLINE)`: a spurious `card_status_cb(evt=0, sess_id=0)` MMGSDI event**; plus
the deployed instrument fix — `/root/diagboot.status`),
**`204`** (**ATTACKS and then CORRECTS `203` §6.1**: the `u=1` race is real and exactly separating, and it
**does** control the spurious `evt=0` — but it is **NOT the online gate**. Four fresh v19 reboots are all
`cm`-wins (**BLIND**) and the modem is **`offline`** on the measured same-boot pair; the BURST class is
`offline` by its own `Prot_state 7(OFFLINE)`. The **class-invariant** gate is that `cmmsc_auto.c` /
`cmss.c` are **0 in all 22 UZ801 captures** (both classes) vs **35 / 590** in stock, and the modem itself
refuses: `AT+CFUN?` = `7`, `AT+CPIN?` = `READY`, **`AT+CFUN=1` → `+CME ERROR: phone failure`**. The `evt=0`
patch is therefore downgraded from "fallback" to "not worth building", and the race is demoted from
"patch target" to "class marker"),
**`205`** (the F3 **descriptor table** — the static route to `cmmsc_auto` is closed, the "missing caller"
hypothesis is dead from both ends, and the **complete op-mode refusal** is enumerated),
**`206`** (**retracts a corpus-wide misidentification**: `FUN_c06928a8` is **`cmcc.c`**, not
`cmmsc_auto.c`, so the deployed GROUP 11 patch has never touched its stated target; the "no static caller
⇒ never invoked" inference is **falsified by stock's own behaviour**; the **WMS/MMGSDI input path is
intact in both builds**),
**`207`** (the descriptor model **corrected** — `word1` is a hash for ~92 % of sites, not a string pointer
— plus the descriptor→`file:line` instrument, and the measurement that closes the `cmcc.c` axis:
`FUN_c06928a8` ≡ stock `FUN_c066fa30` ⇒ **GROUP 11 is inert by construction**),
**`208`** (the CM dispatcher is **structurally identical** — 83.49 % raw `fdiff` collapses to 2
declaration-order blocks once `unaff_GP` offsets are normalised; the stock and UZ801 F3 arms used the
**same `cntl-enable 64` mask**; the F3 record's `code` field is **not** the AP-selected SSID; **and the F3
burst-capture class is LOST** — 20 consecutive blind boots, the gating blocker),
**`210`** (**clears that blocker and REFUTES five load-bearing campaign negatives**: the burst class is
restored by replacing a 1-second DIAG-device poll with `sleep 0.05`, and the restored class is **deeper**
than the campaign's "burst" class — `rcinit_init.c` = 76–171 vs **0** — so `cmss.c` / `cmmsc_auto.c` /
`cmregprx.c` / `sdss.c` / `sdcmd.c` / `mmoc.c` all **run**, and `cmmsc_auto` emits the exact stock sequence
including `updating op_mode`; it also opens a **new root-cause lead** — the UZ801 policyman ends the boot
with **`Filtered RAT mask 0 based on HW capabilities 544`**, its RAT/RF/UE-mode EFS items cannot be
populated and the write-back fails, while two independent controls prove the EFS is neither read-only nor
unwritable — and finds a **census-invisible CM divergence**: the `MSC_AUTO` mask is `0x220` on stock and
`0xebe` on UZ801, and `0x220` = **544** = exactly the value UZ801's policyman calls its HW capabilities),
`143`/`145` (the transplant verdict — the swap cannot fix the ~900 s fatal).

---

## 1. MANDATORY SOP — applies to every document from 197 onward

This is a standing instruction from the operator (2026-09-26). It is not optional and it is not
retroactive-only: **each new doc must carry all four of the following.**

1. **A ledger reference.** A line of the form
   `**Ledger:** see 197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md` near the top, so any reader lands on the
   current achieved-vs-expected state before trusting the new doc.
2. **An "Achieved vs Expected" section.** An explicit table with columns
   `Intent | Expected | Achieved | Status`. No new doc may state a result without naming what it was
   *supposed* to achieve and whether it did. Use `NOT MET`, `PARTIAL`, `MET`, `UNEXPECTED` — do not use
   adjectives.
3. **An SOP-compliance statement.** Per the five-step protocol in `133 §1` (backup → ground-truth
   verification against stock HMU05 → reconcile transport/config → surgical Hexagon patching +
   re-signing → live empirical validation), as modelled by `194 §1`. Name every step taken and every
   step skipped, and say why.
4. **A status line.** `Status:` in the header, using the vocabulary already in the corpus
   (`IN PROGRESS` / `RESULTS` / `RETRACTED` / `LIVING DOCUMENT`).

Additionally, this ledger itself must be **updated in the same session** as any change it describes —
a patch deployed, a threshold changed, a result obtained. If a doc and this ledger disagree, this
ledger wins and the doc is stale.

---

## 2. The bootloop fallback (`modem-guard`)

### 2.1 The hazard it exists for

A bad baseband patch can make the modem crash inside its own boot path. The crash trips the **PMIC PON
watchdog**, which resets the **whole SoC** — so the AP reboots, the bad firmware crashes again, and the
device is in an AP reset loop that costs physical access to break. This is not hypothetical: it
happened twice during the campaign, at **v9** and **v10** (Doc 195 §7.5/§7.6), and both times was
recovered only by racing an SSH window.

### 2.2 What it does

`modem-guard` is a small on-device service that counts **consecutive boots that never reach a stable
state**, and once that count exceeds the threshold it restores the **stock HMU05 modem firmware** so
the device comes back up and the offending patch can be inspected.

| Property | Value |
| :-- | :-- |
| Trigger | `boot_count > MAX_BOOTLOOPS` at boot, i.e. **more than 3 bootloops** |
| Threshold | `MAX_BOOTLOOPS = 3` (revert happens on the 4th unproven boot) |
| "Good boot" | the AP has stayed up `GOOD_AFTER = 180 s` |
| Revert source | `/overlay/fwbackup/hmu05_stock/` (`modem.*` + `mba.mbn`, 22 files) |
| Revert verified by | `modem.mdt` md5 `1a6f9507e03d4ddbbf1977af81ecdbd7` |
| Runs at | `S01` — **measured at AP uptime 9.47 s, before the kernel powers up the modem at 11.72 s** |
| Counter cleared by | a clean shutdown (either hook below) **or** surviving 180 s **or** a deploy |

### 2.3 Why the counter is trustworthy — clean reboot vs crash

The counter only climbs when a boot ends **without** a clean shutdown. That distinction is real, and it
was measured, not assumed:

- `procd`'s shutdown runs `/etc/rc.d/K*` in **ascending** order (`/etc/inittab`:
  `::shutdown:/etc/init.d/rcS K shutdown`). `umount` is `K90` and `umount-overlay` is `K98`, and they
  remount the overlay read-only. A `K99` hook therefore **silently failed to write** (measured — two
  reboots produced no marker). Moving the hook to **`K01`** makes it run first, while the overlay is
  still writable, and it now lands. *(This is the one non-obvious detail of the whole mechanism.)*
- A **PMIC PON watchdog reset** skips the shutdown entirely, so neither hook runs and the counter is
  preserved.

Two independent hooks are armed, both measured working:

```
clean shutdown: counter 1 -> 0            # K01 stop_service (primary)
clean shutdown (signal): counter 0 -> 0   # procd's kill(-1, SIGTERM) -> watcher trap (backup)
```

### 2.4 What is preserved for inspection

Before anything is overwritten, the live (bad) set is copied verbatim to
`/overlay/modem_guard/quarantine/<timestamp>/` together with:

- `deployed.md5` — md5 of every live `modem.*` + `mba.mbn`,
- `deployed.info` — the deploy manifest (label, source set, host, timestamp),
- `context.txt` — boot count, live and expected `modem.mdt` md5, paths.

A human-readable summary is written to `/overlay/modem_guard/REVERT_DONE` and to
**`/root/BOOTLOOP_REVERT.txt`**, which is the first thing seen on login. The full timeline is
`/overlay/modem_guard/events.log`.

### 2.5 Operating it

```bash
# status: counter, live vs stock identity, last revert, recent events
ssh root@192.168.8.1 '/root/modem_guard.sh status'

# re-arm for a fresh patch trial (also done automatically by the deploy helper)
ssh root@192.168.8.1 '/root/modem_guard.sh reset'

# rescue by hand, now
ssh root@192.168.8.1 '/root/modem_guard.sh revert-now'

# turn it off / on (a DISABLED marker also works, and survives reboots)
ssh root@192.168.8.1 '/etc/init.d/modem-guard disable'
ssh root@192.168.8.1 '/etc/init.d/modem-guard enable'
```

Tunables live in `/overlay/modem_guard/config` (shell syntax): `MAX_BOOTLOOPS`, `GOOD_AFTER`,
`STOCK_DIR`, `STOCK_MDT_MD5`.

### 2.6 Limits — stated, not hidden

1. **A crash that resets the SoC before the earliest userspace script runs cannot be caught on that
   boot.** The counter is persistent, so as long as *any* boot reaches `S01` the mechanism converges —
   but a firmware that dies inside the kernel's deferred probe, every boot, would loop without
   recovery. The two observed bootloops (v9/v10) both gave a userspace window, so this is a boundary,
   not a present failure.
2. **An AP hang is not a bootloop.** A hang does not reset the SoC, so no counter moves. The
   `echo stop` hazard and the SSR-teardown hang (`159`/`166`–`169`) are out of scope here.
3. **The fallback restores the modem subsystem only** (`modem.*` + `mba.mbn`). The WCNSS / TrustZone
   images are deliberately untouched — the port does not patch them.
4. **Four rapid intentional reboots, each shorter than 180 s, would false-trigger.** The clean-shutdown
   hook makes this unlikely (a normal `reboot` clears the counter), and the deploy helper resets it, but
   it is the residual edge.

### 2.7 Verification record (2026-09-26)

| Check | Result |
| :-- | :-- |
| Sandbox: counter increments, reverts at `count > 3`, quarantines, resets | PASS |
| Sandbox: already-stock firmware does not revert | PASS |
| Sandbox: missing stock backup → `REVERT_FAILED`, live set untouched | PASS |
| Sandbox: `DISABLED` marker suppresses counting | PASS |
| Sandbox: watcher clears counter after `GOOD_AFTER`; SIGTERM clears it | PASS |
| Device: `S01` runs **before** the modem probe (9.47 s vs 11.72 s) | PASS |
| Device: real `revert-now` → live `modem.mdt` `14df2f49…` → `1a6f9507…`, quarantine intact | PASS |
| Device: clean `reboot` clears the counter (both hooks logged) | PASS |
| Device: `mark good` clears the counter at 180 s uptime (`10:35:43 mark good: up >= 180s, counter 1 -> 0`) | PASS |
| Device: `modem.mdt` restored from stock is md5-verified before the counter is reset | PASS |
| Deploy helper: exactly 22 files, no `.bak`/`.patched` scratch copies | PASS (after a bug fix — see §4 note) |

---

## 3. Achieved vs Expected

| Intent | Expected | Achieved | Status |
| :-- | :-- | :-- | :-- |
| Remove QMI **error 52** (`DeviceNotReady`) on `set-operating-mode=online` | QMI returns success | QMI returns **"set successfully"** in < 1 s | **MET** (v8 onward) |
| Bring the UZ801 modem to `online` on HMU05 hardware | `Mode: 'online'`, LTE attached | `Mode: 'offline'`; `NAS: not-registered`; `radio interfaces: 1` | **NOT MET** |
| Initialise the LTE RF / protocol stack at boot | `rflte_*` / `cmss.c` / `mcpm_*` / `mmocdbg.c` emit `Prot_state 5(ONLINE_GWL)` | **RE-SCOPED by Doc 202**: stock does **not** do this at boot either — its boot chunk is all `Prot_state 0(NULL)` and the LTE layer (`rflte_*`, `pgi_msgr`, `mcpm_*`) is **online-gated** (first appears 6–8 ms **after** the AP-driven `5(ONLINE_GWL)`). What UZ801 genuinely lacks, re-scored on **burst-caught** captures, is the **CM serving-system / MMOC boot layer**: `cmss.c` / `cmlog.c` / `cmmsc_auto.c` / `cmregprx.c` / `sdss.c` / `sdcmd.c` / `mmoc.c` / `policyman_serving_system.c` / `wmsmsg.c` = **0 in all six burst-caught UZ801 captures** (Doc 202 §5.3) — **⚠ THE PREMISE OF THIS RE-SCOPE IS RETRACTED by Doc 210 §3.2/§4.2**: those "burst-caught" captures carry **`rcinit_init.c` = 0**, i.e. they never reached the boot bring-up, so their zeroes are a coverage artifact. On the restored class the CM serving-system layer **does run** (see the next row). The *positive* half of the row — that the LTE layer is online-gated, not boot-gated — is untouched and stands | **NOT MET** (re-scoped; **the re-scope's negative is RETRACTED by Doc 210**) |
| Keep the device reachable while iterating on a firmware that can crash | AP never left in an unrecoverable reset loop | `modem-guard` reverts to stock HMU05 after > 3 bootloops and the device returns reachable | **MET** |
| Recover the unrecorded v15/v16 outcomes | a documented result for each | both recovered from archived captures; neither changed the boot outcome (Doc 198) | **MET** |
| Test GROUP 14 (the never-deployed v17 patch) | bring-up proceeds, mode changes | boot log unchanged; mode still `offline` (Doc 199 §3) | **NOT MET** |
| Name the stock-vs-UZ801 boot divergence | a specific event | ~~stock **never** enters `Prot_state 7(OFFLINE)`; UZ801 is driven there by a spurious `DUAL_STANDBY_CHGD` from the UZ801-only `mmocmmgsdi.c` (Doc 199 §4)~~ — **RETRACTED by Doc 200 §2/§5**: `mmocmmgsdi.c` is not UZ801-only (stock has 17 boot messages + the strings), and the command's writer is CM's dispatcher, identical in stock; the real divergence is the *first MMGSDI event's content* (`sess_id=0`/uninitialised session) | **RETRACTED** (superseded by Doc 200 §3/§4: the break is the `dcc`-task CM command dropped in 0.66 ms) |
| Re-derive the QMI→CM opmode mapping | the real table | the table at `0xc1ba7894` is **non-sequential**; QMI online → **5 = `CM_PH_OPRT_MODE_ONLINE`** — correct (Doc 200 §6.1, amends §6) | **MET** |
| Name the exact CM gate that drops `0x1000001` | a condition | ~~three, incl. the whitelist byte **`cm_state+0x39e8 == 3`** (Doc 200 §6.2)~~ — **CORRECTED by Doc 201 §3.2**: condition (A) `cmd+0x14 == 9` compares the command's **allocation uptime** to `9`, so it is effectively never true and the whitelist (B)(C) sits behind it as **dead code**; the one effective condition is `cmd->ctx->[0] == 2` | **MET** (re-derived) |
| Check whether v13's whitelist patch takes effect | a yes/no | **no** — the patch sets 3, its caller overwrites 1; **one-byte fix found** at `0xc067f4c0` (Doc 200 §6.3) | **MET** |
| Explain *why* v13/v19's `cm_state+0x39e8 = 3` patches were inert | a mechanism | **the whitelist is unreachable** — condition (A) `cmd+0x14 == 9` is a 1 Hz uptime compare, so no `0x1000001` command reaches (B)/(C) (Doc 201 §3.2) | **MET** |
| Answer Doc 200 §8 item 4 (who writes `cmd+0x14`, and is it `9`?) | a writer + a verdict | the CM allocator `FUN_c06951b0` stores `FUN_c093cc80()`; `u` in `=CM= CMD {alloc,free}` **is** `cmd+0x14` (proven from the free path's stack vararg); the value advances **1/second** ⇒ `==9` is a *moment* (Doc 201 §3.1) | **MET** |
| Deploy the condition-(A) inversion as v20 | a scored result | **WITHDRAWN BEFORE DEPLOYMENT** — the premise (`!=9` = drop) is wrong; `0xc067e994` is the `ctx->[0]==2` handler (Doc 201 §3.3) | **NOT MET** (by design) |
| Re-derive the QMI-mode → CM-opmode mapping | the real table | the table at `0xc1ba7894` is a **code switch table** whose cases set `r19`; QMI mode 0 → `r19 = 5` (ONLINE) — conclusion kept, mechanism corrected (Doc 201 §4) | **MET** |
| Name the stock online sequence and its timing | the chain + timing | `dcc` CM cmd (ts 3 039 772 312) → `PROT_GEN_CMD` ×3 → `OPRT_MODE_CHGD` → `ONLINE` → `Prot_state [MAIN] 5(ONLINE_GWL)` (ts 3 039 775 136) = **13.8 ms** (Doc 201 §5.1) | **MET** |
| Name the first stock-only CM event | a specific event | **`cmmsc_auto.c` `=CM= CMMSC_AUTO: updating op_mode`** at ts 375 844, right after the MMGSDI card events and right before MMOC `SUBSCRIPTION_CHGD` (Doc 201 §5.3) | **MET** |
| Check whether the stock-vs-UZ801 subsystem differential is mask-controlled | a yes/no | **RAISED then CLOSED — the differential is REAL.** A live 256-sweep boot capture added exactly one file (`rcinit_rex.c`) over the 64-sweep captures; 34 stock files still have no UZ801 counterpart (Doc 201 §6) | **MET** |
| Correct Doc 194 §8's "`u` is a command handle" | a correction | two allocs 4 ticks apart both carry `u=1`; Δu = Δts/204 800 (±1.5 %, n=8) ⇒ a **1 Hz counter** (Doc 201 §3.1) | **MET** |
| Characterise the v19 stability event seen live | a cause | the v19 boot ended **uncleanly** at ~2 280 s uptime (guard `consecutive_unproven=1`, empty pstore = PMIC PON WDT); the next boot's AP side is wedged (`bam_dmux RX watchdog: quiesced Ns, rx=held, ring disarmed`; QMI `dms` `CID allocation failed … endpoint hangup`) (Doc 201 §7.2) | **MET** (recorded, not root-caused) |
| Prove the live capture's F3 mask is adequate | a check | **FAILED** — 0 `mmocdbg.c` in the whole live 40 s vs 60 in the boot capture; the `mmocdbg` sub-evidence is withdrawn (Doc 200 §4 amendment) | **NOT MET** (confound declared) |
| Test whether forcing `cm_state+0x39e8 = 3` unblocks online | online, or a ruled-out cause | **v19 deployed + scored: still `offline`; boot capture unchanged, `cmmsc_auto.c` still 0** (Doc 200 §6.4) | **NOT MET** — hypothesis **FALSIFIED** |
| Keep the deploy path correct | every file installed, or a clean abort | newline-collapse bug found and fixed; install exit status now checked (Doc 199 §1) | **MET** |
| Preserve the offending patch for post-mortem | exact bytes + identity of the bad set | quarantined verbatim with md5 manifest and deploy label | **MET** |
| Decide whether the stock firmware auto-onlines at boot (`194` §7 vs `200` §3) | a yes/no | **`200` §3 is right; `194` §7 is RETRACTED.** The stock capture's own boot chunk is all `Prot_state 0(NULL)`; `5(ONLINE_GWL)` appears at emission index **915**, right after `=CM= CMD alloc u=17, tsk=dcc` (Doc 202 §4.1) | **MET** — `194` §7 retracted |
| Explain how `194` §7's "0.78 s / modem uptime 2.1 s" arose | a mechanism | `scratch/f3clean.py`'s ±40e6-tick window around the **median** `ts` **deletes the 324-message boot chunk and the 1 272-message online-transition cluster**, leaving only the post-online steady state, whose start was mistaken for the capture start (Doc 202 §3.2) | **MET** |
| Split the "25 stock-only subsystems" into boot-path vs online-gated | two sets | **boot-path:** `cmss.c`, `cmlog.c`, `cmregprx.c`, `sdss.c`, `sdcmd.c`, `cmmsc_auto.c`, `mmoc.c`, `policyman_serving_system.c`, `wmsmsg.c`. **online-gated:** `rflte_core_rxctl.c`, `rflte_mc_meas.c`, `pgi_msgr.c`, `mcpm_*`, `a2_ipfilter.c`, `trm_config_handler.c`, `tle_log.c`, `rfmeas_mc.c` (Doc 202 §4.2) | **MET** |
| Re-test whether the UZ801-vs-stock differential is mask-controlled | a yes/no | **the `201` §6 control was under-powered.** `mmocdbg.c` is **60** in six UZ801 captures and **0** in six others **of the same firmware** ⇒ per-capture **coverage** varies; a negative must be scored only on a capture that caught the boot burst (Doc 202 §5.1–§5.2) | **MET** — `201` §6's assurance **weakened** |
| ~~Re-score the UZ801 CM-layer negative under that control~~ | a valid negative | ~~on the six burst-caught captures, `cmss.c` / `cmlog.c` / `cmmsc_auto.c` / `cmregprx.c` / `sdss.c` / `sdcmd.c` / `mmoc.c` are **all still 0** ⇒ the CM serving-system layer genuinely never runs (Doc 202 §5.3)~~ — **★★ RETRACTED by Doc 210 §3.2/§4.2**: the six "burst-caught" captures carry **`rcinit_init.c` = 0** (they start *after* the boot bring-up), while the restored class carries **76–171**. Scored there, **`cmss.c` 2, `cmmsc_auto.c` 4, `cmregprx.c` 5, `sdss.c` 2, `sdcmd.c` 1, `mmoc.c` 6** — the CM serving-system layer **DOES run**; the zeroes were a **coverage artifact**, and the "strengthening" here strengthened nothing | **RETRACTED** (Doc 210) |
| Name the boot-path divergence precisely | a specific event | **the `u=1, tsk=cm, ftsk=cm` CM command.** Stock's completion path runs 50 records and contains **`cmmsc_auto.c` ×3** then MMOC **`Recvd command 0(SUBSCRIPTION_CHGD)`**; UZ801's runs **12** and contains **neither** (Doc 202 §6.1) | **MET** |
| Characterise the UZ801-only log modules | a list | `nvruim.c`, `mcfg_uim.c`, `estk_bip.c`, `gstk_proactive_cmd.c`, `gstklib.c`, `gstk_s_term_profile_rsp_wait.c`, `mmgsdi_session.c`, `policyman_uim.c` — a **UIM / SIM-Toolkit / BIP** stack the stock build never runs (Doc 202 §6.2) | **MET** |
| Re-check whether `FUN_c06928a8` (`cmmsc_auto`) has a caller | a yes/no | **still none** in the decompile; its `MSC_AUTO: …` string is its **early-return** path (so stock calls it **at least twice** at boot, and v14's callee patch could never fire on the first); it is reached through **external `d0…` service thunks** (Doc 202 §7) | **MET** (negative confirmed) |
| Prove `boot_sweep256.bin` really is a 256-sweep capture (Doc 203) | provenance | **PROVEN** — the live `diagboot.log` head carries exactly **256** `CNTL TX F3_MASK` packets, and the device mtime order settles it: `diagboot_run.sh` was restored to 64 at **12:05:34**, *after* the **12:00:09** boot that produced the capture | **MET** |
| Re-check `201` §6's "256 added exactly one file, `rcinit_rex.c`" (Doc 203) | the artifact | **FALSIFIED.** 64-sweep `boot_256_prep_64sweep.bin` (673 msgs/25 files) vs 256-sweep `boot_sweep256.bin` (670/24): the delta is **`nvruim.c` lost**; `rcinit_rex.c` is present in **both** | **NOT MET** — `201` §6 sentence retracted |
| Re-check `202` §6's "both firmwares issue the same `u=1, tsk=cm` command" (Doc 203) | holds | **FALSIFIED as stated.** `tsk` is **capture-class dependent**: burst class → first alloc `u=1, tsk=mmgsdi_1`; blind class → `u=1, tsk=cm` (then `mmgsdi_1` at `u=2`). `202` read it from the blind class | **NOT MET** — `202` §6.1 corrected |
| ★ Find the immediate precursor of `Prot_state 7(OFFLINE)` (Doc 203) | unknown | **MET (new).** A spurious `mmocmmgsdi_card_status_cb(evt=0, sess_id=0)` — a **zeroed MMGSDI event** — precedes `Recvd command 7(DUAL_STANDBY_CHGD)` and `Prot_state 7(OFFLINE)` in **every** burst-caught UZ801 capture. **Correlation 16/16**: `evt=0` is present in exactly the six burst-caught UZ801 captures and in **none** of the nine blind UZ801 captures or stock. Stock's first event is `evt=19` | **MET** |
| Make the capture instrument self-reporting (Doc 203) | a usable instrument | **MET** — `/root/diagboot_run.sh` now redirects the capture tool's stdout (it was flooding the log with **134 726 075 B** per boot) and writes `/root/diagboot.status` with `boot_id`, instrument md5, uptimes, sweep, `out_md5` and per-file discriminator counts. Deployed `d385dad0…` (backup `pre203.bak` = `72e3ba04…`), counters validated against the parser | **MET** |
| ★ Attack the `u=1` boot race (Doc 204) | a lever on the race | **MET, with a NEGATIVE verdict.** `scratch/race_classify.py` (delimiter-aware, emission-index ordered, no median-`ts` filter) reproduces the 16/16 class separation over the corpus and classifies **four fresh v19 reboots: all BLIND (`cm`-wins)**. The race is real and exactly separating — but it is **not the online gate** (§ next rows) | **MET** |
| ★★ Establish whether the race decides `online` (Doc 204 §5) | Doc 203 §4 called it "**not established**" | **MET — NO.** The `cm`-wins (BLIND) class is **`offline`** on a **measured same-boot pair** (`race_boot_4.bin` md5 `a7f55a97…` = the live `/root/diag_boot.bin` = the instrument's `out_md5`; same boot: `--dms-get-operating-mode` → `offline`, `--nas-get-serving-system` → `not-registered`). The `mmgsdi_1`-wins (BURST) class is `offline` by its own `Prot_state [MAIN] 7(OFFLINE)`. **Both race outcomes are `offline` ⇒ the race is not sufficient** | **MET** |
| ★★ Establish whether the race decides `evt=0` (Doc 204 §4) | Doc 203 §6.1's hypothesis | **MET — CONFIRMED.** Every `cm`-wins boot has no leading `evt=0`; every `mmgsdi_1`-wins boot does. The BLIND class's `mmocmmgsdi.c` stream is **healthy** (`Found session, sess_type=0, ss=0, session id=1036737951`, no `Opening session type`, no `WAIT_SESSION_OPEN_CNF`); BURST shows `evt=0` → `sess_type=0x7fffffff, ss=4` → stuck | **MET** |
| ★★★ Name the class-invariant online gate (Doc 204 §6) | unknown | **MET (new).** `cmmsc_auto.c` = `cmss.c` = **0 in all 22 UZ801 captures** (BURST **and** BLIND) vs **35 / 590** in stock. The gate does not move when the race flips — consistent with the race being non-causal for online | **MET** |
| Confirm the modem itself refuses online, not an MM artifact (Doc 204 §7) | a yes/no | **MET** — `AT` → `OK`; `AT+CFUN?` → **`+CFUN: 7`**; `AT+CPIN?` → **`+CPIN: READY`**; **`AT+CFUN=1` → `+CME ERROR: phone failure`**; `AT+CFUN?` still `7`. `--dms-set-operating-mode=online` reports **success** and the mode is **unchanged** (`offline`) | **MET** |
| Re-confirm the `dcc` break during a live online attempt (Doc 204 §7) | the Doc 200 §4 shape | **MET** — `scratch/online_attempt2.bin` (md5 `009b145c…`, = device `/root/online_attempt.bin`): `CMD alloc u=192, tsk=dcc` → `CMD free …, ftsk=cm` in **72 ticks = 0.35 ms** with nothing between (×2); no `tsk=ds`, no `PROT_GEN_CMD`, no `OPRT_MODE_CHGD`, no opmode log line at all | **MET** |
| ★★★ Explain why the `cmmsc_auto.c` / `mmocmmgsdi.c` format strings look **unreferenced** (Doc 205 §3) | a mechanism | **MET (new instrument).** The F3 strings live in a **separate string segment (seg25)** and are reached through an **8-byte-stride `{packed, strptr}` descriptor table in seg18**, sorted by `packed = (line<<16)|level`. The code never embeds the string address — which is exactly why a raw-u32 or `immext` scan returns **0 for strings the modem demonstrably emits** (`=CM= mode_pref %d, pref_term %d`, `=MMOC= mmocmmgsdi_card_status_cb`, `=CM= RAT_DISABLED_MASK: sys_mode %d`) while returning 2 for the one seg18 string (`=CM= CMD alloc u=%d, tsk=%s`) | **MET** — `200 §4`'s "instrument defect" framing and `201 §5.3`'s "no pointer-table reference" inference are **method artifacts** |
| ★ Derive and validate the Hexagon `immext` encoding (Doc 205 §4) | a formula | **MET.** `immword(A) = (A[31:28]<<24) | ((A>>20)&0xFF)<<16 | 0x4000 | ((A>>6)&0x3FFF)`, validated on six `llvm-objdump`-resolved constants. **Two guards are mandatory** (4-byte alignment + the next instruction's low 6 bits) — without them the scan false-positives (the first pass mis-located the `CMD alloc` string by 0x1000 through a VA→offset arithmetic slip) | **MET** |
| ★ Re-test whether `FUN_c06928a8` (`cmmsc_auto`) has a static caller, with a calibrated method (Doc 205 §7) | a yes/no | **MET — still none, and now trustworthy.** The scan returns the **four** intra-`FUN_c06928a8` references the decompile predicts (`FUN_c0691d9c` ×2 @ `0xc0691b6c`/`0xc0692958`, `FUN_c0692794` @ `0xc0692a1c`, `FUN_c069282c` @ `0xc0692a64`) and **0** for `FUN_c06928a8` itself. ⇒ the "the build is missing the caller" hypothesis is **DEAD**: the caller was never in the image for *either* firmware | **MET** (negative, method-validated) |
| ★★ Quantify the CM service layer's death over a long uptime (Doc 205 §5) | a count | **MET.** A live capture whose CM records reach **modem uptime `u=1584` (26.4 min)**: `cmlog.c` / `cmss.c` / `cmregprx.c` / `cmsoa.c` / `cmcall.c` / `cmcc.c` / `cmmsc_auto.c` / `mmoc.c` / `mmocdbg.c` / `mmocmmgsdi.c` / `sdss.c` / `sdcmd.c` = **0**, while `cmdbg.c` = 20 (alloc/free only) and `cmph.c` = 1. The **one** CM command that works is `CM_PH_CMD_GET_NETWORKS` (`AT+COPS=?` → `sys_mode 0`) ⇒ the dispatcher is alive and the drop is **case-specific** | **MET** |
| ★★★ Enumerate every op-mode lever the modem will accept (Doc 205 §6) | a table | **MET — none work.** QMI `set-operating-mode` = `low-power` / `persistent-low-power` / `reset` / `shutting-down` / `online` → **all report `success`, mode stays `offline`**; `AT+CFUN=0` and `AT+CFUN=4` → **`+CME ERROR: operation not supported`**; `AT+CFUN=1` → **`phone failure`**; `AT+COPS=0` / `AT+COPS=?` / `AT+CGATT=1` → **`ERROR`**; QMI NAS `set-system-selection-preference=lte` → **`QMI protocol error (3): 'Internal'`**. There is **no AP-side lever** | **MET** |
| ★★ Establish the UZ801 `cmmsc_auto.c` source revision (Doc 205 §8) | a diff | **MET (UNEXPECTED).** All 13 shared log lines match stock **except** a uniform **+9 line shift** from 2849/3830 upward, and the UZ801 build has **three log statements stock lacks** (`lte data call call:%d ss;%d` @5076, `Updating ss for call_id:%d to HYBR2` @5080, `… to MAIN` @5087). The +9 insertion is between source lines **2686 and 2849**. ⇒ UZ801's `cmmsc_auto.c` is a **later, larger** revision, **not** a stripped one | **MET** — kills the last "missing function" hypothesis |
| ★ Characterise a live `cntl-enable` capture's time window (Doc 205 §5.3) | a check | **NOT MET — new trap.** The 90 s `capture` produced records whose F3 `ts` spans **1 348.7 s** (`cfm_cpu_monitor.c`: 19 521 records, all distinct `ts`, ~50 ms spacing, 976 s span). `cntl-enable` **flushes the modem's buffered F3 history** ⇒ a live capture is valid for **presence** claims, invalid for **rate/order/timing** claims unless the flushed history is separated out (e.g. by the 1 Hz `u` field) | **NOT MET** (confound declared) |
| ★★★ Identify the source file of `FUN_c06928a8` / `FUN_c06917f0` (Doc 206 §3) | a file name | **MET — and it RETRACTS a corpus-wide misidentification.** Both are **`cmcc.c`**, not `cmmsc_auto.c`: (a) every log-descriptor `FUN_c06928a8` passes resolves to a `cmcc.c:` string (`=CM= MMGSDI EPS mmgsdi_status=%d` @UZ801 L3081, `=CM= MMGSDI EPS srv_available=%d, cm_mmgsdi_acl_availability=%d` @L3115); `FUN_c06917f0` passes `=CM= CC: app_type=%d` @L1266; (b) their own embedded symbols are `s_cmcc_service_available_cb` / `s_cmcc_call_control_processing_lte`. **⇒ `201 §5.3`, `202 §7`, `204 §6`, `205 §7` and ledger §8 all mislabel it** | **MET** (UNEXPECTED, retraction) |
| ★★★ Confirm the misidentification against stock runtime (Doc 206 §3.3) | a second method | **MET.** Stock **emits** those very formats at runtime — `=CM= MMGSDI EPS mmgsdi_status=%d` @stock **L3062**, `… srv_available=%d …` @stock **L3096** — while the UZ801 descriptors carry **3081 / 3115**, i.e. the identical statements under a uniform **+19** revision shift. The full `cmcc.c` differential is a clean staircase (+0 up to L1490, then +4 / +7 / +12 / +19) with one UZ801-only statement (`= Voip CC sys_mode = %d` @2535) | **MET** |
| ★★ Re-attribute the deployed v14/v19 GROUP 11 patch (Doc 206 §4.1) | the real target | **MET.** GROUP 11 patches VA **`0xc0691950`** — inside **`FUN_c06917f0`**, a **`cmcc.c`** function — under the label "cmmsc_auto.c mode gate bypass". Its stated rationale is **VOID**; it has never touched `cmmsc_auto.c`. (It is not thereby harmful — `cmcc.c` is *also* dead in UZ801 — but its inertness proves nothing about `cmmsc_auto`) | **MET** (re-attributed) |
| ★★★ Test whether "no static caller" implies non-invocation (Doc 206 §4.2) | a yes/no | **MET — NO, it does not.** Stock **runs `cmcc.c`** (9 records incl. the exact lines `FUN_c06928a8` emits) while that function has **no static caller in the stock image either** ⇒ on this platform a function can run with **zero** static references. **The inference of `201 §5.3` / `202 §7` / `205 §7` is FALSIFIED** (their measurement stands; their interpretation does not) | **MET** (inference falsified) |
| ★★ Execute Doc 205 §11 item 1 — the `0x456`/`0x422` cross-reference (Doc 206 §5) | a peer, or a negative | **MET (negative, CLOSED).** Neither ID appears elsewhere as a message ID (the other `0x456` hits are CRMBuilds `readConfigXmlData` config-XML item IDs and an F3 line number). `0x456` is one of a **computed family** `{0x11b,0x21b,0x41d,0x435,0x80c}` + `{0x456,0x422}` all registered with the **same handler** `FUN_c0691d9c` (the `cmcc_service_available_cb`) and selected by a **service handle** from `thunk_EXT_FUN_d001d2fc(sel,&h)`. **There is no peer to find statically — do not spend further sessions here** | **MET** (negative, closed) |
| ★★★ Bound the UZ801-vs-stock divergence on the INPUT side (Doc 206 §6) | a window | **MET (new, tight).** The **WMS/MMGSDI path is intact**: `wmssim.c L4322 wms_sim_mmgsdi_response_cb_proc` runs **14× in both** builds with an **identical `cnf` sequence** (51,26,26,26,0,0,0,0,2,0,2,2,2,0) and identical `slot`; `wms.c` **puts and processes** 625/626/627/628/629/631 in both (nothing put-but-unprocessed). `wmssim.c` 16/16, `wmsmsg.c` 3/3, `qmi_nas_mmgsdi.c` 1/1 ⇒ the CM-layer zeros are **not** a capture-length artifact. **The break is strictly inside the CM layer** | **MET** |
| ★ Retire the literal-reference census as a code-presence test (Doc 206 §7.1) | a check | **MET — it cannot decide presence.** `wmssim.c` emits **14 records** at runtime with **0** literal `immext` references, exactly like `cmmsc_auto.c` (0 refs in **both** builds, yet stock emits 35 records). ⇒ a zero carries **no** information. Also: the descriptor table is **not globally sorted** (runs reset at `0xc165c400` 4054→408 and inside the `cmmsc_auto` block 5087→3250); and `scratch/desc.py`'s `{u32;u16;u16}` model is **WRONG** — superseded by `scratch/desc_table.py` (Doc 206 §7.2/§7.3) | **NOT MET** (method retired) |
| ★★★ Correct the F3 descriptor model (Doc 207 §3) | the real layout | **MET — Doc 205 §3 was half right and the omission mattered.** `word1` is a seg25 **string pointer for only ~8 % of sites**; for ~92 % it is a **32-bit hash** (`< 0xc0000000`). `desc_table.py entries` filters on `word1 ∈ seg25` ⇒ **it silently keeps only ~8 % of the table**; every count taken from it is a lower bound of unknown size. Measured on the `cmcc.c` block: `0xc165c398` (L3027) → hash `0xa18d6925`; `0xc165c3b8` (L3081) → strptr `0xc4561700` `"cmcc.c:=CM= MMGSDI EPS mmgsdi_status=%d"` | **MET** (model corrected) |
| ★★ Fix `desc_table.py`'s crash-to-empty-output (Doc 207 §3.2) | a bug | **MET.** `entries`/`cmd_file` append `off2va(segs, j)` unguarded; on a file gap it is `None` and `"0x%08x" % None` raises `TypeError`. Under the usual `2>/dev/null` the script dies and the output is **empty = "no entries"**. Reproduced: a wide range (`0xc4558000..0xc4614cec`) prints **0**, a narrow range inside it prints **149**. **Any earlier wide-`entries` count taken with stderr suppressed must be re-taken** | **MET** (bug fixed in `descref.py`) |
| ★★★ Establish a build-independent cross-build statement key (Doc 207 §3.3) | a measurement | **MET.** With a tight level filter, UZ801 has **11 200** hash keys and HMU05 **12 721**, of which **10 039 (89.6 %)** are common ⇒ a descriptor hash identifies the *same log statement* in both images. `xmap.py`'s documented `{u16 file_id; u16 msg_id; u32 hash}` model is a **mislabelling of `{packed, hash}`**; its purpose is sound | **MET** |
| ★★★ Build a descriptor → `file:line` instrument (Doc 207 §4) | a tool | **MET.** Log sites pass the descriptor as a **bare literal** (`FUN_c091d940(0xc165c398,…)`), so `scratch/logsite.py` reads `packed` from the ELF at each literal VA and attributes the line to a file via the resolved anchors + **table adjacency**. **UZ801 69 775 sites; HMU05 68 186 sites.** Supporting: `scratch/immbase.py`, `scratch/va2func.py` (81 800 UZ801 functions indexed) | **MET** |
| ★★ Re-answer Doc 206 §9 item 2 — is `cmcc.c` reachable before GROUP 11 can matter? (Doc 207 §5) | a yes/no | **MET (negative, CLOSED).** `cmcc.c` is reachable in **both** builds **identically**: UZ801 **13 functions / 90 sites** vs HMU05 **12 / 89**, with a **1:1 site-count correspondence** (`FUN_c06917f0`↔`FUN_c066e9a4` 28/28, `FUN_c06928a8`↔`FUN_c066fa30` 13/13, …). The **exact** (resolved-string) `cmcc.c` line sets independently reproduce Doc 206's revision staircase — stock `264 1078 1082 1088 1266 1329 1490 1616 1813 1816 2313 2341 2839 3062 3096` vs UZ801 `264 1078 1082 1088 1266 1329 1490 1620 1820 1823 2325 2353 2535 2858 3081 3115` ⇒ shifts `+0 ×7, +4, +7, +7, +12, +12, UZ801-only 2535, +19, +19, +19`. And the sole entry `FUN_c06928a8` is referenced **only at its own definition** in UZ801 — **and so is stock's `FUN_c066fa30`**. ⇒ the no-static-caller property is **not a build difference** and cannot explain the missing `cmmsc_auto` call | **MET** (negative, axis closed) |
| ★★★ Decide whether GROUP 11 can ever change the outcome (Doc 207 §6) | a verdict | **MET — it cannot, by construction.** UZ801 `FUN_c06928a8` and stock `FUN_c066fa30` are **structurally identical**: same guard, same `0xff` alloc, same field offsets (`0x29c`, `0xa68`, `0x1509/0x150a/0x150f`), same registration IDs (`0x456`, `0x422`), same single tail call (`0xffffffff`), same cleanup — differing only in layout and the **+19** revision shift. GROUP 11 patches `0xc0691950` *inside* that identical chain ⇒ **inert by construction**, which is exactly what v14/v19 measured. **Stop deploying GROUP 11** | **MET** (axis closed) |
| ★ Bound Doc 206 §9 item 1 — locate the `cmmsc_auto.c` entry point (Doc 207 §7.2) | a function | **NOT MET, route bounded.** The `cmmsc_auto.c` descriptors are referenced by **no literal and no `immext` in *either* build** (0 of 69 775 / 0 of 68 186 sites); `immbase` over `0xc165fd40..0xc165ff40` (UZ801) and `0xc155f700..0xc155f900` (stock) finds **zero** bases. All descriptor-table `immext` bases from the CM cluster are in the **`cmcc.c` region only**. The four descriptor VAs are recorded for both builds in Doc 207 §7.2 so the next attempt starts from them | **NOT MET** (bounded) |
| ★★★ Measure whether the CM dispatcher is the same code (Doc 208 §3) | a verdict | **MET — it is identical.** UZ801 `FUN_c067cd2c` @ `0xc067cd2c` vs stock `FUN_c0659ef0` @ `0xc0659ef0` (located by grepping the stock decompile for the switch constant `0x100006a`, **not** by an address guess): 1096 normalised lines each, raw `fdiff` similarity **83.49 %** with 115 differing blocks — but **every block is a `unaff_GP + 0x…` global-layout offset**. After normalising GP offsets, **2 blocks remain, both the same `int *piVar;` declaration move**. ⇒ "the CM code differs" is **closed**; the `dcc` command's 0.66 ms drop must be command-content or CM-state, i.e. a runtime question | **MET** |
| ★★★ Establish that the stock and UZ801 F3 arms used the SAME mask (Doc 208 §5) | a yes/no | **MET — identical instrument.** `scratch/hmu05_stock_boot.log` shows `--- cntl-enable 64 SSIDs at uptime=12.98 ---` and 64 `F3_MASK` packets `ssid_first=ssid_last=0…63`, from the same `/root/diagboot_run.sh` at the same uptime as the UZ801 arm. ⇒ **every** stock-vs-UZ801 file-set differential in this campaign is mask-controlled, and Doc 203 §3.1's load-bearing negative is admissible | **MET** |
| ★★★ Correct the meaning of the F3 record's `code` field (Doc 208 §4) | a semantics | **MET — it is NOT the AP-selected SSID.** `cntl-enable N` provably sweeps SSIDs `0..N-1` (source + wire verified), yet on one boot `code` is **4772 for N = 1, 4, 64 and 256**. It changes only across **boots** (`4756, 4764, 4765, 4766, 4769, 4771, 4772, 40240, 41261, 42100, 42140, 0`) ⇒ a **modem-assigned per-boot subsystem id**. **Rule: read a capture's mask from its `diagboot.log`, never from the F3 record** | **MET** |
| ★★★ Re-confirm the load-bearing negative under a fixed mask (Doc 208 §5.1) | a verdict | **MET — strengthened.** Same mask, same instrument: stock's **boot chunk** (ts < 600 000) carries `cmss.c` **18**, `cmmsc_auto.c` **3**, `mmoc.c` **4**, `cmlog.c` **6**, `cmregprx.c` **7**, `sdss.c` **6**, `sdcmd.c` **5**, `policyman_serving_system.c` **1**; UZ801's burst boot chunk carries **0** of them while delivering the *same* adjacent layers (`mmocdbg.c` 60, `cmdbg.c` 14, `mmocmmgsdi.c` 62, `a2_power.c` 8). The mask cannot be the explanation | **MET** |
| ★★★ Restore the F3 burst-capture class (Doc 208 §6) | a working instrument | **~~NOT MET — NEW BLOCKER.~~ → MET 2026-09-27 by Doc 210 §3.1.** At the time: **20 consecutive boots** were in Doc 203's **blind** class (`mmocdbg.c` = 0), across **three masks** (sweep-256, one-packet-range-256, sweep-64) and **a fresh redeploy of the identical v19 set**; falsified as causes were the sweep width (§6.1, the sweep-64 control blinded 4/4 too), the enable *speed* (the new one-packet range tool blinded 5/5), the firmware bytes (§6.2), and the modem's persistent partitions (`modemst1`/`modemst2`/`fsg`/`fsc` byte-identical across a boot, §6.3); the class flipped in **blocks** (5 burst, then 20 blind; p ≈ 10⁻⁶ for a per-boot coin flip) with no deploy/revert/reset near the flip. **★ The cause was found and the class RESTORED:** the instrument's DIAG-device poll used `sleep 1`, delaying `cntl-enable` to **~13.18 s**; with `sleep 0.05` it fires at **12.3–13.1 s** and the modem's cold boot is caught — **verified 4/4** (vs the campaign's 20/20 blind). **The class is a race on the `cntl-enable` time, not on the sweep width.** Deployed as `diagboot_run.sh` `59fb8e77961175ca4addd4161cdb63eb` / `diag_bind.sh` `4a13dfb86f9ea60e38b98a6883c9eb9e` (backups `*.pre209`) | **MET** (Doc 210) |
| ★ New instrument: `cntl-enable-range` (Doc 208 §7) | a tool | **MET.** `diag_logtool cntl-enable-range [dev] [nssid] [span]` sends **one `F3_MASK` packet per `span` SSIDs**; `span == 1` reproduces the original `ssid s..s` packet byte-for-byte. `span = 256` enables `0..255` in one packet (`ssid_first=0x0000 last=0x00ff`), md5 `470df474ed9425aa2b62386a8ef0eab4`, built with the repo's OpenWrt toolchain. It does **not** by itself restore the burst class | **MET** |
| ★★★ Establish whether the campaign's "burst" captures covered the boot (Doc 210 §3.2) | a yes/no | **NO.** `rcinit_init.c` = **0** in all four campaign captures checked (`v19_boot.bin`, `diag_boot_v16.bin`, `diag_boot_v13.bin`, `post_v18_group14_boot.bin`) vs **76–171** in the two restored-class captures (`boot_early_try1.bin`, `boot_210_canon.bin`). The campaign's class caught the *middle* of the boot; the restored class catches it **from `rcinit` group 4**. The `f3clean.py` filter was checked and is **not** the cause (it drops 101 of 1 887 messages, none load-bearing) | **MET** |
| ★★★ Test Doc 200 §4(b)'s "`cmmsc_auto` never runs" (Doc 210 §4.3) | a yes/no | **REFUTED.** On the restored class UZ801 emits the **exact stock sequence** at idx 699–701: `=CM= MSC_AUTO: 0x200 & 0xebe = 0x200`; `=CM= CMMSC_AUTO:is_ue_mode_csfb=0 hybr_pref 1 …`; `=CM= CMMSC_AUTO: updating op_mode, cdma sub 0 hybr1_allowed 1 hybr2_allowed 0` — including the very statement (`updating op_mode`) Doc 201 §5.3 named as stock-only. **Doc 200 §4(b)'s *timing* evidence (`dcc` 0.35–0.66 ms vs 11.5–13.8 ms) is untouched** | **MET** (refutation) |
| ★★★ Name the UZ801 boot's policyman end state (Doc 210 §5) | a value | **MET (new lead).** `policyman_rat_capability.c:726 subs 0/1/2: policyman_retrieve_rats_bands returned status 2, filesize 0` → `:98 can't populate RAT item from EFS` → `policyman_rf.c:592 can't populate RF item` → `policyman_ue_mode.c:122 can't populate UE mode Item` → **`policyman_rat_capability.c:74 Filtered RAT mask 0 based on HW capabilities 544`**. **A modem with RAT mask 0 has nothing to scan and nothing to register on.** The write-back fails too (`policyman_efs.c:334` + `efs rat & band write status = 2`), so it is **not self-healing on a later boot** | **MET** (new lead) |
| ★★ Prove the EFS is neither read-only nor unwritable (Doc 210 §5.3/§6) | a control | **MET.** (a) `qmicli --wds-create-profile="3gpp,name=efstest,apn=efstest.apn"` → **`New profile created: Profile index: '3'`** and it **persisted** in a later `--wds-get-profile-list=3gpp`; (b) NAS reads EFS fine (`file_enum 220, status:0`; `:PLMNWACT, read_data_len169,size:5`; populated `plmn(0..15), mcc:405, mnc8xx`). ⇒ **any explanation requiring a globally dead or read-only EFS is FALSIFIED**; the policyman failure is **item-specific** | **MET** |
| ~~★★ Name the census-invisible CM divergence (Doc 210 §8)~~ | a specific value | ~~`cmmsc_auto.c`'s first statement's second operand is **`0x220` on stock** and **`0xebe` on UZ801**; `0x220` = 544 = exactly UZ801's policyman HW-capability value~~ — **★★ RETRACTED by Doc 211 §3.3**: stock emits **`0xebe` first and `0x220` later in the same boot** (`stock_boot_240.bin`, `stock_boot_211.bin`); the archived online capture caught only `0x220` and the UZ801 captures only `0xebe`. It is **which invocation the capture happened to catch**, not a build difference | **RETRACTED** (Doc 211) |
| Decide whether the policyman failure is THE differentiator (Doc 210 §5.5) | a verdict | **DECIDED — NO.** Doc 211 §4.4 took the stock boot capture on the restored instrument (`stock_boot_211.bin`, `admissible=yes`, `rcinit_init.c` = 76) and stock emits the **identical** policyman sequence — `:726 … returned status 2, filesize 0` → `:98 can't populate RAT item from EFS` → `policyman_rf.c:592` → `policyman_ue_mode.c:122` → **`:74 Filtered RAT mask 0 based on HW capabilities 544`** → `policyman_efs.c:334` → `:907 efs rat & band write status = 2` — **and still reaches `online` and registers on JIO 4G**. A filtered RAT mask of 0 is **not** why UZ801 stays offline. Doc 210 §5's lead is **FALSIFIED** | **MET** (as a negative) |
| ★★★ Fix the F3 `args`-shift parser defect (Doc 211 §2) | a correct parser | **MET.** `f3parse.py` read `args` at `k+20+4*j` while the Doc-210-era `extra` pad sits **before** the args, so every affected record's args were scaled by **256^extra**. Raw proof at `stock_boot_211.bin off=0x30d33` (`na=2, extra=1`): `args@k+20 = [9728, 0]` vs `args@k+20+1 = [38, 0]` for `=CM= mode_pref %d, pref_term %d`. Corroborated on three independent lines (`mode_pref` extra=2 → 2490368 vs 38; `old_op_mode/new_op_mode` → 2560/2816 vs **10/11**; `NV hybr pref` → 256 vs **1**). Fixed: args at `k+20+extra+4*j`; `f3parse.py` md5 **`9f9d6edf15d64faf3b47897f7838c9ac`**, pre-fix kept as `f3parse.py.pre211` (`59ccb3db69d084eae415dd940d7cdf1a`). ~2 % of records affected, but **every stock `cmph.c mode_pref` record** | **MET** |
| ★★★ Kill the "`mode_pref` 9728 vs 38" differential (Doc 211 §3.1) | a verdict | **MET — no differential.** With the fix, `=CM= mode_pref %d, pref_term %d` is **`[38, 0]` in every arm** (stock 240 s, stock 90 s, archived online, UZ801 canon, UZ801 v19). Stock's own `cmsoa.c main mode_pref=%d` = **38** and `HICPS cm_mode_pref: %d, ph obj mode_pref: %d, ph_ptr mode_pref: %d` = **`[39, 38, 38]`** (both `extra = 0`) independently confirm it | **MET** (negative) |
| ★★★ Show every CM-layer numeric is identical (Doc 211 §3.2) | a verdict | **MET.** Same instrument, same 240 s window: `mode_pref` 38 = 38; `old_op_mode → new_op_mode` **10 → 11** = 10 → 11; `NV hybr pref` 1 = 1; `MSC_AUTO` `0x200 & 0xebe = 0x200` = same; `Filtered RAT mask` `[0, 544]` = `[0, 544]`; `RAT_DISABLED_MASK` `[9,0,0]`/`[4,0,0]` = same. **The CM *inputs* are byte-identical; the divergence is entirely downstream** | **MET** |
| ★★★ Obtain the stock `online` control the campaign never had (Doc 211 §4) | a control arm | **MET.** Stock restored (`modem.mdt 1a6f9507…`, `wcnss.mdt 62bb56b2…`, `cmnlib.mdt 78752167…`, `mcfg_sw.mbn 0be0d361…` all matched) and booted: `--dms-get-revision` = `HIMI_U01_MODEM_V1.0`, `Mode: 'online'`, `Registration state: 'registered'`, `MCC 405 / MNC 861 / 'JIO 4G'`, `technology pref '3gpp, lte' permanent`. **Same hardware, same SIM, same EFS, same AP** | **MET** |
| ★★★ Take the first same-window stock-vs-UZ801 differential (Doc 211 §7) | a differential | **MET.** Same instrument, same 240 s window, only the firmware differs: stock `cmss.c` **18** / `cmlog.c` **6** vs UZ801 **0 / 0**; SD `sdcmd_name_sel3()` vs `hybr_name_sel()` + **`ssscr_user_offline_cdma`**; MMOC `sess opn cnf: …card=%` vs **`…card_1=%` + `card_2 %`**; UZ801 runs a whole **MCFG-UIM/STK** stack stock lacks (`mcfg_uim.c` ×9, `uimsub_manager.c` ×5, `uimgen.c`, `gstkutil.c` ×3) including a **failing** `UIM_%d: Read to NV active slot configuration unsuccessful`; MMOC `Prot_state` `0(NULL)` only vs `0(NULL)` → **`7(OFFLINE)`** | **MET** |
| ★★★ Name the MMOC decision differential (Doc 211 §8) | a specific event | **MET.** Delivery order, `num_args = 0` (so untouched by the parser defect): both receive `=MMOC= Recvd command 2(OPRT_MODE_CHGD)` and both log `Curr_trans 0(NULL)` / `Trans_state 0(NULL)` / the two `Prot_state … 0(NULL)` lines; stock then emits **`=MMOC= New transaction : 2(ONLINE)`** → `Trans_state 6(WAIT_PH_STAT_CNF)`, UZ801 emits **`=MMOC= New transaction : 3(OFFLINE)`** → `Trans_state 25(WAIT_DEACTD_CNF_GWL)`. The command is the same and CM's op mode is byte-identical, so **the decision is made inside MMOC** on inputs the CM log does not expose | **MET** |
| ★ Restore UZ801 v19 and verify (Doc 211 §6) | a deployed set | **MET.** `restore_uz801_v19.sh` (md5 `51965239169cd8c048632f8678fef2d8`) → **68/68 manifest match, 0 extra files**; `modem.mdt 9f39ce579114bcf1383bbdce62a8d284`, `modem.b16 c71d53464a1e88f2072cbbb6f7f87475`, `wcnss.mdt 3d028c77…`, `cmnlib.mdt dec6fdd2…`, `mcfg_sw.mbn 830c13a6…`; `--dms-get-revision` = `UZ801_V3.0_21_V01R01B10`; `Mode: 'offline'`; guard `boot_count` 0, no `BOOTLOOP_REVERT.txt`; fresh `admissible=yes` 240 s capture (`rcinit_init.c` = 110) | **MET** |
| ★ Record the `comm` deploy trap (Doc 211 §6.1) | a hazard | **MET.** `comm` is **not in the OpenWrt busybox**; the first `restore_uz801_v19.sh` aborted **after** the tar extraction but **before** the extra-file removal, leaving a **partial** firmware set. Replaced with `awk 'NR==FNR{a[$0];next} !($0 in a)'`. Same class as Doc 197's `$FILES` newline bug | **MET** |
| Not make the ~900 s fatal worse | no new fatal mechanism introduced by the port | unchanged — and reaching `online` would *arm* the LTE-gated clock (Doc 194 §11). Doc 208 ran **20 clean reboots** (guard `boot_count` 0, no revert) and the modem stayed `offline` throughout, so the clock was never armed; Doc 210's reboots likewise kept it `offline` | **MET / see §6** |
| ★ Correct Doc 214 §5.5's "no reference to the MMOC strings exists" (Doc 215 §3.2) | a correction | **CORRECTED.** The verdict was true **only in the ELF link space**. In the firmware's **native** space the identical search finds **5 hits in the ELF and 7 in the coredump** (`mmocdbg.c`, `OPRT_MODE_CHGD`, `SUBSCRIPTION_CHGD`, `=MMOC= %s`, `ONLINE_GWL`), all in **file-backed** seg[18]/seg[19] | **MET** |
| ★ Solve the firmware's native address space (Doc 215 §3.1) | a constant | **SOLVED, not assumed: `native VA = ELF VA + 0xbeb1000`**, fitted to the 14-entry MMOC protocol-state name table at ELF `0xc4ec67e8` and verified **13/14** against real NUL-terminated strings (`MC(CDMA-online)` … `GPS-MSBASED`) | **MET** |
| ★★ Recover the complete MMOC command/state name table (Doc 215 §4) | the table | **MET.** `mmoc_cmd_name[]` at **ELF VA `0xc1d33eb0`** (file-backed **seg[19]**, native `0xcdbe4eb0`): **96 slots, 90 named**. Index→name matches every observed F3 ordinal (`0 SUBSCRIPTION_CHGD`, `1 PROT_GEN_CMD`, `2 OPRT_MODE_CHGD`, `4 PROT_REDIR_IND`, `7 DUAL_STANDBY_CHGD`, `83 ONLINE_GWL`, `65/85 OFFLINE`) ⇒ any MMOC ordinal is decodable offline | **MET** |
| ★ Recover the `=MMOC= %s` F3 descriptor (Doc 215 §5) | the record | **MET.** ELF VA `0xc1566780` (file-backed seg[18]): `{line=291, level=20, len=28, fmt→native 0xd0d77c9e, file→native 0xd0d77ca8}`; `line=291` is the **stock** `mmocdbg.c` line. Confirms `logsite.py`'s "~8 % string pointer / 92 % hash" model, now with a mechanism | **MET** |
| Test Doc 214 §12 step 1 (rebuild the ELF with seg26 content) as the fix (Doc 215 §6) | the fix | **FALSIFIED.** The blocker is the **address-space mismatch**, not missing bytes: a `0xd0d77cxx` reference cannot be resolved by adding content at `0xc4ec6cxx`. Superseded by "add a Ghidra memory block at the native VAs, or set the PC-relative base" | **NOT MET** (superseded) |
| ★ Find the `OPRT_MODE_CHGD` emitter (Doc 215 §7) | the emitter | **NOT ACHIEVED — stated as a limit.** `llvm-objdump` shows a **PC-relative base** (`r24 = pc` … `r25 = sub(r24,r26)`) + `immext` constants; Ghidra renders it `unaff_GP` with small clustered offsets (9 797 distinct in `0x10…0x12814`). **No instruction references the name table or its page by any absolute constant (0 hits)**, so the emitter needs the base resolved first | **NOT MET** |
| Solve the **UZ801** native constant (Doc 215 §9 step 2) | a constant | ~~**NOT DONE — and DOWNGRADED by Doc 216 §5.** `0xbeb1000` is verified on the **stock** build only; UZ801 links seg26 at `0xc4615000` (`memsz 11448320`). Doc 216 §5 shows the pointer *targets* are unreadable offline, so the constant **cannot** be solved without a **UZ801 coredump**~~ — **★★ SOLVED 2026-09-27 by Doc 217 §6: the coredump was obtained and `native = ELF VA + 0x0beb1000` is content-verified on UZ801 (all 21 segments).** | **MET** (Doc 217) |
| ★ Test the Doc 215 §13.3/§13.4 CM→seg19 adjacency lead (Doc 216 §4) | a CM→MMOC handle | **FALSIFIED — it is a segment-proximity artefact.** All nine enclosing functions are **CM task-scheduler** code (`FUN_c068a77c` task main loop, `FUN_c068a688` signal helper, `FUN_c0682aa0` queue push, `FUN_c0682224` 2-deep history, `FUN_c067df9c` list-head init) and their targets are **CM scheduler globals sharing seg[19]** (3.1 MB). Doc 215 §13.5's "concrete next step" is **withdrawn** | **MET (as a falsification)** |
| ★★ Recover the MMOC **protocol-name** table (Doc 216 §3) | a table | **MET.** `0xc1d33e08`–`0xc1d33eb0` (file `0x1ca0e08`, file-backed seg[19]), **14 × 12-byte `{char* name, void* X, u32=0}`**, ending exactly where `mmoc_cmd_name[]` begins. **10 distinct names over 14 slots** (`NAS(REG Task)` at `5,6,7,9,10`): `MODE_INACT`, `MC(AMPS)`, `CDMA(MC Task)`, `GPS`, `HDR(HDRMC Task)`, `NAS(REG Task)`, `RESERVED`, `HYBR NAS(REG Task)`, `HYBR 3(REG Task)`, `Invalid`. **The indexing and `X` are NOT established** | **MET (with stated limits)** |
| ★ Establish where seg[26] content comes from (Doc 216 §5) | a file | **DEFINITIVE NEGATIVE** (as to the firmware artifacts). Both builds have a `bNN` for **every** `filesz>0` phdr and **no** file for any `filesz=0` phdr; **`modem.b26` does not exist**, and the four distinctive strings are absent from **every** file including both 64 MB FAT16 images. seg[26] is a **fixed-address runtime-populated region** ⇒ **Doc 214 §12 step 1 / task #15 CLOSED as unimplementable.** **⚠ SUPERSEDED IN PART 2026-09-27 (Doc 217 §7): the negative is true of the *firmware artifacts*, but the coredump IS the file — the UZ801 coredump's phdr 20 captures seg[26] verbatim (11 448 320 B), so seg[26] is now readable** | **MET** (as a definitive negative; superseded by Doc 217) |
| ★ Correct the F3 descriptor model (Doc 216 §6) | the layout | **MET.** `=MMOC= %s` @ `0xc1566780` is a **16-byte** `{u16 line, u8 level, u8 len, u32, u32 fmt_native, u32 file_native}`. **Measured 4-byte-LE pointer occurrences:** `=MMOC= %s` **1**, `mmocdbg.c` **1**, and **0** for `Recvd command`/`Recvd report`/`Trans_state`/`Curr_trans`/both `Prot_state [MAIN]` ⇒ those use the **hash** form and **Doc 215 §9 step 1's "xref the `Recvd command` format string" cannot work as written** | **MET (refines Doc 215 §5)** |
| ★ Diff the two builds' seg[19] tables (Doc 216 §7) | a diff | ~~**PARTIAL.** Layouts are **near-identical** (8 structural candidates at matching relative offsets, shifted only `0x400`); the `mmoc_cmd_name[]` relative offset does **not** transfer, so UZ801's table must be found by signature. **A name-level diff is deferred** (no UZ801 coredump)~~ — **★★ DONE 2026-09-27 by Doc 217 §9: the name-level diff is COMPLETE (72/75 present, 3 absent) via the UZ801 + stock coredumps.** The seg[19] layout observation stands | **MET** (Doc 217) |
| ★ Close the SIM-lock question (Doc 216 §9) | a verdict | **MET — NEGATIVE.** `AT+CLCK="FD"/"SC"/"PN"/"PU"/"PP"/"PC",2` → **all `0`**; `AT+CPIN?` → **`READY`**; QMI user-lock → **`no`**. MM's `lock: sim-pin2` / `enabled locks: fixed-dialing` are **MM misreports**; PIN2 `enabled-not-verified` **does not gate registration** | **MET** |
| ★★★ Obtain a UZ801 coredump (Doc 217 §3) | the artifact | **MET.** 84 407 190 B, md5 **`59855a4cfac73e7dd2a4b54032ff5c42`**, ELF32 `ET_CORE`/`EM_NONE`, **21 phdrs**, at `scratch/coredump_uz801/uz801_coredump.elf` (device copy `/root/uz801_coredump.elf`). Captured on the **second** attempt; the first attempt hung the AP and produced nothing (next row). Every segment matches the UZ801 ELF phdr-for-phdr, including the three `filesz = 0` regions | **MET** |
| ★★★ Explain attempt 1's AP hang and its exact block point (Doc 217 §4) | a mechanism | **MET.** Fired with `qcom_bam_dmux` loaded the AP hung (new `boot_id` `26d74096…` → `716548d1…`, PMIC PON WDT) and **no coredump was ever created**; `console-ramoops-0` ends at `[8858.706597] … executing serialized asynchronous SSR teardown` = **between `T4 state_lock acquired` and `T5 rx released` in `bam_dmux_power_off()`** (`qcom_bam_dmux.c:1578`; `T5` `:1623`, `T7` `:1936`; neither appeared). The idempotent fast-return (`:1585`) cannot fire because telemetry says `rx=held`. It propagates because **patch 821's synchronous `flush_work(&dmux->ssr_teardown_work)` (`:2510`, `T9 flush returned` `:2511`) sits in the SSR subdevice's `stop` callback** → `rproc_stop_subdevices()` → `rproc_stop()` → `rproc_boot_recovery()` (`remoteproc_core.c:1792`), **upstream of `rproc->ops->coredump()` (`:1803`)** ⇒ a teardown block means the dump is never generated. This is a **fourth AP-hang site**, with a named propagation mechanism | **MET** |
| ★★★ Prove the hang is the `bam_dmux` SSR teardown (Doc 217 §5) | an A/B | **MET.** Clean A/B on the same device: module **loaded** → hang + no dump; **unloaded** → dump + AP alive (`boot_id` unchanged, `state_after: running`). The block is real and module-specific | **MET** |
| ★★ Retire the recorded `rmmod` hazard (Doc 217 §5) | a correction | **MET — hazard WITHDRAWN.** `rmmod qcom_bam_dmux` succeeds (refcount 0, all `wwan` down) and its own `bam_dmux_power_off()` runs the *same* function to completion in **~6 ms** (`T5 rx released` → `T6 tx released` → `T7 power_off returned`, `rmmod_rc=0`, `boot_id` unchanged, uptime 490.42→490.57 s) ⇒ the block is **specific to the SSR/notifier context**, not intrinsic. The recorded hazard "`bam_dmux_remove()` … will hang a future `rmmod`" is **withdrawn** — the ABBA it described was the *synchronous* watchdog cancel, now **non-blocking** at `:1609` | **MET** (hazard withdrawn) |
| ★★ Solve the UZ801 native constant (Doc 217 §6) | a constant | **MET — content-verified.** `native = ELF VA + 0x0beb1000` (= coredump VA + `0x456b1000`); `ELF VA = coredump VA + 0x39800000` on **all 21 segments**. Identical to stock — a property of the MPSS runtime map, not of the firmware. **Supersedes task #18's `NOT MET` / Doc 216 §5's "cannot be solved without a coredump"** | **MET** |
| ★★★ Reach seg[26] (Doc 217 §7) | the region | **MET.** coredump phdr 20: `p_vaddr 0x8ae15000`, `p_offset 0x04594396`, `filesz = memsz = 0x00aeb000` (**11 448 320 B**; ELF VA `0xc4615000`). Every coredump `p_vaddr` = the ELF's `p_paddr`; sizes are `memsz`, so the three `filesz=0` segments appear (phdr 15 = seg[20], phdr 16 = seg[21], phdr 20 = **seg[26]**). **Doc 216 §5's "seg[26] content exists in NO file" is superseded** — that was true of the firmware artifacts; the coredump *is* the file. seg[26] holds the whole MMOC string pool + the UZ801 source paths | **MET** |
| ★★★ Name-level table diff, stock vs UZ801 (Doc 217 §9) | a diff | **MET.** Stock tables were read from the **stock coredump** (`scratch/coredump_live/modem_coredump_up919.52.elf`), because the stock `mmoc_cmd_name[]` pointers land in the stock's `filesz=0` seg[26]. Result: **72 of 75** stock command names present in UZ801, **3 absent** — **`IMS_DEREG_CNF`, `PS_DETACH_ENTER`, `WAIT_IMS_DEREG_CNF`** (an IMS-dereg / PS-detach reduction; `PS_DETACH_CNF` survives while `PS_DETACH_ENTER` does not ⇒ a *state* was dropped). **All 10 protocol names present in both with identical exact-occurrence counts** (`GPS` 9/9, `Invalid` 3/3, rest 1/1). `SUBSCRIPTION_CHGD` **is** present (`cd 0x8b7f8508`) ⇒ the recorded `0(SUBSCRIPTION_CHGD)` vs `7(DUAL_STANDBY_CHGD)` divergence is **not** a missing name and the Doc 202 §6.3 chain stands. `WAIT_SESSION_OPEN_CNF` present in **both** | **MET** |
| ★★★ Locate the UZ801 `mmoc_cmd_name[]` / protocol tables (Doc 217 §8.1) | the tables | **NOT MET — NEW PRIORITY #1.** The MMOC string pool **is** readable (`cd 0x8b7f8230`–`0x8b7f8b5c`: 119 NUL-terminated strings, 106 name-like), but a whole-image search for 4-byte words equal to each pool string's address in **three encodings** (native `cd+0x456b1000`, ELF-space `cd+0x39800000`, raw `cd`) returns **0 hits** — even for `MODE_INACT`/`WAIT_SESSION_OPEN_CNF`. By contrast the **stock** table *does* hold native pointers (`0xd0d77xxx`) that resolve. The 44 words before the pool (`cd 0x8b7f8180`) are **function pointers** into seg[18]. Candidate tests: the table is **outside the 21 captured segments**, or it is **offset-encoded** — **⚠ BOTH TESTED AND NEGATIVE by Doc 218; the route is exhausted. See the Doc 218 rows below** | **NOT MET** → **bounded NEGATIVE** (Doc 218) |
| ★★★ Test Doc 217 §8.1 candidate (a) — is the table **outside the 21 captured segments**? (Doc 218 §3.5) | a yes/no | **MET — NO.** The UZ801 ELF has 27 phdrs, the coredump 21; the six extra are `type 0` (`phdr[0]`,`phdr[1]`) + four `filesz=memsz=0` placeholders. `phdr[1]` (`va 0x8b900000`, `filesz 0x1c88`) carries content and is the **MBN attestation header** (`Generated Test Root CA`, `SW_ID`, `HW_ID`, `SHA256`, `qctdevattest.crl`) — **not** a table | **MET** (negative) |
| ★★★ Test Doc 217 §8.1 candidate (b) — is the table **offset-encoded**? (Doc 218 §3.2) | a yes/no | **MET — NO.** 0 runs of ≥8 distinct strictly-increasing words equal to pool string offsets; and a **base-independent** search for the pool's consecutive **offset-difference** pattern (24 names from `SUBSCRIPTION_CHGD`) returns **0 runs** | **MET** (negative) |
| ★★★ Decisive count — does **any** word point into the command-name range? (Doc 218 §3.3) | a count | **MET — 3, at chance level.** `0xd0ea9816`@`cd 0x8a6a2408`, `0xd0ea977c`@`cd 0x8a78a494`, `0xd0ea9568`@`cd 0x8ab2aa9c` (all phdr 18), **all mid-string**; expected by chance ≈ 1.4 for the 1 624-byte range | **MET** (negative) |
| ★★ Positive control — does **stock** have such a table? (Doc 218 §4) | a yes/no | **MET — YES.** `mmoc_cmd_name[]` @ stock ELF `0xc1d33eb0` = **96 native pointers** (`0xd0d77820`…) resolving **exactly** to `SUBSCRIPTION_CHGD`, `PROT_GEN_CMD`, `OPRT_MODE_CHGD`, `WAKEUP_FROM_PWR_SAVE`, `PROT_REDIR_IND`, `PROT_HO_IND`, `MMGSDI_INFO_IND`, `DUAL_STANDBY_CHGD`, `1XCSFB_PROT_DEACTD_CNF`, `DS_STAT_CHGD_CNF` via the stock coredump. **The stock-only asset is confirmed as stock-only** | **MET** |
| ★★ Confirm the negative is not an **encoding** artefact (Doc 218 §3.4) | a check | **MET.** **878** aligned words in one 1 MB window of seg[26]'s native range are native pointers into seg[26] (**99** resolve to strings) ⇒ the native encoding is in use, so Test 1's 0 is a real absence | **MET** |
| ★ Characterise the pool's runtime structure (Doc 218 §5) | a layout | **MET.** `[struct-pointer array][protocol names][14-ptr array][command names][log formats]`; the **14-entry** pointer array (`cd 0x8b7f84d0`) points to `0xd0cb94xx`, whose targets are **ALL ZERO** in the dump — the signature of a **table allocated but never populated** | **MET** |
| ★ Name the UZ801 MMOC name-lookup mechanism (Doc 218 §6) | a hypothesis | **STATED, not proven:** a **runtime-populated** table (unpopulated because the modem was `offline`) and/or a **hash/ID** lookup (as the F3 descriptors use, Doc 216 §6). **What is ruled out:** a static pointer array locatable by its references | **STATED** |

**The one-line truth:** the port has **removed the QMI-level refusal** but has **not brought the modem
online**, and it now does so **safely** — a bad patch can no longer brick the device into a reset loop.
**Doc 204 adds the sharpest form of the remaining problem:** the boot-time `u=1` race the campaign was
about to patch is **not** the gate — both of its outcomes are `offline` — so the gate is the
**class-invariant** one: the CM serving-system / `cmmsc_auto` layer never runs and the `dcc` online command
is dropped in 0.35 ms, with the modem itself answering **`AT+CFUN=1` → `+CME ERROR: phone failure`**.
**Doc 206 sharpens it further and corrects the target:** the function the campaign has called `cmmsc_auto`
for four documents is really **`cmcc.c`**, so the deployed GROUP 11 patch (`0xc0691950`) has never touched
`cmmsc_auto.c`; the "no static caller ⇒ never invoked" inference is **falsified by stock's own behaviour**
(stock runs that same `cmcc.c` code with no static caller in the image); and the **WMS/MMGSDI input path is
intact in both builds** (identical `cnf` sequence, identical put/process), so the divergence is confined to
the CM layer itself.
**Doc 207 then closes that axis by measurement:** the `cmcc.c` module is **the same module in both builds**
(13 vs 12 functions, 90 vs 89 sites, 1:1 site counts) and the service-available chain
`FUN_c06928a8 → FUN_c06917f0` is **behaviourally identical to stock's** `FUN_c066fa30 → FUN_c066e9a4` — so
GROUP 11 is inert **by construction**, not by accident. It also corrects the F3 descriptor model (`word1` is
a hash for ~92 % of sites, not a string pointer) and leaves exactly one bounded open route: the
`cmmsc_auto.c` module is unreachable by the descriptor route in **both** images, so the next attack must be
a **runtime** one on the CM command dispatcher, or must use the descriptor hashes as the cross-build key.
**Doc 208 then removes the last "the code differs" hypothesis and, in doing so, exposes a measurement
blocker:** the CM dispatcher is **structurally identical** between the builds (83.49 % raw `fdiff` similarity
collapses to **2 declaration-order blocks** once `unaff_GP` global-layout offsets are normalised away), the
stock and UZ801 F3 arms are proven to have used the **same `cntl-enable 64` mask** (so the campaign's
file-set differentials are mask-controlled and the load-bearing `cmss.c`/`cmmsc_auto.c` negative is
admissible), and the F3 record's `code` field is proven **not** to be the AP-selected SSID (it is a
modem-assigned per-boot id — read a capture's mask from its `diagboot.log`). But the device has **lost the
F3 burst-capture class** that every CM-layer negative must be scored on: **20 consecutive boots are blind**,
across three masks and a fresh redeploy, with the sweep width, the enable speed, the firmware bytes and the
modem's persistent partitions each **falsified** as the cause. **Restoring that class is now the gating task
for all further F3-scored CM work.**
**Doc 210 then clears that blocker and, in doing so, refutes five load-bearing negatives and opens a new
lead.** The class was lost to a **1-second-granularity DIAG-device poll**: `cntl-enable` fired at ~13.18 s;
polling at `sleep 0.05` fires it at **12.3–13.1 s** and catches the modem's cold boot (**4/4**). The
restored class is **deeper** than the campaign's "burst" class — `rcinit_init.c` = **76–171** vs **0** — so
`cmss.c` / `cmmsc_auto.c` / `cmregprx.c` / `sdss.c` / `sdcmd.c` / `mmoc.c` all **run**, `cmmsc_auto` emits
the exact stock sequence including `updating op_mode`, and **"the CM serving-system layer genuinely never
runs" is RETRACTED** (a coverage artifact). The new lead is the **policyman end state**: the UZ801 firmware
boots with **`Filtered RAT mask 0 based on HW capabilities 544`**, its RAT/RF/UE-mode EFS items cannot be
populated and the write-back fails — while two independent controls (a persisted QMI profile, a successful
NAS EFS read) prove the EFS is **neither read-only nor unwritable**, making the failure **item-specific**.
Behind it sits a coherent **missing-NV cluster** (`uim.c:7136`, `cmph.c Can't read ue_based_cw`,
`txlm_hal.c:821` ×27) and a **census-invisible CM divergence** (`MSC_AUTO` mask `0x220` on stock vs `0xebe`
on UZ801, where `0x220` = **544** = exactly UZ801's policyman HW-capability value). **The single decisive
next step is a stock HMU05 boot capture on the restored instrument** — the only arm that can separate
"shared with stock ⇒ benign" from "UZ801-only ⇒ causal", and the campaign has never had one that reaches
`rcinit`.
**Doc 211 then took that capture and, in doing so, found a second parser defect that had been distorting
the very numbers the campaign was reasoning about.** The stock boot on the restored instrument
(`stock_boot_211.bin`, `admissible=yes`, `rcinit_init.c` = 76) **reaches the policyman block** — and emits
the **identical** `Filtered RAT mask 0 based on HW capabilities 544` sequence — while **registering on JIO
4G** (`Mode: 'online'`, MCC 405 / MNC 861). So Doc 210 §5's lead is **FALSIFIED**: a filtered RAT mask of 0
is not why UZ801 stays offline. Chasing that lead exposed the real problem: `f3parse.py` read `args` at
`k+20` while the Doc-210-era `extra` pad sits **before** the args, so every affected record's arguments were
**scaled by 256^extra** — which is exactly why `=CM= mode_pref %d` appeared to read `9728` on stock and `38`
on UZ801. With the fix it is **38 in every arm**, and so is everything else: `old_op_mode → new_op_mode` is
**10 → 11**, `NV hybr pref` is **1**, `MSC_AUTO` is `0x200 & 0xebe = 0x200`, `Filtered RAT mask` is
`[0, 544]`. **The CM *inputs* are byte-identical between the two firmwares; the divergence is entirely
downstream.** With that settled, Doc 211 took the **first same-window differential the campaign has ever
had** — one device, one EFS, one instrument, one 240 s window, only the firmware differing — and it is
sharp: stock runs the **CM serving-system layer** (`cmss.c` 18, `cmlog.c` 6) and never leaves
`Prot_state 0(NULL)`; UZ801 **never runs it** (`cmss.c` 0, `cmlog.c` 0), runs a whole **MCFG-UIM/SIM-Toolkit
bring-up that stock lacks** (`mcfg_uim.c` ×9, `uimsub_manager.c` ×5, `uimgen.c`, `gstkutil.c` ×3) one of
whose steps **fails** (`UIM_%d: Read to NV active slot configuration unsuccessful`), activates the SD script
**`ssscr_user_offline_cdma`**, and drives MMOC to **`Prot_state 7(OFFLINE)`**. The single sharpest event is
the MMOC decision: both firmwares receive `=MMOC= Recvd command 2(OPRT_MODE_CHGD)` and log an identical
sequence up to `Trans_state 0(NULL)`, then stock emits **`New transaction : 2(ONLINE)`** and UZ801 emits
**`New transaction : 3(OFFLINE)`** — a `num_args = 0` record, so **untouched by the parser defect**. UZ801
v19 has been restored and verified (68/68 manifest, `offline`, guard clean) and the next two gated steps are
(1) a **burst-vs-blind pair** isolating the first UIM/STK record's `tsk`, and (2) an instrument that logs the
**MMOC `OPRT_MODE_CHGD` payload / MMOC session table**, which is where the decision is actually made.

**Doc 212 then ran the `OPRT_MODE_CHGD` census across every capture in the archive — and found that the
"UZ801 uniquely receives `OPRT_MODE_CHGD`" reading is a WINDOW ARTEFACT.** Stock receives it **too**, in
`hmu05_stock_boot.bin` — the **only** capture in the archive that reaches `Prot_state [MAIN] 5(ONLINE_GWL)`,
where the identical command produces **`New transaction : 2(ONLINE)`**. The clean 240 s same-window pair does
**not** show it because stock has **not yet transitioned** at 240 s (Doc 211 §4.5: `Prot_state` stays
`0(NULL)` throughout), while UZ801 transitions at ~15 s of boot. **A window is not a control: the pair compares
"stock before the transition" against "UZ801 after one".** With the event anchored instead of the window, the
differential is unchanged and now **confirmed on two independent UZ801 captures** (`uz801_v19_boot_240.bin`,
`boot_210_canon.bin`): the **same** command, the **opposite** outcome ⇒ **the decision is inside MMOC, on the
command's op-mode payload.** Doc 212 also **ruled out** four candidate triggers — the `cmph.c`
`rat_disabled_mask` sequence (stock has **7** of them in 240 s and emits no `OPRT_MODE_CHGD` after any of
them), the `dcc` command break (all 8 `dcc` records sit **after** the OFFLINE transition), the
`policyman_rf.c can't populate RF item from EFS` failure (**stock emits the identical ×3**), and
`trm_config_handler.c` (identical on both) — and produced the **complete 70-vs-65 file census** (11 UZ801-only,
6 stock-only; `cmss.c` 18 vs 0, `rf_task.c` 2 vs 0). It also **recovered the full MMOC name tables** from the
modem coredump and verified them against five captures (task #13's instrument), while establishing the
~~**negative** that matters: the tables are a runtime-populated **string blob**, not an indexable array (0
pointer hits in the dump, no `mmocdbg.c` anchor in `logsite.py`, 0 string hits in the decompile)~~ **⚠ THE
NEGATIVE IS RETRACTED BY DOC 215 (2026-09-27): the tables ARE an indexable array and the pointer hits DO
exist — in the firmware's NATIVE address space (`ELF VA + 0xbeb1000`). `mmoc_cmd_name[]` is at ELF VA
`0xc1d33eb0` in file-backed seg[19], 96 entries / 90 named, and the dump carries 7 native pointer hits. Doc
212's "0 pointer hits" searched the ELF link space only (Doc 215 §3.2/§4).** **Task #12
(the burst-vs-blind pair) is re-scoped, not abandoned:** the UIM/STK burst **ends ~62 000 ticks before** the
`OPRT_MODE_CHGD`, so it is upstream but **not proximate**, and the question is now build-composition rather
than capture-class.

**Doc 215 (2026-09-27) then corrected the model that all of the above rests on: the firmware's data pointers
live in a NATIVE address space (`native VA = ELF VA + 0xbeb1000`), so every prior "no reference to X exists"
verdict that searched only the ELF link space is unsafe.** It solved the constant (13/14 on a known table),
recovered the complete `mmoc_cmd_name[]` (96 entries / 90 named) from file-backed seg[19], recovered the
`=MMOC= %s` F3 descriptor `{line=291, level=20, …}` from file-backed seg[18], and **falsified Doc 214 §12 step
1** (splicing seg26 content into the ELF resolves nothing, because the references are `0xd0d77cxx`, not
`0xc4ec6cxx`). **The `OPRT_MODE_CHGD` emitter is still NOT found** — the name table is data-only and no
instruction references it by any absolute constant — so the next step is to resolve the PC-relative base
(a Ghidra memory block at the native VAs, or the GP value), and to solve the **UZ801** constant, which is
unknown. See item 20.

**Doc 216 (2026-09-27) then executed Doc 215 §13.5 and came back with two negatives and two assets.** Reading
the nine CM functions that touch `0xc1d3xxxx` shows the Doc 215 §13.3/§13.4 lead is a **segment-proximity
artefact** — they are CM **task-scheduler** code and their targets are CM scheduler globals that merely share
seg[19] (3.1 MB), so "within ±0x2000 of the MMOC table" is not a neighbourhood test; **the CM→MMOC adjacency
lead is withdrawn**. In the same pass the region immediately before `mmoc_cmd_name[]` resolves to a **14-record
`{char* name, void* X, u32=0}` protocol-name table** (`0xc1d33e08`–`0xc1d33eb0`, file-backed seg[19]) whose 10
distinct names (`MODE_INACT`, `MC(AMPS)`, `CDMA(MC Task)`, `GPS`, `HDR(HDRMC Task)`, `NAS(REG Task)`,
`RESERVED`, `HYBR NAS(REG Task)`, `HYBR 3(REG Task)`, `Invalid`) supply the **protocol dimension** that
`mmoc_cmd_name[]` lacks — with the caveat that its **indexing is unestablished** (`NAS(REG Task)` occupies five
slots, so it is an index→name mapping, not a unique list). Two further results matter for every downstream
plan: **seg[26] content exists in NO file** (`modem.b26` does not exist; the distinctive strings are absent
from all 23 candidate artifacts including both 64 MB FAT16 images) ⇒ **Doc 214 §12 step 1 / task #15 is closed
as unimplementable and the UZ801 native constant cannot be solved without a UZ801 coredump**; and the F3 log
descriptor is a **16-byte** record of which only the **~8 % string form** carries a pointer, so
**`Recvd command %d(%s)`, `Recvd report`, `Trans_state`, `Curr_trans` and both `Prot_state [MAIN]` variants have
ZERO pointer occurrences in the image** — **Doc 215 §9 step 1's "xref the `Recvd command` format string" cannot
work as written**, and the descriptor **hash** must be computed instead. Finally, the device was re-examined
read-only and the SIM-lock question is **closed NEGATIVE**: all facility locks are `0`, `AT+CPIN?` is `READY`,
the QMI user lock is `no`, and ModemManager's `lock: sim-pin2` / `enabled locks: fixed-dialing` are **MM
misreports**. See item 21.

**Doc 217 (2026-09-27) — THE UZ801 COREDUMP IS CAPTURED, and item 21's priority #1 is CLOSED.** 84 407 190 B,
md5 `59855a4cfac73e7dd2a4b54032ff5c42`, ELF32 `ET_CORE`/`EM_NONE`, 21 phdrs, at
`scratch/coredump_uz801/uz801_coredump.elf`; **every segment matches the UZ801 ELF phdr-for-phdr**, including
the three `filesz = 0` regions that exist in no file — **seg[20], seg[21] and seg[26]**. The UZ801 native
constant is therefore **solved and content-verified**: `native = ELF VA + 0x0beb1000` (= coredump VA +
`0x456b1000`), with `ELF VA = coredump VA + 0x39800000` on all 21 segments — identical to stock, because it is
a property of the MPSS runtime map, not of the firmware. **Doc 216 §5's "seg[26] content exists in NO file"
is superseded**: that was true of the firmware artifacts; the coredump *is* the file, and seg[26]
(`0x00aeb000` B) turns out to hold the whole MMOC string pool plus the UZ801 source paths. **★ The capture
required one non-obvious step: `rmmod qcom_bam_dmux` BEFORE firing the crash.** Fired with the module loaded,
the AP hung (new `boot_id`, PMIC PON WDT) and **no coredump was ever created** — `console-ramoops-0` ends at
`8858.706597 … executing serialized asynchronous SSR teardown`, i.e. **between `T4 state_lock acquired` and
`T5 rx released` inside `bam_dmux_power_off()`**. That block propagates because **patch 821's synchronous
`flush_work(&dmux->ssr_teardown_work)` (`qcom_bam_dmux.c:2510`) sits inside the SSR subdevice's `stop`
callback**, i.e. inside `rproc_stop_subdevices()` → `rproc_stop()` → `rproc_boot_recovery()`, which is
**upstream of `rproc->ops->coredump()`** — so a teardown block means the dump is never generated. This is a
**fourth AP-hang site**, with an exact block window and a named propagation mechanism. A clean **A/B** proves
the cause (loaded → hang, no dump; unloaded → dump, AP alive). **★★ `rmmod qcom_bam_dmux` is SAFE and its own
teardown runs the same `bam_dmux_power_off()` to completion in ~6 ms** (`T5 rx released` → `T6 tx released` →
`T7 power_off returned`, `rmmod_rc=0`, `boot_id` unchanged) ⇒ the block is **specific to the SSR/notifier
context**, not intrinsic to the function — **and the recorded hazard "`bam_dmux_remove()` … will hang a future
`rmmod`" is WITHDRAWN**: the ABBA it described was the *synchronous* watchdog cancel, and the current tree
cancels the watchdog **non-blocking** at `:1609`. **★ The deferred name-level diff is DONE** (it needed the
stock coredump too, because the stock `mmoc_cmd_name[]` pointers land in the stock's `filesz = 0` seg[26]):
**72 of 75** stock command names are present in UZ801, **3 are absent** — **`IMS_DEREG_CNF`,
`PS_DETACH_ENTER`, `WAIT_IMS_DEREG_CNF`** (an IMS-dereg / PS-detach reduction; note `PS_DETACH_CNF` survives
while `PS_DETACH_ENTER` does not, so a *state* was dropped) — while **all 10 protocol names are present in
both with identical exact-occurrence counts**. `SUBSCRIPTION_CHGD` **is** present in UZ801, so the recorded
`0(SUBSCRIPTION_CHGD)` vs `7(DUAL_STANDBY_CHGD)` divergence is **not** a missing name and the Doc 202 §6.3
chain stands. **★ The build provenance is leaked by the image**: 128 source paths, all under
`/home/chiyong/UZ801_V3.0-21/msm8939-la-1-0-2-1_amss_qrd_no-l1-src/modem_proc/` (subtree histogram dominated
by `uim/mmgsdi` 34, `hdr/cp` 29, `uim/gstk` 18, `mmcp/policyman` 12) — an **MSM8939** AMSS tree and a
**`no-l1-src`** package, which is a lead for why the `lte_ml1_*` fault site has never been resolved
statically. **Open problem, stated:** the MMOC pool has **zero pointer references** in three encodings
(native/ELF/raw) — unlike stock, whose table *does* hold native pointers — so **locating the UZ801
`mmoc_cmd_name[]` / protocol tables is now priority #1** (candidate tests: outside the 21 captured segments,
or offset-encoded). See item 22.

**Doc 218 (2026-09-27) then ran those two candidate tests — and both are NEGATIVE, which is itself the
result.** The UZ801 command-name strings (`SUBSCRIPTION_CHGD` … `DORMANT_GWL`, `cd 0x8b7f8508`–`0x8b7f8b5c`)
have **no pointer table and no offset table anywhere in the captured image**: a three-encoding pointer search
returns **0**, an absolute-offset run search returns **0**, a **base-independent** offset-difference search
returns **0**, and the decisive count of *any* word in the 1 624-byte command-name range is **3 — all
mid-string, at the chance level (≈1.4 expected)**. **The stock build is the positive control and it does have
the table**: `mmoc_cmd_name[]` at stock ELF `0xc1d33eb0` is **96 native pointers** resolving exactly to the
command names via the stock coredump. The negative is not an encoding artefact — **878** aligned words in one
1 MB window are native seg[26] pointers (99 resolve to strings) — and it is not "outside the captured
segments": the only un-dumped ELF region is the **MBN attestation header**. What the pool *does* contain is a
**runtime structure** — `[struct-pointer array][protocol names][14-ptr array][command names][log formats]` —
and the **14-entry pointer array's targets are ALL ZERO**, the signature of a table **allocated but never
populated**. The mechanism is therefore **not** a static pointer array: it is a **runtime-populated** table
(unpopulated because the modem was `offline`) and/or a **hash/ID** lookup (as the F3 descriptors use).
**Consequence, stated plainly:** the asset "any MMOC ordinal can be decoded offline" is **stock-only**, and
UZ801 ordinals cannot be decoded from the image alone. See item 23.


---

## 4. Patch version history (`modem.b16`)

Diff counts and results are from Doc 195 §6/§7.3/§12 plus the `scratch/apply_v*.py` scripts. **v15–v17
were never written up; this ledger is their record.**

| Ver | Δ bytes | What it does | Result |
| :-- | --: | :-- | :-- |
| v1–v6 | 16–134 | DMS bypass, CM whitelist return-2, NOP the CM mode check | QMI error 52 (the DMS `0xab4` target is the LPM path) |
| v7 | 145 | `call FUN_c0f8ae64` + boot auto-online dispatch | QMI error 52 gone; `FUN_c0f8ae6c` aborts on uninit `DAT_c33991e8` |
| v8 | 167 | as v7 + synchronous success return | **Error 52 eliminated**; SIM read fully; stack still down |
| v9 | 148 | `call FUN_c067cc2c` + force MMOC mode 5 | **BOOTLOOP** — called during CM early boot, before RF init |
| v10 | 175 | set `cm_state+3=5` then `call FUN_c067cc2c(1)` | **BOOTLOOP** — cross-task call from `ds` faults on `gp+0x4a18` |
| v11–v12 | ~200 | `jump 0xc0e03b80` native CM dispatch | error 52 — CM whitelist skipped `cm_state+0x39e8=3` init |
| v13 | 261 | 44-byte whitelist blocks set `cm_state+0x39e8/f0=3`, copy txn/sub IDs, ret 2 | stable boot, 5 h+ uptime, **mode stays `offline`** |
| v14 | 265 | v13 + `cmmsc_auto.c` mode gate bypass (`0xc0691950`) | `offline`; `FUN_c06917f0` is never called on this boot path |
| v15 | +? | v14 + suppress `DUAL_STANDBY_CHGD` (`FUN_c0b54c40` sub1, `FUN_c067cd2c` sub0) | **no observable change vs v14** — runtime `CMD alloc u=224, tsk=dcc` freed, 0 `mmocdbg.c`, 0 `cmmsc_auto.c` (Doc 198 §4) |
| v16 | +? | v15 + force target mode `= 5` in both CM handlers; GROUP 11 becomes a direct jump to `0xc0691974` | **no observable change vs v14** — runtime same; boot reaches MMOC but `Prot_state 7(OFFLINE)`, `DUAL_STANDBY_CHGD` still received, 0 `ONLINE_GWL` (Doc 198 §5) |
| **v17** | 289 | v16 + GROUP 14: platform gate calls the lazy allocator `FUN_c0f9ce30` instead of `FUN_c0f9ce28` | ⚠️ **NEVER ACTUALLY BUILT** — the deployed image lacks GROUP 14, so its recorded result is the **v16** result (Doc 199 §2.1) |
| **v18** | **290** | **v17 with GROUP 14 genuinely applied** | **NEGATIVE — boot log unchanged (807 vs 839 msgs, `ONLINE_GWL` 0, `OFFLINE` 24, RFC silent); mode still `offline` (Doc 199 §3)** |
| **v19** | **291** | **v18 + GROUP 15**: the caller `FUN_c067f0bc` writes `cm_state+0x39e8 = 3` instead of `1` (one byte at `0xc067f4c0`, `0x22`→`0x62`) — makes v13 GROUP 2's intent survive its own caller (Doc 200 §6.3) | **NEGATIVE — mode still `offline`; boot capture 841 msgs, `cmmsc_auto.c` still **0**, `mmocdbg.c` 60 / `cmph.c` 22 / `mmocmmgsdi.c` 61, `rflte_*` 0 (Doc 200 §6.4). The byte is provably inert** |
| **v20** | — | **the condition-(A) inversion** (`0xc067d18a: 0x42→0x02`) — derived 2026-09-26, encoding verified | **NEVER BUILT, NEVER DEPLOYED, and now WITHDRAWN.** Its premise was wrong: `cmd+0x14` is the modem's **uptime in seconds**, so `== 9` is a moment, not a class, and `0xc067e994` (the `!=9` target) is the `ctx->[0]==2` handler, not a drop (Doc 201 §3.3) |

**v19 is the current deployed firmware** (`modem.b16` md5 `c71d53464a1e88f2072cbbb6f7f87475`, `modem.mdt`
`9f39ce579114bcf1383bbdce62a8d284`). Deployed 2026-09-26, label `v19 (0x39e8 caller-fix c71d5346)`,
`boot_id` `cb740804-2aef-4f8a-88e9-584106721d93`. It is a **verified 1-byte delta from v18**
(`scratch/ref_v18/` holds the v18 backup) and is **behaviourally identical to v18** — it is kept because
it is the tested state and the added byte is inert. The previous deployed firmware was
**v18** (`modem.b16` md5 `a90524d72577d5604ff5ee15014e01a6`, `modem.mdt` `6360a026aaa58f7ee9b2d0a34180786d`,
`boot_id` `6b5b7fb2-acbd-4703-9290-504688b3ece1`), itself behaviourally identical to v17-as-deployed.

> **⚠ CORRECTION (Doc 199, 2026-09-26).** The row previously recorded here as `v17` was produced by an
> *earlier revision* of `apply_v17_patches.py`: the script was edited at 15:58 to add GROUP 14 but never
> re-run and never re-deployed, so the image carried groups 1–13 only (289 diff bytes, not 290). **GROUP 14
> had never been tested until v18.** The previous image is preserved at
> `/tmp/modem.b16.deployed_v17.md5_982a824e`.

> **v15/v16 results — RECOVERED (2026-09-26).** They were never written up, but their archived captures were
> found and analysed in **Doc 198**: neither changed the outcome (both still reach CM on the `dcc` task and
> are freed; v16's boot reaches MMOC but stops at `Prot_state 7(OFFLINE)`, and `DUAL_STANDBY_CHGD` is still
> received). Doc 198 also shows the MMOC boot burst is **text-identical across v7/v8/v13/v16** — the boot
> path was never the lever the campaign was pulling. Attribution is by filename + mtime, since F3 carries no
> wall clock (Doc 198 §1/§3).

> **Deploy-helper bug (fixed 2026-09-26).** The first deploy globbed `modem.*`, which swept the
> `modem.b16.bak` / `.patched` / `.cm_patched` / `.oprt_jump` / `modem.mdt.bak` scratch copies into
> `/lib/firmware`. The kernel only loads what `modem.mdt` names, so nothing broke, but the junk was
> removed and the glob is now anchored to `^(modem\.(mdt|b[0-9]+)|mba\.mbn)$`. **A deploy must report
> exactly 22 files.**
>
> **Second deploy-helper bug (found and fixed 2026-09-26, Doc 199 §1).** `$FILES` contains newlines, and
> the install line embedded it in a *double-quoted remote command* — so the remote shell treated every
> line after the first as a separate command: only `modem.b00` was copied and `sync` never ran. The
> live-image md5 check caught it (as designed), but the install had already run, leaving a partial set.
> The list is now collapsed to one line (`FILELIST="$(echo $FILES)"`) and the install's exit status is
> checked. **If a deploy aborts at the live-image check, the live set may be PARTIAL — restore with
> `cp -f /overlay/fwbackup/hmu05_stock/modem.* /lib/firmware/` before rebooting.**

---

## 5. Deployment runbook (the safe path)

```bash
# 0. build the patched set (from the repo root) — one of:
python3 scratch/apply_v17_patches.py           # or the current apply_v*.py
python3 GitIgnore/compare/ufi001b_hash_tool.py patch scratch/uz801_patched 16 scratch/uz801_patched/modem.b16

# 1. deploy + verify + re-arm the guard, then reboot
bash "Docs/Modem Stability/evidence/197_bootloop_guard/deploy_uz801_patched.sh" \
     --label "v18 (<first 8 of modem.b16 md5>)" --reboot

# 2. after it comes up, check the two things that matter
ssh root@192.168.8.1 'qmicli -p -d /dev/wwan0qmi0 --dms-get-operating-mode'
ssh root@192.168.8.1 '/root/modem_guard.sh status'
```

The helper refuses to touch anything unless **every** file is md5-verified on the device first, and it
resets the guard only after a fully verified install. If the patch bootloops, the guard reverts to stock
after > 3 unproven boots and leaves `/root/BOOTLOOP_REVERT.txt` plus the quarantined set.

**Never** use `echo stop > /sys/class/remoteproc/remoteproc0/state` as a "restart the modem" lever — it
resets the AP (PMIC PON WDT). Only a cold reboot restarts the modem cleanly.

---

## 6. Recovery runbook

| Situation | Action |
| :-- | :-- |
| Patch bootloops, device still reachable between resets | do nothing — `modem-guard` reverts after > 3 boots; then read `/root/BOOTLOOP_REVERT.txt` |
| Patch bootloops, want to stop it now | `ssh root@192.168.8.1 '/root/modem_guard.sh revert-now'` |
| Guard disabled or bypassed and device loops | host-side race: `python3 scratch/restore_fast.py` (pings `192.168.8.1` and fires `cp /overlay/fwbackup/hmu05_stock/modem.* /lib/firmware/` during the pre-crash window) |
| Device completely unreachable | physical power cycle, then boot into a window and run the host-side restore above |
| Want to inspect the offending patch | `ssh root@192.168.8.1 'ls /overlay/modem_guard/quarantine/'`, pull it, and `diff` against `scratch/uz801_patched/` |

---

## 7. Open questions / next steps

1. **⚠ RETRACTED 2026-09-26 by Doc 200 — the "gate is NAMED (Doc 199 §4)" claim is WRONG.** Doc 199 §4
   said stock emits **0** `mmocmmgsdi.c` messages and that the module is UZ801-only, and named a
   "spurious `DUAL_STANDBY_CHGD` **producer** in `mmocmmgsdi.c`" as the target. Three corrections
   (Doc 200 §2/§5):
   - `mmocmmgsdi.c` is **not** UZ801-only. The stock image contains the identical strings
     (`mmocmmgsdi_card_status_cb` `0xc44a519c`, `Invalid session, sess_type` `0xc44a524c`) and the stock
     boot capture contains **17** such messages (UZ801: 61). The "0 vs 59" table was a filtered subset.
   - There is **no `mmocmmgsdi.c` producer** of MMOC command 7. Every writer of `##0x1000007` in the image
     is a **CM** function in the decompiled range; `b17`–`b25` are R|W **data** segments, so no code
     lives there. The writer is CM's own dispatcher `0x1000038` case, and it is **present and identical
     in stock HMU05**.
   - **The real divergence is the *first MMGSDI event's content***: stock's first event carries a real
     `sess_id` (`evt=0x13, sess_id=0x3ddbfa4f` → `Sess type=0, ss=0`); UZ801's is `evt=0, sess_id=0` and
     resolves to an uninitialised session (`Sess type=0x7fffffff, ss=4`).
   **The corrected target is the `dcc`-task CM command** (Doc 200 §8): on UZ801 it is allocated and freed
   by CM in **0.66 ms** with **no** `cmmsc_auto.c` and **no** MMOC command, where stock drives
   `cmmsc_auto → PROT_GEN_CMD → OPRT_MODE_CHGD → ONLINE → ONLINE_GWL` in **11.5 ms**. Doc 200 §3 also
   corrects the Doc 195 §12.1.1 arrow: stock does **not** auto-online at boot; its boot stops at
   `Prot_state 0(NULL)` and `ONLINE_GWL` needs a `dcc`-task CM command. **The next attempt must target
   the command the `dcc` task builds for a QMI online request** — v1–v18 patched the DMS *reply* and
   CM's *consumers*, which is why the arriving command is still dropped. The concrete open question is
   *what is `r19` on the `0xc0e03c10` path and what CM command code does it become* (Doc 200 §6/§8).
   Two supporting routes remain open: (a) boot the stock firmware with the modem prevented from going
   online, to separate cause from consequence; (b) the `wms.c` MMGSDI-event → CM-command differential
   (Doc 200 §8.3).
2. ~~v15/v16 results~~ — **CLOSED 2026-09-26 by Doc 198**; no re-run needed.
3. **`cmmsc_auto.c` is never reached** on this boot path (v14 finding) — **confirmed by Doc 198**: the
   `DUAL_STANDBY_CHGD` suppression in v15/v16 did not take effect (the command is still the first MMOC
   boot event). ~~**Doc 199 §4 explains why**: the suppression was applied to the consumer, not the
   producer.~~ **Corrected by Doc 200 §4**: the `dcc`-task CM command is dropped *before* `cmmsc_auto`
   is ever invoked, so suppressing MMOC events could never have helped — the target is the command the
   `dcc` task builds, not the MMOC consumer.
4. **Scope guard.** None of this reopens the ~900 s fatal: the fault table and crash branch are
   byte-identical between the sets (`143`/`145`), and **reaching `online` would arm the LTE-gated
   fatal clock** (Doc 194 §11). Success here is a porting milestone, not a stability fix.
5. **★ THE CM `0x1000001` GATE, and a CONCRETE DEFECT IN v13 (Doc 200 §6.1–§6.3, amended 2026-09-26).**
   - **Doc 200 §6 is retracted.** The QMI-mode → CM-opmode jump table is at VA **`0xc1ba7894`** (via
     `gp+0xc7c0`; `gp = 0xc1b9b0d4`) and is **non-sequential**. QMI **online → `r19 = 5` =
     `CM_PH_OPRT_MODE_ONLINE`** — the opmode handed to CM is **correct**. The CM opmode enum lives at
     **`0xc1f5a820`** (`0 PWROFF, 1 FTM, 2 OFFLINE, 3 OFFLINE_AMPS, 4 OFFLINE_CDMA, 5 ONLINE, 6 LPM,
     7 RESET, 8 NET_TEST_GW, 9 OFFLINE_IF_NOT_FTM, 10 PSEUDO_ONLINE, 11 RESET_MODEM`). Doc 200 §8's
     "retarget GROUP 1 to `0xc0e03ab4`" advice is **withdrawn** — that is the *low-power* path.
   - **The gate** (`FUN_c067cd2c` @ `0xc067cd2c`, case at decompile line 1134909) drops `0x1000001`
     unless **all three** hold: `cmd+0x14 == 9`; `cm_state[iVar12*0x1cf4 + 1] != 0`; and the whitelist
     byte **`cm_state+0x39e8 == 3`** (sub 0) / `cm_state+0x39f0 == 3` (sub 1). A 0.66 ms alloc→free is
     exactly one of these early-outs.
     **⚠ CORRECTED 2026-09-26 by Doc 201 §3.2: this is not a three-condition gate. `cmd+0x14` is the
     modem's UPTIME IN SECONDS (Doc 201 §3.1), so condition (A) `== 9` is effectively never true and the
     whitelist (B)(C) behind it is DEAD CODE on this boot path. The one effective condition is
     `cmd->ctx->[0] == 2`, and the `!=9` branch is the NORMAL path (it handles `ctx->[0]==2` and
     otherwise returns silently) — not a drop.**
   - **★★ v13 GROUP 2 is self-defeating.** It patches `FUN_c0683c88` to set `cm_state+0x39e8 = 3` and
     `return 2`. Its only relevant caller, `FUN_c067f0bc` (decompile 1136152-1136155), does
     `if (ret == 2) cm_state[0x39e8] = 1;` — **overwriting the 3 with 1.** Verified in the deployed
     image: `0xc0683c88` carries the patch, `0xc067f4c0` (the caller's store) is unchanged.
     **The fix is ONE BYTE: `0xc067f4c0` `0x22` → `0x62`** (`r2 = #0x1` → `r2 = #0x3`; encodings
     `22 40 00 78` → `62 40 00 78`, verified with `llvm-mc -triple=hexagon -show-encoding`).
   - **GROUP 3 misses worse:** its caller `FUN_c0b51b10` writes `*(gp+0x3d44 + 0x3a00) = 1`, but the
     patch writes `*(gp+0x32fc + 0x39f0) = 3` — the **other state object and a different offset**.
   - **★ Instrument defect declared:** the live capture's F3 mask is narrower than the boot capture's
     (0 `mmocdbg.c` in the whole live 40 s — even after `cntl-enable 64` — vs 60 in the boot capture),
     so Doc 200 §4's `mmocdbg.c` sub-evidence is **withdrawn**. The conclusion survives on the 0.664 ms
     vs 11.5 ms timing and on `cmmsc_auto.c` = 0 across *both* UZ801 captures vs 36 in stock.
6. **★ v19 (the one-byte `cm_state+0x39e8` fix) was DEPLOYED and is NEGATIVE (2026-09-26).** Mode still
   `offline`; the post-deploy **boot** capture (841 msgs) shows `cmmsc_auto.c` still **0**, `mmocdbg.c`
   60, `cmph.c` 22, `mmocmmgsdi.c` 61, `rflte_*` 0 — i.e. no change from v18. **Forcing the whitelist
   byte at its caller is not sufficient.** The byte is retained (provably inert); **v19 is now the
   deployed lineage** (1-byte delta from v18, `scratch/ref_v18/` holds the v18 backup).
7. **★ Next candidate — RE-SCOPED 2026-09-26 by Doc 201; the old item is CLOSED.** The old question
   ("who writes the `cmd+0x14` field on the DMS online path, and is it `9`?") is **ANSWERED**: the CM
   allocator `FUN_c06951b0` writes it from `FUN_c093cc80()`, `u` in the CM logs **is** that field, and its
   value advances **1/second** — it is the modem's **uptime in seconds**. So condition (A) `== 9` is a
   moment and the whitelist path is dead; **the v20 condition-(A) patch is withdrawn unbuilt** (Doc 201
   §3.3). `cmmsc_auto` (`FUN_c06928a8`) still has **no direct caller and no pointer-table reference** in
   the image — it is invoked through a **runtime-registered handler**, so the open question is now
   **which message invokes `cmmsc_auto`** (Doc 201 §5.3/§7.1). The static route to the online command's
   *code* is blocked: the DMS handler forwards to the **external** `thunk_EXT_FUN_d03d3420(0xc,0x1a,0x30f)`
   (the `0xd0…` region maps to no PT_LOAD), so the `dcc` command's code/ctx cannot be read from
   `modem.elf` (Doc 201 §4).
   **★ The mask confound was RAISED and CLOSED in the same session** — but **Doc 202 §5 re-opens the
   *coverage* dimension and finds the control was under-powered.** A live **256-sweep** boot capture
   (`scratch/boot_sweep256.bin`) differed from the 64-sweep captures by **one file** — `nvruim.c` *lost*
   (Doc 203 §3 corrects `201` §6's "added `rcinit_rex.c`"; that file is in **both**) — so the **sweep**
   dimension *is* saturated — but that capture is in the **24-file class** (`mmocdbg.c` = 0), i.e. it
   **missed the modem's early boot burst**. `mmocdbg.c` is **60** in six other UZ801 captures and
   **0** in six, *of the same firmware* ⇒ the confound is **per-capture coverage**, not the mask. The
   negative **survives** when re-scored on the six burst-caught captures (§3 table; Doc 202 §5.3), but the
   named next step is **replaced**: there is **no boot-time RF/protocol gate to find** — Doc 194 §7 is
   retracted, the LTE stack is **online-gated**, and the target is that **UZ801's `u=1, tsk=cm` CM command
   never reaches `cmmsc_auto`** (Doc 202 §6/§8). The next experiment is therefore a **coverage-controlled**
   boot capture (bind as early as the stock capture did; sweep 256 on the 32-file class), not a search for
   a boot gate.
8. **Instrument rule (learned the hard way):** a negative scored on a **live** capture is only valid if
   that capture is shown to carry `mmocdbg.c`/`cmmsc_auto.c`. The live mask is narrower than the boot
   mask (0 `mmocdbg.c` in the whole live 40 s, even after `cntl-enable 64`) — **score on a boot
   capture**, which demonstrably carries both. **Updated 2026-09-26 (Doc 201 §6.1):** a negative must
   *also* be shown to be **sweep-saturated** — i.e. that raising `cntl-enable` to 256 adds no new file.
   On UZ801 it does not (`scratch/boot_sweep256.bin`), so a UZ801 negative from a 256-sweep capture is
   admissible. **★★ Updated AGAIN 2026-09-26 (Doc 202 §5):** a negative must *also* be scored on a capture
   that demonstrably **caught the modem's early boot burst** — the tell is the capture's **file count**
   (`mmocdbg.c` = **60** and 31–32 distinct files ⇒ burst caught; `mmocdbg.c` = **0** and 24–25 files ⇒ the
   bind happened after the burst and the boot-path messages are simply absent). `mmocdbg.c` is 60 in six
   UZ801 captures and 0 in six **of the same firmware** — so the zero is an instrument artifact.
   **★★★ Updated 2026-09-26 (Doc 203 §3.2): the sweep condition is RETIRED and the burst condition
   SUBSUMES it.** `scratch/boot_sweep256.bin` is **sweep-saturated and still blind** (`mmocdbg.c` = 0), so
   sweep-saturation proves nothing; conversely the 64- and 256-sweep captures differ by **one file and
   three messages**, so the sweep setting has no measurable effect. **From Doc 203 on: a negative is
   admissible iff its capture has `mmocdbg.c` > 0.** `/root/diagboot.status` (new, Doc 203 §5) records
   that count on-device for every boot, so the check no longer requires re-parsing the blob.
   **★★★★ CORRECTED AGAIN 2026-09-27 (Doc 210 §3.2): `mmocdbg.c` > 0 is NECESSARY BUT NOT SUFFICIENT —
   the correct condition is `rcinit_init.c` > 0.** The six "burst-caught" captures the campaign scored its
   CM negatives on have **`mmocdbg.c` = 60 and `rcinit_init.c` = 0**: they reached the *middle* of the
   boot, not its bring-up. `rcinit_init.c` records the firmware's own cold-boot groups
   (`function enter group 4 func_name mc_init`, `group 5 func_name reg_init` / `mmoc_init`), so a capture
   carrying it demonstrably **started before or at the bring-up**. **From Doc 210 on, a negative about the
   boot path is admissible iff its capture has `rcinit_init.c` > 0.** The deployed instrument was extended
   the same day to count it (`diagboot_run.sh` `48d4…`, §7 item 15), so every future boot self-reports the
   condition.
   **★★★★ ADDED 2026-09-27 (Doc 211 §2): a negative or a *value* claim is also inadmissible if its `args`
   were read by a parser older than `scratch/f3parse.py` md5 `9f9d6edf15d64faf3b47897f7838c9ac`.** That
   parser read `args` at `k+20+4*j` while the `extra` pad sits *before* the args, so **every affected
   record's arguments were scaled by `256^extra`** (raw proof: `mode_pref` read as `9728` and `2490368`
   where the true value is `38`; `old_op_mode/new_op_mode` as `2560/2816` where the truth is `10/11`;
   `NV hybr pref` as `256` where the truth is `1`). ~2 % of records, but **every stock `cmph.c mode_pref`
   record** — i.e. exactly the records the campaign was reasoning about. **A "CM value differential" claim
   must state the parser md5 that produced it.**
9. **★ OPEN STABILITY OBSERVATION on v19 (2026-09-26, Doc 201 §7.2).** During the 256-sweep capture work
   the v19 boot **ended uncleanly at ~2 280 s uptime** — the AP reset and came back with a new `boot_id`,
   the guard recorded `consecutive_unproven=1`, and `/sys/fs/pstore/` was **empty** (the PMIC PON WDT STOP
   signature, *not* a panic). The following boot's AP side was wedged
   (`bam_dmux: RX watchdog: quiesced Ns (pc_state=0, pc_line=0, rx=held, ring disarmed)` every 60 s; QMI
   `dms` fails with `CID allocation failed in the CTL client: endpoint hangup`, while `nas` still
   answers). **Not root-caused.** It is *not* the ~900 s fatal (LTE-gated; this modem never reaches LTE —
   P-PMOS3) and *not* the AP-hang signature (an AP hang does not change `boot_id`). A clean reboot
   cleared the guard counter (1 → 0) and restored a working `nas`.
10. **★★★ NEW LEAD — the spurious `evt=0` MMGSDI event (2026-09-26, Doc 203 §4).** The immediate
   precursor of `Recvd command 7(DUAL_STANDBY_CHGD)` → `Prot_state [MAIN] 7(OFFLINE)` is a
   `mmocmmgsdi_card_status_cb` invocation carrying a **zeroed event** (`evt=0, sess_id=0`). Correlation is
   **16/16**: present in exactly the **six burst-caught** UZ801 captures, absent in the **nine blind**
   UZ801 captures and in **stock** (whose first event is `evt=19` → `0(SUBSCRIPTION_CHGD)`). In
   `v19_boot.bin` the causal order is explicit: UIM bridge (`nvruim.c`/`mcfg_uim.c`) at idx 13–14 → the
   zeroed event at idx 26 → `DUAL_STANDBY_CHGD` at idx 28 → `7(OFFLINE)` at idx 31 → `WAIT_SESSION_OPEN_CNF`
   at idx 43, never left. **Named candidate patch (UNBUILT, single variable): make
   `mmocmmgsdi_card_status_cb` ignore `evt=0`.** Caveat stated in Doc 203 §4: the blind class shows no
   `evt=0` yet is also MMOC-blind, so it is **not** established that suppressing `evt=0` alone reaches
   `online`. **Cheaper competing hypothesis — TESTED in the same session and CONFIRMED (Doc 203 §6.1):**
   the two classes are separated **exactly** by which task wins the `u=1` CM-command race. In **all six**
   burst captures the first `u=1` command is `tsk=mmgsdi_1` and a `u=1 tsk=cm` record exists at **no**
   index; in **all** blind captures the first `u=1` command is `tsk=cm` (idx 32–45) with `mmgsdi_1` at
   idx 75–78. Stock is `cm`-winner with `mmgsdi_1` absent at `u=1`. The blind captures are *longer*
   (2 000–5 707 msgs vs 829–2 547), so this is **not** a truncation artifact — they are two genuinely
   different boot sequences. **`gstk` STK activity (6 vs 0) and `evt=0` (YES vs no) ride with the
   `mmgsdi_1` winner.** ⇒ the `mmocdbg.c` count is a **boot-type** tell, not merely a coverage tell, and
   the patch target is the **`u=1` race, upstream of MMOC**. The `evt=0` patch is the fallback.
   **★★★★ ATTACKED and CORRECTED 2026-09-26 (Doc 204). The race was attacked directly; it is real, it
   controls `evt=0`, and it is NOT the online gate.**
   - **Measured.** `scratch/race_classify.py` (delimiter-aware, emission-index ordered — **no**
     `f3clean` median-`ts` window) reproduces the class separation and classifies **four fresh v19
     reboots: all BLIND (`u=1 tsk=cm`, `gstk`=0, no `evt=0`)**. The BURST class is therefore *reachable
     but not what a reboot produces now*.
   - **The race DOES decide `evt=0`** (Doc 203 §6.1 confirmed): `cm`-wins ⇒ no leading `evt=0` and a
     **healthy** MMOC session path; `mmgsdi_1`-wins ⇒ the zeroed `evt=0` → `sess_type=0x7fffffff, ss=4`
     → `WAIT_SESSION_OPEN_CNF`, never left.
   - **The race does NOT decide `online`.** The `cm`-wins (BLIND) class is **`offline`** on a
     **same-boot measured pair** (`race_boot_4.bin` = live `/root/diag_boot.bin` = instrument `out_md5`
     `a7f55a97…`; `--dms-get-operating-mode` → `offline`); the BURST class is `offline` by its own
     `Prot_state 7(OFFLINE)`. **Both outcomes are `offline` ⇒ the race is not sufficient.** This closes
     Doc 203 §4's explicitly-open question ("not established that suppressing `evt=0` alone would take
     UZ801 to `online`") — **it would not**.
   - **The gate is class-invariant:** `cmmsc_auto.c` = `cmss.c` = **0 in all 22 UZ801 captures**, both
     classes, vs **35 / 590** in stock (Doc 204 §6). And the modem itself refuses:
     `AT+CFUN?` = `7`, `AT+CPIN?` = `READY`, **`AT+CFUN=1` → `+CME ERROR: phone failure`**; the QMI
     `set-operating-mode=online` reports **success** and the mode stays `offline` (Doc 204 §7).
   - **⇒ Consequences.** (a) Doc 203 §6.1's "the patch target is now the `u=1` race" is **corrected** —
     the race is a **class marker**, not the gate. (b) The **`evt=0` patch is downgraded from "fallback"
     to "not worth building"** — it fixes a branch that is not on the critical path to `online`.
     (c) The **`dcc` break is now the only measured CM-side failure common to both classes**
     (alloc→free in **72 ticks = 0.35 ms**, no `PROT_GEN_CMD`; re-measured live in Doc 204 §7).
   - **⇒ The next attack is item 7's**, not the race: find what invokes `cmmsc_auto` / what starts the
     CM serving-system layer, and re-enter the `dcc` break from the CM side. **Do not patch the race and
     do not build the `evt=0` patch.**
11. **★★★★ THE F3 DESCRIPTOR TABLE — the static route to `cmmsc_auto` is CLOSED; the open lead is now two
   message IDs (2026-09-26, Doc 205).**
   - **A new instrument, and two retractions.** Doc 205 §3 found *why* the `cmmsc_auto.c` /
     `mmocmmgsdi.c` format strings look unreferenced: the F3 strings live in a **separate string segment
     (seg25)** and are reached through an **8-byte-stride `{packed, strptr}` descriptor table in seg18**,
     sorted by `packed = (line<<16)|level`. Code never embeds the string address. The control is decisive:
     the scan returns **0** for `=CM= mode_pref %d, pref_term %d` / `=MMOC= mmocmmgsdi_card_status_cb` /
     `=CM= RAT_DISABLED_MASK: sys_mode %d` — **strings the modem demonstrably emits** — while returning
     **2** for the one seg18 string (`=CM= CMD alloc u=%d, tsk=%s`). ⇒ **`200 §4`'s "instrument defect"
     framing and `201 §5.3`'s "no pointer-table reference ⇒ runtime-registered" inference are METHOD
     ARTIFACTS** (their conclusions survive on independent grounds; their reasoning does not).
   - **The "missing caller" hypothesis is DEAD, from both ends.** (a) With the calibrated `immext` scan
     (formula derived and validated on six `llvm-objdump`-resolved constants; **two mandatory guards**:
     4-byte alignment + the next instruction's low 6 bits), `FUN_c06928a8` has **0** references of any
     kind, while the same scan correctly finds the **four** intra-`FUN_c06928a8` references the decompile
     predicts (`FUN_c0691d9c` ×2, `FUN_c0692794`, `FUN_c069282c`) — so the method is trustworthy
     (Doc 205 §7). (b) The descriptor table shows the UZ801 `cmmsc_auto.c` is a **NEWER, LARGER** revision
     than stock's: 13 shared log lines match, then a uniform **+9 line shift** from 2849/3830 up, plus
     **three statements stock lacks** (`lte data call call:%d ss;%d` @5076, `Updating ss for call_id:%d
     to HYBR2` @5080, `… to MAIN` @5087) (Doc 205 §8). The caller was never in the image for **either**
     firmware ⇒ **a property of the platform, not a UZ801 build defect**.
   - **The gate is bounded on both sides, now over 26 min.** A live capture whose CM records reach modem
     uptime **`u=1584` (26.4 min)** still shows `cmlog.c` / `cmss.c` / `cmregprx.c` / `cmsoa.c` /
     `cmcall.c` / `cmcc.c` / `cmmsc_auto.c` / `mmoc.c` / `mmocdbg.c` / `mmocmmgsdi.c` / `sdss.c` /
     `sdcmd.c` = **0**, while `cmdbg.c` = 20 (alloc/free only) and `cmph.c` = 1 (Doc 205 §5.2). The
     **one** CM command that runs is `CM_PH_CMD_GET_NETWORKS` (`AT+COPS=?` → `sys_mode 0`) ⇒ the
     dispatcher is alive and the `dcc` drop is **case-specific**, not a dead task.
   - **The op-mode refusal is now TOTAL and ENUMERATED (Doc 205 §6).** QMI `set-operating-mode` =
     `low-power` / `persistent-low-power` / `reset` / `shutting-down` / `online` → **all report success,
     mode stays `offline`**; `AT+CFUN=0` and `AT+CFUN=4` → **`+CME ERROR: operation not supported`**;
     `AT+CFUN=1` → **`phone failure`**; `AT+COPS=0` / `AT+COPS=?` / `AT+CGATT=1` → **`ERROR`**; QMI NAS
     `set-system-selection-preference=lte` → **`QMI protocol error (3): 'Internal'`**. **There is no
     AP-side lever.** No future session needs to re-probe it.
   - **★ New instrument trap (Doc 205 §5.3).** A live `cntl-enable` capture is **NOT a clean time
     window**: the 90 s `capture` produced records whose F3 `ts` spans **1 348.7 s** (`cfm_cpu_monitor.c`:
     19 521 records, all distinct `ts`, ~50 ms spacing). `cntl-enable` **flushes the modem's buffered F3
     history**. ⇒ a live capture is valid for **presence** claims (a flushed zero only strengthens one),
     **invalid** for **rate / order / timing** claims unless the history is separated out (e.g. by the
     1 Hz `u` field). This is a second, independent reason `200 §4`'s live-`mmocdbg.c` sub-evidence was
     unsound.
   - **★ The open lead — the two external-service message IDs.** `FUN_c06928a8` registers its handlers
     with `thunk_EXT_FUN_d0587538(…, 0x456, FUN_c0691d9c, …)` and `(…, 0x422, FUN_c0692794, …)`. Those
     two **message IDs (`0x456`, `0x422`)** are the only concrete handles the image gives. The next step
     is a **cross-reference question**: *which peer sends `0x456` / `0x422`, and is that peer initialised
     on this boot?* — **not** a caller question.
   - **Complementary live differential (the expensive item).** The `u=1584` capture is the first long
     enough to ask a *negative over time*. The missing arm is a **stock** capture of comparable length: if
     stock's `cmss.c` / `cmmsc_auto.c` traffic is *also* zero after boot, the dead layer is the **normal
     steady state** and the gate is purely the **boot instant**; if stock keeps emitting, UZ801's layer is
     **torn down**. Either answer re-scopes the target. Needs a stock-firmware boot + long soak.
   - **Explicitly NOT next:** any further search for a *caller* of `FUN_c06928a8` (closed by Doc 205 §7);
     re-opening the race (`204`), the `evt=0` patch (`204`), the `0x1000001` whitelist (`201`), or the
     `cm_state+0x39e8` byte (`200`/`201`) — all scored and negative.
12. **★★★★ RETRACTION — the function the campaign calls `cmmsc_auto` is `cmcc.c` (2026-09-26, Doc 206).**
   - **The correction.** `FUN_c06928a8` and `FUN_c06917f0` are **`cmcc.c`** (CM Call Control), not
     `cmmsc_auto.c`. Two independent proofs: (a) every log-descriptor they pass resolves to a `cmcc.c:`
     string — `FUN_c06928a8` → `=CM= MMGSDI EPS mmgsdi_status=%d` (UZ801 L3081) and
     `=CM= MMGSDI EPS srv_available=%d, cm_mmgsdi_acl_availability=%d` (L3115); `FUN_c06917f0` →
     `=CM= CC: app_type=%d` (L1266); (b) their own embedded symbols are `s_cmcc_service_available_cb` and
     `s_cmcc_call_control_processing_lte`. **Retracts the label in `201 §5.3`, `202 §7`, `204 §6`,
     `205 §7` and this ledger's §8.** (The name collision is natural: `FUN_c06928a8` emits the
     `CMMSC_AUTO:`/`MSC_AUTO:`-prefixed strings, but it never passes a `cmmsc_auto.c` descriptor; the two
     files' descriptor blocks are only ~0x3A00 apart in the same segment.)
   - **Confirmed against stock runtime.** Stock emits the *same formats* at stock lines **3062 / 3096**
     while the UZ801 descriptors carry **3081 / 3115** — a uniform **+19** shift. The full `cmcc.c`
     differential is a clean staircase (+0 → +4 → +7 → +12 → +19) plus one UZ801-only statement
     (`= Voip CC sys_mode = %d` @2535). UZ801's `cmcc.c` is a **newer, larger** revision.
   - **The deployed patch is re-attributed.** v14/v19 **GROUP 11** patches VA **`0xc0691950`**, inside
     `FUN_c06917f0` — a `cmcc.c` function — under the label "cmmsc_auto.c mode gate bypass". Its rationale
     is **VOID**; it has never touched `cmmsc_auto.c`. It is not thereby harmful (`cmcc.c` is *also* dead
     in UZ801: 0 records vs stock's 9), but **its inertness proves nothing about `cmmsc_auto`**.
   - **The "no static caller ⇒ never invoked" inference is FALSIFIED.** Stock **runs `cmcc.c`** (9 records,
     including the exact lines `FUN_c06928a8` emits) even though that function has **no static caller in the
     stock image either** ⇒ on this platform a function can be invoked with **zero** static references.
     The *measurement* of `205 §7` stands; its *interpretation* does not.
   - **The `0x456`/`0x422` lead (Doc 205 §11 item 1) is CLOSED — negative.** Neither ID appears elsewhere
     in the image as a message ID (the other `0x456` hits are CRMBuilds `readConfigXmlData` config-XML item
     IDs, plus one F3 line number). `0x456` is one of a **computed family**
     `{0x11b,0x21b,0x41d,0x435,0x80c}` + `{0x456,0x422}` all registered with the **same handler**
     `FUN_c0691d9c` (`cmcc_service_available_cb`) and selected by a **service handle** from
     `thunk_EXT_FUN_d001d2fc(sel,&h)`. **There is no peer to find statically.**
   - **★ New tight bound on the input side.** The **WMS/MMGSDI path is intact in both builds**:
     `wmssim.c L4322 wms_sim_mmgsdi_response_cb_proc` fires **14× in both** with an **identical `cnf`
     sequence** (51,26,26,26,0,0,0,0,2,0,2,2,2,0) and identical `slot`; `wms.c` **puts and processes**
     625/626/627/628/629/631 in both (nothing put-but-unprocessed); `wmssim.c` 16/16, `wmsmsg.c` 3/3,
     `qmi_nas_mmgsdi.c` 1/1. ⇒ **The divergence is strictly inside the CM layer** (`cmmsc_auto.c` 35→0,
     `cmcc.c` 9→0, `cmss.c` 590→0).
   - **★ Method retirements.** (a) The **literal-reference census cannot decide code presence** —
     `wmssim.c` emits 14 records with **0** literal `immext` references, exactly like `cmmsc_auto.c`
     (0 refs in **both** builds, yet stock emits 35 records). (b) The descriptor table is **not globally
     sorted** (runs reset at `0xc165c400` 4054→408, and inside the `cmmsc_auto` block 5087→3250), so
     `205 §3`'s "sorted ⇒ one binary search" is too strong. (c) `scratch/desc.py`'s `{u32;u16;u16}` model
     is **WRONG** — it renders a real entry as `hash 0x09fe000b file_id=15620 line?=50262`; superseded by
     `scratch/desc_table.py`.
   - **★ The next attack.** Re-derive the **real** `cmmsc_auto.c` entry point **behaviourally**: the stock
     capture's L3241→L2849→L2651 burst sits immediately before `mmocdbg.c Recvd command 0(SUBSCRIPTION_CHGD)`,
     so work **backwards from the MMOC `SUBSCRIPTION_CHGD` poster** — that function *does* have a literal
     descriptor reference (12 bases exist in the `cmcc.c` block) and is therefore findable. **Precondition
     the campaign never checked:** is `cmcc.c` reachable at all before GROUP 11 can matter? (`cmcc.c` = 0
     records in UZ801.) If it is never entered, every GROUP 11 variant is inert *by construction*.
   - **Do not** cite `FUN_c06928a8` as `cmmsc_auto` again; do not cite `desc.py`; do not re-open the
     `0x456`/`0x422` search, the race, the `evt=0` patch, the `0x1000001` whitelist, or the
     `cm_state+0x39e8` byte.
13. **★★★★★ THE `cmcc.c` AXIS IS CLOSED BY MEASUREMENT — the descriptor model was wrong, and a
   descriptor→code instrument now exists (2026-09-26, Doc 207).**
   - **★ The F3 descriptor model was half right.** `{u32 packed; u32 word1}` with
     `packed = (line<<16)|level` is correct, but **`word1` is a seg25 string pointer for only ~8 % of
     sites; for ~92 % it is a 32-bit hash** (`< 0xc0000000`, not a VA). `desc_table.py entries` filters on
     `word1 ∈ seg25`, so **it silently keeps ~8 % of the table** and every count taken from it is a lower
     bound of unknown size. (Doc 205 §3's own docstring warned about the hash case; the consequence was
     never drawn.) **Separately, `desc_table.py` has a crash-to-empty-output bug** — an unguarded
     `off2va` returning `None` raises inside a `%08x` format, so with `2>/dev/null` a **wide** range prints
     `0` while a **narrow** range inside it prints `149`. Re-take any earlier wide-`entries` count.
   - **★★ The hash is a build-independent statement key.** UZ801 has 11 200 hash keys, HMU05 12 721, and
     **10 039 (89.6 %) are common** ⇒ a descriptor hash identifies the *same log statement* in both images.
     `xmap.py`'s `{u16 file_id; u16 msg_id; u32 hash}` model is a **mislabelling of `{packed, hash}`**; its
     purpose is sound.
   - **★★★ New instrument: `scratch/logsite.py`** attributes a decompiled function's log calls to
     `file:line` by reading `packed` from the ELF at each **literal descriptor VA** (Ghidra folds
     `immext(base)+add(#off)` into the literal, so the VA appears verbatim in the `.c` text) and using the
     resolved anchors + **table adjacency** for the file. **UZ801 69 775 sites; HMU05 68 186 sites.**
     Companions: `scratch/immbase.py` (immext histogram), `scratch/va2func.py` (VA → function; 81 800
     indexed). **A first version of the token regex (`c16[0-9a-f]{5}`) matched only UZ801's `0xc16xxxxx`
     and produced a confident zero on the stock arm (`0xc155xxxx`); fixed to `(?:0x|DAT_)([0-9a-fA-F]{8})`.**
   - **★★★ Doc 206 §9 item 2 is CLOSED — negative.** `cmcc.c` is reachable **identically** in both builds:
     UZ801 **13 functions / 90 sites**, HMU05 **12 / 89**, with a **1:1 site-count correspondence**
     (`FUN_c06917f0`↔`FUN_c066e9a4` 28/28; `FUN_c06928a8`↔`FUN_c066fa30` 13/13; `FUN_c0691f2c`↔`FUN_c066f0d8`
     17/17; …). And the sole entry `FUN_c06928a8` is referenced **only at its own definition** in UZ801 —
     **and so is stock's `FUN_c066fa30`**. ⇒ the no-static-caller property is **not a build difference**.
   - **★★★★ GROUP 11 is inert BY CONSTRUCTION.** `FUN_c06928a8` (UZ801) and `FUN_c066fa30` (stock) are
     **structurally identical** — same guard, same `0xff` alloc, same field offsets (`0x29c`, `0xa68`,
     `0x1509/0x150a/0x150f`), same registration IDs (`0x456`, `0x422`), same single tail call
     (`0xffffffff`), same cleanup — differing only in layout and the **+19** revision shift. GROUP 11
     patches `0xc0691950` *inside* that identical chain. **Do not deploy GROUP 11 again.**
   - **★ Doc 206 §9 item 1 is NOT met but is now bounded.** The `cmmsc_auto.c` descriptors are referenced by
     **no literal and no `immext` in *either* build** (0 of 69 775 / 0 of 68 186); `immbase` over
     `0xc165fd40..0xc165ff40` (UZ801) and `0xc155f700..0xc155f900` (stock) finds **zero** bases; all
     descriptor-table `immext` bases from the CM cluster are in the **`cmcc.c` region only**. The four
     descriptor VAs (both builds) are recorded in Doc 207 §7.2 so the next attempt starts from them.
   - **★ Method correction.** `re_index.py --callers` matches only `SYM(` and is **blind to
     function-pointer arguments** (`…, 0x456, FUN_c0691d9c, iVar2)`) — it reported "no callers" for a
     function registered twice as a handler. **Use a raw symbol grep for caller censuses.**
   - **★ The next attack.** (a) Take the `cmmsc_auto.c` question to the **runtime**: instrument the CM
     command dispatcher (`scratch/dispatcher_c067cd2c.txt` / `dispatcher_asm.txt`) so the `u=1, tsk=cm`
     command's handler sequence is observable, and compare. (b) Use the **descriptor hashes** as the
     cross-build key: for each statement stock emits at boot but UZ801 does not, find its hash in UZ801's
     table and scan for **any** reference to that descriptor VA from a **computed** base (immediates in the
     same 64-byte block, not only the exact VA).
   - **Do not** re-open the `cmcc.c`/GROUP 11 axis, the `0x456`/`0x422` search, the race, the `evt=0`
     patch, the `0x1000001` whitelist, or the `cm_state+0x39e8` byte.

14. **★★★★★ THE CM DISPATCHER IS IDENTICAL, THE F3 `code` FIELD IS NOT THE MASK, AND THE F3 BURST CLASS IS
    GONE (2026-09-26, Doc 208).** Three results and one blocker.
   - **★ The CM dispatcher is the same program.** UZ801 `FUN_c067cd2c` @ `0xc067cd2c` vs stock
     `FUN_c0659ef0` @ `0xc0659ef0` (found by grepping the stock decompile for the switch constant
     `0x100006a`, **not** by an address guess): 1096 normalised lines each; raw `fdiff` similarity
     **83.49 %** with 115 differing blocks, **every one of which is a `unaff_GP + 0x…` global-layout
     offset**; after normalising GP offsets **2 blocks remain, both the same `int *piVar;` declaration
     move**. ⇒ **"the CM code differs" is closed.** The `dcc` command's 0.66 ms drop must be
     command-content or CM-state — a runtime question. **A `fdiff` verdict must always be read with GP
     offsets normalised** (a GP-offset-blind diff reports two identical programs as 83.49 % similar).
   - **★★★ The stock arm used the IDENTICAL instrument, so the masks are matched.** `scratch/hmu05_stock_boot.log`
     opens `--- cntl-enable 64 SSIDs at uptime=12.98 ---` with 64 `F3_MASK` packets
     `ssid_first=ssid_last=0…63`, from the same `/root/diagboot_run.sh` at the same uptime as the UZ801
     arm. ⇒ every stock-vs-UZ801 file-set differential in this campaign is **mask-controlled**. Re-scored
     with the mask held fixed, the load-bearing negative is **strengthened**: stock's boot chunk
     (ts < 600 000) carries `cmss.c` 18, `cmmsc_auto.c` 3, `mmoc.c` 4, `cmlog.c` 6, `cmregprx.c` 7,
     `sdss.c` 6, `sdcmd.c` 5; UZ801's burst boot chunk carries **0** of them while delivering the *same*
     adjacent layers (`mmocdbg.c` 60, `cmdbg.c` 14, `mmocmmgsdi.c` 62, `a2_power.c` 8).
   - **★★★ The F3 record's `code` field is NOT the AP-selected SSID.** `cntl-enable N` provably sweeps
     SSIDs `0..N-1` (source-verified **and** wire-verified), yet on one boot `code` is **4772 for
     N = 1, 4, 64 and 256**. It varies only across **boots** (`4756, 4764, 4765, 4766, 4769, 4771, 4772,
     40240, 41261, 42100, 42140, 0`) ⇒ a **modem-assigned per-boot subsystem id**. **Rule: a capture's
     mask is read from its own `diagboot.log`, never from the F3 record.**
   - **★★★ BLOCKER — the F3 burst-capture class is gone.** **20 consecutive boots** are in Doc 203's
     **blind** class (`mmocdbg.c` = 0), across **three masks** (sweep-256; a new one-packet
     `cntl-enable-range` 256; and the original sweep-64) and **a fresh redeploy of the identical v19 set**.
     Falsified as causes: **the sweep width** (the sweep-64 control blinded 4/4 too — my own "a wide mask
     blinds" hypothesis died to its own control), **the enable speed** (the one-packet range tool blinded
     5/5), **the firmware bytes**, and **the modem's persistent partitions** (`modemst1`/`modemst2`/`fsg`/
     `fsc` byte-identical across a boot). The class flips in **blocks** (5 burst, then 20 blind;
     p ≈ 10⁻⁶ for a per-boot coin flip) with **no deploy, revert or manual reset** near the flip
     (2026-09-26 12:38 UTC). The blind class is missing exactly the **UIM/SIM-Toolkit bring-up**
     (`estk_bip.c`, `gstk*`, `nvruim.c`, `mcfg_uim.c`, `mmgsdi_session.c`, `mmocdbg.c`) — Doc 203 §6's
     correlate — while the card itself reads `present` / `usim (2) ready` / `PIN1 disabled`.
   - **★ The next attack, in order.** (a) **Restore the burst class** — nothing else in the F3-scored line
     can proceed without it. Leading hypothesis: an **AP-side timing window** (the class is a race between
     the modem's UIM bring-up and CM's first command, and the AP controls the EFS window through `rmtfs`;
     Doc 196 measured `rmtfs` 10.66 s vs modem power-up 10.94 s). Probe: vary the `rmtfs`/probe window and
     watch the `tsk` of the first `CMD alloc`. (b) Then execute Doc 207 §9 item 1 — instrument the
     dispatcher's `u=1` handler chain; the static half is done (the dispatcher is identical, so the
     question is **which case is reached**). (c) Doc 207 §9 item 2 (descriptor hashes as the cross-build
     key) remains open.
   - **★ New tool.** `diag_logtool cntl-enable-range [dev] [nssid] [span]` — one `F3_MASK` packet per
     `span` SSIDs; `span == 1` reproduces the original packet byte-for-byte; `span = 256` enables
     `0..255` in one packet. md5 `470df474ed9425aa2b62386a8ef0eab4`.
   - **Do not** read a capture's mask off the F3 `code` field; **do not** score a CM-layer negative on a
     blind-class capture; **do not** re-open the `cmcc.c`/GROUP 11 axis.
15. **★★★★★ THE BURST CLASS IS RESTORED, FIVE CAMPAIGN NEGATIVES ARE REFUTED, AND A POLICYMAN RAT-MASK-0 LEAD
    IS OPEN (2026-09-27, Doc 210).** Doc 208 §9 item 0's gating blocker is **cleared**; the campaign's
    central "the CM layer never runs" premise is **retracted**; and the boot path has a new, unclosed lead.
   - **★ The instrument fix (clears the blocker).** The DIAG-device poll in `diagboot_run.sh` /
     `diag_bind.sh` used `sleep 1`, so `cntl-enable` fired at **~13.18 s** — after the modem's boot burst.
     Polling at `sleep 0.05` fires it at **12.3–13.1 s** and catches the cold boot: **4/4 verified**, vs
     the campaign's **20/20 blind**. The class is a **race on the enable time**, not the sweep width
     (which Doc 208 had already falsified). Deployed `diagboot_run.sh` `59fb8e77961175ca4addd4161cdb63eb`,
     `diag_bind.sh` `4a13dfb86f9ea60e38b98a6883c9eb9e` (backups `/root/inst_backup/*.pre209`). The
     `diag_logtool.c` comment — *"the enable is a RACE against the modem's boot-time F3 history"* — is
     confirmed.
   - **★★★★ The restored class is DEEPER, and that is the load-bearing measurement.** Every campaign
     capture checked carries **`rcinit_init.c` = 0**; the restored class carries **76–171** (the firmware's
     own `group 4 mc_init` / `group 5 reg_init` / `mmoc_init`). The campaign caught the *middle* of the
     boot. `f3clean.py` was **checked and is not the cause** (it drops 101 of 1 887 messages, none
     load-bearing) — the difference is the capture. **⇒ the admissibility rule is corrected: `mmocdbg.c`
     > 0 is necessary but NOT sufficient; `rcinit_init.c` > 0 is the condition** (§ item 8).
   - **★★★★★ Five load-bearing negatives REFUTED** (all were "0 in all 22 UZ801 captures"):
     `cmss.c` **2**, `cmmsc_auto.c` **4**, `cmregprx.c` **5**, `sdss.c` **2**, `sdcmd.c` **1**,
     `mmoc.c` **6** (and `cmlog.c` still 0 — not claimed refuted). **The CM serving-system layer DOES
     run.** The `197 §3` row *"…the CM serving-system layer genuinely never runs"* is **RETRACTED**.
   - **★★★★ Doc 200 §4(b)'s "`cmmsc_auto` never runs" is REFUTED directly.** UZ801 emits the **exact stock
     sequence** at boot (idx 699–701), including `=CM= CMMSC_AUTO: updating op_mode …` — the very
     statement Doc 201 §5.3 named as stock-only. Its **timing** evidence (`dcc` 0.35–0.66 ms vs
     11.5–13.8 ms) is untouched and **stands** (re-measured live this session at **0.35 ms**).
   - **★★★★★ NEW ROOT-CAUSE LEAD — policyman ends the boot with `Filtered RAT mask 0`.** Verbatim for
     subs 0/1/2: `policyman_rat_capability.c:726 policyman_retrieve_rats_bands returned status 2,
     filesize 0` → `:98 can't populate RAT item from EFS` → `policyman_rf.c:592 can't populate RF item` →
     `policyman_ue_mode.c:122 can't populate UE mode Item` → `policyman_rat_capability.c:74 Filtered RAT
     mask 0 based on HW capabilities 544`. **A modem with RAT mask 0 has no RAT enabled: nothing to scan,
     nothing to register on.** The write-back also fails (`policyman_efs.c:334` + `efs rat & band write
     status = 2`) ⇒ **not self-healing on a later boot**. The policyman init is interleaved with
     **`rcinit` group 5** — which is exactly what the campaign's captures never reached, which is why the
     campaign never saw any of this.
   - **★★★ The EFS is NOT dead — two independent controls.** (a) `qmicli --wds-create-profile=…` →
     **`New profile created: Profile index: '3'`**, and it **persisted**; (b) NAS reads EFS fine
     (`file_enum 220, status:0`; `:PLMNWACT read_data_len169,size:5`; populated PLMN list). ⇒ **any
     explanation requiring a globally dead or read-only EFS is FALSIFIED**; the policyman failure is
     **item-specific**. A coherent **missing-NV cluster** sits behind it: `uim.c:7136 Read to NV active
     slot configuration unsuccessful`, `cmph.c Can't read ue_based_cw`, `txlm_hal.c:821 Mismatch in values
     read from nv : 0x0 …` (×27, an **RF** module reading NV and getting 0).
   - **★★★ A census-invisible CM divergence — ⚠ RETRACTED by Doc 211 §3.3.** `cmmsc_auto.c`'s first
     statement's second operand is **`0x220` on stock** vs **`0xebe` on UZ801** (`=CM= MSC_AUTO: 0x200 &
     0x%x = 0x200`; first operand and result identical in both). **`0x220` = 544** = exactly the value
     UZ801's policyman reports as its HW capabilities. A presence/absence census **cannot** see this — it
     is a *value* divergence. **Doc 211 measured stock emitting `0xebe` *first* and `0x220` *later in the
     same boot*** (`stock_boot_240.bin`, `stock_boot_211.bin`) — the archived online capture caught only
     `0x220` and the UZ801 captures only `0xebe`. It is **which invocation the capture happened to
     catch**, not a build difference. **Not a divergence.**
   - **★ What is NOT established, declared.** The policyman code is **string-identical** across the builds
     (56 vs 57 messages; only delta `policyman_uim.c:updating PLMN for Sub %d`), and the **stock arm's
     boot chunk is only its first 324 messages with no `rcinit_init.c`** ⇒ **the stock capture cannot
     answer whether stock's own policyman boot-time EFS read succeeds.** §5 of Doc 210 is therefore stated
     as a **UZ801-only** measurement, not a differential.
   - **★ The next step, single and decisive: capture a STOCK HMU05 boot on the restored instrument.** It is
     the only arm that can separate *shared with stock ⇒ benign* from *UZ801-only ⇒ causal*. Everything is
     in place: the sub-second instrument is installed and runs on every boot, the stock set is at
     `/overlay/fwbackup/hmu05_stock/` + `/overlay/fwbackup/hmu05_wcnss_tz/` on the device, the v19 set is
     verified byte-identical to `scratch/uz801_patched/` so it can be restored, and the bootloop guard is
     clean.
   - **Do not** score a CM-layer negative on a capture with **`rcinit_init.c` = 0**; **do not** re-open
     `cmcc.c`/GROUP 11, `0x456`/`0x422`, the `u=1` race, the `evt=0` patch, `0x1000001`,
     `cm_state+0x39e8`, or "the CM code differs"; **do not** order F3 messages by `ts` (use file/delivery
     order); **do not** cite `desc.py`.

16. **★★★★★ THE STOCK ARM WAS TAKEN, THE POLICYMAN LEAD IS FALSIFIED, A SECOND PARSER DEFECT WAS FOUND, AND
    THE FIRST CLEAN SAME-WINDOW DIFFERENTIAL IS IN HAND (2026-09-27, Doc 211).** Item 15's single decisive
    next step was executed; it both answered the question and exposed a measurement defect that had been
    distorting the numbers the campaign was reasoning about.
   - **★★★★★ The stock arm (`stock_boot_211.bin`, `admissible=yes`, `rcinit_init.c` = 76) reaches the
     policyman block — and emits the IDENTICAL failure — while reaching `online`.** Verbatim:
     `policyman_rat_capability.c:726 subs 0/1/2 … returned status 2, filesize 0` → `:98 can't populate RAT
     item from EFS` → `policyman_rf.c:592` → `policyman_ue_mode.c:122` → **`:74 Filtered RAT mask 0 based
     on HW capabilities 544`** → `policyman_efs.c:334 Error writing file` → `:907 efs rat & band write
     status = 2`. Stock's only extra record is `policyman_serving_system.c:1024 ss precondition checks
     fail, reset PRECOND_SS`. **⇒ Doc 210 §5's policyman lead is FALSIFIED: a filtered RAT mask of 0 is
     NOT why UZ801 stays offline.**
   - **★★★★★ The stock `online` control the campaign never had.** Stock restored (all four expected hashes
     matched) and booted: `--dms-get-revision` = `HIMI_U01_MODEM_V1.0`, **`Mode: 'online'`**,
     `--nas-get-serving-system` = `Registration state: 'registered'`, `Selected network: '3gpp'`,
     `MCC: '405' MNC: '861' Description: 'JIO 4G'`, `--nas-get-technology-preference` = `'3gpp, lte',
     permanent`. **Same hardware, same SIM, same EFS, same AP.**
   - **★★★★★ A SECOND `f3parse.py` defect: the args were read at the wrong offset and scaled by
     `256^extra`.** Doc 210 §3.4 fixed the *drop* but left `args` at `k+20+4*j`; the `extra` pad sits
     **before** the args, so the whole args block is shifted. Raw proof (`stock_boot_211.bin off=0x30d33`,
     `na=2`, `extra=1`): `args@k+20 = [9728, 0]` vs `args@k+20+1 = [38, 0]` for
     `=CM= mode_pref %d, pref_term %d`. Corroborated on three independent lines (`mode_pref` extra=2 →
     `2490368` vs **38**; `old_op_mode/new_op_mode` → `2560/2816` vs **10/11**; `NV hybr pref` → `256` vs
     **1**). **Fixed: `scratch/f3parse.py` md5 `9f9d6edf15d64faf3b47897f7838c9ac`** (pre-fix kept as
     `scratch/f3parse.py.pre211`). ~2 % of records, but **every stock `cmph.c mode_pref` record**. **Do NOT
     take an `args` value from any older parser.**
   - **★★★★★ The "`mode_pref` 9728 vs 38" differential is DEAD, and so is the `MSC_AUTO` one.** With the
     fix, `mode_pref` is **38 in every arm**; stock's own `cmsoa.c main mode_pref=%d` = 38 and `HICPS
     cm_mode_pref: %d, ph obj mode_pref: %d, ph_ptr mode_pref: %d` = `[39, 38, 38]` (both `extra = 0`)
     confirm it. Item 15's census-invisible divergence is **RETRACTED**. **Every CM-layer numeric is
     identical between the two firmwares** (`mode_pref` 38; `old_op_mode → new_op_mode` 10 → 11;
     `NV hybr pref` 1; `MSC_AUTO` `0x200 & 0xebe = 0x200`; `Filtered RAT mask [0, 544]`;
     `RAT_DISABLED_MASK [9,0,0]`/`[4,0,0]`) ⇒ **the CM *inputs* are byte-identical; the divergence is
     entirely downstream.**
   - **★★★★★ The first same-window differential (same device, EFS, instrument and 240 s window).** Stock
     `cmss.c` **18** / `cmlog.c` **6** vs UZ801 **0 / 0**; SD `sdcmd_name_sel3()` vs `hybr_name_sel()` +
     **`=SD= ** Activate user script = ssscr_user_offline_cdma **`**; MMOC `sess opn cnf: …card=%` vs
     **`…card_1=%` + `card_2 %`**; UZ801 runs a whole **MCFG-UIM / SIM-Toolkit** stack stock lacks
     (`mcfg_uim.c` ×9, `uimsub_manager.c` ×5, `uimgen.c`, `gstkutil.c` ×3) including a **failing**
     `UIM_%d: Read to NV active slot configuration unsuccessful`; MMOC `Prot_state` `0(NULL)` only vs
     `0(NULL)` → **`7(OFFLINE)`**. This **corroborates Doc 202 §6.3's** UIM/STK-bridge chain, which the
     fixed parser had cast into doubt.
   - **★★★★★ The sharpest single event: the MMOC decision.** Delivery order, `num_args = 0` (so **untouched
     by the parser defect**): both receive `=MMOC= Recvd command 2(OPRT_MODE_CHGD)` and log an identical
     sequence up to `Trans_state 0(NULL)`; stock then emits **`=MMOC= New transaction : 2(ONLINE)`** →
     `Trans_state 6(WAIT_PH_STAT_CNF)`, UZ801 emits **`=MMOC= New transaction : 3(OFFLINE)`** →
     `Trans_state 25(WAIT_DEACTD_CNF_GWL)`. The command is the same and CM's op mode is byte-identical ⇒
     **the decision is made inside MMOC** on inputs the CM log does not expose.
   - **★★ The F3 `ts` field is per-`code`.** `code = 4773` appears in **both** the stock boot and the UZ801
     boot, so `code` is a **per-subsystem** id; each `code` has its own `ts` base. **Never order F3
     messages by `ts` across codes** — this is the mechanism behind the apparent multi-thousand-second
     jumps in earlier docs.
   - **★★ A deploy trap.** `comm` is **not in the OpenWrt busybox**; the first `restore_uz801_v19.sh`
     aborted **after** the tar extraction but **before** the extra-file removal, leaving a **partial**
     firmware set. Replaced with `awk 'NR==FNR{a[$0];next} !($0 in a)'`. Same class as the Doc 197
     `$FILES` newline bug.
   - **★ UZ801 v19 restored and verified.** `restore_uz801_v19.sh` (md5 `51965239169cd8c048632f8678fef2d8`)
     → **68/68 manifest match, 0 extra files**; `modem.mdt 9f39ce579114bcf1383bbdce62a8d284`,
     `modem.b16 c71d53464a1e88f2072cbbb6f7f87475`; `--dms-get-revision` = `UZ801_V3.0_21_V01R01B10`;
     `Mode: 'offline'`; guard `boot_count` 0, no `BOOTLOOP_REVERT.txt`; fresh `admissible=yes` 240 s
     capture (`rcinit_init.c` = 110).
   - **★ The two gated next steps.** (1) **Step 1 EXECUTED from the captures in hand (Doc 211 §7.2):** the
     UZ801-only UIM/STK bring-up is a **tight ~113 ms burst** (`code = 4774`, `ts` 4 119 757 352 →
     4 119 870 260) whose **FIRST record is a FAILURE** — `uim.c:7136 UIM_%d: Read to NV active slot
     configuration unsuccessful` `args=[2, 0, 0, 0]` — after which `mcfg_uim.c:318 mcfg_uim_autoselect
     NV=%d` reads **`5`, `0`, `0`**, `mcfg_uim.c:1019 Opening ext session with mask 16` opens **1**
     session (`session_type 6`, `session_id 1037129215`), `uimsub_manager.c` registers the QMI sub manager
     and sets card status unknown, `uimgen.c:3331 Terminal Profile, select df is 0x0`, and
     `mcfg_uim.c:1287 ICCID 89918610400794228644`. **The failing subs id (2) is in the same `0/1/2` set
     policyman reports.** The `tsk` census also **weakens Doc 202 §6.3's literal claim**: `mmgsdi_1` is
     **3 in BOTH** windows; what UZ801 *uniquely* issues is `dcc` (8), `qmi_mmode` (2) and `pbm` (2), and
     what stock uniquely issues is `wms` (2) and `gsdi` (1). **Next:** a burst-vs-blind pair to separate
     cause from symptom. (2) An instrument that logs the **MMOC `OPRT_MODE_CHGD` payload / the MMOC session
     table**, because that is where the ONLINE-vs-OFFLINE decision is actually made.
   - **Do not** re-open the `mode_pref` 9728-vs-38 difference, the `MSC_AUTO` `0x220`-vs-`0xebe` build
     difference, or the policyman RAT-mask-0 lead; **do not** take an `args` value from a parser older than
     `9f9d6edf15d64faf3b47897f7838c9ac`.
17. **★★★★★ THE `OPRT_MODE_CHGD` CENSUS: STOCK RECEIVES IT TOO — THE 240 s PAIR IS PHASE-BLIND, AND THE
    MMOC NAME TABLES ARE RECOVERED (2026-09-27, Doc 212).** Item 16's "sharpest single event" was re-run
    against **every capture in the archive**; the differential survives, but its **scope** was wrong.
   - **★★★★★ `Recvd command 2(OPRT_MODE_CHGD)` is NOT UZ801-only.** Census (all `extra = 0`, so untouched by
     the parser defect): `stock_boot_240.bin` → `0(SUBSCRIPTION_CHGD)`×2, `4(PROT_REDIR_IND)`, `7(DUAL_STANDBY_CHGD)`,
     **no `OPRT_MODE_CHGD`**; `stock_boot_211.bin` → **identical**; `hmu05_stock_boot.bin` (94 MB, 33 743 msgs) →
     `0`×1, `1(PROT_GEN_CMD)`×15, **`2(OPRT_MODE_CHGD)`×1**; `uz801_v19_boot_240.bin` → `0`×1, **`2(OPRT_MODE_CHGD)`×1**,
     `4`, `7`; `boot_210_canon.bin` → same; `live_stock_online.bin` → `1(PROT_GEN_CMD)`×6. **Item 16's
     "both receive it" is CORRECT, but it compares stock from `hmu05_stock_boot.bin` against UZ801 from
     `boot_210_canon.bin` — two different instruments and windows.**
   - **★★★★★ A window is not a control.** In the clean 240 s same-window pair, stock shows **0**
     `OPRT_MODE_CHGD`, **0** `Curr_trans 3(OFFLINE)`, **0** `Prot_state 7(OFFLINE)` and **52** `0(NULL)` —
     because stock **has not yet transitioned** at 240 s (Doc 211 §4.5), while UZ801 transitions at **~15 s**
     of boot. The pair therefore compares **"stock before the transition" against "UZ801 after one"**.
     **Anchor the comparison to the EVENT, not the window.**
   - **★★★★★ The differential, event-anchored and now on TWO independent UZ801 captures.** The **same**
     command produces the **opposite** outcome: `hmu05_stock_boot.bin` idx 878 → `Curr_trans 0(NULL)` →
     `Trans_state 0(NULL)` → **`New transaction : 2(ONLINE)`** → `Trans_state 6(WAIT_PH_STAT_CNF)` →
     `Recvd report 2(PH_STAT_CHGD_CNF)`, ending at **`Prot_state [MAIN] 5(ONLINE_GWL)`** (the **only** capture
     in the archive that reaches it); UZ801 → `Curr_trans 0(NULL)` → `Trans_state 0(NULL)` →
     `Trans_state 25(WAIT_DEACTD_CNF_GWL)` → `Recvd report 0(PROT_DEACTD_CNF)` → **`Curr_trans 3(OFFLINE)`**,
     ending at **`7(OFFLINE)`**. ⇒ **the decision is inside MMOC, on the command's op-mode payload.**
   - **★★★★ Four candidate triggers RULED OUT.** (a) The `cmph.c` `rat_disabled_mask` sequence is 12 ticks
     before the command on **both** — but stock's 240 s boot contains **7** of them with **no**
     `OPRT_MODE_CHGD` after any; (b) the `dcc` break (item 16 / Doc 200 §8) is **downstream** — all 8
     `=CM= CMD free … tsk=dcc, ftsk=cm` records sit **after** the OFFLINE transition; (c)
     `policyman_rf.c:592 subs 0/1/2: can't populate RF item from EFS` is emitted by **stock too** (×3) —
     a **shared failure, not a differentiator**; (d) `trm_config_handler.c` is **identical** on both.
     The one surviving structural asymmetry is **`cmmsc_auto`**: stock runs the op-mode update **2×** in 240 s
     (`512 & 3774 = 512` then `512 & 544 = 512`, the second a no-op `11 → 11`) and **3×** in the long capture;
     UZ801 runs it **1×**.
   - **★★★★ The complete file census (70 UZ801 vs 65 stock).** UZ801-only (11): `mcfg_uim.c` 9,
     `uimsub_manager.c` 6, `mcpm_drv_mux.c` 3, `ale_proc.c` 3, `gstkutil.c` 3, `qmi_voice_msgr_if.c` 2,
     `uim.c` 1, `qpDcm.c` 1, `mgp_pe_common.c` 1, `uimgen.c` 1, `policyman_uim.c` 1. Stock-only (6):
     `cmss.c` 18, `cmlog.c` 6, `rf_task.c` 2, `rcinit_rex_task.c` 1, `gfc_qmi_internal.c` 1,
     `policyman_serving_system.c` 1. **`cmss.c` 18 vs 0 is the end state** (a consequence of not being
     online — 619/945 in the online capture). **`rf_task.c` 2 vs 0 is the only unexplained RF-axis
     difference** (stock logs `RF task rfm_init OK!` / `RF task new imei check !`; UZ801 logs no `imei`
     at all) — and it is **not diagnostic**: both run `policyman_rf.c`, `navrf_*` and `trm_config_handler.c`
     identically.
   - **★★★★ Task #13's instrument: the MMOC name tables are RECOVERED.** Absent from **every shipped
     firmware file** (0 hits in `scratch/uz801_fw/modem.elf`, every `modem.b*`, the deployed
     `/lib/firmware`, the HMU05 stock flash, the 64 MB FAT16 image, `melbon_black`; controls `mmocmmgsdi`
     21, `standby_pref`, `rcinit_init`, `OPRT_MODE_PWROFF` all pass) but **present in the modem coredumps**.
     Full tables recovered from `scratch/coredump_live/modem_coredump_up919.52.elf` file
     `0x4e37be8`–`0x4e38069` (seg `phdr[20]` VA `0x8ace6000` → flash `0xc4ec68xx` = **`phdr[26]`**,
     `filesz = 0x0`, `memsz = 0xaeb000`, **~80 % zeros, ~1 100 populated islands ⇒ runtime-populated**).
     Decoded and verified against **five** captures: `Recvd command` 0=`SUBSCRIPTION_CHGD`, 1=`PROT_GEN_CMD`,
     2=`OPRT_MODE_CHGD`, 4=`PROT_REDIR_IND`, 7=`DUAL_STANDBY_CHGD`; `Recvd report` 0=`PROT_DEACTD_CNF`,
     2=`PH_STAT_CHGD_CNF`, 5=`MMGSDI_CNF`; `Curr_trans`/`New transaction` 0=`NULL`, 1=`SUBSC_CHGD`,
     2=`ONLINE`, 3=`OFFLINE`, 4=`PROT_GEN_CMD`, 9=`DUAL_STANDBY_CHGD`, 12=`MMGSDI_INFO_IND`;
     `Trans_state` 5/6/14/25; `Prot_state` 0/5/7/9. **The whole UZ801 failure is one line:
     `Curr_trans 3(OFFLINE)` is reachable ONLY from `OPRT_MODE_CHGD` → `WAIT_DEACTD_CNF_GWL` →
     `PROT_DEACTD_CNF`.**
   - **★★★★ The instrument's NEGATIVE, which is the important part.** The tables are a **runtime-populated
     string blob, not an indexable array**: a raw search for a 4-byte LE pointer to `OPRT_MODE_CHGD`
     (`3f 68 6c 8b`) in the coredump → **0 hits**; runs of ≥10 pointers into `[0x8b6c0000, 0x8b6e0000)`
     → **0 runs**; runs of ≥8 pointers into `[0xc4e00000, 0xc4f00000)` in `modem.elf` → **0 runs**; the
     pointer-looking array at file `0x4e37bb3` targets `0xd0d777xx`, **outside every coredump segment**;
     `logsite.py anchors` reports **no `mmocdbg.c` anchor at all**; and the decompile has **0** hits for
     `old_op_mode`, `OPRT_MODE_CHGD`, `mmocdbg`, `gstkutil`, `mcfg_uim`. **The emit site cannot be found by
     string search.**
   - **★★ The payload is not logged, so the instrument task #13 asked for cannot be built from this
     firmware.** The `mmocdbg.c` site is `Recvd command %d(%s)` — id and name only; there is **no** `mmoc.c`
     parameter dump for the `oprt_mode_chgd` union member analogous to stock's `subsc_chgd` dump
     (`mmoc.c:10433/10436/10440/10442`, **present on stock, absent on UZ801**).
   - **★★ Task #12 (burst-vs-blind) RE-SCOPED, not abandoned.** In `code = 4774`, the UIM/STK burst
     **ends ~62 000 ticks before** the `OPRT_MODE_CHGD` (and ~50 000 before the first `SUBSCRIPTION_CHGD`):
     the burst is **upstream but not proximate**, so the question is now **build-composition**
     ("which UZ801-only module is load-bearing?") rather than capture-class (already settled by Docs 208/210).
   - **★★★ NEW: `PROT_GEN_CMD` is entirely ABSENT on UZ801.** `Recvd command 1(PROT_GEN_CMD)`:
     stock long capture **15**, `live_stock_online` **6**, stock 240 s **0**, **UZ801 0 (both captures)**.
     On stock the `OPRT_MODE_CHGD` at idx 878 **follows the completion of a `PROT_GEN_CMD` transaction**
     (`#862 Recvd command 1` → `#869 New transaction 4` → `#873 New transaction 0` → `#878 OPRT_MODE_CHGD`
     → `#885 New transaction 2(ONLINE)`). **This is consistent with Doc 200 §8's chain
     `cmmsc_auto → PROT_GEN_CMD → OPRT_MODE_CHGD → ONLINE → ONLINE_GWL`** and with the known fact that
     UZ801's AP `online` request dies in the `ds`/DMS task and never reaches CM. It sharpens the gate:
     the missing link may be **upstream in DMS**, not inside MMOC.
   - **★ The next highest-value single action** is **static from the CM side**: locate the CM function that
     assembles the `OPRT_MODE_CHGD` command and read the op-mode field it copies, entering via the `cmph.c`
     op-mode site (`cmph.c:32810` UZ801 / `cmph.c:32104` stock — note the **+706** line offset between
     builds) with `scratch/logsite.py refs … --file=cmph`, then walk to the MMOC enqueue.
     **⚠ ATTEMPTED 2026-09-27 AND BLOCKED BY A TOOLING DEFECT.** The descriptor IS findable
     (`{packed, word1∈seg25}` with `line=32810` → exactly one hit: **`descVA 0xc165d6b0`**, level 11,
     `'cmph.c:=CM= After updating from rat_disabled_mask mode_pref %d'`), but the decompile references it
     **0 times** — as it does **all 79** cmph.c anchor VAs and the whole `0xc165dxxx` range
     (`0xc165cxxx` has 118). `logsite.py refs` emits **69 775** "log-site literals" (mostly `?`) and **0**
     with `--file=cmph`, because its `packed` plausibility filter (`level ≤ 0xFF`, `0 < line < 200 000`)
     passes **~¾ of all u32 constants**. **⇒ the CM code addresses descriptors by a computed base+offset;
     the instrument needs a sound VA validation (in-table AND `anchors`-resolved). The cheaper path is
     now a coredump taken AT the OFFLINE transition.**
   - **Do not** conclude anything about `OPRT_MODE_CHGD` from a **240 s same-window pair**; **do not**
     re-open the `dcc` break as a *cause*, the `policyman_rf` EFS failure, or the `cmph rat_disabled_mask`
     sequence as the emitter.

18. **★★★★★ THE ONE-COMMAND BOOT DIFFERENTIAL: UZ801's SECOND CM→MMOC COMMAND IS `OPRT_MODE_CHGD` WHERE
    STOCK's IS `SUBSCRIPTION_CHGD` — AND THAT ALONE SETS `Prot_state [MAIN] 7(OFFLINE)` (2026-09-27, Doc 213).**

    This is the sharpest statement of the offline condition the campaign has had. **No deployment, no
    reboot, no device-side write this session** — device unchanged (UZ801 v19, boot_id `26d74096…`
    identical to the one recorded in `uz801_v19_boot_240.status`, `modem.mdt 9f39ce57…`,
    `modem.b16 c71d5346…`, guard clean, AP uptime 4 603 s).

    - **★ The census over six captures (all parsed with `f3parse.py` md5 `9f9d6edf…`).**

      | capture | `SUBSCRIPTION_CHGD` | `OPRT_MODE_CHGD` | `PROT_GEN_CMD` | `Prot_state [MAIN] 7(OFFLINE)` | `Prot_state [MAIN] 5(ONLINE_GWL)` | `CMD alloc tsk=dcc` | `cmmsc_auto` runs |
      | :-- | --: | --: | --: | --: | --: | --: | --: |
      | `hmu05_stock_boot.bin` | 1 | **1** | 15 | **0** | **54** | **1** | 3 |
      | `stock_boot_240.bin` | **2** | 0 | 0 | **0** | 0 | 0 | 2 |
      | `stock_boot_211.bin` | **2** | 0 | 0 | **0** | 0 | 0 | 2 |
      | `doc210/boot_210_canon.bin` (UZ801) | 1 | **1** | 0 | **26** | 0 | 0 | 1 |
      | `uz801_v19_boot_240.bin` | 1 | **1** | 0 | **26** | 0 | 0 | 1 |
      | `live_stock_online.bin` | 0 | 0 | 6 | **0** | **24** | 0 | 0 |

      **`Prot_state [MAIN] 7(OFFLINE)` is emitted 26× on UZ801 and 0× on every stock capture** — the first
      clean binary discriminator for the offline condition.

    - **★ The differential is ONE command id, and the transaction bodies are byte-identical.** Both
      firmwares run the same bring-up: `CM->MCS op_mode 10→11` (via `cmmsc_auto` mask `3774`) →
      `Recvd command 0(SUBSCRIPTION_CHGD)` 20 ticks later → `New transaction : 1(SUBSC_CHGD)` → short body
      (`WAIT_DEACTD_CNF_GWL → PROT_PH_STAT_ENTER → NULL`) → `Recvd command 7(DUAL_STANDBY_CHGD)` →
      `MMGSDI_INFO_IND(12)` → EF_SPN read at +1.5 s. The **only** difference is the **second** bring-up
      command's id: stock `0(SUBSCRIPTION_CHGD)`, UZ801 `2(OPRT_MODE_CHGD)`. The transaction body is
      identical (`WAIT_DEACTD_CNF_GWL → PROT_DEACTD_CNF → WAIT_PH_STAT_CNF → PH_STAT_CHGD_CNF ×3 →
      PROT_PH_STAT_ENTER → NEW 0(NULL)`) and only UZ801's terminal `Prot_state [MAIN]` becomes `7(OFFLINE)`.
      **⇒ MMOC's `SUBSCRIPTION_CHGD` handler does not touch `Prot_state [MAIN]`; its `OPRT_MODE_CHGD`
      handler sets it, and the value UZ801's command carries is `OFFLINE`.**

    - **★ Event-anchored timing (F3 clock 204 800 Hz, validated by two independent internal checks).**
      UZ801's `OPRT_MODE_CHGD` is **75 ms** after the boot `SUBSCRIPTION_CHGD` and **16.80 s BEFORE**
      ModemManager's `WMS_CMD_CFG_GET_ROUTES`; stock's is **0.50 s AFTER** the same routes event, at the
      end of the `dcc` command's lifetime. **⇒ UZ801's command is a firmware boot-path event; stock's is
      the AP's.**

    - **★ The `tsk=dcc` CM command is the carrier of the AP's online request.** `=CM= CMD alloc u=17,
      tsk=dcc` (idx 806, ts 3 040 010 292) is present **only** in `hmu05_stock_boot.bin` — the only capture
      that reaches `ONLINE_GWL` — and its lifetime brackets the whole transition (`cmmsc_auto` →
      `PROT_GEN_CMD` ×3 → `OPRT_MODE_CHGD` → `New transaction : 2(ONLINE)` → `free` at ts 3 040 012 172 →
      `ONLINE_GWL` at ts 3 040 013 116). Both 240 s stock boots and both UZ801 boots have **0 allocs**
      (UZ801 has **8 frees with `u=0`**). `tsk=dcc` is `dcc_task.c`/`dcc_task_svc.c`/`dcc_taski.c`
      (phdr[18] VAs `0xc18c0d48…`). **⚠ n = 1 — strong correlation with a named mechanism, NOT proven;
      do not cite as proven.**

    - **★ `PROT_GEN_CMD` — the queued question is ANSWERED: CONSEQUENCE.** `live_stock_online.bin` (a
      stock modem that **is** `ONLINE_GWL`) emits `PROT_GEN_CMD` **periodically at 6 144 4xx ticks =
      30.00 s** with `Prot_state [MAIN] 5(ONLINE_GWL)` and **0** `OPRT_MODE_CHGD`. ⇒ it is a routine 30 s
      protocol poll gated on the protocol being active; its absence on UZ801 is a consequence of being
      offline. **There are TWO `PROT_GEN_CMD` populations** — the 30 s poll, and a transition burst
      (stock-LONG's [811]/[835]/[862], 280/276 ticks apart, each with an `mmoc.c:13500 addl_action`).
      **Doc 212 §3.4 must be read with this correction.**

    - **★ `CM->MCS: op_mode` is 10 → 11 on ALL FOUR boot captures — identical on both stacks.** The
      earlier "UZ801-only" reading was an artefact of `f3clean.py` deleting `hmu05_stock_boot.bin`'s boot
      chunk. **A 0 on that capture is not evidence of absence.**

    - **★ `scratch/doc210/boot_210_canon.bin` is a UZ801 capture, not stock** (fingerprint `mcfg_uim.c` 9,
      `uim.c` 1, `uimsub_manager.c` 9, `uimgen.c` 1, `dcc` 8, `pbm` 2, `cmss.c` 1 vs stock's 0/0/–/0/0/0/18).
      **⇒ the differential is reproduced on TWO independent UZ801 boots.** Correct the label wherever used.

    - **★ NEW OPEN LEAD — the EF_SPN read's *effect* differs.** `qmi_nas_mmgsdi.c new ef spn size: 17 old
      cached size: 0 session: 0` occurs on **both** stacks with **identical args** at the **same ~1.5 s**
      offset. On stock it → `cmmsc_auto` **run 2** (mask `544`) in 2.17 ms → the second `SUBSCRIPTION_CHGD`
      in 58.6 µs. On UZ801 it triggers **nothing** — no run 2, no second `SUBSCRIPTION_CHGD`. It is **not**
      the cause of the OFFLINE (UZ801's `OPRT_MODE_CHGD` is 315 036 ticks **earlier**), but it is a second,
      independent divergence. Note stock's run 2 is a **no-op** (`old_op_mode 11 → new_op_mode 11`) and
      still emits a command ⇒ the **mask**, not the op-mode delta, selects the command.

    - **★ Cross-build `file:line` is INVALID.** `cmph.c` `CM->MCS: op_mode` is line **15052** on stock and
      **15285** on UZ801; `mmoc.c` `standby_pref` is **10433** on stock and **6752/10358** on UZ801
      (~3 700-line offset); `mmocdbg.c` is **291** vs **288**. Never compare `file:line` across builds.

    - **★ Route (a) — static — re-confirmed BLOCKED, with a new reason.** The MMOC debug format strings
      (`Recvd command`, `Trans_state`, `New transaction`, `Curr_trans`, `Prot_state`, `mmocdbg`) are **not
      in `modem.elf` at all** (0 hits) while the control `old_op_mode` **is** (VA `0xc4563df2`, phdr[25]);
      and the decompile materialises **zero** `0xc45xxxxx` addresses (387 743 `DAT_` refs, none in
      phdr[25]; 4 047 `s_…_c_<va>` names, none matching). **⇒ the payload is reachable only via route (b):
      a coredump taken AT the OFFLINE transition.**

    - **Next, in priority order:** (1) route (b) — a coredump at the `OPRT_MODE_CHGD` (it fires ~75 ms
      after the boot `SUBSCRIPTION_CHGD`, so the trigger must be the command itself); (2) find what makes
      CM choose `OPRT_MODE_CHGD` over `SUBSCRIPTION_CHGD` in that slot; (3) cheap independent test of the
      `dcc` hypothesis — trace the AP's QMI DMS `Set Operating Mode` on a stock boot and look for
      `CMD alloc tsk=dcc` within a second of `WMS_CMD_CFG_GET_ROUTES`; (4) correct the
      `boot_210_canon.bin` label.

    - **Do not** re-open: `cmcc.c`/GROUP 11, `0x456`/`0x422`, the `u=1` race, the `evt=0` patch,
      `0x1000001`, `cm_state+0x39e8`, "the CM code differs", the `mode_pref` 9728-vs-38 difference, the
      `MSC_AUTO` `0x220`-vs-`0xebe` difference **as a code difference** (it is a runtime mask), the
      policyman RAT-mask-0 lead, the `dcc` break as a *cause of the offline* (it is downstream),
      `PROT_GEN_CMD` as the online gate, or the `cmph rat_disabled_mask` sequence as the emitter.
      **Do not** compare `file:line` across builds; **do not** score a CM negative on
      `hmu05_stock_boot.bin`'s boot section.

19. **★★★★★ `tsk=dcc` IS THE AP's OP-MODE CARRIER — CONFIRMED WITH A NEGATIVE CONTROL; the UZ801 `dcc`
    DIES IN 0.35 ms vs STOCK's 9.18 ms; and the MMOC DEBUG STRINGS LIVE IN ELF SEGMENT 26 (`filesz=0`).**
    Source of truth: **Doc 214** (`214_THE_DCC_CARRIER_CONFIRMED_AND_THE_SEG26_MMOC_STRINGS.md`). This item
    **closes Doc 213 §12 step 3, retracts Doc 213 §6.3's reading of `u`, and re-scopes Doc 213 §10.**

    - **★ THE `dcc` CARRIER IS PROVEN (was n = 1).** `live_opmode_cap.sh` records `uptime` per step and ran
      `LIVE_SECS=45`; the capture's own last 45 s is **exactly 9 216 000 ticks** (`688 701 008 −
      679 485 008`), giving `ts = 679 485 008 + (uptime − 820.11) × 204 800`. With that map:

      | qmicli request | ts | nearest preceding alloc | delta |
      | :-- | --: | :-- | --: |
      | T1 `--dms-set-operating-mode=online` → **"set successfully"** | 680 713 808 | **`u=814, tsk=dcc`** | **−75.1 ms** |
      | T2 `--nas-set-system-selection-preference` | 681 950 800 | `u=820, tsk=qmi_mmode` | −77.9 ms |
      | **T3** `--dms-set-operating-mode=online` → **QMI client creation FAILED ("endpoint hangup")** | 683 187 792 | **NONE** | — |
      | T4 `--nas-set-system-selection-preference` | 684 015 184 | `u=830, tsk=qmi_mmode` | −80.2 ms |

      ⇒ **`tsk=dcc` carries the AP's QMI DMS `Set Operating Mode`; `tsk=qmi_mmode` carries QMI NAS
      `Set System Selection Preference`.** **T3 is the negative control** — no QMI client ⇒ no alloc.
      **This closes Doc 213 §6.3's n = 1 caveat.**

    - **★★ THE UZ801 `dcc` COMMAND ABORTS IMMEDIATELY.** `alloc u=814` @ 680 698 432 → `free` @ 680 698 504 =
      **72 ticks = 0.352 ms** (the history copy `u=131` is also 72 ticks). Stock's is **1 880 ticks =
      9.180 ms**, and *inside* that lifetime sit `cmmsc_auto` → `PROT_GEN_CMD` ×3 → `Recvd command
      2(OPRT_MODE_CHGD)` → `New transaction : 2(ONLINE)`, with `Prot_state [MAIN] 5(ONLINE_GWL)` 944 ticks
      after the free. **Ratio 26.1×.** ⚠ This does **not** re-open "the `dcc` break as the cause of the
      offline" — the offline is latched at boot ~826 s earlier, so the abort is downstream. What is new is
      that the carrier is *proven* and its abort is *measured*.

    - **★★ THE AP's ONLINE REQUEST IS ACCEPTED AND HAS NO EFFECT.** `live_cap.log`: baseline (uptime 820.07)
      `Mode: 'offline'`; **T1 → "Operating mode set successfully"**; post-check (uptime 865.40)
      **`Mode: 'offline'` still**. ModemManager independently reports the same fact:
      *"Requested (online) and reloaded (offline) modes did not match: Power update operation timed out"*.
      ⇒ **the AP is not failing to ask; the modem accepts the ask and does not act on it.**

    - **★★★★★ THE MMOC DEBUG STRINGS ARE IN ELF SEGMENT 26, WHOSE `filesz` IS 0 — Doc 213 §10's verdict is
      CORRECTED.** `mmocdbg.c` is absent from **all three** modem ELFs (HMU05 stock, UFI001B, UZ801) yet
      present in **every** coredump. The alignment is exact: **coredump VA + `0x39800000` = ELF VA**, proved
      because the coredump's 21 segment `filesz` values reproduce the HMU05 ELF's 21 `memsz` values in order,
      all 21. The strings sit at ELF VA **`0xc4ec6cxx`** (coredump `0x8b6c6cxx`), i.e. inside
      `seg[26] = va 0xc44e6000, filesz 0, memsz 13737984`. The extraction has `modem.b00`…`modem.b25` and
      **no `modem.b26`** — the missing `bNN` are exactly the `filesz = 0` headers. ⇒ **route (a) is blocked
      by a FIXABLE INPUT DEFECT, not by the absence of the data.** They are a **`(fmt, file)` pair table**
      plus the **enum name tables** (`SUBSCRIPTION_CHGD`, `OPRT_MODE_CHGD`, `ONLINE_GWL`, `OFFLINE`,
      `WAIT_DEACTD_CNF_GWL`, …) — a reusable offline decoder for every F3 ordinal.

    - **★ WHY THAT STILL DID NOT YIELD THE EMITTER.** No 4-byte LE reference to `0xc4ec6cxx` exists anywhere
      in the ELF — and neither does one to controls that *are* unambiguously referenced (`old_op_mode`
      `0xc44a3606`, `=MMOC= standby_pref` `0xc44a4c9f`). Both decompiles materialise **zero** tokens in
      `0xc45xxxxx`–`0xc51xxxxx` while materialising tens of thousands in `0xc15xxxxx`–`0xc18xxxxx`. The
      modem addresses seg24/25/26 **PC-/GP-relatively**, and Ghidra rendered them `unaff_GP + 0x…` because
      the segments had no file content to point into. ⇒ **next action is to rebuild the ELF with seg24/25/26
      content spliced in from a coredump, then re-run Ghidra (Doc 214 §12 step 1).**

    - **★ `u` IS A CM COMMAND ID, NOT UPTIME — Doc 213 §6.3 RETRACTED.** Stock: `1, 2, 17, 17, 17, 18, 18,
      18, 26, 54, 75, 107, 140, 173, 205, 238` — 8 increments over 2 759 s then 28 over 27.5 s. UZ801 boot:
      **`0` for every alloc in the whole capture**. UZ801 live: `131, 134, 814, 820, 830`. ⚠ The all-zero
      boot values are **open** (uninitialised field, or a different variable) — recorded so it is not
      mistaken for uptime again.

    - **★★ THE SHARPENED FORMULATION OF THE DIFFERENTIAL.** `Recvd command 2(OPRT_MODE_CHGD)` occurs on
      **both** stacks. Stock's is the **AP path** (inside the `dcc` lifetime) and creates transaction
      **`2(ONLINE)`** → `Prot_state [MAIN] 5(ONLINE_GWL)`. UZ801's is the **boot path** (+75 ms after the boot
      `SUBSCRIPTION_CHGD`, no `dcc`) and creates transaction **`3(OFFLINE)`** → `Prot_state [MAIN]
      7(OFFLINE)`. **The command id is identical; the target op-mode in the payload differs, and so does the
      actor.** ⇒ the emitter to find is **CM's op-mode source**, not two different MMOC handlers.
      (Doc 213 §3.3's "the command id is the variable" holds only for the *bring-up slot*.)

    - **★ THE TWO BOOT BRING-UPS ARE BYTE-IDENTICAL UP TO `Prot_state [MAIN] 0(NULL)`** (re-verified from the
      raw captures): same `cmmsc_auto` mask `3774` (`MSC_AUTO: 0x200 & 0xebe = 0x200`), same
      `old_op_mode:10 new_op_mode:11`, same `CM->MCS: op_mode=11` / `Set SxLTE simul cap` /
      `max_standby_subscriptions` / `sxlte_as_id`, same `trm_config_handler chain_mapping_modes New: 4,
      Old: 0`, same `mmocmmgsdi subsc_chg = 0x2c`, same `cmd_ptr->param.subsc_chgd.active_ss 1`, same
      `standby_pref 1 / active_ss 1 / device_mode 0`. Then stock waits for `DUAL_STANDBY_CHGD`; UZ801 emits
      `OPRT_MODE_CHGD` **20 ticks (98 µs)** after the `New transaction : 0(NULL)`.

    - **★ BOOT SOURCE-FILE CENSUS DIFF (new).** **13 files in the UZ801 boot and neither stock boot:**
      `ale_proc.c`, `gstkutil.c`, `mcfg_uim.c`, `mcpm_drv_mux.c`, `mgp_pe_common.c`, `policyman_uim.c`,
      `qmi_voice_msgr_if.c`, `qpCircularBuffer.c`, `qpDcm.c`, `qpIO.c`, `uim.c`, `uimgen.c`,
      `uimsub_manager.c`. **8 files in both stock boots and neither UZ801 capture:** `cmlog.c`,
      `gfc_qmi_internal.c`, `lsmp_api.c`, `nf_msg.c`, `policyman_serving_system.c`, `rcinit_rex_task.c`,
      **`rf_task.c`**, `sdp_core.c`. Also asymmetric: **`cmss.c` 18/18 stock vs 0/1 UZ801**; `sdcmd.c` 10/10
      vs 1/1; `sdss.c` 8/8 vs 2/2; `qmi_nas.c` 7/9 vs 84/84. **Candidate set, not a mechanism.**

    - **★ `logsite.py`'s hard-coded `SEG25` filter is the same bug class its own docstring warns about.**
      The `mmocdbg.c` strings are in **seg26**, so any seg25-restricted scan produces a **confident zero**.
      **Rule: print the searched range before scoring a "not present" verdict.** (seg26 holds no `file:line`
      strings at all — its table is `(fmt, file)` pairs — so `logsite.py`'s anchor model does not apply to it.)

    - **★ NEW TRAPS.** (a) **`grep -c` counts matching LINES, not occurrences** — a modem ELF is largely one
      line, so it returns 0 or 1 and never a true count; **use `bytes.count()` in Python**. (b) A capture's
      `ts` **span is not its duration** (`live_cap.bin`'s stream spans 787.58 s for a 45 s capture —
      `cntl-enable` does **not** give a clean window); anchor to the script's `uptime` lines and check
      `window == SECS × 204800`. (c) **`CMD alloc tsk=dcc` presence is capture-dependent** — boot captures
      show 0, the live capture shows several; only compare like captures. (d) **A QMI client-creation
      failure is a perfect negative control — use it.** (e) `filesz = 0` does **not** mean "empty at
      runtime".

    - **Next, in priority order:** (1) ~~**rebuild the ELF with seg24/25/26 content from a coredump and re-run
      Ghidra** — mechanical, and it unblocks route (a)~~ **⚠ SUPERSEDED BY ITEM 20 — this is the WRONG fix**
      (the references are in the native space `ELF VA + 0xbeb1000`, so seg26 content alone resolves nothing);
      (2) **obtain a UZ801 coredump** (the stock one fixes
      only the stock build; UZ801's seg26 is `memsz 11448320` at VA `0xc4615000`) — look for
      `/sys/kernel/debug/msm_subsys/modem`, and **never** use `echo stop >
      /sys/class/remoteproc/remoteproc0/state` (that reset the AP); (3) **re-capture the live UZ801 arm with
      `mmocdbg.c` demonstrably > 0** (the existing one is blind — 86 % of its records are
      `cfm_cpu_monitor.c`); (4) follow the **`cmss.c` 18-vs-0 asymmetry**; (5) correct the
      `boot_210_canon.bin` label.

    - **Do not** re-open (in addition to item 18's list): "`tsk=dcc` is unproven" (it is now proven),
      "`u` is uptime" (retracted), "the MMOC strings are absent from the firmware" (they are in seg26), or
      "the `OPRT_MODE_CHGD` command *id* is the differential" (it is the payload). **Do not** use `grep -c`
      for a string census on these images. **Do not** score an MMOC-layer negative on a capture with
      `mmocdbg.c` = 0.

20. **★★★★★ THE FIRMWARE'S DATA POINTERS LIVE IN A NATIVE ADDRESS SPACE (`native VA = ELF VA + 0xbeb1000`) —
    Doc 214 §5.5's "no reference exists" searched the WRONG SPACE, and the MMOC COMMAND-NAME TABLE IS IN THE
    IMAGE.** Source: **Doc 215**. This is a *tooling-model* correction with a concrete new asset.

    - **The correction.** Doc 214 §5.5 concluded "no 4-byte LE reference to the seg26 strings exists anywhere
      in the ELF" ⇒ "the modem addresses them PC-/GP-relatively". **True only in the ELF's link space.**
      Re-run in **native space** (`ELF VA + 0xbeb1000`), the identical search finds **5 hits in the ELF and 7 in
      the coredump** — `mmocdbg.c`, `OPRT_MODE_CHGD`, `SUBSCRIPTION_CHGD`, `=MMOC= %s`, `ONLINE_GWL` — **all in
      file-backed seg[18] (va `0xc1500000`) or seg[19] (va `0xc1c3c000`)**. Doc 215 §3.2.

    - **How the constant was solved (not assumed).** The 14-entry MMOC protocol-state name table at ELF VA
      `0xc4ec67e8` holds native pointers; fitting one constant to the whole run gives **`C = 0xbeb1000`** and
      matches **13/14** onto real NUL-terminated strings (`MC(CDMA-online)` … `GPS-MSBASED`). The one miss is a
      shared sentinel. Doc 215 §3.1.

    - **★ THE NEW ASSET — `mmoc_cmd_name[]`, complete.** At **ELF VA `0xc1d33eb0`** (file `0x1ca0eb0`,
      file-backed **seg[19]**, native base `0xcdbe4eb0`) sits a contiguous array of native pointers indexed by
      ordinal: **96 slots, 90 named**, 2 null holes, 7 slots sharing a sentinel. Index→name matches every
      observed F3 ordinal — `0 SUBSCRIPTION_CHGD`, `1 PROT_GEN_CMD`, `2 OPRT_MODE_CHGD`,
      `4 PROT_REDIR_IND`, `7 DUAL_STANDBY_CHGD`, `83 ONLINE_GWL`, `65/85 OFFLINE`. **Any MMOC ordinal can now
      be decoded offline.** Doc 215 §4; script `scratch/doc215_cmdname.py`.

    - **The `=MMOC= %s` descriptor.** ELF VA `0xc1566780` (seg[18]): `{line=291, level=20, len=28,
      fmt→native 0xd0d77c9e, file→native 0xd0d77ca8}`. `line=291` is the **stock** `mmocdbg.c` line (Doc 214:
      291 stock / 288 UZ801). The table then switches to an 8-byte form — **`logsite.py`'s "~8 % string pointer,
      92 % hash" model is confirmed, and the ~8 % now has a mechanism: native pointers.** Doc 215 §5.

    - **★ DOC 214 §12 STEP 1 IS FALSIFIED / SUPERSEDED.** Splicing seg26 content into the ELF cannot make a
      `0xd0d77cxx` reference resolve. The blocker is the **address-space mismatch**, not missing bytes. The
      correct fix is a **Ghidra memory block at the native VAs** (the image translated by `+0xbeb1000`) **or the
      PC-relative base value** — either is cheap. Doc 215 §6.

    - **The addressing mechanism (measured).** `llvm-objdump -d --triple=hexagon` shows a **PC-relative base
      register** (`r24 = pc` … `r25 = sub(r24,r26)`) plus `immext`-extended 32-bit constants; Ghidra renders the
      base `unaff_GP` (90 699×) with **small clustered offsets** (9 797 distinct in `0x10…0x12814` ⇒ a ~76 KB
      data window). **No instruction references the MMOC name table or its page by any absolute constant**
      (0 hits), so the emitter still needs the base resolved. Doc 215 §7.

    - **Still open, stated as limits.** (a) The **`OPRT_MODE_CHGD` emitter is NOT found** — the name table is
      data-only. (b) `0xbeb1000` is solved for the **stock** build only; **the UZ801 constant is unknown** and
      must be solved the same way (UZ801 links seg26 at `0xc4615000`, `memsz 11448320`).

    - **Next, in priority order (supersedes item 19's step 1):** (1) **resolve the PC-relative base** — add a
      native-VA memory block to the Ghidra program (or set the GP value), then xref the name table and the
      `Recvd command` format string; (2) **solve the UZ801 native constant**; (3) **diff the two builds'
      `mmoc_cmd_name[]` and descriptor tables** (both file-backed and now locatable — if the code is identical,
      a behavioural difference must live in data); (4) then Doc 214 §12 steps 2–5 (UZ801 coredump; a live UZ801
      capture with `mmocdbg.c` > 0; the `cmss.c` 18-vs-0 asymmetry; the `boot_210_canon.bin` label).

    - **Do not** re-open: "no reference to the MMOC strings exists" (it does, in native space), or Doc 214
      §12 step 1 as the fix. **Do not** score a "no reference" verdict without stating **which address space**
      was searched. **Do not** assume `0xbeb1000` transfers to the UZ801 build.

    - **★ ADDENDUM (Doc 215 §13, same session) — the working instrument, and the first CM→seg19 references.**
      (a) **Ghidra does NOT materialise the `immext` constants** — the decompile has **0** occurrences of
      `c1c3c8c0`, `c1d33eb0` or `c1566780`, while `llvm-objdump` resolves them all. ⇒ **Doc 214 §5.5's "the
      decompile materialises zero `0xc45xxxxx` tokens" is a Ghidra artefact, not evidence about the firmware.**
      The reliable instrument is the disassembly. (b) `scratch/doc215_consts.py` buckets every `##`-operand by
      segment: **167 764 refs into seg[18], 20 298 into seg[19]** (9 954 distinct). (c) **★★ Of those, 8 fall
      within ±0x2000 of the MMOC name table, and every referencing instruction is in `0xc067xxxx`–`0xc068xxxx`
      — the CM range** (`c0675640`, `c0676038`, `c06760a8`, `c067dfac`, `c0682250`, `c0682258`, `c0682ab8`,
      `c068a6b8`, `c068a814`, `c068f800`). CM directly indexes seg[19] tables adjacent to the MMOC name table,
      including a word-indexed table at `0xc1d348c0`. (d) The region `0xc1d33d80`–`0xc1d33eb0` is a second
      descriptor table of `{seg26 name, seg24 record}` pointer pairs. **No instruction references the name
      table's exact base, so the emitter is reached through these neighbours — the concrete next step is to
      disassemble `0xc0675000`–`0xc0690000`.**

      > **⚠ CORRECTED BY ITEM 21 (Doc 216, 2026-09-27).** Sub-clauses **(c)** and **(d)** above are **wrong**.
      > Reading the nine functions refutes the inference: they are **CM task-scheduler code** and their targets
      > are **CM scheduler globals that merely share seg[19]** — seg[19] is 3.1 MB, so "within ±0x2000 of the
      > MMOC table" is not a neighbourhood test. **The CM→MMOC adjacency lead is withdrawn**; the MMOC emitter
      > must be found by **module identity** (log strings), not by data adjacency. The region
      > `0xc1d33e08`–`0xc1d33eb0` is also **not** a "`{seg26 name, seg24 record}` descriptor table" — it is a
      > 14-record `{char* name, void* X, u32=0}` **protocol-name mapping**. See **item 21**.

21. **★★★★★ THE CM→MMOC ADJACENCY LEAD IS DEAD, THE MMOC PROTOCOL-NAME TABLE IS RECOVERED, AND seg[26] IS
    IN NO FILE.** Source: **Doc 216**. Two negatives and two assets.

    - **★ NEGATIVE — Doc 215 §13.3/§13.4's CM lead is a segment-proximity artefact.** The nine enclosing
      functions are **CM task-scheduler code**: `FUN_c068a77c` is a task main loop (`DAT_c28de948` wait-struct,
      `DAT_c28de958`/`d964`/`d960` signal masks, `FUN_c02c29f4` queue drain); `FUN_c068a688` a signal helper;
      `FUN_c0682aa0` a queue push; `FUN_c0682224` a 2-deep history; `FUN_c067df9c` a list-head init with a
      sentinel pointer. Their targets (`0xc1d33da0`, `0xc1d33db8`, `0xc1d348c0/c8/d0`, `0xc1d34900`) are **CM
      scheduler globals** that merely share seg[19] (3.1 MB). **Proximity inside a multi-megabyte data segment
      is not a reference.** Doc 215 §13.5's "concrete next step" is **withdrawn**. By-product:
      `0xc17f3880 = "mtask_orig_para_alloc"`, `0xc17f38a2 = "cm:ready"` (which is what fixes
      `FUN_c06755a0`'s identity), and `DAT_c1d33db8` holds `0x207`/`0x409`, consumed as opcode `0xe1`/`0x74`.

    - **★ NEW ASSET — the MMOC protocol-name table at `0xc1d33e08`–`0xc1d33eb0`** (file `0x1ca0e08`,
      file-backed seg[19]), **14 × 12-byte `{char* name, void* X, u32=0}`**, ending exactly where
      `mmoc_cmd_name[]` begins. **10 distinct names, `NAS(REG Task)` at `5,6,7,9,10`**: `MODE_INACT`,
      `MC(AMPS)`, `CDMA(MC Task)`, `GPS`, `HDR(HDRMC Task)`, `NAS(REG Task)`, `RESERVED`,
      `HYBR NAS(REG Task)`, `HYBR 3(REG Task)`, `Invalid`. **The indexing is UNKNOWN** (five NAS aliases ⇒ an
      index→name mapping, not a unique protocol list) and **`X` is unidentified** (binary data in seg[24]/
      seg[26], not names). Together with item 20's `mmoc_cmd_name[]` this covers both `%s` fields of the
      `mmocdbg.c` `Prot_state`/`Trans_state`/`Recvd command` lines.

    - **★ NEGATIVE (definitive) — seg[26] content exists in NO file.** Both builds have a `bNN` file for
      **every** phdr with `filesz > 0` and for **no** phdr with `filesz = 0`; **`modem.b26` does not exist**,
      and a raw search for `OPRT_MODE_CHGD` / `mmocdbg.c` / `SUBSCRIPTION_CHGD` / `Recvd command` across
      `modem_extracted/`, `GitIgnore/UZ801 Modem/` and `scratch/uz801_fw/` — **including both 64 MB FAT16
      partition images** — returns **zero hits in every file**. ⇒ seg[26] is a **fixed-address
      runtime-populated region** (base `0xc44e6000 + 0xbeb1000 = 0xd0397000`, which *is* the `+0xbeb1000`
      bias). **Doc 214 §12 step 1 / task #15 is CLOSED AS UNIMPLEMENTABLE — there is nothing to splice**, and
      **Doc 215 §9 step 2 is downgraded**: the UZ801 native constant cannot be solved without a UZ801
      coredump, because its pointer targets are unreadable offline.

    - **★ REFINEMENT — the F3 log descriptor is 16 bytes, and only the string form is recoverable offline.**
      `=MMOC= %s` @ `0xc1566780` = `{u16 line=291, u8 level=20, u8 len=28, u32=28, u32 fmt_native, u32
      file_native}`. **Measured 4-byte-LE occurrences of each MMOC log string's native pointer in the ELF:**
      `=MMOC= %s` **1**, `mmocdbg.c` **1**, and **0 for `Recvd command %d(%s)`, `Recvd report %d(%s)`,
      `Trans_state %d(%s)`, `Curr_trans  %d(%s)`, both `Prot_state [MAIN]` variants**. ⇒ those five use the
      **hash** form and carry **no pointer in the image**, so **Doc 215 §9 step 1's "xref the `Recvd command`
      format string" cannot work as stated** — the hash must be computed instead. Also: `0xd0d7783f` /
      `0xd0d77820` found as 4-byte-LE values are **entries of `mmoc_cmd_name[]`**, not log descriptors.

    - **★ The authoritative `mmoc_cmd_name[]` (96 slots).** ELF VA `0xc1d33eb0`: **91 named, 3 null (`11`,
      `61`, `95`), 2 unresolved (`64`, `94`)**, plus a shared non-string marker `0xd0dd533d` at `26`,`62`,`78`.
      Refines item 20's "90 named / 2 null" count; the **index→name map is identical**, incl.
      **`40 WAIT_SESSION_OPEN_CNF`** (the state Doc 202 §6.3 says UZ801 hangs in) and `83 ONLINE_GWL` /
      `65,85 OFFLINE`. Slots `0`–`60` are the forward table; `62`–`95` re-use names from it — **a decoder must
      know which region an ordinal came from**. Full listing in Doc 216 §8.

    - **★ The two builds' seg[19] layouts are near-identical** (Doc 215 §9 step 3, PARTIAL). Structural
      candidates at **matching relative offsets, shifted only `0x400`** (8 pairs). The `mmoc_cmd_name[]`
      relative offset does **not** transfer (`0xf7eb0` gives a valid table in stock, a 12-byte-record region in
      UZ801) ⇒ locate UZ801's table by signature. **A name-level diff is deferred** (no UZ801 coredump).

    - **★ DEVICE, 2026-09-27 — there is NO SIM OR FACILITY LOCK.** `AT+CLCK="FD"/"SC"/"PN"/"PU"/"PP"/"PC",2`
      → **all `0`**; `AT+CPIN?` → **`READY`**; QMI user-lock → **`no`**. **MM's `lock: sim-pin2` and
      `enabled locks: fixed-dialing` are MM misreports.** PIN2 `enabled-not-verified` is the normal unused-PIN2
      state and **does not gate registration**. Also re-confirmed: `AT+CFUN?` = `7`; `mmcli --enable` times out
      at **10.45 s** and MM then **disposes** the modem; `--dms-set-operating-mode=low-power` → *success* and
      the **next QMI CTL client allocation times out** (the modem reacts), then `=online` → *success*, mode
      still `offline`, **no reset**; `pc_state=0, pc_line=0` since boot ⇒ **the A2 line has never been
      asserted**. **The RF question is still open** — NAS cannot scan while `offline` (`Internal` in 0.02 s).

    - **Next, in priority order:** (1) ~~**★ obtain a UZ801 coredump** — now the single highest-value item~~
      **→ DONE 2026-09-27 by Doc 217 (item 22);** the coredump is captured and seg[26] is read. The device
      route note stands for the future: look for `/sys/kernel/debug/msm_subsys/modem`, **never**
      `echo stop > /sys/class/remoteproc/remoteproc0/state`; (2) **find the MMOC emitter by module identity**,
      i.e. compute the F3 descriptor **hash** for `Recvd command %d(%s)` rather than searching for a string
      pointer (Doc 216 §6 — there is no pointer to search for); (3) then item 20's remaining steps.
      **Note:** the F3 burst-capture class is **no longer a blocker** — Doc 210 §1/§3.1 restored it with a
      `sleep 0.05` DIAG-device poll (4/4), so item 20's step 3 and Doc 208 §9 item 0 are **superseded**; the
      gating item is now item 22's table hunt.

    - **Do not** re-open: the CM→seg19 adjacency lead (dead), splicing seg26 into the ELF (void), xref'ing the
      `Recvd command` format string (no pointer exists), or the PIN2/FDN blocker (ruled out). **Do not** read a
      pointer stored inside a name table as a log descriptor. **Do not** de-duplicate a record table while
      transcribing it — print it in address order first. **Do not** read ModemManager's `lock:` / `enabled
      locks:` fields as the modem's facility state.

22. **★★★★★ THE UZ801 COREDUMP IS CAPTURED — seg[26] IS READ, THE NATIVE CONSTANT IS CONTENT-VERIFIED, THE
    NAME-LEVEL DIFF IS DONE, AND A FOURTH AP-HANG SITE IS NAMED.** Source: **Doc 217**. This closes item 21's
    priority #1.

    - **★ THE ARTIFACT.** `scratch/coredump_uz801/uz801_coredump.elf`, **84 407 190 B**, md5
      **`59855a4cfac73e7dd2a4b54032ff5c42`**, ELF32 `ET_CORE` / `EM_NONE`, **21 program headers** (device copy
      `/root/uz801_coredump.elf`). Captured on the **second** attempt. **Every segment matches the UZ801 ELF
      phdr-for-phdr, including the three `filesz = 0` regions** — phdr 15 = seg[20] (`0x8884d000`,
      `0x142628` B), phdr 16 = seg[21] (`0x88990000`, `0x1baf600` B), phdr 20 = **seg[26]** (`0x8ae15000`,
      `0x00aeb000` B). **Every coredump `p_vaddr` = the ELF's `p_paddr`** and the sizes are `memsz`.

    - **★★★ THE RECIPE, AND WHY IT IS NON-OBVIOUS: `rmmod qcom_bam_dmux` BEFORE FIRING THE CRASH.** Fired with
      the module loaded, the AP **hung** (new `boot_id` `26d74096…` → `716548d1…`, PMIC PON WDT) and **no
      coredump was ever created**. `console-ramoops-0` ends at `[8858.706597] … executing serialized
      asynchronous SSR teardown`, i.e. **between `T4 state_lock acquired` and `T5 rx released` inside
      `bam_dmux_power_off()`** (`qcom_bam_dmux.c:1578`; `T5` at `:1623`, `T7` at `:1936` — neither printed).
      The idempotent fast-return (`:1585`) cannot fire because the device telemetry says `rx=held` (rx != NULL).
      **The block kills the coredump because patch 821 made the SSR path synchronous:** `flush_work(&dmux->
      ssr_teardown_work)` (`:2510`, with `T9 flush returned` at `:2511`) sits inside `bam_dmux_ssr_notifier_cb`'s
      `QCOM_SSR_BEFORE_SHUTDOWN` case, i.e. inside the SSR subdevice's `stop` callback → `rproc_stop_subdevices()`
      → `rproc_stop()` → `rproc_boot_recovery()` (`remoteproc_core.c:1792`), which is **upstream of
      `rproc->ops->coredump()` (`:1803`)**. So a teardown block means `rproc_stop()` never returns and the dump
      is never generated. **This is a FOURTH AP-hang site**, with an exact block window and a named propagation
      mechanism (the other three are in `project_ap_hang_and_capture`). The block is in one of: the idempotency
      check (`:1584`), `cancel_delayed_work_sync(&rx_rearm_work)` (`:1608`), `cancel_delayed_work(
      &rx_watchdog_work)` (`:1609`), `wait_event(rx_submit_wait,…)` (`:1612`), `dmaengine_terminate_sync(rx)`
      (`:1617`), or `dma_release_channel(rx)` (`:1618`).

    - **★★★ THE A/B, AND THE `rmmod` SURPRISE.** Loaded → hang, no dump; **unloaded → dump, AP alive**
      (`boot_id` unchanged, `state_after: running`). And `rmmod qcom_bam_dmux` is **safe**: `bam_dmux_remove()`
      (`:2665`) ends with `disable_irq(pc_irq)` then, under `state_lock`, `bam_dmux_ssr_teardown(dmux)`
      (`:2712`) — the *same* function that blocked on the SSR path — and it completed in **~6 ms**
      (`T5 rx released` → `T6 tx released` → `T7 power_off returned`, `rmmod_rc=0`, `boot_id` unchanged,
      uptime 490.42→490.57 s). ⇒ **the block is specific to the SSR/notifier context, not intrinsic to the
      function**, and **the recorded hazard "`bam_dmux_remove()` … will hang a future `rmmod`" is WITHDRAWN**
      (the ABBA it described was the *synchronous* watchdog cancel, now non-blocking at `:1609`).

    - **★★ THE SYNTHETIC CRASH TAKES THE SAME PATH AS A REAL FATAL.** `crash` write →
      `rproc_report_crash()` (`remoteproc_core.c:2703`) → `queue_work(rproc_recovery_wq, &rproc->crash_handler)`
      → `rproc_crash_handler_work()` (`:1864`, `state = RPROC_CRASHED` `:1885`) → `rproc_trigger_recovery()`
      (`:1892`) → `rproc_boot_recovery()` (`:1792`): `rproc_stop(rproc, true)` (`:1798`) →
      **`rproc->ops->coredump(rproc)` (`:1803`)** → `rproc_start()` (`:1813`). Also
      `qcom_q6v5_request_stop()` (`qcom_q6v5.c:205`) returns 0 immediately when `state != RPROC_RUNNING`, so
      the 5 s SMP2P stop dance is skipped; and `q6v5_mba_reclaim()` sets `dump_mba_loaded = false`
      (`qcom_q6v5_mss.c:1246`), so `qcom_q6v5_dump_segment()` (`:1533`) reloads the MBA and transfers memory
      ownership before `memremap()` — **the dump does not depend on the modem having crashed.**

    - **★★★ THE UZ801 NATIVE CONSTANT IS SOLVED AND CONTENT-VERIFIED.** `native VA = ELF VA + 0x0beb1000`
      (= coredump VA + **`0x456b1000`**), with `ELF VA = coredump VA + 0x39800000` on **all 21 segments** —
      **identical to stock**, because it is a property of the MPSS runtime map, not of the firmware. This
      supersedes task #18's `NOT MET` and Doc 216 §5's "cannot be solved without a UZ801 coredump".

    - **★★★ seg[26] IS CAPTURED, AND IT HOLDS THE MMOC STRING POOL.** From coredump VA `0x8b7f8230`
      (ELF `0xc4ff8230`, native `0xd0ea9230`) to `0x8b7f8b5c`: **119 NUL-terminated strings, 106 name-like.**
      Contents: `MMOC:ready`, the 10 protocol names (`MODE_INACT`, `MC(AMPS)`, `CDMA(MC Task)`, `GPS`,
      `HDR(HDRMC Task)`, `RESERVED`, `HYBR NAS(REG Task)`, `HYBR 3(REG Task)`, `Invalid`), ~90 command names
      (`SUBSCRIPTION_CHGD`, `PROT_GEN_CMD`, `OPRT_MODE_CHGD`, `WAIT_SESSION_OPEN_CNF`, `ONLINE_GWL`,
      `GPSONE_MSBASED`, `DORMANT_GWL`, …), and every `=MMOC= …` log format. Pre-rendered variants also exist in
      seg[21]. **Doc 216 §5's "seg[26] content exists in NO file" is superseded** — true of the firmware
      artifacts; the coredump *is* the file.

    - **★★★ THE NAME-LEVEL DIFF IS DONE** (it needed the **stock** coredump too —
      `scratch/coredump_live/modem_coredump_up919.52.elf`, 85 398 475 B — because the stock
      `mmoc_cmd_name[]` pointers land in the stock's `filesz = 0` seg[26]). **72 of 75** stock command names are
      present in UZ801; **3 are absent** — **`IMS_DEREG_CNF`, `PS_DETACH_ENTER`, `WAIT_IMS_DEREG_CNF`** (an
      IMS-dereg / PS-detach reduction; `PS_DETACH_CNF` survives while `PS_DETACH_ENTER` does not, so a *state*
      was dropped). **All 10 protocol names are present in both with identical exact-occurrence counts**
      (`GPS` 9/9, `Invalid` 3/3, rest 1/1). **`SUBSCRIPTION_CHGD` IS present in UZ801** (`cd 0x8b7f8508`), so the
      recorded `0(SUBSCRIPTION_CHGD)` vs `7(DUAL_STANDBY_CHGD)` divergence is **not** a missing name and the
      Doc 202 §6.3 chain stands; `WAIT_SESSION_OPEN_CNF` (Doc 202 §6.3's hang state) is present in **both**.

    - **★★★ OPEN PROBLEM — THE UZ801 POOL HAS ZERO POINTER REFERENCES.** A whole-image search for 4-byte words
      equal to each pool string's address in **three encodings** (native `cd+0x456b1000`, ELF-space
      `cd+0x39800000`, raw `cd`) returns **0 hits** — even for `MODE_INACT` / `WAIT_SESSION_OPEN_CNF` (1 copy
      each). The 44 words before the pool (`cd 0x8b7f8180`) are **function pointers** into seg[18]. By contrast
      the **stock** `mmoc_cmd_name[]` (ELF `0xc1d33eb0`) holds native pointers `0xd0d77820`…`0xd0d77c92` that
      **do** resolve against the stock coredump's seg[26]. ⇒ **locating the UZ801 `mmoc_cmd_name[]` / protocol
      tables is now priority #1.**

    - **★★★ BUILD PROVENANCE LEAKED BY THE IMAGE.** **128 distinct source paths**, all under
      `/home/chiyong/UZ801_V3.0-21/msm8939-la-1-0-2-1_amss_qrd_no-l1-src/modem_proc/`. Subtree histogram:
      `uim/mmgsdi` 34, `hdr/cp` 29, `uim/gstk` 18, `mmcp/policyman` 12, `uim/estk` 6, `datamodem/3gpp` 5,
      `uim/uimdrv` 4, `rftech_cdma/common` 3, `rfa/rf` 3, `core/mproc` 2, plus singletons. Notes: dir is
      `UZ801_V3.0-21`; package is **MSM8939** (`msm8939-la-1-0-2-1`); package is **`no-l1-src`** — a lead for why
      the `lte_ml1_*` 900 s fatal family has never been resolved statically. (Full list:
      `scratch/coredump_uz801/buildpaths.txt`.)

    - **★★★ RECOVERY WAS CLEAN (attempt 2).** `q6v5-trace 05..23` → `MBA booted without debug policy, loading
      mpss` → `remote processor 4080000.remoteproc is now up` → `wwan0at0/at1/qmi0 attached`; `boot_id`
      unchanged, `state_after: running`, UZ801 firmware intact, no `modem-guard` revert. The device was
      afterwards restored with `modprobe qcom_bam_dmux` (8 `wwan` ifaces; modem `CMD_OPEN` received).

    - **★ THE INSTRUMENT (from the tracked kernel tree).** `/sys/kernel/debug/remoteproc/remoteproc0/` holds
      `name` (0400), `recovery` (0600), **`crash` (0200 — the TRIGGER)**, `resource_table` (0400),
      `carveout_memories` (0400), `coredump` (0600 — a **CONFIGURATION**: `disabled`/`enabled`/`inline`,
      refused while `state == RPROC_CRASHED`). `dev_coredumpv()` data is auto-freed after **5 minutes**.
      `/sys/kernel/debug/msm_subsys/` does **not** exist on this kernel.

    - **Next, in priority order:** (1) **★★★ locate the UZ801 `mmoc_cmd_name[]` / protocol tables.** Two
      candidate tests: (a) the table is **outside the 21 captured segments** (check the UZ801 ELF for
      loader-populated regions that are not phdrs; consider a second dump with `coredump = inline`), or
      (b) it is **offset-encoded** — search for a run of u32 values equal to the pool's string offsets
      (`0x00, 0x0B, 0x16, 0x1F, 0x2D, 0x31, 0x41, 0x4A, 0x5D, 0x6E` for the first ten). (2) **Settle the
      T4→T5 block** — instrument the SSR path with `T4a`/`T4b`… probes between `qcom_bam_dmux.c:1608` and
      `:1618` (five `dev_err`, ≈40 ms) and re-fire; **do not** patch `bam_dmux_power_off()` blind (the `rmmod`
      result shows the function is fine in isolation). (3) Use seg[26] for the **MMOC emitter hunt** —
      cross-reference the pool's runtime **native** addresses against the F3 descriptor tables (pointer-form
      ~8 % vs hash-form). (4) Re-read UZ801's own boot-log strings from seg[26] and compare with the stock
      coredump's seg[26]. Then item 21's step (2) and item 20's remaining steps.

    - **Do not** re-open: splicing seg26 into the ELF (there is no `modem.b26`; the coredump is the source),
      xref'ing the `Recvd command` format string (no descriptor pointer), or the PIN2/FDN blocker. **Do not**
      conclude a name is absent from a **pool-window** extraction — decide presence/absence by **exact
      NUL-terminated token search over the whole image** (a pool window can contain a binary gap). **Do not**
      assume a documented `rmmod` hazard still applies — verify against the current tree (this one was stale).
      **Do not** read a coredump phdr's `p_offset` as 4-aligned; test **VA** alignment (`(p_vaddr + i) % 4`),
      because seg[26] has `off % 4 = 2` and `va % 4 = 0`. **Do not** mis-unpack the ELF header: `e_phoff` @28,
      `e_phentsize` @42, `e_phnum` @44 (reading @28 as `e_phentsize` was a real bug this session).

23. **★★★★★ THE UZ801 MMOC NAME TABLE IS NOT IN THE IMAGE — three tests, one bounded negative, and the
    stock control that proves the method works.** Source: **Doc 218**. This **exhausts item 22's priority #1.**

    - **★★★ Test 1 — three-encoding pointer search: 0 hits.** For `MODE_INACT`, `WAIT_SESSION_OPEN_CNF`,
      `ONLINE_GWL`, `OPRT_MODE_CHGD`, `DUAL_STANDBY_CHGD`, `SUBSCRIPTION_CHGD`, `MMOC:ready`, `Recvd command`
      (and every copy of `GPS`, `Invalid`, `RESERVED`, `PROT_GEN_CMD`), search all 21 segments for a 4-byte LE
      word equal to the string's **native** (`cd+0x456b1000`), **ELF-space** (`cd+0x39800000`) and **raw** `cd`
      address, testing **VA** alignment. **0 hits for every target in every encoding.**

    - **★★★ Test 2 — offset-encoded table: 0 runs.** (a) **Absolute-offset form:** 0 runs of ≥8 **distinct,
      strictly increasing** words equal to pool string offsets. (b) **Base-independent form:** 0 runs whose
      consecutive **differences** match the pool's offset-difference pattern for a 24-name stretch from
      `SUBSCRIPTION_CHGD`.

    - **★★★ Test 3 — the decisive count: 3, at chance level.** Words in the command-name range
      `[0xd0ea9508, 0xd0ea9b60)` (1 624 B): `0xd0ea9816` @ `cd 0x8a6a2408`, `0xd0ea977c` @ `cd 0x8a78a494`,
      `0xd0ea9568` @ `cd 0x8ab2aa9c` — **all phdr 18, all mid-string**. Expected by chance ≈ **1.4** for that
      range given seg[26]'s pointer density.

    - **★★ Test 4 — not "outside the captured segments".** The UZ801 ELF has **27 phdrs** vs the coredump's
      **21**; the extra six are `type 0` (`phdr[0]`, `phdr[1]`) + four `filesz = memsz = 0` placeholders.
      `phdr[1]` (`va 0x8b900000`, `filesz 0x1c88`) has content and is the **MBN attestation header**
      (`Generated Test Root CA`, `General Use Test Key (for testing only)`, `SW_ID`/`HW_ID`/`OEM_ID`/
      `MODEL_ID`/`SHA256`, `http://crl.qdst.com/crls/qctdevattest.crl`) — **not** a table.

    - **★★★ The POSITIVE CONTROL — stock has the table.** `mmoc_cmd_name[]` @ stock ELF **`0xc1d33eb0`**
      (file `0x1ca0eb0`, file-backed seg[19]) = **96 native pointers** (`0xd0d77820`…). Resolved against the
      **stock coredump** (`scratch/coredump_live/modem_coredump_up919.52.elf`, 85 398 475 B): slot 0 →
      `SUBSCRIPTION_CHGD`, 1 → `PROT_GEN_CMD`, 2 → `OPRT_MODE_CHGD`, 3 → `WAKEUP_FROM_PWR_SAVE`, 4 →
      `PROT_REDIR_IND`, 5 → `PROT_HO_IND`, 6 → `MMGSDI_INFO_IND`, 7 → `DUAL_STANDBY_CHGD`, 17 →
      `1XCSFB_PROT_DEACTD_CNF`, 18 → `DS_STAT_CHGD_CNF`. **Same method, opposite outcome.**

    - **★★ The negative is not an encoding artefact.** **878** aligned words in one 1 MB window
      (`[0xd0e00000, 0xd0f00000)`) are native pointers into seg[26], of which **99** resolve to printable
      strings ⇒ the native encoding is genuinely in use and searched.

    - **★ The pool's runtime structure.** `cd 0x8b7f8100..0x8b7f8b60` reads as
      `[struct-pointer array → cd 0x8a9exxxx][protocol names + short messages][14-entry pointer array][command
      names][log formats + 3 NV item paths]`. The **14-entry** array (`cd 0x8b7f84d0`) holds `0xd0cb9444`,
      `0xd0cb9454`, `0xd0cb9465`, `0xd0cb9475`, `0xd0cb9494`, `0xd0ce8de9`, `0xd0cb94b1`, `0xd0cb949d`,
      `0xd0cb94b1`, `0xd0cb9486`, `0xd0cb94b1`, `0xd0cb94b1`, `0xd0cb94b1`, `0xd0cb94a5` — and **every one of
      their targets (`cd 0x8b608430..0x8b6084cc`) is `0x00000000`**. **14 is exactly the size of stock's
      protocol-name table.**

    - **★★★ Interpretation.** The strings exist and are used (`Recvd command %d(%s)` prints them), yet nothing
      points at them. Two non-exclusive mechanisms remain: **(1) a runtime-populated table** — the zeroed
      14-pointer array is the signature of a table allocated but never populated, and the dump was taken with
      the modem `offline`; **(2) a hash/ID lookup** — as the F3 descriptors use (Doc 216 §6, ~92 % hash form).
      **What is RULED OUT is the campaign's working assumption** that a `mmoc_cmd_name[]`-style pointer array
      can be located in the UZ801 image by its references. **Consequence: the "any MMOC ordinal can be decoded
      offline" asset (item 20) is STOCK-ONLY.**

    - **Next, in priority order:** (1) **do not re-attempt the pointer/offset hunt** — it is exhausted; (2) if
      the ordinal map is needed for UZ801, the only routes left are a coredump taken with the modem **further
      through bring-up** (blocked while the modem cannot leave `offline`) or a **runtime** read; (3) the
      **emitter hunt** (item 22's step 3) can still use the pool's **native** addresses against the F3
      descriptor tables, but must expect the **hash** form; (4) item 22's T4→T5 instrumentation is unaffected
      and remains the next device-side step.

    - **Do not** re-open: the pointer-table hunt (0 hits, three encodings), the offset-table hunt (0 runs, two
      forms), or "the table is outside the captured segments" (the extra region is the MBN attestation header).
      **Do not** score a 32-bit data word as a pointer without stating the **chance level** for its range —
      the 3 hits are mid-string and ≈1.4 were expected. **Do not** conclude "stock-like" behaviour from a
      stock-only asset.

24. **★★★★★ THE DIAG TOOLING IS NOW THREE SHIPPABLE PACKAGES — `diag-bind`, `diag-efs` (with a guarded
    write), `diag-logtool` (2026-09-28, Doc 219).** The campaign's DIAG instruments existed only as loose
    scripts under `scratch/`, which `.gitignore` excludes — so every reader had to re-derive the bridge, the
    EFS2 transport and the F3 mask plumbing. They are now packages under `packages/`
    (`scripts/openwrt-prepare.sh:189-200` copies them into `openwrt/package/msm8916/`), all
    `GPL-2.0-only`, `1.0-r1`:

    - **`diag-bind`** (`DEPENDS:=+kmod-qcom-rproc-modem`) — the bridge helper `/usr/sbin/diag-bind`
      (idempotent; binds `remoteproc0:smd-edge.DIAG` / `DIAG_CNTL` to `rpmsg_chrdev`, creating
      `/dev/rpmsg0` + `/dev/rpmsg1`), **plus `/usr/sbin/diag-bind-watch` and a procd service** that
      re-binds across an SSR. **This is the answer to "what does boot-time auto-bind buy":** the binding
      lives on an rpmsg device object that an SSR destroys, so a one-shot boot bind goes silent at the
      first ~900 s restart. The watcher polls every 2 s and re-binds only when the device is present but
      **unbound**, so a capture holding `/dev/rpmsg0` open is never disturbed; every (re)bind is logged
      and written to `/root/diag-bind.status`. **Verified: manual unbind → re-bound in ~6 s.** The
      re-bind re-opens the `rpmsg_ept_cb` `priv == NULL` window guarded by kernel patch **819** (present).
    - **`diag-efs`** (`DEPENDS:=+diag-bind`) — `/usr/bin/diag_efs`: `list`/`read`/`stat`/`get`/`detect`
      over EFS2 (subsystem `0x13`) on `/dev/rpmsg0`, letting the modem decrypt its own EFS on the fly —
      the decrypted-NV instrument (the read side was derived from `136`/`196`'s transport; the write side
      is new here). **It now has a write path**: the header
      documented `WRITE=5` but the code defined `EFS_READ5 5`, so there was no write at all. Reverse-
      engineered live: **`4b 13 05 00 │ fdata(u32) offset(u32) │ data[…]` — there is NO `nbytes` field;
      the length is the message length** (sending `nbytes` made the modem read it as the offset). The
      write commits on **CLOSE**; the open flags are the POSIX values (`O_WRONLY 0x1 O_RDWR 0x2
      O_CREAT 0x40 O_TRUNC 0x200 O_APPEND 0x400`; `0x241` = create+truncate = the `put` default; `0x240`
      → err 9). `put` refuses `/rfnv/` and `/mcs/` without `--force`, backs up an existing target to
      `<sanitised>.bak.<epoch>`, writes in ≤1024 B chunks, and always verifies by read-back
      (`MATCH`/`MISMATCH`). **Validated: modify → MATCH → restore → MATCH, final file byte-identical**
      (`conf/hdrmac_config_info.conf`, md5 `50724a0a183e3190bf4c465549e3fa64`).
    - **`diag-logtool`** (`DEPENDS:=+diag-bind`) — `/usr/bin/diag_logtool`, the F3/log-mask/capture
      instrument of Doc 196.
    - **UNEXPECTED, and deliberately NOT implemented:** **EFS2 `REMOVE` is sub-command `8`**
      (`4b 13 08 00 │ path\0`), found by probing while cleaning up the 13 write-test files. It is a
      destructive capability next to the `path_is_cal()` guard, so it is documented (Doc 219 §8) but not
      exposed. **Do not** add a `remove` verb to `diag_efs` without a re-scope.
    - **Build trap, recorded:** `make package/<name>/compile` can print *"Nothing to be done for
      'compile'"* when a stale `$(STAMP_BUILT)` exists and `$(PKG_BUILD_DIR)` is gone. That line is
      **not** evidence the package built — use `make package/<name>/{clean,compile}`.
    - **Landing:** the three packages are pushed as **one commit** to `test/pure-software-modem`; `main`
      gets the packages **plus** the `msm89xx/image/msm8916.mk` `DEVICE_PACKAGES` edit. Doc 219 and this
      ledger section stay **uncommitted in the working tree**, as Docs 195–218 do.

25. **★★★★★ THE SIM-INIT CM COMMAND'S LIFETIME IS THE CLASS-INDEPENDENT, ZERO-OVERLAP DIFFERENTIAL
    (2026-09-28, Doc 220).** The campaign's target — the `u=1, tsk=cm` CM command named by Doc 202 §6.1 —
    was never *measured*, only *compared by record presence*. Measuring its **alloc→free `ts` delta** in
    the two arms gives the sharpest separation the campaign has produced:

    ```
    UZ801   u=1 tsk=cm lifetime:  276, 280, 280, 280, 284, 284, 292, 296, 312, 340, 348   (11 captures)
    stock   u=1 tsk=cm lifetime:  14 816, 14 852                                          ( 2 captures)
    max(UZ801) 348  <  min(stock) 14 816          gap 42.6×   overlap ZERO
    ```

    - **The response is NOT the variable.** The first `wms_sim_mmgsdi_response_cb_proc` lands at
      **10 916–11 924 ticks** in *every* one of the 13 comparable captures — UZ801 and stock alike. The
      SIM/UIM timing is invariant; only the command's own lifetime differs.
    - **The missing downstream is exactly what the response would have triggered *inside* the command.**
      Stock's window (idx 879→931) contains `UICC SEARCH PATTERN` ×4, `626 WMS_CMD_MMGSDI_RESPONSE_CB` ×4,
      `wms_sim_mmgsdi_response_cb_proc` cnf 51/26/26/26, `qmi_nas_mmgsdi.c:1368 new ef spn size`, the
      **second `cmmsc_auto.c` burst**, and MMOC `Recvd command 0(SUBSCRIPTION_CHGD)` → `New transaction
      1(SUBSC_CHGD)`. UZ801's window (idx 1081→1093, 13 records) contains **none** of them — and in UZ801
      the UICC search/response/spn all happen **after** the free (idx 1097 / 1105 / 1115), so
      `cmmsc_auto` is never called from that path.
    - **★ New instrument, and it removes a blocker.** The observable is **intra-capture** (a difference of
      two `ts` values from the same record family), **parser-robust** (both records have `num_args = 0`,
      so the Doc 211 args-shift defect cannot touch them) and **class-agnostic** (it needs only
      `cmdbg.c`, which the blind class carries — 7 of the 11 UZ801 samples are blind). **Doc 208 §4's
      "burst class (`mmocdbg.c` > 0) required" rule is NOT needed for this question.**
    - **★ Corrections.** (a) Doc 203 §3.2's taxonomy is not clean: `boot_210_canon` is burst-grade
      (`mmocdbg.c` = 144) yet its `u=1` is `tsk=cm`, not `mmgsdi_1`. (b) Doc 204 §6's "`cmmsc_auto.c` = 0
      in all 22 UZ801 captures" is false for the Doc 210/211 captures (`boot_210_canon` and
      `uz801_v19_boot_240` both have **4**); the correct statement is **"the *second* `cmmsc_auto` burst —
      the one inside the SIM-init command — is absent."** (c) The five old "burst class" captures
      (`diag_boot_v7/v8/v13`, `reboot_postpatch`, `post_v18_group14`) have **no `u=1 tsk=cm`**; their
      `u=1` is `mmgsdi_1` (124–192) + `gstk` (4–8) — **also all short**.
    - **Structural note (measured, then RESOLVED):** UZ801 also runs a **long `tsk=cm` command at `u=0`**
      — `boot_210_canon` idx 478→695 = **15 812**, `uz801_v19_boot_240` idx 497→701 = **17 620** — whose
      window contains the *first* `cmmsc_auto` (idx 514–517), MMOC `SUBSCRIPTION_CHGD` (523) and, later,
      `OPRT_MODE_CHGD` (625) → `New transaction 3(OFFLINE)` (632) → `Prot_state [MAIN] 7(OFFLINE)` (683).
      `stock_boot_240` has **no** `u=0 tsk=cm` free (seven allocs at idx 445–451 are **never freed** in
      the capture); `stock_boot_211` has three short ones (3 116/3 120/3 120). **§4.6 CLOSED the
      "ordering shift?" question — the two long `cm` windows are DIFFERENT commands** (4 common sites of
      43 vs 21: UZ801's is the MMOC subscription/op-mode command, stock's is the SIM/EF-read command).
      The two `u=1` windows share **1** site (`qmi_mmode_task.c:363`) after normalising the per-family
      line shifts — ⚠ **the shifts are NOT uniform** (`cmdbg.c` +5, `mmgsdi_refresh.c` +2, `mmocdbg.c` −3,
      `mmgsdiutil.c` +25, `wms.c` +9, `cmmsc_auto.c` +9 from L2849, `mmoc.c` **−75**), so a raw set-diff
      reports the same statement as both UZ801-only and stock-only.
    - **Emitter hunt, bounded negative:** the `OPRT_MODE_CHGD` receipt (`mmocdbg.c:288`, `boot_210_canon`
      idx 625) has **no** preceding CM op-mode log; the window is all `cmph.c` `RAT_DISABLED_MASK` plus
      MMOC state dumps. Doc 211's `ssscr_user_offline_cdma` causal reading is **falsified** (it is at
      idx 676, 996 ticks = 4.86 ms **after** the offline transition), and Doc 211's "SD name-select" lead
      is **falsified** (stock's own 240 s boot also calls `hybr_name_sel()`, idx 565/1031). **Doc 218
      stands**: the MMOC name *strings* are in RAM (seg[20], file `0x4f77880`) but **no 4-byte word in the
      coredump equals their cdva, native, or ELF VA** (0 hits × 6 strings × 3 encodings).
    - **Scoring criterion for the next patch (pre-registration, Doc 220 §6):** accepted iff the `u=1
      tsk=cm` lifetime is **≥ 10 000 ticks** *and* `cmmsc_auto.c` appears **inside** the window *and*
      MMOC `Recvd command 0(SUBSCRIPTION_CHGD)` appears inside it. **n = 3 boots** (the UZ801 spread is
      1.26×, so 3 is ample against a 30× margin).
    - **Leading mechanism — §4.5's hypothesis FALSIFIED, replaced by a stronger statement (§4.7).** The
      45 records preceding the `u=1 tsk=cm` alloc are the **SAME SEQUENCE** in both arms (UZ801
      `boot_210_canon` idx 1036–1080 vs stock `stock_boot_240` idx 834–878): `nvruim_mmgsdi_evt_cb 0x4` →
      `mmocmmgsdi_card_status_cb evt=4` → `Invalid session type=3` → `Sess type=3, ss=4` →
      `Invalid session, sess_type=3, ss=4, session id=0` → CFM ×7 → `evt=19` → `Found session` →
      `qmi_mmode_task` → CFM ×7 → `DalVAdc` → `a2_task A2 inactivity timer` → `evt=21` → `evt=12` →
      `wms 12 SEND_SIM_EFS_READ_EVT` → `wms 625` → `wmscfg Notify SIM_INIT_START` → `qmi_mmode_task`.
      **★★ And the Doc 199 §4 / Doc 202 §6.3 "uninitialised session" (`mmocmmgsdi.c:1718 Found session,
      sess_type = 0x7fffffff, ss = 4, session id = 0`) is in ALL FOUR captures** — UZ801
      `boot_210_canon` idx 887, UZ801 `uz801_v19_boot_240` idx 869, **stock `stock_boot_240` idx 597,
      stock `stock_boot_211` idx 653** — as is `Invalid session, sess_type=3, ss=4`. **⇒ The
      uninitialised-session lead is NOT a UZ801 divergence, and §4.5's hypothesis is falsified: the
      divergence is strictly INSIDE the command.** The next test must be a *code* test, not an input test.
    - **Two ordering observations (recorded, not interpreted).** (a) The WMS queue order around the notify
      differs — stock: `Putting 625` → `Putting 12` → `Notify` → `Processing 12`; UZ801: `Putting 12` →
      `Processing 12` → `Putting 625` → `Processing 625` → `Notify`. (b) **`mmocmmgsdi.c` has a major
      structural change between the builds**: `Invalid session type=%d` is at **245 in both**,
      `Invalid session, sess_type` is UZ801 `1712` / stock `1655` (+57), but `Found session` is UZ801
      `1718` / stock **`23933`** — **not a shift at all**; that statement moved to a different function.
      ⚠ **`mmocmmgsdi.c` cannot be diffed by line offset** like the other families.

26. **★★★★★ THE ANTECEDENT OF THE EARLY FREE IS A UZ801-*BUILD*-ONLY VOICE-SYSTEM-ID EVENT
    (2026-09-28, Doc 220 **§4.8 addendum**).** Item 25 established *that* the SIM-init CM command is freed
    42.6× early on UZ801 and that the inputs are identical. This item names **what returns it** — and it is
    a **build** difference, not a state difference.

    - **The question was changed.** Instead of "what is *inside* the window" (item 25), ask **"what
      immediately precedes the free, in each arm"**.
    - **In BOTH arms the free follows a `629 WMS_CMD_CM_SUBS_EVENT_CB` pair** — the arms differ only in
      **when** it fires:

      | arm | `629 … Putting` / `Processing` (rel. to alloc) | free | Δ(free − 629) |
      | :-- | :-- | --: | --: |
      | UZ801 `boot_210_canon` | **+184 / +188** | **+284** | **100 / 96** |
      | UZ801 `uz801_v19_boot_240` | **+124 / +132** | **+292** | **168 / 160** |
      | stock `stock_boot_240` | **+14 696 / +14 704** | **+14 816** | **120 / 112** |
      | stock `stock_boot_211` | **+14 732 / +14 740** | **+14 852** | **120 / 112** |
      | stock `hmu05_stock_boot` (**online**) | **+13 624 / +13 632** | **+13 808** | **184 / 176** |

      **The tail is structurally identical in all five captures** — same four record *types*, same order,
      only the absolute offset differs:

      ```
      UZ801 boot_210_canon:  … +184   629 Putting → +188   629 Processing → +256   qmi_mmode_task.c:363 → +284   FREE
      stock stock_boot_240:  … +14696 629 Putting → +14704 629 Processing → +14792 qmi_mmode_task.c:363 → +14816 FREE
      stock hmu05_online:    … +13624 629 Putting → +13632 629 Processing → +13772 qmi_mmode_task.c:363 → +13808 FREE
      ```

      **★ Item 25 is amended by this item: stock's n is 3, not 2** — `hmu05_stock_boot.bin` (the 33 743-record
      capture that **does reach `ONLINE_GWL`**) is a third stock sample at **13 808**. The gap becomes
      **39.7×** (was 42.6×) and the "stock is just early in its boot" explanation is ruled out.

    - **The UZ801 `629` is triggered by a UZ801-only `vs_id` event 8 ticks earlier:**
      `cmph.c:15013 =CM= vs_id %x` **`0x10c01000`** (+176) → `629` (+184) →
      `qmi_nas.c:1329/:1330 qmi_nas_cmsubs_evt_cb` (+248) →
      `qmi_nas.c:1447/:1454 lte|wlan_voice_system_id info changed` (`0x10c02000` / `0x10002000`) (+252) →
      **free (+284)**. `uz801_v19_boot_240` is the same sequence at +120/+124/+256/+260/+292. **The `vs_id`
      value is identical in both UZ801 boots ⇒ deterministic, not a race.**
    - **The family is UZ801-BUILD-only — two independent instruments agree.**
      *Dynamic* (per capture): `vs_id` **1 / 1** vs **0 / 0 / 0**; `cmsubs_evt_cb` **2 / 2** vs **0 / 0 / 0**;
      `lte|wlan_voice_system_id` **2 / 2** vs **0 / 0 / 0**. The three stock captures include the
      **33 743-msg capture that reaches `ONLINE_GWL`**, so this is **not** "stock had not got there yet"
      (the Doc 212 §3 trap).
      *Static* (whole-image string search): `=CM= vs_id`, `default data subs`, `voice id - %d`,
      `lte_voice_system_id`, `wlan_voice_system_id`, `cmsubs_evt_cb` → **0 in stock, 1 in UZ801**; controls
      `qmi_nas.c` **2 / 2**, `cmph.c` **2 / 2**, `cmdbg.c` **1 / 1**. The *file* exists on both sides; the
      *statement* on one.
    - **Consequence, with positions:** `cmmsc_auto` burst **#2** (op_mode **11→11**, policyman mask
      `0x220`=544) and MMOC `Recvd command 0(SUBSCRIPTION_CHGD)` #2 + `New transaction 1(SUBSC_CHGD)` #2 are
      **INSIDE** stock's window (+14 524 / +14 536 / +14 560) and **absent** from UZ801's (UZ801 has only
      burst #1, mask `0xEBE`=3774, *before* the alloc).
    - **⚠ Caveat, stated:** the F3 stream is shared, so a record whose `ts` lies between alloc and free is
      not *proven* to be emitted by the command's handler. The `vs_id` is the **prime causal candidate**,
      not a proven cause.
    - **⇒ The next test is re-scoped.** Not "locate the command's handler" (item 25 §9 item 6), but
      **"locate the emitter of `cmph.c:15013` and what calls it"** — a **single-build**, offline question
      answerable from `Modem RE/uz801/modem_full_decompiled.c`. **New open item: decode the mask
      `0x10c01000`** — if it is a band/RAT mask derived from RF configuration, this is the first
      *mechanistic* link between the WTR1605-vs-WTR4905 mismatch and the `offline` gate.
    - **★ Window ≠ content (corollary).** Item 25's "UZ801's window contains none of it" is true of the
      **window** only. The content **is** present on UZ801 at the same relative offsets, **after** the free
      (refresh `[0,2,6]/[0,2,0]/[0,2,1]` at +732…+876; UICC SEARCH at +2 036;
      `626 MMGSDI_RESPONSE_CB` cnf 51/26/26/26 at +11 732; `qmi_nas_mmgsdi.c:1368` spn at +13 968). **The
      differential is a *free timestamp*, not a content set.**
    - **★ Two corpus corrections made this session.** (a) **`DUAL_STANDBY_CHGD` is NOT UZ801-only** — stock
      emits `Recvd command 7(DUAL_STANDBY_CHGD)` ×1 and `New transaction : 9(DUAL_STANDBY_CHGD)` ×1 in
      **both** boot captures, and the transaction sequence that follows is **identical** to UZ801's; only
      the latched `Prot_state` value differs, and its driver is `Recvd command 2(OPRT_MODE_CHGD)` →
      `New transaction 3(OFFLINE)`, which stock never gets (Doc 212 §2.1). The `0` was the long **online**
      capture. (b) **`uimsub_manager.c` is a UZ801-*build*-only module** — the string is **absent from the
      stock firmware image** while **358 of 513** neighbouring strings in the same phdr[19] pool *are*
      present (so not a coverage gap) — but it is one of ~155 differences in that window, so it stays a
      lead, not a cause.
    - **Tools (new):** `scratch/emitter36_afterfree.py`, `emitter37_vsid.py`, `emitter38_cmmsc.py`,
      `emitter22_counts.py`, `emitter24_lifecycle.py`, `emitter30_ctrl.py`, `emitter33_intrawindow.py`.

27. **★★★ The device is running the B08 ("-11") UZ801 build, NOT the B10 ("-21") set the campaign
    analysed; the RF card classes are IDENTICAL to stock; the policyman RAT-mask-0 is NOT the gate; and the
    offline latch is a `cmph.c`-driven `OPRT_MODE_CHGD`. (Doc 221, 2026-09-28.)**
    - **State correction (retraction R3).** The device's 21-file `/lib/firmware` modem set is
      **byte-identical** to `scratch/fwdl/uz801_v32_x/image/` (the md5 manifests `diff` clean), i.e. the
      **B08 / `-11`** build (`UZ801_V01R01B08`, `modem.b16 f6f9900ea3845a1cdab67f6a3c0f1e26`). The
      campaign's v13–v19 / `modem.b16 c71d5346…` / banner `UZ801_V3.0_21_V01R01B10` set is **B10 / `-21`**
      (`GitIgnore/UZ801 Modem/`, `scratch/uz21_swap/`). B08 and B10 differ in **10 of 20 segments**. Both
      are `offline`, so the campaign's *conclusions* survive; its *provenance* does not. **Name the build in
      every future UZ801 citation.**
    - **★ Retraction R1 — the RF-blocked verdict is UFI001B-only.** The `rfc_[A-Za-z0-9_]{3,60}` card-class
      set is **168 names in HMU05 stock, 168 in UZ801-B08, 168 in UZ801-B10**, with `HMU05 − B08 = ∅` and
      `B08 − HMU05 = ∅`; `rfc_wtr1605_*` = 137/137/137 and **`rfc_wtr4905_*` = 0 everywhere**. The `qfe*`
      census returns 46/46/46 as a positive control. **The UZ801 v3.2 firmware carries the same WTR1605 card
      classes as the HMU05 — the port is not RF-impossible at the transceiver layer.**
    - **New structural candidate (bounded, not a verdict).** The RF **device data** families differ:
      `rfdevice_(pa|asm)_*_data_ag` = **75 (UZ801-B08) vs 89 (stock)**, common 73, **16 stock-only**
      (`om_8443`, `rr88643_21`, `rr88916_21`, `s2916`, `s5643_51`, `sky_77643_21`, `vc7643_61`,
      `vc7916_61` × `_pa_`/`_asm_`), 2 UZ801-only (`sky77643`). **⚠ The two builds use different name
      conventions for the same silicon** (`sky77643` vs `sky_77643_21`), so "stock-only" does **not** prove
      absence. This is the first concrete structural candidate for the memory's "board-level RFFE", and it
      is now **bounded**: the transceiver layer is identical, so any RFFE mismatch must be PA/ASM-layer.
    - **New provenance fact.** stock root `/workspace/work/modem/modem8916_1605/`,
      `MPSS.DPM.1.0.c7-00193-M8916EAAAANVZM-1_20150909_103440`; UZ801 root
      `/home/chiyong/UZ801_V3.0-11/msm8939-la-1-0-2-1_amss_qrd_no-l1-src/`,
      `MPSS.DPM.1.0.1.c1-00121-M8936FAAAANUZM-1_20150907_230707`. The UZ801 build is
      **`_amss_qrd_no-l1-src`** — its Layer-1 is a **prebuilt object**, not this build's source. This
      explains why the UZ801 image has the `rfc_*`/`rflte_*` strings yet logs nothing from them (§6.2 of
      Doc 221). Do **not** read `M8936F`/`msm8939` as silicon (already closed:
      `project_uz801_is_msm8916_not_msm8939`).
    - **★ Retraction R2 — the policyman RAT-mask-0 is NOT the gate.** `stock_boot_211.bin` /
      `stock_boot_240.bin` (same-window stock captures that **do** cover the policyman init) emit the
      **identical 12-record** `policyman_rat_capability.c` sequence — same lines, same args including
      `:74 [0, 544] Filtered RAT mask 0` and `:907 [0, 2]` — plus the identical 3 `policyman_rf.c:592`
      records, **and stock is online**. It is a normal transient boot default. Consistent with the live
      state: `--dms-get-band-capabilities` = LTE 1/3/5/8, `--nas-get-system-selection-preference` = mode
      `lte`, LTE bands 1/3/5/8. **Do not re-open.**
    - **★ The gate, pinpointed.** Stock's MMOC holds `Prot_state [MAIN] 0(NULL)` for the whole capture and
      **never** becomes `7(OFFLINE)`. UZ801 does, via a 24-tick chain after the `SUBSC_CHGD` completion:
      `519251776 Trans_state 5(PROT_PH_STAT_ENTER)` → `519251780 cmph.c:18094 RAT_DISABLED_MASK: sys_mode 9`
      + `sys_mode 4` + `cmph.c:32810 After updating from rat_disabled_mask mode_pref 38` →
      **`519251800 Recvd command 2(OPRT_MODE_CHGD)`** → `519251824 New transaction 3(OFFLINE)` →
      `519252872 Prot_state [MAIN] 7(OFFLINE)`. **Stock emits no `cmph.c` record at all at the equivalent
      point.** `mode_pref 38` is the same in both (re-confirms Doc 211 §3.1) — the differential is the
      **op-mode change**, not the mode value.
    - **★ Live confirmation on B08 (first live capture on this build).** A live F3 capture of two
      `--dms-set-operating-mode=online` attempts produced exactly two `cmdbg.c` pairs,
      `=CM= CMD alloc u=7023, tsk=dcc` → free at **+136 ticks (0.66 ms)** and `u=7028` → free at
      **+72 ticks (0.352 ms)**, matching Doc 214's 0.352 ms abort **on B08, with no reboot**. The `u`
      values align with the AP uptimes of the two attempts (7035.27/7040.31 minus the ~12 s boot lag).
      The capture is in Doc 203's **blind** class (`mmocdbg.c` = 0) — **the live op-mode path aborts
      before any MMOC transaction exists**, which is itself the point.
    - **★ `rf_task.c` silence.** Stock logs `rf_task.c:574 RF task  rfm_init OK!` and
      `:582 RF task  new imei check !` at boot (ts 78 428); **no UZ801 capture logs anything from
      `rf_task.c`**, and the format string `RF task  rfm_init OK!` is **0 in both UZ801 images** but 1 in
      stock **and 1 in UFI001B**. The UZ801's two `rfm_init` strings are the *failure* path
      (`UL_RF_OPT: rfm_init_wcdma_tx failed … triggering Mode Offline`), present in both builds.
      **Bounded:** **no RF failure message fires in any UZ801 capture** (searched `Invalid Container`,
      `triggering Mode Offline`, `CPHY Fail`, `rfm_device`, `handles is NULL`, `antenna_tuner` → 0 hits),
      so **do not claim "the RF init fails"** — the RF task is *silent*, not demonstrably failing.
    - **New open items:** (1) resolve the PA/ASM naming caveat by mapping the 16 stock-only
      `rfdevice_{pa,asm}_*_data_ag` to their `rfdevice_id_enum` values and checking whether any of the 168
      identical `rfc_*` card classes reference a device whose *data* the UZ801 build lacks; (2) identify
      the HMU05's actual PA/ASM part; (3) characterise `no-l1-src`; (4) boot-capture **B08** with the
      MMOC-visible class — **the F3 boot instrument is NOT installed** (only `/etc/init.d/diag-bind`
      exists; `/root/diagboot_run.sh` and `/etc/init.d/diagboot` are absent, and the deployed
      `/root/diag_bind.sh` md5 `4f84d28dc2162c512dd36d09860988d9` ≠ the Doc 210 "working" `4a13dfb8…`).
    - **Artifacts:** `Docs/Modem Stability/221_…OPRT_MODE_CHGD.md`;
      `evidence/221_b08_live_and_static/uz801_b08_live_online_attempt.bin`
      (md5 `d56f344ba0576f5b6417a3de45a8a2c5`, 221 750 B) + `device_modem_manifest.txt`.
28. **★★★★ The `7(OFFLINE)` latch is a *propagation* of the CM's op-mode, not a decision; the UZ801-only
    antecedent on its path is `uimsub_manager.c`; and Doc 220's `vs_id` is DOWNSTREAM of the latch.
    (Doc 222, 2026-09-28.)** *This item supersedes item 27's §6.1 causal reading and answers half of its
    open item (1).*
    - **★★★ The control that changes the reading.** `hmu05_stock_boot.bin` (**the 33 743-record ONLINE
      capture**) receives the **same** `Recvd command 2(OPRT_MODE_CHGD)` at ts 3040011632 — and the MMOC
      opens **`New transaction 2(ONLINE)`**, not `3(OFFLINE)`, with `Prot_state [MAIN] 0(NULL)` unchanged
      throughout. ⇒ **the MMOC's `OPRT_MODE_CHGD` handler simply transitions to the CM's current op-mode.**
      `3(OFFLINE)` is a *propagation*; the real question is **why UZ801's CM holds OFFLINE**. Counts:
      `OPRT_MODE_CHGD` = 0 / 0 / **1** (stock 211 / 240 / ONLINE) vs 1 / 1 (UZ801 B10 ×2);
      `7(OFFLINE)` = 0 / 0 / **0** vs **26** / **26**. **One `OPRT_MODE_CHGD` is normal and benign.**
    - **The boot `SUBSC_CHGD` differential, record for record.** Both arms run the identical opening, sit in
      `Trans_state 25(WAIT_DEACTD_CNF_GWL)` ~13 400 ticks, then diverge:
      **stock** `108928 Recvd report 2(PH_STAT_CHGD_CNF)` → `108932 Trans_state 6(WAIT_PH_STAT_CNF)` →
      `116660 Trans_state 5(PROT_PH_STAT_ENTER)` → `116664 New transaction 0(NULL)` →
      `116744 Prot_state [MAIN] 0(NULL)` (**no `OPRT_MODE_CHGD`**);
      **UZ801** `519251776 Trans_state 5(PROT_PH_STAT_ENTER)` (**no `PH_STAT_CHGD_CNF`, no state 6**) →
      `519251776 New transaction 0(NULL)` → `519251800 Recvd command 2(OPRT_MODE_CHGD)` →
      `519251824 New transaction 3(OFFLINE)` → `519252872 Prot_state [MAIN] 7(OFFLINE)`.
      Stock's *second* `SUBSC_CHGD` (`412060`→`425856`) also completes cleanly to `0(NULL)`.
      ⚠ The missing report is a **pairing observation, not a claimed cause**.
    - **★★ `uimsub_manager.c` is UZ801-*firmware*-only.** Whole-image string census:
      **`uimsub_manager.c` = 0 (stock HMU05) / 35 (UZ801-B08) / 35 (UZ801-B10) / 0 (UFI001B)**; controls
      `cmss.c` = 47/46/46/1 and `cmph.c` = 71/88/88/11 are present on **both** sides ⇒ not a coverage gap.
      The unfiltered window at the latch is `cmss.c:13085 cmss_cmd_check` →
      `uimsub_manager.c:809 UIM_1: sub_mgr slot : iccid 0x3=0x1, 0x5=0x70, 0x7=0x22` (an ICCID-byte
      subscription match) → `sdcmd.c:27798 hybr_name_sel()` → `cmph.c:32761/32771` mode-pref update →
      `OPRT_MODE_CHGD`. **Prime candidate, NOT a proven cause** (shared F3 stream; the same caveat Doc 220
      §4.8 attached to `vs_id`).
    - **★ Correction A — Doc 220 §4.8's `vs_id` is ~1.41 s AFTER the latch.** `OPRT_MODE_CHGD` 519251824,
      `7(OFFLINE)` **519252872**, `cmph.c:15013 =CM= vs_id 0x10c01000` **519541324** (Δ 288 452 ticks).
      The `vs_id` event is real and does complete the *later* `u=1 tsk=cm` command early — but that command
      is **post-latch**. **`vs_id` cannot be the cause of the `offline` latch.**
    - **★ Correction B — the `cmph.c` mode-pref block is NOT UZ801-only; only its *timing* differs.**
      The statements exist in **both** builds at shifted line numbers — `mode_pref` 32055→32761,
      `rat_disabled_mask` 32065→32771, `After updating from rat_disabled_mask` 32104→32810,
      `RAT_DISABLED_MASK` 17829→18094, 2nd `rat_disabled_mask` 17838→18103 (`vs_id` is the only
      statement genuinely absent from stock). The offset is **not** constant (≈+265 at 17829, ≈+706 at
      32055) ⇒ the UZ801 `cmph.c` is a **later source revision, not a superset**. Stock *does* run the block
      — at its `PH_STAT_CHGD_CNF` (ts 108940), **7 700 ticks before** its `SUBSC_CHGD` completion, and
      emits no `OPRT_MODE_CHGD` afterwards. **The differential is the trigger/placement of the mode-pref
      update, not the existence of the statements** — and `mode_pref` is `38` and unchanged in both.
    - **★ Item 27 open item (1) advanced.** The stock-only `rfdevice_(pa|asm)_*_data_ag` are **8 complete
      PA+ASM silicon families** (16 names): `om_8443`, `rr88643_21`, `rr88916_21`, `s2916`, `s5643_51`,
      `sky_77643_21`, `vc7643_61`, `vc7916_61`; the UZ801-only pair is `sky77643` (**the same silicon as
      `sky_77643_21`, renamed**). The registry is a table of **80/96/144-byte** records (16-byte-aligned
      name + fixed fields) with the second word an incrementing pointer of stride 80. **The missing set is
      SCATTERED** (indices 2,10,22,29,34,36,40,42,43,44,46,64,67,70,81,82) — **not** a contiguous board
      block ⇒ a selective build-time device list. **`rfca` (the RF-card task) STARTS on both arms**
      (`rcinit_rex.c` `task_name rfca`), and the RF task set has **no stock-only members** ⇒ RF init is not
      failing to run. `_data_ag` symbols have **0 occurrences in either Ghidra decompile** ⇒ they are
      data-table names, not code-referenced strings; the descriptor route does not apply.
    - **★ Live probe on the deployed B08 (Doc 222 §5), no reboot.** `--dms-get-operating-mode` = `offline`,
      **`HW restricted: 'no'`** (⇒ not a hardware lockout — the live counterpart of R1); `--dms-get-ids`
      IMEI **`864293052253917`** = the **HMU05's own IMEI** (⇒ the device is in the stock-NV state of
      `project_uz801_full_device_swap_hy.md`); `--dms-set-operating-mode=online` → **QMI 52
      `DeviceNotReady`** (×2); `--dms-set-operating-mode=low-power` → **QMI 60 `InvalidTransition`**. The
      `low-power` code is a **new** datapoint: the DMS op-mode state machine refuses that edge *from the
      current state*, so **do not read `DeviceNotReady` as "still booting"** — the AP had been up 7 784 s.
      A 25 s F3 capture over two `online` attempts is **MMOC-blind** (`mmocdbg.c` = 0 of 699) and shows only
      the `cmdbg.c` pair — `=CM= CMD alloc u=8289, tsk=dcc` → `free …, ftsk=cm` at **72 ticks (0.352 ms)**
      and `u=8291` at **136 ticks (0.664 ms)** — with **nothing between alloc and free**. ⇒ **the rejection
      is above the MMOC layer, at the CM/DMS layer, and it is silent.** Reproduces Doc 214's abort and
      Doc 221 §3's values on B08. Artifact: `scratch/live_b08/online_try_222.bin`
      (md5 `15a89f35960349838c46fdb1209a1196`, 157 492 B, 699 records).
    - **★ A SECOND, INDEPENDENT rejection path — the AT channel (Doc 222 §5.3).** `/dev/wwan0at0` answers
      via `scratch/at_tool` (deployed to `/root/at_tool`). The modem is **`+CFUN: 7`** (offline
      functionality) with **`+CPIN: READY`** — `AT+CFUN=?` → `+CFUN: (0-1,4-7),(0-1)`. The refusals are
      **three distinct codes and the distinction is load-bearing**:
      **`AT+CFUN=1` → `+CME ERROR: phone failure`** (the request is *attempted* and fails *inside* the
      modem) vs **`AT+CFUN=0` / `=4` / `=5` → `+CME ERROR: operation not supported`** (rejected *before* any
      attempt) vs **`AT+CFUN=6` / `=7` → `OK` but inert** (no state change; already 7) vs QMI `online` →
      52 `DeviceNotReady` vs QMI `low-power` → 60 `InvalidTransition`. **The advertised range is asymmetric:
      `+CFUN=?` = `(0-1,4-7)` claims an online value, but every online-side value is refused and every
      offline-side value is refused or a no-op — no accepted transition moves the op-mode UP.** `phone
      failure` is a generic CME 100-class code ⇒ the online request is admitted by the parser (unlike the
      offline values, which get `operation not supported`) and fails in the CM's own op-mode handler.
      **The CM task differs by path**: QMI `online` → **`tsk=dcc`** (72 / 136 ticks); AT `CFUN=1` →
      **`tsk=ds`** (**84 ticks**, twice) — both freed by **`cm`** in <1 ms with **nothing between alloc and
      free**. ⇒ **two different AP command paths converge on the same CM rejection, and it is silent.** The
      refusal is in the **CM**, not in a service layer. Artifact:
      `scratch/live_b08/at_cfun_try_222.bin` (md5 `7609de07ea24053528d3ec85cb2972e0`, 90 949 B, 533
      records). No AP hang in either capture (boot_id unchanged).
    - **★ Method note — `uimsub_manager.c`'s descriptor route WORKS; `cmph.c`'s does not.** The emitter of
      `uimsub_manager.c:809` is **`FUN_c055bfd8`** (site VA `0xc161b960`), found with
      `logsite.py refs --file=uimsub_manager.c` (**98 literals**). Its callers are **`FUN_c053c7bc`**
      (= **`uim.c:3805`**), `FUN_c055bed4` + `FUN_c055c1ec` (`uimsub_manager.c`), and `FUN_c055c7d4`
      (`uimsub_manager.c:910–949`). By contrast the four `cmph.c` mode-pref descriptors
      (`:32761`→`0xc165d6a0`, `:32810`→`0xc165d6b0`, `:18094`→`0xc165d288`, `:18103`→`0xc165d290`, recovered
      by scanning `modem.elf` for `(line<<16)|level`) have **0 occurrences in the decompile**. **Do not
      conclude "the emitter is unlocatable" from a `cmph.c` zero** — the two files reach their descriptors
      by different mechanisms.
    - **New open items:** (1) **why does UZ801's CM hold OFFLINE at boot?** — *the* root question; §5 narrows
      it to the CM/DMS layer. Instrument: the CM op-mode NV over DIAG EFS vs the HMU05-derived dump
      `scratch/efs_H/` — note that dump has **no op-mode preference file** under `modem/mmode/`
      (`device_mode`=`00`, `ue_usage_setting`=`00`, `sms_domain_pref`=`01`, `sw_version`=
      `msm8916_32_512-userdebug 4.4.4`), which is worth testing against "the UZ801's newer CM looks for an
      NV item the HMU05 EFS lacks and defaults to OFFLINE". **Not yet tested.** (2) pick the
      `FUN_c055bfd8` caller active at boot (4 candidates above) and what ICCID bytes it matches. (3)
      boot-capture **B08** (item 27 item 4) — all of Doc 222 §1–§4 is B10; §5 is a live B08 capture but
      **blind for MMOC**; the static census shows B08 carries the same `uimsub_manager`/`vs_id` families, so
      the mechanism should transfer, but it is untested with a B08 *boot* capture.
    - **Artifacts:** `Docs/Modem Stability/222_…UIMSUB_MANAGER…md`;
      `scratch/doc222_latch_differential.py`; `scratch/doc221_rfdevice.py` (item 27's PA/ASM census);
      `scratch/live_b08/online_try_222.bin` (Doc 222 §5.2).

29. **★★★★ Doc 223 (2026-09-28) — the gate is the *subscription-available* event, and two UIM modules are
    UZ801-only FILES.** `Docs/Modem Stability/223_…SUBSCRIPTION_AVAILABLE…md`;
    `scratch/doc223_subscription_gate.py` (one script, all five sections; enforces the `f3parse.py` md5 gate).
    - **★★★★★ §2 THE GATE — `is_gwl_subs_avail`.** `cmregprx.c` computes a per-stack subscription-available
      flag on each `ph_stat_chgd`. It is **binary and consistent across all five captures**:
      **UZ801_B10_canon / UZ801_B10_v19 → `as_id=0, is_gwl_subs_avail 0->0`**; **stock_211 / stock_240 /
      stock_ONLINE → `0->1`** (ONLINE later `1->1`). The surrounding triple
      (`cmregprx.c:14079/14182` → `:8129` → `:8135` → `:8139`) is identical on both arms, and `ss=0` and
      `ue_mode=0` on both ⇒ the flag is **not** derived from the serving system; it is a separate
      *subscription* input. **⚠ Caveat (stated in the doc): it is 548 ticks AFTER the MMOC opens
      `3(OFFLINE)`** (rec 632 vs 646) ⇒ it is **at least partly downstream**, so direction is NOT resolved.
      What it does establish is that **the one signal naming the missing precondition is never asserted.**
    - **★★★★ §1 THE MEASUREMENT TRAP (corrects Doc 222 §2 and the earlier per-file censuses).** The captures
      are **not the same length**: stock spans **4 398 s / 7 978 s / 17 817 s** vs UZ801 **~17 s** (and
      `UZ801_B10_canon`'s `ts` is **non-monotonic** — ring-buffer read-out; the ~5 % `79 00` false positives
      are not cleaned). ⇒ **any "stock logs X N times, UZ801 once" count is length-biased.** Doc 223
      therefore states §3–§5 as **presence/absence of a specific event**, each verified to be inside the
      UZ801 capture's own window (≥1 000 records after the event in which it could have appeared).
    - **★★★★ §5 THREE UZ801-only *FILES*** (file-**name** string absent from the stock image — a build-level
      test that cannot be confused by log level, window length, or a removed statement):
      **`uimsub_manager.c`, `mcfg_uim.c`, `qpCircularBuffer.c`**. Two of the three sit on the UIM/subscription
      path. This **supersedes Doc 222 §3's log-*site* method** with a strictly stronger one. `policyman_uim.c`
      / `uim.c` / `uimgen.c` / `ale_proc.c` / `qpDcm.c` are present in **both** images (so their 0-counts on
      stock are behavioural/window, not build).
    - **★★★ §5.1 `mcfg_uim.c` reads the HMU05 EFS.** `mcfg_uim_autoselect NV=5` ←
      `/mcfg/mcfg_autoselect_by_uim` = **`05`** (present in the HMU05-derived EFS), and
      `mcfg_last_autoselect_iccid` = ASCII **"89918610400794228644"** (the HMU05 SIM's ICCID). So the
      UZ801-only autoselect applies HMU05-computed MCFG content — **candidate mechanism, NOT proven.**
    - **★★★ §4 `cmss.c` subscription processing never runs on UZ801.** Stock runs 18 `csg_id`/`hnb`/
      `sys_id_type`/`csg_info`/`fmode` records at **~1.7 s** — the *same* moment as its
      `is_gwl_subs_avail 0->1` (ts 412 724) and `sdcmd_name_sel3()-is_gwl_subsc_avail:1` (ts 417 700).
      UZ801: 1 record (`cmss_cmd_check`, B10_canon) / 0 (B10_v19). Same phenomenon as Doc 220 §4.4's
      39.7×-early `u=1` command, seen from the CM side.
    - **★★★ §3 `ssscr_user_offline_cdma` activates only on UZ801** (1×/boot in both B10 captures; **0 in
      37 743 stock records**), 44 ticks before the final `Prot_state [MAIN] 7(OFFLINE)`. **The script NAME
      exists in every image** (stock/B08/B10/UFI001B) ⇒ the differential is the *activation*, not existence;
      `ssscr_user_offline_gwl` exists in **no** image. **Consequence, not cause.**
    - **★★ §6 FOUR KILLED CANDIDATES (build artefacts, not behaviour)** — do not re-open:
      (a) UZ801's 0 `rf_task.c` records ⇒ the strings **`rfm_init OK`** and **`imei check`** are **absent
      from the UZ801 image**; the statements were removed. (b) `uim.c:7136 Read to NV active slot
      configuration unsuccessful` (the boot's **only unique error**) ⇒ the log string is UZ801-only, and
      **the failure is handled**: `FUN_c053e66c` (site VA `0xc161a010`) falls back to the valid slot **1**
      and the "invalid" branch needs 0 or >2, so no second log — exactly the observed output; the live
      session opens on `slot_id 1`. The item is
      `/nv/item_files/modem/uim/uimdrv/nv_active_slot_configuration` (7 B, **absent** from the HMU05 EFS,
      as is `nv_pdown_uim_consecutive_techproblems`; the other 4 uimdrv items are present). (c) the
      UZ801-vs-stock EFS-path diff (54 vs 3) is **not** diagnostic — both builds reference hundreds of absent
      NV files (UZ801 246/513; stock 201/464). (d) `policyman_uim.c` is present in **both** images.
    - **★ Refined root question (replaces Doc 222 §7 item 1's phrasing):** *which step between the UICC read
      (which SUCCEEDS — ICCID `89918610400794228644` at rec 922) and the CM's subscription evaluation fails
      to deliver the subscription-available event, and is it `uimsub_manager.c`'s or `mcfg_uim.c`'s
      ordering?* One layer lower, and it names a component set.
    - **New open items:** (1) read `uimsub_manager.c` and `mcfg_uim.c` at the function level (the descriptor
      route is **hit-or-miss**: `uimsub_manager.c` gives 98 literals via
      `logsite.py refs --file=…`, but `mcfg_uim.c`, `cmregprx.c` and `cmph.c` give **0**) and determine
      their ordering vs the CM's `ph_stat_chgd`. (2) test writing
      `/nv/item_files/modem/uim/uimdrv/nv_active_slot_configuration` on the live device via `diag-efs put`
      — **low expected value** (the failure is handled) but cheap and read-back-verified. (3) whether
      `mcfg_autoselect_by_uim = 5` is the HMU05's own value or a UZ801-era value — needs a UZ801 EFS, which
      is unrecoverable (`reference_modem_efs_crypto.md` §8). (4) boot-capture **B08** with the MMOC class
      (item 27 item 4) — all of Doc 223 §2–§5 is B10.
    - **Artifacts:** `Docs/Modem Stability/223_…SUBSCRIPTION_AVAILABLE…md`;
      `scratch/doc223_subscription_gate.py`; `scratch/efs_H/efs_H/mcfg/*`;
      `scratch/efs_H/efs_H/modem/uim/uimdrv/*`.

30. **★★★★ Doc 224 (2026-09-28) — the EFS instrument is REPAIRED, the live EFS is an HMU05 SUPERSET, and a
    raw DIAG write is a NEW AP-RESET HAZARD.** `Docs/Modem Stability/224_…EFS_INSTRUMENT…md`;
    `scratch/diag_efs_skip.c`; `scratch/diag_probe{,2}.c`; `scratch/doc224_nv_diff.py`;
    `scratch/doc224_cmregprx_desc2.py`; `scratch/doc224_counts.py`; `scratch/doc224_span.py`;
    `scratch/doc224_bootwindow_diff.py`; `evidence/224_efs_live/efs_live_20260928.txt`.
    - **★★★★★ §2 THE INSTRUMENT WAS BROKEN AND IS NOW FIXED.** `diag_efs` failed with `bad resp 79`
      because the DIAG channel carries a **CONTINUOUS spontaneous F3 stream** — measured: **19 of 20
      consecutive `read()`s return an F3 record**. `efs_txn()` reads **once**. Fix: read until a wall-clock
      budget expires, discarding anything that is not `resp[0]==0x4b && resp[1]==req[1] && resp[2]==req[2]`.
      Shipped as `scratch/diag_efs_skip.c` → `/root/diag_efs_skip`. Reply framing is
      `4b 13 <subcmd> 00 | payload | crc(2) | 0x7e` — **no length prefix** (one stray leading `0x15` was
      seen once; do not model it). `EFS_DUMP=1` prints REQ/RESP hex. Verified: `stat /` → `mode=000041ff
      size=34`; `list /` → **1 315 entries, rc=0**.
    - **★★★★★ §3 NEW HAZARD — A 1-BYTE RAW WRITE TO `/dev/rpmsg0` RESET THE AP.** `diag_probe … raw "00"`
      killed the SSH session; uptime went **27 919 s → 51 s** and `boot_id` changed
      (`73a08a06…` → `a45f2ecd…`). **Never write an arbitrary/truncated byte string to `/dev/rpmsg0`** —
      only well-formed `4b 13 <subcmd> 00 …` requests. The reset **also destroyed the rpmsg char nodes**;
      `diag-bind-watch` did NOT restore them (it only acts on *unbound*), so re-run `/root/diag_bind.sh`.
      **The baseband was NOT damaged**: `modem.mdt c132421f…` / `modem.b00 b83653c3…` / `mba.mbn 4869138c…`
      = the deployed UZ801 v32_x set; no `BOOTLOOP_REVERT.txt`.
    - **★★★★★ §4.1 THE EFS-AS-CAUSE HYPOTHESIS IS CLOSED.** Live EFS (1 315 paths) vs the HMU05 reference
      `scratch/efs_H/efs_H`: **0 HMU05 paths are missing from live**; the 184 extras are all
      runtime-created (GPS almanacs `CGPS_PE/AlmFile*`, `CGPS_ME/*`, `Uim{1,2,3}EfsAPDULog.Txt`,
      `.efs_private`). ⇒ the EFS is **not** missing anything the stock modem has.
    - **★★★★ §4.2 the missing UIM NV items are NOT diagnostic.** `/nv/item_files/modem/uim/uimdrv/` live
      holds exactly 6 entries; `nv_active_slot_configuration`, `feature_support_hotswap`,
      `nv_pdown_uim_consecutive_techproblems`, `mmgsdi/refresh_vote_ok`, `jcdma/uim_jcdma_mode` are absent —
      **but so are they on the stock HMU05 device's EFS** (0 hits for `nv_active` in `scratch/efs_H`), and
      stock runs `online`. They are optional/created-on-demand. (This **strengthens** item 29's §6b "the
      failure is handled" and retires item 29's open item 2 as low-value.)
    - **★★★ §4.3 only ONE subscription NV path exists** in either arm:
      `/nv/item_files/modem/mmode/ue_based_cw_Subscription01` (2 B, `0x0000`) and
      `/nv/item_files/modem/nas/mm_backoff_remaining_info_subscription01`. No `…Subscription02`.
    - **★★★ §4.4 the UZ801 device's own NV is still not readable.** The full UZ801 dump exists
      (`GitIgnore/UZ801 Modem/UZ801_Stock/dump/`: `modemst1/2.bin`, `fsg.bin`, `fsc.bin`, `persist.bin`,
      `system.bin`) and **all three NV partitions hold ZERO plaintext EFS path strings** — an independent
      re-confirmation of `reference_modem_efs_crypto.md` §8. Item 29's open item 3 stays closed.
    - **★★★★★ §5 `cmregprx.c` IS A DIFFERENT SOURCE VERSION.** Both builds have exactly **22** `cmregprx.c`
      descriptors with **identical format strings** and **shifted lines**: 8099→8129 (+30), 11821→11860
      (+39), 12493→12536 (+43), 14031→14079 (+48), 16223→16272 (+49). Δ grows monotonically ⇒ code was
      **added throughout** the very file that computes `is_gwl_subs_avail`. **The log-site function is NOT
      yet located:** the descriptor (`0xc165f420` UZ801 / `0xc155ee20` stock) is referenced by **no bare
      literal** in the decompile (unlike `FUN_c091d840(&DAT_c1750178)` elsewhere) and an `immext` scan for
      both the descriptor VA and its `packed` (`0x1fc1000b`) returns **0 hits** ⇒ the site is reached by a
      **computed address**. This is the open item.
    - **★★★★ §6 `is_gwl_subs_avail` re-confirmed with WHOLE-CAPTURE counts** (defeats the length bias):
      UZ801 `[0,0,0]` **0→0** (both arms) vs stock `[0,0,1]` **0→1** (all three; ONLINE then `[0,1,1]`).
      `cmss.c` = 1/0 on UZ801 vs **18/18/640** on stock; `fplmn_list` 0/0 vs 2/2/2; `PRECOND_SS` 0/0 vs
      1/1/2.
    - **Refined root question (replaces item 29's):** *`cmregprx.c` differs by +30…+49 lines between the
      two builds — which added block gates the `is_gwl_subs_avail` computation, and what input does it
      require that the UZ801 firmware cannot obtain on HMU05 hardware?* Secondary: is `cmss.c` never
      running a *cause* or a *consequence* of `is_gwl_subs_avail == 0`?
    - **Artifacts:** as listed in the first line of this item; the frozen live listing is
      `evidence/224_efs_live/efs_live_20260928.txt`.

31. **★★★★ Doc 225 (2026-09-28) — the shipped immext scanner is DEFECTIVE, the `cmregprx.c`
    descriptors are STATICALLY UNREACHABLE, and `uimsub_manager` hardcodes the hotswap flag that
    forces card status UNKNOWN.** `Docs/Modem Stability/225_…UIMSUB_MANAGER_HOTSWAP_LATCH.md`;
    `scratch/doc225_{refs2,str_refs,emitter_map,window_refs,window_dump,table_map,file_desc,desc_hits,find_ref}.py`;
    `scratch/doc225_immext_hits.txt`.
    - **★★★★★ §2 THE SHIPPED SCANNER IS DEFECTIVE — CORRECTED AND VALIDATED.** `desc_table.py refs`
      GUARD 2 requires `next_word & 0x3F == A & 0x3F`. Disassembly of a site that Ghidra *does*
      render (`FUN_c0685dc0 @ c0685dc0` → `FUN_c091d840(&DAT_c165a690)`) proves the guard is wrong:
      `immext(#0xc165a680)` (raw `0x0C16569A` = `immword(0xc165a680)`, so the **key is right**) is
      followed by `r0 = ##-0x3e9a5970` (raw `0x7800C200`), whose low 6 bits are `0x00`, not `A & 0x3F`
      = `0x10`. **Measured: shipped scanner → 0 hits; corrected → 5 hits** (`c0685e04`, `c0685e48`,
      `c0685fe8`, `c068610c`, `c068615c`). ⇒ every Doc 205/206/224 immext **negative** is unsafe in the
      under-reporting direction. New tool `scratch/doc225_refs2.py`. ⚠ Second trap: `llvm-objdump`
      prints `immext(#<DECODED address>)`, **not** the raw encoding — `scratch/doc225_str_refs.py`
      v1 used the raw encoding as the key and produced a false zero on the NV literals.
    - **★★★★★ §4 THE `cmregprx.c` LOG SITE IS UNREACHABLE BY DESCRIPTOR — FOUR INDEPENDENT TESTS.**
      (1) decompile substring search for pages `c165e`/`c165f` → **0** (stock `c155ee2`/`c155edc`/
      `c155ec3`/`c155ea3` → **0** too); (2) the emitter map: **no emitter references page `0xc165e`**;
      (3) full-ELF `llvm-objdump -d` (5 219 227 lines) grep `immext(#0xc165[ef]` → **0**, with the
      in-file control `immext(#0xc165a680)` → **5**; (4) exhaustive raw-ELF scan of every 0x40-aligned
      address in `0xc165e000`–`0xc1660000` → 5 hits, **all unaligned, all in seg[24] rodata** (chance).
      **Doc 224 §5's open item is RETIRED as unachievable by that route.** Same for `mcfg_uim.c`
      (`0xc16adebc`…`0xc16ae0d4` → 0; the page aggregate's 60 `FUN_c091d840` sites belong to *other*
      files in the same 4 KB page). The records are nonetheless on the wire (line 8129 verified).
    - **★★★★ §3 THE EMITTER MAP.** `scratch/doc225_emitter_map.py`: `FUN_c091d840` 17 137 sites/230
      pages; `FUN_c08a4c90` 12 782/123; `FUN_c0287020` 4 569/85; `FUN_c091da60(desc,4)` 4 669;
      `FUN_c091d8c0` 2 638/215. A descriptor page touched by **no** emitter is a page whose records
      still reach the wire.
    - **★★★★★ §5 A ROUTE THAT WORKS — NV PATH LITERALS.** NV path literals **are** embedded in code
      (unlike F3 format strings). `feature_support_hotswap` `0xc18f0e5f` → refs `c053f0b8`,`c05468a8`;
      `nv_active_slot_configuration` `0xc18f0d5a` → `c053e980`,`c053f0d0`,`c055c82c`;
      `me_hotswap_configuration` `0xc18f0d21` → `c053e8c4`,`c053e908`,`c053f06c`;
      `uim_features_status_list` `0xc18f0ce8` → 8 sites. `uimsub_manager.c` **IS reachable**
      (`FUN_c0287020(&DAT_c161b9xx)`, `FUN_c091da60(&DAT_c161b9xx,4)`) and **16 functions** were
      located (L139 `FUN_c055f1c0`; L423 `FUN_c055c348`; L698/711/735 `FUN_c055c68c`; L771/805/809/
      812/814/824 `FUN_c055bfd8`; L910/925/930/933/949 `FUN_c055c7d4`; …).
    - **★★★★★ §6 THE THREE UZ801-ONLY MODULES, RECONFIRMED ON THE ELF AND LIVE.** File-name literal
      counts: `uimsub_manager.c` **35 vs 0**, `mcfg_uim.c` **32 vs 0**, `qpCircularBuffer.c` **2 vs 0**
      (UZ801 vs stock). Live: UZ801 emits 9+9 and 6+9 records; **all three stock captures emit 0**.
    - **★★★★★ §7 THE CANDIDATE MECHANISM — a HARDCODED hotswap flag.** `FUN_c055f1c0`
      (`uimsub_manager.c` L139) writes `(&DAT_c34b120b)[slot*0x1d] = 0` for all 4 slots, and
      **`DAT_c34b120b` has exactly two references in the whole image** (this write and the read).
      `FUN_c055f8f8` copies it to `(&DAT_c34b15bc)[slot*0x14]`; `FUN_c055c68c` (L735) and
      `FUN_c055bfd8` (L771) then read it and set the slot card status to **3 (UNKNOWN)** whenever it is
      0 — which is always. Runtime correlate: **`L711 "…as hotswap is disabled"` is logged exactly
      twice** per boot, and the live EFS confirms `feature_support_hotswap` **absent** (err `0x02`).
      **Falsifier pre-registered (§7.4):** patch that one byte to `1`, deploy, and read
      `is_gwl_subs_avail %d->%d` from a fresh capture — **still `0->0` ⇒ chain FALSIFIED**.
    - **★★★ §6b SELF-CORRECTION — RING-BUFFER ABSENCE IS NOT EVIDENCE.** The captures are multi-boot
      ring dumps. `Reporting QMI latest physical` / `card-status` / `activity status` / `logical slot`
      are absent from the raw captures while `L809` is present — yet **all five of those descriptors
      have exactly one reference in the whole image, and all five are inside `FUN_c055bfd8`.** So
      eviction, not a halted path, is the explanation. Any "string X never appears ⇒ branch never
      taken" claim from these captures is unsafe. (This corrects a reading made earlier in this
      session and strengthens Doc 223 §1's length-bias warning.)
    - **★★★ §7.5 THE SIDE-BY-SIDE AT THE FLAG.** Both arms emit the identical `ph_stat_chgd` triple
      (`cmregprx.c L14079`/`L14182` → `L8129`/`L8135`/`L8139`). stock: `Curr_trans 1(SUBSC_CHGD)` +
      `WMS_CMD_CM_SUBS_EVENT_CB` → **`0->1`**; UZ801: `Curr_trans 3(OFFLINE)` +
      `WMS_CMD_CM_PH_EVENT_CB` → **`0->0`**.
    - **Refined root question (narrows item 30's):** *does the card-status UNKNOWN forced by the
      hardcoded hotswap flag actually drive `is_gwl_subs_avail` to 0 — and if not, what does?* The
      `cmregprx.c` +30…+49 delta is still open but is now known to require a non-descriptor route.
    - **Blast radius, stated before acting (§8):** the proposed test modifies `modem.b16`/`modem.mdt`;
      `modem-guard` bounds a bootloop but a partial deploy has happened before (item 30). **Nothing
      was applied.**
    - **Artifacts:** as listed in the first line of this item.

---

### 32. 2026-09-28 — **Doc 226: the baseband is HASH-VERIFIED; the hotswap flag is NV-driven, not hardcoded; and an EFS write does not survive an AP reboot**

**Doc:** `226_THE_BASEBAND_IS_HASH_VERIFIED_THE_HOTSWAP_FLAG_IS_NV_DRIVEN_AND_THE_NV_WRITE_DOES_NOT_SURVIVE_A_REBOOT.md`

**One line:** the item-31 pre-registered falsifier was executed end-to-end; it is **inconclusive** (the control did not take), and in the process we proved the baseband's 3-file hash chain, **corrected** Doc 225 §7 (the flag is overwritten from NV), and found that the F3-capture and EFS instruments are **mutually exclusive**.

- **★★★★★ The MPSS image is SHA-256 hash-verified; a one-byte baseband edit does NOT boot.** Deploying only a patched `modem.b16` gives
  `qcom-q6v5-mss: MPSS authentication failed: -19` → `remoteproc0: Boot failed: -19`, and the DIAG device never appears.
  The fix is a **3-file** update: (1) patch `modem.b16`; (2) write `sha256(modem.b16)` into `modem.b01[0x028 + 16*32]` = `modem.b01[0x228]`;
  (3) rebuild `modem.mdt = modem.b00 + modem.b01`. Tool: `GitIgnore/compare/ufi001b_hash_tool.py verify|patch|info`
  (lineage: `GitIgnore/compare/build_hmu05_stock_patched.py`). Deployed deltas: `b16` **1 B**, `b01` **32 B**, `mdt` **32 B** — exactly as predicted.
  **Any future baseband patch must do all three, or the modem will not boot.**
- **★★★★ The deployed build is NOT the scratch build.** `scratch/uz801_fw/image/modem.b16` md5 `848abe7e…` vs deployed `/lib/firmware/modem.b16`
  md5 `f6f9900e…` — **324 830 differing bytes** (first at `0x28cc8`), and `modem.mdt` differs by 2 134 bytes (the Doc 221 B08/`-11` vs B10/`-21` split).
  The phdr layout is identical, so `VA 0xc055f380 → modem.b16 + 0x2d8380` still holds, and the site was re-confirmed by building a minimal ELF around the
  **deployed** segment. **Re-derive every site against the deployed bytes.**
- **★★★★★ Doc 225 §7's "there is no NV-driven writer" is WRONG.** `FUN_c055f1c0` writes the defaults and then, at its tail, calls
  `FUN_c09446b0("/nv/item_files/modem/uim/uimdrv/uim_hw_config", &DAT_c34b1204, 0x100, 0x80242, 0x1ff)` — **the whole 256-byte struct is overwritten from NV.**
  The hotswap flag is **NV byte 7** of that item (byte 8 is the adjacent field; byte 0's live value `0x03` matches the in-code default, confirming the identity).
  Corroboration that the NV read *overwrites*: the default for byte 8 is **1** while NV byte 8 is **0**.
- **★★★ The item-31 falsifier result — INCONCLUSIVE, not falsified.** Patched the store (`memb(r25+#0x7) = #0x0` → `#0x1`; the imm=1 twin `0x3c194381`
  already exists in-image, so the encoding is proven by the tool), the modem booted, and the capture reads
  `=CMREGPRX= ph_stat_chgd: as_id=%d, is_gwl_subs_avail %d->%d` → **args `[0,0,0]`**, `AT+CFUN?` → **7**. But the **control did not take**:
  `uimsub_manager.c` **L711** ("…as hotswap is disabled") is still **2×/boot** (baseline 2), so the branch still saw the flag as 0.
  **Reporting this as a falsification would have been exactly the error the measurement discipline exists to prevent.**
- **★★★★ The NV lever was then tested and FAILED.** Live item read: byte 7 = **0**. Wrote byte 7 = 1 with the guarded `put` (backup + read-back **MATCH**);
  in-boot read-back byte 7 = **1**. Re-armed `diagboot`, rebooted: `is_gwl_subs_avail` still `0->0`, `L711` still 2, and the NV item re-read after the reboot is
  **byte-for-byte identical to the original** (byte 7 = **0**). **The EFS write does not survive an AP reboot.**
  The write path is not the problem: `rmtfs -P -s` where `-P` = "find and use raw EFS partitions" (`storage.c`: `modem_fs1`→`modemst1`, `modem_fs2`→`modemst2`,
  `/dev/disk/by-partlabel`), and `storage_pwrite()` is a plain `pwrite()` to the partition fd. The modem appears to restore/ignore it on boot
  (consistent with the `modemst1`/`modemst2` two-copy alternation, or an EFS transaction that only commits on a **clean modem shutdown** — an AP reboot is not one).
  **⇒ an NV lever is not testable until a commit path is found (write both copies, or a clean modem restart).**
- **★★★★★ NEW OPERATIONAL FACT — the F3-capture and EFS instruments are MUTUALLY EXCLUSIVE on `/dev/rpmsg0`.** With `diagboot` armed
  (`diag_logtool cntl-enable <CNTL> 64`), **every** EFS2 request goes unanswered (`efs method: none`; 26–167 messages skipped). With `diagboot` **disabled**:
  `efs method: 0x13`, `stat …/uim_hw_config` → `mode=000081ff size=256`. The tool receives traffic, just never the reply. Sharpens Doc 224's
  `cntl-disable ⇒ UNRESPONSIVE` finding. **Rule: capture the F3 log *or* read/write EFS, never both in one boot.** NV experiments need
  `diagboot off → write NV → diagboot on → reboot → read F3`.
- **★★★★ `diag_efs` CLI — YES, it needed updating; DONE.** Doc 224's skip-fix existed only in the side file `scratch/diag_efs_skip.c` / binary `/root/diag_efs_skip`;
  the canonical package source still had the broken single-read `efs_txn()` (md5 `56e38545…`). The fix is now folded into `packages/diag-efs/src/diag_efs.c`
  (skip loop + `EFS_DUMP`/`EFS_DEBUG` + a NOTE on the §6 incompatibility), rebuilt with `./build.sh package diag-efs` (53 s; the build re-syncs the live tree,
  `./build.sh guard` → **in sync**), and deployed to the device as `/root/diag_efs` **and** `/usr/bin/diag_efs` (md5 `1c82d1a3…`); the old broken binary is
  kept as `/root/diag_efs.old_broken` (md5 `d2942bd0…`). Note the package had **never** been installed on the device (`/usr/bin/diag_efs` did not exist),
  nor had the `diag-bind` service it depends on.
- **★★★ There is no `modem-guard` on the device any more.** `/overlay/fwbackup/` and the S01/K01 service described in §2 are **absent**; the auto-revert net is gone.
  A full manual backup was taken to `/root/fwbackup_20260928/` (`modem.*` + `mba.mbn`) and pulled to `scratch/device_fw/image/` (hash-verified **PASS**).
  **Re-install the guard before any further baseband work.**
- **Device state at end of session:** original firmware **restored** (`modem.b16` md5 `f6f9900e…`; `cmp` = 0 against the backup), modem boots cleanly,
  `+CFUN: 7`, `diagboot` **disabled** (so the EFS instrument is available), NV item unchanged (byte 7 = 0).
- **Refined root question:** *now that the hotswap flag is NV-driven and an EFS write is not persistent, is there any AP-side lever at all — or does the
  offline gate live entirely in modem-internal state that the transplanted firmware computes at boot?*
- **Artifacts:** `scratch/device_fw/image/`, `scratch/device_fw/patched/image/`, `scratch/device_fw/modem_b16_patched.b16`,
  `scratch/p225_falsifier_boot.bin`, `scratch/uim_hw_config.orig.bin`, `scratch/uim_hw_config.byte7is1.bin`, `/tmp/dev16.elf`, `/tmp/dev16_patched.elf`,
  `GitIgnore/compare/ufi001b_hash_tool.py`, `packages/diag-efs/src/diag_efs.c`, `scratch/diagboot_run.sh.current`, `scratch/uz801_diagboot.init`.

---

## 8. Evidence inventory

| artifact | what it is |
| :-- | :-- |
| `evidence/197_bootloop_guard/modem_guard.sh` | the on-device guard (source of truth) |
| `evidence/197_bootloop_guard/modem-guard.init` | the `/etc/init.d/modem-guard` wrapper (`S01`/`K01`) |
| `evidence/197_bootloop_guard/install_modem_guard.sh` | host installer + verifier |
| `evidence/197_bootloop_guard/deploy_uz801_patched.sh` | host deploy helper (verify → install → re-arm) |
| device: `/root/modem_guard.sh`, `/etc/init.d/modem-guard` | deployed guard |
| device: `/overlay/modem_guard/` | `boot_count`, `events.log`, `deployed`, `quarantine/`, `REVERT_DONE` |
| device: `/overlay/fwbackup/hmu05_stock/` | the stock HMU05 modem set the guard restores |
| `scratch/g14_test/pre_v17_noG14_boot.bin` | v17-as-deployed boot capture (Doc 199 §3) |
| `scratch/dispatcher_fdiff.txt`, `scratch/dispatcher_stock_c0659ef0.txt` | Doc 208 §3 — the two CM dispatchers and their full diff |
| `scratch/code_test_{1,4,64}.bin`, `scratch/wide_mask_test.bin` | Doc 208 §4 — the same-boot sweep-invariance captures (`code` = 4772 for every sweep) |
| `scratch/boot_sweep256_try{1..5}.bin`, `scratch/boot_range256_try{1..5}.bin`, `scratch/boot_ctrl64_try{1..4}.bin`, `scratch/boot_after_redeploy.bin` | Doc 208 §6 — the 20-boot blind streak and the sweep-64 control |
| `scratch/ref_sweep64/diagboot_run.sh.{sweep64,sweep256,range256}` | Doc 208 §6 — the instrument variants (the sweep-64 original is md5 `d385dad0905f33637e0f6cfa2cc1a594`) |
| `scratch/diag_logtool_range` | Doc 208 §7 — the range-capable AP tool, md5 `470df474ed9425aa2b62386a8ef0eab4` |
| `scratch/hmu05_stock_boot.log` | Doc 208 §5 — **the stock arm's instrument log; the mask-identity proof** |
| `scratch/g14_test/post_v18_group14_boot.bin` | v18 GROUP-14 boot capture (Doc 199 §3) |
| `scratch/hmu05_stock_boot.bin` | the stock HMU05 boot capture — the comparison arm (Doc 194) |
| `scratch/bootreport.py` | one-shot F3 boot summary used by Doc 199 |
| `scratch/xmap.py` | cross-map UZ801 ↔ HMU05 functions via F3 descriptor hashes (Doc 199 §5) |
| `scratch/diag_online_test.bin` | live 40 s F3 capture spanning the AP's `set-operating-mode=online` on v18 — 973 msgs, **0** `mmocdbg.c`, **0** `cmmsc_auto.c`, `dcc` command freed in 0.66 ms (Doc 200 §4; md5 `746cd4c38fd81420ee05d74a4d65b562`) |
| `scratch/desc.py` | ⚠ **WRONG for this table — do not use.** Its `{u32 ptr_or_hash; u16 file_id; u16 line}` model renders the real entry `0xc165fdf0` as `hash 0x09fe000b file_id=15620 line?=50262`; the correct model is `{u32 packed; u32 strptr}` (Doc 206 §7.3). Superseded by `scratch/desc_table.py` |
| `scratch/fdiff.py` | normalised UZ801-vs-HMU05 function diff — answered "same logic, different data?" (Doc 200 §5) |
| `scratch/diag_online_full.bin` | live 40 s capture **with `cntl-enable 64` first** — 1090 msgs, still **0** `mmocdbg.c`; proves the live mask ≠ the boot mask (Doc 200 §4 amendment) |
| VA `0xc1ba7894` (UZ801 `modem.elf`) | the QMI-mode → CM-opmode jump table (`gp+0xc7c0`, `gp = 0xc1b9b0d4`) — Doc 200 §6.1 |
| VA `0xc1f5a820` (UZ801 `modem.elf`) | the `cm_ph_oprt_mode_e_type` name table — Doc 200 §6.1 |
| `scratch/ref_v18/modem.b16` + `modem.mdt` | the **v18 backup pulled off the device before the v19 deploy** (`a90524d7…` / `6360a026…`) |
| `scratch/uz801_boot.bin`, `scratch/uz801_boot_4.bin` | UZ801 boot captures, **24-file / `mmocdbg.c` = 0** class — they **missed the boot burst**; not admissible for a CM-layer negative (Doc 202 §5.1) |
| `scratch/diag_boot_v7.bin`, `…_v8.bin`, `…_v13.bin`, `…_v16.bin`, `scratch/v19_boot.bin`, `scratch/reboot_postpatch.bin` | UZ801 boot captures, **32-file / `mmocdbg.c` = 60** class — **caught the boot burst**; these are the captures a negative must be scored on (Doc 202 §5.3) |
| `scratch/f3clean.py` | the median-`ts` filter — **sound as a false-positive filter, NOT admissible for first-occurrence timing**: its ±40e6-tick window deletes the stock capture's boot chunk and online-transition cluster (Doc 202 §3.2) |
| `scratch/apply_v19_patches.py` | v19 patcher = v17/v18 groups + GROUP 15 (the `0xc067f4c0` byte) — Doc 200 §6.4 |
| `scratch/uz801_patched/modem.b16` (v19) | `c71d53464a1e88f2072cbbb6f7f87475` — 291 diff bytes vs pristine, **1 vs v18** |
| `scratch/uz801_patched/modem.mdt` (v19) | `9f39ce579114bcf1383bbdce62a8d284` — re-signed by `ufi001b_hash_tool.py patch … 16 …` |
| `scratch/v19_boot.bin` | the post-deploy boot capture scoring v19 — `cmmsc_auto.c` 0, `mmocdbg.c` 60, `cmph.c` 22 (Doc 200 §6.4) |
| `scratch/uz801_boot.bin`, `scratch/uz801_boot_4.bin` | the two early UZ801 boot captures (Doc 194) — their MMGSDI card events are **valid** (`evt=0x13/0x15/0xc`, real `sess_id`), unlike v19's leading `evt=0, sess_id=0` (Doc 201 §5.3) |
| `scratch/uz801_fw/modem.elf` | the joined UZ801-21 image — every VA in Doc 201 is read from here |
| `scratch/boot_sweep256.bin` (md5 `b6d9443495f9552453bddd9c34e88f06`) | the 256-sweep UZ801 boot capture — provenance **proven** (256 `F3_MASK` packets in the live log; instrument restored to 64 at 12:05:34 *after* the 12:00:09 boot); **blind** (`mmocdbg.c` = 0, 24 files) (Doc 203 §3) |
| `scratch/boot_256_prep_64sweep.bin` (md5 `eadb5877ffb047816e88c580060fd931`) | the 64-sweep capture it was compared against — also blind; the 64↔256 delta is **`nvruim.c`** (Doc 203 §3) |
| `scratch/boot_after_user_reboot.bin` (md5 `b6d94434…`) | byte-identical to `boot_sweep256.bin` — both are copies of the device's one `/root/diag_boot.bin` (Doc 203 §3) |
| `scratch/diagboot_run.sh.current` | the fixed instrument, deployed as `d385dad0905f33637e0f6cfa2cc1a594` (previous: `/root/diagboot_run.sh.pre203.bak` = `72e3ba04870b88efc3a61b860f179ab0`) (Doc 203 §5) |
| device: `/root/diagboot.status` | **new** per-boot capture-health record — `boot_id`, instrument md5, start/end uptime, sweep, `out_size`, `out_md5`, and per-file discriminator counts (`mmocdbg.c` etc.); makes a blind capture detectable without re-parsing (Doc 203 §5) |
| device: `/root/diagboot.log` | live instrument log — head verified **ASCII** (`diagboot_run start`, `DIAG dev present after 3s`, `bind rc=0`, `cntl-enable 256 SSIDs at uptime=13.58`, `capture 90s at uptime=13.90`); carries the 256-packet sweep proof. Was 134 726 075 B because the capture tool's stdout was not redirected (fixed) (Doc 203 §5) |
| `Docs/Modem Stability/Modem RE/uz801/modem_full_decompiled.c` | the Ghidra decompile — `FUN_c06951b0` (allocator), `FUN_c069527c` (free, proves `u == cmd+0x14`), `FUN_c06928a8` (`cmmsc_auto`), `FUN_c067cd2c` (dispatcher), `FUN_c0e03974` (DMS set-opmode) |
| VA `0xc1ba7894` (UZ801) | the QMI-mode **code switch table** for `FUN_c0e03974` (Doc 201 §4) |
| VA `0xc0e03a60`–`0xc0e03a74` (UZ801) | the switch's case labels — mode 0 → `r19 = 5` (ONLINE) |
| `thunk_EXT_FUN_d03d3420(0xc,0x1a,0x30f)` | the **external** submit call — the static boundary for the online command's code/ctx (Doc 201 §4) |
| **`scratch/boot_sweep256.bin`** | the **256-sweep** UZ801 v19 boot capture that closed the mask confound (md5 `b6d9443495f9552453bddd9c34e88f06`; 670 clean msgs / 112 distinct classes) — Doc 201 §6 |
| `scratch/boot_256_prep_64sweep.bin` | the 64-sweep capture from the degraded boot before the 256 run (md5 `eadb5877ffb047816e88c580060fd931`) — Doc 201 §7.2 |
| device: `/root/diagboot_run.sh.sweep64.bak` | the 64-sweep instrument backup; **the live script is restored to 64** (the 256 sweep was a one-shot test) |
| `scratch/race_classify.py` (md5 `8cbb1e67af1826aa0e4cb4ccc617071b`) | **new** class/race classifier — delimiter-aware (`f3parse2`), emission-index ordered, **no** median-`ts` filter; reports class, the ordered `=CM= CMD alloc` timeline, the `evt=%d,sess_id=%d` pairs, `gstk*`, `nvruim`/`mcfg_uim` (Doc 204 §3.1) |
| `scratch/race_probe.sh` | the 4-reboot probe that produced `race_boot_1..4` (Doc 204 §3.1) |
| `scratch/race_boot_1.bin` (md5 `83b7a2638e1ddbe8d90b744433a36f64`) | fresh v19 boot — **BLIND**, `u=1 tsk=cm`, `gstk`=0, no `evt=0` (Doc 204) |
| `scratch/race_boot_2.bin` (md5 `565c4a03f77e3252b2c25861b6521373`) | fresh v19 boot — **BLIND** (Doc 204) |
| `scratch/race_boot_3.bin` (md5 `6858004ee78d65ed6077ad062ff14641`) | fresh v19 boot — **BLIND** (Doc 204) |
| **`scratch/race_boot_4.bin`** (md5 `a7f55a973108e325f6e5a20312b7f6e1`) | fresh v19 boot — **BLIND**; **the measured `offline` pair**; equals the device's `/root/diag_boot.bin` **and** the instrument's `out_md5` (Doc 204 §5.1) |
| `scratch/race_boot_{1..4}.status` | the instrument's per-boot health records (`script_md5=d385dad0…`, `sweep=64`, `boot_id`, per-file counts) |
| `scratch/online_attempt2.bin` (md5 `009b145c161609677db5de518d0761ff`) | live capture taken **during** `--dms-set-operating-mode=online`; equals the device's `/root/online_attempt.bin`; shows the `dcc` **72-tick (0.35 ms)** alloc→free ×2, no `tsk=ds`, no opmode line (Doc 204 §7) |
| `Docs/Modem Stability/204_THE_U1_RACE_ATTACKED_IT_CONTROLS_EVT0_BUT_NOT_ONLINE.md` | the race attack, its negative verdict, the class-invariant gate, and the AT/QMI refusal evidence |
| **`scratch/online_battery.bin`** (md5 **`07391dfc81381cd36e796abd7c1e4491`**) | the live 90 s op-mode-battery capture (5 472 107 B) — equals the device's `/root/online_battery.bin`; 22 951 parsed F3 records; CM records reach **`u=1584` (26.4 min)** (Doc 205 §5) |
| device: `/root/battery.sh`, `/root/battery.log` | the device-side battery script (binds DIAG, `cntl-enable`, `capture 90`, issues every op-mode lever) and its `set -x` transcript (Doc 205 §6) |
| **`scratch/uz801_fw/modem.elf`** (rejoined UZ801-21) | the UZ801 ELF used for all static work; 27 phdrs, no sections; seg25 strings = `0xc4558000–0xc4614cec` (Doc 205 §3) |
| **`GitIgnore/MelbonWhiteStock_Dump/modem_extracted/image/modem.elf`** (50 049 024 B) | the **stock HMU05 ELF** — the ground-truth control for the descriptor-table differentials; seg25 = `0xc449a000–0xc44e6000` (Doc 205 §3/§7/§8) |
| `scratch/desc_table.py` | **new** descriptor-table extractor — `{packed, strptr}` entries at an 8-byte stride, `line = packed>>16`, `level = packed&0xFFFF`; the instrument behind Doc 205 §3/§8 (code in Doc 205 §13) |
| VA `0xc165fdf0…0xc165ff00` (UZ801, seg18) | the `cmmsc_auto.c` descriptor-table block — the cluster of `{packed, strptr}` holders (Doc 205 §3) |
| `scratch/uz801_fw/strva.py` | the string-VA resolver used to locate the seg25 string blocks (Doc 205 §3) |
| `immext` encoding | `immword(A) = (A[31:28]<<24) | ((A>>20)&0xFF)<<16 | 0x4000 | ((A>>6)&0x3FFF)` — validated on six `llvm-objdump`-resolved constants; **two mandatory guards** (4-byte alignment, next instruction's low 6 bits) (Doc 205 §4) |
| `thunk_EXT_FUN_d0587538(…, 0x456, FUN_c0691d9c, …)` / `(…, 0x422, FUN_c0692794, …)` | the **external-service registrations** of `FUN_c06928a8` (`cmmsc_auto`) — message IDs **`0x456` / `0x422`** are the open cross-reference lead (Doc 205 §7/§11) |
| `Docs/Modem Stability/205_THE_F3_LOG_DESCRIPTOR_TABLE_AND_THE_COMPLETE_OPMODE_REFUSAL.md` | the descriptor-table instrument, the `immext` derivation, the method-validated no-caller negative, the `u=1584` CM death, the complete op-mode refusal, the `cmmsc_auto.c` newer-revision differential, and the `cntl-enable` trap |
| **`scratch/desc_table.py`** | **new** descriptor-table instrument (Doc 206 §11): `entries` / `file` / `refs` / `u32` modes; supersedes `desc.py` |
| VA `0xc165c250` (UZ801) | line 1266 `cmcc.c:=CM= CC: app_type=%d` — the descriptor proving **`FUN_c06917f0` is `cmcc.c`** (Doc 206 §3.2) |
| VA `0xc165c3b8` / `0xc165c3d0` (UZ801) | lines 3081 / 3115 `cmcc.c:=CM= MMGSDI EPS mmgsdi_status=%d` / `… srv_available=%d, cm_mmgsdi_acl_availability=%d` — the descriptors proving **`FUN_c06928a8` is `cmcc.c`** (Doc 206 §3.2) |
| `scratch/apply_v14_patches.py` / `apply_v19_patches.py` | GROUP 11 = the **`0xc0691950`** patch, labelled "cmmsc_auto.c" — the misattribution; the VA is inside `FUN_c06917f0` (`cmcc.c`) (Doc 206 §4.1) |
| **`Docs/Modem Stability/206_FUN_c06928a8_IS_cmcc_NOT_cmmsc_auto_AND_THE_WMS_INPUT_PATH_IS_INTACT.md`** | the `cmcc.c` retraction with its two independent proofs + the stock-runtime cross-check, the GROUP 11 re-attribution, the falsified no-caller inference, the closed `0x456`/`0x422` lead, the intact WMS/MMGSDI input path, and the three method retirements |
| **`scratch/logsite.py`** | **new (Doc 207 §4)** — the descriptor → `file:line` instrument. Modes `anchors` / `refs` / `func`; token pattern **must** be `(?:0x\|DAT_)([0-9a-fA-F]{8})` (a prefix-specific `c16…` regex silently zeroes the stock arm) |
| **`scratch/immbase.py`** | **new (Doc 207 §4)** — decodes every `immext` in the executable segments and histograms the immediate; used to prove there is **no** descriptor-table base in the `cmmsc_auto.c` region of either build |
| **`scratch/va2func.py`** | **new (Doc 207 §4)** — VA → enclosing function via the `/* ---- Function: … @ va ---- */` markers; caches `<decompiled.c>.funcidx`; 81 800 UZ801 functions indexed |
| **`scratch/descref.py`** | **new (Doc 207 §3.2)** — the earlier descriptor↔decompile join; carries the `off2va(...) is None` guard that fixes `desc_table.py`'s crash-to-empty-output |
| `scratch/uz801_logsites.txt` / `scratch/hmu05_logsites.txt` | **new (Doc 207)** — the per-build descriptor→code maps: **69 775** UZ801 sites, **68 186** stock sites |
| `scratch/uz801_desc.tsv` | **new (Doc 207)** — UZ801 descriptor table, resolved-strptr subset (11 168 entries) |
| VA `0xc165fd70` / `0xc165fe00` / `0xc165fe28` / `0xc165ff00` (UZ801) ↔ `0xc155f740` / `0xc155f7d0` / `0xc155f7f8` / `0xc155f8b8` (HMU05) | **new (Doc 207 §7.2)** — the four `cmmsc_auto.c` descriptor pairs (lines 1872 / 2651 / 2849→2858 / 3241→3250). Referenced by **no literal and no `immext` in either build**; recorded so the next attempt starts from them |
| **`Docs/Modem Stability/207_THE_DESCRIPTOR_MODEL_CORRECTED_THE_DESCRIPTOR_TO_CODE_INSTRUMENT_AND_THE_DEATH_OF_THE_CMCC_AXIS.md`** | the corrected descriptor model (hash vs strptr, ~92/8), the `desc_table.py` crash, the build-independent hash measurement (10 039 / 11 200 = 89.6 %), the new instrument, the two-arm `cmcc.c` inventory (13/90 vs 12/89), the `FUN_c06928a8` ≡ `FUN_c066fa30` identity, and the four method corrections |
| **`Docs/Modem Stability/208_THE_IDENTICAL_CM_DISPATCHER_THE_CODE_FIELD_IS_NOT_THE_MASK_AND_THE_LOSS_OF_THE_F3_BURST_CLASS.md`** | the CM dispatcher is **structurally identical** (83.49 % raw `fdiff` → 2 declaration-order blocks after GP-offset normalisation), the proof that both arms used the **same `cntl-enable 64`** instrument, the proof that the F3 `code` field is **not** the AP-selected SSID, and the loss of the F3 burst-capture class (20 consecutive blind boots, four causes falsified) |
| **`Docs/Modem Stability/210_THE_BOOT_CAPTURE_CLASS_RESTORED_THE_REFUTED_CM_NEGATIVES_AND_THE_POLICYMAN_RAT_MASK_ZERO.md`** | the burst class restored (a 1 s DIAG poll → `sleep 0.05`; verified 4/4), the retraction of "the CM serving-system layer never runs", the refutation of "`cmmsc_auto` never runs", the policyman end state (`Filtered RAT mask 0 based on HW capabilities 544`), the EFS controls, the census-invisible `MSC_AUTO` divergence, and the Doc-210-era parser defect #1 (dropped records) |
| **`Docs/Modem Stability/211_THE_ARGS_SHIFT_PARSER_DEFECT_AND_THE_FIRST_CLEAN_SAME_WINDOW_DIFFERENTIAL.md`** | the `f3parse.py` **args-shift defect #2** (args scaled by 256^extra) and its three corroborations; the killing of the `mode_pref` 9728-vs-38 and `MSC_AUTO` 0x220-vs-0xebe differentials; the falsification of Doc 210 §5's policyman lead by a **stock boot on the restored instrument**; the **stock `online` + JIO 4G control**; the per-subsystem `ts`/`code` structure; and the **first same-window stock-vs-UZ801 differential** — the UZ801-only MCFG-UIM/STK bring-up with a failing NV read, the SD `ssscr_user_offline_cdma` script, and the MMOC `OPRT_MODE_CHGD → 3(OFFLINE)` decision |
| `scratch/doc211/stock_boot_240.bin` | **new (Doc 211 §4.5)** — stock boot on the restored instrument, 240 s, `admissible=yes` (`rcinit_init.c` = 81), md5 `0ff4e4d22c23443e2c6e82bcb24cbc83` |
| `scratch/doc211/stock_boot_211.bin` | **new (Doc 211 §4.4)** — the stock arm that reaches the **policyman block**; md5 `e49515eff18afcf069215bde6a35a835` |
| `scratch/doc211/uz801_v19_boot_240.bin` | **new (Doc 211 §6.3)** — UZ801 v19 boot, 240 s, `admissible=yes` (`rcinit_init.c` = 110), md5 `81318b04f7e0da3e74675d2973366758` |
| `scratch/doc211/live_stock_online.bin` / `.gz` | **new (Doc 211 §4.6)** — live online-modem reference (25 862 records / 60 s, `Prot_state [MAIN] 5(ONLINE_GWL)`), md5 `e765744410d570e9eb0423bc46b58f9f` / gz `119085b9bb75ff4b18ad90e2e45bf0af` |
| `scratch/f3parse.py` | **fixed (Doc 211 §2)** — args now read at `k+20+extra+4*j`; md5 `9f9d6edf15d64faf3b47897f7838c9ac`. Pre-fix kept as `scratch/f3parse.py.pre211` (`59ccb3db69d084eae415dd940d7cdf1a`) |
| `scratch/doc211/restore_uz801_v19.sh` | **new (Doc 211 §6.1)** — the UZ801 v19 restore (68-file manifest, `awk` not `comm`); md5 `51965239169cd8c048632f8678fef2d8` |
| on-device `/root/diagboot_run.sh` (240 s variant) | **new (Doc 211 §4.5)** — instrument with `DIAGBOOT_SECS=240`; md5 `9cd993a7f214b6847e92a7c21ca8c8c8`. The 90 s original is backed up on-device as `/root/diagboot_run.sh.secs90` |
| **`scratch/coredump_uz801/uz801_coredump.elf`** | **new (Doc 217 §3)** — **THE UZ801 COREDUMP.** 84 407 190 B, md5 **`59855a4cfac73e7dd2a4b54032ff5c42`**, ELF32 `ET_CORE`/`EM_NONE`, **21 phdrs**; every segment matches the UZ801 ELF phdr-for-phdr incl. the three `filesz = 0` regions (phdr 15 = seg[20], phdr 16 = seg[21], phdr 20 = **seg[26]**). Device copy `/root/uz801_coredump.elf` |
| **`scratch/coredump_uz801/seg26.bin`** | **new (Doc 217 §7)** — the verbatim seg[26], **11 448 320 B** (coredump VA `0x8ae15000`, ELF VA `0xc4615000`). Holds the MMOC string pool + the UZ801 source paths |
| **`scratch/coredump_uz801/xlat.py`** | **new (Doc 217 §6)** — the coredump↔ELF↔native translator: `NATIVE_BIAS = 0x456b1000`, `ELF_BIAS = 0x39800000`; `read_cdva`/`read_native`/`read_elf`/`cstr`/`native_str`/`resolve`/`u32_native` |
| **`scratch/coredump_uz801/mmocptr.py`** | **new (Doc 217 §8.1)** — the MMOC-token string scan + the three-encoding pointer search that produced the **0-hit negative** (locate pool strings, then search the whole image for 4-byte LE words equal to their addresses) |
| **`scratch/coredump_uz801/findtables.py`** | **new (Doc 217 §8)** — the native-pointer-run finder (found the IMS tables at `len=84 resolved=84` / `len=399 resolved=399`; **not** the MMOC tables). Output `findtables.out` (6 434 lines) |
| **`scratch/coredump_uz801/stocktbl.py`** | **new (Doc 217 §9)** — the stock `mmoc_cmd_name[]` reader (ELF VA `0xc1d33eb0`, file `0x1ca0eb0`); shows the stock pointers are `0xd0d77xxx` (native, into the stock's `filesz = 0` seg[26]) |
| **`scratch/coredump_uz801/buildpaths.txt`** | **new (Doc 217 §10)** — the **128 leaked source paths**, all under `/home/chiyong/UZ801_V3.0-21/msm8939-la-1-0-2-1_amss_qrd_no-l1-src/modem_proc/` (MSM8939 tree; **`no-l1-src`** package) |
| **`scratch/coredump_live/modem_coredump_up919.52.elf`** (85 398 475 B) | **the STOCK coredump** — the **control** for Doc 217 §9's name-level diff (its `mmoc_cmd_name[]` pointers land in its `filesz = 0` seg[26]); pre-existing, reused |
| **`Docs/Modem Stability/217_THE_UZ801_COREDUMP_CAPTURED_THE_BAM_DMUX_SSR_TEARDOWN_HANG_AND_THE_SEG26_CONTENTS.md`** | the capture (the `rmmod`-first recipe), attempt 1's fourth AP-hang site and its exact block window, the A/B, the retired `rmmod` hazard, the coredump verification + the two address relations, seg[26] + the MMOC string pool, the name-level diff, the build provenance, and 13 traps |
| **`scratch/coredump_uz801/tbloff.py`** | **new (Doc 218 §3)** — the memory-safe offset/decisive probe: Test 2a (absolute-offset runs), Test 2b (base-independent difference-pattern), Test 3 (the decisive count into the command-name range), the native-pointer-abundance control, and the 14-pointer-array dump. Uses `mmap` + `array.array` (the naive form OOM-kills this host) |
| **`scratch/coredump_uz801/tblhunt.py`** | **new (Doc 218 §3.1/§3.5)** — the segment map, the all-copy exact-token finder, the three-encoding pointer search, and the ELF-phdr comparison that identified the MBN attestation header |
| **`Docs/Modem Stability/218_THE_UZ801_MMOC_NAME_TABLE_IS_NOT_IN_THE_IMAGE.md`** | the three negative tests (pointer / offset / decisive count), the stock positive control, the "not outside the captured segments" result (the MBN header), the pool's runtime structure, the zeroed 14-pointer array, the mechanism hypothesis, and 8 traps |
| **`packages/diag-bind/`** | **new (Doc 219)** — `Makefile` `9b4fc036…`; `files/diag-bind` `a9c2513b…` (the bridge helper), `files/diag-bind-watch` `8f689b56…` (the SSR watcher), `files/diag-bind.init` `b1c8c200…` (procd, `START=11`), `files/diag-bind.defaults` `dd83b205…` (uci-default: enable+start) |
| **`packages/diag-efs/`** | **new (Doc 219)** — `Makefile` `349be7b0…`; `src/diag_efs.c` `56e38545…` (613 lines) — EFS2 read + the guarded `put` (write layout, POSIX open flags, backup, read-back verify) |
| **`packages/diag-logtool/`** | **new (Doc 219)** — `Makefile` `8f14afe2…`; `src/diag_logtool.c` `21afc46e…` (22 384 B) — the F3/log-mask/capture instrument of Doc 196, packaged |
| **`Docs/Modem Stability/219_DIAG_TOOLING_PACKAGES_DIAG_BIND_DIAG_EFS_AND_DIAG_LOGTOOL.md`** | the three packages, the EFS2 WRITE reverse-engineering (4 bugs), the open-flag table, the `put` safety design, the on-device validation, the auto-bind rationale, and the `REMOVE=8` discovery (documented, not implemented) |
| **`Docs/Modem Stability/220_THE_SIM_INIT_CM_COMMAND_LIFETIME_IS_THE_CLASS_INDEPENDENT_DIFFERENTIAL.md`** | **new (Doc 220)** — the `u=1 tsk=cm` alloc→free lifetime differential (UZ801 276–348 vs stock 14 816/14 852, 42.6×, zero overlap, 11 vs 2 captures), the invariant MMGSDI response offset (10 916–11 924 in all 13 comparable captures), the in-window content diff, the new class-agnostic instrument, the three corrections (Doc 203 §3.2, Doc 204 §6, the old burst class), the bounded emitter-hunt negative, the upheld Doc 218, and the pre-registered scoring criterion |
| **`scratch/emitter18_evidence.py`** | **new (Doc 220 §4.1)** — the evidence-table generator: for every capture, the `u=1` command lifetimes, whether `WMS_CFG_EVENT_MS_SIM_INIT_START` precedes the first `u=1` alloc, the first MMGSDI-response offset, and the first `cmmsc_auto` after it |
| **`scratch/emitter13_lifetime.py`** | **new (Doc 220 §3)** — per-capture alloc→free lifetime dump for **every** `(u, tsk)` CM command key |
| **`scratch/emitter15_all.py`** | **new (Doc 220 §4.1)** — every `u=1` CM command, any `tsk`, all captures |
| **`scratch/emitter17b.py`** | **new (Doc 220 §5.4)** — long (≥ 1 000 tick) CM commands plus all `u=1` commands, both arms |
| **`scratch/emitter19_setdiff.py`** | **new (Doc 220 §4.6)** — the set-compare of the two long `tsk=cm` windows (4 common sites of 43 vs 21 ⇒ **different commands**) plus the `u=0 tsk=cm` alloc/free dump for stock (seven allocs, **never freed**) |
| **`scratch/emitter20_before.py`** | **new (Doc 220 §4.7)** — the 45 records preceding the `u=1 tsk=cm` alloc, both arms; the evidence that the inputs are identical and that the "uninitialised session" is in **both** |
| **`scratch/emitter7_tbltest.py`** | **new (Doc 220 §1)** — the Doc 218 re-test. ⚠ **must `os.chdir()` into `scratch/coredump_uz801/` first**, because `xlat.py` opens `uz801_coredump.elf` by a **relative** path |
| `scratch/emitter8_traj.py` … `emitter16_u0cm.py` | **new (Doc 220)** — the intermediate probes: the op-mode trajectory, the `dcc`/`CMD alloc` timeline, the `u=0 tsk=cm` window, the `Prot_state` trajectory, the `u=1` window dump, the module proximity check |
| `scratch/doc210/boot_210_canon.bin` | `b693faebb2bab65ca5c9eb948285658b` — the Doc 220 primary UZ801 sample (burst-grade, `u=1` = `cm`) |
| `scratch/uz801_boot.bin` / `uz801_boot_4.bin` | `34208c078aa8aae3402cce3faa98dea9` / `ebf0d6b9d2689d92ea0fcea8ff569cfa` — blind UZ801 samples |
| `scratch/patched_boot.bin` / `reboot_devcfg_boot.bin` / `doc209/boot_early_try1.bin` | `6486848163eb5519b221d075d04d99ae` / `975c28fa10c355b8a913194b746c05e2` / `bd3d79d93a590af50fcdf53161e0b084` — further UZ801 samples |
| `scratch/diag_boot_v7.bin` / `v8.bin` / `v13.bin` / `reboot_postpatch.bin` / `g14_test/post_v18_group14_boot.bin` | `ea1dac6332d69634b4e964301333ef97` / `1e393ef3de2537ec02dd1f5a71dec0e7` / `934e10b19c472dc0460255ab9372e64e` / `d4f1017fd49c713b15968f9a6314939f` / `73843bfcd0975f8c9c171e6436df1475` — the five **no-`u=1 tsk=cm`** UZ801 captures (the Doc 203 burst class), whose `u=1` is `mmgsdi_1`/`gstk` and **also short** (4–192 ticks) |
| **`scratch/emitter36_afterfree.py`** | **new (Doc 220 §4.8 / ledger item 26)** — every record from the `u=1 tsk=cm` alloc to free+60, annotated `IN` / `FREE` / `OUT`; the tool that shows the downstream work is present **after** the free |
| **`scratch/emitter37_vsid.py`** | **new (item 26)** — the `cmph.c =CM= vs_id` / `qmi_nas.c cmsubs_evt_cb` / `lte\|wlan_voice_system_id info changed` / `629` / `626` family census over 5 captures, with each family's offset relative to the `u=1` alloc |
| **`scratch/emitter38_cmmsc.py`** | **new (item 26)** — `cmmsc_auto.c` burst positions + MMOC `SUBSCRIPTION_CHGD` receipts + `New transaction 1(SUBSC_CHGD)`, annotated `IN`/`OUT` relative to the `u=1` alloc |
| **`scratch/emitter33_intrawindow.py`** | **new (Doc 220 §5.5)** — the intra-window record census by `(file, line, fmt)`; its `mmgsdi_refresh.c` row (`2/2` inside vs `5/5` whole-capture, both arms) is the window-artefact tell |
| **`scratch/emitter34_refresh.py`** | **new (§5.5)** — the full `mmgsdi_refresh.c` census per capture; shows the `[88]×3 [71]×3 [14]×2 [11]×2 [29][0][6][0][1]` group is present on **both** arms |
| **`scratch/emitter22_counts.py`** / **`emitter23_fmts.py`** / **`emitter24_lifecycle.py`** | **new (Doc 220 §5.6)** — the MMOC marker census, the exact `fmt` strings for the transaction/`Prot_state` records, and the transaction state machine side by side (the `DUAL_STANDBY_CHGD` correction) |
| **`scratch/emitter25_dcc.py`** / **`emitter31_opmode.py`** / **`emitter26_gap.py`** | **new** — the `dcc`/op-mode carriers, the unfiltered window around `OPRT_MODE_CHGD`, and the `WAIT_DEACTD_CNF_GWL → PROT_PH_STAT_ENTER` gap |
| **`scratch/emitter27_files.py`** / **`emitter28_pool.py`** / **`emitter30_ctrl.py`** | **new (Doc 220 §5.7)** — the file-presence census, the F3-file-string → ELF-segment map (Hexagon ⇒ **ELF32**, `e_phoff` at `0x1c`), and the ±0x6000 local control for the `uimsub_manager.c` static negative (358/513 neighbours present in stock) |
| **`scratch/emitter32_window.py`** | **new (§5.5)** — the exact `u=1 tsk=cm` window dump, all four captures |
| **`scratch/emitter35_cmds.py`** | **new** — every `cmdbg.c` CMD alloc/free record in `ts` order (proves the `(u=1, tsk=cm)` pairing is unique) |
| **`GitIgnore/compare/modem_hmu05_extracted/hmu05_modem.bin`** + `…/image/modem.b00…b25` | the **stock HMU05 firmware image** used for the Doc 220 §4.8 static string tests (no `b26` — seg[26] has `filesz = 0`, per Doc 217) |
| **`GitIgnore/UZ801 Modem/modem.bin`** | the **UZ801 firmware** (FAT16 container) used for the same static tests |
| **`evidence/221_b08_live_and_static/uz801_b08_live_online_attempt.bin`** | **new (Doc 221 / item 27)** — `d56f344ba0576f5b6417a3de45a8a2c5`, 221 750 B, 859 msgs — the **first live F3 capture on the deployed B08 build**; the two `=CM= CMD alloc/free u=7023/7028, tsk=dcc` pairs (136 / 72 ticks) are the two `--dms-set-operating-mode=online` attempts |
| **`evidence/221_b08_live_and_static/device_modem_manifest.txt`** | **new (item 27)** — the 21-file on-device `/lib/firmware` modem manifest; `diff` against `scratch/fwdl/uz801_v32_x/image/` is clean ⇒ **the device runs B08 (`UZ801_V01R01B08`)**, not the campaign's B10 |
| **`scratch/fwdl/uz801_v32_x/image/`** | **new (item 27)** — **the deployed UZ801 set** (B08 / `-11`), carved from `uz801_v3.2_stock.zip`; `rfc_*` = 168, `rfc_wtr1605_*` = 137, `rfdevice_(pa\|asm)_*_data_ag` = 75 |
| **`scratch/fwdl/uz801_v1_x/image/`** | **new (item 27)** — the third UZ801 family (`ver_info` `M8916AAAAANLYD1127.2`), never deployed; `rfc_*` = **159** (9 fewer than B08: the `chile_rp4_*` and `chile_v2_sc_{cdma,gnss,tdscdma}_ag` classes) |
| **`scratch/live_b08/`** | **new (item 27)** — working copy of the live capture |
| **`scratch/doc211/stock_boot_211.bin` / `stock_boot_240.bin`** | the **same-window stock captures that DO cover the policyman init** — the control that **retracts Doc 210 §5** (identical 12-record `policyman_rat_capability.c` sequence, identical `[0,544]`/`[0,2]` args, stock online) |
| **`scratch/doc222_latch_differential.py`** | **new (Doc 222 / item 28)** — the single reproduction of Doc 222 §1–§4: the MMOC state timelines of the boot `SUBSC_CHGD`, the per-arm report census (`PH_STAT_CHGD_CNF` / `PROT_DEACTD_CNF` / `OPRT_MODE_CHGD` / `7(OFFLINE)`), the whole-image `uimsub_manager.c` census, and the stock↔UZ801 `cmph.c` line map |
| **`scratch/doc221_rfdevice.py`** | **new (Doc 221 §9 item 1 / item 28)** — the `rfdevice_(pa\|asm)_*_data_ag` registry census: 8 complete PA+ASM families stock-only, `sky77643` the only UZ801-only pair, 80/96/144-byte record stride, scattered (non-contiguous) missing indices |
| **`scratch/hmu05_stock_boot.bin`** | **the Doc 222 §1 control** — stock HMU05, **33 743 records, reaches ONLINE**; its `Recvd command 2(OPRT_MODE_CHGD)` @3040011632 opens **`New transaction 2(ONLINE)`** with `Prot_state [MAIN] 0(NULL)` unchanged ⇒ `3(OFFLINE)` is a *propagation* of the CM's op-mode, not an MMOC decision |
| **`scratch/doc223_subscription_gate.py`** | **new (Doc 223 / item 29)** — the single reproduction of Doc 223 §1–§6: the five capture **spans** (the length-bias trap), the `is_gwl_subs_avail` gate across all five captures, the `ssscr_user_offline_cdma` activation census, the `cmss.c` subscription-processing census, and the **UZ801-only file-name census**; enforces the `f3parse.py` md5 gate |
| **`scratch/efs_H/efs_H/mcfg/*`** | **new (Doc 223 §5.1)** — the MCFG EFS items the UZ801-only `mcfg_uim.c` reads: `mcfg_autoselect_by_uim` = **`05`**, `mcfg_last_autoselect_iccid` = ASCII **"89918610400794228644"**, plus `mcfg_def_config_{hw,sw}_version` and `mcfg_sw_muxd_version` |
| **`scratch/efs_H/efs_H/modem/uim/uimdrv/*`** | **new (Doc 223 §6.1)** — the six uimdrv items `FUN_c053e66c` reads; **`nv_active_slot_configuration` (7 B) and `nv_pdown_uim_consecutive_techproblems` are ABSENT**, the other four present. The absence is **gracefully handled** (fallback to slot 1, which is valid, so no second log) |
| **`scratch/doc210/boot_210_canon.bin`**, **`scratch/doc211/uz801_v19_boot_240.bin`**, **`scratch/doc211/stock_boot_211.bin`**, **`scratch/doc211/stock_boot_240.bin`** | the four captures behind the Doc 223 §2–§4 differentials (2 UZ801-B10 vs 2 stock boot arms); spans differ by **two orders of magnitude** (§1) |
| **`scratch/diag_efs_skip.c`** → `/root/diag_efs_skip` | **new (Doc 224 §2 / item 30)** — the REPAIRED EFS reader: reads until a wall-clock budget expires, discarding the continuous F3 stream, matching only `4b 13 <subcmd> 00`; `EFS_DUMP=1` prints REQ/RESP hex. Built `-static` with the in-tree `openwrt/staging_dir/toolchain-aarch64_generic_gcc-14.3.0_musl` toolchain |
| **`scratch/diag_probe.c`**, **`scratch/diag_probe2.c`** → `/root/diag_probe`, `/root/diag_probe2` | **new (item 30)** — raw DIAG send-and-dump, and the traffic **classifier** that measured **19/20 reads = F3** (the root cause of `bad resp 79`). **⚠ `diag_probe … raw "00"` RESET THE AP** — see item 30 §3 |
| **`evidence/224_efs_live/efs_live_20260928.txt`** | **new (item 30 §4)** — the frozen live EFS listing, **1 315 entries**; `0` HMU05-reference paths missing, `184` runtime-created extras |
| **`scratch/doc224_nv_diff.py`** | **new (item 30 §4.2)** — every `/nv/item_files/…` literal in both ELFs (UZ801 **608**, stock **510**) diffed against the live EFS |
| **`scratch/doc224_cmregprx_desc2.py`** | **new (item 30 §5)** — the complete `cmregprx.c` descriptor table of both builds (**22** entries each) with the line-shift staircase +30…+49 |
| **`scratch/doc224_counts.py`**, **`scratch/doc224_span.py`**, **`scratch/doc224_bootwindow_diff.py`** | **new (item 30 §6)** — whole-capture and index-matched boot-window F3 censuses; the length-bias control for Doc 223 §1 |
| **`GitIgnore/UZ801 Modem/UZ801_Stock/dump/{modemst1,modemst2,fsg}.bin`** | **new (item 30 §4.4)** — the real UZ801 device's NV partitions; **0 plaintext EFS path strings in all three** ⇒ the UZ801 NV is still not an oracle |
| **`scratch/doc225_refs2.py`** | **new (Doc 225 §2 / item 31)** — the **CORRECTED** raw-ELF Hexagon `immext` scanner. The shipped `desc_table.py refs` GUARD 2 is wrong; validated 0→5 hits on the known-positive `0xc165a690`. |
| **`scratch/doc225_str_refs.py`** | **new (item 31 §5)** — the **NV-literal → code-address** finder (the route that reaches `uimsub_manager.c`). ⚠ must key on the **decoded** address, because `llvm-objdump` prints `immext(#<address>)`, not the raw word. |
| **`scratch/doc225_emitter_map.py`** | **new (item 31 §3)** — emitter → descriptor-page map: `FUN_c091d840` 17 137 sites/230 pages, `FUN_c08a4c90` 12 782/123, `FUN_c0287020` 4 569/85, `FUN_c091da60` 4 669, `FUN_c091d8c0` 2 638/215 |
| **`scratch/doc225_window_refs.py`** | **new (item 31 §4 test 4)** — exhaustive per-0x40 immext scan of a VA window; proved `0xc165e000`–`0xc1660000` has **no code** reference (5 unaligned seg[24] coincidences only) |
| **`scratch/doc225_window_dump.py`**, **`scratch/doc225_table_map.py`**, **`scratch/doc225_file_desc.py`**, **`scratch/doc225_desc_hits.py`**, **`scratch/doc225_find_ref.py`** | **new (item 31)** — the descriptor-window/segment/page censuses and the per-descriptor key checks |
| **`scratch/doc225_immext_hits.txt`** | **new (item 31 §4 test 3)** — the (empty) result of `llvm-objdump -d` grepped for `immext(#0xc165[ef]`, with the in-file positive control `immext(#0xc165a680)` = 5 |
| **`/tmp/uz801_dis.txt`** | **regenerable** — the full-ELF Hexagon disassembly, 5 219 227 lines; the substrate for test 3 and the emitter map |
