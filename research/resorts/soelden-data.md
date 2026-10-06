# Sölden: בדיקת הנתונים

נבנה ב-2026-10-06 בפקודה `python3 tools/build-resort.py tools/resorts/soelden.json --cache <dir>`. אל תערכו ביד; מריצים שוב.

- **מסלולים:** 55 (53 עם מספר או שם), 210 קטעים מהמפה הפתוחה, 104.0 ק״מ של קו.
- **רכבלים:** 39 (33 עם שם).
- **מודל גובה:** 390×351 נקודות כל 40 מ׳, מ-1258 עד 3655 מ׳. המקור: אריחי הגובה של AWS בזום 14 (באוסטריה, המודל הלאומי של 10 מ׳).
- **לא נכנסו:** 19 קווים (מסלולי סקי קרוס־קאנטרי, הליכה ומזחלות, וקווים בלי דרגת קושי).

## בדיקות לכל מסלול

עלייה נגדית: סכום העליות לאורך הקו, מלמעלה למטה, במודל שהאתר מצייר (כלל הדיוק 4: עד כ-10 מ׳). מסלול שנכשל בבדיקה מקבל ודאות `low`, ולא מוסתר: הקו עצמו מהמפה הפתוחה.

| מסלול | צבע | אורך (מ׳) | עלייה נגדית (מ׳) | הערות |
|---|---|---|---|---|
| 1 | red | 3663 | 11 | עלייה נגדית 11 מ׳ |
| 1a | blue | 1142 | 0 | תקין |
| 1b | black | 325 | 0 | תקין |
| 2 | blue | 2873 | 6 | תקין |
| 2a | red | 634 | 0 | תקין |
| 3 | black | 3130 | 0 | תקין |
| 3a | blue | 973 | 8 | תקין |
| 3b | blue | 2897 | 5 | תקין |
| 4 | red | 2734 | 5 | תקין |
| 5 | red | 1731 | 4 | תקין |
| 6 | blue | 2161 | 2 | תקין |
| 7 | black | 953 | 0 | תקין |
| 7a | blue | 3261 | 21 | עלייה נגדית 21 מ׳ |
| 7b | red | 181 | 0 | תקין |
| 8 | blue | 3780 | 1 | תקין |
| 9 | blue | 1193 | 1 | תקין |
| 10 | blue | 2394 | 1 | תקין |
| 11 | red | 4513 | 2 | תקין |
| 12 | red | 1621 | 6 | תקין |
| 13 | blue | 3078 | 5 | תקין |
| 14 | black | 2608 | 4 | תקין |
| 15 | blue | 4249 | 7 | תקין |
| 16 | red | 1124 | 4 | תקין |
| 18 | red | 1522 | 2 | תקין |
| 19 | red | 2510 | 9 | דרגות קושי שונות בקטעים: advanced, easy, intermediate |
| 20 | black | 1628 | 1 | תקין |
| 21 | red | 3142 | 1 | תקין |
| 22 | red | 2628 | 21 | עלייה נגדית 21 מ׳; דרגות קושי שונות בקטעים: advanced, intermediate |
| 22a | black | 453 | 0 | תקין |
| 23 | blue | 1298 | 3 | תקין |
| 24 | blue | 3216 | 6 | תקין |
| 25 | black | 1194 | 1 | תקין |
| 30 | blue | 5817 | 4 | תקין |
| 31 | black | 1262 | 2 | תקין |
| 32 | blue | 1783 | 7 | תקין |
| 33 | blue | 3371 | 9 | תקין |
| 34 | blue | 934 | 0 | תקין |
| 35 | blue | 1015 | 0 | תקין |
| 36 | red | 1943 | 3 | תקין |
| 38 | blue | 4021 | 69 | עלייה נגדית 69 מ׳ |
| 39 | blue | 4552 | 8 | תקין |
| 40 | blue | 578 | 3 | תקין |
| 41 | black | 906 | 1 | תקין |
| 50 | blue | 751 | 0 | תקין |
| 61 | black | 371 | 2 | תקין |
| 62 | black | 277 | 0 | תקין |
| 70 | black | 441 | 0 | תקין |
| 71 | black | 785 | 0 | תקין |
| 72 | red | 468 | 0 | תקין |
| 80 | red | 654 | 0 | תקין |
| Funslope | blue | 824 | 3 | תקין |
| Gaislachalm | red | 4172 | 9 | תקין |
| Mini | blue | 205 | 0 | תקין |
| u317765891 | red | 0 | 0 | תקין |
| u432769135 | blue | 75 | 0 | תקין |

## לא נכנסו

- Höfer - Böden Loipe: not a downhill run (nordic)
- Gaislachkogl: not a downhill run (sled)
- Höfer - Böden - Windau Loipe: not a downhill run (nordic)
- Carvin: not a downhill run (snow_park)
- AREA 47 Snowpark Sölden: not a downhill run (snow_park)
- Panoramaweg: not a downhill run (hike)
- Gaislachkogl: not a downhill run (sled)
- Sonneck/Gaislachalm/Silbertal: not a downhill run (hike)
- Sonneck/Gaislachalm/Silbertal, Gaislachkogl, Gaislach: not a downhill run (hike,sled)
- Sonneck/Gaislachalm/Silbertal: not a downhill run (hike)
- (בלי שם): not a downhill run (hike)
- Flug: not a downhill run (snow_park)
- (בלי שם): not a downhill run (nordic)
- Höfer - Böden - Windau Loipe: not a downhill run (nordic)
- AREA 47 Snowpark Sölden: not a downhill run (snow_park)
- (בלי שם): not a downhill run (skitour)
- Abfahrt zur Pension Waldesruh: no difficulty in OSM, so no colour
- Giggijoch: not a downhill run (playground)
- Giggijoch: not a downhill run (playground)
