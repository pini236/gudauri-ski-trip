# Day story and share, part 2 (P.12, P.15): builder report

Session `session_017QrNw8zSLvXvfUsWBFVG8Z`, branch `claude/android-story-share-2`, not merged. Gradle did not run here; CI only. Not tested on a phone.

## Built
1. **Camera glide** (DS2): `MomentWindow` takes `from` and `glide`; the player animates 0 to 1 over 300 ms (`StoryRules.GLIDE_MS`) on every moment change, window centre moves from the last moment's place to this one's, in both the painted-mountain and the flat variant. First moment and the finale just appear.
2. **Pin label**: dark pill with a tail on the moment's place (`drawPin`). Text from `StoryRules.pin`: steepest = `28°`, fastest = km/h number; other moments have no pin.
3. **DS5**: when the story has exactly one card (`StoryRules.solo`), it is the big card (window 362 x 210 over the words), then `WhereBar` ("לאן הלך היום") from the day's summary and segments, then the picture section. `DayStory` got `day` and `segs` parameters; `DayMountain` passes them.
4. **Picture preview 150 x 188** in the end section (beside stacked share/save) and in the finale. The picture is drawn once by `sharePicture` (extracted from `DayShare`) and handed to the share sheet (`pre`), so it is not drawn twice.
5. **In-card video** (`DayShare`): the view stays after the end (last frame); 44 pause/play circle, `0:25 / 1:00` pill (LTR isolate), 4 px progress line along the bottom edge (fills in reading direction), 72 replay circle at the end, tap on the card toggles or replays; screen-reader labels `ds_pause`, `ds_play`, `ds_again`. Link "לראות את היום על ההר" (`ds_see_mtn`, mountain icon) under the card in video mode; it closes the story and starts the day's film (`DayMountain`: `onSeeMountain`).
6. **Queued buttons**: `StoryRules.press` decides run / queue / ignore; the second button waits (acc border) and runs when the first finishes (done or failed). `withVideo` now keeps a waiter list, so a button pressed while the card's tap is still encoding no longer hangs.
7. **Saved**: `savePicture` / `saveVideo` return the `Uri`; a tap on the saved state opens it in the gallery (`ACTION_VIEW`); `haptics.click(.5f)` when saved.

## Not built
- The live preview in the card (frames drawn at once, without waiting for the encoder, DV1): the card still plays the encoded file after the first tap.
- Back-tap direction unchanged (reading-start half), as asked.

## Guesses (the handoff is silent)
- Ease for the glide: ease-out quartic, close to (0.2, 0, 0, 1).
- Pin text: only degrees (steepest) and km/h (fastest); the pin is a dark pill, 13 sp bold.
- Pause button top-start, clock top-end, progress along the bottom, replay glyph is the `↻` character on a 72 circle.
- Finale picture is 150 x 188 like the end section (DS3 says "big"); crop fills the box.
- Queued button: unchanged label with an `acc` border.
- DS5 window zoom (70 pts across) and the where-block placed right after the card.
- Preview drawn in `Crop`; the box shows a quiet tone while drawing.
- "Saved" tap opens by the content resolver's mime type.

## Touched outside the three files
`DayMountain.kt` call site only (passes `day`, `segs`, `onSeeMountain`).

## Check on a phone
Glide feel and RTL; pin position with and without the painted mountain; DS5 on a day with one moment (sample day gives several, so use a short real day); pause, scrub line and replay, large text; saved tap opens the gallery, haptic; press share then save (and the reverse) while the video encodes; preview picture crop; link to the mountain starts the film.
