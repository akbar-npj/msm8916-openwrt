# 222 — VoLTE merge manifest: `test/volte-openimsd` → `main`

Everything the VoLTE work adds to the tree, what each piece is for, what it
depends on, what to check before merging, and what to decide first.

Written 2026-11-14. The per-area numbers in the table are a snapshot from then;
§0 says how to get the current ones.

This is the **inventory** document. For how the stack works see
`221_VOLTE_GUIDE.md`; for the bring-up procedure, the failure ledger and the
bench history see `220_VOLTE_ENABLEMENT_SOP.md`. Nothing here repeats them.

---

## 0. The delta

The branch is `main` plus the inventory in the table below.

No commit count and no branch-tip hash are recorded here, on purpose. Every edit
to this file is itself a commit on the branch, so a count or a tip hash written
here is wrong the moment it is saved — it has been re-anchored three times for
exactly that reason. Ask git instead:

```
git rev-list --left-right --count main...HEAD   # behind, ahead
git diff --shortstat main...HEAD                # files, insertions, deletions
```

Only **behind** matters for the merge itself: at 0 the merge is a clean
`git merge --ff-only`; the moment main moves it is not, and the branch has to be
rebased onto it first.

| area | files | lines | why it is in the merge |
|---|---|---|---|
| `msm89xx/patches/` | 3 | +2 575 | q6voice kernel driver + DTS + one bam-dmux race fix |
| `msm89xx/` (modules, config, image) | 3 | +54 / −2 | kmod package, Kconfig guards, board package lists |
| `packages/ufi-voice/` | 26 | +3 844 | the product: IMS data service, call daemon, audio bridge, push |
| `packages/qmi-raw/` | 2 | +396 | dev tool: arbitrary QMI requests (IMS 0x008f, IMSD 0x20) |
| `packages/luci-app-ufi-voice/` | 4 | +401 | Services → Phone page |
| `msm89xx/base-files/` | 2 | +247 / −3 | voice-aware holds in the bearer watchdog |
| `packages/luci-app-modem-watchdog/` | 2 | +41 / −2 | LuCI for those holds |
| `openwrt-overlay/` (ModemManager) | 1 | +28 | call-matching fix (waiting call vs active call) |
| `packages/msm89xx-scripts/` | 2 | +5 | `chmod +x` the netifd proto payload |
| `scripts/openwrt-prepare.sh` | 1 | +15 / −7 | project package patches land in `package/msm8916/` |
| `tests/modem-watchdog/` + CI | 2 | +385 | self-check for the watchdog changes |
| `diffconfigs/mifib-v12` | 1 | +1 | `qmi-raw` |
| `Docs/` + `README.md` | 9 | +2 704 / −6 | 220, 221, 222, IMS_VOLTE, UFI003_VOICE, watchdog doc |

---

## 1. Kernel side

### 1.1 The three patches

All three are in `msm89xx/patches/`, so they are applied by the normal build prep — no separate step.
They start at 873 because 870/871 went to the Venus video stack (PR #75, merged) and 872 is taken by an open PR.

| patch | lines | what it does | touches |
|---|---|---|---|
| `873-asoc-qcom-msm8916-voice.patch` | 2 519 | q6voice from msm8916-mainline (Stephan Gerhold) plus the UFI-specific work: q6afe internal-BT-SCO ports and the voice record/playback pseudo ports; q6routing `VOC_REC_DL/UL` + `VOICE_PLAYBACK_TX`; q6voice BT-SCO mixers, CS/VoLTE session select, in-call record taps, in-call playback through a CVS passive session, vocproc "Voice Tx Mute"; non-MI2S backends in the qdsp6 card | `sound/soc/qcom/{Kconfig,apq8016_sbc.c,qdsp6/*}`, new `q6cvs.c`, `q6cvp.c`, `q6mvm.c`, `q6voice*.c`, `msm8916-ufi-voice.dtsi`, `msm8916.dtsi`, two dt-bindings headers, ufi003 board dts |
| `874-bam-dmux-pc-irq-recheck-state-under-lock.patch` | 37 | re-checks `pc_state` under `state_lock` in `bam_dmux_pc_irq()`: the RX watchdog can resync the same edge first, and a double ack leaves the A2 handshake one toggle off → `a2_power.c:3017` assert 47 ms later, seen during a VoLTE call | `drivers/net/wwan/qcom_bam_dmux.c` |
| `875-arm64-dts-qcom-mifib-v12-enable-voice.patch` | 19 | includes the shared voice dtsi in the mifib-v12 DTS | `arch/arm64/boot/dts/qcom/msm8916-generic-mifib-v12.dts` |

**Kernel-bump risk:** 873 is a backport, not an upstream cherry-pick — it edits
`msm8916.dtsi`, `q6afe.c`, `q6afe-dai.c`, `q6routing.c` and the qdsp6 Makefile
in place. Any move of the OpenWrt kernel base will require re-basing it by hand.
874 is small and self-contained. 875 is one `#include`.

**DTS coverage:** the voice card is enabled on **mifib-v12** (875) and
**ufi003-mb-v02** (873). The other seven boards do not get it — deliberate, the
audio routing was only characterised on those two.

### 1.2 The kmod package

`msm89xx/modules.mk` gains `KernelPackage/sound-msm8916-voice` (46 lines):

```
DEPENDS  @TARGET_msm89xx +kmod-sound-soc-core
KCONFIG  CONFIG_QCOM_APR, QCOM_PDR_HELPERS, QCOM_PDR_MSG, SND_SOC_QCOM,
         SND_SOC_APQ8016_SBC, SND_SOC_QDSP6, SND_SOC_QDSP6_Q6VOICE,
         SND_SOC_BT_SCO
FILES    21 .ko: apr, pdr_interface, qcom_pdr_msg, snd-soc-bt-sco,
         snd-soc-qcom-common, snd-soc-apq8016-sbc, q6core, q6afe,
         q6afe-dai, q6afe-clocks, q6adm, q6routing, q6asm, q6asm-dai,
         q6voice-common, q6mvm, q6cvs, q6cvp, q6voice, q6voice-dai
AUTOLOAD apr → q6core → … → q6voice-dai → bt-sco → apq8016-sbc
```

`CONFIG_SND_SOC_QDSP6_Q6VOICE` / `_DAI` are **new Kconfig symbols created by
patch 873** — the module package is therefore unusable without the patch, and
vice versa.

`msm89xx/config-6.12` adds `# CONFIG_SND_SOC_MSM8996 is not set` and
`# CONFIG_SND_SOC_SC7180 is not set` (both would otherwise be pulled in by
`SND_SOC_QCOM` and collide).

### 1.3 Board package lists

`msm89xx/image/msm8916.mk`, `DEVICE_PACKAGES`:

| device | added |
|---|---|
| `generic-ufi003-mb-v02` | `kmod-sound-msm8916-voice alsa-utils ufi-voice ufi-voice-asterisk` |
| `generic-mifib-v12` | the same **plus** `luci-app-ufi-voice` |

Note the asymmetry: the UFI003 image gets the daemon and Asterisk but no LuCI
page, because that device is configured from its own scripts. Decide at merge
time whether that is still intended (§9.2).

---

## 2. `packages/ufi-voice` — the product

Two packages out of one directory, `PKG_RELEASE:=19`.

```
ufi-voice           DEPENDS @TARGET_msm89xx +kmod-sound-msm8916-voice +alsa-lib
                    +alsa-utils +libdbus +python3-light +qmi-utils +modemmanager
                    +diag-efs +diag-logtool +libopenssl
ufi-voice-asterisk  DEPENDS +ufi-voice +asterisk +asterisk-pjsip
                    +asterisk-res-rtp-asterisk +asterisk-bridge-simple
                    +asterisk-codec-{ulaw,alaw} +asterisk-app-audiosocket
                    +asterisk-res-audiosocket +asterisk-func-shell
                    +asterisk-res-timing-timerfd
```

### 2.1 What each installed file is

| installed as | source | role | merge-relevant |
|---|---|---|---|
| `/usr/sbin/imsdcm` | `files/imsdcm` (py) | AP side of QMI 770: creates the `ims` PDN through ModemManager, answers the modem's IMSD requests | **the VoLTE enabler**; replaces 81voltd |
| `/etc/init.d/imsdcm` | `files/imsdcm.init` | S96, gated on `ufivoice.main.volte=1` | — |
| `/usr/sbin/ims-profile-setup` | `files/ims-profile-setup` | creates the `ims` WDS profile (IMCN + PCSCF-via-PCO) | the only copy in the tree since §4.4 |
| `/etc/init.d/ims-profile` + S94 | `files/ims-profile.init` | runs the setup once per boot | runs before imsdcm (S96) |
| `/etc/hotplug.d/wwan/20-ims-profile` | `files/hotplug.d/wwan/20-ims-profile` | runs it on every modem (re)boot | — |
| `/usr/sbin/ims-profile-watch` + S97 | `files/ims-profile-watch` | runtime watcher for modem boots that the hotplug misses | — |
| `/usr/sbin/ufi-voiced` | `files/ufi-voiced` (py, 884 lines) | call state machine: MM ↔ AMI ↔ audio bridge, incoming/outgoing, decline→hangup, push notify | the core |
| `/etc/init.d/ufi-voiced` | `files/ufi-voiced.init` | — | — |
| `/usr/bin/voicebridge` | `src/voicebridge.c` (426) | ALSA ↔ AudioSocket, 48 k ↔ 8 k resampler, jitter buffer, `-R` capture | — |
| `/usr/bin/hostless` | `src/hostless.c` (126) | opens CS-Voice both ways to start the voice path | — |
| `/usr/sbin/hfp-ag` + init | `src/hfp-ag.c` (645) | Bluetooth HFP AG over D-Bus (headset calling) | **least tested path** |
| `/usr/sbin/voicectl` | `files/voicectl` (147) | DSP routing: `start host cs\|volte`, teardown | — |
| `/usr/sbin/ufi-voice-apply` | `files/ufi-voice-apply` | applies uci → running system | — |
| `/usr/sbin/ufi-voice-status` | `files/ufi-voice-status` | status helper used by LuCI/CLI | — |
| `/usr/sbin/ufi-voice-dial` | `files/ufi-voice-dial` | dial helper | — |
| `/usr/sbin/ufi-voice-notify` | `files/ufi-voice-notify` | ntfy push (plain or JWE) for a sleeping phone | needs `ntfy-jwe` |
| `/usr/bin/ntfy-jwe` | `src/ntfy-jwe.c` (156) | JWE `dir`/A256GCM builder, `-lcrypto` | needs `libopenssl` |
| `/usr/sbin/volte-setup` | `files/volte-setup` | modem EFS items (`ims_hybrid_enable`) | — |
| `/etc/config/ufivoice` | `files/ufivoice.config` | all runtime knobs, conffile | see §2.2 |
| `/etc/ModemManager/connection.d/10-report-down` | `files/10-report-down` | tells MM an IMS bearer went down | — |
| `/etc/uci-defaults/90-ufi-voice-services` | — | enables `imsdcm` | see §4.4 |
| `/etc/uci-defaults/91-ufi-voice-asterisk` | (asterisk pkg) | runs the Asterisk generator once | — |
| `/usr/sbin/ufi-voice-asterisk-apply` | (asterisk pkg, 249 lines) | generates `pjsip.conf` + `extensions.conf` from uci | idempotent, re-run by LuCI |

### 2.2 Defaults worth reviewing in the diff

`ufivoice.config` ships **active defaults**, not an opt-in switch:

```
volte '1'          imsdcm runs out of the box
mode 'both'        calls go to Bluetooth and to the SIP phones
session 'auto'     volte while IMS registered, else cs
sip_ring 'PJSIP/100&PJSIP/101'
notify '0'         push off by default, topic empty
```

`volte=1` means that on a device with the packages installed the IMS data
service starts by itself. That is the intent (VoLTE "just works"), but it is the
one default a reviewer should consciously accept.

---

## 3. `packages/luci-app-ufi-voice` — the Phone page

`PKG_RELEASE:=3`, `LUCI_DEPENDS:=+ufi-voice`, four files:

- `htdocs/.../view/ufi-voice/config.js` (349) — SIP accounts, ring list, call
  mode, audio (rate/jitter/host-tx-mute), live registration status, and the
  notification block (`notify`, `ntfy_url`, `ntfy_topic`, `ntfy_token`,
  `ntfy_password`).
- `root/usr/share/luci/menu.d/luci-app-ufi-voice.json` — Services → Phone.
- `root/usr/share/rpcd/acl.d/luci-app-ufi-voice.json` — uci `ufivoice`, plus
  exec of the apply/status helpers.
- `Makefile`.

The page contains a deliberate workaround: rpcd answers `uci/apply` with
`NO_DATA (5, "No data received")` when the save already committed the section,
so the apply handler treats a `No data` error as success. Keep it — without it
every Save & Apply shows a red banner.

---

## 4. The other packages

### 4.1 `qmi-raw` (396 lines)

Development tool, `PKG_RELEASE:=4`. Sends an arbitrary QMI request (service id,
message id, raw TLVs) and prints the decoded reply. It exists because libqmi
1.36 lacks IMS `0x008f` ("Set IMS Services Enabled Setting") and IMSDCM `0x20`,
which are what actually turn VoLTE on and drive the IMS bearer.

Ship it or not is a judgement call: it is harmless (~30 KB) and it is how the
modem was brought up. It used to be a dependency of `81voltd` (now removed), but
the SOP's diagnostic recipes use it directly, so I would keep it.

### 4.2 `luci-app-modem-watchdog` (41 lines)

Adds the four voice options to the existing page: `voice_alive`, `data_plan`
(auto / data SIM / voice-only), `voice_hold_secs`, `drop_default_route`. Also
grants rpcd exec on `/etc/init.d/modem-bearer-watchdog` so Apply restarts it.

### 4.3 `modem-bearer-watchdog` (+221 lines)

Voice-aware holds, all opt-in (`voice_alive '0'` by default):

- a call in progress, or a SIM with no data service that is alive for voice
  (IMS registered, or registered with live serving-cell measurements), skips the
  recovery ladder instead of restarting the modem — restarting cannot bring
  data to a voice-only SIM;
- `data_plan=auto` infers "no data service" per ICCID: a SIM swap starts over,
  a modem restart does not; any downlink packet ends the hold;
- `drop_default_route` removes the modem's default routes during a hold so
  traffic never falls back to an empty bearer (on UFI003 those fallbacks came
  with `a2_power.c:1216` fatals during calls). Only while another uplink has,
  or had within 5 minutes, a default route.

This is the only change in the merge that touches a **shipping recovery path**
on all nine boards. It is default-off, but it is also the change most worth a
second pair of eyes. `tests/modem-watchdog/run.sh --self-check` covers it and is
now part of CI (`.github/workflows/tests.yml`).

### 4.4 `81voltd` — removed in this branch

`packages/81voltd` was the first implementation of the AP side of IMSD
(upstream `gitlab.nura.eco/modem/81voltd` @ `a7794dd6`, `PKG_RELEASE:=13`).
`imsdcm` replaced it, and the package is now **gone from the tree**.

Why it had to go rather than sit there as a fallback:

- it installed **the same three paths** as ufi-voice

  ```
  /etc/init.d/ims-profile
  /usr/sbin/ims-profile-setup
  /etc/hotplug.d/wwan/20-ims-profile
  ```

  so whichever package installed last won, and `/etc/init.d/81voltd.apk-new`
  is still on the bench unit as evidence of the collision;
- its copy of `ims-profile-setup` was **stale**: it used `sleep 0.1`, which
  busybox rejects with `sleep: invalid number '0.1'` (verified on the unit), so
  its 600-iteration wait for the modem device became a busy spin;
- nothing selected it for a reason other than "the old path", and the two
  services cannot run together anyway.

What the removal touched:

| file | change |
|---|---|
| `packages/81voltd/` | deleted (11 files: Makefile, 5 scripts, 6 patches) |
| `diffconfigs/mifib-v12` | `CONFIG_PACKAGE_81voltd=y` dropped |
| `packages/ufi-voice/files/90-ufi-voice-services` | the `81voltd disable` line and its comment dropped; it now only enables `imsdcm` |
| `packages/ufi-voice/files/ims-profile.init` | comment retargeted from "runs before 81voltd (S96)" to "runs before imsdcm (S96)" |
| `packages/qrtr/Makefile` | **reverted to main** — the `qrtr.pc` install and the `PKG_RELEASE` bump existed only for 81voltd's meson build |
| `scripts/openwrt-prepare.sh` | **kept** — the bug it fixes (project-package patches copied to `package/system/` instead of `package/msm8916/`) hits any future package with a `patches/` directory |
| docs 220/221 | the live procedure now says `imsdcm`; the history in the failure ledger and the success record still names 81voltd, which is correct |

**Upgrade caveat.** A unit flashed from the test image keeps
`/etc/init.d/81voltd` and its `rc.d` link in the overlay even after the new
image drops the package, and it would then fight `imsdcm` over QMI 770. Disable
it once by hand (`/etc/init.d/81voltd disable`) on any such unit; a clean flash
has nothing to clean up.

---

## 5. Changes outside the voice packages

| change | why it is here | risk |
|---|---|---|
| `openwrt-overlay/.../modemmanager/patches/100-iface-modem-voice-dont-match-different-index-by-number.patch` | a waiting call from the same number as the active call was matched with the active one, which took over its state and lost the real call | low, but it changes MM call matching for **every** board |
| `msm89xx-scripts` `modemmanager-proto-hotfix` | `cp -f` kept the payload's 0644, and netifd only loads executable proto handlers → interface stayed `"proto": "none"` | low, one `chmod +x`; the payload file also flips 644 → 755 |
| `scripts/openwrt-prepare.sh` | project-package patches were copied to `package/system/<pkg>`, but project packages live in `package/msm8916/<pkg>` | none today (no package ships `patches/` any more); kept as a latent fix |
| `.github/workflows/tests.yml` | runs the modem-watchdog self-check on PRs | adds ~seconds |

---

## 6. Docs carried by the branch

| doc | lines | role |
|---|---|---|
| `Docs/Modem Stability/220_VOLTE_ENABLEMENT_SOP.md` | 1 182 | living SOP: system map, procedure, failure ledger F1–F35, success record, open questions |
| `Docs/Modem Stability/221_VOLTE_GUIDE.md` | 520 | the complete guide: status, chain, what is installed, build/flash, bring-up, verification, audio path, automation, Bluetooth, Asterisk |
| `Docs/IMS_VOLTE.md` | 259 | architecture, UFI003 recipe, imsdcm, network config, calls, profile compatibility, privacy |
| `Docs/UFI003_VOICE.md` | 231 | UFI003-specific voice notes |
| `Docs/Modem Stability/MODEM_BEARER_WATCHDOG.md` | +40 | documents the voice holds |
| `Docs/Modem Stability/HMU05_SSR_…_REATTACH.md` | +41 | notes the 874 race |
| `Docs/Boards/UFI003_MB_V02/README.md`, `README.md` | +3 | pointers |

221 §5 and 220 §8 are the two a support engineer will actually open. Make sure
they still describe the shipped defaults after the merge (§2.2).

---

## 7. Dependency graph

```
patch 873 (q6voice, dtsi) ──► kmod sound-msm8916-voice ──► ufi-voice ──► luci-app-ufi-voice
        patch 875 (mifib DTS) ─┘                              │
                                                             ├──► ufi-voice-asterisk
patch 874 (bam-dmux race) ── independent ─────────────────────┘
ufi-voice also owns the IMS profile path (ims-profile-setup, ims-profile, the wwan hotplug hook)
qmi-raw ── standalone tool (no package depends on it any more)
modem-bearer-watchdog + luci ── independent of the above (only the uci keys are shared)
ModemManager patch 100 ── independent, benefits every board with calls
```

Hard dependencies: **873 ⇄ kmod ⇄ ufi-voice**. You cannot merge the package
without the patch or the module. Everything else can be dropped individually
except `ufi-voice-asterisk`, which needs `ufi-voice`.

---

## 8. Merge procedure

```sh
# 0. the branch is on the private remote only; merging publishes it
git fetch private && git fetch origin && git fetch upstream

# 1. today this is a clean fast-forward; if main moved, rebase instead
git checkout main && git pull --ff-only
git merge --ff-only test/volte-openimsd
#    ... or: git checkout test/volte-openimsd && git rebase main

# 2. the tree must match the patches before anything is built
./build.sh guard --deep

# 3. build the two boards that carry the feature
./build.sh build mifib-v12 ufi003

# 4. one board from the untouched set, to prove the kmod/image edits are inert
./build.sh build ufi001b
```

**Expected conflict sites** once main has moved (none today):

- `msm89xx/image/msm8916.mk` — `DEVICE_PACKAGES` lines for ufi003 / mifib-v12
- `msm89xx/modules.mk` — append after `sched-bbr`
- `msm89xx/config-6.12` — the two `is not set` lines
- `diffconfigs/mifib-v12` — two `CONFIG_PACKAGE_` lines
- `scripts/openwrt-prepare.sh`, `.github/workflows/tests.yml`
- `Docs/Modem Stability/MODEM_BEARER_WATCHDOG.md`

If 873 fails to apply after a kernel bump, do **not** force it: re-base it on
the new `sound/soc/qcom` state, because the q6afe/q6routing hunks depend on the
exact surrounding code.

**One commit or 40?** The 40 commits are already grouped and each message
explains itself; a fast-forward keeps that history, which is what the SOP
references. Squashing loses the ability to bisect the audio work. I would keep
them, and write the release note from §0–§2.

---

## 9. Decisions to make before merging

1. **Publish or not.** This is the whole point of the private branch: the work
   is currently only on `private`. Merging to main makes it public.
2. **Board scope.** Voice is enabled on mifib-v12 and ufi003-mb-v02. The other
   seven boards get neither the DTS include nor the packages. Confirm that is
   the intent, and that UFI003 without `luci-app-ufi-voice` is intended too.
3. **Defaults.** `volte '1'` and `mode 'both'` mean a fresh install starts the
   IMS data service and rings both the headset and the SIP phones. Confirm.
4. **`qmi-raw` on the image.** Dev tool, but the SOP recipes use it.
5. **Package releases for the release note:** `ufi-voice` r20,
   `luci-app-ufi-voice` r3, `qmi-raw` r4.

---

## 10. Verification checklist

**Build (host)**

- [ ] `./build.sh guard --deep` — tree matches tracked sources
- [ ] `./build.sh build mifib-v12 ufi003` — both images complete
- [ ] `./build.sh build ufi001b` — an untouched board still builds
- [ ] `ls openwrt/bin/.../packages/` contains `ufi-voice-1.0-r20.apk`,
      `luci-app-ufi-voice-*r3*`, `kmod-sound-msm8916-voice`, `qmi-raw`,
      `ufi-voice-asterisk`

**Flash and boot (mifib-v12, clean image — never tested from a clean flash)**

- [ ] `aplay -l` / `cat /proc/asound/cards` shows the q6voice card
- [ ] `/etc/init.d/imsdcm` running, `logread | grep imsdcm` shows the bearer up
- [ ] `qmicli -d /dev/wwan0qmi0 --nas-get-serving-system` → IMS registered
- [ ] `mmcli -m 0 --voice-list-calls` works; an outgoing call reaches
      `VOICE_IP` / `CONVERSATION`
- [ ] audio both ways on an Asterisk phone (`voicebridge` statistics: `gaps`,
      `dropped 0`, `xruns 0`)
- [ ] `dmesg | grep -c "failed -110"` stays 0
- [ ] a Bluetooth headset call (`bt_headset`, `mode bt`)
- [ ] the watchdog still restarts a deliberately stalled bearer
      (`tests/modem-watchdog/run.sh`)

**Regression on an untouched board**

- [ ] boots, data comes up, no new dmesg warnings from the kmod or the watchdog

---

## 11. What is not proven yet

These do not block the merge, but they belong in the release note so nobody
assumes more than was tested:

- **clean flash** — every result so far came from a unit that was patched in
  place; the image path was never booted from scratch (also the only way to
  test a blank modem profile table);
- **Bluetooth headset** — the HFP AG path has been exercised, but not as a soak;
- **other carriers / 3G-only SIMs** — only Jio was tested;
- **`voicebridge` restart mid-call** (`voicebridge died during a call,
  restarting it`) never fired in a soak; only proven by killing it by hand;
- **narrowband only** — measured: content above −20 dB ends at 3 574 Hz, so the
  call is 8 kHz end to end. G.722 / AMR-WB would carry nothing (SOP §8.9,
  memory S37).
- the ~15-minute modem-death guard from the earlier work is unchanged by this
  branch and still applies.

---

## 12. Rollback

The merge is additive except for the files main already has:
`msm89xx/{modules.mk,config-6.12,image/msm8916.mk}`, `diffconfigs/mifib-v12`,
`msm89xx/base-files/{etc/config/modem-watchdog,usr/sbin/modem-bearer-watchdog}`,
the two `luci-app-modem-watchdog` files, `packages/qrtr/Makefile`,
`packages/msm89xx-scripts/{root/etc/init.d/modemmanager-proto-hotfix,root/usr/share/msm89xx/modemmanager.sh.hotfix}`,
`scripts/openwrt-prepare.sh`, `.github/workflows/tests.yml`, `README.md`,
`Docs/Boards/UFI003_MB_V02/README.md` and the two edited modem-stability docs.
`git revert -m 1 <merge>` restores main exactly; a device already flashed with
the merged image keeps the voice kmods on its overlay until a reflash, which is
harmless because nothing starts without the packages.
