#!/usr/bin/env python3
"""Round 4 design screens: the ski game (G1 to G7), built around the friends.

The descent corridor in G2, G6 and G7 is the real Tatra 2 line (OpenStreetMap, from
site/data/runs-and-lifts.json) with heights from the elevation model, seen from behind
the skier. Times, results and traits are sample data and are marked so on screen.
Run from the repo root with numpy: python3 design/round4/build.py"""
import json, math, base64, pathlib
import numpy as np
root = pathlib.Path(__file__).resolve().parent.parent.parent
T = json.load(open(root / 'site/data/terrain.json', encoding='utf-8'))
D = json.load(open(root / 'site/data/runs-and-lifts.json', encoding='utf-8'))
TRIP = json.load(open(root / 'design/data/trip.json', encoding='utf-8'))
PANO = {'noon': '/_blob/d5ad1e6d2660c28123782ea318a7fd85', 'sunset': '/_blob/7c3d6f578ae52970e966e31246d900c9', 'gold': '/_blob/98b133d6dbe89ed912fd5c489d3704a3'}
C = {'snow': '#EEF2F5', 'paper': '#FFFFFF', 'ink': '#13233A', 'muted': '#4B5A6F', 'rule': '#CBD5DF', 'blue': '#1F5FC4',
     'green': '#1B8A4C', 'red': '#D1342B', 'accent': '#F4B942', 'nbg': '#0D1522', 'npaper': '#16223A', 'nmuted': '#A3B3C8'}
DISP = "font-family: Karantina, 'Arial Narrow', sans-serif; font-weight: 700;"
BODY = "font-family: 'IBM Plex Sans Hebrew', 'Segoe UI', system-ui, sans-serif;"
FONTS = '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Karantina:wght@700&amp;family=IBM+Plex+Sans+Hebrew:wght@400;500;600;700&amp;display=swap">'
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

# ---------------------------------------------------------------- the friends (colours only; traits come from Pini)
SHORT = ['יהודה ו׳', 'דובי', 'שרוליק', 'מוישי', 'יהודה ה׳', 'פיני']
JACKET = ['#D1342B', '#1F5FC4', '#F4B942', '#1B8A4C', '#7B4FC4', '#F07A2E']
PANTS = ['#13233A', '#2A3B55', '#13233A', '#3A4556', '#13233A', '#2A3B55']
assert len(TRIP['members']) == 6

def skier(jacket, pants='#13233A', lean=0, scale=1.0, ghost=False, crouch=0.0):
    """A skier seen from behind: skis, legs, jacket, helmet, poles. Origin at the skis' centre."""
    op = ' opacity="0.45"' if ghost else ''
    h = 1 - 0.18 * crouch
    return (f'<g transform="rotate({lean}) scale({scale})"{op}>'
            f'<path d="M-15,0 L-5,-2 M15,0 L5,-2" stroke="#13233A" stroke-width="4" stroke-linecap="round"></path>'
            f'<line x1="-20" y1="-2" x2="-28" y2="{-44*h:.0f}" stroke="#3A4556" stroke-width="2"></line><line x1="20" y1="-2" x2="28" y2="{-44*h:.0f}" stroke="#3A4556" stroke-width="2"></line>'
            f'<path d="M-9,-2 L-7,{-26*h:.0f} L7,{-26*h:.0f} L9,-2 L3,-2 L0,{-16*h:.0f} L-3,-2 Z" fill="{pants}"></path>'
            f'<path d="M-12,{-24*h:.0f} Q-13,{-50*h:.0f} 0,{-52*h:.0f} Q13,{-50*h:.0f} 12,{-24*h:.0f} Z" fill="{jacket}"></path>'
            f'<path d="M-12,{-38*h:.0f} L-26,{-42*h:.0f} M12,{-38*h:.0f} L26,{-42*h:.0f}" stroke="{jacket}" stroke-width="6" stroke-linecap="round"></path>'
            f'<circle cx="0" cy="{-60*h:.0f}" r="9" fill="#F4F7FB" stroke="#13233A" stroke-width="1.5"></circle>'
            f'<rect x="-8" y="{-63*h:.0f}" width="16" height="4" rx="2" fill="#13233A"></rect></g>')

# ---------------------------------------------------------------- the real corridor: Tatra 2, seen from behind the skier
p = next(x for x in D['pistes'] if x['key'] == 'Tatra 2')
d = T['dem']; H = np.frombuffer(base64.b64decode(d['b64']), dtype='<i2').reshape(d['ny'], d['nx']).astype(float)
CX = (d['x1'] - d['x0']) / (d['nx'] - 1); CY = (d['y1'] - d['y0']) / (d['ny'] - 1)
KX = 111320 * math.cos(math.radians(42.51))
def elev(x, y):
    c = min(max((x - d['x0']) / CX, 0), d['nx'] - 1.001); r = min(max((y - d['y0']) / CY, 0), d['ny'] - 1.001); c0, r0 = int(c), int(r); fc, fr = c - c0, r - r0
    return H[r0, c0]*(1-fc)*(1-fr)+H[r0, c0+1]*fc*(1-fr)+H[r0+1, c0]*(1-fc)*fr+H[r0+1, c0+1]*fc*fr
line = max((s for s in p['segs'] if not s['area']), key=lambda s: len(s['g']))['g']
pts = [((q[1] - 44.495) * KX, -(q[0] - 42.51) * 111320) for q in line]
if elev(*pts[0]) < elev(*pts[-1]): pts = pts[::-1]
# resample every 6 m
dense = [pts[0]]; acc = 0
for a, b in zip(pts, pts[1:]):
    L = math.hypot(b[0] - a[0], b[1] - a[1]); n = max(1, int(L / 6))
    dense += [(a[0] + (b[0] - a[0]) * k / n, a[1] + (b[1] - a[1]) * k / n) for k in range(1, n + 1)]
cum = [0.0]
for a, b in zip(dense, dense[1:]): cum.append(cum[-1] + math.hypot(b[0] - a[0], b[1] - a[1]))
TOTAL = cum[-1]
def corridor(start_m, W=390, HZ=300, F=420, cam_h=2.0, width=24, n=110, sky=True, stripe=('#FFFFFF', '#E3EAF3'), edge='#8FA7C0', shoulder='#A9BED4'):
    """Pseudo-3D projection of the real line ahead of the skier. Returns SVG and the projected centre line."""
    i0 = next(i for i, c in enumerate(cum) if c >= start_m)
    ox, oy = dense[i0]; nx_, ny_ = dense[min(i0 + 3, len(dense) - 1)]
    hd = math.atan2(nx_ - ox, -(ny_ - oy))  # heading, from north
    h0 = elev(ox, oy) + cam_h
    # a few rows just in front of the skis, on the local slope, so the piste reaches the bottom of the screen
    j1 = min(i0 + 3, len(dense) - 1); s0 = (elev(ox, oy) - elev(*dense[j1])) / max(1, cum[j1] - cum[i0])
    rows = [(f, 0.0, -cam_h - f * s0) for f in (1.2, 2, 3, 4.5)]
    for k in range(1, n):
        j = min(i0 + k, len(dense) - 1); x, y = dense[j]
        dx, dy = x - ox, y - oy
        fwd = dx * math.sin(hd) - dy * math.cos(hd); lat = dx * math.cos(hd) + dy * math.sin(hd)
        if fwd < 2: continue
        z = elev(x, y) - h0
        rows.append((fwd, lat, z))
    proj = [(W / 2 + lat / f * F, HZ - z / f * F, width / f * F, f) for f, lat, z in rows]
    svg = ''
    for i in range(len(proj) - 1, 0, -1):
        (xa, ya, wa, fa), (xb, yb, wb, fb) = proj[i - 1], proj[i]
        sa, sb = wa * 1.3, wb * 1.3  # the untouched snow at the piste edge, darker
        svg += f'<path d="M{xa-sa:.1f},{ya:.1f} L{xa+sa:.1f},{ya:.1f} L{xb+sb:.1f},{yb:.1f} L{xb-sb:.1f},{yb:.1f} Z" fill="{shoulder}"></path>'
        svg += f'<path d="M{xa-wa:.1f},{ya:.1f} L{xa+wa:.1f},{ya:.1f} L{xb+wb:.1f},{yb:.1f} L{xb-wb:.1f},{yb:.1f} Z" fill="{stripe[0]}" stroke="{stripe[0]}" stroke-width=".6"></path>'
    # corduroy: grooming lines along the run, and a faint band every few metres for the sense of speed
    for t in (-0.8, -0.55, -0.3, -0.08, 0.14, 0.37, 0.6, 0.84):
        svg += '<polyline points="' + ' '.join(f'{x+w*t:.1f},{y:.1f}' for x, y, w, f in proj) + f'" fill="none" stroke="{stripe[1]}" stroke-width="1.6"></polyline>'
    for i in range(1, len(proj) - 1, 3):
        x, y, w, f = proj[i]
        svg += f'<line x1="{x-w:.1f}" y1="{y:.1f}" x2="{x+w:.1f}" y2="{y:.1f}" stroke="{stripe[1]}" stroke-width="{max(.4, 6/f):.1f}" opacity=".6"></line>'
    for i in range(len(proj) - 1, 0, -1):
        (xb, yb, wb, fb) = proj[i]
        if i % 9 == 0 and f_ok(fb):  # gate poles on both sides
            for sgn, gc in ((-1, '#D1342B'), (1, '#1F5FC4')):
                gx = xb + sgn * wb * 1.05; ph = max(3, 38 / fb * 16)
                svg += f'<line x1="{gx:.1f}" y1="{yb:.1f}" x2="{gx:.1f}" y2="{yb-ph:.1f}" stroke="{gc}" stroke-width="{max(1, 5/fb*14):.1f}"></line>'
    # edges
    L = ' '.join(f'{x-w:.1f},{y:.1f}' for x, y, w, f in proj); R = ' '.join(f'{x+w:.1f},{y:.1f}' for x, y, w, f in proj)
    svg += f'<polyline points="{L}" fill="none" stroke="{edge}" stroke-width="2"></polyline><polyline points="{R}" fill="none" stroke="{edge}" stroke-width="2"></polyline>'
    return svg, proj

def f_ok(f): return f > 5

def backdrop(src, W=390, HZ=300, dy=0):
    # the real view from the village, as the far background
    return f'<img src="{src}" alt="" style="position: absolute; left: -40px; top: {HZ-300+dy}px; width: 470px; height: 482px; max-width: none">'

def ground(HZ=300, top='#B7C9DC', bottom='#CFDCEA'):
    return f'<div style="position: absolute; left: 0; right: 0; top: {HZ}px; bottom: 0; background: linear-gradient({top}, {bottom})"></div>'

out = {}
CSS_SNOW = ('.spray{position:absolute;border-radius:50%;background:#fff;animation:spray 0.9s linear infinite}'
            '@keyframes spray{from{transform:translate(0,0);opacity:.95}to{transform:translate(var(--dx),-40px);opacity:0}}'
            '.speed{position:absolute;height:2px;background:linear-gradient(90deg,transparent,rgba(255,255,255,.9),transparent);animation:speed .5s linear infinite}'
            '@keyframes speed{from{transform:translateY(-60px);opacity:0}30%{opacity:1}to{transform:translateY(260px);opacity:0}}'
            '.bob{animation:bob .6s ease-in-out infinite alternate}@keyframes bob{to{transform:translateY(-3px)}}')
def spray(x, y, n=10):
    return ''.join(f'<span class="spray" style="left: {x + (i-5)*4}px; top: {y - (i%3)*3}px; width: {3+i%3}px; height: {3+i%3}px; --dx: {(i-5)*7}px; animation-delay: -{i*0.09:.2f}s"></span>' for i in range(n))
def speedlines(n=8):
    return ''.join(f'<span class="speed" style="left: {20 + i*45}px; top: {360 + (i*37)%180}px; width: {40 + (i*13)%50}px; animation-delay: -{i*0.07:.2f}s; transform: rotate({(i-4)*8}deg)"></span>' for i in range(n))

# =================================================================== G1: pick your friend
cards = ''
for i, (n, full, j, pa) in enumerate(zip(SHORT, TRIP['members'], JACKET, PANTS)):
    on = i == 5
    cards += (f'<button type="button" onClick="{{{{ c{i} }}}}" aria-pressed="{{{{ p{i} }}}}" style="position: relative; height: 176px; padding: 0; border: {3 if on else 1.5}px solid {{{{ b{i} }}}}; background: {{{{ bg{i} }}}}; display: flex; flex-direction: column; align-items: center; justify-content: flex-end; gap: 2px; font: inherit; color: {C["ink"]}; overflow: hidden; transition: background-color .2s, border-color .2s">'
              f'<span style="position: absolute; top: 0; left: 0; right: 0; height: 6px; background: {j}"></span>'
              f'<svg width="120" height="104" viewBox="-60 -90 120 104" aria-hidden="true" class="bob" style="animation-delay: -{i*0.1:.1f}s">{skier(j, pa, lean=(i%2*2-1)*6, scale=1.2)}</svg>'
              f'<b style="{DISP} font-size: 30px; line-height: 1">{n}</b><span style="font-size: 11.5px; color: {C["muted"]}; padding-bottom: 8px">[תכונה]</span></button>')
body = (f'<div class="anim" style="{ROOT} display: flex; flex-direction: column; background: linear-gradient(#DCE8F2, #EEF2F5 40%)">'
        f'<div style="padding: 18px 20px 6px"><span style="{DISP} font-size: 22px; color: {C["muted"]}">גודאורי 2027 ·  משחק</span><h1 style="margin: 0; {DISP} font-size: 56px; line-height: .9">הירידה</h1><p style="margin: 6px 0 0; font-size: 14px">מי יורד היום? כל אחד מהחבר׳ה, בצבע שלו.</p></div>'
        f'<div style="display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; padding: 10px 16px">{cards}</div>'
        f'<div style="margin: 4px 16px 0; padding: 12px 14px; background: {C["paper"]}; border: 1px solid {C["rule"]}; display: flex; gap: 12px; align-items: center">'
        f'<svg width="54" height="64" viewBox="-30 -72 60 80" aria-hidden="true">' + ''.join(f'<g style="display: {{{{ d{i} }}}}">{skier(JACKET[i], PANTS[i])}</g>' for i in range(6)) + '</svg>'
        f'<div style="display: flex; flex-direction: column; gap: 2px; flex: 1"><b style="{DISP} font-size: 30px; line-height: 1">{{{{ name }}}}</b><span style="font-size: 13px; color: {C["muted"]}">"[המשפט שלו בסיום ירידה]"</span></div></div>'
        f'<div style="margin-top: auto; padding: 12px 16px 18px; display: flex; flex-direction: column; gap: 8px"><a href="G2-Descent.dc.html" style="height: 58px; background: {C["ink"]}; color: #fff; text-decoration: none; display: flex; align-items: center; justify-content: center; gap: 10px; font-size: 17px; font-weight: 700">לרכבל · <span dir="ltr" style="{DISP} font-size: 26px; color: {C["accent"]}">Tatra 2</span></a>'
        f'<span style="font-size: 11.5px; color: {C["muted"]}; text-align: center">התכונות והמשפטים של כל אחד: פיני ימלא, באישור החבר׳ה</span></div></div>')
script = f'''const N = {json.dumps(SHORT, ensure_ascii=False)};
class Component extends DCLogic {{
  state = {{ i: 5 }};
  renderVals() {{
    const s = this.state.i, o = {{ name: N[s] }};
    for (let k = 0; k < 6; k++) {{ o['c' + k] = () => this.setState({{ i: k }}); o['p' + k] = k === s; o['b' + k] = k === s ? '#13233A' : '#CBD5DF'; o['bg' + k] = k === s ? '#FFFFFF' : 'rgba(255,255,255,.55)'; o['d' + k] = k === s ? 'inline' : 'none'; }}
    return o;
  }}
}}'''
out['G1-PickFriend.dc.html'] = page('בוחרים חבר', body, script, CSS_SNOW)

# =================================================================== G2: the descent (behind the skier, real Tatra 2 line)
HZ = 300
G2_AT = 1860
road, proj = corridor(G2_AT, HZ=HZ)
gx, gy, gw, gf = proj[min(9, len(proj)-1)]
ghost = f'<g transform="translate({gx+gw*0.3:.1f},{gy:.1f})">{skier(JACKET[1], PANTS[1], lean=-8, scale=min(1.4, 22/gf), ghost=True)}</g>'
me = f'<g transform="translate(205,748)">{skier(JACKET[5], PANTS[5], lean=10, scale=1.9, crouch=0.6)}</g>'
# mini profile with the real slope colours
S = [(c, elev(*q)) for c, q in zip(cum[::10], dense[::10])]
hmax, hmin = S[0][1], S[-1][1]
def sc_(a): return '#3FA85F' if a < 15 else '#F2C13D' if a < 25 else '#F08A3C' if a < 30 else '#DC3B33'
prof = ''
for (c1, h1), (c2, h2) in zip(S, S[1:]):
    a = math.degrees(math.atan(abs(h1 - h2) / max(1, c2 - c1)))
    prof += f'<line x1="{8 + c1/TOTAL*290:.1f}" y1="{6 + (hmax-h1)/(hmax-hmin)*26:.1f}" x2="{8 + c2/TOTAL*290:.1f}" y2="{6 + (hmax-h2)/(hmax-hmin)*26:.1f}" stroke="{sc_(a)}" stroke-width="3.5" stroke-linecap="round"></line>'
mepos, ghpos = G2_AT / TOTAL, (G2_AT + 55) / TOTAL
i2 = next(i for i, c in enumerate(cum) if c >= G2_AT); SLOPE_HERE = round(math.degrees(math.atan((elev(*dense[i2]) - elev(*dense[i2 + 8])) / (cum[i2 + 8] - cum[i2]))))
def dot(t, col, lbl, below=False):
    i = int(t * (len(S) - 1)); h = S[i][1]
    return f'<g transform="translate({8+t*290:.1f},{6 + (hmax-h)/(hmax-hmin)*26:.1f})"><circle r="5" fill="{col}" stroke="#fff" stroke-width="2"></circle><text y="{14 if below else -8}" text-anchor="middle" font-size="10" font-weight="700" fill="#13233A">{lbl}</text></g>'
hud = (f'<div style="position: absolute; top: 10px; left: 10px; right: 10px; display: flex; justify-content: space-between; align-items: flex-start">'
       f'{sign("Tatra 2", C["blue"], 26, "4px 12px 4px 24px")}'
       f'<div style="display: flex; flex-direction: column; align-items: flex-start; padding: 6px 10px; background: rgba(19,35,58,.82); color: #fff"><b style="{DISP} font-size: 38px; line-height: .9" dir="ltr">1:21.7</b><span style="font-size: 11px; color: {C["accent"]}; font-weight: 700" dir="ltr">דובי 1.4- שנ׳</span></div></div>'
       f'<svg width="306" height="40" viewBox="0 -6 306 52" aria-hidden="true" style="position: absolute; top: 78px; height: 52px; left: 42px; background: rgba(255,255,255,.85)" dir="ltr">{prof}{dot(ghpos, JACKET[1], "דובי")}{dot(mepos, JACKET[5], "אתה", True)}</svg>'
       f'<div style="position: absolute; bottom: 24px; left: 16px; display: flex; flex-direction: column; align-items: center; padding: 6px 10px; background: rgba(19,35,58,.82); color: #fff"><b style="{DISP} font-size: 40px; line-height: .9" dir="ltr">67</b><span style="font-size: 11px">קמ״ש</span></div>'
       f'<div style="position: absolute; bottom: 24px; right: 16px; padding: 6px 10px; background: {C["red"]}; color: #fff; font-size: 12px; font-weight: 700">שיפוע כאן · {SLOPE_HERE}°</div>')
hint = (f'<div class="hintfade" style="position: absolute; left: 0; right: 0; bottom: 100px; display: flex; justify-content: space-between; padding: 0 22px; color: {C["ink"]}; font-size: 12.5px; font-weight: 700">'
        f'<span style="padding: 6px 10px; background: rgba(255,255,255,.85)">← גוררים לפנייה →</span><span style="padding: 6px 10px; background: rgba(255,255,255,.85)">מחזיקים = מתכופפים</span></div>')
body = (f'<div class="anim" style="{ROOT} background: linear-gradient(#5E9BD6, #D6E7F4 {HZ}px)">{backdrop(PANO["noon"], HZ=HZ, dy=-40)}{ground(HZ)}'
        f'<svg width="390" height="844" viewBox="0 0 390 844" aria-hidden="true" style="position: absolute; left: 0; top: 0">{road}{ghost}{me}</svg>'
        f'{spray(205, 748)}{speedlines()}{hud}{hint}</div>')
out['G2-Descent.dc.html'] = page('הירידה', body, css=CSS_SNOW + '.hintfade{animation:hf 4s ease-in forwards}@keyframes hf{70%{opacity:1}to{opacity:0}}')

# =================================================================== G3: the ghost challenge through WhatsApp
road3, proj3 = corridor(1200, HZ=120, F=140, n=50)
chat = (f'<div style="background: #E9E3DA; padding: 12px 12px 14px; display: flex; flex-direction: column; gap: 8px">'
        f'<div style="align-self: flex-end; width: 262px; background: #D9F2C4; padding: 4px; box-shadow: 0 1px 1px rgba(0,0,0,.12)">'
        f'<div style="position: relative; height: 150px; overflow: hidden; background: linear-gradient(#5E9BD6, #D6E7F4 120px)"><svg width="254" height="150" viewBox="68 0 254 150" aria-hidden="true" style="position: absolute; inset: 0">{road3}<g transform="translate(195,140)">{skier(JACKET[5], PANTS[5], lean=8, scale=.8)}</g></svg>'
        f'<div style="position: absolute; left: 6px; right: 6px; bottom: 6px; display: flex; justify-content: space-between; align-items: flex-end">{sign("Tatra 2", C["blue"], 22, "3px 8px 3px 20px")}<span style="padding: 2px 8px; background: {C["accent"]}; {DISP} font-size: 30px; line-height: 1" dir="ltr">1:42.6</span></div></div>'
        f'<div style="padding: 6px 6px 2px; display: flex; flex-direction: column; gap: 2px"><b style="font-size: 14px">פיני ירד ב-<span dir="ltr">Tatra 2</span> ב-1:42.6. מי עוקף?</b><span style="font-size: 12.5px; color: {C["blue"]}; text-decoration: underline" dir="ltr">gudauri-ski-trip.vercel.app/#race/…</span><span style="font-size: 10.5px; color: {C["muted"]}">21:14 ✓✓</span></div></div>'
        f'<div style="align-self: flex-start; background: #fff; padding: 6px 10px; font-size: 14px; box-shadow: 0 1px 1px rgba(0,0,0,.12)"><b style="font-size: 12.5px; color: {JACKET[1]}">דובי</b><br>עוד שנייה אני עוקף אותך</div></div>')
start = (f'<div style="position: relative; flex: 1; overflow: hidden; background: linear-gradient(#13233A, #2A3B55)">'
         f'<div style="position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 10px; color: #fff">'
         f'<span style="font-size: 13px; color: {C["nmuted"]}">הקישור נפתח ישר במירוץ</span>'
         f'<div style="display: flex; align-items: flex-end; gap: 26px"><div style="display: flex; flex-direction: column; align-items: center"><svg width="80" height="90" viewBox="-40 -80 80 90" aria-hidden="true">{skier(JACKET[1], PANTS[1], scale=1.1)}</svg><b style="{DISP} font-size: 30px">דובי</b><span style="font-size: 12px; color: {C["nmuted"]}">אתה</span></div>'
         f'<b style="{DISP} font-size: 44px; color: {C["accent"]}">נגד</b>'
         f'<div style="display: flex; flex-direction: column; align-items: center"><svg width="80" height="90" viewBox="-40 -80 80 90" aria-hidden="true">{skier(JACKET[5], PANTS[5], scale=1.1, ghost=True)}</svg><b style="{DISP} font-size: 30px">פיני</b><span style="font-size: 12px; color: {C["nmuted"]}">רוח רפאים · 1:42.6</span></div></div>'
         f'<b class="count" style="{DISP} font-size: 110px; line-height: .8; color: {C["accent"]}">3</b>'
         f'<span style="font-size: 12.5px; color: {C["nmuted"]}; text-align: center; padding: 0 30px">בלי הרשמה ובלי מסד נתונים: הירידה של פיני שמורה בתוך הקישור עצמו</span></div></div>')
body = (f'<div class="anim" style="{ROOT} display: flex; flex-direction: column">'
        f'<div style="display: flex; align-items: center; gap: 10px; padding: 10px 14px; background: {C["ink"]}; color: #fff"><span style="width: 36px; height: 36px; background: {C["blue"]}; display: flex; align-items: center; justify-content: center; {DISP} font-size: 22px">ג</span><b style="font-size: 15px">החבר׳ה · גודאורי</b></div>'
        f'{chat}{start}</div>')
out['G3-GhostChallenge.dc.html'] = page('רוח רפאים בוואטסאפ', body, css='.count{animation:cnt 3s steps(1) infinite}@keyframes cnt{0%{opacity:1}33%{opacity:.35}66%{opacity:1}}')

# =================================================================== G4: photo finish and the result card
road4, proj4 = corridor(TOTAL - 170, HZ=250, F=300, n=40)
fx, fy, fw, ff = proj4[min(6, len(proj4)-1)]
finish = (f'<rect x="{fx-fw*1.2:.1f}" y="{fy-70:.1f}" width="{fw*2.4:.1f}" height="16" fill="{C["ink"]}"></rect>'
          f'<text x="{fx:.1f}" y="{fy-58:.1f}" text-anchor="middle" font-size="12" font-weight="700" fill="#fff">סיום · Tatra 2</text>'
          f'<line x1="{fx-fw*1.2:.1f}" y1="{fy-54:.1f}" x2="{fx-fw*1.2:.1f}" y2="{fy:.1f}" stroke="{C["ink"]}" stroke-width="4"></line><line x1="{fx+fw*1.2:.1f}" y1="{fy-54:.1f}" x2="{fx+fw*1.2:.1f}" y2="{fy:.1f}" stroke="{C["ink"]}" stroke-width="4"></line>'
          f'<g transform="translate({fx-18:.1f},{fy+24:.1f})">{skier(JACKET[1], PANTS[1], lean=-6, scale=1.3, crouch=.8)}</g><g transform="translate({fx+44:.1f},{fy+80:.1f})">{skier(JACKET[5], PANTS[5], lean=12, scale=1.6, crouch=.5)}</g>')
rows = [(1, SHORT[1], JACKET[1], '1:39.4', ''), (2, SHORT[5], JACKET[5], '1:42.6', '+3.2'), (3, SHORT[2], JACKET[2], '1:47.1', '+7.7')]
res = ''.join(f'<div style="display: flex; align-items: center; gap: 10px; min-height: 44px; border-bottom: 1px solid {C["rule"]}"><b style="{DISP} font-size: 28px; width: 18px">{r}</b><span style="width: 6px; height: 30px; background: {c}"></span><b style="font-size: 15px; flex: 1">{n}</b><span style="{DISP} font-size: 26px" dir="ltr">{t}</span><span style="width: 46px; font-size: 12px; color: {C["red"]}; font-weight: 700" dir="ltr">{g}</span></div>' for r, n, c, t, g in rows)
body = (f'<div class="anim" style="{ROOT} display: flex; flex-direction: column">'
        f'<div style="position: relative; height: 380px; flex: none; overflow: hidden; background: linear-gradient(#5E9BD6, #D6E7F4 250px)">{backdrop(PANO["noon"], HZ=250, dy=-60)}{ground(250)}'
        f'<svg width="390" height="380" viewBox="0 0 390 380" aria-hidden="true" style="position: absolute; left: 0; top: 0; filter: saturate(.9)">{road4}{finish}</svg>'
        f'<div class="flash" style="position: absolute; inset: 0; background: #fff"></div>'
        f'<span style="position: absolute; top: 12px; right: 12px; padding: 4px 10px; background: {C["ink"]}; color: #fff; font-size: 12px; font-weight: 700">צילום סיום · הילוך איטי</span></div>'
        f'<div style="padding: 14px 20px 0; display: flex; flex-direction: column; gap: 6px">'
        f'<div style="display: flex; align-items: baseline; justify-content: space-between"><h2 style="margin: 0; {DISP} font-size: 40px; line-height: 1">דובי עקף אותך</h2><span style="font-size: 13px; color: {C["muted"]}">ב-3.2 שניות</span></div>'
        f'<p style="margin: 0 0 4px; font-size: 13.5px; color: {C["muted"]}">"[המשפט של דובי כשהוא מנצח]"</p>{res}</div>'
        f'<div style="margin-top: auto; padding: 12px 16px 18px; display: grid; grid-template-columns: 1fr 1fr; gap: 8px">'
        f'<a href="#" style="grid-column: 1/-1; height: 54px; background: {C["red"]}; color: #fff; text-decoration: none; display: flex; align-items: center; justify-content: center; font-size: 17px; font-weight: 700">נקמה: עוד ירידה מול דובי</a>'
        f'<a href="#" style="height: 48px; background: {C["ink"]}; color: #fff; text-decoration: none; display: flex; align-items: center; justify-content: center; font-size: 14px; font-weight: 700">שליחת הכרטיס לקבוצה</a>'
        f'<a href="#" style="height: 48px; border: 1.5px solid {C["ink"]}; color: {C["ink"]}; text-decoration: none; display: flex; align-items: center; justify-content: center; font-size: 14px; font-weight: 700">מסלול אחר</a></div>'
        f'<span style="position: absolute; bottom: 4px; left: 0; right: 0; text-align: center; font-size: 10.5px; color: {C["muted"]}">זמנים לדוגמה</span></div>')
out['G4-PhotoFinish.dc.html'] = page('צילום סיום', body, css='.flash{animation:fl 1.2s ease-out forwards}@keyframes fl{from{opacity:.95}to{opacity:0}}')

# =================================================================== G5: tournament night at the hotel (one phone, pass it on)
order = [(5, '1:41.0', True), (1, '1:39.4', True), (3, '1:52.8', True), (2, None, False), (0, None, False), (4, None, False)]
tb = ''
done = sorted([o for o in order if o[2]], key=lambda o: o[1])
for rank, (i, t, _) in enumerate(done, 1):
    tb += f'<div style="display: flex; align-items: center; gap: 10px; min-height: 42px; border-bottom: 1px solid #2A3B55"><b style="{DISP} font-size: 26px; width: 18px; color: {C["accent"] if rank==1 else "#EAF0F7"}">{rank}</b><span style="width: 6px; height: 28px; background: {JACKET[i]}"></span><b style="font-size: 15px; flex: 1">{SHORT[i]}</b><span style="{DISP} font-size: 24px" dir="ltr">{t}</span></div>'
for i, t, dn in order:
    if not dn and i != 2: tb += f'<div style="display: flex; align-items: center; gap: 10px; min-height: 42px; border-bottom: 1px solid #2A3B55; opacity: .5"><b style="{DISP} font-size: 26px; width: 18px">·</b><span style="width: 6px; height: 28px; background: {JACKET[i]}"></span><b style="font-size: 15px; flex: 1">{SHORT[i]}</b><span style="font-size: 12px">מחכה</span></div>'
body = (f'<div class="anim" style="{ROOT} background: #0D1522; color: #EAF0F7; display: flex; flex-direction: column">'
        f'<div style="padding: 16px 20px 8px"><span style="font-size: 12.5px; color: {C["nmuted"]}">ערב ב-New Gudauri · ג׳ 12.1</span><h1 style="margin: 0; {DISP} font-size: 48px; line-height: .95">טורניר <span dir="ltr">Kudebi 1</span></h1><span style="font-size: 13px; color: {C["nmuted"]}">טלפון אחד, שישה חבר׳ה, ירידה אחת לכל אחד</span></div>'
        f'<div class="pass" style="margin: 8px 16px; padding: 16px; background: {JACKET[2]}; color: {C["ink"]}; display: flex; align-items: center; gap: 14px">'
        f'<svg width="70" height="84" viewBox="-35 -76 70 84" aria-hidden="true">{skier(JACKET[2], PANTS[2], scale=1.05)}</svg>'
        f'<div style="display: flex; flex-direction: column; gap: 2px"><span style="font-size: 13px; font-weight: 700">מעבירים את הטלפון ל</span><b style="{DISP} font-size: 48px; line-height: .9">שרוליק</b><span style="font-size: 12.5px">צריך 1:39.3 כדי לעקוף את דובי</span></div></div>'
        f'<div style="padding: 6px 20px 0">{tb}</div>'
        f'<div style="margin-top: auto; padding: 12px 16px 18px; display: flex; flex-direction: column; gap: 8px"><a href="G2-Descent.dc.html" style="height: 58px; background: {C["accent"]}; color: {C["ink"]}; text-decoration: none; display: flex; align-items: center; justify-content: center; font-size: 18px; font-weight: 700">שרוליק, לחץ כשאתה מוכן</a>'
        f'<span style="font-size: 11.5px; color: {C["nmuted"]}; text-align: center">בסוף הערב: כרטיס ניצחון לקבוצה. זמנים לדוגמה</span></div></div>')
out['G5-HotelTournament.dc.html'] = page('טורניר בערב', body, css='.pass{animation:pass 1.4s cubic-bezier(.3,1.5,.5,1) both}@keyframes pass{from{transform:translateX(120%) rotate(8deg)}}')

# =================================================================== G6: the last lift (sunset, a clock that runs out)
road6, proj6 = corridor(1500, HZ=HZ, stripe=('#FBE6DE', '#EBCBC6'), edge='#8C6A86', shoulder='#B98A9A')
me6 = f'<g transform="translate(190,748)">{skier(JACKET[0], PANTS[0], lean=-12, scale=1.9, crouch=.4)}</g>'
body = (f'<div class="anim" style="{ROOT} background: linear-gradient(#3A4677, #F09A6A {HZ}px)">{backdrop(PANO["sunset"], HZ=HZ, dy=-40)}{ground(HZ, "#A77E93", "#C9A1A8")}'
        f'<svg width="390" height="844" viewBox="0 0 390 844" aria-hidden="true" style="position: absolute; left: 0; top: 0">{road6}{me6}</svg>{spray(190, 748)}'
        f'<div style="position: absolute; top: 12px; left: 12px; right: 12px; display: flex; justify-content: space-between; align-items: flex-start">'
        f'<div style="padding: 8px 12px; background: rgba(13,21,34,.85); color: #fff; display: flex; flex-direction: column"><span style="font-size: 11.5px; color: {C["accent"]}; font-weight: 700">הרכבל האחרון · <span dir="ltr">Shino</span></span><b class="tick" style="{DISP} font-size: 46px; line-height: .9" dir="ltr">16:29:12</b><span style="font-size: 11.5px">נסגר ב-16:30</span></div>'
        f'<div style="padding: 6px 10px; background: rgba(255,255,255,.85); color: {C["ink"]}; font-size: 12px; font-weight: 700">השמש שוקעת<br>מאחורי Sadzele</div></div>'
        f'<div style="position: absolute; bottom: 110px; left: 16px; right: 16px; height: 12px; background: rgba(13,21,34,.5)"><div class="bar" style="height: 100%; background: {C["accent"]}"></div></div>'
        f'<span style="position: absolute; bottom: 86px; left: 16px; right: 16px; text-align: center; font-size: 12.5px; font-weight: 700; color: {C["ink"]}">עוד 380 מ׳ לתחנה · צריך 48 קמ״ש לפחות</span></div>')
out['G6-LastLift.dc.html'] = page('הרכבל האחרון', body, css=CSS_SNOW + '.bar{animation:bar 6s linear infinite}@keyframes bar{from{width:100%}to{width:0}}')

# =================================================================== G7: the caravan (everyone together, to the meeting point)
road7, proj7 = corridor(1000, HZ=HZ)
train = ''
for k, i in enumerate([4, 2, 3, 1, 0]):
    x, y, w, f = proj7[min(6 + k * 3, len(proj7) - 1)]
    off = ((k % 2) * 2 - 1) * min(w * 0.3, 60); x = min(330, max(60, x))
    train += f'<g transform="translate({x+off:.1f},{y:.1f})">{skier(JACKET[i], PANTS[i], lean=(k%2*2-1)*6, scale=min(1.5, 22/f))}</g>'
    if i == 3:
        train += f'<g transform="translate({min(300, max(20, x+off+10)):.1f},{y-min(90, 60*22/f):.1f})"><rect x="-4" y="-22" width="76" height="22" fill="#fff" stroke="{C["ink"]}"></rect><text x="34" y="-6" text-anchor="middle" font-size="12" font-weight="700" fill="{C["ink"]}">חכו לי!</text></g>'
me7 = f'<g transform="translate(195,748)">{skier(JACKET[5], PANTS[5], lean=4, scale=1.9, crouch=.2)}</g>'
body = (f'<div class="anim" style="{ROOT} background: linear-gradient(#6FA6DC, #DCEAF4 {HZ}px)">{backdrop(PANO["gold"], HZ=HZ, dy=-40)}{ground(HZ)}'
        f'<svg width="390" height="844" viewBox="0 0 390 844" aria-hidden="true" style="position: absolute; left: 0; top: 0">{road7}{train}{me7}</svg>'
        f'<div style="position: absolute; top: 12px; left: 12px; right: 12px; padding: 10px 12px; background: rgba(255,255,255,.92); display: flex; flex-direction: column; gap: 6px">'
        f'<div style="display: flex; justify-content: space-between; align-items: baseline"><b style="{DISP} font-size: 30px; line-height: 1">השיירה</b><span style="font-size: 12.5px; color: {C["muted"]}">אל נקודת המפגש: <b dir="ltr">Goodaura</b> 12:30</span></div>'
        f'<div style="display: flex; gap: 6px">' + ''.join(f'<span style="flex: 1; display: flex; flex-direction: column; align-items: center; gap: 2px; font-size: 11px; font-weight: 700"><span style="width: 100%; height: 5px; background: {JACKET[i]}; opacity: {0.35 if i==3 else 1}"></span>{SHORT[i]}</span>' for i in range(6)) + '</div>'
        f'<span style="font-size: 12px; color: {C["red"]}; font-weight: 700">מוישי נתקע בשלג עמוק · השיירה מחכה לו 0:04</span></div>'
        f'<div style="position: absolute; bottom: 20px; left: 16px; right: 16px; padding: 8px 12px; background: rgba(19,35,58,.85); color: #fff; font-size: 12.5px">אתה מוביל. ניקוד על כמה שהקבוצה נשארת יחד, לא על מהירות. מגיעים יחד לנקודת המפגש מהאתר.</div></div>')
out['G7-Caravan.dc.html'] = page('השיירה', body)

dst = root / 'design/canvas/project'
for n, s in out.items(): (dst / n).write_text(s, encoding='utf-8')
print({n: len(s) for n, s in out.items()}, round(TOTAL))
