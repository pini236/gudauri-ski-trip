# Isolated ChatGPT Android test build — 2026-10-01

Publication is authorized by the user's confirmation after the proposal for a
GitHub test download that installs alongside the existing app. Website and the
separate `app/` project remain out of scope.

## Identity

- Launcher: `גודאורי · בדיקת ChatGPT`.
- Package: `com.pini.gudauri.chatgpt.test`; version code 1.
- Version: `0.1.0-chatgpt-test.20261001.1`.
- Minimum Android 8 / API 26; target API 36.
- Activity: `com.pini.gudauri.MainActivity`.
- Provider: `com.pini.gudauri.chatgpt.test.files`; AndroidX startup authority also
  uses the test package. Instrumentation targets the isolated test package.
- The production package/version/signing are unchanged. Existing app data is
  retained by leaving that app installed; preferences are independent.

## Verification

Actual Gradle workflow:

```sh
source /workspace/.cache/gudauri-cloud/android-env.sh
cd /workspace/gudauri-ski-trip/chatgpt/android-native
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest \
  --no-daemon --console=plain --max-workers=2
```

Completed successfully in 1 min 8 sec, 37 tasks executed / 44 up-to-date.
All **37 unit tests passed**, zero failures/errors/skips, at
2026-10-01 18:21:55–56 UTC. The debug APK and instrumentation APK built; debug
lint passed. The isolated package/name/version, activity and provider authority
were checked from the actual packaged/merged manifests, not just Gradle input.
The APK supports ARM64, ARMv7, x86 and x86_64, contains all 29 verified local
assets/licenses, passes APK v2 signature verification and 16 KiB page alignment.
No emulator case was rerun or claimed passed for this packaging change.

APK SHA-256:
`657e4997415bba98b9054abfd4d5a821d8ac6e54e16979e82dc76fdf25d30980`.
Public debug certificate SHA-256:
`bff33d4cf96c990c1cf1f0203d1ca1008e9164bd6c70e1f323e44c8a1a7a92b7`.
No signing key or credential is copied into the repository or download.

## Distribution

GitHub API/native Git access to this public repository works with existing
authentication. The separate `uploads.github.com` host returns a proxy CONNECT
403; it is outside the current allowlist. No TLS check or network restriction
is bypassed and no extra credential is requested.

The supported fallback is a dedicated GitHub artifact branch containing the
exact APK, checksum and manifest, linked from prerelease notes. Source is kept
in a separate branch/tag so the APK is not added to website files or `main`.
Publication and remote-byte verification are separate steps after this local
build. The artifact manifest records the source commit and APK identity/hash.

The parity plan is still open. Meetings/profile flows require the user's real
phone checks; games, live lift status and profile-to-map selection remain open.
The original browser regression is historical for `d0a2e88`, not a claim that
the latest upstream website has been retested.

## Confirmed publication

GitHub prerelease `chatgpt-test-2026.10.01-1` was published at
2026-10-01 18:55:35 UTC; release ID 401260230, `draft=false`, `prerelease=true`.
[Release and download](https://github.com/pini236/gudauri-ski-trip/releases/tag/chatgpt-test-2026.10.01-1).

The tag targets source commit `d1ad6d7ddd4566bab75978cf991f76f0cad10130`.
Artifact commit: `6e6a07da9e91800850907ac104070f19954bcd27`, dedicated branch
`chatgpt/android-test-apk-20261001-1`. GitHub's contents API confirmed the exact
file, 13,807,983 bytes and blob `8e3e464b03d1b99fc35cb7ea35bc873003f72d83`.
Downloading that blob through GitHub's supported raw media API reproduced the
local APK byte count and SHA-256 exactly. The release notes link to the immutable
artifact path, not the website or a local-workspace path.

Only the source/artifact branches and prerelease were published. `main` was not
changed or merged, and no installation or phone acceptance is inferred.
