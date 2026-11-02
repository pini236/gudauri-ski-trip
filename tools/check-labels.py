#!/usr/bin/env python3
"""Checks for run-name windows (round 21): the builder against tools/fixtures/labels-build.json, and that the Gudauri data is up to date."""
import json, subprocess, sys
from pathlib import Path
ROOT = Path(__file__).resolve().parent.parent
src = (ROOT / 'tools/build-labels.py').read_text().replace('\nmain()\n', '\n')
ns = {'__file__': str(ROOT / 'tools/build-labels.py')}
exec(compile(src, 'build-labels', 'exec'), ns)
fx = json.load(open(ROOT / 'tools/fixtures/labels-build.json'))
got = ns['build'](fx['data'], ns['proj'](fx['lat0'], fx['lon0']))
bad = 0
if got != fx['expected']:
    print('fixture mismatch\n got      %s\n expected %s' % (json.dumps(got), json.dumps(fx['expected']))); bad += 1
r = subprocess.run([sys.executable, str(ROOT / 'tools/build-labels.py'), '--check'], capture_output=True, text=True)
print(r.stdout.strip() or r.stderr.strip())
bad += r.returncode != 0
sys.exit(1 if bad else 0)
