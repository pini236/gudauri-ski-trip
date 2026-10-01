#!/usr/bin/env python3
"""Round 9: the app icon and the store feature graphic (docs/PLAY.md, the critical path of the closed test).
Three icon directions and a feature graphic, drawn from the app's own language: the trail sign, the real ridge
(design/round9/skyline.json, from skyline.py) and the run painted in slope colours. Writes the SVGs and
index.html (the review page) into design/round9/. Run from the repo root: python3 design/round9/build.py"""
import json, math, pathlib
here = pathlib.Path(__file__).resolve().parent
SKY = json.load(open(here / 'skyline.json'))

INK, BLUE, GOLD, SNOW = '#13233A', '#1F5FC4', '#F4B942', '#EEF2F5'
GREEN_S, YEL_S, ORG_S, RED_S = '#3FA85F', '#F2C13D', '#F08A3C', '#DC3B33'   # the slope colours of the map

def ridge(x0, x1, base, k, lift=3.0, smooth=2):
    """The skyline as path points: azimuth -> x, elevation angle -> height (exaggerated by k)."""
    a = [p[1] for p in SKY]
    s = [sum(a[max(0, i - smooth):i + smooth + 1]) / len(a[max(0, i - smooth):i + smooth + 1]) for i in range(len(a))]
    n = len(s)
    return [(x0 + (x1 - x0) * i / (n - 1), base - (v - lift) * k) for i, v in enumerate(s)]

def poly(pts, close_to=None):
    d = 'M' + ' L'.join(f'{x:.1f} {y:.1f}' for x, y in pts)
    if close_to is not None: d += f' L{pts[-1][0]:.1f} {close_to} L{pts[0][0]:.1f} {close_to} Z'
    return d

def run_path(pts):
    return 'M' + ' C'.join(f'{a[0]} {a[1]} {b[0]} {b[1]} {c[0]} {c[1]}' for a, b, c in pts[1:]).join([f'{pts[0][0]} {pts[0][1]} ', '']) if False else None

# one S-shaped run, top to bottom, as cubic segments
RUN = 'M292 262 C 262 288, 330 318, 292 350 C 258 380, 218 392, 240 452'

def run_ribbon(width_case, width_paint, grad_id):
    return (f'<path d="{RUN}" fill="none" stroke="#fff" stroke-width="{width_case}" stroke-linecap="round" stroke-linejoin="round"/>'
            f'<path d="{RUN}" fill="none" stroke="url(#{grad_id})" stroke-width="{width_paint}" stroke-linecap="round" stroke-linejoin="round"/>')

def slope_gradient(gid):
    return (f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="0" y1="262" x2="0" y2="452">'
            f'<stop offset="0" stop-color="{GREEN_S}"/><stop offset=".30" stop-color="{YEL_S}"/><stop offset=".55" stop-color="{ORG_S}"/>'
            f'<stop offset=".78" stop-color="{YEL_S}"/><stop offset="1" stop-color="{GREEN_S}"/></linearGradient>')

def snow_cap(cx, cy, w):
    """Fresh snow on top of a sign, as on the site: soft heaps and two drips."""
    return (f'<path d="M{cx-w/2} {cy+6} q{w*0.06} -26 {w*0.2} -22 q{w*0.1} -22 {w*0.26} -8 q{w*0.14} -20 {w*0.24} 2 q{w*0.14} -6 {w*0.14} 28 '
            f'l-6 6 l-6 10 l-8 -8 l-{w*0.5} 0 l-8 12 l-8 -14 Z" fill="#fff"/>')

# ---------- A: the trail sign ----------
def icon_a():
    r = ridge(0, 512, 330, 16, 3.4)
    far = [(x, y - 10) for x, y in r]
    return f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">
<defs><linearGradient id="skyA" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#9FC3E6"/><stop offset="1" stop-color="#EAF2F8"/></linearGradient></defs>
<rect width="512" height="512" fill="url(#skyA)"/>
<circle cx="392" cy="138" r="30" fill="{GOLD}"/>
<path d="{poly(far, 512)}" fill="#C9D6E3"/>
<path d="{poly(r, 512)}" fill="#B2C3D4"/>
<path d="M0 392 Q140 360 256 380 T512 372 L512 512 L0 512 Z" fill="#fff"/>
<rect x="244" y="268" width="24" height="170" fill="{INK}"/>
<path d="M118 190 H378 L418 232 L378 274 H118 Z" transform="translate(512 0) scale(-1 1)" fill="{BLUE}"/>
<path d="M394 214 l-34 18 l34 18 Z" fill="#fff" opacity="0"/>
<path d="M150 232 l46 -30 v18 h124 v24 H196 v18 Z" fill="#fff"/>
{snow_cap(254, 192, 300)}
</svg>'''

# ---------- B: the ridge and the run ----------
def icon_b():
    r = ridge(-20, 532, 290, 20, 3.4)
    front = [(x, y + 34) for x, y in ridge(-20, 532, 290, 20, 3.4)]
    return f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">
<defs>
<linearGradient id="skyB" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#0D1522"/><stop offset=".55" stop-color="#1F3B73"/><stop offset="1" stop-color="#6E5D86"/></linearGradient>
<linearGradient id="snowB" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#F4F8FC"/><stop offset="1" stop-color="#C9D6E3"/></linearGradient>
{slope_gradient('slopeB')}
</defs>
<rect width="512" height="512" fill="url(#skyB)"/>
<circle cx="190" cy="168" r="36" fill="{GOLD}"/>
<circle cx="190" cy="168" r="58" fill="{GOLD}" opacity=".18"/>
<path d="{poly(r, 512)}" fill="#8FA3C2"/>
<path d="{poly(front, 512)}" fill="url(#snowB)"/>
{run_ribbon(52, 34, 'slopeB')}
</svg>'''

# ---------- C: the plain mark ----------
def icon_c():
    r = ridge(-20, 532, 318, 24, 3.4)
    return f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">
<defs>{slope_gradient('slopeC')}</defs>
<rect width="512" height="512" fill="{BLUE}"/>
<circle cx="372" cy="150" r="30" fill="{GOLD}"/>
<path d="{poly(r, 512)}" fill="#fff"/>
{run_ribbon(58, 38, 'slopeC')}
</svg>'''

# ---------- the feature graphic, 1024 x 500 ----------
def feature():
    r = ridge(-20, 1044, 215, 30, 3.4)
    front = [(x, y + 40) for x, y in ridge(-20, 1044, 215, 30, 3.4)]
    return f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 500">
<defs>
<linearGradient id="skyF" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#1F5FC4"/><stop offset=".62" stop-color="#9FC3E6"/><stop offset="1" stop-color="#F1DDC2"/></linearGradient>
<linearGradient id="snowF" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFFFFF"/><stop offset="1" stop-color="#DCE7F2"/></linearGradient>
<linearGradient id="slopeF" gradientUnits="userSpaceOnUse" x1="0" y1="250" x2="0" y2="480"><stop offset="0" stop-color="{GREEN_S}"/><stop offset=".3" stop-color="{YEL_S}"/><stop offset=".55" stop-color="{ORG_S}"/><stop offset=".8" stop-color="{YEL_S}"/><stop offset="1" stop-color="{GREEN_S}"/></linearGradient>
</defs>
<rect width="1024" height="500" fill="url(#skyF)"/>
<circle cx="800" cy="140" r="46" fill="{GOLD}"/>
<circle cx="800" cy="140" r="78" fill="#fff" opacity=".22"/>
<path d="{poly(r, 500)}" fill="#8FA3C2"/>
<path d="{poly(front, 500)}" fill="url(#snowF)"/>
<g transform="translate(120 -10)">
<path d="M292 262 C 262 288, 330 318, 292 350 C 258 380, 218 392, 240 470" fill="none" stroke="#fff" stroke-width="40" stroke-linecap="round"/>
<path d="M292 262 C 262 288, 330 318, 292 350 C 258 380, 218 392, 240 470" fill="none" stroke="url(#slopeF)" stroke-width="26" stroke-linecap="round"/>
</g>
<g transform="translate(500 288)">
<path d="M0 0 H400 L440 54 L400 108 H0 Z" fill="{INK}"/>
<path d="M0 -6 q18 -24 42 -18 q22 -22 50 -6 q26 -20 52 0 q30 -10 56 8 q24 -14 50 4 l-2 14 H0 Z" fill="#fff"/>
<text x="204" y="82" text-anchor="middle" font-family="Karantina, 'Arial Narrow', sans-serif" font-weight="700" font-size="82" fill="#fff" letter-spacing="2">GUDI</text>
</g>
<text x="704" y="452" text-anchor="middle" font-family="Karantina, 'Arial Narrow', sans-serif" font-weight="700" font-size="40" fill="{INK}" letter-spacing="3">GUDAURI SKI MAP</text>
</svg>'''

ICONS = {'a': ('A. השלט', 'שלט המסלול מהאתר, עם שלג טרי, על רקע הרכס והשמש. הכי קרוב לשפה של האתר.', icon_a()),
         'b': ('B. הרכס והמסלול', 'הרכס האמיתי של גודאורי בערב, והמסלול צבוע בצבעי השיפוע כמו באפליקציה. המלצה.', icon_b()),
         'c': ('C. הסימן הפשוט', 'כחול, הרכס בלבן ומסלול צבוע. הכי קריא בגודל קטן, וקרוב לאייקון של האתר.', icon_c())}
for k, (_, _, svg) in ICONS.items(): (here / f'icon-{k}.svg').write_text(svg, encoding='utf-8')
(here / 'feature.svg').write_text(feature(), encoding='utf-8')

def masks(svg, name):
    """The same icon as the phone launcher shows it: circle, squircle and rounded square, and small."""
    sq = f'<div class="m" style="border-radius:50%;width:96px;height:96px">{svg}</div><div class="m" style="border-radius:30%;width:96px;height:96px">{svg}</div><div class="m" style="border-radius:22%;width:96px;height:96px">{svg}</div>'
    small = ''.join(f'<div class="m" style="border-radius:50%;width:{s}px;height:{s}px">{svg}</div>' for s in (48, 36, 24))
    return f'<div class="row">{sq}</div><div class="row small">{small}</div>'

cards = ''
for k, (title, note, svg) in ICONS.items():
    cards += f'<section class="card"><h2>{title}</h2><p>{note}</p><div class="big">{svg}</div>{masks(svg, k)}</section>'
TOKENS = """
:root{--snow:#EEF2F5;--paper:#FFFFFF;--ink:#13233A;--muted:#4B5A6F;--rule:#CBD5DF}
@media (prefers-color-scheme:dark){:root:not([data-theme="light"]){--snow:#0D1522;--paper:#16223A;--ink:#EAF0F7;--muted:#A3B3C8;--rule:#2A3B55;color-scheme:dark}}
:root[data-theme="dark"]{--snow:#0D1522;--paper:#16223A;--ink:#EAF0F7;--muted:#A3B3C8;--rule:#2A3B55;color-scheme:dark}
"""
CSS = """
body{background:var(--snow);color:var(--ink);font-family:'IBM Plex Sans Hebrew',system-ui,sans-serif;margin:0;padding-inline:16px;padding-block:20px 48px}
main{max-width:1100px;margin:0 auto}
h1{font-family:Karantina,'Arial Narrow',sans-serif;font-weight:700;font-size:44px;margin:8px 0 4px;text-wrap:balance}
h2{font-family:Karantina,'Arial Narrow',sans-serif;font-weight:700;font-size:32px;margin:0}
p{color:var(--muted);margin:4px 0 12px;line-height:1.5;max-width:70ch}
.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,300px),1fr));gap:16px}
.card{background:var(--paper);border:1px solid var(--rule);padding:16px;min-width:0}
.big{width:100%;max-width:300px;margin:0 auto 12px;aspect-ratio:1;border-radius:22%;overflow:hidden}
.big svg,.m svg{display:block;width:100%;height:100%}
.row{display:flex;gap:12px;align-items:center;justify-content:center;margin:10px 0}
.m{overflow:hidden;flex:none;border:1px solid var(--rule)}
.feat{margin-top:24px;background:var(--paper);border:1px solid var(--rule);padding:16px}
.feat svg{width:100%;height:auto;display:block}
"""
BODY_HTML = f'''<main>
<h1>סמל ותמונה ראשית לחנות</h1>
<p>שלושה כיוונים לסמל, מצוירים משפת העיצוב של האפליקציה: שלט המסלול, הרכס האמיתי של גודאורי (מנתוני הגובה של האתר) ומסלול צבוע בצבעי השיפוע. כל סמל מוצג גדול, כפי שהטלפון חותך אותו (עיגול, ריבוע מעוגל) ובגודל קטן. אפשר לצאת מכאן עם בחירה אחת.</p>
<div class="grid">{cards}</div>
<section class="feat"><h2>התמונה הראשית (1024 על 500)</h2><p>בכיוון B, עם השלט והשם הזמני. השם עוד פתוח: כאן הגיבוי Gudi.</p>{feature()}</section>
</main>'''
FONT_LINK = '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Karantina:wght@700&family=IBM+Plex+Sans+Hebrew:wght@400;600;700&display=swap">'
# the review page on its own, and the fragment the Artifact tool wraps in its own skeleton
(here / 'index.html').write_text(f'<!doctype html>\n<html lang="he" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">\n<title>סמל לאפליקציה</title>\n{FONT_LINK}\n<style>{TOKENS}{CSS}</style></head><body>{BODY_HTML}</body></html>', encoding='utf-8')
(here / 'artifact.html').write_text(f'<title>סמל לאפליקציה</title>\n{FONT_LINK}\n<style>{TOKENS}{CSS}</style>\n<div dir="rtl" lang="he">{BODY_HTML}</div>', encoding='utf-8')
print('written', [p.name for p in sorted(here.glob('*.svg'))] + ['index.html'])
