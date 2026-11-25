#!/usr/bin/env python3
"""
The far ground round the live mountain, schema 2 (the architect's light option, docs/ARCHITECTURE.md, 25.11.2026; the
designer's handoff, design/handoff/2026-11-25-redesign-ring-detail/README.md).

Past the edge of the height model (site/data/terrain.json, about 15 km square, 40 m cells) two grids of real heights,
from the same source and in the same projection as terrain.json: Terrain Tiles on AWS (terrarium PNG, zoom 11, about
56 m a pixel here; mostly SRTM; the credits already name them), the site's projection (x east, y south, metres round
lat0/lon0).

  inner  300 m cells out to 22.2 km each way (Kazbek, 21 km north, inside). A ridge vertex (its height over the mean
         of its four neighbours by more than RIDGE_M) takes the highest real sample in its own cell, so the sky line is
         not shaved by the grid; every other vertex is the bilinear sample. No height is invented.
  outer  1,400 m cells out to 42 km each way, bilinear samples only.
  ringrelief.webp  the inner ring's shading at 90 m: R, G = 0.5 + slope east, south / 3.2 (as relief.webp); B = the
         occlusion, (occ - 0.7) / 0.6 with occ = 1 + clamp(c1/60)*0.09 + clamp(c2/300)*0.11, c1, c2 the height over its
         400 m and 3,000 m mean. Row 0 north, over the inner ring's box.

Lone spikes in the source (a sample over all eight neighbours by more than 60 m) are replaced by their neighbours'
median before anything is sampled.

Run by hand only (the build never downloads anything):  python3 tools/build-ring.py   (numpy, Pillow)
It writes app/android/app/src/main/assets/redesign/live/ring.json and ringrelief.webp. The app's unit test (RingTest)
fails over 64 KB, 48,000 triangles or an 80 KB relief map.
"""
import base64, datetime, json, math, os, sys, tempfile, urllib.request, zlib

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LIVE = os.path.join(ROOT, 'app/android/app/src/main/assets/redesign/live')
OUT = os.path.join(LIVE, 'ring.json')
TEX = os.path.join(LIVE, 'ringrelief.webp')
TERRAIN = os.path.join(ROOT, 'site/data/terrain.json')
IN_HALF, IN_CELL = 22200.0, 300.0       # 148 cells, 149 x 149 vertices
OUT_HALF, OUT_CELL = 42000.0, 1400.0    # 60 cells, 61 x 61 vertices
RELIEF_PX = 493                         # 90 m texels over the inner box
RIDGE_M = 4.0
Z = 11
SRC = 'https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png'
CACHE = os.path.join(tempfile.gettempdir(), 'gudi-ring-tiles')

proj = json.load(open(TERRAIN))['proj']
lat0, lon0 = proj['lat0'], proj['lon0']
KX, KY = 111320 * math.cos(math.radians(lat0)), 111320   # as Geo.kt and build-resort.py


def pix(x, y):
    """the zoom-Z tile pixel (float, global) of the site's x east, y south"""
    lat = lat0 - np.asarray(y, dtype=np.float64) / KY; lon = lon0 + np.asarray(x, dtype=np.float64) / KX
    n = 2 ** Z * 256; la = np.radians(lat)
    return (lon + 180) / 360 * n - .5, (1 - np.log(np.tan(la) + 1 / np.cos(la)) / math.pi) / 2 * n - .5


def mosaic(half):
    """the source tiles over [-half, half] each way, spikes cleaned: (array, px0, py0)"""
    fx, fy = pix(np.array([-half - 500, half + 500]), np.array([half + 500, -half - 500]))
    x0, x1 = int(math.floor(fx.min())) - 2, int(math.ceil(fx.max())) + 3
    y0, y1 = int(math.floor(fy.min())) - 2, int(math.ceil(fy.max())) + 3
    big = np.zeros((y1 - y0, x1 - x0)); n = 0
    os.makedirs(CACHE, exist_ok=True)
    for ty in range(y0 // 256, (y1 - 1) // 256 + 1):
        for tx in range(x0 // 256, (x1 - 1) // 256 + 1):
            f = os.path.join(CACHE, f'{Z}-{tx}-{ty}.png')
            if not os.path.exists(f):
                with urllib.request.urlopen(SRC.format(z=Z, x=tx, y=ty), timeout=60) as r, open(f, 'wb') as o: o.write(r.read())
            a = np.asarray(Image.open(f).convert('RGB'), dtype=np.float64); n += 1
            t = a[..., 0] * 256 + a[..., 1] + a[..., 2] / 256 - 32768
            ax, ay = tx * 256, ty * 256
            sx0, sy0, sx1, sy1 = max(x0, ax), max(y0, ay), min(x1, ax + 256), min(y1, ay + 256)
            big[sy0 - y0:sy1 - y0, sx0 - x0:sx1 - x0] = t[sy0 - ay:sy1 - ay, sx0 - ax:sx1 - ax]
    nb = np.stack([np.roll(np.roll(big, i, 0), j, 1) for i in (-1, 0, 1) for j in (-1, 0, 1) if i or j])
    big = np.where(big - nb.max(0) > 60, np.median(nb, 0), big)
    print('tiles', n)
    return big, x0, y0


def sampler(m):
    big, x0, y0 = m
    def at(x, y):
        px, py = pix(x, y); px = px - x0; py = py - y0
        c0 = np.floor(px).astype(int); r0 = np.floor(py).astype(int); a = px - c0; b = py - r0
        return (big[r0, c0] * (1 - a) + big[r0, c0 + 1] * a) * (1 - b) + (big[r0 + 1, c0] * (1 - a) + big[r0 + 1, c0 + 1] * a) * b
    return at


def grid(at, half, cell, ridge):
    n = int(round(2 * half / cell)) + 1
    s = -half + np.arange(n) * cell
    X, Y = np.meshgrid(s, s)                    # row j is y = -half + j*cell (y south: row 0 north)
    h = at(X, Y)
    if ridge:
        lap = h - (np.roll(h, 1, 0) + np.roll(h, -1, 0) + np.roll(h, 1, 1) + np.roll(h, -1, 1)) / 4
        lap[0, :] = lap[-1, :] = lap[:, 0] = lap[:, -1] = 0
        step = 56.0; k = int(cell / 2 / step)
        hi = np.full_like(h, -1e9)
        for dy in range(-k, k + 1):
            for dx in range(-k, k + 1): hi = np.maximum(hi, at(X + dx * step, Y + dy * step))
        h = np.where(lap > RIDGE_M, hi, h)
    return n, np.round(h).astype(np.int16)


def pack(h):
    d = h.astype(np.int32).copy(); d[:, 1:] -= h[:, :-1].astype(np.int32)
    return base64.b64encode(zlib.compress(d.astype('<i2').tobytes(), 9)).decode()


def blur(a, metres, g):
    k = max(1, int(metres / g)); hh, w = a.shape
    im = Image.fromarray(a.astype(np.float32), mode='F').resize((max(2, w // k), max(2, hh // k)), Image.BOX).resize((w, hh), Image.BICUBIC)
    return np.asarray(im, dtype=np.float64)


def q8(a, seed):
    a = np.clip(a, 0, 1) * 255 + np.random.default_rng(90 + seed).random(a.shape) - .5
    return np.clip(np.round(a), 0, 255).astype(np.uint8)


def main():
    at = sampler(mosaic(OUT_HALF))
    ni, hi = grid(at, IN_HALF, IN_CELL, True)
    no, ho = grid(at, OUT_HALF, OUT_CELL, False)
    # the relief map: texel centres over the inner box, row 0 north
    px = 2 * IN_HALF / RELIEF_PX
    s = -IN_HALF + (np.arange(RELIEF_PX) + .5) * px
    X, Y = np.meshgrid(s, s); h = at(X, Y)
    dz, dx = np.gradient(h, px, px)            # rows go south: d/drow = d/dsouth
    c1 = h - blur(h, 400, px); c2 = h - blur(h, 3000, px)
    occ = 1 + np.clip(c1 / 60, -1, 1) * 0.09 + np.clip(c2 / 300, -1, 1) * 0.11
    Image.fromarray(np.dstack([q8(.5 + dx / 3.2, 1), q8(.5 + dz / 3.2, 2), q8((occ - .7) / .6, 3)])).save(TEX, quality=90, method=6)
    doc = {
        'schema': 2,
        'source': 'Terrain Tiles on AWS (terrarium, zoom %d, about 56 m here), mostly SRTM' % Z,
        'url': SRC,
        'fetched': datetime.date.today().isoformat(),
        'proj': {'lat0': lat0, 'lon0': lon0},
        'encoding': 'zlib of little-endian int16 metres, row by row (y south, row 0 north), each value after the first '
                    'in a row the difference from the one before it',
        'spikes': 'a source sample over all eight neighbours by more than 60 m is their median',
        'inner': {'cell': IN_CELL, 'x0': -IN_HALF, 'y0': -IN_HALF, 'nx': ni, 'ny': ni,
                  'ridge': 'a vertex over the mean of its four neighbours by more than %g m takes the highest sample '
                           '(every 56 m) in its own cell' % RIDGE_M, 'h': pack(hi)},
        'outer': {'cell': OUT_CELL, 'x0': -OUT_HALF, 'y0': -OUT_HALF, 'nx': no, 'ny': no, 'h': pack(ho)},
        'relief': {'file': 'ringrelief.webp', 'x0': -IN_HALF, 'y0': -IN_HALF, 'x1': IN_HALF, 'y1': IN_HALF, 'px': RELIEF_PX,
                   'encoding': 'R, G = 0.5 + slope east, south / 3.2; B = (occlusion - 0.7) / 0.6; row 0 north'},
    }
    with open(OUT, 'w') as f: json.dump(doc, f, separators=(',', ':'))
    size, tex = os.path.getsize(OUT), os.path.getsize(TEX)
    print(f'{OUT}: inner {ni}x{ni} at {IN_CELL:.0f} m ({hi.min()}..{hi.max()} m), outer {no}x{no} at {OUT_CELL:.0f} m, {size} bytes')
    print(f'{TEX}: {RELIEF_PX}x{RELIEF_PX}, {tex} bytes')
    if size > 64 * 1024: sys.exit('ring.json over the 64 KB budget')
    if tex > 80 * 1024: sys.exit('ringrelief.webp over the 80 KB budget')


if __name__ == '__main__':
    main()
