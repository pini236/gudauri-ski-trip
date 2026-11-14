#!/bin/sh
# Gate (DevOps, 14.11.2026, after iOS build 55 crashed). Android has no runtime isolation trap, but a system
# registration left on the default (main) thread runs its callbacks beside the recording thread: a data race.
# Fails on registerListener( / requestLocationUpdates( / registerGnssStatusCallback( / registerReceiver( whose whole
# argument list (up to the closing paren, across lines) names no handler or looper, unless
# "main-thread-ok: <non-empty reason>" is in a comment on the call line or the line before.
# Pattern check only. Races inside a handler are covered by SystemBoundariesTest.
# usage: check-android-threads.sh [dir ...]   (default: app/android/app/src, all source sets)
set -u
[ "$#" -gt 0 ] || set -- app/android/app/src
exec python3 - "$@" <<'PY'
import re, sys, pathlib
CALL = re.compile(r'(?<!un)\b(registerListener|requestLocationUpdates|registerGnssStatusCallback|registerReceiver)\s*\(')
OK = re.compile(r'handler|looper', re.I)
REASON = re.compile(r'main-thread-ok:\s*\S')
bad = 0
for root in sys.argv[1:]:
    for f in sorted(pathlib.Path(root).rglob('*.kt')):
        lines = f.read_text(encoding='utf-8').splitlines()
        for i, line in enumerate(lines):
            m = CALL.search(line)
            if not m or line.lstrip().startswith('//'):
                continue
            # read to the matching closing paren, across lines
            depth, args, j, started = 0, '', i, False
            start = m.end() - 1
            while j < len(lines) and j < i + 15:
                seg = lines[j][start:] if j == i else lines[j]
                for ch in seg:
                    if ch == '(':
                        depth += 1; started = True
                    elif ch == ')':
                        depth -= 1
                    args += ch
                    if started and depth == 0:
                        break
                if started and depth == 0:
                    break
                args += ' '; j += 1
            ctx = line + ' ' + (lines[i-1] if i else '')
            if REASON.search(ctx) or OK.search(args):
                continue
            bad += 1
            print(f'::error file={f},line={i+1}::{m.group(1)}( without a handler or looper; pass one, or add "main-thread-ok: <reason>"')
print('threads gate: ' + ('FAILED' if bad else 'ok'))
sys.exit(1 if bad else 0)
PY
