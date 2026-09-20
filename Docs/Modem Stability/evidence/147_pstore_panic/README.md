# Evidence for Doc 147 — the pstore kernel panic

Raw, unedited copies taken from the device on 2026-09-20.

| file | origin | what it is |
| :--- | :--- | :--- |
| `dmesg-ramoops-0.txt` | `/sys/fs/pstore/dmesg-ramoops-0` | **The panic.** Written only on a kernel oops/panic. Contains the `bam_dmux_skb_dma_map` NULL dereference and its full call trace. Text is partly corrupted by the ramoops record format — see Doc 147 §4.2 for the de-garbled version. |
| `console-ramoops-0.txt` | `/sys/fs/pstore/console-ramoops-0` | Console of the boot that ended at 530 s uptime. Shows an **orderly** shutdown (usb0 down, wlan down, `timeout waiting for ssctl service`), i.e. that boot did **not** panic. |

Both files are also the reason the two boots can be told apart: `dmesg-ramoops-0` has mtime 16:10 UTC
(pre-fix module), `console-ramoops-0` 17:38 UTC (post-install, pre-reboot).

Live copies on the device are overwritten by the next panic; the record survives reboot otherwise.
