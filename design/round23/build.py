#!/usr/bin/env python3
"""Round 23: several ski resorts (decision 68). Drawn over the real site by shots.mjs + proposal.js.

One canvas page, "סבב 23". Run from the repo root:
  python3 design/round23/build.py webp
  python3 design/round23/build.py [canvas project folder]"""
import html, json, pathlib, subprocess, sys

here = pathlib.Path(__file__).resolve().parent
root = here.parent.parent
sys.path.insert(0, str(root / 'design/round12'))
from build import page  # the board shell of rounds 12 to 20

PAGE = ('round23', 'סבב 23: כמה אתרי סקי')
P = lambda f: ('shots/' + f + '.png', 390, 0, 0)
SRC = {n: P(n) for n in ['today-home', 'today-map', 'picker-phone', 'sol-picker', 'sol-home', 'sol-home-night', 'plates-phone', 'plates-night']}
for n in ['picker-desk', 'sol-home-desk', 'plates-desk']:
    SRC[n] = ('shots/' + n + '.png', 900, 0, 0)
N = lambda f: f + '.webp'
BOARDS = [
    ('today', 'Today', 'היום', 'אתר אחד. שם המקום כתוב בראש, ולא עושה כלום.',
     [('דף הבית', ['today-home']), ('המפה', ['today-map'])]),
    ('alt', 'Picker', '1. בורר אתר: שם המקום הוא הכפתור', 'בטלפון הכותרת "גודאורי, גאורגיה" בדף הבית והכותרת של המפה, ובמחשב שם האתר בסרגל העליון, מקבלים חץ. נגיעה פותחת רשימה של אתרים כשלטים על עמוד (אותו רכיב של דף הבית): שם, מדינה, מסלולים ורכבלים, וסימון על האתר הנוכחי. בחירה טוענת את הדף מחדש באתר החדש. בטלפון מגירה מלמטה, במחשב רשימה נפתחת.',
     [('טלפון', ['picker-phone']), ('טלפון, מתוך Sölden', ['sol-picker']), ('מחשב', ['picker-desk'])]),
    ('alt', 'Home', '2. דף הבית של אתר בלי טיול', 'אותו מבנה. במקום הכרטיס, כרטיס סקי של האתר: שם, מדינה, מסלולים, רכבלים וטווח הגובה, ופס בצבעי המסלולים לפי החלוקה. השמיים של היום והלילה נשארים, בלי הנוף של גודאורי ובלי שמות הפסגות שלו. השלטים רק למפה ולמשחקים. אין ספירה לטיסה, ושעון האתר בשעון של האתר.',
     [('יום', ['sol-home']), ('לילה', ['sol-home-night']), ('מחשב', ['sol-home-desk'])]),
    ('alt', 'Plates', '3. מסלול שהשם שלו מספר: לוחית', 'לוחית מרובעת קטנה בצבע המסלול, עם המספר בגופן הכותרות, עומדת זקופה על הקו, כמו הלוחיות על העמודים במסלולים באוסטריה. נכנסת רק במקום שאף קו אחר ולוחית אחרת לא עוברים (הכלל של סבב 21), ואם אין מקום, מופיעה כשמקרבים. מסלול עם שם נשאר עם השם. הדגמה על המפה של גודאורי, והמספרים לדוגמה.',
     [('טלפון', ['plates-phone']), ('לילה', ['plates-night']), ('מחשב', ['plates-desk'])]),
]
ROWS = [('today', 'היום'), ('alt', 'שלוש ההחלטות'), ('text', 'פרטים למימוש')]
TEXT_BOARDS = [
    ('Spec', 'פרטים למימוש', 640, 980, [
        ('הבורר', 'כפתור בגובה 44 לפחות, השם והחץ יחד. הרשימה בסדר קבוע (גודאורי ראשון). האתר נשמר בדפדפן ובכתובת (למשל ?resort=soelden), וקישור למסלול נושא את האתר שלו.'),
        ('שם האתר', 'בגודאורי הכל כמו היום ("גודאורי 2027", "גודאורי, גאורגיה"). באתר אחר: השם המקובל באותיות שלו (Sölden), בלי תרגום.'),
        ('כרטיס הסקי', 'המספרים נספרים מהנתונים של האתר (מסלולים עם קו, רכבלים, הגובה הנמוך והגבוה ממודל הגובה), לא נכתבים ביד. הפס: אורך המסלולים לפי צבע.'),
        ('"הטיול שלך"', 'שייך לטיול, ומופיע רק באתר של הטיול (היום: גודאורי). בהמשך: באתר ששדה התעופה שלו הוא היעד של הטיול.'),
        ('מה לא מוצג ב-Sölden', 'נקודת מפגש, מצב רכבלים, מזג אוויר וספירה לטיסה: לא שלטים כבויים, פשוט לא מופיעים. הקבוצה: בהמשך.'),
        ('הנוף', 'באב הטיפוס שמיים בלבד. כשיש מודל גובה של Sölden, נוף שמרונדר ממנו (design/round3/panorama.py), ושמות הפסגות רק ממקור אמיתי.'),
        ('לוחית', '20 פיקסלים גובה, 22 לספרה אחת ו-30 לשתיים; מסגרת דקה בצבע הרקע של המפה; המספר ב-Karantina 17. בלילה הטוקנים של הלילה (טקסט כהה על הצבעים הבהירים).'),
        ('פאנל המסלול', 'השלט בראש הפאנל: המספר בגדול ("30"), בצבע המסלול. אם יש גם שם, השם אחריו.'),
        ('השעון והיום והלילה', 'לפי אזור הזמן והקואורדינטות של האתר (Sölden: שעון מרכז אירופה).'),
    ]),
]
DECIDE = [
    ('מה ביקשו', 'אב טיפוס של כמה אתרים (החלטה 68): Sölden לצד גודאורי. בגודאורי הכל כמו היום.'),
    ('1. בורר אתר', 'שם המקום הוא הכפתור: הכותרת בטלפון, שם האתר בסרגל העליון במחשב. רשימה של שלטים על עמוד. בחירה טוענת מחדש.'),
    ('2. דף הבית', 'אותו מבנה, וכרטיס סקי של האתר במקום כרטיס הטיסה. שמיים בלי הרים עד שיש נוף אמיתי. שלטים למפה ולמשחקים בלבד.'),
    ('3. מספרים במפה', 'לוחית קטנה בצבע המסלול עם המספר, זקופה על הקו, רק במקום נקי.'),
    ('בתוקף', 'לפי החלטה 68 אין המתנה לאישור: סשן המנהל מממש לפי זה.'),
]
GAP, PAD, HEAD, CGAP, DW, DH = 18, 20, 150, 28, 600, 700


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
    lead = ('שלוש החלטות לאב הטיפוס של כמה אתרים, מצוירות מעל האתר האמיתי.')
    boards = [('R23-0-Decide.dc.html', 'ההחלטות בסבב 23', page('ההחלטות בסבב 23', card('ההחלטות בסבב 23', DECIDE, DW, DH, lead), DW, DH), DW, DH, None)]
    for b in BOARDS:
        c, w, h = board(b, urls)
        boards.append((f'R23-{b[1]}.dc.html', b[2], page(b[2], c, w, h), w, h, b[0]))
    for key, title, w, h, items in TEXT_BOARDS:
        boards.append((f'R23-{key}.dc.html', title, page(title, card(title, items, w, h, accent='#13233A'), w, h), w, h, 'text'))
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
