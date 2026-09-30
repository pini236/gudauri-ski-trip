#!/usr/bin/env python3
"""Panorama of the real ridge as seen from New Gudauri, for the day-and-night home screen (N1).

A small ray caster over the site's own elevation model (site/data/terrain.json): lit by the real
January sun (with shadows cast by other mountains), rock on steep slopes, and haze by distance.
Vertical scale is exaggerated 3x so the ridge reads on a phone. One image per moment of the day:
design/round3/assets/pano-<key>.webp (780x800, transparent sky). Run from the repo root:
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
FOV = 64.0
W, HH = 1560, 1600                # rendered at 2x the 780x800 output, then downsampled (4x css px)
FOV0, FOV1 = AZC - FOV / 2, AZC + FOV / 2
V = (W / FOV) * 2.6               # px per degree vertically: 2.6x exaggeration
HZ = 300 * 4                      # the eye-level row, in css px x4

def bil(A, x, y):
    c = np.clip((x - d['x0']) / CX, 0, d['nx'] - 1.001); r = np.clip((y - d['y0']) / CY, 0, d['ny'] - 1.001)
    c0 = c.astype(int); r0 = r.astype(int); fc = c - c0; fr = r - r0
    return A[r0, c0] * (1 - fc) * (1 - fr) + A[r0, c0 + 1] * fc * (1 - fr) + A[r0 + 1, c0] * (1 - fc) * fr + A[r0 + 1, c0 + 1] * fc * fr

E0 = float(bil(H, np.array([VX]), np.array([VY]))[0]) + LIFT
dist = [40.0]
while dist[-1] < 16000: dist.append(dist[-1] + max(8.0, dist[-1] * 0.006))
dist = np.array(dist)
az = np.radians(FOV0 + (np.arange(W) + 0.5) / W * (FOV1 - FOV0))
PX = VX + np.outer(np.sin(az), dist); PY = VY - np.outer(np.cos(az), dist)     # (W, n)
inside = (PX > d['x0']) & (PX < d['x1']) & (PY > d['y0']) & (PY < d['y1'])
PH = bil(H, PX, PY) - dist**2 / (2 * 6371000) * 0.87
ang = np.degrees(np.arctan2(PH - E0, dist))
YS = np.where(inside, HZ - ang * V, np.inf)
RUN = np.minimum.accumulate(YS, axis=1)
rows = np.arange(HH) + 0.5
IDX = np.empty((W, HH), dtype=int)
for c in range(W):
    IDX[c] = np.searchsorted(-RUN[c], -rows, side='left')      # first sample whose running top is at or above the row
SKY = IDX >= len(dist)
IDXc = np.minimum(IDX, len(dist) - 1)
colsI = np.arange(W)[:, None]
SX, SY, SD = PX[colsI, IDXc], PY[colsI, IDXc], dist[IDXc]
slope = bil(SLOPE, SX, SY); lap = bil(LAP, SX, SY); nz = bil(1 / NRM, SX, SY)
rock = np.clip((slope - 38) / 10, 0, 1) * 0.8 + np.clip(-lap / 80 - 0.3, 0, 0.2)
rock = np.clip(rock, 0, 1)[..., None]
SNOW, ROCK = rgb('#F7FAFD'), rgb('#5F5B5A')
ALB = SNOW * (1 - rock) + ROCK * rock

def project(x, y):
    dx, dy = x - VX, y - VY; dd = math.hypot(dx, dy); a = math.degrees(math.atan2(dx, -dy))
    h = float(bil(H, np.array([x]), np.array([y]))[0]) - dd**2 / (2 * 6371000) * 0.87
    return round((a - FOV0) / (FOV1 - FOV0) * 390, 1), round((HZ - math.degrees(math.atan2(h - E0, dd)) * V) / 4, 1)
PROJ = {p['n']: project(p['x'], p['y']) for p in T['peaks'] if not p['pass']}
PROJ['New Gudauri'] = project(-452.0, 4441.0)
json.dump({'peaks': PROJ, 'camera': [VX, VY, round(E0)]}, open(root / 'design/round3/pano.json', 'w'), ensure_ascii=False)
print(PROJ)
if 'proj' in sys.argv: sys.exit()
KEYS = {   # key: (hour for the sun or None, sun colour, sun strength, ambient colour, ambient strength, haze colour, haze km)
    'night':   (None, '#8FA6D6', 0.28, '#2B3A60', 0.50, '#141E36', 9),
    'dawn':    (None, '#FFFFFF', 0.00, '#9A96BC', 0.72, '#D8A592', 12),
    'morning': (9.6,  '#FFEBD2', 0.72, '#7F9CC8', 0.50, '#CFDFEE', 11),
    'noon':    (12.5, '#FFFFFF', 0.88, '#86A6D0', 0.40, '#D3E3F2', 14),
    'gold':    (15.9, '#FFDDB0', 0.90, '#7888B8', 0.42, '#E6D2C0', 12),
    'sunset':  (17.35, '#FF8F78', 1.35, '#6C6896', 0.48, '#B98597', 10),
    'dusk':    (None, '#FFFFFF', 0.00, '#4C5584', 0.55, '#4F517E', 9),
}
out = root / 'design/round3/assets'; out.mkdir(parents=True, exist_ok=True)
for key, (hr, sc, sk, ac, ak, fc, fk) in KEYS.items():
    if hr is not None:
        alt, azs = sun(hr); L = light(max(alt, 0.6), azs)
    elif key == 'night':
        alt, azs = 38, 150; L = light(alt, azs)     # moonlight from the south-east
    else:
        L = np.zeros_like(H); alt = azs = None
    lam = bil(L, SX, SY)[..., None]
    amb = rgb(ac) * ak * (0.55 + 0.45 * nz[..., None])
    col = ALB * (amb + rgb(sc) * sk * lam)
    haze = (1 - np.exp(-SD / (fk * 1000)))[..., None] * 0.85
    col = col * (1 - haze) + rgb(fc) * haze
    img = np.clip(col, 0, 1) ** 1.08
    rgba = np.concatenate([img, (~SKY)[..., None].astype(float)], axis=2)
    im = Image.fromarray((rgba.transpose(1, 0, 2) * 255).astype(np.uint8), 'RGBA').resize((780, 800), Image.LANCZOS)
    im.save(out / f'pano-{key}.webp', quality=84, method=6)
    print(key, 'sun', None if alt is None else round(alt, 1), None if azs is None else round(azs), (out / f'pano-{key}.webp').stat().st_size)
