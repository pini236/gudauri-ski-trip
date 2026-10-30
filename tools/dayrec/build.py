"""Writes the test values of the day analysis:
    tools/fixtures/dayrec-logs/<case>.jsonl   the raw logs, as an app writes them
    tools/fixtures/dayrec-cases.json          the index, with the expected final and live results

    python3 tools/dayrec/build.py          write them
    python3 tools/dayrec/build.py --check  fail if the files differ from what the code makes now (CI)
"""
import json, os, random, sys, zlib

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, '..', '..'))
sys.path.insert(0, HERE)
import analyze, cases, net as netmod, rawlog, synth  # noqa: E402

FIX = os.path.join(ROOT, 'tools', 'fixtures')
LOGS = os.path.join(FIX, 'dayrec-logs')


def params():
    return json.load(open(os.path.join(FIX, 'dayrec-params.json'), encoding='utf-8'))


def seed_of(name, salt=0):
    return zlib.crc32(('%s/%d' % (name, salt)).encode()) & 0x7fffffff


def make(base, P, name, builder, opts, salt=0):
    """(log text, network patch or None, the truth Day) for a case. salt != 0 makes a stress variant."""
    seed = seed_of(name, salt)
    d = synth.Day(base, seed, skier={'vmax': [15.0, 17.0, 19.0, 21.0][seed % 4], 'wander': opts.get('wander', 7.0)})
    d.random_events = opts.get('events', False)
    builder(d)
    synth.truth_labels(base, d.rows)
    pts = synth.sense(base, d, seed + 1, baro=opts.get('baro', True))
    ski = [r['t'] for r in d.rows if r['act'] == 'ski']
    events = [{'ev': 'start', 't': d.rows[0]['t']}]
    cut = None
    if opts.get('spike') or 'spike' in d.marks:
        t = d.marks.get('spike') or ski[int(len(ski) * opts['spike'])]
        synth.spike(base, pts, t)
    loss = None
    if opts.get('loss'):
        f, secs = opts['loss']
        t = ski[int(len(ski) * f)]
        loss = (t, t + (secs - 1) * 1000)
    elif 'loss' in d.marks:
        loss = (d.marks['loss'], d.marks['loss'] + 59000)
    if loss:
        pts = synth.drop_range(pts, *loss)
        d.lost = loss
    if opts.get('crash'):
        cut = pts[int(len(pts) * opts['crash'])]['t']
        pts = synth.drop_range(pts, cut + 1000, cut + 90000)
        events.append({'ev': 'resume', 't': cut + 91000})
        d.lost = (cut, cut + 90000)
    # the motion windows the phone writes around a blow over trigger_g (condition 10); "I did not fall" leaves a line
    rng = random.Random(seed + 2)
    lost = getattr(d, 'lost', None)
    for imp in d.impacts:
        if imp['peak_g'] < P['fall']['trigger_g'] or (lost and lost[0] <= imp['t'] <= lost[1] + 1000):
            continue
        if opts.get('dismiss') and imp['kind'] == 'fall':
            events.append({'ev': 'impact', 't': int(imp['t']), 'dismissed': True})
        else:
            events.append(synth.impact_window(imp, rng, P))
    events.append({'ev': 'stop', 't': pts[-1]['t'], 'reason': 'user'})
    d.recorded = sorted(q['t'] for q in pts)
    header = {'v': 1, 'tz': 'Asia/Tbilisi', 'resort': 'gudauri', 'device': 'synthetic', 'start': d.rows[0]['t']}
    text = synth.write_log(pts, header, events, cut_at=cut)
    patch = cases.mid_patch(base) if opts.get('patch') else None
    if patch:  # the truth of the stations, on the patched network
        pn = netmod.Net(base.raw_rl, base.raw_tj, P, patch)
        for r in d.rides:
            l = next(x for x in pn.lifts if x.id == r['lift'])
            u = l.line.nearest(*next((q['x'], q['y']) for q in d.rows if q['t'] == r['t1']))[1]
            st = min(l.stations, key=lambda q: abs(q['u'] - u))
            r['to'] = st['id'] if abs(st['u'] - u) <= 60 else None
    return text, patch, d


def analyse(text, patch, P, base):
    n = netmod.Net(base.raw_rl, base.raw_tj, P, patch) if patch else base
    a = analyze.Analyzer(n, P)
    h, pts, evs, bad = rawlog.read(text)
    return a.final(h, pts, evs), a.live(h, pts, evs), bad


def truth_summary(d):
    """What the synthetic skier did, for people: the rides, and per descent the runs held for 10 s or more."""
    out = {'rides': [{'lift': r['lift'], 'from': r['from'], 'to': r['to']} for r in d.rides], 'descents': []}
    cur, run = None, []
    def flush():
        if cur is not None and run and run[1] >= 10 and run[0] and (not cur or cur[-1] != run[0]):
            cur.append(run[0])
    for r in d.rows:
        if r['act'] == 'ski':
            lab = r['runs']
            lab = 'off' if lab == 'off' else ('unnamed' if lab == () else (' · '.join(lab) if lab else None))
            if cur is None:
                cur = []; out['descents'].append(cur)
            if run and run[0] == lab:
                run[1] += 1
            else:
                flush(); run = [lab, 1]
        elif r['act'] == 'lift':
            flush(); cur, run = None, []
    flush()
    return out


def load_base(P):
    rl = json.load(open(os.path.join(ROOT, 'site/data/runs-and-lifts.json'), encoding='utf-8'))
    tj = json.load(open(os.path.join(ROOT, 'site/data/terrain.json'), encoding='utf-8'))
    b = netmod.Net(rl, tj, P)
    b.raw_rl, b.raw_tj = rl, tj
    return b


def main(check=False):
    P = params()
    base = load_base(P)
    index = {
        'about': 'Test values of the day analysis (decision 76, docs/ARCHITECTURE.md m-8, condition 4). Each case: a raw log '
                 '(tools/fixtures/dayrec-logs/<case>.jsonl), an optional network_patch to merge into lifts of '
                 'site/data/runs-and-lifts.json (by lift id) before the analysis, and the expected final and live results of '
                 'tools/dayrec (the reference). An app passes a case when tools/dayrec/compare.py rules hold: '
                 'see tools/dayrec/README.md, "Running the test values in an app". The truth is what the synthetic skier did; '
                 'it is for people, the comparison is with expected.',
        'analysis': P['analysis'],
        'params': 'tools/fixtures/dayrec-params.json',
        'data': 'site/data/runs-and-lifts.json, site/data/terrain.json',
        'cases': [],
    }
    files = {}
    for name, builder, about, opts in cases.CASES:
        text, patch, d = make(base, P, name, builder, opts)
        fin, liv, bad = analyse(text, patch, P, base)
        files[os.path.join(LOGS, name + '.jsonl')] = text
        c = {'name': name, 'about': about, 'log': 'tools/fixtures/dayrec-logs/%s.jsonl' % name}
        if patch:
            c['network_patch'] = patch
        c['dropped_lines'] = bad
        c['truth'] = truth_summary(d)
        c['expected'] = {'final': fin, 'live': liv}
        index['cases'].append(c)
    files[os.path.join(FIX, 'dayrec-cases.json')] = json.dumps(index, ensure_ascii=False, indent=1) + '\n'
    if check:
        diff = [p for p, t in files.items() if not os.path.exists(p) or open(p, encoding='utf-8').read() != t]
        if diff:
            print('dayrec: these files are not what tools/dayrec/build.py makes now:\n  ' + '\n  '.join(os.path.relpath(p, ROOT) for p in diff))
            print('run: python3 tools/dayrec/build.py')
            return 1
        print('dayrec: test values up to date (%d cases)' % len(index['cases']))
        return 0
    os.makedirs(LOGS, exist_ok=True)
    for p, t in files.items():
        with open(p, 'w', encoding='utf-8', newline='\n') as f:
            f.write(t)
    print('dayrec: wrote %d cases' % len(index['cases']))
    return 0


if __name__ == '__main__':
    sys.exit(main('--check' in sys.argv))
