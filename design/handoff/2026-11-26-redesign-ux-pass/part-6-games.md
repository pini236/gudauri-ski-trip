# Part 6: games (item 9). Sonnet

Boards `GA1`, `GA2` (`ref/GA*.jpg`). **Approved (Pini, 27.11.2026; recorded in `docs/STATUS.md` J.7 and `docs/DECISIONS.md` by the manager session). Build.** The games themselves do not change; this is the entry and the list. It replaces board `R11` ("ערב בבקתה") and, in the app, the old-design `GamesScreen` that the row opens today.

## 1. The name

"ערב בבקתה" becomes "משחקים" (`app.cabin_title`; the menu tile of part 1). The subtitle `app.cabin_sub` is retired.

## 2. The screen

A full screen on the veiled mountain (the sunset picture by day, the night picture at night), like the other full screens.

- **Top:** the round back button, "משחקים" beside it (21 / 700), and at the end a small glass tag "גם בלי קליטה" (height 36).
- **The first card** (full width, height 140, radius 20, background `tile`, border 1 `line`): the game's picture at the start side (150 wide, full height, cropped to fill), and beside it: a small line in `acc` 12 / 700 ("המשחק האחרון"; with no history "מתחילים כאן"), the name (18 / 700), my best (12 `ink2`; with no history the game's short description `games.*_tag`), and a chip in `acc` with a play icon ("ממשיכים" / "לשחק").
  - Which game: the last one played; with no history, "איחוד כדורי שלג".
- **The other four:** a grid of two columns, gap 10. Each card: the picture (118 high, cropped to fill), the name (15 / 700) and my best (12 `ink2`; never played: `app.cabin_not_played`).
  - Order: by last played; with no history: הירידה, בית הספר, קרב, שלג טרי.
  - My best, per game: the existing strings (`app.cabin_best`, `app.cabin_stars_run`, `app.cabin_lesson`, `app.cabin_level`, `games.fresh_meta`).
- **Pictures:** the games' own thumbnails, already in the repo: `site/games/thumbs/` (`merge`, `descent`, `school`, `snowball`, `fresh-snow`, webp). Package them with the app; no network.
- **Bottom:** `app.cabin_table` ("הטבלה של {group}"), a secondary glass button, height 48, only for a group member; opens the scores tab of the group.
- A tap on a card opens the game, as today. The shell around a game and the end screen (`R12`) do not change.

## 3. Android, languages, accessibility

- System back returns to the menu.
- Left to right: mirrored; the picture of the first card moves to the other side.
- Screen reader: each card is one item: the name, then my best.
- Large text: the grid stays two columns up to font scale 1.3, then one column; the screen scrolls.
