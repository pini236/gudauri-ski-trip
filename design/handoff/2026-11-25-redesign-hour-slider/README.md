# The hour slider, light states through the day, and weather on the mountain

Product designer (Sonnet), 25.11.2026, for Pini (via the manager session). **Design only; waits for Pini's approval (CLAUDE.md rule 7). Nothing here is built, and nothing is handed to an implementer yet.**

Canvas: https://claude.ai/artifact/8NrAKy97KWRj3JWcxx41yQ (version 72), page "שעת היום, מצבי אור ומזג אוויר". Boards (codes at the end of the titles): `HR00`, `HR1` to `HR4`, `HL00`, `WX00`, `WX1` to `WX3`. Tool: `design/redesign/tools/hr_boards.py` (writes the boards and the page into `canvas.json`).

## 1. Why Pini cannot find the slider

`LiveMountain.kt` has the hour slider (7:00 to 23:30, moves the sun along 12 Jan, picture and live). After the 24.11 review the top holds only the pill and "me"; the slider's only entry is a test row in "me" that exists in the **debug build** only (`if (BuildConfig.DEBUG) TestRow(...)`). In the store build there is no way to open it. It is a missing entry, not a bug.

## 2. Where it lives (board `HR00`, phones `HR1` to `HR4`)

**Revised 26.11.2026 after Pini's note** (the first strip was ugly and took too much room: a 76px bar above the bottom bar, with a number scale, ticks and a separate return button, and it pushed the column up).

A (recommended): a round "light of the day" button in the button column, under the picture/live switch (sun by day, moon by night, 44). One tap and **the button itself grows in place into a glass pill, 44 high**, across the screen at the button's row: nothing else moves and nothing is added at the bottom. The track is the sky of the day (night blue, pink dawn, blue day, golden hour, violet dusk) with one disc on it; no numbers and no ticks. The chosen hour sits at the pill's end in the selection colour (tap it to close); a small return arrow at the other end shows only when the light is not on now. Closed, the column button shows the hour in the selection colour.

B: a time row inside the "ההר עכשיו" weather tab (the same 44 track), opened from the pill. No new button, but two taps, and the sheet dims the mountain so the light cannot be watched while moving it.

Rules for both: the mountain stays under every layer; the day/night switch of the screen follows the light; time runs left to right; no explanatory text; screen reader: "אור היום", value in hours and minutes, half-hour steps; opens on now every time (not saved); picture steps between light states (450 ms cross-fade), live is continuous. No server, data or measurement change. A screen-reader string for the button (`app.sr_light` or similar) goes to the content session when approved.

Questions for Pini: 1 where (recommend A); 2 closing keeps the hour (recommend yes); 3 range 7:00 to 23:30, today only (recommend yes).

## 3. Light states (board `HL00`)

Existing (`site/data/peak.json`, `site/img/peak/kobi/ba64acea9a/`, `app/android/.../assets/redesign/img|village`): seven keys (dawn, morning, noon, after, sunset, blue, night) for the front view (hero 1,185KB + lens 315KB + village 172KB = 1,673KB packed, ceiling 1.75MB) and for Kobi (7 images, 327KB, downloaded, ceiling 350KB). `PeakLight.pick` chooses by sun height and direction, not by the clock.

Measured with `peak.sun()` for 12.1.2027 at Gudauri:
- the sun is up from about 8:40 to 17:35; the nearest picture is off by up to **20°** (14:30), and 13° to 15° between 11:30 and 12:00 and between 14:00 and 15:00: the shadow jumps;
- 8:00 to 8:50 shows the **evening** blue picture (sun in the west) though the morning light is in the east; before 8:00 it is night;
- 19:00 to 23:30 is one night picture (fine).

Proposal: four new states (first light 8:25, late morning 11:55, early afternoon 14:20, golden hour 16:50). Maximum off-sun drops from 20° to 10°, mean from 7.7° to 4.4°. The live model needs nothing (light and shadow are computed).

Weight: the four new hero pictures were rendered with the existing painter (`design/redesign/tools/peak.py`, colours interpolated between neighbours; candidates in `candidates/`, not final art): **85, 174, 170, 166KB = 595KB** (webp q80, 1170 by 2532, measured). They do not fit the packed set (77KB free), so they download from the site like the Kobi set. With lens, village and Kobi pictures the total is about 1,075KB (estimate, not rendered); on the phone Kobi 327 + 1,075 is about 1.4MB, under the 2MB cap. Alternative not tested: re-encode the seven at 2x pixels to free about 400KB.

**Architect review needed before code:** a downloaded front set in `peak.json` (additive, `packaged: false`, same `rev` rule); the per-set cap (350KB to 600KB hero-only or about 1.1MB all views); independent images (an image not yet downloaded falls back to the nearest packed key; no all-or-nothing as for Kobi); `peak-projection.json` unchanged (projection identical).

Questions: 4 add the four states (recommend yes, hero only first); 5 download, not pack (recommend yes); 6 morning "first light" instead of the evening blue (recommend yes, part of 4).

## 4. Weather on the mountain (board `WX00`, phones `WX1` to `WX3`)

**Corrected 26.11.2026 after Pini's note: the weather design already exists.** Canvas page "סבב 3", board `K03` ("ההר נראה כמו היום שבחוץ"): four states (fresh snow, ridge wind, sea of cloud, white day with falling snow), each with its data source, approved with round 3 (decision 83: weather on the picture, second stage). The four painted pictures exist in `design/redesign/assets/pk-hero-*` (fresh, wind, dawn-cloud, noon-storm), and `S05` already uses `morning-wind`. They are **not in the app**: only the seven light pictures are packed, and each weather picture exists for one light only. My first version of the board presented this as a new proposal; it now says so and only covers what is left: how to ship K03.

Proposal: **layers over the normal picture of the hour, not a painting per combination** (11 lights by 4 weathers is about 44 pictures, about 7MB). Per K03 state: fresh snow (`snow24` over 10 cm; rock mask about 25KB), ridge wind (gusts at sadzele over 50 km/h, `dir`; about 10KB), sea of cloud (needs `cloud_base`, not in the answer today: K03 already marked it as a question for the architect; mask about 20KB from the `depth` table in `peak.json`), white day with falling snow (`code`, `vis`; colour grade plus particles in code; a true flat picture about 150KB, optional). My addition: sky clouds by `code` and fog by height (about 40KB). Total about 100KB of assets: does not fit the packed set (77KB free of 1.75MB), so download like Kobi or raise the ceiling.

Order: stage 1 picture (fresh snow, ridge wind, white day and falling snow; data already in `/api/weather`, no server change); stage 2 picture (sea of cloud, after `cloud_base`); stage 3 live model (shader; separate work).

Rules (as in K03): no guessing. Weather follows the slider hour inside the 72 forecast hours; for past hours the layers fade out and the past part of the track is dimmed (missing data, never drawn as clear); 12 to 48 hours old keeps the layers and the pill says it is an old forecast (U06); over 48 hours or no forecast: the normal picture of the hour; it is a forecast, not a measurement (Open-Meteo credit stays); pause when the screen is not visible, in power saving, in reduce-motion (static haze instead of flakes), and in "live for a moment".

**Architect review needed before code:** `cloud_base` in `/api/weather` (additive; fixture `tools/fixtures/weather-m6.json`; server session); new assets in `peak.json` (masks, with `rev`) and packed versus downloaded; per-frame cost, battery and heat of particles on picture mode (30 fps on a mid-range phone) and one weather layer hook in `Stage` for both implementations; memory (up to three small bitmaps over the "two decoded pictures" rule). No privacy or measurement change.

Questions: 7 K03 shipped as layers (recommend yes); 8 order, stage 1 without the server (recommend yes); 9 past hours fade out (recommend yes).

## Honesty

Boards were rendered locally in a browser (no phone). Example values (hour now, temperatures, lifts) are made up for the mockups. The "A" and "B" phones show where the control lives, not final graphics. `HR3` (live mode) shows the live picture asset of the canvas; in `ref/hour-slider-HR1-HR4.jpg` it is a local stand-in. The four light pictures are candidates; their weights are measured, but lens, village and Kobi weights are estimates. Nothing was tested on a phone. Whether the bar ("spectacular") is reached for the new light and weather art needs Pini's eye; if the interpolated pictures or the cloud layers are not enough, a stronger model (or a painter's pass) would be needed and that is Pini's call (CLAUDE.md, "raise a flag").

## Files

- `ref/hour-slider-HR1-HR4.jpg`, `ref/weather-WX1-WX3.jpg`: reference renders of the phones.
- `candidates/hero-{first,late,early,golden}.webp`: the four candidate pictures (1170 by 2532, front view).
