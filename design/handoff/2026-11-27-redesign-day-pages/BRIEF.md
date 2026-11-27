# Brief for the next designer session: the day pages (Pini, 27.11.2026)

**Why a brief and not boards:** the designer session that did the UX pass, the run picker, the shelf and the run card (`session_017QrNw77yiLRaYaGhuEMWEh`) is far past the size in `CLAUDE.md` rule 12. These four items go to a new designer session. Nothing here is designed yet; rule 7 applies (canvas first, Pini approves).

**Start from:** the last section of `design/redesign/README.md` (the tool chain and its traps), `design/handoff/2026-11-21-redesign-day-gaps/README.md` (the day screens), and the rule "the mountain is always full-screen" in `design/handoff/2026-11-27-redesign-shelf-layers/README.md`.

## 1. Faint white runs behind the day's line ("היום שלי", the day map)

- **What exists:** the other runs are already drawn behind the day line, but **in their piste colours at 32% opacity** (`design/handoff/2026-11-21-redesign-day-gaps/README.md`, the day map section; boards built by `tools/gap_boards.py`, `QUIET` and `mount()`). A white version was not drawn.
- **Pini wants:** the runs as a faint white line, no colours, so it is clear where the runs are and where he skied.
- **Starting point for the one board:** named runs only, white, about 1.6 wide, no case and no night glow, opacity about 0.55 by day and 0.4 at night; unnamed pieces and connecting ways dashed at 0.3; lifts stay dimmed; no run labels (the day line's own pins stay). Show day, night and picture mode. Check the day line's lilac-to-pink speed scale stays readable over white.

## 2. The day timeline page ("הרכס של היום")

Pini: not designed, not experiential. Wanted: a visual timeline of the day in order, an icon per kind of segment, a nicer speed chart; day and night, four languages, large text, long names never cut ("Goodaur" today).
- **Only what the analyzer has** (`tools/dayrec/`, `tools/fixtures/dayrec-*`): lift (with its queue), descent (run, stops, off-piste part), break. No stop types: a coffee icon only when a place from `site/data/places.json` is within its distance of the break; otherwise a generic rest icon. Say what is assumed.
- Idea to test: one vertical line down the page whose height is time, lifts going up and runs coming down as a real ridge (the page's own name), each segment a card on the line.

## 3. "הסיפור של היום"

Pini: one small card, not tappable, an empty screen under it. First find out **whether it is data or UI**: how many `moment.*` / `insight.*` the sample day produces (`docs/handoff/2026-10-30-dayrec-moments-copy/`, the moments engine in `tools/dayrec/`), and what the round 29 boards defined (`design/round29/README.md`, rows about the story). Then design the whole experience: several cards, each opening its descent on the mountain, the one-insight and no-insight states, the share image as the finale.

## 4. The share page: "סרטון של דקה" plays in place

Judged an obvious behaviour, not a design question; a builder can do it without a board, if the manager and Pini agree:
- The play circle plays **inside the preview card**; a tap toggles play and pause; a thin progress line along the card's bottom edge; at the end the circle becomes "replay".
- "שיתוף" and "שמירה" stay enabled while it plays and do not stop it.
- Nothing on the card leaves the page. The full replay on the mountain becomes an explicit text link under the card (wording through content).

## Suggested order

1 (one board, small), then 2, then 3 with 4's link folded into the same page family.
