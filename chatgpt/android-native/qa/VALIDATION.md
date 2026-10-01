# Android validation — 0.1.0 / 1

Date: 2026-09-30. Website baseline: `27e8725def879515f98219b981c3097690842b46`.

| Check | Result |
|---|---|
| Native data unit tests | 4 passed: exact counts/partial flags, little-endian DEM/interpolation/bounds, coordinate projection, geometry/video/flight consistency |
| Instrumented native flows | 2 passed on Android 10 / API 29 x86_64 emulator: home/ridge/flight → 3D map → search → select → favorite → partial details → top view → home; camera controls/Kobi/theme |
| Release build | `lintRelease`, `assembleRelease`, `bundleRelease` passed; R8 and resource shrinking enabled |
| Android lint | 0 errors, 20 warnings; dependency/update notices, KTX/style, API checks, custom-view constructor and legacy backup behavior. No ignored lint errors |
| Release signatures | APK verified with Android apksigner; AAB signature verified with jarsigner (normal self-signed upload certificate) |
| Play bundle structure | Validated successfully using AGP's bundletool 1.18.1 |
| APK alignment | `zipalign -c -P 16 4` passed |
| 64-bit native libraries | All ELF LOAD segments in the packaged arm64-v8a and x86_64 libraries use 16 KiB alignment |
| Shared source data | All four JSON files in both release packages equal `site/data/` byte for byte |
| Existing website | No tracked file under `site/` differs from the baseline commit |
| Genuine captures | Home, light/dark 3D maps, top view, selected piste and partial details in `screenshots/`; captured from the running app, not mockups |
| Final release smoke check | Signed, minified APK installed and launched on the emulator. Privacy/footer and system-bar adjustments were added after the two main flow tests and checked separately in the release build |
| Owner's physical-device feedback | Pini installed the delivered APK after allowing installation outside the store and reported that everything feels excellent. This is qualitative feedback; frame times, memory, battery and lifecycle measurements remain open |
| GitHub write access | Verified on 2026-09-30 after Pini installed the connector on the repository owner's account; creating the README blob succeeded |

APK: 2,357,668 bytes. AAB: 4,592,418 bytes.

SHA-256:

```
APK eb4aaf24fdb1d307fe534ff35f701b0f3430ef8979ad82b3a40537d29f8079fb
AAB 7869f4ffb7420deed01cf9479566895166ed1c2e21799e300ee4cda0f96dd3a5
```

## Remaining before production

The emulator used software graphics and CPU emulation; its speed is not evidence
of phone performance. A physical midrange Android phone must still be used to
measure orbit/zoom frame times, cold start, memory, battery/thermal behavior,
background/resume and rotation. Also verify accessibility/larger fonts and the
external video player on target devices. An internal Play test/pre-launch report
and the account's applicable testing requirements remain.

Store publication is pending authenticated Play Console access and final
developer identity, support email and hosted privacy policy. Pini deferred
developer-account signup until later. The previous GitHub HTTP 403 write block
was resolved by installing the connector on the repository owner's account.
