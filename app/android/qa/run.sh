#!/usr/bin/env bash
# The emulator run (app/android/qa/README.md). Installs the debug build, drives it through the screens with
# "qa." extras (app/.../qa/Qa.kt) and adb input, and keeps screenshots, logs and numbers in $OUT.
# Runs inside reactivecircus/android-emulator-runner on GitHub, from the repo root.
set -uo pipefail

OUT=${OUT:-qa-out}
SCENARIO=${SCENARIO:-all}
PKG=io.github.pini236.skiapp.test
ACT=$PKG/io.github.pini236.skiapp.MainActivity
APK=app/android/app/build/outputs/apk/preview/debug/app-preview-debug.apk
RELEASE_APK=app/android/app/build/outputs/apk/preview/release/app-preview-release.apk
mkdir -p "$OUT/shots"
FAILS=0
n=0

note() { echo "$*" | tee -a "$OUT/summary.txt"; }
fail() { FAILS=$((FAILS + 1)); note "FAIL: $*"; }

shot() { # shot <name>: a numbered screenshot
  n=$((n + 1))
  local f; f=$(printf "%s/shots/%02d-%s.png" "$OUT" "$n" "$1")
  adb exec-out screencap -p > "$f" || fail "screenshot $1"
}
burst() { # burst <name> <count> <gap seconds>: frames of an animation
  for i in $(seq 1 "$2"); do shot "$1-$i"; sleep "$3"; done
}

# the app's own log lines (tag SkiQa) stream into a file; each step waits for its line after a mark
adb logcat -c
adb logcat -v time > "$OUT/logcat.txt" 2>&1 &
LOGCAT=$!
MARK=0
mark() { MARK=$(wc -l < "$OUT/logcat.txt"); }
waitlog() { # waitlog <text> [seconds]
  local deadline=$((SECONDS + ${2:-60}))
  while (( SECONDS < deadline )); do
    # grep reads to the end (no -q): with pipefail, an early exit would kill tail and fail the check
    tail -n +"$((MARK + 1))" "$OUT/logcat.txt" | grep "SkiQa.*$1" > /dev/null && return 0
    sleep 0.5
  done
  fail "no '$1' in the log after ${2:-60}s"; return 1
}
qa() { # qa <extras...>: one string for the device shell, values with spaces in single quotes
  mark
  adb shell "am start -W --activity-single-top -n $ACT $*" > /dev/null
}
tap() { adb shell input tap "$1" "$2"; }
hold() { adb shell input swipe "$1" "$2" "$1" "$2" "$3"; } # hold <x> <y> <ms>
drag() { adb shell input swipe "$1" "$2" "$3" "$4" "$5"; } # drag <x1> <y1> <x2> <y2> <ms>

read -r W H < <(adb shell wm size | sed -n 's/.*: \([0-9]*\)x\([0-9]*\).*/\1 \2/p' | tail -1)
DENSITY=$(adb shell wm density | sed -n 's/.*: \([0-9]*\).*/\1/p' | tail -1)
dp() { echo $(( $1 * DENSITY / 160 )); }
note "device: $(adb shell getprop ro.product.model | tr -d '\r') · Android $(adb shell getprop ro.build.version.release | tr -d '\r') · ${W}x${H} · ${DENSITY}dpi"
note "gpu: $(adb shell dumpsys SurfaceFlinger | grep -m1 'GLES:' | tr -d '\r')"

adb install -r -g "$APK" > /dev/null || { fail "install"; exit 1; }
# the test build carries all four languages (tools/build-app-strings.py); the run, and the store screenshots, are in
# Hebrew, the app's own language setting, whatever the emulator's language is
adb shell cmd locale set-app-locales "$PKG" --locales he > /dev/null 2>&1 || note "could not set the app's language"

# ---- the map ----
map() {
  mark
  adb shell "am start -W -n $ACT --es qa.tab map --es qa.time 2027-01-12T11:00" | tee -a "$OUT/summary.txt"
  waitlog "scene ready" 120 && waitlog "shadow ready" 120
  sleep 2; shot map-overview-11h
  grep "SkiQa.*shadow ready" "$OUT/logcat.txt" | tail -1 | sed 's/.*SkiQa[^:]*: /startup: /' | tee -a "$OUT/summary.txt"

  # gestures: the app plays them as real touches (the emulator's input has one finger) and logs the camera
  RESET="--es qa.cam '600,-500,11000,20,36'"
  for g in pan pinch-out pinch-in turn tilt-up tilt-down double-tap two-tap; do
    qa "$RESET"; sleep 1
    qa "--es qa.gesture $g"
    waitlog "gesture $g done" 15 && grep "SkiQa.*gesture $g done" "$OUT/logcat.txt" | tail -1 | sed 's/.*SkiQa[^:]*: //' | tee -a "$OUT/summary.txt"
    sleep 0.5; shot "map-gesture-$g"
  done
  qa "$RESET"; sleep 1.5; shot map-camera-reset

  # runs: the reveal, the landing on the whole run and the sign
  for run in "Tatra 2" "Kudebi 1" "Sadzele 2" "Goodaura 1" "Snow Park"; do
    local slug=${run// /-}
    qa "--es qa.run '$run'"
    if waitlog "selected $run" 30; then
      burst "run-$slug" 3 0.45
      sleep 1.5; shot "run-$slug-settled"
    fi
  done
  # the system closes the app in the background: it must come back to the same run (nav/Nav.kt)
  # (am kill does not close it on Android 15; the debug app's own user can kill its process, as the system does)
  adb shell input keyevent KEYCODE_HOME; sleep 2
  local pid; pid=$(adb shell pidof "$PKG" | tr -d '\r')
  [ -n "$pid" ] && adb shell run-as "$PKG" kill -9 "$pid"; sleep 1
  if adb shell pidof "$PKG" > /dev/null; then fail "the app was not closed in the background"; fi
  mark; adb shell "am start -W -n $ACT" > /dev/null
  waitlog "scene ready" 120 && waitlog "restored run Snow Park" 60 && note "restored after the system closed it: Snow Park"
  sleep 1.5; shot map-restored
  qa "--es qa.run none"; waitlog "selected none" 10; sleep 1.5; shot map-cleared

  # the fly-down: the skier's dot and the bar along the way, then the stop
  qa "--es qa.run 'Tatra 2' --ez qa.fly true"
  waitlog "fly started" 30 && burst fly-tatra-2 6 3
  qa "--es qa.run none"; sleep 1

  # the light through a day in January: dawn, noon, the low afternoon sun, sunset, night with the moon
  for t in 09:30/sun 12:30/- 16:30/sun 17:10/- 20:00/- 21:00/moon; do
    local at=${t%/*} face=${t#*/} extra=""
    [ "$face" != "-" ] && extra="--es qa.face $face"
    qa "--es qa.time 2027-01-12T$at $RESET $extra"
    waitlog "shadow ready" 120; sleep 1.5; shot "map-light-${at/:/}${extra:+-$face}"
  done
  qa "--es qa.time 2027-01-12T12:30 $RESET"; waitlog "shadow ready" 120
}

# ---- the descent game ----
descent() {
  qa "--es qa.tab descent"; sleep 3; shot descent-start
  for i in 1 2 3; do hold $((W / 2)) $((H / 2)) 700; sleep 0.6; shot "descent-jump-$i"; done
  hold $((W / 2)) $((H / 2)) 300; sleep 0.15; hold $((W / 2)) $((H / 2)) 1600; shot descent-flip
  sleep 8; shot descent-later
}

# ---- the boarding pass ----
ticket() {
  qa "--es qa.tab ticket"; sleep 2; shot ticket
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null && adb pull /sdcard/ui.xml "$OUT/ticket-ui.xml" > /dev/null
  # the ticket canvas starts 18dp under the hint line; the stub is its left 30%
  local top
  top=$(python3 - "$OUT/ticket-ui.xml" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding="utf-8").read()
m = re.search(r'text="כרטיס לדוגמה[^"]*"[^>]*bounds="\[\d+,\d+\]\[\d+,(\d+)\]"', xml)
print(m.group(1) if m else "")
PY
)
  if [ -z "$top" ]; then fail "ticket hint not found"; return; fi
  local x=$(( $(dp 16) + (W - $(dp 32)) * 12 / 100 ))
  local y0=$(( top + $(dp 18) + $(dp 20) ))
  drag "$x" "$y0" "$x" $(( y0 + $(dp 90) )) 600; sleep 0.3; shot ticket-half-tear
  sleep 1.5; shot ticket-sprang-back
  drag "$x" "$y0" "$x" $(( y0 + $(dp 220) )) 700; sleep 0.25; burst ticket-torn 3 0.4
  sleep 2; shot ticket-back
  tap $((W / 2)) $(( top + $(dp 18) + $(dp 230) + $(dp 18) + $(dp 85) )); sleep 0.3; shot ticket-sign-wobble
}

# ---- the store screenshots (Google Play: portrait 9:16) ----
# a 1080x1920 screen, a clean status bar (demo mode), no measuring bar; the files go to $OUT/store
store() {
  mkdir -p "$OUT/store"
  local k=0
  sshot() { k=$((k + 1)); adb exec-out screencap -p > "$(printf "%s/store/store-%d-%s.png" "$OUT" "$k" "$1")" || fail "store shot $1"; }
  adb shell wm size 1080x1920
  local W=1080 H=1920
  adb shell settings put global sysui_demo_allowed 1
  adb shell am broadcast -a com.android.systemui.demo -e command enter > /dev/null
  adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0930 > /dev/null
  adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false > /dev/null
  adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e mobile hide > /dev/null
  adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false > /dev/null
  adb shell am force-stop "$PKG"
  mark
  adb shell "am start -W -n $ACT --es qa.tab map --es qa.stats off --es qa.time 2027-01-12T11:00" > /dev/null
  waitlog "scene ready" 120 && waitlog "shadow ready" 120
  sleep 2; sshot map
  qa "--es qa.run 'Tatra 2'"; waitlog "selected Tatra 2" 30; sleep 3; sshot run
  qa "--ez qa.fly true"; waitlog "fly started" 30; sleep 7; sshot fly
  qa "--es qa.run none"; sleep 1
  qa "--es qa.time 2027-01-12T16:40 --es qa.cam '600,-500,11000,20,36' --es qa.face sun"; waitlog "shadow ready" 120; sleep 1.5; sshot sunset
  qa "--es qa.time 2027-01-12T21:00 --es qa.cam '600,-500,11000,20,36' --es qa.face moon"; waitlog "shadow ready" 120; sleep 1.5; sshot night
  qa "--es qa.time 2027-01-12T11:00"; waitlog "shadow ready" 120
  qa "--es qa.tab descent"; sleep 3
  sleep 2; hold $((W / 2)) $((H / 2)) 700; sleep 0.5; sshot descent
  qa "--es qa.tab ticket"; sleep 2; sshot ticket
  adb shell am broadcast -a com.android.systemui.demo -e command exit > /dev/null
  adb shell wm size reset
}

case "$SCENARIO" in
  map) map ;;
  descent) descent ;;
  ticket) ticket ;;
  store) store ;;
  *) map; descent; ticket; store ;;
esac

# ---- what the run measured ----
adb shell dumpsys gfxinfo "$PKG" > "$OUT/gfxinfo.txt"
grep "SkiQa" "$OUT/logcat.txt" > "$OUT/qa-log.txt"
# crashes anywhere, and every error line the app itself wrote
grep -E "FATAL EXCEPTION|ANR in $PKG" -A 30 "$OUT/logcat.txt" > "$OUT/errors.txt"
PID=$(adb shell pidof "$PKG" | tr -d '\r')
# known noise: the system's note when the map's surface changes size (the bars appear), not an app error
if [ -n "$PID" ]; then adb logcat -d --pid="$PID" '*:E' | grep -v -E "^-+ beginning of|BLASTBufferQueue.*rejecting buffer" >> "$OUT/errors.txt"; else fail "the app is not running at the end"; fi
[ -s "$OUT/errors.txt" ] && fail "errors in the log (errors.txt)"

# ---- the release build (R8): only that it starts and draws ----
if [ -f "$RELEASE_APK" ]; then
  adb uninstall "$PKG" > /dev/null
  if adb install -r "$RELEASE_APK" > /dev/null; then
    mark; adb shell am start -W -n "$ACT" > /dev/null; sleep 12; shot release-start
    adb shell pidof "$PKG" > /dev/null && note "release build: running" || fail "release build crashed"
    tail -n +"$((MARK + 1))" "$OUT/logcat.txt" | grep -E "FATAL EXCEPTION|AndroidRuntime: " >> "$OUT/errors.txt" && fail "release build errors"
  else
    fail "release install"
  fi
fi

kill "$LOGCAT" 2> /dev/null
note "screenshots: $n · failures: $FAILS"
exit 0
