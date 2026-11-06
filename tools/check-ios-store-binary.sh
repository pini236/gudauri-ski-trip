#!/bin/sh
# Gate on the iOS plain (store) build (manager, 6.11.2026): the compiled app must not carry the DAYREC code path or a
# tester's raw-log export. Looks at the strings in the executable. Convention: the tester export's type or key contains
# "TesterExport" or "RawLogExport"; the DAYREC code is the class DayRecorder, which `#if DAYREC` compiles out of plain.
# usage: check-ios-store-binary.sh <path to the app's executable>   (run only for the plain variant)
set -u
B="$1"
[ -f "$B" ] || { echo "::error::no executable at $B"; exit 1; }
bad=0
for pat in TesterExport RawLogExport DayRecorder; do
  if strings -a "$B" | grep -q "$pat"; then echo "::error::the plain build carries $pat"; bad=1; fi
done
# DayRecorder is allowed only as the empty stand-in: it must not reference the background-location switch
if strings -a "$B" | grep -q "allowsBackgroundLocationUpdates"; then echo "::error::the plain build carries background location code"; bad=1; fi
[ "$bad" = 0 ] && echo "plain build: no DAYREC code path and no tester export"
exit "$bad"
