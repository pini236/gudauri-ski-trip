#!/bin/sh
# Gate on every iOS build (manager and DevOps, 14.11.2026, after build 55 crashed when a recording started).
# In a file with @MainActor, a closure written at the call site of a system API that calls back on a background queue
# inherits the actor isolation, and the Swift 6 runtime traps when the system calls it off the main actor.
# The closure must come from a nonisolated static function instead (see DayRecorder.accelHandler).
# Fails on: startAccelerometerUpdates(to:, startDeviceMotionUpdates(to:, startGyroUpdates(to:, startMagnetometerUpdates(to:,
#   startUpdates(to:, requestTemporaryFullAccuracyAuthorization, addObserver(forName:..queue: other than .main),
#   dataTask(with:, scheduledTimer, ASWebAuthenticationSession(, and pathUpdateHandler/updateHandler = { }, with a closure literal or a trailing closure.
# Every isolation-ok needs a written reason next to it and review approval. Pattern check only, not a replacement for the compiler.
# Allowed: the closure comes from a function call such as Self.handler(x). A deliberate exception: put "isolation-ok" in a comment on the line.
# usage: check-ios-isolation.sh [dir ...]   (default: app/ios/Gudi)
set -u
[ "$#" -gt 0 ] || set -- app/ios/Gudi
exec python3 - "$@" <<'PY'
import re, sys, pathlib
API = re.compile(r'(?:\.|\b)(startAccelerometerUpdates|startDeviceMotionUpdates|startGyroUpdates|startMagnetometerUpdates|startUpdates|requestTemporaryFullAccuracyAuthorization|addObserver|dataTask|scheduledTimer|ASWebAuthenticationSession)\s*\(')
ASSIGN = re.compile(r'\b(pathUpdateHandler|updateHandler|didCompleteHandler)\s*=\s*(\[[^\]]*\]\s*)?\{')
bad = 0
for root in sys.argv[1:]:
    for f in sorted(pathlib.Path(root).rglob('*.swift')):
        src = f.read_text(encoding='utf-8')
        if '@MainActor' not in src:
            continue
        for m in API.finditer(src):
            name = m.group(1)
            i, depth, j = m.end(), 1, m.end()
            while j < len(src) and depth:
                depth += (src[j] == '(') - (src[j] == ')')
                j += 1
            args = src[i:j - 1]
            tail = src[j:j + 40].lstrip()
            line_no = src.count('\n', 0, m.start()) + 1
            line = src.splitlines()[line_no - 1]
            if 'isolation-ok' in line:
                continue
            if name == 'startUpdates' and 'to:' not in args:
                continue
            if name == 'addObserver' and ('forName' not in args or re.search(r'queue:\s*\.main', args)):
                continue
            if name == 'dataTask' and 'with:' not in args:
                continue
            literal = re.search(r'(^|[,(])\s*(\w+:\s*)?(\[[^\]]*\]\s*)?\{', args) or tail.startswith('{')
            if literal:
                bad = 1
                print(f'::error file={f},line={line_no}::closure literal passed to {name}( in a @MainActor file; build it in a nonisolated static function')
for f in sorted(p for r in sys.argv[1:] for p in pathlib.Path(r).rglob('*.swift')):
    src = f.read_text(encoding='utf-8')
    if '@MainActor' not in src:
        continue
    for m in ASSIGN.finditer(src):
        line_no = src.count('\n', 0, m.start()) + 1
        if 'isolation-ok' in src.splitlines()[line_no - 1]:
            continue
        bad = 1
        print(f'::error file={f},line={line_no}::closure literal assigned to {m.group(1)} in a @MainActor file; build it in a nonisolated static function')
for f in sorted(p for r in sys.argv[1:] for p in pathlib.Path(r).rglob('*.swift')):
    src = f.read_text(encoding='utf-8')
    if '@MainActor' not in src:
        continue
    for m in ASSIGN.finditer(src):
        line_no = src.count('\n', 0, m.start()) + 1
        if 'isolation-ok' in src.splitlines()[line_no - 1]:
            continue
        bad = 1
        print(f'::error file={f},line={line_no}::closure literal assigned to {m.group(1)} in a @MainActor file; build it in a nonisolated static function')
print('isolation gate: ' + ('FAILED' if bad else 'ok'))
sys.exit(bad)
PY
