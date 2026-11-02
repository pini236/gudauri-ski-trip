#!/bin/sh
# Gate on the COMPILED app's Info.plist (architect, docs/ARCHITECTURE.md, m-13 condition 4), not on the yml.
# usage: check-ios-variant.sh <Info.plist inside the .app> <plain|dayrec>
# plain: fails if background location or the temporary-location key is present (build 1.0).
# dayrec:   fails if either is missing, or if the marketing version is still 1.0.
set -u
PLIST="$1"; VARIANT="$2"
[ -f "$PLIST" ] || { echo "::error::no Info.plist at $PLIST"; exit 1; }
has_bg=no
if plutil -extract UIBackgroundModes json -o - "$PLIST" 2>/dev/null | grep -q '"location"'; then has_bg=yes; fi
has_tmp=no
if plutil -extract NSLocationTemporaryUsageDescriptionDictionary json -o - "$PLIST" >/dev/null 2>&1; then has_tmp=yes; fi
echo "variant=$VARIANT background-location=$has_bg temporary-location=$has_tmp"
case "$VARIANT" in
  plain)
    [ "$has_bg" = no ] && [ "$has_tmp" = no ] || { echo "::error::a plain (1.0) build must not carry background location or the temporary-location key"; exit 1; } ;;
  dayrec)
    [ "$has_bg" = yes ] && [ "$has_tmp" = yes ] || { echo "::error::a dayrec (1.1) build must carry both keys"; exit 1; }
    V=$(plutil -extract CFBundleShortVersionString raw -o - "$PLIST" 2>/dev/null || echo "")
    echo "marketing version: $V"
    [ -n "$V" ] && [ "$V" != "1.0" ] || { echo "::error::a dayrec build needs its own marketing version, not 1.0"; exit 1; } ;;
  *) echo "::error::unknown variant $VARIANT"; exit 1 ;;
esac
