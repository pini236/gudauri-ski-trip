# The day pages: white runs, the ridge, the story, the video in its card

**When:** 27.11.2026, product designer (Opus 5.5). Design only, no code.
**Status:** waiting for Pini (rule 7). Five questions in board `DP00`.
**Canvas:** https://claude.ai/artifact/8NrAKy97KWRj3JWcxx41yQ (version 94), page "עמודי היום: המפה, הרכס, הסיפור והשיתוף (27.11)", boards `DP00`, `DM1`-`DM3`, `DT1`-`DT5`, `DS1`-`DS5`, `DV1`-`DV8`. Source: `design/redesign/tools/dp_boards.py`.
**Data:** the sample day (`tools/fixtures/dayrec-logs/full_day.jsonl`, 11.1.2027) and the engine's real output (`dayrec-cases.json` `full_day`, `dayrec-moments.json` `full_day`). Wording: the approved `moment.*` and existing `app.*` keys; new strings listed at the end.
**Rule kept:** the mountain picture fills the screen under every page and sheet (v91 rule, `design/handoff/2026-11-27-redesign-shelf-layers/README.md`). The ridge and story pages sit over the day-painted mountain, veiled to `bg` at 86% (84% at night).

## What I found (before designing)

| # | Pini saw | Cause |
|---|---|---|
| 1 | "היום שלי": unclear where the runs are | `DayMountain.showDay` hides every run and lift while the day is drawn. The 32% coloured runs exist only in old boards (`gap_boards.py`), never built |
| 2 | "הרכס של היום": a chart and a text list, "Goodaur" cut | not designed beyond `L07`; the analyzer has more than the screen shows |
| 3 | "הסיפור של היום": one card, not tappable, empty below | **not the data.** For the sample day the engine gives 11 moments and puts 4 in `story` (`first_color` black, `steepest`, `fastest`, `tomorrow`); every test day with a descent gives 3 or 4. The screen shows fewer: a build issue. Check on the emulator with the log line `redesign day story N` (`DayStory.kt`) and `MomentText.plan` for the dropped keys. Also: `DayStory` filters `fall` and `offpiste` out of `story` and never backfills, which can leave holes |
| 4 | "תמונה לשיתוף": the play circle leaves the page | **a bug.** The circle is only drawn; the preview has no click handler, so the tap falls through to the screen's dismiss layer (`DayShare.kt`, the outer `Box(...).clickable { onBack() }`). There is also no player: the mp4 is encoded only on share or save |

## 1. "היום שלי": the resort's runs in faint white (`DM1`-`DM3`)

- **All runs** of the resort under the day's line, **white, no colours, no names**.
  - Named runs: width 1.7 (x the view's zoom factor), day opacity .92 over a hairline shadow `#1D3556` at .16, width 3.4 (without it white vanishes on the day's snow); night opacity .42, no shadow.
  - Unnamed pieces and connecting ways (`kind: ski-way`): dashed `1 5`, width 1.3, day .5 (shadow .08), night .26.
- The day's line keeps its own case and speed colours on top (width 3.2). At night the fastest band is near-white; the day line stays readable because it is twice as wide and cased. Checked by eye on both pictures.
- **Lifts:** only the ones ridden today, dotted (as today). No other lifts.
- **Labels:** none. A tap on a white line shows its name in a small glass tag with a stem for 3 s (`DM3`); a second tap elsewhere hides it. No new string (the run's name).
- Same in picture mode and on the live model; same when zoomed (`DM3`).

## 2. "הרכס של היום": a visual timeline (`DT1`-`DT5`)

Top to bottom, one scrolling page under the pill (back, "הרכס של היום · date", "דוגמה" on the sample):

1. **Four numbers** (26 / 300, caption 12 `ink2`): runs, km skied, m down, hours on the mountain (`end - start`).
2. **The ridge** (glass card, chart 362 x 150, time left to right in every language, as `L07`): the area of altitude over time; descents in the speed ramp, lifts dotted `ink2`, other time thin. Grid at 2,200 / 2,600 / 3,000 m with labels (11, `ink2`, halo). Hours under it. The long break as a band (`rgba(242,193,61,.18)`) with a bench icon and its minutes on top. The day's top speed as a dot and a bubble `12:18 · 68.9`. Legend: slow-fast ramp, "· · · רכבל", the break swatch.
3. **"לאן הלך היום"**: one bar, four parts by minutes: skiing (`ski_s`, the speed gradient), lifts (`lift_s`, `ink2`), queues (sum of `stop.kind=queue`, `#E2B13C`), breaks and walking (the rest of `end - start`, `line`). Legend with the minutes.
4. **The timeline.** Section heads "בוקר" (before 12:00) and "צהריים" (from 12:00). Each row: time (42, tabular, LTR) | node (38 column with the spine) | content.
   - **Lift** (`up`): node 34 with the lift's kind icon (gondola, chair, drag), spine dotted. Name 15.5 / 700 (LTR isolate); meta "גונדולה · 6:35 דק׳ · 544 מ׳ למעלה". **The queue before it** (`stop.kind=queue` just before, from 2 min) is a chip in the meta, hourglass + "תור 3 דק׳" (`app.day_queue`); the row's time is the queue's start.
   - **Descent** (`dn`): node filled `acc` with a down arrow, spine a lilac-to-pink gradient. A glass card: the run signs and names of `main(descent)` with "›" between, 16 / 700, **wrapping, never ellipsized**; the descent's speed strip (10 high, 48 bins of mean speed in the ramp, a ringed dot at its top speed); meta "2.9 ק״מ · 546 מ׳ למטה · מרבית 68 קמ״ש"; chips: "עצירה אחת" / "{n} עצירות" (pause icon), "חלק מחוץ למסלולים" (tree), "דקה בלי קליטה" (no-signal) when a gap falls inside it.
   - **Long break** (`stop.kind=long`): node 38 in amber; an amber card "הפסקה ארוכה · 35 דק׳" (`app.day_lunch`) and "11:04–11:39 · {where}".
   - **Walk** over 100 m: small node, "הליכה, 360 מ׳" (`app.tl_walk`).
   - Not shown as rows: short `rest` stops (about a minute at the top), the fall (it lives in the story, as today).
5. **Scrolled** (`DT2`, `DT3`): the ridge card collapses into a 52 high glass strip under the pill: the whole day's ridge with a window (`acc` outline) over the rows on screen. A tap on the strip scrolls to that time.

**Icons and the café rule** (`DT5`): the analyzer knows queue, lift, descent, stops inside a descent, off-piste, no signal, walk and long break. It does not know what you did in a break. So:
- A long break within `places.json` `maxDistance` (250 m) of a place of kind `cafe` with a name: a **cup** icon and the place's name.
- Of kind `food`: a **fork and knife** and the name.
- Otherwise: a **bench** icon and "בראש {lift}" when it follows a lift (the sample day: "בראש Kobi"; no named café or restaurant within 250 m of it).
- No other stop types are invented.

**Large text** (`DT3`, font scale 1.3): every size scales; names and meta wrap; the time column widens to 55; the ridge strip keeps its height. **English** (`DT4`): mirrored rows, the chart stays left to right.

## 3. "הסיפור של היום" (`DS1`-`DS5`)

- **The page** (`DS1`): the head (theme title 27 / 700, `moment.story.sub`), a primary **"לצפות בסיפור"** (play), then **every `story` item as a card** (glass, radius 20, a 76 window onto the mountain at the moment's place, or the number, or the run's shape; title 16.5 / 700, line 13.5 `ink2`; a chevron: it opens). Then **"עוד מהיום"**: the engine's other items as rows (icon tile 40, title, line, chevron), in the day's order: for the sample `longest_descent`, the second `first_color` (red), `drop_compare`, `time_split`, `queue`. `fall` and `offpiste` stay out, as today.
- **No holes:** when a `story` item is dropped (fall, off-piste, or a key without words), the next item from "עוד מהיום" takes its place, so the cards are never fewer than the items the engine gave.
- **The end of the page** (`DS4`): "התמונה של היום": the share picture (150 x 188) with "שיתוף" and "שמירה" beside it.
- **The player** (`DS2`): "לצפות בסיפור" or a card opens it full screen at that card. Segments at the top (one per story card, plus the finale), "הסיפור של היום · 2 מתוך 5", an X. Each moment: the mountain close on its place (the white runs, the day faint, the moment's descent lit, a pin like "28°" where the moment is), a glass card at the bottom (the kind 12.5 `ink2`, the run's big sign and name, the number 44 / 300, the line). 6 s per moment; a tap on the inline-end half goes back, on the rest forward; a press holds. Each moment's camera moves from the last one's (300 ms).
- **The finale** (`DS3`): "התמונה של היום", the share picture big, "שיתוף" and "שמירה", and "סרטון של דקה" as a link to the share page in video mode.
- **One moment** (`DS5`): the single card is big (the window 362 x 210 over the card's words), then "לאן הלך היום" for that day, then the picture. Illustrative: in the engine's test days a day with a descent always gives 3 or 4.
- **No moments:** happens only on a day without descents; that day already ends on the approved empty end screen (`EM`), with no story button. Not drawn again.

## 4. The one-minute video plays in its card, and the share sheet (`DV1`-`DV8`)

A bug fix with a small spec; it can be built before the rest:
- **The card plays in place.** The preview shows the same frames as the file (`StoryVideo` drawing the film into the card), so it plays at once, without waiting for the encoder.
- While playing: a 44 pause button in the card's corner (glass dark), "0:25 / 1:00" (LTR) in the other corner, a 4 px progress line along the card's bottom edge (white over 28% white, filling in the reading direction). A tap anywhere on the card toggles play and pause.
- At the end: the last frame stays, a 72 "שוב" circle in the middle; a tap replays.
- **"שיתוף" and "שמירה" stay enabled while it plays and do not stop it.** The encoder runs in the background; the pressed button fills and reads "מכינים · 64%"; at 100% the system sheet opens (share) or the file is saved.
- **Nothing on the card leaves the page.** The replay on the mountain ("היום בדקה") is an explicit link under the card: mountain icon + "לראות את היום על ההר".
- The outer dismiss layer stays only outside the card and the sheet.

**The sheet** (Pini, P.15; `DV4`-`DV8`), for picture and video alike:
- **No explanatory sentence** ("בלי שם ובלי נפילות...", `app.share_note`, leaves the sheet; the privacy line stays in the policy).
- **What to send:** two tiles instead of the toggle pill, 56 high, radius 18: a picture icon + "תמונה", a play icon + "סרטון של דקה". The chosen tile: `acc` border 1.5 plus an inset 1, a 10% `acc` wash, the icon in `acc`.
- **Two buttons**, 56 high, the primary a bit wider (1.25 : 1): "שיתוף" (share icon, `acc`) and "שמירה" (download icon, outline).
- **Progress lives in the pressed button:** it fills from its start side (white 26% on the primary, `acc` 16% on the outline), the verb changes ("מכינים" for share, "שומרים" for save) and the percent sits small after it. Never a bare number elsewhere. A picture is quick: the fill may only flash.
- **Saved:** the save button turns `good` with a check and "נשמר בגלריה" for 4 s; a tap in that time opens the file in the gallery; then it returns to "שמירה". Haptic tick.
- **Share done:** the system sheet opens by itself when the file is ready; back from it, the button is "שיתוף" again.
- **Failed:** the pressed button turns to a `warn` outline with a retry icon and "לא נשמר · שוב" (for share: "לא הוכן · שוב"); a tap retries. No dialog.
- Both actions keep the video playing; while one runs, the other button stays enabled and queues after it.

## Strings (to the content session after approval)

- **Existing, reused:** the `moment.*` story texts; `app.day_queue`, `app.day_lunch`, `app.tl_one_stop`, `app.day_off`, `app.rec_gap_title`, `app.tl_walk`, `app.speed_slow` / `app.speed_fast`, `app.share_video`, `app.share_save`, `app.day_share`, `moment.time_split.title`.
- **New:** "בוקר", "צהריים"; "בראש {lift}"; "{n} עצירות" (plural); "שעות על ההר", "ק״מ גלישה"; "גלישה" / "ברכבלים" / "בתורים" / "הפסקות והליכה" (with minutes); "לצפות בסיפור", "עוד מהיום", "התמונה של היום", "{i} מתוך {n}"; "לראות את היום על ההר", "מכינים", "שומרים", "נשמר בגלריה", "לא נשמר · שוב", "לא הוכן · שוב", screen reader "השהיה", "הפעלה", "שוב". Four languages. **Dropped from this sheet:** `app.share_note`.

## Build notes

- Android files: `DayMountain.kt` / the live renderer's `showDay` (white runs instead of hiding), `DayRidge.kt`, `DayStory.kt`, `DayShare.kt`. iOS follows the same boards.
- Screen reader: a timeline row reads as one item ("09:40, ירידה, Goodaura 1, 2.9 ק״מ, 546 מ׳ למטה, עצירה אחת"); the ridge chart is one item with the day's range and top speed; the video card says playing or paused and its position.
- Sample day: the page shows "דוגמה" as today; nothing is counted.

## Honestly

Every number, time, name, moment and wording is from the sample day and the analyzer's and engine's real output. Illustrative and marked so: `DT5` (sample rows, including `Ice Bar` and `Gradus` to show the café rule) and `DS5` (a one-moment day). Drawn in the design tool, not on a phone; motion is specified, not drawn. No contract or data change.

## Fixes after Pini's look (27.11.2026, canvas 99)

- **The day timeline `DT1`-`DT5`: approved** (Pini: "פגז, אני מאשר"). Strings go to content now.
- **The day map `DM`: no white lines.** Keep today's faint run lines in each run's colour (today 32%), only a small step stronger: 45% by day, 40% at night, 1.2 times the width; unnamed pieces and connecting ways dashed at 60% of that. They stay tone-on-snow. `DM1` day, `DM2` night, `DM3` today for comparison. The same lines are used in every mountain window (story, my days, records).
- **The story progress bars `DS`:** they follow the layout direction, never a fixed left-to-right. Hebrew: the first segment on the right, each bar fills right to left; English, Russian, Georgian: the reverse. Build with the layout direction (`inset-inline-start`, `LayoutDirection` in Compose), not a hardcoded start.
- **The share sheet `DV`: one button system.** All four buttons (the two tiles and share/save) are 56 high, radius 18, border 1.5 (transparent on the filled ones), in a two-column grid of equal width with gap 8, so the edges line up.
