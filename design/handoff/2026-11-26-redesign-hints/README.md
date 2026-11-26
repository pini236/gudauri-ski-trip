# First-run hints (Android redesign, for the store release)

**When:** 26.11.2026, product designer (Opus). Design only, no code.
**Status:** waiting for Pini. Built from `design/redesign/review-release.md` items B4 (hints), B8 (before the season), B9 (text without a frame) and B10 (buttons without words).
**Canvas:** https://claude.ai/artifact/8NrAKy97KWRj3JWcxx41yQ, page "רמזים לפעם הראשונה", boards `HN00` to `HN10`. Source: `design/redesign/tools/hn_boards.py`.
**Replaces:** section 7 of `design/handoff/2026-11-23-redesign-on-mountain/README.md` (S09, three hints). The bubble spec there stays, with the changes below.

## Summary (for the implementer)

1. One component (`HintBubble` + `HintRing`), one queue of six hints on the mountain, one hint inside the village sheet, reset from Settings.
2. At most two hints per opening; the second only after the user did something and came back to a quiet mountain. All six are seen in three openings.
3. A hint for something the user already used is marked seen silently.
4. Before the season the mountain opens free: the countdown is a small framed tile (B8), not the big number with a sheet.
5. "מחפש מיקום" on the search screen moves into a glass pill (B9).
6. Five new strings (with the content session); the tile uses the existing season strings.
7. Stored on the phone only (`redesign` prefs), nothing measured. No architect review needed (same terms as S09: no contract, no data, no measurement).
8. Extend `Hints.reset` and `qa.redesign hint:reset` to all ids.

## 1. The hints

| # | id (prefs key) | Points at | When | Text (key) |
|---|---|---|---|---|
| 1 | `hint_run` | a named blue run nearest the screen centre (ring on the line) | opening 1, first | `app.hint_run` (exists, built) |
| 2 | `hint_find` | "לאן יורדים?" in the bar | opening 1, after a run sheet was closed | `app.hint_find` (exists) |
| 3 | `hint_rec` | the record button (RX3) | opening 2, first | `app.hint_rec` **new**: "הקלטת גלישה: מהירות, ירידות ומסלולים." |
| 4 | `hint_village` | "הכפר" in the bar | opening 2, after an action | `app.hint_village` (exists) |
| 5 | `hint_mode` | the picture / live switch at the top of the button column | opening 3, first | `app.hint_mode` **new**: "תמונה של ההר, או הר חי שאפשר לסובב." |
| 6 | `hint_me` | the "אני" button | opening 3, after an action | `app.hint_me` **new**: "אני: חשבון, שפה והגדרות." |
| V | `hint_group` | the row "החבר׳ה" in the village sheet | the first time the village sheet opens, only with no group | `app.hint_group` **new**: "מזמינים חברים וקובעים איפה נפגשים." |

The queue order is 1 to 6. Hint V is outside the queue and its pacing.

## 2. When a hint appears

- **Opening** = a cold start, or coming back to the app after 30 minutes or more away.
- **Quiet mountain:** the mountain is drawn; the opening view (not the Kobi side); no sheet, dialog or permission sheet; not recording; not in "לרדת בסרט" or "היום בדקה"; the camera is still; no touch in the last 1,200 ms.
- **First hint of an opening:** the next unseen hint in the queue, 1,200 ms after the mountain is quiet.
- **Second hint of the same opening:** only after the user did something (chose a run and released it, closed any sheet, came back from the village or from "אני") and the mountain is quiet again for 1,200 ms. Never two in a row without an action between them. **At most two per opening.**
- **Already used = seen:** choosing a run (1), opening the shelf (2), tapping record (3), entering the village (4), changing the switch (5), opening "אני" (6), opening "החבר׳ה" (V) marks that hint seen, silently, whether or not it was shown.
- **Z03:** the existing pill "סיבוב והטיה זמינים רק במצב חי" (when someone tries to rotate the picture) also marks `hint_mode` seen, and the other way round.
- **Not shown** in an opening that started from a link (invite, meetup) or a notification (reminder, recording). The queue waits for the next opening.
- **Hint V:** 600 ms after the village sheet settles, the first time it opens, only if the user is not in any group. Shown once.

## 3. When a hint goes away

- The action itself (a tap on the target: it does what it always does), a tap on the bubble, any other touch on the screen, or 8 seconds.
- Any of these marks it seen forever (`Hints.done`).
- A sheet opening, recording starting or the app going to the background also closes it; it counts as seen.
- **Screen reader on:** no 8-second timeout. The bubble is announced once (polite live region) and has the action "סגירת הרמז" (`app.hint_close_a11y`). It goes away on that action or when the target is activated.

## 4. Reset

- **Settings,** a new row at the top of the last box, above "איפוס השיאים במשחקים": light-bulb icon, "להציג שוב את הרמזים" (`app.hints_again`, new), chevron (`HN9`).
- A tap clears all seven ids and the per-opening count, closes Settings and returns to the mountain; hint 1 appears 1,200 ms later. That return is the confirmation: no toast.
- Reinstalling resets. Changing the language does not.
- QA: `qa.redesign hint:reset` clears all seven ids (today only `hint_run`). The emulator keeps a hint up until its action, as today.

## 5. The component (one for all)

**Bubble** (as S09, with two changes):
- Radius 16, background `ink`, text `bg`, 14 / weight 500, line height 1.35, padding 10 and 14, shadow `0 8 22 rgba(10,25,45,.3)`.
- **Width: the text's own width, up to 266** (was 230). With large text: up to three lines.
- **Point:** a 12 square rotated 45°, radius 2, half outside the bubble, centred on the target's centre; at least 14 from the bubble's corner. 12 between the point's tip and the ring.
- At least 12 from every screen edge.

**Where the bubble goes:**
- Above the target (point down): the bar (2, 3, 4), the run (1), the village row (V).
- Below the target (point up): a target in the top quarter (6).
- Beside it, toward the screen centre (point sideways): a target in the button column (5).
- The bubble is anchored at the side its target is on (record: from the end edge; village: from the start edge), so it never hangs over the far side.

**Ring around the target (new for the controls):**
- 5 outside the control, the control's own shape: a circle (record 54, "אני" 54), a capsule (shelf 217 × 54, village 99 × 54, switch 56 × 100), a row with radius 16 (village row).
- Border 2.5 in `ink` (the bubble's colour, so bubble and ring read as one thing), with a 2.5 outer line: white by day, `rgba(6,11,22,.75)` at night; shadow `0 6 18 rgba(10,25,45,.25)`.
- On a run: a 46 circle on the line, the same border, filled white 30% (replaces the white-only ring of S09, which was not visible on snow).
- The ring does not take touches: a tap goes to the control under it.

**Motion:** the bubble fades in and rises 6 in 200 ms; the ring shrinks once from 1.18 to 1 in 600 ms. No repeating pulse. Out: 150 ms fade. Reduced motion: appear and disappear only.

**Left to right** (English, Russian, Georgian): everything mirrors, including the side the bubble takes next to the column (`HN8`).

## 6. Before the season (B8, interim until the "before the season" round)

- The mountain opens free, no sheet. The countdown is a **tile** (`HN1`): glass, radius 18, padding 8 / 14 / 8 / 12, on the start side, 12 from the edge, its top at 636 (above the bar and clear of the runs); the number 34 light, tabular; beside it `app.season_days_caption` (13.5 bold) and `app.season_tomorrow_sub` with the date (12, `ink2`). The other states (tomorrow, today, late, moved) put the existing words in the same tile, up to two lines, max width 220.
- **Tap on the tile:** opens the existing season sheet (planned date, your trip, ski pass) **without** the big number; pulling it down closes it; the tile stays. Screen reader: `app.season_days_a11y`.
- The pill stays "פתיחה מתוכננת {date} · לפי MTA".
- Hint 1 appears as on any day.
- This removes the big number and its haloed caption from the opening (`LiveSeason.kt`), and with them the bug where the first tap on the mountain went to the count.

## 7. Text without a frame (B9)

- **The rule:** every text on the mountain sits in a frame: a pill, a tile, a sheet or the hint bubble. Text straight on the background is allowed only as the title of a whole page whose background is veiled (Settings, About, the ski pass page).
- **The search screen** (`SearchPulse.kt`): "מחפש מיקום" moves into a glass pill, 40 high, padding 0 18, 16 bold, centred 132 under the pulse's centre (`HN10`). Everything else as GS1.
- The countdown: solved by the tile (section 6).
- After 30 s the pill grows into one frame with `app.search_hint` (B9 designer, boards `B91` to `B93`, `design/handoff/2026-11-26-redesign-b9/`); build it together with `HN10`.

## 8. Buttons without words (B10)

Solved by hints 3, 5 and 6. The buttons stay without words.

## 9. Strings (sent to the content session)

| Key | Hebrew | English |
|---|---|---|
| `app.hint_rec` | הקלטת גלישה: מהירות, ירידות ומסלולים. | Record skiing: speed, descents and runs. |
| `app.hint_mode` | תמונה של ההר, או הר חי שאפשר לסובב. | A photo of the mountain, or a live one you can turn. |
| `app.hint_me` | אני: חשבון, שפה והגדרות. | Me: account, language and settings. |
| `app.hint_group` | מזמינים חברים וקובעים איפה נפגשים. | Invite friends and set where to meet. |
| `app.hints_again` | להציג שוב את הרמזים | Show the hints again |

Existing and unchanged: `app.hint_run`, `app.hint_find`, `app.hint_village`, `app.hint_close_a11y`, the `app.season_*` keys.

## 10. Boards

`HN00` the system · `HN1` opening 1, the run, and the countdown tile · `HN2` the shelf · `HN3` record · `HN4` the village · `HN5` the switch, at night · `HN6` "אני" · `HN7` the village sheet, "החבר׳ה" · `HN8` English, record · `HN9` Settings, the reset row · `HN10` the search pill.

## 11. Honestly

- Measured in the design tool against RX1, OP1 and K17, not on a phone or the emulator. Positions are for 390 × 844; in code they follow the real controls.
- The village sheet row positions are from K17; the row "החבר׳ה" in the board reads "הזמנה ומפגש" as an example.
- iOS: the same system when the redesign reaches it.
- Open for Pini (in `HN00`): the seven hints and the order; the tile opens the season sheet (or no action); "מחפש מיקום" in a pill, although GS1 was approved without one.
