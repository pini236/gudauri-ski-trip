#!/usr/bin/env python3
"""Round 7, part 2: the look of A (the classic) with the idea of B (the wallet), as Pini asked.

AB1  the classic, wider: B's extra fields, and the return pass behind it (tap to swap)
AB2  B's tall shape dressed as A: airline strip, big codes, notched perforation, stub at the bottom
AB3  AB2 at night: dark paper with a glowing print
AB4  the tear, step by step (static), for review
All of them: paper texture, a stub you tear off with a short vibration, and it comes back.
Run from the repo root: python3 design/round7/build_mix.py"""
import sys, pathlib
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from build import C, DISP, BODY, FONTS, PANO, O, R, TRIP, plane, lbl, d, ridge_barcode, sign, frame  # noqa: E402

root = pathlib.Path(__file__).resolve().parent.parent.parent

SCRIPT = '''class Component extends DCLogic {
renderVals() {
const s = this.state || {};
const night = %(night)s;
const d = Math.ceil((new Date('2027-01-10T16:00:00+02:00') - new Date()) / 864e5);
const out = {fromCode: 'TLV', fromCity: '%(of)s', toCode: 'TBS', toCity: '%(ot)s', flight: '%(ofl)s', date: '%(od)s', dep: '%(odep)s', arr: '%(oarr)s', kind: 'הלוך', other: 'חזרה · TBS › TLV · %(rd)s · %(rdep)s'};
const ret = {fromCode: 'TBS', fromCity: '%(ot)s', toCode: 'TLV', toCity: '%(of)s', flight: '%(rfl)s', date: '%(rd)s', dep: '%(rdep)s', arr: '%(rarr)s', kind: 'חזור', other: 'הלוך · TLV › TBS · %(od)s · %(odep)s'};
const t = s.ret ? ret : out;
const buzz = (p) => { try { if (navigator.vibrate) navigator.vibrate(p); } catch (e) {} };
return Object.assign({}, t, {
  days: d > 0 ? d : 0,
  word: night ? 'לילות' : 'ימים',
  frontMove: s.busy ? 'translateY(34px) rotate(1deg)' : 'translateY(0) rotate(%(tilt)s)',
  frontFade: s.busy ? '0' : '1',
  stubMove: s.torn ? '%(fly)s' : 'none',
  stubFade: s.torn ? '0' : '1',
  stubEase: s.torn ? 'transform .75s cubic-bezier(.35,.05,.7,1), opacity .75s ease-in .2s' : 'transform .35s ease-out, opacity .25s',
  swap: () => {
    if (s.busy) return;
    buzz(8);
    this.setState({busy: true});
    setTimeout(() => this.setState({ret: !(this.state || {}).ret}), 230);
    setTimeout(() => this.setState({busy: false}), 260);
  },
  tear: () => {
    if (s.torn) return;
    buzz([10, 40, 26]);
    this.setState({torn: true});
    setTimeout(() => this.setState({torn: false}), 2400);
  }
});
}
}'''


def script(night=False, tilt='-2deg', fly='translate(-60px, 170px) rotate(-26deg)'):
    return SCRIPT % dict(night='true' if night else 'false', of=O['from'], ot=O['to'], ofl=O['flight'], od=d(O['date']), odep=O['departs'], oarr=O['arrives'],
                         rfl=R['flight'], rd=d(R['date']), rdep=R['departs'], rarr=R['arrives'], tilt=tilt, fly=fly)


def page(title, body, js, css='', night=False):
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
body{{margin:0;background:{C['nbg'] if night else C['snow']}}}
.hero{{background:url({PANO['noon']}) center 40%/cover no-repeat}}
.hero-night{{background:linear-gradient(#0B1320,#16223A)}}
.paper{{background-image:repeating-linear-gradient(97deg,rgba(19,35,58,.018) 0 1px,transparent 1px 7px),repeating-linear-gradient(4deg,rgba(19,35,58,.014) 0 1px,transparent 1px 11px)}}
.paper-n{{background-image:repeating-linear-gradient(97deg,rgba(255,255,255,.025) 0 1px,transparent 1px 7px),repeating-linear-gradient(4deg,rgba(255,255,255,.02) 0 1px,transparent 1px 11px)}}
.glow{{text-shadow:0 0 6px rgba(255,200,90,.65),0 0 18px rgba(244,185,66,.35)}}
.glow-svg{{filter:drop-shadow(0 0 3px rgba(255,200,90,.8))}}
.tap{{border:0;margin:0;font:inherit;color:inherit;cursor:pointer;text-align:inherit}}
.tap:focus-visible{{outline:3px solid {C['gold']};outline-offset:2px}}
{css}
@media (prefers-reduced-motion: reduce){{ *{{ transition: none !important; animation: none !important }} }}
</style>
</helmet>
{body}
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{{"$preview":{{"width":390,"height":844}}}}'>
{js}
</script>
</body>
</html>
'''


def grain(op=.09, light=False):
    """Paper grain: fractal noise over the card, multiply on paper, screen at night."""
    m = '0 0 0 0 1  0 0 0 0 1  0 0 0 0 1  0 0 0 .9 0' if light else '0 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 .9 0'
    fid = 'gn' if light else 'gd'
    return (f'<svg aria-hidden="true" width="100%" height="100%" style="position: absolute; inset: 0; pointer-events: none; opacity: {op}; mix-blend-mode: {"screen" if light else "multiply"}">'
            f'<filter id="{fid}"><feTurbulence type="fractalNoise" baseFrequency=".85" numOctaves="2" stitchTiles="stitch"></feTurbulence><feColorMatrix values="{m}"></feColorMatrix></filter>'
            f'<rect width="100%" height="100%" filter="url(#{fid})"></rect></svg>')


def notch_css(cls, side):
    """Half-circle cut-outs where the perforation meets the edge. side: left|right (a vertical line) or top|bottom (a horizontal line)."""
    if side in ('left', 'right'):
        x = '0' if side == 'left' else '100%'
        m = f'radial-gradient(circle 11px at {x} 0, transparent 98%, #000) top/100% 51% no-repeat, radial-gradient(circle 11px at {x} 100%, transparent 98%, #000) bottom/100% 51% no-repeat'
    else:
        y = '0' if side == 'top' else '100%'
        m = f'radial-gradient(circle 11px at 0 {y}, transparent 98%, #000) left/51% 100% no-repeat, radial-gradient(circle 11px at 100% {y}, transparent 98%, #000) right/51% 100% no-repeat'
    return f'.{cls}{{-webkit-mask:{m};mask:{m}}}'


def pal(night):
    if night:
        return dict(paper=C['npaper'], paper2=C['npaper2'], ink='#FFD98A', muted='#C9B98F', rule='#3A4A66', dash='#6C7E9C', strip='#F4B942', onstrip=C['nbg'], acc='#FFD98A', bg=C['nbg'], g=' glow', gs=' glow-svg', pc='paper-n')
    return dict(paper=C['paper'], paper2=C['paper2'], ink=C['ink'], muted=C['muted'], rule=C['rule'], dash=C['dash'], strip=C['blue'], onstrip='#FFFFFF', acc=C['blue'], bg=C['snow'], g='', gs='', pc='paper')


def L(t, p):
    return f'<span style="display: block; font-size: 11px; font-weight: 600; letter-spacing: .05em; color: {p["muted"]}">{t}</span>'


def extras(p, cols=3):
    return (f'<div style="display: grid; grid-template-columns: repeat({cols}, minmax(0, 1fr)); gap: 8px; border-top: 1px solid {p["rule"]}; padding-top: 8px">'
            f'<div>{L("נוסעים", p)}<b class="{p["g"]}" style="font-size: 14px">6 החבר׳ה</b></div><div>{L("כבודה", p)}<b class="{p["g"]}" style="font-size: 14px">תיק יד</b></div>'
            f'<div>{L("ימי סקי", p)}<b class="{p["g"]}" style="font-size: 14px">11–14.1</b></div></div>')


def back_strip(p, w='calc(100% - 28px)'):
    """The other flight, peeking out from behind. Tapping it swaps the two."""
    return (f'<button class="tap {p["pc"]}" onClick="{{{{swap}}}}" aria-label="להחליף לכרטיס השני" style="position: absolute; top: 0; right: 14px; width: {w}; height: 64px; box-sizing: border-box; '
            f'background-color: {p["paper2"]}; border-top: 6px solid {p["strip"]}; transform: rotate(1.2deg); box-shadow: 0 4px 10px rgba(0,0,0,.12); '
            f'display: flex; justify-content: space-between; align-items: flex-start; padding: 5px 14px; font-size: 12px; color: {p["muted"]}">'
            f'<b class="{p["g"]}" style="color: {p["ink"]}">{{{{other}}}}</b><span>נגיעה להחלפה</span></button>')


# ---------------------------------------------------------------- AB1: the classic, wider, with the other flight behind it
def ab1(p):
    return (f'<div style="position: relative; padding-top: 30px">{back_strip(p)}'
            f'<div style="position: relative; display: flex; transform: {{{{frontMove}}}}; opacity: {{{{frontFade}}}}; transition: transform .23s ease-in, opacity .23s; filter: drop-shadow(0 10px 14px rgba(0,0,0,.2))">'
            f'<div class="nl {p["pc"]}" style="position: relative; flex: 1; min-width: 0; background-color: {p["paper"]}; display: flex; flex-direction: column">{grain()}'
            f'<div style="background: {p["strip"]}; color: {p["onstrip"]}; display: flex; justify-content: space-between; align-items: center; padding: 7px 16px; font-size: 12px; font-weight: 700; letter-spacing: .04em">'
            f'<span>{TRIP["airline"]} · כרטיס עלייה · {{{{kind}}}}</span><span>{{{{date}}}}</span></div>'
            f'<div style="padding: 10px 16px 12px; display: flex; flex-direction: column; gap: 10px">'
            f'<div style="display: flex; align-items: flex-end; justify-content: space-between; gap: 6px">'
            f'<div>{L("מ", p)}<div class="{p["g"]}" style="{DISP} font-size: 50px; line-height: .8; color: {p["ink"]}" dir="ltr">{{{{fromCode}}}}</div><span style="font-size: 12.5px; font-weight: 600; color: {p["ink"]}">{{{{fromCity}}}}</span></div>'
            f'<div class="{p["gs"]}" style="align-self: center">{plane(28, p["acc"])}</div>'
            f'<div style="text-align: left">{L("אל", p)}<div class="{p["g"]}" style="{DISP} font-size: 50px; line-height: .8; color: {p["ink"]}" dir="ltr">{{{{toCode}}}}</div><span style="font-size: 12.5px; font-weight: 600; color: {p["ink"]}">{{{{toCity}}}}</span></div></div>'
            f'<div style="display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; border-top: 1px solid {p["rule"]}; padding-top: 8px">'
            f'<div>{L("טיסה", p)}<b class="{p["g"]}" style="font-size: 14px; color: {p["ink"]}" dir="ltr">{{{{flight}}}}</b></div>'
            f'<div>{L("המראה", p)}<b class="{p["g"]}" style="font-size: 14px; color: {p["ink"]}">{{{{dep}}}}</b></div><div>{L("נחיתה", p)}<b class="{p["g"]}" style="font-size: 14px; color: {p["ink"]}">{{{{arr}}}}</b></div></div>'
            f'{extras(p)}</div></div>'
            f'<div style="position: relative; width: 100px; flex: none">'
            f'<div style="position: absolute; inset: 0; border: 2px dashed {p["dash"]}; border-right: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; font-size: 12px; color: {p["muted"]}">נתלש!<br>נתראה בשדה</div>'
            f'<button class="tap nr {p["pc"]}" onClick="{{{{tear}}}}" aria-label="לתלוש את הספח" style="position: relative; width: 100%; height: 100%; background-color: {p["paper2"]}; border-right: 2px dashed {p["dash"]}; '
            f'display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 12px 6px 10px; box-sizing: border-box; text-align: center; transform: {{{{stubMove}}}}; opacity: {{{{stubFade}}}}; transition: {{{{stubEase}}}}; transform-origin: 50% 0">'
            f'{grain()}{L("עוד", p)}<b class="{p["g"]}" style="{DISP} font-size: 62px; line-height: .8; color: {p["acc"]}">{{{{days}}}}</b>{L("{{word}} לטיסה", p)}'
            f'<span class="{p["g"]}" style="{DISP} font-size: 20px; line-height: 1; margin-top: 4px; color: {p["ink"]}" dir="ltr">{{{{fromCode}}}} › {{{{toCode}}}}</span>'
            f'<span class="{p["gs"]}" style="margin-top: auto">{ridge_barcode(80, 28, p["ink"])}</span></button></div></div></div>')


# ---------------------------------------------------------------- AB2: B's tall shape, in A's dress
def ab2(p):
    return (f'<div style="position: relative; padding-top: 30px">{back_strip(p)}'
            f'<div style="position: relative; transform: {{{{frontMove}}}}; opacity: {{{{frontFade}}}}; transition: transform .23s ease-in, opacity .23s; filter: drop-shadow(0 10px 16px rgba(0,0,0,.22))">'
            f'<div class="nb {p["pc"]}" style="position: relative; background-color: {p["paper"]}">{grain(.09 if p["pc"] == "paper" else .07, p["pc"] != "paper")}'
            f'<div style="background: {p["strip"]}; color: {p["onstrip"]}; display: flex; justify-content: space-between; align-items: center; padding: 8px 16px; font-size: 12px; font-weight: 700; letter-spacing: .04em">'
            f'<span>{TRIP["airline"]} · כרטיס עלייה למטוס</span><span>{{{{kind}}}} · {{{{date}}}}</span></div>'
            f'<div style="padding: 12px 16px 14px; display: flex; flex-direction: column; gap: 12px">'
            f'<div style="display: flex; align-items: flex-end; justify-content: space-between">'
            f'<div>{L("מ", p)}<div class="{p["g"]}" style="{DISP} font-size: 76px; line-height: .78; color: {p["ink"]}" dir="ltr">{{{{fromCode}}}}</div><span style="font-size: 13px; font-weight: 600; color: {p["ink"]}">{{{{fromCity}}}}</span></div>'
            f'<div style="flex: 1; align-self: center; margin: 0 10px; height: 2px; position: relative; background: repeating-linear-gradient(90deg, {p["dash"]} 0 6px, transparent 6px 11px)">'
            f'<span class="{p["gs"]}" style="position: absolute; left: 50%; top: 50%; transform: translate(-50%, -50%); background: {p["paper"]}; padding: 0 4px; display: flex">{plane(24, p["acc"])}</span></div>'
            f'<div style="text-align: left">{L("אל", p)}<div class="{p["g"]}" style="{DISP} font-size: 76px; line-height: .78; color: {p["ink"]}" dir="ltr">{{{{toCode}}}}</div><span style="font-size: 13px; font-weight: 600; color: {p["ink"]}">{{{{toCity}}}}</span></div></div>'
            f'<div style="display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; border-top: 1px solid {p["rule"]}; padding-top: 10px">'
            f'<div>{L("טיסה", p)}<b class="{p["g"]}" style="font-size: 16px; color: {p["ink"]}" dir="ltr">{{{{flight}}}}</b></div>'
            f'<div>{L("המראה", p)}<b class="{p["g"]}" style="font-size: 16px; color: {p["ink"]}">{{{{dep}}}}</b></div><div>{L("נחיתה", p)}<b class="{p["g"]}" style="font-size: 16px; color: {p["ink"]}">{{{{arr}}}}</b></div></div>'
            f'{extras(p)}</div></div>'
            f'<div style="position: relative; height: 92px">'
            f'<div style="position: absolute; inset: 0; border: 2px dashed {p["dash"]}; border-top: 0; display: flex; align-items: center; justify-content: center; font-size: 13px; color: {p["muted"]}">הספח נתלש. נתראה בשדה!</div>'
            f'<button class="tap nt {p["pc"]}" onClick="{{{{tear}}}}" aria-label="לתלוש את הספח" style="position: relative; width: 100%; height: 100%; background-color: {p["paper2"]}; border-top: 2px dashed {p["dash"]}; box-sizing: border-box; '
            f'display: flex; align-items: center; gap: 14px; padding: 8px 18px; transform: {{{{stubMove}}}}; opacity: {{{{stubFade}}}}; transition: {{{{stubEase}}}}; transform-origin: 100% 0">'
            f'{grain()}<b class="{p["g"]}" style="{DISP} font-size: 66px; line-height: .8; color: {p["acc"]}">{{{{days}}}}</b>'
            f'<span style="flex: 1; display: flex; flex-direction: column; gap: 2px"><b class="{p["g"]}" style="font-size: 14px; color: {p["ink"]}">{{{{word}}}} לטיסה</b>'
            f'<span style="font-size: 11.5px; color: {p["muted"]}">נגיעה בספח תולשת אותו</span></span>'
            f'<span class="{p["gs"]}">{ridge_barcode(92, 40, p["ink"])}</span></button></div></div></div>')


css_lr = notch_css('nl', 'left') + notch_css('nr', 'right')
css_tb = notch_css('nb', 'bottom') + notch_css('nt', 'top')
out = {}
out['AB1-ClassicPlus.dc.html'] = page('א+ב: הקלאסי עם החזור מאחור', frame(ab1(pal(False)), top_card=196, signs_top=610), script(), css_lr)
out['AB2-ClassicTall.dc.html'] = page('א+ב: הקלאסי לגובה', frame(ab2(pal(False)), top_card=178, signs_top=660), script(tilt='-1deg', fly='translate(30px, 190px) rotate(14deg)'), css_tb)
out['AB3-ClassicTallNight.dc.html'] = page('א+ב: לגובה, בלילה', frame(ab2(pal(True)), top_card=178, night=True, signs_top=660),
                                         script(night=True, tilt='-1deg', fly='translate(30px, 190px) rotate(14deg)'), css_tb, night=True)

# ---------------------------------------------------------------- AB4: the tear, step by step
p = pal(False)


def mini(step):
    tf = ['none', 'translate(4px, 8px) rotate(3deg)', 'translate(22px, 40px) rotate(12deg)'][step]
    op = ['1', '1', '.5'][step]
    gap = ['', f'<span style="position: absolute; left: 30%; top: -3px; width: 34%; height: 5px; background: {C["snow"]}; clip-path: polygon(0 0, 10% 100%, 20% 0, 30% 100%, 40% 0, 50% 100%, 60% 0, 70% 100%, 80% 0, 90% 100%, 100% 0)"></span>', ''][step]
    return (f'<div style="position: relative; width: 300px; filter: drop-shadow(0 6px 10px rgba(19,35,58,.18))">'
            f'<div class="nb paper" style="position: relative; height: 50px; background-color: {p["paper"]}; display: flex; align-items: flex-end; padding: 8px 14px; box-sizing: border-box">{grain()}'
            f'<span style="{DISP} font-size: 34px; line-height: .8" dir="ltr">TLV › TBS</span></div>'
            f'<div style="position: relative; height: 62px"><div style="position: absolute; inset: 0; border: 2px dashed {p["dash"]}; border-top: 0"></div>'
            f'<div class="nt paper" style="position: relative; height: 100%; background-color: {p["paper2"]}; border-top: 2px dashed {p["dash"]}; display: flex; align-items: center; gap: 12px; padding: 0 14px; box-sizing: border-box; transform: {tf}; opacity: {op}; transform-origin: 100% 0">'
            f'{gap}<b style="{DISP} font-size: 52px; line-height: .8; color: {C["blue"]}">101</b><span style="flex: 1; font-size: 12.5px; font-weight: 700">ימים לטיסה</span>{ridge_barcode(70, 30)}</div></div></div>')


steps = [('1. נוגעים בספח', 'רטט קצר אחד, כמו קליק של נייר'),
         ('2. נקרע לאורך הניקוב', 'הספח נמשך מהפינה, ושן קטנה של נייר נשארת'),
         ('3. עף ונופל', 'רטט שני כשהוא משתחרר. אחרי שתי שניות הוא חוזר למקום')]
rows = ''.join(f'<div style="display: flex; flex-direction: column; gap: 10px"><div><b style="{DISP} font-size: 30px; line-height: 1">{t}</b><div style="font-size: 13px; color: {C["muted"]}">{s}</div></div>{mini(i)}</div>'
               for i, (t, s) in enumerate(steps))
tex = (f'<div style="display: flex; gap: 10px"><div class="paper" style="position: relative; flex: 1; height: 70px; background-color: #fff; border: 1px solid {C["rule"]}">{grain(.12)}'
       f'<span style="position: absolute; bottom: 6px; right: 8px; font-size: 11px; color: {C["muted"]}">מרקם נייר, ביום</span></div>'
       f'<div class="paper-n" style="position: relative; flex: 1; height: 70px; background-color: {C["npaper"]}; display: flex; align-items: center; justify-content: center">{grain(.08, True)}'
       f'<b class="glow" style="{DISP} font-size: 40px; color: #FFD98A" dir="ltr">TBS</b>'
       f'<span style="position: absolute; bottom: 6px; right: 8px; font-size: 11px; color: {C["nmuted"]}">הדפס זוהר, בלילה</span></div></div>')
steps_body = (f'<div style="width: 390px; height: 844px; box-sizing: border-box; overflow: hidden; background: {C["snow"]}; color: {C["ink"]}; {BODY} direction: rtl; padding: 24px 24px; display: flex; flex-direction: column; gap: 14px">'
              f'<b style="{DISP} font-size: 44px; line-height: 1">התלישה, שלב אחר שלב</b>{rows}{tex}'
              f'<span style="font-size: 12px; color: {C["muted"]}">מי שהגדיר בטלפון הפחתת תנועה: הספח פשוט נעלם וחוזר, בלי תנועה. רטט רק במכשירים שתומכים (לא באייפון).</span></div>')
out['AB4-TearSteps.dc.html'] = page('התלישה, שלב אחר שלב', steps_body, 'class Component extends DCLogic {\n  renderVals() { return {}; }\n}', notch_css('nb', 'bottom') + notch_css('nt', 'top'))

if __name__ == '__main__':
    dst = root / 'design/canvas/project'
    for n, s in out.items():
        (dst / n).write_text(s, encoding='utf-8')
    print({n: len(s) for n, s in out.items()})
