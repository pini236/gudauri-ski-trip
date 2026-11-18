#!/usr/bin/env python3
"""Round 39: countdown to the ski-season opening (20.12.2026). Schematic HTML mocks (open.html) rendered by shots.mjs. Run from the repo root:
  python3 design/round39/build.py webp
  python3 design/round39/build.py [canvas project folder]"""
import html, json, re, pathlib, subprocess, sys

here = pathlib.Path(__file__).resolve().parent
root = here.parent.parent
sys.path.insert(0, str(root / 'design/round12'))
from build import page  # the board shell of rounds 12 to 25

PAGE = ('round39', 'סבב 39: ספירה לאחור לפתיחת העונה')
names = ['a-ios-he-day','a-and-he-day','a-ios-he-night','a-and-en-night','b-ios-he-day','b-and-he-day','b-ios-he-night','b-and-en-night','c-ios-he-day','c-and-he-day','c-ios-he-night','c-and-en-night']
names += [f's-{s}-{p}' for s in ['eve','today','live','late','moved','after'] for p in ['ios-he-day','and-en-day']] + ['s-far-notrip-ios-he-day']
SRC = {n: ('shots/' + n + '.png', 390, 0, 0) for n in names}
N = lambda f: f + '.webp'
def alt(k): return [('iPhone, עברית, יום', [f'{k}-ios-he-day']), ('Android, עברית, יום', [f'{k}-and-he-day']), ('iPhone, עברית, לילה', [f'{k}-ios-he-night']), ('Android, English, night', [f'{k}-and-en-night'])]
BOARDS = [
    ('a', 'A', 'א. השלט מונה: המספר על השלט המושלג', 'הלוח של היום ("ההר עוד ישן") נשאר אותו שלט, וקיבל מספר גדול של ימים ושורה אחת: "פתיחה מתוכננת 20.12". הכי שקט, והכי קרוב למה שיש. **סכמה בלבד:** לא צילום אפליקציה; האנדרואיד מרובע והאייפון מעוגל, כמו היום.', alt('a')),
    ('b', 'B', 'ב. הרכבל עולה: המספר ומתחתיו הרכבל שמתקדם לפסגה (מומלץ)', 'מספר הימים בגדול, ומתחתיו רכס עם כבל: הקרון מתקדם מהכפר לפסגה לפי הימים שעברו מההכרזה (28.10) עד הפתיחה, ותנודה עדינה שנעצרת בהפחתת תנועה. ביום הפתיחה הקרון מגיע לפסגה והרכס הופך ללוח הרכבלים החי. מתחבר לסמל של המוצר (רכבל), ועובד גם כשיש כרטיס טיסה מעליו.', alt('b')),
    ('c', 'C', 'ג. פס הימים: 53 ימים כפס ניקוב', 'שורה צרה של 53 פסים, אחד לכל יום מההכרזה ועד הפתיחה; הימים שעברו צבועים והיום מודגש. קומפקטי (חוסך גובה בדף הבית), אבל פחות חוויה. **סכמה בלבד.**', alt('c')),
]
STATES = [('eve', 'מחר פותחים'), ('today', 'יום הפתיחה, בלי דיווח'), ('live', 'יש דיווח: לוח הרכבלים החי'), ('late', 'עבר התאריך ואין דיווח'), ('moved', 'MTA הזיזה את התאריך'), ('after', 'סוף העונה')]
BOARDS.append(('s', 'States', 'ב (המומלצת): מה קורה לאורך הדרך, באייפון בעברית וב-Android באנגלית', 'מחר פותחים; בוקר הפתיחה לפני דיווח; יש דיווח (הספירה הופכת ללוח הרכבלים שכבר קיים); עבר התאריך ואין דיווח (חוזרים ל"אין מידע עדכני", בלי להמציא); התאריך הוזז; וסוף העונה (בלי ספירה עד שיש תאריך).',
    [(t, [f's-{k}-ios-he-day', f's-{k}-and-en-day']) for k, t in STATES]))
BOARDS.append(('t', 'Trip', 'וכשאין טיול: אותו לוח, בלי כרטיס הטיסה', 'בלי טיול הלוח נשאר בלי שינוי, והוא מקבל את המקום של הכרטיס. עם טיול: שני מספרים, בסדר קבוע: הלוח הגדול (הפתיחה) והכרטיס קטן מעליו (הטיסה), כי הפתיחה קודמת לטיסה ב-21 יום.',
    [('עם טיול (יום 1)', ['b-ios-he-day']), ('בלי טיול', ['s-far-notrip-ios-he-day'])]))
ROWS = [('a', 'א. השלט מונה'), ('b', 'ב. הרכבל עולה'), ('c', 'ג. פס הימים'), ('s', 'מצבים לאורך הדרך'), ('t', 'עם טיול ובלי')]
TEXT_BOARDS = [('Spec', 'ערכים למימוש', 600, 1150, [
    ('מקור אחד לתאריך', 'בנתונים ולא בקוד: `season: {open, close, note, src, updated}` לגודאורי ב-`site/data/resorts.json` (תוספת בלבד, בלי העלאת `schema`; האפליקציות מתעדכנות בלי גרסה חדשה). התאריך הנוכחי 20.12.2026, מקור הודעת MTA מ-28.10.2026. אם אין תאריך, אין ספירה. הצורה והחוזה: סקירת ארכיטקט לפני קוד.'),
    ('מתוכננת, לא מובטחת', 'תמיד "פתיחה מתוכננת" ומקור קטן ("לפי MTA"). אם MTA מזיזה, הלוח אומר "הפתיחה נדחתה ל-X" והקרון חוזר אחורה. עבר התאריך ואין דיווח: חוזרים לשלט "ההר עוד ישן" ול"אין מידע עדכני", בלי "מתעכב" שלא ידוע.'),
    ('התקדמות הקרון', 'מ-28.10 (ההכרזה) עד תאריך הפתיחה, ליניארי לפי ימים. אם התאריך הוזז, ההתחלה נשארת והקרון זז אחורה. מספר הימים: ימי לוח בשעון גודאורי, "מחר" ביום האחרון.'),
    ('יום הפתיחה', 'כשיש דיווח עדכני (S-20), הספירה נעלמת והלוח הופך ללוח הרכבלים הקיים ("10 מתוך 12 פתוחים"), באותו מקום, בלי מסך חדש. בלי דיווח: "היום פותחים · ממתינים לדיווח", ו"—" ברכבלים.'),
    ('אחרי העונה', 'השלט "העונה נגמרה · נתראה בחורף הבא", בלי ספירה עד שמתפרסם תאריך חדש. לא מנחשים תאריך.'),
    ('תנועה ונגישות', 'תנודה עדינה של הקרון (3 שניות), נעצרת בהפחתת תנועה והמיקום נשאר. הקרון והרכס הם קישוט: המספר והתאריך בטקסט לקורא מסך ("35 ימים לפתיחה המתוכננת, 20 בדצמבר"). ניגודיות כמו היום; גובה מינימלי 48 לנגיעה אם הלוח לחיץ.'),
    ('אפליקציות מול אתר', 'אנדרואיד ואייפון: כל אחת בסגנון שלה (מרובע מול מעוגל, כמו היום). האתר בעדיפות נמוכה: אותו לוח בדף הבית, אחרי האפליקציות. בלי שינוי בדף הבית (החלטה 67).'),
])]
DECIDE = [
    ('אושר, נמסר למימוש (18.11.2026)', 'פיני אישר (דרך סשן המנהל) לפי ההמלצה: ב, הרכבל עולה. בשפת העיצוב של היום. סקירת ארכיטקט לשדה `season`, ואז אנדרואיד ואייפון במקביל, ואחריהם האתר. בלי קוד לפני הסקירה.'),
    ('מה ביקשו', 'פיני (דרך סשן המנהל, 15.11.2026): ספירה לאחור לפתיחת עונת הסקי בגודאורי, בצורה יפה וחווייתית. פתיחה רשמית 20.12.2026 (MTA, 28.10.2026), בעוד 35 יום. אפליקציות קודם, האתר אחר כך.'),
    ('המלצה: ב, הרכבל עולה, בתוך הלוח שכבר קיים', 'הספירה שייכת ללוח מצב העונה, לא לכרטיס הטיסה: הלוח כבר שאל "מתי הרכבלים עולים". הקרון שמתקדם לפסגה נותן תחושה של התקדמות (לא רק מספר יורד), הוא מהעולם של המוצר, וביום הפתיחה הפסגה מתחלפת בלוח הרכבלים. א פשוטה יותר לבנייה ושקטה; ג קומפקטית אבל חסרת חוויה.'),
    ('שאלה 1: איזו חלופה', 'א, ב או ג. המלצה: ב.'),
    ('שאלה 2: יום הפתיחה', 'הספירה הופכת ללוח הרכבלים הקיים כשיש דיווח, ובלי דיווח "היום פותחים · ממתינים לדיווח". עבר התאריך בלי דיווח: חוזרים ל"אין מידע עדכני". המלצה: כן.'),
    ('שאלה 3: טיול וספירה יחד', 'הלוח הגדול (הפתיחה) והכרטיס קטן מעליו (הטיסה). בלי טיול: הלוח לבד. המלצה: כן.'),
    ('שאלה 4: אחרי העונה', 'בלי ספירה עד שמתפרסם תאריך חדש; "נתראה בחורף הבא". המלצה: כן, ולא לנחש תאריך.'),
    ('שאלה 5: תאריך בנתונים', '`season.open` ב-`resorts.json` לגודאורי, כך שהזזה של MTA היא עדכון נתונים. דורש סקירת ארכיטקט (חוזה קריאה לאפליקציות). המלצה: כן.'),
    ('אזהרות', 'כל התמונות סכמה ולא צילום אפליקציה; הנתונים הם דוגמה (35 ימים, 10 מתוך 12). אין קוד ואין שינוי בדף הבית (החלטה 67) עד האישור.'),
    ('אחרי האישור', 'סקירת ארכיטקט לשדה `season`; אחר כך האנדרואיד והאייפון במקביל, ואחריהם האתר. שומר היישור פותח שורה ב-`docs/PARITY.md`.'),
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
    lead = 'ספירה לאחור לפתיחת העונה (20.12.2026): שלוש חלופות, המלצה, ומה קורה ביום הפתיחה, עם טיול, ואחרי העונה.'
    boards = [('R39-0-Decide.dc.html', 'סבב 39: אושר, נמסר למימוש', page('סבב 39: אושר, נמסר למימוש', card('סבב 39: אושר, נמסר למימוש', DECIDE, DW, DH, lead), DW, DH), DW, DH, None)]
    for b in BOARDS:
        c, w, h = board(b, urls)
        boards.append((f'R39-{b[1]}.dc.html', b[2], page(b[2], c, w, h), w, h, b[0]))
    for key, title, w, h, items in TEXT_BOARDS:
        boards.append((f'R39-{key}.dc.html', title, page(title, card(title, items, w, h, accent='#13233A'), w, h), w, h, 'text'))
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
