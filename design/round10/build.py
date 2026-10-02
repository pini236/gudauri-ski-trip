#!/usr/bin/env python3
"""Round 10 design screens: what the app's next stages are waiting for (decision 18: canvas before code).

Three canvas pages:
  round10   "סבב 10: הטיול שלך וחשבון"  H1-H4 the guest home and "your trip" (decision 27, blocks 13.2),
                                         A1-A5 sign in with Google or Apple, guest, the account (docs/USERS.md).
  round10q  "סבב 10: קבוצה"              Q1-Q10 create a group, invite by link and short code, join without
                                         signing up, "I'm already in the group", admin approval, the group page
                                         (flights, "I'm on the same flight", meetups with a reminder, high scores).
  round10p  "סבב 10: פרטיות ושפות"       P1-P2 the privacy page on the site (docs/PRIVACY.md),
                                         LT1-LT3 left-to-right signs (English, Russian, Georgian), FT1 fonts.

Only what the planning documents already say is drawn. Names and flights are the public ones from
design/data/trip.json; the scores, the second flight and the invite code are sample data, and say so.
Run from the repo root: python3 design/round10/build.py [canvas folder]
(default: design/canvas/project; the canvas index is updated next to the files)."""
import json, pathlib, random, sys

root = pathlib.Path(__file__).resolve().parent.parent.parent
TRIP = json.load(open(root / 'design/data/trip.json', encoding='utf-8'))
O, R = TRIP['outbound'], TRIP['return']
CREW = TRIP['members']
PANO = '/_blob/d5ad1e6d2660c28123782ea318a7fd85'  # the view from the village at noon, already in the canvas

C = {'snow': '#EEF2F5', 'paper': '#FFFFFF', 'paper2': '#F4F7FA', 'ink': '#13233A', 'muted': '#4B5A6F', 'rule': '#CBD5DF',
     'dash': '#8E9CAD', 'blue': '#1F5FC4', 'green': '#1B8A4C', 'red': '#D1342B', 'gold': '#F4B942', 'sky': '#DCE8F1',
     'nbg': '#0D1522', 'npaper': '#16223A', 'npaper2': '#1B2944', 'nink': '#EAF0F7', 'nmuted': '#A3B3C8', 'nrule': '#2A3B55'}
DISP = "font-family: Karantina, 'Arial Narrow', sans-serif; font-weight: 700;"
BODY = "font-family: 'IBM Plex Sans Hebrew', 'Segoe UI', system-ui, sans-serif;"
FONTS = ('<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Karantina:wght@700'
         '&amp;family=IBM+Plex+Sans+Hebrew:wght@400;500;600;700&amp;display=swap">')
FONTS_INTL = ('<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Karantina:wght@700'
              '&amp;family=IBM+Plex+Sans+Hebrew:wght@400;500;600;700&amp;family=IBM+Plex+Sans:wght@400;500;600;700'
              '&amp;family=Oswald:wght@500;600;700&amp;family=Yanone+Kaffeesatz:wght@600;700'
              '&amp;family=Fira+Sans+Extra+Condensed:wght@600;700;800'
              '&amp;family=Noto+Sans+Georgian:wdth,wght@62.5..100,400..900&amp;display=swap">')
DAYS = '''class Component extends DCLogic {
renderVals() {
const d = Math.ceil((new Date('2027-01-10T16:00:00+02:00') - new Date()) / 864e5);
return { days: d > 0 ? d : 0 };
}
}'''
STATIC = '''class Component extends DCLogic {
  renderVals() { return {}; }
}'''

CSS = f'''
.hero{{background:url({PANO}) center 40%/cover no-repeat}}
.paper{{background-image:repeating-linear-gradient(97deg,rgba(19,35,58,.018) 0 1px,transparent 1px 7px),repeating-linear-gradient(4deg,rgba(19,35,58,.014) 0 1px,transparent 1px 11px)}}
.paper-n{{background-image:repeating-linear-gradient(97deg,rgba(255,255,255,.025) 0 1px,transparent 1px 7px),repeating-linear-gradient(4deg,rgba(255,255,255,.02) 0 1px,transparent 1px 11px)}}
.glow{{text-shadow:0 0 6px rgba(255,200,90,.65),0 0 18px rgba(244,185,66,.35)}}
.nl{{-webkit-mask:radial-gradient(circle 11px at 0 0,transparent 98%,#000) top/100% 51% no-repeat,radial-gradient(circle 11px at 0 100%,transparent 98%,#000) bottom/100% 51% no-repeat;mask:radial-gradient(circle 11px at 0 0,transparent 98%,#000) top/100% 51% no-repeat,radial-gradient(circle 11px at 0 100%,transparent 98%,#000) bottom/100% 51% no-repeat}}
.nr{{-webkit-mask:radial-gradient(circle 11px at 100% 0,transparent 98%,#000) top/100% 51% no-repeat,radial-gradient(circle 11px at 100% 100%,transparent 98%,#000) bottom/100% 51% no-repeat;mask:radial-gradient(circle 11px at 100% 0,transparent 98%,#000) top/100% 51% no-repeat,radial-gradient(circle 11px at 100% 100%,transparent 98%,#000) bottom/100% 51% no-repeat}}
.tap{{border:0;margin:0;font:inherit;color:inherit;cursor:pointer;text-align:inherit;background:transparent;padding:0}}
.tap:focus-visible,a:focus-visible,input:focus-visible{{outline:3px solid {C['gold']};outline-offset:2px}}
.fld{{display:block;width:100%;box-sizing:border-box;height:48px;padding:0 12px;border:1.5px solid {C['rule']};border-radius:0;background:{C['paper']};font:inherit;font-size:16px;color:{C['ink']}}}
.fld::placeholder{{color:#7D8A9C}}
.sw{{appearance:none;-webkit-appearance:none;flex:none;width:46px;height:28px;margin:0;border:2px solid {C['ink']};background:{C['paper']};position:relative;cursor:pointer}}
.sw::after{{content:"";position:absolute;top:3px;right:3px;width:18px;height:18px;background:{C['ink']};transition:right .15s}}
.sw:checked{{background:{C['blue']};border-color:{C['blue']}}}
.sw:checked::after{{right:21px;background:#fff}}
a{{color:{C['blue']}}}
@media (prefers-reduced-motion: reduce){{ *{{ transition: none !important; animation: none !important }} }}
'''


def page(title, body, w=390, h=844, js=STATIC, fonts=FONTS, night=False, lang='he', d='rtl'):
    return f'''<!doctype html>
<html lang="{lang}" dir="{d}">
<head>
<meta charset="utf-8">
<title>{title}</title>
<script src="./support.js"></script>
</head>
<body>
<x-dc>
<helmet>
{fonts}
<style>
body{{margin:0;background:{C['nbg'] if night else C['snow']}}}
{CSS}
</style>
</helmet>
{body}
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{{"$preview":{{"width":{w},"height":{h}}}}}'>
{js}
</script>
</body>
</html>
'''


# ---------------------------------------------------------------- shared pieces

def cap(w, seed, h=24, drips=True):
    """Fresh snow lying on a top edge (the same drift as the snowy signs on the site, round 8)."""
    rnd = random.Random(seed)
    n = max(4, int(w / 34))
    pts = [(i * w / n, 6 + rnd.random() * 8) for i in range(n + 1)]
    dd = f'M0 {h} L0 {pts[0][1]:.1f} '
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        dd += f'Q{(x0 + x1) / 2:.1f} {min(y0, y1) - 6 - rnd.random() * 6:.1f} {x1:.1f} {y1:.1f} '
    dd += f'L{w} {h - 6} '
    x = w
    while x > 0:
        nx = max(0, x - (18 + rnd.random() * 30))
        if drips and rnd.random() < .35 and nx > 8:
            dx = (x + nx) / 2
            dd += f'L{dx + 5:.1f} {h - 6} Q{dx + 4:.1f} {h + 6 + rnd.random() * 6:.1f} {dx:.1f} {h + 8 + rnd.random() * 5:.1f} Q{dx - 4:.1f} {h + 6:.1f} {dx - 5:.1f} {h - 6} '
        dd += f'Q{(x + nx) / 2:.1f} {h - 2 + rnd.random() * 4:.1f} {nx:.1f} {h - 6} '
        x = nx
    dd += 'Z'
    return (f'<svg width="{w}" height="{h + 16}" viewBox="0 0 {w} {h + 16}" aria-hidden="true" style="position: absolute; right: 0; top: -{h - 8}px; overflow: visible; filter: drop-shadow(0 2px 2px rgba(19,35,58,.18)); pointer-events: none">'
            f'<path d="{dd}" fill="#FFFFFF"></path><path d="{dd}" fill="none" stroke="#C9D8E8" stroke-width="1"></path></svg>')


def icon(name, size=22, color='currentColor', sw=2):
    paths = {
        'gear': '<circle cx="12" cy="12" r="3.2"></circle><path d="M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5.3 5.3l2.1 2.1M16.6 16.6l2.1 2.1M5.3 18.7l2.1-2.1M16.6 7.4l2.1-2.1"></path>',
        'back': '<path d="M5 12h14"></path><path d="M13 6l6 6-6 6"></path>',
        'fwd': '<path d="M19 12H5"></path><path d="M11 6l-6 6 6 6"></path>',
        'edit': '<path d="M4 20h4L19 9l-4-4L4 16z"></path><path d="M13.5 6.5l4 4"></path>',
        'bell': '<path d="M6 16V11a6 6 0 0 1 12 0v5l2 2H4z"></path><path d="M10 20a2 2 0 0 0 4 0"></path>',
        'share': '<circle cx="6" cy="12" r="2.5"></circle><circle cx="18" cy="6" r="2.5"></circle><circle cx="18" cy="18" r="2.5"></circle><path d="M8.2 10.8l7.6-3.6M8.2 13.2l7.6 3.6"></path>',
        'copy': '<rect x="8" y="8" width="12" height="12"></rect><path d="M4 16V4h12"></path>',
        'check': '<path d="M4 12.5l5 5L20 6.5"></path>',
        'x': '<path d="M6 6l12 12M18 6L6 18"></path>',
        'plus': '<path d="M12 5v14M5 12h14"></path>',
        'phone': '<rect x="7" y="2.5" width="10" height="19"></rect><path d="M11 18.5h2"></path>',
        'cloud': '<path d="M7 18h10a4 4 0 0 0 .5-8A6 6 0 0 0 6 9.5 4.3 4.3 0 0 0 7 18z"></path>',
        'lock': '<rect x="5" y="10.5" width="14" height="10"></rect><path d="M8 10.5V7.5a4 4 0 0 1 8 0v3"></path>',
        'people': '<circle cx="9" cy="8.5" r="3.2"></circle><path d="M3 20c0-3.6 2.7-6 6-6s6 2.4 6 6"></path><circle cx="17" cy="9.5" r="2.5"></circle><path d="M16 14.2c3 .2 5 2.4 5 5.8"></path>',
        'trophy': '<path d="M8 4h8v5a4 4 0 0 1-8 0z"></path><path d="M8 6H4.5a3 3 0 0 0 3.5 4M16 6h3.5a3 3 0 0 1-3.5 4M12 13v4M8.5 20.5h7M10 17h4"></path>',
        'pin': '<path d="M12 21s-6.5-6.4-6.5-11.2a6.5 6.5 0 0 1 13 0C18.5 14.6 12 21 12 21z"></path><circle cx="12" cy="9.8" r="2.4"></circle>',
        'link': '<path d="M10 14a4.5 4.5 0 0 0 6.4 0l3-3a4.5 4.5 0 0 0-6.4-6.4l-1 1"></path><path d="M14 10a4.5 4.5 0 0 0-6.4 0l-3 3a4.5 4.5 0 0 0 6.4 6.4l1-1"></path>',
        'trash': '<path d="M4 7h16M9.5 7V4.5h5V7M6.5 7l1 13h9l1-13"></path>',
        'out': '<path d="M14 4h6v16h-6"></path><path d="M10 12H3M6.5 8.5L3 12l3.5 3.5"></path>',
        'key': '<circle cx="8" cy="15" r="4"></circle><path d="M11 12l9-9M16.5 6.5l2.5 2.5M14 9l2 2"></path>',
        'clock': '<circle cx="12" cy="12" r="9"></circle><path d="M12 7v5l3.5 2"></path>',
    }
    return (f'<svg width="{size}" height="{size}" viewBox="0 0 24 24" fill="none" stroke="{color}" stroke-width="{sw}" stroke-linecap="square" '
            f'stroke-linejoin="miter" aria-hidden="true" style="flex: none">{paths[name]}</svg>')


PLANE = '<path d="M2 13.5v-2l8-4.5V2.5a1.5 1.5 0 0 1 3 0V7l8 4.5v2l-8-2.5v5l2.5 2v1.5L12 18.5 8.5 19.5V18l2.5-2v-5z" transform="rotate(-90 12 12)"></path>'


def plane(size, color):
    return f'<svg width="{size}" height="{size}" viewBox="0 0 24 24" fill="{color}" aria-hidden="true" style="flex: none">{PLANE}</svg>'


def sign(text, sub, color, width='88%', fg='#FFFFFF', snow=None, ltr=False, h=72, disp=DISP, size=40, href='#'):
    """A trail sign. In RTL the arrow points left (forward in Hebrew); in LTR it flips and points right."""
    clip = ('polygon(0 0, calc(100% - 26px) 0, 100% 50%, calc(100% - 26px) 100%, 0 100%)' if ltr
            else 'polygon(0 50%, 26px 0, 100% 0, 100% 100%, 26px 100%)')
    pad = '8px 40px 8px 20px' if ltr else '8px 20px 8px 40px'
    snowcap = cap(300, snow, 22) if snow is not None else ''
    if snowcap and ltr:
        snowcap = snowcap.replace('right: 0;', 'left: 0;')
    return (f'<a href="{href}" style="position: relative; display: block; width: {width}; text-decoration: none; filter: drop-shadow(0 4px 6px rgba(19,35,58,.16))">'
            f'<span style="display: flex; flex-direction: column; justify-content: center; min-height: {h}px; box-sizing: border-box; '
            f'padding: {pad}; background: {color}; color: {fg}; clip-path: {clip}">'
            f'<span style="{disp} font-size: {size}px; line-height: 1">{text}</span><span style="font-size: 13px">{sub}</span></span>{snowcap}</a>')


def post(signs, top=0, ltr=False, ink=C['ink'], gap=14):
    side = 'left' if ltr else 'right'
    align = 'flex-start' if ltr else 'flex-end'
    pad = 'padding-left: 30px' if ltr else 'padding-right: 30px'
    return (f'<nav aria-label="{"Sections" if ltr else "חלקי האפליקציה"}" style="position: relative; margin-top: {top}px; display: flex; flex-direction: column; align-items: {align}; gap: {gap}px; {pad}; box-sizing: border-box">'
            f'<span style="position: absolute; {side}: 20px; top: -8px; bottom: -400px; width: 6px; background: {ink}"></span>{"".join(signs)}</nav>')


def topbar(title, back='בית', night=False, size=44):
    ink = C['nink'] if night else C['ink']
    lk = '#F4B942' if night else C['blue']
    return (f'<div style="display: flex; justify-content: space-between; align-items: center; padding: 12px 16px 6px; color: {ink}">'
            f'<h1 style="margin: 0; {DISP} font-size: {size}px; line-height: 1">{title}</h1>'
            f'<a href="#" style="display: inline-flex; align-items: center; gap: 4px; min-height: 44px; font-size: 15px; font-weight: 700; color: {lk}; text-decoration: none">{icon("back", 20)}{back}</a></div>')


def btn(text, kind='primary', ico=None, full=True, small=False):
    h = 44 if small else 52
    base = (f'display: {"flex" if full else "inline-flex"}; align-items: center; justify-content: center; gap: 8px; '
            f'{"width: 100%;" if full else ""} min-height: {h}px; box-sizing: border-box; padding: 0 18px; font-size: {15 if small else 16.5}px; font-weight: 700; text-decoration: none; cursor: pointer; font-family: inherit')
    styles = {
        'primary': f'background: {C["blue"]}; color: #FFFFFF; border: 0',
        'ink': f'background: {C["ink"]}; color: #FFFFFF; border: 0',
        'ghost': f'background: transparent; color: {C["ink"]}; border: 2px solid {C["ink"]}',
        'quiet': f'background: transparent; color: {C["blue"]}; border: 0',
        'danger': f'background: transparent; color: {C["red"]}; border: 2px solid {C["red"]}',
        'gold': f'background: {C["gold"]}; color: {C["ink"]}; border: 0',
        'google': f'background: #FFFFFF; color: #1F1F1F; border: 1px solid #747775',
        'apple': f'background: #000000; color: #FFFFFF; border: 0',
    }
    i = icon(ico, 20) if ico else ''
    return f'<button type="button" class="tap" style="{base}; {styles[kind]}">{i}{text}</button>'


def gmark(size=20):
    """A neutral stand-in for the Google mark. In the app the real button comes from the system (Credential Manager)."""
    return (f'<span aria-hidden="true" style="flex: none; width: {size}px; height: {size}px; border-radius: 50%; border: 2px solid #747775; box-sizing: border-box; '
            f'display: inline-flex; align-items: center; justify-content: center; font: 700 {size * .6:.0f}px/1 Arial, sans-serif; color: #1F1F1F">G</span>')


def amark(size=20):
    return (f'<span aria-hidden="true" style="flex: none; width: {size}px; height: {size}px; border-radius: 50%; border: 2px solid #FFFFFF; box-sizing: border-box; '
            f'display: inline-flex; align-items: center; justify-content: center; font: 700 {size * .6:.0f}px/1 Arial, sans-serif; color: #FFFFFF">A</span>')


def google_btn(text='המשך עם גוגל'):
    return btn(text, 'google').replace('">' + text, '">' + gmark() + text, 1)


def apple_btn(text='המשך עם אפל'):
    return btn(text, 'apple').replace('">' + text, '">' + amark() + text, 1)


def field(label, value='', ph='', fid=None, dir_=None, w=None, mono=False):
    fid = fid or f'f{abs(hash(label + value + ph)) % 99999}'
    dv = f' dir="{dir_}"' if dir_ else ''
    st = f' style="{DISP} font-size: 26px; letter-spacing: .02em"' if mono else ''
    return (f'<div style="display: flex; flex-direction: column; gap: 5px; {f"width: {w};" if w else ""} min-width: 0">'
            f'<label for="{fid}" style="font-size: 13px; font-weight: 600; color: {C["muted"]}">{label}</label>'
            f'<input id="{fid}" class="fld" type="text" value="{value}" placeholder="{ph}"{dv}{st}></div>')


def toggle(label, sub='', on=False, tid=None):
    tid = tid or f't{abs(hash(label)) % 99999}'
    subh = f'<span style="font-size: 13px; color: {C["muted"]}">{sub}</span>' if sub else ''
    return (f'<label for="{tid}" style="display: flex; align-items: center; justify-content: space-between; gap: 14px; min-height: 52px; padding: 8px 0; border-bottom: 1px solid {C["rule"]}; cursor: pointer">'
            f'<span style="display: flex; flex-direction: column; gap: 2px"><span style="font-size: 15.5px; font-weight: 600">{label}</span>{subh}</span>'
            f'<input id="{tid}" class="sw" type="checkbox"{" checked" if on else ""}></label>')


def L(t, color=C['muted']):
    return f'<span style="display: block; font-size: 11px; font-weight: 600; letter-spacing: .05em; color: {color}">{t}</span>'


def note(text, ico='phone', color=C['muted']):
    return (f'<p style="margin: 0; display: flex; gap: 8px; align-items: flex-start; font-size: 13px; line-height: 1.45; color: {color}">'
            f'{icon(ico, 18, color)}<span>{text}</span></p>')


def screen(inner, h=844, night=False, bg=None, extra=''):
    b = bg or (C['nbg'] if night else C['snow'])
    ink = C['nink'] if night else C['ink']
    return (f'<div style="width: 390px; height: {h}px; box-sizing: border-box; position: relative; overflow: hidden; background: {b}; color: {ink}; {BODY} direction: rtl"{extra}>'
            f'{inner}</div>')


def sheet(under, content, h=844, top=300):
    """A bottom sheet over a dimmed screen."""
    return (f'{under}<div style="position: absolute; inset: 0; background: rgba(13,21,34,.55)"></div>'
            f'<div role="dialog" aria-modal="true" style="position: absolute; right: 0; left: 0; bottom: 0; top: {top}px; background: {C["paper"]}; '
            f'box-shadow: 0 -10px 30px rgba(0,0,0,.25); display: flex; flex-direction: column; padding: 10px 20px 24px; box-sizing: border-box">'
            f'<span style="align-self: center; width: 44px; height: 5px; background: {C["rule"]}; margin-bottom: 14px"></span>{content}</div>')


def d(iso):
    y, m, dd = iso.split('-')
    return f'{int(dd)}.{int(m)}'


# ---------------------------------------------------------------- the home of the app

def app_head(night=False, clock='13:35'):
    ink = '#EAF0F7' if night else C['ink']
    return (f'<div style="position: absolute; top: 18px; right: 16px; left: 10px; display: flex; justify-content: space-between; align-items: center; color: {ink}">'
            f'<span style="display: flex; flex-direction: column"><span style="{DISP} font-size: 34px; line-height: 1">גודאורי, גאורגיה</span>'
            f'<span style="font-size: 12px">השעה בגודאורי <b style="font-size: 15px">{clock}</b></span></span>'
            f'<span style="display: flex; gap: 2px">'
            f'<button type="button" class="tap" aria-label="מצב תצוגה: {"לילה" if night else "אוטומטי"}" style="width: 44px; height: 44px; display: flex; align-items: center; justify-content: center">'
            f'<svg width="22" height="22" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9" fill="none" stroke="{ink}" stroke-width="1.8"></circle><path d="M12 3a9 9 0 0 1 0 18z" fill="{ink}"></path></svg></button>'
            f'<a href="#" aria-label="אודות והגדרות" style="width: 44px; height: 44px; display: flex; align-items: center; justify-content: center; color: {ink}">{icon("gear", 22, ink)}</a></span></div>')


def hero(night=False, h=300):
    if not night:
        return f'<div class="hero" style="position: absolute; inset: 0 0 auto 0; height: {h}px"></div>'
    pts = [(40, 40), (90, 90), (150, 30), (210, 70), (300, 40), (350, 110), (120, 150), (260, 130), (60, 190), (330, 180), (185, 120), (20, 120)]
    stars = ''.join(f'<span style="position: absolute; left: {x}px; top: {y}px; width: 2px; height: 2px; background: #EAF0F7; opacity: .8"></span>' for x, y in pts)
    return (f'<div style="position: absolute; inset: 0 0 auto 0; height: {h}px; background: linear-gradient(#0B1320,#16223A)">{stars}'
            f'<svg width="390" height="140" viewBox="0 0 390 140" aria-hidden="true" style="position: absolute; left: 0; bottom: 0">'
            f'<path d="M0 140 L0 90 L40 70 L80 88 L130 40 L170 72 L215 30 L260 66 L300 50 L340 80 L390 60 L390 140 Z" fill="#22324D"></path>'
            f'<path d="M0 140 L0 112 L60 96 L120 110 L180 92 L240 108 L310 94 L390 106 L390 140 Z" fill="#2C3E5C"></path></svg></div>')


def bpass(f, out=True, night=False, stub=None, who='אני', edit=True):
    """The approved horizontal pass (AB5/AB6), static, for one person's own trip."""
    p = (dict(paper=C['npaper'], paper2=C['npaper2'], ink='#FFD98A', muted='#C9B98F', rule='#3A4A66', dash='#6C7E9C', strip=C['gold'], onstrip=C['nbg'], acc='#FFD98A', g=' glow', pc='paper-n')
         if night else dict(paper=C['paper'], paper2=C['paper2'], ink=C['ink'], muted=C['muted'], rule=C['rule'], dash=C['dash'], strip=C['blue'], onstrip='#FFFFFF', acc=C['blue'], g='', pc='paper'))
    fc, tc = (f['fromCode'], f['toCode'])
    stub = stub or (f'{L("עוד", p["muted"])}<b class="{p["g"]}" style="{DISP} font-size: 60px; line-height: .8; color: {p["acc"]}">{{{{days}}}}</b>{L("ימים לטיסה", p["muted"])}')
    lab = lambda t: L(t, p['muted'])
    val = lambda t, ltr=False: f'<b class="{p["g"]}" style="font-size: 14px; color: {p["ink"]}"{" dir=" + chr(34) + "ltr" + chr(34) if ltr else ""}>{t}</b>'
    return (f'<div style="position: relative; display: flex; height: 230px; filter: drop-shadow(0 10px 14px rgba(0,0,0,{.42 if night else .2}))">'
            f'<div class="nl {p["pc"]}" style="position: relative; flex: 1; min-width: 0; background-color: {p["paper"]}; display: flex; flex-direction: column">'
            f'<div style="background: {p["strip"]}; color: {p["onstrip"]}; display: flex; justify-content: space-between; align-items: center; padding: 7px 16px; font-size: 12px; font-weight: 700; letter-spacing: .04em">'
            f'<span>הטיול שלך · {"הלוך" if out else "חזור"}</span><span>{d(f["date"])}</span></div>'
            f'<div style="padding: 10px 16px 12px; display: flex; flex-direction: column; gap: 10px">'
            f'<div style="display: flex; align-items: flex-end; justify-content: space-between; gap: 6px">'
            f'<div>{lab("מ")}<div class="{p["g"]}" style="{DISP} font-size: 50px; line-height: .8; color: {p["ink"]}" dir="ltr">{fc}</div><span style="font-size: 12.5px; font-weight: 600; color: {p["ink"]}">{f["from"]}</span></div>'
            f'<div style="align-self: center">{plane(28, p["acc"])}</div>'
            f'<div style="text-align: left">{lab("אל")}<div class="{p["g"]}" style="{DISP} font-size: 50px; line-height: .8; color: {p["ink"]}" dir="ltr">{tc}</div><span style="font-size: 12.5px; font-weight: 600; color: {p["ink"]}">{f["to"]}</span></div></div>'
            f'<div style="display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; border-top: 1px solid {p["rule"]}; padding-top: 8px">'
            f'<div>{lab("טיסה")}{val(f["flight"], True)}</div><div>{lab("המראה")}{val(f["departs"])}</div><div>{lab("נחיתה")}{val(f["arrives"])}</div></div>'
            f'<div style="display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; border-top: 1px solid {p["rule"]}; padding-top: 8px">'
            f'<div>{lab("נוסע")}{val(who)}</div><div>{lab("ימי סקי")}{val("11–14.1", True)}</div><div>{lab("חזור")}{val("15.1, בלילה")}</div></div></div></div>'
            f'<div class="nr {p["pc"]}" style="position: relative; width: 100px; flex: none; background-color: {p["paper2"]}; border-right: 2px dashed {p["dash"]}; '
            f'display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 12px 6px 10px; box-sizing: border-box; text-align: center">{stub}'
            f'<span class="{p["g"]}" style="{DISP} font-size: 20px; line-height: 1; margin-top: 4px; color: {p["ink"]}" dir="ltr">{fc} › {tc}</span></div></div>'
            + (f'<div style="display: flex; justify-content: space-between; align-items: center; margin-top: 10px; font-size: 12px; color: {p["muted"]}">'
               f'<span>נגיעה בספח תולשת · החלקה לכרטיס החזור</span>'
               f'<a href="#" style="display: inline-flex; align-items: center; gap: 4px; min-height: 44px; font-size: 13.5px; font-weight: 700; color: {p["acc"]}; text-decoration: none">{icon("edit", 17, p["acc"])}עריכה</a></div>' if edit else ''))


def empty_pass():
    """No trip yet: the same pass, blank, waiting to be filled in."""
    blank = lambda w: f'<span style="display: block; width: {w}px; height: 12px; margin-top: 5px; border-bottom: 2px dashed {C["dash"]}"></span>'
    return (f'<div style="position: relative; filter: drop-shadow(0 10px 14px rgba(0,0,0,.18))">'
            f'<div style="display: flex; height: 216px">'
            f'<div class="nl paper" style="position: relative; flex: 1; min-width: 0; background-color: {C["paper"]}; display: flex; flex-direction: column">'
            f'<div style="background: {C["blue"]}; color: #fff; display: flex; justify-content: space-between; padding: 7px 16px; font-size: 12px; font-weight: 700; letter-spacing: .04em">'
            f'<span>הטיול שלך</span><span>עוד לא הוגדר</span></div>'
            f'<div style="padding: 12px 16px; display: flex; flex-direction: column; gap: 10px">'
            f'<div style="display: flex; align-items: flex-end; justify-content: space-between">'
            f'<div>{L("מ")}<div style="{DISP} font-size: 50px; line-height: .8; color: #C3CEDA" dir="ltr">???</div></div>'
            f'<div style="align-self: center">{plane(28, "#C3CEDA")}</div>'
            f'<div style="text-align: left">{L("אל")}<div style="{DISP} font-size: 50px; line-height: .8; color: #C3CEDA" dir="ltr">???</div></div></div>'
            f'<div style="display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; border-top: 1px solid {C["rule"]}; padding-top: 8px">'
            f'<div>{L("טיסה")}{blank(54)}</div><div>{L("המראה")}{blank(40)}</div><div>{L("ימי סקי")}{blank(50)}</div></div></div></div>'
            f'<div class="nr paper" style="width: 100px; flex: none; background-color: {C["paper2"]}; border-right: 2px dashed {C["dash"]}; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 2px; text-align: center">'
            f'{L("עוד")}<b style="{DISP} font-size: 60px; line-height: .8; color: #C3CEDA">?</b>{L("ימים לטיסה")}</div></div>'
            f'<div style="position: absolute; inset: 64px 0 0 100px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px">'
            f'<button type="button" class="tap" style="display: inline-flex; align-items: center; gap: 8px; min-height: 52px; padding: 0 20px; background: {C["blue"]}; color: #fff; font-size: 16.5px; font-weight: 700; box-shadow: 0 6px 14px rgba(31,95,196,.35)">{icon("plus", 20, "#FFFFFF")}הוספת הטיסה שלי</button></div></div>'
            f'<div style="margin-top: 10px">{note("בלי הרשמה. נשמר רק בטלפון, ואפשר למחוק בכל רגע.", "lock")}</div>')


def season_board():
    """The state of the season, as on the site: no report yet, so the signs sleep under the snow (S3)."""
    return (f'<a href="#" style="position: relative; display: block; text-decoration: none; color: {C["ink"]}; background: {C["paper"]}; border-top: 6px solid {C["dash"]}; padding: 12px 14px; box-shadow: 0 6px 14px rgba(19,35,58,.10)">'
            f'{cap(358, 7, 22)}'
            f'<span style="display: flex; justify-content: space-between; align-items: baseline; gap: 10px"><b style="{DISP} font-size: 30px; line-height: 1">ההר עוד ישן</b>'
            f'<span style="font-size: 12.5px; font-weight: 700; color: {C["blue"]}">מצב הרכבלים</span></span>'
            f'<span style="display: block; margin-top: 4px; font-size: 13px; line-height: 1.45; color: {C["muted"]}">עוד אין דיווח על רכבלים. העונה נפתחת בדרך כלל בדצמבר.</span></a>')


SIGNS_HE = [
    ('מפת מסלולים', '27 מסלולים, פרטים וסרטונים', C['blue'], '90%', '#FFFFFF'),
    ('נקודת מפגש', 'בוחרים תחנה ושעה ושולחים לקבוצה', C['gold'], '82%', C['ink']),
    ('משחקים', 'הירידה, בית הספר לסקי, שלג טרי ועוד', C['green'], '86%', '#FFFFFF'),
]


def h1():
    signs = [sign(*s, snow=None) for s in SIGNS_HE] + [sign('קבוצה', 'יצירת קבוצה, או הצטרפות בקוד', C['ink'], '78%')]
    inner = (hero(False, 250) + app_head()
             + f'<div style="position: absolute; top: 132px; right: 16px; left: 16px">{empty_pass()}</div>'
             + f'<div style="position: absolute; top: 432px; right: 16px; left: 16px">{season_board()}</div>'
             + f'<div style="position: absolute; top: 548px; right: 0; left: 0">{post(signs, gap=12)}</div>')
    return screen(inner, h=900)


def h2():
    sec = lambda t, s: (f'<div style="display: flex; align-items: center; gap: 10px; margin: 6px 0 2px"><span style="{DISP} font-size: 30px; line-height: 1">{t}</span>'
                        f'<span style="flex: 1; height: 1px; background: {C["rule"]}"></span><span style="font-size: 12px; color: {C["muted"]}">{s}</span></div>')
    grid = lambda *fs: f'<div style="display: grid; grid-template-columns: repeat({len(fs)}, minmax(0, 1fr)); gap: 10px">{"".join(fs)}</div>'
    form = (sec('הלוך', 'חובה רק התאריך')
            + grid(field('תאריך', '10.1.2027', fid='od'), field('מספר טיסה', '6H 897', 'למשל 6H 897', 'of', 'ltr'))
            + grid(field('מ', 'TLV · תל אביב', fid='ofr'), field('אל', 'TBS · טביליסי', fid='oto'))
            + grid(field('המראה', '16:00', fid='odp'), field('נחיתה', '20:35', fid='oar'))
            + sec('חזור', 'אפשר גם בלי')
            + grid(field('תאריך', '15.1.2027', fid='rd'), field('מספר טיסה', '6H 892', fid='rf', dir_='ltr'))
            + grid(field('המראה', '01:35', fid='rdp'), field('נחיתה', '02:15', fid='rar'))
            + f'<div style="display: flex; justify-content: space-between; align-items: center; padding: 12px 14px; background: {C["paper"]}; border-top: 6px solid {C["blue"]}">'
              f'<span style="display: flex; flex-direction: column"><span style="font-size: 12px; font-weight: 600; color: {C["muted"]}">ימי סקי מלאים, מחושב מהטיסות</span>'
              f'<b style="{DISP} font-size: 30px; line-height: 1"><span dir="ltr">11–14.1</span> · 4 ימים</b></span><a href="#" style="font-size: 13.5px; font-weight: 700; min-height: 44px; display: inline-flex; align-items: center">שינוי</a></div>')
    inner = (topbar('הטיול שלך', 'ביטול')
             + f'<div style="padding: 0 16px 14px; font-size: 13.5px; color: {C["muted"]}">הטיסות שלך לגודאורי. בלי הרשמה: נשמר רק בטלפון.</div>'
             + f'<form style="display: flex; flex-direction: column; gap: 12px; padding: 0 16px">{form}'
             + f'<div style="display: flex; flex-direction: column; gap: 6px; margin-top: 8px">{btn("שמירה", "primary", "check")}{btn("מחיקת הטיול", "quiet")}</div></form>')
    return screen(inner, h=844)


def h3(night=False):
    signs = [sign(*s) for s in SIGNS_HE] + [sign('קבוצה', 'יצירת קבוצה, או הצטרפות בקוד', C['ink'] if not night else '#2C3E5C', '78%')]
    inner = (hero(night, 250) + app_head(night, '22:10' if night else '13:35')
             + f'<div style="position: absolute; top: 130px; right: 16px; left: 16px">{bpass(O, night=night)}</div>'
             + f'<div style="position: absolute; top: 452px; right: 0; left: 0">{post(signs, ink=C["nink"] if night else C["ink"], gap=12)}</div>')
    return screen(inner, h=844, night=night)


# ---------------------------------------------------------------- sign in and the account

def group_entry(dim=False):
    """The group sign opened as a guest: create (needs an account) or join with a code (doesn't)."""
    return (topbar('קבוצה')
            + f'<div style="padding: 0 16px 16px; font-size: 14px; line-height: 1.5; color: {C["muted"]}">בקבוצה רואים את הטיסות של כולם, קובעים מפגשים ומתחרים בשיאים.</div>'
            + f'<div style="display: flex; flex-direction: column; gap: 22px; padding: 18px 16px 0">'
            + f'<div style="position: relative; background: {C["paper"]}; border-top: 6px solid {C["blue"]}; padding: 16px; box-shadow: 0 6px 14px rgba(19,35,58,.10)">{cap(358, 31, 22)}'
              f'<b style="{DISP} font-size: 34px; line-height: 1">יש לי קוד הזמנה</b>'
              f'<p style="margin: 4px 0 12px; font-size: 13.5px; color: {C["muted"]}">שש אותיות, מהקישור שקיבלת. בלי הרשמה.</p>'
              f'<div style="display: flex; gap: 8px">{field("קוד הזמנה", "", "KZBQRM", "code", "ltr", mono=True).replace("display: flex; flex-direction: column", "display: flex; flex: 1; flex-direction: column")}'
              f'<span style="align-self: flex-end">{btn("הצטרפות", "primary", full=False)}</span></div></div>'
            + f'<div style="position: relative; background: {C["paper"]}; border-top: 6px solid {C["ink"]}; padding: 16px; box-shadow: 0 6px 14px rgba(19,35,58,.10)">{cap(358, 32, 22)}'
              f'<b style="{DISP} font-size: 34px; line-height: 1">יצירת קבוצה</b>'
              f'<p style="margin: 4px 0 12px; font-size: 13.5px; color: {C["muted"]}">מי שמנהל קבוצה צריך חשבון, כדי שההזמנות יישארו שלו גם בטלפון חדש. כניסה בנגיעה, עם החשבון שכבר בטלפון.</p>'
              f'{btn("יצירת קבוצה", "ink", "people")}</div>'
            + f'{note("כל השאר עובד בלי חשבון: המפה, הטיול שלך, המשחקים והשיאים.", "phone")}</div>')


def a1():
    return screen(group_entry(), h=844)


def login_sheet(ios=False):
    btns = (apple_btn() + google_btn()) if ios else google_btn()
    return (f'<b style="{DISP} font-size: 36px; line-height: 1">כניסה כדי ליצור קבוצה</b>'
            f'<p style="margin: 6px 0 16px; font-size: 14px; line-height: 1.5; color: {C["muted"]}">בלי סיסמה, ובלי מיילים מאיתנו. רק שם, ומה ש{"אפל או גוגל" if ios else "גוגל"} מעבירים.</p>'
            f'<div style="display: flex; flex-direction: column; gap: 10px">{btns}{btn("לא עכשיו, להמשיך כאורח", "quiet")}</div>'
            f'<div style="margin-top: 16px; padding-top: 14px; border-top: 1px solid {C["rule"]}; display: flex; flex-direction: column; gap: 10px">'
            f'{note("הטיול שלך, המועדפים והשיאים שכבר בטלפון עוברים לחשבון, ומופיעים גם באתר.", "cloud")}'
            f'{note("אפשר למחוק את החשבון בכל רגע, מההגדרות.", "trash")}'
            f'<a href="#" style="font-size: 13px; font-weight: 600; min-height: 44px; display: inline-flex; align-items: center">מדיניות הפרטיות</a></div>')


def a2():
    return screen(sheet(group_entry(), login_sheet(False), top=420), h=844)


def a3():
    return screen(sheet(group_entry(), login_sheet(True), top=360), h=844)


def a4():
    row = lambda ico, t, s, act: (f'<div style="display: flex; align-items: center; gap: 12px; min-height: 56px; border-bottom: 1px solid {C["rule"]}">{ico}'
                                  f'<span style="flex: 1; display: flex; flex-direction: column"><b style="font-size: 15px">{t}</b><span style="font-size: 12.5px; color: {C["muted"]}">{s}</span></span>{act}</div>')
    inner = (topbar('חשבון', 'הגדרות')
             + f'<div style="margin: 6px 16px 18px; padding: 14px 16px; background: {C["paper"]}; border-top: 6px solid {C["blue"]}; display: flex; align-items: center; gap: 14px">'
               f'<span style="width: 52px; height: 52px; background: {C["ink"]}; color: #fff; display: flex; align-items: center; justify-content: center; {DISP} font-size: 34px">פ</span>'
               f'<span style="display: flex; flex-direction: column"><b style="{DISP} font-size: 30px; line-height: 1">פיני זולברג</b><span style="font-size: 13px; color: {C["muted"]}">השם שמופיע בקבוצות · <a href="#">שינוי</a></span></span></div>'
             + f'<div style="padding: 0 16px"><h2 style="margin: 0 0 4px; {DISP} font-size: 28px">דרכי כניסה</h2>'
               f'<p style="margin: 0 0 6px; font-size: 13px; color: {C["muted"]}">כמה דרכים לאותו חשבון, כדי שלא ייווצר חשבון שני.</p>'
             + row(gmark(28), 'גוגל', 'מחובר', f'<span style="color: {C["green"]}">{icon("check", 22, C["green"])}</span>')
             + row(f'<span aria-hidden="true" style="width: 28px; height: 28px; border-radius: 50%; background: #000; color: #fff; display: inline-flex; align-items: center; justify-content: center; font: 700 15px/1 Arial">A</span>', 'אפל', 'לא מחובר', btn('חיבור', 'ghost', full=False, small=True))
             + f'<h2 style="margin: 22px 0 4px; {DISP} font-size: 28px">מה מסונכרן</h2>'
             + note('הטיול שלך, המועדפים, השיאים והקבוצות. בין הטלפון לאתר, ובטלפון חדש.', 'cloud')
             + f'<div style="display: flex; flex-direction: column; gap: 10px; margin-top: 24px">{btn("יציאה מהחשבון", "ghost", "out")}{btn("מחיקת החשבון", "danger", "trash")}</div>'
             + f'<p style="margin: 10px 0 0; font-size: 12.5px; line-height: 1.5; color: {C["muted"]}">מחיקה מוחקת מהשרת את החשבון, הטיול והחברות בקבוצות. קבוצה שרק את/ה מנהל/ת בה מבקשת קודם למנות מנהל אחר. גם מהאתר: <span dir="ltr">gudauri-ski-trip.vercel.app/account</span></p></div>')
    return screen(inner, h=844)


def a5():
    under = q6_body(joined=True)
    content = (f'<b style="{DISP} font-size: 36px; line-height: 1">ברוכים הבאים לקבוצה!</b>'
               f'<p style="margin: 6px 0 16px; font-size: 14px; line-height: 1.5; color: {C["muted"]}">הכניסה הייתה בלי הרשמה, וזה מספיק. רק מי שמחליף טלפון או מוחק את האפליקציה צריך לבקש מהמנהל לחזור. נגיעה אחת שומרת את המקום שלך:</p>'
               f'<div style="display: flex; flex-direction: column; gap: 10px">{google_btn("שמירה עם גוגל")}{btn("לא עכשיו", "quiet")}</div>'
               f'<p style="margin: 12px 0 0; font-size: 12.5px; color: {C["muted"]}">נשאל עוד פעם אחת בלבד, שבוע לפני הטיול.</p>')
    return screen(sheet(under, content, top=470), h=844)


# ---------------------------------------------------------------- the group

GROUP = 'גודאורי 2027'
CODE = 'KZBQRM'  # sample; letters only, Karantina's 7 reads like a Hebrew letter


def q1():
    inner = (topbar('קבוצה חדשה', 'ביטול')
             + f'<form style="display: flex; flex-direction: column; gap: 14px; padding: 8px 16px">'
             + field('שם הקבוצה', GROUP, fid='gn')
             + f'<div style="display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px">{field("מתאריך", "10.1.2027", fid="gf")}{field("עד תאריך", "15.1.2027", fid="gt")}</div>'
             + note('התאריכים של הקבוצה: הספירה לאחור, ותוקף ההזמנה עד הסוף שלהם.', 'clock')
             + f'<div style="margin-top: 6px">{toggle("הטיול שלי יופיע בקבוצה", "טיסה 6H 897, 10.1 · 16:00", True, "tm")}</div>'
             + f'<div style="margin-top: 10px">{btn("יצירת הקבוצה", "primary", "people")}</div></form>'
             + f'<div style="position: absolute; bottom: 22px; right: 16px; left: 16px">{note("את/ה המנהל/ת. מומלץ למנות עוד מנהל אחד לפחות, כדי שהקבוצה לא תישאר בלי מנהל.", "key")}</div>')
    return screen(inner, h=844)


def invite_card(big=True):
    letters = ''.join(f'<span style="flex: 1; display: flex; align-items: center; justify-content: center; height: 64px; background: {C["paper2"]}; border-bottom: 3px solid {C["blue"]}; {DISP} font-size: 46px; line-height: 1">{ch}</span>' for ch in CODE)
    return (f'<div class="paper" style="position: relative; background-color: {C["paper"]}; box-shadow: 0 10px 22px rgba(19,35,58,.16); transform: rotate(-1.2deg)">'
            f'{cap(358, 41, 24)}'
            f'<div style="background: {C["blue"]}; color: #fff; display: flex; justify-content: space-between; padding: 7px 16px; font-size: 12px; font-weight: 700; letter-spacing: .04em"><span>הזמנה לקבוצה</span><span>{GROUP}</span></div>'
            f'<div style="padding: 14px 16px 16px; display: flex; flex-direction: column; gap: 12px">'
            f'<span style="font-size: 13px; color: {C["muted"]}">הקישור פותח את האפליקציה, או את האתר למי שאין. מי שהתקין בדרך מקליד את הקוד:</span>'
            f'<div dir="ltr" aria-label="קוד ההזמנה {CODE}" style="display: flex; gap: 6px">{letters}</div>'
            f'<div style="display: flex; align-items: center; gap: 8px; padding: 10px 12px; border: 1.5px dashed {C["dash"]}; font-size: 13.5px">{icon("link", 18, C["muted"])}<span dir="ltr" style="flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap">gudauri-ski-trip.vercel.app/j/{CODE}</span>'
            f'<button type="button" class="tap" aria-label="העתקת הקישור" style="width: 44px; height: 44px; display: flex; align-items: center; justify-content: center">{icon("copy", 20, C["blue"])}</button></div></div></div>')


def q2():
    inner = (topbar('הזמנת חברים', 'לקבוצה')
             + f'<div style="padding: 22px 16px 0">{invite_card()}</div>'
             + f'<div style="display: flex; gap: 10px; padding: 18px 16px 0">{btn("וואטסאפ", "primary", "share")}{btn("שיתוף", "ghost", "share")}</div>'
             + f'<div style="padding: 18px 16px 0"><h2 style="margin: 0; {DISP} font-size: 28px">הגדרות ההזמנה · מנהלים</h2>'
             + toggle('אישור ידני לכל מצטרף', 'כבוי: כל מי שיש לו את הקישור נכנס', False, 'ap')
             + f'<div style="display: flex; justify-content: space-between; align-items: center; min-height: 52px; border-bottom: 1px solid {C["rule"]}"><span style="display: flex; flex-direction: column"><span style="font-size: 15.5px; font-weight: 600">תוקף</span><span style="font-size: 13px; color: {C["muted"]}">עד סוף הטיול, 15.1.2027</span></span></div>'
             + f'<div style="margin-top: 12px">{btn("ביטול הקישור והחלפה בחדש", "danger", "x")}</div></div>')
    return screen(inner, h=844)


def invited_card(inner_extra):
    return (f'<div style="position: relative; margin: 0 16px; background: {C["paper"]}; border-top: 6px solid {C["blue"]}; padding: 18px 16px; box-shadow: 0 10px 22px rgba(19,35,58,.14)">{cap(358, 51, 24)}'
            f'<span style="font-size: 13px; font-weight: 600; color: {C["muted"]}">פיני זולברג מזמין אותך ל</span>'
            f'<b style="display: block; {DISP} font-size: 50px; line-height: 1">{GROUP}</b>'
            f'<span style="display: flex; gap: 14px; margin-top: 6px; font-size: 13.5px; color: {C["muted"]}"><span dir="ltr">10–15.1.2027</span><span>6 חברים</span></span>{inner_extra}</div>')


def q3():
    form = (f'<form style="display: flex; flex-direction: column; gap: 12px; margin-top: 16px">'
            + field('איך קוראים לך בקבוצה?', '', 'השם שהחבר׳ה מכירים', 'nm')
            + btn('כניסה לקבוצה', 'primary') + '</form>')
    inner = (hero(False, 230) + app_head()
             + f'<div style="position: absolute; top: 150px; right: 0; left: 0">{invited_card(form)}'
             + f'<div style="padding: 18px 16px 0; display: flex; flex-direction: column; gap: 12px">'
             + note('בלי הרשמה ובלי סיסמה. אפשר לשמור חשבון אחר כך, אם רוצים.', 'lock')
             + f'<a href="#" style="display: flex; align-items: center; justify-content: space-between; min-height: 56px; padding: 0 14px; background: {C["paper"]}; color: {C["ink"]}; text-decoration: none; border: 1.5px solid {C["rule"]}">'
               f'<span style="display: flex; flex-direction: column"><b style="font-size: 15px">אני כבר בקבוצה</b><span style="font-size: 12.5px; color: {C["muted"]}">החלפתי טלפון, או מחקתי את האפליקציה</span></span>{icon("fwd", 20, C["blue"])}</a></div></div>')
    return screen(inner, h=844)


def q4():
    boxes = ''.join(f'<span style="flex: 1; height: 64px; display: flex; align-items: center; justify-content: center; background: {C["paper"]}; border: 2px solid {C["ink"] if i < 4 else C["rule"]}; {DISP} font-size: 44px">{ch}</span>'
                    for i, ch in enumerate('KZBQ' + '  '))
    inner = (topbar('הצטרפות בקוד', 'קבוצה')
             + f'<div style="padding: 8px 16px 0; font-size: 14px; line-height: 1.5; color: {C["muted"]}">התקנת מהקישור? החנות לא מעבירה את ההזמנה, אז מקלידים את הקוד מההודעה.</div>'
             + f'<div style="padding: 28px 16px 0"><label for="cd" style="font-size: 13px; font-weight: 600; color: {C["muted"]}">שש אותיות</label>'
             + f'<div dir="ltr" style="display: flex; gap: 6px; margin-top: 6px">{boxes}</div>'
             + f'<input id="cd" type="text" value="KZBQ" style="position: absolute; opacity: 0; width: 1px; height: 1px" aria-label="קוד הזמנה">'
             + f'<div style="margin-top: 18px">{btn("המשך", "primary")}</div>'
             + f'<div style="margin-top: 22px">{note("אותיות לטיניות וספרות. בלי 0 ו-O, בלי 1 ו-I, כדי שלא יתבלבלו.", "key")}</div></div>')
    return screen(inner, h=844)


def q5():
    names = CREW
    rows = ''.join(
        f'<label style="display: flex; align-items: center; gap: 12px; min-height: 52px; padding: 0 12px; background: {C["paper"] if n != "יהודה הרש" else "#E3ECF9"}; border: {"2px solid " + C["blue"] if n == "יהודה הרש" else "1px solid " + C["rule"]}; cursor: pointer">'
        f'<input type="radio" name="me"{" checked" if n == "יהודה הרש" else ""} style="width: 20px; height: 20px; accent-color: {C["blue"]}; margin: 0">'
        f'<span style="font-size: 15.5px; font-weight: 600">{n}</span></label>' for n in names if n != 'פיני זולברג')
    inner = (topbar('אני כבר בקבוצה', 'חזרה')
             + f'<div style="padding: 4px 16px 12px; font-size: 14px; line-height: 1.5; color: {C["muted"]}">בוחרים את השם שלך ב{GROUP}. מנהל מאשר, ואת/ה חוזר/ת עם כל מה שהיה לך: הטיסה, המפגשים והשיאים.</div>'
             + f'<fieldset style="border: 0; margin: 0; padding: 0 16px; display: flex; flex-direction: column; gap: 8px"><legend style="position: absolute; opacity: 0">מי את/ה</legend>{rows}</fieldset>'
             + f'<div style="padding: 16px 16px 0">{btn("שליחת בקשה למנהל", "primary")}</div>'
             + f'<div style="position: absolute; bottom: 20px; right: 16px; left: 16px; padding: 12px 14px; background: {C["paper"]}; border-top: 6px solid {C["gold"]}; display: flex; gap: 10px; align-items: center">'
               f'{icon("clock", 24, C["ink"])}<span style="display: flex; flex-direction: column"><b style="font-size: 14.5px">אחרי השליחה: ממתין לאישור</b><span style="font-size: 12.5px; color: {C["muted"]}">המנהלים מקבלים בקשה בדף הקבוצה. בינתיים כל השאר עובד.</span></span></div>')
    return screen(inner, h=844)


def mini_flight(f, people, filled=()):
    def chip(p):
        mark = icon('edit', 13, C['muted']) if p in filled else ''
        ring = f'; outline: 2px solid {C["blue"]}' if p.endswith('(את/ה)') else ''
        return f'<span style="display: inline-flex; align-items: center; gap: 4px; min-height: 30px; padding: 0 10px; background: {C["paper2"]}; font-size: 13px; font-weight: 600{ring}">{p}{mark}</span>'
    by = (f'<span style="display: flex; align-items: center; gap: 4px; margin-top: 6px; font-size: 12px; color: {C["muted"]}">{icon("edit", 13, C["muted"])}הוזן על ידי פיני (מנהל). דובי רואה את זה, ויכול לשנות.</span>' if filled else '')
    return (f'<div class="nl paper" style="position: relative; display: flex; background-color: {C["paper"]}; box-shadow: 0 6px 14px rgba(19,35,58,.10)">'
            f'<div style="flex: 1; min-width: 0; padding: 10px 14px 12px">'
            f'<div style="display: flex; align-items: center; gap: 8px"><b style="{DISP} font-size: 30px; line-height: 1" dir="ltr">{f[0]}</b>{plane(18, C["blue"])}<b style="{DISP} font-size: 30px; line-height: 1" dir="ltr">{f[1]}</b>'
            f'<span style="margin-inline-start: auto; font-size: 12.5px; color: {C["muted"]}" dir="ltr">{f[2]}</span></div>'
            f'<span style="display: block; font-size: 12.5px; color: {C["muted"]}">{f[3]}</span>'
            f'<div style="display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px">{"".join(chip(p) for p in people)}</div>{by}</div>'
            f'<div style="width: 64px; flex: none; border-right: 2px dashed {C["dash"]}; display: flex; flex-direction: column; align-items: center; justify-content: center; background: {C["paper2"]}">'
            f'<b style="{DISP} font-size: 34px; line-height: .9">{len(people)}</b><span style="font-size: 11px; font-weight: 600; color: {C["muted"]}">על הטיסה</span></div></div>')


FL_OUT = ('TLV', 'TBS', '6H 897', 'הלוך · 10.1 · 16:00, נחיתה 20:35')
FL_RET = ('TBS', 'TLV', '6H 892', 'חזור · 15.1 · 01:35, נחיתה 02:15')


def tabs(active):
    t = ['טיסות', 'מפגשים', 'שיאים', 'חברים']
    return ('<div role="tablist" style="display: flex; gap: 0; margin: 0 16px; border-bottom: 2px solid ' + C['ink'] + '">'
            + ''.join(f'<button type="button" role="tab" aria-selected="{"true" if x == active else "false"}" class="tap" style="flex: 1; min-height: 46px; text-align: center; font-size: 15px; font-weight: 700; '
                      f'{"background: " + C["ink"] + "; color: #fff" if x == active else "color: " + C["muted"]}">{x}</button>' for x in t) + '</div>')


def group_head(sub_days=True):
    return (f'<div style="position: relative; height: 150px">{hero(False, 150)}'
            f'<div style="position: absolute; top: 10px; right: 16px; left: 10px; display: flex; justify-content: space-between; align-items: center">'
            f'<a href="#" style="display: inline-flex; align-items: center; gap: 4px; min-height: 44px; font-size: 15px; font-weight: 700; color: {C["ink"]}; text-decoration: none">{icon("back", 20)}בית</a>'
            f'<a href="#" aria-label="הזמנת חברים" style="display: inline-flex; align-items: center; gap: 6px; min-height: 44px; padding: 0 12px; background: {C["paper"]}; color: {C["blue"]}; font-size: 14px; font-weight: 700; text-decoration: none">{icon("plus", 18, C["blue"])}הזמנה</a></div>'
            f'<div style="position: absolute; right: 16px; left: 16px; bottom: -26px; display: flex; align-items: stretch; background: {C["paper"]}; box-shadow: 0 8px 18px rgba(19,35,58,.16)">'
            f'<div style="flex: 1; padding: 10px 14px"><b style="display: block; {DISP} font-size: 40px; line-height: 1">{GROUP}</b><span style="font-size: 13px; color: {C["muted"]}"><span dir="ltr">10–15.1.2027</span> · 6 חברים · 2 מנהלים</span></div>'
            f'<div style="width: 84px; flex: none; background: {C["blue"]}; color: #fff; display: flex; flex-direction: column; align-items: center; justify-content: center">'
            f'<b style="{DISP} font-size: 44px; line-height: .85">{{{{days}}}}</b><span style="font-size: 11.5px; font-weight: 600">ימים</span></div></div></div>')


def q6_body(joined=False):
    crew = ['פיני' if joined else 'פיני (את/ה)', 'יהודה ו.', 'שרוליק', 'מוישי', 'דובי']
    who = 'יהודה הרש (את/ה)' if joined else 'יהודה הרש'
    act = btn('אני על אותה טיסה', 'primary', full=False, small=True) if joined else btn('מילוי בשבילו', 'ghost', full=False, small=True)
    return (group_head() + '<div style="height: 40px"></div>' + tabs('טיסות')
            + f'<div style="display: flex; flex-direction: column; gap: 12px; padding: 14px 16px">'
            + mini_flight(FL_OUT, crew, filled=('דובי',)) + mini_flight(FL_RET, crew, filled=('דובי',))
            + f'<div style="padding: 12px 14px; border: 2px dashed {C["dash"]}; display: flex; align-items: center; gap: 12px">'
              f'<span style="flex: 1; display: flex; flex-direction: column"><b style="font-size: 15px">{who}</b><span style="font-size: 12.5px; color: {C["muted"]}">עוד אין טיסה</span></span>{act}</div>'
            + note('הטיסות גלויות רק לחברי הקבוצה.', 'lock') + '</div>')


def q6():
    return screen(q6_body(), h=844)


def q7():
    pick = (f'<b style="{DISP} font-size: 36px; line-height: 1">אני על אותה טיסה</b>'
            f'<p style="margin: 6px 0 14px; font-size: 14px; color: {C["muted"]}">הטיסות שכבר בקבוצה. נגיעה אחת, והיא נכנסת גם לטיול שלך.</p>'
            f'<div style="display: flex; flex-direction: column; gap: 10px">'
            + ''.join(f'<button type="button" class="tap" style="display: flex; align-items: center; gap: 12px; min-height: 64px; padding: 8px 14px; background: {C["paper2"]}; border: {"2px solid " + C["blue"] if i == 0 else "1px solid " + C["rule"]}">'
                      f'<span style="flex: 1; display: flex; flex-direction: column"><span style="display: flex; align-items: center; gap: 6px"><b style="{DISP} font-size: 28px; line-height: 1" dir="ltr">{f[0]}</b>{plane(16, C["blue"])}<b style="{DISP} font-size: 28px; line-height: 1" dir="ltr">{f[1]}</b>'
                      f'<span style="font-size: 12.5px; color: {C["muted"]}; margin-inline-start: 6px" dir="ltr">{f[2]}</span></span><span style="font-size: 12.5px; color: {C["muted"]}">{f[3]} · {n}</span></span>'
                      f'{icon("check", 22, C["blue"]) if i == 0 else ""}</button>'
                      for i, (f, n) in enumerate([(FL_OUT, '5 מהקבוצה'), (('TBS', 'TLV', '6H 892', 'חזור · 15.1 · 01:35'), '5 מהקבוצה')]))
            + f'</div><div style="display: flex; flex-direction: column; gap: 6px; margin-top: 14px">{btn("שמירה", "primary", "check")}{btn("טיסה אחרת", "quiet")}</div>')
    return screen(sheet(q6_body(joined=True), pick, top=360), h=844)


def meetup(day, time, where, who, color, mins, remind=True, next_=False):
    return (f'<div class="paper" style="position: relative; display: flex; background-color: {C["paper"]}; box-shadow: 0 6px 14px rgba(19,35,58,.10){"; outline: 2px solid " + C["blue"] if next_ else ""}">'
            f'<div style="width: 76px; flex: none; display: flex; flex-direction: column; align-items: center; justify-content: center; border-left: 2px dashed {C["dash"]}; padding: 8px 0">'
            f'<span style="font-size: 12px; font-weight: 600; color: {C["muted"]}">{day}</span><b style="{DISP} font-size: 36px; line-height: .9">{time}</b></div>'
            f'<div style="flex: 1; min-width: 0; padding: 10px 14px">'
            f'<div style="display: flex; align-items: center; gap: 8px"><span style="width: 12px; height: 12px; flex: none; background: {color}"></span><b style="font-size: 15.5px">{where}</b></div>'
            f'<span style="display: block; font-size: 12.5px; color: {C["muted"]}">{who}{" · בעוד " + mins if mins else ""}</span>'
            f'<label style="display: flex; align-items: center; gap: 8px; margin-top: 6px; min-height: 36px; font-size: 13px; font-weight: 600; cursor: pointer">'
            f'<input type="checkbox"{" checked" if remind else ""} style="width: 18px; height: 18px; accent-color: {C["blue"]}; margin: 0">{icon("bell", 16)}תזכורת רבע שעה לפני</label></div></div>')


def q8():
    inner = (group_head() + '<div style="height: 40px"></div>' + tabs('מפגשים')
             + f'<div style="display: flex; flex-direction: column; gap: 12px; padding: 14px 16px">'
             + f'<span style="font-size: 13px; font-weight: 700; color: {C["muted"]}">היום · יום שני, 11.1</span>'
             + meetup('11.1', '09:30', 'תחתית Goodaura', 'קבע: פיני', C['blue'], 'שעה ו-12 דק׳', True, True)
             + meetup('11.1', '13:00', 'התחנה העליונה', 'קבע: שרוליק', C['red'], '', True)
             + meetup('11.1', '16:30', 'תחתית New Goodaura', 'קבע: מוישי', C['green'], '', False)
             + f'<span style="font-size: 13px; font-weight: 700; color: {C["muted"]}; margin-top: 4px">מחר · 12.1</span>'
             + meetup('12.1', '09:30', 'תחתית Goodaura', 'קבע: פיני', C['blue'], '', True)
             + f'{btn("מפגש חדש", "ink", "pin")}'
             + note('שמור בטלפון: המפגשים והתזכורות עובדים גם בלי קליטה על ההר.', 'phone') + '</div>')
    return screen(inner, h=900)


def q9():
    games = ['הירידה', 'בית הספר לסקי', 'שלג טרי', 'קרב כדורי שלג', 'איחוד כדורי שלג']
    chips = ''.join(f'<button type="button" class="tap" style="flex: none; min-height: 44px; padding: 0 14px; font-size: 14px; font-weight: 700; '
                    f'{"background: " + C["blue"] + "; color: #fff" if i == 0 else "background: " + C["paper"] + "; border: 1px solid " + C["rule"]}">{g}</button>' for i, g in enumerate(games))
    rows = [('פיני', '12,480', 'Tatra 2'), ('שרוליק', '11,920', 'Tatra 2'), ('מוישי', '9,310', 'Soliko 1'), ('יהודה ו.', '8,775', 'Tatra 2'), ('דובי', '6,040', 'Goodaura 1'), ('יהודה ה.', '—', '')]
    table = ''.join(
        f'<div style="display: flex; align-items: center; gap: 12px; min-height: 54px; padding: 0 12px; background: {C["paper"] if i else "#FFF6E0"}; border-bottom: 1px solid {C["rule"]}">'
        f'<b style="width: 28px; {DISP} font-size: 30px; line-height: 1; color: {C["gold"] if i == 0 else C["ink"]}; text-align: center">{i + 1 if s != "—" else ""}</b>'
        f'<span style="flex: 1; display: flex; flex-direction: column"><b style="font-size: 15px">{n}</b><span style="font-size: 12px; color: {C["muted"]}">{run}</span></span>'
        f'<b style="{DISP} font-size: 30px; line-height: 1" dir="ltr">{s}</b></div>' for i, (n, s, run) in enumerate(rows))
    inner = (group_head() + '<div style="height: 40px"></div>' + tabs('שיאים')
             + f'<div style="display: flex; gap: 8px; padding: 14px 16px 10px; overflow: hidden">{chips}</div>'
             + f'<div style="margin: 0 16px; box-shadow: 0 6px 14px rgba(19,35,58,.10)">{table}</div>'
             + f'<div style="padding: 12px 16px 0; display: flex; flex-direction: column; gap: 8px">{note("השיא הכי טוב של כל אחד, מכל משחק. נשלח לקבוצה כשיש קליטה.", "trophy")}'
             + f'<span style="font-size: 12px; color: {C["muted"]}">[נתוני דוגמה]</span></div>')
    return screen(inner, h=844)


def q10():
    req = (f'<div style="background: {C["paper"]}; border-top: 6px solid {C["gold"]}; padding: 12px 14px; display: flex; flex-direction: column; gap: 10px">'
           f'<span style="display: flex; flex-direction: column"><b style="font-size: 15.5px">יהודה הרש: "אני כבר בקבוצה"</b><span style="font-size: 12.5px; color: {C["muted"]}">טלפון חדש. אחרי אישור הוא חוזר עם הטיסה והשיאים, בלי כפיל.</span></span>'
           f'<div style="display: flex; gap: 8px">{btn("אישור", "primary", "check", small=True)}{btn("דחייה", "ghost", "x", small=True)}</div></div>')
    member = lambda n, role, me=False: (f'<div style="display: flex; align-items: center; gap: 12px; min-height: 56px; border-bottom: 1px solid {C["rule"]}">'
                                        f'<span style="width: 36px; height: 36px; flex: none; background: {C["ink"] if role else C["paper2"]}; color: {"#fff" if role else C["ink"]}; display: flex; align-items: center; justify-content: center; {DISP} font-size: 24px">{n[0]}</span>'
                                        f'<span style="flex: 1; display: flex; flex-direction: column"><b style="font-size: 15px">{n}{" (את/ה)" if me else ""}</b><span style="font-size: 12.5px; color: {C["muted"]}">{role or "חבר"}</span></span>'
                                        + ('' if me else f'<button type="button" class="tap" aria-label="פעולות על {n}" style="width: 44px; height: 44px; display: flex; align-items: center; justify-content: center; font-size: 22px; font-weight: 700; color: {C["muted"]}">⋮</button>')
                                        + '</div>')
    inner = (group_head() + '<div style="height: 40px"></div>' + tabs('חברים')
             + f'<div style="padding: 14px 16px 0; display: flex; flex-direction: column; gap: 6px"><span style="font-size: 13px; font-weight: 700; color: {C["muted"]}">בקשות · רק מנהלים רואים</span>{req}</div>'
             + f'<div style="padding: 14px 16px 0">'
             + member('פיני זולברג', 'מנהל', True) + member('שרוליק לפקוביץ׳', 'מנהל') + member('יהודה וצלר', '') + member('מוישי בוקצ׳ין', '') + member('דובי אלבום', '')
             + f'<div style="margin-top: 10px; display: flex; flex-direction: column; gap: 4px; font-size: 13px; color: {C["muted"]}">'
               f'<span>בתפריט של כל חבר: מינוי למנהל, הוצאה מהקבוצה.</span></div>'
             + f'<div style="display: flex; flex-direction: column; gap: 4px; margin-top: 14px">{btn("שם ותאריכים", "quiet", "edit")}{btn("הגדרות ההזמנה", "quiet", "link")}{btn("עזיבת הקבוצה", "quiet", "out")}{btn("מחיקת הקבוצה", "danger", "trash")}</div></div>')
    return screen(inner, h=1000)


# ---------------------------------------------------------------- privacy page on the site

def privacy_sections():
    """The Hebrew policy from docs/PRIVACY.md, section by section."""
    text = (root / 'docs/PRIVACY.md').read_text(encoding='utf-8')
    he = text.split('## עברית', 1)[1].split('## English', 1)[0]
    blocks = []
    for part in he.split('\n### ')[1:]:
        title, _, body = part.partition('\n')
        blocks.append((title.strip(), body.strip().strip('-').strip()))
    return blocks


def md_inline(s):
    import re
    s = s.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
    s = re.sub(r'\*\*(.+?)\*\*', r'<b>\1</b>', s)
    s = re.sub(r'`(.+?)`', r'<span dir="ltr">\1</span>', s)
    s = s.replace('pinisagent@gmail.com', '<a href="#" dir="ltr">pinisagent@gmail.com</a>')
    return s


def md_block(body):
    out, lst, ol = [], [], False
    for line in body.split('\n'):
        line = line.strip()
        if not line:
            continue
        if line.startswith('- ') or line[:3] in ('1. ', '2. ', '3. '):
            ol = line[0].isdigit()
            lst.append(f'<li style="margin-bottom: 6px">{md_inline(line[3:] if ol else line[2:])}</li>')
        else:
            if lst:
                out.append(f'<{"ol" if ol else "ul"} style="margin: 0 0 10px; padding-inline-start: 20px">{"".join(lst)}</{"ol" if ol else "ul"}>'); lst = []
            out.append(f'<p style="margin: 0 0 10px">{md_inline(line)}</p>')
    if lst:
        out.append(f'<{"ol" if ol else "ul"} style="margin: 0 0 10px; padding-inline-start: 20px">{"".join(lst)}</{"ol" if ol else "ul"}>')
    return ''.join(out)


def privacy_body(desktop=False):
    secs = privacy_sections()
    short = secs[0]
    rest = secs[1:]
    fs = 16 if desktop else 15
    sec_html = ''.join(f'<section style="padding: 18px 0 8px; border-top: 1px solid {C["rule"]}"><h2 style="margin: 0 0 8px; {DISP} font-size: {38 if desktop else 32}px; line-height: 1">{t}</h2>'
                       f'<div style="font-size: {fs}px; line-height: 1.65">{md_block(b)}</div></section>' for t, b in rest)
    lang = (f'<div role="group" aria-label="שפה" style="display: inline-flex; border: 2px solid {C["ink"]}">'
            f'<button type="button" class="tap" aria-pressed="true" style="min-height: 40px; padding: 0 14px; background: {C["ink"]}; color: #fff; font-weight: 700; font-size: 14px">עברית</button>'
            f'<button type="button" class="tap" aria-pressed="false" lang="en" style="min-height: 40px; padding: 0 14px; font-weight: 700; font-size: 14px">English</button></div>')
    head = (f'<div style="display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 10px">'
            f'<span style="font-size: 13px; color: {C["muted"]}">בתוקף מ-1.10.2026</span>{lang}</div>'
            f'<p style="margin: 12px 0 0; font-size: {fs}px; line-height: 1.65">אפליקציה לאנדרואיד ואתר עם מפת הסקי של גודאורי, גאורגיה. פיני זולברג, אדם פרטי מישראל, פיתח אותם ואחראי עליהם. לכל שאלה: <a href="#" dir="ltr">pinisagent@gmail.com</a>.</p>')
    unofficial = sign('אפליקציה לא רשמית', 'אין לה קשר ל-MTA, לאתר הסקי גודאורי או לגוף רשמי אחר', C['red'], '100%', h=70, size=32)
    short_box = (f'<div style="position: relative; margin-top: 26px; background: {C["paper"]}; border-top: 6px solid {C["blue"]}; padding: 16px 16px 6px; box-shadow: 0 6px 14px rgba(19,35,58,.10)">{cap(700 if desktop else 358, 61, 22)}'
                 f'<h2 style="margin: 0 0 8px; {DISP} font-size: 34px; line-height: 1">{short[0]}</h2><div style="font-size: {fs}px; line-height: 1.6">{md_block(short[1])}</div></div>')
    return head, unofficial, short_box, sec_html


def p1():
    head, unofficial, short_box, secs = privacy_body()
    inner = (f'<div style="display: flex; justify-content: space-between; align-items: center; padding: 12px 16px 6px">'
             f'<h1 style="margin: 0; {DISP} font-size: 44px; line-height: 1">מדיניות פרטיות</h1>'
             f'<a href="#" style="display: inline-flex; align-items: center; gap: 4px; min-height: 44px; font-size: 15px; font-weight: 700; text-decoration: none">{icon("back", 20)}אודות</a></div>'
             f'<div style="padding: 4px 16px 24px">{head}<div style="margin-top: 16px">{unofficial}</div>{short_box}{secs}'
             f'<p style="margin: 18px 0 0; font-size: 13px; color: {C["muted"]}">הכתובת: <span dir="ltr">gudauri-ski-trip.vercel.app/privacy</span> · קישור מעמוד האודות, מתחתית דף הבית ומדף האפליקציה בחנות.</p></div>')
    return screen(inner, h=2760)


def p2():
    head, unofficial, short_box, secs = privacy_body(True)
    nav = (f'<div style="height: 64px; background: {C["paper"]}; border-bottom: 1px solid {C["rule"]}; display: flex; align-items: center; gap: 28px; padding: 0 24px">'
           f'<span style="{DISP} font-size: 38px">גודאורי 2027</span><span style="font-size: 20px; font-weight: 600">בית</span><span style="font-size: 20px; font-weight: 600">מפת מסלולים</span>'
           f'<span style="font-size: 20px; font-weight: 600">נקודת מפגש</span><span style="font-size: 20px; font-weight: 600">משחקים</span>'
           f'<span style="margin-inline-start: auto">{icon("gear", 24)}</span></div>')
    body = (f'<div style="max-width: 760px; margin: 0 auto; padding: 26px 0 40px">'
            f'<h1 style="margin: 0 0 10px; {DISP} font-size: 64px; line-height: 1">מדיניות פרטיות</h1>{head}'
            f'<div style="margin-top: 18px; width: 70%">{unofficial}</div>{short_box}{secs}</div>')
    return (f'<div style="width: 1280px; height: 2240px; box-sizing: border-box; overflow: hidden; background: {C["snow"]}; color: {C["ink"]}; {BODY} direction: rtl">{nav}{body}</div>')


# ---------------------------------------------------------------- left-to-right signs, and fonts

RU_HEAD = "font-family: Oswald, 'Arial Narrow', sans-serif; font-weight: 600;"
KA_HEAD = "font-family: 'Noto Sans Georgian', sans-serif; font-weight: 800; font-stretch: 62.5%; font-variation-settings: 'wdth' 62.5;"
LAT_BODY = "font-family: 'IBM Plex Sans', 'Segoe UI', system-ui, sans-serif;"
KA_BODY = "font-family: 'Noto Sans Georgian', sans-serif;"

LANGS = {
    'en': dict(head=DISP, body=LAT_BODY, size=40, loc='Gudauri, Georgia', clock='Time in Gudauri', trip='Your trip', out='Outbound', days='days to go', more='more', frm='From', to='To',
               flight='Flight', dep='Departs', arr='Arrives', who='Traveller', me='Me', ski='Ski days', back='Return', tear='Tap the stub to tear it · swipe for the return pass', edit='Edit',
               signs=[('Trail map', '27 runs, details and videos'), ('Meeting point', 'Pick a station and a time, send it to the group'), ('Games', 'The descent, ski school, fresh snow and more'), ('Group', 'Create a group, or join with a code')],
               run='Tatra 2', runsub='Blue · 2.28 km · from the Tatra lift'),
    'ru': dict(head=RU_HEAD, body=LAT_BODY, size=34, loc='Гудаури, Грузия', clock='Время в Гудаури', trip='Ваша поездка', out='Туда', days='дней до вылета', more='ещё', frm='Откуда', to='Куда',
               flight='Рейс', dep='Вылет', arr='Прилёт', who='Пассажир', me='Я', ski='Дни катания', back='Обратно', tear='Нажмите на корешок, чтобы оторвать', edit='Изменить',
               signs=[('Карта трасс', '27 трасс, описание и видео'), ('Место встречи', 'Станция и время, и отправить группе'), ('Игры', 'Спуск, школа катания, свежий снег'), ('Группа', 'Создать группу или войти по коду')],
               run='Tatra 2', runsub='Синяя · 2,28 км · от подъёмника Tatra'),
    'ka': dict(head=KA_HEAD, body=KA_BODY, size=36, loc='გუდაური, საქართველო', clock='დრო გუდაურში', trip='თქვენი მოგზაურობა', out='წასვლა', days='დღე ფრენამდე', more='კიდევ', frm='საიდან', to='სად',
               flight='რეისი', dep='გაფრენა', arr='ჩაფრენა', who='მგზავრი', me='მე', ski='სათხილამურო დღეები', back='დაბრუნება', tear='შეეხეთ ნაწილს მოსახევად', edit='შეცვლა',
               signs=[('ტრასების რუკა', '27 ტრასა, დეტალები და ვიდეოები'), ('შეხვედრის ადგილი', 'სადგური და დრო, ჯგუფისთვის'), ('თამაშები', 'დაშვება, სათხილამურო სკოლა და სხვა'), ('ჯგუფი', 'ჯგუფის შექმნა ან კოდით შესვლა')],
               run='Tatra 2', runsub='ლურჯი · 2,28 კმ · საბაგირო Tatra'),
}


def ltr_screen(code):
    t = LANGS[code]
    cols = [(C['blue'], '#FFFFFF', '92%'), (C['gold'], C['ink'], '86%'), (C['green'], '#FFFFFF', '88%'), (C['ink'], '#FFFFFF', '80%')]
    signs = ''.join(sign(s, sub, c, w, fg, ltr=True, disp=t['head'], size=t['size'], h=74) for (s, sub), (c, fg, w) in zip(t['signs'], cols))
    lab = lambda x: f'<span style="display: block; font-size: 11px; font-weight: 600; letter-spacing: .04em; color: {C["muted"]}">{x}</span>'
    pass_ = (f'<div style="display: flex; height: 196px; filter: drop-shadow(0 10px 14px rgba(0,0,0,.2))">'
             f'<div class="nr paper" style="flex: 1; min-width: 0; background-color: {C["paper"]}; display: flex; flex-direction: column">'
             f'<div style="background: {C["blue"]}; color: #fff; display: flex; justify-content: space-between; padding: 7px 16px; font-size: 12px; font-weight: 700"><span>{t["trip"]} · {t["out"]}</span><span>10.1</span></div>'
             f'<div style="padding: 10px 16px; display: flex; flex-direction: column; gap: 10px">'
             f'<div style="display: flex; align-items: flex-end; justify-content: space-between">'
             f'<div>{lab(t["frm"])}<div style="{DISP} font-size: 50px; line-height: .8">TLV</div></div>'
             f'<div style="align-self: center; transform: scaleX(-1)">{plane(28, C["blue"])}</div>'
             f'<div style="text-align: right">{lab(t["to"])}<div style="{DISP} font-size: 50px; line-height: .8">TBS</div></div></div>'
             f'<div style="display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; border-top: 1px solid {C["rule"]}; padding-top: 8px">'
             f'<div>{lab(t["flight"])}<b style="font-size: 14px">6H 897</b></div><div>{lab(t["dep"])}<b style="font-size: 14px">16:00</b></div><div>{lab(t["arr"])}<b style="font-size: 14px">20:35</b></div></div></div></div>'
             f'<div class="nl paper" style="width: 100px; flex: none; background-color: {C["paper2"]}; border-left: 2px dashed {C["dash"]}; display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; padding: 0 4px">'
             f'<b style="{DISP} font-size: 60px; line-height: .8; color: {C["blue"]}">{{{{days}}}}</b>{lab(t["days"])}</div></div>')
    runsign = (f'<div style="display: flex; align-items: center; gap: 10px; margin: 0 16px; padding: 10px 40px 10px 14px; background: {C["blue"]}; color: #fff; '
               f'clip-path: polygon(0 0, calc(100% - 26px) 0, 100% 50%, calc(100% - 26px) 100%, 0 100%)">'
               f'<span style="width: 34px; height: 34px; flex: none; background: #fff; color: {C["blue"]}; display: flex; align-items: center; justify-content: center; {DISP} font-size: 26px">2</span>'
               f'<span style="display: flex; flex-direction: column"><b style="{t["head"]} font-size: {t["size"] - 6}px; line-height: 1">{t["run"]}</b><span style="font-size: 12.5px">{t["runsub"]}</span></span></div>')
    inner = (hero(False, 230)
             + f'<div style="position: absolute; top: 18px; left: 16px; right: 10px; display: flex; justify-content: space-between; align-items: center">'
               f'<span style="display: flex; flex-direction: column"><span style="{t["head"]} font-size: {t["size"] - 6}px; line-height: 1.05">{t["loc"]}</span>'
               f'<span style="font-size: 12px">{t["clock"]} <b style="font-size: 15px">13:35</b></span></span>{icon("gear", 22)}</div>'
             + f'<div style="position: absolute; top: 118px; left: 16px; right: 16px">{pass_}</div>'
             + f'<div style="position: absolute; top: 352px; left: 0; right: 0">'
               f'<nav aria-label="Sections" style="position: relative; display: flex; flex-direction: column; align-items: flex-start; gap: 12px; padding-left: 30px">'
               f'<span style="position: absolute; left: 20px; top: -8px; bottom: -30px; width: 6px; background: {C["ink"]}"></span>{signs}</nav></div>'
             + f'<div style="position: absolute; bottom: 22px; left: 0; right: 0">{runsign}</div>')
    lang = {'en': 'en', 'ru': 'ru', 'ka': 'ka'}[code]
    return (f'<div lang="{lang}" dir="ltr" style="width: 390px; height: 900px; box-sizing: border-box; position: relative; overflow: hidden; background: {C["snow"]}; color: {C["ink"]}; {t["body"]}">'
            f'{inner}</div>')


def ft1():
    """Fonts: what we have, and the candidates for Russian and Georgian headings, with their licenses."""
    ru = 'Карта трасс · Гудаури'
    ka = 'ტრასების რუკა · გუდაური'
    ru_body = 'Синяя трасса от подъёмника Tatra, 2,28 км. Начало пологое, в середине самый крутой участок, около 22°.'
    ka_body = 'ლურჯი ტრასა საბაგირო Tatra-დან, 2,28 კმ. დასაწყისი დამრეცია, შუაში ყველაზე ციცაბო მონაკვეთია, დაახლოებით 22°.'

    def card(tag, name, sample, style, lic, why, rec=False, size=54):
        sd = '' if any('\u0590' <= ch <= '\u05ff' for ch in sample) else ' dir="ltr" lang="und"'
        return (f'<div style="position: relative; display: flex; flex-direction: column; gap: 6px; padding: 14px 16px; background: {C["paper"]}; border-top: 6px solid {C["blue"] if rec else C["rule"]}">'
                f'<span style="display: flex; justify-content: space-between; align-items: baseline; gap: 10px; {BODY}" dir="rtl"><b style="font-size: 15px">{tag} {name}</b>'
                f'<span style="font-size: 12px; color: {C["muted"]}" dir="ltr">{lic}</span></span>'
                f'<span{sd} style="{style} font-size: {size}px; line-height: 1.05">{sample}</span>'
                f'<span style="{BODY} font-size: 13px; color: {C["muted"]}" dir="rtl">{why}</span>'
                + (f'<span style="position: absolute; top: -6px; left: 16px; background: {C["blue"]}; color: #fff; {BODY} font-size: 12px; font-weight: 700; padding: 3px 10px">המלצה</span>' if rec else '')
                + '</div>')
    col = lambda title, items: (f'<div style="display: flex; flex-direction: column; gap: 14px"><h2 style="margin: 0; {DISP} font-size: 40px; line-height: 1">{title}</h2>{"".join(items)}</div>')
    now = col('היום', [
        card('', 'Karantina 700', 'מפת מסלולים · Trail map', DISP, 'OFL 1.1', 'כותרות. עברית ולטינית בלבד: אין קירילית ואין גאורגית.'),
        card('', 'IBM Plex Sans Hebrew', 'כחול מהרכבל Tatra, 2.28 ק״מ.', BODY + ' font-weight: 400;', 'OFL 1.1', 'טקסט בעברית. לאנגלית ולרוסית: IBM Plex Sans (נקבע, החלטה 32).', size=24),
        card('', 'IBM Plex Sans', ru_body, LAT_BODY + ' font-weight: 400;', 'OFL 1.1', 'טקסט באנגלית וברוסית. אותה משפחה, אז הטקסט נראה אותו דבר בכל השפות.', size=19),
    ])
    rus = col('כותרות ברוסית', [
        card('א', 'Oswald 600', ru, "font-family: Oswald, sans-serif; font-weight: 600;", 'OFL 1.1 · Google Fonts', 'צר וגבוה כמו Karantina, יציב בגדלים קטנים. קירילית מלאה.', True),
        card('ב', 'Yanone Kaffeesatz 700', ru, "font-family: 'Yanone Kaffeesatz', sans-serif; font-weight: 700;", 'OFL 1.1 · Google Fonts', 'הכי קרוב באופי: קצוות מעוגלים וקצת יד. פחות קריא בשלטים קטנים.', size=58),
        card('ג', 'Fira Sans Extra Condensed 700', ru, "font-family: 'Fira Sans Extra Condensed', sans-serif; font-weight: 700;", 'OFL 1.1 · Google Fonts', 'הכי קריא ונקי, פחות אופי. יש לו קרוב לגאורגית (FiraGO).'),
    ])
    geo = col('גאורגית', [
        card('א', 'Noto Sans Georgian, צר, 800', ka, KA_HEAD, 'OFL 1.1 · Google Fonts', 'כותרות. ציר רוחב עד 62.5%, כך שהוא צר כמו Karantina. אחד משלושה גופנים בלבד עם גאורגית ב-Google Fonts.', True, size=46),
        card('', 'Noto Sans Georgian, רגיל, 400', ka_body, KA_BODY + ' font-weight: 400;', 'OFL 1.1 · Google Fonts', 'טקסט בגאורגית. אותה משפחה כמו הכותרות.', size=18),
        card('ב', 'FiraGO', 'מאוחסן אצלנו', BODY + ' font-weight: 600; color: ' + C['muted'] + ';', 'OFL 1.1 · GitHub, לא ב-Google Fonts', 'חלופה: Fira עם גאורגית, מתאים לכותרות ב-Fira (ג). דורש לארוז את הקבצים באפליקציה ובאתר.', size=22),
    ])
    note_ = (f'<p style="margin: 0; {BODY} font-size: 14px; color: {C["muted"]}" dir="rtl">נבדק ב-1.10.2026 מול רשימת Google Fonts ותיקיות הרישיון במאגר שלהם. Google Sans תומך בגאורגית, אבל הוא גופן מותג של גוגל, ולכן לא מוצע. '
             f'הטקסטים ברוסית ובגאורגית הם טיוטה לבדיקת דובר.</p>')
    return (f'<div style="width: 1440px; height: 800px; box-sizing: border-box; overflow: hidden; background: {C["snow"]}; color: {C["ink"]}; {BODY} direction: rtl; padding: 32px">'
            f'<div style="display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 28px">{now}{rus}{geo}</div>'
            f'<div style="margin-top: 22px">{note_}</div></div>')


# ---------------------------------------------------------------- write

BOARDS = []  # (file, title, page, w, h, html)


def add(name, title, pg, html_, w=390, h=844, **kw):
    BOARDS.append((name, title, pg, w, h, html_, kw))


def build():
    days = DAYS
    add('H1-GuestHome.dc.html', 'דף הבית: אורח, בלי טיול', 'round10', page('דף הבית: אורח, בלי טיול', h1(), h=900), h=900)
    add('H2-TripForm.dc.html', 'הוספת הטיול שלך', 'round10', page('הוספת הטיול שלך', h2(), h=844))
    add('H3-TripHome.dc.html', 'דף הבית: עם הטיול שלך', 'round10', page('דף הבית: עם הטיול שלך', h3(), js=days))
    add('H4-TripHomeNight.dc.html', 'דף הבית: עם הטיול, בלילה', 'round10', page('דף הבית: עם הטיול, בלילה', h3(True), js=days, night=True))
    add('A1-GroupEntry.dc.html', 'קבוצה: אורח', 'round10', page('קבוצה: אורח', a1()))
    add('A2-SignInAndroid.dc.html', 'כניסה באנדרואיד', 'round10', page('כניסה באנדרואיד', a2()))
    add('A3-SignInIphone.dc.html', 'כניסה באייפון', 'round10', page('כניסה באייפון', a3()))
    add('A4-Account.dc.html', 'חשבון', 'round10', page('חשבון', a4()))
    add('A5-SaveAccount.dc.html', 'הצעה לשמור חשבון', 'round10', page('הצעה לשמור חשבון', a5(), js=days))

    add('Q1-NewGroup.dc.html', 'קבוצה חדשה', 'round10q', page('קבוצה חדשה', q1()))
    add('Q2-Invite.dc.html', 'הזמנה: קישור וקוד', 'round10q', page('הזמנה: קישור וקוד', q2()))
    add('Q3-Invited.dc.html', 'מוזמן: רק שם ונכנסים', 'round10q', page('מוזמן: רק שם ונכנסים', q3()))
    add('Q4-Code.dc.html', 'הצטרפות בקוד', 'round10q', page('הצטרפות בקוד', q4()))
    add('Q5-AlreadyIn.dc.html', 'אני כבר בקבוצה', 'round10q', page('אני כבר בקבוצה', q5()))
    add('Q6-GroupFlights.dc.html', 'דף הקבוצה: טיסות', 'round10q', page('דף הקבוצה: טיסות', q6(), js=days))
    add('Q7-SameFlight.dc.html', 'אני על אותה טיסה', 'round10q', page('אני על אותה טיסה', q7(), js=days))
    add('Q8-Meetups.dc.html', 'מפגשים ותזכורת', 'round10q', page('מפגשים ותזכורת', q8(), h=900, js=days), h=900)
    add('Q9-Scores.dc.html', 'טבלת שיאים', 'round10q', page('טבלת שיאים', q9(), js=days))
    add('Q10-Admin.dc.html', 'חברים וניהול', 'round10q', page('חברים וניהול', q10(), h=1000, js=days), h=1000)

    add('P1-PrivacyPhone.dc.html', 'מדיניות פרטיות: טלפון', 'round10p', page('מדיניות פרטיות', p1(), h=2760), h=2760)
    add('P2-PrivacyDesktop.dc.html', 'מדיניות פרטיות: מחשב', 'round10p', page('מדיניות פרטיות במחשב', p2(), w=1280, h=2240), w=1280, h=2240)
    add('LT1-English.dc.html', 'משמאל לימין: אנגלית', 'round10p', page('Left to right: English', ltr_screen('en'), h=900, js=days, fonts=FONTS_INTL, lang='en', d='ltr'), h=900)
    add('LT2-Russian.dc.html', 'משמאל לימין: רוסית', 'round10p', page('Слева направо: русский', ltr_screen('ru'), h=900, js=days, fonts=FONTS_INTL, lang='ru', d='ltr'), h=900)
    add('LT3-Georgian.dc.html', 'משמאל לימין: גאורגית', 'round10p', page('მარცხნიდან მარჯვნივ: ქართული', ltr_screen('ka'), h=900, js=days, fonts=FONTS_INTL, lang='ka', d='ltr'), h=900)
    add('FT1-Fonts.dc.html', 'גופנים לרוסית ולגאורגית', 'round10p', page('גופנים לרוסית ולגאורגית', ft1(), w=1440, h=800, fonts=FONTS_INTL), w=1440, h=800)


PAGES = [('round10', 'סבב 10: הטיול שלך וחשבון'), ('round10q', 'סבב 10: קבוצה'), ('round10p', 'סבב 10: פרטיות ושפות')]
ROWS = {  # page -> rows of files, each with its title
    'round10': [('דף הבית של אורח והטיול שלך', ['H1-GuestHome.dc.html', 'H2-TripForm.dc.html', 'H3-TripHome.dc.html', 'H4-TripHomeNight.dc.html']),
                ('כניסה: גוגל, אפל, או אורח', ['A1-GroupEntry.dc.html', 'A2-SignInAndroid.dc.html', 'A3-SignInIphone.dc.html', 'A4-Account.dc.html', 'A5-SaveAccount.dc.html'])],
    'round10q': [('יצירה, הזמנה והצטרפות בלי הרשמה', ['Q1-NewGroup.dc.html', 'Q2-Invite.dc.html', 'Q3-Invited.dc.html', 'Q4-Code.dc.html', 'Q5-AlreadyIn.dc.html']),
                 ('דף הקבוצה: טיסות, מפגשים, שיאים וניהול', ['Q6-GroupFlights.dc.html', 'Q7-SameFlight.dc.html', 'Q8-Meetups.dc.html', 'Q9-Scores.dc.html', 'Q10-Admin.dc.html'])],
    'round10p': [('דף מדיניות הפרטיות באתר', ['P1-PrivacyPhone.dc.html', 'P2-PrivacyDesktop.dc.html']),
                 ('שלטים משמאל לימין', ['LT1-English.dc.html', 'LT2-Russian.dc.html', 'LT3-Georgian.dc.html']),
                 ('גופנים לרוסית ולגאורגית', ['FT1-Fonts.dc.html'])],
}


def layout(index):
    by = {b[0]: b for b in BOARDS}
    for pid, name in PAGES:
        if not any(p['id'] == pid for p in index['pages']):
            index['pages'].insert(0, {'id': pid, 'name': name})
    order = [p for p, _ in PAGES]
    index['pages'].sort(key=lambda p: order.index(p['id']) if p['id'] in order else len(order))
    for pid, rows in ROWS.items():
        y = 0
        for ri, (title, files) in enumerate(rows):
            x, rowh, roww = 0, 0, 0
            for f in files:
                name, btitle, pg, w, h, _, _ = by[f]
                index['boards'][f] = {'x': x, 'y': y, 'w': w, 'h': h, 'page': pg, 'title': btitle}
                if f not in index['order']:
                    index['order'].append(f)
                x += w + 80
                rowh = max(rowh, h)
            roww = x - 80
            index['notes'][f'{pid}row{ri}'] = {'kind': 'title1', 'maxW': max(860, roww), 'page': pid, 'text': title, 'w': 240, 'x': 0, 'y': y - 300}
            y += rowh + 120 + 300
    index['launch'] = {'view': 'canvas', 'page': 'round10'}
    return index


if __name__ == '__main__':
    dst = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else root / 'design/canvas/project'
    build()
    for name, _, _, _, _, html_, _ in BOARDS:
        (dst / name).write_text(html_, encoding='utf-8')
    ip = dst / 'canvas.json'
    if ip.exists():
        index = json.loads(ip.read_text(encoding='utf-8'))
        ip.write_text(json.dumps(layout(index), ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(len(BOARDS), 'boards ->', dst)
