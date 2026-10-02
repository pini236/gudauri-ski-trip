#!/usr/bin/env python3
"""Round 12 design screens: the site's own "your trip", sign-in, account and group (stage 13.9).

Round 10 drew these for the app only. Here they are drawn inside the real site (proto.mjs, proto-lib.js, proto.css),
so what Pini approves is what the site will look like. The screenshots are uploaded to the canvas as assets;
their urls are below. One canvas page, "סבב 12: חשבון וקבוצה באתר", with a card that lists what to decide.
Run from the repo root: python3 design/round12/build.py [canvas folder]  (default: design/canvas/project)."""
import json, pathlib, sys

root = pathlib.Path(__file__).resolve().parent.parent.parent
PAGE = ('round12', 'סבב 12: חשבון וקבוצה באתר')

SHOTS = [  # file, title, asset url, width, height, row
    ('W1-GuestHome.dc.html', 'דף הבית: אורח, בלי טיול', '/_blob/93d41006d332e644e4dd1aa4051c4ceb', 390, 1349, 0),
    ('W2-TripForm.dc.html', 'הטיול שלך באתר', '/_blob/a0105af559e14ce274dc4dec5af8eb46', 390, 929, 0),
    ('W3-TripHome.dc.html', 'דף הבית: עם הטיול, מחובר', '/_blob/6aedaa3995b3c60f258afd1aebe867eb', 390, 1263, 0),
    ('W11-TripHomeNight.dc.html', 'דף הבית: עם הטיול, בלילה', '/_blob/a663e95db61164786445c495bb6d27ac', 390, 1263, 0),
    ('W4-SignIn.dc.html', 'כניסה: גוגל, אפל, או אורח', '/_blob/4abad587e73d220c1bacb81458409b90', 390, 844, 1),
    ('W5-Account.dc.html', 'חשבון', '/_blob/1c2a5ace4cfe16f71e192f257868eedc', 390, 844, 1),
    ('W6-DeleteAccount.dc.html', 'מחיקת חשבון (לגוגל פליי)', '/_blob/4fc9d3b4c69a2ff87234a3c636ffe953', 390, 844, 1),
    ('W7-Join.dc.html', 'הזמנה לקבוצה מקישור', '/_blob/0f59512bed496984e12d82f5d0578049', 390, 844, 2),
    ('W8-Group.dc.html', 'דף הקבוצה: טיסות', '/_blob/03a7d6b5700edfc8ffa9f29372bcb920', 390, 844, 2),
    ('W10-GroupDesktop.dc.html', 'דף הקבוצה במחשב', '/_blob/a7dfaa5e601e1a10b18f9d1274a9b95b', 1280, 844, 2),
    ('W9-TripHomeDesktop.dc.html', 'דף הבית במחשב: עם הטיול', '/_blob/a5eac7232feb86c5ed4826b1af12f854', 1280, 844, 3),
    # where the account goes on the phone (Pini: in W1 and W3 it looks odd). Three options, guest and signed in
    ('K1-DuoGuest.dc.html', 'א. בקפסולה עם יום ולילה: אורח', '/_blob/ac2aff4dba44fc8c14fe8783df43e1ce', 390, 330, 4),
    ('K2-DuoMe.dc.html', 'א. בקפסולה עם יום ולילה: מחובר', '/_blob/e943dd2c9f4ddef73729c8d7246081dd', 390, 330, 4),
    ('K7-DuoMeNight.dc.html', 'א. בקפסולה, בלילה', '/_blob/8b25d7ab0fe7de20e1eadef54d75fdfd', 390, 330, 4),
    ('K3-TagGuest.dc.html', 'ב. סקי־פס על עמוד השלטים: אורח', '/_blob/fe8d22fce160a42bfe9dd1f9de86179c', 390, 430, 5),
    ('K4-TagMe.dc.html', 'ב. סקי־פס על עמוד השלטים: מחובר', '/_blob/9c7b1a1a8594b4c71f64b694ce45672c', 390, 430, 5),
    ('K8-TagMeNight.dc.html', 'ב. סקי־פס, בלילה', '/_blob/04967ed2aaefd3d00c89e92645d9923c', 390, 430, 5),
    ('K5-LineGuest.dc.html', 'ג. שורה מתחת לשם המקום: אורח', '/_blob/31a486563ecd07bc1d69dafdd444bff9', 390, 360, 6),
    ('K6-LineMe.dc.html', 'ג. שורה מתחת לשם המקום: מחובר', '/_blob/69b64c82aad8eb2b6c9472f8cb5014e5', 390, 360, 6),
]
ROWS = ['דף הבית: הטיול שלך במקום הכרטיס של החבר׳ה', 'כניסה, חשבון ומחיקת חשבון', 'קבוצה: הזמנה מקישור ודף הקבוצה', 'במחשב',
        'כפתור החשבון בטלפון, א: בקפסולה אחת עם מתג היום והלילה (ההמלצה)', 'ב: סקי־פס תלוי על עמוד השלטים', 'ג: שורה שקטה מתחת לשם המקום']

DECIDE = [
    ('הכרטיס והשמות של החבר׳ה יורדים מדף הבית הציבורי', 'ועוברים לדף הקבוצה, שרק חבריה רואים (החלטה 27). בדף הבית: הכרטיס של מי שמסתכל, או כרטיס ריק עם "הוספת הטיסה שלי".'),
    ('הספירה לאחור בסרגל העליון', 'הולכת לפי הטיול שלך. בלי טיול היא לא מוצגת.'),
    ('כפתור החשבון', 'במחשב: בסרגל העליון, ליד מתג היום והלילה. בטלפון (פיני: המקום ב-W1 וב-W3 נראה מוזר) שלוש אפשרויות בשורות למטה: א. בקפסולה אחת עם מתג היום והלילה (ההמלצה: נקי, באותו מקום שמחפשים הגדרות); ב. סקי־פס שתלוי על עמוד השלטים (הכי בשפה של האתר, אבל רחוק מלמעלה); ג. שורה מתחת לשם המקום ("שלום, פיני").'),
    ('שלט "קבוצה" על העמוד', 'במקום "חלקים נוספים בקרוב". לאורח: יצירה או הצטרפות בקוד; למי שבקבוצה: שם הקבוצה ומספר החברים.'),
    ('דף מחיקת החשבון', 'בכתובת /account, נדרש לגוגל פליי. עובד בלי האפליקציה: נכנסים ומוחקים, או כותבים לכתובת התמיכה. מתחייבים ל-30 יום במייל.'),
    ('הצטרפות מהאתר', 'הקישור /join/<קוד> פותח את האפליקציה אם מותקנת, ואת האתר אם לא. בדפדפן: רק שם, בלי הרשמה, כמו באפליקציה (Q3, Q5).'),
    ('מה לא בסבב הזה', 'יצירת קבוצה, הזמנה, מפגשים, שיאים וניהול באתר: אותם מסכים כמו באפליקציה (Q1, Q2, Q8 עד Q10), באותה שפה של W8. אם צריך, אצייר אותם גם לאתר.'),
]


def page(title, body, w, h):
    return f'''<!doctype html>
<html lang="he" dir="rtl">
<head>
<meta charset="utf-8">
<title>{title}</title>
<script src="./support.js"></script>
</head>
<body>
<x-dc>
<helmet>
<style>
body{{margin:0;background:#EEF2F5}}
</style>
</helmet>
{body}
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{{"$preview":{{"width":{w},"height":{h}}}}}'>
class Component extends DCLogic {{
  renderVals() {{ return {{}}; }}
}}
</script>
</body>
</html>
'''


def shot(title, url, w, h):
    return (f'<div style="width: {w}px; height: {h}px; overflow: hidden; background: #EEF2F5">'
            f'<img src="{url}" alt="{title}, צילום מסך של אב טיפוס בתוך האתר" style="display: block; width: {w}px; height: auto"></div>')


def decide_card():
    items = ''.join(f'<li style="display: flex; flex-direction: column; gap: 2px; padding: 10px 0; border-top: 1px solid #CBD5DF">'
                    f'<b style="font-size: 16px">{i + 1}. {t}</b><span style="font-size: 14px; line-height: 1.5; color: #4B5A6F">{s}</span></li>'
                    for i, (t, s) in enumerate(DECIDE))
    return (f'<div style="width: 520px; height: 1349px; box-sizing: border-box; padding: 28px; background: #FFFFFF; color: #13233A; '
            f"font-family: 'IBM Plex Sans Hebrew', system-ui, sans-serif; direction: rtl; border-top: 8px solid #1F5FC4\">"
            f"<h1 style=\"margin: 0; font-family: Karantina, 'Arial Narrow', sans-serif; font-size: 52px; line-height: 1\">מה לאשר בסבב 12</h1>"
            f'<p style="margin: 8px 0 16px; font-size: 14.5px; line-height: 1.55; color: #4B5A6F">שלב 13.9: "הטיול שלך", כניסה, חשבון וקבוצה באתר. סבב 10 צייר אותם רק לאפליקציה. '
            f'כאן הם מצוירים בתוך האתר האמיתי, עם העיצוב שלו, כדי שמה שמאשרים הוא מה שייבנה. נתוני הדוגמה (הטיסה השנייה בקבוצה, הקוד) מסומנים.</p>'
            f'<ol style="margin: 0; padding: 0; list-style: none">{items}</ol></div>')


def build(dst):
    boards = [('R12-0-Decide.dc.html', 'מה לאשר בסבב 12', page('מה לאשר בסבב 12', decide_card(), 520, 1349), 520, 1349, 0)]
    for f, t, url, w, h, row in SHOTS:
        boards.append((f, t, page(t, shot(t, url, w, h), w, h), w, h, row))
    for f, _, html_, *_ in boards:
        (dst / f).write_text(html_, encoding='utf-8')
    ip = dst / 'canvas.json'
    index = json.loads(ip.read_text(encoding='utf-8'))
    if not any(p['id'] == PAGE[0] for p in index['pages']):
        index['pages'].insert(0, {'id': PAGE[0], 'name': PAGE[1]})
    y = 0
    for ri, title in enumerate(ROWS):
        x, rowh = 0, 0
        for f, t, _, w, h, row in boards:
            if row != ri:
                continue
            index['boards'][f] = {'x': x, 'y': y, 'w': w, 'h': h, 'page': PAGE[0], 'title': t}
            if f not in index['order']:
                index['order'].append(f)
            x += w + 80
            rowh = max(rowh, h)
        index['notes'][f'{PAGE[0]}row{ri}'] = {'kind': 'title1', 'maxW': max(860, x - 80), 'page': PAGE[0], 'text': title, 'w': 240, 'x': 0, 'y': y - 300}
        y += rowh + 120 + 300
    index['launch'] = {'view': 'canvas', 'page': PAGE[0]}
    ip.write_text(json.dumps(index, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    return len(boards)


if __name__ == '__main__':
    dst = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else root / 'design/canvas/project'
    print(build(dst), 'boards ->', dst)
