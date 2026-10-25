#!/usr/bin/env python3
"""Round 27: a generic name, title, description and share image for when the site opens to Google
(Pini, 25.10.2026: "generic for everyone, not aimed at our trip"). Texts in copy.json; the share images from og.py;
the Google and WhatsApp previews from mocks.mjs; the top bar over the real site from shots.mjs. Run from the repo root:
  python3 design/round27/og.py && node design/round27/mocks.mjs && node design/round27/shots.mjs
  python3 design/round27/build.py webp
  python3 design/round27/build.py [canvas project folder]"""
import html, json, re, pathlib, subprocess, sys

here = pathlib.Path(__file__).resolve().parent
root = here.parent.parent
sys.path.insert(0, str(root / 'design/round12'))
from build import page  # the board shell of rounds 12 to 26

PAGE = ('round27', 'סבב 27: שם ותמונת שיתוף לכולם')
C = json.loads((here / 'copy.json').read_text(encoding='utf-8'))
SRC = {n: ('shots/' + n + '.png', w, 0, 0) for n, w in [
    ('g-today-he', 600), ('g-today-en', 600), ('g-he', 600), ('g-en', 600), ('g-ru', 600), ('g-ka', 600), ('g-he-ph', 392), ('g-en-ph', 392),
    ('w-today-he', 390), ('w-today-he-sm', 390), ('w-he', 390), ('w-en', 390), ('w-he-sm', 390), ('w-en-sm', 390), ('w-he-b', 390), ('w-he-b-sm', 390),
    ('og-a', 600), ('og-a-sq', 300), ('og-b', 600), ('og-b-sq', 300)]}
SRC.update({n: ('shots/' + n + '.png', 900, 0, 300) for n in ['today-desk', 'today-desk-en', 'a-desk', 'a-desk-en', 'b-desk']})
N = lambda f: f + '.webp'
BOARDS = [
    ('today', 'Today', 'היום: מכוון לטיול שלנו', 'מה שגוגל ומי שמקבל קישור רואים היום: "גודאורי 2027: אתר ההיערכות", תיאור של "חופשת סקי", ותמונה עם 10 עד 15 בינואר 2027. במחשב השם בסרגל הוא "גודאורי 2027", ומתחת לנוף "חופשת סקי, ינואר 2027". בטלפון אין סרגל, והשורה הזו מוסתרת.',
     [('בגוגל', ['g-today-he', 'g-today-en']), ('בוואטסאפ', ['w-today-he']), ('הסרגל במחשב', ['today-desk', 'today-desk-en'])]),
    ('a', 'Google', 'א. בגוגל', 'הכותרת אומרת מה זה ועל איזה הר, במילים שאנשים מחפשים ("מפת סקי", "מסלולים", "רכבלים"), ונגמרת ב-Gudi, השם בחנות. מעליה שם האתר, Gudi. התיאור אומר מה יש בפנים, רק מה שבאמת קיים היום (מצב הרכבלים עוד בלי נתון, ולכן לא כתוב). נבדק שהכותרת נכנסת בשורה אחת במחשב (600 פיקסלים בגופן של גוגל) והתיאור בשתי שורות, בכל ארבע השפות. הסמל הוא הסמל של האתר היום.',
     [('עברית ואנגלית, במחשב', ['g-he', 'g-en']), ('רוסית וגאורגית', ['g-ru', 'g-ka']), ('בטלפון', ['g-he-ph', 'g-en-ph'])]),
    ('a', 'WhatsApp', 'א. בוואטסאפ', 'התמונה בתצוגה הגדולה, והכותרת והתיאור הקצר מתחתיה. בתצוגה הקטנה וואטסאפ חותך ריבוע מהאמצע, ולכן השלט באמצע התמונה: הוא נשאר שלם גם שם.',
     [('עברית', ['w-he']), ('English', ['w-en']), ('התצוגה הקטנה', ['w-he-sm', 'w-en-sm'])]),
    ('a', 'Image', 'א. תמונת השיתוף: העולם של החנות (מומלץ)', 'אותו עולם כמו התמונה הראשית בגוגל פליי שאושרה (סבב 9): השמיים, הרכס, היער והשלט המושלג עם GUDAURI, בגודל 1200 על 630. בלי תאריכים ובלי טקסט נוסף: הכותרת והתיאור כבר מופיעים ליד התמונה. שם המקום באותיות לטיניות, כמו בשלטים בהר, ולכן תמונה אחת לכל השפות. מימין: הריבוע שוואטסאפ חותך.',
     [('1200 על 630', ['og-a']), ('הריבוע', ['og-a-sq'])]),
    ('a', 'Brand', 'א. הסרגל: שם המקום', 'במחשב הסרגל אומר "גודאורי" (בכל שפה בשפה שלה), ושורת "חופשת סקי, ינואר 2027" יורדת: "גודאורי, גאורגיה" כבר כתוב שם. כך שם המקום הוא גם הכפתור של בחירת האתר כשיחזרו אתרים נוספים (סבב 23). השם Gudi נשאר בכותרת, בחנות ובסמל. בטלפון אין שינוי.',
     [('עברית', ['a-desk']), ('English', ['a-desk-en'])]),
    ('b', 'ImageB', 'ב. תמונת השיתוף: הנוף האמיתי מהכפר', 'הנוף של דף הבית, שמרונדר ממודל הגובה, עם אותו שלט. נאמן להר, אבל לבן על לבן ופחות קריא בתצוגה הקטנה, ולא דומה לחנות. לא מומלץ.',
     [('1200 על 630', ['og-b']), ('בוואטסאפ', ['w-he-b', 'w-he-b-sm'])]),
    ('b', 'BrandB', 'ב. הסרגל: Gudi', 'השם של האפליקציה בסרגל במקום שם המקום. מחזק את המותג, אבל כפתור בחירת האתר צריך אז מקום אחר, ובטלפון שם המקום כבר בכותרת. לא מומלץ.',
     [('עברית', ['b-desk'])]),
]
ROWS = [('today', 'היום'), ('copy', 'הטקסטים בארבע השפות'), ('a', 'א. ההמלצה'), ('b', 'ב. חלופות'), ('text', 'ערכים למימוש')]
L = {'he': 'עברית', 'en': 'English', 'ru': 'Русский', 'ka': 'ქართული'}
COPY_ITEMS = []
for k, name in [('title', 'כותרת (title, og:title)'), ('description', 'תיאור לגוגל (description)'), ('og_description', 'תיאור לשיתוף (og:description)')]:
    COPY_ITEMS.append((name, '\n'.join('**%s** (%d תווים) ⟦%s⟧' % (L[l], len(C['new'][l][k]), C['new'][l][k]) for l in L)))
COPY_ITEMS.append(('שם האתר (og:site_name, ושם האתר בגוגל)', '**Gudi** בכל השפות, כמו בחנות ובסמל.'))
COPY_ITEMS.append(('השם בסרגל (common.site_name)', ' · '.join('%s ⟦%s⟧' % (L[l], C['new'][l]['brand']) for l in L) + '. השורה "חופשת סקי, ינואר 2027" (`home.when`) יורדת.'))
TEXT_BOARDS = [
    ('Copy', 'הטקסטים בארבע השפות', 760, 900, COPY_ITEMS, 'copy'),
    ('Spec', 'ערכים למימוש', 640, 940, [
        ('שני מפתחות במקום אחד', 'היום `common.site_name` הוא גם `og:site_name` וגם השם בסרגל. צריך מפתח חדש לשם האתר (`meta.site_name`: Gudi), ו-`common.site_name` נשאר לסרגל עם שם המקום. גם `site/privacy.html` (`pv-brand`) לפי הסרגל.'),
        ('תמונת השיתוף', '`site/og.jpg` מחדש מ-`design/round27/og.py` (1200 על 630, JPEG באיכות 85, פחות מ-200KB). `og:image:alt` חדש: "שלט GUDAURI מושלג מול ההרים". הקובץ הישן `design/og/og.html` יוצא משימוש.'),
        ('עוד מקומות שמכוונים לטיול', '`run.share_title` "גודאורי 2027: {run}" → "{run} · גודאורי". `meet.card_sub` "גודאורי 2027" → "גודאורי". `about.pass_top` "SKI PASS · גודאורי 2027" → "SKI PASS · Gudi". נשארים: `about.bio` (הסיפור של פיני, נכון כמו שהוא), "הירידה של החבר׳ה" (שם המשחק), `group.create_name_ph` (דוגמה לשם קבוצה).'),
        ('השפה של התצוגה בוואטסאפ', 'וואטסאפ קורא את הדף בלי להריץ קוד, ולכן רואה את מה שכתוב בדף עצמו, היום בעברית, לכל מי שמשתף. גוגל כן מריץ את הקוד, ובלי עברית בדפדפן שלו יראה אנגלית (החלטה 48). ההמלצה: הדף נשאר בעברית (רוב מי שמשתף בוואטסאפ ישראלים), והתמונה בלי טקסט בשפה.'),
        ('לגוגל, לפני הפתיחה (סשן המנהל והארכיטקט)', 'קישורי `hreflang` לכל שפה (`?lang=he` וכו׳, שכבר עובד), `canonical`, `og:locale:alternate`, ו-`robots.txt` ו-`sitemap.xml`. ושם האתר בגוגל לפי הנתונים המובנים של `WebSite` (`name: Gudi`).'),
        ('הסמל בלשונית ובגוגל', '`site/favicon.svg` הוא עדיין השלט הכחול הישן, ובגוגל הוא מופיע ליד כל תוצאה. כבר רשום ב-`docs/ROADMAP.md` כהחלטה של פיני: אותו סמל כמו באפליקציה? מומלץ כן, יחד עם הפתיחה לגוגל.'),
        ('מחרוזות', 'הנוסחים בלוח "הטקסטים בארבע השפות", לסשן המנהל, שמכניס ל-`i18n/strings.json`. הרוסית והגאורגית בלי בדיקת דובר (החלטה 39).'),
    ], 'text'),
]
DECIDE = [
    ('מה ביקשו', 'סשן המנהל, לבקשת פיני (25.10.2026, לפני הפתיחה לגוגל): "זה צריך להיות גנרי לכולם, לא משהו שמוכוון לטיול שלנו".'),
    ('ההמלצה', '**כותרת** "מפת הסקי של גודאורי: מסלולים, רכבלים ותלת-ממד | Gudi", ותיאור של מה שבאמת יש בפנים, בארבע השפות. **שם האתר** Gudi, כמו בחנות. **תמונת השיתוף** בעולם של התמונה הראשית בגוגל פליי, השלט GUDAURI באמצע, בלי תאריכים. **בסרגל** שם המקום, "גודאורי", ובלי "חופשת סקי, ינואר 2027".'),
    ('מה לא', '**ב** הנוף האמיתי בתמונה (לבן על לבן ופחות קריא קטן), ו-Gudi בסרגל (שם המקום הוא כפתור בחירת האתר).'),
    ('שאלה 1', 'לאשר את הכותרת, התיאור ושם האתר בארבע השפות?'),
    ('שאלה 2', 'לאשר את תמונת השיתוף א?'),
    ('שאלה 3', 'בסרגל ובדף הבית: גם שם להחליף את "גודאורי 2027" בשם המקום, "גודאורי" (ההמלצה), או ב-Gudi?'),
    ('שאלה 4', 'התצוגה בוואטסאפ: בעברית לכולם, כמו היום (ההמלצה), או באנגלית?'),
    ('אחרי האישור', 'סשן המנהל מממש לפי "ערכים למימוש". שום שינוי ב-`site/` עד האישור.'),
]
GAP, PAD, HEAD, CGAP, DW, DH = 18, 20, 150, 28, 600, 920

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
    lead = ('מה שגוגל ומי שמקבל קישור יראו, גנרי לכל גולש בגודאורי ולא לטיול שלנו: הכותרת, התיאור והשם בארבע השפות, תמונת השיתוף, ותצוגה של תוצאת חיפוש ושל שיתוף בוואטסאפ, בעברית ובאנגלית.')
    boards = [('R27-0-Decide.dc.html', 'מה לאשר בסבב 27', page('מה לאשר בסבב 27', card('מה לאשר בסבב 27', DECIDE, DW, DH, lead), DW, DH), DW, DH, None)]
    for b in BOARDS:
        c, w, h = board(b, urls)
        boards.append((f'R27-{b[1]}.dc.html', b[2], page(b[2], c, w, h), w, h, b[0]))
    for key, title, w, h, items, g in TEXT_BOARDS:
        boards.append((f'R27-{key}.dc.html', title, page(title, card(title, items, w, h, accent='#13233A'), w, h), w, h, g))
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
