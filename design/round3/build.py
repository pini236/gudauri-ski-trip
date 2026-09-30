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

# =================================================================== T1: cinematic entry (ת1)
k1 = 0.45; l1 = 195 - 213 * k1; tp1 = 118 - 905 * k1; f1 = tr(k1, l1, tp1)
def frame(n, cap, inner):
    return f'<div style="display: flex; flex-direction: column"><div style="position: relative; height: 230px; overflow: hidden; background: #DCE8F1; border-bottom: 1px solid {C["rule"]}">{inner}<span style="position: absolute; top: 8px; right: 8px; width: 28px; height: 28px; border-radius: 14px; background: {C["ink"]}; color: #FFFFFF; {DISP} font-size: 20px; display: flex; align-items: center; justify-content: center">{n}</span></div><span style="padding: 5px 16px 7px; font-size: 13.5px; font-weight: 600; background: {C["paper"]}">{cap}</span></div>'
img1 = f'<img src="{A["s12"]}" alt="" style="position: absolute; left: {l1:.1f}px; top: {tp1:.1f}px; width: {IMG_W*k1:.1f}px; height: {IMG_H*k1:.1f}px; max-width: none">'
tap = f'<span style="position: absolute; left: {f1(T2[15]["px"], T2[15]["py"])[0]-22:.1f}px; top: {f1(T2[15]["px"], T2[15]["py"])[1]-22:.1f}px; width: 44px; height: 44px; border-radius: 22px; border: 3px solid {C["ink"]}; background: rgba(244,185,66,0.35)"></span>'
half = T2[: len(T2) * 3 // 5]
fr1 = frame(1, 'לוחצים על Tatra 2 במפה', img1 + tap)
fr2 = frame(2, 'ההר מתעמעם, והמסלול נצבע מלמעלה למטה לפי השיפוע', img1 + '<div style="position: absolute; inset: 0; background: rgba(19,35,58,0.45)"></div>' + line_svg(f1, 230, half, True, 5))
fr3 = frame(3, 'המצלמה נוחתת נמוך מעל המסלול', f'<img src="{A["f1"]}" alt="" style="position: absolute; left: 0; top: -60px; width: 390px; height: 440px; object-fit: cover">')
out['T1-RunEntry.dc.html'] = page('כניסה קולנועית למסלול', f'<div style="{ROOT} display: flex; flex-direction: column"><div style="padding: 10px 16px 8px"><span style="{DISP} font-size: 30px; line-height: 1">כניסה למסלול</span><span style="display: block; font-size: 12.5px; color: {C["muted"]}">שלושה רגעים, כשנייה וחצי בסך הכל</span></div>{fr1}{fr2}{fr3}</div>')

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
names = ['Goodaura', 'Shino', 'Soliko', 'Kudebi', 'Sadzele', 'Kikilo']
srow = ''.join(f'<div style="position: relative; display: flex; align-items: center; justify-content: space-between; height: 52px; padding: 0 14px 0 30px; background: {C["blue"] if i % 2 == 0 else C["ink"]}; color: rgba(255,255,255,0.55); clip-path: polygon(0 50%, 18px 0, 100% 0, 100% 100%, 18px 100%)"><span style="position: absolute; left: 10px; right: 0; top: 0; height: 13px; background: #FFFFFF; border-radius: 0 0 14px 22px"></span><span style="{DISP} font-size: 28px; line-height: 1; margin-top: 8px" dir="ltr">{n}</span><span style="margin-top: 8px; font-size: 13px">?</span></div>' for i, n in enumerate(names))
body = (f'<div style="{ROOT} display: flex; flex-direction: column">{topbar("מצב ההר")}'
        f'<div style="padding: 20px 20px 10px; display: flex; flex-direction: column; gap: 4px"><h1 style="margin: 0; {DISP} font-size: 46px; line-height: 1">ההר עוד ישן</h1><p style="margin: 0; font-size: 15px; line-height: 1.45">עוד אין דיווח על רכבלים. העונה בגודאורי נפתחת בדרך כלל בדצמבר, ואז השלטים יתנקו מהשלג.</p></div>'
        f'<div style="margin: 6px 20px 0 36px; position: relative; display: flex; flex-direction: column; gap: 12px"><div style="position: absolute; right: -16px; top: -10px; bottom: -30px; width: 8px; background: {C["ink"]}"></div>{srow}</div>'
        f'<div style="margin: auto 20px 18px; padding: 12px 14px; background: {C["paper"]}; border: 1px solid {C["rule"]}; font-size: 13.5px; line-height: 1.45">בינתיים המפה, המסלולים והסרטונים עובדים כרגיל. <b>לא מנחשים מצב:</b> כשאין דיווח עדכני מ-MTA, כתוב כאן שאין.</div></div>')
out['S3-NoData.dc.html'] = page('אין עדיין מידע', body)

# =================================================================== N1: sunset theatre (י1, י2, י4, י5, י6, י7)
d = T['dem']; Hd = np.frombuffer(base64.b64decode(d['b64']), dtype='<i2').reshape(d['ny'], d['nx']).astype(float)
CX = (d['x1'] - d['x0']) / (d['nx'] - 1); CY = (d['y1'] - d['y0']) / (d['ny'] - 1)
def el(x, y):
    c = min(max((x - d['x0']) / CX, 0), d['nx'] - 1.001); r = min(max((y - d['y0']) / CY, 0), d['ny'] - 1.001); c0, r0 = int(c), int(r); fc, fr = c - c0, r - r0
    return Hd[r0, c0]*(1-fc)*(1-fr)+Hd[r0, c0+1]*fc*(1-fr)+Hd[r0+1, c0]*(1-fc)*fr+Hd[r0+1, c0+1]*fc*fr
VX, VY = -452.0, 4441.0; E0 = el(VX, VY) + 2; prof = []
for i in range(118):
    a = math.radians(-56 + i * 112 / 117); best = 0
    for dist in range(150, 9000, 60):
        best = max(best, math.degrees(math.atan2(el(VX + math.sin(a)*dist, VY - math.cos(a)*dist) - E0, dist)))
    prof.append(best)
SH = 340; RH = 230; sc = RH * 0.9 / max(prof)
ridge = f'M0,{SH} ' + ' '.join(f'L{i*390/117:.1f},{SH - p*sc:.1f}' for i, p in enumerate(prof)) + f' L390,{SH} Z'
near = f'M0,{SH} ' + ' '.join(f'L{i*390/117:.1f},{SH - (0.4*p + 3 + 4*math.sin(i/6))*sc*0.55:.1f}' for i, p in enumerate(prof)) + f' L390,{SH} Z'
peaks = sorted(range(118), key=lambda i: -prof[i])[:1]
stars = ''.join(f'<span style="position: absolute; left: {x}px; top: {y}px; width: {2+i%2}px; height: {2+i%2}px; border-radius: 2px; background: #FFFFFF; opacity: {{{{ starOp }}}}; transition: opacity 1.2s {i*0.08:.2f}s"></span>' for i, (x, y) in enumerate([(30,26),(80,58),(132,20),(196,44),(258,24),(312,66),(352,30),(58,104),(170,90),(296,110),(236,78),(118,134),(24,160),(344,142),(210,150),(150,60)]))
flakes = ''.join(f'<span class="flake" style="left: {x}px; animation-duration: {6+i%4}s; animation-delay: -{i*0.7:.1f}s; width: {2+i%3}px; height: {2+i%3}px"></span>' for i, x in enumerate(range(12, 390, 22)))
windows = ''.join(f'<span style="position: absolute; left: {x}px; top: {y}px; width: 3px; height: 3px; background: #FFD27A; box-shadow: 0 0 6px 2px rgba(255,200,110,0.7); opacity: {{{{ winOp }}}}; transition: opacity 1s {i*0.1:.1f}s"></span>' for i, (x, y) in enumerate([(62,322),(74,326),(88,320),(101,328),(120,324),(138,330),(152,322),(166,327),(214,318),(228,325),(240,320),(300,326),(316,321),(330,329)]))
SUNSVG = '<svg width="44" height="44" viewBox="0 0 44 44" aria-hidden="true"><circle cx="22" cy="22" r="21" fill="#F4B942" fill-opacity="0.25"></circle><circle cx="22" cy="22" r="13" fill="#F4B942"></circle></svg>'
MOONSVG = '<svg width="40" height="40" viewBox="0 0 34 34" aria-hidden="true"><path d="M17 3 A14 14 0 0 1 17 31 A7.5 14 0 0 0 17 3 Z" fill="#F4F7FB"></path></svg>'
layers = ''.join(f'<div style="position: absolute; inset: 0; background: {g}; opacity: {{{{ {k} }}}}; transition: opacity 1.2s"></div>' for k, g in [('oDay', 'linear-gradient(#B9D2E8, #E6EEF5)'), ('oGold', 'linear-gradient(#DDBF96, #F4E2C6)'), ('oSet', 'linear-gradient(#2E3A68 0%, #B8637F 55%, #F29A5E 100%)'), ('oNight', 'linear-gradient(#050A15, #172340)')])
toggle = f'<button type="button" onClick="{{{{ flip }}}}" aria-label="{{{{ toggleLbl }}}}" style="position: relative; width: 48px; height: 48px; border-radius: 24px; border: 1.5px solid {{{{ ink }}}}; background: transparent; overflow: hidden; padding: 0"><span style="position: absolute; left: 0; top: 0; width: 48px; height: 96px; transform: rotate({{{{ wheel }}}}deg); transform-origin: 24px 48px; transition: transform 1.2s cubic-bezier(.5,0,.3,1)"><span style="position: absolute; left: 12px; top: 10px; width: 24px; height: 24px; border-radius: 12px; background: #F4B942"></span><span style="position: absolute; left: 13px; top: 62px; width: 22px; height: 22px">{MOONSVG.replace("40", "22")}</span></span></button>'
body = f'''<div class="anim" style="{ROOT} background: {{{{ bg }}}}; color: {{{{ ink }}}}; transition: background-color 1.2s, color 1.2s; display: flex; flex-direction: column">
<div style="display: flex; align-items: center; justify-content: space-between; padding: 8px 16px 8px 10px; background: {{{{ paper }}}}; border-bottom: 1px solid {{{{ rule }}}}; transition: background-color 1.2s">
<span style="{DISP} font-size: 30px; line-height: 1">גודאורי 2027</span>
<div style="display: flex; align-items: center; gap: 10px"><span style="display: flex; flex-direction: column; align-items: flex-end; font-size: 11px; color: {{{{ muted }}}}">בגודאורי<b style="{DISP} font-size: 22px; line-height: 1; color: {{{{ ink }}}}" dir="ltr">{{{{ clock }}}}</b></span>{toggle}</div></div>
<div style="position: relative; height: {SH}px; flex: none; overflow: hidden">{layers}{stars}
<div style="position: absolute; left: 250px; top: {{{{ sunTop }}}}px; opacity: {{{{ sunOp }}}}; transition: top 1.4s cubic-bezier(.4,0,.3,1), opacity 1s">{SUNSVG}</div>
<div style="position: absolute; left: 70px; top: {{{{ moonTop }}}}px; transition: top 1.4s cubic-bezier(.4,0,.3,1)">{MOONSVG}</div>
<svg width="390" height="{SH}" viewBox="0 0 390 {SH}" aria-hidden="true" style="position: absolute; left: 0; top: 0"><path d="{ridge}" fill="{{{{ far }}}}" style="transition: fill 1.2s"></path><path d="{ridge}" fill="#F08A9A" opacity="{{{{ glow }}}}" style="transition: opacity 1.2s"></path><path d="{near}" fill="{{{{ nearC }}}}" style="transition: fill 1.2s"></path></svg>
{windows}<div style="position: absolute; inset: 0; opacity: {{{{ snowOp }}}}; transition: opacity 1s; pointer-events: none">{flakes}</div>
<span style="position: absolute; top: 12px; right: 16px; font-size: 13px; font-weight: 600; color: {{{{ skyInk }}}}">{{{{ phaseLbl }}}}</span></div>
<div style="padding: 16px 20px 0; display: flex; flex-direction: column; gap: 12px">
<h1 style="margin: 0; {DISP} font-size: 46px; line-height: 1">גודאורי, גאורגיה</h1>
<div style="display: inline-flex; align-self: flex-start; align-items: center; gap: 10px; padding: 8px 14px; background: {{{{ paper }}}}; border: 1px solid {{{{ rule }}}}; transition: background-color 1.2s"><span style="width: 14px; height: 14px; border-radius: 7px; background: {{{{ dotC }}}}"></span><span style="{DISP} font-size: 30px; line-height: 1">{{{{ count }}}}</span></div>
<div role="group" aria-label="שעה ביום" style="display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 6px"><sc-for list="{{{{ phases }}}}" as="p" hint-placeholder-count="4"><button type="button" aria-pressed="{{{{ p.on }}}}" onClick="{{{{ p.pick }}}}" style="height: 44px; border: 1.5px solid {{{{ ink }}}}; background: {{{{ p.bg }}}}; color: {{{{ p.fg }}}}; font: inherit; font-size: 13.5px; font-weight: 700">{{{{ p.lbl }}}}</button></sc-for></div>
<p style="margin: 0; font-size: 12.5px; color: {{{{ muted }}}}; line-height: 1.4">הרכס הוא הקו האמיתי כפי שהוא נראה מהכפר. באתר השמיים ילכו לבד לפי השעה בגודאורי, והכפתור למעלה עובר בין יום ללילה.</p></div></div>'''
css = '.flake{position:absolute;top:-10px;border-radius:50%;background:#fff;opacity:.85;animation:fall linear infinite} @keyframes fall{to{transform:translate(-18px,360px)}}'
script = '''const DAYP = { bg: '#EEF2F5', paper: '#FFFFFF', ink: '#13233A', muted: '#4B5A6F', rule: '#CBD5DF' };
const NIGHTP = { bg: '#0D1522', paper: '#16223A', ink: '#EAF0F7', muted: '#A3B3C8', rule: '#2A3B55' };
const PH = {
  day:    { lbl: 'צהריים', clock: '12:30', sunTop: 40,  sunOp: 1, moonTop: 400, o: [1,0,0,0], star: 0,   win: 0,   snow: 0, glow: 0,    far: '#B4C3D2', nearC: '#EEF2F5', skyInk: '#13233A', dark: false, phase: 'שמש · 12.1.2027' },
  gold:   { lbl: 'שעת זהב', clock: '16:40', sunTop: 150, sunOp: 1, moonTop: 400, o: [0,1,0,0], star: 0,   win: 0,   snow: 0, glow: 0.25, far: '#C3AFA3', nearC: '#EEE4D8', skyInk: '#13233A', dark: false, phase: 'השמש נמוכה, אור חם על הפסגות' },
  sunset: { lbl: 'שקיעה',  clock: '17:44', sunTop: 250, sunOp: 1, moonTop: 400, o: [0,0,1,0], star: 0.3, win: 0.7, snow: 0, glow: 0.8,  far: '#5B4E78', nearC: '#2B3456', skyInk: '#FFFFFF', dark: true,  phase: 'השמש שוקעת מאחורי הרכס' },
  night:  { lbl: 'לילה',   clock: '21:10', sunTop: 400, sunOp: 0, moonTop: 40,  o: [0,0,0,1], star: 1,   win: 1,   snow: 1, glow: 0,    far: '#22345A', nearC: '#0D1522', skyInk: '#EAF0F7', dark: true,  phase: 'ירח · שלג קל' },
};
const ORDER = ['day', 'gold', 'sunset', 'night'];
class Component extends DCLogic {
  state = { ph: 'sunset' };
  renderVals() {
    const k = this.state.ph, p = PH[k], pal = p.dark ? NIGHTP : DAYP, night = k === 'night' || k === 'sunset';
    const phases = ORDER.map((id) => ({ lbl: PH[id].lbl, on: id === k, bg: id === k ? pal.ink : pal.paper, fg: id === k ? pal.paper : pal.ink, pick: () => this.setState({ ph: id }) }));
    return { ...pal, clock: p.clock, sunTop: p.sunTop, sunOp: p.sunOp, moonTop: p.moonTop, oDay: p.o[0], oGold: p.o[1], oSet: p.o[2], oNight: p.o[3],
      starOp: p.star, winOp: p.win, snowOp: p.snow, glow: p.glow, far: p.far, nearC: p.nearC, skyInk: p.skyInk, phaseLbl: p.phase,
      count: night ? 'עוד 102 לילות' : 'עוד 102 ימים', dotC: night ? '#EAF0F7' : '#F4B942',
      wheel: night ? 180 : 0, toggleLbl: night ? 'מעבר ליום' : 'מעבר ללילה', flip: () => this.setState({ ph: night ? 'day' : 'night' }), phases };
  }
}'''
out['N1-SunsetTheatre.dc.html'] = page('שקיעה בדף הבית', body, script, css)

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
k5 = 0.8; l5 = 195 - 250 * k5; tp5 = 190 - 1250 * k5; f5 = tr(k5, l5, tp5)
pins = []
for n in ['Goodaura', 'Zuma', 'Soliko', 'Shino', 'Pirveli']:
    l = next(x for x in D['lifts'] if x.get('name') == n); x, y = f5(*ipx(l['g'][0])); pins.append({'k': n, 'x': round(x), 'y': round(y)})
body = (f'<div style="{ROOT} display: flex; flex-direction: column">{topbar("נקודת מפגש", "בית")}'
        + map_img(A['s12'], k5, l5, tp5, 400, 'הכפר והתחנות התחתונות',
                  f'<sc-for list="{{{{ pins }}}}" as="p" hint-placeholder-count="5"><button type="button" onClick="{{{{ p.pick }}}}" aria-label="{{{{ p.k }}}}" style="position: absolute; left: {{{{ p.bx }}}}px; top: {{{{ p.by }}}}px; width: 44px; height: 56px; border: 0; background: transparent; padding: 0"><svg width="44" height="56" viewBox="0 0 44 56" aria-hidden="true"><path d="M22 54 C22 54 6 34 6 21 A16 16 0 0 1 38 21 C38 34 22 54 22 54 Z" fill="{{{{ p.fill }}}}" stroke="{C["ink"]}" stroke-width="2"></path><circle cx="22" cy="21" r="6" fill="#FFFFFF"></circle></svg></button><span style="position: absolute; left: {{{{ p.lx }}}}px; top: {{{{ p.ly }}}}px; padding: 2px 7px; background: {{{{ p.lbg }}}}; color: {{{{ p.lfg }}}}; border: 1.5px solid {C["ink"]}; font-size: 12.5px; font-weight: 700; white-space: nowrap" dir="ltr">{{{{ p.k }}}}</span></sc-for>'
                  f'<span style="position: absolute; bottom: 10px; right: 10px; padding: 4px 10px; background: rgba(255,255,255,0.94); border: 1px solid {C["rule"]}; font-size: 12.5px; font-weight: 600">לוחצים על סיכה, או בכל מקום במפה</span>')
        + f'<div style="flex-grow: 1; background: {C["paper"]}; padding: 12px 16px 0; display: flex; flex-direction: column; gap: 8px"><span style="font-size: 12.5px; font-weight: 700; color: {C["muted"]}">מהיר: הנקודות של הקבוצה</span>'
        f'<div style="display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px"><sc-for list="{{{{ presets }}}}" as="q" hint-placeholder-count="3"><button type="button" aria-pressed="{{{{ q.on }}}}" onClick="{{{{ q.pick }}}}" style="min-height: 56px; border: 1.5px solid {C["ink"]}; background: {{{{ q.bg }}}}; color: {{{{ q.fg }}}}; font: inherit; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 1px"><b style="font-size: 14px">{{{{ q.lbl }}}}</b><span style="font-size: 12px" dir="ltr">{{{{ q.sub }}}}</span></button></sc-for></div>'
        f'<a href="M2-MeetCard.dc.html" style="margin-top: 4px; height: 52px; background: {C["ink"]}; color: #FFFFFF; text-decoration: none; font-size: 16px; font-weight: 700; display: flex; align-items: center; justify-content: center">יצירת כרטיס מפגש: <span dir="ltr" style="margin-right: 6px">{{{{ place }}}} · {{{{ time }}}}</span></a></div></div>')
script = f'''const PINS = {json.dumps(pins)};
const PRESETS = [ {{ id: 'am', lbl: 'רכבל הבוקר', place: 'Goodaura', time: '09:30' }}, {{ id: 'noon', lbl: 'צהריים', place: 'Zuma', time: '13:00' }}, {{ id: 'pm', lbl: 'סוף יום', place: 'Soliko', time: '16:30' }} ];
class Component extends DCLogic {{
  state = {{ place: 'Goodaura', time: '12:30', preset: '' }};
  renderVals() {{
    const s = this.state;
    const pins = PINS.map((p) => {{ const on = p.k === s.place; return {{ ...p, bx: p.x - 22, by: p.y - 54, lx: p.x + (p.x > 200 ? -80 : 16), ly: p.y - 44, fill: on ? '#F4B942' : '#FFFFFF', lbg: on ? '#13233A' : '#FFFFFF', lfg: on ? '#FFFFFF' : '#13233A', pick: () => this.setState({{ place: p.k, preset: '' }}) }}; }});
    const presets = PRESETS.map((q) => {{ const on = q.id === s.preset; return {{ ...q, sub: q.place + ' · ' + q.time, on, bg: on ? '#13233A' : '#FFFFFF', fg: on ? '#FFFFFF' : '#13233A', pick: () => this.setState({{ place: q.place, time: q.time, preset: q.id }}) }}; }});
    return {{ pins, presets, place: s.place, time: s.time }};
  }}
}}'''
out['M1-MeetPick.dc.html'] = page('בחירת נקודת מפגש', body, script)

# =================================================================== M2: meeting boarding pass + how to get there (מ2, מ5)
routes = [('מ-Kudebi', ['Kudebi 1', 'Goodaura 1'], '#D1342B', 'כ-14 דק׳'), ('מ-Tatra', ['Tatra 1', 'Shino'], C['blue'], 'כ-12 דק׳'), ('מהכפר', ['הליכה לתחנה'], C['muted'], 'כ-5 דק׳')]
rrows = ''.join(f'<div style="display: flex; align-items: center; justify-content: space-between; gap: 8px; min-height: 48px; border-bottom: 1px solid {C["rule"]}"><span style="display: flex; flex-direction: column"><b style="font-size: 14px" dir="auto">{a}</b><span style="font-size: 12.5px; color: {C["muted"]}" dir="ltr">{" → ".join(b)}</span></span><span style="font-size: 13px; font-weight: 700">{t}</span></div>' for a, b, c, t in routes)
body = f'''<div style="{ROOT} display: flex; flex-direction: column">{topbar("כרטיס מפגש", "חזרה")}
<div style="padding: 26px 22px 0">
<div style="display: flex; background: {C["paper"]}; transform: rotate(-2deg); box-shadow: 0 10px 24px rgba(19,35,58,0.18)">
<div style="flex: 1; padding: 14px 16px; display: flex; flex-direction: column; gap: 8px">
<span style="font-size: 12px; font-weight: 600; color: {C["muted"]}">כרטיס מפגש · החבר׳ה</span>
<span style="{DISP} font-size: 44px; line-height: 0.95" dir="ltr">Goodaura</span>
<span style="font-size: 13px; color: {C["muted"]}">התחנה התחתונה של הגונדולה</span>
<div style="display: flex; gap: 22px"><span style="display: flex; flex-direction: column"><small style="font-size: 12px; color: {C["muted"]}">שעה</small><b style="{DISP} font-size: 30px; line-height: 1">12:30</b></span><span style="display: flex; flex-direction: column"><small style="font-size: 12px; color: {C["muted"]}">גובה</small><b style="{DISP} font-size: 30px; line-height: 1">2,161 מ׳</b></span></div></div>
<div style="width: 96px; flex: none; background: {C["accent"]}; color: {C["ink"]}; border-inline-start: 2px dashed {C["ink"]}; display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center"><b style="{DISP} font-size: 58px; line-height: 0.85">38</b><span style="font-size: 13px; font-weight: 700">דקות<br>לפגישה</span></div></div></div>
<div style="padding: 26px 20px 0; display: flex; flex-direction: column"><h2 style="margin: 0 0 4px; {DISP} font-size: 28px; line-height: 1">איך מגיעים</h2>{rrows}
<p style="margin: 6px 0 0; font-size: 11.5px; color: {C["muted"]}">לפי החיבורים בנתוני המסלולים. זמנים משוערים, בלי תורים.</p></div>
<div style="margin-top: auto; padding: 12px 16px 18px"><a href="M3-MeetShare.dc.html" style="height: 56px; background: {C["ink"]}; color: #FFFFFF; text-decoration: none; font-size: 17px; font-weight: 700; display: flex; align-items: center; justify-content: center; gap: 10px">{SHARE}שליחה לקבוצה</a></div></div>'''
out['M2-MeetCard.dc.html'] = page('כרטיס מפגש', body)

# =================================================================== M3: share preview (מ3, מ4)
gp = next(p for p in pins if p['k'] == 'Goodaura')
k6 = k5 * 0.62; l6 = 130 - 250 * k6 + 0; tp6 = 80 - 1250 * k6
gx, gy = (gp['x'] - l5) / k5 * k6 + l6, (gp['y'] - tp5) / k5 * k6 + tp6
body = f'''<div style="{ROOT} background: #E7E1D9; display: flex; flex-direction: column">
<div style="display: flex; align-items: center; gap: 10px; padding: 10px 14px; background: {C["ink"]}; color: #FFFFFF"><span style="width: 36px; height: 36px; border-radius: 18px; background: {C["blue"]}; display: flex; align-items: center; justify-content: center; {DISP} font-size: 20px">ג</span><span style="display: flex; flex-direction: column"><b style="font-size: 15px">החבר׳ה · גודאורי</b><span style="font-size: 12px; color: {C["nmuted"]}">6 משתתפים</span></span></div>
<div style="padding: 14px 12px; display: flex; flex-direction: column; gap: 10px; flex-grow: 1">
<div style="align-self: flex-end; max-width: 250px; padding: 8px 12px; background: #FFFFFF; font-size: 14px">איפה נפגשים לצהריים?</div>
<div style="align-self: flex-start; width: 270px; background: #D9F2C4; padding: 4px; display: flex; flex-direction: column">
<div style="position: relative; height: 160px; overflow: hidden; background: #DCE8F1"><img src="{A["s12"]}" alt="" style="position: absolute; left: {l6:.1f}px; top: {tp6:.1f}px; width: {IMG_W*k6:.1f}px; height: {IMG_H*k6:.1f}px; max-width: none"><svg width="34" height="44" viewBox="0 0 44 56" aria-hidden="true" style="position: absolute; left: {gx-17:.0f}px; top: {gy-43:.0f}px"><path d="M22 54 C22 54 6 34 6 21 A16 16 0 0 1 38 21 C38 34 22 54 22 54 Z" fill="{C["accent"]}" stroke="{C["ink"]}" stroke-width="2.5"></path><circle cx="22" cy="21" r="6" fill="#FFFFFF"></circle></svg>
<div style="position: absolute; left: 0; right: 0; bottom: 0; padding: 6px 10px; background: rgba(19,35,58,0.85); color: #FFFFFF; display: flex; justify-content: space-between; align-items: baseline"><span style="{DISP} font-size: 26px; line-height: 1" dir="ltr">Goodaura</span><span style="{DISP} font-size: 26px; line-height: 1">12:30</span></div></div>
<div style="padding: 8px 8px 4px; display: flex; flex-direction: column; gap: 3px"><b style="font-size: 14px">נפגשים ב-<span dir="ltr">Goodaura</span> ב-12:30</b><span style="font-size: 13px">התחנה התחתונה של הגונדולה. לחצו לראות על המפה:</span><span style="font-size: 12.5px; color: {C["blue"]}; text-decoration: underline" dir="ltr">gudauri-ski-trip.vercel.app/#meet/goodaura-1230</span><span style="align-self: flex-start; font-size: 11px; color: {C["muted"]}">12:02</span></div></div>
<div style="align-self: flex-end; max-width: 250px; padding: 8px 12px; background: #FFFFFF; font-size: 14px">מגיע מ-<span dir="ltr">Kudebi</span>, 15 דק׳</div></div>
<p style="margin: 0 16px 16px; padding: 10px 12px; background: rgba(255,255,255,0.85); font-size: 12.5px; line-height: 1.4">כך תיראה ההודעה בקבוצה: תמונה של ההר עם הסיכה, והקישור פותח את האתר ישר על הנקודה.</p></div>'''
out['M3-MeetShare.dc.html'] = page('שיתוף המפגש', body)

dst = root / 'design/canvas/project'
for n, s in out.items():
    (dst / n).write_text(s, encoding='utf-8')
print({n: len(s) for n, s in out.items()})
