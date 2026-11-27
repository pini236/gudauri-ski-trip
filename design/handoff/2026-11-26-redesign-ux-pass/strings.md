# Strings for the UX pass (to the content session)

Hebrew is the designer's proposal; content writes the final text in four languages and picks the key names. Plain native Hebrew, short, not poetic (CLAUDE.md, 25.11.2026). Approved with the boards (Pini, 27.11.2026) and entered by the content session. The Hebrew below is after the content review (`content-review.md`); `i18n/strings.json` is the source from now on. Where an existing key already says it, it is named instead of a new one.

## Renames (the text of existing keys)

| Key | Today | Proposed |
|---|---|---|
| `app.home_trip`, `trip.title` (in the app) | הטיול שלך | הטיול שלי |
| `app.grp_title` | החבר׳ה | הקבוצה שלי |
| `app.cabin_title` | ערב בבקתה | משחקים |

One voice for the user's own things: "שלי" (as in `app.days_title` "הימים שלי", `acct.my_groups` "הקבוצות שלי"). Please check the other `app.*` keys that say "שלך" for a title.

Retired: `app.dock_village` (in the dock), `app.hint_village`, `app.cabin_sub`, `app.grp_sub`, `app.g_rest_works`, `loc.out_title`, `loc.out_sub`.

## Part 1: the menu

| Proposed key | Hebrew | Note |
|---|---|---|
| `app.dock_menu` | תפריט | the dock button and the sheet's name for the screen reader |
| `app.menu_trip_add` | הוספת טיסה | chip on the empty trip tile; the question above it is `trip.when_fly` |
| `app.menu_grp_none` | הזמנת חברים לטיול | group tile, no group |
| `app.menu_food_n` | {n} מקומות, בכפר ועל ההר | |
| `app.menu_games_n` | {n} משחקים · גם בלי קליטה | |
| `app.menu_days_n` | {n} ימים · {km} ק״מ | |
| `app.menu_days_none` | יום גלישה לדוגמה | |
| `app.menu_lifts` | רכבלים ומזג אוויר | tile title |
| `app.menu_lifts_now` | {open} מתוך {total} פתוחים · {temp} | |
| `app.menu_lifts_pre` | נפתחים ב-{date}, בעוד {n} ימים | before the season; forms one / two / n, the client picks |
| `app.menu_account` | חשבון | footer |
| `app.hint_menu` | כאן הטיסה, כרטיס הסקי, הקבוצה, האוכל, המשחקים והימים שהקלטתם. | replaces `app.hint_village`; up to three lines |

Existing, reused: `app.skipass_title`, `app.vill_pass_trip`, `app.vill_pass_day`, `app.vill_grp_meet`, `app.vill_grp_n`, `app.days_title`, `app.pass_days`, `about.settings`, `app.hint_trip`, `app.hint_group` (check both hints still read right on a tile).

## Part 2: my trip

| Proposed key | Hebrew |
|---|---|
| `trip.add_bag` | + כבודה |

Existing: `trip.edit` (עריכה), `trip.add_time`, `trip.add_number`. Editor titles: the field names that exist (`ticket.departs` etc.).

## Part 3: ski pass

| Proposed key | Hebrew | Note |
|---|---|---|
| `app.skipass_season` | {resort} · עונת {season} | on the pass |
| `app.skipass_days_one` / `_two` / `_n` | יום אחד / יומיים / {n} ימים | with "· מבוגר" or "· ילד 6 עד 12" after it |
| `app.skipass_by_trip` | {dates} · לפי הטיול שלי | |
| `app.skipass_saves` | חיסכון של {amount} לעומת כרטיסי יום | chip |
| `app.skipass_days_q` | כמה ימי סקי? | |
| `app.skipass_office` | קונים בקופה שבכפר | |
| `app.skipass_office_where` | איפה הקופה? | link |
| `app.skipass_deposit` | פיקדון על הכרטיס | value: "{amount}, מוחזר" |
| `app.skipass_night_row` | סקי לילה, בנפרד | value: "{amount} לערב" |
| `app.skipass_foot` | מחיר לתיירים, כולל מע״מ · {src} · נבדק {date} | one footnote |

Retired from this screen: `app.skipass_hero`, `app.skipass_vs_daily`, `app.skipass_save`, `app.skipass_card`, `app.skipass_foreign`, `app.skipass_night`, `app.skipass_buy`, `app.skipass_from_trip`.

## Part 4: food and drink

| Proposed key | Hebrew |
|---|---|
| `app.food_tab_village` | בכפר · {n} |
| `app.food_tab_rest` | על ההר · {n} |
| `app.food_from_lift` | {m} מטר מ-{lift} |
| `app.food_near_top` | ליד התחנה העליונה של {lift} |
| `app.food_near_bottom` | ליד התחנה התחתונה של {lift} |
| `app.food_on_run` | על המסלול, ליד הרכבל {lift} |
| `app.food_navigate` | ניווט |
| `app.food_share` | שיתוף |
| `app.food_more_village` | עוד בכפר |
| `app.food_more_rest` | עוד על ההר |

Existing: `app.vill_food` (אוכל ושתייה), `places.kind_food`, `places.kind_cafe` (today's text stays: "אוכל", "בית קפה"), `places.seasonal` (proposed "פתוח רק בעונה"), `places.none`, `places.check`.

## Part 5: my group

| Proposed key | Hebrew |
|---|---|
| `app.gate_title` | נוסעים עם חברים? |
| `app.gate_sub` | קבוצה אחת לכל החברים בטיול |
| `app.gate_why_flights` | מי טס מתי |
| `app.gate_why_meet` | איפה נפגשים |
| `app.gate_why_scores` | מי מוביל במשחקים |
| `app.gate_code_title` | קוד ההזמנה |

Existing: `app.g_new_title` (קבוצה חדשה), `app.g_have_code`, `app.g_join`, `app.err_offline`.

## Part 6: games

| Proposed key | Hebrew |
|---|---|
| `app.games_offline` | גם בלי קליטה |
| `app.games_last` | המשחק האחרון |
| `app.games_continue` | ממשיכים |
| `app.games_start_here` | מתחילים כאן |
| `app.games_play` | לשחק |

Existing: `app.cabin_not_played`, `app.cabin_table`, `app.cabin_best` and the other best-score strings, `games.*_name`, `games.*_tag`.

## Part 7: far from the mountain

| Proposed key | Hebrew | Note |
|---|---|---|
| `app.far_title` | {resort} · {km} ק״מ מכאן | the card and the sheet |
| `app.far_where_sub` | המיקום יופיע כשתהיו על ההר | as `loc.out_sub` today |
| `app.far_rec_sub` | מקליטים גלישה כשנמצאים על ההר | |
| `app.far_sample` | לראות יום גלישה לדוגמה | primary button |
| `app.far_rec_anyway` | להקליט בכל זאת | text button |
| `app.sr_far` | {resort} במרחק {km} קילומטר | screen reader |

Existing: `app.rec_outside`.
