# Qualcomm MSM8916 Reboot-to-EDL Reverse Engineering

## Stock Android Kernel Findings and OpenWrt Objective

**Target device:** Melbon White / HiMI UFI  
**SoC:** Qualcomm MSM8916 / Snapdragon 410  
**Stock firmware:** Android 4.4.4 (`KTU84P`)  
**Kernel image:** `android-vmlinux`  
**Device Tree:** `HMU05.dts` / `qcom,msm8916-mtp`  
**Analysis date:** 2026-08-23

---

## 1. Objective

The objective is to determine the **exact mechanism used by the stock Melbon White / HiMI UFI firmware to reboot the MSM8916 into Qualcomm Emergency Download Mode (EDL / 9008)**.

The ultimate goal is to reproduce that mechanism safely from **OpenWrt**, preferably through a native kernel/userspace implementation, so the device can deliberately reboot into Qualcomm 9008 mode for EDL operations such as GPT access, partition backup, and recovery.

The central question is:

> **What exact values does the stock firmware write, to which address, and what reset mechanism causes the Qualcomm boot chain to enter EDL?**

---

# 2. Important Correction to the Earlier Hypothesis

An earlier working hypothesis assumed that this device used the commonly documented MSM8916 EDL cookie:

```text
0x65646c00
```

at:

```text
0x0860065c
```

The actual stock kernel binary does **not** contain this value.

Direct search of `android-vmlinux` produced:

```text
0x65646C00
hits = 0
```

Therefore:

> **We cannot currently claim that this stock kernel implements EDL by writing `0x65646c00` to `0x0860065c`.**

The exact mechanism must instead be reconstructed from the binary.

---

# 3. Major Discovery: Three EDL-Related Magic Values

The stock kernel contains:

```text
0x322A4F99
0xC67E4350
0x77777777
```

The first two occur exactly once:

```text
0x322A4F99 -> file offset 0x004cd1f4
0xC67E4350 -> file offset 0x004cd1f8
```

The third occurs at:

```text
0x004cd1fc
0x005772fc
0x005784c0
0x00578820
0x00578a6c
0x00578d28
0x00579908
```

The most important occurrence is the contiguous sequence:

```text
0x004cd1f4: 0x322A4F99
0x004cd1f8: 0xC67E4350
0x004cd1fc: 0x77777777
```

This is strong evidence that these three words form an intentional Qualcomm emergency-download data structure or magic sequence.

---

# 4. Kernel Virtual Addresses

Using the established mapping:

```text
kernel VA = file offset + 0xC0000000
```

the sequence becomes:

```text
File offset     Kernel VA       Value

0x004cd1f4      0xc04cd1f4      0x322A4F99
0x004cd1f8      0xc04cd1f8      0xC67E4350
0x004cd1fc      0xc04cd1fc      0x77777777
```

Thus the primary table to investigate is:

```text
0xc04cd1f4
    0x322A4F99
    0xC67E4350
    0x77777777
```

---

# 5. What Has Been Proven

### 5.1 The stock kernel contains the three magic values

Proven by direct binary search.

### 5.2 The values are contiguous

The three important values occur consecutively at:

```text
0x004cd1f4
0x004cd1f8
0x004cd1fc
```

### 5.3 The generic ASCII EDL cookie is absent

The value:

```text
0x65646c00
```

has zero occurrences.

### 5.4 Restart/SSR-related code exists

The kernel contains code and strings associated with subsystem restart and SoC reset, including:

```text
"Resetting the SoC - %s crashed."
```

at file offset:

```text
0x009fd2c7
```

A raw pointer to this string was found at:

```text
file offset 0x0029b90c
value      0xc09fd2c7
```

This led to investigation of the surrounding restart/subsystem framework.

---

# 6. Important Finding About the `0x539xxx` Functions

Several functions around:

```text
0x5396a4
0x5396f0
0x539780
0x5397c4
0x5398f4
```

were investigated.

The actual function at:

```text
0x5398f4
```

begins:

```asm
005398f4: push     {r3, r4, r5, lr}
005398f8: mov      r5, r2
005398fc: lsl      r2, r3, #2
00539900: mov      r4, r3
00539904: bl       #0x539780
00539908: cmn      r0, #0x1000
0053990c: bls      #0x539924
00539914: ldr      r3, [r0]
00539918: add      r0, r0, #4
0053991c: rev      r3, r3
00539920: str      r3, [r5], #4
```

This is a data-processing/copy routine involving byte-order reversal. It is **not itself the EDL magic writer**.

Similarly:

- `0x5396f0` walks/inspects structures.
- `0x5397c4` obtains a structure member.
- These functions should not be treated as the EDL implementation simply because they appeared in earlier indirect-call analysis.

---

# 7. SSR / Reset Investigation

The string:

```text
"Resetting the SoC - %s crashed."
```

was traced to code around:

```text
0x29b150
```

The relevant code includes:

```asm
0x29b154: bl #0x5398f4
```

This established that the SSR/reset framework uses the `0x539xxx` helper family.

However, because `0x5398f4` is a data-processing routine, this does **not** prove that it performs the actual hardware reset or EDL transition.

Therefore:

> **The SSR path and EDL path may intersect, but their exact relationship is not yet proven.**

---

# 8. Investigation of the `0x587xxx` Functions

The following functions were examined:

```text
0x587564
0x58763c
0x587688
```

### `0x587564`

This function walks/selects an entry from a structure and eventually returns:

```text
[r4, #0x14]
```

or an error value.

It does not directly show an EDL magic write.

### `0x58763c`

This function prepares a structure and calls:

```text
0x539b7c
```

followed by:

```text
0x5879c8
```

No direct EDL magic write was identified.

### `0x587688`

This function calls:

```text
0x5396f0
0x58763c
0x5397c4
```

and performs error/retry handling.

These functions demonstrate multiple layers of Qualcomm/downstream subsystem handling, but they are not yet proven to be the final EDL path.

---

# 9. SSR Function-Dispatch Structures

A table-like region was found at:

```text
0xc0bdf374
```

with entries such as:

```text
c0bdf374: c02ce378
c0bdf378: c02cd88c
c0bdf37c: c02ce204
c0bdf380: c02cd85c
c0bdf384: c02cd848
c0bdf394: c02cd798
c0bdf3b4: c02cdf30
c0bdf3c0: c02cd76c
c0bdf3d0: c02ce430
```

This appears relevant to subsystem/restart operation dispatch.

Another region at:

```text
0xc02cd878
```

was initially treated as a possible pointer table, but its contents decode as ARM instructions:

```text
ebf86486
e3550000
0a000005
e59f0a68
...
```

Therefore:

> We must carefully distinguish real function-pointer tables from executable code that happens to be referenced by indirect structures.

---

# 10. Earlier IMEM Finding

The Device Tree contains:

```dts
qcom,msm-imem@8600000 {
    compatible = "qcom,msm-imem";
    reg = <0x8600000 0x1000>;
    ranges = <0x00 0x8600000 0x1000>;

    restart_reason@65c {
        compatible = "qcom,msm-imem-restart_reason";
        reg = <0x65c 0x04>;
    };
};
```

Therefore:

```text
IMEM base       = 0x08600000
restart offset  = 0x65c
physical addr   = 0x0860065c
```

This remains an important restart-reason address.

However:

> The existence of `restart_reason@65c` does not prove that EDL uses this address.

The absence of `0x65646c00` from the kernel makes it particularly important to establish the actual EDL destination from code.

---

# 11. Current EDL Evidence

| Item | Result |
|---|---|
| MSM8916 stock kernel | Confirmed |
| `0x322A4F99` present | **Yes** |
| `0xC67E4350` present | **Yes** |
| `0x77777777` present | **Yes** |
| Three values contiguous | **Yes** |
| `0x65646c00` present | **No** |
| EDL magic destination | **Unknown** |
| Code that writes all three values | **Not yet proven** |
| Reset primitive | **Unknown** |
| Exact PBL/SBL1 interpretation | **Not yet proven** |
| OpenWrt EDL implementation | **Not ready** |

---

# 12. Main Question We Are Trying to Solve

The investigation is now focused on this exact chain:

```text
EDL request
    |
    v
Stock restart/EDL function
    |
    v
0x322A4F99
0xC67E4350
0x77777777
    |
    v
??? exact destination ???
    |
    v
??? reset / PMIC / watchdog operation ???
    |
    v
Qualcomm PBL/SBL1
    |
    v
9008 / EDL
```

The missing pieces are the **destination** and **reset primitive**.

---

# 13. Immediate Next Reverse-Engineering Task

The next step is to locate all code references to:

```text
0x004cd1f4
0x004cd1f8
0x004cd1fc
```

especially ARM PC-relative `LDR` instructions.

We want to find code equivalent to:

```asm
ldr rX, =0xc04cd1f4
```

or code loading one or more of the three words.

The surrounding function should then reveal whether the values are:

- copied somewhere,
- written to hardware,
- passed to another function,
- or used as comparisons.

---

# 14. Second Search: Direct Construction of the Magic Values

The compiler may construct constants rather than load them from the literal pool.

Therefore also search for:

```asm
movw
movt
orr
add
ldr
```

sequences that produce:

```text
0x322A4F99
0xC67E4350
0x77777777
```

followed by:

```asm
str
strb
strh
```

The important pattern is:

```text
construct magic
      |
      v
store magic
      |
      v
identify destination
```

---

# 15. Third Search: Identify the Destination

Once the writing instruction is found, determine the address held by the destination register.

For example:

```asm
ldr r3, [something]
str r2, [r3]
```

We must trace `r3`.

If it resolves to:

```text
0x0860065c
```

then the IMEM hypothesis becomes supported.

If it resolves elsewhere, that new address is potentially the real Qualcomm EDL state location.

---

# 16. Fourth Search: Identify the Reset Trigger

Immediately after the EDL writes, inspect the following instructions for:

```text
dsb
dmb
isb
PMIC calls
watchdog calls
restart-register writes
power-off calls
infinite loops
```

The ideal reconstructed sequence would look like:

```text
write MAGIC1
write MAGIC2
write MAGIC3
memory barrier
configure reset
trigger reset
```

This sequence must be demonstrated from the stock binary before reproducing it in OpenWrt.

---

# 17. Restart Reason vs EDL State

The investigation should keep these mechanisms separate until proven otherwise.

### Normal restart reason

Potentially associated with:

```text
0x0860065c
```

and normal Android restart values such as:

```text
0x77665501
0x77665500
0x77665502
```

### EDL state

The stock kernel demonstrably contains:

```text
0x322A4F99
0xC67E4350
0x77777777
```

The relationship between these two mechanisms is currently unknown.

Possible explanations include:

1. EDL uses a different shared-memory location.
2. EDL uses multiple IMEM words.
3. The three words are passed to a Qualcomm-specific PMIC/reset mechanism.
4. They are stored in a Qualcomm emergency-download structure.
5. They are used by downstream bootloader firmware in a way different from the generic `0x65646c00` mechanism.

Only binary tracing can distinguish these possibilities.

---

# 18. OpenWrt Implementation Goal

After the stock mechanism is proven, reproduce it in OpenWrt.

Preferred order:

### Option A — Kernel implementation

Implement the correct restart-mode behavior in the MSM8916 OpenWrt kernel/device tree.

Advantages:

- correct ordering;
- memory barriers;
- kernel-controlled reset;
- better integration with Linux reboot;
- less dependence on `/dev/mem`.

### Option B — Userspace helper

If the mechanism turns out to require only memory writes plus a normal reset, implement a small OpenWrt helper using `devmem` or a C program.

### Option C — Hardware EDL

Keep the physical/test-point method as the recovery fallback.

---

# 19. What We Should Not Do Yet

Do **not** blindly implement:

```sh
devmem 0x0860065c 32 0x65646c00
reboot -f
```

and do not add:

```dts
mode-edl = <0x65646c00>;
```

to OpenWrt solely on the basis of generic MSM8916 documentation.

The stock binary does not contain that value.

The correct implementation must be derived from the actual firmware.

---

# 20. Working Hypothesis

The strongest current hypothesis is:

```text
                 Stock Android
                       |
                       v
              Restart / EDL request
                       |
                       v
             Qualcomm downstream code
                       |
              +--------+--------+
              |                 |
              v                 v
        Normal restart       EDL path
                                  |
                                  v
                         0x322A4F99
                         0xC67E4350
                         0x77777777
                                  |
                                  v
                         Qualcomm restart
                              state
                                  |
                                  v
                           Warm reset
                                  |
                                  v
                             PBL/SBL1
                                  |
                                  v
                              9008 EDL
```

This is a **working hypothesis**, not yet a proven call graph.

---

# 21. Final Success Criteria

The reverse engineering will be considered complete when we can state, with binary evidence:

> **On this exact Melbon White / HiMI UFI MSM8916 firmware, reboot-to-EDL is implemented by writing [exact values] to [exact physical addresses], followed by [exact reset mechanism].**

Then we can implement the equivalent OpenWrt functionality.

The final OpenWrt implementation should ideally allow something like:

```sh
reboot edl
```

or:

```sh
reboot-edl
```

and reliably produce:

```text
USB VID:PID = 05c6:9008
Qualcomm HS-USB QDLoader / QUSB_BULK
```

without relying on a physical test point.

---

# 22. Immediate Action Plan

1. **Find references to `0x004cd1f4`, `0x004cd1f8`, and `0x004cd1fc`.**
2. Identify the containing function(s).
3. Trace the three values to their destination.
4. Resolve the destination to a physical register/address.
5. Identify the code immediately following the writes.
6. Identify the actual reset trigger.
7. Compare the mechanism with the `HMU05.dts` restart-reason node.
8. Verify whether the EDL sequence uses IMEM, PMIC registers, or another Qualcomm-specific mechanism.
9. Reconstruct the exact stock EDL call graph.
10. Only then implement the equivalent mechanism in OpenWrt.
11. Test first with non-destructive observation/readback where possible.
12. Finally verify enumeration as Qualcomm 9008.

---

## Bottom Line

The most important discovery so far is:

```text
The stock kernel does NOT contain 0x65646c00.

It DOES contain the contiguous sequence:

    0x322A4F99
    0xC67E4350
    0x77777777

at:

    0x004cd1f4
    0x004cd1f8
    0x004cd1fc
```

Therefore the investigation has moved from a **generic MSM8916 EDL recipe** to a **firmware-specific reconstruction of the actual Qualcomm EDL mechanism**.

The next breakthrough should come from finding the code that references the three-word table and tracing exactly where those values are written.
