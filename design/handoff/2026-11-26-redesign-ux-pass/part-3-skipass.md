# Part 3: the ski pass (item 5). Sonnet

Boards `SP1`, `SP2` (`ref/SP*.jpg`). **Approved (Pini, 27.11.2026; recorded in `docs/STATUS.md` J.7 and `docs/DECISIONS.md` by the manager session). Build.** The calculation and the data do not change (`skipass/Prices`, `site/data/prices.json`); base spec: `design/handoff/2026-11-21-redesign-around/README.md`, section 7.

## What changes

- **The big price over the mountain is removed.** The price is printed on a ski pass that hangs from a lanyard: an object, not a number in the sky.
- **The paragraphs are removed.** Three short rows with an icon and a value replace `app.skipass_card`, `app.skipass_foreign`, `app.skipass_night` and `app.skipass_buy`.
- The minus / plus stepper is removed; the day tiles are the control.

## 1. The pass (on the veiled mountain)

- **Lanyard:** two bands, 7 wide, `acc` (the second at 82%), from above the screen top (x 150 and x 262) down to the slot of the pass.
- **The pass:** 232 x 316, radius 22, paper `paper`, shadow `0 18 34 rgba(10,25,45,.34)`, turned 3 degrees around its top centre, 136 from the top, centred. Our own drawing; not MTA's card.
- On it, centred, top to bottom: the slot (46 x 10, radius 5); "גודאורי · עונת 2026/27" (11.5 / 700 `ink2`); the price (76 / 300, the currency sign 30); "4 ימים · מבוגר" (17 / 700); with a trip and unchanged days, "11–14.1 · לפי הטיול שלי" (13 `ink2`); when it is cheaper than day passes, a chip "חיסכון של 90 ₾ לעומת כרטיסי יום" (height 26, `#DDF2E5` / `#0F5C31`; night `rgba(70,208,138,.2)` / `#7BE3AC`); at the bottom a band 74 high in `acc` with a two-layer mountain silhouette in white 22% and 18%.
- The price changes with a 150 ms cross-fade; on a change of days the pass swings once (3 to 5 to 3 degrees, 300 ms; reduced motion: no swing).

## 2. The sheet

Bottom sheet, padding 10 / 16 / 14, gap 12:

1. Adult / child 6 to 12 (the existing two-way selector, height 44).
2. "כמה ימי סקי?" (12.5 / 700 `ink2`) and six tiles, 1 to 6: height 56, radius 16, the number 17 / 700 and the price 11 under it; the chosen one `acc`. Seven days and more: the row scrolls, as today, and `app.skipass_named` appears under it.
3. Three rows, height 44, divider 1 `line`, icon 19 `ink2`, text 14, value 13.5 `ink2` at the end:
   - pin · "קונים בקופה שבכפר" · link "איפה הקופה?" in `acc` 700: opens the village picture with the ticket-office pin marked (part 4's backdrop).
   - card · "פיקדון על הכרטיס" · "5 ₾, מוחזר" (`card_deposit`).
   - moon · "סקי לילה, בנפרד" · "80 ₾ לערב" (`night`).
4. One footnote, 11.5 `ink2`, centred: "מחיר לתיירים, כולל מע״מ · MTA · נבדק 15.11.2026" (`audience`, `vat`, `src`, `checked`).

The link to the SKIPASS store app is dropped from this screen (buying is at the office; the app link was the least used line and the most text). If Pini wants it back: a fourth row.

## 3. States (unchanged rules, new places)

| State | What shows |
|---|---|
| Two tickets are cheaper | under the "who" line on the pass: `app.skipass_two_tickets`, 12.5 |
| More than 14 days | the pass shows `app.skipass_too_long` instead of the price |
| After `until` | the pass shows `app.skipass_no_info`; no tiles; the footnote becomes `app.skipass_check_site` |
| Under 6 | a fourth row: `app.skipass_child_free` |
| No trip | starts at one day, no dates line (`SP2`) |

Top: the round back button and "כרטיס סקי" beside it (21 / 700).
