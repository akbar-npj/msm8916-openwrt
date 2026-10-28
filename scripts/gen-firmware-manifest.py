#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-only
#
# gen-firmware-manifest.py - emit firmware.json for the release pipeline.
#
# Reads the device->supported_devices mapping straight from the image Makefile
# (the same source of truth the build uses), finds each board's built
# sysupgrade image, computes its sha256, and writes a manifest that the
# firmware-upgrade script on the device can consume.
#
# The manifest itself is published to GitHub Pages, but the sysupgrade
# images are NOT copied there by the CI (only packages + the manifest are).
# So the per-board `sysupgrade` URL must point at the GitHub *Release asset*
# download URL, not the Pages path.  Pass --release-base for that; if omitted
# it falls back to the Pages path (which would 404 on the image).

import argparse
import hashlib
import json
import os
import re
import sys


def parse_devices(mk_path):
    """Return [(device_name, [supported_devices]), ...] from the image Makefile."""
    devices = []
    cur = None
    cur_devs = None
    with open(mk_path) as fh:
        for line in fh:
            m = re.match(r"\s*define\s+Device/(\S+)", line)
            if m:
                if cur is not None:
                    devices.append((cur, cur_devs or []))
                cur = m.group(1)
                cur_devs = None
                continue
            if cur is not None:
                m2 = re.match(r"\s*SUPPORTED_DEVICES\s*:=\s*(.+)", line)
                if m2:
                    # SUPPORTED_DEVICES is a whitespace-separated list; each
                    # entry is a full board name that may itself contain a
                    # comma (e.g. "mifib-v12,generic"), so split on spaces only.
                    cur_devs = [
                        d for d in m2.group(1).split() if d
                    ]
    if cur is not None:
        devices.append((cur, cur_devs or []))
    return devices


def find_sysupgrade(bin_dir, device):
    """Locate the squashfs-sysupgrade.bin for a device; return path or None."""
    suffix = "-%s-squashfs-sysupgrade.bin" % device
    matches = []
    for root, _dirs, files in os.walk(bin_dir):
        for name in files:
            if name.endswith(suffix):
                matches.append(os.path.join(root, name))
    if not matches:
        return None
    matches.sort(key=lambda p: 0 if ("-%s-squashfs" % device) in os.path.basename(p) else 1)
    return matches[0]


def sha256_of(path):
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--mk", required=True, help="path to msm8916.mk")
    ap.add_argument("--bin-dir", required=True, help="OpenWrt target bin dir")
    ap.add_argument("--version", required=True, help="full version, e.g. 25.12.6-r2")
    ap.add_argument("--pages-base", required=True,
                    help="GitHub Pages base URL, no trailing slash")
    ap.add_argument("--release-base", default=None,
                    help="Base URL for the actual sysupgrade image download, e.g. "
                         "https://github.com/owner/repo/releases/download/v1.2.3. "
                         "Defaults to <pages-base>/releases/<base>/targets/msm89xx/msm8916 "
                         "if omitted; prefer the release-asset URL because the CI does "
                         "not publish the .bin images to GitHub Pages.")
    ap.add_argument("--out", required=True, help="output firmware.json path")
    ap.add_argument("--min-version", default=None,
                    help="minimum supported version (refuses auto-upgrade below)")
    ap.add_argument("--notes", default="", help="optional global notes string")
    args = ap.parse_args()

    base = re.split(r"-r\d+$", args.version)[0]

    devices = parse_devices(args.mk)
    if not devices:
        sys.exit("error: no devices parsed from %s" % args.mk)

    base_url = "%s/releases/%s/targets/msm89xx/msm8916" % (args.pages_base, base)

    boards = {}
    missing = []
    for device, supported in devices:
        img = find_sysupgrade(args.bin_dir, device)
        if img is None:
            missing.append(device)
            sys.stderr.write("warning: no sysupgrade image for device %s\n" % device)
            continue
        fname = os.path.basename(img)
        url = "%s/%s" % (args.release_base or base_url, fname)
        sha = sha256_of(img)
        size = os.path.getsize(img)
        entry = {
            "version": args.version,
            "sysupgrade": url,
            "sha256": sha,
            "size": size,
            "min_version": args.min_version or "",
            "notes": args.notes,
        }
        if not supported:
            sys.stderr.write("warning: device %s has no SUPPORTED_DEVICES, skipping\n" % device)
            missing.append(device)
            continue
        for sd in supported:
            boards[sd] = dict(entry)

    manifest = {
        "manifest_version": 1,
        "release": args.version,
        "base_url": base_url,
        "boards": boards,
    }

    out_dir = os.path.dirname(os.path.abspath(args.out))
    if out_dir and not os.path.isdir(out_dir):
        os.makedirs(out_dir, exist_ok=True)
    with open(args.out, "w") as fh:
        json.dump(manifest, fh, indent=2, sort_keys=True)
        fh.write("\n")

    n_boards = len(boards)
    sys.stderr.write(
        "wrote %s: %d board keys from %d devices (%d without images)\n"
        % (args.out, n_boards, len(devices), len(missing))
    )
    if not boards:
        sys.exit("error: manifest has zero boards; aborting")


if __name__ == "__main__":
    main()
