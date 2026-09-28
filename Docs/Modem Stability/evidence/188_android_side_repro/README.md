# Android-side SSH setup — passwordless, with `passwd root` honored

**Date:** 2026-09-23
**Device:** stock Android 4.4.4 on the HMU05 unit (serial `c2b9103c`), kernel 3.10.28 armv7l
**Purpose:** give the R5b work (Doc 188) a reliable root shell that survives independently
of `adb` — the corpus documents that background jobs launched from a short-lived
`adb shell` die, while an SSH session can hold a loop open.
**Final state capture:** `android_ssh_setup_final_state.txt`

---

## 1. What was already there

- `/system/xbin/sshd` — **OpenSSH 8.2p1 / OpenSSL 1.1.1d**, plus `/system/xbin/ssh`.
- `/system/etc/ssh/sshd_config` (3 727 B): `Port 22`, `PermitRootLogin yes` (twice),
  `PubkeyAuthentication yes`, `UseDNS no`, `UsePrivilegeSeparation no`, a `PidFile` at
  `/data/local/tmp/sshd.pid`, and `SetEnv` lines. **No `Match` blocks.** No
  `PasswordAuthentication` / `PermitEmptyPasswords` / `UsePAM` line at all, so OpenSSH
  defaults applied (`PermitEmptyPasswords` defaults to **no**).
- `/etc/passwd`: `root:x:0:0:root:/data/local:/system/xbin/bash`
- `/etc/shadow`: a real `$1$…` hash (you had run `passwd root` at 01:07)
- `/system/xbin/passwd` — a **standalone shadow-utils-style `passwd`** (not busybox),
  with `-d/--delete`, `-l/--lock`, `-u/--unlock`, `-S/--status`. **No `--stdin`.**
- **Autostart already existed, and it is the operator's.** `/system/etc/init.qcom.post_boot.sh:981`
  runs `/system/xbin/sshd -p 22` inside a "Master Boot Trigger" block (after `sleep 15`),
  reached from `/init.qcom.rc:714` — `on property:sys.boot_completed=1` → `start qcom-post-boot`
  (`class late_start`, `user root`, `oneshot`). The ROM's *other* entry,
  `service sshd /system/bin/start-ssh`, is `disabled`, has **no** trigger, and its target
  `/system/bin/start-ssh` **does not exist** — dead code, left alone.
- The device is **already root** (`adb shell id` → `uid=0(root)`) and `/system` was
  mounted **rw**.

---

## 2. What was changed

| change | detail |
| :-- | :-- |
| `/system/etc/ssh/sshd_config` | appended `PasswordAuthentication yes` and **`PermitEmptyPasswords yes`** (lines 136–137) |

**★ Nothing was added to autostart, and an earlier mistake of mine was reverted.** I first
created `/system/bin/start-ssh` and `/system/etc/install-recovery.sh` (the latter wired to
the ROM's unused `service flash_recovery` hook, `class main`). **That was wrong**, and the
operator caught it: it **duplicated their existing `post_boot.sh` autostart**. Worse, since
`flash_recovery` is `class main` and `qcom-post-boot` is `class late_start` (+15 s), mine
would run **first**, win port 22, and make **the operator's mechanism fail** with
`Address already in use`. Both files were **removed**, and sshd was restarted through the
`post_boot` path. Verified afterwards: exactly one listener, and `post_boot.sh:981` is the
**sole** remaining sshd autostart.

Backups (on device): `/data/ssh_backup_2026-09-23/{sshd_config.bak,shadow.bak,passwd.bak}`.
Original hashes: `sshd_config` `b3b534eea3deede7bb238bd8ac9b5399`, `shadow`
`af849bd9a1605eb1d3035679f14fd773`.

---

## 3. The verified auth matrix

| shadow state | how set | `ssh root@…` with no password | with the right password | with a wrong password |
| :-- | :-- | :-- | :-- | :-- |
| real `$1$…` hash | `passwd root` + a password | **denied** | **accepted** | **denied** |
| empty field | `passwd -d root` | **accepted** | — | **accepted** |
| hash = `crypt("")` | `passwd root` + **empty input** | **accepted** | — | **accepted** |

Measured, verbatim:

```
$ ssh -o BatchMode=yes root@192.168.100.1 'echo OK; id'      # passwordless state
TRULY_PASSWORDLESS_OK
UID=0(root) GID=0(root)

$ sshpass -p 'RealPass456' ssh … root@192.168.100.1 'echo OK; id'   # password set
PASSWORD_HONORED
UID=0(root) GID=0(root)

$ sshpass -p 'nope' ssh … root@192.168.100.1 'echo NOPE'
root@192.168.100.1: Permission denied (publickey,password,keyboard-interactive).
```

So **both of the requested behaviours hold**: `passwd root` with a password is enforced,
and `passwd root` with nothing entered yields a passwordless login.

---

## 4. The mechanism — it is `none` auth, not empty-password auth

`ssh -vv` on a "passwordless" login reports:

```
Authenticated to 192.168.100.1 ([192.168.100.1]:22) using "none".
```

So this sshd is **patched to let the `none` method succeed when the account has no
password** (it checks the shadow entry, not `/etc/passwd`'s `x`). The client never sends
a password at all — which is why `BatchMode=yes` works and why `sshpass -p <anything>`
also works.

**★ Consequence, and it is a security fact worth stating plainly:** in the passwordless
state the device accepts **any** client that can reach port 22 as **root, with no
credential**. `PermitEmptyPasswords yes` is what makes the *password* method agree with
the *none* method; the `none` acceptance itself comes from the patch.

**How to lock it down again:** run `passwd root` with a password. That was verified to
restore enforcement immediately (no sshd restart needed for a *set* password; a `HUP`
was used here only for the config change).

---

## 5. Driving `passwd` non-interactively (the part that cost time)

`passwd` reads through `getpass()`, which needs a **real tty**:

- `printf … | passwd root` → input is swallowed by the pty before the prompt; **no change**.
- `printf … | setsid passwd root` → no tty, so `getpass()` returns empty →
  `The password for root is unchanged.` (rc 1).
- `printf … | socat - EXEC:'passwd root',pty,setsid,ctty` → prompt appears but the input
  **races ahead** of it; still no change.
- **Working form — pace the input:**
  ```sh
  { sleep 2; printf 'NewPass\n'; sleep 2; printf 'NewPass\n'; sleep 3; } | \
      socat - EXEC:'/system/xbin/passwd root',pty,setsid,ctty,echo=0
  ```
  → `passwd: password changed.`

**Empty password** uses the same form with two bare `\n`. Note this passwd prints
"minimum of 5 characters" but **still accepts an empty input**, storing `crypt("")`
rather than an empty field. `passwd -d root` is the alternative and stores a genuinely
empty field; both behave identically at login.

**Trap that wasted a cycle:** piping the command through `head -8` closed the pipe early,
SIGPIPE-killed `passwd` before it committed, and left the shadow **unchanged** — which
looks exactly like "passwd rejected the password".

---

## 6. Persistence

| item | survives reboot? |
| :-- | :-- |
| `sshd_config` edit | **yes** — written to the ext4 `/system` partition (mounted `ro` at boot, but the bytes persist) |
| `/etc/shadow` | **yes** — on `/data` |
| sshd **running** | **yes** — via the operator's existing `post_boot.sh` hook, triggered by `sys.boot_completed=1` |

`/fstab.qcom` mounts `/system` as `ro,barrier=1,discard`, so if the config ever needs
editing again, `mount -o rw,remount /system` first.

---

## 7. SOP compliance

- **Read the definition, not the name.** The crash behaviour was determined from
  `ssh -vv`'s `Authenticated … using "none"` and from the shadow/passwd contents, not
  assumed from "PermitEmptyPasswords is on".
- **Verify a manipulation is PRESENT, not merely issued.** Each state change was
  confirmed by re-reading `/etc/shadow` (with md5) *and* by an actual login attempt; the
  one case where the shadow did not change (the `head -8` SIGPIPE) was caught exactly
  this way.
- **A surprising claim was re-tested.** "Any password logs in" looked like a test
  artefact, so it was re-run with `ssh -vv` — which showed the real mechanism (`none`),
  not a password comparison.
- **No baseband involvement.** This is AP-side userspace/system configuration only.
- **★ Error caught by the operator: I added an autostart without checking whether one
  already existed.** I found the ROM's *unused* `service sshd` and *unused*
  `flash_recovery` hook and wired up a boot script — never asking "does something already
  start this?" A single `grep -rn sshd /system/etc/*.sh` would have shown
  `init.qcom.post_boot.sh:981` immediately. The rule this reinforces: **before adding a
  start-up path, enumerate the existing ones**; two hooks for one service is a race, and
  because classes start in order the *earlier* class silently wins and the intended
  mechanism fails. Also note the evidence was in the operator's own shell history
  (`/data/local/.bash_history:282`), which is a cheap thing to check on a device someone
  else has been configuring.

---

## 8. Kernel module profile comparison — builtin vs loadable (separate question)

Answering *"also check inbuilt and loadble kernel modules, they also may be what protects
15 minute crash"*: **see `android_module_profile_comparison.md`** in this directory.

Short version:

- **Builtin-vs-loadable is not itself a mechanism.** Android's modem stack is 100 % builtin;
  OpenWrt's is 100 % loadable; both run the same functions. Android's **only** loadable
  module is `wlan` (`/proc/modules`), and all 15 `=m` config symbols are test/dev drivers
  (`oprofile`, `evbug`, `mmc_test`, `spidev`, `qcrypto`, `dma_test`, `gpio_*`, …) — **none on
  the modem path**.
- The one modem-path module that is **built but never loaded** on OpenWrt is
  `rpm_master_stats.ko` (no `/etc/modules.d` entry, no modalias) — an *observation* driver.
- Two promising hypotheses were **killed**: the Android/OpenWrt `CONFIG_SUSPEND` difference
  (refuted — Android ran **2 423 s, 2.69 × the 902 s clock, 0 fatals, with
  `[system] suspend success count = 0`**), and the `CONFIG_INTERCONNECT_QCOM` difference
  (inert — the msm8916 DT declares `bimc`/`pcnoc`/`snoc` but has **zero consumers**).
- `CONFIG_MSM_PM_TIMEOUT_HALT=y` on Android has help text that reads exactly like our failure
  mode ("the AP's action when Power Management times out waiting for Modem's handshake") but
  is **dead config** — no `.c`/`.h` in the 46 096-file reference tree reads it.
- **New control measurement:** Android, modem `ONLINE`, **0 fatals in 2 423 s**, AP entered
  `pc` (power collapse) 39 664 times and never suspended.
- **Correction:** the modem daemons (`rild`, `qmuxd`, `netmgrd`, `rmt_storage`, `time_daemon`,
  `qcomsysd`) **are running** — verified via `init.svc`, `ps`, and `/proc/<pid>/cmdline`. An
  earlier "they are all absent" reading was **wrong** (no tooling artifact involved).
- **The image is identified:** a **HiMI vendor engineering build**,
  `qcom/msm8916_32_512/msm8916_32_512:4.4.4/KTU84P/eng.edwin.20250828:userdebug/test-keys`,
  baseband string `HIMI_U01_MODEM_V1.0` — not a stock carrier image.

Evidence files: `android_kernel_config.gz` / `.txt`, `openwrt_kernel_config.txt`,
`android_identity_and_daemons.txt`, `android_lpm_evidence.txt`.

---

## 9. Setup findings for the A0 window (2026-09-23, second pass) — Doc 188 §12

Collected while preparing the control arm; each one would otherwise have silently corrupted
an arm, so they are recorded before the ladder starts.

**The `/system` mount posture.** Live `/proc/mounts`:

```
/dev/block/bootdevice/by-name/system   /system   ext4 rw,seclabel,relatime,discard,data=ordered
/dev/block/bootdevice/by-name/userdata /data     ext4 rw,seclabel,nosuid,nodev,relatime,...
/dev/block/bootdevice/by-name/cache    /cache    ext4 rw,seclabel,nosuid,nodev,relatime,...
/dev/block/bootdevice/by-name/modem    /firmware vfat ro,relatime,uid=1000,gid=1000,...
```

`/system` read **`rw`** when first sampled — but **CORRECTION (operator, 2026-09-23): the image
mounts `/system` `ro` at boot, like stock Android.** The `rw` state was operator-induced (a
`remount,rw` to change the ssh root password) and has since been reverted. One live mount
reading tells you the *current* flag, not the *fstab* flag. The baseband partition
`/firmware` is genuinely **`ro`** — so the modem image is already protected from us — and the
modem's NV/EFS is owned by `rmt_storage` on raw block devices, orthogonal to `/system`.

**CORRECTION — the A3 target path.** `/system/lib/libril-qc-qmi-1.so` **does not exist**.
`getprop rild.libpath` = `/system/vendor/lib/libril-qc-qmi-1.so`, and all ten QMI/RIL
libraries live under **`/system/vendor/lib/`**. `/system/lib/` holds only the generic AOSP
`libril.so` / `libreference-ril.so` / `librilutils.so`. A3's mandatory "manipulation took"
check would have found nothing to rename ⇒ **VOID, not a negative**. A3 is re-specified as a
non-destructive bind-mount overlay (`mount -o bind <empty> <lib>`), which needs no rw mount.

**The instrument is not durable.** `watch.sh` (launched `nohup … &` from a shell session)
recorded samples only from `ap_uptime` 3889.22 to 3899.69, then stopped — a **357.1 s hole**
against the next reading at 4 256.79 s. No OOM kill in `dmesg`. It died when its launching
session went away; **the earlier 20 s "nohup survives" check was too short to be evidence of
durability.** Consequence: no arm may be scored from a window whose CSV shows a gap in
`ap_uptime`.

**The frozen `pc` counter — resolved, and it is real.** Over a 45 s window, both counters read:

| counter | T0 | T1 (+45 s) |
| :-- | --: | --: |
| `/d/lpm_stats/cpu0` `wfi` | 825 907 | 832 171 (**+6 264**) |
| `/d/lpm_stats/cpu0` `standalone_pc` | 742 | 742 (frozen) |
| `/d/lpm_stats/cpu0` `pc` success | 44 110 | 44 110 (frozen) |
| `/d/lpm_stats/cpu0` `pc` total success time | 574.939090375 | 574.939090375 (frozen) |
| sysfs `state1/usage` | 903 | 903 (frozen) |
| sysfs `state2/usage` | 53 867 | 53 867 (frozen) |

**Android's AP on this boot is entering `wfi` only — no power collapse at any level.**

**Trap — the two counters are different quantities.** sysfs `state1`=903 vs
`/d/lpm_stats` `standalone_pc`=742; sysfs `state2`=53 867 vs `/d/lpm_stats` `pc`=44 110.
Always report which counter was read.

**Substantive consequence.** The earlier Android control boot ran 2 423 s / 0 fatals **with**
`pc` = 39 664; this boot has run 4 256.79 s / 0 fatals with **no** power collapse. So the
Android fatal **does not correlate with AP power collapse** — both regimes are fatal-free.
P-A6 must be interpreted against **both** baselines.

**State:** uptime 4 256.79 s (4.7 × the 902 s clock), `subsys2` = `ONLINE`, 0 fatals. **No arm
run, no manipulation applied, no `/system` file modified.**

---

## 10. The HiMI vendor stack — `himiwebserver` (Doc 188 §13)

**What it is.** `/system/bin/himiwebserver`, 2 254 232 B, **root**, single-threaded, dated
**Aug 28 2025** (= stock vendor ROM). Listens on **TCP `0.0.0.0:8000`** and **UDP
`0.0.0.0:50601`**; serves the vendor "4G UFI" admin SPA and a `POST /himiapi/json`
`{"cmdid": …}` API.

**Its sockets are only its two listeners** — **no QMI socket, no `/dev/smd*`, no `/dev/tty*`,
no unix socket to `rild`/`netmgrd`**. Its other fds are `/dev/null`, `/dev/log/{main,radio,
events,system}` (write), `/dev/__properties__`. It reads
`/dev/block/bootdevice/by-name/config` (= `mmcblk0p26`, currently all-zero).

**Auth is enforced.** Unauthenticated `getoverview`/`getsysinfo`/`getallstatus`/`getapninfo`/
`getconnectedsta` all return `{"reply":"SessionOut"}`; `GET /himiapi/json` returns
`{"reply":"error"}`. Session-gated, `Authorization: <sessionId>`, with a Login/PassWord flow
and a `logout` command. Not an unauthenticated backdoor. (Session *strength* not tested — no
credential guessing was performed.)

**Command surface:** `getoverview`, `getsysinfo`, `getallstatus`, `getapninfo`,
`setcurrentapn`, `setnewapn`, `getnetworkmode`, `setnetworkmode`, `getcondetect`,
`setcondetect`, `getdhcpinfo`, `setdhcpinfo`, `getsimset`, `setsimnum`, **`sethimiimei`**,
`gethimiusbtether`, `sethimiusbtether`, `getconnectedsta`, `selectlang`, `logout`. Plus, per
`/data/himiprot.json`: `shutdown`, `forceReset`, `forceRestart`, and the WiFi key in BASE64.

**Modem relevance — the important part.**
- The daemon is **request-driven**: it holds no modem-facing socket, so it is **not** a
  background AP-side modem traffic source.
- **Nothing is polling it** — the established-connection scan on `:8000` shows only the
  operator's own probe.
- **But the admin UI polls when a browser has it open**, and those `getallstatus` polls *do*
  reach the modem via RIL. **A browser tab left open on `192.168.100.1:8000` is a periodic
  AP-side modem traffic generator** ⇒ recorded as a **ladder confounder**.

**The RIL half is `HiMILauncher2`** (`/system/priv-app/HiMILauncher2.apk`, 7 621 694 B, also
Aug 28 2025 = stock), pid 1038, uid 1000 (system), holding `telephony-common.jar`,
`telephony-msim.jar`, `oem-services.jar`. So: launcher = RIL client; webserver = HTTP
front-end driving it via properties + the `config` partition.

**`sshd`/`telnetd`/SELinux are NOT vendor — the dates separate them.** `ro.build.date` is
2025-08-28, but `/system/xbin/telnet_login` = **Aug 14 2026**, `/system/xbin/sshd` = **Aug 15
2026**, `/system/etc/init.qcom.post_boot.sh` = **Aug 16 2026** (holds the "Master Boot
Trigger" ssh/telnet block and `setenforce 0`). `getenforce` = **Permissive**. `telnet_login`
compares against a **hardcoded plaintext password and `exec`s bash as root**, and is mode
**755** (world-readable). `telnetd` is gated on `/data/local/tmp/start_telnet` (mode 600 root,
present). `sshd_config`: `PermitRootLogin yes`, `PasswordAuthentication yes`,
**`PermitEmptyPasswords yes`**.

**Flagged, not acted on:** the hardcoded world-readable telnet password, `PermitEmptyPasswords
yes`, and Permissive SELinux. None touches the modem path, so none affects the ladder.

---

## 11. A0 RESULT — the control arm produced a fatal, and the RPM rate test falsified Doc 189

**Full write-up: Doc 190.** This is the evidence index for it.

### 11.1 The A0 control caught a real modem fatal

Window opened `ap_uptime` 5 298.59 (wall 2026-09-22T21:00:28Z), **no manipulation**. It ran
**31 177 s** with **0 gaps > 7 s** and `himi_conn = 0` throughout, and recorded **1 fatal**:

```
TRANSITION at ap_uptime=31525.35  modem_up=31518.74  fatal=1  rst=1  subsys2=OFFLINE
TRANSITION at ap_uptime=31530.82  modem_up=4.40      fatal=1  rst=2  subsys2=ONLINE
```

| quantity | value |
| :-- | :-- |
| modem brought out of reset | `[ 6.610988]` |
| fatal detected | `[31524.147021]` = wall **2026-09-23T04:17:34Z** |
| **modem uptime at the fatal** | **31 517.536 s = 8 h 45 min** |
| **reason** | **`ps_icmp6_msg.c:938`** — appears **once in the whole corpus**, and matches **no** OpenWrt fatal |
| detection → `subsys2=ONLINE` | ≈ 5.5 s |

Artifacts (all hashed):

| file | md5 |
| :-- | :-- |
| `a0_window/samples_a0.csv` (frozen copy) | `5c51dfe9b7a1dc4ec7bd6ff39ba15a25` |
| `a0_window/dmesg_fatal_1_uptime_31525.35.txt` (170 228 B, whole boot) | `544a3f98c13bde363d9f21dcda787e0c` |
| `a0_window/samples_at_fatal_1.csv` | `e4c588d9f1682d53474d128434ef7b0b` |

**The statistical correction this forces.** Prior Android "0 fatals" windows were **2 423 s**,
**4 256.79 s**, and a 594 K-frame soak — all **≤ 13.5 %** of the 31 517.5 s time-to-fatal. A 2 423 s
window has a ~7.4 % chance of containing a fatal, so zero was the *expected* result. The claim
becomes **"Android fatals at ~1 per 31 500 s vs OpenWrt's ~1 per 900 s — a ~35× rate difference"**,
and the derived claim "the fatal does not correlate with AP power collapse" is **withdrawn** as
untestable at those window lengths.

### 11.2 The traffic-matched RPM rate test — run, P-R1 FALSIFIED

Instrument: `devmem 0x29dc38` (the `rpmring` header `+0x38` byte-write counter; records = Δ/32).
Same boot, same instrument, three arms, reversibility as the control.

| arm | samples (rec/s) | n | mean | sd |
| :-- | :-- | --: | --: | --: |
| **A idle** | 237.4, 239.1, 217.1, 213.3, 227.0 | 5 | **226.8** | 10.0 |
| **B AP CPU load** (4 × `yes >/dev/null`) | 58.8, 55.0, 60.0 | 3 | **57.9** | 2.6 |
| **A′ recovered** | 235.0, 214.2, 234.6 | 3 | **227.9** | 11.9 |

* **B vs A: 3.91× drop, Welch t = 31.2**, fully reversible.
* **A vs OpenWrt's archived 224.2 rec/s: ratio 1.012** ⇒ **P-R1 FALSIFIED**.
* Doc 189's **429.9 rec/s is retracted** as an uncontrolled-window artefact.
* The mechanism is **not CPU frequency** — load and recovery arms both read
  `scaling_cur_freq = 800000`; the variable is whether the CPUs enter idle at all.

**New lead this creates:** the RPM record rate appears to be an **AP-idle meter**, not a platform
constant. This makes a **10-minute OpenWrt test** top priority on the reflash: `rpmring` idle vs
4 × CPU-loaded, same A/B/A′ design. Same 4× drop ⇒ Doc 150's "the RPM traffic is the modem's, not
the AP's" needs revision. Flat ⇒ a genuine differential.

### 11.3 The data path — same symptom as OpenWrt's stall, different defect

`rmnet1` is the **only** rmnet interface that has ever carried a byte (all others `rx=0 tx=0`).
4 058 s after the fatal, and held for 180 s:

```
t=35600s  rmnet1 rx=429730  tx=710012
t=35780s  rmnet1 rx=429730  tx=711712      <-- rx frozen, tx +600/60 s
```

…while `dumpsys connectivity` reports:

```
NetworkInfo: type: mobile[LTE], state: CONNECTED/CONNECTED, reason: linkPropertiesChanged, extra: jionet
{InterfaceName: rmnet1  LinkAddresses: [10.156.131.85/30, 2409:4071:d4f:ea49:.../64]
 Routes: [0.0.0.0/0 -> 10.156.131.86, ::/0 -> fe80::3130:7e2f:e850:7c47]
 DnsAddresses: [49.45.0.1, 2405:200:800::1]}
Mobile data state, sub0: CONNECTED     Data enabled sub0: user=true
```

| dimension | OpenWrt stall | Android now |
| :-- | :-- | :-- |
| iface up, addresses + routes | yes | **yes** |
| control plane says connected | yes | **yes** |
| data does not pass | yes | **yes** |
| **duration** | **14–17 s** per fatal | **≥ 4 058 s, ongoing** |
| **self-recovers** | **yes** (netifd ifdown/ifup) | **no** |
| **retry works?** | **yes** (stall eats one packet) | **no** (8 pings, DNS, IPv6 ping all fail) |
| layer | userspace (ModemManager + netifd) | telephony/RIL — Android has no ModemManager |

**Not the same defect.** Shared *symptom class*: control plane connected, data plane dead. And the
reframing that matters — **Android does not prevent the data stall, it hides it.** No ModemManager,
no `modem-bearer-watchdog` equivalent, and Android's own `DcTracker` stall alarm is **disabled while
dormant**, which is the state this device is in.

**NOT established:** the onset cannot be dated (`/proc/net/dev` has no timestamps). The WAN carried
**430 KB** total and `rmnet1` holds a `scope global dynamic` IPv6 address (which requires a received
RA), so the path **did** work at some point — but whether it died at the fatal or earlier is unknown,
so **the fatal is not implicated**. A carrier/plan cause cannot be excluded. n = 1.

**Decisive test, not run (perturbs the window):** `svc data disable && sleep 5 && svc data enable`,
then re-check `rx_bytes`.

### 11.4 The ICMPv6 lead

The fatal's reason names an **ICMPv6 message handler** and the device has live dual-stack cellular
(`2409:4071:d4f:ea49:.../64`, Jio, `net.dns2 = 2405:200:800::1`). `/proc/net/snmp6` over the whole
boot: `Icmp6InMsgs 86`, `Icmp6InRouterSolicits 38`, `Icmp6InNeighborSolicits 45`,
`Icmp6OutMsgs 81`, `Icmp6OutNeighborAdvertisements 43`, **`Icmp6OutMLDv2Reports 20`**,
`Icmp6OutRouterSolicits 11`, `Icmp6OutNeighborSolicits 7`.

The **out**-direction messages (RS, NS, MLDv2 Reports → `ff02::2` / `ff02::1` / solicited-node
multicast) are the ones that traverse the modem. This is the first reason string here that names a
**data-path** site rather than a power/sleep state machine, and it is consistent with the corpus's
*activity-correlated* fatal finding. It offers a **positive test** — generate ICMPv6, look for a rate
change — where a 1-per-8.75 h fatal rate is otherwise untestable. **Caveat: the reason string is a
mutable global "last fatal" record; treat this as a lead with a test, not a mechanism.**
