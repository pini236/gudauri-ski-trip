# First-run hints (Android redesign, for the store release)

**When:** 26.11.2026, product designer (Opus). Design only, no code.
**Status:** version 2 after Pini's notes (26.11.2026): a guided sequence and longer, clearer texts. **Approved in full (Pini, 26.11.2026): the sequence (canvas 79) and the countdown tile (canvas 80, all three questions yes). Build now.** Wording follows `i18n/strings.json` (content is polishing; the boards read it). Built from `design/redesign/review-release.md` items B4 (hints), B8 (before the season), B9 (text without a frame) and B10 (buttons without words).
**Canvas:** https://claude.ai/artifact/8NrAKy97KWRj3JWcxx41yQ (version 82), page "רמזים לפעם הראשונה", boards `HN00` to `HN12`. Source: `design/redesign/tools/hn_boards.py`.
**Replaces:** section 7 of `design/handoff/2026-11-23-redesign-on-mountain/README.md` (S09, three hints).

## Summary (for the implementer)

1. One guided sequence of six hints on the mountain, once, at the first quiet opening; a sequence of two in the village sheet, the first time it opens.
2. A tap anywhere moves to the next hint (the target too, without its action). Drags and pinches do nothing while it runs.
3. A light dim with a hole around the target; the hole is the target ring.
4. Each hint: an X inside a ring that drains over 8 s; at the end, the next hint. The X closes the whole sequence.
5. Dots at the bottom of the bubble: how many hints and which one.
6. Interrupted: resumes at the same hint next opening. Closed or finished: never again, unless "הצגה מחדש של הרמזים" in Settings.
7. Before the season the countdown is a framed tile (B8); "מחפש מיקום" in a pill (B9).
8. Stored on the phone only, nothing measured. No architect review needed (no contract, data or measurement).

## 1. The hints

Mountain sequence, in this order (prefs `hints_mountain` = the next step, 1 to 6, or `done`):

| # | Points at | Key | Text (Hebrew) |
|---|---|---|---|
| 1 | a named blue run nearest the screen centre | `app.hint_run` | נוגעים במסלול כדי לראות אותו מלמעלה למטה, עם האורך, הירידה והשיפוע. |
| 2 | "לאן יורדים?" | `app.hint_find` | כל המסלולים ברשימה אחת, לפי צבע. נגיעה במסלול מראה אותו על ההר. |
| 3 | the record button (RX3) | `app.hint_rec` | מקליטים את הגלישה עם הטלפון בכיס, ורואים מהירות, ירידות ומסלולים לאורך היום. |
| 4 | "הכפר" | `app.hint_village` | בכפר נמצאים הטיול ותאריכי הטיסה, החבר׳ה ונקודת המפגש, כרטיס הסקי ואוכל. |
| 5 | the picture / live switch | `app.hint_mode` | אפשר לראות את ההר בתמונה, או להסתובב בו ולהטות אותו באצבע. מחליפים כאן. |
| 6 | "אני" | `app.hint_me` | כאן החשבון, השפה וההגדרות. כניסה לחשבון שומרת את הקבוצות שלך גם בטלפון חדש. |

Village sequence (prefs `hints_village`):

| # | Points at | Key | Text (Hebrew) |
|---|---|---|---|
| 1 | the row "הטיול שלך" | `app.hint_trip` (new) | מכניסים את תאריכי הטיסה, ורואים כמה ימים נשארו ומה התחזית לימי הסקי. |
| 2 | the row "החבר׳ה" | `app.hint_group` | מזמינים חברים בקישור, קובעים נקודת מפגש על ההר, ורואים מי על איזו טיסה. |

## 2. When the sequence starts

- **Mountain:** at the first opening, 1,200 ms after the mountain is quiet (drawn, opening view, no sheet or dialog, not recording, camera still, no touch in the last 1,200 ms). No "two per opening" limit any more.
- **Village:** the first time the village sheet opens, 600 ms after it settles. Independent of the mountain sequence.
- **Not started** in an opening that began from a link (invite, meetup) or a notification; it waits for the next opening.
- Users updating from the old design get it too (new UI).

## 3. While it runs

- **Tap anywhere** (on the dim, on the target, on the bubble's text): the next hint. The tap is consumed: no run is chosen, no sheet opens.
- **Drag, pinch, back gesture:** nothing, except system back, which closes the sequence like the X.
- **8 s** per hint (see accessibility); at the end, the next hint. After the last one, the sequence ends.
- **X:** closes the whole sequence; marked `done`.
- **Interruption** (the app goes to the background, a notification, a recording started from the lock screen): it pauses, and resumes at the same hint at the next quiet opening.
- **Pre-season:** the same; the countdown tile stays under the dim.

## 4. Reset

- **Settings,** row "הצגה מחדש של הרמזים" (`app.hints_again`), light-bulb icon, above "איפוס השיאים במשחקים" (`HN9`).
- A tap clears both sequences, closes Settings, returns to the mountain, and the mountain sequence starts from 1 after 1,200 ms. The village sequence comes back at the next village opening.
- Reinstalling resets; changing the language does not.
- QA: `qa.redesign hint:reset` clears both; the emulator build keeps each hint until a tap (no timer), as today.

## 5. The component

**Bubble:** radius 16, background `ink`, text `bg` 14 / weight 500, line height 1.4; width of its content up to 300, up to three lines; padding 10, 14 on the start side and 8 on the X side; gap 8 between the text row and the dots; shadow `0 8 22 rgba(10,25,45,.3)`.

**X in a ring** (end side of the first line): 32 in sight, 44 to touch. Ring radius 14, stroke 2: the track at 22% of `bg`, the arc in `bg`, starting at the top, draining clockwise over the hint's time. X of 12, stroke 1.8.

**Dots:** 6 × 6, radius 3, gap 5, `bg` at 32%; past ones 60%; the current one 16 wide at 100%. In left-to-right languages they run left to right.

**Point:** a 12 square rotated 45°, radius 2, centred on the target, at least 14 from a bubble corner; 12 from the point's tip to the target ring. The bubble at least 12 from every screen edge. Above the target; below it in the top quarter; beside it, toward the centre, for the button column.

**Target ring and dim:** 5 outside the control in its own shape (circle, capsule, row with radius 16); border 2.5 `ink`, outer line 2.5 white (night `rgba(6,11,22,.75)`). The ring's outside is the dim: `rgba(8,16,30,.26)` by day, `rgba(0,0,0,.34)` at night. On a run: a 46 circle with a 30% white fill.

**Motion:** between hints the dim's hole moves to the new target in 300 ms (ease out) and the bubble fades out and in (150 / 200 ms, rising 6). The ring shrinks once from 1.18 on appearing. No repeating pulse.

**Accessibility:**
- Screen reader: no timer (the ring stays full). Focus moves to the bubble; it reads "רמז {n} מתוך {total}" (`app.hint_step_a11y`) and then the text. Two actions: "לרמז הבא" (`app.hint_next_a11y`) and "סגירת כל הרמזים" (`app.hints_skip_a11y`).
- Time: 8 s, or longer by `AccessibilityManager.getRecommendedTimeoutMillis(8000, FLAG_CONTENT_TEXT | FLAG_CONTENT_CONTROLS)`.
- Reduced motion: no rise and no hole movement (it jumps); the ring drains in one-second steps.

## 6. Before the season (B8, version 2 after Pini, 26.11.2026; interim until the "before the season" round)

Boards `CT00` to `CT5` (canvas version 80). **Approved (Pini, 26.11.2026): the tile, opening by itself once a day, and the tap on the open tile opening the season sheet.**

- **The mountain opens free, no sheet.** The countdown is a **square tile**, 64 × 64, glass, radius 18, padding 8, on the start side, 12 from the edge and 18 above the bar (top 690 on a 844 screen); clear of the runs.
- **Inside: only the number, in a ring.** Ring 48, stroke 3: the track in `line`, the fill in `acc`, from the top, clockwise. The fill is how much of the wait has passed, from MTA's announcement to the opening (`season.announced` to `season.open` in `site/data/resorts.json`; today 55%). Number 20 / weight 600, tabular; 17 from 100 days.
- **Tap on the closed tile:** it opens toward the screen centre in 250 ms (ease out): the ring, then `app.season_days_caption` (14 bold) over `app.season_tomorrow_sub` with the date (12, `ink2`), and a chevron 18 at the end. It stays 3.5 s (or `getRecommendedTimeoutMillis(3500, FLAG_CONTENT_TEXT)`) and closes in 250 ms.
- **Tap on the open tile:** the existing season sheet (planned date, weekday and source, your trip, ski pass), **without** the big number (`CT3`). Pulling it down closes it; the tile stays.
- **Opens by itself** once at the first opening of each day until the opening, for the same 3.5 s. On the very first opening the hint sequence starts after it closes.
- **States:** tomorrow, `1` in the ring and `app.season_tomorrow`; the day itself, a full ring with a gondola icon instead of a number and `app.season_today`; moved, the number to the new date and `app.season_moved`; date passed with no report, a full ring in `ink2` with a clock icon and `app.season_late`.
- **Screen reader:** one button, `app.season_days_a11y`; activating it opens the season sheet directly (no expanding).
- **Reduced motion:** no widening; the caption fades in and out.
- **Left to right:** the tile sits on the left, opens to the right, chevron points right (`CT5`).
- No new strings. The pill stays "פתיחה מתוכננת {date} · לפי MTA".
- This removes the big number and its haloed caption from the opening (`LiveSeason.kt`), and with them the bug where the first tap on the mountain went to the count.

## 7. Text without a frame (B9)

- **The rule:** every text on the mountain sits in a frame: a pill, a tile, a sheet or the hint bubble. Text straight on the background is allowed only as the title of a whole page whose background is veiled (Settings, About, the ski pass page).
- **The search screen** (`SearchPulse.kt`): "מחפש מיקום" moves into a glass pill, 40 high, padding 0 18, 16 bold, centred 132 under the pulse's centre (`HN10`). Everything else as GS1.
- The countdown: solved by the tile (section 6).
- After 30 s the pill grows into one frame with `app.search_hint` (B9 designer, boards `B91` to `B93`, `design/handoff/2026-11-26-redesign-b9/`); build it together with `HN10`.

## 8. Buttons without words (B10)

Solved by hints 3, 5 and 6. The buttons stay without words.

## 9. Strings (sent to the content session)

The source of truth for the wording is `i18n/strings.json` (the content session smoothed my draft, `d3adee5b`; still pending Pini). Version 2 of the eight texts in section 1 (`app.hint_run`, `app.hint_find`, `app.hint_rec`, `app.hint_village`, `app.hint_mode`, `app.hint_me`, `app.hint_group` change; `app.hint_trip` is new), and:

| Key | Hebrew | English |
|---|---|---|
| `app.hint_step_a11y` | רמז {n} מתוך {total} | Hint {n} of {total} |
| `app.hint_next_a11y` | לרמז הבא | Next hint |
| `app.hints_skip_a11y` | סגירת כל הרמזים | Close all hints |
| `app.hints_again` | הצגה מחדש של הרמזים | Show the hints again |

`app.hint_close_a11y` is no longer used.

## 10. Boards

`HN00` the system (version 2) · `CT00` to `CT5` the countdown tile · `HN1` to `HN6` the six mountain hints (`HN1` also the countdown tile, `HN5` at night) · `HN7`, `HN11` the village sequence · `HN12` the bubble: time, closing and dots · `HN8` English · `HN9` Settings · `HN10` the search pill.

## 11. Honestly

- Measured in the design tool against RX1, OP1 and K17, not on a phone or the emulator. Positions are for 390 × 844; in code they follow the real controls.
- The village sheet row positions are from K17; the row "החבר׳ה" in the board reads "הזמנה ומפגש" as an example.
- iOS: the same system when the redesign reaches it.
- Open for Pini (in `HN00`): the sequence as drawn; the eight texts; the tile opens the season sheet; "מחפש מיקום" in a pill.
- Version 1 (a quiet queue, at most two per opening) is replaced; it is in git history (`dea53753`).
