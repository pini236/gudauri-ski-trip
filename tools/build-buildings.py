#!/usr/bin/env python3
"""Build site/data/buildings.json: the New Gudauri buildings for the 3D map (POC, decision 77).

Inputs (all in the repo):
  tools/buildings/osm-buildings.json   footprints, OpenStreetMap (ODbL)
  tools/buildings/overture-roads.json  road centrelines, Overture Maps (derived from OSM, ODbL)
  tools/buildings/osm-mapped.json      what is mapped around them: signs, stops, parking (OSM, ODbL)
  tools/buildings/new-gudauri.json     heights, looks and where each look came from
  site/data/terrain.json               the projection (proj) shared with the 3D model
  site/data/runs-and-lifts.json        runs and lifts, so street lamps never land on them

The contract (docs/ARCHITECTURE.md, the architect's review of 30-31.10.2026):
  schema 1, additions only; coordinates in terrain.json metres with its proj; an OSM id per building;
  hsrc and lsrc are required on every building and the build fails without them; a 'fill' look is
  derived from the OSM id (deterministic, never random); decorations carry fill:true and never sit
  on a road, a building, a run or a lift line; at most 60 KB.

Usage: python3 tools/build-buildings.py [--check]
"""
import hashlib, json, math, os, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
P = lambda *a: os.path.join(ROOT, *a)
OUT = P('site', 'data', 'buildings.json')
MAX_BYTES = 60 * 1024
HSRC = {'osm-height', 'osm-levels', 'photo', 'estimate'}
LSRC = {'photo', 'fill'}


def fail(msg):
    sys.exit('build-buildings: ' + msg)


def load(*a):
    with open(P(*a), encoding='utf-8') as f:
        return json.load(f)


cfg = load('tools', 'buildings', 'new-gudauri.json')
osm = load('tools', 'buildings', 'osm-buildings.json')
rds = load('tools', 'buildings', 'overture-roads.json')
mapped = load('tools', 'buildings', 'osm-mapped.json')
proj = load('site', 'data', 'terrain.json')['proj']
runs = load('site', 'data', 'runs-and-lifts.json')

LAT0, LON0 = proj['lat0'], proj['lon0']
KX = 111320 * math.cos(math.radians(LAT0))


def xy(lat, lon):  # same projection as terrain.json: x east, y south
    return ((lon - LON0) * KX, -(lat - LAT0) * 111320)


cx, cy = xy(*cfg['center'])
RADIUS = cfg['radius']


def r1(v):
    return round(v, 1)


def area(poly):
    return sum(poly[i][0] * poly[i - 1][1] - poly[i - 1][0] * poly[i][1] for i in range(len(poly))) / 2


def simplify(pts, tol):  # Douglas-Peucker
    if len(pts) < 3:
        return pts
    a, b = pts[0], pts[-1]
    dx, dy = b[0] - a[0], b[1] - a[1]
    L = math.hypot(dx, dy) or 1e-9
    best, bi = 0, 0
    for i in range(1, len(pts) - 1):
        d = abs(dy * (pts[i][0] - a[0]) - dx * (pts[i][1] - a[1])) / L
        if d > best:
            best, bi = d, i
    if best <= tol:
        return [a, b]
    return simplify(pts[:bi + 1], tol)[:-1] + simplify(pts[bi:], tol)


def simplify_ring(ring, tol):  # a closed ring: split at the vertex farthest from the first, simplify both halves
    far = max(range(len(ring)), key=lambda i: math.dist(ring[0], ring[i]))
    a = simplify(ring[:far + 1], tol)
    b = simplify(ring[far:] + [ring[0]], tol)
    return a[:-1] + b[:-1]


def seg_dist(p, a, b):
    dx, dy = b[0] - a[0], b[1] - a[1]
    t = max(0, min(1, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / ((dx * dx + dy * dy) or 1e-9)))
    return math.hypot(p[0] - a[0] - t * dx, p[1] - a[1] - t * dy)


def line_dist(p, line):
    return min(seg_dist(p, line[i], line[i + 1]) for i in range(len(line) - 1))


def inside(p, poly):
    c = False
    for i in range(len(poly)):
        a, b = poly[i], poly[i - 1]
        if (a[1] > p[1]) != (b[1] > p[1]) and p[0] < (b[0] - a[0]) * (p[1] - a[1]) / (b[1] - a[1]) + a[0]:
            c = not c
    return c


def poly_dist(p, poly):
    if inside(p, poly):
        return 0
    return line_dist(p, poly + [poly[0]])


def pick(osm_id, n):  # deterministic choice from the OSM id
    return int(hashlib.sha1(osm_id.encode()).hexdigest()[:8], 16) % n


# ---------- buildings ----------
styles = cfg['styles']
out_b, polys = [], []
for e in osm['elements']:
    oid, tags = e['id'], e['tags']
    poly = [xy(la, lo) for la, lo in e['geometry']]
    if poly[0] == poly[-1]:
        poly = poly[:-1]
    if area(poly) < 0:  # one winding for every building (counter-clockwise in x east, y south)
        poly.reverse()
    poly = simplify_ring(poly, 0.3)
    c = cfg['buildings'].get(oid, {})
    # height: OSM first, then the per-building source, then an estimate
    lv = None
    hsrc = c.get('hsrc')
    if tags.get('building:levels'):
        lv, hsrc = int(float(tags['building:levels'])), 'osm-levels'
    elif c.get('levels'):
        lv = c['levels']
    h_total = None
    if tags.get('height'):
        try:
            h_total, hsrc = float(tags['height'].split()[0]), 'osm-height'
        except ValueError:
            pass
    if lv is None:  # no source: an estimate from the footprint, so a kiosk is not a five-storey block
        a = abs(area(poly))
        lv = next((l for lim, l in cfg['levels_by_area'] if a < lim), cfg['default_levels'])
        hsrc = hsrc or 'estimate'
    if hsrc not in HSRC:
        fail(f'{oid}: hsrc missing or unknown ({hsrc!r})')
    small = abs(area(poly)) < cfg['small_area']
    style = c.get('style') or (cfg['small_style'] if small else cfg['fill_styles'][pick(oid, len(cfg['fill_styles']))])
    lsrc = c.get('lsrc') or ('fill' if oid not in cfg['buildings'] else None)
    if lsrc not in LSRC:
        fail(f'{oid}: lsrc missing or unknown ({lsrc!r})')
    if lsrc == 'photo' and c.get('src') not in cfg['sources']:
        fail(f'{oid}: a photo look needs a source from "sources"')
    if style not in styles:
        fail(f'{oid}: unknown style {style}')
    S = styles[style]
    wall = lv * 3.0 + 1.0
    roof = {'hip': 0.42, 'flat': 0.0}[S['roof']]
    w = 2 * abs(area(poly)) / max(1, sum(math.dist(poly[i], poly[i - 1]) for i in range(len(poly))))  # mean width
    rh = r1(min(9, roof * w)) if roof else 0
    if h_total:  # OSM height is the whole building: walls up to it, the roof inside it
        wall = max(3.5, min(wall, h_total - rh * 0.6))
    b = {'id': oid, 'p': [[r1(x), r1(y)] for x, y in poly], 'lv': lv, 'h': r1(wall), 'rh': rh,
         'hsrc': hsrc, 'look': style, 'lsrc': lsrc}
    if tags.get('name'):
        b['n'] = tags['name']
    if lsrc == 'photo':
        b['src'] = c['src']
    out_b.append(b)
    polys.append(poly)
out_b.sort(key=lambda b: b['id'])

# ---------- roads ----------
widths = cfg['road_widths']
out_r, road_lines = [], []
for s in rds['segments']:
    line = [xy(la, lo) for la, lo in s['geometry']]
    if min(math.hypot(x - cx, y - cy) for x, y in line) > RADIUS + 150:
        continue
    line = simplify(line, 0.5)
    out_r.append({'k': s['class'], 'w': widths[s['class']], 'p': [[r1(x), r1(y)] for x, y in line]})
    road_lines.append((s['class'], line, widths[s['class']]))

# ---------- street lamps: decoration, fill:true, never on a road, building, run or lift ----------
L = cfg['lamps']
ski = []
for p in runs['pistes']:
    for sg in p.get('segs', []):
        g = [xy(la, lo) for la, lo in sg['g']]
        if len(g) > 1 and min(math.hypot(x - cx, y - cy) for x, y in g) < RADIUS + 200:
            ski.append(g)
for l in runs['lifts']:
    g = [xy(la, lo) for la, lo in l['g']]
    if min(math.hypot(x - cx, y - cy) for x, y in g) < RADIUS + 400:
        ski.append(g)
lamps = []
for k, line, wd in road_lines:
    if k not in L['classes']:
        continue
    tot = sum(math.dist(line[i], line[i + 1]) for i in range(len(line) - 1))
    d, side = L['every'] / 2, 1
    while d < tot:
        acc = 0
        for i in range(len(line) - 1):
            sl = math.dist(line[i], line[i + 1])
            if acc + sl >= d:
                t = (d - acc) / sl
                a, b = line[i], line[i + 1]
                nx, ny = -(b[1] - a[1]) / sl, (b[0] - a[0]) / sl
                off = wd / 2 + L['side'] - wd / 2 * 0  # beside the road edge
                p = (a[0] + (b[0] - a[0]) * t + nx * off * side, a[1] + (b[1] - a[1]) * t + ny * off * side)
                ok = math.hypot(p[0] - cx, p[1] - cy) < RADIUS
                ok = ok and all(poly_dist(p, q) > L['clear_building'] for q in polys)
                ok = ok and all(line_dist(p, rl) > w2 / 2 + 1.0 for _, rl, w2 in road_lines)
                ok = ok and all(line_dist(p, g) > L['clear_run'] for g in ski)
                ok = ok and all(math.dist(p, q) > L['every'] * 0.6 for q in lamps)
                if ok:
                    lamps.append(p)
                break
            acc += sl
        d += L['every']
        side = -side
deco = [{'t': 'lamp', 'x': r1(x), 'y': r1(y), 'fill': True} for x, y in lamps]

# ---------- mapped objects: only what OSM has (the architect: signs only if mapped), with the OSM id ----------
areas = []
for m in mapped['elements']:
    t = m['tags']
    if 'pos' in m:
        kind = 'bus_stop' if t.get('highway') == 'bus_stop' else ('sign' if 'traffic_sign' in t else t.get('highway'))
        x, y = xy(*m['pos'])
        deco.append({'t': kind, 'x': r1(x), 'y': r1(y), 'osm': m['id'], 'fill': False})
    elif t.get('amenity') == 'parking':
        ring = [xy(la, lo) for la, lo in m['geometry']]
        if ring[0] == ring[-1]:
            ring = ring[:-1]
        areas.append({'k': 'parking', 'osm': m['id'], 'p': [[r1(x), r1(y)] for x, y in simplify_ring(ring, 0.3)]})

data = {
    'schema': 1,
    'proj': proj,
    'about': 'New Gudauri buildings for the 3D map (POC). A rendering, not a photo: footprints from OpenStreetMap; '
             'height source per building in hsrc (osm-height, osm-levels, photo, estimate); look source in lsrc '
             '(photo: from a photo listed in sources, fill: completed from the village style). Decorations marked fill. '
             'Built by tools/build-buildings.py from tools/buildings/.',
    'license': 'Footprints and roads: (c) OpenStreetMap contributors, ODbL 1.0 (roads via Overture Maps). '
               'This file is a derived database under the ODbL.',
    'center': [r1(cx), r1(cy)],
    'radius': RADIUS,
    'styles': styles,
    'sources': cfg['sources'],
    'b': out_b,
    'roads': out_r,
    'deco': deco,
    'areas': areas,
}
txt = json.dumps(data, ensure_ascii=False, separators=(',', ':'))
if len(txt.encode()) > MAX_BYTES:
    fail(f'{len(txt.encode())} bytes, over the POC budget of {MAX_BYTES}')
if '--check' in sys.argv:
    with open(OUT, encoding='utf-8') as f:
        if f.read() != txt + '\n':
            fail('site/data/buildings.json is not what the tool builds; run python3 tools/build-buildings.py')
    print('build-buildings: up to date')
else:
    with open(OUT, 'w', encoding='utf-8') as f:
        f.write(txt + '\n')
    print(f'build-buildings: {len(out_b)} buildings, {len(out_r)} roads, {len(lamps)} lamps, {len(deco)-len(lamps)} mapped objects, {len(areas)} areas, {len(txt.encode())} bytes')
