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
import analyze, build, cases, compare, net as netmod, rawlog  # noqa: E402

TARGET = {'false_falls': 0.03, 'edges_p95_s': 2.0, 'lift_precision': 0.95, 'lift_recall': 0.95, 'station': 0.90, 'live_p95_s': 30.0, 'wrong_names': 0.02}


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
    # a car on a road: every drive found as one "drive" segment, with its edges; no drive where there was none, and no
    # driving inside a descent (it would count in the skiing figures)
    drv = [s for s in fin['segments'] if s['type'] == 'drive']
    tr, i = [], 0
    while i < len(d.rows):
        if d.rows[i]['act'] == 'drive':
            j = i
            while j + 1 < len(d.rows) and d.rows[j + 1]['act'] == 'drive':
                j += 1
            tr.append((d.rows[i]['t'], d.rows[j]['t'])); i = j + 1
        else:
            i += 1
    m['drives'], m['drives_found'], m['drive_edges'] = len(tr), 0, []
    for t0, t1 in tr:
        s = next((s for s in drv if min(t1, s['t1']) - max(t0, s['t0']) >= 0.8 * (t1 - t0)), None)
        if s is not None:
            m['drives_found'] += 1
            m['drive_edges'] += [abs(t0 - s['t0']) / 1000.0, abs(t1 - s['t1']) / 1000.0]
    m['drives_false'] = sum(1 for s in drv if not any(min(t1, s['t1']) - max(t0, s['t0']) > 0 for t0, t1 in tr))
    car = {r['t'] for r in d.rows if r['act'] == 'drive'}
    m['drive_in_descent_s'] = sum(1 for s in fin['segments'] if s['type'] == 'descent' for t in car if s['t0'] <= t <= s['t1'])
    # the edges, to the second (Pini, 30.10.2026): getting on and off, and the start and end of each descent
    lost = getattr(d, 'lost', None)
    near_lost = lambda t: lost is not None and lost[0] - 5000 <= t <= lost[1] + 5000
    m['edges'] = []
    for r in d.rides:
        s = min(lifts, key=lambda s: abs(s['t0'] - r['t0']), default=None)
        if s is None:
            continue
        for a, b in ((r['t0'], s['t0']), (r['t1'], s['t1'])):
            if not near_lost(a) and abs(a - b) < 120000:
                m['edges'].append(abs(a - b) / 1000.0)
    ds = [s for s in fin['segments'] if s['type'] == 'descent']
    rows, i = d.rows, 0
    while i < len(rows) and ds:
        if rows[i]['act'] != 'ski':
            i += 1; continue
        j = i
        while j + 1 < len(rows) and rows[j + 1]['act'] in ('ski', 'stand', 'walk'):
            j += 1
        k = j
        while rows[k]['act'] != 'ski':
            k -= 1
        s = min(ds, key=lambda s: abs(s['t0'] - rows[i]['t']))
        for a, b in ((rows[i]['t'], s['t0']), (rows[k]['t'], s['t1'])):
            if not near_lost(a) and abs(a - b) < 300000:
                m['edges'].append(abs(a - b) / 1000.0)
        i = j + 1
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
        runs += [('full_day', fd[1], '', {'events': True}, 1), ('full_day', fd[1], '', {'events': True, 'wander': 18.0}, 2),
                 ('full_day', fd[1], '', {'baro': False, 'wander': 12.0, 'events': True}, 3)]
        runs += [('full_day', fd[1], '', {'events': True}, k) for k in (4, 5, 6, 7)]
    tot = {}
    print('%-22s %5s %5s %5s %7s %8s %8s %7s %7s' % ('case', 'rides', 'found', 'stat', 'live_s', 'named_m', 'wrong_m', 'max', 'truth'))
    for name, builder, _, opts, salt in runs:
        text, patch, d = build.make(base, P, name, builder, opts, salt)
        n = netmod.Net(base.raw_rl, base.raw_tj, P, patch) if patch else base
        an = analyze.Analyzer(n, P)
        hd, pts, evs, bad = rawlog.read(text)
        fin, liv = an.final(hd, pts, evs), an.live(hd, pts, evs)
        fd_ = analyze.falls_doppler(pts, [q['t'] for q in pts], an._state, P)
        if opts.get('dismiss'):
            d.falls = []  # the skier said "I did not fall": there is nothing to say
        for key, got in (('imp', fin['falls']), ('dop', fd_)):
            hit = set()
            for f in got:
                k = next((j for j, t in enumerate(d.falls) if abs(t - f['t']) <= 3000 and j not in hit), None)
                if k is None:
                    tot[key + '_false'] = tot.get(key + '_false', 0) + 1
                else:
                    hit.add(k)
            tot[key + '_found'] = tot.get(key + '_found', 0) + len(hit)
            tot[key + '_said'] = tot.get(key + '_said', 0) + len(got)
        lv = liv['falls']
        tot['live_fall_ok'] = tot.get('live_fall_ok', 0) + sum(1 for f in lv if any(abs(t - f['t']) <= 3000 for t in d.falls))
        tot['live_fall_said'] = tot.get('live_fall_said', 0) + len(lv)
        tot['falls'] = tot.get('falls', 0) + len(d.falls)
        tot['impacts'] = tot.get('impacts', 0) + sum(1 for e in evs if e.get('ev') == 'impact')
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
            if k in ('delays', 'edges', 'drive_edges'):
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
        stop_want = {'run_from_middle': 'station', 'run_short_stop': 'lift', 'run_still': 'still'}.get(name) if not salt else None
        if stop_want:
            st = liv.get('autostop')
            truth_t = {'station': next((r['t'] for r in d.rows[::-1] if r['act'] == 'ski'), 0) + 5000,
                       'lift': d.rides[0]['t0'] if d.rides else 0,
                       'still': 0}[stop_want]
            if not st or st['reason'] != stop_want:
                fails.append('%s: stopped by itself %s, expected %s' % (name, st, stop_want))
            # the recording stops when it is known (a lift: the live delay, up to 30 s); the run's own edges come from
            # the final analysis of what was recorded, to the second
            elif stop_want != 'still' and not (0 <= st['t'] - truth_t <= (30000 if stop_want == 'lift' else 3000)):
                fails.append('%s: stopped at %+.0f s from the moment expected' % (name, (st['t'] - truth_t) / 1000))
            else:
                print('  %s: stopped by itself (%s), %+.0f s' % (name, st['reason'], (st['t'] - truth_t) / 1000 if truth_t else 0))
        nop = sum(len(s.get('offpiste', [])) for s in fin['segments'] if s['type'] == 'descent')
        want = {'offpiste': 1, 'offpiste_short': 0, 'run_single': 0}.get(name) if not salt else None
        if want is not None and nop != want:
            fails.append('%s: %d off-piste events, expected %d' % (name, nop, want))
        tot['offpiste_events'] = tot.get('offpiste_events', 0) + nop
        if name == 'gps_spike' and mx is not None and mx > tm + 3:
            fails.append('gps_spike: the 100 km/h reading counted (max %s km/h, truth %.1f)' % (mx, tm))
        if mx is not None and abs(mx - tm) > 6:
            fails.append('%s: maximum speed %s km/h, truth %.1f' % (label, mx, tm))
    eg = sorted(tot['edges'])
    e95 = eg[min(len(eg) - 1, int(math.ceil(0.95 * len(eg))) - 1)] if eg else 0.0
    dl = sorted(tot['delays'])
    p95 = dl[min(len(dl) - 1, int(math.ceil(0.95 * len(dl))) - 1)] if dl else 0.0
    R = {
        'edges_p95_s': e95,
        'lift_precision': tot['pred_ok'] / tot['pred'] if tot['pred'] else 1.0,
        'lift_recall': tot['found'] / tot['rides'] if tot['rides'] else 1.0,
        'station': tot['station_ok'] / tot['found'] if tot['found'] else 1.0,
        'live_p95_s': p95,
        'wrong_names': tot['wrong_m'] / tot['named_m'] if tot['named_m'] else 0.0,
        'false_falls': tot.get('imp_false', 0) / tot['imp_said'] if tot.get('imp_said') else 0.0,
    }
    print()
    print('rides %d, found %d, predicted %d' % (tot['rides'], tot['found'], tot['pred']))
    print('edges (on and off a lift, start and end of a descent): 95th percentile %.0f s, max %.0f s, of %d (<= 2)' % (e95, eg[-1] if eg else 0, len(eg)))
    print('lifts: precision %.1f %% (>= 95), recall %.1f %% (>= 95)' % (100 * R['lift_precision'], 100 * R['lift_recall']))
    print('station to get off right: %.1f %% (>= 90)' % (100 * R['station']))
    print('live boarding detected after: 95th percentile %.0f s, max %.0f s (<= 30)' % (p95, dl[-1] if dl else 0))
    print('wrong run names: %.2f %% of %.0f m named (<= 2)' % (100 * R['wrong_names'], tot['named_m']))
    print('named right: %.1f %% of %.0f m on named runs; off-piste said off or unmatched: %.1f %% of %.0f m' % (
        100 * tot['right_m'] / max(1, tot['truth_named_m']), tot['truth_named_m'],
        100 * tot['off_ok_m'] / max(1, tot['off_m']), tot['off_m']))
    print('off-piste events: %d (the off-piste case and the full days; none in the short trip to the side)' % tot.get('offpiste_events', 0))
    de = sorted(tot.get('drive_edges', [])) or [0.0]
    print('by car: %d drives, found %d, edges max %.0f s; drives where there was none: %d; seconds of driving inside a descent: %d' % (
        tot.get('drives', 0), tot.get('drives_found', 0), de[-1], tot.get('drives_false', 0), tot.get('drive_in_descent_s', 0)))
    if tot.get('drives_found', 0) < tot.get('drives', 0) or tot.get('drives_false', 0) or tot.get('drive_in_descent_s', 0) or de[-1] > 2:
        fails.append('driving: found %d of %d, %d false, %d s inside descents, edges max %.0f s' % (
            tot.get('drives_found', 0), tot.get('drives', 0), tot.get('drives_false', 0), tot.get('drive_in_descent_s', 0), de[-1]))
    print('"here you fell": %d real falls, %d impact windows in the logs' % (tot.get('falls', 0), tot.get('impacts', 0)))
    for key, label in (('imp', 'blow + rotation + stop (in the result)'), ('dop', 'Doppler speed only (alternative)')):
        said, fal, fnd = tot.get(key + '_said', 0), tot.get(key + '_false', 0), tot.get(key + '_found', 0)
        print('  %s: found %d of %d, said %d, wrong %d (%.1f %%)' % (label, fnd, tot.get('falls', 0), said, fal, 100.0 * fal / said if said else 0))
    print('  live: said %d, right %d' % (tot.get('live_fall_said', 0), tot.get('live_fall_ok', 0)))
    for k, v in TARGET.items():
        bad = R[k] > v if k in ('live_p95_s', 'wrong_names', 'edges_p95_s', 'false_falls') else R[k] < v
        if bad:
            fails.append('%s = %.3f, target %s' % (k, R[k], v))
    if fails:
        print('\nFAILED:\n  ' + '\n  '.join(fails))
        return 1
    print('\nall targets met')
    return 0


if __name__ == '__main__':
    sys.exit(main('--quick' in sys.argv))
