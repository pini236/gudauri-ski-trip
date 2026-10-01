#!/usr/bin/env python3
"""Round 8 design screens.

Games page (Pini: "doesn't look good, find something nicer; e.g. fresh snow on top of every card"):
  GP1 snow caps on the cards, GP2 trail signs on a post, GP3 ski passes on a rope, GP4 GP1 on a desktop.
Meeting point (Pini: nothing selected by default, and a way to clear the selection):
  MP1 the empty start, MP2 a station picked with the ways to clear it, MP3 the moment it clears.
The thumbnails are the real ones from site/games/thumbs/, uploaded to the canvas.
Run from the repo root: python3 design/round8/build.py"""
import math, pathlib, random
root = pathlib.Path(__file__).resolve().parent.parent.parent

C = {'snow': '#EEF2F5', 'paper': '#FFFFFF', 'ink': '#13233A', 'muted': '#4B5A6F', 'rule': '#CBD5DF', 'dash': '#8E9CAD',
     'blue': '#1F5FC4', 'green': '#1B8A4C', 'red': '#D1342B', 'gold': '#F4B942', 'sky': '#DCE8F1'}
DISP = "font-family: Karantina, 'Arial Narrow', sans-serif; font-weight: 700;"
BODY = "font-family: 'IBM Plex Sans Hebrew', 'Segoe UI', system-ui, sans-serif;"
FONTS = '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Karantina:wght@700&amp;family=IBM+Plex+Sans+Hebrew:wght@400;500;600;700&amp;display=swap">'

GAMES = [  # slug, title, tag, meta, colour, thumb
    ('descent', 'הירידה של החבר׳ה', 'הירידה על המסלולים האמיתיים', '5 מסלולים', C['blue'], '/_blob/869f4dcfdcf167b0e62e41540b19b2e3'),
    ('school', 'בית הספר לסקי', 'מעצירה בפיצה ועד קרווינג', '7 שיעורים', C['green'], '/_blob/7695e03bef0935139456a245734c6146'),
    ('fresh', 'שלג טרי', 'לגעת בשלג ולרסק אגם קפוא', 'בלי ניקוד', '#5B9BFF', '/_blob/3f8df6762c988568664dd3113cf87fc4'),
    ('snowball', 'קרב כדורי שלג', 'מאחורי החומה, מול החבר׳ה', '5 יריבים', C['ink'], '/_blob/3d1124f2c965e738d86a43515c74bb6a'),
    ('merge', 'איחוד כדורי שלג', 'מפתית ועד מלך קזבק', 'משחק קצר', C['gold'], '/_blob/51127cc8c6ebf501548c3f458eb2d41f'),
]
PLAY = '<svg width="30" height="30" viewBox="0 0 34 34" aria-hidden="true"><circle cx="17" cy="17" r="16" fill="#13233A" opacity=".82"></circle><path d="M21.5 11.5v11L12.5 17z" fill="#fff"></path></svg>'


def page(title, body, w=390, h=844, css=''):
    return f'''<!doctype html>
<html lang="he" dir="rtl">
<head>
<meta charset="utf-8">
<title>{title}</title>
<script src="./support.js"></script>
</head>
<body>
<x-dc>
<helmet>
{FONTS}
<style>
body{{margin:0;background:{C['snow']}}}
{css}
</style>
</helmet>
{body}
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{{"$preview":{{"width":{w},"height":{h}}}}}'>
class Component extends DCLogic {{
  renderVals() {{ return {{}}; }}
}}
</script>
</body>
</html>
'''


def cap(w, seed, h=26, drips=True):
    """A drift of fresh snow lying on a top edge: soft lumps, a lip that hangs over, a few drops."""
    rnd = random.Random(seed)
    n = max(4, int(w / 34))
    xs = [i * w / n for i in range(n + 1)]
    pts = [(x, 6 + rnd.random() * 8) for x in xs]
    d = f'M0 {h} L0 {pts[0][1]:.1f} '
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        mx = (x0 + x1) / 2
        d += f'Q{mx:.1f} {min(y0, y1) - 6 - rnd.random() * 6:.1f} {x1:.1f} {y1:.1f} '
    # the underside: where the snow overhangs the edge, with drops
    d += f'L{w} {h - 6} '
    x = w
    while x > 0:
        step = 18 + rnd.random() * 30
        nx = max(0, x - step)
        if drips and rnd.random() < .35 and nx > 8:
            dx = (x + nx) / 2
            d += f'L{dx + 5:.1f} {h - 6} Q{dx + 4:.1f} {h + 6 + rnd.random() * 6:.1f} {dx:.1f} {h + 8 + rnd.random() * 5:.1f} Q{dx - 4:.1f} {h + 6:.1f} {dx - 5:.1f} {h - 6} '
        d += f'Q{(x + nx) / 2:.1f} {h - 2 + rnd.random() * 4:.1f} {nx:.1f} {h - 6} '
        x = nx
    d += 'Z'
    return (f'<svg width="{w}" height="{h + 16}" viewBox="0 0 {w} {h + 16}" aria-hidden="true" style="position: absolute; left: 0; top: -{h - 8}px; overflow: visible; filter: drop-shadow(0 2px 2px rgba(19,35,58,.18)); pointer-events: none">'
            f'<path d="{d}" fill="#FFFFFF"></path>'
            f'<path d="{d}" fill="none" stroke="#C9D8E8" stroke-width="1"></path></svg>')


def flakes(w, h, n, seed, op=.9):
    rnd = random.Random(seed)
    return ''.join(f'<span style="position: absolute; left: {rnd.random() * w:.0f}px; top: {rnd.random() * h:.0f}px; width: {s}px; height: {s}px; border-radius: 50%; background: #FFFFFF; opacity: {op}"></span>'
                   for s in [rnd.choice([2, 3, 3, 4]) for _ in range(n)])


def head(sub='5 משחקים · השיאים נשמרים רק בטלפון שלכם'):
    return (f'<div style="display: flex; justify-content: space-between; align-items: center; padding: 14px 16px 4px">'
            f'<span style="{DISP} font-size: 46px; line-height: 1">משחקים</span>'
            f'<a href="#" style="font-size: 15px; font-weight: 700; color: {C["blue"]}; text-decoration: none">→ בית</a></div>'
            f'<div style="padding: 0 16px 10px; font-size: 13.5px; color: {C["muted"]}">{sub}</div>')


# ---------------------------------------------------------------- GP1: fresh snow on every card
def gp1_card(g, i, feature=False):
    slug, title, tag, meta, col, img = g
    if feature:
        return (f'<a href="#" style="position: relative; display: flex; flex-direction: column; background: {C["paper"]}; color: {C["ink"]}; text-decoration: none; box-shadow: 0 6px 16px rgba(19,35,58,.14); border-bottom: 6px solid {col}">'
                f'{cap(358, 11 + i, 30)}'
                f'<span style="position: relative; display: block; height: 170px; overflow: hidden"><img src="{img}" alt="" style="width: 100%; height: 100%; object-fit: cover; object-position: 30% 60%">'
                f'<span style="position: absolute; left: 10px; bottom: 10px">{PLAY}</span>'
                f'<span style="position: absolute; right: 0; bottom: 12px; background: {col}; color: #fff; font-size: 12px; font-weight: 700; padding: 3px 12px 3px 16px; clip-path: polygon(8px 0, 100% 0, 100% 100%, 8px 100%, 0 50%)">{meta}</span></span>'
                f'<span style="display: flex; flex-direction: column; gap: 2px; padding: 10px 14px 12px"><b style="{DISP} font-size: 34px; line-height: 1">{title}</b>'
                f'<span style="font-size: 13px; color: {C["muted"]}">{tag}</span></span></a>')
    return (f'<a href="#" style="position: relative; display: flex; flex-direction: column; background: {C["paper"]}; color: {C["ink"]}; text-decoration: none; box-shadow: 0 6px 14px rgba(19,35,58,.12); border-bottom: 5px solid {col}">'
            f'{cap(172, 20 + i, 24)}'
            f'<span style="position: relative; display: block; height: 118px; overflow: hidden"><img src="{img}" alt="" style="width: 100%; height: 100%; object-fit: cover">'
            f'<span style="position: absolute; left: 8px; bottom: 8px">{PLAY}</span></span>'
            f'<span style="display: flex; flex-direction: column; gap: 2px; padding: 8px 10px 10px"><b style="{DISP} font-size: 25px; line-height: 1">{title}</b>'
            f'<span style="font-size: 12px; color: {C["muted"]}">{meta} · {tag}</span></span></a>')


def gp1():
    cards = gp1_card(GAMES[0], 0, True) + '<div style="display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 34px 14px">' + ''.join(gp1_card(g, i) for i, g in enumerate(GAMES[1:])) + '</div>'
    return (f'<div style="width: 390px; height: 1010px; box-sizing: border-box; position: relative; overflow: hidden; background: linear-gradient({C["sky"]}, {C["snow"]} 40%); color: {C["ink"]}; {BODY} direction: rtl">'
            f'{flakes(390, 1010, 70, 3)}'
            f'<div style="position: relative">{head()}</div>'
            f'<div style="position: relative; display: flex; flex-direction: column; gap: 40px; padding: 26px 16px 24px">{cards}</div></div>')


def gp1_desk():
    feat = gp1_card(GAMES[0], 0, True).replace('height: 170px', 'height: 330px').replace(cap(358, 11, 30), cap(600, 11, 34))
    small = ''.join(gp1_card(g, i).replace('height: 118px', 'height: 150px').replace(cap(172, 20 + i, 24), cap(285, 20 + i, 26)) for i, g in enumerate(GAMES[1:]))
    return (f'<div style="width: 1280px; height: 800px; box-sizing: border-box; position: relative; overflow: hidden; background: linear-gradient({C["sky"]}, {C["snow"]} 45%); color: {C["ink"]}; {BODY} direction: rtl">'
            f'{flakes(1280, 800, 140, 4)}'
            f'<div style="position: relative; height: 64px; background: {C["paper"]}; border-bottom: 1px solid {C["rule"]}; display: flex; align-items: center; gap: 28px; padding: 0 24px">'
            f'<span style="{DISP} font-size: 38px">גודאורי 2027</span><span style="font-size: 20px; font-weight: 600">בית</span><span style="font-size: 20px; font-weight: 600">מפת מסלולים</span>'
            f'<span style="font-size: 20px; font-weight: 600">נקודת מפגש</span><span style="font-size: 20px; font-weight: 700; color: #fff; background: {C["blue"]}; padding: 4px 16px 4px 26px; clip-path: polygon(0 50%, 14px 0, 100% 0, 100% 100%, 14px 100%)">משחקים</span></div>'
            f'<div style="position: relative; max-width: 1008px; margin: 0 auto; padding: 22px 0 0">'
            f'<div style="display: flex; align-items: baseline; gap: 14px; margin-bottom: 30px"><span style="{DISP} font-size: 44px; line-height: 1">5 משחקים</span><span style="font-size: 14px; color: {C["muted"]}">על השלג של גודאורי. השיאים נשמרים רק בטלפון שלכם, ומומלץ עם צליל.</span></div>'
            f'<div style="display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 40px 18px">'
            f'<div style="grid-column: span 2; grid-row: span 2">{feat}</div>{small}</div></div></div>')


# ---------------------------------------------------------------- GP2: trail signs on a post
def gp2():
    rows = ''
    for i, (slug, title, tag, meta, col, img) in enumerate(GAMES):
        big = i == 0
        hh = 150 if big else 104
        fg = C['ink'] if col == C['gold'] else '#FFFFFF'
        width = ['96%', '90%', '84%', '90%', '82%'][i]
        rows += (f'<a href="#" style="position: relative; display: block; width: {width}; height: {hh}px; text-decoration: none; filter: drop-shadow(0 6px 8px rgba(19,35,58,.18))">'
                 f'<span style="position: absolute; inset: 0; display: flex; align-items: stretch; background: {col}; color: {fg}; clip-path: polygon(0 50%, 30px 0, 100% 0, 100% 100%, 30px 100%)">'
                 f'<span style="flex: 0 0 {hh}px; height: 100%; overflow: hidden; border-left: 4px solid rgba(255,255,255,.55)"><img src="{img}" alt="" style="width: 100%; height: 100%; object-fit: cover"></span>'
                 f'<span style="flex: 1; display: flex; flex-direction: column; justify-content: center; gap: 3px; padding: 0 14px 0 34px; min-width: 0">'
                 f'<b style="{DISP} font-size: {32 if big else 26}px; line-height: .95">{title}</b><span style="font-size: 12.5px; opacity: .92">{tag}</span>'
                 f'<span style="font-size: 11.5px; font-weight: 700; opacity: .85">{meta}</span></span></span>'
                 f'<span style="position: absolute; right: 0; left: 30px; top: 0">{cap(300, 40 + i, 20)}</span></a>')
    return (f'<div style="width: 390px; height: 920px; box-sizing: border-box; position: relative; overflow: hidden; background: linear-gradient({C["sky"]}, {C["snow"]} 45%); color: {C["ink"]}; {BODY} direction: rtl">'
            f'{flakes(390, 920, 60, 5)}'
            f'<div style="position: relative">{head()}</div>'
            f'<div style="position: absolute; right: 26px; top: 120px; bottom: 0; width: 10px; background: {C["ink"]}"></div>'
            f'<div style="position: absolute; right: 21px; top: 110px; width: 20px; height: 14px; background: #fff; border-radius: 10px 10px 4px 4px"></div>'
            f'<div style="position: relative; display: flex; flex-direction: column; align-items: flex-start; gap: 30px; padding: 30px 40px 24px 12px">{rows}</div></div>')


# ---------------------------------------------------------------- GP3: ski passes on a rope
def ridge(w=60, h=20, col=C['ink']):
    hs = [.3, .45, .6, .5, .7, .9, .75, .6, .7, 1, .85, .7, .6, .72, .55, .4, .5, .35, .25, .3]
    st = w / len(hs)
    return f'<svg width="{w}" height="{h}" viewBox="0 0 {w} {h}" fill="{col}" aria-hidden="true">' + ''.join(f'<rect x="{i * st:.1f}" y="{h - v * h:.1f}" width="{1.6 if i % 2 else 1}" height="{v * h:.1f}"></rect>' for i, v in enumerate(hs)) + '</svg>'


def gp3_pass(g, i, w, rot, big=False):
    slug, title, tag, meta, col, img = g
    fg = C['ink'] if col == C['gold'] else '#FFFFFF'
    ih = 150 if big else 96
    return (f'<div style="position: relative; display: flex; flex-direction: column; align-items: center; transform: rotate({rot}deg); transform-origin: 50% 0">'
            f'<span style="width: 2px; height: 26px; background: {C["muted"]}"></span>'
            f'<span style="width: 16px; height: 16px; border: 3px solid {C["muted"]}; border-radius: 50%; margin-top: -4px; background: transparent"></span>'
            f'<a href="#" style="position: relative; width: {w}px; margin-top: -8px; display: flex; flex-direction: column; background: {C["paper"]}; color: {C["ink"]}; text-decoration: none; border-radius: 10px; overflow: hidden; box-shadow: 0 8px 16px rgba(19,35,58,.16)">'
            f'<span style="height: 26px; background: {col}; color: {fg}; display: flex; align-items: center; justify-content: space-between; padding: 0 10px; font-size: 11px; font-weight: 700; letter-spacing: .05em">'
            f'<span>SKI PASS · גודאורי</span><span style="width: 12px; height: 12px; border-radius: 50%; background: {C["snow"]}"></span></span>'
            f'<span style="position: relative; height: {ih}px; overflow: hidden"><img src="{img}" alt="" style="width: 100%; height: 100%; object-fit: cover">'
            f'<span style="position: absolute; left: 6px; bottom: 6px">{PLAY}</span></span>'
            f'<span style="display: flex; align-items: flex-end; justify-content: space-between; gap: 6px; padding: 8px 10px 10px">'
            f'<span style="display: flex; flex-direction: column; gap: 1px; min-width: 0"><b style="{DISP} font-size: {32 if big else 23}px; line-height: 1">{title}</b>'
            f'<span style="font-size: 11.5px; color: {C["muted"]}">{meta}</span></span>{ridge(46 if not big else 70, 18)}</span></a></div>')


def gp3():
    feat = gp3_pass(GAMES[0], 0, 330, -1.5, True)
    grid = ''.join(gp3_pass(g, i, 166, [2, -2.5, -1.5, 2.5][i]) for i, g in enumerate(GAMES[1:]))
    rope = (f'<svg width="390" height="40" viewBox="0 0 390 40" aria-hidden="true" style="position: absolute; left: 0; top: -14px"><path d="M-10 6 Q195 34 400 6" fill="none" stroke="{C["muted"]}" stroke-width="2.5"></path></svg>')
    return (f'<div style="width: 390px; height: 1000px; box-sizing: border-box; position: relative; overflow: hidden; background: linear-gradient({C["sky"]}, {C["snow"]} 40%); color: {C["ink"]}; {BODY} direction: rtl">'
            f'{flakes(390, 1000, 50, 6)}'
            f'<div style="position: relative">{head()}</div>'
            f'<div style="position: relative; display: flex; justify-content: center; padding-top: 18px">{rope}{feat}</div>'
            f'<div style="position: relative; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); justify-items: center; gap: 8px 6px; padding: 18px 12px 24px">'
            f'<svg width="390" height="40" viewBox="0 0 390 40" aria-hidden="true" style="position: absolute; left: 0; top: 4px"><path d="M-10 6 Q195 30 400 6" fill="none" stroke="{C["muted"]}" stroke-width="2.5"></path></svg>{grid}</div></div>')


# ---------------------------------------------------------------- meeting point: the empty start and clearing
def meet_map(pin=None, ghost=None, toast=None, ring=None):
    """A sketch of the meeting map: contours, a few runs and lifts, and the lift stations as pins."""
    rnd = random.Random(9)
    cont = ''.join(f'<path d="M-20 {60 + k * 34} C 90 {30 + k * 34}, 200 {100 + k * 34}, 410 {50 + k * 34}" fill="none" stroke="#C3CFDB" stroke-width="1"></path>' for k in range(13))
    runs = (f'<path d="M60 40 C 80 140, 40 220, 110 330" fill="none" stroke="{C["blue"]}" stroke-width="3"></path>'
            f'<path d="M210 30 C 190 120, 230 200, 190 300 S 160 380, 200 430" fill="none" stroke="{C["blue"]}" stroke-width="3"></path>'
            f'<path d="M300 60 C 320 160, 260 250, 300 360" fill="none" stroke="{C["red"]}" stroke-width="3"></path>'
            f'<path d="M140 120 C 150 220, 120 300, 150 410" fill="none" stroke="{C["green"]}" stroke-width="3"></path>')
    lifts = (f'<path d="M110 330 L 70 50" stroke="{C["ink"]}" stroke-width="1.5"></path><path d="M200 430 L 215 40" stroke="{C["ink"]}" stroke-width="1.5"></path>'
             f'<path d="M300 360 L 305 70" stroke="{C["ink"]}" stroke-width="1.5"></path>')
    st = {'a': (110, 330, 'Goodaura'), 'b': (70, 50, 'Goodaura, העליונה'), 'c': (200, 430, 'New Goodaura'), 'd': (215, 40, 'Kudebi'), 'e': (300, 360, 'Tatra'), 'f': (305, 70, 'Sadzele')}
    pins = ''
    for k, (x, y, n) in st.items():
        sel = pin == k
        col = C['gold'] if sel else '#FFFFFF'
        sc = 1.35 if sel else 1
        pins += (f'<g transform="translate({x} {y}) scale({sc})"><path d="M0 0 C -9 -12, -11 -18, -11 -22 A 11 11 0 1 1 11 -22 C 11 -18, 9 -12, 0 0 Z" fill="{col}" stroke="{C["ink"]}" stroke-width="2"></path>'
                 f'<circle cx="0" cy="-22" r="4" fill="{C["ink"]}"></circle></g>')
    extra = ''
    if ghost:
        x, y, _ = st[ghost]
        extra += (f'<g transform="translate({x} {y + 18}) scale(1.1)" opacity=".35"><path d="M0 0 C -9 -12, -11 -18, -11 -22 A 11 11 0 1 1 11 -22 C 11 -18, 9 -12, 0 0 Z" fill="{C["gold"]}" stroke="{C["ink"]}" stroke-width="2"></path></g>'
                  f'<path d="M{x} {y - 40} L {x} {y - 4}" stroke="{C["ink"]}" stroke-width="1.5" stroke-dasharray="3 4" opacity=".5"></path>')
    if ring:
        x, y = ring
        extra += (f'<circle cx="{x}" cy="{y}" r="22" fill="none" stroke="{C["ink"]}" stroke-width="2" opacity=".5"></circle>'
                  f'<circle cx="{x}" cy="{y}" r="36" fill="none" stroke="{C["ink"]}" stroke-width="1.5" opacity=".25"></circle>'
                  f'<circle cx="{x}" cy="{y}" r="6" fill="{C["ink"]}" opacity=".6"></circle>')
    svg = (f'<svg width="390" height="460" viewBox="0 0 390 460" aria-hidden="true" style="display: block; background: #E6EDF3">{cont}{runs}{lifts}{extra}{pins}</svg>')
    return svg, st


def meet_frame(map_html, below, title_note=''):
    return (f'<div style="width: 390px; height: 1000px; box-sizing: border-box; position: relative; overflow: hidden; background: {C["snow"]}; color: {C["ink"]}; {BODY} direction: rtl">'
            f'<div style="display: flex; justify-content: space-between; align-items: center; padding: 12px 16px; background: {C["snow"]}">'
            f'<span style="{DISP} font-size: 44px; line-height: 1">נקודת מפגש</span><a href="#" style="font-size: 15px; font-weight: 700; color: {C["blue"]}; text-decoration: none">→ בית</a></div>'
            f'<div style="position: relative">{map_html}</div>{below}</div>')


def when_block(dim=False):
    op = '.45' if dim else '1'
    days = ''.join(f'<span style="flex: 1; min-height: 44px; display: flex; align-items: center; justify-content: center; border: 2px solid {C["ink"]}; font-size: 14px; font-weight: 700; background: {C["gold"] if i == 0 else C["paper"]}">{d}</span>' for i, d in enumerate(['ב׳ 11.1', 'ג׳ 12.1', 'ד׳ 13.1', 'ה׳ 14.1']))
    return (f'<div style="padding: 16px 16px 0; opacity: {op}"><b style="{DISP} font-size: 34px">מתי</b><div style="display: flex; gap: 8px; margin-top: 8px">{days}</div></div>')


def empty_ticket():
    return (f'<div style="margin: 18px 16px 0; padding: 18px 16px; border: 2px dashed {C["dash"]}; background: rgba(255,255,255,.5); display: flex; align-items: center; gap: 14px; transform: rotate(-1.5deg)">'
            f'<svg width="40" height="48" viewBox="-12 -36 24 38" aria-hidden="true"><path d="M0 0 C -9 -12, -11 -18, -11 -22 A 11 11 0 1 1 11 -22 C 11 -18, 9 -12, 0 0 Z" fill="none" stroke="{C["dash"]}" stroke-width="2" stroke-dasharray="4 3"></path></svg>'
            f'<span style="display: flex; flex-direction: column; gap: 2px"><b style="{DISP} font-size: 30px; line-height: 1">כרטיס המפגש מחכה</b>'
            f'<span style="font-size: 13px; color: {C["muted"]}">בוחרים תחנה על המפה, או נקודה קבועה למטה, והכרטיס יודפס כאן.</span></span></div>')


def mp1():
    svg, st = meet_map()
    over = (f'<div style="position: absolute; right: 12px; left: 12px; bottom: 12px; display: flex; align-items: center; gap: 10px; padding: 10px 14px; background: {C["ink"]}; color: #fff; box-shadow: 0 6px 14px rgba(0,0,0,.25)">'
            f'<span style="width: 10px; height: 10px; border-radius: 50%; background: {C["gold"]}; box-shadow: 0 0 0 6px rgba(244,185,66,.3)"></span>'
            f'<span style="font-size: 14px; font-weight: 600">איפה נפגשים? לחצו על אחת מ-6 התחנות</span></div>'
            f'<span style="position: absolute; top: 10px; right: 10px; padding: 6px 12px; background: {C["paper"]}; font-size: 13px; font-weight: 700">כל ההר</span>')
    presets = ''.join(f'<div style="display: flex; align-items: center; justify-content: space-between; min-height: 52px; width: {w}; padding: 0 16px 0 34px; box-sizing: border-box; background: {c}; color: {fg}; clip-path: polygon(0 50%, 22px 0, 100% 0, 100% 100%, 22px 100%)">'
                      f'<b style="{DISP} font-size: 26px">{t}</b><span style="font-size: 12px; font-weight: 700">{s}</span></div>'
                      for t, s, c, fg, w in [('Goodaura 09:30', 'רכבל הבוקר', C['gold'], C['ink'], '94%'), ('Snow Park 13:00', 'צהריים', C['blue'], '#fff', '88%'), ('Goodaura 16:30', 'סוף יום', C['ink'], '#fff', '82%')])
    below = (when_block(True) + f'<div style="padding: 16px 16px 0"><b style="{DISP} font-size: 30px">או בלחיצה אחת</b><div style="display: flex; flex-direction: column; align-items: flex-end; gap: 8px; margin-top: 8px">{presets}</div></div>' + empty_ticket())
    return meet_frame(f'{svg}{over}', below)


def picked_card(x_btn=True):
    xb = (f'<button type="button" aria-label="לבטל את הבחירה" style="position: absolute; top: -14px; left: -10px; width: 44px; height: 44px; border-radius: 50%; border: 2px solid {C["ink"]}; background: {C["paper"]}; display: flex; align-items: center; justify-content: center; box-shadow: 0 4px 10px rgba(19,35,58,.2)">'
          f'<svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true"><path d="M3 3 L13 13 M13 3 L3 13" stroke="{C["ink"]}" stroke-width="2.4" stroke-linecap="round"></path></svg></button>') if x_btn else ''
    return (f'<div style="position: relative; margin: 22px 16px 0; background: {C["paper"]}; box-shadow: 0 10px 22px rgba(19,35,58,.18); transform: rotate(-1.5deg)">{xb}'
            f'<div style="background: {C["ink"]}; color: #fff; padding: 8px 14px; display: flex; justify-content: space-between"><b style="{DISP} font-size: 26px">כרטיס מפגש</b><span style="font-size: 12px">החבר׳ה · גודאורי 2027</span></div>'
            f'<div style="display: flex; align-items: center; justify-content: space-between; padding: 12px 14px">'
            f'<div><span style="display: block; font-size: 11px; color: {C["muted"]}">שעה</span><b style="{DISP} font-size: 50px; line-height: .9">12:30</b></div>'
            f'<div style="padding: 6px 14px 6px 30px; background: {C["blue"]}; color: #fff; clip-path: polygon(0 50%, 18px 0, 100% 0, 100% 100%, 18px 100%)"><b style="{DISP} font-size: 36px" dir="ltr">Goodaura</b></div></div></div>')


def mp2():
    svg, st = meet_map(pin='a')
    callout = (f'<div style="position: absolute; left: 120px; top: 238px; padding: 8px 12px; background: {C["ink"]}; color: #fff; box-shadow: 0 6px 14px rgba(0,0,0,.25)">'
               f'<b style="{DISP} font-size: 28px; line-height: 1" dir="ltr">Goodaura</b><span style="display: block; font-size: 12px; opacity: .85">התחנה התחתונה · 2,161 מ׳</span></div>'
               f'<span style="position: absolute; top: 10px; right: 10px; padding: 6px 12px; background: {C["paper"]}; font-size: 13px; font-weight: 700">כל ההר</span>'
               f'<button type="button" style="position: absolute; top: 10px; left: 10px; min-height: 40px; padding: 0 14px; border: 0; background: {C["paper"]}; font: inherit; font-size: 13px; font-weight: 700; display: flex; align-items: center; gap: 6px">'
               f'<svg width="12" height="12" viewBox="0 0 16 16" aria-hidden="true"><path d="M3 3 L13 13 M13 3 L3 13" stroke="{C["ink"]}" stroke-width="2.6" stroke-linecap="round"></path></svg>ניקוי הבחירה</button>'
               f'<div style="position: absolute; right: 12px; left: 12px; bottom: 12px; padding: 8px 12px; background: rgba(255,255,255,.92); font-size: 12.5px; color: {C["ink"]}">לחיצה על שטח ריק במפה, או שוב על הסיכה, מבטלת את הבחירה</div>')
    notes = (f'<div style="margin: 34px 16px 0; display: flex; flex-direction: column; gap: 10px">'
             + ''.join(f'<div style="display: flex; gap: 10px; align-items: flex-start"><b style="flex: none; width: 26px; height: 26px; border-radius: 50%; background: {C["gold"]}; display: flex; align-items: center; justify-content: center; font-size: 13px">{n}</b><span style="font-size: 13.5px">{t}</span></div>'
                       for n, t in [('1', 'כפתור "ניקוי הבחירה" בפינת המפה.'), ('2', 'לחיצה על שטח ריק במפה, רחוק מתחנה. לחיצה ליד תחנה עדיין בוחרת אותה.'), ('3', 'לחיצה שנייה על הסיכה שנבחרה.'), ('4', 'כפתור ה-X על כרטיס המפגש.')])
             + '</div>')
    return meet_frame(f'{svg}{callout}', when_block() + picked_card() + notes)


def mp3():
    svg, st = meet_map(ghost='a', ring=(320, 200))
    toast = (f'<div style="position: absolute; right: 12px; left: 12px; bottom: 12px; display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 8px 8px 8px 14px; background: {C["ink"]}; color: #fff">'
             f'<span style="font-size: 14px; font-weight: 600">הבחירה בוטלה</span>'
             f'<button type="button" style="min-height: 40px; padding: 0 16px; border: 0; background: {C["gold"]}; color: {C["ink"]}; font: inherit; font-size: 14px; font-weight: 700">החזרה</button></div>')
    fold = (f'<div style="margin: 18px 16px 0; position: relative; height: 150px">'
            f'<div style="position: absolute; inset: 0; transform: rotate(-1.5deg) translateY(18px) scale(.94); opacity: .35; background: {C["paper"]}; box-shadow: 0 10px 22px rgba(19,35,58,.18)"></div>'
            f'<div style="position: absolute; inset: 0">{empty_ticket().replace("margin: 18px 16px 0;", "margin: 0;")}</div></div>')
    notes = (f'<div style="margin: 30px 16px 0; font-size: 13.5px; color: {C["muted"]}">הסיכה יורדת ומתכווצת לתחנה רגילה, הכרטיס מתקפל חזרה למקום הריק, והקישור בכתובת חוזר ל-<span dir="ltr">#meet</span>. '
             f'הודעה קטנה עם "החזרה" לכמה שניות, למקרה שזה קרה בטעות.</div>')
    return meet_frame(f'{svg}{toast}', when_block(True) + fold + notes)


out = {
    'GP1-SnowCaps.dc.html': (page('משחקים: שלג על הכרטיסים', gp1(), 390, 1010), 390, 1010, 'א · שלג טרי על כל כרטיס (הרעיון שלך)'),
    'GP2-TrailSigns.dc.html': (page('משחקים: שלטים על עמוד', gp2(), 390, 920), 390, 920, 'ב · שלטי מסלול על עמוד'),
    'GP3-SkiPasses.dc.html': (page('משחקים: סקי־פס על חבל', gp3(), 390, 1000), 390, 1000, 'ג · כרטיסי סקי־פס תלויים'),
    'GP4-SnowCapsDesktop.dc.html': (page('משחקים במחשב', gp1_desk(), 1280, 800), 1280, 800, 'א במחשב'),
    'MP1-MeetEmpty.dc.html': (page('נקודת מפגש: בלי בחירה', mp1(), 390, 1000), 390, 1000, 'מפגש 1 · ברירת מחדל: שום נקודה'),
    'MP2-MeetPicked.dc.html': (page('נקודת מפגש: נבחרה תחנה', mp2(), 390, 1000), 390, 1000, 'מפגש 2 · נבחרה תחנה, וארבע דרכים לבטל'),
    'MP3-MeetCleared.dc.html': (page('נקודת מפגש: הבחירה בוטלה', mp3(), 390, 1000), 390, 1000, 'מפגש 3 · רגע הביטול, עם "החזרה"'),
}

if __name__ == '__main__':
    dst = root / 'design/canvas/project'
    for n, (s, *_rest) in out.items():
        (dst / n).write_text(s, encoding='utf-8')
    print({n: len(v[0]) for n, v in out.items()})
