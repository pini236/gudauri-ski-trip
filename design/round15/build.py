#!/usr/bin/env python3
"""Round 15: the privacy page in four languages (decision 46, Pini: "שיהיה בכל השפות").

Screenshots of the real page (shots.mjs): before (main) and after (the branch claude/site-langs), uploaded to the canvas
as assets. One canvas page, "סבב 15: דף הפרטיות בארבע שפות". Merged only after Pini approves (decision 18).
Run from the repo root: python3 design/round15/build.py [canvas folder]  (default: design/canvas/project)."""
import json, pathlib, sys

root = pathlib.Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(root / 'design/round12'))
from build import page, shot  # same board shell as round 12

PAGE = ('round15', 'סבב 15: דף הפרטיות בארבע שפות')
B = '/_blob/'
SHOTS = [  # file, title, asset, w, h, row
    ('V1-Before.dc.html', 'היום: עברית ואנגלית', B + '3fcfed8f5f3a5aee9542d5a3b744ff0e', 390, 844, 0),
    ('V2-He.dc.html', 'עברית', B + '0fe25a32e23381903f1e4e77a3bcffd1', 390, 844, 0),
    ('V3-En.dc.html', 'אנגלית', B + '4ed96a8cc727dd93c032f9d4f44b2b8b', 390, 844, 0),
    ('V4-Ru.dc.html', 'רוסית', B + 'a66b466833221442b24d3df9a994c84b', 390, 844, 0),
    ('V5-Ka.dc.html', 'גאורגית', B + 'bb734550f2a867fe6c5496c52b652c91', 390, 844, 0),
    ('V6-RuDesktop.dc.html', 'רוסית במחשב', B + '13e0c54bd708fac3bcc99f9dabfe5236', 1280, 800, 1),
]
ROWS = ['בטלפון: לפני, ואחרי בארבע השפות', 'במחשב']
DECIDE = [
    ('ארבעה כפתורי שפה', 'בראש הדף: עב, EN, RU, KA (השם המלא לקורא מסך). במקום "עברית" ו-"English" המלאים, כי ארבעה שמות מלאים לא נכנסים בטלפון.'),
    ('השפה של האתר', 'בלי בחירה בכתובת, הדף נפתח בשפה שנבחרה באתר, ואם לא נבחרה, לפי הדפדפן, כמו שאר האתר. #ru ו-#ka פותחים ישירות.'),
    ('גופנים', 'כמו בסבב 10: כותרות ברוסית ב-Oswald, ובגאורגית Noto Sans Georgian צר ומודגש; הכותרות קטנות יותר כדי להיכנס בטלפון. בצילום הגאורגית לא צרה, כי הצילום משתמש בגופן שבאפליקציה; באתר הוא צר.'),
    ('התוכן', 'תרגום מילולי של המדיניות שאישרת, מהאנגלית, בלי שינוי בתוכן. בלי בדיקת דובר (החלטה 39).'),
]


def decide_card():
    items = ''.join(f'<li style="display: flex; flex-direction: column; gap: 2px; padding: 10px 0; border-top: 1px solid #CBD5DF">'
                    f'<b style="font-size: 16px">{i + 1}. {t}</b><span style="font-size: 14px; line-height: 1.5; color: #4B5A6F">{s}</span></li>'
                    for i, (t, s) in enumerate(DECIDE))
    return (f'<div style="width: 520px; height: 844px; box-sizing: border-box; padding: 28px; background: #FFFFFF; color: #13233A; '
            f"font-family: 'IBM Plex Sans Hebrew', system-ui, sans-serif; direction: rtl; border-top: 8px solid #1F5FC4\">"
            f"<h1 style=\"margin: 0; font-family: Karantina, 'Arial Narrow', sans-serif; font-size: 52px; line-height: 1\">פרטיות בארבע שפות</h1>"
            f'<p style="margin: 8px 0 16px; font-size: 14.5px; line-height: 1.55; color: #4B5A6F">ביקשת שדף הפרטיות יהיה בכל השפות. '
            f'הצילומים מהדף האמיתי, לפני ואחרי. מה שמשתנה במראה:</p>'
            f'<ol style="margin: 0; padding: 0; list-style: none">{items}</ol></div>')


def build(dst):
    boards = [('V0-Decide.dc.html', 'מה לאשר בסבב 15', page('מה לאשר בסבב 15', decide_card(), 520, 844), 520, 844, -1)]
    for f, t, url, w, h, row in SHOTS:
        boards.append((f, t, page(t, shot(t, url, w, h), w, h), w, h, row))
    for f, _, html_, *_ in boards:
        (dst / f).write_text(html_, encoding='utf-8')
    ip = dst / 'canvas.json'
    index = json.loads(ip.read_text(encoding='utf-8'))
    if not any(p['id'] == PAGE[0] for p in index['pages']):
        index['pages'].insert(0, {'id': PAGE[0], 'name': PAGE[1]})
    index['boards']['V0-Decide.dc.html'] = {'x': 0, 'y': 0, 'w': 520, 'h': 844, 'page': PAGE[0], 'title': 'מה לאשר בסבב 15'}
    if 'V0-Decide.dc.html' not in index['order']:
        index['order'].append('V0-Decide.dc.html')
    y = 0
    for ri, title in enumerate(ROWS):
        x, rowh = 600, 0
        for f, t, _, w, h, row in boards:
            if row != ri:
                continue
            index['boards'][f] = {'x': x, 'y': y, 'w': w, 'h': h, 'page': PAGE[0], 'title': t}
            if f not in index['order']:
                index['order'].append(f)
            x += w + 60
            rowh = max(rowh, h)
        index['notes'][f'{PAGE[0]}row{ri}'] = {'kind': 'title1', 'maxW': max(860, x - 660), 'page': PAGE[0], 'text': title, 'w': 240, 'x': 600, 'y': y - 300}
        y += rowh + 120 + 300
    index['launch'] = {'view': 'canvas', 'page': PAGE[0]}
    ip.write_text(json.dumps(index, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    return len(boards)


if __name__ == '__main__':
    dst = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else root / 'design/canvas/project'
    print(build(dst), 'boards ->', dst)
