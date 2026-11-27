# The run shelf and the layers: what a chip does

**When:** 27.11.2026, product designer (Fable). Design only, no code.
**Status:** waiting for Pini (rule 7). Three questions in board `SL00`.
**Canvas:** https://claude.ai/artifact/8NrAKy97KWRj3JWcxx41yQ (version 91), page "בוחרים מסלול: הכפתור, המדף והשכבות (27.11)", boards `SL00`, `SL1` to `SL8`. Source: `design/redesign/tools/sl_boards.py`. Pictures: `ref/`.
**Read with:** `design/handoff/2026-11-27-redesign-dock-runs/README.md` (the "מסלולים" button, same page). Base spec of the shelf and the lenses: `design/handoff/2026-11-24-redesign-village-shelf-lenses/README.md`, sections 2 and 4.

## The mountain is always full-screen (Pini, 27.11.2026)

**The mountain fills the whole screen, edge to edge, under every sheet, shelf, card row and bar. No band of background is ever visible below it or beside it.** The cards, the strip, the chips and the bar are glass over the mountain.

- When the shelf (or any sheet) opens and the ski area has to sit above it, **the view moves; the canvas does not shrink.** Live model: the camera target shifts so the ski area rises 120 (as in the shelf handoff). Picture mode: the picture is anchored at its top and scales up to about 1.14, so its bottom edge stays at or below the bottom of the screen; back to 1.0 when the shelf closes (300 ms, with the shelf).
- This holds for every screen of the redesign: the menu, the trip, the ski pass, food and drink, the group, the games.
- The first upload of boards `SL1`-`SL5` and `SV3`-`SV6` showed a pale band under the cards; that was a fault of the drawing, fixed in canvas version 91.

## Pini's three notes are one problem

Two rows of chips look the same and do different things: in the shelf they sort the cards, in the layers they colour the mountain. "בשמש עכשיו" is in both; "מהקל לקשה" and "קל ← קשה" are nearly the same words. In the shelf the sort changes only what is off screen (the centre card stays, the mountain does not change). In the layers nothing says which layer is on or what the colours mean, an empty answer turns every run grey, and the button column jumps 210 because the layer's sheet is tall and the bar disappears.

## Recommended (A): one row of chips, in the shelf; every chip orders the cards and paints the mountain

Six chips: לפי רכבל, לפי קושי, הארוכים, בשמש עכשיו, פתוחים, שלג חדש. The layers button leaves the button column (`SL6`).

| Chip | Cards | Mountain | Run strip (tick height) | The "why" line on the card |
|---|---|---|---|---|
| לפי רכבל | a group per lift, from the village up | the runs of this lift lit, the lift in colour, the rest ghosts | a gap between lifts (even height) | the return lift and its minutes (as today) |
| לפי קושי | green, blue, red, black; inside a colour from the gentlest | all runs of the centre card's colour lit | four colour blocks (steepest stretch) | "הקטע התלול 21°" |
| הארוכים | longest first | the six longest lit | length, descending | "מקום 7 באורך" |
| בשמש עכשיו | most in the sun first | sun `#FFC93A` on a white case, shade `#B9CCFA` thin (the existing lens) | yellow then grey-blue (share in the sun) | "42% מהמסלול בשמש" |
| פתוחים | open first | open in colour, closed `#8A93A3` dashed (the existing lens) | colour, then low grey | "פתוח · Kudebi עובד" / "סגור · Sadzele לא עובד" |
| שלג חדש | most new snow first | 10 cm and more in `acc`, up to 10 `#7FB2FF` (the existing lens) | centimetres | "12 ס״מ שלג חדש" |

**The run strip** (new): a glass panel above the chips (12 from the sides, radius 18, padding 9 / 12 / 10). Row 1: the chip's name (13.5 / 700), "· the group or the count" (500, `ink2`), and at the end the position ("1 מתוך 4", tabular). Row 2: one tick per named run, in the chip's order: width 7, radius 3.5, gap 3 (9 at a group boundary), height 8 to 26 by the table, colour of the run (or of the lens). The centre card's tick has a ring: 2 `bg`, then 2 `ink`. A tap on a tick brings that run's card to the centre; a drag along the strip scrubs.

**When a chip changes** (the feedback Pini asked for), all together, with one light haptic tick:
- the ticks **move** to their new places and heights, 300 ms, staggered 8 ms from the start side;
- the neighbours of the centre card slide out 16 and fade (120 ms), the new ones slide in (180 ms); the centre card stays the same run and only its "why" line cross-fades (150 ms), in `acc` 700;
- the mountain repaints in a 200 ms cross-fade; the strip's first row cross-fades (150 ms).
- Reduced motion: everything swaps in 150 ms, no movement.

**Empty answers never grey the mountain.** No sun (night): the chip is at 40% and not selectable (as today). No recent lift report: "פתוחים" at 40%, a tap shows the existing `status.board_hint` in the strip's first row for 2 s. No new snow anywhere: "שלג חדש" stays selectable, the mountain keeps the runs' own colours, the first row says "אין שלג חדש ב-24 השעות האחרונות", order by lift.

**What is removed:** the layers button, the chip row under the pill, the lens sheet with its paragraph, and so the column jump. The sources ("לפי התחזית, לא מדידה · Open-Meteo"; "הצל מחושב מצורת ההר") move to one 11.5 `ink2` line under the cards' row, shown only while that chip is on.

## If the layers button stays (B, `SL7`, `SL8`)

- **A legend bar in the bar's own slot:** left and right 10, bottom 12, min height 60, radius 30, glass. It cross-fades with the bottom bar (200 ms). **The column does not move at all.** Inside: the layer's name (15 / 700), one line under it (12 `ink2`), the legend at the end (swatches 16 x 5 with words, 11.5), and a round X (44) that turns the layer off.
- The chip row under the pill stays, with the shelf's names ("לפי קושי", "פתוחים"); the layers button is filled (`ink`) while a layer is on.
- No data: the runs keep their own colours; the bar says so ("אין שלג חדש"), no legend.

## Remove entirely (C)

The column without the button (`SL6`); "פתוחים" and "שלג חדש" are not offered anywhere. Not recommended: both answer real questions on a ski day.

## Build notes (after approval)

- One Sonnet session for A is enough for the strip, the chips and the card line; the mountain painting reuses the existing lens code (`LiveLenses` and the shelf's highlight). The tick animation is a `LazyRow`-free custom layout (28 items); keep each tick keyed by run so it animates to its new place.
- Strings, through content: chip "לפי קושי" (replaces "מהקל לקשה" and "קל ← קשה"), "פתוחים", the strip's first-row texts, and the five "why" lines with their variables. Existing lens strings cover the sources.
- Hint 2 (`app.hint_find`) text should mention that the list can be ordered; content decides.

## Honestly

Orders, lengths, slopes and shade on the boards are computed from `site/data`; the snow and the closed lifts (Sadzele, Firni) are examples. Drawn in the design tool, not on a phone; the motion is specified, not drawn. No contract or data change, so no architect review.

## Version 2 (27.11.2026): same concept, more craft

**Why:** the manager relayed Pini's answers: the "מסלולים" button and the layers moving into the shelf (no layers button) are the chosen direction, and he asked for a more beautiful, more experiential design. Boards `SV00`, `SV1` to `SV6` (canvas version 90), source `design/redesign/tools/sv_boards.py`. **Version 2 replaces `DK2`-`DK4` and `SL1`-`SL6` as what to build; it waits for Pini's look.** Alternatives B and C of both rounds are dropped.

**The bar (`SV1`, `SV2`):** height 64, radius 32, padding 0 8, gap 8. "תפריט" and record are 48 high.
- **The runs button:** fills the middle, height 48, radius 24, background `tile`, border 1 `line`, padding 16 start and 10 end. Two lines at the start: "מסלולים" (15.5 / 700) with the count beside it (12.5 / 700 `ink2`); under it, 5 lower, **the micro strip**: one mark per named run (3 wide, 5 high, radius 1.5, gap 1.5), in the run's colour, ordered green to black. At the end a 32 circle (`line`) with an up chevron (17).
- Narrow (recording with the clock, English, large text): the circle with the chevron drops first, then the count. The word and the micro strip always stay.

**Opening the shelf (350 ms):** the bar sinks and fades; the marks of the micro strip rise about 190 and grow into the shelf's ticks, each mark staying the same run (a shared element; they re-order to the shelf's saved chip on the way); the cards rise one after another, 40 ms apart; the mountain rises 120. Closing: the reverse, the ticks fall back into the button. Reduced motion: 150 ms cross-fade.

**The shelf (`SV3` to `SV6`):**
- **Strip panel:** 12 from the sides, radius 22, padding 11 / 14 / 12. First row 15.5 / 700 with the group or count in 13 / 500 `ink2` and the position at the end. Ticks 8 wide, radius 4, 3 apart (8 at a group boundary), height 12 to 30; the centre run's tick has the ring and a small pointer (5) under it toward its card.
- **Chips:** height 38, an icon (16) and the word; six, the row scrolls and the lit chip is always fully in view. **The lit chip takes the colour of what it paints:** "בשמש עכשיו" `#FFC93A` with text `#3B2A00`; "פתוחים" `good` with white; "שלג חדש" `acc`; the three orders that do not paint by a scale (רכבל, קושי, הארוכים) stay `ink`. Lit chips get a shadow `0 6 14 rgba(10,25,45,.25)`.
- **Card:** 232 x 172, radius 24, padding 14 / 16. At the start a panel 52 wide, full height, radius 16, the run's colour at 12%, with the run's shape (40 x 110). Beside it, top to bottom: the sign and the name (17 / 700); **the chip's answer as the big number** (42 / 300, its unit 15 / 500) with a caption under it in `acc` 12.5 / 700; a foot line 12 `ink2` with the other facts.

| Chip | Big number | Caption | Foot line |
|---|---|---|---|
| לפי רכבל | minutes of the return lift | "חזרה ב-{lift}" | length · drop · colour |
| לפי קושי | the steepest stretch, degrees | "הקטע התלול" | length · drop · colour |
| הארוכים | length in km | "מקום {i} מתוך {n} באורך" | drop · steepest · colour |
| בשמש עכשיו | share in the sun, percent | "מהמסלול בשמש עכשיו" | length · drop · colour |
| פתוחים | "פתוח" / "סגור" (as the big word) | "{lift} עובד" / "לא עובד" | length · drop · colour |
| שלג חדש | centimetres | "שלג חדש, לפי התחזית" | length · drop · colour |

- **On a chip change** the big number rolls to its new value (digits slide 12 up, 200 ms) while the ticks move (300 ms) and the mountain repaints (200 ms). `SV4` plays the change between "לפי רכבל" and "הארוכים" in a loop.
- **Ranks on the mountain:** on "הארוכים" the six longest runs carry a small disc with 1 to 6 (22, `ink`, a 2 `bg` ring) about a third of the way down each.
- **Scrubbing the strip:** a drag along the ticks changes the lit run on the mountain under the finger and brings its card to the centre, with a light haptic tick per run.

Everything else (what each chip orders and paints, the empty answers, what is removed) is as in the sections above.
