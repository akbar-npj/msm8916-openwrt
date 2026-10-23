# Recovery: the rescue image and the recovery partition

There are **two** separate rescue mechanisms on these sticks, and they are easy
to confuse:

| | Rescue image | Recovery partition |
| :-- | :-- | :-- |
| What it is | a self-contained kernel + initramfs you boot from eMMC | a 16 MiB partition holding that image |
| Lives in | `recovery-<board>.img` in the release | partition **p17** (`recovery`) |
| How you start it | write it to `boot` with EDL/fastboot | power-on key, `reboot recovery`, or 3 failed boots |
| Needs | nothing but the image | `GPT_RECOVERY=1` **and** a patched `aboot` |

Both are built from [`msm89xx/recovery/`](../../msm89xx/recovery/) — see that
README for what is inside the image (USB NCM + ACM gadget, SSH on
`192.168.10.1`, the browser uploader, the WiFi AP `OpenWrt-Recovery`).  This
page is about the **partition** and how to get into it.

---

## 1. The partition layout

With `GPT_RECOVERY=1` the image writes 17 partitions instead of 15:

| # | Name | Size | Notes |
| - | :--- | :--- | :---- |
| 13 | `boot` | 64 MiB | kernel + DTB (lk2nd/appended) |
| 14 | `rootfs` | 128 MiB | squashfs — the cmdline pins `root=/dev/mmcblk0p14` |
| 15 | `rootfs_data` | remainder − 17 MiB | the writable overlay |
| 16 | `misc` | 1 MiB | FFBM cookie + recovery BCB + our failover counter |
| 17 | `recovery` | 16 MiB | the rescue image |

`misc` and `recovery` are appended **after** `rootfs_data` on purpose: every
partition number up to `p14` keeps its offset, so a stick that already runs
OpenWrt does not need its boot/rootfs moved.

Boards built with this layout today: **hmuf02v5**, **mf800b**, **mifib-v12**.
The other four keep the 15-partition layout (no `misc`, no `recovery`) — the
LuCI entry hides itself there and the failover stays inert.

---

## 2. Entering recovery

### a) Hold the reset button at power-on

`lk2nd` samples the board reset button before `aboot` runs and, if it is held,
reports it as **VOLUMEUP**, which is exactly what the stock bootloader treats as
"boot recovery".

| Board | Recovery key |
| :---- | :----------- |
| `mf800b`, `mifib-v12` | reset button, GPIO 102, active-low |
| `hmuf02v5` | **none** — its reset button is GPIO 37, which is also the EDL strap: holding it at power-on drops the SoC into EDL before `lk2nd` runs |

So: unplug, hold the button, plug in, keep holding ~2 s.  On the two button
boards you land in recovery; on hmuf02v5 you land in EDL instead (which is also
useful, just different).

### b) From a running system

```sh
reboot-recovery          # /sbin/reboot-recovery (reboot-edl package)
reboot recovery          # same thing, via the generic wrapper
```

In LuCI: **System → Reboot to Recovery**.  The entry only appears when
`/sys/class/block/*/uevent` reports `PARTNAME=recovery`, i.e. when the
partition actually exists.

### c) Automatically, after three failed boots

`aboot` keeps a counter in the **last 512 bytes of `misc`**:

```
magic 0x4C4B4643 ("LKFC") | count | padding to 512 bytes
```

Every normal boot increments it; `S99recovery-bootcount` (from `reboot-edl`)
clears it once userspace is up.  At `count >= 3` the bootloader clears the
cookie and boots the recovery partition instead.  A healthy system resets the
counter on every boot and never trips it — this is the only rescue path for a
board whose boot partition is broken **and** which has no recovery key.

---

## 3. Enabling recovery on an install that already runs OpenWrt

Adding the partitions is **not** a `sysupgrade` operation — the GPT has to be
rewritten.  You need:

1. A `flash.sh` run with `GPT_RECOVERY=1` in the device profile (all three
   boards above ship it that way).
2. `aboot.mbn` built with `BOOT_FAILOVER_COUNT=3` — `generate_firmware.sh`
   injects it automatically when it builds the firmware zip.
3. `recovery-<board>.img` flashed into `recovery`, and `reboot-edl` **r4 or
   newer** in the running rootfs (that is what provides
   `/etc/rc.d/S99recovery-bootcount`).

Check a live device with:

```sh
for d in /sys/class/block/*; do grep -l PARTNAME=recovery $d/uevent 2>/dev/null; done
ls -l /etc/rc.d/S99recovery-bootcount
strings /dev/mmcblk0p12 2>/dev/null | grep -c "boot failover"   # aboot patched?
```

---

## 4. Traps

* **`lk2nd` must delay before sampling the key.** The first read after the TLMM
  config returns 0, which an active-low test reports as "pressed" — without the
  10 ms `udelay()` every boot would land in recovery.
* **The failover counter must be cleared on hand-over.** If it is not, every
  later boot is forced into recovery even after the system is repaired, because
  the only thing that could clear it is the userspace hook a broken system never
  reaches.
* **A recovery partition without the patched `aboot` is just dead space.** The
  key and the counter both live in `aboot`; `lk2nd` alone cannot boot `p17`.
* **`sysupgrade -n` keeps `misc`/`recovery`** (they are not touched by an
  upgrade) but wipes the overlay — the boot counter starts from zero again,
  which is harmless.

---

## 5. See also

* [`msm89xx/recovery/README.md`](../../msm89xx/recovery/README.md) — the image
  itself: what `/init` starts, how to rebuild the frozen binaries, the web UI.
* [`Docs/EDL/`](../EDL/) — getting into EDL, and the firehose quirks of these
  sticks.
* [`Docs/POWER_BUTTON.md`](../POWER_BUTTON.md) — the button that triggers the
  recovery key on the two button boards.