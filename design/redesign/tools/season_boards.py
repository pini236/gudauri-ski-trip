#!/usr/bin/env python3
"""Two asks from Pini (25.11.2026, via the manager), on one canvas page:
  SE  the opening screen before the season ("it has to be stunning, designed and an experience, to draw the user in"):
      three alternatives (A the cabin climbs, B the sign in the sky, C a quiet row), day and night, and a recommendation
  RB  the record button in the bottom bar ("not quite clear what it does ... minimal and part of the bar, but clear to
      someone who never skied"): three alternatives, the first tap and the live state
The mountain, runs, lifts, the opening date (site/data/resorts.json, MTA) and the prices come from the repo; the trip
and the recording are examples and carry the tag. Design only.
Usage: python3 design/redesign/tools/season_boards.py"""
import os, json, datetime
import numpy as np
from peak_boards import *
import peak_boards as PB
import gap4_boards as G4
from gap4_boards import top, ctl, ic, P_HOME, P_SRCH, P_LAY, P_LOC
from peak_b4 import EG, veil, fade
from gap_boards import li

RES = [r for r in json.load(open(os.path.join(P.ROOT, 'site/data/resorts.json')))['resorts'] if r['id'] == 'gudauri'][0]
PR = json.load(open(os.path.join(P.ROOT, 'site/data/prices.json')))
TODAY = datetime.date(2026, 11, 25)
OPEN = datetime.date.fromisoformat(RES['season']['open']); ANN = datetime.date.fromisoformat(RES['season']['announced'])
DAYS = (OPEN - TODAY).days                       # 25
FLY = (datetime.date(2027, 1, 10) - TODAY).days  # 46, the example trip
FRAC = (TODAY - ANN).days / (OPEN - ANN).days    # how far the cabin has climbed: from the announcement to the opening
SKI = 4
PASS = [p for p in PR['passes'] if p['days'] == SKI and p['scope'] == 'gudauri'][0]['adult']
P_FLY = '<path d="M3 15.500 21 9l-2-2-6 2.500L7 5 5.500 6l3 4.500L4.500 12 3 11l-1 1z"/>'
P_PASS = '<rect x="3.500" y="6" width="17" height="12" rx="2.500"/><path d="M9.500 6v12" stroke-dasharray="2 2"/>'
CSS = """
.H .cab{position:absolute;transform:translate(-50%,-30%);width:26px;height:26px;border-radius:6px;background:#F6C445;border:2px solid #22303F;box-sizing:border-box;box-shadow:0 3px 8px rgba(10,25,45,.35)}
.H .cab::after{content:'';position:absolute;left:3px;right:3px;top:3px;height:7px;border-radius:2px;background:#BFE0FF}
.H .night .cab{background:#FFD873;border-color:#C9D5E2;box-shadow:0 0 18px 6px rgba(255,204,110,.45)}
.H .night .cab::after{background:#FFE7A8}
.H .ctag{position:absolute;display:flex;flex-direction:column;align-items:flex-start;gap:4px;padding:10px 14px 10px 14px;border-radius:18px;box-sizing:border-box}
.H .ctag .cn{font-size:50px;font-weight:300;line-height:.9;direction:ltr;font-variant-numeric:tabular-nums;letter-spacing:-.02em}
.H .ctag .c{display:flex;flex-direction:column;gap:1px}
.H .ctag .c b{font-size:14px;font-weight:700;white-space:nowrap}
.H .ctag .c span{font-size:12px;color:var(--ink2);white-space:nowrap}
.H .peek{position:absolute;left:10px;right:10px;bottom:82px;display:flex;gap:8px}
.H .peek .t{flex:1;min-width:0;height:52px;border-radius:18px;display:flex;align-items:center;gap:9px;padding:0 12px;box-sizing:border-box}
.H .peek .t span{display:flex;flex-direction:column;gap:0;min-width:0}
.H .peek .t b{font-size:15px;font-weight:700;white-space:nowrap}
.H .peek .t small{font-size:11.5px;color:var(--ink2);white-space:nowrap}
.H .sign{position:absolute;display:flex;flex-direction:column;align-items:flex-start}
.H .sign .pl{background:#1B6FDB;color:#fff;border-radius:10px;padding:10px 20px 12px 16px;box-sizing:border-box;clip-path:polygon(0 0,100% 0,100% 100%,0 100%);position:relative;box-shadow:0 10px 24px rgba(10,25,45,.30)}
.H .sign .pl::before{content:'';position:absolute;left:0;right:0;top:-1px;height:9px;background:#fff;border-radius:10px 10px 6px 6px;opacity:.95}
.H .sign .cn{font-size:84px;font-weight:300;line-height:.9;direction:ltr;font-variant-numeric:tabular-nums;letter-spacing:-.03em;display:block}
.H .sign b{font-size:17px;font-weight:700}
.H .sign .pole{width:5px;height:96px;background:#22303F;margin-inline-start:30px;border-radius:2px}
.H .night .sign .pl{background:#2B5DB8}.H .night .sign .pole{background:#9FB2CE}
.H .rbw{height:44px;border-radius:22px;background:#E5352B;color:#fff;display:flex;align-items:center;gap:8px;padding:0 16px 0 13px;font-size:15px;font-weight:700;border:0;flex:none;white-space:nowrap}
.H .rbw i{width:12px;height:12px;border-radius:50%;background:#fff;flex:none}
.H .rbw.live i{box-shadow:0 0 0 4px rgba(255,255,255,.35)}
.H .rbw .tm{font-variant-numeric:tabular-nums;direction:ltr;font-weight:500}
"""
G4.MB.CSS = G4.MB.CSS + CSS


def dock(rec='dot'):
    """the bottom bar; rec: dot (today), word (the dot and its word), live (recording, with the time), none"""
    b = {'dot': '<button class="rb rec" aria-label="מקליטים"><span></span></button>',
         'word': '<button class="rbw" aria-label="מקליטים"><i></i>מקליטים</button>',
         'live': '<button class="rbw live" aria-label="מקליטים, שעה ו-24 דקות. נגיעה פותחת את ההקלטה."><i></i><span class="tm">1:24</span></button>'}[rec]
    return ('<div class="dock2 glass"><button class="vl">%s הכפר</button><div class="find">%s<span>לאן יורדים?</span></div>%s</div>'
            % (ic(P_HOME, 20), ic(P_SRCH, 18), b))


def peek():
    return ('<div class="peek"><div class="t glass">%s<span><b>%d ימים לטיסה</b><small>הטיול שלך · 10.1</small></span><span style="margin-inline-start: auto">%s</span></div>'
            '<div class="t glass">%s<span><b><bdi>%d ₾</bdi></b><small>כרטיס סקי · %d ימים</small></span></div></div>' % (ic(P_FLY, 20), FLY, EG, ic(P_PASS, 20), PASS, SKI))


def mountain(light, night, cabins=False):
    """the opening view, the real runs and lifts"""
    return pic('hero', light, night) + base('hero', cabins=cabins)


def waiting(light):
    """before the season: the mountain first; the runs are there, thinner and quieter (they wake up on a touch), the lifts stand still"""
    m = 'night' if light == 'night' else 'day'
    return (pic('hero', light, None) + lay(P.details(VH, m, light)) + lay(P.runs_svg(VH, m, dim=True))
            + lay(P.lifts_svg(VH, m, light, cabins=False)))


def gondola():
    """the cabin on the real Goodaura line, as far up as the days that passed since the announcement"""
    l = P.flift('Goodaura'); x, y, vis, z, q = P.project(VH, l['pts'], lift=11)
    if y[0] < y[-1]: x, y = x[::-1], y[::-1]          # bottom first
    c = np.r_[0, np.cumsum(np.hypot(np.diff(x), np.diff(y)))]; t = FRAC * c[-1]; i = int(np.searchsorted(c, t))
    return float(x[i]), float(y[i]), (float(x[-1]), float(y[-1])), (float(x[0]), float(y[0]))


def season_pill(text, eg=False, dot='sd q'):
    return top(text=text, eg=eg, dot=dot)


# ---------------------------------------------------------------- the opening screen before the season
def alt_a(night):
    light = 'night' if night else 'morning'; m = 'night' if night else 'day'
    gx, gy, tp, bt = gondola()
    l = P.flift('Goodaura'); x, y, vis, z, q = P.project(VH, l['pts'], lift=11)
    if y[0] < y[-1]: x, y = x[::-1], y[::-1]
    k = int(np.argmin(np.hypot(x - gx, y - gy)))
    up = P.pathd(list(zip(x[:k + 1].tolist(), y[:k + 1].tolist())), 1); rest = P.pathd(list(zip(x[k:].tolist(), y[k:].tolist())), 1)
    wire = ('<svg width="390" height="844" viewBox="0 0 390 844" fill="none" stroke-linecap="round" style="position: absolute">'
            '<path d="%s" stroke="%s" stroke-width="7" opacity=".55"/><path d="%s" stroke="#F6C445" stroke-width="3.2"/>'
            '<path d="%s" stroke="%s" stroke-width="2" stroke-dasharray="1 6"/>'
            '<circle cx="%.1f" cy="%.1f" r="6" fill="%s" stroke="#F6C445" stroke-width="2.5"/><circle cx="%.1f" cy="%.1f" r="4.5" fill="#F6C445"/></svg>'
            % (up, '#fff' if m == 'day' else '#000', up, rest, '#fff' if m == 'day' else '#FFE7A8', tp[0], tp[1], '#fff' if m == 'day' else '#0A1120', bt[0], bt[1]))
    o = waiting(light) + lay(wire)
    o += '<div class="cab" style="left: %.0fpx; top: %.0fpx"></div>' % (gx, gy)
    o += ('<div class="ctag glass" style="left: %.0fpx; top: %.0fpx"><span class="cn">%d</span><span class="c"><b>ימים לפתיחה</b><span>הרכבל עולה <bdi>20.12</bdi></span></span></div>'
          % (gx + 22, gy - 70, DAYS))
    o += ('<div class="tag" style="left: %.0fpx; top: %.0fpx; height: 26px; font-size: 12.5px; font-weight: 700"><bdi>Goodaura</bdi> · <bdi>%s</bdi> מ׳</div>'
          % (tp[0] + 10, tp[1] - 30, '{:,}'.format(round(float(P.elev(*P.lift_ends(l)[1]))))))
    o += season_pill('פתיחה מתוכננת %d.%d.%d · לפי <bdi>MTA</bdi>' % (OPEN.day, OPEN.month, OPEN.year))
    o += ctl().replace('class="mc"', 'class="mc" style="bottom: 146px"') + peek() + dock()
    return o


def alt_b(night):
    light = 'morning' if not night else 'night'
    o = waiting(light)
    o += ('<div class="sign" style="right: 26px; top: 400px"><div class="pl"><span class="cn">%d</span><b>ימים לפתיחה</b></div><div class="pole"></div></div>' % DAYS)
    o += season_pill('פתיחה מתוכננת %d.%d.%d · לפי <bdi>MTA</bdi>' % (OPEN.day, OPEN.month, OPEN.year))
    o += ctl().replace('class="mc"', 'class="mc" style="bottom: 146px"') + peek() + dock()
    return o


def alt_c(night):
    light = 'morning' if not night else 'night'
    o = waiting(light)
    o += season_pill('<b style="font-variant-numeric: tabular-nums">%d</b> ימים לפתיחה · <bdi>20.12</bdi>' % DAYS)
    o += ('<div class="glass" style="position: absolute; left: 10px; right: 10px; bottom: 82px; border-radius: 20px; padding: 10px 14px; box-sizing: border-box; display: flex; align-items: center; gap: 10px">'
          '%s<span style="display: flex; flex-direction: column; gap: 1px"><b style="font-size: 15px">הטיול שלך · %d ימים לטיסה</b><span class="sub" style="font-size: 12.5px">10.1 עד 15.1 · 4 ימי סקי · כרטיס סקי <bdi>%d ₾</bdi></span></span><span style="margin-inline-start: auto">%s</span></div>'
          % (ic(P_FLY, 20), FLY, PASS, EG))
    o += ctl().replace('class="mc"', 'class="mc" style="bottom: 150px"') + dock()
    return o


def first_hint():
    """the one-time hint on a run, for someone who opens the app for the first time"""
    R = P.run_path(VH, 'Kudebi 1'); x, y, *_ = P.at(R, R['cum'][-1] * 0.30)
    return ('<div class="hint" style="left: %.0fpx; top: %.0fpx; max-width: 190px">נוגעים במסלול, והוא נדלק מלמעלה עד למטה.<i style="right: 22px; bottom: -6px"></i></div>' % (x - 168, y - 76))


# ---------------------------------------------------------------- the record button
def rec_hint():
    return ('<div class="hint" style="left: 14px; bottom: 86px; max-width: 250px"><b style="display: block; font-size: 15px; margin-bottom: 2px">מקליטים</b>'
            'מהירות, מסלולים וירידות. מכל מקום, בכל שעה.<i style="left: 20px; bottom: -6px"></i></div>')


def first_sheet():
    o = ('<div class="sheet glass" style="gap: 12px"><div class="grab"></div>'
         '<p class="ttl" style="font-size: 23px; line-height: 1.25">מעקב גלישה</p>'
         '<p class="sub" style="font-size: 14.5px">מזהה לבד רכבל, ירידה והפסקה, בשם המסלול.</p>'
         '<div style="display: flex; flex-direction: column; gap: 10px">%s</div>'
         '<div class="seg"><button class="on">כל היום</button><button>ירידה אחת</button></div>'
         '<button class="pri" style="flex: none">מתחילים</button><button class="lnk">לא עכשיו</button></div>')
    rows = ''.join('<div style="display: flex; gap: 12px; align-items: center"><span class="ic" style="background: var(--line); flex: none">%s</span><span style="font-size: 14.5px">%s</span></div>' % (ic(p), t) for p, t in (
        ('<rect x="7" y="3" width="10" height="18" rx="2.500"/><path d="M11 17.500h2"/>', 'עובד גם כשהטלפון בכיס'),
        ('<rect x="5" y="11" width="14" height="9" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/>', 'הכל נשאר בטלפון')))
    return o % rows


def phones():
    out = {}
    for k, f, cap in (('A', alt_a, 'א · הרכבל עולה'), ('B', alt_b, 'ב · השלט בשמיים'), ('C', alt_c, 'ג · שורה שקטה')):
        for night in (False, True):
            b = f(night)
            if k == 'A' and not night: b += first_hint()
            out['SE%s%s.dc.html' % (k, 'N' if night else 'D')] = ('לפני העונה · %s, %s' % (cap, 'בלילה' if night else 'ביום'), b, 'night' if night else 'day')
    # the record button: three ways, the first tap, recording
    m = lambda: mountain('morning', None, cabins=True) + top() + ctl()
    out['RB1.dc.html'] = ('כפתור ההקלטה · א · הנקודה והמילה', m() + dock('word'), 'day')
    out['RB2.dc.html'] = ('כפתור ההקלטה · ב · נקודה ורמז, פעם אחת', m() + dock('dot') + rec_hint(), 'day')
    out['RB3.dc.html'] = ('כפתור ההקלטה · נגיעה ראשונה', m() + dock('word') + veil(0, 844, 'rgba(6,11,22,.38)') + first_sheet(), 'day')
    out['RB4.dc.html'] = ('כפתור ההקלטה · בזמן הקלטה, בלילה', mountain('night', None, cabins=True) + top() + ctl() + dock('live'), 'night')
    return out


def strip(rec, night=False, w=370):
    """the bottom bar alone, over a slice of the mountain"""
    light = 'night' if night else 'morning'
    s = w / 390
    inner = pic('hero', light, None, top=-740) + dock(rec).replace('bottom: 12px', '')
    return ('<div class="th H" style="width: %dpx; height: %dpx; position: relative; overflow: hidden; border-radius: 16px"><div class="%s" style="position: absolute; left: 0; top: 0; width: 390px; height: 104px; transform: scale(%.4f); transform-origin: 0 0">%s</div></div>'
            % (w, round(104 * s), 'night' if night else 'day', s, pic('hero', light, None, top=-740) + dock(rec)))


def strip(rec, night=False, w=280, cap='', sub=''):
    """the bottom bar alone, over the foot of the mountain"""
    light = 'night' if night else 'morning'; s = w / 390
    return ('<div class="fr" style="width: %dpx"><div class="th H" style="width: %dpx; height: %dpx; position: relative; overflow: hidden; border-radius: 16px">'
            '<div class="%s" style="color: var(--ink); position: absolute; left: 0; top: 0; width: 390px; height: 92px; transform: scale(%.4f); transform-origin: 0 0">%s%s</div></div>'
            '<p class="cap"><b>%s</b>%s</p></div>'
            % (w, w, round(92 * s), 'night' if night else 'day', s, pic('hero', light, None, top=-752), dock(rec), cap, ('<br>' + sub) if sub else ''))


from live_boards import board, wide as wbox, PROJ
HS = 1830


def se00():
    saw = ('<div class="card" style="flex: 1"><p class="sec">מה ראית ב-0.1.877</p><h2>ההר חיכה, והמסך לא סיפר את זה</h2>' + li(
        '<b>המצלמה קרובה ונמוכה, וההר שטוח ולבן.</b> זה ההר החי ממרחק קצר, בלי השמיים, האופק והצל שעושים את התמונה.',
        '<b>אין מסלולים.</b> רק קווי רכבלים דקים. למי שפותח בפעם הראשונה אין במה לגעת.',
        '<b>״25״ ענק באמצע ההר,</b> כהה על לבן. המספר מסתיר את מה שהוא מספר עליו.',
        '<b>הגיליון לוקח שליש מסך</b> לשלוש שורות, והגלולה אומרת רק מה שאין.') + '</div>')
    common = ('<div class="card" style="flex: 1.15"><p class="sec">בכל שלוש החלופות</p><h2>ההר במיטבו, והמסלולים כבר שם</h2>' + li(
        '<b>מבט הפתיחה של התמונה</b> (שמיים, רכס, צל, האור של השעה), לא ההר החי מקרוב. כך גם ההמלצה בלוח "תמונה או חי": פותחים בתמונה עד שהקצה נפתר.',
        '<b>המסלולים מצוירים, דקים ושקטים יותר מבעונה,</b> כציפייה. נגיעה במסלול מדליקה אותו כרגיל, עם הגיליון, "לרדת בסרט" והכל. זו התשובה לש3 של הבודק: לפני העונה ההר נפתח עם המסלולים.',
        '<b>הרכבלים עומדים:</b> קו בלי קרונות, כי הם באמת לא נוסעים.',
        '<b>הגיליון יורד.</b> במקומו שני אריחים קטנים מעל הסרגל: "הטיול שלך" וכרטיס הסקי. נגיעה פותחת אותם, כמו בכפר.',
        '<b>רמז אחד בפעם הראשונה</b> על מסלול: "נוגעים במסלול, והוא נדלק מלמעלה עד למטה." (כבר בקובץ השפות). זה מה שמשאיר משתמש חדש: יש במה לגעת, ומה שנדלק יפה.') + '</div>')
    alts = ('<div class="rowx" style="align-items: stretch">'
            '<div class="card" style="flex: 1; border: 3px solid #6B3FD4"><p class="sec">א · ההמלצה</p><h2>הרכבל עולה</h2>' + li(
                '<b>הספירה היא קרון על הקו האמיתי של Goodaura,</b> שעולה עוד קצת כל יום, מההודעה של MTA (28.10) עד הפתיחה (20.12). היום הוא ב-53 אחוז מהדרך.',
                'החלק שכבר עלה בצהוב, מה שנשאר מנוקד, ובתחנה העליונה הגובה האמיתי.',
                'ליד הקרון: המספר הגדול ו"ימים לפתיחה". בלילה הקרון מואר.',
                '<b>למה:</b> זה הרעיון שאישרת בסבב 39 ("הרכבל עולה"), הפעם על ההר עצמו ולא בציור. משתנה כל יום, ולכן יש סיבה לחזור.') + '</div>'
            '<div class="card" style="flex: 1"><p class="sec">ב</p><h2>השלט בשמיים</h2>' + li(
                '<b>שלט מסלול כחול על עמוד, נעוץ בשלג,</b> עם המספר ו"ימים לפתיחה". אותו שלט כמו בשפה של האתר.',
                'ברור מאוד, ונראה מרחוק.',
                '<b>מה חסר:</b> הוא לא זז. המספר משתנה, אבל המסך נראה אותו דבר כל יום, והשלט מסתיר חלק מההר.') + '</div>'
            '<div class="card" style="flex: 1"><p class="sec">ג</p><h2>שורה שקטה</h2>' + li(
                '<b>ההר לבד, והספירה בגלולה:</b> "25 ימים לפתיחה · 20.12". הטיול וכרטיס הסקי בשורה אחת מעל הסרגל.',
                'זה מה שהמלצת אחרי הבודק: ההר עם המסלולים ושורת ספירה קטנה.',
                '<b>מה חסר:</b> נקי ויפה, אבל אין בו חוויה של ספירה; המספר הוא רק מילה.') + '</div></div>')
    states = ('<div class="card"><p class="sec">לאורך הדרך, לפי הכללים של סבב 39</p><h2>מה הקרון עושה בכל מצב</h2>'
              '<table style="border-collapse: collapse; width: 100%; font-size: 14.5px; line-height: 1.45">' + ''.join(
                  '<tr style="border-top: 1px solid #D9DEE4"><td style="padding: 8px 10px; width: 24%%; vertical-align: top"><b>%s</b></td><td style="padding: 8px 10px; vertical-align: top">%s</td></tr>' % r for r in [
                      ('היום (25 ימים)', 'הקרון ב-53 אחוז מהקו. בגלולה: "פתיחה מתוכננת 20.12.2026 · לפי MTA". נגיעה בגלולה או בקרון פותחת את הגיליון של לוח Q10 (הפתיחה, הטיול, כרטיס הסקי).'),
                      ('מחר', 'הקרון כמעט בתחנה העליונה. ליד הקרון "מחר פותחים", ומתחת "הרכבלים עולים ב-20.12" (כבר בקובץ השפות).'),
                      ('יום הפתיחה, בלי דיווח', 'הקרון בתחנה העליונה, עם "היום פותחים · ממתינים לדיווח מהרכבלים".'),
                      ('הגיע דיווח', 'הקרון יורד מהמסך, הרכבלים מקבלים קרונות לפי הדיווח, המסלולים חוזרים לעובי הרגיל, והגלולה "11 מתוך 14 רכבלים פתוחים". זה המסך הרגיל.'),
                      ('עבר התאריך, ועדיין בלי דיווח', 'הקרון נשאר למעלה, עם "הפתיחה הייתה מתוכננת ל-20.12 · אין עדיין דיווח מהרכבלים".'),
                      ('MTA דחו', 'הקרון יורד לנקודה שלפי התאריך החדש, ולידו "הפתיחה נדחתה ל-{תאריך} · לפי הודעת MTA". לא מסתירים שירד.'),
                      ('אחרי העונה, בלי תאריך חדש', 'בלי קרון ובלי ספירה. ההר כמו בתמונה, והגלולה "עוד אין מידע על הרכבלים", כמו היום.')]) + '</table></div>')
    rec = ('<div class="card"><h2>ביושר ושאלות</h2>' + li(
        '<b>אמיתי:</b> ההר, המסלולים, הרכבלים, קו Goodaura והגובה בתחנה העליונה (2,705 מ׳), תאריך הפתיחה וההודעה (site/data/resorts.json, MTA), והמחיר של 4 ימים (390 ₾, MTA, נבדק 15.11.2026).',
        '<b>דוגמה, ומסומן:</b> הטיול (10.1, 46 ימים לטיסה).',
        '<b>מקום הקרון</b> (מההודעה עד הפתיחה) הוא ההגדרה שלי, כמו בסבב 39.',
        '<b>טקסט חדש (הצעה לסשן התוכן):</b> "הרכבל עולה {date}" מתחת למספר. כל השאר כבר בקובץ השפות.',
        '<b>לא נבדק בטלפון.</b> נבדק ברינדור מקומי של הלוחות.')
           + '<div class="q" style="margin-top: 6px"><span class="qn">1</span><span><b>איזו חלופה?</b> א, ב או ג. <b>(ההמלצה: א, הרכבל עולה)</b></span></div>'
           '<div class="q"><span class="qn">2</span><span><b>המסלולים לפני העונה,</b> דקים ושקטים, ונדלקים בנגיעה. <b>(ההמלצה: כן; זו התשובה לש3)</b></span></div>'
           '<div class="q"><span class="qn">3</span><span><b>הגיליון יורד,</b> ובמקומו שני אריחים מעל הסרגל. <b>(ההמלצה: כן)</b></span></div></div>')
    inner = ('<p class="sec">בקשה שלך · 25.11.2026</p><h1>לפני העונה: המסך הראשון</h1>'
             '<p class="lead">"מסך הפתיחה לפני פתיחת העונה צריך להיות הרבה יותר יפה ממה שיש היום, זה צריך להיות מרהיב, מעוצב וחווייתי כדי למשוך את המשתמש." '
             'שלוש חלופות, ביום ובלילה, בלוחות שמתחת. ההמלצה: א, הרכבל עולה.</p>'
             '<div class="rowx" style="align-items: stretch">%s%s</div>%s%s%s' % (saw, common, alts, states, rec))
    return board('לפני העונה: המסך הראשון', 1280, HS, wbox(1280, HS, inner))


HR = 1330


def rb00():
    strips = ('<style>%s</style><div class="rowx" style="gap: 18px; flex-wrap: wrap">' % G4.MB.CSS
              + strip('word', cap='עד ההקלטה הראשונה', sub='הנקודה והמילה "מקליטים"')
              + strip('dot', cap='אחרי ההקלטה הראשונה', sub='רק הנקודה, כמו היום')
              + strip('live', cap='בזמן הקלטה', sub='הנקודה פועמת, והזמן שעבר')
              + strip('live', night=True, cap='בזמן הקלטה, בלילה') + '</div>')
    why = ('<div class="card" style="flex: 1"><p class="sec">מה לא ברור היום</p><h2>נקודה אדומה בלי מילה</h2>' + li(
        '<b>למי שגולש,</b> נקודה אדומה היא "מקליטים". למי שלא גלש ולא ראה אפליקציות סקי, לא ברור מה מקליטים: וידאו? קול?',
        '<b>ההסבר כבר קיים,</b> אבל רק אחרי הנגיעה (הגיליון של L01). מי שלא נוגע לא יודע שיש כאן יום גלישה שמצייר את עצמו.',
        '<b>אהבת שהוא משולב למטה ומינימליסטי.</b> לכן כל החלופות נשארות בסרגל, באותו מקום ובאותו אדום.') + '</div>')
    alts = ('<div class="card" style="flex: 1.25"><p class="sec">שלוש חלופות</p><h2>מה הכפתור אומר</h2>' + li(
        '<b>א · הנקודה והמילה, תמיד.</b> המילה "מקליטים" ליד הנקודה, בסרגל (לוח 1). הכי ברור, אבל תופס מקום מ"לאן יורדים?" לתמיד, גם אחרי שלמדו.',
        '<b>ב · נקודה ורמז, פעם אחת.</b> הנקודה של היום, ובפתיחה הראשונה בועה מעליה: "מקליטים · מהירות, מסלולים וירידות. מכל מקום, בכל שעה." (לוח 2). מינימליסטי, אבל מי שסגר את הרמז נשאר עם נקודה.',
        '<b>ג · לפי מה שכבר יודעים (ההמלצה).</b> המילה עד שההקלטה הראשונה מסתיימת, ואז רק הנקודה. בזמן הקלטה הנקודה פועמת והזמן שעבר לידה, כך שגם מי שחזר להר רואה שזה רץ (לוח 4). בכל מצב, הנגיעה הראשונה פותחת את ההסבר (לוח 3).') + '</div>')
    first = ('<div class="card" style="flex: 1"><p class="sec">הנגיעה הראשונה</p><h2>ההסבר של L01, כמו שאושר</h2>' + li(
        '<b>הגיליון:</b> "מעקב גלישה", שתי שורות (בכיס, הכל בטלפון), "כל היום" או "ירידה אחת", "מתחילים" ו"לא עכשיו".',
        '<b>אחרי "מתחילים":</b> ההסבר של המערכת על מיקום, ואז המסך החי (L02).',
        '<b>"לא עכשיו"</b> לא מעלים את המילה. היא יורדת רק אחרי הקלטה שהסתיימה.',
        '<b>לפני העונה ומחוץ לגודאורי</b> הכפתור עובד אותו דבר ("מכל מקום, בכל שעה"); השמות של המסלולים מופיעים רק על ההר.') + '</div>')
    hon = ('<div class="card"><h2>ביושר ושאלות</h2>' + li(
        '<b>כל הטקסטים כבר בקובץ השפות:</b> "מקליטים", "מהירות, מסלולים וירידות. מכל מקום, בכל שעה.", והגיליון של L01.',
        '<b>הצעה לסשן התוכן:</b> לקורא המסך בזמן הקלטה, "מקליטים, {time}. נגיעה פותחת את ההקלטה."',
        '<b>הרוחב:</b> עם המילה, "לאן יורדים?" מתקצר לכ-140 נקודות ועדיין נכנס בעברית. באנגלית ("Record") קצר יותר; ברוסית ובגאורגית לבדוק, ואם לא נכנס, רק הנקודה.',
        '<b>הדופק</b> כבוי בהפחתת תנועה; הזמן נשאר.',
        '<b>לא נבדק בטלפון.</b>')
           + '<div class="q" style="margin-top: 6px"><span class="qn">1</span><span><b>איזו חלופה?</b> א, ב או ג. <b>(ההמלצה: ג)</b></span></div>'
           '<div class="q"><span class="qn">2</span><span><b>מתי המילה יורדת?</b> <b>(ההמלצה: אחרי ההקלטה הראשונה שהסתיימה)</b></span></div>'
           '<div class="q"><span class="qn">3</span><span><b>הזמן שעבר בסרגל בזמן הקלטה.</b> <b>(ההמלצה: כן)</b></span></div></div>')
    inner = ('<p class="sec">בקשה שלך · 25.11.2026</p><h1>כפתור ההקלטה: ברור, ועדיין מינימליסטי</h1>'
             '<p class="lead">"הכפתור של הקלטה לא לגמרי ברור מה תפקידו, אני אוהב שזה מצד אחד משולב למטה ומינימליסטי, אבל צריך לחשוב על חוויה טובה שגם תהיה ברורה למשתמש, גם כזה שלא גלש ולא ראה אפליקציות סקי קודם." '
             'ההמלצה: ג, המילה עד שיודעים, ואז הנקודה.</p>'
             '<h2 style="margin-top: 4px">ג, לאורך הזמן</h2>%s<div class="rowx" style="align-items: stretch">%s%s%s</div>%s' % (strips, why, alts, first, hon))
    return board('כפתור ההקלטה', 1280, HR, wbox(1280, HR, inner), props={'night': False}, script="return { mode: (this.props.night ?? false) ? 'night' : 'day' };")



# ---------------------------------------------------------------- the live recording screen (Pini, 25.11.2026, on 0.1.8xx "looking for satellites")
import day4 as D
from peak_b4 import IC, you as B4you
LCSS = """
.H .steps{position:absolute;left:12px;right:12px;top:106px;height:44px;border-radius:22px;display:flex;align-items:center;gap:6px;padding:0 14px;box-sizing:border-box;font-size:12.5px;white-space:nowrap}
.H .steps span{display:flex;align-items:center;gap:6px;color:var(--ink2)}
.H .steps span i{width:8px;height:8px;border-radius:50%;border:1.5px solid currentColor;box-sizing:border-box;flex:none}
.H .steps span.on{color:var(--ink);font-weight:700}.H .steps span.on i{background:#E5352B;border-color:#E5352B;box-shadow:0 0 0 4px rgba(229,53,43,.22)}
.H .steps b{flex:1;height:1px;background:var(--line);min-width:8px}
.H .radar{position:absolute;border-radius:50%;transform:translate(-50%,-50%);border:1.5px solid rgba(255,255,255,.75);box-shadow:0 0 18px rgba(255,255,255,.35)}
.H .night .radar{border-color:rgba(190,215,255,.6)}
.H .bars{display:flex;gap:5px;align-items:flex-end;direction:ltr}.H .bars i{width:9px;border-radius:2px;background:var(--ink)}
"""
G4.MB.CSS = G4.MB.CSS + LCSS


def recp(t='0:14', extra=''):
    return '<div class="recp glass"><span class="rdot"></span><span>מקליטים</span><span class="tmr">%s</span><span style="margin-inline-start: auto">%s</span></div>' % (t, extra)


def hold():
    return '<div class="hold glass"><i></i>החזקה לסיום</div>'


def bars(n=2):
    return '<span class="bars">%s</span>' % ''.join('<i style="height: %dpx; opacity: %s"></i>' % (h, '1' if k < n else '.25') for k, h in enumerate((8, 12, 16, 20)))


def search_card(top=560, extra=''):
    return ('<div class="card glass" style="position: absolute; left: 16px; right: 16px; top: %dpx; gap: 8px"><div style="display: flex; align-items: center; gap: 10px">%s<b style="font-size: 16px">מחפשים לוויינים</b><span style="margin-inline-start: auto">%s</span></div>'
            '<div class="sub">הזמן כבר נספר מהנגיעה. המהירות והקו מתחילים כשיש מיקום מדויק, ולא רגע לפני.</div>%s</div>' % (top, IC['sat'], bars(), extra))


def lv_a(night):
    light = 'night' if night else 'morning'
    o = pic('hero', light, None) + veil(0, 844, 'var(--veil)') + recp()
    o += ('<div class="steps glass"><span class="on"><i></i>מחפשים לוויינים</span><b></b><span><i></i>מקליטים</span><b></b><span><i></i>היום על ההר</span></div>')
    o += '<div style="position: absolute; left: 0; right: 0; top: 300px; display: flex; flex-direction: column; align-items: center; gap: 6px"><div class="huge" style="font-weight: 200; font-size: 104px">—</div><div class="unit">קמ״ש</div></div>'
    o += search_card() + hold()
    return o


def ghost(m, a=.42):
    return '<g opacity="%s">%s</g>' % (a, D.day(VH, m, w=2.6, lifts=False))


def lv_b(night):
    light = 'night' if night else 'morning'; m = 'night' if night else 'day'
    o = pic('hero', light, None) + lay(svg(VH, ghost(m)))
    o += ('<div class="tag" style="left: 190px; top: 238px; height: 28px; font-size: 12.5px; font-weight: 700">%s כך ייראה היום שלך על ההר</div>' % EG)
    o += recp() + fade(470, 90) + veil(560, 284, 'var(--bg)')
    o += search_card(top=520, extra='<div style="border-top: 1px solid var(--line); padding-top: 8px; font-size: 14px; line-height: 1.45">בכל ירידה: המהירות ושם המסלול. בסוף היום: כל היום על ההר, וסיפור קצר.</div>')
    o += hold()
    return o


def lv_c(night):
    light = 'night' if night else 'morning'
    vx, vy = 195, 222
    o = pic('hero', light, None) + veil(0, 844, 'var(--veil)') + recp()
    o += ''.join('<div class="radar" style="left: %.0fpx; top: %.0fpx; width: %dpx; height: %dpx; opacity: %s"></div>' % (vx, vy, r, r, op) for r, op in ((150, '.9'), (270, '.5'), (390, '.25')))
    o += ('<div style="position: absolute; left: 0; right: 0; top: 190px; display: flex; flex-direction: column; align-items: center; gap: 10px; text-shadow: 0 0 18px var(--halo)">'
          '<span class="bars" style="transform: scale(1.8); margin-bottom: 14px">%s</span><b style="font-size: 25px">מחפשים לוויינים</b>'
          '<span style="font-size: 15px">תחת שמיים פתוחים זה לוקח עד דקה</span></div>' % ''.join('<i style="height: %dpx; opacity: %s"></i>' % (h, op) for h, op in ((8, '1'), (12, '1'), (16, '.25'), (20, '.25'))))
    o += hold()
    return o


def lv_found(night=False):
    """B, a minute later: the fix came, the example fades, and the line starts where you stand"""
    light = 'night' if night else 'morning'
    x, y, _, _ = P.pt(VH, *P.lift_ends(P.flift('Goodaura'))[0])
    o = pic('hero', light, None) + base('hero', cabins=False) + B4you(x, y)
    o += '<div class="hint" style="left: %.0fpx; top: %.0fpx; max-width: 180px">הירידה הראשונה תצויר מכאן<i style="left: 22px; bottom: -6px"></i></div>' % (x - 20, y - 70)
    o += recp('1:02')
    o += ('<div class="glass" style="position: absolute; left: 12px; right: 12px; top: 106px; border-radius: 22px; padding: 10px 16px; display: flex; align-items: baseline; gap: 10px"><b style="font-size: 18px">עומדים</b>'
          '<span class="sub">מחכים לירידה הראשונה</span></div>') + hold()
    return o


def lv_run():
    """C of the toggle: one descent lives in the run sheet"""
    k = 'Tatra 2'; f = facts(k)
    o = pic('hero') + base('hero', runs='line', skip=None) + lay(svg(VH, glow_run(VH, k, 'day'))) + top()
    o += ('<div class="sheet glass" style="gap: 12px"><div class="grab"></div><div style="display: flex; align-items: center; gap: 10px">%s<span class="ttl" dir="ltr">%s</span><span class="sub" style="margin-inline-start: auto">%s · %s</span></div>'
          '<div class="nums"><div><span class="num">%s</span><span class="cap">אורך</span></div><div><span class="num">%d מ׳</span><span class="cap">ירידה</span></div><div><span class="num">%d°</span><span class="cap">הקטע התלול</span></div></div>'
          '<div style="display: flex; gap: 8px"><button class="sec" style="flex: 1">לרדת בסרט</button><button class="sec" style="flex: 1.5; white-space: nowrap; display: flex; align-items: center; justify-content: center; gap: 8px"><i style="width: 11px; height: 11px; border-radius: 50%%; background: #E5352B; display: inline-block"></i>מודדים ירידה אחת</button></div>'
          '</div>'
          % (sign(k, True), k, HE[f['color']], 'כחול' and 'קל', km(f['len']), round(f['drop']), round(f['steep'])))
    return o


def lv_phones():
    out = {}
    for k, f, cap in (('A', lv_a, 'א · שלושה שלבים'), ('B', lv_b, 'ב · הצצה למה שיתקבל'), ('C', lv_c, 'ג · מחפשים, חי')):
        for night in (False, True):
            out['LV%s%s.dc.html' % (k, 'N' if night else 'D')] = ('המסך החי · %s, %s' % (cap, 'בלילה' if night else 'ביום'), f(night), 'night' if night else 'day')
    out['LVF.dc.html'] = ('המסך החי · ב, אחרי שנמצא מיקום', lv_found(), 'day')
    out['LVR.dc.html'] = ('ירידה אחת · בגיליון המסלול', lv_run(), 'day')
    return out


HL = 1400


def lv00():
    tog = ('<div class="card" style="flex: 1.2"><p class="sec">הבורר "כל היום / ירידה אחת"</p><h2>להוריד אותו מהמסך החי</h2>' + li(
        '<b>היום ההבדל לא נראה:</b> שני המצבים מקליטים, מודדים מהירות ומזהים מסלול. ההבדל היחיד הוא מתי זה נעצר: "ירידה אחת" נעצרת לבד בתחתית, "כל היום" כשמסיימים. את זה אי אפשר להבין מבורר.',
        '<b>וכל היום כבר מודד כל ירידה:</b> על הרכבל מופיע "הירידה שהרגע עשית" (L04), ובסוף היום לכל ירידה כרטיס. מי שרוצה רק את הירידה הזו מסיים בתחתית, ומקבל אותו דבר.',
        '<b>א · בלי בורר בכלל.</b> הכי פשוט. אבל "ירידה אחת" הייתה בקשה שלך (30.10: "שמישהו יוכל להתחיל למדוד מהירות במסלול ספציפי, שלא יפעיל את זה כל היום"), והיא נעלמת.',
        '<b>ב · להשאיר, עם שורה שמסבירה</b> מתחת לכל צד. ברור יותר, אבל שתי בחירות לפני שהתחלת, במסך שצריך להיות שקט.',
        '<b>ג · "ירידה אחת" עוברת לגיליון המסלול (ההמלצה).</b> במסך החי ובגיליון של L01 רק "מקליטים", כלומר כל היום. בגיליון של מסלול נבחר, ליד "לרדת בסרט": "מודדים ירידה אחת", שנעצר לבד בתחתית ונותן כרטיס (לוח 8). שם ברור למה זה: בחרת מסלול, ואתה רוצה לדעת כמה מהר ירדת בו.') + '</div>')
    impact = ('<div class="card" style="flex: 1"><p class="sec">מה זה משנה</p><h2>אם ג מאושר</h2>' + li(
        '<b>L01</b> (הנגיעה הראשונה) ו-<b>L02</b>: בלי הבורר. נשארים הכותרת, שתי השורות, "מתחילים" ו"לא עכשיו". גם הלוח של הנגיעה הראשונה בעמוד הזה (לוח 3 של הכפתור).',
        '<b>L03</b>: לא השתנה (הבורר כבר שקט בירידה).',
        '<b>החלטה 84:</b> לא נוגעת בבורר, ולכן לא משתנה. מה שאושר בה (סולם המהירות, שקט בירידה, החזקה לסיום, מסך הנעילה, "היום בדקה") נשאר.',
        '<b>הנתונים:</b> "ירידה אחת" נשארת אותו מצב הקלטה (<code>mode: run</code>), רק נכנסים אליו ממקום אחר. בלי שינוי בכלי של יום הגלישה.',
        '<b>הכרטיס בסוף:</b> אותו כרטיס של "ירידה אחת · נשמרה" (P04).') + '</div>')
    alts = ('<div class="card"><p class="sec">המסך החי, עד שנמצא מיקום</p><h2>שיהיה ברור מה קורה, ומה יתקבל</h2>' + li(
        '<b>א · שלושה שלבים.</b> שורה מתחת לגלולה: "מחפשים לוויינים · מקליטים · היום על ההר", והשלב שעכשיו דולק. ברור איפה אתה בדרך, אבל לא מראה מה יתקבל.',
        '<b>ב · הצצה למה שיתקבל (ההמלצה).</b> בזמן החיפוש, על ההר, היום לדוגמה בקווי האור (מסומן "דוגמה"), ובכרטיס שורה אחת: "בכל ירידה: המהירות ושם המסלול. בסוף היום: כל היום על ההר, וסיפור קצר." כשנמצא מיקום הדוגמה נמסה, והנקודה שלך מופיעה עם "הירידה הראשונה תצויר מכאן" (לוח 7). משתמש חדש רואה מה יקבל, לפני שעשה משהו.',
        '<b>ג · מחפשים, חי.</b> טבעות של אור שמתרחבות סביב פסי הקליטה, גדולים במרכז המסך (לא סביב מקום, כי עוד לא ידוע איפה אתה). חי ורגוע, אבל לא מסביר מה תקבל בסוף.',
        '<b>בכל שלוש:</b> הזמן רץ מהנגיעה, אין "0 קמ״ש" לפני שיש מיקום, ו"החזקה לסיום" במקום. בהפחתת תנועה: בלי התרחבות ובלי נמס, רק החלפה.') + '</div>')
    hon = ('<div class="card"><h2>ביושר ושאלות</h2>' + li(
        '<b>היום לדוגמה</b> הוא היום של כלי הבדיקה (<code>tools/fixtures/dayrec-logs/full_day.jsonl</code>), על הקווים האמיתיים, כמו בסבב 4 וב"יום לדוגמה" שכבר באפליקציה. תמיד עם התג "דוגמה".',
        '<b>טקסטים קיימים:</b> "מקליטים", "מחפשים לוויינים", ההסבר שמתחתיו, "עומדים", "תחת שמיים פתוחים זה לוקח עד דקה", "החזקה לסיום", "ירידה אחת", "היום על ההר".',
        '<b>הצעות לסשן התוכן:</b> "כך ייראה היום שלך על ההר"; "בכל ירידה: המהירות ושם המסלול. בסוף היום: כל היום על ההר, וסיפור קצר."; "הירידה הראשונה תצויר מכאן"; "מחכים לירידה הראשונה"; "מודדים ירידה אחת"; "נעצר לבד בתחתית, ונותן כרטיס של הירידה."',
        '<b>הפס הטכני בתחתית</b> בצילום שלך הוא של גרסת הבדיקה, לא חלק מהעיצוב.',
        '<b>לא נבדק בטלפון.</b>')
           + '<div class="q" style="margin-top: 6px"><span class="qn">1</span><span><b>הבורר:</b> א (בלי), ב (להסביר) או ג (ירידה אחת בגיליון המסלול). <b>(ההמלצה: ג)</b></span></div>'
           '<div class="q"><span class="qn">2</span><span><b>המסך החי עד שנמצא מיקום:</b> א, ב או ג. <b>(ההמלצה: ב, הצצה למה שיתקבל)</b></span></div>'
           '<div class="q"><span class="qn">3</span><span><b>היום לדוגמה על ההר</b> כהצצה, עם התג. <b>(ההמלצה: כן)</b></span></div></div>')
    inner = ('<p class="sec">בקשה שלך · 25.11.2026</p><h1>המסך החי: מה קורה אחרי הנגיעה</h1>'
             '<p class="lead">ביקשת לבדוק אם צריך בכלל את הבורר "כל היום / ירידה אחת", ולתת למסך החי חוויה ברורה: מה קורה עכשיו, ומה יתקבל בסוף. '
             'ההמלצה: "ירידה אחת" עוברת לגיליון המסלול, ובמסך החי הצצה למה שיתקבל.</p>'
             '<div class="rowx" style="align-items: stretch">%s%s</div>%s%s' % (tog, impact, alts, hon))
    return board('המסך החי', 1280, HL, wbox(1280, HL, inner))


def main():
    for fn, (title, body, fx) in list(phones().items()) + list(lv_phones().items()):
        PB.phone(fn, title, body, night=fx == 'night', fixed=fx)
    for fn, f in (('SE00-PreSeason.dc.html', se00), ('RB00-RecButton.dc.html', rb00), ('LV00-Live.dc.html', lv00)):
        open(os.path.join(PROJ, fn), 'w', encoding='utf8').write(f())
    p = os.path.join(PROJ, 'canvas.json'); j = json.load(open(p, encoding='utf8'))
    Y1 = HS + 140; Y2 = Y1 + 844 + 200; Y3 = Y2 + HR + 140
    place = [('SE00-PreSeason.dc.html', 0, 0, 1280, HS, 'לפני העונה: שלוש חלופות, והמלצה')]
    caps = ['א · הרכבל עולה, ביום (ההמלצה)', 'א · בלילה', 'ב · השלט בשמיים, ביום', 'ב · בלילה', 'ג · שורה שקטה, ביום', 'ג · בלילה']
    for i, fn in enumerate(['SEAD.dc.html', 'SEAN.dc.html', 'SEBD.dc.html', 'SEBN.dc.html', 'SECD.dc.html', 'SECN.dc.html']):
        place.append((fn, 470 * i, Y1, 390, 844, caps[i]))
    place.append(('RB00-RecButton.dc.html', 0, Y2, 1280, HR, 'כפתור ההקלטה: שלוש חלופות, והמלצה'))
    caps = ['1 · א: הנקודה והמילה', '2 · ב: נקודה ורמז, פעם אחת', '3 · הנגיעה הראשונה (L01)', '4 · בזמן הקלטה, בלילה']
    for i, fn in enumerate(['RB1.dc.html', 'RB2.dc.html', 'RB3.dc.html', 'RB4.dc.html']):
        place.append((fn, 470 * i, Y3, 390, 844, caps[i]))
    Y4 = Y3 + 844 + 200; Y5 = Y4 + HL + 140; Y6 = Y5 + 844 + 160
    place.append(('LV00-Live.dc.html', 0, Y4, 1280, HL, 'המסך החי: הבורר, וחוויה עד שנמצא מיקום'))
    caps = ['1 · א: שלושה שלבים, ביום', '2 · א, בלילה', '3 · ב: הצצה למה שיתקבל, ביום (ההמלצה)', '4 · ב, בלילה', '5 · ג: מחפשים, חי, ביום', '6 · ג, בלילה']
    for i, fn in enumerate(['LVAD.dc.html', 'LVAN.dc.html', 'LVBD.dc.html', 'LVBN.dc.html', 'LVCD.dc.html', 'LVCN.dc.html']):
        place.append((fn, 470 * i, Y5, 390, 844, caps[i]))
    place.append(('LVF.dc.html', 0, Y6, 390, 844, '7 · ב: נמצא מיקום'))
    place.append(('LVR.dc.html', 470, Y6, 390, 844, '8 · ירידה אחת בגיליון המסלול'))
    for fn, x, y, w, h, t in place:
        j['boards'][fn] = dict(x=x, y=y, w=w, h=h, title=t, page='season')
        if fn not in j['order']: j['order'].append(fn)
    if not any(pg['id'] == 'season' for pg in j['pages']): j['pages'].insert(2, dict(id='season', name='לפני העונה, וכפתור ההקלטה'))
    j['notes']['tSE'] = dict(kind='title1', maxW=1600, page='season', x=0, w=1000, y=-200, text='לפני העונה, וכפתור ההקלטה (25.11.2026)')
    open(p, 'w', encoding='utf8').write(json.dumps(j, ensure_ascii=False, indent=1) + '\n'); print('boards written:', len(place))


if __name__ == '__main__':
    main()
