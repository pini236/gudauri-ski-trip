#!/usr/bin/env python3
"""Sun and shade for the ski area on a given day, from the site's own terrain model.

Outputs (in design/round2/):
  assets/sun-HHMM.png   map renders for the sun-map design
  sun-stats.json        sunrise, sunset, and sun hours per piste
Run from the repo root with numpy and pillow installed.  The shading includes shadows cast by
other mountains (ray marching over the 40 m grid), but not trees, buildings or clouds."""
import json, base64, math, datetime
import numpy as np
from PIL import Image, ImageDraw

LAT, LON, TZ = 42.51, 44.495, 4.0
DAY = datetime.date(2027, 1, 12)
T = json.load(open('site/data/terrain.json', encoding='utf-8'))
D = json.load(open('site/data/runs-and-lifts.json', encoding='utf-8'))
d = T['dem']
H = np.frombuffer(base64.b64decode(d['b64']), dtype='<i2').reshape(d['ny'], d['nx']).astype(np.float64)
CELL = (d['x1'] - d['x0']) / (d['nx'] - 1)          # meters per cell, x
CELLY = (d['y1'] - d['y0']) / (d['ny'] - 1)
KX, KY = 111320 * math.cos(math.radians(T['proj']['lat0'])), 111320.0

def xy(lat, lon):
    return (lon - T['proj']['lon0']) * KX, -(lat - T['proj']['lat0']) * KY
def cell(x, y):
    return (x - d['x0']) / CELL, (y - d['y0']) / CELLY   # col, row

def sun(hour_local):
    n = DAY.timetuple().tm_yday
    dec = math.radians(-23.44 * math.cos(math.radians(360 / 365 * (n + 10))))
    b = math.radians(360 / 364 * (n - 81))
    eot = 9.87 * math.sin(2 * b) - 7.53 * math.cos(b) - 1.5 * math.sin(b)   # minutes
    solar = hour_local + (LON - TZ * 15) / 15 + eot / 60
    ha = math.radians(15 * (solar - 12))
    phi = math.radians(LAT)
    alt = math.asin(math.sin(phi) * math.sin(dec) + math.cos(phi) * math.cos(dec) * math.cos(ha))
    az = math.atan2(math.sin(ha), math.cos(ha) * math.sin(phi) - math.tan(dec) * math.cos(phi))
    az = (math.degrees(az) + 180) % 360      # from north, clockwise
    return math.degrees(alt), az, dec, eot

def sunrise_sunset():
    _, _, dec, eot = sun(12)
    h0 = math.acos(-math.tan(math.radians(LAT)) * math.tan(dec))
    half = math.degrees(h0) / 15
    noon = 12 - (LON - TZ * 15) / 15 - eot / 60
    return noon - half, noon + half

def fmt(h):
    return f"{int(h):02d}:{int(round((h % 1) * 60)) % 60:02d}"

gy, gx = np.gradient(H, CELLY, CELL)     # d/drow (southward), d/dcol (eastward)
def shade(hour):
    alt, az, _, _ = sun(hour)
    if alt <= 0: return np.zeros_like(H), np.zeros_like(H)
    a, z = math.radians(alt), math.radians(az)
    sx, sy = math.sin(z), -math.cos(z)               # unit vector toward the sun: east, south
    nx, ny, nz = -gx, -gy, np.ones_like(H)           # surface normal (east, south, up)
    ln = np.sqrt(nx**2 + ny**2 + nz**2)
    lx, ly, lz = math.cos(a) * sx, math.cos(a) * sy, math.sin(a)
    cosi = np.clip((nx * lx + ny * ly + nz * lz) / ln, 0, 1)
    rows, cols = np.mgrid[0:d['ny'], 0:d['nx']]
    lit = np.ones_like(H, dtype=bool)
    step = CELL
    for k in range(1, 100):
        dist = k * step
        c = cols + sx * dist / CELL; r = rows + sy * dist / CELLY
        ok = (c >= 0) & (c <= d['nx'] - 1) & (r >= 0) & (r <= d['ny'] - 1)
        ci, ri = np.clip(np.round(c).astype(int), 0, d['nx'] - 1), np.clip(np.round(r).astype(int), 0, d['ny'] - 1)
        blocked = ok & (H[ri, ci] > H + math.tan(a) * dist + 2)
        lit &= ~blocked
    sunny = lit & (cosi > 0.03)          # not blocked by another mountain AND facing the sun
    return cosi * lit, sunny.astype(float)

def piste_samples(p):
    out = []
    for s in p['segs']:
        g = s['g']
        for i in range(len(g) - 1):
            (x0, y0), (x1, y1) = xy(*g[i]), xy(*g[i + 1])
            n = max(1, int(math.hypot(x1 - x0, y1 - y0) / 20))
            for t in range(n): out.append((x0 + (x1 - x0) * t / n, y0 + (y1 - y0) * t / n))
    return out
def sample(arr, pts):
    v = []
    for x, y in pts:
        c, r = cell(x, y); c = min(max(c, 0), d['nx'] - 1.001); r = min(max(r, 0), d['ny'] - 1.001)
        c0, r0 = int(c), int(r); fc, fr = c - c0, r - r0
        v.append(arr[r0, c0] * (1 - fc) * (1 - fr) + arr[r0, c0 + 1] * fc * (1 - fr) + arr[r0 + 1, c0] * (1 - fc) * fr + arr[r0 + 1, c0 + 1] * fc * fr)
    return np.array(v)

# extent: the pistes and lifts, with margin
def meanlat(gs):
    t = n = 0
    for g in gs:
        for q in g: t += q[0]; n += 1
    return t / n
KOBI_LAT = 42.5115     # same split as the site: everything north of Kobi Pass is the Kobi side
MAIN_P = [p for p in D['pistes'] if meanlat([s['g'] for s in p['segs']]) <= KOBI_LAT]
MAIN_L = [l for l in D['lifts'] if meanlat([l['g']]) <= KOBI_LAT]
allpts = [xy(*q) for p in MAIN_P for s in p['segs'] for q in s['g']] + [xy(*q) for l in MAIN_L for q in l['g']]
xs, ys = zip(*allpts); M = 350
X0, X1, Y0, Y1 = min(xs) - M, max(xs) + M, min(ys) - M, max(ys) + M
W = 780; SC = W / (X1 - X0); Hh = int((Y1 - Y0) * SC)
print('extent m', round(X1 - X0), round(Y1 - Y0), 'image', W, Hh)

def upsample(arr):
    c0, r0 = cell(X0, Y0); c1, r1 = cell(X1, Y1)
    im = Image.fromarray(arr.astype(np.float32), mode='F').resize((W, Hh), Image.BICUBIC, box=(c0, r0, c1, r1))
    return np.clip(np.asarray(im), 0, 1)

PC = {'green': (27, 138, 76), 'blue': (31, 95, 196), 'red': (209, 52, 43), 'black': (19, 35, 58)}
def render(hour, path, overlay=True):
    cosi, lit = shade(hour)
    E = upsample(cosi); L = upsample(lit)
    _, az, _, _ = sun(hour)
    warm = np.array([255, 244, 220]); cool = np.array([120, 142, 178]); deep = np.array([68, 86, 122])
    v = E[..., None]
    img = np.where(L[..., None] > 0.5, cool * (1 - v) + warm * v, deep * (1 - 0.35 * (1 - v)) * 0.6 + cool * 0.4 * (1 - 0.35 * (1 - v)))
    # light relief so slopes read even in shadow
    relief = upsample((np.clip(gx * 0.5 - gy * 0.5, -1, 1) * 0.5 + 0.5))
    img = img * (0.86 + 0.28 * relief[..., None])
    im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), 'RGB')
    if overlay:
        dr = ImageDraw.Draw(im)
        def P(q):
            x, y = xy(*q); return ((x - X0) * SC, (y - Y0) * SC)
        for l in MAIN_L:
            pts = [P(q) for q in l['g']]; dr.line(pts, fill=(255, 255, 255), width=4); dr.line(pts, fill=(19, 35, 58), width=2)
        for p in MAIN_P:
            if not p.get('named') or p.get('kind') == 'ski-way': continue
            for s in p['segs']:
                pts = [P(q) for q in s['g']]
                dr.line(pts, fill=(255, 255, 255), width=8, joint='curve'); dr.line(pts, fill=PC[p['color']], width=5, joint='curve')
    im.save(path, optimize=True)

hours = [9, 12, 16.5]
def tag(h): return f'{int(h):02d}{int(round((h % 1) * 60)):02d}'
for h in hours: render(h, f'design/round2/assets/sun-{tag(h)}.png')
render(12, 'design/round2/assets/base-1200.png', overlay=False)

# stats: sun hours per piste (hourly samples 8..17, fraction of the line lit, with cast shadows)
rise, setv = sunrise_sunset()
grid = {}
for hh in [x / 2 for x in range(16, 36)]:      # 8:00 .. 17:30 every half hour
    a, _, _, _ = sun(hh)
    if a > 3: grid[hh] = shade(hh)
stats = {}
for p in D['pistes']:
    if not p.get('named'): continue
    pts = piste_samples(p); e = sample(H, pts)
    row = {'top': float(e.max()), 'bottom': float(e.min()), 'drop': float(e.max() - e.min()), 'len': p['len'], 'color': p['color']}
    for hh in hours:
        row[f'sun{tag(hh)}'] = float((sample(shade(hh)[1], pts) > 0.5).mean())
    lit_hours = sum(0.5 * float((sample(grid[hh][1], pts) > 0.5).mean()) for hh in grid)
    row['sunHours'] = lit_hours
    stats[p['key']] = row
alt12 = sun(12)[0]
json.dump({'date': str(DAY), 'sunrise': fmt(rise), 'sunset': fmt(setv), 'noonAltitude': alt12, 'extent': [X0, Y0, X1, Y1], 'image': [W, Hh], 'pistes': stats}, open('design/round2/sun-stats.json', 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
print('sunrise', fmt(rise), 'sunset', fmt(setv), 'noon alt', round(alt12, 1))
for k, r in sorted(stats.items(), key=lambda kv: -kv[1]['sunHours'])[:6]: print('most', k, round(r['sunHours'], 1))
for k, r in sorted(stats.items(), key=lambda kv: kv[1]['sunHours'])[:6]: print('least', k, round(r['sunHours'], 1))
