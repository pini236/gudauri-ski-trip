# Builder reports of 27.11.2026 (kept here so they survive context compaction)

Pini asked every builder for a full account. Each report below is condensed: built, not built or different, what to check on a phone, open decisions. Nothing here was tested on a phone; CI (`android`, unit tests and compile) is the only check unless stated. Current preview: build 1091 (`main` `2005fb48`, published 27.11 13:50 UTC; Pini's phone is still on 1069). The map of designs, decisions and builds is a separate task (`docs/handoff/2026-11-27-pini-ledger.md` and the interactive page).

## P.3 Runs button A "the window" (session_017QrNw9YBYpchuX77eTWJgd, in 1079)
- Built: `RunsButton.kt` (window picture in the hour's light, word, count, up chevron, press and narrow states), bar 64 high, the button's surface flies into the shelf title pill (open 420 ms, close 280 ms), shelf choreography (pill, chips, cards in order), reduced motion cross-fade 150 ms. `RunsButtonTest`.
- Different: no thumbs asset (the picture is cropped at runtime from the packaged hero pictures), so the architect's thumb conditions do not apply; a missing picture leaves an empty window (no fallback to option C). The word and count use the site key `resort.pass_runs` (content may want an `app.*` key).
- Not built by this builder: shelf v2 (chip painting, strip of all runs, big number, layers in the shelf) = P.4 and P.5. It was approved by Pini (the manager had wrongly listed it as waiting) and was built later by another builder, see the shelf section below. On close the shelf and cards vanish at once (no drop animation) and the bar buttons return without the 160 ms delay.
- Open: first-run hint 2 (`app.hint_find`) still talks about the "where to go" field (content task). Not checked: RTL alignment of the flight, tablet, large text, the 175 dp narrow threshold, night brightness.

## P.9 Run card (first part session_017QrNw72EgrTu34zZFDmwXj in 1079; rest session_017QrNw7gLPHLsFLw4ucLX1U in 1081)
- Built, first part: tag without "(מפה רשמית)", route line, slope-coloured profile with pin and ticks every 500 m, three numbers, film button "טיסה מעל המסלול", foot row with video frames and conditions. The low card has no close X (drag down only). `RunCardRules` and tests.
- Built, rest: floating card 18/44 from sides and bottom, radius 28; neighbours peek (10 visible, 8 away, 72 %); dots under the card (18 from the bottom, window of seven); sideways drag settles in 300 ms, mirrors in RTL; flight strip RK6 in `LiveRide.kt` (76 high, sign and name, 6 high progress bar with a 14 dot and scrubbing, 52 round stop). Stop or the end of the run restores the card; reduced motion only frames the run.
- Different: the speed button, the next-meeting line, the three live numbers and the left rail are gone from the film (RK6 has none) - Pini was asked whether to bring any back. The strip slides in 300 ms but the card leaves in 200 ms and returns in 250 ms instead of morphing. The neighbours are blank glass (content pops in when the swap lands). Dragging up still opens the card. The dot running down the profile during the flight does not exist (the profile is hidden then).
- Open: the profile's screen-reader sentence is numbers only (the spec sentence has no string; content). Check on a phone: drag feel, RTL direction of the neighbours, pull-down together with the sideways drag, the strip look, the camera flight.

## P.11 Day timeline "הרכס של היום" (first session_017QrNw84gJHQnuM1fPevJB1, rest session_017QrNw8D7Yx5B8wtpj7a6qU; rest in 1079)
- Built, first: four numbers, "לאן הלך היום" bar, rows on a spine with icons (lift, descent with speed strip, long break as cup, fork or bench by `places.json`, walk, queue), names wrap, large-text time column. `DayTimeline.kt` rules, 7 tests.
- Built, rest: scrolled ridge strip (appears when the ridge card is scrolled off, tap scrolls to that time), altitude grid 2,200/2,600/3,000 m, long-break band with bench and minutes, legend with break swatch, two chapters "בוקר"/"צהריים" (the third chapter removed). 4 new tests.
- Guesses to review: strip animation is a plain fade and 8 dp slide; the window end is the next row's time; the break icon may overlap the top-speed bubble; the halo is a text shadow; the break swatch shows only when a long break exists.
- Not done: descent-row accessibility label; the `redesign` QA step for the ridge; large text 1.3 and mirrored English rows not checked.

## P.12 and P.15 Story page, player and share sheet (session_017QrNw77f7FFWeQzGZAtg7a, in 1082)
- Built: `StoryRules.kt` and 10 tests; `DayStory.kt` page = head, "לצפות בסיפור", every story item a tappable glass card, "עוד מהיום" rows, end section with share and save; player with segments (follow layout direction), "i מתוך n", X, 6 s per moment, tap halves, hold, finale with share, save and the "סרטון של דקה" link. `DayShare.kt`: two tiles, two buttons, all four 56 high radius 18 border 1.5; progress inside the pressed button, "✓ נשמר בגלריה" for 4 s, failure "לא נשמר · שוב" with retry, no dialog.
- Not built (approved in DS1-DS5, DV4-DV8): camera glide between moments; pin label like "28°"; DS5 big single-moment layout and the "לאן הלך היום" block; the picture preview in the finale and the end section (150x188); in-card video controls (pause, play, replay circle, "לראות את היום על ההר" link; keys `app.ds_pause/play/again/see_mtn` unused); queueing the second button while one runs; tap on "saved" opens the gallery; haptic tick on saved.
- Decision: back-tap = the reading-start half (the spec says "inline-end goes back", judged a slip).
- Phone checks: player timing, hold and tap feel, RTL segment fill, large text, night, window crops, save to gallery and failure, share chooser.

## P.23 and P.24 My days and records (session_017QrNw8xyHfXbaYkyWDT8HD, in 1079)
- Built: `KeepRules.kt` (a recording is kept only if a descent segment has a drop of at least 20 m and a reading inside the resort; only a day that ended within 30 min is judged, old junk days are never touched); the end sheet NS variant "ההקלטה לא נשמרה"; `MyDaysRules.kt` and `MyDaysUi.kt`; `DayList.kt` (painted mountain, trip card with bars, one card per day with speed strip and tags, sample day, empty state); `RecordsScreen.kt` (headline, four tiles, ghost tiles when empty). Tests: `KeepRulesTest`, `MyDaysRulesTest`.
- Known gap: the delete happens when the end screen loads, not in `DayRecService.finish`; if the app is closed at stop, a junk day stays until the screen opens.
- Not built or different from the boards: km skiing is a trip-card number only (no tile); the old sixth history row `ski_dist_m` has no tile; the headline "מצב" tags and some board details were not seen (only `md_boards.py` read); the trip card bars have no day labels; framing and drawings unverified visually; only the NS icon was compared with the board; no QA step.

## P.21 Ski pass ticket-office buttons and P.16 record mode (session_017QrNw8xUH7NpTCSf3LK6CS, in main)
- Built: `LiveSkipass.kt` office row ends with two 40 dp round buttons (Google Maps walking to 42.470828,44.487555, medium certainty from OSM, confirm in January; the SKIPASS app `com.lemondo.skipass` or its Play page); "איפה הקופה?" removed. `DayStart.kt` two tiles (all day, one descent); `LiveDay.kt` the switch removed and a fixed label shown while recording.
- Needs a decision: the manifest got `<queries><package com.lemondo.skipass/></queries>` (needed on Android 11 and up); architect and Play to confirm.
- Not done: the label has no icon (spec 14); tile icons are the builder's own; the `onSwitch` parameter is still threaded (unused); leftovers (`app.skipass_office_where`, `foodFocus="tickets"`, `toggleQuiet()`).

## P.17 Kobi side in picture mode (session_017QrNw8bq4Be9d2C8ysZNyc, in main)
- Built: the seven Kobi pictures at 3 px/pt (1001 KB total, each up to 179 KB) in `site/img/peak/kobi/2f9091ad79/`; `KobiPictures.kt` (download to no-backup storage, sha256 check, resumable, foreground only); `PictureStage.kt` two faces and the 450 ms turn; "צד Kobi" tag and the compass that turns back; `tools/build-peak.py` limits 1.1 MB and 180 KB per file; `KobiSetTest`. `docs/PARITY.md` section 25 has the row.
- Open: quality q75-q80 per file, judged by PSNR only, not by eye; q80 everywhere needs a per-file ceiling of about 215 KB (architect, then Pini).
- Deviations: the compass floats at 510/844 when a run sheet is open; lens and focus lines are not drawn during the slide; zoom is not kept per face; the tag is hidden while a run is chosen or a sheet is open; going back lets go of a chosen Kobi run; no network-type limit, retry timer or telemetry for the download.
- Pini still sees no sharp picture and the cause is unknown (Sentry has no Kobi event); build 1079 and later show a small `kobi:` diagnostic line: waiting for his screenshot.

## P.25 Top pill (session_017QrNwA2kWwzvtqMVDcqxWn, in main, run 1041)
- Built: lifts ring (green open, amber wind only from a report `reason`), weather part with condition, temperature and one line; states TP1-TP7 in `TopPillModel.kt` with unit tests; the pre-season pill replaces the countdown tile (the once-a-day auto-open is gone); the QA step taps the pill.
- Different from the boards: (a) icons are the builder's own simple drawings, not the boards' artwork; (b) night is the clock rule 19:00-07:00, not real sunset; (c) closed lifts before 16:00 show "lifts" or "stopped by wind" instead of "opens tomorrow 09:00" (09:00 is a handoff constant, no data source); (d) no network and no report uses `app.now_offline` instead of `tp_no_data`; (e) today, tomorrow and date-passed states reuse `app.season_*`; (f) position not verified; (g) pre-season snow-ahead needs the forecast's daily snow.
- Needs: `android-qa` core, a phone look (sizes, pulse, long ru and ka strings), decisions on (c) and (d).

## P.19 Hint motion (session_017QrNw8gQPUzupPbTf6iEKX, in 1079)
- Built (`HintSequence.kt` only, no strings): the ring glides 360-560 ms by distance with the (0.2, 0, 0, 1) curve; the old 1.18 pop is replaced by one halo breath; the bubble fades out 120 ms, swaps at 55 % of the glide, fades in 220 ms with a 6 dp slide; taps locked until the new bubble starts; the auto-close timer (9 s since Pini's change) starts after the bubble is in. Reduced motion snaps and cross-fades.
- Not done: the dim fade-in 240 ms at start and the ring and dim fade-out 240 ms at the end; skipping a missing target before the glide (the 900 ms wait is unchanged); the bubble is not measured off screen.

## Content session (session_019k19PuDmyzecDEFv7DGcdx)
- Everything on `main`, `check.py` green (2137 strings, 0 errors); ru and ka have no native review (decision 39). Entered: hints, search line, the UX-pass set (~55 keys), the far-from-mountain wording, my days, ridge, story and share keys, the top pill keys, small items, the run-card keys (`app.run_ride` is now "טיסה מעל המסלול" and iOS reads the same key).
- Open for Pini or the owners: whether the site key `trip.title` follows the rename "הטיול שלי" (left "הטיול שלך"); gendered forms in non-`app.*` keys (tester consent, group); the name `app.me_title` ("אני"); old keys `day_n_descents`, `days_item`, `rec_day_line` still say "{n} ירידות" (use `md_runs` where n can be 1); plural keys need all forms read by the client.
- Content tasks that came out of the builder reports: update `app.hint_find` (the search pill is now the "מסלולים" button); add a screen-reader sentence for the run-card profile; maybe an `app.*` key for the runs button word.

## Open decisions collected from the reports
1. Flight strip: bring back the speed button, next-meeting line, live numbers or left rail? (asked, waiting)
2. Kobi pictures: q80 everywhere with a per-file ceiling of about 215 KB? (architect recommends, Pini decides)
3. Top pill: the closed-by-day second line and the offline wording (c, d above); the icon artwork.
4. Story back-tap: the builder's reading or the spec's literal "inline-end"?
5. Manifest `<queries>` for the SKIPASS app: architect confirmation.
6. Site `trip.title`, gendered keys, `app.me_title`: owners above.
7. Not-built approved parts, still open after the story part 2 and shelf v2 builds: live in-card preview before the encoder finishes (DV1), close choreography of the shelf and the runs button, hint dim fades and early skip, my-days tiles and tags, top-pill deviations (a-g above), Kobi leftovers (compass position, lens lines during the slide, zoom per face, telemetry). To be built in follow-up passes unless Pini drops them.
8. Hint 2 wording (`app.hint_find`, content proposal, waits for Pini with the shelf build): old he "כל המסלולים ברשימה אחת, מסודרים לפי צבע. נוגעים באחד והוא מסומן על ההר." New he "כל המסלולים כאן, לפי רכבל, קושי או אורך. נוגעים באחד והוא מסומן על ההר." New ka (corrected by content, includes "by lift"): "ყველა ტრასა აქ: საბაგიროს, სირთულის, სიგრძის მიხედვით. შეეხეთ და მთაზე მოინიშნება." (en/ru in the content session's message). Not entered in `i18n/strings.json` until Pini approves.

## P.4, P.5, P.6 Shelf v2 (session_017QrNw6HwU9N9gpr1yy4khY, merged `f1befb47`, in build 1091)
- Built: six chips that order and paint (lift, difficulty, six longest runs, sun, open, new snow; sun, open and snow use the lens code); strip of ticks with ring, pointer, tap and scrub with a haptic tick; cards 232x172 with the shape panel, big number that rolls 200 ms, caption and foot line; rank discs 1-6 on the mountain for "הארוכים"; layers button removed (the left column no longer jumps); the runs-button flight lands on the strip panel; picture mode raises by scaling about 1.14 so no pale band. `ShelfV2Test`; report `docs/handoff/2026-11-27-shelf-v2.md`.
- Empty answers: no sun or lift report makes the chip 40% and a tap shows a line for 2 s ("פתוחים" is dimmed until December data); no snow data stays selectable and paints nothing.
- Not built: the close choreography (the ticks falling back into the button; shelf and cards vanish at once). Old `Chip`, `LensChips`, `LensSheet` are dead code to delete; `app.shelf_easy_hard` unused.
- Guesses: picture-mode camera framing ignores the 1.14 scale (a framed run may sit a little low); the builder added `app.shelf_unit_cm` (content to confirm). Not checked: RTL tick order, 120 runs, large text, night colours, tablet.
- Phone checks: every chip tap, scrub, "הארוכים" discs, no pale band in picture mode, the button column does not jump.

## P.12 and P.15 Story and share part 2 (session_017QrNw8zSLvXvfUsWBFVG8Z, merged `7b50671d`, in build 1091)
- Built: camera glide 300 ms; pin label (steepest degrees, fastest km/h); DS5 one-moment page with "לאן הלך היום"; picture preview 150x188 in the end section and the finale; in-card video controls (pause/play, clock, progress line, replay, tap toggles, link "לראות את היום על ההר"); the second share or save queues behind the first; a tap on "saved" opens the gallery with a haptic. `StoryRulesTest`; report `docs/handoff/2026-11-27-story-share-2.md`.
- Not built: the live in-card preview before the encoder finishes (DV1). Back-tap direction unchanged (reading-start half).
- Guesses: quartic ease-out for the glide; pin shows only degrees or km/h; pause top-start, clock top-end, replay glyph; the finale picture is also 150x188 (DS3 says "big"); queued button has an accent border.
- Phone checks: glide and RTL, pin position, DS5 on a one-moment day, video controls, queue while encoding, gallery tap.
