#!/usr/bin/env python3
"""Generates the six round-2 design screens (.dc.html) from real site data.

Inputs: site/data/runs-and-lifts.json, design/round2/sun-stats.json, design/round2/first-sun.json
Output: design/canvas/project/R1..R6-*.dc.html  and  design/round2/canvas-additions.json
Run from the repo root:  python3 design/round2/build.py"""
import json, pathlib
root = pathlib.Path(__file__).resolve().parent.parent.parent
D = json.load(open(root / 'site/data/runs-and-lifts.json', encoding='utf-8'))
S = json.load(open(root / 'design/round2/sun-stats.json', encoding='utf-8'))
FIRST = json.load(open(root / 'design/round2/first-sun.json', encoding='utf-8'))
TRIP = json.load(open(root / 'site/data/trip.json', encoding='utf-8'))
ASSET = {  # uploaded to the design canvas
    'sun0900': '/_blob/918c78a6d85918a5dcbb7d31388ab657',
    'sun1200': '/_blob/27f863a8b3c3734d1b91b58cf1ba14d5',
    'sun1630': '/_blob/aa98fe0850d854afdd550f90185650cd',
    'base': '/_blob/41e5cfc0f951dfc1e45ae077491cfe58',
}
COL = {'green': '#1B8A4C', 'blue': '#1F5FC4', 'red': '#D1342B', 'black': '#13233A'}
HEB = {'green': 'ירוק', 'blue': 'כחול', 'red': 'אדום', 'black': 'שחור'}
P = {p['key']: p for p in D['pistes'] if p.get('named')}
ST = S['pistes']
FONTS = '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Karantina:wght@700&amp;family=IBM+Plex+Sans+Hebrew:wght@400;500;600;700&amp;family=Noto+Sans+Georgian:wght@500;700&amp;display=swap">'
ROOT = "width: 390px; height: 844px; box-sizing: border-box; overflow: hidden; position: relative; background: #EEF2F5; color: #13233A; font-family: 'IBM Plex Sans Hebrew', 'Segoe UI', system-ui, sans-serif; direction: rtl;"
DISPLAY = "font-family: Karantina, 'Arial Narrow', sans-serif; font-weight: 700;"
ICON_BACK = '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 12h14"></path><path d="M13 6l6 6-6 6"></path></svg>'

def page(title, body, script, props='{"$preview":{"width":390,"height":844}}'):
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
body{{margin:0;background:#EEF2F5}}
a{{color:#1F5FC4}}
</style>
</helmet>
{body}
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{props}'>
{script}
</script>
</body>
</html>
'''

def sign(text, color, size=30):
    return (f'<div style="display: inline-flex; align-items: center; padding: 5px 14px 5px 26px; background: {color}; color: #FFFFFF; {DISPLAY} font-size: {size}px; line-height: 1; clip-path: polygon(0 50%, 14px 0, 100% 0, 100% 100%, 14px 100%)"><span dir="ltr">{text}</span></div>')

def fmtm(m): return f'{m/1000:.2f} ק״מ' if m >= 1000 else f'{int(m)} מ׳'

# ---------------------------------------------------------------- R1 sun map
r1_data = [{'k': k, 'c': COL[P[k]['color']], 'd': round(v['drop']), 'a': round(v['sun0900'], 2), 'b': round(v['sun1200'], 2), 'e': round(v['sun1630'], 2)}
           for k, v in ST.items() if P[k].get('kind') != 'ski-way']
r1_first = {k: FIRST[k] for k in ('Kudebi 1', 'Sadzele 1', 'Firni 1', 'Kobi', 'Zuma')}
body = f'''<div style="{ROOT}">
<img src="{{{{ mapSrc }}}}" alt="מפת הר עם צל ושמש לפי השעה שנבחרה" style="position: absolute; top: 0; left: 0; width: 390px; height: 785px; object-fit: cover; display: block">
<div style="position: absolute; top: 10px; right: 10px; left: 10px; display: flex; align-items: center; justify-content: space-between; gap: 8px">
<div style="display: flex; flex-direction: column; gap: 6px; align-items: flex-start">{sign("מפת שמש", "#13233A", 34).replace('dir="ltr"', 'dir="rtl"')}
<span style="padding: 4px 10px; background: rgba(255,255,255,0.94); border: 1px solid #CBD5DF; font-size: 12.5px; font-weight: 600">שלישי 12.1.2027 · זריחה {S["sunrise"]} · שקיעה {S["sunset"]}</span></div>
<a href="#" style="display: flex; align-items: center; gap: 6px; min-height: 44px; padding: 0 12px; background: rgba(255,255,255,0.94); border: 1px solid #CBD5DF; color: #13233A; font-size: 15px; font-weight: 600; text-decoration: none">{ICON_BACK}לכל המפה</a>
</div>
<div style="position: absolute; right: 10px; top: 106px; display: flex; flex-direction: column; gap: 4px; padding: 8px 10px; background: rgba(255,255,255,0.94); border: 1px solid #CBD5DF; font-size: 12px; font-weight: 600"><span style="display: flex; align-items: center; gap: 6px"><span style="width: 14px; height: 14px; background: #FFF4DC; border: 1px solid #B4C3D2"></span>שמש</span><span style="display: flex; align-items: center; gap: 6px"><span style="width: 14px; height: 14px; background: #44567A"></span>צל</span></div>
<div style="position: absolute; left: 0; right: 0; bottom: 0; height: 404px; box-sizing: border-box; background: #FFFFFF; border-top: 1px solid #CBD5DF; padding: 12px 16px 0; display: flex; flex-direction: column; gap: 8px">
<div role="group" aria-label="שעה ביום" style="display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px">
<sc-for list="{{{{ times }}}}" as="t" hint-placeholder-count="3"><button type="button" aria-pressed="{{{{ t.on }}}}" onClick="{{{{ t.pick }}}}" style="height: 44px; border: 1.5px solid #13233A; background: {{{{ t.bg }}}}; color: {{{{ t.fg }}}}; font: inherit; font-size: 16px; font-weight: 700"><span dir="ltr">{{{{ t.label }}}}</span></button></sc-for>
</div>
<h2 style="margin: 4px 0 0; {DISPLAY} font-size: 30px; line-height: 1">{{{{ headline }}}}</h2>
<p style="margin: 0; font-size: 13.5px; color: #4B5A6F; line-height: 1.4">{{{{ sub }}}}</p>
<div style="display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; flex-grow: 1; min-height: 0">
<div style="display: flex; flex-direction: column; min-width: 0"><span style="font-size: 12px; font-weight: 700; color: #4B5A6F; padding-bottom: 2px; border-bottom: 2px solid #F4B942">בשמש, מהתלולים</span>
<sc-for list="{{{{ sunny }}}}" as="r" hint-placeholder-count="4"><div style="display: flex; align-items: center; justify-content: space-between; gap: 6px; min-height: 34px; border-bottom: 1px solid #CBD5DF"><span style="display: flex; align-items: center; gap: 6px; min-width: 0"><span style="width: 10px; height: 10px; flex: none; background: {{{{ r.c }}}}"></span><span style="font-weight: 600; font-size: 14px" dir="ltr">{{{{ r.k }}}}</span></span><span style="font-size: 12.5px; color: #4B5A6F">{{{{ r.pct }}}}</span></div></sc-for></div>
<div style="display: flex; flex-direction: column; min-width: 0"><span style="font-size: 12px; font-weight: 700; color: #4B5A6F; padding-bottom: 2px; border-bottom: 2px solid #44567A">בצל</span>
<sc-if value="{{{{ hasShade }}}}" hint-placeholder-val="{{{{ true }}}}"><sc-for list="{{{{ shaded }}}}" as="r" hint-placeholder-count="4"><div style="display: flex; align-items: center; justify-content: space-between; gap: 6px; min-height: 34px; border-bottom: 1px solid #CBD5DF"><span style="display: flex; align-items: center; gap: 6px; min-width: 0"><span style="width: 10px; height: 10px; flex: none; background: {{{{ r.c }}}}"></span><span style="font-weight: 600; font-size: 14px" dir="ltr">{{{{ r.k }}}}</span></span><span style="font-size: 12.5px; color: #4B5A6F">{{{{ r.pct }}}}</span></div></sc-for></sc-if>
<sc-if value="{{{{ noShade }}}}" hint-placeholder-val="{{{{ false }}}}"><span style="padding-top: 10px; font-size: 13px; color: #4B5A6F">אין מסלול בצל בשעה הזו.</span></sc-if></div>
</div>
<p style="margin: 0 0 14px; font-size: 11.5px; color: #4B5A6F; line-height: 1.35">מחושב ממודל הגובה של האתר ל-12.1.2027, כולל צל של הרים אחרים וכיוון המדרון. בלי עננים.</p>
</div>
</div>'''
script = f'''const ASSETS = {{ '0900': '{ASSET["sun0900"]}', '1200': '{ASSET["sun1200"]}', '1630': '{ASSET["sun1630"]}' }};
const RUNS = {json.dumps(r1_data, ensure_ascii=False)};
const FIRST = {json.dumps(r1_first, ensure_ascii=False)};
class Component extends DCLogic {{
  state = {{ t: '0900' }};
  renderVals() {{
    const t = this.state.t, key = {{ '0900': 'a', '1200': 'b', '1630': 'e' }}[t];
    const label = {{ '0900': '09:00', '1200': '12:00', '1630': '16:30' }};
    const times = Object.keys(label).map((k) => ({{ label: label[k], on: k === t, bg: k === t ? '#13233A' : '#FFFFFF', fg: k === t ? '#FFFFFF' : '#13233A', pick: () => this.setState({{ t: k }}) }}));
    const pct = (v) => Math.round(v[key] * 100) + '% שמש';
    const sunny = RUNS.filter((r) => r[key] >= 0.9).sort((x, y) => y.d - x.d).slice(0, 4).map((r) => ({{ ...r, pct: 'ירידה ' + r.d + ' מ׳' }}));
    const shadedAll = RUNS.filter((r) => r[key] < 0.5).sort((x, y) => x[key] - y[key]);
    const shaded = shadedAll.slice(0, 4).map((r) => ({{ ...r, pct: pct(r) }}));
    const heads = {{
      '0900': 'בבוקר הכפר עוד בצל',
      '1200': 'בצהריים כל ההר בשמש',
      '1630': 'אחר הצהריים: כמעט הכל בשמש',
    }};
    const subs = {{
      '0900': 'Kudebi 1 ו-Sadzele 1 מגיעים לשמש רק ב-' + FIRST['Kudebi 1'] + ', Firni 1 ב-' + FIRST['Firni 1'] + ' ו-Kobi ב-' + FIRST['Kobi'] + '. Soliko 1 ו-Tatra 2 כבר בשמש.',
      '1200': 'שמש נמוכה של ינואר, כ-24 מעלות מעל האופק. הכל מואר, גם המסלולים שפונים צפונה קצת פחות.',
      '1630': 'רק Kobi כמעט כולו בצל. השקיעה ב-{S["sunset"]}.',
    }};
    return {{ mapSrc: ASSETS[t], times, headline: heads[t], sub: subs[t], sunny, shaded, hasShade: shaded.length > 0, noShade: shaded.length === 0 }};
  }}
}}'''
R1 = page('מפת שמש', body, script)

# ---------------------------------------------------------------- R2 morning card
def erow(name, h, temp, wind, snow):
    return f'<div style="display: grid; grid-template-columns: 1fr auto auto; gap: 12px; align-items: center; min-height: 46px; border-bottom: 1px solid #CBD5DF"><span style="display: flex; flex-direction: column"><span style="font-weight: 700" dir="auto">{name}</span><span style="font-size: 12px; color: #4B5A6F">{h} מ׳</span></span><span style="{DISPLAY} font-size: 28px; line-height: 1" dir="ltr">{temp}</span><span style="font-size: 12.5px; color: #4B5A6F; text-align: left">רוח {wind} קמ״ש<br>שלג {snow} ס״מ</span></div>'
s1 = ST['Soliko 1']; k1 = ST['Kudebi 1']
body = f'''<div style="{ROOT} display: flex; flex-direction: column">
<div style="background: #13233A; color: #FFFFFF; padding: 20px 20px 16px; display: flex; flex-direction: column; gap: 4px">
<span style="font-size: 13px; font-weight: 600; color: #F4B942">שלישי 12.1.2027 · יום 2 מתוך 4 ימי סקי</span>
<h1 style="margin: 0; {DISPLAY} font-size: 50px; line-height: 1">בוקר טוב, גודאורי</h1>
</div>
<div style="padding: 14px 16px 0; display: flex; flex-direction: column; gap: 12px; flex-grow: 1">
<section aria-label="שמש" style="background: #FFFFFF; border: 1px solid #CBD5DF; padding: 12px 14px">
<div style="display: flex; align-items: baseline; justify-content: space-between"><h2 style="margin: 0; {DISPLAY} font-size: 24px; line-height: 1">השמש</h2><span style="font-size: 11.5px; color: #4B5A6F">מחושב ממודל הגובה</span></div>
<div style="position: relative; height: 34px; margin-top: 10px">
<div style="position: absolute; top: 15px; right: 0; left: 0; height: 4px; background: #CBD5DF"></div>
<div style="position: absolute; top: 15px; right: 5%; width: 90%; height: 4px; background: #F4B942"></div>
<div style="position: absolute; top: 4px; right: 5%; width: 12px; height: 12px; border-radius: 6px; background: #F4B942; border: 2px solid #13233A; box-sizing: border-box"></div>
<div style="position: absolute; top: 4px; right: 95%; width: 12px; height: 12px; border-radius: 6px; background: #F4B942; border: 2px solid #13233A; box-sizing: border-box"></div>
</div>
<div style="display: flex; justify-content: space-between; font-size: 13px"><span>זריחה <b class="num">{S["sunrise"]}</b></span><span>שקיעה <b class="num">{S["sunset"]}</b></span></div>
<p style="margin: 8px 0 0; font-size: 13px; color: #4B5A6F; line-height: 1.4">הכפר וצד Zuma מקבלים שמש ב-{FIRST["Zuma"]}. Kudebi 1 ו-Sadzele 1 רק ב-{FIRST["Kudebi 1"]}.</p>
</section>
<section aria-label="מזג אוויר" style="background: #FFFFFF; border: 1px solid #CBD5DF; padding: 12px 14px 4px">
<div style="display: flex; align-items: baseline; justify-content: space-between"><h2 style="margin: 0; {DISPLAY} font-size: 24px; line-height: 1">מזג אוויר לפי גובה</h2><span style="font-size: 11.5px; color: #4B5A6F">נתוני דוגמה</span></div>
{erow("Sadzele", "3,237", "−12°", 45, 8)}{erow("Goodaura", "2,706", "−8°", 22, 6)}{erow("הכפר", "2,000", "−3°", 10, 4)}
</section>
<section aria-label="הצעה לבוקר" style="background: #FFF6DE; border-inline-start: 5px solid #F4B942; padding: 12px 14px; display: flex; flex-direction: column; gap: 6px">
<span style="font-weight: 700; font-size: 16px">להתחיל ב-<span dir="ltr">Soliko 1</span></span>
<span style="font-size: 13.5px; color: #13233A; line-height: 1.4">{fmtm(P["Soliko 1"]["len"])}, ירידה של {round(s1["drop"])} מ׳, {round(s1["sun0900"]*100)}% שמש כבר ב-9:00. אחרי {FIRST["Kudebi 1"]} עוברים ל-<span dir="ltr">Kudebi 1</span>, שאז מתחמם.</span>
<span style="font-size: 12px; color: #4B5A6F">הרכבל הראשון: [שעה, מדף המצב של MTA]</span>
</section>
</div>
<div style="padding: 12px 16px 18px"><button type="button" style="width: 100%; height: 56px; border: 0; background: #13233A; color: #FFFFFF; font: inherit; font-size: 17px; font-weight: 700; display: flex; align-items: center; justify-content: center; gap: 10px"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="18" cy="5" r="3"></circle><circle cx="6" cy="12" r="3"></circle><circle cx="18" cy="19" r="3"></circle><path d="M8.6 13.5l6.8 4M15.4 6.5l-6.8 4"></path></svg>שתפו לקבוצה בוואטסאפ</button></div>
</div>'''
R2 = page('כרטיס הבוקר', body, 'class Component extends DCLogic {\n  renderVals() { return {}; }\n}')

# ---------------------------------------------------------------- R3 day route
steps = [
    ('lift', 'Kudebi', 1258, 9, None),
    ('run', 'Kudebi 2', None, None, None),
    ('lift', 'Khada', 565, 9, None),
    ('run', 'Kikilo 1', None, None, None),
    ('lift', 'Kikilo', 1295, 9, 'הערכה'),
    ('run', 'Kikilo 2', None, None, None),
]
def runmin(m): return round(m / 333)
drop = sum(round(ST[k]['drop']) for t, k, *_ in steps if t == 'run')
ski = sum(runmin(P[k]['len']) for t, k, *_ in steps if t == 'run')
lifts_min = sum(s[3] for s in steps if s[0] == 'lift')
rows = ''
for i, (t, k, ln, dur, note) in enumerate(steps):
    if t == 'lift':
        rows += f'<div style="display: flex; align-items: center; gap: 12px; min-height: 44px"><span style="width: 44px; display: flex; justify-content: center; color: #4B5A6F"><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 19V5"></path><path d="M6 11l6-6 6 6"></path></svg></span><span style="display: flex; flex-direction: column"><span style="font-weight: 600; font-size: 14px">רכבל <span dir="ltr">{k}</span></span><span style="font-size: 12px; color: #4B5A6F">{fmtm(ln)} · כ-{dur} דק׳{" (הערכה)" if note else ""}</span></span></div>'
    else:
        p = P[k]; st = ST[k]
        rows += f'<div style="display: flex; align-items: center; gap: 12px; min-height: 58px"><span style="width: 44px; display: flex; justify-content: center; color: #4B5A6F"><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 5v14"></path><path d="M6 13l6 6 6-6"></path></svg></span><div style="flex-grow: 1; display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 8px 12px; background: #FFFFFF; border: 1px solid #CBD5DF; border-inline-start: 6px solid {COL[p["color"]]}"><span style="display: flex; flex-direction: column"><span style="font-weight: 700; font-size: 16px" dir="ltr">{k}</span><span style="font-size: 12px; color: #4B5A6F">{fmtm(p["len"])} · ירידה {round(st["drop"])} מ׳ · כ-{runmin(p["len"])} דק׳</span></span><span style="font-size: 12.5px; font-weight: 600; color: #13233A; white-space: nowrap">{round(st["sun0900"]*100)}% שמש ב-9:00</span></div></div>'
# sawtooth (elevation) chart from the real top/bottom of each piece
pts = [(0, 2669)]  # bottom of Kudebi lift
seq = [('Kudebi', 2983), ('Kudebi 2', round(ST['Kudebi 2']['bottom'])), ('Khada', 2806), ('Kikilo 1', round(ST['Kikilo 1']['bottom'])), ('Kikilo', 2764), ('Kikilo 2', round(ST['Kikilo 2']['bottom']))]
x = 0; poly = [(0, 2669)]
w = [9, 3, 9, 5, 9, 3]
for (n, e), wi in zip(seq, w):
    x += wi; poly.append((x, e))
lo, hi = 2300, 3050; W_, H_ = 350, 110
def sx(v): return 6 + v / x * (W_ - 12)
def sy(e): return 8 + (hi - e) / (hi - lo) * (H_ - 22)
line = ' '.join(f'{sx(a):.1f},{sy(b):.1f}' for a, b in poly)
chart = f'<svg width="{W_}" height="{H_}" viewBox="0 0 {W_} {H_}" role="img" aria-label="גובה לאורך המסלול: שלוש עליות ושלוש ירידות" style="display: block; direction: ltr"><polygon points="{sx(0):.1f},{H_-10} {line} {sx(x):.1f},{H_-10}" fill="#1F5FC4" fill-opacity="0.10"></polygon><polyline points="{line}" fill="none" stroke="#1F5FC4" stroke-width="2.4" stroke-linejoin="round"></polyline><text x="4" y="{sy(2983)-3:.1f}" font-size="11" font-weight="700" fill="#13233A">2,983 מ׳</text><text x="4" y="{H_-1}" font-size="11" fill="#4B5A6F">2,346 מ׳</text></svg>'
chips = ''.join(f'<span style="display: inline-flex; align-items: center; height: 44px; padding: 0 14px; border: 1.5px solid {"#13233A" if on else "#CBD5DF"}; background: {"#13233A" if on else "#FFFFFF"}; color: {"#FFFFFF" if on else "#13233A"}; font-size: 14px; font-weight: 600; white-space: nowrap">{t}</span>' for t, on in [('שמש בבוקר', True), ('הכי תלול', False), ('רגוע', False), ('אדומים', False)])
body = f'''<div style="{ROOT} display: flex; flex-direction: column">
<div style="display: flex; align-items: center; justify-content: space-between; padding: 10px 16px 0 12px"><h1 style="margin: 0; {DISPLAY} font-size: 40px; line-height: 1">מסלול היום</h1><a href="#" style="display: flex; align-items: center; gap: 6px; min-height: 44px; padding: 0 8px; font-size: 15px; font-weight: 600; text-decoration: none">{ICON_BACK}בית</a></div>
<div style="display: flex; gap: 8px; padding: 4px 16px 10px; overflow: hidden"><span style="display: none"></span>{chips}</div>
<div style="margin: 0 16px; background: #FFFFFF; border: 1px solid #CBD5DF; padding: 10px 12px 8px">
<div style="display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; margin-bottom: 8px"><div style="display: flex; flex-direction: column"><span style="font-size: 12px; color: #4B5A6F">ירידה</span><span style="{DISPLAY} font-size: 30px; line-height: 1">{drop:,} מ׳</span></div><div style="display: flex; flex-direction: column"><span style="font-size: 12px; color: #4B5A6F">זמן</span><span style="{DISPLAY} font-size: 30px; line-height: 1">כ-{lifts_min + ski} דק׳</span></div><div style="display: flex; flex-direction: column"><span style="font-size: 12px; color: #4B5A6F">עליות</span><span style="{DISPLAY} font-size: 30px; line-height: 1">3</span></div></div>
{chart}
</div>
<div style="padding: 10px 16px 0; display: flex; flex-direction: column; flex-grow: 1; min-height: 0">{rows}</div>
<div style="padding: 6px 16px 16px; display: flex; flex-direction: column; gap: 8px"><p style="margin: 0; font-size: 11.5px; color: #4B5A6F; line-height: 1.35">כל החיבורים לפי הנתונים של האתר. זמני הרכבלים מהנתונים, זמני הירידה מוערכים לפי 20 קמ״ש. בלי תורים.</p>
<div style="display: grid; grid-template-columns: 1fr 1fr; gap: 10px"><button type="button" style="height: 52px; border: 1.5px solid #13233A; background: #FFFFFF; color: #13233A; font: inherit; font-size: 16px; font-weight: 700">מסלול אחר</button><button type="button" style="height: 52px; border: 0; background: #13233A; color: #FFFFFF; font: inherit; font-size: 16px; font-weight: 700">להתחיל</button></div></div>
</div>'''
R3 = page('מסלול היום', body, 'class Component extends DCLogic {\n  renderVals() { return {}; }\n}')

# ---------------------------------------------------------------- R4 meeting point
X0, Y0, X1, Y1 = S['extent']; SCALE = 2 * 780 / (X1 - X0) / 2  # px per meter at 780 wide
import math
KX = 111320 * math.cos(math.radians(42.51))
def mp(lat, lon):
    x = (lon - 44.495) * KX; y = -(lat - 42.51) * 111320
    return ((x - X0) * 780 / (X1 - X0), (y - Y0) * 780 / (X1 - X0))
stations = []
for name, lname in [('Shino', 'Shino'), ('Soliko', 'Soliko'), ('Zuma', 'Zuma'), ('Goodaura', 'Goodaura'), ('Pirveli', 'Pirveli')]:
    l = next(l for l in D['lifts'] if l.get('name') == lname)
    px, py = mp(*l['g'][0]); stations.append({'k': name, 'x': round(px), 'y': round(py)})
OFF = 1110   # window offset in the 780-wide image, px
for s in stations: s['y'] -= OFF
body = f'''<div style="{ROOT} display: flex; flex-direction: column">
<div style="display: flex; align-items: center; justify-content: space-between; padding: 10px 16px 6px 12px"><h1 style="margin: 0; {DISPLAY} font-size: 40px; line-height: 1">נקודת מפגש</h1><a href="#" style="display: flex; align-items: center; gap: 6px; min-height: 44px; padding: 0 8px; font-size: 15px; font-weight: 600; text-decoration: none">{ICON_BACK}בית</a></div>
<div style="position: relative; height: 380px; flex: none; overflow: hidden; background: #DCE8F1">
<img src="{ASSET["sun1200"]}" alt="הכפר והתחנות התחתונות של הרכבלים" style="position: absolute; top: -{OFF}px; left: 0; width: 780px; height: 1569px; max-width: none; display: block">
<sc-for list="{{{{ pins }}}}" as="p" hint-placeholder-count="5"><div style="position: absolute; left: {{{{ p.x }}}}px; top: {{{{ p.y }}}}px; width: 0; height: 0"><span style="position: absolute; left: -9px; top: -9px; width: 18px; height: 18px; border-radius: 9px; background: {{{{ p.bg }}}}; border: 3px solid #FFFFFF; box-sizing: border-box; box-shadow: 0 1px 4px rgba(19,35,58,0.5)"></span><span style="position: absolute; {{{{ p.side }}}}: 14px; top: -11px; padding: 2px 7px; background: {{{{ p.lbg }}}}; color: {{{{ p.lfg }}}}; border: 1.5px solid #13233A; font-size: 12.5px; font-weight: 700; white-space: nowrap" dir="ltr">{{{{ p.k }}}}</span></div></sc-for>
</div>
<div style="flex-grow: 1; background: #FFFFFF; border-top: 1px solid #CBD5DF; padding: 12px 16px 0; display: flex; flex-direction: column; gap: 8px">
<span style="font-size: 12.5px; font-weight: 700; color: #4B5A6F">איפה</span>
<div style="display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px"><sc-for list="{{{{ pins }}}}" as="p" hint-placeholder-count="5"><button type="button" aria-pressed="{{{{ p.on }}}}" onClick="{{{{ p.pick }}}}" style="height: 44px; border: 1.5px solid #13233A; background: {{{{ p.lbg }}}}; color: {{{{ p.lfg }}}}; font: inherit; font-size: 14px; font-weight: 700"><span dir="ltr">{{{{ p.k }}}}</span></button></sc-for></div>
<span style="font-size: 12.5px; font-weight: 700; color: #4B5A6F">מתי</span>
<div style="display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px"><sc-for list="{{{{ times }}}}" as="t" hint-placeholder-count="4"><button type="button" aria-pressed="{{{{ t.on }}}}" onClick="{{{{ t.pick }}}}" style="height: 44px; border: 1.5px solid #13233A; background: {{{{ t.bg }}}}; color: {{{{ t.fg }}}}; font: inherit; font-size: 15px; font-weight: 700"><span dir="ltr">{{{{ t.k }}}}</span></button></sc-for></div>
<div style="margin-top: 4px; align-self: flex-start; max-width: 300px; padding: 10px 14px; background: #DCF8C6; color: #13233A; font-size: 14px; line-height: 1.45; border: 1px solid #B7E3A0"><span style="font-weight: 700">נפגשים ב-<span dir="ltr">{{{{ place }}}}</span> ב-<span dir="ltr">{{{{ time }}}}</span></span><br><span style="font-size: 12.5px; color: #4B5A6F">התחנה התחתונה. המפה בקישור: [קישור לאתר]</span></div>
</div>
<div style="padding: 10px 16px 18px"><button type="button" style="width: 100%; height: 56px; border: 0; background: #13233A; color: #FFFFFF; font: inherit; font-size: 17px; font-weight: 700">שתפו לקבוצה בוואטסאפ</button></div>
</div>'''
script = f'''const STATIONS = {json.dumps(stations)};
class Component extends DCLogic {{
  state = {{ place: 'Goodaura', time: '12:30' }};
  renderVals() {{
    const on = this.state.place;
    const pins = STATIONS.map((s) => ({{ ...s, on: s.k === on, bg: s.k === on ? '#F4B942' : '#FFFFFF', lbg: s.k === on ? '#13233A' : '#FFFFFF', lfg: s.k === on ? '#FFFFFF' : '#13233A', side: s.x > 250 ? 'right' : 'left', pick: () => this.setState({{ place: s.k }}) }}));
    const times = ['11:30', '12:00', '12:30', '13:00'].map((k) => ({{ k, on: k === this.state.time, bg: k === this.state.time ? '#13233A' : '#FFFFFF', fg: k === this.state.time ? '#FFFFFF' : '#13233A', pick: () => this.setState({{ time: k }}) }}));
    return {{ pins, times, place: this.state.place, time: this.state.time }};
  }}
}}'''
R4 = page('נקודת מפגש', body, script)

# ---------------------------------------------------------------- R5 help card
def phrase(he, ru, ka):
    return f'<div style="padding: 12px 0; border-bottom: 1px solid #2A3B55; display: flex; flex-direction: column; gap: 2px"><span style="font-size: 13px; color: #A3B3C8">{he}</span><span style="font-size: 26px; font-weight: 700; line-height: 1.15" dir="ltr" lang="ru">{ru}</span><span style="font-size: 17px; color: #F4B942; font-weight: 500; font-family: \'Noto Sans Georgian\', sans-serif" dir="ltr" lang="ka">{ka}</span></div>'
body = f'''<div style="width: 390px; height: 844px; box-sizing: border-box; overflow: hidden; position: relative; background: #0D1522; color: #EAF0F7; font-family: 'IBM Plex Sans Hebrew', 'Segoe UI', system-ui, sans-serif; direction: rtl; padding: 20px 20px 0; display: flex; flex-direction: column">
<div style="display: flex; align-items: baseline; justify-content: space-between"><h1 style="margin: 0; {DISPLAY} font-size: 46px; line-height: 1">כרטיס עזרה</h1><span style="font-size: 12px; color: #A3B3C8">עובד גם בלי אינטרנט</span></div>
<div style="margin-top: 12px; display: grid; grid-template-columns: 1fr 1fr; gap: 10px">
<div style="background: #D1342B; color: #FFFFFF; padding: 12px 14px; display: flex; flex-direction: column"><span style="font-size: 12.5px; font-weight: 600">חירום בגאורגיה</span><span style="{DISPLAY} font-size: 52px; line-height: 1" dir="ltr">112</span></div>
<div style="background: #16223A; border: 1px solid #2A3B55; padding: 12px 14px; display: flex; flex-direction: column; gap: 2px"><span style="font-size: 12.5px; color: #A3B3C8">חילוץ במסלול</span><span style="font-size: 15px; font-weight: 700">[מספר מצילי המסלולים]</span></div>
</div>
<div style="margin-top: 10px; background: #16223A; border: 1px solid #2A3B55; padding: 12px 14px; display: grid; gap: 8px">
<div style="display: flex; justify-content: space-between; gap: 10px"><span style="color: #A3B3C8; font-size: 13px">המלון</span><span style="font-weight: 700; font-size: 15px">[שם המלון] · New Gudauri</span></div>
<div style="display: flex; justify-content: space-between; gap: 10px"><span style="color: #A3B3C8; font-size: 13px">ביטוח</span><span style="font-weight: 700; font-size: 15px">[חברה] · [מספר פוליסה]</span></div>
<div style="display: flex; justify-content: space-between; gap: 10px"><span style="color: #A3B3C8; font-size: 13px">איש קשר בארץ</span><span style="font-weight: 700; font-size: 15px">[שם וטלפון]</span></div>
</div>
<div style="margin-top: 6px; flex-grow: 1">
{phrase("עזרה!", "Помогите!", "მიშველეთ!")}
{phrase("אני צריך רופא", "Мне нужен врач", "მჭირდება ექიმი")}
{phrase("איפה הרכבל?", "Где подъёмник?", "სად არის ამწე?")}
</div>
<p style="margin: 0 0 16px; font-size: 11.5px; color: #A3B3C8; line-height: 1.35">המשפטים ברוסית ובגאורגית לאימות מול דובר שפה לפני הטיסה. הסוגריים המרובעים ימולאו בפרטים האמיתיים.</p>
</div>'''
R5 = page('כרטיס עזרה', body, 'class Component extends DCLogic {\n  renderVals() { return {}; }\n}')

# ---------------------------------------------------------------- R6 passport
groups = []
for c in ('green', 'blue', 'red', 'black'):
    ks = [k for k, p in P.items() if p['color'] == c]
    groups.append({'c': c, 'name': HEB[c], 'hex': COL[c], 'keys': ks})
meta = {k: {'d': round(ST[k]['drop'])} for k in P}
body = f'''<div style="{ROOT} display: flex; flex-direction: column">
<div style="padding: 14px 16px 6px; display: flex; align-items: baseline; justify-content: space-between"><h1 style="margin: 0; {DISPLAY} font-size: 40px; line-height: 1">דרכון המסלולים</h1><span style="font-size: 12px; color: #4B5A6F">נתוני דוגמה</span></div>
<div style="margin: 0 16px; display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; padding: 8px 12px; background: #FFFFFF; border: 1px solid #CBD5DF">
<div style="display: flex; flex-direction: column"><span style="font-size: 12px; color: #4B5A6F">של</span><span style="font-weight: 700; font-size: 15px">{TRIP["members"][5]}</span></div>
<div style="display: flex; flex-direction: column"><span style="font-size: 12px; color: #4B5A6F">מסלולים</span><span style="{DISPLAY} font-size: 30px; line-height: 1">{{{{ count }}}} / 27</span></div>
<div style="display: flex; flex-direction: column"><span style="font-size: 12px; color: #4B5A6F">ירידה מצטברת</span><span style="{DISPLAY} font-size: 30px; line-height: 1">{{{{ meters }}}} מ׳</span></div>
</div>
<div style="padding: 8px 16px 0; display: flex; flex-direction: column; gap: 6px; flex-grow: 1; min-height: 0">
<sc-for list="{{{{ groups }}}}" as="g" hint-placeholder-count="4">
<div style="display: flex; flex-direction: column; gap: 4px">
<div style="display: flex; align-items: baseline; justify-content: space-between; border-top: 6px solid {{{{ g.hex }}}}; padding-top: 3px"><span style="font-size: 13px; font-weight: 700">{{{{ g.name }}}}</span><span style="font-size: 12px; color: #4B5A6F">{{{{ g.done }}}} מתוך {{{{ g.total }}}}</span></div>
<div style="display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 6px">
<sc-for list="{{{{ g.tiles }}}}" as="t" hint-placeholder-count="4"><button type="button" aria-pressed="{{{{ t.done }}}}" onClick="{{{{ t.toggle }}}}" style="min-height: 44px; padding: 2px 4px; border: 1.5px solid {{{{ t.hex }}}}; background: {{{{ t.bg }}}}; color: {{{{ t.fg }}}}; font: inherit; font-size: 11.5px; font-weight: 700; line-height: 1.15; display: flex; align-items: center; justify-content: center; text-align: center"><span dir="ltr">{{{{ t.k }}}}</span></button></sc-for>
</div></div></sc-for>
</div>
<div style="padding: 8px 16px 14px"><p style="margin: 0; font-size: 11.5px; color: #4B5A6F; line-height: 1.35">לוחצים על מסלול כדי לסמן שירדתם בו. הירידה של כל מסלול מחושבת ממודל הגובה. הדרכון נשמר במכשיר.</p></div>
</div>'''
script = f'''const GROUPS = {json.dumps(groups, ensure_ascii=False)};
const META = {json.dumps(meta)};
class Component extends DCLogic {{
  state = {{ done: ['Pirveli', 'Zuma', 'Baby', 'Snow Park', 'Goodaura 1', 'Tatra 1', 'Soliko 1', 'Kudebi 1'] }};
  renderVals() {{
    const done = new Set(this.state.done);
    const toggle = (k) => () => {{ const s = new Set(this.state.done); s.has(k) ? s.delete(k) : s.add(k); this.setState({{ done: [...s] }}); }};
    const groups = GROUPS.map((g) => ({{ hex: g.hex, name: g.name, total: g.keys.length, done: g.keys.filter((k) => done.has(k)).length,
      tiles: g.keys.map((k) => ({{ k, done: done.has(k), hex: g.hex, bg: done.has(k) ? g.hex : '#FFFFFF', fg: done.has(k) ? '#FFFFFF' : '#13233A', toggle: toggle(k) }})) }}));
    const meters = [...done].reduce((a, k) => a + (META[k] ? META[k].d : 0), 0);
    return {{ groups, count: done.size, meters: meters.toLocaleString('en-US') }};
  }}
}}'''
R6 = page('דרכון המסלולים', body, script)

out = {'R1-SunMap.dc.html': R1, 'R2-MorningCard.dc.html': R2, 'R3-DayRoute.dc.html': R3, 'R4-MeetingPoint.dc.html': R4, 'R5-HelpCard.dc.html': R5, 'R6-Passport.dc.html': R6}
for n, s in out.items():
    (root / 'design/canvas/project' / n).write_text(s, encoding='utf-8')
print({n: len(s) for n, s in out.items()}, 'drop', drop, 'ski', ski, 'lift', lifts_min)
