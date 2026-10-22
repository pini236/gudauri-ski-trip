#!/usr/bin/env bash
# One attempt of an emulator part, run by reactivecircus/android-emulator-runner on the booted emulator.
# Marks that the emulator did start (the workflow retries the whole boot once if this mark is missing), and
# retries the script once when the only failure was the app's install over adb. A failure in a screen is not retried:
# it is a real finding, and a part that failed twice in ten runs on one step is a bug in the script (qa/README.md).
set -uo pipefail
OUT=${OUT:-qa-out}
mkdir -p "$OUT"
: > "$OUT/.started"
bash app/android/qa/run.sh
rc=$?
if [ "$rc" -ne 0 ] && [ "$(grep -c '^FAIL' "$OUT/summary.txt" 2> /dev/null)" = 1 ] && grep -q '^FAIL: install' "$OUT/summary.txt"; then
  echo "install failed on the first try: adb restarted, one more attempt"
  adb kill-server; adb start-server; adb wait-for-device
  rm -rf "$OUT.first"; mv "$OUT" "$OUT.first"
  mkdir -p "$OUT"; : > "$OUT/.started"
  bash app/android/qa/run.sh
  rc=$?
  [ "$rc" -eq 0 ] && echo "UNSTABLE: the install needed a second try (infrastructure)" | tee -a "$OUT/summary.txt"
fi
exit "$rc"
