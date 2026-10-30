"""The raw day log (docs: tools/dayrec/README.md, "Raw log"): one JSON object per line.

Line 1 is the header ({"v":1,...}). A point is a line with "t"; an event is a line with "ev".
A line that does not parse (a crash cut it) is dropped, wherever it is. Unknown fields are ignored.
"""
import json

FIELDS = ('t', 'la', 'lo', 'a', 'p', 's', 'sa', 'ha', 'va', 'b', 'm')


def read(text):
    header, points, events, bad = None, [], [], 0
    for line in text.split('\n'):
        line = line.strip()
        if not line:
            continue
        try:
            o = json.loads(line)
        except ValueError:
            bad += 1
            continue
        if not isinstance(o, dict):
            bad += 1
            continue
        if header is None and 'v' in o and 't' not in o:
            header = o
        elif 'ev' in o:
            events.append(o)
        elif 't' in o and 'la' in o and 'lo' in o:
            if o.get('la') is None or o.get('lo') is None:
                bad += 1
                continue
            points.append({k: o.get(k) for k in FIELDS})
        else:
            bad += 1
    points.sort(key=lambda q: q['t'])
    out = []
    for q in points:  # the same second twice (a resumed write): keep the first
        if out and q['t'] <= out[-1]['t']:
            continue
        out.append(q)
    return header or {}, out, events, bad


def line(o):
    return json.dumps(o, separators=(',', ':'), ensure_ascii=False)


def compact(text):
    """Closing a day (and recovering after a crash, at the next start): the motion windows of dismissed impacts are
    deleted for real (docs/ARCHITECTURE.md m-8, condition 10). Every other line stays as it was, in order; the
    dismissal line stays, without a window. The app writes the result to a temporary file and swaps it in atomically.
    Cut lines are dropped too. Nothing that is exported (GPX) ever carries a window."""
    lines = [ln for ln in text.split('\n') if ln.strip()]
    gone = set()
    for ln in lines:
        try:
            o = json.loads(ln)
        except ValueError:
            continue
        if isinstance(o, dict) and o.get('ev') == 'impact' and o.get('dismissed'):
            gone.add(o.get('t'))
    out = []
    for ln in lines:
        try:
            o = json.loads(ln)
        except ValueError:
            continue
        if isinstance(o, dict) and o.get('ev') == 'impact' and not o.get('dismissed') and o.get('t') in gone:
            continue
        out.append(ln)
    return '\n'.join(out) + '\n'
