#!/usr/bin/env bash
# UI and logcat smoke test of the shrunk store release on a running emulator. usage: store-smoke.sh <apk>
set -u
OUT=store-smoke-out; mkdir -p "$OUT"; PKG=io.github.pini236.skiapp
adb install -r "$1" > "$OUT/install.txt" 2>&1 || { cat "$OUT/install.txt"; exit 1; }
adb logcat -c
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 > /dev/null
sleep 20
adb shell "monkey -p $PKG --throttle 400 --pct-syskeys 0 --pct-appswitch 0 -s 7 150" > "$OUT/monkey.txt" 2>&1
sleep 5
adb logcat -d > "$OUT/logcat.txt"; adb exec-out screencap -p > "$OUT/final.png"
fail=0
grep -q "FATAL EXCEPTION" "$OUT/logcat.txt" && { echo "::error::crash in the store build"; grep -A12 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -40; fail=1; }
grep -qi "ANR in $PKG" "$OUT/logcat.txt" && { echo "::error::ANR in the store build"; fail=1; }
adb shell pidof "$PKG" > /dev/null || { echo "::error::the app is not running at the end"; fail=1; }
[ "$fail" = 0 ] && echo "store smoke: opened, 150 random touches, zero crashes"
exit "$fail"
