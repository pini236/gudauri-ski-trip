#!/usr/bin/env python3
"""Round 20: less text, more experience (Pini, 6.10.2026), and the "your trip" form. (The panorama map was dropped, decision 63; panorama.mjs stays for history.)

The shots come from inventory.mjs + classify.py (every text on every screen, marked by class), after.mjs (the same
screens after the text pass, drawn in the real site), trip.mjs (the ticket as the form) and panorama.mjs (the map from
our data only; the official map is not copied). One canvas page, "סבב 20", with a card on what to approve.
Run from the repo root:
  python3 design/round20/build.py collect <scratch folder> <round 18 site shots> <qa shots>
  python3 design/round20/build.py webp
  python3 design/round20/build.py [canvas project folder]"""
import html, json, pathlib, shutil, subprocess, sys

here = pathlib.Path(__file__).resolve().parent
root = here.parent.parent
sys.path.insert(0, str(root / 'design/round12'))
from build import page  # the board shell of rounds 12 to 19

PAGE = ('round20', 'סבב 20: פחות טקסט וטופס הטיול')
# name: (folder: r = round 20 scratch, s = round 18 site shots, q = qa shots; file; width; crop top; crop height)
SRC = {
    'b-home-guest': ('r', 'marked/home-guest.png', 390, 0, 1150), 'a-home-guest': ('r', 'after/home-guest.png', 390, 0, 1150),
    'b-map-run': ('r', 'marked/map-run.png', 390, 1250, 1500), 'a-map-run': ('r', 'after/map-run.png', 390, 1250, 1500),
    'b-map': ('r', 'marked/map.png', 390, 700, 1500),
    'b-meet': ('r', 'marked/meet.png', 390, 0, 1900), 'a-meet': ('r', 'after/meet.png', 390, 0, 1900),
    'b-games': ('r', 'marked/games.png', 390, 0, 844), 'a-games': ('r', 'after/games.png', 390, 0, 844),
    'b-about': ('r', 'marked/about.png', 390, 0, 1000), 'a-about': ('r', 'after/about.png', 390, 0, 1000),
    'b-signin': ('r', 'marked/signin.png', 390, 0, 844), 'a-signin': ('r', 'after/signin.png', 390, 0, 844),
    'b-group-empty': ('r', 'marked/group-empty.png', 390, 0, 844), 'a-group-empty': ('r', 'after/group-empty.png', 390, 0, 844),
    'b-join': ('r', 'marked/join.png', 390, 0, 844), 'a-join': ('r', 'after/join.png', 390, 0, 844), 'a-group-signin': ('r', 'after/group-signin.png', 390, 0, 844), 'a-join-name': ('r', 'after/join-name.png', 390, 0, 700),
    'b-account': ('r', 'marked/account.png', 390, 0, 844),
    'trip-site': ('s', 'site-trip-form.png', 390, 0, 883), 'trip-app': ('q', 'home-26-trip-form-filled.png', 390, 0, 844),
    'trip-1': ('r', 'trip/t1-ticket.png', 390, 0, 844), 'trip-2': ('r', 'trip/t2-dates.png', 390, 0, 844),
    'trip-3': ('r', 'trip/t3-done.png', 390, 0, 844), 'trip-4': ('r', 'trip/t4-route.png', 390, 0, 844),
    'pano-today-desk': ('r', 'pano-today-desk.png', 900, 0, 0), 'pano-today-phone': ('r', 'pano-today-phone.png', 390, 0, 0),
    'pano-desk': ('r', 'pano-desk.png', 900, 0, 0), 'pano-west-desk': ('r', 'pano-west-desk.png', 900, 0, 0), 'pano-phone': ('r', 'pano-phone.png', 390, 0, 0),
}
N = lambda f: f + '.webp'
KEY = ('<div style="display: flex; gap: 14px; flex-wrap: wrap; font-size: 13.5px; color: #4B5A6F">'
       '<span><i style="display: inline-block; width: 14px; height: 14px; margin-inline-end: 5px; vertical-align: -2px; background: #D5372E55; border: 2px solid #D5372E"></i>מיותר: למחוק</span>'
       '<span><i style="display: inline-block; width: 14px; height: 14px; margin-inline-end: 5px; vertical-align: -2px; background: #E8A21A55; border: 2px solid #E8A21A"></i>הסבר: להחליף בצורה</span>'
       '<span><i style="display: inline-block; width: 14px; height: 14px; margin-inline-end: 5px; vertical-align: -2px; background: #1F5FC455; border: 2px solid #1F5FC4"></i>חובה: להציג אחרת</span>'
       '<span><i style="display: inline-block; width: 14px; height: 14px; margin-inline-end: 5px; vertical-align: -2px; background: #8A97A855; border: 2px solid #8A97A8"></i>משפטי וקרדיטים</span>'
       '<span>בלי סימון: שמות, כפתורים ומספרים</span></div>')

# group, key, title, note, columns [(label, [images])]
BOARDS = [
    ('inv', 'Inv-map', 'המלאי: המפה והמסלול', 'כל טקסט במסך מסומן לפי הסיווג. פאנל המסלול הוא המסך הכי עמוס באתר (420 מילים), ורובן הסברים על הגבהים, השיפוע והמקורות.',
     [('המפה, מתחת לתלת-ממד', ['b-map']), ('פאנל המסלול (Tatra 2)', ['b-map-run'])]),
    ('inv', 'Inv-rest', 'המלאי: החשבון והקבוצה', 'בדפי החשבון, ההצטרפות והכניסה רוב המשפטים מסבירים מה קורה מאחורי הקלעים.',
     [('כניסה', ['b-signin']), ('חשבון', ['b-account']), ('הצטרפות', ['b-join'])]),
    ('ba', 'BA-home', 'דף הבית', 'עודכן לפי ההערות: השלטים בלי שורת הסבר ובלי המספר 27, בלי שורת המנעול ובלי פירוט המסלולים בתחתית. מצב הרכבלים נראה עכשיו כרכיב של מצב רכבלים שעוד כבוי: סמל רכבל, 12 נקודות ריקות, "0/12" ו"אין דיווח", באפור.',
     [('היום', ['b-home-guest']), ('ההצעה', ['a-home-guest'])]),
    ('ba', 'BA-run', 'פאנל המסלול', 'עודכן לפי ההערות: שלושה מספרים גדולים בלי שורה מתחתם. בלי "תלול בערך כמו", בלי חיבורים, בלי הערות ובלי מקור וודאות. נשארים הפרופיל (עם i), "מה מחכה לך" והסרטונים.',
     [('היום', ['b-map-run']), ('ההצעה', ['a-map-run'])]),
    ('ba', 'BA-meet', 'נקודת המפגש', 'עודכן לפי ההערות: על הסיכה רק שם התחנה. "שעה אחרת" הוא בלוק שעון בשורת השעות. בכרטיס רק התחנה והשעה (היום מעליה). בלי "איך מגיעים". השיתוף: ארבעה כפתורי סמל קטנים (וואטסאפ, תמונה, קישור, מפה).',
     [('היום', ['b-meet']), ('ההצעה', ['a-meet'])]),
    ('ba', 'BA-games', 'עמוד המשחקים', 'עודכן לפי ההערות: בלי התגיות על התמונות ("5 מסלולים" וכו׳) ובלי "עם צליל". רק השלטים והתמונות.',
     [('היום', ['b-games']), ('ההצעה', ['a-games'])]),
    ('ba', 'BA-about', 'הגדרות', 'עודכן לפי ההערות: בלי תיאור מתחת לצליל. המדידה במשפט קצר ופשוט: "בלי שם ובלי עוגיות. עוזר לנו לתקן תקלות."',
     [('היום', ['b-about']), ('ההצעה', ['a-about'])]),
    ('ba', 'BA-signin', 'כניסה', 'עודכן לפי ההערות: שלושת הסמלים (בלי סיסמה, בלי מיילים, רק השם) עברו לתוך הכרטיס "למה להתחבר". בשורה התחתונה ירד "(הטיול שלך)".',
     [('היום', ['b-signin']), ('ההצעה', ['a-signin'])]),
    ('ba', 'BA-group', 'קבוצה ריקה והצטרפות', 'עודכן לפי ההערות: "יצירת קבוצה דורשת חשבון" ירד; נגיעה ב"יצירת קבוצה" כאורח פותחת מגירת כניסה עם גוגל או אפל וכותרת אחת. בהזמנה: ראש המסך מראה לאיזו קבוצה מצטרפים, בצורה: השם, התאריכים והחברים, ורק אחר כך השם שלך (הקישור או הקוד כבר קובעים את הקבוצה, ולכן אין בחירה). הבקשה הממתינה היא כרטיס (שעון חול, "ממתין לאישור המנהל", ביטול), והקוד לאפליקציה בכרטיס קטן עם האותיות בתיבות והעתקה.',
     [('קבוצה ריקה: היום', ['b-group-empty']), ('ההצעה', ['a-group-empty']), ('נגיעה ביצירת קבוצה', ['a-group-signin']), ('הצטרפות: היום', ['b-join']), ('ההצעה: לפני ואחרי הבקשה', ['a-join-name', 'a-join'])]),
    ('trip', 'Trip-today', 'הטיול שלך: היום', 'באתר טופס של שמונה שדות. באפליקציה עשרה שדות, כולם עם בוררים. בשני הצדדים רק התאריך באמת חובה.',
     [('באתר', ['trip-site']), ('באפליקציה', ['trip-app'])]),
    ('trip', 'Trip-new', 'הטיול שלך: הכרטיס הוא הטופס', 'אין טופס. על הכרטיס הריק: TLV ו-TBS כבר כתובים, ושדה אחד גדול, "מתי טסים?". לוח שנה אחד, שתי נגיעות (הלוך וחזור), וימי הסקי נצבעים ביניהן. הכרטיס מתמלא מיד; מספר הטיסה והשעות מחכים כשדות על הכרטיס, למי שרוצה. כל שדה פותח רק את המגירה שלו.',
     [('1. הכרטיס הריק', ['trip-1']), ('2. שתי נגיעות', ['trip-2']), ('3. מוכן', ['trip-3']), ('4. שדה אחד', ['trip-4'])]),
]
ROWS = [('inv', 'המלאי: כל טקסט מסומן'), ('ba', 'לפני ואחרי'), ('trip', 'טופס "הטיול שלך"'), ('text', 'המספרים, המחקר והאפליקציה')]
TEXT_BOARDS = [
    ('Numbers', 'המספרים', 620, [
        ('מה נספר', 'כל טקסט גלוי ב-15 מסכים של האתר, בטלפון ובעברית: 1,775 מילים.'),
        ('הסיווג', '**158 מילים מיותרות** (9%), **267 הסברים שאפשר להחליף בצורה** (15%), **294 חובה** שאפשר להציג אחרת (17%), ו-105 של קרדיטים ומשפטי. השאר (957) שמות, כפתורים ומספרים: ממה שהמסך עשוי.'),
        ('הכי עמוסים', 'פאנל המסלול (420 מילים), המפה (303), ההגדרות (197), נקודת המפגש (135) ודף הבית (127).'),
        ('בקובץ השפות', '902 מחרוזות בלי המשחקים והמחקר, ו-215 מהן משפטים של שש מילים ומעלה.'),
        ('במה זה חוסך', 'אחרי ההצעות: כ-25% פחות מילים באתר, ובפאנל המסלול כמחצית. בלי לוותר על המקור והוודאות.'),
    ]),
    ('Research', 'איך מעבירים מידע בלי טקסט', 620, [
        ('מפות', 'אפליקציות המפות הגדולות כמעט לא כותבות הסברים: צבע, עובי קו וסמל אומרים מה זה, ופרט נוסף נפתח בנגיעה. מקרא קצר עם דוגמאות קו מחליף פסקה.'),
        ('אפליקציות סקי', 'מספרים גדולים (גובה, ירידה, שיפוע) במקום משפטים, צבעי הקושי המוכרים, ופרופיל גובה שהוא בעצמו ההסבר על המסלול.'),
        ('הדרכה', 'במקום שורת "איך משתמשים" שנשארת לתמיד: הדגמה של שתי שניות בפעם הראשונה (מחוות במפה, תלישת הספח), ואז היא נעלמת.'),
        ('מצב ריק', 'מצב ריק טוב מראה את הדבר עצמו, ריק, עם פעולה אחת. כמו הכרטיס הריק עם "מתי טסים?".'),
        ('מה חייב להישאר', 'פרטיות, ודאות ומקור (כלל דיוק 5), והקרדיטים שהרישיונות דורשים. אותם מקצרים לסמל ולתגית, והפירוט המלא נשאר בנגיעה.'),
    ]),
    ('App', 'באפליקציה', 620, [
        ('אותו קובץ שפות', 'האפליקציה והאתר קוראים מאותו קובץ מחרוזות, ולכן כל מחרוזת שנמחקת או מתקצרת משתנה בשני הצדדים יחד.'),
        ('מה יש רק באפליקציה', '319 מחרוזות של האפליקציה, 87 מהן משפטים של שש מילים ומעלה. הארוכות: ההסבר על התחזית (25 מילים), אורח בקבוצה (24 ו-20), מחיקת חשבון (20), ההערה על התנאים במסלול (18), ויצירת קבוצה (18).'),
        ('אותו טיפול', 'אותם כללים: ההסבר על התחזית הופך לסמל (i); אורח בקבוצה לתג אזהרה עם כפתור "שמירה"; "נגיעה במסלול בוחרת אותו..." להדגמה בפעם הראשונה. מחיקת חשבון נשארת: זו פעולה שאי אפשר לבטל.'),
        ('אחרי האישור', 'הרשימה המלאה, מחרוזת אחר מחרוזת, עם החלטה לכל אחת, עוברת לסשן התוכן והניסוח ולסשני האתר והאנדרואיד.'),
    ]),
]
DECIDE = [
    ('עודכן לפי ההערות שלך', 'כל ההערות על סבב 20 בקנבס טופלו בלוחות (6.10.2026): דף הבית, פאנל המסלול, נקודת המפגש, המשחקים, ההגדרות, הכניסה, הקבוצה וההזמנה. בכל לוח, עמודת "ההצעה" היא הגרסה החדשה.'),
    ('הכלל', 'אם הטקסט לא אומר משהו שאין במסך עצמו, הוא יורד. מה שחייב להישאר מקבל צורה: סמל, מספר גדול, כרטיס או מגירה.'),
    ('המקור והוודאות', 'פיני: "אפשר להוריד" (החלטה 64). יורדים מפאנל המסלול, באתר ובאפליקציה; בנתונים נשארים חובה.'),
    ('מסך ההצטרפות', 'פיני: "מסתדר". אין בחירה של קבוצה (הקישור או הקוד כבר קובעים אותה). בראש המסך השם בגדול, התאריכים והחברים כסמלים, ורק אחר כך "איך קוראים לך בקבוצה?".'),
    ('מוכן לאישור סופי', 'כל ההערות טופלו, ושתי השאלות נענו. מה שנשאר: אישור על כל הסבב (פחות טקסט בשמונת המסכים, וטופס "הטיול שלך"), ואז רשימת המחרוזות לסשן התוכן והקוד לסשני האתר והאנדרואיד.'),
    ('טופס "הטיול שלך"', 'בלי שינוי מהגרסה הקודמת: הכרטיס הוא הטופס, שתי נגיעות בלוח שנה, השאר לא חובה.'),
    ('איך ממשיכים', 'אחרי האישור: רשימת המחרוזות לסשן התוכן; הקוד אצל סשני האתר והאנדרואיד; שומר היישור בודק ששני הצדדים זהים.'),
]
GAP, PAD, HEAD, CGAP, DW, DH = 18, 20, 128, 28, 600, 1300


def collect(scratch, site18, qa):
    out = here / 'shots'
    out.mkdir(exist_ok=True)
    dirs = {'r': pathlib.Path(scratch), 's': pathlib.Path(site18), 'q': pathlib.Path(qa)}
    for name, (d, f, *_) in SRC.items():
        shutil.copy(dirs[d] / f, out / (name + '.png'))
    return len(SRC)


def webp():
    src, out = here / 'shots', here / 'shots/web'
    out.mkdir(exist_ok=True)
    for name, (d, f, w, top, h) in SRC.items():
        args = ['convert', str(src / (name + '.png')), '-resize', f'{w}x']
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
    key_html = KEY if group in ('inv', 'ba') else ''
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
    lead = ('ביקשת אתר ואפליקציה נקיים וחווייתיים, בלי טקסט שמתאר או מסביר כשאין בו צורך. ספרתי כל טקסט ב-15 מסכים וסיווגתי אותו, '
            'וציירתי את המסכים העמוסים אחרי, בתוך האתר האמיתי. וגם: טופס "הטיול שלך" מחדש. שום דבר לא נבנה לפני שתאשר.')
    boards = [('R20-0-Decide.dc.html', 'מה לאשר בסבב 20', page('מה לאשר בסבב 20', card('מה לאשר בסבב 20', DECIDE, DW, DH, lead), DW, DH), DW, DH, None)]
    for b in BOARDS:
        c, w, h = board(b, urls)
        boards.append((f'R20-{b[1]}.dc.html', b[2], page(b[2], c, w, h), w, h, b[0]))
    for key, title, w, items in TEXT_BOARDS:
        h = 720
        boards.append((f'R20-{key}.dc.html', title, page(title, card(title, items, w, h, accent='#13233A'), w, h), w, h, 'text'))
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
    if a[:1] == ['collect']:
        print(collect(*a[1:4]), 'shots collected')
    elif a == ['webp']:
        print(webp(), 'shots -> webp')
    else:
        dst = pathlib.Path(a[0]) if a else root / 'design/canvas/project'
        print(len(build(dst)), 'boards ->', dst)
