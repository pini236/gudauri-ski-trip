#!/bin/sh
# The ski-day gate (docs/ARCHITECTURE.md m-13): the built app's Info.plist must match the build.
#   check-ios-plist.sh <Gudi.app> plain    1.0 and main: no background location, no temporary-precision keys
#   check-ios-plist.sh <Gudi.app> dayrec   1.1: both present
set -e
APP="$1"; MODE="$2"; PL="$APP/Info.plist"
[ -f "$PL" ] || { echo "no Info.plist in $APP"; exit 2; }
modes=$(plutil -extract UIBackgroundModes json -o - "$PL" 2>/dev/null || echo "[]")
tmp=$(plutil -extract NSLocationTemporaryUsageDescriptionDictionary json -o - "$PL" 2>/dev/null || echo "")
has_loc=0; echo "$modes" | grep -q '"location"' && has_loc=1
has_tmp=0; [ -n "$tmp" ] && has_tmp=1
case "$MODE" in
  plain) [ $has_loc = 0 ] && [ $has_tmp = 0 ] || { echo "::error::a plain build declares background location or the temporary-precision key"; exit 1; } ;;
  dayrec) [ $has_loc = 1 ] && [ $has_tmp = 1 ] || { echo "::error::the dayrec build is missing background location or the temporary-precision key"; exit 1; } ;;
  *) echo "mode: plain|dayrec"; exit 2 ;;
esac
echo "Info.plist ok ($MODE)"
