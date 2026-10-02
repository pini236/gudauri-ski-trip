#!/usr/bin/env python3
"""Round 13: small fixes, before and after (asked by the fixes session, branch claude/fixes-night).

Screenshots of the real site (fixes.mjs), uploaded to the canvas as assets; their urls are below. One canvas page,
"סבב 13: תיקונים קטנים", with a card that lists what to decide. Nothing here is in the code until Pini approves (decision 18).
Run from the repo root: python3 design/round13/build.py [canvas folder]  (default: design/canvas/project)."""
import json, pathlib, sys

root = pathlib.Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(root / 'design/round12'))
from build import page, shot  # same board shell as round 12

PAGE = ('round13', 'סבב 13: תיקונים קטנים')
B = '/_blob/'
SHOTS = [  # file, title, asset, w, h, row
    ('X1a-PrivacyLang.dc.html', 'דף הפרטיות: לפני (40 פיקסלים)', B + 'ac2c27a8a7cf1ba973a828dfec0919f4', 390, 120, 0),
    ('X1b-PrivacyLang.dc.html', 'דף הפרטיות: אחרי (44 פיקסלים)', B + '636fdb190d2e8b80dbd739af0779d62c', 390, 124, 0),
    ('X2a-School1He.dc.html', 'שיעור 1: לפני', B + '2f84b0df78f0fc0e2419ebe05387fb58', 390, 220, 1),
    ('X2b-School1He.dc.html', 'שיעור 1: אחרי', B + 'c43c3dd651f25e3a5a411a159f6c0b6a', 390, 254, 1),
    ('X2c-School2He.dc.html', 'שיעור 2: לפני', B + '7c8bdbd7c95e8b52e589c0f3e30eeaa6', 390, 230, 1),
    ('X2d-School2He.dc.html', 'שיעור 2: אחרי', B + 'a8c31bc8353fd4239f298621c3253e93', 390, 264, 1),
    ('X2e-School1En.dc.html', 'Lesson 1: before', B + '1ae6955db800abf72cda9f5e4b5b8ac1', 390, 220, 2),
    ('X2f-School1En.dc.html', 'Lesson 1: after', B + 'f42962672a4e254981dca86d99ff4349', 390, 254, 2),
    ('X2g-School2En.dc.html', 'Lesson 2: before', B + '019b8df2ae8147100bbe272f15b18be1', 390, 230, 2),
    ('X2h-School2En.dc.html', 'Lesson 2: after', B + '9e0694608f9bfa788b472eb7f420ac00', 390, 264, 2),
    ('X3a-Google.dc.html', 'כפתור גוגל: לפני', B + 'bb55b1c8d453161a17edfd7a744a49b5', 390, 174, 3),
    ('X3b-Google.dc.html', 'כפתור גוגל: אחרי (הסמל של גוגל)', B + 'daaa41bab8a483a05c5e4f246cbb9374', 390, 174, 3),
    ('X4a-ReclaimAsk.dc.html', '"אני כבר בקבוצה": לפני', B + '7ecd7e8d77d51dd7b7581eef1a625781', 390, 280, 4),
    ('X4b-ReclaimAsk.dc.html', '"אני כבר בקבוצה": אחרי, עם השם שלך', B + 'e8fcfa883160757bea01298e8007996b', 390, 390, 4),
    ('X4c-ReclaimAdmin.dc.html', 'אצל המנהל: לפני', B + '2faa001deea73c46e97998a2696d4958', 390, 173, 4),
    ('X4d-ReclaimAdmin.dc.html', 'אצל המנהל: אחרי', B + '803c27351152c217f4104b64bfb09674', 390, 173, 4),
]
ROWS = ['1. דף הפרטיות: כפתורי השפה', '2. בית הספר לסקי: המילים בציורים, בעברית', '2. בית הספר לסקי: באנגלית',
        '3. "המשך עם גוגל"', '4. "אני כבר בקבוצה": מי מבקש']
DECIDE = [
    ('כפתורי השפה בדף הפרטיות', 'היום בגובה 40 פיקסלים, והכלל אצלנו 44 לכל דבר שנוגעים בו. ההצעה: 44. כמעט לא רואים הבדל, אבל קל יותר לפגוע באצבע.'),
    ('הציורים בבית הספר לסקי', 'המילים בתוך הציור נחתכות בשוליים, בעברית וגם באנגלית. ההצעה: בלי מילים בתוך הציור; מקרא קטן מתחתיו, בצבעים של הציור, שנשבר לשורות בכל שפה. כך בכל שבעת השיעורים.'),
    ('"המשך עם גוגל"', 'הסמל הרשמי של גוגל, הצבעוני, לפי כללי המיתוג שלהם, במקום עיגול עם G. באתר זה כבר כך מהיום, כי את הכפתור מציירת גוגל עצמה. ההצעה היא בעיקר לאפליקציה (A2).'),
    ('"אני כבר בקבוצה"', 'היום הבקשה מגיעה למנהל עם השם של החבר שמבקשים להיות, ולכן המנהל לא יודע מי באמת מבקש (פתח להתחזות). ההצעה: המבקש כותב "איך קוראים לך?" לפני שבוחר את השם, והמנהל רואה את שני השמות. באתר ובאפליקציה (Q5, Q10). השרת כבר מוכן לזה בענף של סשן התיקונים.'),
]


def decide_card():
    items = ''.join(f'<li style="display: flex; flex-direction: column; gap: 2px; padding: 10px 0; border-top: 1px solid #CBD5DF">'
                    f'<b style="font-size: 16px">{i + 1}. {t}</b><span style="font-size: 14px; line-height: 1.5; color: #4B5A6F">{s}</span></li>'
                    for i, (t, s) in enumerate(DECIDE))
    return (f'<div style="width: 520px; height: 760px; box-sizing: border-box; padding: 28px; background: #FFFFFF; color: #13233A; '
            f"font-family: 'IBM Plex Sans Hebrew', system-ui, sans-serif; direction: rtl; border-top: 8px solid #1F5FC4\">"
            f"<h1 style=\"margin: 0; font-family: Karantina, 'Arial Narrow', sans-serif; font-size: 52px; line-height: 1\">תיקונים קטנים</h1>"
            f'<p style="margin: 8px 0 16px; font-size: 14.5px; line-height: 1.55; color: #4B5A6F">ארבעה שינויים קטנים במראה, כל אחד לפני ואחרי, מצולמים בתוך האתר האמיתי בטלפון. '
            f'אפשר לאשר כל אחד לבד. שום דבר מזה עוד לא בקוד. השמות בצילומים מומצאים.</p>'
            f'<ol style="margin: 0; padding: 0; list-style: none">{items}</ol></div>')


def build(dst):
    boards = [('X0-Decide.dc.html', 'מה לאשר בסבב 13', page('מה לאשר בסבב 13', decide_card(), 520, 760), 520, 760, -1)]
    for f, t, url, w, h, row in SHOTS:
        boards.append((f, t, page(t, shot(t, url, w, h), w, h), w, h, row))
    for f, _, html_, *_ in boards:
        (dst / f).write_text(html_, encoding='utf-8')
    ip = dst / 'canvas.json'
    index = json.loads(ip.read_text(encoding='utf-8'))
    if not any(p['id'] == PAGE[0] for p in index['pages']):
        index['pages'].insert(0, {'id': PAGE[0], 'name': PAGE[1]})
    names = {b[0] for b in boards}
    for f in [f for f, b in index['boards'].items() if b.get('page') == PAGE[0] and f not in names]:
        del index['boards'][f]
        if f in index['order']:
            index['order'].remove(f)
    # the card on the right, the rows of before and after next to it
    index['boards']['X0-Decide.dc.html'] = {'x': 0, 'y': 0, 'w': 520, 'h': 760, 'page': PAGE[0], 'title': 'מה לאשר בסבב 13'}
    if 'X0-Decide.dc.html' not in index['order']:
        index['order'].append('X0-Decide.dc.html')
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
