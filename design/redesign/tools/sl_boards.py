#!/usr/bin/env python3
"""The run shelf and the layers, with the bar's run button (27.11.2026, Pini, three notes read together):
(2) a tap on a sort chip changes nothing visible; (3) the layers button is not understood, and the button column
jumps when it opens. One answer: one row of chips, in the shelf, and every chip both orders the cards and paints
the mountain. Boards SL00 to SL8 on the page of dk_boards. Design only.
Handoff: design/handoff/2026-11-27-redesign-shelf-layers/.
Usage: python3 design/redesign/tools/sl_boards.py [canvas.json path] [--preview <dir> <assets dir>]"""
import os, sys, json, re
import numpy as np
import dk_boards as DK
from dk_boards import UX, ic, wb, li, PROJ, PB, G4, P, mountain, rc
from peak_boards import VH, pic, both, svg, tag, sign, facts, mini, glow_run, shaded, HEX, HE
from v56_boards import ctl_sw, top

PAGE = DK.PAGE
T0 = -120
CUR = 'Kudebi 1'
CLOSED = ('Sadzele', 'Firni')
CORD = {'green': 0, 'blue': 1, 'red': 2, 'black': 3}
SUNC, SHADEC = '#FFC93A', '#B9CCFA'

SCSS = """
.H .sp{position:absolute;left:12px;right:12px;bottom:238px;border-radius:18px;padding:9px 12px 10px;box-sizing:border-box;display:flex;flex-direction:column;gap:8px}
.H .sp .l{display:flex;align-items:center;gap:6px;font-size:13.5px;font-weight:700;white-space:nowrap}
.H .sp .l span{font-weight:500;color:var(--ink2)}
.H .sp .l .p{margin-inline-start:auto;font-variant-numeric:tabular-nums}
.H .stp2{display:flex;align-items:flex-end;height:26px}
.H .stp2 i{width:7px;border-radius:3.5px;flex:none;margin-inline-start:3px}
.H .stp2 i.g{margin-inline-start:9px}.H .stp2 i:first-child{margin-inline-start:0}
.H .stp2 i.c{box-shadow:0 0 0 2px var(--bg),0 0 0 4px var(--ink)}
.H .why2{font-size:12.5px;font-weight:700;color:var(--acc)}
.H .lbar{position:absolute;left:10px;right:10px;bottom:12px;min-height:60px;border-radius:30px;display:flex;align-items:center;gap:10px;padding:8px 8px 8px 18px;box-sizing:border-box}
.H [dir=rtl] .lbar{padding:8px 18px 8px 8px}
.H .lbar b{font-size:15px;font-weight:700;display:block;line-height:1.25}
.H .lbar .s2{font-size:12px;color:var(--ink2)}
.H .lbar .lg2{margin-inline-start:auto;display:flex;flex-direction:column;gap:3px;font-size:11.5px;color:var(--ink2)}
.H .lbar .lg2 span{display:flex;align-items:center;gap:6px;white-space:nowrap}
.H .lbar .lg2 i{width:16px;height:5px;border-radius:3px;flex:none}
.H .lbar .x{width:44px;height:44px;border-radius:50%;border:1px solid var(--line);display:flex;align-items:center;justify-content:center;flex:none}
.H .mc button.lon{background:var(--ink);color:var(--bg);border-color:transparent}
"""
G4.MB.CSS = G4.MB.CSS + SCSS

# ---------------------------------------------------------------- the data behind the boards
RUNS = [p for p in P.PISTES if p['named']]
F = {p['key']: facts(p['key']) for p in RUNS}
ST = {p['key']: P.stats(p['key']) for p in RUNS}


def _sun(p):
    tot = sun = 0
    for s in p['segs']:
        x, y, vis, z, q = P.project(VH, s); sh = shaded(q, 'dawn'); tot += len(sh); sun += int((~sh).sum())
    return sun / max(tot, 1)


SUN = {p['key']: _sun(p) for p in RUNS}
snow = lambda k: 12 if ST[k]['top'] >= 2900 else 8 if ST[k]['top'] >= 2600 else 4 if ST[k]['top'] >= 2300 else 2   # example forecast
lift0 = lambda k: (F[k]['frm'] or [None])[0]
lbot = lambda n: float(P.elev(*P.lift_ends(P.flift(n))[0])) if n else 9e9
is_open = lambda k: lift0(k) not in CLOSED


def order(kind):
    """the keys in the order of this chip, and the group of each"""
    K = [p['key'] for p in RUNS]
    if kind == 'lift':
        K.sort(key=lambda k: (lbot(lift0(k)), lift0(k) or 'zz', CORD[F[k]['color']], k)); G = [lift0(k) for k in K]
    elif kind == 'hard':
        K.sort(key=lambda k: (CORD[F[k]['color']], ST[k]['steep'], k)); G = [F[k]['color'] for k in K]
    elif kind == 'long':
        K.sort(key=lambda k: -F[k]['len']); G = [0] * len(K)
    elif kind == 'sun':
        K.sort(key=lambda k: -SUN[k]); G = [SUN[k] >= .5 for k in K]
    elif kind == 'open':
        K.sort(key=lambda k: (not is_open(k), lbot(lift0(k)), k)); G = [is_open(k) for k in K]
    else:
        K.sort(key=lambda k: (-snow(k), k)); G = [snow(k) for k in K]
    return K, G


def tick(kind, k):
    """colour and height (10 to 26) of a run's tick: the height is what the chip orders by"""
    c = 'var(--%s)' % F[k]['color']; h = 18
    if kind == 'hard': h = 8 + min(ST[k]['steep'], 36) / 2
    elif kind == 'long': h = 8 + 18 * F[k]['len'] / 3200
    elif kind == 'sun': h = 8 + 18 * SUN[k]; c = SUNC if SUN[k] >= .5 else '#9DB0D6'
    elif kind == 'open': c = c if is_open(k) else '#8A93A3'; h = 18 if is_open(k) else 10
    elif kind == 'snow': h = 8 + 1.5 * snow(k); c = 'var(--acc)' if snow(k) >= 10 else '#7FB2FF'
    return c, max(8, min(26, h))


def strip(kind):
    K, G = order(kind); o = ''
    for i, k in enumerate(K):
        c, h = tick(kind, k)
        o += '<i class="%s%s" style="height: %.0fpx; background: %s"></i>' % ('g ' if i and G[i] != G[i - 1] else '', 'c' if k == CUR else '', h, c)
    return '<div class="stp2" aria-hidden="true">%s</div>' % o


def label(kind):
    K, G = order(kind); i = K.index(CUR); grp = [k for k, g in zip(K, G) if g == G[i]]
    if kind == 'lift': a, b, p = 'לפי רכבל', '<bdi>%s</bdi>' % G[i], '%d מתוך %d' % (grp.index(CUR) + 1, len(grp))
    elif kind == 'hard': a, b, p = 'לפי קושי', {'green': 'ירוקים', 'blue': 'כחולים', 'red': 'אדומים', 'black': 'שחורים'}[G[i]], '%d מתוך %d' % (grp.index(CUR) + 1, len(grp))
    elif kind == 'long': a, b, p = 'מהארוך לקצר', '', '%d מתוך %d' % (i + 1, len(K))
    elif kind == 'sun': a, b, p = 'בשמש עכשיו', '%d מסלולים בשמש' % sum(1 for k in K if SUN[k] >= .5), '%d מתוך %d' % (i + 1, len(K))
    elif kind == 'open': a, b, p = 'פתוחים עכשיו', '%d מתוך %d' % (sum(1 for k in K if is_open(k)), len(K)), ''
    else: a, b, p = 'שלג חדש', 'הכי הרבה למעלה', '%d מתוך %d' % (i + 1, len(K))
    return '<div class="l">%s%s<span class="p">%s</span></div>' % (a, (' <span>· %s</span>' % b) if b else '', p)


def why(kind, k):
    f = F[k]
    if kind == 'lift':
        return ('חוזרים ב-<bdi>%s</bdi> · %s דק׳' % (f['to'][0], PB.lift(f['to'][0])['dur'])) if f['to'] else 'ממשיכים במסלול אחר', False
    if kind == 'hard': return 'הקטע התלול <bdi>%d°</bdi>' % ST[k]['steep'], True
    if kind == 'long': return 'מקום %d באורך' % (order('long')[0].index(k) + 1), True
    if kind == 'sun': return '<bdi>%d%%</bdi> מהמסלול בשמש' % round(SUN[k] * 100), True
    if kind == 'open': return ('פתוח · <bdi>%s</bdi> עובד' % lift0(k)) if is_open(k) else 'סגור · <bdi>%s</bdi> לא עובד' % lift0(k), True
    return '<bdi>%d</bdi> ס״מ שלג חדש' % snow(k), True


def card(kind, k, left, on):
    f = F[k]; w, hl = why(kind, k)
    lvl = {'green': 'קל', 'blue': 'קל', 'red': 'בינוני', 'black': 'קשה'}[f['color']]
    return ('<div class="card glass" style="position: absolute; left: %dpx; bottom: 22px; width: 232px; height: 156px; flex-direction: row; gap: 12px; align-items: stretch;%s">'
            '<div style="flex: none; display: flex; align-items: center">%s</div><div style="display: flex; flex-direction: column; gap: 6px; min-width: 0; flex: 1">'
            '<div style="display: flex; align-items: center; gap: 8px">%s<b style="font-size: 17px; direction: ltr; white-space: nowrap; overflow: hidden; text-overflow: ellipsis">%s</b></div><p class="sub">%s · %s</p>'
            '<div class="nums" style="gap: 14px"><div><span class="num" style="font-size: 24px">%s</span><span class="cap">ק״מ</span></div><div><span class="num" style="font-size: 24px">%d</span><span class="cap">מ׳ ירידה</span></div></div>'
            '<p class="%s" style="font-size: 12px; white-space: nowrap">%s</p></div></div>'
            % (left, '' if on else ' opacity: .72;', mini(k), sign(k), f['name'], HE[f['color']], lvl, '%.1f' % (f['len'] / 1000), f['drop'], 'why2' if hl else 'sub', w))


def paths(k, col, w, case=None, op=1, dash=''):
    o = ''
    for ch in P.all_chunks(VH, k):
        d = P.pathd(ch)
        if case: o += '<path d="%s" stroke="%s" stroke-width="%.1f"/>' % (d, case, w + 2.4)
        o += '<path d="%s" stroke="%s" stroke-width="%.1f" opacity="%s"%s/>' % (d, col, w, op, dash)
    return o


def paint(kind, shelf=True):
    """the mountain under a chip: what the chip asks is what is lit"""
    K, G = order(kind); i = K.index(CUR); grp = [k for k, g in zip(K, G) if g == G[i]]

    def fn(mode, light):
        pal = P.PAL[mode]; case = pal['case']
        veil = '<rect width="390" height="844" fill="%s"/>' % ('rgba(40,62,110,.26)' if mode == 'day' else 'rgba(2,6,18,.36)')
        o = P.details(VH, mode, light) + (svg(VH, veil) if shelf else '')
        lit = ''
        if kind in ('lift', 'hard'):
            o += P.runs_svg(VH, mode, 'ghost', skip=grp, glow=False) + P.lifts_svg(VH, mode, light, dim=True)
            if kind == 'lift': o += P.lifts_svg(VH, mode, light, only=[G[i]])
            lit = ''.join(paths(k, pal[F[k]['color']], 2.6, case) for k in grp if k != CUR)
        elif kind == 'long':
            o += P.runs_svg(VH, mode, 'ghost', skip=K[:6], glow=False) + P.lifts_svg(VH, mode, light, dim=True)
            lit = ''.join(paths(k, pal[F[k]['color']], 2.6, case) for k in K[:6] if k != CUR)
        else:
            o += P.lifts_svg(VH, mode, light, dim=True)
            for k in K:
                if k == CUR and shelf: continue
                if kind == 'sun': lit += paths(k, SUNC, 3.0, '#fff') if SUN[k] >= .5 else paths(k, SHADEC, 1.8, None, .95)
                elif kind == 'open': lit += paths(k, pal[F[k]['color']], 3.0, case) if is_open(k) else paths(k, '#8A93A3', 1.8, None, 1, ' stroke-dasharray="6 5"')
                else: lit += paths(k, pal['blue'] if False else ('#7B4FE0' if snow(k) >= 10 else '#7FB2FF'), 3.2 if snow(k) >= 10 else 2.8, case)
        return o + svg(VH, lit) + (svg(VH, glow_run(VH, CUR, mode, w=3.8)) if shelf else '')
    return fn


def stage(inner):
    """the mountain always fills the screen: under the shelf the view rises 120 and comes a little closer, so the
    picture reaches the bottom edge behind the cards (never a band of background)"""
    return ('<div style="position: absolute; left: 0; top: 0; width: 390px; height: 844px; transform-origin: 50%% %dpx; transform: scale(%.4f)">%s</div>'
            % (T0, (844 - T0) / 844, inner))


CHIPS = [('lift', 'לפי רכבל'), ('hard', 'לפי קושי'), ('long', 'הארוכים'), ('sun', 'בשמש עכשיו'), ('open', 'פתוחים'), ('snow', 'שלג חדש')]


def shelf(kind, six=False):
    K, G = order(kind); i = K.index(CUR)
    R = P.run_path(VH, CUR); x, y = P.at(R, R['cum'][-1] * .5)[:2]
    b = stage((pic('hero', 'dawn', 'night', top=T0) if kind == 'sun' else pic('hero', top=T0)) + both(paint(kind), top=T0)
              + tag(x - 44, y + T0, CUR, HEX[F[CUR]['color']]))
    b += top()
    ch = CHIPS if six else CHIPS[:4]
    show = ch[2:] if six and kind in ('open', 'snow') else ch[:4]
    cut = ' style="margin-inline-start: -46px"' if show is not ch and show[0] != ch[0] else ''
    chips = ''.join('<span class="ch%s"%s>%s</span>' % (' on' if c == kind else '', cut if n == 0 else '', t) for n, (c, t) in enumerate(show))
    b += '<div class="sp glass">%s%s</div>' % (label(kind), strip(kind))
    b += '<div class="chipr" style="position: absolute; right: 12px; left: 12px; bottom: 192px">%s</div>' % chips
    for n, left in ((i - 1, 330), (i, 79), (i + 1, -172)):
        if 0 <= n < len(K): b += card(kind, K[n], left, n == i)
    return b


def column(layers=True, on=False):
    c = ctl_sw('pic')
    lay_btn = '<button class="glass" aria-label="שכבות">'
    assert lay_btn in c
    return c.replace(lay_btn, '<button class="glass lon" aria-label="שכבות">') if on else (c if layers else re.sub(re.escape(lay_btn) + r'.*?</button>', '', c, count=1, flags=re.S))


def layer_bar(empty=False):
    if empty:
        return ('<div class="lbar glass" role="status"><span><b>אין שלג חדש</b><span class="s2">ב-24 השעות האחרונות · לפי התחזית</span></span>'
                '<span class="lg2"></span><button class="x" aria-label="סגירת השכבה">%s</button></div>' % ic(UX.P_X, 18, 2))
    return ('<div class="lbar glass" role="status"><span><b>שלג חדש</b><span class="s2">ב-24 השעות האחרונות · לפי התחזית</span></span>'
            '<span class="lg2"><span><i style="background: #7B4FE0"></i>10 ס״מ ומעלה</span><span><i style="background: #7FB2FF"></i>עד 10 ס״מ</span></span>'
            '<button class="x" aria-label="סגירת השכבה">%s</button></div>' % ic(UX.P_X, 18, 2))


def layers_keep(empty=False):
    m = (pic('hero', 'morning', 'night') + PB.base('hero', cabins=True)) if empty else (pic('hero') + both(paint('snow', shelf=False)))
    chips = ''.join('<span class="ch%s">%s</span>' % (' on' if t == 'שלג חדש' else '', t) for t in ('שלג חדש', 'לפי קושי', 'פתוחים', 'בשמש עכשיו'))
    return m + top() + '<div class="chipr" style="position: absolute; right: 12px; left: 12px; top: 106px">%s</div>' % chips + column(on=True) + layer_bar(empty)


def phones():
    return {
        'SL1.dc.html': ('המדף: "לפי רכבל". הפס מראה את כל המסלולים בסדר הזה', shelf('lift'), None),
        'SL2.dc.html': ('נוגעים ב"לפי קושי": הפס מסתדר לפי צבע, וכל האדומים נדלקים', shelf('hard'), None),
        'SL3.dc.html': ('"הארוכים": גובה כל פס הוא האורך', shelf('long'), None),
        'SL4.dc.html': ('"בשמש עכשיו": ההר נצבע שמש וצל', shelf('sun'), None),
        'SL5.dc.html': ('מומלץ: גם "פתוחים" ו"שלג חדש" כאן, במקום כפתור השכבות', shelf('snow', six=True), None),
        'SL6.dc.html': ('מומלץ: ההר בלי כפתור השכבות', mountain() + column(layers=False) + DK.dock_a(), None),
        'SL7.dc.html': ('אם משאירים את השכבות: מקרא במקום הסרגל, והעמודה לא זזה', layers_keep(), None),
        'SL8.dc.html': ('אם משאירים: בלי שלג חדש, המסלולים נשארים בצבע שלהם', layers_keep(empty=True), None),
    }


HB = 1420


def sl00():
    whyl = li('<b>שתי שורות של צ׳יפים שנראות אותו דבר ועושות דברים שונים.</b> במדף הן ממיינות את הכרטיסים; בשכבות הן צובעות את ההר. "בשמש עכשיו" מופיע בשתיהן, ו"מהקל לקשה" מול "קל ← קשה" כמעט אותה מילה.',
             '<b>במדף, המיון משנה רק מה שלא רואים.</b> הכרטיס שבאמצע נשאר, השכנים מתחלפים בשקט, וההר לא משתנה. אין שום דבר שמראה את הסדר עצמו.',
             '<b>בשכבות, אין שם ואין מקרא.</b> לא כתוב איזו שכבה דלוקה ומה הצבעים אומרים, וכשאין נתון (אין שלג חדש) כל המסלולים נהיים אפורים, כאילו משהו התקלקל.',
             '<b>העמודה קופצת 210 נקודות</b> כי הגיליון של השכבה גבוה והסרגל נעלם, ונשאר רווח ריק.')
    one = li('<b>שורה אחת של צ׳יפים, במדף.</b> שישה: לפי רכבל, לפי קושי, הארוכים, בשמש עכשיו, פתוחים, שלג חדש.',
             '<b>כל צ׳יפ עושה שני דברים יחד:</b> מסדר את הכרטיסים, וצובע את ההר באותה שאלה. כך רואים מה השתנה גם על ההר וגם למטה.',
             '<b>פס המסלולים</b> מעל הצ׳יפים: 28 פסים קטנים, אחד לכל מסלול, בסדר של הצ׳יפ הדלוק. הגובה של כל פס הוא מה שממיינים לפיו (אורך, שיפוע, שמש, שלג), והמסלול שבאמצע מסומן. כשמחליפים צ׳יפ הפסים זזים למקום החדש.',
             '<b>שורה אחת בכרטיס אומרת למה הוא כאן:</b> המקום שלו באורך, כמה ממנו בשמש, הקטע התלול.',
             '<b>כפתור השכבות יורד מהעמודה.</b> אין יותר "שכבה" להבין, אין כפתור בלי מילה, והעמודה לא זזה אף פעם.')
    row = lambda *c: '<tr>%s</tr>' % ''.join('<td>%s</td>' % x for x in c)
    tbl = ('<table style="border-collapse: collapse; width: 100%"><tr><th>צ׳יפ</th><th>הכרטיסים</th><th>ההר</th><th>הפס</th><th>השורה בכרטיס</th></tr>'
           + row('לפי רכבל', 'קבוצה לכל רכבל, מהכפר ומעלה', 'המסלולים של הרכבל הזה דלוקים, והרכבל בצבע', 'רווח בין רכבל לרכבל', why('lift', CUR)[0])
           + row('לפי קושי', 'ירוקים, כחולים, אדומים, שחורים; בתוך הצבע מהמתון', 'כל המסלולים בצבע של הכרטיס דלוקים', 'ארבעה גושי צבע; הגובה הוא השיפוע', why('hard', CUR)[0])
           + row('הארוכים', 'מהארוך לקצר', 'ששת הארוכים דלוקים', 'הגובה הוא האורך, יורד', why('long', CUR)[0])
           + row('בשמש עכשיו', 'מי שיותר בשמש, קודם', 'שמש בצהוב, צל בתכלת', 'צהובים ואז אפורים', why('sun', CUR)[0])
           + row('פתוחים', 'הפתוחים קודם', 'פתוח בצבע, סגור אפור מקווקו', 'צבעוניים ואז אפורים נמוכים', why('open', CUR)[0])
           + row('שלג חדש', 'מי שקיבל יותר שלג, קודם', 'סגול מ-10 ס״מ, תכלת עד 10', 'הגובה הוא הס״מ', why('snow', CUR)[0])
           + '</table>')
    keep = li('<b>שם ומקרא:</b> פס בתחתית עם שם השכבה, שורה אחת של מה זה ("ב-24 השעות האחרונות · לפי התחזית"), מקרא של הצבעים וכפתור סגירה. (SL7)',
              '<b>העמודה לא זזה:</b> הפס יושב בדיוק במקום ובגובה של הסרגל התחתון, ומחליף אותו בהתמוססות. בלי קפיצה ובלי רווח.',
              '<b>כשאין נתון:</b> המסלולים נשארים בצבע שלהם, והפס אומר "אין שלג חדש". בלי אפור. (SL8)',
              '<b>השמות זהים למדף:</b> "לפי קושי", "פתוחים". עדיין נשארות שתי שורות צ׳יפים דומות, ולכן זו לא ההמלצה.')
    asks = ''.join('<p class="q"><span class="qn">%d</span><span>%s</span></p>' % (i + 1, t) for i, t in enumerate([
        '<b>צ׳יפ במדף גם מסדר וגם צובע את ההר, עם פס המסלולים ושורת ה"למה" בכרטיס?</b> (ההמלצה: כן)',
        '<b>השכבות:</b> א. עוברות למדף וכפתור השכבות יורד (SL5, SL6); ב. נשארות, עם שם, מקרא ועמודה שלא זזה (SL7, SL8); ג. יורדות לגמרי, בלי "פתוחים" ו"שלג חדש". (ההמלצה: א)',
        '<b>"לפי קושי"</b> במקום "מהקל לקשה" ו"קל ← קשה". (כן; הניסוח דרך סשן התוכן)']))
    inner = ('<p class="sec">המדף והשכבות, יחד עם כפתור "מסלולים" · 27.11.2026 · ממתין לפיני</p>'
             '<h1>שלוש ההערות הן בעיה אחת: שתי שורות צ׳יפים דומות, ואף אחת לא מראה מה היא עושה</h1>'
             '<p class="lead">ההצעה: שורה אחת, במדף. כל צ׳יפ מסדר את הכרטיסים וצובע את ההר באותו רגע, ופס קטן של כל המסלולים מראה את הסדר עצמו.</p>'
             '<div class="rowx" style="align-items: stretch"><div class="card" style="flex: 1"><h2>למה לא מבינים</h2>%s</div>'
             '<div class="card" style="flex: 1.15"><h2>ההצעה</h2>%s</div></div>'
             '<div class="card"><h2>מה כל צ׳יפ עושה</h2>%s</div>'
             '<div class="rowx" style="align-items: stretch"><div class="card" style="flex: 1"><h2>אם משאירים את כפתור השכבות</h2>%s</div>'
             '<div class="card" style="flex: 1"><h2>לפיני</h2>%s</div></div>'
             '<p class="cap">הסדרים, האורכים, השיפועים והצל בלוחות מחושבים מהנתונים של האתר. השלג והרכבלים הסגורים (Sadzele, Firni) הם דוגמה. נמדד בכלי העיצוב, לא בטלפון. '
             'המסירה: <span dir="ltr">design/handoff/2026-11-27-redesign-shelf-layers/</span></p>'
             % (whyl, one, tbl, keep, asks))
    wb('SL00-Shelf.dc.html', 'המדף והשכבות', 1280, HB, inner)


FILES = ['SL00-Shelf.dc.html'] + ['SL%d.dc.html' % i for i in range(1, 9)]


def write_canvas(cj):
    j = json.load(open(cj, encoding='utf8'))
    for fn in FILES: j['boards'].pop(fn, None)
    j['order'] = [f for f in j['order'] if f not in FILES]
    T = phones()
    y0 = max(b['y'] + b['h'] for f, b in j['boards'].items() if b.get('page') == PAGE) + 520
    place = [('SL00-Shelf.dc.html', 0, y0, 1280, HB, 'המדף והשכבות: בעיה אחת, הצעה אחת')]
    for k, fn in enumerate(FILES[1:]):
        place.append((fn, 470 * k, y0 + HB + 140, 390, 844, T[fn][0]))
    for fn, x, y, w, h, t in place:
        code = fn.split('.dc.html')[0].split('-')[0]
        j['boards'][fn] = dict(x=x, y=y, w=w, h=h, title='%s (%s)' % (t, code), page=PAGE)
        j['order'].append(fn)
    j['notes']['tsl'] = dict(kind='title1', maxW=1600, page=PAGE, x=0, y=y0 - 300, text='המדף והשכבות: מה צ׳יפ עושה')
    for pg in j['pages']:
        if pg['id'] == PAGE: pg['name'] = 'בוחרים מסלול: הכפתור, המדף והשכבות (27.11)'
    j['launch'] = dict(view='canvas', page=PAGE)
    open(cj, 'w', encoding='utf8').write(json.dumps(j, ensure_ascii=False, indent=1) + '\n')


def main():
    args = sys.argv[1:]
    pv = None
    if '--preview' in args:
        i = args.index('--preview'); pv = (args[i + 1], args[i + 2]); del args[i:i + 3]
    cj = args[0] if args else os.path.join(PROJ, 'canvas.json')
    for fn, v in phones().items():
        PB.phone(fn, v[0], v[1], night=v[2] == 'night', fixed=v[2])
    sl00()
    write_canvas(cj)
    if pv:
        keep = DK.FILES; DK.FILES = FILES; DK.preview(*pv); DK.FILES = keep
    print('ok', cj)


if __name__ == '__main__':
    main()
