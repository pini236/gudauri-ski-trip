#!/usr/bin/env python3
"""Round 19: weather and wind by altitude, and where am I, for the site and Android together (decision 57,
docs/FEATURES.md sections c and d; the technical review: docs/ARCHITECTURE.md, W-6 and W-7, waiting for Pini).

The site's proposals are drawn inside the real site (proto.css, proto.js, shots.mjs); the app's over real emulator
screenshots (android.mjs). The shots are uploaded to the canvas as assets (shots/assets.json maps each file to its url).
One canvas page, "סבב 19: מזג אוויר ומיקום", with a card on what to approve. Nothing is built before Pini approves (decision 18).
Run from the repo root, after shots.mjs and android.mjs:
  python3 design/round19/build.py webp             the shots to WebP (ImageMagick), cropped where a page is long
  python3 design/round19/build.py [canvas folder]  the boards and canvas.json (default: design/canvas/project)"""
import html, json, pathlib, subprocess, sys

here = pathlib.Path(__file__).resolve().parent
root = here.parent.parent
sys.path.insert(0, str(root / 'design/round12'))
from build import page  # the board shell of rounds 12 to 16

PAGE = ('round19', 'סבב 19: מזג אוויר ומיקום')
CROP = {'home-today': 1110, 'home-today-live-night': 1110, 'home-today-en': 1110, 'home-ahead': 1110, 'run-cond': 1260, 'desk-home': 1170}
SAMPLE = 'מספרי מזג האוויר הם נתוני דוגמה. הגבהים, המסלולים והתחנות אמיתיים.'

# group, file (without .webp), title, note, extra images under it: (file, crop top, height) or a list of state crops
BOARDS = [
    ('weather', 'map-weather', 'השכבה במפה: עדכנית', 'הכפתור "מזג אוויר" בשורת הסינון, כבוי עד שנוגעים. שלוש הנקודות בגובה שלהן ממודל הגובה. אותו מידע ברשימה מתחת למפה, לקורא מסך ולמקרה שהנקודות מתנגשות.', ['map-weather-list']),
    ('weather', 'map-weather-stale', 'תחזית ישנה (12 עד 48 שעות)', 'אותם מספרים, עם "תחזית ישנה" על כל נקודה ובראש הרשימה. כנראה בלי קליטה: כל מכשיר שומר את התשובה האחרונה.', ['map-weather-stale-list']),
    ('weather', 'map-weather-none', 'אין מידע', 'מעל 48 שעות, או שעוד לא הגיעה תחזית: לא מנחשים. הנקודות נשארות עם קו.', ['map-weather-none-list']),
    ('weather', 'map-weather-night', 'בלילה', 'הכרטיסים בצבעי הלילה של האתר, עם הדיו הבהיר.', []),
    ('weather', 'and-map-buttons', 'אנדרואיד: איפה הכפתור', 'במפה של האפליקציה אין שורת סינון, ולכן "מזג אוויר" ליד "כל המסלולים", ו"איפה אני" בצד השני. מעל צילום אמיתי מהאמולטור.', []),
    ('weather', 'and-map-weather', 'אנדרואיד: השכבה', 'אותה רשימה ואותן מחרוזות, בגיליון מלמטה. בתלת-ממד הנקודות יושבות על ההר כמו תוויות הפסגות (לא צוירו כאן, כדי לא לנחש את המקום בתמונה).', []),
    ('today', 'home-today', 'היום על ההר: בטיול', 'לוח אחד במקום לוח העונה: היום ויום הסקי מהטיול שלך (התיקון של F3), שלושת הגבהים, סגירה בגלל רוח כהערכה, ומצב הרכבלים באותו לוח.', []),
    ('today', 'home-ahead', 'לפני הטיול: ימי הסקי שלך', 'מופיע כשיום הסקי הראשון שלך נכנס ל-15 ימי התחזית. יום שעוד מחוץ לתחזית אומר מתי הוא ייפתח. לפני כן אין לוח בכלל.', []),
    ('today', 'home-today-live-night', 'בלילה, עם דיווח של MTA', 'כשיש דיווח, הוא מחליף את ההערכה לאותו רכבל: Sadzele "סגור · לפי MTA", ול-Kudebi עדיין ההערכה.', []),
    ('today', 'home-today-en', 'באנגלית (משמאל לימין)', 'הלוח מתהפך, והמד מתמלא מהצד של תחילת השורה.', []),
    ('today', 'and-home-today', 'אנדרואיד: היום על ההר', 'אותו לוח ואותן מחרוזות מתחת לכרטיס. הכרטיס כאן מצילום של היום (40 ימים לטיסה); בטיול עצמו הוא מראה את יום הטיול.', []),
    ('today', 'desk-home', 'במחשב', 'הלוח בטור של הכרטיס, מתחת לו.', []),
    ('run', 'run-cond', 'בפאנל המסלול', 'Tatra 2: התנאים בראש המסלול (2,665 מ׳) ובתחתית (2,170 מ׳), אחרי הפרטים ולפני הפרופיל.', []),
    ('run', 'and-run-cond', 'אנדרואיד: בגיליון המסלול', 'באותו מקום בגיליון, אחרי המספרים, כשגוללים למעלה.', []),
    ('loc', 'loc-ask', 'לפני שהטלפון שואל', 'משפט קצר לפני חלון ההרשאה של הדפדפן או של אנדרואיד: המיקום נשאר בטלפון, פועל רק כשהמפה פתוחה, ועובד בלי קליטה.', []),
    ('loc', 'loc-on', 'איפה אני: על מסלול', 'הכפתור בעמודת הזום, כבוי בכל פתיחה. נקודה עם עיגול דיוק וכיוון, והשורה למטה: על איזה מסלול, גובה ודיוק.', []),
    ('loc', 'loc-lift', 'על רכבל', '"על הרכבל Goodaura" רק כשהטלפון מתקדם לאורך הכבל למעלה, לא לפי הגובה של ה-GPS.', []),
    ('loc', 'states', 'כל המצבים האחרים', 'מחוץ לגודאורי, אין הרשאה, מיקום לא מדויק (מעל 50 מ׳), מיקום משוער (אנדרואיד 12 ומעלה), ומכשיר בלי מיקום. בכולם אין "על..." בלי ודאות.', [('loc-out', 470, 172), ('loc-denied', 470, 172), ('loc-low', 330, 320), ('loc-approx', 250, 400), ('loc-unavail', 470, 172)]),
    ('loc', 'and-loc-on', 'אנדרואיד: על מסלול', 'אותה שורה ואותו כפתור, על המסלול שמסומן בצילום (Tatra 2).', []),
    ('desk', 'desk-map', 'במחשב: השכבה, המיקום והתנאים יחד', 'Tatra 2 נבחר: השכבה מעל ההר המעומעם, הנקודה שלך, והתנאים בפאנל.', []),
    ('down', 'loc-down', 'הדרך למטה לכפר', 'רק לשיקולך: לפי הארכיטקט זה לא בגרסה 1.0, כי טעות שולחת גולש לשחור או למסלול סגור. הדרך כאן לפי החיבורים בנתונים: סוף Tatra 1, ואז Shino לתחתית הרכבלים.', ['loc-down-list']),
]
ROWS = [
    ('weather', 'שכבת מזג אוויר במפה'),
    ('today', '"היום על ההר" בדף הבית, לפי הטיול שלך'),
    ('run', 'התנאים בראש המסלול ובתחתית'),
    ('loc', 'איפה אני'),
    ('desk', 'במחשב'),
    ('down', 'לא בגרסה 1.0: "הדרך למטה", הצעה נפרדת'),
]
PRIVACY = ('כשנוגעים ב"איפה אני", האפליקציה והאתר מבקשים גישה למיקום. המיקום משמש רק להצגת הנקודה על המפה, בתוך המכשיר. '
           'הוא לא נשלח אלינו או לאף אחד אחר, ולא נשמר. אפשר לבטל את ההרשאה בכל רגע בהגדרות.')
DECIDE = [
    ('מקור הנתונים (מ-6, ממתין לאישור שלך)', 'השרת מביא את התחזית פעם בשעה, והאתר והאפליקציה קוראים ממנו. לכן "עודכן לפני" זהה אצל כולם. '
     'בכל מקום שמזג האוויר מופיע: "נתוני מזג האוויר: Open-Meteo.com", כי הרישיון שלהם דורש קרדיט. הלוחות מצוירים לפי ההמלצה הזו.'),
    ('השכבה במפה', 'כפתור "מזג אוויר", כבוי עד שנוגעים: באתר בשורת הסינון, ובאפליקציה ליד "כל המסלולים". שלוש נקודות: הכפר, Goodaura העליונה ו-Sadzele, '
     'בגובה שלהן ממודל הגובה (2,170, 2,710 ו-3,240 מ׳). בכל נקודה: טמפרטורה, רוח עם חץ ועם מילים, ושלג חדש. אותו מידע גם ברשימה מתחת למפה.'),
    ('שלושה מצבים', 'עדכנית; "תחזית ישנה" בין 12 ל-48 שעות, למשל כשאין קליטה; ו"אין מידע" אחרי 48 שעות או כשאין תחזית בכלל. לא מנחשים.'),
    ('"היום על ההר" בדף הבית', 'לפי הטיול שלך, לא של החבר׳ה. מופיע כשיום הסקי הראשון שלך נכנס ל-15 ימי התחזית: לפני הטיול ימי הסקי שלך, ובטיול עצמו היום. '
     'הלוח מחליף את לוח העונה, ומצב הרכבלים נכנס לתוכו, כך שיש לוח אחד ולא שניים. התיקון של F3: "יום סקי 2 מתוך 4" מחושב מהטיול שלך.'),
    ('סגירה בגלל רוח', 'שלוש דרגות במילים ("נמוך", "בינוני", "גבוה"), עם מד של שלוש משבצות. **בלי ירוק ואדום**, כי אצלנו אלה צבעי מסלולים, ו"אדום" ייקרא כמו מסלול אדום. '
     'בלי מספרי סף על המסך: הספים יכוילו בעונה. מתחת לדרגות: "הערכה לפי התחזית, לא דיווח של אתר הסקי". כשיש דיווח של MTA על רכבל, הוא מחליף את ההערכה לאותו רכבל.'),
    ('בפאנל המסלול', 'התנאים בראש המסלול ובתחתית, בגבהים של המסלול עצמו, אחרי הפרטים ולפני הפרופיל.'),
    ('איפה אני', 'כפתור בעמודת הזום, שכבוי בכל פתיחה ונכבה כשיוצאים מהמפה. לפני שהטלפון שואל, משפט קצר: המיקום נשאר בטלפון. '
     'אחר כך נקודה עם עיגול דיוק, ובשורה למטה "על Tatra 1" או "על הרכבל Goodaura". המצבים האחרים: מיקום משוער, מיקום לא מדויק, מחוץ לגודאורי, בלי הרשאה, ומכשיר בלי מיקום. '
     'הניסוח בלי זכר ונקבה: "לא בגודאורי כרגע", ולא "אתה לא בגודאורי".'),
    ('שורה במדיניות הפרטיות', 'הנוסח שהארכיטקט הציע. סשן המשפט מנסח את הנוסח הסופי בארבע השפות, והוא לאישור שלך:<br><i>' + html.escape(PRIVACY) + '</i>'),
    ('"הדרך למטה"', 'מצויר בשורה נפרדת, למטה. לפי הארכיטקט זה לא בגרסה 1.0, כי טעות שולחת גולש למסלול שחור או סגור. אם תרצה, זה עובר סקירה נפרדת.'),
    ('מה מותר להיות שונה', 'המחרוזות זהות בשתי הפלטפורמות. שונים רק מקום הכפתור והמפה עצמה: באפליקציה תלת-ממד, והנקודות יושבות על ההר (החלטה 35).'),
    ('נתוני דוגמה', 'כל מספרי מזג האוויר על הלוחות מומצאים. הגבהים, המסלולים, התחנות והחיבורים מהנתונים של האתר.'),
]
GAP, PAD, HEAD = 24, 20, 124
DH = 1560
N = lambda f: f + '.webp'


def webp():
    """The shots to WebP: long pages cropped to what the board needs (ImageMagick; no Python imaging here)."""
    src, out = here / 'shots', here / 'shots/web'
    out.mkdir(exist_ok=True)
    n = 0
    for f in sorted(src.glob('*.png')):
        args = ['convert', str(f)]
        if f.stem in CROP:
            w = int(subprocess.check_output(['identify', '-format', '%w', str(f)]))
            args += ['-crop', f'{w}x{CROP[f.stem]}+0+0', '+repage']
        subprocess.run(args + ['-quality', '90', str(out / N(f.stem))], check=True)
        n += 1
    return n


def size(f):
    w, h = subprocess.check_output(['identify', '-format', '%w %h', str(here / 'shots/web' / N(f))]).split()
    return int(w), int(h)


def img(urls, f, title, crop=None):
    w, h = size(f)
    if crop:
        top, ch = crop
        return (f'<div style="width: {w}px; height: {ch}px; overflow: hidden; box-shadow: 0 0 0 1px #CBD5DF">'
                f'<img src="{urls[N(f)]}" alt="{html.escape(title)}" style="display: block; width: {w}px; margin-top: -{top}px"></div>'), w, ch
    return f'<img src="{urls[N(f)]}" alt="{html.escape(title)}" style="display: block; width: {w}px; height: {h}px; box-shadow: 0 0 0 1px #CBD5DF">', w, h


def board(b, urls):
    group, f, title, note, extra = b
    parts = []
    if f != 'states':
        parts.append(img(urls, f, title))
    for e in extra:
        parts.append(img(urls, e[0], title, (e[1], e[2])) if isinstance(e, tuple) else img(urls, e, title + ': הרשימה'))
    w = max(p[1] for p in parts) + 2 * PAD
    h = PAD + HEAD + sum(p[2] for p in parts) + GAP * (len(parts) - 1) + PAD
    head = (f'<div style="display: flex; flex-direction: column; gap: 4px; height: {HEAD - 12}px">'
            f"<b style=\"font-family: Karantina, 'Arial Narrow', sans-serif; font-size: 32px; line-height: 1\">{html.escape(title)}</b>"
            f'<span style="font-size: 13.5px; line-height: 1.45; color: #4B5A6F">{html.escape(note)}</span></div>')
    body = f'<div style="display: flex; flex-direction: column; gap: {GAP}px; align-items: flex-start">{"".join(p[0] for p in parts)}</div>'
    card = (f'<div style="width: {w}px; height: {h}px; box-sizing: border-box; padding: {PAD}px; display: flex; flex-direction: column; gap: 12px; '
            f"background: #FFFFFF; color: #13233A; font-family: 'IBM Plex Sans Hebrew', system-ui, sans-serif; direction: rtl\">{head}{body}</div>")
    return card, w, h


def decide_card():
    def md(s):  # **bold** in the items
        out, on = '', False
        for i, part in enumerate(s.split('**')):
            out += (f'<b style="color: #13233A">{part}</b>' if i % 2 else part)
        return out
    items = ''.join(f'<li style="display: flex; flex-direction: column; gap: 2px; padding: 10px 0; border-top: 1px solid #CBD5DF">'
                    f'<b style="font-size: 16px">{i + 1}. {t}</b><span style="font-size: 14px; line-height: 1.55; color: #4B5A6F">{md(s)}</span></li>'
                    for i, (t, s) in enumerate(DECIDE))
    return (f'<div style="width: 600px; height: {DH}px; box-sizing: border-box; padding: 28px; background: #FFFFFF; color: #13233A; '
            f"font-family: 'IBM Plex Sans Hebrew', system-ui, sans-serif; direction: rtl; border-top: 8px solid #1F5FC4\">"
            f"<h1 style=\"margin: 0; font-family: Karantina, 'Arial Narrow', sans-serif; font-size: 52px; line-height: 1\">מה לאשר בסבב 19</h1>"
            f'<p style="margin: 8px 0 16px; font-size: 14.5px; line-height: 1.55; color: #4B5A6F">קבעת שמזג אוויר ורוח לפי גבהים, ומיקום עצמי על המפה, הם חובה לפני העונה (החלטה 57). '
            f'כאן הם מצוירים פעם אחת לאתר ולאנדרואיד, באותן מחרוזות, בשפה העיצובית של היום. באתר הם מצוירים בתוך האתר האמיתי, ובאפליקציה מעל צילומים אמיתיים מהאמולטור. '
            f'{SAMPLE} שום דבר לא נבנה לפני שתאשר.</p>'
            f'<ol style="margin: 0; padding: 0; list-style: none">{items}</ol></div>')


def build(dst):
    urls = json.loads((here / 'shots/assets.json').read_text(encoding='utf-8'))
    boards = [('R19-0-Decide.dc.html', 'מה לאשר בסבב 19', page('מה לאשר בסבב 19', decide_card(), 600, DH), 600, DH, None)]
    for b in BOARDS:
        card, w, h = board(b, urls)
        boards.append((f'R19-{b[1]}.dc.html', b[2], page(b[2], card, w, h), w, h, b[0]))
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
    if sys.argv[1:] == ['webp']:
        print(webp(), 'shots -> webp')
    else:
        dst = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else root / 'design/canvas/project'
        out = build(dst)
        print(len(out), 'boards ->', dst)
