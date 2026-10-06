#!/usr/bin/env python3
"""Round 21: the map, a UX audit and proposals (Pini, 6.10.2026: clear runs, no cut runs, no names that swap at crossings).

The shots come from audit.mjs (the map today, site), the emulator run of main (app), and after.mjs (before and after,
drawn over the real overview map by proposal.js; canvas only). One canvas page, "סבב 21". Run from the repo root:
  python3 design/round21/build.py webp
  python3 design/round21/build.py [canvas project folder]"""
import html, json, pathlib, subprocess, sys

here = pathlib.Path(__file__).resolve().parent
root = here.parent.parent
sys.path.insert(0, str(root / 'design/round12'))
from build import page  # the board shell of rounds 12 to 20

PAGE = ('round21', 'סבב 21: המפה')
# name: (file under design/round21, width, crop top, crop height)
SRC = {
    'site-desk-3d': ('audit/desk-3d-he.png', 900, 0, 0),
    'site-desk-zoom': ('audit/desk-day-he-zoom.png', 900, 0, 0),
    'site-phone-ru': ('audit/desk-night-ru.png', 900, 0, 0),
    'app-overview': ('audit/app-overview.png', 390, 0, 0),
    'app-3d': ('audit/app-3d.png', 390, 0, 0),
    'b-phone-fit': ('shots/phone-fit-before.png', 390, 0, 0), 'a-phone-fit': ('shots/phone-fit-after.png', 390, 0, 0),
    'b-phone-zoom': ('shots/phone-zoom-before.png', 390, 0, 0), 'a-phone-zoom': ('shots/phone-zoom-after.png', 390, 0, 0),
    'b-desk-zoom': ('shots/desk-zoom-before.png', 880, 0, 0), 'a-desk-zoom': ('shots/desk-zoom-after.png', 880, 0, 0),
    'b-phone-night': ('shots/phone-night-before.png', 390, 0, 0), 'a-phone-night': ('shots/phone-night-after.png', 390, 0, 0),
    'b-phone-ends': ('shots/phone-ends-before.png', 390, 0, 0), 'a-phone-ends': ('shots/phone-ends-after.png', 390, 0, 0),
    'b-phone-sel': ('shots/phone-sel-before.png', 390, 0, 0), 'a-phone-sel': ('shots/phone-sel-after.png', 390, 0, 0),
}
N = lambda f: f + '.webp'
BOARDS = [
    ('audit', 'Audit-site', 'היום באתר', 'מבט על במחשב, בשתי הגדלות: 10 מתוך 24 שמות יושבים על קו של מסלול אחר או צמודים אליו (Kudebi 1 על Sadzele 3, Snow Park על Sadzele 1, Baby על Firni 2, Bombora על שלושה מסלולים). בתלת-ממד השמות בצבע אחד, ו-Bombora על הקו של Kudebi. ברוסית סרגל הסינון נחתך.',
     [('מבט על, שתי הגדלות', ['site-desk-zoom']), ('תלת-ממד', ['site-desk-3d'])]),
    ('audit', 'Audit-app', 'היום באפליקציה', 'אותו כלל בדיוק: שם אופקי באמצע הקטע הארוך. Firni 1 מתחת לפס המצב ו-Sadzele 2 מתחת לקנה המידה. בתלת-ממד רק שמות הרכבלים. (צילומי האמולטור מ-4.10.)',
     [('מבט על', ['app-overview']), ('תלת-ממד', ['app-3d'])]),
    ('ba', 'BA-names', 'א. השם על הקו, בכיוון שלו', 'השם יושב על הקו עצמו, במקטע ישר, איפה שאף מסלול, שם או כפתור לא עוברים. אף פעם לא על מסלול אחר: בלי מקום נקי, השם מופיע כשמקרבים. בטלפון, בזום ההתחלתי: 8 מתוך 12 שמות על מסלול אחר היום, 3 צמודים בלבד אחרי (מתוך 16).',
     [('היום', ['b-phone-fit']), ('ההצעה', ['a-phone-fit']), ('היום, מקרוב', ['b-phone-zoom']), ('ההצעה, מקרוב', ['a-phone-zoom'])]),
    ('ba', 'BA-desk', 'א. במחשב', 'אותו כלל. Kudebi 1, 2 ו-3, שלושה קווים אדומים צמודים, כל שם על הקו שלו. Goodaura 1 ו-2 ו-Sportuli 1 ו-2, שמתחלפים היום, נקראים לאורך הקו.',
     [('היום', ['b-desk-zoom']), ('ההצעה', ['a-desk-zoom'])]),
    ('ba', 'BA-ends', 'ב. קצה פתוח, ג. קטע בלי שם', 'ב: מסלול שהמקור שלו חלקי (Soliko 2, Firni 1 ו-2, Shino) ממשיך בנקודות דוהות ונגמר בעיגול עם סימן שאלה, בלי טקסט; נגיעה פותחת את פאנל המסלול. ג: קטע בלי שם בקו דק, רציף ושקט, כדי שלא ייראה כמו מסלול קטוע. הקווקוו נשאר רק לדרך מקשרת (Shino) ולמסלול סגור.',
     [('היום', ['b-phone-ends']), ('ההצעה', ['a-phone-ends'])]),
    ('ba', 'BA-sel', 'ד. מסלול נבחר, ובלילה', 'בבחירה רק השם של המסלול, על הקו, וחיצים קטנים במורד (הכיוון ממודל הגובה). בלילה אותם כללים; הקריאות טובה כבר היום.',
     [('נבחר: היום', ['b-phone-sel']), ('נבחר: ההצעה', ['a-phone-sel']), ('לילה: היום', ['b-phone-night']), ('לילה: ההצעה', ['a-phone-night'])]),
]
ROWS = [('audit', 'היום: הביקורת'), ('ba', 'לפני ואחרי'), ('text', 'הממצאים')]
TEXT_BOARDS = [
    ('Findings', 'הממצאים, לפי חומרה', 640, 980, [
        ('1. שמות שנקראים כשם של מסלול אחר', 'השם אופקי באמצע הקטע הארוך, ונבדק רק מול שמות אחרים, לא מול קווים. **8 מתוך 12** בטלפון, **10 מתוך 24** במחשב. באפליקציה אותו כלל.'),
        ('2. מסלולים שנגמרים באמצע', 'Soliko 2, Firni 1, Firni 2 ו-Shino חלקיים במקור, והקו פשוט נעצר. ההסבר רק ברשימה ארוכה מתחת למפה.'),
        ('3. קווקוו אחד, שלוש משמעויות', 'קטע בלי שם, דרך מקשרת ומסלול סגור. עשרת הקטעים בלי שם נראים כמו מסלולים קטועים.'),
        ('4. מסלול בכמה חלקים', 'Goodaura 2, Sportuli 2, Sadzele 1, Kudebi 1 ו-Kobi: השם רק על החלק הארוך. הנתונים אצל מומחה הסקי.'),
        ('5. אין כיוון', 'שום דבר לא אומר לאיזה צד יורדים.'),
        ('6. צפיפות', 'בראש ההר בטלפון שמות נעלמים בלי סדר; באפליקציה שמות מתחת לכפתורים.'),
        ('7. קטן', 'אזור נגיעה של 16 פיקסלים בין קווים צמודים; סרגל הסינון ברוסית נחתך.'),
    ]),
]
DECIDE = [
    ('מה ביקשת', 'מסלולים ברורים, בלי מסלולים חתוכים ובלי שמות שמתחלפים בהצטלבות. כאן החוויה; הנתונים אצל מומחה הסקי.'),
    ('א. השם על הקו', 'בכיוון הקו, במקטע ישר, אף פעם לא על מסלול אחר. **ההמלצה: לאשר.**'),
    ('המחיר של א', 'בזום שבו אין מקום נקי, כמה מסלולים קצרים (Baby, Bombora, Khada) בלי שם עד שמקרבים. ההמלצה: לקבל. שם חסר עדיף על שם של מסלול אחר.'),
    ('ב. קצה פתוח', 'נקודות דוהות ועיגול עם סימן שאלה, בלי טקסט. **ההמלצה: לאשר.**'),
    ('ג. קטע בלי שם', 'קו דק ורציף. הקווקוו רק לדרך מקשרת ולסגור. **ההמלצה: לאשר.**'),
    ('ד. חיצים במורד', 'רק במסלול הנבחר. **ההמלצה: לאשר.**'),
    ('ה. אותו דבר באתר ובאפליקציה', 'נקודת העוגן והזווית של כל שם, והקצה הפתוח, נשמרים בנתונים בזמן הבנייה. זה שינוי בחוזה הנתונים, ולכן סקירה אצל הארכיטקט לפני קוד.'),
    ('אחרי האישור', 'סקירת הארכיטקט, ואז האתר והאנדרואיד במקביל; שומר היישור בודק ששניהם זהים.'),
]
GAP, PAD, HEAD, CGAP, DW, DH = 18, 20, 150, 28, 600, 820


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
    lead = ('עברתי על המפה באתר ובאפליקציה, בטלפון ובמחשב, ביום ובלילה ובארבע שפות, ובדקתי כל שם מול הקווים. '
            'ההצעות מצוירות מעל המפה האמיתית. שום דבר לא נבנה לפני שתאשר.')
    boards = [('R21-0-Decide.dc.html', 'מה לאשר בסבב 21', page('מה לאשר בסבב 21', card('מה לאשר בסבב 21', DECIDE, DW, DH, lead), DW, DH), DW, DH, None)]
    for b in BOARDS:
        c, w, h = board(b, urls)
        boards.append((f'R21-{b[1]}.dc.html', b[2], page(b[2], c, w, h), w, h, b[0]))
    for key, title, w, h, items in TEXT_BOARDS:
        boards.append((f'R21-{key}.dc.html', title, page(title, card(title, items, w, h, accent='#13233A'), w, h), w, h, 'text'))
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
