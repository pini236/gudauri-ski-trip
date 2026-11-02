#!/usr/bin/env python3
"""Label windows for run names (round 21, architect conditions in docs/ARCHITECTURE.md).

Adds `label: {"v":1,"a":[[s,t0,t1,cL,cR],...]}` to every named run in a runs-and-lifts.json, and `shared` (same shape, a 6th item: the other run's key)
for the stretch two runs share, and nothing else.
s = index into segs[] (a window never spans two segments: no line is drawn across a gap, accuracy rule 1);
t0, t1 = thousandths of the segment's length; cL, cR = clear distance in metres (max 255) to any other run, ski way or lift
on the left and right of the window. Geometry only: no zoom, no language. The client decides at run time.

  python3 tools/build-labels.py                       # Gudauri
  python3 tools/build-labels.py PATH --lat0 L --lon0 L # another resort
  python3 tools/build-labels.py --check                # rebuild and compare; nothing else may change
"""
import argparse, gzip, json, math, re, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
# (shortest window m, accumulated turn degrees, distance from a real crossing m): the strict tier first; a run that has no
# window in it gets the relaxed tier, whose honest clearances make the client show its name only when zoomed in
TIERS = [(60.0, 35.0, 100.0), (40.0, 60.0, 40.0)]
CAP = 255             # clearances are stored up to this many metres (the architect's text said 99: too little for a phone's first view, where a name is 100+ m tall)
SHARE = 15.0          # a sample closer than this to another run is a shared stretch
MAX_WIN = 2
STEP = 10.0           # resampling step
BUDGET = 0.05         # of the gzip size of the file

def is_plate(key): return re.fullmatch(r'\d{1,2}[a-z]?', key) is not None

def proj(lat0, lon0):
    kx = 111320 * math.cos(math.radians(lat0)); ky = 111320
    return lambda q: ((q[1] - lon0) * kx, -(q[0] - lat0) * ky)

def resample(pts, step):
    out = [pts[0]]; cum = [0.0]; acc = 0.0; total = 0.0
    for i in range(1, len(pts)):
        a, b = pts[i - 1], pts[i]; l = math.hypot(b[0] - a[0], b[1] - a[1])
        d = step - acc
        while d < l:
            out.append((a[0] + (b[0] - a[0]) * d / l, a[1] + (b[1] - a[1]) * d / l)); cum.append(total + d); d += step
        acc = (acc + l) % step if l else acc; total += l
    if math.hypot(out[-1][0] - pts[-1][0], out[-1][1] - pts[-1][1]) > step * .3:
        out.append(pts[-1]); cum.append(total)
    return out, cum, total

def heading(a, b): return math.degrees(math.atan2(b[1] - a[1], b[0] - a[0]))
def dturn(h1, h2):
    d = (h2 - h1 + 180) % 360 - 180
    return abs(d)

def seg_cross(p1, p2, p3, p4):
    d = (p2[0]-p1[0])*(p4[1]-p3[1]) - (p2[1]-p1[1])*(p4[0]-p3[0])
    if abs(d) < 1e-9: return None
    t = ((p3[0]-p1[0])*(p4[1]-p3[1]) - (p3[1]-p1[1])*(p4[0]-p3[0])) / d
    u = ((p3[0]-p1[0])*(p2[1]-p1[1]) - (p3[1]-p1[1])*(p2[0]-p1[0])) / d
    if 0 < t < 1 and 0 < u < 1: return (p1[0] + t*(p2[0]-p1[0]), p1[1] + t*(p2[1]-p1[1]))
    return None

def build(data, P):
    # every other line: (owner key, sampled points); lifts own their id
    lines = []
    for p in data['pistes']:
        for s in p['segs']:
            if s.get('area'): continue
            pts = [P(q) for q in s['g']]
            lines.append((p['key'], pts, resample(pts, 15.0)[0]))
    for l in data['lifts']:
        pts = [P(q) for q in l['g']]
        lines.append(('lift:%s' % l['id'], pts, resample(pts, 15.0)[0]))
    cell = {}
    for k, _, smp in lines:
        for x, y in smp: cell.setdefault((int(x // 100), int(y // 100)), []).append((x, y, k))
    def near(x, y, own, R):
        for cx in range(int((x - R) // 100), int((x + R) // 100) + 1):
            for cy in range(int((y - R) // 100), int((y + R) // 100) + 1):
                for q in cell.get((cx, cy), ()):
                    if q[2] != own: yield q
    def windows(p, ex, partner):
        """Windows on run p. ex: keys that do not count as other lines (p itself, and the partner on a shared stretch).
        partner None: ordinary windows, no sample within SHARE of any other line. partner key: windows lying wholly on the
        stretch shared with that run, clear of everything else."""
        cross = []
        for s in p['segs']:
            if s.get('area'): continue
            a = [P(q) for q in s['g']]
            for k, pts, _ in lines:
                if k in ex: continue
                for i in range(1, len(a)):
                    for j in range(1, len(pts)):
                        c = seg_cross(a[i-1], a[i], pts[j-1], pts[j])
                        if c: cross.append(c)
        cands = []
        for MIN_LEN, MAX_TURN, CROSS_CLEAR in TIERS:
          if cands: break
          for si, s in enumerate(p['segs']):
            if s.get('area'): continue
            pts, cum, total = resample([P(q) for q in s['g']], STEP)
            if total < MIN_LEN or len(pts) < 3: continue
            n = len(pts)
            onp = [True] * n
            if partner is not None:
                onp = [any(qk == partner and math.hypot(qx - x, qy - y) < SHARE for qx, qy, qk in near(x, y, p['key'], SHARE + 1)) for x, y in pts]
            for i in range(n - 1):
                if not onp[i] or not onp[i+1]: continue
                turn = 0.0; h = heading(pts[i], pts[i+1]); j = i + 1
                best = None
                while True:
                    if cum[j] - cum[i] >= MIN_LEN: best = j
                    if j + 1 >= n or not onp[j+1]: break
                    h2 = heading(pts[j], pts[j+1]); t2 = turn + dturn(h, h2)
                    if t2 > MAX_TURN: break
                    turn = t2; h = h2; j += 1
                if best is None: continue
                w = pts[i:best+1]
                if any(math.hypot(x - c[0], y - c[1]) < CROSS_CLEAR for x, y in w for c in cross): continue
                cl, cr, shared, onpartner = float(CAP), float(CAP), False, True
                for k in range(len(w)):
                    a, b = w[max(k-1, 0)], w[min(k+1, len(w)-1)]
                    tx, ty = b[0]-a[0], b[1]-a[1]
                    dpart = CAP
                    for qx, qy, qk in near(w[k][0], w[k][1], p['key'], CAP):
                        dx, dy = qx - w[k][0], qy - w[k][1]; d = math.hypot(dx, dy)
                        if qk == partner: dpart = min(dpart, d); continue
                        if qk in ex or d >= CAP: continue
                        if d < SHARE: shared = True; break
                        side = tx * dy - ty * dx   # >0: right on screen (y down)
                        if side > 0: cr = min(cr, d)
                        else: cl = min(cl, d)
                    if shared: break
                    if partner is not None and dpart >= SHARE: onpartner = False; break
                if shared or not onpartner: continue
                t0 = round(cum[i] / total * 1000); t1 = round(cum[best] / total * 1000)
                if t1 - t0 < 1: continue
                cands.append((min(cl, cr), max(cl, cr), cum[best] - cum[i], si, t0, t1, int(cl) // 5 * 5, int(cr) // 5 * 5))
        cands.sort(key=lambda c: (-c[0], -c[1], -c[2], c[3], c[4]))
        chosen = []
        for c in cands:
            if any(c[3] == o[3] and c[4] < o[5] and o[4] < c[5] for o in chosen): continue
            chosen.append(c)
            if len(chosen) == MAX_WIN: break
        return [[c[3], c[4], c[5], c[6], c[7]] for c in chosen]
    result = {}; shared_out = {}
    runs = [p for p in data['pistes'] if p.get('named') and not is_plate(p['key']) and not (p.get('kind') == 'ski-route' and p.get('access') == 'private')]
    for p in runs:
        a = windows(p, {p['key']}, None)
        if a: result[p['key']] = {'v': 1, 'a': a}
    # shared stretches (round 21, name of two runs at once): on the longer run of a pair, a window lying wholly on the stretch the
    # two share, as [s,t0,t1,cL,cR,"other key"]. A separate field, not part of `label`.
    for p in runs:
        for q in runs:
            if q['key'] == p['key'] or (q.get('len', 0), q['key']) > (p.get('len', 0), p['key']): continue
            a = windows(p, {p['key'], q['key']}, q['key'])
            if a: shared_out.setdefault(p['key'], {'v': 1, 'a': []})['a'] += [w + [q['key']] for w in a]
    return result, shared_out

def dump(d): return json.dumps(d, ensure_ascii=False, separators=(',', ':'))

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('path', nargs='?', default=str(ROOT / 'site/data/runs-and-lifts.json'))
    ap.add_argument('--lat0', type=float); ap.add_argument('--lon0', type=float)
    ap.add_argument('--check', action='store_true')
    a = ap.parse_args()
    path = Path(a.path); raw = path.read_text(encoding='utf-8'); data = json.loads(raw)
    lat0, lon0 = a.lat0, a.lon0
    if lat0 is None:
        rs = json.load(open(ROOT / 'site/data/resorts.json'))['resorts']
        r = next(x for x in rs if path.resolve().parent == (ROOT / 'site' / x['dir']).resolve())
        lat0, lon0 = r['proj']['lat0'], r['proj']['lon0']
    stripped = json.loads(raw)
    for p in stripped['pistes']: p.pop('label', None); p.pop('shared', None)
    labels, shared = build(stripped, proj(lat0, lon0))
    out = json.loads(dump(stripped))
    for p in out['pistes']:
        if p['key'] in labels: p['label'] = labels[p['key']]
        if p['key'] in shared: p['shared'] = shared[p['key']]
    if a.check:
        ok = dump(out) == dump(data)
        print('labels up to date' if ok else 'labels differ: run python3 tools/build-labels.py'); sys.exit(0 if ok else 1)
    before = len(gzip.compress(dump(stripped).encode(), 9)); after = len(gzip.compress(dump(out).encode(), 9))
    if (after - before) > BUDGET * before: print('over budget: %d -> %d bytes gzip' % (before, after)); sys.exit(1)
    path.write_text(dump(out), encoding='utf-8')
    n = sum(len(v['a']) for v in labels.values()) + sum(len(v['a']) for v in shared.values())
    print('%d runs, %d windows, gzip %d -> %d (+%.1f%%)' % (len(labels), n, before, after, (after-before)*100/before))

main()
