#!/usr/bin/env python3
"""The real skyline of Gudauri for the app icon (round 9): the highest ground along each direction, seen from
the valley south of New Gudauri looking north (the same viewpoint as the home-page panorama, design/round3/panorama.py).
Writes skyline.json: [[azimuth degrees from north, elevation angle degrees], ...]. Run from the repo root:
python3 design/round9/skyline.py"""
import json, base64, math, pathlib
import numpy as np
root = pathlib.Path(__file__).resolve().parent.parent.parent
d = json.load(open(root / 'site/data/terrain.json', encoding='utf-8'))['dem']
H = np.frombuffer(base64.b64decode(d['b64']), dtype='<i2').reshape(d['ny'], d['nx']).astype(np.float64)
CX = (d['x1'] - d['x0']) / (d['nx'] - 1); CY = (d['y1'] - d['y0']) / (d['ny'] - 1)
VX, VY, LIFT = -700.0, 7300.0, 260.0

def elev(x, y):
    c = (x - d['x0']) / CX; r = (y - d['y0']) / CY
    c0 = np.clip(np.floor(c).astype(int), 0, d['nx'] - 2); r0 = np.clip(np.floor(r).astype(int), 0, d['ny'] - 2)
    fc = np.clip(c - c0, 0, 1); fr = np.clip(r - r0, 0, 1)
    return (H[r0, c0] * (1 - fc) * (1 - fr) + H[r0, c0 + 1] * fc * (1 - fr) + H[r0 + 1, c0] * (1 - fc) * fr + H[r0 + 1, c0 + 1] * fc * fr)

eye = float(elev(np.array(VX), np.array(VY))) + LIFT
out = []
for az in np.arange(-40, 41, 1.0):
    a = math.radians(8 + az)
    best = -90.0
    for dist in np.arange(200, 9000, 40):
        x = VX + math.sin(a) * dist; y = VY - math.cos(a) * dist
        if not (d['x0'] <= x <= d['x1'] and d['y0'] <= y <= d['y1']): break
        best = max(best, math.degrees(math.atan2(float(elev(np.array(x), np.array(y))) - eye, dist)))
    out.append([8 + az, round(best, 2)])
json.dump(out, open(pathlib.Path(__file__).with_name('skyline.json'), 'w'))
print(len(out), 'points, angle', min(p[1] for p in out), 'to', max(p[1] for p in out))
