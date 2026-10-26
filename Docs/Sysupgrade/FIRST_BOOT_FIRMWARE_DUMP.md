# First boot after a clean install: the firmware dump (and the 45 s)

**Short version.** The squashfs ships *no* modem/WiFi firmware. All of it lives
on the overlay and is extracted there by an init script on the first boot after
a clean install. That first boot therefore has no firmware for ~14 s, the kernel
logs a failed load, and the box only reaches full service at ~39 s. Every later
boot takes ~17 s. Nothing is broken.

---

## 1. Where the firmware actually lives

Measured on **mifib-v12**, 25.12.6-r1, kernel 6.12.112, after sysupgrade -n:

~~~
/rom/lib/firmware          1 entry    (regulatory.db)
/overlay/upper/lib/firmware  47 entries, 47.3 MB
                            (wcnss.mdt + segments, mba.mbn, modem.mdt + segments,
                             MCFG_SW.MBN, cmnlib/keymaste/tz, wlan/prima NV, DUMPED)
~~~

The read-only squashfs (mounted at /rom once the overlay is up) contains only
the regulatory database. Everything the modem and the WiFi chip need is on the
writable overlay, because it is device-specific and must not be baked into a
generic image.

## 2. Who puts it there

/etc/init.d/msm-firmware-dumper (S95, i.e. late in the boot):

~~~
FLAG=/lib/firmware/DUMPED
START=95
FLAG=/lib/firmware/DUMPED

start() {
    [ -f "$FLAG" ] && return 0          # already dumped: nothing to do
    # wait (max 30 s) for /overlay/upper or a writable /lib/firmware
    if FLAG="$FLAG" /usr/bin/msm-firmware-dumper; then
        sync
        /etc/init.d/rmtfs restart
        for rproc in /sys/class/remoteproc/remoteproc*; do
            echo start > "$rproc/state"     # <-- re-trigger both remoteprocs
        done
        # if the WiFi radio has no path yet, rebuild /etc/config/wireless
        /sbin/wifi config; /sbin/wifi reload
    else
        logger -t "$NAME" "Firmware dump FAILED"   # no marker written
        return 1
    fi
}
~~~

So on a fresh install the sequence is: kernel tries to boot the remoteprocs with
no firmware available -> the dump runs -> the script starts them itself.

## 3. Measured timeline

| event | clean install, first boot | every later boot |
|---|---|---|
| overlay inode count at e2fsck | 12 | 361 |
| wcnss.mdt load | failed -2 @ 14.18 s, retry @ 38.31 s | OK @ 13.75 s |
| mba.mbn load | failed -2 @ 17.85 s, retry @ 38.32 s | OK @ 15.86 s |
| both remoteprocs up | 38.98 s | 16.70 s |
| wcn36xx firmware API line | ~44.7 s | 21.1 s |

Reproduced on two consecutive reboots (13.75 s and 13.73 s), so ~17 s is the
steady state and the 45 s is a one-off.

## 4. The scary messages are expected on that first boot

~~~
remoteproc remoteproc1: Direct firmware load for wcnss.mdt failed with error -2
remoteproc remoteproc1: request_firmware failed: -2
remoteproc remoteproc0: Direct firmware load for mba.mbn failed with error -2
remoteproc remoteproc0: Boot failed: -2
~~~

-2 is ENOENT: at 14 s the file genuinely does not exist yet. The dumper fills
/lib/firmware at ~30-38 s and then starts the processors explicitly, which is
why they come up right after. If you see these lines on a *later* boot, that is
different - it means the overlay lost its firmware (see section 6).

## 5. What counts as a clean install

| operation | overlay | firmware |
|---|---|---|
| sysupgrade (no -n) | preserved | preserved, next boot is fast |
| sysupgrade -n | wiped | **gone** - re-dumped on the next boot |
| first flash / repartition | empty | **gone** - re-dumped on the next boot |

So a config-preserving upgrade does not pay the 45 s; a clean install does, once.

## 6. How to check the current state

~~~
ls /lib/firmware | wc -l                  # ~48 when dumped, ~1 when not
ls -l /lib/firmware/DUMPED                # marker; absent = next boot will dump
ls /rom/lib/firmware | wc -l              # 1 (regulatory.db) - by design
logread | grep -i "firmware dump"         # what the dumper did this boot
dmesg | grep -E "Direct firmware load|Booting fw image|now up"
~~~

If the dump fails (e.g. the modem partitions are unreadable), the script logs
"Firmware dump FAILED", does not write the marker, and the box boots with no
modem and no WiFi - the recovery image is the way back.

## 7. Why it was left as-is

The dump could be moved earlier (START=95 -> ~S20) to overlap with the rest of
boot and save 10-15 s on a clean install. It was not, because:

* it costs 20 extra seconds **once per clean flash**, and
* moving it earlier means racing the overlay mount it explicitly waits for.

Shipping the 47 MB in the squashfs is the other option and is worse: every
image grows, and the firmware is per-device.

**Do not judge modem or WiFi behaviour on the first boot after a clean
install** - for the first ~40 s there is no firmware at all.

---

## See also

- [`Docs/Sysupgrade/README.md`](README.md) - flashing methods and recovery paths.
- [`Docs/RECOVERY/RECOVERY_FIELD_NOTES.md`](../RECOVERY/RECOVERY_FIELD_NOTES.md) - the recovery image, and what to do when the dump fails and the box has no modem/WiFi.
- [`Docs/Failsafe/README.md`](../Failsafe/README.md) - the 7 s button window if the box never comes back.
