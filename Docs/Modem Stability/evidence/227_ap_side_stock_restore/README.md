# 227 evidence — the AP-side stock restore and the P-W1 soak

**Opened:** 2026-09-28.
**Device:** HMU05 serial `c2b9103c` at `192.168.8.1` (USB ethernet, MAC `02:00:c2:b9:10:3c`).
**OpenWrt:** 25.12.5, kernel 6.12.94.

This directory holds the evidence for the plan in
`~/.codebuddy/plans/radiant-aurora-newton-FYUTYbLS.md`: restore stock HMU05 firmware, finish
the P-W1 pre-registration, bisect the SSR-teardown AP hang, and probe axis A15.

---

## 0. Pre-state snapshot (before anything was changed)

`00_pre_state_snapshot.txt` — the full `/lib/firmware` md5 inventory plus identity, taken
**before** any modification.

| item | value |
| :-- | :-- |
| `boot_id` | `1cd22c6b-43e6-4ca4-b21a-df6a1e295920` |
| AP uptime | 4967 s |
| modem operating mode | **`offline`** |
| `qcom_bam_dmux.ko` | `9c08871ff0d29f714acfa89b41893898` — **patch 825 live** |
| `/usr/sbin/ModemManager` | `8182d40d91396df70e3f02ca91ab7a6b` — **patch 0005 live** |
| firmware files | 109 (63 of them under `modem_pr/`) |

### ★ The deployed firmware is NOT reproducible from the host

This was discovered here and is worth recording, because it changes what "reversible" means.

| file | **on the device** | `scratch/uz_new/image` | `GitIgnore/UZ801 Modem/` |
| :-- | :-- | :-- | :-- |
| `modem.mdt` | `c132421fe6d7766b012f59d3f851db87` | `b27ce586623a865057dae1d7595c268c` | `b27ce586623a865057dae1d7595c268c` |
| `modem.b16` | `f6f9900ea3845a1cdab67f6a3c0f1e26` | `848abe7eea4b772741eaaf3225dc5434` | `848abe7eea4b772741eaaf3225dc5434` |
| `modem.b18` | `29e777da91697e8b88c9c1a57d43578c` | `5fa63200bf3ae7834164e523c3b12012` | `5fa63200bf3ae7834164e523c3b12012` |
| `mba.mbn` | `4869138c24f4fda3903efc78060c275c` | `7aa1bcd5131bbbeeaea9c4a27dbe0cd3` | `7aa1bcd5131bbbeeaea9c4a27dbe0cd3` |
| `wcnss.mdt` | `3d028c77ebe034d7fe25d5033cccb5bf` | `3d028c77ebe034d7fe25d5033cccb5bf` ✓ | same ✓ |
| `cmnlib.mdt` | `dec6fdd2544aac29f119d44e229f1108` | `dec6fdd2544aac29f119d44e229f1108` ✓ | same ✓ |

**The device runs a different UZ801 build than the FAT16 dump** (this is the `f6f9900e…` vs
`848abe7e…` divergence recorded in memory). So the modem set **cannot be restored from the host
copies** — the host set is a different build.

### Reversibility, established before overwriting

Two independent safety nets, both verified:

1. **On-device:** `/root/fwbackup_20260928` — **22/22 files byte-identical** to the deployed
   `modem.*` + `mba.mbn` (verified file-by-file this session). It covers the modem set only,
   **not** wcnss/cmnlib/keymaste.
2. **On the host:** `pre_state_firmware.tar.gz` — a complete `tar czf` of the device's whole
   `/lib/firmware` (28 138 386 B, **109 members**), pulled and verified:

   ```
   device md5  7f9feb868b27b02163502949584059aa
   host   md5  7f9feb868b27b02163502949584059aa   == IDENTICAL
   sha256      9ade395009dc241dfbe9b1c5da5e0de7c52ec7f2209c7fbebb5279e66509db83
   ```

   (The device copy remains at `/overlay/pre_state_firmware.tar.gz`.)

### ⚠ `scratch/swap_uz21_full.sh --revert` must NOT be used

Its source dirs `/overlay/fwbackup/hmu05_stock` and `/overlay/fwbackup/hmu05_wcnss_tz` are
**both MISSING** on the device. The script's `cp` is stderr-suppressed with `|| true`, so it
would fail silently, still delete `modem_pr`/`isdbtmm.*`/`playread.*`/`widevine.*`, still
**reboot**, and still `exit 0` — a silent half-revert that looks like success. The stock
restore is therefore done by pushing from the host with md5 verification (see §1).

---

## 1. The stock restore

**Outcome: VERIFIED COMPLETE.** The device now runs the stock HMU05 firmware set,
byte-identical to the documented ground truth, with the modem reading `online`.

### 1.1 Chain of custody for the stock set

The set pushed to the device is `scratch/mcfg_extracted/image/` (51 files). Its
authenticity is corroborated **independently of this session** by
`scratch/swap_uz21_full.sh`, which was written on **2026-09-25 while stock was still live
on the device** and hard-codes the stock md5s in its own `--revert` expectation line:

```
expected stock: modem.mdt  1a6f9507e03d4ddbbf1977af81ecdbd7
                wcnss.mdt  62bb56b2bfd0a1aa1ef57ec44b51a396
                cmnlib.mdt 78752167469a6b0d981765445fbb235e
                mcfg_sw.mbn 0be0d361224873be71e012637e04b3e4
```

All six documented values match `scratch/mcfg_extracted/image/` **and** the post-restore
device. `scratch/stock_set_227/` is the same 51 files (verified file-by-file, 0 mismatch);
`scratch/stock_set_227.tar.gz` is the archive of that set.

### 1.2 What was done

1. Pushed the 51 stock files with the `swap_uz21_full.sh` protocol — each file's md5 is
   checked **on the device** (`/tmp/$f.new`) *before* it is installed, so a truncated
   transfer aborts without changing anything.
2. Deleted the UZ801-only residue: `modem_pr/` (the whole tree, 63 files) and `isdbtmm.*`.
3. `sync`, then `reboot`.

### 1.3 Post-restore verification (`01_post_restore_verification.txt`)

`boot_id 6c19817a-d969-4138-a1e1-c7efd24f0d86`, modem operating mode **`online`**.

| check | result |
| :-- | :-- |
| stock-manifest files present on device | **51 / 51** |
| stock-manifest files byte-identical (md5 join) | **51 / 51, 0 MISMATCH** |
| stock-manifest files missing | **0** |
| `modem_pr/` present | **absent** ✓ (stock has none) |
| `isdbtmm.*` present | **absent** ✓ (UZ801-only) |
| `mcfg_sw.mbn` | `0be0d361…` ✓ — the **package** carrier config, not the UZ801 one |

The five device-only files are all expected and none is UZ801 residue:
`DUMPED` (marker), `MCFG_SW.MBN` + `mcfg_sw.mbn` (the carrier config, correctly the
package copy `0be0d361…`), `regulatory.db`, and `wlan/prima/WCNSS_qcom_wlan_nv.bin`
(explicitly *not* touched by the swap — UZ801 ships no `wlan/` dir).

Selected md5s, device vs documented stock:

| file | device | documented stock | |
| :-- | :-- | :-- | :-- |
| `modem.mdt` | `1a6f9507e03d4ddbbf1977af81ecdbd7` | same | ✓ |
| `modem.b16` | `57fef19de7178fb732c8b2edc40bc9dc` | same | ✓ |
| `modem.b18` | `43434a4053ca82c3c33581c41e5c2cc5` | same | ✓ |
| `mba.mbn` | `dcc67421780587c5b0e25cb6c99cb35f` | same | ✓ |
| `wcnss.mdt` | `62bb56b2bfd0a1aa1ef57ec44b51a396` | same | ✓ |
| `cmnlib.mdt` | `78752167469a6b0d981765445fbb235e` | same | ✓ |
| `keymaste.mdt` | `907006be3febbda61061ebbd1fac0d17` | same | ✓ |
| `mcfg_sw.mbn` | `0be0d361224873be71e012637e04b3e4` | same | ✓ |

The UZ801→stock delta that the restore had to undo was therefore
`modem.*` + `mba.mbn` (the baseband), `wcnss.*`, `cmnlib.*`, `keymaste.*`, plus the
`modem_pr/` + `isdbtmm.*` additions.

### 1.4 The capture run armed on this state

With stock restored and the modem `online`, the natural ~902.8 s fatal was armed for a
coredump capture:

- `rmmod qcom_bam_dmux` — **essential** (Doc 217 §3.3): the module carries the SSR
  notifier that hangs recovery, so removing it is what lets the dump be generated.
  Verified after: `qcom_bam_dmux` absent from `lsmod`, all `wwan*` down, no holders.
- `echo enabled > /sys/class/remoteproc/remoteproc0/coredump` — confirmed `enabled`.
- `coredump_watch.sh` running on the device (background task `ecxybs`), polling every 15 s
  via `watch_fatal.sh` into `fatal_watch.log`.

**This run is a strong test in its own right:** with `qcom_bam_dmux` fully removed there is
no AP-side data path and no bearer at all, so a fatal at ~902.8 s proves the timer is
entirely AP-independent. (It reproduces pmOS fatal #3's condition, which also had no data
path — `project_900s_fatal_anatomy.md` §11.)

> ⚠ **Restore after the capture:** `modprobe qcom_bam_dmux` and re-`bind` the DIAG rpmsg
> channels (`/root/diag_bind.sh`) before running the P-W1 soak.

---

## 2. ★ THE CAPTURE: the natural fatal, with `qcom_bam_dmux` removed

**It fired. The coredump was captured.** This is the first **stock-HMU05 natural-fatal**
coredump (the only previous stock-family dumps were the UZ801 `echo crash` capture and the
run-8/run-9 corpus).

### 2.1 The fatal

```
[  913.564657] qcom-q6v5-mss 4080000.remoteproc: fatal error received: lte_ml1_common_timer.c:390:
[  913.564725] remoteproc remoteproc0: crash detected in 4080000.remoteproc: type fatal error
[  913.572316] remoteproc remoteproc0: handling crash #1 in 4080000.remoteproc
[  913.580657] remoteproc remoteproc0: recovering 4080000.remoteproc
[  913.595730] remoteproc remoteproc0: stopped remote processor 4080000.remoteproc
[  914.295610] qcom-q6v5-mss 4080000.remoteproc: port failed halt
[  914.343302] qcom-q6v5-mss 4080000.remoteproc: MBA booted without debug policy, loading mpss
[  914.893387] remoteproc remoteproc0: remote processor 4080000.remoteproc is now up
```

| quantity | value |
| :-- | :-- |
| AP uptime at the fatal | **913.565 s** |
| AP uptime at modem power-up (this boot) | 10.968 s |
| **modem uptime at the fatal** | **902.60 s** |
| crash-log `Uptime (h:m:s)` | `0:15:01` (= 901 s) |
| site | `lte_ml1_common_timer.c:390` |
| SSR recovery | `port failed halt` → MBA reload → up in **1.30 s**; **no AP hang** |

### 2.2 ★★ Why this run is decisive: `qcom_bam_dmux` was NOT LOADED

The module was `rmmod`'d before the capture and the dump watcher confirms it stayed out
(`lsmod` re-checked after the fatal: absent). So at the moment of the fatal there was

* **no `bam_dmux` driver at all** — no A2 handshake, no pc-ack, no `runtime_resume`, no TX/RX;
* **no bearer**, no `wwan*` interface, no data path, no userspace QMI bearer.

The fatal still fired at **902.60 s modem uptime**, on schedule. This is the fourth
independent confirmation of AP-independence and the cleanest one yet: pmOS fatal #3 (no data
path), the RPM-vote-invariance test (1 vote → 910 s, 311 → 912.6 s), and now
**bam_dmux fully removed → 902.60 s**.

⇒ **No AP-side change can prevent this fatal.** The only lever that moves it is the modem's own
LTE stack (P-PMOS3: `offline`/CFUN=4 suppresses it; P-PMOS5: no reversible lever).

### 2.3 The modem's own crash record (`02_natural_fatal_crash_log.txt`)

The coredump carries the modem's full ERR crash log. The header, verbatim:

```
ERR crash log report.  Version 3.
Error in file lte_ml1_common_timer.c, line 390
Error message: Assert 0 failed:
Time of crash (m-d-y h:m:s): 09-28-2026 9:35:38
Uptime (h:m:s): 0:15:01
Build ID: HIMI_U01_MODEM_V1.0
REX_TCB ptr: 0xc3c0bf84
tcb.task_name: tmr_slave3
Coredump ARCH type is: ERR_ARCH_QDSP6
```

**New facts this capture gives us:**

1. **The fault is an `ASSERT(0)`** — a bare always-fail assert, not a `"Assertion (expr) failed"`
   message. The message text `Assert 0 failed: ` is composed into the record at run time and is
   **not** a static string in the ELF (searched; absent), which is why a static grep for it fails.
2. **It executes in REX task `tmr_slave3`** — a **REX timer-slave task**, i.e. it is a **timer
   callback** that trips the assert. That is the strongest direct evidence yet that the ~902 s
   event is a *timer expiry*, not a data-path stall.
3. A full **QDSP6 register set** and a **stack dump** are recorded (`SP 0x8ad52160`, `FP
   0x8ad52168`, `LR 0xc0879164`, `PC 0xc087a804`). ⚠ The recorded `PC` is **not** the fault
   address: disassembly shows it is inside the *context-save* routine itself
   (`c087a804: r1 = pc ; memw(r0+#0x80) = r1`), so it is self-referential. The usable
   information is the **LR and the stack return addresses**.
4. A **REX watchdog `dog_state_table`** (200 slots) listing every task's timeout, count and
   blocked flag. The only monitored (`Timeout 60, Is_Blocked 0`) entries are
   `DSMSGR RECV`, `tc`, `hdrsrch`, `pgi`, `mgpmc`, `cd`; every LTE/ML1 task shows
   `Timeout 0, Count -1, Is_Blocked 1` (excluded from the dog). This is a **new instrument** —
   it names the tasks the REX watchdog was actually watching at the moment of death.
5. The `lte_ml1_common_timer.c` filename appears in a **24-byte-stride filename table**, and the
   coredump's copy of the ELF rodata is offset by a **content-verified constant**:
   **`dump_va = elf_va − 0x39800000`** (verified: ELF `0xc175b2b8` ↔ dump `0x87f5b2b8`, same
   bytes). This is the same bias Doc 163 recorded, now re-verified on a fresh dump.

### 2.4 The ERR_FATAL descriptor — firmware-side confirmation

```
dump_va 0x89db1280  03 00 00 00 01 00 00 00 84 bf c0 c3 03 00 00 00
dump_va 0x89db1290  86 01 00 00 f9 5c 82 2d 85 ab 12 01 00 00 00 00
dump_va 0x89db12a0  00 00 00 00 6c 74 65 5f 6d 6c 31 5f 63 6f 6d 6d
dump_va 0x89db12b0  6f 6e 5f 74 69 6d 65 72 2e 63 00 00 00 00 00 00
dump_va 0x89db12d0  00 00 00 00 00 00 41 73 73 65 72 74 20 30 20 66
```

| field | value |
| :-- | :-- |
| line (`+0x10`) | `0x0186` = **390** ✓ matches dmesg |
| filename (`+0x24`) | `lte_ml1_common_timer.c` ✓ matches dmesg |
| message (after the filename) | `Assert 0 f…` ✓ matches the crash log |
| A (`+0x14`) | `0x2d825cf9` |
| B (`+0x18`) | `0x0112ab85` — **also in `QDSP6_GP_R7`** (R8 = `0x0112ab7a` = B−11) |
| `+0x08` | `0xc3c0bf84` — the value the crash log labels `REX_TCB ptr` |

⇒ the dmesg `file:line` is faithful, and the crash log's "REX_TCB ptr" is really the
descriptor's `+0x08` field, not a task control block.

### 2.5 The corpus census (`03_coredump_corpus_census.txt`)

Decoding all 42 coredumps on the host gives the true signature distribution. **There are FIVE
assert sites, not three:**

| signature | dumps |
| :-- | --: |
| `lte_ml1_sleepmgr_stm.c:4054` | 18 |
| `a2_power.c:1189` | 11 |
| **`lte_ml1_common_timer.c:390`** | **9** |
| `a2_power.c:2949` | 3 |
| `a2_taskq.c:759` | 1 |

`a2_power.c:2949` and `a2_taskq.c:759` are **not in the consolidated model's list of three** and
should be folded in. `B` is a **global fatal counter**: it runs monotonically across the whole
corpus (`0x01128bfc` → `0x01128e13`), advancing ≈ +11 per fatal period.

### 2.6 What this does and does not settle

**Settles:**
* The ~902 s event is an **`ASSERT(0)` inside a REX timer-slave task**, i.e. a timer callback.
* It is **AP-independent** (four independent confirmations, the strongest being this one).
* The dmesg `file:line` is faithful; the descriptor says `lte_ml1_common_timer.c:390` too.
* Removing `qcom_bam_dmux` makes the **SSR recovery clean** — `port failed halt` → reload → up in
  1.30 s, **no AP hang**. This independently confirms Doc 217's mechanism (the hang is in the
  bam_dmux SSR-teardown notifier, not in the remoteproc core).

**Does not settle:**
* **Which** ML1 timer fires. The timer-name tables found near the crash-log region belong to
  **TD-SCDMA RRC** (`tdsrrctmr.c`), *not* LTE ML1 — an easy mis-attribution, now excluded. No RAM
  pointer to any timer-name VA exists in the dump, so the fired timer cannot be named from the
  dump alone.
* Whether the assert is the *cause* or the *report* of a preceding error. `DELAY_ERR_FATAL_TIMER`
  exists as a TD-SCDMA name only, so the "deferred fatal" reading is **not** supported.
