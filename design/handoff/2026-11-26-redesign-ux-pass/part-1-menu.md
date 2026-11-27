# Part 1: the menu (items 1, 2). Sonnet

Boards `MN1`-`MN6` (`ref/MN*.jpg`). Read `README.md` here first. **Not before Pini approves.**

## 1. The bar (`MN1`)

`Dock` (`redesign/LiveShelf.kt`): the first button changes from the house + "הכפר" to a grid icon + "תפריט" (`app.dock_menu`).

- Height 44, radius 22, **filled**: background `ink`, text and icon `bg`, 15 / 700, padding 13 start and 15 end, gap 7. Icon: four rounded squares, 19, stroke 2.
- The search pill and the record button do not change.
- Tap opens the menu sheet. Long press shows the name pill, as today (B10).

## 2. The sheet (`MN2`-`MN5`)

A bottom sheet over the mountain, full width, top corners 28, padding 10 / 14 / 16, gap 10, height by its content (about 557 of 844). It replaces `VillageSheet`.

- Opens in 250 ms (reduced motion: at once). **No camera flight.** The mountain stays where it was, under a veil: `rgba(40,62,110,.22)` by day, `rgba(2,6,18,.36)` at night.
- The dock and the button column hide while it is open; the top pill and "אני" stay.
- Closes on drag down, system back, or a tap on the mountain. No title inside the sheet (the screen reader names it `app.dock_menu`).

Top to bottom: handle; the trip tile; a grid of six tiles (two columns, gap 10); a footer of three buttons.

**The trip tile** (full width, height 112, radius 22, paper `paper`, shadow `0 6 18 rgba(10,25,45,.16)`): a small boarding pass. A stub 112 wide at the end side, separated by a dashed line 2 `line` with a notch of radius 9 at its top and bottom.

| State | Main part | Stub |
|---|---|---|
| No trip | "הטיול שלי" (12.5 / 700 `ink2`), "מתי טסים?" (21 / 700), a chip "+ הוספת טיסה" (height 28, `acc`) | a plane, 30, `ink2` |
| Before the flight | "הטיול שלי", the two airport codes with the plane between (25 / 300), "10–15.1 · 4 ימי סקי" (13 `ink2`) | the number (44 / 300) and "ימים לטיסה" (12.5 / 700), by the same stage rules as the pass stub |
| Ski days | the same | the ski day number and "יום סקי" |
| After the return | the same | no number; a check mark |

**A tile** (height 104, radius 22, background `tile`, border 1 `line`, padding 12 / 14): icon in a 36 circle (`tint`, icon `acc`, 20) at the top start; an optional slot at the top end; the title 16 / 700; one status line 12.5 `ink2`, one line, cut with an ellipsis.

| Tile | Title | Status line | End slot | Opens |
|---|---|---|---|---|
| Ski pass | כרטיס סקי (`app.skipass_title`) | with a trip `app.vill_pass_trip`, else `app.vill_pass_day`; after `until`: `weather.none` | | the ski pass (part 3) |
| Group | הקבוצה שלי | today's meetup `app.vill_grp_meet`; a group: its name and member count; several: `app.vill_grp_n`; pending: "ממתין לאישור המנהל"; none: "מזמינים את החברים לטיול" | up to four avatars, 28 | as today: gate / group / "הקבוצות שלי" |
| Food | אוכל ושתייה | "{n} מקומות, בכפר ועל ההר" (food and cafes in `places.json`) | | food and drink (part 4) |
| Games | משחקים | "{n} משחקים · גם בלי קליטה" | three game thumbnails, 28 | games (part 6) |
| My days | הימים שלי (`app.days_title`) | "{n} ימים · {km} ק״מ"; none: "יום גלישה לדוגמה" | | "הימים שלי" (with no days: the sample day) |
| Lifts | רכבלים ומזג אוויר | a report: "{open} מתוך {total} פתוחים · {temp}"; before the season: "נפתחים ב-{date} · עוד {n} ימים"; no recent report: `status.bar_no_recent` | | the lifts sheet (what the top pill opens) |

A tile whose capability is off for the resort (`has(...)`) is not shown; the grid closes the gap.

**Footer:** three buttons, height 44, radius 22, border 1 `line`, 13.5 / 500, icon 18: "חשבון" (the avatar when signed in) opens "אני"; "הגדרות"; the current language name opens the language sheet.

## 3. States

- **Group tile while groups load:** show the last list saved on the phone (part 5). First ever load: a 90 x 12 skeleton bar in place of the status line. Never "none" while loading.
- **Offline:** tiles work from saved data; the lifts tile says `app.now_offline`.
- **While recording:** the menu opens as usual; recording continues.

## 4. The village

The village picture (`Village`, `LiveVillage.kt`) stays, without its sheet rows; it becomes the backdrop of "אוכל ושתייה" (part 4). Until part 4 is built, the food tile opens the existing `PlacesSheet` of the Goodaura bottom station (not the lift panel). "חזרה להר" is replaced by the back button of part 4.

## 5. Android, languages, accessibility

- System back closes the sheet. Sits above the gesture area.
- Left to right: mirrored; the stub moves to the other end (`MN5`).
- Screen reader: the sheet is "תפריט"; each tile is one item, "title, status".
- Large text: tiles grow to 124 and the status wraps to two lines; from font scale 1.3 the grid is one column and the sheet scrolls inside.
- Touch targets: every tile and footer button is at least 44.

## 6. Hints

- Mountain sequence, step 4: the target is the menu button, the text `app.hint_menu` (`MN6`). `app.hint_village` is retired.
- The two village hints (`app.hint_trip`, `app.hint_group`) move to the menu: the first time it opens, on the trip tile and on the group tile. Prefs key `hints_village` becomes `hints_menu` (a user who finished the village sequence does not see them again).

## 7. Removed

`VillageSheet` and its rows, `app.dock_village` in the dock, the 700 ms flight to the village from the dock.
