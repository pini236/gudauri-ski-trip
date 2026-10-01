# Cloud Android environment — 2026-10-01

The Maven Central HTTP 429 build blocker is resolved. Repository routing uses
Google's official Maven Central mirror in a **workspace-local Gradle init script**,
not changes to the project's repositories or dependency versions. Java uses the
existing system trust store and the platform's proxy; TLS verification remains on.
The website, shared data, design files and root documentation remain read-only.

The active module is `chatgpt/android-native/` after the user's relocation on
`main` (`49fe97b`). The install/start instructions use that path. Synchronizing
the checkout brought in existing upstream website changes; no local website
edits were made. Browser results below are historical for `d0a2e88`, not a run
against the newer reference. See [relocation verification](../qa/RELOCATION_2026_10_01.md).

## Reusable setup

A complete install script and start instructions are saved in this chat's cloud
environment configuration draft. Save that draft in environment settings to use
it for future environments. Saving a draft alone is not evidence that a new
environment has run it. The current machine was installed and tested separately.

Generated environment helpers, outside the repository:

- `/workspace/.cache/gudauri-cloud/install.sh`: the complete draft install script.
- `/workspace/.cache/gudauri-cloud/setup.sh`: verified downloads, SDK packages,
  AVDs, npm/Chromium installation, debug build, unit tests and lint.
- `/workspace/.cache/gudauri-cloud/android-env.sh`: Java, SDK, proxy, certificate
  store, caches, emulator configuration and adb key locations.
- `/workspace/.cache/gudauri-cloud/check-android-device.sh`: bounded, read-only
  device preflight; also available as `chatgpt/android-native/tools/check-android-device.sh`.
- `/workspace/.cache/gudauri-cloud/check-browser.cjs`: checks the reviewed platform
  CA fingerprint, real CDN HTTPS responses and a Chromium WebGL context; never
  installs certificates or ignores TLS errors.
- `/workspace/.cache/gudauri-cloud/reference-playwright.config.ts`: read-only
  reference-suite override, HTTPS checking enabled and all output under `/tmp`.
- `/workspace/.cache/gudauri-gradle/init.d/gudauri-repositories.gradle`: routes
  only the `GudauriNative` project's repositories through the official mirror.

The installer was run end to end and rerun against the installed environment.
Gradle's wrapper also downloaded and executed successfully. It does not create
a Git worktree, alter dependency pins, install a replacement release signing
key, disable security checks, modify tests to report success or write to `site/`.

## Toolchain and verification

| Component | Installed / verified |
|---|---|
| Full JDK | Temurin 17.0.16+8; official SHA-256 verified |
| Gradle | 8.13; official SHA-256 verified; wrapper tested |
| Kotlin project compiler | 2.2.20, unchanged project dependency |
| SDK / build tools | API 36 / 36.0.0, installed with SDK Manager |
| SDK command-line tools | Official archive 13114758; published SHA-1 matched, pinned SHA-256 |
| Platform tools / emulator | 37.0.1 / currently 37.2.12; older live runs used 37.1.11 |
| AOSP images | API 28 x86, API 30 x86_64 and API 36 x86_64; separate AVDs |
| Node dependencies | `npm ci`, unchanged lockfile |
| Playwright Chromium | Build 1194, workspace-local browser cache |
| Baseline Android build | Debug app + instrumentation APKs built; original four unit tests passed |
| Baseline device checks | Original two instrumentation cases passed on AOSP API 28 |
| Updated Android build | Batch 3 debug APK, all 37 unit tests and debug lint passed |
| Updated device regression | Earlier 8-case runner pass had a stale system dialog; guarded rerun: 1 passed, 1 failed, 6 unrun; clean-system acceptance not complete |
| Website reference regression | All 100 cases passed; 0 failures/skips, four light/dark desktop/phone projects, HTTPS checking enabled |

Current updated instrumentation results are recorded in
[batch 3](../qa/PARITY_BATCH_03.md); earlier failures and the meeting implementation
remain in [batch 2](../qa/PARITY_BATCH_02.md). They are not inferred from baseline.

## Android workflow

```sh
source /workspace/.cache/gudauri-cloud/android-env.sh
cd /workspace/gudauri-ski-trip/chatgpt/android-native
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest \
  --no-daemon --console=plain --max-workers=2
```

AVDs use 720×1600 pixels at density 280, preserving the Pixel 6 preset's logical
display size with less software rasterization work. There is no `/dev/kvm` in
this cloud machine. Cold boot and rendering are slow; do not translate these
results into claims about real-phone performance. Run only one emulator at a
time for routine checks, and finish expensive builds before instrumentation.
Google APIs images caused background service stalls, so the test AVDs use AOSP.

```sh
# In a separate session after sourcing android-env.sh:
emulator -avd gudauri-api30-aosp -port 5554 -no-window -no-audio -no-boot-anim \
  -no-metrics -gpu swiftshader -accel off -memory 2048 -cores 2 -no-snapshot

# Run in short calls until it succeeds; do not block silently on boot:
bash /workspace/.cache/gudauri-cloud/check-android-device.sh emulator-5554
adb -s emulator-5554 shell settings put global window_animation_scale 0
adb -s emulator-5554 shell settings put global transition_animation_scale 0
adb -s emulator-5554 shell settings put global animator_duration_scale 0
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest \
  --no-daemon --console=plain --max-workers=2
```

API 36 software-emulator validation is separate: its image booted, but UI runs
encountered timeouts and system/Quickstep ANRs. They are not counted as passed.
The software workflow uses AOSP API 30 for diagnostics. Its earlier complete
run reported all seven cases passed without a system crash, failures or skips,
but later pixel inspection found an old system dialog. Earlier
API 30 runs exposed an ambiguous name selector, then a merged semantics-tree
selector. The final assertion checks the selected lift's exact ID, name and
visibility in the unmerged tree; it does not remove the selection assertion.
The API 28 baseline passed, but updated runs aborted when the Android framework
restarted, including an isolated run with only one emulator. App/system traces
showed dialog removal waiting on WindowManager, which was blocked by
SurfaceFlinger's region operation. Current results are recorded in the batch
report. A stale `sys.boot_completed=1` survived the framework failure while the
activity service was unavailable; do not use that property alone as readiness.
The read-only preflight script checks boot, activity/package/window service
registration and real ActivityManager/PackageManager binder responses, with
bounded calls. It is not a replacement for device tests. Restart a failed test
AVD normally rather than disabling framework/watchdog checks.

Batch 3 pixel inspection also exposed a boot-time SystemUI ANR dialog that had
remained open despite passing Compose semantics assertions. The event predated
the run by over an hour. Normal **Wait** cleared the dialog and the process flag;
this was not disabling ANR reporting. The preflight now rejects
`mCrashing=true`/`mNotResponding=true`. Every instrumented case additionally checks
actual process state and the active accessibility window before and after its
assertions. Do not use `dumpsys activity lastanr` alone: it reported no ANR even
while this dialog and process flag remained. Inspect/correct a stale boot dialog
normally and recheck readiness before running tests; never suppress an active
failure or silently auto-dismiss recurring errors. A fresh instance's cold-boot
behaviour is not established by a recovered running AVD.

The first guarded full rerun aborted at MainActivity's focus-event timeout:
one case passed, one failed and six were unrun. The app-side wait cause remains
unestablished. A normal restart without wiping data used emulator 37.2.12 and
booted in about 215 seconds, but reproduced SystemUI and process-system ANR
dialogs before another app run. Readiness correctly rejected it; the unhealthy
test AVD was stopped normally. The official `emulator -accel-check`, also run
outside the restricted sandbox, returns 3: `KVM requires a CPU that supports vmx
or svm`; `/dev/kvm` is absent there too. The sandbox is not hiding acceleration.
No available configuration tool provisions that missing hardware. Reliable
device acceptance needs a KVM-capable environment or accessible Android device;
software runner passes must not be represented as a fully healthy environment.

Stop emulators using this SDK before rerunning setup. SDK Manager may update its
official emulator package; replacing a live process's tool files risks mixed
runtime versions. The installer now refuses this operation while such a process
is running, and that rejection was tested. Do not silently stop someone else's
AVD or disable ANR/watchdog checks to make installation or testing pass.

adb needs `/home/agent/.android` to exist even with an explicit vendor key.
The emulator's local console requires its authentication token; the installer
creates it securely only if absent and links the relocated Java home to that
same token. No token/private key contents belong in logs, configuration drafts
or source control. Emulator gRPC authentication also remains enabled.

## Browser trust — resolved after approval and diagnosis

The earlier run passed 96 cases and skipped 4 three-dimensional checks. Initial
auto-review rejected persistent CA import; the user then explicitly approved it.
The approved import command succeeded, but NSS retained the existing nickname
`OpenAI-nebula-dns`: the exact certificate was already trusted. The earlier
assumption that the certificate was missing was incorrect.

The public platform CA and actual CDN chain were independently matched to SHA-256
`C7:1B:4D:1E:9D:77:75:C5:38:C6:88:AB:10:CE:A9:CE:41:7D:73:12:E8:98:38:F9:D1:5F:F7:32:56:CD:7F:C3`.
Tracing Chromium's file access showed `EROFS` when NSS requested write access to
`/home/agent/.pki/nssdb/cert9.db`. Chromium could not load the trusted database in
the restricted read-only mount. A normal browser run with approved access to
that store resolved the failure; no additional CA, changed home, moved key store,
certificate-ignore flag or disabled verification was needed.

The real Chromium 151 check returned HTTPS 200 from both the three.js and font
CDNs, with `ignoreHTTPSErrors=false`. THREE and a real WebGL context loaded on the
reference map. The full existing suite then passed **100/100**, including all
four previously skipped lift-label cases. XML is retained in
`chatgpt/android-native/qa/reports/reference-browser-tls-verified.xml`.

Run browser commands with normal approved access to NSS if the execution sandbox
mounts the store read-only. A failure in the restricted mount is not permission
to disable TLS. The readiness helper pins the reviewed certificate and fails if
it changes; review a replacement rather than automatically trusting another CA.

```sh
cd /workspace/gudauri-ski-trip
node /workspace/.cache/gudauri-cloud/check-browser.cjs
PLAYWRIGHT_JUNIT_OUTPUT_FILE=/tmp/gudauri-browser-tls-verified.xml \
  npx playwright test --config=/workspace/.cache/gudauri-cloud/reference-playwright.config.ts \
  --reporter=list,junit
```

This resolves the browser prerequisite, not API 36 device validation or complete
application parity. Draft review/Save is still needed for future environments.

## Not provided by environment setup

The user's uploaded APK and signing identity are still unavailable. A debug
build is not a verified in-place update for that binary. Release signing,
hardware performance, publication and a real lift-status service remain
separate prerequisites; nothing was uploaded or changed on the website.
