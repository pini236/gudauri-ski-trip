#!/usr/bin/env python3
"""Version 2 of the run button and the shelf (27.11.2026): Pini chose the direction (a "מסלולים" button; the layers
move into the shelf and their button goes) and asked for more craft. Same concept, pushed: the button carries a
miniature of the shelf's strip and opens into it; the active chip takes the colour of what it paints; the card's big
number is the chip's answer; ranks are written on the mountain. Boards SV00 to SV6 on the page of dk_boards.
Design only. Handoff: design/handoff/2026-11-27-redesign-shelf-layers/ (section "Version 2").
Usage: python3 design/redesign/tools/sv_boards.py [canvas.json path] [--preview <dir> <assets dir>]"""
import os, sys, json, re
import sl_boards as SL
from sl_boards import DK, UX, ic, wb, li, PROJ, PB, G4, P, F, ST, SUN, snow, order, tick, lift0, is_open, CUR, T0, CHIPS
from peak_boards import VH, pic, both, tag, sign, mini, HEX, HE
from v56_boards import top
import gap3_boards as G3

PAGE = DK.PAGE
NR = len(F)
P_MTN = '<path d="M3 19 9.500 8l3.500 5.500L15.500 10 21 19z"/>'
P_RUL = '<path d="M4 16 16 4l4 4L8 20z"/><path d="m8 12 2 2M11 9l2 2M14 6l2 2"/>'
P_OK = '<circle cx="12" cy="12" r="8.500"/><path d="m8.500 12 2.500 2.500 4.500-5"/>'
P_SNW = '<path d="M12 3v18M4.200 7.500l15.600 9M19.800 7.500l-15.600 9"/>'
ICON = {'lift': UX.P_GOND, 'hard': P_MTN, 'long': P_RUL, 'sun': G3.P_SUN, 'open': P_OK, 'snow': P_SNW}
ONC = {'sun': ('#FFC93A', '#3B2A00'), 'open': ('var(--good)', '#fff'), 'snow': ('var(--acc)', 'var(--onacc)')}

VCSS = """
.H .dock4{position:absolute;left:10px;right:10px;bottom:12px;height:64px;border-radius:32px;display:flex;align-items:center;gap:8px;padding:0 8px;box-sizing:border-box}
.H .dock4 .mn{height:48px;border-radius:24px;background:var(--ink);color:var(--bg);display:flex;align-items:center;gap:7px;padding:0 16px 0 14px;font-size:15.5px;font-weight:700;flex:none}
.H .dock4 .rn2{flex:1;min-width:0;height:48px;border-radius:24px;border:1px solid var(--line);background:var(--tile);display:flex;align-items:center;gap:8px;padding:0 10px 0 16px;box-sizing:border-box;text-align:start}
.H [dir=rtl] .dock4 .rn2{padding:0 16px 0 10px}
.H .rn2 .w{display:flex;flex-direction:column;gap:5px;min-width:0;flex:1}
.H .rn2 .w b{font-size:15.5px;font-weight:700;line-height:1;display:flex;align-items:baseline;gap:7px}
.H .rn2 .w b span{font-size:12.5px;font-weight:700;color:var(--ink2);font-variant-numeric:tabular-nums}
.H .mst{display:flex;gap:1.5px;height:5px}
.H .mst i{width:3px;height:5px;border-radius:1.5px;flex:none}
.H .rn2 .up{width:32px;height:32px;border-radius:50%;background:var(--line);display:flex;align-items:center;justify-content:center;flex:none}
.H .dock4 .rq{transform:scale(1.09)}
.H .ch2{height:38px;padding:0 13px 0 11px;border-radius:19px;border:1px solid var(--line);font-size:13px;font-weight:500;display:inline-flex;align-items:center;gap:6px;white-space:nowrap;flex:none;background:var(--glass)}
.H .ch2.on{background:var(--ink);color:var(--bg);border-color:transparent;font-weight:700;box-shadow:0 6px 14px rgba(10,25,45,.25)}
.H .sp2{position:absolute;left:12px;right:12px;bottom:246px;border-radius:22px;padding:11px 14px 12px;box-sizing:border-box;display:flex;flex-direction:column;gap:10px}
.H .sp2 .l{display:flex;align-items:baseline;gap:7px;font-size:15.5px;font-weight:700;white-space:nowrap;min-height:20px}
.H .sp2 .l span{font-size:13px;font-weight:500;color:var(--ink2)}
.H .sp2 .l .p{margin-inline-start:auto;font-variant-numeric:tabular-nums;font-weight:700}
.H .tk2{position:relative;height:30px;direction:ltr}
.H .tk2 i{position:absolute;left:0;bottom:0;width:8px;border-radius:4px}
.H .tk2 i.c{box-shadow:0 0 0 2px var(--bg),0 0 0 4px var(--ink)}
.H .tk2 u{position:absolute;bottom:-9px;width:0;height:0;border:5px solid transparent;border-top-color:var(--ink);border-bottom-width:0}
.H .vcd{position:absolute;bottom:22px;width:232px;height:172px;border-radius:24px;padding:14px 16px;box-sizing:border-box;display:flex;gap:12px;align-items:stretch}
.H .vcd .vshape{flex:none;width:52px;height:100%;border-radius:16px;display:flex;align-items:center;justify-content:center}
.H .vcd .vmain{display:flex;flex-direction:column;min-width:0;flex:1;justify-content:space-between}
.H .vcd .vnum{font-size:42px;font-weight:300;line-height:1;direction:ltr;display:inline-flex;align-items:baseline;gap:5px;font-variant-numeric:tabular-nums;letter-spacing:-.02em}
.H .vcd .vnum small{font-size:15px;font-weight:500;letter-spacing:0;direction:rtl}
.H .vcd .vcap{font-size:12.5px;font-weight:700;color:var(--acc);white-space:nowrap}
.H .vcd .vfoot{font-size:12px;color:var(--ink2);white-space:nowrap}
.H .rk{position:absolute;width:22px;height:22px;border-radius:50%;transform:translate(-50%,-50%);background:var(--ink);color:var(--bg);font-size:12px;font-weight:700;display:flex;align-items:center;justify-content:center;box-shadow:0 0 0 2px var(--bg)}
.H .lay2{position:absolute;inset:0}
@keyframes svA{0%,40%{opacity:1}50%,90%{opacity:0}100%{opacity:1}}
@keyframes svB{0%,40%{opacity:0}50%,90%{opacity:1}100%{opacity:0}}
@keyframes svT{0%,38%{transform:translateX(var(--x1));height:var(--h1);background:var(--c1)}52%,88%{transform:translateX(var(--x2));height:var(--h2);background:var(--c2)}100%{transform:translateX(var(--x1));height:var(--h1);background:var(--c1)}}
.H .svA{animation:svA 6.4s ease-in-out infinite}.H .svB{animation:svB 6.4s ease-in-out infinite;opacity:0}
.H .tk2.mv i{animation:svT 6.4s cubic-bezier(.3,0,.2,1) infinite}
"""
G4.MB.CSS = G4.MB.CSS + VCSS


# ---------------------------------------------------------------- the bar
def micro():
    K = order('hard')[0]
    return '<span class="mst" aria-hidden="true">%s</span>' % ''.join('<i style="background: var(--%s)"></i>' % F[k]['color'] for k in K)


def dock2(rec=None, L='he', narrow=False):
    w, m = ('Runs', 'Menu') if L == 'en' else ('מסלולים', 'תפריט')
    return ('<div class="dock4 glass"><button class="mn">%s %s</button><button class="rn2" aria-label="%s, %d"><span class="w"><b>%s<span>%d</span></b>%s</span>'
            '%s</button>%s</div>' % (ic(UX.P_GRID, 19, 2), m, w, NR, w, NR, micro(), '' if narrow else '<span class="up">%s</span>' % ic(DK.P_UP, 17, 2.4), rec if rec is not None else DK.rc()))


# ---------------------------------------------------------------- the shelf
W_STRIP = 334


def xs(kind):
    """left of every tick, from the start side (right in Hebrew), with a wider gap between groups"""
    K, G = order(kind); x = 0; o = {}
    for i, k in enumerate(K):
        if i: x += 11 + (5 if G[i] != G[i - 1] else 0)
        o[k] = x
    scale = min(1, (W_STRIP - 8) / max(x, 1))
    return {k: W_STRIP - 8 - v * scale for k, v in o.items()}


def strip2(kind, to=None):
    X = xs(kind); o = ''
    if to:
        X2 = xs(to)
        for n, k in enumerate(order(kind)[0]):
            c1, h1 = tick(kind, k); c2, h2 = tick(to, k)
            o += ('<i class="%s" style="--x1: %.1fpx; --x2: %.1fpx; --h1: %.0fpx; --h2: %.0fpx; --c1: %s; --c2: %s; animation-delay: %dms"></i>'
                  % ('c' if k == CUR else '', X[k], X2[k], h1 + 4, h2 + 4, c1, c2, n * 8))
        return '<div class="tk2 mv" aria-hidden="true">%s</div>' % o
    for k in order(kind)[0]:
        c, h = tick(kind, k)
        o += '<i class="%s" style="transform: translateX(%.1fpx); height: %.0fpx; background: %s"></i>' % ('c' if k == CUR else '', X[k], h + 4, c)
    return '<div class="tk2" aria-hidden="true">%s<u style="left: %.1fpx"></u></div>' % (o, X[CUR] - 1)


def hero(kind, k):
    f = F[k]
    if kind == 'lift':
        return (str(PB.lift(f['to'][0])['dur']), 'דק׳', 'חזרה ב-<bdi>%s</bdi>' % f['to'][0]) if f['to'] else ('', '', 'ממשיכים במסלול אחר')
    if kind == 'hard': return '%d°' % ST[k]['steep'], '', 'הקטע התלול'
    if kind == 'long': return '%.1f' % (f['len'] / 1000), 'ק״מ', 'מקום %d מתוך %d באורך' % (order('long')[0].index(k) + 1, NR)
    if kind == 'sun': return '%d%%' % round(SUN[k] * 100), '', 'מהמסלול בשמש עכשיו'
    if kind == 'open': return ('פתוח', '', '<bdi>%s</bdi> עובד' % lift0(k)) if is_open(k) else ('סגור', '', '<bdi>%s</bdi> לא עובד' % lift0(k))
    return str(snow(k)), 'ס״מ', 'שלג חדש, לפי התחזית'


def card2(kind, k, left, on):
    f = F[k]; n, u, c = hero(kind, k)
    foot = '%s · %d מ׳ ירידה · %s' % (PB.km(f['len']), f['drop'], HE[f['color']]) if kind != 'long' else '%d מ׳ ירידה · הקטע התלול <bdi>%d°</bdi> · %s' % (f['drop'], ST[k]['steep'], HE[f['color']])
    return ('<div class="vcd glass" style="left: %dpx;%s"><span class="vshape" style="background: color-mix(in srgb, %s 12%%, transparent)">%s</span><span class="vmain">'
            '<span style="display: flex; align-items: center; gap: 8px">%s<b style="font-size: 17px; direction: ltr; white-space: nowrap; overflow: hidden; text-overflow: ellipsis">%s</b></span>'
            '<span><span class="vnum">%s%s</span><span class="vcap" style="display: block; margin-top: 3px">%s</span></span>'
            '<span class="vfoot">%s</span></span></div>'
            % (left, '' if on else ' opacity: .7;', HEX[f['color']], mini(k, 40, 110), sign(k), f['name'], ('<small>%s</small>' % u) if u else '', n, c, foot))


def cards(kind):
    K = order(kind)[0]; i = K.index(CUR)
    return ''.join(card2(kind, K[n], left, n == i) for n, left in ((i - 1, 330), (i, 79), (i + 1, -172)) if 0 <= n < len(K))


def chips2(kind, six=True):
    ch = CHIPS if six else CHIPS[:4]
    start = 2 if kind in ('sun', 'open', 'snow') else 0
    o = ''
    for n, (c, t) in enumerate(ch[start:start + 5]):
        st = ''
        if c == kind and c in ONC: st = ' style="background: %s; color: %s%s"' % (ONC[c][0], ONC[c][1], '; margin-inline-start: -78px' if n == 0 and start else '')
        elif n == 0 and start: st = ' style="margin-inline-start: -78px"'
        o += '<span class="ch2%s"%s>%s%s</span>' % (' on' if c == kind else '', st, ic(ICON[c], 16, 1.9), t)
    return '<div class="chipr" style="position: absolute; right: 12px; left: 12px; bottom: 202px; gap: 7px">%s</div>' % o


def label2(kind):
    return SL.label(kind)


def ranks(kind):
    if kind != 'long': return ''
    o = ''
    for n, k in enumerate(order('long')[0][:6]):
        R = P.run_path(VH, k); x, y = P.at(R, R['cum'][-1] * (.34 if k != CUR else .72))[:2]
        o += '<span class="rk" style="left: %.0fpx; top: %.0fpx">%d</span>' % (x, y + T0, n + 1)
    return o


def mount(kind):
    R = P.run_path(VH, CUR); x, y = P.at(R, R['cum'][-1] * .5)[:2]
    return both(SL.paint(kind), top=T0) + ranks(kind)


def shelf2(kind, to=None):
    pc = pic('hero', 'dawn', 'night', top=T0) if kind == 'sun' else pic('hero', top=T0)
    R = P.run_path(VH, CUR); x, y = P.at(R, R['cum'][-1] * .5)[:2]
    name = tag(x - 44, y + T0, CUR, HEX[F[CUR]['color']])
    if not to:
        b = pc + mount(kind) + name + top()
        b += '<div class="sp2 glass">%s%s</div>' % (label2(kind), strip2(kind))
        return b + chips2(kind) + cards(kind)
    A = lambda s: '<div class="lay2 svA">%s</div>' % s
    B = lambda s: '<div class="lay2 svB">%s</div>' % s
    b = pc + A(mount(kind)) + B(mount(to)) + name + top()
    b += ('<div class="sp2 glass"><div style="position: relative; height: 20px"><div class="svA" style="position: absolute; inset: 0">%s</div><div class="svB" style="position: absolute; inset: 0">%s</div></div>%s</div>'
          % (label2(kind), label2(to), strip2(kind, to)))
    b += A(chips2(kind)) + B(chips2(to)) + A(cards(kind)) + B(cards(to))
    return b


def root2(rec=None):
    return UX.mountain() + SL.column(layers=False) + dock2(rec, narrow=rec is not None)


def phones():
    return {
        'SV1.dc.html': ('גרסה 2: כפתור "מסלולים" עם הפס הקטן, ובלי כפתור השכבות', root2(), None),
        'SV2.dc.html': ('גרסה 2: בלילה, בזמן הקלטה', root2(DK.rc('live')), 'night'),
        'SV3.dc.html': ('גרסה 2: המדף, "לפי רכבל"', shelf2('lift'), None),
        'SV4.dc.html': ('גרסה 2, בתנועה: מ"לפי רכבל" ל"הארוכים" ובחזרה', shelf2('lift', 'long'), None),
        'SV5.dc.html': ('גרסה 2: "בשמש עכשיו". הצ׳יפ בצבע של מה שהוא צובע', shelf2('sun'), None),
        'SV6.dc.html': ('גרסה 2: "שלג חדש"', shelf2('snow'), None),
    }


HB = 900


def sv00():
    new = li('<b>הכפתור נושא את הרשימה בתוכו.</b> מתחת למילה "מסלולים" יש פס זעיר של 28 קווים בצבעי המסלולים. בנגיעה הקווים האלה עולים וגדלים, והופכים לפס המסלולים של המדף. רואים מאיפה הרשימה באה. (SV1 ← SV3)',
             '<b>המספר הגדול בכרטיס הוא התשובה של הצ׳יפ.</b> לפי רכבל: כמה דקות חזרה. לפי קושי: הקטע התלול. הארוכים: האורך והמקום. בשמש: כמה אחוזים בשמש. שלג: כמה ס״מ. כשמחליפים צ׳יפ המספר מתחלף. (SV3 עד SV6)',
             '<b>הצ׳יפ הדלוק לובש את הצבע של מה שהוא צובע.</b> "בשמש עכשיו" צהוב, "שלג חדש" סגול, "פתוחים" ירוק. הצ׳יפ עצמו הוא המקרא. לכל צ׳יפ יש סמל.',
             '<b>הסדר כתוב על ההר.</b> ב"הארוכים" ששת הארוכים מקבלים מספר 1 עד 6 על המסלול עצמו. (SV4)',
             '<b>התנועה מצוירת.</b> בלוח SV4 הפסים זזים למקום ולגובה החדשים, ההר נצבע מחדש, והכרטיסים מתחלפים, בלולאה.',
             '<b>הסרגל גדל מעט:</b> גובה 64, כפתורים 48, כדי שלכפתור "מסלולים" יהיו שתי שורות.')
    how = li('<b>פתיחה (350 מ״ש):</b> הסרגל שוקע; 28 הקווים של הכפתור עולים 190 נקודות ומתרחבים לפס (כל קו נשאר אותו מסלול); הכרטיסים עולים אחד אחרי השני, 40 מ״ש ביניהם; ההר עולה 120.',
             '<b>החלפת צ׳יפ (300 מ״ש):</b> כמו בגרסה 1, ועוד: המספר הגדול מתגלגל לערך החדש, והצ׳יפ מקבל את הצבע שלו.',
             '<b>גרירה על הפס:</b> המסלול הדלוק על ההר מתחלף מתחת לאצבע, עם רטט קל בכל מסלול.',
             '<b>סגירה:</b> הפוך. הקווים חוזרים לתוך הכפתור.',
             '<b>הפחתת תנועה:</b> הכל בהתמוססות של 150 מ״ש.')
    asks = '<p class="q"><span class="qn">1</span><span><b>גרסה 2 לבנייה?</b> הכיוון כבר נבחר: כפתור "מסלולים", והשכבות בתוך המדף בלי כפתור משלהן. (ההמלצה: כן)</span></p>'
    inner = ('<p class="sec">בוחרים מסלול · גרסה 2 · 27.11.2026 · הכיוון נבחר (נמסר דרך סשן המנהל) · העיצוב ממתין לפיני</p>'
             '<h1>אותו רעיון, עם יותר חוויה: הכפתור נפתח אל המדף, והצ׳יפ עונה במספר גדול ובצבע</h1>'
             '<div class="rowx" style="align-items: stretch"><div class="card" style="flex: 1.3"><h2>מה חדש בגרסה 2</h2>%s</div>'
             '<div style="flex: 1; display: flex; flex-direction: column; gap: 16px"><div class="card"><h2>התנועה</h2>%s</div><div class="card"><h2>לפיני</h2>%s</div></div></div>'
             '<p class="cap">הסדרים, האורכים, השיפועים והצל מחושבים מהנתונים של האתר. השלג הוא דוגמה. נמדד בכלי העיצוב, לא בטלפון. '
             'המסירה: <span dir="ltr">design/handoff/2026-11-27-redesign-shelf-layers/</span>, הסעיף "Version 2".</p>' % (new, how, asks))
    wb('SV00-V2.dc.html', 'בוחרים מסלול: גרסה 2', 1280, HB, inner)


FILES = ['SV00-V2.dc.html'] + ['SV%d.dc.html' % i for i in range(1, 7)]


def write_canvas(cj):
    j = json.load(open(cj, encoding='utf8'))
    for fn in FILES: j['boards'].pop(fn, None)
    j['order'] = [f for f in j['order'] if f not in FILES]
    T = phones()
    y0 = max(b['y'] + b['h'] for f, b in j['boards'].items() if b.get('page') == PAGE) + 520
    place = [('SV00-V2.dc.html', 0, y0, 1280, HB, 'גרסה 2: מה חדש')]
    for k, fn in enumerate(FILES[1:]):
        place.append((fn, 470 * k, y0 + HB + 140, 390, 844, T[fn][0]))
    for fn, x, y, w, h, t in place:
        code = fn.split('.dc.html')[0].split('-')[0]
        j['boards'][fn] = dict(x=x, y=y, w=w, h=h, title='%s (%s)' % (t, code), page=PAGE)
        j['order'].append(fn)
    j['notes']['tsv'] = dict(kind='title1', maxW=1600, page=PAGE, x=0, y=y0 - 300, text='גרסה 2: הכפתור והמדף, עם יותר חוויה')
    j['launch'] = dict(view='focused', file='SV4.dc.html') if False else dict(view='canvas', page=PAGE)
    open(cj, 'w', encoding='utf8').write(json.dumps(j, ensure_ascii=False, indent=1) + '\n')


def main():
    args = sys.argv[1:]
    pv = None
    if '--preview' in args:
        i = args.index('--preview'); pv = (args[i + 1], args[i + 2]); del args[i:i + 3]
    cj = args[0] if args else os.path.join(PROJ, 'canvas.json')
    for fn, v in phones().items():
        PB.phone(fn, v[0], v[1], night=v[2] == 'night', fixed=v[2])
    sv00()
    write_canvas(cj)
    if pv:
        keep = DK.FILES; DK.FILES = FILES; DK.preview(*pv); DK.FILES = keep
    print('ok', cj)


if __name__ == '__main__':
    main()
