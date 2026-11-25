#!/usr/bin/env python3
"""The edge of the live model (Pini, 25.11.2026: "the model is cut, as if it floats"): the data for the design tool,
for alternative A, real mountains all around up to the far horizon.
  ring.json        the real terrain around the shipped model, out to RING metres each way, from the same open
                   elevation tiles as the model and the far horizon (live_export.fetch_far): int16 metres, row 0 north
  tex/horizon.webp the far horizon again, now only what lies beyond the ring (the ring draws the rest itself)
Both go to design/redesign/assets/edge. Nothing invented: every height is the open terrain data, cleaned of lone
spikes (voids) as in live_export.horizon. Usage: PEAK_CACHE=/tmp/peak-cache python3 design/redesign/tools/edge_export.py"""
import os, sys, json, math, base64
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import live_export as LE

RING, CELL = 30000.0, 240.0
OUT = os.path.join(LE.ROOT, 'design/redesign/assets/edge'); os.makedirs(os.path.join(OUT, 'tex'), exist_ok=True)


def main():
    if not os.path.exists(LE.FAR): LE.fetch_far()
    z = np.load(LE.FAR); m = z.shape[0]; half = 62000.0; fc = 120.0
    nb = np.stack([np.roll(np.roll(z, i, 0), j, 1) for i in (-1, 0, 1) for j in (-1, 0, 1) if i or j]); z = np.where(z - nb.max(0) > 60, np.median(nb, 0), z)
    n = int(2 * RING / CELL) + 1; e = np.linspace(-RING, RING, n); no = np.linspace(RING, -RING, n)          # row 0 north
    E, N = np.meshgrid(e, no); c = (E + half) / fc; r = (half - N) / fc
    c0 = np.clip(c.astype(int), 0, m - 2); r0 = np.clip(r.astype(int), 0, m - 2); a = c - c0; b = r - r0
    zz = (z[r0, c0] * (1 - a) + z[r0, c0 + 1] * a) * (1 - b) + (z[r0 + 1, c0] * (1 - a) + z[r0 + 1, c0 + 1] * a) * b
    q = np.round(zz).astype('<i2')
    json.dump(dict(note='real terrain around the model, open elevation tiles (as the model); int16 metres little-endian, row 0 = north (z = x0 side of world z)',
                   x0=-RING, x1=RING, z0=-RING, z1=RING, n=n, cell=CELL, b64=base64.b64encode(q.tobytes()).decode()),
              open(os.path.join(OUT, 'ring.json'), 'w'), separators=(',', ':'))
    print('ring', q.shape, 'min', int(q.min()), 'max', int(q.max()), os.path.getsize(os.path.join(OUT, 'ring.json')) // 1024, 'KB')
    cam = json.load(open(os.path.join(LE.ROOT, 'design/handoff/2026-11-20-redesign-live/peak-live.json')))['camera']
    pr, dd = math.radians(cam['pitch_deg']), cam['dist']
    eye = (cam['tx'], -(cam['tz'] + math.cos(pr) * dd), cam['ty'] + math.sin(pr) * dd)
    LE.OUT = OUT; LE.X0, LE.X1, LE.W0, LE.W1 = -RING, RING, -RING, RING                   # the ring draws everything inside it
    LE.horizon(eye)


if __name__ == '__main__' and '--cloud' not in sys.argv: main()


def cloud(n=512):
    """alternative B only: a tileable lumpy field for the sea of cloud (paint, no claim about real weather)"""
    from PIL import Image
    f = sum(LE.tnoise(n, c, c, 70 + i) * w for i, (c, w) in enumerate([(4, .5), (8, .28), (16, .14), (32, .08)]))
    f = (f - f.min()) / (f.max() - f.min()); f = np.clip((f - 0.25) / 0.6, 0, 1) ** 1.4
    Image.fromarray(LE.q8(f, False)).save(os.path.join(OUT, 'tex', 'cloud.webp'), quality=92, method=6); print('cloud.webp')


if __name__ == '__main__' and '--cloud' in sys.argv: cloud()
