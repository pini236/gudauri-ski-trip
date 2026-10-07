#!/usr/bin/env python3
"""Round 22: the map as the main screen (Pini, 6.10.2026). Four directions drawn over the real site by shots.mjs + proposal.js.

The app board puts the same folded ticket over the emulator shot of the overview map (design/round21/audit).
One canvas page, "סבב 22". Run from the repo root:
  python3 design/round22/build.py webp
  python3 design/round22/build.py [canvas project folder]"""
import html, json, pathlib, subprocess, sys

here = pathlib.Path(__file__).resolve().parent
root = here.parent.parent
sys.path.insert(0, str(root / 'design/round12'))
from build import page  # the board shell of rounds 12 to 20

PAGE = ('round22', 'סבב 22: המפה כמסך הראשי')
P = lambda f: ('shots/' + f + '.png', 390, 0, 0)
SRC = {n: P(n) for n in ['today-home', 'today-map', 'A-pre', 'A-during', 'A-none', 'A-menu', 'A-ticket', 'B-pre', 'B-during', 'B-none',
                          'C-pre', 'C-during', 'C-none', 'D-pre', 'D-during', 'D-none', 'A-pre-night', 'A-during-night', 'B-pre-night',
                          'APP-pre', 'APP-during', 'APP-none']}
SRC['A-desk'] = ('shots/A-desk.png', 900, 0, 0)
SRC['B-desk'] = ('shots/B-desk.png', 900, 0, 0)
SRC['today-home'] = ('shots/today-home.png', 390, 0, 0)
N = lambda f: f + '.webp'
BOARDS = [
    ('today', 'Today', 'היום', 'נפתחים על הכרטיס והנוף, ולמפה מגיעים בשלט. פיני: "בתכלס בניתי את זה בשביל המפה".',
     [('דף הבית היום', ['today-home']), ('המפה היום', ['today-map'])]),
    ('alt', 'A', 'א. ההר קודם (ההמלצה)', 'נפתחים ישר על ההר, במסך מלא. הכרטיס מתקפל לספח בתחתית: לפני הטיול הספירה, בזמן הטיול יום הסקי עם מזג האוויר והרכבלים, ובלי טיול "מתי טסים?". נגיעה בספח פותחת את הכרטיס המלא (שהוא גם הטופס, סבב 20). שאר החלקים בתפריט, כשלטים על עמוד. בראש שם האתר, מוכן לכמה אתרים.',
     [('לפני הטיול', ['A-pre']), ('בזמן הטיול', ['A-during']), ('בלי טיול', ['A-none']), ('התפריט', ['A-menu']), ('נגיעה בספח', ['A-ticket'])]),
    ('alt', 'B', 'ב. חלון להר', 'דף הבית נשאר, אבל בראשו חלון חי להר עם מצב הרכבלים, ונגיעה פותחת את המפה המלאה. הכרטיס מתחת. הכי קרוב להיום, אבל בהר זו עוד נגיעה בכל פעם.',
     [('לפני הטיול', ['B-pre']), ('בזמן הטיול', ['B-during']), ('בלי טיול', ['B-none'])]),
    ('alt', 'C', 'ג. שלטים על ההר', 'המפה במסך מלא, והשלטים עומדים עליה, על עמוד בפינה, כמו בהר. הספח תלוי בפינה העליונה. הכי "שלנו", אבל בטלפון השלטים מכסים חלק מהמפה.',
     [('לפני הטיול', ['C-pre']), ('בזמן הטיול', ['C-during']), ('בלי טיול', ['C-none'])]),
    ('alt', 'D', 'ד. לשוניות', 'המפה במסך מלא, עם סרגל לשוניות למטה: ההר, הטיול, משחקים, עוד. מוכר מאפליקציות, אבל לשונית שלמה לכרטיס שמסתכלים בו מעט, ובמחשב באתר סרגל כזה לא טבעי.',
     [('לפני הטיול', ['D-pre']), ('בזמן הטיול', ['D-during']), ('בלי טיול', ['D-none'])]),
    ('more', 'Night', 'א ו-ב בלילה', 'אותם רכיבים בטוקנים של הלילה. הספח בצבעי הלילה של האתר.',
     [('א, לפני הטיול', ['A-pre-night']), ('א, בזמן הטיול', ['A-during-night']), ('ב, לפני הטיול', ['B-pre-night'])]),
    ('more', 'Desk', 'במחשב', 'א: המפה היא הדף, והסרגל העליון נשאר; הספח בפינה. ב: החלון ליד הכרטיס.',
     [('א, בזמן הטיול', ['A-desk']), ('ב, לפני הטיול', ['B-desk'])]),
    ('more', 'App', 'באפליקציה (א)', 'אותו ספח מעל מבט העל של האפליקציה (צילום האמולטור). כפתור "כל המסלולים" ושורת ההסבר עוברים לתפריט.',
     [('לפני הטיול', ['APP-pre']), ('בזמן הטיול', ['APP-during']), ('בלי טיול', ['APP-none'])]),
]
ROWS = [('today', 'היום'), ('alt', 'ארבע חלופות'), ('more', 'לילה, מחשב ואפליקציה'), ('text', 'מה עובר לאן')]
TEXT_BOARDS = [
    ('Moves', 'מה עובר לאן (בחלופה א)', 640, 900, [
        ('הכרטיס', 'מתקפל לספח בתחתית המפה. נגיעה פותחת אותו במגירה, עם התלישה וההחלפה כמו היום.'),
        ('"הטיול שלך"', 'מהספח: בלי טיול הוא אומר "מתי טסים?" ופותח את הכרטיס כטופס (סבב 20).'),
        ('מזג האוויר והרכבלים', 'בזמן הטיול בתוך הספח (טמפרטורה, רכבלים פתוחים, רוח); הפירוט נשאר בשכבות של המפה.'),
        ('משחקים, מפגש, קבוצה, הגדרות', 'בתפריט, כשלטים על עמוד (אותו רכיב של דף הבית היום).'),
        ('דף הבית והנוף מהכפר', 'כבר לא מסך נפרד. הנוף יכול להישאר כרקע של מגירת הכרטיס, או לרדת.'),
        ('כמה אתרים (החלטה 61)', 'שם האתר בראש עם חץ יפתח בהמשך את רשימת האתרים. הספח שייך לטיול, ולכן מופיע רק באתר של הטיול.'),
        ('לארכיטקט (לפני קוד)', 'באתר: המסך הראשון הוא מבט העל, שנבנה בלי מודל הגובה ובלי three.js; התבליט נכנס כשהמודל מגיע, ותלת-ממד רק בבקשה. צריך למדוד שהדף מוכן בפחות מ-3 שניות בטלפון (R-11), ולהפנות את #home למפה. באפליקציה הנתונים ארוזים.'),
        ('נתוני דוגמה', 'מזג האוויר והרכבלים בספח של "בזמן הטיול" הם להמחשה.'),
    ]),
]
DECIDE = [
    ('מה ביקשת', '"המפה צריכה להיות במסך הראשי, במקום הטיסה והכרטיס". ארבע חלופות, באתר ובאפליקציה, לפני הטיול, בזמנו ובלי טיול, ביום ובלילה.'),
    ('א. ההר קודם', 'המפה היא המסך הראשי, והכרטיס מתקפל לספח. **ההמלצה.** בהר פותחים ורואים את המסלולים מיד, והספח אומר בדיוק מה צריך באותו רגע: ספירה, או יום הסקי עם מזג האוויר והרכבלים.'),
    ('ב. חלון להר', 'דף הבית עם חלון חי להר. הכי קרוב להיום, אבל עוד נגיעה בכל פעם.'),
    ('ג. שלטים על ההר', 'השלטים על עמוד בפינת המפה. הכי בשפה שלנו, אבל מכסה חלק מהמפה בטלפון.'),
    ('ד. לשוניות', 'סרגל למטה. מוכר, אבל לא טבעי באתר במחשב, וכרטיס לא שווה לשונית.'),
    ('אפשר לשלב', 'א עם הספח של ג (תלוי בפינה במקום פס בתחתית), אם הפס מכסה יותר מדי מהמפה.'),
    ('אחרי הבחירה', 'סקירת הארכיטקט על הטעינה באתר (R-11), ואז האתר והאנדרואיד במקביל.'),
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
    lead = ('ארבע חלופות למסך הראשון, מצוירות מעל האתר האמיתי ומעל צילום האפליקציה. שום דבר לא נבנה לפני שתאשר.')
    boards = [('R22-0-Decide.dc.html', 'מה לאשר בסבב 22', page('מה לאשר בסבב 22', card('מה לאשר בסבב 22', DECIDE, DW, DH, lead), DW, DH), DW, DH, None)]
    for b in BOARDS:
        c, w, h = board(b, urls)
        boards.append((f'R22-{b[1]}.dc.html', b[2], page(b[2], c, w, h), w, h, b[0]))
    for key, title, w, h, items in TEXT_BOARDS:
        boards.append((f'R22-{key}.dc.html', title, page(title, card(title, items, w, h, accent='#13233A'), w, h), w, h, 'text'))
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
