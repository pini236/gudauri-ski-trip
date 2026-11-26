#!/usr/bin/env python3
"""First-run hints for the Android redesign (26.11.2026, for the store release; review-release.md B4, B8, B9, B10).
One system: one bubble component, one queue on the mountain, one hint inside the village, reset in Settings.
A new canvas page "רמזים לפעם הראשונה". Design only. Handoff: design/handoff/2026-11-26-redesign-hints/.
Usage: python3 design/redesign/tools/hn_boards.py"""
import os, json, datetime
import v56_boards as V
from v56_boards import pic, ic, wb, li, PROJ, top, mtn, P_HOME, P_SRCH, ctl_sw, veil, rings, P_AIM, hold
import v56b_boards as VB
from v56b_boards import dock_a, rc
import peak_boards as PB
from peak_boards import VH, VV, base, tag, P
import gap4_boards as G4
import gap3_boards as G3
from gap3_boards import rl, topbar, CHL
from peak_b4 import EG

PAGE = 'hints'
TODAY = datetime.date(2026, 11, 26)
OPEN = datetime.date(2026, 12, 20)
DAYS = (OPEN - TODAY).days      # 24

HCSS = """
@keyframes hnin{0%{opacity:0;transform:translateY(6px)}100%{opacity:1;transform:none}}
@keyframes hrin{0%{transform:scale(1.18);opacity:0}100%{transform:none;opacity:1}}
.H .hn{position:absolute;border-radius:16px;background:var(--ink);color:var(--bg);padding:10px 14px;box-sizing:border-box;font-size:14px;font-weight:500;line-height:1.35;box-shadow:0 8px 22px rgba(10,25,45,.3);width:max-content;max-width:266px;animation:hnin .2s ease-out both;z-index:5}
.H .hn i{position:absolute;width:12px;height:12px;background:var(--ink);transform:rotate(45deg);border-radius:2px}
.H .hr{position:absolute;box-sizing:border-box;border:2.5px solid var(--ink);box-shadow:0 0 0 2.5px rgba(255,255,255,.9),0 6px 18px rgba(10,25,45,.25);animation:hrin .6s ease-out both;pointer-events:none;z-index:4}
.H .night .hr{box-shadow:0 0 0 2.5px rgba(6,11,22,.75),0 6px 18px rgba(0,0,0,.4)}
.H .hr.run{background:rgba(255,255,255,.3)}
.H .ctile{position:absolute;display:flex;align-items:center;gap:10px;padding:8px 14px 8px 12px;border-radius:18px;box-sizing:border-box}
.H .ctile .cn{font-size:34px;font-weight:300;line-height:1;direction:ltr;font-variant-numeric:tabular-nums;letter-spacing:-.02em}
.H .ctile .c{display:flex;flex-direction:column;gap:1px}
.H .ctile .c b{font-size:13.5px;font-weight:700;white-space:nowrap}
.H .ctile .c span{font-size:12px;color:var(--ink2);white-space:nowrap}
.H .gpill{position:absolute;left:50%;transform:translateX(-50%);height:40px;border-radius:20px;display:flex;align-items:center;padding:0 18px;font-size:16px;font-weight:700;white-space:nowrap}
"""
G4.MB.CSS = G4.MB.CSS + HCSS

T = {  # the hint texts as they are (or will be) in i18n/strings.json
    'run': 'נגיעה במסלול מדגישה אותו.',                       # app.hint_run (exists)
    'find': 'כל המסלולים במקום אחד, לפי צבע.',                # app.hint_find (exists)
    'rec': 'הקלטת גלישה: מהירות, ירידות ומסלולים.',            # app.hint_rec (new)
    'village': 'בכפר: הטיול, הקבוצה וכרטיס הסקי.',            # app.hint_village (exists)
    'mode': 'תמונה של ההר, או הר חי שאפשר לסובב.',            # app.hint_mode (new)
    'me': 'אני: חשבון, שפה והגדרות.',                        # app.hint_me (new)
    'group': 'מזמינים חברים וקובעים איפה נפגשים.',           # app.hint_group (new)
}
EN = {'rec': 'Record skiing: speed, descents and runs.'}

P_BULB = '<path d="M9 18h6M10 21h4"/><path d="M12 3a6 6 0 0 0-3.600 10.800c.8.600 1.100 1.300 1.100 2.200h5c0-.9.300-1.600 1.100-2.200A6 6 0 0 0 12 3z"/>'


# ---------------------------------------------------------------- the one component
def ring(x, y, w, h, r=None, run=False):
    """the mark around the target: 5 outside the control, its own shape"""
    r = h / 2 if r is None else r
    return '<div class="hr%s" style="left: %.0fpx; top: %.0fpx; width: %.0fpx; height: %.0fpx; border-radius: %.0fpx"></div>' % (
        ' run' if run else '', x, y, w, h, r)


def bubble(text, side, ax, frm='left', **pos):
    """the bubble; it takes its own width (up to 266) and is placed by any of left/right/top/bottom.
    side = where the point is (down, up, left, right); ax = the point's centre from the bubble's edge frm"""
    pt = {'down': 'bottom: -5px', 'up': 'top: -5px', 'left': 'left: -5px', 'right': 'right: -5px'}[side]
    pt += '; %s: %dpx' % (frm, ax - 6)
    st = '; '.join('%s: %.0fpx' % (k, v) for k, v in pos.items())
    return '<div class="hn" role="status" style="%s">%s<i style="%s"></i></div>' % (st, text, pt)


# ---------------------------------------------------------------- the mountain screen as built (RX3, Stage, before the season)
def season_top():
    return top(text='פתיחה מתוכננת 20.12.2026 · לפי <bdi>MTA</bdi>', eg=False, dot='sd q')


def tile(days=DAYS):
    """B8: the countdown as a small framed tile on the start side, above the bar; never covers the mountain's runs"""
    return ('<button class="ctile glass" aria-label="%d ימים לפתיחה, 20.12" style="right: 12px; top: 636px"><span class="cn">%d</span>'
            '<span class="c"><b>ימים לפתיחה</b><span>הרכבלים עולים ב-<bdi>20.12</bdi></span></span></button>' % (days, days))


def screen(light='morning', pre=True, mode='pic'):
    m = (pic('hero', 'morning', 'night') + base('hero', cabins=True)) if light == 'morning' else mtn(light)
    o = m + (season_top() if pre else top()) + ctl_sw(mode) + dock_a(rc())
    if pre:
        o += tile()
    return o


def run_hint():
    R = P.run_path(VH, 'Goodaura 1'); x, y, *_ = P.at(R, R['cum'][-1] * 0.45)
    return ring(x - 23, y - 23, 46, 46, run=True) + bubble(T['run'], 'down', 60, 'left', left=x - 60, bottom=844 - (y - 23 - 12))


# targets measured on RX1/OP1: record 17,780,44,44 · find 69,780,207,44 · village 284,780,89,44
# switch 12,566,46,90 · me 12,52,44,44
def find_hint():
    return ring(64, 775, 217, 54) + bubble(T['find'], 'down', 110, 'left', left=62, bottom=844 - 763)


def rec_hint():
    return ring(12, 775, 54, 54) + bubble(T['rec'], 'down', 27, 'left', left=12, bottom=844 - 763)


def village_hint():
    return ring(279, 775, 99, 54) + bubble(T['village'], 'down', 50, 'right', right=12, bottom=844 - 763)


def mode_hint():
    return ring(7, 561, 56, 100) + bubble(T['mode'], 'left', 22, 'top', left=75, top=589)


def me_hint():
    return ring(7, 47, 54, 54) + bubble(T['me'], 'up', 22, 'left', left=12, top=113)


# ---------------------------------------------------------------- the village, its sheet, and the group hint
def village(night=False):
    light = 'night' if night else 'morning'
    b = pic('vill', light, None) + base('vill')
    gx, gy, _, gz = P.pt(VV, *P.lift_ends(P.flift('Goodaura'))[0]); sx_, sy_, _, _ = P.pt(VV, *P.lift_ends(P.flift('Soliko'))[0])
    zx, zy, _, _ = P.pt(VV, *P.lift_ends(P.flift('Zuma'))[0])
    b += tag(gx + 30, gy - 58, 'Goodaura ↑', None) + tag(sx_ - 28, sy_ + 30, 'Soliko ↑', None) + tag(zx - 8, zy + 24, 'Zuma', None)
    b += '<div class="tag" style="left: 62px; top: 76px; height: 32px; border-radius: 16px; padding: 0 12px; direction: rtl; font-size: 13px">↑ חזרה להר</div>'
    b += ('<div class="glass" style="position: absolute; left: 0; right: 0; bottom: 0; height: 296px; border-radius: 28px 28px 0 0; padding: 10px 18px 14px; box-sizing: border-box; display: flex; flex-direction: column; gap: 4px">'
          '<div class="grab"></div><div style="margin: 6px 0 4px"><p class="ttl">הכפר</p><p class="sub"><span dir="ltr">New Gudauri</span> · <span dir="ltr">%s</span> מ׳ · 41 בניינים ממופים</p></div>'
          '<div class="rw"><span style="font-weight: 700">כרטיס סקי</span><span class="r">4 ימים · 390 ₾</span></div>'
          '<div class="rw"><span style="font-weight: 700">אוכל ושתייה</span><span class="r">15 מקומות עד 250 מ׳ מהבסיס</span></div>'
          '<div class="rw"><span style="font-weight: 700">החבר׳ה</span><span class="r">הזמנה ומפגש</span></div>'
          '<div class="rw"><span style="font-weight: 700">ערב בבקתה</span><span class="r">5 משחקים</span></div></div>') % ('{:,}'.format(int(round(gz / 10) * 10)))
    # the row "החבר׳ה" sits at about y 734..780 (sheet top 548; header; two rows)
    b += ring(8, 731, 374, 52, r=16) + bubble(T['group'], 'down', 44, 'right', right=12, bottom=844 - 719)
    return b


# ---------------------------------------------------------------- Settings, with the reset row
def settings():
    o = pic('hero', 'morning', 'night') + veil(0, 844, 'var(--veil)') + topbar('הגדרות')
    o += ('<div class="glass box" style="position: absolute; left: 12px; right: 12px; top: 112px">' + rl(ic(G3.P_SND, 20), 'צליל', 'בכרטיס הטיסה ובמשחקים', G3.sw(True))
          + rl(ic(G3.P_VIB, 20), 'רטט', '', G3.sw(True)) + '</div>')
    o += ('<div class="glass box" style="position: absolute; left: 12px; right: 12px; top: 238px">' + rl(ic(G3.P_SUN, 20), 'תצוגה', 'אוטומטי: לפי השעה בגודאורי', 'אוטומטי' + CHL)
          + rl(ic(G3.P_GLOBE, 20), 'שפה', 'לפי הטלפון', 'עברית' + CHL) + '</div>')
    o += ('<div class="glass box" style="position: absolute; left: 12px; right: 12px; top: 364px">' + rl(ic(G3.P_CHART, 20), 'מדידת שימוש ודיווח שגיאות', 'בלי שם ובלי עוגיות. עוזר לנו לתקן תקלות.', G3.sw(True))
          + rl('', 'מה נאסף', '', CHL) + '</div>')
    o += ('<div class="glass box" style="position: absolute; left: 12px; right: 12px; top: 504px">' + rl(ic(P_BULB, 20), 'להציג שוב את הרמזים', '', CHL)
          + rl(ic(G3.P_UNDO, 20), 'איפוס השיאים במשחקים') + '</div>')
    return o


# ---------------------------------------------------------------- B9: the search screen, its one line in a pill
def search(night=False, pill=True):
    light = 'night' if night else 'morning'; cx, cy = 195, 360
    o = pic('hero', light, None) + veil(0, 844, 'var(--veil)') + rings(cx, cy)
    o += '<div class="gsc glass" style="left: %dpx; top: %dpx">%s</div>' % (cx, cy, ic(P_AIM, 40, 1.6))
    if pill:
        o += '<div class="gpill glass" style="top: %dpx">מחפש מיקום</div>' % (cy + 132)
    else:
        o += '<div class="gst" style="top: %dpx"><b>מחפש מיקום</b></div>' % (cy + 150)
    return o + hold()


# ---------------------------------------------------------------- English, left to right
def en_screen():
    o = mtn() + top(text='Planned opening 20.12.2026 · by <bdi>MTA</bdi>', eg=False, dot='sd q').replace('left: 64px', 'left: 12px; right: 64px')
    o = o.replace('left: 12px; padding: 0', 'right: 12px; left: auto; padding: 0')
    o += ctl_sw('pic').replace('class="mc"', 'class="mc" style="left: auto; right: 12px"')
    o += ('<div class="dock3 glass"><button class="vl">%s Village</button><div class="find">%s<span>Where to ski?</span></div>%s</div>'
          % (ic(P_HOME, 20), ic(P_SRCH, 18), rc().replace('aria-label="%s"' % VB.WORD, 'aria-label="Record skiing"')))
    o += ('<button class="ctile glass" style="left: 12px; top: 636px"><span class="cn">%d</span>'
          '<span class="c"><b>days to opening</b><span>Lifts start <bdi>20.12</bdi></span></span></button>' % DAYS)
    o += ring(324, 775, 54, 54) + bubble(EN['rec'], 'down', 27, 'right', right=12, bottom=844 - 763)
    return o


def phones():
    return {
        'HN1.dc.html': ('הפתיחה הראשונה, לפני העונה: הרמז על המסלול, והספירה במסגרת', screen() + run_hint(), None),
        'HN2.dc.html': ('אחרי שנסגר הגיליון של המסלול: "לאן יורדים?"', screen() + find_hint(), None),
        'HN3.dc.html': ('הפתיחה השנייה: כפתור ההקלטה', screen() + rec_hint(), None),
        'HN4.dc.html': ('אחרי פעולה: הכפר', screen() + village_hint(), None),
        'HN5.dc.html': ('הפתיחה השלישית: תמונה או חי, בלילה', screen('night') + mode_hint(), 'night'),
        'HN6.dc.html': ('אחרי פעולה: "אני"', screen() + me_hint(), None),
        'HN7.dc.html': ('בכניסה הראשונה לכפר: החבר׳ה', village(), None),
        'HN8.dc.html': ('באנגלית: כפתור ההקלטה, משמאל לימין', en_screen(), 'day', 'en'),
        'HN9.dc.html': ('הגדרות: "להציג שוב את הרמזים"', settings(), None),
        'HN10.dc.html': ('חיפוש מיקום: השורה בתוך גלולה (ב9)', search(), None),
    }


HB = 1500


def hn00():
    rows = [
        ('1', 'מסלול כחול באמצע המסך (טבעת על הקו)', 'פתיחה 1, 1.2 שנ׳ אחרי שההר עלה', T['run'], '<span dir="ltr">app.hint_run</span>', 'קיים, בנוי'),
        ('2', '"לאן יורדים?"', 'פתיחה 1, אחרי שנסגר גיליון של מסלול', T['find'], '<span dir="ltr">app.hint_find</span>', 'קיים'),
        ('3', 'כפתור ההקלטה (RX3)', 'פתיחה 2', T['rec'], '<span dir="ltr">app.hint_rec</span>', '<b>חדש</b>'),
        ('4', '"הכפר"', 'פתיחה 2, אחרי פעולה', T['village'], '<span dir="ltr">app.hint_village</span>', 'קיים'),
        ('5', 'המתג תמונה / חי', 'פתיחה 3', T['mode'], '<span dir="ltr">app.hint_mode</span>', '<b>חדש</b>'),
        ('6', '"אני"', 'פתיחה 3, אחרי פעולה', T['me'], '<span dir="ltr">app.hint_me</span>', '<b>חדש</b>'),
        ('כ', 'השורה "החבר׳ה" בגיליון הכפר', 'בפעם הראשונה שהגיליון נפתח, למי שאין קבוצה', T['group'], '<span dir="ltr">app.hint_group</span>', '<b>חדש</b>'),
    ]
    tbl = ('<table style="border-collapse: collapse; width: 100%; font-size: 14.5px; line-height: 1.45"><tr style="color: #56606C; font-size: 13px">'
           '<td style="padding: 6px 10px">#</td><td style="padding: 6px 10px">על מה מצביע</td><td style="padding: 6px 10px">מתי</td><td style="padding: 6px 10px">הטקסט</td><td style="padding: 6px 10px">המפתח</td><td style="padding: 6px 10px"></td></tr>'
           + ''.join('<tr style="border-top: 1px solid #D9DEE4">%s</tr>' % ''.join('<td style="padding: 8px 10px; vertical-align: top">%s</td>' % c for c in r) for r in rows) + '</table>')
    rules = li('<b>אחד בכל פעם, ועד שניים בפתיחה.</b> הראשון 1.2 שנ׳ אחרי שהמסך שקט. השני רק אחרי שהמשתמש עשה משהו וחזר להר (סגר גיליון, בחר מסלול). כך ששת הרמזים נגמרים בשלוש פתיחות.',
               '<b>פתיחה</b> = הפעלה מאפס, או חזרה לאפליקציה אחרי 30 דק׳ ומעלה.',
               '<b>מסך שקט:</b> ההר צויר, מבט הפתיחה (לא צד Kobi), בלי גיליון ובלי חלון, לא בהקלטה, לא בסרט ולא ב"היום בדקה", המצלמה לא זזה, ואין נגיעה ב-1.2 השניות האחרונות.',
               '<b>מה שכבר השתמשו בו לא מקבל רמז:</b> מי שבחר מסלול, פתח את המדף, נגע בהקלטה, נכנס לכפר, החליף מצב או פתח את "אני" לפני התור, הרמז שלו מסומן כנראה בשקט.',
               '<b>נעלם:</b> בפעולה עצמה (נגיעה ביעד), בנגיעה בבועה, בכל נגיעה אחרת במסך, או אחרי 8 שנ׳. בכל אחד מהם הוא לא חוזר לעולם (נשמר בטלפון, בלי מדידה).',
               '<b>לא מופיע</b> כשהאפליקציה נפתחה מקישור (הזמנה, מפגש) או מהתראה, עד הפתיחה הבאה.',
               '<b>הרמז בכפר</b> מחוץ לתור: 600 מ״ש אחרי שהגיליון נעצר, פעם אחת. רק למי שאין קבוצה.',
               '<b>הרמז על המתג</b> נחשב גם כשמופיעה הגלולה הקיימת "סיבוב והטיה זמינים רק במצב חי" (Z03): אחרי אחד מהם, השני לא מופיע.',
               '<b>איפוס:</b> בהגדרות, "להציג שוב את הרמזים". הנגיעה מאפסת את כולם וחוזרת להר, והרמז הראשון מופיע אחרי 1.2 שנ׳. התקנה מחדש מאפסת גם היא. החלפת שפה לא.')
    comp = li('<b>בועה:</b> רדיוס 16, רקע <span dir="ltr">ink</span>, טקסט בצבע <span dir="ltr">bg</span>, 14 במשקל 500, ריפוד 10 ו-14, ברוחב התוכן ועד 266 (בטקסט גדול עד שלוש שורות), צל <span dir="ltr">0 8 22 rgba(10,25,45,.3)</span>.',
              '<b>חוד 12</b> שמצביע על מרכז היעד, 12 מהבועה עד הטבעת; לפחות 14 מפינת הבועה. הבועה לפחות 12 מקצה המסך.',
              '<b>איפה הבועה:</b> מעל היעד. יעד ברבע העליון: מתחת. יעד בעמודת הכפתורים: לצדו, לכיוון מרכז המסך.',
              '<b>טבעת סביב היעד:</b> 5 מחוץ לפקד, באותה צורה (עיגול, גלולה, שורה עם רדיוס 16): מסגרת 2.5 בצבע <span dir="ltr">ink</span> (כמו הבועה) וקו 2.5 לבן מבחוץ (בלילה כהה). על מסלול: עיגול 46, ובתוכו מילוי לבן 30%.',
              '<b>תנועה:</b> הבועה עולה 6 ונגלית ב-200 מ״ש; הטבעת מתכווצת פעם אחת מ-1.18 ב-600 מ״ש. בלי פעימה חוזרת. בהפחתת תנועה: הופעה בלבד.',
              '<b>קורא מסך:</b> נקרא פעם אחת, בנימוס; בלי 8 השניות; פעולה "סגירת הרמז" (<span dir="ltr">app.hint_close_a11y</span>).',
              '<b>משמאל לימין:</b> הכל מתהפך, וגם צד הבועה ליד העמודה.')
    rel = li('<b>ב4, הרמזים:</b> כל המערכת כאן. שלושה מפתחות קיימים, ארבעה חדשים.',
             '<b>ב8, לפני העונה:</b> הספירה היא אריח קטן במסגרת, בצד ההתחלה מעל הסרגל, והר מלא מאחוריו. הרמז הראשון מופיע כרגיל. נגיעה באריח פותחת את הגיליון הקיים (התאריך, הטיול וכרטיס הסקי), בלי המספר הגדול; משיכה למטה סוגרת, והאריח נשאר. זה הביניים עד סבב "לפני העונה".',
             '<b>ב9, טקסט בלי מסגרת:</b> הכלל: כל טקסט על ההר יושב במסגרת (גלולה, אריח, גיליון או בועה). כותרת של עמוד שלם עם רקע מעומעם (הגדרות, אודות, הספירה המלאה וכרטיס הסקי) מותרת. לכן: "מחפש מיקום" עובר לגלולה (HN10), והספירה על ההר עוברת לאריח (HN1).',
             '<b>ב10, כפתורים בלי מילים:</b> נפתר ברמזים 3, 5 ו-6. הכפתורים נשארים בלי מילים.')
    asks = li('<b>לאשר את שבעת הרמזים, הסדר והטקסטים?</b> (ההמלצה: כן)',
              '<b>נגיעה באריח הספירה פותחת את גיליון העונה,</b> או אריח בלי פעולה? (פותחת: כל מה שבמסגרת נראה נגיע)',
              '<b>"מחפש מיקום" בגלולה?</b> הלוח GS1 אושר בלי מסגרת. (בגלולה, כדי שהכלל יהיה אחד)')
    inner = ('<p class="sec">רמזים לפעם הראשונה · לגרסה בחנות · 26.11.2026 · ממתין לפיני</p>'
             '<h1>מערכת רמזים אחת: משתמש חדש מבין את האפליקציה בלי מדריך</h1>'
             '<p class="lead">שישה רמזים על ההר, אחד בכפר, כולם באותה בועה עם חוד וטבעת סביב היעד. בלי מסכי הדרכה ובלי טקסט צף. גם הדברים שלא רואים מההר (הקבוצה, המפגש, החשבון והשפה) מקבלים רמז.</p>'
             '<div class="card"><h2>הרמזים</h2>%s</div>'
             '<div class="rowx" style="align-items: stretch"><div class="card" style="flex: 1.15"><h2>מתי מופיע ומתי נעלם</h2>%s</div>'
             '<div class="card" style="flex: 1"><h2>הרכיב, אחד לכולם</h2>%s</div></div>'
             '<div class="rowx" style="align-items: stretch"><div class="card" style="flex: 1.15"><h2>מה זה סוגר בדוח המוכנות</h2>%s</div>'
             '<div class="card" style="flex: 1"><h2>לפיני</h2>%s<p class="cap">חמש מחרוזות חדשות (ארבעה רמזים ו"להציג שוב את הרמזים") נשלחו לסשן התוכן; האריח משתמש במחרוזות הקיימות של הספירה. נמדד בכלי העיצוב, לא בטלפון. המסירה: <span dir="ltr">design/handoff/2026-11-26-redesign-hints/</span></p></div></div>'
             % (tbl, rules, comp, rel, asks))
    wb('HN00-Hints.dc.html', 'רמזים לפעם הראשונה', 1280, HB, inner)


def main():
    ph = phones()
    for fn, v in ph.items():
        title, body, fx = v[:3]; lang = v[3] if len(v) > 3 else 'he'
        PB.phone(fn, title, body, night=fx == 'night', fixed=fx, lang=lang)
    hn00()
    p = os.path.join(PROJ, 'canvas.json'); j = json.load(open(p, encoding='utf8'))
    for fn in list(j['boards']):
        if fn.startswith('HN'): del j['boards'][fn]
    j['order'] = [f for f in j['order'] if not f.startswith('HN')]
    place = [('HN00-Hints.dc.html', 0, 0, 1280, HB, 'רמזים לפעם הראשונה')]
    y = HB + 140
    names = [('HN1', 'פתיחה 1: המסלול, והספירה באריח'), ('HN2', 'פתיחה 1: "לאן יורדים?"'), ('HN3', 'פתיחה 2: ההקלטה'),
             ('HN4', 'פתיחה 2: הכפר'), ('HN5', 'פתיחה 3: תמונה או חי, בלילה'), ('HN6', 'פתיחה 3: "אני"')]
    for i, (c, t) in enumerate(names):
        place.append(('%s.dc.html' % c, 470 * i, y, 390, 844, t))
    y += 844 + 200
    for i, (c, t) in enumerate([('HN7', 'בכפר: החבר׳ה'), ('HN8', 'באנגלית: ההקלטה'), ('HN9', 'הגדרות: להציג שוב'), ('HN10', 'חיפוש מיקום בגלולה (ב9)')]):
        place.append(('%s.dc.html' % c, 470 * i, y, 390, 844, t))
    for fn, x, y_, w, h, t in place:
        code = fn.split('.dc.html')[0].split('-')[0]
        j['boards'][fn] = dict(x=x, y=y_, w=w, h=h, title='%s (%s)' % (t, code), page=PAGE)
        j['order'].append(fn)
    if not any(pg['id'] == PAGE for pg in j['pages']):
        j['pages'].insert(0, dict(id=PAGE, name='רמזים לפעם הראשונה'))
    j['notes']['t' + PAGE] = dict(kind='title1', maxW=1600, page=PAGE, x=0, w=1000, y=-200, text='רמזים לפעם הראשונה (26.11.2026)')
    open(p, 'w', encoding='utf8').write(json.dumps(j, ensure_ascii=False, indent=1) + '\n')
    print('ok', len(ph))


if __name__ == '__main__':
    main()
