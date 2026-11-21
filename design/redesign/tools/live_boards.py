#!/usr/bin/env python3
"""The canvas boards of stage B, the live model of the mountain (page "live"): M00 to M05.
The frames are the reference frames of the handoff (design/handoff/2026-11-20-redesign-live/ref), uploaded to the canvas
as assets; the urls are below. Writes the boards into design/redesign/canvas/project and adds the page to canvas.json.
  python3 design/redesign/tools/live_boards.py            write the boards and update canvas.json
  python3 design/redesign/tools/live_boards.py --check D   also write plain html copies with local images into D (to look at)
"""
import json, os, re, sys
HERE = os.path.dirname(os.path.abspath(__file__)); ROOT = os.path.normpath(os.path.join(HERE, '..', '..', '..'))
PROJ = os.path.join(ROOT, 'design/redesign/canvas/project'); REF = 'design/handoff/2026-11-20-redesign-live/ref/'
IMG = {  # asset url in the canvas, and the file it came from
    'hero': ('/_blob/9cc4c24209724e2a7cefb753eeb992ee', 'design/redesign/assets/live-hero-after.jpg'),
    'dawn': ('/_blob/92fe4c7c19451474136c22943ff02600', REF + 'open-dawn.jpg'),
    'morning': ('/_blob/13713d0f1269013143f08259b57ec645', REF + 'open-morning.jpg'),
    'noon': ('/_blob/3f4a9b3ec28e29106075c935de80d557', REF + 'open-noon.jpg'),
    'after': ('/_blob/3f9e7e09c733bfc34734f622edcac861', REF + 'open-after.jpg'),
    'sunset': ('/_blob/570fe9ee9a8b199449f4e1d2a3250cb4', REF + 'open-sunset.jpg'),
    'blue': ('/_blob/c3f53236b7aeca687128b463a1a50991', REF + 'open-blue.jpg'),
    'night': ('/_blob/b8b35d43b5fa1106334b58e78ddb83e6', REF + 'open-night.jpg'),
    'east': ('/_blob/3a3be622f987dccd33047e2a76037f6e', REF + 'east-after.jpg'),
    'north': ('/_blob/ac5e08836ff7ffbf0297e3601b10c532', REF + 'north-noon.jpg'),
    'close': ('/_blob/fe9894df642ea7e00be5c9e32d9fc4bc', REF + 'close-morning.jpg'),
    'plain': ('/_blob/27f33f811bf9b7258d1f30b9e95b76ad', REF + 'plain-morning.jpg'),
}
U = lambda k: IMG[k][0]
FONT = '<link href="https://fonts.googleapis.com/css2?family=Heebo:wght@300;400;500;700;900&display=swap" rel="stylesheet">'
CSS = """
body{margin:0}
.HW{font-family:'Heebo',system-ui,sans-serif;color:#10161D;background:#F6F7F9}
.HW h1,.HW h2,.HW h3,.HW p,.HW ul,.HW ol{margin:0}
.HW ul,.HW ol{padding-inline-start:20px;display:flex;flex-direction:column;gap:7px}
.HW li{font-size:15px;line-height:1.5}
.HW .card{background:#fff;border:1px solid #D9DEE4;border-radius:14px;padding:22px;display:flex;flex-direction:column;gap:10px}
.HW .q{display:flex;gap:12px;align-items:baseline;font-size:15.5px;line-height:1.5}
.HW .qn{font-weight:900;font-size:20px;min-width:24px}
.HW .th{position:relative;overflow:hidden;border-radius:14px;border:1px solid #D9DEE4;flex:none;background:#DCE6F2}
.HW .th img{position:absolute;left:0;top:0;width:100%;height:100%;display:block}
.HW .p{font-size:15px;line-height:1.55}
.HW .sec{font-size:13px;font-weight:700;letter-spacing:.04em;color:#6B3FD4}
.HW h1{font-size:34px;font-weight:900;line-height:1.15}
.HW h2{font-size:23px;font-weight:700;line-height:1.25}
.HW .lead{font-size:18px;line-height:1.55}
.HW .cap{font-size:13.5px;line-height:1.45;color:#3C4753}
.HW .cap b{color:#10161D;font-size:15px}
.HW .rowx{display:flex;gap:16px;align-items:flex-start}
.HW .fr{display:flex;flex-direction:column;gap:8px;flex:none}
.HW .tm{font-size:22px;font-weight:300;direction:ltr;display:inline-block}
"""


def board(title, w, h, body, props=None, script='return {};', lang='he'):
    p = dict(props or {}); p['$preview'] = {'width': w, 'height': h}
    return ('<!doctype html>\n<html lang="%s">\n<head>\n<meta charset="utf-8">\n<title>%s</title>\n<script src="./support.js"></script>\n</head>\n<body>\n<x-dc>\n<helmet>\n%s\n<style>%s</style>\n</helmet>\n%s\n</x-dc>\n'
            '<script type="text/x-dc" data-dc-script data-props=\'%s\'>\nclass Component extends DCLogic {\nrenderVals() {\n%s\n}\n}\n</script>\n</body>\n</html>\n'
            % (lang, title, FONT, CSS, body, json.dumps(p, ensure_ascii=False, separators=(',', ':')), script))


def frame(k, w, cap, sub=''):
    h = round(w * 844 / 390)
    return ('<div class="fr" style="width: %dpx"><div class="th" style="width: %dpx; height: %dpx"><img alt="" src="%s"></div><p class="cap"><b>%s</b>%s</p></div>'
            % (w, w, h, U(k), cap, ('<br>' + sub) if sub else ''))


def wide(w, h, inner):
    return '<div class="HW" dir="rtl" style="width: %dpx; height: %dpx; box-sizing: border-box; padding: 44px 52px; display: flex; flex-direction: column; gap: 22px">%s</div>' % (w, h, inner)


def m00():
    steps = [('אור לכל פיקסל, ושלג בשני צבעים.', 'המואר כמעט לבן וחם, הצל כחול עמוק.'),
             ('אוויר.', 'מה שרחוק נמס אל צבע האופק, ובעמקים שוכב אובך דק.'),
             ('האופק הרחוק האמיתי.', 'מאחורי Sadzele עולה הקווקז הגבוה, עם קזבק (5,054 מ׳, כ-21 ק״מ צפונה).'),
             ('השעה.', 'שבעה מצבי אור לאורך היום, ואור ורוד על הפסגות כשהשמש נמוכה.'),
             ('סלע.', 'מדרון תלול מדי לשלג הוא סלע, בפסים לאורך קו הנפילה.'),
             ('מבט הפתיחה.', 'נמוך ורחוק, כך שיש שמיים ואופק.'),
             ('הפרטים הקטנים.', 'אדוות רוח, ברק של שמש נמוכה, מסלול מגוהץ, אור בכל בניין בכפר, ענני נוצה.')]
    li = ''.join('<li><b>%s</b> %s</li>' % s for s in steps)
    hero = ('<div class="th" style="width: 1176px; height: 518px; border-radius: 18px"><img alt="" src="%s">'
            '<div style="position: absolute; right: 36px; top: 26px; color: #fff; text-shadow: 0 2px 16px rgba(20,30,80,.75)"><p style="font-size: 14px; font-weight: 700; letter-spacing: .04em">שלב ב · 21.11.2026</p>'
            '<p style="font-size: 54px; font-weight: 900; line-height: 1.1; margin-top: 4px">ההר החי</p><p style="font-size: 20px; font-weight: 500; margin-top: 6px">אותו נוף, ועכשיו אפשר לזוז בו</p></div></div>' % U('hero'))
    lead = ('<p class="lead">ביקשת שההר ייראה מרהיב כמו נוף אלפיני, וגם שיהיה מודל תלת-ממדי. זו המטרה: ההר האמיתי של גודאורי, במודל חי שמסתובבים בו, '
            'עם האור, האוויר והאופק של התמונה המצוירת. המבצע בונה לפי זה עכשיו, וצילומים של מה שנבנה באמת יחזרו לכאן.</p>')
    c1 = '<div class="card" style="flex: 1.25"><p class="sec">לפי הסדר: מה שמשנה הכי הרבה, קודם</p><h2>מה הופך אותו לנוף אלפיני</h2><ol>%s</ol></div>' % li
    c2 = ('<div class="card" style="flex: 1"><p class="sec">כללי הדיוק</p><h2>מה אמיתי ומה ציור</h2><ul>'
          '<li><b>אמיתי:</b> ההר (מודל הגובה, בלי הגזמת גובה); האופק הרחוק (אותו מקור פתוח, עד 62 ק״מ); מקום השמש לפי השעה והתאריך; הצל שהרים מטילים; 41 הבניינים של הכפר, אור אחד לכל בניין בלילה.</li>'
          '<li><b>ציור:</b> ענני הנוצה, אדוות הרוח, הכוכבים וגוון הסלע.</li>'
          '<li><b>אין:</b> עצים, בתים או פסגות מומצאים.</li></ul>'
          '<p class="sec" style="margin-top: 8px">הלוחות</p><ul><li><b>1</b> שבע שעות, אותו מבט</li><li><b>2</b> אותו הר מכל כיוון</li><li><b>3</b> מה ההבדל</li><li><b>4</b> הפתיחה בגודל טלפון, ביום ובלילה</li><li><b>5, 6</b> שקיעה ואחר הצהריים, בגודל טלפון</li></ul></div>')
    qs = ('<div class="card"><h2>שאלות אליך</h2>'
          '<p class="p" style="background: #EEE8FB; border-radius: 10px; padding: 10px 14px"><b>ענית (21.11.2026): "מקבל את ההמלצות".</b> מבט הפתיחה הנמוך והרחוק, והקווקז האמיתי באופק, נשארים. '
          'והעדיפות שקבעת: קודם שההר עצמו יהיה מרהיב (אור, שלג, סלע, אוויר ופרטים); באופק הרחוק לא משקיעים יותר כרגע.</p>'
          '<div class="q"><span class="qn">1</span><span><b>מבט הפתיחה.</b> נמוך ורחוק יותר מהתמונה של שלב א, כדי שיהיו שמיים ואופק. המחיר: בפתיחה המסלולים קטנים יותר, ומתקרבים אליהם בצביטה. <b>(ההמלצה: כן)</b></span></div>'
          '<div class="q"><span class="qn">2</span><span><b>האופק הרחוק.</b> הקווקז האמיתי מאחורי ההר, כולל קזבק. <b>(ההמלצה: כן. שם לקזבק על האופק: בהמשך)</b></span></div>'
          '<div class="q"><span class="qn">3</span><span><b>זה הנוף שהתכוונת אליו?</b> אם חסר משהו (אור דרמטי יותר, יותר סלע, שמיים אחרים), זה הזמן לומר.</span></div></div>')
    hon = ('<div class="card"><h2>ביושר</h2><ul>'
           '<li><b>הפריימים מכלי עיצוב בדפדפן,</b> עם אותו עולם, אותה מצלמה ואותו חישוב צל כמו המנוע של האפליקציה. הם לא צילום מהטלפון, והמודל לא נבדק על טלפון.</li>'
           '<li><b>מקרוב ההר רך:</b> מודל הגובה בתאים של כ-40 מ׳, ולא ממציאים פרטים.</li>'
           '<li><b>המסלולים בפריימים</b> הם סרטים פשוטים של הכלי. באפליקציה: קו האור והחרוזים שאישרת.</li>'
           '<li><b>בלילה</b> הירח בכלי קבוע. באפליקציה הוא אמיתי, ולכן הצללים בלילה ייראו אחרת.</li>'
           '<li><b>פס השעה</b> שבגרסת הבדיקה הוא כלי בדיקה, לא חלק מהמוצר.</li>'
           '<li><b>הארכיטקט</b> אישר את המודל החי עם אור שמחושב בטלפון, ואת חמשת המרקמים הקטנים (0.86 מגה יחד). מדידה בטלפון בינוני חובה לפני שהמסך יוצא מגרסת הבדיקה.</li></ul></div>')
    return board('שלב ב: ההר החי', 1280, H00, wide(1280, H00, hero + lead + '<div class="rowx">' + c1 + c2 + '</div>' + qs + hon))


H00 = 1680


def m01():
    fr = [('dawn', '09:00', 'זריחה על ההר'), ('morning', '10:36', 'בוקר'), ('noon', '13:00', 'צהריים'), ('after', '15:54', 'אחר הצהריים'), ('sunset', '17:21', 'שקיעה'), ('blue', '18:18', 'השעה הכחולה'), ('night', '22:00', 'לילה, אור ירח')]
    row = ''.join(frame(k, 216, '<span class="tm">%s</span>' % t, n) for k, t, n in fr)
    head = '<h1>שבע שעות, אותו מבט</h1><p class="p">מבט הפתיחה ב-12.1.2027, באור של כל שעה. בין השעות האור משתנה ברצף, לפי גובה השמש.</p>'
    return board('שבע שעות, אותו מבט', 1740, 740, wide(1740, 740, head + '<div class="rowx" style="gap: 18px">' + row + '</div>'))


def m02():
    row = frame('east', 300, 'ממזרח', 'אחר הצהריים') + frame('north', 300, 'מצפון, מול השמש', 'צהריים') + frame('close', 300, 'מקרוב, הצד של Tatra', 'בוקר')
    head = '<h1>אותו הר מכל כיוון</h1><p class="p">במודל החי מסובבים, מטים ומתקרבים כמו במפה של היום. האור והאוויר נשארים נכונים מכל מקום.</p>'
    return board('אותו הר מכל כיוון', 1044, 920, wide(1044, 920, head + '<div class="rowx" style="gap: 20px">' + row + '</div>'))


def m03():
    row = frame('plain', 300, 'בלי', 'בלי אדוות, בלי פסי סלע, בלי האופק הרחוק') + frame('morning', 300, 'עם', 'אותו מבט, אותה שעה')
    head = '<h1>מה ההבדל</h1><p class="p">הפרטים הקטנים והאופק הרחוק, דלוקים וכבויים. האור, האוויר והסלע דלוקים בשניהם.</p>'
    return board('מה ההבדל', 724, 920, wide(724, 920, head + '<div class="rowx" style="gap: 20px">' + row + '</div>'))


def phone(title, day, night=None, default_night=False):
    css_extra = ''
    if night:
        imgs = '<img class="d" alt="" src="%s"><img class="n" alt="" src="%s">' % (U(day), U(night))
        props = {'night': {'editor': 'boolean', 'default': default_night}}
        script = "return { mode: (this.props.night ?? %s) ? 'night' : 'day' };" % ('true' if default_night else 'false')
        wrap = '<div class="{{mode}}">%s</div>' % imgs
    else:
        wrap = '<img alt="" src="%s">' % U(day); props = None; script = 'return {};'
    body = ('<div class="MV" style="width: 390px; height: 844px; position: relative; overflow: hidden; background: #0B1220">%s</div>' % wrap)
    b = board(title, 390, 844, body, props, script)
    return b.replace('</style>', '.MV img{position:absolute;left:0;top:0;width:390px;height:844px;display:block}\n.MV .n{display:none}.MV .night .n{display:block}.MV .night .d{display:none}\n</style>')


BOARDS = [
    ('M00-Brief.dc.html', m00, 0, 0, 1280, H00, 'שלב ב: המטרה והשאלות'),
    ('M01-Hours.dc.html', m01, 0, H00 + 380, 1740, 740, '1 · שבע שעות, אותו מבט'),
    ('M02-Free.dc.html', m02, 0, H00 + 1240, 1044, 920, '2 · אותו הר מכל כיוון'),
    ('M03-Compare.dc.html', m03, 1124, H00 + 1240, 724, 920, '3 · מה ההבדל'),
    ('M04-Open.dc.html', lambda: phone('הפתיחה, בגודל טלפון', 'morning', 'night'), 0, H00 + 2600, 390, 844, '4 · הפתיחה, בגודל טלפון (יום ולילה)'),
    ('M05-Dusk.dc.html', lambda: phone('שקיעה, בגודל טלפון', 'sunset'), 470, H00 + 2600, 390, 844, '5 · שקיעה'),
    ('M06-After.dc.html', lambda: phone('אחר הצהריים, בגודל טלפון', 'after'), 940, H00 + 2600, 390, 844, '6 · אחר הצהריים'),
]
NOTES = {'tM': dict(kind='title1', maxW=1600, page='live', w=760, x=0, y=-300, text='שלב ב · ההר החי (המודל התלת-ממדי)'),
         'tM1': dict(kind='title1', maxW=1600, page='live', w=420, x=0, y=H00 + 80, text='א · האור לאורך היום'),
         'tM2': dict(kind='title1', maxW=1600, page='live', w=420, x=0, y=H00 + 940, text='ב · מכל כיוון, ומה ההבדל'),
         'tM3': dict(kind='title1', maxW=1600, page='live', w=420, x=0, y=H00 + 2300, text='ג · בגודל טלפון')}


def main():
    chk = sys.argv[sys.argv.index('--check') + 1] if '--check' in sys.argv else None
    p = os.path.join(PROJ, 'canvas.json'); j = json.load(open(p, encoding='utf-8'))
    for name, fn, x, y, w, h, title in BOARDS:
        s = fn(); open(os.path.join(PROJ, name), 'w', encoding='utf-8').write(s)
        j['boards'][name] = dict(x=x, y=y, w=w, h=h, title=title, page='live')
        if name not in j['order']: j['order'].append(name)
        if chk:
            os.makedirs(chk, exist_ok=True); t = s
            for k, (u, f) in IMG.items(): t = t.replace(u, 'file://' + os.path.join(ROOT, f))
            t = re.sub(r'</?x-dc>|</?helmet>|<script src="./support.js"></script>', '', t).replace('{{mode}}', 'night' if '--night' in sys.argv else 'day')
            open(os.path.join(chk, name.replace('.dc.html', '.html')), 'w', encoding='utf-8').write(t)
    j['notes'].update(NOTES)
    if not any(pg['id'] == 'live' for pg in j['pages']): j['pages'].insert(1, dict(id='live', name='שלב ב · ההר החי'))
    j['launch'] = dict(page='live', view='canvas')
    open(p, 'w', encoding='utf-8').write(json.dumps(j, ensure_ascii=False, indent=1) + '\n')
    print('wrote', len(BOARDS), 'boards')


if __name__ == '__main__': main()
