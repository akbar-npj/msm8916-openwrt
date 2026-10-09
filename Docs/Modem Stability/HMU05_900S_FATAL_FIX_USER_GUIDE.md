# HMU05 ~900 s Modem Fatal — User Guide: Choose Your Fix

**Status:** ✅ CURRENT — user-facing guide.
**Date:** 2026-10-07
**Applies to:** HMU05 (`hmu05,250605v0s`) — MSM8916 / Snapdragon 410
**Ledger:** `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md` §112.162, §112.165–§112.171
**See also:** `HMU05_900S_FATAL_FIX_OPTIONS_AND_DECISION.md` (the rationale record)

> **Two supported paths.** The **default** — already in the shipped image — is the
> pure-software **`himi-ok-guard`** (Path A). A **baseband binary patch** is offered as an
> **opt-in** for users who prefer it (Path B). This guide shows how to do **Path B yourself**;
> **the binary-patch code is deliberately not shipped** in this branch (see the box in §3).

---

## 0. TL;DR — pick one

| You want… | Do this |
|---|---|
| The fix, with no baseband changes | **Nothing.** Path A is already enabled in the image. Verify in §2.2. |
| The baseband-patch route | Follow **§3** (opt-in, manual, modifies your modem firmware). |
| No fix at all | Disable the guard — §2.3. |

---

## 1. What the ~900 s fatal is (one paragraph)

The modem's RF task reads **NV item 2500** (`NV_FACTORY_DATA_4_I`) and `memcmp()`s it against
the literal **`"HiMI_OK"`**. When it does **not** match, the task takes its *"get imei will
stop"* branch and waits (stock **600000 ms = 600 s**), after which the ML1 fatal that the
~900 s deadline is built on fires (`lte_ml1_common_timer.c:390`). Item 2500 is **cleared at
every modem boot**, so any AP that never rewrites it lets the modem fatal ~900 s after a warm
restart. The ~900 s = **~300 s anchor + 600 s wait** on the modem clock.

There are exactly two ways to stop it:

- **A — make the comparison pass** (rewrite `HiMI_OK` from the AP). Root cause. **Default.**
- **B — move the wait far into the future** (patch the 600000 ms constant). A deferral.

---

## 2. Path A — Pure-software `himi-ok-guard` (DEFAULT, shipped)

### 2.1 How it works

A small service (`/usr/sbin/himi-ok-guard`, started by `/etc/init.d/himi-ok`) polls the modem
over `/dev/rpmsg0` via `/usr/sbin/diag_nv`, reads NV item 2500, and — if the 128-byte item is
**all-zero** (and not already `"HiMI_OK"`) — writes `"HiMI_OK"` back. It re-checks continuously,
so it covers the cold boot *and* every SSR/crash restart, and self-heals if the modem clears the
item again. When the `memcmp` passes, **the deadline never arms**.

> **Safety (2026-10-09):** item 2500 is a generic factory-data item. The guard only ever writes
> when the item reads back **all-zero**, so it can never overwrite real factory data on a modem
> that stores something else there. Non-zero, non-`HiMI_OK` content is left untouched (one warning
> is logged). DIAG access is serialized on `/var/lock/himi-ok-diag.lock` because `/dev/rpmsg0` is
> exclusive-open.

It is gated by `board_name` to the boards that **manifest** the fatal — `*hmu05*` and `*uz801*`
(a UZ801 v3 with the same HiMI baseband needs the same fix) — so it is inert on every other board.
The gate lives in `/lib/himi-ok.sh`; it is deliberately *not* "every HiMI-baseband board", because
UFI001B (V2.0) carries the same baseband line but does not manifest the crash.

### 2.2 Verify it is running

```sh
# service state
/etc/init.d/himi-ok status
logread | grep himi-ok-guard | tail

# is item 2500 currently "HiMI_OK"?
/usr/sbin/diag_nv read 2500

# after a modem (re)start, the guard should log a write only if the item was cleared:
logread | grep "item 2500 was cleared"
```

### 2.3 Enable / disable

```sh
# disable (e.g. to test the raw failure, or if you run Path B)
uci set modem-watchdog.recovery.himi_ok_enabled='0'
uci commit modem-watchdog
/etc/init.d/himi-ok restart

# re-enable
uci set modem-watchdog.recovery.himi_ok_enabled='1'
uci commit modem-watchdog
/etc/init.d/himi-ok restart
```

Tuning: `modem-watchdog.recovery.himi_ok_interval` (seconds between checks; default **20**,
minimum 5).

**Board gate.** The guard runs on the boards that manifest the fatal (`*hmu05*`, `*uz801*`).
On another board that shares the HiMI baseband but is not in the list (e.g. `uf02`), enable it
explicitly — the all-zero safety check makes this safe:

```sh
uci set modem-watchdog.recovery.himi_ok_boards='*hmu05* *uz801* *uf02*'
uci commit modem-watchdog
/etc/init.d/himi-ok enable && /etc/init.d/himi-ok restart
```

### 2.4 Revert

Disable it as in §2.3. Nothing else was changed — Path A touches **no** baseband.

---

## 3. Path B — Baseband binary patch (OPT-IN, you do it)

> ⚠️ **This section is instructions, not shipped code.** The project **does not** add the
> patcher to this branch: there is no `hmu05-patch-modem` source, no Makefile build, and no
> boot hook in the image. If you want this route you apply it yourself, and you own the
> consequences. The retired implementation is recoverable from git history
> (`git show 57024f2^:packages/msm-firmware-dumper/src/hmu05-patch-modem.c`) if you want to
> build your own automation.

### 3.1 What it changes, and what it is (and isn't)

It rewrites **one 8-byte constant** in `modem.b16` — the RF task's event-wait timeout — from
`600000 ms` (10 min) to `0x7fffffff ms` ≈ **24.855 days**, and re-signs the firmware so MPSS
authentication still passes.

- It is a **deferral**, not a fix: the deadline still arms, just ~24.9 days out. The modem will
  still eventually hit the same failure unless the NV item is (re)written.
- It modifies your **baseband**. A mistake can leave the modem unusable until you restore
  stock firmware (see §3.7).

### 3.2 Prerequisites and backup

- Root shell on the device, `scp`/`ssh` from a Linux host.
- The host needs `dd`, `sha256sum`, `xxd`.
- **Back up first.** The firmware lives in `/lib/firmware` on the device:

```sh
mkdir -p ~/hmu05-patch && cd ~/hmu05-patch
scp root@<device-ip>:/lib/firmware/modem.b16 .
scp root@<device-ip>:/lib/firmware/modem.b01 .
scp root@<device-ip>:/lib/firmware/modem.mdt .
cp modem.b16 modem.b16.bak; cp modem.b01 modem.b01.bak; cp modem.mdt modem.mdt.bak
```

You can always regenerate the stock files with §3.7, but keep this backup anyway.

### 3.3 Patch the constant in `modem.b16`

The site is **`modem.b16` file offset `0x00AD8160`** (VA `0xc0d5f160`, base `0xc0287000`).

```sh
# 1) confirm the STOCK bytes (must print: 9f64 0000 00c0 0078)
xxd -s 0xAD8160 -l 8 modem.b16

# 2) write the patched bytes  r0 = ##0x7fffffff  (0x7fffffff ms ≈ 24.855 days)
printf '\xff\x7f\xff\x07\xe0\xc7\x00\x78' | dd of=modem.b16 bs=1 seek=$((0xAD8160)) conv=notrunc

# 3) confirm
xxd -s 0xAD8160 -l 8 modem.b16   # expect: ff7f ff07 e0c7 0078
```

### 3.4 Re-sign (`modem.b01` / `modem.mdt`)

The firmware uses a per-segment SHA-256 table. `modem.mdt = modem.b00 ‖ modem.b01` with
`len(modem.b00) = 916 (0x394)`; segment *n*'s hash lives at `modem.b01 + 0x28 + 32·n`, i.e.
`modem.mdt + 0x3bc + 32·n`. Because we changed only segment **16** (`modem.b16`):

```sh
h=$(sha256sum modem.b16 | cut -d' ' -f1)      # SHA-256 of the PATCHED modem.b16

# segment 16's hash slot in modem.b01 (0x28 + 32*16 = 0x228)
printf '%s' "$h" | xxd -r -p | dd of=modem.b01 bs=1 seek=$((0x228)) conv=notrunc

# the same bytes in modem.mdt (0x394 + 0x228 = 0x5bc)
printf '%s' "$h" | xxd -r -p | dd of=modem.mdt bs=1 seek=$((0x5bc)) conv=notrunc
```

> If you ever change a **different** segment (e.g. a `modem.b05` patch), re-hash **that**
> segment too: its slot is `0x28 + 32·n` in `modem.b01`. An un-re-hashed segment makes the
> modem fail with `MPSS authentication failed: -19`.

### 3.5 Deploy and reboot

```sh
scp modem.b16 modem.b01 modem.mdt root@<device-ip>:/lib/firmware/
ssh root@<device-ip> 'sync; reboot'
```

`/lib/firmware/DUMPED` already exists, so `msm-firmware-dumper` will **not** re-dump and
overwrite your files.

### 3.6 Verify

```sh
# on the device, after it comes back
dmesg | grep -i "fatal error"          # expect nothing
dmesg | grep "modem subsystem failure reason"   # expect nothing
mmcli -m 0 | head                      # modem present
uptime                                 # watch past 1000 s with a data bearer up
```

The decisive check is simply time: with the patch, no `lte_ml1` fatal should appear at ~900 s.

### 3.7 Revert to stock firmware

The cleanest revert re-runs the dumper, which copies the stock blobs back from the device's
own `modem` partition:

```sh
ssh root@<device-ip> 'rm -f /lib/firmware/DUMPED && reboot'
```

(`msm-firmware-dumper` sees the marker gone, re-dumps `modem.*` from the `modem` partition,
then reboots once more.) You can also re-flash the stock firmware.

### 3.8 Making the patch survive `sysupgrade`

**It does not survive by itself.** `/lib/firmware` is on the **overlay**, not in the sysupgrade
image; a `sysupgrade -n` wipes the overlay, and `msm-firmware-dumper` re-creates `/lib/firmware`
from the **stock** `modem` partition on the next boot — discarding your patch.

Two ways to handle that:

1. **Re-apply manually** after each `sysupgrade`: repeat §3.2–§3.5. Simple, no extra code.
2. **Automate a re-apply** by adding your own patcher + hooks (the project's retired mechanism):
   the patcher ran from `msm-firmware-dumper.sh` (right after the dump, before `sync`) and from
   `99-msm89xx-firstboot`, so it re-applied on every provisioning. Because `sysupgrade -n` wipes
   the overlay, an automated hook must live **in the image** — i.e. you must build your own
   firmware with the patcher included. The full mechanism, the exact hook snippets, and the
   adaptation are documented in
   [`HMU05_900S_FATAL_FIX_OPTIONS_AND_DECISION.md`](HMU05_900S_FATAL_FIX_OPTIONS_AND_DECISION.md) §3.

---

## 4. Which should you choose?

| | **A — `himi-ok-guard`** (default) | **B — baseband binary patch** |
|---|---|---|
| Nature | Root cause — the deadline never arms | Deferral — deadline ~24.9 days out |
| Touches baseband | No | Yes (and re-signs it) |
| Risk | None to the modem firmware | MPSS-auth / brick risk if mis-signed |
| Survives `sysupgrade` | Yes, inherently (in the image) | No — re-apply manually (§3.8) |
| Effort | Zero (already enabled) | Manual, per device, per reflash |
| Revert | One `uci` key | Re-dump stock (§3.7) |

**Recommendation: use Path A.** Choose Path B only if you specifically need the modem to keep
its firmware untouched by the AP at runtime, or you are experimenting — and treat it as a
time-boxed deferral, not a cure.

---

## 5. Warnings

- **Back up `/lib/firmware` before Path B.** A bad re-sign can make the modem fail to
  authenticate (`MPSS authentication failed: -19`).
- **Do not edit arbitrary `modem.bNN` files.** Some segments carry an RSA signature + cert
  chain; changing a segment you did not re-hash breaks auth even when `modem.b01` looks right.
- **Path B is not shipped and not supported by the build.** You are applying it yourself; no
  update in this branch will carry it for you.

---

## 6. Reference — exact offsets and bytes

| Item | Value |
|---|---|
| Patch site (VA) | `0xc0d5f160` (in `modem.b16`) |
| `modem.b16` VA base | `0xc0287000` |
| `modem.b16` file offset | `0x00AD8160` (= `0xc0d5f160 − 0xc0287000`) |
| Stock bytes | `9f 64 00 00 00 c0 00 78` (`r0 = ##0x927c0` = 600000 ms) |
| Patched bytes | `ff 7f ff 07 e0 c7 00 78` (`r0 = ##0x7fffffff` = 2147483647 ms ≈ 24.855 days) |
| Hash table base | `modem.b01` + `0x28`, stride `32` |
| Segment 16 slot | `modem.b01` @ `0x0228` == `modem.mdt` @ `0x05bc` |
| `modem.mdt` layout | `modem.b00 ‖ modem.b01`, `len(modem.b00) = 916 (0x394)` |

---

## 7. SOP compliance statement

- **Firmware modification (shipped):** none. Path A is AP-side userspace only; this branch
  contains no baseband patcher, no `modem.b16` build step, and no patcher hook.
- **This guide:** documents a user-applied procedure only. It does not add executable code to
  the tree. The mechanism was grounded against the stock HMU05 ground truth (live connected
  coredump + `modem_full_decompiled.c`) and the retired implementation in git history.
- **Re-signing:** the offsets and scheme were verified against the current tooling
  (`modem.mdt = modem.b00 ‖ modem.b01`, `len(modem.b00) = 916`; `modem.b01 @ 0x0228` ==
  `modem.mdt @ 0x05bc` == segment 16's SHA-256).
- **Errors/limits stated, not hidden:** Path B is a deferral, not a cure; it does not survive
  `sysupgrade` on its own.

---

## 8. Achieved vs Expected

| Goal | Expected | Achieved |
|---|---|---|
| Default fix present, no baseband change | guard in the image, HMU05-gated | ✅ `himi-ok-guard` + `init.d/himi-ok` + `97-himi-ok` (verified in the built rootfs) |
| User can choose the software path | enable/disable without reflashing | ✅ `uci set modem-watchdog.recovery.himi_ok_enabled` |
| User can choose the binary path | reproducible, exact recipe | ✅ §3: site, bytes, re-sign, deploy, verify, revert |
| Binary code kept out of the branch | no patcher source/Makefile/hook added | ✅ instructions only; code recoverable from `57024f2^` |
| Honest about limits | state deferral + sysupgrade caveat | ✅ §3.1, §3.8, §5 |
