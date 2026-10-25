#!/usr/bin/env python3
"""Round 28: a short link to the app (/app) that shows a preview in WhatsApp. A Google Play link shows none, because
WhatsApp reads only the first 300KB of a page and Play's preview tags come after about 1MB. Texts in copy.json; the page
prototype in page.html; the renders from mocks.mjs. Run from the repo root:
  node design/round28/mocks.mjs
  python3 design/round28/build.py webp
  python3 design/round28/build.py [canvas project folder]"""
import html, json, re, pathlib, subprocess, sys

here = pathlib.Path(__file__).resolve().parent
root = here.parent.parent
sys.path.insert(0, str(root / 'design/round12'))
from build import page  # the board shell of rounds 12 to 27

PAGE = ('round28', 'סבב 28: קישור לאפליקציה עם תצוגה בוואטסאפ')
SRC = {n: ('shots/' + n + '.png', w, 0, 0) for n, w in [
    ('today-wa', 745), ('wa-b', 390), ('wa-b-sm', 390), ('wa-b-dark', 520), ('wa-a', 390), ('wa-a-sm', 390), ('wa-a-dark', 520),
    ('page-he', 390), ('page-he-dark', 390), ('page-en', 390), ('page-ka', 390), ('page-ios', 390), ('page-ios-dark', 390)]}
N = lambda f: f + '.webp'
BOARDS = [
    ('today', 'Today', 'היום: קישור לחנות בלי תמונה', 'צילום המסך של פיני: שני קישורים לגוגל פליי, ובשניהם וואטסאפ מציג רק "play.google.com". לפי התיעוד של וואטסאפ, הוא קורא רק את 300 הקילובייט הראשונים של הדף, ותגיות התצוגה חייבות להיות בתוכם. בדף של גוגל פליי (כמגה-בייט) הן מופיעות רק אחרי כמגה-בייט אחד. זה קורה לכל אפליקציה בגוגל פליי, ואי אפשר לשנות את הדף של החנות.',
     [('וואטסאפ במחשב, 25.10.2026', ['today-wa'])]),
    ('b', 'WhatsAppB', 'ההצעה: הקישור הקצר, עם תמונת השיתוף של האתר (מומלץ)', 'משתפים את gudauri-ski-trip.vercel.app/app. הדף קטן מאוד ומתחיל בתגיות התצוגה, ולכן וואטסאפ מציג את התמונה, השם בעברית כמו בחנות ושורה על מה שיש באפליקציה. התמונה היא תמונת השיתוף שאושרה בסבב 27 (שעת זהב, השלט באמצע), כך שהשלט נשאר שלם גם בריבוע של התצוגה הקטנה, ואין קובץ חדש. נגיעה בקישור פותחת את גוגל פליי.',
     [('בטלפון', ['wa-b']), ('התצוגה הקטנה', ['wa-b-sm']), ('במחשב, בלילה', ['wa-b-dark'])]),
    ('a', 'WhatsAppA', 'חלופה: התמונה הראשית מהחנות', 'אותו קישור, עם התמונה הראשית של גוגל פליי (סבב 9). מזהה את החנות, אבל השלט בה בצד ימין, ובתצוגה הקטנה וואטסאפ חותך ריבוע מהאמצע וחותך את GUDAURI. לא מומלץ.',
     [('בטלפון', ['wa-a']), ('התצוגה הקטנה', ['wa-a-sm']), ('במחשב, בלילה', ['wa-a-dark'])]),
    ('page', 'Page', 'הדף שנפתח לרגע: באנדרואיד', 'מי שנוגע בקישור רואה את הדף הזה לחלק משנייה, ואז גוגל פליי נפתח לבד. אם משהו עוצר (חוסם, דפדפן בלי סקריפטים), התג הרשמי של גוגל פליי והקישור לאתר שם. בכל ארבע השפות, לפי השפה של הטלפון, וביום ובלילה כמו האתר. במחשב: ישר לדף של האפליקציה בגוגל פליי, שמשם מתקינים לטלפון.',
     [('עברית', ['page-he']), ('בלילה', ['page-he-dark']), ('English', ['page-en']), ('ქართული', ['page-ka'])]),
    ('page', 'PageIos', 'הדף שנפתח: באייפון', 'באייפון אין עדיין אפליקציה בחנות, ולכן הדף לא שולח לגוגל פליי: הוא אומר את זה בשורה אחת, ומציע את המפה באתר. כשהאפליקציה תהיה ב-App Store, אותו קישור ישלח כל טלפון לחנות שלו, וכל הקישורים ששותפו ימשיכו לעבוד.',
     [('ביום', ['page-ios']), ('בלילה', ['page-ios-dark'])]),
]
ROWS = [('today', 'היום'), ('b', 'ההצעה'), ('page', 'הדף עצמו'), ('a', 'חלופה'), ('text', 'ערכים למימוש')]
C = json.loads((here / 'copy.json').read_text(encoding='utf-8'))
L = {'he': 'עברית', 'en': 'English', 'ru': 'Русский', 'ka': 'ქართული'}
TEXT_BOARDS = [
    ('Spec', 'ערכים למימוש', 680, 1280, [
        ('הכתובת', '`https://gudauri-ski-trip.vercel.app/app`, קובץ חדש `site/app.html` (`cleanUrls` כבר קיים). לא במפת האתר, ועם `noindex`, כי הוא רק מעביר.'),
        ('התגיות (בעברית, בראש הדף)', '`og:title` ⟦%s⟧ · `og:description` ⟦%s⟧ · `og:site_name` Gudi · `og:image` `og.jpg?v=27` (התמונה מסבב 27) · `og:image:alt` ⟦%s⟧. וואטסאפ לא מריץ קוד, ולכן רואה את מה שכתוב בדף, בעברית לכל משתף, כמו בדף הבית (החלטה 73).' % (C['share']['title'], C['share']['description'], C['share']['image_alt'])),
        ('ההעברה', 'סקריפט קטן מהאתר עצמו (`site/js/app-link.js`, בגלל מדיניות אבטחת התוכן, בלי קוד בתוך הדף): באנדרואיד ובמחשב `location.replace` לגוגל פליי; באייפון ובאייפד מצב האייפון. לא הפניה של השרת: עם הפניה, וואטסאפ היה עובר לדף של גוגל ונכשל שוב.'),
        ('מקור ההתקנה', 'הקישור לחנות נושא `referrer` עם `utm_source` מהכתובת (`/app?s=whatsapp`), וברירת מחדל `share`, ו-`utm_medium=app_link`. כך ההתקנות מקישור ששותף נספרות באפליקציה בנפרד מהשלט בדף הבית (`home_board`, `home_qr`).'),
        ('מדידה', 'בלי PostHog ובלי Sentry בדף הזה: הוא מוצג לחלק משנייה. הספירה דרך מקור ההתקנה באפליקציה (`docs/GROWTH.md`).'),
        ('טקסטים', 'מפתחות חדשים `applink.*` בארבע השפות, דרך `i18n/strings.json` ו-`T()`. השפה לפי הכללים של האתר (החלטה 48). הנוסחים בלוח "הטקסטים".'),
        ('בדיקה', '`tests/applink.spec.ts`: התגיות בראש הדף ובתוך 300 הקילובייט הראשונים, היעד של ההעברה באנדרואיד (עם `referrer`), מצב האייפון, ואפס שגיאות בקונסולה.'),
        ('בלי שינוי', 'השלט "אפליקציה לאנדרואיד" וקוד ה-QR בדף הבית ממשיכים לגוגל פליי ישירות. הקישור הקצר הוא לשיתוף.'),
    ], 'text'),
    ('Copy', 'הטקסטים בארבע השפות', 760, 760, [(name, '\n'.join('**%s** ⟦%s⟧' % (L[l], C['page'][l][k]) for l in L)) for k, name in [
        ('sub', 'מתחת לשם'), ('going', 'בזמן ההעברה'), ('site', 'קישור לאתר'), ('ios_title', 'אייפון: כותרת'), ('ios_text', 'אייפון: שורה'), ('ios_btn', 'אייפון: כפתור')]], 'text'),
]
DECIDE = [
    ('הבעיה', 'קישור לגוגל פליי בוואטסאפ מופיע בלי תמונה ובלי שם: וואטסאפ קורא רק את 300 הקילובייט הראשונים של דף, ובדף של החנות תגיות התצוגה מופיעות רק אחרי כמגה-בייט. זה לא תלוי בנו.'),
    ('ההצעה', 'קישור קצר משלנו, **gudauri-ski-trip.vercel.app/app**, שמציג בוואטסאפ את תמונת השיתוף של האתר, את השם ושורה על האפליקציה, ונגיעה בו פותחת את גוגל פליי.'),
    ('באייפון', 'עד שהאפליקציה תהיה ב-App Store, הדף אומר שאין עדיין אפליקציה ומציע את המפה באתר. אחר כך אותו קישור ישלח לחנות של כל טלפון.'),
    ('התמונה', '**תמונת השיתוף של האתר (מומלץ)**, שהשלט בה באמצע; או התמונה הראשית מהחנות, שבתצוגה הקטנה נחתכת.'),
    ('מה לא משתנה', 'השלט בדף הבית וקוד ה-QR ממשיכים ישר לחנות.'),
    ('שאלה', 'לאשר את הקישור הקצר, את הדף (אנדרואיד ואייפון) ואת התמונה?'),
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
    s = ''.join(f'<b style="color: #13233A">{p}</b>' if i % 2 else p for i, p in enumerate(html.escape(s).split('**')))
    s = re.sub(r'⟦([^⟧]+)⟧', r'<bdi dir="auto" style="color: #13233A">\1</bdi>', s).replace('\n', '<br>')
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
    lead = ('פיני: "שיתפתי לינק, למה אני לא רואה את התמונה של האפליקציה בשיתוף בוואצאפ?" קישור קצר שמציג תצוגה מלאה ומעביר לחנות.')
    boards = [('R28-0-Decide.dc.html', 'מה לאשר בסבב 28', page('מה לאשר בסבב 28', card('מה לאשר בסבב 28', DECIDE, DW, DH, lead), DW, DH), DW, DH, None)]
    for b in BOARDS:
        c, w, h = board(b, urls)
        boards.append((f'R28-{b[1]}.dc.html', b[2], page(b[2], c, w, h), w, h, b[0]))
    for key, title, w, h, items, g in TEXT_BOARDS:
        boards.append((f'R28-{key}.dc.html', title, page(title, card(title, items, w, h, accent='#13233A'), w, h), w, h, g))
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
