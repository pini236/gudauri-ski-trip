#!/usr/bin/env python3
"""UX pass after Pini's feedback on build 0.1.954 (26.11.2026): design/handoff/2026-11-26-pini-feedback, section A.
A fresh user could not tell what does what or where things live. The diagnosis and one coherent fix for all ten items:
a real menu instead of "the village as the menu", names that say what a thing is, screens that open what they promise,
and honest states (loading, far from the mountain).
A new canvas page "מעבר חוויית משתמש". Design only. Handoff: design/handoff/2026-11-26-redesign-ux-pass/.
Usage: python3 design/redesign/tools/ux_boards.py [canvas.json path, default the repo copy] [--preview <dir> <assets dir>]"""
import os, sys, json, re
import hn_boards as HN
from v56_boards import pic, ic, wb, li, PROJ, top, veil, P_SRCH, ctl_sw
from v56b_boards import rc
import peak_boards as PB
from peak_boards import VV, VH, base, tag, P
import gap4_boards as G4
import gap3_boards as G3
import gap2_boards as G2
from gap3_boards import av
from mountain import EN

PAGE = 'ux'
ACC = '#6B3FD4'
THUMB = {'descent': '3c0d37a76b28b468485cc5b45d932e9b', 'fresh': 'a4f02888570874d64fa3229b3999e9f1', 'merge': 'cbfbf4f3c8e61aca1797b020684286f4',
         'school': '14551d1536d37754acb972246fa0a48e', 'snowball': '9c7572de52d8d7be2cba3999374e42b7'}
TH = lambda k: '/_blob/' + THUMB[k]

# ---------------------------------------------------------------- icons (24 box, stroke)
P_GRID = '<rect x="4" y="4" width="6.500" height="6.500" rx="1.800"/><rect x="13.500" y="4" width="6.500" height="6.500" rx="1.800"/><rect x="4" y="13.500" width="6.500" height="6.500" rx="1.800"/><rect x="13.500" y="13.500" width="6.500" height="6.500" rx="1.800"/>'
P_PASS = '<rect x="6" y="3" width="12" height="18" rx="2.500"/><path d="M10 6.500h4"/><path d="M8.500 17 11 13.500l1.500 2 1.200-1.500L15.500 17z"/>'
P_PPL = '<circle cx="9" cy="9" r="3.200"/><path d="M3 19.500a6 6 0 0 1 12 0"/><circle cx="17" cy="10" r="2.500"/><path d="M17 14.500a4.500 4.500 0 0 1 4 5"/>'
P_FOOD = '<path d="M6 3v6a2.500 2.500 0 0 0 5 0V3M8.500 3v18M17 21V3c-2 1.500-3 4-3 7.500h3"/>'
P_CAFE = '<path d="M5 9h11v5a5 5 0 0 1-5 5h-1a5 5 0 0 1-5-5z"/><path d="M16 10.500h1.500a2.500 2.500 0 0 1 0 5H16M8.500 3v2.500M12.500 3v2.500"/>'
P_GAME = '<rect x="3" y="7.500" width="18" height="10.500" rx="5.200"/><path d="M8 11v3.500M6.200 12.750h3.600"/><circle cx="15.200" cy="11.500" r=".9" fill="currentColor"/><circle cx="17.500" cy="13.800" r=".9" fill="currentColor"/>'
P_TRK = '<path d="M18 3c-8 1-11 3.500-6.500 6.500S16 15 7.500 18.500"/><circle cx="6" cy="20" r="1.300"/>'
P_GOND = '<path d="M3 5.500 21 3"/><path d="M12 4.300V8"/><rect x="6" y="8" width="12" height="11" rx="3"/><path d="M6 13h12"/>'
P_PEN = '<path d="M4 20h4L19 9l-4-4L4 16z"/><path d="m13.500 6.500 4 4"/>'
P_CHV = '<path d="M15 6l-6 6 6 6"/>'          # points to the start side in right-to-left
P_BACK = '<path d="m9 6 6 6-6 6"/>'
P_PIN = '<path d="M12 21s-6.500-5.600-6.500-11A6.500 6.500 0 0 1 18.500 10c0 5.400-6.500 11-6.500 11z"/><circle cx="12" cy="10" r="2.300"/>'
P_NAV = '<path d="M12 3 19 20l-7-4-7 4z"/>'
P_MOON = '<path d="M19 14.500A8 8 0 0 1 9.500 5a7.500 7.500 0 1 0 9.500 9.500z"/>'
P_CARD = '<rect x="3" y="6" width="18" height="12" rx="2.500"/><path d="M3 10.500h18"/>'
P_X = '<path d="M6 6l12 12M18 6 6 18"/>'
P_PLUS = '<path d="M12 5v14M5 12h14"/>'
P_PLAY = '<path d="M8 5.500v13l10-6.500z"/>'
PLANE = ('<svg width="%d" height="%d" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" style="transform: scaleX(-1)">'
         '<path d="M21 15.500v-2l-8-5V3.800a1.500 1.500 0 0 0-3 0V8.500l-8 5v2l8-2.500v5l-2 1.500V21l3.500-1 3.500 1v-1.500L13 18v-5z" transform="rotate(90 12 12)"/></svg>')

UCSS = """
.H{--tile:rgba(255,255,255,.74);--tint:rgba(107,63,212,.11);--food:#C2540A;--cafe:#7A4A2B}
.H .night{--tile:rgba(255,255,255,.07);--tint:rgba(183,156,255,.17);--food:#FF9D5C;--cafe:#D9A77E}
.H .dock3 .mn{height:44px;border-radius:22px;background:var(--ink);color:var(--bg);display:flex;align-items:center;gap:7px;padding:0 15px 0 13px;font-size:15px;font-weight:700;flex:none}
.H .usheet{position:absolute;left:0;right:0;bottom:0;border-radius:28px 28px 0 0;padding:10px 14px 16px;box-sizing:border-box;display:flex;flex-direction:column;gap:10px}
.H .usheet>*{flex-shrink:0}
.H .tl{position:relative;border-radius:22px;background:var(--tile);border:1px solid var(--line);padding:12px 14px;box-sizing:border-box;display:flex;flex-direction:column;justify-content:space-between;text-align:start;height:104px;min-width:0}
.H .tl .ti{width:36px;height:36px;border-radius:50%;background:var(--tint);color:var(--acc);display:flex;align-items:center;justify-content:center;flex:none}
.H .tl .tt{font-size:16px;font-weight:700;line-height:1.2}
.H .tl .ts{font-size:12.5px;color:var(--ink2);line-height:1.3;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.H .tl .te{position:absolute;top:12px;inset-inline-end:12px;display:flex;align-items:center}
.H .tg2{display:grid;grid-template-columns:1fr 1fr;gap:10px}
.H .trp{position:relative;height:112px;border-radius:22px;background:var(--paper);color:var(--ink);display:flex;align-items:stretch;box-shadow:0 6px 18px rgba(10,25,45,.16);-webkit-mask:radial-gradient(circle 9px at 112px 0,#0000 97%,#000),radial-gradient(circle 9px at 112px 100%,#0000 97%,#000);-webkit-mask-composite:source-in;mask-composite:intersect;text-align:start}
.H .trp .a{flex:1;min-width:0;padding:14px 16px;display:flex;flex-direction:column;justify-content:space-between}
.H .trp .b{width:112px;flex:none;border-inline-start:2px dashed var(--line);display:flex;flex-direction:column;align-items:center;justify-content:center;gap:0;box-sizing:border-box}
.H [dir=rtl] .trp{-webkit-mask:radial-gradient(circle 9px at 112px 0,#0000 97%,#000),radial-gradient(circle 9px at 112px 100%,#0000 97%,#000);-webkit-mask-composite:source-in;mask-composite:intersect}
.H [dir=ltr] .trp{-webkit-mask:radial-gradient(circle 9px at calc(100% - 112px) 0,#0000 97%,#000),radial-gradient(circle 9px at calc(100% - 112px) 100%,#0000 97%,#000);-webkit-mask-composite:source-in;mask-composite:intersect}
.H .trp .tk{font-size:12.5px;font-weight:700;color:var(--ink2)}
.H .trp .cd3{font-size:25px;font-weight:300;direction:ltr;display:flex;align-items:center;gap:8px;line-height:1}
.H .trp .tn{display:block;font-size:44px;font-weight:300;line-height:1;direction:ltr;font-variant-numeric:tabular-nums}
.H .foot{display:flex;gap:8px}
.H .foot button{flex:1;height:44px;border-radius:22px;border:1px solid var(--line);display:flex;align-items:center;justify-content:center;gap:7px;font-size:13.5px;font-weight:500;min-width:0;white-space:nowrap}
.H .cta{display:inline-flex;align-items:center;gap:5px;height:28px;padding:0 11px 0 9px;border-radius:14px;background:var(--acc);color:var(--onacc);font-size:12.5px;font-weight:700;width:max-content}
.H .bk{position:absolute;top:52px;inset-inline-start:12px;width:44px;height:44px;border-radius:50%;display:flex;align-items:center;justify-content:center;padding:0}
.H .scr[dir=ltr] .bk svg{transform:scaleX(-1)}
.H .hd{position:absolute;top:52px;inset-inline-start:64px;height:44px;display:flex;align-items:center;font-size:21px;font-weight:700;text-shadow:0 0 8px var(--halo)}
.H .ed{display:inline-flex;align-items:center;gap:5px;height:32px;padding:0 12px 0 10px;border-radius:16px;background:var(--tint);color:var(--acc);font-size:13px;font-weight:700;position:relative}
.H .ed::before{content:'';position:absolute;inset:-6px}
.H .fv{border-bottom:1.5px dashed rgba(107,63,212,.45);padding-bottom:2px;width:max-content;max-width:100%}
.H .night .fv{border-bottom-color:rgba(183,156,255,.55)}
.H .fl.act b{color:var(--acc)}.H .fl.act .fv{border-bottom:2px solid var(--acc)}
.H .whl{display:flex;flex-direction:column;align-items:center;gap:0;direction:ltr;font-variant-numeric:tabular-nums}
.H .whl span{height:40px;display:flex;align-items:center;font-size:20px;color:var(--ink2);opacity:.55}
.H .whl b{height:48px;width:200px;display:flex;align-items:center;justify-content:center;font-size:30px;font-weight:500;border-top:1px solid var(--line);border-bottom:1px solid var(--line)}
/* the stub as its own piece of paper */
.H .pass.cut{-webkit-mask:radial-gradient(circle 10px at 0 100%,#0000 97%,#000),radial-gradient(circle 10px at 100% 100%,#0000 97%,#000);-webkit-mask-composite:source-in;mask-composite:intersect;padding-bottom:18px;border-radius:22px 22px 0 0}
.H .stub2{position:relative;height:100px;background:var(--paper);border-radius:0 0 22px 22px;display:flex;align-items:center;gap:10px;padding:0 20px;box-sizing:border-box;-webkit-mask:radial-gradient(circle 10px at 0 0,#0000 97%,#000),radial-gradient(circle 10px at 100% 0,#0000 97%,#000);-webkit-mask-composite:source-in;mask-composite:intersect;transform-origin:0 0}
.H .perf{position:absolute;left:14px;right:14px;top:-1px;height:0;border-top:2px dashed var(--line)}
.H .holes{position:absolute;left:14px;right:14px;top:-4px;height:8px;display:flex;justify-content:space-between}
.H .holes i{width:8px;height:8px;border-radius:50%;background:rgba(40,56,90,.55);transform:scale(0)}
@keyframes tearA{0%,12%{transform:none;opacity:1}34%{transform:rotate(5deg) translateY(3px);opacity:1}40%{transform:rotate(5deg) translateY(3px);opacity:1}62%{transform:rotate(20deg) translate(10px,250px);opacity:0}80%{transform:translateY(34px);opacity:0}92%,100%{transform:none;opacity:1}}
@keyframes holeA{0%,3%{transform:scale(0)}8%,62%{transform:scale(1)}70%,100%{transform:scale(0)}}
.H .tearing .stub2{animation:tearA 4.6s cubic-bezier(.4,0,.6,1) infinite}
.H .tearing .holes i{animation:holeA 4.6s linear infinite}
.H .torn .stub2{transform:rotate(5deg) translateY(3px)}.H .torn .holes i{transform:scale(1)}
/* the ski pass as an object */
.H .lan{position:absolute;left:0;top:0;width:390px;height:160px}
.H .skp{position:absolute;left:79px;top:136px;width:232px;height:316px;border-radius:22px;background:var(--paper);color:var(--ink);box-shadow:0 18px 34px rgba(10,25,45,.34);transform:rotate(-3deg);transform-origin:50% 0;overflow:hidden;display:flex;flex-direction:column;align-items:center;box-sizing:border-box;padding:14px 16px 0}
.H .skp .slot{width:46px;height:10px;border-radius:5px;background:rgba(40,56,90,.38);flex:none}
.H .night .skp .slot{background:rgba(0,0,0,.6)}
.H .skp .sk1{font-size:11.5px;font-weight:700;color:var(--ink2);letter-spacing:.02em;margin-top:12px}
.H .skp .sprice{font-size:76px;font-weight:300;line-height:1;direction:ltr;letter-spacing:-.03em;font-variant-numeric:tabular-nums;margin-top:12px;display:flex;align-items:baseline;gap:4px}
.H .skp .sprice small{font-size:30px;font-weight:400}
.H .skp .swho{font-size:17px;font-weight:700;margin-top:6px}
.H .skp .sdt{font-size:13px;color:var(--ink2);margin-top:2px}
.H .skp .bot{position:absolute;left:0;right:0;bottom:0;height:74px;background:var(--acc)}
.H .skp .bot svg{position:absolute;left:0;bottom:0}
.H .save{display:inline-flex;align-items:center;height:26px;padding:0 11px;border-radius:13px;background:#DDF2E5;color:#0F5C31;font-size:12.5px;font-weight:700;margin-top:10px}
.H .night .save{background:rgba(70,208,138,.2);color:#7BE3AC}
.H .dchips{display:flex;gap:6px}
.H .dc{flex:1;min-width:0;height:56px;border-radius:16px;border:1px solid var(--line);display:flex;flex-direction:column;align-items:center;justify-content:center;gap:1px;background:var(--tile)}
.H .dc b{font-size:17px;font-weight:700;line-height:1}.H .dc span{font-size:11px;color:var(--ink2);direction:ltr}
.H .dc.on{background:var(--acc);border-color:transparent;color:var(--onacc)}.H .dc.on span{color:var(--onacc)}
.H .ir{display:flex;align-items:center;gap:10px;min-height:44px;font-size:14px}
.H .ir+.ir{border-top:1px solid var(--line)}
.H .ir .i{color:var(--ink2);display:flex;flex:none}
.H .ir .e{margin-inline-start:auto;font-size:13.5px;color:var(--ink2);white-space:nowrap;display:flex;align-items:center;gap:4px}
.H .ir .e.l{color:var(--acc);font-weight:700}
/* places */
.H .pm{position:absolute;width:26px;height:26px;border-radius:50%;transform:translate(-50%,-50%);border:2px solid #fff;display:flex;align-items:center;justify-content:center;color:#fff;box-shadow:0 2px 6px rgba(0,0,0,.35);box-sizing:border-box}
.H .pm.food{background:#C2540A}.H .pm.cafe{background:#7A4A2B}.H .pm.sm{width:14px;height:14px;border-width:1.5px}
.H .pm.sel{width:40px;height:40px;border-width:3px;box-shadow:0 0 0 3px var(--acc),0 6px 14px rgba(0,0,0,.4);z-index:3}
.H .pl{display:flex;align-items:center;gap:12px;min-height:58px;text-align:start}
.H .pl+.pl{border-top:1px solid var(--line)}
.H .pl .pi{width:36px;height:36px;border-radius:50%;display:flex;align-items:center;justify-content:center;color:#fff;flex:none}
.H .pl .pi.food{background:#C2540A}.H .pl .pi.cafe{background:#7A4A2B}
.H .pl .c{display:flex;flex-direction:column;gap:1px;min-width:0;flex:1}
.H .pl .c b{font-size:15.5px;font-weight:700;direction:ltr;unicode-bidi:isolate;text-align:start;width:max-content;max-width:100%}
.H .pl .c span{font-size:12.5px;color:var(--ink2)}
.H .pl .e{margin-inline-start:auto;font-size:13px;color:var(--ink2);white-space:nowrap;font-variant-numeric:tabular-nums}
.H .pl.sel{background:var(--tint);border-radius:14px;margin:0 -8px;padding:0 8px;border-top-color:transparent}
.H .pl.sel+.pl{border-top-color:transparent}
/* the group */
.H .why{display:grid;grid-template-columns:1fr 1fr 1fr;gap:8px}
.H .why div{height:108px;border-radius:18px;background:var(--tile);border:1px solid var(--line);display:flex;flex-direction:column;align-items:center;justify-content:space-between;padding:12px 6px 10px;box-sizing:border-box;font-size:12.5px;font-weight:500;text-align:center}
.H .why .v{height:52px;display:flex;align-items:center;justify-content:center}
.H .sk{border-radius:8px;background:linear-gradient(90deg,var(--line) 0%,var(--line) 35%,rgba(255,255,255,.55) 50%,var(--line) 65%,var(--line) 100%);background-size:300% 100%;animation:shm 1.4s linear infinite}
.H .night .sk{background-image:linear-gradient(90deg,var(--line) 0%,var(--line) 35%,rgba(255,255,255,.3) 50%,var(--line) 65%,var(--line) 100%)}
@keyframes shm{0%{background-position:100% 0}100%{background-position:0 0}}
/* games */
.H .gm{border-radius:20px;background:var(--tile);border:1px solid var(--line);overflow:hidden;display:flex;flex-direction:column;text-align:start;min-width:0}
.H .gm img{width:100%;height:118px;object-fit:cover;display:block}
.H .gm .c{padding:9px 12px 11px;display:flex;flex-direction:column;gap:1px}
.H .gm b{font-size:15px;font-weight:700;line-height:1.25}
.H .gm span{font-size:12px;color:var(--ink2)}
.H .gm .cta{color:var(--onacc);font-size:12.5px}
.H .gm.big{flex-direction:row;height:140px}
.H .gm.big img{width:150px;height:100%;flex:none}
.H .gm.big .c{flex:1;justify-content:center;gap:3px;padding:0 14px}
/* far from the mountain */
.H .far{position:absolute;left:12px;right:12px;top:106px;min-height:60px;border-radius:22px;display:flex;align-items:center;gap:12px;padding:8px 14px 8px 8px;box-sizing:border-box}
.H .far .dir{width:44px;height:44px;border-radius:50%;background:var(--tint);color:var(--acc);display:flex;align-items:center;justify-content:center;flex:none}
.H .far b{font-size:16px;font-weight:700;line-height:1.25;display:block}
.H .far span{font-size:13px;color:var(--ink2)}
.H .txb{height:44px;display:flex;align-items:center;justify-content:center;font-size:14.5px;font-weight:700;color:var(--acc)}
"""
G4.MB.CSS = G4.MB.CSS + UCSS

PRICES = G2.PR
PL = G2.PL
LARI = '₾'


# ---------------------------------------------------------------- the mountain and the bar
def dock(L='he', btn=None):
    w = {'he': ('תפריט', 'לאן יורדים?'), 'en': ('Menu', 'Where to ski?')}[L]
    return ('<div class="dock3 glass"><button class="mn">%s %s</button><div class="find">%s<span>%s</span></div>%s</div>'
            % (ic(P_GRID, 19, 2), w[0], ic(P_SRCH, 18), w[1], btn if btn is not None else rc()))


def mountain(pre=False):
    return pic('hero', 'morning', 'night') + base('hero', cabins=True) + (HN.season_top() if pre else top())


def root(pre=False):
    return mountain(pre) + ctl_sw('pic') + dock()


def root_en():
    o = (pic('hero', 'morning', 'night') + base('hero', cabins=True)
         + top(text='11 of 14 lifts open · <span dir="ltr">−7°</span>', eg=False).replace('left: 64px', 'left: 12px; right: 64px').replace('left: 12px; padding: 0', 'right: 12px; left: auto; padding: 0'))
    return o


# ---------------------------------------------------------------- 1, 2: the menu
def trip_tile(state='trip', L='he'):
    if L == 'en':
        return ('<button class="trp"><span class="a"><span class="tk">My trip</span><span class="cd3" style="font-size: 21px; font-weight: 700; direction: ltr">When do you fly?</span>'
                '<span class="cta">%s Add a flight</span></span><span class="b" style="color: var(--ink2)">%s</span></button>' % (ic(P_PLUS, 15, 2.2), PLANE % (30, 30)))
    if state == 'none':
        return ('<button class="trp"><span class="a"><span class="tk">הטיול שלי</span><span style="font-size: 21px; font-weight: 700; line-height: 1">מתי טסים?</span>'
                '<span class="cta">%s הוספת טיסה</span></span><span class="b" style="color: var(--ink2)">%s</span></button>' % (ic(P_PLUS, 15, 2.2), PLANE % (30, 30)))
    return ('<button class="trp"><span class="a"><span class="tk">הטיול שלי</span>'
            '<span class="cd3"><span>TLV</span><span style="color: var(--acc); display: flex">%s</span><span>TBS</span></span>'
            '<span style="font-size: 13px; color: var(--ink2)"><bdi>10–15.1</bdi> · 4 ימי סקי</span></span>'
            '<span class="b"><span class="tn">45</span><span style="font-size: 12.5px; font-weight: 700">ימים לטיסה</span></span></button>' % (PLANE % (20, 20)))


def tile(icon, title, status, end=''):
    return ('<button class="tl"><span class="ti">%s</span>%s<span><span class="tt" style="display: block">%s</span><span class="ts" style="display: block">%s</span></span></button>'
            % (ic(icon, 20), ('<span class="te">%s</span>' % end) if end else '', title, status))


def avs(names):
    return '<span style="display: flex; direction: rtl">%s</span>' % ''.join(av(n, 's') for n in names)


def thumbs(keys):
    return ''.join('<img alt="" src="%s" style="width: 28px; height: 28px; border-radius: 50%%; object-fit: cover; border: 2px solid var(--paper); margin-inline-start: -9px">' % TH(k) for k in keys)


NFOOD = None


def menu(state='trip', L='he'):
    """the menu sheet: every destination that is not the mountain, each tile with its live line"""
    n = len(PLACES_V) + len(PLACES_M)
    if L == 'en':
        tiles = [tile(P_PASS, 'Ski pass', 'Day pass · 120 ' + LARI), tile(P_PPL, 'My group', 'Invite your friends'),
                 tile(P_FOOD, 'Food and drink', '%d places' % n), tile(P_GAME, 'Games', '5 games · work offline', thumbs(['merge', 'descent', 'snowball'])),
                 tile(P_TRK, 'My ski days', 'See a sample day'), tile(P_GOND, 'Lifts and weather', '11 of 14 open · <bdi>−7°</bdi>')]
        foot = ('Account', 'Settings', 'English')
    elif state == 'none':
        tiles = [tile(P_PASS, 'כרטיס סקי', 'כרטיס יום · 120 ' + LARI), tile(P_PPL, 'הקבוצה שלי', 'מזמינים את החברים לטיול'),
                 tile(P_FOOD, 'אוכל ושתייה', '%d מקומות, בכפר ועל ההר' % n), tile(P_GAME, 'משחקים', '5 משחקים · גם בלי קליטה', thumbs(['merge', 'descent', 'snowball'])),
                 tile(P_TRK, 'הימים שלי', 'יום גלישה לדוגמה'), tile(P_GOND, 'רכבלים ומזג אוויר', 'נפתחים ב-<bdi>20.12</bdi> · עוד 24 ימים')]
        foot = ('חשבון', 'הגדרות', 'עברית')
    else:
        tiles = [tile(P_PASS, 'כרטיס סקי', '4 ימים · 390 ' + LARI), tile(P_PPL, 'הקבוצה שלי', 'נפגשים 13:00 · <bdi>Goodaura</bdi>', avs(['נועה', 'איתי', 'דנה', 'יואב'])),
                 tile(P_FOOD, 'אוכל ושתייה', '%d מקומות, בכפר ועל ההר' % n), tile(P_GAME, 'משחקים', '5 משחקים · גם בלי קליטה', thumbs(['merge', 'descent', 'snowball'])),
                 tile(P_TRK, 'הימים שלי', '3 ימים · 41 ק״מ'), tile(P_GOND, 'רכבלים ומזג אוויר', '11 מתוך 14 פתוחים · <bdi>−7°</bdi>')]
        foot = ('חשבון', 'הגדרות', 'עברית')
    f = ('<div class="foot"><button>%s%s</button><button>%s%s</button><button>%s%s</button></div>'
         % ((av('נ', 's').replace('margin-inline-start:-8px', '') if state == 'trip' and L == 'he' else ic(G3.P_USER, 18)), foot[0], ic(G3.P_GEAR, 18), foot[1], ic(G3.P_GLOBE, 18), foot[2]))
    return ('<div class="usheet glass" role="dialog" aria-label="%s"><div class="grab"></div>%s<div class="tg2">%s</div>%s</div>'
            % ('Menu' if L == 'en' else 'תפריט', trip_tile(state, L), ''.join(tiles), f))


def menu_screen(state='trip', pre=False):
    return mountain(pre) + veil(0, 844, 'rgba(40,62,110,.22)') + menu(state)


def menu_hint():
    text = 'כל השאר נמצא כאן: הטיסה, כרטיס הסקי, הקבוצה, אוכל, משחקים והימים שהקלטת.'
    return root(True) + HN.tile() + HN.ring(274, 775, 104, 54) + HN.bubble(text, 'down', 50, 'right', step=4, right=12, bottom=844 - 763)


# ---------------------------------------------------------------- 3, 4: the trip
def field(k, v, cls=''):
    return '<button class="fl %s" style="text-align: start; align-items: flex-start"><span>%s</span><b class="fv">%s</b></button>' % (cls, k, v)


def pass_top(act=None):
    o = ('<div style="display: flex; align-items: center; gap: 8px; min-height: 32px"><span class="st" style="font-size: 12.5px; font-weight: 700; color: var(--ink2)">הטיול שלי</span>'
         '<span class="tog" style="height: 24px; padding: 0 9px; font-size: 11.5px; font-weight: 700">הלוך</span>'
         '<button class="ed" style="margin-inline-start: auto">%s עריכה</button></div>' % ic(P_PEN, 15, 2))
    o += ('<div style="display: flex; align-items: center; justify-content: space-between"><button class="fl" style="align-items: flex-start"><b class="code">TLV</b><span>תל אביב</span></button>'
          '<span style="color: var(--acc)">%s</span><button class="fl" style="align-items: flex-end"><b class="code">TBS</b><span>טביליסי</span></button></div>' % (PLANE % (26, 26)))
    F = [('תאריך', 'יום א׳ · 10.1.2027', 'date'), ('המראה', '<bdi>15:30</bdi>', 'dep'), ('נחיתה', '<bdi>20:05</bdi>', 'arr'),
         ('נוסע', 'נועה', 'who'), ('טיסה', '<bdi>GD 201</bdi>', 'no'), ('כבודה', '<span style="color: var(--acc)">+ כבודה</span>', 'bag')]
    o += '<div style="display: grid; grid-template-columns: 1.5fr 1fr 1fr; gap: 14px 10px">%s</div>' % ''.join(field(k, v, 'act' if a == act else '') for k, v, a in F)
    return o


STUB = ('<span class="cd" style="font-size: 66px">45</span><span style="display: flex; flex-direction: column; gap: 2px"><b style="font-size: 16px">ימים לטיסה</b>'
        '<span class="sub" style="font-size: 12.5px">4 ימי סקי · <bdi>11–14.1</bdi></span></span>')


def trip(mode='', act=None, light=('sunset', 'night')):
    """the pass in two pieces of paper: the card and the stub. mode: '' | 'tearing' | 'torn'"""
    o = pic('hero', *light) + veil(0, 844, 'var(--veil)') + '<button class="bk glass" aria-label="חזרה">%s</button>' % ic(P_BACK, 20, 2)
    o += ('<div class="passw" style="top: 124px; left: 34px; right: 34px; opacity: .78"><div class="pass" style="height: 150px; -webkit-mask: none; mask: none">'
          '<div style="display: flex; align-items: center; gap: 8px"><span style="font-size: 12.5px; font-weight: 700; color: var(--ink2)">הטיול שלי</span>'
          '<span class="tog" style="height: 24px; padding: 0 9px; font-size: 11.5px; font-weight: 700">חזור · <bdi>15.1</bdi></span></div></div></div>')
    holes = '<div class="holes">%s</div>' % ''.join('<i style="animation-delay: %dms"></i>' % (i * 55) for i in range(12))
    o += ('<div class="passw %s" style="top: 174px"><div class="pass cut">%s</div><div class="stub2"><div class="perf"></div>%s%s</div></div>'
          % (mode, pass_top(act), holes if mode else '', STUB))
    o += ('<div style="position: absolute; left: 20px; right: 20px; top: 656px; display: flex; gap: 8px"><button class="sec glass" style="flex: 1">טיסת החזור</button>'
          '<button class="sec glass" style="flex: 1">כרטיס סקי · 390 %s</button></div>' % LARI)
    return o


def trip_edit():
    o = trip(act='dep')
    o += veil(0, 844, 'rgba(8,16,30,.28)')
    o += ('<div class="usheet glass" style="padding: 10px 16px 16px; gap: 12px"><div class="grab"></div>'
          '<div style="display: flex; align-items: center; min-height: 44px"><span class="ttl">שעת ההמראה</span><span class="sub" style="margin-inline-start: auto">לפי השעון בתל אביב</span></div>'
          '<div class="whl"><span>15:00</span><span>15:15</span><b>15:30</b><span>15:45</span><span>16:00</span></div>'
          '<div style="display: flex; gap: 8px"><button class="sec" style="flex: none">ביטול</button><button class="pri">שמירה</button></div></div>')
    return o


# ---------------------------------------------------------------- 5: the ski pass
MTN_SIL = ('<svg width="232" height="74" viewBox="0 0 232 74" aria-hidden="true"><path d="M0 74V52l34-30 22 20 30-36 30 34 24-18 40 34 52-22v40z" fill="rgba(255,255,255,.22)"/>'
           '<path d="M0 74V62l50-22 34 18 40-30 50 34 58-16v28z" fill="rgba(255,255,255,.18)"/></svg>')


def skipass(days=4, child=False, trip=True):
    p = [x for x in PRICES['passes'] if x['days'] == days][0]
    price = p['child' if child else 'adult']
    daily = PRICES['passes'][0]['child' if child else 'adult'] * days
    o = pic('hero', 'morning', 'night') + veil(0, 844, 'var(--veil)') + '<button class="bk glass" aria-label="חזרה">%s</button><span class="hd">כרטיס סקי</span>' % ic(P_BACK, 20, 2)
    o += ('<svg class="lan" viewBox="0 0 390 160" fill="none" aria-hidden="true"><path d="M150 -6 C150 60 182 112 190 150" stroke="%s" stroke-width="7" stroke-linecap="round"/>'
          '<path d="M262 -6 C258 62 214 112 202 150" stroke="%s" stroke-width="7" stroke-linecap="round" opacity=".82"/></svg>' % (ACC, ACC))
    who = 'ילד 6 עד 12' if child else 'מבוגר'
    dl = 'יום אחד' if days == 1 else ('יומיים' if days == 2 else '%d ימים' % days)
    o += ('<div class="skp"><span class="slot"></span><span class="sk1">גודאורי · עונת <bdi>2026/27</bdi></span>'
          '<span class="sprice">%d<small>%s</small></span><span class="swho">%s · %s</span>%s%s<div class="bot">%s</div></div>'
          % (price, LARI, dl, who, '<span class="sdt"><bdi>11–14.1</bdi> · לפי הטיול שלי</span>' if trip else '',
             ('<span class="save">חוסכים %d %s מול כרטיסי יום</span>' % (daily - price, LARI)) if daily > price else '', MTN_SIL))
    chips = ''.join('<button class="dc %s"><b>%d</b><span>%d %s</span></button>' % ('on' if x['days'] == days else '', x['days'], x['child' if child else 'adult'], LARI)
                    for x in PRICES['passes'] if x['days'] <= 6)
    o += ('<div class="usheet glass" style="gap: 12px; padding: 10px 16px 14px"><div class="grab"></div>'
          '<div class="seg"><button class="%s">מבוגר</button><button class="%s">ילד 6 עד 12</button></div>'
          '<div><p style="font-size: 12.5px; font-weight: 700; color: var(--ink2); margin: 0 2px 6px">כמה ימי סקי?</p><div class="dchips">%s</div></div>'
          '<div><div class="ir"><span class="i">%s</span><span>קונים בקופה שבכפר</span><button class="e l">איפה הקופה? %s</button></div>'
          '<div class="ir"><span class="i">%s</span><span>פיקדון על הכרטיס</span><span class="e">5 %s, מוחזר</span></div>'
          '<div class="ir"><span class="i">%s</span><span>סקי לילה, בנפרד</span><span class="e">80 %s לערב</span></div></div>'
          '<p style="font-size: 11.5px; color: var(--ink2); text-align: center">מחיר לתיירים, כולל מע״מ · <bdi>MTA</bdi> · נבדק <bdi>15.11.2026</bdi></p></div>'
          % (*(('', 'on') if child else ('on', '')), chips, ic(P_PIN, 19), ic(P_CHV, 15, 2.2), ic(P_CARD, 19), LARI, ic(P_MOON, 19), LARI))
    return o


# ---------------------------------------------------------------- 6: food and drink
HEK = {'food': 'מסעדה', 'cafe': 'קפה ובר'}


def places():
    """food and cafes from site/data/places.json: those inside the village picture, and the rest of the mountain"""
    near, liftname = {}, {}
    for s in PL['stations']:
        lift = next((e for e in s['ends'] if e['lift']), None)
        for e in s['ends']:
            if e['lift']: liftname[e['liftId']] = e['lift']
        for q in s['places']:
            if lift and (q['p'] not in near or q['d'] < near[q['p']][0]):
                near[q['p']] = (q['d'], lift['lift'], lift['end'])
    V, M, seen = [], [], set()
    for p in PL['places']:
        if p['kind'] not in HEK or p['name'] in seen:
            continue
        if p['id'] in near:
            d, lift, end = near[p['id']]
        elif p.get('slope') and p.get('liftId') in liftname:
            d, lift, end = None, liftname[p['liftId']], 'slope'
        else:
            continue
        seen.add(p['name'])
        e, n = EN(p['ll'])
        x, y, vis, _ = P.pt(VV, e, n)
        r = dict(name=p['name'], kind=p['kind'], d=d, lift=lift, end=end, seasonal=p.get('seasonal'))
        if d is not None and 8 < x < 382 and 100 < y < 500:
            r.update(x=x, y=y); V.append(r)
        else:
            hx, hy, hv, _ = P.pt(VH, e, n); r.update(x=hx, y=hy); M.append(r)
    V.sort(key=lambda r: r['d']); M.sort(key=lambda r: -r['y'] if False else r['y'])
    return V, M


PLACES_V, PLACES_M = places()
MSHIFT = 150      # the mountain picture moves up under the list, so the pins are above the sheet


def where(r):
    if r['end'] == 'slope':
        return 'על המסלול, ליד <bdi>%s</bdi>' % r['lift']
    if r in PLACES_V:
        return '%d מ׳ מ-<bdi>%s</bdi>' % (round(r['d'] / 10) * 10, r['lift'])
    return 'ליד התחנה %s של <bdi>%s</bdi>' % ('העליונה' if r['end'] == 'top' else 'התחתונה', r['lift'])


def prow(r, sel=False):
    return ('<button class="pl %s"><span class="pi %s">%s</span><span class="c"><b>%s</b><span>%s · %s</span></span>%s</button>'
            % ('sel' if sel else '', r['kind'], ic(P_FOOD if r['kind'] == 'food' else P_CAFE, 19), r['name'], HEK[r['kind']], where(r),
               '<span class="e">%s</span>' % ic(P_CHV, 16, 2)))


def spread(rows, gap=24):
    """pins that fall on each other move apart a little (drawing only)"""
    out = []
    for r in rows:
        x, y = r['x'], r['y']
        for _ in range(12):
            hit = [o for o in out if abs(o[0] - x) < gap and abs(o[1] - y) < gap]
            if not hit:
                break
            x -= gap * .6; y -= gap * .5
        out.append((x, y))
    return out


def food(tab='v', sel=None):
    vill = tab == 'v'
    rows = PLACES_V if vill else PLACES_M
    if sel is not None:
        sel = [r['name'] for r in rows].index(sel)
    if vill:
        o = pic('vill', 'morning', 'night') + base('vill')
        gx, gy, _, _ = P.pt(VV, *P.lift_ends(P.flift('Goodaura'))[0])
        o += tag(gx + 30, gy - 58, 'Goodaura ↑', None)
        dy = 0
    else:
        dy = MSHIFT
        o = ('<div style="position: absolute; left: 0; top: -%dpx; width: 390px; height: 844px">%s</div>' % (dy, pic('hero', 'morning', 'night') + base('hero', cabins=True))
             + '<div style="position: absolute; left: 0; right: 0; top: %dpx; bottom: 0; background: var(--bg)"></div>' % (844 - dy) + veil(0, 844, 'rgba(40,62,110,.14)'))
    pos = [(x, y - dy) for x, y in spread(rows)]
    for i, r in enumerate(rows):
        s = sel is not None and i == sel
        o += ('<span class="pm %s %s" style="left: %.0fpx; top: %.0fpx">%s</span>'
              % (r['kind'], 'sel' if s else '', pos[i][0], pos[i][1], ic(P_FOOD if r['kind'] == 'food' else P_CAFE, 22 if s else 14, 2)))
    if sel is not None:
        r = rows[sel]
        o += '<div class="tag" style="left: %.0fpx; top: %.0fpx; height: 26px; border-radius: 13px; font-size: 12.5px; z-index: 3">%s</div>' % (pos[sel][0], pos[sel][1] - 38, r['name'])
    o += '<button class="bk glass" aria-label="חזרה">%s</button>' % ic(P_BACK, 20, 2)
    seg = ('<div class="seg"><button class="%s">בכפר · %d</button><button class="%s">בשאר ההר · %d</button></div>'
           % ('on' if vill else '', len(PLACES_V), '' if vill else 'on', len(PLACES_M)))
    if sel is not None:
        r = rows[sel]
        rest = [x for x in rows if x is not r][:3]
        body = ('<div style="display: flex; align-items: center; gap: 12px; min-height: 56px"><span class="pl" style="min-height: 0"><span class="pi %s" style="width: 44px; height: 44px">%s</span></span>'
                '<span style="display: flex; flex-direction: column; gap: 1px; min-width: 0"><b style="font-size: 21px; font-weight: 700; direction: ltr; unicode-bidi: isolate; text-align: start; width: max-content">%s</b>'
                '<span class="sub">%s · %s%s</span></span>'
                '<button class="xb" aria-label="סגירה" style="margin-inline-start: auto; width: 44px; height: 44px; display: flex; align-items: center; justify-content: center">%s</button></div>'
                '<div style="display: flex; gap: 8px"><button class="pri" style="display: flex; align-items: center; justify-content: center; gap: 8px">%s ניווט</button>'
                '<button class="sec" style="flex: none">שיתוף</button></div>'
                '<p style="font-size: 12.5px; font-weight: 700; color: var(--ink2); margin-top: 6px">עוד בכפר</p><div style="display: flex; flex-direction: column; overflow: hidden; flex: 1; margin-top: -6px">%s</div>'
                % (r['kind'], ic(P_FOOD if r['kind'] == 'food' else P_CAFE, 23), r['name'], HEK[r['kind']], where(r),
                   ' · פתוח רק בעונה' if r['seasonal'] else '', ic(P_X, 20, 2), ic(P_NAV, 19, 2), ''.join(prow(x) for x in rest)))
        head = ''
    else:
        body = '<div style="display: flex; flex-direction: column; overflow: hidden; flex: 1">%s</div>' % ''.join(prow(r) for r in rows[:5])
        head = '<span class="ttl">אוכל ושתייה</span>' + seg
    h = 452 if vill else 396
    o += '<div class="usheet glass" style="height: %dpx; padding: 10px 16px 0; gap: 10px; overflow: hidden"><div class="grab"></div>%s%s</div>' % (h, head, body)
    return o


# ---------------------------------------------------------------- 7, 8: the group
def gate(code=False):
    o = pic('hero', 'morning', 'night') + base('hero', cabins=True) + veil(0, 844, 'rgba(40,62,110,.22)') + '<button class="bk glass" aria-label="חזרה">%s</button>' % ic(P_BACK, 20, 2)
    v1 = '<span style="display: flex; align-items: center; gap: 6px"><span style="color: var(--acc); display: flex">%s</span>%s</span>' % (PLANE % (22, 22), avs(['נועה', 'איתי', 'דנה']))
    v2 = ('<svg width="76" height="52" viewBox="0 0 76 52" fill="none" aria-hidden="true"><path d="M2 48 26 18l12 14 10-12 26 28z" fill="var(--line)"/>'
          '<path d="M38 32V8" stroke="var(--ink)" stroke-width="2.4" stroke-linecap="round"/><path d="M39 8h24l-5 6 5 6H39z" fill="%s"/></svg>' % ACC)
    v3 = ('<svg width="76" height="52" viewBox="0 0 76 52" aria-hidden="true"><rect x="6" y="28" width="18" height="22" rx="4" fill="var(--line)"/><rect x="29" y="12" width="18" height="38" rx="4" fill="%s"/>'
          '<rect x="52" y="34" width="18" height="16" rx="4" fill="var(--line)"/></svg>' % ACC)
    why = '<div class="why"><div><span class="v">%s</span>מי טס מתי</div><div><span class="v">%s</span>איפה נפגשים</div><div><span class="v">%s</span>מי מוביל במשחקים</div></div>' % (v1, v2, v3)
    if code:
        act = ('<p style="font-size: 14px; font-weight: 700; margin-top: 2px">קוד ההזמנה</p>%s'
               '<div style="display: flex; gap: 8px"><button class="sec" style="flex: none">חזרה</button><button class="pri" style="opacity: .45">הצטרפות</button></div>' % G3.codebox('WNS   ', cur=3))
    else:
        act = ('<button class="pri" style="flex: none; display: flex; align-items: center; justify-content: center; gap: 8px">%s קבוצה חדשה</button>'
               '<button class="sec" style="height: 52px">יש לי קוד הזמנה</button>' % ic(P_PLUS, 19, 2.2))
    o += ('<div class="usheet glass" style="gap: 12px; padding: 10px 16px 16px"><div class="grab"></div>'
          '<div><p class="ttl">נוסעים עם חברים?</p><p class="sub">קבוצה אחת לכל מי שבא לטיול</p></div>%s%s</div>' % (why, act))
    return o


def group(loading=False):
    """the same frame while loading and after: the sheet, the head and the tabs never move"""
    o = pic('hero', 'morning', 'night') + base('hero', cabins=False) + veil(0, 844, 'rgba(40,62,110,.14)') + top()
    sk = lambda w, h, st='': '<span class="sk" style="display: block; width: %dpx; height: %dpx;%s"></span>' % (w, h, st)
    if loading:
        head = ('<div style="display: flex; align-items: center; gap: 10px; height: 48px"><div style="display: flex; flex-direction: column; gap: 7px">%s%s</div>'
                '<span style="margin-inline-start: auto">%s</span></div>' % (sk(150, 22), sk(110, 13), G3.INV.replace('class="sec"', 'class="sec" disabled').replace('display: flex', 'display: flex; opacity: .45')))
        card = ('<div class="glass" style="border-radius: 20px; height: 150px; padding: 14px 16px; box-sizing: border-box; display: flex; flex-direction: column; justify-content: space-between; box-shadow: none">'
                '%s%s%s</div>' % (sk(90, 13), sk(180, 40), sk(200, 14)))
        rows = ''.join('<div class="rl" style="height: 60px">%s<span class="c" style="gap: 7px">%s%s</span></div>' % ('', sk(170, 15), sk(120, 12)) for _ in range(2))
    else:
        head = G3.ghead(G3.INV).replace(G3.EG, '') if hasattr(G3, 'EG') else G3.ghead(G3.INV)
        card = ('<div class="glass" style="border-radius: 20px; height: 150px; padding: 14px 16px; box-sizing: border-box; display: flex; flex-direction: column; justify-content: space-between; box-shadow: none">'
                '<div style="display: flex; font-size: 12.5px; font-weight: 700"><span style="color: var(--ink2)">המפגש הבא</span><span style="margin-inline-start: auto; color: var(--acc)">בעוד שעה ו-12 דק׳</span></div>'
                '<div style="display: flex; align-items: center; gap: 14px"><span class="num" style="font-size: 44px">13:00</span><span style="display: flex; flex-direction: column"><b style="font-size: 17px; direction: ltr; text-align: start">Goodaura</b>'
                '<span class="sub">התחנה העליונה · <bdi>2,705</bdi> מ׳</span></span></div>'
                '<div style="display: flex; align-items: center; gap: 8px; font-size: 13.5px">%s תזכורת רבע שעה לפני<span style="margin-inline-start: auto">%s</span></div></div>' % (ic(G3.P_BELL, 18), G3.sw(True)))
        rows = (G3.rl('', '<bdi>New Goodaura</bdi> · 16:30', 'התחנה התחתונה · קבעה נועה', ic(G3.P_BELL, 20)).replace('class="rl "', 'class="rl" style="height: 60px"')
                + G3.rl('', 'מחר <bdi>Goodaura</bdi> · 09:30', 'התחנה התחתונה · קבעה דנה', ic(G3.P_BELL, 20)).replace('class="rl "', 'class="rl" style="height: 60px"'))
    o += ('<div class="usheet glass" style="top: 254px; gap: 10px; padding: 10px 16px 16px"><div class="grab"></div>%s%s%s<div>%s</div>'
          '<button class="pri" style="flex: none; margin-top: auto;%s">מפגש חדש</button></div>' % (head, G3.tabs(0), card, rows, ' opacity: .45' if loading else ''))
    return o


# ---------------------------------------------------------------- 9: the games
GAMES = [('merge', 'איחוד כדורי שלג', 'שיא 2,048'), ('descent', 'הירידה של החבר׳ה', '3 כוכבים ב-<bdi>Tatra 2</bdi>'), ('school', 'בית הספר לסקי', 'שיעור 4 מתוך 7'),
         ('snowball', 'קרב כדורי שלג', 'שלב 3 מתוך 5'), ('fresh', 'שלג טרי', 'בלי ניקוד')]


def games(first=False):
    o = pic('hero', 'sunset', 'night') + veil(0, 844, 'var(--veil)') + '<button class="bk glass" aria-label="חזרה">%s</button><span class="hd">משחקים</span>' % ic(P_BACK, 20, 2)
    o += ('<span class="tog glass" style="position: absolute; top: 56px; inset-inline-end: 14px; box-shadow: none">גם בלי קליטה</span>')
    G = GAMES
    big = ('<button class="gm big"><img alt="" src="%s"><span class="c"><span style="font-size: 12px; font-weight: 700; color: var(--acc)">%s</span><b style="font-size: 18px">%s</b><span>%s</span>'
           '<span class="cta" style="margin-top: 6px">%s %s</span></span></button>'
           % (TH(G[0][0]), 'מתחילים כאן' if first else 'המשחק האחרון', G[0][1], 'מפתית ועד מלך קזבק' if first else G[0][2], ic(P_PLAY, 14, 2, 'currentColor'), 'לשחק' if first else 'ממשיכים'))
    cards = ''.join('<button class="gm"><img alt="" src="%s"><span class="c"><b>%s</b><span>%s</span></span></button>' % (TH(k), n, 'עוד לא שיחקת' if first else s) for k, n, s in G[1:])
    o += '<div style="position: absolute; left: 14px; right: 14px; top: 112px; display: flex; flex-direction: column; gap: 10px">%s<div class="tg2">%s</div>%s</div>' % (
        big, cards, '' if first else '<button class="sec glass" style="height: 48px">הטבלה של <bdi>גודאורי 2027</bdi></button>')
    return o


# ---------------------------------------------------------------- 10: far from the mountain
def far_card(km='1,430', rot=52):
    return ('<div class="far glass" role="status"><span class="dir"><span style="display: flex; transform: rotate(%ddeg)">%s</span></span>'
            '<span><b>גודאורי · <bdi>%s</bdi> ק״מ מכאן</b><span>המיקום שלך יופיע כשתהיו על ההר</span></span></div>' % (rot, ic(P_NAV, 22, 2), km))


def far_where():
    return root() + far_card()


def far_rec(km='1,430', rot=52):
    o = root() + veil(0, 844, 'rgba(8,16,30,.28)')
    o += ('<div class="usheet glass" style="gap: 12px; padding: 10px 16px 12px"><div class="grab"></div>'
          '<div style="display: flex; align-items: center; gap: 14px; min-height: 64px"><span class="far" style="position: static; padding: 0; min-height: 0"><span class="dir" style="width: 56px; height: 56px"><span style="display: flex; transform: rotate(%ddeg)">%s</span></span></span>'
          '<span><span class="ttl" style="display: block">גודאורי · <bdi>%s</bdi> ק״מ מכאן</span><span class="sub">מקליטים גלישה כשנמצאים על ההר</span></span></div>'
          '<button class="pri" style="flex: none">לראות יום גלישה לדוגמה</button><button class="txb">להקליט בכל זאת</button></div>' % (rot, ic(P_NAV, 28, 2), km))
    return o


def far_near():
    """close, but outside the model: the same card with a short distance; no dot is ever drawn outside the model"""
    return root() + far_card('3.2', -24)


# ---------------------------------------------------------------- the boards
def phones():
    return {
        'MN1.dc.html': ('ההר, עם "תפריט" במקום "הכפר"', root(), None),
        'MN2.dc.html': ('התפריט פתוח: יש טיול ויש קבוצה', menu_screen('trip'), None),
        'MN3.dc.html': ('התפריט בפעם הראשונה: בלי טיול ובלי קבוצה', menu_screen('none', pre=True), None),
        'MN4.dc.html': ('התפריט בלילה', menu_screen('trip'), 'night'),
        'MN5.dc.html': ('Menu, in English', root_en() + veil(0, 844, 'rgba(40,62,110,.22)') + menu('none', 'en'), 'day', 'en'),
        'MN6.dc.html': ('הרמז הרביעי מצביע על "תפריט"', menu_hint(), None),
        'TR1.dc.html': ('הטיול שלי: רואים שאפשר לערוך', trip(), None),
        'TR2.dc.html': ('נגיעה בשדה פותחת את העריכה שלו', trip_edit(), None),
        'TR3.dc.html': ('הספח נתלש (בתנועה)', trip('tearing'), None),
        'TR4.dc.html': ('הספח באמצע התלישה', trip('torn'), 'night'),
        'SP1.dc.html': ('כרטיס סקי: הכרטיס עצמו, והמחיר עליו', skipass(4), None),
        'SP2.dc.html': ('כרטיס סקי: ילד, יום אחד, בלי טיול', skipass(1, child=True, trip=False), 'night'),
        'FD1.dc.html': ('אוכל ושתייה: בכפר', food('v'), None),
        'FD2.dc.html': ('אוכל ושתייה: מקום אחד', food('v', sel='Ice Bar'), None),
        'FD3.dc.html': ('אוכל ושתייה: בשאר ההר', food('m'), None),
        'GR1.dc.html': ('הקבוצה שלי: עוד בלי קבוצה', gate(), None),
        'GR2.dc.html': ('יש לי קוד הזמנה', gate(code=True), None),
        'GR3.dc.html': ('הקבוצה נטענת: אותו מסך, בלי קפיצה', group(loading=True), None),
        'GR4.dc.html': ('הקבוצה נטענה: שום דבר לא זז', group(), None),
        'GA1.dc.html': ('משחקים', games(), None),
        'GA2.dc.html': ('משחקים, בפעם הראשונה ובלילה', games(first=True), 'night'),
        'NM1.dc.html': ('"איפה אני" רחוק מגודאורי', far_where(), None),
        'NM2.dc.html': ('הקלטה רחוק מגודאורי: שואלים לפני', far_rec(), None),
        'NM3.dc.html': ('קרוב, אבל מחוץ להר', far_near(), 'night'),
    }


ROWS = [('חלק 1 · התפריט (סעיפים 1, 2)', ['MN1', 'MN2', 'MN3', 'MN4', 'MN5', 'MN6']),
        ('חלק 2 · הטיול שלי והספח (סעיפים 3, 4)', ['TR1', 'TR2', 'TR3', 'TR4']),
        ('חלק 3 · כרטיס סקי (סעיף 5)', ['SP1', 'SP2']),
        ('חלק 4 · אוכל ושתייה (סעיף 6)', ['FD1', 'FD2', 'FD3']),
        ('חלק 5 · הקבוצה שלי (סעיפים 7, 8)', ['GR1', 'GR2', 'GR3', 'GR4']),
        ('חלק 6 · משחקים (סעיף 9)', ['GA1', 'GA2']),
        ('חלק 7 · רחוק מההר (סעיף 10)', ['NM1', 'NM2', 'NM3'])]
HB = 1880


def ux00():
    tg = lambda t, c='new': '<span class="tg %s">%s</span>' % (c, t)
    causes = li(
        '<b>מקום שעושה עבודה של תפריט.</b> "הכפר" הוא מקום על ההר, אבל בתוכו הטיסה, הקבוצה והמשחקים, שאין להם קשר לכפר. מי שמחפש את הטיסה שלו לא יחפש אותה בכפר. הרמז הרביעי נאלץ למנות במילים את כל מה שיש שם.',
        '<b>התפריט נראה כמו טבלת נתונים.</b> חמש שורות טקסט, בלי סמל, בלי חץ ובלי הבדל ביניהן. המילים האפורות בקצה נקראות כמו ערך, לא כמו יעד. שום דבר לא אומר "כאן נוגעים".',
        '<b>שמות של אווירה במקום שמות של דבר.</b> "החבר׳ה", "ערב בבקתה", "הכפר". אף אחד מהם לא אומר מה נפתח. גם הקול מתחלף: "הטיול שלך" ליד "הימים שלי".',
        '<b>שורה מבטיחה דבר אחד ופותחת אחר.</b> "אוכל ושתייה" פותח את הפאנל של הרכבל <bdi>Goodaura</bdi>, והאוכל שם בשורה קטנה בתחתית. "ערב בבקתה" פותח את עמוד המשחקים של העיצוב הישן.',
        '<b>מה שאפשר לעשות לא נראה.</b> בכרטיס הטיסה כל שדה נערך בנגיעה, אבל שדה מלא נראה כמו דפוס. הספח נתלש, אבל שום דבר לא מזמין לגעת בו, והנייר עצמו לא נקרע (רק הטקסט זז, והכרטיס חותך אותו).',
        '<b>המסך לא אומר את האמת על המצב שלו.</b> הקבוצות נטענות מאוחר, ועד אז כתוב "מצטרפים או פותחים קבוצה" ואז המסך קופץ. רחוק מההר, ההקלטה מתחילה כרגיל והמצלמה טסה לשטח ריק.',
        '<b>שני בתים לדברים האישיים.</b> הקבוצה והטיול בכפר, והקבוצות, הימים שלי והחשבון ב"אני". אין כלל שאפשר לנחש.')
    rule = li('<b>ההר הוא הבית.</b> מסלולים, רכבלים, "איפה אני" והקלטה. בלי שינוי.',
              '<b>כל השאר בתפריט אחד, שקוראים לו "תפריט".</b> כל יעד הוא אריח עם סמל, שם שאומר מה זה, ושורה חיה אחת (כמה ימים לטיסה, מחיר הכרטיס, המפגש הבא).',
              '<b>הכפר חוזר להיות מקום.</b> מה שבאמת נמצא בו: אוכל ושתייה, הקופה, החניה. תמונת הכפר היא המפה של "אוכל ושתייה".',
              '<b>כל שם אומר מה נפתח, וכל מסך פותח את מה שהשם הבטיח.</b>',
              '<b>מה שנערך נראה כמו שדה. מה שנטען שומר על המקום שלו. מה שלא אפשרי נאמר בפשטות.</b>')
    row = lambda *c: '<tr>%s</tr>' % ''.join('<td>%s</td>' % x for x in c)
    items = ('<table style="border-collapse: collapse; width: 100%"><tr><th>#</th><th>מה פיני אמר</th><th>ההמלצה</th><th>לוחות</th><th>מי בונה</th></tr>'
             + row('1', 'הכפר לא מובן כתפריט של כל השאר', 'בסרגל התחתון "תפריט" במקום "הכפר". הכפר נשאר מקום, ומגיעים אליו מ"אוכל ושתייה"', 'MN1, MN6', 'Sonnet')
             + row('2', 'הגיליון לא נראה כמו תפריט', 'גיליון של אריחים: כרטיס הטיסה למעלה, שישה אריחים עם סמל ושורה חיה, ובתחתית חשבון, הגדרות ושפה', 'MN2 עד MN5', 'Sonnet')
             + row('3', 'לא ברור שהכל ניתן לעריכה', '"עריכה" עם עיפרון בתוך הכרטיס, קו מקווקו מתחת לכל ערך, ונגיעה בשדה פותחת את העריכה שלו', 'TR1, TR2', 'Sonnet')
             + row('4', 'הספח: רק צליל, בלי חיתוך', 'הספח הוא חתיכת נייר נפרדת. חורים ננקבים אחד אחרי השני, הספח נקרע מצד אחד ונופל, וחוזר', 'TR3, TR4', 'Sonnet')
             + row('5', 'מחירי הסקי: יותר מדי טקסט, והמחיר על המסך לא יפה', 'הכרטיס עצמו, תלוי על שרוך, והמחיר כתוב עליו. מתחת: מבוגר או ילד, ימים, ושלוש שורות קצרות', 'SP1, SP2', 'Sonnet')
             + row('6', '"אוכל ושתייה" לא פותח אוכל ושתייה', 'רשימה של המקומות בשמם, על תמונת הכפר, ולשונית "בשאר ההר". נגיעה במקום: ניווט', 'FD1 עד FD3', 'Sonnet')
             + row('7', '"החבר׳ה": ניסוח לא טוב, לא ברור מה זה', '"הקבוצה שלי". בלי קבוצה: "נוסעים עם חברים?", שלוש תמונות קטנות של מה מקבלים, ו"קבוצה חדשה" ראשון', 'GR1, GR2', 'Sonnet')
             + row('8', 'רשימת הקבוצות נטענת וקופצת', 'מציגים מיד את מה שנשמר בטלפון. בפעם הראשונה: שלד באותן מידות. הגיליון, הכותרת והלשוניות לא זזים', 'GR3, GR4', 'Sonnet')
             + row('9', '"ערב בבקתה" לא אומר שאלה משחקים', '"משחקים". מסך בשפה החדשה עם התמונות של המשחקים עצמם, והאחרון ששיחקת למעלה', 'GA1, GA2', 'Sonnet')
             + row('10', 'לא על ההר: הסמן משתולל', 'בלי נקודה מחוץ להר, אף פעם. במקומה: "גודאורי · 1,430 ק״מ מכאן" עם חץ לכיוון. הקלטה רחוק מההר שואלת קודם', 'NM1 עד NM3', '<b>Opus</b>')
             + '</table>')
    names = ('<table style="border-collapse: collapse; width: 100%"><tr><th>היום</th><th>מוצע</th><th>למה</th></tr>'
             + row('הכפר (בסרגל)', '<b>תפריט</b>', 'המילה שפיני עצמו השתמש בה')
             + row('הטיול שלך', '<b>הטיול שלי</b>', 'קול אחד, כמו "הימים שלי" ו"הקבוצות שלי"')
             + row('החבר׳ה', '<b>הקבוצה שלי</b>', 'אומר מה זה')
             + row('ערב בבקתה', '<b>משחקים</b>', 'אומר מה זה')
             + row('12 מקומות ליד הרכבל', '<b>%d מקומות, בכפר ועל ההר</b>' % (len(PLACES_V) + len(PLACES_M)), 'כל מה שברשימה')
             + row('לא בגודאורי כרגע', '<b>גודאורי · 1,430 ק״מ מכאן</b>', 'מידע במקום שלילה')
             + '</table>')
    asks = ''.join('<p class="q"><span class="qn">%d</span><span>%s</span></p>' % (i + 1, t) for i, t in enumerate([
        '<b>"תפריט" מחליף את "הכפר" בסרגל התחתון, והכפר נשאר מקום?</b> זה משנה את מה שאושר בסבב 3 ("הכל חי בכפר, בלי תפריט"). (ההמלצה: כן)',
        '<b>השמות החדשים:</b> תפריט, הטיול שלי, הקבוצה שלי, משחקים. (כן; הניסוח הסופי מסשן התוכן)',
        '<b>כרטיס הסקי כחפץ תלוי,</b> בלי המספר הגדול על ההר ובלי פסקאות. (כן)',
        '<b>רחוק מגודאורי:</b> מרחק וכיוון במקום נקודה, והקלטה שואלת קודם ומציעה יום לדוגמה. "להקליט בכל זאת" נשאר, בשביל הבדיקות. (כן)',
        '<b>"אני" נשאר בראש ההר,</b> והחשבון, ההגדרות והשפה גם בתחתית התפריט. (כן; אפשר להוריד את "אני" מההר בהמשך אם התפריט מספיק)']))
    inner = ('<p class="sec">מעבר חוויית משתמש · אחרי גרסה <bdi>0.1.954</bdi> · 26.11.2026 · ממתין לפיני</p>'
             '<h1>האפליקציה יפה, אבל לא אומרת מה יש בה ואיפה. הנה למה, ותיקון אחד לכל העשרה</h1>'
             '<p class="lead">עברתי על כל האפליקציה כמו משתמש חדש, על הלוחות ועל הקוד של אנדרואיד. הבעיה אינה במסך אחד. היא בשבעה דברים שחוזרים בכל מקום, ולכן התיקון הוא כלל אחד ולא עשרה טלאים.</p>'
             '<div class="rowx" style="align-items: stretch"><div class="card" style="flex: 1.25"><h2>למה מתבלבלים</h2>%s</div>'
             '<div style="flex: 1; display: flex; flex-direction: column; gap: 16px"><div class="card"><h2>הכלל</h2>%s</div><div class="card"><h2>השמות</h2>%s</div></div></div>'
             '<div class="card"><h2>עשרת הסעיפים</h2>%s<p class="cap">כל חלק נמסר בנפרד, כדי שמבצע אחר יבנה אותו בסשן משלו. חלק 7 נוגע בשכבת המיקום של המודל החי ובמצלמה של יום הגלישה, ולכן הוא למבצע על Opus. '
             'חלק 1 משנה גם את סדרת הרמזים (הרמז הרביעי, ושני הרמזים שהיו בכפר עוברים לתפריט).</p></div>'
             '<div class="card"><h2>לפיני</h2>%s<p class="cap">המקומות, המחירים והמרחקים בלוחות מהקבצים של האתר. הטיול, הקבוצה והשיאים הם דוגמה. נמדד בכלי העיצוב, לא בטלפון. '
             'המסירה: <span dir="ltr">design/handoff/2026-11-26-redesign-ux-pass/</span></p></div>'
             % (causes, rule, names, items, asks))
    wb('UX00-Diagnosis.dc.html', 'מעבר חוויית משתמש: האבחון וההמלצה', 1280, HB, inner)


def write_canvas(cj):
    j = json.load(open(cj, encoding='utf8'))
    mine = set(['UX00-Diagnosis.dc.html'] + ['%s.dc.html' % c for _, cs in ROWS for c in cs])
    clash = [f for f in j['boards'] if f not in mine and re.match(r'^(UX|MN|TR|SP|FD|GR|GA|NM)\d', f)]
    assert not clash, clash
    for fn in list(j['boards']):
        if fn in mine: del j['boards'][fn]
    j['order'] = [f for f in j['order'] if f not in mine]
    for k in [k for k in j['notes'] if k.startswith('ux')]: del j['notes'][k]
    T = phones()
    place = [('UX00-Diagnosis.dc.html', 0, 0, 1280, HB, 'האבחון, הכלל וההמלצה לכל סעיף')]
    y = HB + 420
    for i, (name, codes) in enumerate(ROWS):
        j['notes']['ux%d' % i] = dict(kind='title1', maxW=2700, page=PAGE, x=0, y=y - 300, text=name)
        for k, c in enumerate(codes):
            place.append(('%s.dc.html' % c, 470 * k, y, 390, 844, T['%s.dc.html' % c][0]))
        y += 844 + 520
    for fn, x, y_, w, h, t in place:
        code = fn.split('.dc.html')[0].split('-')[0]
        j['boards'][fn] = dict(x=x, y=y_, w=w, h=h, title='%s (%s)' % (t, code), page=PAGE)
        j['order'].append(fn)
    if not any(pg['id'] == PAGE for pg in j['pages']):
        j['pages'].insert(0, dict(id=PAGE, name='מעבר חוויית משתמש (26.11)'))
    j['notes']['uxt'] = dict(kind='title1', maxW=1600, page=PAGE, x=0, y=-300, text='מעבר חוויית משתמש: איפה כל דבר, ומה הוא עושה')
    j['launch'] = dict(view='canvas', page=PAGE)
    open(cj, 'w', encoding='utf8').write(json.dumps(j, ensure_ascii=False, indent=1) + '\n')
    return sorted(mine)


def preview(out, assets):
    """plain pages for a local screenshot: the board without the canvas runtime, pictures from a folder of downloaded assets"""
    os.makedirs(out, exist_ok=True)
    loc = {os.path.splitext(f)[0]: os.path.join(assets, f) for f in os.listdir(assets)}
    for k, v in THUMB.items():
        loc[v] = os.path.join(P.ROOT, 'site/games/thumbs', {'fresh': 'fresh-snow'}.get(k, k) + '.webp')
    for fn in ['UX00-Diagnosis.dc.html'] + ['%s.dc.html' % c for _, cs in ROWS for c in cs]:
        s = open(os.path.join(PROJ, fn), encoding='utf8').read()
        s = s.replace('<script src="./support.js"></script>', '').replace('<x-dc>', '').replace('</x-dc>', '').replace('<helmet>', '').replace('</helmet>', '')
        s = re.sub(r'<script type="text/x-dc".*?</script>', '', s, flags=re.S)
        s = re.sub(r'/_blob/([0-9a-f]{32})', lambda m: 'file://' + loc.get(m.group(1), '/missing/' + m.group(1)), s)
        for mode in ('day', 'night'):
            open(os.path.join(out, fn.replace('.dc.html', '-%s.html' % mode)), 'w', encoding='utf8').write(s.replace('{{mode}}', mode))


def main():
    args = [a for a in sys.argv[1:]]
    pv = None
    if '--preview' in args:
        i = args.index('--preview'); pv = (args[i + 1], args[i + 2]); del args[i:i + 3]
    cj = args[0] if args else os.path.join(PROJ, 'canvas.json')
    for fn, v in phones().items():
        title, body, fx = v[:3]; lang = v[3] if len(v) > 3 else 'he'
        PB.phone(fn, title, body, night=fx == 'night', fixed=fx, lang=lang)
    ux00()
    files = write_canvas(cj)
    if pv: preview(*pv)
    print('ok', len(files), cj, 'village', len(PLACES_V), 'mountain', len(PLACES_M))


if __name__ == '__main__':
    main()
