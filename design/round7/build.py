#!/usr/bin/env python3
"""Round 7 design screens: the flight card on the home page, as a real boarding pass.

Five phone artboards for the canvas page "סבב 7: כרטיס עלייה למטוס":
BP0 is the card as it is on the site today (for comparison), BP1 to BP3 are three
directions, and BP4 is the recommended one at night. All flight data comes from
site/data/trip.json; there is no gate, seat, boarding time or booking number, and
the barcode and the square are drawings, not codes.
Run from the repo root: python3 design/round7/build.py"""
import json, pathlib
root = pathlib.Path(__file__).resolve().parent.parent.parent
TRIP = json.load(open(root / 'site/data/trip.json', encoding='utf-8'))
O, R = TRIP['outbound'], TRIP['return']
PANO = {'noon': '/_blob/d5ad1e6d2660c28123782ea318a7fd85', 'night': None}
C = {'snow': '#EEF2F5', 'paper': '#FFFFFF', 'paper2': '#F4F7FA', 'ink': '#13233A', 'muted': '#4B5A6F', 'rule': '#CBD5DF',
     'dash': '#8E9CAD', 'blue': '#1F5FC4', 'green': '#1B8A4C', 'red': '#D1342B', 'gold': '#F4B942',
     'nbg': '#0D1522', 'npaper': '#16223A', 'npaper2': '#1B2944', 'nink': '#EAF0F7', 'nmuted': '#A3B3C8', 'nrule': '#2A3B55', 'ndash': '#51627D'}
DISP = "font-family: Karantina, 'Arial Narrow', sans-serif; font-weight: 700;"
BODY = "font-family: 'IBM Plex Sans Hebrew', 'Segoe UI', system-ui, sans-serif;"
FONTS = '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Karantina:wght@700&amp;family=IBM+Plex+Sans+Hebrew:wght@400;500;600;700&amp;display=swap">'
DAYS = '''class Component extends DCLogic {
renderVals() {
const d = Math.ceil((new Date('2027-01-10T16:00:00+02:00') - new Date()) / 864e5);
return { days: d > 0 ? d : 0 };
}
}'''
PLANE = '<path d="M2 13.5v-2l8-4.5V2.5a1.5 1.5 0 0 1 3 0V7l8 4.5v2l-8-2.5v5l2.5 2v1.5L12 18.5 8.5 19.5V18l2.5-2v-5z" transform="rotate(-90 12 12)"></path>'


def plane(size, color):
    return f'<svg width="{size}" height="{size}" viewBox="0 0 24 24" fill="{color}" aria-hidden="true" style="flex: none">{PLANE}</svg>'


def page(title, body, css=''):
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
.hero{{background:url({PANO['noon']}) center 40%/cover no-repeat}}
.hero-night{{background:linear-gradient(#0B1320,#16223A)}}
{css}
</style>
</helmet>
{body}
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{{"$preview":{{"width":390,"height":844}}}}'>
{DAYS}
</script>
</body>
</html>
'''


def sign(text, sub, color, width='88%', fg='#FFFFFF'):
    return (f'<div style="display: flex; flex-direction: column; justify-content: center; min-height: 72px; box-sizing: border-box; width: {width}; '
            f'padding: 8px 20px 8px 40px; background: {color}; color: {fg}; clip-path: polygon(0 50%, 26px 0, 100% 0, 100% 100%, 26px 100%)">'
            f'<span style="{DISP} font-size: 40px; line-height: 1">{text}</span><span style="font-size: 13px">{sub}</span></div>')


def frame(card, top_card=236, night=False, signs_top=None):
    """The home screen around the card: the view from the village, the header, and the first signs below."""
    bg, ink, muted = (C['nbg'], C['nink'], C['nmuted']) if night else (C['snow'], C['ink'], C['muted'])
    hero = 'hero-night' if night else 'hero'
    stars = ''
    if night:
        pts = [(40, 40), (90, 90), (150, 30), (210, 70), (300, 40), (350, 110), (120, 150), (260, 130), (60, 190), (330, 180)]
        stars = ''.join(f'<span style="position: absolute; left: {x}px; top: {y}px; width: 2px; height: 2px; background: #EAF0F7; opacity: .8"></span>' for x, y in pts)
        stars += ('<svg width="390" height="140" viewBox="0 0 390 140" aria-hidden="true" style="position: absolute; left: 0; bottom: 0">'
                  '<path d="M0 140 L0 90 L40 70 L80 88 L130 40 L170 72 L215 30 L260 66 L300 50 L340 80 L390 60 L390 140 Z" fill="#22324D"></path>'
                  '<path d="M0 140 L0 112 L60 96 L120 110 L180 92 L240 108 L310 94 L390 106 L390 140 Z" fill="#2C3E5C"></path></svg>')
    signs_top = signs_top or top_card + 340
    return (f'<div style="width: 390px; height: 844px; box-sizing: border-box; overflow: hidden; position: relative; background: {bg}; color: {ink}; {BODY} direction: rtl">'
            f'<div class="{hero}" style="position: absolute; inset: 0 0 auto 0; height: 300px">{stars}</div>'
            f'<div style="position: absolute; top: 26px; right: 22px; left: 22px; display: flex; justify-content: space-between; align-items: center; color: {"#EAF0F7" if night else C["ink"]}">'
            f'<span style="font-size: 15px; font-weight: 700">גודאורי, גאורגיה</span>'
            f'<span style="font-size: 12px">השעה בגודאורי <b style="font-size: 18px">{"22:10" if night else "13:35"}</b></span></div>'
            f'<div style="position: absolute; top: {top_card}px; right: 18px; left: 18px">{card}</div>'
            f'<div style="position: absolute; top: {signs_top}px; right: 0; left: 0; display: flex; flex-direction: column; align-items: flex-end; gap: 14px; padding-right: 30px; box-sizing: border-box">'
            f'<div style="position: absolute; right: 20px; top: -8px; bottom: -200px; width: 6px; background: {ink}"></div>'
            + sign('מפת מסלולים', '27 מסלולים, פרטים וסרטונים', C['blue'])
            + sign('נקודת מפגש', 'בוחרים תחנה ושעה ושולחים לקבוצה', C['gold'], '80%', C['ink'])
            + '</div></div>')


def ridge_barcode(w=90, h=34, color=C['ink']):
    """Bars whose heights trace the ridge above New Gudauri, so it reads as the mountain and not as a code."""
    hs = [.30, .42, .55, .48, .62, .80, .70, .58, .66, .92, 1, .86, .74, .60, .68, .78, .64, .50, .44, .56, .70, .62, .48, .36, .42, .30, .24, .34, .28, .20, .26, .18]
    step = w / len(hs)
    bars = ''.join(f'<rect x="{i * step:.1f}" y="{h - v * h:.1f}" width="{2 if i % 3 == 0 else 1.2 if i % 2 else .7}" height="{v * h:.1f}"></rect>' for i, v in enumerate(hs))
    return f'<svg width="{w}" height="{h}" viewBox="0 0 {w} {h}" fill="{color}" aria-hidden="true">{bars}</svg>'


def peak_square(size=74, color=C['ink'], paper=C['paper']):
    """Three corner squares like a 2D code, and a mountain drawn in pixels between them. It holds no data."""
    r = ''
    for x, y in [(0, 0), (14, 0), (0, 14)]:
        r += f'<rect x="{x}" y="{y}" width="7" height="7"></rect><rect x="{x + 1}" y="{y + 1}" width="5" height="5" fill="{paper}"></rect><rect x="{x + 2}" y="{y + 2}" width="3" height="3"></rect>'
    for y in range(8, 21):
        for x in range(21):
            if (x < 8 and y < 8) or (x > 12 and y < 8) or (x < 8 and y > 12):
                continue
            if abs(x - 13) + (20 - y) * 0.9 < 8.5 or (x * 7 + y * 13) % 5 == 0:
                r += f'<rect x="{x}" y="{y}" width="1" height="1"></rect>'
    return f'<svg width="{size}" height="{size}" viewBox="0 0 21 21" fill="{color}" shape-rendering="crispEdges" aria-hidden="true">{r}</svg>'


def lbl(t, color=C['muted']):
    return f'<span style="display: block; font-size: 11px; font-weight: 600; letter-spacing: .05em; color: {color}">{t}</span>'


def d(iso):
    y, m, dd = iso.split('-')
    return f'{int(dd)}.{int(m)}'


out = {}

# ---------------------------------------------------------------- BP0: today
today = (f'<div style="display: flex; background: {C["paper"]}; transform: rotate(-2deg); box-shadow: 0 10px 24px rgba(19,35,58,.18)">'
         f'<div style="flex: 1; padding: 14px 18px 16px; display: flex; flex-direction: column; gap: 8px">'
         f'<span style="font-size: 12px; font-weight: 600; color: {C["muted"]}">כרטיס עלייה למטוס</span>'
         f'<div style="{DISP} font-size: 34px; line-height: 1; display: flex; flex-wrap: wrap; gap: 8px; align-items: center">{O["from"]} <span style="color: {C["blue"]}">←</span> {O["to"]}</div>'
         f'<div style="display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px 20px">'
         f'<div>{lbl("תאריך")}<b style="font-size: 15px">10.1.2027</b></div><div>{lbl("טיסה")}<b style="font-size: 15px" dir="ltr">{O["flight"]}</b></div>'
         f'<div>{lbl("המראה")}<b style="font-size: 15px">{O["departs"]}</b></div><div>{lbl("נחיתה")}<b style="font-size: 15px">{O["arrives"]}</b></div></div>'
         f'<span style="font-size: 12px; color: {C["muted"]}">חזרה: <b style="color: {C["ink"]}">15.1.2027</b>, טיסה <b style="color: {C["ink"]}" dir="ltr">{R["flight"]}</b>, המראה <b style="color: {C["ink"]}">{R["departs"]}</b><br>{R["note"]}</span></div>'
         f'<div style="width: 108px; flex: none; border-right: 2px dashed {C["dash"]}; background: {C["blue"]}; color: #fff; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px">'
         f'<b style="{DISP} font-size: 72px; line-height: .8">{{{{days}}}}</b><span style="font-size: 13px; font-weight: 600">ימים לטיסה</span></div></div>')
out['BP0-Today.dc.html'] = page('הכרטיס היום', frame(today))

# ---------------------------------------------------------------- BP1: classic, with real notches and a stub
NOTCH = 'radial-gradient(circle 11px at {x} 0, transparent 98%, #000) top/100% 51% no-repeat, radial-gradient(circle 11px at {x} 100%, transparent 98%, #000) bottom/100% 51% no-repeat'
classic = (f'<div style="display: flex; transform: rotate(-2deg); filter: drop-shadow(0 10px 14px rgba(19,35,58,.2))">'
           f'<div class="nl" style="flex: 1; min-width: 0; background: {C["paper"]}; display: flex; flex-direction: column">'
           f'<div style="background: {C["blue"]}; color: #fff; display: flex; justify-content: space-between; align-items: center; padding: 7px 16px; font-size: 12px; font-weight: 700; letter-spacing: .04em">'
           f'<span>{TRIP["airline"]} · כרטיס עלייה למטוס</span><span dir="ltr">10 JAN 27</span></div>'
           f'<div style="padding: 12px 16px 14px; display: flex; flex-direction: column; gap: 12px">'
           f'<div style="display: flex; align-items: flex-end; justify-content: space-between; gap: 6px">'
           f'<div>{lbl("מ")}<div style="{DISP} font-size: 54px; line-height: .8" dir="ltr">TLV</div><span style="font-size: 13px; font-weight: 600">{O["from"]}</span></div>'
           f'<div style="align-self: center">{plane(30, C["blue"])}</div>'
           f'<div style="text-align: left">{lbl("אל")}<div style="{DISP} font-size: 54px; line-height: .8" dir="ltr">TBS</div><span style="font-size: 13px; font-weight: 600">{O["to"]}</span></div></div>'
           f'<div style="display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px; border-top: 1px solid {C["rule"]}; padding-top: 10px">'
           f'<div>{lbl("טיסה")}<b style="font-size: 15px" dir="ltr">{O["flight"]}</b></div><div>{lbl("תאריך")}<b style="font-size: 15px">{d(O["date"])}</b></div>'
           f'<div>{lbl("המראה")}<b style="font-size: 15px">{O["departs"]}</b></div><div>{lbl("נחיתה")}<b style="font-size: 15px">{O["arrives"]}</b></div></div>'
           f'<span style="font-size: 12px; color: {C["muted"]}">חזרה: <b style="color: {C["ink"]}">{d(R["date"])}</b>, טיסה <b style="color: {C["ink"]}" dir="ltr">{R["flight"]}</b>, המראה <b style="color: {C["ink"]}">{R["departs"]}</b> בלילה</span></div></div>'
           f'<div class="nr" style="width: 104px; flex: none; background: {C["paper2"]}; border-right: 2px dashed {C["dash"]}; display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 12px 8px 10px; box-sizing: border-box; text-align: center">'
           f'{lbl("עוד")}<b style="{DISP} font-size: 64px; line-height: .8; color: {C["blue"]}">{{{{days}}}}</b>{lbl("ימים לטיסה")}'
           f'<span style="{DISP} font-size: 22px; line-height: 1; margin-top: 4px" dir="ltr">TLV › TBS</span>'
           f'<div style="margin-top: auto">{ridge_barcode(84, 30)}</div></div></div>')
css1 = ('.nl{-webkit-mask:' + NOTCH.format(x='0') + ';mask:' + NOTCH.format(x='0') + '}'
        '.nr{-webkit-mask:' + NOTCH.format(x='100%') + ';mask:' + NOTCH.format(x='100%') + '}')
out['BP1-Classic.dc.html'] = page('א. הקלאסי', frame(classic), css1)


# ---------------------------------------------------------------- BP2: wallet pass (and BP4: the same at night)
def wallet(night=False):
    paper, paper2, ink, muted, rule, dash, acc, bg = ((C['npaper'], C['npaper2'], C['nink'], C['nmuted'], C['nrule'], C['ndash'], C['gold'], C['nbg']) if night
                                                     else (C['paper'], C['paper2'], C['ink'], C['muted'], C['rule'], C['dash'], C['blue'], C['snow']))
    word = 'לילות' if night else 'ימים'
    hole = f'radial-gradient(circle 11px at 0 50%, {bg} 98%, transparent), radial-gradient(circle 11px at 100% 50%, {bg} 98%, transparent)'
    return (f'<div style="position: relative; padding-top: 26px">'
            f'<a href="#" aria-label="להחליף לכרטיס החזור" style="position: absolute; top: 0; right: 14px; left: 14px; height: 60px; box-sizing: border-box; background: {paper2}; border: 1px solid {rule}; '
            f'display: flex; justify-content: space-between; align-items: flex-start; padding: 5px 12px; font-size: 11.5px; color: {muted}; text-decoration: none">'
            f'<span>חזרה · <b style="color: {ink}" dir="ltr">TBS › TLV</b></span><span>{d(R["date"])} · {R["departs"]}</span></a>'
            f'<div style="position: relative; background: {paper}; box-shadow: 0 10px 24px rgba(0,0,0,{.45 if night else .18}); display: flex; flex-direction: column">'
            f'<div style="display: flex; justify-content: space-between; align-items: center; padding: 12px 16px; border-bottom: 1px solid {rule}">'
            f'<div style="display: flex; align-items: center; gap: 8px; font-weight: 700"><span style="width: 22px; height: 22px; background: {acc}; clip-path: polygon(0 100%, 38% 22%, 55% 52%, 72% 30%, 100% 100%)"></span>גודאורי 2027</div>'
            f'<div style="text-align: left">{lbl("טיסה", muted)}<b style="{DISP} font-size: 26px; line-height: 1" dir="ltr">{O["flight"]}</b></div></div>'
            f'<div style="display: flex; justify-content: space-between; align-items: center; padding: 14px 16px 12px">'
            f'<div>{lbl(O["from"], muted)}<div style="{DISP} font-size: 72px; line-height: .8" dir="ltr">TLV</div></div>'
            f'<div style="flex: 1; margin: 0 10px; height: 2px; position: relative; background: repeating-linear-gradient(90deg, {dash} 0 6px, transparent 6px 11px)">'
            f'<span style="position: absolute; left: 50%; top: 50%; transform: translate(-50%, -50%); background: {paper}; padding: 0 4px; display: flex">{plane(22, acc)}</span></div>'
            f'<div style="text-align: left">{lbl(O["to"], muted)}<div style="{DISP} font-size: 72px; line-height: .8" dir="ltr">TBS</div></div></div>'
            f'<div style="display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px 8px; padding: 0 16px 14px">'
            f'<div>{lbl("תאריך", muted)}<b style="font-size: 16px">10.1.2027</b></div><div>{lbl("המראה", muted)}<b style="font-size: 16px">{O["departs"]}</b></div><div>{lbl("נחיתה", muted)}<b style="font-size: 16px">{O["arrives"]}</b></div>'
            f'<div>{lbl("נוסעים", muted)}<b style="font-size: 16px">6 החבר׳ה</b></div><div>{lbl("כבודה", muted)}<b style="font-size: 16px">תיק יד</b></div><div>{lbl("ימי סקי", muted)}<b style="font-size: 16px">11–14.1</b></div></div>'
            f'<div style="height: 22px; position: relative; background: {hole}"><div style="position: absolute; top: 10px; right: 14px; left: 14px; border-top: 2px dashed {dash}"></div></div>'
            f'<div style="display: flex; align-items: center; gap: 14px; padding: 6px 16px 16px">'
            f'<b style="{DISP} font-size: 60px; line-height: .8; color: {acc}">{{{{days}}}}</b>'
            f'<div style="flex: 1; font-size: 12px; color: {muted}"><b style="display: block; font-size: 14px; color: {ink}">{word} לטיסה</b>נגיעה בכרטיס שמאחור מחליפה לטיסת החזור</div>'
            f'{peak_square(70, ink, paper)}</div></div></div>')


out['BP2-Wallet.dc.html'] = page('ב. בארנק של הטלפון', frame(wallet(), top_card=196, signs_top=640))
out['BP4-WalletNight.dc.html'] = page('ב. בלילה', frame(wallet(True), top_card=196, night=True, signs_top=640))

# ---------------------------------------------------------------- BP3: one pass for the crew, a stub for each
names = TRIP['members']
stubs = ''.join(f'<div style="padding: 8px 10px; background: {C["paper"]}; display: flex; flex-direction: column; gap: 1px; min-width: 0">'
                f'<span style="font-size: 11px; color: {C["muted"]}">נוסע {i + 1}</span><b style="font-size: 13px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis">{n}</b></div>'
                for i, n in enumerate(names))
crew = (f'<div style="background: {C["paper"]}; box-shadow: 0 10px 24px rgba(19,35,58,.18); transform: rotate(1.5deg)">'
        f'<div style="display: flex; justify-content: space-between; align-items: center; gap: 10px; padding: 14px 16px 10px">'
        f'<div style="display: flex; align-items: center; gap: 8px"><span style="{DISP} font-size: 46px; line-height: .8" dir="ltr">TLV</span>{plane(22, C["blue"])}<span style="{DISP} font-size: 46px; line-height: .8" dir="ltr">TBS</span></div>'
        f'<div style="text-align: left">{lbl(d(O["date"]) + " · " + O["flight"])}<b style="{DISP} font-size: 34px; line-height: .9">{O["departs"]}</b></div></div>'
        f'<div style="display: flex; height: 8px"><span style="flex: 1; background: {C["green"]}"></span><span style="flex: 1; background: {C["blue"]}"></span><span style="flex: 1; background: {C["red"]}"></span><span style="flex: 1; background: {C["ink"]}"></span></div>'
        f'<div style="display: flex; justify-content: space-between; align-items: center; padding: 8px 16px; font-size: 12.5px; color: {C["muted"]}">'
        f'<span><b style="{DISP} font-size: 30px; color: {C["blue"]}">{{{{days}}}}</b> ימים לטיסה</span><span>חזרה {d(R["date"])} · {R["departs"]} בלילה</span></div>'
        f'<div style="display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 1px; background: {C["rule"]}; border-top: 2px dashed {C["dash"]}">{stubs}</div></div>')
out['BP3-Crew.dc.html'] = page('ג. כרטיס של החבר׳ה', frame(crew, signs_top=560))

if __name__ == '__main__':
    dst = root / 'design/canvas/project'
    for n, s in out.items():
        (dst / n).write_text(s, encoding='utf-8')
    print({n: len(s) for n, s in out.items()})
