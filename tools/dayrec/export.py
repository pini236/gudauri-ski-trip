"""The raw log a field tester sends (docs/handoff/2026-10-31-tester-log-consent/README.md; the architect's
conditions of 31.10.2026 in docs/ARCHITECTURE.md): the part of the day inside the site's rectangle, and nothing else.

    crop(text, rect, header_extra={}, allow_impacts=False) -> text

- The rectangle is the extent of the terrain model (site/data/terrain.json, dem x0..x1, y0..y1, local metres of the
  site's projection) plus params.export.margin_m (500 m) on every side. A point is kept only if it is inside. The
  home, the hotel and the drive to the village are outside, and so is every barometer and motion reading of those
  seconds (they are fields of the point line).
- Every stretch of points that is cut becomes one line `{"ev":"gap","t0":..,"t1":..}` (the times of its first and
  last cut point, nothing else), and the header says `"cropped":true`. The analysis does not depend on the gap
  line: holes are found in the time between points, so a ride that crosses the border is cut, not guessed.
- Motion windows ("here you fell"): the window of a blow the skier dismissed is never exported (it is deleted when
  the day closes; this drops it again). A window that was not dismissed is exported only when the tester agreed to
  that in a separate line of the consent (allow_impacts), and only when the blow is inside the rectangle.
- The header carries no device id, account, name or advertising id: what the caller passes in header_extra
  (app version, platform, a coarse device type, whether there was a barometer), plus the fields of the log
  that are not about the person (v, tz, resort, start, mode).
- Unknown event lines are dropped, as are unknown fields: the export never carries what it does not know.

The Android app does the same (dayrec/RawExport.kt) and is checked against tools/fixtures/dayrec-export.json.
"""
import json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import net as netmod, rawlog  # noqa: E402

HEADER_KEEP = ('v', 'tz', 'resort', 'start', 'mode')
KEPT_EVENTS = ('start', 'resume', 'stop', 'full')


def rect_of(terrain, margin_m):
    d = terrain['dem']
    return (float(d['x0']) - margin_m, float(d['y0']) - margin_m, float(d['x1']) + margin_m, float(d['y1']) + margin_m)


def crop(text, rect, proj, header_extra=None, allow_impacts=False):
    header, pts, events, _bad = rawlog.read(text)
    x0, y0, x1, y1 = rect

    def inside(q):
        x, y = proj.xy(q['la'], q['lo'])
        return x0 <= x <= x1 and y0 <= y <= y1

    rows = []  # (t, order, object)
    run = None
    keep = {}
    for q in pts:
        if inside(q):
            if run:
                rows.append((run[0], 1, {'ev': 'gap', 't0': run[0], 't1': run[1]}))
                run = None
            keep[q['t']] = True
            rows.append((q['t'], 2, {k: q.get(k) for k in rawlog.FIELDS}))
        else:
            run = (q['t'], q['t']) if run is None else (run[0], q['t'])
    if run:
        rows.append((run[0], 1, {'ev': 'gap', 't0': run[0], 't1': run[1]}))
    dismissed = {e.get('t') for e in events if e.get('ev') == 'impact' and e.get('dismissed')}
    for e in events:
        ev = e.get('ev')
        if ev in KEPT_EVENTS:
            o = {k: e[k] for k in ('ev', 't', 'reason') if k in e}
            rows.append((e.get('t', 0), 0, o))
        elif ev == 'impact':
            if e.get('dismissed'):
                rows.append((e['t'], 3, {'ev': 'impact', 't': e['t'], 'dismissed': True}))
            elif allow_impacts and e.get('t') not in dismissed and 'acc' in e:
                near = _nearest(pts, e['t'])
                if near is not None and inside(near):
                    rows.append((e['t'], 3, {k: e[k] for k in ('ev', 't', 'hz', 't0', 'acc', 'gyr') if k in e}))
    rows.sort(key=lambda r: (r[0], r[1]))
    head = {k: header[k] for k in HEADER_KEEP if k in header}
    head.update(header_extra or {})
    head['cropped'] = True
    out = [rawlog.line(head)] + [rawlog.line(r[2]) for r in rows]
    return '\n'.join(out) + '\n'


def _nearest(pts, t):
    best = None
    for q in pts:
        if best is None or abs(q['t'] - t) < abs(best['t'] - t):
            best = q
    return best if best is not None and abs(best['t'] - t) <= 5000 else None


def _pt(t, la, lo, **kw):
    q = {'t': t, 'la': round(la, 7), 'lo': round(lo, 7), 'a': 2500.0, 'p': 740.0, 's': 8.0, 'sa': 0.5, 'ha': 5.0, 'va': 8.0, 'b': 90.0, 'm': 0.5}
    q.update(kw)
    return q


def fixture(root):
    """tools/fixtures/dayrec-export.json: a small log with a drive in from outside, an excursion out and back, a blow
    that stayed, a blow that was dismissed, a blow outside, and an unknown event; the cropped log with and without the
    tester's agreement to motion windows. The Android app (RawExportTest) must give the same lines."""
    P = json.load(open(os.path.join(root, 'tools/fixtures/dayrec-params.json'), encoding='utf-8'))
    terrain = json.load(open(os.path.join(root, 'site/data/terrain.json'), encoding='utf-8'))
    proj = netmod.Proj(terrain['proj']['lat0'], terrain['proj']['lon0'])
    rect = rect_of(terrain, P['export']['margin_m'])
    in_la, in_lo = proj.ll(0.0, 0.0)
    out_la, out_lo = in_la, in_lo + 0.4  # about 33 km east: far outside
    edge_la, edge_lo = proj.ll(rect[2] - 50.0, 0.0)        # 50 m inside the margin
    over_la, over_lo = proj.ll(rect[2] + 50.0, 0.0)        # 50 m past the margin: outside
    t0 = 1768000000000
    lines = [{'v': 1, 'tz': 'Asia/Tbilisi', 'resort': 'gudauri', 'device': 'Pixel 7 (secret)', 'start': t0, 'mode': 'day',
              'account': 'someone'}, {'ev': 'start', 't': t0}]
    pts = []
    pts += [_pt(t0 + i * 1000, out_la, out_lo + i * 1e-4) for i in range(3)]                       # the drive: out
    pts += [_pt(t0 + (3 + i) * 1000, in_la, in_lo + i * 1e-4) for i in range(4)]                     # in
    pts += [_pt(t0 + (7 + i) * 1000, over_la, over_lo, p=None) for i in range(2)]                    # past the margin: out
    pts += [_pt(t0 + 9 * 1000, edge_la, edge_lo)]                                                    # just inside
    pts += [_pt(t0 + (10 + i) * 1000, in_la, in_lo - i * 1e-4) for i in range(3)]
    pts += [_pt(t0 + (13 + i) * 1000, out_la, out_lo) for i in range(2)]                             # out again at the end
    lines += pts
    kept = {'ev': 'impact', 't': t0 + 4000, 'hz': 50, 't0': t0 - 1000, 'acc': [0.0, 9.8, 0.5, 1.0, 20.5, 2.0], 'gyr': [0.1, 0.2, 0.3]}
    lines.append(kept)
    lines.append({'ev': 'impact', 't': t0 + 11000, 'hz': 50, 't0': t0 + 10000, 'acc': [0.0, 9.8, 0.5], 'gyr': [0.1, 0.2, 0.3]})
    lines.append({'ev': 'impact', 't': t0 + 11000, 'dismissed': True})                              # it was dismissed
    lines.append({'ev': 'impact', 't': t0 + 1000, 'hz': 50, 't0': t0, 'acc': [0.0, 9.8, 0.5], 'gyr': [0.1, 0.2, 0.3]})  # outside
    lines.append({'ev': 'someday', 't': t0 + 5000, 'x': 1})                                            # an event this export does not know
    lines.append({'ev': 'stop', 't': t0 + 14000, 'reason': 'user'})
    text = '\n'.join(rawlog.line(o) for o in lines) + '\n'
    extra = {'app': '0.1.0-test', 'platform': 'android', 'baro': True}
    cases = []
    for name, allow in (('without_motion_windows', False), ('with_motion_windows', True)):
        out = crop(text, rect, proj, extra, allow)
        cases.append({'name': name, 'allow_impacts': allow, 'expected': out.strip().split('\n')})
    doc = {'about': 'Test values of the field tester export (tools/dayrec/export.py; docs/handoff/2026-10-31-tester-log-consent). '
                    'rect is the extent of site/data/terrain.json dem plus params.export.margin_m, in local metres of its proj; '
                    'an app passes when it writes exactly the expected lines for the input lines.',
           'proj': terrain['proj'], 'margin_m': P['export']['margin_m'], 'rect': list(rect), 'header_extra': extra,
           'input': text.strip().split('\n'), 'cases': cases}
    return json.dumps(doc, ensure_ascii=False, indent=1) + '\n'
