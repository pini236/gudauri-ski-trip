#!/usr/bin/env python3
"""Version 4 of the "מסלולים" button in the bottom bar (27.11.2026, Pini): the mountain glyph of version 3 was the
closest, but the little drawn picture feels cheap and the button as a whole is not good enough. Five genuinely
different buttons, each with its own way of opening into the (unchanged) shelf of version 2, in day and night:
  A the window   the app's own mountain picture inside the button, in the light of the hour
  B the contours the real contour lines around Kudebi, engraved in the glass
  C the numeral  type only: the big light number of the cards, the word, one small marker of the four piste colours
  D the handle   no inner button: the middle of the bar is the lip of the shelf, tap or pull
  E the primary  the one dark, filled button of the bar; "תפריט" turns quiet
No per-run marks on the button, no new strings. A new canvas page "כפתור המסלולים, גרסה 4". Design only.
Handoff: design/handoff/2026-11-27-redesign-run-button-v4/.
Usage: python3 design/redesign/tools/rn_boards.py [canvas.json path, default the repo copy] [--preview <dir> <assets dir>]"""
import os, sys, json, re, base64, math
import numpy as np
from sw_boards import SV, DK, UX, ic, li, PROJ, PB, G4, F, SL, order
from peak_boards import pic, U, VH, tag, HEX
import peak as P
import mtn_boards as MB

PAGE = 'runbtn4'
PAGE_NAME = 'כפתור המסלולים, גרסה 4 (27.11)'
NR = SV.NR
P_UP = DK.P_UP
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '../../..'))
T0 = SL.T0
CUR = SL.CUR

# ------------------------------------------------------------------ B: the real contour lines (site/data/terrain.json)
def _dem():
    t = json.load(open(os.path.join(ROOT, 'site/data/terrain.json')))
    d = t['dem']
    z = np.frombuffer(base64.b64decode(d['b64']), dtype='<i2').reshape(d['ny'], d['nx']).astype(float)
    return z, d, t['peaks']


def _segments(z, lv):
    """marching squares on one level: a list of ((x, y), (x, y)) in cell units"""
    a = z[:-1, :-1]; b = z[:-1, 1:]; c = z[1:, 1:]; d = z[1:, :-1]
    idx = (a > lv) * 1 + (b > lv) * 2 + (c > lv) * 4 + (d > lv) * 8
    out = []
    ys, xs = np.nonzero((idx > 0) & (idx < 15))
    for y, x in zip(ys, xs):
        A, B, C, D = a[y, x], b[y, x], c[y, x], d[y, x]
        def e(n):
            if n == 0: return (x + (lv - A) / (B - A), y)            # top
            if n == 1: return (x + 1, y + (lv - B) / (C - B))        # right
            if n == 2: return (x + (lv - D) / (C - D), y + 1)        # bottom
            return (x, y + (lv - A) / (D - A))                        # left
        k = idx[y, x]
        T = {1: [(3, 0)], 2: [(0, 1)], 3: [(3, 1)], 4: [(1, 2)], 5: [(3, 0), (1, 2)], 6: [(0, 2)], 7: [(3, 2)],
             8: [(2, 3)], 9: [(0, 2)], 10: [(0, 1), (2, 3)], 11: [(1, 2)], 12: [(1, 3)], 13: [(0, 1)], 14: [(3, 0)]}[int(k)]
        for p, q in T: out.append((e(p), e(q)))
    return out


def _chain(segs):
    """join segments into polylines"""
    key = lambda p: (round(p[0], 3), round(p[1], 3))
    ends = {}
    for i, (p, q) in enumerate(segs):
        ends.setdefault(key(p), []).append(i); ends.setdefault(key(q), []).append(i)
    used = [False] * len(segs); lines = []
    for i in range(len(segs)):
        if used[i]: continue
        used[i] = True; line = [segs[i][0], segs[i][1]]
        for rev in (False, True):
            while True:
                tip = line[-1]; nxt = [j for j in ends.get(key(tip), []) if not used[j]]
                if not nxt: break
                j = nxt[0]; used[j] = True; p, q = segs[j]
                line.append(q if key(p) == key(tip) else p)
            line.reverse()
        lines.append(line)
    return lines


def _smooth(line, n=2):
    for _ in range(n):
        if len(line) < 3: break
        closed = line[0] == line[-1]
        o = [] if closed else [line[0]]
        for p, q in zip(line, line[1:]):
            o.append((p[0] * .75 + q[0] * .25, p[1] * .75 + q[1] * .25)); o.append((p[0] * .25 + q[0] * .75, p[1] * .25 + q[1] * .75))
        if closed: o.append(o[0])
        else: o.append(line[-1])
        line = o
    return line


_CONT = {}


def contours(w=260, h=48, peak='Kudebi', px=24, py=22, mpp=13.0, step=50, flip=False):
    """the contour lines around a real peak, as svg paths in a w x h window; the peak sits at (px, py) from the
    left (flip: from the right). mpp: metres per pixel. Lines every `step` m, every fifth one heavier"""
    k = (w, h, peak, px, py, mpp, step, flip)
    if k in _CONT: return _CONT[k]
    z, d, peaks = _dem()
    cell = (d['x1'] - d['x0']) / (d['nx'] - 1)
    pk = [p for p in peaks if p['n'] == peak][0]
    # the true top near the named peak
    cx = (pk['x'] - d['x0']) / cell; cy = (pk['y'] - d['y0']) / cell
    r = 6; y0, x0 = int(cy) - r, int(cx) - r
    sub = z[y0:y0 + 2 * r, x0:x0 + 2 * r]; iy, ix = np.unravel_index(np.argmax(sub), sub.shape)
    cx, cy = x0 + ix, y0 + iy; top = sub.max()
    s = cell / mpp                                           # pixels per cell
    ox = (w - px if flip else px) - cx * s; oy = py - cy * s
    X0 = max(int(-ox / s) - 2, 0); X1 = min(int((w - ox) / s) + 3, d['nx']); Y0 = max(int(-oy / s) - 2, 0); Y1 = min(int((h - oy) / s) + 3, d['ny'])
    win = z[Y0:Y1, X0:X1]
    thin = ''; bold = ''
    lo = int(math.floor(win.min() / step) * step); hi = int(top)
    for lv in range(lo, hi + step, step):
        for line in _chain(_segments(win, lv + .01)):
            if len(line) < 4: continue
            pts = [((x + X0) * s + ox, (y + Y0) * s + oy) for x, y in _smooth(line)]
            dstr = 'M' + ' '.join('%.1f %.1f' % p for p in pts)
            if lv % (step * 5) == 0: bold += dstr
            else: thin += dstr
    _CONT[k] = (thin, bold, int(top))
    return _CONT[k]


# ------------------------------------------------------------------ the buttons
RCSS = """
.H{--rnA:linear-gradient(180deg,#FFFFFF,#EEF4FA);--rnq:rgba(14,34,51,.06);--rnE1:#FFFFFF;--rnE2:#E3ECF6;--rnEg:rgba(30,70,130,.10);--rnEh:rgba(255,255,255,.95);--rnEt:rgba(38,92,170,.34)}
.H .night{--rnA:linear-gradient(180deg,#1C2B47,#121C30);--rnq:rgba(234,241,248,.09);--rnE1:#27385A;--rnE2:#141F36;--rnEg:rgba(4,10,24,.42);--rnEh:rgba(190,212,255,.16);--rnEt:rgba(150,190,255,.5)}
.H .rnbar{position:absolute;left:10px;right:10px;bottom:12px;height:64px;border-radius:32px;display:flex;align-items:center;gap:8px;padding:0 8px;box-sizing:border-box}
.H .rnbar .mn{height:48px;border-radius:24px;background:var(--ink);color:var(--bg);display:flex;align-items:center;gap:7px;padding:0 16px 0 14px;font-size:15.5px;font-weight:700;flex:none}
.H .rnbar .rq{transform:scale(1.09)}
.H .rnb{position:relative;flex:1;min-width:0;height:48px;border-radius:24px;display:flex;align-items:center;gap:8px;box-sizing:border-box;color:var(--ink);text-align:start;overflow:hidden;white-space:nowrap}
.H .rnb .rnw{font-size:17px;font-weight:700;line-height:1;position:relative}
.H .rnb .rnc{font-size:13.5px;font-weight:700;color:var(--ink2);font-variant-numeric:tabular-nums;line-height:1;position:relative}
.H .rnb .rnup{position:relative;display:flex;flex:none;margin-inline-start:auto}
/* A, the window */
.H .rnA{background:var(--rnA);padding-inline-start:18px;box-shadow:0 1px 2px rgba(10,25,45,.12),0 5px 12px rgba(10,25,45,.12)}
.H .rnA .rnph{position:absolute;top:0;bottom:0;inset-inline-end:0;width:132px;overflow:hidden}
.H [dir=rtl] .rnA .rnph{-webkit-mask-image:linear-gradient(to right,#000 46%,transparent 96%);mask-image:linear-gradient(to right,#000 46%,transparent 96%)}
.H [dir=ltr] .rnA .rnph{-webkit-mask-image:linear-gradient(to left,#000 46%,transparent 96%);mask-image:linear-gradient(to left,#000 46%,transparent 96%)}
.H .rnA .rnph img{position:absolute;max-width:none}
.H .rnA::after{content:"";position:absolute;inset:0;border-radius:inherit;pointer-events:none;box-shadow:inset 0 0 0 1px rgba(14,34,51,.10),inset 0 1.5px 0 rgba(255,255,255,.9),inset 0 -10px 12px -10px rgba(14,34,51,.16)}
.H .night .rnA::after{box-shadow:inset 0 0 0 1px rgba(234,241,248,.16),inset 0 1.5px 0 rgba(255,255,255,.16),inset 0 -10px 12px -10px rgba(0,0,0,.4)}
.H .rnA .rnph img.n{filter:brightness(1.7) saturate(1.1)}
.H .rnA .rnup{color:var(--ink);margin-inline-end:12px;filter:drop-shadow(0 0 2px var(--halo))}
/* B, the contours */
.H .rnB{background:var(--tile);border:1px solid var(--line);padding-inline-start:18px}
.H .rnB .rnct{position:absolute;top:0;inset-inline-end:0;color:var(--ink)}
.H [dir=rtl] .rnB .rnct{-webkit-mask-image:linear-gradient(to right,#000 34%,transparent 70%);mask-image:linear-gradient(to right,#000 34%,transparent 70%)}
.H [dir=ltr] .rnB .rnct{-webkit-mask-image:linear-gradient(to left,#000 34%,transparent 70%);mask-image:linear-gradient(to left,#000 34%,transparent 70%)}
.H .rnB .rnup{margin-inline-end:11px;width:26px;height:26px;align-items:center;justify-content:center;color:var(--acc)}
/* C, the numeral */
.H .rnC{background:var(--rnq);padding-inline:16px 12px;gap:9px}
.H .rnC .rnn{font-size:29px;font-weight:300;line-height:1;letter-spacing:-.02em;font-variant-numeric:tabular-nums;direction:ltr}
.H .rnC .rnmk{display:flex;flex-direction:column;flex:none;border-radius:2.5px;overflow:hidden}
.H .rnC .rnmk i{width:5px;height:6.5px}
.H .rnC .rnup{color:var(--ink2)}
/* D, the handle */
.H .rnD{height:64px;border-radius:0;flex-direction:column;justify-content:center;gap:7px;overflow:visible}
.H .rnD .rngr{width:38px;height:5px;border-radius:3px;background:var(--ink);opacity:.26}
.H .rnD .rnl{display:flex;align-items:baseline;gap:7px}
/* E, the primary */
.H .rnE{background:var(--ink);color:var(--bg);padding-inline:20px 13px;box-shadow:0 1px 2px rgba(6,16,30,.3),0 6px 16px rgba(6,16,30,.28)}
.H .rnE::after{content:"";position:absolute;inset:0;border-radius:inherit;pointer-events:none;box-shadow:inset 0 1.5px 0 rgba(255,255,255,.16)}
.H .night .rnE::after{box-shadow:inset 0 -1.5px 0 rgba(10,25,45,.12)}
.H .rnE .rnc{color:inherit;opacity:.7}
.H .rnE .rnup{width:28px;height:28px;border-radius:50%;background:var(--acc);color:var(--onacc);align-items:center;justify-content:center}
.H .rnbar .mn.rnq{background:var(--rnq);color:var(--ink)}
"""
G4.MB.CSS = G4.MB.CSS + RCSS


def word(L):
    return ('Runs', 'Menu') if L == 'en' else ('מסלולים', 'תפריט')


def up(s=18, w=2.4):
    return '<span class="rnup">%s</span>' % ic(P_UP, s, w)


# crop of the hero picture for the window: centre (1x pixels of the view) and scale
CROP = {'he': dict(cx=128, cy=172, s=.5), 'en': dict(cx=262, cy=176, s=.5)}


def photo(L='he'):
    V = VH; C = CROP['en' if L == 'en' else 'he']; s = C['s']; w = 132; h = 48
    left = w / 2 - C['cx'] * s; topp = h / 2 - C['cy'] * s
    st = 'left: %.1fpx; top: %.1fpx; width: %.1fpx; height: %.1fpx' % (left, topp, V['W'] * s, V['H'] * s)
    return ('<span class="rnph" aria-hidden="true"><img class="d" alt="" src="%s" style="%s"><img class="n" alt="" src="%s" style="%s"></span>'
            % (U('hero-morning'), st, U('hero-night'), st))


def btn(kind, n=NR, L='he', narrow=False):
    w, _ = word(L)
    lab = '%s, %d' % (w, n)
    cnt = '' if narrow else '<span class="rnc">%d</span>' % n
    if kind == 'A':
        return '<button class="rnb rnA" aria-label="%s">%s<span class="rnw">%s</span>%s%s</button>' % (lab, photo(L), w, cnt, up())
    if kind == 'B':
        W = 170; thin, bold, top = contours(W, 48, px=24, py=24, mpp=21.0, step=50, flip=(L == 'en'))
        svg = ('<svg class="rnct" width="%d" height="48" viewBox="0 0 %d 48" fill="none" stroke="currentColor" stroke-linejoin="round" stroke-linecap="round" aria-hidden="true">'
               '<path d="%s" stroke-width=".8" opacity=".24"/><path d="%s" stroke-width="1.1" opacity=".42"/></svg>' % (W, W, thin, bold))
        return '<button class="rnb rnB" aria-label="%s">%s<span class="rnw">%s</span>%s%s</button>' % (lab, svg, w, cnt, up(18, 2.6))
    if kind == 'C':
        mk = '<span class="rnmk" aria-hidden="true">%s</span>' % ''.join('<i style="background: var(--%s)"></i>' % c for c in ('black', 'red', 'blue', 'green'))
        return ('<button class="rnb rnC" aria-label="%s">%s<span class="rnn">%d</span><span class="rnw">%s</span>%s</button>'
                % (lab, mk, n, w, up()))
    if kind == 'D':
        return ('<button class="rnb rnD" aria-label="%s"><span class="rngr" aria-hidden="true"></span><span class="rnl"><span class="rnw">%s</span>%s</span></button>'
                % (lab, w, cnt))
    if kind == 'E':
        return '<button class="rnb rnE" aria-label="%s"><span class="rnw">%s</span>%s%s</button>' % (lab, w, cnt, up(16, 2.8))


# ------------------------------------------------------------------ pressed and narrow states, motion
LOOP = 7.2      # seconds; 1% = 72 ms, so the loops play at the real speed


def kf(s, p0, o0, o1, ease, c0=69.0, c1=73.5):
    """the keyframes of one loop: press at p0, open from o0 to o1, close from c0 to c1 (percent of the loop)"""
    R = 'left:var(--bl);right:var(--br);bottom:var(--bb);height:var(--bh);border-radius:var(--brd)'
    O = 'left:12px;right:12px;bottom:246px;height:88px;border-radius:22px'
    d = o1 - o0; back = 'cubic-bezier(.3,0,.2,1)'
    return '\n'.join([
        '@keyframes rnF%s{0%%,%g%%{%s;opacity:1;animation-timing-function:%s}%g%%{%s;opacity:1}%g%%,%g%%{%s;opacity:0}%g%%{%s;opacity:1;animation-timing-function:%s}%g%%,100%%{%s;opacity:1}}'
        % (s, o0, R, ease, o1, O, o1 + 1, c0, O, c0 + .5, O, back, c1, R),
        '@keyframes rnP%s{0%%,%g%%{transform:scale(1)}%g%%{transform:scale(.965)}%g%%,100%%{transform:scale(1)}}' % (s, p0, (p0 + o0) / 2, o0 + .7),
        '@keyframes rnFg%s{0%%,%g%%{opacity:0}%g%%,%g%%{opacity:1}%g%%,100%%{opacity:0}}' % (s, o0, o0 + d * .55, c0 + 1, c1 - .5),
        '@keyframes rnFs%s{0%%,%g%%{opacity:1}%g%%,%g%%{opacity:0}%g%%,100%%{opacity:1}}' % (s, o0, o0 + d * .6, c0 + 2, c1),
        '@keyframes rnM%s{0%%,%g%%{transform:translateY(120px) scale(1);animation-timing-function:%s}%g%%,%g%%{transform:translateY(0) scale(1.1422);animation-timing-function:%s}%g%%,100%%{transform:translateY(120px) scale(1)}}'
        % (s, o0, ease, o1, c0, back, c1),
        '@keyframes rnOc%s{0%%,%g%%{opacity:1}%g%%,%g%%{opacity:0}%g%%,100%%{opacity:1}}' % (s, o0, o0 + d * .6, c0, c1),
        '@keyframes rnOo%s{0%%,%g%%{opacity:0}%g%%,%g%%{opacity:1}%g%%,100%%{opacity:0}}' % (s, o0 + d * .3, o1, c0, c1 - 1),
        '@keyframes rnU%s{0%%,%g%%{opacity:1;transform:none}%g%%,%g%%{opacity:0;transform:translateY(10px)}%g%%,100%%{opacity:1;transform:none}}' % (s, o0, o0 + 2, c1 - 2, c1),
        '@keyframes rnS%s{0%%,%g%%{opacity:0}%g%%,%g%%{opacity:1}%g%%,100%%{opacity:0}}' % (s, o0 + d * .65, o1 + .5, c0, c0 + .5),
        '@keyframes rnTk%s{0%%,%g%%{transform:scaleY(.1)}%g%%,100%%{transform:scaleY(1)}}' % (s, o0 + d * .6, o1 + 2),
        '@keyframes rnCh%s{0%%,%g%%{opacity:0;transform:translateY(14px);animation-timing-function:%s}%g%%,%g%%{opacity:1;transform:none}%g%%,100%%{opacity:0;transform:translateY(14px)}}' % (s, o0 + 2, ease, o1 + .5, c0, c0 + 2),
        '@keyframes rnK%s{0%%,%g%%{opacity:0;transform:translateY(46px);animation-timing-function:%s}%g%%,%g%%{opacity:1;transform:none;animation-timing-function:cubic-bezier(.4,0,1,1)}%g%%,100%%{opacity:0;transform:translateY(34px)}}'
        % (s, o0 + 2.2, ease, o1 + 1.6, c0, c0 + 2.2),
        ('@keyframes rnT%s{0%%,%g%%{opacity:0;transform:translate(-50%%,50%%) scale(.6);bottom:44px}%g%%,%g%%{opacity:1;transform:translate(-50%%,50%%) scale(1);bottom:44px}%g%%,100%%{opacity:0;transform:translate(-50%%,50%%) scale(1.25);bottom:44px}}'
         % (s, p0 - 2.5, p0, o0, o0 + 1.6)) if s == '' else
        ('@keyframes rnT%s{0%%,%g%%{opacity:0;transform:translate(-50%%,50%%) scale(.6);bottom:44px}%g%%,%g%%{opacity:1;transform:translate(-50%%,50%%) scale(1);bottom:44px}%g%%{opacity:1;transform:translate(-50%%,50%%) scale(1);bottom:290px}%g%%,100%%{opacity:0;transform:translate(-50%%,50%%) scale(1.25);bottom:290px}}'
         % (s, p0 - 2.5, p0, o0, o1, o1 + 1.6)),
        '@keyframes rnIn%s{0%%,%g%%{transform:scale(1);opacity:1}%g%%,%g%%{transform:scale(2.1);opacity:0}%g%%,100%%{transform:scale(1);opacity:1}}' % (s, o0, o0 + d * .7, c0 + 2, c1)])


EASE = 'cubic-bezier(.2,.8,.2,1)'
ANIM = ('F', 'Fg', 'Fs', 'M', 'Oc', 'Oo', 'U', 'S', 'Tk', 'Ch', 'K', 'T')
MCSS = kf('', 22, 24, 30, EASE) + '\n' + kf('d', 19, 21, 29.5, 'cubic-bezier(.35,.1,.25,1)') + """
.H .rnmo .rnM{position:absolute;left:0;top:0;width:390px;height:844px;transform-origin:50% -120px}
.H .rnmo .rnL{position:absolute;inset:0}
.H .rnmo .rnF{position:absolute;z-index:4}
.H .rnmo .rnFg{position:absolute;inset:0;border-radius:inherit}
.H .rnmo .rnFs{position:absolute;inset:0;border-radius:inherit}
.H .rnmo .rnFs>.rnb{width:100%;height:100%;border-radius:inherit}
.H .rnmo .rnFs>.rnbar{position:absolute;left:0;right:0;top:0;bottom:0;height:auto;border-radius:inherit}
.H .rnmo .tk2{transform-origin:50% 100%}
.H .rnmo .rnT{position:absolute;width:46px;height:46px;border-radius:50%;background:rgba(14,34,51,.2);box-shadow:0 0 0 2px rgba(255,255,255,.85),0 0 0 3px rgba(14,34,51,.25);z-index:6;opacity:0}
.H .night .rnmo .rnT{background:rgba(234,241,248,.3)}
.H .rnmo .rnph img,.H .rnmo .rnct{transform-origin:22px 30px}
""" + ''.join('.H .rnmo .rn%s{animation:rn%s %gs infinite}.H .rnmo.rnmd .rn%s{animation-name:rn%sd}\n' % (a, a, LOOP, a, a) for a in ANIM) + """
.H .rnmo .rnF{animation:rnF LOOPs infinite,rnP LOOPs infinite}.H .rnmo.rnmd .rnF{animation-name:rnFd,rnPd}
.H .rnmo .rnph img,.H .rnmo .rnct,.H .rnmo .rnmk{animation:rnIn LOOPs infinite}
/* pressed, as a still */
.H .rnb.rnpr{transform:scale(.965)}
.H .rnA.rnpr{box-shadow:0 1px 2px rgba(10,25,45,.14)}
.H .rnA.rnpr .rnph{width:150px}.H .rnA.rnpr .rnph img{transform:scale(1.1);transform-origin:22px 30px}
.H .rnB.rnpr{background:var(--rnq)}.H .rnB.rnpr .rnct{color:var(--acc)}.H .rnB.rnpr .rnct path{opacity:.6}
.H .rnC.rnpr{background:var(--line)}
.H .rnD.rnpr{transform:translateY(-3px)}.H .rnD.rnpr .rngr{opacity:.55;width:46px}
.H .rnE.rnpr{box-shadow:0 1px 2px rgba(6,16,30,.3)}.H .rnE.rnpr::before{content:"";position:absolute;inset:0;background:rgba(255,255,255,.12)}
/* narrow: recording with the clock, large text */
.H .rnA.rnnr{padding-inline-start:15px}.H .rnA.rnnr .rnph{width:84px}.H .rnA.rnnr .rnph img{margin-left:-10px}.H .rnA.rnnr .rnup{margin-inline-end:8px}
.H .rnB.rnnr{padding-inline-start:15px}.H .rnB.rnnr .rnup{margin-inline-end:5px}
.H .rnC.rnnr{padding-inline:12px 6px;gap:7px}.H .rnC.rnnr .rnn{font-size:25px}
.H .rnE.rnnr{padding-inline:15px 9px}
.H .rnb.rnnr .rnw{font-size:16px}
/* the close-up board */
.H .rnx{padding:44px 48px;box-sizing:border-box;display:flex;flex-direction:column;gap:30px;color:#10161D}
.H .rnx h1{font-size:34px;font-weight:900;line-height:1.15}
.H .rnx .rnrow{display:flex;flex-direction:column;gap:10px}
.H .rnx .rnhd{display:flex;align-items:baseline;gap:12px;font-size:15px;line-height:1.4;color:#3C4753}
.H .rnx .rnhd b{font-size:22px;font-weight:700;color:#10161D;white-space:nowrap}
.H .rnx .rnhd .rnlt{width:30px;height:30px;border-radius:50%;background:#10161D;color:#fff;font-size:16px;font-weight:700;display:inline-flex;align-items:center;justify-content:center;flex:none;align-self:center}
.H .rnx .rnhd .rnrec{height:24px;padding:0 10px;border-radius:12px;background:#DDF2E5;color:#0F5C31;font-size:12.5px;font-weight:700;display:inline-flex;align-items:center;align-self:center}
.H .rnx .rnbx{display:flex;gap:14px}
.H .rnx .rncp{font-size:12.5px;font-weight:500;color:#3C4753;margin-top:5px}
"""
G4.MB.CSS = G4.MB.CSS + MCSS.replace('LOOPs', '%gs' % LOOP)

KINDS = [('A', 'החלון', 'התמונה האמיתית של ההר בתוך הכפתור, באור של השעה'),
         ('B', 'קווי הגובה', 'קווי הגובה האמיתיים של פסגת Kudebi, חרוטים בזכוכית'),
         ('C', 'המספר', 'רק אותיות: המספר הגדול והדק של הכרטיסים, המילה, וסימון קטן בארבעת צבעי המסלולים'),
         ('D', 'הידית', 'אין כפתור פנימי: אמצע הסרגל הוא השפה של המדף. נוגעים או מושכים למעלה'),
         ('E', 'הראשי', 'כפתור אחד כהה ומלא באמצע; "תפריט" נעשה שקט')]
REC = 'A'


def btn2(kind, cls='', **kw):
    b = btn(kind, **kw)
    return b.replace('class="rnb ', 'class="rnb %s ' % cls, 1) if cls else b


def bar2(kind, cls='', n=NR, L='he', rec=None):
    _, m = word(L)
    nar = rec is not None
    c = (cls + (' rnnr' if nar else '')).strip()
    return ('<div class="rnbar glass"><button class="mn%s">%s %s</button>%s%s</div>'
            % (' rnq' if kind == 'E' else '', ic(UX.P_GRID, 19, 2), m, btn2(kind, c, n=n, L=L, narrow=nar), rec if rec is not None else DK.rc()))


def rest(kind, rec=None):
    return UX.mountain() + SL.column(layers=False) + bar2(kind, rec=rec)


def motion(kind):
    """the loop: rest, a touch, the button opens into the shelf of version 2 ("לפי רכבל"), holds, closes"""
    K = order('lift')[0]; i = K.index(CUR)
    R = P.run_path(VH, CUR); x, y = P.at(R, R['cum'][-1] * .5)[:2]
    name = tag(x - 44, y + T0, CUR, HEX[F[CUR]['color']])
    closed = '<div style="position: absolute; left: 0; top: %dpx; width: 390px; height: 844px">%s</div>' % (T0, PB.base('hero', cabins=True))
    o = '<div class="rnM">%s<div class="rnL rnOc">%s</div><div class="rnL rnOo">%s%s</div></div>' % (pic('hero', top=T0), closed, SV.mount('lift'), name)
    o += SV.top()
    o += '<div class="rnL rnU">%s</div>' % SL.column(layers=False)
    mn = '<button class="mn%s">%s תפריט</button>' % (' rnq' if kind == 'E' else '', ic(UX.P_GRID, 19, 2))
    if kind == 'D':
        surf = '<div class="rnbar glass" style="box-shadow: none; border: 0; background: none"><span class="rnU" style="display: flex">%s</span>%s<span class="rnU" style="display: flex">%s</span></div>' % (mn, btn('D'), DK.rc())
        rect = '--bl: 10px; --br: 10px; --bb: 12px; --bh: 64px; --brd: 32px'
        o += '<div class="rnF" style="%s"><div class="rnFg glass" style="opacity: 1; animation: none"></div><div class="rnFs">%s</div></div>' % (rect, surf)
        dot = '<span class="rnT" style="left: 167px"></span>'
    else:
        o += '<div class="rnL rnU"><div class="rnbar glass">%s<span style="flex: 1"></span>%s</div></div>' % (mn, DK.rc())
        rect = '--bl: 71px; --br: 126px; --bb: 20px; --bh: 48px; --brd: 24px'
        o += '<div class="rnF" style="%s"><div class="rnFg glass"></div><div class="rnFs">%s</div></div>' % (rect, btn(kind))
        dot = '<span class="rnT" style="left: 167px"></span>'
    o += '<div class="rnL rnS"><div class="sp2 glass">%s%s</div></div>' % (SV.label2('lift'), SV.strip2('lift').replace('class="tk2"', 'class="tk2 rnTk"'))
    o += '<div class="rnL rnCh">%s</div>' % SV.chips2('lift')
    for j, (n, left) in enumerate(((i, 79), (i - 1, 330), (i + 1, -172))):
        if 0 <= n < len(K): o += '<div class="rnL rnK" style="animation-delay: %dms">%s</div>' % (45 * j, SV.card2('lift', K[n], left, n == i))
    return '<div class="rnL rnmo%s">%s%s</div>' % (' rnmd' if kind == 'D' else '', o, dot)


def phones():
    T = {}
    for k, (kind, name, _) in enumerate(KINDS, 1):
        he = 'אבגדה'[k - 1]
        T['RN%d1.dc.html' % k] = ('%s · %s: ביום' % (he, name), rest(kind), 'day')
        T['RN%d2.dc.html' % k] = ('%s · %s: בלילה, בזמן הקלטה' % (he, name), rest(kind, DK.rc('live')), 'night')
        T['RN%d3.dc.html' % k] = ('%s · %s: בתנועה, הכפתור נפתח אל המדף' % (he, name), motion(kind), None)
    return T


# ------------------------------------------------------------------ the close-ups (one wide board, day and night side by side)
def box(inner, mode='day', L='he', scale=1.0):
    w, h = 390, 92
    return ('<div style="width: %dpx; height: %dpx; position: relative; overflow: hidden; border-radius: %dpx; flex: none">'
            '<div class="%s" style="position: absolute; left: 0; top: 0; width: %dpx; height: %dpx; transform-origin: 0 0; transform: scale(%g)">'
            '<div class="scr" dir="%s">%s%s</div></div></div>'
            % (round(w * scale), round(h * scale), 18, mode, w, h, scale, 'ltr' if L == 'en' else 'rtl', pic('hero', 'morning', 'night', top=h - 844), inner))


HX = 1800


def closeups():
    rows = ''
    for k, (kind, name, what) in enumerate(KINDS):
        he = 'אבגדה'[k]
        big = '<div class="rnbx">%s%s</div>' % (box(bar2(kind), 'day', scale=1.5), box(bar2(kind), 'night', scale=1.5))
        small = ('<div class="rnbx"><div>%s<p class="rncp">בנגיעה</p></div><div>%s<p class="rncp">בזמן הקלטה, בלילה</p></div><div>%s<p class="rncp">באנגלית, באתר עם 120 מסלולים</p></div></div>'
                 % (box(bar2(kind, 'rnpr')), box(bar2(kind, rec=DK.rc('live')), 'night'), box(bar2(kind, n=120, L='en'), 'day', 'en')))
        rows += ('<div class="rnrow"><p class="rnhd"><span class="rnlt">%s</span><b>%s</b><span>%s</span>%s</p>%s%s</div>'
                 % (he, name, what, '<span class="rnrec">ההמלצה</span>' if kind == REC else '', big, small))
    return '<div class="rnx"><h1>חמשת הכפתורים מקרוב: יום ולילה, ושלושה מצבים</h1>%s</div>' % rows


def write_closeups():
    fn = 'RN01-Close.dc.html'
    MB.phone(fn, 'כפתור המסלולים, גרסה 4: מקרוב', closeups(), w=1280, h=HX)
    p = os.path.join(PROJ, fn); s = open(p, encoding='utf8').read()
    s = s.replace('<div class="{{mode}}">', '<div class="day">').replace('<div class="scr" dir="rtl">\n<div class="rnx">', '<div class="scr" dir="rtl" style="background: #F6F7F9">\n<div class="rnx">')
    open(p, 'w', encoding='utf8').write(s)


# ------------------------------------------------------------------ the summary board
H0 = 1560


def rn00():
    from v56_boards import wb
    cheap = li('<b>ציור במקום ההר.</b> משולש לבן עם שלושה קווים צבעוניים, בסגנון של אייקון. מאחוריו עומד ההר האמיתי, והפער בולט.',
               '<b>מסגרת בתוך מסגרת.</b> תמונה בעיגול, בתוך כפתור, בתוך סרגל, ולכל אחד קו מתאר משלו.',
               '<b>ארבעה דברים בכפתור אחד:</b> ציור, מילה, מספר, ועיגול אפור עם חץ שנראה כמו עוד כפתור.',
               '<b>הכפתור הכי חשוב היה הכי חלש:</b> לבן על לבן, בין כפתור שחור לכפתור אדום.')
    fixed = li('<b>אין ציורים.</b> מה שרואים הוא אמיתי: התמונה של ההר מהאפליקציה, קווי הגובה מהנתונים, או רק אותיות.',
               '<b>רעיון אחד לכל כפתור,</b> ובלי קווי מתאר. העומק בא מאור ומצל.',
               '<b>החץ הוא חץ,</b> בלי עיגול אפור מסביב.',
               '<b>אין סימן לכל מסלול.</b> הכפתור זהה ב-28 וב-120 מסלולים, ורק המספר מתחלף.')
    opts = [
        ('א', 'החלון', True, 'RN11 עד RN13',
         ['בקצה הכפתור רואים את ההר עצמו: התמונה של האפליקציה, חתוכה על הפסגה, ונמסה אל תוך הכפתור. בלי עיגול ובלי מסגרת.',
          '<b>האור של השעה:</b> בבוקר שמיים בהירים, בשקיעה ורוד, בלילה כוכבים. שבע התמונות כבר קיימות.',
          '<b>נפתח:</b> הכפתור גדל והופך ללוח של המדף, והתמונה מתקרבת ונמסה אל ההר.',
          '<b>בעד:</b> זה הכיוון שהיה הכי קרוב, בלי הזול. מעורר תיאבון, ושייך רק לאפליקציה הזאת.',
          '<b>נגד:</b> הכי הרבה פרטים מהחמש. באתר שאין לו תמונה חוזרים ל-ג.']),
        ('ב', 'קווי הגובה', False, 'RN21 עד RN23',
         ['קווי הגובה האמיתיים סביב פסגת <bdi>Kudebi</bdi>, בקו דק ובצבע אחד. החץ יושב בדיוק על הפסגה.',
          '<b>נפתח:</b> הקווים מתרחבים מהפסגה החוצה, כמו התקרבות במפה.',
          '<b>בעד:</b> מראה של מפה רצינית. אמיתי, ובלי אף צבע.',
          '<b>נגד:</b> מי שלא מכיר מפות טופוגרפיות יראה קישוט.']),
        ('ג', 'המספר', False, 'RN31 עד RN33',
         ['28 גדול ודק, כמו המספרים בכרטיסי המדף, ולידו "מסלולים". בצד עמוד קטן בארבעת צבעי המסלולים.',
          '<b>נפתח:</b> הכפתור גדל ללוח של המדף, והעמוד נפרש לפס המסלולים.',
          '<b>בעד:</b> הכי נקי וקריא. עובד בכל אתר ובכל שפה.',
          '<b>נגד:</b> הכי פחות חוויה.']),
        ('ד', 'הידית', False, 'RN41 עד RN43',
         ['בלי כפתור פנימי. באמצע הסרגל יש ידית קטנה, המילה והמספר. הסרגל הוא השפה של המדף.',
          '<b>נפתח:</b> בנגיעה, או במשיכה למעלה. המדף עולה עם האצבע, וההר עולה איתו.',
          '<b>בעד:</b> הכי שקט, והתנועה הכי טבעית. מוכר מאפליקציות מפות.',
          '<b>נגד:</b> פחות נראה כמו כפתור. משיכה מתחתית המסך קרובה למחוות הבית של אנדרואיד.']),
        ('ה', 'הראשי', False, 'RN51 עד RN53',
         ['"מסלולים" הוא הכפתור הכהה והמלא של הסרגל, עם חץ בעיגול סגול. "תפריט" נעשה בהיר ושקט.',
          '<b>נפתח:</b> הכפתור הכהה גדל, ומתבהר ללוח של המדף.',
          '<b>בעד:</b> ברור מיד מה הפעולה הראשית במסך.',
          '<b>נגד:</b> משנה את המראה של "תפריט" שאושר, ואין בו שום דבר מההר.'])]

    def oc(o):
        he, name, rec, codes, items = o
        return ('<div class="card" style="flex: 1%s">%s<h2 style="margin-top: %dpx">%s · %s</h2>%s<p class="cap" dir="rtl">הלוחות: <span dir="ltr">%s</span></p></div>'
                % ('; border: 3px solid #6B3FD4' if rec else '', '<span style="align-self: flex-start; display: inline-flex; align-items: center; height: 24px; padding: 0 10px; border-radius: 12px; background: #DDF2E5; color: #0F5C31; font-size: 12.500px; font-weight: 700">ההמלצה</span>' if rec else '', 0, he, name, li(*items), codes))
    why = ('<p style="font-size: 16px; line-height: 1.55">התמונה בכפתור הייתה הכי קרובה, ומה שהפריע בה היה שהיא ציור. ב-א זה ההר עצמו, באותו אור שעל המסך, '
           'והכפתור משתנה עם השעה. אם תמונה בכפתור עדיין מרגישה עמוסה, הבחירה השנייה שלי היא ג.</p>')
    asks = ''.join('<p class="q"><span class="qn">%d</span><span>%s</span></p>' % (i + 1, t) for i, t in enumerate([
        '<b>איזה כפתור?</b> (ההמלצה: א, החלון)',
        '<b>ב-א: התמונה מתחלפת לפי השעה,</b> כמו ההר? (כן)',
        '<b>המספר נשאר על הכפתור?</b> (כן; בזמן הקלטה הוא יורד ראשון)']))
    keep = li('<b>המדף של גרסה 2 לא השתנה:</b> הצ׳יפים, המיון, הפס והכרטיסים. (SV3 עד SV6)',
              '<b>אין טקסט חדש.</b> המילה "מסלולים" והמספר, כמו שאושרו. על הכפתור אין שורת הסבר.',
              '<b>הסרגל:</b> גובה 64, כפתורים 48. "תפריט" וההקלטה כמו היום (חוץ מ-ה).')
    motion_ = li('<b>פתיחה, 420 מ״ש:</b> הכפתור עצמו גדל והופך ללוח העליון של המדף; ההר עולה 120; הצ׳יפים והכרטיסים עולים אחריו, אחד אחרי השני.',
                 '<b>סגירה, 280 מ״ש:</b> הפוך, בלי השהיות.',
                 '<b>בנגיעה:</b> הכפתור שוקע מעט, ורטט קל בשחרור.',
                 '<b>הפחתת תנועה:</b> התמוססות של 150 מ״ש, בלי תזוזה.',
                 'הלוחות שנגמרים ב-3 מנגנים את התנועה במהירות האמיתית, בלולאה.')
    inner = ('<p class="sec">בוחרים מסלול · גרסה 4: הכפתור · 27.11.2026 · ממתין לפיני</p>'
             '<h1>כפתור "מסלולים": חמש אפשרויות שונות באמת</h1>'
             '<div class="rowx" style="align-items: stretch"><div class="card" style="flex: 1"><h2>מה היה זול בתמונה שבכפתור</h2>%s</div>'
             '<div class="card" style="flex: 1"><h2>מה תוקן, בכל החמש</h2>%s</div></div>'
             '<div class="rowx" style="align-items: stretch">%s%s%s</div>'
             '<div class="rowx" style="align-items: stretch">%s%s<div class="card" style="flex: 1"><h2>התנועה</h2>%s</div></div>'
             '<div class="rowx" style="align-items: stretch"><div class="card" style="flex: 1.2; border: 3px solid #6B3FD4"><h2>ההמלצה: א, החלון</h2>%s<h2 style="margin-top: 6px">לפיני</h2>%s</div>'
             '<div class="card" style="flex: 1"><h2>מה לא השתנה</h2>%s</div></div>'
             '<p class="cap">התמונה היא התמונה של האפליקציה, וקווי הגובה מחושבים ממודל הגובה של האתר. 120 מסלולים הוא דוגמה. נמדד בכלי העיצוב, לא בטלפון. '
             'המסירה: <span dir="ltr">design/handoff/2026-11-27-redesign-run-button-v4/</span></p>'
             % (cheap, fixed, oc(opts[0]), oc(opts[1]), oc(opts[2]), oc(opts[3]), oc(opts[4]), motion_, why, asks, keep))
    wb('RN00-V4.dc.html', 'כפתור המסלולים, גרסה 4', 1280, H0, inner)


def write_canvas(cj):
    j = json.load(open(cj, encoding='utf8'))
    clash = [f for f in j['boards'] if f not in FILES and re.match(r'^RN\d', f)]
    assert not clash, clash
    for fn in FILES: j['boards'].pop(fn, None)
    j['order'] = [f for f in j['order'] if f not in FILES]
    T = phones()
    place = [('RN00-V4.dc.html', 0, 0, 1280, H0, 'מה היה זול, חמש האפשרויות וההמלצה'),
             ('RN01-Close.dc.html', 1400, 0, 1280, HX, 'חמשת הכפתורים מקרוב, יום ולילה')]
    y0 = max(H0, HX) + 460
    for k, (kind, name, _) in enumerate(KINDS, 1):
        y = y0 + (k - 1) * (844 + 500)
        for n in (1, 2, 3):
            fn = 'RN%d%d.dc.html' % (k, n)
            place.append((fn, 470 * (n - 1), y, 390, 844, T[fn][0]))
        j['notes']['trn%d' % k] = dict(kind='title1', maxW=1330, page=PAGE, x=0, y=y - 300,
                                        text='%s · %s%s' % ('אבגדה'[k - 1], name, ' (ההמלצה)' if kind == REC else ''))
    for fn, x, y, w, h, t in place:
        code = fn.split('.dc.html')[0].split('-')[0]
        j['boards'][fn] = dict(x=x, y=y, w=w, h=h, title='%s (%s)' % (t, code), page=PAGE)
        j['order'].append(fn)
    if not any(pg['id'] == PAGE for pg in j['pages']):
        j['pages'].insert(0, dict(id=PAGE, name=PAGE_NAME))
    j['notes']['t' + PAGE] = dict(kind='title1', maxW=2680, page=PAGE, x=0, y=-300, text='כפתור "מסלולים", גרסה 4: חמש אפשרויות, יום ולילה')
    j['launch'] = dict(view='canvas', page=PAGE)
    open(cj, 'w', encoding='utf8').write(json.dumps(j, ensure_ascii=False, indent=1) + '\n')


FILES = ['RN00-V4.dc.html', 'RN01-Close.dc.html'] + ['RN%d%d.dc.html' % (k, j) for k in range(1, 6) for j in (1, 2, 3)]


def preview(out, assets):
    keep = DK.FILES; DK.FILES = FILES; DK.preview(out, assets); DK.FILES = keep


def main():
    args = sys.argv[1:]
    pv = None
    if '--preview' in args:
        i = args.index('--preview'); pv = (args[i + 1], args[i + 2]); del args[i:i + 3]
    cj = args[0] if args else os.path.join(PROJ, 'canvas.json')
    for fn, v in phones().items():
        PB.phone(fn, v[0], v[1], night=v[2] == 'night', fixed=v[2])
    write_closeups()
    rn00()
    write_canvas(cj)
    if pv: preview(*pv)
    print('ok', cj)


if __name__ == '__main__':
    main()
