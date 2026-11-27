#!/usr/bin/env python3
"""P.25 (Pini, 27.11.2026): the top pill on the mountain ("23 ימים לפתיחה · -8°") is dry text. A more beautiful,
experiential and informative top: in season the lifts open out of all, with a lift icon; before the season the wait;
the weather beside it. Boards TP00 (overview), TP1-TP6 (states on the mountain), TP7 (every state side by side).
Data: /api/status (lifts, live only from December; until then "אין מידע עדכני", never a guess) and /api/weather
(temp, gust, snow24, daily snow). Design only. Handoff: design/handoff/2026-11-27-redesign-top-pill/.
Usage: python3 design/redesign/tools/tp_boards.py [canvas.json path] [--preview <dir> <assets dir>]"""
import os, sys, json
import sw_boards as SW
from sw_boards import DK, UX, ic, wb, li, PROJ, PB, G4, SL
import gap3_boards as G3
from gap4_boards import P_WIND
from peak_boards import pic, base

PAGE = 'toppill'
PAGE_NAME = 'הפס העליון: רכבלים ומזג אוויר (27.11)'
P_SNOW = '<path d="M12 3v18M4.200 7.500l15.600 9M19.800 7.500l-15.600 9"/><path d="m9.500 4.500 2.500 2 2.500-2M9.500 19.500l2.500-2 2.500 2"/>'
P_CLOUD = '<path d="M7 18h10a4 4 0 0 0 .500-7.970A6 6 0 0 0 6.100 9.500 4.300 4.300 0 0 0 7 18z"/>'

TCSS = """
.H .tp{position:absolute;top:48px;right:12px;left:64px;height:52px;border-radius:26px;display:flex;align-items:stretch;box-sizing:border-box;padding:0 6px;z-index:3}
.H .tp .sg{display:flex;align-items:center;gap:9px;padding:0 8px;min-width:0;border:0;background:none;color:var(--ink);font:inherit;text-align:start}
.H .tp .sg.l{flex:1}
.H .tp .sep{width:1px;margin:12px 0;background:var(--line);flex:none}
.H .tp .t{display:flex;flex-direction:column;gap:1px;min-width:0}
.H .tp .t b{font-size:16.5px;font-weight:700;line-height:1.1;white-space:nowrap;font-variant-numeric:tabular-nums}
.H .tp .t b small{font-size:13px;font-weight:600;color:var(--ink2)}
.H .tp .t span{font-size:12px;color:var(--ink2);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;line-height:1.2}
.H .tp .t span.am{color:#A35A00;font-weight:600}
.H .night .tp .t span.am{color:#FFC266}
.H .tp .t span.ac{color:var(--acc);font-weight:600}
.H .tp .rg{position:relative;width:38px;height:38px;flex:none;display:flex;align-items:center;justify-content:center}
.H .tp .rg svg.r{position:absolute;inset:0}
.H .tp .rg b{font-size:14px;font-weight:700;font-variant-numeric:tabular-nums}
.H .tp .tpw{width:30px;height:30px;min-height:30px;max-height:30px;align-self:center;border-radius:50%;display:flex;align-items:center;justify-content:center;flex:none}
.H .tp .tpw.sun{background:rgba(255,201,58,.22);color:#B07800}
.H .tp .tpw.snow{background:rgba(91,140,230,.16);color:#2F6FD0}
.H .tp .tpw.wind{background:rgba(255,170,60,.2);color:#A35A00}
.H .tp .tpw.moon{background:rgba(150,160,220,.18);color:#5B63A8}
.H .night .tp .tpw.sun{color:#FFC93A}.H .night .tp .tpw.snow{color:#9CC0FF}.H .night .tp .tpw.wind{color:#FFC266}.H .night .tp .tpw.moon{color:#C8CCFF}
.H .tp .spark{position:absolute;inset:-3px;border-radius:50%;box-shadow:0 0 0 2px rgba(91,140,230,.35);animation:tpS 2.4s ease-out infinite}
@keyframes tpS{0%{transform:scale(.85);opacity:1}100%{transform:scale(1.35);opacity:0}}
.H .tplab{position:absolute;right:16px;left:16px;font-size:12.5px;font-weight:700;color:var(--ink);text-shadow:0 1px 0 rgba(255,255,255,.6)}
.H .night .tplab{text-shadow:none}
"""
G4.MB.CSS = G4.MB.CSS + TCSS

GOOD, AMBER, TRACK = 'var(--good)', '#F0A020', 'var(--line)'


def ring(open_=11, held=0, total=14, inner=None, col=None, frac=None):
    """a 38 ring: green for the open share, amber for lifts held by wind, the rest the track; a lift inside"""
    C = 2 * 3.14159 * 16
    a = (open_ / total) if frac is None else frac
    h = held / total if total else 0
    arcs = '<circle cx="19" cy="19" r="16" stroke="%s" stroke-width="3.2" fill="none"/>' % TRACK
    if a: arcs += '<circle cx="19" cy="19" r="16" stroke="%s" stroke-width="3.2" fill="none" stroke-linecap="round" stroke-dasharray="%.1f %.1f" transform="rotate(-90 19 19)"/>' % (col or GOOD, a * C - 1.5, C)
    if h: arcs += '<circle cx="19" cy="19" r="16" stroke="%s" stroke-width="3.2" fill="none" stroke-linecap="round" stroke-dasharray="0 %.1f %.1f %.1f" transform="rotate(-90 19 19)"/>' % (AMBER, a * C + 1.5, h * C - 3, C)
    mid = inner if inner is not None else ic(UX.P_GOND, 18, 1.9)
    return '<span class="rg"><svg class="r" viewBox="0 0 38 38" aria-hidden="true">%s</svg>%s</span>' % (arcs, mid)


def wx(kind, temp, line='', cls=''):
    P = {'sun': G3.P_SUN, 'snow': P_SNOW, 'wind': P_WIND, 'moon': UX.P_MOON, 'cloud': P_CLOUD}[kind]
    k = 'sun' if kind == 'cloud' else kind
    spark = '<span class="spark"></span>' if cls == 'spark' else ''
    return ('<button class="sg w" aria-label="מזג האוויר, %s"><span class="tpw %s" style="position: relative">%s%s</span><span class="t"><b dir="ltr">%s</b>%s</span></button>'
            % (temp.replace('−', 'מינוס '), k, ic(P, 18, 1.9), spark, temp, '<span>%s</span>' % line if line else ''))


def pill(lifts, weather, top=None):
    st = ' style="top: %dpx"' % top if top is not None else ''
    return '<div class="tp glass"%s>%s<i class="sep"></i>%s</div>' % (st, lifts, weather)


def avatar():
    return '<button class="xb glass" aria-label="אני: חשבון והגדרות" style="position: absolute; top: 52px; left: 12px; padding: 0">%s</button>' % G3.av('נ')


def lifts_seg(head, sub, rg, sub_cls='', label=''):
    return ('<button class="sg l" aria-label="%s">%s<span class="t"><b>%s</b><span class="%s">%s</span></span></button>'
            % (label or 'מצב הרכבלים', rg, head, sub_cls, sub))


# the states
def s_open():
    return pill(lifts_seg('11 <small>מתוך 14</small>', 'רכבלים פתוחים', ring(11), label='11 מתוך 14 רכבלים פתוחים'), wx('sun', '−7°', 'שמש'))


def s_wind():
    return pill(lifts_seg('9 <small>מתוך 14</small>', '2 עצרו ברוח', ring(9, 2), 'am'), wx('wind', '−9°', 'משבים 65'))


def s_fresh():
    return pill(lifts_seg('14 <small>מתוך 14</small>', 'כל הרכבלים פתוחים', ring(14)), wx('snow', '−11°', '18 ס״מ שלג טרי', 'spark'))


def s_closed():
    return pill(lifts_seg('סגורים', 'נפתחים מחר ב-09:00', ring(0)), wx('moon', '−14°', 'לילה בהיר'))


def s_nodata():
    return pill(lifts_seg('רכבלים', 'אין מידע עדכני', ring(0)), wx('cloud', '−6°', 'מעונן'))


def s_pre():
    inner = '<b>23</b>'
    return pill(lifts_seg('ימים לפתיחה', 'הרכבלים עולים ב-<bdi>20.12</bdi>', ring(inner=inner, col='var(--acc)', frac=.62), 'ac', '23 ימים לפתיחה, 20.12'),
                wx('snow', '−8°', 'שלג בדרך · 20 ס״מ'))


def screen(p, night=False):
    return pic('hero', 'morning', 'night') + base('hero', cabins=True) + avatar() + p + SL.column(layers=False) + SW.dock(SW.btn_a(night=night))


def sheet():
    """TP7: every state, one under the other, on a veiled mountain"""
    o = pic('hero', 'morning', 'night') + '<div style="position: absolute; inset: 0; background: var(--bg); opacity: .55"></div>'
    S = [('בעונה, יום רגיל', s_open), ('רוח: שני רכבלים עצרו', s_wind), ('בוקר אחרי שלג', s_fresh), ('ערב: הרכבלים סגורים', s_closed),
         ('אין נתון חי (עד דצמבר, או ישן)', s_nodata), ('לפני העונה', s_pre)]
    for n, (lab, f) in enumerate(S):
        y = 40 + n * 124
        o += '<div class="tplab" style="top: %dpx">%d · %s</div>' % (y, n + 1, lab)
        o += f().replace('<div class="tp glass">', '<div class="tp glass" style="top: %dpx; left: 12px">' % (y + 24))
    return o


def phones():
    return {
        'TP1.dc.html': ('הפס העליון · בעונה, יום רגיל', screen(s_open()), None),
        'TP2.dc.html': ('הפס העליון · רוח, שני רכבלים עצרו', screen(s_wind()), None),
        'TP3.dc.html': ('הפס העליון · בוקר אחרי שלג', screen(s_fresh()), None),
        'TP4.dc.html': ('הפס העליון · ערב, הרכבלים סגורים', screen(s_closed(), True), 'night'),
        'TP5.dc.html': ('הפס העליון · אין נתון חי', screen(s_nodata()), None),
        'TP6.dc.html': ('הפס העליון · לפני העונה', screen(s_pre()), None),
        'TP7.dc.html': ('הפס העליון · כל המצבים', sheet(), None),
    }


HB = 980


def tp00():
    what = li('<b>שני חלקים בפס אחד:</b> מימין הרכבלים, משמאל מזג האוויר. כל חלק נלחץ: הרכבלים פותחים את לוח הרכבלים, מזג האוויר את התחזית.',
              '<b>הרכבלים:</b> סמל רכבל בתוך טבעת. הטבעת מתמלאת בירוק לפי כמה פתוחים מתוך כולם; רכבל שעצר ברוח בכתום. בגדול "11 מתוך 14", מתחת "רכבלים פתוחים". הטבעת אחת, לא סימן לכל רכבל, ולכן עובדת גם באתר עם 40 רכבלים.',
              '<b>מזג האוויר:</b> סמל בעיגול צבעוני (שמש, שלג, רוח, לילה), הטמפרטורה בגדול, ומתחת שורה אחת שחשובה עכשיו: שלג טרי, משבים חזקים, או המצב.',
              '<b>שלג טרי</b> מקבל הבהוב עדין סביב הפתית, רק בבוקר שאחרי השלג. (TP3)',
              '<b>לפני העונה:</b> הטבעת היא ההמתנה (מתמלאת מההודעה של MTA עד הפתיחה), והמספר בתוכה. מזג האוויר אומר אם שלג בדרך השבוע. (TP6)',
              '<b>בלי נתון חי</b> (עד שהמנתח של MTA עובד בדצמבר, או נתון ישן מ-30 דק׳): "אין מידע עדכני", טבעת ריקה. לא מנחשים מהתחזית. (TP5)')
    data = li('<b>רכבלים:</b> <span dir="ltr">/api/status</span> (<span dir="ltr">lifts: {open, reason}</span>). חי רק מדצמבר; עד אז 404, והפס מציג "אין מידע עדכני".',
              '<b>"עצרו ברוח":</b> רק כש-<span dir="ltr">reason</span> אומר רוח בנתון החי. הערכת הסיכון של התחזית (<span dir="ltr">ridge.risk</span>) לא נכנסת לפס; היא נשארת בלוח הרכבלים עם "הערכה לפי התחזית".',
              '<b>מזג אוויר:</b> <span dir="ltr">/api/weather</span>: <span dir="ltr">temp</span> ו-<span dir="ltr">code</span> של השעה, <span dir="ltr">gust</span> (מעל 50 קמ״ש מוצג), <span dir="ltr">snow24</span> (5 ס״מ ומעלה = שלג טרי), ולפני העונה סכום <span dir="ltr">daily.snow</span> של שבעה ימים.',
              '<b>סדר העדיפות בשורה התחתונה של מזג האוויר:</b> שלג טרי, אחר כך משבים, אחר כך המצב במילה.',
              '<b>מקור לנתון החי בעונה</b> הוא החלטה של סשן השרת והארכיטקט (S-20); הלוחות לא משנים חוזה.')
    asks = ''.join('<p class="q"><span class="qn">%d</span><span>%s</span></p>' % (i + 1, t) for i, t in enumerate([
        '<b>פס אחד בשני חלקים, רכבלים ומזג אוויר, וכל חלק נלחץ?</b> (כן)',
        '<b>טבעת אחת שמתמלאת</b> במקום מספר יבש, ירוק לפתוח וכתום לעצר ברוח? (כן)',
        '<b>לפני העונה הפס מחליף את אריח הספירה (CT)</b>: אותה טבעת והמספר בתוכה, בלי אריח נפרד מתחת. (כן: פחות דברים על ההר; האריח אושר, ולכן שואלים)',
        '<b>בלי נתון חי "אין מידע עדכני"</b>, ולא הערכה מהתחזית בפס? (כן)']))
    inner = ('<p class="sec">P.25 · הפס העליון · 27.11.2026 · ממתין לפיני</p>'
             '<h1>במבט אחד: כמה רכבלים עובדים, ואיך ההר עכשיו</h1>'
             '<div class="rowx" style="align-items: stretch"><div class="card" style="flex: 1.25"><h2>מה בפס</h2>%s</div>'
             '<div style="flex: 1; display: flex; flex-direction: column; gap: 16px"><div class="card"><h2>הנתונים</h2>%s</div><div class="card"><h2>לפיני</h2>%s</div></div></div>'
             '<p class="cap">המספרים בלוחות הם דוגמה. נבדק בכלי העיצוב, לא בטלפון. המסירה: <span dir="ltr">design/handoff/2026-11-27-redesign-top-pill/</span>.</p>' % (what, data, asks))
    wb('TP00-TopPill.dc.html', 'הפס העליון', 1280, HB, inner)


FILES = ['TP00-TopPill.dc.html'] + ['TP%d.dc.html' % i for i in range(1, 8)]


def write_canvas(cj):
    j = json.load(open(cj, encoding='utf8'))
    for fn in FILES: j['boards'].pop(fn, None)
    j['order'] = [f for f in j['order'] if f not in FILES]
    T = phones()
    place = [('TP00-TopPill.dc.html', 0, 0, 1280, HB, 'P.25: הפס העליון, מה ולמה')]
    for k, fn in enumerate(FILES[1:]):
        place.append((fn, 470 * (k % 4), HB + 160 + (k // 4) * (844 + 200), 390, 844, T[fn][0]))
    for fn, x, y, w, h, t in place:
        code = fn.split('.dc.html')[0].split('-')[0]
        j['boards'][fn] = dict(x=x, y=y, w=w, h=h, title='%s (%s)' % (t, code), page=PAGE)
        j['order'].append(fn)
    if not any(pg['id'] == PAGE for pg in j['pages']):
        j['pages'].insert(0, dict(id=PAGE, name=PAGE_NAME))
    j['notes']['t' + PAGE] = dict(kind='title1', maxW=1800, page=PAGE, x=0, y=-300, text='הפס העליון: רכבלים ומזג אוויר')
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
    tp00()
    write_canvas(cj)
    if pv:
        keep = DK.FILES; DK.FILES = FILES; DK.preview(*pv); DK.FILES = keep
    print('ok', cj)


if __name__ == '__main__':
    main()
