# Stock Android Live Session — Findings for OpenWrt Comparison

**Date:** 2026-09-20
**Purpose:** Establish the stock-Android baseline against which the OpenWrt 900 s failure can be
compared. This is the *differential* the project's dual-firmware protocol requires: same baseband,
different AP stack.
**Status:** firmware identity **PROVEN**; DIAG capture on Android **WORKING** — first capture in
`captures/android_test4.qmdl` (266,862 B), format fully derived from the kernel driver (§6, §7).

---

## 1. Device identification

| Property | Value | Source |
|---|---|---|
| `ro.build.version.release` | `4.4.4` | `getprop` |
| `ro.build.display.id` | `msm8916_32_512-userdebug 4.4.4 KTU84P eng.edwin.20250828 test-keys` | `getprop` |
| `ro.product.model` | `UFI` | `getprop` |
| `ro.board.platform` | `msm8916` | `getprop` |
| adb serial | `c2b9103c` (usb:1-1) | `adb devices -l` |

**Two things matter here:**

1. **The build is `userdebug`, not `user`.** `setprop service.adb.root 1; busybox killall adbd`
   yields `uid=0(root) gid=0(root) context=u:r:shell:s0` — verified. No SuperSU/Magisk needed.
   Note the SELinux context stays `u:r:shell:s0`; this is a *root UID with shell domain*, which is
   enough for file reads and process control but is **not** full `su` domain. Anything blocked by
   SELinux policy (not by UID) will still be blocked.
2. **The build is dated `eng.edwin.20250828`** — i.e. this is a *rebuilt* Android, not the 2015
   factory image. The firmware partition files are dated **2025-11-26/27**, i.e. newer than the
   build. Do not assume this image is bit-identical to whatever Android the earlier
   `Stock_Android_Analysis` docs were captured from (those are dated Sep 4).

**Uptime at session start:** 167.68 s (kernel), modem up since kernel 6.64 s → modem uptime
≈ 161 s. See §3.

---

## 2. FIRMWARE IDENTITY — PROVEN IDENTICAL TO OPENWRT'S BASEBAND

This is the single most important result of the session.

### 2.1 Method

Per the project's **dual-firmware comparative protocol**, every modem image file was hashed on the
device and compared against the local stock dump used to build the OpenWrt firmware.

```
Android : /firmware/image/{modem.b*, mba.mbn}
Local   : GitIgnore/MelbonWhiteStock_Dump/modem_extracted/image/{modem.b*, mba.mbn}
```

### 2.2 Result

```
segments compared: 21
RESULT: ALL SEGMENTS IDENTICAL — modem firmware is byte-for-byte the same as the stock dump
```

`modem.mdt` specifically:

```
sha256 = 3d23e145637689d79f44f72349b24b61f6f50b884a6a2bedae2542637cccc4c0
         ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
matches the stock HMU05 hash recorded in 900S_CRASH_LPR_FRAMEWORK_RE.md §7.4
```

### 2.3 Full hash table (Android, sha256)

| File | sha256 |
|---|---|
| `mba.mbn` | `f0e207485cf63af953d7c0cfdf69e770b06ad3a408f86a297102532b5d2ad764` |
| `modem.b00` | `bd49db6aadfdbd89ffe83dd7bd1c6c2cbcd91dc8b0c27c7918ca4eb5987c3551` |
| `modem.b01` | `92813c08e385541ce2825cf33d8da6ad417eecb12f192ad8d81331af8c3a471e` |
| `modem.b02` | `2584c620b542b716b3a974670bc94581525307e7f425697c81f7161062c2d898` |
| `modem.b03` | `e5de309f5ef2a62e799302c8b6349e5eadc39be00983b349a4298a20ae21b4d5` |
| `modem.b04` | `b09b1c7fcb05d3269f455cf2b1717a4c790057992a548f92e589c4109a53f952` |
| `modem.b05` | `6cada5c9c0253192244446d057dc23484e988444fc38ba3e8dbddcc89eac90ab` |
| `modem.b08` | `1e03c26d074db181f6dae28d29e3f423773ac708d80dfe85a6fabf03290418cd` |
| `modem.b10` | `efc26d8a3592d57a065951e69e1e5fa5a6e2c023545e79700954d3c08680d3b4` |
| `modem.b11` | `bb8dc133a8ac723b21547cf77fba0f4e559e85de47e2ab8d7edf71dfc30fd7f1` |
| `modem.b13` | `3f7571bb4e94cae6da9d484b4c4516cbdb820804cb5e969f09877cdbd62995e4` |
| `modem.b14` | `c9d5afaf7834dbea212a292f5f781f1c14b06e2608a2b9f8c33e322a95c3f2ae` |
| `modem.b15` | `06cf00d6abfcb05a30ea1dc0abb6ecc0912004ac35457607499f684438b36959` |
| `modem.b16` | `ab795bf097be054880da5e8992e372701afa1b6be1cd2226002d3108c9338c60` |
| `modem.b17` | `cc3635fbbf00db873bc4aa1d94edcef4dca25ca93dcd334f05ed4b0c53c1b34c` |
| `modem.b18` | `edf616a7e5d82b5a6473d9e12129bef72adc96018dd55a56f2ca552dfad95b8a` |
| `modem.b19` | `a78c7cac007beb9b159e05444eb424ab9c6f05d399f575dedc4672daec2c6c4b` |
| `modem.b22` | `6214c32179339757703130888a9e7375c0ed44dad6c7e4bda1ae8dc39800c22f` |
| `modem.b23` | `451aabcc7db85eeff57d4a4b5647494385a539b728f66430eb2d2b709acb4258` |
| `modem.b24` | `e2932e00c00a633c37be37c2f6d817e551e08fd5448967d77630b6a5289a4794` |
| `modem.b25` | `60bf50c1d780590a30d4dbd2b1cac7164c62284b2e5369235c4448a76f98744a` |

Raw hash files saved during the session: `/tmp/android_modem_hashes.txt`,
`/tmp/stock_modem_hashes.txt`, normalized copies `a.norm` / `s.norm`.

### 2.4 What this establishes

> **The baseband is provably byte-identical between the running stock Android and the OpenWrt
> build. Combined with the user's verified multi-day Android stability, the 900 s failure is
> therefore 100 % AP-side.**

This **eliminates** every hypothesis of the form "Android ships a different modem firmware":

* it is not a different MPSS build,
* it is not a patched vs unpatched baseband,
* it is not a different MBA (the `mba.mbn` boot loader is also identical).

It also means the answer to open question §7.4 of `900S_CRASH_LPR_FRAMEWORK_RE.md` ("which firmware
was deployed at the 912.9 s capture — stock `3d23e145…` or patched `68fb4a5a…`?") is now only half
answered: we know the *stock* hash is what Android runs. The OpenWrt-side hash still needs to be
read off the router.

### 2.5 Caveat — this does not cover NV / carrier configuration

Only the firmware **image** is proven identical. The following are *not* covered and could still
differ between the two systems:

* `modemst1` / `modemst2` (EFS NV) — `/dev/block/mmcblk0p13`, `p14`
* `fsg` / `fsc` — `/dev/block/mmcblk0p20`, `p16`
* the carrier MBN selected at runtime (`mcfg` / PDC)

A different NV/carrier configuration is a live AP-visible difference and must be ruled out
separately before attributing everything to AP software.

---

## 3. Boot and modem timing

From `dmesg` on the Android device:

```
[    5.964277] pil-q6v5-mss 4080000.qcom,mss: modem: loading from 0x86800000 to 0x8ba00000
[    6.010431] pil: MBA boot done
[    6.640107] pil-q6v5-mss 4080000.qcom,mss: modem: Brought out of reset
[    6.745480] pil-q6v5-mss 4080000.qcom,mss: modem: Power/Clock ready interrupt received
[    6.745512] pil-q6v5-mss 4080000.qcom,mss: Subsystem error monitoring/handling services are up
[    7.152501] apr_tal:Modem Is Up
```

**Modem reset de-asserts at kernel 6.64 s.** Therefore:

```
modem_uptime = kernel_uptime - 6.64 s
```

**OpenWrt comparison:** `scratch/diag900/run_capture.sh:25` uses `MODEM_BOOT_OFFSET=11.7`
(modem up at kernel 11.7 s). The offsets differ, which is expected — different boot paths — but
this must be accounted for when aligning the two timelines. **The 900 s figure is modem uptime, not
kernel uptime**; a naive kernel-time comparison between the two systems will be off by ~5 s.

Also note `init` errors at boot:

```
init: cannot expand '${persist.sys.mba_boot_timeout}' ...
init: property 'persist.sys.modem_auth_timeout' doesn't exist ...
init: property 'persist.sys.pil_proxy_timeout' doesn't exist ...
```

These are unset properties, harmless, but they confirm this is a hand-built image.

---

## 4. Tooling inventory

### 4.1 `/system/xbin` — full BusyBox (≈420 applets)

Relevant to this investigation:

| Tool | Use |
|---|---|
| `strace`, `gdb`, `gdbserver` | syscall tracing / live debug of `qcril`, `qmuxd`, `netmgrd` |
| `tcpdump`, `nethogs`, `iftop`, `mtr`, `arping` | network observation |
| `lsof`, `ps`, `pstree`, `pidstat`, `pmap`, `top`, `htop` | process/FD inspection |
| `devmem` | **direct register access** — can read/write MMIO |
| `hexdump`, `xxd`, `strings` | binary inspection |
| `dmesg`, `logread`, `logcat` (`/system/bin`) | kernel / Android logs |
| `sar`, `mpstat`, `iostat`, `powertop` | activity accounting |
| `nc`, `socat`, `ssh`, `curl`, `rsync`, `tftp` | data movement |
| `sha256sum`, `md5sum` | hashing |
| `microcom` | serial/muxed-port terminal |
| `rtcwake`, `hwclock`, `date` | time/RTC |

`devmem` is notable: it gives register-level access that OpenWrt-side work has needed for the
PM8916 / RPM path.

### 4.2 `/system/bin` — Qualcomm DIAG suite present

```
diag_callback_client   diag_dci_sample      diag_klog        diag_mdlog
diag_qshrink4_daemon   diag_socket_log      diag_uart_log    test_diag
qmiproxy               rmt_storage
```

`diag_mdlog` is the modem-log capture tool. `rmt_storage` is the EFS2 NV-sync daemon
(compare OpenWrt's `packages/rmtfs`).

### 4.3 Device nodes

```
crw-rw---- system qcom_diag 243, 0  /dev/diag
crw------- root    root     244, 1  /dev/smd1
crw-rw---- radio   radio    244,11  /dev/smd11
crw-rw---- bluetooth ...    244, 2  /dev/smd2
... smd3..smd8, smd21, smd22, smd36, smd_pkt_loopback
crw-r----- radio   radio    242, 0..20  /dev/smdcntl0..11, /dev/smdcnt_rev0..8
```

`/dev/diag` (char 243,0, group `qcom_diag`) is the AP-side DIAG driver — the Android equivalent of
the `rpmsg0`/`rpmsg1` endpoint pair OpenWrt had to hand-bind in
`scratch/qmi900/diag_bind.sh`.

**debugfs** (`/d/`, present):

```
bam_dmux   diag   clk   cpr-regulator   ghsic_ctrl_xport   ghsic_data_xport
ghsuart_ctrl_xport   ghsuart_data_xport   ...
```

`/d/diag/` contains: `dci_stats`, `mempool`, `power`, `status`, `table`, `work_pending`
(all mode `-r--r--r--`, read-only).

`/d/bam_dmux` exists — this is where the stock-Android `bam_dmux/tbl` evidence in
`Stock_Android_Analysis/04_network_and_bam_dump.txt` came from. **Re-check it here**, because the
existing BAM-DMUX autosuspend theory has since been disproven (see
`project_diag_900s_capture_finding`).

---

## 5. Partition map (from `/dev/block/bootdevice/by-name/`)

```
modem    -> /dev/block/mmcblk0p1
modemst1 -> /dev/block/mmcblk0p13
modemst2 -> /dev/block/mmcblk0p14
fsc      -> /dev/block/mmcblk0p16
fsg      -> /dev/block/mmcblk0p20
```

`modemst1/2`, `fsg`, `fsc` hold the NV/EFS state. **These are the un-hashed, potentially-differing
pieces** flagged in §2.5.

> ### ⚠ CORRECTION (2026-09-21, Doc 170 PART 23) — DO NOT USE THIS TABLE TO LOCATE NV
>
> The device's **actual GPT**, read from LBA 2 with `dd if=/dev/mmcblk0`, is:
>
> ```
> p1 fsc   p2 fsg   p3 modem   p4 modemst1   p5 modemst2   p6 persist
> p7 sec   p8 hyp   p9 rpm    p10 sbl1      p11 tz        p13 boot
> p14 rootfs       p15 rootfs_data
> ```
>
> OpenWrt's `/dev/block/by-name/` matches that GPT **exactly**, and the GPT has
> **no p16 and no p20 at all** (it stops at p15). So the table above disagrees
> with the device on every entry that matters: a dump taken from
> "`modemst1` = p13" would have read the **boot** partition and "`modemst2` =
> p14" the **squashfs rootfs**.
>
> Two readings are open and this correction does not choose between them:
> **(a)** the GPT was rewritten after this session — it carries OpenWrt-specific
> names (`rootfs`, `rootfs_data`) that stock Android would not have, so if the
> OpenWrt install re-partitioned the device then the stock layout seen here is
> gone and the stock NV may have been relocated or overwritten, which would make
> a same-device NV comparison **impossible** rather than merely mis-indexed; or
> **(b)** this map was recorded from a different msm8916 board or a stale source.
>
> **Action: locate NV by partition NAME on both sides, and re-read the GPT on
> each side, before any comparison.** Also established at the same time:
> `mmcblk0p4` is **not** empty — its header carries the ASCII magic `IMAGEFS1V`
> at offset 40 (the QCOM EFS/FSG image format) and its first 1 MB holds ~1.6 M
> non-zero bytes, as does `mmcblk0p5`. So "OpenWrt's NV is uninitialised" is
> **dead**. See Doc 170 PART 23.

---

## 6. `Diag.cfg` mask file format — REVERSE-ENGINEERED

### 6.1 Where the parser lives

`diag_mdlog` (18,440 B, ELF32 ARM, stripped) is a thin CLI wrapper. The parser is in
**`/system/vendor/lib/libdiag.so`** (64,552 B, ELF32 ARM, stripped, Thumb-2), exported as:

| Symbol | vaddr | Size |
|---|---|---|
| `diag_read_mask_file` | `0x6274` | 1856 B |
| `diag_read_mask_file_list` | `0x69b5` | 1484 B |
| `mask_file` (default path) | `0x10094` | 100 B |
| `mask_file_mdm` | `0x10214` | 100 B |
| `token_list` | `0x10068` | 40 B (10 × u32) |
| `read_mask` | `0xfc0fc` | 19488 B |
| `event_mask` | `0x105374` | 325 B |

Disassembly method (mirrors the Hexagon workflow in
`reference_hexagon_disasm_toolchain`):

```bash
adb pull /system/vendor/lib/libdiag.so /tmp/libdiag.so
llvm-objdump -d --triple=thumbv7-linux-gnueabi \
    --start-address=0x6274 --stop-address=0x6f75 --no-show-raw-insn /tmp/libdiag.so
```

> **Trap:** the ARM triple produces garbage — the library is **Thumb-2**. Use
> `--triple=thumbv7-linux-gnueabi`. Same class of mistake as the signed-hexagon-constant trap in
> the modem RE work.

### 6.2 It is NOT a struct-based binary format

The decisive evidence is the set of library calls `diag_read_mask_file` makes:

```
35 × __android_log_print   21 × __errno     19 × printf     10 × puts
 5 × strlcpy    4 × strlcat    4 × strerror
 3 × malloc     3 × free       3 × fopen      2 × fclose     2 × __stack_chk_fail
 1 × strtol     1 × strndup    1 × strlen     1 × stat       1 × realloc
 1 × readdir    1 × opendir    1 × memcpy
 1 × fgets      1 × fgetc      1 × closedir
```

**There is no `fread`, no `fscanf`, no `mmap`.** The file is consumed **one byte at a time with
`fgetc`**. (`fgets` + `strtol` belong to `diag_read_mask_file_list`, the *list* file — see §6.6.)

So the requested "header struct / field types / sizes / byte order" **does not exist**. There is no
header struct in the file. The header is *synthesised by the tool* (§6.4).

### 6.3 The record separator is `0x7E` (`~`) — the core loop

Thumb disassembly, `diag_read_mask_file`, the whole read loop:

```asm
6812:  ldr   r6, [pc, #0x19c]      @ 0x69b0
6814:  movs  r4, #0x4              ; r4 = index i, reset to 4 after each record
6816:  add   r5, sp, #0x2fc        ; r5 = packet buffer base
6818:  add   r6, pc
681a:  mov   r0, r10               ; r0 = FILE*
681c:  blx   fgetc                 ; c = fgetc(f)
6820:  adds  r3, r0, #0x1
6822:  beq   0x6854                ; if (c == EOF) -> fclose, return
6824:  ldr   r3, [sp, #0x28]       ; r3 = proc_type
6826:  cmp   r3, #0x0
6828:  ble   0x6836                ; if (proc_type <= 0) skip
682a:  cmp   r4, #0x4
682c:  bne   0x6836                ; only when i == 4
682e:  ldr.w r1, [r6, r3, lsl #2]  ; r1 = proc_table[proc_type]   <-- per-processor dword
6832:  movs  r4, #0x8              ; i = 8
6834:  str   r1, [r5, #0x4]        ; buf[4..7] = proc_table[proc_type]
6836:  uxtb  r0, r0
6838:  add.w r1, r4, #0x1
683c:  cmp   r0, #0x7e             ; c == '~' (0x7E) ?
683e:  strb  r0, [r5, r4]          ; buf[i] = c        <-- '~' IS stored
6840:  bne   0x6850                ; not '~' -> i = i+1, loop
6842:  mov   r0, r5
6844:  movs  r4, #0x20
6846:  bl    0x61b8 <diag_send_data>  ; <-- SEND THE ACCUMULATED PACKET
684a:  str   r4, [r5]              ; buf[0..3] = 0x00000020   (reset)
684c:  movs  r4, #0x4              ; i = 4
684e:  b     0x681a                ; next record
6850:  mov   r4, r1                ; i++
6852:  b     0x681a
```

### 6.4 The file format, stated exactly

**CORRECTED 2026-09-20** — the first draft of this section inferred the framing from the Thumb
disassembly alone and got `buf[0..3]` wrong (it called `0x20` a "synthesised 8-byte header"). The
authoritative answer is in the **kernel driver**, which is checked into this repo at
`GitIgnore/android_kernel_zte_msm8916/drivers/char/diag/`. The corrected statement follows; the
proof is in §6.9.

Each record is transmitted to the modem as the packet:

```
  offset  size  content
  ------  ----  -----------------------------------------------------------
  +0x00     4   u32 LE 0x00000020  = USER_SPACE_DATA_TYPE (pkt_type)
  +0x04     4   remote-processor token, ONLY when proc_type > 0
  +0x04/8   n   the file's bytes, verbatim
  last      1   0x7E — the file's own terminator byte, included in the packet
```

The file's bytes land at `buf[4]` when `proc_type == 0` and at `buf[8]` when `proc_type > 0`
(`0x682a cmp r4,#0x4` / `0x6832 movs r4,#0x8`). **`proc_table[proc_type]` is not a header field —
it is the remote-processor token that the driver reads back out with `diag_get_remote()`** (§6.9).

**Consequences:**

* The file is a **byte stream of HDLC-encoded DIAG frames**, each terminated by `0x7E`.
* The `0x7E` byte is **part of the transmitted packet** — it is stored into the buffer *before* the
  comparison branch (`strb r0, [r5, r4]` at `0x683e` precedes `bne` at `0x6840`).
* Byte `0x20` is **not a DIAG opcode** and is **not** read from the file — it is the driver's
  payload-type selector, and it is the *same constant on every record*.
* `buf[4..7]` is rewritten on **every** record (the index resets to 4), so a single `proc_type`
  applies to the whole file.
* The file carries **no length field** — the length is implicit in the distance to the next `0x7E`.
* `diag_send_data(buf, len)` is a bare `write()` to the diag fd — **it adds no framing of its own**.
  Verified by disassembly of `diag_send_data` @ `0x61b8`: `errno = 0; write(fd, buf, len);`.

### 6.5 `proc_type` ("token") must be 1..9

`token_list` at vaddr `0x10068` (`.data`, file offset `0xf068`) is exactly 10 dwords:

```
[0] 0x00000000   [1] 0xffffffff   [2] 0xfffffffe   [3] 0xfffffffd
[4] 0xfffffffc   [5] 0xfffffffb   [6] 0xfffffffa   [7] 0xfffffff9
[8] 0xfffffff8   [9] 0xfffffff7
```

i.e. `{ 0, -1, -2, -3, -4, -5, -6, -7, -8, -9 }`.

`valid_token` (`0x4530`, 28 bytes) is:

```asm
4530:  cmp  r0, #0x0
4532:  bge  0x454a            ; r0 >= 0 -> return 0 (invalid)
4534:  ldr  r1, [pc, #0x18]
4536:  movs r3, #0x0
4538:  add  r1, pc            ; r1 = &token_list
453a:  ldr  r2, [r3, r1]
453c:  cmp  r0, r2
453e:  bne  0x4544
4540:  rsbs r0, r0, #0       ; return -r0
4542:  bx   lr
4544:  adds r3, #0x4
4546:  cmp  r3, #0x28         ; 0x28/4 = 10 entries
4548:  bne  0x453a
454a:  movs r0, #0x0          ; not found -> return 0
454c:  bx   lr
```

The caller does `rsbs r0, r0, #0` (negate) then `bl valid_token`. So the **token is a positive
integer 1–9**, and `valid_token` returns it unchanged when valid. Failure logs:

```
diag: In %s, invalid Token number %d
```

**In this context the "token" is the remote-processor type.** The companion list-file parser emits
`Skipping line. Invalid processor type found.` / `Invalid processor type: %d specified.`, so
`proc_type` is the same concept under two names. `proc_type` is a global (vaddr `0xe2870`, 4 bytes).

### 6.6 The companion *list* file (`-l`) is plain TEXT

`diag_read_mask_file_list` (`0x69b5`) uses `fopen` + **`fgets`** + **`strtol`** + `strlcpy` +
`strndup` — a genuine line-oriented text parser. Its error strings define the grammar:

```
Mask list file name is: %s
Sorry, can't open mask list file,please check the device, errno: %d
Skipping line. Invalid processor type found. line: %s
Skipping line. No processor type present. line: %s
Skipping line. Remote processor: %d is not present.
Skipping line. Invalid processor type: %d specified. line; %s
Skipping line. No remote processors present. proc_type: %d, line: %s
Skipping line. No file name found. line: %s
Mask list read for proc_type: %d, mask file: %s
Error reading mask file: %s
Reading list of mask files complete. Successfully read %d files
```

So each line is roughly `<proc_type> <mask-file-name>`; the `strtol` supplies `proc_type`, the
remainder the path. This is the file passed with `-l`.

### 6.7 Default paths

| Global | vaddr | Value |
|---|---|---|
| `mask_file` | `0x10094` | `/sdcard/diag_logs/Diag.cfg` |
| `mask_file_mdm` | `0x10214` | `/sdcard/diag_logs/mdm/Diag.cfg` |

Other defaults observed in `diag_mdlog`'s strings:
`/sdcard/diag_logs/Diag_list.txt` (list file),
`/sdcard/diag_logs/mdm{,2,3,4}/Diag.cfg`, `/sdcard/diag_logs/qsc/Diag.cfg`,
output dir `/data/diag_logs`, PID file `/data/diag_logs/diag_pid`.

### 6.8 `diag_mdlog` CLI

```
-f, --filemsm      mask file name for MSM
-m, --filemdm      mask file name for MDM
-l, --filelist     name of file containing list of mask files
-o, --output       output directory name
-s, --size         maximum file size in MB
-w, --wait         waiting for directory
-n, --number       maximum file number
-k, --kill         kill existing instance of diag_mdlog
-c, --cleanmask    Send mask cleanup to modem at exit
-d, --disablecon   Disable console messages
-e, --enablelock   Run using wake lock to keep APPS processor on
-b, --nonrealtime  Have peripherals buffer data and send in non-real-time
-r, --renamefiles  Rename dir/file names to time when closed
-q, --qshrink4dir  Directory containing the APSS qshrink4 database files
-h, --help
```

Critical behaviour string:

```
diag_mdlog: No mask files have been successfully read.
diag_mdlog: Running with masks that were set prior to diag_mdlog start-up.
```

— i.e. **it can run with no mask file at all**, inheriting whatever masks QCRIL/the system already
set. This is the cheapest path to a first capture and is what §7 tests.

### 6.9 RESOLVED — `buf[0] = 0x20` is `USER_SPACE_DATA_TYPE`, and the payload must be HDLC+CRC

The first draft of this section left `0x20` as an open question and guessed it might be a DIAG
opcode or a length field. **Both guesses were wrong.** The answer is in the AP-side diag driver.

#### 6.9.1 The 4-byte prefix is the driver's payload-type selector

`drivers/char/diag/diagchar_core.c:1678`:

```c
static ssize_t diagchar_write(struct file *file, const char __user *buf,
			      size_t count, loff_t *ppos)
{
	int err, ret = 0, pkt_type, token_offset = 0;
	...
	/* Get the packet type F3/log/event/Pkt response */
	err = copy_from_user((&pkt_type), buf, 4);
	if (err) { pr_alert("diag: copy failed for pkt_type\n"); return -EAGAIN; }
	/* First 4 bytes indicate the type of payload - ignore these */
	if (count < 4) { pr_err("diag: Client sending short data\n"); return -EBADMSG; }
	payload_size = count - 4;
```

and `include/linux/diagchar.h:21`:

```c
#define USER_SPACE_DATA_TYPE	0x00000020
```

So `0x20` is `USER_SPACE_DATA_TYPE` — the selector that routes the write to the
"mask / on-device-logging request" handler. The full selector set (`include/linux/diagchar.h:16-26`):

```
MSG_MASKS_TYPE          0x00000001     DCI_DATA_TYPE           0x00000040
LOG_MASKS_TYPE          0x00000002     CALLBACK_DATA_TYPE      0x00000080
EVENT_MASKS_TYPE        0x00000004     DCI_LOG_MASKS_TYPE      0x00000100
PKT_TYPE                0x00000008     DCI_EVENT_MASKS_TYPE    0x00000200
DEINIT_TYPE             0x00000010     DCI_PKT_TYPE            0x00000400
USER_SPACE_DATA_TYPE    0x00000020
```

#### 6.9.2 The remote-processor token and the mask gate

`diagchar_core.c:1860-1890`:

```c
if (pkt_type == USER_SPACE_DATA_TYPE) {
	err = copy_from_user(driver->user_space_data_buf, buf + 4, payload_size);
	/* Check for proc_type */
	remote_proc = diag_get_remote(*(int *)driver->user_space_data_buf);

	if (remote_proc) {
		if (payload_size <= MIN_SIZ_ALLOW) { ... return -EBADMSG; }
		token_offset = 4;
		payload_size -= 4;
		buf += 4;
	}
	/* Check masks for On-Device logging */
	if (driver->mask_check) {
		if (!mask_request_validate(driver->user_space_data_buf + token_offset)) {
			pr_alert("diag: mask request Invalid\n");
			return -EFAULT;
		}
	}
	...
	/* send masks to 8k now */
	if (!remote_proc)
		diag_process_hdlc((void *)(driver->user_space_data_buf + token_offset),
					payload_size);
	return 0;
}
```

`diag_get_remote()` (`diagchar_core.c:465`) returns `-remote_info` only for `MDM`/`MDM2`/`QSC`,
and **`0` for everything else**. The MSM8916 modem is on-chip, so **`proc_type = 0` →
`remote_proc = 0` → `token_offset = 0`** and the file's first byte *is* the start of the DIAG
frame. This is why `diag_mdlog` prints `REMOTE PROCESSOR MASK 0` — that is expected, not an error.

`mask_check` is set **only** in memory-device mode (`diagchar_core.c:908-911`):

```c
if (driver->logging_mode == MEMORY_DEVICE_MODE) {
	diag_clear_local_tbl();
	diag_clear_hsictbl();
	driver->mask_check = 1;
```

…which is exactly the mode `diag_mdlog` puts the driver in. The gate
(`diagchar_core.c:2334 mask_request_validate`) therefore **applies to every `Diag.cfg` we write**:

```c
packet_id = mask_buf[0];
if (packet_id == 0x4B) { /* subsystem dispatch, per-subsystem ss_cmd allow-list */ }
else switch (packet_id) {
case 0x00:  /* Version Number */
case 0x0C:  /* CDMA status packet */
case 0x1C:  /* Diag Version */
case 0x1D:  /* Time Stamp */
case 0x60:  /* Event Report Control */
case 0x63:  /* Status snapshot */
case 0x73:  /* Logging Configuration */
case 0x7C:  /* Extended build ID */
case 0x7D:  /* Extended Message configuration */
case 0x81:  /* Event get mask */
case 0x82:  /* Set the event mask */
	return 1;
default:
	return 0;
}
```

**So a valid `Diag.cfg` frame starts with one of `0x00, 0x0C, 0x1C, 0x1D, 0x60, 0x63, 0x73, 0x7C,
0x7D, 0x81, 0x82`** (or `0x4B` with an allowed subsystem). `0x73` and `0x7D` are the useful ones.

#### 6.9.3 The payload must be HDLC-framed **with a valid CRC-CCITT**

`diag_process_hdlc()` (`diagfwd.c:1748`) **decodes** the payload — it does not accept raw frames:

```c
ret = diag_hdlc_decode(&hdlc);
if (ret) {
	crc_chk = crc_check(hdlc.dest_ptr, hdlc.dest_idx);
	if (crc_chk) {
		pr_err_ratelimited("diag: In %s, bad CRC. Dropping packet\n", __func__);
		mutex_unlock(&driver->diag_hdlc_mutex);
		return;
	}
}
...
if (hdlc.dest_idx < 4) { ... "message is too short" ... }
if (ret)
	type = diag_process_apps_pkt(driver->hdlc_buf, hdlc.dest_idx - 3);
```

`crc_check()` (`diagchar_hdlc.c:235`):

```c
uint16_t crc = CRC_16_L_SEED;                     /* 0xFFFF */
if (!buf || len < 4) return -EIO;
/* Run CRC check for the original input. Skip the last 3 CRC bytes */
crc = crc_ccitt(crc, buf, len-3);
crc ^= CRC_16_L_SEED;
sent_crc[0] = buf[len-3];
sent_crc[1] = buf[len-2];
if (crc != *((uint16_t *)sent_crc)) return -EIO;
```

and `diagchar_hdlc.c:29-32` / `include/linux/crc-ccitt.h`:

```c
#define CRC_16_L_SEED   0xFFFF
#define CRC_16_L_STEP(xx_crc, xx_c)  crc_ccitt_byte(xx_crc, xx_c)
static inline u16 crc_ccitt_byte(u16 crc, const u8 c)
{
	return (crc >> 8) ^ crc_ccitt_table[(crc ^ c) & 0xff];
}
```

**The polynomial is the *reflected* one.** `lib/crc-ccitt.c` says so explicitly:

> *The polynomial can be seen in entry 128, 0x8408. This corresponds to x^0 + x^5 + x^12.*

so `crc_ccitt_table[i]` must be generated as
`c = i; for 8: c = (c & 1) ? (c >> 1) ^ 0x8408 : (c >> 1)`.
**Getting this wrong is the single easiest way to fail:** an MSB-first `0x1021` table (the more
familiar "CRC-16/CCITT-FALSE") produces a plausible-looking CRC that the driver rejects with
`diag: In diag_process_hdlc, bad CRC. Dropping packet`. This was reproduced and then fixed.

Escape rules (`diagchar_hdlc.c:167 diag_hdlc_decode`, standard HDLC):

```
ESC_CHAR = 0x7D      CONTROL_CHAR = 0x7E      ESC_MASK = 0x20
0x7D  ->  0x7D 0x5D
0x7E  ->  0x7D 0x5E
```

A leading `0x7E` on a record is tolerated and skipped (`msg_start` logic).

#### 6.9.4 The format, definitively

```
Diag.cfg := record { record }                        (no file header of any kind)

record   := <hdlc_escape(frame_body)> 0x7E
frame_body := <diag_frame> <crc_lo> <crc_hi>
crc      := crc_ccitt(0xFFFF, diag_frame) ^ 0xFFFF     (reflected poly 0x8408)
```

and each `record` is handed to the driver as `write(fd, 0x20_00_00_00 || record, 4 + len(record))`.

#### 6.9.5 Verified frame layouts (from the driver's own decoders)

`diag_process_apps_masks()` (`diag_masks.c:657`):

**`0x73` op=3 — SET log mask** (per equipment id):

```
 0  u8   0x73
 1..3     reserved (zero)
 4..7  u32 LE operation = 3
 8..11 u32 LE equip_id            (0 .. MAX_EQUIP_ID-1, MAX_EQUIP_ID = 16)
12..15 u32 LE num_items            (last log code for this equip_id)
16..    mask bytes, length = (num_items + 7) / 8
```

decoded by `diag_update_log_mask(*(int *)buf, buf+8, *(int *)(buf+4))` after `buf += 8`.

**`0x73` op=4 — GET log mask**, `op=0` — disable all log masks.

**`0x7D` op=4 — SET runtime message mask**:

```
 0..1  u8 0x7D, u8 0x04
 2..3  u16 LE ssid_first
 4..5  u16 LE ssid_last            (must be >= ssid_first)
 6..7      status (2 bytes)
 8..    mask, length = 4 * (ssid_last - ssid_first + 1)
```

**`0x7D` op=3 — GET runtime message mask**, `op=5` — disable.

#### 6.9.6 How this reaches the modem — and the one real asymmetry with OpenWrt

`0x73` op=3 does **not** just set an AP-side table. `diag_process_apps_masks()` then calls

```c
for (i = 0; i < NUM_SMD_CONTROL_CHANNELS; i++) {
	if (driver->smd_cntl[i].ch)
		diag_send_log_mask_update(&driver->smd_cntl[i], *(int *)buf);
}
```

and `diag_send_log_mask_update()` (`diag_masks.c:342`) builds the **`DIAG_CTRL_MSG_LOG_MASK`**
packet and `smd_write()`s it to the modem's control channel:

```c
ctrl_pkt.cmd_type = DIAG_CTRL_MSG_LOG_MASK;
ctrl_pkt.data_len = 11 + log_mask_size;
ctrl_pkt.stream_id = 1;
ctrl_pkt.status = driver->log_status;
switch (driver->log_status) {
case DIAG_CTRL_MASK_ALL_DISABLED:
case DIAG_CTRL_MASK_ALL_ENABLED:
	ctrl_pkt.equip_id = 0;
	ctrl_pkt.num_items = 0;
	ctrl_pkt.log_mask_size = 0;
	send_once = 1;
	break;
case DIAG_CTRL_MASK_VALID:
	ctrl_pkt.equip_id = i;
	ctrl_pkt.num_items = log_item->num_items;
	ctrl_pkt.log_mask_size = log_mask_size;
	send_once = 0;
	break;
```

For `DIAG_CTRL_MASK_ALL_ENABLED` this emits **exactly the 19-byte packet that OpenWrt's
`diag_logtool cntl-enable` sends**:

```
09 00 00 00   DIAG_CTRL_MSG_LOG_MASK
0b 00 00 00   data_len = 11
01            stream_id
02            status = ALL_ENABLED
00            equip_id
00 00 00 00   num_items
00 00 00 00   log_mask_size
```

**But there is an asymmetry worth recording.** In this driver build `driver->log_status` is only
ever assigned `DIAG_CTRL_MASK_INVALID` (`diag_masks.c:288, 911`), `DIAG_CTRL_MASK_VALID`
(`:312`) or `DIAG_CTRL_MASK_ALL_DISABLED` (`:239`). **It is never set to `DIAG_CTRL_MASK_ALL_ENABLED`.**
Only `msg_status` can reach `ALL_ENABLED` (`diag_masks.c:112`). So:

* **OpenWrt** writes `DIAG_CNTL` frames *directly* to `/dev/rpmsg1`, bypassing the AP diag driver
  entirely — it can therefore assert `LOG_MASK status = ALL_ENABLED`.
* **Android** must go through `diagchar_write()`/`diag_process_apps_masks()`, which will only ever
  push **per-`equip_id` `status = VALID`** log masks (or `ALL_DISABLED`).

The captures are therefore comparable but **not bit-identical in provenance**: Android's log mask is
16 explicit per-equipment masks; OpenWrt's is one "everything on" assertion. Note also
`diag_mask_update_fn()` (`diag_masks.c:318`) re-pushes all three masks every time the modem's SMD
control channel opens — and at this device's boot that call **fails harmlessly** with
`diag: In diag_send_msg_mask_update, invalid status 0` / `diag_send_log_mask_update, invalid status 0`
(status 0 = `DIAG_CTRL_MASK_INVALID`), because no client had set masks yet.

### 6.10 Working recipe (VERIFIED — this is what produced the capture in `captures/`)

```python
import struct

# Linux lib/crc-ccitt.c — REFLECTED poly 0x8408 (table entry 128 == 0x8408)
_tbl = []
for i in range(256):
    c = i
    for _ in range(8):
        c = ((c >> 1) ^ 0x8408) if (c & 1) else (c >> 1)
    _tbl.append(c & 0xffff)
assert _tbl[:8] == [0x0000, 0x1189, 0x2312, 0x329b, 0x4624, 0x57ad, 0x6536, 0x74bf]
assert _tbl[128] == 0x8408

def crc_ccitt_byte(crc, b): return ((crc >> 8) ^ _tbl[(crc ^ b) & 0xff]) & 0xffff
def crc_ccitt(seed, buf):
    c = seed
    for b in buf: c = crc_ccitt_byte(c, b)
    return c

def hdlc_escape(bs):
    o = bytearray()
    for b in bs:
        o += bytes([0x7d, b ^ 0x20]) if b in (0x7d, 0x7e) else bytes([b])
    return bytes(o)

def record(payload):
    crc = crc_ccitt(0xFFFF, payload) ^ 0xFFFF
    return hdlc_escape(payload + bytes([crc & 0xff, (crc >> 8) & 0xff])) + b'\x7e'

recs = []
for eq in range(16):                                   # 0x73 op=3, all logs on
    ni = 512
    recs.append(bytes([0x73, 0, 0, 0]) + struct.pack('<I', 3)
                 + struct.pack('<I', eq) + struct.pack('<I', ni)
                 + b'\xff' * ((ni + 7) // 8))
recs.append(bytes([0x7d, 0x04]) + struct.pack('<H', 0)  # 0x7D op=4, all messages on
            + struct.pack('<H', 0) + b'\x00\x00' + b'\xff\xff\xff\xff')

open('/sdcard/diag_logs/Diag.cfg', 'wb').write(b''.join(record(r) for r in recs))
```

Then:

```bash
adb push Diag.cfg /sdcard/diag_logs/Diag.cfg
adb shell 'diag_mdlog -o /sdcard/diag_logs/run1 -s 5 &'   # -s is MB, and it is a MINIMUM
sleep 30
adb shell 'busybox killall -9 diag_mdlog'
adb pull /sdcard/diag_logs/run1/<timestamp>.qmdl
```

**Failure signatures to watch for** (all three were hit during development):

| `dmesg` line | Meaning | Fix |
|---|---|---|
| `diag: In diag_process_hdlc, bad CRC. Dropping packet` | CRC wrong or framing wrong | use the **reflected** `0x8408` table (§6.9.3) |
| `diag: mask request Invalid` | first byte not in the `mask_request_validate` allow-list | start the frame with `0x73`/`0x7D`/`0x60`/… |
| `Sorry, can't open MSM mask file, errno: 2` (logcat `Diag_Lib`) | `Diag.cfg` missing | create `/sdcard/diag_logs/Diag.cfg` |

`diag_mdlog` logs to **logcat tag `Diag_Lib`**, *not* stdout — `adb logcat -s Diag_Lib:V`. Its
stdout is block-buffered and is lost if the process is killed, which is what made the first
attempts look silent.

---

## 7. DIAG capture on Android — SOLVED

**Status: WORKING as of 2026-09-20 01:18 (device uptime 1259 s).** First successful capture:
`captures/android_test4.qmdl`, 266,862 bytes in a 30 s window (~8.9 KB/s).

### 7.1 What was tried, and what each attempt proved

| Attempt | Command | Result | Conclusion |
|---|---|---|---|
| 1 | `diag_mdlog` (no args) | daemonised (pid 2158); created `/sdcard/diag_logs/20260920_010129/` but **empty** | ran, but logged nothing |
| 2 | `diag_mdlog -k` | printed `stopping diag_mdlog instance pid: 2158`, **but the process survived** | `-k` is unreliable; use `kill -9` |
| 3 | `kill -9 2158` | worked; `ps` then showed no mdlog | — |
| 4 | `diag_mdlog -o /data/local/tmp/dc -s 100 -d -e` | process started (pid 2318), **no output dir, no files** | the earlier "no dir" reading was a shell-output artefact — see 7.2 |
| 5 | `adb logcat -s Diag_Lib:V` | **the whole story** | messages go to logcat, not stdout |
| 6 | raw `Diag.cfg` (no CRC) | `dmesg`: `diag: In diag_process_hdlc, bad CRC. Dropping packet` ×17 | payload must be HDLC+CRC |
| 7 | HDLC `Diag.cfg`, MSB-first `0x1021` CRC | `bad CRC` ×17 again | wrong polynomial variant |
| 8 | HDLC `Diag.cfg`, **reflected `0x8408`** CRC | **`diag_log_20260920_011837.qmdl`, 266,862 B** | **solved** |

### 7.2 The two misleading symptoms, explained

**"No output directory was created."** It *was* created every time — `ls -la` of a directory that
exists but is empty returns nothing visible in the `adb shell '...' | head` idiom used, and the
error text was swallowed. Always re-check in a separate `adb shell` invocation.

**"`diag_mdlog` produces no output."** It logs through `__android_log_print` with tag `Diag_Lib`.
Its *stdout* is block-buffered and is silently discarded when the process is `kill -9`'d — which is
how every early attempt was terminated. `adb logcat -s Diag_Lib:V` shows the real trace:

```
diag_mdlog: Created logging directory /sdcard/diag_logs/test4
diag_mdlog: Diag_LSM_Init succeeded.
 REMOTE PROCESSOR MASK 0
 logging switched
diag_mdlog: Reading mask for MSM, proc_type: 0
diag_mdlog: Default mask file being read for proc_type: 0
mask file name is: /sdcard/diag_logs/Diag.cfg
diag: Determining contents of directory /sdcard/diag_logs/test4 for circular logging ...
```

`REMOTE PROCESSOR MASK 0` is **not** an error — the MSM8916 modem is on-chip, so
`diag_get_remote(0) == 0` (§6.9.2).

### 7.3 What was actually wrong, in one line

`diag_mdlog` reads `/sdcard/diag_logs/Diag.cfg`; with no such file it prints
`Sorry, can't open MSM mask file, errno: 2` and falls back to
`Running with masks that were set prior to diag_mdlog start-up` — i.e. **no masks**, hence no data.
The file must be **HDLC-framed DIAG frames with a valid reflected CRC-CCITT** (§6.9, §6.10).

### 7.4 What the capture contains

3568 `0x7E`-delimited frames, 1794 of which carry `0x10` (`DIAG_LOG_F`) and 570 `0x11`
(`DIAG_MSG_F`). Plain-text MCPM payloads decode inline, e.g. at offset 2000:

```
4d 43 50 4d 3a 20 74 65 63 68 20 77 61 6b 65 75 70 5f 72 65 71 2d 20 63 6c 65 61 72 69 6e 67 ...
"MCPM: tech wakeup_req- clearing params early_wakeup_tim..."
```

**That is the exact MCPM message that fires at the OpenWrt stall onset** (Task #26), so the two
captures are directly comparable. The file also begins with the modem's **responses** to our own
mask frames (`73 00 00 00 03 00 00 00 …`), which is a useful positive control that the write path
worked end-to-end.

### 7.5 The one real asymmetry vs OpenWrt

Android can only ever assert **per-`equip_id` `LOG_MASK status = VALID`**; OpenWrt's
`cntl-enable` asserts **`status = ALL_ENABLED`** because it writes `DIAG_CNTL` frames straight to
`/dev/rpmsg1`, bypassing the AP diag driver. `driver->log_status` is never set to `ALL_ENABLED` in
this driver build (§6.9.6). The captures are therefore comparable in *content* but not identical in
*provenance*; treat "record present/absent" comparisons as sound and "record count" comparisons as
approximate.

### 7.6 Why the comparison still matters

The OpenWrt DIAG capture (see `900S_CRASH_LPR_FRAMEWORK_RE.md` §9) shows the LTE path collapsing to
~20–30 % in two episodes (t≈30–60 s, t≈70–100 s) with `rflte_core_rxctl` exclusive to episode 1.
The Android capture answers, on the same byte-identical firmware:

* does Android's LTE path collapse at all?
* does Android emit `MCPM: tech wakeup_req- clearing params early_wakeup_time 0x%x%x sleep_active %d`?
* **which sleep/LPR mode does the modem select under Android vs OpenWrt?** (open question §7.1 of
  the LPR report — a mode-selection difference would be a *direct* explanation)

### 7.7 Next step for this capture

The window above is a smoke test at device uptime ~1259 s. The real experiment is a **long capture
spanning a fresh boot's 900 s mark**, with the same ping traffic pattern used on OpenWrt:

```bash
adb reboot; adb wait-for-device
# re-root (required every boot) — see §9
adb shell 'nohup sh -c "while true; do ping -c 3 -W 2 -I rmnet0 8.8.8.8; sleep 27; done" >/dev/null 2>&1 &'
adb shell 'diag_mdlog -o /sdcard/diag_logs/soak -s 200 &'
# ... at ~14 min, pull and compare against scratch/diag900
```

---

## 8. Comparison checklist for the OpenWrt side

Things to capture on OpenWrt so the two systems can be diffed properly:

| # | Item | Android value | OpenWrt value |
|---|---|---|---|
| 1 | `modem.mdt` sha256 | `3d23e145637689d79f44f72349b24b61` | *to read* |
| 2 | all 21 modem segment hashes | see §2.3 | *to read* |
| 3 | `mba.mbn` sha256 | `f0e207485cf63af953d7c0cfdf69e770b06ad3a408f86a297102532b5d2ad764` | *to read* |
| 4 | modem reset de-assert time | kernel **6.64 s** | kernel **11.7 s** |
| 5 | `modemst1/2`, `fsg`, `fsc` hashes | *to read* | *to read* |
| 6 | carrier MBN / `mcfg` in use | *to read* | *to read* |
| 7 | BAM-DMUX channel state (`/d/bam_dmux/tbl`) | *to read* | n/a (different driver) |
| 8 | DIAG capture across 900 s | **blocked — §7** | **have it** (`scratch/diag900`) |

Items 5 and 6 are the only remaining *non-firmware-image* inputs that could differ and are therefore
the highest-value checks that do not depend on getting DIAG working.

---

## 9. Reproducing this session

```bash
# 1. root (required after every reboot)
adb shell 'setprop service.adb.root 1; busybox killall adbd'
sleep 6 && adb wait-for-device && adb shell id     # expect uid=0(root)

# 2. firmware identity check
adb shell 'cd /firmware/image && sha256sum modem.b* mba.mbn' | sed 's/\r$//' | sort > android.txt
(cd GitIgnore/MelbonWhiteStock_Dump/modem_extracted/image && sha256sum modem.b* mba.mbn | sort > stock.txt)
diff android.txt stock.txt          # expect no output

# 3. pull the mask parser
adb pull /system/vendor/lib/libdiag.so /tmp/libdiag.so
llvm-objdump -d --triple=thumbv7-linux-gnueabi \
  --start-address=0x6274 --stop-address=0x6f75 --no-show-raw-insn /tmp/libdiag.so

# 4. boot/modem timing
adb shell dmesg | grep -E "pil-q6v5-mss|apr_tal"
```

**Note on `sha256sum` over adb:** the device's BusyBox emits CRLF line endings. Always
`sed 's/\r$//'` before diffing, or every line will appear to differ (this produced one false
"DIFFERENCES FOUND" during the session).
