# Work status

**This document is mandatory for everyone working in the repo: read it before starting, and update it before finishing.**
The full rules are in `CLAUDE.md`, in the section "Mandatory for everyone working in the repo".

Last updated: 2.11.2026 (round 21 on the site, route name by name, Gudauri data fixes; earlier 30.10.2026: decision 76: speed measurement and ski tracking; decision 75: the short link to the app, `/app`; decision 72: round 26, the Google Play sign; decision 71: iOS starts now, in the cloud; Apple: the guide to the account and the App Store, `docs/APPLE.md`; decision 69: round 24, like the official map, approved; decision 68: prototype of several ski resorts, Sölden on the site; decision 67: the map is not on the home screen; decision 66: infrastructure is a DevOps person; decision 65: round 20 approved; decision 64: no source/certainty in the panel; decision 63: no panorama; decision 62: round 18 approved; decision 61: vision for every ski resort; decision 60: store page and the "קבוצה" sign unchanged)

## Who is working on what now

**Mandatory (decision 45):** before starting work on an area, check here. Area taken: do not enter, send a message to the session that holds it. Area free: register the session and push to the main branch before starting. When done, return it to "free". The full rules are in `CLAUDE.md`, rule 11.

| Area | Folders | Who is working now | Session id | On what |
|---|---|---|---|---|
| Site | `site/`, `tests/`, the site's `tools/build-*` free (last session: `session_017QrNw9uUUH47AzK3jXibqU`) ; the feasibility session (buildings) only in `buildings3d.js` and `buildings.json` | `session_017QrNwACAZiKEHHSCgbVj19` (buildings) | (2.11.2026) Round 21 on the site merged, including the open end (`open`). The buildings in 3D behind `?b3d=1` (decision 77), in `site/js/buildings3d.js` and `site/data/buildings.json` only: the western cluster, roads and signs |
| Android | `app/android/` | UX pass part 1 (the menu), Sonnet, branch `claude/android-ux-part1`, shared with the other UX-pass part sessions (files: `redesign/LiveShelf.kt`, `LiveVillage.kt`, new menu file) (27.11.2026); otherwise free (last session: `session_01Gpr4L7H5gxaycQcxMRD5cE`; 22.11.2026 `is_internal` and `install_channel` in measurement, see the log; the POC code in `src/preview` is with the POC implementer) | `session_01Gpr4L7H5gxaycQcxMRD5cE` | **(13.11.2026)** diagnostic log in the test build, the counter counts from the touch, motion while waiting for the first reading and cropping of the day map merged to `main` (`23357b4f`); round 38 (minimum map size, "מחפשים מיקום") deferred to the design-language refactor. **(2.11.2026, after round 29)** story and insights, descent card, records, sample day, share image and route names on the map merged (after a green run); **remaining:** replay in 3D, the open end `open`, and testing on a phone. Round 31 and round 34 merged. (Decision 76, 30.10.2026) ski day: the recorder, the analyzer, the moments **and the round 29 screens** (entry "מקליטים", live screen, end of day, my days) are built, behind `BuildConfig.DAYREC_UI` (test build true, store build false), and therefore merged to `main` with nothing visible in the store. **Do not upload to Google Play a build with the feature until Pini submits the foreground service declaration and the privacy line is published (the manager session coordinates).** **In parallel (13.7):** accessibility, on branch `claude/android-a11y`, merges to `main` after a green run . **(1.11.2026)** the "תמיכה" row in About merged, accessibility 13.7 finished (except large text), and now performance (cold start and games) and gaps versus the site **(2.11.2026)** round 31 (places at stations), round 34 (snowball fight, corrected D) and showing the route name by `name` (PARITY section 17, `9491403a`) merged to `main` after a green run; the ski expert's data fixes merged, and the emulator script updated (`b7c771e6`). **Next:** route names of round 21 (PARITY section 16), after the site merges. |
| Android: shelf v2 (P.4, P.5, P.6) | `app/android/` only `redesign/LiveShelf.kt`, `ShelfRules.kt`, `RunsButton.kt` flight target, the layers button and chip row in `LiveMountain.kt`, `LiveLens.kt` | the shelf v2 builder (Sonnet), branch `claude/android-shelf-v2` | `session_017QrNw6HwU9N9gpr1yy4khY` | (27.11.2026) six chips that order and paint, strip panel, cards with the big number, layers removed into the shelf, mountain always full-screen |
| Android: top pill (P.25) | `app/android/` only `redesign/TopPill*.kt`, the pill block in `LiveMountain.kt`, `Weather.kt` | free (merged 27.11.2026, `android` run 1041 green; last session: `session_017QrNwA2kWwzvtqMVDcqxWn`) | | |
| Android: day story and share, part 2 (P.12, P.15) | `app/android/` only `redesign/DayStory.kt`, `DayShare.kt`, `StoryRules.kt` (+ tests) | the part-2 builder (Sonnet), branch `claude/android-story-share-2` | `session_017QrNw8zSLvXvfUsWBFVG8Z` | (27.11.2026) the approved parts part 1 left out: camera glide, pin label, DS5, finale picture, in-card video controls, queued buttons, saved tap and haptic |
| Android: my days (P.23, P.24) | `app/android/` only: `KeepRules.kt`, `EmptyEnd.kt`, `DayList.kt`, `RecordsScreen.kt` | free (merged 27.11.2026 in build 1079; last session: `session_017QrNw8xyHfXbaYkyWDT8HD`) | | |
| Android: runs button v4 (P.3) | `app/android/` only `redesign/RunsButton.kt`, the `Dock` in `LiveShelf.kt` | free (merged 27.11.2026 in build 1079; the thumbs asset waits for the architect) | | |
| Android: run card rest (P.9) | `app/android/` only `redesign/LiveRunFull.kt`, `RunCardView.kt`, `PeakScreen.kt` | free (merged 27.11.2026 in build 1081; last session: `session_017QrNw7gLPHLsFLw4ucLX1U`) | | |
| Android: redesign, second implementer (Sonnet) | `app/android/` only the four screens: pulsing search, opening on the picture, record button RX3, end screen without descents; branch `claude/android-redesign-s4` | the second implementer | `session_017QrNw6gpsVzdKmiHmWpPrc` | (25.11.2026) does not touch the seam (`ringband`, Ring shaders) or the `core` part in `qa/run.sh`: those are with the implementer on Opus |
| Android: run card (P.9) | `app/android/` only `redesign/LiveRunFull.kt`, `RunCard*.kt` | free (first part merged 27.11.2026 in build 1079) | | |
| Server | `server/` | the server session (Sonnet) | `session_014ke3f3CtfjLp4WS92ScS6L` | (2.11.2026) the MTA page parser and the matching table were written (`mta_status.ts`, off, not connected to `fetch_status`) in a separate session, branch `claude/mta-status-parser`, id `session_017QrNw7v1AacjP6x3vM7dbp`, which returns the row to "server" on merge. (1.11.2026) preparation for lift status for December (S-20); after it preparation for an encrypted daily backup (R-1), **off and without deployment**. `/api/weather` is running; `/api/status` waits for December. Actions on the live server only with Pini's approval |
| iOS | `app/ios/` | free (last session: `session_012Y5jLfzBy1HzYPy4JE9v4q`, 22.11.2026: `is_internal` and `install_channel` merged) (earlier: rounds 39 and 40 merged) | `session_012Y5jLfzBy1HzYPy4JE9v4q` | Pini: "take it all the way, a finished product" (21.10.2026). On the main branch all the parts: home page, the map (top view and 3D), lift status, weather, "where am I", the meeting point, the games, About, the trip form, the group and account, and measurement. **(13.11.2026) the ski day is complete in `main`, behind `#if DAYREC` and in the `variant=dayrec` build only** (the recorder, the analyzer, the moments, round 29 screens, sample day, the story and insights, descent card, records, share image, replay and a button on the map); the regular build (1.0.x) does not change, and `claude/ios-dayrec` is no longer needed. **Remaining:** Live Activity (id, profile and secret are with Pini), exporting the raw log to testers (not required for submission), Sentry for iOS, and restoring text scaling in `SkiType.text()`. **Submission of 1.1:** the manager session runs `ios-release` with `variant=dayrec` to TestFlight (Pini approved, 13.11.2026); nothing is sent to Apple without Pini's gate (the store declarations and the privacy line) |
| Canvas | `design/`, the canvas | free | | |
| Canvas: redesign | `design/redesign/` and a separate canvas (https://claude.ai/artifact/8NrAKy97KWRj3JWcxx41yQ) | the designer is working (`session_017QrNwAPwyrY12TRdaupPSb`): **the completion round approved in full (23.11.2026, decision 88);** the "המפה" page in the canvas (version 31): what was drawn and what remains, the navigation map, and eight open decisions with a recommendation; **parts 4 to 8 (on the mountain, what was described in words, the edges, languages and platforms, for everyone and on every screen) are in the canvas and handed over, and all approved (Pini, 23.11.2026); marked so in the canvas (version 43);** **only the games remain for the end (R11, R12, U12, U13 and the games from the inside), when Pini asks;** now (24.11.2026): only design of new and complex things; the design reviews of the implementer's builds moved to the design reviewer (`design/redesign/REVIEW.md`) | | |
| Design reviewer (Opus 5.5) | design review checks of the implementer's builds, read only | the design reviewer (new decision, 24.11.2026) | `session_017QrNw7nFaumJ4hZW4rcMnk` | one review per part, per the boards and handoffs; parts 1 and 2 approved, parts 3 and 4 on the way. The designer (Fable 5.2) only designs new and complex things |
| Apple: account and App Store | `docs/APPLE.md` | the account-guidance session | `session_01AjoD5yuhiyvk3kHGBuQVt1` | **Version 1.0 of iOS was sent for Apple's review (1.11.2026, build 17, manual release)**; **until the answer: no other build is sent to Apple, and version 1.1 (ski day) does not enter `main`;** next: Apple's answer (up to 48 hours), manual release with approval, and then Sentry for iOS, sign in with Apple on the site and version 1.1 with the ski day |
| Infrastructure and CI | `.github/workflows/`, `docs/OPERATIONS.md` | the DevOps person (permanent) | `session_01B7WsKFuWhyvYcU9VQLfMRW` | (Decision 66) the emulator pipeline (retry, `auto`, part pairs) and `ios-release.yml` and `ios-store-shots.yml` (App Store screenshots) merged. Live services: read only |
| Architecture | `docs/ARCHITECTURE.md`, `docs/ROLE-ARCHITECT.md` | the system architect (permanent) | `session_01JUxNys6FzfkFTAFyHbYdCe` | The baseline is done (`docs/ARCHITECTURE.md`). A-2, A-6 and A-7 approved (decision 58); the contract of `/api/weather` and `/api/status` approved with corrections (5.10.2026). Available for questions and design reviews |
| Ski day: algorithm | `tools/dayrec/`, `tools/fixtures/dayrec-*` | free (last session: `session_017QrNw79sojt4mVesBZ6HbJ`) | | Car driving, the moments engine (30.10.2026) and the field-day command (`field.py`, 31.10.2026) on the main branch; next: Android and iOS implement against `dayrec-moments.json`, and the wording in the language file after Pini's tone |
| 3D map: buildings | `research/3d-buildings.md`, `design/round30/`, `site/data/buildings.json`, `site/js/buildings3d.js`, `tools/build-buildings.py`, `tools/buildings/` | the feasibility session | `session_017QrNwACAZiKEHHSCgbVj19` | (Decision 77) the realistic POC on the main branch, behind `?b3d=1` (1.11.2026); waiting for Pini's response and for pictures of the buildings without a picture. Before removing the flag: 30 frames per second on a mid-range phone |
| Places at stations: data | `research/places.md`, `tools/build-places.py`, `research/places/` | free (last session: the ski expert) | `session_018h1GMPbYZpMqn9Hk2S3fXL` | (1.11.2026) advance preparation for round 31, and data only, without `site/`: certainty A or B only. The file structure first goes through review with the architect, and merges only after Pini approves round 31 |
| Languages | `i18n/` | shared: each session adds only keys of its own area | | |
| Documents | `docs/`, `README.md`, `CLAUDE.md` | shared: merge the main branch before every push, and on conflict keep both sides | | |

## The ordered work list (2.11.2026, at Pini's request)

**This list is the order in which we go all the way, from the work of 30.10 to 2.11.2026 and what remains of it.** The details of each item stay in their place (`docs/PARITY.md`, `docs/ROADMAP.md`, "everything waiting for Pini" below); here only the order, the owner and when the item counts as done. Mark `[x]` when finished. Created after an alignment pass over `docs/PARITY.md`.

**Part A: can be taken all the way now, without a Pini gate** (in order)

- [x] 1. **(done 2.11.2026)** **Content and languages: the wording of "the story of the day"** (`moment.*` and `insight.*`, one voice, from `docs/handoff/2026-10-30-dayrec-moments-copy/insights.json`) into `i18n/strings.json` in four languages. Done when: `python3 i18n/check.py` is green. **Done:** 103 `moment.*` keys in `i18n/strings.json` (each version as a numbered key `.1`, `.2`; the app picks `seed` modulo the number of versions), outside the site bundle (`tools/build-site-strings.py`), and the Android and iOS builds pass. Item 2 is now open.
- [x] 2. **(done 2.11.2026, including replay in 3D and the "without permission" screen; only the raw log export remains, which waits for consent)** **Android: what remains of the ski day** (`docs/PARITY.md`, A-44), all behind `DAYREC_UI`: the story and insights screen, a card for each descent, personal records, end-of-trip card, sample day in history, share image, map under the live screen, weak GPS and no-permission states, hourglass `#A87300`, events `story_view` and `story_share` (first in `docs/GROWTH.md`). Done when: the emulator screenshots are green and every row in A-44 is marked.
- [x] 3. **(done 2.11.2026, including the open end, the arrows and the thin line)** **Android: route names on the map** (round 21, A-45): a decoding test of `label` and `shared`, and then drawing the name beside the line. Done when: map screenshots in Hebrew and English, day and night.
- [x] 4. **(done 3.11.2026, `10d59a25`: inert code in `main`, `ios.yml` green; the architect's condition 2 was met the same day: a simulator-only `variant=dayrec` run green, `d3eac7eb`, DevOps)** **iOS: merging the ski day code to `main` without it reaching Apple's review** (Pini's question, 2.11.2026). Merging to `main` uploads only to TestFlight (`ios-release.yml`), and submitting for review is a manual action by Pini. Therefore it is allowed to merge **inert code**: the domain, the log, the analyzer, the moments, the tests and `DayRecorder`. **Does not enter until 1.1:** the `UIBackgroundModes: location` rows and the temporary permission in `app/ios/project.yml` (a separation mechanism, for example a separate config file for the 1.1 build), and a guard in code before `allowsBackgroundLocationUpdates` (today it is read unconditionally and crashes without background mode). `AppModel` creates the recorder only when the `dayrec` capability exists, and it is already registered for Gudauri, so the run gate has to be in the build too and not only in the data. **Before code: an architect design review of the separation mechanism** (release chain and privacy, rule 11). Sent to the iOS session. Done when: `ios.yml` is green, the build's `Info.plist` has no background mode, and `main` contains the code. **Until Apple's answer on 1.0 arrives: do not merge `claude/ios-dayrec` as is.** **(2.11.2026) the separation pipeline is merged (`02d317ff`, DevOps, with the architect's approval): manual `variant` and a `plutil` gate; remaining are `project-dayrec.yml` and the iOS code from the iOS session.**
- [x] 5. **(done as far as possible; run 255 of `main` green. I-11 stays on the branch until Apple's answer.)** (3.11.2026: I-10, I-9 and I-12 merged to `main` after green runs on the branches; I-11 remains, which was built on the branch and is not merged until Apple's answer)** **iOS: closing gaps I-9 to I-12** (places near the stations, route display name, route names, round 34), each on its own branch and merged after a green run. Done when: `docs/PARITY.md` is marked.
- [x] 6. **(done 2.11.2026)** **Ski expert: the `open` field** (open end for route names): `research/map-open-ends.md` (five ends with a source), the architect approved, and the site session implemented and merged (`3f21262c`): manual `open: [{at,conf,src}]` in `tools/build-labels.py`, shared values in `tools/fixtures/labels-open.json` (`python3 tools/check-labels.py`), a fade of the last 130 m of the line in top view, only from medium (Soliko 2, Firni 2, Shino, New Goodaura); Firni 1 and `u1234557981` as low in the data without drawing; Sportuli 2 did not enter; not built in 3D. The site tests are green on the branch; the `main` run of the merge is in progress. **Remaining:** Android (A-45) and iOS (I-11, no merge until Apple's answer), rows in `docs/PARITY.md` section 16.
- [ ] 7. **Server: encrypted daily backup (R-1)**: the code is ready and off (`server-backup.yml`). Done when: only after a key from Pini (part B); until then nothing.
- [~] 8. **Companion character:** the first loop, the leaning pose and the "standing leaning" loop (step 3, 2.11.2026) were received; waiting for more poses from Pini's tool, for a short sequence (up to 36 frames, without the hand gestures, per the architect's ruling) and for feedback on the canvas (`design/characters/poses.md`); the license is closed. Done when: a pose for every row in the list, and then the architect's review (A-10 to A-12).
- [ ] 9. **Buildings in 3D:** continuing the POC per Pini's street pictures and the photo list for the trip; before removing `?b3d=1`: 30 frames per second on a mid-range phone.
- [x] 10. **(done 3.11.2026; "the 47 decisions" were a list in the team reports from 4.10.2026 and not a document in the repo, so there is nothing to mark row by row. The decisions themselves are recorded in the table "decisions made" and in the open questions; they were checked mainly against what has been closed since)** (earlier: partial, 3.11.2026: TEAM, GROWTH and the FEATURES gates were updated to what is clear; at the top of the first section in `docs/QA.md` a status note against 1.0 was added (not row-by-row marking, since nobody checked each row on the build itself); the "who is active" section in `docs/TEAM.md` was checked and is current; the 47 decisions were not checked)** **Review of the team documents not yet checked row by row:** the 47 decisions in `docs/TEAM.md` and `docs/GROWTH.md`, the version 1.0 gates in `docs/FEATURES.md`, and the pre-submission checklist in `docs/QA.md` (39 rows, none marked). Done when: marking what is already right and what is out of date.

**Part B: Pini's gates (start early, because they are what delays)**

- [x] 11. **(6.11.2026: Pini released, and the status in App Store Connect is "Ready for Distribution"; it may take up to 24 hours until the store page opens)** Apple's answer on 1.0. The link: https://apps.apple.com/app/gudi-gudauri-ski-map/id6825506333 (after release).
- [x] 12. **(done 13.11.2026 per the architect: the marking came off and the ski day is live on Google Play since 14.11, build 0.1.533)** **The privacy line for the ski day:** (6.11.2026) merged to `main` and published at `/privacy` in four languages with the marking "will enter in the next version" (Pini: the marking stays and comes off on the day the feature is turned on); both wording fixes are included. The "field testers" line was not published (Pini: postpone, until Google and Apple approve 1.1 and before recruiting the testers in December).
- [~] 13. **(Google Play declaration approved, per the architect, 26.11.2026; Apple Live Activity items remain)** **Store declarations:** Google Play, foreground service declaration with a video (`docs/PLAY.md`, section 5); Apple, a second id for Live Activity, a profile and a seventh secret (`docs/APPLE.md`, 5.9).
- [ ] 14. **Exporting the raw log to testers:** approval of the consent wording, the line in the policy, and checking that training is off in the account (`docs/handoff/2026-10-31-tester-log-consent/README.md`); without it calibration from the field is impossible.
- [ ] 15. **The tone of "the story of the day"** (one voice, approved in direction); **canvas approval:** round 32 (character), speeds table and people on the map (waiting, decision 80).
- [ ] 16. **Cleanup:** deleting merged branches on GitHub (Pini); read keys for PostHog and Sentry; sign in with Apple on the site (not urgent).
- [ ] 17. **Phone tests:** which iPhone tests from TestFlight, who the testers in December are, one skier with two phones on the trip (`docs/QA.md`).

**Part C: in December and in the field**

- [ ] 18. **Lift status (S-20):** an MTA page from an active day, connecting the parser (off and merged) to `fetch_status`, deployment with Pini's approval.
- [ ] 19. **Ski day calibration in snow:** driving in a car first, then the testers and the trip (11 to 14.1.2027), and at the end the field test.
- [ ] 20. **Places at stations in the field:** checking toilets, signs and restaurants near Bombora, and floors for buildings.

## Decisions (index)

Full text: `docs/DECISIONS.md`. Read a decision only when the task needs it. A new decision: add the full row there and one line here.

| # | Topic |
|---|---|
| 1 | Source of truth |
| 2 | Shared data |
| 3 | Privacy |
| 4 | Address |
| 5 | The next part of the site |
| 6 | Supabase project |
| 7 | Vercel protection |
| 8 | The mountain's story |
| 9 | Hotel location on the map |
| 10 | Slope coloring thresholds |
| 11 | Cancelled |
| 12 | Target audience |
| 13 | New |
| 14 | Android |
| 15 | Approved screens |
| 16 | Round 3 |
| 17 | Games on the site |
| 18 | Canvas before code |
| 19 | The flight card |
| 20 | Round 8 |
| 21 | About and settings |
| 22 | New original app |
| 23 | Growth |
| 24 | Management in monday |
| 25 | Native apps |
| 26 | Site and app |
| 27 | Users and groups |
| 28 | Server |
| 29 | Schedule |
| 30 | The ChatGPT folder |
| 31 | Distribution, signing and stores |
| 32 | Measurement and languages |
| 33 | Google Play: policy and language |
| 34 | Repo structure |
| 35 | The app does not imitate the site |
| 36 | Round 10 |
| 37 | Visitor ID on the site |
| 38 | Distribution and recruitment timing |
| 39 | Speaker check |
| 40 | Server code |
| 41 | Language picker on the site |
| 42 | Round 12: account and group on the site |
| 43 | Round 13: small fixes |
| 44 | Round 14: group management on the site |
| 45 | Several sessions working in parallel |
| 46 | Answers on site gaps |
| 47 | Language in the app |
| 48 | Language on the site: English by default and a fixed button |
| 49 | Round 16: polishing the design on the site |
| 50 | Alignment between the clients |
| 51 | System architect |
| 52 | The professional team |
| 53 | Permanent guard in every area |
| 54 | A very lean team |
| 55 | No names in the games in the app |
| 56 | Answers on the team's decisions |
| 57 | Full alignment with a single owner, and weather and location before the season |
| 58 | Round 19, weather and location, and open questions |
| 59 | "איפה אני" and the weather on the site, and the privacy line |
| 60 | The store page and the "קבוצה" sign |
| 61 | Vision: every ski resort |
| 62 | Round 18: alignment in the canvas |
| 63 | The panorama |
| 64 | Source and certainty in the run panel |
| 65 | Round 20: less text and the trip form |
| 66 | Cloud infrastructure and operations is a DevOps person |
| 67 | The map is not on the main screen |
| 68 | Prototype for several ski resorts |
| 69 | Round 24: like the official map |
| 70 | Measurement, recordings and test improvement |
| 71 | The iPhone starts now, in the cloud |
| 72 | Round 26: the app on Google Play |
| 73 | Round 27: name and sharing for everyone, and opening to Google |
| 74 | The icon in the tab and the store page |
| 75 | Round 28: short link to the app |
| 77 | Answers on the canvas and forward (31.10.2026) |
| 76 | Speed measurement and ski tracking |
| 79 | All sessions on Sonnet |
| 80 | The team works freely until a Pini gate |
| 81 | No technique feedback |
| 82 | Every action is documented |
| 83 | Redesign: round 3 chosen, "The mountain, the choice and the day" |
| 84 | Redesign: round 4, surf day, approved |
| 85 | Redesign: Android POC, one screen |
| 87 | Fable only for building the missing screens |
| 88 | Redesign: the completion round approved |
| 78 | App Store: version 1.0 without the surf day, and privacy for iPhone |
| New decision | A menu instead of the village as the menu (UX pass) |
| New decision | Separation between design review and new design |

## Stages

The full history of the stages (0 to 15) is in `docs/STATUS-ARCHIVE.md`. The current tracking is in the "organized work list" above and in "everything waiting for Pini" below.

## Flight details (from the group's booking confirmation)

Not stored in the repo: passport numbers, birth dates, phone, email, payment details and booking number.

| | Outbound | Return |
|---|---|---|
| Date | 10.1.2027 | 15.1.2027 |
| Route | Tel Aviv to Tbilisi | Tbilisi to Tel Aviv |
| Departure | 16:00 | 01:35 (in the night between 14 and 15 January) |
| Landing | 20:35 | 02:15 |
| Flight number | 897, operated by Smartwings | 892, operated by Israir |

- Airline: Israir. Economy class.
- Baggage in the booking: carry-on only.
- **Full ski days:** 11 to 14 January, meaning four days. On arrival day we land at 20:35, and the return flight leaves at night.
- Documented booking: 5 passengers.

## The group members (6)

Yehuda Wexler, Dubi Albom, Sharulik Lapkovich, Moishi Bokchin, Yehuda Hirsch, Pini Zolberg.

**Decided:** to display the full names on the public site (in stage 3).


## Open now

**Archive:** stages 0 to 15, old items from "open now", closed decisions, and the log before 24.11.2026 moved to `docs/STATUS-ARCHIVE.md` (25.11.2026, to save tokens). Read it only when history is needed.

**Everything waiting for Pini (the single place; updated 31.10.2026).** Every question, dilemma or action waiting for Pini, from all sessions. The recommendation in parentheses, the details in the linked file. A new question from any session goes in here. The shared canvas: https://claude.ai/artifact/TVdeDbFBhvFDZdhCKjcys4 (version 119).

**A. What is blocking work now (most urgent)**
- [ ] 3. **The tone of the moments** (round 29, question 11): the texts are ready in three tones and four languages (`docs/handoff/2026-10-30-dayrec-moments-copy/`), and displayed in the canvas in row t.

**B. Actions only Pini can do**
- [~] 4. Apple: the three signing files are backed up and the Mac was cleaned (31.10.2026). **Remaining:** remove the Apple ID from Xcode when there is battery (`docs/APPLE.md`, 4.2 e). **Pini (2.11.2026): leaves it for now.**
- [ ] 5. **After there are screens for the surf day:** the foreground service declaration in Google Play, with a video (`docs/PLAY.md`, section 5; the text and script are ready).
- [ ] 6. **After there are screens for the surf day:** Apple, for the lock screen (Live Activity): a second identifier `io.github.pini236.skiapp.live`, a second profile and a seventh secret (`docs/APPLE.md`, 5.9).
- [ ] 7. **To make sure it was done (from 21.10.2026, for iPhone):** a `gudi-ios` project in Sentry with IP address storage prevention; an iOS client in the Google console, and its identifier in supabase (without it the Google button is hidden on iPhone).
- [ ] 9. Read keys for PostHog and Sentry, so the team can read live data; and a check on the device of screen recording in the games in Android (`docs/handoff/2026-10-06-observability-ci/README.md`).
- [ ] 10. **Tests on the phone:** an invitation to a second phone and "אני על אותה טיסה" with a friend; the games in Android (feel, sound, vibration and performance); the sounds and vibration of the flight card.
- [ ] 13. **Sign-in with Apple on the site** (`docs/APPLE.md`, step 7 and section 3): a service identifier for the site, a key, and configuration in supabase. On iPhone it already works without it. The secret on the site expires after 180 days, and needs renewing. (Not urgent; after the iPhone submission)

**C. Round 29: surf day — fully approved on 1.11.2026 (question 1 per the recommendation, question 9 a separate map, the rest as in the boards; the items below are kept as documentation)** (`design/round29/README.md`):

**D. Buildings, roads and paths in 3D in New Gudauri** (`research/3d-buildings.md`, section 5; the mockups in `design/round30/` and in the canvas, page "סבב 30"; the architect approved the POC on the site with four conditions):

**E. Round 31: food and places at the stations** (`design/round31/README.md`, `research/places.md`) — **fully approved on 1.11.2026, all per the recommendations** (the architect approved the file with five conditions); handed to the ski expert (merging the data), to the site, to the Android and to the iPhone (the iPhone does not merge until Apple's answer on 1.0). The items are kept as documentation:

**F. Companion character** (`design/characters/README.md`; the sketches in `design/characters/shots/` and in the canvas, page "סבב 32"):
  - [ ] 2. The personality (dry, laughs at itself)
  - [ ] 3. A name for the character (not now)
  - [ ] 4. Where (site and both apps; in surf day moments only in the apps)
  - [ ] 5. Order (surf day first, then the home page and empty states)
  - [ ] 6. "פה נפלת": Pini asked for a joke, branding recommends neutral (a light text with "לא נפלתי", and the character shakes off snow without words)
  - [ ] 7. Final drawing (sketches now, final drawing after the design language is chosen, J below)
  - [ ] 9. **Who draws the character** (the illustrator, 31.10.2026: for the level Pini asks for, a full human character that stays the same character in every pose, he won't get there with SVG and code): A. a human illustrator, paid and reached externally, with a character sheet, layered files and transfer of rights, while our illustrator writes the guidelines for them; B. an image generator that Pini operates himself (hard to keep the same character; check commercial license); C. a combination: a generator to choose a direction, and a human illustrator for the final character. (A, or C)

**G. AI** (`research/ai.md` and `docs/AI-PRODUCT.md`, section 5):
  - [ ] 1. A paid account with a cloud AI provider (not before the season)
  - [ ] 2. Day data to the cloud only with consent, and never coordinates (yes)
  - [ ] 3. Don't lead with the word "AI" in marketing (yes)
  - [ ] 4. Bring back the route planner (decision 11) as part of "מה עכשיו" (yes)
  - [ ] 5. One level question at launch, saved only on the phone (yes)
  - [ ] 6. A canvas for a meetup of a group with mixed levels (yes)
  - [ ] 7. What counts as success in the first season (activation on the first day and sharing, not retention)
  - [ ] 8. Your trip (10 to 15.1.2027) as a field test, with three questions for the friends (yes)
  - [ ] 9. Places at the stations as content, not as AI (yes)
  - The tone of "הסיפור של היום" is question 11 in round 29 (C above).

**H. New ideas from Pini awaiting a decision** (`docs/ROADMAP.md`):
  - [ ] 1. **A weekly speed table** for everyone in Gudauri. Conflicts with the architect's condition 9 (no speed tables, for safety), and requires a server and sending data about the person. (A weekly table of cumulative descent or number of runs; speed only within your group. After the decision: architect, legal and canvas)
  - [ ] 2. **A name for a guest at launch**, even without registration (decide together with 1)
  - [ ] 3. **Shared off-piste lines**: check now with the ski expert and legal, or after surf day? (after; meanwhile they are saved only with the skier)
  - [ ] 4. **People on the map**: the scope (name and location only for group members, only for whoever turned it on, for a limited time; for strangers at most a number)

**I. The Gudauri map**

**Not now: other ski resorts** (Pini, 31.10.2026: focus on what is relevant to the app in Gudauri). Kept here so they don't get lost, and are not being worked on:
  - **Round 25, a readable map at a large resort** (`design/round25/README.md`): valleys across the whole mountain (A), a chip for each valley (B), valleys also in 3D and on the phone circling the mountain (D), and the list divided into valleys; "list first" (C) no. (A, B and D)
  - **The next ski resorts** (`research/resorts.md`, "שאלות לפיני"): the order (Mayrhofen first), Hermon (not now), the southern hemisphere (not now), Bakuriani (quietly, when there is a structure for multiple resorts), Israeli demand (counting flights, without reaching out externally).
  - **Sella Ronda:** the Sellaronda circuit on the map (yes, via canvas and architect); a link to the official maps of Val Gardena and Arabba (only Pini can download); languages for the site in Italy (Italian and German).
  - **Data corrections from the ski expert's check (31.10.2026):** in the lower segment of Dantercepies Variante the color is red and not blue; Kids Fun Line La Crusc is a park, and therefore leaves the data, and the tool will always skip anything that is not `downhill`. In 33 and 34 in Sölden and the four runs of Arabba the lines are correct, and therefore stay (`research/resorts/sellaronda-official-compare.md`, `research/resorts/soelden-official-compare.md`)

**J. Design, name and branding**
  - [ ] 1. **A new design language** for the whole site: five languages in a separate canvas (https://claude.ai/artifact/9yxrGgytb2Gmi4i1UqEhST), three questions on the opening page there (which language, what stays from today, and whether it moves to the apps).
  - [ ] 2. **Design language audit:** 15 suggestions in the "מה לאשר" board (https://claude.ai/artifact/TW8xaEsc2EjqodwLcrMzaw, `design/audit/README.md`). Some of them will change if a new language is chosen.
  - [ ] 5. **B9, text in a frame (26.11.2026):** canvas page "ב9 · טקסט במסגרת" (`B900` to `B93`, version 74). Two questions: after 30 s the search line joins "מחפש מיקום" in one frame (`B91`) or a second pill (`B93`) (one frame); the ski pass head stays as is, a page over a veiled mountain (yes).
  - [x] 8. **(Built 27.11.2026: runs button A in `main` since build 1079, not tested on a phone)** **The run picker in the bottom bar (27.11.2026, Pini: "לאן יורדים?" looks like a text field but opens a list):** canvas page "הסרגל: בחירת מסלול (27.11)" (`DK00` to `DK6`, version 88), `design/handoff/2026-11-27-redesign-dock-runs/`. Three questions: which alternative, A a button "מסלולים" with the piste colours, the count and an up chevron, B four colour buttons, C run cards peeking above the bar (A); the word "מסלולים" instead of the question (yes); the count 28 on the button (yes). **Pini (27.11.2026): direction approved (run button A; layers removed into the shelf), wants a more beautiful, experiential v2 (canvas 90); his final look pending.**
  - [~] 9. **The shelf chips and the layers (27.11.2026, Pini: a chip tap shows nothing; the layers are not understood and the column jumps):** same canvas page (`SL00` to `SL8`, version 89), `design/handoff/2026-11-27-redesign-shelf-layers/`. Three questions: every shelf chip both orders the cards and paints the mountain, with a strip of all 28 runs and a "why" line on the card (yes); the layers: A move into the shelf as two more chips and the layers button goes, B stay with a name, a legend in the bar's slot and a column that never moves, C removed (A); "לפי קושי" as the one name (yes). **Approved (Pini, 27.11.2026): he loves the v2 shelf (canvas `SV3`-`SV6`: chips, sorting, the card re-order on a tap) and keeps it as drawn; the layers go into the shelf as chips. NOT BUILT until 27.11 afternoon (the first builder built only the button; the manager had wrongly listed it as waiting for Pini). Builder `session_017QrNw6HwU9N9gpr1yy4khY`, branch `claude/android-shelf-v2`.**
  - [x] 10. **(Approved by Pini 27.11.2026, canvas RK; built 27.11.2026 and in `main`, build 1081, not tested on a phone)** **The run card (27.11.2026, Pini's five notes):** canvas page "כרטיס המסלול (27.11)" (`RK00` to `RK5`, version 92), `design/handoff/2026-11-27-redesign-run-card/`. Four questions: the floating card with the neighbours peeking and the dots under it (yes); the slope-coloured profile with the three numbers (yes); "טיסה מעל המסלול" instead of "לרדת בסרט" (yes); a foot row with video frames, the conditions and an up chevron instead of "סרטונים · 1" (yes).
  - [ ] 3. **The app name:** Pini wants a creative name; today `Gudi: Gudauri Ski Map` (decision 56: stays for now). And until there is a final name: translate the name that appears under the icon per language?

**K. Small old items**
  - [ ] 1. Service providers and advertising: the questions in `research/providers.md`, "שאלות פתוחות לפיני" (the criterion for recommendation, heli-ski and paragliders). After the system works end to end (decision 38).
  - [ ] 2. Enforce at least two admins in a group? (don't enforce, only suggest)
  - [ ] 3. Delete 21 merged branches on GitHub (the list is in "פתוח עכשיו", "הצעה לפיני: ניקוי ענפים")
  - [ ] 4. Delete from PostHog and Sentry URLs with invite codes that were sent before the fix of 4.10.2026 (yes)
  - [ ] 5. The site address before distribution: stay with the Vercel address or move to a domain, because a link that has already been distributed is hard to replace (`docs/GROWTH.md`; decision 28: a domain only if the app succeeds)
  - [ ] 6. **Two old recommendations of the architect** (`docs/ARCHITECTURE.md`, M-3 and M-4): a written contract for the data the apps read from the site (only adding fields, the address stays), and an app version number that doesn't reset. (approve; in practice we already work accordingly, for example with the new cable speed field)
  - [ ] 7. **An open invitation shows the members' names** to whoever holds the code, and they are hidden only when admin approval is required (`docs/ARCHITECTURE.md`, T-4). Always hide? (leave: "אני כבר בקבוצה" needs the list, and the risk is low)
  - [ ] 8. **A dedicated support address** instead of `pinisagent@gmail.com`, which appears in the policy, in the stores and on the support page (`docs/ROADMAP.md`). (leave until there is a domain)
  - [ ] 9. **Measuring open time from the field (`first_frame_ms` in `app_open`, without identifying details)** to improve the cold open based on real data and not on the emulator; a change to the measurement contract (`docs/GROWTH.md`), requires your approval and architect review. (recommendation: yes; the idea is in `docs/ROADMAP.md`)

**L. Retroactive approvals — all approved (Pini, 1.11.2026)** (look that was already implemented without a canvas round, per screenshots; if something doesn't fit, change it):

**M. Testing and release** (head of quality, `docs/QA.md`):
  - [ ] 1. **Which iPhone tests the builds from TestFlight** (model and system version)? Without it, rows 24 to 39 in the list cannot be marked before the App Store submission. (an iPhone that supports iOS 26, Pini's or a friend's who joins as an internal tester)
  - [ ] 2. **Who the testers in Gudauri in December are**, with which phones and when? (at least an Android with a barometer, an Android without a barometer and an iPhone)
  - [ ] 3. **Raw log export for testers, with written consent:** the conditions were set with the legal session and approved by the architect (31.10.2026): only in test builds, up to 15 testers aged 18 and over, the files with you until 31.3.2027 and not in the repo. Waiting for you: the consent wording, the line in the policy, and a check that training is off in your Claude account (`docs/handoff/2026-10-31-tester-log-consent/README.md`, `docs/QA.md`). Without it surf day cannot be calibrated from the field. (approve) **And how:** the export exists only in the test build on Android and in a separate build for testers on iPhone; the pipeline fails if it reaches the store build. iPhone testers join as internal users in your App Store Connect account, because for an external group Apple performs a review. **Do not send to Apple review a build marked for testers** (`docs/APPLE.md`).
  - [ ] 4. **On the trip (11 to 14.1.2027): one skier with two phones**, Android and iPhone, every day. Who? (yes)

**N. Round 33: the support page (approved, Pini, 31.10.2026: "I approve", per the designer's recommendation: all as in the boards)** (`design/round33/`; for the `Support URL` in the App Store, `docs/APPLE.md` 5.6). **Done on the site (31.10.2026):** https://gudauri-ski-trip.vercel.app/support in four languages, and a "תמיכה" row in about. **The row in about was also merged on Android and iPhone (1.11.2026).** **Remaining:** type the address in as the `Support URL` in App Store Connect (Pini).

**O. Round 34: snowball fight, seeing the friends** (`design/round34/README.md`, in the canvas on the page "סבב 34"; the opponents are too small):

**P. Pini's feedback list, 27.11.2026 (from the phone test of the new build; one place, tick when done).** Rule: design items go to the canvas first and wait for Pini's look (CLAUDE.md rule 7); builds only after approval. Designer = `session_017QrNw77yiLRaYaGhuEMWEh`, content = `session_019k19PuDmyzecDEFv7DGcdx`.
  - [x] 3. **(Built 27.11.2026: option A, in `main` since build 1079; not tested on a phone. The thumbs asset waits for the architect.)** "לאן יורדים?" looks like a text field but opens a list. Direction approved: a "מסלולים" button (canvas `DK`, v2 `SV`, v91), more beautiful and experiential requested. Waiting for Pini's final look, then one Sonnet builder; the word "מסלולים" and a screen-reader label with the count go through content. **Version 4 (27.11.2026):** Pini was not happy with the version 3 glyph button; five new options in the canvas (page "כפתור המסלולים, גרסה 4", `RN00`-`RN53`, v101), recommended A (the app's real mountain picture inside the button, in the light of the hour); waiting for his pick. Handoff `design/handoff/2026-11-27-redesign-run-button-v4/`.
  - [~] 4. Shelf chips (לפי רכבל / מהקל לקשה / הארוכים / בשמש עכשיו) show no visible change on tap. Direction approved: each chip orders the cards and paints the mountain, a strip of all runs, a "why" line. **Approved by Pini (27.11.2026, canvas `SV3`-`SV6`); not built by the first builder; shelf v2 builder `session_017QrNw6HwU9N9gpr1yy4khY` (branch `claude/android-shelf-v2`) is building it.**
  - [~] 5. Layers button unclear, and the left button column jumps up about 210 px when it is on. Decision: layers removed, their contents move into the shelf as chips. Same canvas boards; approved, built with item 4 by the same builder.
  - [~] 6. Mountain picture must fill the whole screen under every sheet, never cut (v91 rule written at the top of `design/handoff/2026-11-27-redesign-shelf-layers/README.md`). The builder must check picture mode for a pale band and that the B1 fix holds. Same builder.
  - [x] 9. **(Built 27.11.2026: the whole card in `main`, build 1081, with neighbours peeking, dots under the card and the flight strip RK6; not tested on a phone. The flight strip dropped the speed button, next-meeting line, live numbers and left rail: Pini is asked whether to bring any back.)** **Run card redesign** (designer): drop "(מפה רשמית)" from the subtitle and generally less text; nicer stats (length, drop, slope); clearer wording than "לרדת בסרט"; "סרטונים · 1" must tell the user to drag the sheet up; the page dots swipe between RUNS but sit under the videos (Pini's idea: the card floats and the dots sit under the card). Monday item 3392641809.
  - [x] 12. **(Built 27.11.2026, in `main`, build 1082; not tested on a phone.)** **"הסיפור של היום" page** (designer): today one non-tappable card "המסלול הכי תלול שלך" and an empty screen. Needs a real story experience: tappable insight cards, finale with the share image; check if the single card is a sample-day data issue or UI.
  - [x] 13. **(Built 27.11.2026, in `main`; not tested on a phone.)** **Share image page "תמונה לשיתוף"** (designer, maybe a quick builder fix): tapping the play circle leaves the page; the video must play inside the card, and a tap on something shown must never throw the user out.
  - [x] 15. **(Built 27.11.2026, in `main`, build 1082; not tested on a phone.)** **Share page, "שמירה" gives no feedback (Pini, 27.11.2026):** in `DayShare.kt` save (photo and video) writes to Pictures/Gudi or Movies/Gudi and only logs the result; nothing is shown on success or failure, and the video shows a lone percent ("63%") plus the privacy sentence. Needs a saved state (check, "נשמר בגלריה" or similar via content), a failed state, and progress inside the button/card; part of the share-sheet board with the new designer (`session_017QrNw6xEaoKKLDQ36m6fXG`). Inline video playback is a small behaviour fix, a builder can take it after Pini agrees.
  - [x] 16. **(Built 27.11.2026, in `main`; not tested on a phone.)** Recording screen "כל היום" / "ירידה אחת" toggle (Pini, 27.11.2026):** the two modes are not explained, tapping one changes nothing visible, and the toggle stays live after recording has started. Code: `redesign/LiveDay.kt:260-273` (`onSwitch` also allowed while recording), `redesign/DayStart.kt:84`. Proposed: choose the mode before the start only, then show it as a fixed label while recording (no switch); one plain line says what each does (one descent stops by itself at the bottom). Designer (`session_017QrNw6xEaoKKLDQ36m6fXG`) to board it with item 12/15; wording through content.
  - [~] 17. **(Built 27.11.2026 and in `main` (3 px/pt set, architect approved), but Pini still sees no sharp Kobi picture, cause unknown. Build 1079 and later show a small diagnostic line: waiting for his screenshot. Open for the architect and Pini: q80 for every file needs a per-file ceiling of about 215 KB.)** **Kobi side in picture mode (Pini, 27.11.2026):** today the Kobi run is seen foreshortened at the top of the front picture, and tapping it only selects it; nothing turns. Pini wants a way to go to the other side of the ridge (picture mode too) and the Kobi picture in **high quality**. Seven side images exist (`site/img/peak/kobi/ba64acea9a/`, 327 KB total, architect ceiling 350 KB), not wired into the app (log 25.11: live fallback). Needs: designer board for the switch and sharper images; architect review if the size ceiling rises or the download contract changes; then one Sonnet builder. **Return to the front side (Pini): must exist; options he named: a back button at the top near the picture/live switch, or a tap on the compass; designer picks what feels best.**
  - [x] 19. **(Built 27.11.2026, in `main`, build 1079; not tested on a phone.)** **Hint-to-hint transition is an instant jump (Pini, 27.11.2026):** both the ring around the target and the bubble jump. Wants a gentle, experiential motion (ring glides to the next target, bubble moves/cross-fades). Today `HintSequence.kt:162-163` only scales the ring 1.18 to 1 in 200 ms per step. Motion spec (durations, easing, reduced-motion fallback) from the designer first; then a builder. No new strings.
  - [x] 20. **(Built 27.11.2026, in `main`; not tested on a phone.)** **Ski pass screen: the sheet covers the bottom of the pass (Pini, 27.11.2026, screenshot):** the purple foot of the pass and its lower part are hidden under the sheet (`redesign/LiveSkipass.kt`, sheet at line 167, pass drawn from the top). Probably the sheet is too tall for this phone (tall tabs, 6 rows, footnote). Fix: the pass must be fully visible above the sheet (smaller pass or a shorter sheet/scroll); a layout bug, no new look. One Sonnet builder, check with large text.
  - [x] 21. **(Built 27.11.2026, in `main`; not tested on a phone. The ticket-office point is OSM, medium certainty: confirm on site in January.)** Ski pass "איפה הקופה?" (Pini, 27.11.2026):** today it opens the painted village map with a bottom sheet (the map cannot be moved, nobody can tell how to get there). Replace by a link that opens Google Maps (not Waze) at the ticket office; plus, if relevant, a small round button for the SKIPASS app by Lemondo (`research/practical-info.md` row 15: verified, load a card or order a new one with delivery within an hour; Google Play `com.lemondo.skipass`, App Store `id6698866856`; string `app.skipass_app`, old code `skipass/SkipassScreen.kt:127`, not in the redesign `LiveSkipass.kt`). **Coordinate found (ski expert, OSM, medium certainty, `research/places.md` `26ea11d`):** "Ski Pass Office" 42.470828, 44.487555 (recommended, matches the name); alternative "Gondola Tickets" 42.471311, 44.492023 (at the Goodaura lift start, ~380 m apart); confirm on site in January. Designer boards two round buttons (small, no explanation text), Pini approves; then one builder. Wording through content.
  - [~] 22. **Games page: the "הטבלה של גודאורי 2027" button opened the old screen (Pini, 27.11.2026):** button hidden for now (`Redesign.kt:241` passes `onTable = null`); a new-design records table is a later design task (designer), not now. Not tested on a phone; merge after green `android`.
  - [x] 23. **(Built 27.11.2026, in `main`, build 1079; not tested on a phone.)** **(27.11.2026 Pini: leave the 35 saved junk days as they are for now, decide later)** **"הימים שלי" fills with fake days (Pini, 27.11.2026, screenshot: 35 days, 1.1 km):** every recording is saved, so tapping record and stopping makes a day. Wanted: a recording with no real descent (not even part of one inside the Gudauri mountain) is **not saved**; after stopping, a short informative message ("לא נרשמה ירידה, לא נרשמה גלישה בהר" or similar via content) and nothing in the list. Asking "save?" was rejected (a real day could be lost by a slip). Also: the name "הימים שלי" is unclear; rename to something clearer (e.g. "ימי הגלישה שלי", content proposes). Open: what to do with the junk days already saved on test phones (a one-time cleanup of days with no descent?). Touches `EM` end screen (approved, `design/handoff/2026-11-25-redesign-empty-end/`, which today offers "חזרה להר" and "מחיקה") and the days tile/page; designer board for the no-descent end state first (rule 7), wording through content, then one builder.
  - [x] 24. **(Built 27.11.2026 with P.23, in `main`, build 1079; not tested on a phone.)** "הימים שלי" list page and "השיאים שלך" (records) page are plain (Pini, 27.11.2026, screenshot):** the list is white rows of date and one line ("היום נקטע", "בהקלטה הזו אין ירידות", "1 ירידות · 0 מ' למטה · 0.5 ק"מ"), a small route thumbnail only on some. Wanted: a much more experiential page for both, the days list and the records. Designer board (rule 7), together with P.23 (same page, no-descent days disappear, new name). Seen in the shot: 0 m drop on a 0.5 km day and a grammar slip "1 ירידות" (content: plurals). Wording through content.
  - [x] 25. **(Built 27.11.2026, in `main`; not tested on a phone. Open from the builder: the wording of "opens tomorrow" and "no reception".)** Top status pill on the mountain is bland (Pini, 27.11.2026, screenshot: "23 ימים לפתיחה · -8°"):** dry text. Wanted: a more beautiful, experiential, informative top bar; in season it should show how many lifts are open out of how many, with a lift icon; also the pre-season state (and in-season; post-season not needed). Designer board (rule 7), wording via content. Data caveat: real lift status (S-20) is not live until December (MTA parser off), so the in-season count needs a source decision with the server session/architect; the pre-season board can be drawn now.
  - [ ] 14. Before store upload (standing): QA re-run on `30bd7454` (DevOps), full `android-qa` and `ios.yml` on the same SHA, design reviewer go/no-go, Pini tests on a phone. The branch (`865ee175`, `a2d1d49f`, `30bd7454`) must be merged to `main` after `android` is green.

**Open questions (answered during the season, not now):**
- At what ridge wind does MTA close Kudebi and Sadzele? Learn from comparing the forecast to the actual status at the start of the season.
- What does the MTA page look like when the season is open, and is there JSON behind it.

**New fact from the document:** the group will stay in New Gudauri, right next to the lifts.

**Sync:** the features document in Claude Docs contains a line that is no longer accurate ("Stage 1 in Claude Code is finished. Stages 2 and 3 are in planning"). In practice stages 1 to 4 were done, and stage 3 was reduced. Update it there, or decide that the document is a historical snapshot.

- **Videos:** YouTube blocked the viewing pages, and therefore the proof is the title or the description snippet in the search results. Watch a few videos before trusting everything, and mark in `tools/videos-curated.json` what to remove. After editing: `python3 tools/build-videos.py`.
- **Runs without a video:** Sportuli 1 and 2, Sadzele 1 and 3, Firni 2, Khada, Zuma, Baby, Bombora. Some of them are found only in videos without a number (see candidates in `research/videos.md`). They can be added manually if Pini knows which run is shown.

- The share image `site/og.jpg` is created with the command `python3 design/round27/og.py --site` (round 27, decision 73: golden hour, the GUDAURI sign with a thin layer of snow and snow at the foot of the pole, without dates). When replacing it, bump the `?v=` in `og:image` in `site/index.html`, so that WhatsApp and Google read it again.
- When reaching the personalization stage: add a `Store` layer that replaces reading the files (see `docs/HANDOFF.md`, stage 3, and `docs/ROADMAP.md`).
- `vercel.json` is in `site/` and not in the root, because the Root Directory in Vercel is `site`.

## Log
- 27.11.2026: **Android (Sonnet): shelf v2 (P.4, P.5, P.6) built on `claude/android-shelf-v2`, not merged.** Six chips that order and paint, strip panel with ticks and scrub, cards with the big number, layers button removed, picture mode never shows a band under the shelf. CI only; details in `docs/handoff/2026-11-27-shelf-v2.md`.
- 27.11.2026: **Manager: shelf chips and layers (P.4, P.5, P.6, J.9) are approved by Pini and were never built; I had wrongly listed them as waiting for his look.** Builder `session_017QrNw6HwU9N9gpr1yy4khY` (`claude/android-shelf-v2`) opened, content strings already in (`9b17e0c8`). `android-qa` run 514 on build 1082 green.
- 27.11.2026: **Content: shelf version 2 strings entered (builder request).** 17 `app.shelf_*` keys (chips "לפי קושי", "פתוחים", "שלג חדש"; captions under the big number; foot line; empty-snow line; strip tick screen reader), builder's proposed names kept; `shelf_word_open` added. `check.py` green.
- 27.11.2026: **Architect: SKIPASS `<queries>` approved (no data leaves the phone, result never logged); Kobi per-file cap: recommended 220 KB / 1.2 MB total for q80 on every file, Pini decides; run-button thumbs (7, ~42 KB packaged) approved.** No code changed.
- 27.11.2026: **Content: `app.run_profile_a11y` and `app.runs_button` added (manager request).** Screen-reader line for the run profile ({top}, {bottom}, {deg}) and the runs-button word as an app key, same wording as `resort.pass_runs`. `check.py` green.
- 27.11.2026: **Manager: the full builder reports of 27.11 are kept in `docs/handoff/2026-11-27-builder-reports.md`.** Per item: built, not built or different, phone checks, and seven open decisions (flight strip controls, Kobi q80, top pill states, story back-tap, manifest `<queries>`, content keys, the not-built approved parts).
- 27.11.2026: **Manager: Pini asked why new builder sessions were opened for tasks that already had sessions stopped by the usage limit.** Two of the old sessions (run card, story+share) had resumed by themselves; I opened a new run-card builder while the old one was alive (a few minutes of duplicate work) and a duplicate story+share builder (archived after 15 s). Rule from now: check `get_session` of the old session before `create_session`. The account weekly limit shows `allowed_warning`, reset Monday 30.11 at 10:00 Israel time.
- 27.11.2026: **Manager: build 0.1.1082 (`890ac6cc`, `android` run 1082 green) is the current preview; `android-qa` core was green on 1079 (run 512) and on the run card merge (run 513).** It has the whole run card, the story page, the share sheet states, runs button A, my days and records, timeline rest, hints 9 s with the glide. Pini was asked about the film controls and for a Kobi screenshot; phone not tested.
- 27.11.2026: **Manager: Monday updated and six finished builder sessions archived.** Existing items moved to "waiting for Pini" with what to check; new items for my days, top pill and the ticket-office buttons (they had none). P and J marks in "Open now" now show built and in `main`.
- 27.11.2026: **Manager: build 0.1.1079 (`a7a70053`) is out, and the run card rest is merged to `main` (`3d5f22db`, `android` run 1081).** 1079 has runs button A, my days and records, day timeline rest, hints 9 s with the glide, the run card first part and the Kobi diagnostic line. The new flight strip (RK6) has no speed button, next-meeting line, live numbers or left rail; Pini is asked whether to bring any back.
- 27.11.2026: **Manager: story+share (P.12, P.15) merged to `main` after its branch run 45999030375 was green.** Story cards, player and finale (DS1-DS5), share sheet saved and failed states (DV4-DV8). Not tested on a phone. Next preview is the build after 1081.
- 27.11.2026: **Manager: weekly archive done (token saving).** `docs/STATUS.md` went from 147 KB to about 66 KB: log lines older than the newest 25, closed `[x]` items of "Open now" and the rows of free areas moved to `docs/STATUS-ARCHIVE.md` (section "Weekly archive of 27.11.2026"). Nothing was deleted.
- 27.11.2026: **Manager: Sentry check for the Kobi picture (Pini asked) and two gaps from the ledger.** Sentry `gudi-android`, last 24 h: no Kobi event (only a CI test event), so it is not an exception; the `kobi:` line in the preview shows the reason once Pini taps Kobi. Google Play rollout 100% (not 20%) is now in `docs/PLAY.md`. P.22 verified in code: the games table button is hidden (`Redesign.kt:241` passes `null, null`).
- 27.11.2026: **Manager: integration branch `claude/android-integration-1127` (android run 1076) and new builders.** It is main plus hints-kobi-fix (the Kobi diagnostic line), runs button A, day timeline rest, my days and records (the missing `drawText` import fixed), hint motion (9 s kept) and the run card first part (a wrong test expectation fixed). New builder for the run card rest `session_017QrNw7gLPHLsFLw4ucLX1U`; the story+share builder `session_017QrNw77f7FFWeQzGZAtg7a` came back by itself and the duplicate I opened was archived within a minute.
- 27.11.2026: **Manager: stuck-work audit of the last five hours (Pini).** The account usage limit stopped three builders at 09:09-09:15 UTC (story+share, runs button, run card) and I noticed about three hours late; my-days ended its turn while CI ran and a compile error sat from 10:40; runs button, timeline rest and hint motion were green but never merged; hints-kobi-fix waits for a build with its diagnostic line and Pini's screenshot.
- 27.11.2026: **Manager: first-run hints are 9 s (Pini) in `main` and in the preview 0.1.1075 (`b8e8a821`).** Pini's phone had 1069 (13 s on the wall clock).
- 27.11.2026: **Android (Sonnet): run card rest (P.9) on `claude/android-run-card-rest`, not merged.** Floating card with the neighbours peeking and the dots under it (sideways drag settles 300 ms, mirrors in RTL), flight strip RK6 (76 high, progress bar with scrub, round stop; end of run or stop restores the card; reduced motion keeps the card). Not compiled locally, not tested on a phone.
- 27.11.2026: **Manager: Pini on build 1057 again: hints ~6 s, Kobi side shows only the run.** Checked: code says 13 s, Kobi files and sha are right and live on Vercel; the preview APK is now 0.1.1064 (`61dbe985`). Likely causes: Compose tween scaled by the phone's animation setting; Kobi download or layer load failing (no log). Builder `session_017QrNw68N1FdkhUeKPUYGwn` (`claude/android-hints-kobi-fix`) fixes both and audits `main` against the handoffs.
- 27.11.2026: **Manager: Pini on build 1057 and the timeline omissions.** 1057 = `main` `f5ee429f` (has hints 13 s in code, Kobi side, top pill, my-days rule, small items); NOT in it: runs button A, shelf chips, hint motion, run card, story+share, days list/records. Day timeline left out DT2, chart grid, break band, legend, two chapters (my miss); builder `session_017QrNw8D7Yx5B8wtpj7a6qU` (`claude/android-day-timeline-rest`); omissions audit sent to the other builders.
- 27.11.2026: **Manager: Pini approved the run card (RK), the day story (DS) and the share sheet (DV); rejected the faint-runs change (DM).** Faint runs stay as today (no white, no stronger). Day timeline (P.11) built and merged (`655a2daa`; not built: scrolled strip DT2, chart grid/break band/legend, two-chapter split). Builders for RK and DS+DV follow after content enters their strings.
- 27.11.2026: **Manager: audit of Pini's 26-27.11 feedback.** In `main`: far ring, tile and icon, hints 13 s, ski pass fit, games table hidden, share video, top pill, no-descent rule, ticket-office buttons, record mode. Building: hint motion, Kobi side, runs button A, days list and records. Waiting for Pini (designed): shelf and layers, run card, faint runs, story, share sheet, B9. Not built, no board yet: none known. Section P marks for 9, 10, 12, 13, 15, 16, 20 are stale (duplicates); the checklist artifact is current. Unmerged old branches to check: `android-redesign-pulse` (speed ring GS6), `android-redesign-shots` (store screenshots of the new design).
- 27.11.2026: **Manager: Pini chose option A (the window) for the "מסלולים" button (P.3, canvas RN, v101).** Builder `session_017QrNw9YBYpchuX77eTWJgd` (branch `claude/android-runs-button-a`); architect asked about the seven packaged thumbs (~42 KB). Shelf chips and layers (P.4, P.5) ride with it.
- 27.11.2026: **Manager: architect approved the Kobi set at 3 px/pt (P.17); ceiling 350 KB to 1.1 MB, max 180 KB per file.** Still downloaded, never packaged, one `rev`, `sha256` check, light key first, at most two decoded pictures. Builder opened: `session_017QrNw8bq4Be9d2C8ysZNyc` (Sonnet), branch `claude/android-kobi-side`.
- 27.11.2026: **Manager: Pini approved the Kobi side in picture mode (P.17, canvas KB00-KB4).** Sent to the architect for the image size ruling (350 KB ceiling vs 626-1,069 KB); builder after his answer.
- 27.11.2026: **Manager: Pini approved the hint motion (P.19, canvas HM00-HM4).** Builder `session_017QrNw8gQPUzupPbTf6iEKX` (branch `claude/android-hint-motion`), no new strings. Top pill (P.25) merged to `main` (run 1041 green); run button v4 in canvas v101 waits for Pini.
- 27.11.2026: **Android (Sonnet): run card (P.9, RK00-RK6) built in part on `claude/android-run-card`, not merged.** Tag without "(מפה רשמית)", route line, slope-coloured profile with pin, three numbers, film button, foot row with frames and conditions (`RunCardRules`, tests). Not built: neighbours peeking, flight strip. Not compiled locally, not tested on a phone.
- 27.11.2026: **Android (Sonnet): hints now 13 s by the wall clock (the animator scale halved the tween), Kobi side fetch hardened (retries, first-picture wait, Qa.log per stage), main audit in `docs/handoff/2026-11-27-main-audit.md`.** Branch `claude/android-hints-kobi-fix`; root cause of the missing Kobi photo not proven (no device, site unreachable here); CI only, not on a phone.
- 27.11.2026: **Android (Sonnet): P.24 built on `claude/android-my-days-2` (not merged).** Days list MY1-MY3 (`DayList.kt`: trip card, day cards on the painted mountain, speed strip, tags, sample day, empty state), records RS1-RS3 (`RecordsScreen.kt`: headline and four tiles with drawings), NS end-sheet icon matched to the board. Rules in `MyDaysRules.kt` with tests. Not compiled locally (no Gradle network), not tested on a phone; the trip card title is only the month (no content key for "גודאורי · ").
- 27.11.2026: **Content: records and trip-card strings for my days entered (Pini approved).** `app.md_trip_title`, `md_rd_drop`, `md_rd_oneday`, `md_rt_speed`, `md_rt_longest`, `md_rt_bigday`, `md_rt_most`, `md_unit_kmh` in four languages; steepest reuses `md_steepest`. `check.py` green.
- 27.11.2026: **Android (Sonnet): day timeline rest (P.11) built on `claude/android-day-timeline-rest`, not merged.** Scrolled ridge strip with window and tap-to-scroll (DT2), altitude grid 2,200/2,600/3,000, long-break band with bench and minutes, legend break swatch, two chapters "בוקר"/"צהריים". Not compiled locally; CI only; not tested on a phone.
- 27.11.2026: **Content: run-card strings entered (Pini approved).** `app.run_ride` (the film button, also read by iOS) now "טיסה מעל המסלול"; new `app.run_film_stop`, `app.run_vids_n` (plural), `app.run_cond_now`, `app.run_vids_from`. Day-story and share-sheet strings were already in (`ds_*`, `dt_*`). `check.py` green.
- 27.11.2026: **Android (Sonnet): day story page and player (P.12) and share sheet states (P.15) built on `claude/android-story-share`, not merged.** Every story item a tappable card with backfill from "עוד מהיום", player (6 s, tap halves, hold, finale), tiles and buttons with progress, saved and failed states (`StoryRules.kt`, unit tests). Not compiled locally, not tested on a phone; no camera glide or in-card pause controls yet.
- 27.11.2026: **Android (Sonnet): day timeline "הרכס של היום" (P.11) built on `claude/android-day-timeline`.** Four numbers, "לאן הלך היום" bar, rows on a spine with icons (lift, descent with speed strip, long break as cup/fork/bench by `places.json`, walk, queue), names wrap and are never cut; rules and tests in `DayTimeline.kt`. Not built: the scrolled ridge strip, grid and break band on the chart. Not compiled locally, not tested on a phone.
- 27.11.2026: **Android (Sonnet): the "מסלולים" button, option A (P.3), built on `claude/android-runs-button-a`, not merged.** Window picture in the hour's light (crop of the packaged hero picture, no new asset), count, chevron, press and narrow states; the button's surface flies into the shelf's title pill (420/280 ms), chips and cards rise in order; bar 64 high. Not compiled locally (no Gradle network), CI only; not tested on a phone.
- 27.11.2026: **Architect: Kobi side picture set at 3 px/pt approved.** Download ceiling 1.1 MB (was 350), each file up to 180 KB, with a build check; current light key first, the rest in the background; one `rev`, sha256-checked, at most two decoded pictures. No code changed.
- 27.11.2026: **Android (Sonnet): P.17 Kobi side in picture mode built on `claude/android-kobi-side`, not merged, not tested on a phone.** `PictureStage` has two faces (front, Kobi) with the 450 ms turn; tag "צד Kobi" on the ridge or a tap on the Kobi line turns, the compass above the switch returns. The side's set is 3 px/pt (1001 KB, each up to 177 KB; `peak.json` rev `2f9091ad79`), downloaded to no-backup storage with sha256 check, resumable, foreground only; without it Kobi goes live for a moment as before. Gradle not run here: CI only.
- 27.11.2026: **Android (Sonnet): P.19 hint motion built on `claude/android-hint-motion`, not merged.** `HintSequence.kt`: ring glides 360-560 ms, old bubble out 120 ms, new bubble in at 55% with a 6 dp slide, one breath halo, taps locked mid-move, 13 s timer starts after the bubble is in, reduced motion = cross-fade. Not compiled locally, CI only; not tested on a phone; dim fade-in and early skip of a missing target not done.
- 27.11.2026: **Product designer (Fable): the "מסלולים" button, version 4, in the canvas (version 101, page `runbtn4`, `RN00`, `RN01`, `RN11`-`RN53`), waiting for Pini's pick.** Five options, each day, night and a real-speed opening loop: A the real mountain picture in the button (recommended), B real contour lines, C type only, D the bar as the shelf's handle, E one dark primary button.
  The shelf of version 2 and the strings are unchanged. Handoff `design/handoff/2026-11-27-redesign-run-button-v4/`; design tool only, not on a phone; Monday board not updated by me.
- 27.11.2026: **Designer (Opus): P.19 hint motion and P.17 Kobi side in the canvas (v100, boards HM00-HM4, KB00-KB4), waiting for Pini.** Ring glides 360-560 ms, bubble cross-fades; Kobi tag on the ridge turns the view, compass returns. Handoffs `design/handoff/2026-11-27-redesign-hint-motion/`, `...-kobi-side/`. Architect needed: Kobi set at 3 px/pt is 1,069 KB vs the 350 KB ceiling (2 px: 626 KB).
- 27.11.2026: **Manager: Pini approved the ski pass ticket-office buttons (P.21) and the record-mode choice (P.16).** Builder `session_017QrNw8xUH7NpTCSf3LK6CS` (branch `claude/android-small-items`); content enters three strings. Ticket-office coordinate is medium certainty, confirm in January.
  Kobi at 3 px per point like the front measured 1.07 MB: the 350 KB ceiling goes to the architect; a painter round for the shaded side is flagged (ROADMAP). P.16/21/23/24/25 checked, nothing missing. No strings.
  P.16, P.17, P.19 and P.21 not designed here (session size, rule 12): brief in `design/handoff/2026-11-27-redesign-day-pages/BRIEF-next.md`.
  Found: the one-card story is a build issue (engine gives 4 story + 7 more for the sample day); the share circle is a bug (tap falls to the dismiss layer). Handoff `design/handoff/2026-11-27-redesign-day-pages/`; strings to content after approval.
