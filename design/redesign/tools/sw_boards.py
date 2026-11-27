#!/usr/bin/env python3
"""Version 3 of the "מסלולים" button (27.11.2026, Pini): he loves the shelf of version 2 (chips, sorting, the card
re-order) and keeps it unchanged; he does not like the button. The micro strip of one mark per run is not understood
and does not scale past 28 runs. Three simple alternatives with no per-run marks, a recommendation, and how each
behaves at 28 and at 120 runs. Boards SW00 to SW6 on the page of dk_boards. Design only.
Handoff: design/handoff/2026-11-27-redesign-shelf-layers/ (section "Version 3: the button").
Usage: python3 design/redesign/tools/sw_boards.py [canvas.json path] [--preview <dir> <assets dir>]"""
import os, sys, json
import sv_boards as SV
from sv_boards import DK, UX, ic, wb, li, PROJ, PB, G4, F, SL, order

PAGE = DK.PAGE
NR = SV.NR
BYC = DK.BYC
P_UP = DK.P_UP

WCSS = """
.H .dock5{position:absolute;left:10px;right:10px;bottom:12px;height:64px;border-radius:32px;display:flex;align-items:center;gap:8px;padding:0 8px;box-sizing:border-box}
.H .dock5 .mn{height:48px;border-radius:24px;background:var(--ink);color:var(--bg);display:flex;align-items:center;gap:7px;padding:0 16px 0 14px;font-size:15.5px;font-weight:700;flex:none}
.H .dock5 .rb{flex:1;min-width:0;height:48px;border-radius:24px;border:1px solid var(--line);background:var(--tile);display:flex;align-items:center;gap:10px;padding:0 8px 0 6px;box-sizing:border-box;text-align:start;color:var(--ink)}
.H [dir=rtl] .dock5 .rb{padding:0 6px 0 8px}
.H .rb .gl{width:36px;height:36px;border-radius:50%;flex:none;overflow:hidden;box-shadow:inset 0 0 0 1px rgba(10,25,45,.12)}
.H .rb .gl svg{display:block}
.H .rb .w{display:flex;flex-direction:column;gap:3px;min-width:0;flex:1}
.H .rb .w b{font-size:16px;font-weight:700;line-height:1.05;display:flex;align-items:baseline;gap:7px;white-space:nowrap}
.H .rb .w b span{font-size:13px;font-weight:700;color:var(--ink2);font-variant-numeric:tabular-nums}
.H .rb .w small{font-size:12.5px;font-weight:500;color:var(--ink2);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;line-height:1.1}
.H .rb .w small bdi{font-weight:700;color:var(--ink)}
.H .rb .up{width:32px;height:32px;border-radius:50%;background:var(--line);display:flex;align-items:center;justify-content:center;flex:none;color:var(--ink)}
.H .rb .cc{display:flex;gap:9px;font-size:12.5px;font-weight:700;color:var(--ink2);font-variant-numeric:tabular-nums;line-height:1}
.H .rb .cc span{display:inline-flex;align-items:center;gap:4px}
.H .rb .cc i{width:9px;height:9px;border-radius:50%;flex:none}
.H .rb .cc i.dm{border-radius:2px;transform:rotate(45deg);width:8px;height:8px}
.H .rb .cn{min-width:28px;height:24px;padding:0 8px;box-sizing:border-box;border-radius:12px;background:var(--line);font-size:13px;font-weight:700;display:inline-flex;align-items:center;justify-content:center;font-variant-numeric:tabular-nums;flex:none}
.H .note{position:absolute;left:12px;right:12px;top:104px;border-radius:16px;padding:9px 13px;font-size:12.5px;line-height:1.4;font-weight:500}
@keyframes swR{0%,38%{opacity:1}46%,92%{opacity:0}100%{opacity:1}}
@keyframes swS{0%,44%{opacity:0}54%,90%{opacity:1}98%,100%{opacity:0}}
@keyframes swG{0%,38%{opacity:0;left:92px;right:66px;bottom:20px;height:48px;border-radius:24px}
 40%{opacity:1;left:92px;right:66px;bottom:20px;height:48px;border-radius:24px}
 50%{opacity:1;left:12px;right:12px;bottom:246px;height:74px;border-radius:22px}
 54%,100%{opacity:0;left:12px;right:12px;bottom:246px;height:74px;border-radius:22px}}
@keyframes swL{0%,40%{transform:scale(1);opacity:1}50%{transform:translate(0,-6px) scale(1.6);opacity:0}100%{opacity:0}}
.H .swR{animation:swR 7s ease-in-out infinite}
.H .swS{animation:swS 7s ease-in-out infinite;opacity:0}
.H .swG{position:absolute;animation:swG 7s cubic-bezier(.3,0,.2,1) infinite;background:var(--tile);border:1px solid var(--line);opacity:0;z-index:5}
.H .lay3{position:absolute;inset:0}
"""
G4.MB.CSS = G4.MB.CSS + WCSS


# ---------------------------------------------------------------- the glyph (alternative A)
def glyph(night=False, s=36):
    """a round window onto a drawn mountain with three runs; the same at any number of runs"""
    sky = ('#22345A', '#3A5384') if night else ('#5E9BDB', '#B9D7F4')
    snow, shade = ('#DCE6F2', '#A9B9D0') if night else ('#FFFFFF', '#AFC4DC')
    g = 'gl%s' % ('n' if night else 'd')
    return ('<span class="gl" aria-hidden="true"><svg width="%d" height="%d" viewBox="0 0 36 36">'
            '<defs><linearGradient id="%s" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="%s"/><stop offset="1" stop-color="%s"/></linearGradient></defs>'
            '<rect width="36" height="36" fill="url(#%s)"/>'
            '<path d="M-1 37 9 21 17 8.500 21.500 14 26.500 10.500 37 25V37z" fill="%s"/>'
            '<path d="M17 8.500 9 21-1 37h13l2.500-15z" fill="%s" opacity=".8"/>'
            '<g fill="none" stroke-width="1.8" stroke-linecap="round">'
            '<path d="M14.500 15c-.500 4-3 6-4.500 9.500S7.500 30 6.500 34" stroke="var(--blue)"/>'
            '<path d="M18 13.500c1.500 4-.500 7 .500 10.500s1.500 6 1 10" stroke="var(--red)"/>'
            '<path d="M26 14c.500 4 2.500 5.500 2.500 9s1.500 6.500 2.500 10" stroke="var(--black)"/></g></svg></span>'
            % (s, s, g, sky[0], sky[1], g, snow, shade))


def word(L):
    return ('Runs', 'Menu') if L == 'en' else ('מסלולים', 'תפריט')


def bar(btn, rec=None):
    return '<div class="dock5 glass"><button class="mn">%s %s</button>%s%s</div>' % (ic(UX.P_GRID, 19, 2), '{menu}', btn, rec if rec is not None else DK.rc())


def btn_a(n=NR, L='he', night=False, narrow=False):
    w, m = word(L)
    up = '' if narrow else '<span class="up">%s</span>' % ic(P_UP, 17, 2.4)
    return ('<button class="rb" aria-label="%s, %d">%s<span class="w"><b>%s<span>%d</span></b></span>%s</button>'
            % (w, n, glyph(night), w, n, up)), m


def btn_b(by=None, L='he', narrow=False):
    by = by or BYC
    w, m = word(L)
    cc = ''.join('<span><i class="%s" style="background: var(--%s)"></i>%d</span>' % ('dm' if c == 'black' else '', c, by[c]) for c in ('green', 'blue', 'red', 'black'))
    up = '' if narrow else '<span class="up">%s</span>' % ic(P_UP, 17, 2.4)
    return ('<button class="rb" style="padding-inline-start: 16px" aria-label="%s, %d"><span class="w"><b>%s</b><span class="cc">%s</span></span>%s</button>'
            % (w, sum(by.values()), w, cc, up)), m


def btn_c(line, n=NR, L='he', narrow=False):
    w, m = word(L)
    up = '' if narrow else '<span class="up">%s</span>' % ic(P_UP, 17, 2.4)
    return ('<button class="rb" style="padding-inline-start: 16px" aria-label="%s, %d"><span class="w"><b>%s<span>%d</span></b><small>%s</small></span>%s</button>'
            % (w, n, w, n, line, up)), m


def dock(pair, rec=None):
    b, m = pair
    return bar(b, rec).replace('{menu}', m)


def longest():
    k = order('long')[0][0]
    return 'הכי ארוך: <bdi>%s</bdi>' % F[k]['name']


def root(d, note=''):
    return UX.mountain() + SL.column(layers=False) + (('<div class="note glass">%s</div>' % note) if note else '') + d


def motion():
    """SW6: the button opens into the shelf: the button's surface grows into the strip panel (one shared container),
    the glyph fades into it, then the ticks, chips and cards arrive; and back"""
    A = '<div class="lay3 swR">%s</div>' % root(dock(btn_a()))
    B = '<div class="lay3 swS">%s</div>' % SV.shelf2('lift')
    return A + B + '<div class="swG"></div>'


def phones():
    by120 = {'green': 31, 'blue': 47, 'red': 32, 'black': 10}
    return {
        'SW1.dc.html': ('גרסה 3, א: סמל ההר, המילה והמספר (מומלץ)', root(dock(btn_a())), None, 'he'),
        'SW2.dc.html': ('גרסה 3, א: בלילה, בזמן הקלטה', root(dock(btn_a(night=True, narrow=True), DK.rc('live'))), 'night', 'he'),
        'SW3.dc.html': ('גרסה 3, א: אתר עם 120 מסלולים, באנגלית', root(dock(btn_a(120, 'en')), 'דוגמה: אותו כפתור באתר גדול. רק המספר משתנה.'), None, 'he'),
        'SW4.dc.html': ('גרסה 3, ב: ארבעה צבעים, ארבעה מספרים', root(dock(btn_b())), None, 'he'),
        'SW5.dc.html': ('גרסה 3, ג: שורה חיה מהצ׳יפ האחרון ("הארוכים")', root(dock(btn_c(longest()))), None, 'he'),
        'SW6.dc.html': ('גרסה 3, א, בתנועה: הכפתור נפתח אל המדף של גרסה 2', motion(), None, 'he'),
    }


HB = 1000


def sw00():
    alts = ('<div class="rowx" style="align-items: stretch">'
            '<div class="card" style="flex: 1"><span class="tg ok">מומלץ</span><h2 style="margin-top: 8px">א · סמל ההר</h2>%s</div>'
            '<div class="card" style="flex: 1"><h2>ב · ארבעה צבעים, ארבעה מספרים</h2>%s</div>'
            '<div class="card" style="flex: 1"><h2>ג · שורה חיה</h2>%s</div></div>') % (
        li('עיגול קטן עם הר מצויר ושלושה מסלולים בצבעים. אחריו "מסלולים" והמספר, וחץ למעלה.',
           '<b>מובן מיד:</b> הר, מסלולים, כמה יש, נפתח למעלה. אין מה לפענח.',
           '<b>מעורר תיאבון:</b> הסמל הוא חלון קטן להר, ביום שמיים בהירים ובלילה כחול כהה.',
           '<b>בפתיחה:</b> הכפתור עצמו גדל והופך לפאנל של המדף, והסמל נמס לתוכו. (SW6)',
           'אין טקסט חדש: "מסלולים" והמספר כבר אושרו.'),
        li('"מסלולים" ומתחתיו ארבעה מספרים: כמה ירוקים, כחולים, אדומים ושחורים.',
           'נותן מידע אמיתי במבט, ושפת הצבעים מוכרת לכל גולש.',
           '<b>חסרון:</b> ארבעה מספרים צמודים זה עדיין עמוס, ואומר מעט על מה שנפתח.',
           'אין טקסט חדש.'),
        li('"מסלולים" והמספר, ומתחת שורה אחת שעונה לפי הצ׳יפ האחרון במדף: "הכי ארוך: Tatra 1", "בשמש עכשיו: 9", "פתוחים: 21".',
           'הכי חי מהשלוש: הכפתור מזמין עם תשובה, לא עם רשימה.',
           '<b>חסרון:</b> שורה שמשתנה בסרגל קבוע מסיחה, ונחתכת באנגלית, ברוסית ובטקסט גדול.',
           'צריך מחרוזת לכל צ׳יפ (שש, בארבע שפות).'))
    scale = ('<table class="tb"><tr><th></th><th>28 מסלולים (גודאורי)</th><th>120 מסלולים (אתר גדול)</th><th>צר: הקלטה, אנגלית, טקסט גדול</th></tr>'
             '<tr><th>א</th><td>סמל, "מסלולים 28", חץ</td><td>אותו דבר, "120". הסמל לא תלוי במספר.</td><td>החץ יורד ראשון, אחריו המספר. הסמל והמילה נשארים.</td></tr>'
             '<tr><th>ב</th><td>%s</td>' % ' · '.join(str(BYC[c]) for c in ('green', 'blue', 'red', 'black')) + '<td>31 · 47 · 32 · 10: ספרות כפולות, עדיין נכנס.</td><td>המספרים יורדים, נשארת המילה בלבד.</td></tr>'
             '<tr><th>ג</th><td>שורה אחת</td><td>אותה שורה.</td><td>השורה נחתכת או יורדת; הכי שביר.</td></tr></table>')
    keep = li('<b>המדף של גרסה 2 לא משתנה:</b> הצ׳יפים, המיון, הפס עם הסימנים, הכרטיסים שמתחלפים והמספר הגדול. (SV3 עד SV6)',
              '<b>רק הכפתור בסרגל מתחלף.</b> הפס הזעיר של 28 הסימנים יוצא מהכפתור; הסימנים לכל מסלול נשארים רק בתוך המדף, שם יש להם מקום ומשמעות.',
              'הסרגל נשאר בגובה 64, והכפתור 48.')
    asks = ('<p class="q"><span class="qn">1</span><span><b>איזה כפתור?</b> א, ב או ג. (ההמלצה: א, סמל ההר)</span></p>'
            '<p class="q"><span class="qn">2</span><span><b>הכפתור נפתח אל המדף כך שהמשטח שלו גדל לפאנל?</b> (ההמלצה: כן)</span></p>')
    inner = ('<p class="sec">בוחרים מסלול · גרסה 3: הכפתור · 27.11.2026 · ממתין לפיני</p>'
             '<h1>כפתור פשוט וברור: הר קטן, המילה והמספר. המדף נשאר כמו שאהבת</h1>'
             '%s'
             '<div class="rowx" style="align-items: stretch; margin-top: 16px"><div class="card" style="flex: 1.4"><h2>28 מול 120 מסלולים</h2>%s</div>'
             '<div style="flex: 1; display: flex; flex-direction: column; gap: 16px"><div class="card"><h2>מה נשאר</h2>%s</div><div class="card"><h2>לפיני</h2>%s</div></div></div>'
             '<p class="cap">המספרים לפי הצבע מחושבים מהנתונים של האתר; 120 הוא דוגמה. נמדד בכלי העיצוב, לא בטלפון. '
             'המסירה: <span dir="ltr">design/handoff/2026-11-27-redesign-shelf-layers/</span>, הסעיף "Version 3: the button".</p>' % (alts, scale, keep, asks))
    wb('SW00-V3.dc.html', 'בוחרים מסלול: גרסה 3, הכפתור', 1280, HB, inner)


FILES = ['SW00-V3.dc.html'] + ['SW%d.dc.html' % i for i in range(1, 7)]
TCSS = '<style>.HW .tb{width:100%;border-collapse:collapse;font-size:14.5px;line-height:1.4}.HW .tb th,.HW .tb td{border-bottom:1px solid #DCE3EC;padding:8px 10px;text-align:start;vertical-align:top}.HW .tb tr:first-child th{font-size:13px;color:#3C4753}</style>'


def write_canvas(cj):
    j = json.load(open(cj, encoding='utf8'))
    for fn in FILES: j['boards'].pop(fn, None)
    j['order'] = [f for f in j['order'] if f not in FILES]
    T = phones()
    y0 = max(b['y'] + b['h'] for f, b in j['boards'].items() if b.get('page') == PAGE) + 520
    place = [('SW00-V3.dc.html', 0, y0, 1280, HB, 'גרסה 3: הכפתור')]
    for k, fn in enumerate(FILES[1:]):
        place.append((fn, 470 * k, y0 + HB + 140, 390, 844, T[fn][0]))
    for fn, x, y, w, h, t in place:
        code = fn.split('.dc.html')[0].split('-')[0]
        j['boards'][fn] = dict(x=x, y=y, w=w, h=h, title='%s (%s)' % (t, code), page=PAGE)
        j['order'].append(fn)
    j['notes']['tsw'] = dict(kind='title1', maxW=1600, page=PAGE, x=0, y=y0 - 300, text='גרסה 3: כפתור "מסלולים" פשוט; המדף של גרסה 2 נשאר')
    j['launch'] = dict(view='canvas', page=PAGE)
    open(cj, 'w', encoding='utf8').write(json.dumps(j, ensure_ascii=False, indent=1) + '\n')


def main():
    args = sys.argv[1:]
    pv = None
    if '--preview' in args:
        i = args.index('--preview'); pv = (args[i + 1], args[i + 2]); del args[i:i + 3]
    cj = args[0] if args else os.path.join(PROJ, 'canvas.json')
    for fn, v in phones().items():
        PB.phone(fn, v[0], v[1], night=v[2] == 'night', fixed=v[2], lang=v[3])
    sw00()
    p = os.path.join(PROJ, 'SW00-V3.dc.html'); s = open(p, encoding='utf8').read()
    if TCSS not in s:
        s = s.replace('</helmet>', TCSS + '</helmet>', 1) if '</helmet>' in s else TCSS + s
        open(p, 'w', encoding='utf8').write(s)
    write_canvas(cj)
    if pv:
        keep = DK.FILES; DK.FILES = FILES; DK.preview(*pv); DK.FILES = keep
    print('ok', cj)


if __name__ == '__main__':
    main()
