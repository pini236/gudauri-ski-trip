#!/bin/sh
# Gate on the iOS plain (store) build (manager, 6.11.2026): the compiled app must not carry the DAYREC code path or a
# tester's raw-log export. Looks at the strings in the executable. Convention: the tester export's type or key contains
# "TesterExport" or "RawLogExport"; the DAYREC code is detected by the background-location calls only it makes (the empty DayRecorder stand-in stays in plain).
# usage: check-ios-store-binary.sh <path to the app's executable>   (run only for the plain variant)
set -u
B="$1"
[ -f "$B" ] || { echo "::error::no executable at $B"; exit 1; }
bad=0
S=$(strings -a "$B" 2>/dev/null)
# DayRecorder itself is NOT a pattern: the plain build keeps an empty stand-in with that name (#if DAYREC else branch).
# The real recorder is told apart by the location code it alone calls.
for pat in TesterExport RawLogExport allowsBackgroundLocationUpdates showsBackgroundLocationIndicator; do
  if printf '%s\n' "$S" | grep -q "$pat"; then echo "::error::the plain build carries $pat"; bad=1; fi
done
[ "$bad" = 0 ] && echo "plain build: no DAYREC code path and no tester export"
exit "$bad"
