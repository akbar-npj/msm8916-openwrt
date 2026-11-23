# mifib-v12 — PARTIAL capture

This entry was assembled from targeted probes rather than the full
`scripts/record-baseband.py` run, because only one unit can be attached to the
bench at a time and the V18 was plugged in afterwards.

Recorded and reliable:

* device tree model and compatible string
* vendor label (`HM02_MODEM_V1.2`), QMI h/w revision `10000`
* MPSS build: `MPSS.DPM.2.0.2.c1-00178-M8936FAAAANUZM-1_20161104_023832`
* QMI capabilities: `umts, lte` (no CDMA — this is what differs from the V18)
* ModemManager mode list: `4g`-alone is advertised and was the current mode
* `modem.mdt`, `modem.b01`, `modem.b05` md5

Missing: the md5 of every other `modem.bNN` segment.

To complete it, plug the unit in and run:

    scripts/record-baseband.py --name mifib-v12 --host 192.168.9.1

That overwrites this directory with the full manifest and flips `complete` to
true in the registry.
