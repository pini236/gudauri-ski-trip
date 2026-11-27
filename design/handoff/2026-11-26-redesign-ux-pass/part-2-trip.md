# Part 2: my trip and the stub (items 3, 4). Sonnet

Boards `TR1`-`TR4` (`ref/TR*.jpg`; `TR3` is animated in the canvas). **Approved (Pini, 27.11.2026; recorded in `docs/STATUS.md` J.7 and `docs/DECISIONS.md` by the manager session). Build.** The rules of the trip itself (stored on the phone, stages of the count) do not change; base spec: `design/handoff/2026-11-21-redesign-around/README.md`, section 6.

## 1. It shows that everything can be edited (`TR1`)

`LiveTripPass` (`redesign/LiveTrip.kt`):

- **"עריכה" moves into the card**, at the end of its first row: a chip, height 32 (touch 44), radius 16, background `tint`, text and a pencil icon (15) in `acc`, 13 / 700. The glass pill on the mountain is removed. It opens the full form, as today.
- **Every value sits on a dashed line:** 1.5, `acc` at 45% (night: 55%), 2 below the text, as wide as the value. This is the field look; no hint text.
- **An empty field** stays "+ שעה", "+ מספר", "+ כבודה" in `acc`, on the same line.
- **Pressed:** the value turns `acc` and its line becomes solid 2 `acc` while its editor is open (`TR2`).
- The airport codes are fields too: a tap opens the airport list, with the same pressed state (no line under the big codes; the city name under them gets it).
- Title: "הטיול שלי" (the text of `app.home_trip` / `trip.title`).

## 2. A tap on a field opens its own editor (`TR2`)

Already built (`LiveCalendar`, `PassEditor`). Only the look: the editor is a bottom sheet with the field's name as its title (21 / 700), "ביטול" (secondary) and "שמירה" (primary). The pass stays visible above it under a veil `rgba(8,16,30,.28)`, with the field marked.

## 3. The stub tears (`TR3`, `TR4`)

**Why only the sound is noticed today:** the transform is applied to the stub's `Row`, which has no background; the paper, the notches and the perforation belong to the parent column. So the paper never tears; only the text tilts, slides and is clipped by the card's shadow shape.

**The fix: two pieces of paper.**

- **The card** ends at the perforation: bottom corners square, a half notch (radius 10) at each bottom corner.
- **The stub** is its own surface: paper background, height 100, bottom corners 22, a half notch at each top corner, the dashed line (2, `line`) along its top edge, 14 from each side. It carries its own shadow.
- At rest the two meet with no gap and read as one card, exactly as today.

**The motion** (the same sound, haptics and total times as today: 520 + 620 ms):

| Step | ms | What |
|---|---|---|
| 1. Holes | 0 to 520 | twelve holes (8, `rgba(40,56,90,.55)`) punch along the perforation one after another, 43 ms apart, from the start side to the end side, each with its haptic tick. `ticket-tear` plays at 0 |
| 2. The tear | 120 to 520 | the stub turns on its end-side top corner, 0 to 5 degrees, so the start side opens first; it drops 3 |
| 3. The fall | 520 to 1140 | the stub turns to 20 degrees, falls 250 and fades out. **Not clipped:** it is drawn above the card's parent |
| 4. Back | after 1,500 | a new stub rises 34 into place and fades in, 300 ms; `ticket-land` plays |

- `TR4` is step 2 at its end.
- Reduced motion: the holes appear together, the stub fades out and in; no rotation or fall.
- In left-to-right languages the hinge is on the other side.
- **Inviting the tap:** none added. The stub is a second piece of paper with its own shadow line now; that is the affordance. The screen-reader action `ticket.tear_stub` stays.
