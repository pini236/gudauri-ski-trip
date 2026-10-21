#!/usr/bin/env python3
"""Copy the site's data into the iPhone app (read-only: never writes into site/).

Usage: python3 tools/build-ios-data.py [out-dir]
Default out-dir: app/ios/Gudi/Resources/Data (git-ignored, made at build time, like Android's).
The iPhone's own copy of tools/build-app-data.py (docs/ARCHITECTURE.md, review of 21.10.2026: Android's tool stays
as it is). trip.json (the group's flight and names) is never copied: see docs/USERS.md.
"""
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SITE = ROOT / "site"
FILES = ("terrain.json", "runs-and-lifts.json", "videos-seed.json")


def main():
    out = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else ROOT / "app/ios/Gudi/Resources/Data"
    out.mkdir(parents=True, exist_ok=True)
    for name in FILES:
        shutil.copyfile(SITE / "data" / name, out / name)
    # the real view from the village through the day, for the home page (site/img/pano; design/round3/panorama.py)
    (out / "pano").mkdir(exist_ok=True)
    panos = sorted((SITE / "img/pano").glob("*.webp"))
    for img in panos:
        shutil.copyfile(img, out / "pano" / img.name)
    print(f"copied {len(FILES)} files and {len(panos)} views to {out}")


if __name__ == "__main__":
    main()
