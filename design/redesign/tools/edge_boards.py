#!/usr/bin/env python3
"""Two boards (25.11.2026), for Pini's notes on 0.1.867 via the manager:
  Y01  the edge of the live model: "I don't like that it is cut, as if the model floats" (alternatives A, B, C)
  Z00  the picture and the live model as two views of the same mountain: where to switch, what opens, what has no
       counterpart in the picture; with the phone boards Z01 to Z04
The frames of Y01 are from the design tool of the live model (live_ref.js, with ring=1 / cloud= / edge= added for this
board), in design/redesign/assets/edge/frames, uploaded to the canvas as assets; the urls are below.
  python3 design/redesign/tools/edge_boards.py      write the boards and update canvas.json
The frames (FROM_DISK=1 node design/redesign/tools/live_ref.js <dir> name=query ...):
  now-far-*   light=<m|s|n>&dist=12500                              today: the model alone, zoomed out to the limit
  A-open-*    light=..&ring=1&far=0                                  A: the real terrain around it, 30 km each way
  A-far-*     light=..&dist=12500&ring=1&far=0 ; A-far26-m the same with pitch=26 ; A-north yaw=180 tx=-60 tz=-2300 dist=12000 pitch=20
  B-*         light=..[&dist=12500]&cloud=2150&edge=1500             B: a sea of cloud at 2,150 m, the last 1.5 km of the model fade
  C-open-*    light=..&pitch=18 ; C-max-m light=morning&pitch=30&dist=9000      C: the camera never sees past the model
"""
import json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from live_boards import board, wide, PROJ, ROOT
import gap4_boards as G4
from gap4_boards import phone, pic, top, dock, ic, P_LAY, P_LOC, P_CMP

IMG = {'now-far-m': 'a7dc436ee025fedaf84a37e8cb6d7b5d', 'now-far-s': '76c704de5ee66b00c95b1a6f3bf1bfbc', 'now-far-n': '0bd5cd8f74b10e71ae247574d1fb64c5',
       'A-open-m': 'ea45b4820d533936d51c1d72d74dfc12', 'A-open-n': 'ae310bd42c8f168d70fb2761b5171540', 'A-far-m': 'e8d523f03b4d8dd641129cd484444ff5',
       'A-far-n': '5b9d94592223053141f9327da0350823', 'A-far-s': 'a0e5bfbbe182152c65f8104a62528e9b', 'A-north': '7116a99e682a0e6191403401d9c1ab89',
       'A-far26-m': '9c00abaa1e55b4242d631deafb146412', 'B-open-m': 'db469f4367580e40b756315fddca5cda', 'B-far-m': '9e2b1e2bcb37f93159e8c09128a6f793',
       'B-far-n': '16c4edf6343e1dc455659549ecefc587', 'C-open-m': '84f4f99881c3f9b666743ff2a63dc591', 'C-open-n': '4f7379e12ab6a8cb2f606da2c4a44083',
       'C-max-m': '03c6cde8d1b59bb7d3b7ebad1759e35e'}
U = lambda k: '/_blob/' + IMG[k]
REC = '; border: 3px solid #6B3FD4'


def shot(k, w, cap, sub='', mark=''):
    h = round(w * 844 / 390)
    return ('<div class="fr" style="width: %dpx"><div class="th" style="width: %dpx; height: %dpx%s"><img alt="" src="%s"></div><p class="cap"><b>%s</b>%s</p></div>'
            % (w, w, h, mark, U(k), cap, ('<br>' + sub) if sub else ''))


def li(*xs): return '<ul>' + ''.join('<li>%s</li>' % x for x in xs) + '</ul>'


HY = 3340


def y01():
    now = (shot('now-far-m', 200, 'היום, בוקר', 'מתרחקים עד הסוף (12.5 ק״מ)') + shot('now-far-s', 200, 'היום, שקיעה', 'הקצה החתוך מעל רקע ורוד')
           + shot('now-far-n', 200, 'היום, לילה'))
    why = ('<div class="card" style="flex: 1"><p class="sec">מה קורה</p><h2>ההר נגמר, והמצלמה רואה מעבר לו</h2>' + li(
        '<b>מודל הגובה הוא ריבוע של 15.6 על 15.6 ק״מ</b> סביב אזור הסקי. מה שמחוץ לו לא מצויר, ולכן רואים את הקיר שלו ואת צבע האופק מתחתיו.',
        '<b>במבט הפתיחה המצלמה עומדת כ-1.4 ק״מ דרומית לקצה הדרומי,</b> ורואה אותו בתחתית המסך. בטלפון של האמולטור זה בדיוק נכנס; בטלפון גבוה יותר, או כשמתרחקים, שליש המסך התחתון נשאר ריק, כמו בצילום שלך.',
        '<b>הקצה הדרומי הוא העמק</b> (1,350 עד 2,360 מ׳), ולכן הוא נמוך מכל השאר ונראה כמו מדף שנחתך.',
        'הפריימים כאן מכלי העיצוב, עם אותו עולם ואותה מצלמה של המנוע. כך שחזרתי את מה שצילמת.') + '</div>')
    alts = (shot('A-far-m', 230, 'א · הרים אמיתיים מסביב (ההמלצה)', 'השטח שמסביב, 30 ק״מ לכל צד, מאותו מקור פתוח של ההר. קזבק בתוכו.', REC)
            + shot('A-far-n', 230, 'א, בלילה')
            + shot('B-far-m', 230, 'ב · ים עננים', 'ההר עולה מתוך שכבת עננים ב-2,150 מ׳, והקצה נמס באוויר.')
            + shot('B-far-n', 230, 'ב, בלילה'))
    alts2 = (shot('C-open-m', 230, 'ג · המצלמה לא מגיעה לקצה', 'מבט הפתיחה מעט גבוה יותר (18°), ואי אפשר להתרחק יותר ממנו.')
             + shot('C-max-m', 230, 'ג, הכי רחוק שמותר', 'רק בזווית גבוהה (30°, 9 ק״מ): התחתית עדיין בתוך ההר.')
             + shot('C-open-n', 230, 'ג, בלילה'))
    cmp_ = ('<div class="card" style="flex: 1"><h2>שלוש הדרכים, זו מול זו</h2>' + li(
        '<b>א · הרים אמיתיים מסביב.</b> הכי קרוב למה שביקשת: "הרים מסביב עד הנוף". השטח שמסביב בתאים של 240 מ׳ (פי שש מההר עצמו), מואר באותה שמש ובאותו שלג, ונמס באוויר ב-5 הק״מ האחרונים. <b>אין קצה בשום זווית ובשום זום,</b> גם מהצד של Kobi. המחיר: עוד רשת בטלפון (63 אלף נקודות, כמחצית מההר) ועוד קובץ גבהים של כ-90 קילובייט. בתמורה יורדת רצועת האופק הרחוק (8 מגה בזיכרון הגרפי), כי הקווקז וקזבק בתוך הרשת עכשיו.',
        '<b>ב · ים עננים.</b> יפה ודרמטי, אבל זה ציור של מזג אוויר שלא קיים באותו יום, וכך ההר נראה כמו אי. ביום בלי עננים זה לא נכון. הצדדים הגבוהים (המערב, 3,368 מ׳) נשארים מעל העננים, ושם הקצה רק נמס. <b>מתאים כשכבת מזג אוויר כשיש נתון</b> (ים העננים, K03), לא כפתרון לקצה.',
        '<b>ג · מסגור שלא מגיע לקצה.</b> הכי פשוט: בלי נתון חדש. אבל זה בדיוק "כאילו המודל מרחף" מוסתר ולא נפתר: אי אפשר להתרחק, הסיבוב לצד של Kobi צריך כללים משלו, ובטלפון רחב או גבוה צריך לחשב מחדש.') + '</div>')
    rec = ('<div class="card" style="flex: 1.25"><p class="sec">ההמלצה</p><h2>א, ועם כלל אחד של מצלמה</h2><ol>'
           '<li><b>השטח האמיתי מסביב,</b> 30 ק״מ לכל צד, בתאים של 240 מ׳. התפר עם ההר: 900 מ׳ שבהם הגובה עובר בהדרגה; מתחת להר הרשת נמוכה ב-40 מ׳ ומוסתרת.</li>'
           '<li><b>כשמתרחקים, המצלמה עולה מעט</b> (עד 26° במרחק 12.5 ק״מ, במקום 16°). כך התחתית של המסך היא ההר עצמו ולא העמק הרחוק והרך (ראו "מה ממלא את התחתית").</li>'
           '<li><b>רצועת האופק הרחוק יורדת:</b> מה שמעבר ל-30 ק״מ הוא אוויר. מהמבט שלנו הקווקז הגבוה, כולל קזבק, כבר בתוך הרשת.</li>'
           '<li><b>עד שזה בנוי:</b> ג כתיקון זמני (פתיחה ב-18°, ואין התרחקות מעבר לפתיחה), או פתיחה בתמונה (הלוח הבא).</li></ol></div>')
    bottom = (shot('A-open-m', 200, 'א, הפתיחה', 'למטה: העמק שדרומית לכפר, אמיתי') + shot('A-open-n', 200, 'א, הפתיחה בלילה')
              + shot('A-far26-m', 200, 'א, רחוק עם הכלל', 'במרחק 12.5 ק״מ ו-26°: התחתית היא ההר')
              + shot('A-far-s', 200, 'א, שקיעה') + shot('A-north', 200, 'א, מהצד של Kobi', 'אין קצה גם מאחורי ההר'))
    fill = ('<div class="card" style="flex: 1"><p class="sec">ביקשת לשים לב</p><h2>מה ממלא את התחתית במבט הפתיחה</h2>' + li(
        '<b>היום:</b> המדרון שמתחת ל-New Gudauri, ואחריו הקצה. בטלפון גבוה, או כשמתרחקים, הקצה והרקע הוורוד.',
        '<b>עם א:</b> המדרון, ומתחתיו העמק האמיתי שדרומית לכפר, מושלג ובאובך. הוא רך (תאים של 240 מ׳), ולכן לא נותנים לו יותר משליש תחתון של המסך: זה הכלל של המצלמה בהמלצה.',
        '<b>למה לא להוריד את ההר למטה:</b> הגיליון והסרגל התחתון יושבים שם ממילא (84 נק׳ התחתונות), ומה שמתחתם צריך רק להיות שקט, לא ריק.') + '</div>')
    hon = ('<div class="card"><h2>ביושר</h2>' + li(
        '<b>כל הפריימים מכלי העיצוב בדפדפן,</b> לא מהטלפון. את הצילום שלך מ-0.1.867 לא ראיתי; שחזרתי את מה שתיארת בהתרחקות עד 12.5 ק״מ.',
        '<b>הגבהים שמסביב אמיתיים,</b> מאותם אריחי גובה פתוחים של ההר והאופק הרחוק (בתאים של 120 מ׳, מוחלקים ל-240). כיסוי השלג שם לא ידוע: מצויר כמו על ההר, ובלי יער.',
        '<b>קזבק בנתונים הפתוחים 4,981 מ׳</b> (באמת 5,054): הנתונים גסים בפסגות חדות.',
        '<b>ים העננים בב הוא ציור,</b> בלי נתון.',
        '<b>לא נמדד בטלפון</b> כמה הרשת הנוספת עולה בקצב הפריימים. המבצע מאבחן במקביל את הצד הטכני.') + '</div>')
    qs = ('<div class="card"><h2>שאלות אליך</h2>'
          '<div class="q"><span class="qn">1</span><span><b>איך נפטרים מהקצה?</b> א (הרים אמיתיים מסביב), ב (ים עננים) או ג (המצלמה לא מגיעה לקצה). <b>(ההמלצה: א)</b></span></div>'
          '<div class="q"><span class="qn">2</span><span><b>כשמתרחקים, המצלמה עולה מעט,</b> כדי שהתחתית תהיה ההר ולא העמק הרחוק. <b>(ההמלצה: כן)</b></span></div>'
          '<div class="q"><span class="qn">3</span><span><b>ים העננים</b> כשכבת מזג אוויר, רק ביום שהתחזית אומרת שיש. <b>(ההמלצה: אחר כך, כשיש נתון; לא עכשיו)</b></span></div></div>')
    inner = ('<p class="sec">הערה שלך על 0.1.867 · 25.11.2026</p><h1>ההר מרחף: מה עושים עם הקצה</h1>'
             '<p class="lead">"אני לא אוהב שרואים את זה חתוך כאילו המודל מרחף. או שנעשה הרים מסביב עד הנוף, או שנמצא פתרון אחר." '
             'שחזרתי את זה בכלי העיצוב, והכנתי שלוש דרכים, ביום ובלילה. ההמלצה: א, הרים אמיתיים מסביב.</p>'
             '<div class="rowx">%s%s</div>'
             '<h2 style="margin-top: 4px">שלוש דרכים, באותו מרחק (12.5 ק״מ)</h2><div class="rowx">%s</div><div class="rowx">%s%s</div>'
             '<h2 style="margin-top: 4px">א מקרוב: הפתיחה, הכלל, השקיעה, והצד של Kobi</h2><div class="rowx">%s</div>'
             '<div class="rowx" style="align-items: stretch">%s%s</div>%s'
             % (now, why, alts, alts2, cmp_, bottom, rec, fill, hon + qs))
    return board('ההר מרחף: הקצה', 1280, HY, wide(1280, HY, inner))


# ---------------------------------------------------------------- the picture and the live model
P_PIC = '<rect x="3.500" y="5" width="17" height="14" rx="2.500"/><path d="m3.500 16 5-5 4 4 3-3 5 5"/><circle cx="15.500" cy="9.500" r="1.500"/>'
P_CUBE = '<path d="M12 3.500 19.500 7.500v9L12 20.500 4.500 16.500v-9z"/><path d="M4.500 7.500 12 11.500l7.500-4M12 11.500v9"/>'
EXTRA = """
.H .seg{display:inline-flex;height:36px;border-radius:18px;border:1px solid var(--line);background:var(--glass);padding:3px;gap:2px;flex:none;box-sizing:border-box}
.H .seg span{display:inline-flex;align-items:center;gap:6px;padding:0 12px;border-radius:15px;font-size:13px;font-weight:500;white-space:nowrap}
.H .seg span.on{background:var(--ink);color:var(--bg);font-weight:700}
.H .vbar{width:1px;height:24px;background:var(--line);flex:none;align-self:center}
.H .rhint{position:absolute;left:12px;right:12px;top:106px;border-radius:22px;display:flex;align-items:center;gap:10px;padding:8px 8px 8px 14px;box-sizing:border-box;font-size:14px;line-height:1.35}
.H .rhint button{height:36px;border-radius:18px;padding:0 14px;font-size:13.5px;font-weight:700;background:var(--ink);color:var(--bg);border:0;flex:none;margin-inline-start:auto}
.H .live{position:absolute;left:0;top:0;width:390px;height:844px}
.H .ltag{position:absolute;top:106px;right:12px;height:30px;border-radius:15px;padding:0 12px;display:inline-flex;align-items:center;gap:6px;font-size:12.5px;font-weight:700}
"""
G4.MB.CSS = G4.MB.CSS + EXTRA


def live(day, night):
    return '<img class="live d" alt="" src="%s"><img class="live n" alt="" src="%s">' % (U(day), U(night))


def ctl(extra=''):
    return ('<div class="mc">%s<button class="glass on" aria-label="שכבות" style="background: var(--ink); color: var(--bg)">%s</button><button class="glass" aria-label="איפה אני">%s</button></div>'
            % (extra, ic(P_LAY, 20), ic(P_LOC, 20, 1.8, 'none')))


def ctl_plain(extra=''):
    return ('<div class="mc">%s<button class="glass" aria-label="שכבות">%s</button><button class="glass" aria-label="איפה אני">%s</button></div>'
            % (extra, ic(P_LAY, 20), ic(P_LOC, 20, 1.8, 'none')))


def seg(on):
    return ('<span class="seg"><span class="%s">%s תמונה</span><span class="%s">%s חי</span></span>'
            % ('on' if on == 'pic' else '', ic(P_PIC, 16), 'on' if on == 'live' else '', ic(P_CUBE, 16)))


def row(on):
    return ('<div class="chipr" style="position: absolute; right: 12px; left: 12px; top: 106px">%s<span class="vbar"></span>'
            '<span class="ch">בשמש עכשיו</span><span class="ch">פתוח</span><span class="ch">קל ← קשה</span></div>' % seg(on))


def phones():
    out = {}
    b = pic('hero') + G4.base('hero') + top() + row('pic') + ctl() + dock()
    out['Z01-SwitchPic.dc.html'] = ('תמונה או חי · 1 · בשורת השכבות, בתמונה', b)
    b = live('A-open-m', 'A-open-n') + top() + row('live') + ctl() + dock()
    out['Z02-SwitchLive.dc.html'] = ('תמונה או חי · 2 · אותה שורה, בהר החי', b)
    b = (pic('hero') + G4.base('hero') + top() + '<div class="rhint glass">%s<span>סיבוב והטיה עובדים בהר החי</span><button>לעבור לחי</button></div>' % ic(P_CUBE, 20)
         + ctl_plain() + dock())
    out['Z03-RotateHint.dc.html'] = ('תמונה או חי · 3 · מסובבים בתמונה', b)
    b = (pic('hero') + G4.base('hero') + top()
         + ctl_plain('<button class="glass" aria-label="תמונה או חי">%s</button>' % ic(P_CUBE, 20)) + dock())
    out['Z04-SwitchColumn.dc.html'] = ('תמונה או חי · 4 · חלופה: כפתור בעמודה', b)
    return out


HZ = 1400


def z00():
    rules = ('<table style="border-collapse: collapse; width: 100%; font-size: 14.5px; line-height: 1.45">'
             + ''.join('<tr style="border-top: 1px solid #D9DEE4"><td style="padding: 8px 10px; width: 25%%; vertical-align: top"><b>%s</b></td><td style="padding: 8px 10px; vertical-align: top">%s</td></tr>' % r for r in [
                 ('צביטה וגרירה', 'בשתיהן. בתמונה: התקרבות עד פי 3 והזזה בתוך הציור, כמו בשלב א.'),
                 ('מסלול נבחר, הגיליון, הקודם והבא', 'בשתיהן, אותו גיליון. בתמונה קו האור על הציור (K11), בחי על ההר.'),
                 ('עדשות, מדף, "מהתחנה", הכפר', 'בשתיהן. הכפר תמונה ממילא.'),
                 ('מזג אוויר, מפגש, "איפה אני", היום על ההר', 'בשתיהן, באותה הטלה. הנקודות והקווים יושבים על הציור כמו על ההר.'),
                 ('האור לפי השעה', 'בשתיהן. בתמונה שבעה מצבי אור שמתחלפים; בחי רציף.'),
                 ('סיבוב והטיה', '<b>רק בחי.</b> בתמונה, סיבוב בשתי אצבעות לא עושה כלום, ומופיע פעם אחת "סיבוב והטיה עובדים בהר החי" עם "לעבור לחי" (לוח 3). לא עוברים לבד.'),
                 ('מצפן', 'רק בחי (בתמונה הכיוון קבוע).'),
                 ('"לרדת בסרט"', '<b>תמיד בחי.</b> נגיעה בו בתמונה: מעבר רך (300 מ״ש) לחי באותו מסגור, הסרט, ובסוף או בעצירה חוזרים לתמונה.'),
                 ('הצד של Kobi', 'כמו הסרט: בחירת Kobi בתמונה עוברת לחי לצד השני (סעיף 10 במסירה); "צד Kobi" מחזיר לחזית ולתמונה.'),
                 ('טלפון בלי תלת-ממד', 'רק תמונה, ואין מתג. "לרדת בסרט" וסיבוב לא מוצגים, כמו היום.')]) + '</table>')
    where = ('<div class="card" style="flex: 1"><p class="sec">איפה עוברים</p><h2>בשורת השכבות (ההמלצה)</h2>' + li(
        '<b>כפתור השכבות פותח את השורה שמתחת לגלולה.</b> ראשון בה מתג של שני חלקים, "תמונה" ו"חי", ואחריו קו דק והעדשות (לוחות 1 ו-2).',
        '<b>למה שם:</b> זו הבחירה "איך ההר נראה", כמו העדשות. רוב האנשים בוחרים פעם אחת, והבחירה נשמרת בטלפון. בעמודת הכפתורים כבר שלושה (מצפן, שכבות, "איפה אני"), ורביעי צפוף.',
        '<b>המתג לא מכבה עדשה,</b> ועדשה לא מזיזה את המתג. המעבר: הצלבה של 300 מ״ש באותו מסגור; מה שנבחר נשאר נבחר.',
        '<b>החלופה (לוח 4):</b> כפתור רביעי בעמודה, מעל השכבות, שמחליף בנגיעה. מהיר יותר, וקל למצוא, אבל עמוס ולא מסביר את עצמו.') + '</div>')
    default = ('<div class="card" style="flex: 1"><p class="sec">מה נפתח</p><h2>חי, ואם אי אפשר, תמונה</h2>' + li(
        '<b>ההמלצה: פותחים בחי,</b> כי זה מה שביקשת ("אני רוצה מודל חי"), ואחרי שהקצה נפתר (הלוח הקודם, א) אין סיבה להסתיר אותו.',
        '<b>עד שהקצה נפתר: פותחים בתמונה.</b> זה פותר את "המרחף" מיד, בלי לחכות. המתג כבר שם, ומי שרוצה עובר.',
        '<b>אחרי זה: מה שבחרו נשמר.</b> מי שעבר לתמונה פותח בתמונה.',
        '<b>טלפון בלי תלת-ממד, או כשהמודל לא נטען:</b> תמונה, כמו היום.') + '</div>')
    hon = ('<div class="card"><h2>ביושר ושאלות</h2>' + li(
        '<b>שני הלוחות בחי</b> (2) מראים את פריים הפתיחה מכלי העיצוב, עם "א" של הלוח הקודם, בלי המסלולים שמעליו. הם מראים רק איפה המתג יושב.',
        '<b>טקסטים חדשים (הצעה לסשן התוכן):</b> "תמונה", "חי", "סיבוב והטיה עובדים בהר החי", "לעבור לחי". לא הכנסתי אותם לקובץ השפות.',
        '<b>הכלל של הסרט ושל Kobi</b> (לעבור לחי ולחזור) הוא ההגדרה שלי. לא נבדק בטלפון.')
           + '<div class="q" style="margin-top: 6px"><span class="qn">1</span><span><b>איפה המתג?</b> בשורת השכבות, או כפתור בעמודה. <b>(ההמלצה: בשורת השכבות)</b></span></div>'
           '<div class="q"><span class="qn">2</span><span><b>מה נפתח?</b> <b>(ההמלצה: תמונה עד שהקצה נפתר, ואז חי; ומה שבחרו נשמר)</b></span></div>'
           '<div class="q"><span class="qn">3</span><span><b>"לרדת בסרט" ו-Kobi מתוך התמונה</b> עוברים לחי לרגע וחוזרים. <b>(ההמלצה: כן)</b></span></div></div>')
    inner = ('<p class="sec">הבהרה שלך · 25.11.2026</p><h1>תמונה או חי: שני מבטים על אותו הר</h1>'
             '<p class="lead">"לא התכוונתי להוריד את התמונה, אלא שגם בתמונה תהיה כל הפונקציונליות וכל הכפתורים שיש גם במודל." '
             'כאן: איפה עוברים, מה נפתח, ומה קורה במה שאין לו מקבילה בתמונה.</p>'
             '<div class="rowx" style="align-items: stretch">%s%s</div>'
             '<div class="card"><p class="sec">כל מה שעל ההר, בשני המבטים</p>%s</div>%s'
             % (where, default, rules, hon))
    return board('תמונה או חי', 1280, HZ, wide(1280, HZ, inner))


def main():
    for fn, (title, body) in phones().items(): phone(fn, title, body)
    for fn, f in (('Y01-Edge.dc.html', y01), ('Z00-PicLive.dc.html', z00)):
        open(os.path.join(PROJ, fn), 'w', encoding='utf8').write(f())
    p = os.path.join(PROJ, 'canvas.json'); j = json.load(open(p, encoding='utf8'))
    Z0 = HY + 380
    place = [('Y01-Edge.dc.html', 0, 0, 1280, HY, 'ההר מרחף: שלוש דרכים, והמלצה'), ('Z00-PicLive.dc.html', 0, Z0, 1280, HZ, 'תמונה או חי: איפה עוברים, ומה נפתח')]
    for i, fn in enumerate(['Z01-SwitchPic.dc.html', 'Z02-SwitchLive.dc.html', 'Z03-RotateHint.dc.html', 'Z04-SwitchColumn.dc.html']):
        place.append((fn, [0, 470, 940, 1410][i], Z0 + HZ + 140, 390, 844, ['1 · המתג בשורת השכבות, בתמונה', '2 · אותו מתג, בהר החי', '3 · מסובבים בתמונה', '4 · חלופה: כפתור בעמודה'][i]))
    for fn, x, y, w, h, t in place:
        j['boards'][fn] = dict(x=x, y=y, w=w, h=h, title=t, page='edge')
        if fn not in j['order']: j['order'].append(fn)
    if not any(pg['id'] == 'edge' for pg in j['pages']): j['pages'].insert(2, dict(id='edge', name='ההר: הקצה, ותמונה או חי'))
    j['notes']['tY'] = dict(kind='title1', maxW=1600, page='edge', x=0, w=1000, y=-200, text='ההר מרחף, ותמונה או חי (25.11.2026)')
    open(p, 'w', encoding='utf8').write(json.dumps(j, ensure_ascii=False, indent=1) + '\n'); print('boards written:', len(place))


if __name__ == '__main__': main()
