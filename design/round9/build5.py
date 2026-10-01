#!/usr/bin/env python3
"""Round 5 of the app icon (1.10.2026): five improved directions after Pini's notes on round 4
(sign with a more mountainous view, gondola, compass, pin). Writes icon-h1..h5.svg here, and, when given a folder,
the canvas boards (IconH1..H5.dc.html, SizesH.dc.html) into it. The ridge is the real skyline of Gudauri
(skyline.json, exaggerated) and the compass face is real contour lines from the site's height model.
Run from the repo root: python3 design/round9/build5.py [canvas-project-folder]"""
import json, base64, math, pathlib, random, sys
import numpy as np

here = pathlib.Path(__file__).resolve().parent
root = here.parent.parent
SKY = json.load(open(here / 'skyline.json'))

INK, BLUE, GOLD, SNOW, RED, GREEN = '#13233A', '#1F5FC4', '#F4B942', '#FFFFFF', '#D1342B', '#1B8A4C'


def f(v):
    return ('%.1f' % v).rstrip('0').rstrip('.')


def path(points, close=True):
    return 'M' + ' L'.join('%s %s' % (f(x), f(y)) for x, y in points) + (' Z' if close else '')


def snow_cap(x0, x1, y, top, rnd=1, drips=None):
    """A snow cap lying on a flat top edge at y (x0..x1): a wavy top (up to `top` px above) and drips below."""
    w = x1 - x0
    d = 'M%s %s' % (f(x0), f(y + 6))
    n = 5
    for i in range(n):
        a = x0 + w * i / n; b = x0 + w * (i + 1) / n
        h = top * (0.55 + 0.45 * math.sin(i * 2.1 + rnd))
        d += ' C %s %s, %s %s, %s %s' % (f(a + (b - a) * .1), f(y - h * .9), f(a + (b - a) * .55), f(y - h * 1.1), f(b), f(y - h * .35))
    d += ' L%s %s' % (f(x1), f(y + 6))
    # the bottom edge, with drips
    drips = drips or [(.14, 26), (.42, 14), (.7, 30), (.88, 12)]
    cur = x1
    for p, ln in sorted(drips, key=lambda t: -t[0]):
        cx = x0 + w * p
        d += ' L%s %s L%s %s L%s %s' % (f(cx + 9), f(y + 6), f(cx), f(y + 6 + ln), f(cx - 9), f(y + 6))
    d += ' L%s %s Z' % (f(x0), f(y + 6))
    return d


def smooth_closed(pts):
    """A closed Catmull-Rom spline through pts, as a cubic Bezier path."""
    n = len(pts)
    d = 'M%s %s' % (f(pts[0][0]), f(pts[0][1]))
    for i in range(n):
        p0 = pts[(i - 1) % n]; p1 = pts[i]; p2 = pts[(i + 1) % n]; p3 = pts[(i + 2) % n]
        c1 = (p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6)
        c2 = (p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6)
        d += ' C %s %s, %s %s, %s %s' % (f(c1[0]), f(c1[1]), f(c2[0]), f(c2[1]), f(p2[0]), f(p2[1]))
    return d + ' Z'


def ring_cap(cx, cy, r, a0, a1, depth, bump=12, lobes=3, seed=1):
    """Snow lying on the rim of a circle of radius r between angles a0..a1 (degrees clockwise from 12 o'clock):
    a soft bulge outside the rim and a scalloped lower edge reaching `depth` px inside it (rounded drips)."""
    pts = []
    n = 14
    for i in range(n + 1):
        t = i / n
        a = math.radians(a0 + (a1 - a0) * t)
        env = math.sin(math.pi * t) ** .75
        rr = r + 1 + bump * env * (0.82 + 0.18 * math.sin(t * 5 + seed))
        pts.append((cx + math.sin(a) * rr, cy - math.cos(a) * rr))
    m = lobes * 4
    for i in range(1, m):
        t = 1 - i / m
        a = math.radians(a0 + (a1 - a0) * t)
        env = math.sin(math.pi * t) ** .6
        lobe = (0.5 + 0.5 * math.cos((t * lobes + seed * .13) * 2 * math.pi)) ** 1.3
        dd = depth * (0.38 + 0.62 * lobe) * env
        pts.append((cx + math.sin(a) * (r - dd), cy - math.cos(a) * (r - dd)))
    return smooth_closed(pts)


def ridge_from_skyline(base, k, x_from=0, x_to=512):
    lo = min(v for _, v in SKY)
    a0, a1 = SKY[0][0], SKY[-1][0]
    pts = []
    for az, el in SKY:
        pts.append((x_from + (az - a0) / (a1 - a0) * (x_to - x_from), base - (el - lo) * k))
    return pts


def peak(apex, left, right, base, ridge_x, seed, jag=9, steps=9, lit=('#FFFFFF', '#DDE9F5'), shade=('#A4BCD8', '#6685B0'), gid='p'):
    """A faceted mountain: outline from the apex down each side with a little jitter, a ridge line that splits
    the lit face (left, facing the sun) from the shaded face (right). Returns (defs, svg)."""
    r = random.Random(seed)
    ax, ay = apex

    def side(x_end, sign):
        pts = []
        for i in range(1, steps + 1):
            t = i / steps
            x = ax + (x_end - ax) * t ** 0.92
            y = ay + (base - ay) * t
            if i < steps:
                x += r.uniform(-jag, jag) * (1 - t * .4) + sign * (1 - t) * 3
                y += r.uniform(-jag * .6, jag * .6)
            pts.append((x, y))
        return pts
    L = side(left, -1)
    R = side(right, 1)
    # ridge from the apex down to ridge_x at the base, with kinks
    ridge = [(ax, ay)]
    for i in range(1, 5):
        t = i / 5
        ridge.append((ax + (ridge_x - ax) * t + r.uniform(-jag, jag) * .8, ay + (base - ay) * t))
    ridge.append((ridge_x, base))
    lit_poly = [(ax, ay)] + L + [(ridge_x, base)] + ridge[-2:0:-1]
    shade_poly = [(ax, ay)] + ridge[1:] + R[::-1]
    defs = ('<linearGradient id="%sL" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="%s"></stop><stop offset="1" stop-color="%s"></stop></linearGradient>'
            '<linearGradient id="%sS" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="%s"></stop><stop offset="1" stop-color="%s"></stop></linearGradient>'
            % (gid, lit[0], lit[1], gid, shade[0], shade[1]))
    svg = ('<path d="%s" fill="url(#%sL)"></path><path d="%s" fill="url(#%sS)"></path>'
           % (path(lit_poly), gid, path(shade_poly), gid))
    return defs, svg, (L, R, ridge)


def spruce(x, y, h, col='#1E4A6B', cap=SNOW):
    """A small snowy spruce standing at (x, y) (its foot), h tall."""
    w = h * .5
    tiers = []
    for i, (fy, fw) in enumerate([(1.0, .55), (.72, .8), (.42, 1.0)]):
        top = y - h * fy; bot = y - h * (fy - .34)
        tiers.append('<path d="M%s %s L%s %s L%s %s Z" fill="%s"></path>' % (f(x), f(top), f(x + w * fw / 2), f(bot), f(x - w * fw / 2), f(bot), col))
        tiers.append('<path d="M%s %s L%s %s Q%s %s %s %s L%s %s Q%s %s %s %s Z" fill="%s"></path>' % (
            f(x), f(top), f(x + w * fw / 2 * .55), f(top + (bot - top) * .5), f(x + w * fw / 4), f(top + (bot - top) * .38), f(x), f(top + (bot - top) * .5),
            f(x - w * fw / 2 * .55), f(top + (bot - top) * .5), f(x - w * fw / 4), f(top + (bot - top) * .38), f(x), f(top + (bot - top) * .5), cap))
    trunk = '<rect x="%s" y="%s" width="%s" height="%s" fill="#13233A"></rect>' % (f(x - h * .035), f(y - h * .08), f(h * .07), f(h * .1))
    return trunk + ''.join(tiers)


def svg_wrap(body, defs=''):
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512" style="display: block">'
            '<defs>%s</defs>%s</svg>' % (defs, body))


# ---------------------------------------------------------------- H1: the sign in the mountains (Pini's choice, 1.10.2026)
def h1_parts():
    """The chosen icon in two layers: (defs, scene, sign), all in the 512 frame. Pini: no run on the mountain."""
    defs = ['<linearGradient id="h1sky" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#5588CB"></stop><stop offset=".5" stop-color="#A9CBEA"></stop><stop offset="1" stop-color="#FBE6CF"></stop></linearGradient>',
            '<linearGradient id="h1gnd" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFFFFF"></stop><stop offset="1" stop-color="#D5E2F0"></stop></linearGradient>',
            '<radialGradient id="h1sun" cx=".5" cy=".5" r=".5"><stop offset="0" stop-color="#FFE9A8" stop-opacity=".95"></stop><stop offset="1" stop-color="#FFE9A8" stop-opacity="0"></stop></radialGradient>']
    body = ['<rect width="512" height="512" fill="url(#h1sky)"></rect>',
            '<circle cx="436" cy="176" r="100" fill="url(#h1sun)"></circle><circle cx="436" cy="176" r="24" fill="%s"></circle>' % GOLD]
    # far range: the real skyline of Gudauri, exaggerated, in two tones
    far = ridge_from_skyline(base=300, k=26)
    body.append('<path d="%s" fill="#B1C9E1"></path>' % path(far + [(512, 400), (0, 400)]))
    far2 = ridge_from_skyline(base=322, k=18, x_from=-40, x_to=560)
    body.append('<path d="%s" fill="#97B3D3"></path>' % path(far2 + [(512, 400), (0, 400)]))
    lit = ('#FFFFFF', '#CFE0F2')
    d3, s3, _ = peak((44, 210), -80, 170, 372, 80, seed=3, jag=6, steps=6, lit=lit, gid='h1c')
    d2, s2, _ = peak((392, 104), 196, 620, 372, 404, seed=8, jag=9, steps=11, lit=lit, gid='h1b')
    d1, s1, _ = peak((176, 56), -60, 372, 372, 216, seed=5, jag=10, steps=12, lit=lit, gid='h1a')
    defs += [d3, d2, d1]
    body += [s3, s2, s1]
    # the forest band the sign stands in front of, and the snow at the front
    body.append('<path d="M0 344 Q70 326 130 338 T260 334 T390 340 T512 328 V450 H0 Z" fill="#285684"></path>')
    for i, x in enumerate([14, 44, 76, 108, 142, 174, 206, 236, 300, 340, 374, 408, 440, 474, 502]):
        h_ = 50 + (i * 11) % 22
        body.append(spruce(x, 366 + (i * 7) % 18, h_, col='#1E4A78'))
    body.append('<path d="M0 438 Q120 414 256 428 T512 418 V512 H0 Z" fill="url(#h1gnd)"></path>')
    # the sign, smaller, standing in the snow
    scene = body
    body = []
    body.append('<ellipse cx="288" cy="488" rx="70" ry="8" fill="%s" opacity=".16"></ellipse>' % INK)
    body.append('<rect x="272" y="432" width="32" height="56" fill="%s"></rect>' % INK)
    body.append('<path d="M110 398 L158 352 H420 V442 H158 Z" fill="%s"></path>' % BLUE)
    body.append('<path d="M110 398 L158 352 H420 V360 H164 L120 402 Z" fill="#fff" opacity=".18"></path>')
    body.append('<path d="M176 398 L222 366 V382 H356 V414 H222 V430 Z" fill="#fff"></path>')
    body.append('<path d="%s" fill="#fff"></path>' % snow_cap(132, 428, 354, 30, rnd=1.4, drips=[(.12, 22), (.4, 10), (.64, 28), (.9, 12)]))
    return defs, scene, body


def h1():
    defs, scene, sign = h1_parts()
    return svg_wrap(''.join(scene + sign), ''.join(defs))


# ---------------------------------------------------------------- H2: the gondola
def cabin(cx, top, s, glow=True):
    """A gondola cabin hanging from (cx, top - hanger). Scale s; width 150 s, height 126 s below `top`."""
    def X(v): return cx + v * s
    def Y(v): return top + v * s
    o = []
    # hanger arm and grip on the cable
    o.append('<rect x="%s" y="%s" width="%s" height="%s" fill="%s"></rect>' % (f(X(-5)), f(Y(-44)), f(10 * s), f(48 * s), INK))
    o.append('<rect x="%s" y="%s" width="%s" height="%s" rx="%s" fill="%s"></rect>' % (f(X(-22)), f(Y(-58)), f(44 * s), f(20 * s), f(9 * s), INK))
    # body
    o.append('<path d="M%s %s Q%s %s %s %s H%s Q%s %s %s %s V%s Q%s %s %s %s H%s Q%s %s %s %s Z" fill="%s"></path>' % (
        f(X(-78)), f(Y(30)), f(X(-78)), f(Y(0)), f(X(-48)), f(Y(0)), f(X(48)), f(X(78)), f(Y(0)), f(X(78)), f(Y(30)), f(Y(106)),
        f(X(78)), f(Y(126)), f(X(58)), f(Y(126)), f(X(-58)), f(X(-78)), f(Y(126)), f(X(-78)), f(Y(106)), BLUE))
    # glazing
    win = GOLD if glow else '#DCEBFA'
    o.append('<rect x="%s" y="%s" width="%s" height="%s" rx="%s" fill="%s"></rect>' % (f(X(-62)), f(Y(26)), f(124 * s), f(58 * s), f(12 * s), '#FFF3C9' if glow else win))
    o.append('<rect x="%s" y="%s" width="%s" height="%s" rx="%s" fill="%s" opacity=".55"></rect>' % (f(X(-62)), f(Y(26)), f(124 * s), f(58 * s), f(12 * s), GOLD))
    for dx in (-21, 21):
        o.append('<rect x="%s" y="%s" width="%s" height="%s" fill="%s"></rect>' % (f(X(dx - 3)), f(Y(26)), f(6 * s), f(58 * s), BLUE))
    # window reflections and skirt
    o.append('<path d="M%s %s L%s %s L%s %s L%s %s Z" fill="#fff" opacity=".45"></path>' % (f(X(-56)), f(Y(32)), f(X(-40)), f(Y(32)), f(X(-52)), f(Y(60)), f(X(-62)), f(Y(60))))
    o.append('<rect x="%s" y="%s" width="%s" height="%s" rx="%s" fill="%s"></rect>' % (f(X(-78)), f(Y(94)), f(156 * s), f(32 * s), f(14 * s), INK))
    o.append('<rect x="%s" y="%s" width="%s" height="%s" fill="#fff" opacity=".9"></rect>' % (f(X(-50)), f(Y(106)), f(100 * s), f(5 * s)))
    # the snow on the roof
    o.append('<path d="%s" fill="#fff"></path>' % snow_cap(X(-82), X(82), Y(10), 22 * s, rnd=2.2, drips=[(.18, 16 * s), (.5, 9 * s), (.82, 18 * s)]))
    return ''.join(o)


def h2():
    defs = ['<linearGradient id="h2sky" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#1B2F52"></stop><stop offset=".5" stop-color="#4466A0"></stop><stop offset="1" stop-color="#F3B58F"></stop></linearGradient>',
            '<linearGradient id="h2gnd" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#F6F9FC"></stop><stop offset="1" stop-color="#C5D6E8"></stop></linearGradient>']
    body = ['<rect width="512" height="512" fill="url(#h2sky)"></rect>']
    # stars
    for x, y, r in [(60, 40, 1.6), (150, 70, 1.2), (310, 34, 1.5), (440, 70, 1.8), (480, 120, 1.2), (214, 24, 1.1), (96, 120, 1.1)]:
        body.append('<circle cx="%s" cy="%s" r="%s" fill="#fff" opacity=".8"></circle>' % (x, y, r))
    # alpenglow peaks (warm lit faces, cool shaded faces)
    alp_l = ('#FBE3D0', '#F0B79A'); alp_s = ('#6E86B4', '#3F5B90')
    dA, sA, _ = peak((120, 214), -60, 280, 420, 150, seed=11, jag=9, steps=9, lit=alp_l, shade=alp_s, gid='h2a')
    dB, sB, _ = peak((380, 190), 230, 600, 420, 400, seed=14, jag=9, steps=9, lit=alp_l, shade=alp_s, gid='h2b')
    defs += [dA, dB]
    body += [sA, sB]
    body.append('<path d="M0 420 Q120 392 250 408 T512 396 V512 H0 Z" fill="url(#h2gnd)"></path>')
    for x, y, h in [(40, 424, 56), (84, 432, 44), (456, 420, 52), (492, 430, 40)]:
        body.append(spruce(x, y, h, col='#1E3F66'))
    # the cable, a pylon and two cabins
    cable = 'M-10 214 L522 62'
    # a lattice pylon whose head holds the cable (at x 66 the cable is at y 195)
    body.append('<path d="M44 440 L60 206 H74 L90 440 Z" fill="%s"></path>' % INK)
    body.append('<path d="M52 380 L82 330 M82 330 L55 282 M64 250 L88 410 M52 330 L78 380 M58 282 L78 250" stroke="#2F4C75" stroke-width="3" fill="none"></path>')
    body.append('<rect x="38" y="190" width="58" height="16" rx="5" fill="%s"></rect>' % INK)
    body.append('<circle cx="54" cy="199" r="7" fill="#31507C"></circle><circle cx="82" cy="199" r="7" fill="#31507C"></circle>')
    body.append('<path d="%s" stroke="%s" stroke-width="7" fill="none"></path>' % (cable, INK))
    body.append('<path d="M-10 211 L522 59" stroke="#fff" stroke-width="2" fill="none" opacity=".35"></path>')
    # far cabin first (smaller), then the big one
    body.append(cabin(412, 134, .48))
    body.append(cabin(252, 214, 1.0))
    # snow on the pylon's crossbar
    body.append('<path d="M36 196 Q46 178 62 184 Q76 172 98 192 L94 196 L86 204 L80 196 L66 196 L60 206 L54 196 Z" fill="#fff"></path>')
    return svg_wrap(''.join(body), ''.join(defs))


# ---------------------------------------------------------------- contours of the real mountain (for the compass)
def upsample(a, z):
    """Bilinear upsample by z, then a light binomial blur so the contour lines come out smooth (no scipy needed)."""
    ny, nx = a.shape
    xs = np.linspace(0, nx - 1, nx * z); ys = np.linspace(0, ny - 1, ny * z)
    rows = np.array([np.interp(xs, np.arange(nx), r) for r in a])
    big = np.array([np.interp(ys, np.arange(ny), c) for c in rows.T]).T
    k = np.array([1, 4, 6, 4, 1], dtype=float) / 16
    for _ in range(3):
        big = np.apply_along_axis(lambda v: np.convolve(np.pad(v, 2, mode='edge'), k, mode='valid'), 1, big)
        big = np.apply_along_axis(lambda v: np.convolve(np.pad(v, 2, mode='edge'), k, mode='valid'), 0, big)
    return big


def contour_paths(cx, cy, half, step=60, size=300):
    d = json.load(open(root / 'site/data/terrain.json', encoding='utf-8'))['dem']
    H = np.frombuffer(base64.b64decode(d['b64']), dtype='<i2').reshape(d['ny'], d['nx']).astype(np.float64)
    dx = (d['x1'] - d['x0']) / (d['nx'] - 1); dy = (d['y1'] - d['y0']) / (d['ny'] - 1)
    c0 = int((cx - half - d['x0']) / dx); c1 = int((cx + half - d['x0']) / dx)
    r0 = int((cy - half - d['y0']) / dy); r1 = int((cy + half - d['y0']) / dy)
    sub = H[r0:r1 + 1, c0:c1 + 1]
    z = 4
    big = upsample(sub, z)
    from matplotlib import pyplot as plt
    fig, ax = plt.subplots()
    levels = np.arange(math.floor(big.min() / step) * step, big.max() + step, step)
    cs = ax.contour(big, levels=levels)
    out = []
    n = big.shape[0]
    sc = size / max(big.shape)
    for lvl, segs in zip(cs.levels, cs.allsegs):
        for seg in segs:
            if len(seg) < 6: continue
            pts = [(p[0] * sc, p[1] * sc) for p in seg[::3]]
            out.append((lvl, path(pts, close=False)))
    plt.close(fig)
    return out, levels


def compass_face(cx, cy, r, gid, rot=0, ring=BLUE, face='#EAF2F8', line='#6F97C4', needle_scale=1.0):
    """The compass: a ring with ticks, a topographic face, a needle whose north tip is a snowy peak."""
    o = []
    cont, _ = contour_paths(1440, -274, 1500, step=50, size=2 * r * 1.0)
    o.append('<circle cx="%s" cy="%s" r="%s" fill="%s"></circle>' % (cx, cy, r, ring))
    inner = r * .78
    o.append('<circle cx="%s" cy="%s" r="%s" fill="%s"></circle>' % (cx, cy, inner, face))
    o.append('<clipPath id="%sclip"><circle cx="%s" cy="%s" r="%s"></circle></clipPath>' % (gid, cx, cy, inner))
    o.append('<g clip-path="url(#%sclip)"><g transform="translate(%s %s)">' % (gid, f(cx - r), f(cy - r)))
    for i, (lvl, p) in enumerate(cont):
        strong = int(round(lvl / 50)) % 4 == 0
        o.append('<path d="%s" fill="none" stroke="%s" stroke-width="%s" stroke-linejoin="round" opacity="%s"></path>' % (p, line, 2.8 if strong else 1.4, 1 if strong else .8))
    o.append('</g></g>')
    # ticks
    for i in range(72):
        a = math.radians(i * 5 + rot)
        major = i % 18 == 0; mid = i % 9 == 0; tenth = i % 2 == 0
        l = r * (.15 if major else .11 if mid else .075 if tenth else .045)
        x0 = cx + math.sin(a) * (r - 4); y0 = cy - math.cos(a) * (r - 4)
        x1 = cx + math.sin(a) * (r - 4 - l); y1 = cy - math.cos(a) * (r - 4 - l)
        o.append('<path d="M%s %s L%s %s" stroke="#fff" stroke-width="%s" stroke-linecap="round"></path>' % (f(x0), f(y0), f(x1), f(y1), 4 if major else 2.4 if mid else 1.6))
    return ''.join(o), inner


def needle(cx, cy, L, w, gold=GOLD, dark=INK, cap=True):
    """A needle: north half gold with a snow-capped tip (a peak), south half ink."""
    n = '<path d="M%s %s L%s %s L%s %s Z" fill="%s"></path>' % (cx, f(cy - L), f(cx + w), cy, f(cx - w), cy, gold)
    n += '<path d="M%s %s L%s %s L%s %s Z" fill="%s"></path>' % (cx, f(cy + L), f(cx + w), cy, f(cx - w), cy, dark)
    if cap:
        h = L * .42
        n += '<path d="M%s %s L%s %s L%s %s L%s %s L%s %s L%s %s Z" fill="#fff"></path>' % (
            cx, f(cy - L), f(cx + w * (h / L)), f(cy - L + h), f(cx + w * (h / L) * .35), f(cy - L + h * .72), f(cx), f(cy - L + h * 1.02),
            f(cx - w * (h / L) * .45), f(cy - L + h * .74), f(cx - w * (h / L)), f(cy - L + h))
    return n


def north_mark(cx, cy, r, w=26, depth=36):
    """A gold marker on the ring at 12 o'clock, pointing in."""
    return '<path d="M%s %s L%s %s L%s %s Z" fill="%s" stroke="%s" stroke-width="3" stroke-linejoin="round"></path>' % (
        f(cx - w / 2), f(cy - r + 3), f(cx + w / 2), f(cy - r + 3), cx, f(cy - r + depth), GOLD, INK)


def h3():
    defs = ['<linearGradient id="h3bg" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#1B2F52"></stop><stop offset="1" stop-color="#0D1522"></stop></linearGradient>']
    body = ['<rect width="512" height="512" fill="url(#h3bg)"></rect>']
    for r_, op in [(244, .07), (222, .11)]:
        body.append('<circle cx="256" cy="268" r="%s" fill="none" stroke="#fff" stroke-width="1.5" opacity="%s"></circle>' % (r_, op))
    face, inner = compass_face(256, 268, 192, 'h3')
    body.append(face)
    for ang in (90, 180, 270):
        a = math.radians(ang)
        x = 256 + math.sin(a) * 192; y = 268 - math.cos(a) * 192
        body.append('<circle cx="%s" cy="%s" r="9" fill="%s" stroke="#fff" stroke-width="3"></circle>' % (f(x), f(y), BLUE))
    body.append(north_mark(256, 268, 192))
    body.append(needle(256, 268, 128, 34))
    body.append('<circle cx="256" cy="268" r="14" fill="#fff" stroke="%s" stroke-width="5"></circle>' % INK)
    body.append('<path d="%s" fill="#fff"></path>' % ring_cap(256, 268, 192, -84, -10, 34, bump=18, lobes=3, seed=2))
    body.append('<path d="%s" fill="#fff"></path>' % ring_cap(256, 268, 192, 46, 80, 24, bump=12, lobes=2, seed=5))
    return svg_wrap(''.join(body), ''.join(defs))


# ---------------------------------------------------------------- H4: the pin with a mountain scene inside
def pin_path(cx, top, w, h):
    """Teardrop pin: circle of radius w around (cx, top + w), tip at (cx, top + h)."""
    r = w
    cy = top + r
    return 'M%s %s C %s %s, %s %s, %s %s A%s %s 0 1 1 %s %s C %s %s, %s %s, %s %s Z' % (
        f(cx), f(top + h), f(cx - r * .42), f(top + h - r * .85), f(cx - r), f(cy + r * .62), f(cx - r), f(cy), f(r), f(r), f(cx + r), f(cy),
        f(cx + r), f(cy + r * .62), f(cx + r * .42), f(top + h - r * .85), f(cx), f(top + h))


def h4():
    defs = ['<linearGradient id="h4sky" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#7FAEDF"></stop><stop offset="1" stop-color="#F6EBDD"></stop></linearGradient>',
            '<linearGradient id="h4win" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#4F86C8"></stop><stop offset="1" stop-color="#F8E6D0"></stop></linearGradient>',
            '<linearGradient id="h4gnd" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFFFFF"></stop><stop offset="1" stop-color="#D5E2F0"></stop></linearGradient>']
    body = ['<rect width="512" height="512" fill="url(#h4sky)"></rect>']
    far = ridge_from_skyline(base=414, k=18)
    body.append('<path d="%s" fill="#C0D3E7"></path>' % path(far + [(512, 520), (0, 520)]))
    body.append('<path d="M0 438 Q120 416 256 430 T512 420 V512 H0 Z" fill="url(#h4gnd)"></path>')
    body.append('<path d="M168 512 C 188 478, 300 490, 250 452" fill="none" stroke="#9DB6D2" stroke-width="5" stroke-linecap="round" opacity=".85"></path>')
    body.append('<path d="M190 512 C 212 482, 316 494, 262 456" fill="none" stroke="#9DB6D2" stroke-width="5" stroke-linecap="round" opacity=".85"></path>')
    body.append('<ellipse cx="256" cy="452" rx="92" ry="17" fill="none" stroke="%s" stroke-width="3" opacity=".25"></ellipse>' % INK)
    body.append('<ellipse cx="256" cy="452" rx="58" ry="10" fill="%s" opacity=".22"></ellipse>' % INK)
    P = pin_path(256, 52, 142, 394)
    body.append('<path d="%s" fill="%s"></path>' % (P, BLUE))
    body.append('<path d="%s" fill="none" stroke="%s" stroke-width="6"></path>' % (P, INK))
    # the window: a whole little mountain scene
    body.append('<clipPath id="h4clip"><circle cx="256" cy="194" r="100"></circle></clipPath>')
    body.append('<circle cx="256" cy="194" r="108" fill="#fff"></circle>')
    sc = ['<rect x="150" y="90" width="212" height="212" fill="url(#h4win)"></rect>',
          '<circle cx="330" cy="138" r="26" fill="#FFE9A8" opacity=".5"></circle><circle cx="330" cy="138" r="14" fill="%s"></circle>' % GOLD]
    far_w = ridge_from_skyline(base=262, k=14, x_from=140, x_to=372)
    sc.append('<path d="%s" fill="#B1C9E1"></path>' % path(far_w + [(372, 310), (140, 310)]))
    dQ, sQ, _ = peak((318, 138), 250, 410, 300, 330, seed=24, jag=3, steps=6, gid='h4b')
    dP, sP, _ = peak((222, 104), 110, 336, 300, 246, seed=21, jag=3.5, steps=8, gid='h4a')
    defs += [dP, dQ]
    sc += [sQ, sP]
    sc.append('<path d="M222 110 C 206 132, 228 146, 216 166 C 208 186, 238 192, 232 212 C 228 228, 208 232, 212 262" fill="none" stroke="%s" stroke-width="6" stroke-linecap="round"></path>' % RED)
    sc.append('<path d="M140 270 Q200 246 256 260 T372 254 V310 H140 Z" fill="#fff"></path>')
    body.append('<g clip-path="url(#h4clip)">%s</g>' % ''.join(sc))
    body.append('<circle cx="256" cy="194" r="100" fill="none" stroke="%s" stroke-width="5"></circle>' % INK)
    body.append('<path d="%s" fill="#fff"></path>' % ring_cap(256, 194, 142, -76, 28, 30, bump=20, lobes=4, seed=3))
    body.append('<path d="%s" fill="#fff"></path>' % ring_cap(256, 194, 142, 54, 84, 22, bump=12, lobes=2, seed=6))
    return svg_wrap(''.join(body), ''.join(defs))


# ---------------------------------------------------------------- H5: pin and compass together, at night
def h5():
    defs = ['<linearGradient id="h5sky" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#14264A"></stop><stop offset=".6" stop-color="#2C4A82"></stop><stop offset="1" stop-color="#E7A98B"></stop></linearGradient>',
            '<linearGradient id="h5gnd" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#E9F0F8"></stop><stop offset="1" stop-color="#A9BDD6"></stop></linearGradient>']
    body = ['<rect width="512" height="512" fill="url(#h5sky)"></rect>']
    for x, y, r in [(54, 54, 1.8), (118, 98, 1.2), (176, 36, 1.4), (332, 46, 1.6), (410, 92, 1.3), (462, 46, 1.9), (488, 150, 1.2), (30, 160, 1.2)]:
        body.append('<circle cx="%s" cy="%s" r="%s" fill="#fff" opacity=".85"></circle>' % (x, y, r))
    body.append('<circle cx="440" cy="104" r="22" fill="#F2F5FA"></circle><circle cx="451" cy="98" r="20" fill="#27437A"></circle>')
    cool_l = ('#DCE6F6', '#9FB4D8'); cool_s = ('#52709F', '#2A4572')
    dA, sA, _ = peak((86, 316), -80, 250, 452, 108, seed=31, jag=8, steps=7, lit=cool_l, shade=cool_s, gid='h5a')
    dB, sB, _ = peak((430, 296), 280, 600, 452, 440, seed=34, jag=8, steps=7, lit=cool_l, shade=cool_s, gid='h5b')
    defs += [dA, dB]
    body += [sA, sB]
    body.append('<path d="M0 442 Q120 420 256 434 T512 424 V512 H0 Z" fill="url(#h5gnd)"></path>')
    body.append('<ellipse cx="256" cy="452" rx="92" ry="17" fill="none" stroke="#fff" stroke-width="3" opacity=".3"></ellipse>')
    body.append('<ellipse cx="256" cy="452" rx="58" ry="10" fill="%s" opacity=".35"></ellipse>' % INK)
    P = pin_path(256, 44, 148, 408)
    body.append('<path d="%s" fill="%s"></path>' % (P, INK))
    body.append('<path d="%s" fill="none" stroke="#35568C" stroke-width="5"></path>' % P)
    face, inner = compass_face(256, 192, 120, 'h5')
    body.append(face)
    body.append(north_mark(256, 192, 120, w=20, depth=26))
    body.append(needle(256, 192, 80, 22))
    body.append('<circle cx="256" cy="192" r="9" fill="#fff" stroke="%s" stroke-width="4"></circle>' % INK)
    body.append('<path d="%s" fill="#fff"></path>' % ring_cap(256, 192, 148, -80, 10, 24, bump=20, lobes=4, seed=3))
    body.append('<path d="%s" fill="#fff"></path>' % ring_cap(256, 192, 148, 54, 84, 18, bump=12, lobes=2, seed=6))
    return svg_wrap(''.join(body), ''.join(defs))


BUILDERS = [('h1', h1, 'השלט בהרים', 'שלט עם נוף הררי: הרכס האמיתי של גודאורי, פסגות עם צד מואר וצד בצל, וצללית יער. נבחר (בלי השביל על ההר).'),
            ('h2', h2, 'הרכבל בשקיעה', 'תא גונדולה מוקדם, חלונות מוארים, עמוד ותא רחוק על הכבל, והרים באור שקיעה.'),
            ('h3', h3, 'המצפן עם הר', 'מצפן שפניו קווי גובה אמיתיים של ההר, והמחוג שלו פסגה מושלגת.'),
            ('h4', h4, 'סיכה עם חלון הרים', 'סיכה שבתוכה חלון על הר ומסלול אדום, עם עקבות סקי שמובילים אליה.'),
            ('h5', h5, 'סיכה ומצפן', 'המצפן והסיכה ביחד: סיכה כהה בלילה, ובה מצפן עם מחוג פסגה.')]


def dc_board(i, name, svg, title):
    return ('<!doctype html>\n<html lang="he">\n<head>\n<meta charset="utf-8">\n<title>לוגו %d: %s</title>\n<script src="./support.js"></script>\n</head>\n<body>\n<x-dc>\n<helmet>\n<style>\nbody{margin:0}\n</style>\n</helmet>\n'
            '<div style="width: 512px; height: 512px; overflow: hidden; background: #0D1522">\n%s\n</div>\n</x-dc>\n'
            '<script type="text/x-dc" data-dc-script data-props=\'{"$preview":{"width":512,"height":512}}\'>\nclass Component extends DCLogic {\nrenderVals() {\nreturn {};\n}\n}\n</script>\n</body>\n</html>\n') % (i, name, svg)


def sizes_board(names):
    cards = ''
    for i, (key, _, name, note) in enumerate(BUILDERS):
        n = 10 + i
        cards += ('<div style="background: #FFFFFF; border: 1px solid #CBD5DF; padding: 24px; display: flex; flex-direction: column; gap: 24px">\n'
                  '<div style="font-family: Karantina, \'Arial Narrow\', sans-serif; font-weight: 700; font-size: 36px; line-height: 1">%d. %s</div>\n'
                  '<div style="display: flex; flex-direction: row; align-items: center; justify-content: space-between; gap: 12px">\n'
                  '<sc-for list="{{ sizes }}" as="s" hint-placeholder-count="5">\n'
                  '<div style="{{ s.wrap }}"><div style="{{ s.inner }}"><dc-import name="Icon%s" hint-size="512px,512px"></dc-import></div></div>\n'
                  '</sc-for>\n</div>\n<div style="font-size: 15px; line-height: 1.4; color: #4B5A6F">%s</div>\n</div>\n') % (n, name, key.upper(), note)
    return ('<!doctype html>\n<html lang="he" dir="rtl">\n<head>\n<meta charset="utf-8">\n<title>סבב 5 בגדלים של טלפון</title>\n<script src="./support.js"></script>\n</head>\n<body>\n<x-dc>\n<helmet>\n'
            '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Karantina:wght@700&amp;family=IBM+Plex+Sans+Hebrew:wght@400;600&amp;display=swap">\n<style>\nbody{margin:0}\n</style>\n</helmet>\n'
            '<div style="width: 2880px; height: 340px; box-sizing: border-box; padding: 32px; background: #EEF2F5; display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 32px; font-family: \'IBM Plex Sans Hebrew\', system-ui, sans-serif; color: #13233A">\n'
            + cards + '</div>\n</x-dc>\n<script type="text/x-dc" data-dc-script data-props=\'{"$preview":{"width":2880,"height":340}}\'>\nclass Component extends DCLogic {\nrenderVals() {\n'
            "const spec = [[96, '50%'], [96, '30%'], [64, '50%'], [48, '50%'], [32, '50%']];\nreturn {\nsizes: spec.map(([box, radius]) => ({\n"
            "wrap: 'width: ' + box + 'px; height: ' + box + 'px; border-radius: ' + radius + '; overflow: hidden; position: relative; flex: none; border: 1px solid #CBD5DF',\n"
            "inner: 'position: absolute; left: 0; top: 0; width: 512px; height: 512px; transform: scale(' + (box / 512) + '); transform-origin: 0 0'\n}))\n};\n}\n}\n</script>\n</body>\n</html>\n")


if __name__ == '__main__':
    out = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else None
    for i, (key, fn, name, note) in enumerate(BUILDERS):
        svg = fn()
        (here / ('icon-%s.svg' % key)).write_text(svg, encoding='utf-8')
        if out:
            (out / ('Icon%s.dc.html' % key.upper())).write_text(dc_board(10 + i, name, svg, name), encoding='utf-8')
    if out:
        (out / 'SizesH.dc.html').write_text(sizes_board([b[2] for b in BUILDERS]), encoding='utf-8')
    print('ok', [b[0] for b in BUILDERS])
