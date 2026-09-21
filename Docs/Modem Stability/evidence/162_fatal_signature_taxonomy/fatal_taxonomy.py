#!/usr/bin/env python3
"""Fatal-signature taxonomy: modem uptime at fatal, grouped by dmesg signature.

Every sample below is `fatal_dmesg_ts - last_'4080000.remoteproc is now up'_ts`,
i.e. SECONDS OF MODEM UPTIME. Sources are named so each row can be re-derived.
"""
import statistics as st

# signature -> list of (modem_uptime_s, source)
DATA = {
    "lte_ml1_common_timer.c:390": [
        (902.169, "Modem RE/hmu05/900S_CRASH_LPR_FRAMEWORK_RE.md:278"),
        (902.394, "Modem RE/hmu05/900S_CRASH_LPR_FRAMEWORK_RE.md:278"),
        (902.395, "Modem RE/hmu05/900S_CRASH_LPR_FRAMEWORK_RE.md:278"),
        (902.395, "Modem RE/hmu05/900S_CRASH_LPR_FRAMEWORK_RE.md:278"),
        (902.394, "Modem RE/hmu05/900S_CRASH_LPR_FRAMEWORK_RE.md:278"),
        (902.395, "Modem RE/hmu05/900S_CRASH_LPR_FRAMEWORK_RE.md:278"),
        (902.677, "Doc 149 table fatal #1"),
        (902.267, "Doc 149 table fatal #2"),
        (902.311, "Doc 149 table fatal #3"),
        (902.281, "Doc 149 table fatal #4"),
        (902.210, "Doc 149 table fatal #5"),
    ],
    "lte_ml1_sleepmgr_stm.c:4054": [
        (900.662, "soak814 run8 fatal #3"),
        (900.699, "soak814 run6 fatal #2"),
        (900.811, "README.md:377"),
        (900.864, "Doc 149 table fatal #8"),
        (900.904, "README.md:468"),
        (900.965, "soak814 run8 fatal #1"),
        (901.120, "Doc 122 table milestone 1"),
        (901.324, "soak814 run6 fatal #8"),
        (901.965, "soak814 run6 fatal #3"),
        (902.981, "soak814 run6 fatal #7"),
    ],
    "a2_power.c:1189": [
        (68.524, "soak814 run6 fatal #5/6"),
        (177.971, "soak814 run6 fatal #4"),
        (895.490, "Doc 149 table fatal #6 (E1 phase B)"),
        (909.112, "soak814 run6 fatal #1"),
        (932.855, "Doc 149 table fatal #7 (E1 phase B)"),
        (940.238, "soak814 run8 fatal #4"),
        (941.288, "soak814 run8 fatal #2"),
        (947.161, "soak814 run8 fatal #5 (breaks the apparent 2-cycle)"),
    ],
}

print(f"{'signature':<30} {'n':>2} {'min':>9} {'max':>9} {'mean':>10} {'spread':>8} {'+/-ppm':>8}")
print("-" * 82)
for sig, rows in DATA.items():
    v = sorted(x for x, _ in rows)
    mean = st.mean(v)
    spread = v[-1] - v[0]
    ppm = (spread / 2) / mean * 1e6
    print(f"{sig:<30} {len(v):>2} {v[0]:>9.3f} {v[-1]:>9.3f} {mean:>10.3f} {spread:>8.3f} {ppm:>8.0f}")

print()
print("run 8 (soak814) — raw dmesg anchors (S,A,S,A,A: NOT a 2-cycle):")
up = [11.950, 914.305, 1856.956, 2759.129, 3700.741]
fat = [912.915, 1855.593, 2757.618, 3699.367, 4647.902]
sig = ["sleepmgr", "a2_power", "sleepmgr", "a2_power", "a2_power"]
for i, (u, f, s) in enumerate(zip(up, fat, sig), 1):
    print(f"  #{i}  modem_up {f:>9.3f} - {u:>9.3f} = {f-u:>9.3f} s   {s}")
