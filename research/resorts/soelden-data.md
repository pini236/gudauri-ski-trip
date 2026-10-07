# Sölden: בדיקת הנתונים

נבנה ב-2026-10-07 בפקודה `python3 tools/build-resort.py tools/resorts/soelden.json --cache <dir>`. אל תערכו ביד; מריצים שוב.

- **מסלולים:** 54 (50 עם מספר או שם), 205 קטעים מהמפה הפתוחה, 103.2 ק״מ של קו.
- **רכבלים:** 39 (33 עם שם).
- **מודל גובה:** 779×702 נקודות כל 20 מ׳, מ-1258 עד 3662 מ׳. המקור: אריחי הגובה של AWS בזום 14 (באוסטריה, המודל הלאומי של 10 מ׳).
- **לא נכנסו:** 24 קווים (מסלולי סקי קרוס־קאנטרי, הליכה ומזחלות, וקווים בלי דרגת קושי).

## בדיקות לכל מסלול

עלייה נגדית: סכום העליות לאורך הקו, מלמעלה למטה, במודל שהאתר מצייר (כלל הדיוק 4: עד כ-10 מ׳). מסלול שנכשל בבדיקה מקבל ודאות `low`, ולא מוסתר: הקו עצמו מהמפה הפתוחה.

| מסלול | צבע | אורך (מ׳) | עלייה נגדית (מ׳) | הערות |
|---|---|---|---|---|
| 1 | red | 3663 | 10 | תקין |
| 1a | black | 325 | 0 | המספר הוחלף לפי הרשימה הרשמית של האתר (במפה הפתוחה 1b) |
| 1b | blue | 1142 | 0 | המספר הוחלף לפי הרשימה הרשמית של האתר (במפה הפתוחה 1a) |
| 2 | blue | 2873 | 4 | תקין |
| 2a | red | 634 | 0 | תקין |
| 3 | black | 3130 | 1 | תקין |
| 3a | blue | 973 | 9 | תקין |
| 3b | blue | 2897 | 6 | תקין |
| 4 | red | 2734 | 2 | תקין |
| 5 | red | 1731 | 0 | תקין |
| 6 | blue | 2161 | 4 | תקין |
| 7 | black | 953 | 0 | תקין |
| 7a | blue | 3261 | 22 | עלייה נגדית 22 מ׳; דרך מקשרת ולא מסלול, לפי המקרא של המפה הרשמית (סוג בלבד) |
| 8 | blue | 3780 | 2 | תקין |
| 9 | blue | 1193 | 2 | תקין |
| 10 | blue | 2394 | 1 | תקין |
| 11 | red | 4513 | 2 | תקין |
| 12 | red | 1621 | 5 | תקין |
| 13 | blue | 3078 | 2 | תקין |
| 14 | black | 2608 | 2 | תקין |
| 15 | blue | 4249 | 7 | תקין |
| 16 | red | 1124 | 6 | תקין |
| 18 | red | 1522 | 1 | תקין |
| 19 | red | 2510 | 12 | עלייה נגדית 12 מ׳; דרגות קושי שונות בקטעים: advanced, easy, intermediate |
| 20 | black | 1628 | 2 | תקין |
| 21 | red | 3142 | 0 | תקין |
| 22 | red | 2628 | 22 | עלייה נגדית 22 מ׳; דרגות קושי שונות בקטעים: advanced, intermediate |
| 22a | black | 453 | 0 | תקין |
| 23 | blue | 1298 | 2 | תקין |
| 24 | blue | 3216 | 3 | תקין |
| 25 | black | 1194 | 0 | תקין |
| 30 | blue | 5817 | 2 | דרך מקשרת ולא מסלול, לפי המקרא של המפה הרשמית (סוג בלבד) |
| 31 | black | 1262 | 3 | תקין |
| 32 | blue | 1783 | 4 | תקין |
| 33 | blue | 3371 | 10 | תקין |
| 34 | blue | 934 | 0 | תקין |
| 36 | red | 1943 | 1 | תקין |
| 38 | blue | 4021 | 5 | תקין |
| 39 | blue | 4552 | 12 | עלייה נגדית 12 מ׳ |
| 40 | blue | 578 | 3 | תקין |
| 41 | black | 906 | 3 | תקין |
| 50 | blue | 751 | 0 | תקין |
| 61 | none | 371 | 1 | דרך סקי (Skiroute): מאובטחת רק מפני מפולות, בלי הכשרה ובלי דרגת קושי; לפי piste:grooming=backcountry במפה הפתוחה או הרשימה הרשמית של האתר |
| 62 | none | 277 | 0 | דרך סקי (Skiroute): מאובטחת רק מפני מפולות, בלי הכשרה ובלי דרגת קושי; לפי piste:grooming=backcountry במפה הפתוחה או הרשימה הרשמית של האתר |
| 70 | none | 441 | 0 | דרך סקי (Skiroute): מאובטחת רק מפני מפולות, בלי הכשרה ובלי דרגת קושי; לפי piste:grooming=backcountry במפה הפתוחה או הרשימה הרשמית של האתר |
| 71 | none | 785 | 0 | דרך סקי (Skiroute): מאובטחת רק מפני מפולות, בלי הכשרה ובלי דרגת קושי; לפי piste:grooming=backcountry במפה הפתוחה או הרשימה הרשמית של האתר |
| 72 | none | 468 | 0 | דרך סקי (Skiroute): מאובטחת רק מפני מפולות, בלי הכשרה ובלי דרגת קושי; לפי piste:grooming=backcountry במפה הפתוחה או הרשימה הרשמית של האתר |
| 80 | none | 654 | 0 | דרך סקי (Skiroute): מאובטחת רק מפני מפולות, בלי הכשרה ובלי דרגת קושי; לפי piste:grooming=backcountry במפה הפתוחה או הרשימה הרשמית של האתר |
| Gaislachalm | none | 4172 | 10 | דרך סקי (Skiroute): מאובטחת רק מפני מפולות, בלי הכשרה ובלי דרגת קושי; לפי piste:grooming=backcountry במפה הפתוחה או הרשימה הרשמית של האתר |
| Mini | blue | 205 | 0 | תקין |
| u317765891 | red | 0 | 0 | תקין |
| u432769135 | blue | 75 | 0 | תקין |
| u440323831 | blue | 1015 | 0 | המספר במפה הפתוחה לא מופיע במפה הרשמית, ולכן הקו בלי מספר |
| u1179539285 | red | 181 | 0 | המספר במפה הפתוחה לא מופיע במפה הרשמית, ולכן הקו בלי מספר |

## לא נכנסו

- Höfer - Böden Loipe: not a downhill run (nordic)
- Gaislachkogl: not a downhill run (sled)
- Höfer - Böden - Windau Loipe: not a downhill run (nordic)
- Funslope: a park, not a run (the ski expert)
- Carvin: not a downhill run (snow_park)
- Funslope: a park, not a run (the ski expert)
- AREA 47 Snowpark Sölden: not a downhill run (snow_park)
- Panoramaweg: not a downhill run (hike)
- Gaislachkogl: not a downhill run (sled)
- Funslope: a park, not a run (the ski expert)
- Sonneck/Gaislachalm/Silbertal: not a downhill run (hike)
- Sonneck/Gaislachalm/Silbertal, Gaislachkogl, Gaislach: not a downhill run (hike,sled)
- Sonneck/Gaislachalm/Silbertal: not a downhill run (hike)
- (בלי שם): not a downhill run (hike)
- Funslope: a park, not a run (the ski expert)
- Flug: not a downhill run (snow_park)
- (בלי שם): not a downhill run (nordic)
- Höfer - Böden - Windau Loipe: not a downhill run (nordic)
- AREA 47 Snowpark Sölden: not a downhill run (snow_park)
- Funslope: a park, not a run (the ski expert)
- (בלי שם): not a downhill run (skitour)
- Abfahrt zur Pension Waldesruh: no difficulty in OSM, so no colour
- Giggijoch: not a downhill run (playground)
- Giggijoch: not a downhill run (playground)

## רכבלים מול המאגר הרשמי

המקור: Datenquelle: Land Tirol, data.tirol.gv.at (Aufstiegshilfen), CC BY 4.0, https://creativecommons.org/licenses/by/4.0/. השכבה: https://services3.arcgis.com/hG7UfxX49PQ8XkXh/arcgis/rest/services/Aufstiegshilfen/FeatureServer/0, הורדה ב-2026-10-07. התאמה לפי שני הקצוות, עד 120 מ׳. נלקחים רק קיבולת, שנת בנייה ואורך משופע; הקו נשאר מהמפה הפתוחה.

- **הותאמו:** 29 מתוך 39.
- **אצלנו, בלי התאמה (נשארים בלי הנתונים):** Gletscherband, Rotkogl I, Rotkogl II, Zentrum Shuttle, (no name, magic_carpet, 22 m), (no name, magic_carpet, 40 m), (no name, magic_carpet, 20 m), (no name, magic_carpet, 20 m), (no name, magic_carpet, 20 m), (no name, drag_lift, 67 m).
- **במאגר, בלי התאמה** (רוב הרשימה רכבלים של אתרים שכנים שבתוך התחום): Krumpwasser, EUB Rifflsee / Rifflseebahn, Gletscherexpress - Pitzexpress, Gletscherseebahn, Top Schermer 3000, Hochgurglbahn I, EUB Mittelbergbahn, Mandarfenlift, Hochsölden- Rotkogl, Vorderer Wurmkogel I, Wildspitzbahn, Top Express Gurgl, Brunnenkogellift, Hochgurglbahn II, Vorderer Wurmkogel II, TOP Wurmkogel I, Kirchenkar I, Große Karbahn, Kirchenkar II, Mittagskogellift.
