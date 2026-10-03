#!/usr/bin/env python3
"""New design language: the real map (geometry from the site, data/mapkit.json) drawn in each language's colours,
as inline SVG snippets for the canvas boards: snip/<L>-map.svg (all runs, top view) and snip/<L>-run.svg (Tatra 2,
painted from the top down in the slope colours, the rest dimmed). No invented geometry: every line is the site's.
Run from the repo root: python3 design/language/mapsnip.py"""
import json, math, re, pathlib
here = pathlib.Path(__file__).resolve().parent
K = json.load(open(here / 'data/mapkit.json', encoding='utf-8'))
P = json.load(open(here / 'data/tatra2-profile.json', encoding='utf-8'))
L = json.load(open(here / 'languages.json', encoding='utf-8'))['langs']
out = here / 'snip'; out.mkdir(exist_ok=True)
X0, Y0, W, H = map(int, K['viewBox'].split())
PX = W / 390  # map units per CSS pixel at phone width

def pts_of(d):
    res = []
    for sub in re.split(r'(?=M)', d):
        n = [float(v) for v in re.findall(r'-?\d+(?:\.\d+)?', sub)]
        if len(n) >= 4: res.append(list(zip(n[0::2], n[1::2])))
    return res

def cut(line, a, b):
    """the part of a polyline between fractions a and b of its length"""
    seg = [math.dist(line[i], line[i + 1]) for i in range(len(line) - 1)]; tot = sum(seg)
    s0, s1, acc, pts = a * tot, b * tot, 0.0, []
    for i, l in enumerate(seg):
        p, q = line[i], line[i + 1]
        lo, hi = acc, acc + l
        if hi >= s0 and lo <= s1 and l > 0:
            t0 = max(0, (s0 - lo) / l); t1 = min(1, (s1 - lo) / l)
            A = (p[0] + (q[0] - p[0]) * t0, p[1] + (q[1] - p[1]) * t0); B = (p[0] + (q[0] - p[0]) * t1, p[1] + (q[1] - p[1]) * t1)
            if not pts: pts.append(A)
            pts.append(B)
        acc = hi
    return 'M' + 'L'.join(f'{round(x)} {round(y)}' for x, y in pts)

# Tatra 2: the longest line, oriented top (small y, north) to bottom
t2 = max(pts_of(K['tatra2']), key=lambda l: sum(math.dist(l[i], l[i + 1]) for i in range(len(l) - 1)))
if t2[0][1] > t2[-1][1]: t2 = t2[::-1]
SLOPE_OF = {'#3FA85F': 0, '#F2C13D': 1, '#E8833A': 2, '#D64541': 3}

def svg(code, run):
    s = L[code]; m = s['map']; c = s['light']; font = s['fonts']['map']
    if run:
        xs = [p[0] for p in t2]; ys = [p[1] for p in t2]
        pad = 260; bx0, bx1, by0, by1 = min(xs) - pad, max(xs) + pad, min(ys) - pad, max(ys) + pad
        h = max(by1 - by0, (bx1 - bx0) * 400 / 390); w = h * 390 / 400   # the frame is 390 x 400 at phone width
        cx, cy = (bx0 + bx1) / 2, (by0 + by1) / 2; vb = (round(cx - w / 2), round(cy - h / 2), round(w), round(h)); px = w / 390
    else:
        vb = (X0, Y0, W, H); px = PX
    sw = lambda v: round(v * px, 1)
    dim = ' opacity="0.28"' if run else ''
    o = [f'<svg viewBox="{" ".join(map(str, vb))}" width="100%" style="display: block" role="img" aria-label="{"Tatra 2 על המפה, צבוע לפי השיפוע" if run else "מפת המסלולים והרכבלים של גודאורי"}">',
         f'<rect x="{X0 - 2000}" y="{Y0 - 2000}" width="{W + 4000}" height="{H + 4000}" fill="{m["bg"]}"/>',
         f'<path d="{K["contour_50"]}" fill="none" stroke="{m["contourMinor"]}" stroke-width="{sw(.6)}"/>',
         f'<path d="{K["contour_100"]}" fill="none" stroke="{m["contour"]}" stroke-width="{sw(.8)}"/>',
         f'<path d="{K["contour_major"]}" fill="none" stroke="{m["contourMajor"]}" stroke-width="{sw(1.2)}"/>',
         f'<path d="{K["village"]}" fill="{m["village"]}"/>',
         f'<path d="{K["roads"]}" fill="none" stroke="{m["road"]}" stroke-width="{sw(1.1)}"/>',
         f'<g fill="none" stroke-linecap="round" stroke-linejoin="round"{dim}>']
    for col in ('green', 'blue', 'red', 'black'):
        o.append(f'<path d="{K["piste_" + col]}" stroke="{c[col]}" stroke-width="{sw(2.6)}"/>')
    o.append(f'<path d="{K["piste_unnamed"]}" stroke="{c["blue"]}" stroke-width="{sw(1.4)}" stroke-dasharray="{sw(4)} {sw(4)}"/>')
    o.append(f'<path d="{K["lifts"]}" stroke="{m["lift"]}" stroke-width="{sw(1.3)}"/></g>')
    o.append(f'<g fill="{m["station"]}" stroke="{m["lift"]}" stroke-width="{sw(1.2)}"{dim}>' + ''.join(f'<circle cx="{x}" cy="{y}" r="{sw(3)}"/>' for x, y in K['stations']) + '</g>')
    if run:
        o.append(f'<path d="{"M" + "L".join(f"{round(x)} {round(y)}" for x, y in t2)}" fill="none" stroke="{m["labelHalo"]}" stroke-width="{sw(9)}" stroke-linecap="round" stroke-linejoin="round"/>')
        for g in P['segments']:
            k = SLOPE_OF.get(g['fill'], 0)
            o.append(f'<path d="{cut(t2, g["from"], g["to"])}" fill="none" stroke="{s["slope"][k]}" stroke-width="{sw(6)}" stroke-linecap="round" stroke-linejoin="round"/>')
        a, b = t2[0], t2[-1]
        o.append(f'<circle cx="{round(a[0])}" cy="{round(a[1])}" r="{sw(6)}" fill="{c["surface"]}" stroke="{c["ink"]}" stroke-width="{sw(2)}"/>')
        o.append(f'<circle cx="{round(b[0])}" cy="{round(b[1])}" r="{sw(6)}" fill="{c["ink"]}" stroke="{c["surface"]}" stroke-width="{sw(2)}"/>')
    fs = sw(11)
    lab = [f'<g font-family="\'{font}\', sans-serif" font-weight="700" font-size="{fs}" text-anchor="middle" paint-order="stroke" stroke="{m["labelHalo"]}" stroke-width="{sw(3)}" stroke-linejoin="round">']
    for t in K['labels']:
        kind = t['kind']
        if run and t['t'] != 'Tatra 2': continue
        if kind.startswith('pg '):
            col = kind.split()[1]
            lab.append(f'<text x="{t["x"]}" y="{t["y"]}" fill="{c[col]}">{t["t"]}</text>')
        elif kind == 'lift lg':
            lab.append(f'<text x="{t["x"]}" y="{t["y"]}" fill="{c["muted"]}" font-weight="500" font-size="{sw(9.5)}">{t["t"]}</text>')
        elif kind == 'peak':
            lab.append(f'<text x="{t["x"]}" y="{t["y"]}" fill="{c["ink"]}" font-weight="500" font-size="{sw(9.5)}">▲ {t["t"]}</text>')
        elif kind == 'place-v' and t['t'] == 'גודאורי':
            lab.append(f'<text x="{t["x"]}" y="{t["y"]}" fill="{c["muted"]}" font-size="{sw(12)}">{t["t"]}</text>')
    lab.append('</g>')
    if run:
        lab[-1:] = []
        lab = lab[:1] + [f'<text x="{round(t2[0][0] + sw(12))}" y="{round(t2[0][1] + sw(4))}" text-anchor="start" fill="{c["ink"]}" font-size="{sw(13)}">Tatra 2</text>'] + ['</g>']
    o += lab + ['</svg>']
    return '\n'.join(o)

for code in L:
    (out / f'{code}-map.svg').write_text(svg(code, False), encoding='utf-8')
    (out / f'{code}-run.svg').write_text(svg(code, True), encoding='utf-8')
print(sorted(p.name for p in out.iterdir()), [len((out / f'{c}-map.svg').read_text()) for c in L])
