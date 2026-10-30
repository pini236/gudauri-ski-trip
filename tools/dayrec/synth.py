"""Synthetic days on the real resort data, with the truth, for the test values (docs/ARCHITECTURE.md m-8, condition 4).

A day is built second by second from real geometry only: lift cables and run lines of
site/data/runs-and-lifts.json, altitudes of site/data/terrain.json. Then the sensors are
simulated (correlated GPS error, Doppler speed, GPS altitude with a slow bias, barometer with
a weather drift, accelerometer variance), and the raw log is written as an app would.
Nothing here invents a run: an "off-piste" stretch is a run line moved sideways, and is
checked to stay far from every line.
"""
import math, random
import rawlog

T0 = 1799645400000  # 2027-01-11 09:30 Asia/Tbilisi (05:30 UTC), in ms


class Day:
    def __init__(self, net, seed, t0=T0, skier=None):
        self.net = net
        self.rng = random.Random(seed)
        self.t = t0
        self.t0 = t0
        self.rows = []
        self.rides = []
        self.marks = {}
        self.impacts = []   # what the phone felt: {'t' (ms, not on the second), 'kind', 'peak_g', 'rot', 'act'}
        self.falls = []     # the truth: times of real falls
        self.random_events = False
        self.pos = None
        sk = skier or {}
        self.vmax = sk.get('vmax', 17.0)
        self.wander = sk.get('wander', 7.0)

    # ----- geometry helpers -----
    def lift(self, name):
        return next(l for l in self.net.lifts if l.name == name)

    def run_line(self, key, seg):
        q = next(p for p in self.net.pistes if p.key == key and p.seg == seg)
        ln = q.line
        T = self.net.terrain
        if T.elev(*ln.p[0]) < T.elev(*ln.p[-1]):
            ln = ln.reversed()
        return ln

    def _emit(self, x, y, z, act, lift=None):
        self.rows.append({'t': self.t, 'x': x, 'y': y, 'z': z, 'act': act, 'lift': lift})
        self.t += 1000
        self.pos = (x, y)

    def at(self, x, y):
        self.pos = (x, y)
        return self

    # ----- moves -----
    def stand(self, secs, act='stand'):
        x, y = self.pos
        z = self.net.terrain.elev(x, y)
        for _ in range(int(secs)):
            self._emit(x, y, z, act)
        return self

    def walk_to(self, x1, y1, speed=1.2):
        x0, y0 = self.pos
        d = math.hypot(x1 - x0, y1 - y0)
        n = max(1, int(math.ceil(d / speed)))
        for k in range(1, n + 1):
            x, y = x0 + (x1 - x0) * k / n, y0 + (y1 - y0) * k / n
            self._emit(x, y, self.net.terrain.elev(x, y), 'walk')
        return self

    def walk_to_lift(self, name):
        l = self.lift(name)
        return self.walk_to(*l.line.p[0])

    def ride(self, name, u0=None, u1=None, speed=1.0, pause=None, bump_at=None):
        """Ride a lift from u0 to u1 (fractions). speed: the real speed against the data's. pause=(at, secs): the lift stops."""
        l = self.lift(name)
        u0 = 0.0 if u0 is None else u0 * l.length
        u1 = l.length if u1 is None else u1 * l.length
        sp = l.speed * speed
        # walk onto the boarding point first, if not there
        bx, by = l.line.at(u0)[0]
        if self.pos is None:
            self.pos = (bx, by)
        elif math.hypot(self.pos[0] - bx, self.pos[1] - by) > 3:
            self.walk_to(bx, by)
        tb = self.t
        n = max(1, int(round((u1 - u0) / sp)))
        paused = False
        bump = (u0 + (u1 - u0) * self.rng.uniform(0.2, 0.8)) if (self.random_events and self.rng.random() < 0.35) or bump_at else None
        if bump_at:
            bump = u0 + (u1 - u0) * bump_at
        for k in range(1, n + 1):
            u = u0 + (u1 - u0) * k / n
            (x, y), _ = l.line.at(u)
            # on the ground at the stations where one gets on and off, up to 12 m above it in between
            z = self.net.terrain.elev(x, y) + 12.0 * math.sin(math.pi * (u - u0) / max(1.0, u1 - u0))
            self._emit(x, y, z, 'lift', l.id)
            if bump is not None and u >= bump:
                self._impact('lift_bump', self.rng.uniform(2.6, 3.4), self.rng.uniform(0.5, 2.0), 'lift')
                bump = None
            if pause and not paused and u >= u0 + (u1 - u0) * pause[0]:
                paused = True
                for _ in range(int(pause[1])):
                    self._emit(x, y, z, 'lift', l.id)
        self.rides.append({'lift': l.id, 't0': tb, 't1': self.t - 1000,
                           'from': self._st(l, u0), 'to': self._st(l, u1)})
        return self

    def _st(self, l, u):
        st = min(l.stations, key=lambda q: abs(q['u'] - u))
        return st['id'] if abs(st['u'] - u) <= 60 else None

    def ski_line(self, ln, s0=None, s1=None, act='ski', events=None):
        """Ski a line (oriented downhill) from s0 to s1, with turns and speed from the slope."""
        T = self.net.terrain
        if s0 is None:
            s0 = ln.nearest(*self.pos)[1] if self.pos else 0.0
        s1 = ln.length if s1 is None else s1
        (sx, sy), _ = ln.at(s0)
        if self.pos and math.hypot(sx - self.pos[0], sy - self.pos[1]) > 8:
            self._glide(sx, sy)
        amp = self.wander * self.rng.uniform(0.6, 1.0)
        lam = self.rng.uniform(45, 80) * max(1.0, amp / 7.0)
        v = max(2.0, getattr(self, '_v', 3.0))
        s = s0
        evs = sorted((s0 + f * (s1 - s0), k) for f, k in (events or []))
        if self.random_events and self.rng.random() < 0.7:
            evs.append((s0 + self.rng.uniform(0.2, 0.8) * (s1 - s0), self.rng.choice(EVENT_KINDS)))
            evs.sort()
        while s < s1 - 0.5:
            (px, py), _ = ln.at(s)
            (qx, qy), _ = ln.at(min(ln.length, s + 20))
            g = (T.elev(px, py) - T.elev(qx, qy)) / max(1.0, math.hypot(qx - px, qy - py))
            target = max(4.0, min(self.vmax, 4.0 + 55.0 * g))
            v += max(-2.0, min(1.5, target - v))
            s = min(s1, s + v)
            (x, y), (ux, uy) = ln.at(s)
            taper = min(1.0, (s - s0) / 40.0, (s1 - s) / 40.0)
            off = amp * taper * math.sin(2 * math.pi * (s - s0) / lam)
            x, y = x - uy * off, y + ux * off
            self._emit(x, y, T.elev(x, y), act)
            while evs and s >= evs[0][0]:
                v, s = self._event(evs.pop(0)[1], ln, s, v)
        self._v = v
        return self

    def _impact(self, kind, peak, rot, act='ski'):
        t = self.t - 1000 + self.rng.randint(150, 850)  # during the second just written
        self.impacts.append({'t': t, 'kind': kind, 'peak_g': peak, 'rot': rot, 'act': act})
        return t

    def _event(self, kind, ln, s, v):
        """Something happens while skiing at s: a fall, a jump, the phone falling in the pocket, a bump, a hard stop."""
        T = self.net.terrain
        r = self.rng
        if kind == 'fall':
            self.falls.append(self._impact('fall', r.uniform(3.0, 6.0), r.uniform(4.0, 9.0)))
            for f in (0.35, 0.08):  # sliding to a stop
                s = min(ln.length, s + v * f)
                (x, y), _ = ln.at(s)
                self._emit(x, y, T.elev(x, y), 'ski')
            self.stand(r.randint(8, 25))
            return 1.5, s
        if kind == 'hockey':  # a hard stop on purpose, as fast as a fall, but no blow
            for f in (0.35, 0.08):
                s = min(ln.length, s + v * f)
                (x, y), _ = ln.at(s)
                self._emit(x, y, T.elev(x, y), 'ski')
            self.stand(r.randint(10, 25))
            return 1.5, s
        if kind == 'jump':
            self._impact('jump', r.uniform(2.8, 4.8), r.uniform(0.8, 2.8))
        elif kind == 'pocket':
            self._impact('pocket', r.uniform(2.6, 3.6), r.uniform(5.0, 9.0))
        elif kind == 'bump':
            self._impact('bump', r.uniform(2.55, 3.3), r.uniform(0.4, 1.8))
        return v, s

    def _glide(self, x1, y1):
        x0, y0 = self.pos
        d = math.hypot(x1 - x0, y1 - y0)
        n = max(1, int(math.ceil(d / 4.0)))
        T = self.net.terrain
        for k in range(1, n + 1):
            x, y = x0 + (x1 - x0) * k / n, y0 + (y1 - y0) * k / n
            self._emit(x, y, T.elev(x, y), 'ski')

    def ski(self, key, seg, until=None, frm=None, stop=None, events=None):
        """Ski a run line. until=(key, seg): stop at the point nearest to that line's start.
        frm='start' starts at the top of the line instead of the nearest point. stop=(s, secs): a rest on the way."""
        ln = self.run_line(key, seg)
        s0 = 0.0 if frm == 'start' else None
        s1 = None
        if until:
            o = self.run_line(*until)
            s1 = ln.nearest(*o.p[0])[1]
        if stop:
            at, secs = stop
            self.ski_line(ln, s0, at * ln.length)
            self._v = 2.0
            self.stand(secs)
            self.ski_line(ln, None, s1)
        else:
            self.ski_line(ln, s0, s1, events=events)
        return self

    def offpiste(self, key, seg, a, b, dist):
        """Ski a run line from a to b (fractions), going `dist` metres to the side and back in between."""
        ln = self.run_line(key, seg)
        sa, sb = a * ln.length, b * ln.length
        self.ski_line(ln, None, sa)
        best = None
        for side in (1, -1):
            pts = []
            n = int((sb - sa) / 10)
            for k in range(n + 1):
                s = sa + (sb - sa) * k / n
                (x, y), (ux, uy) = ln.at(s)
                f = math.sin(math.pi * k / n) ** 2
                pts.append((x - uy * dist * f * side, y + ux * dist * f * side))
            mid = pts[len(pts) // 3: 2 * len(pts) // 3]
            clear = min(self.net.nearest_piste_dist(x, y) for x, y in mid)
            if best is None or clear > best[0]:
                best = (clear, pts)
        import net as netmod
        self._offclear = best[0]
        self.ski_line(netmod.Line(best[1]), 0.0)
        self.ski_line(ln, sb)
        return self


# ---------- truth labels for the metrics ----------

def truth_labels(net, rows, on_m=20.0, off_m=40.0):
    """Per ski row: tuple of named runs within on_m, 'off' if no line within off_m, None if in between."""
    for r in rows:
        if r['act'] != 'ski':
            r['runs'] = None; continue
        near = net.near_pistes(r['x'], r['y'], off_m)
        if not near:
            r['runs'] = 'off'; continue
        on = sorted({net.pistes[i].key for i, d in near if d <= on_m and net.pistes[i].named})
        if on:
            r['runs'] = tuple(on)
        elif any(d <= on_m for i, d in near):
            r['runs'] = ()  # an unnamed line
        else:
            r['runs'] = None


# ---------- sensors and the raw log ----------

def true_velocity(rows, i):
    """The skier's velocity at row i: direction of the central difference, speed the mean of the two steps."""
    a = rows[max(0, i - 1)]; r = rows[i]; b = rows[min(len(rows) - 1, i + 1)]
    steps = [math.hypot(q['x'] - p['x'], q['y'] - p['y']) for p, q in ((a, r), (r, b)) if q is not p]
    sp = sum(steps) / len(steps) if steps else 0.0
    dx, dy = b['x'] - a['x'], b['y'] - a['y']
    n = math.hypot(dx, dy)
    if n < 1e-9:
        return 0.0, 0.0, 0.0
    return dx / n * sp, dy / n * sp, sp


EVENT_KINDS = ('fall', 'jump', 'pocket', 'bump', 'hockey')


HACC = {'stand': 6.0, 'walk': 6.0, 'ski': 5.0, 'lift': 6.0}


def sense(net, day, seed, baro=True, gondola_hacc=12.0, device='synthetic'):
    """Raw log points (dicts in the raw-log fields) for the truth rows of a day."""
    rng = random.Random(seed)
    rows = day.rows
    out = []
    ex = ey = 0.0
    bias = rng.gauss(0, 4)
    kinds = {l.id: l.kind for l in net.lifts}
    for i, r in enumerate(rows):
        ha = HACC[r['act']]
        if r['act'] == 'lift' and kinds.get(r['lift']) == 'gondola':
            ha = gondola_hacc
        sig = ha * 0.6
        rho = 0.9
        ex = rho * ex + math.sqrt(1 - rho * rho) * rng.gauss(0, sig)
        ey = rho * ey + math.sqrt(1 - rho * rho) * rng.gauss(0, sig)
        vx, vy, st = true_velocity(rows, i)
        s = abs(st + rng.gauss(0, 0.12))
        sa = round(rng.uniform(0.3, 0.7) if r['act'] != 'lift' or ha < 10 else rng.uniform(1.1, 1.6), 2)
        brg = None
        if st >= 0.5:
            brg = (math.degrees(math.atan2(vx, -vy)) + rng.gauss(0, 2.0)) % 360
        bias = 0.998 * bias + math.sqrt(1 - 0.998 ** 2) * rng.gauss(0, 4)
        alt = r['z'] + bias + rng.gauss(0, 2.0)
        p = None
        if baro:
            drift = 8.0 * (r['t'] - day.t0) / 10800000.0
            p = 1013.25 * (1 - (r['z'] + drift) / 44330.77) ** (1 / 0.190263) + rng.gauss(0, 0.008)
        mv = {'stand': 0.02, 'lift': 0.06, 'walk': 1.2, 'ski': 2.5}[r['act']] * math.exp(rng.gauss(0, 0.4))
        la, lo = net.proj.ll(r['x'] + ex, r['y'] + ey)
        out.append({'t': r['t'], 'la': round(la, 7), 'lo': round(lo, 7), 'a': round(alt, 1),
                    'p': round(p, 3) if p is not None else None, 's': round(s, 2), 'sa': sa,
                    'ha': round(ha * rng.uniform(0.85, 1.15), 1), 'va': 8.0,
                    'b': round(brg, 1) if brg is not None else None, 'm': round(mv, 3)})
    return out


def spike(net, pts, t, kmh=100.0):
    """One reading jumps to kmh, with a position jump, and looks accurate."""
    for q in pts:
        if q['t'] == t:
            q['s'] = round(kmh / 3.6, 2); q['sa'] = 0.8
            q['la'] = round(q['la'] + 25 / 111320.0, 7)
    return pts


def drop_range(pts, t0, t1):
    return [q for q in pts if not (t0 <= q['t'] <= t1)]


def write_log(pts, header, events=(), cut_at=None):
    """The raw log text. cut_at=t: the line of that point is cut in the middle (a crash), then a resume event."""
    lines = [rawlog.line(header)]
    evs = sorted(events, key=lambda e: e['t'])
    ei = 0
    for q in pts:
        while ei < len(evs) and evs[ei]['t'] <= q['t']:
            lines.append(rawlog.line(evs[ei])); ei += 1
        ln = rawlog.line(q)
        if cut_at is not None and q['t'] == cut_at:
            lines.append(ln[:len(ln) // 2])
            continue
        lines.append(ln)
    while ei < len(evs):
        lines.append(rawlog.line(evs[ei])); ei += 1
    return '\n'.join(lines) + '\n'


def impact_window(ev, rng, P):
    """The motion window the phone writes around a blow (docs/ARCHITECTURE.md m-8, condition 10): 50 Hz,
    pre_s before and post_s after the peak, accelerometer (m/s², with gravity) and gyroscope (rad/s), flat x,y,z."""
    F = P['fall']
    hz, pre, post = F['hz'], F['pre_s'], F['post_s']
    n = int(round((pre + post) * hz))
    t0 = ev['t'] - int(pre * 1000)
    ax, ay = rng.gauss(0, 1), rng.gauss(0, 1)
    an = math.hypot(ax, ay) or 1.0
    noise_a = 0.3 if ev['act'] == 'lift' else 2.2
    noise_w = 0.1 if ev['act'] == 'lift' else 0.5
    d = [rng.gauss(0, 1) for _ in range(3)]; dn = math.sqrt(sum(q * q for q in d)) or 1.0
    d = [q / dn for q in d]
    w = [rng.gauss(0, 1) for _ in range(3)]; wn = math.sqrt(sum(q * q for q in w)) or 1.0
    w = [q / wn for q in w]
    acc, gyr = [], []
    for k in range(n):
        tt = k / hz - pre
        lying = ev['kind'] == 'fall' and tt > 0.8
        na = 0.15 if lying else noise_a
        nw = 0.05 if lying else noise_w
        env = math.exp(-0.5 * (tt / 0.035) ** 2)  # the blow: its peak is a sample, at ev['t']
        rot = ev['rot'] * math.exp(-0.5 * (tt / 0.15) ** 2)
        g = [0.0, 0.0, 9.80665]
        a = [g[i] * (1 - env) + d[i] * ev['peak_g'] * 9.80665 * env + rng.gauss(0, na) for i in range(3)]
        acc += [round(q, 2) for q in a]
        gyr += [round(w[i] * rot + rng.gauss(0, nw), 3) for i in range(3)]
    return {'ev': 'impact', 't': int(ev['t']), 'hz': hz, 't0': t0, 'acc': acc, 'gyr': gyr}
