# B9: text on the mountain only inside a frame (26.11.2026)

**Status:** waiting for Pini. Canvas version 74, page "ב9 · טקסט במסגרת", boards `B900`, `B91`, `B92`, `B93`. Source: `design/redesign/tools/b9_boards.py`. Design only; nothing changes in code before approval (rule 7).

From `design/redesign/review-release.md`, B9: the search screen, the countdown and the ski pass show text with a halo and no frame. Pini's rule: no loose text.

## 1. Verdict per screen

| Screen | Today | Verdict |
|---|---|---|
| Search, first 30 s | "מחפש מיקום" with a halo | Covered by the hints round: glass pill (`HN10`, `design/handoff/2026-11-26-redesign-hints/` section 7) |
| Search, after 30 s | `app.search_hint` loose under the title | **New here:** the pill grows into one frame holding both lines (`B91`, `B92`) |
| Countdown before the season | Big number and a haloed line | Covered by the hints round: a framed tile (`HN1`, hints handoff section 6) |
| Ski pass (`LiveSkipass.kt`) | Big price and lines at the top | **No change.** It is the head of a whole page over the veiled mountain (`SheetInk.veil`, Q09), which the rule allows |
| Speed ring while recording | Number inside the ring | **No change.** Already inside the ring's glass circle |

## 2. The search frame (`SearchPulse.kt`, `B91` day, `B92` night)

- Starts as the `HN10` pill: glass, height 40, radius 20, its top 132 under the pulse centre, "מחפש מיקום" 16 bold.
- When `hintShown` turns true (30 s), the same frame grows downward and holds two centred lines: the title (16 bold, `ink`) and `app.search_hint` (13.5, `ink2`, gap 3). Radius 20, padding 10 / 18 / 11, max width 300; the hint wraps to two lines if needed (large text, Russian, Georgian).
- Growth 250 ms ease-out, the hint fades in with it. Reduced motion: no growth, it appears at once.
- No `Shadow(halo, ...)` on either text. The veil, pulse and "החזקה לסיום" stay as GS1.
- Screen reader unchanged (title, and the hint once shown).
- No new strings.

Alternative, not recommended (`B93`): a second smaller pill under the first. Two frames for one state.

## 3. Coordination

- The hints designer (`session_017QrNw6Jfd4CrtBZbwCRuoR`) owns `HN10`; this extends it and does not edit any `HN` board. Implement `HN10` and `B91` together in one change.
- B8 is closed by `HN1` (see the STATUS log of 26.11.2026); nothing here touches the countdown.

## 4. Honestly

Measured in the design tool only (local render, mountain picture not loaded), not on the emulator or a phone.
