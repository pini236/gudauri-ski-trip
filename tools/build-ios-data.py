#!/usr/bin/env python3
"""Copy the site's data into the iPhone app (read-only: never writes into site/).

Usage: python3 tools/build-ios-data.py [out-dir]
Default out-dir: app/ios/Gudi/Resources/Data (git-ignored, made at build time, like Android's).
The iPhone's own copy of tools/build-app-data.py (docs/ARCHITECTURE.md, review of 21.10.2026: Android's tool stays
as it is). trip.json (the group's flight and names) is never copied: see docs/USERS.md.
"""
import json
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SITE = ROOT / "site"
FILES = ("terrain.json", "runs-and-lifts.json", "videos-seed.json")


def descent_profiles():
    """The descent's five runs (the site's PROFILES in site/games/descent, its page or its own script), as Android's
    tools/build-app-data.py exports them, so the iPhone plays the exact same runs as the site."""
    d = SITE / "games/descent"
    marker = "const PROFILES = "
    for f in [d / "index.html", *sorted(d.glob("*.js"))]:
        if f.exists() and marker in (text := f.read_text(encoding="utf-8")):
            data, _ = json.JSONDecoder().raw_decode(text[text.index(marker) + len(marker):])
            return [{**{k: p[k] for k in ("key", "color", "len", "top", "bot", "step", "h")}, "lift": p.get("lift", "")} for p in data]
    raise SystemExit("no PROFILES in site/games/descent")


def main():
    out = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else ROOT / "app/ios/Gudi/Resources/Data"
    out.mkdir(parents=True, exist_ok=True)
    for name in FILES:
        shutil.copyfile(SITE / "data" / name, out / name)
    (out / "profiles.json").write_text(json.dumps(descent_profiles(), separators=(",", ":")), encoding="utf-8")
    # the real view from the village through the day, for the home page (site/img/pano; design/round3/panorama.py)
    (out / "pano").mkdir(exist_ok=True)
    panos = sorted((SITE / "img/pano").glob("*.webp"))
    for img in panos:
        shutil.copyfile(img, out / "pano" / img.name)
    # the pass's recorded sounds (site/audio; the tear is CC BY, credited in about)
    (out / "audio").mkdir(exist_ok=True)
    for snd in sorted((SITE / "audio").glob("*.wav")):
        shutil.copyfile(snd, out / "audio" / snd.name)
    # the games page's pictures (site/games/thumbs), as Android's assets/thumbs
    (out / "thumbs").mkdir(exist_ok=True)
    for img in sorted((SITE / "games/thumbs").glob("*.webp")):
        shutil.copyfile(img, out / "thumbs" / img.name)
    print(f"copied {len(FILES)} files and {len(panos)} views to {out}")


if __name__ == "__main__":
    main()
