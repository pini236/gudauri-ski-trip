# Android parity batch 2 — environment and meeting points

Date: 2026-10-01. Website baseline: `d0a2e88dbc7802a100fe997c8c3b350913ad80a1`.
This report supersedes the initial build-blocker status in batch 1. It is not a
claim that the entire parity plan or a signed user upgrade is complete.

## Implemented

- Native meeting screen from home and map, empty state without a selected place,
  a pan/zoom Canvas station map and an accessible alternative station list.
- Exactly 20 station identities derived from the 12 main-side active named lifts,
  the site's source order, elevation ordering and strict 70 m merge rule.
  Shared station endpoint aliases resolve to the original canonical identity.
- Four trip ski dates from data, six common times, native custom date/time
  pickers, the three website presets and actual Gudauri-time countdowns.
- Selection, focus, clear/card cancellation, repeat-pin/empty-map cancellation,
  undo preserving day/time, and saved meeting state across navigation. The undo
  action remains visible in a fixed native footer regardless of scroll position;
  its default 4.5-second duration honors Android's accessibility recommendation.
- Up to four arrival alternatives from existing connections, explicitly not
  navigation instructions, open-lift assertions, journey times or queue data.
- Exact `#meet/<station>/<HHMM>/<YYYYMMDD>` links, strict real calendar/time
  validation, explicit link import and incoming intents; unknown locations fail
  visibly instead of silently selecting another station.
- Copy, WhatsApp link, system text sharing, and a locally rendered 1080×1080 map
  image with pin, place, date, time, height and attribution. A non-exported
  FileProvider exposes only the feature's cache folder with temporary read grants.
  No storage/contact/location permissions were added; member names are excluded.
- Map lift focus from a meeting and back to the saved meeting; saved map filters,
  selection, search, view mode and camera across screen changes.
- Privacy explanation for self-contained meeting links and local cached images.
- A real Compose build exposed the ticket's ambiguous animation receiver; it was
  fixed explicitly. Project versions and dependency pins were not changed.

## Verified so far

| Check | Result |
|---|---|
| Original isolated baseline | Debug + instrumentation APKs built, 4 unit tests passed |
| Original instrumentation | 2/2 passed on AOSP API 28, 720×1600 / density 280 |
| Updated production Compose sources | Compiled successfully |
| Updated unit suite | 29 passed: 12 data, 6 time, 5 links, 6 meeting tests |
| Stations | All 20 source IDs, endpoints, coordinates and elevations match the frozen inventory |
| Actual debug build / lint | Passed; debug APK produced; no lint errors (warnings remain) |
| Complete saved setup installer | Ran successfully end to end and on rerun; wrapper tested |
| Updated API 28 instrumentation | Aborted after 1 passing case: Android framework restarted, `INSTRUMENTATION_ABORTED: System has crashed` |
| Updated API 36 AOSP instrumentation | Not passed: readiness timeout, Quickstep/system ANRs; interrupted after partial run |
| Updated API 28 isolated rerun | Aborted again after 1 passing case; framework watchdog restart, not passed |
| Device preflight | Added boot + service + binder checks; correctly rejected incomplete boot, passed after a fresh normal boot |
| AOSP API 30 comparison | Complete 7-case run finished without system crash: 6 passed, 1 failed because the `Snow Park` text selector matched 3 legitimate nodes |
| Final AOSP API 30 regression | **7/7 passed together; 0 failures/errors/skips**, including exact lift selection and the full meeting/map round trip |
| Updated four-file installer | Saved version with API 30 and external preflight ran end to end successfully; project pins and website untouched |
| Import dialog correction | Single-line URI keyboard without autocorrection; dialog-local focus/keyboard dismissed before navigation; compiled and unit/lint checks passed |
| Focused meeting-link import | 1/1 passed on a fresh AOSP API 28 boot (72.055 s); not equivalent to the complete suite |
| Complete suite after URI-field correction | Not passed: first map case passed, next import case aborted by system watchdog; remaining cases did not run |
| Packaged content | 26 data/panorama/audio/thumbnail hashes match the source; all 3 local license notices included |
| Website reference browser suite | Final 100/100 passed with HTTPS checking enabled; 0 failures/skips; earlier 96/4-skip result retained in the cloud setup audit trail |
| Website integrity | 35 source/asset hashes unchanged; all repository changes are Android-only |

The older emulator failure is retained, not hidden by changing app tests or
declaring a logic-only check equivalent to a device test. The updated suite has
seven cases: original map/search/favorite/back and day/night controls, return
pass/stub, run-link import, meeting-link import, selection/clear/undo/map return,
and bitmap/FileProvider confinement. Final device results are recorded below,
alongside the earlier failures. The first API 36 attempt also
exposed a stale readiness assumption: the larger home content can place its map
sign outside the composed lazy-list viewport. The test now waits for the loaded
home container and explicitly scrolls to each action; its original action and
content assertions are retained. No timeout was increased to mask the failure.
The isolated repeat still failed around IME input. A subsequent attempt could
not start because Android's boot-complete property was stale: the activity
service was missing. That attempt was stopped, not reported as a test pass.
The AVD was restarted normally. The focused meeting-link import passed, but the
complete suite reproduced the failure. App and system traces were read from
this task's AOSP test emulator using temporary adb root; adb was then returned
to its non-root mode. The app main thread waited in `IWindowSession.remove`
while disposing the import dialog. WindowManager's animation thread held its
global lock while waiting for SurfaceFlinger, whose main thread was inside
`Region::boolean_operation` / `handleTransactionLocked`. The watchdog explicitly
killed the system for the blocked WindowManager monitor. This is evidence of
the observed wait chain, not proof that all app behavior is correct or that
the first URI-field correction solved it. The official AOSP API 30 x86_64 image
was installed and booted for comparison without changing the app's SDK or
dependency versions. The seven-case suite was run with the latest native
undo footer: its assertions additionally require the old card to disappear
and the undo action to be visible without scrolling. No failing case is skipped.
The API 30 comparison finished without the API 28 watchdog crash. The final
case reached the map but its unqualified `Snow Park` text assertion matched
three nodes (selected heading and source-linked piste labels). The selected
heading now exposes an ID-specific test tag; the assertion requires lift
`158744055`, its exact name and visibility. This is a more specific selection
check, not choosing the first text match or removing the original behavior.
The complete suite was rerun against that correction.
The next complete run again passed six cases without a system crash. Its final
assertion confirmed in the failure diagnostics that the ID-specific heading
exists in the unmerged semantics tree, but its clickable parent merges the
public tree. The selector now uses Compose's `useUnmergedTree=true`, retaining
the exact lift ID, exact name and visibility assertions. The 6/7 result is
retained in `qa/reports/api30-merged-heading-run.xml`; it is not counted as passed.

The final complete run passed **all seven cases** on AOSP API 30 / Android 11,
720×1600 pixels at density 280, emulator 37.1.11, SwiftShader and software CPU
emulation. JUnit XML records 7 tests, 0 failures, 0 errors and 0 skips, with a
suite duration of 399.317 seconds. The actual Gradle task finished successfully.
This includes meeting cancel/undo, exact lift selection, two system-back actions
and return to the saved meeting card. The earlier failed runs remain above.

Final debug APK SHA-256:
`93e02149fa9a4eb8f83c9843cb512fda864019586d6c3d10bc72ff7fbecfef67`.
The unit suite has 29 passing tests, with no failures/errors/skips. The 26
packaged reference assets and 35 unchanged website files were rechecked against
the frozen inventory. These are development/regression results, not completion
of every parity phase, every supported Android version or receiving-app sharing.

## Reproduce and inspect

See [cloud setup and the resolved browser-trust diagnosis](../docs/CLOUD_ENVIRONMENT.md).

```sh
source /workspace/.cache/gudauri-cloud/android-env.sh
cd /workspace/gudauri-ski-trip/chatgpt/android-native
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
```

Reports are under `app/build/test-results/testDebugUnitTest`,
`app/build/reports/lint-results-debug.html`, and
`app/build/outputs/androidTest-results/connected/debug`. Instrumentation logs
include the actual device/image and test names. Historical captures in the
original `qa/screenshots` are not substituted for current screenshots.
The original baseline's 2/2 XML and the API 30 comparison's 6/7 XML are retained
in `qa/reports/original-baseline-api28.xml` and
`qa/reports/api30-first-full-run.xml`. The latter deliberately retains the
ambiguous-selector failure, rather than rewriting old test evidence.
The final 7/7 XML is retained in `qa/reports/api30-final-full-run.xml`.
Later batch 3 pixel inspection revealed a stale boot-time SystemUI ANR dialog
on that long-running AVD. The recorded seven-case runner result remains valid,
but is not evidence that no system dialog covered the visible screen. Batch 3
adds real active-window/process guards and documents a separate full rerun after
normal recovery; use its latest evidence for clean-system validation.
The final read-only website regression is retained in
`qa/reports/reference-browser-tls-verified.xml`: 100 passed, no skips, including
all four 3D lift-label checks. The approved certificate was already trusted under
an existing nickname; a read-only sandbox mount prevented Chromium loading NSS.
Normal approved browser access resolved it without a TLS bypass or website edit.

## Remaining work

The other parity phases remain: full native game catalog and all five game
engines, interactive/slope-colored profiles and synchronized map markers,
briefings/comparisons, downhill camera flight, GPU polygons, terrain lighting,
lift-status lifecycle/UI and its unavailable live source. Meeting visual QA,
all four map cancellation gestures, rotation/large font, background restoration,
and actual receiving-app sharing need broader device coverage.

No release key, publication, website change or installation over the user's
uploaded signed binary was performed. The current debug package/version are
development artifacts, not proof of update compatibility or real-phone speed.
