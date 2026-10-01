# Gudauri — native Android

An independent Kotlin/Jetpack Compose Android app using the website's existing
data and trail-sign design. **No source file under `site/` is modified.**

The active module is now `chatgpt/android-native/`, following the user's move
on `main`. Local parity work was preserved there. See
[`qa/RELOCATION_2026_10_01.md`](qa/RELOCATION_2026_10_01.md).
The audit and browser results below describe baseline `d0a2e88`, not newer
website changes pulled from `main`; those need a separate parity delta review.

## Test installation

Debug builds now install as **גודאורי · בדיקת ChatGPT**, package
`com.pini.gudauri.chatgpt.test`, version `0.1.0-chatgpt-test.20261001.1`.
They install alongside the existing `com.pini.gudauri` app and use separate
local preferences/provider authorities. Keep the old app installed. Release
build identity and signing remain unchanged.

See [`play/TESTING_HE.md`](play/TESTING_HE.md) for installation and a short phone
checklist, and [`qa/TEST_RELEASE_2026_10_01.md`](qa/TEST_RELEASE_2026_10_01.md)
for verification. This is a test build, not complete website parity or a store
release. APK distribution uses a dedicated GitHub test branch and a link in a
prerelease because the environment blocks GitHub's separate upload host.

## Website content parity audit and plan

The 2026-10-01 audit of the current website and native app is in
[`docs/WEBSITE_APP_AUDIT_HE.md`](docs/WEBSITE_APP_AUDIT_HE.md), with the complete
content inventory in [`docs/CONTENT_INVENTORY.json`](docs/CONTENT_INVENTORY.json).
The staged implementation plan and acceptance criteria are in
[`docs/ANDROID_PARITY_PLAN_HE.md`](docs/ANDROID_PARITY_PLAN_HE.md).
These documents describe planned app changes; they do not claim that the new
screens or games are already implemented. Work from this plan changes only the
Android project and uses the website and its data as read-only reference inputs.

The first implementation batch is recorded in
[`qa/PARITY_BATCH_01.md`](qa/PARITY_BATCH_01.md). It adds complete trip/lift/video
models, optional-content failure isolation, Gudauri automatic/day/night time,
local panoramas, full outbound/return passes, ticket feedback, exact run links
with explicit import, and distance-based downhill profile selection.
The build blocker is now resolved using an environment-only official Maven
Central mirror. The updated Compose app builds, all **37 unit tests** pass, and
debug lint passes. [`qa/PARITY_BATCH_02.md`](qa/PARITY_BATCH_02.md) records the
native meeting implementation, source-compatible sharing links, local image
sharing and preserved navigation/camera state. Its seven native device cases
passed together on AOSP API 30 / Android 11, without failures or skips. Earlier
failed/aborted emulator runs remain recorded; API 28/API 36 updated validation
is not claimed passed. Lift-status
UI, the game catalog and all five native game engines are still pending.
[`qa/PARITY_BATCH_03.md`](qa/PARITY_BATCH_03.md) adds pure sampled route profiles,
slope colours, the highlighted steep stretch and an accessible distance/height/
slope selector. The first eight-case runner result passed, but pixel inspection
found a stale SystemUI ANR dialog above the app. A strengthened active-window/
process check exposed a focus timeout (1 passed, 1 failed, 6 unrun), and a fresh
AVD still reported system ANRs before testing. Clean device validation is **not
complete**. The machine lacks KVM even outside the sandbox; an accelerated
environment or accessible Android test device is needed for reliable acceptance.
Profile-to-map marker synchronization and the remaining parity phases are open.

Reusable cloud setup, resolved browser trust and the remaining device limitation:
[`docs/CLOUD_ENVIRONMENT.md`](docs/CLOUD_ENVIRONMENT.md). The original Android
baseline was rebuilt, with four unit and two API 28 device tests passing.
The reference website suite now passes all 100 cases with HTTPS checking enabled;
the website remains unchanged. Saved setup instructions require review/Save in
environment settings for reuse; saving a draft is not publishing an environment.

## Build

Open this directory in Android Studio (JDK 17, Android SDK 36), or:

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest
./gradlew :app:lintRelease :app:bundleRelease
```

Set `sdk.dir` in your untracked `local.properties`, or set `ANDROID_HOME`.
The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`.
The app supports Android 8.0+ and targets Android 16 / API 36.

## Architecture

- **UI:** AndroidX Material 3 and Compose. Nonmodal draggable bottom sheet keeps
  the map visible, lazy lists, native keyboard, touch targets, system back,
  edge-to-edge insets, RTL, light/dark palettes and the site's local OFL fonts.
- **Map:** native OpenGL ES 3 renderer on its own GL thread. Actual DEM geometry,
  shaded snow/rock, environmental texture, contours, trails, lifts and stations.
  Screen-space line meshes retain visible width at any zoom. Native gestures,
  collision-managed labels, terrain occlusion, selection and camera animation.
  Two-dimensional orthographic view uses the same renderer and data. Devices
  lacking ES 3 receive a native Canvas top-view fallback.
- **Performance:** view-model parsing and elevation statistics off the UI thread;
  static meshes/textures uploaded once per GL context; bounded terrain mesh;
  rendering only on interaction/change; no continuously running idle loop.
  Lifecycle pause/resume and GL context recreation are handled. Camera animation
  honors Android's disabled-animation setting. No assumption that native code
  is automatically fast: hardware frame/memory/battery measurements remain a
  release check.
- **Data:** Gradle `copySiteData` reads `../../site/data/*.json` into generated APK
  assets alongside read-only panorama/audio/thumbnail copies and local licenses.
  It never writes into the website. The existing JSON files are the
  shared data source. Partial geometries, confidence and OSM links are retained.
  Trip and videos fail independently without disabling the mountain; malformed
  required terrain/geometry fails explicitly. Rebuild to receive new data;
  there is no live lift-status feed in this version.
- **Android additions:** search, device-local favorites, exact run/meeting sharing
  and explicit website-link import, interactive slope-coloured elevation profile,
  native sheet navigation,
  meeting stations/date/time/undo, local share images with confined FileProvider,
  bundled fonts and local map data. Videos and
  thumbnail downloads require internet. No new permissions beyond internet.
- **Video:** Coil for thumbnails; isolated WebView for the YouTube iframe player,
  with an external/native YouTube fallback. The app screens/map use no WebView.

`data/`, `map/`, and `ui/` deliberately separate the domain, rendering and UI so
future heavy features or device integration can be added without changing web.
This version does **not** claim to contain GPS tracking, notifications, weather,
accounts or monetization; those remain separate future decisions. The five
website games are planned in parity phases 8–9 but are not implemented yet.

## Data limitations inherited from the website

27 named entries plus seven unnamed segments; 16 lifts; 43 video entries covering
18 named entries. Six named entries contain partial geometry or centerlines only.
Missing sections are never interpolated. The source height model is approximately
30 m; the packaged grid spacing is approximately 40 m. Computed connections are
proximity estimates, not navigation advice.
Slope metrics match the existing method: min/max elevations and steepest ~100 m
section. The chart shows only the longest available segment for disconnected runs.

## Release

See [`play/RELEASE.md`](play/RELEASE.md). Unsigned AABs cannot be uploaded. Signing
keys are never committed. Local emulator tests do not establish real-device
smoothness or store eligibility. Store publication is not complete until Play
Console confirms acceptance and the release is actually available.

Completed checks and genuine emulator captures: [`qa/VALIDATION.md`](qa/VALIDATION.md).
Those captures describe the original app, not the parity implementation batches.

For a reproducible SDK-independent domain check when Gradle dependencies cannot
be downloaded, see [`tools/run-domain-tests.sh`](tools/run-domain-tests.sh) and
the batch report. This is not a substitute for an Android build or device tests.

## Licenses

© OpenStreetMap contributors, ODbL. DEM: AWS Terrain Tiles (primarily SRTM).
Names/colors: official MTA map as described in the existing research. Font licenses
are in `licenses/`. Dependency licenses remain those of their upstream projects.
Ticket audio credits and license links: [`licenses/TICKET_AUDIO.md`](licenses/TICKET_AUDIO.md).
