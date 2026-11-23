# Baseband firmware registry

One entry per unit, captured from the device itself.  Run

    scripts/record-baseband.py --name <board> --host <unit-ip>

with the unit attached.  Only one unit can be plugged in at a time, so a unit
shows as `partial` until it has been probed.

`HM02_MODEM_V1.2` is a vendor label, not a version.  Two boards can print it
while carrying different images with different radio capabilities, so always
compare `modem.mdt` md5 and the MPSS build string, never the label.

| board | device tree model | MPSS branch | vendor label | hw rev | mdt md5 | DMS networks | state |
|---|---|---|---|---|---|---|---|
| `mifib-v12` | HM-MIFIB-V12 4G LTE USB WiFi Dongle | MPSS.DPM.2.0.2 | HM02_MODEM_V1.2 | 10000 | `38b11d1b4ded` | umts, lte | complete |
| `mifib-v18` | HM-MIFIB-V18 | MPSS.DPM.2.0.2 | HM02_MODEM_V1.2 | 10000 | `4a7fcf5bcd5e` | cdma20001x, evdo, umts, lte | complete |
