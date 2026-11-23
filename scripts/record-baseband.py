#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-only
"""Record a unit's baseband firmware identity into Docs/Boards/baseband/.

The dongles in this project ship several different basebands behind very
similar vendor version strings.  `HM02_MODEM_V1.2` in particular is printed by
two boards whose images are NOT the same bytes and do NOT advertise the same
radio capabilities, so the label alone cannot be used to decide what a board
supports.  This tool captures the facts that actually discriminate them:

  * the MPSS build path embedded in the segments (the real branch/version),
  * the per-segment md5 manifest (exact image identity),
  * the QMI device capabilities and the ModemManager mode list.

Typical use, with the unit reachable over the USB gadget link:

    scripts/record-baseband.py --name mifib-v18 --host 192.168.10.1
    scripts/record-baseband.py --name mifib-v12 --host 192.168.9.1

Only one unit can be attached at a time on the bench, so this is meant to be
run once per unit as it is plugged in.  Output is written to
Docs/Boards/baseband/<name>/ and the registry README is regenerated from all
the meta.json files that are present.

Everything is read-only on the device.
"""

import argparse
import datetime
import json
import re
import shlex
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT_ROOT = ROOT / "Docs" / "Boards" / "baseband"

# Remote probe.  Each section is printed with a marker line so it can be split
# apart without relying on the exact wording of the tool output.
REMOTE_SCRIPT = r"""
set -u
mark() { printf '\n@@@ %s\n' "$1"; }

mark model
cat /proc/device-tree/model 2>/dev/null; echo
mark compatible
tr '\0' ' ' < /proc/device-tree/compatible 2>/dev/null; echo
mark soc
cat /sys/devices/soc0/machine 2>/dev/null
mark qmi_software_version
qmicli -p -d /dev/wwan0qmi0 --dms-get-software-version 2>&1
mark qmi_hardware_revision
qmicli -p -d /dev/wwan0qmi0 --dms-get-hardware-revision 2>&1
mark qmi_capabilities
qmicli -p -d /dev/wwan0qmi0 --dms-get-capabilities 2>&1
mark nas_system_selection
qmicli -p -d /dev/wwan0qmi0 --nas-get-system-selection-preference 2>&1
mark modemmanager
mmcli -m any 2>&1
mark segments
ls -l /lib/firmware/modem.mdt /lib/firmware/modem.b* 2>/dev/null
mark segment_md5
md5sum /lib/firmware/modem.mdt /lib/firmware/modem.b* 2>/dev/null
mark mpss
strings -n 6 /lib/firmware/modem.b* 2>/dev/null \
  | grep -o 'CRMBuilds/[^/]*' \
  | sed 's#^CRMBuilds/##' \
  | sort -u
"""


def ssh(host, user, script, timeout=180):
    cmd = [
        "ssh", "-o", "StrictHostKeyChecking=no", "-o", "ConnectTimeout=10",
        "-o", "BatchMode=yes", f"{user}@{host}", script,
    ]
    proc = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
    if proc.returncode != 0 and not proc.stdout.strip():
        raise SystemExit(
            f"ssh to {user}@{host} failed (rc={proc.returncode})\n"
            f"{proc.stderr.strip()}"
        )
    return proc.stdout


def split_sections(text):
    sections = {}
    current = None
    buf = []
    for line in text.splitlines():
        if line.startswith("@@@ "):
            if current is not None:
                sections[current] = "\n".join(buf).strip()
            current = line[4:].strip()
            buf = []
        else:
            buf.append(line)
    if current is not None:
        sections[current] = "\n".join(buf).strip()
    return sections


def first_value(block, pattern, default=""):
    m = re.search(pattern, block or "")
    return m.group(1).strip() if m else default


def clean_text(value):
    """Device-tree properties are NUL terminated; cat prints the NUL."""
    return " ".join(value.replace("\x00", " ").split())


def parse_modes(block):
    """Pull the ModemManager 'Modes' rows into (supported, current) lists.

    The table looks like

        Modes    |         supported: allowed: 2g; preferred: none
                 |                    allowed: 3g; preferred: none
                 |           current: allowed: 4g; preferred: none

    so the label only appears on the first (supported) and last (current) row
    and the rows in between belong to whichever label came last.
    """
    supported, current = [], []
    state = None
    for line in (block or "").splitlines():
        if "|" not in line:
            continue
        cell = line.split("|")[-1].strip()
        if cell.startswith("supported:"):
            state = supported
            cell = cell[len("supported:"):].strip()
        elif cell.startswith("current:"):
            state = current
            cell = cell[len("current:"):].strip()
        if state is not None and cell.startswith("allowed:"):
            state.append(cell)
    return supported, current


def parse_md5(block):
    out = {}
    for line in (block or "").splitlines():
        parts = line.split()
        if len(parts) == 2 and re.fullmatch(r"[0-9a-f]{32}", parts[0]):
            out[Path(parts[1]).name] = parts[0]
    return out


def build_meta(name, host, sections):
    md5s = parse_md5(sections.get("segment_md5", ""))
    supported, current = parse_modes(sections.get("modemmanager", ""))
    mpss_lines = [l.strip() for l in sections.get("mpss", "").splitlines() if l.strip()]
    return {
        "board": name,
        "host": host,
        "captured": datetime.date.today().isoformat(),
        "complete": bool(md5s) and "modem.mdt" in md5s,
        "dt_model": clean_text(first_value(sections.get("model"), r"^(.*)$")),
        "dt_compatible": clean_text(first_value(sections.get("compatible"), r"^(.*)$")),
        "soc": first_value(sections.get("soc"), r"^(.*)$"),
        "vendor_sw_version": first_value(
            sections.get("qmi_software_version"), r"Software version:\s*(.+)$"),
        "hw_revision": first_value(
            sections.get("qmi_hardware_revision"), r"Revision:\s*'([^']+)'"),
        "dms_networks": first_value(
            sections.get("qmi_capabilities"), r"Networks:\s*'([^']+)'"),
        "mm_revision": first_value(
            sections.get("modemmanager"), r"firmware revision:\s*(.+)$"),
        "mm_hw_revision": first_value(
            sections.get("modemmanager"), r"h/w revision:\s*(.+)$"),
        "nas_mode_preference": first_value(
            sections.get("nas_system_selection"), r"Mode preference:\s*'([^']+)'"),
        "mpss": mpss_lines[0] if mpss_lines else "",
        "modem_mdt_md5": md5s.get("modem.mdt", ""),
        "segment_count": len([k for k in md5s if k != "modem.mdt"]),
        "segment_md5": dict(sorted(md5s.items())),
        "supported_modes": supported,
        "current_modes": current,
    }


def write_unit(name, meta, sections):
    out = OUT_ROOT / name
    out.mkdir(parents=True, exist_ok=True)

    (out / "identity.txt").write_text(
        "\n".join(f"@@@ {k}\n{v}" for k, v in sections.items()) + "\n",
        encoding="utf-8")

    if meta["segment_md5"]:
        lines = [f"{v}  {k}" for k, v in meta["segment_md5"].items()]
        (out / "segments.md5").write_text("\n".join(lines) + "\n",
                                          encoding="utf-8")

    (out / "meta.json").write_text(
        json.dumps(meta, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return out


def regenerate_readme():
    rows = []
    for meta_path in sorted(OUT_ROOT.glob("*/meta.json")):
        meta = json.loads(meta_path.read_text(encoding="utf-8"))
        mpss = meta.get("mpss") or "not captured"
        # Keep only the branch-version token, the full build id is in meta.json.
        m = re.match(r"(MPSS\.[A-Za-z0-9]+(?:\.[0-9]+)+)", mpss)
        branch = m.group(1) if m else mpss
        rows.append(
            "| `{board}` | {model} | {branch} | {sw} | {hw} | `{mdt}` | {nets} | {state} |".format(
                board=meta.get("board", "?"),
                model=meta.get("dt_model", "?"),
                branch=branch,
                sw=meta.get("vendor_sw_version", "?"),
                hw=meta.get("hw_revision", "?"),
                mdt=(meta.get("modem_mdt_md5", "") or "-")[:12],
                nets=meta.get("dms_networks", "?"),
                state="complete" if meta.get("complete") else "partial",
            ))

    text = """# Baseband firmware registry

One entry per unit, captured from the device itself.  Run

    scripts/record-baseband.py --name <board> --host <unit-ip>

with the unit attached.  Only one unit can be plugged in at a time, so a unit
shows as `partial` until it has been probed.

`HM02_MODEM_V1.2` is a vendor label, not a version.  Two boards can print it
while carrying different images with different radio capabilities, so always
compare `modem.mdt` md5 and the MPSS build string, never the label.

| board | device tree model | MPSS branch | vendor label | hw rev | mdt md5 | DMS networks | state |
|---|---|---|---|---|---|---|---|
""" + "\n".join(rows) + "\n"
    (OUT_ROOT / "README.md").write_text(text, encoding="utf-8")


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--name", help="board codename, e.g. mifib-v18")
    ap.add_argument("--host", help="unit address, e.g. 192.168.10.1")
    ap.add_argument("--user", default="root")
    ap.add_argument("--no-readme", action="store_true",
                    help="do not regenerate the registry README")
    ap.add_argument("--readme-only", action="store_true",
                    help="just rebuild the registry README from existing entries")
    args = ap.parse_args()

    if args.readme_only:
        regenerate_readme()
        print(f"regenerated {OUT_ROOT / 'README.md'}")
        return
    if not args.name or not args.host:
        ap.error("--name and --host are required unless --readme-only is used")

    raw = ssh(args.host, args.user, REMOTE_SCRIPT)
    sections = split_sections(raw)
    if "model" not in sections:
        raise SystemExit("probe returned no device-tree model; is the unit up?")

    meta = build_meta(args.name, args.host, sections)
    out = write_unit(args.name, meta, sections)

    if not args.no_readme:
        regenerate_readme()

    print(f"wrote {out}")
    print(f"  model        {meta['dt_model']}")
    print(f"  vendor label {meta['vendor_sw_version']}  hw {meta['hw_revision']}")
    print(f"  MPSS         {meta['mpss'] or 'not captured'}")
    print(f"  networks     {meta['dms_networks']}")
    print(f"  modem.mdt    {meta['modem_mdt_md5']}")
    print(f"  segments     {meta['segment_count']}")
    for entry in meta["current_modes"]:
        print(f"  current mode {entry}")


if __name__ == "__main__":
    main()
