#!/usr/bin/env python3
"""Round 25: a readable map for large resorts (Sella Ronda, Sölden), decision 68. Drawn over the real site by shots.mjs + proposal.js.

One canvas page, "סבב 25". Run from the repo root:
  python3 design/round25/build.py webp
  python3 design/round25/build.py [canvas project folder]"""
import html, json, pathlib, subprocess, sys

here = pathlib.Path(__file__).resolve().parent
root = here.parent.parent
sys.path.insert(0, str(root / 'design/round12'))
from build import page  # the board shell of rounds 12 to 24

PAGE = ('round25', 'סבב 25: מפה קריאה באתר גדול')
PH = ['today-sr', 'today-sr-night', 'today-sr-list', 'today-sr-3d', 'today-so', 'today-gu',
      'a-sr', 'a-sr-night', 'a-sr-list', 'a-so', 'b-sr-badia', 'b-sr-badia-night', 'b-sr-gardena', 'b-sr-arabba', 'b-sr-badia-list',
      'b-so-c', 'b-so-b', 'b-so-b-night', 'c-sr', 'd-sr-badia', 'd-sr-badia-night', 'd-sr-gardena', 'd-so-c', 'd-sr-all', 'd-so-all']
DK = ['today-sr-desk', 'a-sr-desk', 'a-sr-desk-night', 'b-sr-badia-desk', 'b-sr-gardena-desk-night', 'd-sr-badia-desk', 'd-sr-all-desk']
SRC = {n: ('shots/' + n + '.png', 390, 0, 0) for n in PH}
SRC.update({n: ('shots/' + n + '.png', 900, 0, 0) for n in DK})
N = lambda f: f + '.webp'
BOARDS = [
    ('today', 'Today', 'היום: Sella Ronda בטלפון', '9,049 קווים במפה, בכ-50 מ׳ לפיקסל: מסלול של קילומטר הוא 20 פיקסלים. התוויות כבר לא עולות זו על זו, אבל אין היררכיה: נשארות Kids Fun Line ו-Bear Slope, ולא העמקים והכפרים. הרכבלים השחורים והתחנות הם הדבר הבולט במסך. הרשימה: 248 מסלולים לפי צבע וא״ב, בלי חלוקה. בתלת-ממד אותה צפיפות, מ-26 ק״מ.',
     [('מבט על', ['today-sr']), ('בלילה', ['today-sr-night']), ('הרשימה', ['today-sr-list']), ('תלת-ממד', ['today-sr-3d'])]),
    ('today', 'Today2', 'היום: Sölden וגודאורי', 'Sölden (2,232 קווים) עמוס פחות, אבל אותו דבר: שלושה אזורים ואין להם שם על המפה. גודאורי (1,117) קריא, ולא משתנה בהצעה.',
     [('Sölden', ['today-so']), ('גודאורי', ['today-gu'])]),
    ('today', 'TodayDesk', 'היום: מחשב', 'במחשב יש מקום לתוויות, ולכן קריא יותר, כמו שפיני אמר. אבל גם כאן אין סדר: אותו משקל לכל מסלול, ורשימה של 248.',
     [('מחשב', ['today-sr-desk'])]),
    ('all', 'All', 'א. כל ההר: עמקים, לא קווים (מומלץ)', 'במבט על כל האתר רואים קודם עמקים: אזור רך סביב המסלולים של כל עמק (המסלולים עצמם, ברוחב גדול ובשקיפות; לא גבול שצויר), שלט עם השם וכמה מסלולים בכל צבע. המסלולים דקים ובלי מעטפת, הרכבלים אפורים ודקים ובלי נקודות תחנה, ובלי תוויות של מסלולים ורכבלים. נשארים הכפרים והפסגות. נגיעה בעמק או בשלט שלו מטיסה אליו (ב). הרשימה מתחת: ארבע שורות במקום 248.',
     [('Sella Ronda', ['a-sr']), ('בלילה', ['a-sr-night']), ('הרשימה', ['a-sr-list']), ('Sölden', ['a-so'])]),
    ('all', 'AllDesk', 'א. במחשב', 'אותו דבר, והרשימה בצד מחולקת לעמקים.',
     [('ביום', ['a-sr-desk']), ('בלילה', ['a-sr-desk-night'])]),
    ('val', 'Val', 'ב. עמק אחד: בורר, מסגור והיררכיה (מומלץ, יחד עם א)', 'שורת צ\'יפים מתחת לסינון: "כל ההר" וכל עמק עם הצבע שלו. בחירה (או נגיעה בשלט) ממסגרת את העמק; השאר מתעמעם ל-16 אחוז ונשאר להתמצאות; האזור של העמק נשאר כרקע עדין. רק התוויות של העמק, והמסלולים הארוכים קודם, אחריהם הרכבלים. הרשימה מתחת היא המסלולים של העמק, עם "כל ההר" לחזרה. קישור לשיתוף: #map/area/badia. כשמתקרבים בזום לעמק בלי לבחור, המפה כמו היום.',
     [('Alta Badia', ['b-sr-badia']), ('בלילה', ['b-sr-badia-night']), ('Val Gardena', ['b-sr-gardena']), ('Arabba', ['b-sr-arabba']), ('הרשימה של העמק', ['b-sr-badia-list'])]),
    ('val', 'ValSo', 'ב. Sölden: שלושת האזורים', 'Gaislachkogl, Giggijoch והקרחון, לפי הרשימה הרשמית. באזור קטן, הלוחיות של סבב 23 נשארות כמו שהן.',
     [('הקרחון', ['b-so-c']), ('Giggijoch', ['b-so-b']), ('בלילה', ['b-so-b-night'])]),
    ('val', 'ValDesk', 'ב. במחשב: הרשימה בצד', 'הרשימה בצד היא המסלולים של העמק. מעבר עכבר על שורה מבליט את המסלול במפה (מעטפת צהובה, כמו בחירה בתלת-ממד), בלי לשנות שום דבר אחר. כאן Boè.',
     [('Alta Badia, מעבר עכבר', ['b-sr-badia-desk']), ('Val Gardena בלילה', ['b-sr-gardena-desk-night'])]),
    ('d3', 'D3', 'ד. תלת-ממד: גם כאן עמקים (מומלץ, עודכן לפי פיני)', 'פיני: "בתלת-ממד אני לא רואה את החלוקה לעמקים, זו מפה של הכל". עכשיו התלת-ממד מראה את אותה מערכת עמקים. כל ההר: מבט גבוה יותר, כל מסלול בצבע של העמק שלו, שלט לכל עמק (על נקודה גבוהה במסלולים שלו, כדי שרכס לא יסתיר אותו), ובלי שמות של מסלולים, רכבלים, כפרים ופסגות. דרגת הקושי חוזרת כשבוחרים עמק. עמק אחד: המצלמה נמוכה מהצד החיצוני שלו, מבט אל הרכס; המסלולים והרכבלים שלו כמו היום, והמסלולים של שאר העמקים באפור דק ובלי שם, בלי הרכבלים שלהם. בטלפון ההר כולו לא נכנס במסך אחד, ולכן הוא מוצג לאורך; גוררים כדי לראות עוד, או בוחרים עמק.',
     [('היום', ['today-sr-3d']), ('כל ההר', ['d-sr-all']), ('Alta Badia', ['d-sr-badia']), ('בלילה', ['d-sr-badia-night']), ('Val Gardena', ['d-sr-gardena'])]),
    ('d3', 'D3So', 'ד. Sölden', 'כל ההר בשלושת הצבעים, והקרחון לבד.',
     [('כל ההר', ['d-so-all']), ('הקרחון', ['d-so-c'])]),
    ('d3', 'D3Desk', 'ד. במחשב', 'כל ההר עם ארבעת השלטים, ו-Alta Badia מצפון-מזרח, אל Sella.',
     [('כל ההר', ['d-sr-all-desk']), ('Alta Badia', ['d-sr-badia-desk'])]),
    ('alt', 'Alt', 'ג. הרשימה קודם (לא מומלץ)', 'הרעיון של פיני: באתר גדול המסך נפתח על הרשימה, עם רצועת מפה קטנה למעלה. זה עובד כשיודעים את שם המסלול, אבל רוב מי שמגיע לאתר חדש לא יודע; הוא צריך לראות איפה הדברים. לכן ההמלצה לקחת מכאן רק את החלוקה לעמקים ברשימה (בא וב), ולהשאיר את המפה העמוד המרכזי (החלטה 67).',
     [('הרשימה קודם', ['c-sr']), ('לעומת: א', ['a-sr-list'])]),
    ('alt', 'Gud', 'גודאורי: בלי שינוי', 'שכבת העמקים רק באתר שיש לו חלוקה לעמקים בנתונים. גודאורי (27 מסלולים) נשאר בדיוק כמו היום, עם "צד Kobi".',
     [('גודאורי', ['today-gu'])]),
]
ROWS = [('today', 'היום'), ('all', 'א. כל ההר: עמקים'), ('val', 'ב. עמק אחד'), ('d3', 'ד. תלת-ממד לכל עמק'), ('alt', 'ג. הרשימה קודם, וגודאורי'), ('text', 'ערכים למימוש')]
TEXT_BOARDS = [
    ('Spec', 'ערכים למימוש', 640, 2050, [
        ('מתי', 'רק באתר שיש בו חלוקה לעמקים בנתונים (היום Sella Ronda ו-Sölden). גודאורי בלי שינוי.'),
        ('נתונים', 'לכל מסלול ולכל רכבל שדה עמק, מקיבוץ בלבד: הרשימה הכתובה של העמק, ואם אין, האזור ב-OpenSkiMap, ואחרון, העמק של המסלול הקרוב (מסומן). שום קו לא נוסף. חוזה משותף: סקירת ארכיטקט ושורה ב-PARITY (אצל סשן המנהל).'),
        ('צבע לעמק', 'ארבעה גוונים רכים, לא צבעי דרגת קושי. ביום: #C49A3A, #2F978B, #8C6CC0, #7E9136. בלילה: #E2BB5C, #4CC3B4, #B69AE8, #A9BE57. טוקנים --v-<id>, לפי סדר העמקים בקובץ.'),
        ('האזור הרך', 'המסלולים של העמק, ברוחב 1,500 מ׳ ב-Sella Ronda ו-700 ב-Sölden (בערך 6 אחוז מרוחב האתר), קצוות מעוגלים, בקבוצה אחת בשקיפות 34 אחוז ביום ו-30 בלילה. מתחת למעטפות של המסלולים, מעל התבליט. בעמק נבחר: 12 אחוז ביום, 10 בלילה, רק שלו.'),
        ('כל ההר (א)', 'עד שעמק ממלא את רוב המסך (בזום שבו מטר לפיקסל גבוה מ-25 בערך): המסלולים בחצי מהרוחב ובלי מעטפת; שטח מסלול רחב ב-55 אחוז; רכבלים ב---muted, 0.9, שקיפות 75 אחוז, בלי תחנות; בלי תוויות של מסלולים ורכבלים. כפרים ופסגות כמו היום.'),
        ('שלט עמק', 'רקע --paper, מסגרת --rule 1.5, פס עליון 4 בצבע העמק, צל --shadow. השם ב-Karantina 19 (16 באתר קטן), מתחת ריבוע צבע ומספר לכל צבע, 11.5 במשקל 600. במרכז המסלולים של העמק, ושלטים נדחפים זה מזה. כולו יעד מגע (לפחות 44); נגיעה בוחרת את העמק.'),
        ('הצ\'יפים', 'שורה מתחת לסינון, אותו רכיב כמו הסינון (44, פינות ישרות): "כל ההר" ואחריו העמקים, ריבוע 12 בצבע העמק. נבחר: רקע --ink וטקסט --snow. בטלפון גוללים הצידה, והנבחר נגלל לתוך המסך. קישור: #map/area/<id>.'),
        ('עמק נבחר (ב)', 'מסגור לגבולות המסלולים של העמק (בלי דרכים מקשרות ארוכות), עם 18 אחוז שוליים. מסלולים ורכבלים של עמקים אחרים ב-16 אחוז. תוויות רק של העמק: המסלולים לפי אורך, מהארוך, ואחריהם שמות הרכבלים; אותו כלל של "לא עולה על אחרת" של היום. לוחיות של סבב 23 כמו היום.'),
        ('הרשימה', 'כל ההר: שורה לכל עמק, גובה 64 לפחות, פס 6 בצבע העמק, השם ב-Karantina 24, וריבוע ומספר לכל צבע, מספר המסלולים והקילומטרים. נגיעה כמו בצ\'יפ. עמק: "← כל ההר", כותרת עם הפס, "N מסלולים · N ק״מ", והמסלולים כמו ברשימה של היום.'),
        ('מחשב', 'הרשימה בצד, אותו דבר. מעבר עכבר על שורה: המעטפת של המסלול בצהוב #FFD34D ברוחב 11 והקו ברוחב 5, מעל השאר. בלי בחירה ובלי תנועת מצלמה.'),
        ('תלת-ממד (ד)', 'אותם צ\'יפים. כל ההר: זווית 0.95 (גבוה), מסלול בצבע העמק שלו (הטוקנים --v-<id>, גם השטח הרחב), רכבלים #8A96A6, ובלי תוויות של מסלולים, רכבלים, כפרים ופסגות; שלט העמק הוא התווית הראשונה שמונחת, על נקודת מסלול גבוהה קרוב לאמצע העמק (גובה פחות 0.12 כפול המרחק מהאמצע). עמק אחד: מצלמה במרכז העמק, זווית 0.4, מרחק פי 1.15 מהרוחב שלו, מהכיוון שמהמרכז של האתר אל העמק (ב-Sella Ronda: קבוצת Sella, 46.512, 11.80); המסלולים של עמקים אחרים #AEB8C4 ובלי תווית, והרכבלים שלהם לא מצוירים.'),
        ('מחרוזות', '"כל ההר", "עמקים" (תווית לקורא מסך של השורה), "N עמקים", "N מסלולים · N ק״מ", בארבע השפות. שמות העמקים משמם המקומי, בלי תרגום.'),
    ]),
]
DECIDE = [
    ('מה ביקשו', 'פיני: באתר גדול (Sella Ronda, וגם Sölden) המפה מבולגנת ולא קריאה. הציע רשימה כאפשרות ראשית, חלוקה לקטעים עם מבט על הכל, ותלת-ממד בשיפוע.'),
    ('מה מצאתי', 'הבעיה היא לא חפיפה של טקסט (אין), אלא שאין היררכיה: 9,049 קווים באותו משקל, רכבלים שחורים ותחנות בולטים מהמסלולים, ותוויות שנבחרות לפי סדר ולא לפי חשיבות. החלוקה לעמקים כבר קיימת במקורות אמיתיים.'),
    ('ההמלצה: מערכת אחת של עמקים', '**א** כל ההר מראה עמקים עם שם וספירה, והפרטים רק בפנים; **ב** שורת צ\'יפים לעמק, שממסגרת ומעמעמת את השאר, עם תוויות לפי חשיבות; הרשימה מחולקת לעמקים; **ד** גם בתלת-ממד: כל ההר בצבעי העמקים עם שלטים, ובעמק אחד מצלמה נמוכה והשאר באפור. בטלפון ובמחשב.'),
    ('מה לא', '**ג** הרשימה קודם: מתאים למי שיודע שם, לא למי שמגיע לאתר חדש. רק החלוקה של הרשימה נכנסת. גודאורי בלי שינוי.'),
    ('שאלה 1', 'לאשר את א, ב וד יחד כמו בקנבס?'),
    ('שאלה 2', 'ארבעה גוונים רכים לעמקים (חרדל, טורקיז, סגול, זית), שלא מתנגשים בצבעי הקושי. בסדר, או בלי צבע לעמקים (רק שם)?'),
    ('שאלה 3', 'ג, הרשימה קודם: לוותר (ההמלצה), או לנסות גם אותה באתר הגדול?'),
    ('אחרי האישור', 'שדה עמק בנתונים: סקירת ארכיטקט ושורה ב-PARITY (סשן המנהל), ואז מימוש. הנתונים: קיבוץ בלבד, שום קו לא נוסף.'),
]
GAP, PAD, HEAD, CGAP, DW, DH = 18, 20, 150, 28, 600, 1160


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
    lead = ('ארבעה כיוונים, מצוירים מעל האתר האמיתי, והמלצה אחת. העמקים מקיבוץ של מקורות אמיתיים; שום קו לא נוסף.')
    boards = [('R25-0-Decide.dc.html', 'מה לאשר בסבב 25', page('מה לאשר בסבב 25', card('מה לאשר בסבב 25', DECIDE, DW, DH, lead), DW, DH), DW, DH, None)]
    for b in BOARDS:
        c, w, h = board(b, urls)
        boards.append((f'R25-{b[1]}.dc.html', b[2], page(b[2], c, w, h), w, h, b[0]))
    for key, title, w, h, items in TEXT_BOARDS:
        boards.append((f'R25-{key}.dc.html', title, page(title, card(title, items, w, h, accent='#13233A'), w, h), w, h, 'text'))
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
