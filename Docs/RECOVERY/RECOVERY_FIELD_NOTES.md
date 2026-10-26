# Recovery image — what broke, and why it is built this way

A field log of every failure hit while getting the rescue image to boot, serve
DHCP and raise its AP on real hardware. Each entry is **symptom → what it
looked like → what it actually was → fix → how to prove it**. The reference
material lives in [msm89xx/recovery/README.md](../../msm89xx/recovery/README.md);
this page is the reasoning behind it.

The short version: almost every symptom on this page came from **one** decision
— building the recovery image on a second, private kernel instead of the one the
device actually runs.

---

## 0. The decision that caused most of this

The original recipe compiled a dedicated recovery kernel from
msm89xx/config-6.12-recovery: **devtmpfs on**, **wcn36xx built in**, **no
modules**. It worked, and it was wrong. The rescue image then differed from the
system it was rescuing in exactly the two places that matter:

| | recovery kernel (old) | production kernel |
|---|---|---|
| CONFIG_DEVTMPFS | on | **off** (OpenWrt uses mdev) |
| wireless | built in | **modules** |

So the image was validated against a kernel that no user runs. The moment the
recipe changed to pack the **production kernel** and only swap the ramdisk, both
differences became real failures — and they are the two biggest entries below.

The current recipe is: take the board's own squashfs-boot.img, keep its kernel
(gzipped Image + board DTB), replace the ramdisk with a gzipped cpio of
msm89xx/recovery/irfs, drop root= from the cmdline. No second kernel exists any
more, so this class of drift cannot come back.

---

## 1. Booted fine, but the host saw nothing at all

**Symptom.** The board powered up, the LED went steady, and the host got no
/dev/ttyACM0, no new network interface, no DHCP, no AP. It looked hung.

**What it looked like.** A broken boot image, a corrupt initramfs, or the newly
added AP+STA code deadlocking /init before it reached the gadget.

**What it actually was.** All of those were wrong, and one measurement killed
them all: putting a static address on the USB NIC and running
ssh root@192.168.10.1 **worked**. So /init was running to completion, the
network stack was up, dropbear was serving — and only the pieces that need
device nodes were missing.

**Root cause.** The production kernel has **no devtmpfs**. "mount -t devtmpfs
devtmpfs /dev" fails, /dev stays empty, and everything downstream fails
*silently*:

* dnsmasq exits with *"failed to seed the random number generator"* because
  there is no /dev/urandom — so there is **no DHCP on USB or WiFi**;
* the console loop's ">/dev/ttyGS0" **creates a regular file**, so the shell
  never attaches and the console stays dead;
* every "2>/dev/null" in the script creates a file named null.

Three unrelated-looking failures, one cause.

**Fix.**

~~~sh
if ! mount -t devtmpfs devtmpfs /dev 2>/dev/null; then
	mount -t tmpfs tmpfs /dev 2>/dev/null
	mdev -s 2>/dev/null
fi
~~~

plus a second "mdev -s" **after** the gadget exists — /dev/ttyGS0 and the NCM
netdev only appear at that point.

**Prove it.** "ls /dev" shows console, null, urandom; "cat /tmp/dnsmasq.err" is
empty; the host gets a lease.

---

## 2. DHCP worked, but /dev/mmcblk0p17 did not exist

**Symptom.** "dd: can't open '/dev/mmcblk0p17': No such file or directory", and
the web UI's "write a partition" failed. /proc/partitions listed all 17
partitions, so the flash was clearly there.

**Cause.** The eMMC is probed about a second **after** /init starts. The
"mdev -s" near the top of the script ran before /sys/block/mmcblk0 existed, so
no block nodes were ever created.

**Fix.** Wait for /sys/block/mmcblk0, then rescan:

~~~sh
i=0
while [ $i -lt 20 ]; do
	[ -d /sys/block/mmcblk0 ] && break
	i=$((i+1)); sleep 1
done
mdev -s 2>/dev/null
~~~

**Prove it.** "ls /dev | grep -c mmcblk" → 21.

> The same trap bites when flashing *from* an older recovery image: run
> "mdev -s" once by hand and the node appears.

---

## 3. No wlan0 — twice, for two different reasons

### 3a. The wireless stack is modular

**Symptom.** "iw dev" → "nl80211 not found". No /lib/modules at all.

**Cause.** The production kernel builds wcn36xx, mac80211, cfg80211 and compat
as modules, and the initramfs has no kmodloader. The old recovery kernel had
them built in, which is why nobody noticed.

**Fix.** build-recovery.sh copies eight .ko files out of
root-msm89xx/lib/modules/<ver>/ (so they always match the packed kernel) and
/init insmods them in dependency order:

~~~
wcn36xx        -> mac80211 -> cfg80211 -> compat
qcom_wcnss_pil -> qcom_common, qcom_pil_info, mdt_loader   (WCNSS firmware)
~~~

**Prove it.** "cat /proc/modules" lists all eight; dmesg shows
"Booting fw image wcnss.mdt" and "wcn36xx: mac address: …".

### 3b. phy0 exists, wlan0 does not

**Symptom.** /sys/class/ieee80211/phy0 exists, "iw phy" prints a fully capable
radio (AP mode, HT20/HT40), but "iw dev" is empty and hostapd has no interface.

**Cause.** The **backports** mac80211 that the production kernel uses does not
create a default netdev on registration. The in-tree 6.12 mac80211 (old recovery
kernel) did.

**Fix.** Create it explicitly — "__ap", not "AP":

~~~sh
[ -d /sys/class/net/wlan0 ] || iw phy phy0 interface add wlan0 type __ap
~~~

---

## 4. Connecting the box killed the host's internet

**Symptom.** *"If I connect USB, it kills my internet."* Same over the AP.

**Cause.** dnsmasq handed out "dhcp-option=3,192.168.10.1" (default router) and
"dhcp-option=6,192.168.10.1" (DNS). The box has no WAN, so every client
replaced its default route **and** its resolver with a dead link the moment it
attached. Routing looked fine locally, which is why it reads as "the dongle
breaks my network" rather than "the dongle has no internet".

**Fix.** Hand out address + prefix only, and run dnsmasq as a pure DHCP server
(port=0, no options 3/6). The client keeps its own route and resolver and still
reaches 192.168.10.1 over the subnet route.

The **failsafe** gadget had the identical bug (--dhcp-option=3,6 in
lib/preinit/35_failsafe_usb) and got the identical fix.

**Rule.** A rescue link is never a gateway. Do not re-add options 3 or 6.

---

## 5. A flash that reported success and did not happen

**Symptom.** dd reported success, the immediate readback matched the new hash —
and the next boot still ran the **previous** image. Proved by comparing
"md5sum /init" on the box with the source file: they differed, and the box had
the older one.

**Cause.** "dd if=… of=/dev/mmcblk0p17 bs=1M" returns as soon as the data is in
the page cache. "sync" only *starts* writeback; the reboot that followed reset
the controller with pages still dirty. The tell was the speed:
**0.085 s / 134 MB/s**, against the honest **1.25 s / 9 MB/s** of a real write.

**Fix.** conv=fsync, then verify against the flash rather than the cache:

~~~sh
dd if=/tmp/r.img of=/dev/mmcblk0p17 bs=1M conv=fsync; sync
echo 3 > /proc/sys/vm/drop_caches
dd if=/dev/mmcblk0p17 bs=<image size> count=1 2>/dev/null | sha256sum
~~~

Reading back **without** dropping the cache proves nothing — it reads the cache
you just filled.

---

## 6. reboot-recovery hung

**Symptom.** The command never returned and the board never rebooted.

**Cause.** The graceful path (ifdown -a, killall5, umount -a) blocks when a
modem bearer is up.

**Fix.** Always "reboot-recovery -f". From inside recovery the equivalent is
"reboot-mode-raw recovery".

---

## 7. hostapd failed with an empty log

**Symptom.** No AP, and /tmp/hostapd.log was zero bytes — nothing to go on.

**Cause.** "hostapd -B" daemonises, which closes stderr. A startup failure then
leaves no trace at all.

**Fix.** Run it in the **foreground under a restart loop**, so the log always has
something. Also wait ~3 s after wlan0 appears: starting hostapd inside wcn36xx's
firmware bring-up window enables the AP and tears it down again ~200 ms later.

---

## 8. DHCP over WiFi never worked with udhcpd

**Symptom.** A station associated, but never got a lease; SSH and TCP were fine.

**Cause.** wcn36xx AP mode **drops group-addressed frames on an encrypted BSS**.
Measured with WPA2-PSK: 20/20 unicast frames arrived, ~10 broadcast DHCP OFFERs
arrived **0** times. busybox udhcpd always broadcasts its reply, so it can never
serve a WiFi client.

**Fix.** dnsmasq, which unicasts the reply to the client's MAC. Consequence worth
remembering: anything else that needs broadcast/multicast over the AP (IPv6 RA,
mDNS) will not work either. ARP replies, TCP and SSH are unicast and do.

---

## 9. The AP ran 802.11g at ~2 Mbit/s

**Symptom.** A phone 1 m away got 2 Mbit/s tx, long preamble, and multi-second
ping RTTs.

**Cause.** "hw_mode=g" alone. No "ieee80211n" means no HT; no "wmm_enabled" means
no WMM information element, and an HT AP without WMM makes stations fall back to
legacy rates anyway.

**Fix.** "ieee80211n=1", "wmm_enabled=1", "ht_capab=[HT20][SHORT-GI-20]" (HT20
keeps it inside the pinned channel). Verified: the phone associates at
**MCS 4 / 39 Mbit/s** with "WMM/WME: yes".

Note the ping RTTs stayed bimodal (4 ms awake, 1.7–3.6 s dozing) — that is the
*phone's* power save, not the AP.

---

## 10. AP+STA was removed

The image briefly ran the AP **and** a station vif that joined your LAN from the
web UI. It was the fragile half of the wireless path — two vifs on one radio,
wpa_supplicant, a DHCP hook, a CGI, a UI card, and the whole -EBUSY scan-wedge
family of traps — for a benefit the AP already provides: reachability without a
cable. Removed in 5dfc8e9; the code is at a43efba if it is ever wanted back.

---

## Toolbox: diagnosing a recovery image that misbehaves

| Question | How |
|---|---|
| Is /init even running? | LED pattern; "cat /proc/uptime" over SSH |
| Host side missing, or the whole box? | put a **static** 192.168.10.2/24 on the USB NIC and "ssh root@192.168.10.1" — this works even with no DHCP |
| Are the device nodes there? | "ls /dev" — need console, null, urandom, ttyGS0, mmcblk0p* |
| Is the gadget bound? | "ls /sys/class/udc", "cat /sys/kernel/config/usb_gadget/g1/UDC" |
| Did dnsmasq start? | "cat /tmp/dnsmasq.err", "cat /tmp/dnsmasq.leases" |
| Did the modules load? | "cat /proc/modules", "cat /tmp/modules.err", "dmesg | grep -i wcn" |
| Is there a radio? | "ls /sys/class/ieee80211", "iw phy", "iw dev" |
| Is the running /init the one I built? | "md5sum /init" vs the source file |
| Did the flash really land? | "echo 3 > /proc/sys/vm/drop_caches", then read the partition back |
| What is inside the image I built? | see below |

Inspect a built image on the host, without a device:

~~~sh
python3 - <<'PY'
import struct, gzip, subprocess
d = open("msm89xx/recovery/<board>.img", "rb").read()
f = struct.unpack("<8s10I", d[:48])
ksize, page, rsize = f[1], f[8], f[3]
rs = page + ((ksize + page - 1) // page) * page
c = gzip.decompress(d[rs:rs + rsize])
print(subprocess.run(["cpio", "-it"], input=c, capture_output=True).stdout.decode())
PY
~~~

## Rules that must not be broken

1. **Pack the production kernel.** A second kernel is a second set of bugs, and
   they only show up on the device you were trying to rescue.
2. **/init must build /dev itself** (tmpfs + mdev -s), and rescan after the
   gadget **and** after the eMMC is probed.
3. **A rescue link is not a gateway** — no DHCP router, no DHCP DNS.
4. **conv=fsync on any partition write**, and verify after dropping the cache.
5. **hostapd in the foreground**, channel=6 pinned, ieee80211n + wmm_enabled set,
   and the AP interface created explicitly with "type __ap".
6. **Never exec a redirection in /init** — a redirection failure kills PID 1.
7. **USB is the rescue path.** Everything wireless is best-effort and must never
   be able to block the console, DHCP or SSH.
