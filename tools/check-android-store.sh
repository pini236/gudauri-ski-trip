#!/bin/sh
# Gate on the Android STORE build (manager, 6.11.2026; docs/OPERATIONS.md): a tester's raw-log export must not be switched
# on in a build that goes to Google Play. Reads the BuildConfig the store flavor generated. The ski day's screens
# (DAYREC_UI) were gated here too until 13.11.2026, when Pini released them to the store.
# usage: check-android-store.sh <generated BuildConfig.java of the store release build>
# Convention for the app sessions: a tester export is a BuildConfig boolean named TESTER_EXPORT (or RAW_LOG_EXPORT) that is
# true only in the preview flavor. A field with another name is not seen here: say so and the name is added.
set -u
F="$1"
[ -f "$F" ] || { echo "::error::no generated BuildConfig at $F"; exit 1; }
bad=0
for name in TESTER_EXPORT RAW_LOG_EXPORT; do
  if grep -q "boolean $name" "$F" && ! grep -q "boolean $name = false" "$F"; then
    echo "::error::$name is on in the store build"; bad=1
  fi
done
[ "$bad" = 0 ] && echo "store build: the tester export is off"
exit "$bad"
