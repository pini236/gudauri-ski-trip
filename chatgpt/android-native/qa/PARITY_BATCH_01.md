# First Android parity implementation batch

Date: 2026-10-01. Website reference: `d0a2e88dbc7802a100fe997c8c3b350913ad80a1`.
Status: source implementation and domain verification; **not a compiled or released app**.

Update: this is the historical first-batch report. The build blocker has since
been resolved and meeting features added; current results and remaining limits
are recorded in [batch 2](PARITY_BATCH_02.md). The failure statements below record
what happened at the time, not the environment's latest build status.

## Implemented in source

- Trip: destination, airline, airport codes, baggage, inclusive ski-day range,
  full return flight, six members, and support for no return flight.
- Lift: nullable cover information and source height difference; JSON null is
  no longer displayed as the text `null` in optional lift fields.
- Piste/research: source names, per-segment OSM difficulty, confirmation flags,
  and the missing-piste inventory. No geometry has been invented or changed.
- Video: author, research timestamp, publication estimate and evidence,
  newest-research-first order, preserving all 43 associations / 37 distinct URLs.
- Loading: independently optional trip/video content; explicit validation of
  required terrain dimensions, sample bytes, contours, coordinates and keys;
  coroutine cancellation is not converted into an app error.
- Resources: one read-only Gradle sync task for data, 14 panoramas, three ticket
  WAVs, five game thumbnails and local license notices; output is Android build
  assets only. Thumbnail packaging does not implement games.
- Time: injectable pure Gudauri sunrise/phase calculations, auto/day/night,
  preservation of old explicit theme values, minute updates while foregrounded
  and immediate refresh on resume. The clock stays real in forced day/night.
- Home: local cross-faded panoramas with a terrain-skyline fallback, peak labels,
  stars/moon/static snow, actual Gudauri time, updated trip content and counts.
- Flight: complete switchable outbound/return passes, independent per-flight
  day count, nonnegative countdown labels, tear/restore interaction, local
  ticket sounds, platform haptic feedback, and visible attribution/license links.
- Navigation/link foundations: explicit home/map destinations and saved page
  content, accurate run sharing, strict URL parsing, manual link import,
  incoming view intents, and exact run selection. No domain association or
  website change; automatic link opening is not promised.
- Profile: longest line chosen by geometric length instead of vertex count,
  ordered downhill without joining separate segments; polygon-only content
  shows unknown profile metrics instead of misleading zeros.

## What was actually verified

| Check | Result |
|---|---|
| Original domain tests in isolated pre-change snapshot | **4 passed** |
| Current domain suite | **22 passed**, including the original four |
| Compiler/runtime for domain tests | Kotlin **2.2.20**, Java **17.0.16**, JVM target 17 |
| JSON implementation | Real JSON-java **20250517**, built from upstream source tag; no JSON test double |
| Test runner | JUnit **4.13.2**, Hamcrest **1.3** |
| Android build | **Blocked before app compilation**, Maven Central HTTP 429 for required dependencies |
| Follow-up offline Android build | Blocked: required artifacts are not cached |
| Connected Android tests | **Not run**; no emulator/device was available |
| Compose UI compilation / lint | **Not reached** because project dependency configuration failed |
| New APK/AAB | **Not produced**, no signing or publication attempted |
| Website integrity | All **35** recorded source/asset hashes unchanged; no tracked changes outside `android-native/` |
| Local checks | Document links valid; `git diff --check` and domain-runner shell syntax check passed |

The domain runner compiles the real production domain files and the actual test
suite, then executes it against the website data as read-only input. It does not
replace Android compilation, screen tests, lifecycle/device checks, sound/haptic
verification or hardware performance measurements.

SDK API 36, build tools 36.0.0, platform tools, Gradle 8.13 and JDK 17 were
prepared outside the repository. The baseline build ran against a temporary
source snapshot so implementation did not modify files during that run.
Maven Central returned `Your ip has exceeded rate limits`; public mirror
alternatives were unavailable in this environment. Project repository settings
and dependency versions have not been changed to work around the failure.

The two original instrumentation cases were adapted for three-state themes and
scroll-preserving navigation. Two new cases cover the return pass/stub restoration
and exact run-link import. **All four remain unexecuted.** Historical screenshots
in `qa/screenshots/` are not presented as screenshots of this batch.

## Reproduce the domain check

Install/extract the official Kotlin 2.2.20 compiler, Gradle 8.13 and JSON-java
20250517 source tag outside the repository. Set these explicit paths, then run
from `chatgpt/android-native/` (relocated after this historical run):

```sh
GUDAURI_JAVA=/path/to/jdk17/bin/java \
GUDAURI_KOTLIN_HOME=/path/to/kotlinc \
GUDAURI_GRADLE_LIB=/path/to/gradle-8.13/lib \
GUDAURI_JSON_SOURCE=/path/to/JSON-java-20250517 \
bash tools/run-domain-tests.sh
```

Actual output after the final source changes:

```text
JUnit version 4.13.2
......................
OK (22 tests)
```

Once dependency access works, run the real Android checks before treating the
screens as validated or delivering an installer:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

## Still pending

This is the first batch, not completion of the parity plan. Meeting screens,
lift-status UI/source integration, the game catalog and all five native game
engines are not implemented. Interactive/slope-colored profiles, synchronized map
markers, briefings/comparisons, downhill camera flight, polygon GPU rendering and
sun-driven terrain lighting also remain pending.

The home/card implementation still needs compiled-device visual review,
village-light/glow and motion refinements, paper/perforation/ridge decoration,
large-font/small-screen coverage and real sound/haptic checks. Saved favorites
keys and explicit theme preferences are retained in source, but installation
over the user's existing signed binary has not been tested. App version and
signing identity are not claimed to match the uploaded file.

Next: unblock the real build, resolve any compilation/UI failures, run the four
instrumentation cases and capture current screenshots; then continue the
[remaining plan](../docs/ANDROID_PARITY_PLAN_HE.md). The website and its shared
data remain unchanged.
