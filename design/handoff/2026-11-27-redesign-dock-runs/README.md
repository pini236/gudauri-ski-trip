# The run picker in the bottom bar (instead of "לאן יורדים?")

**When:** 27.11.2026, product designer (Fable). Design only, no code.
**Status:** waiting for Pini (rule 7). Three questions in board `DK00`.
**Canvas:** https://claude.ai/artifact/8NrAKy97KWRj3JWcxx41yQ (version 88), page "הסרגל: בחירת מסלול (27.11)", boards `DK00`, `DK1` to `DK6`. Source: `design/redesign/tools/dk_boards.py`. Pictures: `ref/`.

## The problem

The pill has a magnifier, a grey fill and grey text: the three signs of a text field. A tap is expected to open a keyboard; it opens the shelf of run cards. "לאן יורדים?" is also a question, not a name. There is no typed search (decision 50).

## Three alternatives

| | What | For | Against |
|---|---|---|---|
| **A (recommended), `DK2` to `DK4`** | a button "מסלולים": four dots in the piste colours, the word, the count (28), an up chevron | one word that says what opens; reads as a button like "תפריט"; the chevron says "opens from here" | none found |
| B, `DK5` | four small buttons, one per colour, each with its count; a tap opens the shelf at that colour | fast for a skier who knows the colours | no word at all; four tight targets beside record |
| C, `DK6` | the tops of three run cards peek above the bar, with a tab "28 מסלולים" | a list is visible before any tap | covers 60 more of the mountain all the time; pushes the button column up; the bar is left with a hole |

## A, to build (after approval)

- **The button** (`Dock`, `redesign/LiveShelf.kt`, replaces the search pill): height 44, radius 22, background `tile` (white 74%; night: white 7%), border 1 `line`, fills the width between "תפריט" and record. Text 15 / 700 `ink`. No magnifier.
- **Inside, start to end:** four dots of 8 in a 2 x 2 grid, gap 3 (green, blue, red, black; the night colours at night); "מסלולים"; the count in a small pill (height 22, min width 26, background `line`, 12.5 / 700 `ink2`); at the end an up chevron, 18, `ink2`.
- **Tap:** opens the shelf, exactly as today. Long press: the name pill (B10), as today.
- **The count:** named runs in the data (28 today). With the "פתוח" lens on and a recent report: the number of open runs.
- **Narrow** (recording with the clock in the record button, English, large text): the count drops first, then the dots. The word and the chevron always stay.
- **Left to right:** mirrored (`DK4`).
- **Screen reader:** "מסלולים, 28. פתיחת הרשימה".
- **Hint 2** (`app.hint_find`) points at this button; its text stays.

## Strings (through the content session, after approval)

`app.dock_find`: "לאן יורדים?" becomes "מסלולים" (en "Runs"). New: the screen-reader label with `{n}`. Nothing else.

## Honestly

Drawn in the design tool (390 x 844), not on a phone. No contract, data or measurement change, so no architect review.
