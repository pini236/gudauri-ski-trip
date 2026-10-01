#!/usr/bin/env python3
"""The real Georgian Military Road through the site's map area, for the driver game.
Joins the trunk-road pieces (class 0 in site/data/terrain.json env.roads, from OpenStreetMap) into the
longest connected line, orders it uphill (from the valley to Gudauri), resamples every 4 m and adds
heights from the site's elevation model. Writes road.json next to this file.
Run from the repo root: python3 design/games/driver/road.py"""
import json, math, base64, pathlib
import numpy as np
root = pathlib.Path(__file__).resolve().parents[3]
T = json.load(open(root / 'site/data/terrain.json', encoding='utf-8'))
d = T['dem']; H = np.frombuffer(base64.b64decode(d['b64']), dtype='<i2').reshape(d['ny'], d['nx']).astype(float)
CX = (d['x1'] - d['x0']) / (d['nx'] - 1); CY = (d['y1'] - d['y0']) / (d['ny'] - 1)
def el(x, y):
    c = min(max((x - d['x0']) / CX, 0), d['nx'] - 1.001); r = min(max((y - d['y0']) / CY, 0), d['ny'] - 1.001); c0, r0 = int(c), int(r); fc, fr = c - c0, r - r0
    return H[r0, c0]*(1-fc)*(1-fr)+H[r0, c0+1]*fc*(1-fr)+H[r0+1, c0]*(1-fc)*fr+H[r0+1, c0+1]*fc*fr
segs = [[(f[i], f[i+1]) for i in range(0, len(f), 2)] for k, f in T['env']['roads'] if k == 0]
key = lambda p: (round(p[0]), round(p[1]))
# greedy chaining: join pieces whose ends touch (within 3 m)
def near(a, b): return math.hypot(a[0]-b[0], a[1]-b[1]) < 3
chains = []
pool = segs[:]
while pool:
    c = pool.pop(0); grew = True
    while grew:
        grew = False
        for s in pool:
            if near(c[-1], s[0]): c = c + s[1:]
            elif near(c[-1], s[-1]): c = c + s[::-1][1:]
            elif near(c[0], s[-1]): c = s + c[1:]
            elif near(c[0], s[0]): c = s[::-1] + c[1:]
            else: continue
            pool.remove(s); grew = True; break
    chains.append(c)
def length(c): return sum(math.hypot(c[i+1][0]-c[i][0], c[i+1][1]-c[i][1]) for i in range(len(c)-1))
chains.sort(key=length, reverse=True)
line = chains[0]
if el(*line[0]) > el(*line[-1]): line = line[::-1]
cum = [0.0]
for a, b in zip(line, line[1:]): cum.append(cum[-1] + math.hypot(b[0]-a[0], b[1]-a[1]))
pts = []; s = 0.0; i = 0
while s <= cum[-1]:
    while i < len(cum) - 2 and cum[i+1] < s: i += 1
    t = (s - cum[i]) / max(1e-6, cum[i+1] - cum[i]); x = line[i][0] + (line[i+1][0]-line[i][0])*t; y = line[i][1] + (line[i+1][1]-line[i][1])*t
    pts.append([round(x, 1), round(y, 1), round(el(x, y), 1)]); s += 4
out = {'src': 'OpenStreetMap trunk road in site/data/terrain.json, heights from the site elevation model', 'step': 4, 'len': round(cum[-1]), 'pts': pts,
       'chains': [round(length(c)) for c in chains[:6]]}
json.dump(out, open(pathlib.Path(__file__).with_name('road.json'), 'w'), separators=(',', ':'))
print(out['len'], out['chains'], pts[0], pts[-1])
