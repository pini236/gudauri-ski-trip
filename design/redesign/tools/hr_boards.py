#!/usr/bin/env python3
"""The hour slider, the light states and weather on the mountain (Pini, 25.11.2026, via the manager). Design only.
Page "hour" in the redesign canvas; board codes at the end of every title (CONVENTIONS.md):
  HR00 where the hour slider lives (two options, one recommended) · HR1 to HR4 the phones
  HL00 light states through the day: what exists, what is missing, the weight · HL1 to HL3 the new pictures
  WX00 weather on the mountain, second stage of decision 83 · WX1 to WX3 the phones
The four new light pictures are renders of the existing painter (tools/peak.py) with interpolated colours: candidates, not
final art. Every number about the sun comes from peak.sun(); sizes are measured (webp q80, 1170x2532) or marked as estimates.
Usage: python3 design/redesign/tools/hr_boards.py [--check DIR]    (writes boards + canvas.json next to the others)"""
import os, sys, json, math
import v56_boards as V
from v56_boards import ic, li, q, mtn, top, dock2, veil, LIVE
import peak_boards as PB
import mtn_boards as MB
import peak as P
from gap_boards import li as li2
from live_boards import PROJ

PAGE = 'hour'
# the uploaded candidate pictures (canvas assets, 25.11.2026)
PB.IMG.update({'hero-first': '1dcdbe62a035c8b83e1fc4c56ab6686b', 'hero-late': '52fc42599ff07bc23def2cd019105339',
               'hero-early': '26dc5beddac45f64f4c061a8b9e1b3bf', 'hero-golden': 'bf083838a213d61dd96355aee265463e'})
LOCAL = {'1dcdbe62a035c8b83e1fc4c56ab6686b': 'pk-hero-first.jpg', '52fc42599ff07bc23def2cd019105339': 'pk-hero-late.jpg',
         '26dc5beddac45f64f4c061a8b9e1b3bf': 'pk-hero-early.jpg', 'bf083838a213d61dd96355aee265463e': 'pk-hero-golden.jpg'}

# ------------------------------------------------------------------ the sun, today's rule and the proposed set
KEYS = [  # name, hour the picture was drawn for, Hebrew name, existing?
    ('first', 8.4, 'אור ראשון', False), ('dawn', 9.0, 'שחר', True), ('morning', 10.6, 'בוקר', True), ('late', 11.9, 'לפני הצהריים', False),
    ('noon', 13.0, 'צהריים', True), ('early', 14.3, 'אחרי הצהריים, מוקדם', False), ('after', 15.9, 'אחר הצהריים', True),
    ('golden', 16.8, 'שעת הזהב', False), ('sunset', 17.35, 'שקיעה', True), ('blue', 18.3, 'דמדומים', True), ('night', 22.0, 'לילה', True)]
SUN = {n: P.sun(h) for n, h, _, _ in KEYS}
HN = {n: t for n, _, t, _ in KEYS}
HH = {n: h for n, h, _, _ in KEYS}


def dist(a, b):
    return min(abs(a - b), 360 - abs(a - b))


def pick(h, names):
    alt, az = P.sun(h)
    if alt >= 1:
        day = [n for n in names if SUN[n][0] >= 1]
        k = min(day, key=lambda n: math.hypot(alt - SUN[n][0], dist(az, SUN[n][1])))
        return k, round(math.hypot(alt - SUN[k][0], dist(az, SUN[k][1])), 1)
    if alt >= -9:
        k = 'first' if (az < 180 and 'first' in names) else 'blue'
        return k, 0.0
    return 'night', 0.0


TODAY = ['dawn', 'morning', 'noon', 'after', 'sunset', 'blue', 'night']
PROPOSED = [n for n, _, _, _ in KEYS]


def hours(a=7.0, b=19.0, step=0.5):
    h = a
    while h <= b + 1e-9:
        yield h
        h += step


ERR0 = {h: pick(h, TODAY) for h in hours()}
ERR1 = {h: pick(h, PROPOSED) for h in hours()}
MAX0 = max(e for _, e in ERR0.values())
MAX1 = max(e for _, e in ERR1.values())
AVG0 = sum(e for h, (_, e) in ERR0.items() if P.sun(h)[0] >= 1) / sum(1 for h in ERR0 if P.sun(h)[0] >= 1)
AVG1 = sum(e for h, (_, e) in ERR1.items() if P.sun(h)[0] >= 1) / sum(1 for h in ERR1 if P.sun(h)[0] >= 1)
print('max error today %.1f, proposed %.1f; mean (sun up) %.1f -> %.1f' % (MAX0, MAX1, AVG0, AVG1))


def hm(h):
    m = round(h * 60)
    return '%d:%02d' % (m // 60, m % 60)


# ------------------------------------------------------------------ the strip (one inline svg) and the column button
P_SUN = ('<circle cx="12" cy="12" r="4"/><path d="M12 2.500v2.500M12 19v2.500M2.500 12H5M19 12h2.500M5.300 5.300l1.800 1.800M16.900 16.900l1.800 1.800M18.700 5.300l-1.800 1.800M7.100 16.900l-1.800 1.800"/>')
P_MOON = '<path d="M20 14.500A8 8 0 0 1 9.500 4a8 8 0 1 0 10.500 10.500z"/>'
P_BACK = '<path d="M4 12a8 8 0 1 0 2.400-5.700"/><path d="M4 4v4.500h4.500"/>'

CSS = """
.H .mc button.lt{flex-direction:column;gap:0;line-height:1}
.H .mc button.lt.on{background:var(--acc);color:var(--onacc);border-color:transparent}
.H .mc button.lt b{font-size:11.5px;font-weight:700;direction:ltr}
.H .lp{position:absolute;left:12px;width:366px;height:44px;border-radius:22px;box-sizing:border-box;overflow:hidden}
.H .lp>svg{position:absolute;left:0;top:0}
.H .lp .lt{position:absolute;left:0;top:0;width:44px;height:44px;border-radius:22px;display:flex;align-items:center;justify-content:center;border:0}
.H .lp .lt{background:var(--acc);color:var(--onacc)}
.H .lp .lt b{font-size:11.5px;font-weight:700;direction:ltr}
.H .hs>svg{position:absolute;left:0;top:0;overflow:visible}
.H .hs .rt{position:absolute;left:8px;top:16px;width:44px;height:44px;border-radius:50%;border:1px solid var(--line);display:flex;align-items:center;justify-content:center}
.H .hsi{position:relative;height:44px;width:366px;align-self:center;flex:none}
.H .hsi>svg{position:absolute;left:0;top:0;overflow:visible}
.H .hsi .rt{position:absolute;left:8px;top:16px;width:44px;height:44px;border-radius:50%;border:1px solid var(--line);display:flex;align-items:center;justify-content:center}
.H .tabs2{display:flex;gap:8px}
.H .tabs2 span{height:36px;padding:0 16px;border-radius:18px;border:1px solid var(--line);display:inline-flex;align-items:center;font-size:14px;font-weight:500}
.H .tabs2 span.on{background:var(--acc);color:var(--onacc);border-color:transparent;font-weight:700}
.H .hr3{flex:none;display:flex;align-items:center;gap:12px;min-height:44px;font-size:15px}
.H .hr3+.hr3{border-top:1px solid var(--line)}
.H .hr3 b{font-weight:700;direction:ltr;min-width:46px}
.H .hr3 .v{margin-inline-start:auto;display:flex;gap:16px;color:var(--ink2);font-size:14px;direction:ltr}
.H .hr3.on{background:var(--line);border-radius:12px;padding:0 10px;margin:0 -10px}
.H .hr3.on b,.H .hr3.on .v{color:var(--ink)}
"""
MB.CSS = MB.CSS + CSS


SKY = [(7.0, '#1B2A52'), (8.4, '#8C6FB5'), (9.0, '#F2A98C'), (10.6, '#8EC5F2'), (13.0, '#5FA9F2'), (15.9, '#A9D0F2'),
       (16.8, '#F6C37A'), (17.35, '#F08A5D'), (18.3, '#5C5BA8'), (19.5, '#1B2A52'), (23.5, '#0E1730')]
TX0, TX1 = 60, 322     # the track inside the 366 by 44 pill


def TX(h):
    return TX0 + (h - 7) / 16.5 * (TX1 - TX0)


def track_svg(h, now=None, past=False, back=True, uid='g'):
    """the slider: the sky of the day as the track, one disc, nothing else (no numbers, no ticks)"""
    o = ['<svg width="366" height="44" viewBox="0 0 366 44" fill="none" aria-hidden="true"><defs><linearGradient id="%s" x1="0" x2="1" y1="0" y2="0">' % uid]
    for hh, c in SKY:
        o.append('<stop offset="%.3f" stop-color="%s"/>' % ((hh - 7) / 16.5, c))
    o.append('</linearGradient></defs>')
    o.append('<rect x="%d" y="17" width="%d" height="10" rx="5" fill="url(#%s)"/>' % (TX0, TX1 - TX0, uid))
    if past and now:
        o.append('<rect x="%d" y="17" width="%.1f" height="10" rx="5" fill="var(--glass)" opacity=".62"/>' % (TX0, TX(now) - TX0))
    if now:
        o.append('<rect x="%.1f" y="13" width="2.4" height="18" rx="1.2" fill="var(--ink)" opacity=".75"/>' % (TX(now) - 1.2))
    x = TX(h)
    gl = P_MOON if P.sun(h)[0] < -1 else P_SUN
    o.append('<circle cx="%.1f" cy="22" r="15" fill="var(--bg)" stroke="var(--acc)" stroke-width="2.4"/>'
             '<g transform="translate(%.1f 12.5) scale(.8)" stroke="var(--ink)" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">%s</g>' % (x, x - 9.6, gl))
    if back:
        o.append('<g transform="translate(335 12) scale(.8)" stroke="var(--ink2)" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">%s</g>' % P_BACK)
    o.append('</svg>')
    return ''.join(o)


def pill(h, now=None, past=False, top=612):
    """the light button opened in place: the same 44 button grows into a pill across the screen, nothing else moves"""
    return ('<div class="lp glass" style="top: %dpx"><button class="lt on" aria-label="אור היום, %s"><b>%s</b></button>%s</div>'
            % (top, hm(h), hm(h), track_svg(h, now, past)))


def column(sel, light=None, open_=False, scrub=None):
    """the button column (OP1) with the light button under the picture/live switch; light = 'sun'|'moon'; scrub = hour shown on the button"""
    sw = ('<div class="msw glass" role="radiogroup" aria-label="תצוגת ההר"><span class="%s" role="radio" aria-label="תמונה">%s</span><span class="%s" role="radio" aria-label="חי">%s</span></div>'
          % ('on' if sel == 'pic' else '', ic(V.P_PIC, 19), 'on' if sel == 'live' else '', ic(V.P_CUBE, 19)))
    if open_:
        lt = '<button class="lt" style="visibility: hidden" aria-hidden="true"></button>'
    elif scrub is not None:
        lt = '<button class="glass lt on" aria-label="אור היום, %s" aria-pressed="true"><b>%s</b></button>' % (hm(scrub), hm(scrub))
    else:
        lt = '<button class="glass lt" aria-label="אור היום" aria-pressed="false">%s</button>' % ic(P_MOON if light == 'moon' else P_SUN, 21, 1.7)
    return ('<div class="mc">%s%s<button class="glass" aria-label="שכבות">%s</button><button class="glass" aria-label="איפה אני">%s</button></div>'
            % (sw, lt, ic(V.P_LAY, 20), ic(V.P_LOC, 20, 1.8, 'none')))


def phone(fn, title, body, fixed='day'):
    PB.phone(fn, title, body, night=fixed == 'night', fixed=fixed, lang='he')


def screens():
    out = {}
    # HR1: option A closed, in picture mode, at noon
    out['HR1.dc.html'] = ('אור היום · כפתור בעמודה, סגור (HR1)', PB.pic('hero', 'noon', None) + PB.base('hero', cabins=True) + top() + column('pic', 'sun') + dock2('dot'), 'day')
    # HR2: open, picture mode, 16:50 (golden hour: a new picture)
    out['HR2.dc.html'] = ('אור היום · פתוח, תמונה, 16:50 (HR2)',
                          PB.pic('hero', 'golden', None) + PB.base('hero', cabins=True) + top() + column('pic', scrub=16.83, open_=True) + pill(16.83, now=13.33) + dock2('dot'), 'day')
    # HR3: open, live mode, 15:50 (the live model is continuous)
    out['HR3.dc.html'] = ('אור היום · פתוח, מצב חי, 15:50 (HR3)',
                          '<img class="livei" alt="" src="%s">' % LIVE['open-after'] + top() + column('live', scrub=15.83, open_=True) + pill(15.83, now=13.33) + dock2('dot'), 'day')
    # HR4: option B, the time row inside "ההר עכשיו"
    rows = ''.join('<div class="hr3%s"><b>%s</b><span class="v"><span>%s</span><span>%s</span><span>%s</span></span></div>'
                   % (' on' if on else '', t, a, b, c) for t, a, b, c, on in [
                       ('13:00', '−9°', '28', '0', False), ('14:00', '−9°', '31', '0.2', True), ('15:00', '−10°', '34', '0.6', False), ('16:00', '−11°', '36', '1.1', False)])
    sheet = ('<div class="sheet glass" style="gap: 10px; bottom: 8px; height: 336px"><div class="grab"></div><div class="tabs2"><span>רכבלים</span><span class="on">מזג אוויר</span></div>'
             '<div class="hsi"><button class="lt on" style="position: absolute; left: 0; top: 0; width: 44px; height: 44px; border-radius: 22px; border: 0; display: flex; align-items: center; justify-content: center; background: var(--acc); color: var(--onacc)"><b style="font-size: 11.5px; direction: ltr">14:00</b></button>%s</div>%s</div>' % (track_svg(14.0, now=13.33, uid='gb'), rows))
    out['HR4.dc.html'] = ('אור היום · חלופה ב, בתוך "ההר עכשיו" (HR4)',
                          PB.pic('hero', 'early', None) + PB.base('hero', cabins=True) + top() + veil(0, 844, 'var(--veil)') + sheet, 'day')
    # WX phones: the painter's own weather pictures stand for what the layers should look like
    out['WX1.dc.html'] = ('מזג אוויר · ים עננים, 8:30 (WX1)',
                          PB.pic('hero', 'dawn-cloud', None) + PB.base('hero', cabins=True) + top() + column('pic', scrub=8.5, open_=True) + pill(8.5, now=7.8, past=True) + dock2('dot'), 'day')
    flakes = ''.join('<circle cx="%d" cy="%d" r="%.1f"/>' % (hash((i, 'x')) % 390, hash((i, 'y')) % 844, 1 + (hash((i, 'r')) % 20) / 10) for i in range(0))
    rnd = __import__('random').Random(7)
    flakes = ''.join('<circle cx="%.0f" cy="%.0f" r="%.1f" opacity="%.2f"/>' % (rnd.random() * 390, rnd.random() * 844, 1.3 + rnd.random() * 2.2, .6 + rnd.random() * .4) for _ in range(190))
    fl = '<svg style="position: absolute; left: 0; top: 0" width="390" height="844" viewBox="0 0 390 844" fill="#fff" aria-hidden="true">%s</svg>' % flakes
    out['WX2.dc.html'] = ('מזג אוויר · שלג יורד, אור שטוח (WX2)', PB.pic('hero', 'noon-storm', None) + PB.base('hero', cabins=True) + fl + top() + column('pic', 'sun') + dock2('dot'), 'day')
    out['WX3.dc.html'] = ('מזג אוויר · רוח ברכס, שלג שעף (WX3)', PB.pic('hero', 'morning-wind', None) + PB.base('hero', cabins=True) + top() + column('pic', 'sun') + dock2('dot'), 'day')
    return out


# ------------------------------------------------------------------ wide boards
def tbl(rows, widths, head=None, size='15px'):
    h = ''
    if head:
        h = '<tr>%s</tr>' % ''.join('<td style="padding: 7px 10px; width: %s; font-weight: 700; font-size: 13px; color: #3C4753">%s</td>' % (w, t) for w, t in zip(widths, head))
    b = ''.join('<tr style="border-top: 1px solid #D9DEE4">%s</tr>' % ''.join('<td style="padding: 8px 10px; width: %s; vertical-align: top">%s</td>' % (w, c) for w, c in zip(widths, r)) for r in rows)
    return '<table style="border-collapse: collapse; width: 100%%; font-size: %s; line-height: 1.45">%s%s</table>' % (size, h, b)


def tag(kind, t):
    return '<span class="tg %s">%s</span>' % (kind, t)


def hr00():
    qs = (q(1, '<b>איפה הסליידר:</b> חלופה א, כפתור בעמודת הכפתורים? <b>(ההמלצה: א)</b>') +
          q(2, '<b>כשסוגרים את הסליידר,</b> האור נשאר על השעה שבחרת (הכפתור מראה אותה), ולחיצה על החץ חוזרת לעכשיו? <b>(ההמלצה: כן)</b>') +
          q(3, '<b>טווח השעות:</b> 7:00 עד 23:30 כמו היום, ורק היום הנוכחי? <b>(ההמלצה: כן; ימים נוספים לא עכשיו)</b>'))
    why = ('<div class="card" style="flex: 1"><p class="sec">למה לא מצאת אותו</p><h2>הסליידר קיים בקוד, אבל אין אליו כניסה</h2>' + li2(
        'בבדיקת העיצוב מ-24.11 נשארו בראש רק הגלולה ו"אני", והסליידר "נפתח משם".',
        'בפועל הוא נפתח משורת בדיקה שקיימת <b>רק בגרסת הבדיקה</b>, תחת "אני". בגרסה שלך אין שום דרך לפתוח אותו.',
        'המסקנה: זה לא באג, חסר לו מקום. צריך כפתור שרואים.') + '</div>')
    A = ('<div class="card" style="flex: 1; border: 3px solid #6B3FD4"><p class="sec">חלופה א · ההמלצה (עיצוב חדש, אחרי ההערה של פיני)</p><h2>כפתור "אור היום" שנפתח במקומו</h2>' + li2(
        '<b>איפה:</b> בעמודת הכפתורים, מתחת למתג תמונה / חי. סמל שמש ביום וירח בלילה, אותו גודל (44) כמו שאר הכפתורים.',
        '<b>פתיחה:</b> נגיעה אחת, והכפתור עצמו מתרחב במקומו לפס זכוכית בגובה 44. בלי שורה חדשה בתחתית ובלי להזיז כפתורים אחרים.',
        '<b>הפס:</b> השמיים של היום כמסלול (לילה כחול, שחר ורוד, יום תכלת, שעת זהב, ערב סגול) וגלגל אחד עליו. בלי מספרים ובלי סימונים. השעה נכתבת בקצה הפס, בצבע הבחירה.',
        '<b>סגירה:</b> נגיעה בקצה (השעה) או משיכה. החץ בקצה מחזיר לעכשיו, ונראה רק כשהאור לא על עכשיו.',
        '<b>בשני המצבים:</b> בתמונה ההר עובר בין מצבי האור (הצלבה של 450 מ״ש); בחי האור רציף, כי הצל מחושב.',
        '<b>המחיר:</b> כפתור רביעי בעמודה, ובזמן הפתיחה פס של 44 על חלק מהמסך. אין צורך לפנות מקום בתחתית.') + '</div>')
    B = ('<div class="card" style="flex: 1"><p class="sec">חלופה ב</p><h2>שורת שעה בתוך "ההר עכשיו"</h2>' + li2(
        '<b>איפה:</b> הגלולה העליונה פותחת את הגיליון, ובלשונית מזג האוויר עולה שורת שעה: אותו פס, ומתחתיה התחזית של השעה.',
        '<b>יתרון:</b> אין כפתור חדש על ההר, והשעה והתחזית על אותו ציר. כשמזג האוויר יופיע על ההר, אותה שעה תנהל גם אותו.',
        '<b>חסרון:</b> שתי נגיעות עד הסליידר, והוא מוסתר בגיליון שמעמם את ההר (כלל: גיליון מעמם את כל המסך). את האור לא רואים כשמזיזים אותו.',
        '<b>חסרון נוסף:</b> מי שמחפש את האור לא יחפש אותו ליד תחזית. זה בדיוק מה שקרה היום.') + '</div>')
    rule = ('<div class="card" style="flex: 1.1"><p class="sec">כללים לשתי החלופות</p><h2>מה לא משתנה</h2>' + li2(
        'ההר נשאר מתחת לכל שכבה, והמעבר יום ולילה של המסך הולך עם האור: אם גוררים ל-20:30, המסך עובר ללילה.',
        'הזמן משמאל לימין גם בעברית. בלי טקסט הסבר: סמל, שעה, והחץ.',
        'לקורא מסך: "אור היום", ערך בשעות ודקות, ופעולות "קדימה" ו"אחורה" של חצי שעה.',
        'פתיחה: תמיד על עכשיו. הבחירה לא נשמרת בין פתיחות.',
        'בלי שינוי בשרת, בלי נתון חדש ובלי מדידה חדשה.') + '</div>')
    shots = ''.join(V.shot2(PB.U('hero-' + k) if k in PB.IMG else '', 170, c) for k, c in [])
    inner = ('<p class="sec">שעת היום · הסליידר, מצבי האור ומזג האוויר (25.11.2026)</p><h1>איפה הסליידר חי, ואיך פותחים אותו</h1>'
             '<div class="rowx" style="align-items: stretch">%s%s%s</div><div class="rowx" style="align-items: stretch">%s<div class="card" style="flex: 1"><p class="sec">שאלות לפיני</p>%s'
             '<p class="cap" style="margin-top: 6px">הלוחות HR1 עד HR4 מציגים את שתי החלופות. ערכי הדוגמה (שעה עכשיו, תחזית) מומצאים להדמיה.</p></div></div>'
             % (why, A, B, rule, qs))
    MB_wide('HR00-Hour.dc.html', 'איפה הסליידר חי', inner, 1280, 1160)


def MB_wide(fn, title, body, w, h):
    PB.wide(fn, title, body, w, h)


def thumb(name, w=150):
    h = round(w * 844 / 390)
    return '<div class="th" style="width: %dpx; height: %dpx; border-radius: 12px"><img alt="" src="%s" style="left: 0; top: 0; width: 100%%; height: 100%%"></div>' % (w, h, PB.U('hero-' + name))


def hl00():
    # chart 1: sun height through the day, the keys on it
    W, Hh, x0, x1, y0, y1 = 1160, 250, 40, 1130, 205, 30
    Xc = lambda h: x0 + (h - 7) / 16.5 * (x1 - x0)
    Yc = lambda a: y0 - (a + 62) / 90 * (y0 - y1)
    pts = ' '.join('%.1f,%.1f' % (Xc(h), Yc(P.sun(h)[0])) for h in [7 + i / 8 for i in range(0, 133)])
    g = '<svg width="%d" height="%d" viewBox="0 0 %d %d" style="direction: ltr; display: block">' % (W, Hh, W, Hh)
    for a in (-60, -30, 0, 25):
        g += '<path d="M%d %.1f H%d" stroke="#D9DEE4"/><text x="%d" y="%.1f" font-size="11" fill="#3C4753">%d°</text>' % (x0, Yc(a), x1, 6, Yc(a) + 4, a)
    g += '<path d="M%d %.1f H%d" stroke="#8A97A6" stroke-dasharray="4 4"/>' % (x0, Yc(0), x1)
    for t in range(8, 24, 2):
        g += '<text x="%.1f" y="%d" font-size="12" fill="#3C4753" text-anchor="middle">%d:00</text>' % (Xc(t), Hh - 6, t)
    g += '<polyline points="%s" fill="none" stroke="#10161D" stroke-width="2"/>' % pts
    for n, kh, nm, old in KEYS:
        a = P.sun(kh)[0]
        c = '#10161D' if old else '#6B3FD4'
        g += '<circle cx="%.1f" cy="%.1f" r="6" fill="%s" stroke="#fff" stroke-width="2"/>' % (Xc(kh), Yc(a), c)
        up = n in ('first', 'late', 'early', 'golden', 'morning', 'after')
        ty = Yc(a) + (-14 if up else 22)
        g += '<text x="%.1f" y="%.1f" font-size="11.5" font-weight="700" fill="%s" text-anchor="middle">%s</text>' % (Xc(kh), ty, c, nm)
    g += '</svg>'
    # chart 2: how far the sun is from the nearest picture, every half hour
    W2, H2 = 1160, 170
    g2 = '<svg width="%d" height="%d" viewBox="0 0 %d %d" style="direction: ltr; display: block">' % (W2, H2, W2, H2)
    xx = lambda h: 40 + (h - 7) / 12 * 1090
    yy = lambda e: 135 - e / 22 * 115
    for e in (0, 10, 20):
        g2 += '<path d="M40 %.1f H1130" stroke="#D9DEE4"/><text x="6" y="%.1f" font-size="11" fill="#3C4753">%d°</text>' % (yy(e), yy(e) + 4, e)
    bw = 13
    for h in hours():
        if P.sun(h)[0] < 1:
            continue
        e0, e1 = ERR0[h][1], ERR1[h][1]
        g2 += '<rect x="%.1f" y="%.1f" width="%d" height="%.1f" fill="#8A97A6"/>' % (xx(h) - bw, yy(e0), bw, max(yy(0) - yy(e0), 1))
        g2 += '<rect x="%.1f" y="%.1f" width="%d" height="%.1f" fill="#6B3FD4"/>' % (xx(h), yy(e1), bw, max(yy(0) - yy(e1), 1))
    for t in range(9, 19):
        g2 += '<text x="%.1f" y="158" font-size="12" fill="#3C4753" text-anchor="middle">%d:00</text>' % (xx(t), t)
    g2 += '</svg>'
    # pictures
    exist = ['dawn', 'morning', 'noon', 'after', 'sunset', 'blue', 'night']
    newk = ['first', 'late', 'early', 'golden']
    def cell(n, new):
        a, z = SUN[n]
        return ('<div class="fr" style="width: 150px"><div style="position: relative">%s%s</div><p class="cap"><b>%s</b> · <span dir="ltr">%s</span><br><span dir="ltr">%d° · %d°</span></p></div>'
                % (thumb(n), tag('new', 'חדש') .replace('class="tg new"', 'class="tg new" style="position: absolute; right: 6px; top: 6px"') if new else '', HN[n], hm(HH[n]), round(a), round(z)))
    order = ['night'] + [n for n, _, _, _ in KEYS if n != 'night']
    cells = ''.join(cell(n, n in newk) for n in [n for n, _, _, _ in KEYS[:-1]])
    cells += cell('night', False)
    # the table: hour by hour
    rows = []
    for h in hours(7.0, 19.0, 0.5):
        a, z = P.sun(h)
        k0, e0 = ERR0[h]
        k1, e1 = ERR1[h]
        c0 = '#B3261E' if e0 >= 13 else ('#9A5200' if e0 >= 8 else '#10161D')
        c1 = '#B3261E' if e1 >= 13 else ('#9A5200' if e1 >= 8 else '#10161D')
        rows.append(('<b dir="ltr">%s</b>' % hm(h), '<span dir="ltr">%d°</span>' % round(a),
                     HN[k0], '<b style="color: %s" dir="ltr">%s</b>' % (c0, ('%.0f°' % e0) if a >= 1 else '—'),
                     HN[k1], '<b style="color: %s" dir="ltr">%s</b>' % (c1, ('%.0f°' % e1) if a >= 1 else '—')))
    half = len(rows) // 2 + 1
    T = lambda rs: tbl(rs, ['16%', '12%', '28%', '12%', '20%', '12%'], head=['שעה', 'גובה', 'היום', 'סטייה', 'מוצע', 'סטייה'], size='13px')
    t1, t2 = T(rows[:half]), T(rows[half:])
    # weight
    wt = tbl([
        ('<b>היום, ארוז באפליקציה</b>', 'שבע תמונות הר (1,185KB), שבע עדשות (315KB), שבע של הכפר (172KB)', '<b>1,673KB</b>', 'תקרה 1.75MB: נשארו כ-77KB'),
        ('<b>היום, צד Kobi</b>', 'שבע תמונות, יורדות מהאתר (לא ארוזות)', '<b>327KB</b>', 'תקרה 350KB לסט'),
        ('<b>ארבעה מצבים חדשים, ההר בלבד</b>', 'אור ראשון 85 · לפני הצהריים 174 · אחרי הצהריים מוקדם 170 · שעת הזהב 166 (נמדד, אותו קידוד)', '<b>595KB</b>', 'לא נכנס לארוז: יורד מהאתר, כמו צד Kobi'),
        ('ארבעה מצבים חדשים, גם עדשה, כפר ו-Kobi', 'עדשה כ-180KB, כפר כ-100KB, Kobi כ-200KB (הערכה לפי השכנות, עוד לא צוירו)', '<b>כ-1,075KB</b>', 'בטלפון: Kobi 327 + 1,075 = כ-1.4MB, מתחת ל-2MB'),
    ], ['23%', '47%', '12%', '18%'], head=['', 'מה', 'משקל', 'מול התקרה'], size='14px')
    qs = (q(4, '<b>להוסיף ארבעה מצבי אור</b> (אור ראשון, לפני הצהריים, אחרי הצהריים מוקדם, שעת הזהב)? <b>(ההמלצה: כן, ההר בלבד קודם, 595KB)</b>') +
          q(5, '<b>איך הם מגיעים:</b> יורדים מהאתר ברקע, לא ארוזים. עד שירדו, ההר משתמש במצב הקרוב מהשבעה. <b>(ההמלצה: כן; דורש סקירת ארכיטקט)</b>') +
          q(6, '<b>בוקר לפני הזריחה:</b> "אור ראשון" במקום התמונה של הדמדומים של הערב. <b>(ההמלצה: כן, חלק מ-4)</b>'))
    inner = ('<p class="sec">שעת היום · מצבי אור ביום</p><h1>מצבי אור לאורך היום: מה יש, מה חסר, וכמה זה שוקל</h1>'
             '<div class="rowx" style="align-items: stretch"><div class="card" style="flex: 1.15"><h2>מה יש ומה חסר</h2>' + li2(
                 'באפליקציה ובאתר <b>שבע תמונות</b>, צוירו ל-12.1.2027: שחר, בוקר, צהריים, אחר הצהריים, שקיעה, דמדומים ולילה. כל תמונה בנויה גם כעדשה וככפר, ולצד Kobi יש סט משלו. הנתונים: <span dir="ltr">site/data/peak.json</span> ,<span dir="ltr">site/img/peak/</span>.',
                 'התמונה נבחרת לפי <b>השמש</b> (גובה וכיוון), לא לפי השעה. מ-9:30 עד 17:30 כל תמונה מכסה שעה עד שעתיים, והצל לא זז בתוכה.',
                 '<b>סטייה:</b> כ-<b>%.0f°</b> בין השמש האמיתית לשמש של התמונה (ב-14:30). בין 11:30 ל-15:00 היא בין 13° ל-%.0f°, וזה מה שרואים: הצל קופץ.' % (MAX0, MAX0),
                 '<b>בוקר לפני הזריחה:</b> מ-8:00 עד 8:50 מוצגת תמונת הדמדומים של הערב (שמש במערב), אבל האור בבוקר במזרח. עד 8:00 לילה.',
                 '<b>מ-19:00 עד 23:30</b> תמונת לילה אחת. זה בסדר: אין שם שינוי אמיתי בעין.') + '</div>'
             '<div class="card" style="flex: 1"><h2>מה מוצע</h2>' + li2(
                 '<b>ארבעה מצבים חדשים</b>, בסגול בתרשים: אור ראשון (8:25), לפני הצהריים (11:55), אחרי הצהריים מוקדם (14:20), שעת הזהב (16:50).',
                 'הסטייה המרבית יורדת מ-<b>%.0f°</b> ל-<b>%.0f°</b>, והממוצע (בשעות עם שמש) מ-%.1f° ל-%.1f°.' % (MAX0, MAX1, AVG0, AVG1),
                 'ההר החי לא צריך תמונות: האור והצל מחושבים לכל שעה. החלק הזה רק בתמונה.',
                 'ארבע התמונות החדשות הן <b>הצעה</b>: אותו צייר של ההר עם צבעים מושלבים בין שכנים. שפר לפני שמאשרים.') + '</div></div>'
             '<div class="card"><h2>גובה השמש לאורך היום (12.1.2027, גודאורי) ומצבי האור</h2>' + g + '<p class="cap">נקודה שחורה: תמונה קיימת. נקודה סגולה: מוצעת. הקו המקווקו: האופק.</p></div>'
             '<div class="card"><h2>המצבים, לפי השעה שבה השמש במקום של התמונה</h2><div class="rowx" style="gap: 12px; flex-wrap: wrap">%s</div>'
             '<p class="cap">מתחת לכל תמונה: השעה, גובה השמש וכיוונה. אותו מבט פתיחה בכולן.</p></div>'
             '<div class="card"><h2>כמה רחוק השמש מהתמונה הקרובה, כל חצי שעה</h2>%s<p class="cap"><b style="color: #8A97A6">אפור</b>: היום, שבע תמונות. <b style="color: #6B3FD4">סגול</b>: אחד-עשר מצבים. בשעות בלי שמש אין סטייה.</p></div>'
             '<div class="rowx" style="align-items: flex-start"><div class="card" style="flex: 1"><h2>שעה אחר שעה</h2><div class="rowx" style="gap: 24px; align-items: flex-start"><div style="flex: 1">%s</div><div style="flex: 1">%s</div></div></div></div>'
             '<div class="card"><h2>משקל</h2>%s<p class="cap">מקור ההיום: חוזה ההר המצויר ו-<span dir="ltr">docs/ARCHITECTURE.md</span>. הארבעה החדשים נמדדו ב-webp איכות 80, 1170 על 2532, כמו הקיימות. אין עדיין בדיקה בטלפון.</p></div>'
             '<div class="card" style="border: 3px solid #6B3FD4"><p class="sec">מה צריך סקירת ארכיטקט</p>%s%s</div>') % (
        cells, g2, t1, t2, wt, li2(
            'סט מתחלף מהאתר: <span dir="ltr">peak.json</span> מקבל מבט <span dir="ltr">front</span> עם תמונות נוספות, <span dir="ltr">packaged: false</span>, באותו <span dir="ltr">rev</span>. תוספת בלבד, בלי <span dir="ltr">schema: 2</span>.',
            'תקרת הסט שיורד מ-350KB (Kobi) ל-600KB להר בלבד, או כ-1.1MB אם גם עדשה, כפר ו-Kobi. תקרת הכל בטלפון נשארת 2MB.',
            'כלל השימוש: בניגוד ל-Kobi, כל תמונה עומדת לבד. מצב שלא ירד מוחלף במצב הקרוב מהשבעה, ואין "הכל או כלום".',
            'הצל על הצד: הכיוון והגובה של כל מצב חדש נכנסים לטבלה של הלקוח, ולערכי הבדיקה <span dir="ltr">tools/fixtures/peak-projection.json</span> אין שינוי (ההטלה זהה).'), qs)
    MB_wide('HL00-Light.dc.html', 'מצבי אור לאורך היום', inner, 1280, 3680)


def wx00():
    layers = [
        ('שלג חדש', tag('ok', 'מעוצב ב-K03'), '<span dir="ltr">snow24</span> מעל 10 ס״מ (קיים בתשובה)', 'פחות סלע חשוף ונצנוץ. מסכת סלע אחת למבט, כ-25KB', 'לא'),
        ('רוח ברכס', tag('ok', 'מעוצב ב-K03'), 'משבים בנקודת Sadzele מעל 50 קמ״ש, כיוון <span dir="ltr">dir</span> (קיים)', 'שלג שעף מהרכסים: סמל אחד וטבלת קווי הרכס, כ-10KB', 'לא'),
        ('ים עננים', tag('ok', 'מעוצב ב-K03'), 'גובה בסיס הענן. <b>אין בתשובה</b>; אפשר להעריך מראות נמוכה בכפר וגבוהה למעלה, אבל זו הערכה חלשה', 'מסכה אחת למבט, כ-20KB, מטבלת העומק שב-<span dir="ltr">peak.json</span>', '<b>כן</b>: <span dir="ltr">cloud_base</span> (K03 כבר סימן את זה כשאלה לארכיטקט)'),
        ('יום לבן ושלג יורד', tag('ok', 'מעוצב ב-K03'), '<span dir="ltr">code</span> ו-<span dir="ltr">vis</span> (קיימים)', 'אור שטוח (מסנן צבע) ושלג יורד כשכבה נעה (חלקיקים בקוד, בלי נכס). תמונה שטוחה אמיתית: כ-150KB, אופציונלי', 'לא'),
        ('ענני שמיים וערפל לפי גובה', tag('ask', 'תוספת שלי'), '<span dir="ltr">code</span> (0 עד 3) ו-<span dir="ltr">vis</span> לפי גובה', 'כ-40KB (בהר החי כבר יש <span dir="ltr">cirrus.webp</span>), ומעבר צבע בלי נכס', 'לא'),
    ]
    lt = tbl([(f'<b>{a}</b>', b, c, d, e) for a, b, c, d, e in layers], ['14%', '13%', '28%', '30%', '15%'], head=['מצב', 'עיצוב', 'ממה נגזר (מהתשובה של השרת)', 'מה צריך לבנות', 'שינוי בשרת'], size='13.5px')
    cost = tbl([
        ('<b>ציור מלא</b> לכל שילוב של אור ומזג אוויר', '11 מצבי אור כפול 4 מצבי מזג אוויר: כ-44 תמונות, כ-7MB', tag('ask', 'לא ריאלי')),
        ('<b>שכבות על התמונה (ההמלצה)</b>', 'כ-100KB של נכסים (ענן, מסכות, עשן), שלג וערפל בקוד, ועוד תמונה שטוחה כ-150KB לבחירה', tag('ok', 'מומלץ')),
        ('ארוז, או מהאתר', 'הסט הארוז היום 1,673KB מתוך 1.75MB: 100KB לא נכנסים. מורידים מהאתר כמו צד Kobi, או מעלים את התקרה', tag('ask', 'להכרעה')),
    ], ['34%', '50%', '16%'], head=['דרך', 'מחיר', ''], size='14px')
    order = li2(
        '<b>שלב 1 · התמונה:</b> שלג חדש, רוח ברכס, יום לבן ושלג יורד. הנתונים כבר בתשובה של <span dir="ltr">/api/weather</span>. בלי שרת ובלי סקירת חוזה.',
        '<b>שלב 2 · התמונה:</b> ים עננים, אחרי ש-<span dir="ltr">cloud_base</span> נכנס לתשובה (זה מה ש-K03 סימן כשאלה לארכיטקט).',
        '<b>שלב 3 · ההר החי:</b> אותם מצבים בשיידר (הענן של הים כמשטח בגובה 2,130 מ׳). עבודה נפרדת, אחרי שהתמונה עובדת.',
        '<b>בכל שלב:</b> אם אין תחזית עדכנית, התמונה הרגילה של השעה ו"אין מידע", כמו ב-K03. לא מנחשים.')
    rules = li2(
        'התחזית היא <b>שלוש נקודות</b> (כפר 2,170, גודאורי 2,710, Sadzele 3,240). השכבות נקבעות לפי גובה, לא לפי מקום על המפה.',
        'מזג האוויר הולך <b>עם הסליידר</b> בתוך 72 השעות שבתחזית. שעות שעברו: השכבות נעלמות, והחלק של הפס שלפני עכשיו מעומעם (נתון חסר, לא "בהיר").',
        'תחזית בת 12 עד 48 שעות: השכבות נשארות והגלולה אומרת "תחזית ישנה" כמו היום (U06). מעל 48 שעות: אין שכבות.',
        '<b>תחזית, לא מדידה:</b> העיצוב לא טוען שהענן שם. הקרדיט <span dir="ltr">Open-Meteo.com</span> נשאר במקומו.',
        'עצירה בחיסכון בסוללה, בהפחתת תנועה ובהר במצב חי לרגע.')
    review = li2(
        '<b>חוזה:</b> שדה <span dir="ltr">cloud_cover</span> (ו-<span dir="ltr">cloud_cover_low</span>) לתשובה של <span dir="ltr">/api/weather</span>: תוספת בלבד, וערכי הבדיקה <span dir="ltr">tools/fixtures/weather-m6.json</span>. משימה לסשן השרת.',
        '<b>נכסים:</b> מסכות ועננים ב-<span dir="ltr">peak.json</span> (תוספת עם <span dir="ltr">rev</span>), וההכרעה ארוז או מהאתר.',
        '<b>ביצועים:</b> חלקיקים ותנועת ענן על מצב התמונה: תקציב פריימים, סוללה, חום, 30 פריימים בטלפון בינוני. <span dir="ltr">Stage</span> צריך שכבת מזג אוויר אחת לשני המימושים.',
        '<b>זיכרון:</b> שכבות הן עד שלוש מפות סיביות קטנות, מעל "שתי תמונות מפוענחות" הקיים: לוודא שזה נשאר בתקרה.',
        '<b>פרטיות ומדידה:</b> אין. בלי מיקום חדש ובלי מדידה חדשה.')
    qs = (q(7, '<b>K03 (מאושר) נשלח לאפליקציה</b> כשכבות על התמונה ולא בציור לכל שילוב? <b>(ההמלצה: כן)</b>') +
          q(8, '<b>הסדר:</b> קודם שלג חדש, רוח ברכס ויום לבן (שלב 1, בלי שרת), ואחר כך ים עננים? <b>(ההמלצה: כן)</b>') +
          q(9, '<b>שעות שעברו:</b> השכבות נעלמות והפס מעומעם, במקום להציג "בהיר"? <b>(ההמלצה: כן)</b>'))
    inner = ('<p class="sec">שעת היום · מזג אוויר על ההר, שלב שני של החלטה 83 · מקור: <span dir="ltr">/api/weather</span></p><h1>מזג אוויר על ההר: העיצוב כבר קיים</h1><p class="lead">פיני צדק: ארבעת המצבים כבר מעוצבים ומצוירים בקנבס, בעמוד "סבב 3", בלוח <b>K03</b> ("ההר נראה כמו היום שבחוץ"), ואושרו עם סבב 3 (החלטה 83: מזג אוויר על התמונה, בשלב שני). גם ארבע התמונות המצוירות קיימות (<span dir="ltr">design/redesign/assets/pk-hero-*</span>), אבל לא נארזו באפליקציה. כאן רק מה שנשאר: איך שולחים אותם, ומה זה עולה.</p>'
             '<div class="rowx" style="gap: 12px">%s%s%s%s</div>'
             '<div class="card"><h2>מה כבר קיים: K03, ארבע תמונות מצוירות</h2><p class="cap">אלה התמונות של K03, כל אחת באור אחד בלבד (שחר, בוקר, צהריים). בשביל כל שעה צריך את אותו מצב בכל אחד ממצבי האור, ולכן מציעים שכבות על התמונה הרגילה במקום ציור לכל שילוב.</p></div>'
             '<div class="card"><h2>ארבעת המצבים של K03, ומה כל אחד צריך כדי להגיע לאפליקציה</h2>%s</div>'
             '<div class="rowx" style="align-items: stretch"><div class="card" style="flex: 1"><h2>שתי דרכים, ומה כל אחת עולה</h2>%s</div><div class="card" style="flex: 1"><h2>סדר העבודה</h2>%s</div></div>'
             '<div class="rowx" style="align-items: stretch"><div class="card" style="flex: 1"><h2>כללים</h2>%s</div><div class="card" style="flex: 1; border: 3px solid #6B3FD4"><p class="sec">מה צריך סקירת ארכיטקט</p>%s</div></div>'
             '<div class="card"><p class="sec">שאלות לפיני</p>%s<p class="cap" style="margin-top: 6px">הלוחות WX1 עד WX3 מציגים את התוצאה. ערכי הדוגמה (טמפרטורה, רכבלים) מומצאים להדמיה.</p></div>') % (
        V.shot2(PB.U('hero-dawn-cloud'), 270, 'ים עננים · שחר'), V.shot2(PB.U('hero-morning-fresh'), 270, 'שלג טרי · בוקר'),
        V.shot2(PB.U('hero-morning-wind'), 270, 'רוח ברכס · בוקר'), V.shot2(PB.U('hero-noon-storm'), 270, 'סערה · אור שטוח'),
        lt, cost, order, rules, review, qs)
    MB_wide('WX00-Weather.dc.html', 'מזג אוויר על ההר', inner, 1280, 2600)


def build():
    for fn, (title, body, fx) in screens().items():
        phone(fn, title, body, fx)
    hr00()
    hl00()
    wx00()


PLACEMENT = [('HR00-Hour.dc.html', 0, 0, 1280, 1160, 'איפה הסליידר חי, ואיך פותחים אותו'),
             ('HR1.dc.html', 0, 1320, 390, 844, 'אור היום · כפתור בעמודה, סגור'), ('HR2.dc.html', 470, 1320, 390, 844, 'אור היום · פתוח, תמונה, 16:50'),
             ('HR3.dc.html', 940, 1320, 390, 844, 'אור היום · פתוח, מצב חי, 15:50'), ('HR4.dc.html', 1410, 1320, 390, 844, 'אור היום · חלופה ב, בתוך "ההר עכשיו"'),
             ('HL00-Light.dc.html', 0, 2420, 1280, 3680, 'מצבי אור לאורך היום'),
             ('WX00-Weather.dc.html', 0, 6240, 1280, 2600, 'מזג אוויר על ההר'),
             ('WX1.dc.html', 0, 9000, 390, 844, 'מזג אוויר · ים עננים, 8:30'), ('WX2.dc.html', 470, 9000, 390, 844, 'מזג אוויר · שלג יורד, אור שטוח'),
             ('WX3.dc.html', 940, 9000, 390, 844, 'מזג אוויר · רוח ברכס, שלג שעף')]


def place(canvas_path, out_path):
    j = json.load(open(canvas_path, encoding='utf8'))
    for fn in [k for k in j['boards'] if k.startswith(('HR', 'HL', 'WX')) and j['boards'][k].get('page') == PAGE]:
        del j['boards'][fn]
        if fn in j['order']:
            j['order'].remove(fn)
    for fn, x, y, w, h, t in PLACEMENT:
        code = fn.split('.dc.html')[0].split('-')[0]
        j['boards'][fn] = dict(x=x, y=y, w=w, h=h, title='%s (%s)' % (t, code), page=PAGE)
        j['order'].append(fn)
    if not any(pg['id'] == PAGE for pg in j['pages']):
        j['pages'].insert(0, dict(id=PAGE, name='שעת היום, מצבי אור ומזג אוויר'))
    j['notes']['t' + PAGE] = dict(kind='title1', maxW=1600, page=PAGE, x=0, w=1000, y=-200, text='שעת היום, מצבי אור ומזג אוויר (25.11.2026)')
    open(out_path, 'w', encoding='utf8').write(json.dumps(j, ensure_ascii=False, indent=1) + '\n')


if __name__ == '__main__':
    build()
    src = os.environ.get('CANVAS_JSON') or os.path.join(PROJ, 'canvas.json')
    place(src, os.path.join(PROJ, 'canvas.json'))
    print('boards:', [p[0] for p in PLACEMENT])
