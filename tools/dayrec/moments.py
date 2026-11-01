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
import math
import zlib

HISTORY_V = 2
COLOR_ORDER = ('green', 'blue', 'red', 'black')
DAY_SUM = ('drop_m', 'descents', 'ski_dist_m')  # what the history keeps of a day: numbers only, never a place


def empty_history():
    return {'v': HISTORY_V, 'days': 0, 'runs': {}, 'colors': {}, 'best': {}, 'first': None, 'prev': None}


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


def _deg(g):
    return int(round(math.degrees(math.atan(g))))


def _steepest(res, runs, M):
    """The steepest run skied today (a leg of at least color_min_m on it), by its steepest ~100 m (the number of the
    run panel). {run, deg, t} or None."""
    best = None
    for k, t in _done(res, runs, M):
        d = _deg(runs[k].get('steep_g') or 0.0)
        if best is None or d > best['deg']:
            best = {'run': k, 'deg': d, 't': t}
    return best


def _desc_colors(s, runs, M):
    """The colours of a descent: of each run it went on for at least color_min_m (legs on that one run)."""
    d = {}
    for lg in s['legs']:
        if lg['kind'] == 'run' and len(lg['runs']) == 1:
            d[lg['runs'][0]] = d.get(lg['runs'][0], 0.0) + lg['dist_m']
    return {runs[k]['color'] for k, m in d.items() if k in runs and m >= M['color_min_m'] and runs[k]['color']}


def _queues(res):
    """[(seconds, t0, lift name)] of the queues: a queue stop and the lift after it."""
    segs = res.get('segments', [])
    qs = []
    for i, s in enumerate(segs):
        if s['type'] == 'stop' and s.get('kind') == 'queue':
            nxt = next((x for x in segs[i + 1:] if x['type'] == 'lift'), None)
            qs.append((round((s['t1'] - s['t0']) / 1000.0), s['t0'], nxt['name'] if nxt else None))
    return qs


def _queue_pattern(res, M):
    """A lift whose queues were shorter on one side of an hour than on the other: {lift, split, early, late} (minutes,
    split the local hour), the largest difference, or None."""
    I = M['insights']
    by = {}
    for sec, t0, lift in _queues(res):
        if lift:
            by.setdefault(lift, []).append((t0, sec))
    best = None
    for lift in sorted(by):
        q = sorted(by[lift])
        hours = sorted({int(((t // 1000) / 3600.0 + M['utc_offset_h']) % 24) + 1 for t, _ in q})
        for hsplit in hours:
            cut = lambda t: ((t // 1000) / 3600.0 + M['utc_offset_h']) % 24 < hsplit
            a = [sec for t, sec in q if cut(t)]
            b = [sec for t, sec in q if not cut(t)]
            if len(a) < I['queue_pattern_each'] or len(b) < I['queue_pattern_each']:
                continue
            e, l = round(sum(a) / len(a) / 60.0), round(sum(b) / len(b) / 60.0)
            if abs(e - l) >= I['queue_pattern_min_min'] and (best is None or abs(e - l) > abs(best['early'] - best['late'])):
                best = {'lift': lift, 'split': hsplit, 'early': e, 'late': l}
    return best


# a single-run recording (mode run) counts for these records only
RUN_RECORDS = ('max_kmh', 'longest_m')


def _trip_add(tr, history, res, runs, M):
    """The trip so far after this day (a copy): ski days, drop, the days on each run, and the colours skied for the
    first time ever during the trip ({run, day_n}). Numbers and run keys only."""
    tr = {'start': tr['start'], 'days': tr['days'] + 1, 'drop_m': round(tr['drop_m'] + (res.get('stats', {}).get('drop_m') or 0), 1),
          'runs': dict(tr['runs']), 'first': dict(tr['first'])}
    for k, _ in _done(res, runs, M):
        tr['runs'][k] = tr['runs'].get(k, 0) + 1
        c = runs[k]['color']
        if c and c not in history.get('colors', {}) and c not in tr['first']:
            tr['first'][c] = {'run': k, 'day_n': tr['days']}
    return tr


def update(history, res, runs, M, trip=None):
    """The history after this day: first time of each run and colour, and the personal bests. Never a position.
    trip: {start, last} of "your trip" (the app knows it), or None; the trip totals restart when start changes."""
    h = {'v': HISTORY_V, 'days': history.get('days', 0), 'runs': dict(history.get('runs', {})),
         'colors': dict(history.get('colors', {})), 'best': dict(history.get('best', {})),
         'first': history.get('first'), 'prev': history.get('prev'),  # a v1 history has neither: none yet
         'trip': history.get('trip')}
    day = res.get('mode', 'day') == 'day'
    if day and _descents(res) and trip:
        tr = history.get('trip')
        if not tr or tr['start'] != trip['start']:
            tr = {'start': trip['start'], 'days': 0, 'drop_m': 0.0, 'runs': {}, 'first': {}}
        h['trip'] = _trip_add(tr, history, res, runs, M)
    if day and _descents(res):
        h['days'] += 1
        b = _day_bests(res)
        h['prev'] = {k: b[k] for k in DAY_SUM}
        if h['first'] is None:
            h['first'] = dict(h['prev'])
    st = _steepest(res, runs, M)
    if st and st['deg'] > h['best'].get('steep_deg', -1):
        h['best']['steep_deg'] = st['deg']
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


def moments(res, history, runs, M, trip=None):
    """{moments, items, story, share, theme, history}. items: [{key, t, seed, p}], each p numbers and names only; the
    places (fastest, fall) carry 'at' {lat, lon}, shown on the phone's own map and never in the share picture.
    trip: {start, last} of "your trip" (start: its first ski day, a key; last: today is its last ski day), or None."""
    history = history or empty_history()
    newh = update(history, res, runs, M, trip)
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
        p = dict({'kmh': st['max_kmh'], 'at': {'lat': at['lat'], 'lon': at['lon']}}, **_where(_leg_at(res, at['t'])))
        nth = next((i + 1 for i, s in enumerate(ds) if s['t0'] <= at['t'] <= s['t1']), None)
        if nth:  # the how-manieth descent of the day, of how many, and (with two or more) in which half
            p.update({'nth': nth, 'of': len(ds)})
            if len(ds) >= 2:
                p['when'] = 'early' if 2 * nth <= len(ds) else 'late'
        add('fastest', at['t'], p)
    # the longest descent
    if ds:
        s = max(ds, key=lambda s: (s['dist_m'], -s['t0']))
        add('longest_descent', s['t0'], {'t1': s['t1'], 'dist_m': s['dist_m'], 'drop_m': s['drop_m'],
                                         'moving_s': s['moving_s'], 'runs': _main_runs(s),
                                         'stops': len(s.get('stops', []))})
    # the first of each colour today, and whether it is the first ever
    done = _done(res, runs, M)
    for c in M['colors_first']:
        first = next(((k, t) for k, t in done if runs[k]['color'] == c), None)
        if first:
            add('first_color', first[1], {'color': c, 'run': first[0], 'first_ever': c not in history.get('colors', {}),
                                          'day_n': history.get('days', 0) + 1,
                                          'n': sum(1 for x in ds if c in _desc_colors(x, runs, M))})
    # runs skied for the first time (only against a history: on the first day everything is new)
    if known:
        new = [(k, t) for k, t in done if k not in history.get('runs', {})]
        if new:
            ever = set(history.get('runs', {})) | {k for k, _ in done}
            add('new_runs', new[0][1], {'runs': [k for k, _ in new], 'done': len(ever),
                                        'total': sum(1 for r in runs.values() if r['named'] and r['kind'] == 'run'
                                                     and r['color'])})
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
    qs = _queues(res)
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
    # insights (the content session, "the story of the day" as insights, 31.10.2026): against my own days only,
    # never against other people, never praising speed
    I = M['insights']
    bests = _day_bests(res)
    # against the day before, or the first day (from day 3): only a rise
    if day and ds:
        cands = []
        for vs, ref, need in (('first', history.get('first'), I['vs_first_times']), ('prev', history.get('prev'), 1 + I['vs_prev_rise'])):
            if not ref or (vs == 'first' and history.get('days', 0) < 2):
                continue
            for k in DAY_SUM:
                if ref.get(k) and bests.get(k) and bests[k] / ref[k] >= need:
                    cands.append((vs, k, bests[k] / ref[k], ref[k]))
            if cands:
                break  # the first day, when it says something, says more than the day before
        if cands:
            vs, k, r, before = max(cands, key=lambda c: (c[2], -DAY_SUM.index(c[1])))
            add('vs_day', end, {'vs': vs, 'what': k, 'value': bests[k], 'before': before, 'times': round(r, 1)})
    # flow: fewer stops inside descents in the second half of the day
    if len(ds) >= 2 * I['flow_min_each']:
        h = len(ds) // 2
        am, pm = ds[:h], ds[h:]
        a, b = sum(len(x.get('stops', [])) for x in am), sum(len(x.get('stops', [])) for x in pm)
        if a - b >= I['flow_min_fewer']:
            add('flow', pm[0]['t0'], {'am': a, 'am_n': len(am), 'pm': b, 'pm_n': len(pm)})
    # the favourite run of the day
    if ds:
        cnt = {}
        for x in ds:
            m = _main_runs(x, 1)
            if m:
                cnt[m[0]] = cnt.get(m[0], 0) + 1
        if cnt:
            fav, n = min(cnt.items(), key=lambda kv: (-kv[1], kv[0]))
            if n >= I['favorite_min'] and n >= I['favorite_share'] * len(ds):
                t = next(x['t0'] for x in ds if _main_runs(x, 1)[:1] == [fav])
                add('favorite', t, {'run': fav, 'n': n, 'of': len(ds)})
    # where the day went: skiing, on lifts, in queues (minutes)
    if day and len(ds) >= I['time_split_min_descents']:
        add('time_split', end, {'ski': round(st.get('ski_s', 0) / 60.0), 'lift_min': round(st.get('lift_s', 0) / 60.0),
                                'queue': round(sum(q[0] for q in qs) / 60.0)})
    # the steepest stretch skied today, and whether it is the steepest ever
    stp = _steepest(res, runs, M)
    if stp and stp['deg'] >= I['steep_min_deg']:
        prev = history.get('best', {}).get('steep_deg')
        add('steepest', stp['t'], {'run': stp['run'], 'deg': stp['deg'], 'ever': known and prev is not None and stp['deg'] > prev})
    # a lift whose queue is shorter at one time of the day
    qp = _queue_pattern(res, M) if day else None
    if qp:
        add('queue_pattern', end, qp)
    # one suggestion for tomorrow: never a colour harder than the hardest skied today
    if day:
        tm = _tomorrow(res, history, runs, M, done, qp)
        if tm:
            add('tomorrow', end, tm)
    # the end of the trip, on its last ski day (from its second day)
    tr = newh.get('trip')
    if day and ds and trip and trip.get('last') and tr and tr['start'] == trip['start'] and tr['days'] >= I['trip_min_days']:
        p = {'days': tr['days'], 'drop': int(round(tr['drop_m'])), 'done': len(tr['runs']), 'first': tr['first']}
        if tr['runs']:
            run, n = min(tr['runs'].items(), key=lambda kv: (-kv[1], kv[0]))
            if n >= 2:
                p.update({'run': run, 'n': n})
        add('trip', end, p)
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
        if k in M['story_last']:
            continue
        if len(story) < M['story_max'] and per.get(k, 0) < M['story_per_key']:
            story.append(i); per[k] = per.get(k, 0) + 1
    # a day without a comparison against my own days gets an insight in its place (story_fallback, the first ranked)
    keys = {items[i]['key'] for i in story}
    if 'vs_day' not in keys and not keys & set(M['story_fallback']):
        fb = next((i for i in rank if items[i]['key'] in M['story_fallback']), None)
        if fb is not None:
            if len(story) >= M['story_max']:
                story.pop()  # the last ranked of the story
            story.append(fb)
    story.sort()
    for k in M['story_last']:  # the end of the trip, then the suggestion for tomorrow: outside story_max, at the end
        story += [i for i, m in enumerate(items) if m['key'] == k]
    share = next((i for i in rank if items[i]['key'] not in M['share_exclude']), None)
    return {'moments': M['version'], 'items': items, 'story': story, 'share': share,
            'theme': _theme(items, ds, M), 'history': newh}


def _theme(items, ds, M):
    """The theme of the day, for the title of the story: progress (the first of a colour ever, or a record that is not
    speed or a day total), explore (3 new runs or more), endurance (a record of descents, drop or distance),
    favorite, easy (up to two descents), else steady. A speed record never sets the theme."""
    if not ds:
        return None
    keys = [(m['key'], m['p']) for m in items]
    if any(k == 'first_color' and p['first_ever'] for k, p in keys) or \
            any(k == 'record' and p['what'] == 'longest_m' for k, p in keys):
        return 'progress'
    if any(k == 'new_runs' and len(p['runs']) >= M['insights']['explore_min_new'] for k, p in keys):
        return 'explore'
    if any(k == 'record' and p['what'] in DAY_SUM for k, p in keys):
        return 'endurance'
    if any(k == 'favorite' for k, _ in keys):
        return 'favorite'
    if len(ds) <= 2:
        return 'easy'
    return 'steady'


def _tomorrow(res, history, runs, M, done, qp):
    """One suggestion: new_run (a run never skied, of the hardest colour skied today, from the lift ridden most
    today), finish_color (up to finish_max runs left of a colour skied today), or early_lift (the queue pattern)."""
    today = {runs[k]['color'] for k, _ in done if runs[k]['color'] in COLOR_ORDER}
    ever = set(history.get('runs', {})) | {k for k, _ in done}
    if today:
        hard = max(today, key=COLOR_ORDER.index)
        rides = {}
        for s in res.get('segments', []):
            if s['type'] == 'lift' and s.get('name'):
                rides.setdefault(s['name'], [0, s['t0']])[0] += 1
        if rides:
            lift = min(rides.items(), key=lambda kv: (-kv[1][0], kv[1][1]))[0]
            cand = sorted(k for k, r in runs.items() if r['named'] and r['kind'] == 'run' and r['color'] == hard
                          and k not in ever and lift in r.get('from_lifts', []))
            if cand:
                return {'kind': 'new_run', 'run': cand[0], 'color': hard, 'lift': lift}
        for c in sorted(today, key=COLOR_ORDER.index, reverse=True):
            left = sorted(k for k, r in runs.items() if r['named'] and r['kind'] == 'run' and r['color'] == c and k not in ever)
            if 1 <= len(left) <= M['insights']['finish_max']:
                return {'kind': 'finish_color', 'color': c, 'n': len(left), 'runs': left}
    if qp and qp['early'] < qp['late']:
        return {'kind': 'early_lift', 'lift': qp['lift'], 'split': qp['split']}
    return None


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
         'max_kmh': s.get('max_kmh'), 'falls': sum(1 for f in res.get('falls', []) if s['t0'] <= f['t'] <= s['t1']),
         'stops': len(s.get('stops', [])), 'nonstop': not s.get('stops'),
         'longest_so_far': len(_descents(res)) > 1 and all(s['dist_m'] > x['dist_m'] for x in _descents(res) if x is not s)}
    return {'key': 'last_descent', 't': s['t0'], 'seed': seed('last_descent', s['t0']), 'p': p}
