#!/usr/bin/env python3
"""Day and night designs: R7 (sun and moon swap on a toggle) and R8 (sky clock by the real hour).

The skyline is the real horizon as seen from the village (Zuma lower station), computed from the
site's terrain model. The moon is drawn as a full disc. Run from the repo root:
python3 design/round2/daynight.py  (needs numpy)"""
import json, math, base64, datetime, pathlib
import numpy as np
root = pathlib.Path(__file__).resolve().parent.parent.parent
T = json.load(open(root / 'site/data/terrain.json', encoding='utf-8'))
S = json.load(open(root / 'design/round2/sun-stats.json', encoding='utf-8'))
d = T['dem']
H = np.frombuffer(base64.b64decode(d['b64']), dtype='<i2').reshape(d['ny'], d['nx']).astype(float)
CELL = (d['x1'] - d['x0']) / (d['nx'] - 1); CELLY = (d['y1'] - d['y0']) / (d['ny'] - 1)
def elev(x, y):
    c = min(max((x - d['x0']) / CELL, 0), d['nx'] - 1.001); r = min(max((y - d['y0']) / CELLY, 0), d['ny'] - 1.001)
    c0, r0 = int(c), int(r); fc, fr = c - c0, r - r0
    return H[r0, c0] * (1 - fc) * (1 - fr) + H[r0, c0 + 1] * fc * (1 - fr) + H[r0 + 1, c0] * (1 - fc) * fr + H[r0 + 1, c0 + 1] * fc * fr

VX, VY = -452.0, 4441.0                      # Zuma lower station, the village side
E0 = elev(VX, VY) + 2
AZ = 56                                      # degrees each side of north
prof = []
for i in range(0, 118):
    a = math.radians(-AZ + i * (2 * AZ) / 117)
    best = 0.0
    for dist in range(150, 9000, 40):
        x = VX + math.sin(a) * dist; y = VY - math.cos(a) * dist
        best = max(best, math.degrees(math.atan2(elev(x, y) - E0, dist)))
    prof.append(best)
top = max(prof); print('max horizon angle', round(top, 1))
W, HH = 390, 236; SCALE = HH * 0.92 / max(top, 14)
pts = [(round(i * W / 117, 1), round(HH - p * SCALE, 1)) for i, p in enumerate(prof)]
ridge = 'M0,' + str(HH) + ' L' + ' L'.join(f'{x},{y}' for x, y in pts) + f' L{W},{HH} Z'
# a second, lower, nearer hill line so the scene has depth
near = [(round(i * W / 117, 1), round(HH - (0.42 * p + 4 + 5 * math.sin(i / 6)) * SCALE * 0.55, 1)) for i, p in enumerate(prof)]
near_d = 'M0,' + str(HH) + ' L' + ' L'.join(f'{x},{y}' for x, y in near) + f' L{W},{HH} Z'

# moon age: synodic month from a known new moon
ref = datetime.datetime(2000, 1, 6, 18, 14); day = datetime.datetime(2027, 1, 12, 18, 0)
age = ((day - ref).total_seconds() / 86400) % 29.530588853
illum = (1 - math.cos(2 * math.pi * age / 29.530588853)) / 2
print('moon age days', round(age, 1), 'illuminated', round(illum * 100))

rise = S['sunrise']; sset = S['sunset']
def hm(s): h, m = s.split(':'); return int(h) + int(m) / 60
RISE, SET = hm(rise), hm(sset)

FONTS = '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Karantina:wght@700&amp;family=IBM+Plex+Sans+Hebrew:wght@400;500;600;700&amp;display=swap">'
DISPLAY = "font-family: Karantina, 'Arial Narrow', sans-serif; font-weight: 700;"
STARS = [(38, 30), (92, 64), (140, 22), (200, 48), (262, 26), (318, 70), (352, 34), (64, 110), (176, 96), (300, 116), (240, 84), (120, 140), (30, 168), (346, 150)]
stars = ''.join(f'<span style="position: absolute; left: {x}px; top: {y}px; width: {2 + (i % 2)}px; height: {2 + (i % 2)}px; border-radius: 2px; background: #EAF0F7; opacity: {{{{ starOp }}}}" class="anim"></span>' for i, (x, y) in enumerate(STARS))
SUN_SVG = '<svg width="30" height="30" viewBox="0 0 30 30" aria-hidden="true"><circle cx="15" cy="15" r="14" fill="#F4B942" fill-opacity="0.28"></circle><circle cx="15" cy="15" r="9.5" fill="#F4B942" stroke="#13233A" stroke-width="1.5"></circle></svg>'
MOON_SVG = '<svg width="34" height="34" viewBox="0 0 34 34" aria-hidden="true"><circle cx="17" cy="17" r="16" fill="#EAF0F7" fill-opacity="0.22"></circle><circle cx="17" cy="17" r="12" fill="#EAF0F7" stroke="#A3B3C8" stroke-width="1"></circle><circle cx="13" cy="13" r="2.6" fill="#C9D4E2"></circle><circle cx="21.5" cy="19.5" r="3.4" fill="#C9D4E2"></circle><circle cx="14.5" cy="22" r="1.7" fill="#C9D4E2"></circle></svg>'
ICON_SUN = '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><circle cx="12" cy="12" r="4.2"></circle><path d="M12 2.5v2.4M12 19.1v2.4M2.5 12h2.4M19.1 12h2.4M5.3 5.3l1.7 1.7M17 17l1.7 1.7M5.3 18.7L7 17M17 7l1.7-1.7"></path></svg>'
ICON_MOON = '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 14.5A8.5 8.5 0 1 1 9.5 4a6.8 6.8 0 0 0 10.5 10.5z"></path></svg>'

def scene(bodies, controls, extra_top=''):
    return f'''<div class="anim" style="width: 390px; height: 844px; box-sizing: border-box; overflow: hidden; position: relative; background: {{{{ bg }}}}; color: {{{{ ink }}}}; font-family: 'IBM Plex Sans Hebrew', 'Segoe UI', system-ui, sans-serif; direction: rtl; display: flex; flex-direction: column; transition: background-color 0.9s, color 0.9s">
<div class="anim" style="display: flex; align-items: center; justify-content: space-between; padding: 8px 16px 8px 8px; background: {{{{ paper }}}}; border-bottom: 1px solid {{{{ rule }}}}; transition: background-color 0.9s, border-color 0.9s">
<span style="{DISPLAY} font-size: 30px; line-height: 1">גודאורי 2027</span>
{extra_top}
</div>
<div class="anim" style="position: relative; height: 330px; flex: none; overflow: hidden; background: {{{{ sky }}}}; transition: background-color 0.9s">
{stars}
{bodies}
<svg width="390" height="236" viewBox="0 0 390 236" aria-hidden="true" style="position: absolute; left: 0; bottom: 0; display: block"><path d="{ridge}" fill="{{{{ ridgeFar }}}}" class="anim" style="transition: fill 0.9s"></path><path d="{near_d}" fill="{{{{ ridgeNear }}}}" class="anim" style="transition: fill 0.9s"></path></svg>
<div style="position: absolute; top: 12px; right: 16px; left: 16px; display: flex; justify-content: space-between; align-items: flex-start"><span style="font-size: 13px; font-weight: 600; color: {{{{ ink }}}}" class="anim">{{{{ phaseLabel }}}}</span><span style="font-size: 12px; color: {{{{ muted }}}}">קו הרכס: מחושב מהכפר</span></div>
</div>
<div style="padding: 16px 20px 0; display: flex; flex-direction: column; gap: 10px">
<div style="display: flex; align-items: baseline; justify-content: space-between"><h1 style="margin: 0; {DISPLAY} font-size: 44px; line-height: 1">גודאורי, גאורגיה</h1><span style="font-size: 13px; color: {{{{ muted }}}}">ינואר 2027</span></div>
<div class="anim" style="display: flex; background: {{{{ paper }}}}; border: 1px solid {{{{ rule }}}}; transform: rotate(-2deg); transition: background-color 0.9s, border-color 0.9s">
<div style="flex: 1; padding: 10px 14px; display: flex; flex-direction: column; gap: 4px"><span style="font-size: 12px; font-weight: 600; color: {{{{ muted }}}}">כרטיס עלייה למטוס</span><span style="{DISPLAY} font-size: 30px; line-height: 1">תל אביב ← טביליסי</span><span style="font-size: 12.5px; color: {{{{ muted }}}}">10.1.2027 · 6H 897 · 16:00</span></div>
<div style="width: 92px; background: {{{{ accent }}}}; color: {{{{ onAccent }}}}; display: flex; flex-direction: column; align-items: center; justify-content: center; border-inline-start: 2px dashed {{{{ onAccent }}}}"><span style="{DISPLAY} font-size: 52px; line-height: 0.85">102</span><span style="font-size: 12px; font-weight: 600">ימים לטיסה</span></div>
</div>
<div class="anim" style="display: inline-flex; align-self: flex-start; align-items: center; margin-top: 8px; padding: 8px 20px 8px 34px; background: {{{{ board }}}}; color: {{{{ onBoard }}}}; {DISPLAY} font-size: 30px; line-height: 1; clip-path: polygon(0 50%, 18px 0, 100% 0, 100% 100%, 18px 100%); transition: background-color 0.9s, color 0.9s">מפת מסלולים</div>
</div>
<div style="margin-top: auto; padding: 0 16px 18px">{controls}</div>
</div>'''

COMMON_STYLE = '''<style>
body{margin:0;background:#EEF2F5}
@media (prefers-reduced-motion: reduce){ .anim{ transition: none !important } }
</style>'''

def page(title, body, script):
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
{COMMON_STYLE}
</helmet>
{body}
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{{"$preview":{{"width":390,"height":844}}}}'>
{script}
</script>
</body>
</html>
'''

THEMES_JS = '''const DAY = { bg: '#EEF2F5', paper: '#FFFFFF', ink: '#13233A', muted: '#4B5A6F', rule: '#CBD5DF', sky: '#DCE6EF', ridgeFar: '#B4C3D2', ridgeNear: '#EEF2F5', accent: '#F4B942', onAccent: '#13233A', board: '#1F5FC4', onBoard: '#FFFFFF' };
const NIGHT = { bg: '#0D1522', paper: '#16223A', ink: '#EAF0F7', muted: '#A3B3C8', rule: '#2A3B55', sky: '#0A1220', ridgeFar: '#22345A', ridgeNear: '#0D1522', accent: '#F4B942', onAccent: '#13233A', board: '#5B9BFF', onBoard: '#0D1522' };'''

# ---------------------------------------------------------------- R7: toggle with swapping sun and moon
bodies7 = f'''<div class="anim" style="position: absolute; left: 195px; top: 330px; width: 0; height: 0; transform: rotate({{{{ rot }}}}deg); transition: transform 1.3s cubic-bezier(0.55, 0, 0.35, 1)">
<div style="position: absolute; left: -15px; top: -{{{{ radius }}}}px; width: 30px; height: 30px">{SUN_SVG}</div>
<div style="position: absolute; left: -17px; top: {{{{ radiusMoon }}}}px; width: 34px; height: 34px; transform: rotate({{{{ counter }}}}deg)" class="anim">{MOON_SVG}</div>
</div>'''
toggle7 = f'''<button type="button" aria-label="{{{{ toggleLabel }}}}" onClick="{{{{ flip }}}}" class="anim" style="position: relative; width: 48px; height: 48px; border: 1.5px solid {{{{ ink }}}}; background: {{{{ paper }}}}; color: {{{{ ink }}}}; display: flex; align-items: center; justify-content: center; transition: background-color 0.9s, color 0.9s, border-color 0.9s">
<span class="anim" style="position: absolute; display: flex; opacity: {{{{ sunIconOp }}}}; transform: rotate({{{{ sunIconRot }}}}deg) scale({{{{ sunIconScale }}}}); transition: opacity 0.5s, transform 0.7s">{ICON_SUN}</span>
<span class="anim" style="position: absolute; display: flex; opacity: {{{{ moonIconOp }}}}; transform: rotate({{{{ moonIconRot }}}}deg) scale({{{{ moonIconScale }}}}); transition: opacity 0.5s, transform 0.7s">{ICON_MOON}</span></button>'''
controls7 = '''<div style="display: flex; flex-direction: column; gap: 8px">
<span style="font-size: 12.5px; font-weight: 700; color: {{ muted }}">מצב תצוגה</span>
<div role="group" aria-label="מצב תצוגה" style="display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); border: 1.5px solid {{ ink }}">
<sc-for list="{{ modes }}" as="m" hint-placeholder-count="3"><button type="button" aria-pressed="{{ m.on }}" onClick="{{ m.pick }}" class="anim" style="height: 48px; border: 0; background: {{ m.bg }}; color: {{ m.fg }}; font: inherit; font-size: 15px; font-weight: 700; transition: background-color 0.4s, color 0.4s">{{ m.label }}</button></sc-for>
</div>
<span style="font-size: 12.5px; color: {{ muted }}; line-height: 1.35">{{ caption }}</span>
</div>'''
script7 = f'''{THEMES_JS}
class Component extends DCLogic {{
  state = {{ mode: 'auto', sticky: 'night' }};
  renderVals() {{
    const auto = this.state.mode === 'auto';
    const eff = auto ? 'night' : this.state.mode;   // in the demo, "auto" reads a device time of 18:12, after sunset
    const t = eff === 'day' ? DAY : NIGHT;
    const isDay = eff === 'day';
    const labels = {{ auto: 'אוטומטי', day: 'יום', night: 'לילה' }};
    const modes = ['auto', 'day', 'night'].map((k) => ({{ label: labels[k], on: k === this.state.mode, bg: k === this.state.mode ? t.ink : t.paper, fg: k === this.state.mode ? t.paper : t.ink, pick: () => this.setState({{ mode: k }}) }}));
    return {{ ...t, rot: isDay ? -28 : 152, counter: isDay ? 28 : -152, radius: 230, radiusMoon: 200,
      starOp: isDay ? 0 : 0.9, phaseLabel: isDay ? 'שמש · 12.1.2027' : 'ירח',
      toggleLabel: isDay ? 'מעבר למצב לילה' : 'מעבר למצב יום',
      flip: () => this.setState({{ mode: isDay ? 'night' : 'day' }}),
      sunIconOp: isDay ? 1 : 0, sunIconRot: isDay ? 0 : 90, sunIconScale: isDay ? 1 : 0.4,
      moonIconOp: isDay ? 0 : 1, moonIconRot: isDay ? -90 : 0, moonIconScale: isDay ? 0.4 : 1,
      modes, caption: auto ? 'לפי שעת המכשיר. עכשיו 18:12, אחרי השקיעה ב-{sset}, ולכן לילה.' : (isDay ? 'תמיד בהיר, גם בלילה.' : 'תמיד כהה, גם ביום.') }};
  }}
}}'''
R7 = page('יום ולילה', scene(bodies7, controls7, toggle7), script7)

# ---------------------------------------------------------------- R8: sky clock by the real hour in Gudauri
bodies8 = f'''<div class="anim" style="position: absolute; left: {{{{ sunX }}}}px; top: {{{{ sunY }}}}px; width: 30px; height: 30px; opacity: {{{{ sunOp }}}}">{SUN_SVG}</div>
<div class="anim" style="position: absolute; left: {{{{ moonX }}}}px; top: {{{{ moonY }}}}px; width: 34px; height: 34px; opacity: {{{{ moonOp }}}}">{MOON_SVG}</div>'''
controls8 = '''<div style="display: flex; flex-direction: column; gap: 6px">
<div style="display: flex; align-items: baseline; justify-content: space-between"><label for="hour" style="font-size: 12.5px; font-weight: 700; color: {{ muted }}">השעה בגודאורי</label><span style="font-family: Karantina, 'Arial Narrow', sans-serif; font-weight: 700; font-size: 34px; line-height: 1" dir="ltr">{{ clock }}</span></div>
<input id="hour" type="range" min="0" max="23.75" step="0.25" value="{{ hour }}" onInput="{{ onHour }}" style="width: 100%; height: 44px; accent-color: {{ board }}">
<div style="display: flex; justify-content: space-between; font-size: 12.5px; color: {{ muted }}"><span>זריחה <b dir="ltr">RISE</b></span><span>שקיעה <b dir="ltr">SET</b></span></div>
<span style="font-size: 12.5px; color: {{ muted }}; line-height: 1.35">השמש והירח עוברים בקשת לפי הזריחה והשקיעה של 12.1.2027. הירח עולה אחרי השקיעה, ומיקומו בשמיים משוער. המצב הכהה נדלק עם השקיעה.</span>
</div>'''.replace('RISE', rise).replace('SET', sset)
script8 = f'''{THEMES_JS}
const RISE = {RISE}, SET = {SET};
const lerp = (a, b, t) => Math.round(a + (b - a) * t);
const hex = (c) => [1, 3, 5].map((i) => parseInt(c.slice(i, i + 2), 16));
const mix = (a, b, t) => {{ const x = hex(a), y = hex(b); return '#' + [0, 1, 2].map((i) => lerp(x[i], y[i], t).toString(16).padStart(2, '0')).join(''); }};
class Component extends DCLogic {{
  state = {{ hour: 8.25 }};
  renderVals() {{
    const h = this.state.hour;
    // 0 at night, 1 in full day, with about 45 minutes of twilight each side
    const tw = 0.75;
    const dayness = Math.max(0, Math.min(1, Math.min(h - RISE + tw / 2, SET - h + tw / 2) / tw));
    const golden = 1 - Math.abs(dayness - 0.5) * 2;   // warm sky only at the edges
    const skyBase = mix(NIGHT.sky, DAY.sky, dayness);
    const sky = mix(skyBase, '#F2C9A0', golden * 0.55);
    const theme = dayness > 0.5 ? DAY : NIGHT;
    const P = (k) => mix(NIGHT[k], DAY[k], dayness);
    const arc = (frac) => ({{ x: 195 + 205 * Math.cos(Math.PI * (1 - frac)) - 15, y: 330 - 232 * Math.sin(Math.PI * frac) - 15 }});
    const dayFrac = (h - RISE) / (SET - RISE);
    const nightLen = 24 - (SET - RISE);
    const nh = h >= SET ? h - SET : h + 24 - SET;
    const nightFrac = nh / nightLen;
    const s = arc(Math.max(0, Math.min(1, dayFrac))), m = arc(Math.max(0, Math.min(1, nightFrac)));
    const inDay = dayFrac > 0 && dayFrac < 1;
    const clock = String(Math.floor(h)).padStart(2, '0') + ':' + String(Math.round((h % 1) * 60)).padStart(2, '0');
    return {{ bg: P('bg'), paper: P('paper'), ink: theme.ink, muted: theme.muted, rule: P('rule'), sky, ridgeFar: P('ridgeFar'), ridgeNear: P('ridgeNear'),
      accent: NIGHT.accent, onAccent: NIGHT.onAccent, board: theme.board, onBoard: theme.onBoard,
      starOp: (1 - dayness) * 0.9, phaseLabel: dayness > 0.5 ? 'יום' : (golden > 0.6 ? 'דמדומים' : 'לילה'),
      sunX: s.x, sunY: s.y, sunOp: inDay ? 1 : 0, moonX: m.x, moonY: m.y, moonOp: inDay ? 0 : 1,
      hour: h, clock, onHour: (e) => this.setState({{ hour: parseFloat(e.target.value) }}) }};
  }}
}}'''
R8 = page('שמיים לפי השעה', scene(bodies8, controls8), script8)

out = root / 'design/canvas/project'
(out / 'R7-DayNight.dc.html').write_text(R7, encoding='utf-8')
(out / 'R8-SkyClock.dc.html').write_text(R8, encoding='utf-8')
print('written', len(R7), len(R8))
