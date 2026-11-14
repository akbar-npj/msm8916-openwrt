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
| D7 | Call audio is audible in both directions | `arecord -D hw:0,0` non-silent on a live call; audio on `hw:0,1` heard by the far end | **PASS** (S26/S27) |

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
| F32 | Full prepare after `81voltd` gained `patches/` | `Error: OpenWrt package not found: package/system/81voltd` — the prepare died before the build started | `scripts/openwrt-prepare.sh:install_package_patches()` looked for the package under `package/system/<pkg>`, but `install_packages()` copies the project packages to `package/msm8916/<pkg>`. It had never been exercised because 81voltd was the first project package with a `patches/` directory | Fixed: resolve `package/msm8916/<pkg>` first, fall back to `package/system/<pkg>`. Any project package that adds `patches/` would have hit this. |
| F33 | Build with the new `KernelPackage/sound-msm8916-voice` | `WARNING: Makefile 'package/msm8916/ufi-voice/Makefile' has a dependency on 'kmod-sound-msm8916-voice', which does not exist` — and the image would have shipped **without** the kmod, silently | The kernel package scan cache `tmp/info/.packageinfo-kernel_linux` was from **2026-10-25**: `SCAN_DEPS` does not track `target/linux/*/modules.mk`, so the cache never invalidates when a KernelPackage is added (Doc 171) | `rm -f openwrt/tmp/info/.packageinfo-kernel_linux openwrt/tmp/.config-{package,target}.in openwrt/tmp/.config` before the build. `guard` cannot see this — it is a derived cache. Always `find openwrt/bin -iname "kmod-<name>*.apk"` afterwards. |
| F34 | `voicectl start host` (session defaults to `cs`) during a VoLTE call | Call in `conversation`, but the `pcm0` capture was **all zeros** (96000 samples, peak 0) | The voice session is a **DSP-level selector**, not just a label: on a `voice-ip` call the CS session carries nothing. `voicectl` sets the `Voice Session` mixer control to `CS` unless told otherwise | Always pass the session on a VoLTE call: `voicectl start host volte` (or leave it to `ufi-voiced`, whose `session=auto` picks `volte` while IMS is registered). |
| F35 | Raise `jitter_ms` to cure a choppy call | The call got **worse**, not better; `gaps` stayed and each one was longer | `voicebridge` set `buffering` on **any** short read and cleared it only at the full `jitter` target, so a 2-sample miss cost `jitter_ms` of silence (and every silence period was written to the PCM). The buffer size was not the problem — the refill policy was | Fixed in `voicebridge.c`: pad the short read and keep playing, only rebuffer when the FIFO is empty. See §8.3. Diagnostic: `jbuf <min>/<target> ms` in the stats line. |
| F36 | `apk add --allow-untrusted /tmp/ufi-voice-1.0-r11.apk` on the bench unit | `ERROR: unable to select packages: kernel-6.12.112~8efa871a…: breaks: world[kernel=6.12.112~0a9f7302…]` — nothing installed, `apk list -I` still showed r10 | The local build tree's kernel hash differs from the flashed image's, and resolving the package's kmod dependencies pulls `kernel` from the feed index | For bench iteration copy the built file straight from `build_dir/…/ufi-voice-1.0/.pkgdir/ufi-voice/` (verify with `md5sum`). A real release goes through `publish-package.yml` so the feed matches the image. |

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
| S26 | **VoLTE DOWNLINK audio works.** With `voicectl start host volte` and a live call, `pcm0` (MultiMedia1, `VOC_REC_DL`) captures the caller's voice | `/tmp/dl2.wav`: 96000 frames @ 8 kHz, peak 457, RMS 44.2, 12408 non-zero samples (the `cs` session gives peak 0 — F34) |
| S27 | **VoLTE UPLINK audio works.** Audio played on `pcm1` (MultiMedia2, `VOICE_PLAYBACK_TX`) is heard by the far end | A 6 s 1 kHz tone at −11 dBFS played 3× into `hw:0,1` during a `conversation` call; the user on the other phone reported "a long beep" |

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

### 8.2 Stage 1 — call audio (in progress, 2026-11-13)

A VoLTE call that connects but carries no audio is only half a phone. The audio path is a
**separate** problem from IMS registration, and it was already solved upstream: the
`isprogrammer/msm8916-openwrt` branch `voice_calls_new` adds a full QDSP6 voice stack
(5 commits, ~6,400 lines). It is now merged into this tree as patches **870** (kernel) and
**872** (mifib-v12 DTS), plus the `ufi-voice` package.

**Why it works without an ADSP partition.** The voice services (`q6mvm`, `q6cvs`, `q6cvp`,
`q6voice`) are reached over **APR, which rides the modem's existing `smd-edge`**. The modem
firmware we already run contains them. No extra partition, no extra firmware blob.

**What patch 870 adds**

| Layer | Addition |
|---|---|
| `sound/soc/qcom/qdsp6/` | `q6voice.c`, `q6voice-common.c`, `q6voice-dai.c`, `q6mvm.c`, `q6cvs.c`, `q6cvp.c` |
| `q6afe` | internal BT SCO ports `INT_BT_SCO_RX/TX` (136/137) and pseudo ports `VOICE_RECORD_RX/TX` (138/139), `VOICE_PLAYBACK_TX` (140) |
| `q6routing` | `VOC_REC_DL/UL` capture and `VOICE_PLAYBACK_TX` playback routes |
| `apq8016_sbc` | non-MI2S backends skip LPASS/codec setup and are fixed at 8 kHz mono S16_LE |
| DT | `q6mvm`/`q6cvs`/`q6cvp` service nodes in `msm8916.dtsi`; the card in `msm8916-ufi-voice.dtsi` |

**The card** is `qcom,msm8916-qdsp6-sndcard`, model `msm8916-voice`, with three PCMs:

| PCM | Use |
|---|---|
| `pcm0` MultiMedia1 | capture (`VOC_REC_DL` downlink / `VOC_REC_UL` uplink) or playback |
| `pcm1` MultiMedia2 | playback **into** the call (`VOICE_PLAYBACK_TX`), or capture |
| `pcm2` CS-Voice | hostless; open both directions to start the voice path |

**Package `ufi-voice`** (`packages/ufi-voice`, from the same branch)

| Binary | Role |
|---|---|
| `imsdcm` | AP-side IMS data service (QMI service 770 over **QRTR**) — the mature form of our 81voltd |
| `ufi-voiced` | call manager: polls MM every 0.5 s, drives BT and/or Asterisk |
| `hfp-ag` | HFP AG on BlueZ Profile1 (Bluetooth headset) |
| `voicectl` | `bt` = internal BT SCO, `host` = record tap on pcm0 + playback on pcm1 |
| `voicebridge` | AudioSocket bridge to Asterisk |
| `volte-setup` | modem EFS items |

`ufi-voice-asterisk` adds a PJSIP config (phones 100/101) and an AudioSocket bridge, i.e. the
box can be a small home PBX whose trunk is the SIM.

**Deliberately not enabled at install.** `imsdcm` and `ufi-voiced` are installed but not
`enable`d (no postinst). This build keeps the **working** 81voltd + `ims-profile` IMS path;
`imsdcm` is the planned replacement and must be evaluated before it takes over (it brings
several fixes we hit the hard way: bearer deleted at boot, SSR before MM exists, stale
local-id mapping, the `10-report-down` reconnect loop).

**LIVE-VERIFIED 2026-11-13** on the mifib-v12 (image flashed at AP uptime 0, kernel 6.12.112):

```
cat /proc/asound/cards        # 0 [msm8916voice]: msm8916-voice
ls /dev/snd                   # controlC0 pcmC0D0c/p pcmC1D0c/p pcmC2D0c/p timer
amixer -c 0 controls | wc -l  # 1066 (includes INT_BT_SCO_RX Voice Mixer CS-Voice)
```

The end-to-end audio test, both directions (S26/S27):

```
# 1. voice path on the VoLTE session (cs gives a SILENT downlink -- see F34)
voicectl start host volte        # status: running: mode=host session=volte card=0

# 2. place a call and keep the QMI client alive
QMI_HOLD_SECS=60 qmi-raw /dev/wwan0qmi0 0x09 0x0020 \
    0x01=38343331323235313636 0x02=38343331323235313636 &

# 3. downlink: the caller's voice on pcm0
arecord -D hw:0,0 -f S16_LE -r 8000 -c 1 -d 12 /tmp/dl.wav

# 4. uplink: audio played on pcm1 is heard on the far end
aplay -D hw:0,1 /tmp/tone.wav
```

**What is still manual:** `voicectl start` and the dial. `ufi-voiced` (installed and enabled,
mode `both`) is meant to do both automatically; that path has not been verified yet.

**A manual `qmi-raw` dial does NOT get the host bridge** (measured 2026-11-13). `voicebridge` is
spawned by `ufi-voiced` inside `voice_start()`, which only runs for a call the daemon knows about;
a call placed behind its back gets `voicectl`'s mixer routes but no bridge, so the SIP leg carries
nothing. MM logs it as `unexpected outgoing call to number '…' reported in call list`. To exercise
the bridge on a manual call, start it yourself once the call is in `conversation` — **and give it a
pipe on stdin**:

```sh
sleep 60 | voicebridge -D hw:0 -r 48000 -j 60 -v >/dev/null
#    ^ stdin MUST stay open. The loop does read(0) and treats a 0-byte read as
#      "the caller went away" and exits immediately -- run it from an ssh one-liner
#      and it dies before the first 5 s statistics, with rc 0 and no output.
#    stdout = the 8 kHz downlink to Asterisk (discard it), stderr = the statistics.
```

`-j 60` matches the unit's `ufivoiced.main.jitter_ms`; `-v` is what `uci bridge_debug=1` adds.
That is the same command `ufi-voiced` runs (`packages/ufi-voice/files/ufi-voiced`, ~line 434),
except that there stdout is a pipe into the AudioSocket leg.

**Measured on a live VoLTE call, 40 s, `-j 60 -v` (2026-11-13, S29):**

```
voicebridge: 5s cap 48155/s play 50073/s in 0/s out 8023/s jbuf 0/60 ms gaps 0 dropped 0 ms xruns 0/1 outdrop 0
voicebridge: 5s cap 47888/s play 48080/s in 0/s out 7981/s jbuf 0/60 ms gaps 0 dropped 0 ms xruns 0/0 outdrop 0
... 6 more identical windows ...
```

Reading it: `cap`/`play` at the full 48 kHz, `out` at the 8 kHz pipe rate, and **zero gaps, zero
dropped ms, zero xruns after the first window, zero outdrop** for 40 s. The DSP→host leg is clean at
jitter 60 — consistent with §8.3, where the choppiness was traced to the *phone* leg and the
rebuffer policy. `in 0/s` because nothing was feeding stdin: there was no SIP client registered, so
the uplink half of this measurement is still missing and needs a real softphone.

Two other observations from the same runs: the call **survives the QMI client exiting** — it was still
`conversation` 9 s after a 45 s hold and again after a 95 s hold, so `QMI_HOLD_SECS` is needed for
call *events*, not to keep the call up. And hanging up is awkward: `End Call` (`0x0021`, TLV
`0x01=<call id>`) answers `FAILURE: InvalidQosId`; `mmcli --voice-hangup` does not exist (the option
is `--voice-hangup-all`, plus `--voice-delete-call`); `mmcli -m any --voice-hangup-all` returns
`QMI protocol error (90): 'IncompatibleState'` **and yet the call list is empty 3 s later**, so it
does end the call despite the error. Always confirm with `mmcli -m any --voice-list-calls` — do not
trust either the error or the success.

### 8.3 Audio quality — the jitter buffer (2026-11-13)

The first live call was intelligible but **choppy and jittery**. The bridge statistics localised it
to the phone leg, not the modem:

```
60s cap 47997/s play 47997/s in 7970/s out 8000/s jbuf 60 ms gaps 20  dropped 1030 ms
60s cap 47992/s play 48208/s in 8004/s out 7998/s jbuf 60 ms gaps 62  dropped 2430 ms
```

* `cap`/`play` ≈ 48 000/s and `xruns 0/0` ⇒ the DSP side (the modem's own voice path) is perfect and
  continuous. Nothing here is a modem, DSP or mixer problem.
* `in 7970/s out 8000/s` with `gaps` and `dropped` ⇒ the 8 kHz uplink from Asterisk arrives in
  **bursts**, and the bridge was mangling them.

**The defect (F35).** `voicebridge` set its `buffering` flag on *any* short read, and cleared it only
when the FIFO had refilled to the full `jitter` target. A shortfall of two samples therefore cost
`jitter_ms` of silence, and every 10 ms period of that silence was written to the PCM. With
`jitter_ms=60` a 2-sample miss produced 60 ms of silence; raising the buffer to 150 ms (the obvious
"more buffer" reaction) made each hiccup **2.5× longer**. That is the choppiness.

**The fix.** Pad a short read and keep playing; only stop when the FIFO is genuinely empty:

```c
if (got < want) {
        memset(pipebuf + got, 0, (want - got) * 2);
        /* A short read is one lost period, not a stall: pad it and keep
         * playing. Rebuffering to the whole jitter target for a couple of
         * missing samples turned every hiccup into jitter_ms of silence. */
        if (got == 0 && !buffering) {
                st.gaps++;
                buffering = 1;
        }
}
```

The statistics line now prints `jbuf <min>/<target> ms`, where `<min>` is the lowest FIFO occupancy
since the previous print — the instantaneous value it printed before said nothing about starvation.

**The network side.** The softphone talks to Asterisk over the unit's own AP, and the AP was
advertising `DTIM period 2`, so a power-saving client is only woken every ~205 ms; that is exactly
the burst length that was overflowing the buffer. Set `dtim_period=1` (persisted in
`wireless.default_radio0.dtim_period`, applied by `wifi reload`) ⇒ the AP delivers buffered unicast
at every beacon (~102 ms) and `uapsd_advertisement_enabled=1` lets a U-APSD client pull frames
itself. The station dump is clean: `tx retries: 0`, `tx failed: 0`, −42 dBm — this was never a
lossy link, only a badly paced one.

**Tuning.** `ufivoice.main.jitter_ms` (default 40, bench value 150) sets the target occupancy and
therefore the added one-way delay; `ufivoice.main.bridge_debug=1` prints the line above every 5 s
instead of every 60 s. Force the softphone to **G.711 µ-law/A-law, ptime 20 ms, no Opus**: an Opus
48 kHz leg makes Asterisk transcode 48k→8k for AudioSocket and adds its own jitter buffer in series
with ours.

**Verified after the fix, over the real SIP path (S30, 2026-11-13).** Two calls originated from the
softphone (`100` at 192.168.8.247): SIP → dialplan → `ufi-voice-dial` → ModemManager → modem, with
`ufi-voiced` spawning the bridge itself. This is the *automated* path, not the hand-started one, and
the callee reported the audio **clear**:

```
18:43:45  5s cap 47901/s play 50296/s in 8015/s out 7981/s jbuf  0/60 ms gaps 1 dropped 0 ms xruns 0/1 outdrop 0
18:43:50  5s cap 48101/s play 47910/s in 7985/s out 8017/s jbuf 10/60 ms gaps 0 dropped 0 ms xruns 0/0 outdrop 0
...  second call, 66 s ...
18:44:56  5s cap 48113/s play 50222/s in 8019/s out 8016/s jbuf  0/60 ms gaps 3 dropped 0 ms xruns 0/1 outdrop 0
18:45:01  5s cap 47989/s play 47989/s in 7998/s out 7998/s jbuf 50/60 ms gaps 0 dropped 0 ms xruns 0/0 outdrop 0
18:45:06 .. 18:45:57    eleven more windows, all "gaps 0 dropped 0 ms xruns 0/0 outdrop 0"
```

Both directions at the full 8 kHz, **`dropped 0 ms` and `outdrop 0` for the entire call**, and zero
gaps in every window except the first. The first window of *each* call shows `jbuf 0/60` and a few
gaps plus one playback xrun — that is the jitter buffer starting empty and filling up, not a fault;
it is the only silence left. (If that initial ~50 ms is ever audible, pre-fill the FIFO before the
first playback write.) Note `jbuf <min>` is not a starvation metric on its own: the playback loop
drains every period the device has room for, so it routinely dips to 10 ms while nothing is lost —
`gaps` is the number that matters.

### 8.4 The phone page — `luci-app-ufi-voice` (2026-11-13)

Until now the only way to change a SIP password was to edit `/etc/asterisk/pjsip.conf` over SSH, and
the password existed nowhere else. The old `luci-app-asterisk` is gone from the feed (105 apps in
`feeds/luci/applications`, none of them Asterisk) and it was a generic Asterisk editor whose dialplan
writes would break `AudioSocket()` — the one line that puts a call on the modem. So the page is
purpose-built instead: `Services → Phone`.

**One source of truth.** The SIP accounts moved from the generated `pjsip.conf` into uci:

```
config phone 'p1'
	option number '100'
	option password '1234567890'
```

`/usr/sbin/ufi-voice-asterisk-apply` generates `pjsip.conf`, `extensions.conf`, `manager.conf` and
`ufi-voice-phones.txt` from those sections. It is idempotent, it takes over the passwords of an older
install from the existing `pjsip.conf` the first time it runs, and it generates a random password for
any account that has none. **Never edit the generated files** — the next apply overwrites them.

**Applying.** The JS form API has no `on_after_commit` hook (`m.on_after_commit` exists only in
Lua `luci-compat`), so the page overrides `handleSaveApply`: save → `uci.apply()` → wait 2 s →
`uci.callConfirm()` → `/usr/sbin/ufi-voice-apply` → reload. The wait and the confirm are not
decoration: `uci apply` is a *checked* apply that rolls the whole change back unless it is confirmed
within a few seconds, and a page reload cancels the internal confirm timer. Skipping them silently
reverts the user's change.

`ufi-voice-apply` regenerates the Asterisk config and reloads it. It does **not** restart
`ufi-voiced`, because that daemon reads uci on every call (`uci()` shells out to `uci get`), so
`mode`, `session`, `jitter_ms`, `sip_ring` and the rest are live already — a save therefore does not
drop a call in progress. Only `manage_answer` needs a restart (`ufi-voice-apply -r`).

**Do not use `procd_add_reload_trigger` here.** It was tried and produced an empty `triggers` array
on this build (`ubus call service list '{"name":"ufi-voiced","verbose":true}'`), so `uci commit`
never restarted the service; the explicit exec above replaced it. A plain `uci commit ufivoice` over
SSH also does not fire a config-change event (`/sbin/uci` is the C binary, not the shell wrapper) —
run `/usr/sbin/ufi-voice-apply` afterwards.

**Status is read-only and bounded.** `/usr/sbin/ufi-voice-status` prints one JSON object: service
states, modem state/operator/signal/RAT, IMS registration, active call count, and each SIP account
with its Asterisk contact state and source address. Every probe has its own `timeout`, so a dead
modem or a stopped Asterisk cannot hang the page; the whole script runs in ~0.4 s.

```sh
/usr/sbin/ufi-voice-status
{
  "ufi_voiced": "running", "imsdcm": "running", "asterisk": "running",
  "hfp_ag": "running", "voicebridge": "stopped",
  "modem_state": "registered", "modem_operator": "IND-JIO", "modem_signal": 63,
  "modem_tech": "lte", "ims": "registered", "calls": 0,
  "sip": [{"number":"100","status":"Avail","host":"192.168.8.5"}]
}
```

`mmcli` prints `--` for anything it does not know; the script maps that to empty so the page does not
render `— --`.

**Verified on the bench** (2026-11-13): add a `config phone` section → apply → `pjsip.conf` gains
`[102]`, `pjsip show endpoints` lists it, `ufi-voice-phones.txt` is rewritten, `ufi-voice-status`
reports it; delete it → apply → the endpoint is gone. The migration path was exercised too: the two
accounts whose password had been set by hand were taken over into uci unchanged.

### 8.5 Installing the package on a bench unit — `apk` cannot install anything on this one

`apk add /tmp/ufi-voice-1.0-r12.apk` on the soak unit fails with a ~100-line wall that has nothing to
do with the package:

```
ERROR: unable to select packages:
  kernel-6.12.112~8efa871abf779b5591d7c27483980dfd-r1:
    breaks: world[kernel=6.12.112~0a9f73027075980ab99fd752c36da786-r1]
            kmod-bam-dmux-6.12.112-r1[kernel=6.12.112~0a9f73027075980ab99fd752c36da786-r1]
            ... every kmod ...
```

Diagnosis (2026-11-13): the **installed** kernel is `8efa…` but `/etc/apk/world` still pins `0a9f…`
and every installed `kmod-*` records a dependency on `0a9f…`. `apk` can find no solution at all, so
**no package can be installed on this unit**, ours or otherwise. Moving `/etc/apk/repositories.d`
aside and adding `--no-network` does not help — the conflict is in the installed database, not in the
feed.

`/etc/apk/world` lives on the overlay, so a config-preserving `sysupgrade` carries the *old* kernel pin
into the new image. Correcting the pin alone (`sed -i 's|^kernel=.*|kernel=<installed>|' /etc/apk/world`)
is not enough while the kmod records still name the old hash; the world file was corrected anyway,
because it now at least matches reality.

What we did instead — install exactly the files the package would have written, from the build's
`.pkgdir` (the same method used earlier in this project when `apk` refused a local apk):

```sh
cd openwrt
rm -rf /tmp/ufistage && mkdir -p /tmp/ufistage
cp -a build_dir/target-aarch64_generic_musl/ufi-voice-1.0/.pkgdir/ufi-voice/. /tmp/ufistage/
cp -a build_dir/target-aarch64_generic_musl/ufi-voice-1.0/.pkgdir/ufi-voice-asterisk/. /tmp/ufistage/
cp -a build_dir/target-aarch64_generic_musl/luci-app-ufi-voice/.pkgdir/luci-app-ufi-voice/. /tmp/ufistage/
rm -rf /tmp/ufistage/etc/uci-defaults /tmp/ufistage/etc/config   # keep the unit's own config
tar -C /tmp/ufistage -cf - . | ssh root@192.168.8.1 'tar -C / -xf -'
```

then `/etc/init.d/rpcd restart` (new ACL file) and `/etc/init.d/ufi-voiced restart` (new
`voicebridge`). Verify with `md5sum` against `.pkgdir` — a silent partial copy is indistinguishable
from success otherwise.

The apk database still records `ufi-voice-1.0-r10` even though r12's files are in place. That is
cosmetic for testing; a reflash of the image built from this branch restores the bookkeeping.

**Durable fix, not done yet:** reconcile `/etc/apk/world` (and the kmod dependency records) at first
boot after an image change, next to `apk-overlay-heal`. Until then a freshly sysupgraded unit can
install nothing from the feed.

### 8.6 The `ims` profile at every modem boot (2026-11-13)

Three triggers, in the order they fire, all ending in the same idempotent script:

| Trigger | File | Fires | Covers |
|---|---|---|---|
| `wwan` hotplug | `/etc/hotplug.d/wwan/20-ims-profile` | the moment `wwan0qmi0` is created | the real "at modem boot" — the earliest point the AP can speak QMI |
| init S94 | `/etc/init.d/ims-profile` | late boot, once the device exists | fallback when no hotplug event arrives |
| init S97 | `/etc/init.d/ims-profile-watch` | a *later* remoteproc transition | runtime restarts (crash recovery, Stage 3 SSR, manual stop/start) |

`ims-profile-setup` is the whole mechanism: if a profile with `APN: 'ims'` exists it does nothing;
otherwise it creates one (`--wds-create-profile=3gpp,apn=ims,pdp-type=IPV4V6`) and sets `IMCN=1` +
`PCSCF-via-PCO=1` on the **index it was given** with WDS `0x0028` (TLV `0x01` = `{type 0, index}`,
`0x22` = IMCN, `0x1f` = P-CSCF-via-PCO).

The watcher adds what the other two cannot. After a runtime restart it also resets ModemManager's
3GPP view (`--disable`, 4 s, `--enable`, 12 s) and restarts `imsdcm` — without those the bearer
request fails even with the profile present (§9.1 item 2). It deliberately ignores the **first**
transition it sees, because at AP boot S94/S96 already own that one; only a later transition is a
genuine restart. It never restarts the modem itself.

**Trap: `sleep` takes no fractions on this busybox.** `sleep 0.5` → `sleep: invalid number '0.5'`.
Both scripts use `usleep`. The original `ims-profile-setup` used `sleep 0.1` in its two wait loops,
so the "60 s" device wait was really a few-millisecond spin that would give up instantly if the
modem was not up yet; that is fixed.

**Correction to F30 (measured 2026-11-13).** F30 recorded the WDS table being re-provisioned at
every modem boot, "back to profiles 1 and 6". That is not what this unit does now: profiles 7 and 8
(`APN: 'ims'`, `IMCN=1`, `PCSCF-via-PCO=1`, read back with WDS `0x002b`) **survive a
`--wds-delete-profile` plus a full AP reboot** — the modem restores them from NV. So the table is
persistent, and the create path only has to run on a genuinely blank table (first boot after
flashing, or after an NV reset). The triggers are still required: they are what makes that first
boot work, and the flags are what makes the network deliver a P-CSCF.

Verified on the bench (S31): profiles 7 and 8 deleted (table 1/6 only), unit rebooted — the table
came back as 1/6/7/8 with the flags set, all three triggers logged `ims profile already present,
nothing to do`, and `--imsa-get-ims-registration-status` reported `registered`. A separate
fake-state run of the watcher (offline → running twice) showed the first transition ignored and the
second one running the full recovery: profile check, MM disable/enable (17 s), `imsdcm` restart.
Note the recovery is not instant: IMS came back ~90 s after an `imsdcm` restart.

Both previously unpackaged pieces (`ims-profile-setup`, `ims-profile`) and the hotplug handler are
now in `packages/ufi-voice` (r13) — before that they existed only on the bench unit's overlay and a
reflash would have lost them.

### 8.7 The audio DSP hangs at call teardown — dead air, both directions (2026-11-14)

**Symptom.** Calls connect and behave normally — ringing, answer, `conversation`, hangup — but there
is **no audio in either direction**, and it starts suddenly on a unit that has been working for
hours. Nothing in the configuration changed: the same call an hour earlier had clean audio.

**Diagnosis: the audio DSP stopped answering, the AP side is fine.** `dmesg` holds the whole story.
The first failure of the boot is a **close**, not an open:

```
[24209.818983] qcom-q6afe aprsvc:service:4:4: AFE close failed -110
[24209.819243] q6afe-dai ...: fail to close AFE port (-110)
[24214.858918] q6asm-dai ...: CMD 10bcd timeout
[24219.979355] qcom-q6asm aprsvc:service:4:7: CMD 10d94 timeout
[24220.992407] qcom-q6adm aprsvc:service:4:8: ADM copp cmd timedout
```

and from then on every call repeats the same three ports, all `-110` (`ETIMEDOUT`):

```
qcom-q6afe ...: AFE enable for port 0x3000 failed -110     # INT_BT_SCO_RX
qcom-q6afe ...: AFE enable for port 0x8003 failed -110     # VOICE_RECORD_RX
q6afe-dai ...: ASoC: error at snd_soc_pcm_dai_prepare on VOICE_RECORD_RX: -110
voicebridge: hw:0,0 capture: hw params: Operation timed out
voicebridge exited 1
```

`-110` is a command timeout at the APR layer: AFE (service 4), ASM (7) and ADM (8) never reply, so
`voicebridge` cannot open the capture PCM and exits, and `ufi-voiced` stops the path. The modem,
IMS, the SIP leg and ModemManager are all healthy throughout — only the DSP is gone.

**Where that DSP lives.** On this port there is **no `adsp.mdt` and no ADSP remoteproc**;
`ls /lib/firmware | grep adsp` is empty and the only remoteprocs are `4080000.remoteproc` (mba →
mpss) and `a204000.remoteproc` (wcnss). The APR services are children of the **modem's** SMD edge:

```
$ ls /sys/bus/platform/devices | grep apr
4080000.remoteproc:smd-edge:apr:service@4:dais    # AFE
4080000.remoteproc:smd-edge:apr:service@7:dais    # ASM
4080000.remoteproc:smd-edge:apr:service@8:routing # ADM
```

So call audio shares the modem's Q6, and anything that takes that core down takes audio with it — the
AP gets no notification and nothing re-probes the services.

**Trigger and fix.** The hang began at an **AFE close**, i.e. at call teardown, which points at
`voicectl stop`. `stop_hostless` sent SIGTERM and returned immediately, and `do_stop` then cleared
the mixer routes at once. But `hostless` polls its stop flag **once a second**
(`while (!stop) { sleep(1); ... }`, `hostless.c:112`), so its PCMs were still open when the routes
went away — the AFE port is torn down underneath a live PCM, and the DSP never recovers from that.
`do_start` had the same race, since it also begins with `stop_hostless`.

Fixed in `ufi-voice` r14: `stop_hostless` now waits (up to 2 s, `usleep 50000` steps) for the pid to
disappear before returning, so the mixer is only touched once the PCMs are really closed.

**Recovery when it has already happened: an AP reboot.** Nothing on the AP can re-open the services —
they are on the modem's firmware — and a modem SSR was not attempted, per the standing rule that a
failed modem cold boot leaves the unit with no in-band recovery. A reboot restored audio
immediately (S33: `voicectl start host volte` + `stop` afterwards, zero `-110`).

**Detecting it.** `dmesg | grep "failed -110"` is decisive; the count is 0 on a healthy boot. On the
LuCI side the same failure shows up as `voicebridge: stopped` with a call active.

### 8.8 A dead `voicebridge` left the rest of the call silent (2026-11-14)

**Symptom.** Audio works for the first few seconds of a call and then disappears — **both**
directions — for the rest of that call. The next call is fine again.

**Cause.** `ufi-voiced` never restarted a bridge that died mid-call. `Bridge.reader()` notices the
exit, logs `voicebridge exited <rc>` and calls `bridge_died()`, which cleared `self.bridge.leg` and
called `reroute()`. But `reroute()` only starts the bridge *together with the voice path*:

```python
if len(active) == 1 and not self.voice_on:      # <- voice_on is already True
    self.voice_start()                          #    so this never runs
```

So the daemon re-attached the leg to a bridge that no longer existed and logged
`phone audio: /Call/N` as if nothing had happened. Observed on the bench at 03:44:58 → 03:45:00 →
03:46:04: bridge started, exited, and the remaining 64 s of the call were silent.

**Reproducing it deliberately:** `kill $(pidof voicebridge)` during an active call. `voicebridge`
handles SIGTERM by setting its stop flag, so it exits **0** — which is why the log line is
`voicebridge exited 0` and not a signal. Anything that ends the bridge mid-call has the same effect:
a capture error storm (`cap_fail > 100`), the DSP voice session going away, or the card being torn
down.

**Fix (r15).** `bridge_died()` now restarts the bridge when the voice path is still up and exactly one
call is active, then re-routes the leg:

```python
if self.voice_on and len(active) == 1 and not self.bridge.running():
    log('voicebridge died during a call, restarting it')
    self.bridge.start()
    self.reroute()
```

The existing 10 s `bridge_restart` gate stays, so a DSP that keeps failing cannot spin: at most one
attempt per 10 s.

**Do not kill the bridge to free the capture device during a call.** That is how this was found — a
spectrum capture needs `hw:0,0` exclusively, and taking it away from a live call costs the caller
their audio. Capture on a call that is being set up, or accept the call is over.

### 8.9 Is the call wideband? Measured — no (2026-11-14)

The question behind "should we add G.722 / AMR-WB": how much bandwidth does the downlink actually
carry? Measured on a live call (`state = 'conversation' type = 'voice-ip' mode = 'lte'`), capturing
`hw:0,0` directly at 48 kHz for 12 s and taking a Welch spectrum of the loudest 2 s:

```
  3.50 kHz  -67 dB
  3.75 kHz  -79
  4.00 kHz  -94
  4.25 kHz  -96
  4.50 kHz  -131   <- noise floor
  5.00 kHz  -142
  6.00 kHz  -145
  7.00 kHz  -144
```

That is a textbook 8 kHz anti-alias wall: speech to ~3.9 kHz, nothing above 4.25 kHz. The call is
**narrowband (AMR-NB)**, so there is no 4–7 kHz content for G.722 to carry.

Consequences:

- Adding `codec_g722` alone would be a **downgrade**: Asterisk would decode G.722 to 16 kHz and then
  transcode to 8 kHz `slin` for the AudioSocket app (which hardcodes `ast_format_slin`), adding a
  second jitter buffer and CPU for identical audio.
- A real wideband path needs all four pieces at once: `asterisk-chan-audiosocket` (only the *channel
  driver* takes a codec option, `c(slin16)`; the dialplan app is fixed at 8 kHz), `codec_g722`,
  `voicebridge` with a configurable pipe rate (`PIPE_RATE` is `#define`d to 8000), and the dialplan
  switched from `AudioSocket()` to `Dial(AudioSocket/...)`.
- None of that is worth building until a call actually carries wideband. Whether it does depends on
  the network and the far end — retest with a call to a known VoLTE handset before starting.

## 9 Open questions — the trap line and the bench

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
5. ~~**Bench blocker for all functional work:** the SIM is **absent**~~ — **CLEARED 2026-11-13 17:30.**
   The card is back in the unit and the modem came up on its own: `state: connected`, `access tech:
   lte`, `signal quality: 100%`, `packet service state: attached`, operator `IND-JIO`, SIM `active`,
   and `--imsa-get-ims-registration-status` → `Status: 'registered'`, `Technology: 'wwan'`. No SSR was
   needed. (Whether the earlier `sim_hotplug` pulse or a physical reseat did it is not known — the
   card was simply present when the unit was next inspected.) Functional VoLTE work is unblocked; a
   call can be placed on request.

## 9.1 What is left before this ships (2026-11-13)

The functional goal is met. These are the productisation gaps, in priority order.

1. ~~**The `ims` profile must exist before the modem's IMS stack reads the table.**~~ — **DONE
   2026-11-13**, see §8.6. A `wwan` hotplug handler creates it the moment `wwan0qmi0` appears, with
   `ims-profile` (S94) as the boot fallback and `ims-profile-watch` (S97) for runtime modem restarts.
   All three are packaged in `ufi-voice` r13; before that they lived only on the bench unit.
2. **MM loses its 3GPP view after a modem restart.** `mmcli -m 0 --disable` / `--enable` restores it;
   without it MM's `simple connect` hangs forever at `state (6/10): register` and the data bearer never
   comes up. This needs a watchdog (the same class of fix as the existing `modem-bearer-watchdog`
   zombie nudge).
3. ~~**The IMS PDN churns.**~~ — **NOT REPRODUCIBLE with `imsdcm` (measured 2026-11-13).** A 180 s
   capture of `start_connection` / `stop_connection` / `create-bearer` on a settled unit recorded
   **0 events**, and `imsdcm` held the same pid throughout a call. The §9.1 note was written against
   `81voltd`; the daemon that replaced it does not exhibit it. The one burst seen all day
   (20:25:24–20:25:50) was caused by restarting `imsdcm` **by hand while the default bearer was
   being re-established**: the IMS bearer took `wwan0`, collided with `jionet`, the modem sent
   `stop_connection`, and the two settled onto `wwan0`/`wwan1` about 25 s later. It is a transition
   artifact, not a loop, and the startup path avoids it by design (`wait_wan` waits for the data
   bearer, then 10 s, before creating anything).
4. ~~**Coexistence with the default data bearer.**~~ — **WORKS (measured 2026-11-13, S32).** With
   `jionet` connected on `wwan0` and `ims` on `wwan1`, both `connected: yes` simultaneously, a VoLTE
   call went `dialing → ringing-out → active`, the callee answered, both PDNs stayed connected for
   the whole call, IMS stayed `registered`, and no `stop_connection` arrived during it. `multiplexed:
   no` on both bearers — the two PDNs sit on separate `bam-dmux` channels, which is why this works.
   The earlier "one PDN per client" reading came from the 81voltd era and is wrong.
5. **D6 regression pass** on both bench units (boot, register, data, VoLTE call) once 1–4 are addressed.

## 10. Changelog

| Date | Change |
|---|---|
| 2026-11-12 | Opened. Captured goal, definition of done, system map, ground rules, procedure, 15 failures, 7 successes. Hook mechanism live-verified (S3). |
| 2026-11-12 | `0xc0b3d420` trap deployed and tested: **negative** (F20). Added S8 (liveness), S13 (on-demand `0x23` trigger), and repointed §9 at the send path. |
| 2026-11-13 | IMSD work: `81voltd` decodes `0x23`, reply is selectable (up/down) and the PDN delay is configurable; the indication now carries the modem's conn id. Tested `down` (no `0x20`), `up` with the address + `wwan1` up (still rx/tx 0) ⇒ F24–F26. DIAG LOG_F capture fixed (F27). Found the modem's own DCM PDP-activate path (S19). |
| 2026-11-13 | **Root-cause candidate found**: the `ims` PDN profile must set `IMCN=1` + `PCSCF-via-PCO=1`; without them the network delivers **no P-CSCF** and IMS can never register. Also found that WDS profiles are wiped at every modem boot, so the `ims` profile never exists when the modem's IMS stack runs. Added F28–F30, S20–S22. |
| 2026-11-13 | **VoLTE WORKS.** IMS registers reproducibly (S23) and a real VoLTE call connects end-to-end (S24/S25). Two blockers found and fixed along the way: MM loses its 3GPP view after an SSR (fix: `mmcli -m 0 --disable/--enable`), and the VOICE Dial Call number TLVs are ASCII strings, not BCD (F31). Added §8.1 with the complete recipe. |
| 2026-11-13 | **Call audio (stage 1) wired in.** Merged the upstream `voice_calls_new` stack: patches 870 (q6voice/q6mvm/q6cvs/q6cvp, BT SCO + record/playback ports, non-MI2S card), 871 (bam-dmux pc_irq re-check — an A2 assertion seen *during a voice call* on UFI003), 872 (mifib-v12 DTS hook), the `ufi-voice` + `ufi-voice-asterisk` packages, and `DEVICE_PACKAGES` for mifib-v12. No ADSP partition needed: APR rides the modem's `smd-edge`. See §8.2. |
| 2026-11-13 | **VoLTE CALL AUDIO WORKS, both directions** (S26/S27). Flashed the voice image to the mifib-v12; `/proc/asound/cards` shows `msm8916voice` with all 1066 mixer controls. With `voicectl start host volte` a live VoLTE call carries the caller's voice into `pcm0` and plays `pcm1` back to the far end. F34: the `cs` session gives a silent downlink on a `voice-ip` call. |
| 2026-11-13 | **Audio quality pass (§8.3).** The bridge stats proved the DSP leg perfect and the phone leg bursty. Root cause of the choppiness was the jitter buffer's refill policy (F35) — fixed; `ufi-voice` r11. AP `dtim_period` 2 → 1. F36: a locally built `.apk` cannot be `apk add`ed on a bench unit whose kernel hash differs — copy the file. |
| 2026-11-13 | **The Phone page (`luci-app-ufi-voice`) built and installed; `ufi-voice` r12.** SIP accounts moved into uci (`config phone`), one idempotent generator (`ufi-voice-asterisk-apply`) writes `pjsip.conf`/`extensions.conf`/`manager.conf`, and the page applies changes by running `ufi-voice-apply` through rpcd. Image built (`./build.sh build mifib-v12`, 704 s, all three apks + sysupgrade image). **F36 explained properly (§8.5):** the unit's `/etc/apk/world` pins a kernel hash the unit no longer runs and every kmod record names that same stale hash, so `apk` cannot solve *any* install; files were installed from `.pkgdir` and the world pin corrected. Bench unit is healthy again — SIM active, `connected`, LTE, IMS `registered`. |
| 2026-11-13 | **Live call re-verified after the r12 + LuCI change** (S28): Dial Call `SUCCESS`, MM saw `dialing → ringing-out → active`, call info `state=conversation type=voice-ip direction=mo mode=lte`, and the far end answered. No regression from the package rebuild. Found that a manual `qmi-raw` dial bypasses `ufi-voiced.voice_start()`, so no `voicebridge` runs for it (§8.2) — the audio-quality test needs either the daemon to own the call or a hand-started bridge plus a registered SIP client. |
| 2026-11-13 | **Bridge measured by hand on a live call** (S29). A manual dial leaves `ufi-voiced` out of the loop, so `voicebridge` was started directly — 40 s of statistics at `-j 60`: `cap`/`play` at 48 kHz, `out` at 8 kHz, **gaps 0, dropped 0 ms, xruns 0, outdrop 0**; the DSP→host leg is clean. `in 0/s` because no SIP client was registered, so the uplink/phone-leg half is still unmeasured. Trap: `voicebridge` exits instantly when stdin is EOF (an ssh one-liner gives it EOF) — it must get a pipe. Hangup: `--voice-hangup-all` errors `IncompatibleState` but does clear the call; `End Call` TLV `0x01` gives `InvalidQosId`. |
| 2026-11-13 | **F35 verified on the real SIP path (S30).** Two softphone-originated calls went SIP → dialplan → `ufi-voice-dial` → MM → modem with `ufi-voiced` starting the bridge itself: both directions at 8 kHz, `dropped 0 ms`, `outdrop 0`, and zero gaps in every window after the first — the callee reported the audio clear. Only remaining silence is the buffer fill in each call's first 5 s window (`jbuf 0/60`, 1–3 gaps, one playback xrun). Added the reading note: `jbuf <min>` dips to 10 ms by design, `gaps` is the number that matters. |
| 2026-11-13 | **`ims` profile at every modem boot, packaged (S31, §8.6).** `ims-profile-setup`, its init script and the `wwan` hotplug handler existed only on the bench unit; they are now in `ufi-voice` r13 together with a new `ims-profile-watch` (S97) that covers runtime modem restarts (MM disable/enable + `imsdcm` restart). Fixed a latent bug: this busybox rejects `sleep 0.1`, which turned the setup script's 60 s device wait into a millisecond spin. Corrected F30 — the WDS table and its IMCN/PCO flags survive a delete plus reboot (restored from NV), so the create path only matters on a blank table. |
| 2026-11-13 | **IMS PDN churn and data coexistence re-measured (S32).** No churn with `imsdcm`: 180 s capture, 0 start/stop events, daemon pid unchanged through a call. The only burst was self-inflicted (manual `imsdcm` restart while the default bearer was re-establishing — IMS took `wwan0`, collided, settled in ~25 s). Coexistence works: `jionet` on `wwan0` + `ims` on `wwan1` both connected, a VoLTE call reached `active` with the callee answering, no PDN teardown during it. §9.1 items 3 and 4 closed as not-reproducible / works. |
| 2026-11-14 | **Dead air both directions — the audio DSP hung at call teardown (§8.7, S33).** A unit that had worked for hours suddenly connected every call but carried no audio. First failure of the boot is an **AFE close** `-110`, after which every AFE/ASM/ADM command times out and `voicebridge` cannot open the capture PCM. This port has no ADSP firmware: the APR services are children of the modem's SMD edge (`4080000.remoteproc:smd-edge:apr:service@{4,7,8,9}`), so audio shares the modem's Q6 and the AP cannot re-probe it. Fixed the AP-side ordering defect that precedes it — `voicectl stop` cleared the mixer routes while `hostless` (1 s poll) still had its PCMs open; r14 waits for the pid, zombie-aware. Recovery is an AP reboot; detection is now `dsp: hung` in the status JSON plus a row on the Phone page. |
| 2026-11-14 | **A dead bridge left the rest of the call silent (§8.8, S34).** `bridge_died()` cleared the leg and re-routed but never restarted the bridge, because `reroute()` only starts it together with the voice path — so any mid-call bridge death meant silence in both directions until hangup. Found by killing the bridge for a spectrum capture (`voicebridge` exits **0** on SIGTERM). Fixed in r15: restart it when the path is up and one call is active, still gated to one attempt per 10 s. |
| 2026-11-14 | **The downlink is narrowband — measured (§8.9).** 12 s of `hw:0,0` at 48 kHz during a `voice-ip`/`lte` call: speech to 3.9 kHz, `-94 dB` at 4.0 kHz, noise floor above 4.5 kHz. AMR-NB, no 4–7 kHz content, so G.722 alone would only add a 16k→8k transcode. Wideband would need the AudioSocket **channel driver** (`c(slin16)`), `codec_g722`, a configurable `PIPE_RATE`, and `Dial(AudioSocket/...)` — not worth building until a call is actually wideband. |
