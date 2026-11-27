# Handoff: the ticket office buttons (P.21) and the recording mode (P.16)

Status: **waiting for Pini** (rule 7). Nothing built. Canvas page "כרטיס הסקי: הקופה, ומצב ההקלטה (27.11)" (`small2711`), boards `SM00`, `SK1`, `SK2`, `RM1`-`RM4`. Source `design/redesign/tools/sm_boards.py`.

## P.21: the ski pass "איפה הקופה?" (`LiveSkipass.kt`, boards `SK1`, `SK2`)

- The row "קונים בקופה שבכפר" ends with two round buttons instead of "איפה הקופה? ›" (the village map no longer opens from here). Each 40 round (44 touch target), `acc` at 13% with an `acc` icon (19), 8 apart.
- **Navigation (arrow, first at the start side):** opens Google Maps walking directions to the ticket office: `https://www.google.com/maps/dir/?api=1&destination=42.470828,44.487555&travelmode=walking` ("Ski Pass Office", OSM, medium; `research/places.md`; confirm on site in January; alternative "Gondola Tickets" 42.471311, 44.492023). Not Waze. Screen reader: "ניווט לקופה".
- **App (phone):** opens SKIPASS by Lemondo if installed (Android `com.lemondo.skipass`; iOS App Store `id6698866856`), else its store page. Screen reader: `app.skipass_app`.
- No explanation text; a long press shows the name in the B10 pill (`NameHold.kt`), `SK2`.
- P.20 (the sheet covering the pass) is a separate layout fix; nothing here changes the sheet height.

## P.16: "כל היום / ירידה אחת" (`LiveDay.kt` 260-273, `DayStart.kt` 84; boards `RM1`-`RM4`)

- **Before the start only:** the start sheet has two tiles side by side (radius 18, border 1.5 `line`; the chosen one `acc` border and 8% `acc` fill, `aria-pressed`): icon (22), name 16 / 700, one line 12.5 `ink2`: "כל היום" / "עד שעוצרים", "ירידה אחת" / "נעצר לבד בתחתית". "כל היום" is preselected. Then "מתחילים" and "לא עכשיו".
- **While recording:** the mode is a fixed label (26 high, `line` background, icon 14) at the end of the recording pill, beside the time. No switch, no `onSwitch` while recording. To change the mode: finish and start again.
- This also closes the open choice of version 54 (`LV` boards): "ירידה אחת" stays in the start sheet, it does not move to the run sheet.

## Strings (to content after approval)

New: "עד שעוצרים", "נעצר לבד בתחתית", "ניווט לקופה" (screen reader). Existing: "כל היום", "ירידה אחת", "מתחילים", "לא עכשיו", `app.skipass_app`.

Checked in the design tool only, not on a phone.
