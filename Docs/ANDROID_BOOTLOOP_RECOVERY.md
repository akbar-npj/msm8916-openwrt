# Recovering an MSM8916 Android Dongle from a Bootloop

**Applies to:** headless MSM8916 (Snapdragon 410) USB dongles / MiFi running
stock Android (4.4/5.x) — including the **MIFID / MF800B-class** units. There is
no display and usually no serial console; USB is the only channel.

**What this guide covers**

1. Catching a bootlooping device long enough to command it.
2. Getting to fastboot.
3. Getting to **EDL (9008)** when fastboot cannot write.
4. Restoring a partition from a dump, and verifying it.
5. Why a **locked bootloader does not stop an OpenWrt flash**.
6. How to get **back to EDL if OpenWrt itself bootloops**.

---

## 0. Know the USB IDs

| USB ID | Mode | Meaning |
| :--- | :--- | :--- |
| `05c6:9008` | **EDL / PBL** | Emergency Download. Firehose writes raw partitions. **The Android unlock state does not apply here.** |
| `05c6:9006` | DLOAD | SBL1 mass-storage / dump mode. |
| `05c6:9091` | Android diag | The kernel is up: adb + diag. Often the *only* window in a bootloop. |
| `05c6:9024` | Android | Normal Android composite (adb / MTP). |
| `18d1:d00d` | **Fastboot** | ABOOT fastboot (some units report `05c6:9025`). |

Watch the device while it loops:

~~~sh
watch -n0.5 'lsusb | grep -E "05c6|18d1"'
~~~

- Cycles `9091` → disappears → `9091`: a bootloop that reaches the kernel. Go to §1.
- Never appears, or only a brief `9008`: go straight to hardware EDL (§3.3).

---

## 1. Catch ADB in the bootloop

Even a bootlooping Android usually brings adbd up for a few seconds. Catch it:

~~~sh
adb devices -l
# 10d200c3   device   usb:1-1 product:MIFID model:MIFID device:MIFID
~~~

If `adb shell` fails with `exec '/system/bin/sh' failed: Permission denied`,
that is normal mid-boot — the shell is not ready yet. **You do not need a shell**
to reboot; the adb daemon handles `reboot` itself:

~~~sh
adb -s <serial> reboot bootloader     # -> fastboot
adb -s <serial> reboot edl            # -> EDL (9008), if the kernel supports it
~~~

> If the device is `unauthorized`, the host key is not accepted. On
> `userdebug` / `test-keys` builds it is usually already authorized. If not,
> you need physical access to accept the prompt, or go to hardware EDL.

---

## 2. Fastboot

~~~sh
fastboot devices
fastboot -s <serial> oem device-info
~~~

Typical output on a locked stock unit:

~~~text
(bootloader)  Device tampered: false
(bootloader)  Device unlocked: false
~~~

- **Unlocked** → fastboot is your recovery path: `fastboot flash boot …`,
  `fastboot flash system …`, then `fastboot reboot`.
- **Locked** → `fastboot flash` fails (`FAILED (remote: 'flash write failure')`
  or `not allowed in locked state`). Use EDL instead.

Minimal ABOOTs answer only a few variables. `getvar all` may be empty; query
individually (`getvar product`, `getvar serialno`, `getvar partition-size:system`).
`fastboot fetch` is usually unsupported, so you cannot dump a partition from
fastboot.

---

## 3. EDL (9008)

### 3.1 From Android (preferred)

~~~sh
adb -s <serial> reboot edl
~~~

This works when the stock kernel has the IMEM/TCSR EDL path (the MIFID unit
does). The host then sees `05c6:9008`.

### 3.2 From fastboot

~~~sh
fastboot -s <serial> oem edl
~~~

Often `FAILED (remote: 'unknown command')` on minimal ABOOTs. If it fails, the
device stays in fastboot; nothing is lost.

### 3.3 Hardware (the guaranteed way)

If neither of the above works — or if the device never enumerates — you must
force EDL electrically. On MSM8916 this is normally one of:

- short the **EDL test points** to ground while plugging in / powering on;
- hold the **EDL / recovery button** (if fitted);
- the **D+ / test-point** trick used by the service manual.

**Identify the test points before you flash anything.** This is the only entry
method that does not depend on software booting.

---

## 4. Restore a partition with `edl.py`

You need a **firehose loader** for MSM8916 (e.g.
`prog_emmc_firehose_8916_*.mbn`). A wrong loader fails the Sahara handshake;
it does **not** flash anything.

~~~sh
# 1. Handshake and read the partition table (read-only)
edl.py printgpt --loader=prog_emmc_firehose_8916_xxx.mbn

# 2. Confirm the partition matches the image size
#    system: Offset 0x1618c000, Length 0x32000000   == 838,860,800 bytes

# 3. Write the raw partition image
edl.py w system system.bin --loader=prog_emmc_firehose_8916_xxx.mbn

# 4. Verify: read back head and tail, compare md5
edl.py rs 724064 2048 /tmp/rb_head.bin --loader=prog_emmc_firehose_8916_xxx.mbn
edl.py rs $((724064+0x190000-2048)) 2048 /tmp/rb_tail.bin --loader=prog_emmc_firehose_8916_xxx.mbn
dd if=system.bin bs=512 count=2048 status=none | md5sum
md5sum /tmp/rb_head.bin

# 5. Reset out of EDL and boot
edl.py reset --loader=prog_emmc_firehose_8916_xxx.mbn --resetmode=reset
~~~

> The loader reports a *sparse* payload size, not the expanded image size; the
> `100.0%` and the final "Wrote … to sector N" are the real success signals.
> Always verify with a read-back: a loose cable produces
> `flash write failure` / short writes that still look plausible in the log.

---

## 5. Worked example — MIFID, 2026-10-21

A stock MIFID unit bootlooped. It enumerated as `05c6:9091` with adb alive.

~~~text
adb devices -l            -> 10d200c3  device  product:MIFID model:MIFID
adb reboot bootloader     -> 18d1:d00d (fastboot)
fastboot oem device-info  -> Device unlocked: false
fastboot flash system …   -> FAILED (remote: 'flash write failure')
adb reboot edl            -> 05c6:9008
edl.py printgpt           -> system: Offset 0x1618c000, Length 0x32000000
edl.py w system …         -> 100.0%, "Wrote … to sector 724064"
edl.py rs head/tail       -> md5 match with the image
edl.py reset              -> boots, adb stable, bootloop gone
~~~

The system image was a raw ext4 dump of exactly `0x32000000` bytes.

---

## 6. The bootloader is locked — how do we flash OpenWrt?

**Use EDL. The lock does not apply.**

The lock is enforced by **ABOOT** (the Android bootloader) when it receives a
`fastboot flash` command. EDL is a lower layer: the MSM8916 **PBL / Sahara /
firehose** path runs below ABOOT and writes raw eMMC sectors. The
`is_unlocked=false` flag is not consulted there.

This is exactly how this project already flashes OpenWrt:
`msm89xx/image/flash.sh --mode migrate` does **everything via EDL** —
repartition (GPT), bootloader (`aboot`, `hyp`, `rpm`, `sbl1`, `tz`),
`boot`, and `rootfs`. A locked stock bootloader is therefore not a blocker
for the initial install.

Caveats:

- The **firehose loader must match the SoC / secure-boot state**. A loader that
  fails the handshake simply does not start; it cannot half-write.
- Keep a **full dump** before the first `migrate`. The script backs up the seven
  radio/calibration partitions (`fsc fsg modem modemst1 modemst2 persist sec`);
  also keep `aboot`, `sbl1`, `tz`, `hyp`, `rpm`, `boot`, `system`.
- Once the OpenWrt layout is in place, use `flash.sh --mode update`
  (`boot` + `rootfs` only) — it leaves the bootloader and the radio
  partitions alone.

---

## 7. OpenWrt bootloops — how do we get back to EDL?

There are four routes, in decreasing order of reliability:

### 7.1 Hardware EDL — the only guaranteed one

Short the EDL test points (or hold the EDL button) while powering on. This works
even if the kernel, DTB, or userspace is completely broken. **Identify these
before flashing OpenWrt.**

### 7.2 Fastboot — only if the bootloader is unlocked and reachable

If `aboot` is unlocked, you do not need EDL at all: reflash a known-good
`boot` (and `rootfs`) with `fastboot flash`. Keep the bootloader unlocked
for this reason, and **never flash `aboot` / `sbl1` unless you have a full
dump and a hardware EDL fallback.**

On a locked ABOOT, fastboot will not write; and minimal ABOOTs often do not
implement `oem edl`. So fastboot is not a recovery path on a locked unit.

### 7.3 `reboot-edl` from a booting OpenWrt

The project ships `reboot-edl` (patch 813 + `packages/reboot-edl`). If
OpenWrt reaches userspace, this writes the IMEM/TCSR cookies and warm-resets
into EDL. It is the easiest route — but it requires OpenWrt to boot.

### 7.4 Early-boot USB gadget ("mimic Android")

Android survives a bootloop long enough to expose adb/diag, which is what let us
run `adb reboot edl`. OpenWrt can do the same: the **failsafe** preinit hook
brings up a USB gadget (CDC-NCM + ACM) at `192.168.10.1` and a minimal shell
before the normal init. You can add a `reboot edl` action to that window.

**But this only works if the kernel and DTB boot.** The most common OpenWrt
bootloop is a bad `boot` image / DTB, in which case preinit never runs. So this
is a **convenience**, not a guarantee — do not rely on it as your only way back.

> The OpenWrt GPT used by this project has **no `recovery` partition**
> (`aboot`, `boot`, `rootfs`, `rootfs_data`), so a "boot the recovery
> partition on failure" fallback is not available in this layout.

---

## 8. The recovery contract — do this before flashing

- [ ] **Dump everything** you are about to overwrite (`aboot sbl1 tz hyp rpm
      boot system` + the seven radio partitions) and store it off-device.
- [ ] **Prove EDL entry works** on the stock Android: `adb reboot edl` →
      `05c6:9008` → `edl.py printgpt` → `edl.py reset`.
- [ ] **Identify the hardware EDL test points** (or confirm a button combo).
- [ ] Keep the matching **firehose loader** next to the dumps.
- [ ] After the first working OpenWrt boot, test `reboot-edl` and note the
      result.
- [ ] Prefer `flash.sh --mode update` for later changes; `migrate` is
      one-time and destructive.

If all of the above are true, a bootloop is recoverable in minutes. If the only
entry method is "hope OpenWrt boots", you do not have a recovery path yet.

---

## Appendix — MIFID / MF800B-class findings (2026-10-21)

Captured from a stock MIFID unit (`ro.product.device=MIFID`, Android 4.4.4,
`eng.edwin.20240806`):

- The board is **MF800B-class**: patch `807`'s LED/battery/button GPIOs match
  exactly (`wifistatus` 73, `4g_2` 84, `4g_1` 85, `4g_type` 72, `batl2` 0,
  `bat1` 1, `key_freset` 102).
- The **SIM block is missing from `807`**. From
  `/sys/kernel/debug/gpio` (TLMM base 902 → pin = number − 902):

  | Function | Android node | TLMM pin | State |
  | :--- | :--- | ---: | :--- |
  | Physical SIM enable | `esim1_en` | **119** | **out HIGH** |
  | SIM 2 enable | `esim2_en` | **112** | out LOW |
  | SIM 3 enable | `esim3_en` | 12 | out LOW |
  | SIM hot-plug | `sim_hotplug` | **114** | out LOW |

  (`esim2_en` is 112 here; HMU05 uses 14. `esim1_en` / `sim_hotplug`
  match HMU05's 119/114.)

- Modem: `gsm.version.baseband = HM02_MODEM_V1.2`; the 64 MB `modem`
  partition is FAT16 and contains build
  `MPSS.DPM.2.0.2.c1-00178-M8936FAAAANUZM-1_20161104_023832` (**V2.0**).
  It contains **zero** `HiMI_OK` strings ⇒ the `himi-ok` guard is **not
  needed**, and the board belongs on the normal (non-`a2-pin`) path, like
  UFI001B/MF800B.
