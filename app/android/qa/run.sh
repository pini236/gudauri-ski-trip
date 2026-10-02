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
# no "isn't responding" dialogs from the system's own apps on the slow emulator (unblock() handles any that still show)
adb shell settings put global hide_error_dialogs 1 > /dev/null 2>&1 || true

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
}

# ---- the descent game ----
descent() {
  qa "--es qa.tab descent"; sleep 3; shot descent-start
  for i in 1 2 3; do hold $((W / 2)) $((H / 2)) 700; sleep 0.6; shot "descent-jump-$i"; done
  hold $((W / 2)) $((H / 2)) 300; sleep 0.15; hold $((W / 2)) $((H / 2)) 1600; shot descent-flip
  sleep 8; shot descent-later
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
where() { # where <text>: the middle ("x y") of the element whose text or description is it (else holds it), a tappable one first
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
    if b and got:
        rank = 0 if t in got else 1 if any(t in g for g in got) else 9
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
  tapText "למשל 10.1.2027" && sleep 0.5 && adb shell input text "10.1.2027" && sleep 0.5
  # close the keyboard (Back closes only the keyboard while it is up)
  adb shell dumpsys input_method | grep -q "mInputShown=true" && adb shell input keyevent KEYCODE_BACK; sleep 0.8; shot trip-form-date
  mark; tapText "שמירה" && waitlog "trip saved" 10 && { sleep 1.5; shot home-trip-date-only; }

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
  qa "--es qa.mode auto --es qa.time 2026-12-01T13:35"; sleep 1
  tapText "עריכה" && sleep 1.5 && shot trip-form-filled && adb shell input keyevent KEYCODE_BACK && sleep 1

  # the signs lead on, and back home
  tapText "מפת מסלולים" && sleep 3 && shot home-sign-map
  tapText "בית" && sleep 1.5 && shot home-back
  tapText "נקודת מפגש" && sleep 1 && shot home-sign-meet && adb shell input keyevent KEYCODE_BACK && sleep 1
  tapText "אודות והגדרות" && sleep 1 && shot home-about && adb shell input keyevent KEYCODE_BACK && sleep 1

  # left to right (LT1 to LT3): the post on the left, the arrows and the stub on the right
  for l in en ru ka; do
    adb shell cmd locale set-app-locales "$PKG" --locales "$l" > /dev/null 2>&1; sleep 3
    qa "--es qa.tab home --es qa.trip '$TRIP_EN' --es qa.time 2026-12-01T13:35"; sleep 2; shot "home-$l"
    qa "--es qa.trip none"; sleep 1.5; shot "home-$l-guest"
  done
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
  tapText "אישור ידני לכל מצטרף" && sleep 1.5 && shot group-invite-approval
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
  tapText "אני על אותה טיסה" && sleep 1.2 && shot group-same-flight
  tapText "מהקבוצה" && sleep 0.5 && tapText "שמירה" && sleep 2 && shot group-same-flight-done
  tapText "מפגשים" && sleep 1.5 && shot group-meetups
  tapText "שיאים" && sleep 1.5 && shot group-scores
  tapText "חברים" && sleep 1.5 && shot group-members

  # an admin: a request to approve, a member's menu, the group at night and in English (Q10)
  qa "--es qa.group admin --es qa.tab group"; waitlog "group seed admin" 20; sleep 2.5; shot group-admin
  tapText "חברים" && sleep 1.5 && shot group-admin-members
  tapText "אישור" && sleep 1.5 && shot group-admin-approved
  tapText "פעולות על דנה מזרחי" && sleep 1 && shot group-admin-menu && adb shell input keyevent KEYCODE_BACK && sleep 0.8
  qa "--es qa.tab account"; sleep 1.5; shot account
  qa "--es qa.tab group --es qa.mode night"; sleep 2.5; shot group-night
  qa "--es qa.mode auto"
  adb shell cmd locale set-app-locales "$PKG" --locales en > /dev/null 2>&1; sleep 3
  qa "--es qa.group admin --es qa.tab group"; sleep 2.5; shot group-en
  adb shell cmd locale set-app-locales "$PKG" --locales he > /dev/null 2>&1; sleep 3
  qa "--es qa.group none --es qa.tab home --es qa.trip '$TRIP_HE'"; sleep 1
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
    home) home ;;
    group) group ;;
    store) store ;;
    *) map; descent; home; group; store ;;
  esac
done

# ---- usage and crash reporting: with the keys, a check message to Sentry and everything queued sent now ----
qa "--es qa.sentry run-$(date +%s)"; waitlog "flushed" 20 && grep "SkiQa.*flushed" "$OUT/logcat.txt" | tail -1 | sed 's/.*SkiQa[^:]*: //' | tee -a "$OUT/summary.txt"; sleep 5

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
