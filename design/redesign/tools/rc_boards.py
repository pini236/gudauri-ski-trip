#!/usr/bin/env python3
"""The run card (27.11.2026, Pini's five notes on the run sheet): less text in the head, the three numbers on the
run's own profile, a film button that says what it is, a visible way to the videos and conditions, and a card that
floats with its neighbours peeking, so the dots read as "more runs". A new canvas page "כרטיס המסלול".
Design only. Handoff: design/handoff/2026-11-27-redesign-run-card/.
Usage: python3 design/redesign/tools/rc_boards.py [canvas.json path] [--preview <dir> <assets dir>]"""
import os, sys, json, re
import sv_boards as SV
from sv_boards import SL, DK, UX, ic, wb, li, PROJ, PB, G4, P
from peak_boards import VH, pic, both, svg, sign, glow_run
from v56_boards import top
import gap3_boards as G3

PAGE = 'runcard'
RUN = 'Tatra 2'
T0 = SL.T0
PROFILE = ('M0 8 L5.1 8 L10.2 8.2 L15.3 8.3 L20.4 8.4 L25.5 8.6 L30.6 9.6 L35.7 10.4 L40.8 11 L45.8 11.7 L50.9 12.6 L56 13.3 L61.1 14.3 L66.2 15.5 L71.3 16 L76.4 16.5 L81.5 17.6 L86.6 18.8 L91.7 20.1 '
           'L96.8 20.9 L101.9 22.2 L107 23.3 L112.1 24.2 L117.2 24.7 L122.2 26 L127.3 27.3 L132.4 29.7 L137.5 32 L142.6 34.5 L147.7 37.3 L152.8 40.2 L157.9 44 L163 45.9 L168.1 47.8 L173.2 50.1 L178.3 51.5 '
           'L183.4 53.1 L188.5 54.8 L193.6 57 L198.7 58.1 L203.8 59.9 L208.8 62.1 L213.9 64.3 L219 65.9 L224.1 67.3 L229.2 68.9 L234.3 70.5 L239.4 72.1 L244.5 73.7 L249.6 75 L254.7 76.1 L259.8 77.4 '
           'L264.9 78.7 L270 80.1 L275.1 81.6 L280.2 82.8 L285.2 83.6 L290.3 84.5 L295.4 85.5 L300.5 86.5 L305.6 87.3 L310.7 87.9 L315.8 88 L320.9 87.9 L326 87.7')
PTS = [tuple(float(v) for v in p.split()) for p in PROFILE[1:].split(' L')]
STEEP = (127.3, 163.0)          # the steepest stretch, from the model (20 degrees)
GENTLE, MID = '#3FA85F', '#F2C13D'
P_PLAY = '<path d="M8 5.500v13l10-6.500z"/>'
P_PAUSE = '<path d="M8 6v12M16 6v12"/>'
P_DOWN = '<path d="m6 9.500 6 6 6-6"/>'

RCSS = """
.H .rcd{position:absolute;left:18px;right:18px;bottom:44px;border-radius:28px;padding:8px 16px 14px;box-sizing:border-box;display:flex;flex-direction:column;gap:12px;overflow:hidden}
.H .rcd>*{flex-shrink:0}
.H .rsl{position:absolute;bottom:56px;width:22px;border-radius:24px;opacity:.72}
.H .rdots{position:absolute;left:0;right:0;bottom:18px;display:flex;justify-content:center;gap:6px;align-items:center}
.H .rdots i{width:6px;height:6px;border-radius:3px;background:var(--ink);opacity:.32;box-shadow:0 0 0 1px var(--halo)}
.H .rdots i.on{width:18px;opacity:1}
.H .rhd{display:flex;align-items:center;gap:10px;min-height:48px}
.H .rhd .nm{font-size:23px;font-weight:700;line-height:1.1;direction:ltr;unicode-bidi:isolate}
.H .rhd .sb{display:flex;align-items:center;gap:8px;font-size:13px;color:var(--ink2);margin-top:3px;white-space:nowrap}
.H .dch{display:inline-flex;align-items:center;gap:5px;height:22px;padding:0 9px 0 7px;border-radius:11px;background:var(--line);color:var(--ink);font-size:12.5px;font-weight:700}
.H .dch i{width:9px;height:9px;border-radius:50%}
.H .rnd{width:44px;height:44px;border-radius:50%;border:1px solid var(--line);display:flex;align-items:center;justify-content:center;flex:none}
.H .prf{position:relative;height:104px}
.H .prf svg{position:absolute;left:0;top:8px;display:block}
.H .prf .el{position:absolute;font-size:11.5px;font-weight:700;color:var(--ink2);font-variant-numeric:tabular-nums;white-space:nowrap}
.H .prf .pin{position:absolute;transform:translate(-50%,-100%);height:22px;padding:0 8px;border-radius:11px;background:var(--ink);color:var(--bg);font-size:12px;font-weight:700;display:flex;align-items:center;direction:ltr}
.H .prf .pin::after{content:'';position:absolute;left:50%;bottom:-4px;width:8px;height:8px;background:var(--ink);transform:translateX(-50%) rotate(45deg);border-radius:1.5px}
.H .prf .pdot{position:absolute;width:14px;height:14px;border-radius:50%;transform:translate(-50%,-50%);background:var(--acc);border:3px solid #fff;box-shadow:0 2px 6px rgba(0,0,0,.35)}
.H .rst{display:grid;grid-template-columns:1fr 1fr 1fr;gap:8px}
.H .rst div{display:flex;flex-direction:column;gap:1px}
.H .rst b{font-size:26px;font-weight:300;line-height:1.05;font-variant-numeric:tabular-nums;display:inline-flex;align-items:baseline;gap:4px}
.H .rst b small{font-size:13px;font-weight:500}
.H .rst span{font-size:12px;color:var(--ink2)}
.H .flm{height:50px;border-radius:25px;background:var(--acc);color:var(--onacc);font-size:16px;font-weight:700;display:flex;align-items:center;justify-content:center;gap:9px}
.H .flm.sec2{background:none;border:1px solid var(--line);color:var(--ink)}
.H .mor{display:flex;align-items:center;gap:10px;min-height:48px;margin:0 -6px -4px;padding:0 6px;border-radius:18px;background:var(--line);text-align:start}
.H .mor .tb{display:flex;direction:ltr}
.H .vth{width:52px;height:34px;border-radius:9px;background:linear-gradient(135deg,#28384E,#56657A);border:2px solid var(--paper);display:flex;align-items:center;justify-content:center;color:#fff;margin-left:-22px;flex:none}
.H .vth:first-child{margin-left:0}
.H .mor b{font-size:14px;font-weight:700}.H .mor span.s{font-size:12.5px;color:var(--ink2)}
.H .mor .up{margin-inline-start:auto;width:32px;height:32px;border-radius:50%;background:var(--glass);display:flex;align-items:center;justify-content:center;flex:none}
.H .cnd{display:grid;grid-template-columns:1fr 1fr;gap:8px}
.H .cnd div{height:62px;border-radius:18px;border:1px solid var(--line);display:flex;align-items:center;gap:10px;padding:0 14px}
.H .cnd b{font-size:28px;font-weight:300;direction:ltr}
.H .cnd span{font-size:12.5px;color:var(--ink2);line-height:1.3}.H .cnd span strong{display:block;color:var(--ink);font-size:13.5px}
.H .vrow{display:flex;align-items:center;gap:12px;min-height:66px;text-align:start}
.H .vrow+.vrow{border-top:1px solid var(--line)}
.H .vrow .big{width:96px;height:54px;border-radius:12px;background:linear-gradient(135deg,#28384E,#56657A);display:flex;align-items:center;justify-content:center;color:#fff;flex:none;position:relative}
.H .vrow .big em{position:absolute;right:5px;bottom:4px;font-style:normal;font-size:11px;font-weight:700;background:rgba(0,0,0,.6);border-radius:5px;padding:0 5px}
.H .vrow b{font-size:14.5px;font-weight:500;direction:ltr;unicode-bidi:isolate;display:block;text-align:start;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:220px}
.H .vrow span.c{font-size:12.5px;color:var(--ink2);direction:ltr;unicode-bidi:isolate;display:block;text-align:start}
.H .h3x{font-size:13.5px;font-weight:700}
"""
G4.MB.CSS = G4.MB.CSS + RCSS

TX = {'he': dict(diff='כחול · קל', len='ק״מ', lenc='אורך', drop='מ׳', dropc='ירידה', steepc='הקטע התלול', film='טיסה מעל המסלול', stop='עצירה', vid='3 סרטונים', cond='התנאים עכשיו',
                 condh='התנאים במסלול', toph='בראש המסלול', both_='בתחתית', wind='רוח %d קמ״ש', vidh='סרטונים מהמסלול', share='שיתוף', top='2,707 מ׳', bot='2,171 מ׳'),
      'en': dict(diff='Blue · easy', len='km', lenc='long', drop='m', dropc='drop', steepc='steepest part', film='Fly over the run', stop='Stop', vid='3 videos', cond='conditions now',
                 condh='Conditions on the run', toph='At the top', both_='At the bottom', wind='wind %d km/h', vidh='Videos from the run', share='Share', top='2,707 m', bot='2,171 m')}


def profile(L='he', at=None):
    """the run's own profile, coloured by slope; the top is at the start side"""
    rtl = L == 'he'
    fx = (lambda x: 326 - x) if rtl else (lambda x: x)
    seg = lambda a, b: [p for p in PTS if a <= p[0] <= b]
    area = lambda pts, c, op: '<path d="M%s L%.1f 96 L%.1f 96 Z" fill="%s" opacity="%s"/>' % (' L'.join('%.1f %.1f' % (fx(x), y) for x, y in pts), fx(pts[-1][0]), fx(pts[0][0]), c, op)
    line = lambda pts, c: '<path d="M%s" fill="none" stroke="%s" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>' % (' L'.join('%.1f %.1f' % (fx(x), y) for x, y in pts), c)
    A, B, C = seg(0, STEEP[0]), seg(*STEEP), seg(STEEP[1], 326)
    o = area(A, GENTLE, .2) + area(B, MID, .3) + area(C, GENTLE, .2)
    o += '<path d="M%s" fill="none" stroke="var(--paper)" stroke-width="7" stroke-linecap="round" stroke-linejoin="round"/>' % ' L'.join('%.1f %.1f' % (fx(x), y) for x, y in PTS)
    o += line(A, GENTLE) + line(B, MID) + line(C, GENTLE)
    for xk in (63, 126, 189, 252, 315):          # a tick every 500 m of the 2.7 km
        o += '<path d="M%.1f 96v-5" stroke="var(--ink2)" stroke-width="1.2" opacity=".6"/>' % fx(xk * 326 / 340)
    h = '<svg width="318" height="94" viewBox="0 0 326 96" preserveAspectRatio="none" aria-hidden="true">%s</svg>' % o
    T = TX[L]; s, e = ('right', 'left') if rtl else ('left', 'right')
    h += '<span class="el" style="%s: 0; top: 34px">%s</span><span class="el" style="%s: 0; top: 68px">%s</span>' % (s, T['top'], e, T['bot'])
    h += '<span class="pin" style="left: %.0fpx; top: %.0fpx">20°</span>' % (fx(145) * 318 / 326, 8 + 30)
    if at is not None:
        x, y = PTS[int(at * (len(PTS) - 1))]
        h += '<span class="pdot" style="left: %.0fpx; top: %.0fpx"></span>' % (fx(x) * 318 / 326, 8 + y)
    return '<div class="prf">%s</div>' % h


def head(L='he'):
    T = TX[L]
    return ('<div class="rhd">%s<span style="min-width: 0"><span class="nm" style="display: block">%s</span>'
            '<span class="sb"><span class="dch"><i style="background: var(--blue)"></i>%s</span><bdi dir="ltr">Goodaura ↓ Shino</bdi></span></span>'
            '<button class="rnd" aria-label="%s" style="margin-inline-start: auto">%s</button></div>' % (sign(RUN, big=True), RUN, T['diff'], T['share'], ic(G3.P_SHARE, 20)))


def stats(L='he'):
    T = TX[L]
    return ('<div class="rst"><div><b>2.7<small>%s</small></b><span>%s</span></div><div><b>536<small>%s</small></b><span>%s</span></div><div><b>20°</b><span>%s</span></div></div>'
            % (T['len'], T['lenc'], T['drop'], T['dropc'], T['steepc']))


def film(L='he', playing=False):
    T = TX[L]
    return ('<button class="flm sec2">%s %s</button>' % (ic(P_PAUSE, 20, 2.4), T['stop'])) if playing else '<button class="flm">%s %s</button>' % (ic(P_PLAY, 19, 2, 'currentColor'), T['film'])


def more(L='he'):
    T = TX[L]
    th = ''.join('<span class="vth">%s</span>' % ic(P_PLAY, 13, 2, 'currentColor') for _ in range(3))
    return ('<button class="mor"><span class="tb">%s</span><span><b style="display: block">%s</b><span class="s"><bdi>−7°</bdi> · %s</span></span><span class="up">%s</span></button>'
            % (th, T['vid'], T['cond'], ic(DK.P_UP, 17, 2.4)))


VID = [('8:57', 'Спуск по Tatra 2, Гудаури, Грузия', 'Vlad Key'), ('6:55', 'Gudauri 2018. Tatra 2 Slope. 4K.', 'Valeri Khomutov'), ('3:20', 'Gudauri 2020. Tatra2', 'Alex Cyclist')]


def opened(L='he'):
    T = TX[L]
    c = ('<p class="h3x">%s</p><div class="cnd"><div><b>−7°</b><span><strong>%s</strong>%s</span></div><div><b>−4°</b><span><strong>%s</strong>%s</span></div></div>'
         % (T['condh'], T['toph'], T['wind'] % 22, T['both_'], T['wind'] % 11))
    v = '<p class="h3x">%s</p><div style="margin-top: -8px">%s</div>' % (T['vidh'], ''.join(
        '<button class="vrow"><span class="big">%s<em>%s</em></span><span style="min-width: 0"><b>%s</b><span class="c">%s</span></span></button>' % (ic(P_PLAY, 20, 2, 'currentColor'), d, t, ch) for d, t, ch in VID))
    return c + v


def mount(scale=True):
    def fn(mode, light):
        veil = '<rect width="390" height="844" fill="%s"/>' % ('rgba(40,62,110,.22)' if mode == 'day' else 'rgba(2,6,18,.32)')
        return (P.details(VH, mode, light) + svg(VH, veil) + P.runs_svg(VH, mode, 'ghost', skip=[RUN], glow=False) + P.lifts_svg(VH, mode, light, dim=True)
                + svg(VH, glow_run(VH, RUN, mode, w=4.2)))
    return SL.stage(pic('hero', top=T0) + both(fn, top=T0))


def screen(L='he', state='peek', playing=False):
    b = mount() + (top() if L == 'he' else UX.root_en().split('<div class="now', 1)[1].join(['<div class="now', '']) if False else top())
    inner = '<div class="grab"></div>' + head(L) + profile(L, .42 if playing else None) + stats(L) + film(L, playing)
    if state == 'open':
        b += '<div class="rcd glass" style="top: 112px">%s%s</div>' % (inner, opened(L))
        tp = 124
    else:
        b += '<div class="rcd glass">%s%s</div>' % (inner, '' if playing else more(L))
        tp = 844 - 44 - (392 if not playing else 336) + 12
    for side in ('left: -12px', 'right: -12px'):
        b += '<div class="rsl glass" style="%s; top: %dpx"></div>' % (side, tp)
    b += '<div class="rdots" aria-hidden="true">%s</div>' % ''.join('<i class="%s"></i>' % ('on' if i == 2 else '') for i in range(7))
    return b


def phones():
    return {
        'RK1.dc.html': ('כרטיס המסלול: צף, עם השכנים מציצים והנקודות מתחתיו', screen(), None),
        'RK2.dc.html': ('הכרטיס פתוח: התנאים והסרטונים', screen(state='open'), None),
        'RK3.dc.html': ('בלילה', screen(), 'night'),
        'RK4.dc.html': ('בזמן הטיסה מעל המסלול: הנקודה יורדת על הפרופיל', screen(playing=True), None),
        'RK5.dc.html': ('The run card in English', screen('en'), 'day', 'en'),
    }


HB = 1240


def rc00():
    row = lambda *c: '<tr>%s</tr>' % ''.join('<td>%s</td>' % x for x in c)
    tbl = ('<table style="border-collapse: collapse; width: 100%"><tr><th>#</th><th>מה פיני אמר</th><th>ההצעה</th></tr>'
           + row('1', '"(מפה רשמית)" מיותר, ופחות טקסט', 'בראש הכרטיס: השלט, השם, תג קטן "כחול · קל" עם נקודת צבע, ו-<bdi>Goodaura ↓ Shino</bdi> (מאיפה ולאן, בלי מילים). "(מפה רשמית)" יורד בכל מקום.')
           + row('2', 'המספרים הם שורות טקסט', 'הפרופיל האמיתי של המסלול, צבוע לפי השיפוע: ירוק במתון, צהוב בתלול. עליו הגובה בראש ובתחתית, סימון כל 500 מ׳, ותג "20°" בדיוק במקום של הקטע התלול. שלושת המספרים מתחתיו.')
           + row('3', '"לרדת בסרט" לא ברור', '"טיסה מעל המסלול", עם משולש של ניגון. בזמן הטיסה נקודה יורדת על הפרופיל, והכפתור הופך ל"עצירה". שתי חלופות לניסוח למטה.')
           + row('4', '"סרטונים · 1" לא אומר מה לעשות', 'שורה אחת בתחתית הכרטיס: שלוש תמונות קטנות של סרטונים, "3 סרטונים", הטמפרטורה עכשיו, ועיגול עם חץ למעלה (אותו עיגול של כפתור "מסלולים"). נגיעה או משיכה פותחת.')
           + row('5', 'הנקודות נראות כמו דפדוף בסרטונים', 'הכרטיס צף, קצות הכרטיסים של המסלול הקודם והבא מציצים משני הצדדים, והנקודות מתחת לכרטיס, מחוץ לו. ברור שהכרטיס כולו זז הצידה.'))
    tbl += '</table>'
    words = li('<b>"טיסה מעל המסלול" (מומלץ).</b> אומר מה רואים: המצלמה טסה מעל המסלול מלמעלה עד למטה.',
               '<b>"סיור במסלול".</b> קצר, אבל יכול להישמע כמו הדרכה.',
               '<b>"לראות את הירידה".</b> טבעי, אבל פחות מדויק (אפשר לחשוב שזה סרטון של גולש).')
    spec = li('<b>ההר ממלא את כל המסך</b> מאחורי הכרטיס, כמו בכלל של גרסה 91; המסלול הנבחר דלוק מעל הכרטיס.',
              '<b>הכרטיס:</b> 18 מהצדדים, 44 מהתחתית, רדיוס 28, זכוכית. מתחתיו הנקודות (הפעילה רחבה). קצות השכנים: 10 נקודות נראות מכל צד, 8 מהכרטיס.',
              '<b>פתוח:</b> אותו כרטיס גדל עד 112 מלמעלה ונגלל מבפנים; השורה התחתונה מתחלפת בתנאים וברשימת הסרטונים. משיכה למטה מחזירה.',
              '<b>החלקה הצידה:</b> הכרטיס כולו נוסע, והשכן נכנס; ההר והמסלול הדלוק מתחלפים איתו (300 מ״ש). הסדר: של הצ׳יפ הדלוק במדף.',
              '<b>בלי סרטונים:</b> השורה התחתונה מראה רק את התנאים; בלי תחזית ובלי סרטונים: אין שורה, והכרטיס קצר יותר.')
    asks = ''.join('<p class="q"><span class="qn">%d</span><span>%s</span></p>' % (i + 1, t) for i, t in enumerate([
        '<b>הכרטיס הצף, עם השכנים מציצים והנקודות מתחתיו?</b> (ההמלצה: כן)',
        '<b>הפרופיל הצבוע עם שלושת המספרים?</b> (כן)',
        '<b>"טיסה מעל המסלול"</b> במקום "לרדת בסרט"? (כן; הניסוח הסופי דרך סשן התוכן)',
        '<b>השורה התחתונה</b> (תמונות, "3 סרטונים", התנאים, חץ למעלה) במקום "סרטונים · 1"? (כן)']))
    inner = ('<p class="sec">כרטיס המסלול · 27.11.2026 · ממתין לפיני</p>'
             '<h1>כרטיס המסלול: פחות מילים, הפרופיל במקום שורות, ודרך ברורה לסרטונים ולמסלול הבא</h1>'
             '<div class="card"><h2>חמש ההערות</h2>%s</div>'
             '<div class="rowx" style="align-items: stretch"><div class="card" style="flex: 1"><h2>איך לקרוא לכפתור</h2>%s</div>'
             '<div class="card" style="flex: 1.3"><h2>המבנה</h2>%s</div></div>'
             '<div class="card"><h2>לפיני</h2>%s<p class="cap">הפרופיל, האורך, הירידה, הגבהים והקטע התלול של <bdi>Tatra 2</bdi> מהנתונים של האתר. הסרטונים שבלוח הם מהרשימה האמיתית; הטמפרטורות דוגמה. נמדד בכלי העיצוב, לא בטלפון. '
             'המסירה: <span dir="ltr">design/handoff/2026-11-27-redesign-run-card/</span></p></div>' % (tbl, words, spec, asks))
    wb('RK00-RunCard.dc.html', 'כרטיס המסלול', 1280, HB, inner)


FILES = ['RK00-RunCard.dc.html'] + ['RK%d.dc.html' % i for i in range(1, 6)]


def write_canvas(cj):
    j = json.load(open(cj, encoding='utf8'))
    clash = [f for f in j['boards'] if f not in FILES and re.match(r'^RK\d', f)]
    assert not clash, clash
    for fn in FILES: j['boards'].pop(fn, None)
    j['order'] = [f for f in j['order'] if f not in FILES]
    T = phones()
    place = [('RK00-RunCard.dc.html', 0, 0, 1280, HB, 'חמש ההערות, ההצעה והשאלות')]
    for k, fn in enumerate(FILES[1:]):
        place.append((fn, 470 * k, HB + 140, 390, 844, T[fn][0]))
    for fn, x, y, w, h, t in place:
        code = fn.split('.dc.html')[0].split('-')[0]
        j['boards'][fn] = dict(x=x, y=y, w=w, h=h, title='%s (%s)' % (t, code), page=PAGE)
        j['order'].append(fn)
    if not any(pg['id'] == PAGE for pg in j['pages']):
        j['pages'].insert(0, dict(id=PAGE, name='כרטיס המסלול (27.11)'))
    j['notes']['t' + PAGE] = dict(kind='title1', maxW=1600, page=PAGE, x=0, y=-300, text='כרטיס המסלול')
    j['launch'] = dict(view='canvas', page=PAGE)
    open(cj, 'w', encoding='utf8').write(json.dumps(j, ensure_ascii=False, indent=1) + '\n')


def main():
    args = sys.argv[1:]
    pv = None
    if '--preview' in args:
        i = args.index('--preview'); pv = (args[i + 1], args[i + 2]); del args[i:i + 3]
    cj = args[0] if args else os.path.join(PROJ, 'canvas.json')
    have = json.load(open(cj, encoding='utf8'))['boards']
    assert not [f for f in have if f not in FILES and re.match(r'^RK\d', f)], 'board code taken'
    assert not [f for f in os.listdir(PROJ) if f not in FILES and re.match(r'^RK\d', f)], 'board file exists'
    for fn, v in phones().items():
        title, body, fx = v[:3]; lang = v[3] if len(v) > 3 else 'he'
        PB.phone(fn, title, body, night=fx == 'night', fixed=fx, lang=lang)
    rc00()
    write_canvas(cj)
    if pv:
        keep = DK.FILES; DK.FILES = FILES; DK.preview(*pv); DK.FILES = keep
    print('ok', cj)


if __name__ == '__main__':
    main()
