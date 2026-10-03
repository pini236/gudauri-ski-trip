#!/usr/bin/env python3
"""Round 16: a design review of the whole site, page by page and component by component (Pini, 2.10.2026: "תעבור על כל
העיצוב ועל כל החלקים של ה-UI, תחפש בעיות באתר ותתקן... עמוד עמוד רכיב רכיב").

Every board is one pair from pairs.mjs: the same state of the real site from main (before) and from the branch (after),
uploaded to the canvas as assets (shots/assets.json maps each file to its url). One canvas page, "סבב 16: ליטוש העיצוב
באתר", with a card on what to approve. Nothing is merged before Pini approves (decision 18).
Run from the repo root, after pairs.mjs:
  python3 design/round16/build.py webp             the shots to WebP (a quarter of the size), then upload them to the canvas
  python3 design/round16/build.py [canvas folder]  the boards and canvas.json (default: design/canvas/project)"""
import html, json, pathlib, sys

here = pathlib.Path(__file__).resolve().parent
root = here.parent.parent
sys.path.insert(0, str(root / 'design/round12'))
from build import page  # same board shell as rounds 12 to 15

PAGE = ('round16', 'סבב 16: ליטוש העיצוב באתר')
ROWS = [  # group, title over the row
    ('snow', 'השלג על השלטים והכרטיסים: מה שהראית בצילום, בכל מקום באתר'),
    ('season', 'לוח העונה בדף הבית, בינואר: בזמן הטיול'),
    ('lang', 'כפתור השפה מהענף הראשי (החלטה 48), בתוך סבב 16'),
    ('home', 'דף הבית'),
    ('top', 'הסרגל העליון במחשב'),
    ('map', 'מפת המסלולים'),
    ('meet', 'נקודת מפגש'),
    ('games', 'משחקים'),
    ('about', 'אודות והגדרות'),
    ('acct', 'כניסה, חשבון וקבוצה'),
    ('narrow', 'טלפון קטן ושפות ארוכות'),
]
TEXTS = [  # strings whose words are no longer true (in four languages; the Hebrew here)
    ('בנקודת המפגש, מתחת לשיתוף', 'בלי מסד נתונים ובלי הרשמה: המקום, היום והשעה נמצאים בתוך הקישור עצמו.', 'הקישור עובד בלי הרשמה: המקום, היום והשעה נמצאים בתוך הקישור עצמו.'),
    ('כרטיס המפגש, בכותרת', 'החבר׳ה · גודאורי 2027', 'גודאורי 2027'),
    ('עמוד המשחקים', 'השיאים נשמרים רק בטלפון שלכם', 'השיאים נשמרים בדפדפן, ומי שבקבוצה רואה אותם גם בטבלת הקבוצה'),
    ('הגדרות, איפוס השיאים', 'השיאים נשמרים רק בטלפון הזה', 'השיאים שנשמרו בדפדפן הזה'),
    ('קבוצה, שיאים ריקים', 'השיאים מגיעים מהמשחקים באפליקציה.', 'השיאים מגיעים מהמשחקים באתר ובאפליקציה.'),
    ('הטיול שלך, ימי סקי ידניים', 'יום ראשון / יום אחרון (נקרא כמו היום בשבוע)', 'יום סקי ראשון / יום סקי אחרון, כמו באפליקציה'),
]
DECIDE = [
    ('ההערות שלך', 'כל 12 ההערות מהקנבס (3.10.2026) טופלו, והזוגות צולמו מחדש: על הכרטיסים הלבנים חזרו ערימות שלג מלאות, כמו בשלג הקודם שהעדפת, '
                   'אבל על כל הרוחב ומעל המסגרת, כך שהקו לא מציץ מתחתיו; השלג על שלטי המפה והמשחקים לא השתנה; דף הפרטיות מצולם במקום שבו הכרטיס עומד; '
                   'בכותרת של דף הבית ובסרגל העליון השעון, ואז שני הכפתורים יחד; הירח חזר לשמיים בטלפון; הספח שנתלש שקוף ובלי מסגרת מקווקוות; '
                   'בפרופיל שני הגבהים בצד שמאל; בלוח הרכבלים השמות מיושרים לימין; השורה מתחת לכותרות הסרטונים בצד ימין, כמו קודם; '
                   'וסימן ההפעלה במשחקים באמצע התמונה, בכל השפות.'),
    ('השלג', 'רכיב אחד לכל השלטים והכרטיסים באתר ובדף הפרטיות (שלט "ההר עוד ישן", שלטי הרכבלים, המשחקים, הכרטיסים של הכניסה והקבוצה). '
             'השלג מצויר לפי הרוחב האמיתי של כל שלט, יושב על הקצה ומכסה את המסגרת עד הסוף, נגמר איפה שהחץ מתחיל ומתהפך עם השפה, ובלילה באור ירח. '
             'על השלטים במפה שלג נמוך, ועל הכרטיסים הלבנים ערימות כמו בשלט המושלג שאישרת בסבב 3 (S3).'),
    ('לוח העונה', 'הלוח בדף הבית אמר תמיד "ההר עוד ישן... העונה נפתחת בדרך כלל בדצמבר", גם בינואר וגם כשהרכבלים פתוחים. '
                  'עכשיו הוא כמו השלטים והפס במפה (S1, S3): עד דצמבר ההר ישן, בעונה בלי דיווח "אין מידע עדכני", ועם דיווח עדכני השלג יורד, '
                  'הפס למעלה ירוק ונכתב כמה רכבלים פתוחים ומתי עודכן. כך זה גם באפליקציה (סשן האנדרואיד, 3.10.2026), כדי ששתיהן ייראו אותו דבר. '
                  'הלוח עדיין מופיע רק למי שעוד אין לו טיול (החלטה 46).'),
    ('כפתור השפה', 'הכפתור שנכנס היום לענף הראשי (החלטה 48) משתלב בסבב 16: אחרי השעון, ליד כפתור היום והלילה, בסרגל העליון ובדף הבית בטלפון. '
                   'בסרגל העליון הוא לא מוסתר אף פעם כשהסרגל מוותר על דברים, והכפתורים נשארים יחד בקצה. בטלפון, כשכותרת לא נכנסת במילים שלמות, הכפתור מוותר על הגלובוס; '
                   'בדף הבית אחר כך השעון מוותר על הכיתוב, ובשאר העמודים הכותרת עוברת לשורה משלה. ובדרך: החץ חזרה בעמודי החשבון, הקבוצה והטיול '
                   'הצביע קדימה באנגלית, ברוסית ובגאורגית.'),
    ('הלילה', 'בלילה הכחול, הירוק והאדום בהירים, ולכן הטקסט עליהם כהה, כמו בשלטים במפה. עד היום היה שם לבן, שלא קריא (ניגודיות 2.8, ובפסים על הדיו 1.15). '
              'בסריקה של כל העמודים, 35 ממצאי ניגודיות ירדו ל-6: שני "???" בכרטיס הריק (קישוט), ושלושה גבוליים (4.4 במקום 4.5) בירוק ובאדום של המסלולים.'),
    ('בלי גלילה הצידה', 'נבדק בשמונה רוחבים (320 עד 1024) ובארבע השפות. הסרגל העליון במחשב צר מוותר על דברים לפי הסדר; בדף הפרטיות, בטופס הטיול ובכותרות בגאורגית הכל נכנס.'),
    ('נקודת המפגש', '"איך מגיעים" נקרא עכשיו בכיוון הדף, מהמקום שלך אל הנקודה הצהובה, עם החצים של השלטים (היום הוא נקרא משמאל לימין גם בעברית). '
                    'ימי השבוע בגאורגית מגיעים מהאתר עצמו, כי הדפדפן לא מכיר אותם ונפל לעברית.'),
    ('נגישות', 'יעדי מגע של 44 בכל מקום שנמצא קטן מזה. לשוניות הקבוצה עובדות עם המקלדת ועם קורא מסך, השלט Kobi Pass נגיש מהמקלדת, ולכפתור מחיקת מפגש יש שם נכון.'),
    ('אפל', 'הכפתור של אפל אומר "בקרוב" (מקווקו, כמו כל מה שעוד לא קיים באתר), עד שיהיה חשבון מפתח של אפל.'),
    ('טקסטים', 'שישה שכבר לא נכונים או מבלבלים, בארבע השפות:<br>' + '<br>'.join(f'<b>{html.escape(w)}:</b> <s>{html.escape(a)}</s> ← {html.escape(b)}' for w, a, b in TEXTS)),
    ('לא בסבב הזה', 'פערי המראה בין האתר לאפליקציה (צבעי הכפתורים בלילה, המתג, הלשוניות, כרטיס ההזמנה, צורת השלג ועוד): החלטה אחת לשתי הפלטפורמות, אולי יחד עם השפה העיצובית החדשה. '
                    'הסמל בלשונית הדפדפן הוא עדיין השלט הכחול הישן. רשום ב-ROADMAP.'),
]
GAP, PAD = 24, 20
DH = 1920  # the decide card's height


def pair_board(p, urls):
    """Before and after side by side (or one above the other when wide), with what changed."""
    (bw, bh), (aw, ah) = p['before'], p['after']
    stack = max(bw, aw) > 600
    head = (f'<div style="display: flex; flex-direction: column; gap: 4px">'
            f"<b style=\"font-family: Karantina, 'Arial Narrow', sans-serif; font-size: 34px; line-height: 1\">{html.escape(p['title'])}</b>"
            f'<span style="font-size: 14px; line-height: 1.45; color: #4B5A6F">{html.escape(p["note"])}</span></div>')
    def col(label, src, w, h, color):
        return (f'<div style="display: flex; flex-direction: column; gap: 6px; width: {w}px">'
                f'<span style="align-self: flex-start; padding: 2px 10px; background: {color}; color: #FFFFFF; font-size: 13px; font-weight: 700">{label}</span>'
                f'<img src="{src}" alt="{html.escape(p["title"])}: {label}" style="display: block; width: {w}px; height: {h}px; box-shadow: 0 0 0 1px #CBD5DF"></div>')
    b, a = col('לפני', urls[p['id'] + '-before.webp'], bw, bh, '#4B5A6F'), col('אחרי', urls[p['id'] + '-after.webp'], aw, ah, '#1B8A4C')
    if stack:
        w = max(bw, aw) + 2 * PAD
        h = PAD + 76 + (bh + 28) + GAP + (ah + 28) + PAD
        body = f'<div style="display: flex; flex-direction: column; gap: {GAP}px">{b}{a}</div>'
    else:
        w = bw + aw + GAP + 2 * PAD
        h = PAD + 76 + max(bh, ah) + 28 + PAD
        # after on the right: the page reads right to left
        body = f'<div style="display: flex; gap: {GAP}px; align-items: flex-start">{a}{b}</div>'
    w = max(w, 420)
    card = (f'<div style="width: {w}px; height: {h}px; box-sizing: border-box; padding: {PAD}px; display: flex; flex-direction: column; gap: 12px; '
            f"background: #FFFFFF; color: #13233A; font-family: 'IBM Plex Sans Hebrew', system-ui, sans-serif; direction: rtl\">{head}{body}</div>")
    return card, w, h


def decide_card(n):
    items = ''.join(f'<li style="display: flex; flex-direction: column; gap: 2px; padding: 10px 0; border-top: 1px solid #CBD5DF">'
                    f'<b style="font-size: 16px">{i + 1}. {t}</b><span style="font-size: 14px; line-height: 1.55; color: #4B5A6F">{s}</span></li>'
                    for i, (t, s) in enumerate(DECIDE))
    return (f'<div style="width: 560px; height: {DH}px; box-sizing: border-box; padding: 28px; background: #FFFFFF; color: #13233A; '
            f"font-family: 'IBM Plex Sans Hebrew', system-ui, sans-serif; direction: rtl; border-top: 8px solid #1F5FC4\">"
            f"<h1 style=\"margin: 0; font-family: Karantina, 'Arial Narrow', sans-serif; font-size: 52px; line-height: 1\">מה לאשר בסבב 16</h1>"
            f'<p style="margin: 8px 0 16px; font-size: 14.5px; line-height: 1.55; color: #4B5A6F">ביקשת לעבור על כל העיצוב באתר, עמוד אחרי עמוד ורכיב אחרי רכיב, ולתקן. '
            f'עברתי על כל העמודים והמצבים, בטלפון ובמחשב, ביום ובלילה ובארבע השפות, ונמצאו כ-60 בעיות. כאן {n} זוגות של לפני ואחרי מהאתר האמיתי, '
            f'מסודרים לפי עמוד. הכל בענף, ונכנס לאתר רק אחרי שתאשר. בתוך השפה העיצובית הקיימת, בלי שינוי בכיוון.</p>'
            f'<ol style="margin: 0; padding: 0; list-style: none">{items}</ol></div>')


def build(dst):
    pairs = json.loads((here / 'shots/pairs.json').read_text(encoding='utf-8'))
    urls = json.loads((here / 'shots/assets.json').read_text(encoding='utf-8'))
    pairs = [p for p in pairs if p.get('before') and p.get('after')]
    boards = [('R16-0-Decide.dc.html', 'מה לאשר בסבב 16', page('מה לאשר בסבב 16', decide_card(len(pairs)), 560, DH), 560, DH, None)]
    for p in pairs:
        card, w, h = pair_board(p, urls)
        boards.append((f'R16-{p["id"]}.dc.html', p['title'], page(p['title'], card, w, h), w, h, p['group']))
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
    index['boards'][boards[0][0]] = {'x': 0, 'y': 0, 'w': 560, 'h': DH, 'page': PAGE[0], 'title': boards[0][1]}
    if boards[0][0] not in index['order']:
        index['order'].append(boards[0][0])
    y = 0
    for ri, (group, title) in enumerate(ROWS):
        x, rowh = 640, 0
        for f, t, _, w, h, g in boards:
            if g != group:
                continue
            index['boards'][f] = {'x': x, 'y': y, 'w': w, 'h': h, 'page': PAGE[0], 'title': t}
            if f not in index['order']:
                index['order'].append(f)
            x += w + 80
            rowh = max(rowh, h)
        if not rowh:
            continue
        index['notes'][f'{PAGE[0]}row{ri}'] = {'kind': 'title1', 'maxW': max(860, x - 720), 'page': PAGE[0], 'text': title, 'w': 240, 'x': 640, 'y': y - 300}
        y += rowh + 120 + 300
    index['launch'] = {'view': 'canvas', 'page': PAGE[0]}
    ip.write_text(json.dumps(index, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    return [b[0] for b in boards]


def webp():
    from PIL import Image
    n = 0
    for f in sorted((here / 'shots').glob('*.png')):
        Image.open(f).save(f.with_suffix('.webp'), 'WEBP', quality=88, method=6)
        f.unlink()
        n += 1
    return n


if __name__ == '__main__':
    if sys.argv[1:] == ['webp']:
        print(webp(), 'shots -> webp')
    else:
        dst = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else root / 'design/canvas/project'
        out = build(dst)
        print(len(out), 'boards ->', dst)
