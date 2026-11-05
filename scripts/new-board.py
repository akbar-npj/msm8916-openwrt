#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-only
"""Scaffold board support from a stock/vendor device tree.

The OpenWrt tree already carries every dtsi a board needs.  What a new board
is missing is its *board DTS* (LEDs, buttons, battery, SIM switch lines) and
the five places that register it.  This tool reads the vendor DTB, reports
what it can map, and writes the board DTS plus the plumbing.

    scripts/new-board.py --dtb stock.dtb --inspect
    scripts/new-board.py --dtb stock.dtb --name jz02 --reference generic-uf02

Nothing is written without --write, and every mapping it cannot make with
confidence is left as a TODO in the generated DTS instead of being guessed.

Run from the repository root.  Requires dtc/fdtget (already used by the
build host).
"""

import argparse
import os
import re
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

LED_FUNCTIONS = [
    (r"wlan|wifi", "LED_FUNCTION_WLAN"),
    (r"4g|lte|wwan|\bwan\b|signal|net", "LED_FUNCTION_WAN"),
    (r"\bbat|charg|chg", "LED_FUNCTION_CHARGING"),
    (r"power|pwr", "LED_FUNCTION_POWER"),
]

# Vendor gpio-leds nodes double as GPIO outputs on these boards: a SIM switch
# or an enable line is exposed as an LED so Android userspace can write
# /sys/class/leds/<label>/brightness.  Keep the stock label, make it a plain
# output, and do not pretend it is an indicator.
GPIO_LINE = re.compile(r"sim|_en$|_en\d", re.I)

LED_COLORS = [
    (r"red", "LED_COLOR_ID_RED"),
    (r"green", "LED_COLOR_ID_GREEN"),
    (r"blue", "LED_COLOR_ID_BLUE"),
    (r"white", "LED_COLOR_ID_WHITE"),
    (r"amber|yellow|orange", "LED_COLOR_ID_AMBER"),
]

KEYS = {
    116: "KEY_POWER",
    114: "KEY_VOLUMEDOWN",
    115: "KEY_VOLUMEUP",
    0x198: "KEY_RESTART",
    0x211: "KEY_WPS_BUTTON",
}


def die(msg):
    sys.stderr.write("new-board: %s\n" % msg)
    sys.exit(1)


def sh(cmd, check=True):
    p = subprocess.run(cmd, capture_output=True, text=True)
    if check and p.returncode != 0:
        die("%s failed: %s" % (" ".join(cmd), p.stderr.strip()))
    return p


def to_dtb(path):
    """Return (dtb_path, cleanup) for a .dtb or a decompiled .dts."""
    if path.endswith(".dtb"):
        return path, None
    tmp = tempfile.NamedTemporaryFile(suffix=".dtb", delete=False)
    tmp.close()
    p = sh(["dtc", "-I", "dts", "-O", "dtb", "-o", tmp.name, path], check=False)
    if p.returncode != 0:
        os.unlink(tmp.name)
        die("%s is not compilable as a standalone DTS (includes?):\n%s\n"
            "Pass the .dtb from the stock firmware instead." % (path, p.stderr.strip()))
    return tmp.name, tmp.name


def walk(dtb):
    """All nodes as (path, name), breadth first."""
    out = []
    todo = ["/"]
    while todo:
        path = todo.pop()
        p = sh(["fdtget", "-l", dtb, path], check=False)
        if p.returncode != 0:
            continue
        for name in p.stdout.split():
            child = path.rstrip("/") + "/" + name
            out.append((child, name))
            todo.append(child)
    return out


def sget(dtb, path, prop):
    p = sh(["fdtget", "-t", "s", dtb, path, prop], check=False)
    if p.returncode != 0:
        return None
    return p.stdout.strip()


def xget(dtb, path, prop):
    p = sh(["fdtget", "-t", "x", dtb, path, prop], check=False)
    if p.returncode != 0:
        return None
    return [int(v, 16) for v in p.stdout.split()]


def compat_list(dtb, path="/"):
    # fdtget prints a string list space-separated; compatible strings never
    # contain whitespace, so splitting on it is safe (unlike model).
    raw = sget(dtb, path, "compatible")
    return raw.split() if raw else []


def slugify(text):
    s = re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")
    return s


def pick_slug(compat, model, forced):
    if forced:
        return slugify(forced)
    for c in compat:
        if not c.startswith("qcom,"):
            return slugify(c.split(",")[-1])
    return None


def gpio_of(cells):
    """(pin, active_low) from a <&gpio pin flags> property."""
    if not cells or len(cells) < 2:
        return None
    pin = cells[1]
    flags = cells[2] if len(cells) > 2 else 0
    return pin, bool(flags & 1)


def collect(dtb):
    info = {
        "model": sget(dtb, "/", "model") or "",
        "compat": compat_list(dtb),
        "leds": [],
        "keys": [],
        "battery": {},
        "lut": None,
        "display": [],
    }
    nodes = walk(dtb)
    for path, name in nodes:
        parent = path.rsplit("/", 1)[0] or "/"
        pname = parent.rsplit("/", 1)[-1].lower()
        if pname in ("gpio-leds", "gpio_leds", "leds"):
            g = gpio_of(xget(dtb, path, "gpios"))
            if not g:
                continue
            label = sget(dtb, path, "label") or name
            info["leds"].append({
                "label": label,
                "pin": g[0],
                "low": g[1],
                "state": sget(dtb, path, "default-state") or "off",
            })
        elif pname in ("gpio-keys", "gpio_keys"):
            g = gpio_of(xget(dtb, path, "gpios"))
            code = xget(dtb, path, "linux,code")
            if not g:
                continue
            info["keys"].append({
                "label": sget(dtb, path, "label") or name,
                "pin": g[0],
                "low": g[1],
                "code": code[0] if code else None,
            })
        elif re.search(r"mdss|dsi|simple-panel", sget(dtb, path, "compatible") or ""):
            info["display"].append(path)

        for prop in ("qcom,max-voltage-uv", "qcom,v-cutoff-uv",
                     "qcom,fcc-mah", "qcom,nom-batt-capacity-mah",
                     "qcom,chg-term-ua", "qcom,fastchg-current-ma"):
            v = xget(dtb, path, prop)
            if v:
                info["battery"][prop] = v[0]
        btype = sget(dtb, path, "qcom,battery-type")
        if btype:
            info["battery"]["qcom,battery-type"] = btype
        lut = xget(dtb, path, "qcom,lut-data")
        cols = xget(dtb, path, "qcom,lut-col-legend")
        rows = xget(dtb, path, "qcom,lut-row-legend")
        if (name == "qcom,pc-temp-ocv-lut" and lut and cols and rows
                and len(lut) == len(cols) * len(rows)):
            info["lut"] = {"cols": cols, "rows": rows, "data": lut}
    return info


def led_lines(info, warn):
    out = []
    pins = []
    for led in info["leds"]:
        label = led["label"]
        node = "led-" + slugify(label)
        pins.append(led["pin"])
        low = "GPIO_ACTIVE_LOW" if led["low"] else "GPIO_ACTIVE_HIGH"
        if GPIO_LINE.search(label):
            out.append("\t%s {\n\t\tlabel = \"%s\";\n\t\tgpios = <&tlmm %d %s>;\n"
                       "\t\tdefault-state = \"off\";\n\t};\n"
                       % (node, label, led["pin"], low))
            continue
        func = next((m for p, m in LED_FUNCTIONS if re.search(p, label, re.I)), None)
        color = next((m for p, m in LED_COLORS if re.search(p, label, re.I)), None)
        if not func and not color:
            warn.append("LED '%s' (gpio %d): no function or colour in the label, "
                        "emitted as a plain GPIO" % (label, led["pin"]))
        body = "\t%s {\n\t\tlabel = \"%s\";\n\t\tgpios = <&tlmm %d %s>;\n" % (
            node, label, led["pin"], low)
        if func:
            body += "\t\tfunction = %s;\n" % func
        if color:
            body += "\t\tcolor = <%s>;\n" % color
        else:
            body += "\t\t/* TODO colour: red / green / blue, from the board */\n"
        body += "\t\tdefault-state = \"%s\";\n\t};\n" % led["state"]
        out.append(body)
    return out, sorted(set(pins))


def key_lines(info, warn):
    out = []
    reset = None
    for key in info["keys"]:
        label = key["label"]
        low = "GPIO_ACTIVE_LOW" if key["low"] else "GPIO_ACTIVE_HIGH"
        if re.search(r"freset|reset|restart", label, re.I):
            reset = key
            continue
        if re.search(r"wps", label, re.I):
            code = "KEY_WPS_BUTTON"
        elif key["code"] in KEYS:
            code = KEYS[key["code"]]
        else:
            warn.append("key '%s' (gpio %d): linux,code %s has no known KEY_* "
                        "name, emitted as KEY_UNKNOWN" % (label, key["pin"], key["code"]))
            code = "KEY_UNKNOWN"
        out.append("\tkey-%s {\n\t\tlabel = \"%s\";\n\t\tgpios = <&tlmm %d %s>;\n"
                   "\t\tlinux,code = <%s>;\n\t};\n"
                   % (slugify(label), label, key["pin"], low, code))
    return out, reset


def signed(v):
    return v - (1 << 32) if v >= (1 << 31) else v


def battery_block(info, warn):
    b = info["battery"]
    if not b:
        return None
    vmax = b.get("qcom,max-voltage-uv")
    vmin = b.get("qcom,v-cutoff-uv")
    mah = b.get("qcom,fcc-mah") or b.get("qcom,nom-batt-capacity-mah")
    term = b.get("qcom,chg-term-ua")
    fast = b.get("qcom,fastchg-current-ma")
    btype = b.get("qcom,battery-type")
    if not (vmax and vmin and mah):
        warn.append("battery profile is incomplete (voltage or capacity missing); "
                    "emitted a commented template only")
        return ("\t/* TODO battery: the stock profile lacks voltage/capacity.\n"
                "\t * Fill in the cell's real values before enabling the gauge. */\n")
    lines = ["\t/*\n\t * Values taken from the stock profile%s.  Check them\n"
             "\t * against the pack label: vendors ship a reference profile that\n"
             "\t * often does not match the fitted cell.\n\t */\n"
             % (" '%s'" % btype if btype else ""),
             "\tbattery: battery {\n", "\t\tcompatible = \"simple-battery\";\n",
             "\t\tvoltage-min-design-microvolt = <%d>;\n" % vmin,
             "\t\tvoltage-max-design-microvolt = <%d>;\n" % vmax,
             "\t\tcharge-full-design-microamp-hours = <%d>;\n" % (mah * 1000)]
    if term:
        lines.append("\t\tcharge-term-current-microamp = <%d>;\n" % term)
    if fast:
        lines.append("\t\tconstant-charge-current-max-microamp = <%d>;\n" % (fast * 1000))
    lines.append("\t\tconstant-charge-voltage-max-microvolt = <%d>;\n" % vmax)

    lut = info["lut"]
    if lut:
        cols = [signed(c) for c in lut["cols"]]
        rows = lut["rows"]
        data = lut["data"]
        n = len(cols)
        lines.append("\t\tocv-capacity-celsius = <%s>;\n"
                     % " ".join("(%d)" % c if c < 0 else str(c) for c in cols))
        for c in range(n):
            pairs = ["<%d %d>" % (data[r * n + c] * 1000, rows[r])
                     for r in range(len(rows))]
            lines.append("\t\tocv-capacity-table-%d =\n" % c)
            for i in range(0, len(pairs), 4):
                lines.append("\t\t\t%s%s\n" % (", ".join(pairs[i:i + 4]),
                                               ";" if i + 4 >= len(pairs) else ","))
    else:
        warn.append("no qcom,pc-temp-ocv-lut in the stock profile; the OCV "
                    "table still has to be written by hand")
        lines.append("\t\t/* TODO ocv-capacity-table-0..N: not in the stock DTB */\n")
    lines.append("\t};\n")
    return "".join(lines)


def build_dts(slug, info, name, vendor, warn, source):
    compat = [c for c in info["compat"] if not c.startswith("qcom,")]
    if not compat:
        compat = ["%s,generic" % slug]
    led, pins = led_lines(info, warn)
    keys, reset = key_lines(info, warn)
    batt = battery_block(info, warn)

    out = []
    out.append("// SPDX-License-Identifier: GPL-2.0-only\n")
    out.append("/*\n * %s - %s MSM8916 board.\n *\n" % (info["model"] or name, vendor))
    out.append(" * Generated by scripts/new-board.py from %s.\n" % os.path.basename(source))
    out.append(" * Review every TODO and verify the GPIOs on hardware before\n"
               " * calling the board supported.\n */\n\n")
    out.append("/dts-v1/;\n\n#include \"msm8916-ufi.dtsi\"\n\n")
    out.append("#include <dt-bindings/gpio/gpio.h>\n")
    out.append("#include <dt-bindings/leds/common.h>\n")
    out.append("#include <dt-bindings/input/linux-event-codes.h>\n\n")
    out.append("/ {\n")
    out.append("\tmodel = \"%s\";\n" % (info["model"] or name))
    out.append("\tcompatible = \"%s\", \"qcom,msm8916\";\n" % compat[0])
    if batt:
        out.append("\n" + batt)
    out.append("};\n\n")

    if led:
        out.append("&{/leds} {\n")
        out.append("\t/delete-node/ led-r;\n\t/delete-node/ led-g;\n\t/delete-node/ led-b;\n\n")
        out.extend(led)
        out.append("};\n\n")
        if pins:
            out.append("&gpio_leds_default {\n\tpins = \"%s\";\n};\n\n"
                       % "\", \"".join("gpio%d" % p for p in pins))

    if keys:
        out.append("&{/gpio-keys} {\n")
        out.extend(keys)
        out.append("};\n\n")

    if reset:
        low = "GPIO_ACTIVE_LOW" if reset["low"] else "GPIO_ACTIVE_HIGH"
        out.append("&button_restart {\n\tgpios = <&tlmm %d %s>;\n};\n\n"
                   % (reset["pin"], low))

    if batt:
        out.append("&pm8916_bms {\n\tmonitored-battery = <&battery>;\n"
                   "\tpower-supplies = <&pm8916_charger>;\n\tstatus = \"okay\";\n};\n\n")

    if info["display"]:
        out.append("/* TODO display: the stock DTB carries %d display node(s):\n"
                   % len(info["display"]))
        for p in info["display"]:
            out.append(" *   %s\n" % p)
        out.append(" * Mainline panel bindings differ; wire the panel by hand.\n */\n")

    return "".join(out)


def next_patch_number(patches):
    used = set()
    for f in os.listdir(patches):
        m = re.match(r"^(8\d\d)-", f)
        if m:
            used.add(int(m.group(1)))
    n = 802
    while n in used:
        n += 1
    return n


def make_patch(dts_name, body):
    lines = body.rstrip("\n").split("\n")
    out = ["--- /dev/null\n",
           "+++ b/arch/arm64/boot/dts/qcom/%s.dts\n" % dts_name,
           "@@ -0,0 +1,%d @@\n" % len(lines)]
    out.extend("+" + ln + "\n" for ln in lines)
    return "".join(out)


def add_to_801(text, dts_name):
    lines = text.split("\n")
    last = None
    for i, ln in enumerate(lines):
        if ln.startswith("+dtb-$(CONFIG_ARCH_QCOM)") and "msm8916-generic-" in ln:
            last = i
    if last is None:
        die("801 patch has no msm8916-generic- line to extend")
    lines.insert(last + 1, "+dtb-$(CONFIG_ARCH_QCOM)\t+= %s.dtb" % dts_name)
    for i, ln in enumerate(lines):
        m = re.match(r"^(@@ -\d+,\d+ \+\d+,)(\d+)( @@)(.*)$", ln)
        if m and last + 1 > i:
            lines[i] = "%s%d%s%s" % (m.group(1), int(m.group(2)) + 1, m.group(3), m.group(4))
            break
    return "\n".join(lines)


def add_to_build_yml(text, slug):
    """The Tests harness requires build.yml's choice list to equal diffconfigs."""
    lines = text.split("\n")
    try:
        start = next(i for i, ln in enumerate(lines) if ln.strip() == "options:")
    except StopIteration:
        die("build.yml has no options: block")
    end = start + 1
    while end < len(lines) and re.match(r"^\s+- \S+$", lines[end]):
        end += 1
    entries = [ln.split("- ", 1)[1].strip() for ln in lines[start + 1:end]]
    if slug in entries:
        return text
    indent = re.match(r"^(\s+)", lines[start + 1]).group(1)
    lines.insert(end, indent + "- " + slug)
    return "\n".join(lines)


def device_block(slug, info, vendor, reference_mk):
    """Clone the reference profile's package list, write a clean block."""
    m = re.search(r"^define Device/%s\n(.*?)^endef" % re.escape(reference_mk),
                  MK_TEXT, re.S | re.M)
    if not m:
        die("reference device %s not found in msm8916.mk" % reference_mk)
    body = m.group(1)
    pk = re.search(r"^\tDEVICE_PACKAGES :=(.*?)(?=^\t[A-Z_]+ :=|^\Z)", body, re.S | re.M)
    packages = pk.group(1).strip() if pk else "msm89xx-scripts wpad-basic-wolfssl rmtfs uci-usb-gadget"
    compat = [c for c in info["compat"] if not c.startswith("qcom,")]
    supported = " ".join(compat) if compat else "%s,generic" % slug
    return (
        "define Device/generic-%s\n"
        "\t$(Device/msm8916)\n"
        "\tDEVICE_VENDOR := %s\n"
        "\tDEVICE_MODEL := %s\n"
        "\tDEVICE_DTS := msm8916-generic-%s\n"
        "\tSUPPORTED_DEVICES := %s\n"
        "\tFILESYSTEMS := squashfs\n"
        "\tDEVICE_PACKAGES := %s\n"
        "endef\n"
        "TARGET_DEVICES += generic-%s\n\n" % (
            slug, vendor, info["model"] or slug, slug, supported, packages, slug))


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--dtb", required=True, help="stock .dtb (or a decompiled .dts)")
    ap.add_argument("--name", help="board slug when the DTS does not identify itself")
    ap.add_argument("--vendor", default="Generic")
    ap.add_argument("--model", help="override the model string")
    ap.add_argument("--reference", default="uf02",
                    help="board (diffconfig name) to copy the profile from")
    ap.add_argument("--maintainer", help="add a MAINTAINERS.md row for this handle")
    ap.add_argument("--inspect", action="store_true", help="report and exit")
    ap.add_argument("--write", action="store_true", help="write the files")
    ap.add_argument("--no-branch", action="store_true", help="do not create/commit a branch")
    ap.add_argument("--no-validate", action="store_true", help="skip harness and guard")
    ap.add_argument("--root", default=ROOT, help=argparse.SUPPRESS)
    args = ap.parse_args()

    global MK_TEXT
    root = os.path.abspath(args.root)
    mk_path = os.path.join(root, "msm89xx/image/msm8916.mk")
    MK_TEXT = open(mk_path).read()

    dtb, cleanup = to_dtb(args.dtb)
    try:
        info = collect(dtb)
    finally:
        if cleanup:
            os.unlink(cleanup)

    compat = info["compat"]
    slug = pick_slug(compat, info["model"], args.name)
    generic = not any(not c.startswith("qcom,") for c in compat)
    if generic and not args.name:
        if sys.stdin.isatty():
            slug = input("DTS identifies itself as %r; board name: " %
                         (compat[0] if compat else "nothing")).strip()
        if not slug:
            die("this DTS has no vendor compatible (%s); pass --name <board>"
                % (", ".join(compat) or "none"))
    if not slug:
        die("could not derive a board name; pass --name")
    slug = slugify(slug)
    if not re.match(r"^[a-z0-9][a-z0-9-]*$", slug):
        die("'%s' is not a valid board name" % slug)
    if args.model:
        info["model"] = args.model

    warn = []
    body = build_dts(slug, info, slug, args.vendor, warn, args.dtb)
    dts_name = "msm8916-generic-" + slug
    patch_no = next_patch_number(os.path.join(root, "msm89xx/patches"))
    patch_name = "%d-arm64-dts-qcom-add-%s.patch" % (patch_no, dts_name)

    print("board     %s" % slug)
    print("model     %s" % (info["model"] or "-"))
    print("compatible %s" % ", ".join(compat))
    print("LEDs      %d" % len(info["leds"]))
    for led in info["leds"]:
        print("  gpio %-3d %-20s %s" % (led["pin"], led["label"],
                                         "active-low" if led["low"] else "active-high"))
    print("keys      %d" % len(info["keys"]))
    for key in info["keys"]:
        print("  gpio %-3d %-20s code %s" % (key["pin"], key["label"], key["code"]))
    print("battery   %s" % ("yes" if info["battery"] else "no"))
    print("display   %s" % (", ".join(info["display"]) if info["display"] else "no"))
    print("writes    diffconfigs/%s" % slug)
    print("          msm89xx/image/msm8916.mk (Device/generic-%s)" % slug)
    print("          msm89xx/patches/%s" % patch_name)
    print("          msm89xx/patches/801-arm64-dts-qcom-add-devices-makefile.patch")
    print("          .github/workflows/build.yml")
    for w in warn:
        print("warning   %s" % w)

    if args.inspect:
        print("\n--- %s.dts ---\n%s" % (dts_name, body))
        return
    if not args.write:
        print("\nnothing written (pass --write)")
        return

    dirty = subprocess.run(["git", "status", "--porcelain", "-uno"], cwd=root,
                           capture_output=True, text=True).stdout.strip()
    if dirty:
        die("tracked files are modified; commit or stash them first:\n%s" % dirty)

    diffconfig = os.path.join(root, "diffconfigs", slug)
    if os.path.exists(diffconfig):
        die("diffconfigs/%s already exists" % slug)
    if re.search(r"^define Device/generic-%s$" % re.escape(slug), MK_TEXT, re.M):
        die("Device/generic-%s already exists" % slug)

    ref = args.reference
    ref_dev = "generic-" + ref if ("define Device/generic-%s\n" % ref) in MK_TEXT else ref
    ref_conf = open(os.path.join(root, "diffconfigs", ref)).read()
    dev_sym = "CONFIG_TARGET_msm89xx_msm8916_DEVICE_%s" % ref_dev
    if dev_sym + "=y" not in ref_conf:
        die("diffconfigs/%s does not enable %s" % (ref, dev_sym))
    version = open(os.path.join(root, "version")).read().strip()
    conf = ref_conf.replace(dev_sym + "=y",
                            "CONFIG_TARGET_msm89xx_msm8916_DEVICE_generic-%s=y" % slug)
    conf = re.sub(r'^CONFIG_VERSION_NUMBER="[^"]*"',
                  'CONFIG_VERSION_NUMBER="%s"' % version, conf, flags=re.M)

    patch = make_patch(dts_name, body)
    p801 = os.path.join(root, "msm89xx/patches/801-arm64-dts-qcom-add-devices-makefile.patch")
    p801_new = add_to_801(open(p801).read(), dts_name)
    byml = os.path.join(root, ".github/workflows/build.yml")
    byml_new = add_to_build_yml(open(byml).read(), slug)

    mk_new = MK_TEXT
    block = device_block(slug, info, args.vendor, ref_dev)
    idx = mk_new.rstrip().rfind("\nendif")
    if idx < 0:
        die("msm8916.mk has no trailing endif")
    mk_new = mk_new[:idx].rstrip("\n") + "\n\n" + block.rstrip("\n") + "\n\n" + mk_new[idx + 1:]

    os.makedirs(os.path.join(root, "msm89xx/patches"), exist_ok=True)
    open(diffconfig, "w").write(conf)
    open(os.path.join(root, "msm89xx/patches", patch_name), "w").write(patch)
    open(p801, "w").write(p801_new)
    open(mk_path, "w").write(mk_new)
    open(byml, "w").write(byml_new)

    if args.maintainer:
        maint = os.path.join(root, "MAINTAINERS.md")
        if os.path.exists(maint):
            with open(maint, "a") as fh:
                fh.write("| `%s` | %s |\n" % (slug, args.maintainer))

    print("\nwrote 5 files (and MAINTAINERS.md)" if args.maintainer else "\nwrote 5 files")

    if not args.no_validate:
        ok = True
        for cmd in ([os.path.join(root, "tests/board-profile/run.sh")],
                    [os.path.join(root, "build.sh"), "guard"]):
            if not os.path.exists(cmd[0]):
                continue
            p = subprocess.run(cmd, cwd=root)
            if p.returncode != 0:
                ok = False
                print("validation failed: %s" % " ".join(cmd))
        if not ok:
            die("fix the failures above, then commit by hand")

    if args.no_branch:
        return
    subprocess.run(["git", "switch", "-c", "test/" + slug, "origin/staging-main"],
                   cwd=root, check=True)
    subprocess.run(["git", "add", "diffconfigs/" + slug, "msm89xx/image/msm8916.mk",
                    "msm89xx/patches", ".github/workflows/build.yml"], cwd=root, check=True)
    subprocess.run(["git", "commit", "-m", "board: add %s support" % slug],
                   cwd=root, check=True)
    print("\ncommitted on test/%s" % slug)
    print("next: tests/board-profile/signoff.sh, then push and open a PR")


if __name__ == "__main__":
    main()
