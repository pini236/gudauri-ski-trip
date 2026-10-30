#!/usr/bin/env python3
"""Companion character, stage B: direction sketches for the canvas (Pini, 30.10.2026, via the manager session).

One skeleton per pose (joints in units, feet at y=0, y up is negative), drawn in three skins:
  A  the skier from the descent game (helmet, goggles, coat), expression by body only
  B  a knit hat and a simple face, bigger head
  C  a pictogram, like the figures on ski area signs
Colours are the site's tokens and the game's six coats. No code for the product: these are sketches only.
Run from the repo root:  python3 design/characters/sketch.py && node design/characters/render.mjs"""
import math, pathlib

here = pathlib.Path(__file__).resolve().parent
out = here / 'boards'
out.mkdir(exist_ok=True)

DAY = dict(bg='#EEF2F5', paper='#FFFFFF', ink='#13233A', muted='#4B5A6F', rule='#CBD5DF', line='#13233A', legs='#2A3B55',
           snow='#FFFFFF', shade='#D3DFEB', helmet='#F4F7FB')
NIGHT = dict(bg='#0D1522', paper='#16223A', ink='#EAF0F7', muted='#A3B3C8', rule='#2A3B55', line='#A3B3C8', legs='#34496E',
             snow='#E8EEF6', shade='#9EB1C9', helmet='#E8EEF6')
COAT = '#F07A2E'  # the game's default coat ("my coat"); the other five: red, blue, yellow, green, purple
COATS = ['#D1342B', '#1F5FC4', '#F4B942', '#1B8A4C', '#7B4FC4', '#F07A2E']

# ---------- poses: side view, facing the reading start is not assumed (the figure faces right) ----------
P = {
    'stand': dict(feet=[(0, 0), (5, 0)], knees=[(4, -23), (8, -23)], hip=(1, -46), neck=(3, -76),
                  arms=[[(-6, -61), (-8, -46)], [(13, -62), (17, -48)]], pole=[(17, -48), (12, 2)], skis=0),
    'wave': dict(feet=[(0, 0), (5, 0)], knees=[(4, -23), (8, -23)], hip=(1, -46), neck=(3, -76),
                 arms=[[(-6, -61), (-8, -46)], [(16, -82), (22, -100)]], skis=0),
    'carve': dict(feet=[(0, 0), (6, 0)], knees=[(13, -18), (17, -18)], hip=(2, -37), neck=(15, -63),
                  arms=[[(4, -52), (-2, -42)], [(26, -52), (35, -46)]], pole=[(35, -46), (28, 2)], skis=0, tilt=-14, spray=True),
    'cheer': dict(feet=[(0, -8), (5, -8)], knees=[(3, -31), (8, -31)], hip=(1, -54), neck=(3, -84),
                  arms=[[(-10, -96), (-15, -113)], [(15, -96), (20, -113)]], skis=-8),
    'shrug': dict(feet=[(0, 0), (5, 0)], knees=[(4, -23), (8, -23)], hip=(1, -46), neck=(3, -76),
                  arms=[[(-16, -62), (-22, -72)], [(18, -62), (24, -72)]], skis=0),
    'sit': dict(feet=[(18, 22), (22, 22)], knees=[(17, -2), (21, -2)], hip=(0, 0), neck=(-2, -30),
                arms=[[(4, -15), (14, -6)], [(8, -15), (18, -6)]], seat=True),
    'sleep': dict(feet=[(18, 22), (22, 22)], knees=[(17, -2), (21, -2)], hip=(0, 0), neck=(4, -27), head_dx=6, head_dy=4,
                  arms=[[(6, -14), (16, -8)], [(10, -14), (20, -8)]], seat=True, zz=True, eyes='closed'),
    'tea': dict(feet=[(18, 22), (22, 22)], knees=[(17, -2), (21, -2)], hip=(0, 0), neck=(-2, -30),
                arms=[[(4, -15), (14, -6)], [(12, -18), (12, -30)]], seat=True, cup=(12, -30)),
    'brush': dict(feet=[(30, -4), (34, -4)], knees=[(15, -15), (19, -15)], hip=(0, -8), neck=(-3, -37),
                  arms=[[(-13, -22), (-15, -9)], [(10, -48), (2, -60)]], ground_skis=True, puff=True, eyes='squint'),
    'wait': dict(feet=[(0, 0), (5, 0)], knees=[(4, -23), (8, -23)], hip=(1, -46), neck=(3, -76),
                 arms=[[(-6, -61), (-8, -46)], [(14, -60), (21, -44)]], bag=(21, -44), skis=None),
}


def L(a, b, col, w, extra=''):
    return f'<line x1="{a[0]:.1f}" y1="{a[1]:.1f}" x2="{b[0]:.1f}" y2="{b[1]:.1f}" stroke="{col}" stroke-width="{w}" stroke-linecap="round" {extra}/>'


def poly(pts, col, w):
    d = ' '.join(f'{x:.1f},{y:.1f}' for x, y in pts)
    return f'<polyline points="{d}" fill="none" stroke="{col}" stroke-width="{w}" stroke-linecap="round" stroke-linejoin="round"/>'


def head_at(p, r):
    """Head centre: one radius and a bit above the neck, along the torso."""
    hx, hy = p['hip']; nx, ny = p['neck']
    dx, dy = nx - hx, ny - hy; d = math.hypot(dx, dy)
    c = (nx + dx / d * (r + 2) + p.get('head_dx', 0), ny + dy / d * (r + 2) + p.get('head_dy', 0))
    return c


def face(c, r, mood, T):
    x, y = c; ink = DAY['ink']
    e = r * .32; ey = y - r * .05
    s = []
    if mood == 'closed':
        s += [f'<path d="M{x+e-3:.1f},{ey:.1f} q3,2.5 6,0" stroke="{ink}" stroke-width="1.6" fill="none" stroke-linecap="round"/>',
              f'<path d="M{x-e-3:.1f},{ey:.1f} q3,2.5 6,0" stroke="{ink}" stroke-width="1.6" fill="none" stroke-linecap="round"/>']
    elif mood == 'squint':
        s += [L((x+e-3, ey-1.5), (x+e+3, ey+1), ink, 1.6), L((x-e+3, ey-1.5), (x-e-3, ey+1), ink, 1.6)]
    else:
        s += [f'<circle cx="{x+e:.1f}" cy="{ey:.1f}" r="1.9" fill="{ink}"/>', f'<circle cx="{x-e:.1f}" cy="{ey:.1f}" r="1.9" fill="{ink}"/>']
    my = y + r * .38
    if mood in ('happy', None):
        s.append(f'<path d="M{x-4:.1f},{my-1:.1f} q4,4 8,0" stroke="{ink}" stroke-width="1.6" fill="none" stroke-linecap="round"/>')
    elif mood == 'oops':
        s.append(f'<ellipse cx="{x:.1f}" cy="{my:.1f}" rx="2.2" ry="2.8" fill="{ink}"/>')
    elif mood == 'meh':
        s.append(L((x-3.5, my), (x+3.5, my-1), ink, 1.6))
    elif mood in ('closed',):
        s.append(f'<path d="M{x-2.5:.1f},{my:.1f} q2.5,1.5 5,0" stroke="{ink}" stroke-width="1.4" fill="none"/>')
    s.append(f'<circle cx="{x+r*.55:.1f}" cy="{my-2:.1f}" r="2.4" fill="#D1342B" opacity=".25"/>')
    return ''.join(s)


def figure(pose, skin='A', T=DAY, coat=COAT, mood=None):
    p = P[pose]; s = []
    line = T['line']
    (f1, f2), (k1, k2) = p['feet'], p['knees']
    hip, neck = p['hip'], p['neck']
    if skin == 'C':
        lc, lw, tw, arm_c, hr = T['ink'], 8, 13, T['ink'], 8.5
    elif skin == 'B':
        lc, lw, tw, arm_c, hr = T['legs'], 7, 19, coat, 15
    else:
        lc, lw, tw, arm_c, hr = T['legs'], 6.5, 17, coat, 9.5
    # skis and poles first (behind)
    if p.get('skis') is not None and skin != 'C' or (skin == 'C' and p.get('skis') is not None):
        y0 = p['skis']
        s.append(f'<path d="M-24,{y0+2} L32,{y0+2} q6,0 8,-5" fill="none" stroke="{line if skin != "C" else T["ink"]}" stroke-width="3" stroke-linecap="round"/>')
    if p.get('ground_skis'):
        s.append(L((10, 6), (52, -2), line, 3) + L((14, -4), (48, 8), line, 3))
    if p.get('pole') and skin != 'C':
        s.append(L(p['pole'][0], p['pole'][1], T['muted'], 2))
    # back leg and arm
    s.append(poly([hip, k1, f1], lc, lw))
    s.append(poly([neck, *p['arms'][0]], arm_c if skin != 'C' else T['ink'], lw - 1))
    # torso
    if skin == 'C':
        s.append(L(hip, neck, T['ink'], tw))
    else:
        s.append(L(hip, neck, coat, tw))
        mid = ((hip[0] + neck[0]) / 2, (hip[1] + neck[1]) / 2)
        s.append(L((mid[0] - 6, mid[1] + 2), (mid[0] + 6, mid[1] + 2), 'rgba(255,255,255,.4)', 3))
    s.append(poly([hip, k2, f2], lc, lw))
    if skin != 'C':
        for f in (f1, f2):
            s.append(f'<rect x="{f[0]-5:.1f}" y="{f[1]-4:.1f}" width="11" height="6" rx="1.5" fill="#1A2A40"/>')
    # head
    c = head_at(p, hr)
    if skin == 'A':
        s.append(f'<circle cx="{c[0]:.1f}" cy="{c[1]:.1f}" r="{hr}" fill="{T["helmet"]}" stroke="{DAY["ink"]}" stroke-width="2"/>')
        if pose not in ('sleep',):
            s.append(f'<rect x="{c[0]+1:.1f}" y="{c[1]-2:.1f}" width="10" height="5" rx="2" fill="{DAY["ink"]}"/><rect x="{c[0]+2:.1f}" y="{c[1]-1:.1f}" width="8" height="2" fill="{coat}"/>')
        else:
            s.append(f'<rect x="{c[0]-3:.1f}" y="{c[1]-9:.1f}" width="11" height="4" rx="2" fill="{DAY["ink"]}"/>')  # goggles pushed up
        if pose in ('carve', 'tea', 'wave', 'cheer'):
            sx = c[0] - hr * .4; sy = c[1] + hr * .9
            s.append(f'<path d="M{sx:.1f},{sy:.1f} q-9,2 -17,{-3 if pose=="carve" else 6}" stroke="{coat}" stroke-width="4" fill="none" stroke-linecap="round"/>')
    elif skin == 'B':
        s.append(f'<circle cx="{c[0]:.1f}" cy="{c[1]:.1f}" r="{hr}" fill="#F6D7BE" stroke="{DAY["ink"]}" stroke-width="2"/>')
        s.append(face(c, hr, p.get('eyes') or mood or 'happy', T))
        s.append(f'<path d="M{c[0]-hr-1:.1f},{c[1]-2:.1f} a{hr+1},{hr+1} 0 0 1 {2*hr+2:.1f},0 z" fill="{coat}" stroke="{DAY["ink"]}" stroke-width="2"/>')
        s.append(f'<rect x="{c[0]-hr-2:.1f}" y="{c[1]-5:.1f}" width="{2*hr+4:.1f}" height="5" rx="2" fill="{T["snow"]}" stroke="{DAY["ink"]}" stroke-width="1.5"/>')
        s.append(f'<circle cx="{c[0]:.1f}" cy="{c[1]-hr-4:.1f}" r="4.5" fill="{T["snow"]}" stroke="{DAY["ink"]}" stroke-width="1.5"/>')
        if pose not in ('sleep', 'brush'):
            s.append(f'<rect x="{c[0]-hr*.7:.1f}" y="{c[1]-hr*.95:.1f}" width="{hr*1.4:.1f}" height="5" rx="2.5" fill="{DAY["ink"]}"/>')  # goggles on the hat
        s.append(f'<path d="M{c[0]-hr*.7:.1f},{c[1]+hr*.95:.1f} q{hr*.7},5 {hr*1.4:.1f},0" stroke="{coat}" stroke-width="5" fill="none" stroke-linecap="round"/>')
    else:
        s.append(f'<circle cx="{c[0]:.1f}" cy="{c[1]:.1f}" r="{hr}" fill="{T["ink"]}"/>')
    s.append(poly([neck, *p['arms'][1]], arm_c if skin != 'C' else T['ink'], lw - 1))
    # props
    if p.get('cup'):
        x, y = p['cup']; s.append(f'<rect x="{x-4:.1f}" y="{y-8:.1f}" width="9" height="10" rx="2" fill="{T["paper"]}" stroke="{DAY["ink"]}" stroke-width="1.5"/>'
                                  f'<path d="M{x-1:.1f},{y-11:.1f} q2,-4 0,-7 M{x+3:.1f},{y-11:.1f} q2,-4 0,-7" stroke="{T["muted"]}" stroke-width="1.2" fill="none"/>')
    if p.get('bag'):
        x, y = p['bag']; s.append(f'<rect x="{x-9:.1f}" y="{y+2:.1f}" width="22" height="30" rx="3" fill="{COATS[1]}" stroke="{DAY["ink"]}" stroke-width="2"/>'
                                  f'<rect x="{x-4:.1f}" y="{y+14:.1f}" width="12" height="6" fill="{T["paper"]}" opacity=".8"/>'
                                  f'<circle cx="{x-5:.1f}" cy="{y+35:.1f}" r="2.5" fill="{DAY["ink"]}"/><circle cx="{x+9:.1f}" cy="{y+35:.1f}" r="2.5" fill="{DAY["ink"]}"/>')
    if p.get('zz'):
        s.append(f'<text x="{c[0]+14:.1f}" y="{c[1]-14:.1f}" font-family="Karantina" font-weight="700" font-size="16" fill="{T["muted"]}">z</text>'
                 f'<text x="{c[0]+22:.1f}" y="{c[1]-25:.1f}" font-family="Karantina" font-weight="700" font-size="22" fill="{T["muted"]}">Z</text>')
    if p.get('spray'):
        for i, (dx, dy, r) in enumerate([(-30, -6, 5), (-38, -12, 4), (-44, -4, 3.5), (-50, -14, 3), (-34, -18, 2.5)]):
            s.append(f'<circle cx="{dx}" cy="{dy}" r="{r}" fill="{T["snow"]}" stroke="{T["shade"]}" stroke-width="1"/>')
    if p.get('puff'):
        for dx, dy, r in [(-4, -66, 4), (8, -70, 3), (-10, -58, 2.5), (24, -12, 6), (34, -4, 5), (-18, -4, 5), (44, -10, 4)]:
            s.append(f'<circle cx="{dx}" cy="{dy}" r="{r}" fill="{T["snow"]}" stroke="{T["shade"]}" stroke-width="1"/>')
    g = ''.join(s)
    if p.get('tilt'):
        g = f'<g transform="rotate({p["tilt"]})">{g}</g>'
    return g


def svg(inner, w, h, vb=None):
    vb = vb or f'0 0 {w} {h}'
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="{vb}" direction="ltr">{inner}</svg>'


def place(fig, x, y, k=1.0):
    return f'<g transform="translate({x},{y}) scale({k})">{fig}</g>'


def ground(T, x0, x1, y):
    return f'<path d="M{x0},{y} Q{(x0+x1)/2},{y-6} {x1},{y} L{x1},{y+30} L{x0},{y+30}z" fill="{T["snow"]}" stroke="{T["shade"]}" stroke-width="1.5"/>'


CSS = """
@import url('https://fonts.googleapis.com/css2?family=Karantina:wght@700&family=IBM+Plex+Sans+Hebrew:wght@400;600;700&family=IBM+Plex+Sans:wght@400;600&display=swap');
*{box-sizing:border-box;margin:0}
body{font-family:'IBM Plex Sans Hebrew',sans-serif;background:var(--bg);color:var(--ink);direction:rtl;padding:28px;width:max-content}
h1{font-family:Karantina;font-weight:700;font-size:44px;line-height:1;margin-bottom:6px}
h2{font-family:Karantina;font-weight:700;font-size:30px;line-height:1}
p.sub{color:var(--muted);font-size:15px;max-width:1100px;margin-bottom:20px;line-height:1.5}
.row{display:flex;gap:18px;align-items:stretch}
.card{background:var(--paper);border:1px solid var(--rule);padding:16px;display:flex;flex-direction:column;gap:8px}
.note{font-size:13.5px;color:var(--muted);line-height:1.45}
.tag{align-self:flex-start;display:inline-block;font-size:12px;font-weight:600;padding:2px 8px;border:1px solid var(--rule);color:var(--muted)}
.rec{border:2px solid var(--blue)}
.lbl{font-size:13px;font-weight:600;text-align:center}
"""


def page(name, title, sub, body, T=DAY):
    vars_ = f"--bg:{T['bg']};--paper:{T['paper']};--ink:{T['ink']};--muted:{T['muted']};--rule:{T['rule']};--blue:#1F5FC4"
    html = f'<!doctype html><html lang="he" dir="rtl"><head><meta charset="utf-8"><title>{title}</title><style>:root{{{vars_}}}{CSS}</style></head>' \
           f'<body><h1>{title}</h1><p class="sub">{sub}</p>{body}</body></html>'
    (out / f'{name}.html').write_text(html, encoding='utf-8')


# ---------- 1. three directions ----------
DIRS = [
    ('A', 'א. הגולש של הירידה', 'אותו גולש כמו במשחק הירידה: קסדה, משקפי סקי, מעיל ויריעת צעיף. בלי פנים: האופי בגוף, בתנועה ובמשקפיים (על העיניים או על הקסדה). עולם אחד עם המשחקים, ולא ילדותי.',
     ['עולם אחד עם המשחקים (מעצב המשחקים)', 'בוגר, מתאים לנתונים', 'הכי זול לתנועה: השלד כבר בקוד'], ['פחות הבעה בלי פנים']),
    ('B', 'ב. כובע גרב', 'אותו שלד, ראש גדול יותר, כובע גרב עם פונפון בצבע המעיל ופנים פשוטות: שתי נקודות ופה. הכי הרבה הבעה וחום.',
     ['הבעות ברורות ("פה נפלת", תה)', 'הכי חם ומשעשע'], ['קרוב לילדותי', 'שונה מהדמויות במשחקים']),
    ('C', 'ג. פיקטוגרמה', 'כמו הדמויות בשלטים של אתרי סקי: דמות מלאה בצבע הדיו, בלי פנים ובלי פרטים. יושב טבעי על השלטים של השפה שלנו.',
     ['הכי קרוב לשפת השלטים', 'שקט, לא מתחרה במידע'], ['כמעט בלי אופי', 'לא מתאים לבדיחה']),
]
cols = []
for d, name, desc, plus, minus in DIRS:
    figs = ''.join(place(figure(pz, d), x, 150, 1.25) for pz, x in (('stand', 70), ('wave', 175), ('carve', 290)))
    art = svg(ground(DAY, 0, 380, 152) + figs, 380, 190)
    nightfigs = ''.join(place(figure(pz, d, NIGHT), x, y, .9) for pz, x, y in (('stand', 70, 110), ('sit', 185, 62), ('cheer', 300, 110)))
    night = svg(f'<rect width="380" height="140" fill="{NIGHT["bg"]}"/>' + ground(NIGHT, 0, 380, 112) +
                f'<rect x="150" y="62" width="90" height="50" fill="{NIGHT["paper"]}"/>' + nightfigs, 380, 140)
    rec = ' rec' if d == 'A' else ''
    cols.append(f'<div class="card{rec}" style="width:412px"><h2>{name}</h2>{"<span class=tag>מומלץ</span>" if d == "A" else ""}'
                f'{art}<div class="note">{desc}</div>{night}<div class="lbl">בלילה, בטוקנים של הלילה</div>'
                f'<div class="note">+ {" · ".join(plus)}<br>− {" · ".join(minus)}</div></div>')
page('01-directions', 'דמות מלווה: שלושה כיוונים', 'סקיצות כיוון, לא ציור סופי: הציור הסופי אחרי הבחירה בשפה העיצובית (מעצב המוצר). אותו שלד ואותן תנוחות בשלושתם, כדי שההשוואה תהיה הוגנת. המעיל הוא "המעיל שלי" מהמשחקים (כאן הכתום, ברירת המחדל).',
     f'<div class="row">{"".join(cols)}</div>')

# ---------- 2. poses x directions ----------
POSES = [('stand', 'עומד'), ('wave', 'מנופף'), ('carve', 'גולש'), ('cheer', 'מריע'), ('shrug', 'מושך בכתפיים'),
         ('sit', 'יושב'), ('sleep', 'ישן'), ('tea', 'תה בבקתה'), ('brush', 'מנער שלג'), ('wait', 'מחכה עם תיק')]
rows = []
for d, name, *_ in DIRS:
    cells = ''
    for pz, lbl in POSES:
        seat = P[pz].get('seat')
        base = (f'<rect x="10" y="{118}" width="100" height="40" fill="{DAY["paper"]}" stroke="{DAY["rule"]}"/>' if seat else ground(DAY, 0, 120, 132))
        y = 118 if seat else 130
        cells += f'<div style="text-align:center">{svg(base + place(figure(pz, d), 52, y, 1.0), 120, 170)}<div class="lbl">{lbl}</div></div>'
    rows.append(f'<div class="card" style="flex-direction:row;gap:6px;align-items:center"><h2 style="width:150px">{name}</h2>{cells}</div>')
COAT_ROW = ''.join(place(figure('stand', 'A', coat=c), 40 + i * 70, 120, .95) for i, c in enumerate(COATS))
page('02-poses', 'דמות מלווה: התנוחות', 'עשר תנוחות לכל הרגעים שהומלצו, באותו שלד. בכיוון ב גם הבעות פנים (שמח, עיניים עצומות, מצמוץ); בכיוון א המשקפיים עולות על הקסדה כשנחים. בשורה האחרונה: ששת המעילים של המשחקים, כי המעיל הוא "המעיל שלי".',
     '<div style="display:flex;flex-direction:column;gap:14px">' + ''.join(rows) +
     f'<div class="card" style="align-self:flex-start"><h2>ששת המעילים</h2>{svg(ground(DAY, 0, 460, 122) + COAT_ROW, 460, 150)}</div></div>')

# ---------- 3. home: the entrance on the landscape ----------
def ridge(T, w=230, h=150, night=False):
    sky = '#16223A' if night else '#BFD8EE'
    return (f'<rect width="{w}" height="{h}" fill="{sky}"/>'
            f'<path d="M0,95 L40,62 L70,78 L110,40 L150,70 L185,52 L{w},80 L{w},{h} L0,{h}z" fill="{"#34496E" if night else "#E2ECF6"}"/>'
            f'<path d="M0,112 C60,100 120,104 {w},92 L{w},{h} L0,{h}z" fill="{T["snow"]}"/>')
frames = [(40, 70, .55, 'carve', 'נכנס מלמעלה'), (95, 92, .62, 'carve', 'קשת אחת'), (150, 108, .7, 'carve', 'בולם עם ענן שלג'), (160, 112, .7, 'wave', 'מנופף פעם אחת ונשאר')]
fs = ''
for i, (x, y, k, pz, lbl) in enumerate(frames):
    trail = f'<path d="M30,60 Q{x-30},{y-10} {x},{y}" stroke="{DAY["shade"]}" stroke-width="2" fill="none" stroke-dasharray="3 3"/>' if i else ''
    fs += f'<div style="text-align:center">{svg(ridge(DAY) + trail + place(figure(pz, "A"), x, y, k), 230, 150)}<div class="lbl">{i+1}. {lbl}</div></div>'
static = svg(ridge(DAY) + place(figure('wave', 'A'), 160, 112, .7), 230, 150)
nightf = svg(ridge(NIGHT, night=True) + place(figure('wave', 'A', NIGHT), 160, 112, .7), 230, 150)
tap = svg(ridge(DAY) + place(figure('cheer', 'A'), 160, 108, .7) +
          f'<circle cx="190" cy="40" r="5" fill="{COAT}"/><path d="M185,48 q5,-14 12,-4" stroke="{COAT}" stroke-width="2" fill="none"/>', 230, 150)
page('03-home', 'דף הבית: הכניסה על הנוף', 'פעם אחת בכל פתיחה, כשתי שניות, ולא לולאה. הגולש יורד ברכס שמעל הכרטיס, בולם ונשאר עומד ליד הספח. נגיעה בו: קפיצה קטנה, ולפעמים הכובע עף (הרעיון מ-30.9). הנוף כאן סכמטי; בפועל הנוף האמיתי מהכפר.',
     f'<div class="row">{fs}</div><div class="row" style="margin-top:16px">'
     f'<div class="card"><div class="lbl">הפחתת תנועה: התמונה האחרונה בלבד</div>{static}</div>'
     f'<div class="card"><div class="lbl">בלילה</div>{nightf}</div>'
     f'<div class="card"><div class="lbl">נגיעה בו: קפיצה, והכובע עף</div>{tap}</div>'
     f'<div class="card" style="width:300px"><div class="note">לא מכסה את הכרטיס, את השעון ואת עמוד השלטים. קישוט לקורא המסך. מכובה במתג "דמות" בהגדרות. נעצר כשהמסך לא נראה.</div></div></div>')

# ---------- 4. empty states ----------
def sign(T, x, y, w, h, text, col='#13233A', fg='#FFFFFF'):
    return (f'<path d="M{x},{y} L{x+w},{y} L{x+w},{y+h} L{x},{y+h} L{x-14},{y+h/2}z" fill="{col}"/>'
            f'<path d="M{x-6},{y+2} q{w/4},-9 {w/2},-3 q{w/4},-7 {w/2+6},2 L{x+w},{y+4} L{x-4},{y+4}z" fill="{T["snow"]}" stroke="{T["shade"]}" stroke-width="1"/>'
            f'<text x="{x+w/2}" y="{y+h/2+9}" text-anchor="middle" font-family="Karantina" font-weight="700" font-size="26" fill="{fg}" direction="rtl">{text}</text>'
            f'<rect x="{x+w/2-3}" y="{y+h}" width="6" height="70" fill="{T["legs"]}"/>')
e1 = svg(f'<rect width="300" height="230" fill="{DAY["bg"]}"/>' + ground(DAY, 0, 300, 200) + sign(DAY, 70, 70, 180, 50, 'ההר עוד ישן') +
         place(figure('sleep', 'A'), 150, 66, .85), 300, 230)
e1n = svg(f'<rect width="300" height="230" fill="{NIGHT["bg"]}"/>' + ground(NIGHT, 0, 300, 200) + sign(NIGHT, 70, 70, 180, 50, 'ההר עוד ישן', '#34496E', NIGHT['ink']) +
          place(figure('sleep', 'A', NIGHT), 150, 66, .85), 300, 230)
e2 = svg(f'<rect width="300" height="230" fill="{DAY["bg"]}"/>'
         f'<rect x="30" y="40" width="240" height="120" fill="{DAY["paper"]}" stroke="{DAY["rule"]}" transform="rotate(-2 150 100)"/>'
         f'<text x="150" y="100" text-anchor="middle" font-family="Karantina" font-weight="700" font-size="32" fill="{DAY["ink"]}">מתי טסים?</text>'
         + place(figure('wait', 'A'), 60, 205, .8) + ground(DAY, 0, 300, 206), 300, 230)
e3 = svg(f'<rect width="300" height="230" fill="{DAY["bg"]}"/>'
         f'<path d="M30,40 L270,60" stroke="{DAY["ink"]}" stroke-width="2"/>'
         f'<rect x="125" y="50" width="70" height="8" fill="{DAY["ink"]}"/><path d="M160,58 L160,80" stroke="{DAY["ink"]}" stroke-width="3"/>'
         f'<rect x="120" y="80" width="80" height="6" rx="2" fill="{DAY["ink"]}"/>'
         + place(figure('sit', 'A'), 140, 80, .8) +
         f'<text x="150" y="190" text-anchor="middle" direction="rtl" font-size="15" font-weight="600" fill="{DAY["muted"]}" font-family="IBM Plex Sans Hebrew, IBM Plex Sans, sans-serif">עוד אין חברים בקבוצה</text>', 300, 230)
page('04-empty', 'מצבים ריקים ו"אין מידע"', 'הדמות לא מוסיפה מילים: הטקסט הוא של המוצר, כמו היום. היא רק מראה את המצב: ישנה על השלט המושלג כשההר עוד ישן, מחכה עם תיק כשאין טיסה, ולבד על כיסא הרכבל כשהקבוצה ריקה.',
     f'<div class="row"><div class="card">{e1}<div class="lbl">ההר עוד ישן</div></div><div class="card">{e1n}<div class="lbl">ההר עוד ישן, בלילה</div></div>'
     f'<div class="card">{e2}<div class="lbl">בלי טיסה</div></div><div class="card">{e3}<div class="lbl">קבוצה ריקה</div></div></div>')

# ---------- 5. ski day: the hut and the end of the day ----------
def phone(inner, h=560, T=DAY):
    return svg(f'<rect width="300" height="{h}" rx="22" fill="{T["bg"]}" stroke="{T["rule"]}" stroke-width="2"/>{inner}', 300, h)
def row(T, y, t, sub, col):
    return (f'<rect x="18" y="{y}" width="264" height="54" fill="{T["paper"]}" stroke="{T["rule"]}"/>'
            f'<rect x="270" y="{y}" width="12" height="54" fill="{col}"/>'
            f'<text x="258" y="{y+24}" text-anchor="start" direction="rtl" font-size="15" font-weight="700" fill="{T["ink"]}" font-family="IBM Plex Sans Hebrew, IBM Plex Sans, sans-serif">{t}</text>'
            f'<text x="258" y="{y+43}" text-anchor="start" direction="rtl" font-size="12.5" fill="{T["muted"]}" font-family="IBM Plex Sans Hebrew, IBM Plex Sans, sans-serif">{sub}</text>')
hut = phone(f'<text x="282" y="44" text-anchor="start" direction="rtl" font-family="Karantina" font-weight="700" font-size="30" fill="{DAY["ink"]}">היום · ציר זמן</text>'
            + row(DAY, 64, 'רכבל Goodaura', '10:42 · 7 דק׳', '#13233A') + row(DAY, 126, 'Tatra 1 ← Tatra 2', '10:49 · 4:12 · 61 קמ״ש', '#1F5FC4')
            + f'<rect x="18" y="188" width="264" height="120" fill="#FFF6E8" stroke="{DAY["rule"]}"/>'
            f'<text x="258" y="214" text-anchor="start" direction="rtl" font-size="15" font-weight="700" fill="{DAY["ink"]}" font-family="IBM Plex Sans Hebrew, IBM Plex Sans, sans-serif">הפסקה ליד Goodaura העליונה</text>'
            f'<text x="258" y="234" text-anchor="start" direction="rtl" font-size="12.5" fill="{DAY["muted"]}" font-family="IBM Plex Sans Hebrew, IBM Plex Sans, sans-serif">11:20 · 25 דק׳</text>'
            + place(figure('tea', 'A'), 70, 290, .8) + f'<rect x="30" y="290" width="80" height="8" fill="#8A5A3B"/>'
            + row(DAY, 320, 'רכבל Sadzele', '11:47 · 9 דק׳', '#13233A'), 400)
endday = phone(f'<rect x="20" y="70" width="260" height="300" fill="{DAY["paper"]}" stroke="{DAY["rule"]}" transform="rotate(-2 150 220)"/>'
               f'<text x="262" y="112" text-anchor="start" direction="rtl" font-family="Karantina" font-weight="700" font-size="34" fill="{DAY["ink"]}">היום שלך</text>'
               f'<text x="262" y="134" text-anchor="start" direction="rtl" font-size="13" fill="{DAY["muted"]}" font-family="IBM Plex Sans Hebrew, IBM Plex Sans, sans-serif">12.1 · גודאורי</text>'
               + ''.join(f'<text x="{x}" y="200" text-anchor="middle" font-family="Karantina" font-weight="700" font-size="40" fill="{DAY["ink"]}">{v}</text>'
                         f'<text x="{x}" y="220" text-anchor="middle" direction="rtl" font-size="12" fill="{DAY["muted"]}" font-family="IBM Plex Sans Hebrew, IBM Plex Sans, sans-serif">{t}</text>'
                         for x, v, t in ((225, '68', 'קמ״ש מרבית'), (150, '24.6', 'ק״מ גלישה'), (75, '11', 'ירידות')))
               + f'<path d="M50,330 C90,260 140,300 170,250 S240,280 250,230" stroke="{COAT}" stroke-width="4" fill="none"/>'
               + place(figure('sit', 'A'), 228, 74, .85), 420)
page('05-skiday', 'יום הגלישה: הבקתה וסוף היום', 'רק כשיש זמן להסתכל: בהפסקה בציר הזמן הדמות שותה תה, ובסוף היום יושבת על קצה הכרטיס "היום שלך". המספרים והשמות בקול המוצר, הדמות לא אומרת אותם (המיתוג). לא במסך החי, לא בהתראה ולא ב-Live Activity (מעצב המוצר). המספרים לדוגמה.',
     f'<div class="row"><div class="card">{hut}<div class="lbl">הפסקה בציר הזמן</div></div><div class="card">{endday}<div class="lbl">סוף היום</div></div></div>')

# ---------- 6. "you fell here": two versions ----------
def fallcard(T, title, sub, words):
    return phone(f'<rect x="18" y="40" width="264" height="300" fill="{T["paper"]}" stroke="{T["rule"]}"/>'
                 f'<rect x="18" y="40" width="264" height="150" fill="{T["bg"]}"/>'
                 f'<path d="M40,80 C90,110 120,90 150,130 S210,160 260,170" stroke="#1F5FC4" stroke-width="4" fill="none"/>'
                 f'<circle cx="160" cy="138" r="9" fill="none" stroke="{T["ink"]}" stroke-width="2.5"/>'
                 + place(figure('brush', 'B' if words == 'B' else 'A', T, mood='oops'), 100, 184, .95) +
                 f'<text x="266" y="222" text-anchor="start" direction="rtl" font-size="16" font-weight="700" fill="{T["ink"]}" font-family="IBM Plex Sans Hebrew, IBM Plex Sans, sans-serif">{title}</text>'
                 f'<text x="266" y="244" text-anchor="start" direction="rtl" font-size="13" fill="{T["muted"]}" font-family="IBM Plex Sans Hebrew, IBM Plex Sans, sans-serif">{sub}</text>'
                 f'<rect x="150" y="270" width="116" height="44" fill="{T["paper"]}" stroke="{T["ink"]}" stroke-width="2"/>'
                 f'<text x="208" y="298" text-anchor="middle" direction="rtl" font-size="14" font-weight="600" fill="{T["ink"]}" font-family="IBM Plex Sans Hebrew, IBM Plex Sans, sans-serif">לא נפלתי</text>', 360)
va = fallcard(DAY, 'נראה שנשקת לשלג כאן?', 'Tatra 2 · 11:42', 'A')
vb = fallcard(DAY, 'עצירה חדה כאן', 'Tatra 2 · 11:42', 'A')
vbn = fallcard(NIGHT, 'עצירה חדה כאן', 'Tatra 2 · 11:42', 'A')
vab = fallcard(DAY, 'נראה שנשקת לשלג כאן?', 'Tatra 2 · 11:42', 'B')
page('06-fall', '"פה נפלת": שתי הגרסאות, לבחירה של פיני', 'א. הבדיחה בטקסט, כמו שפיני ביקש ("נראה שנשקת לשלג כאן?"). ב. ניטרלי, כמו שהמיתוג ממליץ: המוצר כותב "עצירה חדה כאן", וההומור כולו בתנועה, הדמות מנערת שלג בלי מילים. בשתיהן "לא נפלתי" מוחק, ואף אחת לא מבטיחה בטיחות. ההמלצה שלי: ב (ההומור בלי לצחוק על מישהו שאולי נפל באמת).',
     f'<div class="row"><div class="card">{va}<div class="lbl">א. בדיחה בטקסט</div></div><div class="card rec">{vb}<div class="lbl">ב. ניטרלי, הדמות מנערת שלג (מומלץ)</div></div>'
     f'<div class="card">{vbn}<div class="lbl">ב. בלילה</div></div><div class="card">{vab}<div class="lbl">א. בכיוון "כובע גרב", להשוואה</div></div></div>')

# ---------- 7. games: the end screen ----------
def stars(n):
    return ''.join(f'<text x="{110+i*40}" y="120" text-anchor="middle" direction="rtl" font-size="34" fill="{"#F4B942" if i < n else DAY["rule"]}">★</text>' for i in range(3))
win = phone(f'<text x="150" y="70" text-anchor="middle" font-family="Karantina" font-weight="700" font-size="34" fill="{DAY["ink"]}">הגעתם לרכבל!</text>' + stars(3)
            + ground(DAY, 0, 300, 300) + place(figure('cheer', 'A'), 150, 298, 1.1) +
            f'<text x="150" y="352" text-anchor="middle" direction="rtl" font-size="14" fill="{DAY["muted"]}" font-family="IBM Plex Sans Hebrew, IBM Plex Sans, sans-serif">שיא חדש במסלול!</text>', 400)
lose = phone(f'<text x="150" y="70" text-anchor="middle" font-family="Karantina" font-weight="700" font-size="34" fill="{DAY["ink"]}">המפולת תפסה אותך</text>' + stars(0)
             + ground(DAY, 0, 300, 300) + place(figure('shrug', 'A'), 110, 298, 1.1) +
             f'<rect x="170" y="190" width="112" height="70" fill="{DAY["paper"]}" stroke="{DAY["rule"]}"/>'
             f'<text x="270" y="216" text-anchor="start" direction="rtl" font-size="13" font-weight="700" fill="{DAY["ink"]}" font-family="IBM Plex Sans Hebrew, IBM Plex Sans, sans-serif">טיפ</text>'
             f'<text x="270" y="238" text-anchor="start" direction="rtl" font-size="12.5" fill="{DAY["muted"]}" font-family="IBM Plex Sans Hebrew, IBM Plex Sans, sans-serif">לפני סלע: לשחרר</text>'
             f'<text x="270" y="254" text-anchor="start" direction="rtl" font-size="12.5" fill="{DAY["muted"]}" font-family="IBM Plex Sans Hebrew, IBM Plex Sans, sans-serif">ולקפוץ</text>', 400)
first = phone(f'<text x="150" y="60" text-anchor="middle" font-family="Karantina" font-weight="700" font-size="30" fill="{DAY["ink"]}">הפעם הראשונה</text>'
              + ground(DAY, 0, 300, 230) + place(figure('carve', 'A'), 90, 226, 1.0) + place(figure('cheer', 'A'), 210, 222, 1.0) +
              f'<path d="M120,150 q30,-30 60,0" stroke="{DAY["muted"]}" stroke-width="2" fill="none" stroke-dasharray="4 4"/>'
              f'<text x="90" y="290" text-anchor="middle" direction="rtl" font-size="13" fill="{DAY["muted"]}" font-family="IBM Plex Sans Hebrew, IBM Plex Sans, sans-serif">מחזיקים</text>'
              f'<text x="210" y="290" text-anchor="middle" direction="rtl" font-size="13" fill="{DAY["muted"]}" font-family="IBM Plex Sans Hebrew, IBM Plex Sans, sans-serif">משחררים</text>', 330)
page('07-games', 'המשחקים: מסך הסיום והפעם הראשונה', 'אף פעם לא בזמן קלט (מעצב המשחקים). בניצחון: מריע לכוכבים. בהפסד: מושך בכתפיים, והטיפ בקול המוצר לידו (המיתוג: הדמות לא מסבירה). בפעם הראשונה: מדגים את התנועה במקום קיר טקסט. עד שתי שניות, ואפשר לדלג.',
     f'<div class="row"><div class="card">{win}<div class="lbl">שלושה כוכבים</div></div><div class="card">{lose}<div class="lbl">הפסד, עם טיפ</div></div><div class="card">{first}<div class="lbl">הדגמה בפעם הראשונה</div></div></div>')

print('boards:', ', '.join(sorted(p.stem for p in out.glob('*.html'))))
