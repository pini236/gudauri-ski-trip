#!/usr/bin/env python3
"""Round 14: managing the group on the site, and the site as built after round 12 (closing the loop).

Screenshots of the real site on the branch claude/site-group-admin (shots.mjs), uploaded to the canvas as assets.
One canvas page, "סבב 14: ניהול הקבוצה באתר". Nothing here is merged until Pini approves (decision 18).
Run from the repo root: python3 design/round14/build.py [canvas folder]  (default: design/canvas/project)."""
import json, pathlib, sys

root = pathlib.Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(root / 'design/round12'))
from build import page, shot  # same board shell as round 12

PAGE = ('round14', 'סבב 14: ניהול הקבוצה באתר')
B = '/_blob/'
SHOTS = [  # file, title, asset, w, h, row
    ('M1-Members.dc.html', 'חברים: התפריט של חבר (⋮)', B + 'dd13482a5f82fbbee062a60358fab171', 390, 1096, 0),
    ('M2-FillTrip.dc.html', 'מילוי הטיסה בשביל חבר', B + '10d5770053834c59e278cef8dbf6d3a1', 390, 1524, 0),
    ('M3-NameDates.dc.html', 'שם ותאריכים', B + '1409b2d425cc9503877424c989c27b5c', 390, 1306, 0),
    ('M4-Invite.dc.html', 'הגדרות ההזמנה', B + '9a649fefd2e99b41dff86b9e2e327e3c', 390, 1282, 0),
    ('M5-DeleteArmed.dc.html', 'מחיקת הקבוצה: לחיצה שנייה', B + '22e19f411f5333495b05ad93ba61c660', 390, 1019, 0),
    ('M6-MembersDesktop.dc.html', 'במחשב', B + '4ca041efdacd7b2a73df625199af1d71', 1280, 844, 1),
    ('M7-Scores.dc.html', 'שיאים: גם מהמשחקים באתר', B + '7e228c10ce6847293998b7bd101d6db6', 390, 844, 2),
    ('M8-MeetPick.dc.html', 'נקודת מפגש: באיזו קבוצה לשמור', B + 'b42593ae8c5f8cd2138105754753a7da', 390, 2069, 2),
    ('N1-GuestHome.dc.html', 'דף הבית, אורח', B + '44c64af3397379c7e65b6f906f786a6e', 390, 1352, 3),
    ('N2-TripHome.dc.html', 'עם טיול, נגיעה בשם', B + '0e4008929b40bd7341d27f19abc276de', 390, 1263, 3),
    ('N3-SignIn.dc.html', 'כניסה', B + '05eb2caa29e48d1e535b1926ddca46fe', 390, 844, 3),
    ('N4-GroupFlights.dc.html', 'קבוצה: טיסות', B + '05c45ba7ef035197ebf979750783480c', 390, 844, 3),
    ('N5-Account.dc.html', 'חשבון', B + '6056af0360428e4e71adc2e2f5591a6a', 390, 844, 3),
    ('N6-AboutPass.dc.html', 'הגדרות: הסקי־פס', B + 'a42e84b9fed7d8a26acce09b537ecdf5', 390, 760, 3),
]
ROWS = ['ניהול הקבוצה למנהל, בטלפון', 'ניהול הקבוצה במחשב', 'שיאים ונקודת מפגש', 'באתר עכשיו: סבב 12 כפי שנבנה (בלי שינוי, רק להשוואה)']
DECIDE = [
    ('תפריט לכל חבר (⋮)', 'רק מנהל רואה: מינוי למנהל או הורדה ממנהל, מילוי הטיסה בשבילו (כשלא הזין בעצמו), והוצאה מהקבוצה בלחיצה שנייה. כמו Q10 באפליקציה.'),
    ('ניהול הקבוצה', 'שלוש שורות מתחת לחברים: שם ותאריכים, הגדרות ההזמנה (אישור מנהל לכל מצטרף, קוד וקישור חדשים, ביטול), ומחיקת הקבוצה בלחיצה שנייה.'),
    ('שיאים מהמשחקים באתר', 'השיא שלך עולה לטבלת הקבוצה, רק אחרי שהצטרפת. מה נספר: בבית הספר לסקי סך הכוכבים (הטוב בכל שיעור), ובשאר המשחקים הניקוד הכי גבוה. האפליקציה צריכה לספור אותו דבר.'),
    ('מפגש לקבוצה הנכונה', 'מי שנמצא בכמה קבוצות בוחר לאיזו לשמור. מי שבקבוצה אחת: נשמר ישר, כמו היום.'),
    ('באתר עכשיו', 'השורה התחתונה היא האתר כפי שנבנה אחרי סבב 12, כדי שהעיצוב הבא יתחיל ממה שבאמת יש.'),
]


def decide_card():
    items = ''.join(f'<li style="display: flex; flex-direction: column; gap: 2px; padding: 10px 0; border-top: 1px solid #CBD5DF">'
                    f'<b style="font-size: 16px">{i + 1}. {t}</b><span style="font-size: 14px; line-height: 1.5; color: #4B5A6F">{s}</span></li>'
                    for i, (t, s) in enumerate(DECIDE))
    return (f'<div style="width: 520px; height: 900px; box-sizing: border-box; padding: 28px; background: #FFFFFF; color: #13233A; '
            f"font-family: 'IBM Plex Sans Hebrew', system-ui, sans-serif; direction: rtl; border-top: 8px solid #1F5FC4\">"
            f"<h1 style=\"margin: 0; font-family: Karantina, 'Arial Narrow', sans-serif; font-size: 52px; line-height: 1\">ניהול הקבוצה באתר</h1>"
            f'<p style="margin: 8px 0 16px; font-size: 14.5px; line-height: 1.55; color: #4B5A6F">מה שעוד חסר באתר כדי שמנהל יוכל לעבוד מהמחשב, ועוד שני דברים קטנים. מצולם מהאתר עצמו, בענף שלא ימוזג עד שתאשר. '
            f'השמות והקוד בצילומים מומצאים.</p>'
            f'<ol style="margin: 0; padding: 0; list-style: none">{items}</ol></div>')


def build(dst):
    boards = [('M0-Decide.dc.html', 'מה לאשר בסבב 14', page('מה לאשר בסבב 14', decide_card(), 520, 900), 520, 900, -1)]
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
    index['boards']['M0-Decide.dc.html'] = {'x': 0, 'y': 0, 'w': 520, 'h': 900, 'page': PAGE[0], 'title': 'מה לאשר בסבב 14'}
    if 'M0-Decide.dc.html' not in index['order']:
        index['order'].append('M0-Decide.dc.html')
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
