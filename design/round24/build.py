#!/usr/bin/env python3
"""Round 24: ski routes as a kind of their own, and wide runs that show (decision 68). Drawn over the real site by shots.mjs + proposal.js.

One canvas page, "סבב 24". Run from the repo root:
  python3 design/round24/build.py webp
  python3 design/round24/build.py [canvas project folder]"""
import html, json, pathlib, subprocess, sys

here = pathlib.Path(__file__).resolve().parent
root = here.parent.parent
sys.path.insert(0, str(root / 'design/round12'))
from build import page  # the board shell of rounds 12 to 20

PAGE = ('round24', 'סבב 24: דרכי סקי ומסלולים רחבים')
SRC = {n: ('shots/' + n + '.png', 390, 0, 0) for n in [
    'today-zoom', 'today-zoom-night', 'today-map', 'today-gud-zoom', 'today-zoom61',
    'prop-zoom61', 'prop-route61', 'prop-route61-night', 'prop-3d61', 'prop-map',
    'prop-zoom', 'prop-zoom-night', 'hatch-zoom', 'prop-gud-zoom', 'prop-run4',
    'prop-missing', 'prop-missing63', 'prop-hut',
    'sel-today4', 'sel-a4', 'sel-b4', 'sel-today11', 'sel-a11', 'sel-b11', 'sel-a11-night', 'sel-b11-night', 'sel-a-gud', 'sel-b-gud',
    'off-zoom61', 'off-zoom61-night', 'off-zoom11', 'off-hut', 'off-route61', 'off-hut-panel', 'off-missing', 'off-3d',
    'full-zoom', 'full-zoom-night', 'today-zoom11', 'full-zoom11', 'full-zoom11-night', 'full-run4', 'full-map', 'full-gud', 'full-gud-night']}
SRC['full-desk'] = ('shots/full-desk.png', 900, 0, 0)
SRC['off-desk'] = ('shots/off-desk.png', 900, 0, 0)
SRC['sel-b4-desk'] = ('shots/sel-b4-desk.png', 900, 0, 0)
SRC['prop-desk-zoom'] = ('shots/prop-desk-zoom.png', 900, 0, 0)
for n in ['soelden-top-desk', 'soelden-run11-desk', 'soelden-route62-desk', 'gudauri-top-desk']:
    SRC['built-' + n] = ('built/' + n + '.png', 900, 0, 0)
SRC['built-soelden-top-phone'] = ('built/soelden-top-phone-night.png', 390, 0, 0)
for n in ['leg-today', 'leg-closed', 'leg-open', 'leg-closed-night', 'leg-open-night', 'leg-run11', 'leg-gud-today', 'leg-gud-closed', 'leg-gud-open', 'leg-gud-open-night']:
    SRC[n] = ('shots/' + n + '.png', 390, 0, 0)
for n in ['leg-today-desk', 'leg-closed-desk', 'leg-open-desk', 'leg-open-desk-night', 'leg-gud-open-desk']:
    SRC[n] = ('shots/' + n + '.png', 900, 0, 0)
N = lambda f: f + '.webp'
BOARDS = [
    ('leg', 'Leg', 'המקרא שמתקפל (ממתין לאישור)', 'היום בטלפון המקרא מכסה כרבע מרוחב המפה, ובמחשב יושב על קנה המידה. בהצעה הוא אותו רכיב כמו "צד Kobi": כפתור של 44 עם סמל קטן ו"מקרא", בפינה השנייה של המפה. נגיעה פותחת את מה שיש היום כקלף מעל הכפתור, ונגיעה שנייה או ה-X סוגרות. קנה המידה עולה מעל הכפתור, ונעלם רק כשהמקרא פתוח. סגור כברירת מחדל.',
     [('היום', ['leg-today']), ('סגור', ['leg-closed']), ('פתוח', ['leg-open']), ('סגור בלילה', ['leg-closed-night']), ('פתוח בלילה', ['leg-open-night']), ('עם מסלול נבחר', ['leg-run11'])]),
    ('leg', 'LegGud', 'המקרא גם בגודאורי', 'כשהוא כפתור, יש לו מקום גם בגודאורי: בפינה השמאלית, מול "צד Kobi", בלי לכסות אותו. שתי שורות: דרך מקשרת ומסלול רחב.',
     [('היום', ['leg-gud-today']), ('סגור', ['leg-gud-closed']), ('פתוח', ['leg-gud-open']), ('פתוח בלילה', ['leg-gud-open-night'])]),
    ('leg', 'LegDesk', 'המקרא במחשב', 'היום המקרא יושב על "1 km". בהצעה קנה המידה מעל הכפתור, והקלף נפתח רק כשרוצים.',
     [('היום', ['leg-today-desk']), ('סגור', ['leg-closed-desk']), ('פתוח', ['leg-open-desk']), ('פתוח בלילה', ['leg-open-desk-night']), ('גודאורי', ['leg-gud-open-desk'])]),
    ('built', 'Built', 'באתר עכשיו (מומש, 7.10.2026)', 'צילומים מהאתר עצמו, אחרי המימוש של סשן המנהל (1ב, 2ב ו-2ג בחלופה ב). שתי תוספות על הקנבס: דרך לבקתה בשורה משלה במקרא, ומסלול סגור במצב הרכבלים מאפיר גם את השטח, לא רק את הקו. המקרא רק באתר שיש בו דרכי סקי, ולכן לא בגודאורי. מה שעוד לא טוב: בטלפון המקרא מכסה כרבע מרוחב המפה, ובמחשב הוא יושב על קנה המידה.',
     [('Sölden, מחשב', ['built-soelden-top-desk']), ('טלפון', ['built-soelden-top-phone']), ('מסלול 11 נבחר', ['built-soelden-run11-desk']), ('דרך סקי 62 נבחרת', ['built-soelden-route62-desk']), ('גודאורי', ['built-gudauri-top-desk'])]),
    ('sel', 'Sel', '2ג. מסלול נבחר: גם החלקים הרחבים מסומנים (אושר)', 'היום, כשבוחרים מסלול, רק הקו נצבע לפי השיפוע, והשטח הרחב נעלם מתחת לצביעה של פני השטח. בהצעה השטח עולה לשכבה של המסלול הנבחר, ומסגרת לבנה אחת עוברת סביב הקו והשטח יחד, כצורה אחת. א: השטח בצבע המסלול. ב (מומלץ): גם השטח צבוע לפי השיפוע, כמו הקו, עם מסגרת דקה בצבע המסלול. כך כל המסלול, הקו והחלקים הרחבים, מסומן מלמעלה למטה באותה שפה של תלילות.',
     [('היום (עם 2ב)', ['sel-today4']), ('א: בצבע המסלול', ['sel-a4']), ('ב: לפי השיפוע', ['sel-b4']), ('היום, 11', ['sel-today11']), ('א', ['sel-a11']), ('ב', ['sel-b11']), ('א בלילה', ['sel-a11-night']), ('ב בלילה', ['sel-b11-night'])]),
    ('sel', 'Sel2', '2ג. בגודאורי ובמחשב', 'Pirveli בגודאורי, ו-4 במחשב (ב).', [('גודאורי, א', ['sel-a-gud']), ('גודאורי, ב', ['sel-b-gud']), ('מחשב, ב', ['sel-b4-desk'])]),
    ('off', 'Off', '1ב. דרך סקי כמו במפה הרשמית 2026/27 (אושר)', 'אחרי השוואה למפה הרשמית של Sölden לעונת 2026/27 (ה-PDF שפיני שלח; לצורך השוואה בלבד, לא נשמר בריפו): דרך סקי היא רצועה כתומה בהירה עם שוליים מנוקדים באדום, והמספר בלבן בתוך מעוין אדום. דרך פרטית לבקתה (Gaislachalm) היא קו דק מנוקד באדום, בלי לוחית. מחליף את הקו הכתום המקווקו של 1. יחד עם 2ב.',
     [('דרך סקי', ['off-zoom61']), ('בלילה', ['off-zoom61-night']), ('Giggijoch', ['off-zoom11']), ('דרך לבקתה', ['off-hut']), ('הפאנל', ['off-route61']), ('פאנל הבקתה', ['off-hut-panel']), ('הרשימה', ['off-missing']), ('תלת-ממד', ['off-3d'])]),
    ('off', 'OffDesk', '1ב ו-2ב יחד, במחשב', 'האזור של 4, 5 ו-61 מתחת ל-Gaislachkogel.', [('מחשב', ['off-desk'])]),
    ('new', 'Full', '2ב. מסלול רחב בצבע המסלול (אושר, החלטה 69)', 'השטח של מסלול רחב מלא בצבע של המסלול, כמו במפה הרשמית, וממשיך את הקו בלי הבדל: קו המתאר הלבן עובר מסביב לשטח ולקו יחד, כצורה אחת, ושום קו לבן לא חוצה את השטח. מסלולים אחרים ורכבלים מצוירים מעליו. מחליף את ההצעה הקודמת (30 אחוז שקיפות).',
     [('היום', ['today-zoom']), ('בצבע המסלול', ['full-zoom']), ('בלילה', ['full-zoom-night']), ('Giggijoch היום', ['today-zoom11']), ('Giggijoch', ['full-zoom11']), ('בלילה', ['full-zoom11-night'])]),
    ('new', 'Full2', '2ב. בכל המצבים', 'כל ההר, מסלול נבחר (כמו היום: צביעת השיפוע מעליו), גודאורי (Pirveli) ביום ובלילה, ומחשב. בלילה שטח של מסלול שחור נראה כמעט לבן, כמו הקו השחור בלילה היום.',
     [('כל ההר', ['full-map']), ('מסלול נבחר', ['full-run4']), ('גודאורי', ['full-gud']), ('גודאורי בלילה', ['full-gud-night']), ('מחשב', ['full-desk'])]),
    ('today', 'Today', 'היום', 'ב-Sölden: 13 דרכי סקי לא מופיעות בכלל, ושטחי המסלולים הרחבים (26 מסלולים) בשקיפות של 14 אחוז, כמעט בלי לראות. בגודאורי שני שטחים כאלה, באותו מצב.',
     [('Sölden, מקרוב', ['today-zoom']), ('בלילה', ['today-zoom-night']), ('כל ההר', ['today-map']), ('גודאורי, Pirveli', ['today-gud-zoom'])]),
    ('today', 'Why', 'למה לא בצבע של מסלול', 'אותה דרך סקי (61), אם היא נכנסת כמו כל מסלול: אדום, עם לוחית מרובעת. נראה כמו מסלול מוכשר ונבדק, וזה לא נכון. לכן היא ירדה מהמפה עד שיש לה סוג משלה.',
     [('61 כמסלול רגיל', ['today-zoom61'])]),
    ('alt', 'Routes', '1. דרך סקי: סוג משלה', 'קו כתום מקווקו (לא צבע של דרגת קושי), לוחית בצורת מעוין עם המספר, ושם בכתום כשאין מספר (Gaislachalm). בפאנל, במקום הצבע ודרגת הקושי: "סוג · דרך סקי · לא מוכשרת, לא נבדקת", בלי שורות הדירוג וההכשרה. בתלת-ממד אותו כתום, בקווקוו. מקרא קטן על המפה, רק באתר שיש בו דרכי סקי או דרכים מקשרות.',
     [('מבט על', ['prop-zoom61']), ('הפאנל', ['prop-route61']), ('הפאנל בלילה', ['prop-route61-night']), ('תלת-ממד', ['prop-3d61']), ('כל ההר', ['prop-map'])]),
    ('alt', 'Areas', '2. מסלול רחב: שטח שרואים', 'השטח של המסלול בצבע שלו בשקיפות של 30 אחוז (36 בלילה), עם קו מתאר דק ורציף בצבע המסלול. בגודאורי אותו דבר (Pirveli). מסלול נבחר נשאר כמו היום: צביעת השיפוע מכסה. חלופה שלא מומלצת: פסים אלכסוניים, עמוסים מדי ליד הקווים.',
     [('Sölden', ['prop-zoom']), ('בלילה', ['prop-zoom-night']), ('גודאורי, Pirveli', ['prop-gud-zoom']), ('מסלול נבחר', ['prop-run4']), ('חלופה: פסים', ['hatch-zoom'])]),
    ('alt', 'More', '1א. דרכי סקי חסרות ודרך לבקתה', 'במפה הרשמית 12 דרכי סקי, ורק לשש יש קו במפה הפתוחה (60, 63, 64, 65, 73, 81 בלי). ברשימה: הדרכים בקבוצה משלהן עם המעוין, וכמו 5a, החסרות בשורה "דרכי סקי בלי קו במפה". Gaislachalm הוא דרך פרטית לבקתה (במקרא הרשמי סוג נפרד): אותו קו כתום, ובפאנל "דרך לבקתה · פרטית, לא מוכשרת". לא להסתיר: זו דרך אמיתית שגולשים עוברים בה.',
     [('הרשימה', ['prop-missing']), ('דרך חסרה', ['prop-missing63']), ('דרך לבקתה', ['prop-hut'])]),
    ('alt', 'Desk', 'במחשב', 'שני הדברים יחד, באזור של 4, 5 ו-65 מתחת ל-Gaislachkogel.',
     [('מחשב', ['prop-desk-zoom'])]),
]
ROWS = [('leg', 'המקרא שמתקפל (ממתין לאישור)'), ('today', 'היום'), ('built', 'באתר עכשיו'), ('sel', 'מסלול נבחר כולל החלקים הרחבים (אושר, החלטה 69)'), ('off', 'כמו המפה הרשמית 2026/27 (אושר, החלטה 69)'), ('new', 'מסלול רחב בצבע המסלול (אושר, החלטה 69)'), ('alt', 'שתי ההחלטות (2 הוחלף ב-2ב)'), ('text', 'ערכים למימוש')]
TEXT_BOARDS = [
    ('Spec', 'ערכים למימוש', 640, 2200, [
        ('צבע דרך סקי', 'טוקן חדש --p-route: ביום #D96A00 (3.1 מול רקע המפה, 3.5 מול המעטפת הלבנה), בלילה #FF9A3D. טקסט עליו --route-ink #1A1206 (5.3 ביום, 8.8 בלילה). לא צבע של דרגת קושי, ולכן לא נכנס לבורר הצבעים.'),
        ('הקו', 'מעטפת --casing ברוחב 5.5, קו כתום ברוחב 2.8, קווקוו 7 על 5. הדרך המקשרת נשארת כחולה, 2.6, קווקוו 8 על 5: הצבע מבדיל, וגם הלוחית.'),
        ('תלת-ממד', 'אותו כתום, ברוחב של דרך מקשרת (2.8), בקווקוו של כ-45 מ׳ קו ו-28 מ׳ רווח, בשיידר של הקו (בקנבס הקווקוו נחתך בנתונים). התווית בתלת-ממד בכתום.'),
        ('הלוחית', 'מעוין 32 על 32 לספרה, 40 על 32 לשתיים, מילוי --p-route, מסגרת --casing 1.8, המספר ב-Karantina 15 בטוקן --route-ink. אותו כלל מקום כמו הלוחית המרובעת (סבב 23).'),
        ('הפאנל', 'השלט בראש בכתום עם טקסט כהה. שורה "סוג" עם תגית: מעוין קטן ו"דרך סקי · לא מוכשרת, לא נבדקת". בלי "צבע", "דרגת קושי", "דירוג OSM" ו"הכשרה". הפרופיל והשיפוע נשארים: הם מהנתונים.'),
        ('1ב: הקו', 'מעטפת --casing ברוחב 8; מעליה --p-route-edge (#D1342B, בלילה #FF6A5F) ברוחב 6 בקווקוו 2 על 2.5 (השוליים המנוקדים); מעליה --p-route-band (#EE8E1C, בלילה #F5A84B) ברוחב 3.6. הלוחית: מעוין במילוי --p-red, המספר בטוקן --on-board. דרך לבקתה: מעטפת 4 וקו --p-route-edge ברוחב 1.8, קווקוו 4 על 3, בלי לוחית; השם בטוקן --p-route-edge. בתלת-ממד: הרצועה בכתום הבהיר. השלט בפאנל ברקע הכתום הבהיר, עם טקסט כהה.'),
        ('2ג: מסלול נבחר', 'בשכבה של המסלול הנבחר (runpaint), מעל צביעת פני השטח: מעטפת לבנה ברוחב 10 סביב השטח, והמעטפות של הקו מתחת לצבע; אז השטח (מילוי בצבע המסלול, קו מתאר בצבע המסלול ברוחב 6); בב: מעליו תמונת השיפוע של אותו מסלול, חתוכה לצורת השטח ואטומה; ואז הקו בצבעי השיפוע. כך הלבן רק מסביב לצורה כולה.'),
        ('דרך חסרה', 'ברשימה, אחרי המסלולים החסרים: "דרכי סקי בלי קו במפה · N", צ\'יפ עם המעוין. הפאנל: השלט בכתום, "סוג" עם התגית, ההודעה הרגילה על קו חסר. בנתונים: ב-missing עם kind "ski-route".'),
        ('דרך לבקתה', 'אותו קו ואותה לוחית. בפאנל התגית "דרך לבקתה · פרטית, לא מוכשרת". בנתונים: kind "ski-route" ושדה access: "private" (או רשימה בקובץ ההגדרות), לפי המקרא הרשמי.'),
        ('מקרא', 'בפינה של המפה, מעל קנה המידה: דרך סקי, דרך מקשרת, מסלול רחב. רק באתר שיש בו אחד מהם, ונסגר בנגיעה. מחרוזות חדשות בארבע השפות.'),
        ('מקרא שמתקפל (ממתין לאישור)', 'מחליף את המקרא הקבוע. כפתור כמו .inset-toggle: גובה 44, ריפוד 0 14, מסגרת --rule 1.5, רקע --paper, 14px במשקל 600, סמל של 22 על 16 (פס כתום, קו כחול מקווקו, מלבן אדום) ו"מקרא". מקום: bottom 12, inset-inline-end 12 (בעברית משמאל, מול "צד Kobi"). פתוח: קלף מעליו, gap 6, ריפוד 10 12 12, מסגרת --rule 1.5, כותרת "מקרא" ו-X של 44, והשורות של היום; הכפתור עם aria-expanded ומסגרת --ink. קנה המידה עולה ל-bottom 64, ומוסתר כשהקלף פתוח. סגור בכל כניסה. בכל אתר שיש בו אחד מהסוגים, גם בגודאורי (דרך מקשרת, מסלול רחב). מחרוזות: "מקרא" ו"סגירת המקרא".'),
        ('הסינון', 'צ\'יפ חדש "דרכי סקי" אחרי צבעי המסלולים, באותה צורה, רק באתר שיש בו כאלה.'),
        ('שטח מסלול (2ב)', 'מלא בצבע המסלול (fill-opacity 1), עם קו מתאר בצבע המסלול ברוחב הקו (3.4, לא משתנה בזום). סדר הציור: כל המעטפות הלבנות (של הקווים, ומעטפת ברוחב 6.5 סביב כל שטח), אז השטחים, אז הקווים בצבע. כך הלבן עובר רק מסביב לצורה כולה. מחליף את 30 האחוזים.'),
        ('שטח בתלת-ממד', 'היום לא מצויר שם בכלל. ההמלצה: אותו צבע ושקיפות, מצויר על פני השטח כמו שכבת הסביבה. לא צויר בקנבס.'),
        ('נתונים', 'kind: "ski-route" ב-runs-and-lifts.json, בלי צבע ובלי דרגת קושי (כלל דיוק 3: הסוג לפי המקרא של המפה הרשמית, לא הציור). מקור: OSM עם piste:grooming=backcountry, מזהה OSM לכל קטע.'),
    ]),
]
DECIDE = [
    ('מה ביקשו', 'פיני: מפת Sölden "אפילו לא חצי ממה שיש שם". הגאומטריה נכונה; חסרות דרכי הסקי, והמסלולים הרחבים לא נראים. גם בגודאורי.'),
    ('1. דרך סקי', 'קו כתום מקווקו (#D96A00, בלילה #FF9A3D), 2.8 על מעטפת 5.5, קווקוו 7/5. לוחית מעוין עם המספר. בפאנל תגית "דרך סקי · לא מוכשרת, לא נבדקת" במקום צבע ודרגת קושי.'),
    ('2ב. מסלול רחב (אושר, החלטה 69)', 'השטח מלא בצבע המסלול, כהמשך של הקו, עם קו מתאר לבן אחד מסביב לשניהם. מחליף את 30 האחוזים. גם בגודאורי.'),
    ('1א. חסרות ובקתות', 'דרך סקי בלי קו: שורה משלה ברשימה, כמו 5a. Gaislachalm, דרך פרטית לבקתה: אותו קו, ותגית "דרך לבקתה · פרטית, לא מוכשרת".'),
    ('1ב. כמו המפה הרשמית (אושר, החלטה 69)', 'רצועה כתומה בהירה (#EE8E1C, בלילה #F5A84B) עם שוליים מנוקדים באדום, והמספר בלבן במעוין אדום. דרך לבקתה: קו דק מנוקד באדום. מחליף את הקו המקווקו של 1.'),
    ('2ג. מסלול נבחר (אושר, החלטה 69)', 'גם השטח הרחב מסומן כשבוחרים מסלול, עם מסגרת לבנה אחת סביב הקו והשטח, צבוע לפי השיפוע כמו הקו (חלופה ב).'),
    ('ועוד', 'מקרא קטן וצ\'יפ סינון "דרכי סקי", רק באתר שיש בו כאלה. בתלת-ממד אותו כתום בקווקוו.'),
    ('המקרא שמתקפל (חדש, ממתין)', 'כפתור "מקרא" באותו רכיב כמו "צד Kobi", בפינה השנייה של המפה, שנפתח לקלף בנגיעה. לא מכסה את המפה ואת קנה המידה, ונכנס גם לגודאורי.'),
    ('בתוקף', 'אושר (פיני, 7.10.2026, החלטה 69): 1ב, 2ב ו-2ג בחלופה ב. 1ב ו-2ב מחליפים את 1 ו-2. סשן המנהל מממש.'),
]
GAP, PAD, HEAD, CGAP, DW, DH = 18, 20, 150, 28, 600, 1160


def webp():
    out = here / 'shots/web'
    out.mkdir(exist_ok=True)
    for name, (f, w, top, h) in SRC.items():
        args = ['convert', str(here / f), '-resize', f'{w}x']
        if h:
            args += ['-crop', f'{w}x{h}+0+{top}', '+repage']
        subprocess.run(args + ['-quality', '88', str(out / N(name))], check=True)
    return len(SRC)


def size(f):
    w, h = subprocess.check_output(['identify', '-format', '%w %h', str(here / 'shots/web' / N(f))]).split()
    return int(w), int(h)


def board(b, urls):
    group, key, title, note, cols = b
    cells = []
    for label, ims in cols:
        parts = [(f'<img src="{urls[N(i)]}" alt="{html.escape(label)}" style="display: block; width: {size(i)[0]}px; height: {size(i)[1]}px; box-shadow: 0 0 0 1px #CBD5DF">', *size(i)) for i in ims]
        w, h = max(p[1] for p in parts), sum(p[2] for p in parts) + GAP * (len(parts) - 1) + 34
        cells.append((f'<div style="display: flex; flex-direction: column; gap: {GAP}px; width: {w}px">'
                      f'<b style="height: 16px; font-size: 15px; color: #1F5FC4">{html.escape(label)}</b>{"".join(p[0] for p in parts)}</div>', w, h))
    w = sum(c[1] for c in cells) + CGAP * (len(cells) - 1) + 2 * PAD
    key_html = ''
    head_h = HEAD + (28 if key_html else 0)
    h = PAD + head_h + max(c[2] for c in cells) + PAD
    head = (f'<div style="display: flex; flex-direction: column; gap: 6px; height: {head_h - 12}px; max-width: {max(w - 2 * PAD, 600)}px">'
            f"<b style=\"font-family: Karantina, 'Arial Narrow', sans-serif; font-size: 34px; line-height: 1\">{html.escape(title)}</b>"
            f'<span style="font-size: 14px; line-height: 1.45; color: #4B5A6F">{html.escape(note)}</span>{key_html}</div>')
    body = f'<div style="display: flex; gap: {CGAP}px; align-items: flex-start">{"".join(c[0] for c in cells)}</div>'
    return (f'<div style="width: {w}px; height: {h}px; box-sizing: border-box; padding: {PAD}px; display: flex; flex-direction: column; gap: 12px; '
            f"background: #FFFFFF; color: #13233A; font-family: 'IBM Plex Sans Hebrew', system-ui, sans-serif; direction: rtl\">{head}{body}</div>"), w, h


def md(s):
    return ''.join(f'<b style="color: #13233A">{p}</b>' if i % 2 else p for i, p in enumerate(html.escape(s).split('**')))


def card(title, items, w, h, lead='', accent='#1F5FC4'):
    lis = ''.join(f'<li style="display: flex; flex-direction: column; gap: 2px; padding: 9px 0; border-top: 1px solid #CBD5DF">'
                  f'<b style="font-size: 16px">{html.escape(t)}</b><span style="font-size: 14px; line-height: 1.55; color: #4B5A6F">{md(s)}</span></li>' for t, s in items)
    return (f'<div style="width: {w}px; height: {h}px; box-sizing: border-box; padding: 28px; background: #FFFFFF; color: #13233A; '
            f"font-family: 'IBM Plex Sans Hebrew', system-ui, sans-serif; direction: rtl; border-top: 8px solid {accent}\">"
            f"<h1 style=\"margin: 0; font-family: Karantina, 'Arial Narrow', sans-serif; font-size: 48px; line-height: 1\">{html.escape(title)}</h1>"
            + (f'<p style="margin: 8px 0 12px; font-size: 14.5px; line-height: 1.55; color: #4B5A6F">{lead}</p>' if lead else '<div style="height: 10px"></div>')
            + f'<ol style="margin: 0; padding: 0; list-style: none">{lis}</ol></div>')


def build(dst):
    urls = json.loads((here / 'shots/assets.json').read_text(encoding='utf-8'))
    lead = ('שתי החלטות למפה, מצוירות מעל האתר האמיתי. הדרכים מהמפה הפתוחה (OSM), שום קו לא צויר ביד.')
    boards = [('R24-0-Decide.dc.html', 'ההחלטות בסבב 24', page('ההחלטות בסבב 24', card('ההחלטות בסבב 24', DECIDE, DW, DH, lead), DW, DH), DW, DH, None)]
    for b in BOARDS:
        c, w, h = board(b, urls)
        boards.append((f'R24-{b[1]}.dc.html', b[2], page(b[2], c, w, h), w, h, b[0]))
    for key, title, w, h, items in TEXT_BOARDS:
        boards.append((f'R24-{key}.dc.html', title, page(title, card(title, items, w, h, accent='#13233A'), w, h), w, h, 'text'))
    for f, _, html_, *_ in boards:
        (dst / f).write_text(html_, encoding='utf-8')
    ip = dst / 'canvas.json'
    index = json.loads(ip.read_text(encoding='utf-8'))
    if not any(pg['id'] == PAGE[0] for pg in index['pages']):
        index['pages'].insert(0, {'id': PAGE[0], 'name': PAGE[1]})
    names = {b[0] for b in boards}
    for f in [f for f, b in index['boards'].items() if b.get('page') == PAGE[0] and f not in names]:
        del index['boards'][f]
        if f in index['order']:
            index['order'].remove(f)
    for n in [n for n in index['notes'] if n.startswith(PAGE[0] + 'row')]:
        del index['notes'][n]
    index['boards'][boards[0][0]] = {'x': 0, 'y': 0, 'w': DW, 'h': DH, 'page': PAGE[0], 'title': boards[0][1]}
    if boards[0][0] not in index['order']:
        index['order'].append(boards[0][0])
    y = 0
    for ri, (group, title) in enumerate(ROWS):
        x, rowh = DW + 80, 0
        for f, t, _, w, h, g in boards:
            if g != group:
                continue
            index['boards'][f] = {'x': x, 'y': y, 'w': w, 'h': h, 'page': PAGE[0], 'title': t}
            if f not in index['order']:
                index['order'].append(f)
            x += w + 80
            rowh = max(rowh, h)
        index['notes'][f'{PAGE[0]}row{ri}'] = {'kind': 'title1', 'maxW': max(860, x - DW - 160), 'page': PAGE[0], 'text': title, 'w': 240, 'x': DW + 80, 'y': y - 300}
        y += rowh + 120 + 300
    index['launch'] = {'view': 'canvas', 'page': PAGE[0]}
    ip.write_text(json.dumps(index, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    return [b[0] for b in boards]


if __name__ == '__main__':
    a = sys.argv[1:]
    if a == ['webp']:
        print(webp(), 'shots -> webp')
    else:
        dst = pathlib.Path(a[0]) if a else root / 'design/canvas/project'
        print(len(build(dst)), 'boards ->', dst)
