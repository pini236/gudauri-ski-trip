#!/usr/bin/env python3
"""P.17 (Pini, 27.11.2026): in picture mode the Kobi run is only a foreshortened line at the top of the front picture, and
a tap on it only selects it; nothing turns. Pini wants a way to the other side of the ridge, the Kobi picture in high
quality, and a clear way back (his ideas: a back button near the picture/live switch, or a tap on the compass).
Here: a "צד Kobi" tag on the ridge (and the Kobi line itself) turns to the side with the approved slide (Z08); on the side
the compass appears at the top of the column, its needle turned, with "צד Kobi" beside it for 3.5 s; a tap on it turns
back. The same rule as the live mountain (S08: the compass appears only after turning, a tap returns). No new strings.
Quality: the side set at 3 px per point like the front (measured), which needs the architect (ceiling 350 KB today).
Boards: KB00 (what and why, quality), KB1 (front: the way in), KB2 (in motion), KB3 (the side, day, arrived),
KB4 (the side, night, the label folded). Design only. Handoff: design/handoff/2026-11-27-redesign-kobi-side/.
Usage: python3 design/redesign/tools/kb_boards.py [canvas.json path] [--preview <dir> <assets dir>]"""
import os, sys, json
import hn_boards as HN
from hn_boards import screen
from v56_boards import ic, wb, li, PROJ, top, P_PIC, P_CUBE, P_LAY, P_LOC
from v56b_boards import dock_a, rc
import peak_boards as PB
from peak_boards import VH, P
import dk_boards as DK

PAGE = 'kobiside'
PAGE_NAME = 'צד Kobi בתמונה: לעבור ולחזור (27.11)'

# the side pictures: today's set (1.5 px per point) and the proposed one (3 px per point, like the front); canvas assets
PB.IMG.update({'kobi-morning': '32e9f8adb0cffb3992573797c40e971e', 'kobi-night': 'e9939a4f7211c57bb1e8fa06dd9685ef',
               'kobi-3m': 'd7751c4dca0633910278a0b724408345', 'kobi-3n': 'e0156626f6effd3d993c764f0b063d9e'})
PB.VIEW['kobi'] = VK = P.load('kobi')
U = lambda k: '/_blob/' + PB.IMG[k]

P_CMP = '<circle cx="12" cy="12" r="8.500"/><path d="m12 6 2.300 6H9.700z" fill="currentColor"/><path d="M9.700 12 12 18l2.300-6"/>'
P_OVER = '<path d="M4 17c2.500-6 5-9 8-9s5.500 3 8 9"/><path d="m9 6 3-3 3 3"/>'   # over the ridge

# measured sizes of the side set (kobi_export at out=3, webp, method 6; scratch render 27.11.2026)
SIZES = [('היום', '1.5', '74', '327', '33 עד 68'), ('חלופה ב', '2', '80', '626', '63 עד 126'),
         ('ההמלצה (א)', '3', '80', '1,069', '108 עד 215'), ('החזית, לשם השוואה', '3', '', 'נארזת', '127 עד 218')]

KCSS = """
.H .ktag{position:absolute;height:36px;border-radius:18px;border:0;display:inline-flex;align-items:center;gap:6px;padding:0 12px 0 10px;font:inherit;font-size:14px;font-weight:700;color:var(--ink);white-space:nowrap}
.H .ktag::before{content:'';position:absolute;inset:-4px}
.H .kc{position:absolute;left:12px;top:510px;height:44px;border-radius:22px;border:0;display:flex;align-items:center;padding:0;font:inherit;color:var(--ink);overflow:hidden;width:44px;box-sizing:border-box}
.H .kc.open{width:118px}
.H .kc .ki{width:44px;height:44px;flex:none;display:flex;align-items:center;justify-content:center}
.H .kc .ki svg{transform:rotate(180deg)}
.H .kc .kt{font-size:14px;font-weight:700;white-space:nowrap;padding-inline-end:14px}
.H .kq{position:absolute;inset:0}
.H .kq img{position:absolute;max-width:none}
@keyframes kbF{0%,18%{opacity:1;transform:none}23.6%,75%{opacity:0;transform:translateX(-36px)}80.6%,100%{opacity:1;transform:none}}
@keyframes kbK{0%,18%{opacity:0;transform:translateX(36px)}23.6%,75%{opacity:1;transform:none}80.6%,100%{opacity:0;transform:translateX(36px)}}
@keyframes kbC{0%,18%{opacity:0;width:44px}23.6%{opacity:1;width:118px}60%{opacity:1;width:118px}63%{opacity:1;width:44px}75%{opacity:1;width:44px}80.6%,100%{opacity:0;width:44px}}
@keyframes kbN{0%,18%{transform:rotate(0)}23.6%,75%{transform:rotate(180deg)}80.6%,100%{transform:rotate(0)}}
@keyframes kbT{0%,12%{opacity:0;transform:scale(.6)}15%{opacity:1;transform:scale(1)}18%,100%{opacity:0;transform:scale(1.25)}}
@keyframes kbT2{0%,69%{opacity:0;transform:scale(.6)}72%{opacity:1;transform:scale(1)}75%,100%{opacity:0;transform:scale(1.25)}}
.H .kbF{animation:kbF 9s ease-in-out infinite}
.H .kbK{animation:kbK 9s ease-in-out infinite;opacity:0}
.H .kc.kbC{animation:kbC 9s ease-in-out infinite;opacity:0}
.H .kc.kbC .ki svg{animation:kbN 9s ease-in-out infinite;transform:none}
.H .tap{position:absolute;width:44px;height:44px;margin:-22px 0 0 -22px;border-radius:50%;background:rgba(255,255,255,.6);box-shadow:0 0 0 2px rgba(10,25,45,.25);z-index:6;opacity:0;pointer-events:none}
.H .hmlab{position:absolute;left:12px;z-index:7;font-size:12px;font-weight:700;border-radius:12px;padding:4px 10px}
"""
HN.G4.MB.CSS = HN.G4.MB.CSS + KCSS


def col():
    """the column of the picture: the switch, layers, "where am I" (unchanged)"""
    return ('<div class="mc"><div class="msw glass" role="radiogroup" aria-label="תצוגת ההר"><span class="on" role="radio" aria-label="תמונה">%s</span>'
            '<span role="radio" aria-label="חי">%s</span></div><button class="glass" aria-label="שכבות">%s</button><button class="glass" aria-label="איפה אני">%s</button></div>'
            % (ic(P_PIC, 19), ic(P_CUBE, 19), ic(P_LAY, 20), ic(P_LOC, 20, 1.8, 'none')))


def kc(open_=True, anim=False):
    """the compass on the Kobi side: above the switch, the needle turned (north is behind you); "צד Kobi" beside it for
    3.5 s after arriving. A tap: back to the front. Screen reader: map.kobi_side + map.compass"""
    return ('<button class="kc glass%s%s" aria-label="צד Kobi. סיבוב חזרה למבט מדרום"><span class="ki">%s</span><span class="kt">צד <bdi>Kobi</bdi></span></button>'
            % (' open' if open_ and not anim else '', ' kbC' if anim else '', ic(P_CMP, 22, 1.6)))


def kobi_xy():
    R = P.run_path(VH, 'Kobi'); x, y, *_ = P.at(R, R['cum'][-1] * 0.5)
    return x, y


def ktag():
    """on the front: a tag where Kobi shows over the ridge; a tap (or a tap on the line) turns to the side"""
    x, y = kobi_xy()
    return ('<button class="ktag glass" style="left: %.0fpx; top: %.0fpx" aria-label="פתיחת צד Kobi">%s<span>צד <bdi>Kobi</bdi></span></button>'
            % (x - 52, y - 58, ic(P_OVER, 18, 2)))


def side(light='3m', night=False, sel=True):
    mode = 'night' if night else 'day'
    o = PB.pic('kobi', light, None) + PB.lay(P.details(VK, mode, 'night' if night else 'morning'))
    o += PB.lay(P.runs_svg(VK, mode)) + PB.lay(P.lifts_svg(VK, mode, 'night' if night else 'morning', cabins=not night))
    if sel:
        o += PB.lay(PB.svg(VK, PB.glow_run(VK, 'Kobi', mode)))
        R = P.run_path(VK, 'Kobi'); x, y, *_ = P.at(R, R['cum'][-1] * 0.45)
        o += PB.tag(x - 74, y - 12, '<span dir="ltr">Kobi</span>', 'var(--blue)')
    return o


def front():
    return PB.pic('hero', 'morning', None) + PB.base('hero', cabins=True)


def chrome(k=''):
    return top() + col() + k + dock_a(rc())


def kb1():
    x, y = kobi_xy()
    o = front() + chrome() + ktag()
    return o


def kb3():
    return side('3m') + chrome(kc(True))


def kb4():
    return side('3n', night=True) + chrome(kc(False))


def kb2():
    x, y = kobi_xy()
    o = '<div class="kq kbF">%s%s</div>' % (front(), ktag())
    o += '<div class="kq kbK">%s</div>' % side('3m')
    o += chrome(kc(anim=True))
    o += '<span class="tap" style="left: %.0fpx; top: %.0fpx; animation: kbT 9s linear infinite"></span>' % (x, y - 40)
    o += '<span class="tap" style="left: 34px; top: 532px; animation: kbT2 9s linear infinite"></span>'
    o += '<span class="hmlab glass" style="top: 104px">450 מ״ש לכל כיוון; "צד Kobi" נסגר אחרי 3.5 שנ׳</span>'
    return o


def crop(key, x0, y0, w, h, s=2):
    """a window on a side picture, in points, s times larger"""
    return ('<div class="H" style="position: relative; width: %dpx; height: %dpx; overflow: hidden; border-radius: 12px; border: 1px solid #D9DEE4; flex: none">'
            '<img alt="" src="%s" style="position: absolute; left: %dpx; top: %dpx; width: %dpx; height: %dpx; max-width: none"></div>'
            % (w * s, h * s, U(key), -x0 * s, -y0 * s, 390 * s, 844 * s))


HB0 = 1720


def kb00():
    flow = li('<b>הכניסה:</b> תגית "צד <bdi>Kobi</bdi>" עם חץ מעל הרכס, בדיוק איפה ש-<bdi>Kobi</bdi> מציץ בראש התמונה. נגיעה בתגית, או בקו של <bdi>Kobi</bdi> עצמו, מסובבת לצד השני. גם בחירה של <bdi>Kobi</bdi> מהמדף, מ"לאן יורדים?" או מקישור. (KB1)',
              '<b>המעבר:</b> ההצלבה עם ההזזה מ-Z08, 450 מ״ש: החזית זזה 36 נקודות שמאלה ודוהה, הצד של <bdi>Kobi</bdi> נכנס מימין. בזמן הזה המצפן מופיע בראש העמודה והמחט שלו מסתובבת 180 מעלות. זה מה שאומר "הסתובבת". (KB2)',
              '<b>בצד:</b> <bdi>Kobi</bdi> נבחר ומואר, הגיליון שלו עולה כרגיל. הסרגל, הפס העליון והעמודה נשארים במקום; רק התמונה מתחלפת. (KB3)',
              '<b>החזרה: המצפן.</b> מופיע מעל המתג תמונה/חי, רק כשמסתכלים מהצד השני, והמחט מצביעה למטה (הצפון מאחוריך). בהגעה כתוב לידו "צד <bdi>Kobi</bdi>" ל-3.5 שניות, ואז הוא מתקפל לעיגול. נגיעה: חזרה לחזית באותה תנועה הפוכה, והמצפן נעלם. (KB3, KB4)',
              '<b>למה המצפן ולא כפתור "חזרה":</b> זה כבר הכלל בהר החי (S08, אושר): המצפן מופיע רק אחרי שסובבו, ונגיעה מחזירה למבט הפתיחה. אותו כלל בתמונה, אותו מקום, אותה תנועה. כפתור "חזרה" היה כפתור חמישי בעמודה, ונראה כמו יציאה מהמסך.',
              '<b>סגירת הגיליון לא מחזירה.</b> נשארים בצד, כמו בהר החי (Z08). לחיצה ארוכה על המצפן מראה את השם, כמו בשאר הכפתורים בלי מילים (B10).',
              '<b>הפחתת תנועה:</b> החלפה מיידית, המחט כבר מסובבת. קורא מסך: "צד <bdi>Kobi</bdi>. סיבוב חזרה למבט מדרום".',
              '<b>אין טקסט חדש:</b> <span dir="ltr">map.kobi_side</span>, <span dir="ltr">map.kobi_side_aria</span> ו-<span dir="ltr">map.compass</span> כבר בקובץ השפות.')
    rows = ''.join('<tr><td><b>%s</b></td><td dir="ltr" style="text-align: right">%s</td><td dir="ltr" style="text-align: right">%s</td><td>%s</td><td>%s</td></tr>' % r for r in SIZES)
    table = ('<table style="border-collapse: collapse; width: 100%%"><tr><th></th><th>פיקסלים לנקודה</th><th>איכות</th><th>הסט, ק״ב</th><th>תמונה אחת, ק״ב</th></tr>%s</table>' % rows)
    qual = ('<div class="rowx" style="gap: 16px; align-items: flex-start">'
            '<div class="fr">%s<p class="cap"><b>היום:</b> 1.5 פיקסלים לנקודה, 327 ק״ב לשבע תמונות.</p></div>'
            '<div class="fr">%s<p class="cap"><b>ההמלצה:</b> 3 פיקסלים לנקודה, כמו החזית.</p></div>'
            '<div class="card" style="flex: 1">%s'
            '<p class="cap" style="margin-top: 8px"><b>ההמלצה (א):</b> אותה חדות כמו החזית. הסט יורד ברקע פעם אחת לכל גרסה, כמו היום, ונשמר בטלפון. 1.07 מ״ב פעם אחת בעונה. '
            '<b>ב:</b> 626 ק״ב, חד יותר מהיום אבל רך מהחזית, ורואים את ההבדל במעבר.</p>'
            '<p class="cap"><b>לארכיטקט:</b> התקרה היום 350 ק״ב לסט. א צריכה 1.1 מ״ב. השאר כמו בתנאים של 25.11: <span dir="ltr">rev</span> אחד לסט ולטבלאות, משתמשים רק כשכל השבע ירדו, ובלי הסט חי לרגע.</p></div></div>'
            % (crop('kobi-morning', 150, 280, 120, 150, 3), crop('kobi-3m', 150, 280, 120, 150, 3), table))
    honest = li('<b>החדות פותרת חצי.</b> בצל של הצד הצפוני הסלע מצויר בפסים ארוכים לאורך קו הנפילה, וקירות שפונים אלינו נמרחים מעט. זה הסגנון של הצייר בזווית הזו, לא הרזולוציה. ניסיתי דגימה צפופה יותר (פי 2): הפסים קצרים יותר אבל עדיין פסים.',
                '<b>דגל לפיני:</b> כדי שהצד הזה יהיה "מרהיב" כמו החזית צריך סבב צייר משלו: סלע אמיתי בקירות (שכבות ומדפים במקום פסים), ופרטים בשלג שבצל. זה סבב עבודה נפרד, לא חלק מהלוחות האלה.',
                'התמונות כאן רונדרו בכלי העיצוב (<span dir="ltr">peak.py</span>, <span dir="ltr">out=3</span>), לא נבדקו בטלפון. הגדלים נמדדו על שבע התמונות.')
    asks = ''.join('<p class="q"><span class="qn">%d</span><span>%s</span></p>' % (i + 1, t) for i, t in enumerate([
        '<b>הכניסה:</b> תגית "צד <bdi>Kobi</bdi>" על הרכס, ונגיעה בקו של <bdi>Kobi</bdi> מסובבת? (כן)',
        '<b>החזרה:</b> המצפן מעל המתג, עם "צד <bdi>Kobi</bdi>" ל-3.5 שניות, ולא כפתור "חזרה"? (כן)',
        '<b>האיכות:</b> צד <bdi>Kobi</bdi> בחדות של החזית, 1.1 מ״ב שיורדים פעם אחת (א), או 626 ק״ב (ב)? (א, אחרי הארכיטקט)',
        '<b>סבב צייר לצד הצפוני</b> (סלע ושלג בצל), כדי שיהיה מרהיב כמו החזית? (כן, סבב נפרד)']))
    inner = ('<p class="sec">P.17 · 27.11.2026 · ממתין לפיני</p>'
             '<h1>לצד השני של הרכס, ובחזרה בנגיעה אחת</h1>'
             '<p class="lead">היום <bdi>Kobi</bdi> הוא קו קטן בראש התמונה, ונגיעה בו רק בוחרת. ההצעה: תגית על הרכס שמסובבת לצד של <bdi>Kobi</bdi>, תמונה חדה כמו החזית, ומצפן שמחזיר, כמו בהר החי.</p>'
             '<div class="card"><h2>הדרך</h2>%s</div>'
             '<div class="card"><h2>האיכות: צד <bdi>Kobi</bdi> בחדות של החזית</h2>%s</div>'
             '<div class="card"><h2>ביושר</h2>%s</div>'
             '<div class="card"><h2>לפיני</h2>%s</div>'
             '<p class="cap">המסירה: <span dir="ltr">design/handoff/2026-11-27-redesign-kobi-side/</span>. נבדק בכלי העיצוב, לא בטלפון.</p>'
             % (flow, qual, honest, asks))
    import live_boards as LB
    html = LB.board('צד Kobi: לעבור ולחזור', 1280, HB0, LB.wide(1280, HB0, inner)).replace('<style>', '<style>' + HN.G4.MB.CSS, 1)
    open(os.path.join(PROJ, 'KB00-KobiSide.dc.html'), 'w', encoding='utf8').write(html)


def phones():
    return {
        'KB1.dc.html': ('P.17 · החזית: תגית "צד Kobi" על הרכס', kb1(), 'day'),
        'KB2.dc.html': ('P.17 · בתנועה: לצד של Kobi ובחזרה', kb2(), 'day'),
        'KB3.dc.html': ('P.17 · בצד: המצפן עם "צד Kobi", ביום', kb3(), 'day'),
        'KB4.dc.html': ('P.17 · בצד, בלילה: המצפן מקופל', kb4(), 'night'),
    }


FILES = ['KB00-KobiSide.dc.html', 'KB1.dc.html', 'KB2.dc.html', 'KB3.dc.html', 'KB4.dc.html']


def write_canvas(cj):
    j = json.load(open(cj, encoding='utf8'))
    for fn in FILES: j['boards'].pop(fn, None)
    j['order'] = [f for f in j['order'] if f not in FILES]
    T_ = phones()
    place = [('KB00-KobiSide.dc.html', 0, 0, 1280, HB0, 'P.17: מה ולמה, והאיכות')]
    for k, fn in enumerate(FILES[1:]):
        place.append((fn, 1400 + 470 * k, 0, 390, 844, T_[fn][0]))
    for fn, x, y, w, h, t in place:
        code = fn.split('.dc.html')[0].split('-')[0]
        j['boards'][fn] = dict(x=x, y=y, w=w, h=h, title='%s (%s)' % (t, code), page=PAGE)
        j['order'].append(fn)
    if not any(pg['id'] == PAGE for pg in j['pages']):
        j['pages'].insert(0, dict(id=PAGE, name=PAGE_NAME))
    j['notes']['t' + PAGE] = dict(kind='title1', maxW=3200, page=PAGE, x=0, y=-300, text='צד Kobi בתמונה: לעבור ולחזור')
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
    kb00()
    write_canvas(cj)
    if pv:
        keep = DK.FILES; DK.FILES = FILES; DK.preview(*pv); DK.FILES = keep
    print('ok', cj)


if __name__ == '__main__':
    main()
