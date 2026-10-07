# Sella Ronda ו-3 Vallées: בדיקת הנתונים

**(סשן המנהל, 7.10.2026, לבקשת פיני, אחרי `research/resorts/israeli-market.md`.)** שני האתרים שהחברות הישראליות מוכרות הכי הרבה, ושהיו חסרים בדירוג (Sella Ronda) או נמדדו רק בחלקם (Val Thorens במקום כל 3 Vallées). אותה שיטה כמו ב-`research/resorts.md`: המפה הפתוחה נמדדה באמת, והשאר לפי מקורות עם קישור.

## בקצרה

| | Sella Ronda (ארבעת העמקים) | Les 3 Vallées |
|---|---|---|
| ק״מ במפה הפתוחה מול הרשמי | 352 מול כ-415, **0.85** | 537 מול 600, **0.90** |
| קטעים עם שם או מספר | **82%** (94% מהקילומטרים) | 56% (88% מהקילומטרים) |
| קטעים עם דרגת קושי | 91% | 99% |
| מצב רכבלים | דף לכל אזור, **חוסם קריאה מהשרת** (403) | דף עם מפה מוטמעת, נטען בסקריפט |
| מודל גובה פתוח | דרום טירול 2.5 מ׳, CC0; טרנטינו לייזר, CC BY 4.0; ונטו לא נבדק | IGN, 1 מ׳, Licence Ouverte (נבדק) |
| שכבה ממשלתית | מסלולי דרום טירול, CC0 (השירות לא ענה) | שכבת סבואה, העתק של המפה הפתוחה |
| אפליקציה רשמית | MyDolomiti, 4.5 | Les 3 Vallées, 4.0 ו-4.7 |
| ניקוד (נתונים + קהל) | **13** (6 + 7) | **14** (7 + 7) |

**3 Vallées עולה למקום 6 (14 נקודות, בשוויון עם Grandvalira ו-Tignes), ו-Sella Ronda נכנס במקום 7 (13)**, אחרי Mayrhofen, Sölden וגודאורי. מצב הרכבלים ב-Sella Ronda קיבל 0, כי הדף חוסם קריאה מהשרת; עם הסכם או מקור אחר הוא עולה ל-14. הקהל הישראלי מעלה את שניהם (היעדים המבוקשים ביותר לפי סקי דיל), והנתונים טובים אבל לא כמו Sölden.

**המסקנה:** Sella Ronda הוא המועמד החזק יותר לאתר שלישי, כי 82 אחוז מהמסלולים עם שם או מספר (ב-3 Vallées רק 56, ושם בלי שם המסלול נשאר "קטע בלי שם"). **שני הסיכונים בו:** (1) הוא בשלושה מחוזות, ולכן מודל הגובה מורכב משלושה מקורות; (2) דף מצב הרכבלים של Dolomiti Superski חוסם קריאה מהשרת, ולכן מצב רכבלים שם כנראה לא יעבוד בלי הסכם או מקור אחר.

## איך נמדד

- **המפה הפתוחה:** הייצוא של OpenSkiMap מ-7.10.2026 (`runs.geojson`, ODbL), והתסריט `research/resorts/osm_two_areas.py`. נספר כל קטע מסלול שמשויך לאתר, ואורכו מחושב מהקו.
- **Sella Ronda** אינו אתר אחד במפה הפתוחה. הוא נמדד כקטעים של Dolomiti Superski בתוך המלבן של ארבעת העמקים (קו רוחב 46.44 עד 46.62, קו אורך 11.66 עד 11.97), בלי Seiser Alm. Val Gardena לא מופיע כאתר נפרד, ולכן נמדד באותו מלבן. החלקים הדרומיים של Val di Fassa (Ciampac, Vigo di Fassa) עלולים להיות חלקית מחוץ למלבן.
- **הקילומטרים הרשמיים של Sella Ronda** הם סכום ארבעת האזורים של Dolomiti Superski: Val Gardena עם Alpe di Siusi 175, Alta Badia 130, Arabba ו-Marmolada 60, Val di Fassa ו-Carezza 110 (לפי תוצאות חיפוש, האתר של Dolomiti Superski חוסם קריאה). מ-Val Gardena הורדנו כ-60 ק״מ של Seiser Alm (כך במפה הפתוחה), ולכן "כ-415". זו הערכה; הדיוק כאן נמוך יותר מבשאר הטבלה.
- **3 Vallées:** האתר "Les Trois Vallées" במפה הפתוחה, מול 600 ק״מ הרשמיים (`research/resorts/facts-west.md`).

## לפי עמק

| אזור | קטעים | ק״מ | שם | מספר | שם או מספר | דרגת קושי |
|---|---|---|---|---|---|---|
| Sella Ronda, ארבעת העמקים | 605 | 351.9 | 80% | 42% | 82% | 91% |
| Val Gardena (בלי Seiser Alm) | 165 | 92.3 | 76% | 6% | 76% | 92% |
| Alta Badia | 215 | 124.8 | 78% | 64% | 85% | 92% |
| Arabba | 77 | 44.8 | 89% | 68% | 92% | 85% |
| Belvedere ו-Col Rodella (Canazei) | 75 | 39.9 | 74% | 52% | 74% | 93% |
| Val di Fassa | 126 | 77.5 | 74% | 59% | 74% | 96% |
| Les 3 Vallées, כולו | 872 | 537.3 | 56% | 10% | 56% | 99% |
| Courchevel | 288 | 149.7 | 54% | 0% | 54% | 99% |
| Méribel | 217 | 139.3 | 52% | 14% | 52% | 100% |

ב-3 Vallées 29 קטעים מסומנים כדרך לא מוכשרת או כפרירייד (סוג `ski-route` כמו ב-Sölden). אזורים חופפים: קטע יכול להיות גם ב-Belvedere וגם ב-Val di Fassa.

## מקורות

- הקילומטרים של Dolomiti Superski לפי אזור: https://italia.it/en/italy/things-to-do/skiing-in-italy-dolomiti-superski-resort ו-https://www.val-gardena.com/it/inverno/dolomiti-superski/ (דרך תוצאות החיפוש, 7.10.2026).
- 3 Vallées, 600 ק״מ, ודף המצב: `research/resorts/facts-west.md`, סעיף 9. דף המצב (https://www.les3vallees.com/en/live/lifts-and-trails-opening/val-thorens) ענה 200 ב-7.10.2026.
- דף המצב של Dolomiti Superski (https://www.dolomitisuperski.com/en/live-info/lifts/alta-badia) ענה 403 לבקשה מהשרת ב-7.10.2026, כמו בבדיקה הקודמת.
- מודל הגובה של דרום טירול, 2.5 מ׳ ו-0.5 מ׳, CC0 (לפי הקטלוג, לא הורד): https://geonetwork1.civis.bz.it/geonetwork/srv/api/records/p_bz:Elevation:DigitalTerrainModel-0.5m
- הלייזר של טרנטינו, CC BY 4.0 (לפי הקטלוג): https://www.provincia.tn.it/en/News/Insights/Lidar-survey-of-the-territory-of-the-Province-of-Trento
- IGN RGE ALTI ומסלולי דרום טירול: `research/map-sources-europe.md`.

## הבא

1. **החלטה של פיני:** האתר השלישי. ההמלצה שלי: Sella Ronda, בגלל הקהל הישראלי והשמות, עם אזהרה על מצב הרכבלים.
2. **לפני בנייה:** מומחה הסקי בודק את המספרים והצבעים מול המפה הרשמית (כמו ב-Sölden), ולהוריד באמת את מודלי הגובה של שלושת המחוזות ולבדוק את התפרים ביניהם.
3. **הקהל הישראלי באתרים אחרים בטבלה:** ההוכחות החדשות (`israeli-market.md`) משנות גם את Mayrhofen, Ischgl, Sölden, Saalbach, Livigno ו-Alpe d'Huez. הם עוד לא נוקדו מחדש.
