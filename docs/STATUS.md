# Work status

**This document is mandatory for everyone working in the repo: read it before starting, and update it before finishing.**
The full rules are in `CLAUDE.md`, in the section "Mandatory for everyone working in the repo".

Last updated: 2.11.2026 (round 21 on the site, route name by name, Gudauri data fixes; earlier 30.10.2026: decision 76: speed measurement and ski tracking; decision 75: the short link to the app, `/app`; decision 72: round 26, the Google Play sign; decision 71: iOS starts now, in the cloud; Apple: the guide to the account and the App Store, `docs/APPLE.md`; decision 69: round 24, like the official map, approved; decision 68: prototype of several ski resorts, Sölden on the site; decision 67: the map is not on the home screen; decision 66: infrastructure is a DevOps person; decision 65: round 20 approved; decision 64: no source/certainty in the panel; decision 63: no panorama; decision 62: round 18 approved; decision 61: vision for every ski resort; decision 60: store page and the "קבוצה" sign unchanged)

## Who is working on what now

**Mandatory (decision 45):** before starting work on an area, check here. Area taken: do not enter, send a message to the session that holds it. Area free: register the session and push to the main branch before starting. When done, return it to "free". The full rules are in `CLAUDE.md`, rule 11.

| Area | Folders | Who is working now | Session id | On what |
|---|---|---|---|---|
| Site | `site/`, `tests/`, the site's `tools/build-*` free (last session: `session_017QrNw9uUUH47AzK3jXibqU`) ; the feasibility session (buildings) only in `buildings3d.js` and `buildings.json` | `session_017QrNwACAZiKEHHSCgbVj19` (buildings) | (2.11.2026) Round 21 on the site merged, including the open end (`open`). The buildings in 3D behind `?b3d=1` (decision 77), in `site/js/buildings3d.js` and `site/data/buildings.json` only: the western cluster, roads and signs |
| Android | `app/android/` | free (last session: `session_01Gpr4L7H5gxaycQcxMRD5cE`; 22.11.2026 `is_internal` and `install_channel` in measurement, see the log; the POC code in `src/preview` is with the POC implementer) | `session_01Gpr4L7H5gxaycQcxMRD5cE` | **(13.11.2026)** diagnostic log in the test build, the counter counts from the touch, motion while waiting for the first reading and cropping of the day map merged to `main` (`23357b4f`); round 38 (minimum map size, "מחפשים מיקום") deferred to the design-language refactor. **(2.11.2026, after round 29)** story and insights, descent card, records, sample day, share image and route names on the map merged (after a green run); **remaining:** replay in 3D, the open end `open`, and testing on a phone. Round 31 and round 34 merged. (Decision 76, 30.10.2026) ski day: the recorder, the analyzer, the moments **and the round 29 screens** (entry "מקליטים", live screen, end of day, my days) are built, behind `BuildConfig.DAYREC_UI` (test build true, store build false), and therefore merged to `main` with nothing visible in the store. **Do not upload to Google Play a build with the feature until Pini submits the foreground service declaration and the privacy line is published (the manager session coordinates).** **In parallel (13.7):** accessibility, on branch `claude/android-a11y`, merges to `main` after a green run . **(1.11.2026)** the "תמיכה" row in About merged, accessibility 13.7 finished (except large text), and now performance (cold start and games) and gaps versus the site **(2.11.2026)** round 31 (places at stations), round 34 (snowball fight, corrected D) and showing the route name by `name` (PARITY section 17, `9491403a`) merged to `main` after a green run; the ski expert's data fixes merged, and the emulator script updated (`b7c771e6`). **Next:** route names of round 21 (PARITY section 16), after the site merges. |
| Android: redesign, second implementer (Sonnet) | `app/android/` only the four screens: pulsing search, opening on the picture, record button RX3, end screen without descents; branch `claude/android-redesign-s4` | the second implementer | `session_017QrNw6gpsVzdKmiHmWpPrc` | (25.11.2026) does not touch the seam (`ringband`, Ring shaders) or the `core` part in `qa/run.sh`: those are with the implementer on Opus |
| Android: redesign (POC) | the redesign code, behind `BuildConfig.REDESIGN` | free (last session: the Opus implementer, `session_017QrNw6GUA8H2VtubNvFG2f`) | | (25.11.2026) The seam and the QA part `core` are merged to `main`. **Open, not started by anyone yet:** `android-preview`, store screenshots, deleting the old design (under the architect's conditions), and testing on a phone (Pini). The emulator sometimes vanishes while the day is drawn; that belongs to DevOps (pipeline). Pini moves the next Android work to Sonnet |
| Android: opening on the picture (Stage) | `app/android/` | free (last session: `session_017QrNw8CkjGjjF8LtqeGehJ`, merged 25.11.2026) | | |
| Server | `server/` | the server session (Sonnet) | `session_014ke3f3CtfjLp4WS92ScS6L` | (2.11.2026) the MTA page parser and the matching table were written (`mta_status.ts`, off, not connected to `fetch_status`) in a separate session, branch `claude/mta-status-parser`, id `session_017QrNw7v1AacjP6x3vM7dbp`, which returns the row to "server" on merge. (1.11.2026) preparation for lift status for December (S-20); after it preparation for an encrypted daily backup (R-1), **off and without deployment**. `/api/weather` is running; `/api/status` waits for December. Actions on the live server only with Pini's approval |
| iOS | `app/ios/` | free (last session: `session_012Y5jLfzBy1HzYPy4JE9v4q`, 22.11.2026: `is_internal` and `install_channel` merged) (earlier: rounds 39 and 40 merged) | `session_012Y5jLfzBy1HzYPy4JE9v4q` | Pini: "take it all the way, a finished product" (21.10.2026). On the main branch all the parts: home page, the map (top view and 3D), lift status, weather, "where am I", the meeting point, the games, About, the trip form, the group and account, and measurement. **(13.11.2026) the ski day is complete in `main`, behind `#if DAYREC` and in the `variant=dayrec` build only** (the recorder, the analyzer, the moments, round 29 screens, sample day, the story and insights, descent card, records, share image, replay and a button on the map); the regular build (1.0.x) does not change, and `claude/ios-dayrec` is no longer needed. **Remaining:** Live Activity (id, profile and secret are with Pini), exporting the raw log to testers (not required for submission), Sentry for iOS, and restoring text scaling in `SkiType.text()`. **Submission of 1.1:** the manager session runs `ios-release` with `variant=dayrec` to TestFlight (Pini approved, 13.11.2026); nothing is sent to Apple without Pini's gate (the store declarations and the privacy line) |
| Canvas | `design/`, the canvas | free | | |
| Canvas: round 18 | `design/round18/` and the shared canvas | free (round 18 is ready in the canvas, waiting for Pini) | | |
| Canvas: round 20 | `design/round20/` and the shared canvas | free (round 20 is ready in the canvas, waiting for Pini) | | |
| Canvas: round 21 | `design/round21/` and the shared canvas | free (round 21 approved in full, handed to the site session for implementation) | | |
| Canvas: round 22 | `design/round22/` and the shared canvas | free (round 22 closed: the home page stays as it is today, decision 67) | | |
| Canvas: round 23 | `design/round23/` and the shared canvas | free (round 23 in the canvas, handed to the manager session for implementation, decision 68) | | |
| Canvas: round 24 | `design/round24/` and the shared canvas | free (round 24 and the collapsing legend approved, implemented and closed in the canvas) | | |
| Canvas: round 25 | `design/round25/` and the shared canvas | free (round 25 in the canvas, waiting for Pini's choice) | | |
| Canvas: round 26 | `design/round26/` and the shared canvas | free (round 26 approved and implemented, decision 72) | | |
| Canvas: round 27 | `design/round27/` and the shared canvas | free (round 27 approved in full and implemented, decision 73) | | |
| Canvas: round 28 | `design/round28/` and the shared canvas | free (round 28 approved and implemented, decision 75) | | |
| Canvas: round 29 | `design/round29/` and the shared canvas | free (round 29 in the canvas, waiting for Pini) | | |
| Canvas: round 30 | `design/round30/` and the shared canvas | free (the feasibility session's mockups in the canvas, waiting for Pini) | | |
| Canvas: round 31 | `design/round31/` (the code in `design/round29/`) and the shared canvas | free (round 31 in the canvas, waiting for Pini) | | |
| Canvas: round 32 | `design/characters/` and the shared canvas | free (character sketches in the canvas, waiting for Pini) | | |
| Canvas: round 33 | `design/round33/` (the boards are built in `design/round29/build.py`) and the shared canvas | free (round 33 approved, 31.10.2026; implementation: `/support` on the site and a "תמיכה" row on the three platforms) | | |
| Canvas: round 39 | `design/round39/` and the shared canvas | free (round 39 approved, 18.11.2026, handed to Android and iOS after the architect's review) | | |
| Canvas: round 40 | `design/round40/` and the shared canvas | free (round 40 approved, 18.11.2026, handed to Android and iOS after the architect's review) | | |
| Canvas: redesign | `design/redesign/` and a separate canvas (https://claude.ai/artifact/8NrAKy97KWRj3JWcxx41yQ) | the designer is working (`session_017QrNwAPwyrY12TRdaupPSb`): **the completion round approved in full (23.11.2026, decision 88);** the "המפה" page in the canvas (version 31): what was drawn and what remains, the navigation map, and eight open decisions with a recommendation; **parts 4 to 8 (on the mountain, what was described in words, the edges, languages and platforms, for everyone and on every screen) are in the canvas and handed over, and all approved (Pini, 23.11.2026); marked so in the canvas (version 43);** **only the games remain for the end (R11, R12, U12, U13 and the games from the inside), when Pini asks;** now (24.11.2026): only design of new and complex things; the design reviews of the implementer's builds moved to the design reviewer (`design/redesign/REVIEW.md`) | | |
| Canvas: redesign B9 (text in a frame) | `design/redesign/tools/b9_boards.py`, boards `B9*` | free (last session: `session_017QrNw6jtAxZgN7a1ndpPAv`; B9 in the canvas, version 74, waiting for Pini) | | |
| Design reviewer (Opus 5.5) | design review checks of the implementer's builds, read only | the design reviewer (new decision, 24.11.2026) | `session_017QrNw7nFaumJ4hZW4rcMnk` | one review per part, per the boards and handoffs; parts 1 and 2 approved, parts 3 and 4 on the way. The designer (Fable 5.2) only designs new and complex things |
| Canvas: B10 (buttons without words) | `design/redesign/tools/b10_boards.py`, `design/handoff/2026-11-26-redesign-b10/` | free (last session: `session_017QrNw8Sx16Q5GiNokfYHLr`; boards BT00 to BT4 in canvas version 75, waiting for Pini) | | |
| Canvas: round 38 | `design/round38/` and the shared canvas | free (round 38 deferred to the design-language refactor, 13.11.2026) | | |
| Canvas: round 37 | `design/round37/` and the shared canvas | free (round 37 approved, handed to the site session for implementation) | | |
| Canvas: round 34 | `design/round34/` (the boards are built in `design/round29/build.py`) and the shared canvas | free (round 34 in the canvas, waiting for Pini) | | |
| Companion characters | `design/characters/` (sketches; to the canvas via the product designer) | free (last session: `session_017QrNw86vUyxUKoiBbbEDcS`) | | Stage B: direction sketches in the canvas, page "round 32: companion character" (version 91), waiting for Pini |
| Canvas: new design language | `design/language/` and a separate canvas (https://claude.ai/artifact/9yxrGgytb2Gmi4i1UqEhST) | free (the five languages are ready, waiting for Pini's choice) | | |
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
- [x] 1. **Approved (Pini, 31.10.2026, decision 77)** **The privacy line for the surf day**, ready in four languages; published with the version in which the feature is turned on, and if the GPX is removed, without the sentence about export. The final text, without the GPX sentences, is ready on branch `claude/legal-dayrec-final` (not merged). **Remaining for Pini, two small changes in the text, together when round 29 closes:** (1) that it also covers a recording started in the middle of the day (c10); (2) that it says what is not sent instead of "only", because the measurement of "הסיפור של היום" (`story_view`, `story_share`, without location, run and speed) contradicts the "only". Both in the table at the end of the handoff document on the branch (`docs/handoff/2026-10-30-dayrec-privacy/README.md`). Without it the surf day is not merged in Android and iPhone, both of which are already built and tested on branches.
- [x] 2. **Round 29 approved (Pini, 1.11.2026, "round 29 approved"), everything per the recommendations, and question 9 a separate map.** Handed off for implementation to the Android and iPhone sessions (all behind the `dayrec` capability, turned off). Pini does not like the squared look of Android and prefers the rounded corners of the iPhone: not blocking, and the product designer is preparing an alignment proposal (iteration later).
- [ ] 3. **The tone of the moments** (round 29, question 11): the texts are ready in three tones and four languages (`docs/handoff/2026-10-30-dayrec-moments-copy/`), and displayed in the canvas in row t.
- [x] 4. **The 3D buildings POC was approved (31.10.2026), in a realistic direction** (d below). In progress with the feasibility session, after a short repeat review with the architect.

**B. Actions only Pini can do**
- [x] 1. **(Done, 1.11.2026, per the Apple session: sign-in with Apple works on the personal iPhone, and the "App Review" group was created)** Sign-in with Apple on iPhone: try on the latest build in TestFlight (11 and up; Apple in supabase was turned on, 31.10.2026). Per the iPhone session, processing in App Store Connect is seen only by Pini. And at the same time: how the 3D feels on iPhone.
- [x] 2. **(Done, 1.11.2026)** Google Play: the store page in Hebrew, Russian, Georgian and English was updated, and the site address was added in the contact details; five changes were sent for review (`docs/PLAY.md`, `i18n/store-listing.md`).
- [x] 3. Search Console: **the site is indexed** (Pini found it on Google, on the 8th results page, 31.10.2026).
- [~] 4. Apple: the three signing files are backed up and the Mac was cleaned (31.10.2026). **Remaining:** remove the Apple ID from Xcode when there is battery (`docs/APPLE.md`, 4.2 e). **Pini (2.11.2026): leaves it for now.**
- [ ] 5. **After there are screens for the surf day:** the foreground service declaration in Google Play, with a video (`docs/PLAY.md`, section 5; the text and script are ready).
- [ ] 6. **After there are screens for the surf day:** Apple, for the lock screen (Live Activity): a second identifier `io.github.pini236.skiapp.live`, a second profile and a seventh secret (`docs/APPLE.md`, 5.9).
- [ ] 7. **To make sure it was done (from 21.10.2026, for iPhone):** a `gudi-ios` project in Sentry with IP address storage prevention; an iOS client in the Google console, and its identifier in supabase (without it the Google button is hidden on iPhone).
- [x] 8. **(Approved, 31.10.2026, and published in four languages, 1.11.2026: `/privacy`, effective from 1.11.2026, without the surf day line)** **Lines in the privacy policy for iPhone and for sign-in with Apple** (`docs/APPLE.md`, 5.2; **the draft is ready: `docs/handoff/2026-10-31-ios-privacy/README.md`**, with three decisions: the text, the iPhone backup, and the country in PostHog; can be approved together with the surf day line): for your approval before submitting to App Store.
- [ ] 9. Read keys for PostHog and Sentry, so the team can read live data; and a check on the device of screen recording in the games in Android (`docs/handoff/2026-10-06-observability-ci/README.md`).
- [ ] 10. **Tests on the phone:** an invitation to a second phone and "אני על אותה טיסה" with a friend; the games in Android (feel, sound, vibration and performance); the sounds and vibration of the flight card.
- [x] 11. **Submission to App Store: everything is ready to type in** (`docs/APPLE.md`, 5.10 e; after 1 and 8): an "App Review" group with an invitation code without manager approval, for the reviewer; in App Store Connect the privacy label (without crashes, since there is no Sentry on iPhone), the rating questionnaire (expected 9+), the store page in three languages (`i18n/appstore-listing.md`) and the reviewer notes; `Support URL`: https://gudauri-ski-trip.vercel.app/support (live since 31.10.2026); manual release after approval. **(Done, 1.11.2026: version 1.0, build 17, sent for review.)**
- [x] 12. **Choose screenshots for iPhone for the App Store:** nine screens in two sizes, in English, Hebrew and Russian (the list in `app/ios/qa/store/store.txt`; the screenshots from the iPhone session, as you chose for Google Play). **The screenshots are ready (1.11.2026):** all nine in three languages, at 1320×2868 and 1206×2622 (Apple requires only one of them), in the `ios-store-shots` artifact of run 41225992654, until 30.11.2026; the selection page https://claude.ai/artifact/1WhYJcbdvLJxhM75da9mD7. Georgia receives the page in English. **Not mandatory for 1.0** (Apple session, against Apple's documentation): iPhone Duo (from April 2027, only with the iOS 27.1 SDK and above), Header asset and Search Results (can be uploaded after approval). (All nine, by list order; if fewer: 1 to 6 and 9) **(Done, 1.11.2026: uploaded and sent with the submission.)**
- [ ] 13. **Sign-in with Apple on the site** (`docs/APPLE.md`, step 7 and section 3): a service identifier for the site, a key, and configuration in supabase. On iPhone it already works without it. The secret on the site expires after 180 days, and needs renewing. (Not urgent; after the iPhone submission)

**C. Round 29: surf day — fully approved on 1.11.2026 (question 1 per the recommendation, question 9 a separate map, the rest as in the boards; the items below are kept as documentation)** (`design/round29/README.md`):
  - [x] 1. Where to start: **version 2 (31.10.2026):** a single entry "מקליטים", a ski-pass at the top of the signs page and a round button on the map, from anywhere and at any hour, with the same idea in both apps; after the tap recording runs, and you choose "כל היום" or "ירידה אחת". Or only the button on the map? (both)
  - [x] 2. One suggestion per day when opening inside the site, or none? (one suggestion)
  - [x] 3. Its own color scale for speed, from yellow to purple? (yes)
  - [x] 4. Share image without a name? (yes)
  - [x] 5. "סיום" asks before stopping? (per the boards)
  - [x] 6. "פה נפלת" as a playful question with "לא נפלתי", or not at all? (as a question)
  - [x] 7. On the lift, the run you just did? (yes)
  - [x] 8. "היום שלך" as a single card, with milestones against yourself? (yes)
  - [x] 9. The day map as a separate map (mountain and runs dimmed, only your run colored) or on the main map? (Pini hesitated; recommendation: a separate map, both alternatives in the canvas)
  - [x] 10. "מודדים ירידה": **Pini (31.10.2026): not from within a run panel**, but a recording that starts immediately when the app is opened, from anywhere and at any hour, even in the middle of the day. The designer proposes the entry.
  - [x] 11. **"הסיפור של היום" as insights** (instead of the tone question): version 2 from the content session, a single voice in four languages, against yourself only and without encouraging speed (`docs/handoff/2026-10-30-dayrec-moments-copy/insights.json`); in the canvas, round 29, row ט (version 97), and also in the timeline. (approve)
  - [x] 12. A sample day in the history, for the Apple reviewer and a new user before the season (yes)
  - [x] 13. **"Export as GPX"** (a route file that apps like Strava and Google Earth open): Pini: "too technical". Remove entirely from version 1? (yes; in version 2 in the canvas it has already been removed, and so has the warning board. So the sentence about export in the privacy line will be deleted too)
  - [x] 14. **Share card for the end of the trip** (content session, 31.10.2026): the summary of the whole trip is already computed in the engine, but didn't make it into the share image. Make it its own card? (yes, this is the moment friends share; the board is in the canvas, round 29, row ט, version 97)
  - [x] 15. **Contrast** (iPhone session; round 29, row י): green `#198549` instead of `#1B8A4C` (white text 4.67:1), dashed line `#77869A` (at night `#6A7C99`), and hourglass `#A87300`, on all platforms. (yes)

**D. Buildings, roads and paths in 3D in New Gudauri** (`research/3d-buildings.md`, section 5; the mockups in `design/round30/` and in the canvas, page "סבב 30"; the architect approved the POC on the site with four conditions):
  - [x] 1. Build a POC: about 25 buildings around the Goodaura and New Goodaura stations, on the site only, behind `?b3d=1`. **Approved (31.10.2026).**
  - [x] 2. Style: **realistic** (Pini, 31.10.2026): based on real photos, with colors, extension, snow, roads and road signs, and filling gaps so that someone who knows the place will recognize it. If photos are missing, the list will go to Pini.
  - [x] 3. Height: count floors from open street photos, and photograph what is missing on the January trip? (yes; today height is known for only 26 out of 230) **Approved (Pini, 1.11.2026).**
  - [x] 4. Add the floors found to the open map? (yes) **Approved (Pini, 1.11.2026); editing the open map only after the list goes through the manager session (external account).**
  - [x] 5. **The POC is built on the site** (`?b3d=1`, in the preview of `preview/b3d`; in the canvas, round 30, row ב, version 99): continue in this direction? And if you have photos of the buildings from the street, they will improve accuracy (the list is with the manager session). (yes) **Approved (Pini, 1.11.2026): continue.**

**E. Round 31: food and places at the stations** (`design/round31/README.md`, `research/places.md`) — **fully approved on 1.11.2026, all per the recommendations** (the architect approved the file with five conditions); handed to the ski expert (merging the data), to the site, to the Android and to the iPhone (the iPhone does not merge until Apple's answer on 1.0). The items are kept as documentation:
  - [x] 1. On the map: a "food" chip off by default, or markers always, or only when zooming in? **Version 2 in the canvas (31.10.2026):** a color for each type (food orange, coffee brown, first aid green, restrooms purple). A: small colored dots always, and the chip enlarges them to labels; B: labels only with the chip. (A)
  - [x] 2. Rescue posts in the list, without a promise and without a phone number; or only food and restrooms? (rescue posts in the list)
  - [x] 3. Old restrooms (2017, low certainty): don't show until a check in the field in December? (don't show)
  - [x] 4. On the site and both apps? (yes)
  - [x] 5. Only places with certainty A or B, and at an empty station "אין מידע" and not "אין כלום"? (yes)

**F. Companion character** (`design/characters/README.md`; the sketches in `design/characters/shots/` and in the canvas, page "סבב 32"):
  - [x] 1. Add a character? **Yes, but not in the directions of round 32** (Pini, 31.10.2026): a fully human character, in very high-quality illustration, identified with the app, in tight iterations. The illustrator prepares a first iteration and says honestly what level can be reached with our tools.
  - [ ] 2. The personality (dry, laughs at itself)
  - [ ] 3. A name for the character (not now)
  - [ ] 4. Where (site and both apps; in surf day moments only in the apps)
  - [ ] 5. Order (surf day first, then the home page and empty states)
  - [ ] 6. "פה נפלת": Pini asked for a joke, branding recommends neutral (a light text with "לא נפלתי", and the character shakes off snow without words)
  - [ ] 7. Final drawing (sketches now, final drawing after the design language is chosen, J below)
  - [x] 8. Which direction: **none of the three** (Pini, 31.10.2026): a full human character.
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
  - [x] Technique feedback: not doing it (Pini, 30.10.2026).

**I. The Gudauri map**
  - [x] 1. **Round 21, the Gudauri map** (`design/round21/README.md`; **Pini, 31.10.2026: "run names add clutter". Version 2 in the canvas: a small name in three options, on the line, beside the line and curving with it, or a tag; the recommendation: beside the line, because the line stays clean**): the name on the line, an open end for a partial run, an unnamed segment in a thin line and downhill arrows (yes); in a shared segment a combined name ("Tatra 1 · 2") or only the main one (combined in the same family, the main one when the names differ). **(1.11.2026) Pini approved only the names part: a small name beside the line and curving with it. Waiting for architect review on the name anchor in the data before code; the other items (open end, unnamed segment, combined name) are still waiting for him.**
  - [x] 2. **Gudauri data against the official map** (`research/mta-map-compare.md`, `research/map-audit.md`): name and type corrections without geometry (Kobi and Truso, Zuma as a beginner area, New Goodaura 1 and 2, a connecting road), marking the missing ones, and the start of Tatra 2 as a shared segment with Tatra 1 (`docs/ROADMAP.md`). (yes) **Approved (Pini, 1.11.2026):** in branch `claude/data-official-fixes` with the ski expert; without changing `key` and without geometry; merge coordinated with the site session.
  - [x] 3. **The rest of the lift data against the sources** (`research/lifts.md`, section 5; cable speed and travel times were already corrected): the height difference (`rise`, including Firni), the year, the capacity and whether there is a bubble per the sources; Zuma is a drag lift (`j-bar`); and also fix in the open map Firni, which received the tags of Goodaura. (yes for the data; the open map only if you want) **Approved (Pini, 1.11.2026) for our data; the open map not.**

**Not now: other ski resorts** (Pini, 31.10.2026: focus on what is relevant to the app in Gudauri). Kept here so they don't get lost, and are not being worked on:
  - **Round 25, a readable map at a large resort** (`design/round25/README.md`): valleys across the whole mountain (A), a chip for each valley (B), valleys also in 3D and on the phone circling the mountain (D), and the list divided into valleys; "list first" (C) no. (A, B and D)
  - **The next ski resorts** (`research/resorts.md`, "שאלות לפיני"): the order (Mayrhofen first), Hermon (not now), the southern hemisphere (not now), Bakuriani (quietly, when there is a structure for multiple resorts), Israeli demand (counting flights, without reaching out externally).
  - **Sella Ronda:** the Sellaronda circuit on the map (yes, via canvas and architect); a link to the official maps of Val Gardena and Arabba (only Pini can download); languages for the site in Italy (Italian and German).
  - **Data corrections from the ski expert's check (31.10.2026):** in the lower segment of Dantercepies Variante the color is red and not blue; Kids Fun Line La Crusc is a park, and therefore leaves the data, and the tool will always skip anything that is not `downhill`. In 33 and 34 in Sölden and the four runs of Arabba the lines are correct, and therefore stay (`research/resorts/sellaronda-official-compare.md`, `research/resorts/soelden-official-compare.md`)

**J. Design, name and branding**
  - [x] 0. **Redesign for the apps (15.11.2026):** four completely new directions in the canvas https://claude.ai/artifact/8NrAKy97KWRj3JWcxx41yQ (`design/redesign/README.md`), and six questions in the opening board there: which direction or combination, what the first screen is, the lines diagram in direction D versus the accuracy rules, "הטיול שלך" as a single row, rounds 39 and 40 in the old language (answered, 18.11.2026: release in the existing language), and the site. (The skeleton of A "ההר", with the recording screen of C and the "עכשיו" row of B) **Closed (20.11.2026, decision 83):** Pini chose round 3, which is built on the skeleton of direction A.
  - [x] 0a. **Redesign, round 2: "ההר המצויר" (19.11.2026):** in the same canvas, on the page "סבב 2 · ההר המצויר" (`design/redesign/README.md`). Five questions in the opening board: a pre-drawn mountain with three views instead of live 3D; "מצויר" or "דגם שכבות"; "סרט הירידה" instead of the elevation profile; the fly-down as a moving dot; light by hour in four images. (yes to all, and "מצויר") **Closed (20.11.2026, decision 83):** round 3 replaces it.
  - [x] 0b. **Redesign, round 3: "ההר, הבחירה והיום" (19.11.2026):** in the same canvas, on the page "סבב 3" (`design/redesign/README.md`). Seven questions in the opening board: the new mountain (sky, ridges, real light, details); weather on the picture; ways of choosing (magnifying glass, from the station, from a shelf); the lens "בשמש עכשיו" (brings back the sun map, decision 11); marking a selected run; where to go deeper outside the mountain (morning, lift, drawing the day, the village); and "ההר שלי" and "שקט בירידה" (bringing back the run passport and glove mode, decision 11). (yes to the first ones; morning and drawing the day first; the last only if you want) **Chosen in full (Pini, 20.11.2026, decision 83).** **(20.11.2026) Pini accepted all the recommendations:** a line of light by day and beads of light at night, and the three proposals that bring back decision 11 are approved.
  - [x] 0c. **Redesign, round 4: surf day (20.11.2026):** in the same canvas, on the page "סבב 4 · יום הגלישה" (`design/redesign/README.md`). Six questions in the opening board: a speed scale as light (lilac to burning pink) instead of yellow to purple; "שקט בירידה" (only speed, run name and finish); "החזקה לסיום"; the lock screen without live speed and with "סיום" also on iPhone; "היום בדקה" as the replay; and a second view from the Kobi side. (yes to all) **Approved (Pini, 20.11.2026, decision 84).**
  - [x] 0e. **Redesign, stage B: the living mountain (21.11.2026):** in the same canvas, on the page "שלב ב · ההר החי" (`design/redesign/README.md`). Three questions in the opening board: the opening view lower and farther, with sky and horizon (runs smaller at the opening); the real Caucasus on the horizon, with Kazbek; and whether this is the view you meant. (yes to the first two) **Answered (Pini, 21.11.2026): "I accept the recommendations"; the priority is that the mountain itself be spectacular, and tall mountains in the background "not urgent".**
  - [x] 0f. **Redesign, a run behind the mountain (21.11.2026):** in the same canvas (version 21), on the page "שלב ב · ההר החי", the board "מסלול שמאחורי ההר: Kobi". Two questions: the mountain has two sides, and the camera rotates only to show a hidden run (today only Kobi); and after closing the Kobi sheet we stay on its side, with the sign "צד Kobi" that returns to the front. (yes to the first; we stay) **Approved (Pini, 21.11.2026: "I approve both recommendations").**
  - [x] 0d. **Redesign, completion round: the three parts were handed over (23.11.2026).** Part 1: `design/handoff/2026-11-21-redesign-day-gaps/`; part 2: `design/handoff/2026-11-21-redesign-around/`; part 3 (group and invitation, sign-in and account, the games, settings, language and about): `design/handoff/2026-11-23-redesign-people/`. **Fully approved (Pini, 23.11.2026, decision 88),** including the language badge that moves to "אני" (designer's interpretation).
  - [x] 0g. **Redesign, the navigation map (23.11.2026):** in the same canvas (version 31), on the page "המפה", the board "מה עוד לא הוחלט". Eight decisions, each with a recommendation: how to enter the village, how to reach "הימים שלי", where the full forecast is, "איפה אני", the map buttons, the first time, the full run sheet, and returning to the opening view. (the recommendations are in the board) **Drawn per the recommendations (23.11.2026):** the page "השלמה ד · על ההר" in the canvas, version 33, 11 boards; the handoff in `design/handoff/2026-11-23-redesign-on-mountain/`. **Approved (Pini, 23.11.2026), with all of parts 4 to 8.**
  - [ ] 1. **A new design language** for the whole site: five languages in a separate canvas (https://claude.ai/artifact/9yxrGgytb2Gmi4i1UqEhST), three questions on the opening page there (which language, what stays from today, and whether it moves to the apps).
  - [ ] 2. **Design language audit:** 15 suggestions in the "מה לאשר" board (https://claude.ai/artifact/TW8xaEsc2EjqodwLcrMzaw, `design/audit/README.md`). Some of them will change if a new language is chosen.
  - [ ] 5. **B9, text in a frame (26.11.2026):** canvas page "ב9 · טקסט במסגרת" (`B900` to `B93`, version 74). Two questions: after 30 s the search line joins "מחפש מיקום" in one frame (`B91`) or a second pill (`B93`) (one frame); the ski pass head stays as is, a page over a veiled mountain (yes).
  - [ ] 6. **Countdown tile before the season, version 2 (26.11.2026, Pini's direction):** canvas `CT00` to `CT5` (version 80, page "רמזים לפעם הראשונה"), hints handoff section 6. A 64 square with only the number in a ring that fills over the wait; a tap opens it to the caption for 3.5 s; a tap on the open tile opens the season sheet. Three questions: the tile (yes); opens by itself once a day until the opening (yes); tap on the open tile opens the season sheet (yes).
  - [ ] 4. **First-run hints, version 2 (26.11.2026, after Pini's notes):** canvas page "רמזים לפעם הראשונה" (`HN00` to `HN12`, version 79, wording from `i18n/strings.json`), `design/handoff/2026-11-26-redesign-hints/`. **Approved (Pini, 26.11.2026): the reset row in Settings and "מחפש מיקום" in a pill.** Still open: the guided sequence (six on the mountain, two in the village; tap anywhere = next; X closes all; 8 s ring) (yes); the eight texts (yes); a tap on the countdown tile opens the season sheet (yes). Content session proposed more natural wording for the hint strings; Pini picks.
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
  - [x] 1. Android: the sun, moon and stars in the map's sky (`docs/APP-NATIVE.md`).
  - [x] 2. Android: the language badge and the language list on the home page (decision 47).
  - [x] 3. iPhone, games and trip form: the sound and back icons in the games, an X button to exit mid-game, 44 buttons in fresh snow, the iPhone calendar and time wheel in the form, and ski days in translucent blue at night.
  - [x] 4. iPhone, meeting point: "שעה אחרת" in the iPhone time wheel, and the title in the card that stops before the X.
  - [x] 5. iPhone, the group: the flight card with a dashed seam, the station name at the meeting, and the reminder as a check mark.
  - [x] 6. Round 16: close in the canvas the seven notes that were already handled (the play mark, the videos, the lift board, the profile, the moon, the stub and the buttons in Georgian).

**M. Testing and release** (head of quality, `docs/QA.md`):
  - [ ] 1. **Which iPhone tests the builds from TestFlight** (model and system version)? Without it, rows 24 to 39 in the list cannot be marked before the App Store submission. (an iPhone that supports iOS 26, Pini's or a friend's who joins as an internal tester)
  - [ ] 2. **Who the testers in Gudauri in December are**, with which phones and when? (at least an Android with a barometer, an Android without a barometer and an iPhone)
  - [ ] 3. **Raw log export for testers, with written consent:** the conditions were set with the legal session and approved by the architect (31.10.2026): only in test builds, up to 15 testers aged 18 and over, the files with you until 31.3.2027 and not in the repo. Waiting for you: the consent wording, the line in the policy, and a check that training is off in your Claude account (`docs/handoff/2026-10-31-tester-log-consent/README.md`, `docs/QA.md`). Without it surf day cannot be calibrated from the field. (approve) **And how:** the export exists only in the test build on Android and in a separate build for testers on iPhone; the pipeline fails if it reaches the store build. iPhone testers join as internal users in your App Store Connect account, because for an external group Apple performs a review. **Do not send to Apple review a build marked for testers** (`docs/APPLE.md`).
  - [ ] 4. **On the trip (11 to 14.1.2027): one skier with two phones**, Android and iPhone, every day. Who? (yes)

**N. Round 33: the support page (approved, Pini, 31.10.2026: "I approve", per the designer's recommendation: all as in the boards)** (`design/round33/`; for the `Support URL` in the App Store, `docs/APPLE.md` 5.6). **Done on the site (31.10.2026):** https://gudauri-ski-trip.vercel.app/support in four languages, and a "תמיכה" row in about. **The row in about was also merged on Android and iPhone (1.11.2026).** **Remaining:** type the address in as the `Support URL` in App Store Connect (Pini).

**O. Round 34: snowball fight, seeing the friends** (`design/round34/README.md`, in the canvas on the page "סבב 34"; the opponents are too small):
  - [x] 1. Which framing: corrected D, a lens 1.4 times longer and the horizon at 50 percent, with the opponents denser across the width and the view moving with the finger (no change in distances, only the opponent is larger on the screen); or A, B, C of the iPhone session (A and C change the distance, and in stage 5 take opponents off the screen)? (corrected D) **Approved: corrected D (Pini, 1.11.2026); handed to the site, to the Android and to the iPhone (the iPhone without merging until Apple's answer on 1.0).**

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
- 26.11.2026: **Content (Opus): first-run hint texts v3 in `i18n/strings.json`, four languages.** Eight `app.hint_*` keys rewritten in plain Hebrew; the village hint now lists what is there, the trip hint says it is your flight dates and what they give. Key names unchanged; `app.hints_again` kept; wording pending Pini; `check.py` green.
- 26.11.2026: **Content: `app.search_hint` set to Pini's wording** ("עוד רגע ונמצא אותך.", four languages) and hint texts polished (find, rec, village, me, group; keys unchanged). `check.py` green.
- 26.11.2026: **Android QA: manual part `storesmoke` merged to `main`** (branch `claude/qa-store-smoke`, run 45866893596 green on `fe0dc76`, 7 shots). Clean install of the STORE flavor (R8, debug key only, no qa hooks, `apk-store` artifact 3 days): season board closed by a touch, permission prompts, record button, recording by the recorder service, finish, end screen. The screen dump fails while the mountain animates, so the end-screen text is a note and the shot is the evidence. Manual only (not `core`/ALL); added to the pre-upload checklist in `docs/OPERATIONS.md`; architect and DevOps reviewed.
- 26.11.2026: **Product designer (Opus): the pre-season countdown tile, version 2, per Pini's direction (canvas 80, `CT00` to `CT5`).** A square with only the number in a ring that fills from MTA's announcement to the opening; a tap opens it to the caption for 3.5 s, a tap on the open tile opens the season sheet; opens by itself once a day. Hints handoff section 6 rewritten; no new strings; waiting for Pini.
- 26.11.2026: **Android (Sonnet implementer): hints settings row (HN9) and the search pill (HN10, B91).** "להציג שוב את הרמזים" in Settings clears the hint prefs and restarts them on the mountain; "מחפש מיקום" in a glass pill that grows to hold the 30 s line. Not tested on a phone.
- 26.11.2026: **Android (Sonnet implementer): B10 built, long press on icon-only buttons shows the name in a pill (`NameHold.kt`).** Switch cells, layers, compass, where-am-I, record, "אני"; tap unchanged, no new strings. Not tested on a phone or emulator; no new QA stage.
- 26.11.2026: **Content: hint texts v2 in `i18n/strings.json`** (7 changed, `app.hint_trip` and three a11y keys new, `app.hints_again` reworded), natural Hebrew without colons per Pini's notes; wording still pending his approval; `app.hint_close_a11y` now unused. `check.py` green.
- 26.11.2026: **Product designer (Opus): first-run hints version 2, after Pini's notes (canvas 77, `HN00` to `HN12`).** A guided sequence: six on the mountain at the first opening and two in the village sheet; tap anywhere = next, X in a draining 8 s ring closes all, dots, light dim with a hole on the target; longer texts, a new hint for "הטיול שלך". Handoff updated; strings to content; waiting for Pini.
- 26.11.2026: **Product designer (Opus): B10, buttons without words, in the canvas (version 75, `BT00` to `BT4`), waiting for Pini.** Hints 3, 5, 6 already cover record, switch and "אני"; for layers, "איפה אני" and the compass a long press shows the name in a pill. No new strings; handoff `design/handoff/2026-11-26-redesign-b10/`; not a release blocker. Monday board not updated by me.
- 26.11.2026: **Product designer (B9): text in a frame, canvas version 74 (`B900` to `B93`), waiting for Pini.** Search title pill (`HN10`) and countdown tile (`HN1`) already cover most of B9; added: after 30 s the pill grows into one frame with `app.search_hint`. Ski pass head unchanged (whole page over a veil). Handoff `design/handoff/2026-11-26-redesign-b9/`; no strings, no code.
- 26.11.2026: **Product designer (B8): no new boards; B8 is covered by the hints round (`HN1`, hints handoff section 6).** The countdown becomes a small tile, the mountain opens free and `hint_run` comes first, which answers both options in the release review. Coordinated with the hints designer; no strings, no code.
- 26.11.2026: **Content: five Android first-run hint strings added in four languages** (`app.hint_rec`, `app.hint_mode`, `app.hint_me`, `app.hint_group`, `app.hints_again`), per hints designer section 9; wording pending Pini's approval with the boards; ru/ka unreviewed (decision 39). `check.py` green (2007). Monday board not updated by me.
- 26.11.2026: **Product designer (Opus): first-run hints for the Android release, in the canvas (version 73, `HN00` to `HN10`), waiting for Pini.** One bubble with a ring on the target; six mountain hints (run, shelf, record, village, picture/live, "אני"), at most two per opening, one in the village sheet, reset in Settings; also B8 (countdown tile), B9 (text in a frame) and B10. Handoff `design/handoff/2026-11-26-redesign-hints/`; five strings to content; design tool only.
- 26.11.2026: **Android (Sonnet implementer): release fixes B2, B3, B5-B7 of the release review.** Shelf summary in a pill, run card height fits its content, `redesign3` reminder step (row 18), "לאפליקציה" only in DEBUG, QA.md section 5 row 1 updated. B4 (hints) left to the designer. Not tested on a phone.
- 26.11.2026: **Design reviewer: release-readiness pass of the Android redesign (`design/redesign/review-release.md`).** All 18 rows of `docs/QA.md` §5 exist and are reachable, no dead end; row 18 (reminder) not shot on the new design. One blocker: no full `android-qa` run on current `main` (with Stage), and RX3, pulsing search and EM never shot on the emulator. Five small fixes before store (shelf summary floating unframed, shelf card's last line clipped, `hint_find`/`hint_village` not built though the bar exists, reminder QA step, QA row 1 wording); three items for the designer. No code changed; not checked on a phone.
- 26.11.2026: **Server: weather fixed (Pini approved).** Merged to `main`, function `api` deployed (v13, source verified identical to the repo), one manual `fetch_weather` returned 200 and `private.weather_cache` is fresh. Cause: Open-Meteo answered `503` to every hourly fetch from 20.11 (reason never logged); fix = retries, reason in logs and 502 body, optional `OPEN_METEO_API_KEY`. New `weather-freshness.yml` alert (3 hours). Watch the next hourly fetches; if 503 returns, a paid key is Pini's decision.
- 25.11.2026: **DevOps: `android-qa` publish no longer empties the release.** Upload first, delete only stale assets after, no literal glob when there are no contact sheets (run 467). Vanishing emulator: diagnostic run in progress.
- 25.11.2026: **Android redesign: the opening on the picture (Stage) merged to `main`** (Stage implementer, Opus, branch `claude/android-redesign-stage`). The `Stage` interface: the live model and a new `PictureStage` under the same layers; the picture is the default, the picture/live switch at the top of the button column, the choice kept on the phone only; one tap for both (`StagePick`, the contract's radius, a hidden line never chosen); the swap through one screen snapshot, never both in memory; the film and Kobi go live for a moment (Z05). Unit tests against `peak-projection.json`; emulator `redesign5` checks the picture (day, night, he and en, a real tap, picture freed before live draws), its own steps green; its tail after the video still fails as before. **Open:** Kobi pictures not downloaded yet (live instead), no fly-in to the village from the picture, a phone check.
- 25.11.2026: **DevOps: "core only" stage B merged (Pini).** Main, `all` and `auto` on main run only the `core` QA part (~5.5 min per emulator job); all other parts are manual via `workflow_dispatch`; `i18n/strings.json` no longer triggers `android-qa`. Text-only pushes start neither emulator nor iOS simulator (iOS screenshots are manual). Measured times follow after the first main runs. `docs/CI-AUDIT.md`.
- 25.11.2026: **Android redesign: the Opus implementer's row set to free** (manager's request). The seam and `core` are on `main`. The emulator that vanishes while the day is drawn is with DevOps.
- 25.11.2026: **Android redesign: QA part `core` merged to `main` (`claude/android-redesign-root`; `android` green, `android-qa` part `core` green on `1d4231b8`: 6 shots, 0 failures, about 5.5 min per emulator job, shots checked by eye).** `core` in `app/android/qa/run.sh` (in `ALL_SCENARIOS` and the allowed list): the live mountain, a run, the village, "me", and a recording on the day screen (asks for `qa.redesign day` first, as the debug build needs). Also a NaN guard in the redesign's line shader (`LINE_VS`: no widening for points at or behind the eye, any direction for a zero-length segment). **Not fixed by it:** the emulator itself still disappears now and then while the day is drawn on the mountain (parts `store,lang` and `redesign2`; the same on `main`, run 45687633364), so that crash has another cause, still open. DevOps can merge `ops-core`.
- 25.11.2026: **Android redesign: the seam at the mountain's edge merged to `main` (`claude/android-redesign-seam`, `android` and `android-qa` part `redesign` green, 67 shots, 0 failures).** The ring around the model now gets its shadow from the model itself across a band (`LiveRing.modelBand`, every 3 cells with bilinear fill, stopped at its budget: the larger of 150 ms and half the mountain's own shadow time), so the bright line where the model meets the ring is gone in the near, far and north views (checked by eye, before and after). Emulator frames on the seam view: 99th percentile 2.0 s before, 0.7 s after. QA: the wait for the view to come back after the shelf and the station is 25 s (the mountain is drawn again there; up to 9 s at load 16).
- 25.11.2026: **Manager session: STATUS shrunk from 205KB to about 82KB (token saving, Pini).** Decisions moved to `docs/DECISIONS.md` (index stays here), the 24.11 log and the full 25.11 log moved to `docs/STATUS-ARCHIVE.md`, this log compressed to 1-2 sentences per entry. Limit now about 60KB, weekly archiving (CLAUDE.md rule 12). Open now (32KB) is the next candidate.
- 25.11.2026: **Android redesign: three screens merged to `main` (`claude/android-redesign-s4`, `android` green on `0f726524`).** Pulsing search screen, RX3 recording button in the bar (`app.sr_rec_live`) and the end screen without descents (EM). Opening on the picture postponed until after release (Pini); not checked on emulator or phone.
- 25.11.2026: **Manager session: `docs/STATUS.md` translated to English (Pini, token saving).** Structure, ids and numbers verified against the Hebrew original in git history; log lines are English from now on. Archive, ROADMAP, FEATURES and HANDOFF stay Hebrew until read.
- 25.11.2026: **Manager session: new task, opening on the picture (Pini).** Dedicated Opus session `session_017QrNw8CkjGjjF8LtqeGehJ`, branch `claude/android-redesign-stage`: the `Stage` interface, a picture mode with all functions and a toggle in the buttons column. Monday board not updated by me.
- 25.11.2026: **Token savings: further recommendations approved (Pini) and added to rule 12 in `CLAUDE.md`.** Session per task, effort level, images only of what changed, Monday once a day, broad search in a cheap agent. The manager session moved to Sonnet.
- 25.11.2026: **New decision (Pini): releasing without the opening on the picture.** It needs the shared `Stage` interface (architect-approved, not yet built), so it is built after the release, probably by an Opus implementer. The release gets pulsing search, RX3 and EM; the screen stays open in `docs/ROADMAP.md`.
- 25.11.2026: **Manager session: token savings (Pini).** `docs/STATUS.md` cut from about 1MB to about 235KB by moving old phases, decisions and log to `docs/STATUS-ARCHIVE.md`. `CLAUDE.md` now auto-loads only STATUS and has the new rule 12; nine finished sessions were archived.
- 25.11.2026: **Manager session: second implementer on Sonnet, to save (Pini).** `session_017QrNw6gpsVzdKmiHmWpPrc`, branch `claude/android-redesign-s4`: pulsing search, opening with the picture, RX3 and EM. The Opus implementer keeps only the seam and the `core` emulator part.
- 25.11.2026: **Architect: "main activity only" (`ops-core`, DevOps) approved with two conditions.** Before each store upload a full manual run of all `android-qa` parts and `ios.yml` with screenshots, green on the same commit; `core` fails on any crash or ANR. Recommendation: one full run per week; no code changed.
- 25.11.2026: **New decision (Pini): reducing the test scripts.** `main` gets one short emulator part (about 10 min: opens, mountain drawn, run touch, recording start/end, village, "אני"); all else and iPhone screenshots only on manual runs. Sent to DevOps and the implementer, architect review before merge; the implementer stops fixing `run` and `bigtext`.
- 25.11.2026: **Manager session: pipeline speed research (`research/ci-speed.md`).** 806 runs in 7 days (about 180 hours); the emulator fails in `main` in about two thirds of runs, always `run` and `bigtext`. Recommendation: screenshots and full emulator at night and before release; target branch push 5 min, `main` 15; build 0.1.916 delivered; no code changed.
- 25.11.2026: **Architect: fast `main` (`ops-fast`, DevOps) approved with three conditions.** `build-ios-strings.py` runs in `core` on every run; `site/img/pano/` and `site/audio/` enter the `ios.yml` paths; a change in `tools/check-ios-` gates runs the simulator. `run` and `bigtext` red in `main` are an implementer blocker before release; no code changed.
- 25.11.2026: **Content: four old keys deleted from `i18n/strings.json`.** `app.rec_soon`, `app.days_delete_hint`, `app.ride_hint_haptic`, `app.rec_gps_weak` removed after `eba2891e` (in `main`, `97b63530`) was confirmed and no code calls them; `app.rec_perm_local` stays. `check.py` green (2002).
- 25.11.2026: **Android redesign: `claude/android-redesign-root` merged to `main`.** Fixed Pini's bug (touching the mountain before the season now selects a run), added the new far ring, adapted old emulator tests, and `android` plus `android-qa` ran green. Open: a meeting-link date outside the coming week moves to tomorrow; the seam is in a separate branch awaiting QA.
- 25.11.2026: **Content: wording of the end screen without descents (EM00 to EM4) entered in four languages.** 13 `app.empty_*` keys added to `i18n/strings.json`, reusing `app.day_sample`, `app.days_delete`, `app.vill_back`. `check.py` green (2006); ru and ka without speaker check.
- 25.11.2026: **Product designer: end screen without descents approved (Pini) and handed to the implementer.** Marked approved in canvas version 70 (boards `EM00` to `EM4`); handoff `design/handoff/2026-11-25-redesign-empty-end/`. 13 new strings went to the content session; checked in a local render only.
- 25.11.2026: **Product designer: recording button screen-reader wording fixed.** During a recording a tap opens the live screen, so the wording is `app.sr_rec_live` ("מקליט, {time}. פתיחת המסך החי."), not "עצירת ההקלטה". Fixed in the handoff, boards `RX00`, `RX3` to `RX5` (canvas 69) and `docs/ROADMAP.md`.
- 25.11.2026: **Product designer: recording button approved (Pini): time inside the button (`RX3`).** Canvas version 68 marks `RX00` and `RX3` approved and `RX5` not chosen; handoff `design/handoff/2026-11-25-redesign-record-button/` marked approved. End screen without descents still waits for Pini.
- 25.11.2026: **Content: `app.sr_rec_live` entered `i18n/strings.json` in four languages.** Screen-reader text for a recording, with `{time}` built in the client; `check.py` green (1993).
- 25.11.2026: **Product designer: end of a recording without descents, in the canvas (version 67, boards `EM00` to `EM4`).** Title, three facts, a sample-day card and two buttons ("חזרה להר", "מחיקה" in two taps), in four cases; waits for Pini, no handoff. Tool `design/redesign/tools/em_boards.py`; local render only.
- 25.11.2026: **Product designer: three decisions of Pini in canvas version 66.** Record button inside the bottom bar (red circle in a ring, square and time while recording, `RX00` to `RX5`); search screen approved as is; simple speed ring (`GS6`) chosen over the car-style gauge. Handoff `design/handoff/2026-11-25-redesign-record-button/` rewritten.
- 25.11.2026: **Architect: direct answer on the seam branch (`claude/android-redesign-seam`, `4341b374`).** Same conditions as the previous line: weight check is the sum of the whole set up to 1.2MB, the bending check fails on any run, lift, label or touch target in the area, shadow time counts within the ring's 150 ms. No code changed.
- 25.11.2026: **Product designer: Pini's decisions on record button, search and speedometer (canvas 65).** Record button floats above the bar in the thumb corner (recommendation b), search timer starts at 0:00 from first location, and a gentle car-style gauge is proposed next to the ring (waiting for Pini). Handoff `2026-11-25-redesign-record-button` added.
- 25.11.2026: **Architect: seam fix between mountain and ring (`ringband.webp`, board `Y03`) approved with conditions.** Packed set is 1,185KB, so the ceiling rises to 1.2MB (1.3MB full) with a build check; bending only in drawing, `terrain.json` unchanged, plus an emulator measurement before and after. No code changed.
- 25.11.2026: **Product designer: front seam between live mountain and ring fixed, board `Y03` (canvas 64).** Three causes found (ring held 900 m edge height, relief entered late, rock and shadow differed); the ring keeps its heights and the mountain's last 1,500 m bend to them via `ringband.webp` (228 KB), zero extra triangles. Handoff `2026-11-25-redesign-ring-detail/` step 4; local tool check only.
- 25.11.2026: **Product designer: opening with the picture handed off; search screen updated to version 3 (canvas 63).** Handoff `design/handoff/2026-11-25-redesign-opening-picture/`: picture is the default, toggle at the top of the column, choice saved, no new text. Search handoff updated (no pill or steps line, "בחוץ..." after 30 s); timer waits for Pini.
- 25.11.2026: **Product designer: second round on "Notes on version 56" (canvas 62; waiting for Pini).** Three record-button alternatives without words (b, a 62 floating button, recommended), search version 3 with or without a timer, and a dashboard-style speed ring (`GD00` to `GD4`). Tool `design/redesign/tools/v56b_boards.py`; local render only.
- 25.11.2026: **Content: gender-neutral wording per Pini.** `app.rec_hold` changed to "להחזיק לסיום" and "את/ה" and "יכול/ה" removed from `app.a_delete_note`, `app.g_reclaim_intro`, `app.g_entered_by`. Similar forms remain in non-`app.*` keys (tester consent, group), waiting for a decision.
- 25.11.2026: **Android redesign: "לרדת בסרט" now covers the whole run (merged to `main`).** Multi-part run lines are joined into one path, the point reaches the end, the camera rises over high slopes, and unnamed next runs are no longer shown. Emulator test on 33 runs OK; known: `redesign2` emulator falls after the "היום בדקה" video, moved to DevOps.
- 25.11.2026: **Product designer: search screen, how much text (canvas 61, boards `GX00`, `GX1d` to `GX3n`).** Three options; recommended is pulse plus "מחפש מיקום" only, with the "less than a minute" line after 30 s without location. Waiting for Pini, no handoff; tool `design/redesign/tools/gx_boards.py`.
- 25.11.2026: **Product designer: recording button, three alternatives (canvas 60, boards `RD00` to `RD6`).** a traces only, b the day's light line, c (recommended) caption "הקלטת גלישה" until the first recording ends, then icon only. Waiting for Pini, no handoff; tool `design/redesign/tools/rd_boards.py`.
- 25.11.2026: **Content: `app.rec_sign` and `app.sr_dock_rec` merged as "הקלטת גלישה" in four languages (Pini).** The feature name stays "מעקב גלישה" and the caption may change later. `check.py` green.
- 25.11.2026: **Content: natural-Hebrew wording of the new language entered `i18n/strings.json` in four languages (Pini approved).** 40 keys updated and 6 added (search screen); `app.rec_sign` and `app.sr_dock_rec` held for Pini. Old keys not yet deleted because Android code still reads them; `check.py` green.
- 25.11.2026: **Product designer: Pini's notes on version 56 in the canvas (version 59).** Compared the picture opening against the live mountain with full ring (`Y02`, recommend live if 30 fps holds), anchored the record bubble to the button, and redesigned live-screen states. Waiting for Pini; "before the season" and capability-showcase rounds need a stronger model (flag, rule 12).
- 25.11.2026: **Product designer: pulsing search screen of the surfing day handed off for building.** Canvas version 58, boards `GS1` to `GS3`; handoff `design/handoff/2026-11-25-redesign-search-pulse/README.md`; no speed number until a location and no reception bars. Wording and feature name go to content and Pini.
- 25.11.2026: **Pini on the canvas (13 notes) and new rules.** Opening should be the most spectacular, picture/live toggle in the buttons column, natural Hebrew record-button text, search stays alive and pulsing, less generic text, and capabilities shown spectacularly. Two permanent rules went into `CLAUDE.md`; handed to designer, implementer and content session.
- 25.11.2026: **Product designer: board code at the end of every title in the redesign canvas (version 56).** At the manager session's request 248 titles received the code in parentheses, e.g. "(Y02)", so Pini can name boards; nothing else changed. Rule in `design/redesign/canvas/CONVENTIONS.md`.
- 25.11.2026: **Architect: ring widening around the mountain (board `Y02`), light alternative approved.** 300 m cells up to 22 km, up to 48 thousand triangles and 64KB, 90 m relief map, set stays within 1MB; the full 92-thousand-triangle version only after a phone measurement. Shadow on the ring allowed outside the drawing thread; no code changed.
- 25.11.2026: **Product designer: mountains around in the same quality as the mountain, board `Y02` (canvas 55) and handoff.** Recommended three steps (inner 240 m ring to 24 km, 60 m relief map, height-dependent haze) exceed the approved budget (about 92 thousand triangles, 103 KB heights, 156 KB map), light option 44 thousand. Handoff `design/handoff/2026-11-25-redesign-ring-detail/`; for the architect.
- 25.11.2026: **Product designer: live recording screen in the canvas (version 54, "Before the season, and the recording button").** Recommends "מקליטים" only, moving "מודדים ירידה אחת" to the run sheet, and a sample-day peek until a location is found. Waiting for Pini, no code; tool `design/redesign/tools/season_boards.py`.
- 25.11.2026: **Android redesign: measurement bar of the test build no longer drawn over the live recording screen (`03d2c9cd`, `android` green).** Pini noticed it covering "מחפשים לוויינים"; the diagnostics log continues and the store build did not change. `peak-check.yml` now runs the painted-mountain contract check on GitHub.
- 25.11.2026: **Painted mountain: architect's three contract fixes merged to `main` (`358f86b5`, `android` 881 green).**
- 25.11.2026: **Product designer: two requests from Pini in the canvas (version 53, "לפני העונה, וכפתור ההקלטה").** First screen before the season in three alternatives (recommended A, a car rising on the real Goodaura line toward the 20.12 opening) and three record-button options (recommended C). Waiting for Pini, no code.
- 25.11.2026: **Architect: retroactive review of the painted-mountain contract (`70ee060a`) approved with three corrections.** `rev` computed from images and view tables together, a `rev` for the front view too, and a `--check` mode run on GitHub. `docs/ARCHITECTURE.md`; no code changed.
- 25.11.2026: **Painted mountain: `site/data/peak.json` (`schema: 1`) and test values merged to `main` (`046f349e`, `android` 879 green).**
- 25.11.2026: **Android new mountain: return from shelf, sheets to the bottom, and a ring of distant ground.** Fixed Pini's bugs from 0.1.867 (camera and names after "לאן יורדים?"), added a real-elevation ring (`tools/build-ring.py`, 29.8KB, about 28 thousand triangles); QA 420 green in all five parts. Remaining: phone measurement and Stage after Pini's comments.
- 25.11.2026: **Product designer: Kobi side in the picture delivered (canvas 52).** Seven side images, 327 KB (ceiling 350), projection table for the `kobi` record in `site/data/peak.json`; recommends a cross-fade with a short pan. Handoff `design/handoff/2026-11-25-redesign-kobi-picture/`; local render only.
- 25.11.2026: **Content: string for the Kobi-side message in picture view.** `app.kobi_side_wait` ("התמונה של צד Kobi תיטען כשתהיה קליטה") added in four languages, shown when side images have not downloaded and there is no GLES 3. `check.py` green.
- 25.11.2026: **Architect: Kobi side in picture view on Android approved with conditions.** The set is not packaged but downloaded from `site/img/peak/kobi/<rev>/` (up to 350KB) and used only when all seven keys share the `rev`; otherwise a temporary switch to live, and a message without `GLES 3`. `docs/ARCHITECTURE.md`; no code changed.
- 25.11.2026: **Content: another string for the picture/live toggle (board Z05).** `app.mode_live_temp` ("חי לרגע · חוזרים לתמונה") added in four languages; `check.py` green.
- 25.11.2026: **Product designer: addition to the "picture or live" board (canvas 49).** The Kobi side from the picture is "live for a moment", not a new picture, and "לרדת בסרט" and "היום בדקה" follow the same rule; new board `Z05` with a tag under the pill. New text proposed to content.
- 25.11.2026: **Content: four strings for the "picture / live" toggle (boards Z00 to Z04).** `app.mode_photo`, `app.mode_live`, `app.mode_live_hint`, `app.mode_live_go` entered in four languages; `check.py` green.
- 25.11.2026: **Product designer: two boards for Pini's comments on 0.1.867 (canvas 48).** The floating-mountain edge (`Y01`; recommended A, real 30 km ring from open data) and the picture/live toggle (`Z00` to `Z04`, default picture until the edge is fixed). Four new strings proposed; waiting for Pini.
- 25.11.2026: **Architect: mountain mesh edge on Android decided as a coarse ground ring.** 500 m cells, 60 by 60 km, from the same source as `terrain.json`, plus a 10 km maximum camera distance; stored in the repo up to 64KB, scenery only. Phone measurement required before store upload; `docs/ARCHITECTURE.md`.
- 25.11.2026: **Architect: painted mountain returns on Android as a selectable mode with a shared `Stage` interface, approved with conditions.** Projection and test values (`site/data/peak.json`, `tools/fixtures/peak-projection.json`) go in before code; touch identical in both modes and no simultaneous graphics memory. No Kobi picture packaged; no live-model buttons without `GLES 3`.
- 25.11.2026: **Design reviewer: run 414 (`a298968c`), latest fixes checked (section 9).** Film by day, village tags in large text and at night, and "closed" tags fixed. Minor remains (Goodaura tag cut off, name over ticket-office icon, meetup touch targets); phone performance, q3 and q4 still open.
- 25.11.2026: **Android redesign: `claude/android-redesign-root` merged to `main` after QA run 414 green (`a298968c`).** Fixes the reviewer's seven blockers, plus a Hebrew bug where `Modifier.offset` flipped pins (fixed with `atScreen`). Frame-count check in the film now starts after the mountain reloads.
- 25.11.2026: **Manager session: implementer found a real Hebrew bug; merge postponed by one run.** Compose's `Modifier.offset` flips the horizontal position in Hebrew, so seven elements (pins, lift tags, meetup touch areas) sat on the wrong side; fixed in `397897ca` (`absoluteOffset`) with an emulator check. Merge after the five parts are green.
- 25.11.2026: **Design reviewer: run 410 (`5ab8bfa2`), implementer fixes checked (section 8).** Opening view, sheet drag (h6), shelf framing (h5), trip form, season board and screen-reader mountain fixed; the film line is visible. Remaining: film screenshots by day, village tag overlap in large text and at night, and phone performance (h7).
- 25.11.2026: **Manager session: implementer fixed the blockers in `claude/android-redesign-root` (run 410, `5ab8bfa2`).** Film line, shelf framing, opening view, screen reader, sheet drag, q7 and q8; four parts green, `redesign2` failed the film 99th-percentile check (2050 against 2000 ms). Re-run on `1075f4fc` and a reviewer check on the way; phone performance remains.
- 25.11.2026: **Manager session: design reviewer finished the people screens (run 405, `cc2ccf69`; report `27d32068`).** Most screens match the boards; four blockers remain, and new findings q7 (mountain hidden behind sheets, day background at night) and q8 (transparent trip form). Sent to the implementer; Monday update 676972129.
- 25.11.2026: **Manager session: correction to the comprehensive pass report (reviewer, `67b60aba`).** Three blockers were emulator-build and script artifacts, not the build Pini installs; four blockers remain ("לרדת בסרט", framing, sheet drag, phone performance). Content session dropped the word "כפתור" from three screen-reader strings (`ef8adf6d`); three decisions for Pini still open.
- 25.11.2026: **Content: word "כפתור" dropped from three screen-reader strings.** `app.sr_pill`, `app.sr_me`, `app.sr_me_guest` in four languages, per the reviewer's finding k2 (the reader already says "button"); `check.py` green.
- 25.11.2026: **Manager session: comprehensive Android pass in the new design (reviewer, `design/redesign/review-full-android.md`, run 404).** Seven release blockers (old-page dead end from "ערב בבקתה", sheets over old home, POC tools in "אני", the film, framing, sheet drag, phone performance); the reviewer calls the 3D model the product's heart. Three decisions for Pini; sent to the implementer.
- 25.11.2026: **Design reviewer: run 405 (`cc2ccf69`), people screens, night and large text, added to the report (section 7).** Most work and match the boards; four blockers (h4 to h7) remain, new findings q7 and q8; "הימים שלי" and the village in large text and at night not photographed. No code changed.
- 25.11.2026: **Design reviewer: comprehensive pass over the whole Android app, like a new user (run 404, `7c44780`).** Report in `design/redesign/review-full-android.md`: seven blockers, important findings (opening view, screen-reader mountain, season-opening build without pistes), and a real phone not checked. No code changed.
- 25.11.2026: **Infrastructure: only the main activity (Pini), stage a, architect approved (d696a6ae).** `ios.yml` simulator screenshots are manual only, `ios-release.yml` requires a full green manual `ios.yml` run on the same commit, and `core` is allowed in `android-qa`. Remaining (`ops-core`): `main` and `all` run only `core`, after the implementer merges it and it is green once.
- 25.11.2026: **Infrastructure: fast main, architect approved (3c1ba7cd, 2e1d8b47).** On main the iPhone simulator (38 to 47 min) runs only when app files or packaged data change, else only `core` (which now also runs `build-ios-strings.py`); text-only pushes run `home` and `lang` in `android-qa`. `android-qa` main failures (15 of 17) are old-page scripts, a blocker for the implementer; details `docs/CI-AUDIT.md`.
- 25.11.2026: **Infrastructure: android-qa main failures classified; my retry bug fixed.** `games`, `phone`, `bigtext` and `run` fail because scripts expect the old home (owner: implementer, `app/android/qa/run.sh`); my retry hung 30 minutes on `adb wait-for-device`, now limited to at most three FAIL lines and 90 s. `redesign2` emulator dies after the "היום בדקה" video in three runs.
- 25.11.2026: **Infrastructure: `peak-check`, painted-mountain check (architect fix c).** Separate light workflow `.github/workflows/peak-check.yml` (about 15 s) runs `tools/build-peak.py --check` with `numpy==2.5.3`, no secrets or network, on contract, data and image changes. Green on the branch.
