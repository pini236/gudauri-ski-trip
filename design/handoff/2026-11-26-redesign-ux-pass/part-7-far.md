# Part 7: far from the mountain (item 10, with bug B2). **Opus**

Boards `NM1`-`NM3` (`ref/NM*.jpg`). **Not before Pini approves.**

**Why Opus:** it touches the location layer on the live model and the picture (`LiveWhere.kt`), the camera follow of the ski day (`LiveDay.kt:160`, `LiveView.follow`), terrain height outside the grid (`data/Terrain.kt:23`) and the recorder's start path (`MainActivity.recordNow`, the recorder service). If the service's start contract changes, check with the architect first.

## The rule

**No dot is ever drawn outside the model, and the camera never follows a point outside it.** "Outside" is what `Locator.feed` already decides (`Fix.Outside`: the point is not inside the terrain model's box). Instead of a dot, the app says how far Gudauri is and in which direction.

## 1. "איפה אני" when outside (`NM1`, `NM3`)

Replaces the box "לא בגודאורי כרגע".

- **The card:** under the top pill (106 from the top, 12 from the sides), glass, radius 22, min height 60. At the start a 44 circle (`tint`) with an arrow (`acc`, 22); beside it "גודאורי · 1,430 ק״מ מכאן" (16 / 700) and under it "המיקום שלך יופיע כשתהיו על ההר" (13 `ink2`).
- **Distance:** great-circle, from the fix to the resort's centre (the Goodaura bottom station). Under 10 km: one decimal ("3.2"); from 10: whole, with a thousands separator. In kilometres in all four languages.
- **The arrow:** points to Gudauri. With a compass heading: relative to where the phone points, updated live. Without: relative to north (up = north).
- **Shown** when the tap on "איפה אני" gets an outside fix; stays 6 s, or until a touch on the mountain. The button returns to its off state (it does not stay blue). Tapping again shows it again.
- **Never:** a dot, a centring of the view, or a position clamped to the model's edge. `WhereDot` already draws nothing; keep it so in both the picture and the live model.
- While waiting for the first fix, and with a weak fix: the existing states, unchanged.
- Screen reader: the card is announced once: "גודאורי במרחק 1,430 קילומטר".

## 2. Recording when outside (`NM2`)

Today `recordNow` starts from anywhere with no check.

- **A fix from the last 2 minutes is outside:** the tap on record does not start. A bottom sheet opens (the mountain under a veil `rgba(8,16,30,.28)`): a 56 circle with the arrow; "גודאורי · 1,430 ק״מ מכאן" (21 / 700); "מקליטים גלישה כשנמצאים על ההר" (13.5 `ink2`); a primary button "לראות יום גלישה לדוגמה" (opens the sample day); a text button in `acc`, height 44, "להקליט בכל זאת".
- **No recent fix:** recording starts as today (the pulsing search). If the first fix is outside, the same sheet rises over the search screen; nothing has been saved yet. Dismissing it (drag, back) cancels the recording.
- **"להקליט בכל זאת"** (needed for the car calibration and for testers): recording runs as today, with three differences on the live screen:
  1. **The camera does not follow.** The mountain stays in the opening view.
  2. **No dot and no line on the mountain** while the points are outside the model.
  3. The top pill carries the existing `app.rec_outside` ("מחוץ לאתר הסקי"); speed, distance and time work as usual; no run or lift names (as today).
  When a point enters the model, the normal follow, dot and line start, with the usual 600 ms camera move.
- `IdleWatch` (auto stop after 20 minutes without movement outside the area) is unchanged.
- The permission prompts and the store's foreground-service flow do not change: the sheet comes before the service starts.

## 3. Bug B2 (the marker that sticks to an unrelated place)

The design answer is the rule at the top. In code, for the implementer to confirm:

- `LiveDay` projects the raw position, and the terrain height is clamped to the grid edge, so a far point lands on the model's rim. Skip drawing and following for any point outside the model's box.
- Check the picture mode too (`PictureStage`): a point outside the picture's projection table must not be drawn at the table's edge.
- Add unit tests: a fix 1 km and 1,000 km outside gives no dot, no follow, and the card's distance within 1%.

## 4. Languages

The four strings are in `strings.md`. The number and "ק״מ" are isolated in direction; in left-to-right languages the card mirrors and the arrow does not (it is a real direction).
