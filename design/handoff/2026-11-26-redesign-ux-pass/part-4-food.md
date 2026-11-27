# Part 4: food and drink (item 6). Sonnet

Boards `FD1`-`FD3` (`ref/FD*.jpg`). **Approved (Pini, 27.11.2026; recorded in `docs/STATUS.md` J.7 and `docs/DECISIONS.md` by the manager session). Build.** Build after part 1 (the tile). Data: `site/data/places.json`, already read by the app (`SiteData`); nothing new in the file.

## What changes

Today "אוכל ושתייה" opens the Goodaura lift panel. It must open the places: their names, what each is, where it is, and a way to get there.

## 1. The screen

- **Entry:** the menu tile; "איפה הקופה?" in the ski pass (same backdrop, the ticket pin marked, the sheet low). Tapping the village on the mountain is not built today; when it is, it opens this screen.
- **Top:** a round glass back button (44) at the start. Back returns to where the user came from.
- **Two tabs** (the existing two-way selector): "בכפר · {n}" and "על ההר · {n}". Only kinds `food` and `cafe`. A place that appears twice in the data under the same name is shown once.
  - **In the village:** places whose point falls inside the village picture. Backdrop: the village picture (`Village`), in the light of the hour.
  - **On the mountain:** all others (this includes the lower village, which is outside the picture). Backdrop: the mountain picture, moved up 150 so the pins sit above the sheet; a veil `rgba(40,62,110,.14)`.
- **Pins:** a 26 circle, white border 2, an icon 14: `food` `#C2540A` with fork and knife, `cafe` `#7A4A2B` with a cup (the colours approved in round 31). Pins closer than 24 move apart (drawing only). The ticket-office and parking pins of the village stay, as today.
- **The sheet:** height 452 in the village, 396 on the mountain; the list scrolls inside. Title "אוכל ושתייה" (21 / 700), the tabs, then rows.
- **A row** (height 58, divider 1 `line`): a 36 circle in the kind's colour with its icon (19, white); the name (15.5 / 700, always left to right, as in the data); under it 12.5 `ink2`: the kind and where; a chevron at the end.
  - Kind: `places.kind_food`, `places.kind_cafe`, with today's text ("אוכל", "בית קפה"): `food` also covers fast food.
  - Where, in the village: "{m} מטר מ-{lift}" (the nearest station that lists the place, rounded to 10). On the mountain: "ליד התחנה העליונה של {lift}" / "התחתונה". A place on a run (`slope` with a `liftId`): "על המסלול, ליד הרכבל {lift}" (the lift of `liftId`; the data has no run name for a place).
  - Order: village by distance; mountain from the top down.

## 2. One place (`FD2`)

A tap on a row or a pin:

- The pin grows to 40 with a ring 3 `acc`; a name tag (height 26) 38 above it.
- **The sheet keeps its height.** Its head becomes the place: the kind's circle (44), the name (21 / 700), the kind, where, and for `seasonal` "פתוח רק בעונה" (`places.seasonal`); an X (44) at the end.
- Two buttons: "ניווט" (primary, with an arrow icon): opens the phone's maps app at the place's coordinates (`geo:` intent). "שיתוף" (secondary): the system share sheet with the name and a maps link.
- Under them "עוד בכפר" (12.5 / 700 `ink2`) and the list continues.
- X, back, or a tap on the picture returns to the list.

## 3. States

- No places for a tab: the tab shows 0 and the list says `places.none`. No places at all: the menu tile is not shown.
- Capability `places` off: no tile.
- One footer line at the end of the list, 11.5 `ink2`: `places.check` and the data credit (`attribution`).
- No hours, phone, menu or rating: the data has none. Nothing is invented.

## 4. For the ski expert (data, not blocking)

`Dunkin' Donuts` appears twice (`n11103888462`, `w1424986577`). The screen hides the duplicate by name; the data should keep one.
