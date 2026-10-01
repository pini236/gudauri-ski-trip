#!/bin/bash
# Prepares a Claude Code on the web session: the site's test tools (npm) and the Android SDK
# for the native app in app/android (docs/APP-NATIVE.md). Idempotent: quick when already installed.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "$CLAUDE_PROJECT_DIR"

# site tests (Playwright; the browser is preinstalled in the container)
npm install --no-audit --no-fund --loglevel=error

# Android SDK: command-line tools, the platform the app compiles against, build tools
SDK=/opt/android-sdk
CLT="$SDK/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$CLT" ]; then
  tmp="$(mktemp -d)"
  curl -fsSL --retry 3 -o "$tmp/clt.zip" https://dl.google.com/android/repository/commandlinetools-linux-9862592_latest.zip
  mkdir -p "$SDK/cmdline-tools"
  unzip -q -o "$tmp/clt.zip" -d "$tmp"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -rf "$tmp"
fi
if [ ! -d "$SDK/platforms/android-37.2" ] || [ ! -d "$SDK/build-tools/36.0.0" ] || [ ! -d "$SDK/platform-tools" ]; then
  yes | "$CLT" --licenses > /dev/null 2>&1 || true
  "$CLT" "platforms;android-37.2" "build-tools;36.0.0" "platform-tools" > /dev/null
fi

if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  echo "export ANDROID_HOME=$SDK" >> "$CLAUDE_ENV_FILE"
  echo "export ANDROID_SDK_ROOT=$SDK" >> "$CLAUDE_ENV_FILE"
fi
