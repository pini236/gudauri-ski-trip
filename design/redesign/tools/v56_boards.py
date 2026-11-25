#!/usr/bin/env python3
"""Pini's notes on canvas v56 (25.11.2026, via the manager), one canvas page ("הערות על גרסה 56"):
  GS  the live recording screen in the style Pini loved (the pulsing rings of the search), for every state;
      GS1/GS2 (the search) go to the executor now, the rest separately
  OP  the opening: picture against the live model after the Y02 ring, honestly; the picture/live switch in the button column
  RC  the record button: a name in native Hebrew, and the hint anchored to the button itself in both directions
  PS  before the season, a new round
  TR  showing what the app can do, without an onboarding
The mountain, runs, lifts, dates and prices come from the repo; examples carry the tag. Design only.
Usage: python3 design/redesign/tools/v56_boards.py"""
import os, json, datetime, math
import numpy as np
from peak_boards import *
import peak_boards as PB
import gap4_boards as G4
from gap4_boards import top, ctl, ic, P_HOME, P_SRCH, P_LAY, P_LOC, P_CMP
from peak_b4 import EG, veil, fade, you as B4you
from gap_boards import li
import day4 as D
from live_boards import board, wide as wbox, PROJ

PAGE = 'v56'
CSS = """
@keyframes gsr{0%{transform:translate(-50%,-50%) scale(.22);opacity:.95}70%{opacity:.35}100%{transform:translate(-50%,-50%) scale(1);opacity:0}}
@keyframes gsb{0%,100%{transform:translate(-50%,-50%) scale(1)}50%{transform:translate(-50%,-50%) scale(1.06)}}
@keyframes gsl{0%,100%{opacity:.55}50%{opacity:1}}
.H .gs{position:absolute;border-radius:50%;width:420px;height:420px;transform:translate(-50%,-50%) scale(.22);border:2px solid rgba(255,255,255,.95);box-shadow:0 0 24px rgba(255,255,255,.55),inset 0 0 24px rgba(255,255,255,.35);animation:gsr 2.4s cubic-bezier(.2,.6,.3,1) infinite;opacity:0}
.H .night .gs{border-color:rgba(176,206,255,.9);box-shadow:0 0 26px rgba(120,170,255,.45),inset 0 0 22px rgba(120,170,255,.3)}
.H .gs.b{animation-delay:.8s}.H .gs.c{animation-delay:1.6s}
.H .gsc{position:absolute;width:92px;height:92px;border-radius:50%;transform:translate(-50%,-50%);display:flex;align-items:center;justify-content:center;color:var(--ink);animation:gsb 2.4s ease-in-out infinite}
.H .gsc svg{animation:gsl 2.4s ease-in-out infinite}
.H .st3{position:absolute;left:12px;right:12px;top:106px;height:40px;border-radius:20px;display:flex;align-items:center;gap:6px;padding:0 14px;box-sizing:border-box;font-size:13px;white-space:nowrap}
.H .st3 span{display:flex;align-items:center;gap:6px;color:var(--ink2)}
.H .st3 span i{width:8px;height:8px;border-radius:50%;border:1.5px solid currentColor;box-sizing:border-box;flex:none}
.H .st3 span.on{color:var(--ink);font-weight:700}.H .st3 span.on i{background:#E5352B;border-color:#E5352B;box-shadow:0 0 0 4px rgba(229,53,43,.22)}
.H .st3 span.ok i{background:currentColor}
.H .st3 b{flex:1;height:1px;background:var(--line);min-width:8px}
.H .gst{position:absolute;left:0;right:0;display:flex;flex-direction:column;align-items:center;gap:6px;text-align:center;text-shadow:0 0 18px var(--halo),0 0 4px var(--halo)}
.H .gst b{font-size:26px;font-weight:700;line-height:1.2}
.H .gst span{font-size:15px;line-height:1.4;color:var(--ink)}
.H .recp2{position:absolute;top:52px;left:12px;right:12px;height:44px;border-radius:22px;display:flex;align-items:center;gap:9px;padding:0 14px;box-sizing:border-box;font-size:14.5px;font-weight:500;white-space:nowrap}
.H .recp2 .tmr{margin-inline-start:auto}
"""
G4.MB.CSS = G4.MB.CSS + CSS

P_AIM = ('<circle cx="12" cy="12" r="6.5"/><circle cx="12" cy="12" r="1.6" fill="currentColor"/>'
         '<path d="M12 2.500v3.200M12 18.300v3.200M2.500 12h3.200M18.300 12h3.200"/>')
REC = 'מעקב גלישה'


def recp(t='0:14'):
    return '<div class="recp2 glass"><span class="rdot"></span><span>%s</span><span class="tmr">%s</span></div>' % (REC, t)


def steps(k):
    """the three steps; k = index of the current one"""
    names = ['מחפש מיקום', 'מודד', 'סיכום היום']
    o = []
    for i, n in enumerate(names):
        o.append('<span class="%s"><i></i>%s</span>' % ('on' if i == k else ('ok' if i < k else ''), n))
    return '<div class="st3 glass">%s</div>' % '<b></b>'.join(o)


def hold():
    return '<div class="hold glass"><i></i>החזקה לסיום</div>'


def rings(x, y, still=False):
    if still:   # reduced motion: three fixed rings
        return ''.join('<div class="gs" style="left: %dpx; top: %dpx; animation: none; opacity: %s; transform: translate(-50%%,-50%%) scale(%s)"></div>' % (x, y, op, s)
                       for s, op in (('.42', '.85'), ('.66', '.45'), ('.9', '.2')))
    return ''.join('<div class="gs %s" style="left: %dpx; top: %dpx"></div>' % (c, x, y) for c in ('', 'b', 'c'))


def gs_search(night, still=False):
    light = 'night' if night else 'morning'
    cx, cy = 195, 330
    o = pic('hero', light, None) + veil(0, 844, 'var(--veil)')
    o += rings(cx, cy, still)
    o += '<div class="gsc glass" style="left: %dpx; top: %dpx">%s</div>' % (cx, cy, ic(P_AIM, 40, 1.6))
    o += ('<div class="gst" style="top: %dpx"><b>מחפש מיקום</b><span>בחוץ, מתחת לשמיים פתוחים,<br>זה לוקח בדרך כלל פחות מדקה</span></div>' % (cy + 150))
    o += recp() + steps(0) + hold()
    return o


def phones():
    out = {}
    out['GS1.dc.html'] = ('המסך החי · מחפש מיקום, ביום', gs_search(False), 'day')
    out['GS2.dc.html'] = ('המסך החי · מחפש מיקום, בלילה', gs_search(True), 'night')
    out['GS3.dc.html'] = ('המסך החי · מחפש מיקום, בהפחתת תנועה', gs_search(False, still=True), 'day')
    return out


PLACE = []   # (file, x, y, w, h, title without the code)


def place_all():
    p = os.path.join(PROJ, 'canvas.json'); j = json.load(open(p, encoding='utf8'))
    for fn, x, y, w, h, t in PLACE:
        code = fn.split('.dc.html')[0].split('-')[0]
        j['boards'][fn] = dict(x=x, y=y, w=w, h=h, title='%s (%s)' % (t, code), page=PAGE)
        if fn not in j['order']: j['order'].append(fn)
    if not any(pg['id'] == PAGE for pg in j['pages']): j['pages'].insert(0, dict(id=PAGE, name='הערות על גרסה 56'))
    j['notes']['t' + PAGE] = dict(kind='title1', maxW=1600, page=PAGE, x=0, w=1000, y=-200, text='הערות על גרסה 56 (25.11.2026)')
    open(p, 'w', encoding='utf8').write(json.dumps(j, ensure_ascii=False, indent=1) + '\n')


def main():
    ph = phones()
    for fn, (title, body, fx) in ph.items():
        PB.phone(fn, title, body, night=fx == 'night', fixed=fx)
    PLACE.extend([('GS1.dc.html', 0, 0, 390, 844, 'מחפש מיקום, ביום · לבנייה עכשיו'),
                  ('GS2.dc.html', 470, 0, 390, 844, 'מחפש מיקום, בלילה'),
                  ('GS3.dc.html', 940, 0, 390, 844, 'מחפש מיקום, בהפחתת תנועה')])
    place_all(); print('phones', len(ph))


if __name__ == '__main__':
    main()
