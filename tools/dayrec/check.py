"""Checks the day analysis against the truth of the synthetic days, and prints the report
(docs/ARCHITECTURE.md m-8, conditions 4, 6, 7):
  lifts found and correct (precision and recall) >= 95 %, live detection of boarding <= 30 s (95th percentile),
  the right station to get off >= 90 %, wrong run names <= 2 % of the named distance.
Also checks that the test values still match what the code gives (a regression).

    python3 tools/dayrec/check.py           the test values and three more full days (one without a barometer)
    python3 tools/dayrec/check.py --quick   the test values only
"""
import json, math, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import build, cases, compare  # noqa: E402

TARGET = {'lift_precision': 0.95, 'lift_recall': 0.95, 'station': 0.90, 'live_p95_s': 30.0, 'wrong_names': 0.02}


def metrics(d, fin, liv):
    m = {'rides': 0, 'found': 0, 'pred': 0, 'pred_ok': 0, 'station_ok': 0, 'delays': [],
         'named_m': 0.0, 'wrong_m': 0.0, 'truth_named_m': 0.0, 'right_m': 0.0, 'off_m': 0.0, 'off_ok_m': 0.0}
    lifts = [s for s in fin['segments'] if s['type'] == 'lift']
    m['rides'] = len(d.rides); m['pred'] = len(lifts)
    used = set()
    for r in d.rides:
        dur = r['t1'] - r['t0']
        hit = None
        for n, s in enumerate(lifts):
            ov = min(r['t1'], s['t1']) - max(r['t0'], s['t0'])
            if s['lift'] == r['lift'] and ov >= 0.5 * dur and n not in used:
                hit = n; break
        if hit is not None:
            used.add(hit); m['found'] += 1
            if lifts[hit]['to'].get('station') == r['to']:
                m['station_ok'] += 1
        dl = [e['t'] for e in liv['events'] if e['act'] == 'lift' and e.get('lift') == r['lift']
              and r['t0'] - 30000 <= e['t'] <= r['t1']]
        # from the first reading of the ride (a crash can hide its start)
        first = next((t for t in d.recorded if t >= r['t0']), r['t0'])
        m['delays'].append(max(0.0, (dl[0] - first) / 1000.0) if dl else float('inf'))
    m['pred_ok'] = len(used)
    legs = [lg for s in fin['segments'] if s['type'] == 'descent' for lg in s['legs']]
    lost = getattr(d, 'lost', None)
    prev = None
    for r in d.rows:
        if prev is not None and r['act'] == 'ski' and r['runs'] is not None and not (lost and lost[0] <= r['t'] <= lost[1] + 1000):
            step = math.hypot(r['x'] - prev['x'], r['y'] - prev['y'])
            lg = next((l for l in legs if l['t0'] <= r['t'] <= l['t1']), None)
            truth = r['runs']
            pred_run = lg is not None and lg['kind'] == 'run'
            if truth == 'off':
                m['off_m'] += step
                if lg is not None and lg['kind'] in ('off', 'unmatched'):
                    m['off_ok_m'] += step
            elif truth:
                m['truth_named_m'] += step
                if pred_run and set(lg['runs']) & set(truth):
                    m['right_m'] += step
            if pred_run:
                m['named_m'] += step
                if truth == 'off' or truth == () or not (set(lg['runs']) & set(truth)):
                    m['wrong_m'] += step
        prev = r
    return m


def truth_max_kmh(d):
    """The truth for the maximum speed: the same rule (held over two readings) on the true speed, skiing only."""
    import synth
    rows = d.rows
    v = [synth.true_velocity(rows, i)[2] if rows[i]['act'] == 'ski' else None for i in range(len(rows))]
    best = 0.0
    for i in range(1, len(rows)):
        if v[i] is not None and v[i - 1] is not None:
            best = max(best, min(v[i], v[i - 1]))
    return best * 3.6


def main(quick=False):
    P = build.params()
    base = build.load_base(P)
    idx = json.load(open(os.path.join(build.FIX, 'dayrec-cases.json'), encoding='utf-8'))
    exp = {c['name']: c for c in idx['cases']}
    fails = []
    runs = [(n, b, a, o, 0) for n, b, a, o in cases.CASES]
    if not quick:
        fd = next(c for c in cases.CASES if c[0] == 'full_day')
        runs += [('full_day', fd[1], '', {}, 1), ('full_day', fd[1], '', {'wander': 18.0}, 2),
                 ('full_day', fd[1], '', {'baro': False, 'wander': 12.0}, 3)]
    tot = {}
    print('%-22s %5s %5s %5s %7s %8s %8s %7s %7s' % ('case', 'rides', 'found', 'stat', 'live_s', 'named_m', 'wrong_m', 'max', 'truth'))
    for name, builder, _, opts, salt in runs:
        text, patch, d = build.make(base, P, name, builder, opts, salt)
        fin, liv, bad = build.analyse(text, patch, P, base)
        if salt == 0:
            e = exp.get(name)
            if e is None:
                fails.append('%s: not in dayrec-cases.json (run build.py)' % name)
            else:
                errs = compare.final(e['expected']['final'], fin) + compare.live(e['expected']['live'], liv)
                if errs:
                    fails.append('%s: differs from the test values: %s' % (name, '; '.join(errs[:3])))
        m = metrics(d, fin, liv)
        for k, v in m.items():
            if k == 'delays':
                tot.setdefault(k, []).extend(v)
            else:
                tot[k] = tot.get(k, 0) + v
        tm = truth_max_kmh(d)
        mx = fin['stats'].get('max_kmh')
        label = name + ('' if not salt else ' #%d' % salt) + (' no baro' if opts.get('baro') is False else '') \
            + (' wide %d' % opts['wander'] if opts.get('wander') else '')
        print('%-22s %5d %5d %5d %7s %8.0f %8.0f %7s %7.1f' % (
            label, m['rides'], m['found'], m['station_ok'],
            ('%.0f' % max(m['delays'])) if m['delays'] else '-', m['named_m'], m['wrong_m'], mx, tm))
        if name == 'gps_spike' and mx is not None and mx > tm + 3:
            fails.append('gps_spike: the 100 km/h reading counted (max %s km/h, truth %.1f)' % (mx, tm))
        if mx is not None and abs(mx - tm) > 6:
            fails.append('%s: maximum speed %s km/h, truth %.1f' % (label, mx, tm))
    dl = sorted(tot['delays'])
    p95 = dl[min(len(dl) - 1, int(math.ceil(0.95 * len(dl))) - 1)] if dl else 0.0
    R = {
        'lift_precision': tot['pred_ok'] / tot['pred'] if tot['pred'] else 1.0,
        'lift_recall': tot['found'] / tot['rides'] if tot['rides'] else 1.0,
        'station': tot['station_ok'] / tot['found'] if tot['found'] else 1.0,
        'live_p95_s': p95,
        'wrong_names': tot['wrong_m'] / tot['named_m'] if tot['named_m'] else 0.0,
    }
    print()
    print('rides %d, found %d, predicted %d' % (tot['rides'], tot['found'], tot['pred']))
    print('lifts: precision %.1f %% (>= 95), recall %.1f %% (>= 95)' % (100 * R['lift_precision'], 100 * R['lift_recall']))
    print('station to get off right: %.1f %% (>= 90)' % (100 * R['station']))
    print('live boarding detected after: 95th percentile %.0f s, max %.0f s (<= 30)' % (p95, dl[-1] if dl else 0))
    print('wrong run names: %.2f %% of %.0f m named (<= 2)' % (100 * R['wrong_names'], tot['named_m']))
    print('named right: %.1f %% of %.0f m on named runs; off-piste said off or unmatched: %.1f %% of %.0f m' % (
        100 * tot['right_m'] / max(1, tot['truth_named_m']), tot['truth_named_m'],
        100 * tot['off_ok_m'] / max(1, tot['off_m']), tot['off_m']))
    for k, v in TARGET.items():
        bad = R[k] > v if k in ('live_p95_s', 'wrong_names') else R[k] < v
        if bad:
            fails.append('%s = %.3f, target %s' % (k, R[k], v))
    if fails:
        print('\nFAILED:\n  ' + '\n  '.join(fails))
        return 1
    print('\nall targets met')
    return 0


if __name__ == '__main__':
    sys.exit(main('--quick' in sys.argv))
