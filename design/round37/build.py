#!/usr/bin/env python3
"""Round 37: the iPhone app sign (App Store) next to the Google Play sign, the QR card with both stores, and the /app page (the app went live on the App Store 6.11.2026).
Drawn over the real site by shots.mjs + proposal.js. One canvas page, "סבב 37". Run from the repo root:
  python3 design/round37/build.py webp
  python3 design/round37/build.py [canvas project folder]"""
import html, json, re, pathlib, subprocess, sys

here = pathlib.Path(__file__).resolve().parent
root = here.parent.parent
sys.path.insert(0, str(root / 'design/round12'))
from build import page  # the board shell of rounds 12 to 25

PAGE = ('round37', 'סבב 37: האפליקציה לאייפון ב-App Store')
PH = ['today-iphone', 'a-ios-he', 'a-ios-he-night', 'a-ios-en', 'a-ios-en-night', 'a-android', 'page-ios', 'page-ios-night', 'page-android']
DK = ['d-he', 'd-en-night']
PG = ['page-desk', 'page-desk-night']
SRC = {n: ('shots/' + n + '.png', 390, 0, 0) for n in PH + PG}
SRC.update({n: ('shots/' + n + '.png', 900, 0, 0) for n in DK})
N = lambda f: f + '.webp'
BOARDS = [
    ('today', 'Today', 'היום: באייפון אין שלט', 'באייפון השלט "אפליקציה לאנדרואיד" מוסתר (יכולת `app` ב-`resorts.json`), ואין מקום לאפליקציה שלו. כך נראה העמוד באייפון עד היום: אחרי "קבוצה" אין כלום. האפליקציה בחנות מ-6.11.2026.',
     [('באייפון היום', ['today-iphone']), ('באנדרואיד היום (לא משתנה)', ['a-android'])]),
    ('a', 'Sign', 'א. באייפון: שלט App Store (מומלץ)', 'אותו שלט, באותו מקום ובאותה צורה כמו של גוגל פליי (סבב 26): "אפליקציה לאייפון", מתחתיו "ב-App Store" וסימן קישור החוצה, בסוף עמוד השלטים אחרי "קבוצה", ברוחב 80 אחוז. נגיעה פותחת את דף האפליקציה ב-App Store (באייפון: ישר באפליקציית החנות). כל טלפון רואה רק את השלט של החנות שלו: אייפון את App Store, אנדרואיד את גוגל פליי.',
     [('עברית', ['a-ios-he']), ('בלילה', ['a-ios-he-night']), ('English', ['a-ios-en']), ('English, בלילה', ['a-ios-en-night'])]),
    ('b', 'Desk', 'ב. במחשב: שני שלטים וכרטיס אחד עם שני קודי QR', 'במחשב אי אפשר להתקין, ולכן מתחת לשני השלטים (אנדרואיד, אחר כך אייפון) כרטיס אחד: שני קודי QR זה ליד זה, כל אחד עם הכיתוב שלו ("סורקים בטלפון אנדרואיד", "סורקים באייפון") והתג של החנות שלו מתחתיו, באותו גובה (44): גוגל דורשת שהתג שלה לא יהיה קטן מתג של חנות אחרת. **התג של אפל בצילום הוא מקום שמור, לא התג הרשמי** (הקובץ שלו מ-Apple Marketing Resources, ולא בריפו): שחור, מעוגל, "Download on the App Store", בלי להזיז או לשנות בו כלום, בשפת הדף אם יש. אפשר גם קוד אחד שמפנה לפי הטלפון, אבל הוא לא נותן הפרדה בין החנויות במדידה, ולכן לא מומלץ.',
     [('עברית', ['d-he']), ('English, בלילה', ['d-en-night'])]),
    ('c', 'Page', 'ג. הדף הקצר /app: כל טלפון לחנות שלו', 'היום באייפון הדף אומר "באייפון עוד אין אפליקציה" ומציע את האתר. עכשיו: **באייפון** מעבר ישיר ל-App Store ("פותחים את App Store...", כמו "פותחים את Google Play..." באנדרואיד, עם פס התקדמות); **באנדרואיד** כמו היום; **במחשב** (ובכל מכשיר אחר) לא עוברים לשום מקום: שני התגים זה ליד זה והקישור "או למפה באתר", כי אי אפשר לדעת לאיזו חנות הוא רוצה. הכותרת והתיאור בתצוגה של וואטסאפ מדברים עכשיו על שתי הפלטפורמות.',
     [('אייפון', ['page-ios']), ('אייפון, בלילה', ['page-ios-night']), ('מחשב וכל השאר', ['page-desk']), ('בלילה', ['page-desk-night']), ('אנדרואיד (כמו היום)', ['page-android'])]),
]
ROWS = [('today', 'היום'), ('a', 'א. שלט App Store'), ('b', 'ב. מחשב: שני קודים'), ('c', 'ג. הדף /app'), ('text', 'ערכים למימוש')]
TEXT_BOARDS = [
    ('Spec', 'ערכים למימוש', 600, 1500, [
        ('הקישור', '`https://apps.apple.com/app/gudi-gudauri-ski-map/id6825506333`. מדידת המקור: קישור קמפיין של אפל דורש `pt` (אסימון ספק מ-App Store Connect, אצל פיני) ו-`ct`: עד שיש `pt`, המקור נמדד באתר עצמו (`app_download` עם `store: appstore` ו-`placement` ‏`home`, `home_qr` או `app_link`), ובקוד ה-QR `ct=home_qr` כבר מצורף. התקנות מהאתר לא ייספרו באפליקציה עד אז (ב-App Store אין Install Referrer).'),
        ('מי רואה', 'השלט של אייפון: אייפון ואייפד (לפי `navigator.userAgent`, ואייפד שמתחזה למק לפי `navigator.maxTouchPoints`), ובמחשב. השלט של גוגל פליי: אנדרואיד ומחשב, כמו היום. אחת היכולות ב-`resorts.json`, `app`, מתפצלת לשתיים (`app-play`, `app-ios`) או נשארת ושני השלטים נבדקים לפי המכשיר; ההכרעה אצל הארכיטקט (אתר שרק לגודאורי יש לו אפליקציה).'),
        ('השלט', 'אותו `.board-app` (רקע --ink, טקסט --paper), ברוחב 80 אחוז, אחרי השלט של גוגל פליי, בלי `target` באייפון. כותרת `home.ios_board`, מתחת `home.ios_store` בגודל 13 עם שם החנות ב-`<bdi>`, וסימן קישור 12 על 12.'),
        ('כרטיס ה-QR (מחשב)', 'מתחת לשני השלטים, ברוחב 92 אחוז: --paper, מסגרת --rule 1.5, פס עליון 4 --ink, צל --shadow. שני טורים (ב-RTL: אנדרואיד מימין). QR 112 על 112 על לבן קבוע (גם בלילה), רמת תיקון M. כיתוב 14.5 במשקל 600, והתג בגובה 44. הקוד של אנדרואיד כבר באתר (`img/play-qr.svg`); של אייפון נבנה באותו כלי (`tools/build-play-qr.py`, מקבל קישור נוסף).'),
        ('התג הרשמי של אפל', 'לפי ההנחיות של אפל (Marketing Guidelines): לא לשנות את התג, גודל מינימלי 40 על 40 פיקסלים, רווח חופשי סביבו, גרסה לשפה (יש עברית, רוסית ואנגלית; לגאורגית: אנגלית). הקבצים מ-Apple Marketing Resources, והורדה דורשת לאשר את תנאי השימוש שם (פיני או סשן האתר). הקנבס מצייר מקום שמור בלבד, ולא התג הרשמי.'),
        ('הדף /app', 'ב-`site/js/app-link.js` מוסיפים זיהוי אייפון והפניה ל-App Store, ו-`utm` לא נתמך שם. בדף `site/app.html`: `og:description` ו-`meta description` מתעדכנים ל"אפליקציה לאנדרואיד ולאייפון, בחינם: ..." (בארבע השפות, `applink.*`), נוסף תג של אפל למצב מחשב, והמצב "באייפון עוד אין אפליקציה" ו-`applink.ios_*` יורדים. `site/css/app-link.css`: שורת תגים (`flex`, `gap` 12) במצב מחשב.'),
        ('מדידה', '`app_download`: מוסיפים `store: appstore` (ערך חדש, החוזה ב-`docs/GROWTH.md`). אין שינוי בשום דבר אחר.'),
        ('מחרוזות חדשות (לסשן האתר, לא נכנסו לקובץ השפות)', '`home.ios_board`: אפליקציה לאייפון / iPhone app / Приложение для iPhone / აპლიკაცია iPhone-ისთვის. `home.ios_store`: ב-App Store / on the App Store / в App Store / App Store-ზე. `home.app_scan_ios`: סורקים באייפון / Scan with an iPhone / Отсканируйте iPhone / დაასკანერეთ iPhone-ით. `applink.going_ios`: פותחים את App Store… / Opening the App Store… / Открываем App Store… / იხსნება App Store… . לגאורגית ולרוסית: טיוטה, בלי בדיקת דובר (החלטה 39).'),
        ('שלוש הערות', '(1) באייפון, ב-WhatsApp וב-Safari, קישור `apps.apple.com` נפתח ישר באפליקציית החנות. (2) iPad: נחשב אייפון. (3) אין שינוי בדף החנות בגוגל פליי ובשלט שלה.'),
    ]),
]
DECIDE = [
    ('מה ביקשו', 'סשן המנהל, באישור פיני (6.11.2026): האפליקציה לאייפון בחנות (`id6825506333`). שלט ב-App Store ליד השלט של גוגל פליי, כרטיס QR במחשב, והדף `/app` שמוביל לחנות של כל טלפון.'),
    ('ההמלצה', '**א** באייפון שלט "אפליקציה לאייפון · ב-App Store" באותו מקום ובאותה צורה כמו של גוגל פליי; כל טלפון רואה רק את החנות שלו. **ב** במחשב שני השלטים וכרטיס אחד עם שני קודי QR והתגים באותו גובה. **ג** `/app`: אייפון לחנות של אפל, אנדרואיד לגוגל פליי, מחשב שני תגים בלי מעבר אוטומטי.'),
    ('מה לא', 'קוד QR אחד שמפנה לפי הטלפון: נוח, אבל לא נותן הפרדה בין החנויות במדידה, וקוד שנסרק במחשב ללא טלפון בכלל לא עוזר.'),
    ('שאלה 1', 'לאשר את א, ב ו-ג כמו בקנבס?'),
    ('שאלה 2', 'במחשב: שני שלטים (המלצה) או שלט אחד "אפליקציה" שפותח את הכרטיס עם שני התגים?'),
    ('שאלה 3', 'התג של אפל: לאשר ולהוריד את הקובץ הרשמי מ-Apple Marketing Resources (פיני או סשן האתר, אישור תנאים)?'),
    ('אחרי האישור', 'סשן האתר מממש לפי "ערכים למימוש"; הארכיטקט מכריע על פיצול היכולת `app`; בלי שינוי ב-`site/` עד האישור.'),
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
    lead = ('שלט, כרטיס ודף קצר, מצוירים מעל האתר האמיתי, בטלפון ובמחשב, ביום ובלילה, בעברית ובאנגלית, והמלצה אחת.')
    boards = [('R37-0-Decide.dc.html', 'מה לאשר בסבב 37', page('מה לאשר בסבב 37', card('מה לאשר בסבב 37', DECIDE, DW, DH, lead), DW, DH), DW, DH, None)]
    for b in BOARDS:
        c, w, h = board(b, urls)
        boards.append((f'R37-{b[1]}.dc.html', b[2], page(b[2], c, w, h), w, h, b[0]))
    for key, title, w, h, items in TEXT_BOARDS:
        boards.append((f'R37-{key}.dc.html', title, page(title, card(title, items, w, h, accent='#13233A'), w, h), w, h, 'text'))
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
