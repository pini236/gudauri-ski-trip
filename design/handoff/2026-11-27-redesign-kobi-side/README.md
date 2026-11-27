# The Kobi side in picture mode: going over and back (P.17, 27.11.2026)

**Status: approved by Pini 27.11.2026; Android built on `claude/android-kobi-side` (not merged, not tested on a phone); the 3 px set is in `site/img/peak/kobi/<rev>/` (architect ceiling 1.1 MB, 180 KB each).** Was: waiting for Pini (canvas https://claude.ai/artifact/8NrAKy97KWRj3JWcxx41yQ, page "צד Kobi בתמונה: לעבור ולחזור", boards `KB00`-`KB4`). Do not build before approval (rule 7); the quality part needs the architect first. No new strings. Builds on `design/handoff/2026-11-25-redesign-kobi-picture/` (the view, the tables, the slide). Source: `design/redesign/tools/kb_boards.py`.

## The way in (KB1)
- A glass tag "צד Kobi" (`map.kobi_side`, screen reader `map.kobi_side_aria`) with an over-the-ridge icon, 36 high (44 touch), just above where the Kobi line peeks at the top of the front picture.
- A tap on the tag **or on the Kobi line** turns to the side (today the line only selects: that is the gap Pini hit). Choosing Kobi from the shelf, search or a link turns too (as in Z08).

## The move (KB2)
The approved Z08 slide: 450 ms ease-in-out, front fades and moves 36 pt left, the side comes in from the right. During it the compass button appears at the top of the column and its needle turns 180 degrees. Top pill, column and dock do not move. Reduced motion: instant swap, needle already turned.

## On the side, and back (KB3, KB4)
- Kobi is selected and lit; its sheet rises as usual. Closing the sheet does not return (as in live).
- **The compass** sits above the picture/live switch (left 12, top 510, 44), only while on the Kobi side. The needle points down (north is behind you). On arrival "צד Kobi" shows beside it for 3.5 s (pill 118 wide), then it folds to the 44 circle.
- A tap on it: back to the front, the same move reversed; the compass fades out. Screen reader: "צד Kobi. סיבוב חזרה למבט מדרום" (`map.kobi_side` + `map.compass`); long press shows the name (B10).
- Why the compass and not a back button: it is already the rule on the live mountain (S08, approved: the compass appears only after turning, a tap returns). A back button would be a fifth column button and reads like leaving the screen.

## Quality (needs the architect)
Measured on the seven lights, `peak.py` `out=3`, webp method 6:

| Set | px per point | quality | set KB | one image KB |
|---|---|---|---|---|
| Today | 1.5 | 74 | 327 | 33-68 |
| B | 2 | 80 | 626 | 63-126 |
| **A (recommended)** | 3 | 80 | 1,069 | 108-215 |
| Front, for comparison | 3 | | packaged | 127-218 |

A = the same sharpness as the front. Downloaded in the background once per `rev`, kept on the phone, as today. **The ceiling (350 KB, architect 25.11.2026) must rise to about 1.1 MB**; all other conditions stay (one `rev` for set and tables, use only when all seven are in, live-for-a-moment without the set). The export tool change is `PX,Q=3,80` in `kobi_export.py`, then `build-peak.py` for the new `rev`.

## Honestly
- Sharpness fixes about half. In the shaded north valley the painter draws rock as long fall-line streaks, and walls facing the camera smear a little; a 2x denser sampling only shortens the streaks. A painter round for this side (real rock bands, detail in shaded snow) is a separate task (flag for Pini, in `docs/ROADMAP.md`).
- Rendered in the design tool, not checked on a phone. The 3x morning and night images are canvas assets and `design/redesign/assets/pk-kobi3-*.webp` (not the shipping set).
