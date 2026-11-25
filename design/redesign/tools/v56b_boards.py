#!/usr/bin/env python3
"""Pini's answers on the "הערות על גרסה 56" page (25.11.2026, via the manager), a second round on the same page:
  RX  the record button without a word, three places; the live state stays inside its bar (the bug in RD2)
  TM  the search screen, version 3 that Pini chose: with a small counter from the tap, or without time until a location
  GD  the speed ring of the live screen as a dashboard: the mountain inside the ring, and snow that rushes by with the speed
Placed under the boards already on the page. Design only. Usage: python3 design/redesign/tools/v56b_boards.py"""
import os, json, math, random
import v56_boards as V
from v56_boards import (pic, veil, rings, ic, P_AIM, hold, wb, li, q, PROJ, PAGE, top, ctl, mtn, P_HOME, P_SRCH,
                        daylines, me, gauge, sign, DAY, mm, recp)
import peak_boards as PB
from peak_boards import VH
import gap4_boards as G4
import live_boards as LB
import day4 as D

RED = '#E5352B'
WORD = 'הקלטת גלישה'      # what the screen reader says (app.rec_sign)
TIME = '1:24'

# ---------------------------------------------------------------- RX: the record button
BCSS = """
@keyframes tkd{0%{stroke-dashoffset:1;opacity:1}28%{stroke-dashoffset:0;opacity:1}78%{stroke-dashoffset:0;opacity:1}100%{stroke-dashoffset:0;opacity:0}}
@keyframes arc{0%{stroke-dashoffset:1}100%{stroke-dashoffset:0}}
@keyframes glo{0%,100%{box-shadow:inset 0 0 0 0 rgba(255,255,255,0)}50%{box-shadow:inset 0 0 14px 2px rgba(255,255,255,.45)}}
.sh{position:relative;width:48px;height:48px;border-radius:50%;background:#E5352B;border:0;padding:0;flex:none;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:1px;color:#fff;box-sizing:border-box;font-family:inherit;box-shadow:0 2px 6px rgba(120,20,15,.28)}
.sh .rg{position:absolute;inset:0;overflow:visible}
.sh .tk{stroke-dasharray:1;stroke-dashoffset:0}
.sh.idle .tk{animation:tkd 6s ease-out infinite}
.sh.live{animation:glo 2.4s ease-in-out infinite}
.sh.live .tk{animation:tkd 2.4s linear infinite}
.sh.live .ar{stroke-dasharray:1;animation:arc 60s linear infinite}
.sh .stm{background:none!important;box-shadow:none!important;width:auto!important;height:auto!important;border-radius:0!important;font-size:12.5px;font-weight:700;font-variant-numeric:tabular-nums;direction:ltr;line-height:1}
.sh.fab{width:62px;height:62px;box-shadow:0 6px 16px rgba(120,20,15,.35)}
.sh.mid{width:56px;height:56px}
.still .tk,.still .ar,.still.sh{animation:none!important}
.nub{display:inline-flex;align-items:center;gap:7px;font-variant-numeric:tabular-nums;direction:ltr;font-weight:700}
.nub i{width:9px;height:9px;border-radius:50%;background:#E5352B;box-shadow:0 0 0 3px rgba(229,53,43,.25)}
"""
PCSS = """
.H .dock3{position:absolute;left:10px;right:10px;bottom:12px;height:60px;border-radius:30px;display:flex;align-items:center;gap:8px;padding:0 6px;box-sizing:border-box}
.H .dock3 .vl{height:44px;border-radius:22px;border:1px solid var(--line);display:flex;align-items:center;gap:7px;padding:0 14px 0 12px;font-size:15px;font-weight:500;flex:none}
.H .dock3 .find{flex:1;min-width:0;height:44px;border-radius:22px;padding:0 14px;font-size:15px;white-space:nowrap}
.H .fabw{position:absolute;right:12px;bottom:86px}
.H .notch{position:absolute;left:50%;bottom:12px;width:76px;height:76px;margin-left:-38px;border-radius:50%;display:flex;align-items:center;justify-content:center}
.H .dock3.split .find{flex:1}
.H .dock3.split .gap{flex:none;width:74px}
"""
G4.MB.CSS = G4.MB.CSS + BCSS.replace('\n.', '\n.H .').replace(',.', ',.H .') + PCSS
LB.CSS = LB.CSS + BCSS + """
.HW .stage{display:flex;align-items:center;justify-content:center;gap:24px;height:140px;border-radius:12px}
.HW .stage.d{background:linear-gradient(#DDE8F3,#F4F7FA)}
.HW .stage.n{background:linear-gradient(#0B1424,#16213A)}
.HW .stage .z{zoom:1.7}
.HW .opt{flex:1;min-width:0}
.HW .opt h2 .k{display:inline-flex;width:34px;height:34px;border-radius:17px;background:#10161D;color:#fff;align-items:center;justify-content:center;font-size:18px;margin-inline-end:8px}
.HW .opt.rec{border:3px solid #6B3FD4}
.HW .pc{font-size:14.5px;line-height:1.5}
.HW .pc b{display:inline-block;min-width:58px}
.HW .mini{display:flex;align-items:center;gap:8px;height:52px;border-radius:26px;background:#fff;border:1px solid #D9DEE4;padding:0 6px 0 14px;font-size:14px;color:#56606C}
"""

TRK = 'M19 2.4C9.6 3.4 5.6 6.4 10.2 9.6C14.8 12.6 15.6 15.8 7.6 18.6'


def tracks(px=22):
    """ski tracks that end at the skier: one curve drawn wide in white and thin in red, so it reads as two lines"""
    return ('<svg width="%d" height="%d" viewBox="0 0 24 24" fill="none" stroke-linecap="round" aria-hidden="true" style="position: relative">'
            '<path class="tk" pathLength="1" d="%s" stroke="#fff" stroke-width="5"/>'
            '<path class="tk" pathLength="1" d="%s" stroke="%s" stroke-width="2"/>'
            '<circle cx="6.9" cy="18.8" r="3.4" fill="#fff"/></svg>' % (px, px, TRK, TRK, RED))


def ring(d, live, prog=.3):
    """the white ring of a record button; while recording it fills once a minute"""
    r = d / 2 - 4.5
    base = '<circle cx="%.1f" cy="%.1f" r="%.1f" fill="none" stroke="#fff" stroke-opacity="%s" stroke-width="2"/>' % (d / 2, d / 2, r, '.4' if live else '.92')
    arc = ''
    if live:
        arc = ('<circle class="ar" pathLength="1" cx="%.1f" cy="%.1f" r="%.1f" fill="none" stroke="#fff" stroke-width="2.6" stroke-linecap="round" '
               'transform="rotate(-90 %.1f %.1f)" style="stroke-dashoffset: %.2f; animation-delay: -%.0fs"/>' % (d / 2, d / 2, r, d / 2, d / 2, 1 - prog, 60 * prog))
    return '<svg class="rg" viewBox="0 0 %d %d">%s%s</svg>' % (d, d, base, arc)


def sh(state='idle', size='', t=TIME, still=False, timer_in=False):
    d = {'': 48, 'fab': 62, 'mid': 56}[size]
    cls = 'sh %s%s%s' % (state, ' ' + size if size else '', ' still' if still else '')
    inner = tracks(24 if size == 'fab' else 22)
    if state == 'live' and timer_in:
        inner = tracks(18) + '<span class="stm" style="position: relative">%s</span>' % t
    lab = WORD if state == 'idle' else '%s, %s' % (WORD, t)
    return '<button class="%s" aria-label="%s">%s%s</button>' % (cls, lab, ring(d, state == 'live'), inner)


def nub(t=TIME):
    return '<span class="nub"><i></i>%s</span>' % t


def dock_a(btn):
    return ('<div class="dock3 glass"><button class="vl">%s הכפר</button><div class="find">%s<span>לאן יורדים?</span></div>%s</div>'
            % (ic(P_HOME, 20), ic(P_SRCH, 18), btn))


def dock_plain():
    return ('<div class="dock3 glass"><button class="vl">%s הכפר</button><div class="find">%s<span>לאן יורדים?</span></div></div>'
            % (ic(P_HOME, 20), ic(P_SRCH, 18)))


def dock_c(btn):
    """the button in the middle of the bar, between the two: a bigger circle, still inside the bar"""
    return ('<div class="dock3 glass"><button class="vl">%s הכפר</button>%s<div class="find">%s<span>לאן יורדים?</span></div></div>'
            % (ic(P_HOME, 20), btn, ic(P_SRCH, 18)))


def top_rec(t=TIME):
    return top(text=nub(t), dot='dot" style="display: none')


def rx_phones():
    out = {}
    out['RX1.dc.html'] = ('כפתור ההקלטה · א, בסרגל, ביום', mtn() + top() + ctl() + dock_a(sh('idle')), 'day')
    out['RX2.dc.html'] = ('כפתור ההקלטה · א, בזמן הקלטה, בלילה', mtn('night') + top_rec() + ctl() + dock_a(sh('live')), 'night')
    out['RX3.dc.html'] = ('כפתור ההקלטה · ב, צף מעל הסרגל, ביום', mtn() + top() + ctl() + dock_plain() + '<div class="fabw">%s</div>' % sh('idle', 'fab'), 'day')
    out['RX4.dc.html'] = ('כפתור ההקלטה · ב, בזמן הקלטה, בלילה', mtn('night') + top() + ctl() + dock_plain()
                          + '<div class="fabw">%s</div>' % sh('live', 'fab', timer_in=True), 'night')
    out['RX5.dc.html'] = ('כפתור ההקלטה · ג, באמצע הסרגל, ביום', mtn() + top() + ctl() + dock_c(sh('idle', 'mid')), 'day')
    out['RX6.dc.html'] = ('כפתור ההקלטה · ג, בזמן הקלטה, בלילה', mtn('night') + top_rec() + ctl() + dock_c(sh('live', 'mid')), 'night')
    return out


def opt(k, h2, sd, sn, pros, cons, rec=False):
    return ('<div class="card opt%s"><h2><span class="k">%s</span>%s</h2><div class="stage d">%s</div><div class="stage n">%s</div>'
            '<p class="pc"><b>מה עובד</b>%s</p><p class="pc"><b>מה חסר</b>%s</p></div>' % (' rec' if rec else '', k, h2, sd, sn, pros, cons))


def z(x):
    return '<div class="z">%s</div>' % x


HRX = 1000


def rx00():
    a = opt('א', 'בסרגל, בלי מילה', z(sh('idle')) + z(sh('live')), z(sh('idle')) + z(sh('live')),
            'הכפתור האדום עם העקבות שאהבת, ועכשיו גם בתוך טבעת לבנה, כמו כפתור הקלטה של מצלמה. בזמן הקלטה הכפתור לא גדל: הטבעת מתמלאת פעם בדקה, והזמן עובר לגלולה העליונה.',
            'עדיין קטן, בסוף הסרגל, ליד שני כפתורים אחרים.')
    b = opt('ב', 'כפתור צף, מעל הסרגל', z(sh('idle', 'fab')) + z(sh('live', 'fab', timer_in=True)), z(sh('idle', 'fab')) + z(sh('live', 'fab', timer_in=True)),
            'גדול (62), לבד, בפינה של האגודל, כמו כפתור הפעולה הראשי באנדרואיד. הסרגל נשאר רק לניווט. בזמן הקלטה הזמן בתוך הכפתור.',
            'יושב על ההר, ומכסה פינה קטנה ממנו.', rec=True)
    c = opt('ג', 'באמצע הסרגל, בין "הכפר" ל"לאן יורדים?"', z(sh('idle', 'mid')) + z(sh('live', 'mid')), z(sh('idle', 'mid')) + z(sh('live', 'mid')),
            'במרכז, וגדול יותר (56), כמו כפתור הצילום במצלמה. עדיין כולו בתוך הסרגל.',
            'הסרגל צר יותר ל"לאן יורדים?", ובאנגלית ובגאורגית המילים ארוכות.')
    bug = ('<div class="card"><h2>הבאג בלוח RD2</h2>' + li(
        'בזמן הקלטה הכפתור גדל לרוחב בשביל הזמן, ויחד עם ההילה הוא יצא מהסרגל.',
        'מעכשיו הכפתור לא משנה גודל אף פעם, וההילה בתוכו ולא סביבו. הזמן עובר לגלולה העליונה (א, ג) או נכנס לתוך הכפתור (ב).') + '</div>')
    rec = ('<div class="card"><h2>ההמלצה: ב</h2>' + li(
        'ההקלטה היא הפעולה הראשית בהר, ולכן היא מקבלת כפתור משלה, גדול ולבד, ולא עוד פריט בסרגל.',
        'מה שאומר "הקלטה" בלי מילה: עיגול אדום בתוך טבעת לבנה. העקבות בפנים אומרות "סקי".',
        'קורא מסך אומר "%s", ובזמן הקלטה גם את הזמן.' % WORD,
        'בהפחתת תנועה: העקבות והטבעת בלי תנועה.')
        + q(1, '<b>איזו חלופה:</b> א, ב או ג? <b>(ההמלצה: ב)</b>') + '</div>')
    inner = ('<p class="sec">הערות על גרסה 56 · כפתור ההקלטה, סבב 2</p><h1>כפתור ההקלטה בלי מילה, בשלושה מקומות</h1>'
             '<div class="rowx" style="align-items: stretch">%s%s%s</div><div class="rowx" style="align-items: stretch">%s%s</div>' % (a, b, c, bug, rec))
    wb('RX00-RecButton2.dc.html', 'כפתור ההקלטה: בלי מילה, שלושה מקומות', 1280, HRX, inner)


# ---------------------------------------------------------------- TM: the search screen, with or without a counter
TCSS = """
.H .gtm{font-size:20px;font-weight:500;font-variant-numeric:tabular-nums;direction:ltr;color:var(--ink2)}
"""
G4.MB.CSS = G4.MB.CSS + TCSS
LONG = 'בחוץ, מתחת לשמיים פתוחים,<br>זה לוקח בדרך כלל פחות מדקה'


def search3(night, t=None, long=False):
    cx, cy = 195, 360
    o = pic('hero', 'night' if night else 'morning', None) + veil(0, 844, 'var(--veil)')
    o += rings(cx, cy) + '<div class="gsc glass" style="left: %dpx; top: %dpx">%s</div>' % (cx, cy, ic(P_AIM, 40, 1.6))
    sub = ('<span class="gtm">%s</span>' % t if t else '') + ('<span>%s</span>' % LONG if long else '')
    return o + '<div class="gst" style="top: %dpx"><b>מחפש מיקום</b>%s</div>' % (cy + 150, sub) + hold()


def found0(night=False):
    """the first frame of the live screen, when the location arrives: the counter starts here at 0:00"""
    t = D.SEG[0]['t0'] + 1000
    rg, _ = me(t)
    return (pic('hero', 'night' if night else 'morning', None) + rg + recp('0:00') + hold())


def tm_phones():
    return {'TM1d.dc.html': ('מסך החיפוש · עם מונה, ביום', search3(False, '0:14'), 'day'),
            'TM1n.dc.html': ('מסך החיפוש · עם מונה, אחרי 30 שניות, בלילה', search3(True, '0:41', True), 'night'),
            'TM2d.dc.html': ('מסך החיפוש · בלי זמן, ביום', search3(False), 'day'),
            'TM2f.dc.html': ('מסך החיפוש · בלי זמן: כשנמצא מיקום, המונה מתחיל ב-0:00', found0(), 'day')}


HTM = 540


def tm00():
    rows = [('עם מונה (ההמלצה)', 'מתחת ל"מחפש מיקום", בלי מילה ליד. סופר מהנגיעה, וממשיך באותו מספר במסך החי. כך הקוד עובד היום (התיקון מ-13.11).'),
            ('בלי זמן', 'בזמן החיפוש אין מספר. כשנמצא מיקום המסך עובר למסך החי, והמונה מתחיל שם ב-0:00.')]
    tbl = ''.join('<tr style="border-top: 1px solid #D9DEE4"><td style="padding: 9px 10px; width: 24%%"><b>%s</b></td><td style="padding: 9px 10px">%s</td></tr>' % r for r in rows)
    inner = ('<p class="sec">הערות על גרסה 56 · מסך החיפוש, אחרי הבחירה ב-3</p><h1>מסך החיפוש: מונה או בלי</h1>'
             '<div class="card"><table style="border-collapse: collapse; width: 100%%; font-size: 15.5px; line-height: 1.45">%s</table></div>'
             '<div class="card"><h2>ההמלצה: עם מונה</h2>%s%s</div>' % (tbl, li(
                 'השאלה שלך הייתה איך יודעים שההקלטה התחילה. מספר שזז עונה על זה בלי מילה, גם כשהחיפוש לוקח דקה.',
                 'המונה סופר את הזמן מהנגיעה, אבל מה שנמדד (ירידות, מהירות) מתחיל רק כשיש מיקום. זה כבר כך היום.',
                 'בלי זמן, חיפוש ארוך נראה תקוע, ואז המונה במסך החי מתחיל ב-0:00 כאילו כלום לא קרה עד עכשיו.',
                 'השורה "בחוץ זה לוקח בדרך כלל פחות מדקה" מופיעה רק אחרי 30 שניות בלי מיקום, בשתי האפשרויות.'),
                 q(1, '<b>מונה מהנגיעה, או בלי זמן עד שיש מיקום?</b> <b>(ההמלצה: מונה)</b>')))
    wb('TM00-Counter.dc.html', 'מסך החיפוש: מונה או בלי', 1280, HTM, inner)


# ---------------------------------------------------------------- GD: the speed ring as a dashboard
GCSS = """
@keyframes snf{0%{transform:translate(0,-30px)}100%{transform:translate(var(--dx),230px)}}
.H .gd{position:absolute;left:50%;bottom:92px;width:252px;height:252px;transform:translateX(-50%)}
.H .gd .win{position:absolute;inset:26px;border-radius:50%;overflow:hidden;box-shadow:0 10px 26px rgba(8,18,36,.35),inset 0 0 0 2px rgba(255,255,255,.7)}
.H .night .gd .win{box-shadow:0 10px 26px rgba(0,0,0,.5),inset 0 0 0 2px rgba(176,206,255,.45)}
.H .gd .win img{position:absolute;width:780px;height:1688px;max-width:none}
.H .gd .win .vg{position:absolute;inset:0;background:radial-gradient(ellipse 70% 46% at 50% 40%,rgba(255,255,255,.5) 0,rgba(255,255,255,.18) 70%,rgba(255,255,255,0) 100%)}
.H .night .gd .win .vg{background:radial-gradient(ellipse 70% 46% at 50% 40%,rgba(6,12,26,.55) 0,rgba(6,12,26,.25) 70%,rgba(6,12,26,0) 100%)}
.H .gd .sn{position:absolute;inset:0}
.H .gd .sn i{position:absolute;top:0;width:3px;height:3px;border-radius:2px;background:#fff;opacity:.95;animation:snf linear infinite;box-shadow:0 0 0 1px rgba(70,92,125,.45),0 1px 3px rgba(40,60,90,.35)}
.H .night .gd .sn i{box-shadow:0 0 3px rgba(255,255,255,.7)}
.H .gd .in{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:6px;padding-bottom:34px;box-sizing:border-box}
.H .gd .v{display:flex;align-items:baseline;gap:6px;direction:ltr}
.H .gd .v b{font-size:88px;font-weight:300;line-height:.85;letter-spacing:-.04em;font-variant-numeric:tabular-nums;text-shadow:0 0 16px var(--halo),0 0 4px var(--halo)}
.H .gd .v span{font-size:17px;font-weight:700;color:var(--ink2)}
.H .gd .rn{display:flex;align-items:center;gap:7px;font-size:16px;font-weight:700}
.H .gd .you{position:absolute;left:50%;top:84%;width:10px;height:10px;border-radius:50%;background:#2F7BFF;border:2px solid #fff;transform:translate(-50%,-50%)}
"""
G4.MB.CSS = G4.MB.CSS + GCSS


def snow(v, still=False, n=26, seed=7):
    """flakes inside the window: they rush by faster and stretch into streaks as the speed goes up"""
    r = random.Random(seed); f = min(v / 70.0, 1.0)
    dur = 2.6 - 1.9 * f          # seconds to cross the window
    ln = 3 + 13 * f              # px, a streak at speed
    o = []
    for i in range(n):
        x = r.uniform(4, 196); dx = r.uniform(-10, 10) - 18 * f
        if still:
            y = r.uniform(6, 190)
            o.append('<i style="left: %.0fpx; top: %.0fpx; height: %.0fpx; animation: none"></i>' % (x, y, 3 + 4 * f))
        else:
            o.append('<i style="left: %.0fpx; height: %.0fpx; --dx: %.0fpx; animation-duration: %.2fs; animation-delay: -%.2fs"></i>' % (x, ln, dx, dur * r.uniform(.85, 1.2), r.uniform(0, dur)))
    return '<div class="sn">%s</div>' % ''.join(o)


def dash(v, t, best=61, mode='day', run='Sadzele 1', still=False):
    R = 112; C = 2 * math.pi * R; vmax = 80; f = min(v / vmax, 1)
    ramp = D.RAMP[mode]
    stops = ''.join('<stop offset="%d%%" stop-color="%s"/>' % (i * 25, c) for i, c in enumerate(ramp))
    x, y, vis, i = D.where(VH, t)
    win = ('<div class="win"><img alt="" src="%s" style="left: %.0fpx; top: %.0fpx">%s<div class="vg"></div>%s<span class="you"></span></div>'
           % (PB.U('hero-' + ('night' if mode == 'night' else 'morning')), 100 - 2 * x, 168 - 2 * y, '', snow(v, still)))
    g = ('<svg width="252" height="252" viewBox="0 0 252 252" style="position: absolute; inset: 0; transform: rotate(135deg)"><defs><linearGradient id="gd%s" x1="0" y1="0" x2="1" y2="1">%s</linearGradient></defs>'
         '<circle cx="126" cy="126" r="%d" fill="none" stroke="%s" stroke-width="12" stroke-dasharray="%.1f %.1f" stroke-linecap="round"/>'
         '<circle cx="126" cy="126" r="%d" fill="none" stroke="url(#gd%s)" stroke-width="12" stroke-dasharray="%.1f %.1f" stroke-linecap="round"/></svg>'
         % (mode, stops, R, 'rgba(255,255,255,.6)' if mode == 'day' else 'rgba(255,255,255,.18)', C * .75, C, R, mode, C * .75 * f, C))
    a = math.radians(135 + 270 * min(best / vmax, 1))
    bx, by = 126 + (R + 17) * math.cos(a), 126 + (R + 17) * math.sin(a)
    ix, iy = 126 + R * math.cos(a), 126 + R * math.sin(a)
    tick = ('<span style="position: absolute; left: %.0fpx; top: %.0fpx; transform: translate(-50%%,-50%%); font-size: 11.5px; font-weight: 700; direction: ltr; color: var(--ink); text-shadow: 0 0 6px var(--halo)">%d</span>'
            '<span style="position: absolute; left: %.0fpx; top: %.0fpx; width: 4px; height: 20px; border-radius: 2px; background: var(--ink); transform: translate(-50%%,-50%%) rotate(%.0fdeg)"></span>'
            % (bx, by, best, ix, iy, math.degrees(a) + 90))
    return ('<div class="gd">%s%s%s<div class="in"><div class="v"><b>%d</b><span>קמ״ש</span></div><div class="rn">%s<bdi>%s</bdi></div></div></div>'
            % (win, g, tick, v, sign(run), run))


def gd_down(night=False, still=False, slow=False):
    if slow:
        d = D.DESC[2]; t = d['t0'] + 25000
    else:
        t = D.ST['max_at']['t'] - 3000
    light = 'night' if night else 'morning'; m = 'night' if night else 'day'
    rg, _ = me(t)
    v = round(float(D.KMH[D.where(VH, t)[3]]))
    run = 'Sadzele 1' if not slow else 'Goodaura 2'
    return pic('hero', light, None) + daylines(t, m) + rg + recp(mm(t - DAY)) + dash(v, t, mode=m, run=run, still=still) + hold()


def gd_phones():
    return {'GD1.dc.html': ('המסך החי · לוח מחוונים, מהר, ביום', gd_down(), 'day'),
            'GD2.dc.html': ('המסך החי · לוח מחוונים, מהר, בלילה', gd_down(True), 'night'),
            'GD3.dc.html': ('המסך החי · לוח מחוונים, לאט, ביום', gd_down(slow=True), 'day'),
            'GD4.dc.html': ('המסך החי · לוח מחוונים, בהפחתת תנועה', gd_down(still=True), 'day')}


HGD = 760


def gd00():
    inner = ('<p class="sec">הערות על גרסה 56 · המסך החי, בירידה</p><h1>הטבעת כלוח מחוונים: ההר בפנים, והשלג עף לפי המהירות</h1>'
             '<div class="rowx" style="align-items: stretch"><div class="card" style="flex: 1"><h2>מה בתוך הטבעת</h2>%s</div>'
             '<div class="card" style="flex: 1"><h2>ביושר</h2>%s</div></div>'
             '<div class="card"><h2>ההחלטה שלי: כן, ליד הקיים (GS6, GS7)</h2>%s%s</div>' % (
                 li('<b>חלון להר:</b> ההר המצויר של השעה, מקרוב, ואתה בתחתית החלון (הנקודה הכחולה). רואים איפה אתה בלי להוריד את העיניים מהמספר.',
                    '<b>שלג שעף:</b> פתיתים חוצים את החלון. לאט הם יורדים רכים, ומהר הם נמתחים לפסים ועפים מהר. כך מרגישים את המהירות גם בלי לקרוא.',
                    '<b>המספר נשאר הדבר הראשון:</b> החלון מואר מאחורי המספר ביום, ומוחשך בלילה, כדי שהספרות תמיד קריאות.',
                    '<b>בהפחתת תנועה:</b> הפתיתים עומדים, והפסים קצרים לפי המהירות.'),
                 li('<b>הפתיתים מדומים,</b> לא שלג אמיתי שיורד עכשיו. אם אין שלג בתחזית, אפשר לכבות אותם (שאלה 2).',
                    '<b>נבדק ברינדור מקומי,</b> לא בטלפון. 26 פתיתים בתנועה עולים מעט סוללה; לבדוק בטלפון בינוני.',
                    '<b>ההר בחלון</b> הוא התמונה המצוירת. במודל החי זה יהיה מבט מלמעלה על המקום שלך.'),
                 li('זה מוסיף מה שהיה חסר בטבעת: תחושה של תנועה ומקום, בלי עוד טקסט. מתאים ליום הגלישה, שצריך להיות הכי מרהיב.',
                    'הלוחות GD1 עד GD4 ליד המצבים הקיימים: מהר ביום ובלילה, לאט, ובהפחתת תנועה.'),
                 q(1, '<b>לוח המחוונים או הטבעת הפשוטה?</b> <b>(ההמלצה: לוח המחוונים)</b>') + q(2, '<b>שלג תמיד, או רק כשיורד שלג בתחזית?</b> <b>(ההמלצה: תמיד; הוא אומר "מהירות", לא "מזג אוויר")</b>')))
    wb('GD00-Dashboard.dc.html', 'המסך החי: הטבעת כלוח מחוונים', 1280, HGD, inner)


PREFIX = ('RX', 'TM', 'GD')


def main():
    ph = {}
    for d in (rx_phones(), tm_phones(), gd_phones()): ph.update(d)
    for fn, (title, body, fx) in ph.items():
        PB.phone(fn, title, body, night=fx == 'night', fixed=fx, lang='he')
    rx00(); tm00(); gd00()
    p = os.path.join(PROJ, 'canvas.json'); j = json.load(open(p, encoding='utf8'))
    for fn in list(j['boards']):
        if fn.startswith(PREFIX): del j['boards'][fn]
    j['order'] = [f for f in j['order'] if not f.startswith(PREFIX)]
    open(p, 'w', encoding='utf8').write(json.dumps(j, ensure_ascii=False, indent=1) + '\n')
    y = max(v['y'] + v['h'] for v in j['boards'].values() if v.get('page') == PAGE) + 260
    V.PLACE.clear()
    for board, H, title, rows in [
        ('RX00-RecButton2.dc.html', HRX, 'כפתור ההקלטה: בלי מילה, שלושה מקומות',
         [('RX1', 'א · בסרגל, ביום'), ('RX2', 'א · בזמן הקלטה, בלילה'), ('RX3', 'ב · צף, ביום (ההמלצה)'), ('RX4', 'ב · בזמן הקלטה, בלילה'),
          ('RX5', 'ג · באמצע הסרגל, ביום'), ('RX6', 'ג · בזמן הקלטה, בלילה')]),
        ('TM00-Counter.dc.html', HTM, 'מסך החיפוש: מונה או בלי',
         [('TM1d', 'עם מונה, ביום (ההמלצה)'), ('TM1n', 'עם מונה, אחרי 30 שניות, בלילה'), ('TM2d', 'בלי זמן, ביום'), ('TM2f', 'בלי זמן: המונה מתחיל ב-0:00')]),
        ('GD00-Dashboard.dc.html', HGD, 'המסך החי: הטבעת כלוח מחוונים',
         [('GD1', 'מהר, ביום'), ('GD2', 'מהר, בלילה'), ('GD3', 'לאט, ביום'), ('GD4', 'בהפחתת תנועה')])]:
        V.PLACE.append((board, 0, y, 1280, H, title)); y += H + 140
        for i, (c, t) in enumerate(rows):
            V.PLACE.append(('%s.dc.html' % c, 470 * i, y, 390, 844, t))
        y += 844 + 260
    V.place_all(); print('ok', len(ph))


if __name__ == '__main__':
    main()
