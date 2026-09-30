# Gudauri — native Android

An independent Kotlin/Jetpack Compose Android app using the website's existing
data and trail-sign design. **No source file under `site/` is modified.**

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
- **Data:** Gradle `copySiteData` reads `../site/data/*.json` into generated APK
  assets. It never writes into the website. The existing JSON files are the
  shared data source. Partial geometries, confidence and OSM links are retained.
  Rebuild to receive new data; there is no live lift-status feed in this version.
- **Android additions:** search, device-local favorites, native sharing, elevation
  profile, native sheet navigation, bundled fonts and local map data. Videos and
  thumbnail downloads require internet. No new permissions beyond internet.
- **Video:** Coil for thumbnails; isolated WebView for the YouTube iframe player,
  with an external/native YouTube fallback. The app screens/map use no WebView.

`data/`, `map/`, and `ui/` deliberately separate the domain, rendering and UI so
future heavy features or device integration can be added without changing web.
This version does **not** claim to contain GPS tracking, notifications, weather,
accounts, monetization or a game; those remain separate future decisions.

## Data limitations inherited from the website

27 named entries plus seven unnamed segments; 16 lifts; 43 video entries covering
18 named entries. Six named entries contain partial geometry or centerlines only.
Missing sections are never interpolated. Heights have approximately 30 m model
resolution; computed connections are proximity estimates, not navigation advice.
Slope metrics match the existing method: min/max elevations and steepest ~100 m
section. The chart shows only the longest available segment for disconnected runs.

## Release

See [`play/RELEASE.md`](play/RELEASE.md). Unsigned AABs cannot be uploaded. Signing
keys are never committed. Local emulator tests do not establish real-device
smoothness or store eligibility. Store publication is not complete until Play
Console confirms acceptance and the release is actually available.

Completed checks and genuine emulator captures: [`qa/VALIDATION.md`](qa/VALIDATION.md).

## Licenses

© OpenStreetMap contributors, ODbL. DEM: AWS Terrain Tiles (primarily SRTM).
Names/colors: official MTA map as described in the existing research. Font licenses
are in `licenses/`. Dependency licenses remain those of their upstream projects.
