"""The moments engine, the reference (docs/AI-PRODUCT.md opportunity 1, research/ai.md a1; docs/ARCHITECTURE.md design reviews).

From the final analysis of a day (analyze.Analyzer.final) and the skier's own history, it picks the "moments" of the day:
the fastest moment, the longest descent, the first red, new runs, personal records, the longest queue, the drop against
known heights, falls and off-piste. It writes no text: every moment is a key and parameters, and the text comes from
i18n/strings.json (moment.<key>). On the phone, without a network and without a language model. The same function makes
the three surfaces: the story of the day, the card on the lift (lift_card) and the share picture (share).

    import moments
    out = moments.moments(result, history, net.runs, params['moments'])
    out['items'], out['story'], out['share'], out['history']

The history is kept on the phone, never with a position, and grows by one day at a time (update); it is not
recomputed when a day is deleted (architect, 30.10.2026).
"""
import zlib

HISTORY_V = 1


def empty_history():
    return {'v': HISTORY_V, 'days': 0, 'runs': {}, 'colors': {}, 'best': {}}


def seed(key, t):
    """The same text variant on both apps: the app shows variant seed % (number of variants of the key)."""
    return zlib.crc32(('%s|%d' % (key, t)).encode()) & 0x7fffffff


def _descents(res):
    return [s for s in res.get('segments', []) if s['type'] == 'descent']


def _run_legs(res):
    return [lg for s in _descents(res) for lg in s['legs'] if lg['kind'] == 'run']


def _leg_at(res, t):
    """What the skier was on at time t: a leg's {kind, runs?}, or None."""
    for s in _descents(res):
        for lg in s['legs']:
            if lg['t0'] <= t <= lg['t1']:
                return lg
    return None


def _where(lg):
    if lg is None:
        return {'kind': 'unmatched'}
    out = {'kind': lg['kind']}
    if lg['kind'] == 'run':
        out['runs'] = list(lg['runs'])
    return out


def _main_runs(s, n=3):
    """The runs of a descent, by the distance on each (names only from legs on one run), longest first."""
    d = {}
    for lg in s['legs']:
        if lg['kind'] == 'run' and len(lg['runs']) == 1:
            d[lg['runs'][0]] = d.get(lg['runs'][0], 0.0) + lg['dist_m']
    return [k for k, _ in sorted(d.items(), key=lambda kv: (-kv[1], kv[0]))][:n]


def _done(res, runs, M):
    """[(key, t of the first leg)] of the named runs skied today: a leg on that one run of at least color_min_m."""
    out = {}
    for lg in _run_legs(res):
        if len(lg['runs']) == 1 and lg['dist_m'] >= M['color_min_m']:
            k = lg['runs'][0]
            if k in runs and runs[k]['named'] and k not in out:
                out[k] = lg['t0']
    return sorted(out.items(), key=lambda kv: (kv[1], kv[0]))


def _day_bests(res):
    ds = _descents(res)
    st = res.get('stats', {})
    return {
        'max_kmh': st.get('max_kmh'),
        'drop_m': st.get('drop_m') if ds else None,
        'descents': len(ds) if ds else None,
        'longest_m': max((s['dist_m'] for s in ds), default=None),
        'ski_dist_m': st.get('ski_dist_m') if ds else None,
    }


# a single-run recording (mode run) counts for these records only
RUN_RECORDS = ('max_kmh', 'longest_m')


def update(history, res, runs, M):
    """The history after this day: first time of each run and colour, and the personal bests. Never a position."""
    h = {'v': HISTORY_V, 'days': history.get('days', 0), 'runs': dict(history.get('runs', {})),
         'colors': dict(history.get('colors', {})), 'best': dict(history.get('best', {}))}
    day = res.get('mode', 'day') == 'day'
    if day and _descents(res):
        h['days'] += 1
    for k, t in _done(res, runs, M):
        h['runs'].setdefault(k, t)
        c = runs[k]['color']
        if c:
            h['colors'][c] = min(t, h['colors'].get(c, t))
    for k, v in _day_bests(res).items():
        if v is None or (not day and k not in RUN_RECORDS):
            continue
        if k not in h['best'] or v > h['best'][k]:
            h['best'][k] = v
    return h


def moments(res, history, runs, M):
    """{moments, items, story, share, history}. items: [{key, t, seed, p}], each p numbers and names only; the places
    (fastest, fall) carry 'at' {lat, lon}, shown on the phone's own map and never in the share picture."""
    history = history or empty_history()
    items = []
    end = res.get('end') or 0
    ds = _descents(res)
    day = res.get('mode', 'day') == 'day'
    known = history.get('days', 0) > 0 or bool(history.get('best'))

    def add(key, t, p):
        items.append({'key': key, 't': t, 'seed': seed(key, t), 'p': p})

    st = res.get('stats', {})
    # the fastest moment of the day ("here you flew")
    if st.get('max_kmh') is not None and st.get('max_at'):
        at = st['max_at']
        add('fastest', at['t'], dict({'kmh': st['max_kmh'], 'at': {'lat': at['lat'], 'lon': at['lon']}},
                                     **_where(_leg_at(res, at['t']))))
    # the longest descent
    if ds:
        s = max(ds, key=lambda s: (s['dist_m'], -s['t0']))
        add('longest_descent', s['t0'], {'t1': s['t1'], 'dist_m': s['dist_m'], 'drop_m': s['drop_m'],
                                         'moving_s': s['moving_s'], 'runs': _main_runs(s)})
    # the first of each colour today, and whether it is the first ever
    done = _done(res, runs, M)
    for c in M['colors_first']:
        first = next(((k, t) for k, t in done if runs[k]['color'] == c), None)
        if first:
            add('first_color', first[1], {'color': c, 'run': first[0], 'first_ever': c not in history.get('colors', {})})
    # runs skied for the first time (only against a history: on the first day everything is new)
    if known:
        new = [(k, t) for k, t in done if k not in history.get('runs', {})]
        if new:
            add('new_runs', new[0][1], {'runs': [k for k, _ in new]})
    # personal records against the history, and "all the blues"
    if known:
        best = history.get('best', {})
        bests = _day_bests(res)
        t_of = {'max_kmh': (st.get('max_at') or {}).get('t', end),
                'longest_m': max(ds, key=lambda s: (s['dist_m'], -s['t0']))['t0'] if ds else end}
        for k in M['records']:
            v = bests.get(k)
            if v is None or (not day and k not in RUN_RECORDS) or k not in best or v <= best[k]:
                continue
            add('record', t_of.get(k, end), {'what': k, 'value': v, 'before': best[k]})
        allr = {}
        for k, r in runs.items():
            if r['named'] and r['kind'] == 'run' and r['color']:
                allr.setdefault(r['color'], set()).add(k)
        had = set(history.get('runs', {}))
        now = had | {k for k, _ in done}
        for c in M['colors_all']:
            want = allr.get(c, set())
            if want and want <= now and not want <= had:
                t = max(t for k, t in done if k in want and k not in had)
                add('all_color', t, {'color': c, 'n': len(want)})
    # the queues
    segs = res.get('segments', [])
    qs = []
    for i, s in enumerate(segs):
        if s['type'] == 'stop' and s.get('kind') == 'queue':
            nxt = next((x for x in segs[i + 1:] if x['type'] == 'lift'), None)
            qs.append((round((s['t1'] - s['t0']) / 1000.0), s['t0'], nxt['name'] if nxt else None))
    if qs:
        q = max(qs, key=lambda q: (q[0], -q[1]))
        if q[0] >= M['queue_min_s']:
            add('queue', q[1], {'s': q[0], 'lift': q[2], 'total_s': sum(x[0] for x in qs), 'count': len(qs)})
    # the drop against known heights: the first in the list that gives a ratio in range
    drop = st.get('drop_m') or 0
    if day and drop >= M['drop_min_m']:
        for ref in M['drop_refs']:
            times = drop / ref['m']
            if M['drop_times'][0] <= times <= M['drop_times'][1]:
                add('drop_compare', end, {'ref': ref['id'], 'times': round(times, 1), 'drop_m': drop})
                break
    # "here you fell" (a question, never a safety claim; "I did not fall" already removed it from the analysis)
    for f in res.get('falls', []):
        add('fall', f['t'], dict({'at': {'lat': f['lat'], 'lon': f['lon']}}, **_where(_leg_at(res, f['t']))))
    # off-piste descents
    for s in ds:
        for o in s.get('offpiste', []):
            add('offpiste', o['t0'], {'t1': o['t1'], 'drop_m': o['drop_m'], 'dist_m': o['dist_m']})
    items.sort(key=lambda m: (m['t'], M['priority'][m['key']] * -1, m['key']))
    def order(i):
        m = items[i]
        sub = M['records'].index(m['p']['what']) if m['key'] == 'record' else 0
        return (-M['priority'][m['key']], sub, m['t'], m['key'])
    rank = sorted(range(len(items)), key=order)
    # the story: the best moments, at most story_per_key of a kind (one record, not five), in the order of the day
    story, per = [], {}
    for i in rank:
        k = items[i]['key']
        if len(story) < M['story_max'] and per.get(k, 0) < M['story_per_key']:
            story.append(i); per[k] = per.get(k, 0) + 1
    story.sort()
    share = next((i for i in rank if items[i]['key'] not in M['share_exclude']), None)
    return {'moments': M['version'], 'items': items, 'story': story, 'share': share,
            'history': update(history, res, runs, M)}


def lift_card(res, M):
    """The card on the lift: the last descent before the lift the skier is on now, from the final analysis of the log
    so far (run off the main thread, only once the lift is certain). None if the last segment is not a lift or there
    was no descent before it."""
    segs = res.get('segments', [])
    if not segs or segs[-1]['type'] != 'lift':
        return None
    s = next((x for x in reversed(segs[:-1]) if x['type'] == 'descent'), None)
    if s is None:
        return None
    p = {'t1': s['t1'], 'runs': _main_runs(s), 'dist_m': s['dist_m'], 'drop_m': s['drop_m'], 'moving_s': s['moving_s'],
         'max_kmh': s.get('max_kmh'), 'falls': sum(1 for f in res.get('falls', []) if s['t0'] <= f['t'] <= s['t1'])}
    return {'key': 'last_descent', 't': s['t0'], 'seed': seed('last_descent', s['t0']), 'p': p}
