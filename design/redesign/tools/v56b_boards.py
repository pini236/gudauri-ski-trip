#!/usr/bin/env python3
"""Pini's answers on the "הערות על גרסה 56" page (25.11.2026, via the manager), a second round on the same page:
  RX  the record button inside the bar: a red dot in a ring, and a red square while recording
  TM  the search screen, version 3 that Pini chose: with a small counter from the tap, or without time until a location
  GD  the speed ring of the live screen: the plain ring (GS6), decided
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
.rq{height:44px;min-width:44px;border-radius:22px;border:0;padding:0;flex:none;display:flex;align-items:center;gap:7px;background:none;font-family:inherit;color:var(--ink)}
.rq .o{position:relative;width:44px;height:44px;flex:none;display:flex;align-items:center;justify-content:center}
.rq .o svg{position:absolute;inset:0}
.rq .dt{width:30px;height:30px;border-radius:50%;background:#E5352B;box-shadow:inset 0 -2px 3px rgba(90,10,6,.25)}
.rq .sq{width:16px;height:16px;border-radius:3px;background:#E5352B}
.rq.on{padding-inline-end:12px;background:rgba(229,53,43,.12)}
.night .rq.on{background:rgba(255,116,104,.16)}
.rq .tm{font-size:16px;font-weight:700;font-variant-numeric:tabular-nums;direction:ltr;line-height:1}
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


def rc(state='idle', t=TIME, timer=True):
    """the record button as decided (Pini, 25.11.2026, second answer): inside the bar, at the end, the height of the
    other buttons (44). Before recording: a red dot in a ring, like any camera. While recording: a red square (stop),
    and the time next to it in the same button (timer=False: the time moves to the top pill)"""
    ring = ('<svg viewBox="0 0 44 44"><circle cx="22" cy="22" r="19.75" fill="none" stroke="currentColor" stroke-width="2.5"/></svg>')
    if state == 'idle':
        return '<button class="rq" aria-label="%s"><span class="o">%s<span class="dt"></span></span></button>' % (WORD, ring)
    lab = 'עצירת ההקלטה, %s' % t
    tm = '<span class="tm">%s</span>' % t if timer else ''
    return ('<button class="rq on%s" aria-label="%s"><span class="o">%s<span class="sq"></span></span>%s</button>'
            % ('' if timer else '" style="background: none; padding: 0', lab, ring, tm))


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
    return {'RX1.dc.html': ('כפתור ההקלטה · ביום', mtn() + top() + ctl() + dock_a(rc()), 'day'),
            'RX2.dc.html': ('כפתור ההקלטה · בלילה', mtn('night') + top() + ctl() + dock_a(rc()), 'night'),
            'RX3.dc.html': ('כפתור ההקלטה · בזמן הקלטה, ביום (אושר)', mtn() + top() + ctl() + dock_a(rc('live')), 'day'),
            'RX4.dc.html': ('כפתור ההקלטה · בזמן הקלטה, בלילה', mtn('night') + top() + ctl() + dock_a(rc('live')), 'night'),
            'RX5.dc.html': ('כפתור ההקלטה · חלופה שלא נבחרה: הזמן בגלולה העליונה', mtn() + top_rec() + ctl() + dock_a(rc('live', timer=False)), 'day')}


def opt(k, h2, sd, sn, pros, cons, rec=False):
    return ('<div class="card opt%s"><h2><span class="k">%s</span>%s</h2><div class="stage d">%s</div><div class="stage n">%s</div>'
            '<p class="pc"><b>מה עובד</b>%s</p><p class="pc"><b>מה חסר</b>%s</p></div>' % (' rec' if rec else '', k, h2, sd, sn, pros, cons))


def z(x):
    return '<div class="z">%s</div>' % x


HRX = 900


def rx00():
    st = lambda *xs: ''.join(z(x) for x in xs)
    look = ('<div class="card" style="flex: 1.25"><h2>בסרגל, כמו כפתור הקלטה של מצלמה</h2>'
            '<div class="stage d">%s</div><div class="stage n"><div class="night" style="display: contents; --ink: #EAF1F8; color: #EAF1F8">%s</div></div>' % (st(rc(), rc('live')), st(rc(), rc('live')))
            + '<p class="cap">מימין לשמאל: לפני הקלטה, ובזמן הקלטה.</p></div>')
    spec = ('<div class="card" style="flex: 1"><h2>הערכים</h2>' + table_rows([
        ('מקום', 'בתוך הסרגל התחתון, בסוף שלו (אחרי "לאן יורדים?"). בגובה 44, כמו "הכפר". לא יוצא מהסרגל, גם בזמן הקלטה.'),
        ('לפני הקלטה', 'טבעת בצבע הטקסט (ביום כהה, בלילה בהיר), קוטר 44 ועובי 2.5, ובתוכה עיגול אדום מלא <span dir="ltr">#E5352B</span>, קוטר 30. בלי עקבות.'),
        ('בזמן הקלטה', 'אותה טבעת, ובתוכה ריבוע אדום 16, פינות 3: "עצירה". לידו הזמן, 16, משקל 700. הכפתור כולו גלולה ברקע אדום שקוף (12 אחוז ביום, 16 בלילה). "לאן יורדים?" מתכווץ.'),
        ('תנועה', 'בלי. הזמן מתעדכן כל שנייה. במעבר בין המצבים: העיגול הופך לריבוע ב-200 מ״ש.'),
        ('נגיעה', 'לפני הקלטה: הגיליון של L01. בזמן הקלטה: המסך החי (שם "החזקה לסיום"). הנגיעה לא עוצרת לבד.'),
        ('קורא מסך', 'לפני: "%s" (<span dir="ltr">app.rec_sign</span>). בזמן: "עצירת ההקלטה" ואחריו הזמן; טקסט חדש לסשן התוכן.' % WORD)]) + '</div>')
    inner = ('<p class="sec">הערות על גרסה 56 · כפתור ההקלטה · אושר (פיני, 25.11.2026): הזמן בכפתור, RX3</p><h1>כפתור ההקלטה: בתוך הסרגל, עיגול ואז ריבוע</h1>'
             '<div class="rowx" style="align-items: stretch">%s%s</div>'
             '<div class="rowx" style="align-items: stretch"><div class="card" style="flex: 1"><h2>מה החליט פיני</h2>%s</div>'
             '<div class="card" style="flex: 1"><h2>החלופה שלא נבחרה: הזמן בגלולה העליונה (RX5)</h2>%s</div></div>' % (look, spec, li(
                 'הכפתור נשאר בתוך הסרגל התחתון ובגובה של שאר הכפתורים, ונראה כמו כפתור הקלטה.',
                 'עיגול אדום לפני הקלטה, ריבוע אדום בזמן הקלטה, כמו במצלמה. בלי העקבות.',
                 'משולש של "פליי" לא נבחר: הוא אומר "ניגון", לא "הקלטה".',
                 'המסירה: <span dir="ltr">design/handoff/2026-11-25-redesign-record-button</span>.'), li(
                 'בזמן הקלטה הכפתור נשאר עיגול עם ריבוע, והזמן עולה לגלולה העליונה.',
                 'מתי עדיף: אם ברוסית או בגאורגית "לאן יורדים?" נחתך כשהכפתור מתרחב.',
                 'פיני אישר את ההמלצה: הזמן בכפתור (RX3). החלופה נשארת רק אם בשפה ארוכה הסרגל נחתך.')))
    wb('RX00-RecButton2.dc.html', 'כפתור ההקלטה: בתוך הסרגל (אושר)', 1280, HRX, inner)


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


# ---------------------------------------------------------------- GD: the speed ring, decided
def gd_phones():
    return {'GD1.dc.html': ('המסך החי · בירידה, ביום', V.gs_down(), 'day'),
            'GD2.dc.html': ('המסך החי · בירידה, בלילה', V.gs_down(True), 'night')}


HGD = 380


def gd00():
    inner = ('<p class="sec">הערות על גרסה 56 · המסך החי, בירידה · הוחלט (פיני, 25.11.2026)</p><h1>מד המהירות: הטבעת הפשוטה</h1>'
             '<div class="card"><h2>מה החליט פיני</h2>%s</div>' % li(
                 'בלי המד בסגנון רכב ובלי לוח המחוונים עם השלג.',
                 'נשארת הטבעת הפשוטה של GS6: המספר הגדול, הטבעת בצבעי המהירות, וסימן קטן לשיא של היום.',
                 'המסירה של המסך החי עודכנה: <span dir="ltr">design/handoff/2026-11-25-redesign-search-pulse</span>.'))
    wb('GD00-Dashboard.dc.html', 'מד המהירות: הטבעת הפשוטה', 1280, HGD, inner)


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
        ('RX00-RecButton2.dc.html', HRX, 'כפתור ההקלטה: בתוך הסרגל (אושר)',
         [('RX1', 'ביום'), ('RX2', 'בלילה'), ('RX3', 'בזמן הקלטה, ביום (אושר)'), ('RX4', 'בזמן הקלטה, בלילה'), ('RX5', 'חלופה שלא נבחרה')]),
        ('TM00-Counter.dc.html', HTM, 'מסך החיפוש: בלי מונה (הוחלט)',
         [('TM1d', 'ביום'), ('TM1n', 'אחרי 30 שניות, בלילה'), ('TM2f', 'נמצא מיקום: 0:00')]),
        ('GD00-Dashboard.dc.html', HGD, 'מד המהירות: הטבעת הפשוטה',
         [('GD1', 'בירידה, ביום'), ('GD2', 'בירידה, בלילה')])]:
        V.PLACE.append((board, 0, y, 1280, H, title)); y += H + 140
        for i, (c, t) in enumerate(rows):
            V.PLACE.append(('%s.dc.html' % c, 470 * i, y, 390, 844, t))
        y += 844 + 260
    V.place_all(); print('ok', len(ph))


if __name__ == '__main__':
    main()
