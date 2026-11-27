# Pini ledger, 26-27 Nov 2026 (read only audit, `origin/main` 5647ab28)

Source: Pini's raw messages of 26-27.11 (146 chunks; relayed subagent reports, stop-hook notices and worker restarts were skipped as not Pini). Checked against `origin/main` (grep of code and docs) and the branches `claude/android-day-timeline-rest`, `my-days-2`, `hint-motion`, `runs-button-a`, `run-card`, `hints-kobi-fix`, `kobi-side` (merged). **Branch `claude/android-story-share` does not exist on origin.** Not run on a phone; "in main" means the code or doc is present in `origin/main`, not that it works.

Recorded = STATUS.md section P/J, a log line, DECISIONS.md or a handoff. Some messages are cut off in the raw file (L10 ends mid-sentence).

## Summary

Counts (74 rows): in main 30 (code or doc), on branch 6, designed only 7 (canvas, approved, not built), not started 3, answered/no build needed 21 (questions, status asks, process), dropped/closed 7.

**Pini asked, not recorded anywhere (NOT RECORDED):**
- L61: hints shorter than 13 s: "13 is too much, make it 9 seconds" (27.11 11:54). Code and STATUS P.18 still say 13 s; no 9 s anywhere.
- L69: "think how to map all work done or not done, and search today's and yesterday's conversations, not session summaries" (this ledger is the first answer).
- L66: check Sentry for the Kobi crash ("הנפילה בקובי"): no Sentry check recorded.
- L16: "what is the status of the old-vs-new version feedback of his wife": recorded in the UX pass, but her verdict (new design mature but unclear what does what) is only inside the UX00 board context.
- L37: junk "ימים שלי" (35 days) is recorded (P.23) as "leave"; the earlier question "request approval vs. no-descent rule" is recorded. OK.
- L10 part: "the store default will still be the picture; ski day must be in this release; promote all the version now for Android, Apple separate axis": the decisions are in log lines only as scattered text, no single decision row.
- L12: roll out to 100% not 20% (Google Play): not in PLAY.md or STATUS (grep "100%" found nothing).
- L13: "what are task house 9 and 10" and "send each small non-blocking open issue to its own session": process, only visible in session spawns.

**Approved by Pini but not built (in `main`):**
- Runs button A (L55, L65): branch `claude/android-runs-button-a`, not in main; shelf chips ordering and layers into the shelf (L47-L49, P.4/P.5): no code found in main or any branch (`layersOn` still in `LiveShelf`/`LiveMountain`).
- Run card RK (L51, L72): branch `claude/android-run-card` only.
- Day story DS and share sheet DV (saved state, progress, two tiles; L53, L72, L27): NOT built, no branch (the named `android-story-share` branch is missing); `DayStory.kt`/`DayShare.kt` in main have no `app_ds_*` strings and no saved state.
- DT2 scrolled strip, chart grid, break band, legend, two chapters (L73): branch `claude/android-day-timeline-rest` only.
- My days list MY and records RS (L56): branch `claude/android-my-days-2` only.
- Hint motion HM (L63): branch `claude/android-hint-motion` only (its Hint 13 s base).
- Hints shortened to 9 s (L61): not recorded, not built.
- Games table button hidden (L36): STATUS P.22 says hidden, but `MainActivity.kt:1023-1024` in main still passes a real `onTable` when a group exists, so the button is probably still visible and opens the old screen. Verify.
- Kobi side photo (L58, L62): the code is in main but the photo does not show on build 1057/1069; `claude/android-hints-kobi-fix` has 2 more commits (retries, first picture wait) not in main.

## Ledger

State legend: in main / branch X / designed only / not started / dropped / n.a. (question or process).

| id | time (UTC) | what Pini asked or decided | type | recorded in | build state | gap note |
|---|---|---|---|---|---|---|
| L01 | 26.11 06:21 | Prepare store release of new design: everything works, no missed screen, explanations for first-time users (tooltip over the record button never seen), little text, text only in a frame | request | STATUS log 26.11 (release-readiness, hints); `design/redesign/review-release.md`; `design/handoff/2026-11-26-redesign-hints/` | in main (hints sequence `HintSequence.kt`, B9/B10 `NameHold.kt`) | store upload gate P.14 still open |
| L02 | 26.11 06:27 | Hints for important functions and for hidden things (group, sign-in), designer's choice | request | hints handoff, STATUS log 26.11 | in main | |
| L03 | 26.11 06:28 | What is the foreground service declaration at Google? | question | n.a. (`docs/PLAY.md` section 5) | n.a. | declaration approved per architect (STATUS item 13) |
| L04 | 26.11 06:29 | Other phone permissions that could help the app? | question | n.a. | n.a. | |
| L05 | 26.11 11:56-57 | `/autocompact`, is it on for all sessions? | question | none | n.a. | |
| L06 | 26.11 15:03 | Open a new manager session to ask about status and store pre-conditions | request | none | n.a. | |
| L07 | 26.11 15:05 | Asked who did the comprehensive check yesterday and what came out | question | `design/redesign/review-full-android.md` | n.a. | |
| L08 | 26.11 15:08 | "Send everything to work", do the Fable designs on Opus, go to release with new design | decision | STATUS decision 87/log (Opus designer approved) | n.a. | |
| L09 | 26.11 15:14 | Default in store stays the picture (with a switch); ski day must be in this release; Google declarations: say what to do; advance Android now, Apple separate axis | decision | log lines (Stage), PLAY.md; no single decision row | in main (Stage, DAYREC) | text cut off in raw file |
| L10 | 26.11 15:19 | Asked for a concise list: what remains for the new-design release (already uploaded ski day version) | request | STATUS P.14, release review | n.a. | |
| L11 | 26.11 15:23 | Roll out to 100% not 20% (no users now); did not know "house task 9/10"; send each small non-blocking open issue to its own session | decision | NOT RECORDED (no "100%" in PLAY.md / STATUS) | n.a. | rollout percentage decision absent from repo |
| L12 | 26.11 15:33, 23:31 etc | "What is the status" (x8 on 26-27.11) | question | n.a. | n.a. | |
| L13 | 26.11 15:35 | Stop the manager session that keeps receiving notifications | request | none | n.a. | |
| L14 | 26.11 15:39 | Hints: a bit more text (village, trip); sequence one by one, tap = next, timeout ring around an X (5 to 10 s) | request | hints handoff v2, STATUS J.4 | in main (`HintSequence.kt`) | |
| L15 | 26.11 15:41 | Approves settings row "show again" and the "looking for location" bubble | approval | STATUS J.4 | in main (`app_hints_again`, `SearchPulse.kt`) | |
| L16 | 26.11 15:42 | Dislikes "outside it usually takes under a minute"; wants positive wording; approves same bubble | request/approval | log 26.11 content (`app.search_hint`) | in main | final wording "עוד רגע ונמצא אותך" per L25 |
| L17 | 26.11 15:43 | Approves buttons without words, long press shows the word | approval | B10 handoff, log 26.11 | in main (`NameHold.kt`) | |
| L18 | 26.11 15:44 | What else is needed from me (short list) | question | n.a. | n.a. | |
| L19 | 26.11 15:57 | Where is the search line? B9 pill grows to the row: approved but text invalid; asked what the two small hint items are | approval/bug | B9 handoff, log 26.11 | in main | |
| L20 | 26.11 15:58 | Disliked the three search wordings; wants natural Hebrew ("מתחברים ללוויינים" not natural) | rejection | log 26.11 content | in main | |
| L21 | 26.11 16:00 | Pre-season tile idea: a square with only the number that expands to show full text; designer to find the design | request | STATUS J.6, handoff hints section 6 | in main (top pill replaced the tile later) | |
| L22 | 26.11 16:00 / 22:25 | List of everything waiting for my approval (x2) | question | n.a. | n.a. | |
| L23 | 26.11 17:01 | Link to the latest install and the version number | request | none | n.a. | |
| L24 | 26.11 23:21 | Approves the hint sequence (wants more natural, useful texts), approves countdown tile, search = "עוד רגע ונמצא אותך", confirms phone check | approval | STATUS J.4, J.6, log 26.11 | in main | |
| L25 | 26.11 23:22 | Opus 5.5 may be used for texts | decision | log 26.11 content (Opus) | n.a. | |
| L26 | 26.11 23:44 | UX pass feedback: village as menu unclear; menu list does not look like a menu; my trip: editing not clear, tear not visible only sound; ski price card too much text, plain; food and drink opens wrong thing; "החבר'ה" wording; group list loading jumps; "ערב בבקתה" unclear; mountain picture pans up/down; far from mountain live model location goes crazy; tap on run zooms out | bug/request | STATUS J.7, `design/handoff/2026-11-26-redesign-ux-pass/`, decision row "menu instead of the village" | in main (parts 1-7, B1-B3) | not tested on a phone |
| L27 | 26.11 23:44 | Wife's feedback: new app looks mature but unclear what does what and where | request | UX00 board context | in main (UX pass) | not quoted in STATUS |
| L28 | 27.11 00:15 | "I approve all the recommendations" (UX pass) | approval | STATUS J.7 [x], DECISIONS | in main | |
| L29 | 27.11 00:20 | Make sure sessions can report to the manager (a Sonnet had no send tool) | request | none | n.a. | process, not recorded |
| L30 | 27.11 00:28, 00:31 | What waits for me; list of feedback and fixes in progress | question | STATUS section P | n.a. | |
| L31 | 27.11 05:20-05:40 | Status; no active session visible; why were items left out of the spec; are there more spec items not built, also from other sessions | question | `docs/handoff/2026-11-27-main-audit.md`, log 27.11 | n.a. | |
| L32 | 27.11 05:25 | Do the group loading skeleton | request | log 27.11 (GR3) | in main (`SkeletonBar` in `LivePeople.kt`) | |
| L33 | 27.11 05:26 | Then send a link to the new version with everything | request | none | n.a. | |
| L34 | 27.11 05:58 | Spec gaps: menu button needs no long press; do group tile, games tile, days tile, lifts tile, account in footer, large text, old village, trip title; my trip no baggage; tear last step (add if missing); "where is the ticket office" opens old village (fix); food pins, ticket office/parking, name tag, credit; group part 5 (pictures, load failure with copy); games part 6 | request | log 27.11 (UX-pass audit gaps built, `6ea049e1`), P.22 | in main | the long-press on the menu button was built then reverted (in main: `nameHold` only on the record button) |
| L35 | 27.11 05:58 | Do all of these through parallel agents, not by yourself | decision | none | n.a. | process |
| L36 | 27.11 06:13 | The ring around the mountains vanishes now and then in the live model | bug | P.1, commit `865ee175` | in main (`LiveRenderer.kt` RING_PITCH 1.1 / RING_EDGE 2500) | values were guesses, not tested on phone |
| L37 | 27.11 06:16 | Countdown tile corners not fully round (grey square behind) | bug | P.2 `a2d1d49f` | in main (shadow removed) | |
| L38 | 27.11 06:16 | Menu icon looks odd | bug | P.2 | in main (redrawn) | |
| L39 | 27.11 06:16 | "לאן יורדים?" looks like a text input, unclear that it opens a list; needs a strong model | request | P.3, J.8 | designed only: canvas DK, SV, SW, RN v101 | see L55, L66 |
| L40 | 27.11 06:17 | Shelf chips (by lift, easy to hard, longest) show no change when tapped | bug/request | P.4, J.9 | designed only (canvas SL/SV); shelf code `ShelfRules.kt` exists but the visible re-order/paint not built | not started in main |
| L41 | 27.11 06:18 | Layers button: unclear what it does; solve in UX or remove | request | P.5, J.9 | designed only: layers go into the shelf; `layersOn` still in main | |
| L42 | 27.11 06:18 | Layers button pushes the left button column up by ~210 px | bug | P.5 | not started (same as L41) | |
| L43 | 27.11 06:19 | Location banner: could a tap open the phone's permission dialog like Google? | question | P.7 | n.a. | closed: no new library (L66) |
| L44 | 27.11 06:29 | Likes the runs button direction (option A), wants more beautiful, experiential; layers not needed; likes the shelf, wants it more spectacular | decision | J.8, J.9, log 27.11 | designed only (v2 canvas 90-91) | |
| L45 | 27.11 06:32 | Mountain picture must fill the whole screen, never cut | bug/request | P.6, shelf-layers handoff top rule | designed only (rule written); builder must verify | not in main as a check |
| L46 | 27.11 06:37 | Run card: drop "(מפה רשמית)", nicer stats, clearer than "לרדת בסרט", "סרטונים · 1" must tell to drag up, dots swipe runs not videos: card should float with dots under | request | P.9, J.10, handoff `2026-11-27-redesign-run-card` | designed only; code on `claude/android-run-card` (1 commit), strings in main | |
| L47 | 27.11 06:38 | "Not on the mountain" screen too uninformative; say "you are not on the mountain; recording works only on the mountain or runs" | request | P.8, log 27.11 content | in main (`far_rec_sub`) | |
| L48 | 27.11 06:39 | My-day map: faint white mark where runs are | request | P.10 | dropped (see L70: rejected in canvas; keep today's faint lines) | |
| L49 | 27.11 06:40 | Day page undesigned: visual timeline with icons (coffee etc.); send to designer | request | P.11, J, handoff day-pages | in main (`DayTimeline.kt`, `DayRidge.kt`, P.11) | rest on branch, L73 |
| L50 | 27.11 06:41 | Story page unclear, not clickable ("steepest run" only) | bug | P.12 | designed only (DS approved L71); code not built, no branch | |
| L51 | 27.11 06:42 | Share page: tapping the play circle exits; video should play in place, taps must not dismiss | bug | P.13 | in main (`DayShare.kt` VideoView) | |
| L52 | 27.11 06:42-06:56 | "Go over everything I said today and record it in an organized way" (x2) | request | STATUS section P, Monday | in main (P list) | this ledger is the fuller check |
| L53 | 27.11 06:47 | Share page: save gives no feedback (video percent only, photo nothing) | bug | P.15, DV4-DV8 | designed only; NOT built | no `app_ds_saved`/saved state in main; branch missing |
| L54 | 27.11 06:49 | Record screen toggle all-day / one descent: unclear, changes nothing visible, live after start | bug/request | P.16, RM boards | in main (`DayStart.kt`, start_all_sub) | |
| L55 | 27.11 06:51-06:52 | Kobi side in picture mode: tapping the run only selects; wants a way to the other side of the ridge with high quality picture | request | P.17, KB boards, DECISIONS | in main (`PictureStage.kt` Face.KOBI, `KobiPictures.kt`); but photo not showing, fix on `claude/android-hints-kobi-fix` | |
| L56 | 27.11 06:52 | Must be able to return to the main ridge (button at top or compass) | request | P.17 | in main (compass returns) | |
| L57 | 27.11 06:53 | Hints end too fast | bug | P.18 | in main (13 s) but see L61 | |
| L58 | 27.11 06:54 | Transition between hints jumps; wants gentle motion for ring and bubble | request | P.19, HM boards | branch `claude/android-hint-motion` | |
| L59 | 27.11 06:55 | Ski pass sheet covers the pass | bug | P.20 | in main (`LiveSkipass.kt` scales) | |
| L60 | 27.11 06:56 | Link to latest version download | request | none | n.a. | |
| L61 | 27.11 06:59 | "Where is the ticket office" opens an unusable map: replace with Google Maps link (not Waze); add a round button for the SKIPASS app if relevant | decision | P.21, SK boards | in main (`OFFICE_NAV` in `LiveSkipass.kt`) | coordinate medium certainty, confirm in Jan |
| L62 | 27.11 07:02 | Games page: the Gudauri table button opens an old screen; hide for now, new records table later | bug | P.22 | claimed in main; **not confirmed**: `MainActivity.kt:1023-24` still passes `onTable` | verify on phone |
| L63 | 27.11 07:03 | My days saves every recording (35 fake days): do not save recordings without a real descent, show an informative message; rename "הימים שלי" | decision | P.23, `KeepRules.kt`, `EmptyEnd.kt` | in main (rule and end sheet); rename `app.menu_days` in strings, list page on branch | existing junk days left (Pini: later) |
| L64 | 27.11 07:04 | My days and my records screens should be more experiential | request | P.24, MY/RS boards | designed only; code on `claude/android-my-days-2` | |
| L65 | 27.11 07:07 | Top status pill bland: show how many lifts open of how many with lift icon, pre-season and in-season | request | P.25, TP boards | in main (`TopPill.kt`, `TopPillModel.kt`) | no live lift data until December (S-20) |
| L66 | 27.11 07:08 | Full status snapshot of all requests incl. rejects | request | log 27.11 (artifact checklist) | n.a. | |
| L67 | 27.11 07:09 | List is hard (mixed Hebrew/English); wants clearer, interactive checklist | request | artifact checklist | n.a. | |
| L68 | 27.11 07:54 | Runs button: loves chips and ordering; the button itself with many dots feels wrong, will not scale beyond 28 runs; wants clear, simple, experiential button; keep all chips and sorting | rejection/decision | P.3 | designed only | see L69 |
| L69 | 27.11 07:57 | Question: does "flight over the run" show movement on the run itself or only in the new slope strip? | question | RK handoff | n.a. | answer not recorded in repo |
| L70 | 27.11 08:02 | Faint lines: not drawn white; make today's faint run marks slightly more visible, like today | decision | STATUS log 27.11 (P.10 keep faint lines) | in main (no change); "slightly more visible" not built | a stronger line was not implemented |
| L71 | 27.11 08:02 | Day ridge design approved ("פגז") | approval | P.11, log | in main | |
| L72 | 27.11 08:04 | Day story: loved; story progress bars must load right-to-left in RTL (canvas loads left-to-right) | request | log 27.11 (DS progress follows layout direction) | designed only (canvas v99); build not done | |
| L73 | 27.11 08:06 | Share card video: four buttons not aligned (upper square-ish, lower round, different widths) | request | log 27.11 (DV one system 56/18/equal columns) | designed only | |
| L74 | 27.11 08:06-08:07 | Meant the Google Maps style permission popup (screenshot); what does it need; do not add yet; if a library is needed, leave as is | decision | P.7 closed | n.a. | no build, correct |
| L75 | 27.11 08:07 | Does the first record tap ask for location permission or send to settings? | question | none | n.a. | |
| L76 | 27.11 08:09-08:14 | Decisions: keep far-sheet button and wording as is; fix the share player (yes); approve the rest | decision | P.7, P.8 closed; P.13 | in main | |
| L77 | 27.11 08:15 | Updated interactive status page and download link | request | artifact checklist | n.a. | |
| L78 | 27.11 08:18 | Runs button v3 not pretty; closest was the mountain picture but feels cheap; wants a stronger model, several examples | request | P.3 v4 (RN, canvas 101), log | designed only | |
| L79 | 27.11 08:21-08:24 | Approves: my days and records canvas; top bar; ski pass cards and record-mode choice | approval | P.23-P.25, P.21, P.16 | my days/records on branch; top pill, ski pass, record mode in main | |
| L80 | 27.11 08:32 | Update the interactive page with anything changed | request | artifact | n.a. | |
| L81 | 27.11 08:42 | Approves smooth hint transition | approval | P.19 | branch `claude/android-hint-motion` | |
| L82 | 27.11 08:43 | Approves the move to the Kobi side | approval | P.17 | in main | |
| L83 | 27.11 08:45 | Kobi pictures as sharp as possible, tell if cost is heavy | decision | P.17, architect log (1.1 MB) | in main (set 1001 KB) | |
| L84 | 27.11 08:47 | Takes recommendation: option A (the window) for runs button | decision | log 27.11 | branch `claude/android-runs-button-a` | |
| L85 | 27.11 08:49 | Are you sure all fixes from several rounds, also yesterday's, are covered? | question | `docs/handoff/2026-11-27-main-audit.md` | n.a. | |
| L86 | 27.11 08:57 | Wanted canvas locations of pending items (run card, story, share, faint lines, bar/chips) | request | none | n.a. | |
| L87 | 27.11 09:08 | Approves run card, day story, share page in canvas; rejects white faint lines (not white, keep as today) | approval/rejection | P.9, P.12, P.15, P.10 | designed only except faint lines (no change) | |
| L88 | 27.11 09:08 | Why were DT2, graph grid, break band, legend and two-chapter split not built? | question | log 27.11 manager | n.a. | |
| L89 | 27.11 09:08 | Document everything of all rounds | request | STATUS | n.a. | |
| L90 | 27.11 09:17 | Build DT2, chart grid, break band and legend | request | log 27.11 | branch `claude/android-day-timeline-rest` | |
| L91 | 27.11 09:17 | Why was an approved item omitted? Are there other omissions without my approval? | question | main audit | n.a. | audit exists, ledger confirms more gaps |
| L92 | 27.11 09:42 | On build 1057: hints not 13 s, no runs button, story/share not updated, Kobi side not updated | bug | log 27.11 | see rows above | |
| L93 | 27.11 11:11 | Hints pass in 6 s; Kobi side shows only the run, not the picture; many things missing, maybe overwritten by parallel work | bug | log 27.11 (`hints-kobi-fix`) | wall-clock fix in main (`5647ab28`); Kobi fetch hardening: **on branch `claude/android-hints-kobi-fix`**, not in main | root cause of Kobi not proven |
| L94 | 27.11 11:39-11:51 | Status; when will there be a version | question | none | n.a. | |
| L95 | 27.11 11:54 | 13 s is too much, make it 9 seconds | request | NOT RECORDED | not started | code still 13000 (`HintSequence.kt:145`) |
| L96 | 27.11 11:54-11:56 | Should the Kobi picture exist now? (screenshots of Kobi and selected run card; shouldn't there be a new card?); now on build 1069 | question/bug | log 27.11 | n.a. | run card not in main (branch) |
| L97 | 27.11 11:58 | Check Sentry about the Kobi crash | request | NOT RECORDED | not started | |
| L98 | 27.11 12:00 | Almost all of today's fixes did not enter nor were documented, token waste; map all work done/not done; search today's and yesterday's conversations, not summaries | complaint/request | this ledger | n.a. | |

## Notes on state counts (rows above, rough)

in main: L01, L02, L14-L17, L19, L20, L24, L26, L28, L32, L34, L36-L38, L47, L49, L51, L54, L56, L57 (13 s), L59, L61, L63 (rule), L65, L76, L82, L83 = about 30.
On branch: L46, L55 fix (L93), L58/L81, L64, L84, L90 = 6.
Designed only: L39-L41, L44, L45, L50, L53, L72, L73, L78, L87 (some), = 7+.
Not started: L95 (9 s), L97 (Sentry), L69 answer.
Dropped/closed: L43, L48, L74, L76, L87 faint lines = 5-7.
