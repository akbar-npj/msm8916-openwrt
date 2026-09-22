#!/usr/bin/env python3
"""
177_period_anchor/period_anchor.py

Decides which quantity is INVARIANT across fatal-to-fatal intervals:
  R  = fatal -> next "...is now up"   (SSR recovery time)
  T  = "...is now up" -> next fatal   (the so-called "modem-uptime period")
  AP = fatal -> fatal                 (the AP-observed interval)

AP = R + T identically.  So:
  * if T is the invariant (timer armed at modem boot), sd(AP) == sd(R)
  * if AP is the invariant (wall/always-on clock),  sd(T) == sd(R) and corr(R,T) = -1

Input is an explicit list of triples (fatal_i, boot_{i+1}, fatal_{i+1}) so a pair that
straddles an a2_power event can simply be omitted -- a2_power is an event, not the clock
(Doc 162 taxonomy), so only common_timer->common_timer pairs are used.

Run:  python3 period_anchor.py
"""
import statistics as st


def analyse(name, pairs):
    """pairs = [(fatal_i, boot_next, fatal_next), ...]"""
    R, T, AP = [], [], []
    print(f"\n=== {name} ===")
    print(f"{'fatal_i':>12} {'R=boot-fatal_i':>15} {'T=fatal_next-boot':>18} {'AP=R+T':>12}")
    for f0, boot1, f1 in pairs:
        r, t, ap = boot1 - f0, f1 - boot1, f1 - f0
        R.append(r); T.append(t); AP.append(ap)
        print(f"{f0:>12.6f} {r:>15.6f} {t:>18.6f} {ap:>12.6f}")

    def stat(n, v):
        m, sd = st.mean(v), st.stdev(v)
        print(f"  {n:>10}: mean={m:.6f}  sd={sd:.6f}  spread={max(v)-min(v):.6f}  ppm={sd/m*1e6:.2f}")
    print(f"  n={len(R)}")
    stat("R recov", R); stat("T modemup", T); stat("AP int", AP)
    r = st.correlation(R, T)
    print(f"  corr(R,T) = {r:+.5f}")
    print(f"  T-invariant would predict sd(AP)=sd(R)={st.stdev(R):.6f}"
          f"  -> observed sd(AP)={st.stdev(AP):.6f}  (ratio {st.stdev(AP)/st.stdev(R):.3f})")
    print(f"  AP-invariant would predict sd(T)=sd(R)={st.stdev(R):.6f}"
          f"  -> observed sd(T)={st.stdev(T):.6f}  (ratio {st.stdev(T)/st.stdev(R):.3f})")
    return R, T, AP


# --- CORPUS: Doc 149 section 2, 2026-09-20 boot, fatals 2..5 (all common_timer) ---
CORPUS = [(1818.443149, 1819.807165, 2722.117987),
          (2722.117987, 2723.511313, 3625.792806),
          (3625.792806, 3627.257122, 4529.467002)]

# --- LIVE: 2026-09-22 boot, consecutive common_timer->common_timer pairs only ---
#   f2->f3, f3->f4, f4->f5, f5->f6  and  f8->f9   (f6->f7 and f7->f8 straddle a2_power)
LIVE = [(1427.099153, 1427.997630, 2330.773740),
        (2330.773740, 2331.674506, 3234.448768),
        (3234.448768, 3235.350784, 4138.119715),
        (4138.119715, 4139.016959, 5041.798167),
        (6852.985676, 6853.886544, 7756.662692)]

Rc, Tc, APc = analyse("CORPUS boot 2026-09-20 (Doc 149 s2, n=3)", CORPUS)
Rl, Tl, APl = analyse("LIVE boot 2026-09-22 (n=5)", LIVE)

print("\n=== CROSS-BOOT (same modem firmware, 2 days apart, kernel changed) ===")
print(f"  AP interval  : corpus {st.mean(APc):.6f} s  live {st.mean(APl):.6f} s"
      f"  -> {abs(st.mean(APl)-st.mean(APc))*1e6/st.mean(APc):.2f} ppm")
print(f"  T (modem up) : corpus {st.mean(Tc):.6f} s  live {st.mean(Tl):.6f} s"
      f"  -> {abs(st.mean(Tl)-st.mean(Tc))*1e6/st.mean(Tc):.0f} ppm")
print(f"  R (recovery) : corpus {st.mean(Rc):.6f} s  live {st.mean(Rl):.6f} s"
      f"  -> delta {st.mean(Rc)-st.mean(Rl):+.6f} s")
print("\n  The AP interval is a ~1 ppm physical constant across the two boots.")
print("  The 'modem-uptime period' moves by 563 ppm -- it is exactly 903.675 - R.")
print("  => Doc 149 s2 ('the period is MODEM uptime, not AP uptime') is INVERTED.")
