# Main audit, 27.11.2026 (read only, `origin/main` 61dbe98)

Method: handoff folders `design/handoff/2026-11-2[3-7]*` vs files in `origin/main` and unmerged `claude/android-*` branches. File-level check only (symbols and files), no phone, no run of the app. Not checked line by line against `git log -p` of the shared files; no removed-by-later-merge case was found at file level.

| Item | State |
|---|---|
| UX pass 1 menu (`LiveMenu.kt`, `MenuSheet`) | in main |
| UX pass 2 my trip (`LiveTrip.kt`) | in main; notes branch `android-ux-part2-notes` (ROADMAP only) not merged |
| UX pass 3 ski pass (`LiveSkipass.kt`), P.20 fit, P.21 office buttons | in main |
| UX pass 4 food (`LiveFood.kt`), 5 group gate, 6 games (`LiveGames.kt`), 7 far from mountain | in main (old `android-ux-part*` branches show as unmerged only because of merge history) |
| Hints v2 sequence, 13 s | in main; the 13 s was cut to about 6 s by the animator scale: fixed on this branch |
| Hint motion P.19 | on branch `android-hint-motion` (1 commit), not in main |
| Countdown tile, top pill P.25 (`TopPill*.kt`) | in main; `SeasonTile` gone from `LiveMountain.kt` |
| Runs button "מסלולים" (P.3, option A) | on branch `android-runs-button-a` (2 commits), not in main; Pini has not picked v4 |
| Shelf chips, layers moved into shelf (SL/SV) | not found as built; waiting for the runs-button pick |
| Run card redesign (RK) | missing (no run-card file; only strings merged) |
| Day story and share page (DS, DV) | share tap fix in main; story page rework and share states missing |
| Day timeline P.11 | in main; "rest" (scrolled strip, grid, break band) on branch `android-day-timeline-rest` |
| My days list MY, records RS (P.24) | on branch `android-my-days-2` (6 commits), not in main; `KeepRules`/NS (P.23) in main |
| Small items: record mode choice, `NameHold`, ticket office | in main |
| Kobi side in picture (P.17) | in main, but the photo never shows: fixed on this branch |
