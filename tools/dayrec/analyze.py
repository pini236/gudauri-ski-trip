"""The day analysis, reference implementation (decision 76, docs/ARCHITECTURE.md m-8).

Raw log in, day model out. Two modes over the same steps:
  final(): everything known, smoothed backwards (Kalman + RTS, full Viterbi). This is the truth of the day.
  live():  causal only, every value at time t uses readings up to t (forward filter, forward Viterbi argmax).
Every threshold comes from tools/fixtures/dayrec-params.json. The steps, in order, are described in
tools/dayrec/README.md ("The algorithm"); the apps implement the same steps and match tools/fixtures/dayrec-cases.json.
"""
import math

NEG = -1e9
STAND, WALK, SKI, UP = 0, 1, 2, 3
ACT = {STAND: 'stand', WALK: 'walk', SKI: 'ski', UP: 'up'}


def kmh(v):
    return round(v * 3.6, 2)


# ---------- 1. Kalman filter, per axis, state (position, velocity) ----------

class Axis:
    def __init__(self):
        self.p = self.v = 0.0
        self.a = self.b = self.c = 0.0  # covariance [[a, b], [b, c]]

    def reset(self, z, rz, v, rv):
        self.p, self.v, self.a, self.b, self.c = z, v, rz, 0.0, rv

    def predict(self, dt, q):
        self.p += self.v * dt
        q2 = q * q
        a, b, c = self.a, self.b, self.c
        self.a = a + 2 * b * dt + c * dt * dt + q2 * dt ** 4 / 4
        self.b = b + c * dt + q2 * dt ** 3 / 2
        self.c = c + q2 * dt * dt

    def innov_pos(self, z, r):
        return z - self.p, self.a + r

    def upd_pos(self, y, s):
        k0, k1 = self.a / s, self.b / s
        a, b, c = self.a, self.b, self.c
        self.p += k0 * y; self.v += k1 * y
        self.a, self.b, self.c = (1 - k0) * a, (1 - k0) * b, c - k1 * b

    def innov_vel(self, z, r):
        return z - self.v, self.c + r

    def upd_vel(self, y, s):
        k0, k1 = self.b / s, self.c / s
        a, b, c = self.a, self.b, self.c
        self.p += k0 * y; self.v += k1 * y
        self.a, self.b, self.c = a - k0 * b, b - k0 * c, (1 - k1) * c

    def state(self):
        return (self.p, self.v, self.a, self.b, self.c)


def kalman(xy, pts, P, smooth):
    """Positions and velocities in local metres. Returns [(x, y, vx, vy)] per point."""
    K = P['kalman']
    q, rmin, vmin, gate, gn = K['q_acc'], K['pos_sigma_min_m'], K['vel_sigma_min_mps'], K['gate_chi2'], K['gate_reset_n']
    X, Y = Axis(), Axis()
    n = len(pts)
    fx, fy, px, py, dts, starts = [], [], [], [], [], []
    gated = 0
    for i, pt in enumerate(pts):
        x, y = xy[i]
        r = max(rmin, pt['ha'] if pt['ha'] is not None else 10.0) ** 2
        vel = None
        if pt['s'] is not None and pt['b'] is not None:
            rv = max(vmin, pt['sa'] if pt['sa'] is not None else 1.0) ** 2
            br = math.radians(pt['b'])
            vel = (pt['s'] * math.sin(br), -pt['s'] * math.cos(br), rv)
        elif pt['s'] is not None and pt['s'] < K['still_mps']:
            # no bearing because the phone is not moving: the velocity is about zero
            rv = max(vmin, pt['s'] + (pt['sa'] if pt['sa'] is not None else 1.0)) ** 2
            vel = (0.0, 0.0, rv)
        dt = (pt['t'] - pts[i - 1]['t']) / 1000.0 if i else None
        if i == 0 or dt > P['reset_gap_s'] or gated >= gn:
            v0 = vel if vel else (0.0, 0.0, 16.0)
            X.reset(x, r, v0[0], v0[2]); Y.reset(y, r, v0[1], v0[2])
            gated = 0
            starts.append(True)
            px.append(X.state()); py.append(Y.state()); dts.append(0.0)
        else:
            X.predict(dt, q); Y.predict(dt, q)
            starts.append(False)
            px.append(X.state()); py.append(Y.state()); dts.append(dt)
            yx, sx = X.innov_pos(x, r); yy, sy = Y.innov_pos(y, r)
            if yx * yx / sx + yy * yy / sy > gate:
                gated += 1
            else:
                gated = 0
                X.upd_pos(yx, sx); Y.upd_pos(yy, sy)
            if vel:
                ux, tx = X.innov_vel(vel[0], vel[2]); uy, ty = Y.innov_vel(vel[1], vel[2])
                if ux * ux / tx + uy * uy / ty <= gate:
                    X.upd_vel(ux, tx); Y.upd_vel(uy, ty)
        fx.append(X.state()); fy.append(Y.state())
    if not smooth:
        return [(fx[i][0], fy[i][0], fx[i][1], fy[i][1]) for i in range(n)]
    sx_ = rts(fx, px, dts, starts)
    sy_ = rts(fy, py, dts, starts)
    return [(sx_[i][0], sy_[i][0], sx_[i][1], sy_[i][1]) for i in range(n)]


def rts(f, p, dts, starts):
    """Rauch-Tung-Striebel smoother for one axis; never across a reset."""
    n = len(f)
    out = [None] * n
    if not n:
        return out
    out[-1] = (f[-1][0], f[-1][1])
    for k in range(n - 2, -1, -1):
        if starts[k + 1]:
            out[k] = (f[k][0], f[k][1]); continue
        dt = dts[k + 1]
        a, b, c = f[k][2], f[k][3], f[k][4]
        # C = Pf F^T Pp^-1, F = [[1, dt], [0, 1]]
        m00, m01, m10, m11 = a + b * dt, b, b + c * dt, c
        pa, pb, pc = p[k + 1][2], p[k + 1][3], p[k + 1][4]
        det = pa * pc - pb * pb
        if det <= 0:
            out[k] = (f[k][0], f[k][1]); continue
        i00, i01, i11 = pc / det, -pb / det, pa / det
        c00 = m00 * i00 + m01 * i01; c01 = m00 * i01 + m01 * i11
        c10 = m10 * i00 + m11 * i01; c11 = m10 * i01 + m11 * i11
        d0 = out[k + 1][0] - p[k + 1][0]; d1 = out[k + 1][1] - p[k + 1][1]
        out[k] = (f[k][0] + c00 * d0 + c01 * d1, f[k][1] + c10 * d0 + c11 * d1)
    return out


# ---------- 2. Maximum speed, from the raw Doppler readings ----------

def valid_speeds(pts, xy, P):
    M = P['max_speed']
    n = len(pts)
    W, mdt = M['pos_window_s'], M['max_pair_dt_s']
    ok = [False] * n
    for i in range(n):
        s = pts[i]['s']; sa = pts[i]['sa']
        if s is None or sa is None or sa > M['speed_acc_max_mps']:
            continue
        good = True
        for j in (i - 1, i + 1):  # acceleration against both neighbours
            if 0 <= j < n and pts[j]['s'] is not None:
                dt = abs(pts[j]['t'] - pts[i]['t']) / 1000.0
                if dt <= mdt and abs(pts[j]['s'] - s) / dt > M['accel_max_mps2']:
                    good = False
        if not good:
            continue
        # the Doppler speed agrees with how far the raw positions moved, over i-W .. i+W
        lo, hi = i - W, i + W
        if lo < 0 or hi >= n:
            continue
        path = dur = sd = 0.0
        for k in range(lo + 1, hi + 1):
            dt = (pts[k]['t'] - pts[k - 1]['t']) / 1000.0
            if dt > mdt or pts[k]['s'] is None or pts[k - 1]['s'] is None:
                good = False; break
            path += math.hypot(xy[k][0] - xy[k - 1][0], xy[k][1] - xy[k - 1][1])
            dur += dt
            sd += (pts[k]['s'] + pts[k - 1]['s']) / 2 * dt
        if not good:
            continue
        if abs(path - sd) > max(M['pos_match_rel'] * sd, M['pos_match_abs_mps'] * dur):
            continue
        ok[i] = True
    return ok


def max_speed(pts, ok, idx, P):
    """The highest speed held over sustain_s readings in a row, all valid. (value m/s, point index) or None."""
    M = P['max_speed']
    n = M['sustain_s']
    best = None
    idx = sorted(idx)
    for a in range(len(idx) - n + 1):
        w = idx[a:a + n]
        if any(w[k + 1] != w[k] + 1 for k in range(n - 1)):
            continue
        if not all(ok[k] for k in w):
            continue
        if any((pts[w[k + 1]]['t'] - pts[w[k]]['t']) / 1000.0 > M['max_pair_dt_s'] for k in range(n - 1)):
            continue
        v = min(pts[k]['s'] for k in w)
        at = min(w, key=lambda k: (pts[k]['s'], k))
        if best is None or v > best[0]:
            best = (v, at)
    return best


# ---------- 3. Altitude: source, smoothing, vertical speed, station calibration ----------

def baro_alt(p):
    return 44330.77 * (1 - (p / 1013.25) ** 0.190263)


def alt_source(pts):
    """Barometer if most readings have it (relative), else GPS altitude. (series, baro?)"""
    nb = sum(1 for q in pts if q['p'] is not None)
    baro = nb * 2 >= len(pts) and nb > 0
    out, last = [], None
    for q in pts:
        if baro:
            v = baro_alt(q['p']) if q['p'] is not None else last
        else:
            v = q['a'] if q['a'] is not None else last
        if v is None:
            v = 0.0
        out.append(v); last = v
    return out, baro


def window_mean(ts, vals, i, lo, hi):
    """Mean of vals over the points with lo <= t <= hi (times in ms), around index i."""
    s = n = 0
    j = i
    while j >= 0 and ts[j] >= lo:
        if ts[j] <= hi:
            s += vals[j]; n += 1
        j -= 1
    j = i + 1
    while j < len(ts) and ts[j] <= hi:
        if ts[j] >= lo:
            s += vals[j]; n += 1
        j += 1
    return s / n if n else vals[i]


def smooth_alt(ts, src, P, live):
    w = P['alt']['ma_s'] * 1000
    out = []
    for i, t in enumerate(ts):
        out.append(window_mean(ts, src, i, t - w, t) if live else window_mean(ts, src, i, t - w / 2, t + w / 2))
    return out


def vertical_speed(ts, h, P, live):
    w = P['alt']['vz_window_s'] * 1000
    n = len(ts)
    out = []
    j = 0
    k = 0
    for i, t in enumerate(ts):
        lo = t - (w if live else w / 2)
        hi = t if live else t + w / 2
        while ts[j] < lo:
            j += 1
        if k < i:
            k = i
        while k + 1 < n and ts[k + 1] <= hi:
            k += 1
        dt = (ts[k] - ts[j]) / 1000.0
        out.append((h[k] - h[j]) / dt if dt > 0 else 0.0)
    return out


def drop(h, idx, P):
    """Vertical drop with a hysteresis threshold, over the given point indexes."""
    th = P['alt']['hyst_m']
    tot, ref = 0.0, None
    for i in idx:
        v = h[i]
        if ref is None or v > ref + th:  # a climb of more than the threshold moves the reference up
            ref = v
        elif ref - v >= th:
            tot += ref - v; ref = v
    return tot


def offsets(meas, A):
    """[(from time, offset)] from station measurements [(time, station altitude - source altitude)].
    A measurement is used if it is within cal_jump_max_m of the last one used, or of the last one refused
    (two in a row that agree win, so one bad first station does not lock the day). The offset is the
    median of the last cal_median_n used."""
    acc, last_rej, offs = [], None, []
    for t, m in meas:
        j = A['cal_jump_max_m']
        if not acc or abs(m - acc[-1]) <= j or (last_rej is not None and abs(m - last_rej) <= j):
            acc.append(m); last_rej = None
            k = sorted(acc[-A['cal_median_n']:])
            h = len(k) // 2
            offs.append((t, k[h] if len(k) % 2 else (k[h - 1] + k[h]) / 2))
        else:
            last_rej = m
    return offs


# ---------- 4. Activity: Viterbi over stand, walk, ski, up and one state per lift ----------

def lift_features(net, x, y, P):
    """{lift index: (distance, u along the cable, along-cable unit vector)} for the lifts close enough."""
    out = {}
    m = P['lift']['max_dist_m']
    for l in net.lifts:
        b = l.line.box
        if x < b[0] - m or x > b[2] + m or y < b[1] - m or y > b[3] + m:
            continue
        d, u, _, _ = l.line.nearest(x, y)
        if d <= m:
            out[l.idx] = (d, u, l.line.at(u)[1])
    return out


def emissions(net, pts, kf, vz, P):
    A, L = P['activity'], P['lift']
    nl = len(net.lifts)
    E, feats = [], []
    for i, pt in enumerate(pts):
        x, y, vx, vy = kf[i]
        vh = math.hypot(vx, vy)
        z = vz[i]
        m = pt['m']
        lvl = -0.5 * (z / A['level_vz_sigma_mps']) ** 2
        mlow = 0.0 if m is None else -min(A['motion_cap'], 0.5 * (m / A['motion_low']) ** 2)
        mhigh = 0.0 if m is None else -min(A['motion_cap'], 0.5 * (max(0.0, A['motion_high'] - m) / A['motion_sigma']) ** 2)
        e = [0.0] * (4 + nl)
        e[STAND] = -0.5 * (vh / A['stand_sigma_mps']) ** 2 + lvl + mlow
        e[WALK] = -0.5 * ((vh - A['walk_mean_mps']) / A['walk_sigma_mps']) ** 2 + lvl + mhigh
        e[SKI] = -0.5 * (max(0.0, A['ski_min_mps'] - vh) / A['ski_sigma_mps']) ** 2 \
            - 0.5 * (max(0.0, z) / A['ski_vz_sigma_mps']) ** 2 + mhigh
        e[UP] = -0.5 * (max(0.0, A['up_vz_mps'] - z) / A['up_vz_sigma_mps']) ** 2 - A['up_cost']
        f = lift_features(net, x, y, P)
        feats.append(f)
        ha = pt['ha'] if pt['ha'] is not None else 10.0
        sd = max(L['sigma_m'], ha)
        for li in range(nl):
            if li not in f:
                e[4 + li] = NEG; continue
            d, u, (ux, uy) = f[li]
            sp = net.lifts[li].speed
            va = vx * ux + vy * uy
            vc = -vx * uy + vy * ux
            # the speed in the data is a guide, not a rule: lifts slow down and the data can be old
            over = max(0.0, L['speed_lo'] * sp - va, va - L['speed_hi'] * sp)
            e[4 + li] = -0.5 * (d / sd) ** 2 - 0.5 * (over / (L['speed_rel_sigma'] * sp)) ** 2 \
                - 0.5 * (vc / L['cross_sigma_mps']) ** 2 \
                - 0.5 * (max(0.0, L['vz_min_mps'] - z) / L['vz_sigma_mps']) ** 2 + mlow
        E.append(e)
    return E, feats


def board_ok(net, li, f, P):
    if li not in f:
        return False
    u = f[li][1]
    return any(abs(u - s['u']) <= P['lift']['board_m'] for s in net.lifts[li].stations[:-1])


def viterbi_activity(net, E, feats, P, live):
    A = P['activity']
    S = len(E[0]) if E else 0
    T = len(E)
    V = list(E[0]) if T else []
    bps, cur = [], []
    if T:
        cur.append(max(range(S), key=lambda s: (V[s], -s)))
    for t in range(1, T):
        # best and second best of the previous step
        b1 = b2 = None
        for s in range(S):
            if b1 is None or V[s] > V[b1]:
                b2, b1 = b1, s
            elif b2 is None or V[s] > V[b2]:
                b2 = s
        nv = [0.0] * S
        bp = [0] * S
        for s in range(S):
            o = b1 if b1 != s else b2
            sw = V[o] - A['switch']
            if s >= 4 and not board_ok(net, s - 4, feats[t], P):
                sw -= A['board_far']
            if V[s] >= sw:
                nv[s], bp[s] = V[s] + E[t][s], s
            else:
                nv[s], bp[s] = sw + E[t][s], o
        mx = max(nv)
        V = [v - mx for v in nv]
        bps.append(bp)
        cur.append(max(range(S), key=lambda s: (V[s], -s)))
    if live or not T:
        return cur
    path = [0] * T
    path[-1] = max(range(S), key=lambda s: (V[s], -s))
    for t in range(T - 1, 0, -1):
        path[t - 1] = bps[t - 1][path[t]]
    return path


# ---------- 5. Map matching of a descent: Viterbi over the lines of the runs, plus "off" ----------

def match(net, pts, kf, idx, P, live):
    """Per point of idx: (piste index or -1 for off, distance to it)."""
    M = P['match']
    off_e = -0.5 * M['off_z'] ** 2
    prev = None  # {state: score}
    bps, out_live, cands = [], [], []
    for n, i in enumerate(idx):
        x, y = kf[i][0], kf[i][1]
        ha = pts[i]['ha'] if pts[i]['ha'] is not None else 10.0
        sig = math.sqrt(max(M['sigma_min_m'], ha) ** 2 + M['halfwidth_m'] ** 2)
        near = dict(net.near_pistes(x, y, M['cand_m']))
        cands.append(near)
        cur = {-1: off_e}
        for k, d in near.items():
            cur[k] = -0.5 * (d / sig) ** 2
        if prev is None:
            V, bp = cur, {s: s for s in cur}
        else:
            px, py = kf[idx[n - 1]][0], kf[idx[n - 1]][1]
            V, bp = {}, {}
            for s, e in cur.items():
                best, arg = None, None
                for r, v in prev.items():
                    if r == s:
                        c = 0.0
                    elif r == -1 or s == -1:
                        c = M['off_switch']
                    else:
                        c = M['switch']
                        if cands[n - 1].get(r, 1e9) > M['near_m'] and net.pistes[r].dist(px, py) > M['near_m']:
                            c += M['jump']
                    sc = v - c
                    if best is None or sc > best or (sc == best and r < arg):
                        best, arg = sc, r
                V[s], bp[s] = best + e, arg
            mx = max(V.values())
            V = {s: v - mx for s, v in V.items()}
        bps.append(bp)
        prev = V
        best = max(V, key=lambda s: (V[s], -s))
        out_live.append(best)
    if live:
        path = out_live
    else:
        path = [0] * len(idx)
        if idx:
            path[-1] = max(prev, key=lambda s: (prev[s], -s))
            for n in range(len(idx) - 1, 0, -1):
                path[n - 1] = bps[n][path[n]]
    res = []
    for n, s in enumerate(path):
        if s == -1:
            res.append((-1, min(cands[n].values(), default=1e9)))
        else:
            res.append((s, cands[n].get(s, 1e9)))
    return res


def legs(net, pts, kf, idx, mt, P):
    """Group a matched descent into legs: run (names), unnamed, off, unmatched."""
    M = P['match']
    raw = []
    for n, i in enumerate(idx):
        s, d = mt[n]
        x, y = kf[i][0], kf[i][1]
        if s == -1:
            key = ('off',)
        else:
            lab = net.label_at(s, x, y)
            key = ('run',) + lab if lab else ('unnamed',)
        raw.append(key)
    # pieces of equal label
    pieces = []
    for n, k in enumerate(raw):
        if pieces and pieces[-1]['key'] == k:
            pieces[-1]['n1'] = n
        else:
            pieces.append({'key': k, 'n0': n, 'n1': n})

    def length(pc):
        a, b = pc['n0'], pc['n1']
        s = 0.0
        for n in range(a + 1, b + 1):
            s += math.hypot(kf[idx[n]][0] - kf[idx[n - 1]][0], kf[idx[n]][1] - kf[idx[n - 1]][1])
        if a > 0:  # the step into the piece belongs to it
            s += math.hypot(kf[idx[a]][0] - kf[idx[a - 1]][0], kf[idx[a]][1] - kf[idx[a - 1]][1])
        return s

    def merge_equal(ps):
        out = []
        for pc in ps:
            if out and out[-1]['key'] == pc['key']:
                out[-1]['n1'] = pc['n1']
            else:
                out.append(dict(pc))
        return out

    # short pieces join the one before (or after, for the first)
    changed = True
    while changed and len(pieces) > 1:
        changed = False
        for j, pc in enumerate(pieces):
            if length(pc) < M['min_leg_m']:
                if j > 0:
                    pieces[j - 1]['n1'] = pc['n1']
                else:
                    pieces[1]['n0'] = pc['n0']
                del pieces[j]
                pieces = merge_equal(pieces)
                changed = True
                break
    out = []
    for pc in pieces:
        k = pc['key']
        ns = range(pc['n0'], pc['n1'] + 1)
        kind = k[0]
        if kind == 'off':
            nd = [net.nearest_piste_dist(kf[idx[n]][0], kf[idx[n]][1]) for n in ns]
            if sum(nd) / len(nd) < M['off_min_m']:
                kind = 'unmatched'
        else:
            ds = [mt[n][1] for n in ns]
            sig_in = 0
            for n in ns:
                i = idx[n]
                ha = pts[i]['ha'] if pts[i]['ha'] is not None else 10.0
                sig = math.sqrt(max(M['sigma_min_m'], ha) ** 2 + M['halfwidth_m'] ** 2)
                if mt[n][1] <= 2 * sig:
                    sig_in += 1
            if sum(ds) / len(ds) > M['leg_mean_max_m'] or sig_in < M['leg_in_frac'] * len(ds):
                kind = 'unmatched'
        out.append({'t0': pts[idx[pc['n0']]]['t'], 't1': pts[idx[pc['n1']]]['t'], 'kind': kind,
                    'runs': list(k[1:]) if kind == 'run' else [], 'dist_m': round(length(pc), 1)})
    # equal neighbours after reclassifying
    res = []
    for lg in out:
        if res and res[-1]['kind'] == lg['kind'] and res[-1]['runs'] == lg['runs']:
            res[-1]['t1'] = lg['t1']; res[-1]['dist_m'] = round(res[-1]['dist_m'] + lg['dist_m'], 1)
        else:
            res.append(lg)
    return res


# ---------- 6. The day ----------

class Analyzer:
    def __init__(self, net, params):
        self.net, self.P = net, params

    def _prep(self, pts, live):
        net, P = self.net, self.P
        xy = [net.proj.xy(q['la'], q['lo']) for q in pts]
        kf = kalman(xy, pts, P, smooth=not live)
        ts = [q['t'] for q in pts]
        src, baro = alt_source(pts)
        h = smooth_alt(ts, src, P, live)
        vz = vertical_speed(ts, h, P, live)
        return xy, kf, ts, src, baro, h, vz

    # ----- live -----
    def live(self, header, pts, events=()):
        """The live view as it changes: [{t, act, lift?, runs?}] at every change, and the live maximum speed."""
        net, P = self.net, self.P
        if not pts:
            return {'events': [], 'max_kmh': None}
        xy, kf, ts, src, baro, h, vz = self._prep(pts, True)
        E, feats = emissions(net, pts, kf, vz, P)
        act = viterbi_activity(net, E, feats, P, live=True)
        mt = match(net, pts, kf, list(range(len(pts))), P, live=True)
        evs, last = [], None
        for i, a in enumerate(act):
            ev = {'act': 'lift' if a >= 4 else ACT[a]}
            if a >= 4:
                ev['lift'] = net.lifts[a - 4].id
            if a == SKI:
                s = mt[i][0]
                ev['runs'] = list(net.label_at(s, kf[i][0], kf[i][1])) if s >= 0 else None
            key = (ev['act'], ev.get('lift'), tuple(ev['runs']) if ev.get('runs') else ev.get('runs'))
            if key != last:
                ev = dict({'t': ts[i]}, **ev)
                evs.append(ev); last = key
        # live maximum: a reading counts once its window is complete (2 s later), only while skiing
        ok = valid_speeds(pts, xy, P)
        ski = [i for i, a in enumerate(act) if a == SKI]
        best = max_speed(pts, ok, ski, P)
        return {'events': evs, 'max_kmh': kmh(best[0]) if best else None}

    # ----- final -----
    def final(self, header, pts, events=()):
        net, P = self.net, self.P
        res = {'analysis': P['analysis'], 'v': header.get('v', 1), 'resort': header.get('resort'),
               'points': len(pts)}
        if not pts:
            res.update({'segments': [], 'gaps': [], 'stats': {}})
            return res
        xy, kf, ts, src, baro, h, vz = self._prep(pts, False)
        E, feats = emissions(net, pts, kf, vz, P)
        path = viterbi_activity(net, E, feats, P, live=False)
        pieces = self._pieces(path, ts, kf, feats)
        segs = self._segments(pieces, pts, xy, kf, ts, h)
        self._calibrate(segs, ts, src)
        gaps = [{'t0': ts[i - 1], 't1': ts[i]} for i in range(1, len(ts)) if ts[i] - ts[i - 1] > P['gap_s'] * 1000]
        rec = len(pts)
        span = (ts[-1] - ts[0]) / 1000.0 + 1
        res.update({'start': ts[0], 'end': ts[-1], 'baro': baro, 'coverage': round(rec / span, 4), 'gaps': gaps,
                    'segments': [self._public(s) for s in segs]})
        res['stats'] = self._stats(res['segments'])
        return res

    def _pieces(self, path, ts, kf, feats):
        net, P = self.net, self.P
        S, L = P['segments'], P['lift']
        pcs = []
        for i, s in enumerate(path):
            if pcs and pcs[-1]['s'] == s:
                pcs[-1]['i1'] = i
            else:
                pcs.append({'s': s, 'i0': i, 'i1': i})
        dur = lambda p: (ts[p['i1']] - ts[p['i0']]) / 1000.0

        def merge_equal(ps):
            out = []
            for p in ps:
                if out and out[-1]['s'] == p['s']:
                    out[-1]['i1'] = p['i1']
                else:
                    out.append(dict(p))
            return out

        # a lift stopped for a while, or the signal wandered: the same lift again within pause_max_s, with no skiing
        # in between and not further down the cable, is one ride
        def u(i, li):
            return self.net.lifts[li].line.nearest(kf[i][0], kf[i][1])[1]
        j = 0
        while j < len(pcs):
            a = pcs[j]
            if a['s'] < 4:
                j += 1; continue
            k = j + 1
            while k < len(pcs) and pcs[k]['s'] in (STAND, WALK, UP) and (ts[pcs[k]['i1']] - ts[a['i1']]) / 1000.0 <= L['pause_max_s']:
                k += 1
            if k > j + 1 and k < len(pcs) and pcs[k]['s'] == a['s'] \
                    and (ts[pcs[k]['i0']] - ts[a['i1']]) / 1000.0 <= L['pause_max_s'] \
                    and u(pcs[k]['i0'], a['s'] - 4) >= u(a['i1'], a['s'] - 4) - L['station_snap_m']:
                a['i1'] = pcs[k]['i1']; del pcs[j + 1:k + 1]
            else:
                j += 1
        # a "ride" that does not climb along the cable is not a ride
        for p in pcs:
            if p['s'] >= 4:
                li = p['s'] - 4
                u0 = feats[p['i0']].get(li, (0, None))[1]; u1 = feats[p['i1']].get(li, (0, None))[1]
                if u0 is None or u1 is None or u1 - u0 < L['min_progress_m']:
                    vh = sum(math.hypot(kf[i][2], kf[i][3]) for i in range(p['i0'], p['i1'] + 1)) / (p['i1'] - p['i0'] + 1)
                    p['s'] = STAND if vh < 0.5 else UP
        pcs = merge_equal(pcs)
        # short stand, walk and up pieces join a neighbour that is not a lift
        changed = True
        while changed and len(pcs) > 1:
            changed = False
            for j, p in enumerate(pcs):
                if p['s'] in (STAND, WALK, UP) and dur(p) < S['min_piece_s']:
                    prev = pcs[j - 1] if j > 0 else None
                    nxt = pcs[j + 1] if j + 1 < len(pcs) else None
                    if prev is not None and (prev['s'] < 4 or nxt is None or nxt['s'] >= 4):
                        prev['i1'] = p['i1']
                    elif nxt is not None:
                        nxt['i0'] = p['i0']
                    else:
                        continue
                    del pcs[j]
                    pcs = merge_equal(pcs)
                    changed = True
                    break
        return pcs

    def _segments(self, pcs, pts, xy, kf, ts, h):
        net, P = self.net, self.P
        S, L = P['segments'], P['lift']
        dur = lambda p: (ts[p['i1']] - ts[p['i0']]) / 1000.0
        # descents: ski pieces joined across short stands and walks
        groups = []
        j = 0
        while j < len(pcs):
            p = pcs[j]
            if p['s'] == SKI:
                g = [p]; k = j + 1
                while k < len(pcs):
                    q = pcs[k]
                    if q['s'] == SKI:
                        g.append(q); k += 1; continue
                    if q['s'] in (STAND, WALK) and dur(q) < S['rest_min_s'] and k + 1 < len(pcs) and pcs[k + 1]['s'] == SKI:
                        g.append(q); k += 1; continue
                    break
                groups.append(('descent', g)); j = k
            else:
                groups.append((p['s'], [p])); j += 1
        segs = []
        for kind, g in groups:
            i0, i1 = g[0]['i0'], g[-1]['i1']
            if kind == 'descent':
                idx = list(range(i0, i1 + 1))
                dist = self._dist(kf, ts, idx)
                dr = drop(h, idx, P)
                if dr < S['min_descent_drop_m'] and dist < S['min_descent_m']:
                    segs.append({'type': 'walk', 'i0': i0, 'i1': i1}); continue
                segs.append({'type': 'descent', 'i0': i0, 'i1': i1, 'pieces': g})
            elif kind >= 4:
                segs.append({'type': 'lift', 'i0': i0, 'i1': i1, 'li': kind - 4})
            elif kind == UP:
                segs.append({'type': 'ascent', 'i0': i0, 'i1': i1})
            elif kind == WALK:
                segs.append({'type': 'walk', 'i0': i0, 'i1': i1})
            else:
                segs.append({'type': 'stop', 'i0': i0, 'i1': i1})
        # neighbouring walks (a short descent that became a walk) join
        out = []
        for s in segs:
            if out and s['type'] == 'walk' and out[-1]['type'] == 'walk':
                out[-1]['i1'] = s['i1']
            else:
                out.append(s)
        segs = out
        ok = valid_speeds(pts, xy, P)
        for n, s in enumerate(segs):
            i0, i1 = s['i0'], s['i1']
            s['t0'], s['t1'] = ts[i0], ts[i1]
            idx = list(range(i0, i1 + 1))
            if s['type'] == 'lift':
                lift = net.lifts[s['li']]
                u0 = net.lifts[s['li']].line.nearest(kf[i0][0], kf[i0][1])[1]
                u1 = net.lifts[s['li']].line.nearest(kf[i1][0], kf[i1][1])[1]
                s['lift'], s['name'] = lift.id, lift.name
                s['from'], s['to'] = self._station(lift, u0), self._station(lift, u1)
                s['rise_m'] = round(lift.ele_at(u1) - lift.ele_at(u0), 1)
            elif s['type'] == 'descent':
                ski = [i for p in s['pieces'] if p['s'] == SKI for i in range(p['i0'], p['i1'] + 1)]
                mt = match(net, pts, kf, idx, P, live=False)
                s['legs'] = legs(net, pts, kf, idx, mt, P)
                s['dist_m'] = round(self._dist(kf, ts, idx), 1)
                s['drop_m'] = round(drop(h, idx, P), 1)
                mov = sum((ts[p['i1']] - ts[p['i0']]) / 1000.0 for p in s['pieces'] if p['s'] == SKI)
                s['moving_s'] = round(mov)
                s['avg_kmh'] = kmh(s['dist_m'] / mov) if mov > 0 else None
                best = max_speed(pts, ok, ski, P)
                if best:
                    v, at = best
                    la, lo = net.proj.ll(kf[at][0], kf[at][1])
                    s['max_kmh'] = kmh(v)
                    s['max_at'] = {'t': ts[at], 'lat': round(la, 6), 'lon': round(lo, 6)}
                else:
                    s['max_kmh'] = None
                s['stops'] = [{'t0': ts[p['i0']], 't1': ts[p['i1']]} for p in s['pieces'] if p['s'] != SKI]
                s['_h'] = (i0, i1)
            elif s['type'] == 'stop':
                mx = sum(kf[i][0] for i in idx) / len(idx); my = sum(kf[i][1] for i in idx) / len(idx)
                la, lo = net.proj.ll(mx, my)
                s['lat'], s['lon'] = round(la, 6), round(lo, 6)
                nxt = segs[n + 1] if n + 1 < len(segs) else None
                kind = 'rest'
                if nxt is not None and nxt['type'] == 'lift' and (ts[nxt['i0']] - ts[i1]) / 1000.0 <= S['queue_gap_s']:
                    lift = net.lifts[nxt['li']]
                    d, u, _, _ = lift.line.nearest(mx, my)
                    st = min(lift.stations[:-1], key=lambda q: abs(q['u'] - u))
                    pt = lift.line.at(st['u'])[0]
                    if math.hypot(mx - pt[0], my - pt[1]) <= S['queue_dist_m']:
                        kind = 'queue'
                if kind == 'rest' and (ts[i1] - ts[i0]) / 1000.0 >= S['long_break_s']:
                    kind = 'long'
                s['kind'] = kind
            elif s['type'] == 'walk':
                s['dist_m'] = round(self._dist(kf, ts, idx), 1)
            elif s['type'] == 'ascent':
                s['rise_m'] = round(h[i1] - h[i0], 1)
        return segs

    def _station(self, lift, u):
        st = min(lift.stations, key=lambda q: abs(q['u'] - u))
        if abs(st['u'] - u) <= self.P['lift']['station_snap_m']:
            return {'station': st['id']}
        return {'station': None, 'unmapped': True}

    def _dist(self, kf, ts, idx):
        s = 0.0
        for a, b in zip(idx, idx[1:]):
            if (ts[b] - ts[a]) / 1000.0 <= self.P['reset_gap_s']:
                s += math.hypot(kf[b][0] - kf[a][0], kf[b][1] - kf[a][1])
        return s

    def _calibrate(self, segs, ts, src):
        """Absolute altitude: offsets measured at known stations (median, jump guard); applied from then on."""
        A = self.P['alt']
        w = A['cal_window_s'] * 1000 / 2
        meas = []
        for s in segs:
            if s['type'] != 'lift':
                continue
            lift = self.net.lifts[s['li']]
            for end, i in (('from', s['i0']), ('to', s['i1'])):
                sid = s[end]['station']
                if sid is None:
                    continue
                st = next(q for q in lift.stations if q['id'] == sid)
                meas.append((ts[i], st['ele'] - window_mean(ts, src, i, ts[i] - w, ts[i] + w)))
        offs = offsets(meas, A)
        self._offs = offs

        def alt(i):
            if not offs:
                return None
            o = offs[0][1]
            for t, v in offs:
                if t <= ts[i]:
                    o = v
            return round(src[i] + o, 1)
        for s in segs:
            if s['type'] == 'descent':
                i0, i1 = s.pop('_h')
                s['top_m'], s['bottom_m'] = alt(i0), alt(i1)

    def _public(self, s):
        return {k: v for k, v in s.items() if k not in ('i0', 'i1', 'pieces', 'li', '_h')}

    def _stats(self, segs):
        d = [s for s in segs if s['type'] == 'descent']
        l = [s for s in segs if s['type'] == 'lift']
        best = max((s for s in d if s.get('max_kmh') is not None), key=lambda s: s['max_kmh'], default=None)
        return {
            'descents': len(d), 'lifts': len(l),
            'ski_dist_m': round(sum(s['dist_m'] for s in d), 1),
            'drop_m': round(sum(s['drop_m'] for s in d), 1),
            'ski_s': sum(s['moving_s'] for s in d),
            'lift_s': round(sum((s['t1'] - s['t0']) / 1000.0 for s in l)),
            'lift_rise_m': round(sum(s['rise_m'] for s in l), 1),
            'max_kmh': best['max_kmh'] if best else None,
            'max_at': best['max_at'] if best else None,
        }
