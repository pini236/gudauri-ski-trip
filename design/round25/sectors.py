#!/usr/bin/env python3
"""Round 25: which valley each run and lift of Sella Ronda belongs to, from real sources only.

1. The valley's written list of runs (research/resorts/sellaronda-official.json: Val Gardena, Alta Badia, Val di Fassa).
2. Otherwise the ski area OpenSkiMap gives the run or lift (Arabba, Alta Badia, Belvedere - Col Rodella - Passo Pordoi).
3. Otherwise (unnamed pieces, and a few named runs on no list) the valley of the nearest run that has one, marked "near".
Grouping only: no line is drawn or moved. For the canvas of round 25; not site data yet.
  python3 design/round25/sectors.py <cache folder of tools/build-resort.py>"""
import json, math, sys, collections, pathlib, unicodedata
root = pathlib.Path(__file__).resolve().parent.parent.parent
cache = pathlib.Path(sys.argv[1])
R = json.load(open(root / 'site/data/resorts/sellaronda/runs-and-lifts.json'))
OFF = json.load(open(root / 'research/resorts/sellaronda-official.json'))['runs']
N = lambda s: unicodedata.normalize('NFC', s or '').casefold().strip()
VALLEY = {'Val Gardena': 'gardena', 'Alta Badia': 'badia', 'Arabba': 'arabba', 'Belvedere': 'fassa', 'Val di Fassa': 'fassa'}
def valley(area):
    for k, v in VALLEY.items():
        if area.startswith(k): return v
off = {}
for r in OFF: off.setdefault(N(r['name']), valley(r['area']))
def osk(kind):
    out = collections.defaultdict(collections.Counter)
    for l in open(cache / f'osk_{kind}.jsonl'):
        f = json.loads(l)
        vs = [valley((a.get('properties', a)).get('name', '')) for a in f['properties'].get('skiAreas') or []]
        vs = [v for v in vs if v]
        for s in f['properties'].get('sources', []):
            for v in vs: out[s['id'].split('/')[-1]][v] += 1
    return out
ORUN, OLIFT = osk('runs'), osk('lifts')
def mid(g): return g[len(g) // 2]
def hav(a, b): return math.hypot((b[1] - a[1]) * 111320 * math.cos(math.radians(a[0])), (b[0] - a[0]) * 111320)
runs, why = {}, {}
for p in R['pistes']:
    v = None
    for n in [p['name']] + p.get('osmNames', []):
        if N(n) in off: v, w = off[N(n)], 'list'; break
    if not v:
        c = collections.Counter()
        for s in p['segs']: c.update(ORUN.get(str(s['id']), {}))
        if c: v, w = c.most_common(1)[0][0], 'osm'
    if v: runs[p['key']], why[p['key']] = v, w
pts = [(mid(s['g']), runs[p['key']]) for p in R['pistes'] if p['key'] in runs for s in p['segs']]
for p in R['pistes']:
    if p['key'] in runs: continue
    m = mid(max(p['segs'], key=lambda s: len(s['g']))['g'])
    runs[p['key']] = min(pts, key=lambda t: hav(m, t[0]))[1]; why[p['key']] = 'near'
lifts = {}
for L in R['lifts']:
    c = collections.Counter()
    for i in L.get('osmIds', []) or [L.get('id')]: c.update(OLIFT.get(str(i), {}))
    if c: lifts[L['id']] = c.most_common(1)[0][0]
    else:
        g = L.get('g') or []
        if g: lifts[L['id']] = min(pts, key=lambda t: hav(mid(g), t[0]))[1]
NAMES = {'gardena': 'Val Gardena', 'badia': 'Alta Badia', 'arabba': 'Arabba', 'fassa': 'Val di Fassa'}
sect = {}
for v, name in NAMES.items():
    G = [q for p in R['pistes'] if runs[p['key']] == v for s in p['segs'] for q in s['g']]
    named = [p for p in R['pistes'] if runs[p['key']] == v and p.get('named')]
    col = collections.Counter(p['color'] for p in named if p.get('kind', 'run') == 'run')
    sect[v] = {'name': name, 'bbox': [min(q[0] for q in G), min(q[1] for q in G), max(q[0] for q in G), max(q[1] for q in G)],
               'center': [sum(q[0] for q in G) / len(G), sum(q[1] for q in G) / len(G)],
               'runs': len(named), 'colors': dict(col), 'lifts': sum(1 for x in lifts.values() if x == v),
               'km': round(sum(p['len'] for p in named) / 1000)}
out = {'about': __doc__.split('\n\n')[0], 'sources': ['research/resorts/sellaronda-official.json', 'OpenSkiMap skiAreas (ODbL)'],
       'sectors': sect, 'runs': runs, 'why': dict(collections.Counter(why.values())), 'lifts': lifts}
json.dump(out, open(root / 'design/round25/data/sectors-sellaronda.json', 'w'), ensure_ascii=False, indent=1)
for v, s in sect.items(): print(v, s['runs'], s['lifts'], s['km'], s['colors'])
print(out['why'], 'lifts', len(lifts), '/', len(R['lifts']))
