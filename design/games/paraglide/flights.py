#!/usr/bin/env python3
"""Flights for the paragliding game: each one is a real line over the map (through named peaks and places from
site/data/terrain.json), unrolled into a side view. Heights every 10 m come from the site's elevation model, and
thermals sit where the ground faces the sun most steeply (south-facing slopes, from the same model).
Writes flights.json next to this file. Run from the repo root."""
import json, math, base64, pathlib
import numpy as np
root = pathlib.Path(__file__).resolve().parents[3]
T = json.load(open(root/'site/data/terrain.json', encoding='utf-8'))
d = T['dem']; H = np.frombuffer(base64.b64decode(d['b64']), dtype='<i2').reshape(d['ny'], d['nx']).astype(float)
CX = (d['x1']-d['x0'])/(d['nx']-1); CY = (d['y1']-d['y0'])/(d['ny']-1)
def el(x, y):
    c = min(max((x-d['x0'])/CX, 0), d['nx']-1.001); r = min(max((y-d['y0'])/CY, 0), d['ny']-1.001); c0, r0 = int(c), int(r); fc, fr = c-c0, r-r0
    return float(H[r0,c0]*(1-fc)*(1-fr)+H[r0,c0+1]*fc*(1-fr)+H[r0+1,c0]*(1-fc)*fr+H[r0+1,c0+1]*fc*fr)
P = {p['n']: (p['x'], p['y']) for p in T['peaks']}; P.update({p['n']: (p['x'], p['y']) for p in T['env']['places']})
P['Kudebi top'] = (1025, 1443); P['Sadzele top'] = (674, 113); P['New Goodaura'] = (-208, 4298)  # lift top and bottom stations (runs-and-lifts.json)
P['Monument'] = (-3471, 2004)  # Russia–Georgia Friendship Monument, 42.4920N 44.4527E
NAMES = {'Kudebi top':'התחנה העליונה של Kudebi','Sadzele top':'התחנה העליונה של Sadzele','New Goodaura':'New Gudauri','Monument':'אנדרטת הידידות','Gudauri':'גודאורי','Kobi':'קובי','Chrdili':'Chrdili','Kudebi':'Kudebi','Sadzele':'Sadzele','Sadzele West':'Sadzele מערב','Bidara':'Bidara','Kobi Pass':'מעבר קובי'}
FLIGHTS = [
  {'n':'טיסת היכרות', 'route':['Kudebi top','New Goodaura'], 'photo':[], 'wind':0},
  {'n':'מעל Chrdili', 'route':['Sadzele top','Chrdili','Gudauri'], 'photo':[], 'wind':-1.5},
  {'n':'אנדרטת הידידות', 'route':['Kudebi top','Monument','Gudauri'], 'photo':['Monument'], 'wind':1},
  {'n':'אל קובי', 'route':['Kobi Pass','Kobi'], 'photo':[], 'wind':2},
  {'n':'המשימה הגדולה', 'route':['Bidara','Sadzele West','Kudebi','Chrdili','Gudauri'], 'photo':[], 'wind':-2},
]
out = []
for f in FLIGHTS:
    pts = [P[n] for n in f['route']]; prof = []; turns = []; s = 0.0
    for k in range(len(pts)-1):
        (ax, ay), (bx, by) = pts[k], pts[k+1]; L = math.hypot(bx-ax, by-ay); n = max(1, int(L//10))
        if k: turns.append({'s': round(s), 'n': NAMES[f['route'][k]], 'photo': f['route'][k] in f['photo']})
        for i in range(n):
            t = i/n; x = ax+(bx-ax)*t; y = ay+(by-ay)*t
            # sun on the slope: how steeply the ground falls toward the south here
            south = (el(x, y-15)-el(x, y+15))/30; steep = math.hypot((el(x+15, y)-el(x-15, y))/30, south)
            prof.append([round(el(x, y)), round(max(0.0, south)*steep*10, 2)])
        s += L
    bx, by = pts[-1]; prof.append([round(el(bx, by)), 0])
    sun = np.array([p[1] for p in prof]); sm = np.convolve(sun, np.ones(15)/15, mode='same')
    therm = []; order = np.argsort(-sm)
    for i in order:
        if sm[i] < 0.6 or len(therm) >= 2+len(prof)//180: break
        if i*10 < 250 or i*10 > len(prof)*10-500: continue
        if any(abs(i*10-t['s']) < 450 for t in therm): continue
        therm.append({'s': int(i*10), 'w': 70, 'v': round(float(min(4.2, 1.4+sm[i]*1.1)), 1)})
    therm.sort(key=lambda t: t['s'])
    # a launch thermal where the route climbs right after take-off (so the first ridge can be reached)
    if max(p[0] for p in prof[:150]) > prof[4][0]+50 and not any(t["s"] < 500 for t in therm): therm.insert(0, {"s": 160, "w": 70, "v": 2.8})
    out.append({'n': f['n'], 'from': NAMES[f['route'][0]], 'to': NAMES[f['route'][-1]], 'len': round(s), 'h': [p[0] for p in prof], 'turns': turns, 'therm': therm, 'wind': f['wind']})
    print(f['n'], round(s), prof[0][0], prof[-1][0], max(p[0] for p in prof), [(t['s'], t['v']) for t in therm], [t['n'] for t in turns])
json.dump(out, open(pathlib.Path(__file__).with_name('flights.json'), 'w'), ensure_ascii=False, separators=(',', ':'))
