#!/usr/bin/env bash
# The store build must have no trace of the field testers' export (docs/ARCHITECTURE.md, 31.10.2026, condition 1): its code,
# its words and its gate live in src/preview only. This unpacks a signed store .aab or .apk and fails if any of them is in it.
#   qa/check-store-clean.sh <file.aab|file.apk>
# Checked: the strings the gate and the export keep after shrinking (they are string constants and resource names, which
# R8 does not rename), and the resource names of the export's words.
set -euo pipefail
f="${1:?usage: check-store-clean.sh <file.aab|file.apk>}"
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
unzip -q -o "$f" -d "$tmp"
bad=0
for needle in 'gudi-tester:' 'field_tester' 'bb7f606df28f6617e1b0898c1276006d9b6d007466a4346f91f539447b16c086' 'tester_consent' 'tester_export' 'tester_unlock' 'tester_about_row' 'RawExport' 'TesterGate'; do
  if grep -rqaF -- "$needle" "$tmp"; then echo "FOUND in the store build: $needle"; grep -rlaF -- "$needle" "$tmp" | head -3; bad=1; fi
done
[ "$bad" = 0 ] && echo "store build is clean of the field testers' export" || exit 1
