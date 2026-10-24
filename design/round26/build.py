#!/usr/bin/env python3
"""Round 26: the "Android app" sign on the home page links to Google Play (the app was published 23.10.2026).
Drawn over the real site by shots.mjs + proposal.js. One canvas page, "סבב 26". Run from the repo root:
  python3 design/round26/build.py webp
  python3 design/round26/build.py [canvas project folder]"""
import html, json, re, pathlib, subprocess, sys

here = pathlib.Path(__file__).resolve().parent
root = here.parent.parent
sys.path.insert(0, str(root / 'design/round12'))
from build import page  # the board shell of rounds 12 to 25

PAGE = ('round26', 'סבב 26: האפליקציה בגוגל פליי')
PH = ['today', 'today-open', 'a-he', 'a-he-night', 'a-en', 'a-en-night', 'a-iphone', 'b-he', 'b-en-night', 'f-iphone']
DK = ['d-he', 'd-en-night', 'f-desk']
SRC = {n: ('shots/' + n + '.png', 390, 0, 0) for n in PH}
SRC.update({n: ('shots/' + n + '.png', 900, 0, 0) for n in DK})
N = lambda f: f + '.webp'
BOARDS = [
    ('today', 'Today', 'היום: השלט מוסתר', 'השלט "אפליקציה לאנדרואיד" מופיע רק כשקובץ ההתקנה נמצא באתר, ולכן היום הוא מוסתר. כך הוא היה נראה: הורדת קובץ, ושלושה שלבים של "איך מתקינים" עם אישור התקנה ממקור לא ידוע. מאז 23.10.2026 האפליקציה בגוגל פליי, וכל זה כבר לא נכון.',
     [('דף הבית היום', ['a-iphone']), ('עם הקובץ', ['today']), ('"איך מתקינים?" פתוח', ['today-open'])]),
    ('a', 'Sign', 'א. שלט על העמוד, לחנות (מומלץ)', 'אותו שלט, בשפה שלנו: "אפליקציה לאנדרואיד", ומתחת "ב-Google Play" וסימן של קישור החוצה. נגיעה פותחת את דף האפליקציה בגוגל פליי (באנדרואיד: ישר באפליקציה של החנות). "איך מתקינים?" יורד: החנות מתקינה. השלט עובר לסוף העמוד, אחרי "קבוצה", וצר יותר, כי הוא יוצא מהאתר ושאר השלטים הם חלקי האתר. שם החנות במילים ולא הלוגו של גוגל: לפי הכללים שלהם הלוגו שמור לתג הרשמי.',
     [('עברית', ['a-he']), ('בלילה', ['a-he-night']), ('English', ['a-en']), ('English, בלילה', ['a-en-night'])]),
    ('a', 'Who', 'מי רואה את השלט', 'באנדרואיד ובמחשב כן; באייפון לא, עד שיש אפליקציה ב-App Store (TestFlight הוא לבודקים בלבד). כך נראה העמוד באייפון: כמו היום.',
     [('באייפון', ['a-iphone'])]),
    ('a', 'Desk', 'א. במחשב: השלט וקוד QR', 'במחשב אי אפשר להתקין, ולכן מתחת לשלט כרטיס: קוד QR לסריקה בטלפון, ו"או" התג הרשמי של גוגל פליי, שפותח את דף החנות בדפדפן (משם אפשר לשלוח את ההתקנה לטלפון). התג בגובה 44 (המינימום של גוגל 28), עם רווח של רבע מגובהו מכל צד, בלי שינוי, בשפה של הדף. הקוד מוביל לאותו דף, עם סימון משלו (home_qr), כדי שנדע כמה סרקו.',
     [('עברית', ['d-he']), ('English, בלילה', ['d-en-night'])]),
    ('b', 'Badge', 'ב. התג הרשמי במקום השלט (לא מומלץ)', 'התג של גוגל בתוך עמוד השלטים. מוכר ואמין, אבל נראה כמו פרסומת בתוך הניווט, ולא בשפה של האתר: שחור, מעוגל, באותיות של גוגל. ואסור לשנות בו כלום (צבע, גודל יחסי, טקסט). לכן התג רק במחשב, בכרטיס של ה-QR, ובטלפון השלט.',
     [('עברית', ['b-he']), ('English, בלילה', ['b-en-night'])]),
    ('f', 'Future', 'בהמשך: App Store, בלי לעצב שוב', 'כשהאפליקציה לאייפון תאושר: באייפון שלט "אפליקציה לאייפון, ב-App Store" באותו מקום ובאותה צורה; באנדרואיד רק השלט של גוגל פליי. במחשב שני התגים באותו גובה, אחד ליד השני (גוגל דורשת שהתג שלה לא יהיה קטן מאחרים), ושני קודי QR או קוד אחד שמפנה לפי הטלפון (החלטה לאז). התג של אפל מסומן כאן כמקום שמור בלבד, ולא צויר.',
     [('אייפון, בהמשך', ['f-iphone']), ('מחשב, בהמשך', ['f-desk'])]),
]
ROWS = [('today', 'היום'), ('a', 'א. שלט לחנות, ובמחשב QR'), ('b', 'ב. התג הרשמי'), ('f', 'בהמשך: App Store'), ('text', 'ערכים למימוש')]
TEXT_BOARDS = [
    ('Spec', 'ערכים למימוש', 600, 1420, [
        ('הקישור', '`https://play.google.com/store/apps/details?id=io.github.pini236.skiapp&referrer=utm_source%3Dsite%26utm_medium%3Dhome_board`. בקוד ה-QR: `utm_medium%3Dhome_qr`. האפליקציה כבר קוראת את ה-Install Referrer (`telemetry/OpenSource.kt`), כך שהתקנות מהאתר ייספרו עם המקור.'),
        ('מי רואה', 'השלט מוצג לכל דפדפן חוץ מאייפון ואייפד (לפי `navigator.userAgent` ו-`navigator.maxTouchPoints` לאייפד שמתחזה למק). כרטיס ה-QR רק ברוחב 900 ומעלה ובלי מגע (`pointer: fine`). בלי בדיקת קובץ: השלט כבר לא תלוי ב-`downloads/gudauri-2027.apk`.'),
        ('השלט', 'אותו `.board-app` (רקע --ink, טקסט --paper), ברוחב 80 אחוז, האחרון בעמוד אחרי "קבוצה". הכותרת `home.app_board` כמו היום; מתחת "ב-Google Play" בגודל 13, ושם החנות בתוך `<bdi>` כדי שלא יתהפך בעברית. סימן קישור החוצה 12 על 12. `target` לא נדרש: באנדרואיד הקישור נפתח באפליקציה של החנות.'),
        ('"איך מתקינים?"', 'יורד, עם ארבעת המפתחות שלו (`home.app_how`, `home.app_step1` עד `3`, `home.app_req`) ו-`home.app_download`, `home.app_download_meta_size`, אם אף פלטפורמה לא משתמשת בהם.'),
        ('כרטיס ה-QR (מחשב)', 'מתחת לשלט, ברוחב השלט: רקע --paper, מסגרת --rule 1.5, פס עליון 4 --ink, צל --shadow. QR 112 על 112 על לבן קבוע (גם בלילה, כדי שייסרק), רמת תיקון M. מימין "סורקים בטלפון אנדרואיד" (15, משקל 600), "או", והתג הרשמי בגובה 44.'),
        ('התג הרשמי', 'מהחבילה העדכנית ב-Partner Marketing Hub של גוגל (הורדה דורשת לאשר את תנאי השימוש שם: פיני או סשן האתר). לא לשנות צבע, יחס או טקסט; גובה מינימלי 28 פיקסלים; רווח של רבע מהגובה מכל צד; גרסה לפי שפת הדף (יש עברית; לרוסית ולגאורגית לבדוק בחבילה, ואם אין, אנגלית). הקנבס משתמש בתג הישן מ-play.google.com רק לתצוגה.'),
        ('מדידה', '`app_download` נשאר, עם store: play ו-placement: home, או home_qr בנגיעה בתג שבכרטיס. השינוי בחוזה המדידה ב-`docs/GROWTH.md`.'),
        ('מחרוזות חדשות (לסשן האתר, לא נכנסו לקובץ השפות)', '`home.app_store`: ב-Google Play / on Google Play / в Google Play / Google Play-ზე. `home.app_scan`: סורקים בטלפון אנדרואיד / Scan with an Android phone / Отсканируйте телефоном Android / დაასკანერეთ Android ტელეფონით. `home.app_or`: או / or / или / ან. בהמשך: `home.ios_board` (אפליקציה לאייפון), `home.ios_store` (ב-App Store).'),
        ('בהמשך: App Store', 'אותו רכיב (`.board-ios`) באייפון במקום השלט של גוגל; במחשב שני התגים באותו גובה בכרטיס. התג של אפל לפי ההנחיות של אפל, כשיגיע הזמן.'),
    ]),
]
DECIDE = [
    ('מה ביקשו', 'סשן המנהל, לבקשת פיני: האפליקציה בגוגל פליי מ-23.10.2026, והשלט "אפליקציה לאנדרואיד" בדף הבית צריך להוביל לחנות במקום לקובץ.'),
    ('ההמלצה', '**א** שלט בשפה שלנו, "אפליקציה לאנדרואיד · ב-Google Play", בסוף עמוד השלטים, שפותח את החנות. **במחשב** מתחתיו כרטיס עם קוד QR והתג הרשמי. **באייפון** לא מוצג עד App Store. "איך מתקינים?" יורד.'),
    ('מה לא', '**ב** התג הרשמי במקום השלט בטלפון: נראה כמו פרסומת בתוך הניווט, ואסור לשנות בו כלום. התג נשאר רק בכרטיס של המחשב.'),
    ('שאלה 1', 'לאשר את א, עם הכרטיס במחשב, כמו בקנבס?'),
    ('שאלה 2', 'השלט בסוף העמוד, אחרי "קבוצה" (ההמלצה), או במקום של היום, לפני "קבוצה"?'),
    ('שאלה 3', 'באייפון: להסתיר עד App Store (ההמלצה), או להראות כבר עכשיו את השלט של אנדרואיד גם שם?'),
    ('אחרי האישור', 'סשן האתר מממש לפי "ערכים למימוש"; התג העדכני מהחבילה של גוגל; ההתקנות מהאתר נספרות דרך ה-referrer. שום שינוי ב-`site/` עד האישור.'),
]
GAP, PAD, HEAD, CGAP, DW, DH = 18, 20, 150, 28, 600, 1100

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
    lead = ('שני כיוונים לשלט, מצוירים מעל האתר האמיתי, בטלפון ובמחשב, ביום ובלילה, בעברית ובאנגלית, והמלצה אחת.')
    boards = [('R26-0-Decide.dc.html', 'מה לאשר בסבב 26', page('מה לאשר בסבב 26', card('מה לאשר בסבב 26', DECIDE, DW, DH, lead), DW, DH), DW, DH, None)]
    for b in BOARDS:
        c, w, h = board(b, urls)
        boards.append((f'R26-{b[1]}.dc.html', b[2], page(b[2], c, w, h), w, h, b[0]))
    for key, title, w, h, items in TEXT_BOARDS:
        boards.append((f'R26-{key}.dc.html', title, page(title, card(title, items, w, h, accent='#13233A'), w, h), w, h, 'text'))
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
