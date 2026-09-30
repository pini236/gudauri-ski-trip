#!/usr/bin/env python3
"""Round 3 design screens: richer versions of run view, lift status, day and night, and meeting point.

Numbers come from the site's data (design/round3/data.json, design/round2/sun-stats.json).
Lift status is sample data and is marked so on screen. Run from the repo root with numpy:
python3 design/round3/build.py"""
import json, math, base64, pathlib
import numpy as np
root = pathlib.Path(__file__).resolve().parent.parent.parent
DATA = json.load(open(root / 'design/round3/data.json', encoding='utf-8'))
SUN = json.load(open(root / 'design/round2/sun-stats.json', encoding='utf-8'))
T = json.load(open(root / 'site/data/terrain.json', encoding='utf-8'))
ST, T2 = DATA['stats'], DATA['tatra2']
A = {'s09': '/_blob/918c78a6d85918a5dcbb7d31388ab657', 's12': '/_blob/27f863a8b3c3734d1b91b58cf1ba14d5',
     's1630': '/_blob/aa98fe0850d854afdd550f90185650cd', 'f1': '/_blob/aca40335b34c14663d104a75d5a6c2d3'}
IMG_W, IMG_H = 780, 1569
C = {'snow': '#EEF2F5', 'paper': '#FFFFFF', 'ink': '#13233A', 'muted': '#4B5A6F', 'rule': '#CBD5DF', 'blue': '#1F5FC4',
     'green': '#1B8A4C', 'red': '#D1342B', 'black': '#13233A', 'accent': '#F4B942', 'nbg': '#0D1522', 'npaper': '#16223A', 'nink': '#EAF0F7', 'nmuted': '#A3B3C8', 'nrule': '#2A3B55'}
DISP = "font-family: Karantina, 'Arial Narrow', sans-serif; font-weight: 700;"
BODY = "font-family: 'IBM Plex Sans Hebrew', 'Segoe UI', system-ui, sans-serif;"
FONTS = '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Karantina:wght@700&amp;family=IBM+Plex+Sans+Hebrew:wght@400;500;600;700&amp;display=swap">'
BACK = '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 12h14"></path><path d="M13 6l6 6-6 6"></path></svg>'
SHARE = '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="18" cy="5" r="3"></circle><circle cx="6" cy="12" r="3"></circle><circle cx="18" cy="19" r="3"></circle><path d="M8.6 13.5l6.8 4M15.4 6.5l-6.8 4"></path></svg>'
ROOT = f"width: 390px; height: 844px; box-sizing: border-box; overflow: hidden; position: relative; background: {C['snow']}; color: {C['ink']}; {BODY} direction: rtl;"

def page(title, body, script='class Component extends DCLogic {\n  renderVals() { return {}; }\n}', css=''):
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
a{{color:{C['blue']}}}
{css}
@media (prefers-reduced-motion: reduce){{ .anim, .anim *{{ animation: none !important; transition: none !important }} }}
</style>
</helmet>
{body}
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{{"$preview":{{"width":390,"height":844}}}}'>
{script}
</script>
</body>
</html>
'''

def sign(text, color, size=34, pad='6px 16px 6px 30px', fg='#FFFFFF'):
    return f'<div style="display: inline-flex; align-items: center; padding: {pad}; background: {color}; color: {fg}; {DISP} font-size: {size}px; line-height: 1; clip-path: polygon(0 50%, 18px 0, 100% 0, 100% 100%, 18px 100%)"><span dir="ltr">{text}</span></div>'

def topbar(title, back='לכל המפה', dark=False):
    bg, rule, ink = (C['npaper'], C['nrule'], C['nink']) if dark else (C['paper'], C['rule'], C['ink'])
    return f'<div style="display: flex; align-items: center; justify-content: space-between; padding: 8px 16px 8px 8px; background: {bg}; border-bottom: 1px solid {rule}; color: {ink}"><span style="{DISP} font-size: 30px; line-height: 1">{title}</span><a href="#" style="display: flex; align-items: center; gap: 6px; min-height: 44px; padding: 0 8px; font-size: 15px; font-weight: 600; text-decoration: none; color: {"#8DB9FF" if dark else C["blue"]}">{BACK}{back}</a></div>'

def slope_col(a):
    return '#3FA85F' if a < 15 else '#F2C13D' if a < 25 else '#F08A3C' if a < 30 else '#DC3B33'

def map_img(src, k, left, top, h, alt, extra=''):
    return (f'<div style="position: relative; height: {h}px; flex: none; overflow: hidden; background: #DCE8F1">'
            f'<img src="{src}" alt="{alt}" style="position: absolute; left: {left:.1f}px; top: {top:.1f}px; width: {IMG_W*k:.1f}px; height: {IMG_H*k:.1f}px; max-width: none; display: block">{extra}</div>')

def tr(k, left, top):
    return lambda px, py: (px * k + left, py * k + top)

def line_svg(f, h, pts, colored=True, width=6, extra_attr=''):
    segs = ''
    for i in range(len(pts) - 1):
        (x1, y1), (x2, y2) = f(pts[i]['px'], pts[i]['py']), f(pts[i + 1]['px'], pts[i + 1]['py'])
        col = slope_col(pts[i]['a']) if colored else C['blue']
        segs += f'<line x1="{x1:.1f}" y1="{y1:.1f}" x2="{x2:.1f}" y2="{y2:.1f}" stroke="{col}" stroke-width="{width}" stroke-linecap="round"></line>'
    casing = ' '.join(f'{f(p["px"], p["py"])[0]:.1f},{f(p["px"], p["py"])[1]:.1f}' for p in pts)
    return f'<svg width="390" height="{h}" viewBox="0 0 390 {h}" aria-hidden="true" style="position: absolute; left: 0; top: 0" {extra_attr}><polyline points="{casing}" fill="none" stroke="#FFFFFF" stroke-width="{width + 4}" stroke-linecap="round" stroke-linejoin="round"></polyline>{segs}</svg>'

HEB = {'green': 'ירוק', 'blue': 'כחול', 'red': 'אדום', 'black': 'שחור'}
t2 = ST['Tatra 2']; t2_drop = round(SUN['pistes']['Tatra 2']['drop'])
out = {}

# =================================================================== T1: cinematic entry, animated (ת1)
# Tap the run: the mountain dims, the run paints itself top to bottom in slope colours, then the camera settles and the sheet rises.
xs = [p['px'] for p in T2]; ys = [p['py'] for p in T2]
k1 = 0.62; MH = 783
l1 = min(0, 195 - (min(xs) + max(xs)) / 2 * k1); tp1 = max(MH - IMG_H * k1, 330 - (min(ys) + max(ys)) / 2 * k1); f1 = tr(k1, l1, tp1)
RCX, RCY = f1((min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2)
Z1 = 1.35
sp = [f1(p['px'], p['py']) for p in T2]
ptxt = ' '.join(f'{x:.1f},{y:.1f}' for x, y in sp)
pd = 'M' + ' L'.join(f'{x:.1f} {y:.1f}' for x, y in sp)
segs1 = ''.join(f'<line x1="{sp[i][0]:.1f}" y1="{sp[i][1]:.1f}" x2="{sp[i+1][0]:.1f}" y2="{sp[i+1][1]:.1f}" stroke="{slope_col(T2[i]["a"])}" stroke-width="5" stroke-linecap="round"></line>' for i in range(len(sp) - 1))
paint = (f'<svg width="390" height="{MH}" viewBox="0 0 390 {MH}" aria-hidden="true" style="position: absolute; left: 0; top: 0">'
         f'<defs><mask id="t1paint" maskUnits="userSpaceOnUse" x="0" y="0" width="390" height="{MH}"><polyline points="{ptxt}" fill="none" stroke="#FFFFFF" stroke-width="16" stroke-linecap="round" stroke-linejoin="round" pathLength="1" stroke-dasharray="1 1" style="stroke-dashoffset: {{{{ dash }}}}; transition: stroke-dashoffset {{{{ dashDur }}}}s cubic-bezier(.45,0,.3,1)"></polyline></mask></defs>'
         f'<polyline points="{ptxt}" fill="none" stroke="#FFFFFF" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"></polyline>'
         f'<polyline points="{ptxt}" fill="none" stroke="{C["blue"]}" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"></polyline>'
         f'<g mask="url(#t1paint)"><polyline points="{ptxt}" fill="none" stroke="#FFFFFF" stroke-width="9" stroke-linecap="round" stroke-linejoin="round"></polyline>{segs1}</g></svg>')
# the glowing brush tip that leads the paint
tip = (f'<span style="position: absolute; left: 0; top: 0; width: 18px; height: 18px; margin: -9px 0 0 -9px; border-radius: 9px; background: #FFFFFF; box-shadow: 0 0 0 4px rgba(255,255,255,0.35), 0 0 18px 6px rgba(255,255,255,0.8); '
       f'offset-path: path(\'{pd}\'); offset-rotate: 0deg; offset-anchor: 9px 9px; offset-distance: {{{{ tipAt }}}}%; opacity: {{{{ tipOp }}}}; transition: offset-distance {{{{ dashDur }}}}s cubic-bezier(.45,0,.3,1), opacity 0.3s"></span>')
tx, ty = sp[len(sp) // 3]
tapbtn = (f'<button type="button" onClick="{{{{ go }}}}" aria-label="Tatra 2" style="position: absolute; left: {tx-30:.0f}px; top: {ty-30:.0f}px; width: 60px; height: 60px; border: 0; padding: 0; background: transparent; opacity: {{{{ tapOp }}}}; pointer-events: {{{{ tapPe }}}}; transition: opacity 0.3s">'
          f'<span class="ring" style="position: absolute; inset: 8px; border-radius: 50%; border: 3px solid {C["accent"]}"></span><span style="position: absolute; inset: 22px; border-radius: 50%; background: {C["accent"]}; border: 2px solid {C["ink"]}"></span></button>'
          f'<span style="position: absolute; left: {tx+26:.0f}px; top: {ty-16:.0f}px; padding: 4px 10px; background: {C["ink"]}; color: #FFFFFF; font-size: 13px; font-weight: 700; white-space: nowrap; opacity: {{{{ tapOp }}}}; transition: opacity 0.3s">לחצו על המסלול</span>')
flag = lambda x, y, t: f'<span style="position: absolute; left: {x+12:.0f}px; top: {y-12:.0f}px; padding: 2px 7px; background: #FFFFFF; border: 1.5px solid {C["ink"]}; font-size: 12px; font-weight: 700; opacity: {{{{ flagOp }}}}; transition: opacity 0.4s 0.3s" dir="ltr">{t}</span>'
flags = flag(*sp[0], f'{t2["top"]:,} מ׳') + flag(*sp[-1], f'{t2["bot"]:,} מ׳')
beats = ''.join(f'<span style="flex: 1; display: flex; flex-direction: column; gap: 4px; font-size: 12px; font-weight: 700; color: {{{{ b{i}c }}}}; transition: color 0.3s"><span style="height: 3px; background: {{{{ b{i}c }}}}; transition: background-color 0.3s"></span>{t}</span>' for i, t in enumerate(['בחירה', 'צביעה', 'נחיתה']))
sheet = (f'<div style="position: absolute; left: 0; right: 0; bottom: 0; background: {C["paper"]}; border-top: 1px solid {C["rule"]}; box-shadow: 0 -10px 30px rgba(19,35,58,0.18); padding: 12px 20px 18px; display: flex; flex-direction: column; gap: 12px; transform: translateY({{{{ sheetY }}}}px); transition: transform 0.6s cubic-bezier(.2,.8,.2,1)">'
         f'<span style="align-self: center; width: 40px; height: 4px; border-radius: 2px; background: {C["rule"]}"></span>'
         f'<div style="display: flex; align-items: center; justify-content: space-between">{sign("Tatra 2", C["blue"], 38)}<span style="font-size: 13px; color: {C["muted"]}">כחול · רכבל Shino</span></div>'
         f'<div style="display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px">'
         + ''.join(f'<div style="display: flex; flex-direction: column"><span style="font-size: 12px; color: {C["muted"]}">{a}</span><b style="{DISP} font-size: 32px; line-height: 1">{b}</b></div>' for a, b in [('אורך', f'{t2["len"]:,} מ׳'), ('ירידה', f'{t2_drop} מ׳'), ('הכי תלול', f'{t2["steep"]}%')])
         + f'</div><button type="button" onClick="{{{{ replay }}}}" style="height: 48px; border: 1.5px solid {C["ink"]}; background: transparent; font: inherit; font-size: 15px; font-weight: 700; color: {C["ink"]}">לראות שוב את המעבר</button></div>')
body = (f'<div class="anim" style="{ROOT} display: flex; flex-direction: column">'
        f'<div style="display: flex; align-items: center; justify-content: space-between; padding: 8px 16px 8px 8px; background: {C["paper"]}; border-bottom: 1px solid {C["rule"]}; height: 44px; flex: none"><span style="position: relative; height: 30px; flex-grow: 1; overflow: hidden"><span style="position: absolute; right: 0; {DISP} font-size: 30px; line-height: 1; opacity: {{{{ t0op }}}}; transform: translateY({{{{ t0y }}}}px); transition: opacity 0.4s, transform 0.4s">מפת המסלולים</span><span style="position: absolute; right: 0; {DISP} font-size: 30px; line-height: 1; opacity: {{{{ t1op }}}}; transform: translateY({{{{ t1y }}}}px); transition: opacity 0.4s, transform 0.4s" dir="ltr">Tatra 2</span></span>'
        f'<span style="display: flex; gap: 6px; width: 150px; flex: none">{beats}</span></div>'
        f'<div style="position: relative; height: {MH}px; flex: none; overflow: hidden; background: #DCE8F1">'
        f'<div style="position: absolute; inset: 0; transform-origin: {RCX:.0f}px {RCY:.0f}px; transform: translateY({{{{ camY }}}}px) scale({{{{ camZ }}}}); transition: transform 0.9s cubic-bezier(.3,0,.2,1)">'
        f'<img src="{A["s12"]}" alt="מפת ההר, Tatra 2 מסומן" style="position: absolute; left: {l1:.1f}px; top: {tp1:.1f}px; width: {IMG_W*k1:.1f}px; height: {IMG_H*k1:.1f}px; max-width: none">'
        f'<div style="position: absolute; inset: 0; background: #0D1522; opacity: {{{{ dim }}}}; transition: opacity 0.5s"></div>{paint}{tip}{flags}</div>{tapbtn}{sheet}</div></div>')
css1 = '.ring{animation:ring 1.6s ease-out infinite} @keyframes ring{0%{transform:scale(.7);opacity:1}100%{transform:scale(1.5);opacity:0}}'
script = f'''const Z = {Z1}, CAMY = -70;
class Component extends DCLogic {{
  state = {{ step: 0 }};
  play() {{
    this.setState({{ step: 1 }});
    clearTimeout(this.t); this.t = setTimeout(() => this.setState({{ step: 2 }}), 1500);
  }}
  renderVals() {{
    const s = this.state.step, on = '#13233A', off = '#CBD5DF';
    return {{
      dash: s === 0 ? 1 : 0, dashDur: s === 0 ? 0 : 1.3, tipAt: s === 0 ? 0 : 100, tipOp: s === 1 ? 1 : 0,
      dim: s === 0 ? 0 : 0.42, flagOp: s === 2 ? 1 : 0, tapOp: s === 0 ? 1 : 0, tapPe: s === 0 ? 'auto' : 'none',
      camZ: s === 2 ? Z : 1, camY: s === 2 ? CAMY : 0, sheetY: s === 2 ? 0 : 320,
      t0op: s === 2 ? 0 : 1, t0y: s === 2 ? -14 : 0, t1op: s === 2 ? 1 : 0, t1y: s === 2 ? 0 : 14,
      b0c: on, b1c: s >= 1 ? on : off, b2c: s >= 2 ? on : off,
      go: () => this.play(),
      replay: () => {{ clearTimeout(this.t); this.setState({{ step: 0 }}); this.t = setTimeout(() => this.play(), 700); }},
    }};
  }}
}}'''
out['T1-RunEntry.dc.html'] = page('כניסה למסלול', body, script, css1)

# =================================================================== T2: profile drives the mountain (ת2)
k2 = 0.72; l2 = 195 - 213 * k2; tp2 = 190 - 905 * k2; f2 = tr(k2, l2, tp2)
pos = [dict(zip(['x', 'y'], [round(v, 1) for v in f2(p['px'], p['py'])])) for p in T2]
W, Hc = 350, 110
hmax, hmin, dmax = max(p['h'] for p in T2), min(p['h'] for p in T2), T2[-1]['d']
def cx(d): return 20 + d / dmax * (W - 20)
def cy(h): return 8 + (hmax - h) / (hmax - hmin) * (Hc - 24)
prof_pts = ' '.join(f'{cx(p["d"]):.1f},{cy(p["h"]):.1f}' for p in T2)
bands = ''.join(f'<rect x="{cx(T2[i]["d"]):.1f}" y="{Hc-10}" width="{cx(T2[i+1]["d"])-cx(T2[i]["d"]):.1f}" height="6" fill="{slope_col(T2[i]["a"])}"></rect>' for i in range(len(T2) - 1))
chart = (f'<svg width="{W}" height="{Hc}" viewBox="0 0 {W} {Hc}" aria-hidden="true" style="display: block; direction: ltr">'
         f'<polygon points="20,{Hc-12} {prof_pts} {W},{Hc-12}" fill="{C["blue"]}" fill-opacity="0.12"></polygon>'
         f'<polyline points="{prof_pts}" fill="none" stroke="{C["blue"]}" stroke-width="2.4"></polyline>{bands}'
         f'<text x="0" y="12" font-size="10.5" fill="{C["muted"]}">{hmax}</text><text x="0" y="{Hc-14}" font-size="10.5" fill="{C["muted"]}">{hmin}</text>'
         f'<line x1="{{{{ lineX }}}}" y1="0" x2="{{{{ lineX }}}}" y2="{Hc-10}" stroke="{C["ink"]}" stroke-width="1.4" stroke-dasharray="3 3"></line>'
         f'<circle cx="{{{{ lineX }}}}" cy="{{{{ dotY }}}}" r="6" fill="#FFFFFF" stroke="{C["ink"]}" stroke-width="2.4"></circle></svg>')
skier = f'<div class="anim" style="position: absolute; left: {{{{ mx }}}}px; top: {{{{ my }}}}px; width: 0; height: 0; transition: left 0.15s, top 0.15s"><span style="position: absolute; left: -13px; top: -13px; width: 26px; height: 26px; border-radius: 13px; background: {C["ink"]}; border: 3px solid #FFFFFF; box-shadow: 0 2px 6px rgba(19,35,58,0.5)"></span><span style="position: absolute; left: -32px; top: -46px; width: 64px; text-align: center; padding: 2px 0; background: #FFFFFF; border: 1.5px solid {C["ink"]}; font-size: 12px; font-weight: 700" dir="ltr">{{{{ hLbl }}}}</span></div>'
legend = '<div style="position: absolute; top: 10px; right: 10px; display: flex; flex-direction: column; gap: 3px; padding: 6px 9px; background: rgba(255,255,255,0.94); border: 1px solid #CBD5DF; font-size: 11.5px; font-weight: 600">' + ''.join(f'<span style="display: flex; align-items: center; gap: 5px"><span style="width: 12px; height: 4px; background: {c}"></span>{t}</span>' for c, t in [('#3FA85F', 'עד 15°'), ('#F2C13D', '15°–25°'), ('#F08A3C', '25°–30°'), ('#DC3B33', 'מעל 30°')]) + '</div>'
body = (f'<div style="{ROOT} display: flex; flex-direction: column">{topbar("Tatra 2")}'
        + map_img(A['s12'], k2, l2, tp2, 380, 'Tatra 2 על ההר, צבוע לפי שיפוע', '<div style="position: absolute; inset: 0; background: rgba(238,242,245,0.35)"></div>' + line_svg(f2, 380, T2, True, 6) + skier + legend)
        + f'<div style="flex-grow: 1; background: {C["paper"]}; padding: 12px 20px 0; display: flex; flex-direction: column; gap: 6px">'
        f'<div style="display: flex; align-items: baseline; justify-content: space-between"><h2 style="margin: 0; {DISP} font-size: 28px; line-height: 1">גררו לאורך המסלול</h2><span style="font-size: 12px; color: {C["muted"]}">הגולש על ההר זז איתכם</span></div>'
        f'<div style="position: relative">{chart}<input type="range" min="0" max="{len(T2)-1}" value="{{{{ i }}}}" onInput="{{{{ onPos }}}}" aria-label="מיקום לאורך המסלול" style="position: absolute; left: 0; right: 0; top: 0; width: 100%; height: {Hc}px; margin: 0; opacity: 0; cursor: ew-resize"></div>'
        f'<div style="display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; border-top: 1px solid {C["rule"]}; padding-top: 10px">'
        + ''.join(f'<div style="display: flex; flex-direction: column"><span style="font-size: 12px; color: {C["muted"]}">{a}</span><span style="{DISP} font-size: 30px; line-height: 1">{{{{ {b} }}}}</span></div>' for a, b in [('מההתחלה', 'dLbl'), ('גובה', 'hLbl2'), ('שיפוע כאן', 'aLbl')])
        + f'</div><p style="margin: 0; font-size: 11.5px; color: {C["muted"]}">גובה ושיפוע ממודל הגובה (כ-30 מ׳). הקו הכי ארוך של המסלול.</p></div></div>')
script = f'''const PTS = {json.dumps([{**p, **q} for p, q in zip(T2, pos)])};
const W = {W}, H = {Hc}, HMAX = {hmax}, HMIN = {hmin}, DMAX = {dmax};
class Component extends DCLogic {{
  state = {{ i: 16 }};
  renderVals() {{
    const p = PTS[this.state.i];
    const lineX = 20 + p.d / DMAX * (W - 20), dotY = 8 + (HMAX - p.h) / (HMAX - HMIN) * (H - 24);
    return {{ i: this.state.i, mx: p.x, my: p.y, lineX, dotY, hLbl: p.h.toLocaleString('en-US') + ' מ׳',
      dLbl: p.d.toLocaleString('en-US') + ' מ׳', hLbl2: p.h.toLocaleString('en-US') + ' מ׳', aLbl: Math.round(p.a) + '°',
      onPos: (e) => this.setState({{ i: parseInt(e.target.value, 10) }}) }};
  }}
}}'''
out['T2-RunProfile.dc.html'] = page('פרופיל שמזיז את ההר', body, script)

# =================================================================== T3: flyover + briefing + comparison (ת3, ת5, ת6)
steep_d = t2['steepAt']
moments = [('התחלה', f'{t2["top"]:,} מ׳', 'יוצאים מהצומת עם Tatra 1', '#3FA85F'),
           ('הקטע התלול', f'{t2["steep"]}% שיפוע', f'אחרי {steep_d} מ׳, כ-100 מ׳ רצוף', '#F2C13D'),
           ('הסוף', f'{t2["bot"]:,} מ׳', 'נגמר בתחנה התחתונה של רכבל Shino', C['blue'])]
RAIL = f'<span style="flex-grow: 1; width: 3px; background: {C["rule"]}"></span>'
mrows = ''.join(f'<div style="display: flex; gap: 12px; align-items: stretch"><div style="display: flex; flex-direction: column; align-items: center; width: 18px"><span style="width: 14px; height: 14px; border-radius: 7px; background: {col}; border: 2.5px solid {C["ink"]}; box-sizing: border-box; flex: none"></span>{"" if i == 2 else RAIL}</div><div style="display: flex; flex-direction: column; padding-bottom: 10px"><span style="font-size: 12.5px; color: {C["muted"]}">{a}</span><span style="{DISP} font-size: 26px; line-height: 1">{b}</span><span style="font-size: 13px">{c}</span></div></div>' for i, (a, b, c, col) in enumerate(moments))
body = (f'<div style="{ROOT} display: flex; flex-direction: column">{topbar("Tatra 2")}'
        f'<div style="position: relative; height: 320px; flex: none; overflow: hidden"><img src="{A["f1"]}" alt="מבט נמוך מעל Tatra 2" style="position: absolute; left: 0; top: -40px; width: 390px; height: 440px; object-fit: cover">'
        f'<div style="position: absolute; left: 0; right: 0; bottom: 0; padding: 10px 12px; background: linear-gradient(transparent, rgba(19,35,58,0.85)); display: flex; align-items: center; gap: 10px; color: #FFFFFF">'
        f'<button type="button" aria-label="עצירה" style="width: 44px; height: 44px; flex: none; border: 0; border-radius: 22px; background: #FFFFFF; color: {C["ink"]}; display: flex; align-items: center; justify-content: center"><svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><rect x="5" y="4" width="5" height="16"></rect><rect x="14" y="4" width="5" height="16"></rect></svg></button>'
        f'<div style="flex-grow: 1; display: flex; flex-direction: column; gap: 5px"><span style="font-size: 13px; font-weight: 600">טסים במורד Tatra 2 · הקטע התלול</span><div style="height: 5px; background: rgba(255,255,255,0.3)"><div style="width: 38%; height: 5px; background: {C["accent"]}"></div></div></div><span style="font-size: 12.5px; font-weight: 600" dir="ltr">0:11 / 0:30</span></div></div>'
        f'<div style="flex-grow: 1; background: {C["paper"]}; padding: 14px 20px 0; display: flex; flex-direction: column; gap: 8px">'
        f'<h2 style="margin: 0; {DISP} font-size: 30px; line-height: 1">מה מחכה לך</h2>{mrows}'
        f'<div style="margin-top: 2px; padding: 10px 12px; background: {C["snow"]}; border: 1px solid {C["rule"]}; font-size: 14px; line-height: 1.4"><b>להשוואה:</b> תלול כמו <b dir="ltr">Goodaura 1</b> ({ST["Goodaura 1"]["steep"]}%), וארוך כמו <b dir="ltr">Goodaura 2</b> (2.32 ק״מ).</div>'
        f'<p style="margin: 0; font-size: 11.5px; color: {C["muted"]}">מספרים ממודל הגובה. ההשוואה לפי הקטע התלול ביותר ולפי האורך.</p></div></div>')
out['T3-RunBriefing.dc.html'] = page('תדריך וטיסה במורד המסלול', body)

# =================================================================== T4: trail sign + swipe + share (ת4, ת7, ת8)
runs = []
for k in ['Tatra 2', 'Soliko 1', 'Kudebi 1', 'Goodaura 1']:
    s = ST[k]; runs.append({'k': k, 'ref': (DATA['refs'].get(k) or [''])[0], 'c': C[s['color']], 'heb': HEB[s['color']], 'len': f'{s["len"]/1000:.2f} ק״מ', 'drop': f'{round(SUN["pistes"][k]["drop"])} מ׳', 'steep': f'{s["steep"]}%', 'slug': k.lower().replace(' ', '-')})
body = f'''<div style="{ROOT} display: flex; flex-direction: column">{topbar("מפת מסלולים", "בית")}
<div style="flex-grow: 1; position: relative; overflow: hidden; background: linear-gradient({C["snow"]}, #DCE6EF)">
<svg width="390" height="160" viewBox="0 0 390 160" aria-hidden="true" style="position: absolute; left: 0; bottom: 250px"><path d="M0,160 L0,110 L70,70 L120,95 L190,30 L250,80 L300,55 L390,100 L390,160 Z" fill="#C9D6E3"></path></svg>
<div style="position: absolute; right: 36px; top: 40px; bottom: 250px; width: 10px; background: {C["ink"]}"></div>
<div style="position: absolute; right: 36px; top: 70px; left: 20px; display: flex; flex-direction: column; gap: 14px; align-items: flex-end">
<div style="display: inline-flex; align-items: center; gap: 12px; padding: 12px 22px 12px 44px; background: {{{{ c }}}}; color: #FFFFFF; clip-path: polygon(0 50%, 28px 0, 100% 0, 100% 100%, 28px 100%); box-shadow: 0 6px 14px rgba(19,35,58,0.25)"><span style="padding: 3px 7px; background: #FFFFFF; color: {{{{ c }}}}; {DISP} font-size: 22px; line-height: 1" dir="ltr">{{{{ ref }}}}</span><span style="{DISP} font-size: 50px; line-height: 0.9" dir="ltr">{{{{ k }}}}</span></div>
<span style="margin-left: 30px; padding: 4px 10px; background: #FFFFFF; border: 1.5px solid {C["ink"]}; font-size: 13px; font-weight: 700">{{{{ heb }}}} · לפי המפה הרשמית</span></div>
<div style="position: absolute; left: 0; right: 0; bottom: 0; height: 250px; background: {C["paper"]}; border-top: 1px solid {C["rule"]}; padding: 14px 20px 0; box-sizing: border-box; display: flex; flex-direction: column; gap: 10px">
<div style="display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px"><div style="display: flex; flex-direction: column"><span style="font-size: 12px; color: {C["muted"]}">אורך</span><span style="{DISP} font-size: 30px; line-height: 1">{{{{ len }}}}</span></div><div style="display: flex; flex-direction: column"><span style="font-size: 12px; color: {C["muted"]}">ירידה</span><span style="{DISP} font-size: 30px; line-height: 1">{{{{ drop }}}}</span></div><div style="display: flex; flex-direction: column"><span style="font-size: 12px; color: {C["muted"]}">קטע תלול</span><span style="{DISP} font-size: 30px; line-height: 1">{{{{ steep }}}}</span></div></div>
<div style="display: flex; align-items: center; justify-content: space-between; gap: 8px">
<button type="button" onClick="{{{{ prev }}}}" aria-label="המסלול הקודם" style="width: 48px; height: 48px; border: 1.5px solid {C["ink"]}; background: #FFFFFF; color: {C["ink"]}; font: inherit; font-size: 22px">›</button>
<div style="display: flex; gap: 6px"><sc-for list="{{{{ dots }}}}" as="d" hint-placeholder-count="4"><span style="width: {{{{ d.w }}}}px; height: 8px; background: {{{{ d.bg }}}}"></span></sc-for></div>
<button type="button" onClick="{{{{ next }}}}" aria-label="המסלול הבא" style="width: 48px; height: 48px; border: 1.5px solid {C["ink"]}; background: #FFFFFF; color: {C["ink"]}; font: inherit; font-size: 22px">‹</button></div>
<span style="font-size: 12.5px; color: {C["muted"]}; text-align: center">מחליקים ימינה ושמאלה למסלול הבא, והמצלמה עפה אליו</span>
<button type="button" style="height: 48px; border: 0; background: {C["ink"]}; color: #FFFFFF; font: inherit; font-size: 15px; font-weight: 700; display: flex; align-items: center; justify-content: center; gap: 8px">{SHARE}שיתוף המסלול</button>
<span style="font-size: 12px; color: {C["muted"]}; text-align: center" dir="ltr">gudauri-ski-trip.vercel.app/#map/run/{{{{ slug }}}}</span>
</div></div></div>'''
script = f'''const RUNS = {json.dumps(runs, ensure_ascii=False)};
class Component extends DCLogic {{
  state = {{ i: 0 }};
  renderVals() {{
    const i = this.state.i, r = RUNS[i], n = RUNS.length;
    return {{ ...r, dots: RUNS.map((_, j) => ({{ w: j === i ? 22 : 8, bg: j === i ? r.c : '#CBD5DF' }})),
      next: () => this.setState({{ i: (i + 1) % n }}), prev: () => this.setState({{ i: (i + n - 1) % n }}) }};
  }}
}}'''
out['T4-RunSign.dc.html'] = page('שלט מסלול ודפדוף', body, script)

# =================================================================== S1: the mountain is alive (ר1, ר4)
k3 = 0.35; l3 = (390 - IMG_W * k3) / 2; tp3 = -140 * k3; f3 = tr(k3, l3, tp3)
D = json.load(open(root / 'site/data/runs-and-lifts.json', encoding='utf-8'))
X0, Y0, X1, Y1 = SUN['extent']; SC = 780 / (X1 - X0); KX = 111320 * math.cos(math.radians(42.51))
def ipx(q): return ((q[1] - 44.495) * KX - X0) * SC, (-(q[0] - 42.51) * 111320 - Y0) * SC
CLOSED = {'Sadzele', 'Kudebi'}
lifts_svg = ''
for l in D['lifts']:
    if not l.get('name') or l['name'].startswith('Kobi'): continue
    a = f3(*ipx(l['g'][0])); b = f3(*ipx(l['g'][-1]))
    if l['name'] in CLOSED:
        lifts_svg += f'<line x1="{a[0]:.1f}" y1="{a[1]:.1f}" x2="{b[0]:.1f}" y2="{b[1]:.1f}" stroke="#FFFFFF" stroke-width="5"></line><line x1="{a[0]:.1f}" y1="{a[1]:.1f}" x2="{b[0]:.1f}" y2="{b[1]:.1f}" stroke="#8A97A8" stroke-width="2.4" stroke-dasharray="4 4"></line>'
    else:
        dur = max(4, math.hypot(b[0] - a[0], b[1] - a[1]) / 10)
        lifts_svg += f'<line x1="{a[0]:.1f}" y1="{a[1]:.1f}" x2="{b[0]:.1f}" y2="{b[1]:.1f}" stroke="#FFFFFF" stroke-width="5"></line><line x1="{a[0]:.1f}" y1="{a[1]:.1f}" x2="{b[0]:.1f}" y2="{b[1]:.1f}" stroke="{C["ink"]}" stroke-width="2.2"></line>'
        for j in range(3):
            lifts_svg += f'<circle r="3.2" fill="{C["accent"]}" stroke="{C["ink"]}" stroke-width="1"><animateMotion dur="{dur:.1f}s" begin="-{dur*j/3:.1f}s" repeatCount="indefinite" path="M{a[0]:.1f},{a[1]:.1f} L{b[0]:.1f},{b[1]:.1f}"></animateMotion></circle>'
    lx, ly = (a[0] + b[0]) / 2, (a[1] + b[1]) / 2
    lifts_svg += f'<text x="{lx+5:.1f}" y="{ly:.1f}" font-size="10.5" font-weight="700" fill="{C["ink"]}" stroke="#FFFFFF" stroke-width="3" paint-order="stroke">{l["name"]}</text>'
body = (f'<div style="{ROOT} display: flex; flex-direction: column">{topbar("מצב ההר")}'
        f'<div style="display: flex; align-items: center; gap: 10px; padding: 10px 16px; background: {C["ink"]}; color: #FFFFFF"><span style="{DISP} font-size: 34px; line-height: 1">12/14</span><span style="display: flex; flex-direction: column; font-size: 13px"><b>רכבלים פתוחים</b><span style="color: {C["nmuted"]}">עודכן לפני 6 דק׳ · MTA</span></span><span style="margin-right: auto; padding: 3px 8px; border: 1px solid {C["nmuted"]}; font-size: 11.5px">נתוני דוגמה</span></div>'
        + f'<div class="anim" style="position: relative; height: 560px; flex: none; overflow: hidden; background: #DCE8F1"><img src="{A["s12"]}" alt="מפת ההר עם הרכבלים" style="position: absolute; left: {l3:.1f}px; top: {tp3:.1f}px; width: {IMG_W*k3:.1f}px; height: {IMG_H*k3:.1f}px; max-width: none"><div style="position: absolute; inset: 0; background: rgba(238,242,245,0.35)"></div><svg width="390" height="560" viewBox="0 0 390 560" aria-hidden="true" style="position: absolute; left: 0; top: 0">{lifts_svg}</svg>'
        f'<div style="position: absolute; bottom: 10px; right: 10px; display: flex; flex-direction: column; gap: 4px; padding: 8px 10px; background: rgba(255,255,255,0.95); border: 1px solid {C["rule"]}; font-size: 12px; font-weight: 600"><span style="display: flex; align-items: center; gap: 6px"><span style="width: 8px; height: 8px; border-radius: 4px; background: {C["accent"]}; border: 1px solid {C["ink"]}"></span>פתוח: הכיסאות זזים</span><span style="display: flex; align-items: center; gap: 6px"><span style="width: 18px; border-top: 2.4px dashed #8A97A8"></span>סגור</span></div></div>'
        f'<div style="flex-grow: 1; background: {C["paper"]}; padding: 10px 16px; font-size: 13.5px; display: flex; align-items: center; gap: 8px"><span style="width: 10px; height: 10px; border-radius: 5px; background: {C["red"]}"></span><b dir="ltr">Sadzele, Kudebi</b> סגורים · רוח</div></div>')
out['S1-LiveMountain.dc.html'] = page('ההר חי', body)

# =================================================================== S2: departures board (ר2, ר3, ר5)
rows = [('Goodaura', 'פתוח', '', True), ('Shino', 'פתוח', '', True), ('Soliko', 'פתוח', '', True), ('Kikilo', 'פתוח', 'נפתח', True), ('Kudebi', 'סגור', 'רוח', False), ('Sadzele', 'סגור', 'רוח', False), ('Pirveli', 'פתוח', '', True)]
def flap(text, col, delay, w=22):
    return ''.join(f'<span class="flip" style="display: inline-flex; align-items: center; justify-content: center; width: {w}px; height: 30px; margin-left: 2px; background: #1C2A44; border-top: 1px solid #2E4063; color: {col}; {DISP} font-size: 22px; animation-delay: {delay + i*0.05:.2f}s">{ch}</span>' for i, ch in enumerate(text))
brows = ''
for i, (n, st, why, op) in enumerate(rows):
    col = '#8FE3A8' if op else '#FF8A80'
    brows += f'<div style="display: grid; grid-template-columns: 1fr auto; align-items: center; gap: 8px; padding: 6px 0; border-bottom: 1px solid #22314D"><span dir="ltr" style="text-align: right; display: flex; justify-content: flex-end">{flap(n.upper(), "#EAF0F7", i*0.12, 17)}</span><span style="display: flex; align-items: center; gap: 6px">{flap(st, col, i*0.12+0.3)}<span style="width: 34px; font-size: 11.5px; color: {C["nmuted"]}">{why}</span></span></div>'
css = '@keyframes flip{0%{transform:rotateX(90deg);opacity:.2}60%{transform:rotateX(-12deg)}100%{transform:rotateX(0);opacity:1}} .flip{animation:flip .5s ease-out both;transform-origin:50% 50%}'
body = (f'<div class="anim" style="{ROOT} background: {C["nbg"]}; color: {C["nink"]}; display: flex; flex-direction: column">{topbar("רכבלים", "למפה", True)}'
        f'<div style="margin: 12px 16px 0; padding: 10px 12px; background: #3A2F12; border-inline-start: 5px solid {C["accent"]}; font-size: 13.5px; line-height: 1.4"><b>מאז שבדקת ב-09:10:</b> <span dir="ltr">Kikilo</span> נפתח.</div>'
        f'<div style="margin: 12px 16px 0; padding: 12px; background: #111C30; border: 1px solid #22314D">'
        f'<div style="display: flex; justify-content: space-between; font-size: 11.5px; color: {C["nmuted"]}; padding-bottom: 6px; border-bottom: 1px solid #22314D"><span>רכבל</span><span>מצב</span></div>{brows}</div>'
        f'<div style="margin: 12px 16px 0; display: flex; align-items: center; justify-content: space-between; gap: 10px; min-height: 48px; padding: 0 12px; background: {C["npaper"]}; border: 1px solid {C["nrule"]}"><span style="font-weight: 700; font-size: 14px">רק מה שפתוח בשבילי</span><span role="switch" aria-checked="true" style="width: 44px; height: 26px; border-radius: 13px; background: {C["accent"]}; position: relative"><span style="position: absolute; left: 3px; top: 3px; width: 20px; height: 20px; border-radius: 10px; background: {C["nbg"]}"></span></span></div>'
        f'<p style="margin: 8px 16px 0; font-size: 12.5px; color: {C["nmuted"]}; line-height: 1.4"><span dir="ltr">Sadzele 2</span> מוצג סגור אף שאין עליו הודעה, כי הרכבל שמגיע אליו סגור.</p>'
        f'<p style="margin: auto 16px 14px; font-size: 11.5px; color: {C["nmuted"]}">נתוני דוגמה · MTA · עודכן לפני 6 דק׳</p></div>')
out['S2-DepartureBoard.dc.html'] = page('לוח רכבלים', body, css=css)

# =================================================================== S3: snow-covered signs, no data (ר6)
import random
def smooth(pts):
    """Catmull-Rom through pts -> cubic path commands (after an initial M)."""
    d = ''
    for i in range(len(pts) - 1):
        p0 = pts[max(i - 1, 0)]; p1 = pts[i]; p2 = pts[i + 1]; p3 = pts[min(i + 2, len(pts) - 1)]
        c1 = (p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6); c2 = (p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6)
        d += f' C{c1[0]:.1f},{c1[1]:.1f} {c2[0]:.1f},{c2[1]:.1f} {p2[0]:.1f},{p2[1]:.1f}'
    return d
def snowcap(w, seed, top=26):
    """A soft, lumpy pile of snow sitting on a sign of width w (y=top is the sign's top edge).
    Returns the pile outline, its upper edge, and a few rounded drips hanging over the face."""
    r = random.Random(seed)
    x0, x1 = 16, w + 6
    up = [(x0 - 4, top + 6), (x0 + 4, top - 2)]
    x = x0 + 14; peak = True
    while x < x1 - 20:
        mid = 1 - abs((x - x0) / (x1 - x0) - 0.5) * 1.1
        up.append((x, top - 8 - mid * r.uniform(10, 20)) if peak else (x, top - r.uniform(1, 6)))
        x += r.uniform(30, 48) if peak else r.uniform(22, 34); peak = not peak
    up += [(x1 - 6, top - 3), (x1 + 2, top + 5)]
    lo = []; x = x1 - 2
    while x > x0 + 8:
        lo.append((x, top + r.uniform(8, 13))); x -= r.uniform(26, 44)
    lo.append((x0 + 2, top + 10))
    pts = up + lo
    drips = []
    for _ in range(r.randint(2, 3)):
        dx = r.uniform(x0 + 40, x1 - 40); dl = r.uniform(7, 13); dw = r.uniform(4, 6)
        y0 = top + 8
        drips.append(f'M{dx - dw:.1f},{y0:.1f} C{dx - dw:.1f},{y0 + dl * 0.6:.1f} {dx - dw * 0.5:.1f},{y0 + dl:.1f} {dx:.1f},{y0 + dl:.1f} C{dx + dw * 0.5:.1f},{y0 + dl:.1f} {dx + dw:.1f},{y0 + dl * 0.6:.1f} {dx + dw:.1f},{y0:.1f} Z')
    return f'M{pts[0][0]:.1f},{pts[0][1]:.1f}' + smooth(pts + [pts[0]]) + ' Z', up, drips
SW = 334
def snow_svg(i):
    p, up, drips = snowcap(SW, 11 + i * 7)
    r = random.Random(100 + i)
    dp = ''.join(f'<path d="{q}" fill="url(#sn{i})"></path>' for q in drips)
    sparkle = ''.join(f'<circle cx="{r.uniform(30, SW - 10):.1f}" cy="{r.uniform(14, 26):.1f}" r="{r.uniform(0.8, 1.4):.1f}" fill="#FFFFFF"></circle>' for _ in range(5))
    shade = f'<path d="M{up[0][0]:.1f},{up[0][1] + 6:.1f}' + smooth([(x, y + 7) for x, y in up]) + f'" fill="none" stroke="#C9D8E7" stroke-width="3" stroke-linecap="round" opacity="0.7"></path>'
    return (f'<svg width="{SW + 16}" height="56" viewBox="0 0 {SW + 16} 56" aria-hidden="true" style="position: absolute; left: 0; top: -26px; overflow: visible">'
            f'<defs><linearGradient id="sn{i}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFFFFF"></stop><stop offset="0.6" stop-color="#F3F7FB"></stop><stop offset="1" stop-color="#D3E0EC"></stop></linearGradient></defs>'
            f'<g transform="translate(0,2.5)" fill="#0D1522" fill-opacity="0.25"><path d="{p}"></path>' + ''.join(f'<path d="{q}"></path>' for q in drips) + '</g>'
            f'<path d="{p}" fill="url(#sn{i})"></path>{dp}{sparkle}</svg>')
names = ['Goodaura', 'Shino', 'Soliko', 'Kudebi', 'Sadzele', 'Kikilo']
srow = ''.join(f'<div style="position: relative; height: 58px"><div style="position: absolute; inset: 0; display: flex; align-items: center; justify-content: space-between; padding: 14px 14px 0 30px; background: {C["blue"] if i % 2 == 0 else C["ink"]}; color: rgba(255,255,255,0.6); clip-path: polygon(0 50%, 18px 0, 100% 0, 100% 100%, 18px 100%)"><span style="{DISP} font-size: 28px; line-height: 1" dir="ltr">{n}</span><span style="font-size: 13px">?</span></div>{snow_svg(i)}</div>' for i, n in enumerate(names))
pole_cap = f'<svg width="30" height="20" viewBox="0 0 30 20" aria-hidden="true" style="position: absolute; left: -11px; top: -16px"><path d="M3,18 C2,10 8,4 15,4 C22,4 28,10 27,18 Z" fill="#FFFFFF" stroke="#D5E2EE" stroke-width="1"></path></svg>'
drift = f'<svg width="390" height="70" viewBox="0 0 390 70" aria-hidden="true" style="position: absolute; left: 0; bottom: 0"><path d="M0,40 C60,22 110,30 170,36 C230,42 280,18 330,24 C360,28 380,34 390,32 L390,70 L0,70 Z" fill="#FFFFFF"></path><path d="M0,40 C60,22 110,30 170,36 C230,42 280,18 330,24 C360,28 380,34 390,32" fill="none" stroke="#D5E2EE" stroke-width="2"></path></svg>'
flakes3 = ''.join(f'<span class="flake" style="left: {x}px; animation-duration: {9+i%5}s; animation-delay: -{i*1.3:.1f}s; width: {2+i%3}px; height: {2+i%3}px"></span>' for i, x in enumerate(range(8, 390, 29)))
body = (f'<div class="anim" style="{ROOT} display: flex; flex-direction: column; background: linear-gradient(#E3EBF2, #EEF2F5 60%)">{topbar("מצב ההר")}'
        f'<div style="position: absolute; inset: 60px 0 0; overflow: hidden; pointer-events: none">{flakes3}</div>'
        f'<div style="position: relative; padding: 20px 20px 10px; display: flex; flex-direction: column; gap: 4px"><h1 style="margin: 0; {DISP} font-size: 46px; line-height: 1">ההר עוד ישן</h1><p style="margin: 0; font-size: 15px; line-height: 1.45">עוד אין דיווח על רכבלים. העונה בגודאורי נפתחת בדרך כלל בדצמבר, ואז השלטים יתנקו מהשלג.</p></div>'
        f'<div style="margin: 26px 20px 0 36px; position: relative; display: flex; flex-direction: column; gap: 20px"><div style="position: absolute; right: -16px; top: -10px; bottom: -60px; width: 8px; background: {C["ink"]}">{pole_cap}</div>{srow}</div>'
        f'{drift}<div style="position: relative; margin: auto 20px 22px; padding: 12px 14px; background: {C["paper"]}; border: 1px solid {C["rule"]}; font-size: 13.5px; line-height: 1.45">בינתיים המפה, המסלולים והסרטונים עובדים כרגיל. <b>לא מנחשים מצב:</b> כשאין דיווח עדכני מ-MTA, כתוב כאן שאין.</div></div>')
css3 = '.flake{position:absolute;top:-10px;border-radius:50%;background:#fff;box-shadow:0 0 2px rgba(19,35,58,.25);animation:fall3 linear infinite} @keyframes fall3{to{transform:translate(-24px,800px)}}'
out['S3-NoData.dc.html'] = page('אין עדיין מידע', body, css=css3)

# =================================================================== N1: day and night, three modes (י1, י2, י4, י6, י7)
# The mountains are a real render of the ridge above New Gudauri from the elevation model (design/round3/panorama.py),
# one image per moment of the day, cross-faded as the hour changes. The sky, stars, moon and village lights are HTML on top.
PJ = json.load(open(root / 'design/round3/pano.json', encoding='utf-8'))
PANO = {'night': '/_blob/ae191b2af49c397010a19e712991d87e', 'dawn': '/_blob/892b23604671dd12be91ca509299a43c', 'morning': '/_blob/3b0f41f94d2bca7da08bde36e8a36690',
        'noon': '/_blob/d5ad1e6d2660c28123782ea318a7fd85', 'gold': '/_blob/98b133d6dbe89ed912fd5c489d3704a3', 'sunset': '/_blob/7c3d6f578ae52970e966e31246d900c9', 'dusk': '/_blob/1e5915034100963ed329846fd20130f9'}
SEQ = ['night', 'dawn', 'morning', 'noon', 'gold', 'sunset', 'dusk', 'night', 'night']
SH = 400
panos = ''.join(f'<img src="{PANO[k]}" alt="" style="position: absolute; left: 0; top: 0; width: 390px; height: {SH}px; opacity: {{{{ o{i} }}}}; transition: opacity 0.9s">' for i, k in enumerate(SEQ))
vx, vy = PJ['peaks']['New Gudauri']
wins = [(vx + dx, vy + dy) for dx, dy in [(-44, 6), (-35, 2), (-28, 9), (-19, 4), (-12, 11), (-5, 1), (3, 7), (9, 13), (16, 3), (24, 10), (31, 5), (39, 12), (-23, 15), (0, 16), (20, 17), (46, 8)]]
windows = ''.join(f'<span style="position: absolute; left: {x:.0f}px; top: {y:.0f}px; width: 2px; height: 2px; background: #FFD890; box-shadow: 0 0 4px 1px rgba(255,196,110,0.5)"></span>' for x, y in wins)
labels = ''.join(f'<span style="position: absolute; left: {x-50:.0f}px; top: {y-26:.0f}px; width: 100px; display: flex; flex-direction: column; align-items: center; font-size: 11px; font-weight: 700; color: {{{{ skyInk }}}}; opacity: 0.8"><span dir="ltr">{n}</span><span style="width: 1px; height: 9px; background: currentColor"></span></span>' for n, (x, y) in PJ['peaks'].items() if n in ('Sadzele', 'Bidara'))
stars = ''.join(f'<span class="tw" style="position: absolute; left: {x}px; top: {y}px; width: {1.5+(i%3)*0.8:.1f}px; height: {1.5+(i%3)*0.8:.1f}px; border-radius: 2px; background: #FFFFFF; animation-delay: -{i*0.37:.2f}s"></span>' for i, (x, y) in enumerate([(30,26),(80,58),(132,20),(196,44),(258,24),(312,66),(352,30),(58,104),(170,90),(296,110),(236,78),(118,134),(24,150),(344,142),(210,120),(150,60),(98,18),(276,140),(372,92),(12,84),(186,128),(328,14),(64,140),(226,20)]))
flakes = ''.join(f'<span class="flake" style="left: {x}px; animation-duration: {7+i%4}s; animation-delay: -{i*0.7:.1f}s; width: {2+i%3}px; height: {2+i%3}px"></span>' for i, x in enumerate(range(12, 390, 26)))
clouds = (f'<span class="cloud" style="top: 60px; width: 150px; height: 30px; animation-duration: 80s; animation-delay: -20s"></span>'
          f'<span class="cloud" style="top: 112px; width: 110px; height: 22px; animation-duration: 100s; animation-delay: -70s"></span>')
MOONSVG = '<svg width="40" height="40" viewBox="0 0 34 34" aria-hidden="true"><path d="M17 3 A14 14 0 0 1 17 31 A7.5 14 0 0 0 17 3 Z" fill="#F4F7FB"></path></svg>'
ICON = {
 'sun': '<svg width="26" height="26" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="5" fill="#F4B942"></circle><g stroke="#F4B942" stroke-width="2" stroke-linecap="round"><path d="M12 2v2.5M12 19.5V22M2 12h2.5M19.5 12H22M4.9 4.9l1.8 1.8M17.3 17.3l1.8 1.8M4.9 19.1l1.8-1.8M17.3 6.7l1.8-1.8"></path></g></svg>',
 'moon': '<svg width="26" height="26" viewBox="0 0 34 34" aria-hidden="true"><path d="M17 3 A14 14 0 0 1 17 31 A7.5 14 0 0 0 17 3 Z" fill="currentColor"></path></svg>',
 'auto': '<svg width="26" height="26" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="1.8"></circle><path d="M12 3a9 9 0 0 1 0 18z" fill="currentColor"></path></svg>'}
modes = ''.join(f'<button type="button" aria-pressed="{{{{ m{k}.on }}}}" onClick="{{{{ m{k}.pick }}}}" style="flex: 1; min-height: 52px; border: 0; background: {{{{ m{k}.bg }}}}; color: {{{{ m{k}.fg }}}}; font: inherit; font-size: 14px; font-weight: 700; display: flex; align-items: center; justify-content: center; gap: 8px; transition: background-color 0.4s, color 0.4s">{ICON[k]}{t}</button>' for k, t in [('auto', 'אוטומטי'), ('sun', 'יום'), ('moon', 'לילה')])
body = f'''<div class="anim" style="{ROOT} background: {{{{ bg }}}}; color: {{{{ ink }}}}; transition: background-color 0.9s, color 0.9s; display: flex; flex-direction: column">
<div style="display: flex; align-items: center; justify-content: space-between; padding: 8px 16px 8px 10px; background: {{{{ paper }}}}; border-bottom: 1px solid {{{{ rule }}}}; transition: background-color 0.9s">
<span style="{DISP} font-size: 30px; line-height: 1">גודאורי 2027</span>
<div style="display: flex; align-items: center; gap: 10px"><span style="display: flex; flex-direction: column; align-items: flex-end; font-size: 11px; color: {{{{ muted }}}}">השעה בגודאורי<b style="{DISP} font-size: 22px; line-height: 1; color: {{{{ ink }}}}" dir="ltr">{{{{ clock }}}}</b></span>
<button type="button" onClick="{{{{ cycle }}}}" aria-label="{{{{ modeLbl }}}}" style="position: relative; width: 48px; height: 48px; border-radius: 24px; border: 1.5px solid {{{{ ink }}}}; background: transparent; color: {{{{ ink }}}}; padding: 0; overflow: hidden">
<span style="position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; opacity: {{{{ iSun }}}}; transform: rotate({{{{ rSun }}}}deg); transition: opacity 0.4s, transform 0.6s">{ICON["sun"]}</span>
<span style="position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; opacity: {{{{ iMoon }}}}; transform: rotate({{{{ rMoon }}}}deg); transition: opacity 0.4s, transform 0.6s">{ICON["moon"]}</span>
<span style="position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; opacity: {{{{ iAuto }}}}; transition: opacity 0.4s">{ICON["auto"]}</span></button></div></div>
<div style="position: relative; height: {SH}px; flex: none; overflow: hidden; background: linear-gradient({{{{ skyTop }}}}, {{{{ skyBot }}}} 75%)">
<div style="position: absolute; left: {{{{ glowX }}}}px; top: 190px; width: 560px; height: 420px; margin: -210px 0 0 -280px; background: radial-gradient(closest-side, {{{{ glowC }}}}, transparent); opacity: {{{{ glowOp }}}}"></div>
<div style="position: absolute; inset: 0; opacity: {{{{ starOp }}}}">{stars}</div>
<div style="position: absolute; inset: 0; opacity: {{{{ cloudOp }}}}">{clouds}</div>
<div style="position: absolute; left: 60px; top: {{{{ moonTop }}}}px; opacity: {{{{ moonOp }}}}; filter: drop-shadow(0 0 12px rgba(244,247,251,0.6))">{MOONSVG}</div>
{panos}
<div style="position: absolute; inset: 0; opacity: {{{{ winOp }}}}">{windows}</div>
{labels}
<div style="position: absolute; inset: 0; opacity: {{{{ snowOp }}}}; pointer-events: none">{flakes}</div>
<div style="position: absolute; left: 0; right: 0; bottom: 0; height: 70px; background: linear-gradient(transparent, {{{{ bg }}}})"></div>
<span style="position: absolute; top: 14px; right: 16px; padding: 3px 10px; font-size: 13px; font-weight: 600; color: {{{{ skyInk }}}}; background: {{{{ chipBg }}}}">{{{{ phaseLbl }}}}</span></div>
<div style="padding: 0 20px; margin-top: -28px; display: flex; flex-direction: column; gap: 12px; position: relative">
<div style="display: flex; align-items: flex-end; justify-content: space-between"><h1 style="margin: 0; {DISP} font-size: 44px; line-height: 1">גודאורי, גאורגיה</h1>
<span style="display: inline-flex; align-items: center; gap: 8px; padding: 6px 12px; background: {{{{ paper }}}}; border: 1px solid {{{{ rule }}}}; transition: background-color 0.9s"><span style="width: 12px; height: 12px; border-radius: 6px; background: {{{{ dotC }}}}"></span><span style="{DISP} font-size: 26px; line-height: 1">{{{{ count }}}}</span></span></div>
<div role="group" aria-label="מצב תצוגה" style="display: flex; border: 1.5px solid {{{{ ink }}}}">{modes}</div>
<div style="display: flex; flex-direction: column; gap: 4px; opacity: {{{{ scrubOp }}}}; transition: opacity 0.4s">
<span style="font-size: 12.5px; color: {{{{ muted }}}}">אוטומטי: השמיים הולכים לפי השעה בגודאורי. לתצוגה, גררו לאורך יום שלם:</span>
<input type="range" min="0" max="{{{{ hMax }}}}" value="{{{{ hi }}}}" onInput="{{{{ onHour }}}}" aria-label="שעה לתצוגה" style="width: 100%; accent-color: #F4B942; height: 28px; margin: 0" dir="ltr">
</div>
<p style="margin: 0; font-size: 11.5px; line-height: 1.4; color: {{{{ muted }}}}">ההרים הם הנוף האמיתי מעל New Gudauri, ממודל הגובה של האתר: האור והצל לפי השמש ב-12.1.2027, והגובה מוגדל פי 2.6.</p></div></div>'''
css = ('.flake{position:absolute;top:-10px;border-radius:50%;background:#fff;opacity:.85;animation:fall linear infinite} @keyframes fall{to{transform:translate(-18px,420px)}}'
       ' .tw{animation:tw 3.2s ease-in-out infinite} @keyframes tw{50%{opacity:.35}}'
       ' .cloud{position:absolute;left:-160px;border-radius:40px;background:#fff;filter:blur(8px);opacity:.7;animation:drift linear infinite} @keyframes drift{to{transform:translateX(720px)}}')
script = '''// Moments of a January day in Gudauri (sunrise about 8:25, sunset about 17:45). Each one has its own mountain render.
const NIGHT = { sky: ['#050A15', '#1A2645'], glow: 0, glowC: '#FFB38A', star: 1, moon: 1, win: 1, snow: 1, cloud: 0 };
const K = [
  { h: 0, ...NIGHT },
  { h: 7.6,   sky: ['#2C3B66', '#E8A987'], glow: 0.7, glowC: '#FFB38A', star: 0.2, moon: 0.3, win: 0.8, snow: 0, cloud: 0.3 },
  { h: 9.6,   sky: ['#6FA6DC', '#DCEAF4'], glow: 0.35, glowC: '#FFF2D6', star: 0, moon: 0, win: 0, snow: 0, cloud: 0.8 },
  { h: 12.5,  sky: ['#4F90D2', '#D2E4F3'], glow: 0.2, glowC: '#FFFFFF', star: 0, moon: 0, win: 0, snow: 0, cloud: 0.9 },
  { h: 15.9,  sky: ['#6F9CCB', '#F1DDC2'], glow: 0.6, glowC: '#FFD29A', star: 0, moon: 0, win: 0, snow: 0, cloud: 0.6 },
  { h: 17.35, sky: ['#3A4677', '#F09A6A'], glow: 1, glowC: '#FF9A6A', star: 0.1, moon: 0.2, win: 0.6, snow: 0, cloud: 0.4 },
  { h: 18.2,  sky: ['#1B2448', '#6E5D86'], glow: 0.35, glowC: '#C98AA0', star: 0.6, moon: 0.8, win: 1, snow: 0.3, cloud: 0 },
  { h: 21, ...NIGHT },
  { h: 24, ...NIGHT },
];
const hex = (c) => [1, 3, 5].map((i) => parseInt(c.slice(i, i + 2), 16));
const mix = (a, b, t) => '#' + hex(a).map((v, i) => Math.round(v + (hex(b)[i] - v) * t).toString(16).padStart(2, '0')).join('');
const lerp = (a, b, t) => a + (b - a) * t;
function at(h) {
  let i = 0; while (K[i + 1].h < h) i++;
  const a = K[i], b = K[i + 1], t = (h - a.h) / (b.h - a.h), o = { i, t };
  for (const k of Object.keys(a)) {
    if (k === 'h') continue;
    o[k] = Array.isArray(a[k]) ? [mix(a[k][0], b[k][0], t), mix(a[k][1], b[k][1], t)] : typeof a[k] === 'string' ? mix(a[k], b[k], t) : lerp(a[k], b[k], t);
  }
  return o;
}
const DAYP = { bg: '#EEF2F5', paper: '#FFFFFF', ink: '#13233A', muted: '#4B5A6F', rule: '#CBD5DF' };
const NIGHTP = { bg: '#0D1522', paper: '#16223A', ink: '#EAF0F7', muted: '#A3B3C8', rule: '#2A3B55' };
const STEP = 0.25, HMAX = Math.round(23.75 / STEP);
const FIXED = { sun: 12.5, moon: 21.5 };
const pad = (n) => String(n).padStart(2, '0');
class Component extends DCLogic {
  state = { mode: 'auto', h: 17.25 };
  renderVals() {
    const { mode } = this.state, h = mode === 'auto' ? this.state.h : FIXED[mode], s = at(h);
    const dark = h < 7.9 || h >= 17.9, pal = dark ? NIGHTP : DAYP;
    const phase = h < 7.3 || h >= 18.8 ? 'לילה · שלג קל' : h < 8.6 ? 'לפני הזריחה' : h < 15.3 ? 'יום בהיר' : h < 17 ? 'שעת זהב' : h < 17.9 ? 'אלפנגלו: הפסגות נצבעות ורוד' : 'דמדומים';
    const op = {};
    for (let j = 0; j < K.length; j++) op['o' + j] = j === s.i ? 1 : j === s.i + 1 ? s.t : 0;
    const pick = (k) => ({ on: mode === k, bg: mode === k ? pal.ink : 'transparent', fg: mode === k ? pal.paper : pal.ink, pick: () => this.setState({ mode: k }) });
    const order = ['auto', 'sun', 'moon'];
    return { ...pal, ...op, skyTop: s.sky[0], skyBot: s.sky[1], glowC: s.glowC, glowOp: s.glow, glowX: h < 12 ? 380 : 10,
      starOp: s.star, moonOp: s.moon, moonTop: lerp(110, 30, s.moon), winOp: s.win, snowOp: s.snow, cloudOp: s.cloud,
      skyInk: dark ? '#EAF0F7' : '#13233A', chipBg: dark ? 'rgba(13,21,34,0.45)' : 'rgba(255,255,255,0.55)', phaseLbl: phase,
      clock: pad(Math.floor(h)) + ':' + pad(Math.round((h % 1) * 60)),
      count: dark ? 'עוד 102 לילות' : 'עוד 102 ימים', dotC: dark ? '#EAF0F7' : '#F4B942',
      iSun: mode === 'sun' ? 1 : 0, iMoon: mode === 'moon' ? 1 : 0, iAuto: mode === 'auto' ? 1 : 0, rSun: mode === 'sun' ? 0 : -90, rMoon: mode === 'moon' ? 0 : 90,
      modeLbl: 'מצב תצוגה: ' + ({ auto: 'אוטומטי', sun: 'יום', moon: 'לילה' })[mode],
      cycle: () => this.setState({ mode: order[(order.indexOf(mode) + 1) % 3] }),
      mauto: pick('auto'), msun: pick('sun'), mmoon: pick('moon'),
      scrubOp: mode === 'auto' ? 1 : 0.35, hMax: HMAX, hi: Math.round(this.state.h / STEP),
      onHour: (e) => this.setState({ mode: 'auto', h: parseInt(e.target.value, 10) * STEP }) };
  }
}'''
out['N1-SunsetTheatre.dc.html'] = page('יום ולילה בדף הבית', body, script, css)

# =================================================================== N2: mountain light by the hour (י3)
k4 = 0.5; tp4 = -520 * k4
body = (f'<div style="{ROOT} display: flex; flex-direction: column">{topbar("האור על ההר")}'
        f'<div style="position: relative; height: 520px; flex: none; overflow: hidden; background: #DCE8F1"><img src="{{{{ src }}}}" alt="ההר לפי השעה, עם שמש וצל" style="position: absolute; left: 0; top: {tp4:.0f}px; width: 390px; height: {IMG_H*k4:.0f}px; max-width: none"><div class="anim" style="position: absolute; inset: 0; background: {{{{ tint }}}}; mix-blend-mode: multiply; transition: background-color 1s"></div>'
        f'<span style="position: absolute; top: 10px; right: 10px; padding: 4px 10px; background: rgba(255,255,255,0.94); border: 1px solid {C["rule"]}; font-size: 12.5px; font-weight: 600">{{{{ note }}}}</span></div>'
        f'<div style="flex-grow: 1; background: {C["paper"]}; padding: 14px 20px 0; display: flex; flex-direction: column; gap: 10px"><h2 style="margin: 0; {DISP} font-size: 30px; line-height: 1">{{{{ head }}}}</h2>'
        f'<div role="group" aria-label="שעה" style="display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px"><sc-for list="{{{{ hours }}}}" as="h" hint-placeholder-count="3"><button type="button" aria-pressed="{{{{ h.on }}}}" onClick="{{{{ h.pick }}}}" style="height: 44px; border: 1.5px solid {C["ink"]}; background: {{{{ h.bg }}}}; color: {{{{ h.fg }}}}; font: inherit; font-size: 15px; font-weight: 700"><span dir="ltr">{{{{ h.lbl }}}}</span></button></sc-for></div>'
        f'<p style="margin: 0; font-size: 12.5px; color: {C["muted"]}; line-height: 1.4">הצל והאור מחושבים ממודל הגובה ל-12.1.2027, כולל צל של הרים אחרים. בתלת-ממד זה יהיה צבע האור על ההר. כבד יותר לטלפון.</p></div></div>')
script = f'''const H = {{ '0900': ['{A["s09"]}', 'rgba(170,190,230,0.25)', 'בוקר קר: הכפר עוד בצל', 'אור כחלחל'],
  '1200': ['{A["s12"]}', 'rgba(255,255,255,0)', 'צהריים: כל ההר בשמש', 'אור לבן'],
  '1630': ['{A["s1630"]}', 'rgba(250,190,140,0.3)', 'אחר הצהריים: אור חם ונמוך', 'אור כתום'] }};
const L = {{ '0900': '09:00', '1200': '12:00', '1630': '16:30' }};
class Component extends DCLogic {{
  state = {{ t: '1630' }};
  renderVals() {{
    const t = this.state.t, h = H[t];
    return {{ src: h[0], tint: h[1], head: h[2], note: h[3], hours: Object.keys(L).map((k) => ({{ lbl: L[k], on: k === t, bg: k === t ? '#13233A' : '#FFFFFF', fg: k === t ? '#FFFFFF' : '#13233A', pick: () => this.setState({{ t: k }}) }})) }};
  }}
}}'''
out['N2-MountainLight.dc.html'] = page('האור על ההר', body, script)

# =================================================================== M1: pick on the mountain + fixed spots (מ1, מ6)
# lift station heights and the far skyline from the village, both from the elevation model
_d = T['dem']; _H = np.frombuffer(base64.b64decode(_d['b64']), dtype='<i2').reshape(_d['ny'], _d['nx']).astype(float)
_CX = (_d['x1'] - _d['x0']) / (_d['nx'] - 1); _CY = (_d['y1'] - _d['y0']) / (_d['ny'] - 1)
def elev(x, y):
    c = min(max((x - _d['x0']) / _CX, 0), _d['nx'] - 1.001); r = min(max((y - _d['y0']) / _CY, 0), _d['ny'] - 1.001); c0, r0 = int(c), int(r); fc, fr = c - c0, r - r0
    return _H[r0, c0]*(1-fc)*(1-fr)+_H[r0, c0+1]*fc*(1-fr)+_H[r0+1, c0]*(1-fc)*fr+_H[r0+1, c0+1]*fc*fr
def _xy(q): return (q[1] - 44.495) * KX, -(q[0] - 42.51) * 111320
EX = {'stations': {l['name']: {'bottom': round(elev(*_xy(l['g'][0])))} for l in D['lifts'] if l.get('name')}, 'ridge': {'far': []}}
_E0 = elev(-452.0, 4441.0) + 2
for _i in range(160):
    _a = math.radians(-60 + _i * 120 / 159)
    EX['ridge']['far'].append(max(math.degrees(math.atan2(elev(-452 + math.sin(_a) * _t, 4441 - math.cos(_a) * _t) - _E0, _t)) for _t in range(150, 9000, 50)))
k5 = 0.8; l5 = 195 - 250 * k5; tp5 = 175 - 1250 * k5; f5 = tr(k5, l5, tp5)
MAPH = 350
pins = []
for n in ['Goodaura', 'Zuma', 'Soliko', 'Shino', 'Pirveli']:
    l = next(x for x in D['lifts'] if x.get('name') == n); x, y = f5(*ipx(l['g'][0]))
    pins.append({'k': n, 'x': round(x), 'y': round(y), 'alt': f"{EX['stations'][n]['bottom']:,} מ׳", 'desc': 'התחנה התחתונה של הגונדולה' if n == 'Goodaura' else 'התחנה התחתונה'})
PIN = lambda fill, stroke='#13233A', w=44, h=56: f'<svg width="{w}" height="{h}" viewBox="0 0 44 56" aria-hidden="true"><path d="M22 54 C22 54 6 34 6 21 A16 16 0 0 1 38 21 C38 34 22 54 22 54 Z" fill="{fill}" stroke="{stroke}" stroke-width="2.2"></path><circle cx="22" cy="21" r="6.5" fill="#FFFFFF" stroke="{stroke}" stroke-width="1.5"></circle></svg>'
pin_btns = (f'<sc-for list="{{{{ pins }}}}" as="p" hint-placeholder-count="5"><button type="button" onClick="{{{{ p.pick }}}}" aria-label="{{{{ p.k }}}}" aria-pressed="{{{{ p.on }}}}" style="position: absolute; left: {{{{ p.bx }}}}px; top: {{{{ p.by }}}}px; width: 44px; height: 56px; border: 0; background: transparent; padding: 0; transform-origin: 22px 54px; transform: scale({{{{ p.sc }}}}); transition: transform 0.35s cubic-bezier(.3,1.6,.5,1); z-index: {{{{ p.z }}}}">'
            f'<span class="pulse" style="position: absolute; left: 6px; top: 44px; width: 32px; height: 14px; border-radius: 50%; border: 2px solid {C["accent"]}; opacity: {{{{ p.ring }}}}"></span>'
            f'<svg width="44" height="56" viewBox="0 0 44 56" aria-hidden="true" style="position: relative"><path d="M22 54 C22 54 6 34 6 21 A16 16 0 0 1 38 21 C38 34 22 54 22 54 Z" fill="{{{{ p.fill }}}}" stroke="{C["ink"]}" stroke-width="2.2"></path><circle cx="22" cy="21" r="6.5" fill="#FFFFFF" stroke="{C["ink"]}" stroke-width="1.5"></circle></svg></button></sc-for>')
callout = (f'<div style="position: absolute; left: {{{{ cx }}}}px; top: {{{{ cy }}}}px; width: 190px; transition: left 0.35s cubic-bezier(.3,0,.2,1), top 0.35s cubic-bezier(.3,0,.2,1); filter: drop-shadow(0 6px 14px rgba(19,35,58,0.3)); z-index: 5">'
           f'<div style="background: {C["ink"]}; color: #FFFFFF; padding: 8px 12px 9px; display: flex; flex-direction: column; gap: 2px"><span style="display: flex; align-items: baseline; justify-content: space-between; gap: 8px"><b style="{DISP} font-size: 30px; line-height: 0.9" dir="ltr">{{{{ place }}}}</b><span style="font-size: 12px; color: {C["accent"]}; font-weight: 700; white-space: nowrap" dir="ltr">{{{{ alt }}}}</span></span><span style="font-size: 12px; color: {C["nmuted"]}">{{{{ desc }}}}</span></div>'
           f'<span style="position: absolute; left: {{{{ tipX }}}}px; bottom: -8px; width: 16px; height: 16px; background: {C["ink"]}; transform: rotate(45deg)"></span></div>')
times = ['09:30', '11:00', '12:30', '13:30', '15:00', '16:30']
time_row = (f'<div role="group" aria-label="שעה" style="display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 6px" dir="ltr"><sc-for list="{{{{ times }}}}" as="t" hint-placeholder-count="6">'
            f'<button type="button" aria-pressed="{{{{ t.on }}}}" onClick="{{{{ t.pick }}}}" style="height: 52px; border: 1.5px solid {C["ink"]}; background: {{{{ t.bg }}}}; color: {C["ink"]}; {DISP} font-size: 24px; line-height: 1; padding: 0; transform: translateY({{{{ t.lift }}}}px); box-shadow: {{{{ t.sh }}}}; transition: transform 0.25s, background-color 0.25s, box-shadow 0.25s">{{{{ t.lbl }}}}</button></sc-for></div>')
ICONS = {
 'am': '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M3 18h18M7 18a5 5 0 0 1 10 0M12 5v3M4.9 9.9l2 2M19.1 9.9l-2 2"></path></svg>',
 'noon': '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M4 11h16a8 8 0 0 1-16 0zM8 7c0-1.5 1-2 1-3.5M12 7c0-1.5 1-2 1-3.5M16 7c0-1.5 1-2 1-3.5"></path></svg>',
 'pm': '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 19l6-9 4 5 3-4 5 8z"></path><path d="M9 10l1.5 2.2"></path></svg>'}
signs = ''.join(f'<button type="button" aria-pressed="{{{{ q{k}.on }}}}" onClick="{{{{ q{k}.pick }}}}" style="position: relative; width: {w}px; height: 46px; border: 0; padding: 0 16px 0 30px; background: {col}; color: {fg}; clip-path: polygon(0 50%, 18px 0, 100% 0, 100% 100%, 18px 100%); display: flex; align-items: center; justify-content: space-between; gap: 10px; font: inherit; transform: translateX({{{{ q{k}.dx }}}}px); transition: transform 0.3s cubic-bezier(.3,1.5,.5,1)">'
                f'<span style="display: flex; align-items: center; gap: 8px; white-space: nowrap">{ICONS[k]}<b style="font-size: 14px">{lbl}</b></span><span style="{DISP} font-size: 24px; line-height: 1; white-space: nowrap" dir="ltr">{sub}</span></button>'
                for k, lbl, sub, col, fg, w in [('am', 'רכבל הבוקר', 'Goodaura 09:30', C['accent'], C['ink'], 318), ('noon', 'צהריים', 'Zuma 13:30', C['blue'], '#FFFFFF', 296), ('pm', 'סוף יום', 'Soliko 16:30', C['ink'], '#FFFFFF', 274)])
post = f'<div style="position: relative; display: flex; flex-direction: column; align-items: flex-end; gap: 8px; padding-right: 14px"><span style="position: absolute; right: 0; top: -8px; bottom: -14px; width: 7px; background: {C["ink"]}"></span>{signs}</div>'
TICKET = '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round" aria-hidden="true"><path d="M3 7h18v3a2 2 0 0 0 0 4v3H3v-3a2 2 0 0 0 0-4z"></path><path d="M15 7v10" stroke-dasharray="2 2"></path></svg>'
body = (f'<div class="anim" style="{ROOT} display: flex; flex-direction: column">{topbar("נקודת מפגש", "בית")}'
        + map_img(A['s12'], k5, l5, tp5, MAPH, 'הכפר והתחנות התחתונות',
                  f'<div style="position: absolute; inset: 0; background: linear-gradient(rgba(238,242,245,0) 70%, rgba(238,242,245,0.9))"></div>{pin_btns}{callout}'
                  f'<span style="position: absolute; bottom: 10px; left: 12px; padding: 4px 10px; background: rgba(255,255,255,0.94); border: 1px solid {C["rule"]}; font-size: 12px; font-weight: 600">לחצו על סיכה, או בכל מקום במפה</span>')
        + f'<div style="flex-grow: 1; padding: 12px 16px 0; display: flex; flex-direction: column; gap: 10px">'
        f'<span style="display: flex; justify-content: space-between; font-size: 12.5px; font-weight: 700; color: {C["muted"]}"><span>מתי</span><span>יום ה׳, 14.1</span></span>{time_row}'
        f'<span style="margin-top: 4px; font-size: 12.5px; font-weight: 700; color: {C["muted"]}">או בלחיצה אחת, הנקודות של הקבוצה</span>{post}'
        f'<a href="M2-MeetCard.dc.html" style="margin: auto 0 16px; height: 56px; background: {C["ink"]}; color: #FFFFFF; text-decoration: none; font-size: 16px; font-weight: 700; display: flex; align-items: center; justify-content: center; gap: 10px">{TICKET}יצירת כרטיס מפגש<span style="{DISP} font-size: 24px; line-height: 1; color: {C["accent"]}; margin-right: 4px; white-space: nowrap" dir="ltr">{{{{ place }}}} {{{{ time }}}}</span></a></div></div>')
css_m1 = '.pulse{animation:pulse 1.6s ease-out infinite} @keyframes pulse{0%{transform:scale(.6)}100%{transform:scale(1.8);opacity:0}}'
script = f'''const PINS = {json.dumps(pins, ensure_ascii=False)};
const TIMES = {json.dumps(times)};
const PRESETS = {{ am: ['Goodaura', '09:30'], noon: ['Zuma', '13:30'], pm: ['Soliko', '16:30'] }};
class Component extends DCLogic {{
  state = {{ place: 'Goodaura', time: '12:30', preset: '' }};
  renderVals() {{
    const s = this.state, sel = PINS.find((p) => p.k === s.place);
    const pins = PINS.map((p) => {{ const on = p.k === s.place; return {{ ...p, on, bx: p.x - 22, by: p.y - 54, sc: on ? 1.25 : 0.9, z: on ? 3 : 1, ring: on ? 1 : 0, fill: on ? '#F4B942' : '#FFFFFF', pick: () => this.setState({{ place: p.k, preset: '' }}) }}; }});
    const cxv = Math.max(8, Math.min(390 - 198, sel.x - 95)), cyv = Math.max(6, sel.y - 140);
    const times = TIMES.map((t) => {{ const on = t === s.time; return {{ lbl: t, on, bg: on ? '#F4B942' : '#FFFFFF', lift: on ? -3 : 0, sh: on ? '0 4px 0 #13233A' : 'none', pick: () => this.setState({{ time: t, preset: '' }}) }}; }});
    const q = (k) => ({{ on: s.preset === k, dx: s.preset === k ? -10 : 0, pick: () => this.setState({{ place: PRESETS[k][0], time: PRESETS[k][1], preset: k }}) }});
    return {{ pins, times, place: s.place, time: s.time, alt: sel.alt, desc: sel.desc, cx: cxv, cy: cyv, tipX: Math.max(10, Math.min(166, sel.x - cxv - 8)),
      qam: q('am'), qnoon: q('noon'), qpm: q('pm') }};
  }}
}}'''
out['M1-MeetPick.dc.html'] = page('בחירת נקודת מפגש', body, script, css_m1)

# =================================================================== M2: meeting boarding pass + how to get there (מ2, מ5)
PC = {p['name']: p['color'] for p in D['pistes']}
LIFT = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M2 5l20-3"></path><path d="M12 3.5V9"></path><rect x="6" y="9" width="12" height="10" rx="2"></rect><path d="M6 14h12"></path></svg>'
WALK = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="13" cy="4" r="2"></circle><path d="M9 21l2-6 3 3v3M8 11l3-3 3 2 3 1M11 8l-1 5"></path></svg>'
def chip(kind, name):
    if kind == 'walk': return f'<span style="display: inline-flex; align-items: center; gap: 5px; height: 30px; padding: 0 10px; border: 1.5px dashed {C["muted"]}; color: {C["muted"]}; font-size: 13px; font-weight: 700">{WALK}הליכה</span>'
    if kind == 'lift': return f'<span style="display: inline-flex; align-items: center; gap: 5px; height: 30px; padding: 0 10px; background: {C["ink"]}; color: #FFFFFF; font-size: 13px; font-weight: 700" dir="ltr">{LIFT}{name}</span>'
    return f'<span style="display: inline-flex; align-items: center; height: 30px; padding: 0 10px 0 20px; background: {C[PC[name]]}; color: #FFFFFF; {DISP} font-size: 21px; line-height: 1; clip-path: polygon(0 50%, 12px 0, 100% 0, 100% 100%, 12px 100%)" dir="ltr">{name}</span>'
routes = [('מ-Kudebi', [('run', 'Kudebi 1'), ('run', 'Goodaura 1')], 'כ-14 דק׳'), ('מ-Tatra', [('run', 'Tatra 1'), ('lift', 'Shino'), ('run', 'Shino')], 'כ-12 דק׳'), ('מהכפר', [('walk', '')], 'כ-5 דק׳')]
DOT = f'<span style="width: 10px; border-top: 2px dotted {C["muted"]}"></span>'
rrows = ''.join(f'<div style="display: flex; flex-direction: column; gap: 6px; padding: 10px 0; border-bottom: 1px solid {C["rule"]}"><span style="display: flex; justify-content: space-between; font-size: 13px"><b dir="auto">{a}</b><span style="font-weight: 700; color: {C["muted"]}">{t}</span></span><span style="display: flex; align-items: center; flex-wrap: wrap; gap: 4px" dir="ltr">' + DOT.join(chip(*c) for c in b) + f'{DOT}<span style="width: 14px; height: 14px; border-radius: 7px; background: {C["accent"]}; border: 2px solid {C["ink"]}"></span></span></div>' for a, b, t in routes)
gp = next(p for p in pins if p['k'] == 'Goodaura')
k7 = 1.1; ms_w, ms_h = 118, 150
gix, giy = (gp['x'] - l5) / k5, (gp['y'] - tp5) / k5
l7, tp7 = ms_w / 2 - gix * k7, ms_h * 0.62 - giy * k7
notch = 'radial-gradient(circle at 0 {y}px, transparent 11px, #000 11.5px) left / 51% 100% no-repeat, radial-gradient(circle at 100% {y}px, transparent 11px, #000 11.5px) right / 51% 100% no-repeat'
ridge_near = ' '.join(f'{i*390/(len(EX["ridge"]["far"])-1):.1f},{130 - a*7.5:.1f}' for i, a in enumerate(EX['ridge']['far']))
body = f'''<div style="{ROOT} display: flex; flex-direction: column">{topbar("כרטיס מפגש", "חזרה")}
<div style="position: relative; flex: none; padding: 30px 22px 0">
<svg width="390" height="140" viewBox="0 0 390 140" aria-hidden="true" style="position: absolute; left: 0; top: 0"><polygon points="0,140 {ridge_near} 390,140" fill="#DCE6EF"></polygon></svg>
<div style="position: relative; transform: rotate(-2deg); filter: drop-shadow(0 12px 22px rgba(19,35,58,0.22))">
<div style="background: {C["paper"]}; -webkit-mask: {notch.format(y=176)}; mask: {notch.format(y=176)}; display: flex; flex-direction: column">
<div style="display: flex; align-items: center; justify-content: space-between; padding: 10px 16px; background: {C["ink"]}; color: #FFFFFF"><span style="{DISP} font-size: 24px; line-height: 1">כרטיס מפגש</span><span style="font-size: 12px; color: {C["nmuted"]}">החבר׳ה · גודאורי 2027</span></div>
<div style="padding: 12px 16px 14px; display: flex; flex-direction: column; gap: 10px; height: 128px; box-sizing: border-box">
<div style="display: flex; align-items: flex-end; justify-content: space-between"><span style="display: flex; flex-direction: column"><small style="font-size: 12px; color: {C["muted"]}">נפגשים ב</small>{sign("Goodaura", C["blue"], 40, "5px 14px 5px 28px")}</span><span style="display: flex; flex-direction: column; align-items: flex-end"><small style="font-size: 12px; color: {C["muted"]}">שעה</small><b style="{DISP} font-size: 48px; line-height: 0.85">12:30</b></span></div>
<div style="display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; border-top: 1px solid {C["rule"]}; padding-top: 8px">
<span style="display: flex; flex-direction: column"><small style="font-size: 11.5px; color: {C["muted"]}">שער</small><b style="font-size: 13.5px">תחתית הגונדולה</b></span>
<span style="display: flex; flex-direction: column"><small style="font-size: 11.5px; color: {C["muted"]}">גובה</small><b style="font-size: 13.5px" dir="ltr">{EX["stations"]["Goodaura"]["bottom"]:,} מ׳</b></span>
<span style="display: flex; flex-direction: column"><small style="font-size: 11.5px; color: {C["muted"]}">יום</small><b style="font-size: 13.5px">ה׳, 14.1</b></span></div></div>
<div style="margin: 0 14px; border-top: 2px dashed {C["rule"]}"></div>
<div style="display: flex; height: 150px">
<div style="flex: 1; background: {C["accent"]}; display: flex; flex-direction: column; justify-content: center; align-items: center; gap: 2px; margin: 10px 0 10px 10px"><span style="font-size: 12.5px; font-weight: 700">עוד</span><b style="{DISP} font-size: 76px; line-height: 0.8">38</b><span style="font-size: 13px; font-weight: 700">דקות לפגישה</span></div>
<div style="position: relative; width: {ms_w}px; height: {ms_h - 20}px; margin: 10px; overflow: hidden; background: #DCE8F1; flex: none"><img src="{A["s12"]}" alt="" style="position: absolute; left: {l7:.1f}px; top: {tp7 - 10:.1f}px; width: {IMG_W*k7:.1f}px; height: {IMG_H*k7:.1f}px; max-width: none"><span style="position: absolute; left: {ms_w/2-15:.0f}px; top: {ms_h*0.62-10-41:.0f}px">{PIN(C["accent"], w=30, h=40)}</span></div></div></div></div></div>
<div style="padding: 22px 20px 0; display: flex; flex-direction: column"><div style="display: flex; align-items: baseline; justify-content: space-between"><h2 style="margin: 0; {DISP} font-size: 28px; line-height: 1">איך מגיעים</h2><span style="font-size: 12px; color: {C["muted"]}">לפי החיבורים בנתונים</span></div>{rrows}
<p style="margin: 6px 0 0; font-size: 11.5px; color: {C["muted"]}">זמנים משוערים, בלי תורים לרכבלים.</p></div>
<div style="margin-top: auto; padding: 12px 16px 18px"><a href="M3-MeetShare.dc.html" style="height: 56px; background: {C["ink"]}; color: #FFFFFF; text-decoration: none; font-size: 17px; font-weight: 700; display: flex; align-items: center; justify-content: center; gap: 10px">{SHARE}שליחה לקבוצה</a></div></div>'''
out['M2-MeetCard.dc.html'] = page('כרטיס מפגש', body)

# =================================================================== M3: share preview (מ3, מ4)
k6 = k5 * 0.75; iw, ih = 262, 150
l6, tp6 = iw * 0.62 - gix * k6, ih * 0.58 - giy * k6
TAILR = lambda col: f'<svg width="10" height="14" viewBox="0 0 10 14" aria-hidden="true" style="position: absolute; top: 0; right: -9px"><path d="M0 0 H10 L0 12 Z" fill="{col}"></path></svg>'
TAILL = lambda col: f'<svg width="10" height="14" viewBox="0 0 10 14" aria-hidden="true" style="position: absolute; top: 0; left: -9px"><path d="M10 0 H0 L10 12 Z" fill="{col}"></path></svg>'
bubble_in = lambda who, txt, tm, col: f'<div style="position: relative; align-self: flex-start; max-width: 250px; padding: 6px 10px 5px; background: #FFFFFF; box-shadow: 0 1px 1px rgba(0,0,0,0.12); display: flex; flex-direction: column">{TAILR("#FFFFFF")}<b style="font-size: 12.5px; color: {col}">{who}</b><span style="font-size: 14px">{txt}</span><span style="align-self: flex-start; font-size: 10.5px; color: {C["muted"]}">{tm}</span></div>'
share_img = (f'<div style="position: relative; height: {ih}px; overflow: hidden; background: #DCE8F1"><img src="{A["s12"]}" alt="" style="position: absolute; left: {l6:.1f}px; top: {tp6:.1f}px; width: {IMG_W*k6:.1f}px; height: {IMG_H*k6:.1f}px; max-width: none">'
             f'<div style="position: absolute; inset: 0; background: linear-gradient(rgba(19,35,58,0) 45%, rgba(19,35,58,0.75))"></div>'
             f'<span style="position: absolute; left: {iw*0.62-17:.0f}px; top: {ih*0.58-44:.0f}px">{PIN(C["accent"], w=34, h=44)}</span>'
             f'<span style="position: absolute; left: {iw*0.62-24:.0f}px; top: {ih*0.58-4:.0f}px; width: 48px; height: 14px; border-radius: 50%; border: 2px solid {C["accent"]}; opacity: 0.8"></span>'
             f'<div style="position: absolute; right: 8px; bottom: 8px; left: 8px; display: flex; align-items: flex-end; justify-content: space-between">{sign("Goodaura", C["blue"], 26, "4px 10px 4px 22px")}<span style="padding: 2px 8px; background: {C["accent"]}; color: {C["ink"]}; {DISP} font-size: 34px; line-height: 1">12:30</span></div>'
             f'<span style="position: absolute; top: 8px; right: 8px; padding: 2px 8px; background: {C["ink"]}; color: #FFFFFF; font-size: 11px; font-weight: 700">כרטיס מפגש · ה׳ 14.1</span></div>')
body = f'''<div style="{ROOT} background: #E9E3DA; display: flex; flex-direction: column">
<div style="display: flex; align-items: center; gap: 10px; padding: 10px 14px; background: {C["ink"]}; color: #FFFFFF"><span style="width: 38px; height: 38px; background: {C["blue"]}; clip-path: polygon(0 50%, 10px 0, 100% 0, 100% 100%, 10px 100%); display: flex; align-items: center; justify-content: center; {DISP} font-size: 22px; padding-left: 6px; box-sizing: border-box">ג</span><span style="display: flex; flex-direction: column"><b style="font-size: 15px">החבר׳ה · גודאורי</b><span style="font-size: 12px; color: {C["nmuted"]}">6 משתתפים</span></span></div>
<div style="padding: 14px 16px; display: flex; flex-direction: column; gap: 10px; flex-grow: 1; background-image: radial-gradient(rgba(19,35,58,0.05) 1px, transparent 1px); background-size: 14px 14px">
<span style="align-self: center; padding: 3px 10px; background: rgba(255,255,255,0.85); font-size: 11.5px; font-weight: 600; color: {C["muted"]}">היום</span>
{bubble_in("דובי", "איפה נפגשים לצהריים?", "12:01", C["red"])}
<div style="position: relative; align-self: flex-end; width: {iw + 8}px; background: #D9F2C4; padding: 4px; box-shadow: 0 1px 1px rgba(0,0,0,0.12); display: flex; flex-direction: column">{TAILL("#D9F2C4")}{share_img}
<div style="padding: 8px 6px 2px; display: flex; flex-direction: column; gap: 3px"><b style="font-size: 14.5px">נפגשים ב-<span dir="ltr">Goodaura</span> ב-12:30</b><span style="font-size: 13px; line-height: 1.4">תחתית הגונדולה, {EX["stations"]["Goodaura"]["bottom"]:,} מ׳. פותחים את הקישור ורואים את הנקודה על המפה:</span><span style="font-size: 12.5px; color: {C["blue"]}; text-decoration: underline" dir="ltr">gudauri-ski-trip.vercel.app/#meet/goodaura-1230</span><span style="align-self: flex-start; font-size: 10.5px; color: {C["muted"]}">12:02 ✓✓</span></div></div>
{bubble_in("שרוליק", "מגיע מ-<span dir='ltr'>Kudebi</span>, רבע שעה", "12:04", C["blue"])}
{bubble_in("יהודה", "יוצא עכשיו מ-<span dir='ltr'>Tatra</span>", "12:05", C["green"])}</div>
<p style="margin: 0 16px 16px; padding: 10px 12px; background: rgba(255,255,255,0.9); font-size: 12.5px; line-height: 1.4">ההודעה נשלחת עם תמונה של ההר והסיכה, והקישור פותח את האתר ישר על הנקודה. בלי מסד נתונים: הכל נמצא בקישור עצמו.</p></div>'''
out['M3-MeetShare.dc.html'] = page('שיתוף המפגש', body)

dst = root / 'design/canvas/project'
for n, s in out.items():
    (dst / n).write_text(s, encoding='utf-8')
print({n: len(s) for n, s in out.items()})
