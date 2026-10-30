"""The resort network for the day analysis: projection, terrain model, runs and lifts.

Reads the same files as the site (site/data/runs-and-lifts.json, site/data/terrain.json)
and builds what the analyzer needs. Standard library only, so it ports line by line.
"""
import base64, json, math, struct

KM = 111320.0  # metres per degree of latitude, as site/js/app.js


class Proj:
    """Local metres, exactly as site/js/app.js: x east, y south."""
    def __init__(self, lat0, lon0):
        self.lat0, self.lon0 = lat0, lon0
        self.kx = KM * math.cos(lat0 * math.pi / 180)

    def xy(self, lat, lon):
        return ((lon - self.lon0) * self.kx, -(lat - self.lat0) * KM)

    def ll(self, x, y):
        return (self.lat0 - y / KM, self.lon0 + x / self.kx)


class Terrain:
    """The terrain model of site/data/terrain.json, bilinear like GudRelief.load (site/js/relief.js)."""
    def __init__(self, t):
        d = t['dem']
        self.nx, self.ny = int(d['nx']), int(d['ny'])
        self.x0, self.y0, self.x1, self.y1 = float(d['x0']), float(d['y0']), float(d['x1']), float(d['y1'])
        raw = base64.b64decode(d['b64'])
        self.h = struct.unpack('<%dh' % (len(raw) // 2), raw)
        self.sx = (self.x1 - self.x0) / (self.nx - 1)
        self.sy = (self.y1 - self.y0) / (self.ny - 1)

    def elev(self, x, y):
        c = (x - self.x0) / self.sx
        r = (y - self.y0) / self.sy
        c = max(0.0, min(self.nx - 1.0001, c))
        r = max(0.0, min(self.ny - 1.0001, r))
        c0, r0 = int(c), int(r)
        fc, fr = c - c0, r - r0
        i = r0 * self.nx + c0
        H = self.h
        return (H[i] * (1 - fc) + H[i + 1] * fc) * (1 - fr) + (H[i + self.nx] * (1 - fc) + H[i + self.nx + 1] * fc) * fr

    def inside(self, x, y):
        return self.x0 <= x <= self.x1 and self.y0 <= y <= self.y1


class Line:
    """A polyline in local metres, with distance and position along it."""
    def __init__(self, pts):
        self.p = [(float(x), float(y)) for x, y in pts]
        self.cum = [0.0]
        for i in range(1, len(self.p)):
            self.cum.append(self.cum[-1] + math.hypot(self.p[i][0] - self.p[i - 1][0], self.p[i][1] - self.p[i - 1][1]))
        self.length = self.cum[-1]
        xs = [q[0] for q in self.p]; ys = [q[1] for q in self.p]
        self.box = (min(xs), min(ys), max(xs), max(ys))

    def nearest(self, x, y):
        """(distance, position along the line, x, y of the nearest point)."""
        best = (1e18, 0.0, self.p[0][0], self.p[0][1])
        p = self.p
        for i in range(len(p) - 1):
            ax, ay = p[i]; bx, by = p[i + 1]
            dx, dy = bx - ax, by - ay
            L2 = dx * dx + dy * dy
            t = 0.0 if L2 == 0 else max(0.0, min(1.0, ((x - ax) * dx + (y - ay) * dy) / L2))
            qx, qy = ax + t * dx, ay + t * dy
            d = math.hypot(x - qx, y - qy)
            if d < best[0]:
                best = (d, self.cum[i] + t * math.sqrt(L2), qx, qy)
        if len(p) == 1:
            best = (math.hypot(x - p[0][0], y - p[0][1]), 0.0, p[0][0], p[0][1])
        return best

    def at(self, s):
        """Point and unit direction at position s along the line."""
        s = max(0.0, min(self.length, s))
        for i in range(len(self.p) - 1):
            if self.cum[i + 1] >= s or i == len(self.p) - 2:
                seg = self.cum[i + 1] - self.cum[i]
                t = 0.0 if seg == 0 else (s - self.cum[i]) / seg
                ax, ay = self.p[i]; bx, by = self.p[i + 1]
                ux, uy = (bx - ax, by - ay)
                n = math.hypot(ux, uy) or 1.0
                return (ax + t * (bx - ax), ay + t * (by - ay)), (ux / n, uy / n)
        return self.p[0], (1.0, 0.0)

    def reversed(self):
        return Line(list(reversed(self.p)))


def point_in_polygon(x, y, poly):
    inside = False
    n = len(poly)
    j = n - 1
    for i in range(n):
        xi, yi = poly[i]; xj, yj = poly[j]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
            inside = not inside
        j = i
    return inside


class Grid:
    """Buckets of line indexes by cell, for the candidates near a point."""
    def __init__(self, lines, cell=200.0):
        self.cell = cell
        self.b = {}
        for i, ln in enumerate(lines):
            x0, y0, x1, y1 = ln.box
            for cx in range(int(math.floor(x0 / cell)), int(math.floor(x1 / cell)) + 1):
                for cy in range(int(math.floor(y0 / cell)), int(math.floor(y1 / cell)) + 1):
                    self.b.setdefault((cx, cy), []).append(i)

    def near(self, x, y, r):
        c = self.cell
        out = set()
        for cx in range(int(math.floor((x - r) / c)), int(math.floor((x + r) / c)) + 1):
            for cy in range(int(math.floor((y - r) / c)), int(math.floor((y + r) / c)) + 1):
                out.update(self.b.get((cx, cy), ()))
        return sorted(out)


class Piste:
    """One line (or area) of a run: a segment of site/data/runs-and-lifts.json."""
    def __init__(self, idx, key, named, seg_id, line, area):
        self.idx, self.key, self.named, self.seg, self.line, self.area = idx, key, named, seg_id, line, area

    def dist(self, x, y):
        if self.area and point_in_polygon(x, y, self.line.p):
            return 0.0
        return self.line.nearest(x, y)[0]


class Lift:
    """A lift oriented from its bottom to its top, with its stations along it."""
    def __init__(self, idx, raw, proj, terrain, params):
        self.idx = idx
        self.id = str(raw['id'])
        self.name = raw.get('name')
        self.kind = raw.get('kind')
        line = Line([proj.xy(la, lo) for la, lo in raw['g']])
        e0 = terrain.elev(*line.p[0]); e1 = terrain.elev(*line.p[-1])
        if e1 < e0:
            line = line.reversed(); e0, e1 = e1, e0
        self.line = line
        self.length = line.length
        dur = raw.get('dur')
        if dur:
            self.speed = float(raw.get('len') or line.length) / (float(dur) * 60.0)
            self.speed_src = 'data'
        else:
            self.speed = float(params['lift']['default_speed_mps'].get(self.kind, params['lift']['default_speed_mps']['other']))
            self.speed_src = 'kind'
        # stations: bottom, mid (only from data, lifts[].mid), top. u is the position along the cable.
        self.stations = [{'id': 'bottom', 'u': 0.0, 'ele': e0, 'name': None}]
        for i, m in enumerate(raw.get('mid') or []):
            mx, my = proj.xy(*m['pos'])
            d, u, _, _ = line.nearest(mx, my)
            if d > params['lift']['mid_max_off_m']:
                raise ValueError('lift %s: mid station %s is %.0f m from the cable' % (self.id, m.get('osm'), d))
            ele = float(m['ele']) if m.get('ele') is not None else terrain.elev(mx, my)
            self.stations.append({'id': 'mid%d' % (i + 1), 'u': u, 'ele': ele, 'name': m.get('name'), 'osm': m.get('osm')})
        self.stations.append({'id': 'top', 'u': line.length, 'ele': e1, 'name': None})
        self.stations.sort(key=lambda s: s['u'])

    def ele_at(self, u):
        st = self.stations
        for a, b in zip(st, st[1:]):
            if u <= b['u'] or b is st[-1]:
                f = 0.0 if b['u'] == a['u'] else max(0.0, min(1.0, (u - a['u']) / (b['u'] - a['u'])))
                return a['ele'] + f * (b['ele'] - a['ele'])
        return st[-1]['ele']


class Net:
    def __init__(self, runs_and_lifts, terrain_json, params, patch=None):
        rl = json.loads(json.dumps(runs_and_lifts))  # copy, so a test patch never leaks
        if patch:
            for lid, extra in (patch.get('lifts') or {}).items():
                for l in rl['lifts']:
                    if str(l['id']) == lid:
                        l.update(extra)
        p = terrain_json['proj']
        self.proj = Proj(float(p['lat0']), float(p['lon0']))
        self.terrain = Terrain(terrain_json)
        self.params = params
        self.pistes = []
        self.runs = {}  # key: color, kind, named (the moments: "the first red", "all the blues")
        for run in rl['pistes']:
            named = run.get('named') in (True, 'True', 'true')
            self.runs[run['key']] = {'color': run.get('color'), 'kind': run.get('kind', 'run'), 'named': named}
            for s in run['segs']:
                line = Line([self.proj.xy(la, lo) for la, lo in s['g']])
                self.pistes.append(Piste(len(self.pistes), run['key'], named, s['id'], line, bool(s.get('area'))))
        lp = params['lift']
        self.lifts = []
        for l in rl['lifts']:
            if l.get('status') == 'inactive' or not l.get('g') or len(l['g']) < 2:
                continue
            lift = Lift(len(self.lifts), l, self.proj, self.terrain, params)
            if lift.length < lp['min_len_m']:
                continue
            self.lifts.append(lift)
        self.grid = Grid([q.line for q in self.pistes])
        # roads of the terrain model (local metres already, as site/js/relief.js reads them)
        self.roads = []
        for k, flat in terrain_json.get('env', {}).get('roads', []):
            pts = [(flat[i], flat[i + 1]) for i in range(0, len(flat) - 1, 2)]
            if len(pts) >= 2:
                self.roads.append(Line(pts))
        self.road_grid = Grid(self.roads)
        self._labels()

    def near_pistes(self, x, y, r):
        """[(piste index, distance)] for the lines within r metres."""
        out = []
        for i in self.grid.near(x, y, r):
            d = self.pistes[i].dist(x, y)
            if d <= r:
                out.append((i, d))
        return out

    def nearest_road_dist(self, x, y, r=400.0):
        return min((self.roads[i].nearest(x, y)[0] for i in self.road_grid.near(x, y, r)), default=1e9)

    def nearest_piste_dist(self, x, y, r=400.0):
        n = self.near_pistes(x, y, r)
        return min((d for _, d in n), default=1e9)

    def _labels(self):
        """Every line, sampled along it, with the named runs that share it (docs: 'shared stretch')."""
        m = self.params['match']
        step, near, keep = m['sample_m'], m['shared_m'], m['shared_min_m']
        for q in self.pistes:
            n = max(1, int(q.line.length // step))
            pts = [q.line.at(q.line.length * k / n)[0] for k in range(n + 1)]
            sets = []
            for x, y in pts:
                s = set()
                for i, d in self.near_pistes(x, y, near):
                    o = self.pistes[i]
                    if o.named and o.key != q.key:
                        s.add(o.key)
                sets.append(s)
            # a run counts as shared only along a stretch of at least `keep` metres (not at a crossing)
            others = set().union(*sets) if sets else set()
            kept = [set() for _ in sets]
            span = q.line.length / n if n else 0.0
            for k in others:
                i = 0
                while i < len(sets):
                    if k in sets[i]:
                        j = i
                        while j + 1 < len(sets) and k in sets[j + 1]:
                            j += 1
                        if (j - i) * span >= keep:
                            for t in range(i, j + 1):
                                kept[t].add(k)
                        i = j + 1
                    else:
                        i += 1
            own = {q.key} if q.named else set()
            q.samples = [(pts[i], tuple(sorted(own | kept[i]))) for i in range(len(pts))]

    def label_at(self, piste_idx, x, y):
        """The named runs at the point of a line nearest to (x, y)."""
        q = self.pistes[piste_idx]
        if q.area:
            return (q.key,) if q.named else ()
        d, s, _, _ = q.line.nearest(x, y)
        n = len(q.samples) - 1
        k = 0 if n == 0 else int(round(s / q.line.length * n)) if q.line.length else 0
        return q.samples[max(0, min(n, k))][1]


def load(root, params, patch=None):
    import os
    rl = json.load(open(os.path.join(root, 'site/data/runs-and-lifts.json'), encoding='utf-8'))
    tj = json.load(open(os.path.join(root, 'site/data/terrain.json'), encoding='utf-8'))
    return Net(rl, tj, params, patch)
