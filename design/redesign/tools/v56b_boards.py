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
@keyframes rdp{0%,100%{transform:scale(1)}50%{transform:scale(.9)}}
.rb{position:relative;width:64px;height:64px;border-radius:50%;border:0;padding:0;flex:none;display:flex;align-items:center;justify-content:center;background:rgba(14,34,51,.30);-webkit-backdrop-filter:blur(10px);backdrop-filter:blur(10px);box-shadow:0 6px 18px rgba(8,18,36,.30);font-family:inherit;color:#fff}
.night .rb{background:rgba(0,0,0,.40);box-shadow:0 6px 18px rgba(0,0,0,.45)}
.rb .rg{position:absolute;inset:0;overflow:visible}
.rb .dt{position:relative;width:44px;height:44px;border-radius:50%;background:#E5352B;display:flex;align-items:center;justify-content:center;box-shadow:inset 0 -2px 4px rgba(90,10,6,.25)}
.rb.live .dt{animation:rdp 2.4s ease-in-out infinite}
.rb.live .ar{stroke-dasharray:1;animation:arc 60s linear infinite}
.rb .dt .stm{font-size:12.5px;font-weight:700;font-variant-numeric:tabular-nums;direction:ltr;line-height:1}
.rb.still .dt,.rb.still .ar{animation:none!important}
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


def tracks2(px=20):
    """the ski tracks, quiet: two thin white lines inside the dot, no motion"""
    return ('<svg width="%d" height="%d" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-linecap="round" stroke-width="1.6" stroke-opacity=".85" aria-hidden="true">'
            '<path d="M17.4 3.2C9.4 4.2 6.4 6.8 10.2 9.6C14 12.4 14.6 15.4 7.4 18.4"/><path d="M20.6 4.8C13.6 5.8 11 8 14 10.6C17.2 13.4 17.6 16.6 10.6 20"/></svg>' % (px, px))


def rb(state='idle', t=TIME, still=False, plain=False, prog=.3):
    """the record button as decided (25.11.2026): 64, floating in the thumb corner. A white ring, and in it a red dot;
    in the dot the ski tracks, quiet (or nothing: plain). While recording the dot breathes, the ring fills once a minute,
    and the time is in the dot"""
    d = 64; r = 29.5
    base = '<circle cx="32" cy="32" r="%.1f" fill="none" stroke="#fff" stroke-opacity="%s" stroke-width="3"/>' % (r, '.45' if state == 'live' else '1')
    arc = ''
    if state == 'live':
        arc = ('<circle class="ar" pathLength="1" cx="32" cy="32" r="%.1f" fill="none" stroke="#fff" stroke-width="3" stroke-linecap="round" '
               'transform="rotate(-90 32 32)" style="stroke-dashoffset: %.2f; animation-delay: -%.0fs"/>' % (r, 1 - prog, 60 * prog))
    inner = '<span class="stm">%s</span>' % t if state == 'live' else ('' if plain else tracks2())
    lab = WORD if state == 'idle' else '%s, %s' % (WORD, t)
    return ('<button class="rb %s%s" aria-label="%s"><svg class="rg" viewBox="0 0 64 64">%s%s</svg><span class="dt">%s</span></button>'
            % (state, ' still' if still else '', lab, base, arc, inner))


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
    f = lambda b: '<div class="fabw">%s</div>' % b
    return {'RX1.dc.html': ('כפתור ההקלטה · ביום', mtn() + top() + ctl() + dock_plain() + f(rb()), 'day'),
            'RX2.dc.html': ('כפתור ההקלטה · בלילה', mtn('night') + top() + ctl() + dock_plain() + f(rb()), 'night'),
            'RX3.dc.html': ('כפתור ההקלטה · בזמן הקלטה, ביום', mtn() + top() + ctl() + dock_plain() + f(rb('live')), 'day'),
            'RX4.dc.html': ('כפתור ההקלטה · בזמן הקלטה, בלילה', mtn('night') + top() + ctl() + dock_plain() + f(rb('live')), 'night')}


def opt(k, h2, sd, sn, pros, cons, rec=False):
    return ('<div class="card opt%s"><h2><span class="k">%s</span>%s</h2><div class="stage d">%s</div><div class="stage n">%s</div>'
            '<p class="pc"><b>מה עובד</b>%s</p><p class="pc"><b>מה חסר</b>%s</p></div>' % (' rec' if rec else '', k, h2, sd, sn, pros, cons))


def z(x):
    return '<div class="z">%s</div>' % x


HRX = 910


def rx00():
    st = lambda *xs: ''.join(z(x) for x in xs)
    look = ('<div class="card" style="flex: 1.3"><h2>הצורה: נקודה אדומה בתוך טבעת לבנה</h2>'
            '<div class="stage d">%s</div><div class="stage n">%s</div>' % (st(rb(), rb(plain=True), rb('live')), st(rb(), rb(plain=True), rb('live')))
            + '<p class="cap">מימין לשמאל: עם העקבות, בלי כלום, ובזמן הקלטה.</p></div>')
    spec = ('<div class="card" style="flex: 1"><h2>הערכים</h2>' + table_rows([
        ('מקום', 'צף, 12 מימין ו-86 מלמטה (מעל הסרגל), בפינה של האגודל. בשפות משמאל לימין: באותה פינה.'),
        ('גודל', '64. הטבעת לבנה, 3, ברדיוס 29.5. הנקודה האדומה <span dir="ltr">#E5352B</span>, קוטר 44.'),
        ('רקע', 'זכוכית כהה מאחורי הטבעת (ביום 30 אחוז, בלילה 40), כדי שהטבעת הלבנה תיראה גם על שלג.'),
        ('בתוך הנקודה', 'שני קווי העקבות, דקים ולבנים (85 אחוז), בלי תנועה. אם זה מבלבל: בלי כלום.'),
        ('בזמן הקלטה', 'הנקודה נושמת לאט (90 עד 100 אחוז, 2.4 שניות), הטבעת מתמלאת פעם בדקה, והזמן בתוך הנקודה.'),
        ('נגיעה', 'כמו היום: לפני הקלטה פותחת את הגיליון של L01, ובזמן הקלטה את המסך החי.'),
        ('הפחתת תנועה', 'בלי נשימה ובלי מילוי; הזמן מתעדכן.'),
        ('קורא מסך', '"%s", ובזמן הקלטה גם הזמן (<span dir="ltr">app.rec_sign</span>).' % WORD)]) + '</div>')
    inner = ('<p class="sec">הערות על גרסה 56 · כפתור ההקלטה · הוחלט (פיני, 25.11.2026)</p><h1>כפתור ההקלטה: צף, וכמו כפתור הקלטה מוכר</h1>'
             '<div class="rowx" style="align-items: stretch">%s%s</div>'
             '<div class="card"><h2>מה החליט פיני</h2>%s</div>' % (look, spec, li(
                 'הכפתור נראה מיד כמו כפתור הקלטה: נקודה אדומה בתוך טבעת לבנה. העקבות בפנים עדינות ובלי תנועה; אם לא יהיה מספיק ברור, חושבים שוב.',
                 'המקום: ב, צף מעל הסרגל בפינה של האגודל. הסרגל נשאר "הכפר" ו"לאן יורדים?".',
                 'המסירה: <span dir="ltr">design/handoff/2026-11-25-redesign-record-button</span>.')))
    wb('RX00-RecButton2.dc.html', 'כפתור ההקלטה: הוחלט', 1280, HRX, inner)


def table_rows(rows):
    return ('<table style="border-collapse: collapse; width: 100%%; font-size: 14.5px; line-height: 1.45">%s</table>'
            % ''.join('<tr style="border-top: 1px solid #D9DEE4"><td style="padding: 7px 10px; vertical-align: top; width: 22%%"><b>%s</b></td><td style="padding: 7px 10px; vertical-align: top">%s</td></tr>' % r for r in rows))


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
    return {'TM1d.dc.html': ('מסך החיפוש · ביום', search3(False), 'day'),
            'TM1n.dc.html': ('מסך החיפוש · אחרי 30 שניות, בלילה', search3(True, long=True), 'night'),
            'TM2f.dc.html': ('נמצא מיקום: המסך החי, והמונה מתחיל ב-0:00', found0(), 'day')}


HTM = 420


def tm00():
    inner = ('<p class="sec">הערות על גרסה 56 · מסך החיפוש · הוחלט (פיני, 25.11.2026)</p><h1>מסך החיפוש: בלי מונה</h1>'
             '<div class="card"><h2>מה החליט פיני</h2>%s</div>' % li(
                 'בזמן החיפוש אין מספר: רק הפעימה ו"מחפש מיקום".',
                 'כשנמצא מיקום, המסך עובר לבד למסך החי, והמונה מתחיל שם ב-0:00. הוא סופר מרגע שנמצא מיקום, לא מהנגיעה.',
                 'השורה "בחוץ זה לוקח בדרך כלל פחות מדקה" מופיעה רק אחרי 30 שניות בלי מיקום.',
                 '"החזקה לסיום" נשאר, כדי שאפשר לוותר.',
                 'המסירה עודכנה: <span dir="ltr">design/handoff/2026-11-25-redesign-search-pulse</span>.'))
    wb('TM00-Counter.dc.html', 'מסך החיפוש: בלי מונה', 1280, HTM, inner)


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


CCSS = """
.H .cg{position:absolute;left:50%;bottom:92px;width:240px;height:240px;transform:translateX(-50%)}
.H .cg .disc{position:absolute;inset:0;border-radius:50%}
.H .cg svg{position:absolute;inset:0;overflow:visible}
.H .cg .tk{stroke:var(--ink);stroke-linecap:round}
.H .night .cg .tk{stroke:#DCE8FF;filter:drop-shadow(0 0 2px rgba(170,200,255,.55))}
.H .cg .nm{position:absolute;transform:translate(-50%,-50%);font-size:11px;font-weight:500;color:var(--ink2);direction:ltr;font-variant-numeric:tabular-nums}
.H .night .cg .nm{color:#B9C8E6}
.H .cg .nd{transition:transform .3s cubic-bezier(.3,.7,.3,1)}
.H .cg .rd{position:absolute;left:0;right:0;top:146px;display:flex;flex-direction:column;align-items:center;gap:2px}
.H .cg .rd b{font-size:48px;font-weight:300;line-height:.9;letter-spacing:-.03em;font-variant-numeric:tabular-nums;direction:ltr}
.H .cg .rd span{font-size:12px;font-weight:700;color:var(--ink2)}
.H .cg .rn{position:absolute;left:0;right:0;top:211px;display:flex;justify-content:center;align-items:center;gap:6px;font-size:12.5px;font-weight:700;transform:scale(.92)}
"""
G4.MB.CSS = G4.MB.CSS + CCSS


def mix(c1, c2, t):
    a = [int(c1[i:i + 2], 16) for i in (1, 3, 5)]; b = [int(c2[i:i + 2], 16) for i in (1, 3, 5)]
    return '#%02X%02X%02X' % tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def rampc(mode, f):
    r = D.RAMP[mode]; x = min(max(f, 0), 1) * (len(r) - 1); i = min(int(x), len(r) - 2)
    return mix(r[i], r[i + 1], x - i)


def car(v, mode='day', run='Sadzele 1', best=61, vmax=80):
    """a quiet car speedometer (Pini, 25.11.2026: "a gentle, beautiful gauge, in the style of a car, that fits here"):
    fine ticks on frosted glass, a thin needle in the colour of the speed, and the number small under the hub"""
    cx = cy = 120; R = 104; a0 = 135; sw = 270
    ang = lambda k: math.radians(a0 + sw * min(max(k / vmax, 0), 1))
    o = []
    for k in range(0, vmax + 1, 5):
        mj = k % 20 == 0; a = ang(k); r1 = R - (12 if mj else 6)
        o.append('<line class="tk" x1="%.1f" y1="%.1f" x2="%.1f" y2="%.1f" stroke-width="%s" stroke-opacity="%s"/>'
                 % (cx + R * math.cos(a), cy + R * math.sin(a), cx + r1 * math.cos(a), cy + r1 * math.sin(a), '2' if mj else '1', '.9' if mj else '.45'))
    nums = ''.join('<span class="nm" style="left: %.0fpx; top: %.0fpx">%d</span>' % (cx + (R - 26) * math.cos(ang(k)), cy + (R - 26) * math.sin(ang(k)), k)
                   for k in range(0, vmax + 1, 20))
    f = min(v / vmax, 1); col = rampc(mode, f)
    stops = ''.join('<stop offset="%d%%" stop-color="%s"/>' % (i * 25, c) for i, c in enumerate(D.RAMP[mode]))
    C = 2 * math.pi * (R + 7)
    trail = ('<defs><linearGradient id="cg%s" x1="0" y1="1" x2="1" y2="0">%s</linearGradient></defs>'
             '<circle cx="%d" cy="%d" r="%d" fill="none" stroke="url(#cg%s)" stroke-width="3" stroke-linecap="round" stroke-dasharray="%.1f %.1f" transform="rotate(%d %d %d)" opacity=".9"/>'
             % (mode, stops, cx, cy, R + 7, mode, C * sw / 360 * f, C, a0, cx, cy))
    ab = ang(best); bx, by = cx + (R + 7) * math.cos(ab), cy + (R + 7) * math.sin(ab)
    bmark = '<circle cx="%.1f" cy="%.1f" r="3" fill="none" stroke="var(--ink)" stroke-width="1.6"/>' % (bx, by)
    an = math.degrees(ang(v))
    needle = ('<g class="nd" style="transform-origin: %dpx %dpx; transform: rotate(%.1fdeg)">'
              '<path d="M%d %.1f L%d %.1f L%d %.1f L%d %.1f Z" fill="%s"/>'
              '<path d="M%d %d L%d %d" stroke="%s" stroke-width="6" stroke-linecap="round" opacity=".18"/></g>'
              % (cx, cy, an, cx - 14, cy - 1.6, cx + R - 16, cy - .7, cx + R - 16, cy + .7, cx - 14, cy + 1.6, col, cx, cy, cx + R - 18, cy, col))
    hub = '<circle cx="%d" cy="%d" r="7" fill="var(--bg)" stroke="%s" stroke-width="2.4"/>' % (cx, cy, col)
    svg = '<svg width="240" height="240" viewBox="0 0 240 240">%s%s%s%s%s</svg>' % (''.join(o), trail, bmark, needle, hub)
    return ('<div class="cg" role="img" aria-label="%d קמ״ש"><div class="disc glass"></div>%s%s<div class="rd"><b>%d</b><span>קמ״ש</span></div>'
            '<div class="rn">%s<bdi>%s</bdi></div></div>' % (v, svg, nums, v, sign(run), run))


def car_down(night=False, slow=False):
    if slow:
        d = D.DESC[2]; t = d['t0'] + 25000
    else:
        t = D.ST['max_at']['t'] - 3000
    light = 'night' if night else 'morning'; m = 'night' if night else 'day'
    rg, _ = me(t)
    v = round(float(D.KMH[D.where(VH, t)[3]]))
    run = 'Sadzele 1' if not slow else 'Goodaura 2'
    return pic('hero', light, None) + daylines(t, m) + rg + recp(mm(t - DAY)) + car(v, mode=m, run=run) + hold()


def gd_phones():
    return {'GD1.dc.html': ('המסך החי · הטבעת הפשוטה (GS6), ביום', V.gs_down(), 'day'),
            'GD2.dc.html': ('המסך החי · מד עדין, מהר, ביום', car_down(), 'day'),
            'GD3.dc.html': ('המסך החי · מד עדין, מהר, בלילה', car_down(True), 'night'),
            'GD4.dc.html': ('המסך החי · מד עדין, לאט, ביום', car_down(slow=True), 'day')}


HGD = 500


def gd00():
    inner = ('<p class="sec">הערות על גרסה 56 · המסך החי, בירידה · סבב 3</p><h1>מד המהירות: הטבעת הפשוטה, או מד עדין כמו ברכב</h1>'
             '<div class="rowx" style="align-items: stretch"><div class="card" style="flex: 1"><h2>המד העדין</h2>%s</div>'
             '<div class="card" style="flex: 1"><h2>ההמלצה: %s</h2>%s%s</div></div>' % (
                 li('<b>שנתות דקות</b> כל 5 קמ״ש, ומספרים קטנים כל 20, על זכוכית. בלילה השנתות זוהרות קלות.',
                    '<b>מחוג דק</b> בצבע של המהירות (אותו סולם של קווי האור), עם עקבה דקה מ-0 ועד המחוג. הוא נע ברכות, בלי לקפוץ.',
                    '<b>המספר מתחת לציר,</b> קטן יותר מהיום (48 ולא 92). את המהירות קוראים מהמחוג במבט.',
                    '<b>השיא של היום:</b> עיגול קטן על השנתות, בלי מספר.',
                    '<b>בלי שלג ובלי חלון.</b> בהפחתת תנועה המחוג קופץ בלי תנועה.'),
                 'מד עדין',
                 li('הטבעת הפשוטה ברורה, אבל היא פס צבע עם מספר ענק. המד אומר "מד מהירות" מיד, ונראה כמו מכשיר מדויק ולא כמו גרף.',
                    'הוא שקט יותר מלוח המחוונים: אין תנועה חוץ מהמחוג.',
                    'החיסרון: המספר קטן יותר. בירידה, בכפפות, מבט מהיר על המחוג מספיק; אם פיני רוצה מספר גדול, אפשר להגדיל ל-60 בלי לשבור את המד.'),
                 q(1, '<b>מד עדין או הטבעת הפשוטה (GS6)?</b> <b>(ההמלצה: מד עדין)</b>')))
    wb('GD00-Dashboard.dc.html', 'מד המהירות: טבעת או מד עדין', 1280, HGD, inner)


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
        ('RX00-RecButton2.dc.html', HRX, 'כפתור ההקלטה: הוחלט',
         [('RX1', 'ביום'), ('RX2', 'בלילה'), ('RX3', 'בזמן הקלטה, ביום'), ('RX4', 'בזמן הקלטה, בלילה')]),
        ('TM00-Counter.dc.html', HTM, 'מסך החיפוש: בלי מונה (הוחלט)',
         [('TM1d', 'ביום'), ('TM1n', 'אחרי 30 שניות, בלילה'), ('TM2f', 'נמצא מיקום: 0:00')]),
        ('GD00-Dashboard.dc.html', HGD, 'מד המהירות: טבעת או מד עדין',
         [('GD1', 'הטבעת הפשוטה (GS6)'), ('GD2', 'מד עדין, מהר, ביום (ההמלצה)'), ('GD3', 'מד עדין, מהר, בלילה'), ('GD4', 'מד עדין, לאט, ביום')])]:
        V.PLACE.append((board, 0, y, 1280, H, title)); y += H + 140
        for i, (c, t) in enumerate(rows):
            V.PLACE.append(('%s.dc.html' % c, 470 * i, y, 390, 844, t))
        y += 844 + 260
    V.place_all(); print('ok', len(ph))


if __name__ == '__main__':
    main()
