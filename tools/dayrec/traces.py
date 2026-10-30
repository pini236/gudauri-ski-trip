"""Consistency check on the public GPS traces of OpenStreetMap (docs/ARCHITECTURE.md m-8, answer 5).

The traces belong to the people who uploaded them and are not under the open licence of the map,
so they are NEVER saved in the repository: they are downloaded to a folder outside it, analysed,
and only totals are printed (and written to research/dayrec-traces.md by hand). They have no truth
(nobody says which run was skied), so they check consistency, not accuracy: lifts found, a sane
share of "unmatched", no impossible jumps.

    python3 tools/dayrec/traces.py download <folder outside the repo> [pages]
    python3 tools/dayrec/traces.py run <folder>
"""
import json, math, os, re, sys, time, urllib.request
from datetime import datetime, timezone

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import analyze, build  # noqa: E402

BBOX = (44.47, 42.455, 44.515, 42.52)  # the resort, without Kobi (west, south, east, north)
API = 'https://api.openstreetmap.org/api/0.6/trackpoints?bbox=%s,%s,%s,%s&page=%d'


def download(folder, pages=60):
    root = os.path.realpath(os.path.join(HERE, '..', '..'))
    if os.path.realpath(folder).startswith(root):
        sys.exit('the traces must not go into the repository')
    os.makedirs(folder, exist_ok=True)
    for p in range(pages):
        path = os.path.join(folder, 'page%03d.gpx' % p)
        if os.path.exists(path):
            continue
        req = urllib.request.Request(API % (BBOX + (p,)), headers={'User-Agent': 'gudauri-ski-trip dayrec check'})
        data = urllib.request.urlopen(req, timeout=120).read()
        open(path, 'wb').write(data)
        if data.count(b'<trkpt') < 5000:
            break
        time.sleep(1)


def tracks(folder):
    """Ordered tracks with times, from the pages (a track can span pages)."""
    pt = re.compile(r'<trkpt lat="([-0-9.]+)" lon="([-0-9.]+)">\s*(?:<ele>[^<]*</ele>\s*)?<time>([^<]+)</time>')
    out = []
    for f in sorted(os.listdir(folder)):
        txt = open(os.path.join(folder, f), encoding='utf-8').read()
        for seg in re.findall(r'<trkseg>(.*?)</trkseg>', txt, re.S):
            pts = [(float(a), float(b), datetime.fromisoformat(c.replace('Z', '+00:00')).timestamp()) for a, b, c in pt.findall(seg)]
            if len(pts) > 60:
                out.append(pts)
    return out


def run(folder):
    P = build.params()
    net = build.load_base(P)
    a = analyze.Analyzer(net, P)
    tot = {'tracks': 0, 'winter': 0, 'points': 0, 'lifts': 0, 'descents': 0, 'legs_m': {}, 'jumps': 0, 'hours': 0.0,
           'step_s': []}
    for tr in tracks(folder):
        tot['tracks'] += 1
        m = datetime.fromtimestamp(tr[0][2], timezone.utc).month
        if m not in (12, 1, 2, 3, 4):
            continue
        tot['winter'] += 1
        pts = []
        for la, lo, t in tr:
            pts.append({'t': int(t * 1000), 'la': la, 'lo': lo, 'a': None, 'p': None, 's': None, 'sa': None,
                        'ha': None, 'va': None, 'b': None, 'm': None})
        pts.sort(key=lambda q: q['t'])
        pts = [q for i, q in enumerate(pts) if i == 0 or q['t'] > pts[i - 1]['t']]
        steps = [(pts[i]['t'] - pts[i - 1]['t']) / 1000.0 for i in range(1, len(pts))]
        tot['step_s'] += steps
        tot['points'] += len(pts)
        tot['hours'] += (pts[-1]['t'] - pts[0]['t']) / 3.6e6
        for i in range(1, len(pts)):
            xy0 = net.proj.xy(pts[i - 1]['la'], pts[i - 1]['lo']); xy1 = net.proj.xy(pts[i]['la'], pts[i]['lo'])
            if math.hypot(xy1[0] - xy0[0], xy1[1] - xy0[1]) / max(1.0, steps[i - 1]) > 40:
                tot['jumps'] += 1
        # GPS altitude is not in the traces: the terrain model stands in, as a rough vertical signal
        for q in pts:
            q['a'] = net.terrain.elev(*net.proj.xy(q['la'], q['lo']))
        res = a.final({'v': 1}, pts)
        for s in res['segments']:
            if s['type'] == 'lift':
                tot['lifts'] += 1
            if s['type'] == 'drive':
                tot['drives'] = tot.get('drives', 0) + 1
                tot['drive_m'] = round(tot.get('drive_m', 0) + s['dist_m'])
            if s['type'] == 'descent':
                tot['descents'] += 1
                for lg in s['legs']:
                    tot['legs_m'][lg['kind']] = tot['legs_m'].get(lg['kind'], 0.0) + lg['dist_m']
    st = sorted(tot.pop('step_s')) or [0]
    tot['median_step_s'] = st[len(st) // 2]
    lm = tot['legs_m']; sm = sum(lm.values()) or 1
    tot['legs_share'] = {k: round(v / sm, 3) for k, v in lm.items()}
    tot['legs_m'] = {k: round(v) for k, v in lm.items()}
    tot['hours'] = round(tot['hours'], 1)
    print(json.dumps(tot, ensure_ascii=False, indent=1))


if __name__ == '__main__':
    if len(sys.argv) >= 3 and sys.argv[1] == 'download':
        download(sys.argv[2], int(sys.argv[3]) if len(sys.argv) > 3 else 60)
    elif len(sys.argv) >= 3 and sys.argv[1] == 'run':
        run(sys.argv[2])
    else:
        print(__doc__)
