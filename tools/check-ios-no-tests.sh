#!/bin/sh
# Read-only gate: the shipped .app carries no test bundle (architect, docs/ARCHITECTURE.md, iOS app tests, condition 1).
# usage: check-ios-no-tests.sh <path to Gudi.app>
set -u
APP="$1"
[ -d "$APP" ] || { echo "::error::no app bundle at $APP"; exit 1; }
FOUND=$(find "$APP" -name '*.xctest' -o -name 'GudiAppTests*' -o -name 'XCTest*.framework' -o -name 'libXCTest*' 2>/dev/null)
if [ -n "$FOUND" ]; then
  echo "$FOUND"
  echo "::error::the app bundle contains test code (GudiAppTests or an .xctest), it must never ship"
  exit 1
fi
echo "no test bundle inside $APP"
