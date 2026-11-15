#!/usr/bin/env python3
"""Round 40: ski-pass prices (MTA 26/27). Schematic HTML mocks (price.html) rendered by shots.mjs. Run from the repo root:
  python3 design/round40/build.py webp
  python3 design/round40/build.py [canvas project folder]"""
import html, json, re, pathlib, subprocess, sys

here = pathlib.Path(__file__).resolve().parent
root = here.parent.parent
sys.path.insert(0, str(root / 'design/round12'))
from build import page  # the board shell of rounds 12 to 25

PAGE = ('round40', 'סבב 40: מחירי כרטיס הסקי')
names = [f'{v}-{p}' for v in 'abc' for p in ['ios-he-day','and-he-day','ios-he-night','and-en-night']] + ['s-d1-ios-he-day','s-d7-and-en-day','s-d8-ios-he-day','s-kid-and-en-day','s-stale-ios-he-day']
SRC = {n: ('shots/' + n + '.png', 390, 0, 0) for n in names}
N = lambda f: f + '.webp'
def alt(k): return [('iPhone, עברית, יום', [f'{k}-ios-he-day']), ('Android, עברית, יום', [f'{k}-and-he-day']), ('iPhone, עברית, לילה', [f'{k}-ios-he-night']), ('Android, English, night', [f'{k}-and-en-night'])]
BOARDS = [
    ('a', 'A', 'א. שורת מחיר בלוח הבית, ליד הספירה לפתיחה', 'שורה אחת מתחת לספירה: "כרטיס 4 ימים 390 ₾" (לפי ימי הסקי של הטיול), ובנגיעה נפתח מסך המחירים. הכי שקט, ומתיישב עם סבב 40 (הספירה והמחיר באותו לוח). **סכמה בלבד.**', alt('a')),
    ('b', 'B', 'ב. מסך "כרטיס סקי": כמה עולה לך (מומלץ)', 'מחשבון קצר: מבוגר או ילד, כמה ימי סקי (מתמלא מ"הטיול שלך"), המחיר בגדול, ומתחתיו ההשוואה לכרטיס יומי וכמה חוסכים. מתחת: סקי לילה, הכרטיס הפיזי והפיקדון, "מחיר לזר", איפה קונים, והמקור והתאריך.', alt('b')),
    ('c', 'C', 'ג. כרטיס הטיול: ספח עם ימי הסקי מנוקבים', 'כרטיס שמאחד את הטיול והמחיר: ארבעה משבצות, אחת לכל יום סקי, והמחיר והחיסכון. חוויתי ומתחבר לכרטיס הטיסה, אבל קורא כרטיס אחד מול מחיר אחד ופחות מחשבון. **סכמה בלבד.**', alt('c')),
]
BOARDS.append(('s', 'States', 'ב (המומלץ): מצבים', 'יום אחד; 7 ימים (MultiSki, כל ארבעת האתרים); 8 ימים (אין כרטיס כזה: 7 ימים ועוד יום אחד זול מ-10 ימים); ילד; ו"אין מידע" כשהמחירון לא נבדק זמן רב. **אין המרה לשקלים:** ראו את הערכים למימוש.',
    [('יום אחד', ['s-d1-ios-he-day']), ('7 ימים', ['s-d7-and-en-day']), ('8 ימים', ['s-d8-ios-he-day']), ('ילד', ['s-kid-and-en-day']), ('אין מידע', ['s-stale-ios-he-day'])]))
ROWS = [('a', 'א. שורה בבית'), ('b', 'ב. מסך כרטיס סקי'), ('c', 'ג. כרטיס הטיול'), ('s', 'מצבים')]
TEXT_BOARDS = [('Spec', 'ערכים למימוש', 600, 1250, [
    ('מקור הנתונים', 'המחירון הרשמי של MTA, 26/27 ([mta.ski/en/prices](https://mta.ski/en/prices)), מ-15.11.2026; הפירוט והמקורות ב-`research/practical-info.md`. כל מחיר מוצג עם "MTA · mta.ski · עודכן ב-<תאריך>". המספרים נלקחים מהקובץ, לא מהקוד.'),
    ('קובץ נתונים', 'קובץ נתונים נפרד שמתעדכן בלי גרסה בחנות (`prices` לגודאורי: מוצר, זר, ילד, מקור, תאריך עדכון), לפי אותו עיקרון כמו `season.open` בסבב 40. הצורה והחוזה: סקירת ארכיטקט לפני קוד. מחיר שאין לו מקור מאומת: "אין מידע".'),
    ('החישוב', 'מחיר = הערך בטבלה לימים שנבחרו. אין כרטיס ל-8 ו-9 ימים: מציגים את הזול מבין 7 ימים ועוד כרטיס לשאר, או 10 ימים, ואומרים את זה במשפט. החיסכון = יומי × ימים פחות המחיר. לא מחשבים מע"מ ולא עמלות מסוף (כלולים; עמלת מסוף 2 ₾ או 1.25 אחוז מוזכרת רק בטקסט קטן אם יידרש).'),
    ('שקלים: המלצה, לא בגרסה הזו', 'המרה דורשת מקור לשער ותאריך, והשער זז. המלצה: להציג רק לארי, ובשורה קטנה "בערך X ₪" רק אם יימצא מקור שער רשמי ותאריך. אם יוחלט להוסיף: סמן "הערכה" ותאריך, ולא כמחיר.'),
    ('ימי הסקי', 'מתמלא מ"הטיול שלך" (ימי הסקי שחושבו), עם אפשרות לשנות 1 עד 7 ו-10. בלי טיול: ברירת מחדל 1, וכותרת "ימי סקי" במקום "מהטיול שלך". ילד: 6 עד 12; מתחת לגיל 6 חינם, ונכתב בשורה קטנה.'),
    ('מה חייב להיכתב', 'כרטיס פיזי חובה (QR לא מתקבל), פיקדון 5 ₾ שמוחזר בקופה, "מחיר לזר" (לתושבי גאורגיה מחיר אחר; הנחת סטודנט רק לאזרחי גאורגיה, לכן לא מוצגת), סקי לילה כרטיס נפרד (80 ₾), וכרטיס יומי לא כולל לילה.'),
    ('איפה קונים', 'לא קונים דרכנו. "בקופות האתרים" (קישורי קנייה רשמיים "יפורסמו בנפרד" לפי MTA), וקישור משני לאפליקציה הרשמית SKIPASS של Lemondo (App Store `id6698866856`, Google Play `com.lemondo.skipass`), בלי שיתוף נתונים. אם MTA תפרסם קישור קנייה באתר: מחליפים.'),
    ('מבצע', 'מבצע 30 אחוז על עונתי MultiSki (מ-20.11.2026, רק בכרטיס TBC Visa Premium, באתר `mta.ski`) לא נכנס למחשבון, כי הוא מוגבל לכרטיס אשראי מסוים ולעונתי בלבד. אפשר שורה קטנה בקנבס אחרי האישור, עם מקור.'),
    ('תנועה ונגישות', 'הפסים בהשוואה נמתחים 300 מ״ש, ומכבדים הפחתת תנועה. כל מחיר גם כטקסט לקורא מסך ("390 לארי לארבעה ימים"), יעדי מגע 48 לכפתורי המחשבון.'),
])]
DECIDE = [
    ('מה ביקשו', 'פיני (דרך סשן המנהל, 15.11.2026): מחירי כרטיס הסקי בצורה יפה וחווייתית, במקביל לסבב 40, כך ששניהם מתיישבים יחד. אפליקציות קודם, האתר אחר כך.'),
    ('המלצה: ב, מסך "כרטיס סקי", עם שורת כניסה בבית (א)', 'משתמש לא רוצה טבלה אלא תשובה: כמה עולה לי. ארבעה ימי סקי (מ"הטיול שלך") מול יומי: 390 ₾ במקום 480, חוסכים 90. השורה בבית (א) יושבת מתחת לספירה לפתיחה של סבב 40, ופותחת את המסך. ג יפה, אבל קורא כמו כרטיס אחד ולא מחשבון.'),
    ('שאלה 1: איזו חלופה', 'א, ב או ג, או שילוב. המלצה: ב עם כניסה מ-א.'),
    ('שאלה 2: שקלים', 'המלצה: לא עכשיו. דורש מקור לשער, והשער זז; לארי בלבד, עם מקור ותאריך.'),
    ('שאלה 3: איפה זה גר', 'שורה בלוח הבית, ליד הספירה, ומסך נפרד שנפתח ממנה. המלצה: כן, ואחרי יום הפתיחה השורה נשארת מתחת ללוח הרכבלים.'),
    ('שאלה 4: איך קונים', 'בקופות, עם קישור משני ל-SKIPASS, בלי לקנות דרכנו. המלצה: כן.'),
    ('שאלה 5: קובץ נתונים', 'קובץ מחירים שמתעדכן בלי גרסה, עם מקור ותאריך לכל מחיר, ו"אין מידע" כשחסר. סקירת ארכיטקט לפני קוד. המלצה: כן.'),
    ('אזהרות', 'כל התמונות סכמה ולא צילום אפליקציה. המחירים מ-`research/practical-info.md` ומהמחירון הרשמי, ללא המרה. אין קוד עד האישור.'),
    ('אחרי האישור', 'סקירת ארכיטקט לקובץ המחירים (ביחד עם `season` של סבב 40); אחר כך אנדרואיד ואייפון במקביל, ואחריהם האתר.'),
]
GAP, PAD, HEAD, CGAP, DW, DH = 18, 20, 150, 28, 600, 1000

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
    s = ''.join(f'<b style="color: #13233A">{p}</b>' if i % 2 else p for i, p in enumerate(html.escape(s).split('**')))
    return re.sub(r'`([^`]+)`', r'<code dir="ltr" style="font-size: 12.5px; unicode-bidi: isolate; word-break: break-all">\1</code>', s)


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
    lead = 'מחירי כרטיס הסקי לעונת 26/27: שלוש חלופות, המלצה, מצבים וערכים למימוש.'
    boards = [('R40-0-Decide.dc.html', 'סבב 40: נדחה', page('סבב 40: נדחה', card('סבב 40: נדחה', DECIDE, DW, DH, lead), DW, DH), DW, DH, None)]
    for b in BOARDS:
        c, w, h = board(b, urls)
        boards.append((f'R40-{b[1]}.dc.html', b[2], page(b[2], c, w, h), w, h, b[0]))
    for key, title, w, h, items in TEXT_BOARDS:
        boards.append((f'R40-{key}.dc.html', title, page(title, card(title, items, w, h, accent='#13233A'), w, h), w, h, 'text'))
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
