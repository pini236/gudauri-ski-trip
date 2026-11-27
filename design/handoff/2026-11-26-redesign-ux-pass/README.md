# UX pass on the new Android design (after build 0.1.954)

**When:** 26.11.2026, product designer (Fable). Design only, no code.
**Status:** **approved in full (Pini, 27.11.2026: "I approve all the recommendations"; recorded in `docs/STATUS.md` J.7 and as a new decision in `docs/DECISIONS.md` by the manager session).** All five questions of `UX00` yes, including the menu instead of the village (changes decision 83). Implementers build per part.
**From:** `design/handoff/2026-11-26-pini-feedback/README.md`, section A (ten items).
**Canvas:** https://claude.ai/artifact/8NrAKy97KWRj3JWcxx41yQ (version 87), page "מעבר חוויית משתמש (26.11)", boards `UX00`, `MN1`-`MN6`, `TR1`-`TR4`, `SP1`-`SP2`, `FD1`-`FD3`, `GR1`-`GR4`, `GA1`-`GA2`, `NM1`-`NM3`. Source: `design/redesign/tools/ux_boards.py`. Pictures of every board: `ref/`.

## The diagnosis (why a fresh user is lost)

Walked the whole app as a new user, on the boards and in the Android code (`app/android/.../redesign/`). The problem is not one screen; seven things repeat everywhere.

1. **A place does a menu's job.** "הכפר" is a place on the mountain, yet it holds the flight, the group and the games. Nobody looks for their flight in a village. Hint 4 had to list the contents in words.
2. **The menu looks like a data table.** `VillageSheet` is five text rows: no icon, no chevron, grey text at the end that reads as a value, not a destination.
3. **Mood names instead of names of things.** "החבר׳ה", "ערב בבקתה", "הכפר". The voice also flips: "הטיול שלך" beside "הימים שלי".
4. **A row promises one thing and opens another.** "אוכל ושתייה" opens the Goodaura lift panel (`onFood`, `LiveMountain.kt:839`), with the food as a small line at the bottom. "ערב בבקתה" opens the old-design `GamesScreen`.
5. **What can be done is not visible.** Every field of the boarding pass is editable by a tap, but a filled field looks like print. The stub tears, but nothing invites the tap, and the paper itself never tears (see part 2).
6. **The screen does not tell the truth about its state.** Groups load late; until then the row says "מצטרפים או פותחים קבוצה" and then jumps. Far from the mountain, recording starts as usual and the camera flies to empty ground.
7. **Two homes for personal things.** The group and the trip in the village; the groups, my days and the account under "אני". No rule a user can guess.

## The rule (one fix, not ten patches)

- **The mountain is home.** Runs, lifts, "איפה אני", recording. Unchanged.
- **Everything else is in one menu, and it is called "תפריט".** Each destination is a tile: an icon, a name that says what it is, and one live line.
- **The village goes back to being a place.** What is really there: food and drink, the ticket office, parking. The village picture is the map of "אוכל ושתייה".
- **Every name says what opens, and every screen opens what its name promised.**
- **What is editable looks like a field. What loads keeps its place. What is not possible is said plainly.**

This changes what was approved in round 3 ("everything lives in the village, no menu", decision 83). Approved as a new decision (question 1 in `UX00`).

## The parts (one implementer session each)

| Part | Items | File | Boards | Implementer | Depends on |
|---|---|---|---|---|---|
| 1. The menu | 1, 2 | `part-1-menu.md` | `MN1`-`MN6` | Sonnet | none; build first |
| 2. My trip and the stub | 3, 4 | `part-2-trip.md` | `TR1`-`TR4` | Sonnet | none |
| 3. Ski pass | 5 | `part-3-skipass.md` | `SP1`, `SP2` | Sonnet | none |
| 4. Food and drink | 6 | `part-4-food.md` | `FD1`-`FD3` | Sonnet | part 1 (the tile) |
| 5. My group | 7, 8 | `part-5-group.md` | `GR1`-`GR4` | Sonnet | none |
| 6. Games | 9 | `part-6-games.md` | `GA1`, `GA2` | Sonnet | none |
| 7. Far from the mountain | 10 (and bug B2) | `part-7-far.md` | `NM1`-`NM3` | **Opus** | none |

Part 7 is the only one for Opus: it touches the location layer of the live model, the camera follow of the ski day (`LiveDay`, `LiveView.follow`) and the recorder's start path. The rest are layout, wording and state on existing Compose screens.

Shared values (colours, glass, sheet, buttons, type) are unchanged: `design/handoff/2026-11-20-redesign-poc/README.md`. New tokens used by several parts: `tile` = white 74% (night: white 7%); `tint` = accent 11% (night 17%).

## Strings

The list: `strings.md`, updated after the content session's review (`content-review.md`, with the designer's answers at its end). The content session (`session_019k19PuDmyzecDEFv7DGcdx`) enters the final text in four languages (entered 27.11.2026). Renames of existing keys' text (not the keys): `app.home_trip` "הטיול שלי", `app.grp_title` "הקבוצה שלי", `app.cabin_title` "משחקים".

## Not in this round

- The hint sequence is changed only where the menu forces it (part 1, section 6).
- "אני" stays at the top of the mountain. Whether to remove it once the menu proves enough is in `docs/ROADMAP.md`.
- iOS: the redesign has not reached it; when it does, the same parts apply. `docs/PARITY.md` gets its rows when each part is built.
- No contract, data or measurement change, so no architect review. Exception: part 7 changes when the recorder starts; the Opus implementer checks with the architect if the recorder service's start contract changes.

## Honestly

Drawn and measured in the design tool (390 x 844), not on a phone. Places, prices and distances on the boards come from `site/data`; the trip, the group and the scores are examples. `TR3` is animated only in the canvas.
