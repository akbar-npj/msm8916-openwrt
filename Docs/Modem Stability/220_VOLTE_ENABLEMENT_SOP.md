# 220 — VoLTE Enablement: LIVING SOP, failure ledger, and success record

**Date opened:** 2026-11-12
**Branch:** `test/volte-openimsd`
**Device:** HM-MIFIB-V12 (`mifib-v12`), OpenWrt, bench `192.168.8.1` over WiFi (`wld0`)
**Baseband:** `MPSS.DPM.2.0.2.C1-00178`, Build ID `HM02_MODEM_V1.2`
**Status:** **LIVING DOCUMENT — append to it; do not fork it.** This file is the single source of truth for
the VoLTE campaign: what we are after, what we have tried, what failed and why, what worked, and the
procedure that produced each result.

**Companions:** `project_volte_enablement.md` (the running RE notebook — raw findings, §1–§18),
`197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md` (the baseband-RE conventions this doc inherits),
`../RELEASING_FIRMWARE.md` (never ship any of this without reading it).

---

## 1. What we are after

**Goal: VoLTE on the mifib-v12.** The network in use (Jio, MCC/MNC `405861`) is **VoLTE-only** — it
advertises `Voice support: 'no'`, `IMS voice support: 'yes'` in `--nas-get-system-info`. Without IMS
there is no voice service at all: no CS fallback, no circuit-switched call.

**The blocker is precisely located and is modem-side.** On a registered network with the ISIM detected
and a live default data bearer:

- the modem sends **exactly one** IMSD message, msg id **`0x23`** (a link-local announcement,
  `fe80::…`), on service bind;
- the modem **never sends `0x20` `IMSD_START_CONNECTION`**;
- therefore **no IMS PDN is created**, and **IMSA never leaves `not-registered`**.

**Protocol direction (this cost us a whole round — do not re-litigate it).** `81voltd` on the AP is the
IMSD **server** (`/tmp/81voltd-spec/qvd-server.c`: `#define IMSD_START_CONNECTION 0x20`,
`process_start_connection()`). The **modem is the client**. The AP cannot initiate IMS; it can only
answer a request the modem never makes. Every AP-side lever (81voltd behaviour, op_res replies, PDN
creation, DMS reset, `AT+CEMODE`, QMI PDC) has been exhausted and is spec-correct.

⇒ **The fix must make the modem's IMS task emit `0x20`.** That is the whole campaign.

## 2. What success looks like (definition of done)

Ordered, each one independently checkable. Do not claim success on a partial.

| # | Check | Command / evidence | Status |
|---|---|---|---|
| D1 | The modem sends IMSD `0x20` with TLVs `0x01` (wds_spec/apn), `0x10` (connection), `0x13` (subscription) | 81voltd debug hexdump / `qmi-raw` capture | **PASS** (S23) |
| D2 | An IMS PDN is created (a second `wwan`/`rmnet` bearer, IMS APN) | `mmcli` bearer log; `ip -6 addr` | **PASS** (S23) |
| D3 | IMSA registers | `qmi-raw … 0x21 0x0021` → TLV `0x10` = `02` | **PASS** (S23) |
| D4 | `81voltd` answers `0x20` with success and the IMS session comes up | 81voltd log, no `connection down (err 13)` | **PASS** (S23) |
| D5 | A voice call completes over LTE with no CS fallback | `qmi-raw … 0x09 0x002f` → `type=0x02 VOICE_IP`, `state=0x03 CONVERSATION`; callee phone rings | **PASS** (S24) |
| D6 | The device still boots, registers, and passes data after the change | §6 acceptance gate | **PARTIAL** — see §9.1 |

**D1 alone is the milestone that proves the fix direction.** If we can make the modem *ask*, the rest is
AP-side plumbing we already understand.

## 3. System map (what we currently believe, with confidence)

```
   AP (OpenWrt)                                  MODEM (Hexagon, MPSS)
   ────────────                                  ─────────────────────
   81voltd  ── IMSD service 770 SERVER ──┐
     ▲                                   │  QMI over bam-dmux / smd
     │ 0x23 (link-local announce)        │
     └───────────────────────────────────┘
                                          QMI service broker  FUN_c1066310
                                            table uRamc1e044f0, 8-byte entries
                                            {u32 @+0, u16 service @+4, s8 announced @+6}
                                            count _DAT_c1e044f4
                                            header = (instance << 16) | service   ← 0x302 comes from the TABLE
                                          client lookup   func_0xc0b3d420(service, &client)   [175 callers]
                                          build message   func_0xc09026bc(buf, svc, (instance<<16)|msg_id)
                                          send            func_0xc09026c4(buf, 0x10)
```

- **Confidence: high** — `FUN_c1066310`, `0xc0b3d420`, `0xc09026bc`, `0xc09026c4` are decompiled and
  their calling conventions were read off the disassembly.
- **Unknown:** which module is the live IMSD client (the `0x23` sender), and why it stops at `0x23`.
  It is **not** findable by string, thread name, log-descriptor pointer, or `0x302` code immediate —
  all four routes are exhausted (§7).

**Image layout (verified against `modem.mdt` program headers):**

| Segment | File | VA base | Size |
|---|---|---|---|
| 15 | `modem.b15` | `0xc02c2000` | `0x010ec130` |

`modem.mdt` is a real ELF32, `e_machine = 0xA4` (164 = QDSP6), `e_phoff = 0x34`, 28 phdrs. Phdr `i`
(`i >= 2`, `p_filesz > 0`) maps to `modem.b{i:02d}` at `p_offset`. Reconstruct one flat ELF by writing
each b-file at its `p_offset` (total `0x31f2dac` ≈ 49 MB) — see §5.7.

## 4. Ground rules (non-negotiable)

1. **Never SSR a unit you cannot physically power-cycle.** A modem wedged by a failed cold boot
   (`PBL boot timed out`, `can't start rproc -110`, `smd-edge: remote side did not enter open state`)
   has **no in-band recovery** — `echo restart > /sys/kernel/debug/msm_subsys/modem`, `echo start`, and
   `qmicli` are all useless. Only an **AP reboot** brings it back.
2. **Keep `/root/modem_fw_backup/` intact** (`modem.b00/b01/b15/mdt`). Verify it before every flash.
3. **Always `recovery=disabled` before a risky modem restart.** `recovery=enabled` is required to
   *collect* a coredump but causes an SSR boot loop when the image crashes at boot.
4. **Never write `/sys/class/devcoredump/disabled`** — it is a class-level write-once global.
5. **Never trust a build you have not disassembled at true VAs** (§5.4). Silent out-of-range branch
   wrapping is a real failure mode that produces a booting-but-wrong image.
6. **Never attribute a result to a firmware change without hashing the loaded file** on the device
   (`md5sum /lib/firmware/modem.b15`), not the staged copy.
7. **Never edit a script while a build is running** (bash re-reads by byte offset).
8. **Commit to `test/*`, never to `main`.**

## 5. The procedure

### 5.1 Static analysis — get a real decompilation

REA **6.8.0** supports Hexagon (`machine 164`) and drives Ghidra properly. **The session MCP server is
pinned to 6.3.0 and will reject Hexagon** (`unsupported ELF architecture`) — use the CLI:

```bash
export PATH=/home/shaanair/.npm-global/bin:$PATH
export GHIDRA_INSTALL_DIR=/home/shaanair/Projects/Ghidra/ghidra_12.1.2_PUBLIC
rea doctor                # ghidra must be 'healthy'
rea analyze   /tmp/full_modem.elf
rea function  /tmp/full_modem.elf 0xc0b3d420
rea decompile /tmp/full_modem.elf 0xc0b3d420
```

`rea doctor` may flag `node 22.23.1 < 22.23.4`; that is a warning, the Ghidra provider still works.

Raw Ghidra headless (used before REA was available):

```bash
/home/shaanair/Projects/Ghidra/ghidra_12.1.2_PUBLIC/support/analyzeHeadless \
  /tmp/ghidra_imsd_proj imsd_proj -process modem.elf -noanalysis \
  -scriptPath /home/shaanair/ghidra_scripts -postScript DecompIms2.java -log /tmp/x.log
```

`-logName` is **not** a valid option — only `-log`.

### 5.2 Build a firmware cave

All work is in `modem.b15`. Assembler: `llvm-mc-23 -triple hexagon`; disassembler
`llvm-objdump-23 -d --triple hexagon`. **Absolute branch targets are rejected** — use a label or a
register jump.

**★★★ THE HEXAGON PACKET RULE — read this before writing any cave.**
Hexagon commits a packet's register writes only **after every instruction in the packet has read its
sources**. The function prologue at `0xc0b3d420` is one packet:

```
{ immext(#0xc0091080)
  r18 = ##-0x3ff6ef7c        ; r18 = GP (global pointer) = 0xc0091084
  memd(r29+#-0x18) = r19:18  ; saves the CALLER's r18
  allocframe(#0x20) }
```

The save and the GP load are in the **same packet on purpose**: the `memd` reads the *pre-packet* r18,
i.e. the caller's, while the packet loads this function's GP. **If you reproduce `r18 = ##gp` in a
separate packet, the function saves its own GP and restores it into the caller on `dealloc_return` —
the caller's GP is destroyed and an early-boot task asserts (`fws_task.c:558`).**

`llvm-mc` encodes the grouping in the parse bit: writing the instructions **inside one `{ }`** yields
`92 40 00 78` (parse `01`, not end of packet, matches stock); writing them as separate packets yields
`92 c0 00 78` (parse `11`, end of packet) and silently changes the grouping. **Copy the stock bytes or
keep them in one packet — never re-emit the prologue as separate instructions.**

**Space budget (measured):** there is **no nop run longer than 12 bytes** anywhere within ±8 MB of the
hook (3663 runs scanned, max 3 words), and exactly **one** 4-word zero run in that window
(`0xc0ed64cc`, +3.7 MB, probably data). A prologue-plus-return cave needs **16 bytes**, so the only
viable home is the **128-byte nop sled at `0xc1346b80`** — which is `0x809760` from the hook,
**outside the ±8 MB J-type range**. A register jump is therefore mandatory.

**Working hook (live-verified):**

```
0xc0b3d420: { r6 = ##0xc1346b80 }   ae 51 13 0c 06 c0 00 78
0xc0b3d428: { jumpr r6 }            00 c0 86 52

0xc1346b80: { immext(#0xc0091080)
              r18 = ##-0x3ff6ef7c
              memd(r29+#-0x18) = r19:18
              allocframe(#0x20) }   42 64 00 0c 92 40 00 78 40 1c ed eb
0xc1346b8c: { r6 = ##0xc0b3d42c }   50 4f 0b 0c 86 c5 00 78
0xc1346b94: { jumpr r6 }            00 c0 86 52
```

`r6` is caller-saved and is **not** an argument register for this function, so clobbering it at entry is
safe; `r0` (service) and `r1` (&client) are untouched. Resume at `0xc0b3d42c` — the instruction after
the prologue packet.

### 5.3 Re-sign the segment

`modem.b01` carries the per-segment SHA-256 table at `0x28 + seg*32`; segment 15 = `modem.b15`.

```python
b01[0x28 + 15*32 : 0x28 + 15*32 + 32] = sha256(b15).digest()
mdt = b00 + b01
```

MPSS auth is **not** a blocker for `b15` edits: a harmless one-byte flip in the nop sled, with the
table rebuilt, boots cleanly. (Contrast `modem.b19` — see the b19 auth trap in the modem-RE notes.)

### 5.4 Verify at true VAs — mandatory

Wrap the patched region in an ELF32 with `e_machine=0xA8` and the **real VA** as `sh_addr`, then
`llvm-objdump-23 -d --triple hexagon`. Confirm every branch target resolves where you intended.

> A non-final packet instruction disassembled **in isolation** prints `<unknown>`. That is normal — it
> means the instruction is a packet continuation, not that the encoding is wrong. Always disassemble in
> context.

### 5.5 Deploy

```bash
scp image/modem.b15 image/modem.b01 image/modem.mdt root@192.168.8.1:/tmp/
ssh root@192.168.8.1 '
  cp -f /tmp/modem.b15 /tmp/modem.b01 /tmp/modem.mdt /lib/firmware/
  md5sum /lib/firmware/modem.b15
  echo disabled > /sys/class/remoteproc/remoteproc0/recovery
  sync; reboot'
```

`/lib/firmware` lives on the **overlay** — it survives `sysupgrade` and is what the modem boots. Device
`/tmp` is tmpfs and is wiped by the reboot, so stage and install in the same pass.

### 5.6 Verify the deployment

```bash
ssh root@192.168.8.1 '
  uptime
  cat /sys/class/remoteproc/remoteproc0/state          # must be "running"
  md5sum /lib/firmware/modem.b15                       # must match the build
  dmesg | grep -iE "remoteproc0.*(up|fatal|crash)|fws_task"
  mmcli -L; mmcli -m 0 | grep -iE "state|access tech|registration|packet service"'
```

Healthy looks like: `state: running`, `remote processor 4080000.remoteproc is now up`, `bam_dmux:
received CMD_OPEN` on several channels, and `mmcli` reporting `connected` / `lte` / `registration: home`
/ `packet service state: attached`.

### 5.7 Reconstruct the full image for analysis

```python
# phdr i (i>=2, p_filesz>0) -> modem.b{i:02d} written at p_offset
# total size = max(p_offset + p_filesz) = 0x31f2dac
```

Produces `/tmp/full_modem.elf` — the only file to hand to REA/Ghidra.

### 5.8 Capture a coredump (when instrumenting)

`/sys/class/remoteproc/remoteproc0/coredump` is a **control knob** (`enabled\n`), not the data. The dump
appears as `/sys/class/devcoredump/devcdN/data`. `recovery=disabled` **suppresses dump collection
entirely** — to capture you must set `recovery=enabled`, accept the SSR loop, and copy the dump
**device-local** (never to host stdout — it silently truncates). Then set `recovery=disabled` again.

Parse the modem's own report with `scratch/diag_patch_v12/read_crash_report.py`; match the **filled**
form `QDSP6_PC : 0x<hex>`, never the format string.

### 5.9 Recover a wedged bench

```bash
ssh root@192.168.8.1 'sync; reboot'
```

Wait ~30 s, reconnect, confirm `state: running`. If the installed image is the one that crashed, **fix
or restore it first** or you will simply re-wedge on the next boot.

## 6. Acceptance gate (run after every change)

- [ ] modem `state: running`, no `fatal error received` in `dmesg`
- [ ] `md5sum /lib/firmware/modem.b15` matches the build you intended
- [ ] `mmcli -m 0` → `connected`, `lte`, `registration: home`, `packet service state: attached`
- [ ] data passes (`ping`/`curl` over `wwan0`)
- [ ] the ~900 s fatal does not appear (soak ≥ 20 min before declaring anything stable)

## 7. Failure ledger

Every entry is a real attempt on this branch. **Read this before repeating an idea.**

| # | Attempt | Symptom | Root cause | Lesson |
|---|---|---|---|---|
| F1 | Identify the IMSD client as `0xc0f4xxxx` | Analysis built on the wrong module | `0xc0f4xxxx` is the **Vdec (video decoder)** — `"VDC$"` thread, `Vdec_*Q` queues | Verify a module by its **thread/queue registration**, not by a `0x302` immediate. All `0xc0f4` conclusions retracted. |
| F2 | Find the IMS task by literal thread name | 33 `func_0xc0edaab0` sites, all media (`AVT`,`ADM`,`ASM`,`VDC$`,`VDP$`,`VEC$`,`VPM`,`VSM`,…) | The IMS task is **not** a literal-named thread | Thread names are a dead anchor here. |
| F3 | Find the IMS task by its F3 log descriptor `0xc1521ca0` | Zero code references in 203 MB of disassembly and in raw pointer scans | QShrink / hash / PC-relative logging | Log-descriptor→code anchoring is dead in this build. |
| F4 | Find the IMSD client by a `0x302` code immediate | 5 candidate sites, all unrelated (DIAG, LTE-ML1 coex, Vdec, a stride-`0x3a` table, dead call-state code) | Service 770 comes from the **broker's runtime table**, not an immediate | Stop hunting immediates; the service id is data. |
| F5 | Direct J-type `jump` from `0xc0b3d420` to the far sled | Image flashed, modem boot-crashed | The sled is `0x809760` away — **just past the ±8 MB J-type range**, so the 24-bit field **wrapped silently** (`jump 0xc0346b80`, `jump 0xc1b3d42c`) | **Always disassemble at true VAs.** The assembler does not warn about wraparound. |
| F6 | Register jump (`r5 = ##cave; jumpr r5`) with the prologue re-emitted as separate instructions | `fws_task.c:558:Assertion err == E_SUCCESS failed` at modem-uptime ~0; SSR loop | **GP corruption** (§5.2) — the function saved its own `r18` and restored it into the caller | The jump mechanism was innocent; the packet grouping was the bug. |
| F7 | "Passthrough" control (same register jump, no compare/trap) | Same `fws_task.c:558` crash → we wrongly concluded the *jump* was broken | Same GP bug. Worse, the first control run was **invalid** — the bench was still wedged from F6, so it crashed for the previous reason | **Always start from a healthy bench.** A control run on a wedged unit proves nothing. |
| F8 | Read `/sys/class/remoteproc/remoteproc0/coredump` for the dump | Got `65 6e 61 62 6c 65 64 0a` = `"enabled\n"` | That file is the **control knob**; data is `/sys/class/devcoredump/devcdN/data` | Know which sysfs file is the switch and which is the payload. |
| F9 | Set `recovery=disabled` and expect a dump | `crash detected` / `handling crash #1`, but no `devcdN` ever appears | `recovery=disabled` **suppresses collection** | `recovery=enabled` to capture; `disabled` to stop the loop. |
| F10 | Recover a wedged modem in-band | `smd-edge: remote side did not enter open state`; `echo restart`, `echo start`, `qmicli` all no-ops | The subsystem is down before any AP-side lever exists | **AP reboot only.** Hence ground rule #1. |
| F11 | `cp` a staged image after a reboot | `cp: can't stat '/tmp/p302pass/modem.b15'` | Device `/tmp` is tmpfs | Stage and install in the same pass. |
| F12 | `llvm-mc` with an absolute branch target | `error: invalid operand for instruction` | The assembler requires a label | Use a local label, or a register jump. |
| F13 | `analyzeHeadless … -logName x.log` | `InvalidInputException: Bad argument: -logName` | Only `-log` exists | — |
| F14 | REA MCP `open_binary` on the Hexagon ELF | `unsupported ELF architecture` | The session's MCP server is pinned to **`rea-agents@6.3.0`** (`npm exec rea-agents@6.3.0 mcp`), which predates Hexagon support; 6.8.0 is installed globally | Use the **CLI** (§5.1). |
| F15 | Diagnose the trap crash from the coredump | `PC = 0xc0809728`, `LR = 0xc0807f88`, all exception regs zero, ring all zeros | `PC` sits inside the register-dump routine; the real cause was the ASCII report's `fws_task.c:558` assert, and the trap never matched | Read the modem's own crash report; a zeroed trap ring means **your trap never ran**. |
| F16 | Put a 512-entry census ring at `0xc1460000` (4 KB) | Modem boot-crashed with `fws_task.c:558` on every boot | The ring spanned a **loaded data segment** (`0xc13c0000..0xc1461fe0`) and overwrote live data. Only `0xc1461000` (16 B) had been proven safe | Size the scratch area against the segment, not against "the last address that worked". |
| F17 | Create the `ims` bearer from 81voltd with `profile_idx_3gpp = 0` | `Couldn't load settings from profile index 0: QMI error (81) 'ExtendedInternal'` | `0` means "profile 0", which does not exist. `0xffff` means "no profile id, use the APN" | Pass `0xffff` unless you are deliberately selecting a profile. |
| F18 | Create the `ims` bearer immediately on `0x23` | Bearer creation failed: `registration in network failed: Network timeout` | `0x23` arrives at modem boot, **before** the modem is registered, and MM will not create a bearer then | Defer until after registration (90 s timer). |
| F19 | Activate the ISIM application with `--uim-change-provisioning-session … activate=yes` | `could not power off SIM: QMI protocol error (3): 'Internal'` | The modem refuses to power-cycle the SIM while it is in use | Needs a different route (see §10). |
| F20 | Trap `func_0xc0b3d420` (QMI client lookup) when `r0 == 0x302`, to catch the IMSD client taking its service-770 handle | Image deployed and loaded; the modem sent `0x23` on demand — **the trap never fired** (0 crashes, `remoteproc0/state` `running`) | The IMSD client does **not** obtain its 770 handle through this lookup (or the lookup is not exercised at all) | Do not re-run this trap. Identifying the client needs the **send** path, not the bind path. |
| F21 | Serve the modem's IMS over the AP data path: assign the announced `fe80::…` (and an MM-granted `10.186.246.167`) to `wwan1`, bring it up, hold it | **rx=0, tx=0 for the whole window**, IMSA still `not-registered` | The modem's IMS stack is **entirely internal** — it never sends a byte through the AP | Every AP-side "make the data path work" idea is dead. Do not retry. |
| F22 | Start the IMS PDN from the AP with a correctly flagged profile (`apn=ims`, `IMCN=1`, `PCSCF-via-PCO=1`) | `--wds-start-network=3gpp-profile=2` → `QMI protocol error (79) 'PolicyMismatch'` | The Jio MCFG's `/data/pdn_policy_db.txt` declares the `ims` PDN with **`Override_Type:API`** — it is reserved for the modem's internal PDN | The AP may not create the IMS PDN. Full stop. |
| F23 | Read the ISIM with `--uim-open-logical-channel` then `--uim-send-apdu` | `SimFileNotFound` for the card's own reported AID; the short AID `A0000000871004` opens channel 2, but the follow-up `--uim-send-apdu` returns `InvalidArgument`; with `--client-no-release-cid` the UIM endpoint hangs up | The logical channel dies when qmicli exits; qmicli refuses both actions in one run (`too many UIM actions requested`) | Reading EF_IMPI/EF_IMPU needs a **purpose-built tool** that opens the channel and sends the APDUs in one QMI session. |
| F24 | Reply to the modem's `0x23` with the documented "connection down" result (`err_code 13`) instead of success, expecting it to fall back to `0x20` Start Connection | Reply accepted; **no `0x20` within 60 s**, no further traffic | `0x23` is not a request that the modem retries differently on failure | Stop trying to steer the modem through the `0x23` reply. |
| F25 | Send the IMSD Connection-Changed indication carrying the **connection id from `0x23` (1)** and the live PDN address, with `wwan1` up and addressed | Modem still silent: `wwan1` rx=0 tx=0, IMSA `not-registered` | The modem does not use the AP data path at all, so no indication can make it talk | Confirms F21 from the signalling side. |
| F26 | Create a second `ims` bearer while one is already up | `OutOfCall` (QMI 15) from Start Network; MM reports `Bearer removed remotely` | The modem allows **one** IMS PDN; the old call must be fully torn down first | Reuse the existing bearer, never create a second one. |
| F27 | Capture modem DIAG logs to see the IMS subsystem's own decisions | `log-enable` + `cntl-enable /dev/rpmsg1` + `capture` → **269 messages / 118 LOG_F** (capture now works), but **0 MSG_F** and **0 IMS strings** | The default LOG_F mask carries no IMS logs; the F3 (MSG_F) path is still silent | LOG_F capture is available for other subsystems; the IMS task's logs need a different mask. |

| F28 | Read the P-CSCF from a live IMS PDN (`Start Network` on a bare `ims` profile, then `Get Current Settings`) | `PCSCF Address Using PCO = 0`, `PCSCF Server Address List` **empty**, `PCSCF Domain Name List` **empty** | The profile did not ask the network for a P-CSCF, so none was delivered | **This is the gate.** The firmware's own `HandleEmergencyRegister: No PCSCF address` matches exactly. |
| F29 | Create the `ims` WDS profile after the modem has booted and re-trigger the modem (81voltd rebind → `0x23`, then a full 2 min wait) | Profile present and correctly flagged, but IMSA stays `not-registered` and no data call appears on any mux channel | The modem runs its IMS PDP bring-up **once, at boot**, before the AP can create a profile; it does not retry afterwards | The profile must exist **at modem boot**, not after it. |
| F30 | Expect WDS profiles to survive a modem restart | After every modem restart (SSR or full boot) the table is back to profiles 1 and 6 with empty APNs — profiles 2/3 are gone | The modem re-provisions the WDS profile table from the carrier MCFG at boot, and the MCFG's `ims` entry is `Override_Type:API`, so no `ims` profile is materialised | Do not rely on a runtime-created profile; it lasts only until the next modem restart. |
| F31 | Dial with the number encoded as packed BCD (`0x01=0C91194908304297`, the format used for `ATD`/SIM records) | `Dial Call` returns **SUCCESS** with a call id, but the call never alerts: `AT+CLCC` sits in `DIALING` for ~21 s then `NO CARRIER`, and `Get All Call Info` is empty ~1 s later | The VOICE `Dial Call` number TLVs are **ASCII strings**, not BCD. A BCD blob is accepted as a (nonsense) calling number and no usable called party is set, so the call is torn down before it reaches the network | Always pass the digits as ASCII hex, e.g. `0x02=38343331323235313636` = `"8431225166"`. A SUCCESS result does **not** mean the call was placed — verify with `Get All Call Info`. |

## 8. Success record

| # | Result | Evidence |
|---|---|---|
| S1 | **Firmware patching + MPSS auth are sound.** A harmless one-byte flip in the nop sled, with `b01` re-hashed, boots normally | modem `running`, `remote processor is now up` |
| S2 | **The Hexagon packet rule was identified** — the cause of every `fws_task.c:558` failure | `fws_task.c:558` only ever appeared with a separate-packet GP load; stock groups them |
| S3 | **A GP-correct passthrough boots and the modem is fully healthy** — this proves the hook mechanism (`r6 = ##cave; jumpr r6`) is safe for `0xc0b3d420` | `state: running`; `mmcli` → `connected`, `lte`, `registration: home`, `packet service state: attached` |
| S4 | **The IMSD protocol direction was corrected**: the modem is the client, 81voltd is the server | `/tmp/81voltd-spec/qvd-server.c` |
| S5 | **The QMI broker was mapped**: `FUN_c1066310` (announce, table `uRamc1e044f0`, header `(instance<<16)|service`), `0xc0b3d420` (lookup), `0xc09026bc` (build), `0xc09026c4` (send) | decompiled, conventions read |
| S6 | **A complete analysis image can be reconstructed** from `modem.mdt` + `modem.bNN` at true VAs | `/tmp/full_modem.elf`, phdr 15 = `0xc02c2000` |
| S7 | **REA 6.8.0 drives Ghidra on Hexagon** (`machine 164`) | `rea doctor` → `ghidra healthy` |
| S8 | **`0xc0b3d420` is live but never carries service 770.** An unconditional trap fired at the first call and recorded `r0 = 7`, `r31 = 0xc0b3d50c`; the `0x302` trap ran a whole boot + registration + IMS bind without firing | trap coredump ring at `0xc1461000`: `1 / 0xc0b3d50c / 7 / 0xc009121c` |
| S9 | **The modem has no IMS APN profile.** `--wds-get-profile-list=3gpp` returned a single profile with `APN: ''` | created profile 2 (`apn=ims`) and it persisted |
| S10 | **An IMS PDN can be brought up on this network** and the network grants it IPv4 + IPv6 | `wwan1` `apn=ims` → `10.175.93.127` + `2409:4031:…` |
| S11 | **The full AP-side IMSD flow now works**: answer `0x23` → create the `ims` PDN → send `connection_changed_ind` (0x20) with the real address to the modem | 81voltd log `starting the "ims" PDN` → `Connection Address Changed … addr: 10.175.93.127` |
| S12 | **The modem reacts to it**: `UE to TAS service` goes from `unavailable` to **`available`** | `--imsa-get-ims-services-status` |
| S13 | **The IMSD client can be triggered on demand, with no SIM and no modem restart**: restarting `81voltd` makes the modem re-send `0x23` within ~10 s (it re-announces on every daemon bind) | `/etc/init.d/81voltd restart` → `QRTR data (42 bytes): 000100230023000120008b230100000019666538303a3a…` |
| S14 | **The `0x23` wire format is decoded.** Header = 7 bytes `{u8 type, u16 txn, u16 msg_id, u16 length}`; payload = one TLV `type 0x01`, value `{u16 0x238b, u32 1, u8 strlen, "<fe80::…>"}` | `00 0100 2300 2200 \| 01 1f00 \| 8b23 01000000 18 "fe80::254d:a15:1c6b:21fe"` ⇒ the modem announces **its own IMS interface up**, connection id 1 |
| S15 | **The carrier IMS configuration is correctly applied.** Every `/nv/item_files/ims/*` value in the Jio MCFG matches the modem's EFS byte-for-byte (`IMS_enable=1`, `ims_operation_mode=2`, `ims_hybrid_enable=1`, `qp_ims_reg_config`/`_db`/`_param_config` identical), and the voice-domain items are right: `voice_domain_pref=3` (IMS preferred), `ue_usage_setting=0` (voice-centric) | MCFG parser vs `diag_efs read` of each file |
| S16 | **The MCFG's rmnet mapping was recovered**: `/rmnet_config.txt` in the Jio MCFG maps **rmnet instance 1 → `3gpp_profile 3`**, and `/data/pdn_policy_db.txt` declares `PDN_APN_String:ims; Override_Type:API` | MCFG string/hex parse |

| S17 | **`81voltd` now decodes and logs the `0x23` payload, and the reply is selectable** (`IMSD_LINKUP_REPLY=up\|down`, `IMSD_LINKUP_DELAY=<s>` via uci) | log: `hdr 8b230100000018 unk 0x238b conn 1 addr_len 24 addr fe80::254d:a15:1c6b:21fe` |
| S18 | **The connection-changed indication now carries the modem's own connection id (1)** instead of 0, and reuses an already-open `ims` PDN | log: `QMI Connection Address Changed: local_id: 0, conn_id: 1, sub: 1, type: 1, addr: 10.145.232.182` |
| S19 | **The modem firmware contains its own IMS data path**: `ims_qmi_dcm_client.c:build_req_for_pdp_activate`, `PDPManager`, `RegisterManager` — it can activate the IMS PDP through its own DCM client, i.e. it does **not** need the AP for the data path | firmware strings + `ims_qmi_dcm_client.c` call sites |

| S20 | **The P-CSCF gate is identified and proven.** With `IMCN Flag = 1` **and** `PCSCF Address Using PCO = 1` on the `ims` profile, the same PDN returns a populated P-CSCF list; with the default profile it returns nothing | `PCSCF Server Address List (0x23) length = 9, value = 02:0C:8B:F0:38:30:82:38:0A` (translated `{955288332, 171475504}`) vs `length = 1, value = 00` |
| S21 | **WDS profile flags can be set from the AP**: `Modify Profile` (WDS `0x0028`) with TLV `0x01` = `{u8 type, u8 index}` (type **0** = 3GPP), `0x1F` = PCSCF-via-PCO, `0x22` = IMCN | `Result = SUCCESS`, read-back `IMCN Flag = 01`, `PCSCF Address Using PCO = 01` |
| S22 | **`Get Current Settings` is WDS `0x002D`** (not `0x002B`, which is Get Profile Settings), and `qmi-raw` can now chain it straight after `Start Network` using the returned packet-data handle | `QMI_THEN_MSG=0x002d QMI_HANDLE_TLV=0x01 qmi-raw … 0x0020 0x14=696d73 0x19=04 0x31=03` |
| S23 | **IMS REGISTERS — reproducibly.** Four clean runs (20:13, 20:33, 21:25, 21:51) end with IMSA `0x0021` TLV `0x10` = `02` = `QMI_IMSA_IMS_REGISTERED`, and `0x0020` TLV `0x10` = `01` (IMS services enabled). The whole bring-up is scripted on the unit as `/root/volte_flow.sh` | `IMSA reg=02:00:00:00` within 3–5 s of the `0x20`-triggered PDN; stable for ≥16 min in the 20:33 run |
| S24 | **A VoLTE call connects.** `VOICE 0x0020` Dial Call with TLV `0x01` (calling) + `0x02` (called) as ASCII strings produces a call whose type is `0x02` = `QMI_VOICE_CALL_TYPE_VOICE_IP` (**VoIP**), state `0x05` ALERTING → `0x03` CONVERSATION; the callee's phone rang and the call was answered | `Get All Call Info` (`0x002F`) TLV `0x10` = `02:01:05:02:01:04:00:00:02:06:…` (2 calls, first `id=1 state=5 type=2 dir=1`) then `01:01:03:02:01:04:00:00` (1 call, `state=3` CONVERSATION, `type=2` VOICE_IP) |
| S25 | **The exact working call command.** The client must stay allocated for the duration of the call, so `qmi-raw` gained `QMI_HOLD_SECS` / `QMI_NO_RELEASE` (r4) | `QMI_HOLD_SECS=14 qmi-raw /dev/wwan0qmi0 0x09 0x0020 0x01=38343331323235313636 0x02=38343331323235313636` |

### 8.1 The working end-to-end recipe (live-verified 2026-11-12)

```
# 1. modem restart + create the ims profile with IMCN + PCSCF-via-PCO within ~2 s
/root/earlyprof.sh                     # SSR, wait for /dev/wwan0qmi0, create+flag profile 2

# 2. make sure MM has a live 3GPP view (after an SSR it often does not)
mmcli -m 0 --disable; sleep 4; mmcli -m 0 --enable; sleep 12

# 3. trigger the modem's IMS stack: 81voltd rebinds -> 0x23 -> 0x20 -> PDN via MM
/etc/init.d/81voltd restart

# 4. wait for registration (3-5 s)
qmi-raw /dev/wwan0qmi0 0x21 0x0021        # TLV 0x10 must read 02

# 5. place a VoLTE call (client must stay alive for the call's lifetime)
QMI_HOLD_SECS=60 qmi-raw /dev/wwan0qmi0 0x09 0x0020 \
    0x01=38343331323235313636 0x02=38343331323235313636

# 6. confirm: type 0x02 = VOICE_IP, state 0x03 = CONVERSATION
qmi-raw /dev/wwan0qmi0 0x09 0x002f
```

`/root/volte_flow.sh` does steps 1–4 in one shot.

## 9. Open questions / next actions

0. **CLOSED — the `0xc0b3d420` trap.** Deployed, proven loaded (`b15` md5 `27086dd7…`, modem boot at AP
   17 s / SSR at 96 s, both after the file mtime) and proven live (S8). The IMSD client was then
   triggered on demand (S13) and **the trap did not fire** — `handling crash` stayed 0, `state: running`.
   ⇒ The client does not take its 770 handle from this lookup. See F20. **Do not re-run it.**
1. **Target the send path, not the bind path.** The next trap must catch the `0x23` *message*, not a
   client lookup. Best candidate is the header builder `0xc0b3d6a8(buf=r0, service=r1, msg_id=r2)`
   (reached via thunks `0xc09026bc` → `c0066a90` → `c0b3d6a8`): filter `zxth(r2) == 0x23`, record
   `{counter, r31, r0, r1, r2}`, `trap0(#0)`. `r31` is the live sender.
   **Scratch area is constrained:** only `0xc1461000` (16 B) is proven safe — a 512-entry ring at
   `0xc1460000` overwrote live data and boot-crashed (F16). Keep the record inside the sled or in 16 B.
2. Verify the builder hypothesis **before** flashing: disassemble `0xc0b3d6a8` and confirm which
   register carries the message id (`memw(r0+#0) = r2`, `memh(r0+#4) = r1` in this build), and check
   that the wire bytes of the observed `0x23` are consistent with that header layout. If they are not,
   the `0x23` sender is a different encoder — find it by the payload, not by the header.
3. Understand **why** the client stops after `0x23`. Candidates, none confirmed: IMS PDN already
   considered up; a subscription/profile gate; an ISIM-readiness race (`ISIM app detected` but not
   `ready` at the time of the send).
4. Re-run the 81voltd `0x23`-as-link-up experiment only if D1 shows the modem will never send `0x20` —
   and only with the §6 gate plus a physical power-cycle within reach.
5. **Bench blocker for all functional work:** the SIM is **absent** (`Card state: 'absent'`;
   `sim-hotplug: card still not visible after re-announce (3)`), so the modem is
   `not-registered-searching` with CS/PS `detached`. No VoLTE, IMSA or data test is possible until a
   Jio SIM is inserted. Offline work (S13 + traps) is unaffected.

## 9.1 What is left before this ships (2026-11-13)

The functional goal is met. These are the productisation gaps, in priority order.

1. **The `ims` profile must exist before the modem's IMS stack reads the table.** Today this needs an
   SSR plus a create within ~2 s (`/root/earlyprof.sh`). For a real device the profile has to be
   created at *every* modem boot — an init service that watches `/dev/wwan0qmi0` and creates+flags the
   profile immediately, before the IMS stack looks. Without this, a plain power-cycle lands back at
   `not-registered`.
2. **MM loses its 3GPP view after a modem restart.** `mmcli -m 0 --disable` / `--enable` restores it;
   without it MM's `simple connect` hangs forever at `state (6/10): register` and the data bearer never
   comes up. This needs a watchdog (the same class of fix as the existing `modem-bearer-watchdog`
   zombie nudge).
3. **The IMS PDN churns.** The modem asks for the `ims` PDN, 81voltd starts it, and ~2 s later the
   modem removes the bearer and asks again (every ~5–7 s). Registration and calls still work through
   it, so this is not fatal, but it is not correct either and it burns a bearer + a PDN activation per
   cycle. Worth understanding before shipping (the modem's own DCM path, S19, is the likely intended
   owner of this PDN).
4. **Coexistence with the default data bearer.** While the default `jionet` bearer comes up, the IMS
   bearer is torn down each time (the modem appears to allow one active PDN per client/technology).
   VoLTE calls worked with the default bearer down; this needs a proper multi-PDN test.
5. **D6 regression pass** on both bench units (boot, register, data, VoLTE call) once 1–4 are addressed.

## 10. Changelog

| Date | Change |
|---|---|
| 2026-11-12 | Opened. Captured goal, definition of done, system map, ground rules, procedure, 15 failures, 7 successes. Hook mechanism live-verified (S3). |
| 2026-11-12 | `0xc0b3d420` trap deployed and tested: **negative** (F20). Added S8 (liveness), S13 (on-demand `0x23` trigger), and repointed §9 at the send path. |
| 2026-11-13 | IMSD work: `81voltd` decodes `0x23`, reply is selectable (up/down) and the PDN delay is configurable; the indication now carries the modem's conn id. Tested `down` (no `0x20`), `up` with the address + `wwan1` up (still rx/tx 0) ⇒ F24–F26. DIAG LOG_F capture fixed (F27). Found the modem's own DCM PDP-activate path (S19). |
| 2026-11-13 | **Root-cause candidate found**: the `ims` PDN profile must set `IMCN=1` + `PCSCF-via-PCO=1`; without them the network delivers **no P-CSCF** and IMS can never register. Also found that WDS profiles are wiped at every modem boot, so the `ims` profile never exists when the modem's IMS stack runs. Added F28–F30, S20–S22. |
| 2026-11-13 | **VoLTE WORKS.** IMS registers reproducibly (S23) and a real VoLTE call connects end-to-end (S24/S25). Two blockers found and fixed along the way: MM loses its 3GPP view after an SSR (fix: `mmcli -m 0 --disable/--enable`), and the VOICE Dial Call number TLVs are ASCII strings, not BCD (F31). Added §8.1 with the complete recipe. |
