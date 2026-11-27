#!/usr/bin/env python3
"""Two small items from Pini (27.11.2026), one canvas page:
P.21: on the ski pass, "איפה הקופה?" opened the painted village map. Instead two round buttons in the row: navigation
to the ticket office in Google Maps, and the SKIPASS app by Lemondo. No explanation text (SK1, SK2).
P.16: the "כל היום / ירידה אחת" switch stays live after recording starts and changes nothing visible. The mode is picked
before the start only, each with one short line; while recording it is a fixed label, no switch (RM1-RM3).
Boards SM00, SK1, SK2, RM1-RM3. Design only. Handoff: design/handoff/2026-11-27-redesign-small-items/.
Usage: python3 design/redesign/tools/sm_boards.py [canvas.json path] [--preview <dir> <assets dir>]"""
import os, sys, json
import ux_boards as UX
from ux_boards import ic, wb, li, PROJ, PB, G4
import season_boards as SE
import b10_boards  # noqa: F401  (the name pill CSS, .bname)
import dk_boards as DK
from peak_boards import pic, base, VH, P

PAGE = 'small2711'
PAGE_NAME = 'כרטיס הסקי: הקופה, ומצב ההקלטה (27.11)'
P_NAV = '<path d="M4 11.500 20 4l-7.500 16-1.800-6.700z"/>'
P_APP = '<rect x="6.500" y="2.500" width="11" height="19" rx="2.500"/><path d="M9.500 7.500h5M9.500 11h5M11 18h2"/>'
P_ALL = '<circle cx="12" cy="12" r="8.500"/><path d="M12 7.500V12l3 2"/>'
P_ONE = '<path d="M5 4c3 4 1 7 4 10s7 3 8 6"/><circle cx="17" cy="20" r="1.600" fill="currentColor"/>'

MCSS = """
.H .rb2{width:40px;height:40px;border-radius:50%;border:0;display:inline-flex;align-items:center;justify-content:center;background:color-mix(in srgb,var(--acc) 13%,transparent);color:var(--acc);flex:none;margin:2px 0}
.H .rbs{margin-inline-start:auto;display:flex;gap:8px}
.H .mode2{display:grid;grid-template-columns:1fr 1fr;gap:10px}
.H .mode2 button{border-radius:18px;border:1.5px solid var(--line);background:var(--tile);color:var(--ink);font:inherit;display:flex;flex-direction:column;align-items:flex-start;gap:6px;padding:12px 14px;text-align:start}
.H .mode2 button b{font-size:16px;font-weight:700}
.H .mode2 button span{font-size:12.5px;color:var(--ink2);line-height:1.3}
.H .mode2 button.on{border-color:var(--acc);box-shadow:0 0 0 1px var(--acc) inset;background:color-mix(in srgb,var(--acc) 8%,var(--tile))}
.H .mode2 button.on svg{color:var(--acc)}
.H .mlab{display:inline-flex;align-items:center;gap:5px;height:26px;border-radius:13px;padding:0 10px;font-size:12.5px;font-weight:700;background:var(--line);color:var(--ink);white-space:nowrap}
"""
G4.MB.CSS = G4.MB.CSS + MCSS

OLD_ROW = '<button class="e l">איפה הקופה? %s</button>' % ic(UX.P_CHV, 15, 2.2)


def buttons(names=False):
    return ('<span class="rbs"><button class="rb2" aria-label="ניווט לקופה">%s</button><button class="rb2" aria-label="אפליקציית SKIPASS">%s</button></span>'
            % (ic(P_NAV, 19, 2), ic(P_APP, 19, 1.9)))


def skipass(names=False):
    s = UX.skipass(4)
    assert OLD_ROW in s, 'skipass row changed'
    s = s.replace(OLD_ROW, buttons())
    if names:   # the long press shows each button's name (B10)
        s += '<span class="press" style="left: 37px; top: 688px; width: 52px; height: 52px; transform: translate(-50%, -50%)"></span>'
        s += '<span class="bname" style="left: 12px; top: 644px">אפליקציית SKIPASS</span>'
    return s


# ---------------------------------------------------------------- P.16
def start_sheet(one=False):
    o = ('<div class="sheet glass" style="gap: 14px"><div class="grab"></div>'
         '<p class="ttl" style="font-size: 23px; line-height: 1.25">הקלטת גלישה</p>'
         '<div class="mode2"><button class="%s" aria-pressed="%s">%s<b>כל היום</b><span>עד שעוצרים</span></button>'
         '<button class="%s" aria-pressed="%s">%s<b>ירידה אחת</b><span>נעצר לבד בתחתית</span></button></div>'
         '<button class="pri" style="flex: none">מתחילים</button><button class="lnk">לא עכשיו</button></div>'
         % ('' if one else 'on', 'false' if one else 'true', ic(P_ALL, 22, 1.9), 'on' if one else '', 'true' if one else 'false', ic(P_ONE, 22, 1.9)))
    return SE.mountain('morning', None, cabins=True) + '<div style="position: absolute; inset: 0; background: rgba(10,20,35,.18)"></div>' + o


def live(one=False, night=False):
    light = 'night' if night else 'morning'
    x, y, _, _ = P.pt(VH, *P.lift_ends(P.flift('Goodaura'))[0])
    lab = '<span class="mlab">%s%s</span>' % (ic(P_ONE if one else P_ALL, 14, 2), 'ירידה אחת' if one else 'כל היום')
    o = pic('hero', light, None) + base('hero', cabins=False) + SE.B4you(x, y)
    o += SE.recp('12:40' if not one else '0:48', lab)
    return o + SE.hold()


def phones():
    return {
        'SK1.dc.html': ('P.21 · כרטיס הסקי: ניווט לקופה ואפליקציית SKIPASS', skipass(), None),
        'SK2.dc.html': ('P.21 · בלילה, לחיצה ארוכה מראה את השם', skipass(True), 'night'),
        'RM1.dc.html': ('P.16 · לפני ההתחלה: בוחרים "כל היום"', start_sheet(), None),
        'RM2.dc.html': ('P.16 · לפני ההתחלה: "ירידה אחת"', start_sheet(True), None),
        'RM3.dc.html': ('P.16 · בזמן ההקלטה: תווית קבועה, בלי בורר', live(), None),
        'RM4.dc.html': ('P.16 · "ירידה אחת" בלילה, בזמן ההקלטה', live(True, True), 'night'),
    }


HB = 900


def sm00():
    p21 = li('<b>בשורה "קונים בקופה שבכפר" שני כפתורים עגולים</b> במקום "איפה הקופה?":',
             '<b>ניווט (חץ):</b> פותח את מפות Google בהליכה אל הקופה, <span dir="ltr">42.470828, 44.487555</span> ("Ski Pass Office", OSM, ודאות בינונית; לבדוק בינואר).',
             '<b>אפליקציה (טלפון):</b> פותח את SKIPASS של Lemondo אם מותקנת, אחרת את דף החנות (<span dir="ltr">com.lemondo.skipass</span>, <span dir="ltr">id6698866856</span>). בה טוענים כרטיס או מזמינים חדש.',
             '<b>בלי טקסט הסבר.</b> לחיצה ארוכה מראה את השם, כמו בשאר הכפתורים בלי מילים (B10). (SK2)',
             'מפת הכפר לא נפתחת מכאן יותר.')
    p16 = li('<b>היום:</b> הבורר נשאר לחיץ גם אחרי שההקלטה התחילה, ולחיצה עליו לא משנה כלום שרואים.',
             '<b>בוחרים לפני ההתחלה בלבד:</b> שני אריחים בגיליון, לכל אחד שם, סמל ושורה קצרה: "עד שעוצרים", "נעצר לבד בתחתית". "כל היום" מסומן מראש. (RM1, RM2)',
             '<b>בזמן ההקלטה:</b> המצב הוא תווית קבועה בפס העליון, ליד הזמן. אין בורר. (RM3, RM4)',
             'מי שרוצה מצב אחר: מסיים ומתחיל מחדש.',
             'זו גם תשובה לבחירה הפתוחה מגרסה 54 (LV): "ירידה אחת" נשארת בגיליון ההתחלה, לא עוברת לגיליון המסלול.')
    asks = ''.join('<p class="q"><span class="qn">%d</span><span>%s</span></p>' % (i + 1, t) for i, t in enumerate([
        '<b>P.21:</b> שני כפתורים עגולים, ניווט ב-Google Maps לקופה ואפליקציית SKIPASS, בלי טקסט? (כן)',
        '<b>P.21:</b> הנקודה של "Ski Pass Office" ולא "Gondola Tickets" ליד Goodaura? (כן, לפי השם; לבדוק בטיול)',
        '<b>P.16:</b> בוחרים מצב רק לפני ההתחלה, ובזמן ההקלטה תווית קבועה? (כן)']))
    inner = ('<p class="sec">P.21 ו-P.16 · 27.11.2026 · ממתין לפיני</p>'
             '<h1>הקופה בנגיעה אחת, ומצב ההקלטה נבחר פעם אחת</h1>'
             '<div class="rowx" style="align-items: stretch"><div class="card" style="flex: 1"><h2>P.21 · איפה הקופה</h2>%s</div>'
             '<div class="card" style="flex: 1"><h2>P.16 · כל היום או ירידה אחת</h2>%s</div></div>'
             '<div class="card" style="margin-top: 16px"><h2>לפיני</h2>%s</div>'
             '<p class="cap">טקסטים חדשים לתוכן אחרי האישור: "עד שעוצרים", "נעצר לבד בתחתית", "ניווט לקופה" (קורא מסך). "אפליקציית SKIPASS" קיים (<span dir="ltr">app.skipass_app</span>). '
             'נבדק בכלי העיצוב, לא בטלפון. המסירה: <span dir="ltr">design/handoff/2026-11-27-redesign-small-items/</span>.</p>' % (p21, p16, asks))
    wb('SM00-Small.dc.html', 'הקופה ומצב ההקלטה', 1280, HB, inner)


FILES = ['SM00-Small.dc.html', 'SK1.dc.html', 'SK2.dc.html', 'RM1.dc.html', 'RM2.dc.html', 'RM3.dc.html', 'RM4.dc.html']


def write_canvas(cj):
    j = json.load(open(cj, encoding='utf8'))
    for fn in FILES: j['boards'].pop(fn, None)
    j['order'] = [f for f in j['order'] if f not in FILES]
    T = phones()
    place = [('SM00-Small.dc.html', 0, 0, 1280, HB, 'P.21 ו-P.16: מה ולמה')]
    for k, fn in enumerate(FILES[1:]):
        place.append((fn, 470 * k, HB + 160, 390, 844, T[fn][0]))
    for fn, x, y, w, h, t in place:
        code = fn.split('.dc.html')[0].split('-')[0]
        j['boards'][fn] = dict(x=x, y=y, w=w, h=h, title='%s (%s)' % (t, code), page=PAGE)
        j['order'].append(fn)
    if not any(pg['id'] == PAGE for pg in j['pages']):
        j['pages'].insert(0, dict(id=PAGE, name=PAGE_NAME))
    j['notes']['t' + PAGE] = dict(kind='title1', maxW=2800, page=PAGE, x=0, y=-300, text='כרטיס הסקי: הקופה, ומצב ההקלטה')
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
    sm00()
    write_canvas(cj)
    if pv:
        keep = DK.FILES; DK.FILES = FILES; DK.preview(*pv); DK.FILES = keep
    print('ok', cj)


if __name__ == '__main__':
    main()
