#!/usr/bin/env python3
"""The mountains around the live model, as good as the mountain itself (Pini on 0.1.884, 25.11.2026: "the mountain looks
better, but it looks odd among all the mountains, they are plain, without details"). The data for the design tool:
  inner.json        real heights round the model, CELL_IN metres, out to IN metres each way (Kazbek is 21 km north)
  outer.json        the same out to OUT metres, CELL_OUT (what 0.1.884 ships for the whole ring)
  now.json          the app's ring of 0.1.884 (app/android/.../live/ring.json), only re-encoded, for the "before" frames
  tex/ringrelief.webp   R,G = slope east and south at the source's own resolution (120 m), B = occlusion, over IN metres:
                    the shading has the detail the triangles cannot carry
Same projection and format as edge_export (int16 metres, row 0 north). Nothing invented: every height is the open
elevation data (Terrain Tiles on AWS, as the model and the far horizon), cleaned of lone spikes; on a ridge the vertex
takes the highest real sample in its own cell, so the sky line is not shaved by the coarser grid.
Usage: PEAK_CACHE=/tmp/peak-cache python3 design/redesign/tools/ring2_export.py"""
import os, sys, json, base64, zlib
import numpy as np
from PIL import Image
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import live_export as LE

IN, CELL_IN = float(os.environ.get('RING_IN', 24000)), float(os.environ.get('RING_CELL_IN', 240))
OUT, CELL_OUT = 42000.0, float(os.environ.get("RING_CELL_OUT", 700))
DST = os.path.join(LE.ROOT, 'design/redesign/assets', os.environ.get('RING_DST', 'ring2'))
TEX = float(os.environ.get('RING_TEX', 60))           # metres a texel of the relief (zoom 11 tiles: about 56 m)
os.makedirs(os.path.join(DST, 'tex'), exist_ok=True)
HALF, FC = 62000.0, 120.0                        # far.npy: 120 m cells, 62 km each way, row 0 north


def source():
    if not os.path.exists(LE.FAR): LE.fetch_far()
    z = np.load(LE.FAR).astype(np.float64)
    nb = np.stack([np.roll(np.roll(z, i, 0), j, 1) for i in (-1, 0, 1) for j in (-1, 0, 1) if i or j])
    return np.where(z - nb.max(0) > 60, np.median(nb, 0), z)


def fine(half, cell):
    """the same open tiles at zoom 11 (about 56 m here), as a grid of cell metres over half each way, row 0 north"""
    import math, urllib.request
    from PIL import Image as I
    Z = 11; lat0, lon0 = LE.lat0, LE.lon0; kx = 111320 * math.cos(math.radians(lat0))
    n = int(round(2 * half / cell)); e = -half + (np.arange(n) + .5) * cell; no = half - (np.arange(n) + .5) * cell
    E, N = np.meshgrid(e, no); lat = lat0 + N / 111320; lon = lon0 + E / kx
    fx = (lon + 180) / 360 * 2 ** Z * 256 - .5; la = np.radians(lat); fy = (1 - np.log(np.tan(la) + 1 / np.cos(la)) / math.pi) / 2 * 2 ** Z * 256 - .5
    tiles = {}
    def tile(tx, ty):
        if (tx, ty) not in tiles:
            f = os.path.join(LE.CACHE, 't%d_%d_%d.png' % (Z, tx, ty))
            if not os.path.exists(f):
                with urllib.request.urlopen('https://s3.amazonaws.com/elevation-tiles-prod/terrarium/%d/%d/%d.png' % (Z, tx, ty), timeout=60) as r: open(f, 'wb').write(r.read())
            a = np.asarray(I.open(f).convert('RGB'), dtype=np.float64); tiles[(tx, ty)] = a[..., 0] * 256 + a[..., 1] + a[..., 2] / 256 - 32768
        return tiles[(tx, ty)]
    x0 = int(np.floor(fx.min())) - 1; y0 = int(np.floor(fy.min())) - 1; x1 = int(np.ceil(fx.max())) + 2; y1 = int(np.ceil(fy.max())) + 2
    big = np.zeros((y1 - y0, x1 - x0))
    for ty in range(y0 // 256, (y1 - 1) // 256 + 1):
        for tx in range(x0 // 256, (x1 - 1) // 256 + 1):
            t = tile(tx, ty); ax, ay = tx * 256, ty * 256
            sx0, sy0 = max(x0, ax), max(y0, ay); sx1, sy1 = min(x1, ax + 256), min(y1, ay + 256)
            big[sy0 - y0:sy1 - y0, sx0 - x0:sx1 - x0] = t[sy0 - ay:sy1 - ay, sx0 - ax:sx1 - ax]
    nb = np.stack([np.roll(np.roll(big, i, 0), j, 1) for i in (-1, 0, 1) for j in (-1, 0, 1) if i or j]); big = np.where(big - nb.max(0) > 60, np.median(nb, 0), big)
    px, py = fx - x0, fy - y0; c0 = px.astype(int); r0 = py.astype(int); a = px - c0; b = py - r0
    print('zoom 11 tiles', len(tiles))
    return (big[r0, c0] * (1 - a) + big[r0, c0 + 1] * a) * (1 - b) + (big[r0 + 1, c0] * (1 - a) + big[r0 + 1, c0 + 1] * a) * b


def sample(z, E, N):
    m = z.shape[0]; c = (E + HALF) / FC; r = (HALF - N) / FC
    c0 = np.clip(c.astype(int), 0, m - 2); r0 = np.clip(r.astype(int), 0, m - 2); a = c - c0; b = r - r0
    return (z[r0, c0] * (1 - a) + z[r0, c0 + 1] * a) * (1 - b) + (z[r0 + 1, c0] * (1 - a) + z[r0 + 1, c0 + 1] * a) * b


def grid(z, half, cell, ridge=True):
    n = int(round(2 * half / cell)) + 1; e = np.linspace(-half, half, n); no = np.linspace(half, -half, n)
    E, N = np.meshgrid(e, no); h = sample(z, E, N)
    if ridge:                                    # the highest real sample in the vertex's own cell, on ridges only
        k = int(round(cell / FC / 2)); hi = np.full_like(h, -1e9)
        for dy in range(-k, k + 1):
            for dx in range(-k, k + 1): hi = np.maximum(hi, sample(z, E + dx * FC, N + dy * FC))
        lap = h - (np.roll(h, 1, 0) + np.roll(h, -1, 0) + np.roll(h, 1, 1) + np.roll(h, -1, 1)) / 4
        h = np.where(lap > 4, hi, h)
    return n, np.round(h).astype('<i2')


def dump(name, half, cell, q, note):
    json.dump(dict(note=note, x0=-half, x1=half, z0=-half, z1=half, n=q.shape[0], cell=cell, b64=base64.b64encode(q.tobytes()).decode()),
              open(os.path.join(DST, name), 'w'), separators=(',', ':'))
    # what it would weigh shipped as the app's ring.json (zlib of row deltas), and its triangles past the model (15.6 km box)
    d = q.astype(np.int32).copy(); d[:, 1:] -= q[:, :-1].astype(np.int32)
    kb = len(base64.b64encode(zlib.compress(d.astype('<i2').tobytes(), 9))) / 1024
    print(name, q.shape, 'cell', cell, 'min', int(q.min()), 'max', int(q.max()), 'as shipped ~%.0f KB' % kb)
    return kb


def main():
    z = source()
    n, qi = grid(z, IN, CELL_IN)
    kin = dump('inner.json', IN, CELL_IN, qi, 'real terrain round the model, %d m cells, %d km each way; ridges keep their highest sample' % (CELL_IN, IN / 1000))
    n2, qo = grid(z, OUT, CELL_OUT, ridge=False)
    kout = dump('outer.json', OUT, CELL_OUT, qo, 'the same, %d m cells, %d km each way' % (CELL_OUT, OUT / 1000))
    # the app's ring of 0.1.884, as it is
    A = json.load(open(os.path.join(LE.ROOT, 'app/android/app/src/main/assets/redesign/live/ring.json')))
    raw = np.frombuffer(zlib.decompress(base64.b64decode(A['h'])), '<i2').reshape(A['ny'], A['nx']).astype(np.int32)
    qa = np.cumsum(raw, axis=1).astype('<i2')
    json.dump(dict(note='the ring of 0.1.884 (tools/build-ring.py), re-encoded', x0=A['x0'], x1=-A['x0'], z0=A['y0'], z1=-A['y0'], n=A['nx'], cell=A['cell'], b64=base64.b64encode(qa.tobytes()).decode()),
              open(os.path.join(DST, 'now.json'), 'w'), separators=(',', ':'))
    print('now.json', qa.shape, 'max', int(qa.max()))
    # the relief texture: slopes and occlusion at TEX metres (zoom 11), over the inner ring
    h = fine(IN, TEX)
    dz, dx = np.gradient(h, TEX, TEX)            # rows go south: d/drow = d/dsouth
    from mountain import blur
    c1 = h - blur(h, 400, TEX); c2 = h - blur(h, 3000, TEX); occ = 1 + np.clip(c1 / 60, -1, 1) * 0.09 + np.clip(c2 / 300, -1, 1) * 0.11
    im = Image.fromarray(np.dstack([LE.q8(.5 + dx / 3.2, seed=1), LE.q8(.5 + dz / 3.2, seed=2), LE.q8((occ - .7) / .6, seed=3)]))
    p = os.path.join(DST, 'tex', 'ringrelief.webp'); im.save(p, quality=90, method=6)
    ktex = os.path.getsize(p) / 1024
    print('ringrelief.webp', im.size, '%.0f KB' % ktex)
    # triangles past the model's box
    def tris(half, cell, hole):
        k = int(round(2 * half / cell)); s = np.arange(k) * cell - half
        X, Y = np.meshgrid(s, s); inside = (X > -7800 + cell) & (X + cell < 6975 - cell) & (Y > -7792 + cell) & (Y + cell < 7792 - cell)
        if hole: inside |= (X >= -hole) & (X + cell <= hole) & (Y >= -hole) & (Y + cell <= hole)
        return int((~inside).sum() * 2)
    ti, to = tris(IN, CELL_IN, 0), tris(OUT, CELL_OUT, IN)
    json.dump(dict(inner_tris=ti, outer_tris=to, inner_kb=round(kin), outer_kb_full=round(kout), tex_kb=round(ktex)), open(os.path.join(DST, 'budget.json'), 'w'), indent=1)
    print('triangles: inner', ti, 'outer (outside the inner)', to, 'total', ti + to)


if __name__ == '__main__': main()
