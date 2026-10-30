"""Static preview for round 30 (3D buildings): New Gudauri from real data only.

Terrain: site/data/terrain.json (40 m grid). Buildings: OpenStreetMap footprints
(ODbL), fetched with Overpass and passed as a JSON file. Height: building:levels x 3 m
or height when tagged (solid), otherwise an estimate of 3 levels (hatched, lighter).
Usage: python3 design/round30/render.py <overpass.json> <out.png> [lat lon px-per-m radius]
Not product code: a picture for the canvas, to judge feasibility.
"""
import base64, json, math, sys
import numpy as np
from PIL import Image, ImageDraw

T = json.load(open('site/data/terrain.json'))
d = T['dem']; H = np.frombuffer(base64.b64decode(d['b64']), dtype='<i2').reshape(d['ny'], d['nx']).astype(float)
sx = (d['x1'] - d['x0']) / (d['nx'] - 1); sy = (d['y1'] - d['y0']) / (d['ny'] - 1)
lat0, lon0 = T['proj']['lat0'], T['proj']['lon0']; kx = 111320 * math.cos(math.radians(lat0)); ky = 111320
P = lambda la, lo: ((lo - lon0) * kx, -(la - lat0) * ky)

def elev(x, y):
    c = min(max((x - d['x0']) / sx, 0), d['nx'] - 1.0001); r = min(max((y - d['y0']) / sy, 0), d['ny'] - 1.0001)
    c0, r0 = int(c), int(r); fc, fr = c - c0, r - r0
    return (H[r0, c0] * (1 - fc) + H[r0, c0 + 1] * fc) * (1 - fr) + (H[r0 + 1, c0] * (1 - fc) + H[r0 + 1, c0 + 1] * fc) * fr

# view: from the south-west, looking north-east over New Gudauri
# optional: <lat> <lon> <px per m> <radius m>; default is the whole of New Gudauri
V = [float(a) for a in sys.argv[3:7]] or [42.4665, 44.4870, 1.05, 900]
cx, cy = P(V[0], V[1]); W, Hh = 1600, 1000
yaw = math.radians(35); pitch = math.radians(32); scale = V[2]  # px per m
EX = 1.0  # no vertical exaggeration
def proj(x, y, z):
    dx, dy = x - cx, y - cy
    u = dx * math.cos(yaw) - dy * math.sin(yaw)          # screen right
    v = dx * math.sin(yaw) + dy * math.cos(yaw)          # depth (positive = toward viewer)
    zz = (z - 2050) * EX
    return (W / 2 + u * scale, Hh * 0.62 + (v * math.sin(pitch) - zz * math.cos(pitch)) * scale, -v)

img = Image.new('RGB', (W, Hh), (214, 226, 238)); dr = ImageDraw.Draw(img, 'RGBA')
sun = np.array([-0.5, -0.6, 0.62]); sun /= np.linalg.norm(sun)
polys = []
R = V[3]; st = 20 if R > 400 else 10
for gx in np.arange(cx - R, cx + R, st):
    for gy in np.arange(cy - R, cy + R, st):
        q = [(gx, gy), (gx + st, gy), (gx + st, gy + st), (gx, gy + st)]
        zs = [elev(*p) for p in q]
        n = np.array([-(zs[1] - zs[0]) / st, -(zs[3] - zs[0]) / st, 1.0]); n /= np.linalg.norm(n)
        l = 0.55 + 0.45 * max(0, float(n @ sun))
        slope = math.degrees(math.acos(n[2]))
        base = (236, 241, 247) if slope < 32 else (176, 170, 164)
        col = tuple(int(c * l) for c in base)
        pp = [proj(p[0], p[1], z) for p, z in zip(q, zs)]
        polys.append((sum(p[2] for p in pp) / 4, [(p[0], p[1]) for p in pp], col, None))

for r in T['env']['roads']:   # roads already in the site data (OpenStreetMap)
    pts = r[1]; w = {0: 7, 1: 6, 2: 5, 3: 4, 4: 3}.get(r[0], 3)
    for i in range(0, len(pts) - 2, 2):
        x1, y1, x2, y2 = pts[i], pts[i + 1], pts[i + 2], pts[i + 3]
        if abs(x1 - cx) > R or abs(y1 - cy) > R: continue
        a = proj(x1, y1, elev(x1, y1) + 0.5); b = proj(x2, y2, elev(x2, y2) + 0.5)
        polys.append(((a[2] + b[2]) / 2 - 0.5, [(a[0], a[1]), (b[0], b[1])], (120, 124, 132), ('line', max(1, w * scale * 0.6))))

known = est = 0
for e in json.load(open(sys.argv[1]))['elements']:
    g = e.get('geometry'); t = e.get('tags', {})
    if not g or e['type'] != 'way': continue
    ring = [P(p['lat'], p['lon']) for p in g]
    if any(abs(x - cx) > R or abs(y - cy) > R for x, y in ring): continue
    h = None
    try: h = float(str(t['height']).split()[0])
    except Exception:
        try: h = float(t['building:levels']) * 3 + 1
        except Exception: pass
    sure = h is not None
    if sure: known += 1
    else: est += 1; h = 3 * 3 + 1
    zb = min(elev(x, y) for x, y in ring) - 1; zt = max(elev(x, y) for x, y in ring) + h
    wall = (198, 106, 72) if sure else (214, 196, 186)
    roof = (120, 62, 52) if sure else (184, 176, 172)
    for (x1, y1), (x2, y2) in zip(ring, ring[1:]):
        nx_, ny_ = (y2 - y1), -(x2 - x1); ln = math.hypot(nx_, ny_) or 1
        l = 0.6 + 0.4 * max(0, (nx_ * sun[0] + ny_ * sun[1]) / ln)
        pts = [proj(x1, y1, zb), proj(x2, y2, zb), proj(x2, y2, zt), proj(x1, y1, zt)]
        polys.append((sum(p[2] for p in pts) / 4 - 0.1, [(p[0], p[1]) for p in pts], tuple(int(c * l) for c in wall), ('edge', None)))
    tp = [proj(x, y, zt) for x, y in ring]
    polys.append((sum(p[2] for p in tp) / len(tp) - 0.2, [(p[0], p[1]) for p in tp], roof, ('edge', None)))

polys.sort(key=lambda p: -p[0])
for _, pts, col, kind in polys:
    if kind and kind[0] == 'line': dr.line(pts, fill=col, width=int(kind[1]))
    elif kind: dr.polygon(pts, fill=col, outline=(60, 50, 50, 120))
    else: dr.polygon(pts, fill=col)
img.save(sys.argv[2])
print('buildings with a real height', known, 'estimated', est)
