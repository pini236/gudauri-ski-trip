#!/bin/sh
# Gate (DevOps, 14.11.2026, after iOS build 55 crashed). Android has no runtime isolation trap, but a system
# registration left on the default (main) thread runs its callbacks beside the recording thread: a data race.
# Fails on registerListener( / requestLocationUpdates( / registerGnssStatusCallback( / registerReceiver( with no explicit
# handler or looper on the line, unless "main-thread-ok: <reason>" is in a comment on that line or the line before.
# Pattern check only. Races inside a handler are covered by SystemBoundariesTest.
# usage: check-android-threads.sh [dir ...]   (default: app/android/app/src/main/java)
set -u
[ "$#" -gt 0 ] || set -- app/android/app/src/main/java
exec python3 - "$@" <<'PY'
import re, sys, pathlib
CALL = re.compile(r'(?<!un)\b(registerListener|requestLocationUpdates|registerGnssStatusCallback|registerReceiver)\s*\(')
OK = re.compile(r'handler|looper', re.I)
bad = 0
for root in sys.argv[1:]:
    for f in sorted(pathlib.Path(root).rglob('*.kt')):
        lines = f.read_text(encoding='utf-8').splitlines()
        for i, line in enumerate(lines):
            if not CALL.search(line) or line.lstrip().startswith('//'):
                continue
            ctx = line + ' ' + (lines[i-1] if i else '')
            if 'main-thread-ok:' in ctx or OK.search(line):
                continue
            bad += 1
            print(f'::error file={f},line={i+1}::{CALL.search(line).group(1)}( without a handler or looper; pass one, or add "main-thread-ok: <reason>"')
print('threads gate: ' + ('FAILED' if bad else 'ok'))
sys.exit(1 if bad else 0)
PY
