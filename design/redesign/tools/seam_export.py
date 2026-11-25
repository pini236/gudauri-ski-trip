#!/usr/bin/env python3
"""The front seam of the live model and its ring (Pini on Y02, 25.11.2026: "the join at the bottom does not look natural:
a blurred flat strip and then an edge; you see where the model ends and the ring begins"). The cause, measured in the
design tool: (1) past the edge the ring held the edge heights for 900 m (a flat apron), (2) its relief faded in only 500 m
out, and at 60 m it is four times softer than the model's 14 m maps, (3) its rock came from another formula.
This writes the band: the model's own maps (live_export.world, the same recipe), carried MARGIN metres past the edge on
every side and INSIDE metres into the model, at TEX metres a texel; the interior is flat (unused, it compresses to nothing).
  tex/ringband.webp   four grey planes stacked: slope east and south, occlusion (as relief.webp), rock potential (as cover.webp R)
  band.json           its box (x0 z0 x1 z1, world metres, row 0 north), the margin, and the texel
Heights: the shipped model inside its box; outside, the same open tiles (zoom 12, about 28 m here), as the model's.
Nothing invented. Usage: PEAK_CACHE=/tmp/peak-cache python3 design/redesign/tools/seam_export.py"""
import os, sys, json, math, urllib.request
import numpy as np
from PIL import Image
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import live_export as LE
from mountain import dm, elev, blur, sstep, lat0, lon0

MARGIN, INSIDE, TEX = float(os.environ.get('BAND', 2000)), float(os.environ.get('BAND_IN', 1600)), float(os.environ.get('BAND_TEX', 30))
DST = os.path.join(LE.ROOT, 'design/redesign/assets', os.environ.get('RING_DST', 'ring2'))
X0, X1, W0, W1 = dm['x0'], dm['x1'], dm['y0'], dm['y1']          # world: x east, z = -north


def tiles12(E, N, Z=12):
    kx = 111320 * math.cos(math.radians(lat0)); lat = lat0 + N / 111320; lon = lon0 + E / kx
    fx = (lon + 180) / 360 * 2 ** Z * 256 - .5; la = np.radians(lat); fy = (1 - np.log(np.tan(la) + 1 / np.cos(la)) / math.pi) / 2 * 2 ** Z * 256 - .5
    x0 = int(np.floor(fx.min())) - 1; y0 = int(np.floor(fy.min())) - 1; x1 = int(np.ceil(fx.max())) + 2; y1 = int(np.ceil(fy.max())) + 2
    big = np.zeros((y1 - y0, x1 - x0)); n = 0
    for ty in range(y0 // 256, (y1 - 1) // 256 + 1):
        for tx in range(x0 // 256, (x1 - 1) // 256 + 1):
            f = os.path.join(LE.CACHE, 't%d_%d_%d.png' % (Z, tx, ty))
            if not os.path.exists(f):
                with urllib.request.urlopen('https://s3.amazonaws.com/elevation-tiles-prod/terrarium/%d/%d/%d.png' % (Z, tx, ty), timeout=60) as r: open(f, 'wb').write(r.read())
            a = np.asarray(Image.open(f).convert('RGB'), dtype=np.float64); t = a[..., 0] * 256 + a[..., 1] + a[..., 2] / 256 - 32768; n += 1
            ax, ay = tx * 256, ty * 256; sx0, sy0 = max(x0, ax), max(y0, ay); sx1, sy1 = min(x1, ax + 256), min(y1, ay + 256)
            big[sy0 - y0:sy1 - y0, sx0 - x0:sx1 - x0] = t[sy0 - ay:sy1 - ay, sx0 - ax:sx1 - ax]
    nb = np.stack([np.roll(np.roll(big, i, 0), j, 1) for i in (-1, 0, 1) for j in (-1, 0, 1) if i or j]); big = np.where(big - nb.max(0) > 60, np.median(nb, 0), big)
    px, py = fx - x0, fy - y0; c0 = px.astype(int); r0 = py.astype(int); a = px - c0; b = py - r0
    print('zoom %d tiles' % Z, n)
    return (big[r0, c0] * (1 - a) + big[r0, c0 + 1] * a) * (1 - b) + (big[r0 + 1, c0] * (1 - a) + big[r0 + 1, c0 + 1] * a) * b


def main():
    bx0, bx1, bz0, bz1 = X0 - MARGIN, X1 + MARGIN, W0 - MARGIN, W1 + MARGIN
    nx, nz = int(round((bx1 - bx0) / TEX)), int(round((bz1 - bz0) / TEX)); G = TEX
    x = bx0 + (np.arange(nx) + .5) * (bx1 - bx0) / nx; z = bz0 + (np.arange(nz) + .5) * (bz1 - bz0) / nz
    X, Zw = np.meshgrid(x, z)
    o = np.maximum.reduce([X0 - X, X - X1, W0 - Zw, Zw - W1])            # > 0 outside the model
    hm = elev(X, -Zw); ht = tiles12(X, -Zw)
    print('the model against the tiles, along the last 300 m inside: median %.1f m, 90%% %.1f m' % tuple(np.percentile(np.abs(hm - ht)[(o < 0) & (o > -300)], [50, 90])))
    w = sstep(-200, 0, o); h = (hm * (1 - w) + ht * w).astype(np.float32)          # the model's own heights inside, the tiles outside
    # the recipe of live_export.world, without runs (no groomed snow out here)
    sh = h.shape; zb = blur(h, 44, G); zs = zb + 0.9 * (zb - blur(h, 420, G))
    gz_, gx_ = np.gradient(zb, G, G); slope = np.degrees(np.arctan(np.hypot(gx_, gz_)))
    amp = (1 - sstep(30, 40, slope)) * 0.35
    zs = zs + (LE.vnoise(sh, 64 / G, 11) - .5) * 2.6 * 0.6 * amp
    dz, dx = np.gradient(zs, G, G)
    c1 = h - blur(h, 60, G); c2 = h - blur(h, 300, G); occ = 1 + np.clip(c1 / 26, -1, 1) * 0.08 + np.clip(c2 / 130, -1, 1) * 0.10
    gn = np.hypot(gx_, gz_) + 1e-5; fu, fd = gx_ / gn, gz_ / gn
    base = LE.vnoise(sh, 5 / G if G <= 5 else 1, 31); rr, cc = np.mgrid[0:sh[0], 0:sh[1]].astype(np.float32); streak = np.zeros(sh, dtype=np.float32)
    st = max(1.0, 3 * 1.95 / G)
    for i in range(-5, 6):
        streak += base[np.clip((rr + fd * i * st).astype(np.int32), 0, sh[0] - 1), np.clip((cc + fu * i * st).astype(np.int32), 0, sh[1] - 1)]
    streak /= 11
    pot = slope + (streak - .5) * 17 + (LE.vnoise(sh, 90 / G, 32) - .5) * 8 + (LE.vnoise(sh, 22 / G, 34) - .5) * 5
    deep = o < -INSIDE                                                       # unused inside: flat, so it weighs nothing
    R = np.where(deep, .5, .5 + dx / 3.2); Gc = np.where(deep, .5, .5 + dz / 3.2); B = np.where(deep, .5, (occ - .7) / .6); A = np.where(deep, 0, (pot - 18) / 30)
    dith = bool(int(os.environ.get('BAND_DITHER', 0)))      # no dither: on a flat valley floor the noise turns into blocks
    arr = np.dstack([LE.q8(R, dith, seed=1), LE.q8(Gc, dith, seed=2), LE.q8(B, dith, seed=3), LE.q8(A, dith, seed=4)])
    if os.environ.get('BAND_TRY'):                           # the encodings side by side: size, and the worst error on the slopes
        import io
        for name, kw in [('q88', dict(quality=88)), ('q95', dict(quality=95)), ('q100', dict(quality=100)), ('lossless', dict(lossless=True, quality=100))]:
            b = io.BytesIO(); Image.fromarray(arr, 'RGBA').save(b, 'WEBP', method=6, alpha_quality=100, exact=True, **kw); d = np.asarray(Image.open(io.BytesIO(b.getvalue()))).astype(int)
            e = np.abs(d - arr.astype(int))[~deep]; print(name, '%.0f KB' % (b.tell() / 1024), 'err rg p99 %d max %d, b p99 %d' % (np.percentile(e[:, :2], 99), e[:, :2].max(), np.percentile(e[:, 2], 99)))
        for qq in (85, 90, 95):                              # or each channel as its own grey picture (no colour subsampling)
            tot = 0; worst = []
            for c in range(4):
                b = io.BytesIO(); Image.fromarray(arr[..., c], 'L').save(b, 'WEBP', method=6, quality=qq); d = np.asarray(Image.open(io.BytesIO(b.getvalue())).convert('L')).astype(int)
                tot += b.tell(); worst.append(int(np.percentile(np.abs(d - arr[..., c].astype(int))[~deep], 99)))
            print('grey q%d %.0f KB' % (qq, tot / 1024), 'err p99 per channel', worst)
        return
    # one grey picture, the four planes stacked (R slope east, G slope south, B occlusion, A rock potential, top to bottom):
    # grey has no colour subsampling, so each plane keeps its own detail; the app stacks them back into one RGBA texture
    os.makedirs(os.path.join(DST, 'tex'), exist_ok=True); p = os.path.join(DST, 'tex', 'ringband.webp')
    Image.fromarray(np.vstack([arr[..., c] for c in range(4)]), 'L').save(p, 'WEBP', method=6, quality=int(os.environ.get('BAND_Q', 85)))
    kb = os.path.getsize(p) / 1024
    json.dump(dict(note='the model\'s maps carried past its edge, as one grey picture of four stacked planes (top to bottom): slope east (x3.2 around 0.5), slope south, occlusion (0.7+0.6v), rock potential (18+30v deg); the interior is flat',
                   x0=bx0, z0=bz0, x1=bx1, z1=bz1, margin=MARGIN, inside=INSIDE, texel=TEX, px=[nx, nz], kb=round(kb)), open(os.path.join(DST, 'band.json'), 'w'), indent=1)
    print('ringband.webp', arr.shape[1], 'x', arr.shape[0], 'x 4 planes', '%.0f KB' % kb)


if __name__ == '__main__': main()
