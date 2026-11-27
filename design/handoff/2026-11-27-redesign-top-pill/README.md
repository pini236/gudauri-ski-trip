# Handoff: the top pill on the mountain (P.25)

Status: **waiting for Pini** (rule 7). Nothing built. Canvas page "הפס העליון: רכבלים ומזג אוויר (27.11)" (`toppill`), boards `TP00`, `TP1`-`TP7`. Source `design/redesign/tools/tp_boards.py`.

**Why:** Pini: "23 ימים לפתיחה · -8°" is dry text. Wanted: beautiful, experiential, informative; in season lifts open of total with a lift icon; a pre-season state.

## The pill

Top 48, from the avatar (left 64) to 12 from the edge, height 52, radius 26, glass, padding 0 6. Two buttons split by a 1 px `line` divider (12 inset top and bottom):

- **Lifts (fills the space):** a 38 ring (track `line`, stroke 3.2, round caps) with the gondola icon (18) inside. Green arc = open / total; amber `#F0A020` arc after it = lifts held by wind. Text: "11 מתוך 14" (16.5 / 700, "מתוך 14" 13 / 600 `ink2`), under it 12 `ink2`. One ring, not a mark per lift, so a resort with 40 lifts looks the same. Tap: the lifts sheet.
- **Weather (hugs content):** a 30 circle with the condition icon (sun, snow, wind, moon; 18), the temperature 16.5 / 700, under it one line, priority: fresh snow (`snow24` ≥ 5: "18 ס״מ שלג טרי", a soft pulse ring around the flake, morning only), then gusts (> 50 km/h: "משבים 65"), then the condition word. Tap: the forecast.

## States (`TP7` shows all)

| | Lifts | Weather |
|---|---|---|
| TP1 in season | "11 מתוך 14" / "רכבלים פתוחים", green 11/14 | sun, "−7°", "שמש" |
| TP2 wind | "9 מתוך 14" / "2 עצרו ברוח" (amber), green + amber | wind, "−9°", "משבים 65" |
| TP3 after snow | "14 מתוך 14" / "כל הרכבלים פתוחים" | flake with pulse, "−11°", "18 ס״מ שלג טרי" |
| TP4 evening | "סגורים" / "נפתחים מחר ב-09:00", empty ring | moon, "−14°" |
| TP5 no live data | "רכבלים" / "אין מידע עדכני", empty ring | as now |
| TP6 pre-season | the wait ring (`acc`, fills from MTA's announcement to opening) with "23" inside, "ימים לפתיחה" / "הרכבלים עולים ב-20.12" | flake, "−8°", "שלג בדרך · 20 ס״מ" (7-day `daily.snow` sum) |

## Data

- Lifts: `/api/status` `lifts: {open, reason}`; live only from December (MTA parser, S-20); until then and when older than 30 minutes, TP5. "עצרו ברוח" only from a live `reason`; the forecast risk (`ridge.risk`) never goes into the pill (it stays in the lifts sheet as an estimate). The in-season source decision is with the server session and the architect; no contract change here.
- Weather: `/api/weather` as in `server/CONTRACT.md` (freshness rules unchanged).

## Questions for Pini (on `TP00`)

1. One pill, two tappable parts (yes). 2. A filling ring instead of a dry number, green open, amber wind (yes). 3. **Pre-season, the pill replaces the countdown tile `CT`** (same ring, number inside, no separate tile): fewer things on the mountain; asked because `CT` was approved (yes). 4. No live data: "אין מידע עדכני", never a forecast guess in the pill (yes).

## Strings (to content after approval)

New: "רכבלים פתוחים", "כל הרכבלים פתוחים", "{n} עצרו ברוח", "סגורים", "נפתחים מחר ב-{time}", "אין מידע עדכני" (exists?), "ימים לפתיחה" (exists in CT), "הרכבלים עולים ב-{date}" (exists in CT), "{n} ס״מ שלג טרי", "משבים {n}", "שלג בדרך · {n} ס״מ", condition words. Screen reader: "{open} מתוך {total} רכבלים פתוחים", "מזג האוויר, {temp}".

Checked in the design tool only, not on a phone.
