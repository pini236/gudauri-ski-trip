# שפה עיצובית חדשה: ההנחיות לבניית הלוחות

פיני (2.10.2026): "עיצוב חדש לכל האתר: כמה גישות, קנבס מפורט, לעבור על כל הרכיבים ולהציע כמה אפשרויות לשפה עיצובית חדשה".

הקנבס: https://claude.ai/artifact/9yxrGgytb2Gmi4i1UqEhST (קנבס נפרד מהקנבס המשותף).
חמש שפות, `A` עד `E`, מוגדרות ב-`languages.json` (צבעים בהיר וכהה, גופנים, צבעי שיפוע וצבעי מפה). לכל שפה אותם 12 לוחות, כדי שאפשר יהיה להשוות: אותו לוח בכל שפה עומד באותו טור בקנבס.

This file is also the brief for the agents that draw each language. It is written mostly in English for them; the copy on the boards is Hebrew.

---

## 1. The project, in one minute

- A Hebrew, RTL, phone-first site for a ski trip to Gudauri, Georgia (flight 10.1.2027), now open to anyone: a real 3D/2D map of the runs and lifts, a "your trip" boarding pass with a countdown, a meeting-point planner, five small games, groups (shared flights, meetups, scores), settings. Four UI languages (Hebrew, English, Russian, Georgian).
- Today's look (screenshots in `design/language/shots/today/`): snowy light ground, Karantina display + IBM Plex Sans Hebrew, trail-sign boards with an arrow cut (clip-path) hanging off a dark pole, a 2°-tilted boarding pass with a tear-off stub, snow caps on boards, the real panorama from the village behind the home page.
- Core values: **accuracy first** (no invented geometry, "no data" is shown as such), legible in sun on snow, touch targets ≥ 44 px, no sideways scroll on phones.
- **Piste colours are semantic** (the official map): green, blue, red, black must stay recognisable as those four in every language (tones may differ; the black run in dark mode is drawn light). **Slope steps** (decision 10): ≤15°, 15°–25°, 25°–30°, >30°, four colours from the language's `slope` list, in that order.

## 2. Files and format (read carefully: mistakes fail silently)

- Write each board **directly with the Write tool** (no script that generates boards) to
  `design/language/canvas/project/<file>.dc.html` (flat, no sub-folder; every file name starts with your letter). File names exactly as in section 4.
- Do not touch anything else in the repo. Do not publish anything. Do not commit.
- Each board is one self-contained file in this exact skeleton:

```html
<!doctype html>
<html lang="he">
<head>
<meta charset="utf-8">
<title>A · דף הבית</title>
<script src="./support.js"></script>
</head>
<body>
<x-dc>
<helmet>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Heebo:wght@400;500;700;800;900&display=swap" rel="stylesheet">
<style>
body{margin:0}
a{color:#1460D2}a:hover{color:#0E1116}
</style>
</helmet>
<div dir="rtl" lang="he" style="width: 390px; height: 844px; box-sizing: border-box; overflow: hidden; font-family: 'Heebo', sans-serif; background: #F3F5F7; color: #0E1116">
  ...
</div>
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{"$preview":{"width":390,"height":844}}'>
class Component extends DCLogic {
  renderVals() { return {}; }
}
</script>
</body>
</html>
```

- Keep `<script src="./support.js"></script>` exactly. Close every non-void element; quote every attribute.
- **All styling inline** (`style="…"`), except `<helmet><style>` for `body{margin:0}`, link colours, and at most a few `@keyframes`/pattern helpers if truly needed. No classes-based stylesheet.
- No `{{holes}}`, no `<sc-for>`/`<sc-if>`, no JavaScript UI: static markup only. `renderVals()` returns `{}`. `data-props` only declares `$preview` (the board's width/height).
- Fonts only via Google Fonts `css2` `<link>` in `<helmet>` (only the families/weights you use). Images only by the `/_blob/…` URLs listed in section 6. Icons: inline stroke `<svg>` you draw (never emoji, never icon fonts).
- The root is fixed at the board's size: phones **390×844** (`overflow: hidden`, it is a screen), desktop **1280×800**; the sheets **1280 wide**, height as needed (state the final height in your report; keep `$preview` equal to it).
- Accessible as drawn: real `<button type="button">`, `<a href="#">`, `<input>` with `<label>`; `aria-label` on icon-only buttons; text contrast ≥ 4.5:1 (3:1 at 24 px+ bold). Yellow/light accents are fills, never small text on light grounds. `dir="ltr"` on Latin codes/numbers runs that need it (TLV, 6H 897, times).
- No fake phone status bar. No lorem ipsum: use the copy in section 5. No AI tropes (gradient washes as decoration, left-border accent cards, emoji).
- RTL: the reading start is the right edge. Back arrows point right ("→ בית"). In a sign/arrow that "goes to" a page, the arrow points left (forward in RTL), like today.

## 3. Your language

Read your entry in `design/language/languages.json`: `he`/`en` names, `idea`, `fonts`, `light`/`dark` tokens, `slope`, `map`. Use exactly those colours (you may add at most two tints of them for fills/hover). The short design brief per language is in section 7. Commit to it fully: the five languages must look clearly different from each other and from today's site. Every component on every board must be drawn in your language (no generic gray boxes).

## 4. The 12 boards (same for every language)

| file | size | what |
|---|---|---|
| `<L>0-Identity.dc.html` | 1280 × ~1000 | The language at a glance: big name (Hebrew name + English), the one-sentence idea (from `idea`), a hero piece of art in the language, the palette (light and dark swatches with token names and hex), type specimen (display + body + numbers, in Hebrew, and a line each in English, Russian `Горнолыжная поездка` and Georgian `გუდაური` with the fonts from `fonts.ru`/`fonts.ka`), the motif kit (3–5 recurring shapes/patterns/ornaments), icon style (6 icons: map, meet pin, games, group, settings gear, day/night), and the 4 piste colours + 4 slope colours in this language. |
| `<L>1-Core.dc.html` | 1280 × ~1500 | **Core components**, each labelled with a small caption (component name in Hebrew): top bar (phone: title + back; desktop: brand + nav with active item + countdown + Gudauri clock + day/night + settings); the four home "navigation" items for map / meet / games / group (the replacement for today's trail-sign boards on a pole); buttons (primary, secondary, ghost, danger, icon button; each ≥44 px; disabled; pressed); filter chips (ירוק, כחול, אדום, שחור, רכבלים, ללא שם: on/off); view switch (תלת-ממד / מבט על); tabs (טיסות, מפגשים, שיאים, חברים); toggle and a settings row; inputs (text, date, time, select, with labels; an error state); a note/hint; a toast ("הבחירה בוטלה · החזרה"); loading state ("טוען את האתר…"); empty state; the day/night control (אוטומטי / יום / לילה) with the Gudauri clock. Show light **and** a dark strip for at least buttons, chips and the top bar. |
| `<L>2-Domain.dc.html` | 1280 × ~1700 | **The project's own components**: the boarding pass (front: outbound with tear-off stub and countdown; plus the return card peeking behind; plus the torn-stub state); the empty pass ("הוספת הטיסה שלי"); a run title/sign for each of the four colours (e.g. Tatra 2 blue with ref 7A, Pirveli green, Kudebi 1 red, Sadzele 1 black); run stats block (אורך, גובה, ירידה, קטע תלול) + the elevation profile of Tatra 2 painted by slope + its slope legend; "מה מחכה לך" (start / steep part / end); confidence/source badge (ודאות: גבוהה); lift status: summary bar, a 4-row lift board (open / closed with reason), and the off-season "ההר עוד ישן" no-data state (today it is a board under snow: reinvent it); the meet card (station, day, time, countdown, "איך מגיעים", share buttons); a map pin and a station marker; a game card (one game); a group flight row; a scores row (1st/2nd/3rd); the account pass (ski pass); the official tally (5 ירוק, 16 כחול, 4 אדום, 2 שחור). |
| `<L>3-Home.dc.html` | 390 × 844 | Home, day, with the user's trip. Top: place + Gudauri time + day/night; the hero art (the real ridges, section 6, or the real panorama photo, in your style); the boarding pass with countdown; then the four navigation items (as far as fits; the screen is a cut of a scrolling page). |
| `<L>4-HomeNight.dc.html` | 390 × 844 | The same home at night, in your **dark** tokens: stars/moon/village lights in your style, "לילות לטיסה". |
| `<L>5-Map.dc.html` | 390 × 844 | The map page, top view: header (title "מפת מסלולים" + "→ בית"), filter chips row, view switch, the map (paste `snip/<L>-map.svg` verbatim inside a box, see section 6) with zoom buttons (+, −, fit) and the status pill "עוד אין מידע על הרכבלים", north arrow / scale bar in your style, then the start of the run list ("כל המסלולים"). |
| `<L>6-Run.dc.html` | 390 × 844 | Run view of Tatra 2: the map crop (paste `snip/<L>-run.svg`), the run title/sign with 7A, prev/next (→ Tatra 1, Kudebi 1 ←) + share, the stats, the profile painted by slope with the scrub readout (מההתחלה 0 מ׳ · גובה 2,665 מ׳ · שיפוע כאן 6°), the "טיסה במורד המסלול" button. |
| `<L>7-Meet.dc.html` | 390 × 844 | Meeting point: header, a small piece of the map with station pins (draw a simplified crop: you may reuse `snip/<L>-map.svg` inside a box with a tighter `viewBox`, see section 6), Goodaura selected; "מתי" day chips (ב׳ 11.1, ג׳ 12.1, ד׳ 13.1, ה׳ 14.1) and times (09:30 selected, 11:00, 12:30, 13:30, 15:00, 16:30, שעה אחרת); the three fixed spots; the printed meet card with countdown and share (וואטסאפ, תמונה, קישור) + "שמירה בקבוצה". |
| `<L>8-Games.dc.html` | 390 × 844 | Games page: title, intro line, the five games as your language's game cards (name, tagline, meta), the first one featured. Draw a small illustration for each game in your style (simple, from shapes; no photos). |
| `<L>9-Group.dc.html` | 390 × 844 | Group "גודאורי 2027", meta line "10.1–15.1.2027 · 4 חברים", tabs (טיסות active), three flight cards (two on 6H 897 TLV→TBS 16:00, one A9 691 TLV→KUT 07:40 "הוזן על ידי מנהל"), "אני על אותה טיסה", one member "עוד בלי טיסה", the invite code KZBQRM with וואטסאפ / העתקת קישור, and "רק חברי הקבוצה רואים את הדף הזה." |
| `<L>10-Settings.dc.html` | 390 × 844 | About & settings: the account pass (נועה ניסיון, ניהול החשבון, יציאה); settings rows: צליל (on), רטט (on), תצוגה (אוטומטי), שפה · Language (עברית), מדידת שימוש ודיווח שגיאות (on, "מה נאסף"), איפוס השיאים במשחקים (danger); then the start of "מי בנה". |
| `<L>11-Desktop.dc.html` | 1280 × 800 | Desktop home, day: the desktop top bar, the hero with the ridges/panorama and the peaks' labels (Bidara 3174, Sadzele 3307), the boarding pass, and the four navigation items side by side or in your layout. |

Sheets (`0`, `1`, `2`) are presentation boards: a white/ground page with a big title, sections, captions, in your language's own typography. Screens (`3`–`11`) are product screens only (no captions inside).

## 5. Copy and data (use exactly; it is the real site)

- Place: `גודאורי, גאורגיה` · when: `חופשת סקי, ינואר 2027` · clock: `השעה בגודאורי 19:12` (night board: `22:40`).
- Countdown: `100 ימים לטיסה` (night: `100 לילות לטיסה`); stub: `עוד` / `100` / `ימים לטיסה`, `TLV › TBS`.
- Pass, outbound: strip `הטיול שלך · הלוך`, date `10.1.2027`; `מ` `TLV` `תל אביב` → `אל` `TBS` `טביליסי`; `טיסה 6H 897` · `המראה 16:00` · `נחיתה 20:35`; `נוסע נועה ניסיון` · `חזור 15.1` · `ימי סקי 11–14.1`. Return: `הטיול שלך · חזור · בלילה`, `15.1.2027`, `6H 892`, `01:35` → `02:15`. Hint: `נגיעה בכרטיס שמאחור מחליפה · נגיעה בספח תולשת` and `עריכה`. Empty pass: `הוספת הטיסה שלי`, note `בלי הרשמה. נשמר רק בדפדפן הזה, ואפשר למחוק בכל רגע.`
- Navigation items (title / sub): `מפת מסלולים` / `27 מסלולים, פרטים וסרטונים` · `נקודת מפגש` / `בוחרים תחנה ושעה ושולחים לקבוצה` · `משחקים` / `הירידה, בית הספר לסקי, שלג טרי ועוד` · `קבוצה` / `גודאורי 2027 · 4 חברים`. Footer link: `אודות, הגדרות וקרדיטים`, `מדיניות פרטיות`. Tally title: `מסלולים במפה הרשמית`: 5 ירוק, 16 כחול, 4 אדום, 2 שחור.
- Desktop nav: `בית`, `מפת מסלולים`, `נקודת מפגש`, `משחקים`, brand `גודאורי 2027`.
- Season (no data): kicker `מצב הרכבלים`, title `ההר עוד ישן`, text `עוד אין דיווח על רכבלים. העונה בגודאורי נפתחת בדרך כלל בדצמבר, ואז השלטים יתנקו מהשלג.` Status pill: `עוד אין מידע על הרכבלים`. In season (for the lift board sample): `11 מתוך 12 רכבלים פתוחים · עודכן לפני 6 דק׳`; rows: Goodaura (gondola) פתוח · Sadzele (chair) סגור · רוח · Kudebi פתוח · Shino פתוח. Also `רק מה שפתוח בשבילי`, `Sadzele נפתח מאז שבדקת`.
- Map: filters `ירוק` `כחול` `אדום` `שחור` `רכבלים` `ללא שם`; view `תלת-ממד` / `מבט על`; zoom `התקרב` `התרחק` `הצג הכל` (aria); list title `כל המסלולים`, lead `לחצו על מסלול במפה או ברשימה לפרטים וסרטונים.`; `צד Kobi`; scale `500 m`.
- Tatra 2: ref `7A`, blue, `קל`; `אורך 2.28 ק״מ` · `גובה 2665 מ׳ למעלה, 2170 מ׳ למטה` · `ירידה 494 מ׳ · שיפוע ממוצע 22%` · `קטע תלול 36% (100 מ׳ התלולים ביותר)`; `מה מחכה לך`: `התחלה · 2665 מ׳` — `5° ב-150 המטרים הראשונים.`; `הקטע התלול · אחרי 656 מ׳` — `20° (36%) לאורך 100 מ׳`; `הסוף · 2173 מ׳` — `אחרי 2294 מ׳. מגיעים לרכבל Shino`; compare `תלול בערך כמו Kudebi 1 · ארוך בערך כמו Goodaura 2`; `ודאות: גבוהה`, `מקור: דרך ב-OSM שנשאה את השם הזה`, `3 הקלטות GPS ציבוריות עוברות לאורכו`; legend `עד 15°` `15°–25°` `25°–30°` `מעל 30°`; buttons `טיסה במורד המסלול`, `שיתוף`, `→ Tatra 1`, `Kudebi 1 ←`, `→ כל המסלולים`. Video: `Спуск по Tatra 2, Гудаури, Грузия` · `Vlad Key · 8:57`.
- Meet: title `נקודת מפגש`, `איפה נפגשים? לחצו על אחת מ-20 התחנות`, `ניקוי הבחירה`, `כל ההר`; selected `Goodaura` — `התחנה התחתונה של Goodaura ו-New Goodaura`, `גובה 2,161 מ׳`; `מתי`; fixed spots `או בלחיצה אחת`: `רכבל הבוקר · Goodaura 09:30`, `צהריים · Snow Park 13:00`, `סוף יום · Goodaura 16:30`; card: `כרטיס מפגש`, `החבר׳ה · גודאורי 2027`, `נפגשים ב`, `Goodaura`, `יום ב׳ 11.1` `שעה 09:30`, `עוד 101 ימים למפגש`; `איך מגיעים` — `מ-Goodaura 1` (Goodaura 1 › Goodaura 2), `מ-Snow Park` (Snow Park › Goodaura 1), `מ-Tatra 1` (Tatra 1 › Shino), note `לפי החיבורים בנתוני המסלולים. בלי זמנים ובלי תורים.`; share `שליחה בוואטסאפ`, `שיתוף עם תמונה`, `העתקת הקישור`, `שמירה בקבוצה`, `לראות במפת המסלולים`.
- Games: title `משחקים`, intro `על השלג של גודאורי. השיאים נשמרים רק בטלפון שלכם, ומומלץ עם צליל.` 1 `הירידה של החבר׳ה` — `הירידה על המסלולים האמיתיים` — `5 מסלולים`; 2 `בית הספר לסקי` — `מעצירה בפיצה ועד קרווינג` — `7 שיעורים`; 3 `שלג טרי` — `לגעת בשלג ולרסק אגם קפוא` — `בלי ניקוד`; 4 `איחוד כדורי שלג` — `מפתית ועד מלך קזבק` — `משחק קצר`; 5 `קרב כדורי שלג` — `מאחורי החומה, מול החבר׳ה` — `5 יריבים`. Best score sample: `שיא 1,520`.
- Group: `גודאורי 2027`; tabs `טיסות` `מפגשים` `שיאים` `חברים`; members (made up): נועה ניסיון (את/ה, מנהל), דנה בדיקה (מנהל), רון לדוגמה, טל דוגמה; flights: `הלוך · 6H 897` `10.1` `TLV → TBS` `המראה 16:00` — נועה ניסיון, דנה בדיקה; `הלוך · A9 691` `11.1` `TLV → KUT` `המראה 07:40` — רון לדוגמה, `הוזן על ידי מנהל`; `עוד בלי טיסה: טל דוגמה`; `אני על אותה טיסה`; `קוד הזמנה` `KZBQRM`, `שליחה בוואטסאפ`, `העתקת קישור`; `רק חברי הקבוצה רואים את הדף הזה.` Scores sample: הירידה של החבר׳ה — דנה בדיקה 1,840 · נועה ניסיון 1,520 · רון לדוגמה 960.
- Settings: title `אודות והגדרות`; pass `SKI PASS · גודאורי 2027`, `חשבון`, name `נועה ניסיון`, `גוגל`, `ניהול החשבון`, `יציאה`; `הגדרות`; rows `צליל` / `בכרטיס הטיסה ובמשחקים`; `רטט` / `בטלפונים שתומכים (לא באייפון)`; `תצוגה` / `אוטומטי לפי השעה בגודאורי, יום או לילה` value `אוטומטי`; `שפה · Language` / `נבחרה לפי הדפדפן` value `עברית`; `מדידת שימוש ודיווח שגיאות` / `אנונימי ובלי עוגיות: באילו עמודים משתמשים ומה נשבר.` + `מה נאסף`; `איפוס השיאים במשחקים` / `השיאים נשמרים רק בטלפון הזה`; `מי בנה`: `פיני זולברג`, `בונה האתר`, `תכנן ובנה את האתר לחופשת הסקי של החבר׳ה, ינואר 2027, יחד עם Claude.`; `אתר ואפליקציה לא רשמיים, בלי קשר ל-MTA.`
- Trip form copy (if you show inputs): `הטיול שלך`, `הלוך`, `תאריך`, `מספר טיסה`, `מ`, `אל`, `המראה`, `נחיתה`, `חזור`, `אפשר גם בלי`, `ימי סקי מלאים, מחושב מהטיסות`, `שמירה`, error `תאריך החזור לפני ההלוך`.

## 6. Real art and data (use these; never invent mountains or map lines)

- **Ridges** (`design/language/data/ridges.json`): the real skyline seen from the valley south of New Gudauri, in a `0 0 1000 320` box: `far`, `mid`, `near` (closed filled shapes, three distance layers) and `skyline` (one line) / `skyline_fill`; `peaks` gives x/y for Bidara 3174, Sadzele 3307, Kudebi 3006, Chrdili 2504 etc. Use `<svg viewBox="0 0 1000 320" preserveAspectRatio="xMidYMax slice">` (or `none` to stretch), with your fills. You may crop (a different viewBox), scale vertically, layer, outline, hatch, add sun/moon/stars/lights. Do not redraw the shapes by hand.
- **Map**: `design/language/snip/<L>-map.svg` (all runs, real geometry, coloured with your tokens; aspect 4933:6619, at 390 px wide it is 523 px tall) and `snip/<L>-run.svg` (Tatra 2 painted by slope; 390×400 frame). Paste the file's contents **verbatim** into your board (they are long; that is expected). You may change only: the outer `<svg>`'s `width`/`style`/`viewBox` (to crop/zoom, e.g. for the meet board) and wrap it in your frame. Overlay your own UI on top (zoom buttons, pills, pins) with absolutely positioned HTML.
  - For the meet board, a good crop of the lower mountain is `viewBox="-1500 3100 2600 2300"`; the Goodaura gondola bottom station (shared with New Goodaura) is at map point (-231, 4325) — place your selected pin there (convert: x_px = (x − vbX) / vbW × boxWidth).
- **Elevation profile of Tatra 2**: `design/language/data/tatra2-profile.json`: `pts` = [along 0..1, depth 0..1 (0 = top 2665 m, 1 = bottom 2170 m)], 78 points; `segments` = slope colour runs along the line (`#3FA85F` = slope step 1, `#F2C13D` = step 2 → map them to your `slope[0]`, `slope[1]`); `steep` marks the steepest 100 m at about 0.29–0.33 along. Draw it as an SVG polyline/area in your style.
- **Images already uploaded to the canvas** (use only if your language wants the photo):
  - real panorama from the village, phone crops: noon `/_blob/d2dae4e157b7c0b442268b7a96d3fc82`, night `/_blob/05671fd4097ef85d429983eb805f9ef2`, sunset `/_blob/85f66642e4af3f46746e96bce14a00ff`; wide (desktop): noon `/_blob/f14c3ca16a8433615a715c9912a217d7`, night `/_blob/3062c13a07981e74aedc18c5bfa86a54`.
- Peaks: Sadzele 3307, Sadzele West 3276, Bidara 3174, Kudebi 3006, Kobi Pass 2900, Chrdili 2504.

## 7. The five languages (short brief; tokens in `languages.json`)

- **A · שילוט (Signal):** the mountain's own signage system as UI. Aluminium plates (radius 6–8 px) with small rivets in the corners, very bold upright type (Heebo 900), black on white and white on colour, the signal yellow (`#FFC800`) for the primary action and for "you are here", safety orange for alerts. Navigation items are direction plates pointing left (Swiss hiking-sign style: one pointed end), stacked on a round pole with a striped boundary-pole band. Piste refs live in round marker discs (like the numbered discs on piste poles). Pictograms for gondola/chair/platter. Grid-strict, dense, no tilt, no decoration that is not signage. Dark: night plates, retro-reflective feel (yellow stays).
- **B · פוסטר (Poster):** 1950s silkscreen ski poster. Flat layered real ridges (`far`/`mid`/`near`) in 3–4 inks, a big sun (moon at night), slight overprint offset on headline colour (a 2–3 px shifted duplicate), halftone dot patterns via SVG `<pattern>`, paper ground. Display Suez One, big and tight; Assistant for text. Navigation items as luggage labels / enamel-pin badges / poster tickets with notched corners. The boarding pass becomes a vintage airline ticket (perforations, stamp "GUDAURI 2027" in a circle). Chips as small printed tags. Warm and festive, still clean.
- **C · טופוגרפי (Survey):** atlas + instrument. Real contour lines as background texture (use a crop of your map snippet's contours at low contrast, or the ridges as a line drawing), hairline 1 px rules, crosshair "+" marks at card corners, coordinates `42.51°N 44.49°E`, scale bars, north arrow, tabular mono numbers (IBM Plex Mono) with units, small letter-spaced labels. Display Frank Ruhl Libre (atlas lettering), body IBM Plex Sans Hebrew. Colour only for data (pistes, slope, status) and one map-blue accent; everything else ink on paper. Navigation items as an index/legend list with leader dots and grid references. The boarding pass becomes a precise data slip. Dark: night survey, luminous contours.
- **D · קרטולי (Kartuli):** Georgian craft and hospitality. Khevsur embroidery cross motifs and diamond borders (build as SVG `<pattern>` from small squares/crosses), arch-topped cards (Georgian church windows: `border-radius: 999px 999px 8px 8px` style or SVG arch), carved-wood lace edge (scalloped SVG border), enamel jewel colours (pomegranate, teal, gold) on warm snow. Display Bona Nova (serif with character), body Noto Sans Hebrew. Use `გუდაური` in Georgian script as a decorative wordmark. Navigation items as arched niches or enamel tiles. Dark: wine-black night with gold.
- **E · מדבקות (Stickers):** the crew's snowboard covered in stickers. Thick ink outlines (2.5–3 px `#121212`), hard offset shadows (`box-shadow: 4px 4px 0 #121212`), radius 14–18 px, small rotations (−3°..3°) on stickers only (never on text blocks people read), white "die-cut" borders around sticker shapes, a peeling corner, hand-drawn squiggle underlines, sparkles. Display Fredoka, body Rubik. Navigation items as big stickers in the pink/ice/mint/yellow set. Games-forward and joyful, yet aligned and readable. Dark: same outlines in light ink on a night ground, shadows in black.

## 8. Quality check (do it)

- After writing each screen board, run the layout check:
  `node design/language/check.mjs design/language/canvas/project/<file>.dc.html /tmp/claude-0/-home-user-gudauri-ski-trip/3beb8490-3091-5d6b-b288-91f8558cd7cf/scratchpad/check-<file>.png`
  It renders the board with its Google fonts, writes a PNG and lists overflowing text. **Look at the PNG** (Read tool) and fix what is broken: text cut or overlapping, elements outside the board, illegible contrast, misaligned grids. Re-check after fixing. For sheets, also use it to learn the real height, then set `$preview` height and report it.
- The local server is not needed. Do not open or publish the canvas.

## 9. Report back (short)

A list of the 12 files with their final size (w×h), and 4–6 lines in Hebrew: the strongest idea of your language, what you decided on your own, and anything you could not do.
