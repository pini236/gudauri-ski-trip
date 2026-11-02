#!/usr/bin/env python3
"""site/data/places.json is the lean places file the ski expert builds (research/places/places.lean.json, by
tools/build-places.py). One source: this copies it for the site and the apps, and --check fails when the copy drifts
(round 31, docs/ARCHITECTURE.md "places")."""
import sys
from pathlib import Path
ROOT = Path(__file__).resolve().parent.parent
SRC, DST = ROOT / 'research/places/places.lean.json', ROOT / 'site/data/places.json'
want = SRC.read_text(encoding='utf-8')
if '--check' in sys.argv:
    if not DST.exists() or DST.read_text(encoding='utf-8') != want:
        sys.exit('site/data/places.json is not the lean file: run python3 tools/build-site-places.py')
    print('site/data/places.json matches research/places/places.lean.json')
else:
    DST.write_text(want, encoding='utf-8'); print('wrote', DST.relative_to(ROOT), len(want), 'bytes')
