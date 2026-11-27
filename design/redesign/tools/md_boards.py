#!/usr/bin/env python3
"""P.23 and P.24 (Pini, 27.11.2026): a recording with no real descent is never saved, and the end screen says so;
"הימים שלי" gets a clearer name and, with "השיאים שלך", becomes an experience instead of white rows.
Boards: MY00 (the overview), NS1-NS3 (the end of a recording that is not saved; replaces EM, whose "מחיקה" goes),
MY1-MY3 (the days list: a trip, at night, before the first day), RS1-RS3 (the records: day, night, none yet).
Data: the sample day (day4). The four trip days in MY1/MY2 are cut from the sample day's descents (illustrative, tagged).
Usage: python3 design/redesign/tools/md_boards.py [canvas.json] [--preview <out dir> <blobs dir>]"""
import os, sys, json, re, datetime
import numpy as np
import dp_boards as DP
from dp_boards import (D, P, VH, MB, B4, icon, white_runs, mini_ridge, EG, board, wbox, PROJ, li, ll_of,
                       I_CHEV, I_BACK, I_MTN, I_DOWN)
import em_boards as EMB
import peak_boards as PB

PAGE = 'mydays'
PAGE_NAME = 'ימי הגלישה שלי והשיאים (27.11)'

I_STAR = '<path d="m12 3.500 2.600 5.400 5.900.800-4.300 4.100 1.100 5.800L12 16.800l-5.300 2.800 1.100-5.800-4.300-4.100 5.900-.800z"/>'
I_BOLT = '<path d="M13 3 5 13.500h6L10 21l8-10.500h-6z"/>'
I_RULER = '<path d="M4 17 17 4l3 3L7 20z"/><path d="m8 13 1.500 1.500M11 10l1.500 1.500M14 7l1.500 1.500"/>'
I_SLOPE = '<path d="M3 19h18L5 7z"/><path d="M8 19a5 5 0 0 0-1.200-3.200"/>'
I_REC = '<circle cx="12" cy="12" r="5.500" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="9"/>'
I_CUT = '<path d="M4 12h5M15 12h5"/><path d="m10 9 4 6M14 9l-4 6"/>'
I_LOCK = '<rect x="5" y="10.500" width="14" height="10" rx="2.500"/><path d="M8.500 10.500V8a3.500 3.500 0 0 1 7 0v2.500"/>'
I_NOSAVE = '<path d="M5 4h11l3 3v13H5z"/><path d="M8 4v5h7V4"/><path d="m3 3 18 18"/>'

MCSS = """
.H .mybg{position:absolute;inset:0;background:var(--bg);opacity:.86}
.H .night .mybg{opacity:.9}
.H .myscroll{position:absolute;left:12px;right:12px;top:106px;bottom:0;display:flex;flex-direction:column;gap:10px;overflow:hidden}
.H .mytrip{border-radius:22px;padding:14px 16px 12px;display:flex;flex-direction:column;gap:10px}
.H .mytrip .k{font-size:12.5px;color:var(--ink2);display:flex;align-items:center;gap:6px}
.H .mynums{display:flex;justify-content:space-between;gap:6px}
.H .mynums div{display:flex;flex-direction:column;gap:1px;min-width:0}
.H .mynums b{font-size:24px;font-weight:700;line-height:1.1;font-variant-numeric:tabular-nums;direction:ltr;text-align:start}
.H .mynums span{font-size:12px;color:var(--ink2);white-space:nowrap}
.H .mybars{display:flex;align-items:flex-end;gap:8px;height:58px;direction:rtl}
.H .mybars .c{flex:1;display:flex;flex-direction:column;align-items:stretch;gap:4px;height:100%;justify-content:flex-end}
.H .mybars .c i{display:block;border-radius:6px 6px 3px 3px;background:linear-gradient(180deg,#E0246E,#B565E8 55%,#C9B6F2)}
.H .night .mybars .c i{background:linear-gradient(180deg,#FF5C9A,#C08CFF 55%,#5D4C92)}
.H .mybars .c span{font-size:11px;color:var(--ink2);text-align:center;font-variant-numeric:tabular-nums}
.H .mybars .c.on span{color:var(--ink);font-weight:700}
.H .myrec{display:flex;align-items:center;gap:8px;border-top:1px solid var(--line);padding-top:10px;font-size:13.5px;font-weight:700}
.H .myrec .ch{display:inline-flex;align-items:center;gap:5px;height:28px;border-radius:14px;padding:0 10px;font-size:13px;font-weight:600;background:rgba(107,63,212,.09);color:var(--ink);white-space:nowrap}
.H .night .myrec .ch{background:rgba(183,156,255,.14)}
.H .myrec .ch bdi{font-variant-numeric:tabular-nums}
.H .myrec .go{margin-inline-start:auto;color:var(--acc);display:flex}
.H .mysec{font-size:12.5px;color:var(--ink2);font-weight:600;padding:4px 6px 0}
.H .myday{display:flex;gap:12px;border-radius:22px;padding:8px;align-items:stretch}
.H .myday .win{flex:none}
.H .myday .tx{flex:1;min-width:0;display:flex;flex-direction:column;gap:6px;padding:4px 2px 2px}
.H .myday .dt{display:flex;align-items:baseline;gap:8px}
.H .myday .dt b{font-size:19px;font-weight:700;line-height:1.15}
.H .myday .dt span{font-size:12.5px;color:var(--ink2);font-variant-numeric:tabular-nums;direction:ltr;margin-inline-start:auto}
.H .myday .nm{display:flex;gap:12px}
.H .myday .nm div{display:flex;flex-direction:column}
.H .myday .nm b{font-size:16px;font-weight:700;font-variant-numeric:tabular-nums;line-height:1.15}
.H .myday .nm span{font-size:11.5px;color:var(--ink2);white-space:nowrap}
.H .myday .tg{display:flex;gap:6px;flex-wrap:wrap}
.H .myday .tg span{display:inline-flex;align-items:center;gap:4px;height:22px;border-radius:11px;padding:0 8px;font-size:11.5px;font-weight:600;white-space:nowrap}
.H .myday .tg .best{background:rgba(224,36,110,.1);color:#B0174F}
.H .night .myday .tg .best{background:rgba(255,92,154,.16);color:#FF8DB8}
.H .myday .tg .cut{background:rgba(120,134,154,.14);color:var(--ink2)}
.H .myday.sample{opacity:.8}
.H .mystrip{display:block;width:100%;height:8px;border-radius:4px;direction:ltr}
.H .myempty{display:flex;flex-direction:column;align-items:center;gap:6px;text-align:center;padding:18px 10px 6px}
.H .myempty b{font-size:22px;font-weight:700}
.H .mypri{height:52px;border-radius:26px;border:0;background:var(--acc);color:var(--onacc);font:inherit;font-size:16px;font-weight:700;display:flex;align-items:center;justify-content:center;gap:9px}
.H .rshero{border-radius:24px;overflow:hidden;position:relative}
.H .rshero .lab{position:absolute;left:0;right:0;bottom:0;padding:34px 16px 12px;display:flex;align-items:flex-end;gap:10px;background:linear-gradient(180deg,rgba(255,255,255,0),rgba(255,255,255,.92) 55%)}
.H .night .rshero .lab{background:linear-gradient(180deg,rgba(14,20,40,0),rgba(14,20,40,.92) 55%)}
.H .rsv{display:flex;align-items:baseline;gap:5px;direction:ltr}
.H .rsv b{font-size:44px;font-weight:700;line-height:1;font-variant-numeric:tabular-nums}
.H .rsv i{font-style:normal;font-size:15px;color:var(--ink2);font-weight:600}
.H .rsm{display:flex;flex-direction:column;gap:2px;min-width:0;flex:1}
.H .rsm b{font-size:15px;font-weight:700;display:flex;align-items:center;gap:6px}
.H .rsm span{font-size:12.5px;color:var(--ink2)}
.H .rsnew{display:inline-flex;height:20px;align-items:center;border-radius:10px;padding:0 7px;font-size:11px;font-weight:700;background:#E0246E;color:#fff}
.H .rsgrid{display:grid;grid-template-columns:1fr 1fr;gap:10px}
.H .rst{border-radius:20px;padding:12px 12px 11px;display:flex;flex-direction:column;gap:7px;min-width:0}
.H .rst .h{display:flex;align-items:center;gap:6px;font-size:12.5px;color:var(--ink2);font-weight:600}
.H .rst .h svg{color:var(--acc)}
.H .rst .rsv b{font-size:30px}
.H .rst .m{font-size:12px;color:var(--ink2);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.H .rst.wide{grid-column:span 2;flex-direction:row;align-items:center;gap:12px}
.H .rst svg.vis{display:block}
.H .rsfoot{display:flex;align-items:center;gap:8px;color:var(--ink2);font-size:12.5px;padding:2px 6px}
.H .rsghost{opacity:.45}
"""
MB.CSS = MB.CSS + MCSS

DESC = D.DESC
WD = 'אבגדהוש'


def day_of(dd, d, mon=1, y=2027):
    w = datetime.date(y, mon, dd).isoweekday() % 7
    return 'יום %s׳' % WD[w], '%d.%d' % (dd, mon)


def hhmm(t):
    x = datetime.datetime.fromtimestamp(t / 1000, D.TZ); return '%02d:%02d' % (x.hour, x.minute)


def n0(v):
    return '{:,}'.format(int(round(v)))


def stats_of(ds):
    return dict(n=len(ds), drop=sum(d['drop_m'] for d in ds), km=sum(d['dist_m'] for d in ds) / 1000,
                vmax=max(d['max_kmh'] for d in ds), t0=ds[0]['t0'], t1=ds[-1]['t1'])


# the trip: four days cut from the sample day's descents (illustrative), newest first
TRIP = [dict(dd=14, ds=DESC[0:4], cut='נקטע ב-%s' % hhmm(DESC[3]['t1'] + 240000)),
        dict(dd=13, ds=DESC[5:12]),
        dict(dd=12, ds=DESC[0:7]),
        dict(dd=11, ds=DESC, best='שיא מהירות')]
for t in TRIP: t.update(stats_of(t['ds']))


def strip_days(ds, mode, w=200, h=8, bins=40):
    """one speed strip over the day's descents only (no lifts): where the day was fast"""
    idx = []
    for d in ds:
        a, b = D.idx(d['t0'], d['t1']); idx.append((a, b))
    tot = sum(b - a for a, b in idx); o = ''; seq = np.concatenate([D.KMH[a:b] for a, b in idx])
    for j in range(bins):
        i0 = j * tot // bins; i1 = max(i0 + 1, (j + 1) * tot // bins)
        o += '<rect x="%.1f" y="0" width="%.1f" height="%d" fill="%s"/>' % (j * w / bins, w / bins + .5, h, D.RAMP[mode][D.band(float(np.mean(seq[i0:i1])))])
    return '<svg class="mystrip" viewBox="0 0 %d %d" preserveAspectRatio="none">%s</svg>' % (w, h, o)


def strip2(ds):
    return '<span class="d">%s</span><span class="n">%s</span>' % (strip_days(ds, 'day'), strip_days(ds, 'night'))


def fitwin(ds, w, h, r=16, pad=1.25, extra=None):
    """a window on the painted mountain that frames the given descents, the runs faint white behind them"""
    pts = np.array([ll_of(t) for d in ds for t in np.linspace(d['t0'], d['t1'], 12)])
    x0, y0 = pts.min(0); x1, y1 = pts.max(0)
    sc = min(w / max(40, (x1 - x0) * pad), h / max(40, (y1 - y0) * pad), 2.4)
    sc = max(sc, w / VH['W'], h / VH['H'])   # the window never shows past the picture
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2

    def f(V, mode):
        s = white_runs(V, mode, .8 / sc ** .5)
        for d in ds: s += D.track(V, d['t0'], d['t1'], mode, w=3.0 / sc ** .4)
        return s + (extra(V, mode) if extra else '')
    return B4.win('hero', w, h, cx, cy, sc, f, day='noon', r=r)


def top(t):
    return ('<div class="recp glass">%s<span style="font-size: 16px; font-weight: 700">%s</span><span style="margin-inline-start: auto">%s</span></div>'
            % (icon(I_BACK, 20, 2), t, EG))


def bg():
    return DP.pic('hero', 'noon', 'night') + '<div class="mybg"></div>'


def day_card(t, sample=False):
    wd, dm = day_of(t['dd'], t)
    tags = ''
    if t.get('best'): tags += '<span class="best">%s%s</span>' % (icon(I_STAR, 12, 2), t['best'])
    if t.get('cut'): tags += '<span class="cut">%s%s</span>' % (icon(I_CUT, 12, 2), t['cut'])
    title = ('יום לדוגמה %s' % EG) if sample else '%s · %s' % (wd, dm)
    nm = ('<div class="nm"><div><b>%d</b><span>%s</span></div><div><b>%s</b><span>מ׳ למטה</span></div><div><b>%.1f</b><span>ק״מ</span></div></div>'
          % (t['n'], 'ירידה' if t['n'] == 1 else 'ירידות', n0(t['drop']), t['km']))
    return ('<div class="myday glass%s">%s<div class="tx"><div class="dt"><b>%s</b><span>%s–%s</span></div>%s%s%s</div></div>'
            % (' sample' if sample else '', fitwin(t['ds'], 104, 104, 16), title, hhmm(t['t0']), hhmm(t['t1']), nm,
               strip2(t['ds']), '<div class="tg">%s</div>' % tags if tags else ''))


def trip_head():
    n = sum(t['n'] for t in TRIP); drop = sum(t['drop'] for t in TRIP); km = sum(t['km'] for t in TRIP)
    mx = max(t['drop'] for t in TRIP)
    bars = ''.join('<div class="c%s"><i style="height: %.0fpx"></i><span>%d.1</span></div>' % (' on' if t['dd'] == 14 else '', 6 + 34 * t['drop'] / mx, t['dd'])
                   for t in sorted(TRIP, key=lambda t: t['dd']))
    best = D.MOM['history']['best']
    rec = ('<div class="myrec">%s<span>השיאים שלך</span><span class="ch"><bdi>%.1f</bdi> קמ״ש</span><span class="ch"><bdi>%.1f</bdi> ק״מ</span><span class="go">%s</span></div>'
           % (icon(I_STAR, 16, 2), best['max_kmh'], best['longest_m'] / 1000, icon(I_CHEV, 20, 2)))
    return ('<div class="mytrip glass"><span class="k">%s<span>גודאורי · ינואר 2027</span></span>'
            '<div class="mynums"><div><b>%d</b><span>ימים</span></div><div><b>%d</b><span>ירידות</span></div><div><b>%s</b><span>מ׳ למטה</span></div><div><b>%.0f</b><span>ק״מ גלישה</span></div></div>'
            '<div class="mybars">%s</div>%s</div>' % (icon(I_MTN, 14, 2), len(TRIP), n, n0(drop), km, bars, rec))


SAMPLE = dict(dd=11, **stats_of(DESC)); SAMPLE['ds'] = DESC


def my_list():
    o = bg() + top('ימי הגלישה שלי')
    o += '<div class="myscroll">%s%s</div>' % (trip_head(), ''.join(day_card(t) for t in TRIP))
    return o


def my_list_end():
    """scrolled to the end: the oldest day and the sample day, kept apart and dimmed"""
    o = bg() + top('ימי הגלישה שלי')
    o += ('<div class="myscroll" style="top: 106px">%s%s<div class="mysec">יום לדוגמה, לא נספר בשיאים</div>%s</div>'
          % (day_card(TRIP[2]), day_card(TRIP[3]), day_card(SAMPLE, sample=True)))
    return o


def my_first():
    o = bg() + top('ימי הגלישה שלי')
    big = fitwin(DESC, 366, 300, 22, pad=1.1)
    o += ('<div class="myscroll"><div class="myempty"><b>עוד אין ימי גלישה</b></div>'
          '<div class="rshero">%s<div class="lab"><div class="rsm"><b>יום לדוגמה %s</b><span>%d ירידות · %s מ׳ למטה · %.1f ק״מ</span></div><span style="color: var(--acc)">%s</span></div></div>'
          '<div style="flex: 1"></div><button class="mypri" style="margin-bottom: 24px">%s<span>להקליט יום גלישה</span></button></div>'
          % (big, EG, SAMPLE['n'], n0(SAMPLE['drop']), SAMPLE['km'], icon(I_CHEV, 22, 2), icon(I_REC, 20, 2)))
    return o


# ---------------------------------------------------------------- RS: the records
BEST = D.MOM['history']['best']
LONG = max(DESC, key=lambda d: d['dist_m'])
FAST = max(DESC, key=lambda d: d['max_kmh'])


def ridge_vis(mode):
    r, X, Y = D.ribbon(150, 34, mode, pad=2, sw=1.4)
    return '<svg class="vis" viewBox="0 0 150 34" width="100%%" height="34" fill="none" stroke-linecap="round" style="direction: ltr">%s</svg>' % r


def ticks_vis(mode):
    o = ''
    for i, d in enumerate(DESC):
        hgt = 6 + 24 * d['drop_m'] / 1113
        o += '<rect x="%.1f" y="%.1f" width="7" height="%.1f" rx="2" fill="%s"/>' % (i * 12.4, 32 - hgt, hgt, D.RAMP[mode][D.band(d['max_kmh'] * .8)])
    return '<svg class="vis" viewBox="0 0 150 32" width="100%%" height="32" style="direction: ltr">%s</svg>' % o


def slope_vis(mode, deg=28):
    import math
    c = '#6B3FD4' if mode == 'day' else '#B79CFF'; L = 120; h = L * math.tan(math.radians(deg))
    h = min(h, 30)
    return ('<svg class="vis" viewBox="0 0 150 34" width="100%%" height="34" style="direction: ltr"><path d="M14 32 H134 L14 %.1f Z" fill="%s" opacity=".16"/><path d="M14 %.1f L134 32" stroke="%s" stroke-width="2.4" stroke-linecap="round"/>'
            '<path d="M40 32 A26 26 0 0 0 38.5 %.1f" fill="none" stroke="%s" stroke-width="1.4"/></svg>' % (32 - h, c, 32 - h, c, 32 - 7, c))


def both_vis(f):
    return '<span class="d">%s</span><span class="n">%s</span>' % (f('day'), f('night'))


def tile(icon_d, head, v, u, meta, vis, new=False, wide=False):
    return ('<div class="rst glass%s"><span class="h">%s%s%s</span><div class="rsv"><b>%s</b><i>%s</i></div>%s<span class="m">%s</span></div>'
            % (' wide' if wide else '', icon(icon_d, 15, 2), head, '<span class="rsnew" style="margin-inline-start: auto">חדש</span>' if new else '', v, u, vis, meta))


def records(dim=False):
    o = bg() + ('<div class="recp glass">%s<span style="font-size: 16px; font-weight: 700">השיאים שלך</span><span style="margin-inline-start: auto">%s</span></div>' % (icon(I_BACK, 20, 2), EG))
    mx = D.ST['max_at']; px, py, _, _ = D.ll(VH, mx['lat'], mx['lon'])

    def pin(V, mode):
        return '<circle cx="%.1f" cy="%.1f" r="6" fill="#fff" stroke="#E0246E" stroke-width="2.6"/>' % (px, py)
    hero = fitwin([FAST], 366, 236, 24, pad=1.35, extra=pin)
    dm = '11.1'
    o += '<div class="myscroll" style="gap: 10px">'
    o += ('<div class="rshero">%s<div class="lab"><div class="rsv"><b>%.1f</b><i>קמ״ש</i></div><div class="rsm"><b>שיא מהירות</b><span><bdi>Sadzele 1</bdi> · %s · הערכה</span></div></div></div>'
          % (hero, BEST['max_kmh'], dm))
    lw = fitwin([LONG], 150, 64, 12, pad=1.1)
    o += '<div class="rsgrid">'
    o += tile(I_RULER, 'הירידה הארוכה', '%.1f' % (BEST['longest_m'] / 1000), 'ק״מ', 'מ-<bdi>Sadzele 3</bdi> עד <bdi>Goodaura 1</bdi> · %s' % dm, lw)
    o += tile(I_DOWN, 'היום הכי גדול', n0(BEST['drop_m']), 'מ׳', 'מטרים למטה ביום · %s' % dm, both_vis(ridge_vis))
    o += tile(I_MTN, 'הכי הרבה ירידות', '%d' % BEST['descents'], '', 'ביום אחד · %s' % dm, both_vis(ticks_vis))
    o += tile(I_SLOPE, 'הכי תלול', '%d°' % BEST['steep_deg'], '', '<bdi>Sadzele 1</bdi> · הקטע התלול', both_vis(slope_vis))
    o += '</div>'
    o += '<div class="rsfoot">%s<span>רק מול עצמך. נשאר בטלפון.</span></div></div>' % icon(I_LOCK, 15, 2)
    return o


def records_none():
    o = bg() + ('<div class="recp glass">%s<span style="font-size: 16px; font-weight: 700">השיאים שלך</span></div>' % icon(I_BACK, 20, 2))
    o += '<div class="myscroll"><div class="myempty"><b>עוד אין שיאים</b></div><div class="rsgrid rsghost">'
    for h, ic_ in (('שיא מהירות', I_BOLT), ('הירידה הכי ארוכה', I_RULER), ('היום הכי גדול', I_DOWN), ('הכי הרבה ירידות', I_MTN)):
        o += '<div class="rst glass"><span class="h">%s%s</span><div class="rsv"><b>—</b></div></div>' % (icon(ic_, 15, 2), h)
    o += ('</div><div style="flex: 1"></div><button class="mypri" style="margin-bottom: 24px">%s<span>להקליט יום גלישה</span></button></div>' % icon(I_REC, 20, 2))
    return o


# ---------------------------------------------------------------- NS: the end of a recording that is not saved
def not_saved(night=False, rec='0:42', found=True, where='out', when='היום · 16:02'):
    s = EMB.end_empty(night, rec, found, where, when)
    a = '<b>הקלטה בלי ירידות</b><span>%s</span>' % when
    b = '<div class="emb"><button class="back">חזרה להר</button><button class="del">מחיקה</button></div>'
    assert a in s and b in s, 'EM markup changed'
    s = s.replace(a, '<b>לא נרשמה גלישה בהר</b><span style="display: flex; align-items: center; gap: 6px">%s<span>ההקלטה לא נשמרה</span></span>' % icon(I_NOSAVE, 16, 1.9))
    s = s.replace(b, '<div class="emb"><button class="back">חזרה להר</button></div>')
    return s


def phones():
    return {'NS1.dc.html': ('לא נרשמה גלישה · מחוץ לגודאורי, ביום', not_saved(), 'day'),
            'NS2.dc.html': ('לא נרשמה גלישה · בגודאורי, עלה ברכבל וירד בו', not_saved(rec='38:10', where='in', when='היום · 10:12'), 'day'),
            'NS3.dc.html': ('לא נרשמה גלישה · בלילה, בלי מיקום', not_saved(True, rec='0:18', found=False, where='none', when='היום · 21:14'), 'night'),
            'MY1.dc.html': ('ימי הגלישה שלי · הטיול', my_list(), None),
            'MY2.dc.html': ('ימי הגלישה שלי · סוף הרשימה והיום לדוגמה', my_list_end(), None),
            'MY3.dc.html': ('ימי הגלישה שלי · לפני היום הראשון', my_first(), None),
            'RS1.dc.html': ('השיאים שלך', records(), None),
            'RS2.dc.html': ('השיאים שלך · בלילה', records(), 'night'),
            'RS3.dc.html': ('השיאים שלך · עוד אין', records_none(), None)}


HB = 1040


def my00():
    row = lambda *c: '<tr>%s</tr>' % ''.join('<td style="padding: 9px 10px; vertical-align: top; border-top: 1px solid #D9DEE4">%s</td>' % x for x in c)
    th = '<tr>%s</tr>' % ''.join('<th style="text-align: start; padding: 6px 10px; font-size: 13px; color: #3C4753">%s</th>' % x for x in ('', 'מה ראית', 'ההצעה', 'לוחות'))
    tbl = ('<table style="border-collapse: collapse; width: 100%; font-size: 15px; line-height: 1.45">' + th
           + row('<b>P.23</b>', '35 ימים ברשימה, רובם לחיצה על הקלטה ועצירה. יום של "0 מ׳ למטה" ל-0.5 ק״מ.',
                 '<b>הקלטה בלי ירידה אמיתית לא נשמרת, אף פעם.</b> ירידה אמיתית: ירידה של המנתח עם לפחות 20 מ׳ ירידה בגובה, בתוך גודאורי (עד 2.5 ק״מ ממסלול או רכבל). ירידה "לפי מרחק" בלי גובה (הליכה) לא נחשבת. גם חלק מירידה, ביום שנקטע, נשמר. '
                 'בסוף: "לא נרשמה גלישה בהר", "ההקלטה לא נשמרה", שלוש העובדות של EM, היום לדוגמה, וכפתור אחד "חזרה להר". "מחיקה" יוצא. בלי שאלה "לשמור?" (פיני דחה).', 'NS1 עד NS3 (מחליפים את EM1 עד EM4)')
           + row('<b>שם</b>', '"הימים שלי" לא אומר מה יש שם.', '<b>"ימי הגלישה שלי"</b> בכותרת הדף, <b>"ימי גלישה"</b> באריח התפריט (קצר). התוכן מחליט.', 'MY1 עד MY3')
           + row('<b>P.24</b>', 'רשימה לבנה: תאריך ושורה, תמונה קטנה רק בחלק.',
                 '<b>ההר מאחור, והטיול בראש:</b> כרטיס "גודאורי · ינואר 2027" עם ארבעה מספרים ועמודה לכל יום (גובה = מטרים למטה). מתחתיו "השיאים שלך" עם שני שיאים ונגיעה לדף. '
                 '<b>כל יום כרטיס:</b> חלון על ההר המצויר עם הקווים של אותו יום (המסלולים בלבן מאחור, כמו DM), יום ותאריך, שעות, שלושה מספרים, פס המהירות של הירידות, ותגית כשיש שיא או כשהיום נקטע. היום לדוגמה בסוף, בנפרד ובעמעום.', 'MY1, MY2')
           + row('<b>שיאים</b>', 'שש שורות טקסט מתחת לחלון.', '<b>השיא הראשי בגדול:</b> חלון ההר עם הירידה והנקודה "פה טסת", והמספר על התמונה. מתחת ארבעה אריחים, לכל אחד ציור משלו: הירידה הארוכה על ההר, הרכס של היום הגדול, עמודה לכל ירידה, ושיפוע בזווית האמיתית. "חדש" על מה שנקבע ביום האחרון. נגיעה פותחת את היום בנקודה.', 'RS1 עד RS3')
           + '</table>')
    asks = ''.join('<p class="q"><span class="qn">%d</span><span>%s</span></p>' % (i + 1, t) for i, t in enumerate([
        '<b>הקלטה בלי ירידה אמיתית לא נשמרת</b>, לפי הכלל בטבלה (20 מ׳ בגובה, בגודאורי)? (כן)',
        '<b>מסך הסוף:</b> "לא נרשמה גלישה בהר" ו"ההקלטה לא נשמרה", כפתור אחד, בלי "מחיקה"? (כן)',
        '<b>הימים המזויפים בטלפונים של הבדיקה:</b> ניקוי חד-פעמי בעדכון, של ימים שלא עומדים בכלל? היום לדוגמה נשאר. (כן)',
        '<b>השם:</b> "ימי הגלישה שלי", ובתפריט "ימי גלישה"? (כן; התוכן יכול להציע אחר)',
        '<b>הרשימה:</b> כרטיס הטיול בראש וכרטיס עם חלון על ההר לכל יום? (כן)',
        '<b>השיאים:</b> השיא הראשי על ההר ואריחים עם ציור? (כן)']))
    strings = li('<b>קיימים:</b> "חזרה להר", "דוגמה", "הוקלט", "מיקום", "נמצא", "לא נמצא", "איפה", "בגודאורי", "מחוץ לגודאורי", "השיאים שלך", "עוד אין שיאים", "שיא מהירות אישי", "הירידה הכי ארוכה שלך", "היום הכי גדול שלך", "הכי הרבה ירידות ביום", "מ׳ למטה", "ק״מ", "קמ״ש".',
                 '<b>חדשים, לסשן התוכן אחרי האישור:</b> "לא נרשמה גלישה בהר", "ההקלטה לא נשמרה", "ימי הגלישה שלי", "ימי גלישה" (אריח), "עוד אין ימי גלישה", "להקליט יום גלישה", "יום לדוגמה, לא נספר בשיאים", "נקטע ב-{time}", "{n} ימים", "ק״מ גלישה", "הכי תלול", "הקטע התלול", "רק מול עצמך. נשאר בטלפון.", וקורא מסך לכרטיס יום ("{day}, {n} ירידות, {m} מטר למטה").',
                 '<b>צורות רבים:</b> "ירידה אחת" / "{n} ירידות", "יום אחד" / "{n} ימים" (בתמונה: "1 ירידות").')
    honest = ('המספרים, השמות והשיאים מיום הדוגמה, מהמנתח וממנוע הרגעים. <b>ארבעת ימי הטיול ב-MY1 ו-MY2 מומחשים:</b> נגזרו מהירידות של יום הדוגמה (חלקים שלו), ומסומנים "דוגמה". '
              'הכלל "20 מ׳ בגובה" הוא הסף של המנתח עצמו (<span dir="ltr">min_descent_drop_m</span>); "2.5 ק״מ" הוא <span dir="ltr">WhereRules.atResort</span>. נבדק בכלי העיצוב, לא בטלפון.')
    inner = ('<p class="sec">ימי הגלישה והשיאים · 27.11.2026 · ממתין לפיני</p>'
             '<h1>רק ימים אמיתיים נשמרים, ומה שנשמר נראה כמו יום על ההר</h1>'
             '<div class="card">%s</div>'
             '<div class="rowx" style="align-items: stretch"><div class="card" style="flex: 1.1"><h2>לפיני</h2>%s</div><div class="card" style="flex: 1"><h2>הטקסטים</h2>%s</div></div>'
             '<div class="card"><h2>בכנות</h2><p class="p">%s</p><p class="p">המסירה: <span dir="ltr">design/handoff/2026-11-27-redesign-my-days/</span></p></div>' % (tbl, asks, strings, honest))
    open(os.path.join(PROJ, 'MY00-MyDays.dc.html'), 'w', encoding='utf8').write(board('ימי הגלישה והשיאים', 1280, HB, wbox(1280, HB, inner)))


FILES = ['MY00-MyDays.dc.html'] + ['NS%d.dc.html' % i for i in (1, 2, 3)] + ['MY%d.dc.html' % i for i in (1, 2, 3)] + ['RS%d.dc.html' % i for i in (1, 2, 3)]
CODE = re.compile(r'^(MY|NS|RS)\d')


def write_canvas(cj, T):
    j = json.load(open(cj, encoding='utf8'))
    assert not [f for f in j['boards'] if f not in FILES and CODE.match(f)]
    for fn in FILES: j['boards'].pop(fn, None)
    j['order'] = [f for f in j['order'] if f not in FILES]
    place = [('MY00-MyDays.dc.html', 0, 0, 1280, HB, 'P.23 ו-P.24: מה ראית, ההצעה והשאלות')]
    y = HB + 160
    for fam in ('NS', 'MY', 'RS'):
        for k, fn in enumerate([f for f in FILES[1:] if f.startswith(fam)]):
            place.append((fn, 470 * k, y, 390, 844, T[fn][0]))
        y += 844 + 200
    for fn, x, yy, w, h, t in place:
        code = fn.split('.dc.html')[0].split('-')[0]
        j['boards'][fn] = dict(x=x, y=yy, w=w, h=h, title='%s (%s)' % (t, code), page=PAGE)
        j['order'].append(fn)
    if not any(pg['id'] == PAGE for pg in j['pages']):
        j['pages'].insert(0, dict(id=PAGE, name=PAGE_NAME))
    j['notes']['t' + PAGE] = dict(kind='title1', maxW=1600, page=PAGE, x=0, y=-300, text='ימי הגלישה שלי והשיאים')
    for k, (fam, nm) in enumerate((('NS', '1 · הקלטה בלי ירידה: לא נשמרת'), ('MY', '2 · ימי הגלישה שלי'), ('RS', '3 · השיאים שלך'))):
        j['notes']['t%s%s' % (PAGE, fam)] = dict(kind='title2', maxW=1600, page=PAGE, x=0, y=HB + 160 + k * 1044 - 80, text=nm)
    j['launch'] = dict(view='canvas', page=PAGE)
    open(cj, 'w', encoding='utf8').write(json.dumps(j, ensure_ascii=False, indent=1) + '\n')


def main_():
    args = sys.argv[1:]; pv = None
    if '--preview' in args:
        i = args.index('--preview'); pv = (args[i + 1], args[i + 2]); del args[i:i + 3]
    cj = args[0] if args else os.path.join(PROJ, 'canvas.json')
    T = phones()
    for fn, (title, body, fx) in T.items():
        PB.phone(fn, title, body, night=fx == 'night', fixed=fx, lang='he')
    my00()
    write_canvas(cj, T)
    if pv:
        import dk_boards as DK
        DK.FILES = FILES; DK.preview(*pv)
    print('ok', cj)


if __name__ == '__main__':
    main_()
