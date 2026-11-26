# Buttons without words (B10): the name on a long press

**When:** 26.11.2026, product designer (Opus). Design only, no code.
**Status:** waiting for Pini. From `design/redesign/review-release.md` B10.
**Canvas:** https://claude.ai/artifact/8NrAKy97KWRj3JWcxx41yQ, page "כפתורים בלי מילים (ב10)", boards `BT00` to `BT4`. Source: `design/redesign/tools/b10_boards.py`.
**Builds on:** `design/handoff/2026-11-26-redesign-hints/` (hints 3, 5 and 6 already name record, the picture/live switch and "אני").

## What is left after the hints

| Button | What explains it | Missing |
|---|---|---|
| Record | hint 3 | nothing |
| Picture / live switch | hint 5, and the Z03 pill | nothing |
| "אני" | hint 6 | nothing |
| Layers | a tap opens the lens chips, which have names | only its name before the tap |
| "איפה אני" | the first tap asks for the system location permission | only its name before the tap |
| Compass | appears only after rotating; returns to the opening view (S08) | only its name |

**Recommendation (`BT1` to `BT3`):** a long press on any icon-only button on the mountain shows its name in a small pill. No permanent labels, no more hints. Alternatives rejected in `BT00`: a permanent word beside each button (`BT4`: unframed text on the mountain, against the B9 rule) and two more hints (eight hints need four openings; both buttons explain themselves on the first tap).

**If Pini wants nothing more:** the hints alone are enough for the release. This is not a blocker.

## Spec

- **Which buttons:** both halves of the switch, layers, "איפה אני", the compass, record, "אני". Any later column button (for example the hour button on the canvas page "שעת היום", HR) gets it too.
- **Trigger:** the system long press (about 0.5 s), one short haptic tick. The button's own action does **not** run. The pill stays while the finger is down, and 1.5 s after it lifts.
- **Pill:** height 32, radius 16, background `ink`, text `bg`, 13.5 weight 600, padding 0 12, shadow `0 6 16 rgba(10,25,45,.28)`. 10 from the button toward the screen centre, centred on the button's height. A button in the bottom bar or "אני": above or below it, same gap. At least 12 from every screen edge.
- **Press:** the button shows its normal pressed state (a disc 8 larger, `ink` 16%; at night `#EAF1F8` 22%).
- **Motion:** in 150 ms (scale from 0.92 and fade), out 150 ms. Reduced motion: appear and disappear only.
- **Screen reader:** unchanged; every button already has its `contentDescription`, the pill is not announced.
- **With the hints:** an open hint closes on any touch, including a long press. A long press does not mark a hint seen (the button was not used).
- **Left to right:** the column is on the right and the pill on its left (`BT3`).
- **Android:** a `Modifier` on the existing icon buttons (`combinedClickable` with `onLongClick`, or Material 3 `PlainTooltip` styled as above). iOS: the same when the redesign reaches it (`.contextMenu` is not this; use a long-press gesture with the same pill).

## Strings

None new. Existing keys: `app.map_layers_a11y` (שכבות), `loc.button` (איפה אני), `app.mode_photo` (תמונה), `app.mode_live` (חי), `app.rec_sign` (הקלטת גלישה), `app.pass_me` (אני), and for the compass `map.compass`.

## Honestly

Drawn in the design tool on the RX3 / HN screen (390 × 844), not on a phone. No contract, data or measurement change, so no architect review.
