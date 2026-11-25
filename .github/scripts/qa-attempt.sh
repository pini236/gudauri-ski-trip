#!/usr/bin/env bash
# One attempt of an emulator part, run by reactivecircus/android-emulator-runner on the booted emulator.
# Marks that the emulator did start (the workflow retries the whole boot once if this mark is missing), and
# retries the script once when the only failure was the app's install over adb. A failure in a screen is not retried:
# it is a real finding, and a part that failed twice in ten runs on one step is a bug in the script (qa/README.md).
set -uo pipefail
OUT=${OUT:-qa-out}
mkdir -p "$OUT"
: > "$OUT/.started"
# What the emulator and the system say all the way through, for the part's artifact (25.11.2026: the emulator vanished in
# the middle of store,lang and redesign2, "device emulator-5554 not found", probably SwiftShader's GL): the whole system
# log of every buffer, and a line every 5 s with what adb sees, so the minute it disappears is in the file.
mkdir -p "$OUT/emulator"
adb logcat -b all -v threadtime > "$OUT/emulator/system-logcat.txt" 2> /dev/null &
LOGCAT_PID=$!
( while true; do echo "$(date -u +%T) $(adb devices 2>&1 | tail -n +2 | tr '\n' ' ')"; sleep 5; done ) > "$OUT/emulator/adb-devices.txt" 2>&1 &
WATCH_PID=$!
trap 'kill "$LOGCAT_PID" "$WATCH_PID" 2> /dev/null' EXIT
bash app/android/qa/run.sh
rc=$?
# A failure that is the emulator's and not the app's gets one more attempt of the whole part, marked UNSTABLE in the
# summary, with the first attempt kept (qa-out.first). Infrastructure means one of: the install over adb was the only
# failure; or the log shows the system's own window not responding (the home screen / system UI ANR) or adb losing the
# device, AND the log has no crash or ANR of our app. Anything else is a finding and is not retried. A part that has
# already run 20 minutes is not run twice, and a part with more than three FAIL lines is a finding of the script or the app, not
# a loose emulator (25.11.2026: 24 failures were retried and the retry hung).
infra=0
if [ "$rc" -ne 0 ]; then
  if [ "$(grep -c '^FAIL' "$OUT/summary.txt" 2> /dev/null)" = 1 ] && grep -q '^FAIL: install' "$OUT/summary.txt"; then
    infra=1
  elif [ "${SCENARIO:-}" != phone ] && [ "$SECONDS" -lt 1200 ] && [ "$(grep -c '^FAIL' "$OUT/summary.txt" 2> /dev/null)" -le 3 ] && [ -f "$OUT/logcat.txt" ] \
    && grep -q -E "ANR in com\.android\.(systemui|launcher3)|Application Not Responding.*(systemui|launcher)|System UI isn't responding|device offline|no devices/emulators found|device '[^']*' not found" "$OUT/logcat.txt" "$OUT/summary.txt" 2> /dev/null \
    && ! grep -q -E "FATAL EXCEPTION|ANR in io\.github\.pini236|Fatal signal.*io\.github\.pini236|>>> io\.github\.pini236" "$OUT/logcat.txt"; then
    infra=1   # not in the phone part: it restarts the whole system for each language, so a system ANR or a lost device is expected there (architect a08a81fe, 4)
  fi
fi
if [ "$infra" = 1 ]; then
  echo "the first attempt failed on the emulator, not the app: adb restarted, one more attempt"
  echo "::warning::UNSTABLE ${SCENARIO:-part}: the first attempt failed on the emulator (infrastructure); one more attempt"
  adb kill-server; adb start-server
  # never wait for a device without a limit: on 25.11.2026 the emulator had died, and the wait held the job for 30 minutes
  if ! timeout 90 adb wait-for-device; then echo "the device did not come back in 90 s: no second attempt"; exit "$rc"; fi
  adb shell input keyevent KEYCODE_HOME > /dev/null 2>&1 || true
  rm -rf "$OUT.first"; mv "$OUT" "$OUT.first"
  mkdir -p "$OUT"; : > "$OUT/.started"
  bash app/android/qa/run.sh
  rc=$?
  # the first attempt travels with the part's results (the artifact is qa-out)
  rm -rf "$OUT/first"; mv "$OUT.first" "$OUT/first" 2> /dev/null || true
  [ "$rc" -eq 0 ] && echo "UNSTABLE: the first attempt failed on the emulator (infrastructure); the second passed" | tee -a "$OUT/summary.txt"
  # the same part marked unstable twice in a week is a finding for the script's owner, not infrastructure (architect, 5)
fi
exit "$rc"
