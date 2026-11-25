#!/usr/bin/env python3
"""
The ring of far ground round the live mountain (the architect's option a, docs/ARCHITECTURE.md, 25.11.2026).

Past the edge of the height model (site/data/terrain.json, about 15 km square, 40 m cells) the farthest pinch saw the
mesh's cut edge over the sky (Pini, 0.1.867). This builds a coarse grid of real heights round it, from the same source
and in the same projection as terrain.json: Terrain Tiles on AWS (terrarium PNG, mostly SRTM; the credits already
name them), the site's projection (x east, y south, metres round lat0/lon0).

Run by hand only (the build never downloads anything):  python3 tools/build-ring.py
It writes app/android/app/src/main/assets/redesign/live/ring.json, with the source, the date it was fetched and the
cell in its header. The app's unit test (RingTest) fails over 64 KB or 40,000 triangles.

The grid's middle (under the height model) is kept: the app lowers it under the mesh, so there is no seam.
"""
import base64, datetime, io, json, math, os, sys, tempfile, urllib.request, zlib

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'app/android/app/src/main/assets/redesign/live/ring.json')
TERRAIN = os.path.join(ROOT, 'site/data/terrain.json')
CELL = 700.0          # metres
HALF = 60              # cells each side of the middle: 121 x 121 vertices, 84 km square
Z = 9                  # terrarium zoom: about 230 m a pixel here, finer than the cell
SRC = 'https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png'
CACHE = os.path.join(tempfile.gettempdir(), 'gudi-ring-tiles')

proj = json.load(open(TERRAIN))['proj']
lat0, lon0 = proj['lat0'], proj['lon0']
KX, KY = 111320 * math.cos(math.radians(lat0)), 111320   # as Geo.kt and build-resort.py


def unP(x, y):
    return lat0 - y / KY, lon0 + x / KX


def tile_of(lat, lon):
    n = 2 ** Z
    fx = (lon + 180) / 360 * n
    la = math.radians(lat)
    fy = (1 - math.log(math.tan(la) + 1 / math.cos(la)) / math.pi) / 2 * n
    return fx, fy


tiles = {}


def tile(tx, ty):
    if (tx, ty) not in tiles:
        os.makedirs(CACHE, exist_ok=True)
        f = os.path.join(CACHE, f'{Z}-{tx}-{ty}.png')
        if not os.path.exists(f):
            with urllib.request.urlopen(SRC.format(z=Z, x=tx, y=ty), timeout=60) as r, open(f, 'wb') as o:
                o.write(r.read())
        a = np.asarray(Image.open(f).convert('RGB'), dtype=np.float64)
        tiles[(tx, ty)] = a[..., 0] * 256 + a[..., 1] + a[..., 2] / 256 - 32768
    return tiles[(tx, ty)]


def height(lat, lon):
    """Bilinear in the tile's pixels."""
    fx, fy = tile_of(lat, lon)
    px, py = fx * 256 - 0.5, fy * 256 - 0.5
    x0, y0 = math.floor(px), math.floor(py)
    fx_, fy_ = px - x0, py - y0
    def at(ix, iy):
        tx, ix2 = divmod(ix, 256); ty, iy2 = divmod(iy, 256)
        return tile(tx, ty)[iy2, ix2]
    return ((at(x0, y0) * (1 - fx_) + at(x0 + 1, y0) * fx_) * (1 - fy_) +
            (at(x0, y0 + 1) * (1 - fx_) + at(x0 + 1, y0 + 1) * fx_) * fy_)


n = 2 * HALF + 1
x0 = -HALF * CELL
h = np.zeros((n, n), dtype=np.int16)
for j in range(n):
    for i in range(n):
        h[j, i] = round(height(*unP(x0 + i * CELL, x0 + j * CELL)))

raw = h.astype('<i2').tobytes()
# deltas along each row pack better
d = h.astype(np.int32).copy(); d[:, 1:] -= h[:, :-1].astype(np.int32)
packed = zlib.compress(d.astype('<i2').tobytes(), 9)
doc = {
    'schema': 1,
    'source': 'Terrain Tiles on AWS (terrarium, zoom %d), mostly SRTM' % Z,
    'url': SRC,
    'fetched': datetime.date.today().isoformat(),
    'cell': CELL, 'x0': x0, 'y0': x0, 'nx': n, 'ny': n,
    'proj': {'lat0': lat0, 'lon0': lon0},
    'encoding': 'zlib of little-endian int16 metres, row by row (y south), each value after the first in a row the '
                'difference from the one before it',
    'h': base64.b64encode(packed).decode(),
}
os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, 'w') as f:
    json.dump(doc, f, separators=(',', ':'))
size = os.path.getsize(OUT)
print(f'{OUT}: {n}x{n} at {CELL:.0f} m, heights {h.min()}..{h.max()} m, {size} bytes ({len(raw)} raw), '
      f'{len(tiles)} tiles')
if size > 64 * 1024:
    sys.exit('over the 64 KB budget')
