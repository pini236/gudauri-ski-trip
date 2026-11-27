# The run card (the run sheet, peek and open)

**When:** 27.11.2026, product designer (Fable). Design only, no code.
**Status:** waiting for Pini (rule 7). Four questions in board `RK00`.
**Canvas:** https://claude.ai/artifact/8NrAKy97KWRj3JWcxx41yQ (version 92), page "כרטיס המסלול (27.11)", boards `RK00`, `RK1` to `RK5`. Source: `design/redesign/tools/rc_boards.py`. Pictures: `ref/`.
**Replaces** the look of `K10` and `S07` (`design/handoff/2026-11-23-redesign-on-mountain/README.md`, section 5). The rules there (two heights, conditions, videos, previous and next in the shelf's order, share) do not change.

## Pini's five notes, and the answer to each

| # | Note | Answer |
|---|---|---|
| 1 | "(מפה רשמית)" is not needed; less text | Head: the sign, the name, a small tag "כחול · קל" with the colour dot, and `Goodaura ↓ Shino` (from where to where, no words). The suffix `(מפה רשמית)` is dropped everywhere in the app |
| 2 | The numbers are plain text rows | The run's real profile, coloured by slope, with the top and bottom heights, a tick every 500 m and a pin on the steepest stretch; the three numbers under it |
| 3 | "לרדת בסרט" is unclear | "טיסה מעל המסלול" with a play triangle; while it plays, a dot runs down the profile and the button becomes "עצירה" |
| 4 | "סרטונים · 1" does not say what to do | One row at the foot of the card: three small video frames, "3 סרטונים", the temperature now, and a round up chevron (the same one as in the "מסלולים" button). A tap or a drag opens |
| 5 | The dots read as video paging | The card floats; the edges of the previous and next runs' cards peek at both sides; the dots sit under the card, outside it |

## The card (`RK1`, `RK3`, `RK5`)

- **The mountain fills the screen** behind the card (the rule in `design/handoff/2026-11-27-redesign-shelf-layers/README.md`); the chosen run is lit above the card.
- **Frame:** 18 from the sides, 44 from the bottom, radius 28, glass, padding 8 / 16 / 14, gap 12. **Neighbours:** the same shape, 8 from the card, 10 visible at each side, 72% opacity. **Dots:** 18 from the bottom, 6 x 6, gap 6, `ink` at 32% (the current one 18 wide, 100%); at most seven shown, the window slides.
- **Head** (min height 48): the sign (30 high); the name 23 / 700, always left to right; under it the tag (height 22, radius 11, background `line`, a 9 dot in the run's colour, 12.5 / 700: colour name and level from the existing strings) and the route, 13 `ink2`: the top station, a down arrow, the bottom station (lift names, not translated). At the end a round share button (44).
- **Profile** (height 104): the run's elevation along its length, from the elevation model; the top at the start side (mirrors with the language). The line 4 wide on a `paper` case 7, and the area under it at 20% (30% on the steep class), coloured by the approved slope thresholds (up to 15 degrees `#3FA85F`, to 25 `#F2C13D`, to 30 `#F08A3C`, above `#DC3B33`), per stretch. A tick on the baseline every 500 m. The top and bottom heights, 11.5 / 700 `ink2`, at the two ends. A pin (height 22, `ink`) with the degrees over the middle of the steepest stretch.
- **Numbers:** three in a row, 26 / 300 with the unit 13 / 500 and a caption 12 `ink2`: length, drop, the steepest stretch. The same values as today.
- **The film button:** primary, height 50, a play triangle and "טיסה מעל המסלול". Shown only when the film exists for the run (as today).
- **The foot row** (min height 48, radius 18, background `line`): up to three video frames (52 x 34, overlapping), the count ("3 סרטונים"), under it the temperature at the top of the run and "התנאים עכשיו" (12.5 `ink2`), and at the end a 32 circle with an up chevron. No videos: only the conditions. No forecast: only the videos. Neither: no row.

## Open (`RK2`)

The same card grows to 112 from the top (300 ms) and scrolls inside; the foot row is replaced by "התנאים במסלול" (two cards, as today) and "סרטונים מהמסלול" (rows: a 96 x 54 frame with the length, the title, the channel; up to three, then "כל ה-N"). The neighbours and the dots stay. Drag down returns to the low height; again closes.

## The film (`RK4`)

While it plays: the foot row is hidden, the button is secondary with a pause icon and "עצירה", and a dot (14, `acc`, white ring 3) runs down the profile in step with the camera. A drag on the profile scrubs the film. At the end the card returns to `RK1`.

## Sideways

A horizontal drag moves the whole card with its neighbours; on release the next card settles in the centre (300 ms) and the mountain's lit run and view change with it. The first and last cards have one neighbour only.

## Strings (through the content session, after approval)

- `app.run_film`: "לרדת בסרט" becomes "טיסה מעל המסלול" (en "Fly over the run"). Alternatives for content: "סיור במסלול", "לראות את הירידה".
- New: "עצירה" for the film; the foot row: "{n} סרטונים" (one / two / n), "התנאים עכשיו"; open: "סרטונים מהמסלול". Existing: the conditions strings, colour names and levels, `app.run_videos_all`.
- Dropped from the app: the suffix " (מפה רשמית)", "סרטונים · {n}" as a label, the line "החלקה הצידה: המסלול הבא".

## Build notes

One Sonnet session: the run sheet file of the redesign and the profile drawing (the per-point slope classes already exist for the chosen run's line). Screen reader: the profile is one item ("פרופיל המסלול: מ-2,707 עד 2,171 מטר, הקטע התלול 20 מעלות"); previous and next stay as two actions. Large text: the numbers row wraps to two rows; the profile keeps its height.

## Honestly

The profile, length, drop, heights and steepest stretch of Tatra 2 are from `site/data`; the videos are from the real list; the temperatures are examples. Drawn in the design tool, not on a phone; motion is specified, not drawn. No contract or data change.
