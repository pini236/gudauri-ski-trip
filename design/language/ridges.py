#!/usr/bin/env python3
"""New design language: the real ridges of Gudauri as flat layers, for the hero art in every direction.
Same viewpoint as the home-page panorama (design/round3/panorama.py, design/round9/skyline.py): the valley south
of New Gudauri, looking north. For each azimuth, the highest elevation angle inside three distance bands
(near, middle, far) gives three silhouettes, like the layers of a screen-printed poster. Peaks are placed by azimuth.
Writes ridges.json: SVG paths in a 1000x320 box (y down; the bottom edge is closed). Run from the repo root:
python3 design/language/ridges.py"""
import json, base64, math, pathlib
import numpy as np
root = pathlib.Path(__file__).resolve().parent.parent.parent
T = json.load(open(root / 'site/data/terrain.json', encoding='utf-8'))
d = T['dem']
H = np.frombuffer(base64.b64decode(d['b64']), dtype='<i2').reshape(d['ny'], d['nx']).astype(np.float64)
CX = (d['x1'] - d['x0']) / (d['nx'] - 1); CY = (d['y1'] - d['y0']) / (d['ny'] - 1)
VX, VY, LIFT = -700.0, 7300.0, 260.0
AZ0, AZ1, STEP = -32.0, 48.0, 0.25          # azimuths from north, degrees
W, HH = 1000, 320

def elev(x, y):
    c = (x - d['x0']) / CX; r = (y - d['y0']) / CY
    c0 = np.clip(np.floor(c).astype(int), 0, d['nx'] - 2); r0 = np.clip(np.floor(r).astype(int), 0, d['ny'] - 2)
    fc = np.clip(c - c0, 0, 1); fr = np.clip(r - r0, 0, 1)
    return (H[r0, c0] * (1 - fc) * (1 - fr) + H[r0, c0 + 1] * fc * (1 - fr) + H[r0 + 1, c0] * (1 - fc) * fr + H[r0 + 1, c0 + 1] * fc * fr)

eye = float(elev(np.array(VX), np.array(VY))) + LIFT
azs = np.arange(AZ0, AZ1 + 1e-9, STEP)
dist = np.arange(150, 11000, 30.0)
A = np.radians(azs)[:, None]
X = VX + np.sin(A) * dist[None, :]; Y = VY - np.cos(A) * dist[None, :]
inside = (X >= d['x0']) & (X <= d['x1']) & (Y >= d['y0']) & (Y <= d['y1'])
ang = np.degrees(np.arctan2(elev(np.clip(X, d['x0'], d['x1']), np.clip(Y, d['y0'], d['y1'])) - eye, dist[None, :]))
ang[~inside] = -90
bands = {'near': (150, 2600), 'mid': (2600, 6000), 'far': (6000, 11000)}
cum = {}
lo = None
top = ang.max()
low = -6.0
def sy(a): return HH - (a - low) / (top + 0.6 - low) * HH
out = {'box': [W, HH], 'note': 'real skyline from the valley south of New Gudauri, three distance bands'}
for name in ('far', 'mid', 'near'):
    a0, a1 = bands[name]
    m = (dist[None, :] >= a0) & (dist[None, :] < a1)
    best = np.where(m, ang, -90).max(axis=1)
    best = np.maximum(best, low)
    pts = [(round((az - AZ0) / (AZ1 - AZ0) * W, 1), round(sy(b), 1)) for az, b in zip(azs, best)]
    out[name] = 'M0 %d L' % HH + ' L'.join(f'{x} {y}' for x, y in pts) + f' L{W} {HH} Z'
# the whole skyline (max over all bands) as one line, for single-layer styles
best = np.maximum(ang.max(axis=1), low)
out['skyline'] = 'M' + ' L'.join(f'{round((az - AZ0) / (AZ1 - AZ0) * W, 1)} {round(sy(b), 1)}' for az, b in zip(azs, best))
out['skyline_fill'] = 'M0 %d L' % HH + out['skyline'][1:] + f' L{W} {HH} Z'
peaks = []
for p in T['peaks']:
    az = math.degrees(math.atan2(p['x'] - VX, -(p['y'] - VY)))
    if AZ0 <= az <= AZ1:
        i = int(round((az - AZ0) / STEP))
        peaks.append({'n': p['n'], 'ele': p['ele'], 'x': round((az - AZ0) / (AZ1 - AZ0) * W, 1), 'y': round(float(sy(ang[i].max())), 1)})
out['peaks'] = peaks
json.dump(out, open(pathlib.Path(__file__).parent / 'data' / 'ridges.json', 'w'))
print({k: len(v) for k, v in out.items() if isinstance(v, str)}, peaks)
