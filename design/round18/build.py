#!/usr/bin/env python3
"""Round 18: parity between the site and the app, the items in docs/PARITY.md that wait for a look before code
(K-2 to K-5, P-K2, A-28, A-24, the switch when creating a group, and the visual gaps). Requested by the manager
session for Pini (6.10.2026, decision 50 and 57).

Each board: the site today, the app today, and the proposal. The site's proposals are drawn inside the real site
(proposals.mjs), the app's over real emulator screenshots (app.mjs). One canvas page, "סבב 18: יישור קו", with a
card on what to approve. Nothing is built before Pini approves (decision 18).
Run from the repo root:
  python3 design/round18/build.py collect <site shots> <app proposals> <qa shots>   copy what the boards use to shots/
  python3 design/round18/build.py webp                                             to WebP (ImageMagick), cut and scaled
  python3 design/round18/build.py [canvas folder]                                  the boards and canvas.json"""
import html, json, pathlib, shutil, subprocess, sys

here = pathlib.Path(__file__).resolve().parent
root = here.parent.parent
sys.path.insert(0, str(root / 'design/round12'))
from build import page  # the board shell of rounds 12 to 19

PAGE = ('round18', 'סבב 18: יישור קו בין האתר לאפליקציה')
K = 390 / 1080
# name: (folder: s = site shots, p = app proposals, q = qa shots, file, crop top, crop height) in the final 390-wide pixels
SRC = {
    's-flights': ('s', 'site-group-flights.png', 60, 470), 'p-flights': ('s', 'p-k2-flights.png', 60, 860),
    'a-flights': ('q', 'group-17-group-flights.png', 190, 460),
    's-fill': ('s', 'site-group-fill.png', 240, 670), 'p-pick': ('s', 'p-k3-pick.png', 240, 570), 'p-form': ('s', 'p-k3-form.png', 0, 560),
    'a-fill': ('q', 'group-25-group-admin-fill-new.png', 30, 620),
    's-meet': ('s', 't-k3-offline.png', 60, 520), 'p-meet': ('s', 'p-k3-meet.png', 60, 300),
    'a-meet': ('q', 'group-28-group-meetup-delete-sure.png', 200, 410),
    's-create': ('s', 'site-group-create.png', 370, 320), 'p-create': ('s', 'p-s1-create.png', 370, 520),
    'a-create': ('q', 'group-03-group-new.png', 30, 470),
    's-account': ('s', 'site-account-two.png', 0, 600), 'a-account': ('q', 'group-30-account.png', 0, 520), 'p-account': ('p', 'a-k4-account.png', 0, 680),
    's-trip': ('s', 'site-trip-form.png', 0, 460), 'a-trip': ('q', 'home-07-trip-place-sheet.png', 0, 820),
    'p-sheet': ('s', 'p-k5-sheet.png', 200, 644), 'p-code': ('s', 'p-k5-code.png', 520, 324),
    's-dn-day': ('s', 'c-header-day.png', 0, 56), 's-dn-night': ('s', 'c-header-night.png', 0, 56), 's-dn-auto': ('s', 'c-header-auto.png', 0, 56),
    'a-dn-day': ('q', 'home-10-home-trip-day.png', 32, 120), 'a-dn-night': ('q', 'home-22-home-trip-night.png', 32, 120),
    'p-dn-auto': ('p', 'a-pk2-auto.png', 0, 119), 'p-dn-day': ('p', 'a-pk2-day.png', 0, 119), 'p-dn-night': ('p', 'a-pk2-night.png', 0, 119),
    's-map-en': ('s', 'site-map-en.png', 0, 640), 'a-map-en': ('q', 'run-16-run-head-en.png', 0, 600), 'p-fonts': ('p', 'a-a28-fonts.png', 0, 374),
    'a-about': ('q', 'home-34-home-about-3.png', 440, 400),
    'p-lic-about': ('p', 'a-a24-about.png', 0, 485), 'p-lic-list': ('p', 'a-a24-list.png', 0, 844), 'p-lic-text': ('p', 'a-a24-text.png', 0, 700),
    # the visual gaps: the same component on both sides
    'g-s-night': ('s', 'site-home-night.png', 160, 800), 'g-a-night': ('q', 'home-22-home-trip-night.png', 110, 690),
    'g-s-switch': ('s', 'c-switch-day.png', 0, 131), 'g-a-switch': ('q', 'home-31-home-about.png', 340, 290),
    'g-s-tabs': ('s', 'c-tabs-day.png', 0, 58), 'g-a-tabs': ('q', 'group-17-group-flights.png', 246, 52),
    'g-s-gnight': ('s', 'site-group-night.png', 0, 520), 'g-a-gnight': ('q', 'group-31-group-night.png', 40, 470),
    'g-s-invite': ('s', 'c-invite-day.png', 0, 146), 'g-a-invite': ('q', 'group-06-group-invite.png', 30, 760),
}
DOUBLE = {'c-header-day.png', 'c-header-night.png', 'c-header-auto.png', 'c-switch-day.png', 'c-tabs-day.png', 'c-invite-day.png'}  # shot at scale 2
N = lambda f: f + '.webp'

# group, file, title, note, columns: (label, [image or (image, caption)] or a text cell)
BOARDS = [
    ('k2', 'K2', 'K-2 · טיסת החזור בדף הקבוצה', 'באתר יש כרטיס רק לטיסות ההלוך, ובאפליקציה כרטיס גם לחזור, כל אחד עם מי שעליו. ההצעה: כמו באפליקציה, בכרטיס של האתר: כל הטיסות לפי התאריך, הלוך וחזור, ובכל אחת "אני על אותה טיסה".',
     [('באתר היום', ['s-flights']), ('באפליקציה היום', ['a-flights']), ('ההצעה לאתר', ['p-flights'])]),
    ('k3', 'K3a', 'K-3 · מנהל ממלא טיסה לחבר', 'באתר טופס מקוצר (שלושה שדות תעופה, בלי שעת נחיתה ובלי טיסת חזור). ההצעה: כמו באפליקציה, קודם בחירה מהטיסות שכבר בקבוצה, ו"טיסה אחרת" פותחת את הטופס המלא של "הטיול שלך" עם הכותרת "הטיסה של <שם>".',
     [('באתר היום', ['s-fill']), ('באפליקציה היום: "טיסה אחרת"', ['a-fill']), ('ההצעה לאתר: הבחירה', ['p-pick']), ('ואחר כך הטופס המלא', ['p-form'])]),
    ('k3', 'K3b', 'K-3 · מפגשים: מחיקה, נגיעה, "נשמר ב-"', 'שלושה פערים בלשונית המפגשים. מחיקה: באתר נגיעה אחת מוחקת לכולם; ההצעה: שתי נגיעות, כמו באפליקציה. נגיעה בשורה פותחת את כרטיס המפגש (הקישור כבר קיים באתר). ובלי קליטה: הערה רגילה עם השעה, ולא שורת שגיאה אדומה.',
     [('באתר היום (בלי קליטה)', ['s-meet']), ('באפליקציה היום: נגיעה ראשונה ב-X', ['a-meet']), ('ההצעה לאתר', ['p-meet'])]),
    ('k4', 'K4', 'K-4 · "הקבוצות שלי" באפליקציה', 'באתר רשימת הקבוצות בדף החשבון. באפליקציה אין, והשלט "קבוצה" פותח תמיד את הראשונה. ההצעה: הרשימה של האתר בדף החשבון באפליקציה, באותו סדר (לפי תאריך ההתחלה).',
     [('באתר היום', ['s-account']), ('באפליקציה היום', ['a-account']), ('ההצעה לאפליקציה', ['p-account'])]),
    ('k5', 'K5', 'K-5 · שדות התעופה, ושאלה לך', 'באתר רשימה של שלושה ו"אחר" עם קוד של שלוש אותיות. באפליקציה גיליון עם חיפוש, ואפשר גם שם חופשי. ההצעה: הגיליון של האפליקציה גם באתר. והכלל, אותו כלל בשני הצדדים, בכרטיס "מה לאשר".',
     [('באתר היום', ['s-trip']), ('באפליקציה היום', ['a-trip']), ('ההצעה לאתר: הגיליון', ['p-sheet']), ('קוד שלא ברשימה', ['p-code'])]),
    ('s1', 'S1', 'המתג "להציג את הטיסה שלי" ביצירת קבוצה', 'באתר הטיול מצורף תמיד ליצירת קבוצה, בלי תאריכים. באפליקציה תאריכים מהטיול ומתג, דולק, רק כשיש טיול. ההצעה: אותו דבר בטופס של האתר, ברכיב המתג של האתר.',
     [('באתר היום', ['s-create']), ('באפליקציה היום', ['a-create']), ('ההצעה לאתר', ['p-create'])]),
    ('pk2', 'PK2', 'P-K2 · כפתור היום והלילה מראה את המצב', 'באתר הכפתור מראה איזה מצב פעיל: חצי עיגול, שמש או ירח. באפליקציה תמיד אותו חצי עיגול, והמצב רק לקורא מסך. ההצעה: שלושת הסמלים של האתר, בכפתור העגול של האתר.',
     [('באתר היום', ['s-dn-auto', 's-dn-day', 's-dn-night']), ('באפליקציה היום', ['a-dn-day', 'a-dn-night']), ('ההצעה לאפליקציה', ['p-dn-auto', 'p-dn-day', 'p-dn-night'])]),
    ('a28', 'A28', 'A-28 · גופן התוויות באנגלית', 'בתוויות המפה ובתמונת המפגש באנגלית, באתר IBM Plex Sans ובאפליקציה IBM Plex Sans Hebrew. ההצעה: כמו באתר. שינוי קטן מאוד, ולכן רק להשוואה.',
     [('באתר היום', ['s-map-en']), ('באפליקציה היום', ['a-map-en']), ('ההשוואה', ['p-fonts'])]),
    ('a24', 'A24', 'A-24 · מסך רישיונות באפליקציה', 'הרישיונות של הספריות והגופנים (Apache, MIT, OFL) דורשים להציג את הטקסט המלא. הטקסטים כבר ארוזים באפליקציה, ואין מסך שמציג אותם. באתר אין צורך: הקרדיטים מספיקים שם. ההצעה: שורה בסוף הקרדיטים, רשימה, וטקסט.',
     [('באפליקציה היום: הקרדיטים', ['a-about']), ('ההצעה: השורה', ['p-lic-about']), ('הרשימה', ['p-lic-list']), ('הטקסט', ['p-lic-text'])]),
    ('gap', 'Gnight', 'פערי מראה · דף הבית בלילה', 'באתר בלילה הצבעים לפי הטוקנים של הלילה (שלט המפה כחול בהיר, הקבוצה בהירה עם טקסט כהה), ובאפליקציה צבעי היום. גם השלג על הכרטיס והכותרת שונים. ההצעה: האפליקציה מתיישרת לאתר.',
     [('באתר', ['g-s-night']), ('באפליקציה', ['g-a-night'])]),
    ('gap', 'Gparts', 'פערי מראה · מתג ולשוניות', 'המתג: באתר 52 על 30, אפור וירוק, "דולק" בסוף השורה; באפליקציה 46 על 28 עם מסגרת, כחול. הלשוניות: באתר ברוחב התוכן עם קו מתחת לפעילה; באפליקציה ברוחב שווה, הפעילה מלאה. ההצעה: כמו באתר.',
     [('באתר', ['g-s-switch', 'g-s-tabs']), ('באפליקציה', ['g-a-switch', 'g-a-tabs'])]),
    ('gap', 'Ggroup', 'פערי מראה · הקבוצה בלילה', 'באתר הכרטיסים בלילה כחולים וכהים עם טקסט בהיר; באפליקציה כרטיס טיסה עם ספח ומונה "על הטיסה", וזהוב. כרטיס הטיסה של האפליקציה עשיר יותר; ההצעה כאן היא רק לצבעים ולגופנים, לפי האתר.',
     [('באתר', ['g-s-gnight']), ('באפליקציה', ['g-a-gnight'])]),
    ('gap', 'Ginvite', 'פערי מראה · כרטיס ההזמנה, בכיוון ההפוך', 'כאן ההמלצה הפוכה: מסך ההזמנה של האפליקציה (Q2 מסבב 10, שאושר) עשיר וברור יותר, עם הקוד בתיבות, הקישור, וואטסאפ ושיתוף. ההצעה: האתר מתיישר לאפליקציה.',
     [('באתר', ['g-s-invite']), ('באפליקציה', ['g-a-invite'])]),
]
ROWS = [('k2', 'K-2 · טיסת החזור'), ('k3', 'K-3 · דף הקבוצה באתר'), ('k4', 'K-4 · הקבוצות שלי'), ('k5', 'K-5 · שדות התעופה'),
        ('s1', 'יצירת קבוצה: המתג'), ('pk2', 'P-K2 · יום ולילה'), ('a28', 'A-28 · גופן באנגלית'), ('a24', 'A-24 · רישיונות'),
        ('gap', 'פערי המראה: האפליקציה מתיישרת לאתר (חוץ מההזמנה)')]
DECIDE = [
    ('K-2 · טיסת החזור באתר', 'כרטיס לכל טיסה, הלוך וחזור, לפי התאריך, כל אחד עם מי שעליו ו"אני על אותה טיסה". בכרטיס של האתר.'),
    ('K-3 · מילוי טיסה לחבר באתר', 'קודם בחירה מהטיסות שכבר בקבוצה; "טיסה אחרת" פותחת את הטופס המלא של "הטיול שלך", "הטיסה של <שם>".'),
    ('K-3 · מפגשים באתר', 'מחיקה בשתי נגיעות ("בטוח? לחיצה נוספת מוחקת"), נגיעה בשורה פותחת את כרטיס המפגש, ובלי קליטה הערה עם השעה במקום שגיאה אדומה.'),
    ('K-4 · "הקבוצות שלי" באפליקציה', 'הרשימה של האתר בדף החשבון, לפי תאריך ההתחלה.'),
    ('K-5 · שדות התעופה באתר', 'הגיליון עם החיפוש של האפליקציה.'),
    ('K-5 · שאלה: מה מותר להקליד', '**ההמלצה שלי: מהרשימה, או קוד של שלוש אותיות שלא ברשימה, בלי שם חופשי, ואותו כלל בשני הצדדים.** הכרטיס בדף הבית מציג את השדה באותיות ענק, ושם חופשי ("ברלין שנפלד") שובר אותו ואת הקיבוץ של "מי טס יחד". מי שטס משדה נדיר מקליד את הקוד מהכרטיס שלו. המחיר: באפליקציה נעלם השם החופשי שקיים היום. החלופה: שם חופשי בשני הצדדים, והכרטיס מקצר אותו.'),
    ('יצירת קבוצה באתר', 'תאריכים מהטיול, ומתג "להציג את הטיסה שלי בקבוצה", דולק, רק כשיש טיול. ברכיב המתג של האתר.'),
    ('P-K2 · יום ולילה באפליקציה', 'הכפתור מראה את המצב: חצי עיגול, שמש או ירח, בכפתור העגול של האתר.'),
    ('A-28 · גופן באנגלית באפליקציה', 'IBM Plex Sans בתוויות המפה ובתמונת המפגש באנגלית, כמו באתר.'),
    ('A-24 · רישיונות באפליקציה', 'שורה "רישיונות קוד פתוח" בסוף הקרדיטים, רשימה, והטקסט המלא. באתר לא נדרש.'),
    ('פערי המראה', 'האפליקציה מתיישרת לאתר של היום: הלילה, המתג, הלשוניות, השלג, השלט וכותרת דף הבית. **חוץ מכרטיס ההזמנה, שבו האתר מתיישר לאפליקציה.** '
     'אם תבחר שפה עיצובית חדשה (הקנבס הנפרד), היא מחליפה את שניהם, וכל השורה הזו נדחית אליה.'),
    ('מי מממש', 'אחרי האישור: האתר אצל סשן האתר, האפליקציה אצל סשן האנדרואיד, ושומר היישור מחלק ובודק בשני הצדדים. שום דבר לא נבנה לפני שתאשר.'),
]
GAP, PAD, HEAD, CGAP = 18, 20, 120, 28
DH = 1380


def collect(site, props, qa):
    out = here / 'shots'
    out.mkdir(exist_ok=True)
    dirs = {'s': pathlib.Path(site), 'p': pathlib.Path(props), 'q': pathlib.Path(qa)}
    for name, (d, f, *_) in SRC.items():
        shutil.copy(dirs[d] / f, out / (name + '.png'))
    return len(SRC)


def webp():
    src, out = here / 'shots', here / 'shots/web'
    out.mkdir(exist_ok=True)
    for name, (d, f, top, h) in SRC.items():
        args = ['convert', str(src / (name + '.png'))]
        if d == 'q' or f in DOUBLE:
            args += ['-resize', '390x']
        if h:
            args += ['-crop', f'390x{h}+0+{top}', '+repage']
        subprocess.run(args + ['-quality', '90', str(out / N(name))], check=True)
    return len(SRC)


def size(f):
    w, h = subprocess.check_output(['identify', '-format', '%w %h', str(here / 'shots/web' / N(f))]).split()
    return int(w), int(h)


def board(b, urls):
    group, key, title, note, cols = b
    colw, cells = [], []
    for label, ims in cols:
        parts = [(f'<img src="{urls[N(i)]}" alt="{html.escape(label)}" style="display: block; width: {size(i)[0]}px; height: {size(i)[1]}px; box-shadow: 0 0 0 1px #CBD5DF">', *size(i)) for i in ims]
        w, h = max(p[1] for p in parts), sum(p[2] for p in parts) + GAP * (len(parts) - 1) + 34
        cells.append((f'<div style="display: flex; flex-direction: column; gap: {GAP}px; width: {w}px">'
                      f'<b style="height: 16px; font-size: 15px; color: #1F5FC4">{html.escape(label)}</b>{"".join(p[0] for p in parts)}</div>', w, h))
    w = sum(c[1] for c in cells) + CGAP * (len(cells) - 1) + 2 * PAD
    h = PAD + HEAD + max(c[2] for c in cells) + PAD
    head = (f'<div style="display: flex; flex-direction: column; gap: 4px; height: {HEAD - 12}px; max-width: {max(w - 2 * PAD, 600)}px">'
            f"<b style=\"font-family: Karantina, 'Arial Narrow', sans-serif; font-size: 34px; line-height: 1\">{html.escape(title)}</b>"
            f'<span style="font-size: 14px; line-height: 1.45; color: #4B5A6F">{html.escape(note)}</span></div>')
    body = f'<div style="display: flex; gap: {CGAP}px; align-items: flex-start">{"".join(c[0] for c in cells)}</div>'
    card = (f'<div style="width: {w}px; height: {h}px; box-sizing: border-box; padding: {PAD}px; display: flex; flex-direction: column; gap: 12px; '
            f"background: #FFFFFF; color: #13233A; font-family: 'IBM Plex Sans Hebrew', system-ui, sans-serif; direction: rtl\">{head}{body}</div>")
    return card, w, h


def decide_card():
    md = lambda s: ''.join(f'<b style="color: #13233A">{p}</b>' if i % 2 else p for i, p in enumerate(s.split('**')))
    items = ''.join(f'<li style="display: flex; flex-direction: column; gap: 2px; padding: 9px 0; border-top: 1px solid #CBD5DF">'
                    f'<b style="font-size: 16px">{i + 1}. {html.escape(t)}</b><span style="font-size: 14px; line-height: 1.55; color: #4B5A6F">{md(html.escape(s))}</span></li>'
                    for i, (t, s) in enumerate(DECIDE))
    return (f'<div style="width: 600px; height: {DH}px; box-sizing: border-box; padding: 28px; background: #FFFFFF; color: #13233A; '
            f"font-family: 'IBM Plex Sans Hebrew', system-ui, sans-serif; direction: rtl; border-top: 8px solid #1F5FC4\">"
            f"<h1 style=\"margin: 0; font-family: Karantina, 'Arial Narrow', sans-serif; font-size: 52px; line-height: 1\">מה לאשר בסבב 18</h1>"
            f'<p style="margin: 8px 0 14px; font-size: 14.5px; line-height: 1.55; color: #4B5A6F">ביקשת יישור מלא בין האתר לאפליקציה (החלטות 50 ו-57). '
            f'כאן כל מה שמחכה למבט שלך לפני קוד: לכל פריט, מה יש היום בכל צד ומה מוצע. באתר ההצעות מצוירות בתוך האתר האמיתי, ובאפליקציה מעל צילומים מהאמולטור. '
            f'השמות מומצאים. ברירת המחדל: הצד שחסר מתיישר לצד שיש לו, ובמראה האפליקציה מתיישרת לאתר.</p>'
            f'<ol style="margin: 0; padding: 0; list-style: none">{items}</ol></div>')


def build(dst):
    urls = json.loads((here / 'shots/assets.json').read_text(encoding='utf-8'))
    boards = [('R18-0-Decide.dc.html', 'מה לאשר בסבב 18', page('מה לאשר בסבב 18', decide_card(), 600, DH), 600, DH, None)]
    for b in BOARDS:
        card, w, h = board(b, urls)
        boards.append((f'R18-{b[1]}.dc.html', b[2], page(b[2], card, w, h), w, h, b[0]))
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
    index['boards'][boards[0][0]] = {'x': 0, 'y': 0, 'w': 600, 'h': DH, 'page': PAGE[0], 'title': boards[0][1]}
    if boards[0][0] not in index['order']:
        index['order'].append(boards[0][0])
    y = 0
    for ri, (group, title) in enumerate(ROWS):
        x, rowh = 680, 0
        for f, t, _, w, h, g in boards:
            if g != group:
                continue
            index['boards'][f] = {'x': x, 'y': y, 'w': w, 'h': h, 'page': PAGE[0], 'title': t}
            if f not in index['order']:
                index['order'].append(f)
            x += w + 80
            rowh = max(rowh, h)
        index['notes'][f'{PAGE[0]}row{ri}'] = {'kind': 'title1', 'maxW': max(860, x - 760), 'page': PAGE[0], 'text': title, 'w': 240, 'x': 680, 'y': y - 300}
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
