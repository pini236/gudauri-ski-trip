#!/usr/bin/env python3
"""Build the map data of one more ski resort for the site, from open data only (decision 68).

  python3 tools/build-resort.py tools/resorts/soelden.json --cache <dir>

Writes site/data/resorts/<id>/runs-and-lifts.json and terrain.json, in the same schema as Gudauri
(site/data/runs-and-lifts.json, site/data/terrain.json), and research/resorts/<id>-data.md with the checks.

Sources (nothing is drawn by hand, CLAUDE.md, accuracy rule 1):
- runs and lifts: OpenSkiMap export (OpenStreetMap, ODbL), only the features OpenSkiMap assigns to the ski area.
  Every segment keeps its OSM way id.
- height: Terrain Tiles on AWS (terrarium PNG, zoom from the config). In Austria the source behind them is the
  national 10 m model (data.gv.at, CC BY 3.0 AT).
- villages, roads, water, peaks: OpenStreetMap through Overpass (ODbL).
Colours come from piste:difficulty in the European convention (blue, red, black), unless the resort's own written
list of runs (config 'official', a list kept in research/) gives the colour: then that one wins, as at Gudauri.
The whole process, before and after this tool: .claude/skills/new-resort/SKILL.md.

Config options beyond Sölden's (all optional):
- select: {bbox: [s, w, n, e], exclude_areas: {OpenSkiMap id: "name: why"}}: when OpenSkiMap has one area for several valleys
  (Dolomiti Superski), keep the runs and lifts whose middle is in the box and that are not in an excluded sub-area.
  The report lists every excluded sub-area with its reason (the architect, 7.10.2026).
- route_names: names of route relations (a circuit such as the Sellaronda) that OpenSkiMap glues in front of a run's
  own name; they are stripped, and a feature that is only the route is left out (it repeats the runs under it).
- rename: {"name as in the open map": "name"}, and "name|ref" for one of two runs with the same name; name_fold: {"spelling": "spelling"} inside names.
- key_by_name: the run is its name; a ref is never the key (in Italy numbers repeat in every valley).
  A key is a contract (#map/run/<key> links live outside): it is the name in Unicode NFC, so it does not change between
  builds, and two runs far apart with one name stop the build. key_suffix: {name: [[s, w, n, e, "suffix"], ...]} splits
  them, by where the line is ("Pordoi (Arabba)"); the number stays in refs.
- size: {gz_mb, mesh_vertices}: the budget for terrain.json (the architect, 7.10.2026); over it the build stops.
- official: {path, alias, out_of_scope}: the resort's written list [{name, color, area, nr}]; alias maps an official
  name to the open map's, when they differ in spelling or language.
- dem_source: {type: "tinitaly", tiles: [...], attribution}: a national 10 m model instead of the AWS tiles.
- label_tag: the OSM tag for the names of villages and peaks on the map (name:it where the name is in three languages); label_override: {name: label} where that tag is missing.

Needs numpy, Pillow and scikit-image (pip install numpy pillow scikit-image), and tifffile for a GeoTIFF height model. Downloads go to --cache, not the repo.
"""
import argparse, base64, io, json, math, pathlib, subprocess, sys, datetime, collections, unicodedata
import numpy as np
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parent.parent
UA = 'gudauri-ski-trip-build/1.0 (https://github.com/pini236/gudauri-ski-trip)'
OVERPASS = 'https://overpass.private.coffee/api/interpreter'

ap = argparse.ArgumentParser()
ap.add_argument('config'); ap.add_argument('--cache', required=True)
args = ap.parse_args()
C = json.load(open(args.config, encoding='utf-8'))
RID = C['id']
cache = pathlib.Path(args.cache); cache.mkdir(parents=True, exist_ok=True)
FETCHED = lambda: datetime.date.fromtimestamp((cache / 'osk_runs.jsonl').stat().st_mtime).isoformat()  # the day the open map was read, not the day of the build
out = ROOT / 'site/data/resorts' / RID; out.mkdir(parents=True, exist_ok=True)

def curl(url, dest, data=None, timeout=900):
    cmd = ['curl', '-sS', '--fail', '--max-time', str(timeout), '-A', UA, '-o', str(dest)]
    if data: cmd += ['--data-urlencode', 'data=' + data]
    subprocess.check_call(cmd + [url])

# ---------- download (cached) ----------
def osk(kind):
    f = cache / f'osk_{kind}.jsonl'
    if not f.exists():
        print('OpenSkiMap', kind, '...', file=sys.stderr)
        p = subprocess.Popen(['curl', '-sS', '--fail', '-A', UA, f'https://tiles.openskimap.org/geojson/{kind}.geojson'], stdout=subprocess.PIPE, text=True)
        with open(f, 'w') as fo:
            for line in p.stdout:
                if C['openskimap_id'] not in line: continue
                line = line.strip().rstrip(',')
                try: feat = json.loads(line)
                except ValueError: continue
                if C['openskimap_id'] in json.dumps(feat['properties'].get('skiAreas', [])):
                    fo.write(json.dumps(feat) + '\n')
        if p.wait(): sys.exit('OpenSkiMap download failed')
    return [json.loads(l) for l in open(f)]

s, w, n, e = C['dem_bbox']
def overpass_env():
    f = cache / 'env.json'
    if not f.exists():
        b = f'({s},{w},{n},{e})'
        q = (f'[out:json][timeout:270];(way["highway"~"^(motorway|trunk|primary|secondary|tertiary|unclassified|residential)$"]{b};'
             f'way["waterway"~"^(river|stream)$"]{b};way["natural"="water"]{b};way["landuse"="residential"]{b};'
             f'node["place"~"^(town|village|hamlet)$"]{b};node["natural"~"^(peak|saddle)$"]["name"]{b};);out geom tags;')
        curl(OVERPASS, f, q, 300)
    return json.load(open(f))

Z = C['tile_zoom']
def tile_xy(lat, lon):
    k = 2 ** Z
    return (lon + 180) / 360 * k, (1 - math.asinh(math.tan(math.radians(lat))) / math.pi) / 2 * k
def mosaic():
    x0, y0 = map(int, tile_xy(n, w)); x1, y1 = map(int, tile_xy(s, e))
    rows = []
    for ty in range(y0, y1 + 1):
        row = []
        for tx in range(x0, x1 + 1):
            f = cache / f'tiles/{Z}_{tx}_{ty}.png'
            f.parent.mkdir(exist_ok=True)
            if not f.exists() or f.stat().st_size == 0:
                curl(f'https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{Z}/{tx}/{ty}.png', f, timeout=60)
            a = np.asarray(Image.open(f).convert('RGB'), dtype=np.float64)
            row.append(a[..., 0] * 256 + a[..., 1] + a[..., 2] / 256 - 32768)
        rows.append(np.hstack(row))
    return np.vstack(rows), x0, y0

runs, lifts, env = osk('runs'), osk('lifts'), overpass_env()

# ---------- one area out of a bigger OpenSkiMap area (several valleys under one name) ----------
SEL = C.get('select')
def mid(g):
    c = g['coordinates']; t = g['type']
    pts = c if t == 'LineString' else c[0] if t == 'Polygon' else [q for l in c for q in (l if isinstance(l[0][0], (int, float)) else l[0])]
    return sum(q[1] for q in pts) / len(pts), sum(q[0] for q in pts) / len(pts)
def area_ids(p): return {a['properties']['id'] if isinstance(a, dict) and 'properties' in a else a.get('id') if isinstance(a, dict) else a for a in p.get('skiAreas') or []}
def selected(f):
    if not SEL: return True
    la, lo = mid(f['geometry']); b = SEL['bbox']
    if any(x[0] <= la <= x[2] and x[1] <= lo <= x[3] for x in SEL.get('exclude_boxes', [])): return False  # a corner that belongs to a neighbour
    return b[0] <= la <= b[2] and b[1] <= lo <= b[3] and not area_ids(f['properties']) & set(SEL.get('exclude_areas', []))
runs = [f for f in runs if selected(f)]; lifts = [f for f in lifts if selected(f)]

# ---------- height: AWS terrain tiles (default) or a national model in GeoTIFF ----------
DEMS = C.get('dem_source')
if DEMS and DEMS['type'] == 'tinitaly':
    import tifffile
    def utm32(la, lo):  # WGS84 to UTM 32N, Krueger series (the same as every GIS to well under a metre)
        la, lo = np.radians(np.asarray(la, float)), np.radians(np.asarray(lo, float)) - math.radians(9)
        a, f, k0 = 6378137.0, 1 / 298.257223563, 0.9996; n = f / (2 - f); A = a / (1 + n) * (1 + n * n / 4 + n ** 4 / 64)
        al = [n / 2 - 2 * n * n / 3 + 5 * n ** 3 / 16, 13 * n * n / 48 - 3 * n ** 3 / 5, 61 * n ** 3 / 240]
        e = 2 * math.sqrt(n) / (1 + n)
        t = np.sinh(np.arctanh(np.sin(la)) - e * np.arctanh(e * np.sin(la)))
        xi, eta = np.arctan(t / np.cos(lo)), np.arctanh(np.sin(lo) / np.sqrt(1 + t * t))
        E = eta + sum(al[j] * np.cos(2 * (j + 1) * xi) * np.sinh(2 * (j + 1) * eta) for j in range(3))
        N = xi + sum(al[j] * np.sin(2 * (j + 1) * xi) * np.cosh(2 * (j + 1) * eta) for j in range(3))
        return 500000 + k0 * A * E, k0 * A * N
    TILES = []
    for name in DEMS['tiles']:
        tf = tifffile.TiffFile(cache / 'dem' / f'{name}_s10' / f'{name}_s10.tif'); pg = tf.pages[0]
        sx = pg.tags['ModelPixelScaleTag'].value[0]; tp = pg.tags['ModelTiepointTag'].value
        TILES.append((tp[3], tp[4], sx, pg.asarray().astype(np.float64)))
    print('dem tiles', [(t[0], t[1], t[3].shape) for t in TILES], file=sys.stderr)
    def hv(la, lo):  # bilinear between the 10 m cell centres, in whichever tile holds the point
        E, N = utm32(la, lo); out = np.full(np.shape(E), np.nan)
        for e0, n0, sx, A in TILES:
            px = (E - e0) / sx - .5; py = (n0 - N) / sx - .5
            ok = (px >= 0) & (py >= 0) & (px < A.shape[1] - 1) & (py < A.shape[0] - 1) & np.isnan(out)
            i = np.floor(px[ok]).astype(int); j = np.floor(py[ok]).astype(int); fx, fy = px[ok] - i, py[ok] - j
            out[ok] = (A[j, i] * (1 - fx) + A[j, i + 1] * fx) * (1 - fy) + (A[j + 1, i] * (1 - fx) + A[j + 1, i + 1] * fx) * fy
        if np.isnan(out).any() or (out < -1000).any(): sys.exit('height model: a point outside the tiles or on no-data')
        return out
    DEM_DESC = 'TINITALY/1.1 (INGV), 10 m'
else:
    M, TX0, TY0 = mosaic()
    print('mosaic', M.shape, 'height', round(M.min()), round(M.max()), file=sys.stderr)
    def hv(la, lo): return mos_hv(la, lo)
    DEM_DESC = f'AWS terrain tiles, zoom {Z}'

# ---------- projection: the site's own (app.js), metres east and south of proj ----------
lat0, lon0 = C['proj']['lat0'], C['proj']['lon0']
KX, KY = 111320 * math.cos(math.radians(lat0)), 111320
def P(la, lo): return ((lo - lon0) * KX, -(la - lat0) * KY)
def unP(x, y): return (lat0 - y / KY, lon0 + x / KX)
def mos_hv(la, lo):  # bilinear in the full-resolution mosaic, on arrays of lat and lon
    la, lo = np.asarray(la, float), np.asarray(lo, float); k = 2 ** Z
    px = ((lo + 180) / 360 * k - TX0) * 256 - .5
    py = ((1 - np.arcsinh(np.tan(np.radians(la))) / np.pi) / 2 * k - TY0) * 256 - .5
    i = np.clip(np.floor(px).astype(int), 0, M.shape[1] - 2); j = np.clip(np.floor(py).astype(int), 0, M.shape[0] - 2)
    fx, fy = np.clip(px - i, 0, 1), np.clip(py - j, 0, 1)
    return (M[j, i] * (1 - fx) + M[j, i + 1] * fx) * (1 - fy) + (M[j + 1, i] * (1 - fx) + M[j + 1, i + 1] * fx) * fy
def unPv(x, y): return lat0 - np.asarray(y) / KY, lon0 + np.asarray(x) / KX

# ---------- the DEM grid, the same layout as Gudauri (row 0 = north) ----------
STEP = C['dem_step_m']
X0, Y0 = P(n, w); X1, Y1 = P(s, e)
nx = int((X1 - X0) // STEP) + 1; ny = int((Y1 - Y0) // STEP) + 1
X1 = X0 + (nx - 1) * STEP; Y1 = Y0 + (ny - 1) * STEP
# average the 10 m source into each 40 m cell (a point sample would alias ridges): mean of a 4x4 sub-grid
GX, GY = np.meshgrid(X0 + np.arange(nx) * STEP, Y0 + np.arange(ny) * STEP)
sub = [(-.375 + k * .25) * STEP for k in range(4)]
H = sum(hv(*unPv(GX + dx, GY + dy)) for dx in sub for dy in sub) / 16
H16 = np.round(H).astype('<i2'); HF = H16.astype(float)
def elev(x, y):  # the same bilinear lookup as relief.js
    cx = min(max((x - X0) / STEP, 0), nx - 1.0001); ry = min(max((y - Y0) / STEP, 0), ny - 1.0001)
    c0, r0 = int(cx), int(ry); fc, fr = cx - c0, ry - r0
    a = HF
    return (a[r0, c0] * (1 - fc) + a[r0, c0 + 1] * fc) * (1 - fr) + (a[r0 + 1, c0] * (1 - fc) + a[r0 + 1, c0 + 1] * fc) * fr

# ---------- hillshade (light from the north-west, like Gudauri's) ----------
PXM = C['hill_px_m']; hw = int((X1 - X0) / PXM); hh = int((Y1 - Y0) / PXM)
HX, HY = np.meshgrid(X0 + (np.arange(hw) + .5) * (X1 - X0) / hw, Y0 + (np.arange(hh) + .5) * (Y1 - Y0) / hh)
HS = hv(*unPv(HX, HY))
gy, gx = np.gradient(HS, (Y1 - Y0) / hh, (X1 - X0) / hw)
az, alt = math.radians(315), math.radians(45)
lx, ly, lz = math.cos(alt) * math.sin(az), -math.cos(alt) * math.cos(az), math.sin(alt)  # y grows south
shade = np.clip((-gx * lx - gy * ly + lz) / np.sqrt(gx ** 2 + gy ** 2 + 1), 0, 1)
img = np.clip(255 * (0.36 + 0.66 * shade), 0, 241).astype(np.uint8)
buf = io.BytesIO(); Image.fromarray(img, 'L').save(buf, 'JPEG', quality=62, optimize=True)
hill = {'w': hw, 'h': hh, 'src': 'data:image/jpeg;base64,' + base64.b64encode(buf.getvalue()).decode()}

# ---------- contours every 50 m, delta-coded Int16 [lev,n,x,y,dx,dy...] ----------
from skimage import measure
def rdp(pts, eps):
    if len(pts) < 3: return pts
    a, b = np.array(pts[0]), np.array(pts[-1]); v = b - a; L = np.hypot(*v)
    d = [abs(v[0] * (p[1] - a[1]) - v[1] * (p[0] - a[0])) / L if L else np.hypot(*(np.array(p) - a)) for p in pts[1:-1]]
    k = int(np.argmax(d)) + 1
    if d[k - 1] > eps: return rdp(pts[:k + 1], eps)[:-1] + rdp(pts[k:], eps)
    return [pts[0], pts[-1]]
cont = []
for lev in range(int(H.min() // 50 + 1) * 50, int(H.max()) + 1, 50):
    for line in measure.find_contours(H, lev):
        pts = [(round(X0 + c * STEP), round(Y0 + r * STEP)) for r, c in line]
        pts = rdp(pts, 6)
        if len(pts) < 3: continue
        cont += [lev, len(pts), pts[0][0], pts[0][1]]
        for a, b in zip(pts, pts[1:]): cont += [b[0] - a[0], b[1] - a[1]]
contours = base64.b64encode(np.array(cont, dtype='<i2').tobytes()).decode()

# ---------- environment ----------
inside = lambda x, y: X0 <= x <= X1 and Y0 <= y <= Y1
def flat(geom, eps=8):
    pts = [tuple(round(v) for v in P(q['lat'], q['lon'])) for q in geom]
    pts = rdp(pts, eps)
    return [v for q in pts for v in q]
RK = {'motorway': 0, 'trunk': 0, 'primary': 0, 'secondary': 1, 'tertiary': 2, 'unclassified': 3, 'residential': 4}
roads, water, village, rivers, places, peaks = [], [], [], [], [], []
for el in env['elements']:
    t = el.get('tags', {})
    if el['type'] == 'way':
        g = el.get('geometry') or []
        if len(g) < 2: continue
        if t.get('highway') in RK: roads.append([RK[t['highway']], flat(g)])
        elif t.get('waterway') in ('river', 'stream'):
            if t['waterway'] == 'river' or len(g) > 12: rivers.append(flat(g))
        elif t.get('natural') == 'water': water.append({'n': t.get('name'), 'k': None, 'g': flat(g, 4)})
        elif t.get('landuse') == 'residential': village.append(flat(g, 4))
    elif el['type'] == 'node':
        x, y = (round(v) for v in P(el['lat'], el['lon']))
        if t.get('place') and t.get('name') in C['places']:
            places.append({'n': C.get('label_override', {}).get(t['name']) or t.get(C.get('label_tag', 'name')) or t['name'], 'x': x, 'y': y, 'ele': t.get('ele'), 'show': True, **({'main': True} if t['name'] == C['main_place'] else {})})
        if t.get('natural') in ('peak', 'saddle') and t.get('name') in C['peaks']:
            pb = C['peak_box']
            if pb[0] <= el['lat'] <= pb[2] and pb[1] <= el['lon'] <= pb[3]:
                try: ele = round(float(str(t.get('ele', '')).replace(',', '.')))
                except ValueError: ele = round(elev(x, y))
                peaks.append({'n': C.get('label_override', {}).get(t['name']) or t.get(C.get('label_tag', 'name')) or t['name'], 'ele': ele, 'x': x, 'y': y, 'pass': t['natural'] == 'saddle'})
peaks.sort(key=lambda p: -p['ele'])
terrain = {'license': 'Height: ' + C['dem_attribution'] + ' Villages, roads, water and peaks: © OpenStreetMap contributors, ODbL 1.0; this part is a derived database under the ODbL.',
           'proj': {'lat0': lat0, 'lon0': lon0},
           'dem': {'nx': nx, 'ny': ny, 'x0': round(X0, 2), 'y0': round(Y0, 2), 'x1': round(X1, 2), 'y1': round(Y1, 2),
                   'cell': STEP, 'mesh': C.get('mesh_stride', 1),  # metres per cell, and the 3D mesh takes every mesh-th cell (the architect, 7.10.2026)
                   'b64': base64.b64encode(H16.tobytes()).decode()},
           'hill': hill, 'contours': contours,
           'env': {'roads': roads, 'water': water, 'village': village, 'rivers': rivers, 'places': places},
           'peaks': peaks}

# ---------- runs and lifts ----------
def ll(c): return [round(c[1], 6), round(c[0], 6)]   # GeoJSON [lon, lat] -> the site's [lat, lon]
def osm_id(f):
    src = [x['id'] for x in f['properties'].get('sources', []) if x['type'] == 'openstreetmap' and x['id'].startswith('way/')]
    return int(src[0].split('/')[1]) if src else None
def hl(lat, lon): return elev(*P(lat, lon))
def dist(a, b): pa, pb = P(*a), P(*b); return math.hypot(pa[0] - pb[0], pa[1] - pb[1])
def length(g): return sum(dist(a, b) for a, b in zip(g, g[1:]))

LIFT_KINDS = {'chair_lift', 'gondola', 'cable_car', 'platter', 't-bar', 'j-bar', 'drag_lift', 'magic_carpet', 'rope_tow', 'funicular', 'mixed_lift'}
LIFTS = []
for f in lifts:
    p = f['properties']
    if f['geometry']['type'] != 'LineString' or p.get('status') not in (None, 'operating'): continue
    g = [ll(c) for c in f['geometry']['coordinates']]
    if hl(*g[0]) > hl(*g[-1]): g = g[::-1]          # bottom to top, like Gudauri
    kind = {'t-bar': 't_bar', 'j-bar': 't_bar', 'rope_tow': 'drag_lift', 'cable_car': 'gondola', 'mixed_lift': 'gondola'}.get(p['liftType'], p['liftType'])
    LIFTS.append({'id': osm_id(f), 'name': p.get('name'), 'kind': kind, 'g': g, 'len': round(length(g)),
                  'dur': str(round(p['duration'] / 60)) if p.get('duration') else None,
                  'occ': str(p['occupancy']) if p.get('occupancy') else None,
                  'bubble': p.get('bubble'), 'cap': p.get('capacity'), 'rise': round(hl(*g[-1]) - hl(*g[0])),
                  'year': None, 'status': None})
# one lift drawn as many ways (a funicular in 26 pieces): join pieces of the same name and kind that meet end to start
def join_lifts(L):
    out = []
    for l in L:
        for m in out:
            if l['name'] and m['name'] == l['name'] and m['kind'] == l['kind']:
                if dist(m['g'][-1], l['g'][0]) < 5: m['g'] = m['g'] + l['g'][1:]; break
                if dist(l['g'][-1], m['g'][0]) < 5: m['g'] = l['g'] + m['g'][1:]; break
        else: out.append(l); continue
        m['len'] = round(length(m['g'])); m['rise'] = round(hl(*m['g'][-1]) - hl(*m['g'][0]))
    return out
n0 = 0
while n0 != len(LIFTS): n0 = len(LIFTS); LIFTS = join_lifts(LIFTS)
LIFTS.sort(key=lambda l: (l['name'] is None, l['name'] or '', l['id']))

# ---------- an official lift registry, when the resort's region publishes one (capacity and year only; the line stays the open map's) ----------
REG = C.get('lift_registry')
if REG:
    rf = cache / f'registry_{RID}.geojson'
    if not rf.exists():
        b = C['dem_bbox']
        curl(REG['url'] + '/query?where=1%3D1&geometry=' + f'{b[1]},{b[0]},{b[3]},{b[2]}' + '&geometryType=esriGeometryEnvelope&inSR=4326&outSR=4326&outFields=*&f=geojson', rf)
    RL = []
    for f in json.load(open(rf, encoding='utf-8'))['features']:
        gg = f['geometry']; cs = gg['coordinates'] if gg['type'] == 'LineString' else [c for part in gg['coordinates'] for c in part]
        RL.append((f['properties'], [ll(c) for c in cs]))
    def mid_ends(g): return [g[0], g[-1]]
    used = set()
    for l in LIFTS:
        best = None
        for i, (pr, g) in enumerate(RL):
            if i in used: continue
            a, b = mid_ends(l['g']); c, d = mid_ends(g)
            e = min(max(dist(a, c), dist(b, d)), max(dist(a, d), dist(b, c)))  # both ends, either direction
            if e < REG.get('max_end_m', 120) and (best is None or e < best[0]): best = (e, i, pr)
        if not best: continue
        used.add(best[1]); pr = best[2]; F = REG['fields']
        if pr.get(F['cap']): l['cap'] = int(pr[F['cap']])
        if pr.get(F['year']): l['year'] = int(pr[F['year']])
        if pr.get(F.get('slope')): l['lenSlope'] = int(pr[F['slope']])  # the registry's slope length; 'len' stays horizontal, like Gudauri
        l['reg'] = {'name': pr.get(F['name']), 'end_m': round(best[0])}
    REG_UNMATCHED = {'ours': [l['name'] or f"(no name, {l['kind']}, {l['len']} m)" for l in LIFTS if 'reg' not in l],
                     'registry': [pr.get(REG['fields']['name']) for i, (pr, g) in enumerate(RL) if i not in used]}
    REG_FETCHED = datetime.date.fromtimestamp(rf.stat().st_mtime).isoformat()
    print('registry:', sum(1 for l in LIFTS if 'reg' in l), 'of', len(LIFTS), 'lifts matched; ours unmatched:', REG_UNMATCHED['ours'], file=sys.stderr)

def norm_number(v):
    v = (v or '').strip()
    return v if v and len(v) <= 4 and v[0].isdigit() else None
OV = C.get('overrides', {})
# ---------- names: route relations glued in front, renames, and the resort's written list ----------
import re, unicodedata
ROUTES = sorted(C.get('route_names', []), key=len, reverse=True)
def strip_routes(n):
    n, got, more = (n or '').strip(), [], True
    while more:
        more = False
        for r in ROUTES:
            if n == r or n.startswith(r + ', '): got.append(r); n = n[len(r) + 2:] if n != r else ''; more = True; break
    return n, got
ROM = {'i': '1', 'ii': '2', 'iii': '3', 'iv': '4', 'v': '5'}
NOISE = {'collegamento': 'conn', 'connection': 'conn', 'connecting': 'conn', 'verbindung': 'conn', 'raccordo': 'conn', 'variante': 'var', 'variant': 'var'}
def norm_name(v):  # for matching only: accents, case, roman numerals, "connection" in any of the languages
    v = unicodedata.normalize('NFKD', v or '').encode('ascii', 'ignore').decode().lower()
    return ' '.join(NOISE.get(ROM.get(w, w), ROM.get(w, w)) for w in re.split(r'[^a-z0-9]+', v) if w)
def norm_ref(v): return re.sub(r'\s+', '', (v or '').lower())
OFF = C.get('official')
OFFICIAL = json.load(open(ROOT / OFF['path'], encoding='utf-8'))['runs'] if OFF else []
route_only, route_runs = collections.Counter(), collections.defaultdict(set)
groups, skipped = collections.OrderedDict(), []
for f in runs:
    p = f['properties']
    uses = set(p.get('uses') or [])
    if ROUTES:
        own, got = strip_routes(p.get('name'))
        rel_only = all(x['id'].startswith('relation/') for x in p.get('sources', []))
        if rel_only and not own: route_only[', '.join(got) or '(no name)'] += 1; continue  # the route itself: its runs are there already
        ref = (p.get('ref') or '').strip()
        for x, y in C.get('name_fold', {}).items(): own = own.replace(x, y)  # one spelling for a name the open map spells two ways
        own = C.get('rename', {}).get(own + '|' + ref, C.get('rename', {}).get(own, own))
        p = dict(p, name=own, _rel=rel_only)
        for r in got: route_runs[r].add(own or '(no name)')
    label = (p.get('name') or p.get('ref') or '').strip()
    # a ski route (Skiroute: not prepared, not patrolled, no difficulty under the Tyrol rules) is not a run with a colour:
    # kind 'ski-route', colour 'none' (Pini, round 24 and the architect, 7.10.2026). Only by the tag or the resort's own list, never a guess
    route = p.get('grooming') == 'backcountry' or label in OV.get('ski_routes', []) or (p.get('ref') or '') in OV.get('ski_routes', [])
    if label in OV.get('exclude', []): skipped.append((label, OV['exclude'][label] if isinstance(OV['exclude'], dict) else 'a park, not a run (the ski expert)')); continue
    if not uses & {'downhill', 'connection'}: skipped.append((p.get('name') or p.get('ref'), 'not a downhill run (' + ','.join(sorted(uses)) + ')')); continue
    if not p.get('difficulty') and not route: skipped.append((p.get('name') or p.get('ref'), 'no difficulty in OSM, so no colour')); continue
    name, ref = (p.get('name') or '').strip(), (p.get('ref') or '').strip()
    # one run per piste number; a segment shared by two numbers ("4 / 5", "50, 9") belongs to both
    nums = [norm_number(x) for x in name.replace(',', '/').split('/')] if name and norm_number(name.replace(',', '/').split('/')[0]) else []
    nums = [x for x in nums if x]
    if not nums and norm_number(ref) and not (C.get('key_by_name') and name): nums = [norm_number(ref)]
    if C.get('key_by_name') and not name: nums = []  # a number alone repeats in every valley: the line is an unnamed section
    nums = [OV.get('swap_numbers', {}).get(x, x) for x in nums]  # numbers swapped in the open map, by the resort's own list
    nums = [x for x in nums if x not in OV.get('unnumber', [])]  # a number the resort's own map does not have: the line stays, as an unnamed section
    gid = osm_id(f); poly = f['geometry']['type'] == 'Polygon'
    coords = f['geometry']['coordinates'][0] if poly else f['geometry']['coordinates']
    g = [ll(c) for c in coords]
    if name in OV.get('unnumber', []) or ref in OV.get('unnumber', []): name = ''  # its only name was the number
    if C.get('key_by_name') and name:
        name = unicodedata.normalize('NFC', name)
        la_, lo_ = mid(f['geometry'])
        sfx = [b[4] for b in C.get('key_suffix', {}).get(name, []) if b[0] <= la_ <= b[2] and b[1] <= lo_ <= b[3]]
        if sfx: name = f'{name} ({sfx[0]})'
    keys = nums or ([name] if name else ['u%d' % gid])
    for k in keys:
        G = groups.setdefault(k, {'key': k, 'name': k if (nums or name) else None, 'osmNames': set(), 'refs': set(), 'diff': collections.Counter(),
                                   'osmDiff': set(), 'groom': set(), 'lit': set(), 'segs': [], 'named': bool(nums or name), 'route': route})
        G['route'] = G['route'] and route  # a number that is a run somewhere stays a run
        if name: G['osmNames'].add(name)
        if ref: G['refs'].add(ref)
        if p.get('difficulty'): G['osmDiff'].add(p['difficulty'])
        if not poly and p.get('difficulty'): G['diff'][p['difficulty']] += length(g)
        if p.get('grooming'): G['groom'].add(p['grooming'])
        if p.get('lit') is not None: G['lit'].add('yes' if p['lit'] else 'no')
        if not poly and hl(*g[0]) < hl(*g[-1]): g = g[::-1]
        G['segs'].append({'id': gid, 'area': poly, 'diff': p['difficulty'], 'g': g, **({'tunnel': True} if p.get('tunnel') else {}), **({'_rel': True} if p.get('_rel') else {})})
# a run drawn by a relation repeats the ways under it: keep it only when there is no way of that name, and it does not lie
# on other runs' ways (a race course or a named group of runs, such as Gardenissima, over runs already on the map)
WAYIX = collections.defaultdict(list)
for G in groups.values():
    for s_ in G['segs']:
        if s_.get('_rel') or s_['area']: continue
        for q in s_['g']:
            x, y = P(*q); WAYIX[(int(x // 50), int(y // 50))].append((x, y))
def on_ways(g):
    pts = [P(*q) for q in g]; near = 0
    for x, y in pts:
        cx, cy = int(x // 50), int(y // 50)
        if any(math.hypot(x - a, y - b) < 25 for i in (-1, 0, 1) for j in (-1, 0, 1) for a, b in WAYIX.get((cx + i, cy + j), ())): near += 1
    return near >= .7 * len(pts)
for k in list(groups):
    G = groups[k]
    if any(not s.get('_rel') for s in G['segs']): G['segs'] = [s for s in G['segs'] if not s.get('_rel')]
    else:
        dup = [s for s in G['segs'] if s.get('_rel') and not s['area'] and on_ways(s['g'])]
        if dup: skipped.append((G['key'], 'a relation over runs already on the map (it repeats them)')); G['segs'] = [s for s in G['segs'] if s not in dup]
    for s_ in G['segs']: s_.pop('_rel', None)
    if not G['segs']: del groups[k]

# one key, one run: two groups of lines with one name, far apart, are two runs (Pordoi above Arabba and above Canazei).
# Merging them would draw one run in two valleys and give both one colour, so the build stops until key_suffix splits them.
def pieces(G):
    S = [[P(*q) for q in s_['g']] for s_ in G['segs']]
    ends = [a[::4] + [a[-1]] for a in S]
    par = list(range(len(S)))
    def fd(i):
        while par[i] != i: i = par[i]
        return i
    near = lambda a, b: min(math.hypot(x - u, y - v) for x, y in a for u, v in b)
    for i in range(len(S)):
        for j in range(i + 1, len(S)):
            if fd(i) != fd(j) and near(ends[i], ends[j]) < 300: par[fd(i)] = fd(j)
    cl = collections.defaultdict(list)
    for i in range(len(S)): cl[fd(i)].append(i)
    cl = list(cl.values())
    gap = min((near(ends[a], ends[b]) for x in range(len(cl)) for y in range(x + 1, len(cl)) for a in cl[x] for b in cl[y]), default=0)
    return len(cl), gap
if C.get('key_by_name'):
    clash = [(G['key'], *pieces(G)) for G in groups.values() if G['named']]
    clash = [c for c in clash if c[1] > 1 and c[2] > 800]
    if clash:
        sys.exit('key collision: one name, runs far apart (add key_suffix in the config): '
                 + '; '.join(f'{k}: {n} pieces, {round(g)} m apart' for k, n, g in clash))

# the resort's written list (official names and colours), matched by name, and by number when both have one
off_by = {}
for o in OFFICIAL:
    if o['name'] in OFF.get('out_of_scope', []): continue
    off_by.setdefault(norm_name(OFF.get('alias', {}).get(o['name'], o['name'])), []).append(o)
OFF_USED = set()
def official_for(G, osm_color):
    """all the official entries with this run's name: the same run in two lists (Val Gardena and Val di Fassa both list the
    Passo Sella runs), or two runs with one name (Sef blue and Sef red). One line takes the one whose number, then colour,
    agrees; the others count as found only when they are the same run (same colour)."""
    c = off_by.get(norm_name(G['key']), [])
    if not c: return None, []
    refs = {norm_ref(r) for r in G['refs']}
    best = sorted(c, key=lambda o: (not (o.get('nr') and norm_ref(o['nr']) in refs), o['color'] != osm_color))[0]
    same = [o for o in c if o['color'] == best['color']]
    return best, [o for o in c if o not in same]

# ---------- checks (accuracy rule 4): every line descends, and starts and ends near lifts or runs ----------
def climb(g):
    """counter-climb along the line, top down: sum of rises, sampled every 20 m on the model the site draws"""
    pts = []
    for a, b in zip(g, g[1:]):
        k = max(1, int(dist(a, b) // 20))
        pts += [(a[0] + (b[0] - a[0]) * t / k, a[1] + (b[1] - a[1]) * t / k) for t in range(k)]
    pts.append(tuple(g[-1]))
    hs = [hl(*q) for q in pts]
    return round(sum(max(0, b - a) for a, b in zip(hs, hs[1:])))
lift_tops = [(l['name'], l['g'][-1]) for l in LIFTS]; lift_bots = [(l['name'], l['g'][0]) for l in LIFTS]
pistes, report = [], []
for G in groups.values():
    lines = [s['g'] for s in G['segs'] if not s['area']]
    checked = [s['g'] for s in G['segs'] if not s['area'] and not s.get('tunnel')]  # a tunnel goes under the hill the model measures
    color = 'none' if G['route'] else C['colors'][G['diff'].most_common(1)[0][0]] if G['diff'] else C['colors'][sorted(G['osmDiff'])[0]]
    O, other = official_for(G, color) if OFFICIAL and G['named'] else (None, [])
    if O:
        osm_color = color; color = O['color']; OFF_USED.update(id(o) for o in off_by[norm_name(G['key'])] if o['color'] == O['color'])
        if other: G['twins'] = sorted({f"{o['name']} ({o['color']})" for o in other})
        if O['name'] != G['key'] and not any(g2 is not G and g2['key'] == O['name'] for g2 in groups.values()): G['key'] = G['name'] = O['name']
        G['official'] = {'name': O['name'], 'area': O.get('area'), 'osmColor': osm_color, 'twins': G.get('twins')}
    tops = [L[0] for L in lines] or [G['segs'][0]['g'][0]]; bots = [L[-1] for L in lines] or tops
    P_ = {'key': G['key'], 'name': G['name'], 'osmNames': sorted(G['osmNames']), 'color': color, 'named': G['named'],
          'len': round(sum(length(L) for L in lines)), 'osmDiff': sorted(G['osmDiff']), 'refs': sorted(G['refs']),
          'groom': sorted(G['groom']), 'lit': sorted(G['lit']), 'segs': G['segs'], **({'kind': 'ski-route'} if G['route'] else {}), '_off': G.get('official')}
    P_['fromLifts'] = sorted({nm for nm, q in lift_tops if nm and any(dist(q, t) <= 200 for t in tops)})
    P_['toLifts'] = sorted({nm for nm, q in lift_bots if nm and any(dist(q, b) <= 200 for b in bots)})
    climbs = [climb(L) for L in checked]
    P_['_climb'] = max(climbs) if climbs else 0
    pistes.append(P_)
# joins / fromPistes: within 60 m of another run's line
def near_line(q, other):
    return any(dist(q, v) <= 60 for s in other['segs'] if not s['area'] for v in s['g'])
for p in pistes:
    bots = [s['g'][-1] for s in p['segs'] if not s['area']]; tops = [s['g'][0] for s in p['segs'] if not s['area']]
    p['joins'] = sorted({o['key'] for o in pistes if o is not p and any(near_line(b, o) for b in bots)})
    p['fromPistes'] = sorted({o['key'] for o in pistes if o is not p and any(near_line(t, o) for t in tops)})
    ok_top = bool(p['fromLifts'] or p['fromPistes']); ok_bot = bool(p['toLifts'] or p['joins'])
    flags = []
    if p['_climb'] > 10: flags.append(f"עלייה נגדית {p['_climb']} מ׳")
    if not ok_top: flags.append('ההתחלה רחוקה מרכבל ומסלול')
    if not ok_bot: flags.append('הסוף רחוק מרכבל ומסלול')
    mixed = len({d for d in p['osmDiff']}) > 1
    if mixed: flags.append('דרגות קושי שונות בקטעים: ' + ', '.join(p['osmDiff']))
    extra = []
    O = p.pop('_off')
    if OFFICIAL and p['named'] and p.get('kind') != 'ski-route':
        if O and O['osmColor'] != p['color']: flags.append(f"הצבע במפה הפתוחה {O['osmColor']}, ברשימה הרשמית {p['color']}: לפי הרשמית")
        if O and O['name'] not in p['osmNames']: extra.append('במפה הפתוחה: ' + ', '.join(p['osmNames']))
        if O and O.get('twins'): flags.append('ברשימה הרשמית עוד מסלול בשם הזה, בצבע אחר, ואין לו קו נפרד במפה הפתוחה: ' + ', '.join(O['twins']))
        if not O: extra.append('לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה')
    if p['key'] in OV.get('swap_numbers', {}): extra.append('המספר הוחלף לפי הרשימה הרשמית של האתר (במפה הפתוחה ' + {v: k for k, v in OV['swap_numbers'].items()}[p['key']] + ')')
    if p['key'] in OV.get('check_again', []): flags.append('המספר לא מופיע ברשימה הרשמית של האתר; לבדוק')
    if not p['named'] and set(p['refs']) & set(OV.get('unnumber', [])): extra.append('המספר במפה הפתוחה לא מופיע במפה הרשמית, ולכן הקו בלי מספר')
    if p.get('kind') == 'ski-route' and p['key'] in OV.get('private_routes', []): p['access'] = 'private'  # a private route to a hut (the resort's legend)
    p['kind'] = 'ski-route' if p.get('kind') == 'ski-route' else 'ski-way' if p['key'] in OV.get('ski_ways', []) else 'run'  # a road or link the resort's map draws as a ski way, not a slope
    if p['kind'] == 'ski-route': extra.append('דרך סקי (Skiroute): מאובטחת רק מפני מפולות, בלי הכשרה ובלי דרגת קושי; לפי piste:grooming=backcountry במפה הפתוחה או הרשימה הרשמית של האתר')
    if p['kind'] == 'ski-way': extra.append('דרך מקשרת ולא מסלול, לפי המקרא של המפה הרשמית (סוג בלבד)')
    p['research'] = {'conf': 'medium' if not flags else 'low', 'status': 'osm-named' if p['named'] else 'osm-unnamed',
                     'notes': ('מהמפה הפתוחה כמו שהיא, בלי צבע ובלי דרגת קושי.' if p['kind'] == 'ski-route' else C.get('color_note', 'מהמפה הפתוחה כמו שהיא. הצבע לפי דרגת הקושי במפה הפתוחה (אירופה: קל כחול, בינוני אדום, קשה שחור).')) + (' ' + '; '.join(extra) + '.' if extra else '') + (' בדיקות: ' + '; '.join(flags) + '.' if flags else ''),
                     'sources': ['OpenSkiMap ' + FETCHED() + ' (OpenStreetMap, ODbL)'],
                     'osmIds': sorted({s['id'] for s in p['segs'] if s['id']}), 'checks': flags}
    report.append((p['key'], p['color'], p['len'], p['_climb'], flags + extra))
    del p['_climb']
order = {'green': 0, 'blue': 1, 'red': 2, 'black': 3, 'none': 4}
pistes.sort(key=lambda p: (not p['named'], order[p['color']], [int(x) if x.isdigit() else x for x in __import__('re').split(r'(\d+)', p['key'])]))

lats = [q[0] for p in pistes for s in p['segs'] for q in s['g']] + [q[0] for l in LIFTS for q in l['g']]
lons = [q[1] for p in pistes for s in p['segs'] for q in s['g']] + [q[1] for l in LIFTS for q in l['g']]
LICENSE = ('Runs and lifts from OpenStreetMap through OpenSkiMap: © OpenStreetMap contributors, available under the Open Database '
           'License (ODbL 1.0, https://opendatacommons.org/licenses/odbl/). This file is a derived database and stays under the ODbL.'
           + (' Lift capacity and year of construction: ' + REG['attribution'] if REG else ''))
OFF_MISSING = [o for o in OFFICIAL if o['name'] not in OFF.get('out_of_scope', []) and id(o) not in OFF_USED] if OFFICIAL else []
MISSING = OV.get('missing', []) + [{'name': o['name'], 'color': o['color'], **({'area': o['area']} if o.get('area') else {})} for o in OFF_MISSING]
data = {'resort': RID, 'license': LICENSE, 'pistes': pistes, 'lifts': LIFTS, 'missing': MISSING, 'fetched': FETCHED(),
        'liftLen': 'len: horizontal metres along the line, from the coordinates (as in Gudauri). lenSlope: the slope length from the lift registry, when there is one.',
        **({'registry': {'source': REG['attribution'], 'url': REG['url'], 'fetched': REG_FETCHED, 'fields': ['cap', 'year', 'lenSlope']}} if REG else {}),
        'bbox': [round(min(lats), 3), round(min(lons), 3), round(max(lats), 3), round(max(lons), 3)],
        'research': {'date': datetime.date.today().isoformat(),
                     'summary': 'נבנה אוטומטית מהמפה הפתוחה (OpenSkiMap) וממודל הגובה, בלי השלמות. נבדק מול מודל הגובה ומיקומי הרכבלים (tools/build-resort.py).'}}

json.dump(data, open(out / 'runs-and-lifts.json', 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))
json.dump(terrain, open(out / 'terrain.json', 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))
import gzip
GZ = len(gzip.compress((out / 'terrain.json').read_bytes(), 6)) / 1e6
MESHV = ((nx - 1) // terrain['dem']['mesh'] + 1) * ((ny - 1) // terrain['dem']['mesh'] + 1)
BUDGET = C.get('size', {'gz_mb': 2.5, 'mesh_vertices': 500000})
if GZ > BUDGET['gz_mb'] or MESHV > BUDGET['mesh_vertices']:
    sys.exit(f'terrain.json over the size budget: {GZ:.2f} MB compressed (budget {BUDGET["gz_mb"]}), '
             f'{MESHV} mesh vertices (budget {BUDGET["mesh_vertices"]}). A coarser dem_step_m, a bigger mesh_stride, or split the area.')

# ---------- report ----------
named = [p for p in pistes if p['named']]
lines = [f"# {C['name']}: בדיקת הנתונים", '',
         f"נבנה ב-{datetime.date.today().isoformat()} בפקודה `python3 tools/build-resort.py {args.config} --cache <dir>`. אל תערכו ביד; מריצים שוב.", '',
         f"- **מסלולים:** {len(pistes)} ({len(named)} עם מספר או שם), {sum(len(p['segs']) for p in pistes)} קטעים מהמפה הפתוחה, {round(sum(p['len'] for p in pistes)/1000,1)} ק״מ של קו.",
         f"- **רכבלים:** {len(LIFTS)} ({sum(1 for l in LIFTS if l['name'])} עם שם).",
         f"- **מודל גובה:** {nx}×{ny} נקודות כל {STEP} מ׳, מ-{round(H.min())} עד {round(H.max())} מ׳. המקור: " + (C.get('dem_note') or f"אריחי הגובה של AWS בזום {Z} (באוסטריה, המודל הלאומי של 10 מ׳).") ,
         f"- **גודל:** `terrain.json` {(out / 'terrain.json').stat().st_size / 1e6:.2f}MB, {GZ:.2f}MB דחוס (התקציב: {BUDGET['gz_mb']}MB); ברשת התלת-ממד {MESHV:,} קודקודים (התקציב: {BUDGET['mesh_vertices']:,}).",
         f"- **לא נכנסו:** {len(skipped)} קווים (מסלולי סקי קרוס־קאנטרי, הליכה ומזחלות, וקווים בלי דרגת קושי).", '',
         '## בדיקות לכל מסלול', '',
         'עלייה נגדית: סכום העליות לאורך הקו, מלמעלה למטה, במודל שהאתר מצייר (כלל הדיוק 4: עד כ-10 מ׳). מסלול שנכשל בבדיקה מקבל ודאות `low`, ולא מוסתר: הקו עצמו מהמפה הפתוחה.', '',
         '| מסלול | צבע | אורך (מ׳) | עלייה נגדית (מ׳) | הערות |', '|---|---|---|---|---|']
if SEL:
    sel_lines = ['## התחום', '', f"- **המלבן:** {SEL['bbox']} (דרום, מערב, צפון, מזרח). רק מסלולים ורכבלים שאמצעם בתוכו."]
    ea = SEL.get('exclude_areas', {})
    sel_lines += ['- **אזורי משנה שהוצאו** (המזהה ב-OpenSkiMap, והסיבה):'] + [f'  - `{i}`: {ea[i] if isinstance(ea, dict) else "(בלי סיבה בקובץ ההגדרות)"}' for i in ea]
    sel_lines += [f'- **פינות שהוצאו:** {x[:4]}' + (f': {x[4]}' if len(x) > 4 else '') for x in SEL.get('exclude_boxes', [])]
    i_ = lines.index('## בדיקות לכל מסלול'); lines[i_:i_] = sel_lines + ['']
lines += [f"| {k} | {c} | {L} | {cl} | {'; '.join(fl) or 'תקין'} |" for k, c, L, cl, fl in sorted(report, key=lambda r: [int(x) if x.isdigit() else x for x in __import__('re').split(r'(\d+)', r[0])])]
lines += ['', '## לא נכנסו', ''] + [f'- {nm or "(בלי שם)"}: {why}' for nm, why in skipped]
if ROUTES:
    lines += ['', '## סבבים במפה הפתוחה (לא מצוירים)', '',
              'קווים שהם רק הסבב עצמו (relation בלי דרך משלו) לא נכנסו, כי המסלולים שמתחתיהם כבר במפה. שמות הסבבים הוסרו משמות המסלולים.', '']
    lines += [f'- **{r}:** {len(route_runs[r])} מסלולים עליו: ' + ', '.join(sorted(route_runs[r])) for r in sorted(route_runs)]
    lines += [f'- קווים של הסבב בלבד שלא נכנסו: {sum(route_only.values())}']
if OFFICIAL:
    inscope = [o for o in OFFICIAL if o['name'] not in OFF.get('out_of_scope', [])]
    lines += ['', '## מול הרשימה הרשמית', '',
              f"- **ברשימות:** {len(inscope)} מסלולים בתחום, ועוד {len(OFFICIAL) - len(inscope)} מחוץ לתחום ({', '.join(OFF.get('out_of_scope', [])) or 'אין'}).",
              f"- **יש להם קו:** {len(inscope) - len(OFF_MISSING)}. **אין להם קו במפה הפתוחה** (ברשימת החסרים, לא מצוירים): {len(OFF_MISSING)}.",
              f"- **צבע שונה במפה הפתוחה, ותוקן לפי הרשמי:** " + (', '.join(f"{p['key']}" for p in pistes if any('ברשימה הרשמית' in c for c in p['research']['checks'])) or 'אין') + '.',
              f"- **מסלולים אצלנו שאינם ברשימה הרשמית** (הצבע מהמפה הפתוחה): " + (', '.join(p['key'] for p in pistes if p['named'] and 'לא נמצא ברשימה הרשמית' in p['research']['notes']) or 'אין') + '.',
              '', '### חסרים', ''] + [f"- {o['name']} ({o['color']}, {o.get('area') or ''})" for o in OFF_MISSING]
if REG:
    lines += ['', '## רכבלים מול המאגר הרשמי', '',
              f"המקור: {REG['attribution']} השכבה: {REG['url']}, הורדה ב-{REG_FETCHED}. התאמה לפי שני הקצוות, עד {REG.get('max_end_m', 120)} מ׳. נלקחים רק קיבולת, שנת בנייה ואורך משופע; הקו נשאר מהמפה הפתוחה.", '',
              f"- **הותאמו:** {sum(1 for l in LIFTS if 'reg' in l)} מתוך {len(LIFTS)}.",
              '- **אצלנו, בלי התאמה (נשארים בלי הנתונים):** ' + (', '.join(REG_UNMATCHED['ours']) or 'אין') + '.',
              '- **במאגר, בלי התאמה** (רוב הרשימה רכבלים של אתרים שכנים שבתוך התחום): ' + (', '.join(REG_UNMATCHED['registry']) or 'אין') + '.']
(ROOT / 'research/resorts' / f'{RID}-data.md').write_text('\n'.join(lines) + '\n', encoding='utf-8')
fail = sum(1 for r in report if r[4])
print(f"{len(pistes)} runs ({len(named)} named), {len(LIFTS)} lifts, dem {nx}x{ny}, {fail} runs with notes", file=sys.stderr)
for f_ in ('runs-and-lifts.json', 'terrain.json'): print(f_, (out / f_).stat().st_size, file=sys.stderr)

# ---------- the list of resorts: numbers for the picker and the resort's pass card (round 23), from each resort's own data ----------
def stats_of(r):
    base = ROOT / 'site' / r['dir']
    D = json.load(open(base / 'runs-and-lifts.json', encoding='utf-8')); T = json.load(open(base / 'terrain.json', encoding='utf-8'))
    d = T['dem']; A = np.frombuffer(base64.b64decode(d['b64']), dtype='<i2').reshape(d['ny'], d['nx']).astype(float)
    la0, lo0 = r['proj']['lat0'], r['proj']['lon0']; kx = 111320 * math.cos(math.radians(la0))
    sx, sy = (d['x1'] - d['x0']) / (d['nx'] - 1), (d['y1'] - d['y0']) / (d['ny'] - 1)
    def h(q):
        x, y = (q[1] - lo0) * kx, -(q[0] - la0) * 111320
        c = min(max((x - d['x0']) / sx, 0), d['nx'] - 1.0001); rr = min(max((y - d['y0']) / sy, 0), d['ny'] - 1.0001)
        c0, r0 = int(c), int(rr); fc, fr = c - c0, rr - r0
        return (A[r0, c0] * (1 - fc) + A[r0, c0 + 1] * fc) * (1 - fr) + (A[r0 + 1, c0] * (1 - fc) + A[r0 + 1, c0 + 1] * fc) * fr
    named = [p for p in D['pistes'] if p['named'] and p.get('kind') != 'ski-route']
    routes = [p for p in D['pistes'] if p.get('kind') == 'ski-route']
    ends = [q for l in D['lifts'] for q in (l['g'][0], l['g'][-1])] + [q for p in named for s in p['segs'] if not s['area'] for q in (s['g'][0], s['g'][-1])]
    hs = [h(q) for q in ends]
    by = collections.Counter()
    for p in named: by[p['color']] += p['len']
    return {'runs': len(named), 'lifts': len(D['lifts']), 'km': round(sum(p['len'] for p in named) / 1000), 'alt': [int(round(min(hs), -1)), int(round(max(hs), -1))],
            'byColor': {c: by[c] for c in ('green', 'blue', 'red', 'black') if by[c]}, **({'routes': len(routes)} if routes else {})}
RL = ROOT / 'site/data/resorts.json'
reg = json.load(open(RL, encoding='utf-8'))
for r in reg['resorts']:
    r['stats'] = stats_of(r)
RL.write_text(json.dumps(reg, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print('resorts.json:', {r['id']: r['stats'] for r in reg['resorts']}, file=sys.stderr)
