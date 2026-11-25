#!/usr/bin/env python3
"""
The projection contract of the painted mountain (the architect, 20.11.2026 condition 3 and 25.11.2026 conditions 1 and
the Kobi side; docs/ARCHITECTURE.md): site/data/peak.json, schema 1, and its test values,
tools/fixtures/peak-projection.json. Every client (Android now, the iPhone and the site later) draws its live layer over
the pictures with these numbers and checks itself against the test values.

Built from the designer's views (design/redesign/tools/peak.py: the same numbers the pictures were painted with), but
with nothing of the painter: the projection, a depth grid for what is hidden behind a ridge, the runs and lifts already
projected, and the pictures' metadata. Run by hand:  python3 tools/build-peak.py   (python 3.11, numpy, Pillow)

The formula (per view): u = (e-c.e)*cos(az) - (n-c.n)*sin(az); d = (e-c.e)*sin(az) + (n-c.n)*cos(az) + near;
  x = W/2 + S*u;  y = y_c - S*(F(d) + (z - zref)*C(d)); F, C every `step` metres of d, linear between, clamped;
  z is the height model as shipped (site/data/terrain.json), bilinear; e, n are metres east and north of terrain.json's
  proj (the site's projection, y south is -n). Points of the picture (W x H); a picture of `px` pixels per point.
Hidden: the point is drawn only if 0 <= x <= W, 0 <= y <= H and d <= depth(x, y) + `depth.tol`, depth being the grid's
  cell (cell x cell points, the farthest ground seen in the cell, 65535 for sky), nearest.
A tap (`tap`): the named run whose drawn line (its `l`, segment by segment) is nearest to the point, within `radius`
  points at a scale of one point a dp; none farther.
"""
import base64, hashlib, json, math, os, shutil, sys, zlib

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'site/data/peak.json')
FIX = os.path.join(ROOT, 'tools/fixtures/peak-projection.json')
APP_IMG = os.path.join(ROOT, 'app/android/app/src/main/assets/redesign/img')
KOBI_SRC = os.path.join(ROOT, 'design/handoff/2026-11-25-redesign-kobi-picture')
LIGHTS = ['dawn', 'morning', 'noon', 'after', 'sunset', 'blue', 'night']
CELL, TOL, RADIUS, STEP = 2, 60.0, 24.0, 10
KOBI_BUDGET = 350 * 1024

D = json.load(open(os.path.join(ROOT, 'site/data/runs-and-lifts.json')))
T = json.load(open(os.path.join(ROOT, 'site/data/terrain.json')))
lat0, lon0 = T['proj']['lat0'], T['proj']['lon0']
kx, ky = 111320 * math.cos(math.radians(lat0)), 111320
EN = lambda g: ((g[1] - lon0) * kx, (g[0] - lat0) * ky)
dm = T['dem']
Z0 = np.frombuffer(base64.b64decode(dm['b64']), '<i2').astype(np.float32).reshape(dm['ny'], dm['nx'])
SX = (dm['x1'] - dm['x0']) / (dm['nx'] - 1); SY = (dm['y1'] - dm['y0']) / (dm['ny'] - 1)


def elev(e, n):
    """the model as shipped, bilinear (as Terrain.elev in the apps); e, n metres east and north"""
    c = np.clip((np.asarray(e, dtype=np.float64) - dm['x0']) / SX, 0, dm['nx'] - 1.001)
    r = np.clip((-np.asarray(n, dtype=np.float64) - dm['y0']) / SY, 0, dm['ny'] - 1.001)
    c0 = c.astype(int); r0 = r.astype(int); fc = c - c0; fr = r - r0
    return (Z0[r0, c0] * (1 - fc) + Z0[r0, c0 + 1] * fc) * (1 - fr) + (Z0[r0 + 1, c0] * (1 - fc) + Z0[r0 + 1, c0 + 1] * fc) * fr


def sstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1); return t * t * (3 - 2 * t)


# the designer's views (peak.py VIEWS), as painted; zref is the painter's (its smooth model at c), kept as it is
CE, CN = (44.4945 - lon0) * kx, (42.4865 - lat0) * ky
NORTH = -dm['y0']
VIEWS = {
    'front': dict(src='hero', packaged=True, az=0, S=0.128, c=(CE + 110, CN + 250), near=5350, far=NORTH - (CN + 250) - 60,
                  W=390, H=844, y_c=452, p=(33, 5.5, 6500, 10800), zref=2652.07, px=3),
    'kobi': dict(src='kobi', packaged=False, az=180, S=0.21, c=(-60, 2596.1), near=3900, far=2596.1 - dm['y0'] - 60,
                 W=390, H=844, y_c=530, p=(24, 4, 5800, 8200), zref=2437.97, px=1.5),
}


def setup(V):
    az = math.radians(V['az']); V['f'] = (math.sin(az), math.cos(az)); V['r'] = (math.cos(az), -math.sin(az))
    p0, p1, da, db = V['p']
    dt = np.arange(0, V['near'] + V['far'] + 20, float(STEP)); pp = np.radians(p0 + (p1 - p0) * sstep(da, db, dt))
    F = np.concatenate([[0], np.cumsum(np.sin(pp[:-1]) * STEP)]); F -= np.interp(V['near'], dt, F)
    V['dt'], V['F'], V['C'] = dt, np.round(F, 2), np.round(np.cos(pp), 4)
    return V


def to_ud(V, e, n):
    e = np.asarray(e, dtype=np.float64) - V['c'][0]; n = np.asarray(n, dtype=np.float64) - V['c'][1]
    return e * V['r'][0] + n * V['r'][1], e * V['f'][0] + n * V['f'][1] + V['near']


def screen(V, u, d, z):
    return (V['W'] / 2 + V['S'] * np.asarray(u),
            V['y_c'] - V['S'] * (np.interp(d, V['dt'], V['F']) + (np.asarray(z) - V['zref']) * np.interp(d, V['dt'], V['C'])))


def depth(V, ss=2, dstep=5.0):
    """the nearest ground seen at each pixel (metres of d; inf for sky): a march from the near edge, column by column"""
    W, H, S = V['W'] * ss, V['H'] * ss, V['S'] * ss
    u = (np.arange(W) + .5 - W / 2) / S
    top = np.full(W, float(H)); dep = np.full((H, W), np.inf)
    rows = np.arange(H)[:, None]
    for d in np.arange(0, V['near'] + V['far'], dstep):
        e = V['c'][0] + u * V['r'][0] + (d - V['near']) * V['f'][0]; n = V['c'][1] + u * V['r'][1] + (d - V['near']) * V['f'][1]
        y = (V['y_c'] - V['S'] * (np.interp(d, V['dt'], V['F']) + (elev(e, n) - V['zref']) * np.interp(d, V['dt'], V['C']))) * ss
        up = y < top
        if up.any():
            m = (rows >= np.floor(y)[None, :]) & (rows < top[None, :]) & up[None, :]
            dep[m] = d
            top = np.where(up, y, top)
    # cells of CELL x CELL points: the farthest ground seen in the cell (so a point on the ground is never hidden by its own cell)
    k = CELL * ss; ny, nx = math.ceil(H / k), math.ceil(W / k)
    g = np.full((ny * k, nx * k), np.inf); g[:H, :W] = dep
    cell = g.reshape(ny, k, nx, k).max(axis=(1, 3))
    return np.where(np.isinf(cell), 65535, np.minimum(np.round(cell), 65534)).astype('<u2')


def visible(V, x, y, d):
    g = V['depth']; ny, nx = g.shape
    j = np.clip((np.asarray(x) / CELL).astype(int), 0, nx - 1); i = np.clip((np.asarray(y) / CELL).astype(int), 0, ny - 1)
    inside = (np.asarray(x) >= 0) & (np.asarray(x) <= V['W']) & (np.asarray(y) >= 0) & (np.asarray(y) <= V['H'])
    return inside & (np.asarray(d) <= g[i, j].astype(np.float64) + TOL)


def dense(pts, step=12.0):
    o = [pts[0]]
    for a, b in zip(pts, pts[1:]):
        n = max(1, int(math.hypot(b[0] - a[0], b[1] - a[1]) / step))
        for i in range(1, n + 1): o.append((a[0] + (b[0] - a[0]) * i / n, a[1] + (b[1] - a[1]) * i / n))
    return o


def chunks(x, y, vis):
    out, cur = [], []
    for a, b, v in zip(x, y, vis):
        if v: cur += [round(float(a), 1), round(float(b), 1)]
        else:
            if len(cur) > 2: out.append(cur)
            cur = []
    if len(cur) > 2: out.append(cur)
    return out


def project(V, pts, lift=0.0):
    q = np.array(dense(pts)); u, d = to_ud(V, q[:, 0], q[:, 1]); z = elev(q[:, 0], q[:, 1])
    x, y = screen(V, u, d, z + lift); xg, yg = screen(V, u, d, z)
    return x, y, visible(V, xg, yg, d)


def seg_dist(px, py, l):
    a = np.array(l, dtype=np.float64).reshape(-1, 2); p0 = a[:-1]; p1 = a[1:]; v = p1 - p0
    t = np.clip(((px - p0[:, 0]) * v[:, 0] + (py - p0[:, 1]) * v[:, 1]) / np.maximum((v ** 2).sum(1), 1e-9), 0, 1)
    return float(np.hypot(p0[:, 0] + t * v[:, 0] - px, p0[:, 1] + t * v[:, 1] - py).min())


def tap(view, x, y):
    best, bd = None, RADIUS
    for r in view['runs']:
        if not r['named']: continue
        for l in r['l']:
            dd = seg_dist(x, y, l)
            if dd < bd: best, bd = r['key'], dd
    return best


def sha(f): return hashlib.sha256(open(f, 'rb').read()).hexdigest()


def main():
    sun = {l['name']: l for l in json.load(open(os.path.join(ROOT, 'app/android/app/src/main/assets/redesign/peak-poc.json')))['lights']}
    views, fix_pts, fix_taps = [], [], []
    for vid, V in VIEWS.items():
        setup(V)
        # the tables must be the ones the pictures were painted with (the designer's handoff)
        ref = (json.load(open(os.path.join(ROOT, 'app/android/app/src/main/assets/redesign/peak-poc.json')))['views']['hero']['proj']
               if vid == 'front' else json.load(open(os.path.join(KOBI_SRC, 'peak-kobi.json')))['view']['proj'])
        for key in ('S', 'near', 'y_c', 'zref', 'step'):
            assert abs(ref[key] - (STEP if key == 'step' else V[key])) < 1e-9, (vid, key)
        assert max(abs(a - b) for a, b in zip(ref['F'], V['F'])) < 1e-6 and len(ref['F']) == len(V['F']), (vid, 'F')
        assert max(abs(a - b) for a, b in zip(ref['C'], V['C'])) < 1e-6 and len(ref['C']) == len(V['C']), (vid, 'C')
        V['depth'] = depth(V)
        runs = []
        for p in D['pistes']:
            ln = []
            for s in p['segs']:
                if s.get('area'): continue
                x, y, vis = project(V, [EN(g) for g in s['g']]); ln += chunks(x, y, vis)
            if ln: runs.append(dict(key=p['key'], named=bool(p.get('named')), l=ln))
        lifts = []
        for l in D['lifts']:
            if not l.get('name'): continue
            pts = [EN(g) for g in l['g']]; x, y, vis = project(V, pts, lift=11.0); xg, yg, _ = project(V, pts)
            w = chunks(x, y, vis)
            if w: lifts.append(dict(name=l['name'], wire=w, ground=chunks(xg, yg, vis)))
        # the pictures: the front's are packed in the app; the Kobi side's are downloaded, in a folder named by their rev
        if V['packaged']:
            files = {l: os.path.join(APP_IMG, 'hero-%s.webp' % l) for l in LIGHTS}; path = None; rev = None
        else:
            files = {l: os.path.join(KOBI_SRC, 'img', 'kobi-%s.webp' % l) for l in LIGHTS}
            h = hashlib.sha256()
            for l in LIGHTS: h.update(open(files[l], 'rb').read())
            rev = h.hexdigest()[:10]; path = 'img/peak/%s/%s/' % (vid, rev)
            dst = os.path.join(ROOT, 'site', path); os.makedirs(dst, exist_ok=True)
            for l in LIGHTS: shutil.copyfile(files[l], os.path.join(dst, '%s-%s.webp' % (vid, l)))
            total = sum(os.path.getsize(f) for f in files.values())
            if total > KOBI_BUDGET: sys.exit('%s: %d bytes, over %d' % (vid, total, KOBI_BUDGET))
        images = [dict(light=l, file=(os.path.basename(files[l]) if V['packaged'] else '%s-%s.webp' % (vid, l)),
                       alt=sun[l]['alt'], az=sun[l]['az'], night=sun[l]['night'],
                       bytes=os.path.getsize(files[l]), sha256=sha(files[l])) for l in LIGHTS]
        dg = V['depth']
        view = dict(id=vid, packaged=V['packaged'], az=V['az'], S=V['S'], c=[round(V['c'][0], 1), round(V['c'][1], 1)],
                    near=V['near'], W=V['W'], H=V['H'], y_c=V['y_c'], zref=V['zref'], px=V['px'], step=STEP,
                    F=[float(f) for f in V['F']], C=[float(c) for c in V['C']],
                    depth=dict(cell=CELL, tol=TOL, nx=int(dg.shape[1]), ny=int(dg.shape[0]),
                               b64=base64.b64encode(zlib.compress(dg.tobytes(), 9)).decode()),
                    images=images, runs=runs, lifts=lifts)
        if rev: view.update(rev=rev, path=path)
        views.append(view)

        # test values: the lifts' ends, as the apps compute them from the shipped model (bilinear)
        for l in D['lifts']:
            if not l.get('name') or l['name'] not in ('Goodaura', 'Pirveli', 'Soliko', 'Kudebi', 'Sadzele', 'Shino', 'Kobi', 'Truso', 'Kikilo'): continue
            for tag, g in (('first', l['g'][0]), ('last', l['g'][-1])):
                e, n = EN(g); z = float(elev(e, n)); u, d = to_ud(V, e, n); x, y = screen(V, u, d, z)
                fix_pts.append(dict(view=vid, what='%s, %s end' % (l['name'], tag), lat=g[0], lon=g[1], z=round(z, 2),
                                    x=round(float(x), 2), y=round(float(y), 2), visible=bool(visible(V, x, y, d))))
        # off the picture: the village of Kobi seen from the front is past the top; a point far south
        for what, la, lo in (('south, past the picture', 42.42, 44.48), ('Stepantsminda, far north', 42.657, 44.643)):
            e, n = EN((la, lo)); z = float(elev(e, n)); u, d = to_ud(V, e, n); x, y = screen(V, u, d, z)
            fix_pts.append(dict(view=vid, what=what, lat=la, lon=lo, z=round(z, 2), x=round(float(x), 2), y=round(float(y), 2),
                                visible=bool(visible(V, x, y, d))))
        # taps: on a line, just beside it, in the sky, and between two runs
        named = [r for r in runs if r['named']]
        for r in (named[::4] if len(named) > 8 else named):
            l = max(r['l'], key=len); i = (len(l) // 4) * 2
            for dx, dy in ((0, 0), (6, 0)):
                x, y = l[i] + dx, l[i + 1] + dy
                fix_taps.append(dict(view=vid, x=round(x, 1), y=round(y, 1), run=tap(view, x, y)))
        for x, y in ((195, 20), (20, 830), (380, 400)):
            fix_taps.append(dict(view=vid, x=x, y=y, run=tap(view, x, y)))

    doc = dict(schema=1, note=__doc__.strip().split('\n\n')[0], built_from='design/redesign/tools/peak.py (VIEWS), '
               'site/data/terrain.json, site/data/runs-and-lifts.json', radius=RADIUS, views=views)
    json.dump(doc, open(OUT, 'w'), separators=(',', ':'))
    json.dump(dict(schema=1, note='made by tools/build-peak.py with site/data/peak.json; tolerance half a point',
                   tol=0.5, points=fix_pts, taps=fix_taps), open(FIX, 'w'), indent=1)
    print('%s: %d KB' % (OUT, os.path.getsize(OUT) // 1024))
    for v in views:
        print(' ', v['id'], 'runs', len(v['runs']), 'lifts', len(v['lifts']), 'depth', v['depth']['nx'], 'x', v['depth']['ny'],
              'images', sum(i['bytes'] for i in v['images']) // 1024, 'KB')
    print('%s: %d points (%d hidden or off), %d taps (%d none)' % (FIX, len(fix_pts), sum(not p['visible'] for p in fix_pts),
                                                                  len(fix_taps), sum(t['run'] is None for t in fix_taps)))


if __name__ == '__main__':
    main()
