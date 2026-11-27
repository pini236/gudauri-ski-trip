# Content review of the UX-pass wording (not entered in i18n/strings.json; waits for Pini's approval of the boards)

Reviewer: content session, 27.11.2026. Hebrew changes only where the proposal is not plain spoken Hebrew; en/ru/ka are written at entry time.

## Renames
- `app.home_trip`, `trip.title`: "הטיול שלי" OK (en "My trip", ru "Моя поездка", ka "ჩემი მოგზაურობა"). Check the `where` of `trip.title` is app-only.
- `app.grp_title`: "הקבוצה שלי" OK (en "My group").
- `app.cabin_title`: "משחקים" OK (en "Games"). Not "שלי": it is not the user's own data.
- `app.dock_menu`: "תפריט" OK. The dock label also needs a short form in ru ("Меню") and ka ("მენიუ"); fits.
- Other "שלך" titles: `app.day_title` "היום שלך" -> "היום שלי" (the day screen is the user's own recording); `app.days_trip_title` "הטיול שלך על ההר" -> "הטיול שלי על ההר". Sentences and hints that address the user ("שלך" inside a sentence) stay: "שלי" is for titles only.

## Changes to the proposed Hebrew
- `app.menu_grp_none` "מזמינים את החברים לטיול" -> "הזמנת חברים לטיול" (action noun, as the settings rows).
- `app.menu_lifts_pre` "נפתחים ב-{date} · עוד {n} ימים" -> "נפתחים ב-{date}, בעוד {n} ימים" (no dot-list; the count needs plural forms: one/two/n, `{n}` stays the only variable).
- `app.menu_days_none` "יום גלישה לדוגמה" OK.
- `app.hint_menu`: "כל השאר נמצא כאן: הטיסה, כרטיס הסקי, הקבוצה, אוכל, משחקים והימים שהקלטת." has a colon-list, which Pini rejected for hints. Proposal: "כאן הטיסה, כרטיס הסקי, הקבוצה, האוכל, המשחקים והימים שהקלטתם." Check: about 70 characters, three lines fit. Plural "הקלטתם" to stay neutral.
- `app.skipass_saves` "חוסכים {amount} מול כרטיסי יום" -> "חיסכון של {amount} לעומת כרטיסי יום".
- `app.skipass_foot` "מחיר לתיירים, כולל מע״מ · {src} · נבדק {date}": three dot-separated facts are fine in a footnote (not a sentence).
- `app.skipass_by_trip` "{dates} · לפי הטיול שלי" OK.
- `app.food_from_lift` "{m} מ׳ מ-{lift}" -> "{m} מטר מ-{lift}" (an abbreviation next to an abbreviation reads badly; "מ-" before a Latin name needs a hyphen, already there).
- `app.food_on_run` "על המסלול, ליד {lift}": a run is not a lift; the variable must be a place name. Ask the designer which name it receives.
- `app.food_tab_rest` "בשאר ההר · {n}": it also covers the lower village, so "בשאר ההר" is slightly off. Recommended: "על ההר · {n}" with the first tab "בכפר · {n}"; the old second tab was the mountain, and the lower village is next to the lifts anyway.
- `app.gate_title` "נוסעים עם חברים?" OK, natural. `app.gate_sub` "קבוצה אחת לכל מי שבא לטיול" -> "קבוצה אחת לכל החברים בטיול".
- `app.gate_why_*`: "מי טס מתי", "איפה נפגשים", "מי מוביל במשחקים" OK.
- `app.far_title` "{resort} · {km} ק״מ מכאן" OK; `app.far_where_sub` "המיקום שלך יופיע כשתהיו על ההר" is mixed singular and plural; use "המיקום יופיע כשתהיו על ההר" (as `loc.out_sub` today).
- `app.far_rec_sub` "מקליטים גלישה כשנמצאים על ההר" OK ("מקליטים" is the app's voice).
- `app.far_rec_anyway` "להקליט בכל זאת" OK. `app.far_sample` "לראות יום גלישה לדוגמה" OK.
- `app.games_*`: OK. `app.games_start_here` "מתחילים כאן" OK.
- `places.kind_food` today "אוכל": proposed "מסעדה" is fine only if the type is really a restaurant; keep "אוכל" if it also covers fast food. `places.seasonal` "עונתי" -> "פתוח רק בעונה" OK.

## Notes for entry
- Variable counts of existing keys must not change: `app.home_trip`, `trip.title`, `app.grp_title`, `app.cabin_title` have none, safe.
- Plurals: `menu_*_n`, `skipass_days_*` and "{n} ימים" need one/two/n forms in he and ru (ru has few/many); decide with the implementer whether the client picks the form (it does for `app.skipass_days_*`: three keys).
- Georgian and Russian: "Вне Гудаури" style long strings should be checked in the cell width of the dock and tabs when the boards are built.

## Designer's answers (27.11.2026)

All the Hebrew changes above are accepted; the boards (canvas version 86), `strings.md` and the part files now carry them.

- **`app.food_on_run`:** the variable is a **lift name**. A place on a run has only `liftId` in `places.json` (the lift it sits beside); the data has no run name for it. So the text names the lift for what it is: "על המסלול, ליד הרכבל {lift}".
- **`app.food_tab_rest`:** "על ההר · {n}", as you recommend.
- **`places.kind_food`:** keep today's "אוכל" (the kind also covers fast food); `places.kind_cafe` keeps "בית קפה".
- **Plural forms:** the client picks the form, with three keys (`_one`, `_two`, `_n`), as `app.skipass_days_*`. Needed for: `app.menu_days` ("יום אחד · {km} ק״מ" / "יומיים · …" / "{n} ימים · …") and `app.menu_lifts_pre` (one: "נפתחים מחר, ב-{date}"; two: "נפתחים ב-{date}, בעוד יומיים"; n). `app.menu_food_n`: `_one` ("מקום אחד") and `_n`. `app.menu_games_n`: `_n` only (five games). The tabs "בכפר · {n}" and "על ההר · {n}" show a bare number, no plural. For the Russian few / many forms, add the keys your checker needs and tell the implementer of part 1.
- **`app.day_title`, `app.days_trip_title` to "שלי":** agreed, titles only.
- **`trip.title`:** change it only if its `where` is app-only; otherwise add `app.trip_title` and leave the site's key.
- **Width in ru / ka:** the dock button is as wide as its text ("Меню", "მენიუ" fit); the two food tabs share the width equally, about 170 each for "{word} · {n}".
