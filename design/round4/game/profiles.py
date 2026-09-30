#!/usr/bin/env python3
"""Real run profiles for the ski game prototype: height every 5 m along the longest line of each run,
top to bottom, from OpenStreetMap lines and the site's elevation model. Writes profiles.json next to this file.
Run from the repo root: python3 design/round4/game/profiles.py"""
import json, math, base64, pathlib
import numpy as np
root = pathlib.Path(__file__).resolve().parents[3]
T = json.load(open(root / 'site/data/terrain.json', encoding='utf-8')); D = json.load(open(root / 'site/data/runs-and-lifts.json', encoding='utf-8'))
d = T['dem']; H = np.frombuffer(base64.b64decode(d['b64']), dtype='<i2').reshape(d['ny'], d['nx']).astype(float)
CX = (d['x1'] - d['x0']) / (d['nx'] - 1); CY = (d['y1'] - d['y0']) / (d['ny'] - 1); KX = 111320 * math.cos(math.radians(42.51))
def el(x, y):
    c = min(max((x - d['x0']) / CX, 0), d['nx'] - 1.001); r = min(max((y - d['y0']) / CY, 0), d['ny'] - 1.001); c0, r0 = int(c), int(r); fc, fr = c - c0, r - r0
    return H[r0, c0]*(1-fc)*(1-fr)+H[r0, c0+1]*fc*(1-fr)+H[r0+1, c0]*(1-fc)*fr+H[r0+1, c0+1]*fc*fr
RUNS = ['Pirveli', 'Kikilo 1', 'Tatra 2', 'Sadzele 3', 'Sadzele 1']
out = []
for key in RUNS:
    p = next(x for x in D['pistes'] if x['key'] == key)
    g = max((s for s in p['segs'] if not s['area']), key=lambda s: len(s['g']))['g']
    pts = [((q[1] - 44.495) * KX, -(q[0] - 42.51) * 111320) for q in g]
    if el(*pts[0]) < el(*pts[-1]): pts = pts[::-1]
    cum = [0.0]
    for a, b in zip(pts, pts[1:]): cum.append(cum[-1] + math.hypot(b[0] - a[0], b[1] - a[1]))
    hs = []; s = 0.0; i = 0
    while s <= cum[-1]:
        while i < len(cum) - 2 and cum[i + 1] < s: i += 1
        t = (s - cum[i]) / max(1e-6, cum[i + 1] - cum[i]); x = pts[i][0] + (pts[i + 1][0] - pts[i][0]) * t; y = pts[i][1] + (pts[i + 1][1] - pts[i][1]) * t
        hs.append(round(el(x, y), 1)); s += 5
    out.append({'key': key, 'color': p['color'], 'len': round(cum[-1]), 'top': round(hs[0]), 'bot': round(hs[-1]), 'step': 5, 'h': hs, 'lift': (p.get('toLifts') or [''])[0]})
json.dump(out, open(pathlib.Path(__file__).with_name('profiles.json'), 'w'), separators=(',', ':'))
print([(o['key'], o['len'], o['top'] - o['bot'], o['lift']) for o in out])
