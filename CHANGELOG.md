# Changelog

All notable changes to this project are documented in this file.

The primary source of truth for the modem-stability investigation is
`Docs/Modem Stability/197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md` (a living
document; each §-entry is a changelog entry). This file records commits that
modify that ledger, for quick reference.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### 2026-10-04 — P-V29-RAWWAKE SCORED: the raw WAKEUP handler runs at the event (stall is DOWNSTREAM)

- **Ledger §112.74** — ran v29 (ring on the entry of the raw RFA WAKEUP handler `0xc1017818`) with
  SSR disabled, to the ~900 s event.
- **FATAL at modem-uptime 903.46 s** (AP 1718.567 s − modem restart 815.107 s), site
  `lte_ml1_common_timer.c:390`. Dump `scratch/v29_run/dump_FATAL.bin` (85 398 475 B).
- **H1 PASS** (marker `0x76323901` + site `0xc1017818`), **H2 PASS** (`seq = 1162`).
- **★ H3a SUPPORTED / H3b FALSIFIED:** the raw WAKEUP handler is **entered 1162×**, all via the WAKEUP
  stub (`caller = 0xc10187a0`), regular cadence right up to the recorded end ⇒ the raw op `0x60702aa`
  **DOES fire**; the stall is **NOT upstream** of `0xc1017818`.
- **Corroboration:** RFMGR state 5 (SLEEP) + `ctx+0x104 = 0x4290203` (WAKEUP pending); SLEEPMGR state 9.
- **Residual (honest):** an entry-only ring cannot separate "every entry completed, last CNF lost"
  from "last entry's RF-script work blocked" ⇒ **v30 needs a ring at the handler exit / CNF post
  (`0xc10179a8` / `0xc0316600`)**.

### 2026-10-04 — The router flash-storage bug: overlayfs→ext4→jbd2 unlink oops wedges the overlay

- **Ledger §112.72** — documented the AP-side (OpenWrt/kernel) storage fault found while setting up
  the v29 run; **unrelated to the ~900 s modem event**.
- **Symptom:** every **overlay write hangs forever** (`/root`, `/etc`, `/lib` on `mmcblk0p15` ext4);
  **tmpfs writes work**; **reads work** (deployed v29 image `398dce8a…` intact); load ≈ 7.5.
- **Oops (deterministic, uptime 39 s):** `PID 6049 Comm: rm` → `ovl_unlink` → `ext4_unlink` →
  `ext4_orphan_add` → `jbd2_journal_get_write_access` → **`jbd2_write_access_granted+0x14`** fault
  `(f9400020)` dereferencing poison `x1 = 0xc568142b5fe526b7` (the `s_sbh` handed to jbd2).
- **Wedge mechanism (proven by kernel stacks):** the oopsed `rm` dies holding an open jbd2 handle ⇒
  `PID 187 [jbd2/mmcblk0p15]` stuck in `jbd2_journal_wait_updates`; `PID 12 [kworker/u16:0+f]` stuck
  in `wait_transaction_locked` ⇒ **no new transaction can start** ⇒ all writers block in `D`.
- **Trigger:** the diagnostic scripts launched from `/etc/rc.local` churn files on the overlay —
  `f3cap.sh:54/59` and `dumpwatch.sh:40/52` `rm -f`. Live `…/c000005_up00039.raw` ("up00039" = the
  oops uptime) pins it to `f3cap.sh`.
- **★ A reboot does NOT fix it (hypothesis FALSIFIED):** the oops recurred at the same point, same
  PID 6049 — `f3cap.sh` re-runs at boot and re-triggers. The wedge is in-memory (a dead task's
  handle), so breaking the trigger should give a healthy boot.
- **Blocked:** the v29 run setup (`uci commit` to disable the pre-emptive/Stage-3 SSR cannot persist).
- **RESOLVED 2026-10-04 (ledger §112.72.8):** edited `/etc/rc.local` **out-of-band via the raw block
  device** (journaled writes were wedged): `ls -i` → inode 170 → ext4 superblock (bs 4096, inode 256)
  → group desc 0 (`bg_inode_table_lo` @0x08 = 458) → inode offset 1,919,232 → extent `start_block`
  685158 (byte 2,806,407,168); verified md5, then wrote a **same-length 2001-byte** replacement
  (dropped the `f3cap`/`dumpwatch`/`hang_probe_t4` churn; kept `coredump-enable`) with
  `dd … seek=685158 conv=notrunc,fsync`. After a **`sysrq-b`** reboot (`reboot -f` did nothing):
  `/etc/rc.local` = `7052eb35…`, **0 oops**, `jbd2` healthy, **overlay writes OK**, firmware intact.
  ⚠ A content search (`strings -t d`) found a **stale** older copy — resolve via the INODE.
  Artifacts: `scratch/flash_bug/`.

### 2026-10-04 — Task #278: the raw radio wakeup event is RFA operation 0x60702aa

- **Ledger §112.71** — traced the raw event that must drive the RFMGR wakeup completion.
- **★ The RFMGR is an STM** (class `0xc1a8b9d0`, obj `0xc1e145a8`, 8 states × 20 messages); handler
  table `0xc1a8bb40` decoded in full. SLEEP handler[18] `LTE_ML1_RFMGR_WAKEUP_REQ` (`0x4290203`) =
  `0xc0315490` (issue); SLEEP handler[19] `RFA_RF_LTE_WAKEUP_CNF` (`0x60708aa`) = `0xc0315560`
  (completion) ⇒ the completion is a **software message** delivered to the STM.
- **★ The raw radio wakeup event = RFA operation `0x60702aa`** — one of a 1:1 family of 10 RFA ops
  (request `0x60702a1..aa` ↔ CNF `0x60708a1..aa`, offset +0x600). The RFMGR **sends** `0x60702aa`
  (absent from its received-message filter table `0xc1e14520`) and **receives** `0x60708aa`.
- **★★ gp resolved = `0xc3c09000`** (set at `0xc0000670`), unlocking every gp-relative jump table:
  the CNF builder `0xc0316080` table = `0xc1a8b940` (10 entries, idx 9 → WAKEUP → posts `0x60708aa`);
  the RFA server dispatcher `0xc1018620` table A = `0xc1b328a8` (26 entries, **idx 9 → `0xc1018798`
  → `0xc1017818` = the raw WAKEUP handler**).
- **★ Where the stall sits:** the raw op `0x60702aa` is never completed ⇒ `0x60708aa` is never
  posted ⇒ the RFMGR handler `0xc0315560` never runs. The issue side did run (pending set). **The
  resource that stops the RFA layer completing `0x60702aa` at ~900 s remains OPEN.**
- **Artifacts:** `scratch/_rfmgr_stm.py`, `scratch/_rfatbl.py`, `scratch/_find_u32.py`,
  `scratch/_dump_region.py`, `scratch/_rd_ptr.py`.

### 2026-10-04 — Task #277: the RF confirmation stall is a stuck RFMGR wakeup transaction

- **Ledger §112.70** — named the RF entity and the stall signature.
- **★ The "RF driver" is the `LTE_ML1_RFMGR` STM** (class `0xc1a8b9d0`, obj `0xc1e145a8`, 8 states
  INACTIVE/ACTIVE/RX_TUNED/TUNING/TX_TUNED/SLEEP/SCRIPT_EXEC/SCRIPT_BUILD; msgs `0x4290200..06`).
- **★★★★ FALSIFIED:** the RFMGR state is NOT a FATAL/WEDGE discriminator — the FATAL state is BOTH
  4 (TX_TUNED) and 5 (SLEEP); the WEDGE state is 5 (3/3). (The *sleepmgr* state 9/4 remains the clean
  discriminator.)
- **★★★★★ NEW signature:** the RFMGR context field `ctx+0x104` holds the in-flight request
  (`0x4290202` SLEEP / `0x4290203` WAKEUP). **Every event dump in SLEEP has `0x4290203` (9/9);
  every non-event dump in SLEEP has `0x0` (5/5)** (Fisher p ≈ 5×10⁻⁴) ⇒ the RFMGR is stuck in SLEEP
  holding an unserviced WAKEUP_REQ.
- **★★★★ Code path:** an issue/completion pair per request (WAKEUP issue `0xc0315490` / completion
  `0xc0315560`; SLEEP issue `0xc03152f0` / completion @`0xc0315420`). The completion emits the CNF
  and clears `ctx+0x104`; at the event it never runs. The callback slots (`ctx+0x1b8`/`ctx+0x1f0`)
  are **registered (non-zero)** ⇒ **not** a missing-callback bug — the **async RF completion event
  itself** does not arrive (supports hardware/resource, not a software timer). Root remains OPEN.
- **Artifacts:** `scratch/rfmgr_dump.py` (reader).

### 2026-10-04 — P-DET continuation (n=6): the race reproduces; 3 new dumps re-confirm §112.68; a leftover capture script contaminated the soak

- **Ledger §112.69** — the P-DET soak produced one more modem boot with three events under the
  **same fixed observation config**: **FATAL** `lte_ml1_sm_conn_inter_freq_stm.c:712` @ modem-up ≈901 s,
  **FATAL** `lte_ml1_sleepmgr_stm.c:4054` @ ≈900 s, **WEDGE** @ ≈900 s (data death; `crash detected …
  type watchdog` +175 s). ⇒ **FATAL, FATAL, WEDGE again — the §112.67 RACE verdict is now n = 6.**
- **★ The mitigation's ON→OFF transition is visible in one boot.** `msm_subsys: restarting` at
  812.79 s and 1623.23 s (Δ = 810.4 s ≈ the 800 s pre-emptive interval) held the event off across two
  cycles; after it was disabled the first fatal landed at **2524.5 s = 1623.2 + 901.3 s**.
- **★★★★ 3 new coredumps re-confirm §112.68** (`scratch/pdet_end/dumps/`): the 900 s timer's remaining
  is **+21.73 s** (sm_conn fatal) and **+11.55 s** (sleepmgr fatal) — i.e. it fires *after* the event —
  and is **absent** from the wedge dump (already fired ≈4363 s, not re-armed). **★ NEW: the 900 s
  timer is a per-restart one-shot**, armed ≈14–25 s after the preceding recovery.
- **⚠⚠ Measurement-discipline failure.** A leftover capture script `/root/v28_wedgecap.sh` (started by
  a prior session, never stopped) fired on the wedge commit, `rmmod`'d `qcom_bam_dmux`, and forced a
  crash → the driver never re-initialized → no `wwan0`, MM blind, data path dead ~2000 s. New RULE:
  stop prior-session capture scripts before a soak and verify the soak is *arming*, not merely running.
- **⚠ New, untriaged:** `modprobe qcom_bam_dmux` **hung** (>3 min, no dmesg, no module) after the
  forced crash — a possible reload-after-force-crash defect.
- **Recovery:** soak ended; **mitigation restored** (`preemptive_ssr=1` + `a2_pin=1`, interval 800) and
  the device rebooted clean (`wwan0..7` up, MM `Modem/0`).



- **Ledger §112.68** — tasks #272/#273 ("enumerate boot-armed timers to name the ~900 s timer").
  The modem's timer API is the QuRT timer library (create `FUN_c0914b30`; arm-with-duration
  `FUN_c0914e10`; `FUN_c0914dc0` scales by `0x24a`=586 ≈ 19.2 MHz/32768 Hz).
- **★ New instrument — the timer object pool.** `FUN_c0915020` resolves a handle to
  `&DAT_c2cd4de0 + idx*0x90`, 256 entries, in **BSS ⇒ readable from any coredump**. Field map
  (+0x1c callback, +0x30 expiry, +0x40 arm-time, +0x50 duration, +0x84 magic `0xcacacac`, +0x88
  owner). **Units proven exactly** (`expiry − arm = dur_ms × 19200` for all 57 entries): duration in
  ms, tick clock 19.2 MHz. Tools `scratch/timer_pool.py`, `timer_analyze.py`, `timer_obj.py`.
- **★ `max(+0x40)` = the modem uptime** (verified vs dump filenames 917.4/1536.0/13986.1/13518.5/302.4 s)
  ⇒ the pool gives a reliable uptime + a full live-timer census from any coredump.
- **★ The ML1 timers are directly visible** (callback `c02d7bd0`): 50/100/330/430/530/630/1000/5000 ms,
  re-armed continuously; at the fatal the expiring ones are the 50 ms (state-20 watchdog) + 100 ms.
- **★★★★★ NEGATIVE: the ~900 s trigger is NOT a QuRT software timer.** A 900,000 ms (900 s) timer
  *does* exist (callback `d051e254`, dynamically allocated, ~19/31 dumps) but it is armed at ~30–41 s
  (≈ attach) and in every boot-anchored dump its **remaining is +16…+27 s ⇒ it fires ~20 s AFTER the
  fatal**. No pool timer has an expiry at ~900 s. ⇒ redirect to a non-QuRT source (hardware / RPM /
  MCPM timer) or a counter/resource.
- The `900000` constant is an **IMS `PDPRATHandlerVoLTE.cpp`** config default (3 sites, with 30 s/60 s/
  600 s/5 s neighbours); the 900 s timer's callback `d051e254` is outside the ELF segments.
- ⚠ Tick clock: reset on cold boot, **persists across an SSR** (`mcpm_run/fatal_901` `max(now)`=2526 s
  at ATS uptime ≈900 s) ⇒ "900 s" is an ATS-uptime figure.

### 2026-10-04 — P-DET RESULT: the ~900 s manifestation is a RACE (fixed config → FATAL, FATAL, WEDGE)

- **Ledger §112.67 / Doc 245 §4** — the pre-registered determinism test (P-DET) ran autonomously:
  fixed config (stock baseband, `preemptive_ssr=0`, `a2_pin=1`, continuous traffic); the event fires at
  modem-up ≈900 s on every boot and the modem auto-recovers (fatal → crash-recovery; wedge →
  stall-watchdog Stage-3 SSR).
- **★★★★★ H0 FALSIFIED (n = 3).** Event A (=run 8) **FATAL** `lte_ml1_sm_conn_inter_freq_stm.c:712`
  @ mu 900.385 s; event B **FATAL** `lte_ml1_sleepmgr_stm.c:4054` @ mu 900.838 s; event C **WEDGE**
  @ mu ≈903.7 s. The **timing is deterministic** (spread ≈3.3 s) but the **manifestation is a race**.
  ⇒ "if it's a quiet failure it should always be a quiet failure" is **NO**.
- **★★★★ Variable victim** — events A and B (identical config, back-to-back) assert in two different
  ML1 SMs. One upstream trigger, two independent random outcomes (which SM notices first; assert vs
  silent data death).
- **★★★★ Wedge on STOCK firmware** (event C) ⇒ the wedge is **not** an artefact of the v13/v28 ring
  instruments. Telemetry: RF still reports a cell (RSRP −90 dBm, SNR 10.6 dB) while `RX+0` =
  "connected but the data path is dead" (§112.71).
- Instruments: device `/root/pdet.sh` + `/root/v13_traffic.sh`; host `scratch/pdet_collect.sh` →
  `scratch/pdet_collect.log`. Device left in the observation config (`preemptive_ssr=0`) — restore `=1`
  for the mitigation.

### 2026-10-04 — RUN 8: a FIFTH ~900 s fatal signature (`lte_ml1_sm_conn_inter_freq_stm.c:712`, ML1 MGR) with a HEALTHY sleepmgr

- **Ledger §112.66 / Doc 245 §3** — stock baseband `1a6f9507…`, `preemptive_ssr_enabled=0`, `a2_pin=1`,
  continuous traffic, cold AP boot → **FATAL** at AP uptime 2524.496 s (modem-up **≈901.3 s**).
- **New signature.** Crash report `file=lte_ml1_sm_conn_inter_freq_stm.c line=712 task=ML1 MGR`; the
  descriptor resolved offline from the coredump's assert-DB (`scratch/a2_descr.py`): `0xc3c80640` →
  message **`Assert 0 failed:`** (unconditional `ASSERT(0)`). This is the **5th distinct ~900 s fatal
  site** (after `lte_ml1_sleepmgr_stm.c:4054`, `a2_power.c:1189`, `lte_ml1_common_timer.c:390`,
  `lte_ml1_sm_idle_stm.c:2913`).
- **★★★★★ The sleepmgr was HEALTHY** (`0xc1e158d0` `+0x04 = 1` ONLINE, `+0x08 = 0x06`). ⇒ **the ~900 s
  event can fatal WITHOUT the sleepmgr RF-wakeup stall** — the sleepmgr state is a *victim's* signature,
  not the root. This reframes the model: the trigger is upstream of the ML1 SMs.
- **The crashing SM consumes RF completions.** Its assert cluster includes line **657**
  `((rfa_rf_lte_l2l_build_scripts_cnf_s *)payload)->req_result == RFA_RF_LTE_SUCCESS` (an RF-driver
  completion check) — the same "waits on the RF layer" shape as the sleepmgr's `RF_WAKEUP_CNF`. The
  *fired* assert was line 712 (`ASSERT(0)`), a default branch; the exact reaching event is unresolved
  (the function was not decompiled; disasm has decode gaps).
- **No F3 precursor** (matches §112.13): 227 normal records in the final 1.0 s (IRAT GRFC script every
  40 ms to −23 ms, MCPM cycle running, A2 power req); the last record is `rf_task.c:336 " get imei
  stoped "`. Differs from the wedge's §112.57.11 staged collapse (a fatal crashes at the event).
- **Track record updated** (Doc 245 §3): run 8 → counts now **WEDGE 4 / FATAL 4**; added the **victim
  SM** as a second uncontrolled variable alongside the MCPM phase.
- Artifacts: `scratch/mcpm_run/dump_fatal_901.bin` (md5 `f955dd1344937a9b6b497ec541df21c8`),
  `scratch/mcpm_run/f3win/`, readers `scratch/crashlog_extract.py` / `sleepmgr_dump.py` /
  `a2_descr.py` / `mcpm_run/f3tail.py`.

### 2026-10-04 — v28 RF-WAKEUP-CNF CALLBACK RING: instrument the RF wakeup path's RF-driver side

- **Ledger §112.63 / Doc 244 §6.8** — v26 (egress) and v27 (ingress) see only the sleepmgr's
  *messages*; neither sees the RF driver that produces `RF_WAKEUP_CNF`. v28 hooks the ONE function
  that emits every `RF_*_CNF`, `0xc039e580`, at its **function ENTRY** — the only clean packet
  boundary where both `r2` (the CNF selector) and the true caller `r31` are live.
- **Code map:** `0xc039e580` is entered only via four tail-call stubs that set the selector —
  `0xc039e570` r2=0 → `RF_WAKEUP_CNF 0x42b0801`; `0xc03a0b70` r2=1 → `RF_EXIT_CNF 0x42b0804`;
  `0xc03a0b40` r2=2 → `RF_SLEEP_CNF 0x42b0802`; `0xc039e700` r2=3 → `RF_ENTER_CNF 0x42b0803`
  (switch verified at `0xc039e688`). The stubs are **completion callbacks** registered in
  `0x4290203` RF-request payloads (`0xc039b95c` stores `0xc039e700`; RF dispatcher `0xc0313f1c`),
  so at `0xc039e580` entry `r31` = the **RF driver** return address.
- **v1 crash (mid-packet continuation).** v1 hooked the 4-byte `{ call 0xc02d1140 }` at
  `0xc039e584` and continued at `0xc039e588` — which is **MID-PACKET**: a stream decode shows
  `0xc039e584` is a **FUSED 16-byte packet** `{ call; r17=r2; r18=r0; memd(r29+#0x10)=r19:18 }`
  (spans `0xc039e584..0xc039e594`). The re-executed `r18 = r0` (r0=0) fired
  `Assert cnf_msg != NULL failed` at `lte_ml1_sleepmgr_stm.c:4344` (task ML1 MGR, uptime 16 s).
  v2 re-targets the ENTRY and continues at `0xc039e584`.
- **★★★★★ v2 crash → THE PARSE-BITS BUG (root-caused + fixed).** `site_packet4` built the
  `{ jump 0xc003054c }` word with base `0x58004000` → packet parse bits `[15:14]=0b01`, but a
  **standalone** 4-byte instruction needs **parse=0b11**. With parse=01 the CPU decoded the NEXT
  packet as extra instructions of this packet → **illegal-instruction `:Excep` at the site PC
  `0xc039e580`** (all GPRs 0, `BADVA=0xe2eea008`, task AMSS0, modem crash-loop ≈20 s uptime).
  Fix = base `0x5800C000`. Verified against `llvm-mc` label assembly: site→cave = **`e6cf9259`**
  (correct) vs buggy `e64f9259` (only bit 15 differs). ★ v27's stock site `0xc039d46c` was
  parse=01, so v27's parse=01 coincidentally matched (why v27 ran). STOCK firmware ran 360 s clean
  (`scratch/stock_watch.sh`) = negative control.
- **Cave (`0xc003054c`, 120 B)** reproduces the entry prologue duplex
  `{ memd(r29+#-0x10)=r17:16; allocframe(#0x20) }` byte-for-byte, then logs
  `{seq, sel=r2, caller=r31, state}` to ring `0xc1d4c600` (header/beacon `0xc1455000`, marker
  `0x76323801`), then jumps to `0xc039e584`. Cave uses only `r6`–`r11`; `r2`/`r19`/`r29` preserved.
- **Built from pristine stock** (`scratch/diag_patch_v28/build_diag_patch_v28.py`), site + cave
  byte-verified by re-disassembly, b01 seg16/seg5 hashes rebuilt, hash re-verify PASS.
  **`modem.mdt md5 = 0f57e8836314ac863f7053316979b220`** (parse-fixed).
- **Deployed** to `/lib/firmware` with sha256 read-back PASS on all 5 files
  (`scratch/deploy_v28_ring.py`); `preemptive_ssr_enabled=0`, `ssr_enabled=1`, `a2_pin=1`, dumpwatch
  running.
- **★ RESULT (ledger §112.64).** RUN 1 (cold modem) **WEDGED** — data-path death, NO fatal, no
  coredump; the stall-watchdog's Stage-3 SSR at AP 1435 s **wiped the ring**. RUN 2 (the
  crash-recovery warm restart) **FATALED** at **modem-uptime 902.77 s** (`lte_ml1_common_timer.c:390`,
  task `tmr_slave3`, PC `0xc087a804`); ring captured (`scratch/v28_run/dump_fatal_905.bin`,
  md5 `dc64876e…`): **seq 2541, strictly alternating RF_WAKEUP_CNF ×1270 / RF_SLEEP_CNF ×1271, no
  non-alternating runs** — the **last emission is a RF_SLEEP_CNF**, i.e. the final **RF wakeup
  confirmation is absent**. Sleepmgr object `0xc1e158d0` `+0x04 = 9` = **OFFLINE_WAKEUP** at the
  fatal. **NEW:** the caller resolves the RF-driver completion paths — **ONLINE** wakeup `0xc0314d48`,
  **OFFLINE** wakeup `0xc03155fc`, sleep always `0xc0315440`; the missing confirmation is an OFFLINE
  wakeup. ⇒ the stall is on the **RF-driver completion side**, not the sleepmgr; **root cause still
  OPEN** (third independent confirmation of the RF-wakeup stall). ★ New device-side instrument
  `scratch/v28_wedgecap.sh` forces an on-demand coredump at the stall-watchdog's commit point, so a
  WEDGE (no coredump) can be captured before the Stage-3 SSR wipes the ring.
- **★ RUN 3 — REPRODUCED.** The next warm cycle fataled again (`lte_ml1_common_timer.c:390` at
  modem-uptime **901.98 s**); ring **seq 2441**, identical structure (WAKEUP ×1220 / SLEEP ×1221,
  last = SLEEP, sleepmgr `+0x04 = 9` OFFLINE_WAKEUP, non-alternating runs = 0) — dump
  `scratch/v28_run/dump_fatal_903.bin` (md5 `740c0e33…`). ⇒ reproducible across consecutive warm
  restarts (n = 2). ⚠ Open hypothesis (n = 1): the only wedge (RUN 1) was the cold-boot event; both
  warm-restart events were FATALs.
- **★★★★★ WEDGE CAPTURED (ledger §112.65).** With v28 re-deployed and `preemptive_ssr_enabled=0`, a
  **warm** modem restart (`echo restart > /sys/kernel/debug/msm_subsys/modem`) wedged: data death at
  modem-uptime **≈898–928 s**, no fatal, no SSR restart. `v28_wedgecap.sh` forced the on-demand dump at
  the watchdog commit (AP 8355 s) → `scratch/wedge_run/dump_wedge_8357.bin` (85,398,475 B,
  md5 `db8b304a…`). **Sleepmgr `0xc1e158d0` `+0x04 = 4` = ONLINE_WAKEUP** ⇒ **confirms §112.58:
  FATAL ⇒ state 9, WEDGE ⇒ state 4** (n = 2 for the wedge). Ring **seq 3383** (WAKEUP ×1691 /
  SLEEP ×1692, **last = RF_SLEEP_CNF**) — the **same** missing-final-wakeup signature as the fatal ⇒
  the RF-CNF tail alone does **not** discriminate. **NEW discriminator — the REGIME:** the fatal's last
  76 emissions are all OFFLINE-regime (state 9); the wedge's last 10 are ONLINE-regime (state 4), having
  transitioned **OFFLINE→ONLINE ~3 s before capture**. ⚠ Caveat: the wedge is captured ~145–160 s after
  the data death (at the commit), so the state may be a phase; n = 2. ⚠ **§112.64's cold/warm
  hypothesis is FALSIFIED** (this wedge was a warm restart — the manifestation is a race). ⚠ LIMITATION:
  the F3 stream was silent for the whole v28 modem life (AP 7301→8359), so no F3 for this specimen.
- **Restored to steady state:** `preemptive_ssr_enabled=1` (interval 800, `a2_pin=1`), firmware rolled
  back to stock `1a6f9507…`, diagnostic watcher stopped.
- Reader `scratch/read_v28_ring.py`; monitors `scratch/v28b_wait.sh` / `scratch/stock_watch.sh`.
  Reversible: `scratch/deploy_v28_ring.py --rollback` (stock `1a6f9507…`).
- **Memory:** `project_v28_rf_cnf_ring.md`; new trap `feedback_patch_parse_bits.md` (a general
  Hexagon site-packet lesson — always verify parse bits with `llvm-mc`).

### 2026-10-04 — v27 SLEEPMGR INGRESS RING: confirms the missing RF wakeup confirmation (ingress side)

- **Ledger §112.62 / Doc 244 §6.6–6.7** — v26 logged the sleepmgr **egress**; the OFFLINE *trigger*
  is an **ingress** event. v27 hooks the ML1-dispatch entry `0xc039d460`'s call packet `0xc039d46c`
  (`{ call 0xc02ef910 }` → `{ jump 0xc003054c }`), the only point where both `r16 = r1` (msg id) and
  the true caller `r31` are live. 128 B cave in `modem.b05` @`0xc003054c` logs
  `{seq, msg_id, caller, state}` to ring `0xc1d4c600` (header/beacon `0xc1455000`, marker `0x76323701`).
- **Built from pristine stock** (`scratch/diag_patch_v27/build_diag_patch_v27.py`), site + cave
  byte-verified by disassembly (site jump target `0xc003054c`; cave constants all correct),
  b01 seg16/seg5 hashes rebuilt, hash re-verify PASS. **`modem.mdt md5 = 030f528376f38cce5a1e9fb0803075de`.**
- **Deployed** to `/lib/firmware` with sha256 read-back PASS on all 5 files
  (`scratch/deploy_v27_ring.py`); `preemptive_ssr_enabled=0`, `ssr_enabled=1`, `a2_pin=1`, coredump
  enabled, dumpwatch running; AP rebooted 2026-10-04T09:08Z, v27 modem booted clean.
- **RESULT (H1/H2/H3 PASS):** fatal `lte_ml1_common_timer.c:390` at AP dmesg `914.458760 s`; dump
  `scratch/v27_run/dump_fatal_916.bin` (md5 `4df0b22e…`); `seq=22564`, window seq 18469..22564
  (~163 s). **The hook is sleepmgr-specific** (all 23 msg-ids are sleepmgr messages ⇒ `r1` = msg id,
  resolving the §4.4a ambiguity). **★ The anomaly matches v26 from the ingress side:** `STMR_ON_REQ`
  occurs 266× and is preceded by `RF_WAKEUP_CNF` **265×** — the **sole exception is the LAST one**
  (seq 22561, state 9 `OFFLINE_WAKEUP`): the sleepmgr **armed its sleep timer without the RF-wakeup
  confirmation**, then the ML1 timer slave asserted. RF wakeup was healthy for the previous ~163 s.
  Strengthens the RF-death model; **root cause still OPEN**. Reproduce: `scratch/analyze_v27_cycles.py`.
- Reversible: `scratch/deploy_v27_ring.py --rollback` (stock `1a6f9507…`).

### 2026-10-04 — v26 funnel ring CAUGHT THE FATAL: the last sleepmgr cycle omits `RF_WAKEUP_CNF`

- **Ledger §112.61 / Doc 244 §6.5** — the v26 image (`modem.mdt f57ed422…`) hooking the sleepmgr
  ML1-message **send funnel `0xc03927d0`** (body packet `0xc03927d4` → ring
  `{seq,msg_id,caller,len|state}`) ran with `preemptive_ssr_enabled=0`; fatal at AP dmesg
  `915.260490 s` / crash-report uptime `0:15:02`, `lte_ml1_common_timer.c:390`, **Task `tmr_slave3`**,
  PC `0xc087a804`, BADVA `0`. Dump → `scratch/v26_run/dump_fatal_916.bin`.
- **P-V26-FUNNEL scored: H1/H2/H3 PASS, H4 PASS (n=1).** `seq=40111`, 4096 live entries; the egress
  is a regular ~20–21-message sleep/wake cycle (~192 repeats, **≈44 msg/s**). **The last cycle alone
  is anomalous:** `STMR_ON_REQ (0x42b0206)` is preceded by `RF_WAKEUP_CNF (0x42b0801)` **191/192
  times** — the sole exception is the **last** one (`seq 40110`), preceded by `42b0405`. Last four:
  `40a020f → 42b0405 → STMR_ON_REQ → 42b0406 → FATAL` ⇒ the sleepmgr **armed its sleep timer without
  the RF-wakeup confirmation**, then the **timer slave** fataled.
- **The ring is EGRESS** ⇒ it cannot see the *incoming* message that drives the sleepmgr into
  OFFLINE_WAKEUP (state 9, still frozen at the fatal) — the next instrument must hook the sleepmgr
  **ingress/dispatch** (Doc 244 §5, §112.61.7).
- Reusable: the recorded `caller` is `r31` = the **next packet boundary** (a fused `call` returns
  `call+12`, not `call+4`); the ring window is `[max(1,seq-N+1) .. seq]` (seq is pre-incremented).
  The earlier `0xc5000000` boot-crash (zero-filled coredump region ≠ writable runtime region) is
  recorded as §112.60.3. Reversible: `scratch/deploy_v26_ring.py --rollback` (stock `1a6f9507…`).
  Ledger + Doc 244 + memory updated in the same session.

### 2026-10-04 — v13 run-2 CAUGHT THE FATAL: the sleepmgr is FROZEN in `ONLINE_SLEEP_WAIT` (state 2)

- **Ledger §112.57.14** — the §112.57.13 next-step experiment (same v13 ring firmware
  `daa903be…`, `preemptive_ssr_enabled=0`, continuous traffic, cold boot) produced a **FATAL** this
  time: `fatal error received: lte_ml1_sleepmgr_stm.c:4054` at **AP-up 913.92 s** (modem uptime ≈900 s),
  recovery at 915.63 s (**~1.7 s** — the modem's own fast SSR). Auto-coredump pulled to
  `scratch/v13_run2/dump_run2_fatal.bin` (md5 `7cae3307…`); crash report Task=`slpc`, Uptime `0:15:00`.
- **The ring's last transition is INTO state 2** (`seq 13297: 1 -> 2`, ONLINE -> ONLINE_SLEEP_WAIT) and
  **there is no `2 -> 3`**; live `*(0xc1e158d4)=2`. ⇒ the sleep entry never completed — exactly what
  `ASSERT(stm_get_state(SLEEPMGR) == SLEEP)` rejects. Run-1's wedge had last `3 -> 4`, live state 4.
- **★★★★★ UNIFIED MODEL** — the ~900 s event is the **MCPM sleep/wake power-collapse cycle stopping**;
  the sleepmgr freezes at its current phase. Phase = **sleep entry (state 2)** ⇒ the sleep-entry
  deadline assert fires ⇒ **FATAL**; phase = **wakeup (state 4)** ⇒ **WEDGE**. This refines §112.57.13:
  the sleepmgr stall is the *phase that trips the assert*, not a separate event.
- **★★ Correction to §112.20/§112.57.12** — the `a2_power.c:1189` assert is a **SPIN-COUNT** watchdog,
  not a time watchdog: every a2-fatal dump has `0xc28602f8=45051, 0xc28602fc=901` at **variable** uptimes
  (18/17/27/28/288/576 s); every ~900 s ML1-class fatal/wedge has both counters **0**. The `901` constant
  was recovered from the missing second word of the `c0504314` packet (from the stock ELF).
- Reversible (`scratch/deploy_v13_ring.py --rollback`, stock `1a6f9507…`). Ledger + memory updated in
  the same session.

### 2026-10-04 — v13 STM-state-writer ring WORKS; the run WEDGED with a HEALTHY sleepmgr (the deadlock is a VARIANT)

- **Ledger §112.57.13** — the v13 ring firmware (`modem.mdt daa903be…`) deployed to `/lib/firmware`
  with `preemptive_ssr_enabled=0`; cold boot clean. Soak clean to AP-up 907.95 s, then at **AP-up
  925.10 s** `ping=0` **with `fatals=0 ssr=0`** — a **WEDGE** (modem `running`, `mmcli`
  `connected/lte/attached`). No fatal ⇒ no auto-coredump ⇒ captured **on demand** (§6.2a) →
  `scratch/v13_run/dump_v13_wedge.bin` (md5 `6d731dcc…`).
- **Ring** (`scratch/read_v13_ring.py`): `marker=0xc1455000` ✓, **`seq=12922`**; the 128-entry ring is
  a **continuous normal cycle** (`1->2->3->4->5->1`, `1->6->7->8->1`) to the **last entry `3->4`**;
  live `*(0xc1e158d4)=4` (`ONLINE_WAKEUP`). ⇒ the sleepmgr **never stalled** this run (≈13.5
  transitions/s, cycle ≈0.37 s). **P-V13-STM: H1 FALSIFIED** (state 11 first entered seq 683 ≈50 s,
  not boot), **H2 UNSCORED** (ring wrapped), **H3 FALSIFIED** (cycle ≈0.37 s, not 900 s).
- **F3** (`scratch/v13_run/f3/`): byte rate 3→8–9 MB/5 s at up 913–967; the **same staged shutdown**
  as §112.57.11 — power layer (`a2_power`/`mcpm_npa`/`mcpm_saw`/`pgi_msgr`) collapses at up ~913, then
  CM/NAS/QMI, leaving only `cfm_cpu_monitor`.
- **★★★★★ REFRAMING** — the sleepmgr deadlock is **one manifestation, not the common event**. The
  common feature of the ~900 s event (fatal AND wedge) is the **staged shutdown of the MCPM/power
  layer**; the sleepmgr `ONLINE_SLEEP_WAIT` stall is a **variant** that adds the `sleepmgr_stm.c:4054`
  assert (and thus the modem's fast ~12 s self-recovery).
- Reversible (`scratch/deploy_v13_ring.py --rollback`, stock `1a6f9507…`). Ledger + memory updated in
  the same session.

### 2026-10-04 — v14 FAILED (NEG): a 4th ~900 s signature; full-corpus site set; v15 = suppress all four

- **Ledger §112.57.10** — v14 (`ecdaf2c9…`) with the pre-emptive SSR off was clean through AP-up
  892.96 s, then at **AP-up 924.21 s** the modem fatally crashed and **SSR'd** (`916.816818 fatal
  error received: lte_ml1_sm_idle_stm.c:2913` → `MBA booted … loading mpss` → up at 936 s). A **4th
  signature not covered by v14**; **P-V14-SUPPRESS = NEG**.
- **The 4th signature (D)** — `lte_ml1_sm_idle_stm.c:2913`, task `ML1 MGR`, call-site packet
  `0xc03556a8` (descriptor `0xc3c793f0` → `a2_descr.py`); sibling `0xc03556c0` → `…:2923`; clean
  continuation `0xc0030098`.
- **Full-corpus tally (63 dumps, 43 classified) — exactly 4 ~900 s signatures:** A
  `lte_ml1_sleepmgr_stm.c:4054` (18), B `a2_power.c:1189` (11), C `lte_ml1_common_timer.c:390` (9),
  D `lte_ml1_sm_idle_stm.c:2913` (1). Off-900 (out of scope): `a2_power.c:2949`, `a2_taskq.c:759`.
- **Mechanism re-read** (`scratch/scan_assert_sites.py`) — `FUN_c0879150` is the generic assert/error
  **logger** (**17 708** call sites), not the halt; `0xc087a76c` is the crash-context **saver**
  (`r1 = pc` at `0xc087a804` ⇒ the reported PC is a fixed red herring); the real halt is `stop(r0)` at
  `0xc087a818`. The scanner maps each assert packet's descriptor → file:line (verified: the 8 ML1
  sites resolve to 8 distinct lines, only 390 fires).
- **v15** (`scratch/diag_patch_v15/`) — v14's 10 sites + the D pair = **12 sites**; deployed
  **2026-10-04** with SSR off; `modem.mdt 1eae18690c2ad8fe8769938be09c6cde`, `modem.b16
  717198341650743d128abb83332e5392`. All 13 jumps independently decoded. Reversible
  (`deploy_v15.py --rollback`).
- **P-V15-ALLFOUR** pre-registered: H1 = no fatal past modem-up 900 s (soak to AP-up ≥1500 s);
  H2 = data alive past 900 s (not a zombie); NEG = a 5th signature ⇒ per-site suppression abandoned,
  root cause mandatory. **Scored 2026-10-04 06:16: NEG (H1 PASS, H2 FAIL)** — see the §112.57.11
  entry below.

### 2026-10-04 — v15 FAILED (NEG): assert-suppression converts the FATAL into the WEDGE; the fatal IS the modem's fast recovery trigger

- **Ledger §112.57.11** — v15 (`1eae1869…`) with the pre-emptive SSR off was clean through AP-up
  **1383.6 s** (`fatals=0`) — **H1 PASSES** — but the data path was **dead**: direct `ping -I wwan0`
  = **100 % loss**, `wwan0` RX **frozen at 21678 B** while TX grew, `mmcli` still
  `connected/lte/83 %/attached`. **H2 FAILS ⇒ P-V15-ALLFOUR = NEG.**
- **Recovery was the userspace `modem-stall-watchdog`** (`modem-bearer-watchdog`, `stall_timeout=60`):
  Stage 1 `wds-go-dormant` → Stage 2 bearer rebuild → both **insufficient**; **Stage 3 SSR at up
  1414 s** restored RX. Telemetry at confirmation: `RSRP=-86 dBm, SNR=16.0 dB, Cell ID=4399665,
  WDS dormancy='traffic-channel-active', TX=317, RX=246` — a **healthy control plane with a frozen
  user-plane RX**. ⇒ **~504 s of dead data** (up 910→1414) vs the fatal's **~12 s** recovery.
- **F3 ground truth** (`scratch/f3_v15/`, chunks up 898–923) — a **STAGED SHUTDOWN**:
  `a2_power/mcpm_npa/mcpm_saw/pgi_msgr` die first (~up 910), then `cmlog/qmi_nas/cmss` (~915),
  leaving only the `cfm_cpu_monitor` CPU heartbeat. The modem CPU is **alive**; only the LTE stack
  is dead. Byte burst 2.5–3.0 MB/5 s vs a normal 0.39 MB/5 s at up 898–908 = the collapse flush.
- **Reframing:** the ~900 s **fatal assert IS the modem's own fast recovery trigger**
  (deadlock → RF receiver frozen → assert → `stop(r0)` → SSR → ~12 s). Suppressing it removes the
  trigger and leaves only the slow userspace fallback. **`fatal → wedge` is strictly
  counterproductive (~12 s vs ~504 s). Assert-suppression is a dead axis; the root cause (the
  boot-anchored ~900 s `LTE_ML1_SLEEPMGR_STM` sleep-entry deadlock) is now mandatory.**
- **⚠ Measurement bug found + fixed** — `grep -c "0% packet loss"` also matches `"100% packet
  loss"`, so the soak/fine `ping=` field was bogus. Fixed to use **ping's exit code**
  (`scratch/v15_soak.sh`, `scratch/v15_fine.sh`); historical `ping=` values are not interpretable.
- **Action taken** — v15 **rolled back to stock** (`1a6f9507…`, verified) and the **shipped
  mitigation re-enabled** (`preemptive_ssr_enabled=1`, interval 800 s, `a2_pin=1`); device healthy.
- **Ledger §112.57.12** — decoded `FUN_c05042c8`: a 50 Hz tick that increments `*(0xc28602f8)` and,
  once per 50 ticks, `*(0xc28602fc)++`; otherwise asserts when `*(0xc28602fc) > 900`. Every reference
  to the counter base `0xc28602c0` checked — the **1 Hz counter `0xc28602fc` is written in exactly
  ONE place (the increment) ⇒ never reset** ⇒ the a2 assert is a **deterministic boot-anchored 900 s
  timer**, not a symptom. The ~900 s fatal is a family of boot-anchored timers; suppressing them is
  dead (v14/v15 NEG); the pre-emptive SSR remains the only proven lever; a true fix needs the
  upstream modem-internal ML1/sleepmgr deadlock cause (firmware RE).

### 2026-10-04 — the ~900 s fatal is a family of THREE asserts; v14 suppression fix deployed

- **Ledger §112.57.9** — `crashlog_extract.py` over the archived ~900 s coredumps shows the fatal is
  **three** asserts, each in a different task, all at modem-uptime 0:15:00–0:15:10:
  (A) `lte_ml1_sleepmgr_stm.c:4054` task `slpc` site `FUN_c039ef80` `0xc039f7b8`;
  (B) `a2_power.c:1189` task `a2` site `FUN_c05042c8` `0xc0504320` — a **1 Hz counter `0xc28602fc`
  = 901** (>900 s, never reset) in the fatal dump;
  (C) `lte_ml1_common_timer.c:390` task `tmr_slave3` site `FUN_c02d7bd0` (8 sites).
  Every site is the same `{ call 0xc0879150 ; immext ; r0 = <desc> }` 12-byte packet; `FUN_c0879150`
  never returns, and each caller has a clean continuation elsewhere.
- **v14 fix** (`scratch/diag_patch_v14/`) — overwrite each assert packet with `{ jump <clean> ; nop ;
  nop }` (10 sites, all in `modem.b16`; b01 seg16 re-hashed, mdt rebuilt). Deployed **2026-10-04
  05:24** with the **pre-emptive SSR disabled**; cold boot clean. `modem.mdt
  ecdaf2c97d72da150d367c68d8b7cff8`, `modem.b16 ef1b02e8f5baa885a17a40ef43c92566`. Reversible
  (`deploy_v14_suppress.py --rollback`, stock `1a6f9507…`).
- **P-V14-SUPPRESS** pre-registered: H1 = no fatal past modem-up 900 s (SSR off, soak to AP-up
  ≥1500 s); H2 = data path alive past 900 s (not a zombie); NEG = a signature still fires. **Scored
  2026-10-04 05:38: NEG** — a 4th signature (`lte_ml1_sm_idle_stm.c:2913`) fired at modem-up
  ~916.8 s; see the §112.57.10 entry above.

### 2026-10-04 — v13 instrument debugged (two builder bugs) + early-crash hypothesis falsified

- **Ledger §112.57.7** — v13 deployed; it crash-looped at boot; **two builder bugs fixed**:
  (1) the Hexagon `J2_jump` immediate is **23-bit signed** (sign bit → `word[24]`), not 22-bit —
  the site mis-signed `-0x1F3B44` and jumped to `0xc15edc08`; (2) every `{ r6 = ##X; jumpr r6 }`
  trampoline is a **same-packet hazard** (`jumpr` reads the pre-packet r6) — split into two
  packets. Instrument now cold-boots clean (0 crashes). Image md5 in §112.57.7.
- **Ledger §112.57.8** — "an early-boot SSR/crash arms the ~900 s timer" **FALSIFIED**: a clean
  OpenWrt cold boot (`bootB`) had zero early crashes yet fataled at modem-uptime 900.7 s; Android
  cold boot clean through 2723 s. The arming is powerup-anchored (§112.8), not crash-driven.

### Pending / next

- **Sequenced ~900 s fatal investigation (approved plan)** — user chose order
  1 → 2 → 3:
  1. **RF / interference lead** — DONE (§112.40): the RF/meas layer is the first
     LTE sub-layer to stop, but state-dependent and the F3 does not say why.
  2. **ML1 SERV-MEAS-RSP reply path** — DONE (§112.41): the reply is *downstream*
     of the ML1-wide stall — the producer (`FUN_c01bc8e0`) never runs
     (`slot[+0x01]=0` in 7/7 dumps); no new root.
  3. **New live instrument for the ML1-side counter** — DONE (§112.42): a high-rate
     RAM sampler is **impossible** (TrustZone blocks AP reads); the audit instead
     showed the meas-table flag is confined to (`d2=0`, `e02=1`) — a **new
     confounder `e02`** leaves its fatal-specificity **UNRESOLVED**. §112.41's
     phase-2 conclusion is **RETRACTED** and §105.8's "confound resolved" is
     **UNSUPPORTED**. The decisive control (`P-IDLECTRL2`) was **run but VOID**
     (the idle modem survived to 966 s but read `e02=0`).
  3b. **Positive control for the §105.4 flag (P-FLAGPOS)** — Stage 1 **DONE
     (§112.44)**: the flag is the deterministic output of the event-0x10 handler
     (which clears `d2` then arms), so `(d2=0, armed)` is forced by construction
     and the probabilistic Stage 2 is **superseded** (a snapshot cannot measure
     persistence). The deterministic firmware-ring follow-up was **approved,
     built and run** — **P-MEASRING, DONE (§112.45)**: the technique is proven
     (boot-transparent, captured the real fatal), but the literal "ARM stopped
     first" answer is **confounded** (the event-0x10 stream is early-concentrated
     and stops 814 s before the fatal). **⚠ §112.45.5 CORRECTION:** the proposed
     **v2** (hook `FUN_c02fda90` + `FUN_c02d7bd0`) is **REDUNDANT** — that is
     exactly the **v7–v11** instrument (items 79–86), and item 97 closed the
     armer's upstream statically. The only new piece (effective write/drain on
     the LL1 path) is largely pre-answered by the slot state. ⇒ **no v2 firmware
     patch is obviously warranted**; the next move is a decision, not a build.
  3c. **The state-20 ARM-vs-CANCEL lifecycle ring (P-CANCELRATE)** — **DONE (§112.46),
     VOID/CONFOUNDED**: the run **wedged** (no fatal in 34 min; the fatal grep was
     empty from AP 159→2175), and the "CANCEL" hook site `0xc034e1f0` is a
     **shared dispatch merge point** (15+ selectors) so it counts **every ML1
     dispatch**, not the ctx0 cancel. The ARM (`FUN_c02fda90`, exactly 2 call
     sites) fired only **32×**, **corroborating §63.5's dormant finding** and
     **refuting the pre-registration's "0.63/s armer" premise** (that rate is the
     dispatcher's). **⚠ RETRACTED by §112.47.4b** — the v7 instrument (the SAME two
     sites) measured 674 calls / 902.29 s idle = 0.747 Hz, i.e. the pre-registration
     was plausibly RIGHT and this 32 is the outlier; the ARM rate is OPEN.
  3d. **Offline closure of 3c's "open identification"** — **DONE (§112.47), NEGATIVE**:
     the ctx0 arm is **genuinely only `FUN_c02fda90`** (its 2 sites are the only
     two that pass selector `0x80` to the sole pending-bit setter `FUN_c02fba64`;
     the other 13 sites arm the *other* eight contexts). There is **no hidden
     high-rate armer** ⇒ a v3 ring is **REDUNDANT**. Also corrected: `obj[+0x38]`
     is a **fixed context ID (20..28)**, not a transient state. The ARM rate is
     **contested (4 / 32 / 225 / 674) and OPEN**; the static closure is
     rate-independent. A thermal-signature search in the F3 was a **NULL**.
- **Cleanup patch (carried over, DEFERRED)** — fold the `hang_probe_t4` /
  `f3cap` / `coredump-enable` blocks out of `/etc/rc.local`. Deferred by user
  decision: it fixes no crashes and removes the on-device safety net for a
  possible patch-831 regression.
- **`a2_pin` default decision (carried over, open)** — whether the shipped image
  should default `a2_pin=1` (currently opt-in via uCI set; shipped config sets
  it). A design/risk question: `a2_pin=1` suppresses the cold-boot
  `a2_power.c:1189` fatal but is a **mitigation, not a root-cause fix**
  (§112.24/§112.28); it holds the modem's A2 power-control pin across pre-emptive
  SSRs.

### Changed — modem stability ledger

- **§112.57.6 (v13 — the corrected STM-state-writer ring, BUILT + offline-verified)** — with the
  §112.57.5 correction restoring the premise, the v12 ring is rebuilt as **v13** with the hook on
  the **whole packet at `0xc0fe174c`** (12 bytes → `{jump tramp; nop; nop}`, parse bits 01/01/11,
  hand-built because the target is out of assembler range), trampoline `0xc0dedc08` → cave
  `0xc003054c` (136 B) that filters `r16 == 0xc1e158d0`, logs, then **replays the packet exactly**
  and branches to `0xc0fe1758`/`0xc0fe1764`. Register safety **proven** (the engine uses no r6–r15).
  Save area `0xc1455000`: marker + seq + `first_seq[0..11]` (ring-wrap insurance, sentinel-guarded) +
  a 128-entry `{seq,r31,old,new}` ring. **Disassembly-verified** (`scratch/_verify_v13.py`); hash
  re-verify PASS. **NOT deployed** (dongle offline this session). Builder
  `scratch/diag_patch_v13/build_diag_patch_v13.py`; deploy = copy `modem.mdt`/`b01`/`b05`/`b16`
  into `/lib/firmware/` + reboot. See ledger §112.57.6.

- **§112.57.5 (CORRECTION to §112.57.3 — `0xc0fe1754` IS the general STM state writer)** — a
  **same-session self-correction**. §112.57.3 wrongly concluded the store `memw(r16+#0x4) = r18`
  at `0xc0fe1754` is skipped when the packet's conditional jump is taken. In Hexagon a packet
  issues as a unit — *"the instructions execute in parallel, and their results are written at the
  end of the packet. Branch decisions are also taken at packet end"* (Hex-Rays *Hexagon support*;
  Qualcomm V60/V61 PRM §3.3) — so the store **always executes**. **Confirmed by Ghidra
  decompilation** of the engine `FUN_c0fe1460`: `iVar4 = piVar9[1]; piVar9[1] = iVar6;` sits
  *outside* the `if (iVar6 == -2)` branch ⇒ unconditional. Logical proof: the engine reads
  `piVar9[1]` *after* the handler to detect a state change, so the engine (not the handler) must
  write it, and `0xc0fe1754` is the only `memw(obj+4) = …` in the whole STM cluster. ⇒ **§112.56.6
  stands; §112.57.3 retracted.** The v12 ring's *premise* is restored; only the *implementation*
  (whole-packet hook at `0xc0fe1740`, replay the packet, `jumpr` back to `0xc0fe174c`) was ever
  blocked. Offline only; no device touched. See ledger §112.57.5.

- **§112.57 (the v12 STM-state-writer ring — BLOCKED; two corrections)** — executing §112.56.9's
  recorded next step (a firmware ring on the STM state writer). The instrument was built,
  hash-verified and disassembly-verified, then **NOT deployed** (deployed once, immediately rolled
  back) because two offline checks falsified its premise. **(1) CORRECTION — the AP loads the
  baseband from `/lib/firmware` (persistent ext4 overlay), NOT from the `/dev/mmcblk0p3` `modem`
  partition.** A `fastboot flash modem` of the p3 FAT16 had **no effect** on the running baseband
  (`/lib/firmware/modem.mdt` stayed stock `1a6f9507…`); p3 is not mounted, no script copies it, and
  the DT has no `firmware-name`. To deploy a patched baseband, copy the changed `modem.*` files into
  `/lib/firmware/` and reboot. **(2) CORRECTION — the site `0xc0fe1754` is the 3rd instruction of a
  3-instruction PACKET** (`{ if(!p0.new) jump:t 0xc0fe1764; p0=cmp.eq(r18,-2); compound }`); a bare
  `jump` replacement breaks the packet parse bits (verified with `llvm-mc`, which reproduces the
  firmware bytes exactly). **(3) ⚠ CORRECTION to §112.56.6 — RETRACTED by §112.57.5** — this
  claimed the write at `0xc0fe1754` is **conditional on `r18 == -2`** and so is **NOT** the general
  state writer. **That is WRONG** (Hexagon same-packet instructions all execute); `0xc0fe1754`
  **is** the general STM state writer and §112.56.6 stands. Both `p3` and
  `/lib/firmware` were **restored to stock in the same session**; the running modem was never at
  risk. Root cause remains OPEN. See ledger §112.57.

- **§112.56 (the sleepmgr, decoded from the coredump — state names + full 31-message table
  byte-verified; the armer's ONLY caller; the generic STM/scheduler frameworks)** — continuing
  §112.55's read-only next steps 1–2. **(1)** The 12 state names are now **byte-verified from the
  coredump string pool** (each state-table `name` is a real `char *`): `INACTIVE, ONLINE,
  ONLINE_SLEEP_WAIT, SLEEP, … OFFLINE_SLEEP_WAIT`; class `0xc1a94f70` carries the literal
  `"LTE_ML1_SLEEPMGR_STM"`; instance `0xc1e158d0` has `+0x04 = state = 2`, `+0x14 = ctx
  0xc20f1680`. **(2)** The **full 31-entry message table** (`0xc1a95088`, `{name,id}`) is recovered
  (supersedes §112.55.6's partial list), incl. `LL1_SYS_SLEEP_CNF 0x040a0808`, `RF_SLEEP_CNF
  0x042b0802`, `GO_TO_SLEEP_REQ 0x042b0209`, `STMR_ON_REQ 0x042b0206`. **(3)** The armer
  `FUN_c0396fa0` has **exactly one reference in the whole firmware** (`call` @ `c0398764`, in the
  `OFFLINE_SLEEP_WAIT` handler `FUN_c03986b0`) and **zero** raw-pointer occurrences ⇒ **the
  "armed in state 11, fires in state 2" paradox is PROVEN real**. **(4)** `FUN_c0396f30` (state 2
  entry) is **short** (returns at `c0396f84`); §112.55's "big body" was a **Ghidra merge** of three
  adjacent functions (its §112.55.5 conclusion still stands). **(5)** `FUN_c0397150` is a shared
  helper called by **both** state 2 and state 11; it issues an **`LTE_LL1_SYS` request
  `0x040a0209`** (≠ `GO_TO_SLEEP_REQ`). **(6)** The **generic STM framework** is located: getter
  `stm_get_state(obj)=*(u32*)(obj+4)` @ `0xc0fe1960`; the transition writes `memw(obj+4)=newstate`
  @ `c0fe1754` (old-state exit `0xc0fe11e0`, new-state entry `0xc0fe11f0`). **(7)** The scheduler
  `0xc0b6xxxx` is a **generic** service (callers in ≥6 unrelated modules) ⇒ slot id 4 is the
  sleepmgr's own. **(8)** Context: `ctx+0x068 = 0x040a0808` (LL1_SYS_SLEEP_CNF's id); six embedded
  sub-timer structs `{base=0xc361a3f0, id=4, state=0xdeaddead}`; states 2/3 handlers are
  near-identical clones. ⇒ **the read-only path is saturated**; the remaining unknowns are
  **dynamic** ⇒ the decisive instrument stays the §112.55.7 **firmware ring on the STM state
  writer**. **Root cause still OPEN.** Offline, read-only; no device write, no patch.
- **§112.55 (THE FATAL IS A TIMER-EXPIRY CALLBACK — and the 12-state map; CORRECTS §112.54.3)** —
  continuing §112.54's read-only next steps 1–2. **(1) The sleepmgr STM has 12 states**, not 4
  (class `+0x14 = 12`; table `0xc1a94fc8` runs exactly 12×0x10 B and ends at the message table
  `0xc1a95088`): `INACTIVE, ONLINE, ONLINE_SLEEP_WAIT, SLEEP, ONLINE_WAKEUP, TTL_WAIT,
  LIGHT_SLEEP_WAIT, LIGHT_SLEEP, LIGHT_SLEEP_WAKEUP, OFFLINE_WAKEUP, OFFLINE_RECORD,
  OFFLINE_SLEEP_WAIT` (live `2 = ONLINE_SLEEP_WAIT` unchanged). **(2) Assert texts resolved**
  (`a2_descr.py`): `0xc3c81a40` = `lte_ml1_sleepmgr_stm.c:4054` `Assert stm_get_state (
  LTE_ML1_SLEEPMGR_STM ) == SLEEP failed`; `0xc3c81a50` = `…:4089` and `0xc3c81a00` = `…:10903`
  are `serv_cell != NULL`. The decompressed ERR_FATAL record in **both** coredumps reads exactly the
  4054 text. **(3) `FUN_c039ef80` is a software-TIMER EXPIRY CALLBACK, not a handler**: the fatal
  stack carries the scheduler array base `0xc1d9fef0`, the id-4 slot `0xc1da0830`, the stride
  `0x940 (=4×0x250)`, the stamp `0xe_e81861fe`, and the return address `0xc0b66fcc` — which
  disassembles to `c0b66fbc: memb(r20)=#3` (slot state → 3) … `c0b66fc0: r0 = memw(r17+0x70)` …
  `c0b66fc8: callr r0`. **(4) Registration/arm:** `FUN_c0b66ee0(id,fn)`/`FUN_c0b66f00(id,fn)` write
  `slot+0x70`/`slot+0xf0`; `FUN_c0396fa0` writes `slot+0x70 = FUN_c039ef80`, `slot+0xf0 =
  FUN_c039eed0`, then **arms** (`0xc0b663e0`). **(5) ★ CORRECTION:** `FUN_c0396fa0`'s **only** caller
  is `c0398764`, inside the **`OFFLINE_SLEEP_WAIT`** handler `0xc03986b0` — so §112.54.3's
  "`ONLINE_SLEEP_WAIT` registers `FUN_c039ef80`" is **WRONG** (a Ghidra merge artifact). **(6)**
  `FUN_c039eed0` arms the `ctx+0x4a8` watchdog and sends `STMR_ON_REQ 0x042b0206`; `FUN_c039ef80`
  cancels timer 4 (`func_0xc0b674b0(4)`). The full 31-entry sleepmgr message table is recovered.
  ⇒ **the fatal is a sleep-cycle timer expiring while the STM is mid-sleep-entry** (the §112.54
  "deadlock" framing stands, now pinned to a timer). **OPEN:** why the arm site is state 11 while the
  machine is state 2; the timer period (~0.313 s at 19.2 MHz); why `ONLINE_SLEEP_WAIT → SLEEP` never
  completed. ⚠ the older `lte_ml1_common_timer.c:390` attribution does **not** match these dumps'
  fatal record. Offline, read-only; no device write, no patch.
- **§112.53.1 (P-SCHED1 RESULT — activity ARMS, it does not GATE)** — the pre-registered early-only-traffic
  run produced a **FATAL at modem uptime `900.622162 s`** (`lte_ml1_sleepmgr_stm.c:4054`), **600 s after
  traffic stopped at mu 300 s**. `P-SCHED1-ARM` **CONFIRMED**, `P-SCHED1-GATE` **FALSIFIED**,
  `P-SCHED1-CLASS` **CONFIRMED** (a fatal, not a wedge). The coredump (`dump_devcd2_7362.bin`, md5
  `9e3cd57b…` device==host) has the **same task `slpc`, PC `0xc087a804`, and the identical fatal stack**
  (`+0x01c = 0xc039f7c4`), and the **same STM state `*(0xc1e158d4) = 2` = `ONLINE_SLEEP_WAIT`** ⇒ §112.54
  reproduced on a second independent specimen. ⇒ "ACTIVITY-GATED" is more precisely **"ACTIVITY-ARMED"**:
  early activity arms the ~900 s event; presence of traffic at the mark is irrelevant. Baseline restored
  (`preemptive_ssr_enabled=1`). n=1 (qualitative).
- **§112.54 (THE STATE NAMED — the fatal STM is in `ONLINE_SLEEP_WAIT`, not `SLEEP`)** — continuing
  §112.52's read-only next steps. **(1) The getter is proven:** `FUN_c03a0c20` →
  `c02d9f94` → `c0fe1960` where `c0fe1960: if(r0==0) assert; r0 = memw(r0+#4); return` ⇒
  **`FUN_c03a0c20()` = `stm_get_state(obj)` = `*(0xc1e158d0 + 4)`**, so `SLEEP = 3` is confirmed (compare
  is `cmp.eq(r0,#0x3)`). **(2) The state ENUM is recovered** from the class struct `0xc1a94f70`
  (`+0x04 = "LTE_ML1_SLEEPMGR_STM"`, `+0x18 = state table 0xc1a94fc8`, 0x10 B entries): idx 0 `INACTIVE`
  (h `c0396a40`), 1 `ONLINE` (h `c0396c50`), **2 `ONLINE_SLEEP_WAIT` (h `c0396f30`, f2 `c0397380`)**,
  **3 `SLEEP` (h `c03973a0`)**. Live value at the §112.49 fatal: `*(0xc1e158d4) = 2` =
  **`ONLINE_SLEEP_WAIT`**. **(3) The registration chain is proven from the coredump:** the
  `ONLINE_SLEEP_WAIT` entry handler `FUN_c0396f30` registers `FUN_c039ef80` and `FUN_c039eed0`
  (`c0396fc0: r1:0 = combine(##-0x3fc61080,#0x4); call 0xc0b663e0`); searching the coredump for the
  handler pointers finds each **exactly once** — `FUN_c0396f30`@`0xc1a94fec` (state-table +0x24),
  `FUN_c039ef80`@`0xc1da08a0`, `FUN_c039eed0`@`0xc1da0920`. ⇒ **the fatal function is entered as a
  handler only while the STM is in `ONLINE_SLEEP_WAIT` — the state it then asserts it is not in.**
  ⚠ **CORRECTED by §112.55.5:** the `c0396fa0` registration is a *separate* function whose only caller
  is the `OFFLINE_SLEEP_WAIT` handler; `FUN_c039ef80` is a **timer callback**, not a state handler.
  **Mechanism:** the sleep manager is **deadlocked mid-sleep-entry** — it left `ONLINE`, entered
  `ONLINE_SLEEP_WAIT` (a `GO_TO_SLEEP_REQ` issued, completion watchdog armed), and the sleep **never
  landed** (`SLEEP` never reached); a later event dispatches `FUN_c039ef80`, which asserts `state ==
  SLEEP` ⇒ ERR_FATAL. **§112.54.6:** the completion watchdog (`ctx+0x4a8`, callback `FUN_c03967f0`, a
  message-sender) is **DISARMED** (`+0x1c = 0xdeaddead`) at the fatal, so it did not fire the crash; and
  the line-4089 `serv_cell` assert is the *different* function `FUN_c0396850` — so `0xc3c81a40`/`50` are
  **two functions' asserts**, correcting §112.52's "one shared block" phrasing. Sleep-path message table
  (`0xc1a95088`) recovered (`GO_TO_SLEEP_REQ` `0x042b0209`, `RF_SLEEP_CNF` `0x042b0802`,
  `LL1_SYS_SLEEP_CNF` `0x040a0808`, …). **Does NOT add:** *why* the transition never lands (lost
  `*_CNF`? out-of-order wakeup? watchdog?) — still OPEN. Offline, read-only; no device write, no patch.
- **§112.52 (the assert SITE located — `FUN_c039ef80`, `lte_ml1_sleepmgr_stm.c`)** — continuing §112.51's
  read-only next step. Resolving the ERR/assert descriptor for line 4054 in the zlib descriptor DB gives
  **`0xc3c81a40`** (`lte_ml1_sleepmgr_stm.c`), and searching the stock disassembly for that exact
  constant finds its **unique** code load site: `c039f7c0` (`r0 = ##-0x3c37e5c0`), assert call
  `c039f7b8`/`c039f7c4`. The fatal task's stack (SP `0x8ae992d8` → modem VA `0xc46992d8`) carries the
  return address **`c039f7c4`** ⇒ the assert is issued from **`FUN_c039ef80`** (`c039ef80:
  {call 0xc0030000; allocframe(#0x88)}`), a sleepmgr handler registered at `c0396fc0`. The block is
  **shared** (packet `c039f7b8` → line 4054; packet `c039f7c4` → line 4089) and is reached from two
  in-function branches: `c039efec` (`if (FUN_c03a0c20(...) != 3)`) and `c039f098`
  (`if (FUN_c037121c(...) == 0)`). The return address `c039f7c4` pins the fired call to the **line-4054**
  one, so the fatal condition is **`FUN_c03a0c20(...) != 3`** = (hypothesis) `stm_get_state(
  LTE_ML1_SLEEPMGR_STM) != SLEEP` with `SLEEP = 3`. The stack also spills the live ctx
  (`table 0xc312db4c → 0xc20f1680`). **Adds:** the fatal is a **state-machine precondition** in a named
  sleepmgr handler (NOT the MCPM count guard of §112.38) — site, function, conditions, call all exact.
  **Does NOT add:** the root cause — why the STM was not in SLEEP is still OPEN, and `FUN_c03a0c20`'s
  identity / `FUN_c039ef80`'s role remain inferred. New tools `scratch/descr_scan.py`,
  `scratch/read_stack.py`. Offline, read-only; no device write, no patch.
- **§112.51 (the fatal assert, NAMED)** — resolving the hard-coded ERR_FATAL descriptor `0xc35b1384` in
  the §112.49 coredump gives `{line = 4054, msg_ptr → "Assert stm_get_state ( LTE_ML1_SLEEPMGR_STM ) ==
  SLEEP failed: ", file → "lte_ml1_sleepmgr_stm.c"}` ⇒ the fatal is **`ASSERT(stm_get_state(
  LTE_ML1_SLEEPMGR_STM) == SLEEP)`** — a sleep-manager **state** check, **not** the MCPM count guard
  `FUN_c0ce7fe0` that §112.38 found INERT. ⇒ §112.38's "the `sleepmgr:4054` guard is INERT" does **not**
  dispose of this fatal; the sleepmgr lead is **re-opened**. (Corrects my own §112.49.1 over-claim that
  the label was a generic "shared descriptor".)
- **§112.50 (mitigation hardening — fail-safe fallback anchor + fatal observer)** — audit of
  `modem-bearer-watchdog` found two real gaps, both fixed and deployed. **(1)** When `get_modem_uptime()`
  could not read the kernel's "is now up" line (dmesg evicted + no saved anchor), the old loop only
  *warned* and left the modem **unprotected** — the ~902.7 s deadline would simply expire. It now falls
  back to `SECS_SINCE_SSR`, a local timer since the last known restart (own SSR or observed fatal).
  **(2)** The loop never read dmesg, so a mitigation failure was **silent**; a fatal observer now counts
  `fatal error received` lines, logs each NEW site, and resets the local timer (baseline seeded at
  startup). Unit-tested with a mock dmesg; `sh -n` clean; deployed (`67136b8b…` → `cdb8c7db…` → `9a31d83`);
  backup `/root/modem-bearer-watchdog.pre-harden.bak`. Commits `fa47a85`, `9a31d83`.
- **§112.49 (PRE-REGISTRATION + RESULT — stock-firmware wedge-recoverability run)** — device run with the
  one-change discipline (`preemptive_ssr_enabled` 0→1): stock firmware (no patch), `a2_pin=1`, pre-emptive
  SSR **OFF**, continuous `ping -I wwan0` traffic, stall-watchdog fallback. The event at modem uptime
  **900.478 s** was a **FATAL** — dmesg `lte_ml1_sleepmgr_stm.c:4054`, coredump
  `/root/dumps/dump_devcd1_3334.bin` (85 398 475 B, md5 `580be11b…`) reads `Uptime 0:15:00`, task
  **`slpc`**, PC `0xc087a804`. **P-WEDGE-OUTCOME FALSIFIED** (it was not a wedge); the monitor's
  `WEDGE at up=3346` is a **false positive** (the ping failures were the fatal's SSR, not an independent
  wedge). ★ Corpus inconsistency §112.48.3 gains a clean data point: **traffic → FATAL** (supports
  §112.35, contradicts item 81; item 81's v7-instrumented firmware remains a confound). ★★ **`a2_pin`
  alone did NOT suppress this fatal** — the pin guards a *different* (cold-boot `a2_power.c:1189`) fatal;
  only the pre-emptive SSR suppresses the 900 s event. ★ The site label is the **fixed shared ERR_FATAL
  descriptor**, not the real site: reading the coredump confirms the MCPM guard `FUN_c0ce7fe0` is
  **INERT** (its snapshot arrays `DAT_c30fd9a8`/`DAT_c30fda28` are all zero; LPR `0xc1d473f8` = `"rpm"`),
  so this run does **not** revive the closed `rpm.sync` lead (§112.39) — the surviving root-cause
  candidate is still the **ML1-wide stall** (items 84–86/100/102). Pre-registration written **before** the
  event; coredump pulled device-local and md5-verified; device restored to the mitigated baseline.
- **§112.48 (WEDGE vs FATAL — the ~900 s event's two manifestations)** — offline re-parse of the two
  archived F3 series (`a2pin/f3stall/*` = the §112.34 idle run; `a2pin/f3assert/*` = the §112.35 traffic
  run). The wedge's F3 is a **staged shutdown**: RF (`rflte_*`) → 0 first, then an `a2_power` **storm**
  (53→114), then NAS/QMI decay, then only the `cfm_cpu_monitor` heartbeat; the fatal run instead has the
  RF **busy** (674→560) right up to the assert. **★★ A CORPUS INCONSISTENCY is recorded OPEN:**
  §112.34/35 say *traffic→assert, idle→no assert*; item 81 says the **exact opposite** — both cannot be
  right. Also: §112.34's "wedge" is **confounded with idle-sleep** (§104), so it is not a clean specimen;
  and the stated goal **"convert a fatal into a recoverable wedge" is likely COUNTERPRODUCTIVE** — a
  fatal recovers in ~1.7 s via the SSR (§112.35: assert 912.464 → up 914.168) while a wedge waits for the
  60 s stall watchdog. Two negatives of our own. No device write.
- **§112.47 (offline closure of §112.46.8's "open identification")** — pure offline re-derivation
  (stock `modem.asm` + the Ghidra decompilation; **no device write, no firmware build**). Establishes
  that (a) `obj[+0x38]` is a **fixed context ID `20+k`**, written once by `FUN_c02fb8b0` via
  `FUN_c02d7b80(ctx_base, 20+k)` — not a transient state; (b) `inst[+0x300]` is the pending-selector
  bitmask, **set only** by `FUN_c02fba64` (15 sites, one fixed selector each) and cleared by
  `FUN_c02fc3cc`; (c) the **`0x80` (ctx0) selector is passed at exactly 2 sites, both inside
  `FUN_c02fda90`** ⇒ there is **no hidden high-rate ctx0 armer** and a v3 ring is **REDUNDANT**
  (§112.45.5 already noted the v7–v11 rings ARE the `FUN_c02fda90` instrument). Two nulls recorded: the
  F3 `cap_fatal.bin` carries **no temperature record** (the 16 `temp` hits are the `PC_PENDING_TEMP`
  A2 client name), so a thermal/PA-aging hypothesis gets no log support; and the capture tail is a
  quiet post-SSR idle modem. **A negative of our own is stated** — the hoped-for lead is removed and a
  v3's value is downgraded to zero. Device left on stock firmware (md5-verified).
- **§112.46 (P-CANCELRATE — the state-20 ARM-vs-CANCEL lifecycle ring)** — a firmware ring (the v7/v8
  cave technique) retargeting 3 `call` sites: **ARM** `FUN_c02fda90` at `0xc0326874`/`0xc033c0f4` (both
  its call sites) + **CANCEL** `FUN_c02fc3cc` at the ML1 dispatcher `0xc034e1f0`. Offline-verified (hash
  PASS, read-back disassembly) before any write; boot-transparent (the retargeted dispatcher did **not**
  reproduce the §87 v12 crash-loop). **The fatal NEVER fired** — a 34-min watcher saw an empty fatal grep
  from AP 159→2175 and the modem **WEDGED** (data path dead, `mmcli` still `running/connected/lte`; the
  §71 regime, third confirmation). The coredump was **forced on-demand** (`dump_devcd1_1536.bin`, md5
  `f05204e8…`) and **carries NO filled crash report** (only format strings) — a forced watchdog crash
  cannot be classified. Ring valid (G1–G3 PASS; **G4 FAIL** — wedge, not fatal): **ARM = 32**,
  **CANCEL = 947** (last arg `0x80`), ring 128/128 CANCEL. **VOID on two independent grounds:** (a) the
  CANCEL site `0xc034e1f0` is a **shared dispatch MERGE POINT** — cases `jump` there with selectors
  `1,2,4,8,0x10,0x20,0x40,0x80,0x100,0x200,0x400,0x800,0x1000,0x2000,0x4000` — so the count is a
  **generic ML1-dispatch count, not the ctx0 cancel** (the §87 trap; the ring stores no per-entry
  selector, so the ctx0 subset is unrecoverable); (b) `FUN_c02fda90` fired only 32×, **corroborating
  §63.5** ("entered exactly 4 times in 900.6 s … essentially dormant") and **refuting the
  pre-registration's "~0.63/s armer / 565–674 arms" premise** (≈ the dispatcher rate). A second forced
  crash (`dump_devcd2_2057.bin`) reads the save page as **magic 0, count 0** ⇒ the page is **cleared at
  modem boot** (no persistence). The `0xec121000` counter **rate is unresolved** (builders say 204 800 Hz;
  `ll1_ring_dump.py` says 19.2 MHz; a cross-dump LL1 test is ambiguous because the seq counter resets at
  boot) ⇒ report raw ticks; the ordering is rate-independent. **Rolled back to stock firmware**
  (md5-verified) + config restored (`preemptive_ssr_enabled=1`). Pre-registered
  (`PREREG_cancel_ring.md`); a negative of our own is stated. Tools:
  `scratch/cancel_ring/{build,read}_cancel_ring.py`, `PREREG_cancel_ring.md`.
- **§112.45 (P-MEASRING — the measurement-ring firmware instrument)** — a deterministic firmware ring
  (the v7/v8 cave technique) retargeting the **5 literal `call` sites** of the three ML1
  measurement-scheduler primitives (1 ARM `FUN_c01ecbf4` + 2 WRITE `FUN_c01bc8e0` + 2 DRAIN
  `FUN_c01bc934`), ring on save page `0xc1455000`, entered by `call` (r31 = packet return addr) and
  tail-jumping (`jumpr`, r31 intact) ⇒ **semantically transparent**. Offline-verified (hash PASS,
  read-back disassembly) **before any device write**; boot gate PASSED; captured the real fatal
  (`lte_ml1_common_timer.c:390`, modem ≈901.83 s, coredump `dump_devcd1_916.bin`, md5 `8912a618…`).
  Ring valid (G1–G5, count 389 239). **Literal pre-registered answer = class 1 "ARM stopped first"**
  (`last_ts[ARM]=88.0 s` vs WRITE `901.4 s` / DRAIN `901.8 s`) — **but CONFOUNDED and NOT promoted**:
  the modem stayed fully healthy for **813.8 s after the ARM stream stopped**, the ARM/event-0x10
  stream is **early-concentrated** (5 079 arms at ~58/s for 88 s, then 0 in the last 128 events;
  ARM:WRITE = 1:38), and the WRITE/DRAIN hooks fire on **every call** (incl. no-op iterations) so the
  192 k counts over-count (the armed slot's `+01` is still 0 at the fatal). ⇒ the instrument targeted
  the **wrong armer**: the fatal's armer is the **state-20 watchdog armer `FUN_c02fda90`**, not
  event-0x10. **Also fixes a real bug in the v7 `call` re-encode**: bit 24 (field sign) must be
  **set from the sign of the new field**, not preserved from the old word — validated against all 5
  stock sites **and** the LLVM toolchain's own encoder. **Rolled back to stock firmware** (md5-verified)
  + config restored (`preemptive_ssr_enabled=1`). Pre-registered (`PREREG_meas_ring.md`); a negative of
  our own is stated. Tools: `scratch/meas_ring/{build,read}_meas_ring.py`, `PREREG_meas_ring.md`.
- **§112.44 (P-FLAGPOS Stage 1 — the §105.4 flag's mechanism)** — the offline PC-STRUCT test the user's
  positive-control request called for. The **event-0x10 handler `FUN_c01ecbf4` clears `d2` and then arms
  the ring**: `FUN_c01bc724`'s `memset(carrier+0xbc, 0, 0x12c)` covers **`+0xd2`** (and `+0xbe`), and
  `FUN_c01bc7f0` then sets `carrier+0x00=1`, `slot[b9]+0x00=1`, `carrier+0xb8=(b8+1)mod3` (reads no
  `d2`, writes no `+0x08`). The template at `0xc1d7e91e` is `01 08 28 28 …` ⇒ the handler sets
  **`e02=1`**. ⇒ **`(d2=0, armed)` is reachable — indeed FORCED — by construction**; the §105.4 flag is
  the deterministic post-event-0x10 state (confirmed byte-for-byte in the 3 `d2=0` fatals:
  `up915.42/44`, `up1818.92`). The gate `FUN_c01bc934` **cannot drain** an armed slot until the writer
  `FUN_c01bc8e0` fills `+0x08`/increments `+0x01` ⇒ the fatal is the arm whose **result is never
  written** (§106's "consumer stalled", refined). **Consequence:** the pre-registered probabilistic
  **Stage 2 (n=8) is superseded and NOT run** — a snapshot cannot measure *persistence*, so the test
  cannot distinguish "transient" from "stuck"; the amendment is recorded in `PREREG_posctrl.md` before
  any run. Read-only/offline (no device access). §105.4's observation stands with its mechanism now
  named; §112.41 stays retracted; §105.8's "about to die" is directionally right but the flag is not a
  state that cannot exist healthy.
- **§112.43 (e02 reachability)** — `ifdown modem` (MM `registered`, LTE, no bearer) + a §6.2a
  capture (`dump_devcd1_3125.bin`, md5 `65dc280a…`, no crash report) reads carrier 0
  `e02=1, d2=1` (consumed slot, no flag) and **carrier 1 `e02=1, d2=0`** (empty, no flag).
  ⇒ **`(d2=0, e02=1)` IS reachable in a healthy modem** — the `e02` objection that voided
  P-IDLECTRL2 is **removed**. But the healthy instance was an **empty carrier** (no armed
  slot), so the flag's absence is **vacuous** and does not test it; the direction now
  **favours §105.4** (the flag = "armed but undrained"; healthy carriers always drain). Also
  refines `d2` to **per-carrier**. Device restored. Read-only analysis + one forced capture.
- **`b7b2ece` §112.42 (phase 3, the ML1 meas-table flag)** — a 39-dump census shows
  the §105.4 flag (`+0x00=1` on the `b9` slot **and** `b8=(b9+1)mod3` **and** data=0
  **and** `cons=0`) is confined to (`d2=0` **and** `e02=1`) dumps — so far exactly the
  **3 `d2=0` fatals**; the 2 healthy `d2=0` captures both have **`e02=0`**, which
  **mechanically disables** the flag (gate B `0≤+0x01` always passes). ⇒ the flag's
  fatal-specificity is **UNRESOLVED** (a new confounder, `e02`, was found). Two
  corrections: **§112.41 is RETRACTED** (its `slot[+0x01]=0` evidence was already
  falsified by §105.2 — it is the resting value) and **§105.8's "confound resolved" is
  UNSUPPORTED** (its four controls all read `d2=1`, so §105.5 was never executed).
  Design constraint: a high-rate RAM sampler is **impossible** (TrustZone blocks all AP
  reads of modem RAM). **P-IDLECTRL2 RAN** (`dump_devcd2_13985.bin`, md5
  `a6905ca4…`): the idle modem survived to modem-up **966 s** (`d2=0`) but read
  **`e02=0`** ⇒ **VOID by the pre-registered gate** — the gate fired as designed.
  Device restored (SSR 1500→800, reboot). Read-only analysis + one forced capture.
- **`adb9706` §112.41 (phase 2, ML1 SERV-MEAS-RSP reply path)** — ⚠ **RETRACTED by
  §112.42** (the `slot[+0x01]=0` evidence is non-discriminating). Kept for the
  record: traced the chain (`FUN_c02fda90` armer → CNF gate `FUN_c01bc934` →
  ready-writer `FUN_c01bc8e0`); its "producer never ran" claim holds only for the
  `d2=0` dumps. Read-only, offline.
- **`9d95c78` §112.40 (phase 1, RF/interference lead)** — re-confirmed item 71's RF
  collapse with a new tool `scratch/rf_timeline.py`; found the
  `rflte_core_rxctl` RX gain/freq-comp values are **always 0** (no retune storm);
  showed the collapse is **state-dependent** (876 vs 12 RF records across the two
  wedges). Verdict: **precursor**, cause unknown; the fatal-run RF-vs-assert
  ordering is not pinnable from the multi-session wrapping captures. Read-only,
  offline.
- **`be50e56`** — Add this CHANGELOG.md (Keep-a-Changelog format) documenting
  commit `03bbc17` and the prior §112.37–§112.39 series. Doc 197 remains the
  primary source of truth; this file is a quick-reference index.
- **`03bbc17`** — §112.39 sync note: findings folded back into §112.38.
  - Added a "Sync with §112.38" subsection to §112.39 recording that its
    findings were carried back into §112.38 (commit `c1ad1d9`): the MCPM row #1
    7-dump coredump confirmation (scratch zeroed 7/7, `q6pcvote` LIVE
    1060-1647), the OPEN-section `rpm.sync` RULED OUT, and the ML1-side
    SERV-MEAS-RSP ctx0 watchdog (items 84-86/100/102) listed as the surviving
    candidate.
  - Updated §112.39's Achieved/Expected table last row to cross-reference
    §112.38's OPEN section and items 84-86/100/102.
  - Net state recorded: the `rpm.sync` LPR park axis is **CLOSED**; the
    firmware-timer axis remains **exhausted** (§112.38); the one open question
    — *what stops the ML1 SERV-MEAS-RSP reply path at ~902 s* — is a
    causal-mechanism question, not a timer-limit question.

### Prior commits in this series

- **`c1ad1d9`** — §112.38 update: fold §112.39 ML1 counter findings back in.
  MCPM row #1 coredump-confirmed 7/7; OPEN section `rpm.sync` RULED OUT, ML1-side
  SERV-MEAS-RSP ctx0 watchdog added as surviving candidate; Achieved/Expected
  and SOP/Tools updated.
- **`b9275ad`** — §112.39: `rpm.sync` LPR park CLOSED — `q6pcvote` LIVE, MCPM
  scratch zeroed, not a hang. Read-only disassembly + 7-dump coredump synthesis.
- **`24e1001`** — §112.38: firmware timers/watchdogs complete inventory — three
  distinct ~902 s watchdogs (MCPM INERT, ML1 state-50ms FALSIFIED items 85/86,
  a2_power >900 EVALUATED); no untested lever.
- **`fe1653b`** — §112.37: PSCI/cpuidle axis CLOSED — §112.29 "cluster
  unreachable" retracted; cpuidle sub-lever already falsified (H-IDLE); no
  remaining reversible lever.
