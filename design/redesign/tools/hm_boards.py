#!/usr/bin/env python3
"""P.19 (Pini, 27.11.2026): the first-run hints jump from one target to the next. The ring should glide to the next
target and the bubble should move with it or cross-fade. Here: the ring glides, the old bubble fades out, the new one
fades in beside its target, and one soft breath marks the arrival. No new strings.
Boards: HM00 (what and why, the timing), HM1 (the sequence in motion, day), HM2 (frame strip of one move, and the
reduced-motion row), HM3 (reduced motion, in motion), HM4 (the same motion at night).
Design only. Handoff: design/handoff/2026-11-27-redesign-hint-motion/.
Usage: python3 design/redesign/tools/hm_boards.py [canvas.json path] [--preview <dir> <assets dir>]"""
import os, sys, json, math
import hn_boards as HN
from hn_boards import T, screen, bubble
from v56_boards import wb, li, PROJ
import peak_boards as PB
from peak_boards import VH, P
import dk_boards as DK

PAGE = 'hintmotion'
PAGE_NAME = 'רמזים: תנועה רכה בין רמז לרמז (27.11)'

# ---------------------------------------------------------------- the spec (one place; the board text and the motion read these)
EMPH = (0.2, 0.0, 0.0, 1.0)       # the ring's glide: Material "emphasized decelerate"
DECEL = (0.0, 0.0, 0.2, 1.0)      # the new bubble in
ACCEL = (0.3, 0.0, 1.0, 1.0)      # the old bubble out
OUT_MS = 120                      # old bubble out
IN_MS = 220                       # new bubble in
IN_AT = 0.55                      # new bubble starts at this share of the glide
HALO_AT = 0.80                    # the breath starts at this share of the glide
HALO_MS = 420
HALO_DP = 10
SLIDE_DP = 6                      # the new bubble slides this far away from its target as it comes in


def glide_ms(d_dp):
    """duration by distance: short hops are quick, a hop across the screen is calm"""
    return int(min(560, max(360, 300 + 0.4 * d_dp)))


def bez(p, t):
    """cubic-bezier(x1, y1, x2, y2) at time t (0..1): the progress"""
    x1, y1, x2, y2 = p
    def bx(s): return 3 * x1 * s * (1 - s) ** 2 + 3 * x2 * s * s * (1 - s) + s ** 3
    def by(s): return 3 * y1 * s * (1 - s) ** 2 + 3 * y2 * s * s * (1 - s) + s ** 3
    lo, hi = 0.0, 1.0
    for _ in range(40):
        m = (lo + hi) / 2
        if bx(m) < t: lo = m
        else: hi = m
    return by((lo + hi) / 2)


def cb(p): return 'cubic-bezier(%g, %g, %g, %g)' % p


# ---------------------------------------------------------------- the six steps on the mountain, as on the hint boards (HN1 to HN6)
def steps():
    R = P.run_path(VH, 'Goodaura 1'); x, y, *_ = P.at(R, R['cum'][-1] * 0.45)
    # (id, ring x, y, w, h, r, run, bubble kwargs, the slide direction of the bubble: dx, dy)
    return [
        ('run', x - 23, y - 23, 46, 46, 23, True, dict(text=T['run'], side='down', ax=x - 66, frm='left', step=1, left=66, bottom=844 - (y - 23 - 12)), (0, -1)),
        ('find', 64, 775, 217, 54, 27, False, dict(text=T['find'], side='down', ax=110, frm='left', step=2, left=62, bottom=844 - 763), (0, -1)),
        ('rec', 12, 775, 54, 54, 27, False, dict(text=T['rec'], side='down', ax=27, frm='left', step=3, left=12, bottom=844 - 763), (0, -1)),
        ('village', 279, 775, 99, 54, 27, False, dict(text=T['village'], side='down', ax=50, frm='right', step=4, right=12, bottom=844 - 763), (0, -1)),
        ('mode', 7, 561, 56, 100, 28, False, dict(text=T['mode'], side='left', ax=22, frm='top', step=5, left=75, top=589), (1, 0)),
        ('me', 7, 47, 54, 54, 27, False, dict(text=T['me'], side='up', ax=22, frm='left', step=6, left=12, top=113), (0, 1)),
    ]


def centre(s): return (s[1] + s[3] / 2, s[2] + s[4] / 2)


def dist(a, b):
    (ax, ay), (bx, by) = centre(a), centre(b)
    return math.hypot(bx - ax, by - ay)


def bub(s, state='full'):
    k = dict(s[7]); text = k.pop('text'); side = k.pop('side'); ax = k.pop('ax'); frm = k.pop('frm'); step = k.pop('step')
    return bubble(text, side, ax, frm, step=step, total=6, state=state, **k)


MCSS = """
.H .hm{position:absolute;box-sizing:border-box;border:2.5px solid var(--ink);box-shadow:0 0 0 2.5px rgba(255,255,255,.9),0 0 0 2400px rgba(8,16,30,.26);pointer-events:none;z-index:4}
.H .night .hm{box-shadow:0 0 0 2.5px rgba(6,11,22,.75),0 0 0 2400px rgba(0,0,0,.34)}
.H .hm::after{content:'';position:absolute;inset:-5px;border-radius:inherit;box-shadow:0 0 0 0 rgba(255,255,255,0)}
.H .night .hm::after{--hc:rgba(234,241,248,.55)}
.H .hmw{position:absolute;inset:0;z-index:5;pointer-events:none}
.H .hmw .hn{animation:none}
.H .tap{position:absolute;width:44px;height:44px;margin:-22px 0 0 -22px;border-radius:50%;background:rgba(255,255,255,.55);box-shadow:0 0 0 2px rgba(10,25,45,.25);z-index:6;opacity:0;pointer-events:none}
.H .hmlab{position:absolute;left:12px;top:12px;z-index:7;font-size:12px;font-weight:700;border-radius:12px;padding:4px 10px}
"""
HN.G4.MB.CSS = HN.G4.MB.CSS + MCSS

HOLD = 1900        # the board's dwell on each hint (the real one: 13 s, or until a tap)


def timeline(st):
    """for the looping boards: tap times and glide lengths, in ms from the start of the loop"""
    t = 700; out = []
    for i, s in enumerate(st):
        nxt = st[(i + 1) % len(st)]
        last = i == len(st) - 1
        g = 0 if last else glide_ms(dist(s, nxt))
        out.append(dict(arrive=t, tap=t + HOLD, glide=g))
        t = t + HOLD + (g if not last else 900)
    return out, t


def kf_ring(name, st, tl, total, reduced=False):
    """the ring: holds on each target, glides to the next with the emphasized curve; at the loop's end it fades and returns"""
    def rect(s):
        return 'left: %.1fpx; top: %.1fpx; width: %.1fpx; height: %.1fpx; border-radius: %.1fpx; background: %s' % (
            s[1], s[2], s[3], s[4], s[5], 'rgba(255,255,255,.3)' if s[6] else 'rgba(255,255,255,0)')
    pc = lambda ms: '%.3f%%' % (100.0 * ms / total)
    o = '@keyframes %s{' % name
    o += '0%%{%s; opacity: 0; animation-timing-function: ease-out}' % rect(st[0])
    o += '%s{%s; opacity: 1; animation-timing-function: linear}' % (pc(tl[0]['arrive'] - 300), rect(st[0]))
    for i, s in enumerate(st[:-1]):
        tap = tl[i]['tap']
        if reduced:
            o += '%s{%s; opacity: 1}' % (pc(tap), rect(s))
            o += '%s{%s; opacity: 0}' % (pc(tap + 100), rect(s))
            o += '%s{%s; opacity: 0}' % (pc(tap + 101), rect(st[i + 1]))
            o += '%s{%s; opacity: 1}' % (pc(tap + 250), rect(st[i + 1]))
        else:
            o += '%s{%s; animation-timing-function: %s}' % (pc(tap), rect(s), cb(EMPH))
            o += '%s{%s; animation-timing-function: linear}' % (pc(tap + tl[i]['glide']), rect(st[i + 1]))
    end = tl[-1]['tap']
    o += '%s{%s; opacity: 1}' % (pc(end), rect(st[-1]))
    o += '%s{%s; opacity: 0}' % (pc(end + 240), rect(st[-1]))
    o += '100%%{%s; opacity: 0}}' % rect(st[0])
    return o


def kf_halo(name, tl, total):
    pc = lambda ms: '%.3f%%' % (100.0 * ms / total)
    o = '@keyframes %s{0%%{box-shadow: 0 0 0 0 rgba(255,255,255,0)}' % name
    for i, x in enumerate(tl):
        a = x['arrive'] if i == 0 else tl[i - 1]['tap'] + int(tl[i - 1]['glide'] * HALO_AT)
        if i == 0: a = x['arrive'] - 80
        o += '%s{box-shadow: 0 0 0 0 rgba(255,255,255,0)}' % pc(a - 1)
        o += '%s{box-shadow: 0 0 0 0 rgba(255,255,255,.6); animation-timing-function: %s}' % (pc(a), cb(DECEL))
        o += '%s{box-shadow: 0 0 0 %dpx rgba(255,255,255,0)}' % (pc(a + HALO_MS), HALO_DP)
    return o + '100%{box-shadow: 0 0 0 0 rgba(255,255,255,0)}}'


def kf_bubble(name, i, s, tl, total, reduced=False):
    pc = lambda ms: '%.3f%%' % (100.0 * ms / total)
    dx, dy = s[8]
    off = 'translate(%dpx, %dpx)' % (-dx * SLIDE_DP, -dy * SLIDE_DP)   # starts toward the target, settles away from it
    if i == 0:
        a = tl[0]['arrive']
    else:
        p = tl[i - 1]
        a = p['tap'] + (101 if reduced else int(p['glide'] * IN_AT))
    inn = 150 if reduced else IN_MS
    out_ = 100 if reduced else OUT_MS
    z = 'translate(0, 0)' if reduced else off
    end = tl[i]['tap']
    o = '@keyframes %s{0%%{opacity: 0; transform: %s}' % (name, z)
    o += '%s{opacity: 0; transform: %s; animation-timing-function: %s}' % (pc(a), z, cb(DECEL))
    o += '%s{opacity: 1; transform: translate(0, 0); animation-timing-function: %s}' % (pc(a + inn), cb(ACCEL))
    o += '%s{opacity: 1; transform: translate(0, 0); animation-timing-function: %s}' % (pc(end), cb(ACCEL))
    o += '%s{opacity: 0; transform: %s}' % (pc(end + out_), 'translate(0, 0)' if reduced else 'scale(.98)')
    return o + '100%{opacity: 0}}'


def kf_tap(name, tl, total):
    pc = lambda ms: '%.3f%%' % (100.0 * ms / total)
    o = '@keyframes %s{0%%{opacity: 0; transform: scale(.6)}' % name
    for x in tl:
        o += '%s{opacity: 0; transform: scale(.6)}%s{opacity: 1; transform: scale(1)}%s{opacity: 0; transform: scale(1.25)}' % (
            pc(x['tap'] - 120), pc(x['tap']), pc(x['tap'] + 260))
    return o + '100%{opacity: 0}}'


def moving(tag, reduced=False, night=False):
    """the six hints in motion, one loop"""
    st = steps(); tl, total = timeline(st)
    css = kf_ring('hmR' + tag, st, tl, total, reduced)
    if not reduced: css += kf_halo('hmH' + tag, tl, total)
    css += kf_tap('hmT' + tag, tl, total)
    o = screen('night' if night else 'morning')
    o += '<div class="hm" style="animation: hmR%s %dms linear infinite"></div>' % (tag, total)
    if not reduced:
        o = o.replace('<div class="hm" style="animation: hmR%s' % tag,
                      '<style>.H .hm.h%s::after{animation: hmH%s %dms linear infinite}</style><div class="hm h%s" style="animation: hmR%s' % (tag, tag, total, tag, tag), 1)
    for i, s in enumerate(st):
        css += kf_bubble('hmB%s%d' % (tag, i), i, s, tl, total, reduced)
        o += '<div class="hmw" style="animation: hmB%s%d %dms linear infinite; opacity: 0">%s</div>' % (tag, i, total, bub(s))
    o += '<span class="tap" style="left: 236px; top: 360px; animation: hmT%s %dms linear infinite"></span>' % (tag, total)
    lab = 'הפחתת תנועה: רק הצלבה' if reduced else 'כל רמז מקוצר ל-1.9 שנ׳; המעבר בזמן אמיתי'
    o += '<span class="hmlab glass" style="top: 104px">%s</span>' % lab
    return '<style>%s</style>' % css + o


# ---------------------------------------------------------------- the frame strip: run to find (the longest hop on the dock side)
def frame(a, b, ms, g, reduced=False, w=210):
    """one frozen moment of the move from step a to step b"""
    sc = w / 390
    if reduced:
        ro = max(0.0, 1 - ms / 100) if ms < 100 else 0.0
        ri = 0.0 if ms <= 100 else min(1.0, (ms - 100) / 150)
        rings = ''
        if ro > 0: rings += ring_at(a, 1, ro)
        if ri > 0: rings += ring_at(b, 1, ri)
        if ro == 0 and ri == 0: rings += '<div class="hm" style="left: 0; top: 0; width: 0; height: 0; border: 0; box-shadow: 0 0 0 2400px rgba(8,16,30,.26)"></div>'
        bo, bi = ro, ri; dy_in = 0
    else:
        p = bez(EMPH, min(1.0, ms / g))
        rings = ring_mix(a, b, p, halo=(ms - g * HALO_AT) / HALO_MS)
        bo = 1 - bez(ACCEL, min(1.0, ms / OUT_MS))
        t_in = (ms - g * IN_AT) / IN_MS
        bi = 0.0 if t_in <= 0 else bez(DECEL, min(1.0, t_in))
        dy_in = (1 - bi) * SLIDE_DP
    o = screen()
    o += rings
    if bo > 0.01: o += '<div class="hmw" style="opacity: %.2f">%s</div>' % (bo, bub(a))
    if bi > 0.01: o += '<div class="hmw" style="opacity: %.2f; transform: translateY(%.1fpx)">%s</div>' % (bi, dy_in, bub(b))
    return ('<div class="fr" style="width: %dpx"><div class="H" style="width: %dpx; height: %dpx; position: relative; overflow: hidden; border-radius: 14px; border: 1px solid #D9DEE4; flex: none">'
            '<div class="day" style="position: absolute; left: 0; top: 0; width: 390px; height: 844px; transform: scale(%.4f); transform-origin: 0 0">%s</div></div>'
            % (w, w, round(844 * sc), sc, o))


def ring_at(s, k, op=1.0):
    return ('<div class="hm" style="left: %.1fpx; top: %.1fpx; width: %.1fpx; height: %.1fpx; border-radius: %.1fpx; opacity: %.2f%s"></div>'
            % (s[1], s[2], s[3], s[4], s[5], op, '; background: rgba(255,255,255,.3)' if s[6] else ''))


def ring_mix(a, b, p, halo=-1):
    L = lambda i: a[i] + (b[i] - a[i]) * p
    bg = 0.3 * (1 - p) if a[6] else 0
    hs = ''
    if 0 <= halo <= 1:
        e = bez(DECEL, halo)
        hs = '; outline: 2px solid rgba(255,255,255,%.2f); outline-offset: %.1fpx' % (0.6 * (1 - e), 2.5 + HALO_DP * e)
    return ('<div class="hm" style="left: %.1fpx; top: %.1fpx; width: %.1fpx; height: %.1fpx; border-radius: %.1fpx; background: rgba(255,255,255,%.2f)%s"></div>'
            % (L(1), L(2), L(3), L(4), L(5), bg, hs))


def hm2():
    st = steps(); a, b = st[0], st[1]; g = glide_ms(dist(a, b))
    marks = [(0, 'נגיעה'), (OUT_MS, 'הבועה הישנה נעלמה'), (int(g * IN_AT), 'הבועה החדשה מתחילה'), (g, 'הטבעת הגיעה'),
             (int(g * HALO_AT) + HALO_MS // 2, 'נשימה אחת'), (int(g * IN_AT) + IN_MS + 200, 'במקום')]
    row = ''.join(frame(a, b, ms, g) + '<p class="cap"><b>%d מ״ש</b> · %s</p></div>' % (ms, cap) for ms, cap in marks)
    rrow = ''.join(frame(a, b, ms, g, reduced=True) + '<p class="cap"><b>%d מ״ש</b> · %s</p></div>' % (ms, cap)
                   for ms, cap in [(0, 'נגיעה'), (60, 'הישן דוהה'), (100, 'ריק לרגע'), (175, 'החדש מופיע'), (250, 'במקום')])
    inner = ('<p class="sec">P.19 · פריים אחרי פריים</p><h1>מהמסלול אל "לאן יורדים?": %d מ״ש</h1>' % g +
             '<p class="lead">המעבר הארוך ברצף: מאמצע ההר אל הסרגל. הטבעת מחליקה בקו ישר ומאטה בסוף, הבועה הישנה נעלמת מיד, והחדשה עולה מהיעד כשהטבעת כבר קרובה.</p>'
             '<div class="rowx" style="gap: 14px; margin-top: 10px">%s</div>'
             '<h2 style="margin-top: 26px">הפחתת תנועה (ההגדרה בטלפון): 250 מ״ש, בלי החלקה</h2>'
             '<div class="rowx" style="gap: 14px; margin-top: 10px">%s</div>' % (row, rrow))
    import live_boards as LB
    html = LB.board('פריים אחרי פריים', 1400, HB2, LB.wide(1400, HB2, inner)).replace('<style>', '<style>' + HN.G4.MB.CSS, 1)
    open(os.path.join(PROJ, 'HM2-Frames.dc.html'), 'w', encoding='utf8').write(html)


HB2 = 1300


def chart():
    """the timing of one move as tracks, 0 to 800 ms"""
    W, x0, sc = 1060, 200, 1060 / 800.0
    st = steps(); g = glide_ms(dist(st[0], st[1]))
    tracks = [
        ('הבועה הישנה', 0, OUT_MS, '#9AA6B4', 'נעלמת, מאיצה'),
        ('הטבעת', 0, g, '#1E64C8', 'מחליקה, %d עד %d מ״ש לפי המרחק' % (360, 560)),
        ('הבועה החדשה', int(g * IN_AT), int(g * IN_AT) + IN_MS, '#6B3FD4', 'עולה %d נק׳ מהיעד, מאטה' % SLIDE_DP),
        ('נשימה', int(g * HALO_AT), int(g * HALO_AT) + HALO_MS, '#2BA36B', 'הילה של %d נק׳ שדועכת' % HALO_DP),
        ('ה-X', int(g * IN_AT) + IN_MS, 800, '#C77A00', 'הזמן של הרמז מתחיל רק כשהבועה במקום'),
    ]
    o = '<svg width="%d" height="%d" viewBox="0 0 %d %d" style="display: block; direction: ltr">' % (x0 + W + 20, 60 + 46 * len(tracks), x0 + W + 20, 60 + 46 * len(tracks))
    for ms in range(0, 801, 100):
        x = x0 + ms * sc
        o += '<line x1="%.0f" x2="%.0f" y1="24" y2="%d" stroke="#D9DEE4"/><text x="%.0f" y="16" font-size="12" text-anchor="middle" fill="#3C4753">%d</text>' % (x, x, 30 + 46 * len(tracks), x, ms)
    for i, (nm, a, b, c, note) in enumerate(tracks):
        y = 34 + 46 * i
        o += '<text x="%d" y="%d" font-size="15" font-weight="700" text-anchor="end" fill="#10161D">%s</text>' % (x0 - 14, y + 19, nm)
        o += '<rect x="%.0f" y="%d" width="%.0f" height="26" rx="13" fill="%s"/>' % (x0 + a * sc, y, (b - a) * sc, c)
        o += '<text x="%.0f" y="%d" font-size="13" fill="#3C4753" text-anchor="start">%s</text>' % (x0 + b * sc + 8 if b < 700 else x0 + a * sc + 10, y + 18, note) if b < 700 else \
             '<text x="%.0f" y="%d" font-size="13" fill="#fff" text-anchor="start">%s</text>' % (x0 + a * sc + 10, y + 18, note)
    return o + '</svg>'


HB0 = 1720


def hm00():
    why = li('<b>הטבעת קופצת בגודל:</b> בכל רמז היא מתחילה גדולה פי 1.18 ומתכווצת ב-200 מ״ש. במעבר זה נראה כמו קפיצה, לא כמו תנועה.',
             '<b>הבועה נעלמת לפריים ונוחתת:</b> הגודל שלה נמדד מחדש בכל רמז, ועד המדידה היא שקופה. אחר כך היא מופיעה בבת אחת, ובמקרה של צד אחר (למשל ליד המתג) היא קופצת לצד השני.',
             '<b>על המסלול הטבעת רועדת:</b> היא מקבלת יעד חדש כל 100 מ״ש בזמן שההר מצטייר, ומתחילה תנועה חדשה כל פעם.',
             '<b>רמז שהיעד שלו לא על המסך</b> מחכה 900 מ״ש עם טבעת במקום ישן ובלי בועה, ואז מדלג.')
    rec = li('<b>הטבעת מחליקה</b> מהיעד הקודם לבא, בקו ישר, ומשנה תוך כדי את הגודל והפינות. משך לפי המרחק: %d עד %d מ״ש (קפיצה קצרה בסרגל מהירה, מאמצע ההר אל הסרגל רגועה). עקומה: מאטה בסוף (<span dir="ltr">%s</span>).' % (360, 560, cb(EMPH)),
             '<b>הבועה לא נוסעת:</b> הישנה נעלמת ב-%d מ״ש, והחדשה עולה %d נקודות מתוך היעד ומתייצבת ב-%d מ״ש, כשהטבעת עברה %d%% מהדרך. כך אין בועה שמכסה את ההר בדרך, והטקסט לא מתחלף באוויר.' % (OUT_MS, SLIDE_DP, IN_MS, int(IN_AT * 100)),
             '<b>נשימה אחת בהגעה:</b> הילה לבנה רכה יוצאת %d נקודות מהטבעת ודועכת ב-%d מ״ש. במקום ההתכווצות של היום.' % (HALO_DP, HALO_MS),
             '<b>ההחשכה לא זזה.</b> רק החור שבה נוסע עם הטבעת. הנקודות בבועה החדשה כבר במקום הנכון.',
             '<b>ה-13 שניות מתחילות</b> רק כשהבועה החדשה במקום.',
             '<b>על המסלול:</b> הטבעת עוקבת אחרי הנקודה בקפיץ רך (בלי קפיצה כל 100 מ״ש), כמו מצלמה שמתייצבת.',
             '<b>נגיעה בזמן המעבר לא נספרת,</b> כדי שנגיעה כפולה לא תדלג על רמז. אחרי שהבועה החדשה התחילה לעלות, נגיעה עוברת לרמז הבא מהנקודה שבה הטבעת נמצאת.',
             '<b>יעד שאינו על המסך</b> נבדק לפני התנועה: הטבעת מחליקה ישר לרמז הבא שיש לו יעד.',
             '<b>פתיחה וסיום:</b> בפתיחה ההחשכה עולה ב-240 מ״ש, הטבעת מופיעה עם הנשימה, והבועה אחריה. בסיום (ה-X או הרמז האחרון) הבועה נעלמת ב-120 מ״ש, והטבעת וההחשכה דועכות יחד ב-240 מ״ש.')
    red = li('<b>הפחתת תנועה בטלפון:</b> בלי החלקה, בלי הזזה ובלי נשימה. הטבעת והבועה הישנות דוהות ב-100 מ״ש, והחדשות מופיעות במקומן ב-150 מ״ש. דהייה מותרת גם במצב הזה.',
             '<b>אנימציות כבויות לגמרי</b> (מהירות אנימציה 0 בהגדרות המפתח): החלפה מיידית.',
             '<b>קורא מסך:</b> בלי שינוי. הרמז החדש מוקרא כשהבועה מופיעה.')
    alt = li('<b>הבועה נוסעת עם הטבעת</b> כיחידה אחת. נראה מרשים בקפיצה קצרה, אבל באמצע ההר היא מכסה את המסלולים בדרך, הטקסט מתחלף באוויר, ובמעבר אל המתג היא צריכה להתהפך לצד השני. לא המלצתי.')
    asks = ''.join('<p class="q"><span class="qn">%d</span><span>%s</span></p>' % (i + 1, t) for i, t in enumerate([
        '<b>הטבעת מחליקה, והבועה מתחלפת בדהייה</b> ליד היעד החדש (ולא נוסעת עם הטבעת)? (כן)',
        '<b>נשימה אחת</b> כשהטבעת מגיעה, במקום ההתכווצות של היום? (כן)',
        '<b>נגיעה בזמן המעבר לא נספרת</b>, כדי שלא ידלגו על רמז בטעות? (כן)']))
    inner = ('<p class="sec">P.19 · 27.11.2026 · ממתין לפיני</p>'
             '<h1>מרמז לרמז: הטבעת מחליקה, הבועה מתחלפת בשקט</h1>'
             '<p class="lead">היום הטבעת והבועה קופצות. ההצעה: תנועה אחת רציפה שמובילה את העין מהיעד הקודם לבא, ובועה שמופיעה רק כשיש לה לאן להצביע. בלי טקסט חדש.</p>'
             '<div class="rowx" style="align-items: stretch"><div class="card" style="flex: 1"><h2>למה זה קופץ היום (מהקוד)</h2>%s</div>'
             '<div class="card" style="flex: 1.35"><h2>ההצעה</h2>%s</div></div>'
             '<div class="card" style="margin-top: 16px"><h2>מעבר אחד על ציר הזמן (%d מ״ש, מהמסלול אל הסרגל)</h2>%s</div>'
             '<div class="rowx" style="align-items: stretch; margin-top: 16px"><div class="card" style="flex: 1"><h2>הפחתת תנועה ונגישות</h2>%s</div>'
             '<div class="card" style="flex: 1"><h2>חלופה שלא בחרתי</h2>%s</div></div>'
             '<div class="card" style="margin-top: 16px"><h2>לפיני</h2>%s</div>'
             '<p class="cap">הלוחות HM1, HM3 ו-HM4 זזים: כל רמז מקוצר בהם ל-1.9 שניות, והמעברים בזמן האמיתי. הסדר והיעדים כמו בלוחות HN1 עד HN6; הסרגל של מעבר חוויית המשתמש (תפריט) לא משנה את התנועה. '
             'נבדק בכלי העיצוב, לא בטלפון. המסירה: <span dir="ltr">design/handoff/2026-11-27-redesign-hint-motion/</span>.</p>'
             % (why, rec, glide_ms(dist(steps()[0], steps()[1])), chart(), red, alt, asks))
    wb('HM00-HintMotion.dc.html', 'רמזים: תנועה בין רמז לרמז', 1280, HB0, inner)


def phones():
    return {
        'HM1.dc.html': ('P.19 · בתנועה: ששת הרמזים על ההר, ביום', moving('a'), None),
        'HM3.dc.html': ('P.19 · הפחתת תנועה: הצלבה בלבד', moving('r', reduced=True), None),
        'HM4.dc.html': ('P.19 · בתנועה, בלילה', moving('n', night=True), 'night'),
    }


FILES = ['HM00-HintMotion.dc.html', 'HM1.dc.html', 'HM3.dc.html', 'HM4.dc.html', 'HM2-Frames.dc.html']


def write_canvas(cj):
    j = json.load(open(cj, encoding='utf8'))
    for fn in FILES: j['boards'].pop(fn, None)
    j['order'] = [f for f in j['order'] if f not in FILES]
    T_ = phones()
    place = [('HM00-HintMotion.dc.html', 0, 0, 1280, HB0, 'P.19: מה ולמה, והתזמון')]
    for k, fn in enumerate(['HM1.dc.html', 'HM3.dc.html', 'HM4.dc.html']):
        place.append((fn, 1400 + 470 * k, 0, 390, 844, T_[fn][0]))
    place.append(('HM2-Frames.dc.html', 0, HB0 + 160, 1400, HB2, 'P.19: פריים אחרי פריים, והפחתת תנועה'))
    for fn, x, y, w, h, t in place:
        code = fn.split('.dc.html')[0].split('-')[0]
        j['boards'][fn] = dict(x=x, y=y, w=w, h=h, title='%s (%s)' % (t, code), page=PAGE)
        j['order'].append(fn)
    if not any(pg['id'] == PAGE for pg in j['pages']):
        j['pages'].insert(0, dict(id=PAGE, name=PAGE_NAME))
    j['notes']['t' + PAGE] = dict(kind='title1', maxW=2800, page=PAGE, x=0, y=-300, text='רמזים: תנועה רכה בין רמז לרמז')
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
    hm00(); hm2()
    write_canvas(cj)
    if pv:
        keep = DK.FILES; DK.FILES = FILES; DK.preview(*pv); DK.FILES = keep
    print('ok', cj)


if __name__ == '__main__':
    main()
