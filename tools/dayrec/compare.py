"""How two results of the day analysis are compared (between the reference and an app, docs/ARCHITECTURE.md m-8, answer 2).

By event, not by floating number: the same sequence of segments (type, lift, stations) and of legs
(kind, runs), times within 3 s, distances and drops within 3 % (at least 1 m), the maximum speed
within 0.1 km/h. The live view: the same sequence of changes (activity, lift, runs), times within 3 s.
"""

T_MS = 3000
REL = 0.03
ABS_M = 1.0
KMH = 0.1


def _num(a, b, path, errs, rel=REL, ab=ABS_M):
    if a is None or b is None:
        if a is not b:
            errs.append('%s: %r != %r' % (path, a, b))
        return
    if abs(a - b) > max(rel * max(abs(a), abs(b)), ab):
        errs.append('%s: %s != %s' % (path, a, b))


def _t(a, b, path, errs):
    if a is None or b is None or abs(a - b) > T_MS:
        errs.append('%s: time %s != %s' % (path, a, b))


def _kmh(a, b, path, errs):
    if a is None or b is None:
        if a is not b:
            errs.append('%s: %r != %r' % (path, a, b))
    elif abs(a - b) > KMH + 1e-9:
        errs.append('%s: %s != %s km/h' % (path, a, b))


def seg_key(s):
    k = [s['type']]
    if s['type'] == 'lift':
        k += [s['lift'], s['from'].get('station'), s['to'].get('station')]
    if s['type'] == 'stop':
        k += [s['kind']]
    return tuple(k)


def final(exp, got):
    errs = []
    es, gs = exp.get('segments', []), got.get('segments', [])
    ek, gk = [seg_key(s) for s in es], [seg_key(s) for s in gs]
    if ek != gk:
        return ['segments differ: expected %s, got %s' % (ek, gk)]
    for n, (e, g) in enumerate(zip(es, gs)):
        p = 'segments[%d] %s' % (n, e['type'])
        _t(e['t0'], g['t0'], p + '.t0', errs); _t(e['t1'], g['t1'], p + '.t1', errs)
        if e['type'] == 'descent':
            el = [(l['kind'], tuple(l['runs'])) for l in e['legs']]
            gl = [(l['kind'], tuple(l['runs'])) for l in g['legs']]
            if el != gl:
                errs.append('%s.legs: expected %s, got %s' % (p, el, gl)); continue
            for m, (a, b) in enumerate(zip(e['legs'], g['legs'])):
                _t(a['t0'], b['t0'], '%s.legs[%d].t0' % (p, m), errs); _t(a['t1'], b['t1'], '%s.legs[%d].t1' % (p, m), errs)
                _num(a['dist_m'], b['dist_m'], '%s.legs[%d].dist_m' % (p, m), errs)
            for k in ('dist_m', 'drop_m'):
                _num(e[k], g[k], p + '.' + k, errs)
            _kmh(e.get('max_kmh'), g.get('max_kmh'), p + '.max_kmh', errs)
        if e['type'] in ('walk',):
            _num(e['dist_m'], g['dist_m'], p + '.dist_m', errs)
    es_, gs_ = exp.get('stats', {}), got.get('stats', {})
    for k in ('descents', 'lifts'):
        if es_.get(k) != gs_.get(k):
            errs.append('stats.%s: %s != %s' % (k, es_.get(k), gs_.get(k)))
    for k in ('ski_dist_m', 'drop_m'):
        _num(es_.get(k), gs_.get(k), 'stats.' + k, errs)
    _kmh(es_.get('max_kmh'), gs_.get('max_kmh'), 'stats.max_kmh', errs)
    if len(exp.get('gaps', [])) != len(got.get('gaps', [])):
        errs.append('gaps: %d != %d' % (len(exp.get('gaps', [])), len(got.get('gaps', []))))
    return errs


def live(exp, got):
    key = lambda e: (e['act'], e.get('lift'), tuple(e['runs']) if e.get('runs') else e.get('runs'))
    ek, gk = [key(e) for e in exp['events']], [key(e) for e in got['events']]
    if ek != gk:
        return ['live events differ: expected %s, got %s' % (ek, gk)]
    errs = []
    for n, (a, b) in enumerate(zip(exp['events'], got['events'])):
        _t(a['t'], b['t'], 'live[%d]' % n, errs)
    _kmh(exp.get('max_kmh'), got.get('max_kmh'), 'live.max_kmh', errs)
    return errs
