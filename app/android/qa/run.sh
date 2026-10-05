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
: > "$OUT/logcat.txt"
logcat_on() { # stream the log into the file; after a break (adb drops it now and then), go on from its last line
  local since; since=$(tail -n 1 "$OUT/logcat.txt" | cut -c1-18)
  if [[ "$since" =~ ^[0-9]{2}-[0-9]{2}\ [0-9:.]{12}$ ]]; then adb logcat -v time -T "$since" >> "$OUT/logcat.txt" 2>&1 &
  else adb logcat -v time >> "$OUT/logcat.txt" 2>&1 &
  fi
  LOGCAT=$!
}
logcat_on
MARK=0
mark() { MARK=$(wc -l < "$OUT/logcat.txt"); }
waitlog() { # waitlog <text> [seconds]
  local deadline=$((SECONDS + ${2:-60}))
  while (( SECONDS < deadline )); do
    kill -0 "$LOGCAT" 2> /dev/null || { note "log stream broke: started again"; logcat_on; }
    # grep reads to the end (no -q): with pipefail, an early exit would kill tail and fail the check
    tail -n +"$((MARK + 1))" "$OUT/logcat.txt" | grep "SkiQa.*$1" > /dev/null && return 0
    sleep 0.5
  done
  fail "no '$1' in the log after ${2:-60}s"; return 1
}
seen() { # seen <text> [seconds]: as waitlog, but a miss is no failure (the caller tries again)
  local deadline=$((SECONDS + ${2:-30}))
  while (( SECONDS < deadline )); do
    tail -n +"$((MARK + 1))" "$OUT/logcat.txt" | grep "SkiQa.*$1" > /dev/null && return 0
    sleep 0.5
  done
  return 1
}
qa() { # qa <extras...>: one string for the device shell, values with spaces in single quotes
  mark
  adb shell "am start -W --activity-single-top -n $ACT $*" > /dev/null
}
tap() { adb shell input tap "$1" "$2"; }
hold() { adb shell input swipe "$1" "$2" "$1" "$2" "$3"; } # hold <x> <y> <ms>
drag() { adb shell input swipe "$1" "$2" "$3" "$4" "$5"; } # drag <x1> <y1> <x2> <y2> <ms>
# a page is scrolled near its edge (x 7%, past the system back gesture), where nothing is tappable: a drag over a sign or a link can land as a tap
# while the page is not yet scrollable (main runs 88 to 102: the games sign, a link in "what's ahead")

read -r W H < <(adb shell wm size | sed -n 's/.*: \([0-9]*\)x\([0-9]*\).*/\1 \2/p' | tail -1)
DENSITY=$(adb shell wm density | sed -n 's/.*: \([0-9]*\).*/\1/p' | tail -1)
dp() { echo $(( $1 * DENSITY / 160 )); }
note "device: $(adb shell getprop ro.product.model | tr -d '\r') · Android $(adb shell getprop ro.build.version.release | tr -d '\r') · ${W}x${H} · ${DENSITY}dpi"
note "gpu: $(adb shell dumpsys SurfaceFlinger | grep -m1 'GLES:' | tr -d '\r')"
# no "isn't responding" dialogs from the system's own apps on the slow emulator (unblock() handles any that still show)
adb shell settings put global hide_error_dialogs 1 > /dev/null 2>&1 || true

adb install -r -g "$APK" > /dev/null || { fail "install"; exit 1; }
# the test build carries all four languages (tools/build-app-strings.py); the run, and the store screenshots, are in
# Hebrew, the app's own language setting, whatever the emulator's language is
adb shell cmd locale set-app-locales "$PKG" --locales he > /dev/null 2>&1 || note "could not set the app's language"

# ---- the map ----
map() {
  # from a cold start, whatever ran before (a running app drops a plain start's extras)
  adb shell am force-stop "$PKG"; sleep 1
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
  # the system closes an app only after it saved its state: wait for that before the kill (a slow emulator may not
  # have stopped the app yet two seconds after Home)
  mark; adb shell input keyevent KEYCODE_HOME
  waitlog "state saved" 30; sleep 1
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

  # the map from above (A-30): the whole main side, a chosen run in slope colours over its ground, the Kobi side, the
  # whole map again; then back to 3D, and the compass turns it to look north
  mark; qa "--es qa.mapview 2d"; waitlog "map view 2d" 10; waitlog "overview fit" 60; sleep 2.5; shot top-all
  mark; qa "--es qa.run 'Tatra 2'"; waitlog "overview frame Tatra 2" 20; sleep 3; shot top-run
  qa "--es qa.run none"; waitlog "selected none" 10; sleep 1
  mark; tapText "צד Kobi" && waitlog "overview kobi" 10 && { sleep 1.5; shot top-kobi; }
  tapText "הצג הכל" && { sleep 1.5; shot top-fit; }
  mark; tapText "תלת-ממד" && waitlog "map view 3d" 10
  qa "$RESET"; sleep 1; mark; tapText "סיבוב חזרה למבט מדרום" && waitlog "map north" 5 && { sleep 1.5; shot map-north; }
}

scrollDown() { drag $((W * 7 / 100)) $((H * 80 / 100)) $((W * 7 / 100)) $((H * 30 / 100)) 400; sleep .8; }
# ---- the descent (13.6, the site's game): the coats and the runs, the count, jumps and a flip, the bottom, the result ----
descent() {
  qa "--es qa.group member --es qa.trip none --es qa.tab descent"; sleep 3; shot descent-menu
  [ -n "$(where "המעיל שלך")" ] || fail "no coat picker in the descent's menu"
  [ -n "$(where "Tatra 2")" ] || fail "no runs in the descent's menu"
  scrollDown; shot descent-menu-bottom
  mark; qa "--es qa.descent go"; sleep 1.2; shot descent-count
  waitlog "descent start" 3
  sleep 3; shot descent-start
  for i in 1 2 3; do hold $((W / 2)) $((H / 2)) 700; sleep 0.6; shot "descent-jump-$i"; done
  hold $((W / 2)) $((H / 2)) 300; sleep 0.15; hold $((W / 2)) $((H / 2)) 1600; shot descent-flip
  sleep 6; shot descent-later
  # straight to the bottom: the lift, the result, the copy for the group
  mark; qa "--es qa.descent bottom"; sleep 4.5; shot descent-end
  waitlog "descent done" 6
  [ -n "$(where "עוד ירידה")" ] || fail "no 'another run' after the descent"
  tapText "העתקת התוצאה לקבוצה" && { sleep .8; shot descent-copied; waitlog "descent copied" 3; }
  adb shell input keyevent KEYCODE_BACK; sleep 1
  [ -n "$(where "המעיל שלך")" ] || fail "back from the result is not the descent's menu"
  adb shell input keyevent KEYCODE_BACK; sleep 1.5
  [ -n "$(where "הירידה של החבר׳ה")" ] || fail "back from the descent is not the games page"
  # left to right: the run's sign and the time swap sides, the crew stays on the right
  adb shell cmd locale set-app-locales "$PKG" --locales en > /dev/null 2>&1; sleep 3
  qa "--es qa.tab descent"; sleep 2; shot descent-menu-en
  qa "--es qa.descent go"; sleep 6; shot descent-en
  adb shell cmd locale set-app-locales "$PKG" --locales he > /dev/null 2>&1; sleep 3
  qa "--es qa.tab home"; sleep 1
}

# ---- the games page (13.6, the site's #games, GP2) and the merging game ----
games() {
  # in a group on the pretend server, so a new best goes to the group's table
  qa "--es qa.group member --es qa.tab home --es qa.trip none"; sleep 3
  drag $((W * 7 / 100)) $((H * 80 / 100)) $((W * 7 / 100)) $((H * 35 / 100)) 400; sleep 1
  if tapText "משחקים"; then sleep 2; shot games-page; else fail "no games sign on home"; fi
  tapText "איחוד כדורי שלג" && { sleep 2; shot merge-start; }
  # a swipe anywhere moves the board: a few, then undo
  mark; local d; for d in L U R D L D; do
    case $d in L) drag $((W * 75 / 100)) $((H / 2)) $((W * 25 / 100)) $((H / 2)) 150 ;; R) drag $((W * 25 / 100)) $((H / 2)) $((W * 75 / 100)) $((H / 2)) 150 ;;
      U) drag $((W / 2)) $((H * 60 / 100)) $((W / 2)) $((H * 35 / 100)) 150 ;; D) drag $((W / 2)) $((H * 35 / 100)) $((W / 2)) $((H * 60 / 100)) 150 ;; esac
    sleep 0.5
  done
  sleep 0.4; shot merge-moved
  waitlog "merge moved" 3
  tapText "ביטול מהלך" && { sleep 0.8; shot merge-undo; }
  # one swipe from a whole snowman: the merge, the puff and the points, then "Snowman!"; the best goes to the group
  qa "--es qa.tab home"; sleep 1
  mark; qa "--es qa.merge near --es qa.tab merge"; sleep 2; shot merge-near
  drag $((W * 75 / 100)) $((H / 2)) $((W * 25 / 100)) $((H / 2)) 150; sleep 0.25; shot merge-merging
  sleep 1.2; shot merge-won
  [ -n "$(where "איש שלג!")" ] || fail "no 'Snowman!' after a whole snowman"
  waitlog "score sent merge" 10 # the new best, to the group's table
  tapText "להמשיך" && { sleep 1; shot merge-go-on; }
  adb shell input keyevent KEYCODE_BACK; sleep 1.5; shot games-back
  [ -n "$(where "איחוד כדורי שלג")" ] || fail "back from the game is not the games page"
  # fresh snow (13.6): a finger, a boot and the snowcat on the snow, the frozen crust, new snow; then the frozen lake
  mark; tapText "שלג טרי" && { sleep 2; shot fresh-start; }
  drag $((W * 20 / 100)) $((H * 30 / 100)) $((W * 80 / 100)) $((H * 42 / 100)) 700; drag $((W * 70 / 100)) $((H * 25 / 100)) $((W * 30 / 100)) $((H * 55 / 100)) 700
  sleep 0.6; shot fresh-finger; waitlog "fresh snow" 5
  tapText "מגף" && { drag $((W * 25 / 100)) $((H * 60 / 100)) $((W * 75 / 100)) $((H * 35 / 100)) 1500; sleep 0.5; shot fresh-boot; }
  tapText "חתול שלג" && { drag $((W * 12 / 100)) $((H * 48 / 100)) $((W * 88 / 100)) $((H * 48 / 100)) 1200; sleep 0.2; shot fresh-snowcat; sleep 1.4; shot fresh-corduroy; }
  tapText "קרום קפוא" && tapText "כדור" && { drag $((W * 30 / 100)) $((H * 30 / 100)) $((W * 60 / 100)) $((H * 50 / 100)) 900; sleep 0.5; shot fresh-crust; }
  tapText "שלג חדש" && { sleep 0.8; shot fresh-falling; sleep 2.5; shot fresh-new-snow; }
  mark; tapText "אגם קפוא" && { sleep 2; shot fresh-lake; }
  local k; for k in 1 2 3 4 5 6 7 8; do tap $((W * (25 + (k * 37) % 50) / 100)) $((H * (28 + (k * 23) % 34) / 100)); sleep 0.25; done
  shot fresh-lake-hits; sleep 1.5; shot fresh-lake-broken; waitlog "fresh lake" 5
  tapText "קפיאה מחדש" && { sleep 1.5; shot fresh-refreeze; }
  adb shell input keyevent KEYCODE_BACK; sleep 1.5
  [ -n "$(where "איחוד כדורי שלג")" ] || fail "back from fresh snow is not the games page"
  qa "--es qa.mode night"; sleep 1.5; shot games-night
  qa "--es qa.mode auto"
  # left to right: the post on the left, the signs point right, the board does not flip
  adb shell cmd locale set-app-locales "$PKG" --locales en > /dev/null 2>&1; sleep 3
  qa "--es qa.tab games"; sleep 2; shot games-en
  qa "--es qa.merge near --es qa.tab merge"; sleep 2; shot merge-en
  qa "--es qa.tab fresh"; sleep 2; drag $((W * 20 / 100)) $((H * 35 / 100)) $((W * 80 / 100)) $((H * 45 / 100)) 700; sleep 0.5; shot fresh-en
  adb shell cmd locale set-app-locales "$PKG" --locales he > /dev/null 2>&1; sleep 3
  qa "--es qa.tab home"; sleep 1
}

# ---- the ski school (13.6): the lessons, a brief, the wedge on the slope, the result; then the skis, the beat, carving and the final test ----
school() {
  qa "--es qa.group member --es qa.trip none --es qa.tab school"; sleep 3; shot school-menu
  [ -n "$(where "עוצרים בפיצה")" ] || fail "no first lesson in the school's menu"
  tapText "עוצרים בפיצה" && { sleep 1; shot school-brief; }
  mark; tapText "לשלג!" && { sleep 2.5; shot school-wedge; }
  waitlog "school start 1" 3
  # the finger low on the slope: a wide wedge, and the skier stops
  hold $((W / 2)) $((H * 80 / 100)) 2500; shot school-wedge-wide
  waitlog "coach_wide_pizza" 3
  # the lesson ended at once: no stop, no star, "try again"
  mark; qa "--es qa.school finish"; sleep 1.5; shot school-result
  waitlog "school done 1 0 stars" 3
  [ -n "$(where "לנסות שוב")" ] || fail "no 'try again' after a lesson with no star"
  tapText "לכל השיעורים" && sleep 1
  # every lesson open: the two skis (the first gate is on the left, so the left ski is the wrong one)
  # from home, so the school's page opens again and reads the stars
  qa "--es qa.tab home"; sleep 1
  qa "--es qa.school open --es qa.tab school"; sleep 2; shot school-menu-open
  tapText "פונים בפיצה" && tapText "לשלג!" && sleep 1.5
  mark; tapText "מגלש שמאל"; waitlog "coach_wrong_right" 3
  local xy; xy=$(where "מגלש ימין"); [ -n "$xy" ] && { hold $xy 1800; shot school-skis; }
  waitlog "coach_correct_left" 3
  adb shell input keyevent KEYCODE_BACK; sleep 1
  [ -n "$(where "פונים בפיצה")" ] || fail "back from a lesson is not the school's menu"
  # the beat: taps anywhere, graded
  tapText "מקבילי, בקצב" && tapText "לשלג!" && sleep 2
  mark; local k; for k in 1 2 3 4 5; do tap $((W / 2)) $((H * 55 / 100)); sleep 1.15; done
  shot school-rhythm; seen "beat_" 2 || fail "no grade for a tap on the beat"
  adb shell input keyevent KEYCODE_BACK; sleep 1
  # carving: the finger leans the skis; the gauge at the bottom
  tapText "קרווינג" && tapText "לשלג!" && sleep 1.5
  drag $((W * 30 / 100)) $((H / 2)) $((W * 70 / 100)) $((H / 2)) 1500; drag $((W * 70 / 100)) $((H / 2)) $((W * 30 / 100)) $((H / 2)) 1500
  shot school-carve
  adb shell input keyevent KEYCODE_BACK; sleep 1
  # the final test: gates, other skiers and the wedge button
  tapText "מבחן סיום" && { sleep 1; shot school-final-brief; tapText "לשלג!"; sleep 3; shot school-final; }
  xy=$(where "פיצה"); [ -n "$xy" ] && { hold $xy 1200; shot school-final-wedge; }
  adb shell input keyevent KEYCODE_BACK; sleep 1
  adb shell input keyevent KEYCODE_BACK; sleep 1.5
  [ -n "$(where "בית הספר לסקי")" ] || fail "back from the school is not the games page"
  # left to right: the sign points right, the skis stay where they are, the checklist stays on the right
  adb shell cmd locale set-app-locales "$PKG" --locales en > /dev/null 2>&1; sleep 3
  qa "--es qa.tab school"; sleep 2; shot school-menu-en
  tapText "Stopping in a pizza" && { sleep 1; shot school-brief-en; tapText "To the snow!"; sleep 2.5; shot school-wedge-en; }
  adb shell input keyevent KEYCODE_BACK; sleep 1
  tapText "Turning in a pizza" && tapText "To the snow!" && { sleep 2; shot school-skis-en; }
  adb shell cmd locale set-app-locales "$PKG" --locales he > /dev/null 2>&1; sleep 3
  qa "--es qa.tab home"; sleep 1
}

# ---- the snowball fight (13.6): the coats, the ladder, a throw, the result, back, the summit, left to right ----
snowball() {
  qa "--es qa.group member --es qa.trip none --es qa.tab snowball"; sleep 3; shot snowball-menu
  [ -n "$(where "המעיל שלך")" ] || fail "no coat picker in the snowball fight's menu"
  [ -n "$(where "בחצר המלון")" ] || fail "no first rung on the snowball fight's ladder"
  tapText "כחול" && { sleep .6; shot snowball-coat; }
  scrollDown; shot snowball-menu-bottom
  mark; tapText "לקרב!" && { sleep 2; shot snowball-fight; }
  waitlog "snowball start 1" 3
  # a finger in the middle: up over the wall with the ring and the arc, then the throw
  hold $((W / 2)) $((H * 50 / 100)) 1800 & local hp=$!; sleep 1.2; shot snowball-aim; wait $hp
  waitlog "snowball pop" 4
  # down behind the wall a while: their balls come
  sleep 4; shot snowball-later
  # the fight won at once: the result
  mark; qa "--es qa.snowball win"; sleep 3.5; shot snowball-won
  waitlog "snowball done 1 won" 4
  [ -n "$(where "עוד סיבוב")" ] || fail "no 'another round' after a won fight"
  [ -n "$(where "השלב הבא")" ] || fail "no 'next rung' after a won fight"
  tapText "לתפריט" && { sleep 1; shot snowball-menu-star; }
  # back from a fight is the menu
  scrollDown; tapText "לקרב!" && sleep 1.5; adb shell input keyevent KEYCODE_BACK; sleep 1
  [ -n "$(where "המעיל שלך")" ] || fail "back from a fight is not the snowball menu"
  # every rung open: the summit, three of them in the wind
  qa "--es qa.tab home"; sleep 1
  qa "--es qa.snowball open --es qa.tab snowball"; sleep 2
  scrollDown; tapText "בפסגה, ברוח" && { sleep .5; tapText "לקרב!"; sleep 2.5; shot snowball-summit; }
  adb shell input keyevent KEYCODE_BACK; sleep 1
  adb shell input keyevent KEYCODE_BACK; sleep 1.5
  [ -n "$(where "קרב כדורי שלג")" ] || fail "back from the snowball fight is not the games page"
  # left to right: the same field, the tags where they were
  adb shell cmd locale set-app-locales "$PKG" --locales en > /dev/null 2>&1; sleep 3
  qa "--es qa.tab snowball"; sleep 2; shot snowball-menu-en
  scrollDown; tapText "Fight!" && { sleep 2.5; shot snowball-fight-en; }
  adb shell cmd locale set-app-locales "$PKG" --locales he > /dev/null 2>&1; sleep 3
  qa "--es qa.tab home"; sleep 1
}

# ---- the home page (round 10: H1 to H4, LT1 to LT3) ----
# what is on screen: the accessibility tree (uiautomator), so a step can tap a button by its words
uidump() { # never an old tree: the tool gives up on a screen that is still busy ("could not get idle state"), so try again
  local i
  for i in 1 2 3; do
    adb shell rm -f /sdcard/ui.xml
    adb shell uiautomator dump /sdcard/ui.xml 2>&1 | grep -q "dumped to" && adb pull /sdcard/ui.xml "$OUT/ui.xml" > /dev/null && { unblock >&2; return 0; } # where() prints only its x y
    sleep 1
  done
  : > "$OUT/ui.xml"; return 1
}
# a system "isn't responding" dialog (the emulator's launcher, now and then) covers the app: wait it out and go on,
# and count it as a failure only when it is the app's own
unblock() {
  local hit; hit=$(python3 - "$OUT/ui.xml" <<'PY'
import re, sys
import xml.etree.ElementTree as ET
nodes = list(ET.parse(sys.argv[1]).getroot().iter())
title = next((n.get("text") for n in nodes if "responding" in (n.get("text") or "")), None)
wait = next((n for n in nodes if n.get("resource-id") == "android:id/aerr_wait"), None)
if title and wait is not None:
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", wait.get("bounds")))
    print((x1 + x2) // 2, (y1 + y2) // 2, title)
PY
)
  [ -z "$hit" ] && return 0
  set -- $hit; local x=$1 y=$2; shift 2
  case "$*" in *Launcher*|*"System UI"*) note "system dialog closed: $*" ;; *) fail "not responding: $*" ;; esac
  tap "$x" "$y"; sleep 2
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null && adb pull /sdcard/ui.xml "$OUT/ui.xml" > /dev/null
}
where() { # where <text>: the middle ("x y") of the element whose text or description is it (else holds it), a tappable one first; "~<regex>" searches; never a disabled one
  uidump
  python3 - "$OUT/ui.xml" "$1" <<'PY'
import re, sys
import xml.etree.ElementTree as ET
t = sys.argv[2]
found = []
def walk(n, tappable):
    tap = tappable or n.get("clickable") == "true"
    got = [g.strip() for g in (n.get("text") or "", n.get("content-desc") or "") if g.strip()]
    b = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds") or "")
    if b and got and n.get("enabled") != "false":
        if t.startswith("~"): rank = 1 if any(re.search(t[1:], g) for g in got) else 9
        else: rank = 0 if t in got else 1 if any(t in g for g in got) else 9
        if rank < 9: found.append((rank, 0 if tap else 1, len(found), tuple(map(int, b.groups()))))
    for c in n: walk(c, tap)
try: root = ET.parse(sys.argv[1]).getroot()
except ET.ParseError: sys.exit(0)
walk(root, False)
if found:
    x1, y1, x2, y2 = min(found)[3]; print((x1 + x2) // 2, (y1 + y2) // 2)
PY
}
tapText() { # waits up to about 3 s for the words to show
  local xy i
  for i in 1 2 3; do xy=$(where "$1"); [ -n "$xy" ] && break; sleep 1; done
  if [ -z "$xy" ]; then fail "no '$1' on screen"; return 1; fi
  tap $xy
}

# the trips the run sets (never packed in the app, decision 27): made-up flight numbers, never the group's flight
TRIP_HE='{"v":1,"out":{"date":"2027-01-10","flight":"GD 101","from":"TLV · תל אביב","to":"TBS · טביליסי","departs":"16:00","arrives":"20:35"},"ret":{"date":"2027-01-15","flight":"GD 102","from":"TBS · טביליסי","to":"TLV · תל אביב","departs":"01:35","arrives":"02:15"}}'
TRIP_EN='{"v":1,"out":{"date":"2027-01-10","flight":"GD 101","from":"TLV · Tel Aviv","to":"TBS · Tbilisi","departs":"16:00","arrives":"20:35"},"ret":{"date":"2027-01-15","flight":"GD 102","from":"TBS · Tbilisi","to":"TLV · Tel Aviv","departs":"01:35","arrives":"02:15"}}'

home() {
  # a guest with no trip, early December at midday in Gudauri (H1)
  qa "--es qa.tab home --es qa.trip none --es qa.mode auto --es qa.time 2026-12-01T13:35"; waitlog "trip none" 20
  sleep 2; shot home-guest
  drag $((W / 2)) $((H * 3 / 4)) $((W / 2)) $((H / 4)) 400; sleep 1; shot home-guest-signs
  drag $((W / 2)) $((H / 4)) $((W / 2)) $((H * 3 / 4)) 300; sleep 0.5

  # add a trip by hand: only the date is required (H2)
  tapText "הוספת הטיסה שלי" && sleep 1.5 && shot trip-form-empty
  # nothing typed but flight numbers: the date from the calendar, a time from the clock, the airport from the list
  tapText "בחירת תאריך" && sleep 1.2 && shot trip-date-picker && tapText "מעבר לחודש הבא" && sleep 0.8 && tapText "~(?<!\d)20(?!\d)" && sleep 0.5 && shot trip-date-chosen && tapText "בחירה" && sleep 1
  tapText "בחירת שעה" && sleep 1.2 && shot trip-time-picker && tapText "בחירה" && sleep 1
  tapText "בחירת שדה" && sleep 1.2 && shot trip-place-sheet && tapText "TLV" && sleep 1
  shot trip-form-picked
  mark; tapText "שמירה" && waitlog "trip saved" 10 && { sleep 1.5; shot home-trip-picked; }

  # the whole trip (H3), day and night (H4), the tear and the swipe to the return pass
  qa "--es qa.trip '$TRIP_HE'"; waitlog "trip set" 20; sleep 1.5; shot home-trip-day
  local xy; xy=$(where "לתלוש את הספח")
  if [ -n "$xy" ]; then
    set -- $xy
    tap "$1" "$2"; burst home-tear 4 0.25; sleep 2.5; shot home-tear-back
    # the return pass peeks from behind (AB5, AB6): a tap on it brings it to the front, and the swipe still works
    tapText "להביא קדימה את כרטיס החזור" && { burst home-shuffle 4 0.15; sleep 1; shot home-return-pass; }
    drag $((W * 80 / 100)) "$2" $((W * 25 / 100)) "$2" 300; sleep 1.2; shot home-swipe-back
  else fail "no stub on the pass"; fi
  qa "--es qa.mode night"; sleep 2; shot home-trip-night
  qa "--es qa.mode auto --es qa.time 2026-12-01T16:50"; sleep 2; shot home-trip-sunset
  # during the trip the stub says which ski day it is, and after the return it has no count (A-43)
  qa "--es qa.mode auto --es qa.time 2027-01-12T10:00"; sleep 2; shot home-trip-skiday
  qa "--es qa.mode auto --es qa.time 2027-01-16T10:00"; sleep 2; shot home-trip-over
  qa "--es qa.mode auto --es qa.time 2026-12-01T13:35"; sleep 1
  tapText "עריכה" && sleep 1.5 && shot trip-form-filled && adb shell input keyevent KEYCODE_BACK && sleep 1

  # the signs lead on, and back home
  tapText "מפת מסלולים" && sleep 3 && shot home-sign-map
  tapText "בית" && sleep 1.5 && shot home-back
  tapText "נקודת מפגש" && sleep 1 && shot home-sign-meet && adb shell input keyevent KEYCODE_BACK && sleep 1
  # home below the signs: the tally of runs and the two links (A-32)
  drag $((W * 7 / 100)) $((H * 80 / 100)) $((W * 7 / 100)) $((H * 20 / 100)) 400; sleep 1; shot home-bottom
  drag $((W / 2)) $((H * 20 / 100)) $((W / 2)) $((H * 80 / 100)) 300; drag $((W / 2)) $((H * 20 / 100)) $((W / 2)) $((H * 80 / 100)) 300; sleep 1
  # about and settings (13.7): the account's pass, the settings, who built it, the credits
  if tapText "אודות והגדרות"; then
    sleep 1; shot home-about
    # the reset asks twice: armed while it is on screen, before the credits scroll it away
    tapText "איפוס השיאים במשחקים" && sleep 0.5 && shot home-about-reset-armed
    # slow drags: a quick one from the edge flings the page to its end
    drag $((W * 7 / 100)) $((H * 80 / 100)) $((W * 7 / 100)) $((H * 45 / 100)) 1000; sleep 1; shot home-about-2
    drag $((W * 7 / 100)) $((H * 80 / 100)) $((W * 7 / 100)) $((H * 45 / 100)) 1000; sleep 1; shot home-about-3
    adb shell input keyevent KEYCODE_BACK && sleep 1
  else fail "no way to about from home"; fi

  # left to right (LT1 to LT3): the post on the left, the arrows and the stub on the right
  for l in en ru ka; do
    adb shell cmd locale set-app-locales "$PKG" --locales "$l" > /dev/null 2>&1; sleep 3
    qa "--es qa.tab home --es qa.trip '$TRIP_EN' --es qa.time 2026-12-01T13:35"; sleep 2; shot "home-$l"
    qa "--es qa.trip none"; sleep 1.5; shot "home-$l-guest"
  done
  # a phone in a language the app does not have (French) gets English, not Hebrew (3.10.2026)
  adb shell cmd locale set-app-locales "$PKG" --locales fr > /dev/null 2>&1; sleep 3
  qa "--es qa.tab home --es qa.trip none"; sleep 2; shot home-fr-english
  [ -n "$(where "Add my flight")" ] && note "a French phone: English" || fail "a French phone is not in English"
  adb shell cmd locale set-app-locales "$PKG" --locales he > /dev/null 2>&1; sleep 3
  qa "--es qa.trip '$TRIP_HE'"; sleep 1
}

# ---- accounts and the group (A1 to A5, Q1 to Q10), on the pretend server of debug builds (group/DevServer.kt) ----
typeIn() { # typeIn <field text> <latin text>: adb types Latin only, so the run's names are Latin
  tapText "$1" && sleep 0.4 && adb shell input text "${2// /%s}" && sleep 0.4
}
keyboardOff() { adb shell dumpsys input_method | grep -q "mInputShown=true" && adb shell input keyevent KEYCODE_BACK; sleep 0.8; }
group() {
  # a stranger: the group sign leads to the way in (A1); creating asks to sign in (A2), then the new group (Q1)
  qa "--es qa.group none --es qa.tab home --es qa.trip '$TRIP_HE' --es qa.mode auto --es qa.time 2026-12-01T13:35"; waitlog "group seed none" 20
  qa "--es qa.tab group"; sleep 2; shot group-entry
  tapText "יצירת קבוצה" && sleep 1 && shot group-sign-in
  tapText "גוגל" && sleep 2 && shot group-new
  typeIn "גודאורי 2027" "Gudauri 2027"
  typeIn "השם שהחבר׳ה מכירים" "Noa"
  keyboardOff; shot group-new-filled
  tapText "יצירת הקבוצה" && sleep 2.5 && shot group-created
  tapText "הזמנה" && sleep 2 && shot group-invite
  tapText "כל מצטרף חדש מחכה לאישור של מנהל" && sleep 1.5 && shot group-invite-approval
  adb shell input keyevent KEYCODE_BACK; sleep 1

  # invited: the link (Q3) as a guest, in without signing up, and the offer to keep the place (A5)
  qa "--es qa.group invited --es qa.tab j/KZBQRM"; waitlog "group seed invited" 20; sleep 2.5; shot group-invited
  typeIn "השם שהחבר׳ה מכירים" "Avi"; keyboardOff
  tapText "כניסה לקבוצה" && sleep 3 && shot group-save-offer
  tapText "לא עכשיו" && sleep 1 && shot group-joined
  qa "--es qa.tab account"; sleep 1.5; shot account-guest
  # the code typed by hand (Q4), a wrong one, and "I'm already in the group" (Q5)
  qa "--es qa.group invited --es qa.tab join"; sleep 1.5; shot group-code
  adb shell input text "KZBQRM"; sleep 0.5; keyboardOff; shot group-code-typed
  tapText "המשך" && sleep 2.5 && shot group-code-invited
  tapText "אני כבר בקבוצה" && sleep 2 && shot group-reclaim
  qa "--es qa.tab j/AAAAAA"; sleep 2.5; shot group-bad-code

  # a guest member without a flight: the flights (Q6), "I'm on the same flight" (Q7), meetups (Q8), scores (Q9)
  qa "--es qa.group member --es qa.trip none --es qa.tab group"; waitlog "group seed member" 20; sleep 2.5; shot group-flights
  # "I'm on the same flight" on a flight I am not on (A-34, as on the site): one tap, and I am on it
  tapText "אני על אותה טיסה" && sleep 2 && shot group-same-flight-done
  tapText "מפגשים" && sleep 1.5 && shot group-meetups
  tapText "שיאים" && sleep 1.5 && shot group-scores
  tapText "חברים" && sleep 1.5 && shot group-members

  # an admin: a request to approve, a member's menu, the group at night and in English (Q10)
  qa "--es qa.group admin --es qa.tab group"; waitlog "group seed admin" 20; sleep 2.5; shot group-admin
  tapText "חברים" && sleep 1.5 && shot group-admin-members
  tapText "אישור" && sleep 1.5 && shot group-admin-approved
  # an admin fills in a new flight for a member who has none: "fill in for them", then "another flight" (the trip form)
  tapText "טיסות" && sleep 1.5 && tapText "מילוי בשבילו" && sleep 1.2 && tapText "טיסה אחרת" && sleep 2 && shot group-admin-fill-new
  adb shell input keyevent KEYCODE_BACK && sleep 1 && tapText "חברים" && sleep 1
  tapText "פעולות על דנה מזרחי" && sleep 1 && shot group-admin-menu
  # out of the group in two taps (as on the site): the item asks once more, the menu stays open
  tapText "הוצאה מהקבוצה" && sleep 0.8 && shot group-admin-remove-sure && adb shell input keyevent KEYCODE_BACK && sleep 0.8
  # a meetup is deleted for everyone in two taps: the X, then "tap again"
  tapText "מפגשים" && sleep 1.5 && tapText "מחיקת המפגש" && sleep 0.8 && shot group-meetup-delete-sure
  tapText "~לחיצה נוספת מוחקת" && sleep 1.5 && shot group-meetup-deleted
  tapText "חברים" && sleep 1
  qa "--es qa.tab account"; sleep 1.5; shot account
  qa "--es qa.tab group --es qa.mode night"; sleep 2.5; shot group-night
  qa "--es qa.mode auto"
  adb shell cmd locale set-app-locales "$PKG" --locales en > /dev/null 2>&1; sleep 3
  qa "--es qa.group admin --es qa.tab group"; sleep 2.5; shot group-en
  adb shell cmd locale set-app-locales "$PKG" --locales he > /dev/null 2>&1; sleep 3
  qa "--es qa.group none --es qa.tab home --es qa.trip '$TRIP_HE'"; sleep 1
}

# ---- the meeting point (M1 to M3, round 8's MP1 to MP3) and a meetup saved in the group (Q8) ----
meet() {
  # the page scrolls under the map: drag below it (a drag on the map moves the map)
  local up="$((W * 7 / 100)) $((H * 85 / 100)) $((W * 7 / 100)) $((H * 35 / 100)) 400" down="$((W * 7 / 100)) $((H * 70 / 100)) $((W * 7 / 100)) $((H * 98 / 100)) 300"
  totop() { for _ in 1 2 3 4 5 6 7; do drag $down; sleep 0.3; done; sleep 0.8; }
  # a trip (its ski days are the day chips), mid-December at noon in Gudauri: nothing is picked at first (MP1)
  qa "--es qa.group none --es qa.tab home --es qa.trip '$TRIP_HE' --es qa.mode auto --es qa.time 2026-12-15T12:00"; waitlog "trip set" 20
  mark; qa "--es qa.tab meet"; waitlog "meet ready" 45; waitlog "meet relief ready" 45; sleep 2; shot meet-empty
  drag $up; sleep 1; shot meet-empty-below
  # one tap on a spot of the post (M2) prints the card (M3); the map flies to it
  mark; tapText "רכבל הבוקר" && waitlog "meet picked" 5 && { sleep 1.5; shot meet-preset; }
  drag $up; sleep 1; shot meet-card
  drag $up; sleep 1; shot meet-ways-share
  totop; shot meet-picked-map
  # clear it, and bring it back (MP3); the undo shows for 4.5 s, so it is tapped before anything slow
  mark; tapText "ניקוי הבחירה" && waitlog "meet cleared" 5 && { sleep 0.5; tapText "החזרה" && sleep 1.5 && shot meet-undone; }
  mark; tapText "ניקוי הבחירה" && waitlog "meet cleared" 5 && { sleep 0.5; shot meet-cleared; }
  # a pin on the map: the zoom buttons, then a tap on the picked pin clears it (a tap on another one picks that)
  tapText "להתקרב" && sleep 0.8 && tapText "להתקרב" && sleep 1 && shot meet-zoomed
  # a link from the site (the top of Shino and Firni, 13:00 on the 12th), then at night
  mark; qa "--es qa.tab meet/472374253t/1300/20270112"; waitlog "meet ready" 15; sleep 2; shot meet-link
  qa "--es qa.mode night"; sleep 2; shot meet-link-night; drag $up; sleep 1; shot meet-link-night-card
  qa "--es qa.mode auto"; sleep 1
  # in English, left to right
  adb shell cmd locale set-app-locales "$PKG" --locales en > /dev/null 2>&1; sleep 3
  mark; qa "--es qa.tab meet/158744075b/0930/20270111"; waitlog "meet ready" 15; sleep 2; shot meet-en; drag $up; sleep 1; shot meet-en-card
  adb shell cmd locale set-app-locales "$PKG" --locales he > /dev/null 2>&1; sleep 3

  # a member: the group's meetups, "new meetup" leads here, and saving goes back to the group with the meetup in it (Q8)
  qa "--es qa.group member --es qa.tab group/g1/meetups"; waitlog "group seed member" 20; sleep 2.5; shot meet-group-before
  mark; tapText "מפגש חדש" && waitlog "meet ready" 15 && sleep 1.5
  drag $up; sleep 1; tapText "צהריים" && sleep 1.5
  drag $up; sleep 0.8; drag $up; sleep 0.8; drag $up; sleep 1; shot meet-group-save
  mark; tapText "שמירה בקבוצה" && waitlog "meetup saved" 10 && { sleep 2; shot meet-group-after; }
  # the reminders (Q8): each meetup ahead reminds a quarter of an hour before; one turned off, then the next one rings
  waitlog "reminders armed" 10; tapText "תזכורת רבע שעה לפני" && sleep 1 && shot meet-group-remind-off
  # the shade draws a new notification a moment after it opens: the picture waits for it
  mark; qa "--es qa.remind next"; waitlog "reminder shown" 10 && { adb shell cmd statusbar expand-notifications
    for _ in 1 2 3 4 5 6; do [ -n "$(where "~בעוד רבע שעה")" ] && break; sleep 1; done; sleep 0.5; shot meet-reminder
    tapText "~בעוד רבע שעה" && sleep 3 && shot meet-from-reminder || adb shell cmd statusbar collapse; }
  # a tap on a meetup in the group opens its card
  qa "--es qa.tab group/g1/meetups"; sleep 2
  tapText "~התחנה" && sleep 2 && shot meet-from-group
  qa "--es qa.group none --es qa.tab home"; sleep 1
  # the notification permission is asked when the app first opens (the run installs with it granted): taken away, the
  # next open asks, and "Allow" gives it back
  adb shell pm revoke "$PKG" android.permission.POST_NOTIFICATIONS > /dev/null 2>&1
  adb shell pm clear-permission-flags "$PKG" android.permission.POST_NOTIFICATIONS user-set user-fixed > /dev/null 2>&1 || true
  adb shell am force-stop "$PKG"; sleep 1
  mark; adb shell am start -n "$ACT" > /dev/null; waitlog "notifications asked" 30 && { sleep 2; shot first-open-notifications
    tapText "Allow" && waitlog "notifications allowed" 10 && { sleep 1; shot first-open-allowed; }; }
  adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS > /dev/null 2>&1 || true
}

# ---- the language from the home page (3.10.2026): the tag in the head opens the list; a tap saves the language ----
lang() {
  local up="$((W * 7 / 100)) $((H * 85 / 100)) $((W * 7 / 100)) $((H * 45 / 100)) 400"
  adb shell cmd locale set-app-locales "$PKG" --locales he > /dev/null 2>&1; sleep 3
  qa "--es qa.group none --es qa.tab home --es qa.trip none --es qa.mode auto --es qa.time 2026-12-01T13:35"; waitlog "trip none" 20; sleep 2
  shot lang-he-closed
  mark; tapText "שפה: עברית" && waitlog "lang sheet open" 5 && { sleep 1; shot lang-he-open; }
  qa "--es qa.mode night"; sleep 2; shot lang-he-open-night
  adb shell input keyevent KEYCODE_BACK; sleep 1; shot lang-he-closed-night
  qa "--es qa.mode auto"; sleep 1
  # each language from the list: the app comes back in it, mirrored, with its fonts (LT1 to LT3, FT1)
  local pick
  for pick in "English:en:Language: English" "Русский:ru:Язык: Русский" "ქართული:ka:ენა: ქართული"; do
    local name=${pick%%:*} rest=${pick#*:}; local code=${rest%%:*} tag=${rest#*:}
    mark; tapText "~^(שפה|Language|Язык|ენა): " && waitlog "lang sheet open" 5 && sleep 1 && tapText "$name" && waitlog "lang set $code" 5
    sleep 4; shot "lang-$code-closed"
    mark; tapText "$tag" && waitlog "lang sheet open" 5 && { sleep 1; shot "lang-$code-open"; }
    [ "$code" = en ] && { qa "--es qa.mode night"; sleep 2; shot lang-en-open-night; adb shell input keyevent KEYCODE_BACK; sleep 1; shot lang-en-closed-night; qa "--es qa.mode auto"; sleep 1; } || { adb shell input keyevent KEYCODE_BACK; sleep 1; }
  done
  # "from the phone": back to the phone's language (the emulator's English); then the run goes on in Hebrew
  mark; tapText "ენა: ქართული" && waitlog "lang sheet open" 5 && sleep 1 && { drag $up; sleep 0.5; tapText "~ტელეფონის მიხედვით" && waitlog "lang set auto" 5 && { sleep 4; shot lang-auto; }; }
  adb shell cmd locale set-app-locales "$PKG" --locales he > /dev/null 2>&1; sleep 3
  qa "--es qa.tab home"; sleep 1
}

# ---- the phone's own language, with no choice in the app (decision 47): Hebrew only on a phone set to Hebrew ----
# the emulator's whole system language changes (root, then the system restarts); a fresh install has no app choice
phone() {
  # root, to change the whole system's language; the emulator sometimes answers late, so a few tries
  local rooted="" r
  for r in 1 2 3 4; do adb root 2>&1 | grep -qE "restarting|already" && { rooted=1; break; }; sleep 4; adb wait-for-device; done
  if [ -z "$rooted" ]; then fail "no root: the phone's language not tested"; adb shell am start -W -n "$ACT" > /dev/null; return; fi
  sleep 3; adb wait-for-device
  adb uninstall "$PKG" > /dev/null; adb install -r -g "$APK" > /dev/null || { fail "reinstall"; return; }
  # the notifications question covered the home page on the first open (runs 96, 97): granted outright, as in the rest
  adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS > /dev/null 2>&1 || true
  # no language chosen in the app: the system keeps an app's choice across a quick reinstall (the run set Hebrew at its
  # start), and this part runs on an emulator of its own, with no "from the phone" before it (3.10.2026)
  adb shell cmd locale set-app-locales "$PKG" > /dev/null 2>&1; sleep 1
  local loc
  for loc in he-IL ru-RU ka-GE en-US; do
    adb shell setprop persist.sys.locale "$loc"; adb shell setprop ctl.restart zygote
    sleep 5; adb wait-for-device
    local i; for i in $(seq 1 60); do [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ] && break; sleep 2; done; sleep 10
    kill "$LOGCAT" 2> /dev/null; logcat_on # the restart dropped the log stream
    note "system language asked $loc: $(adb shell getprop persist.sys.locale | tr -d '\r'), the system says $(adb shell am get-config 2> /dev/null | grep -m1 -oE '^config: [^ ]+' | tr -d '\r')"
    # the system may still be coming back from the restart (its "booted" flag outlives it): start the app until it answers
    local opened=""
    for i in 1 2 3; do
      mark; adb shell am start -W -n "$ACT" --es qa.tab home --es qa.trip none --es qa.mode auto > /dev/null 2>&1
      seen "trip none" 30 && { opened=1; break; }; sleep 5
    done
    [ -z "$opened" ] && fail "the app did not open on a phone in $loc"
    sleep 3; shot "phone-$loc"
    tail -n +"$((MARK + 1))" "$OUT/logcat.txt" | grep -m1 "SkiQa.*language " | sed 's/.*SkiQa[^:]*: /  app: /' | tee -a "$OUT/summary.txt"
    # the screen reader of the test can be slow to come back after the restart: a few tries
    local want="Add my flight" got=""; [ "$loc" = he-IL ] && want="הוספת הטיסה שלי"
    for i in 1 2 3 4 5; do got=$(where "$want"); [ -n "$got" ] && break
      # a system question over the page (notifications), in the phone's language: answered, and looked at again
      local w xy; for w in "Allow" "יש אישור" "Разрешить" "დაშვება"; do xy=$(where "$w"); [ -n "$xy" ] && { tap $xy; break; }; done
      sleep 2; done
    [ -n "$got" ] && note "a phone in $loc: ${want}" || fail "a phone in $loc does not show '$want'"
  done
  adb unroot > /dev/null 2>&1; sleep 3; adb wait-for-device
  adb shell cmd locale set-app-locales "$PKG" --locales he > /dev/null 2>&1; sleep 2
  adb shell am start -W -n "$ACT" > /dev/null # the app running again, for whatever comes next
}

# ---- the run view (13.3, the site's T1 to T4): the sign and the panel, the profile that moves the dot, a swipe to the
# next run, a lift's panel, the list of runs with the filters, and English (map/RunSheet.kt) ----
runview() {
  local up="$((W * 7 / 100)) $((H * 90 / 100)) $((W * 7 / 100)) $((H * 50 / 100)) 500" xy
  adb shell am force-stop "$PKG"; sleep 1; mark
  adb shell "am start -W -n $ACT --es qa.tab map --es qa.time 2027-01-12T11:00 --es qa.stats off" > /dev/null
  waitlog "scene ready" 120 && waitlog "shadow ready" 120
  mark; qa "--es qa.run 'Tatra 2'"; waitlog "selected Tatra 2" 20; sleep 3; shot run-head
  mark; tapText "הצגת כל הפרטים" && waitlog "panel open" 5 && { sleep 1.5; shot run-panel; }
  # the profile: a finger along it moves the dot on the mountain. Often already on the screen; a scroll can fling past
  # it (5.10.2026), so look first, then a short scroll at a time
  xy=$(where "מיקום לאורך המסלול, מלמעלה למטה")
  local k; for k in 1 2; do [ -n "$xy" ] && break; drag $((W * 7 / 100)) $((H * 80 / 100)) $((W * 7 / 100)) $((H * 60 / 100)) 600; sleep 1; xy=$(where "מיקום לאורך המסלול, מלמעלה למטה"); done
  if [ -n "$xy" ]; then set -- $xy; mark; drag $((W / 8)) "$2" $((W * 5 / 8)) "$2" 900; waitlog "profile scrub Tatra 2" 5; sleep 1.5; shot run-profile
  else fail "no elevation profile"; fi
  drag $up; sleep 1; shot run-ahead
  drag $up; sleep 1; shot run-connections
  drag $up; sleep 1; shot run-research
  drag $up; sleep 1; shot run-videos
  # the next run: a swipe across the sign (as on the site, to the left is the next one)
  xy=$(where "המסלול הבא: Kudebi 1"); if [ -n "$xy" ]; then set -- $xy; mark; drag $((W * 3 / 4)) "$2" $((W / 6)) "$2" 300; waitlog "selected Kudebi 1" 10 && { sleep 3; shot run-next; }; else fail "no step to the next run"; fi
  # a lift of the run: its panel
  mark; tapText "הצגת כל הפרטים" && waitlog "panel open" 5; sleep 1
  # the lift's tag among the connections (the name alone: "Kudebi 1" and "Kudebi 2" are runs)
  for i in 1 2 3 4 5; do xy=$(where "~^Kudebi$"); [ -n "$xy" ] && break; drag $up; sleep 1; done
  mark; tapText "~^Kudebi$" && waitlog "lift open Kudebi" 5 && { sleep 3; shot lift-panel; drag $up; sleep 1; shot lift-panel-runs; }
  # the list of all runs, and the filters (Back closes the lift's panel first)
  adb shell input keyevent KEYCODE_BACK; sleep 1; qa "--es qa.run none"; sleep 2
  mark; tapText "כל המסלולים" && waitlog "run list open" 5 && { sleep 1.5; shot run-list; }
  mark; tapText "אדום" && waitlog "filter red off" 5 && { sleep 2; shot run-list-no-red; }
  mark; tapText "אדום" && waitlog "filter red on" 5
  drag $up; sleep 1; shot run-list-runs
  for i in 1 2 3 4; do xy=$(where "Sadzele 1"); [ -n "$xy" ] && break; drag $up; sleep 1; done
  mark; tapText "Sadzele 1" && waitlog "selected Sadzele 1" 10 && { sleep 3; shot run-from-list; }
  # a run's link from the site, while the app is open (A-31): the same app comes forward on that run
  mark; adb shell "am start -W -a android.intent.action.VIEW -d 'https://gudauri-ski-trip.vercel.app/?utm_source=app&utm_medium=share#map/run/Tatra%201' -p $PKG" > /dev/null
  waitlog "selected Tatra 1" 15 && { sleep 3; shot run-link; }
  # English: the same panel, left to right
  adb shell cmd locale set-app-locales "$PKG" --locales en > /dev/null 2>&1; sleep 3
  adb shell am force-stop "$PKG"; sleep 1; mark
  adb shell "am start -W -n $ACT --es qa.tab map --es qa.time 2027-01-12T11:00 --es qa.stats off" > /dev/null
  waitlog "scene ready" 120 && waitlog "shadow ready" 120
  mark; qa "--es qa.run 'Tatra 2'"; waitlog "selected Tatra 2" 20; sleep 3; shot run-head-en
  mark; tapText "Show all the details" && waitlog "panel open" 5 && { sleep 1.5; shot run-panel-en; drag $up; sleep 1; shot run-profile-en; }
  adb shell cmd locale set-app-locales "$PKG" --locales he > /dev/null 2>&1; sleep 3
  qa "--es qa.run none --es qa.tab home"; sleep 1
}

# ---- the lift status (13.4, the site's S1 to S3): no report, an old one, a fresh one (status/LiftStatus.kt) ----
status() {
  local up="$((W * 7 / 100)) $((H * 85 / 100)) $((W * 7 / 100)) $((H * 45 / 100)) 400"
  # no report, out of season: the home's board sleeps under the snow (S3), and a tap opens the snowy signs over the map
  adb shell am force-stop "$PKG"; sleep 1; mark
  adb shell "am start -W -n $ACT --es qa.tab home --es qa.trip none --es qa.mode auto --es qa.group none --es qa.time 2026-10-03T11:00 --es qa.status none" > /dev/null
  waitlog "status pinned none" 30; waitlog "scene ready" 120
  sleep 2; drag $up; sleep 1; shot status-home-asleep
  mark; tapText "ההר עוד ישן" && waitlog "status board snowy" 10 && { sleep 2; shot status-snowy; drag $up; sleep 1; shot status-snowy-below; }
  # in season, a report two hours old: no current information, and the map as it is
  mark; qa "--es qa.sheet off --es qa.time 2027-01-12T11:00 --es qa.status stale"; waitlog "status pinned stale" 10; sleep 2; shot status-stale-bar
  mark; tapText "אין מידע עדכני על הרכבלים" && waitlog "status board snowy" 10 && { sleep 2; shot status-stale-board; }
  # a fresh one: the bar counts the open lifts (S1); on the mountain closed lifts grey and dashed, closed runs dashed
  mark; qa "--es qa.sheet off --es qa.status fresh"; waitlog "status on map" 10; sleep 2.5; shot status-live-map
  # from above: closed lifts and runs grey and dashed, and chairs moving on the open lifts (A-35, the site's)
  mark; qa "--es qa.mapview 2d"; waitlog "overview fit" 60; sleep 2.5; burst status-live-top 2 0.8
  qa "--es qa.mapview 3d"; sleep 1
  # the board (S2): its rows flip in, what changed since the last look, and "only what's open for me"
  mark; tapText "~מתוך 12" && waitlog "status board live" 10 && { burst status-board-flip 3 0.2; sleep 1.5; shot status-board; }
  drag $up; sleep 1; shot status-board-below
  mark; tapText "רק מה שפתוח בשבילי" && waitlog "for me on" 5 && { sleep 0.8; shot status-forme; }
  adb shell input keyevent KEYCODE_BACK; sleep 2; shot status-forme-map
  # home: the board without its snow, and how many lifts are open
  qa "--es qa.forme off --es qa.tab home"; sleep 2; drag $up; sleep 1; shot status-home-live
  # left to right (the language change starts the screen again, so the run's time and report go again)
  adb shell cmd locale set-app-locales "$PKG" --locales en > /dev/null 2>&1; sleep 3
  qa "--es qa.time 2027-01-12T11:00 --es qa.status fresh --es qa.tab map --es qa.sheet on"; sleep 2.5; shot status-en
  qa "--es qa.status none --es qa.sheet off"; sleep 0.5; qa "--es qa.sheet on"; sleep 2; shot status-en-snowy
  adb shell cmd locale set-app-locales "$PKG" --locales he > /dev/null 2>&1; sleep 3
  qa "--es qa.sheet off --es qa.tab home"; sleep 1
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
  qa "--es qa.tab descent"; sleep 2; qa "--es qa.descent go"; sleep 6
  hold $((W / 2)) $((H / 2)) 700; sleep 0.5; sshot descent
  qa "--es qa.tab home --es qa.trip '$TRIP_HE' --es qa.time 2026-12-01T13:35"; sleep 2.5; sshot home
  qa "--es qa.mode night"; sleep 2; sshot home-night
  qa "--es qa.mode auto"
  adb shell am broadcast -a com.android.systemui.demo -e command exit > /dev/null
  adb shell wm size reset
}

# one scenario, several with commas (home,group), or all
for sc in ${SCENARIO//,/ }; do
  case "$sc" in
    map) map ;;
    descent) descent ;;
    games) games ;;
    school) school ;;
    snowball) snowball ;;
    home) home ;;
    group) group ;;
    meet) meet ;;
    status) status ;;
    lang) lang ;;
    phone) phone ;;
    store) store ;;
    run) runview ;;
    *) map; descent; games; school; snowball; home; group; meet; status; runview; lang; phone; store ;;
  esac
done

# the run in parallel parts (android-qa.yml): the checks below that need doing once run with one part only (FINAL=1);
# a run on its own (no FINAL) does them all
FINAL=${FINAL-1}

# ---- usage and crash reporting: with the keys, a check message to Sentry and everything queued sent now ----
if [ -n "$FINAL" ]; then
qa "--es qa.sentry run-$(date +%s)"; waitlog "flushed" 20 && grep "SkiQa.*flushed" "$OUT/logcat.txt" | tail -1 | sed 's/.*SkiQa[^:]*: //' | tee -a "$OUT/summary.txt"; sleep 5
fi

# ---- what the run measured ----
adb shell dumpsys gfxinfo "$PKG" > "$OUT/gfxinfo.txt"
grep "SkiQa" "$OUT/logcat.txt" > "$OUT/qa-log.txt"
# crashes anywhere, and every error line the app itself wrote
grep -E "FATAL EXCEPTION|ANR in $PKG" -A 30 "$OUT/logcat.txt" > "$OUT/errors.txt"
PID=$(adb shell pidof "$PKG" | tr -d '\r')
# known noise, the framework's own lines in the app's process, not app errors: the map's surface changing size (the
# bars appear), a slow emulator missing a window sync, and the keyboard's closing animation timing out
if [ -n "$PID" ]; then adb logcat -d --pid="$PID" '*:E' | grep -v -E "^-+ beginning of|BLASTBufferQueue.*rejecting buffer|SurfaceSyncGroup: Failed to receive transaction|FrameTracker: force finish cuj" >> "$OUT/errors.txt"; else fail "the app is not running at the end"; fi
[ -s "$OUT/errors.txt" ] && fail "errors in the log (errors.txt)"

# ---- the release build (R8): only that it starts and draws ----
if [ -n "$FINAL" ] && [ -f "$RELEASE_APK" ]; then
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
# a failure turns the run red (2.10.2026, after clean runs in a row); the results are published either way
[ "$FAILS" -eq 0 ]
