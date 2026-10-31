"""A day from the field: the reference analysis on one raw log, and the comparison with what the app saved
(docs/QA.md, section 2, step 1: "the app against the tool, on the same log").

    python3 tools/dayrec/field.py analyse <log.jsonl> [-o result.json] [--strip]
    python3 tools/dayrec/field.py compare <log.jsonl> <app-result.json> [--live app-live.json] [-o report.md]

analyse prints (or writes) the final result of the reference on the log. The result carries positions (stops,
falls, the place of the maximum speed), so it may be written only outside the repository, unless --strip removes
them (a result without positions may go anywhere).

compare runs the same analysis and compares it with the app's result by the rules of compare.py: the same
sequence of segments and legs, times within 3 s, distances and drops within 3 %, the maximum speed within
0.1 km/h (and, with --live, the live events). It prints a report people can read: every segment on both sides,
with local times, the edges and the differences, and where the sequences part. The report never carries a
position, so it may be kept in the repository. Exit code 0: no difference; 1: differences; 2: bad input.
Every difference here is a bug in a platform, not a matter of calibration (docs/QA.md).
"""
import argparse, difflib, json, os, sys
from datetime import datetime, timedelta, timezone

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, '..', '..'))
sys.path.insert(0, HERE)
import analyze, compare, rawlog  # noqa: E402

POSITION_KEYS = ('lat', 'lon', 'la', 'lo')


def strip(o):
    """The same result without a single position."""
    if isinstance(o, dict):
        return {k: strip(v) for k, v in o.items() if k not in POSITION_KEYS}
    if isinstance(o, list):
        return [strip(v) for v in o]
    return o


def _read(path):
    with open(path, encoding='utf-8') as f:
        return f.read()


def _write(path, text):
    with open(path, 'w', encoding='utf-8') as f:
        f.write(text)


def inside_repo(path):
    p = os.path.realpath(path)
    return p == ROOT or p.startswith(ROOT + os.sep)


def run(text, P=None, base=None):
    """(header, final, live, dropped lines) of the reference on a raw log."""
    import build
    P = P or build.params()
    base = base or build.load_base(P)
    a = analyze.Analyzer(base, P)
    h, pts, evs, bad = rawlog.read(text)
    return h, a.final(h, pts, evs), a.live(h, pts, evs), bad


# ----- the report -----

def _tz(header):
    try:
        from zoneinfo import ZoneInfo
        return ZoneInfo(header.get('tz') or 'Asia/Tbilisi')
    except Exception:
        return timezone(timedelta(hours=4))


def _clock(t, tz):
    return '' if t is None else datetime.fromtimestamp(t / 1000.0, tz).strftime('%H:%M:%S')


def _runs(runs):
    return ' · '.join(runs) if runs else 'unnamed'


def _label(s):
    if s['type'] == 'lift':
        return '%s %s→%s' % (s.get('name') or s.get('lift'), s['from'].get('station') or '?', s['to'].get('station') or '?')
    if s['type'] == 'stop':
        return 'stop (%s)' % s.get('kind')
    if s['type'] == 'descent':
        names = []
        for lg in s['legs']:
            n = _runs(lg['runs']) if lg['kind'] == 'run' else lg['kind']
            if not names or names[-1] != n:
                names.append(n)
        return 'descent: ' + ' > '.join(names)
    return s['type']


def _numbers(s):
    out = []
    if 'dist_m' in s:
        out.append('%d m' % round(s['dist_m']))
    if s.get('drop_m') is not None:
        out.append('↓%d m' % round(s['drop_m']))
    if s.get('rise_m') is not None:
        out.append('↑%d m' % round(s['rise_m']))
    if s.get('max_kmh') is not None:
        out.append('%.1f km/h' % s['max_kmh'])
    return ', '.join(out)


def _cell(s, tz):
    if s is None:
        return '—'
    return '%s, %s–%s%s' % (_label(s), _clock(s['t0'], tz), _clock(s['t1'], tz),
                             (' (%s)' % _numbers(s)) if _numbers(s) else '')


def _dt(a, b):
    if a is None or b is None:
        return ''
    d = (b - a) / 1000.0
    return ('%+.0f s' % d) if d else '0'


def _seg_rows(exp, got, tz):
    """Rows of the segment table, the two sequences aligned by event (difflib on compare.seg_key)."""
    es, gs = exp.get('segments', []), got.get('segments', [])
    ek, gk = [compare.seg_key(s) for s in es], [compare.seg_key(s) for s in gs]
    rows = []
    sm = difflib.SequenceMatcher(a=ek, b=gk, autojunk=False)
    for op, i0, i1, j0, j1 in sm.get_opcodes():
        if op == 'equal':
            for i, j in zip(range(i0, i1), range(j0, j1)):
                e, g = es[i], gs[j]
                errs = []
                compare._t(e['t0'], g['t0'], 't0', errs); compare._t(e['t1'], g['t1'], 't1', errs)
                for k in ('dist_m', 'drop_m'):
                    if k in e or k in g:
                        compare._num(e.get(k), g.get(k), k, errs)
                if e['type'] == 'descent':
                    compare._kmh(e.get('max_kmh'), g.get('max_kmh'), 'max_kmh', errs)
                    if [(l['kind'], tuple(l['runs'])) for l in e['legs']] != [(l['kind'], tuple(l['runs'])) for l in g['legs']]:
                        errs.append('legs')
                rows.append((str(i + 1), _cell(e, tz), _cell(g, tz), _dt(e['t0'], g['t0']), _dt(e['t1'], g['t1']),
                             'ok' if not errs else 'DIFF: ' + ', '.join(x.split(':')[0] for x in errs)))
        else:
            n = max(i1 - i0, j1 - j0)
            for k in range(n):
                e = es[i0 + k] if i0 + k < i1 else None
                g = gs[j0 + k] if j0 + k < j1 else None
                what = {'replace': 'different segment', 'delete': 'only in the tool', 'insert': 'only in the app'}[op]
                rows.append((str(i0 + k + 1) if e else '', _cell(e, tz), _cell(g, tz), '', '', 'DIFF: ' + what))
    return rows


def _table(head, rows):
    out = ['| ' + ' | '.join(head) + ' |', '|' + '---|' * len(head)]
    for r in rows:
        out.append('| ' + ' | '.join(str(c).replace('|', '/') for c in r) + ' |')
    return out


def report(header, exp, got, bad=0, live_exp=None, live_got=None):
    """(markdown text, number of differences). Never a position: only times, names, numbers."""
    tz = _tz(header)
    ferrs = compare.final(exp, got)
    lerrs = compare.live(live_exp, live_got) if live_got is not None else []
    day = datetime.fromtimestamp((exp.get('start') or header.get('start') or 0) / 1000.0, tz).strftime('%d.%m.%Y')
    L = ['# Day from the field: the tool against the app', '',
         '- Day: %s (%s), resort `%s`, mode `%s`, device `%s`' % (day, header.get('tz') or 'Asia/Tbilisi',
                                                                header.get('resort'), exp.get('mode'), header.get('device')),
         '- Log: %d points, %d dropped lines, coverage %s, gaps: %d' % (
             exp.get('points', 0), bad, exp.get('coverage'), len(exp.get('gaps', []))),
         '- Analysis version: tool %s, app %s' % (exp.get('analysis'), got.get('analysis')),
         '- Thresholds (compare.py): times %d s, distances and drops %d %% (at least %d m), maximum speed %.1f km/h' % (
             compare.T_MS // 1000, round(compare.REL * 100), compare.ABS_M, compare.KMH), '']
    n = len(ferrs) + len(lerrs)
    L.append('**Result: %s.**' % ('no difference' if not n else '%d difference%s' % (n, '' if n == 1 else 's')))
    if exp.get('analysis') != got.get('analysis'):
        L.append('')
        L.append('Note: the analysis versions differ; the app should be checked against the tool of its own version.')
    L += ['', '## Segments', '',
          'Δ = the app minus the tool. Local times.', '']
    L += _table(['#', 'tool', 'app', 'Δ start', 'Δ end', ''], _seg_rows(exp, got, tz))
    es_, gs_ = exp.get('stats', {}), got.get('stats', {})
    L += ['', '## Totals', '']
    rows = []
    for k, unit in (('descents', ''), ('lifts', ''), ('ski_dist_m', ' m'), ('drop_m', ' m'), ('max_kmh', ' km/h')):
        a, b = es_.get(k), gs_.get(k)
        errs = []
        if k in ('descents', 'lifts'):
            ok = a == b
        elif k == 'max_kmh':
            compare._kmh(a, b, k, errs); ok = not errs
        else:
            compare._num(a, b, k, errs); ok = not errs
        rows.append((k, '%s%s' % (a, unit), '%s%s' % (b, unit), 'ok' if ok else 'DIFF'))
    a, b = (es_.get('max_at') or {}).get('t'), (gs_.get('max_at') or {}).get('t')
    rows.append(('max speed at', _clock(a, tz), _clock(b, tz), _dt(a, b)))
    L += _table(['', 'tool', 'app', ''], rows)
    ef, gf = exp.get('falls', []), got.get('falls', [])
    if ef or gf:
        L += ['', '## Falls ("here you fell")', '']
        L += _table(['#', 'tool', 'app', 'Δ'], [(i + 1, _clock(ef[i]['t'], tz) if i < len(ef) else '—',
                                                  _clock(gf[i]['t'], tz) if i < len(gf) else '—',
                                                  _dt(ef[i]['t'], gf[i]['t']) if i < len(ef) and i < len(gf) else 'DIFF')
                                                 for i in range(max(len(ef), len(gf)))])
    eg, gg = exp.get('gaps', []), got.get('gaps', [])
    if eg or gg:
        L += ['', '## Gaps in the log', '']
        L += ['- tool: ' + (', '.join('%s–%s' % (_clock(g['t0'], tz), _clock(g['t1'], tz)) for g in eg) or 'none'),
              '- app: ' + (', '.join('%s–%s' % (_clock(g['t0'], tz), _clock(g['t1'], tz)) for g in gg) or 'none')]
    if live_got is not None:
        L += ['', '## Live view', '']
        ev = lambda e: '%s %s' % (e['act'], e.get('lift') or (_runs(e['runs']) if e.get('runs') else ''))
        a, b = live_exp['events'], live_got['events']
        sm = difflib.SequenceMatcher(a=[ev(e) for e in a], b=[ev(e) for e in b], autojunk=False)
        rows = []
        for op, i0, i1, j0, j1 in sm.get_opcodes():
            for k in range(max(i1 - i0, j1 - j0)):
                e = a[i0 + k] if i0 + k < i1 else None
                g = b[j0 + k] if j0 + k < j1 else None
                c = lambda x: '%s %s' % (_clock(x['t'], tz), ev(x).strip()) if x else '—'
                d = _dt(e['t'], g['t']) if e and g else ''
                bad_t = e and g and abs(g['t'] - e['t']) > compare.T_MS
                rows.append((c(e), c(g), d, 'ok' if op == 'equal' and not bad_t else 'DIFF'))
        L += _table(['tool', 'app', 'Δ', ''], rows)
    if n:
        L += ['', '## Differences (compare.py)', '']
        L += ['- ' + x for x in ferrs + lerrs]
    text = '\n'.join(L) + '\n'
    return text, n


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    sub = ap.add_subparsers(dest='cmd', required=True)
    a = sub.add_parser('analyse', help='the reference result of a raw log')
    a.add_argument('log'); a.add_argument('-o', '--out'); a.add_argument('--strip', action='store_true')
    c = sub.add_parser('compare', help='the reference against the result an app saved')
    c.add_argument('log'); c.add_argument('app'); c.add_argument('--live'); c.add_argument('-o', '--out')
    args = ap.parse_args(argv)
    try:
        text = _read(args.log)
    except OSError as e:
        print('cannot read the log: %s' % e, file=sys.stderr); return 2
    header, exp, live_exp, bad = run(text)
    if args.cmd == 'analyse':
        out = strip(exp) if args.strip else exp
        js = json.dumps(out, ensure_ascii=False, indent=1) + '\n'
        if args.out:
            if not args.strip and inside_repo(args.out):
                print('the result has positions: write it outside the repository, or add --strip', file=sys.stderr)
                return 2
            _write(args.out, js)
        else:
            sys.stdout.write(js)
        return 0
    try:
        got = json.loads(_read(args.app))
        live_got = json.loads(_read(args.live)) if args.live else None
    except (OSError, ValueError) as e:
        print('cannot read the app result: %s' % e, file=sys.stderr); return 2
    rep, n = report(header, exp, got, bad, live_exp, live_got)
    if args.out:
        _write(args.out, rep)
    else:
        sys.stdout.write(rep)
    return 1 if n else 0


if __name__ == '__main__':
    sys.exit(main())
