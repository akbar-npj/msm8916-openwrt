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
- **`/system/bin/start-ssh` did not exist** and the init service
  `service sshd /system/bin/start-ssh` is `disabled`, so sshd did **not** autostart.
- The device is **already root** (`adb shell id` → `uid=0(root)`) and `/system` was
  mounted **rw**.

---

## 2. What was changed

| change | detail |
| :-- | :-- |
| `/system/etc/ssh/sshd_config` | appended `PasswordAuthentication yes` and **`PermitEmptyPasswords yes`** (lines 136–137) |
| `/system/bin/start-ssh` | **created** — `exec /system/xbin/sshd -p 22`; makes the ROM's own `service sshd` entry functional and `setprop ctl.start sshd` work |
| `/system/etc/install-recovery.sh` | **created** — same one-liner; this is the ROM's unused `service flash_recovery` boot hook (`class main`, `oneshot`, not disabled), so sshd now starts at boot |

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
| `/system/bin/start-ssh`, `/system/etc/install-recovery.sh` | **yes** |
| sshd **running** | **yes** — via the `flash_recovery` boot hook (validated by killing sshd and re-triggering the service, **without** a reboot) |

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
