#!/usr/bin/env python3
"""The day pages (27.11.2026, Pini's phone test): four items on the screens after a ski day.
DM  "היום שלי": the resort's runs as a faint white line behind the day's coloured line.
DT  "הרכס של היום": a visual timeline of the day, an icon per kind of segment, a better ridge chart.
DS  "הסיפור של היום": every moment the engine gives, tappable, a full-screen player, and the share picture as the finale.
DV  "תמונה לשיתוף": the one-minute video plays inside its card.
All data is the sample day (tools/fixtures/dayrec-logs/full_day.jsonl through day4.py) and the engine's real output
(dayrec-cases.json, dayrec-moments.json); the wording is the approved `moment.*` / `app.*` text. Design only.
Handoff: design/handoff/2026-11-27-redesign-day-pages/.
Usage: python3 design/redesign/tools/dp_boards.py [canvas.json path] [--preview <dir> <assets dir>]"""
import os, sys, json, re, math, datetime
import numpy as np
from peak_b4 import *
import peak_b4 as B4
from peak_boards import lift as LIFT_OF
from live_boards import board, wide as wbox, PROJ
from gap_boards import li, LIFTI, CUP
from mountain import ROOT

PAGE = 'daypages'
PAGE_NAME = 'עמודי היום: המפה, הרכס, הסיפור והשיתוף (27.11)'
T0 = D.FIN['start']; T1 = D.FIN['end']
DATE = datetime.datetime.fromtimestamp(T0 / 1000, D.TZ); DSTR = '%d.%d.%d' % (DATE.day, DATE.month, DATE.year)
MX = D.ST['max_at']
MOM = D.MOM['items']; STORY = [MOM[i] for i in D.MOM['story']]

# ---------------------------------------------------------------- icons (24 grid, stroke)
def icon(d, s=20, w=1.9, fill='none'):
    return '<svg width="%d" height="%d" viewBox="0 0 24 24" fill="%s" stroke="currentColor" stroke-width="%s" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">%s</svg>' % (s, s, fill, w, d)
I_GONDOLA = '<path d="M3 5.500 21 3"/><path d="M12 4.300V8"/><rect x="6.500" y="8" width="11" height="11" rx="2.600"/><path d="M6.500 13h11"/>'
I_CHAIR = '<path d="M3 5.500 21 3"/><path d="M12 4.300v5.200"/><path d="M7 9.500h10"/><path d="M8 9.500v7h9"/><path d="M8 16.500 6.500 20M17 16.500l1 3.500"/>'
I_DRAG = '<path d="M3 5.500 21 3"/><path d="M12 4.300V15"/><path d="M8.500 15h7"/>'
I_DOWN = '<path d="M5 5.500 18.500 19"/><path d="M18.500 11.500V19H11"/>'
I_QUEUE = '<path d="M7 3h10M7 21h10"/><path d="M8 3v3.500a4 4 0 0 0 1.600 3.200L12 11.500l2.400-1.800A4 4 0 0 0 16 6.500V3"/><path d="M8 21v-3.500a4 4 0 0 1 1.600-3.200L12 12.500l2.400 1.800a4 4 0 0 1 1.600 3.200V21"/>'
I_BENCH = '<path d="M5 11V6.500h14V11"/><path d="M3.500 11h17v3h-17z"/><path d="M6 14v5M18 14v5"/>'
I_CUP = '<path d="M5 9h11v5a5 5 0 0 1-5 5h-1a5 5 0 0 1-5-5z"/><path d="M16 10.500h1.500a2.500 2.500 0 0 1 0 5H16"/><path d="M8 3.500v2.500M11.500 3.500v2.500"/>'
I_FOOD = '<path d="M7 3v8a2 2 0 0 0 2 2v8"/><path d="M11 3v8a2 2 0 0 1-2 2"/><path d="M17 21V3c-2.200 1.500-3 4-3 7v4h3"/>'
I_WALK = '<circle cx="13.500" cy="4.500" r="1.900"/><path d="m9.500 21 2.500-6.500-2.500-2.500 1.500-4.500 3.500 3.500 3 1"/><path d="M11 8 7.500 10 6.500 13.500"/><path d="m12 14.500 3.500 6.500"/>'
I_TREE = '<path d="M12 3 7 10h3l-4 6h12l-4-6h3z"/><path d="M12 16v5"/>'
I_PAUSE = '<path d="M9 6v12M15 6v12"/>'
I_PLAY = '<path d="M8 5.500v13l10-6.500z"/>'
I_NOSIG = '<path d="M4 18h2M9 18h2M14 18h2M19 18h1"/><path d="M5 13a10 10 0 0 1 14 0" opacity=".5"/><path d="m4 4 16 16"/>'
I_REPLAY = '<path d="M4 12a8 8 0 1 0 2.400-5.700"/><path d="M4 4v4.500h4.500"/>'
I_CHEV = '<path d="m15 6-6 6 6 6"/>'
I_CHEV_EN = '<path d="m9 6 6 6-6 6"/>'
I_BACK = '<path d="m9 6 6 6-6 6"/>'
I_X = '<path d="M6 6l12 12M18 6 6 18"/>'
I_SHARE = '<path d="M12 15V3"/><path d="m7.500 7.500 4.500-4.500 4.500 4.500"/><path d="M5 12v7.500h14V12"/>'
I_MTN = '<path d="M3 19 9.500 8l3.500 5.500L15.500 10 21 19z"/>'

DPCSS = """
.H .xbg{position:absolute;inset:0;background:var(--bg);opacity:.86}
.H .night .xbg{opacity:.84}
.H .xpage>*{flex-shrink:0}
.H .xpage{position:absolute;left:0;right:0;top:104px;bottom:0;padding:0 14px;box-sizing:border-box;display:flex;flex-direction:column;gap:12px}
.H .xhd{display:flex;flex-direction:column;gap:2px;padding:4px 4px 0}
.H .xhd b{font-size:27px;font-weight:700;line-height:1.15}
.H .xhd span{font-size:14px;color:var(--ink2)}
.H .xstat{display:grid;grid-template-columns:repeat(4,1fr);gap:4px;padding:0 4px}
.H .xstat div{display:flex;flex-direction:column;gap:3px}
.H .xstat b{font-size:26px;font-weight:300;line-height:1;direction:ltr;text-align:start;unicode-bidi:isolate;font-variant-numeric:tabular-nums}
.H .xstat span{font-size:12px;color:var(--ink2);line-height:1.25}
.H .xch{position:relative;border-radius:22px;padding:12px 12px 8px;box-sizing:border-box}
.H .xch .ax{position:absolute;font-size:11px;color:var(--ink2);text-shadow:0 0 3px var(--bg),0 0 3px var(--bg),0 0 6px var(--bg);font-variant-numeric:tabular-nums;white-space:nowrap}
.H .xch .bub{position:absolute;transform:translate(-50%,-100%);padding:4px 9px;border-radius:11px;background:var(--ink);color:var(--bg);font-size:11.5px;font-weight:700;white-space:nowrap}
.H .xch .rest{position:absolute;transform:translateX(-50%);display:flex;align-items:center;gap:4px;font-size:11px;font-weight:700;color:var(--ink2);white-space:nowrap}
.H .xleg{display:flex;align-items:center;gap:12px;font-size:11.5px;color:var(--ink2);padding:2px 2px 0}
.H .xleg i.r{display:block;width:56px;height:5px;border-radius:3px;background:linear-gradient(90deg,#CFC2F4,#A98BEE,#7C4FE0,#B02FC4,#E0246E)}
.H .night .xleg i.r{background:linear-gradient(90deg,#5B46A8,#8A6BE6,#B99CFF,#F3A6F0,#FFF2FA)}
.H .xtb{display:flex;flex-direction:column;gap:8px;border-radius:22px;padding:12px 14px}
.H .xtb .bar{display:flex;height:14px;border-radius:7px;overflow:hidden;gap:2px;direction:ltr}
.H .xtb .bar i{display:block;height:100%}
.H .xtb .lg{display:flex;flex-wrap:wrap;gap:6px 14px;font-size:12.5px;color:var(--ink2)}
.H .xtb .lg span{display:inline-flex;align-items:center;gap:6px}
.H .xtb .lg b{color:var(--ink);font-weight:700;font-variant-numeric:tabular-nums}
.H .xtb .lg i{width:10px;height:10px;border-radius:3px;display:inline-block}
.H .xsec{font-size:13px;font-weight:700;color:var(--ink2);padding:6px 4px 2px;letter-spacing:.02em}
.H .xr{display:flex;align-items:stretch;gap:8px;position:relative}
.H .xr .xt{width:42px;flex:none;font-size:12.5px;color:var(--ink2);direction:ltr;text-align:start;font-variant-numeric:tabular-nums;padding-top:10px;unicode-bidi:isolate}
.H .xr .xn{width:38px;flex:none;position:relative;display:flex;justify-content:center}
.H .xr .xn::before{content:'';position:absolute;top:0;bottom:0;left:50%;width:2px;margin-left:-1px;background:var(--line)}
.H .xr.up .xn::before{background:repeating-linear-gradient(180deg,var(--ink2) 0 2px,transparent 2px 6px);opacity:.75}
.H .xr.dn .xn::before{background:linear-gradient(180deg,#A98BEE,#E0246E)}
.H .night .xr.dn .xn::before{background:linear-gradient(180deg,#8A6BE6,#F3A6F0)}
.H .xr .xo{position:relative;z-index:1;margin-top:4px;width:34px;height:34px;border-radius:50%;display:flex;align-items:center;justify-content:center;background:var(--bg);border:1.5px solid var(--line);color:var(--ink)}
.H .xr.dn .xo{background:var(--acc);border-color:var(--acc);color:var(--onacc)}
.H .xr.sm .xo{width:26px;height:26px;margin-top:6px}
.H .xr.big .xo{width:38px;height:38px;margin-top:8px;background:#F6E7C8;border-color:#E2C48A;color:#6E4A00}
.H .night .xr.big .xo{background:#3A2E17;border-color:#7A6030;color:#F5D79A}
.H .xr .xc{flex:1;min-width:0;padding:6px 0 10px;display:flex;flex-direction:column;gap:4px}
.H .xr .xh{font-size:15.5px;font-weight:500;line-height:1.3}
.H .xr .xh b{font-weight:700;direction:ltr;unicode-bidi:isolate}
.H .xr .xm{font-size:13px;color:var(--ink2);line-height:1.35;display:flex;flex-wrap:wrap;align-items:center;gap:4px 8px}
.H .xcard{border-radius:18px;padding:10px 12px 11px;display:flex;flex-direction:column;gap:7px;margin:2px 0 4px}
.H .xcard .sgr b{font-size:16px}
.H .xbig{border-radius:18px;padding:10px 12px;display:flex;flex-direction:column;gap:2px;margin:2px 0 4px;background:rgba(242,193,61,.16);border:1px solid rgba(190,140,40,.30)}
.H .xchip{display:inline-flex;align-items:center;gap:5px;height:26px;padding:0 9px;border-radius:13px;background:var(--line);font-size:12.5px;font-weight:500;color:var(--ink);white-space:nowrap}
.H .xchip.q{background:rgba(242,193,61,.20)}
.H .xstrip{display:block;width:100%;height:10px}
.H .xmini{position:absolute;left:12px;right:12px;top:104px;height:52px;border-radius:16px;padding:6px 10px;box-sizing:border-box}
.H .xfade{position:absolute;left:0;right:0;height:40px;pointer-events:none}
.H .xsc{position:absolute;left:0;right:0;top:0;bottom:0;overflow:hidden}
/* the story */
.H .scard{border-radius:20px;padding:12px 12px 12px 14px;display:flex;gap:12px;align-items:center;text-align:start;width:100%;box-sizing:border-box}
.H .scard .tx{display:flex;flex-direction:column;gap:3px;min-width:0;flex:1}
.H .scard .tx b{font-size:16.5px;line-height:1.25}
.H .scard .tx span{font-size:13.5px;color:var(--ink2);line-height:1.4}
.H .scard .go{color:var(--ink2);flex:none;display:flex}
.H .scard .nb{width:76px;height:76px;border-radius:14px;flex:none;display:flex;align-items:center;justify-content:center;background:var(--line)}
.H .srow{display:flex;gap:12px;align-items:center;min-height:56px;padding:4px 2px;text-align:start;width:100%}
.H .srow+.srow{border-top:1px solid var(--line)}
.H .srow .ic2{width:40px;height:40px;border-radius:12px;background:var(--line);display:flex;align-items:center;justify-content:center;flex:none;font-size:15px;font-weight:700;direction:ltr}
.H .srow .tx{flex:1;min-width:0;display:flex;flex-direction:column;gap:2px}
.H .srow .tx b{font-size:15px}.H .srow .tx span{font-size:13px;color:var(--ink2);line-height:1.35}
.H .play{display:flex;align-items:center;justify-content:center;gap:9px;height:50px;border-radius:25px;background:var(--acc);color:var(--onacc);font-size:16px;font-weight:700}
.H .prog{position:absolute;left:12px;right:12px;top:58px;display:flex;gap:4px}
.H .prog i{flex:1;height:3.5px;border-radius:2px;background:rgba(255,255,255,.38);position:relative;overflow:hidden}
.H .prog i u{position:absolute;top:0;bottom:0;inset-inline-start:0;width:46%;background:#fff}
.H .prog i.on{background:#fff}
.H .stx{position:absolute;left:12px;right:12px;bottom:18px;border-radius:26px;padding:16px 18px 18px;display:flex;flex-direction:column;gap:6px}
.H .stx .k{font-size:12.5px;font-weight:700;color:var(--ink2)}
.H .stx b{font-size:24px;line-height:1.2}
.H .stx span{font-size:15px;line-height:1.45}
.H .xbtn{position:absolute;width:44px;height:44px;border-radius:50%;display:flex;align-items:center;justify-content:center}
/* the share card */
.H .vcard{position:absolute;left:36px;top:112px;width:318px;height:398px;border-radius:20px;overflow:hidden;box-shadow:0 18px 44px rgba(6,14,30,.45)}
.H .vcard .vp{position:absolute;left:0;right:0;bottom:0;height:4px;background:rgba(255,255,255,.28)}
.H .vcard .vp i{display:block;height:100%;background:#fff}
.H .vcard .vt{position:absolute;top:12px;left:12px;height:26px;padding:0 9px;border-radius:13px;background:rgba(8,14,28,.55);color:#fff;font-size:12px;font-weight:700;display:flex;align-items:center;direction:ltr;font-variant-numeric:tabular-nums}
.H .vcard .vb{position:absolute;top:8px;right:8px;width:44px;height:44px;border-radius:50%;background:rgba(8,14,28,.55);color:#fff;display:flex;align-items:center;justify-content:center}
.H .vcard .vc{position:absolute;left:50%;top:42%;transform:translate(-50%,-50%);width:72px;height:72px;border-radius:50%;background:rgba(8,14,28,.6);color:#fff;display:flex;align-items:center;justify-content:center}
.H .vlink{position:absolute;left:36px;right:36px;top:522px;height:44px;display:flex;align-items:center;justify-content:center;gap:6px;font-size:15px;font-weight:700;color:#fff;text-shadow:0 1px 8px rgba(0,0,0,.45)}
.H .stog{display:grid;grid-template-columns:1fr 1fr;gap:8px}
.H .stile{height:56px;box-sizing:border-box;border-radius:18px;border:1.5px solid var(--line);display:flex;align-items:center;justify-content:center;gap:9px;font-size:15px;font-weight:700;color:var(--ink2)}
.H .stile.on{border-color:var(--acc);color:var(--ink);background:rgba(107,63,212,.10);box-shadow:inset 0 0 0 1px var(--acc)}
.H .night .stile.on{background:rgba(183,156,255,.14)}
.H .stile.on svg{color:var(--acc)}
.H .srow2{display:grid;grid-template-columns:1fr 1fr;gap:8px}
.H .sbt{position:relative;overflow:hidden;height:56px;border-radius:18px;font-size:15px;font-weight:700;border:1.5px solid transparent;box-sizing:border-box}
.H .sbt span{position:relative;display:flex;align-items:center;justify-content:center;gap:9px;height:100%}
.H .sbt em{font-style:normal;font-weight:500;font-size:13px;opacity:.85;font-variant-numeric:tabular-nums;direction:ltr}
.H .sbt.pri2{background:var(--acc);color:var(--onacc)}
.H .sbt.sec2{border:1.5px solid var(--line);color:var(--ink)}
.H .sbt.ok2{background:var(--good);color:#fff}
.H .night .sbt.ok2{color:#062014}
.H .sbt.bad2{border:1.5px solid var(--warn);color:var(--warn)}
.H .sbt i.f{position:absolute;top:0;bottom:0;inset-inline-start:0;display:block}
.H .sbt.pri2 i.f{background:rgba(255,255,255,.26)}
.H .sbt.sec2 i.f{background:rgba(107,63,212,.16)}
.H .night .sbt.sec2 i.f{background:rgba(183,156,255,.22)}
"""
MB.CSS = MB.CSS + DPCSS
TOP = lambda t, extra='': '<div class="recp glass">%s<span style="min-width: 0; white-space: normal; line-height: 1.25">%s</span><span style="margin-inline-start: auto">%s</span>%s</div>' % (icon(I_BACK, 20, 2), t, EG, extra)
TOPEN = lambda t: '<div class="recp glass">%s<span>%s</span><span style="margin-inline-start: auto"><span class="eg">Example</span></span></div>' % (icon('<path d="m15 6-6 6 6 6"/>', 20, 2), t)
hmin = lambda ms_: max(1, int(round(ms_ / 60000)))


def backdrop(dim=True):
    """the mountain with the day on it fills the screen under the page (the v91 rule), veiled for reading"""
    o = pic('hero', 'noon', 'night') + both(lambda m, l: svg(VH, D.day(VH, m, w=2.6)))
    return o + ('<div class="xbg"></div>' if dim else '')


# ---------------------------------------------------------------- DM: the day map with faint white runs
def white_runs(V, mode, k=1.0, a=None, w=None):
    """every run of the resort as today's faint line in its own colour, read as part of the snow (Pini, 27.11:
    not white, not painted on top). Today in the app: colour at 32%. Proposed: a small step stronger, 45% by day and
    40% at night, 1.2 times as wide. Unnamed pieces and connecting ways dashed and fainter. The name stays for imports."""
    zf = max(1, V['S'] / 0.128) ** 0.62; o = []
    a = a if a is not None else (.45 if mode == 'day' else .40)
    w = w if w is not None else 2.0
    for p in P.PISTES:
        named = p['named'] and p.get('kind') != 'ski-way'
        c = HEX.get(p['color'], '#3B82F6')
        for sg in p['segs']:
            x, y, vis, z, q = P.project(V, sg)
            for ch in P.chunks(x, y, vis):
                o.append('<path d="%s" stroke="%s" stroke-width="%.1f" opacity="%.2f"%s/>' % (
                    P.pathd(ch), c, (w if named else w * .75) * zf * k, a if named else a * .6, '' if named else ' stroke-dasharray="2 5"'))
    return ''.join(o)


TODAY = dict(a=.32, w=1.66)   # the app today, for the before / after board


def daymap(mode_fixed=None, view='hero', tagged=None, before=False):
    fall = D.FIN['falls'][0]
    V = VIEW[view]
    px, py, _, _ = D.ll(V, MX['lat'], MX['lon']); fx, fy, _, _ = D.ll(V, fall['lat'], fall['lon'])

    def L(mode, light):
        s = (white_runs(V, mode, **TODAY) if before else white_runs(V, mode)) + D.day(V, mode, w=3.2)
        s += '<circle cx="%.1f" cy="%.1f" r="5" fill="#fff" stroke="#E0246E" stroke-width="2.4"/>' % (px, py)
        return svg(V, s)
    o = (pic('hero', 'noon', 'night') if view == 'hero' else pic('zoom', 'morning', 'night')) + both(L)
    o += '<div class="pin" style="left: %.0fpx; top: %.0fpx"><b>פה טסת · %.1f</b><i></i></div>' % (px, py - 6, D.ST['max_kmh'])
    if tagged:
        tx, ty, txt = tagged
        o += '<div class="pin" style="left: %.0fpx; top: %.0fpx"><b style="background: var(--glass); color: var(--ink); border: 1px solid var(--line); direction: ltr">%s</b><i style="background: var(--ink2)"></i></div>' % (tx, ty, txt)
    o += '<div class="recp glass">%s<span>היום שלך · <span class="tmr" style="font-weight: 500">%s</span> · <span class="tmr" style="font-weight: 500">%s–%s</span></span><span style="margin-inline-start: auto">%s</span></div>' % (icon(I_BACK, 20, 2), DSTR, D.hm(T0), D.hm(T1), EG)
    if view == 'hero':
        kd = [d for d in D.DESC if 'Kobi' in D.names(d)][0]; kx, ky, _, _ = D.where(VH, kd['t0'])
        o += tag(96, ky - 4, 'ועוד ירידה בצד של Kobi ›', style=' direction: rtl')
    o += '<div class="sheet glass" style="gap: 12px"><div class="grab"></div><div class="ttl" style="font-size: 25px">יום של פעם ראשונה</div>'
    o += '<div class="nums" style="gap: 22px"><div><span class="num">%d</span><span class="cap">ירידות</span></div><div><span class="num">%s</span><span class="cap">מ׳ למטה</span></div><div><span class="num">%.1f</span><span class="cap">ק״מ גלישה</span></div><span class="spd" style="margin-inline-start: auto; align-self: flex-end; padding-bottom: 4px"><span>לאט</span><i style="width: 48px"></i><span>מהר</span></span></div>' % (D.ST['descents'], n0(D.ST['drop_m']), D.ST['ski_dist_m'] / 1000)
    o += '<div style="display: flex; gap: 8px"><button class="pri" style="display: flex; align-items: center; justify-content: center; gap: 9px">%s היום בדקה</button><button class="sec">הסיפור</button><button class="sec">שיתוף</button></div></div>' % icon(I_PLAY, 16, 2, 'currentColor')
    return o


def run_point(V, key, frac):
    p = P.find(key); s = max(p['segs'], key=len); x, y, vis, z, q = P.project(V, s); i = int(frac * (len(x) - 1))
    return float(x[i]), float(y[i])


# ---------------------------------------------------------------- DT: the ridge of the day
def place_near(lat, lon):
    """a known café or restaurant within the places file's own distance; never a guess"""
    pl = json.load(open(os.path.join(ROOT, 'site/data/places.json')))
    best = None
    for p in pl['places']:
        if p['kind'] not in ('cafe', 'food') or not p.get('name'):
            continue
        la, lo = p['ll']; dm = math.hypot((la - lat) * 111000, (lo - lon) * 111000 * math.cos(math.radians(lat)))
        if dm <= pl['maxDistance'] and (best is None or dm < best[0]):
            best = (dm, p)
    return best[1] if best else None


def speed_strip(s, w=240, h=10, mode='day', bins=48):
    a, b = D.idx(s['t0'], s['t1']); n = max(2, b - a); o = ''
    for j in range(bins):
        i0 = a + j * n // bins; i1 = max(i0 + 1, a + (j + 1) * n // bins)
        o += '<rect x="%.1f" y="0" width="%.1f" height="%d" fill="%s"/>' % (j * w / bins, w / bins + .5, h, D.RAMP[mode][D.band(float(np.mean(D.KMH[i0:i1])))])
    im = int(np.argmax(D.KMH[a:b])); xm = im / n * w
    o += '<circle cx="%.1f" cy="%.1f" r="%.1f" fill="#fff" stroke="#E0246E" stroke-width="2"/>' % (min(max(xm, 6), w - 6), h / 2, h / 2 + 1)
    return '<svg class="xstrip" viewBox="0 0 %d %d" preserveAspectRatio="none" style="height: %dpx; border-radius: %dpx; direction: ltr">%s</svg>' % (w, h, h, h // 2, o)


def strip2(s, k=1.0):
    return '<span class="d">%s</span><span class="n">%s</span>' % (speed_strip(s, h=int(10 * k)), speed_strip(s, mode='night', h=int(10 * k)))


TXT = {
    'he': dict(lift_kind={'gondola': 'גונדולה', 'chair_lift': 'רכבל כיסאות', 'drag_lift': 'מעלית גרירה', 'platter': 'מעלית גרירה'}, min='דק׳', up='מ׳ למעלה', down='מ׳ למטה', km='ק״מ',
               mx='מרבית %d קמ״ש', queue='תור %d דק׳', one_stop='עצירה אחת', stops='%d עצירות', off='חלק מחוץ למסלולים', gap='דקה בלי קליטה', walk='הליכה, %s מ׳',
               long='הפסקה ארוכה · %d דק׳', at_top='בראש %s', morning='בוקר', noon='צהריים', runs='ירידות', drop='מ׳ למטה', dist='ק״מ גלישה', hours='שעות על ההר',
               split='לאן הלך היום', ski='גלישה', lifts='ברכבלים', queues='בתורים', rest='הפסקות והליכה', slow='לאט', fast='מהר', lift='רכבל', title='הרכס של היום'),
    'en': dict(lift_kind={'gondola': 'Gondola', 'chair_lift': 'Chairlift', 'drag_lift': 'Drag lift', 'platter': 'Drag lift'}, min='min', up='m up', down='m down', km='km',
               mx='top %d km/h', queue='Queue %d min', one_stop='One stop', stops='%d stops', off='Part off the runs', gap='A minute with no signal', walk='Walking, %s m',
               long='Long break · %d min', at_top='At the top of %s', morning='Morning', noon='Midday', runs='runs', drop='m down', dist='km skied', hours='hours on the mountain',
               split='Where the day went', ski='skiing', lifts='on lifts', queues='in lines', rest='breaks and walking', slow='Slow', fast='Fast', lift='lift', title='The ridge of the day'),
}


def lift_icon(name):
    k = LIFT_OF(name).get('kind', 'chair_lift')
    return I_GONDOLA if k == 'gondola' else (I_DRAG if k in ('drag_lift', 'platter', 'j-bar', 't-bar') else I_CHAIR)


def rows(a, b, L='he', k=1.0, cafe=None):
    """the timeline rows for segments a..b: a lift with its queue, each descent as a card, the long break, a walk"""
    T = TXT[L]; o = ''; prev = None; sec = None
    for i in range(a, b):
        s = D.SEG[i]; t = s['t0']; hh = datetime.datetime.fromtimestamp(t / 1000, D.TZ).hour
        part = T['morning'] if hh < 12 else T['noon']
        if part != sec and s['type'] in ('lift', 'descent'):
            o += '<div class="xsec" style="font-size: %.1fpx">%s</div>' % (13 * k, part); sec = part
        if s['type'] == 'lift':
            q = D.SEG[i - 1] if i > 0 and D.SEG[i - 1]['type'] == 'stop' and D.SEG[i - 1].get('kind') == 'queue' else None
            qm = round((q['t1'] - q['t0']) / 60000) if q else 0
            kind = T['lift_kind'].get(LIFT_OF(s['name']).get('kind'), T['lift'])
            meta = '%s · %s %s · %s %s' % (kind, ms((s['t1'] - s['t0']) / 1000), T['min'], n0(s['rise_m']), T['up'])
            chip = '<span class="xchip q" style="font-size: %.1fpx; height: %dpx">%s%s</span>' % (12.5 * k, 26 * k, icon(I_QUEUE, int(15 * k), 1.8), T['queue'] % qm) if qm >= 2 else ''
            o += ('<div class="xr up"><span class="xt" style="font-size: %.1fpx; width: %dpx">%s</span><span class="xn"><span class="xo">%s</span></span><div class="xc"><span class="xh" style="font-size: %.1fpx"><b>%s</b></span>'
                  '<span class="xm" style="font-size: %.1fpx">%s%s</span></div></div>') % (12.5 * k, 42 * k, D.hm(q['t0'] if q else t), icon(lift_icon(s['name']), 18, 1.8), 15.5 * k, s['name'], 13 * k, chip, meta)
        elif s['type'] == 'descent':
            chips = []
            if s['stops']:
                chips.append((I_PAUSE, T['one_stop'] if len(s['stops']) == 1 else T['stops'] % len(s['stops'])))
            if s['offpiste']:
                chips.append((I_TREE, T['off']))
            if any(s['t0'] <= g['t0'] <= s['t1'] for g in D.FIN['gaps']):
                chips.append((I_NOSIG, T['gap']))
            ch = ''.join('<span class="xchip" style="font-size: %.1fpx; height: %dpx">%s%s</span>' % (12.5 * k, 26 * k, icon(c, int(15 * k), 1.8), w) for c, w in chips)
            nm = '<div class="sgr" style="row-gap: 4px">' + ' <em>›</em> '.join('%s<b style="font-size: %.1fpx">%s</b>' % (sign(r), 16 * k, r) for r in main(s)) + '</div>'
            meta = '%.1f %s · %s %s · %s' % (s['dist_m'] / 1000, T['km'], n0(s['drop_m']), T['down'], T['mx'] % round(s['max_kmh']))
            o += ('<div class="xr dn"><span class="xt" style="font-size: %.1fpx; width: %dpx">%s</span><span class="xn"><span class="xo">%s</span></span><div class="xc" style="padding-bottom: 4px"><div class="xcard glass" style="box-shadow: none">%s%s'
                  '<span class="xm" style="font-size: %.1fpx">%s</span>%s</div></div></div>') % (12.5 * k, 42 * k, D.hm(t), icon(I_DOWN, 18, 2.1), nm, strip2(s, k), 13 * k, meta,
                                                                                               '<span class="xm">%s</span>' % ch if ch else '')
        elif s['type'] == 'stop' and s.get('kind') == 'long':
            pl = cafe or place_near(s['lat'], s['lon'])
            ic_ = I_CUP if pl and pl['kind'] == 'cafe' else (I_FOOD if pl else I_BENCH)
            where = (pl['name'] if pl else T['at_top'] % prev['name']) if (pl or (prev and prev['type'] == 'lift')) else ''
            o += ('<div class="xr big"><span class="xt" style="font-size: %.1fpx; width: %dpx">%s</span><span class="xn"><span class="xo">%s</span></span><div class="xc"><div class="xbig">'
                  '<span class="xh" style="font-size: %.1fpx; font-weight: 700">%s</span><span class="xm" style="font-size: %.1fpx"><span class="tmr" style="font-weight: 400">%s–%s</span> · %s</span></div></div></div>') % (
                12.5 * k, 42 * k, D.hm(t), icon(ic_, 20, 1.8), 15.5 * k, T['long'] % hmin(s['t1'] - s['t0']), 13 * k, D.hm(t), D.hm(s['t1']), '<bdi>%s</bdi>' % where)
        elif s['type'] == 'walk' and s['dist_m'] > 100:
            o += ('<div class="xr sm"><span class="xt" style="font-size: %.1fpx; width: %dpx">%s</span><span class="xn"><span class="xo">%s</span></span><div class="xc" style="padding: 8px 0 8px"><span class="xm" style="font-size: %.1fpx">%s</span></div></div>') % (
                12.5 * k, 42 * k, D.hm(t), icon(I_WALK, 14, 1.8), 13 * k, T['walk'] % n0(round(s['dist_m'] / 10) * 10))
        if s['type'] in ('lift', 'descent'):
            prev = s
    return o


def chart(W=362, H=150, L='he'):
    """the ridge: height against time; descents burn by speed, lifts dotted, the long break as a band"""
    T = TXT[L]
    lb = [q for q in D.SEG if q['type'] == 'stop' and q.get('kind') == 'long'][0]
    outs = {}
    for mode in ('day', 'night'):
        r, X, Y = D.ribbon(W, H, mode, pad=4, sw=2.4)
        g = 'rgba(14,34,51,.14)' if mode == 'day' else 'rgba(234,241,248,.18)'
        grid = ''
        for z in (2200, 2600, 3000):
            grid += '<path d="M0 %.1fH%d" stroke="%s" stroke-width="1" stroke-dasharray="2 4"/>' % (Y(z), W, g)
        band = '<rect x="%.1f" y="0" width="%.1f" height="%d" fill="%s"/>' % (X(lb['t0']), X(lb['t1']) - X(lb['t0']), H, 'rgba(242,193,61,.18)' if mode == 'day' else 'rgba(245,215,154,.14)')
        i = int(np.searchsorted(D.T, MX['t'])); cx, cy = X(MX['t']), Y(D.Z[i])
        mk = '<circle cx="%.1f" cy="%.1f" r="4.6" fill="#fff" stroke="#E0246E" stroke-width="2.2"/>' % (cx, cy)
        outs[mode] = '<svg viewBox="0 0 %d %d" width="%d" height="%d" fill="none" stroke-linecap="round" stroke-linejoin="round" style="display: block; direction: ltr">%s%s%s%s</svg>' % (W, H, W, H, band, grid, r, mk)
    o = '<div class="xch glass" style="box-shadow: none; height: %dpx"><div style="position: relative; direction: ltr; height: %dpx"><div class="d">%s</div><div class="n">%s</div>' % (H + 52, H, outs['day'], outs['night'])
    for z in (2200, 2600, 3000):
        o += '<span class="ax" style="left: 2px; top: %.0fpx; transform: translateY(-100%%)">%s</span>' % (Y(z) - 1, n0(z))
    for hh in (10, 11, 12):
        tt = T0 + int((hh - 9.5) * 3600000); o += '<span class="ax" style="left: %.0fpx; top: %dpx; transform: translateX(-50%%)">%d:00</span>' % (X(tt), H + 4, hh)
    o += ('<span class="rest" style="left: %.0fpx; top: 2px">%s<bdi dir="' + ('rtl' if L == 'he' else 'ltr') + '">%d %s</bdi></span>') % ((X(lb['t0']) + X(lb['t1'])) / 2, icon(I_BENCH, 13, 2), hmin(lb['t1'] - lb['t0']), T['min'])
    o += '<span class="bub" style="left: %.0fpx; top: %.0fpx">%s · %.1f</span>' % (min(max(cx, 50), W - 50), max(cy - 8, 22), D.hm(MX['t']), D.ST['max_kmh'])
    o += '</div><div class="xleg" style="margin-top: 22px"><span>%s</span><i class="r"></i><span>%s</span><span style="margin-inline-start: 8px">· · · %s</span><span style="margin-inline-start: auto; display: inline-flex; align-items: center; gap: 4px">%s%s</span></div></div>' % (
        T['slow'], T['fast'], T['lift'], '<i style="width: 10px; height: 10px; border-radius: 3px; background: rgba(242,193,61,.45); display: inline-block"></i>', ' הפסקה' if L == 'he' else ' Break')
    return o


def split():
    tot = (T1 - T0) / 60000
    q = sum((s['t1'] - s['t0']) for s in D.SEG if s['type'] == 'stop' and s.get('kind') == 'queue') / 60000
    ski = D.ST['ski_s'] / 60; lf = D.ST['lift_s'] / 60; rest = tot - ski - lf - q
    return [round(ski), round(lf), round(q), round(rest)], tot


def timebar(L='he'):
    T = TXT[L]; v, tot = split()
    cols = ['linear-gradient(90deg,#A98BEE,#E0246E)', 'var(--ink2)', '#E2B13C', 'var(--line)']
    bar = ''.join('<i style="flex: %d; background: %s"></i>' % (x, c) for x, c in zip(v, cols))
    lg = ''.join('<span><i style="background: %s"></i><b>%d</b> %s %s</span>' % (c, x, T['min'], w) for x, c, w in zip(v, cols, (T['ski'], T['lifts'], T['queues'], T['rest'])))
    return '<div class="xtb glass" style="box-shadow: none"><b style="font-size: 14.5px">%s</b><div class="bar">%s</div><div class="lg">%s</div></div>' % (T['split'], bar, lg)


def stats(L='he'):
    T = TXT[L]; h = (T1 - T0) / 3600000
    return '<div class="xstat">%s</div>' % ''.join('<div><b>%s</b><span>%s</span></div>' % (a, b) for a, b in (
        (D.ST['descents'], T['runs']), ('%.1f' % (D.ST['ski_dist_m'] / 1000), T['dist']), (n0(D.ST['drop_m']), T['drop']), ('%d:%02d' % (int(h), round(h % 1 * 60)), T['hours'])))


def mini_ridge(t0, t1, W=342, H=36):
    outs = {}
    for mode in ('day', 'night'):
        r, X, Y = D.ribbon(W, H, mode, pad=2, sw=1.6)
        win_ = '<rect x="%.1f" y="0" width="%.1f" height="%d" rx="4" fill="%s" stroke="%s" stroke-width="1.2"/>' % (X(t0), X(t1) - X(t0), H, 'rgba(107,63,212,.12)' if mode == 'day' else 'rgba(183,156,255,.16)', '#6B3FD4' if mode == 'day' else '#B79CFF')
        outs[mode] = '<svg viewBox="0 0 %d %d" width="%d" height="%d" fill="none" stroke-linecap="round" style="display: block; direction: ltr">%s%s</svg>' % (W, H, W, H, r, win_)
    return '<div class="xmini glass"><div class="d">%s</div><div class="n">%s</div></div>' % (outs['day'], outs['night'])


def seg_i(pred):
    return [i for i, s in enumerate(D.SEG) if pred(s)][0]


def dt_top(L='he'):
    T = TXT[L]
    pill = TOP('%s · <span class="tmr" style="font-weight: 500">%s</span>' % (T['title'], DSTR)) if L == 'he' else TOPEN('%s · %s' % (T['title'], DSTR))
    o = backdrop() + '<div class="xsc"><div class="xpage" style="top: 112px">'
    o += stats(L) + chart(L=L) + timebar(L)
    o += '<div>%s</div>' % rows(0, 8, L)
    o += '</div></div>' + '<div class="xfade" style="bottom: 0; background: linear-gradient(180deg, transparent, var(--bg))"></div>' + pill
    return o


def dt_mid(a, b, k=1.0, top=168, cafe=None):
    t0 = D.SEG[a]['t0']; t1 = D.SEG[b - 1]['t1']
    o = backdrop() + '<div class="xsc"><div class="xpage" style="top: %dpx; gap: 0">%s</div></div>' % (top, rows(a, b, 'he', k, cafe))
    o += '<div class="xfade" style="top: 150px; background: linear-gradient(0deg, transparent, var(--bg))"></div><div class="xfade" style="bottom: 0; background: linear-gradient(180deg, transparent, var(--bg))"></div>'
    o += TOP('הרכס של היום · <span class="tmr" style="font-weight: 500">%s</span>' % DSTR) + mini_ridge(t0, t1)
    return o


def dt_kinds():
    """every row the page can draw, and the rule for the café"""
    ex = [
        ('up', I_GONDOLA, '<b>Goodaura</b>', '<span class="xchip q">%s תור 3 דק׳</span>גונדולה · 6:12 דק׳ · 544 מ׳ למעלה' % icon(I_QUEUE, 15, 1.8), 'רכבל, ולפניו התור (מ-2 דק׳)'),
        ('dn', I_DOWN, '<b>Goodaura 1</b>', '<span class="xchip">%s עצירה אחת</span><span class="xchip">%s חלק מחוץ למסלולים</span><span class="xchip">%s דקה בלי קליטה</span>' % (icon(I_PAUSE, 15), icon(I_TREE, 15), icon(I_NOSIG, 15)), 'ירידה, ומה שקרה בה'),
        ('big', I_CUP, 'הפסקה ארוכה · 35 דק׳', '<span class="tmr" style="font-weight: 400">11:04–11:39</span> · <bdi>Ice Bar</bdi>', 'הפסקה ארוכה ליד בית קפה מקובץ המקומות (עד 250 מ׳)'),
        ('big', I_FOOD, 'הפסקה ארוכה · 35 דק׳', '<span class="tmr" style="font-weight: 400">11:04–11:39</span> · <bdi>Gradus</bdi>', 'ליד מסעדה מהקובץ'),
        ('big', I_BENCH, 'הפסקה ארוכה · 35 דק׳', '<span class="tmr" style="font-weight: 400">11:04–11:39</span> · בראש <bdi>Kobi</bdi>', 'בלי מקום ידוע בקרבת מקום: סמל כללי (ביום הדוגמה)'),
        ('sm', I_WALK, '', 'הליכה, 360 מ׳', 'הליכה (מעל 100 מ׳)'),
    ]
    o = backdrop() + TOP('הרכס של היום · כל סוגי השורות') + '<div class="xpage" style="gap: 6px">'
    for cl, ic_, h, m, cap in ex:
        o += '<div class="sub" style="font-size: 12px; padding: 6px 2px 0">%s</div>' % cap
        if cl == 'dn':
            body = '<div class="xcard glass" style="box-shadow: none"><div class="sgr">%s%s</div>%s<span class="xm">%s</span></div>' % (sign('Goodaura 1'), h, strip2(D.DESC[1]), m)
        elif cl == 'big':
            body = '<div class="xbig"><span class="xh" style="font-weight: 700">%s</span><span class="xm">%s</span></div>' % (h, m)
        else:
            body = ('<span class="xh">%s</span>' % h if h else '') + '<span class="xm">%s</span>' % m
        o += '<div class="xr %s" style="min-height: 0"><span class="xt">11:04</span><span class="xn"><span class="xo">%s</span></span><div class="xc" style="padding: 4px 0 2px">%s</div></div>' % (cl, icon(ic_, 18 if cl != 'sm' else 14, 1.9), body)
    return o + '</div>'


# ---------------------------------------------------------------- DS: the story
def it(key, **kw):
    for m in MOM:
        if m['key'] == key and all(m['p'].get(a) == b for a, b in kw.items()):
            return m


def spot(lat, lon, sc=2.2, w=76, h=76, r=14):
    x, y, _, _ = D.ll(VH, lat, lon)

    def f(V, mode):
        return white_runs(V, mode, .7) + D.day(V, mode, w=2.2, lifts=False) + '<circle cx="%.1f" cy="%.1f" r="3.4" fill="#fff" stroke="#E0246E" stroke-width="1.6"/>' % (x, y)
    return win('hero', w, h, x, y, sc, f, day='noon', r=r)


def at_t(t):
    i = int(np.clip(np.searchsorted(D.T, t), 0, len(D.T) - 1)); return D.Q[i]


def ll_of(t):
    i = int(np.clip(np.searchsorted(D.T, t), 0, len(D.T) - 1)); x, y, _, _ = P.pt(VH, D.Q[i, 0], D.Q[i, 1], lift=2); return x, y


def scard(title, line, vis):
    return '<button class="scard glass" style="box-shadow: none">%s<span class="tx"><b>%s</b><span>%s</span></span><span class="go">%s</span></button>' % (vis, title, line, icon(I_CHEV, 20, 2))


def story_cards():
    fc = it('first_color', color='black'); stp = it('steepest'); fa = it('fastest'); tm = it('tomorrow')
    fx = fa['p']['at']
    def win_t(t, sc=2.2):
        i = int(np.searchsorted(D.T, t)); return spot(*D.LL[i], sc=sc) if hasattr(D, 'LL') else None
    c = scard('השחור הראשון שלך', '<bdi>Sadzele 1</bdi>, ב-%s, ביום הגלישה 1 שלך. גם השחורים כבר על המפה שלך.' % D.hm(fc['t']), spot(fx['lat'] + 0.0009, fx['lon'], 2.0))
    c += scard('המסלול הכי תלול שלך', '<bdi>Sadzele 1</bdi>, עם קטע של כ-%d°. המסלול הכי תלול של היום שלך.' % stp['p']['deg'], '<span class="nb"><span class="num" style="font-size: 30px">%d°</span></span>' % stp['p']['deg'])
    c += scard('הרגע הכי מהיר שלך', '<bdi>%.1f</bdi> קמ״ש ב-<bdi>Sadzele 1</bdi>, בירידה %d מתוך %d. עד אז הרגליים כבר היו חמות.' % (fa['p']['kmh'], fa['p']['nth'], fa['p']['of']), spot(fx['lat'], fx['lon'], 2.6))
    c += scard('רעיון למחר', 'נשאר שחור אחד שעוד לא גלשת: <bdi>Sadzele 2</bdi>. אחריו, כל השחורים כאן שלך.', '<span class="nb">%s</span>' % mini('Sadzele 2', 34, 66))
    return c


def more_rows():
    ld = it('longest_descent'); fr = it('first_color', color='red'); dc = it('drop_compare'); ts = it('time_split'); qu = it('queue')
    R = [
        ('<span style="font-size: 13px">%.1f</span>' % (ld['p']['dist_m'] / 1000), 'הירידה הארוכה של היום', '%.1f ק״מ ו-%s מ׳ למטה ב-<bdi>Goodaura 1</bdi>. הירידה שהכי בדקה את הרגליים.' % (ld['p']['dist_m'] / 1000, n0(ld['p']['drop_m']))),
        (sign('Kudebi 2'), 'האדום הראשון שלך', '<bdi>Kudebi 2</bdi>, ב-%s, ביום הגלישה 1 שלך. מעכשיו האדומים הם חלק מההר שלך.' % D.hm(fr['t'])),
        (icon(I_MTN, 20, 1.8), 'כמה ירדת היום', '%s מ׳ למטה: פי %s ממגדל אייפל.' % (n0(dc['p']['drop_m']), ('%.1f' % dc['p']['times']).rstrip('0').rstrip('.'))),
        (icon('<circle cx="12" cy="12" r="8.500"/><path d="M12 7.500V12l3 2"/>', 20, 1.8), 'לאן הלך היום', '%d דק׳ גלישה, %d דק׳ ברכבלים, %d דק׳ בתורים. הגלישה עצמה היא החלק הקצר, והכי טוב.' % (ts['p']['ski'], ts['p']['lift_min'], ts['p']['queue'])),
        (icon(I_QUEUE, 20, 1.8), 'הזמן בתורים', 'התור הכי ארוך: %d דק׳ ל-<bdi>%s</bdi>, ב-%s. בסך הכל %d דק׳ בתורים היום.' % (round(qu['p']['s'] / 60), qu['p']['lift'], D.hm(qu['t']), round(qu['p']['total_s'] / 60))),
    ]
    return ''.join('<button class="srow"><span class="ic2">%s</span><span class="tx"><b>%s</b><span>%s</span></span><span class="go" style="color: var(--ink2); display: flex">%s</span></button>' % (a, b, c, icon(I_CHEV, 18, 2)) for a, b, c in R)


def share_card(w=318, h=398, until=None, top=0, left=0, abs_=True, play=None, extra='', L='he'):
    sc = w / 390 * 1.18

    def f(V, mode):
        px, py, _, _ = D.ll(V, MX['lat'], MX['lon'])
        s = D.day(V, mode, w=3.0, until=until)
        if until is None or until >= MX['t']:
            s += '<circle cx="%.1f" cy="%.1f" r="4.6" fill="#fff" stroke="#E0246E" stroke-width="2.2"/>' % (px, py)
        return s
    k = w / 318
    nums = ''.join('<div style="display: flex; flex-direction: column; gap: 2px"><span style="font-size: %.0fpx; font-weight: 300; line-height: 1; direction: ltr">%s</span><span style="font-size: %.1fpx; opacity: .85">%s</span></div>' % (27 * k, a, 11 * k, b)
                   for a, b in zip((D.ST['descents'], n0(D.ST['drop_m']), '%.1f' % (D.ST['ski_dist_m'] / 1000), '%.1f' % D.ST['max_kmh']), (('runs', 'm down', 'km', 'km/h, top') if L == 'en' else ('ירידות', 'מ׳ למטה', 'ק״מ', 'קמ״ש, מרבית'))))
    o = '<div style="%s; width: %dpx; height: %dpx; border-radius: %dpx; overflow: hidden; box-shadow: 0 18px 44px rgba(6,14,30,.45)">%s' % (
        'position: absolute; left: %dpx; top: %dpx' % (left, top) if abs_ else 'position: relative; flex: none', w, h, 20 * k, win('hero', w, h, 196, 400, sc, f, day='noon', r=0))
    o += '<div style="position: absolute; left: 0; right: 0; bottom: 0; height: %dpx; background: linear-gradient(180deg, transparent, rgba(8,14,28,.86) 62%%)"></div>' % (190 * k)
    o += '<div style="position: absolute; left: %dpx; right: %dpx; bottom: %dpx; color: #fff; display: flex; flex-direction: column; gap: %dpx; text-align: start"><div style="font-size: %.0fpx; font-weight: 700; line-height: 1.15">%s</div><div style="display: flex; gap: %dpx">%s</div><div style="display: flex; justify-content: space-between; font-size: %.1fpx; opacity: .85; direction: ltr"><span>Gudi</span><span>Gudauri · %s</span></div></div>' % (
        16 * k, 16 * k, 14 * k, 8 * k, 25 * k, 'A day of firsts' if L == 'en' else 'יום של פעם ראשונה', 18 * k, nums, 11.5 * k, DSTR)
    return o + extra + '</div>'


def ds_index():
    o = backdrop() + '<div class="xsc"><div class="xpage" style="gap: 9px">'
    o += '<div class="xhd"><b>יום של פעם ראשונה</b><span>%d ירידות · %s מ׳ למטה · %.1f ק״מ</span></div>' % (D.ST['descents'], n0(D.ST['drop_m']), D.ST['ski_dist_m'] / 1000)
    o += '<button class="play">%s לצפות בסיפור</button>' % icon(I_PLAY, 16, 2, 'currentColor')
    o += story_cards()
    o += '<div class="xsec">עוד מהיום</div><div class="xcard glass" style="box-shadow: none; padding: 4px 12px; gap: 0">%s</div>' % more_rows()
    o += '</div></div><div class="xfade" style="bottom: 0; background: linear-gradient(180deg, transparent, var(--bg))"></div>'
    o += TOP('הסיפור של היום · <span class="tmr" style="font-weight: 500">%s</span>' % DSTR)
    return o


def ds_end_of_list():
    """the same page scrolled to its end: the rest of the moments and the share picture as the finale"""
    o = backdrop() + '<div class="xsc"><div class="xpage" style="top: 112px; gap: 10px">'
    o += '<div class="xsec">עוד מהיום</div><div class="xcard glass" style="box-shadow: none; padding: 4px 12px; gap: 0">%s</div>' % more_rows()
    o += '<div class="xsec">התמונה של היום</div><div style="display: flex; gap: 14px; align-items: flex-end">%s<div style="display: flex; flex-direction: column; gap: 8px; flex: 1"><button class="pri" style="flex: none; display: flex; align-items: center; justify-content: center; gap: 8px">%s שיתוף</button><button class="sec">שמירה</button></div></div>' % (
        share_card(150, 188, abs_=False), icon(I_SHARE, 18, 2))
    o += '</div></div>' + TOP('הסיפור של היום · <span class="tmr" style="font-weight: 500">%s</span>' % DSTR)
    return o


def ds_player(which='steep'):
    """a moment opened full screen: the mountain close on its place, the descent lit, the words in a card"""
    d = D.DESC[9]
    if which == 'steep':
        V = VH; cx, cy = run_point(VH, 'Sadzele 1', .3); sc = 2.4
    lat, lon = MX['lat'], MX['lon']; px, py, _, _ = D.ll(VH, lat, lon)

    def f(V, mode):
        s = white_runs(V, mode, .6) + D.day(V, mode, w=1.6, lifts=False, alpha=.45) + D.track(V, d['t0'], d['t1'], mode, w=3.2)
        return s
    o = win('hero', 390, 844, cx, cy + 60, sc, f, day='noon', r=0)
    o += '<div style="position: absolute; left: 0; right: 0; top: 0; height: 140px; background: linear-gradient(180deg, rgba(6,12,24,.55), transparent)"></div>'
    o += '<div class="prog">%s</div>' % ''.join('<i class="%s">%s</i>' % (c, '<u></u>' if c == 'half' else '') for c in ('on', 'half', '', '', ''))
    o += '<button class="xbtn" aria-label="סגירה" style="top: 70px; left: 6px; color: #fff">%s</button>' % icon(I_X, 22, 2.2)
    o += '<div style="position: absolute; right: 16px; top: 82px; color: #fff; font-size: 13px; font-weight: 700; text-shadow: 0 1px 6px rgba(0,0,0,.5)">הסיפור של היום · 2 מתוך 5</div>'
    # the steep stretch, where it is on the run
    sx, sy = run_point(VH, 'Sadzele 1', .34)
    o += '<div class="pin" style="left: %.0fpx; top: %.0fpx"><b>28°</b><i></i></div>' % (195 + (sx - cx) * sc, 422 + (sy - cy - 60) * sc - 8)
    o += '<div class="stx glass"><span class="k">המסלול הכי תלול שלך</span><div style="display: flex; align-items: center; gap: 12px">%s<b style="direction: ltr">Sadzele 1</b><span class="num" style="margin-inline-start: auto; font-size: 44px">28°</span></div><span>עם קטע של כ-28°. המסלול הכי תלול של היום שלך.</span></div>' % sign('Sadzele 1', True)
    return o


def ds_finale():
    o = pic('hero', 'noon', 'night') + '<div style="position: absolute; inset: 0; background: rgba(6,12,24,.62)"></div>'
    o += '<div class="prog">%s</div>' % ''.join('<i class="on"></i>' for _ in range(5))
    o += '<button class="xbtn" aria-label="סגירה" style="top: 70px; left: 6px; color: #fff">%s</button>' % icon(I_X, 22, 2.2)
    o += '<div style="position: absolute; right: 16px; top: 82px; color: #fff; font-size: 13px; font-weight: 700">הסיפור של היום · 5 מתוך 5</div>'
    o += '<div style="position: absolute; left: 0; right: 0; top: 120px; text-align: center; color: #fff; font-size: 22px; font-weight: 700">התמונה של היום</div>'
    o += share_card(286, 358, top=164, left=52)
    o += '<div style="position: absolute; left: 16px; right: 16px; bottom: 24px; display: flex; flex-direction: column; gap: 10px"><div style="display: flex; gap: 8px"><button class="pri" style="display: flex; align-items: center; justify-content: center; gap: 8px">%s שיתוף</button><button class="sec" style="color: #fff; border-color: rgba(255,255,255,.4)">שמירה</button></div>' % icon(I_SHARE, 18, 2)
    o += '<button style="height: 44px; color: #fff; font-size: 15px; font-weight: 700; display: flex; align-items: center; justify-content: center; gap: 8px">%s סרטון של דקה</button></div>' % icon(I_PLAY, 15, 2, 'currentColor')
    return o


def ds_one():
    """a day the engine gave a single moment (illustrative: one short run); never an empty screen"""
    d = D.DESC[3]
    o = backdrop() + '<div class="xsc"><div class="xpage" style="gap: 10px">'
    o += '<div class="xhd"><b>יום רגוע</b><span>ירידה אחת · %s מ׳ למטה · %.1f ק״מ</span></div>' % (n0(d['drop_m']), d['dist_m'] / 1000)
    i = int(np.searchsorted(D.T, (d['t0'] + d['t1']) // 2)); x, y, _, _ = P.pt(VH, D.Q[i, 0], D.Q[i, 1], lift=2)

    def f(V, mode):
        return white_runs(V, mode, .8) + D.track(V, d['t0'], d['t1'], mode, w=3.4)
    o += '<button class="xcard glass" style="box-shadow: none; padding: 0; overflow: hidden; gap: 0; text-align: start">%s<span style="display: flex; flex-direction: column; gap: 4px; padding: 12px 14px 14px"><b style="font-size: 18px">המסלול הכי תלול שלך</b><span class="sub" style="font-size: 14px"><bdi>Tatra 2</bdi>, עם קטע של כ-20°. המסלול הכי תלול של היום שלך.</span></span></button>' % win('hero', 362, 210, x, y, 2.3, f, day='noon', r=0)
    o += '<div class="xsec">היום</div>' + timebar_one(d)
    o += '<div class="xsec">התמונה של היום</div><div style="display: flex; gap: 14px; align-items: flex-end">%s<div style="display: flex; flex-direction: column; gap: 8px; flex: 1"><button class="pri" style="flex: none; display: flex; align-items: center; justify-content: center; gap: 8px">%s שיתוף</button><button class="sec">שמירה</button></div></div>' % (
        share_card(120, 150, abs_=False), icon(I_SHARE, 18, 2))
    o += '</div></div>' + TOP('הסיפור של היום · <span class="tmr" style="font-weight: 500">%s</span>' % DSTR)
    return o


def timebar_one(d):
    v = [round((d['t1'] - d['t0']) / 60000), 8, 1, 6]
    cols = ['linear-gradient(90deg,#A98BEE,#E0246E)', 'var(--ink2)', '#E2B13C', 'var(--line)']
    bar = ''.join('<i style="flex: %d; background: %s"></i>' % (x, c) for x, c in zip(v, cols))
    lg = ''.join('<span><i style="background: %s"></i><b>%d</b> דק׳ %s</span>' % (c, x, w) for x, c, w in zip(v, cols, ('גלישה', 'ברכבלים', 'בתורים', 'הפסקות והליכה')))
    return '<div class="xtb glass" style="box-shadow: none"><div class="bar">%s</div><div class="lg">%s</div></div>' % (bar, lg)


# ---------------------------------------------------------------- DV: the one-minute video plays in its card
SHW = {'he': dict(title='תמונה לשיתוף', photo='תמונה', video='סרטון של דקה', share='שיתוף', save='שמירה', making='מכינים', saving='שומרים', saved='נשמר בגלריה', failed='לא נשמר · שוב',
                  link='לראות את היום על ההר', eg='דוגמה'),
       'en': dict(title='Image to share', photo='Picture', video='One-minute video', share='Share', save='Save', making='Preparing', saving='Saving', saved='Saved to gallery', failed='Not saved · retry',
                  link='See the day on the mountain', eg='Example')}
I_PHOTO = '<rect x="3.500" y="5" width="17" height="14" rx="3"/><circle cx="9" cy="10" r="1.700"/><path d="m5 17.500 5-5 3 3 2.500-2.500 3.500 3.500"/>'
I_SAVE = '<path d="M12 4v11"/><path d="m7.500 10.500 4.500 4.500 4.500-4.500"/><path d="M5 19.500h14"/>'
I_CHECK = '<path d="m5 12.500 4.500 4.500L19 7.500"/>'


def share_sheet(kind, state, L='he'):
    """the whole sheet: what to send (two tiles), then share and save; the progress lives in the button pressed"""
    T = SHW[L]; vid = kind == 'video'
    tile = lambda on, ic_, w: '<button class="stile%s">%s<span>%s</span></button>' % (' on' if on else '', icon(ic_, 20, 1.9, 'currentColor' if ic_ == I_PLAY else 'none'), w)
    o = '<div class="sheet glass" style="gap: 12px; padding-bottom: 14px"><div class="grab"></div><div class="stog">%s%s</div>' % (tile(not vid, I_PHOTO, T['photo']), tile(vid, I_PLAY, T['video']))
    pct = 64
    if state == 'make':
        sh = '<button class="sbt pri2 mk"><i class="f" style="width: %d%%"></i><span>%s<b>%s</b><em>%d%%</em></span></button>' % (pct, icon(I_SHARE, 19, 2), T['making'], pct)
    else:
        sh = '<button class="sbt pri2"><span>%s<b>%s</b></span></button>' % (icon(I_SHARE, 19, 2), T['share'])
    if state == 'save':
        sv = '<button class="sbt sec2 mk"><i class="f" style="width: %d%%"></i><span>%s<b>%s</b><em>%d%%</em></span></button>' % (pct, icon(I_SAVE, 19, 2), T['saving'], pct)
    elif state == 'saved':
        sv = '<button class="sbt ok2"><span>%s<b>%s</b></span></button>' % (icon(I_CHECK, 20, 2.6), T['saved'])
    elif state == 'failed':
        sv = '<button class="sbt bad2"><span>%s<b>%s</b></span></button>' % (icon(I_REPLAY, 19, 2.2), T['failed'])
    else:
        sv = '<button class="sbt sec2"><span>%s<b>%s</b></span></button>' % (icon(I_SAVE, 19, 2), T['save'])
    return o + '<div class="srow2">%s%s</div></div>' % (sh, sv)


def dv(state='play', kind='video', L='he'):
    T = SHW[L]; vid = kind == 'video'
    frac = {'play': .42, 'end': 1.0, 'make': .7, 'save': .55, 'saved': .55, 'failed': 1.0, 'idle': 1.0}[state] if vid else 1.0
    until = int(T0 + (T1 - T0) * frac) if frac < 1 else None
    o = pic('hero', 'noon', 'night') + veil(0, 844, 'var(--veil)')
    o += '<div class="recp glass">%s<span>%s</span><span style="margin-inline-start: auto"><span class="eg">%s</span></span></div>' % (icon(I_BACK if L == 'he' else '<path d="m15 6-6 6 6 6"/>', 20, 2), T['title'], T['eg'])
    ex = ''
    if vid:
        secs = int(60 * frac); tm = '%d:%02d' % (secs // 60, secs % 60)
        ex += '<div style="position: absolute; left: 0; right: 0; bottom: 0; height: 4px; background: rgba(255,255,255,.28); direction: %s"><i style="display: block; height: 100%%; width: %d%%; background: #fff"></i></div>' % ('rtl' if L == 'he' else 'ltr', int(frac * 100))
        ex += '<span style="position: absolute; top: 12px; %s: 12px; height: 26px; padding: 0 9px; border-radius: 13px; background: rgba(8,14,28,.55); color: #fff; font-size: 12px; font-weight: 700; display: flex; align-items: center; direction: ltr">%s / 1:00</span>' % ('left' if L == 'he' else 'right', tm)
        if state in ('end', 'failed'):
            ex += '<button aria-label="שוב" style="position: absolute; left: 50%%; top: 42%%; transform: translate(-50%%,-50%%); width: 72px; height: 72px; border-radius: 50%%; background: rgba(8,14,28,.6); color: #fff; display: flex; align-items: center; justify-content: center">%s</button>' % icon(I_REPLAY, 30, 2.2)
        else:
            ex += '<button aria-label="השהיה" style="position: absolute; top: 6px; %s: 6px; width: 44px; height: 44px; border-radius: 50%%; background: rgba(8,14,28,.55); color: #fff; display: flex; align-items: center; justify-content: center">%s</button>' % ('right' if L == 'he' else 'left', icon(I_PAUSE, 20, 2.6))
    o += share_card(until=until, top=112, left=36, extra=ex, L=L)
    if vid:
        o += '<button class="vlink">%s %s</button>' % (icon(I_MTN, 18, 2), T['link'])
    return o + share_sheet(kind, state, L)


# ---------------------------------------------------------------- the phones and the overview
def phones():
    a = seg_i(lambda s: s['type'] == 'lift' and s['name'] == 'Firni')
    b = seg_i(lambda s: s['type'] == 'lift' and s['name'] == 'Sadzele')
    tx, ty = run_point(VZ, 'Tatra 1', .55)
    return {
        'DM1.dc.html': ('היום שלי, אחרי: הקווים העמומים של היום, מעט חזקים יותר (45%)', daymap(), 'day'),
        'DM2.dc.html': ('אחרי, בלילה (40%)', daymap(), 'night'),
        'DM3.dc.html': ('לפני: כמו באפליקציה היום (32%), להשוואה', daymap(before=True), 'day'),
        'DT1.dc.html': ('הרכס של היום: המספרים, הרכס, לאן הלך היום, ותחילת ציר הזמן', dt_top(), 'day'),
        'DT2.dc.html': ('בגלילה: הרכס מתכווץ לפס למעלה, וההפסקה הארוכה', dt_mid(a - 1, a + 12), 'night'),
        'DT3.dc.html': ('טקסט גדול: השמות נשברים לשורה, אף פעם לא נחתכים', dt_mid(b - 1, b + 6, k=1.3), 'day'),
        'DT4.dc.html': ('The ridge of the day in English', dt_top('en'), 'night', 'en'),
        'DT5.dc.html': ('כל סוגי השורות, והכלל של בית הקפה', dt_kinds(), 'day'),
        'DS1.dc.html': ('הסיפור של היום: כל הרגעים, כל כרטיס נפתח', ds_index(), 'day'),
        'DS2.dc.html': ('רגע פתוח: ההר מקרוב על המקום, והמילים בכרטיס', ds_player(), 'day'),
        'DS3.dc.html': ('הסוף: התמונה של היום', ds_finale(), 'night'),
        'DS4.dc.html': ('גלילה לסוף הרשימה: עוד מהיום, והתמונה', ds_end_of_list(), 'night'),
        'DS5.dc.html': ('יום עם רגע אחד: כרטיס גדול, היום והתמונה', ds_one(), 'day'),
        'DV1.dc.html': ('הסרטון מתנגן בתוך הכרטיס', dv('play'), 'night'),
        'DV2.dc.html': ('נגמר: שוב, וקישור מפורש להר', dv('end'), 'night'),
        'DV3.dc.html': ('שיתוף בזמן הניגון: הסרטון ממשיך, הכפתור מתמלא', dv('make'), 'day'),
        'DV4.dc.html': ('תמונה: הדף בלי משפט ההסבר', dv('idle', 'image'), 'day'),
        'DV5.dc.html': ('שמירת סרטון: ההתקדמות בתוך הכפתור', dv('save'), 'night'),
        'DV6.dc.html': ('נשמר: וי ו"נשמר בגלריה"; נגיעה פותחת', dv('saved'), 'day'),
        'DV7.dc.html': ('לא נשמר: אותו כפתור מנסה שוב', dv('failed', 'image'), 'night'),
        'DV8.dc.html': ('The share sheet in English, saved', dv('saved', 'video', 'en'), 'night', 'en'),
    }


HB = 1320


def dp00():
    row = lambda *c: '<tr>%s</tr>' % ''.join('<td style="padding: 9px 10px; vertical-align: top; border-top: 1px solid #D9DEE4">%s</td>' % x for x in c)
    th = '<tr>%s</tr>' % ''.join('<th style="text-align: start; padding: 6px 10px; font-size: 13px; color: #3C4753">%s</th>' % x for x in ('', 'מה ראית', 'מה מצאתי', 'ההצעה', 'לוחות'))
    tbl = ('<table style="border-collapse: collapse; width: 100%; font-size: 15px; line-height: 1.45">' + th
           + row('<b>1</b>', '"היום שלי": לא ברור איפה המסלולים ואיפה גלשת', 'על ההר החי כל המסלולים מוסתרים כשהיום מצויר.', '<b>תיקון (פיני, 27.11):</b> בלי קו לבן. אותם קווים עמומים בצבע המסלול שיש באפליקציה היום (32%), רק מעט חזקים: 45% ביום, 40% בלילה, ופי 1.2 ברוחב. הם נשארים חלק מהשלג. DM3 מראה את היום להשוואה.', 'DM1 עד DM3')
           + row('<b>2</b>', '"הרכס של היום": גרף ורשימת טקסט, ושם נחתך ("Goodaur")', 'המנתח יודע: רכבל עם התור לפניו, ירידה (מסלולים, עצירות, קטע מחוץ למסלולים, דקה בלי קליטה), הפסקה ארוכה, הליכה. הוא לא יודע מה עשית בהפסקה.', 'ארבעה מספרים, הרכס עם ציר גובה ושעות ופס ההפסקה, "לאן הלך היום" בפס אחד, ואז ציר זמן עם סמל לכל סוג. הירידה היא כרטיס עם פס המהירות שלה. שמות נשברים לשורה, אף פעם לא נחתכים. בגלילה הרכס מתכווץ לפס שמראה איפה אתה ביום.', 'DT1 עד DT5')
           + row('<b>3</b>', '"הסיפור של היום": כרטיס אחד שלא נוגעים בו, ומסך ריק', '<b>זה לא הנתונים.</b> המנוע נותן ליום הדוגמה 11 רגעים, ובוחר 4 לסיפור (השחור הראשון, התלול, המהיר, רעיון למחר). גם ירידה אחת נותנת 3 או 4. המסך מראה פחות: זו בעיה בבנייה, לבדוק באמולטור לפי שורת היומן "redesign day story".', 'כל רגעי הסיפור ככרטיסים שנפתחים, "עוד מהיום" עם שאר הרגעים, "לצפות בסיפור" שמנגן אותם במסך מלא על ההר, והתמונה לשיתוף בסוף. כרטיס שיורד (נפילה, מחוץ למסלולים) לא משאיר חור: הבא בתור נכנס.', 'DS1 עד DS5')
           + row('<b>4</b>', '"תמונה לשיתוף": נגיעה בעיגול הניגון מוציאה מהדף', '<b>באג.</b> העיגול רק מצויר, ונגיעה בו עוברת לשכבה שסוגרת את הדף. גם אין נגן. ו"שמירה" שומרת לגלריה בלי לומר דבר: רק אחוז, ואז כלום.', 'הכרטיס מנגן במקום, מאותן תמונות של הסרטון, עם השהיה, פס התקדמות ו"שוב" בסוף. נגיעה בכרטיס לא יוצאת אף פעם; "לראות את היום על ההר" הוא קישור נפרד. הגיליון: שני אריחים במקום המתג, בלי משפט ההסבר, וההתקדמות בתוך הכפתור שנלחץ. בסוף: וי ו"נשמר בגלריה" (נגיעה פותחת), או "לא נשמר · שוב".', 'DV1 עד DV8')
           + '</table>')
    asks = ''.join('<p class="q"><span class="qn">%d</span><span>%s</span></p>' % (i + 1, t) for i, t in enumerate([
        '<b>המסלולים בקו לבן דק</b> מאחורי היום, בלי שמות, ונגיעה מראה שם? (ההמלצה: כן)',
        '<b>הרכס של היום</b> כמו בלוחות: מספרים, רכס, "לאן הלך היום" וציר זמן עם סמלים? (כן)',
        '<b>כוס קפה רק כשההפסקה ליד בית קפה מקובץ המקומות</b>, ובלי זה סמל הפסקה כללי? (כן; ביום הדוגמה אין בית קפה ליד ההפסקה, ולכן רואים את הכללי)',
        '<b>הסיפור:</b> כל הרגעים ככרטיסים שנפתחים, נגן במסך מלא, והתמונה בסוף? (כן)',
        '<b>הסרטון מתנגן בכרטיס</b>, הקישור להר נפרד, והגיליון החדש: אריחים, התקדמות בכפתור, "נשמר בגלריה" ו"לא נשמר · שוב", בלי משפט ההסבר? (כן; אפשר לבנות בלי לחכות לשאר)']))
    strings = li('<b>קיימים:</b> כל רגעי הסיפור (<span dir="ltr">moment.*</span>), "תור {min} דק׳", "הפסקה ארוכה · {min} דק׳", "עצירה אחת", "חלק מחוץ למסלולים", "דקה בלי קליטה", "הליכה, {m} מ׳", "לאן הלך היום", "לאט" / "מהר", "סרטון של דקה", "שמירה".',
                  '<b>חדשים, לסשן התוכן אחרי האישור:</b> "בוקר", "צהריים", "בראש {lift}", "{n} עצירות", "שעות על ההר", "ק״מ גלישה", "גלישה" / "ברכבלים" / "בתורים" / "הפסקות והליכה", "לצפות בסיפור", "עוד מהיום", "התמונה של היום", "{i} מתוך {n}", "לראות את היום על ההר", "מכינים", "שומרים", "נשמר בגלריה", "לא נשמר · שוב", "השהיה", "שוב". יוצא: משפט ההסבר בגיליון השיתוף.')
    honest = ('כל המספרים, הזמנים, השמות, הרגעים והניסוחים מיום הדוגמה ומהפלט האמיתי של המנתח ושל מנוע הרגעים. '
              'שני דברים מומחשים ומסומנים כך: לוח DT5 (שורות לדוגמה, כולל <bdi>Ice Bar</bdi> ו-<bdi>Gradus</bdi> כדי להראות את הכלל), ולוח DS5 (יום עם רגע אחד; במקרי הבדיקה המנוע לא נותן אף פעם רגע אחד, אבל המסך צריך לעמוד בזה). '
              'יום בלי ירידות בכלל הוא מסך הסוף הריק שכבר אושר (EM); אין בו כפתור לסיפור. נבדק בכלי העיצוב, לא בטלפון.')
    inner = ('<p class="sec">עמודי היום · 27.11.2026 · ממתין לפיני</p>'
             '<h1>אחרי יום הגלישה: לראות איפה המסלולים, לקרוא את היום, לשמוע את הסיפור, ולשתף בלי לצאת</h1>'
             '<div class="card">%s</div>'
             '<div class="rowx" style="align-items: stretch"><div class="card" style="flex: 1.2"><h2>לפיני</h2>%s</div><div class="card" style="flex: 1"><h2>הטקסטים</h2>%s</div></div>'
             '<div class="card"><h2>בכנות</h2><p class="p">%s</p><p class="p">המסירה: <span dir="ltr">design/handoff/2026-11-27-redesign-day-pages/</span></p></div>' % (tbl, asks, strings, honest))
    open(os.path.join(PROJ, 'DP00-DayPages.dc.html'), 'w', encoding='utf8').write(board('עמודי היום', 1280, HB, wbox(1280, HB, inner)))


FILES = ['DP00-DayPages.dc.html'] + ['DM%d.dc.html' % i for i in range(1, 4)] + ['DT%d.dc.html' % i for i in range(1, 6)] + ['DS%d.dc.html' % i for i in range(1, 6)] + ['DV%d.dc.html' % i for i in range(1, 9)]
CODE = re.compile(r'^(DP|DM|DT|DS|DV)\d')


def write_canvas(cj, T):
    j = json.load(open(cj, encoding='utf8'))
    clash = [f for f in j['boards'] if f not in FILES and CODE.match(f)]
    assert not clash, clash
    for fn in FILES: j['boards'].pop(fn, None)
    j['order'] = [f for f in j['order'] if f not in FILES]
    place = [('DP00-DayPages.dc.html', 0, 0, 1280, HB, 'ארבעת הדברים, מה מצאתי, ההצעה והשאלות')]
    y = HB + 160
    for fam in ('DM', 'DT', 'DS', 'DV'):
        fs = [f for f in FILES[1:] if f.startswith(fam)]
        for k, fn in enumerate(fs):
            place.append((fn, 470 * k, y, 390, 844, T[fn][0]))
        y += 844 + 200
    for fn, x, yy, w, h, t in place:
        code = fn.split('.dc.html')[0].split('-')[0]
        j['boards'][fn] = dict(x=x, y=yy, w=w, h=h, title='%s (%s)' % (t, code), page=PAGE)
        j['order'].append(fn)
    if not any(pg['id'] == PAGE for pg in j['pages']):
        j['pages'].insert(0, dict(id=PAGE, name=PAGE_NAME))
    j['notes']['t' + PAGE] = dict(kind='title1', maxW=1600, page=PAGE, x=0, y=-300, text='עמודי היום')
    for k, (fam, nm) in enumerate((('DM', '1 · היום שלי: המסלולים בלבן'), ('DT', '2 · הרכס של היום'), ('DS', '3 · הסיפור של היום'), ('DV', '4 · הסרטון בכרטיס'))):
        j['notes']['t%s%s' % (PAGE, fam)] = dict(kind='title2', maxW=1600, page=PAGE, x=0, y=HB + 160 + k * 1044 - 80, text=nm)
    j['launch'] = dict(view='canvas', page=PAGE)
    open(cj, 'w', encoding='utf8').write(json.dumps(j, ensure_ascii=False, indent=1) + '\n')


def main_():
    args = sys.argv[1:]
    pv = None
    if '--preview' in args:
        i = args.index('--preview'); pv = (args[i + 1], args[i + 2]); del args[i:i + 3]
    cj = args[0] if args else os.path.join(PROJ, 'canvas.json')
    have = json.load(open(cj, encoding='utf8'))['boards']
    assert not [f for f in have if f not in FILES and CODE.match(f)], 'board code taken'
    T = phones()
    for fn, v in T.items():
        title, body, fx = v[:3]; lang = v[3] if len(v) > 3 else 'he'
        phone(fn, title, body, night=fx == 'night', fixed=None, lang=lang)
        if fx:   # the board opens in its state; the switch still works
            p = os.path.join(PROJ, fn); s = open(p, encoding='utf8').read()
            s = s.replace('"default":false', '"default":%s' % ('true' if fx == 'night' else 'false')).replace('?? false', '?? %s' % ('true' if fx == 'night' else 'false'))
            open(p, 'w', encoding='utf8').write(s)
    dp00()
    write_canvas(cj, T)
    if pv:
        import dk_boards as DK
        DK.FILES = FILES; DK.preview(*pv)
    print('ok', cj)


if __name__ == '__main__':
    main_()
