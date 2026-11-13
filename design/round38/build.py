#!/usr/bin/env python3
"""Round 38: the iPhone app sign (App Store) next to the Google Play sign, the QR card with both stores, and the /app page (the app went live on the App Store 6.11.2026).
Drawn over the real site by shots.mjs + proposal.js. One canvas page, "סבב 37". Run from the repo root:
  python3 design/round38/build.py webp
  python3 design/round38/build.py [canvas project folder]"""
import html, json, re, pathlib, subprocess, sys

here = pathlib.Path(__file__).resolve().parent
root = here.parent.parent
sys.path.insert(0, str(root / 'design/round12'))
from build import page  # the board shell of rounds 12 to 25

PAGE = ('round38', 'סבב 38: המסך החי ביום הגלישה, המפה והרגעים הראשונים')
SRC = {'compare': ('shots/compare.png', 1100, 0, 0), 'searching': ('shots/searching.png', 1000, 0, 0)}
N = lambda f: f + '.webp'
BOARDS = [
    ('map', 'Map', 'א. גודל המפה מתחת למספרים: היום, 400 מ׳, 800 מ׳', 'היום המפה מתמקדת בקו של היום עם ריפוד של 120 מ׳ מכל צד, והזום קופץ בכל קריאה. בהצעה: מינימום לרוחב, ממורכז על הקו, ואחרי שהקו חורג ממנו הזום יוצא החוצה כמו היום. **הדמיה בלבד:** רינדור מנתוני האתר האמיתיים (קו ב-Tatra 2 בצעדים של 4 מ׳), לא צילום אמולטור.',
     [('ארבעה שלבים בהקלטה, שלוש חלופות', ['compare'])]),
    ('search', 'Search', 'ב. הרגעים שבין הנגיעה לקריאה הראשונה', 'פיני (טלפון אמיתי, 0.1.503): "מתחיל לספור, ואז כשהוא כותב עומדים זה מתאפס, ורק אז מופיעה הנקודה". ההתאפסות תוקנה בקוד; נשאר שהמסך נראה כאילו הוא יודע (מהירות 0, עומדים) לפני שיש מיקום. **סכמה בלבד**, לא צילום של האפליקציה.',
     [('היום מול ההצעה', ['searching'])]),
]
ROWS = [('map', 'א. גודל המפה'), ('search', 'ב. מחפשים מיקום')]
TEXT_BOARDS = []
DECIDE = [
    ('מה ביקשו', 'סשן האנדרואיד, בבקשת פיני (13.11.2026): המפה במסך החי נראתה "מרוחה" בסרטון ההצהרה, והרגעים הראשונים אחרי הנגיעה מבלבלים. הצעה בכתב: `docs/handoff/2026-11-13-live-map-min/README.md` (ענף `ccr-9127081e-kh3b91`).'),
    ('ההמלצה', '**א:** מינימום 400 מ׳ לרוחב המפה (800 מ׳ אם רוצים הרגשה של "אני על ההר"; ההבדל ניכר רק בקו קצר, ואחרי כ-480 מ׳ שלוש האפשרויות כמעט זהות). **ב:** מצב "מחפשים מיקום" עד הקריאה הראשונה: מקף במקום 0, מסגרת מפה ריקה באותו גודל, והמונה ממשיך לספור.'),
    ('אזהרה', 'ההדמיה אינה צילום אמולטור, והמריחה שפיני ראה בסרטון לא משוחזרת בה במלואה. עדיין צריך להסתכל על פריימי הסרטון (DevOps) לפני מימוש.'),
    ('שאלה 1', '400 או 800 מ׳ מינימום? (המלצה: 400)'),
    ('שאלה 2', 'מעבר רך בין זומים (עד שנייה), או קופץ כמו היום? (המלצה: רך)'),
    ('שאלה 3', 'הנוסח "מחפשים מיקום..", ומחוון (נקודות) או רק טקסט? (המלצה: טקסט עם נקודות)'),
    ('שאלה 4', 'עד הקריאה הראשונה: מסגרת מפה ריקה (המלצה, בלי קפיצת גובה) או בלי מפה?'),
    ('אחרי האישור', 'סשן האנדרואיד מממש בשינוי קטן ב-`DayView.kt` מאחורי `DAYREC_UI`, עם צילומי אמולטור ובדיקת יחידה לגבולות. בלי שינוי בקוד עד האישור.'),
]
GAP, PAD, HEAD, CGAP, DW, DH = 18, 20, 150, 28, 600, 900

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
    lead = 'שתי החלטות על המסך החי של יום הגלישה באנדרואיד, עם המלצה לכל אחת.'
    boards = [('R38-0-Decide.dc.html', 'מה לאשר בסבב 38', page('מה לאשר בסבב 38', card('מה לאשר בסבב 38', DECIDE, DW, DH, lead), DW, DH), DW, DH, None)]
    for b in BOARDS:
        c, w, h = board(b, urls)
        boards.append((f'R38-{b[1]}.dc.html', b[2], page(b[2], c, w, h), w, h, b[0]))
    for key, title, w, h, items in TEXT_BOARDS:
        boards.append((f'R38-{key}.dc.html', title, page(title, card(title, items, w, h, accent='#13233A'), w, h), w, h, 'text'))
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
