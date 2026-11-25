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
    cx, cy = 195, 360
    o = pic('hero', light, None) + veil(0, 844, 'var(--veil)')
    o += rings(cx, cy, still)
    o += '<div class="gsc glass" style="left: %dpx; top: %dpx">%s</div>' % (cx, cy, ic(P_AIM, 40, 1.6))
    o += '<div class="gst" style="top: %dpx"><b>מחפש מיקום</b></div>' % (cy + 150)
    o += hold()
    return o


def phones():
    out = {}
    out['GS1.dc.html'] = ('המסך החי · מחפש מיקום, ביום', gs_search(False), 'day')
    out['GS2.dc.html'] = ('המסך החי · מחפש מיקום, בלילה', gs_search(True), 'night')
    out['GS3.dc.html'] = ('המסך החי · מחפש מיקום, בהפחתת תנועה', gs_search(False, still=True), 'day')
    return out



# ---------------------------------------------------------------- the record button (note 5)
RCSS = """
.H .rbw2{height:44px;border-radius:22px;background:#E5352B;color:#fff;display:flex;align-items:center;gap:8px;padding:0 15px 0 13px;font-size:15px;font-weight:700;border:0;flex:none;white-space:nowrap}
.H .rbw2 i{width:12px;height:12px;border-radius:50%;background:#fff;flex:none}
.H .rbw2.live i{animation:gsl 1.6s ease-in-out infinite;box-shadow:0 0 0 4px rgba(255,255,255,.35)}
.H .rbw2 .tm{font-variant-numeric:tabular-nums;direction:ltr;font-weight:500}
.H .tip{position:absolute;inset-inline-end:10px;bottom:84px;width:262px;border-radius:16px;background:var(--ink);color:var(--bg);padding:11px 14px 12px;box-sizing:border-box;font-size:14px;line-height:1.4;box-shadow:0 8px 22px rgba(10,25,45,.3)}
.H .tip b{display:block;font-size:15.5px;margin-bottom:3px}
.H .tip::after{content:'';position:absolute;bottom:-6px;inset-inline-end:var(--ax,28px);width:12px;height:12px;background:var(--ink);transform:rotate(45deg)}
"""
G4.MB.CSS = G4.MB.CSS + RCSS
EN = {'village': 'Village', 'find': 'Where to ski?', 'rec': 'Ski tracking', 'tip_t': 'Ski tracking',
      'tip': 'Measures your speed and vertical, and names every run and lift you took. Works with the phone in your pocket.'}
HE = {'village': 'הכפר', 'find': 'לאן יורדים?', 'rec': REC, 'tip_t': REC,
      'tip': 'מודד מהירות וירידה, ומזהה באילו מסלולים ורכבלים היית. עובד גם כשהטלפון בכיס.'}


def dock2(rec='word', L=HE, t='1:24'):
    b = {'dot': '<button class="rb rec" aria-label="%s"><span></span></button>' % L['rec'],
         'word': '<button class="rbw2" aria-label="%s"><i></i>%s</button>' % (L['rec'], L['rec']),
         'live': '<button class="rbw2 live" aria-label="%s, %s"><i></i><span class="tm">%s</span></button>' % (L['rec'], t, t)}[rec]
    return ('<div class="dock2 glass"><button class="vl">%s %s</button><div class="find">%s<span>%s</span></div>%s</div>'
            % (ic(P_HOME, 20), L['village'], ic(P_SRCH, 18), L['find'], b))


def tip(L=HE, ax=None):
    st = (' style="--ax: %dpx"' % ax) if ax else ''
    return '<div class="tip"%s><b>%s</b>%s</div>' % (st, L['tip_t'], L['tip'])


def mtn(light='morning'):
    return pic('hero', light, None) + base('hero', cabins=True)


def first_sheet2():
    rows = [('<path d="M4 18 9 9l4 5 3-4 4 8z"/>', 'מודד מהירות, מרחק וירידה'),
            ('<path d="M5 19V5l7 4 7-4v14"/>', 'מזהה כל מסלול ורכבל בשם'),
            ('<rect x="7" y="3" width="10" height="18" rx="2.500"/><path d="M11 17.500h2"/>', 'עובד גם כשהטלפון בכיס'),
            ('<rect x="5" y="11" width="14" height="9" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/>', 'המיקום נשמר רק בטלפון')]
    r = ''.join('<div style="display: flex; gap: 12px; align-items: center"><span class="ic" style="background: var(--line); flex: none">%s</span><span style="font-size: 15px">%s</span></div>' % (ic(p), t) for p, t in rows)
    return ('<div class="sheet glass" style="gap: 14px"><div class="grab"></div><p class="ttl" style="font-size: 24px">%s</p>'
            '<div style="display: flex; flex-direction: column; gap: 10px">%s</div>'
            '<button class="pri" style="flex: none">להתחיל</button><button class="lnk">לא עכשיו</button></div>' % (REC, r))


def rc_phones():
    out = {}
    out['RC1.dc.html'] = ('כפתור ההקלטה · השם בסרגל', mtn() + top() + ctl() + dock2('word'), 'day', 'he')
    out['RC2.dc.html'] = ('כפתור ההקלטה · הבועה, מעוגנת לכפתור', mtn() + top() + ctl() + dock2('word') + tip(ax=60), 'day', 'he')
    out['RC3.dc.html'] = ('Record button · the hint, anchored to the button (LTR)',
                          mtn() + top(text='11 of 14 lifts open · <span dir="ltr">−7°</span>') + ctl() + dock2('word', EN) + tip(EN, 56), 'day', 'en')
    out['RC4.dc.html'] = ('כפתור ההקלטה · הנגיעה הראשונה', mtn() + top() + ctl() + dock2('word') + veil(0, 844, 'rgba(6,11,22,.38)') + first_sheet2(), 'day', 'he')
    out['RC5.dc.html'] = ('כפתור ההקלטה · בזמן מעקב, בלילה', mtn('night') + top() + ctl() + dock2('live'), 'night', 'he')
    return out


def wcard(sec, h2, items, flex=1, rec=False):
    return ('<div class="card" style="flex: %s%s"><p class="sec">%s</p><h2>%s</h2>%s</div>'
            % (flex, '; border: 3px solid #6B3FD4' if rec else '', sec, h2, li(*items)))


def q(n, t):
    return '<div class="q"><span class="qn">%d</span><span>%s</span></div>' % (n, t)


def wb(fn, title, w, h, inner):
    open(os.path.join(PROJ, fn), 'w', encoding='utf8').write(board(title, w, h, wbox(w, h, inner)))


HRC = 980


def rc00():
    names = ('<div class="card" style="flex: 1.3; border: 3px solid #6B3FD4"><p class="sec">הערה 5</p><h2>השם: "מעקב גלישה"</h2>'
             '<table style="border-collapse: collapse; width: 100%; font-size: 15px; line-height: 1.45">'
             + ''.join('<tr style="border-top: 1px solid #D9DEE4"><td style="padding: 8px 10px; width: 30%%"><b>%s</b></td><td style="padding: 8px 10px">%s</td></tr>' % r for r in [
                 ('מעקב גלישה (ההמלצה)', 'אומר מה זה עושה, כמו "מעקב ריצה" באפליקציות כושר. ברור גם למי שלא גלש.'),
                 ('מדידת גלישה', 'ברור, אבל נשמע כמו פעולה אחת, לא כמו משהו שרץ כל היום.'),
                 ('יום גלישה', 'השם הפנימי של התכונה. על כפתור הוא לא אומר מה יקרה בנגיעה.')])
             + '</table></div>')
    rules = wcard('איך הכפתור מתנהג', 'כמו בהמלצה הקודמת, רק עם השם החדש', [
        '<b>עד שמעקב ראשון מסתיים:</b> הנקודה והשם. אחרי זה, רק הנקודה.',
        '<b>בזמן מעקב:</b> הנקודה פועמת, והזמן שעבר לידה.',
        '<b>הבועה:</b> פעם אחת, בפתיחה הראשונה של ההר. נעלמת בנגיעה בכל מקום.'])
    anchor = wcard('הבאג מהבדיקה שלך', 'הבועה מעוגנת לכפתור, בכל כיוון כתיבה', [
        '<b>מה קרה:</b> בלוח הקודם הבועה ישבה מעל "הכפר". זו טעות שלי בלוח, לא באפליקציה; תוקנה גם שם.',
        '<b>הכלל:</b> הבועה מיושרת לקצה של הכפתור, והחץ מצביע על הנקודה האדומה. בעברית הכפתור בשמאל, ובאנגלית בימין, והבועה איתו (לוחות 2 ו-3).',
        '<b>בקוד:</b> מיקום יחסי לכפתור עצמו, לא לצד של המסך.'])
    sheet = wcard('הנגיעה הראשונה', 'במקום "הטלפון בכיס. ההר מצייר את היום."', [
        'כותרת: <b>מעקב גלישה</b>. ארבע שורות, כל אחת עובדה: מודד מהירות, מרחק וירידה · מזהה כל מסלול ורכבל בשם · עובד גם כשהטלפון בכיס · המיקום נשמר רק בטלפון.',
        '<b>להתחיל</b> ו<b>לא עכשיו</b>. בלי בורר (כמו בהמלצה על "ירידה אחת").'])
    texts = ('<div class="card"><h2>טקסטים לסשן התוכן</h2>' + li(
        'שם הכפתור והגיליון: "מעקב גלישה".',
        'הבועה: "מודד מהירות וירידה, ומזהה באילו מסלולים ורכבלים היית. עובד גם כשהטלפון בכיס."',
        'הגיליון: ארבע השורות שלמעלה, "להתחיל", "לא עכשיו".',
        'בזמן מעקב, לקורא מסך: "מעקב גלישה, {זמן}".')
        + q(1, '<b>השם:</b> מעקב גלישה, מדידת גלישה או יום גלישה? <b>(ההמלצה: מעקב גלישה)</b>') + '</div>')
    inner = ('<p class="sec">הערות על גרסה 56 · סעיף 5</p><h1>כפתור ההקלטה: שם בעברית, ובועה במקום הנכון</h1>'
             '<div class="rowx" style="align-items: stretch">%s%s</div><div class="rowx" style="align-items: stretch">%s%s</div>%s' % (names, rules, anchor, sheet, texts))
    wb('RC00-RecName.dc.html', 'כפתור ההקלטה', 1280, HRC, inner)


# ---------------------------------------------------------------- the opening, and the switch in the button column (notes 1 to 3)
from edge_boards import P_PIC, P_CUBE
import ring_boards as RB
import kobi_boards as KB
OCSS = """
.H .msw{width:44px;border-radius:22px;display:flex;flex-direction:column;overflow:hidden;padding:0}
.H .msw span{width:44px;height:44px;display:flex;align-items:center;justify-content:center;border-radius:22px;color:var(--ink)}
.H .msw span.on{background:var(--ink);color:var(--bg)}
.H .livei{position:absolute;left:0;top:0;width:390px;height:844px}
"""
G4.MB.CSS = G4.MB.CSS + OCSS
LIVE = {'open-after': RB.U('open-after'), 'edge-n': '/_blob/ae310bd42c8f168d70fb2761b5171540'}


def ctl_sw(on):
    sw = ('<div class="msw glass" role="radiogroup" aria-label="תצוגת ההר"><span class="%s" role="radio" aria-label="תמונה">%s</span><span class="%s" role="radio" aria-label="חי">%s</span></div>'
          % ('on' if on == 'pic' else '', ic(P_PIC, 19), 'on' if on == 'live' else '', ic(P_CUBE, 19)))
    return ('<div class="mc">%s<button class="glass" aria-label="שכבות">%s</button><button class="glass" aria-label="איפה אני">%s</button></div>'
            % (sw, ic(P_LAY, 20), ic(P_LOC, 20, 1.8, 'none')))


def op_phones():
    out = {}
    out['OP1.dc.html'] = ('הפתיחה · תמונה, והמתג בעמודה', mtn() + top() + ctl_sw('pic') + dock2('dot'), 'day', 'he')
    out['OP2.dc.html'] = ('הפתיחה · ההר החי עם הטבעת, והמתג בעמודה',
                          '<img class="livei" alt="" src="%s">' % LIVE['open-after'] + top() + ctl_sw('live') + dock2('dot'), 'day', 'he')
    return out


def shot2(src, w, cap, sub='', mark=False):
    h = round(w * 844 / 390)
    return ('<div class="fr" style="width: %dpx"><div class="th" style="width: %dpx; height: %dpx; border-radius: 14px; overflow: hidden%s"><img alt="" src="%s" style="width: 100%%; height: 100%%; object-fit: cover"></div><p class="cap"><b>%s</b>%s</p></div>'
            % (w, w, h, '; outline: 3px solid #6B3FD4' if mark else '', src, cap, ('<br>' + sub) if sub else ''))


HOP = 1730


def op00():
    W = 196
    row1 = (shot2(PB.U('hero-morning'), W, 'תמונה · בוקר') + shot2(RB.U('open-after'), W, 'חי + הטבעת · בוקר', 'מבט הפתיחה', True)
            + shot2(PB.U('hero-sunset'), W, 'תמונה · שקיעה') + shot2(RB.U('after-s'), W, 'חי + הטבעת · שקיעה', 'מבט רחוק יותר')
            + shot2(PB.U('hero-night'), W, 'תמונה · לילה'))
    verdict = ('<div class="card" style="flex: 1"><p class="sec">ביושר</p><h2>מה כל אחד עושה טוב יותר</h2>'
               '<table style="border-collapse: collapse; width: 100%; font-size: 15px; line-height: 1.45">'
               + ''.join('<tr style="border-top: 1px solid #D9DEE4"><td style="padding: 8px 10px; width: 22%%"><b>%s</b></td><td style="padding: 8px 10px; width: 39%%">%s</td><td style="padding: 8px 10px">%s</td></tr>' % r for r in [
                   ('', '<b>תמונה</b>', '<b>חי, עם הטבעת של Y02</b>'),
                   ('עומק', 'ההר לבד, והרים רכים מאחוריו', '<b>עשרות פסגות אמיתיות מסביב,</b> רכסים וסלע חדים'),
                   ('שמיים', '<b>עננים, וקזבק באופק</b>', 'שמיים חלקים, בלי עננים'),
                   ('קרוב למטה', 'רך ומטושטש', 'רך, ובתחתית המסך מריחה של מרקם'),
                   ('אור', 'מצויר יפה בכל שעה', 'מחושב, ובשקיעה חם פחות'),
                   ('לילה', 'הר כחול, בלי הכפר', '<b>האורות של הכפר</b>'),
                   ('תנועה', 'רק קירוב והזזה', '<b>מסובבים, מטים, ו"לרדת בסרט" במקום</b>')])
               + '</table></div>')
    rec = ('<div class="card" style="flex: 1; border: 3px solid #6B3FD4"><p class="sec">הערה 1 · פיני בחר (25.11.2026)</p><h2>פותחים על התמונה</h2>' + li(
        '<b>התמונה יפה יותר במבט ראשון,</b> ויש בה יותר פרטים: עננים, קזבק באופק, ואור מצויר בכל שעה.',
        '<b>ההר החי זמין בנגיעה על המתג,</b> עם כל מה שיש בו: סיבוב, הטיה, ההרים מסביב והאורות של הכפר בלילה.',
        '<b>"לרדת בסרט" ו"היום בדקה"</b> עוברים לחי לרגע, עם התגית שחוזרת לתמונה (Z05).',
        '<b>נמסר לבנייה:</b> design/handoff/2026-11-25-redesign-opening-picture/.') + '</div>')
    sw = ('<div class="card" style="flex: 1"><p class="sec">הערה 2</p><h2>המתג בעמודת הכפתורים</h2>' + li(
        '<b>בראש העמודה,</b> מעל השכבות ו"איפה אני": שני תאים של 44, תמונה וחי, והתא הנוכחי מלא (לוחות 1 ו-2).',
        '<b>יצא משורת השכבות,</b> כי הוא לא שכבה.',
        '<b>הבחירה נשמרת.</b> מי שלא בחר, פותח על התמונה.') + '</div>')
    kfr = ''.join(shot2(KB.IMG[k][0], 150, c) for k, c in (('open', 'מבט הפתיחה'), ('o60', 'סיבוב, 60°'), ('o120', '120°'), ('facesel', 'הצד של Kobi')))
    kobi = ('<div class="card" style="flex: 1"><p class="sec">הערה 3 · הבחירה שלי</p><h2>צד Kobi: טיסה סביב ההר</h2>' + li(
        '<b>בהר החי:</b> המצלמה עפה סביב ההר דרך המזרח, עולה קצת באמצע כדי לראות מעל הרכס, ונוחתת מול Kobi. כ-1.2 שניות. כך רואים שזה אותו הר, מהצד השני.',
        '<b>בתמונה:</b> הצלבה עם הזזה קצרה לתמונה של צד Kobi, כמו ב-Z08.',
        '<b>"צד Kobi"</b> מחזיר באותה טיסה, בכיוון ההפוך.')
        + '<div class="rowx" style="gap: 10px; margin-top: 4px">%s</div></div>' % kfr)
    inner = ('<p class="sec">הערות על גרסה 56 · סעיפים 1 עד 3</p><h1>הפתיחה: תמונה או ההר החי</h1>'
             '<div class="rowx" style="gap: 14px">%s</div><div class="rowx" style="align-items: stretch">%s%s</div><div class="rowx" style="align-items: stretch">%s%s</div>'
             % (row1, verdict, rec, sw, kobi))
    wb('OP00-Opening.dc.html', 'הפתיחה', 1280, HOP, inner)


# ---------------------------------------------------------------- every state in the style of the search (notes 6 to 8)
SCSS = """
@keyframes prg{0%{transform:translate(-50%,-50%) scale(.3);opacity:.9}100%{transform:translate(-50%,-50%) scale(1);opacity:0}}
.H .pr{position:absolute;width:150px;height:150px;border-radius:50%;border:2px solid rgba(255,255,255,.95);box-shadow:0 0 16px rgba(255,255,255,.6);transform:translate(-50%,-50%) scale(.3);animation:prg 3.2s ease-out infinite;opacity:0}
.H .pr.b{animation-delay:1.6s}
.H .pr.slow{animation-duration:6s}.H .pr.slow.b{animation-delay:3s}
.H .night .pr{border-color:rgba(176,206,255,.9);box-shadow:0 0 18px rgba(120,170,255,.5)}
.H .gme{position:absolute;width:18px;height:18px;border-radius:50%;transform:translate(-50%,-50%);background:#2F7BFF;border:3px solid #fff;box-shadow:0 0 0 6px rgba(47,123,255,.28),0 2px 8px rgba(0,0,0,.35)}
.H .stc{position:absolute;left:12px;right:12px;top:154px;border-radius:22px;padding:12px 16px;box-sizing:border-box;display:flex;align-items:center;gap:12px}
.H .stc .w{display:flex;flex-direction:column;gap:2px;min-width:0;flex:1}
.H .stc .w b{font-size:21px;font-weight:700;line-height:1.2}
.H .stc .w span{font-size:14px;color:var(--ink2)}
.H .stc .big{font-size:34px;font-weight:300;white-space:nowrap;font-variant-numeric:tabular-nums;line-height:1}
.H .sofar{position:absolute;left:12px;right:12px;bottom:90px;border-radius:22px;padding:12px 18px;box-sizing:border-box;display:flex;gap:6px;justify-content:space-between}
.H .sofar .c{display:flex;flex-direction:column;gap:2px;align-items:flex-start}
.H .sofar .r4{display:flex;flex-direction:row;gap:22px;align-items:flex-end}
.H .sofar .num{font-size:26px}
.H .gauge{position:absolute;left:50%;bottom:92px;width:236px;height:236px;transform:translateX(-50%)}
.H .gauge .in{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:6px}
.H .gauge .v{display:flex;align-items:baseline;gap:6px;direction:ltr}
.H .gauge .v b{font-size:92px;font-weight:300;line-height:.85;letter-spacing:-.04em;font-variant-numeric:tabular-nums}
.H .gauge .v span{font-size:17px;font-weight:700;color:var(--ink2)}
.H .gauge .rn{display:flex;align-items:center;gap:7px;font-size:16px;font-weight:700}
.H .gauge .disc{position:absolute;inset:22px;border-radius:50%}
"""
G4.MB.CSS = G4.MB.CSS + SCSS
DAY = D.SEG[0]['t0']


def daylines(t, mode='day', w=3.0):
    return lay(svg(VH, D.day(VH, mode, until=t, w=w)))


def me(t, slow=False):
    x, y, vis, i = D.where(VH, t)
    c = ' slow' if slow else ''
    return ('<div class="pr%s" style="left: %.0fpx; top: %.0fpx"></div><div class="pr b%s" style="left: %.0fpx; top: %.0fpx"></div><div class="gme" style="left: %.0fpx; top: %.0fpx"></div>'
            % (c, x, y, c, x, y, x, y)), (x, y)


def mm(ms):
    s = ms // 1000; return '%d:%02d' % (s // 3600, (s % 3600) // 60)


def so_far(t):
    ds = [d for d in D.DESC if d['t1'] <= t]
    a = [D.idx(d['t0'], d['t1']) for d in ds]
    mx = max([float(D.KMH[x:y].max()) for x, y in a] or [0])
    drop = sum(max(0.0, float(D.Z[x] - D.Z[y - 1])) for x, y in a)
    return len(ds), mx, drop


def sofar_bar(t):
    n, mx, drop = so_far(t)
    cell = lambda v, c: '<div class="c"><span class="num">%s</span><span class="cap">%s</span></div>' % (v, c)
    return ('<div class="sofar glass">%s%s%s<span style="align-self: center">%s</span></div>'
            % (cell(n, 'ירידות'), cell('%d' % round(mx), 'קמ״ש מרבי'), cell('{:,}'.format(int(round(drop, -1))), 'מ׳ ירידה'), EG))


def stc(title, sub, big=''):
    return '<div class="stc glass"><span class="w"><b>%s</b><span>%s</span></span>%s</div>' % (title, sub, ('<span class="big">%s</span>' % big) if big else '')


def gs_stand(night=False):
    t = D.DESC[0]['t1'] + 40000; light = 'night' if night else 'morning'; m = 'night' if night else 'day'
    rg, _ = me(t)
    o = pic('hero', light, None) + daylines(t, m) + rg
    o += recp(mm(t - DAY)) + steps(1) + stc('עומד', 'ליד התחנה התחתונה של <bdi>Goodaura</bdi>') + sofar_bar(t) + hold()
    return o


def gs_lift():
    L = D.LIFT[2]; t = L['t0'] + int((L['t1'] - L['t0']) * .55)
    l = P.flift('Goodaura'); (e0, e1) = P.lift_ends(l)
    x0, y0, _, _ = P.pt(VH, *e0, lift=8); x1, y1, _, _ = P.pt(VH, *e1, lift=8)
    rg, (x, y) = me(t)
    beam = ('<svg width="390" height="844" style="position: absolute; left: 0; top: 0" fill="none" stroke-linecap="round">'
            '<path d="M%.1f %.1f L%.1f %.1f" stroke="#fff" stroke-width="7" opacity=".55"/><path d="M%.1f %.1f L%.1f %.1f" stroke="#F6C445" stroke-width="3"/>'
            '<path d="M%.1f %.1f L%.1f %.1f" stroke="#fff" stroke-width="2" stroke-dasharray="1 6"/></svg>' % (x0, y0, x1, y1, x0, y0, x, y, x, y, x1, y1))
    left = (L['t1'] - t) // 60000 + 1
    o = pic('hero', 'morning', None) + daylines(t) + beam + rg
    last = D.DESC[1]; a, b = D.idx(last['t0'], last['t1'])
    o += recp(mm(t - DAY)) + steps(1) + stc('ברכבל <bdi>Goodaura</bdi>', 'עוד %d דק׳ לראש' % left)
    o += ('<div class="sofar glass" style="flex-direction: column; gap: 8px"><span class="cap">הירידה האחרונה %s</span>'
          '<div class="r4"><div class="c"><span class="num">%d</span><span class="cap">קמ״ש מרבי</span></div><div class="c"><span class="num">%s</span><span class="cap">דקות</span></div>'
          '<div style="display: flex; align-items: center; gap: 6px; align-self: center; margin-inline-start: auto">%s<b dir="ltr">Goodaura 2</b></div></div></div>'
          % (EG, round(float(D.KMH[a:b].max())), round((last['t1'] - last['t0']) / 60000), sign('Goodaura 2'))) + hold()
    return o


def gauge(v, vmax=80, best=None, mode='day', run='Sadzele 1'):
    R = 104; C = 2 * math.pi * R; f = min(v / vmax, 1)
    ramp = D.RAMP[mode]
    stops = ''.join('<stop offset="%d%%" stop-color="%s"/>' % (i * 25, c) for i, c in enumerate(ramp))
    g = ('<svg width="236" height="236" viewBox="0 0 236 236" style="position: absolute; inset: 0; transform: rotate(135deg)"><defs><linearGradient id="gg" x1="0" y1="0" x2="1" y2="1">%s</linearGradient></defs>'
         '<circle cx="118" cy="118" r="%d" fill="none" stroke="%s" stroke-width="12" stroke-dasharray="%.1f %.1f" stroke-linecap="round"/>'
         '<circle cx="118" cy="118" r="%d" fill="none" stroke="url(#gg)" stroke-width="12" stroke-dasharray="%.1f %.1f" stroke-linecap="round"/>'
         % (stops, R, 'rgba(255,255,255,.55)' if mode == 'day' else 'rgba(255,255,255,.16)', C * .75, C, R, C * .75 * f, C))
    if best:
        a = math.radians(135 + 270 * min(best / vmax, 1)); bx, by = 118 + R * math.cos(a), 118 + R * math.sin(a)
        g += ''
    g += '</svg>'
    tick = ''
    if best:
        a = math.radians(135 + 270 * min(best / vmax, 1)); bx, by = 118 + (R + 16) * math.cos(a), 118 + (R + 16) * math.sin(a)
        tick = '<span style="position: absolute; left: %.0fpx; top: %.0fpx; transform: translate(-50%%,-50%%); font-size: 11.5px; font-weight: 700; white-space: nowrap; direction: ltr; color: var(--ink2)">%d</span>' % (bx, by, best)
        ix, iy = 118 + (R - 1) * math.cos(a), 118 + (R - 1) * math.sin(a)
        tick += '<span style="position: absolute; left: %.0fpx; top: %.0fpx; width: 4px; height: 18px; border-radius: 2px; background: var(--ink); transform: translate(-50%%,-50%%) rotate(%.0fdeg)"></span>' % (ix, iy, math.degrees(a) + 90)
    return ('<div class="gauge"><div class="disc glass"></div>%s%s<div class="in"><div class="v"><b>%d</b><span>קמ״ש</span></div>'
            '<div class="rn">%s<bdi>%s</bdi></div></div></div>' % (g, tick, v, sign(run), run))


def gs_down(night=False):
    t = D.ST['max_at']['t'] - 3000; light = 'night' if night else 'morning'; m = 'night' if night else 'day'
    rg, _ = me(t)
    v = round(float(D.KMH[D.where(VH, t)[3]]))
    o = pic('hero', light, None) + daylines(t, m) + rg
    o += recp(mm(t - DAY)) + gauge(v, best=61, mode=m) + hold()
    return o


def gs_break():
    s = [x for x in D.SEG if x['type'] == 'stop' and x['t1'] - x['t0'] > 30 * 60000][0]; t = s['t0'] + 18 * 60000
    rg, _ = me(t, slow=True)
    o = pic('hero', 'morning', None) + daylines(t) + veil(0, 844, 'rgba(92,114,148,.22)') + rg
    o += recp(mm(t - DAY)) + steps(1) + stc('הפסקה', 'מאז <bdi>%s</bdi>' % D.hm(s['t0']), '<bdi>18</bdi> דק׳') + sofar_bar(t) + hold()
    return o


def gs_end():
    t = D.SEG[-1]['t1']
    o = pic('hero', 'sunset', None) + daylines(t, 'day', 3.4)
    n, mx, drop = len(D.DESC), D.ST['max_kmh'], D.ST['drop_m']
    o += recp(mm(t - DAY)).replace('class="rdot"', 'class="rdot" style="background: #198549; box-shadow: none"') + steps(2)
    o += ('<div class="sofar glass" style="flex-direction: column; gap: 10px; bottom: 18px; padding: 16px 18px"><b style="font-size: 22px">סיכום היום %s</b>'
          '<div class="r4"><div class="c"><span class="num">%d</span><span class="cap">ירידות</span></div><div class="c"><span class="num">%d</span><span class="cap">קמ״ש מרבי</span></div>'
          '<div class="c"><span class="num">%s</span><span class="cap">מ׳ ירידה</span></div><div class="c"><span class="num">%.1f</span><span class="cap">ק״מ</span></div></div>'
          '<button class="pri" style="flex: none">לסיכום המלא</button></div>' % (EG, n, round(mx), '{:,}'.format(int(round(drop, -1))), D.ST['ski_dist_m'] / 1000))
    return o


def gs_states():
    return {'GS4.dc.html': ('המסך החי · עומד', gs_stand(), 'day'),
            'GS5.dc.html': ('המסך החי · ברכבל', gs_lift(), 'day'),
            'GS6.dc.html': ('המסך החי · בירידה', gs_down(), 'day'),
            'GS7.dc.html': ('המסך החי · בירידה, בלילה', gs_down(True), 'night'),
            'GS8.dc.html': ('המסך החי · הפסקה', gs_break(), 'day'),
            'GS9.dc.html': ('המסך החי · סוף היום', gs_end(), 'day')}


HGS = 900


def gs00():
    rows = [('מחפש מיקום', 'העיגולים יוצאים מהמרכז, כי עוד לא ידוע איפה אתה. <b>לבנייה עכשיו.</b>'),
            ('עומד', 'העיגולים עוברים אליך, על ההר: שני עיגולים רגועים סביב הנקודה. למטה: מה עשית עד עכשיו.'),
            ('ברכבל', 'הקו של הרכבל נדלק עד המקום שלך. בכרטיס: כמה זמן לראש, והירידה האחרונה.'),
            ('בירידה', 'המהירות בתוך טבעת: המספר והיחידה יחד, הטבעת מתמלאת בצבעי המהירות של יום הגלישה, וסימן על השיא של היום. שם המסלול בתוך הטבעת.'),
            ('הפסקה', 'אותם עיגולים, לאט פי שניים, וההר מעומעם קצת. כמה זמן ומאז מתי.'),
            ('סוף היום', 'היום על ההר באור השקיעה, וארבעה מספרים. השלב השלישי דולק.')]
    tbl = ('<table style="border-collapse: collapse; width: 100%; font-size: 15.5px; line-height: 1.5">' + ''.join(
        '<tr style="border-top: 1px solid #D9DEE4"><td style="padding: 9px 10px; width: 16%%"><b>%s</b></td><td style="padding: 9px 10px">%s</td></tr>' % r for r in rows) + '</table>')
    lang = wcard('השפה', 'עיגולים של אור, ומה שהיה תלוש נעלם', [
        '<b>הקו עם הקמ״ש מתחתיו ירד.</b> עד שיש מיקום אין מספר בכלל, ובירידה המספר והיחידה יושבים יחד בטבעת.',
        '<b>העיגולים הם החוט:</b> בחיפוש הם במרכז, אחרי זה סביבך על ההר, ובהפסקה הם נושמים לאט.',
        '<b>שלושת השלבים</b> נשארים למעלה בכל המצבים.'], flex=1)
    hon = ('<div class="card" style="flex: 1"><h2>ביושר</h2>' + li(
        '<b>החיפוש אצל המבצע.</b> שאר המצבים ממתינים להערות שלך, ואז מסירה נפרדת.',
        '<b>היום על ההר</b> הוא היום לדוגמה של כלי הבדיקה, על הקווים האמיתיים.',
        '<b>נבדק ברינדור מקומי,</b> לא בטלפון.',
        '<b>זה כיוון ראשון לכל מצב.</b> כדי שכל מצב יהיה מרהיב כמו החיפוש, כדאי סבב עיצוב על מודל חזק יותר.') + '</div>')
    inner = ('<p class="sec">הערות על גרסה 56 · סעיפים 6 עד 8</p><h1>המסך החי: כל מצב, בסגנון של החיפוש</h1>'
             '<div class="card">%s</div><div class="rowx" style="align-items: stretch">%s%s</div>' % (tbl, lang, hon))
    wb('GS00-States.dc.html', 'המסך החי', 1280, HGS, inner)


HTX = 900


def tx00():
    rows = [('כותרת הגיליון של הפעם הראשונה (<code>app.rec_first_title</code>)', 'הטלפון בכיס. ההר מצייר את היום.', 'מעקב גלישה'),
            ('השורה מתחת לכותרת', 'מזהים לבד רכבל, ירידה והפסקה, עם שמות המסלולים. אין מה ללחוץ בדרך.', 'מזהה לבד רכבל, ירידה והפסקה, בשם המסלול.'),
            ('שורה בגיליון (<code>app.rec_perm_pocket</code>)', 'הטלפון בכיס, ההקלטה ממשיכה', 'עובד גם כשהטלפון בכיס'),
            ('מצב החיפוש', 'מחפשים לוויינים', 'מחפש מיקום'),
            ('השלבים במסך החיפוש', 'מחפש מיקום · מודד · סיכום היום', 'בלי (בחרת בגרסה 3)'),
            ('השורה מתחת ל"מחפש מיקום"', 'בחוץ, מתחת לשמיים פתוחים, זה לוקח בדרך כלל פחות מדקה', 'רק אחרי 30 שניות בלי מיקום'),
            ('שם התכונה (גלולה, גיליון)', 'מקליטים', 'מעקב גלישה'),
            ('הכפתור, לקורא מסך (<code>app.rec_sign</code>)', 'מקליטים', 'הקלטת גלישה. בסרגל בלי מילה (RX00)'),
            ('מתחת ל"מודדים ירידה אחת" בגיליון המסלול', 'נעצר לבד בתחתית, ונותן כרטיס של הירידה.', 'בלי שורה. הכפתור מספיק')]
    tr = ''.join('<tr style="border-top: 1px solid #D9DEE4"><td style="padding: 9px 10px; width: 30%%">%s</td><td style="padding: 9px 10px; color: #8A93A0; text-decoration: line-through">%s</td><td style="padding: 9px 10px"><b>%s</b></td></tr>' % r for r in rows)
    tbl = ('<table style="border-collapse: collapse; width: 100%%; font-size: 15.5px; line-height: 1.45"><tr><th style="text-align: start; padding: 6px 10px">איפה</th>'
           '<th style="text-align: start; padding: 6px 10px">היה</th><th style="text-align: start; padding: 6px 10px">עכשיו</th></tr>%s</table>' % tr)
    inner = ('<p class="sec">הערות על גרסה 56 · סעיפים 10 ו-13</p><h1>טקסטים: קצר, ברור, אומר מה זה</h1>'
             '<div class="card">%s</div><div class="card"><h2>מה עשיתי בלוחות</h2>%s</div>' % (tbl, li(
                 'עברתי על הלוחות שלי, ותיקנתי את הנוסחים האלה בלוחות L01, P07, RB3 ו-LVR.',
                 'בלוחות החדשים בעמוד הזה אין שורות הסבר מתחת לכפתורים.',
                 'הנוסחים כבר בקובץ השפות, בארבע השפות (סשן התוכן, 25.11.2026).',
                 'מסך החיפוש (GS1 עד GS3) עודכן לגרסה 3 שבחרת: הפעימה, "מחפש מיקום" ו"החזקה לסיום". המונה ממתין לך (TM00).')))
    wb('TX00-Text.dc.html', 'טקסטים', 1280, HTX, inner)


PLACE = []
YNEXT = 0   # (file, x, y, w, h, title without the code)


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
    ph = dict(phones()); ph.update(rc_phones()); ph.update(op_phones()); ph.update(gs_states())
    for fn, v in ph.items():
        title, body, fx = v[:3]; lang = v[3] if len(v) > 3 else 'he'
        PB.phone(fn, title, body, night=fx == 'night', fixed=fx, lang=lang)
    y = 0
    gs00(); PLACE.append(('GS00-States.dc.html', 0, y, 1280, HGS, 'המסך החי: כל מצב, בסגנון של החיפוש'))
    y += HGS + 140
    for i, (fn, t) in enumerate([('GS1.dc.html', 'מחפש מיקום, ביום · גרסה 3'), ('GS2.dc.html', 'מחפש מיקום, בלילה'), ('GS3.dc.html', 'מחפש מיקום, בהפחתת תנועה'),
                                 ('GS4.dc.html', 'עומד'), ('GS5.dc.html', 'ברכבל'), ('GS6.dc.html', 'בירידה'), ('GS7.dc.html', 'בירידה, בלילה'),
                                 ('GS8.dc.html', 'הפסקה'), ('GS9.dc.html', 'סוף היום')]):
        PLACE.append((fn, 470 * i, y, 390, 844, t))
    y += 844 + 260
    op00(); PLACE.append(('OP00-Opening.dc.html', 0, y, 1280, HOP, 'הפתיחה: תמונה או ההר החי, והמתג'))
    PLACE.append(('OP1.dc.html', 1410, y, 390, 844, 'תמונה, והמתג בעמודה (נבחר)'))
    PLACE.append(('OP2.dc.html', 1880, y, 390, 844, 'ההר החי, בנגיעה על המתג'))
    y += HOP + 260
    rc00(); PLACE.append(('RC00-RecName.dc.html', 0, y, 1280, HRC, 'כפתור ההקלטה: השם, והבועה במקום הנכון'))
    y += HRC + 140
    for i, (fn, t) in enumerate([('RC1.dc.html', 'השם בסרגל'), ('RC2.dc.html', 'הבועה מעל הכפתור'), ('RC3.dc.html', 'אותה בועה באנגלית, משמאל לימין'),
                                 ('RC4.dc.html', 'הנגיעה הראשונה'), ('RC5.dc.html', 'בזמן מעקב, בלילה')]):
        PLACE.append((fn, 470 * i, y, 390, 844, t))
    y += 844 + 260
    tx00(); PLACE.append(('TX00-Text.dc.html', 0, y, 1280, HTX, 'טקסטים: קצר, ברור, אומר מה זה'))
    global YNEXT; YNEXT = y
    place_all(); print('phones', len(ph))


if __name__ == '__main__':
    main()
