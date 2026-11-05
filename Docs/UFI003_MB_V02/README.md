# UFI003 MB V02 — stock device-tree reference

This directory keeps the stock Qualcomm device-tree entry most consistent
with the UFI003 stock description, beside the raw DTB from which the DTS was
decompiled.

## Files

| File | Description |
|---|---|
| `dtb02.dtb` | Raw entry 02 from the 33-entry QCDT table extracted from the stock boot image |
| `dtb02.dts` | Decompiled source for that raw DTB |

Entry 02 identifies itself as **Qualcomm Technologies, Inc. MSM 8916 MTP**,
with `qcom,board-id = <0x08 0x00>`. The existing
[`Docs/UFI003_MB_V02.md`](../UFI003_MB_V02.md) notes that the stock DT root
reports a generic MSM8916 MTP identity. This entry is presumed, not verified, to be the UFI003 entry: its MSM8916 MTP model and board ID fit the existing reference’s generic MTP description. No captured stock bootloader/SMEM selection or /proc/device-tree dump ties the running board to index 02; the vendor DT has no UFI003 product label.

## All extracted QCDT entries

Indexes use the extracted filenames `dtb00` through `dtb32`.

| Index | Model | Board ID |
|---:|---|---|
| 00 | MSM 8916 CDP | `0x01/0x00` |
| 01 | MSM 8916 SMB1360 CDP | `0x01/0x01` |
| 02 | MSM 8916 MTP | `0x08/0x00` |
| 03 | MSM 8916 SMB1360 MTP | `0x08/0x01` |
| 04 | MSM 8916 512MB MTP | `0x08/0x100` |
| 05 | MSM 8916 512MB SMB1360 MTP | `0x08/0x101` |
| 06 | MSM8916 RUMI | `0x0f/0x00` |
| 07 | MSM8916 Simulator | `0x10/0x00` |
| 08 | MSM 8916 QRD SKUH | multiple QRD IDs |
| 09 | MSM 8916 QRD SKUI | multiple QRD IDs |
| 10 | MSM 8916 512MB QRD SKUH | multiple QRD IDs |
| 11 | MSM 8916 512MB QRD SKUI | multiple QRD IDs |
| 12 | MSM 8916 QRD SKUHF | multiple QRD IDs |
| 13 | MSM 8916 QRD SKUIC | `0x11010b/0x105` |
| 14 | MSM 8916 QRD SKUID | multiple QRD IDs |
| 15 | MSM 8916 512MB QRD SKUID | multiple QRD IDs |
| 16 | MSM 8936 CDP | `0x01/0x00` |
| 17 | MSM 8936 SMB1360 CDP | `0x01/0x01` |
| 18 | MSM 8936 SMB1360 MTP | `0x08/0x00` |
| 19 | MSM 8936 MTP | `0x08/0x01` |
| 20 | MSM 8936 WCD9306 MTP | `0x08/0x02` |
| 21 | MSM8939 QRD SKUK | `0x1000b/0x05` |
| 22 | MSM 8939 CDP | `0x01/0x00` |
| 23 | MSM 8939 SMB1360 CDP | `0x01/0x01` |
| 24 | MSM 8939 SMB1360 MTP | `0x08/0x00` |
| 25 | MSM 8939 MTP | `0x08/0x01` |
| 26 | MSM 8939 WCD9306 MTP | `0x08/0x02` |
| 27 | MSM8939 RUMI | `0x0f/0x00` |
| 28 | MSM8939 Simulator | `0x10/0x00` |
| 29 | MSM8939 QRD SKUK | multiple QRD IDs |
| 30 | APQ 8016 CDP | `0x01/0x00` |
| 31 | APQ 8016 MTP | `0x08/0x00` |
| 32 | APQ 8016 SMB1360 MTP | `0x08/0x01` |

## OpenWrt comparison

The OpenWrt board DTS is added by
[`msm89xx/patches/852-arm64-dts-qcom-add-msm8916-generic-ufi003.patch`](../../msm89xx/patches/852-arm64-dts-qcom-add-msm8916-generic-ufi003.patch);
its upstream path is
`arch/arm64/boot/dts/qcom/msm8916-generic-ufi003_mb_v02.dts`.
The stock `bam_dmux` node has no `qcom,a2-pin`; OpenWrt patch 872 adds
that property for UFI003 modem power-domain handling.

## Provenance

The raw DTB and decompiled DTS came from the stock boot-image QCDT extraction
on the old build host. The raw DTB is authoritative; decompilation can vary
between dtc versions.
