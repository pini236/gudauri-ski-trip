#!/usr/bin/env python3
"""Round 29: the ski day, recording speed and the day's track (decision 76, docs/FEATURES.md section i), for Android and
iPhone. The maps come from maps.mjs (day.js on the site's own top view), the screens from boards.mjs, over real
screenshots of both apps. Run from the repo root with the site served:
  node design/round29/maps.mjs && node design/round29/boards.mjs <android shots> <ios shots>
  python3 design/round29/build.py webp     (then upload shots/web to the canvas, ids in shots/assets.json)
  python3 design/round29/build.py [canvas project folder]"""
import html, json, re, pathlib, subprocess, sys

here = pathlib.Path(__file__).resolve().parent
root = here.parent.parent
sys.path.insert(0, str(root / 'design/round12'))
from build import page  # the board shell of rounds 12 to 28

PAGE = ('round29', 'סבב 29: יום הגלישה')
NAMES = sorted(p.stem for p in (here / 'shots').glob('[a-f]-*.png'))
SRC = {n: ('shots/' + n + '.png', 0, 0, 0) for n in NAMES}
N = lambda f: f + '.webp'
A, I = 'אנדרואיד', 'אייפון'
BOARDS = [
    ('a', 'Start', 'א. מתחילים יום', 'נגיעה אחת: שלט אדום "מתחילים יום" בראש עמוד השלטים, וכפתור במפה. כשהאפליקציה נפתחת בתוך האתר בלי הקלטה, הצעה אחת, עם "לא עכשיו". בלי התחלה אוטומטית ברקע (היא דורשת הרשאת מיקום "תמיד").',
     [(A + ' · דף הבית', ['a-and-home']), (I + ' · דף הבית, לילה', ['a-ios-home-night']), (A + ' · המפה', ['a-and-map']), (I + ' · המפה, English', ['a-ios-map-en'])]),
    ('a', 'Ask', 'א. ההצעה בתוך האתר, וההסבר לפני ההרשאה', 'לפני שהטלפון שואל על המיקום: שלוש שורות עם סמל, בלי פסקה. מספיקה הרשאה "בזמן השימוש באפליקציה".',
     [(A + ' · בתוך האתר', ['a-and-suggest']), (I + ' · בתוך האתר, לילה', ['a-ios-suggest-night']), (A + ' · לפני ההרשאה', ['a-and-perm']), (I + ' · לפני ההרשאה, English', ['a-ios-perm-en'])]),
    ('b', 'Live', 'ב. בזמן ההקלטה', 'המהירות עכשיו גדולה, ומתחתיה על מה אתה עכשיו: שלט המסלול, או "על הרכבל X". ארבעה מספרים: מרבית, גלישה, ירידה וירידות. השובל של היום על המפה, והנקודה בסופו. "סיום" שואל לפני שעוצר.',
     [(A + ' · במסלול', ['b-and-live']), (I + ' · במסלול', ['b-ios-live']), (A + ' · על הרכבל, לילה, English', ['b-and-live-lift-night-en']), (I + ' · לילה, English', ['b-ios-live-night-en'])]),
    ('b', 'Outside', 'ב. מחוץ לאפליקציה', 'באנדרואיד התראה קבועה (שירות בחזית) עם המהירות, המסלול ו"סיום". באייפון Live Activity במסך הנעילה (עם פס הגובה של הירידה), ו-Dynamic Island מקוצר ומורחב.',
     [(A + ' · ההתראה הקבועה', ['b-and-notif']), (I + ' · מסך הנעילה ו-Dynamic Island', ['b-ios-lock'])]),
    ('c', 'Summary', 'ג. סוף היום', 'שלושה מספרים, ומפה של כל היום צבועה לפי מהירות (סולם משלה, לא צבעי המסלולים ולא צבעי השיפוע), עם המקום של המהירות המרבית. ציר זמן: כל רכבל, כל ירידה עם השלטים של המסלולים לפי הסדר, ההפסקות ועלייה ברגל.',
     [(A + ' · הסיכום', ['c-and-summary']), (I + ' · הסיכום, לילה, English', ['c-ios-summary-night-en']), (A + ' · ציר הזמן', ['c-and-timeline']), (I + ' · ציר הזמן, English', ['c-ios-timeline-en'])]),
    ('c', 'Replay', 'ג. צפייה חוזרת, ירידה, שיאים ושיתוף', 'צפייה חוזרת בתלת-ממד: הנקודה על הקו, וציר זמן של כל היום שהירידות מסומנות עליו; גרירה לכל רגע. כרטיס לכל ירידה. השיאים האישיים מכל הימים, ותמונה לשיתוף בלי שם ובלי חשבון.',
     [(A + ' · צפייה חוזרת', ['c-and-replay']), (I + ' · צפייה חוזרת', ['c-ios-replay']), (A + ' · ירידה', ['c-and-run']), (A + ' · שיאים ושיתוף', ['c-and-records']), (I + ' · שיאים ושיתוף, English', ['c-ios-records-en'])]),
    ('d', 'History', 'ד. הימים שלי: היסטוריה, מחיקה וייצוא', 'כל הימים שהוקלטו, רק בטלפון. לכל יום: שיתוף התמונה, ייצוא GPX ומחיקה; ו"מחיקת הכל". באנדרואיד תפריט, באייפון גיליון פעולות.',
     [(A, ['d-and-history']), (I + ' · English', ['d-ios-history-en'])]),
    ('e', 'States', 'ה. מצבים', 'שורה אחת בראש המפה, עם סמל וטקסט קצר, והמסך החי מתחת. GPS חלש: ממשיכים להקליט, בלי לספור מהירות. בלי הרשאה: מה לעשות. מחוץ לאתר: השהיה. בלי תנועה זמן רב: היום ייסגר לבד. מחוץ למסלולים: קו מקווקו ובלי שם. עלייה בלי רכבל ידוע: "עלייה".',
     [('GPS חלש', ['e-and-gps']), ('אין הרשאה', ['e-and-denied']), ('מחוץ לאתר', ['e-and-out']), ('עצירה אוטומטית', ['e-and-auto']), ('מחוץ למסלולים', ['e-and-off']), ('עלייה בלי רכבל', ['e-and-climb']), ('צריך מיקום מדויק', ['e-and-approx']), ('היום נקטע', ['e-and-resume']), ('ירידה בתחנה לא ממופה', ['e-and-mid']), (I + ' · GPS חלש, English', ['e-ios-gps-en']), (I + ' · מחוץ למסלולים, לילה', ['e-ios-off-night-en'])]),
    ('f', 'Moat', 'ו. החוויה: הרגעים שרק לנו יש', 'הנתונים מופיעים כשיש זמן להסתכל: על הרכבל, בתור, בבקתה. "פה נפלת" כשאלה משועשעת עם "לא נפלתי" שמוחק, ובלי שום הבטחה לבטיחות. על הרכבל: הירידה שהרגע עשית. בסוף היום: כרטיס אחד כמו כרטיס הטיסה, עם ציוני דרך מול עצמך בלבד (האדום הראשון, הירידה הארוכה, זמן בתור, פי כמה ממגדל עזריאלי).',
     [(A + ' · "נראה שנשקת לשלג כאן?"', ['f-and-fall']), (I + ' · אותו דבר, English', ['f-ios-fall-en']), (A + ' · על הרכבל: הירידה שהרגע עשית', ['f-and-liftsum']), (I + ' · Live Activity על הרכבל', ['f-ios-liftsum']), (A + ' · היום שלך, כרטיס אחד', ['f-and-story']), (I + ' · היום שלך, English', ['f-ios-story-en'])]),
]
ROWS = [('a', 'א. ההתחלה'), ('b', 'ב. בזמן ההקלטה'), ('c', 'ג. סוף היום'), ('d', 'ד. היסטוריה'), ('e', 'ה. מצבים'), ('f', 'ו. החוויה')]
TEXT_BOARDS = [
    ('Spec', 'מה למימוש', 640, 900, [
        ('אותו מסך בשתי האפליקציות', 'אותם מספרים ואותם שמות, כל אחת ברכיבים שלה (החלטה 35): באנדרואיד גיליון ברוחב מלא ופינות ישרות והתראה קבועה; באייפון גיליון מעוגל, Live Activity ו-Dynamic Island. הטוקנים של האתר.'),
        ('סולם המהירות', '0, 20, 40 ו-60 קמ״ש ומעלה: צהוב בהיר, כתום, אדום, סגול. נפרד מצבעי המסלולים ומצבעי השיפוע (15/25/30), כי על אותה מפה. רכבל: קו אפור מקווקו. מחוץ למסלולים: קו כהה מקווקו. עלייה ברגל: נקודות.'),
        ('שמות רק מהמפה', 'במסך החי "כנראה"; ציר הזמן הוא האמת. קטע משותף: "Tatra 1 · 2". ירידה מקבלת את השלטים של המסלולים שההתאמה מצאה (כמו `Tatra 1 ← Tatra 2`). בלי קו במפה: "מחוץ למסלולים". עלייה בלי רכבל בנתונים: "עלייה".'),
        ('המהירות המרבית', 'מוצגת כ"מרבית, הערכה", עם המקום והשעה ("פה טסת"). רק אם נשמרה לפחות שתי שניות, ממדידת הדופלר; המקום שלה מסומן במשולש על המפה ובכרטיס הירידה.'),
        ('הנתונים בלוחות', 'יום לדוגמה: הקווים של הרכבלים והמסלולים והגבהים אמיתיים (`site/data`), השעות והמהירויות מומצאות (`design/round29/day.js`). המפות הן מבט העל של האתר, שהאפליקציות מציירות אותו דבר (A-30); הצפייה החוזרת מעל צילום התלת-ממד של כל אפליקציה.'),
        ('מחרוזות', 'הנוסחים בלוחות, בעברית ובאנגלית; לסשן התוכן ול-`i18n/strings.json` אחרי האישור. ברוסית ובגאורגית כרגיל.'),
    ], 'd'),
    ('Data', 'מה עוד לא קיים', 640, 1000, [
        ('הכל', 'כל הלוחות נשענים על אלגוריתם הייחוס ועל ערכי הבדיקה (`tools/dayrec/`), שעוד בעבודה: זיהוי רכבל וירידה, התאמה למסלולים, "מחוץ למסלולים" ו"עלייה".'),
        ('"נראה שנשקת לשלג כאן?"', 'חיישני תנועה בקצב גבוה (בערך 50 בשנייה), שנשמרים רק סביב אירוע חשוד. זו תוספת ליומן הגולמי, ודורשת סקירת ארכיטקט. גם הסף לזיהוי עוד לא נקבע: רק אחרי בדיקה בשלג.'),
        ('על הרכבל', 'זיהוי רכבל בזמן אמת עד 30 שניות. באייפון ה-Live Activity מחכה לסקירה החוזרת של שרשרת השחרור, ולמזהה ולפרופיל נוספים אצל אפל (פיני).'),
        ('תחנת ביניים', 'השדה `lifts[].mid` עוד לא בנתונים.'),
        ('צפייה חוזרת וסרטון', 'הצפייה החוזרת בנויה על "טיסה במורד המסלול" שכבר קיים. "סרטון קצר" לשיתוף דורש קידוד וידאו בטלפון, וזה חדש בשתי האפליקציות.'),
        ('ציוני דרך', '"האדום הראשון" ו"הירידה הארוכה" דורשים היסטוריה של כמה ימים בטלפון. הנתונים עצמם (צבע ואורך) כבר קיימים.'),
        ('זמן בתור', 'עמידה ליד התחנה התחתונה לפני העלייה. נמדד מהיומן, אבל לא בודק אם באמת היה תור.'),
        ('השוואות', 'גובה מגדל עזריאלי (187 מ׳) קבוע. לאמת אותו מול מקור לפני המימוש.'),
    ], 'f'),
]
DECIDE = [
    ('ההמלצה', '**הכל כמו בלוחות:** שלט "מתחילים יום" בדף הבית וכפתור במפה, הצעה אחת כשנפתחים בתוך האתר, מסך חי עם המהירות גדולה ו"על מה אני עכשיו", התראה קבועה ו-Live Activity, ובסוף היום סיכום, מפה לפי מהירות, ציר זמן, צפייה חוזרת, כרטיס לכל ירידה, שיאים ותמונה לשיתוף.'),
    ('1. איפה מתחילים', 'שלט אדום חדש בראש עמוד השלטים (ההמלצה: הכי בולט ביום סקי), או רק כפתור במפה? השלט מופיע רק בימי הטיול ובתוך האתר.'),
    ('2. ההצעה בתוך האתר', 'הצעה אחת ליום, עם "לא עכשיו" (ההמלצה), או בלי הצעה בכלל?'),
    ('3. צבע המהירות', 'סולם משלו, צהוב עד סגול (ההמלצה), כדי שלא יתבלבל עם צבעי המסלולים והשיפוע.'),
    ('4. התמונה לשיתוף', 'בלי שם ובלי חשבון (ההמלצה). עם שם כשיהיה שיתוף לקבוצה (שלב הבא).'),
    ('5. סיום', '"סיום" שואל לפני שעוצר (ההמלצה), ובאנדרואיד גם בהתראה.'),
    ('6. "פה נפלת"', 'כשאלה משועשעת בציר הזמן, עם "לא נפלתי" שמוחק (ההמלצה), או בלי בכלל? ניחוש לפי תנועת הטלפון, לא ודאות ולא כלי בטיחות.'),
    ('7. על הרכבל', 'סיכום הירידה שהרגע עשית בהתראה וב-Live Activity כשעולים לרכבל (ההמלצה). באייפון Live Activity אחרי סקירת שרשרת השחרור.'),
    ('8. היום שלך', 'כרטיס אחד כמו כרטיס הטיסה, עם ציוני דרך מול עצמך בלבד, בלי דירוג (ההמלצה). "סרטון קצר" מהצפייה החוזרת לשיתוף.'),
    ('אחרי האישור', 'סשני האנדרואיד והאייפון מממשים במקביל, אחרי סקירת הארכיטקט ושורת הפרטיות. שום דבר לא שונה באפליקציות.'),
]
GAP, PAD, HEAD, CGAP, DW, DH = 18, 20, 130, 28, 600, 1000
def webp():
    out = here / 'shots/web'
    out.mkdir(exist_ok=True)
    for name, (f, w, top, h) in SRC.items():
        args = ['convert', str(here / f)]
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
    lead = ('יום הגלישה (החלטה 76): הקלטה, מהירות והמסלול היומי, באנדרואיד ובאייפון, ביום ובלילה, בעברית ובאנגלית, מעל צילומי שתי האפליקציות. יום לדוגמה: קווים וגבהים אמיתיים, שעות ומהירויות מומצאות.')
    boards = [('R29-0-Decide.dc.html', 'מה לאשר בסבב 29', page('מה לאשר בסבב 29', card('מה לאשר בסבב 29', DECIDE, DW, DH, lead), DW, DH), DW, DH, None)]
    for b in BOARDS:
        c, w, h = board(b, urls)
        boards.append((f'R29-{b[1]}.dc.html', b[2], page(b[2], c, w, h), w, h, b[0]))
    for key, title, w, h, items, g in TEXT_BOARDS:
        boards.append((f'R29-{key}.dc.html', title, page(title, card(title, items, w, h, accent='#13233A'), w, h), w, h, g))
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
