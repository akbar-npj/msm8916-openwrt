# DOC-138 — MSM8916 Baseband Band Unlocking: Sources, NV Items, and Verification Status

**Status:** 📋 REFERENCE — sources located and consolidated; **not yet verified on the HMU05**
**Related:** Doc 137 (DIAG log stream / verified raw DIAG client), Doc 134 (RF front-end bring-up), Doc 136 (DIAG port discovery)
**Date:** 2026-09-19

---

## 1. Purpose

This document answers the question *"we added an extra band in our test branch using the
DIAG port — where is that documented?"* and consolidates every band-related artifact found
in the repository into a single reference, together with an explicit statement of what has
been **verified** versus what is **still unverified** for the HMU05.

The short answer: the band work lives in a **git-history-only document** (deleted from the
working tree) plus a set of **unlocked EFS images**. Neither has been validated against the
HMU05's own RF front-end.

---

## 2. Sources Found

### 2.1 The band-unlocking guide (git history only)

| Item | Value |
| :--- | :--- |
| Original path | `Docs/QUALCOMM_MSM8916_BASEBAND_BAND_UNLOCKING_GUIDE.md` |
| Commit | `cb252f8` — *"docs: add comprehensive Qualcomm MSM8916 baseband band unlocking guide"* |
| Author / date | `akbar_npj <akbar.npj@protonmail.com>`, 2026-09-05 |
| Branches containing it | `main`, `fix/hmu05-modem-crash-fix`, `backup/local-main-reverts` |
| Size | 328 lines |
| Working-tree status | **DELETED** — recoverable only via `git show` |

Recover with:

```sh
git show cb252f8:Docs/QUALCOMM_MSM8916_BASEBAND_BAND_UNLOCKING_GUIDE.md > /tmp/band_guide.md
```

### 2.2 Unlocked EFS images

| Artifact | Path | Size |
| :--- | :--- | :--- |
| FSG (factory calibration) | `GitIgnore/MelbonWhiteStock_Dump/Unlocked Basedband/fsg_unlocked.img` | 1 572 864 B |
| Modem NV store 1 | `GitIgnore/MelbonWhiteStock_Dump/Unlocked Basedband/modemst1_unlocked.img` | 1 572 864 B |
| Modem NV store 2 | `GitIgnore/MelbonWhiteStock_Dump/Unlocked Basedband/modemst2_unlocked.img` | 1 572 864 B |

These are **reference images only**. They have not been flashed, and no diff against the
HMU05's own `fsg`/`modemst1`/`modemst2` has been recorded.

### 2.3 Searched and ruled out

| Artifact | Finding |
| :--- | :--- |
| `diag-msm8916.sh` | **Unrelated.** A Spanish-language QRTR/QMI diagnostic helper; no band logic. |
| `Docs/Modem Stability/123…137` | No band-unlocking content (Doc 133/134 cover attach and RF bring-up). |

---

## 3. The Documented Method

> Everything in this section is transcribed from the recovered guide. It is **not** an
> endorsement and **not** verified on this hardware — see §5.

### 3.1 Band mask NV items

**NV 6828 — `NV_LTE_BC_CONFIG_I`** (LTE band bitmask, 64-bit little-endian,
bit = `1 << (Band - 1)`):

| Byte | Bits | Bands | Value | Decoded |
| :--- | :--- | :--- | :--- | :--- |
| 0 | 0–7 | B1–B8 | `0xD5` | B1 + B3 + B5 + B7 + B8 |
| 1 | 8–15 | B9–B16 | `0x00` | — |
| 2 | 16–23 | B17–B24 | `0x08` | B20 |
| 3 | 24–31 | B25–B32 | `0x08` | B28 |
| 4 | 32–39 | B33–B40 | `0xA0` | B38 + B40 |
| 5 | 40–47 | B41–B48 | `0x01` | B41 |

**Complete 6-byte value:**
```text
d5 00 08 08 a0 01
```

**NV 1877 — `NV_RF_BC_CONFIG_I`** (WCDMA/GSM band bitmask):

| Bit | Band |
| :--- | :--- |
| 7 | GSM DCS 1800 |
| 8 | GSM EGSM 900 |
| 9 | GSM PGSM 900 |
| 19 | GSM 850 |
| 21 | GSM PCS 1900 |
| 22 | WCDMA Band I (2100) |
| 26 | WCDMA Band V (850) |
| 49 | WCDMA Band VIII (900) |

**Complete 8-byte value:**
```text
80 03 68 04 00 00 02 00
```

### 3.2 Claimed band delta

| Technology | Factory | Claimed after unlock |
| :--- | :--- | :--- |
| LTE FDD | B1, B3, B5, B8 | B1, B3, B5, **B7**, B8, **B20**, **B28** |
| LTE TDD | none | **B38, B40, B41** |
| WCDMA/UMTS | B1, B5, B8 | unchanged |

### 3.3 NV read/write commands

The guide states that both commands must be **exactly 133 bytes** of payload:

```
CMD_NV_READ  (0x26):  u8 cmd | u16 nv_item LE | u8 data[128] (zeroes) | u16 status = 0
CMD_NV_WRITE (0x27):  u8 cmd | u16 nv_item LE | u8 data[128] (value)  | u16 status = 0
```

Response `status == 0` means `NV_DONE_S` (success). The guide attributes the common
`DIAG_BAD_LEN_F` (0x15) failure to omitting the trailing 2-byte status field.

### 3.4 The guide's transport assumption — and the conflict

The guide describes a `diag_bridge` daemon that forwards between `/dev/rpmsg0` and the USB
gadget serial `/dev/ttyGS0`, with the **host** speaking HDLC to `/dev/ttyACM0`:

- Frame delimiter `0x7E`, escape `0x7D` (`byte ^ 0x20`)
- CRC-16 polynomial `0x8408`, init `0xFFFF`, final XOR `0xFFFF`

**This conflicts with what has been directly verified for the HMU05 SMD transport**
(Doc 136 §4.5, Doc 137 §5). The conflict was re-tested during this session — see §5.1.

---

## 4. How This Relates to the UFI001B → HMU05 Port

Band configuration is **orthogonal** to the porting work but shares a failure mode:

| Concern | Owner | Reference |
| :--- | :--- | :--- |
| Which bands the modem *offers* | NV 6828 / 1877 (this doc) | `modemst1`/`modemst2`/`fsg` |
| Which bands the *RF front-end can actually serve* | WTR1605 + QFE2320 FEM | Doc 134 |
| Whether the modem *attaches* | MPSS attach / EPS | Doc 133 |

A band written into NV that the RF front-end cannot serve produces no useful capability —
and may produce exactly the kind of attach failure Doc 133/134 are chasing. **Any band
unlock must therefore be checked against the HMU05's RF capability before being trusted.**

---

## 5. Verification Status

### 5.1 Transport framing — RESOLVED

The guide's HDLC specification was tested against the HMU05's SMD DIAG transport using the
guide's own algorithm (implemented exactly: poly `0x8408`, init `0xFFFF`, XOR-out `0xFFFF`,
`0x7E` delimiters, `0x7D` escaping). The resulting `DIAG_VERNO_F` frame is:

```text
00 78 f0 7e        (CRC16([0x00]) = 0xF078)
```

| Framing | Request | Result on the HMU05 SMD transport |
| :--- | :--- | :--- |
| HDLC (guide §4.1) | `00 78 f0 7e` | **no valid reply** — 16-byte error-shaped response |
| **Raw** | `00` | **58-byte `DIAG_VERNO_F` reply** containing `Nov 21 2021…` |

**Conclusion:** for the HMU05 SMD/rpmsg transport the framing is **raw**, and the guide's
HDLC spec does **not** apply. This is consistent with Doc 136/137.

The most likely explanation is that the two documents describe **different transports**:
the guide targets the USB CDC-ACM link (`ttyACM0`), where Qualcomm's diag function does use
HDLC, whereas the SMD/rpmsg link used here does not. The guide is therefore not necessarily
*wrong* — it is **inapplicable to the path being used**.

> [!IMPORTANT]
> Because a **verified raw-transport DIAG client now exists** (`GitIgnore/compare/diag_logtool.c`,
> Doc 137), the NV read/write can be attempted over the SMD path **without HDLC** — using the
> guide's 133-byte payload layout verbatim. That is the correct way to test this.

### 5.2 Still unverified

| # | Claim | Status |
| :--- | :--- | :--- |
| 1 | NV 6828 / NV 1877 item IDs | **Unverified** on HMU05 |
| 2 | The mask values `d5 00 08 08 a0 01` / `80 03 68 04 00 00 02 00` | **Unverified** |
| 3 | The 133-byte `CMD_NV_READ`/`CMD_NV_WRITE` layout | **Unverified** on HMU05 (guide cites `DIAG_BAD_LEN_F` = 0x15) |
| 4 | That the unlocked images were ever flashed to any device | **Unverified** |
| 5 | That the HMU05 RF front-end (WTR1605 + QFE2320) supports B7/B20/B28/B38/B40/B41 | **Unverified** — and doubtful for some of them |
| 6 | That band unlocking helps the LTE attach failure | **Unverified** — and orthogonal to the current root cause (mode preference, Doc 133/134) |

### 5.3 A note on `DIAG_BAD_LEN_F`

The guide cites `0x15` as `DIAG_BAD_LEN_F`. That is **consistent** with the error family
verified in Doc 137 §5.1 (`0x13` = `BAD_CMD`, `0x14` = `BAD_PARM`), which supports `0x15` =
`BAD_LEN`. This is a point in the guide's favour and can be confirmed cheaply with a
deliberately truncated NV read once the DIAG bridge is re-established.

---

## 6. Proposed Next Steps (not executed)

Ordered by cost, cheapest first:

| # | Step | Why |
| :--- | :--- | :--- |
| 1 | Re-establish the DIAG bridge (Doc 137 §7.1) | Prerequisite for everything below |
| 2 | Send a truncated `CMD_NV_READ` and confirm the error is `0x15` | Validates the guide's error-code mapping at near-zero risk |
| 3 | Send a full 133-byte `CMD_NV_READ` for NV 6828 | Read-only; confirms the item ID and the current mask |
| 4 | Send a full 133-byte `CMD_NV_READ` for NV 1877 | Same |
| 5 | Compare the read-back masks against `d5 00 08 08 a0 01` / `80 03 68 04 00 00 02 00` | Tells us whether the HMU05 is already unlocked |
| 6 | Diff `fsg`/`modemst1`/`modemst2` against the `Unlocked Basedband` images | Non-invasive; may answer step 5 without touching the modem |
| 7 | Only then consider `CMD_NV_WRITE` — **after** backing up the partitions (§3 of the guide) | Writes are destructive and hard to reverse |

> [!CAUTION]
> Steps 6–7 touch persistent modem state. Back up `modemst1`, `modemst2` and `fsg` **before**
> any write, and note that a bad NV write can lose calibration data or the IMEI. Step 6 (an
> offline diff) is the safe way to learn whether the unlock is already present.

---

## 7. Artifacts

| Artifact | Path | Status |
| :--- | :--- | :--- |
| This document | `Docs/Modem Stability/138_MSM8916_BAND_UNLOCKING_NV_DIAG_REFERENCE.md` | Reference |
| Band guide (source) | `git show cb252f8:Docs/QUALCOMM_MSM8916_BASEBAND_BAND_UNLOCKING_GUIDE.md` | In git history only |
| Unlocked EFS images | `GitIgnore/MelbonWhiteStock_Dump/Unlocked Basedband/` | Reference only, never flashed here |
| Verified DIAG client | `GitIgnore/compare/diag_logtool.c` | Working (Doc 137) |
| Transport framing evidence | Doc 136 §4.5, Doc 137 §5.1 | Raw, not HDLC |
