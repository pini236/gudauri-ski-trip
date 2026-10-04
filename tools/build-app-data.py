#!/usr/bin/env python3
"""Copy the site's data into the native apps (read-only: never writes into site/).

Usage: python3 tools/build-app-data.py <out-dir>
Writes <out-dir>/data/{terrain,runs-and-lifts,videos-seed,profiles}.json, <out-dir>/audio/*.wav and
<out-dir>/pano/*.webp (the real view from the village through the day, for the home page) and <out-dir>/thumbs/*.webp
(the games page's pictures).
profiles.json is the elevation profile of each run in the descent game, taken from
site/games/descent/index.html so the app plays the exact same runs as the site.
trip.json (the group's flight and names) is never copied: see docs/USERS.md.
"""
import json
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SITE = ROOT / "site"


def profiles():
    html = (SITE / "games/descent/index.html").read_text(encoding="utf-8")
    marker = "const PROFILES = "
    start = html.index(marker) + len(marker)
    data, _ = json.JSONDecoder().raw_decode(html[start:])
    return [{k: p[k] for k in ("key", "color", "len", "top", "bot", "step", "h")} for p in data]


def main():
    out = Path(sys.argv[1]).resolve()
    (out / "data").mkdir(parents=True, exist_ok=True)
    (out / "audio").mkdir(parents=True, exist_ok=True)
    for name in ("terrain.json", "runs-and-lifts.json", "videos-seed.json"):
        shutil.copyfile(SITE / "data" / name, out / "data" / name)
    (out / "data/profiles.json").write_text(json.dumps(profiles(), separators=(",", ":")), encoding="utf-8")
    for wav in sorted((SITE / "audio").glob("*.wav")):
        shutil.copyfile(wav, out / "audio" / wav.name)
    (out / "pano").mkdir(parents=True, exist_ok=True)
    for img in sorted((SITE / "img/pano").glob("*.webp")):
        shutil.copyfile(img, out / "pano" / img.name)
    # the games page's pictures, the site's (round 8, GP2)
    (out / "thumbs").mkdir(parents=True, exist_ok=True)
    for img in sorted((SITE / "games/thumbs").glob("*.webp")):
        shutil.copyfile(img, out / "thumbs" / img.name)


if __name__ == "__main__":
    main()
