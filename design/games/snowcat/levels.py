#!/usr/bin/env python3
"""Levels for the snowcat puzzle, from real runs: the shape follows each run's longest line (simplified into
rows down the run, bending sideways like the line does), and each row's steepness comes from the site's
elevation model along that line. Writes levels.json next to this file. Run from the repo root."""
import json, math, base64, pathlib
import numpy as np
root = pathlib.Path(__file__).resolve().parents[3]
T = json.load(open(root/'site/data/terrain.json', encoding='utf-8')); D = json.load(open(root/'site/data/runs-and-lifts.json', encoding='utf-8'))
d = T['dem']; H = np.frombuffer(base64.b64decode(d['b64']), dtype='<i2').reshape(d['ny'], d['nx']).astype(float)
CX = (d['x1']-d['x0'])/(d['nx']-1); CY = (d['y1']-d['y0'])/(d['ny']-1); KX = 111320*math.cos(math.radians(T['proj']['lat0']))
def el(x, y):
    c = min(max((x-d['x0'])/CX, 0), d['nx']-1.001); r = min(max((y-d['y0'])/CY, 0), d['ny']-1.001); c0, r0 = int(c), int(r); fc, fr = c-c0, r-r0
    return H[r0,c0]*(1-fc)*(1-fr)+H[r0,c0+1]*fc*(1-fr)+H[r0+1,c0]*(1-fc)*fr+H[r0+1,c0+1]*fc*fr
# eight runs, easy to hard; rows and width grow with the level
LV = [('Pirveli',10,4),('Tatra 1',11,5),('Kikilo 1',12,5),('Tatra 2',13,6),('Soliko 1',13,6),('Kudebi 1',14,7),('Sadzele 3',15,7),('Sadzele 1',16,8)]
out = []
for key, rows, width in LV:
    p = next((x for x in D['pistes'] if x['key'] == key), None)
    if not p: print('missing', key); continue
    g = max((s for s in p['segs'] if not s['area']), key=lambda s: len(s['g']))['g']
    pts = [((q[1]-T['proj']['lon0'])*KX, -(q[0]-T['proj']['lat0'])*111320) for q in g]
    if el(*pts[0]) < el(*pts[-1]): pts = pts[::-1]
    cum = [0.0]
    for a, b in zip(pts, pts[1:]): cum.append(cum[-1]+math.hypot(b[0]-a[0], b[1]-a[1]))
    def at(s):
        i = max(0, min(len(cum)-2, next((k for k in range(len(cum)-1) if cum[k+1] >= s), len(cum)-2))); t = (s-cum[i])/max(1e-6, cum[i+1]-cum[i])
        return (pts[i][0]+(pts[i+1][0]-pts[i][0])*t, pts[i][1]+(pts[i+1][1]-pts[i][1])*t)
    L = cum[-1]; A, B = pts[0], pts[-1]; ux, uy = (B[0]-A[0])/math.hypot(B[0]-A[0], B[1]-A[1]), (B[1]-A[1])/math.hypot(B[0]-A[0], B[1]-A[1])
    lat = []; slope = []
    for r in range(rows):
        s0 = L*r/rows; s1 = L*(r+1)/rows; m = at((s0+s1)/2)
        lat.append((m[0]-A[0])*(-uy)+(m[1]-A[1])*ux)  # sideways distance from the straight line top to bottom
        a, b = at(s0), at(s1); slope.append(round(math.degrees(math.atan2(el(*a)-el(*b), s1-s0)), 1))
    span = max(1.0, max(lat)-min(lat)); mid = (max(lat)+min(lat))/2
    shift = [round((v-mid)/span*min(4, rows/4)) for v in lat]  # bends, in cells
    out.append({'key': key, 'color': p['color'], 'len': round(L), 'top': round(el(*pts[0])), 'bot': round(el(*pts[-1])), 'rows': rows, 'width': width, 'shift': shift, 'slope': slope})
json.dump(out, open(pathlib.Path(__file__).with_name('levels.json'), 'w'), ensure_ascii=False, separators=(',', ':'))
for o in out: print(o['key'], o['color'], o['len'], o['shift'], o['slope'])
