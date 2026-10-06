#!/usr/bin/env python3
"""Round 20: classify every visible text found by inventory.mjs, and mark the full-page shots by class.

Classes (Pini, 6.10.2026: "less text, more experience"):
  del   unnecessary: delete
  shape an explanation a shape, icon, motion or a good empty state can replace
  must  needed: keep, but present it with experience that fits the place
  legal legal or credits: not counted (privacy, "unofficial", credits)
  keep  labels, buttons, names and data (numbers, times, codes): what the screen is made of

Short texts (up to 3 words) are labels or data unless a rule below says otherwise. Longer ones need a rule; a
long text without a rule is reported, so nothing slips through. Each rule: (substring, class, what replaces it).
Run from the repo root:  python3 design/round20/classify.py <folder with inventory.json and inv/*.png>
Writes classified.json and marked/*.png there, and prints the totals."""
import json, pathlib, subprocess, sys

RULES = [
    # home
    ('נגיעה בכרטיס שמאחור', 'shape', 'הכרטיס שמאחור מציץ ונע קלות בפעם הראשונה; קו הקריעה מהבהב פעם אחת'),
    ('27 מסלולים, פרטים וסרטונים', 'shape', 'המספר 27 כתג על השלט, בלי משפט'),
    ('בוחרים תחנה ושעה', 'del', 'השם "נקודת מפגש" והסיכה על השלט מספיקים'),
    ('הירידה, בית הספר לסקי', 'del', 'השם "משחקים" מספיק; השמות בעמוד המשחקים'),
    ('יצירת קבוצה, או הצטרפות', 'del', 'השם "קבוצה" מספיק'),
    ('בלי הרשמה. נשמר רק בדפדפן', 'must', 'מנעול קטן ו"רק במכשיר הזה", על הכפתור'),
    ('כרגע אין דיווח עדכני מ-MTA', 'shape', 'השלט המושלג עצמו הוא ההודעה; מילה אחת: "אין דיווח"'),
    # map
    ('כשאין דיווח עדכני, כתוב כאן', 'del', 'כפילות של פס המצב'),
    ('לחצו על מסלול במפה', 'shape', 'מקרא של דוגמאות קו (מקווקו דק, מקווקו כחול) במקום משפט; "לחצו" מיותר'),
    ('מסלולים שנמצא רק חלק', 'shape', 'סימן "חלקי" ליד המסלול ברשימה; ההסבר בפאנל שלו'),
    ('קווים: OpenStreetMap', 'legal', 'קרדיט: עובר לאודות, עם קישור קטן "מקורות" במפה'),
    ('במבט התלת-ממדי: גרירה', 'shape', 'הדגמת מחוות של שתי שניות בכניסה הראשונה, ואז נעלמת'),
    ('עוד אין מידע על הרכבלים', 'must', 'פס המצב: נקודה אפורה ו"אין דיווח"'),
    # run panel
    ('מ׳ למעלה, מ׳ למטה', 'keep', ''),
    ('(100 מ׳ התלולים', 'shape', 'הקטע מודגש על הפרופיל'),
    ('ב-150 המטרים הראשונים', 'shape', 'שלושה סימנים על הפרופיל: התחלה, הקטע התלול, הסוף, עם המספרים'),
    ('הקטע התלול · אחרי', 'shape', 'סימן על הפרופיל'),
    ('לאורך 100 מ׳', 'shape', 'סימן על הפרופיל'),
    ('מגיעים לרכבל', 'shape', 'סמל רכבל בסוף הפרופיל'),
    ('תלול בערך כמו', 'shape', 'שני פסים קטנים מול המסלולים שבחרת להשוות'),
    ('פני השטח עד 150 מ׳', 'shape', 'המקרא בצבעים עם המעלות, וסמל (i) שפותח את ההסבר'),
    ('גבהים מתוך מודל פני השטח', 'shape', 'סמל (i) ליד הגבהים'),
    ('חושב מקרבת קצוות', 'shape', 'סמל (i) ליד החיבורים'),
    ('דרך ב-OSM שנשאה', 'must', 'כלל דיוק 5: ודאות ומקור מוצגים. כמד ודאות של שלוש נקודות וסמל מקור, והפירוט בנגיעה'),
    ('הקלטות ציבוריות עוברות', 'must', 'כמו למעלה: סמל GPS ליד מד הוודאות'),
    ('קו פנימי מאזור התחנה', 'must', 'כמו למעלה: ההערה נפתחת בנגיעה'),
    ('api.openstreetmap.org', 'must', 'מקור: סמל וקישור, הכתובת המלאה בנגיעה'),
    ('המפה הרשמית של MTA (התמונה', 'must', 'מקור: סמל וקישור'),
    ('ביוטיוב ↗', 'keep', ''), ('Tatra 2 Slope', 'keep', ''), ('Спуск по', 'keep', ''), ('מדידת שימוש ודיווח שגיאות', 'keep', ''),
    # meet
    ('התחנה התחתונה של', 'keep', ''),
    ('לחיצה על שטח ריק במפה', 'del', 'ה"החזרה" שמופיעה אחרי ביטול מספיקה'),
    ('לפי החיבורים בנתוני המסלולים', 'shape', 'סמל (i) ליד "איך מגיעים"'),
    ('הקישור עובד בלי הרשמה', 'del', 'מיותר: מי שמקבל קישור פשוט פותח אותו'),
    # games
    ('על השלג של גודאורי. השיאים', 'shape', 'סמל רמקול ליד הכותרת ("עם צליל"); השאר מיותר'),
    ('הירידה על המסלולים האמיתיים', 'del', 'התמונה מספרת'),
    ('מעצירה בפיצה ועד קרווינג', 'del', 'התמונה מספרת'),
    ('לגעת בשלג ולרסק', 'del', 'התמונה מספרת'),
    ('מפתית ועד מלך קזבק', 'del', 'התמונה מספרת'),
    ('מאחורי החומה, מול', 'del', 'התמונה מספרת'),
    # about and account
    ('SKI PASS', 'keep', ''),
    ('אורח בקבוצה, בלי חשבון', 'keep', ''),
    ('אורח בקבוצה: הדפדפן הזה זוכר', 'must', 'חשוב (אפשר לאבד את המקום): תג אזהרה קטן על הסקי־פס וכפתור "שמירה עם גוגל"'),
    ('בטלפונים שתומכים', 'shape', 'לא להציג את השורה במכשיר שלא תומך, במקום להסביר'),
    ('אוטומטי לפי השעה בגודאורי', 'del', 'הערך שמימין אומר אותו דבר'),
    ('נבחרה כאן, ונשמרת', 'del', 'הערך שמימין מספיק'),
    ('אנונימי ובלי עוגיות', 'must', 'פרטיות: נשאר, קצר, עם הקישור "מה נאסף"'),
    ('השיאים שנשמרו בדפדפן', 'del', 'השם "איפוס השיאים" מספיק; שתי הנגיעות מגינות'),
    ('תכנן ובנה את האתר', 'keep', ''),
    ('AWS Terrain', 'legal', ''), ('Karantina, IBM', 'legal', ''), ('supabase-js', 'legal', ''), ('שייכים ליוצרים', 'legal', ''),
    ('המפה הרשמית של MTA שימשה', 'legal', ''), ('לא רשמיים', 'legal', ''),
    # trip form
    ('רק תאריך ההלוך חובה', 'must', 'בטופס החדש: שדה אחד ראשון; מנעול קטן ו"רק במכשיר הזה"'),
    ('ימי סקי מלאים, מחושב', 'shape', 'ימי הסקי מסומנים על פס התאריכים'),
    # sign in and account
    ('בלי סיסמה, ובלי מיילים', 'must', 'שלושה סמלים קצרים: בלי סיסמה, בלי מיילים, רק השם'),
    ('ליצור קבוצה ולהזמין', 'shape', 'סמל וכותרת קצרה'),
    ('הטיול והקבוצה, גם באפליקציה', 'shape', 'סמל סנכרון וכותרת קצרה'),
    ('כל השאר עובד בלי חשבון', 'del', 'הכפתור "לא עכשיו" אומר את זה'),
    ('לא עכשיו, להמשיך כאורח', 'keep', ''),
    ('מה שכבר שמור בדפדפן הזה', 'must', 'שורה קטנה עם סמל סנכרון'),
    ('כמה דרכים לאותו חשבון', 'del', 'הכותרת "דרכי כניסה" מספיקה'),
    ('מסונכרנים בין האתר לאפליקציה', 'del', 'אפשר סמל ליד הכותרת'),
    # group
    ('חברים · 10.1 עד', 'keep', ''), ('גודאורי 2027 · 10.1', 'keep', ''),
    ('רק חברי הקבוצה רואים', 'shape', 'מנעול קטן ליד שם הקבוצה'),
    ('קבוצה היא דף משותף', 'shape', 'שלושה סמלים: טיסות, מפגשים, שיאים'),
    ('יצירת קבוצה דורשת חשבון', 'must', 'סמלי גוגל ואפל על הכפתור'),
    ('הבקשה נשלחה למנהל', 'must', 'מצב "ממתין" עם שעון חול'),
    ('בלי הרשמה. הדפדפן הזה זוכר', 'del', 'מיותר בשלב ההצטרפות'),
    ('החלפת טלפון או דפדפן', 'del', 'הקישור "אני כבר בקבוצה" מספיק'),
    ('יש לך את האפליקציה', 'must', 'שורה קצרה עם סמל האפליקציה והקוד'),
    ('אני על אותה טיסה', 'keep', ''),
]
COLORS = {'del': '#D5372E', 'shape': '#E8A21A', 'must': '#1F5FC4', 'legal': '#8A97A8'}


def klass(t, words):
    for sub, k, why in RULES:
        if sub in t:
            return k, why
    return ('keep', '') if words <= 3 else ('?', '')


def main(folder):
    d = pathlib.Path(folder)
    inv = json.loads((d / 'inventory.json').read_text(encoding='utf-8'))
    (d / 'marked').mkdir(exist_ok=True)
    out, unknown, tot = [], set(), {}
    for s in inv:
        if s['state'] == 'map-board':
            continue
        by = {}
        for it in s['items']:
            it['cls'], it['why'] = klass(it['text'], it['words'])
            if it['cls'] == '?':
                unknown.add(it['text'][:80])
            by[it['cls']] = by.get(it['cls'], 0) + it['words']
            tot[it['cls']] = tot.get(it['cls'], 0) + it['words']
        out.append({'state': s['state'], 'words': s['words'], 'by': by, 'items': s['items']})
        draw = []
        for it in s['items']:
            c = COLORS.get(it['cls'])
            if c:
                x0, y0, x1, y1 = int(it['x']) - 2, int(it['y']) - 1, int(it['x'] + it['w']) + 2, int(it['y'] + it['h']) + 1
                draw += ['-fill', c + '33', '-stroke', c, '-strokewidth', '2', '-draw', f'rectangle {x0},{y0} {x1},{y1}']
        subprocess.run(['convert', str(d / 'inv' / (s['state'] + '.png'))] + draw + [str(d / 'marked' / (s['state'] + '.png'))], check=True)
        print(f"{s['state']:14} {s['words']:4}  " + '  '.join(f'{k} {v}' for k, v in sorted(by.items())))
    (d / 'classified.json').write_text(json.dumps(out, ensure_ascii=False, indent=1), encoding='utf-8')
    print('total', tot)
    for u in sorted(unknown):
        print('  unclassified:', u)


if __name__ == '__main__':
    main(sys.argv[1])
