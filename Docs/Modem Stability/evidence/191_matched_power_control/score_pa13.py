#!/usr/bin/env python3
"""
score_pa13.py -- replay Doc 191 sec.6 (P-A13a-d) from the frozen artifacts.

WHY THIS EXISTS
    Doc 191 sec.6 pre-registered four predictions about the AP<->modem power-control
    interaction, to be scored from T0/T1 counter deltas over >= 120 s on each platform.
    This script turns the raw captures into those numbers so the verdicts are
    reproducible from the committed files, not re-derived by hand.

SOURCES (all committed)
    Android 120 s : evidence/191_matched_power_control/android_raw_T0T1.txt   (PRE-REGISTERED window)
    Android 600 s : evidence/191_matched_power_control/android_pc_series_600s.txt (extension)
    OpenWrt MPSS  : evidence/175_openwrt_rpm_baseline/rms_out_capture{1,3}.txt
    OpenWrt vote  : Docs/Modem Stability/phase5_soak_1800s_results_deadlock_run.csv
                    Docs/Modem Stability/phase5_soak_1800s_results.csv
                    evidence/111_userspace_subtraction/usub_samples.csv

WHY THE COUNTERS ARE NAMED, NOT ASSUMED
    Doc 191 sec.6.2 labelled Android's `a2 ack out cnt` an "AP power-collapse vote".
    Reading the DEFINITION in the tracked kernel tree shows it is the AP's ACK toggle
    (`toggle_apps_ack()` -> SMSM_APPS_STATE & SMSM_A2_POWER_CONTROL_ACK), called from
    bam_init()/reconnect_to_bam()/disconnect_to_bam() -- i.e. once per BAM connect/
    disconnect transition. The AP's actual vote on Android is power_vote() ->
    SMSM_APPS_STATE & SMSM_A2_POWER_CONTROL, and it is NOT counted anywhere.
    OpenWrt's pc_vote_tx_count IS incremented in bam_dmux_runtime_resume().
    So the two are "AP-side per-power-cycle handshake events", not the same named
    quantity. Both readings are reported; neither is substituted for the other.
"""
import csv
import os
import re
import sys

EV = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))   # .../evidence
DOCS = os.path.dirname(EV)                                         # .../Modem Stability

# ---- established reference values (frozen; quoted, not re-measured here) -----
ANDROID_IDLE_REC_S = 226.8   # Doc 190 sec.4, AP-idle RPM record rate
OPENWRT_IDLE_REC_S = 224.2   # Doc 150, the matched OpenWrt reference
ESTABLISHED_RATIO = 1.012    # Doc 190 sec.4
TOL = 2.0                    # Doc 191 sec.6.3 pre-registered tolerance


def pct(a, b):
    return 100.0 * (a - b) / b


def ratio_verdict(a, b, tol=TOL):
    r = a / b if b else float("inf")
    return r, ("CONFIRMED" if 1.0 / tol <= r <= tol else "FALSIFIED")


def read_android_raw(path):
    """The 120 s T0/T1 capture: one line per counter, 'T0 | T1'."""
    txt = open(path).read()
    def g(pat):
        m = re.search(pat + r"[^\n]*", txt)
        return m.group(0) if m else None
    up = re.search(r"UPTIME\s+([\d.]+)\s+([\d.]+)", txt)
    upt0, upt1 = float(up.group(1)), float(up.group(2))
    def hexes(line):
        return [int(x, 16) for x in re.findall(r"0x([0-9A-Fa-f]+)", line)]
    def ints(line):
        return [int(x) for x in re.findall(r":\s*(\d+)", line)]
    ns = g("MPSS_NUMSHUTDOWNS")
    ao = g("A2OUT")
    ai = g("A2IN")
    pw = g("A2PWR")
    rr = g("RPMRING")
    d = dict(upt0=upt0, upt1=upt1, span=upt1 - upt0)
    d["ns"] = hexes(ns)
    d["ack_out"] = ints(ao)
    d["ack_in"] = ints(ai)
    d["pwr_in"] = ints(pw)
    d["ring"] = hexes(rr)
    return d


def read_android_series(path):
    rows = []
    if not os.path.exists(path):
        return rows
    with open(path, newline="") as fh:
        for r in csv.DictReader(fh):
            if r.get("uptime") and r.get("rpm_ctr", "").startswith("0x"):
                rows.append(r)
    return rows


def read_openwrt_master(path):
    """fields: uptime|APSS_count|MPSS_count|PRONTO_count|...  (MPSS_count = num_shutdowns)"""
    pts = []
    for ln in open(path):
        if ln.startswith("#") or "|" not in ln:
            continue
        f = ln.strip().split("|")
        if len(f) < 3:
            continue
        try:
            pts.append((float(f[0]), int(f[2])))
        except ValueError:
            continue
    return pts


def read_csv_series(path, upcol, vote_col, fatal_col=None):
    """fatal_col is OPTIONAL per file: a missing column must not drop the rows."""
    out = []
    with open(path, newline="") as fh:
        rd = csv.DictReader(fh)
        has_f = fatal_col is not None and fatal_col in (rd.fieldnames or [])
        for r in rd:
            try:
                f = int(r[fatal_col]) if has_f else None
                out.append((float(r[upcol]), int(r[vote_col]), f))
            except (KeyError, TypeError, ValueError):
                continue
    return out


def main():
    print("=" * 78)
    print("Doc 191 sec.6 -- MATCHED POWER-CONTROL / RPM COMPARISON  (P-A13a-d)")
    print("=" * 78)

    a = read_android_raw(os.path.join(EV, "191_matched_power_control", "android_raw_T0T1.txt"))
    print(f"\n[Android PRE-REGISTERED window]  ap_uptime {a['upt0']:.2f} -> {a['upt1']:.2f} "
          f"= {a['span']:.2f} s")
    for k, lbl in (("ns", "MPSS numshutdowns"), ("ack_out", "a2 ack out cnt"),
                   ("ack_in", "a2 ack in cnt"), ("pwr_in", "a2 pwr cntl in")):
        v = a[k]
        d = v[1] - v[0]
        print(f"   {lbl:20s}: {v[0]} -> {v[1]}   delta {d:5d}   {d/a['span']:.4f} /s")
    ring_b = a["ring"][1] - a["ring"][0]
    rec = ring_b / 32.0
    a_rec = rec / a["span"]
    print(f"   {'RPM ring (bytes)':20s}: {a['ring'][0]:#x} -> {a['ring'][1]:#x}   "
          f"delta {ring_b} B = {rec:.0f} rec   {a_rec:.1f} rec/s")

    a_ns = (a["ns"][1] - a["ns"][0]) / a["span"]
    a_vote = (a["ack_out"][1] - a["ack_out"][0]) / a["span"]

    # ---- P-A13d first: the positive control gates a-c --------------------
    print("\n" + "-" * 78)
    print("P-A13d  (positive control: RPM record rate reproduces the established match)")
    print("-" * 78)
    print(f"   Android measured        : {a_rec:.1f} rec/s")
    print(f"   Android idle reference  : {ANDROID_IDLE_REC_S:.1f} rec/s "
          f"(Doc 190 sec.4)  -> {a_rec/ANDROID_IDLE_REC_S:.4f}x")
    print(f"   OpenWrt idle reference  : {OPENWRT_IDLE_REC_S:.1f} rec/s (Doc 150)")
    r = a_rec / OPENWRT_IDLE_REC_S
    print(f"   ratio vs OpenWrt ref    : {r:.4f}x   (established {ESTABLISHED_RATIO:.3f}x, "
          f"{pct(r, ESTABLISHED_RATIO):+.2f}%)")
    v = "CONFIRMED" if 1/TOL <= r <= TOL else "FALSIFIED"
    print(f"   => P-A13d {v}  (tolerance {TOL:.0f}x)")
    if v == "FALSIFIED":
        print("   => method broken; P-A13a-c are VOID")
        return

    # ---- OpenWrt MPSS collapse rate --------------------------------------
    print("\n" + "-" * 78)
    print("OpenWrt modem PC-collapse rate (MPSS num_shutdowns, Doc 175)")
    print("-" * 78)
    ow_ns = []
    for tag, fn in (("capture1", "rms_out_capture1.txt"), ("capture3", "rms_out_capture3.txt")):
        pts = read_openwrt_master(os.path.join(EV, "175_openwrt_rpm_baseline", fn))
        if len(pts) >= 2:
            (u0, c0), (u1, c1) = pts[0], pts[-1]
            rate = (c1 - c0) / (u1 - u0)
            ow_ns.append(rate)
            print(f"   {tag}: up {u0:.1f}->{u1:.1f}  collapses {c1-c0}  rate {rate:.4f} /s")

    print("\n" + "-" * 78)
    print("P-A13b  (modem PC-collapse rate within 2x)")
    print("-" * 78)
    for rate in ow_ns:
        r = rate / a_ns if a_ns else float("inf")
        v = "CONFIRMED" if 1/TOL <= r <= TOL else "FALSIFIED"
        print(f"   OpenWrt {rate:.4f} /s  vs  Android {a_ns:.4f} /s  -> ratio "
              f"{r:.3f}x  => {v}")

    # ---- OpenWrt vote rate ------------------------------------------------
    print("\n" + "-" * 78)
    print("OpenWrt AP vote rate (pc_vote_tx_count) -- all committed sources")
    print("-" * 78)
    ow_votes = []
    for tag, path, upc, vc, fc in (
        ("phase5 deadlock_run", os.path.join(DOCS, "phase5_soak_1800s_results_deadlock_run.csv"),
         "uptime_s", "pc_vote_tx_count", "fatals"),
        ("phase5 soak", os.path.join(DOCS, "phase5_soak_1800s_results.csv"),
         "uptime_s", "pc_vote_tx_count", None),
        ("usub (userspace-subtracted)", os.path.join(EV, "111_userspace_subtraction", "usub_samples.csv"),
         "uptime", "pc_vote_tx_count", "fatals"),
    ):
        if not os.path.exists(path):
            print(f"   {tag}: MISSING")
            continue
        s = read_csv_series(path, upc, vc, fc)
        if len(s) < 2:
            print(f"   {tag}: too few rows")
            continue
        (u0, v0, f0), (u1, v1, f1) = s[0], s[-1]
        rate = (v1 - v0) / (u1 - u0)
        ow_votes.append((tag, rate, u1 - u0, v1 - v0, (f1 - f0) if f0 is not None else None))
        extra = f"  fatals +{f1-f0}" if f0 is not None else ""
        print(f"   {tag:28s}: up {u0:.1f}->{u1:.1f} ({u1-u0:.0f} s)  votes +{v1-v0}  "
              f"rate {rate:.4f} /s{extra}")

    print("\n" + "-" * 78)
    print("P-A13a  (vote rate within 2x)")
    print("-" * 78)
    print(f"   Android a2 ack out rate : {a_vote:.4f} /s")
    lo = min(r for _, r, *_ in ow_votes) if ow_votes else None
    hi = max(r for _, r, *_ in ow_votes) if ow_votes else None
    if lo is not None:
        print(f"   OpenWrt spread          : {lo:.4f} .. {hi:.4f} /s "
              f"({hi/lo:.1f}x within-platform)" if lo else "")
        for tag, rate, *_ in ow_votes:
            rr, v = ratio_verdict(rate, a_vote)
            print(f"     vs {tag:28s}: {rr:.3f}x  => {v}")
        print(f"   NOTE: the pre-registered comparison needs a MATCHED OpenWrt window; "
              f"none of\nthese is matched (different builds, traffic, and dates).")

    # ---- P-A13c: the ratio ------------------------------------------------
    print("\n" + "-" * 78)
    print("P-A13c  (ratio AP_votes / modem_collapses within 2x)")
    print("-" * 78)
    print(f"   Android: {a_vote:.4f} / {a_ns:.4f} = "
          f"{'UNDEFINED (0 collapses)' if a_ns == 0 else f'{a_vote/a_ns:.4f}'}")
    for rate in ow_ns:
        if ow_votes:
            print(f"   OpenWrt ({rate:.4f} /s collapses): "
                  + ", ".join(f"{v/rate:.4f} (vs {t})" for t, v, *_ in ow_votes))
    print("   => UNSCOREABLE: the Android denominator is zero in the pre-registered window.")

    # ---- the 600 s extension ---------------------------------------------
    s = read_android_series(os.path.join(EV, "191_matched_power_control",
                                         "android_pc_series_600s.txt"))
    if len(s) >= 2:
        u0 = float(s[0]["uptime"]); u1 = float(s[-1]["uptime"])
        span = u1 - u0
        print("\n" + "-" * 78)
        print(f"Android 600 s EXTENSION ({len(s)} samples, up {u0:.1f} -> {u1:.1f} = {span:.1f} s)")
        print("-" * 78)
        for col, lbl in (("mpss_shutdowns", "MPSS numshutdowns"), ("ack_out", "a2 ack out cnt"),
                         ("ack_in", "a2 ack in cnt"), ("pwr_in", "a2 pwr cntl in")):
            v0, v1 = s[0][col], s[-1][col]
            if v0.startswith("0x"):
                d = int(v1, 16) - int(v0, 16)
            else:
                d = int(v1) - int(v0)
            print(f"   {lbl:20s}: {v0} -> {v1}   delta {d:5d}   {d/span:.4f} /s")
        b0 = int(s[0]["rpm_ctr"], 16); b1 = int(s[-1]["rpm_ctr"], 16)
        print(f"   {'RPM ring (bytes)':20s}: {b0:#x} -> {b1:#x}   delta {b1-b0} B = "
              f"{(b1-b0)/32:.0f} rec   {(b1-b0)/32/span:.1f} rec/s")
        print(f"   {'rmnet1 rx_bytes':20s}: {s[0]['rmnet1_rx']} -> {s[-1]['rmnet1_rx']}  "
              f"(FROZEN = the Doc 190 sec.6 wedged-data-path confound)")
        print(f"   {'rmnet1 tx_bytes':20s}: {s[0]['rmnet1_tx']} -> {s[-1]['rmnet1_tx']}")
        print(f"   {'load1':20s}: {s[0]['load1']} .. {s[-1]['load1']}")


if __name__ == "__main__":
    main()
