#!/usr/bin/env python3
"""Panorama of the real ridge as seen from New Gudauri, for the day-and-night home screen (N1).

A small ray caster over the site's own elevation model (site/data/terrain.json): lit by the real
January sun (with shadows cast by other mountains), rock on steep slopes, and haze by distance.
Vertical scale is exaggerated (2.6x on the phone, about 1.3x wide) so the ridge reads. One image per moment of the day,
in two widths, pano-<key>.webp (phone, 780x800) and pano-wide-<key>.webp (2880x800), transparent sky,
in design/round3/assets/ and copied to site/img/pano/ for the home page. Run from the repo root:
python3 design/round3/panorama.py"""
import json, base64, math, datetime, pathlib
import numpy as np
from PIL import Image
root = pathlib.Path(__file__).resolve().parent.parent.parent
T = json.load(open(root / 'site/data/terrain.json', encoding='utf-8'))
d = T['dem']
H = np.frombuffer(base64.b64decode(d['b64']), dtype='<i2').reshape(d['ny'], d['nx']).astype(np.float64)
CX = (d['x1'] - d['x0']) / (d['nx'] - 1); CY = (d['y1'] - d['y0']) / (d['ny'] - 1)
gy, gx = np.gradient(H, CY, CX)
NRM = np.sqrt(gx**2 + gy**2 + 1)
SLOPE = np.degrees(np.arctan(np.sqrt(gx**2 + gy**2)))
LAP = (np.roll(H, 1, 0) + np.roll(H, -1, 0) + np.roll(H, 1, 1) + np.roll(H, -1, 1) - 4 * H)   # < 0 on ridges
LAT, LON, TZ, DAY = 42.51, 44.495, 4.0, datetime.date(2027, 1, 12)

def sun(h):
    n = DAY.timetuple().tm_yday
    dec = math.radians(-23.44 * math.cos(math.radians(360 / 365 * (n + 10))))
    b = math.radians(360 / 364 * (n - 81)); eot = 9.87 * math.sin(2 * b) - 7.53 * math.cos(b) - 1.5 * math.sin(b)
    ha = math.radians(15 * (h + (LON - TZ * 15) / 15 + eot / 60 - 12)); phi = math.radians(LAT)
    alt = math.asin(math.sin(phi) * math.sin(dec) + math.cos(phi) * math.cos(dec) * math.cos(ha))
    az = math.atan2(math.sin(ha), math.cos(ha) * math.sin(phi) - math.tan(dec) * math.cos(phi))
    return math.degrees(alt), (math.degrees(az) + 180) % 360

def light(alt, az):
    """Lambert term and cast-shadow mask on the grid for a light at (alt, az)."""
    a, z = math.radians(alt), math.radians(az)
    sx, sy = math.sin(z), -math.cos(z)
    lx, ly, lz = math.cos(a) * sx, math.cos(a) * sy, math.sin(a)
    cosi = np.clip((-gx * lx - gy * ly + lz) / NRM, 0, 1)
    rows, cols = np.mgrid[0:d['ny'], 0:d['nx']]; lit = np.ones_like(H, dtype=bool)
    for k in range(1, 160):
        dist = k * CX
        c = cols + sx * dist / CX; r = rows + sy * dist / CY
        ok = (c >= 0) & (c <= d['nx'] - 1) & (r >= 0) & (r <= d['ny'] - 1)
        ci = np.clip(np.round(c).astype(int), 0, d['nx'] - 1); ri = np.clip(np.round(r).astype(int), 0, d['ny'] - 1)
        lit &= ~(ok & (H[ri, ci] > H + math.tan(a) * dist + 2))
    # soften the shadow edge a little
    litf = lit.astype(float); litf = (litf + np.roll(litf, 1, 0) + np.roll(litf, -1, 0) + np.roll(litf, 1, 1) + np.roll(litf, -1, 1)) / 5
    return cosi * litf

def rgb(h): return np.array([int(h[i:i + 2], 16) / 255 for i in (1, 3, 5)])

import sys
VX, VY = -700.0, 7300.0           # above the valley south of New Gudauri, looking north up the ski area
LIFT = 260.0                      # camera height above the ground there, in metres
AZC = 8.0                         # centre of view, degrees from north
VCSS = 15.84                      # css px per degree vertically, the same in every variant
HZC = 300                         # eye-level row in css px (image is 400 css px tall)
VARIANTS = {                      # name: (output width px, field of view in degrees). Output is 2x css.
    'phone': (780, 64.0),         # 2.6x vertical exaggeration
    'wide': (2880, 118.0),        # about 1.3x
}
KEYS = {   # key: (hour for the sun or None, sun colour, sun strength, ambient colour, ambient strength, haze colour, haze km)
    'night':   (None, '#8FA6D6', 0.28, '#2B3A60', 0.50, '#141E36', 9),
    'dawn':    (None, '#FFFFFF', 0.00, '#9A96BC', 0.72, '#D8A592', 12),
    'morning': (9.6,  '#FFEBD2', 0.72, '#7F9CC8', 0.50, '#CFDFEE', 11),
    'noon':    (12.5, '#FFFFFF', 0.88, '#86A6D0', 0.40, '#D3E3F2', 14),
    'gold':    (15.9, '#FFDDB0', 0.90, '#7888B8', 0.42, '#E6D2C0', 12),
    'sunset':  (17.35, '#FF8F78', 1.35, '#6C6896', 0.48, '#B98597', 10),
    'dusk':    (None, '#FFFFFF', 0.00, '#4C5584', 0.55, '#4F517E', 9),
}

def bil(A, x, y):
    c = np.clip((x - d['x0']) / CX, 0, d['nx'] - 1.001); r = np.clip((y - d['y0']) / CY, 0, d['ny'] - 1.001)
    c0 = c.astype(int); r0 = r.astype(int); fc = c - c0; fr = r - r0
    return A[r0, c0] * (1 - fc) * (1 - fr) + A[r0, c0 + 1] * fc * (1 - fr) + A[r0 + 1, c0] * (1 - fc) * fr + A[r0 + 1, c0 + 1] * fc * fr

E0 = float(bil(H, np.array([VX]), np.array([VY]))[0]) + LIFT
dist = [40.0]
while dist[-1] < 16000: dist.append(dist[-1] + max(8.0, dist[-1] * 0.006))
dist = np.array(dist)
SNOW, ROCK = rgb('#F7FAFD'), rgb('#5F5B5A')
LIGHTS = {}
for key, (hr, *_rest) in KEYS.items():
    if hr is not None: alt, azs = sun(hr); LIGHTS[key] = light(max(alt, 0.6), azs)
    elif key == 'night': LIGHTS[key] = light(38, 150)      # moonlight from the south-east
    else: LIGHTS[key] = np.zeros_like(H)

def render(name, OW, FOV):
    """Render one variant at 2x and downsample to OW x 800. Columns are done in chunks to keep memory low."""
    W, HH = OW * 2, 1600
    FOV0 = AZC - FOV / 2
    V = VCSS * 4; HZ = HZC * 4
    out = {k: np.zeros((W, HH, 4), dtype=np.float32) for k in KEYS}
    rows = np.arange(HH) + 0.5
    for c0 in range(0, W, 256):
        cs = np.arange(c0, min(W, c0 + 256))
        az = np.radians(FOV0 + (cs + 0.5) / W * FOV)
        PX = VX + np.outer(np.sin(az), dist); PY = VY - np.outer(np.cos(az), dist)
        inside = (PX > d['x0']) & (PX < d['x1']) & (PY > d['y0']) & (PY < d['y1'])
        PH = bil(H, PX, PY) - dist**2 / (2 * 6371000) * 0.87
        YS = np.where(inside, HZ - np.degrees(np.arctan2(PH - E0, dist)) * V, np.inf)
        RUN = np.minimum.accumulate(YS, axis=1)
        IDX = np.stack([np.searchsorted(-RUN[i], -rows, side='left') for i in range(len(cs))])
        SKY = IDX >= len(dist); IDXc = np.minimum(IDX, len(dist) - 1)
        ci = np.arange(len(cs))[:, None]
        SX, SY, SD = PX[ci, IDXc], PY[ci, IDXc], dist[IDXc]
        slope = bil(SLOPE, SX, SY); lap = bil(LAP, SX, SY); nz = bil(1 / NRM, SX, SY)[..., None]
        rock = np.clip(np.clip((slope - 38) / 10, 0, 1) * 0.8 + np.clip(-lap / 80 - 0.3, 0, 0.2), 0, 1)[..., None]
        ALB = SNOW * (1 - rock) + ROCK * rock
        for key, (hr, sc, sk, ac, ak, fc, fk) in KEYS.items():
            lam = bil(LIGHTS[key], SX, SY)[..., None]
            col = ALB * (rgb(ac) * ak * (0.55 + 0.45 * nz) + rgb(sc) * sk * lam)
            haze = (1 - np.exp(-SD / (fk * 1000)))[..., None] * 0.85
            col = np.clip(col * (1 - haze) + rgb(fc) * haze, 0, 1) ** 1.08
            out[key][cs] = np.concatenate([col, (~SKY)[..., None]], axis=2)
    dst = root / 'design/round3/assets'; dst.mkdir(parents=True, exist_ok=True)
    for key, arr in out.items():
        im = Image.fromarray((arr.transpose(1, 0, 2) * 255).astype(np.uint8), 'RGBA').resize((OW, 800), Image.LANCZOS)
        fn = dst / (f'pano-{key}.webp' if name == 'phone' else f'pano-{name}-{key}.webp')
        im.save(fn, quality=84 if name == 'phone' else 78, method=6)
        print(name, key, fn.stat().st_size)
    def project(x, y):
        dx, dy = x - VX, y - VY; dd = math.hypot(dx, dy); a = math.degrees(math.atan2(dx, -dy))
        h = float(bil(H, np.array([x]), np.array([y]))[0]) - dd**2 / (2 * 6371000) * 0.87
        return round((a - FOV0) / FOV, 4), round((HZC - math.degrees(math.atan2(h - E0, dd)) * VCSS) / 400, 4)
    pj = {p['n']: project(p['x'], p['y']) for p in T['peaks'] if not p['pass']}
    pj['New Gudauri'] = project(-452.0, 4441.0)
    return pj

PJ = {}
for name, (ow, fov) in VARIANTS.items():
    if len(sys.argv) > 1 and name not in sys.argv[1:]: continue
    PJ[name] = render(name, ow, fov)
# positions as fractions of the image: [x from the left, y from the top]
json.dump({'camera': [VX, VY, round(E0)], 'horizon': HZC / 400, **PJ}, open(root / 'design/round3/pano.json', 'w'), ensure_ascii=False, indent=1)
print(PJ)
