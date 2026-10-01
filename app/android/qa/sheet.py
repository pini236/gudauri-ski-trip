#!/usr/bin/env python3
"""Contact sheets for the emulator run (app/android/qa/README.md): the screenshots of each part side by side,
small enough to look at in one go. Usage: sheet.py <shots dir> <out dir>. Needs Pillow."""
import re
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

THUMB_W = 300
COLS = 6
# the specific parts first: a shot goes to the first part it matches
PARTS = [("gesture", r"-map-gesture-"), ("light", r"-map-light-"), ("fly", r"-fly-"), ("descent", r"-descent-"),
         ("ticket", r"-ticket-|-ticket\."), ("release", r"-release-"), ("map", r"-(map|run)-")]


def sheet(files, out):
    thumbs = []
    for f in files:
        im = Image.open(f).convert("RGB")
        h = round(im.height * THUMB_W / im.width)
        thumbs.append((f.stem, im.resize((THUMB_W, h), Image.LANCZOS)))
    th = max(t.height for _, t in thumbs)
    rows = (len(thumbs) + COLS - 1) // COLS
    cols = min(COLS, len(thumbs))
    pad, label = 8, 22
    canvas = Image.new("RGB", (cols * (THUMB_W + pad) + pad, rows * (th + label + pad) + pad), "white")
    draw = ImageDraw.Draw(canvas)
    font = ImageFont.load_default(size=15)
    for i, (name, t) in enumerate(thumbs):
        x = pad + (i % COLS) * (THUMB_W + pad)
        y = pad + (i // COLS) * (th + label + pad)
        draw.text((x, y), name, fill="black", font=font)
        canvas.paste(t, (x, y + label))
    canvas.save(out, optimize=True)


def main():
    shots, out = Path(sys.argv[1]), Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    files = sorted(shots.glob("*.png"))
    used = set()
    for part, pattern in PARTS:
        picked = [f for f in files if re.search(pattern, "-" + f.name) and f not in used]
        used.update(picked)
        if picked:
            sheet(picked, out / f"sheet-{part}.png")
            print(f"sheet-{part}.png: {len(picked)} shots")


if __name__ == "__main__":
    main()
