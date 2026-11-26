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
ANN = datetime.date(2026, 10, 28)  # MTA's announcement (site/data/resorts.json)
FRAC = (TODAY - ANN).days / (OPEN - ANN).days   # how much of the wait has passed

HCSS = """
@keyframes hnin{0%{opacity:0;transform:translateY(6px)}100%{opacity:1;transform:none}}
@keyframes hrin{0%{transform:scale(1.18);opacity:0}100%{transform:none;opacity:1}}
.H .hn{position:absolute;border-radius:16px;background:var(--ink);color:var(--bg);padding:10px 14px 10px 8px;box-sizing:border-box;font-size:14px;font-weight:500;line-height:1.4;box-shadow:0 8px 22px rgba(10,25,45,.3);width:max-content;max-width:300px;animation:hnin .2s ease-out both;z-index:5;display:flex;flex-direction:column;gap:8px}
.H [dir=ltr] .hn,.H .hn[dir=ltr]{padding:10px 8px 10px 14px}
.H .hn .r{display:flex;align-items:flex-start;gap:10px}
.H .hn .tx{flex:1;min-width:0;padding-top:4px}
.H .hn .hx{position:relative;width:32px;height:32px;flex:none;border:0;background:none;padding:0;color:var(--bg);display:flex;align-items:center;justify-content:center}
.H .hn .hx::before{content:'';position:absolute;inset:-6px}
.H .hn .hx svg.rg{position:absolute;inset:0}
.H .hn .hx .tr{stroke:currentColor;stroke-opacity:.22}
.H .hn .hx .ar{stroke:currentColor;stroke-dasharray:1;animation:drain 8s linear infinite}
.H .hn .hx.p50 .ar{animation:none;stroke-dashoffset:.5}.H .hn .hx.full .ar{animation:none;stroke-dashoffset:0}
@keyframes drain{0%{stroke-dashoffset:0}100%{stroke-dashoffset:1}}
.H .hn .dots{display:flex;gap:5px;align-items:center;padding-inline-start:0}
.H .hn .dots span{width:6px;height:6px;border-radius:3px;background:currentColor;opacity:.32}
.H .hn .dots span.on{width:16px;opacity:1}.H .hn .dots span.done{opacity:.6}
.H .hn i{position:absolute;width:12px;height:12px;background:var(--ink);transform:rotate(45deg);border-radius:2px}
.H .hr{position:absolute;box-sizing:border-box;border:2.5px solid var(--ink);box-shadow:0 0 0 2.5px rgba(255,255,255,.9),0 0 0 2400px rgba(8,16,30,.26);animation:hrin .6s ease-out both;pointer-events:none;z-index:4}
.H .night .hr{box-shadow:0 0 0 2.5px rgba(6,11,22,.75),0 0 0 2400px rgba(0,0,0,.34)}
.H .hr.run{background:rgba(255,255,255,.3)}
.H .anl{position:absolute;left:16px;right:16px;font-size:13px;font-weight:700;color:var(--ink);display:flex;gap:8px;align-items:center}
.H .anl b{display:inline-flex;align-items:center;justify-content:center;width:22px;height:22px;border-radius:11px;background:var(--acc);color:var(--onacc);font-size:12px}
.H .ctile{position:absolute;display:flex;align-items:center;gap:10px;padding:8px 14px 8px 12px;border-radius:18px;box-sizing:border-box}
.H .ctile .cn{font-size:34px;font-weight:300;line-height:1;direction:ltr;font-variant-numeric:tabular-nums;letter-spacing:-.02em}
.H .ctile .c{display:flex;flex-direction:column;gap:1px}
.H .ctile .c b{font-size:13.5px;font-weight:700;white-space:nowrap}
.H .ctile .c span{font-size:12px;color:var(--ink2);white-space:nowrap}
.H .ct{position:absolute;right:12px;top:690px;height:64px;min-width:64px;border-radius:18px;display:flex;align-items:center;gap:10px;padding:0 8px;box-sizing:border-box;border:0;font-family:inherit;color:var(--ink)}
.H [dir=ltr] .ct,.H .ct.ltr{right:auto;left:12px}
.H .ct .cr{position:relative;width:48px;height:48px;flex:none;display:flex;align-items:center;justify-content:center}
.H .ct .cr svg{position:absolute;inset:0}
.H .ct .cr .tk{stroke:var(--line);stroke-width:3}
.H .ct .cr .ac{stroke:var(--acc);stroke-width:3}
.H .ct .cr b{position:relative;font-size:20px;font-weight:600;font-variant-numeric:tabular-nums;direction:ltr;letter-spacing:-.01em}
.H .ct .cc{display:flex;flex-direction:column;gap:1px;text-align:start}
.H .ct .cc b{font-size:14px;font-weight:700;white-space:nowrap}
.H .ct .cc span{font-size:12px;color:var(--ink2);white-space:nowrap}
.H .ct .ch{color:var(--ink2);display:flex;padding-inline-end:4px}
.H .ct.open{padding-inline-end:10px;animation:ctin .25s ease-out both}
@keyframes ctin{0%{clip-path:inset(0 0 0 calc(100% - 64px) round 18px)}100%{clip-path:inset(0 0 0 0 round 18px)}}
.H .ss{position:absolute;left:0;right:0;bottom:0;border-radius:28px 28px 0 0;padding:10px 20px 26px;box-sizing:border-box;display:flex;flex-direction:column;gap:6px}
.H .ss .h b{display:block;font-size:17px;font-weight:700}.H .ss .h span{font-size:12.5px;color:var(--ink2)}
.H .ss .row{display:flex;align-items:center;gap:10px;min-height:52px;border-top:1px solid var(--line)}
.H .ss .row .t{flex:1;display:flex;flex-direction:column}.H .ss .row .t b{font-size:15px}.H .ss .row .t span{font-size:13px;color:var(--ink2)}
.H .ss .row .e{font-size:15px;font-weight:700}.H .ss .row .e.acc{color:var(--acc);font-weight:500}
.H .gpill{position:absolute;left:50%;transform:translateX(-50%);height:40px;border-radius:20px;display:flex;align-items:center;padding:0 18px;font-size:16px;font-weight:700;white-space:nowrap}
"""
G4.MB.CSS = G4.MB.CSS + HCSS

_S = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../../i18n/strings.json'), encoding='utf8'))['strings']
T = {k: _S['app.hint_' + k]['he'] for k in ('run', 'find', 'rec', 'village', 'mode', 'me', 'trip', 'group')}   # from i18n (content session)
EN = {'rec': _S['app.hint_rec']['en']}
AGAIN = _S['app.hints_again']['he']
ORDER = ['run', 'find', 'rec', 'village', 'mode', 'me']


P_BULB = '<path d="M9 18h6M10 21h4"/><path d="M12 3a6 6 0 0 0-3.600 10.800c.8.600 1.100 1.300 1.100 2.200h5c0-.9.300-1.600 1.100-2.200A6 6 0 0 0 12 3z"/>'


# ---------------------------------------------------------------- the one component
def ring(x, y, w, h, r=None, run=False):
    """the mark around the target: 5 outside the control, its own shape"""
    r = h / 2 if r is None else r
    return '<div class="hr%s" style="left: %.0fpx; top: %.0fpx; width: %.0fpx; height: %.0fpx; border-radius: %.0fpx"></div>' % (
        ' run' if run else '', x, y, w, h, r)


def xring(state=''):
    """the X inside a ring that drains over the hint's time (8 s); a tap closes all the hints"""
    return ('<button class="hx %s" aria-label="סגירת כל הרמזים"><svg class="rg" viewBox="0 0 32 32" fill="none" aria-hidden="true">'
            '<circle class="tr" cx="16" cy="16" r="14" stroke-width="2"/>'
            '<circle class="ar" pathLength="1" cx="16" cy="16" r="14" stroke-width="2" stroke-linecap="round" transform="rotate(-90 16 16)"/></svg>'
            '<svg width="12" height="12" viewBox="0 0 12 12" aria-hidden="true" style="position: relative"><path d="M2 2l8 8M10 2 2 10" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg></button>' % state)


def bubble(text, side, ax, frm='left', step=None, total=6, state='', **pos):
    """the bubble; its own width up to 300, placed by left/right/top/bottom. side = where the point is;
    ax = the point's centre from the bubble's edge frm. step: 1-based, for the dots"""
    pt = {'down': 'bottom: -5px', 'up': 'top: -5px', 'left': 'left: -5px', 'right': 'right: -5px'}[side]
    pt += '; %s: %dpx' % (frm, ax - 6)
    st = '; '.join('%s: %.0fpx' % (k, v) for k, v in pos.items())
    dots = ''
    if step:
        dots = '<div class="dots" aria-hidden="true">%s</div>' % ''.join(
            '<span class="%s"></span>' % ('on' if i == step else ('done' if i < step else '')) for i in range(1, total + 1))
    lab = ' aria-label="רמז %d מתוך %d"' % (step, total) if step else ''
    return ('<div class="hn" role="status"%s style="%s"><div class="r"><span class="tx">%s</span>%s</div>%s<i style="%s"></i></div>'
            % (lab, st, text, xring(state), dots, pt))


# ---------------------------------------------------------------- the mountain screen as built (RX3, Stage, before the season)
def season_top():
    return top(text='פתיחה מתוכננת 20.12.2026 · לפי <bdi>MTA</bdi>', eg=False, dot='sd q')


CHV = '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M15 6l-6 6 6 6"/></svg>'


def cring(n, frac=FRAC):
    """the number inside a ring: the ring fills as the wait passes (from MTA's announcement to the opening)"""
    return ('<span class="cr"><svg viewBox="0 0 48 48" fill="none" aria-hidden="true"><circle class="tk" cx="24" cy="24" r="21.5"/>'
            '<circle class="ac" cx="24" cy="24" r="21.5" pathLength="1" stroke-dasharray="%.3f 1" stroke-linecap="round" transform="rotate(-90 24 24)"/></svg>'
            '<b>%s</b></span>' % (frac, n))


def tile(days=DAYS, open_=False, en=False):
    """B8, version 2 (Pini, 26.11.2026): a small square with the number in a ring; a tap opens it to the caption for
    3.5 s, a tap on the open tile opens the season sheet"""
    lab = ('%d days to the planned opening, 20.12' % days) if en else ('%d ימים לפתיחה המתוכננת, 20.12' % days)
    cls = 'ct glass' + (' open' if open_ else '') + (' ltr' if en else '')
    inner = cring(days)
    if open_:
        cap = ('<b>days to opening</b><span>Lifts start <bdi>20.12</bdi></span>' if en else
               '<b>ימים לפתיחה</b><span>הרכבלים עולים ב-<bdi>20.12</bdi></span>')
        chv = CHV.replace('M15 6l-6 6 6 6', 'M9 6l6 6-6 6') if en else CHV
        inner += '<span class="cc">%s</span><span class="ch">%s</span>' % (cap, chv)
    return '<button class="%s" aria-label="%s">%s</button>' % (cls, lab, inner)


def season_sheet():
    return ('<div class="ss glass"><div class="grab"></div><div class="h" style="margin: 6px 0 6px"><b>פתיחה מתוכננת 20.12.2026</b>'
            '<span>יום ראשון · לפי <bdi>MTA</bdi> · מתוכננת, יכולה להשתנות עם השלג</span></div>'
            '<div class="row"><span class="t"><b>הטיול שלך</b></span><span class="e acc">מתי טסים?</span></div>'
            '<div class="row"><span class="t"><b>כרטיס סקי</b><span>כרטיס יום · מבוגר</span></span><span class="e">120 ₾</span></div></div>')


def screen(light='morning', pre=True, mode='pic'):
    m = (pic('hero', 'morning', 'night') + base('hero', cabins=True)) if light == 'morning' else mtn(light)
    o = m + (season_top() if pre else top()) + ctl_sw(mode) + dock_a(rc())
    if pre:
        o += tile()
    return o


def run_hint():
    R = P.run_path(VH, 'Goodaura 1'); x, y, *_ = P.at(R, R['cum'][-1] * 0.45)
    return ring(x - 23, y - 23, 46, 46, run=True) + bubble(T['run'], 'down', x - 66, 'left', step=1, left=66, bottom=844 - (y - 23 - 12))


# targets measured on RX1/OP1: record 17,780,44,44 · find 69,780,207,44 · village 284,780,89,44
# switch 12,566,46,90 · me 12,52,44,44
def find_hint():
    return ring(64, 775, 217, 54) + bubble(T['find'], 'down', 110, 'left', step=2, left=62, bottom=844 - 763)


def rec_hint():
    return ring(12, 775, 54, 54) + bubble(T['rec'], 'down', 27, 'left', step=3, left=12, bottom=844 - 763)


def village_hint():
    return ring(279, 775, 99, 54) + bubble(T['village'], 'down', 50, 'right', step=4, right=12, bottom=844 - 763)


def mode_hint():
    return ring(7, 561, 56, 100) + bubble(T['mode'], 'left', 22, 'top', step=5, left=75, top=589)


def me_hint():
    return ring(7, 47, 54, 54) + bubble(T['me'], 'up', 22, 'left', step=6, left=12, top=113)


# ---------------------------------------------------------------- the village, its sheet, and the group hint
ROW = {'trip': (578, 46), 'group': (730, 47)}   # measured in the render (top, height)


def village(step=1, night=False):
    light = 'night' if night else 'morning'
    b = pic('vill', light, None) + base('vill')
    gx, gy, _, gz = P.pt(VV, *P.lift_ends(P.flift('Goodaura'))[0]); sx_, sy_, _, _ = P.pt(VV, *P.lift_ends(P.flift('Soliko'))[0])
    zx, zy, _, _ = P.pt(VV, *P.lift_ends(P.flift('Zuma'))[0])
    b += tag(gx + 30, gy - 58, 'Goodaura ↑', None) + tag(sx_ - 28, sy_ + 30, 'Soliko ↑', None) + tag(zx - 8, zy + 24, 'Zuma', None)
    b += '<div class="tag" style="left: 62px; top: 76px; height: 32px; border-radius: 16px; padding: 0 12px; direction: rtl; font-size: 13px">↑ חזרה להר</div>'
    b += ('<div class="glass" style="position: absolute; left: 0; right: 0; bottom: 0; height: 344px; border-radius: 28px 28px 0 0; padding: 10px 18px 14px; box-sizing: border-box; display: flex; flex-direction: column; gap: 4px">'
          '<div class="grab"></div><div style="margin: 6px 0 4px"><p class="ttl">הכפר</p><p class="sub"><span dir="ltr">New Gudauri</span> · <span dir="ltr">%s</span> מ׳ · 41 בניינים ממופים</p></div>'
          '<div class="rw"><span style="font-weight: 700">הטיול שלך</span><span class="r">מתי טסים?</span></div>'
          '<div class="rw"><span style="font-weight: 700">כרטיס סקי</span><span class="r">יום · 120 ₾</span></div>'
          '<div class="rw"><span style="font-weight: 700">אוכל ושתייה</span><span class="r">15 מקומות</span></div>'
          '<div class="rw"><span style="font-weight: 700">החבר׳ה</span><span class="r">מצטרפים או פותחים קבוצה</span></div>'
          '<div class="rw"><span style="font-weight: 700">ערב בבקתה</span><span class="r">5 משחקים</span></div></div>') % ('{:,}'.format(int(round(gz / 10) * 10)))
    if step == 1:
        b += ring(8, ROW['trip'][0] - 3, 374, ROW['trip'][1] + 6, r=16) + bubble(T['trip'], 'up', 44, 'right', step=1, total=2, right=12, top=ROW['trip'][0] + ROW['trip'][1] + 3 + 12)
    else:
        b += ring(8, ROW['group'][0] - 3, 374, ROW['group'][1] + 6, r=16) + bubble(T['group'], 'down', 44, 'right', step=2, total=2, right=12, bottom=844 - (ROW['group'][0] - 3 - 12))
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
    o += ('<div class="glass box" style="position: absolute; left: 12px; right: 12px; top: 504px">' + rl(ic(P_BULB, 20), AGAIN, '', CHL)
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
    o += tile(en=True)
    o += ring(324, 775, 54, 54) + bubble(EN['rec'], 'down', 27, 'right', step=3, right=12, bottom=844 - 763).replace('class="hn"', 'class="hn" dir="ltr"').replace('סגירת כל הרמזים', 'Close all hints').replace('רמז 3 מתוך 6', 'Hint 3 of 6')
    return o


def anatomy():
    """the bubble in three moments: the time starts, half gone, and with a screen reader (no time, the ring full)"""
    o = pic('hero', 'morning', 'night') + veil(0, 844, 'var(--veil)')
    def lab(y, n, t):
        return '<div class="anl" style="top: %dpx"><b>%d</b><span class="glass" style="border-radius: 14px; padding: 5px 12px">%s</span></div>' % (y, n, t)
    t = T['rec']
    o += lab(96, 1, 'מופיע: הטבעת מלאה, 8 שניות')
    o += bubble(t, 'down', 40, 'right', step=3, state='full', right=16, top=136).replace('animation:', '')
    o += lab(306, 2, 'חצי זמן. נגיעה בכל מקום: לרמז הבא')
    o += bubble(t, 'down', 40, 'right', step=3, state='p50', right=16, top=346)
    o += lab(516, 3, 'קורא מסך: בלי זמן, "הבא" ו"סגירה"')
    o += bubble(t, 'down', 40, 'right', step=3, state='full', right=16, top=556)
    o += ('<div class="anl" style="top: 700px"><span class="glass" style="border-radius: 14px; padding: 8px 12px; font-weight: 500; line-height: 1.45">'
          'ה-X סוגר את כל הרמזים. הנקודות: איפה בסדרה. בסוף הזמן: הרמז הבא.</span></div>')
    return o


def phones():
    return {
        'HN1.dc.html': ('הפתיחה הראשונה, לפני העונה: הרמז על המסלול, והספירה במסגרת', screen() + run_hint(), None),
        'HN2.dc.html': ('אחרי שנסגר הגיליון של המסלול: "לאן יורדים?"', screen() + find_hint(), None),
        'HN3.dc.html': ('הפתיחה השנייה: כפתור ההקלטה', screen() + rec_hint(), None),
        'HN4.dc.html': ('אחרי פעולה: הכפר', screen() + village_hint(), None),
        'HN5.dc.html': ('הפתיחה השלישית: תמונה או חי, בלילה', screen('night') + mode_hint(), 'night'),
        'HN6.dc.html': ('אחרי פעולה: "אני"', screen() + me_hint(), None),
        'HN7.dc.html': ('בכניסה הראשונה לכפר, 1 מתוך 2: הטיול שלך', village(1), None),
        'HN11.dc.html': ('בכניסה הראשונה לכפר, 2 מתוך 2: החבר׳ה', village(2), None),
        'HN12.dc.html': ('הבועה: הזמן, הסגירה והנקודות', anatomy(), None),
        'HN8.dc.html': ('באנגלית: כפתור ההקלטה, משמאל לימין', en_screen(), 'day', 'en'),
        'HN9.dc.html': ('הגדרות: "הצגה מחדש של הרמזים"', settings(), None),
        'HN10.dc.html': ('חיפוש מיקום: השורה בתוך גלולה (ב9)', search(), None),
    }


HB = 1720


def hn00():
    k = lambda x: '<span dir="ltr">%s</span>' % x
    rows = [
        ('1', 'מסלול כחול באמצע המסך', T['run'], k('app.hint_run')),
        ('2', '"לאן יורדים?"', T['find'], k('app.hint_find')),
        ('3', 'כפתור ההקלטה', T['rec'], k('app.hint_rec')),
        ('4', '"הכפר"', T['village'], k('app.hint_village')),
        ('5', 'המתג תמונה / חי', T['mode'], k('app.hint_mode')),
        ('6', '"אני"', T['me'], k('app.hint_me')),
        ('כפר 1', 'השורה "הטיול שלך"', T['trip'], k('app.hint_trip') + ' <b>חדש</b>'),
        ('כפר 2', 'השורה "החבר׳ה"', T['group'], k('app.hint_group')),
    ]
    tbl = ('<table style="border-collapse: collapse; width: 100%; font-size: 14.5px; line-height: 1.45"><tr style="color: #56606C; font-size: 13px">'
           '<td style="padding: 6px 10px">#</td><td style="padding: 6px 10px">על מה מצביע</td><td style="padding: 6px 10px">הטקסט (גרסה 2, בניסוח של סשן התוכן)</td><td style="padding: 6px 10px">המפתח</td></tr>'
           + ''.join('<tr style="border-top: 1px solid #D9DEE4">%s</tr>' % ''.join('<td style="padding: 8px 10px; vertical-align: top">%s</td>' % c for c in r) for r in rows) + '</table>')
    flow = li('<b>סדרה אחת, בפתיחה הראשונה:</b> 1.2 שנ׳ אחרי שההר צויר ושקט, שישה רמזים ברצף, 1 עד 6. בלי מגבלת "שניים בפתיחה".',
              '<b>נגיעה בכל מקום במסך: לרמז הבא.</b> גם נגיעה ביעד עצמו מעבירה הלאה, ולא מפעילה אותו, כדי שגיליון לא ייפתח באמצע. גרירה וצביטה לא עושות כלום.',
              '<b>עמעום קל</b> (26% ביום, 34% בלילה) עם חור סביב היעד. בלעדיו נגיעה במסלול ש"לא עושה כלום" נראית כמו תקלה.',
              '<b>טבעת שמתרוקנת סביב ה-X:</b> 8 שניות לכל רמז, ובסופן הרמז הבא. אחרי השישי, הסדרה נגמרת.',
              '<b>ה-X סוגר את כל הסדרה.</b> "הבא" כבר בנגיעה בכל מקום, אז ה-X הוא "די, הבנתי".',
              '<b>הנקודות</b> בתחתית הבועה: כמה רמזים יש ואיפה אנחנו. הנוכחית מוארכת.',
              '<b>הכפר:</b> סדרה קצרה משלה, שני רמזים (הטיול שלך, החבר׳ה), בפעם הראשונה שהגיליון נפתח, 600 מ״ש אחרי שנעצר. אותם כללים.',
              '<b>הפרעה</b> (רקע, קישור, התראה, הקלטה): הסדרה נעצרת, וממשיכה מאותו רמז בפתיחה הבאה. X או סוף הסדרה: לא חוזרת.',
              '<b>"הצגה מחדש של הרמזים" בהגדרות:</b> חוזרים להר והסדרה מתחילה מ-1; גם הסדרה של הכפר תחזור בכניסה הבאה.',
              '<b>לפני העונה:</b> אותה סדרה; אריח הספירה נשאר מתחת לעמעום.')
    a11y = li('<b>קורא מסך:</b> בלי זמן. הטבעת מלאה ולא זזה. כל רמז נקרא כ"רמז 3 מתוך 6" ואחריו הטקסט, והמיקוד עובר לבועה. שתי פעולות: "לרמז הבא" ו"סגירת כל הרמזים".',
              '<b>זמן:</b> 8 שניות, או יותר כשבהגדרות הנגישות של אנדרואיד נבחר "זמן לביצוע פעולה" ארוך (' + k('getRecommendedTimeoutMillis') + ').',
              '<b>הפחתת תנועה:</b> הבועה מופיעה בלי לעלות, והטבעת מתרוקנת בקפיצות של שנייה במקום בתנועה רציפה.',
              '<b>יעדי מגע:</b> ה-X 32 בעין ו-44 במגע.')
    comp = li('<b>בועה:</b> רדיוס 16, רקע ' + k('ink') + ', טקסט ' + k('bg') + ' 14 במשקל 500, ברוחב התוכן ועד 300, עד שלוש שורות; ריפוד 10, 14 בצד ההתחלה ו-8 בצד ה-X.',
              '<b>X בטבעת:</b> 32, טבעת 2 (המסלול 22%, המילוי מלא), X של 12. בצד הסוף של השורה הראשונה.',
              '<b>נקודות:</b> 6 על 6, רווח 5, 32%; הנוכחית 16 רוחב ומלאה; שעברו 60%.',
              '<b>חוד וטבעת על היעד:</b> כמו בגרסה 1. הטבעת היא גם החור בעמעום.')
    honest = li('<b>כן, זה הגיוני,</b> בשלושה תנאים: עמעום קל עם חור, כך שהנגיעה "בכל מקום" ברורה; נגיעה ביעד מעבירה הלאה ולא פותחת אותו; ו-X אחד שסוגר הכל.',
                '<b>הסיכון:</b> סדרות כאלה נסגרות מהר. לכן שישה רמזים בלבד, 8 שניות כל אחד (פחות מדקה), טקסט של שתי שורות, ואפשר לראות שוב מההגדרות.',
                '<b>מה שלא הייתי עושה:</b> מסך הדרכה לפני ההר, או סדרה של הכפר בתוך הסדרה של ההר (צריך לפתוח את הגיליון באמצע).')
    asks = li('<b>הסדרה כפי שבלוחות</b> (שישה על ההר, שניים בכפר; נגיעה בכל מקום: הבא; X: סגירת הכל; 8 שנ׳)? (כן)',
              '<b>שמונת הטקסטים</b> (גרסה 2)? (כן)',
              '<b>נגיעה באריח הספירה</b> פותחת את גיליון העונה? (כן)',
              '<b>"מחפש מיקום" בגלולה</b> (HN10, והמשך ב-B91 עד B93)? (כן)')
    inner = ('<p class="sec">רמזים לפעם הראשונה · גרסה 2, אחרי ההערות של פיני · 26.11.2026 · ממתין לפיני</p>'
             '<h1>סדרה אחת מודרכת: נגיעה בכל מקום ממשיכה, X סוגר</h1>'
             '<p class="lead">ההר נשאר במסך, מעומעם קצת, ורק היעד מואר. כל רמז אומר מה יש ולמה, בשתי שורות. בכפר, שני רמזים משלו, וביניהם שורה ברורה ל"הטיול שלך".</p>'
             '<div class="card"><h2>הרמזים</h2>%s</div>'
             '<div class="rowx" style="align-items: stretch"><div class="card" style="flex: 1.2"><h2>איך הסדרה עובדת</h2>%s</div>'
             '<div class="card" style="flex: 1"><h2>נגישות</h2>%s<h2 style="margin-top: 8px">הרכיב</h2>%s</div></div>'
             '<div class="rowx" style="align-items: stretch"><div class="card" style="flex: 1.2"><h2>הדעה שלי, ביושר</h2>%s</div>'
             '<div class="card" style="flex: 1"><h2>לפיני</h2>%s<p class="cap">ארבעה מפתחות חדשים (' + k('app.hint_trip') + ', ' + k('app.hint_step_a11y') + ', ' + k('app.hint_next_a11y') + ', ' + k('app.hints_skip_a11y') + ') וגרסה 2 של שמונת הטקסטים לסשן התוכן. נמדד בכלי העיצוב, לא בטלפון. המסירה: ' + k('design/handoff/2026-11-26-redesign-hints/') + '</p></div></div>'
             ) % (tbl, flow, a11y, comp, honest, asks)
    wb('HN00-Hints.dc.html', 'רמזים לפעם הראשונה, גרסה 2', 1280, HB, inner)


def ct_phones():
    base_ = lambda light='morning': ((pic('hero', 'morning', 'night') + base('hero', cabins=True)) if light == 'morning' else mtn(light)) + season_top() + ctl_sw('pic') + dock_a(rc())
    en = en_screen()
    en = en[:en.index('<div class="hr"')] if '<div class="hr"' in en else en
    en = en.replace(tile(en=True), tile(en=True, open_=True))
    return {
        'CT1.dc.html': ('הספירה · סגור', base_() + tile(), None),
        'CT2.dc.html': ('הספירה · נפתח אחרי נגיעה', base_() + tile(open_=True), None),
        'CT3.dc.html': ('הספירה · גיליון העונה', base_() + veil(0, 844, 'rgba(6,11,22,.28)') + season_sheet(), None),
        'CT4.dc.html': ('הספירה · סגור, בלילה', base_('night') + tile(), 'night'),
        'CT5.dc.html': ('הספירה · באנגלית, פתוח', en, 'day', 'en'),
    }


HCT = 1120


def ct00():
    k = lambda x: '<span dir="ltr">%s</span>' % x
    what = li('<b>ריבוע קטן, 64 על 64,</b> בצד ההתחלה מעל הסרגל. בתוכו רק המספר, בתוך טבעת שמתמלאת לאורך ההמתנה: מההודעה של <bdi>MTA</bdi> (28.10) עד הפתיחה (20.12). היום %d%% מהטבעת מלאה. מספר בטבעת שמתמלאת נקרא כספירה בלי מילה.' % round(FRAC * 100),
              '<b>נגיעה:</b> הריבוע נפתח לצד מרכז המסך (250 מ״ש), ומראה "ימים לפתיחה" ו"הרכבלים עולים ב-20.12" עם חץ. נשאר פתוח 3.5 שניות (או לפי זמן הנגישות של אנדרואיד) ונסגר בחזרה.',
              '<b>נגיעה בריבוע הפתוח:</b> גיליון העונה (התאריך, הטיול שלך וכרטיס הסקי), בלי המספר הגדול. משיכה למטה סוגרת, והריבוע נשאר.',
              '<b>בפתיחה הראשונה של כל יום, עד הפתיחה:</b> הריבוע נפתח לבד פעם אחת ל-3.5 שניות ונסגר. כך ברור מה המספר בלי לגעת. ביום הראשון, סדרת הרמזים מתחילה אחרי שהוא נסגר.',
              '<b>מחר / היום / נדחה / עבר התאריך:</b> מחר, 1 בטבעת וכיתוב "מחר פותחים"; היום, הטבעת מלאה וסמל קרון במקום מספר, "היום פותחים"; נדחה, המספר לפי התאריך החדש, "הפתיחה נדחתה ל-..."; עבר התאריך בלי דיווח, הטבעת מלאה בצבע ' + k('ink2') + ' וסמל שעון, "הפתיחה הייתה מתוכננת ל-...". כל הכיתובים קיימים (' + k('app.season_*') + ').',
              '<b>נגישות:</b> כפתור אחד, נקרא "24 ימים לפתיחה המתוכננת, 20.12" (' + k('app.season_days_a11y') + '); הפעלה פותחת ישר את גיליון העונה. בהפחתת תנועה: בלי התרחבות, הכיתוב מופיע ונעלם.',
              '<b>אין מחרוזת חדשה.</b>')
    spec = li('<b>ריבוע:</b> זכוכית, רדיוס 18, ריפוד 8; 12 מקצה המסך, 18 מעל הסרגל.',
              '<b>טבעת:</b> קוטר 48, קו 3; המסלול ב-' + k('line') + ', המילוי ב-' + k('acc') + ' מלמעלה, עם כיוון השעון. המספר 20 במשקל 600, ספרות שוות רוחב; מ-100 ימים, 17.',
              '<b>פתוח:</b> ברוחב התוכן; רווח 10; "ימים לפתיחה" 14 מודגש, השורה מתחת 12 ב-' + k('ink2') + '; חץ 18 בצד הסוף.')
    inner = ('<p class="sec">רמזים לפעם הראשונה · ב8, גרסה 2 · 26.11.2026 · ממתין לפיני</p>'
             '<h1>הספירה לפני העונה: ריבוע עם מספר, שנפתח בנגיעה</h1>'
             '<div class="rowx" style="align-items: stretch"><div class="card" style="flex: 1.4"><h2>איך זה עובד</h2>%s</div>'
             '<div class="card" style="flex: 1"><h2>מידות</h2>%s<h2 style="margin-top: 8px">לפיני</h2>%s</div></div>'
             % (what, spec, li('<b>הריבוע כפי שבלוחות?</b> (כן)', '<b>נפתח לבד פעם ביום עד הפתיחה?</b> (כן, כדי שלא צריך לנחש)',
                               '<b>נגיעה בריבוע הפתוח פותחת את גיליון העונה?</b> (כן)')))
    wb('CT00-Countdown.dc.html', 'הספירה לפני העונה', 1280, HCT, inner)


def main():
    ph = phones(); ph.update(ct_phones())
    for fn, v in ph.items():
        title, body, fx = v[:3]; lang = v[3] if len(v) > 3 else 'he'
        PB.phone(fn, title, body, night=fx == 'night', fixed=fx, lang=lang)
    hn00()
    p = os.path.join(PROJ, 'canvas.json'); j = json.load(open(p, encoding='utf8'))
    for fn in list(j['boards']):
        if fn.startswith(('HN', 'CT')): del j['boards'][fn]
    j['order'] = [f for f in j['order'] if not f.startswith(('HN', 'CT'))]
    place = [('HN00-Hints.dc.html', 0, 0, 1280, HB, 'רמזים לפעם הראשונה, גרסה 2')]
    y = HB + 140
    names = [('HN1', '1 מתוך 6: המסלול, והספירה באריח'), ('HN2', '2 מתוך 6: "לאן יורדים?"'), ('HN3', '3 מתוך 6: ההקלטה'),
             ('HN4', '4 מתוך 6: הכפר'), ('HN5', '5 מתוך 6: תמונה או חי, בלילה'), ('HN6', '6 מתוך 6: "אני"')]
    for i, (c, t) in enumerate(names):
        place.append(('%s.dc.html' % c, 470 * i, y, 390, 844, t))
    y += 844 + 200
    for i, (c, t) in enumerate([('HN7', 'בכפר, 1 מתוך 2: הטיול שלך'), ('HN11', 'בכפר, 2 מתוך 2: החבר׳ה'), ('HN12', 'הבועה: זמן, סגירה ונקודות'),
                                ('HN8', 'באנגלית: ההקלטה'), ('HN9', 'הגדרות: הצגה מחדש (אושר)'), ('HN10', 'חיפוש מיקום בגלולה, ב9 (אושר)')]):
        place.append(('%s.dc.html' % c, 470 * i, y, 390, 844, t))
    y += 844 + 260
    ct00(); place.append(('CT00-Countdown.dc.html', 0, y, 1280, HCT, 'הספירה לפני העונה: ריבוע שנפתח בנגיעה'))
    y += HCT + 140
    for i, (c, t) in enumerate([('CT1', 'סגור: רק המספר בטבעת'), ('CT2', 'אחרי נגיעה: נפתח ל-3.5 שנ׳'), ('CT3', 'נגיעה בפתוח: גיליון העונה'),
                                ('CT4', 'סגור, בלילה'), ('CT5', 'באנגלית, פתוח')]):
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
